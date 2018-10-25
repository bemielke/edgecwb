/*
 *  This software is in the public domain because it contains materials 
 *  that originally came from the United States Geological Survey, 
 *  an agency of the United States Department of Interior. For more 
 *  information, see the official USGS copyright policy at 
 *  http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package subspacedetector;

import gov.usgs.anss.util.Util;

/**
 * This class just holds station related information for an instance of the subspace detector. It is
 * not really a collection as it can only hold one station.
 *
 * @author benz
 */
public final class StationCollection {

  private String network;                     // 2 character network code
  private String stnam;                       // 5 character station code
  private String location;                    // 2 character location code
  private double latitude;                    // latitude of station
  private double longitude;                   // longitude of station
  private double elevation;                   // elevation of station
  private String[] channels;                 // list of channels used in processing
  private String[] completechannellist;      // complete list of channels
  private int rate;                           // sample rate for channels

  private String phaselabel;                  // Identified phase referenced in processing
  private String phaselabelcomponent;         // channel used to identify referenced phase

  @Override
  public String toString() {
    String chns = "";
    for (String c : channels) {
      chns += c + " ";
    }
    return network + "." + stnam + "." + location + " " + Util.df25(latitude) + " " 
            + Util.df25(longitude) + " " + Util.df21(elevation) + " rt=" + rate + " " + chns;
  }

  public void setLatitude(double d) {
    latitude = d;
  }

  public void setLongitude(double d) {
    longitude = d;
  }

  public void setElevation(double d) {
    elevation = d;
  }

  public void setNetwork(String d) {
    network = d;
    for (int i = network.length(); i < 2; i++) {
      network = network + " ";
    }
  }

  public void setStname(String d) {
    stnam = d;
    for (int i = stnam.length(); i < 5; i++) {
      stnam = stnam + " ";
    }
  }

  public void setLocation(String d) {
    location = d;
    for (int i = location.length(); i < 2; i++) {
      location = location + " ";
    }
  }

  public void setRate(int d) {
    rate = d;
  }

  public void setChannels(String[] d) {

    channels = new String[d.length];

    for (int i = 0; i < channels.length; i++) {
      channels[i] = "" + d[i];
    }
  }

  public void setCompleteChannelList(String[] d) {

    completechannellist = new String[d.length];

    for (int i = 0; i < completechannellist.length; i++) {
      completechannellist[i] = "" + d[i];
    }
  }

  public void setPhaseLabel(String d) {
    phaselabel = d;
  }

  public void setPhaseLabelComponent(String d) {
    phaselabelcomponent = d;
  }

  public String getStname() {
    return stnam;
  }

  public String getLocation() {
    return location;
  }

  public String getNetwork() {
    return network;
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

  public int getRate() {
    return rate;
  }

  public String[] getChannels() {
    return channels;
  }

  public String[] getCompleteChannelList() {
    return completechannellist;
  }

  public int getNumberofChannels() {
    return channels.length;
  }

  public String getPhaseLabel() {
    return phaselabel;
  }

  public String getPhaseLabelComponent() {
    return phaselabelcomponent;
  }

  public StationCollection() {
  }
}
