/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.edge;

import gov.usgs.anss.edgethread.EdgeThread;
import gov.usgs.anss.util.Util;
import gnu.trove.map.hash.TLongObjectHashMap;
import gnu.trove.iterator.TLongObjectIterator;

/**
 * This class is used by the RawToMiniSeed class to handle data that comes out of order (like the
 * Aftac or CD 1.1 data). Its purpose is to delay the sending of data to the RTMS static
 * addTimeseries until enough data is present to make for good compression runs and so that the
 * "current" data will tend to process in order if small bobbles in the run are detected.
 *
 * @author davidketchum
 */
public final class OutOfOrderRTMS extends Thread {

  private final TLongObjectHashMap<OORChan> chans = new TLongObjectHashMap<>();
  private boolean terminate;
  private final int processInterval;
  private final String tag;
  private final EdgeThread parent;
  private final StringBuilder tmpsb = new StringBuilder(20);

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
      sb.append(tag).append(" ").append(parent.getTag()).append(" #chan=").append(chans.size());
    }
    return sb;
  }

  public String getStatusString() {
    return "OORRTMS #ch=" + chans.size();
  }

  /**
   * Creates a new instance of OutOfOrderRTMS
   *
   * @param processInt - ms between calls to the process() of each channel looking for ready to go
   * data.
   * @param tg A tag to use on output for this thread.
   * @param par The parent edgethread for logging.
   */
  public OutOfOrderRTMS(int processInt, String tg, EdgeThread par) {
    processInterval = processInt;
    parent = par;
    tag = tg;
    RTMSBuf.init(parent);   // Only the first caller inits the RTMSBufs.
    gov.usgs.anss.util.Util.prta("new Thread " + getName() + " " + getClass().getSimpleName() + " tag=" + tag);
    start();
  }

  /**
   * process a time series via the OORChan. Hydra is assumed true.
   *
   * @param ts Array of ints with time series
   * @param seedname A seedname of the channel
   * @param nsamp Number of samples in x
   * @param year The year of the 1st sample
   * @param doy The day-of-year of first sample
   * @param sec Seconds since midnight of the first sample
   * @param micros Microseconds (fraction of a second) to add to seconds for first sample
   * @param rate Digitizing rate in Hz.
   * @param activity The activity flags per SEED volume (ch 8 pg 93)
   * @param IOClock The I/O and clock flags per SEED volume
   * @param quality The clock quality as defined in SEED volume
   * @param timingQuality The timingQuality for blockette 1001.
   *
   */
  public synchronized void addBuffer(int[] ts, int nsamp, StringBuilder seedname,
          int year, int doy, int sec, int micros, double rate,
          int activity, int IOClock, int quality, int timingQuality) {
    addBuffer(ts, nsamp, seedname, year, doy, sec, micros, rate, activity, IOClock, quality, timingQuality, true);
  }

  /**
   * process a time series via the OORChan.
   *
   * @param ts Array of ints with time series
   * @param seedname A seedname of the channel
   * @param nsamp Number of samples in x
   * @param year The year of the 1st sample
   * @param doy The day-of-year of first sample
   * @param sec Seconds since midnight of the first sample
   * @param micros Microseconds (fraction of a second) to add to seconds for first sample
   * @param rate Digitizing rate in Hz.
   * @param activity The activity flags per SEED volume (ch 8 pg 93)
   * @param IOClock The I/O and clock flags per SEED volume
   * @param quality The clock quality as defined in SEED volume
   * @param timingQuality The timingQuality for blockette 1001.
   * @param hydra If true, use send to hydra mode.
   *
   */
  public void addBuffer(int[] ts, int nsamp, StringBuilder seedname,
          int year, int doy, int sec, int micros, double rate,
          int activity, int IOClock, int quality, int timingQuality, boolean hydra) {
    if (year < 1970 || year > 2200 || doy <= 0 || doy > 366 || rate <= 0. || rate > 1000. || nsamp == 0) {
      parent.prt(Util.clear(tmpsb).append("OORTMS:  ******  bad data yr=").append(year).
              append(" doy=").append(doy).append(" rate=").append(rate).append(" nsamp=").append(nsamp));
      return;
    }
    try {
      MasterBlock.checkSeedName(seedname);
    } catch (IllegalSeednameException e) {
      parent.prt("OORTMS:    *** Illegal seedname to OORTMS: name=" + seedname);
      return;
    }
    synchronized (chans) {
      OORChan chan = chans.get(Util.getHashFromSeedname(seedname));
      if (chan == null) {
        chan = new OORChan(seedname, parent);
        chans.put(Util.getHashFromSeedname(seedname), chan);
      }
      chan.addBuffer(ts, seedname, nsamp, year, doy, sec, micros, rate, activity, IOClock, quality,
              timingQuality, hydra);
    }

  }

  /**
   * process the channels periodically looking for data ready to ship based on age or size. The
   * interval is set on creation
   */
  @Override
  public void run() {
    long loop = 0;
    StringBuilder runsb = new StringBuilder(50);
    parent.prta(Util.clear(runsb).append(tag).append(" OORTMS: interval=").append(processInterval));
    while (!terminate) {
      try {
        sleep(processInterval);
      } catch (InterruptedException e) {
      }
      if (terminate) {
        break;
      }
      synchronized (chans) {
        TLongObjectIterator<OORChan> itr = chans.iterator();
        while (itr.hasNext()) {
          itr.advance();
          OORChan o = itr.value();
          if (o.getNSpans() != 0) {
            o.process();
            //parent.prta(Util.clear(runsb).append(tag).append(" OORRTMS: Process done lp=").append(loop).append(" ").append(o.toStringBuilder(null)));
          }
          if (loop % 10 == 9 && o.getNSpans() > 0) {
            parent.prta(Util.clear(runsb).append(tag).append(" lp=").append(loop).append(" ").append(o.toStringBuilder(null)));
          }
        }
        /*Object [] objs = chans.values();
        for(int i=0; i<objs.length; i++) {
          OORChan o = (OORChan) objs[i];
          if(o.getNSpans() != 0) {
            o.process();
            //parent.prta(tag+" OORRTMS: Process done lp="+loop+" "+o.toString());
          }
        }*/
      }
      loop++;
      //parent.prta("OORRTMS: loop now="+loop);
    }
    parent.prta(tag + " OORTMS: start force out of remaining spans #chans=" + chans.size());
    synchronized (chans) {
      TLongObjectIterator<OORChan> itr = chans.iterator();
      while (itr.hasNext()) {
        itr.advance();
        OORChan o = itr.value();
        if (o.getNSpans() != 0) {
          o.forceOut();
          parent.prta(tag + " OORRTMS: forceout done lp=" + loop + " " + o.toString());
        }
      }
      /*Object [] objs = chans.values();
      for(int i=0; i<objs.length; i++) {
        OORChan o = (OORChan) objs[i];
        if(o.getNSpans() != 0) {
          o.forceOut();
          parent.prta(tag+" OORRTMS: forceout done lp="+loop+" "+o.toString());
        }
      }*/
    }
    parent.prta(tag + " OORTMS: exiting terminate=" + terminate);
    terminate = false;
  }

  /* cause the OORTMS to wind down.  After main run() exits, forceout will be called for all spans.
   * This routine will not return until all processin is done, or 30 seconds have passed and it is not done
   */
  public void terminate() {
    terminate = true;
    interrupt();
    int loop = 100;
    while (terminate) {
      try {
        sleep(1000);
      } catch (InterruptedException e) {
      }
      loop++;
      if (loop > 30) {
        parent.prta(tag + "OORTMS: ***** terminate() run() did not stop in 30 seconds!");
        break;
      }
    }
  }
}
