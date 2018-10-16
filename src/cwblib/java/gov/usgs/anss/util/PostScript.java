/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.util;

import java.awt.Color;
import java.text.DecimalFormat;

/**
 * This class provides static methods to assist in creating PostScript and EPS output.
 * 
 */
public class PostScript {

  public static final DecimalFormat FF = new DecimalFormat("0.000000");

  /* Prevent this class from being instantiated. */
  private PostScript() {
  }

  /**
   * Return a string representation of color as a triple of floating-point values separated by
   * spaces, suitable for use in a PostScript document.
   *
   * @param color the Color to format
   * @return a string representation of {@code color}
   */
  public static String colorToString(Color color) {
    float[] rgb;

    rgb = color.getRGBColorComponents(null);

    return FF.format(rgb[0]) + " " + FF.format(rgb[1]) + " " + FF.format(rgb[2]);
  }

  /**
   * Escape a string using PostScript conventions. The escaped characters are a subset of those
   * mentioned in section 3.2.2 of the
   * <a href="http://www.adobe.com/products/postscript/pdfs/PLRM.pdf">PostScript Language Reference
   * Manual</a>.
   *
   * @param s the string to escape
   * @return the escaped string
   */
  public static String escape(String s) {
    StringBuffer sb;
    int i;
    char c;

    sb = new StringBuffer();
    for (i = 0; i < s.length(); i++) {
      c = s.charAt(i);
      switch (c) {
        case '\b':
          sb.append("\\b");
          break;
        case '\f':
          sb.append("\\f");
          break;
        case '\\':
          sb.append("\\\\");
          break;
        case '(':
          sb.append("\\(");
          break;
        case ')':
          sb.append("\\)");
          break;
        default:
          sb.append(c);
          break;
      }
    }
    return sb.toString();
  }
}
