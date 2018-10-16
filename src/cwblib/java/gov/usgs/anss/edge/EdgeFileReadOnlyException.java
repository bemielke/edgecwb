/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.edge;

/** This exception is thrown if the software is opening a Writeable file, but said file is readonly.
 *
 * @author davidketchum
 */
public class EdgeFileReadOnlyException extends Exception {
  
  /** Creates a new instance of EdgeFileReadOnlyException
   * @param s Some text about the error*/
  public EdgeFileReadOnlyException(String s) {  
    super(s);
  }
  
}
