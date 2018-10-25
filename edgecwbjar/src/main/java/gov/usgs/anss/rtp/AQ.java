/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */

package gov.usgs.anss.rtp;
import java.nio.ByteBuffer;
import gov.usgs.anss.util.Util;

/**
 * Encapsulate an AQ record from a Reftek 130.
 *
 * @author davidketchum
 */
public class AQ {

  private String time;
  private long timeInMillis;
  private String acquisitionRequested;
  private String acquisitionActive;
  private int eventCount;
  private String eventInProgress;
  private int ramTotal;
  private int ramUsed;
  private int ramAvailable;
  private final StringBuilder snw = new StringBuilder(300);

  // These fields come from XC status messages
  private final byte[] scratch = new byte[18];

  public long getTimeInMillis() {
    return timeInMillis;
  }

  public StringBuilder getSNWSenderString() {
    Util.clear(snw);
    snw.append(":3:Reftek Acquisition Active=").append(acquisitionActive);
    //snw.append(";Reftek RAM Available="+ramAvailable);
    snw.append(";Reftek RAM % Avail=").append(ramAvailable * 100 / ramTotal);
    snw.append(";Reftek RAM Total Kb=").append(ramTotal);
    //snw.append(";Reftek RAM Used Kb="+ramUsed);
    //snw.append(";Reftek Acquisition Status observed at="+time);

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
    sb.append("req=").append(acquisitionRequested).append(" active=").append(acquisitionActive).
            append(" #evt=").append(eventCount).append(" inProg=").append(eventInProgress).
            append(" RAM(kB): ").append(ramUsed).append(" of ").append(ramTotal).append(" ").
            append(ramAvailable).append(" free");
    return sb;
  }

  /**
   *
   * @param bb Load this AQ record from the ByteBfffer.
   */
  public void load(ByteBuffer bb) {
    bb.get(scratch, 0, 18);
    time = new String(scratch, 0, 18).trim();
    timeInMillis = Reftek.getTime(time);
    bb.get(scratch, 0, 1);
    acquisitionRequested = new String(scratch, 0, 1);
    bb.get(scratch, 0, 1);
    acquisitionActive = new String(scratch, 0, 1);
    bb.get(scratch, 0, 6);
    eventCount = Integer.parseInt(new String(scratch, 0, 6).trim());
    bb.get(scratch, 0, 2);
    eventInProgress = new String(scratch, 0, 2);
    bb.get(scratch, 0, 6);
    ramTotal = Integer.parseInt(new String(scratch, 0, 6).trim());
    bb.get(scratch, 0, 6);
    ramUsed = Integer.parseInt(new String(scratch, 0, 6).trim());
    bb.get(scratch, 0, 6);
    ramAvailable = Integer.parseInt(new String(scratch, 0, 6).trim());

  }

  public String getTime() {
    return time;
  }

  public int getRamTotal() {
    return ramTotal;
  }

  public int getRamUsed() {
    return ramTotal;
  }

  public int getRamAvailable() {
    return ramTotal;
  }

  public int getEventCount() {
    return eventCount;
  }

  public String getEventInProgress() {
    return eventInProgress;
  }

  public String getAcquisitionRequested() {
    return acquisitionRequested;
  }

  public String getAcquisitionActive() {
    return acquisitionActive;
  }

  public int getPctRamAvailable() {
    return ramAvailable * 100 / ramTotal;
  }

}
