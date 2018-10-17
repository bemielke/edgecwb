/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.edge;

import gov.usgs.anss.util.Util;

/**
 * Class is a time out thread which can be set up with some other thread to cause it to be
 * interrupted() after some interval and destroy() after some longer interval. Use to cause clean up
 * of threads that can hang indefinitely.
 *
 * @author davidketchum
 */
public final class TimeoutThread extends Thread {

  static boolean noTimeout;
  long last;        // Last time timeout watchdog hit
  long msInterval;
  long destroyInterval;
  Thread target;    // This is the thread we are to stop if it times out
  boolean interruptSent;
  boolean destroySent;
  boolean terminate;
  String tag;

  public static void setNoTimeout() {
    noTimeout = true;
  }

  /**
   * Creates a new instance of TimeoutThread
   *
   * @param tg A string tag to use to refer to this thread
   * @param targ The thread to monitor
   * @param interval The time in MS to set the watchdog for sending an interrupt()
   * @param destInt The time in MS at which to send a follow up destroy()
   */
  public TimeoutThread(String tg, Thread targ, int interval, int destInt) {
    msInterval = interval;
    target = targ;
    tag = tg;
    resetTimeout();
    destroyInterval = destInt;
    interruptSent = false;
    destroySent = false;
    terminate = false;
    //gov.usgs.anss.util.Util.prta("new ThreadTimeout "+getName()+" "+getClass().getSimpleName()+" tag="+tag);
    start();
  }

  @Override
  public void run() {
    if (noTimeout) {
      for (;;) {
        try {
          sleep(10000);
        } catch (InterruptedException e) {
        }
      }
    }
    while ((System.currentTimeMillis() - last) < msInterval) {
      try {
        if (System.currentTimeMillis() - last > 0) {
          sleep(Math.max(1, System.currentTimeMillis() - last));
        }
      } catch (InterruptedException e) {
        if (terminate) {
          break;
        }
      }
    }
    // if we get here the timer has expired.  Terminate the target
    // if this thread has it terminate set, skip out!
    if (target.isAlive() && !terminate) {
      interruptSent = true;
      Util.prta(tag + " TO: interrupt sent");
      target.interrupt();
      while ((System.currentTimeMillis() - last) < destroyInterval && target.isAlive()) {
        try {
          sleep(1000);
        } catch (InterruptedException e) {
          if (terminate) {
            break;
          }
        }
      }
      if (target.isAlive()) {
        destroySent = true;
        Util.prta(tag + " TO: destroy sent");
        //target.destroy();
      }
      if (target.isAlive()) {
        Util.prta(tag + " TO: wait for target to die.");
      }
      while (target.isAlive()) {
        if (terminate) {
          break;
        }
        try {
          sleep(100);
        } catch (InterruptedException e) {
        }
      }
    }
    //Util.prta(tag+" TO: exiting terminate="+terminate);
  }

  /**
   * has this timeout expired and sent an interrupted
   *
   * @return has an interrupt be sent by this timeout thread
   */
  public boolean hasSentInterrupt() {
    return interruptSent;
  }

  /**
   * has this timeout sent a destroy
   *
   * @return true if a destroy has been sent
   */
  public boolean hasSentDestroy() {
    return destroySent;
  }

  /**
   * reset the time out interval (basically a watchdog reset)
   */
  public final void resetTimeout() {
    last = System.currentTimeMillis();
  }

  /**
   * shutdown this thread
   */
  public void shutdown() {
    terminate = true;
    interrupt();
  }
}
