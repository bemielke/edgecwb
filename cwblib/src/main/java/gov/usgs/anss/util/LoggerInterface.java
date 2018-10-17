/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.util;

import java.io.PrintStream;

/**
 * Classes implementing this interface can register with EdgeThread or Util to use the same log file
 *
 * @author U.S. Geological Survey <ketchum at usgs.gov>
 */
public interface LoggerInterface {

  public void setLogPrintStream(PrintStream in);
}
