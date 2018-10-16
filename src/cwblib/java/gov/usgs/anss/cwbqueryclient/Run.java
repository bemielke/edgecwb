/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.cwbqueryclient;

import java.util.ArrayList;
import java.util.Collections;
import java.util.GregorianCalendar;
import gov.usgs.anss.seed.*;
import gov.usgs.anss.util.*;

/**
 * This class creates a list of contiguous blocks. A block can be added to it and will be rejected
 * if it is not contiguous at the end. The user just attempts to add the next data block in time to
 * each of the known runs, and creates a new run with the block when none of the existing ones
 * accepts it.
 */
public class Run implements Comparable<Run> {

  private final ArrayList<MiniSeed> blks = new ArrayList<>(1000);     // List of sequenctial contiuous Mini-seed blocks
  private GregorianCalendar start = new GregorianCalendar();      // start time of this run
  private GregorianCalendar end = new GregorianCalendar();        // current ending time of this run (expected time of next block)
  private final String seedname;
  private final double rate;
  private int nweird;

  /**
   * return the seedname for the run
   *
   * @return The seedname
   */
  public String getSeedname() {
    return seedname;
  }

  /**
   * return the data rate for this run
   *
   * @return the rate in hz
   */
  public double getRate() {
    return rate;
  }

  /**
   * return the start time of the run
   *
   * @return the start time as GregorianCalendar
   */
  public GregorianCalendar getStart() {
    return start;
  }

  /**
   * return the end time of the run (Actually the time of the next expected sample)
   *
   * @return the end time as GregorianCalendar
   */
  public GregorianCalendar getEnd() {
    return end;
  }

  /**
   * return duration of run in seconds
   *
   * @return The duration of run in seconds
   */
  public double getLength() {
    return (end.getTimeInMillis() - start.getTimeInMillis()) / 1000.;
  }

  /**
   * string representation
   *
   * @return a String representation of this run
   */
  @Override
  public String toString() {
    return "Run from " + Util.ascdate(start) + " " + Util.asctime2(start) + " to "
            + Util.ascdate(end) + " " + Util.asctime2(end) + " " + getLength() + " s #blks=" + blks.size();
  }

  /**
   * return the ith miniseed block
   *
   * @param i
   * @return the Miniseed block
   */
  public MiniSeed getMS(int i) {
    return blks.get(i);
  }

  /**
   * return length of miniseed list for this run
   *
   * @return The length of the miniseed list for this run
   */
  public int getNBlocks() {
    return blks.size();
  }

  /**
   * clear the list (used mainly to free up associated memory)
   */
  public void clear() {
    blks.clear();
    start = null;
    end = null;
  }

  /**
   * implement Comparable
   *
   * @param r the Run to compare this to
   * @return -1 if <, 0 if =, 1 if >than
   */
  @Override
  public int compareTo(Run r) {
    if (r == null) {
      return -1;
    }
    if (!seedname.equals(r.getSeedname())) {
      return seedname.compareTo(r.getSeedname());
    }
    return start.compareTo(((Run) r).getStart());
  }

  /**
   * create a new run with the given miniseed as initial block
   *
   * @param ms The miniseed block to first include
   */
  public Run(MiniSeed ms) {
    blks.add(ms);
    end.setTimeInMillis(end.getTimeInMillis() + ((long) (ms.getNsamp() / ms.getRate() * 1000 + 0.49)));
    seedname = ms.getSeedNameString();
    rate = ms.getRate();
  }

  /**
   * see if this miniseed block will add contiguously to the end of this run
   *
   * @param ms Miniseed block to add
   * @return true, if block was contiguous and was added to this run, false otherwise
   */
  public boolean add(MiniSeed ms) {
    if (!ms.getSeedNameString().equals(seedname)) {
      return false;
    }
    if (ms.getRate() == 0.) {
      return true;// probably a trigger packet!
    }
    if (rate > 0. && ms.getRate() > 0. && nweird % 1000 == 0) {
      if (Math.abs(rate - ms.getRate()) / rate > 0.01) {
        Util.prt("Run diff rates! " + seedname + " nw=" + (nweird++)
                + " exp=" + rate + " ms=" + ms.toString());
      }
    }
    // Is the beginning of this one near the end of the last one!
    if (Math.abs(ms.getGregorianCalendar().getTimeInMillis() - end.getTimeInMillis())
            < 500. / ms.getRate()) {
      // add this block to the list
      blks.add(ms);
      end = ms.getGregorianCalendar();
      end.setTimeInMillis(end.getTimeInMillis()
              + ((long) (ms.getNsamp() / ms.getRate() * 1000 + 0.49)));
      return true;
    } else if (Math.abs(ms.getNextExpectedTimeInMillis() - start.getTimeInMillis())
            < 500. / ms.getRate()) {
      blks.add(ms);
      Collections.sort(blks);
      start = ms.getGregorianCalendar();
      return true;
    } else {
      return false;
    }
  }

}
