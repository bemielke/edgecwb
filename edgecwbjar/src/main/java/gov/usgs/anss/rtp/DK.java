/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */

 /*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package gov.usgs.anss.rtp;

import java.nio.ByteBuffer;
import gov.usgs.anss.util.Util;

/**
 * Encapsulate a DK record from a Reftek 130.
 *
 * @author davidketchum
 */
public class DK {

  // These fields come from DK status messages
  private String time;
  private long timeInMillis;
  private double disk1Total;
  private double disk1Used;
  private double disk1Available;
  private double disk2Total;
  private double disk2Used;
  private double disk2Available;
  private String currentDisk;
  private String wrapEnabled;
  private int wrapCount;
  private final StringBuilder snw = new StringBuilder(300);
  private final byte[] scratch = new byte[18];

  public StringBuilder getSNWSenderString() {
    Util.clear(snw);
    snw.append(":6:Reftek Disk 1 % Used=").append(disk1Total <= 0 ? "-1" : ((int) ((disk1Total - disk1Available) * 100 / disk1Total))).
            append(";Reftek Disk 1 Megs Total=").append(disk1Total).append(";Reftek Disk 2 % Used=").
            append(disk2Total <= 0 ? "-1" : ((int) ((disk2Total - disk2Available) * 100 / disk2Total))).
            append(";Reftek Disk 2 Megs Total=").append(disk1Total).append(";Reftek Disk Wrap Enabled=").
            append(wrapEnabled).append(";Reftek Current Disk=").append(currentDisk);
    //snw.append(":3:Reftek Disk 2 Megs Total="+disk2Total+";Reftek Disk 2 Megs Used="+disk2Used+
    //        ";Reftek Disk 2 Megs Available="+disk2Available);
    //snw.append(";Reftek Disk Status observed at="+time);
    return snw;
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
    sb.append("Current=").append(currentDisk).append(" Disk1: ").append(disk1Used).append(" of ").append(disk1Total).append(" ").append(disk1Available).append(" free  Disk2: ").append(disk2Used).append(" of ").append(disk2Total).append(" ").append(disk2Available).append(" free wrap=").append(wrapEnabled).append(" ").append(wrapCount);
    return sb;
  }

  /**
   *
   * @param bb Load a DK record from this byte buffer.
   */
  public void load(ByteBuffer bb) {
    bb.get(scratch, 0, 18);
    time = new String(scratch, 0, 18).trim();
    timeInMillis = Reftek.getTime(time);
    bb.get(scratch, 0, 6);
    disk1Total = Double.parseDouble(new String(scratch, 0, 6).trim());
    bb.get(scratch, 0, 6);
    disk1Used = Double.parseDouble(new String(scratch, 0, 6).trim());
    bb.get(scratch, 0, 6);
    disk1Available = Double.parseDouble(new String(scratch, 0, 6).trim());
    bb.get(scratch, 0, 6);
    disk2Total = Double.parseDouble(new String(scratch, 0, 6).trim());
    bb.get(scratch, 0, 6);
    disk2Used = Double.parseDouble(new String(scratch, 0, 6).trim());
    bb.get(scratch, 0, 6);
    disk2Available = Double.parseDouble(new String(scratch, 0, 6).trim());
    bb.get(scratch, 0, 1);
    currentDisk = new String(scratch, 0, 1);
    bb.get(scratch, 0, 1);
    wrapEnabled = new String(scratch, 0, 1);
    bb.get(scratch, 0, 2);
    wrapCount = Integer.parseInt(new String(scratch, 0, 2).trim(), 16);
  }

  public long getTimeInMillis() {
    return timeInMillis;
  }
}
