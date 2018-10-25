/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */

package gov.usgs.anss.rtp;
import java.nio.ByteBuffer;
import gov.usgs.anss.util.Util;

/**
 * This file encapsulates an EH record from a reftek 130.
 *
 * @author davidketchum
 */
public final class EventHeader {

  private byte stream;
  private short event;
  private byte flags;
  private byte format;
  private String timeSource;
  private char timeQuality;
  private String stationName;
  private String streamName;
  private double rate;
  private final byte[] scratch = new byte[34];
  private String triggerTime;
  private String firstSampTime;
  private String triggerType;
  private final StringBuilder tmpsb = new StringBuilder(80);

  @Override
  public String toString() {
    return toStringBuilder(null).toString();
  }

  public StringBuilder toStringBuilder(StringBuilder tmp) {
    StringBuilder sb = tmp;
    if (sb == null) {
      sb = Util.clear(tmpsb);
    }
    sb.append(stationName).append(" ").append(streamName).append(" str=").append(stream).
            append(" ev=").append(event).append(" rt=").append(rate).append(" form=").append(format).
            append(" flags=").append(Util.toHex(flags)).append(" source=").append(timeSource).
            append(" tQual=").append(getTimeQuality()).append(" ").append(triggerTime).
            append(" 1st=").append(firstSampTime).append(" ty=").append(triggerType);
    return sb;
  }

  /** Load this object from a EH binary ByteBuffer.
   * 
   * @param bb The byte buffer containing the positioned EH record
   */
  public void load(ByteBuffer bb) {
    event = Reftek.BCDtoInt(bb.getShort());
    stream = bb.get();    // unused stream #
    stream = (byte) (((stream & 0xf0) >> 4) * 10 + (stream & 0xf));    // convert from BCD
    bb.get(scratch, 0, 3);
    flags = bb.get();
    format = bb.get();
    bb.get(scratch, 0, 33);   // skip message
    timeSource = "" + (char) bb.get();
    timeQuality = (char) bb.get();
    byte fifth = bb.get();
    bb.get(scratch, 0, 4);
    stationName = new String(scratch, 0, 4);
    stationName += (char) fifth;
    stationName = stationName.trim();
    bb.get(scratch, 0, 16);
    streamName = new String(scratch, 0, 16);
    bb.get(scratch, 0, 8);
    bb.get(scratch, 0, 4);
    rate = Double.parseDouble(new String(scratch, 0, 4));
    bb.get(scratch, 0, 4); // skip type
    triggerType = new String(scratch, 0, 4);
    bb.get(scratch, 0, 16);
    triggerTime = new String(scratch, 0, 16);
    bb.get(scratch, 0, 16);
    firstSampTime = new String(scratch, 0, 16);
  }

  public byte getFlags() {
    return flags;
  }

  public byte getFormat() {
    return format;
  }

  public double getRate() {
    return rate;
  }

  public int getEvent() {
    return event;
  }

  public int getStream() {
    return stream;
  }

  public String getTimeSource() {
    return timeSource;
  }

  public String getTimeQuality() {
    return "" + timeQuality;
  }

  public String getStationName() {
    return stationName;
  }

  public String getTriggerTime() {
    return triggerTime;
  }

  public String getFirstSampTime() {
    return firstSampTime;
  }

  public String getTriggerType() {
    return triggerType;
  }

  public String getStreamName() {
    return streamName;
  }

  public int getTimingQualitiy() {
    if (timeQuality == ' ') {
      return 0;
    }
    if (timeQuality == '?') {
      return 1;
    }
    return 100 - (timeQuality - '0') * 10;
  }

}
