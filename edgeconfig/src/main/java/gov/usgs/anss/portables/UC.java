/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.portables;

import gov.usgs.anss.gui.RTSStationPanel;
import gov.usgs.anss.guidb.TCPStation;
import gov.usgs.anss.util.ErrorTrack;
import gov.usgs.anss.util.Util;
import java.util.StringTokenizer;

/**
 * This class extends gov.usgs.anss.util.UC, which holds global constants and utility methods. This
 * class has changes that are specific to the Anss application.
 */
public final class UC extends gov.usgs.anss.util.UC {

  private static final String JDBC_CATALOG = "anss";

  private static final String PROPERTY_FILENAME = "anss.prop";

  //public static final int XSIZE = 750;
  //public static final int YSIZE = 700;
  public static String JDBCCatalog() {
    return JDBC_CATALOG;
  }

  public static String getPropertyFilename() {
    return PROPERTY_FILENAME;
  }

  /**
   * validate an IP address in dotted number form nnn.nnn.nnn.nnn. This will insure there are 4
   * bytes in the address and that the digits are all numbers. between the dots the numbers can be
   * one, two or three digits long.
   *
   * @param t The textfield with a supposed IP adr
   * @param err errorTrack variable
   * @return String with the reformated to nnn.nnn.nnn.nnn form
   */
  public static String chkIP(javax.swing.JTextField t, ErrorTrack err) {
    // The string is in dotted form, we return always in 3 per section form  
    StringBuilder out = new StringBuilder(15);
    StringTokenizer tk = new StringTokenizer(t.getText(), ".");
    if (t.getText().equals("")) {
      err.set(true);
      err.appendText("IP format bad - its null");
      return "";
    }
    if (t.getText().charAt(0) < '0' || t.getText().charAt(0) > '9') {
      Util.prt("FUTIL: chkip Try to find station " + t.getText());
      TCPStation a = RTSStationPanel.getTCPStation(t.getText());
      String station = t.getText();
      if (a != null) {
        Util.prt("FUtil: chkip found station=" + a);
        t.setText(a.getIP());
        err.appendText(a.getTCPStation());
        tk = new StringTokenizer(t.getText(), ".");
      } else {
        err.set(true);
        err.appendText("IP format bad digit");
        return "";
      }
    }
    if (tk.countTokens() != 4) {
      err.set(true);
      err.appendText("IP format bad - wrong # of '.'");
      return "";
    }
    for (int i = 0; i < 4; i++) {
      String s = tk.nextToken();
      if (s.length() == 3) {
        out.append(s);
      } else if (s.length() == 2) {
        out.append("0").append(s);
      } else if (s.length() == 1) {
        out.append("00").append(s);
      } else {
        err.set(true);
        err.appendText("IP byte wrong length=" + s.length());
        return "";
      }
      if (i < 3) {
        out.append(".");
      }
    }
    t.setText(out.toString());
    return out.toString();
  }
}
