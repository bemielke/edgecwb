/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.edgethread;

/**
 * This interface establishes the minimum required to be an edgethread. Most classes extend
 * EdgeThread, but some have to implement because they extend other classes.
 *
 * @author davidketchum
 */
public interface EdgeThreadInterface {

  public void terminate();          // starts a termination on the thread

  public boolean getTerminate();    // returns the terminate variable

  public String getConsoleOutput();        // must return any output from the thread

}
