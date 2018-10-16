/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.channelstatus;

import java.util.Comparator;

/**
 * LastDataComparator.java
 *
 * Created on January 7, 2004, 10:39 AM For the Display Channel system implements a comparator for
 * last data arrival order
 *
 * @author ketchum
 */
public class LastDataComparator implements Comparator {

  /**
   * Creates a new instance of LastDataComparator
   */
  public LastDataComparator() {
  }

  /**
   * implement Comparator
   *
   * @param o1 First object to compare
   * @param o2 2nd object to compare
   * @return 1 if o1 is older than o2, 0 otherwise
   */
  @Override
  public int compare(Object o1, Object o2) {
    if (o1 == o2) {
      return 0;
    }
    double l1 = ((ChannelStatus) o1).getAge();
    double l2 = ((ChannelStatus) o2).getAge();
    if (l1 == l2) {
      return ((ChannelStatus) o1).getKey().compareTo(((ChannelStatus) o2).getKey());
    }
    return (l1 > l2) ? 1 : -1;
  }

}
