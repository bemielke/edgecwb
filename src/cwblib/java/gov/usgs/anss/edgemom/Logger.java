/*
 *  This software is in the public domain because it contains materials 
 *  that originally came from the United States Geological Survey, 
 *  an agency of the United States Department of Interior. For more 
 *  information, see the official USGS copyright policy at 
 *  http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.edgemom;
import gov.usgs.anss.edgethread.EdgeThread;
import gov.usgs.anss.util.Util;

/**
 * This class is a minimal EdgeThread so it can be created solely for the
 * purpose of being used to log messages from some class to which it is passed.
 * Often computational code is separate from the EdgeThread where it is run. By
 * using this class the test routines can make use of the EdgeThread logging but
 * not actually have to a thread doing something.
 *
 * @author U.S. Geological Survey  <ketchum at usgs.gov>
 */
public final class Logger extends EdgeThread {

  /**
   * return the status string for this thread
   *
   * @return A string representing status for this thread
   */
  @Override
  public StringBuilder getStatusString() {
    Util.clear(statussb);
    return statussb;
  }

  /**
   * return the monitor string for ICINGA
   *
   * @return A String representing the monitor key value pairs for this
   * EdgeThread
   */
  @Override
  public StringBuilder getMonitorString() {
    Util.clear(monitorsb);
    return monitorsb;
  }

  /**
   * return console output - this is fully integrated so it never returns
   * anything
   *
   * @return "" since this cannot get output outside of the prt() system
   */
  @Override
  public StringBuilder getConsoleOutput() {
    return consolesb;
  }

  @Override
  public void terminate() {
    terminate = true;
    interrupt();
  }

  /**
   * Create a logger
   *
   * @param argline The command line might just be ">>>logfile"
   * @param tag Some tag for identifying the output
   */
  public Logger(String argline, String tag) {
    super(argline, tag);
    setDaemon(true);
    start();
  }

  @Override
  public void run() {
    running = true;
    while (!terminate) {
      try {
        sleep(1000);
      } catch (InterruptedException expected) {
      }   // sleep until terminated!

    }
    prta("Logger thread is terminated");
    running = false;
  }
}
