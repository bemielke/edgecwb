/*
 *  This software is in the public domain because it contains materials 
 *  that originally came from the United States Geological Survey, 
 *  an agency of the United States Department of Interior. For more 
 *  information, see the official USGS copyright policy at 
 *  http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.alarm;

import gov.usgs.anss.edgethread.EdgeThread;

/**
 * This class checks on the state of the Alarm thread and if its state is not 1
 * list the state. This is used to find hangs in the alarm thread.
 *
 * @author U.S. Geological Survey  <ketchum at usgs.gov>
 */
public final class AlarmChecker extends Thread {

  public AlarmChecker() {
    EdgeThread.staticprta("AlarmChecker: startup ");
    setDaemon(true);
    start();
  }

  @Override
  public void run() {
    for (;;) {
      try {
        sleep(120000);
      } catch (InterruptedException expected) {
      }
      if (Alarm.state != 1) {
        EdgeThread.staticprta("AlarmChecker: state=" + Alarm.state + " loop=" + Alarm.loop);
      }
    }
  }
}
