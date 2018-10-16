/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */

/*
 * Maskable.java - Classes that represent a MySQL table where the ID is used
 * to build masks mush implement this interface.  Mainly used by the different
 * flags panels to provide this interface.
 *
 * Created on October 11, 2007, 10:19 AM
 *
 */

package gov.usgs.anss.gui;

/**
 *
 * @author rjackson
 */
public interface Maskable {
  long getMask();
  int getID();
  
}
