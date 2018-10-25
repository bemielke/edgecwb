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
 * This deals with the "AD" type packets.
 *
 * @author davidketchum
 */
public final class Sensor {

  String time;
  long timeInMillis;
  byte sensorNumber;
  int count;
  int countLimit;
  double level;
  double[] masspos = new double[3];
  StringBuilder snw = new StringBuilder(100);

  public StringBuilder getSNWSenderString() {
    if (snw.length() > 0) {
      snw.delete(0, snw.length());
    }
    snw.append(":3:");
    for (int i = 0; i < 3; i++) {
      snw.append("Reftek Sensor ").append(sensorNumber + 1).append(" Mass Position Chan ").append(i + 1).append("=").append(masspos[i]).append(i != 2 ? ";" : "");
    }
    return snw;
  }
  byte[] scratch = new byte[18];

  @Override
  public String toString() {
    return toStringBuilder(null).toString();
  }

  public StringBuilder toStringBuilder(StringBuilder tmp) {
    StringBuilder sb = tmp;
    if (sb == null) {
      sb = Util.clear(snw);
    }
    sb.append(sensorNumber).append(" count=").append(count).append("/").append(countLimit).
            append(" lev=").append(level).append(" mass=").append(masspos[0]).
            append(" ").append(masspos[1]).append(" ").append(masspos[2]);
    return sb;
  }

  public long getTimeInMillis() {
    return timeInMillis;
  }

  public void load(ByteBuffer bb, String tm) {
    time = tm;
    timeInMillis = Reftek.getTime(time);
    sensorNumber = bb.get();
    sensorNumber -= 48;     // remove ascii '0'
    bb.get();
    bb.get(scratch, 0, 6);
    count = Integer.parseInt(new String(scratch, 0, 6).trim());
    bb.get(scratch, 0, 6);
    countLimit = Integer.parseInt(new String(scratch, 0, 6).trim());
    bb.get(scratch, 0, 4);
    level = Double.parseDouble(new String(scratch, 0, 4).trim());
    bb.get(scratch, 0, 4);
    masspos[0] = Double.parseDouble(new String(scratch, 0, 4).trim());
    bb.get(scratch, 0, 4);
    masspos[1] = Double.parseDouble(new String(scratch, 0, 4).trim());
    bb.get(scratch, 0, 4);
    masspos[2] = Double.parseDouble(new String(scratch, 0, 4).trim());
    //Util.prt(toString());
  }
}
