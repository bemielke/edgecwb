/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */

package gov.usgs.anss.gui;

//import gov.usgs.anss.gui.RTSStationPanel;
import gov.usgs.anss.guidb.TCPStation;
import gov.usgs.anss.guidb.TCPStation;
import java.util.StringTokenizer;
import gov.usgs.anss.util.ErrorTrack;
import gov.usgs.anss.util.Util;

/**
 * This class extends gov.usgs.anss.util.UC, which holds global constants and
 * utility methods. This class has changes that are specific to the Anss
 * application.
 */
public class UC   extends gov.usgs.anss.util.UC 

{
  private static final String JDBC_CATALOG = "anss";

  private static final String PROPERTY_FILENAME = "anss.prop";

  public static final int XSIZE = 750;
  public static final int YSIZE = 750;

  public static String JDBCCatalog()
  {
    return JDBC_CATALOG;
  }

  public static String getPropertyFilename()
  {
    return PROPERTY_FILENAME;
  }


}
