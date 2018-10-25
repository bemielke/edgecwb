/*
 *  This software is in the public domain because it contains materials 
 *  that originally came from the United States Geological Survey, 
 *  an agency of the United States Department of Interior. For more 
 *  information, see the official USGS copyright policy at 
 *  http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.waveserver;

import gov.usgs.anss.util.Util;
import java.nio.ByteBuffer;

/**
 * This class represent the information from a TrinetChannel query. The data is
 * generally loaded from a binary ByteBuffer or is put into same.
 *
 * @author U.S. Geological Survey  <ketchum at usgs.gov>
 */
public class TrinetChannel {

  private final StringBuilder seedname = new StringBuilder(12);
  private final byte[] seednamebuf = new byte[12];

  /**
   * Samples per second.
   */
  private double sampleRate;    // 16
  /**
   * Station channel latitude decimal degrees.
   */
  private double latitude;      //24
  /**
   * Station channel longitude decimal degrees.
   */
  private double longitude;     //32
  /**
   * Station channel elevation kilometers.
   */
  private double elevation;     //40
  /**
   * Station channel gain.
   */
  private double gain;        //48
  /**
   * Station channel Ml magnitude correction.
   */
  private double mlCorrection; //56
  /**
   * Station channel Me magnitude correction.
   */
  private double meCorrection;  // 64
  private final byte[] network = new byte[3];
  private final byte[] station = new byte[6];
  private final byte[] channel = new byte[4];
  private final byte[] location = new byte[3];
  //byte [] teltype = new byte [2]; // what the hell is 'teltype'? This is not used
  private int instrumentType;   //72
  private final byte[] gain_units = new byte[20]; //76

  public String getSeedname() {
    return seedname.toString().replaceAll("-", " ");
  }

  @Override
  public String toString() {
    return seedname.toString() + " rt=" + sampleRate + " coord=" + latitude + " " + longitude + " " + elevation + " g=" + gain + " ml=" + mlCorrection + " me=" + meCorrection;
  }

  public StringBuilder getSeednameBuilder() {
    return seedname;
  }

  public double getRate() {
    return sampleRate;
  }

  public double getLatitude() {
    return latitude;
  }

  public double getLongitude() {
    return longitude;
  }

  public double getElevation() {
    return elevation;
  }

  public double getGain() {
    return gain;
  }

  public double getMLCorrection() {
    return mlCorrection;
  }

  public double getMECorrection() {
    return meCorrection;
  }

  public TrinetChannel() {
  }

  /**
   * Loads a packetLoad from a channel object //
   *
   *
   * @param pkt
   */
  public void packetLoad(PacketBuilder pkt) {
    pkt.addInt(104);
    pkt.addInt(4);
    pkt.addInt(96);
    pkt.addByteArray(network, 0, 3);   //0
    pkt.addByteArray(station, 0, 6);
    pkt.addByteArray(channel, 0, 4);
    pkt.addByteArray(location, 0, 3);
    pkt.addDouble(sampleRate);  //16
    pkt.addDouble(latitude);
    pkt.addDouble(longitude);
    pkt.addDouble(elevation);   //40
    pkt.addDouble(gain);
    pkt.addDouble(mlCorrection); // 56
    pkt.addDouble(meCorrection); //
    pkt.addInt(instrumentType);  // 72
    pkt.addByteArray(gain_units, 0, 20); //76

  }

  public TrinetChannel(ByteBuffer bb, int len) {
    load(bb, len);
  }

  /**
   *
   * @param ch From the channel list, build up the information about the channel
   * name, rate tec.
   *
   */
  public void load(ChannelList ch) {
    Util.clear(seedname);
    seedname.append((ch.getChannel() + "    ").substring(0, 12));
    for (int i = 0; i < 12; i++) {
      seednamebuf[i] = (byte) seedname.charAt(i);
    }
    System.arraycopy(seednamebuf, 0, network, 0, 2);
    System.arraycopy(seednamebuf, 2, station, 0, 5);
    System.arraycopy(seednamebuf, 7, channel, 0, 3);
    System.arraycopy(seednamebuf, 10, location, 0, 2);
    for (int i = 0; i < seednamebuf.length; i++) {
      if (seednamebuf[i] == '-') {
        seednamebuf[i] = ' ';
      }
    }
    zapSpace(network, 2);
    zapSpace(station, 5);
    zapSpace(channel, 3);
    zapSpace(location, 2);
    sampleRate = ch.getMDSRate();
    latitude = ch.getLatitude();
    longitude = ch.getLongitude();
    elevation = ch.getElevation();

  }

  private void zapSpace(byte[] b, int len) {
    for (int i = 0; i < len; i++) {
      if (b[i] == ' ' || b[i] == '-') {
        b[i] = 0;
      }
    }
  }

  private void fixZero(byte[] b, int len) {
    for (int i = 0; i < len; i++) {
      if (b[i] == 0) {
        for (int j = i; j < len; j++) {
          b[j] = 0;    // zero the remainder of the field
        }
        break;
      }
    }
  }

  public final void load(ByteBuffer bb, int len) {
    int pos = bb.position();
    bb.get(network);
    bb.get(station);
    bb.get(channel);
// uncomment below when when new data format is sent over wire 05/03 aww
    bb.get(location);
    fixZero(network, 3);
    fixZero(station, 6);
    fixZero(channel, 4);
    fixZero(location, 3);
    //bb.get(teltype);
    sampleRate = bb.getDouble();
    latitude = bb.getDouble();
    longitude = bb.getDouble();
    elevation = bb.getDouble();
    gain = bb.getDouble();
    mlCorrection = bb.getDouble();
    meCorrection = bb.getDouble();
    if (len > 76) {
      instrumentType = bb.getInt();
    } else {
      instrumentType = -1;
    }
    if (len >= 96) {
      bb.get(gain_units);
    }
    bb.position(pos + len);
    if (seedname.length() > 0) {
      seedname.delete(0, seedname.length());
    }
    seedname.append(doChar(network[0])).append(doChar(network[1])).
            append(doChar(station[0])).append(doChar(station[1])).append(doChar(station[2])).append(doChar(station[3])).append(doChar(station[4])).
            append(doChar(channel[0])).append(doChar(channel[1])).append(doChar(channel[2])).
            append(doChar(location[0])).append(doChar(location[1]));
  }

  private char doChar(byte b) {
    if (b == '-' || b == 0) {
      return ' ';
    }
    return (char) b;
  }
}
