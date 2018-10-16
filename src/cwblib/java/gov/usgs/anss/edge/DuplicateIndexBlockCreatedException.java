/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.edge;

/**
 * This Exception for the Edge/CWb system is thrown when two IndexBlocks are created at the same
 * time. This is thrown as a race condition and the one that throws it does not create the
 * IndexBlock.
 *
 * @author davidketchum
 */
public class DuplicateIndexBlockCreatedException extends Exception {

  /**
   * Creates a new instance of DuplicateIndexBlockCreatedException
   *
   * @param s Some text about the error
   */
  public DuplicateIndexBlockCreatedException(String s) {
    super(s);
  }

}
