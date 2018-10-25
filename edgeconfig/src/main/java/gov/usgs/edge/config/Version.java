/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */

package gov.usgs.edge.config;

import java.text.SimpleDateFormat;

public class Version
{
  public static final SimpleDateFormat VERSION_FORMAT =
          new SimpleDateFormat("yyyy-MM/dd");
  public static final String v = "2013-02/06";

  public static String getVersion()
  {
    return v;
  }
}
