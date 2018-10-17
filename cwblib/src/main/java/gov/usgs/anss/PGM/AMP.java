/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.PGM;

/**
 * This class encapsulates the amplitude related portion of the PGM calculation.
 *
 * @author davidketchum
 */
public final class AMP {

  protected double aminmm;
  protected double amaxmm;
  protected double pmax;
  protected int imax;
  protected int imin;

  @Override
  public String toString() {
    return "amin=" + aminmm + " amax=" + amaxmm + " pmax=" + pmax + " imax=" + imax + " imin=" + imin;
  }
}
