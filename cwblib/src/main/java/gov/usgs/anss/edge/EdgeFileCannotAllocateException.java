/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.edge;

/**
 * This exception is used by the EdgeCWB system when space is not available.
 *
 * @author davidketchum
 */
public class EdgeFileCannotAllocateException extends Exception {

  /**
   * Creates a new instance of EdgeFileCannotAllocateException
   *
   * @param s Some text about the error
   */
  public EdgeFileCannotAllocateException(String s) {
    super(s);
  }

}
