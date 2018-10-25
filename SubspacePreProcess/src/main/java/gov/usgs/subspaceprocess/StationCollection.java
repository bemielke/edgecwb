/*
 * Class for defining important station attributes
 */
package gov.usgs.subspaceprocess;

/**
 * This class just holds information about a station.
 * <br>
 * DCK: I do not see that it is a collection of any type, just a data holding class with getters and
 * setters for a single station. Probably should be called SubspaceStationInfo or some such.  It basically
 * is just wrapping configuration data which it has poked into and pulled out of it.  
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

  /** CLear out all data in the Stations collection so its ready for reuse.
   * 
   */
  public void clear() {
    latitude=Double.MAX_VALUE;                    // latitude of station
    longitude=Double.MAX_VALUE;                   // longitude of station
    elevation=Double.MAX_VALUE;                   // elevation of station
    channels = null;
    completechannellist=null;
    network=null;
    stnam=null;
    location=null;
    rate = 0;
    phaselabel = null;
    phaselabelcomponent=null;
    
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
  
  public boolean coordinatesOK() {
    return !(latitude == Double.MAX_VALUE || longitude == Double.MAX_VALUE || elevation == Double.MAX_VALUE);
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
    clear();
  }
}
