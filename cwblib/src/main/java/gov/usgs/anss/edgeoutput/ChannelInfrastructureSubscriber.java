/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.edgeoutput;

import java.util.GregorianCalendar;
import gov.usgs.anss.edge.TimeSeriesBlock;

/** Describes the interface for classes that want to use the ChannelInfrastructure to subscribe
 * to data to make it more in-order (LISS and Hydra are the main ones).
 *
 * @author davidketchum
 */
abstract public class ChannelInfrastructureSubscriber {

  /**
   * Creates a new instance of ChannelInfrastructureSubscriber
   */
  public ChannelInfrastructureSubscriber() {
  }

  abstract public void tickle();

  abstract GregorianCalendar getDesired();

  abstract GregorianCalendar getEarliest();

  abstract int getWaitTime();

  abstract void queue(byte[] buf, int len, Class cl, int oor);

  abstract void queue(TimeSeriesBlock b, int oor);

  abstract void shutdown();

  /**
   * return true if overlaps allowed (LISS) or false if strictly non-overlapping (Hydra)
   */
  abstract boolean getAllowOverlaps();

  abstract StringBuilder toStringBuilder(StringBuilder object);

}
