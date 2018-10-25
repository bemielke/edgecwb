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
import gov.usgs.cwbquery.QueryMom;

/**
 *
 * @author U.S. Geological Survey  <ketchum at usgs.gov>
 */
public final class QueryMomInEdgeMom extends EdgeThread {

  String[] args;

  /**
   * terminate thread (causes an interrupt to be sure) you may not need the
   * interrupt!
   */
  @Override
  public void terminate() {
    terminate = true;
    interrupt();
  }

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
   * return the monitor string for Nagios/ICINGA
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

  public QueryMomInEdgeMom(String argline, String tg) {
    super(argline, tg);
    args = argline.split("\\s");
    setDaemon(true);
    running = true;
    start();
  }

  @Override
  public void run() {
    for (int i = 0; i < args.length; i++) {
      prta(i + "=" + args[i]);
    }
    QueryMom.main(args);
    prta("QueryMom.main() has exited!!!!");
    running = false;
  }
}
