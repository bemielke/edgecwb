/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.tsplot;

import java.util.ArrayList;

/*
 * VisibleGraph.java
 *
 * Created on July 17, 2006, 9:38 AM
 * By Jeremy Powell
 *
 */
public final class VisibleGraph {

  private final int max;
  private final int min;
  private final ArrayList lines;
  private final int[] breakValues;

  /**
   * Creates a new instance of VisibleGraph
   *
   * @param maxIn
   * @param minIn
   * @param linesIn
   * @param brkVals
   */
  public VisibleGraph(int maxIn, int minIn, ArrayList linesIn, int[] brkVals) {
    max = maxIn;
    min = minIn;
    lines = linesIn;
    breakValues = brkVals;
  }

  public int getMax() {
    return max;
  }

  public int getMin() {
    return min;
  }

  public int getMid() {
    return (max + min) / 2;
  }

  public int getDelta() {
    return (max - min);
  }

  public ArrayList getLines() {
    return lines;
  }

  public int[] getBreakValues() {
    return breakValues;
  }

}
