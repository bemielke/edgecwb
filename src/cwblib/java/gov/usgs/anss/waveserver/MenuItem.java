/*
 *  This software is in the public domain because it contains materials 
 *  that originally came from the United States Geological Survey, 
 *  an agency of the United States Department of Interior. For more 
 *  information, see the official USGS copyright policy at 
 *  http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.waveserver;

import gov.usgs.anss.util.Util;

/**
 * Represent one item from a WaveServer menu
 *
 * @author U.S. Geological Survey <ketchum at usgs.gov>
 */
public class MenuItem implements Comparable {

  private String network;
  private String station;
  private String channel;
  private String location;
  private String seedname;
  private String type;
  private double start;
  private double end;

  public String getSeedname() {
    return seedname;
  }

  public String getNetwork() {
    return network;
  }

  public String getStation() {
    return station;
  }

  public String getChannel() {
    return channel;
  }

  public String getLocation() {
    return location;
  }

  public double getStart() {
    return start;
  }

  public double getEnd() {
    return end;
  }

  public long getStartInMillis() {
    return (long) (start * 1000.);
  }

  public long getEndInMillis() {
    return (long) (end * 1000.);
  }

  public MenuItem(String seedname, double start, double end, String type) {
    this.seedname = (seedname.replaceAll("-", " ") + "  ").substring(0, 12);
    this.seedname = this.seedname.substring(0, 10) + this.seedname.substring(0, 2).replaceAll(" ", "-");
    network = this.seedname.substring(0, 2);
    station = this.seedname.substring(2, 7);
    channel = this.seedname.substring(7, 10);
    location = this.seedname.substring(10, 12);
    this.start = start;
    this.end = end;
    this.type = type;
  }

  public MenuItem(String network, String station, String channel, String location, double start, double end, String type) {
    this.network = network;
    this.station = station;
    this.channel = channel;
    this.location = location;
    this.seedname = Util.makeSeedname(network, station, channel, location);
    this.start = start;
    this.end = end;
    this.type = type;
  }

  public MenuItem(String line) {
    load(line);
  }

  public final void load(String line) {
    String[] parts = line.trim().split("\\s");
    if (parts.length != 8) {
      Util.prt("Bad Menu load line=" + line);
      network = "";
      station = "";
      channel = "";
      start = 0;
      end = 0;
      return;
    }
    network = parts[3];
    station = parts[1];
    channel = parts[2];
    location = parts[4];
    this.seedname = Util.makeSeedname(network, station, channel, location);
    start = Double.parseDouble(parts[5]);
    end = Double.parseDouble(parts[6]);
    type = parts[7];
  }

  @Override
  public String toString() {
    return seedname + " " + type + " " + Util.ascdatetime2(getStartInMillis()) + " " + Util.ascdatetime2(getEndInMillis());
  }

  @Override
  public int compareTo(Object o1) {
    return seedname.compareTo(((String) o1));
  }
}
