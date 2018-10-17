/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.edgethread;

import gov.usgs.alarm.SendEvent;
import gov.usgs.anss.util.SimpleSMTPThread;
import gov.usgs.anss.util.Util;

/**
 * This thread monitors memory and periodically prints a status line. It can be created as an
 * object, or the static function checkMemory() can be called directly from a routine. The static
 * use the user calls it and gets advice on whether the memory situation is dire. If an object of
 * this class is created, creates the thread do run automatic periodic checks using the static
 * methods. The constructor for the thread can indicate that the process is to be shutdown under
 * this threads control.
 *
 * @author davidketchum
 */
public final class MemoryChecker extends Thread {

  private static long freeMemory, maxMemory, totalMemory, lastTotalMemory, lastPage;
  private final boolean allowShutdown;
  private static long lastGCRun;
  private static int loopsBad;
  private static int memWatchDog = 0;
  private final long secs;
  private static EdgeThread par;
  private boolean terminate;
  private static final StringBuilder tmpsb = new StringBuilder(100);
  private static final StringBuilder monitorsb = new StringBuilder(100);

  public static StringBuilder getLastSB() { return tmpsb;}
  public static StringBuilder getMonitorString() {
    return monitorsb;
  }

  public void terminate() {
    terminate = true;
  }

  public static void prt(String s) {
    if (par == null) {
      Util.prt(s);
    } else {
      par.prt(s);
    }
  }

  public static void prta(String s) {
    if (par == null) {
      Util.prta(s);
    } else {
      par.prta(s);
    }
  }

  public static void prt(StringBuilder s) {
    if (par == null) {
      Util.prt(s);
    } else {
      par.prt(s);
    }
  }

  public static void prta(StringBuilder s) {
    if (par == null) {
      Util.prta(s);
    } else {
      par.prta(s);
    }
  }

  /**
   * Set a EdgeThread for logging when not using as an object, but using static checkMemory
   *
   * @param parent The edgethread to us for logging.
   */
  public static void setPrint(EdgeThread parent) {
    par = parent;
  }

  public MemoryChecker(int secs, boolean allowShutdown, EdgeThread par) {
    this.secs = secs;
    this.setDaemon(true);
    this.allowShutdown = allowShutdown;
    MemoryChecker.par = par;
    this.setDaemon(true);
    start();
  }

  public MemoryChecker(int secs, EdgeThread par) {
    this.secs = secs;
    this.setDaemon(true);
    this.allowShutdown = false;
    MemoryChecker.par = par;
    this.setDaemon(true);
    start();
  }

  @Override
  public void run() {
    StringBuilder runsb = new StringBuilder(100);
    prt(Util.clear(runsb).append("MemoryChecker for par=").append(par != null ? par.getClass().getName() : ""));
    int elapse = 0;
    MemoryChecker.checkMemory(true);    // do a startup one
    while (!terminate) {
      try {
        sleep(10000);
      } catch (InterruptedException e) {
      }
      elapse += 10;
      if (elapse >= secs) {     // is it time to print it
        if (MemoryChecker.checkMemory(true)) {
          prta(Util.clear(runsb).append("MemoryCheck recommends shutdown").append(Util.getProcess()));
          SendEvent.doOutOfMemory("MemCheck", par);
          if (allowShutdown) {
            Util.exit(1);
          }
        }
        elapse = 0;
      } else {
        checkMemory(false); // No just do the check
      }
    }
    prta("MemoryChecker.run() exiting....");
  }

  public static boolean checkMemory() {
    return checkMemory(true);
  }

  /**
   * check the free memory and if things are getting short, send events and emails to dave
   *
   * @param print Set true to print out status and to do shutdown check etc. User must implement
   * shutdown
   * @return True if this routine recommends an memory based shutdown. User must implement shutdown
   */
  public static boolean checkMemory(boolean print) {
    long now = System.currentTimeMillis();
    freeMemory = Runtime.getRuntime().freeMemory();
    maxMemory = Runtime.getRuntime().maxMemory();
    totalMemory = Runtime.getRuntime().totalMemory();
    if (print) {
      prta(Util.clear(tmpsb).append(" freeMemory=").append(Util.df21(freeMemory / 1000000.)).
              append(" maxMem=").append(Util.df21(maxMemory / 1000000.)).
              append(" totMem=").append(Util.df21(totalMemory / 1000000.)).
              append(" net=").append(Util.df21((maxMemory - totalMemory + freeMemory) / 1000000.)));
    }
    Util.clear(monitorsb);
    monitorsb.append("freeMem=").append(Util.df21(freeMemory / 1000000.)).
            append("\nmaxMem=").append(Util.df21(maxMemory / 1000000.)).
            append("\ntotMem=").append(Util.df21(totalMemory / 1000000.)).
            append("\nnetMem=").append(Util.df21((maxMemory - totalMemory + freeMemory) / 1000000.)).append("\n");
    /*if(print && maxMemory == totalMemory && totalMemory != lastTotalMemory && maxMemory > 500000000) {
      SendEvent.debugSMEEvent("MemMaxed","Memory "+Util.getNode()+" just expanded to max="+maxMemory/1000000+" free="+freeMemory/1000000,Util.getProcess());
      lastTotalMemory = totalMemory;
    }*/
    if ((maxMemory - totalMemory + freeMemory) < Math.min(totalMemory / 5, 50000000) && maxMemory == totalMemory) {  // 20% or 5 gB whichever is less
      //prta(Util.clear(tmpsb).append("**FreeMemory warning went off ").append((maxMemory - totalMemory + freeMemory)/1000000).
      //        append(" ").append(Util.df21((maxMemory - totalMemory + freeMemory)*100./maxMemory)).append("% free").
      //        append(" ").append(now-lastGCRun));
      loopsBad++;
      double pct = (maxMemory - totalMemory + freeMemory) * 100. / maxMemory;
      if (now - lastGCRun > 59000) {
        Runtime.getRuntime().gc();
        lastGCRun = now;
        long newfreeMemory = Runtime.getRuntime().freeMemory();
        long newmaxMemory = Runtime.getRuntime().maxMemory();
        long newtotalMemory = Runtime.getRuntime().totalMemory();
        if ((newmaxMemory - newtotalMemory + newfreeMemory) < Math.min(newtotalMemory / 5, 50000000)) {  // 20% or 5 gB which ever is less
          prta(Util.clear(tmpsb).append("*** FreeMemory did not clear on GC run ").append(Util.df21(pct)).append(" now ").
                  append(((newmaxMemory - newtotalMemory) + newfreeMemory) * 100. / newmaxMemory).
                  append("% free ").append((newmaxMemory - newtotalMemory + newfreeMemory) / 1000000).
                  append(" of ").append(newtotalMemory / 1000000).append(" mB"));
        } else {
          prta(Util.clear(tmpsb).append(" * FreeMemory warning caused manual GC run.  Free now OK. aft=").
                  append(((newmaxMemory - newtotalMemory) + newfreeMemory) / 1000000).
                  append(" of ").append(newtotalMemory / 1000000).append(" mB was ").append(Util.df21(pct)).append(" now ").
                  append(Util.df21((newmaxMemory - newtotalMemory + newfreeMemory) * 100. / newmaxMemory)).append("% free "));
          return false;
        }
      }
      if (now - lastPage > 600000 && loopsBad > 2) {
        SendEvent.edgeSMEEvent("MemoryLow", "MemCheck:" + Util.getProcess() + (Util.getProperty("Instance") == null?"":Util.getProperty("Instance")) + " free mem critical "
                + Util.df21((maxMemory - totalMemory + freeMemory) / 1000000.) + "/" + Util.df21(Runtime.getRuntime().maxMemory() / 1000000.)
                + "/" + Util.df21(Runtime.getRuntime().freeMemory() / 1000000.) + "mB "
                + Util.getNode() + "/" + System.getProperty("user.name"), Util.getProcess());
        SimpleSMTPThread.email(Util.getProperty("emailTo"), "MemCheck:" + Util.getProcess() + " free memory critical " + Util.getNode() + "/" + Util.getAccount(),
                Util.ascdate() + " " + Util.asctime()
                + "\nfreeMemory=" + Util.df21(freeMemory / 1000000.)
                + " maxMem=" + Util.df21(maxMemory / 1000000.) + " totMem=" + Util.df21(totalMemory / 1000000.)
                + " net=" + Util.df21((maxMemory - totalMemory + freeMemory) / 1000000.) + "\n");
        lastPage = now;
      }

      if (print) {
        memWatchDog++;
        prta(Util.clear(tmpsb).append("Memory low check shutdown watchdog=").append(memWatchDog).
                append(" ").append(Util.df21((maxMemory - totalMemory + freeMemory) / 1000000.)));
        if (memWatchDog >= 2) {
          prta(Util.clear(tmpsb).append("Memory recommending shutdown....").append(maxMemory - totalMemory + freeMemory));
          SendEvent.edgeSMEEvent("MemLoShutdwn", "MemCheck:" + Util.getProcess() + " free mem low recommend shutdown ="
                  + Util.df21((maxMemory - totalMemory + freeMemory) / 1000000.) + " "
                  + Util.getNode() + "/" + System.getProperty("user.name"), Util.getProcess());
          SimpleSMTPThread.email(Util.getProperty("emailTo"), "MemCheck:" + Util.getProcess() + " free mem low recommand shutdown ="
                  + freeMemory + " " + Util.getNode() + "/" + Util.getAccount(),
                  Util.ascdate() + " " + Util.asctime()
                  + "\nfreeMemory=" + Util.df21(freeMemory / 1000000.)
                  + " maxMem=" + Util.df21(maxMemory / 1000000.) + " totMem=" + Util.df21(totalMemory / 1000000.)
                  + " net=" + Util.df21((maxMemory - totalMemory + freeMemory) / 1000000.) + "\n");
          return true;
        }
      } else {
        memWatchDog = 0;
      }
    } else {
      loopsBad = 0;
      memWatchDog = 0;
    }
    return false;
  }
}
