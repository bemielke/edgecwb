/*
 *  This software is in the public domain because it contains materials 
 *  that originally came from the United States Geological Survey, 
 *  an agency of the United States Department of Interior. For more 
 *  information, see the official USGS copyright policy at 
 *  http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.cwbquery;

import gov.usgs.anss.edge.IllegalSeednameException;
import gov.usgs.anss.edgethread.EdgeThread;
import gov.usgs.anss.seed.MiniSeed;
import gov.usgs.anss.seed.MiniSeedPool;
import gov.usgs.anss.util.Util;
import java.io.IOException;
import static java.lang.Thread.sleep;
import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.Semaphore;

/**
 * This class handles the processing for a MiniSeedArray from the real time
 * system. Normally the MiniSeedCollections creates one of these to wrap the
 * MiniSeedArray for the channel and this thread periodically trims the
 * MiniSeedArray as well as queuing data to be submitted to the MiniSeedArray
 * and dequeuing it.
 * <p>
 * It will fill in data from the QueryServer at startup if configured to fill
 * the msarray and then allow the real time feeds to put MiniSeed blocks into
 * msarray.
 *
 * @author U.S. Geological Survey <ketchum at usgs.gov>
 */
public final class MiniSeedThread extends Thread {

  public final int STARTUP = 0, PROCESSING = 1, IDLE = 2;
  private int state;
  private final MiniSeedArray msarray;
  private EdgeThread par;
  private final String host;
  private final int port;
  private double rate;
  private final String seedname;
  private final long start;
  private final double duration, preDur;
  private boolean terminate;
  private boolean dbg;
  private static MiniSeedPool msp;       // this miniseed pool is shared by all MiniSeedThreads.
  private final StringBuilder sbstatus = new StringBuilder(100);
  private final StringBuilder sbruns = new StringBuilder(100);
  private final StringBuilder sb = new StringBuilder(100);
  private final StringBuilder tmpsb = new StringBuilder(100);

  private final ArrayList<MiniSeed> queue = new ArrayList<>(10);
  private int maxQueueSize;
  private EdgeQueryInternal queryint;

  public void setDebug(boolean t) {
    dbg = true;
    msarray.setDebug(true);
  }

  ;
  public String getSeedname() {
    return seedname;
  }

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
      par.prta("MSTR: " + seedname + " terminate called");
    }
  }

  public static MiniSeedPool getMiniSeedPool() {
    return msp;
  }

  public StringBuilder getStatusString() {
    if (sbstatus.length() > 0) {
      sbstatus.delete(0, sbstatus.length());
    }
    sbstatus.append(msarray.toString()).append(" ").append("\n");
    boolean ok = msarray.checkOrder(sbruns);
    if (!ok) {
      sbstatus.append(sbruns);
    }

    return sbstatus;
  }

  @Override
  public String toString() {
    return seedname + " msarray=" + msarray + " qsz=" + queue.size() + " maxq=" + maxQueueSize;
  }

  public void toStringBuilder(StringBuilder sb) {
    synchronized (sb) {
      sb.append(seedname).append(" msarray=");
      msarray.toStringBuilder(sb);
      sb.append(" qsz=").append(queue.size()).append(" maxq=").append(maxQueueSize);
    }
  }

  public MiniSeedThread(MiniSeed ms, String host, int port, long start,
          double duration, double preDur, EdgeThread parent) throws InstantiationException {
    par = parent;
    this.seedname = ms.getSeedNameString();
    this.host = host;
    this.port = port;
    this.rate = ms.getRate();
    this.start = start;
    this.duration = duration;
    this.preDur = preDur;
    if (msp == null) {   // Cannot be created at startup as there is no parent yet
      msp = new MiniSeedPool(par);
      msp.setDebug(false);
    }
    state = STARTUP;
    par.prta(Util.clear(tmpsb).append("MST: create MiniSeedArray for ").append(host).append("/").
            append(port).append(" ").append(seedname).append(" ").append(Util.ascdate(start)).
            append(" ").append(Util.asctime(start)).append(" dur=").append(duration).
            append(" pre=").append(preDur).append(" rate=").append(rate));
    msarray = new MiniSeedArray(ms, duration, parent); // This makes an empty one
    par.prta(Util.clear(tmpsb).append("MST: created msarray=").append(msarray.toStringFull()));
    if (seedname.equals(MiniSeedArray.getDebugChan())) {
      dbg = true;
    }
    try {
      queryint = new EdgeQueryInternal("MST-" + seedname + "EQI", EdgeQueryServer.isReadOnly(), true, parent);
    } catch (InstantiationException e) {
      par.prta(Util.clear(tmpsb).append("MST").append(seedname).append(" ***** could not make and EdgeQueryInteral!"));
    }
    start();
  }

  public MiniSeedArray getMiniSeedArray() {
    return msarray;
  }

  /**
   * Add a miniSEED packet to the queue to be sent to the MiniSeedArray.
   *
   * @param msbuf A byte array with a miniSEED payload
   */
  public void addRealTime(byte[] msbuf) {
    synchronized (queue) {
      MiniSeed ms = msp.get(msbuf, 0, 512);
      if (ms == null) {
        par.prta(Util.clear(tmpsb).append("MST: addRealTime is null ").append(msbuf.length));
      } else {
        queue.add(ms);// put it in a msp buffer
      }
      if (queue.size() > maxQueueSize) {
        maxQueueSize = queue.size();
      }
    }
    if (state == IDLE) {
      interrupt();
    }
  }

  private static Semaphore semaphore;
  static int nload, nloading;

  @Override
  public void run() {
    state = STARTUP;
    // The following code tries to load memory from the disk.  THis is counter productive as it makes the
    // Miniseed array so slow, it cannot keep up with real time.  Do abandon this code
    if (MiniSeedCollection.nloadThread > 0) {
      if (semaphore == null) {
        semaphore = new Semaphore(MiniSeedCollection.nloadThread);
      }
      try {
        // Now we need to load in some data, do it in stages running backward for better startup performance
        // How much ttime to do at a time, limited to 36000/rate (900 for 40 Hz, 360 for 100 Hz, all of it for 1 Hz)
        long offset = (long) (Math.min(36000 / rate, duration) * 1000 * .9);
        long loadTime = System.currentTimeMillis();
        long st = loadTime - offset;
        nloading++;
        if (dbg) {
          par.prta(Util.clear(tmpsb).append("MSTR: Start load ").append(seedname).append(" Wait for unit"));
        }
        StringBuilder sbrun = new StringBuilder(100);      // status string for run()
        while (st > System.currentTimeMillis() - duration * 1000 && !terminate) {
          // This attempts to limit the loads by how recent they are, only 20 or so queries can be running at a time
          // with lower loops (later data) filling in first
          while (!semaphore.tryAcquire()) {
            emptyQueue();
            try {
              sleep(10);
            } catch (InterruptedException expected) {
            }
          }
          nload++;
          if (dbg) {
            par.prta(Util.clear(tmpsb).append("MSTR: Start load ").append(seedname).append(" for data from ").
                    append(Util.ascdate(st)).append(" ").append(Util.asctime(st)).append(" dur=").
                    append(offset / 1000. + 1).append(" nload=").append(nload));
          }
          realTimeLoad(st, offset / 1000. + 1);     // get the blocks with query, server/not determined by MiniSeedArray setup
          st -= offset;
          // process any new realtime data
          if (terminate) {
            break;
          }
          emptyQueue();
          nload--;
          semaphore.release();
        }
        if (dbg) {
          par.prta(Util.clear(tmpsb).append("MSTR: * Load from disk completed ").append(msarray.toStringDetail()));
        }
        boolean ok = msarray.checkOrder(sbrun);
        if (!ok) {
          par.prta(Util.clear(tmpsb).append("MSTR: ").append(seedname).append(" Test (null=OK)=").append(sbrun));
        }
        //try{sleep(1000000);} catch(InterruptedException expected) {} //DEBUG
        nloading--;
        par.prta(Util.clear(tmpsb).append("MSTR: ").append(seedname).append(" Disk load complete elapsed=").
                append(System.currentTimeMillis() - loadTime).append(" ms nload=").append(nload).
                append("/").append(semaphore.availablePermits()).append(" loading=").append(nloading).
                append(" #ch=").append(MiniSeedCollection.getNchan()));
        state = IDLE;
      } catch (RuntimeException e) {
        par.prt(Util.clear(tmpsb).append("MSTR: ").append(seedname).append(" Runtime during setup exit! e=").append(e));
        e.printStackTrace(par.getPrintStream());
      }
    }     // Loader is not turned on
    long lastTrim = System.currentTimeMillis();
    int loop = 0;
    while (!terminate) {
      try {
        state = PROCESSING;
        emptyQueue();
        state = IDLE;
        try {
          sleep(1000);
        } catch (InterruptedException expected) {
        }
        if (loop % 60 == 59) {
          int n = msarray.trim();
          if (n > 0 && loop % 600 == 59 && dbg) {
            Util.clear(sb);
            msarray.toStringBuilder(sb);
            sb.insert(0, " MSTR: trim=" + n + " ");
            par.prta(sb);
          }
        }
        if (loop % 600 == 59 && !(seedname.charAt(7) == 'H' && seedname.charAt(8) == 'N')) {
          if (!msarray.checkOrder(sb)) {
            sb.insert(0, "MSTR: " + seedname + " *** Check not OK=");
            par.prta(sb);
          } else if (dbg) {
            par.prta(Util.clear(sb).append("MSTR: seedname ").append(seedname).append(" Check was o.k."));
          }
        }
      } catch (RuntimeException e) {
        par.prt(Util.clear(tmpsb).append("MSTR: ").append(seedname).append(" Runtime e=").append(e));
        e.printStackTrace(par.getPrintStream());
      }
      loop++;
    }

    par.prt(Util.clear(tmpsb).append("MSTR: ").append(seedname).append(" is terminated ").append(terminate));
    par = null;
    msarray.close();
  }

  /**
   * If there is anything in the Queue, send it to the MiniSeedArray until the
   * queue is empty
   */
  private void emptyQueue() {
    //if(queue.size() >=0) return;    // DEBUG
    while (queue.size() > 0) {
      try {
        if (queue.get(0) != null) {
          msarray.add(queue.get(0));
        }
      } catch (RuntimeException e) {
        par.prt(Util.clear(tmpsb).append("MSTR: emptyQueue() runtime e=").append(e));
        e.printStackTrace(par.getPrintStream());
      }
      synchronized (queue) {
        msp.free(queue.get(0));
        queue.remove(0);
      }
    }
  }

  /**
   *
   * @param st Starting time for query in Millis since 1970
   * @param duration In seconds
   * @param msb The array list to return the blocks
   */
  public synchronized void doQuery(long st, double duration, ArrayList<MiniSeed> msb) {
    // If this is an internal CWB connection do the query
    try {
      if (dbg) {
        queryint.setDebug(dbg);
      }
      queryint.query(seedname, st, duration, msb);
      if (dbg) {
        par.prta(Util.clear(tmpsb).append("MSTR: doQuery Int ").append(seedname).append(" ").
                append(Util.ascdatetime2(st)).append(" ").append(duration).append(" ret=").
                append(msb.size()).append(" query=").append(queryint));
      }
    } catch (IOException | IllegalSeednameException e) {
      e.printStackTrace();
    }
  }

  /**
   * Load realtime data from a CWB (internal or external depending on how
   * QuerySpan was setup)
   *
   * @param st Starting time in Millis
   * @param duration In seconds.
   */
  public synchronized void realTimeLoad(long st, double duration) {
    ArrayList<MiniSeed> mslist = new ArrayList<>(100);
    doQuery(st, duration, mslist);
    if (mslist.size() > 0) {
      Collections.sort(mslist);
      boolean ok = false;
      msarray.setDebugSuppress(true);
      // This is a load from the database, force update of rateSpanCalculator so a good rate is available for first loaded block
      for (MiniSeed mslist1 : mslist) {
        if (mslist1.getRate() > 0 && mslist1.getNsamp() > 0) {
          msarray.add(mslist1);
        }
      }
      queryint.freeBlocks(mslist);
      msarray.setDebugSuppress(false);
    }
  }

}
