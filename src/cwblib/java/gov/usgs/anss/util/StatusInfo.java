/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.util;

import gov.usgs.anss.edgethread.EdgeThread;

/**
 * StatusInfo.java
 *
 * Created on December 21, 2003, 2:39 PM
 *
 * A status info like ProcessStatus or ChannelStatus implements this interface so that it can be
 * used as the data storage portion of a UdpProcess. Basically members of this interface store
 * status data sent via UDP to UdpProcess. UdpProcess maintains a ordered list of these in getKey()
 * order. The getKey() must return the key used by the comparable interface (such keys must be
 * unique). UdpProcess uses the update() when a new UDP packet's key matches the key. Update() must
 * then update the data while maintaining the key. getData() returns the exact binary buffer as it
 * was used to update() or create this key. update() is used rather than just putting the new data
 * in the list maintained there so that difference calculations can be done in update (i.e. CPUTICKS
 * converted to pct of cpu using last packet and current packet times and cputicks).
 *
 * @author ketchum
 */
public interface StatusInfo {

  /**
   * return the main key
   *
   * @return String with Key
   */
  public String getKey();

  /**
   * Update this object with the data in the argument
   *
   * @param ps A StatusInfo packet to be updated
   */
  public void update(Object ps);

  /**
   * Get the time of this StatusInfo
   *
   * @return GregorianCalendar with the time
   */
  public long getTime();

  /**
   * Return the raw data for this StatusInfo type
   *
   * @return array of bytes with the raw status info packet
   */
  public byte[] getData();

  //** create a backing database record if implmented */
  public void createRecord();

  //** create a new binary buffer with data in record */
  public byte[] makeBuf();

  /**
   * this must reload the object from a raw data buffer
   *
   * @param b the bytes to reload
   */
  public void reload(byte[] b);

  // Note you must implement this static method but it cannot be required by the interface
  //public static  int getMaxLength();
  public void setParent(EdgeThread parent);
}
