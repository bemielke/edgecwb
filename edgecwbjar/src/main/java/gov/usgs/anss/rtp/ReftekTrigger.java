/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.rtp;

import java.util.GregorianCalendar;
import gov.usgs.anss.util.Util;

/** Decode a reftek trigger time
 *
 * @author U.S. Geological Survey  <ketchum at usgs.gov>
 */
public class ReftekTrigger {

  long triggerTime;
  StringBuilder seedname = new StringBuilder(12);
  String filename;
  GregorianCalendar trig = new GregorianCalendar();

  public ReftekTrigger(StringBuilder seed, long time, String file) {
    Util.clear(seedname).append(seed);
    Util.rightPad(seedname, 12);
    triggerTime = time;
    trig.setTimeInMillis(time);
    filename = file;
  }

  public long getTriggerTime() {
    return triggerTime;
  }

  public StringBuilder getSeedname() {
    return seedname;
  }

  public String getFilename() {
    return filename;
  }

  @Override
  public String toString() {
    return seedname + " " + Util.ascdate(trig) + " " + Util.asctime2(trig) + " " + filename;
  }
}
