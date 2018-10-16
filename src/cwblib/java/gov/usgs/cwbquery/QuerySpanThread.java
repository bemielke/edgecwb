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
import gov.usgs.anss.seed.MiniSeedPool;
import gov.usgs.alarm.SendEvent;
import gov.usgs.anss.util.Util;
import java.util.ArrayList;
import java.util.concurrent.Semaphore;

/**
 * This class handles the processing for a QuerySpan from the real time system. It will fill in data
 * from the QueryServer at startup to fill the span and then allow the real time feeds to put
 * MiniSeed blocks or bits of raw data into the span.
 *
 * @author U.S. Geological Survey <ketchum at usgs.gov>
 */
public final class QuerySpanThread extends Thread {

  public final int STARTUP = 0, PROCESSING = 1, IDLE = 2;
  private int state;
  private final QuerySpan span;
  private EdgeThread par;
  private final String host;
  private final int port;
  private double rate;
  private final String seedname;
  private final StringBuilder seednamesb = new StringBuilder(12);
  private final StringBuilder tmpsb = new StringBuilder(200);
  private final long start;
  private final double duration, preDur;
  private final int maxInsert;    // the maximum number of samples that can be inserted at one time
  private boolean terminate;
  private boolean dbg;
  private static MiniSeedPool msp;       // this miniseed pool is shared by all QuerySpanThreads.
  private final StringBuilder sbstatus = new StringBuilder(100);
  private final StringBuilder sbruns = new StringBuilder(100);
  private final ArrayList<MiniSeed> queue = new ArrayList<>(10);

  public void setDebug(boolean t) {
    dbg = true;
    span.setDebug(true);
    span.setDebugMem(true);
  }

  ;
  public boolean isReady() {
    return state != STARTUP;
  }

  public int getQueueSize() {
    return queue.size();
  }

  public void terminate() {
    terminate = true;
    interrupt();
    if (par != null) {
      par.prta("QRT: " + seedname + " terminate called");
    }
  }

  public static MiniSeedPool getMiniSeedPool() {
    return msp;
  }

  public StringBuilder getStatusString() {
    if (sbstatus.length() > 0) {
      sbstatus.delete(0, sbstatus.length());
    }
    sbstatus.append(span.toString()).append(" ").append(span.getRateSpanCalculator().toString()).append("\n");
    //boolean ok = span.testRunsList(sbruns);
    //if(!ok) sbstatus.append(sbruns);

    return sbstatus;
  }

  @Override
  public String toString() {
    return seedname + " span=" + span + " qsz=" + queue.size() + " msp=" + msp;
  }

  public StringBuilder toStringBuilder(StringBuilder tmp) {
    StringBuilder sb = tmp;
    if (sb == null) {
      sb = tmpsb;
      Util.clear(tmpsb);
    }
    sb.append(seedname).append(" span=").append(span).append(" qsz=").append(queue.size()).append(" msp=").append(msp);
    return sb;
  }

  public QuerySpanThread(StringBuilder seedname, double rate, String host, int port, long start,
          double duration, double preDur, EdgeThread parent) {
    par = parent;
    this.seedname = seedname.toString();
    Util.clear(seednamesb).append(seedname);
    this.host = host;
    this.port = port;
    this.rate = rate;
    this.start = start;
    this.duration = duration;
    this.preDur = preDur;
    maxInsert = (int) (preDur * rate);
    if (msp == null) {   // Cannot be created at startup as there is no parent yet
      msp = new MiniSeedPool(par);
      msp.setDebug(false);
    }
    state = STARTUP;
    /*Util.clear(tmpsb).append("create span for ").append(host).append("/").append(port).append(" ").
            append(seedname).append(" ");
    Util.ascdatetime2(start,tmpsb).append(" dur=").append(duration).append(" pre=").
            append(preDur).append(" rate=").append(rate);
    par.prta(tmpsb);*/
    span = new QuerySpan(host, port, this.seedname, start, duration, preDur, rate, par); // This makes an empty one
    //par.prta(Util.clear(tmpsb).append("Span created span=").append(span.toStringFull()));
    if (Util.stringBuilderEqual(seedname, QuerySpan.getDebugChan())) {
      dbg = true;
    }
    start();
  }

  public QuerySpan getQuerySpan() {
    return span;
  }

  public void addRealTime(byte[] ms) {
    if (ms == null) {
      par.prta("addRealTime ms is null)" + toStringBuilder(null));
    }
    if (msp == null) {
      par.prta("addRealTime msp is null" + toStringBuilder(null));
    }
    if (queue == null) {
      par.prta("addRealTime queue is null" + toStringBuilder(null));
    }
    synchronized (queue) {
      queue.add(msp.get(ms, 0, 512));// put it in a msp buffer
    }
    if (state == IDLE) {
      interrupt();
    }
  }

  public void addRealTime(long start, int[] data, int nsamp, double rt) {
    if (rate == 0.) {
      rate = rt;
    }
    if (rt != rate) {
      par.prta(Util.clear(tmpsb).append(seedname).append(" rates disagree have ").
              append(rate).append(" packet rate=").append(rt));
    }
    if (span == null) {
      par.prta(seedname + " **** addRealTime() has null span!");
      return;
    }
    if (nsamp > preDur * rate) {
      par.prta("***** attempt to insert to real time is bigger than memory buffer- ignore +" + seedname + " "
              + Util.ascdatetime2(start) + " ns=" + nsamp + " max=" + preDur * rate);
      SendEvent.edgeSMEEvent("QSTInsTooBig", "Insert too big " + seedname + " "
              + Util.ascdatetime2(start) + " ns=" + nsamp + " max=" + preDur * rate, this);
      return;
    }
    span.addRealTime(start, data, nsamp, preDur);  // Note this delays until data is in buffer
  }
  //static int nload1,nwait1,nload2,nwait2,nload3,nwait3;
  //private static final Integer mutex=Util.nextMutex();
  private static Semaphore semaphore;
  static int nload, nloading;

  @Override
  public void run() {
    state = STARTUP;
    if (QuerySpanCollection.nloadThread > 0 && !Character.isLowerCase(seedname.charAt(7))) { // useless to ask for little b or h
      if (semaphore == null) {
        semaphore = new Semaphore(QuerySpanCollection.nloadThread);
      }
      try {
        // Now we need to load in some data, do it in stages running backward for better startup performance
        // How much ttime to do at a time, limited to 36000/rate (900 for 40 Hz, 360 for 100 Hz, all of it for 1 Hz)
        long offset = (long) (Math.min(36000 / rate, duration) * 1000 * .9);
        long loadTime = System.currentTimeMillis();
        long st = loadTime - offset;
        nloading++;
        if (dbg) {
          par.prta(Util.clear(tmpsb).append("QRT: Start load ").append(seedname).append(" Wait for unit"));
        }
        StringBuilder sb = new StringBuilder(100);      // status string for run()
        while (st > System.currentTimeMillis() - duration * 1000 && !terminate) {
          // This attempts to limit the loads by how recent they are, only 20 or so queries can be running at a time
          while (!semaphore.tryAcquire()) {
            emptyQueue();
            try {
              sleep(50);
            } catch (InterruptedException expected) {
            }
          }
          nload++;
          if (dbg) {
            par.prta(Util.clear(tmpsb).append("QRT: Start load ").append(seedname).
                    append(" for data from ").append(Util.ascdatetime2(st, null)).append(" dur=").append(offset / 1000.).
                    append(" nload=").append(nload).append(" nloading=").append(nloading).append(" #ch=").
                    append(QuerySpanCollection.getNchan()));
          }

          span.realTimeLoad(st, offset / 1000. + 1);     // get the blocks with query, server/not determined by QuerySpan setup
          st -= offset;
          // process any new realtime data
          if (terminate) {
            break;
          }
          nload--;
          semaphore.release();
          emptyQueue();
        }
        span.getNMissingData();
        if (dbg) {
          par.prta(span.toStringFull());
        }
        //boolean ok=span.testRunsList(sb);
        //if(!ok) par.prta(Util.clear(tmpsb).append("QRT: ").append(seedname).append(" Test (null=OK)=").append(sb));
        //try{sleep(1000000);} catch(InterruptedException expected) {} //DEBUG
        nloading--;
        par.prta(Util.clear(tmpsb).append("QRT: ").append(seedname).append(" Disk load is complete elapsed=").
                append(System.currentTimeMillis() - loadTime).append(" ms ").append(span.getRateSpanCalculator())
                .append(" nload=").append(nload).append("/").append(semaphore.availablePermits()).
                append(" nloading=").append(nloading).append(" #ch=").
                append(QuerySpanCollection.getNchan()));
        state = IDLE;
      } catch (RuntimeException e) {
        par.prt(Util.clear(tmpsb).append("QRT: ").append(seedname).append(" Runtime during setup exit! e=").append(e));
        e.printStackTrace(par.getPrintStream());
      }

    }
    while (!terminate) {
      try {
        state = PROCESSING;
        emptyQueue();
        state = IDLE;
        try {
          sleep(1000);
        } catch (InterruptedException expected) {
        }

      } catch (RuntimeException e) {
        par.prta(Util.clear(tmpsb).append("QRT: ").append(seedname).append(" Runtime e=").append(e));
        e.printStackTrace(par.getPrintStream());
      }
    }

    par.prt(Util.clear(tmpsb).append("QRT: ").append(seedname).append(" is terminated ").
            append(terminate));
    par = null;
    span.close();
  }

  /**
   * If there is anything in the Queue, send it until the queue is empty
   */
  private void emptyQueue() {
    //if(queue.size() >=0) return;    // DEBUG
    while (queue.size() > 0) {
      try {
        span.addRealTime(queue.get(0), preDur);
      } catch (RuntimeException e) {
        par.prt(Util.clear(tmpsb).append("QRT: emptyQueue() runtime e=").append(e));
        e.printStackTrace(par.getPrintStream());
      }
      synchronized (queue) {
        msp.free(queue.get(0));
        queue.remove(0);
      }
    }
  }
}
