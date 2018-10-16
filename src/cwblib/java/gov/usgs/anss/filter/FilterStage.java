/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */

package gov.usgs.anss.filter;

/**
 *
 * @author davidketchum
 */
public abstract class FilterStage {
  abstract public int filt(int [] data, int off);
}
