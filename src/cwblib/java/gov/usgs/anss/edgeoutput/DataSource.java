/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */

package gov.usgs.anss.edgeoutput;
import java.io.*;

/** This class provides the interface for different types of input to the output 
 * infrastructure (say ring files or sockets are specific implementation of this 
 * abstract class).
 *
 * @author davidketchum
 */
abstract public class DataSource {
  
  /** Creates a new instance of DataSource */
  public DataSource() {
  }
  /** this method should return the number of bytes and put data in buf*/
  abstract int getNextData(byte [] buf) throws IOException;   // This class returns the next mini-seed block, or whatever
  
}
