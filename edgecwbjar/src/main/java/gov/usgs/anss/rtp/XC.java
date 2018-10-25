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
import java.text.DecimalFormat;

/**
 * This class decodes the XC (External Clock) type packets and store the
 * results.
 *
 * @author davidketchum
 */
public final class XC {

  // These fields come from XC status messages
  private String time;
  private long timeInMillis;
  private String lastLock;
  private int lastLockSecs = -1;
  private String lastLockPhase;
  private int lastLockPhaseErrorUsec;  // this would correct time code to UTC if applied
  private String lockStatus;
  private String nsats;
  private double latitude;
  private double longitude;
  private double elevation;
  private String gpsOn;
  private String gpsMode;
  private final byte[] scratch = new byte[18];
  private final DecimalFormat df6 = new DecimalFormat("0.00000");
  private final StringBuilder snw = new StringBuilder(300);
  private final StringBuilder tmpsb = new StringBuilder(100);

  public int getLastLockSecs() {
    return lastLockSecs;
  }

  public int getLastLockPhaseErrorUsec() {
    return lastLockPhaseErrorUsec;
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

  public int getNsats() {
    return getPLLBits();
  }

  /**
   * return lock status
   *
   * @return U if unlocked L if locked
   */
  public String getLockStatus() {
    return lockStatus;
  }

  public String getGPSOn() {
    return gpsOn;
  }

  /**
   * return the Reftek GPS mode
   *
   * @return C is continuous on, D is duty cycle on, O is off
   */
  public String getGPSMode() {
    return gpsMode;
  }

  @Override
  public String toString() {
    return toStringBuilder(null).toString();
  }

  public StringBuilder toStringBuilder(StringBuilder tmp) {
    StringBuilder sb = tmp;
    if (sb == null) {
      sb = Util.clear(tmpsb);
    }
    sb.append("XC:").append(time).append(" GPS: last=").append(lastLockSecs).
            append("s lastPH=").append(lastLockPhase).append(" ").append(lastLockPhaseErrorUsec).
            append("uS stat=").append(lockStatus).append(" #sat=").append(nsats).
            append(" on=").append(gpsOn).append(" mode=").append(gpsMode).
            append(" lat=").append(df6.format(latitude)).append(" lon=").
            append(df6.format(longitude)).append(" elev=").append(elevation);
    return sb;
  }

  /**
   * There is no PLL bits so number of satellites is returned
   *
   * @return Usecs of phase error limited to 127
   */
  public int getPLLBits() {
    int ans = 0;
    if (nsats == null) return ans;
    for (int i = 0; i < nsats.length(); i++) {
      if (Character.isDigit(nsats.charAt(i))) {
        ans = ans * 10 + (nsats.charAt(i) - 48);
      }
    }
    return ans;

  }

  /**
   * There is no PLL bits so we put phase loc usec error here
   *
   * @return Usecs of phase error limited to +/- 32765
   */
  public int getTimeBase() {
    return Math.max(Math.min(32765, lastLockPhaseErrorUsec), -32765);

  }

  /**
   * the PLL Int for a Reftek is seconds since last lock with a 16 bit ceiling
   *
   * @return The seconds since last lock limited to 32765
   */
  public int getPLLInt() {
    return Math.min(32765, lastLockSecs);
  }

  /**
   * gps bits for a reftek are : 1=GPS is on, 2=GPS continuous, 4=GPS Duty
   * cycle, 8=Phase error >1 MS
   *
   * @return The gps bits
   */
  public int getGPSBits() {
    int b = 0;
    if(gpsOn == null) return b;
    if (gpsOn.contains("Y")) {
      b |= 1;
    }
    if (gpsMode.equals("C")) {
      b |= 2;
    }
    if (gpsMode.equals("D")) {
      b |= 4;
    }
    if (lastLockPhaseErrorUsec > 999) {
      b |= 8;    // Millisecond lock or greater
    }
    return b;
  }

  public int timeQuality() {
    return Math.max(0, 100 - lastLockSecs / 1000);
  }

  public StringBuilder getSNWSenderString() {
    if (snw.length() > 0) {
      snw.delete(0, snw.length());
    }
    snw.append(":7:" + "Reftek GPS Status=").append(lockStatus).append(";Reftek GPS Mode=").
            append(gpsMode).append(";Reftek GPS on=").append(gpsOn).
            append(";Reftek GPS Secs Since Last Lock=").append(lastLockSecs).
            append(";Reftek GPS Satellites Used=").append(nsats).
            append(";Reftek GPS Last Phase Error(us)=").append(lastLockPhaseErrorUsec).
            append(";Reftek GPS Clock Status observed at=").append(time);
    return snw;
  }

  public void load(ByteBuffer bb) {
    bb.get(scratch, 0, 18);
    time = new String(scratch, 0, 18).trim();
    timeInMillis = Reftek.getTime(time);
    try {
      int pos = bb.position();
      bb.get(scratch, 0, 8);
      lastLock = new String(scratch, 0, 8);
      
      // This new code is for XC messages with lastLock > 99 days.  
      int firstColon = lastLock.indexOf(":");
      if(firstColon > 2) {
        lastLock = lastLock + "0";
      }
      lastLockSecs = Integer.parseInt(lastLock.substring(0, firstColon)) * 86400
              + Integer.parseInt(lastLock.substring(firstColon + 1, firstColon+3)) * 3600 + Integer.parseInt(lastLock.substring(firstColon+4));
      //Util.prt("XC: lastLoc="+lastLock+" lastLockSecs="+lastLockSecs);
      //lastLockSecs = Integer.parseInt(lastLock.substring(0, 2)) * 86400
      //        + Integer.parseInt(lastLock.substring(3, 5)) * 3600 + Integer.parseInt(lastLock.substring(6, 8));
      bb.get(scratch, 0, 11);
      lastLockPhase = new String(scratch, 0, 11);
      lastLockPhaseErrorUsec = Integer.parseInt(lastLockPhase.substring(1, 3)) * 1000000
              + Integer.parseInt(lastLockPhase.substring(4, 7)) * 1000 + Integer.parseInt(lastLockPhase.substring(8, 11));
      if (lastLockPhase.substring(0, 1).equals("-")) {
        lastLockPhaseErrorUsec = -lastLockPhaseErrorUsec;
      }
    }
    catch(RuntimeException e) {
      Util.prta("XC Error parsing : *** phaseLockstr="+lastLockPhase+" lastLock="+lastLock);
      throw e;
    }
    lockStatus = "" + (char) bb.get();
    bb.get(scratch, 0, 2);      // net # sats
    nsats = new String(scratch, 0, 2).trim();
    bb.get(scratch, 0, 12);
    String slat = new String(scratch, 0, 12);
    latitude = Integer.parseInt(slat.substring(1, 4).trim()) + Double.parseDouble(slat.substring(5, 12)) / 60.;
    if (slat.substring(0, 1).equals("S")) {
      latitude = -latitude;
    }
    bb.get(scratch, 0, 12);
    slat = new String(scratch, 0, 12);
    longitude = Integer.parseInt(slat.substring(1, 4).trim()) + Double.parseDouble(slat.substring(5, 12)) / 60.;
    if (slat.substring(0, 1).equals("W")) {
      longitude = -longitude;
    }
    bb.get(scratch, 0, 6);
    elevation = Integer.parseInt(new String(scratch, 0, 6).trim().replaceAll("\\+", ""));
    gpsOn = "" + (char) bb.get();
    gpsMode = "" + (char) bb.get();

  }

  public long getTimeInMillis() {
    return timeInMillis;
  }
}
