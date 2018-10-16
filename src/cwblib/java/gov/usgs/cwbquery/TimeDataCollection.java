/*
 *  This software is in the public domain because it contains materials 
 *  that originally came from the United States Geological Survey, 
 *  an agency of the United States Department of Interior. For more 
 *  information, see the official USGS copyright policy at 
 *  http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.cwbquery;

import gov.usgs.anss.util.Util;
import gov.usgs.anss.edgethread.EdgeThread;
import java.util.ArrayList;
import java.util.GregorianCalendar;

/**
 * Times are submitted from real data coming in and offsets and times are recorded periodically
 * based on the sample rate (about 1 in 10000 samples is the interval). This gives a consistent
 * known time point for doing time calculations in the buffer offsets by doing such calculations
 * from a nearby known time. So if the data rate is not particularly good, the calculations of
 * offsets and times of offsets will be pretty accurate event if the QuerySpan covers a lot of time.
 *
 */
public final class TimeDataCollection {

  private boolean tdbg = false;
  private final ArrayList<TimeData> times = new ArrayList(10);
  private double rate;
  private long minTimeDiff;
  private String seedname;
  private int msover2;
  private final double duration;
  private final StringBuilder sb = new StringBuilder(100);
  private final EdgeThread par;

  public int getMemoryUsage() {
    return times.size() * 20 + sb.capacity() + 40;
  }

  public void setDebug(boolean t) {
    tdbg = t;
  }

  private void prt(String s) {
    if (par == null) {
      Util.prt(s);
    } else {
      par.prt(s);
    }
  }

  private void prta(String s) {
    if (par == null) {
      Util.prta(s);
    } else {
      par.prta(s);
    }
  }

  private void prt(StringBuilder s) {
    if (par == null) {
      Util.prt(s);
    } else {
      par.prt(s);
    }
  }

  private void prta(StringBuilder s) {
    if (par == null) {
      Util.prta(s);
    } else {
      par.prta(s);
    }
  }
  private final StringBuilder tmpsb = new StringBuilder(100);

  @Override
  public String toString() {
    return toStringBuilder(null).toString();
  }

  private StringBuilder toStringBuilder(StringBuilder tmp) {
    StringBuilder sbt = tmp;
    if (sbt == null) {
      sbt = Util.clear(sb);
    }
    synchronized (sbt) {
      sbt.append(" #Times=").append(times.size()).append(" rt=").append(rate).append(" minTimeDiff=").
              append(minTimeDiff).append(" msover2=").append(msover2).
              append(times.size() > 0 ? " first=" + times.get(0) + " last=" + times.get(times.size() - 1) : "null");
    }
    return sbt;
  }

  public StringBuilder toStringDetail() {
    synchronized (sb) {
      Util.clear(sb).append(seedname).append(" #Times=").append(times.size()).append(" rt=").append(rate).
              append(" minTimeDiff=").append(minTimeDiff).append(" msover2=").append(msover2).append("\n");
      for (int i = 0; i < times.size(); i++) {
        sb.append("  i=").append(i).append(" ").
                append(seedname).append(" ").append(times.get(i).toStringBuilder()).append(" ").
                append(i < times.size() - 1 ? Util.roundToSig(
                        (times.get(i + 1).getOffset() - times.get(i).getOffset())
                        / ((times.get(i + 1).getTime() - times.get(i).getTime()) / 1000.), 10) : "")
                .append("\n");
      }
    }
    return sb;
  }

  public long getMinTimeDiff() {
    return minTimeDiff;
  }

  public void setMinTimeDiff(long i) {
    minTimeDiff = i;
  }

  /**
   * By calling this collection with fixed timestamps and offsets, the user can have a reasonable
   * sparse collection of known time stamps.
   *
   * @param seedname NNSSSSSCCCLL format
   * @param rate In Hz
   * @param duration To track the times
   * @param parent Logging parent.
   */
  public TimeDataCollection(String seedname, double rate, double duration, EdgeThread parent) {
    this.seedname = seedname;
    setRate(rate);
    par = parent;
    this.duration = duration;
    setRate(rate);
    if (tdbg) {
      prta(Util.clear(tmpsb).append("TDC: ").append(seedname).append(" init mindff=").append(minTimeDiff));
    }
  }

  /**
   * Clear the history of time and add a new starting point for the given offset
   *
   * @param seedname NNSSSSSCCCLL
   * @param rate In Hz
   * @param offset The offset of the time
   * @param time The time in millis
   */
  public void clear(String seedname, double rate, int offset, long time) {
    synchronized (times) {
      times.clear();
    }
    this.seedname = seedname;
    setRate(rate);
    addTime(offset, time, null);
    if (tdbg) {
      prta(Util.clear(tmpsb).append("TDC: clear() ").append(seedname).append(" ").append(toStringDetail()));
    }
  }

  /**
   * Set the data rate. This checks for change of rate
   *
   * @param r The rate to set in Hz
   */
  public final void setRate(double r) {
    if (rate != 0.) {
      if (tdbg && Math.abs(rate - r) / rate > 0.1) {// Is this a significant rate change (not just a better estimate)
        prt(Util.clear(tmpsb).append(" **** Rate changed in TimeDataCollection rnow=").append(r).
                append(" vs ").append(rate).append(" ").append(toStringBuilder(null)));
        times.clear();
      }
    }
    rate = r;
    msover2 = (int) (1. / rate * 1000. / 2.);
    minTimeDiff = (long) (400000 / rate + 0.4);      //DEBUG was 4000000  // Miliseconds between samples.
    if (minTimeDiff > duration / 8 * 1000) {
      minTimeDiff = (long) (duration * 1000. / 8.);
    }
  }

  /**
   * discard any time point older than time and adjust all offsets by the given amount. Used
   * primarily by the shift() routine to time and update for the data moving in the buffer.
   *
   * @param time The earliest time to keep in millis
   * @param offset The offset the data are going to move.
   */
  public void trim(GregorianCalendar time, int offset) {
    trim(time.getTimeInMillis(), offset);
  }

  /**
   * discard any time point older than time and adjust all offsets by the given amount. Used
   * primarily by the shift() routine to time and update for the data moving in the buffer.
   *
   * @param time The earliest time to keep in millis
   * @param offset The offset the data are going to move.
   */
  public void trim(long time, int offset) {
    synchronized (times) {
      for (int i = times.size() - 1; i >= 0; i--) {    // Loop from end to beginning looking for earler times
        times.get(i).setOffset(times.get(i).getOffset() - offset);// Adjust the offset.
        if (times.get(i).getTime() < time) {
          if (tdbg) {
            Util.clear(tmpsb).append("TDC: ").append(seedname).append(" drop i=").
                    append(i).append(" ").append(Util.ascdatetime2(times.get(i).getTime())).
                    append(" trim to ").append(Util.ascdatetime2(time, null));
            prta(tmpsb);
          }
          times.remove(i);
        }
      }
    }
  }

  /**
   * Find closest index to the given time.
   *
   * @param time The time in millis
   * @return The offset closes or -1 no times are present
   */
  public int closestIndex(long time) {
    long min = Long.MAX_VALUE;
    int mini = -1;
    synchronized (times) {
      for (int i = 0; i < times.size(); i++) {
        long diff = difftime(time, i);
        if (Math.abs(diff) < min) {
          min = diff;
          mini = i;
        }
      }
    }
    if (tdbg) {
      prta(Util.clear(tmpsb).append("TDC: ").append(seedname).append(" closestTime i=").
              append(mini).append(" diff=").append(mini >= 0 ? difftime(time, mini) : -1));
    }
    return mini;
  }

  public long getClosestTime(long time) {
    int i = closestIndex(time);
    return i > 0 && i < times.size() ? times.get(i).getTime() : -1;
  }

  /**
   * compute time difference from the given time to the given times index
   *
   * @param time The subject time
   * @param i The offset to the times structure (usually returned from closestIndex()
   * @return Difference in millis
   */
  private long difftime(long time, int i) {
    if (i >= times.size()) {
      return Long.MAX_VALUE;
    }
    if (times.get(i) == null) {
      return Long.MAX_VALUE;
    }
    return time - times.get(i).getTime();
  }

  /**
   * submit a time for consideration to be added to these structures. If this time is is not within
   * the minTimeDifference of the closest time, it will be added
   *
   * @param offset The offset in the data array represented by this time
   * @param time The time in millis
   * @param start Can be null, but if not, returned with start time of earliest time.
   * @return True, if this time was added to the structures.
   */
  public boolean addTime(int offset, long time, GregorianCalendar start) {
    boolean ret = false;
    synchronized (times) {
      if (times.isEmpty()) { // Empty, always add this time
        times.add(0, new TimeData(offset, time));
        ret = true;
      } else {
        int i = closestIndex(time);    // Closed index in times array of this time
        long diff = difftime(time, i);  // diffence in time between this time and the closest time
        if (Math.abs(diff) >= minTimeDiff) {  // Is this at least the minimum time away
          if (difftime(time, i) < 0) {        // if the difference is negative, we want to insert before this index
            times.add(i, new TimeData(offset, time));
            if (i == 0 && start != null) {                   // if we are replacing the first one, recompute start
              long old = start.getTimeInMillis();
              start.setTimeInMillis(getMillisAt(0));
              //prta("TDC: "+seedname+" update start "+Util.ascdatetime2(start)+" "+(old - start.getTimeInMillis()));
            } // recompute start if we update the first one.
            if (tdbg) {
              prta(Util.clear(tmpsb).append("TDC: add bef i=").append(i).append(" ").append(toStringDetail()));
            }
            ret = true;
          } // if its before index, add at this index
          else {  // Its after this index, so insert it after 
            times.add(i + 1, new TimeData(offset, time));
            if (times.size() == 2 && times.get(0).getOffset() == 0) {
              times.remove(0);   // the original first time is not very good!
            }
            if (tdbg) {
              prta(Util.clear(tmpsb).append("TDC: add aft i=").append(i + 1).append(" ").append(toStringDetail()));
            }
            ret = true;
          } // If after this index, put in next one
        }
      }
    }
    return ret;
  }

  /**
   * get index of the time
   *
   * @param time Time of sample for which we need the index
   * @return The index.
   */
  public int getIndexOfTime(long time) {
    if (times.isEmpty()) {
      prta(Util.clear(tmpsb).append("TDC: ").append(seedname).
              append(" * Unusual call getIndexOfTime when times are empty!").append(toStringDetail()));
      return -1;
    }
    int offset;
    int i;
    long msoff;
    synchronized (times) {
      i = closestIndex(time);   // If this returns something, use it in time calculation
      if (i < 0) {
        i = 0;        // If this time is before any time, use the first one
      }
      msoff = time - times.get(i).getTime(); // compute offset in time to closest
      // the offset is this index converted to millis (include +/- rounding factor) from the closest time
      offset = (int) ((msoff + (msoff < 0 ? -msover2 + 1 : msover2 - 1)) / 1000. * rate) + times.get(i).getOffset();
    }
    if (tdbg) {
      Util.clear(tmpsb).append("TDC: getIndexOf() time=").append(Util.ascdatetime2(time, null)).
              append(" ").append(seedname).append(" closesti=").append(i).append(" ").
              append(times.get(i).toString()).append(" msoff=").append(msoff).append(" offset=").
              append(offset).append("\n").append(toStringDetail());
      prta(tmpsb);
    }
    return offset;
  }

  /**
   * get the time of the given index in the data
   *
   * @param index The index to a data point
   * @return The time in millis of that data point.
   */
  public long getMillisAt(int index) {
    if (times.isEmpty()) {
      prta(Util.clear(tmpsb).append("TDC: ").append(seedname).
              append(" ** Unusual call getMillisAt() when times are empty! index=").
              append(index));
      return 0;
    }
    int i;
    long ans;
    synchronized (times) {
      for (i = 0; i < times.size(); i++) {     // find first time offset that is before this index.
        if (times.get(i) != null) {
          if (index < times.get(i).getOffset()) {
            i = i - 1;
            break;
          }
        }
      }
      // if this is before first time, then use first time and negative time offset
      if (i < 0) {
        i = 0;
        //if(tdbg) prta("TDC: "+seedname+" * unusual getMillisAt index is before first index="+index+" "+times.get(0).getOffset());
        return times.get(i).getTime() + ((long) ((index - times.get(i).getOffset()) / rate * 1000. - 0.5));
      }
      // If this is after the last one,  use the last one.
      if (i >= times.size()) {     // Time is before all of these times, use first one
        i = times.size() - 1;
        //prta("TDC: * unusual getMillisAt index is after last index="+index+" "+times.get(i).getOffset());
      }
      ans = times.get(i).getTime() + ((long) ((index - times.get(i).getOffset()) / rate * 1000. + 0.5));
      if (tdbg) {
        Util.clear(tmpsb).append("TDC: ").append(seedname).append(" getMillisAt idx=").
                append(index).append(" rt=").append(rate).append(" i=").append(i).append(" ").
                append(times.get(i).toString()).append(" ans=").append(Util.ascdatetime2(ans, null)).append(" ");
        tmpsb.append(toStringDetail());
        prta(tmpsb);
      }
    }
    return ans;
  }

  /**
   * This class provides data storage for the TimeDataCollection
   *
   */
  class TimeData {

    int offset;
    long time;
    StringBuilder tmpsb = new StringBuilder(40);

    public int getOffset() {
      return offset;
    }

    public long getTime() {
      return time;
    }

    public void setOffset(int i) {
      offset = i;
    }

    public TimeData(int offset, long time) {
      this.offset = offset;
      this.time = time;
    }

    @Override
    public String toString() {
      return Util.ascdatetime2(time) + " off2=" + offset;
    }

    public StringBuilder toStringBuilder() {
      Util.clear(tmpsb);
      Util.ascdatetime2(time, tmpsb).append(" off2=").append(offset);
      return tmpsb;
    }
  }
}
