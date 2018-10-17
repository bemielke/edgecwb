/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.util;

import java.util.Comparator;

/**
 * StationComponentComparator.java = for Display implement a ordering by station/component.
 *
 *
 * @author D.C. Ketchum
 */
public class StationComponentComparator implements Comparator {

  /**
   * Creates a new instance of StationComponentComparator
   */
  public StationComponentComparator() {
  }

  /**
   * implement comparison
   *
   * @param o1 left hand of comparison
   * @param o2 right hand of comparison
   * @return -1, 0, or +1 if o1 < o2, o1=o2, or o1>o2 respectively
   */
  @Override
  public int compare(Object o1, Object o2) {
    String s1 = ((StatusInfo) o1).getKey();
    String s2 = ((StatusInfo) o2).getKey();
    return s1.substring(2, 9).compareTo(s2.substring(2, 9));
  }

  /**
   * for channel Status equality is not meaningful
   *
   * @param o1 Right side of equality comparison
   * @return false, there are no equal ChannelStatus
   */
  @Override
  public boolean equals(Object o1) {
    return false;
  }

  @Override
  public int hashCode() {
    int hash = 3;
    return hash;
  }

}
