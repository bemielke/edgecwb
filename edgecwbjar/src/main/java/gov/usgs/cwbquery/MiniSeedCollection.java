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
import gnu.trove.map.hash.TLongObjectHashMap;
import gnu.trove.iterator.TLongObjectIterator;
import java.util.ArrayList;

/**
 * This class controls the memory based MiniSeed and allows the real time
 * processing to add blocks or simple time series segments to the memory based
 * spans.
 *
 * <PRE>
 * flag    arg     Desc
 * -h     ip.adr  The IP address of the CWB to query for data on startup (def=localhost)
 * -p     port    The port of the CWB (def=2061_
 * -d     secs    Duration of band collections in seconds (def=3600).
 * -bands String  This string is the list of bands to allow into the server default= "BH-LH-SH-EH-HH-"
 * -pre   pct     The amount of memory to alway leave in pre current time (default=75%)
 * -load  N       Set the number of threads to use to populate memory cache from disk (def=2), if 0, no population is done.
 * -dbgchan match The match string will set all debugging on for any QuerySpans which match it
 * </PRE>
 *
 * @author U.S. Geological Survey <ketchum at usgs.gov>
 */
public final class MiniSeedCollection extends EdgeThread {

  private final TLongObjectHashMap<MiniSeedThread> spans = new TLongObjectHashMap<>(10000);

  private MiniSeed mstmp;
  private String dbgChan;
  private int npackets;
  private final StringBuilder runsb = new StringBuilder(100);
  protected static int nloadThread = 2;// So far this is a bad idea!
  private static MiniSeedCollection theMiniSeedCollection;
  private String host;
  private int port;
  private double duration;
  private byte[] allowedBands = "BH-LH-SH-EH-HH-".getBytes();
  private double pctPre;
  
  
  public static MiniSeedCollection getMiniSeedCollection() {
    return theMiniSeedCollection;
  }

  public static boolean isUp() {
    return theMiniSeedCollection != null;
  }
  public static int getNchan() {
    return (theMiniSeedCollection == null ? -1 : theMiniSeedCollection.spans.size());
  }

  public long getMemoryUsage() {
    long tot = 0;
    synchronized (spans) {
      //Iterator<MiniSeedThread> itr = spans.values().iterator();
      TLongObjectIterator<MiniSeedThread> itr = spans.iterator();
      while (itr.hasNext()) {
        itr.advance();
        tot += itr.value().getMiniSeedArray().getMemoryUsage();
      }
    }
    return tot;
  }

  public static MiniSeedArray getMiniSeedArray(byte[] msbuf) {
    if (theMiniSeedCollection == null) {
      return null;
    }
    MiniSeedThread thr = theMiniSeedCollection.getMiniSeedThread(msbuf);
    if (thr == null) {
      return null;
    }
    return thr.getMiniSeedArray();
  }

  /**
   * The the QueryRingThread for a given name. This has a QuerySpan attribute
   * and the machinery for getting data from an internal QueryServer.
   *
   * @param msbuf A raw MiniSEED buffer
   * @return The Thread
   */
  public static MiniSeedThread getMiniSeedThr(byte[] msbuf) {
    if (theMiniSeedCollection == null) {
      return null;
    }
    return theMiniSeedCollection.getMiniSeedThread(msbuf);
  }

  /* public static MiniSeedThread getMiniSeedThr(String seedname) {
    if(theMiniSeedCollection == null) return null;
    return theMiniSeedCollection.getMiniSeedThread(seedname);
  }*/
  /**
   * Get a querySpan thread for a channel
   *
   * @param msbuf A raw MiniSEED buffer
   * @return The thread if it exists, else null
   */
  public MiniSeedThread getMiniSeedThread(byte[] msbuf) {
    MiniSeedThread thr;
    synchronized (spans) {
      thr = spans.get(Util.getHashFromBuf(msbuf, -1));
    }
    return thr;
  }

  /**
   * The the QueryRingThread for a given name. This has a QuerySpan attribute
   * and the machinery for getting data from an internal QueryServer.
   *
   * @param key The long key made by MiniSeed.getLong*() methods
   * @return The Thread
   */
  public static MiniSeedThread getMiniSeedThr(long key) {
    if (theMiniSeedCollection == null) {
      return null;
    }
    return theMiniSeedCollection.getMiniSeedThread(key);
  }

  /**
   * Get a querySpan thread for a channel
   *
   * @param key The long key made by MiniSeed.getLong*() methods
   * @return The thread if it exists, else null
   */
  public MiniSeedThread getMiniSeedThread(long key) {
    MiniSeedThread thr;
    synchronized (spans) {
      thr = spans.get(key);
    }
    return thr;
  }
  /**Return a ArrayList of Strings with a list of all of the channels
   * 
   * @param chans Users ArrayList, if null, one is created.
   * @return The users own array list or a new one created here.
   */
  public ArrayList<String> getChannels(ArrayList<String> chans) {
    if (chans == null) {
      chans = new ArrayList<>(100);
    }
    synchronized (spans) {
      TLongObjectIterator<MiniSeedThread> itr = spans.iterator();
      while (itr.hasNext()) {
        itr.advance();
        chans.add(itr.value().getMiniSeedArray().getSeedname());
      }
    }
    return chans;
  }

  @Override
  public StringBuilder getStatusString() {
    synchronized (statussb) {
      Util.clear(statussb);
      long tot = getMemoryUsage();
      statussb.append(" #MST=").append(spans.size()).append(" #pck=").append(npackets).
              append(" mem=").append(tot / 1000000).
              append("mB MST.").append(MiniSeedThread.getMiniSeedPool()).
              append(" MSA.").append(MiniSeedArray.getMiniSeedPool());
      int insert = statussb.length();
      int queued = 0;
      boolean detail = System.currentTimeMillis() % 86400000L < 600000L;
      // every hour return the details.
      synchronized (spans) {
        TLongObjectIterator<MiniSeedThread> itr = spans.iterator();
        while (itr.hasNext()) {
          itr.advance();
          MiniSeedThread thr = itr.value();
          queued += thr.getQueueSize();
          if (detail) {
            statussb.append(thr.getStatusString());
          }
        }
      }
      statussb.insert(insert, " QST.totQueued=" + queued + "\n");
      return statussb;
    }
  }

  @Override
  public StringBuilder getMonitorString() {
    long tot = getMemoryUsage();
    Util.clear(monitorsb).append("MSTSize=").append(spans.size()).
            append("\nMSCpck=").append(npackets).append("\nMSCmem=").append(tot / 1000000).
            append("\n");

    return monitorsb;
  }

  @Override
  public StringBuilder getConsoleOutput() {
    return consolesb;
  }

  @Override
  public void terminate() {
    prta("MiniSeedCollection start termination of " + spans.size());
    synchronized (spans) {
      TLongObjectIterator<MiniSeedThread> itr = spans.iterator();
      MiniSeedThread thr = null;
      while (itr.hasNext()) {
        try {
          itr.advance();
          thr = itr.value();
          thr.terminate();
        } catch (Exception e) {
          prta("Exception terminating a MiniSeedThread=" + thr + " e=" + e);
          e.printStackTrace(getPrintStream());
        }
      }
    }
    terminate = true;
    prta("MiniSeedCollection terminate() exiting");
  }



  public MiniSeedCollection(String argline, String tg) {
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
        allowedBands = args[i + 1].getBytes();
        i++;
      } else if (args[i].equals("-dbgchan")) {
        dbgChan = args[i + 1];
        i++;
      } else if (args[i].equals("-load")) {
        nloadThread = Integer.parseInt(args[i + 1]);
        i++;
      } else if (args[i].equals("-empty")) ; else {
        prta(i + " ** Unknown argument to MiniSeedCollection=" + args[i]);
      }
    }
    setTheQuerySpan();
    prta("New MiniSeedCollection host=" + host + "/" + port + " dur=" + duration + " pre=" + pctPre + " allowed=" + new String(allowedBands) + " dbgch=" + dbgChan + " nload=" + nloadThread);
    start();
  }

  @Override
  public void run() {
    running = true;
    int loop = 0;
    StringBuilder sb = new StringBuilder(100);
    while (!terminate) {
      try {
        sleep(1000);
      } catch (InterruptedException expected) {
      }
      loop++;
      if (loop % 900 == 899) {
        long totmaxsize = 0;
        long totsize = 0;
        long totmem = 0;
        int i = 0;
        synchronized (spans) {
          //Iterator<MiniSeedThread> itr = spans.values().iterator();
          TLongObjectIterator<MiniSeedThread> itr = spans.iterator();
          while (itr.hasNext()) {
            //MiniSeedThread thr = itr.next();
            itr.advance();
            MiniSeedThread thr = itr.value();
            MiniSeedArray msa = thr.getMiniSeedArray();
            totmaxsize += msa.getMaxSize();
            totsize += msa.getSize();
            totmem += msa.getMemoryUsage();
            if (loop % 10800 == 3599) {
              Util.clear(sb);
              sb.append(i).append(" ");
              thr.toStringBuilder(sb);
              prt(sb);
            }
            i++;
          }
          Util.clear(sb);
          MiniSeed.getPerfString(sb);
          prta(Util.clear(runsb).append("MSC: summary #chan=").append(spans.size()).
                  append(" totsize=").append(totsize).append(" totmaxsize=").append(totmaxsize).
                  append(" memory=").append(totmem / 1024 / 1024).
                  append(" mB msamsp=").append(MiniSeedArray.getMiniSeedPool().toStringBuilder(null)).
                  append(" mstmsp=").append(
                  (MiniSeedThread.getMiniSeedPool() == null ? "Null"
                  : MiniSeedThread.getMiniSeedPool().toStringBuilder(null))).append(" ").append(sb));
        }
      }
    }
    running = false;
    prta("MiniSeedCollection is exiting...");
  }

  private void setTheQuerySpan() {
    if (theMiniSeedCollection != null) {
      prta("***** multiple creation of querySpan collection attempted.");
      theMiniSeedCollection.terminate();     // free up the resources
    }
    theMiniSeedCollection = this;
  }

  public static void add(byte[] msbuf) {
    if (theMiniSeedCollection != null) {
      theMiniSeedCollection.addMS(msbuf);
    }
  } // If a QSC exists ad this block.

  private void addMS(byte[] msbuf) {
    //if(npackets % 10000 == 0) new RuntimeException(" addMS "+npackets+" done.").printStackTrace(getPrintStream());
    //String seedname = MiniSeed.crackSeedname(msbuf);
    boolean found = false;
    for (int i = 0; i < allowedBands.length - 1; i = i + 3) {
      if (allowedBands[i] == msbuf[15] && allowedBands[i + 1] == msbuf[16]) {
        found = true;
        break;
      }
    }
    if (!found) {
      return;
    }
    //if(!allowedBands.contains(seedname.substring(7,9))) return;   // Do not put non-seismic channels int
    npackets++;
    MiniSeedThread thr = getMiniSeedThread(msbuf);
    if (thr == null) {
      synchronized (spans) {   // Note this synchronizes mstmp as well which might be shared between all callers
        try {
          if (mstmp == null) {
            mstmp = new MiniSeed(msbuf);
          } else {
            mstmp.load(msbuf);
          }
          if (mstmp.getRate() > 0 && mstmp.getNsamp() > 0) {
            long start = System.currentTimeMillis() - (long) (duration * 1000. * .9);
            int nago = (int) ((mstmp.getTimeInMillis() - start) * mstmp.getRate() / 1000.);
            start = (long) (mstmp.getTimeInMillis() - nago / mstmp.getRate() * 1000 + 0.5);
            Util.clear(consolesb).append("addMS create span for ").append(mstmp.getSeedNameString()).
                    append(" rate=").append(mstmp.getRate()).append(" dur=").append(duration).
                    append(" buf.len").append(msbuf.length).append(" st=").
                    append(Util.ascdatetime2(mstmp.getTimeInMillis(), null)).append(" nago=").append(nago).
                    append(" start=").append(Util.ascdatetime2(start, null)).append(" #pkt=").append(npackets);
            prta(consolesb);
            Util.clear(consolesb);
            thr = new MiniSeedThread(mstmp, host, port,
                    start, duration, duration * pctPre, this);
            spans.put(Util.getHashFromBuf(msbuf, -1), thr);
            if (mstmp.getSeedNameString().trim().matches(dbgChan)) {
              thr.setDebug(true);
              prta("Set debug on for thr=" + thr);
            }
          } else {
            return;      // Cannot start on a non-time series block
          }
        } catch (InstantiationException e) {
          prta("addMS could not create a MiniSeed thread for ms=" + mstmp);
          return;
        } catch (IllegalSeednameException e) {
          prta("addMS could not create a miniseed for buffer - e=" + e);
          return;
        }
      }   // synchronize spans
      //spans.put(mstmp.getSeedNameString().trim(), thr);
    }
    thr.addRealTime(msbuf);
  }

  /*public static void add(String seedname, long st, int [] data, int nsamp, double rate) {
    return;
  }
  public void addRaw(String seedname, long st, int [] data, int nsamp, double rate) {
   return;
  }*/

  public static void main(String[] args) {
    try {
      MiniSeedCollection qsc = new MiniSeedCollection("-h " + Util.getLocalHostIP() + 
              " -p 2061 -d 4000 -pre 10", "MSC");
    } catch (RuntimeException e) {
      System.err.println("Got err=" + e);
      e.printStackTrace();

    }
  }
}
