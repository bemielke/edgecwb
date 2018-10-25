/*
 *  This software is in the public domain because it contains materials 
 *  that originally came from the United States Geological Survey, 
 *  an agency of the United States Department of Interior. For more 
 *  information, see the official USGS copyright policy at 
 *  http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.edgemom;

import gov.usgs.anss.util.Util;

/**
 * this class is used by GapFromInorderMaker to process the data and create gap
 * entries. It is called statically via method process() which will create one
 * object of this type for every new channel. This class process() method checks
 * for gaps and overlaps and keeps statistics on same.
 *
 * @author davidketchum
 */
public class GapsFromInorderChannel {

  private long latency;
  private long desired;
  private final StringBuilder seedname = new StringBuilder(12);
  private long maxlatency;
  private long totallatency;
  private long npacketlatency;
  private double rate;
  private int oor = 0;
  private int npackets;
  private int ngap = 0;
  private final GapsFromInorderMaker par;
  private int msover2;
  private String gapType;
  private final StringBuilder tmpsb = new StringBuilder(100);

  public long getLatency() {
    return latency;
  }

  public long getDesired() {
    return desired;
  }

  public int getOOR() {
    return oor;
  }

  public int getNgap() {
    return ngap;
  }

  public int getNpackets() {
    return npackets;
  }

  public long getMaxLatency() {
    return maxlatency;
  }

  public String getGapType() {
    return gapType;
  }

  public double getRate() {
    return rate;
  }

  public void setGapType(String gp) {
    gapType = gp;
  }

  public StringBuilder getSeedname() {
    return seedname;
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
    synchronized (sb) {
      sb.append(seedname).append(" ").
              append(" latency=").append(Util.rightPad("" + latency, 8)).
              append(" avglat=");
      if (npacketlatency > 0) {
        sb.append(totallatency / npacketlatency).append(" maxLatency=").
                append(Util.rightPad("" + maxlatency, 8)).append(" oor=").append(Util.rightPad("" + oor, 4)).
                append(" #gap=").append(ngap).append(" npkt=").append(Util.rightPad("" + npackets, 6));
      }
      if (oor > 0) {
        sb.append("   ******");
      }
    }
    return sb;
  }

  public void clearStatistics() {
    totallatency = 0;
    npacketlatency = 0;
  }

  /**
   * Construct a object. Normally this is done by static calls to process, but
   * it could be done from EdgeMom as well for more control
   *
   * @param s SB with NSCL
   * @param time Time in millis of first sample
   * @param nsamp Number of samples
   * @param rate Date rate in Hz
   * @param gaptype If not null, the gap type to insert into the database
   * @param parent The parent which will be used to create the gaps sharing that
   * infrastructure.
   */
  public GapsFromInorderChannel(StringBuilder s, long time, int nsamp, double rate, String gaptype, GapsFromInorderMaker parent) {
    par = parent;
    Util.clear(seedname).append(s);
    msover2 = 10;
    desired = time + (long) (nsamp / rate * 1000. + 0.5);
    latency = System.currentTimeMillis() - desired;
    if (rate > 0.) {
      msover2 = (int) (500. / rate);
    }
    gapType = gaptype;
    maxlatency = 0;
    oor = 0;
    npackets = 1;
    this.rate = rate;
  }

  /**
   * process a trace buf, if it overlaps or is prior return true to indicate
   * skip is needed if data is to remain strictly in increasing order.
   *
   * @param seed The SB with a 12 character NSCL
   * @param time The time in millis
   * @param nsamp The number of samples in this packet
   * @param rate The data rate in Hz
   *
   * @return if true, this packet should be skipped to keep things strictly in
   * increasing order
   */
  //GregorianCalendar d = new GregorianCalendar();
  //GregorianCalendar got = new GregorianCalendar();
  public synchronized boolean process(StringBuilder seed, long time, int nsamp, double rate) {
    if (!Util.stringBuilderEqual(seed, seedname)) {
      par.prt(Util.clear(tmpsb).append("   ***** Chan got wrong channel ").append(seedname).append(" ").append(seed));
      return true;
    }
    if (this.rate == 0.) {
      this.rate = rate;
    }
    long nextExpected = time + (long) (nsamp / rate * 1000 + 0.5);
    latency = System.currentTimeMillis() - nextExpected;
    totallatency += latency;
    if (latency > maxlatency) {
      maxlatency = latency;
    }
    npackets++;
    npacketlatency++;
    // Is there are reversion?
    if (time - desired < -2 * msover2) {
      oor++;
      Util.clear(tmpsb).append("    **** Data out of order ").append(seedname).
              append(" ms=").append(Util.rightPad("" + (time - desired), 8)).
              append(" expect=").append(Util.ascdatetime2(desired)).append(" got=").
              append(Util.ascdatetime2(time)).append(" len=").append(nsamp / rate);
      par.prta(tmpsb);
      return true;
    }
    // Is there a gap.
    if (time - desired > msover2) {
      par.prt(Util.clear(tmpsb).append("    **** Gap found ").append(seedname).
              append(" ms=").append(Util.rightPad("" + (time - desired), 8)).
              append(" expect=").append(Util.asctime2(desired)).append(" got=").append(Util.asctime2(time)));
      ngap++;
      double dur = ((time - msover2 / 2) - (desired - msover2 / 2)) / 1000.;  // just before the time we got - just before the time we expected
      if (gapType != null) {
        par.addGap(seedname, desired - msover2 / 2, dur, gapType);
      }
    }
    desired = nextExpected;
    return false;
  }
}
