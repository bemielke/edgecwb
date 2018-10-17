/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.cwbqueryclient;

import gov.usgs.anss.edge.IllegalSeednameException;
import gov.usgs.anss.edge.MiniSeedOutputHandler;
import gov.usgs.anss.edge.RawToMiniSeed;
import gov.usgs.anss.filter.*;
import gov.usgs.anss.seed.MiniSeed;
import gov.usgs.anss.seed.Steim1;
import gov.usgs.anss.seed.Steim2Object;
import gov.usgs.anss.seed.SteimException;
import gov.usgs.anss.edge.RawDisk;
import gov.usgs.anss.util.Util;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.GregorianCalendar;
import java.io.IOException;

/**
 * This class can take a list of MiniSeed blocks and filter and decimate them to a new list of
 * MiniSeed blocks.
 *
 * @author U.S. Geological Survey ketchum at usgs.gov
 */
public class DeriveAny implements MiniSeedOutputHandler {

  private final Steim2Object steim2 = new Steim2Object();
  private ArrayList<MiniSeed> outblks;
  private final boolean dbg = false;
  private final byte[] frames = new byte[4096];
  private final int[] out = new int[10240];
  private final int[] old = new int[10240];
  private char bandCode;

  public DeriveAny() {

  }

  /**
   * process and ArrayList of miniseed blocks to the same list with data as LH
   *
   * @param blks The Input array list of miniseed (ala CWBQuery)
   * @param outputRate
   * @return The output ArrayList of miniseed
   */
  public ArrayList<MiniSeed> deriveFromMS(ArrayList<MiniSeed> blks, int outputRate) {
    Collections.sort(blks);
    boolean decodeClean;
    RawToMiniSeed rtms = null;
    int ioClock, activity, timingQual, quality;
    long expectedTime = 0;
    int inputRate = 0;
    GregorianCalendar g = new GregorianCalendar();
    GregorianCalendar start = new GregorianCalendar();
    for (MiniSeed blk : blks) {
      if (blk.getRate() > 0 && blk.getNsamp() > 0) {
        inputRate = (int) (blk.getRate() + 0.001);
        break;
      }
    }
    if (inputRate % outputRate != 0) {
      int rate = inputRate;
      while (rate > outputRate) {
        rate = rate / 2;
      }
      rate = rate * 2;
      if (rate == inputRate) {
        rate = inputRate / 2;
      }
      Util.prt(" **** Cannot derive " + outputRate + " Hz data from " + inputRate + " Hz data! set output rate to " + rate);
      outputRate = rate;
    }
    if (outputRate >= 10 && outputRate < 80) {
      bandCode = 'B';
    }
    if (outputRate > 1 && outputRate < 10) {
      bandCode = 'M';
    }
    if (outputRate >= 80) {
      bandCode = 'H';
    }
    if (outputRate <= 1) {
      bandCode = 'L';
    }
    FilterDecimate filter = new FilterDecimate(inputRate, outputRate, false);
    int skip = filter.getDelayMS() * outputRate / 500 + 1;  // The delay * 2 * output rate samples of output delay

    int msover2 = 1000 / inputRate / 2;
    int decimation = inputRate / outputRate;
    if (outblks == null) {
      outblks = new ArrayList<>(Math.min(blks.size() / decimation, 10));
    } else {
      outblks.clear();
    }
    int nold = 0;
    for (MiniSeed ms : blks) {
      ioClock = ms.getIOClockFlags();
      activity = ms.getActivityFlags();
      timingQual = ms.getTimingQuality();
      quality = ms.getDataQualityFlags();
      start.setTimeInMillis(ms.getTimeInMillis());
      if (ms.getRate() <= 0 || ms.getNsamp() <= 0) {
        continue;
      }
      // If this is the first block, set the expected time to agree
      if (expectedTime <= 0) {
        expectedTime = start.getTimeInMillis();
      }
      // Decode the data
      try {
        int[] samples = null;
        int reverse = 0;
        System.arraycopy(ms.getBuf(), ms.getDataOffset(), frames, 0, ms.getBlockSize() - ms.getDataOffset());
        if (ms.getEncoding() == 10) {
          samples = Steim1.decode(frames, ms.getNsamp(), ms.isSwapBytes(), reverse);
        }
        if (ms.getEncoding() == 11) {
          synchronized (steim2) {
            boolean ok = steim2.decode(frames, ms.getNsamp(), ms.isSwapBytes(), reverse);
            if (ok) {
              samples = steim2.getSamples();
            }
            if (steim2.hadReverseError()) {
              decodeClean = false;
              Util.prt("ZeroFilledSpan: decode block had reverse integration error" + ms);
            }
            if (steim2.hadSampleCountError()) {
              decodeClean = false;
              Util.prt("ZeroFilledSpan: decode block had sample count error " + ms);
            }
          }
        }
        // check for continuity
        if (Math.abs(expectedTime - start.getTimeInMillis()) < msover2 - 1) {  // it is continuous, copy date to any leftovers
          System.arraycopy(samples, 0, old, nold, ms.getNsamp());
        } else {  // Discontinuity - set filter warmup and copy to beginning
          //if(dbg)
          Util.prt("Discontinuity " + (expectedTime - start.getTimeInMillis()) + " " + Util.asctime2(expectedTime) + " " + Util.asctime2(start.getTimeInMillis()));
          skip = filter.getDelayMS() * outputRate / 500 + 1;      // The delay * 2 * output rate samples of output delay
          System.arraycopy(samples, 0, old, 0, ms.getNsamp());
        }
        int nout = 0;
        // set time to first sample, (time of last sample of input data, - group delay - any old data added on
        g.setTimeInMillis(start.getTimeInMillis() + 1000 / inputRate * decimation - filter.getDelayMS() - nold * 1000 / inputRate);
        int j = 0;
        while (j + decimation <= nold + ms.getNsamp()) {
          out[nout] = filter.decimate(old, j);
          //if(nout >= 1) if(Math.abs(out[nout-1] - out[nout]) > 450 && g.get(Calendar.HOUR_OF_DAY) == 21 && g.get(Calendar.MINUTE) >35) 
          //  Util.prt("big diff "+Util.asctime(g));
          if (skip-- < 0) {
            nout++;
          } else {
            g.setTimeInMillis(g.getTimeInMillis() + 1000 / outputRate);
          }
          j += decimation;
        }
        nold = (nold + ms.getNsamp() - j);
        if (nold > 0) {
          System.arraycopy(old, j, old, 0, nold);
        }
        long msday = g.getTimeInMillis() % 86400000L;
        int secs = (int) (msday / 1000);
        int micros = (int) ((msday % 1000) * 1000);
        if (rtms == null) {
          rtms = new RawToMiniSeed(ms.getSeedNameSB(), outputRate, 7, g.get(Calendar.YEAR),
                  g.get(Calendar.DAY_OF_YEAR), secs, micros, 1, null);
          RawToMiniSeed.setDebugChannel("IIAAKZ BHZ00");
          rtms.setOutputHandler(this);
        }
        if (nout > 0) {
          rtms.process(out, nout, g.get(Calendar.YEAR), g.get(Calendar.DAY_OF_YEAR),
                  secs, micros, activity, ioClock, quality, timingQual, 0);
        }
        expectedTime = ms.getNextExpectedTimeInMillis();
      } catch (SteimException e) {
        Util.prt("block gave steim decode error. " + e.getMessage() + " " + ms);
        decodeClean = false;
      }
    } // End of for each block!
    return outblks;
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
      if (b[15] == bandCode) {
        b[14] = (byte) (b[14] + 5);
      }
      b[15] = (byte) bandCode;

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

  public static void main(String[] args) {
    Util.setModeGMT();
    DecimalFormat df2 = new DecimalFormat("00");
    GregorianCalendar time = new GregorianCalendar(2013, 03, 29, 0, 0, 0); // remember 03 is April in GregorianCalendars
    time.setTimeInMillis(time.getTimeInMillis() / 1000 * 1000);
    // Make up a string for this same time to get the whole day using a normal query
    String s = "-h localhost -s NEBRYW-HHZ00 -t null -d 3600 -q -b " + time.get(Calendar.YEAR) + "," + time.get(Calendar.DAY_OF_YEAR) + "-"
            + df2.format(time.get(Calendar.HOUR_OF_DAY)) + ":"
            + df2.format(time.get(Calendar.MINUTE)) + ":"
            + df2.format(time.get(Calendar.SECOND));

    // Get the MiniSeed for the data from the query server
    ArrayList<ArrayList<MiniSeed>> mslist = EdgeQueryClient.query(s);
    DeriveAny any = new DeriveAny();
    ArrayList<MiniSeed> out = any.deriveFromMS(mslist.get(0), 40);
    try {
      RawDisk rw = new RawDisk(out.get(0).getSeedNameString().replaceAll(" ", "_") + ".msf", "rw");
      rw.setLength(0);
      for (int i = 0; i < out.size(); i++) {
        rw.writeBlock(i, out.get(i).getBuf(), 0, out.get(i).getBlockSize());
      }
      rw.close();
      rw = new RawDisk(mslist.get(0).get(0).getSeedNameString().replaceAll(" ", "_") + ".ms", "rw");
      rw.setLength(0);
      for (int i = 0; i < mslist.get(0).size(); i++) {
        rw.writeBlock(i, mslist.get(0).get(i).getBuf(), 0, mslist.get(0).get(i).getBlockSize());
      }
      rw.close();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }
}
