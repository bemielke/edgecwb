/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.rtp;

import gov.usgs.anss.util.Util;
import java.util.ArrayList;
import java.nio.ByteBuffer;

/**
 *
 * @author davidketchum
 */
public final class StationChannel {

  private String experimentNumber;
  private String experimentName;
  private String experimentComment;
  private String stationNumber;
  private String stationName;
  private String stationComment;
  private String dasModel;
  private String dasSerial;
  private String experimentStart;
  private String clockType;
  private String clockSerial;
  private String implementTime;
  private final ArrayList<ChannelInfo> channels = new ArrayList<>(30);
  private final StringBuilder snw = new StringBuilder(100);

  @Override
  public String toString() {
    return toStringBuilder(null).toString();
  }

  public StringBuilder toStringBuilder(StringBuilder tmp) {
    StringBuilder sb = tmp;
    if (sb == null) {
      sb = Util.clear(snw);
    }
    sb.append("Exp=").append(experimentNumber.trim()).append("/").append(experimentName.trim()).
            append("/").append(experimentComment.trim()).append("\n" + "Station: ").append(stationNumber.trim()).
            append("/").append(stationName.trim()).append("/").append(stationComment.trim()).
            append("\n" + "DAS :").append(dasModel.trim()).append("/").append(dasSerial.trim()).
            append(" Clock: ").append(clockType.trim()).append("/").append(clockSerial.trim()).
            append("\n" + "Times:  experiment=").append(experimentStart.trim()).
            append(" Implement : ").append(implementTime.trim()).append("\n");
    for (ChannelInfo ci : channels) {
      if (ci != null) {
        sb.append(ci.toStringBuilder(null)).append("\n");
      }
    }
    return sb;
  }

  public StationChannel() {
    for (int i = 0; i < 30; i++) {
      channels.add(i, null);
    }
  }

  public void load(ByteBuffer bb) {
    int pos = bb.position();
    byte[] scratch = new byte[76];
    bb.get(scratch, 0, 2);
    experimentNumber = new String(scratch, 0, 2);
    bb.get(scratch, 0, 24);
    experimentName = new String(scratch, 0, 24);
    bb.get(scratch, 0, 40);
    experimentComment = new String(scratch, 0, 40);
    bb.get(scratch, 0, 4);
    stationNumber = new String(scratch, 0, 4);
    bb.get(scratch, 0, 24);
    stationName = new String(scratch, 0, 24);
    bb.get(scratch, 0, 40);
    stationComment = new String(scratch, 0, 40);
    bb.get(scratch, 0, 12);
    dasModel = new String(scratch, 0, 12);
    bb.get(scratch, 0, 12);
    dasSerial = new String(scratch, 0, 12);
    bb.get(scratch, 0, 14);
    experimentStart = new String(scratch, 0, 14);
    bb.get(scratch, 0, 4);
    clockType = new String(scratch, 0, 4);
    bb.get(scratch, 0, 10);
    clockSerial = new String(scratch, 0, 10);
    for (int i = 0; i < 5; i++) {
      ChannelInfo ci = new ChannelInfo();
      ci.load(bb);
      if (!ci.channelNumber.trim().equals("")) {
        int index = Integer.parseInt(ci.channelNumber.trim());
        channels.add(index, ci);
        //Util.prt("Add chan "+index+" "+ci);
      }
    }
    bb.get(scratch, 0, 76);
    bb.get(scratch, 0, 16);
    implementTime = new String(scratch, 0, 16);

  }

  public class ChannelInfo {

    String channelNumber;
    String channelName;
    String azimuth;
    String inclination;
    String x;
    String y;
    String z;
    String xyunit;
    String zunit;
    String preampGain;
    String sensorModel;
    String sensorSerial;
    String comment;
    String adjustedBitWeight;
    StringBuilder snw = new StringBuilder(100);

    @Override
    public String toString() {
      return toStringBuilder(null).toString();
    }

    public StringBuilder toStringBuilder(StringBuilder tmp) {
      StringBuilder sb = tmp;
      if (sb == null) {
        sb = Util.clear(snw);
      }
      sb.append("ch#=").append(channelNumber.trim()).append(" ").append(channelName.trim()).
              append(" az=").append(azimuth.trim()).append(" incl=").append(inclination.trim()).
              append(" x=").append(x.trim()).append(" y=").append(y.trim()).append(" z=").append(z.trim()).
              append(" xyunit=").append(xyunit.trim()).append(" zunit=").append(zunit.trim()).
              append(" preamp=").append(preampGain.trim()).
              append(" sensor=").append(sensorModel.trim()).append("/").append(sensorSerial.trim()).
              append(" bitw=").append(adjustedBitWeight.trim()).append(" comnt=").append(comment.trim());

      return sb;
    }

    public ChannelInfo() {

    }

    public void load(ByteBuffer bb) {
      byte[] scratch = new byte[40];
      bb.get(scratch, 0, 2);
      channelNumber = new String(scratch, 0, 2);
      bb.get(scratch, 0, 10);
      channelName = new String(scratch, 0, 10);
      bb.get(scratch, 0, 10);
      azimuth = new String(scratch, 0, 10);
      bb.get(scratch, 0, 10);
      inclination = new String(scratch, 0, 10);
      bb.get(scratch, 0, 10);
      x = new String(scratch, 0, 10);
      bb.get(scratch, 0, 10);
      y = new String(scratch, 0, 10);
      bb.get(scratch, 0, 10);
      z = new String(scratch, 0, 10);
      bb.get(scratch, 0, 4);
      xyunit = new String(scratch, 0, 4);
      bb.get(scratch, 0, 4);
      zunit = new String(scratch, 0, 4);
      bb.get(scratch, 0, 4);
      preampGain = new String(scratch, 0, 4);
      bb.get(scratch, 0, 12);
      sensorModel = new String(scratch, 0, 12);
      bb.get(scratch, 0, 12);
      sensorSerial = new String(scratch, 0, 2);
      bb.get(scratch, 0, 40);
      comment = new String(scratch, 0, 40);
      bb.get(scratch, 0, 8);
      adjustedBitWeight = new String(scratch, 0, 8);

    }
  }
}
