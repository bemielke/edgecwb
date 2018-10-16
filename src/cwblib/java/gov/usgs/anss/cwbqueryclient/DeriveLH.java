/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.cwbqueryclient;

import java.util.ArrayList;
import java.util.GregorianCalendar;
import java.util.Calendar;
import gov.usgs.anss.seed.MiniSeed;
import gov.usgs.anss.edge.IllegalSeednameException;
import gov.usgs.anss.edge.MiniSeedOutputHandler;
import gov.usgs.anss.filter.LHOutputer;
import gov.usgs.anss.filter.LHDerivedProcess;
import gov.usgs.anss.edge.RawToMiniSeed;
import gov.usgs.anss.util.Util;

public class DeriveLH implements LHOutputer, MiniSeedOutputHandler {

  private LHDerivedProcess lh;
  private ArrayList<MiniSeed> outblks;
  private final GregorianCalendar start = new GregorianCalendar();
  private RawToMiniSeed rtms;
  private int ioClock, activity, timingQual, quality;
  private boolean dbg = false;

  public DeriveLH() {
  }

  /**
   * process and ArrayList of miniseed blocks to the same list with data as LH
   *
   * @param blks The Input array list of miniseed (ala CWBQuery)
   * @return The output ArrayList of miniseed
   */
  public ArrayList<MiniSeed> deriveLH(ArrayList<MiniSeed> blks) {
    MiniSeed ms = null;
    for (MiniSeed blk : blks) {
      ms = blk;
      try {
        lh = new LHDerivedProcess((int) (ms.getRate() + 0.0001), ms.getSeedNameString(), null);
        break;
      } catch (RuntimeException e) {
        //Util.prt("Bad block to build LHDerivedProcess (bad rate?) ms="+ms);
      }
    }
    if (lh == null) {
      throw new RuntimeException("Cannot derive LH data - probably bad data rate");
    }
    lh.setOutputer(this);
    start.setTimeInMillis(ms.getTimeInMillis());
    long msday = ms.getTimeInMillis() % 86400000L;
    int secs = (int) (msday / 1000);
    int micros = (int) ((msday % 1000) * 1000);
    rtms = new RawToMiniSeed(ms.getSeedNameSB(), 1., 7, start.get(Calendar.YEAR),
            start.get(Calendar.DAY_OF_YEAR), secs, micros, 1, null);
    RawToMiniSeed.setDebugChannel("IIAAKZ BHZ00");
    rtms.setOutputHandler(this);
    outblks = new ArrayList<>(Math.min(blks.size() / 20, 10));
    for (MiniSeed blk : blks) {
      ioClock = blk.getIOClockFlags();
      activity = blk.getActivityFlags();
      timingQual = blk.getTimingQuality();
      quality = blk.getDataQualityFlags();
      if (dbg) {
        Util.prt("DLH: Process ms=" + blk.toString().substring(0, 110));
      }
      lh.processToLH(blk);
    }
    lh.forceout();
    rtms.forceOut();
    return outblks;
  }
  /**
   * this is required by the LHOutputer interface
   *
   * @param time Time as a millisecond
   * @param chan Channel name
   * @param data The data array to send on
   * @param nsamp The number of samples in data
   */
  private final GregorianCalendar g = new GregorianCalendar();

  /**
   * This is called by the LHDerived process for each chunk of in-the clear data This processes it
   * back to MiniSeed blocks
   *
   * @param time A millisecond epochal time
   * @param chan The channel NSCL
   * @param data Some data in a array
   * @param nsamp The number of samples.
   */
  @Override
  public void sendOutput(long time, StringBuilder chan, int[] data, int nsamp) {
    if (nsamp <= 0) {
      return;
    }
    int msday = (int) (time % 86400000L);
    int secs = msday / 1000;
    int micros = (msday % 1000) * 1000;
    g.setTimeInMillis(time);
    if (dbg) {
      Util.prt("DLH : RTMS " + secs + "." + micros + " ns=" + nsamp + " start=" + Util.ascdate(start) + " " + Util.asctime(start) + " g=" + Util.ascdate(g) + " " + Util.asctime2(g));
    }
    rtms.process(data, nsamp, g.get(Calendar.YEAR), g.get(Calendar.DAY_OF_YEAR),
            secs, micros, activity, ioClock, quality, timingQual, 0);
  }

  /**
   * this is required by the MiniSeedOutputHander interface
   *
   * @param b byte array with the miniseed packet
   * @param len Length of the packet
   */
  @Override
  public void putbuf(byte[] b, int len) {
    try {
      if (b[15] == 'B') {
        b[15] = 'C';
      }
      if (b[15] == 'H') {
        b[15] = 'I';
      }
      MiniSeed ms = new MiniSeed(b, 0, len);
      if (dbg) {
        Util.prt("DLH: putbuf() " + ms);
      }
      outblks.add(ms);
    } catch (IllegalSeednameException e) {
      Util.prt("Impossible - the seedname is illegal" + e);
    }
  }

  /**
   * this is required by the MiniSeedOutputHander interface
   *
   */
  @Override
  public void close() {   // called by MiniSeedHandler

  }
}
