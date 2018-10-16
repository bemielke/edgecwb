/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.edge;

import java.util.GregorianCalendar;
import java.util.ArrayList;
import java.util.BitSet;
import gov.usgs.anss.edgemom.Hydra;
import gov.usgs.anss.edgethread.EdgeThread;
import gov.usgs.anss.util.Util;
import gov.usgs.anss.util.SeedUtil;
import gov.usgs.alarm.SendEvent;

/**
 * this class encapsulates the data needed by the RawToMiniSeed static process addTimeseries. This
 * class is used by the OOR software to store data packets and manipulate them as a block until they
 * are ready to be sent to the RTMS. This class manages a pool of RTMSBufs so that they do not need
 * to be created and discarded repeatedly
 *
 * @author davidketchum
 */
public final class RTMSBuf {

  // These static fields form the bases for the free list of RTMS blocks.
  private static EdgeThread parent;      // A thread for outputing
  private static final ArrayList<RTMSBuf> bufs = new ArrayList<>(12000);// ArrayList to manage free/used RTMSBufs.
  private static final BitSet freeList = new BitSet(120000);
  private static int nextCheck;          // next index to check for free block
  private static final GregorianCalendar gc = new GregorianCalendar();
  private static int used;
  private static int created;

  // Data for one buffer
  private int[] ts;        // Time series array
  private int nsamp;        // Number of samples occupied in ts
  private final StringBuilder seedname = new StringBuilder(12);  // The seedname of the channel
  private double rate;      // The digitizeing rate in HZ.
  private int year;         // year of start time
  private int doy;          // Day of year of start time
  private int sec;          // seconds since midnight of start time
  private int usec;         // fractions of seconds of start time in Mics
  private int activity;     // activity flags (per Seed)
  private int timingQuality;// timeing quallity Per seed but vendor specific (0-100)
  private int IOClock;      // IO clock flags per seed
  private int quality;      // Quality flags per seed
  private final int index;        // the index of this RTMS buf on free buf list (or -1 if not on the list)
  private long startTime;   // start time in MS of this timeseries
  private final StringBuilder tmpsb = new StringBuilder(100);
  private static boolean dbg = false;

  public static void setDebug(boolean b) {
    dbg = b;
  }

  /**
   * free a given RTMSBuf which should be on the array list
   *
   * @param b The buffer to free
   */
  static synchronized void freeBuf(RTMSBuf b) {
    if (b != bufs.get(b.getIndex())) {
      parent.prt("RTMSBuf: Something is horribly wrong - free has wrong index! b=" + b + " is not " + bufs.get(b.getIndex()).toString());
      SendEvent.debugEvent("RTMSBufErr", "Attempt to free a buf with wrong index " + b + " is not " + bufs.get(b.getIndex()).toString(), "RTMSBuf");
    }
    used--;
    if (!freeList.get(b.getIndex())) {
      parent.prt("RTSMBuf: Attempt to free an already free block at " + b.getIndex());
      SendEvent.debugEvent("RTMSBufDupFr", "Attempt to free an already free block" + b.getIndex(), "RTMSBuf");
    }
    if (dbg) {
      parent.prt("RTMSBuf: Free Buf at index=" + b.getIndex() + " size=" + bufs.size() + " used=" + used);
    }
    nextCheck = b.getIndex();
    freeList.set(b.getIndex(), false);
  }

  public static String getStatus() {
    return "RTMSBuf: created=" + created + " sz=" + bufs.size() + " used=" + used + " free%=" + ((bufs.size() - used) * 100 / Math.max(1, bufs.size()));
  }

  /**
   * get a RTMSBuf from the free list
   *
   * @return A RTMS buf to use
   */
  static synchronized RTMSBuf getFreeBuf() {
    int i = nextCheck + 1;
    if (i >= bufs.size()) {
      i = 0;
    }
    //if(dbg) parent.prta("RTMSBuf: getFree size="+bufs.size()+" next="+nextCheck+" use="+used);
    while (i != nextCheck) {
      if (!freeList.get(i)) {
        freeList.set(i, true);
        nextCheck = i;
        used++;
        if (dbg) {
          parent.prta("RTMSBuf: return freebuf at i=" + i + " size=" + bufs.size() + " next=" + nextCheck);
        }
        return bufs.get(i);
      }
      i++;
      if (i >= bufs.size()) {
        i = 0;
      }
    }
    // None are free add another RTSMBuf
    int ind = bufs.size();
    bufs.add(ind, new RTMSBuf(ind));
    freeList.set(ind, true);
    if (ind % 1000 == 0) {
      parent.prta("RTMSBuf: size is now " + bufs.size());
    }
    used++;
    if (dbg) {
      parent.prta("RTMSBuf: return new buffer size=" + bufs.size() + " used=" + used + " next=" + nextCheck);
    }
    return bufs.get(ind);
  }

  @Override
  public String toString() {
    return toStringBuilder(null).toString();
  }

  public StringBuilder toStringBuilder(StringBuilder tmp) {
    StringBuilder sb = tmp;
    if (sb == null) {
      sb = Util.clear(tmpsb);
    }
    synchronized (sb) {
      sb.append("RTMSBf: ").append(seedname).append(" ").append(Util.asctime(startTime)).append(" ns=").append(nsamp).append(" rt=").append(rate);
    }
    return sb;
  }

  /**
   * initialize the static pool of RTMSbufs if it has not yet been
   *
   * @param par Edgethread to use for logging
   */
  public static void init(EdgeThread par) {
    if (parent == null) {
      parent = par;
      for (int i = 0; i < 100; i++) {
        bufs.add(bufs.size(), new RTMSBuf(bufs.size()));
      }
    }
  }

  //public GregorianCalendar getStart() {return start;}
  public int getIndex() {
    return index;
  }

  public double getRate() {
    return rate;
  }

  public int getNsamp() {
    return nsamp;
  }

  public long getStartTime() {
    return startTime;
  }

  public long getNextTime() {
    return startTime + (long) (nsamp / rate * 1000. + 0.5);
  }

  public StringBuilder getSeedname() {
    return seedname;
  }

  /**
   * send this packet to the RTMS addTimeseries static routine.
   */
  /* public void clear() {     // deallocate space we have 
    ts = null;
    seedname=null;
  }*/
  public void sendToRTMS() {
    RawToMiniSeed.addTimeseries(ts, nsamp, seedname, year, doy, sec,
            usec, rate, activity, IOClock, quality, timingQuality, parent);
    Hydra.send(seedname, year, doy, sec, usec, nsamp, ts, rate);
  }

  public RTMSBuf(int ind) {
    index = ind;
    ts = new int[400];
    created++;
  }

  /**
   * set the values of on RTMSBuf from another
   *
   * @param in The RTMSBuf to use to set this one
   */
  public void setRTMSBuf(RTMSBuf in) {
    if (ts.length < in.getNsamp()) {
      ts = new int[in.getNsamp() * 12 / 10];/*Util.prt("RTMS new size="+ts.length+" "+in);*/
    }
    System.arraycopy(in.ts, 0, ts, 0, in.getNsamp());
    Util.clear(seedname).append(in.getSeedname());
    Util.rightPad(seedname, 12);
    nsamp = in.nsamp;
    timingQuality = in.timingQuality;
    rate = in.getRate();
    startTime = in.startTime;
    year = in.year;
    doy = in.doy;
    sec = in.sec;
    usec = in.usec;
    quality = in.quality;
    IOClock = in.IOClock;
    activity = in.activity;

  }

  /**
   * Set the values of a RTMSBuf
   *
   * @param ts1 Timeseries array
   * @param name Seedname of the data
   * @param ns Number of samples in ts1
   * @param yr The year
   * @param dy The day of year of the start time
   * @param sc The seconds since midnight of the start time
   * @param mics The fraction of seconds in microseconds
   * @param rt The digitizing rate in Hz
   * @param act Activity flags
   * @param IOClk Clock flags
   * @param qual Quality flags
   * @param timeQual The timing quality (0-100)
   */
  public void setRTMSBuf(int[] ts1, StringBuilder name, int ns, int yr, int dy, int sc, int mics, double rt,
          int act, int IOClk, int qual, int timeQual) {

    if (ns < 0) {
      ns = 0;
    }
    if (ts.length < ns) {
      ts = new int[ns];
      /*Util.prt("RTMS: new size2="+ts.length+" "+name+" "+yr+","+dy+" "+sec+"."+mics);*/
    }
    Util.clear(seedname).append(name);
    Util.rightPad(seedname, 12);

    System.arraycopy(ts1, 0, ts, 0, ns);
    nsamp = ns;
    year = yr;
    doy = dy;
    sec = sc;
    usec = mics;
    activity = act;
    IOClock = IOClk;
    quality = qual;
    timingQuality = timeQual;
    rate = rt;
    startTime = getTimeInMillis(yr, dy, sc, mics);
  }

  public static long getTimeInMillis(int yr, int dy, int sc, int mics) {
    int ms = sc * 1000 + mics / 1000;
    int[] ymd = SeedUtil.ymd_from_doy(yr, dy);
    long ans;
    synchronized (gc) {
      gc.set(yr, ymd[1] - 1, ymd[2]);
      ans = gc.getTimeInMillis() / 86400000L * 86400000L + ms;  // The ms are unknown when using set, so fix this
    }
    return ans;
  }
}
