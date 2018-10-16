/*
 *  This software is in the public domain because it contains materials 
 *  that originally came from the United States Geological Survey, 
 *  an agency of the United States Department of Interior. For more 
 *  information, see the official USGS copyright policy at 
 *  http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.comcat;

import gov.usgs.anss.util.Util;

/**
 * This class just holds the simple stuff for a pick. It is used in the ComCatEvent class to build
 * up a list of picks from the QuakeML file.
 *
 * @author U.S. Geological Survey  <ketchum at usgs.gov>
 */
public final class SimplePick {

  private String nscl;
  private long pickTime;
  private String phase;
  private String onset;
  private String polarity;

  @Override
  public String toString() {
    return nscl + " " + Util.ascdatetime2(pickTime) + " " + phase + (onset == null ? "" : " onset=" + onset)
            + (polarity == null ? "" : " polar=" + polarity);
  }

  public SimplePick(String chan, long picktime, String ph) {
    nscl = chan;
    pickTime = picktime;
    phase = ph;

  }

  public SimplePick(String chan, long picktime, String ph, String onset, String polarity) {
    nscl = chan;
    pickTime = picktime;
    phase = ph;
    this.onset = onset;
    this.polarity = polarity;
  }

  public String getNSCL() {
    return nscl;
  }

  public long getPickTimeInMillis() {
    return pickTime;
  }

  public String getPhase() {
    return phase;
  }

  public String getOnset() {
    return onset;
  }

  public String getPolarity() {
    return polarity;
  }

  public void setPickTime(long newtime) {
    pickTime = newtime;
  }

  public void setNSCL(String chan) {
    nscl = chan;
  }

  public void setPhase(String ph) {
    phase = ph;
  }

  public void setOnset(String on) {
    onset = on;
  }

  public void setPolarity(String polar) {
    polarity = polar;
  }
}
