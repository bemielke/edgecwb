/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.edgethread;

import gov.usgs.anss.util.Util;

/**
 * Class is a time out thread which can be set up with some other EdgeThread to cause it to be
 * interrupted() after some interval and destroy() after some longer interval. Use to cause clean up
 * of threads that can hang indefinitely.
 *
 * @author davidketchum
 */
public final class EdgeThreadTimeout extends Thread {

  public static boolean noTimeout; // set true disables the time outs for debugging
  private long last;                // Last time timeout watchdog hit
  private long msInterval;
  private long destroyInterval;
  private EdgeThread target;        // This is the thread we are to stop if it times out
  private boolean interruptSent;
  private boolean destroySent;
  private boolean terminate;
  private String tag;

  @Override
  public String toString() {
    return (target == null ? "null" : target.getTag());
  }

  /**
   * set the not timeout flag for debugging
   */
  public static void setNoTimeout() {
    noTimeout = true;
  }

  /**
   * Creates a new instance of EdgeThreadTimeout
   *
   * @param tg A string tag to use to refer to this thread
   * @param targ The thread to monitor
   * @param interval The time in MS to set the watchdog for sending terminate() and interrupt()
   * @param destInt The time in MS at which to send a follow up destroy()
   */
  public EdgeThreadTimeout(String tg, EdgeThread targ, int interval, int destInt) {
    msInterval = interval;
    target = targ;
    if (targ == null) {
      Util.prt("****** got an EdgeThreadTimeout with null target");
      new RuntimeException("EdgeThreadTimeout with null target!").printStackTrace();
      return;
    }
    tag = tg;
    tag += "[" + this.getName().substring(this.getName().indexOf("-") + 1) + "]";
    resetTimeout();
    destroyInterval = destInt;
    interruptSent = false;
    destroySent = false;
    terminate = false;
    start();
  }

  public String getTag() {
    return tag;
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
    for (;;) {
      target.prta(tag + " ETO: *** top of loop " + (System.currentTimeMillis() - last) + " "
              + msInterval + " term=" + terminate + " last=" + last);
      while ((System.currentTimeMillis() - last) < msInterval) {
        try {
          if (terminate) {
            break;
          }
          sleep(msInterval / 100);
          if (!target.isAlive()) {
            target.prta(tag + "Target is not alive! " + target.isAlive() + " " + target.toString());
            break;
          }
        } catch (InterruptedException e) {
          if (terminate) {
            break;
          }
        }
      }
      if (!terminate) {
        target.prta(tag + " ETO: *** MS interval expired or not alive=" + target.isAlive() + " " + (System.currentTimeMillis() - last) + " "
                + msInterval + " term=" + terminate + " last=" + last);
      }
      // if we get here the timer has expired.  Terminate the target
      // if this thread has it terminate set, skip out!
      if (target.isAlive() && !terminate) {
        interruptSent = true;
        target.prta(tag + " ETO: *** interrupt sent val=" + msInterval);
        target.terminate();
        target.interrupt();
        while ((System.currentTimeMillis() - last) < destroyInterval) {
          if (System.currentTimeMillis() - last < msInterval) {
            target.prta(tag + " ETO: restart detected " + (System.currentTimeMillis() - last));
            break;
          }
          try {
            if (terminate) {
              break;
            }
            sleep(destroyInterval / 100);
          } catch (InterruptedException e) {
            if (terminate) {
              break;
            }
          }
        }
        if (System.currentTimeMillis() - last < msInterval) {
          continue;
        }
        if (target.isAlive()) {
          destroySent = true;
          target.prta(tag + " ETO: *** destroy should be sent val=" + destroyInterval);
          //target.destroy();
        }
      } else if (terminate) {
        //target.prta("ETO: terminating.");
      } else {
        target.prta(tag + " ETO: weird the target is not alive or terminate is true alive=" + target.isAlive() + " term=" + terminate);
        terminate = true;     // We want to exit and our child to exit
        target.terminate(); // We want the child to exit

      }
      //if(target.isAlive()) target.prta(tag+" ETO: wait for target to die.");
      //while(target.isAlive()) {try{sleep(100);} catch(InterruptedException e) {}}
      last = System.currentTimeMillis();
      if (terminate) {
        break;
      }
    }   // end of infinite loop
    target.prta(tag + " ETO: exiting terminate=" + terminate);
    target = null;
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
   * reset the interrupt flag
   *
   */
  public void resetInterruptSent() {
    interruptSent = false;
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
   * reset the interrupt and destroy intervals
   *
   * @param msint The new interrupt interval in ms
   * @param msdes The new destroy interval in ms
   */
  public void setIntervals(int msint, int msdes) {
    msInterval = msint;
    destroyInterval = msdes;
  }

  /**
   * shutdown this thread
   */
  public void shutdown() {
    target.prta(tag + "ETO: shutdwn() called");
    terminate = true;
    interrupt();
  }
}
