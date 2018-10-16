/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.edge;

/**
 * This exception is thrown by the various parts of the Edge/CWB system if the 12 character
 * NNSSSSSCCCLL does not follow the restrictions on character content.
 *
 * @author davidketchum
 */
public class IllegalSeednameException extends Exception {

  /**
   * Creates a new instance of IllegalSeednameException
   *
   * @param s Some text with the error
   */
  public IllegalSeednameException(String s) {
    /**
     * Creates a new instance of IllegalSeednameException
     */
    super(s);
  }

}
