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
 * Implement a Comparator for the ChannelDisplay in NodeProcess order
 *
 * @author ketchum
 */
public class NodeProcessComparator implements Comparator {

  /**
   * Creates a new instance of NodeProcessComparator
   */
  public NodeProcessComparator() {
  }

  /**
   * implement the comparator
   */
  @Override
  public int compare(Object o1, Object o2) {
    if (o1.equals(o2)) {
      return 0;
    }
    ChannelStatus c1 = (ChannelStatus) o1;
    ChannelStatus c2 = (ChannelStatus) o2;
    String s1 = c1.getCpu() + c1.getProcess() + c1.getKey();
    String s2 = c2.getCpu() + c2.getProcess() + c2.getKey();
    return s1.compareTo(s2);
  }
  /**
   * equality is non-sequitor
   *
   * @return false
   */
  //@Override
  /*public boolean equals(Object o1) 
    if
    return false;
  }*/

}
