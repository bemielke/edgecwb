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
 * This call deals with decoding and storing the US class of status request.
 *
 * @author davidketchum
 */
public final class US {

  //private static DecimalFormat df1 = new DecimalFormat("0.0");
  private String time;
  private long timeInMillis;
  private double inputVolts;
  private double backupVolts;
  private double temperature;   // degrees C
  private double chargerVolts;
  private final StringBuilder snw = new StringBuilder(300);
  // These fields come from XC status messages

  private final byte[] scratch = new byte[18];

  public StringBuilder getSNWSenderString() {
    if (snw.length() > 0) {
      snw.delete(0, snw.length());
    }
    snw.append(":4:Reftek Input Volts=").append(inputVolts);
    snw.append(";Reftek Backup Volts=").append(backupVolts);
    snw.append(";Reftek Temperature Celsius=").append(temperature);
    snw.append(";Reftek Temperature Fahrenheit=").append((int) (temperature * 5 / 9 + 32.5));
    //snw.append(";Reftek Charger Volts="+chargerVolts);
    //snw.append(";Reftek Unit Status observed at="+time+";");
    return snw;
  }

  public long getTimeInMillis() {
    return timeInMillis;
  }

  @Override
  public String toString() {
    return toStringBuilder(null).toString();
  }

  public StringBuilder toStringBuilder(StringBuilder tmp) {
    StringBuilder sb = tmp;
    if (sb == null) {
      sb = Util.clear(snw);
    }
    sb.append(inputVolts).append("V backup=").append(backupVolts).append("V temp=").append(temperature).
            append("C charger=").append(chargerVolts).append("V");
    return sb;
  }

  public void load(ByteBuffer bb) {
    bb.get(scratch, 0, 18);
    time = new String(scratch, 0, 18).trim();
    timeInMillis = Reftek.getTime(time);
    bb.get(scratch, 0, 4);
    inputVolts = Double.parseDouble(new String(scratch, 0, 4).trim());
    bb.get(scratch, 0, 4);
    backupVolts = Double.parseDouble(new String(scratch, 0, 4).trim());
    bb.get(scratch, 0, 6);
    temperature = Double.parseDouble(new String(scratch, 0, 6).trim());
    bb.get(scratch, 0, 4);
    chargerVolts = Double.parseDouble(new String(scratch, 0, 4).trim());

  }

  public String getTime() {
    return time;
  }

  public double getInputVolts() {
    return inputVolts;
  }

  public double getBackupVolts() {
    return backupVolts;
  }

  public double getTemperature() {
    return temperature;
  }

  public double getChargerVolts() {
    return chargerVolts;
  }

}
