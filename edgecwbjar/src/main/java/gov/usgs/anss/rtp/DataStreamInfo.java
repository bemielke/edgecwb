/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.rtp;

import gov.usgs.anss.util.Util;
import java.nio.ByteBuffer;

/**
 * Encapsulate the data from a DS reftek 130 record.
 *
 * @author davidketchum
 */
public class DataStreamInfo {

  private int stream;
  private String location;
  private String comps;
  private String chans;
  private double rate;
  private String band;

  // These come from parsing the log file
  private String streamName;
  private String recordingDestination;
  private String channelsIncluded;
  private double ratelog;
  private String format;
  private String triggerType;
  private String triggerDescription;
  private final byte[] scratch = new byte[162];
  private final EventHeader eh = new EventHeader();
  private final EventHeader et = new EventHeader();
  private final StringBuilder tmpsb = new StringBuilder(80);

  public DataStreamInfo(double r) {
    rate = r;
  }

  public String getComps() {
    return comps;
  }

  public String getChans() {
    return chans;
  }

  public String getBand() {
    return band;
  }

  public String getLocation(int chan) {
    if (chan >= 3 && chans.length() > 3 && location.length() == 4) {
      return location.substring(2, 4).replaceAll("-", " ");
    } else if (location.length() == 0) {
      return "  ";
    }
    return location.substring(0, 2).replaceAll("-", " ");
  }

  public String getSeedComponent(int chan) {
    if (chans == null || comps == null) {
      return null;
    }
    if (chan >= 3 && band.length() == 4) {
      return band.substring(2, 4) + comps.substring(chan, chan + 1);
    }
    chan = chan % 3;

    return band.substring(0, 2) + comps.substring(chan, chan + 1);
  }

  public DataStreamInfo(int str, String loc, String channels, String components, double rt, String bnd) {
    stream = str;
    location = (loc + "    ").substring(0, 4).replaceAll(" ", "-");
    chans = channels;
    comps = components;
    rate = rt;
    band = bnd;
  }

  public void set(String loc, String channels, String components, String bnd, double rt) {
    location = loc.replaceAll(" ", "-").trim();
    chans = channels;
    comps = components;
    rate = rt;
    band = bnd;
  }

  @Override
  public String toString() {
    return toStringBuilder(null).toString();
  }

  public StringBuilder toStringBuilder(StringBuilder tmp) {
    StringBuilder sb = tmp;
    if (sb == null) {
      sb = Util.clear(tmpsb);
    }
    sb.append("  ").append(stream).append(" ").append(chans).append("/").append(comps).append(" ").append(band).append(" ").append(rate);
    return sb;
  }

  public StringBuilder getSoh() {
    Util.clear(tmpsb).append("LOG:").append(streamName).append(" dest=").append(recordingDestination).
            append(" chan=").append(channelsIncluded).append(" rate=").append(rate).append(" form=").append(format).
            append(" trig=").append(triggerType).append("\n" + "    EH:").append(eh).append("\n    ET:").append(et).append("\n");
    return tmpsb;
  }

  /**
   *
   * @param bb Load the event header EH from the byte buffer
   */
  public void loadEH(ByteBuffer bb) {
    eh.load(bb);
  }

  /**
   *
   * @param bb Load the Event trailer from the byte buffer.
   */
  public void loadET(ByteBuffer bb) {
    et.load(bb);
  }

  /**
   *
   * @param bb Load the DS reftek record from a ByteBuffer.
   */
  public void load(ByteBuffer bb) {
    bb.get(scratch, 0, 2);
    stream = Integer.parseInt(new String(scratch, 0, 2).trim());
    bb.get(scratch, 0, 16);
    streamName = new String(scratch, 0, 16);
    bb.get(scratch, 0, 4);
    recordingDestination = new String(scratch, 0, 4);
    bb.getInt();    // reserved
    bb.get(scratch, 0, 16);
    channelsIncluded = new String(scratch, 0, 16);
    bb.get(scratch, 0, 4);
    ratelog = Double.parseDouble(new String(scratch, 0, 4));
    bb.get(scratch, 0, 2);
    format = new String(scratch, 0, 2);
    bb.get(scratch, 0, 16);
    bb.get(scratch, 0, 4);
    triggerType = new String(scratch, 0, 4);
    bb.get(scratch, 0, 162);
    triggerDescription = new String(scratch, 0, 162);
    if (chans == null) {
      chans = "123";
      comps = "ABC";
      if (ratelog >= 80.) {
        band = "HH";
      } else if (ratelog >= 10.) {
        band = "BH";
      } else {
        band = "LH";
      }
      rate = ratelog;
    }
  }

  public int getStream() {
    return stream;
  }

  public double getRate() {
    return rate;
  }

  public String getStreamname() {
    return streamName;
  }

  public String getRecordingDestination() {
    return recordingDestination;
  }

  public String getChannelsIncluded() {
    return channelsIncluded;
  }

  public String getFormat() {
    return format;
  }

  public String getTriggerType() {
    return triggerType;
  }

  public String getTriggerDescription() {
    return triggerDescription;
  }

  public EventHeader getEH() {
    return eh;
  }

}
