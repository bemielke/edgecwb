/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.alarm;

import gov.usgs.anss.util.Util;

/**
 * This thread is a permanent one which if armed, waits a timeout interval and
 * if it is not disarmed, causes a SendEvent.edgeSMEEvent("PanicThrTO",... to be
 * generated and the program exits. It is used in MakeVSATRoutes originally so
 * that if the process hands in the reconfiguration thread, an attempt is made
 * to stop and get things going again.
 *
 * @author davidketchum
 */
public final class PanicThread extends Thread {

  long lastTime;
  boolean exit;
  long waitLen;
  String message;
  boolean armed;

  /**
   * This thread starts a time out and if it has not been exited before the
   * interval it does a SendEvent and System.exit()
   *
   * @param msg Print this message and send in event if this happens
   * @param timeout The time to wait in seconds before panicking and exiting
   */
  public PanicThread(String msg, int timeout) {
    lastTime = System.currentTimeMillis();
    waitLen = timeout * 1000L;
    message = msg;
    armed = false;
    start();
  }

  /**
   * Use to arm or disarm the thread. If the thread is armed a new count too
   * timeout starts
   *
   * @param t If true, arm the thread
   */
  public void setArmed(boolean t) {
    if (t) {
      lastTime = System.currentTimeMillis();
    }
    armed = t;
  }

  public void terminate() {
    exit = true;
  }

  @Override
  public void run() {
    for (;;) {
      try {
        sleep(1000);
      } catch (InterruptedException expected) {
      }
      if (!armed) {
        continue;
      }
      if (exit) {
        break;
      }
      if (System.currentTimeMillis() - lastTime > waitLen) {
        Util.prt(getName() + " PanicThread went off. close the socket " + (System.currentTimeMillis() - lastTime) + message + " " + Util.getIDText());
        SendEvent.edgeSMEEvent("PanicThrTO", message + " " + Util.getIDText(), this);
        MakeVsatRoutes.terminatePanic();
        System.exit(9);
        Util.prt(getName() + " PanicThread past system.shutdown!");
        break;
      }
    }
    if (!exit) {
      Util.prt(getName() + "PanicThread abnormal exited=" + exit);
    }
  }
}
