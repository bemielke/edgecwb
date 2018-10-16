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
 * LatencyComparator.java For the Display Channel system implements a comparator for latency order
 *
 * @author ketchum
 */
public class LatencyComparator implements Comparator {

  /**
   * Creates a new instance of LatencyComparator
   */
  public LatencyComparator() {
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
    double l1 = ((ChannelStatus) o1).getLatency();
    double l2 = ((ChannelStatus) o2).getLatency();
    return (l1 > l2) ? 1 : (l1 == l2) ? 0 : -1;
  }

  /**
   * This has no meaning for equality, always return false
   *
   * @param o1 the object to compare to
   * @return always false
   */
  @Override
  public boolean equals(Object o1) {
    if(o1 instanceof ChannelStatus) return false;
    return false;
  }

}
