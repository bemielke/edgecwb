/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.edge;

import gov.usgs.anss.util.Util;
import java.sql.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.GregorianCalendar;

/**
 * This class keeps a series of runs which may be from different seednames. The starting and ending
 * point of each run is kept. The idea is to simply add data to this class which will add the data
 * to a run if it fits, or create new runs. After all of the runs have been created, the
 * consolidate() method will attempt to eliminate overlapping or adjoining runs into single runs.
 *
 * @author davidketchum
 */
public final class RunList {

  private final ArrayList<Run> runs;
  private final boolean truncate;
  private boolean consolidated;
  private final GregorianCalendar gtmp = new GregorianCalendar();
  private final GregorianCalendar gtmp2 = new GregorianCalendar();
  // For doing availability we need these variables computed by the availability() routine
  private int nruns;
  private long gaps[];
  private int ngaps;

  public boolean getTruncate() {
    return truncate;
  }

  public int size() {
    return runs.size();
  }

  public long getLatest() {
    long last = 0;
    for (Run run : runs) {
      if (run.getEnd() > last) {
        last = run.getEnd();
      }
    }
    return last;
  }

  public long getEarliest() {
    long last = Long.MAX_VALUE;
    for (Run run : runs) {
      if (run.getStart() < last) {
        last = run.getStart();
      }
    }
    return last;
  }

  /**
   * return Number for gaps from last availability call
   *
   * @return The number of gaps
   */
  public int getNGaps() {
    return ngaps;
  }

  /**
   * return the list of gaps in milliseconds = must call availability() first for this to work
   *
   * @return The array of gaps in milliseconds - this array may be larger than ngaps
   */
  public long[] getGaps() {
    return gaps;
  }

  /**
   * return the longest of the gaps in milliseconds = must call availability() first for this to
   * work
   *
   * @return The length of the longest gap in milliseconds - this array may be larger than ngaps
   */

  public synchronized long getLongestGap() {
    long longestGap = Long.MIN_VALUE;
    for (int i = 0; i < ngaps; i++) {
      if (gaps[i] > longestGap) {
        longestGap = gaps[i];
      }
    }
    return longestGap;
  }

  /**
   * Calculate the availability numbers for a particular seedname over a particular interval
   *
   * @param seed The 12 character seedname
   * @param begin The Gregorian time of the earliest time in the interval
   * @param end The Gregorian time of the last time in the interval
   * @return The percentage of the interval the station is present
   */
  public synchronized double availability(String seed, GregorianCalendar begin, GregorianCalendar end) {
    consolidate();
    double pct = 0.;
    nruns = 0;
    gaps = new long[runs.size() + 1];
    ngaps = 0;
    for (int i = 0; i < runs.size(); i++) {
      if (!Util.stringBuilderEqual(runs.get(i).getSeedname(), seed)) {
        continue;           // wrong seedname0
      }
      if (!(runs.get(i).getStart() > end.getTimeInMillis()
              || runs.get(i).getEnd() < begin.getTimeInMillis())) {  // Run is out of bounds
        nruns++;
        if (ngaps == 0) {
          if (begin.getTimeInMillis() - runs.get(i).getStart() < 0) {
            gaps[ngaps++] = runs.get(i).getStart() - begin.getTimeInMillis();
          }
        } else if (i > 0) {
          gaps[ngaps++] = runs.get(i).getStart() - runs.get(i - 1).getEnd();
        }

        pct += (Math.min(end.getTimeInMillis(), runs.get(i).getEnd())
                - Math.max(begin.getTimeInMillis(), runs.get(i).getStart())) / 1000.;
      }
    }
    if (runs.get(runs.size() - 1).getEnd() < end.getTimeInMillis()) {
      gaps[ngaps++] = end.getTimeInMillis() - runs.get(runs.size() - 1).getEnd();
    }
    return pct / ((end.getTimeInMillis() - begin.getTimeInMillis()) / 1000.) * 100.;
  }

  public Run get(int i) {
    return runs.get(i);
  }
  /** 
   * 
   * @param initSize  Initial size of ArrayList of runs
   */
  public RunList(int initSize) {
    truncate = false;
    runs = new ArrayList<>(initSize);
  }
  /**
   * 
   * @param initSize Initial size of ArrayList of runs
   * @param trun If true, truncate mode is on in runs (truncated times from MiniSEED for example)
   */
  public RunList(int initSize, boolean trun) {
    truncate = trun;
    runs = new ArrayList<>(initSize);
  }

  @Override
  public String toString() {
    return toStringFull().toString();
  }
  StringBuilder sb = new StringBuilder(1000);

  public StringBuilder toStringFull() {
    if (sb.length() > 0) {
      sb.delete(0, sb.length());
    }
    for (Run run : runs) {
      run.toStringBuilder(sb).append("\n");
    }
    return sb;
  }

  public synchronized double secsOverlap(long start, double duration) {
    double overlap = 0;
    for (Run run : runs) {
      if (!(run.getStart() > start + duration * 1000 || run.getEnd() < start)) {
        long low = run.getStart();
        if (low < start) {
          low = start;
        }
        long high = run.getEnd();
        if (high > start + (long) (duration * 1000)) {
          high = (long) (start + duration * 1000 + 0.5);
        }
        overlap += (high - low) / 1000.;
      }
    }
    return overlap;
  }

  public ArrayList<Run> getRuns() {
    return runs;
  }

  /**
   * clear all of the runs in this list
   */
  public void clear() {
    runs.clear();
  }

  public synchronized void trim(GregorianCalendar trim) {
    trim(trim.getTimeInMillis());
  }

  public synchronized void trim(long trim) {
    long trimtime = trim;
    for (int i = runs.size() - 1; i >= 0; i--) {
      // If trim time is after end, just remove this one
      if (runs.get(i).getEnd() <= trimtime) {
        runs.remove(i);
      } // if run starts before trim time, reset it
      else if (runs.get(i).getStart() < trimtime) {
        runs.get(i).trim(trim, runs.get(i).getEnd());
      }
    }
  }

  /**
   * attempt to consolidate any overlapping runs.
   *
   * @return The number of overlaps detected
   */
  public synchronized int consolidate() {
    if (consolidated) {
      return 0;
    }
    Collections.sort(runs);
    GregorianCalendar start = new GregorianCalendar();
    int overlaps = 0;
    int lastOverlaps = -1;
    while (overlaps != lastOverlaps) {
      lastOverlaps = overlaps;
      if (runs.size() == 1) {
        return 0;        // Only one run
      }
      for (int i = 0; i < runs.size() - 1; i++) {
        if(runs.get(i).getLength()  <= 0.) continue;
        for (int j = i + 1; j < runs.size(); j++) {
          if(runs.get(j).getLength() <= 0.) continue;
          if (Util.stringBuilderEqual(runs.get(i).getSeedname(), runs.get(j).getSeedname())) {
            // If we can add the jth one to the ith one, then drop the ith one!
            if (runs.get(i).add(runs.get(j).getSeedname(), runs.get(j).getStart(), runs.get(j).getLength())) {
              runs.remove(j);
              j--;      // since we removed one, compensate
              //runs.get(j).reset("            ", runs.get(j).getStart(), 0.);
              overlaps++;     // this consolidated
            }
            if(runs.get(i).getEnd() < runs.get(j).getStart()) {
              break;      // all runs further down start to late to be combined.
            }
          } else {
            break;     // past this seedname, break out of inner loop
          }
        }
      }
      // now delete the ones that are marked
     /* for (int i = runs.size() - 1; i >= 0; i--) {
        if (runs.get(i).getLength() < 0.001) {
          runs.remove(i);
        }
      }*/
    }
    consolidated = true;
    return overlaps;
  }

  public synchronized void add(Run runin) {
    boolean found = false;
    for (Run run : runs) {
      if (run.add(runin.getSeedname(), runin.getStart(), runin.getLength())) {
        found = true;
        break;
      }
    }
    if (!found) {
      runs.add(new Run(runin.getSeedname(), runin.getStart(), runin.getLength()));
      consolidated = false;
    }
  }

  public synchronized void add(TimeSeriesBlock msin) {
    boolean found = false;
    for (Run run : runs) {
      if (run.add(msin)) {
        found = true;
        break;
      }
    }
    if (!found) {
      runs.add(new Run(msin, truncate));
    }
    consolidated = false;

  }

  public synchronized void add(String seed, GregorianCalendar st, double dur) {
    boolean found = false;
    for (Run run : runs) {
      if (run.add(seed, st, dur)) {
        found = true;
        break;
      }
    }
    if (!found) {
      runs.add(new Run(seed, st, dur));
      consolidated = false;
    }
  }

  public synchronized void add(StringBuilder seed, long st, double dur) {
    boolean found = false;
    for (Run run : runs) {
      if (run.add(seed, st, dur)) {
        found = true;
        break;
      }
    }
    if (!found) {
      runs.add(new Run(seed, st, dur));
      consolidated = false;
    }
  }

  public synchronized void add(StringBuilder seed, GregorianCalendar st, double dur) {
    boolean found = false;
    for (Run run : runs) {
      if (run.add(seed, st, dur)) {
        found = true;
        break;
      }
    }
    if (!found) {
      runs.add(new Run(seed, st, dur));
      consolidated = false;
    }
  }

  /**
   * Add run from a holding style result set, Note this does not try to combine on adds call
   * consolidate if you want to eliminate overlaps
   *
   * @param rs The result set positioned to a holding
   * @throws SQLException If one is thrown
   */
  public void addHolding(ResultSet rs) throws SQLException {
    boolean found = false;
    Timestamp start = rs.getTimestamp("start");
    Timestamp end = rs.getTimestamp("ended");
    int end_ms = rs.getShort("end_ms");
    int start_ms = rs.getShort("start_ms");
    gtmp.setTimeInMillis(start.getTime() / 1000 * 1000 + start_ms);
    gtmp2.setTimeInMillis(end.getTime() / 1000 * 1000 + end_ms);
    String seed = rs.getString("seedname");
    if (seed.length() < 12) {
      seed = (seed + "_____").substring(0, 12);
    }
    double dur = (gtmp2.getTimeInMillis() - gtmp.getTimeInMillis()) / 1000.;
    runs.add(new Run(seed, gtmp, dur));
    consolidated = false;
  }
  /** Make up an ArrayList where each entry is a RunList of only one channel.
   * 
   * @return The ArrayList containing one runlist per channel
   */
  public ArrayList<RunList> getRunListByChannel() {
    ArrayList<RunList> ans = new ArrayList<>(3);
    if(runs.isEmpty())  return ans;
    StringBuilder channel = runs.get(0).getSeedname();
    RunList runlist = new RunList(runs.size());
    ans.add(runlist);
    runlist.add(runs.get(0));
    for(int i=1; i<runs.size(); i++) {
      if(Util.stringBuilderEqual(channel, runs.get(i).getSeedname())) {
        runlist.add(runs.get(i));
      }
      else {
        runlist = new RunList(runs.size());
        ans.add(runlist);
        runlist.add(runs.get(i));
      }
    }
    return ans;
  }
}
