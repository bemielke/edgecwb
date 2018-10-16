/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.cwbquery;

import gov.usgs.adslserverdb.MDSChannel;
import gov.usgs.anss.seed.MiniSeed;
import gov.usgs.anss.edgethread.EdgeThread;
import gov.usgs.anss.cwbqueryclient.EdgeQueryClient;
import gov.usgs.anss.cwbqueryclient.ZeroFilledSpan;
import gov.usgs.anss.util.Util;
import gov.usgs.anss.util.StaSrv;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;

/**
 * This class keeps a buffer of a certain length of in the resetSpan data and satisfies requests
 * from it or if it is executed as part of a RAM based QueryMom will satisfy requests from the RAM
 * QuerySpans if the request is in the window.
 *
 * When data is needed which is not in the RAM buffer, a query is made and more data read in from a
 * preDuration for a duration in length. The class is best used to process data in chunks
 * continuously from some start time, or for processing data continuously near real time. This is a
 * subclass of ZeroFilledSpan which is used to hold and manage the data in the resetSpan. * You can
 * think of this as a little window of data that you can slide around by asking for different times.
 * It hides all of the details of doing queries and converting to in the resetSpan data and handling
 * gaps in the data.
 *
 * Its a good batch class for getting time series from the CWB.
 *
 * @author davidketchum
 */
public final class QueryRing {

  private static StaSrv stasrv;
  private static final Integer stasrvmutex = Util.nextMutex();
  public static int FILL_VALUE = 2147000000;
  private String seedname;      // The seed channel that is buffered and returne by this object
  private StringBuilder seednamesb = new StringBuilder(12);
  private final String host;
  private final int port;
  private final double duration;
  private double rate;
  private final double preDuration;
  private GregorianCalendar now = new GregorianCalendar();  // This is used to do time computations in chkData()
  private QuerySpan oldSpan, ramSpan;
  private EdgeThread par;
  private ArrayList<MiniSeed> mslist = new ArrayList<>(100);  // used in chkData to put blocks from query
  private boolean dbg;
  private static String debugChannel = "";
  private final StringBuilder tmpsb = new StringBuilder(50);

  public static void setDebugChan(String s) {
    debugChannel = s;
  }

  public void setDebug(boolean t) {
    dbg = t;
    if (oldSpan != null) {
      oldSpan.setDebug(t);
      oldSpan.setDebugMem(dbg);
    }
  }

  private void prt(String s) {
    if (par == null) {
      Util.prt(s);
    } else {
      par.prt(s);
    }
  }

  private void prta(String s) {
    if (par == null) {
      Util.prta(s);
    } else {
      par.prta(s);
    }
  }

  private void prt(StringBuilder s) {
    if (par == null) {
      Util.prt(s);
    } else {
      par.prt(s);
    }
  }

  private void prta(StringBuilder s) {
    if (par == null) {
      Util.prta(s);
    } else {
      par.prta(s);
    }
  }

  public void setUseWaveRaw(boolean b, boolean c) {
    if (oldSpan != null) {
      oldSpan.setUseWaveRaw(b, c);
    }
  }

  public boolean isWinston() {
    if (oldSpan != null) {
      return oldSpan.isWinston();
    }
    return false;
  }

  @Override
  public String toString() {
    return toStringBuilder(null).toString();
  }

  public final StringBuilder toStringBuilder(StringBuilder tmp) {
    StringBuilder sb = tmp;
    if (sb == null) {
      sb = Util.clear(tmpsb);
    }
    synchronized (sb) {
      sb.append("QR:old=").append((oldSpan == null ? "null" : oldSpan.toStringBuilder(null))).
              append(" ram=").append((ramSpan == null ? "null" : ramSpan.toStringBuilder(null)));
    }
    return sb;
  }

  public QuerySpan getQuerySpan() {
    return oldSpan;
  }

  public QuerySpan getRamQuerySpan() {
    return ramSpan;
  }

  public double getRate() {
    if (ramSpan != null) {
      if (ramSpan.getRate() > 0.) {
        rate = ramSpan.getRate();
        return rate;
      }
    } else if (oldSpan != null) {
      if (oldSpan.getRate() > 0.) {
        rate = oldSpan.getRate();
        return rate;
      }
    }
    return rate;
  }

  /**
   * Creates a new instance of QueryRing set the duration to make the reads not happen too often and
   * the preDuration to be how far in the past you might ask for data from the normal near-real time
   * processing time.
   *
   * @param h The host where the query server is running, if null or "", its gcwb.cr.usgs.gov
   * @param p The port where the cwb is running, if zero, defaults to 2061
   * @param st The starting time for the initialization
   * @param dur The length of the ring buffer in seconds.
   * @param preDur the length of time before the "real time" to make sure is available
   * @param name The seed name of the data channel.
   * @param rt If not zero, the rate of this channels - saves an MDS lookup if known
   * @param parent An edgethread to use for logging.
   */
  //  public QuerySpan(String host, int port, String seedname, long st, double dur, double preDur, double rate, EdgeThread parent) {
  public QueryRing(String h, int p, String name, GregorianCalendar st,
          double dur, double preDur, double rt, EdgeThread parent) {
    par = parent;
    //if(h == null) h="gcwb.cr.usgs.gov";
    if (h != null) {
      if (h.equals("")) {
        h = "gcwb.cr.usgs.gov";
      }
    }
    seedname = name;
    if (name.equals(debugChannel)) {
      dbg = true;
    }
    Util.clear(seednamesb).append(seedname);
    ramSpan = QuerySpanCollection.getQuerySpan(seednamesb);
    host = h;
    port = p;
    if (ramSpan != null) {
      rate = ramSpan.getRate();
    }
    prta(Util.clear(tmpsb).append("QR: init ").append(name).append("dur=").append(dur).
            append(" predur=").append(preDur).append(" rt=").append(rt).append(" rate=").append(rate));
    if (rate <= 0. && rt > 0.) {
      rate = rt;
    }
    if (rate <= 0. || (rt > 0 && Math.abs(rate/rt - 1.)  > 0.0001)) {
      ArrayList<MDSChannel> mdslist = new ArrayList<>(10);
      synchronized (stasrvmutex) {
        if (stasrv == null) {
          stasrv = new StaSrv("cwbpub.cr.usgs.gov", 2052);
        }
        try {
          prta(Util.clear(tmpsb).append("QR: ").append(h).append("/").append(p).append(" ").
                  append(seedname).append(" ramspan null MDS look up "));
          MDSChannel.loadMDSResponse(stasrv.getSACResponse(name, Util.ascdatetime2(st).toString().replaceAll(" ", "-"), "um"), mdslist);
        } catch (RuntimeException e) {
          e.printStackTrace(par.getPrintStream());
        }
      }
      if (mdslist.size() > 0) {
        rate = mdslist.get(0).getRate();
      }
      if (rate <= 0.) {
        prta(Util.clear(tmpsb).append("**** Channel not found in MDS rate is guessed ").append(seedname));
        rate = 100.;
      }
    }
    if (rate <= 0.) {
      rate = 100.;
    }

    // Perform rationality checks on duration and preDuration
    if (dur > 86400.) {
      prta(Util.clear(tmpsb).append("QR: *** cons duration too long limit to 24 hoours ").
              append(seedname).append(" ").
              append(Util.ascdatetime2(st)).append(" dur=").append(dur));
      dur = 86400.;
    }
    if (dur < 2100 / rate && rate > 0.) {
      dur = (int) ((2160. / rate) + 1000); // minimum length is at least 3 miniseed packets at the rate
      prta(Util.clear(tmpsb).append("QR: * adjust length to minimum based on rate=").append(rate).
              append(seedname).append(" ").append(" dur=").append(dur));
    }
    if (preDur / dur < 0.05) {
      preDur = dur / 20.;
      prta(Util.clear(tmpsb).append("QR: * adjust preduration to be at least 5 % of duration=").
              append(dur).append(seedname).append(" ").append(" pre=").append(preDur));
    }
    if (preDur / dur > 0.50) {
      preDur = dur / 2.;
      prta(Util.clear(tmpsb).append("QR: * adjust preduration to be less than 50% of duration=").
              append(dur).append(" pre=").append(preDur));
    }
    duration = dur;
    preDuration = preDur;
    prta(Util.clear(tmpsb).append("QR: create ").append(h).append("/").append(p).append(" ").
            append(seedname).append(" ramspan=").append(ramSpan).append(" rate=").append(rate).
            append(" dur=").append(duration).append(" preDur=").
            append(preDur));

    // If the QuerySpan does not exist, create it
    if (ramSpan != null) {
      prta(Util.clear(tmpsb).append("QR: ").append(h).append("/").append(p).append(" ").
              append(seedname).append(" ramspan=").append(ramSpan));
      if (ramSpan.getMillisAt(0) > st.getTimeInMillis()) {   // is existing before this start time, replace it
        prta(Util.clear(tmpsb).append("QR: cons create oldSpan ").append(seedname).append(" ").
                append(Util.ascdatetime2(st)).append(" dur=").append(duration));
        oldSpan = new QuerySpan(h, p, name, st.getTimeInMillis(), duration, preDuration, ramSpan.getRate(), par);
        oldSpan.realTimeLoad(st.getTimeInMillis(), duration);
        prta(Util.clear(tmpsb).append("QR: cons oldSpan=").append(oldSpan));
        rate = ramSpan.getRate();
      }
    } else {
      if (seedname.equals(QuerySpan.getDebugChan())) {
        dbg = true;
      }
      oldSpan = new QuerySpan(h, p, name, st.getTimeInMillis(), duration, preDuration, rate, par);
      oldSpan.realTimeLoad(st.getTimeInMillis(), duration * .75);
      if (oldSpan.getRate() > 0.) {
        rate = oldSpan.getRate();
      }
    }

    prta(Util.clear(tmpsb).append("QR: created ").append(toStringBuilder(null)));
  }

  /**
   * return a chunk of time series starting at a given time for a number of samples. The start time
   * is modified to be the time of the first sample returned.
   *
   * @param starting The starting time as a GregorianCalendar (at return the time of the first
   * sample)
   * @param nsamp The number of samples to return
   * @param d A data array which must be at least nsamp long where the data are returned.
   * @param allowGaps If true, then the data are returned for the whole interval with no-data
   * values, if false, only data available to the first gap is returned
   * @return The number of samples actually returned, will be ^lt nsamp if the data are not yet
   * available.
   * @throws java.io.IOException
   */
  public int getDataAt(GregorianCalendar starting, int nsamp, double[] d, boolean allowGaps) throws IOException {
    QuerySpan sp = chkData(starting, nsamp);
    if (sp != null) {
      return sp.getData(starting, nsamp, d, allowGaps);
    } else {
      return 0;
    }
  }

  /**
   * return a chunk of time series starting at a given time for a number of samples.The start time
   * is modified to be the time of the first sample returned.
   *
   * @param starting The starting time as a GregorianCalendar (at return the time of the first
   * sample)
   * @param nsamp The number of samples to return
   * @param d A data array which must be at least nsamp long where the data are returned.
   * @param allowGaps If true, then the data are returned for the whole interval with no-data
   * values, if false, only data available to the first gap is returned
   * @return The number of samples actually returned, will be < nsamp if the data are not yet
   * available. @throws java.io.IOException
   */
  public int getDataAt(GregorianCalendar starting, int nsamp, float[] d, boolean allowGaps) throws IOException {
    QuerySpan sp = chkData(starting, nsamp);
    if (sp != null) {
      return sp.getData(starting, nsamp, d, allowGaps);
    } else {
      return 0;
    }
  }

  /**
   * return a chunk of time series starting at a given time for a number of samples.The start time
   * is modified to be the time of the first sample returned.
   *
   * @param starting The starting time as a GregorianCalendar (at return the time of the first
   * sample)
   * @param nsamp The number of samples to return
   * @param d A data array which must be at least nsamp long where the data are returned.
   * @param allowGaps If true, then the data are returned for the whole interval with no-data
   * values, if false, only data available to the first gap is returned
   * @return The number of samples actually returned, will be < nsamp if the data are not yet
   * available. @throws java.io.IOException
   */
  public int getDataAt(GregorianCalendar starting, int nsamp, int[] d, boolean allowGaps) throws IOException {
    QuerySpan sp = chkData(starting, nsamp);
    if (sp != null) {
      return sp.getData(starting, nsamp, d, allowGaps);
    } else {
      return 0;
    }
  }

  /**
   * get the time of the next data after the given time. It will return it or the last data time in
   * the span
   *
   * @param afterTime
   * @param endtime If not null, the time at which to quit searching
   * @return The time of data after this time in the span
   * @throws IOException
   */
  public long getTimeOfNextDataAfter(GregorianCalendar afterTime, GregorianCalendar endtime) throws IOException {
    for (;;) {
      QuerySpan sp = chkData(afterTime, 1);     // get the right span
      long now2 = System.currentTimeMillis();
      if (sp == null) {
        afterTime.setTimeInMillis(afterTime.getTimeInMillis() + (long) (oldSpan.getDuration() * 900));
        if (afterTime.getTimeInMillis() > endtime.getTimeInMillis()) {
          return endtime.getTimeInMillis();
        }
        if (afterTime.getTimeInMillis() > now2) {
          return now2;
        }
        prta(Util.clear(tmpsb).append("getTimeOfNextDataAfter ").append(Util.ascdatetime2(afterTime)).append(" ").append(oldSpan.toStringBuilder(null)));
      } else {
        return sp.getTimeOfNextDataAfter(afterTime.getTimeInMillis());
      }
    }
  }

  /**
   * Check whether the data is already in this object or if RAM span is available in the RAM span.
   * If not, make a request to make it so.
   *
   * @param starting The starting time as a GregorianCalendar (at return the time of the first
   * sample)
   * @param nsamp The number of samples to return
   *
   */
  private synchronized QuerySpan chkData(GregorianCalendar starting, int nsamp) throws IOException {
    // If we have a ramSpan, check to see if it contains the data
    if (ramSpan == null) {
      ramSpan = QuerySpanCollection.getQuerySpan(seednamesb);
    }
    if (ramSpan != null) {
      if (ramSpan.getMillisAt(0) <= starting.getTimeInMillis()) {
        return ramSpan;
      }
    }

    // Using the oldSpan object, try to put the data from disk into it to satisfy the request
    boolean done = false;
    if (oldSpan == null) {
      prta(Util.clear(tmpsb).append("QR: chkData() create oldSpan ").append(seedname).append(" ").
              append(Util.ascdatetime2(starting)).append(" dur=").append(duration));
      if (duration > 86400.) {
        prta(Util.clear(tmpsb).append("***duration is way out of range how can this be ").append(seedname).append(" ").
                append(Util.ascdatetime2(starting)).append(" dur=").append(duration));
      }
      oldSpan = new QuerySpan(host, port, seedname, starting.getTimeInMillis(),
              Math.min(86400., duration), preDuration, getRate(), par);
      oldSpan.realTimeLoad(starting.getTimeInMillis(), duration);
      if (seedname.equals(QuerySpan.getDebugChan())) {
        dbg = true;
      }
      oldSpan.setDebug(dbg);
      oldSpan.setDebugMem(dbg);
      prta(Util.clear(tmpsb).append("QR: chkData() oldSpan=").append(oldSpan));
    }
    mslist.clear();
    for (int loop = 0; loop < 120; loop++) {
      if (dbg) {
        prta(Util.clear(tmpsb).append("Ask for data at ").append(Util.ascdatetime2(starting.getTimeInMillis())).
                append(" ns=").append(nsamp).append(" start=").append(Util.ascdatetime2(oldSpan.getMillisAt(0))).
                append(" lastTime=").append(Util.ascdatetime2(oldSpan.getLastTime())));
      }
      // If the data is time entirely in the buffer, return true, else false
      if (starting.getTimeInMillis() - oldSpan.getMillisAt(0) > -oldSpan.getMSOver2() - 2
              && starting.getTimeInMillis() + nsamp / oldSpan.getRate() * 1000 < oldSpan.getLastTime()) {
        return oldSpan;  // its good
      }
      if (starting.getTimeInMillis() - oldSpan.getMillisAt(0) < -oldSpan.getMSOver2() - 2 || oldSpan.getNsamp() == 0) {
        if (dbg) {
          prta(Util.clear(tmpsb).append("Data request before beginning of oldpsan oldspan=").
                  append(Util.ascdatetime2(oldSpan.getMillisAt(0))).append(" want ").
                  append(Util.ascdatetime2(starting)).append(" dur=").append(oldSpan.getDuration()));
        }
        now.setTimeInMillis(starting.getTimeInMillis());
        now.add(Calendar.MILLISECOND, (int) (-oldSpan.getPreDuration() * 1000.));
        if (dbg) {
          prta(Util.clear(tmpsb).append("Query for data start=").append(Util.ascdatetime2(now)).
                  append(" dur=").append(oldSpan.getDuration()).append(" predur=").append(oldSpan.getPreDuration()));
        }
        oldSpan.doQuery(now.getTimeInMillis(), oldSpan.getDuration(), mslist);
        if (mslist.isEmpty()) {
          if (dbg) {
            prtError(now, oldSpan.getDuration());
          }
          return null;
          //Util.sleep(1000);
          //continue;
        } else {
          oldSpan.refill(mslist, now, oldSpan.getDuration());
        }
        oldSpan.freeBlocks(mslist);
        mslist.clear();
      } else if (starting.getTimeInMillis() + nsamp / oldSpan.getRate() * 1000 >= oldSpan.getLastTime() - 1000) {// is this past the data in the buffer
        // We just need to fill in some data
        if (dbg) {
          prta(Util.clear(tmpsb).append(seedname).append(" Data ask is off end start=").
                  append(Util.ascdatetime2(oldSpan.getMillisAt(0))).
                  append(" dur=").append(oldSpan.getDuration()).append(" ").append(oldSpan.toStringBuilder(null)));
        }
        // We should query from the lastTime (last data sample put in the buffer, or the beginning of the buffer which ever is later
        now.setTimeInMillis(Math.max(oldSpan.getLastTime(), starting.getTimeInMillis()));     // set to last value put in

        // Ask for data length that will fill the buffer from last data we have to end of buffer (or start if last data is too old
        long endingTime = (long) (starting.getTimeInMillis() + nsamp / oldSpan.getRate() * 1000);
        //double secs = (Math.max(endingTime, oldSpan.getMillisAt(oldSpan.getData().length - 1)) - now.getTimeInMillis() + 1000) / 1000.;
        //if(secs > duration - preDuration) 
        double secs = (duration - preDuration) * .8;    // Never ask for more the duration - preduration
        if (dbg) {
          prta(Util.clear(tmpsb).append("Query for data " + " predur=").append(oldSpan.getPreDuration()).
                  append(" queryTime=").append(Util.ascdatetime2(now)).
                  append(" dur=").append(secs).append(" ").append(oldSpan));
        }

        oldSpan.realTimeLoad(now.getTimeInMillis(), secs);
      }
      // If the data is time entirely in the buffer, return true, else false
      if (starting.getTimeInMillis() - oldSpan.getMillisAt(0) > -oldSpan.getMSOver2() - 1
              && starting.getTimeInMillis() + nsamp / oldSpan.getRate() * 1000 < oldSpan.getLastTime()) {
        return oldSpan;
      }
      if (loop > 120) {
        throw new IOException("Cannot get data from CWB");
      }
      return null;
    }
    return null;
  }
  StringBuilder errsb = new StringBuilder(100);

  private void prtError(GregorianCalendar st, double secs) {
    Util.clear(errsb);
    errsb.append("Query return null! args=").append(Util.ascdatetime2(st)).append(" ").append(secs);
    prta(errsb);
  }

  /**
   * test routine - This reads in a whole day of data from the CWB using the normal query method to
   * get an ArrayList of ArrayList of MiniSeed and puts the miniSEED into a ZeroFilledSpan.
   *
   * In then uses a QueryRing to open the beginning of the day an march through it comparing the
   * data by time as it goes.
   *
   * @param args The args
   */
  public static void main(String[] args) {
    Util.setModeGMT();
    boolean useWaveRaw = true;
    boolean useCompression = true;
    boolean debug = false;
    String host = "137.227.224.97";
    int port = 2061;
    //int port=16002;
    // Make up a time in 2013
    GregorianCalendar time = new GregorianCalendar(); // remember 03 is April in GregorianCalendars
    time.setTimeInMillis(System.currentTimeMillis() / 86400000L * 86400000L - 86400000L);// yesterday
    // Set up a query ring for the 
    //QueryRing ring = new QueryRing("137.227.224.97",2061,  "USDUG  BHZ00", time,600., 30., 0., null); // User CWB
    QueryRing ring = new QueryRing(host, port, "USDUG  BHZ00", time, 600., 30., 0., null); // Using GETSCNL
    if (ring.isWinston()) {
      ring.setUseWaveRaw(useWaveRaw, useCompression);
    }
    ring.setDebug(debug);

    // Test jump ahead
    int[] d1 = new int[400];
    time.setTimeInMillis(time.getTimeInMillis() + 30000);
    try {
      int ns2 = ring.getDataAt(time, 400, d1, false);
      time.setTimeInMillis(time.getTimeInMillis() + 3600000);
      int ns3 = ring.getDataAt(time, 400, d1, false);
      time.setTimeInMillis(time.getTimeInMillis() - 3600000);
      int ns4 = ring.getDataAt(time, 400, d1, false);
      Util.prt("ns2=" + ns2 + " ns3=" + ns3 + " ns4=" + ns4);
    } catch (IOException e) {
      e.printStackTrace();
    }

    // Make up a string for this same time to get the whole day using a normal query
    // Get the MiniSeed for the data from the query server for a whole day and compare it to data
    // gotten by the QueryRing which is marching through the day - looking for mismatched gaps or data
    String s = "-h " + host + " -s USDUG..BHZ00 -b " + Util.ascdatetime(time).toString().replaceAll(" ", "-") + " -d 86400 -t null";
    ArrayList<ArrayList<MiniSeed>> mslist = EdgeQueryClient.query(s); // Get a days worth of data
    // Normally the returned list will only contain one Arraylist<MiniSeed>>
    if (mslist.size() >= 1) {
      // create a Span with the full day of data and user FILL_VALUE=2147000000 instead of zero for the fill value
      ZeroFilledSpan span = new ZeroFilledSpan(mslist.get(0), FILL_VALUE);// Put day in a ZFS
      Util.prta("ring=" + ring);      // Show the initial state of the QueryRing
      Util.prta("span=" + span);      // Show the initial state of the ZeroFilledSpan
      int[] d = new int[400];
      int[] d2 = new int[400];
      // every 10 seconds for the entire day
      for (int i = 0; i < 86400; i = i + 10) {   // march through the day 10 seconds at a time
        int ns = 0;
        boolean err = false;
        try {
          ns = ring.getDataAt(time, 400, d, false);  // Get 400 samples (10 seconds) of data at this time from the ring
        } catch (IOException e) {
          Util.prt("IOerror e=" + e);
          e.printStackTrace();
        }
        int ns2 = span.getData(time, 400, d2, false);  // Get the same samples from the full span
        // check that all of the data matches
        if (ns != ns2) {
          Util.prt("i=" + i + " " + Util.ascdatetime2(time)
                  + " Did not get matching number of samples ring ns=" + ns + " span ns=" + ns2);
        }
        for (int j = 0; j < Math.min(ns2, ns); j++) {
          if (d[j] != d2[j]) {
            Util.prt(j + " " + d[j] + "!=" + d2[j]);    // If results do not match print out offset and value
            err = true;
          }
        }
        if (i % 3600 == 0 || err) {
          Util.prta("i=" + i + " " + Util.ascdate(time) + " " + Util.asctime2(time));
        }
        for (int j = 0; j < 400; j++) {
          d[j] = 0;
          d2[j] = 0;
        }    // resetSpan the arrays
        time.add(Calendar.MILLISECOND, 10000);        // Update time by 10 seconds
      }
    }

    // Second test - loop near real time - and show data from ring
    time.setTimeInMillis((System.currentTimeMillis() - 1800000) / 10000L * 10000L);
    //time =  new GregorianCalendar(2011,07,23, 0, 0, 0); // remember 03 is April in GregorianCalendars
    //time.setTimeInMillis(time.getTimeInMillis()/1000*1000);    
    Util.prta("Starting real time ring test at " + Util.ascdate(time) + " " + Util.asctime2(time));
    //ring = new QueryRing("137.227.224.97",2061, "USDUG  BHZ00", time, 500.,100., 0., null);  // remember 2 spaces to pad station name
    ring = new QueryRing(host, port, "USDUG  BHZ00", time, 500., 100., 0., null);  // remember 2 spaces to pad station name
    if (ring.isWinston()) {
      ring.setUseWaveRaw(false, false);
    }
    ring.setDebug(debug);
    int npts;
    double[] d = new double[500];      // Use doubles fetcher this time
    for (;;) {
      try {
        while ((npts = ring.getDataAt(time, 400, d, false)) != 400) {
          Util.sleep(5000);
          if (System.currentTimeMillis() - ring.getQuerySpan().getLastTime() > 300000) {
            time.setTimeInMillis(time.getTimeInMillis() + 10000);
            Util.prt("Stuck on gap for 300 secs - advance 10 secs " + Util.asctime2(time) + " npts=" + npts);
          }
        }
        // 10 seconds of data is in the buffer, do something with it here
        double min = Integer.MAX_VALUE;
        double max = Integer.MIN_VALUE;
        for (int i = 0; i < npts; i++) {
          if (d[i] > max) {
            max = d[i];
          }
          if (d[i] < min) {
            min = d[i];
          }
        }
        Util.prta(" ** Processing data for " + Util.ascdate(time) + " " + Util.asctime2(time) + " npts=" + 400 + " min=" + min + " max=" + max);
        time.setTimeInMillis(time.getTimeInMillis() + 10000);
      } catch (IOException e) {
        Util.prta("Query ring threw an exception ");
        e.printStackTrace();
      }
    }
  }
}
