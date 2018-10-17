/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.cwbquery;

import gov.usgs.anss.edge.Run;
import gov.usgs.anss.edge.RunList;
import gov.usgs.anss.edgethread.EdgeThread;
import gov.usgs.anss.cwbqueryclient.EdgeQueryClient;
import gov.usgs.anss.edge.IllegalSeednameException;
import gov.usgs.anss.edgeoutput.TraceBuf;
import gov.usgs.anss.edgeoutput.TraceBufPool;
import gov.usgs.anss.edge.TimeSeriesBlock;
import gov.usgs.anss.seed.MiniSeed;
import gov.usgs.anss.seed.Steim1;
import gov.usgs.anss.seed.Steim2Object;
import gov.usgs.anss.seed.SteimException;
import gov.usgs.anss.waveserver.WaveServerClient;
import gov.usgs.anss.util.Util;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.io.IOException;
import java.io.EOFException;
import java.util.Collections;

/**
 * This class represent a time series chunk which is filled with specified value if data is missing.
 * The idea is to allow creation, population by a series of blocks which may not be contiguous or
 * even in order. Constructors needs to deal with different data types and construction methods.
 *
 * Initially, this class assumed it would be created from a list of mini-seed blocks. However, it is
 * quite likely it will need to be extended to allow a pre-allocation followed by many calls adding
 * data to the time series.
 *
 * Part of the expansion was to support rolling buffers where the data is shifted down so that the
 * duration remains the same, but the start time keeps rolling forward with the buffer remaining
 * consistent. This is used by the QueryRing extending class to support time series from continuous
 * calculation, while hiding the details of where data population from the user.
 *
 * @author davidketchum
 */
public final class QuerySpan {

  public static int FILL_VALUE = 2147000000;
  private static String debugSeedname = "ZZBOU  MVHR1";
  private final RunList runs = new RunList(10, false);
  private static final int[] fillbuf = new int[50000];
  private static final int CWB = 0;
  private static final int WINSTON = 1;
  private int serverType;           // 0 is CWB, 1 is Winston
  private WaveServerClient winston; // This is a WaveServerClient for using a winston
  private boolean useWaveRaw = true;
  private boolean useCompression = true;
  protected String seedname;        // The seed channel that is buffered and returne by this object
  protected String host;            // Host to query from
  protected int port;               // Port to query from
  protected double duration;        // desired size of buffer in seconds
  protected double preDuration;     // The amount of the buffer to have after a shift in seconds (usually .8 of duration)
  //private long lastNullQuery;
  protected String[] args;
  //private boolean realTime;
  private final GregorianCalendar now = new GregorianCalendar();  // This is used to do time computations in chkData()
  protected EdgeThread par;
  protected int nsamp;        // This is the greatest index value which is a fill value (that is the number of samples in the buffer)
  // Every time data is moved or added nsamp is set to the revised greatest value
  protected int fillValue = 0;
  protected final GregorianCalendar start = new GregorianCalendar();// The time of the first sample in the buffer
  private final TimeDataCollection times;
  //protected long lastTime;                // This is the millisecond time of the newest data + one sample interval (next expected time)
  protected int[] data;                  // The data array of nsamp samples
  protected double rate = 0.;               // The digitizing rate of this buffer
  protected boolean dbg = false;            // If true, more output
  protected boolean dbgmem = false;          // If true, use less efficients data loads, but test buffer for consistency
  private boolean decodeClean;
  private int firstMissing, lastMissing, nmissing;// This are largely unused and cause a pass through the buffer, 
  private int lastData;// these are indices into the data buffer
  protected int msover2;                    // 1/2 of a bin width in millis, set with the rate
  //private final GregorianCalendar shiftTime = new GregorianCalendar();// Scratch variable used in shift routines
  private long shiftTime;
  private final byte[] frames = new byte[4096];               // Space for decompressing Steim1 or 2 frames
  private final GregorianCalendar gend = new GregorianCalendar();  // this is a scratch value
  private final GregorianCalendar gstart = new GregorianCalendar();  // this is a scratch value
  private EdgeQueryInternal query;
  private final RateSpanCalculator rateSpanCalculator;
  private final ArrayList<MiniSeed> mslist = new ArrayList<>(10);
  private final ArrayList<TimeSeriesBlock> tblist = new ArrayList<>(10);
  private final StringBuilder sb = new StringBuilder(100);    // used on test on buffers and runs
  private final StringBuilder sb2 = new StringBuilder(100);
  private final StringBuilder details = new StringBuilder(100);
  private final StringBuilder tmpsb = new StringBuilder(100);

  public int getMemoryUsage() {
    return data.length * 4 + times.getMemoryUsage();
  }

  public static void setDebugChan(String c) {
    debugSeedname = c;
  }

  public static String getDebugChan() {
    return debugSeedname;
  }

  public void setDebugMem(boolean t) {
    dbgmem = t;
    if (seedname.equals(debugSeedname)) {
      dbgmem = t;
    }
  }

  public boolean isWinston() {
    return serverType == WINSTON;
  }

  /**
   *
   * @param b Set use RawWave, if this is false getSCNLRAW will be use
   * @param c If true, compression will be use on getWAVERAW
   */
  public void setUseWaveRaw(boolean b, boolean c) {
    useWaveRaw = b;
    useCompression = c;
  }

  public void close() {
    par = null;
    runs.clear();
  }
  private final Steim2Object steim2 = new Steim2Object();

  public void setDebug(boolean t) {
    dbg = t;
    times.setDebug(t);
    if (seedname.equals(debugSeedname)) {
      dbgmem = t;
    }
  }

  public int getMSOver2() {
    return msover2;
  }

  public double getDuration() {
    return duration;
  }

  public double getPreDuration() {
    return preDuration;
  }

  public String getSeedname() {
    return seedname;
  }

  /**
   * return the time of the newest data point in milliseconds (actually the next expected data
   * point)
   *
   * @return the time of the last valid sample
   */
  public long getLastTime() {
    return getTimeInMillisAt(nsamp);
  }

  /**
   * return index of last actual data value in buffer
   *
   * @return the index of the sample right after the last valid one (the first fill value)
   */
  public int getLastData() {
    return nsamp;
  }

  /**
   * if the last load of miniSeed data had no errors
   *
   * @return true if their were no errors
   */
  public boolean wasDecodeClean() {
    return decodeClean;
  }

  /**
   * reset the decodeClean variable
   */
  public void resetDecodeClear() {
    decodeClean = false;
  }

  /**
   * get the object calculating actual rates
   *
   * @return The RateSpanCalculator for this object
   */
  public RateSpanCalculator getRateSpanCalculator() {
    return rateSpanCalculator;
  }

  /**
   * return the RunList for this buffer
   *
   * @return The RunList
   */
  public RunList getRunList() {
    return runs;
  }

  /**
   * string representing this time series
   *
   * @return a String with nsamp, rate and start date/time
   */
  @Override
  public final String toString() {
    //getNMissingData();

    return "QuerySpan: " + seedname + " ns=" + nsamp + "/" + data.length + " rt=" + Util.df23(rate) + " "
            + Util.ascdatetime2(start, null)
            + " lst=" + Util.ascdatetime2(getTimeInMillisAt(nsamp), null);
  }

  public final StringBuilder toStringBuilder(StringBuilder tmp) {
    //getNMissingData();
    StringBuilder sbt = tmp;
    if (sbt == null) {
      sbt = Util.clear(tmpsb);
    }
    sbt.append("QuerySpan: ").append(seedname).append(" ns=").append(nsamp).append("/").
            append(data.length).append(" rt=").append(Util.df23(rate)).append(" st=");
    Util.ascdatetime2(start.getTimeInMillis(), sbt).append(" lst(nsamp)=");
    Util.ascdatetime2(getTimeInMillisAt(nsamp), sbt);
    return sbt;
  }

  public String toStringFull() {
    String s = toString() + "\n";
    for (int i = 0; i < runs.size(); i++) {
      s += runs.get(i).toString() + "\n";
    }
    return s;

  }

  //EdgeQueryClient query;
  /**
   *
   * @param s A string to print to the log
   */
  protected void prt(String s) {
    if (par == null) {
      Util.prt(s);
    } else {
      par.prt(s);
    }
  }

  /**
   *
   * @param s A String to print in the log preceded by the current time
   */
  protected final void prta(String s) {
    if (par == null) {
      Util.prta(s);
    } else {
      par.prta(s);
    }
  }

  /**
   *
   * @param s A string to print to the log
   */
  protected void prt(StringBuilder s) {
    if (par == null) {
      Util.prt(s);
    } else {
      par.prt(s);
    }
  }

  /**
   *
   * @param s A String to print in the log preceded by the current time
   */
  protected final void prta(StringBuilder s) {
    if (par == null) {
      Util.prta(s);
    } else {
      par.prta(s);
    }
  }

  /**
   * Fill in sections of an array with the current fill value, use the fillbuf static area for
   * efficiency
   *
   * @param d The int data array into which the fills are placed
   * @param offset First index in d to get a fill
   * @param n Number of array indices in data to fill
   */
  protected void fill(int[] d, int offset, int n) {
    int off = offset;
    int end = offset + n;
    if (fillbuf[0] != fillValue) {
      for (int i = 0; i < fillbuf.length; i++) {
        fillbuf[i] = fillValue;
      }
    }
    while (off < end) {
      int ns = n;
      if (ns > 50000) {
        ns = 50000;
      }
      System.arraycopy(fillbuf, 0, d, off, ns);
      off += ns;
      n -= ns;
    }
  }

  /**
   * Creates a new instance of QueryRing used by the realtime memory buffer system. It expects to be
   * loaded by realTimeLoad() and calls to addRealTime() rather than by being populated by queries
   * generated by getData() calls.
   *
   * @param host The host where the query server is running, if null or "", its gcwb.cr.usgs.gov
   * @param port The port where the cwb is running, if zero, defaults to 2061
   * @param seedname The NSCL form of the channel name
   * @param st The starting time for the initialization as Millis since epoch
   * @param dur The length of the ring buffer in seconds.
   * @param preDur the length of time before the "real time" to make sure is available
   * @param rate If known, if not it will be captured when first data is laoded
   * @param parent The EdgeThread to log through
   */
  public QuerySpan(String host, int port, String seedname, long st, double dur, double preDur, double rate, EdgeThread parent) {
    this.seedname = seedname;
    //realTime=true;
    par = parent;
    duration = dur;
    preDuration = preDur;
    start.setTimeInMillis(st);
    nsamp = 0;
    fillValue = FILL_VALUE;
    if (dur * rate > 9500000) {
      if (par == null) {
        new RuntimeException(Util.asctime() + " " + seedname + "*** dur=" + dur + " rt=" + rate
                + " is very large. continue ").printStackTrace();
      } else {
        new RuntimeException(Util.asctime() + " " + seedname + "*** dur=" + dur + " rt=" + rate
                + " is very large. continue ").printStackTrace(par.getPrintStream());
      }
    }
    data = new int[(int) (dur * rate + 0.05)];
    for (int i = 0; i < data.length; i++) {
      data[i] = FILL_VALUE;
    }
    decodeClean = true;
    rateSpanCalculator = new RateSpanCalculator(seedname, rate, dur, par);
    //lastTime=st;    
    if (port <= 0) {
      port = 2061;
    }
    /*(Util.clear(tmpsb).append("QS: create start ").append(host).append("/").append(port).
            append(" ").append(seedname).append(" rt=").append(rate).append(" ").
            append(Util.ascdatetime2(start.getTimeInMillis(), null)).append(" dur=").append(dur).append(" pre=").append(preDur));*/
    if (host == null) {
      try {
        query = new EdgeQueryInternal("EQI-" + seedname, EdgeQueryServer.isReadOnly(), true, par);
      } catch (InstantiationException e) {
        e.printStackTrace(par.getPrintStream());
      }
    } else {
      try {
        StringBuilder version = EdgeQueryClient.getVersion(host, port);
        if (version.indexOf("CWB") > 0) {
          serverType = CWB;
        } else if (version.length() <= 1) {
          serverType = CWB;
        } else {
          serverType = WINSTON;
          winston = new WaveServerClient(host, port);
        }            // Must be a Winston TODO: actual queries do not use winston
      } catch (IOException e) {
        prta("IOError reading servers version, assume its a CWB.");
      }
      this.host = host;
      args = new String[16];
      args[0] = "-s";
      args[1] = seedname;
      args[2] = "-t";
      args[3] = "null";
      args[4] = "-d";
      args[5] = "" + duration;
      args[6] = "-q";
      args[7] = "-b";
      args[8] = "" + start.get(Calendar.YEAR) + "," + start.get(Calendar.DAY_OF_YEAR) + "-"
              + Util.df2(start.get(Calendar.HOUR_OF_DAY)) + ":" + Util.df2(start.get(Calendar.MINUTE))
              + ":" + Util.df2(start.get(Calendar.SECOND));
      args[9] = "-h";
      args[10] = host;
      args[11] = "-p";
      args[12] = "" + port;
      args[13] = "-to";
      args[14] = "60";
      args[15] = "-uf";
    }
    times = new TimeDataCollection(seedname, rate, duration, par);
    times.addTime(0, st, start);
    if (dbg) {
      times.setDebug(dbg);
      prta(times.toStringDetail());
    }
    setRate(rate);
    if (seedname.equals(debugSeedname)) {
      prta("QuerySpan - set debugs on " + debugSeedname + " " + seedname);
      dbgmem = true;
      dbg = true;
    }
    /*Util.clear(tmpsb).append("QS: created ");
    toStringBuilder(tmpsb);
    prta(tmpsb);*/
  }

  /**
   * reset all of the span to start at the given time, to be empty, and with only this time known to
   * the TimeDataCollection, purge the runs to this time, reset the rateSpanCalculator to nominal
   * rate
   *
   * @param startMS Start time to set
   */
  public void resetSpan(long startMS) {
    //prt("Warning: There is no data in this span size="+list.size()+" time="+Util.ascdate(trim)+" "+Util.asctime(trim));
    nsamp = 0;
    if (data == null) {
      data = new int[1];
    }
    for (int i = 0; i < data.length; i++) {
      data[i] = fillValue;
    }
    start.setTimeInMillis(startMS);
    runs.trim(start);
    times.clear(seedname, rate, 0, start.getTimeInMillis());
    times.setRate(rateSpanCalculator.getNominalRate());
    rateSpanCalculator.clear(seedname, rateSpanCalculator.getNominalRate(), 0.);
  }

  /**
   * @param seedname The NSCL channel to query
   * @param st Starting time for query in Millis since 1970
   * @param duration In seconds
   * @param msb The array list to return the blocks
   */
  public synchronized void doQuery(String seedname, long st, double duration, ArrayList<MiniSeed> msb) {
    this.seedname = seedname;
    if (args != null) {
      args[1] = seedname;
    }
    doQuery(st, duration, msb);
  }

  /**
   *
   * @param st Starting time for query in Millis since 1970
   * @param duration In seconds
   * @param msb The array list to return the blocks
   */
  public synchronized void doQuery(long st, double duration, ArrayList<MiniSeed> msb) {
    // If this is an internal CWB connection do the query
    if (host == null) {
      try {
        if (dbg) {
          query.setDebug(dbg);
          query.setQueryDebug(dbg);
        }
        query.query(seedname, st, duration, msb);
        if (dbg) {
          prta(Util.clear(tmpsb).append("QS.doQuery Int ").append(seedname).append(" ").
                  append(Util.ascdatetime2(st, null)).append(" ").append(duration).append(" ret=").
                  append(msb.size()).append(" query=").append(query));
        }
      } catch (EOFException e) {
        prta(Util.clear(tmpsb).append("Got EOF exception querying ").append(seedname).
                append(" ").append(Util.asctime2(st, null))
                + " dur=" + duration + " msb.size=" + msb.size());
      } catch (IOException | IllegalSeednameException e) {
        e.printStackTrace();
      }
    } else {  // Its an external CWB connection, do Query via TCP/IP
      args[5] = "" + duration;
      now.setTimeInMillis(st);
      args[8] = "" + now.get(Calendar.YEAR) + "," + now.get(Calendar.DAY_OF_YEAR) + "-"
              + Util.df2(now.get(Calendar.HOUR_OF_DAY)) + ":" + Util.df2(now.get(Calendar.MINUTE))
              + ":" + Util.df2(now.get(Calendar.SECOND));
      ArrayList<ArrayList<MiniSeed>> mslisttmp = EdgeQueryClient.query(args);
      if (mslisttmp == null) {
        return;
      }
      if (mslisttmp.isEmpty()) {
        return;
      }
      for (ArrayList<MiniSeed> blks : mslisttmp) {
        // Get the returned blocks
        Collections.sort(blks);            // make sure they are sorted
        msb.addAll(blks);                  // add them to the list
      }
      if (dbg) {
        prta(Util.clear(tmpsb).append("QS.doQuery EQC ").append(seedname).append(" ").
                append(Util.ascdatetime2(now)).append(" ").append(" ").append(duration).append(" ret=").append(msb.size()));
      }
      mslisttmp.clear();
    }

  }

  /**
   *
   * @param st Starting time for query in Millis since 1970
   * @param duration In seconds
   * @param msb The array list to return the blocks
   */
  public synchronized void doQueryTB(long st, double duration, ArrayList<TimeSeriesBlock> msb) {
    // If this is an internal CWB connection do the query
    gstart.setTimeInMillis(st);
    gend.setTimeInMillis(st + Math.round(duration * 1000.));
    try {
      winston.getSCNLRAWAsTimeSeriesBlocks("QS", seedname, gstart, gend, true, msb);
    } catch (IOException e) {
      prta("IOErr reading winston e=" + e);
    }
  }
  private int[] rawdata;

  public synchronized int doQueryWaveRaw(long st, double duration, int[] rawdata) {
    gstart.setTimeInMillis(st);
    gend.setTimeInMillis(st + Math.round(duration * 1000.));
    try {
      int ns = winston.getWAVERAW("QS", seedname, gstart, gend, useCompression, rawdata);
      return ns;
    } catch (IOException e) {
      prta("IOErr reading winston rawwave e=" + e);
    }
    return 0;
  }

  /**
   * Load realtime data from a CWB (internal or external depending on how QuerySpan was setup)
   *
   * @param st Starting time in Millis
   * @param duration In seconds.
   */
  public synchronized void realTimeLoad(long st, double duration) {
    if (serverType == WINSTON) {
      if (useWaveRaw) {
        int ns = (int) (duration * rate * 2);
        if (rawdata == null) {
          rawdata = new int[(int) (duration * rate * 2)];
        }
        if (rawdata.length < ns) {
          rawdata = new int[ns];
        }
        ns = doQueryWaveRaw(st, duration, rawdata);
        if (ns > 0) {
          addRealTime(st, rawdata, ns, preDuration); // Just insert the data
        }
        return;
      } else {      // Use getSCNLRAW in do QueryTB
        tblist.clear();
        doQueryTB(st, duration, tblist);
      }
    } else {
      doQuery(st, duration, mslist);
      tblist.clear();
      for (MiniSeed ms : mslist) {
        tblist.add((TimeSeriesBlock) ms); // Convert to TimeSeriesBlocks
      }
    }
    if (tblist == null) {
      prta("Query returned null!");
    } else if (tblist.size() > 0) {
      Collections.sort(tblist);
      boolean ok = false;
      // This is a load from the database, force update of rateSpanCalculator so a good rate is available for first loaded block
      /*for (TimeSeriesBlock tblist1 : tblist) {    // NOTE: This seems to be done in shiftAddTSB()!!!
        if (tblist1.getRate() > 0 && tblist1.getNsamp() > 0) {
          if (rate == 0.) {
            setRate(tblist1.getRate()); // If rate is not set, set it to some starting value.
          }
          if (rateSpanCalculator.addTSB(tblist1)) {
            ok=true;
            setRate(rateSpanCalculator.getBestRate());
            //prta("realTimeLoad:"+seedname+" * set rate initially="+rateSpanCalculator);
          }
        }
      }
      if(!ok) {
        if(rateSpanCalculator.getBestNsamp() <=0) { // if it ddid no set a rate, and the best rate is stil not set
          prta(Util.clear(tmpsb).append("realTimeLoad: ").append(seedname).append(" no best rate nsamp=").
                  append(rateSpanCalculator.getNsamp()).append(" ").append(Util.ascdatetime2(rateSpanCalculator.getStart(),null)).
                  append(Util.ascdatetime2(rateSpanCalculator.getEnd(), null)).append(" diff=").
                  append((rateSpanCalculator.getEnd()-rateSpanCalculator.getStart())/10000.).append(" s"));
          rateSpanCalculator.doBestRate();
          setRate(rateSpanCalculator.getBestRate());
          prta(Util.clear(tmpsb).append("realTimeLoad: ").append(seedname).
                  append(" did not get a bestRate. Force it set rate=").append(rateSpanCalculator));
        }
      }*/
      // Add the data
      this.shiftAddTSB(tblist, st, getTimeInMillisAt(nsamp), preDuration);
    }
    if (serverType == CWB) {
      freeBlocks(mslist);
    } else {
      winston.freeMemoryTSB(tblist);
    }
  }

  /**
   * Free the blocks returned by a call to doQuery.
   *
   * @param msb The array list of blocks, this will be an empty array list on exit.
   */
  public void freeBlocks(ArrayList<MiniSeed> msb) {
    if (host == null) {
      query.freeBlocks(msb);
    } else {
      EdgeQueryClient.freeBlocks(msb, seedname, par != null ? par.getPrintStream() : null);
    }
  }

  /**
   * REturn the time in millis of the given sample bin. This returns the values from the
   * TimesDataCollection to get a accurate time over long intervals. If that system is empty (no
   * times in the collection yet), this returns the time based on start time and nominal sample
   * rate.
   *
   * @param i the offset in the buffer that the time is need
   * @return The time of the ith sample.
   */
  public long getMillisAt(int i) {
    //long val=start.getTimeInMillis() + ((long) (i/rate*1000.+0.001));
    //prta("i="+i+" startGet="+Util.ascdatetime2(start)+" "+Util.ascdatetime2(val)+);
    //String s = Util.ascdatetime2(val);
    long val2 = times.getMillisAt(i);

    if (val2 <= 0) {
      val2 = start.getTimeInMillis() + ((long) (i / rate * 1000. + 0.001));  // ifTimesDataCOllection has no data, do it based on start and rate.
    }
    //if(val != val2 && Math.abs(val-val2) > msover2/2 && i < data.length-2) 
    //  prta(" ** TCHK: getMillisAt() "+seedname+" rt="+rate+" i="+i+
    //        " different "+Util.ascdatetime2(val)+"/"+Util.ascdatetime2(val2)+" diff="+(val-val2));
    return val2;
  }

  /**
   * Return the time of the ith samples as a StringBUilder
   *
   * @param i sample offset desired
   * @return The time of the ith offset as a StringBUilder
   */
  public StringBuilder timeAt(int i) {
    gend.setTimeInMillis(getMillisAt(i));
    return Util.asctime2(gend, null);
  }

  /**
   * Return a new time in millis at index i. It is preferred to use the other method by this name
   * and avoid the GregorianCalendar creation
   *
   * @param i Index of element in data
   * @return A new GregorianCalendar
   */
  public long getTimeInMillisAt(int i) {
    return getMillisAt(i);
  }

  /**
   * Return a new GregorianCalendar with the time at index i. It is preferred to use the other
   * method by this name and avoid the GregorianCalendar creation
   *
   * @param i Index of element in data
   * @return A new GregorianCalendar
   */
  public GregorianCalendar getGregorianCalendarAt(int i) {
    GregorianCalendar g = new GregorianCalendar();
    g.setTimeInMillis(getMillisAt(i));
    return g;
  }

  /**
   * Return a new GregorianCalendar with the time at index i. The same gregorian is returned as was
   * passed in as a curtesy
   *
   * @param i Index of element in data
   * @param g a GreogorianCalendar which will receive the calculated time at i
   * @return The input Gregorian updated with the time of index i
   */
  public GregorianCalendar getGregorianCalendarAt(int i, GregorianCalendar g) {
    g.setTimeInMillis(getMillisAt(i));
    return g;
  }

  /**
   * get the digitizing rate in Hz
   *
   * @return The digitizing rate in Hz.
   */
  public double getRate() {
    return rate;
  }

  /**
   * Get the index into the data array of the given time.
   *
   * @param starting The time of the sample - the nearest sample index to this time is return
   * @return
   */
  public int getIndexOfTime(GregorianCalendar starting) {
    return getIndexOfTime(starting.getTimeInMillis());
  }

  /**
   * Get the index into the data array of the given time.
   *
   * @param starting The time of the sample - the nearest sample index to this time is return
   * @return
   */
  public int getIndexOfTime(long starting) {
    //long msoff = starting - start.getTimeInMillis();
    //int offset = (int) ((msoff + (msoff < 0?-msover2+1:msover2-1))/1000.*rate);
    //int offset = (int) ((msoff + msover2-1.)/1000.*rate);
    //if(offset < 0 ) return -1;
    int offset2 = times.getIndexOfTime(starting);
    if (offset2 == -1) {   // If TimesDataCollection does not know, then do it based on the starttime and rates
      long msoff = starting - start.getTimeInMillis();
      offset2 = (int) ((msoff + (msoff < 0 ? -msover2 + 1 : msover2 - 1)) / 1000. * rate);
      if (offset2 < 0) {
        return -1;
      }
    }
    //if(offset != offset2) prta("*** TCHK: "+seedname+" rt="+rate+" "+Util.ascdatetime2(starting)+" "+offset+"!="+offset2);

    return offset2;
  }

  /**
   * Return the time of the next actual data after the given time. If the remainder of the buffers
   * is fill values, then return the time of the last sample. Useful for jumping through long gaps.
   *
   * @param afterTime The time to consider as the beginning of the gap.
   * @return The time of the first real data sample in the buffer, or the time of the last fill
   * value
   */
  public long getTimeOfNextDataAfter(long afterTime) {
    int offset = getIndexOfTime(afterTime);
    for (int i = offset; i < nsamp; i++) {
      if (data[i] != fillValue) {
        return afterTime += (i - offset) / rate * 1000;   // the time of the next no data value
      }
    }
    return afterTime += (nsamp - offset) / rate * 1000;
  }

  /**
   * This returns an array of data for a given time and number of samples. The start time given is
   * updated to the actual time of the first sample
   *
   * @param starting Time desired of the first sample
   * @param needed Number of samples desired
   * @param d An array of ints to receive the data
   * @param allowGaps
   * @allowGaps If true, data return might include fille values, if not, then returned data is
   * shortened to first fill
   * @return Number of samples returned or -1 if data is not in this buffer
   */
  public int getData(GregorianCalendar starting, int needed, int[] d, boolean allowGaps) {
    int offset = getIndexOfTime(starting);
    if (offset < 0 || needed < 0) {
      if (needed < 0) {
        prta(Util.clear(tmpsb).append("getData with negative needed=").append(needed));
        if (par != null) {
          new RuntimeException("QuerySpan: getData() negative needed=" + needed).printStackTrace(par.getPrintStream());
        }
      }
      return -1;
    }
    starting.setTimeInMillis(getMillisAt(offset));    // Set time to be time of first sample
    if (dbg) {
      prt(Util.clear(tmpsb).append("getData starting =").append(Util.asctime2(starting.getTimeInMillis(), null)).
              append(" buf start=").append(Util.asctime2(start.getTimeInMillis(), null)).append(" offset=").append(offset));
    }
    if (needed + offset > data.length) {
      needed = data.length - offset;
    }
    if (needed + offset > nsamp) {
      needed = nsamp - offset;
    }
    if (!allowGaps) {
      for (int i = 0; i < needed; i++) {
        d[i] = data[i + offset];
        if (data[i + offset] == fillValue) {
          return i;
        }
      }
    }
    if(offset >= 0 && needed > 0) {
      try {
        System.arraycopy(data, offset, d, 0, needed);
      } catch (RuntimeException e) {
        prta(Util.clear(tmpsb).append("Runtime getData() ").append(seedname).
                append(" offset=").append(offset).append(" needed=").append(needed).
                append(" len=").append(data.length).append(" nsamp=").append(nsamp).append(" e=").append(e));
      }
    }
    else {
      needed = -1;
    }
    return needed;

  }

  /**
   * This returns an array of data for a given time and number of samples. The start time given is
   * updated to the actual time of the first sample
   *
   * @param starting Time desired of the first sample
   * @param needed Number of samples desired
   * @param d An array of doubles to receive the data
   * @param allowGaps If true, data return might include fille values, if not, then returned data is
   * shortened to first fill
   * @return Number of samples returned or -1 if data is not in this buffer
   */
  public int getData(GregorianCalendar starting, int needed, double[] d, boolean allowGaps) {
    int offset = getIndexOfTime(starting);
    if (offset < 0) {
      return -1;
    }
    starting.setTimeInMillis(getMillisAt(offset));    // Set time to be time of first sample
    if (dbg) {
      prt(Util.clear(tmpsb).append("getData starting =").append(Util.asctime2(starting, null)).
              append(" buf start=").append(Util.asctime2(start, null)).append(" offset=").append(offset));
    }
    if (needed + offset > data.length) {
      needed = data.length - offset;
    }
    if (needed + offset > nsamp) {
      needed = nsamp - offset;
    }
    if (!allowGaps) {
      for (int i = 0; i < needed; i++) {
        if (data[i + offset] == fillValue) {
          return i;  // if fills are not allowed
        }
        d[i] = data[i + offset];
      }
    } else {
      for (int i = 0; i < needed; i++) {
        d[i] = data[i + offset];    // convert ints to double
      }
    }
    return needed;

  }

  /**
   * This returns an array of data for a given time and number of samples. The start time given is
   * updated to the actual time of the first sample
   *
   * @param starting Time desired of the first sample
   * @param needed Number of samples desired
   * @param d An array of floats to receive the data
   * @param allowGaps If true, data return might include fille values, if not, then returned data is
   * shortened to first fill
   * @return Number of samples returned or -1 if data is not in this buffer
   */
  public int getData(GregorianCalendar starting, int needed, float[] d, boolean allowGaps) {
    int offset = getIndexOfTime(starting);
    if (offset < 0) {
      return -1;
    }
    starting.setTimeInMillis(getMillisAt(offset));    // Set time to be time of first sample
    if (dbg) {
      prt(Util.clear(tmpsb).append("getData starting =").append(Util.asctime2(starting, null)).
              append(" buf start=").append(Util.asctime2(start, null)).append(" offset=").append(offset));
    }
    if (needed + offset > data.length) {
      needed = data.length - offset;
    }
    if (needed + offset > nsamp) {
      needed = nsamp - offset;
    }
    if (!allowGaps) {
      for (int i = 0; i < needed; i++) {
        if (data[i + offset] == fillValue) {
          return i;  // if fills are not allowed
        }
        d[i] = data[i + offset];          // put data in array
      }
    } else {
      for (int i = 0; i < needed; i++) {
        d[i] = data[i + offset];    // convert ints to floats
      }
    }
    return needed;

  }

  /**
   * get the time series as an array of ints
   *
   * @return The timeseries as an array of ints - this is the internal array so any changes will be
   * in this object
   */
  public int[] getData() {
    return data;
  }

  /**
   * get the ith time series value
   *
   * @param i THe index (starting with zero) of the data point to return.
   * @return The ith time series value
   */
  public int getData(int i) {
    return data[i];
  }

  /**
   * get a chunk of the data into an array,
   *
   * @param d The array to put the data in
   * @param off The offset in the internal data buffer to start
   * @param len The maximum length of data to return (d must be dimensioned >len)
   * @return The number of samples actually returned <=len
   */
  public int getData(int[] d, int off, int len) {
    int n = len;
    if ((nsamp - off) < len) {
      n = nsamp - off;
    }
    System.arraycopy(data, off, d, 0, n);
    return n;
  }

  /**
   * get number of data samples in time series (many might be fills). This is the length of the
   * internal buffer from the original setup of the duration. If a larger time series is loaded via
   * the refill() method, this value will grow to that size. So, it represents the duration of this
   * buffer in samples.
   *
   * @return Number of samples in series
   */
  public int getNsamp() {
    return nsamp;
  }

  /**
   * return start time as a GregorianCalendar
   *
   * @return The start time
   */
  public GregorianCalendar getStart() {
    return start;
  }

  /**
   * return the max value of the time series ignoring any fill values
   *
   * @return Min value of the time series
   */
  public int getMin() {
    int min = 2147000000;
    for (int i = 0; i < nsamp; i++) {
      if (data[i] < min && data[i] != fillValue) {
        min = data[i];
      }
    }
    return min;
  }

  /**
   * return the max value of the time series
   *
   * @return Max value of the time series
   */
  public int getMax() {
    int max = -2147000000;
    for (int i = 0; i < nsamp; i++) {
      if (data[i] > max && data[i] != fillValue) {
        max = data[i];
      }
    }
    return max;
  }

  /**
   * return true if any portion of the allocated space has a "no data" or fill value. Trim number of
   * samples to reflect actual sample if end contains some no data points.
   *
   * @return true if there is at least on missing data value
   */
  public boolean hasGapsBeforeEnd() {
    int ns = nsamp;
    if (data[0] == fillValue) {
      return true;     // opening with fill
    }
    int i;
    for (i = nsamp - 1; i >= 0; i--) {
      if (data[i] != fillValue) {
        ns = i + 1;
        break;
      }
    }
    if (i <= 0 && ns == nsamp) {
      return false;      // no fill Values found looking for last one!
    }
    for (i = 0; i < ns; i++) {
      if (data[i] == fillValue) {
        return true;
      }
    }
    return false;
  }

  /**
   * return true if any portion of the allocated space has a "no data" or fill value
   *
   * @return true if there is at least on missing data value
   */
  public boolean hasGaps() {
    for (int i = 0; i < nsamp; i++) {
      if (data[i] == fillValue) {
        return true;
      }
    }
    return false;
  }

  /**
   * return number of missing data points
   *
   *
   * @return Number of missing data points
   */
  public int getNMissingData() {
    int noval = 0;
    int first = nsamp + 1;
    int last = nsamp + 1;
    for (int i = 0; i < nsamp; i++) {
      if (data[i] == fillValue) {
        noval++;
        last = i;
        if (noval == 1) {
          first = i;
        }
      } else {
        lastData = i;
      }
    }
    firstMissing = first;
    lastMissing = last;
    nmissing = noval;
    return noval;
  }

  public int getFirstMissing() {
    getNMissingData();
    return firstMissing;
  }

  public int getLastMissing() {
    getNMissingData();
    return lastMissing;
  }

  /**
   * compare to ZeroFilledSpans for "equivalence"
   *
   * @return True if equivalent
   * @param z Another QuerySpan to compare against.
   */
  public String differences(QuerySpan z) {

    sb2.append("Summary ").append(toString()).append("\n");
    sb2.append("Summary ").append(z.toString()).append("\n");

    if (getNMissingData() != z.getNMissingData()) {
      sb2.append("*** # missing different ").append(getNMissingData()).append("!=").append(z.getNMissingData()).append("\n");
    }
    if (getNsamp() != z.getNsamp()) {
      sb2.append("*** Nsamp different ").append(nsamp).append(" != ").append(z.getNsamp()).append(" diff = ").append(nsamp - z.getNsamp()).append("\n");
    }
    int gapStart = -1;
    int gapSize = 0;
    for (int i = 0; i < Math.min(nsamp, z.getNsamp()); i++) {
      if (data[i] != z.getData(i)) {
        if (gapStart == -1) {
          sb2.append(" difference start at ").append(i).append(" ").append(timeAt(i));
          gapStart = i;
          gapSize++;
        } else {
          gapSize++;
        }
        details.append("*** ").append((i + "        ").substring(0, 8)).
                append(Util.leftPad((data[i] == fillValue ? "  nodata  " : "" + data[i]), 8)).
                append(Util.leftPad((z.getData(i) == fillValue ? "  nodata  " : "" + z.getData(i)), 8));
        if (data[i] == fillValue || z.getData(i) == fillValue) {
          details.append("\n");
        } else {
          details.append(Util.leftPad("df=" + (data[i] - z.getData(i)), 14)).append("\n");
        }
      } else {
        if (gapStart != -1) {
          sb2.append(" ends at ").append(i).append(" ").append(timeAt(i)).append(" # diff=").append(gapSize).append("\n");
          gapStart = -1;
          gapSize = 0;
        }
      }
    }
    if (gapStart != -1) {
      sb2.append(" ends at ").append(nsamp).append(" ").append(timeAt(nsamp)).append(" # diff=").append(gapSize).append("\n");
    }
    return sb2.toString() + "\nDetails:\n" + details.toString();
  }

  /**
   * Replace this with the data on this list. Make the data bigger if the duration calls for it,
   * this can be used to reuse one of these objects for totally unrelated data
   *
   * @param list Of miniSeed blocks to put in the span
   * @param trim Trim data to this time as the beginning
   * @param dur The duration of seconds of the buffer.
   */
  public void refill(ArrayList<MiniSeed> list, GregorianCalendar trim, double dur) {
    if (list.size() > 0) {
      int j = 0;
      MiniSeed ms = list.get(j);
      while (ms.getRate() <= 0.) {
        ms = (MiniSeed) list.get(j++);
      }
      seedname = ms.getSeedNameString();
      if (seedname.equals(debugSeedname)) {
        dbg = true;
        dbgmem = true;
      }
      if (args != null) {
        args[1] = seedname;
      }
    }
    runs.clear();         // This load stands on its own
    doFilledSpan(list, trim, dur);
  }

  /**
   * Replace this with the data on this list. Make the data bigger if the duration calls for it,
   * this can be used to reuse one of these objects for totally unrelated data
   *
   * @param list Of miniSeed blocks to put in the span
   * @param trim Trim data to this time as the beginning
   * @param dur The duration of seconds of the buffer.
   */
  public void refillTB(ArrayList<TraceBuf> list, GregorianCalendar trim, double dur) {
    if (list.size() > 0) {
      int j = 0;
      TraceBuf ms = list.get(j);
      while (ms.getRate() <= 0.) {
        ms = (TraceBuf) list.get(j++);
      }
      seedname = ms.getSeedNameString();
      if (args != null) {
        args[1] = seedname;
      }
    }
    runs.clear();         // This load stands on its own
    doFilledSpan(list, trim, dur);
  }

  public void resetDuration(double duration) {
    nsamp = 0;
    data = new int[(int) (duration * rate + 0.05)];
    this.duration = duration;
    times.setRate(rate);
    rateSpanCalculator.clear(seedname, rate, duration);
  }

  /**
   * populate a zero filled span. Called by the constructors
   *
   * @param list ArrayList of mini-seed data
   * @param trim Start time - data before this time is discarded.
   * @param duration Time in seconds to do it
   * @param fill an integer to use to pre-fill the array (the not-a-data value)
   */
  private void doFilledSpan(ArrayList list, GregorianCalendar trim, double duration) {
    decodeClean = true;
    //lastTime=0;     // this is a complete refill, so reset last time
    // Look through blocks until we find one that has a rate (i.end. probably data!)
    double rt = 0.;
    int j = 0;
    TimeSeriesBlock ms = null;
    while (rt == 0. && j < list.size()) {
      ms = (TimeSeriesBlock) list.get(j);
      if (ms == null) {
        continue;
      }
      rt = ms.getRate();
      setRate(ms.getRate());
      j++;
    }
    if (rt == 0. || ms == null) {
      //prt("Warning: There is no data in this span size="+list.size()+" time="+Util.ascdate(trim)+" "+Util.asctime(trim));
      nsamp = 0;
      if (data == null) {
        data = new int[1];
      }
      data[0] = fillValue;
      if (ms == null) {
        start.setTimeInMillis(trim.getTimeInMillis());
      } else {
        start.setTimeInMillis(ms.getTimeInMillis());
      }
      runs.trim(start);
      times.clear(seedname, rate, 0, start.getTimeInMillis());
      return;
    }
    long diffms = (trim.getTimeInMillis() - ms.getTimeInMillis());
    int begoffset = (int) ((diffms + (diffms > 0 ? msover2 : -msover2)) * rate / 1000.);
    if (dbgmem) {
      prt(Util.clear(tmpsb).append("trim=").append(Util.ascdatetime2(trim, null)).
              append(" ms=").append(Util.ascdatetime2(ms.getTimeInMillis(), null)).append(" begoff=").append(begoffset));
    }
    // The start time of this span is the time of first sample from first ms after 
    // the trim start time
    start.setTimeInMillis(ms.getTimeInMillis());
    //lastTime=start.getTimeInMillis();
    start.add(Calendar.MILLISECOND, (int) (begoffset / rate * 1000.));// first in trimmed interval
    if (dbgmem) {
      prt(Util.clear(tmpsb).append(Util.ascdatetime2(start, null)).append(" begoff=").append(begoffset));
    }
    //MiniSeed msend = (MiniSeed) list.get(list.size()-1);
    nsamp = (int) (duration * rate + 0.05);    // temp, reset to zero below
    this.duration = duration;
    //prt("duration="+duration+" nsf="+(duration*ms.getRate())+"nsamp="+nsamp);
    if (nsamp > 9500000) {
      new RuntimeException(Util.asctime() + " " + seedname + "****  dur=" + duration + " rt=" + rate
              + " is very large. continue").printStackTrace();

    }
    if (data == null) {
      data = new int[nsamp];
    } else if (data.length < nsamp + 1) {
      data = new int[nsamp];
    }
    fill(data, 0, data.length);
    times.clear(seedname, rate, 0, start.getTimeInMillis());
    times.setRate(rate);
    rateSpanCalculator.clear(seedname, rate, duration);
    //for(int i=0; i<data.length; i++) data[i]=fillValue;
    nsamp = 0;
    for (int i = list.size() - 1; i >= 0; i--) {
      if (list.get(i) instanceof MiniSeed) {
        addBlock((MiniSeed) list.get(i));
      } else if (list.get(i) instanceof TraceBuf) {
        TraceBuf tb = (TraceBuf) list.get(i);
        addRealTime(tb.getTimeInMillis(), tb.getData(), tb.getNsamp(), 0.);
      }
    }           // end for each block in list
    runs.trim(start);
  }

  public void shift(long time) {
    int offset = getIndexOfTime(time);
    if (nsamp == 0) {
      resetSpan(time);      // reset data, start time, time calculator, rate calculator
      prt(Util.clear(tmpsb).append(seedname).append(" * Shift: to empty - set time to ").
              append(Util.ascdatetime2(time, null)).append(" ").append(toString()));
      return;
    } else {
      try {
        if (offset < 0 || offset >= data.length) {    // The desired time is not in the current buffer
          // The safe move is to move the time to the preDur from the end point
          long endtime = times.getMillisAt((int) (data.length * preDuration / duration));

          // If this buffer has not times, just resetSpan it and put data at beginning.
          if (time == -1 || time == 0 || start.getTimeInMillis() < 10000) {      // We have no times
            prta(Util.clear(tmpsb).append(seedname).append("* Shift: bad offset=").append(offset).
                    append(" no times or empty.  clear to ").append(Util.ascdatetime2(time, null)).
                    append(" start was=").append(Util.ascdatetime2(start, null)));
            resetSpan(time);     // reset data, start time, time calculator, rate calculator
            offset = 0;
          } else if (offset < 0) {
            Util.clear(tmpsb).append(seedname).append(" ** Shift: bad offset=").append(offset).
                    append(" is before buffer. Skip loading data").append(Util.ascdatetime2(time, null)).
                    append(toStringBuilder(tmpsb));
            prta(tmpsb);
            /*if(par == null)
              new RuntimeException(tmpsb.toString()).printStackTrace(); 
            else
              new RuntimeException(tmpsb.toString()).printStackTrace(par.getPrintStream());   */

            return;
          } else if (offset >= data.length) {    // There is a shift to do, need to either use request time

            Util.clear(tmpsb).append(seedname).append(" ** Shift: bad offset=").append(offset).append(" Override time=");
            Util.ascdatetime2(time, tmpsb).append(" time at end of current ");
            Util.ascdatetime2(endtime, tmpsb).append(" ");
            toStringBuilder(tmpsb);
            prta(tmpsb);
            /*if(par == null) 
              new RuntimeException(tmpsb.toString()).printStackTrace();
            else
              new RuntimeException(tmpsb.toString()).printStackTrace(par.getPrintStream());*/

            resetSpan(time);      // reset data, start time, time calculator, rate calculator
            Util.clear(tmpsb).append(seedname).append(" ** Shift: set offset=0 to ").
                    append(Util.ascdatetime2(time, null)).append(" rt=").append(rate).append(" ").append(times).
                    append(" ms at 0=").append(Util.ascdatetime2(getMillisAt(0)));
            offset = 0;
          }
        }
        if (offset != 0) {
          System.arraycopy(data, offset, data, 0, data.length - offset);  // Put data in right spot.
        }
        start.setTimeInMillis(this.getMillisAt(offset));
      } catch (ArrayIndexOutOfBoundsException e) {
        prt(Util.clear(tmpsb).append(seedname).append("Shift array out of bounds time=").append(Util.ascdatetime2(time, null)).
                append(" Start=").append(Util.ascdatetime2(start, null)).append(" offset=").append(offset).
                append(" length=").append(data.length));
        if (par == null) {
          e.printStackTrace();
        } else {
          e.printStackTrace(par.getPrintStream());
        }

        throw e;
      }
    }
    nsamp = nsamp - offset;
    fill(data, data.length - offset, offset);
    if (dbgmem) {
      Util.clear(tmpsb).append("Shift complete : ").append(" offset=").append(offset).append(" ");
      toStringBuilder(tmpsb);
      prta(tmpsb);
    }
    runs.trim(start);
    times.trim(start, offset);
  }

  /**
   * Set the data rate. This checks for change of rate
   *
   * @param r The rate to set in Hz
   */
  public final void setRate(double r) {
    if (r != 0.) {
      if (dbg && rate != 0. && Math.abs(rate - r) / rate > 0.1) // Is this a significant rate change (not just a better estimate)
      {
        prt(Util.clear(tmpsb).append(" **** Rate changed in QuerySpan rnow=").append(r).
                append(" vs ").append(rate).append(" ").append(toString()));
      }
      rate = r;
      msover2 = (int) (1. / rate * 1000. / 2.);
      times.setRate(rate);
    }
  }         // 1/2 of a bin width in  millis

  /**
   * Change from the standard fill value - do not do this unless you have a good reason!
   *
   * @param val The new fill value
   */
  public final void setFillValue(int val) {
    fillValue = val;
  }

  /**
   * Shift the buffer so that shiftLength seconds of data before the first data time in the list of
   * new blocks is in the buffer before the first time. That is the first time has shiftLen seconds
   * of data before the first sample in the new data.
   *
   * @param list The list of miniseed blocks to add to this span
   * @param begin Limit on the earliest time to shift to (it can be no earlier this time -
   * shiftLength
   * @param end End time limit
   * @param shiftLength The amount of old data before first new data to leave in the buffer
   */
  public void shiftAddTSB(ArrayList<TimeSeriesBlock> list, long begin, long end, double shiftLength) {
    long first = begin;
    long last = end;
    MiniSeed ms;
    double rt = -1;
    if (list != null) {
      for (TimeSeriesBlock list1 : list) {
        if (list1.getNsamp() <= 0 && list1.getRate() <= 0) {
          continue;
        }
        if (list1.getTimeInMillis() < first) {
          first = list1.getTimeInMillis();
        }
        if (list1.getNextExpectedTimeInMillis() > last) {
          last = list1.getNextExpectedTimeInMillis();
        }
        if (rt < 0) {
          if (list1.getRate() > 0. && list1.getNsamp() > 0) {
            rt = list1.getRate();
            if (Math.abs(rate - list1.getRate()) > 0.1 * rate) {
              setRate(list1.getRate());
            }
          }
        }
      }
    }
    if (dbgmem) {
      prt(Util.clear(tmpsb).append("ShiftAdd:Starting Zero:").append(seedname).
              append(" ").append(toString()).append(" first=").append(Util.ascdatetime2(first, null)).
              append(" last=").append(Util.ascdatetime2(last, null)).append(" endbuf=").
              append(Util.ascdatetime2(getMillisAt(data.length - 1), null)).
              append(" shift=").append(getMillisAt(data.length - 1) <= last));
    }
    if (getMillisAt(data.length - 1) <= last) {
      long shiftMillis = (long) (((int) (shiftLength * rate)) / rate * 1000. + 0.5);   // This is a even number of samples
      shiftTime = ((long) (first - shiftMillis)); // This leaves shiftLength at beginning of buffer
      if (dbgmem) {
        prt(Util.clear(tmpsb).append("ShiftAdd:Shifting data to time ").append(Util.ascdatetime2(shiftTime, null)).
                append(" off=").append(getIndexOfTime(shiftTime)).append(" timeat=").
                append(Util.ascdatetime2(getMillisAt(getIndexOfTime(shiftTime)), null)).
                append(" ").append(toString()));
      }
      //shiftTime.setTimeInMillis((long)(last+shiftLength*1000 - data.length/rate*1000)); // Time of the desired first sample
      shift(shiftTime);
      if (dbgmem) {
        prt(Util.clear(tmpsb).append("ShiftAdd: after ").append(toString()));
      }
    }
    long endtime = getMillisAt(data.length - 1);
    if (dbgmem) {
      prt(Util.clear(tmpsb).append("ShiftAdd: Load low overlap=").append(getTimeInMillisAt(nsamp) - first).
              append(" past end=").append(last - ((long) (start.getTimeInMillis() + nsamp / rate * 1000))).
              append(" data before ").append(Util.ascdatetime2(endtime, null)).append(" size=").
              append(list == null ? "Null" : list.size()));
    }
//    if(dbg) prt("Add blocks from "+Util.asctime2(first)+" to "+Util.asctime2(last)+" nblks="+list.size());
    if (list != null) {
      for (int i = list.size() - 1; i >= 0; i--) {
        if (list.get(i).getNsamp() > 0 && list.get(i).getRate() > 0) {
          if (list.get(i).getNextExpectedTimeInMillis() < endtime) {
            if (list.get(i) instanceof MiniSeed) {
              addBlock((MiniSeed) list.get(i));
              if (rateSpanCalculator.addMS((MiniSeed) list.get(i))) {
                setRate(rateSpanCalculator.getBestRate());
                if (dbg) {
                  prta(Util.clear(tmpsb).append("ShiftAdd: set rate=").append(rateSpanCalculator));
                }
              }
            } else if (list.get(i) instanceof TraceBuf) {
              addBlock((TraceBuf) list.get(i));
              if (rateSpanCalculator.addTSB((TraceBuf) list.get(i))) {
                setRate(rateSpanCalculator.getBestRate());
                if (dbg) {
                  prta(Util.clear(tmpsb).append("ShiftAdd: set rate=").append(rateSpanCalculator));
                }
              }
            }
            if (dbg) {
              prt(Util.clear(tmpsb).append("ADD  block[").append(i).append("] ").append(list.get(i)));
            }
          } else if (dbg) {
            prt(Util.clear(tmpsb).append("SKIP block[").append(i).append("] ").append(list.get(i)).append(" ").append(toString()));
            if (par == null) {
              new RuntimeException("SKIP Block ").printStackTrace();
            } else {
              new RuntimeException("SKIP Block ").printStackTrace(par.getPrintStream());
            }

          }
        }
      }           // end for each block in list
    }
    runs.trim(start);
    //getNMissingData();
    if (dbg) {
      prt(Util.clear(tmpsb).append("Ending Zero:").append(toString()).
              append(" first=").append(Util.ascdatetime2(first, null)).append(" last=").
              append(Util.ascdatetime2(last, null)));
    }
  }

  /**
   * Shift the buffer so that shiftLength seconds of data before the first data time in the list of
   * new blocks is in the buffer before the first time. That is the first time has shiftLen seconds
   * of data before the first sample in the new data.
   *
   * @param ms A miniSEED block to add to this span
   * @param shiftLength The amount of old data before first new data to leave in the buffer
   */
  public synchronized void addRealTime(MiniSeed ms, double shiftLength) {
    if (ms == null) {
      return;
    }
    if (ms.getNsamp() <= 0 && ms.getRate() <= 0) {
      return;
    }
    if (ms.getTimeInMillis() < start.getTimeInMillis()) {
      return;            // block is out of buffer
    }
    if (Math.abs(ms.getRate() - rate) > rate * 0.1) {
      setRate(ms.getRate());
    }
    if (rateSpanCalculator.addMS(ms)) {
      setRate(rateSpanCalculator.getBestRate());
      if (dbg) {
        prta(Util.clear(tmpsb).append("addRealTime(MS): set rate=").append(rateSpanCalculator.toStringBuilder(null)));
      }
    }

    if (dbg) {
      prta(Util.clear(tmpsb).append("addRealTime(MS):Starting Zero:").append(toString()).
              append(" first=").append(Util.ascdatetime2(start, null)).append(" last=").
              append(Util.ascdatetime2(getTimeInMillisAt(nsamp), null)).append(" ms=").
              append(ms.toString().substring(0, 60)));
    }

    if (this.getMillisAt(data.length - 1) <= ms.getNextExpectedTimeInMillis()) {  // need to do a shift
      // Find the time that  leaves shiftLength data at beginning of buffer
      long shiftMillis = (long) (((int) (shiftLength * rate)) / rate * 1000. + 0.5);   // This is a even number of samples
      shiftTime = ((long) (ms.getNextExpectedTimeInMillis() - shiftMillis));
      if (dbgmem) {
        prta(Util.clear(tmpsb).append("addRealTime(MS):Shifting data to time ").
                append(Util.ascdatetime2(shiftTime, null)).append(" off=").append(getIndexOfTime(shiftTime)).
                append(" timeat=").append(Util.ascdatetime2(getMillisAt(getIndexOfTime(shiftTime)), null)).
                append(" ").append(toString()));
      }
      //shiftTime.setTimeInMillis((long)(last+shiftLength*1000 - data.length/rate*1000)); // Time of the desired first sample
      shift(shiftTime);
      if (dbgmem) {
        prta(Util.clear(tmpsb).append("addRealTime(MS):Shift after ").append(toString()));
      }
      runs.trim(start);
    }

//    if(dbg) prt("Add blocks from "+Util.asctime2(first)+" to "+Util.asctime2(last)+" nblks="+list.size());
    addBlock(ms);
    if (dbg) {
      prta(Util.clear(tmpsb).append("addRealTime(MS):ADD  block ").append(ms));
    }
    //getNMissingData();
    if (dbg) {
      long endTime = getMillisAt(data.length - 1);
      prta(Util.clear(tmpsb).append("addRealTime(MS):Ending Zero:").append(toString()).append(" first=").
              append(Util.ascdatetime2(start, null)).append(" last=").append(Util.ascdatetime2(endTime, null)));
    }

  }

  public synchronized boolean addRealTime(long st, int[] samples, int ns, double shiftLength) {
    if (rate == 0.) {
      prt("**** call addRealTime with zero rate");
    }
    long endTime = st + (long) (ns / rate * 1000. + 0.5);
    if (dbgmem) {
      prta(Util.clear(tmpsb).append("addRealTime (Raw) : Starting Zero:").append(toString()).
              append(" first=").append(Util.ascdatetime2(st, null)).append(" ns=").append(ns).
              append(" end=").append(Util.ascdatetime2(endTime, null)));
    }
    while (endTime > getMillisAt(data.length - 1)) {   // shift until it will fit!
      //if(this.getMillisAt(data.length-1) < last) {  // need to do a shift
      long shiftMillis = (long) (((int) (shiftLength * rate)) / rate * 1000. + 0.5);   // This is a even number of samples
      shiftTime = ((long) (endTime - shiftMillis)); // This leaves shiftLength at beginning of buffer
      if (dbgmem) {
        prta(Util.clear(tmpsb).append("addRealTime: Shifting data to time ").append(Util.ascdatetime2(shiftTime, null)).
                append(" off=").append(getIndexOfTime(shiftTime)).append(" timeat=").
                append(Util.ascdatetime2(getMillisAt(getIndexOfTime(shiftTime)), null)).
                append(" endTime=").append(Util.ascdatetime2(endTime).append(" ")).append(shiftMillis).append(" ").append(toString()));
      }
      //shiftTime.setTimeInMillis((long)(last+shiftLength*1000 - data.length/rate*1000)); // Time of the desired first sample
      shift(shiftTime);
      //if(Character.isLowerCase(seedname.charAt(7))) 
      times.addTime(0, shiftTime, start);   // Litte b or h needs times too, add a time as we may have shifted everything off!
      if (dbgmem) {
        prta(Util.clear(tmpsb).append("addRealTime:Shift after ").append(toString()));
      }

    }

    // The data should fit into the array easily now
    int offset = getIndexOfTime(st);
    if (offset < 0) {
      prta(Util.clear(tmpsb).append("addRealTime:").append(seedname).
              append(" * Offset < 0 data is way behind? off=").
              append(offset).append("  ").append(toString()).append(" adding ").
              append(Util.ascdatetime2(st, null)).append(" ns=").append(ns));
      return false;
    }
    if (endTime > getMillisAt(data.length)) {
      prta(Util.clear(tmpsb).append("addRealTime:").append(seedname).
              append(" *** Offset after end. SHOULD NOT HAPPEN!  off=").
              append(offset).append(" ").append(toString()).append(" adding ").
              append(Util.ascdatetime(st, null)).append(" ns=").append(ns));
      return false;
    }
    if (dbgmem) {
      if (!testSetData(samples, 0, data, offset, ns, sb)) {
        prta(Util.clear(tmpsb).append("addRealTime:*** Load3 not ok, offset=").append(offset).
                append(" ").append(sb).append("\n").append(times.toStringDetail()));
      } else if (dbg) {
        prta(Util.clear(tmpsb).append("addRealTime: Load3 ok offset=").append(offset).append(" ").append(sb));
      }
    } else {
      System.arraycopy(samples, 0, data, offset, ns);
    }
    if (offset + ns >= nsamp) {
      nsamp = offset + ns;
    }
    //if(Character.isLowerCase(seedname.charAt(7))) 
    times.addTime(offset, st, start);   // Litte b or h needs times too

    if (dbgmem) {
      prta(Util.clear(tmpsb).append("addRealTime:").append(seedname).append(" addRealTimeRaw() ").
              append(Util.ascdatetime2(start, null)).append(" add=").append(Util.ascdate(st)).append(" ").
              append(Util.asctime(st)).append(" offset=").append(offset).append(" ns=").append(ns));
    }
    now.setTimeInMillis(st);
    runs.add(seedname, now, ns / rate);
    if (dbgmem) {
      if (!testNsamp()) {
        prt(Util.clear(tmpsb).append("addRealTime:").append(seedname).append(" testNsamp() failed addRealTime() "));
      }
      if (!testRunsList(sb)) {
        prta(Util.clear(tmpsb).append("addRealTime:*** RunsList problem ").append(sb));
      }
    }

    return true;
  }

  // AddBLock as a TimeSerieis block
  private boolean addBlock(TimeSeriesBlock ms) {

    if (ms == null) {
      return false;
    }
    if (ms.getRate() == 0. || ms.getNsamp() == 0) {
      return false;
    }
    //long msoff = ms.getTimeInMillis() - start.getTimeInMillis();
    //int offset = (int) ((msoff + (msoff < 0?-msover2:msover2)-1.)/1000.*rate);
    int offset = getIndexOfTime(ms.getTimeInMillis());
    //(int) ((ms.getGregorianCalendar().getTimeInMillis()-
    //    start.getTimeInMillis()+msover2)*rate/1000.);
    //long mod = (long)((ms.getGregorianCalendar().getTimeInMillis()-
    //   start.getTimeInMillis()+msover2)*rate) % 1000L;
    if (dbgmem) {
      prta(Util.clear(tmpsb).append("addBlock: ").append(seedname).append(" ").
              append(Util.ascdatetime2(start, null)).append(" ms=").append(Util.ascdatetime2(ms.getTimeInMillis(), null)).
              append(" offset=").append(offset).append(" ns=").append(ms.getNsamp()).append(" rt=").append(rate));
    }

    int[] samples = null;
    if (ms instanceof MiniSeed) {
      // get the compression frames
      MiniSeed ms1 = (MiniSeed) ms;
      System.arraycopy(ms.getBuf(), ms1.getDataOffset(), frames, 0, ms.getBlockSize() - ms1.getDataOffset());
      if (ms1.getEncoding() != 11 && ms1.getEncoding() != 10) {
        boolean skip = false;
        for (int ith = 0; ith < ms1.getNBlockettes(); ith++) {
          if (ms1.getBlocketteType(ith) == 201) {
            skip = true;     // its a Murdock Hutt, skip it
          }
        }
        if (!skip) {
          prta(Util.clear(tmpsb).append("addBlock: Cannot decode - not Steim I or II type=").append(ms1.getEncoding()));
          decodeClean = false;
          prt(ms.toString());
        }
        return false;
      }
      try {
        int reverse = 0;
        if (ms1.getEncoding() == 10) {
          samples = Steim1.decode(frames, ms.getNsamp(), ms1.isSwapBytes(), reverse);
        }
        if (ms1.getEncoding() == 11) {
          synchronized (steim2) {
            boolean ok = steim2.decode(frames, ms.getNsamp(), ms1.isSwapBytes(), reverse);
            if (ok) {
              samples = steim2.getSamples();
            }
            if (steim2.hadReverseError()) {
              decodeClean = false;
              prta(Util.clear(tmpsb).append("addBlock: decode block had reverse integration error").append(ms));
            }
            if (steim2.hadSampleCountError()) {
              decodeClean = false;
              prta(Util.clear(tmpsb).append("addBlock: decode block had sample count error ").append(ms));
            }
          }
        }
      } catch (SteimException e) {
        prta(Util.clear(tmpsb).append("addBlock: gave steim decode error. ").append(e.getMessage()).append(" ").append(ms));
        decodeClean = false;
      } catch (RuntimeException e) {
        prta(Util.clear(tmpsb).append("addBlock: runtime e=").append(e).append(" ").append(seedname).
                append(" off=").append(offset).append(" ms=").
                append(ms).append(" ").append(toString()));
        if (par == null) {
          e.printStackTrace();
        } else {
          e.printStackTrace(par.getPrintStream());
        }
        //throw e;
      }
    } else if (ms instanceof TraceBuf) {
      samples = ((TraceBuf) ms).getData();
    }
    int ns = 0;
    try {
      //reverse = samples[samples.length-1];
      // if the offset calculated is negative, shorten the transfer to beginning
      //prt("offset="+offset+" ms.nsamp="+ms.getNsamp()+" bufsiz="+nsamp);
      if (samples != null) {
        if (offset < 0) {
          if (ms.getNsamp() + offset > 0) {    // was -1
            ns = Math.min(ms.getNsamp() + offset, data.length + offset);
            if (dbgmem) {
              if (!testSetData(samples, -offset, data, 0, ns, sb) || seedname.equals("NTTUC  SSFR0")) {
                prta(Util.clear(tmpsb).append("addBlock: *** Load2 not ok, ").append(seedname).
                        append(" offset=").append(offset).append("/").
                        append(getIndexOfTime(ms.getTimeInMillis())).append(" ").
                        append(sb).append("\n").append(times.toStringDetail()));
              } else if (dbg) {
                prta(Util.clear(tmpsb).append("addBlock: Load2 ok ").
                        append(seedname).append(" offset=").append(offset).append(" ").append(sb));
              }
            } else if (ns > 0) {
              System.arraycopy(samples, -offset, data, 0, ns);
            }

            if (ns >= nsamp) {
              nsamp = ns;
            }
          } else {
            ns = 0;
          }
        } else if (data.length - offset >= 0) {    // Is this within the end of the buffers
          ns = Math.min(ms.getNsamp(), data.length - offset);
          boolean update = times.addTime(offset, ms.getTimeInMillis(), start);
          if (dbgmem) {
            if (!testSetData(samples, 0, data, offset, ns, sb)) {
              prta(Util.clear(tmpsb).append("addBlock:*** Load not ok, ").append(seedname).
                      append(" offset=").append(offset).append(" ").append(sb).append("\n").
                      append(times.toStringDetail()));
            } else if (dbg) {
              prta(Util.clear(tmpsb).append("addBlock: Load ok ").append(seedname).
                      append(" offset=").append(offset).append(" ").append(sb));
            }
          } else if (ns > 0) {
            System.arraycopy(samples, 0, data, offset, ns);
          }
          if (offset + ns >= nsamp) {
            nsamp = offset + ns;
          }
        } else {
          ns = 0;    // No, data is past end, so nothing to do
        }
        if (ns > 0) {
          runs.add(ms);
        }
        if (ns != ms.getNsamp() && dbg) {
          prta(Util.clear(tmpsb).append("addBlock:  * Short load of start=").append(Util.ascdatetime2(start, null)).
                  append(" ms=").append(ms.toString()).append(" ns=").append(ns).append(" len=").
                  append(nsamp).append(" off=").append(offset));
        }
        if (dbgmem) {
          if (!testNsamp()) {
            prta("addBlock: TestNsamp() failed in addBlock");
          }
        }
        return (ns == ms.getNsamp());
      }

    } catch (RuntimeException e) {
      prta(Util.clear(tmpsb).append("addBlock: runtime e=").append(e).append(" ").append(seedname).
              append(" off=").append(offset).append(" ns=").append(ns).append(" ms=").
              append(ms).append(" ").append(toString()));
      if (par == null) {
        e.printStackTrace();
      } else {
        e.printStackTrace(par.getPrintStream());
      }
      //throw e;
    }
    return false;
  }

  private synchronized boolean testNsamp() {
    for (int i = data.length - 1; i >= 0; i--) {
      if (data[i] != fillValue) {
        return nsamp == i + 1;
      }
    }
    return nsamp == 0;
  }

  /**
   * This is a test routine which can be substituted where System.arraycopy is used to put data in
   * the buffer. It checks each samples to see if its replacing a fill value (okay), or is the same
   * as the sample being replaced (also okay especially for buffers from MiniSEED and raw
   *
   * @param samples new data samples
   * @param soff starting offset in samples to move
   * @param data the data array receiving new data
   * @param offset the offset in data where the first sample point toes
   * @param ns the number of new samples
   * @param sb a StringBuilder with the status of this operation
   * @return True if there are no differences (all the same or replacing fill values)
   */
  private synchronized boolean testSetData(int[] samples, int soff, int[] data, int offset, int ns, StringBuilder sb) {
    if (sb.length() > 0) {
      sb.delete(0, sb.length());
    }
    int ndiff = 0;
    int nsame = 0;
    int nfill = 0;
    int first = -1;
    for (int i = 0; i < ns; i++) {
      if (data[i + offset] == fillValue) {
        nfill++;
      } else if (samples[i + soff] == data[i + offset]) {
        nsame++;
      } else {
        if (first < 0) {
          first = i;
        }
        ndiff++;
      }
      data[i + offset] = samples[i + soff];
    }
    sb.append(seedname).append(" nsame=").append(nsame).append(" ndiff=").append(ndiff).
            append(" nfill=").append(nfill).append(" ns=").append(ns).append(" off=").append(first);
    return (ndiff == 0);
  }

  public synchronized boolean testRunsList(StringBuilder sb2) {
    boolean inGap = true;
    if (sb2.length() > 0) {
      sb2.delete(0, sb2.length());
    }
    runs.trim(start);
    int ncon = runs.consolidate();
    sb2.append("ncon=").append(ncon);
    boolean[] ok = new boolean[runs.size()];
    int ngap = 0;
    int ndata = 0;
    int gapStart;
    int runStart = 0;
    //GregorianCalendar gapTime = new GregorianCalendar();
    for (int i = 0; i < data.length; i++) {
      if (i == data.length - 1) {
        if (inGap) {
          continue;     // in a gap so nothing to do
        } else {
          doGap(sb2, runStart, data.length - 1, ok);
        }
        continue;
      }
      if (data[i] == fillValue) {
        if (inGap) {
          ngap++;
        } else {
          inGap = true;
          ngap = 0;
          gapStart = i;
          doGap(sb2, runStart, gapStart, ok);
        }
      } else {    // Not a fill
        if (inGap) { // if we are in a fill, then this must be the first sample of new data
          inGap = false;  // Mark not in gap
          runStart = i;   // this is the beginning of the unr
          ndata = 0;
        } else {
          ndata++;
        }
      }
    }
    // Are all of the runs o.k.
    boolean allOk = true;
    for (int i = 0; i < runs.size(); i++) {
      sb2.append(i).append(" ").append(runs.get(i)).append(" ").append(ok[i] ? " O.K." : " *** NOT FOUND ***").append("\n");
      if (!ok[i]) {
        allOk = false;
      }
    }
    if (!allOk) {
      sb2.append("  TestRuns :  are NOT RIGHT.\n").append(sb2.toString());
      return false;
    } else {
      return true;
    }
  }

  private void doGap(StringBuilder sb, int runStart, int gapStart, boolean[] ok) {
    // Have the startof a gap, calculate times and look for a run
    long st = getMillisAt(runStart);
    long end = getMillisAt(gapStart);
    if (st == end && runStart == 0) {
      return;
    }
    int pos = sb.length();
    sb.append(seedname).append(" Found run from ").append(Util.ascdatetime2(st, null)).
            append("-").append(Util.ascdatetime2(end, null))
            .append(" ").append((end - st) / 1000.).append("s\n");
    boolean found = false;
    for (int j = 0; j < runs.size(); j++) {
      sb.append("j=").append(j).append(" stdiff=").append(runs.get(j).getStart() - st).
              append(" enddiff=").append(runs.get(j).getEnd() - end).append(" ").
              append(runs.get(j).toString());
      if (Math.abs(runs.get(j).getStart() - st) <= msover2 + 1
              && Math.abs(runs.get(j).getEnd() - end) <= msover2 + 1) {
        ok[j] = true;
        found = true;
        sb.append(" matches ");
      } else {
        sb.append(" *");
      }
      sb.append("\n");
    }
    if (found) {
      sb.delete(pos, sb.length());  // This reduces output, but not including details of gaps that match!
    }
  }

  /**
   * Return as many trace bufs as can be made from this span within the time limits. Designed for
   * use in CWBQuery server so a TraceBufPools is required for allocations and the user passes in
   * the ArrayList to get the results. Data on list will be in order.
   *
   * @param start Return as many trace bufs starting after this time
   * @param duration Duration of the request
   * @param tbp A pool to use to allocate tracebufs
   * @param tbs The users array list to get the results.
   */
  public synchronized void makeTraceBuf(long start, double duration, TraceBufPool tbp, ArrayList<TraceBuf> tbs) {
    //if(seedname.equals("NTBOU  MVHR1")) dbgmem=true;
    if (runs.size() > 1) {
      runs.consolidate();       // insures no overlaps and that runs are sorted
    }
    long endRequest = start + (long) (duration * 1000. + 0.5); // the duration + 1/2 of a i nterval
    int offset, lastoffset = -1;
    int ns;
    for (int j = 0; j < runs.size(); j++) {
      Run run = runs.get(j);
      long st = Math.max(run.getStart(), start);   // Earliest time in run to use
      long end = Math.min(run.getEnd(), endRequest);
      //String s= ;
      if (dbgmem) {
        prt(Util.clear(tmpsb).append("makeTrace: ").append(seedname).append(" ").append(j).
                append("/").append(runs.size()).append(" ").append(run.toString()).append(" req=").
                append(Util.ascdatetime2(start, null)).append("-").append(Util.ascdatetime2(endRequest, null)).
                append(" overlap=").append(end - st).append(" memtsb=").append(Util.ascdatetime2(st, null)).
                append("-").append(Util.ascdatetime2(end, null)));
      }
      offset = times.getIndexOfTime(st);

      // insure no overlaps from TB to TB
      if (lastoffset != -1 && offset <= lastoffset) {
        prta(Util.clear(tmpsb).append("maketrace:").append(seedname).append(" ** adjust offset from ").
                append(offset).append(" to ").append(lastoffset + 1).append(" ").
                append(Util.ascdatetime2(st)));
        offset = lastoffset + 1;   // If we sent a sample already in this interval, do not send it again
        prta(this.toStringBuilder(Util.clear(tmpsb)));
        prta(runs.get(j - 1).toStringBuilder(Util.clear(tmpsb)));
        prta(runs.get(j).toStringBuilder(Util.clear(tmpsb)));
      }
      st = times.getMillisAt(offset);
      if (dbgmem) {
        prt(Util.clear(tmpsb).append("makeTrace: ").append(seedname).append(" offset=").
                append(offset).append(" st=").append(st).append(" ").append(Util.ascdatetime2(st, null)).
                append(" off2=").append(times.getIndexOfTime(st)).append(" st2=").
                append(times.getMillisAt(times.getIndexOfTime(st))));
      }
      long lastst = st;
      while (st <= end) {
        ns = (int) ((end - st + msover2 * 3) / 1000. * rate);
        if (ns <= 0) {
          break;
        }
        if (ns > TraceBuf.TRACE_MAX_NSAMPS) {
          ns = TraceBuf.TRACE_MAX_NSAMPS;
        }
        TraceBuf tb = tbp.get();
        offset = getIndexOfTime(st);
        if (dbgmem) {
          prta(Util.clear(tmpsb).append("maketrace:").append(seedname).append(" off=").append(offset).
                  append(" st=").append(Util.ascdatetime2(st, null)).append(" end=").append(Util.ascdatetime2(end, null)).
                  append(" ns=").append(ns).append("/").append(tbs.size()).
                  append(" ").append((long) (st - lastst - ns / rate * 1000 + 0.001)).
                  append(" [0]=").append(data[offset]).
                  append(ns > 1 ? " [1]=" + data[offset + 1] : "").
                  append(ns > 2 ? " [2]=" + data[offset + 2] : "").
                  append(ns > 3 ? " [3]=" + data[offset + 3] : ""));
        }
        lastst = st;
        while (data[offset + ns - 1] == this.fillValue && ns > 0) {  // There is a fille at end sometime, clear it.
          ns--;
          //prta("makeTrace: dropping last data fill at ns="+ns);
        }
        st += (long) (Math.max(ns, 1) / rate * 1000. + 0.1);   // must advance 1 sample to eliminate boundary conditions on end

        // DEBUG: check for any fill Values or really big data
        /*int min=Integer.MAX_VALUE;
        int max=Integer.MIN_VALUE;
        boolean dumpit=false;
        for(int i=0; i<ns; i++) {
          if(data[i+offset] == fillValue) {
            prta("makeTrace: ** Fill value found at i="+i+
                  " off="+offset+" "+seedname+" "+Util.ascdatetime2(lastst));
            dumpit=true;
          }
          if(data[i+offset] > max) max=data[i+offset];
          if(data[i+offset] < min) min=data[i+offset];
        } 
        if(Math.abs(max-min) > 536870912) {
          prta("makeTrace: ** Large value min="+min+" max="+max+
                " "+seedname+" "+Util.ascdatetime2(lastst));
          dumpit=true;
        }*/
        if (ns == 0) {
          tbp.free(tb);
          continue;
        }  // not going to use this trace buf
        tb.setData(seedname, lastst, ns, rate, data, offset, 0, 0, 0, 0);
        lastoffset = offset + ns - 1;        // The index of the last sample sent
        /*if(dumpit) {
          prt(tb.toString());
          StringBuilder sbt = new StringBuilder(10000);
          sbt.append("0:");
          for(int i=0; i<ns; i++) {
            sbt.append(Util.leftPad(""+tb.getData()[i], 7)).append(i % 10 == 9?"\n"+i+":":"");
          }
          prt(sbt.toString());
        }*/
        if (ns == 1 && data[offset] == this.fillValue) {
          prta(Util.clear(tmpsb).append(seedname).append(" ").append(Util.ascdatetime2(st, null)).
                  append(" maketrace: **** 1 sample packet with only fill!!!!"));
          tbp.free(tb);  // Not going to use this tracebuf
        } else {
          tbs.add(tb);
        }
      }
    }
  }

  public static void main(String[] args) {
    // Test for Query span
    Util.setModeGMT();
    Util.init("edge.prop");
    GregorianCalendar g = new GregorianCalendar();
    g.setTimeInMillis(g.getTimeInMillis() / 1800000 * 1800000 - 600000); // a 1/2 hour ago less 10 minutes
    StringBuilder channel = new StringBuilder(12);
    channel.append("USDUG  BHZ00");
    QuerySpanCollection qsc = new QuerySpanCollection("-h 137.227.224.97 -d 3600 -dbgchan USDUG..BHZ00 >>qsc", "QSC");
    int[] data = new int[400];
    for (int i = 0; i < 400; i++) {
      data[i] = i * 10;
    }
    for (int i = 0; i < 10; i++) {
      Util.prt("add " + Util.ascdatetime2(g) + " 400 samps");
      QuerySpanCollection.add(channel, g.getTimeInMillis(), data, 400, 40.);
      g.setTimeInMillis(g.getTimeInMillis() + 10000);
    }
    QuerySpan span = qsc.getQuerySpanThread(channel).getQuerySpan();
    g.setTimeInMillis(g.getTimeInMillis() + 86400000L);
    Util.prt("Mid " + qsc.getQuerySpanThread(channel).getQuerySpan());
    for (int i = 0; i < 10; i++) {
      Util.prt("add " + Util.ascdatetime2(g) + " 400 samps");
      QuerySpanCollection.add(channel, g.getTimeInMillis(), data, 400, 40.);
      g.setTimeInMillis(g.getTimeInMillis() + 10000);
    }
    Util.prt("End " + qsc.getQuerySpanThread(channel).getQuerySpan());
    int nfill = 0;
    int firstData = -1;
    for (int i = 0; i < span.getLastData(); i++) {
      if (span.getData(i) == QuerySpan.FILL_VALUE) {
        nfill++;
      } else if (firstData == -1) {
        firstData = i;
      }
    }

    Util.prt("End of execution 1st data=" + firstData + " nfill=" + nfill + "/" + span.getLastData()
            + " " + span.getNsamp() + " " + Util.ascdatetime2(span.getMillisAt(firstData)));
  }
}
