/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.edge;

import java.util.GregorianCalendar;

/**
 * This is an abstract class describing the requirements for a generalized TimeSeriesBlock.
 * Basically certain routines are required like getSeedNameString(), getNsamp, etc. needed for some
 * class to handle these types such as MiniSeed or TraceBuf.
 *
 * @author davidketchum
 */
abstract public class TimeSeriesBlock implements Comparable<TimeSeriesBlock> {

  abstract public StringBuilder toStringBuilder(StringBuilder sb);

  /**
   * get the seed in NSCL order
   *
   * @return the 12 character NSCL seedname
   */
  abstract public String getSeedNameString();

  /**
   * get the seed in NSCL order
   *
   * @return the 12 character NSCL seedname
   */
  abstract public StringBuilder getSeedNameSB();

  /**
   * get digitizing rate in Hz
   *
   * @return the digitizing rate in Hz
   */
  abstract public double getRate();

  /**
   * return number of samples in block
   *
   * @return The number of samples in this block
   */
  abstract public int getNsamp();

  /**
   * get the raw bytes for the block
   *
   * @return A byte array with the raw bytes
   */
  abstract public byte[] getBuf();

  /**
   * get block size in bytes
   *
   * @return The block size in bytes
   */
  abstract public int getBlockSize();

  /**
   * get start time as a GregorianCalendar - NOTE: please use getTimeInMillis() rather than this as
   * this creates a new GregorianCalendar on each invocation. It is better for the user to have a
   * Gregorian and then gregorian.setTimeInMillis(tb.getTimeInMillis()); if you need a gregorian.
   *
   * @return The start time as a GregorianCalendar
   */
  abstract public GregorianCalendar getGregorianCalendar();

  /**
   * get time in millis
   *
   * @return The time in millis as in System.currentTimeMillis()
   */
  abstract public long getTimeInMillis();

  /**
   * get time in millis of the next sample after this buffer
   *
   * @return The time in millis as in System.currentTimeMillis() of next expected sample
   */
  abstract public long getNextExpectedTimeInMillis();

  /**
   * get time in millis of the next sample after this buffer
   *
   * @return The time in millis as in System.currentTimeMillis() of next expected sample
   */
  abstract public long getLastTimeInMillis();

  /**
   * load from a raw buffer
   *
   * @param buf The raw buffer with data
   * @throws IllegalSeednameException if such a seedname is in the raw buffer
   */
  abstract public void load(byte[] buf) throws IllegalSeednameException;

  /**
   * Render this block clear - normally this means everything returns zeros.
   *
   */
  abstract public void clear();

  /**
   * Tell if this block is currently "clear"
   *
   * @return true if the block is clear
   *
   */
  abstract public boolean isClear();

  /**
   * Compare this MiniSeed object to another. First the two objects' SEED names are compared, and if
   * they are equal, their starting dates are compared.
   *
   * @param o the other MiniSeed object
   * @return -1 if this object is less than other, 0 if the two objects are equal, and +1 if this
   * object is greater than other.
   */
  @Override
  public int compareTo(TimeSeriesBlock o) {
    TimeSeriesBlock other;
    int cmp;
    if (o == null) {
      return -1;      // Nulls are also always at end
    }
    if (o.isClear()) {
      return -1;    // Cleared MiniSeeds are always at end
    }
    if (isClear()) {
      return 1;
    }
    other = o;
    cmp = getSeedNameString().compareTo(other.getSeedNameString());
    if (cmp != 0) {
      return cmp;
    }
    if (getTimeInMillis() < other.getTimeInMillis()) {
      return -1;
    } else if (getTimeInMillis() > other.getTimeInMillis()) {
      return 1;
    } else {
      return 0;
    }
  }
}
