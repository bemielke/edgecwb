/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.edgemom;

import gov.usgs.alarm.SendEvent;
import gov.usgs.anss.edgethread.EdgeThread;
import gov.usgs.anss.util.Util;

/**
 * EdgeThreadMonitor.java - This Class is a time out thread which can be set up
 * with some other thread to cause it to be interrupted() after some interval
 * and destroy() after some longer interval. Use to cause clean up of threads
 * that can hang indefinitely. This thread monitors an edge thread for
 * communications minimums. Each interval the number of bytes in and out
 * returned by the EdgeThread.getBytesin() and EdgeThread.getBytesout() is
 * checked for minimum flows. If the flows are under the minimum for a specified
 * number of consecutive intervals, the thread to be killed has its terminate()
 * called to cause it to exit (and EdgeMom to hopefully restart!). At the
 * beginning and after a terminate, a pause of 4x-interval (Four times the
 * interval) is made as a warmup. Normally this thread is started by the
 * EdgeThread to be monitored as part of its startup. It is not normally
 * configured in an edgemom.setup.
 * <br>
 * <br> This was originally written to monitor a RawInputServer which was
 * listening for input from a RCV/STATION pair getting data from the VMS side.
 * It is the ultimate failsafe on a hang in this communications.
 * <br>
 * <PRE>
 * switch			arg		Description
 * -minout		n			The number of bytes (minimum) this should have output in one interval (def=0)
 * -minin			n			The number of bytes (minimum) this should have gotten in one interval (def=1000)
 * -n					n			The number of intervals under minimum which cause declaration of a terminate on the monitored thread (def=5)
 * -montag		aaa		The tag of the edge thread (from edgemom.setup) to monitor for input or output (can be different from the tag to be killed).
 * -killtag		aaa		The tag of the edge thread (from edgemom.setup) to kill when this monitor goes off
 * -ms				n			The number of milliseconds between checks (at interval)
 * -dbg							Turn on verbose logging
 *
 * </PRE>
 *
 * @author davidketchum
 */
public final class EdgeThreadMonitor extends EdgeThread {

  private static boolean noTimeout; // set true disables the time outs for debugging
  private long last;                // Last time timeout watchdog hit
  private long msInterval;
  private long destroyInterval;
  private EdgeThread t;           // This is the thread we are monitoring
  private String killTag;
  private String targetTag;
  private int minIn;
  private int minOut;
  private long lastIn;
  private long lastOut;
  private int numberConsecutive;
  private int nkills;
  private long lastEmail;
  private final StringBuilder status = new StringBuilder(100);
  private boolean dbg;

  /**
   * Return a string of status for display by edgemom.
   *
   * @return Bland for this thread.
   */
  @Override
  public StringBuilder getStatusString() {
    Util.clear(statussb);
    return statussb.append("ETM: mon=").append(targetTag).append(" kill=").append(killTag).
            append(" last ").append(status);
  }

  /**
   * Return the monitor string for Icinga.
   *
   * @return A String representing the monitor key value pairs for this
   * EdgeThread
   */
  @Override
  public StringBuilder getMonitorString() {
    return monitorsb;
  }

  @Override
  public String toString() {
    return targetTag;
  }

  /**
   * Creates a new instance of EdgeThreadMonitor.
   *
   * @param tg A string tag to use to refer to this thread
   * @param line Command line - see class description for documentation.
   */
  public EdgeThreadMonitor(String line, String tg) {
    super(line, tg);
    msInterval = 60000;
    minIn = 1000;
    minOut = -1;
    numberConsecutive = 5;
    String[] args = line.split(" ");
    for (int i = 0; i < args.length; i++) {
      switch (args[i]) {
        case "-minin":
          minIn = Integer.parseInt(args[i + 1]);
          i++;
          break;
        case "-minout":
          minOut = Integer.parseInt(args[i + 1]);
          i++;
          break;
        case "-ms":
          msInterval = Integer.parseInt(args[i + 1]);
          i++;
          break;
        case "-montag":
          targetTag = args[i + 1];
          i++;
          break;
        case "-n":
          numberConsecutive = Integer.parseInt(args[i + 1]);
          i++;
          break;
        case "-killtag":
          killTag = args[i + 1];
          i++;
          break;
        case "-dbg":
          dbg = true;
          break;
        default:
          prta("Uknown switch to EdgeThreadMonitor=" + args[i]);
          break;
      }
    }
    if (targetTag != null && killTag == null) {
      killTag = targetTag;
    }
    if (targetTag == null) {
      prta("ETM: ** has no target tag- this is likely wrong! args=" + line);
    }
    lastEmail = 0;
    tag = tg;
    start();
  }

  /**
   * Return the tag used to ID this thread.
   *
   * @return The tag
   */
  @Override
  public String getTag() {
    return tag;
  }

  /**
   * Return any console input - not used in this thread.
   *
   * @return ""
   */
  @Override
  public StringBuilder getConsoleOutput() {
    return consolesb;
  }

  /**
   * Terminate this monitor.
   */
  @Override
  public void terminate() {
    terminate = true;
    interrupt();
  }

  /**
   * Main run method.
   */
  @Override
  public void run() {
    running = true;
    boolean goOff;
    int loop = 0;
    try {
      sleep(4 * msInterval);
    } catch (InterruptedException expected) {
    }// Give some warmup.
    for (;;) {
      try {
        sleep(msInterval);
      } catch (InterruptedException expected) {
      }
      t = EdgeMom.getEdgeMoms().get(0).getEdgeThread(targetTag);
      goOff = false;
      Util.clear(status);
      if (t == null) {
        prta("ETM : ****** tag=" + targetTag + " killtag=" + killTag + " edge thread is nulll!!!! minIn=" + minIn
                + " minOut=" + minOut + " loop=" + loop + " #trig=" + numberConsecutive + " #kills=" + nkills);
        continue;
      }
      if (t != null) {
        status.append("minIn=").append(minIn).append(" in=").append(t.getBytesIn() - lastIn).
                append(" minOut=").append(minOut).append(" out=").append(t.getBytesOut() - lastOut).
                append(" loop=").append(loop).append(" #trig=").append(numberConsecutive).
                append(" #kills=").append(nkills);
      }
      if (minIn > 0 && Math.abs(t.getBytesIn() - lastIn) < minIn) {
        goOff = true;
      }
      if (minOut > 0 && Math.abs(t.getBytesOut() - lastOut) < minOut) {
        goOff = true;
      }
      if (goOff) {
        loop++;
      } else {
        loop = 0;
      }
      if (dbg || loop > 0) {
        prta(status.toString());
      }
      if (goOff && loop >= numberConsecutive) {
        EdgeThread killit = EdgeMom.getEdgeMoms().get(0).getEdgeThread(killTag);
        nkills++;
        prta("ETM: went off minIn=" + minIn + " in=" + (t.getBytesIn() - lastIn)
                + " minOut=" + minOut + " out=" + (t.getBytesOut() - lastOut) + "Killtag=" + killTag + " kill = " + killit);
        if (killit != null) {
          killit.terminate();
        }
        if (System.currentTimeMillis() - lastEmail < 600000) {
          SendEvent.edgeSMEEvent("ETMExec", "Killed " + killTag + " on " + targetTag, this);
          Util.prta(
                  Util.ascdate() + " " + Util.asctime() + " This message comes from a EdgeThreadMonitor running on " + Util.getNode()
                  + "\n on monitor tag " + targetTag + " which kills " + killTag + " if in < " + minIn + " or out < " + minOut
                  + " every " + msInterval + " ms\nin=" + (t.getBytesIn() - lastIn)
                  + " minOut=" + minOut + " out=" + (t.getBytesOut() - lastOut) + " But apparently not very sucessfully\n");
          lastEmail = System.currentTimeMillis();
        }
        try {
          sleep(4 * msInterval);
        } catch (InterruptedException expected) {
        }// give some warmup.
      }
      lastIn = t.getBytesIn();
      lastOut = t.getBytesOut();
      if (terminate) {
        break;
      }
    }   // end of infinite loop
    prt("ETM: exiting");
    running = false;
    terminate = false;
  }

  /**
   * Shutdown this thread = similar to terminate!
   */
  public void shutdown() {
    t.prta(tag + "ETM: shutdown() called");
    terminate = true;
    interrupt();
  }
}
