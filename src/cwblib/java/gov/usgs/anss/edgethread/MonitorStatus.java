/*
 *  This software is in the public domain because it contains materials 
 *  that originally came from the United States Geological Survey, 
 *  an agency of the United States Department of Interior. For more 
 *  information, see the official USGS copyright policy at 
 *  http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.edgethread;

/**
 * This thread prints out a EdgeThread status string at intervals. It is especially useful for
 * EdgeTHreads that might do nothing and hence their log files to not progress. By using the parent
 * thread to print and get status, this assures this does not happen.
 *
 * @author davidketchum
 */
public final class MonitorStatus extends Thread {

  private final long ms;
  private boolean terminate;
  private final EdgeThread parent;

  public void terminate() {
    terminate = true;
    interrupt();

  }

  /**
   * Creates a new instance
   *
   * @param ms millis to wait between each status output
   * @param parent The EdgeThread to ask for its Monitor String when this port is hit
   */
  public MonitorStatus(long ms, EdgeThread parent) {
    this.parent = parent;
    this.ms = ms;
    terminate = false;
    // Register our shutdown thread with the Runtime system.
    gov.usgs.anss.util.Util.prta("new ThreadMonitorStatus " + getName() + " " + getClass().getSimpleName() + " par=" + parent.getClass().getSimpleName());
    setDaemon(true);
    start();
  }

  @Override
  public void run() {
    boolean dbg = false;
    StringBuilder sb = new StringBuilder(10000);

    // OPen up a port to listen for new connections.
    while (true) {
      for (int i = 0; i < ms / 1000; i++) {
        try {
          sleep(1000);
        } catch (InterruptedException e) {
        }
        if (terminate) {
          break;
        }
      }
      parent.prta("MS: " + parent.getStatusString());
      if (terminate) {
        break;
      }
    }
    parent.prta("MonitorStatus exiting for " + parent.getStatusString());
  }
}
