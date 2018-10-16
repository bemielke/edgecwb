/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.channelstatus;

import gov.usgs.anss.util.StatusInfo;
import java.util.Comparator;

/**
 * SourceStationComponentComparator.java - creates the ordering for ChannelStatus records in
 * Source/Station/Component order.
 *
 * Created on January 7, 2004, 10:39 AM
 *
 * @author D.C. Ketchum
 */
public class SourceStationComponentComparator implements Comparator {

  /**
   * Creates a new instance of SourceStationComponentComparator
   */
  public SourceStationComponentComparator() {
  }

  /**
   * implement the comparison
   *
   * @param o1 Left side of ChannelStatus comparison
   * @param o2 Right side of ChannelStatus comparison
   * @return -1, 0 , or 1 if o1 less than o2, equal to, or greater than respectively
   */
  @Override
  public int compare(Object o1, Object o2) {
    String s1 = ((ChannelStatus) o1).getSource() + ((StatusInfo) o1).getKey().substring(2, 9);
    String s2 = ((ChannelStatus) o2).getSource() + ((StatusInfo) o2).getKey().substring(2, 9);
    return s1.compareTo(s2);
  }

  /**
   * Channel status cannot be meaningfully equal
   *
   * @param o1
   * @return always false, never equal
   */
  @Override
  public boolean equals(Object o1) {
    return false;
  }

}
