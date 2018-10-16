/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */

package gov.usgs.anss.edge;

/** This exception happens when Edge data or index files are duplicated on creation due to a race condition.
 *
 * @author davidketchum
 */
public class EdgeFileDuplicateCreationException extends Exception {
  
  /** Creates a new instance of EdgeFileDuplicateCreationException
   * @param s Some text about the error
   */
  public EdgeFileDuplicateCreationException(String s) {
    super(s);
  }
  
}
