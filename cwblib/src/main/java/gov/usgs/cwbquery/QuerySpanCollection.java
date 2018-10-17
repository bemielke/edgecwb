/*
 *  This software is in the public domain because it contains materials 
 *  that originally came from the United States Geological Survey, 
 *  an agency of the United States Department of Interior. For more 
 *  information, see the official USGS copyright policy at 
 *  http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.cwbquery;

import gov.usgs.anss.edgethread.EdgeThread;
import gov.usgs.anss.seed.MiniSeed;
import gov.usgs.anss.util.Util;
import gov.usgs.anss.edge.IllegalSeednameException;
import java.util.ArrayList;
import gnu.trove.map.hash.TLongObjectHashMap;
import gnu.trove.iterator.TLongObjectIterator;
import java.util.Arrays;

/**
 * This class controls the memory based QuerySpan and allows the real time processing to add blocks
 * or simple time series segments to the memory based spans.
 *
 * <PRE>
 * flag    arg     Desc
 * -h     ip.adr  The IP address of the CWB to query for data on startup (def=localhost)
 * -p     port    The port of the CWB, (def=2061).
 * -d     secs    Duration of band collections in seconds (def=3600).
 * -bands String  This string is the list of bands to allow into the server (def="BH-LH-SH-EH-HH-"),
 *                to disable band checking set this to '*'
 * -pre   pct     The amount of memory to always leave in pre current time (def=75%)
 * -load  N       Set the number of threads to use to populate memory cache from disk (def=2), if 0, no population is done.
 * -dbgchan match The match string will set all debugging on for any QuerySpans which match it (def=NONE)
 * </PRE>
 *
 * @author U.S. Geological Survey <ketchum at usgs.gov>
 */
public final class QuerySpanCollection extends EdgeThread {

  //private  final TreeMap<String, QuerySpanThread> spans = new TreeMap<String, QuerySpanThread>();
  private final TLongObjectHashMap<QuerySpanThread> spans = new TLongObjectHashMap<>(10000);
  private static QuerySpanCollection theQuerySpanCollection;
  private String host;
  private int port;
  private static double duration;
  private byte[] allowedBands = "BH-LH-SH-EH-HH-".getBytes();
  private double pctPre;
  private String dbgChan;
  protected static int nloadThread = 2;
  private final StringBuilder summary = new StringBuilder(100);

  public static double getDuration() {
    return duration;
  }

  public static QuerySpanCollection getQuerySpanCollection() {
    return theQuerySpanCollection;
  }

  public static boolean isUp() {
    return theQuerySpanCollection != null;
  }

  public static int getNchan() {
    return (theQuerySpanCollection == null ? -1 : theQuerySpanCollection.spans.size());
  }

  public StringBuilder getSummary() {
    long tot = 0;
    int curr = 0;
    int currbh = 0;
    int totalchan = 0;
    int totalbh = 0;
    synchronized (spans) {
      TLongObjectIterator<QuerySpanThread> itr = spans.iterator();
      long now = System.currentTimeMillis();
      while (itr.hasNext()) {
        itr.advance();
        QuerySpan span = itr.value().getQuerySpan();
        tot += span.getMemoryUsage();
        boolean current = (now - span.getMillisAt(span.getNsamp())) < 600000;

        if (span.getSeedname().charAt(7) == 'h' || span.getSeedname().charAt(7) == 'b') {
          if (current) {
            currbh++;
          }
          totalbh++;
        } else {
          if (current) {
            curr++;
          }
          totalchan++;
        }
      }
    }
    Util.clear(summary).append("ncurrmem=").append(curr).append("\nntotalmem=").append(totalchan).
            append("\ncurrmempct=").append((int) ((double) curr / totalchan * 100. + 0.5)).
            append("\ncurrbhmem=").append(currbh).append("\ntotalbhmem=").append(totalbh).
            append("\ncurrbhmempct=").append((int) ((double) currbh / totalbh * 100. + 0.5)).append("\n");
    return summary;
  }

  public long getMemoryUsage() {
    long tot = 0;
    synchronized (spans) {
      TLongObjectIterator<QuerySpanThread> itr = spans.iterator();
      while (itr.hasNext()) {
        itr.advance();
        tot += itr.value().getQuerySpan().getMemoryUsage();
      }
    }
    return tot;
  }

  public static QuerySpan getQuerySpan(StringBuilder channel) {
    if (theQuerySpanCollection == null) {
      return null;
    }
    QuerySpanThread thr = theQuerySpanCollection.getQuerySpanThread(channel);
    if (thr == null) {
      return null;
    }
    return thr.getQuerySpan();
  }

  public static QuerySpan getQuerySpan(byte[] msbuf) {
    if (theQuerySpanCollection == null) {
      return null;
    }
    QuerySpanThread thr = theQuerySpanCollection.getQuerySpanThread(msbuf);
    if (thr == null) {
      return null;
    }
    return thr.getQuerySpan();
  }

  /**
   * The the QueryRingThread for a given name. This has a QuerySpan attribute and the machinery for
   * getting data from an internal QueryServer.
   *
   * @param seedname The NNSSSSSCCCLL
   * @return The Thread
   */

  public static QuerySpanThread getQuerySpanThr(StringBuilder seedname) {
    if (theQuerySpanCollection == null) {
      return null;
    }
    return theQuerySpanCollection.getQuerySpanThread(seedname);
  }

  /**
   * The the QueryRingThread for a given name. This has a QuerySpan attribute and the machinery for
   * getting data from an internal QueryServer.
   *
   * @param seedname A miniSeed buffer
   * @return The Thread
   */

  /**
   * The the QueryRingThread for a given name.This has a QuerySpan attribute and the machinery for
   * getting data from an internal QueryServer.
   *
   * @param msbuf A miniSeed buffer
   * @return The Thread
   */
  public static QuerySpanThread getQuerySpanThr(byte[] msbuf) {
    if (theQuerySpanCollection == null) {
      return null;
    }
    return theQuerySpanCollection.getQuerySpanThread(msbuf);
  }

  /**
   * Get a querySpan thread for a channel
   *
   * @param channel The NNSSSSSCCCLL string
   * @return The thread if it exists, else null
   */
  public QuerySpanThread getQuerySpanThread(StringBuilder channel) {
    QuerySpanThread thr;
    synchronized (spans) {
      thr = spans.get(Util.getHashFromSeedname(channel));
    }
    return thr;
  }

  /**
   * Get a querySpan thread for a channel
   *
   * @param msbuf an miniseed buffer
   * @return The thread if it exists, else null
   */
  public QuerySpanThread getQuerySpanThread(byte[] msbuf) {
    QuerySpanThread thr;
    synchronized (spans) {
      thr = spans.get(Util.getHashFromBuf(msbuf, -1));
    }
    return thr;
  }
  private long[] menuKeys;

  /**
   * Get a copy of the channels in this QuerySpanColletion
   *
   * @param chans User list of channels, if null, return is a new ArrayList
   * @return The array list of channels.
   */
  public ArrayList<String> getChannels(ArrayList<String> chans) {
    if (chans == null) {
      chans = new ArrayList<>(100);
    }
    if (menuKeys == null) {
      menuKeys = new long[chans.size() * 14 / 10];
    } else if (menuKeys.length < chans.size() * 12 / 10) {
      menuKeys = new long[chans.size() * 14 / 10];
    }
    synchronized (spans) {
      menuKeys = spans.keys(menuKeys);
      Arrays.sort(menuKeys, 0, spans.size());
      //Iterator<QuerySpanThread> itr = spans.iterator
      for (int i = 0; i < spans.size(); i++) {
        //while(itr.hasNext()) {
        chans.add(spans.get(menuKeys[i]).getQuerySpan().getSeedname());
      }
    }
    return chans;
  }

  @Override
  public StringBuilder getStatusString() {
    if (statussb.length() > 0) {
      statussb.delete(0, statussb.length());
    }
    long tot = getMemoryUsage();
    statussb.append(" #QST=").append(spans.size()).append(" mem=").append(tot / 1000000).
            append("mB QST.").append(QuerySpanThread.getMiniSeedPool());
    int insert = statussb.length();
    int queued = 0;
    boolean detail = System.currentTimeMillis() % 86400000L < 600000L;
    // every hour return the details.
    synchronized (spans) {
      TLongObjectIterator<QuerySpanThread> itr = spans.iterator();
      while (itr.hasNext()) {
        itr.advance();
        QuerySpanThread thr = itr.value();
        queued += thr.getQueueSize();
        //if(detail) statussb.append(thr.getStatusString()); // This was an awful lot of output!
      }
    }
    statussb.insert(insert, " QST.totQueued=" + queued + "\n");

    return statussb;
  }

  @Override
  public StringBuilder getMonitorString() {
    long tot = getMemoryUsage();
    Util.clear(monitorsb).append("NQST=").append(spans.size()).append("\nQSCmem=").append(tot / 1000000).append("\n");
    return monitorsb;
  }

  @Override
  public StringBuilder getConsoleOutput() {
    return consolesb;
  }

  @Override
  public void terminate() {
    prta("QuerySpanCollection start termination of " + spans.size());
    synchronized (spans) {
      TLongObjectIterator<QuerySpanThread> itr = spans.iterator();
      QuerySpanThread thr = null;
      while (itr.hasNext()) {
        try {
          itr.advance();
          thr = itr.value();
          thr.terminate();
        } catch (Exception e) {
          prta("Exception terminating a QuerySpanThread=" + thr + " e=" + e);
          e.printStackTrace(getPrintStream());
        }
      }
    }
    terminate = true;
    prta("QuerySpanCollection terminate() exiting");
  }

  public QuerySpanCollection(String argline, String tg) {
    super(argline, tg);
    String[] args = argline.split("\\s");
    port = 2061;
    host = null;
    duration = 3600.;
    pctPre = 0.75;
    dbgChan = "ZZZZZZZZZ";
    for (int i = 0; i < args.length; i++) {
      if (args[i].contains(">")) {
      } else if (args[i].equals("-h")) {
        host = args[i + 1];
        i++;
      } else if (args[i].equals("-p")) {
        port = Integer.parseInt(args[i + 1]);
        i++;
      } else if (args[i].equals("-d")) {
        duration = Double.parseDouble(args[i + 1]);
        i++;
      } else if (args[i].equals("-pre")) {
        pctPre = Double.parseDouble(args[i + 1]) / 100.;
        i++;
      } else if (args[i].equals("-bands")) {
        if (args[i + 1].length() == 1) {
          prta("**Allow ALL channels to Memory!");
          allowedBands = null;
        } else {
          allowedBands = args[i + 1].getBytes();
        }
        i++;
      } else if (args[i].equals("-load")) {
        nloadThread = Integer.parseInt(args[i + 1]);
        i++;
      } else if (args[i].equals("-dbgchan")) {
        dbgChan = args[i + 1];
        i++;
      } else if (args[i].equals("-empty")) ; else {
        prta(i + " ** Unknown argument to QuerySpanCollection=" + args[i]);
      }
    }
    setTheQuerySpan();
    prta(Util.ascdate() + " Opening QuerySpan collection");
    start();
  }

  @Override
  public void run() {

    running = true;
    while (!terminate) {
      try {
        sleep(1000);
      } catch (InterruptedException e) {
      }
    }
    running = false;
    prta("QuerySpanCollection is exiting...");
  }

  private void setTheQuerySpan() {
    if (theQuerySpanCollection != null) {
      prta("***** multiple creation of querySpan collection attempted.");
      theQuerySpanCollection.terminate();     // free up the resources
    }
    theQuerySpanCollection = this;
  }

  public static void add(byte[] ms) {
    if (theQuerySpanCollection != null) {
      theQuerySpanCollection.addMS(ms); // If a QSC exists ad this block.
    }
  }
  private MiniSeed mstmp;

  private void addMS(byte[] msbuf) {
    if (!running) {
      return;      // we are shutting down!
    }
    String seedname = MiniSeed.crackSeedname(msbuf);
    boolean found = false;
    if (allowedBands != null) {
      for (int i = 0; i < allowedBands.length - 1; i = i + 3) {
        if (allowedBands[i] == msbuf[15] && allowedBands[i + 1] == msbuf[16]) {
          found = true;
          break;
        }
      }
    } else {
      found = true;
    }
    if (!found) {
      return;
    }
    QuerySpanThread thr = getQuerySpanThread(msbuf);
    //synchronized(spans) {thr = spans.get(seedname.trim());}
    if (thr == null) {
      synchronized (spans) { // not this protects usage of mstmp as well which is shared
        try {
          if (mstmp == null) {
            mstmp = new MiniSeed(msbuf, 0, 512);
          } else {
            mstmp.load(msbuf, 0, 512);
          }
          if (mstmp.getRate() > 0 && mstmp.getNsamp() > 0) {
            long start = System.currentTimeMillis() - (long) (duration * 1000. * .9);
            int nago = (int) ((mstmp.getTimeInMillis() - start) * mstmp.getRate() / 1000.);
            start = (long) (mstmp.getTimeInMillis() - nago / mstmp.getRate() * 1000 + 0.5);
            Util.clear(consolesb).append("addMS create span for ").append(mstmp.getSeedNameSB()).
                    append(" rate=").append(mstmp.getRate()).append(" dur=").append(duration).
                    append(" %=").append(pctPre).append(" st=");
            Util.ascdatetime2(mstmp.getTimeInMillis(), consolesb).append(" nago=").append(nago).append(" start=");
            Util.ascdatetime2(start, consolesb);
            prt(consolesb);
            Util.clear(consolesb);
            thr = new QuerySpanThread(mstmp.getSeedNameSB(), mstmp.getRate(), host, port,
                    start, duration, duration * pctPre, this);
            if (mstmp.getSeedNameString().matches(dbgChan)) {
              thr.setDebug(true);
            }
            spans.put(Util.getHashFromBuf(msbuf, -1), thr);
          } else {
            return;      // Cannot start on a non-time series block
          }
        } catch (IllegalSeednameException e) {
          prta("Could not addMS e=" + e);
          return;
        }
      }   // Synchronized spans
    }
    thr.addRealTime(msbuf);
  }

  public static void add(StringBuilder seedname, long st, int[] data, int nsamp, double rate) {
    theQuerySpanCollection.addRaw(seedname, st, data, nsamp, rate);
  }

  public void addRaw(StringBuilder seedname, long st, int[] data, int nsamp, double rate) {
    if (!running) {
      return;      // we are shutting down
    }
    boolean found = false;
    if (allowedBands != null) {
      for (int i = 0; i < allowedBands.length - 1; i = i + 3) {
        if (allowedBands[i] == seedname.charAt(7) && allowedBands[i + 1] == seedname.charAt(8)) {
          found = true;
          break;
        }
      }
    } else {
      found = true;
    }
    if (!found) {
      return;
    }
    QuerySpanThread thr;
    synchronized (spans) {
      thr = spans.get(Util.getHashFromSeedname(seedname));
    }
    if (thr == null) {
      if (rate > 0 && nsamp > 0) {
        //long start = System.currentTimeMillis() - (long)(duration*1000.*.9);
        //int nago = (int) ((st - start)*rate/1000.);
        // start = (long) (st - nago/rate*1000.+0.5);
        int nago = (int) (duration * rate / 2.);  // Put it in the middle of the memory span
        long start = st - (long) (nago / rate * 1000. + 0.5);
        prta(Util.clear(monitorsb).append("addRaw create span for ").append(seedname).
                append(" rate=").append(rate).append(" dur=").append(duration).
                append(" st=").append(Util.ascdatetime2(st, null)).append(" nago=").append(nago).
                append(" start=").append(Util.ascdatetime2(start, null)));
        Util.clear(monitorsb);
        thr = new QuerySpanThread(seedname, rate, host, port,
                start, duration, duration * pctPre, this);
        if (seedname.toString().matches(dbgChan)) {
          thr.setDebug(true);
        }
        synchronized (spans) {
          spans.put(Util.getHashFromSeedname(seedname), thr);
        }
      } else {
        return;      // Cannot start on a non-time series block
      }
    }
    thr.addRealTime(st, data, nsamp, rate);
  }

  public static void main(String[] args) {
    try {
      QuerySpanCollection qsc = new QuerySpanCollection("-h localhost -p 2061 -d 4000 -pre 10", "QSC");
    } catch (RuntimeException e) {
      System.err.println("Got err=" + e);
      e.printStackTrace();

    }
  }
}
