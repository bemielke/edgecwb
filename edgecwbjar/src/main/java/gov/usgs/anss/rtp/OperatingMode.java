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

/** Encapsulate the data from an OM record from a Reftek
 *
 * @author davidketchum
 */
public final class OperatingMode {

  private String powerState;
  private String recordingMode;
  private String diskReserved;
  private String autoDumpOnET;
  private String autoDumpThreshold;
  private String powerDownDelay;
  private String diskWrap;
  private String diskPower;
  private String terminatorPower;
  private String diskRetry;
  private String wakeupStart;
  private String wakeupDuration;
  private String wakeupRepeat;
  private String wakeupNumberOfIntervals;
  private String implementTime;
  private final StringBuilder tmpsb = new StringBuilder(100);

  public StringBuilder toStringBuilder(StringBuilder tmp) {
    StringBuilder sb = tmp;
    if (sb == null) {
      sb = Util.clear(tmpsb);
    }
    sb.append("power state=").append(powerState.trim()).append(" recMode=").append(recordingMode).
            append(" autoET=").append(autoDumpOnET).append(" autoThres=").append(autoDumpThreshold).
            append(" powerDelay=").append(powerDownDelay.trim()).append(" wrap=").append(diskWrap.trim()).
            append(" diskpower=").append(diskPower.trim()).append(" termPower=").
            append(terminatorPower.trim()).append(" retry=").append(diskRetry).
            append(" wakeup: start=").append(wakeupStart.trim()).append(" dur=").append(wakeupDuration.trim()).
            append(" repeat=").append(wakeupRepeat.trim()).append(" #Ints=").append(wakeupNumberOfIntervals.trim()).
            append(" impTime=").append(implementTime.trim());
    return sb;
  }

  public OperatingMode() {

  }
  /** Load an OM record from a byte buffer
   * 
   * @param bb The byte buffer to load from.
   */
  public void load(ByteBuffer bb) {
    byte[] scratch = new byte[484];
    bb.get(scratch, 0, 2);
    powerState = new String(scratch, 0, 2);
    bb.get(scratch, 0, 2);
    recordingMode = new String(scratch, 0, 2);
    bb.get(scratch, 0, 4);
    diskReserved = new String(scratch, 0, 4);
    bb.get(scratch, 0, 2);
    autoDumpOnET = new String(scratch, 0, 1);
    bb.get(scratch, 0, 2);
    autoDumpThreshold = new String(scratch, 0, 2);
    bb.get(scratch, 0, 4);
    powerDownDelay = new String(scratch, 0, 4);
    bb.get(scratch, 0, 2);
    diskWrap = new String(scratch, 0, 1);
    bb.get(scratch, 0, 1);
    diskPower = new String(scratch, 0, 1);
    bb.get(scratch, 0, 1);
    terminatorPower = new String(scratch, 0, 1);
    bb.get(scratch, 0, 14);
    diskRetry = new String(scratch, 0, 1);
    bb.get(scratch, 0, 12);
    wakeupStart = new String(scratch, 0, 12);
    bb.get(scratch, 0, 6);
    wakeupDuration = new String(scratch, 0, 6);
    bb.get(scratch, 0, 6);
    wakeupRepeat = new String(scratch, 0, 6);
    bb.get(scratch, 0, 2);
    wakeupNumberOfIntervals = new String(scratch, 0, 2);
    bb.get(scratch, 0, 484);
    bb.get(scratch, 0, 448);
    bb.get(scratch, 0, 16);
    implementTime = new String(scratch, 0, 16);
  }
}
