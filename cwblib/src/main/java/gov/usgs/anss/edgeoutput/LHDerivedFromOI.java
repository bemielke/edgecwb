/*
 *  This software is in the public domain because it contains materials 
 *  that originally came from the United States Geological Survey, 
 *  an agency of the United States Department of Interior. For more 
 *  information, see the official USGS copyright policy at 
 *  http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.edgeoutput;
import gov.usgs.anss.edgemom.LHDerivedToQueryMom;
/** This is a kludge - NetBeans would claim not to find LHDerivedToQuerymom in gov.usgs.anss.edgemom in cwblib
 * presumably because it was in the same package as OutputInfraStructure which is in EdgeCWB.  However,
 * by putting this routine in a different package in cwblib all is well and it just passes the call to 
 * the right class.
 *
 * @author U.S. Geological Survey  <ketchum at usgs.gov>
 */
public class LHDerivedFromOI {
  public static void sendToLHDerived(byte [] buf) {
    LHDerivedToQueryMom.processToLHFromOI(buf);
  }
}
