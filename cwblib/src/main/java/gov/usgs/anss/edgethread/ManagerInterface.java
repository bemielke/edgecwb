/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.edgethread;

/**
 * This is the interface implemented by threads that are to be controlled by a manager process. Such
 * processes need to be able to set a check flag in a process to track whether it has disappeared, a
 * way to terminate the threads if they have disappeared, and to gather status from its children.
 *
 * @author davidketchum
 */
public interface ManagerInterface {

  /**
   * set the check flag in this file
   *
   * @param a What to set the check flag to
   */
  abstract public void setCheck(boolean a);

  /**
   * return the check flag
   *
   * @return The state of the check flag
   */
  abstract public boolean getCheck();

  abstract public void terminate();

  abstract public String getArgs();

  abstract public boolean isAlive();

  abstract public boolean isRunning();

  abstract public long getBytesIn();

  abstract public long getBytesOut();
}
