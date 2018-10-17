/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.edgeoutput;

import gov.usgs.anss.util.Util;

/**
 * this class is used by the Hydra class to track the last time of a packet sent through to Hydra so
 * that the channel will not send out packets with time reversals. The "end time" is limited to the
 * near future so that a bad time setting into the future will not preclude data flowing forever.
 *
 * @author davidketchum
 */
public final class InorderChannel {

  private long lastEnd;
  private final StringBuilder seedname = new StringBuilder(12);
  private int lastSeq;

  @Override
  public String toString() {
    return seedname + " last time=" + Util.ascdate(lastEnd) + " " + Util.asctime2(lastEnd) + " seq=" + lastSeq;
  }

  /**
   * create a new in order channel
   *
   * @param seed the seedname of the channel
   * @param last the initial setting of the time
   * @param seq the last sequence or other integer identifier
   */
  public InorderChannel(String seed, long last, int seq) {
    setLastTimeInMillis(last);
    Util.clear(seedname).append(seed);
    lastSeq = seq;
  }

  /**
   * create a new in order channel
   *
   * @param seed the seedname of the channel
   * @param last the initial setting of the time
   */
  public InorderChannel(String seed, long last) {
    setLastTimeInMillis(last);
    Util.clear(seedname).append(seed);
  }

  /**
   * create a new in order channel
   *
   * @param seed the seedname of the channel
   * @param last the initial setting of the time
   * @param seq the last sequence or other integer identifier
   */
  public InorderChannel(StringBuilder seed, long last, int seq) {
    setLastTimeInMillis(last);
    Util.clear(seedname).append(seed);
    while (seedname.length() < 12) {
      seedname.append(" ");
    }
    lastSeq = seq;
  }

  /**
   * create a new in order channel
   *
   * @param seed the seedname of the channel
   * @param last the initial setting of the time
   */
  public InorderChannel(StringBuilder seed, long last) {
    setLastTimeInMillis(last);
    Util.clear(seedname).append(seed);
    while (seedname.length() < 12) {
      seedname.append(" ");
    }
  }

  /**
   * get the seedname for this InorderChannel
   *
   * @return The seedname
   */
  public StringBuilder getSeednameSB() {
    return seedname;
  }

  /**
   *
   * @return the last sequence recorded
   */
  public int getLastSeq() {
    return lastSeq;
  }

  /**
   * return the last recorded time in millis
   *
   * @return The last recorded time
   */
  public long getLastTimeInMillis() {
    return lastEnd;
  }

  /**
   * set the last time in millis, but limit it to the near future to prevent a single forward leap
   * from disabling data until that time arises
   *
   * @param ms The Millis to set the last time to
   * @param seq Set the sequence or other integer identifier of the last packet.
   * @return true if the time set was good, false if it had to be limited as its in the future
   */
  public boolean setLastTimeInMillis(long ms, int seq) {
    lastSeq = seq;
    return setLastTimeInMillis(ms);
  }

  /**
   * set the last time in millis, but limit it to the near future to prevent a single forward leap
   * from disabling data until that time arises
   *
   * @param ms The Millis to set the last time to
   * @return true if the time set was good, false if it had to be limited as its in the future
   */
  public final boolean setLastTimeInMillis(long ms) {
    if ((System.currentTimeMillis() + 20000) < ms) {
      lastEnd = System.currentTimeMillis() + 20000;
      return false;
    }
    lastEnd = ms;
    return true;
  }
}
