/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.waveserver;

import gov.usgs.anss.util.Util;

/**
 * This handles the station list for the CWBWS and track status for the
 * stations. This is used to implement some of the Winston Wave Server commands
 * that allow queries about stations.
 *
 * @author U.S. Geological Survey <ketchum at usgs.gov>
 */
class StationList {

  private static final String rankString = "HNZHLZSHZEHZBHZHHZ";
  private final String station;             //NNSSSSSCCLL
  private final String channel;             // Ranking channel which gave location
  private final StringBuilder channelsb = new StringBuilder(12);
  private int ranking = -2;
  private final StringBuilder wwsInstrumentString = new StringBuilder(100);
  private double latitude, longitude, elevation;// Official station location
  private final StringBuilder longname = new StringBuilder(12);
  private final String timezone;            // These are set to GMT

  @Override
  public String toString() {
    return station + " from " + channel + " " + Util.df26(latitude) + " " + Util.df26(longitude) + " " + Util.df21(elevation);
  }

  public StringBuilder getInstrumentString() {
    return wwsInstrumentString;
  }

  public StationList(String channel, StringBuilder longname, double latitude, double longitude, double elevation, String timezone) {
    this.station = channel.substring(0, 7).trim();
    Util.clear(this.longname).append(longname);
    this.channel = channel;
    Util.clear(channelsb).append(channel);
    this.latitude = latitude;
    this.longitude = longitude;
    this.elevation = elevation;
    this.timezone = timezone;
    if (latitude != 0. && longitude != 0) {
      ranking = rankString.indexOf(channel.substring(7, 10));
    } else {
      ranking = -1;
    }

    makeWWSInstrumentString();
    //Util.prt("SSL: Create Stationlist ="+wwsInstrumentString);
  }

  private void makeWWSInstrumentString() {
    Util.clear(wwsInstrumentString).append("name=").append(station.substring(2)).
            append(",description=").append(longname).append(",longitude=").
            append(Util.df26(longitude).trim()).append(",latitude=").
            append(Util.df26(latitude).trim()).append(",height=").
            append(Util.df21(elevation).trim()).append(",timezone=").append(timezone);
  }

  /**
   * Based on the channel ranking, perhaps update the station parameters.
   *
   * @param ch NNSSSSSCCCLL type string
   * @param lat Latitude
   * @param lon Longitude
   * @param elev Elevation
   * @param longname long station name
   */
  public void updateChannel(String ch, double lat, double lon, double elev, StringBuilder longname) {
    int rank = rankString.indexOf(ch.substring(7, 10));
    if (rank >= ranking && lat != 0. && lon != 0.) {
      ranking = rank;
      latitude = lat;
      longitude = lon;
      elevation = elev;
      if (longname.length() == 0) {
        Util.clear(this.longname).append("Unknown\\cUnknown");
      } else {
        Util.clear(this.longname).append(Util.stringBuilderReplaceAll(longname, ",", "\\\\c"));
      }
      makeWWSInstrumentString();
      //Util.prt("SSL:Channel update "+ch+" "+wwsInstrumentString);
    }

  }

}
