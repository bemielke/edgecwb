/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.edgeoutput;

import java.util.GregorianCalendar;
import gov.usgs.anss.util.*;
import gov.usgs.anss.edgethread.EdgeThread;

/**
 * this class is used by TraceBufListener to report out-of-order a gaps in trace data.
 *
 * @author davidketchum
 */
public class Chan {

  private long latency;
  private long desired;
  private final StringBuilder seedname = new StringBuilder(12);
  private long maxlatency;
  private long totallatency;
  private long npacketlatency;
  private int oor = 0;
  private int npackets;
  private int ngap = 0;
  private final EdgeThread par;
  private int module;
  private int institution;
  private int msover2;
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
      sb.append(seedname).append(" ").append(institution).append("-").append(module).
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

  public Chan(StringBuilder s, TraceBuf tb, EdgeThread parent) {
    par = parent;
    Util.clear(seedname).append(s);
    msover2 = 10;
    desired = tb.getNextExpectedTimeInMillis();
    latency = System.currentTimeMillis() - desired;
    if (tb.getRate() > 0.) {
      msover2 = (int) (500. / tb.getRate());
    }
    maxlatency = 0;
    oor = 0;
    npackets = 1;
    institution = tb.getBuf()[0];
    module = tb.getBuf()[2];
    module = module & 255;
    institution = institution & 255;

  }
  /**
   * process a trace buf, if it overlaps or is prior return true to indicate skip is needed if data
   * is to remain strictly in increasing order.
   *
   * @param tb The trace buf to process
   * @return if true, this packet should be skipped to keep things strictly in increasing order
   */
  GregorianCalendar d = new GregorianCalendar();
  GregorianCalendar got = new GregorianCalendar();

  public synchronized boolean process(TraceBuf tb) {
    if (!Util.stringBuilderEqual(tb.getSeedNameSB(), seedname)) {
      par.prt(Util.clear(tmpsb).append("   ***** Chan got wrong channel ").append(seedname).append(" ").append(tb.toString()));
      return true;
    }
    latency = System.currentTimeMillis() - tb.getNextExpectedTimeInMillis();
    totallatency += latency;
    if (latency > maxlatency) {
      maxlatency = latency;
    }
    npackets++;
    npacketlatency++;
    if (tb.getTimeInMillis() - desired < -2 * msover2) {
      oor++;
      d.setTimeInMillis(desired);
      got.setTimeInMillis(tb.getTimeInMillis());
      Util.clear(tmpsb).append("    **** Data out of order ").append(seedname).append(seedname).append("|").append(tb.getSeedNameSB()).
              append(" ms=").append(Util.rightPad("" + (tb.getTimeInMillis() - desired), 8)).
              append(" expect=").append(Util.asctime2(d)).append(" got=").
              append(Util.asctime2(got)).append(" ");
      tb.toStringBuilder(tmpsb);
      par.prt(tmpsb);
      return true;
    }
    if (tb.getTimeInMillis() - desired > msover2) {
      d.setTimeInMillis(desired);
      got.setTimeInMillis(tb.getTimeInMillis());
      par.prt(Util.clear(tmpsb).append("    **** Gap found ").append(seedname).append("|").append(tb.getSeedNameSB()).
              append(" ms=").append(Util.rightPad("" + (tb.getTimeInMillis() - desired), 8)).
              append(" expect=").append(Util.asctime2(d)).append(" got=").append(Util.asctime2(got)));
      ngap++;
    }
    desired = tb.getNextExpectedTimeInMillis();
    return false;
  }
}
