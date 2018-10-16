/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.edge;

import java.util.GregorianCalendar;
import gov.usgs.anss.seed.MiniSeed;
import gov.usgs.anss.util.Util;
import gov.usgs.anss.edgethread.EdgeThread;
import java.text.DecimalFormat;

/**
 * This class creates encapsulates a continuous timeseries segment. A block of data can be added to it and will be rejected
 * if it is not contiguous at the end or beginning. The user just attempts to add the next data block in time to
 * each of the known runs, and creates a new run with the block when none of the existing ones
 * accepts it.
 * <p>
 * It is used by the RunList class to simplify tracking a series of runs.
 */
public final class Run implements Comparable<Run> {

  public static DecimalFormat df3 = new DecimalFormat("0.000");
  public static EdgeThread par;
  private long start;      // start time of this run
  private long end;        // current ending time of this run (expected time of next block)
  private final StringBuilder seedname = new StringBuilder(12);
  private final StringBuilder scrsb = new StringBuilder(100);
  private long lastUpdate;              // Time of last put to holding server
  private boolean modified;
  private double rate;
  private boolean truncate;
  private int msover2 = 10;
  private static boolean dbgstat;
  private boolean dbg;

  public double getRate() {
    return rate;
  }

  /**
   * return time since last update a
   *
   * @return The long representing ms since 1970
   */

  public long getLastUpdate() {
    return lastUpdate;
  }

  public boolean isModified() {
    return modified;
  }

  /**
   * set the time since last update to the current time
   */
  public void setLastUpdate() {
    lastUpdate = System.currentTimeMillis();
    modified = false;
  }

  /**
   * return the start time of the run
   *
   * @return the start time as GregorianCalendar
   */
  public long getStart() {
    return start;
  }

  /**
   * return the end time of the run (Actually the time of the next expected sample)
   *
   * @return the end time as GregorianCalendar
   */
  public long getEnd() {
    return end;
  }

  /**
   * return duration of run in seconds
   *
   * @return The duration of run in seconds
   */
  public final double getLength() {
    return (end - start) / 1000.;
  }

  /**
   * Set all runs to debug mode for all one created after this call. Optionally set a logger
   *
   * @param t Whether to debug all newly created Runs
   * @param p If not null, set the logging EdgeThread
   */
  public static void setDebug(boolean t, EdgeThread p) {
    dbgstat = t;
    if (p != null) {
      par = p;
    }
  }

  /**
   * Set this Run to log
   *
   * @param t If true, start logging this run.a
   */
  public void setDebug(boolean t) {
    dbg = t;
  }

  /**
   * string representation
   *
   * @return a String representation of this run
   */
  @Override
  public String toString() {
    return "Run from " + seedname + " " + Util.toDOYString(start) + "-"
            + Util.toDOYString(end) + Util.leftPad(" " + df3.format(getLength()) + " s ", 13);
  }

  public StringBuilder toStringBuilder(StringBuilder sb) {
    if (sb == null) {
      sb = Util.clear(scrsb);
    }
    synchronized (sb) {
      sb.append("Run from ").append(seedname).append(" ");
      Util.toDOYString(start, sb).append("-");
      Util.toDOYString(end, sb).append(" ");
      Util.append((int) getLength(), 9, ' ', sb).append(".");
      Util.append(((int) (getLength() * 1000.)) % 1000, 3, '0', sb).append(" s ");
    }
    return sb;
  }

  public StringBuilder getSeedname() {
    return seedname;
  }

  /**
   * implement Comparable, order by seedname, and then by start time
   *
   * @param r the Run to compare this to
   * @return -1 if <, 0 if =, 1 if >than
   */
  @Override
  public int compareTo(Run r) {
    int ret = Util.compareTo(seedname, r.getSeedname());
    if (ret != 0) {
      return ret;    // the seednames determine order
    }
    if (start == r.getStart()) {
      return 0;
    }
    return (start > r.getStart() ? 1 : -1);
  }

  /**
   * create a new run with the given miniseed as initial block
   *
   * @param ms The miniseed block to first include
   * @param trun Use truncated times from MiniSeed
   */
  public Run(TimeSeriesBlock ms, boolean trun) {
    truncate = trun;
    if (truncate && ms instanceof MiniSeed) {
      start = ((MiniSeed) ms).getTimeInMillis();
    } else {
      start = ms.getTimeInMillis();
    }
    if (truncate && ms instanceof MiniSeed) {
      end = ((MiniSeed) ms).getTimeInMillis()
              + (long) (ms.getNsamp() / ms.getRate() * 1000. + 0.49);
    } else {
      end = ms.getTimeInMillis() + (long) (ms.getNsamp() / ms.getRate() * 1000. + 0.49);
    }
    Util.clear(seedname).append(ms.getSeedNameSB());
    lastUpdate = System.currentTimeMillis();
    if (ms.getRate() > 0) {
      rate = ms.getRate();
      msover2 = (int) (500. / rate);
    }
    dbg = dbgstat;
    //if(seedname.substring(0,6).equals("USISCO")) dbg=true;
    if (dbg) {
      par.prt("Create Run for " + seedname + " " + Util.ascdatetime2(start) + " " + Util.ascdatetime2(end)
              + " dur=" + getLength() + " rate=" + rate + " nsamp=" + ms.getNsamp() + " ms2=" + msover2 + " dur=" + ((long) (ms.getNsamp() / ms.getRate() * 1000. + 0.49)));
    }
  }

  /**
   * create a new run with the given miniseed as initial block
   *
   * @param ms The miniseed block to first include
   */
  public Run(TimeSeriesBlock ms) {
    truncate = false;
    start = ms.getTimeInMillis();
    end = ms.getTimeInMillis() + ((long) (ms.getNsamp() / ms.getRate() * 1000. + 0.49));
    Util.clear(seedname).append(ms.getSeedNameSB());
    lastUpdate = System.currentTimeMillis();
    if (ms.getRate() > 0) {
      rate = ms.getRate();
      msover2 = (int) (500. / rate);
    }
    dbg = dbgstat;
    //if(seedname.substring(0,6).equals("USISCO")) dbg=true;
    if (dbg) {
      par.prt("Create Run for " + seedname + " " + Util.ascdate(start) + " " + Util.asctime2(start) + " dur=" + getLength());
    }
  }

  public Run(String seed, GregorianCalendar st, double dur) {
    start = st.getTimeInMillis();
    end = st.getTimeInMillis() + ((long) (dur * 1000.));
    Util.clear(seedname).append(seed);
    Util.rightPad(seedname, 12);
    lastUpdate = System.currentTimeMillis();
    //if(seedname.substring(0,6).equals("USISCO")) true;
    dbg = dbgstat;
    if (dbg) {
      par.prt("Create Run for " + seedname + " " + Util.ascdate(start) + " " + Util.asctime2(start) + " dur=" + getLength());
    }
  }

  public Run(String seed, long st, double dur) {
    start = st;
    end = st + ((long) (dur * 1000.));
    Util.clear(seedname).append(seed);
    Util.rightPad(seedname, 12);
    lastUpdate = System.currentTimeMillis();
    dbg = dbgstat;
    //if(seedname.substring(0,6).equals("USISCO")) true;
    if (dbg) {
      par.prt("Create Run for " + seedname + " " + Util.ascdate(start) + " " + Util.asctime2(start) + " dur=" + getLength());
    }
  }

  public Run(StringBuilder seed, GregorianCalendar st, double dur) {
    start = st.getTimeInMillis();
    end = st.getTimeInMillis() + ((long) (dur * 1000.));
    Util.clear(seedname).append(seed);
    Util.rightPad(seedname, 12);
    lastUpdate = System.currentTimeMillis();
    //if(seedname.substring(0,6).equals("USISCO")) true;
    dbg = dbgstat;
    if (dbg) {
      par.prt("Create Run for " + seedname + " " + Util.ascdate(start) + " " + Util.asctime2(start) + " dur=" + getLength());
    }
  }

  public Run(StringBuilder seed, long st, double dur) {
    start = st;
    end = st + ((long) (dur * 1000.));
    Util.clear(seedname).append(seed);
    Util.rightPad(seedname, 12);
    lastUpdate = System.currentTimeMillis();
    //if(seedname.substring(0,6).equals("USISCO")) true;
    dbg = dbgstat;
    if (dbg) {
      par.prt("Create Run for " + seedname + " " + Util.ascdate(start) + " " + Util.asctime2(start) + " dur=" + getLength());
    }
  }

  public void reset(String seed, GregorianCalendar st, double dur) {
    reset(seed, st.getTimeInMillis(), dur);
  }

  public void reset(String seed, long st, double dur) {
    Util.clear(seedname).append(seed);
    Util.rightPad(seedname, 12);
    start = st;
    end = start + ((long) (dur * 1000. + 0.49));
    setLastUpdate();
  }

  public void trim(long st, long en) {
    start = st;
    end = en;
    setLastUpdate();
  }

  public void trim(GregorianCalendar st, GregorianCalendar en) {
    start = st.getTimeInMillis();
    end = en.getTimeInMillis();
    setLastUpdate();
  }

  /**
   * see if this miniseed block will add contiguously to the end of this run
   *
   * @param seed The seedname for this run
   * @param st The start time of the block as GregorianCalendar
   * @param dur The time in seconds of the blocks duration
   * @return true, if block was contiguous and was added to this run, false otherwise
   */
  public boolean add(String seed, GregorianCalendar st, double dur) {
    Util.clear(scrsb).append(seed);
    return add(scrsb, st, dur);
  }

  /**
   * see if this miniseed block will add contiguously to the end of this run
   *
   * @param seed The seedname for this run
   * @param st The start time of the block as GregorianCalendar
   * @param dur The time in seconds of the blocks duration
   * @return true, if block was contiguous and was added to this run, false otherwise
   */
  public boolean add(String seed, long st, double dur) {
    Util.clear(scrsb).append(seed);
    return add(scrsb, st, dur);
  }

  /**
   * see if this miniseed block will add contiguously to the end of this run
   *
   * @param seed The seedname for this run
   * @param st The start time of the block as GregorianCalendar
   * @param dur The time in seconds of the blocks duration
   * @return true, if block was contiguous and was added to this run, false otherwise
   */
  public boolean add(StringBuilder seed, GregorianCalendar st, double dur) {
    return add(seed, st.getTimeInMillis(), dur);
  }

  /**
   * see if this miniseed block will add contiguously to the end of this run
   *
   * @param seed The seedname for this run
   * @param st The start time of the block as GregorianCalendar
   * @param dur The time in seconds of the blocks duration
   * @return true, if block was contiguous and was added to this run, false otherwise
   */
  public boolean add(StringBuilder seed, long st, double dur) {
    if (Util.compareTo(seedname, seed) != 0) {
      return false;
    }
    // Is the beginning of this one near the end of the last one!
    if (Math.abs(st - end) < msover2) {
      // add this block to the list
      if (dbg) {
        par.prt("Add to end " + toString() + " from " + Util.ascdate(st) + " " + Util.asctime2(st) + " " + dur);
      }
      end = st + ((long) (dur * 1000 + 0.49));
      modified = true;
      return true;
    } // Is it at the beginning
    else if (Math.abs((st + dur * 1000.) - start) < msover2) {
      if (dbg) {
        par.prt("Add to beg " + toString() + " from " + Util.ascdate(st) + " " + Util.asctime2(st) + " " + dur);
      }
      start = st;
      modified = true;
      return true;
    } else if (st >= start - msover2 && st + dur * 1000 <= end + msover2) {
      if (dbg) {
        par.prt("Add includ " + toString() + " from " + Util.ascdate(st) + " " + Util.asctime2(st) + " " + dur);
      }
      //end.setTimeInMillis(st+ ((long) (dur*1000+0.49)));
      modified = true;
      return true;
    } else if (st >= start && st <= end
            && st + dur * 1000 >= end - msover2) {
      if (dbg) {
        par.prt("Add lp end " + toString() + " from " + Util.ascdate(st) + " " + Util.asctime2(st) + " " + dur);
      }
      end = st + ((long) (dur * 1000 + 0.49));
      modified = true;
      return true;
    } else if (st + dur * 1000 >= start - msover2 && st + dur * 1000 <= end + msover2
            && st <= start) {
      if (dbg) {
        par.prt("Add lp beg " + toString() + " from " + Util.ascdate(st) + " " + Util.asctime2(st) + " " + dur);
      }
      start = st;
      modified = true;
      return true;
    } else {
      if (dbg) {
        par.prt("Add No Mat " + toString() + " from " + Util.ascdatetime2(st) + " " + dur);
      }
      return false;
    }
  }

  /**
   * see if this miniseed block will add contiguously to the end of this run
   *
   * @param ms the miniseed block to consider for contiguousness, add it if is is
   * @return true, if block was contiguous and was added to this run, false otherwise
   */
  public boolean add(TimeSeriesBlock ms) {
    if (ms.getRate() > 0  && rate <= 0.) {
      rate = ms.getRate();
      msover2 = (int) (500. / rate);
    }
    long st;
    if (truncate && ms instanceof MiniSeed) {
      st = ((MiniSeed) ms).getTimeInMillisTruncated();
    } else {
      st = ms.getTimeInMillis();
    }
    return add(ms.getSeedNameSB(), st, ms.getNsamp() / ms.getRate());
  }
}
