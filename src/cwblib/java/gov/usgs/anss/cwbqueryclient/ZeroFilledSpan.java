/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.cwbqueryclient;

import gov.usgs.anss.edge.RunList;
import gov.usgs.anss.seed.MiniSeed;
import gov.usgs.anss.seed.Steim1;
import gov.usgs.anss.seed.Steim2Object;
import gov.usgs.anss.seed.SteimException;
import gov.usgs.anss.util.Util;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;

/**
 * This class represent a time series chunk which is zero filled (or filled with user specified
 * value) if data is missing. The idea is to allow creation, population by a series of blocks which
 * may not be contiguous or even in order. Constructors needs to deal with different data types and
 * construction methods.
 *
 * Initially, this class assumed it would be created from a list of mini-seed blocks. However, it is
 * quite likely it will need to be extended to allow a pre-allocation followed by many calls adding
 * data to the time series.
 *
 * Part of the expansion was to support rolling buffers where the data is shifted down so that the
 * duration remains the same, but the start time keeps rolling forward with the buffer remaining
 * consistent. This is used by the QueryRing extending class to support time series from continuous
 * calculation, while hiding the details of where data population from the user.
 *
 * @author davidketchum
 */
public class ZeroFilledSpan {

  private final RunList runs = new RunList(10, false);
  private String seedname;
  int nsamp;        // This is the duration of this in samples, it is fixed to be near the data length
  int fillValue = 0;
  final GregorianCalendar start = new GregorianCalendar();// The time of the first sample in the buffer
  protected long lastTime;                // This is the millisecond time of the newest sample in the buffer
  protected int[] data;                  // The data array of nsamp samples
  double rate = 0.;               // The digitizing rate of this buffer
  boolean dbg = false;            // If true, more output
  private boolean decodeClean;
  private int firstMissing, lastMissing, nmissing, lastData;
  int msover2;                    // 1/2 of a bin width in millis, set with the rate
  private final GregorianCalendar shiftTime = new GregorianCalendar();// Scratch variable used in shift routines
  private final byte[] frames = new byte[4096];               // Space for decompressing Steim1 or 2 frames
  private final GregorianCalendar gend = new GregorianCalendar();  // this is a scratch value

  private final Steim2Object steim2 = new Steim2Object();

  public void setDebug(boolean t) {
    dbg = t;
  }

  /**
   * return the time of the newest data point in millisecond
   *
   * @return s
   */
  public long getLastTime() {
    return lastTime;
  }

  /**
   * return the index of the first missing data value (fill value)
   *
   * @return
   */
  public int getFirstMissing() {
    return firstMissing;
  }

  /**
   * return the index of the last missing data value (fill value)
   *
   * @return
   */
  public int getLastMissing() {
    return lastMissing;
  }

  /**
   * return number of missing data
   *
   * @return
   */
  public int getNumberMissing() {
    return nmissing;
  }

  /**
   * return index of last actual data value in buffer
   *
   * @return
   */
  public int getLastData() {
    return lastData;
  }

  /**
   * if the last load of miniSeed data had no errors
   *
   * @return
   */
  public boolean wasDecodeClean() {
    return decodeClean;
  }

  /**
   * reset the decodeClean variable
   */
  public void resetDecodeClear() {
    decodeClean = false;
  }

  /**
   * return the RunList for this buffer
   *
   * @return
   */
  public RunList getRunList() {
    return runs;
  }

  /**
   * string representing this time series
   *
   * @return a String with nsamp, rate and start date/time
   */
  @Override
  public String toString() {
    getNMissingData();
    return "ZeroFilledSpan: " + seedname + " ns=" + nsamp + " rt=" + rate + " "
            + Util.ascdate(start) + " " + Util.asctime2(start) + " lst=" + Util.ascdate(lastTime) + " " + Util.asctime2(lastTime)
            + " missing: " + (firstMissing > 0 ? "First at " + firstMissing + " last data at " + lastData + " last missing at "
                    + lastMissing + " # missing=" + nmissing : "");
  }

  public String toStringFull() {
    String s = toString() + "\n";
    for (int i = 0; i < runs.size(); i++) {
      s += runs.get(i).toString() + "\n";
    }
    return s;

  }

  public long getMillisAt(int i) {
    long val = start.getTimeInMillis() + ((long) (i / rate * 1000. + 0.001));
    String s = Util.ascdate(val) + " " + Util.asctime2(val);
    return val;
  }

  public StringBuilder timeAt(int i) {

    gend.setTimeInMillis(start.getTimeInMillis() + ((long) (i / rate * 1000. + 0.5)));
    return Util.asctime2(gend);
  }

  /**
   * Return a new GregorianCalendar with the time at index i. It is preferred to use the other
   * method by this name and avoid the GregorianCalendar creation
   *
   * @param i Index of element in data
   * @return A new GregorianCalendar
   */
  public GregorianCalendar getGregorianCalendarAt(int i) {
    GregorianCalendar g = new GregorianCalendar();
    g.setTimeInMillis(start.getTimeInMillis() + ((long) (i / rate * 1000. + 0.5)));
    return g;
  }

  /**
   * Return a new GregorianCalendar with the time at index i. The same gregorian is returned as was
   * passed in as a curtesy
   *
   * @param i Index of element in data
   * @return The input Gregorian updated with the time of index i
   */

  /**
   * Return a new GregorianCalendar with the time at index i.The same gregorian is returned as was
   * passed in as a curtesy
   *
   * @param i Index of element in data
   * @param g User Time to update with index ith time
   * @return The input Gregorian updated with the time of index i
   */
  public GregorianCalendar getGregorianCalendarAt(int i, GregorianCalendar g) {
    g.setTimeInMillis(start.getTimeInMillis() + ((long) (i / rate * 1000. + 0.5)));
    return g;
  }

  /**
   * get the digitizing rate in Hz
   *
   * @return The digitizing rate in Hz.
   */
  public double getRate() {
    return rate;
  }

  /**
   * Get the index into the data array of the given time.
   *
   * @param starting The time of the sample - the nearest sample index to this time is return
   * @return
   */
  public int getIndexOfTime(GregorianCalendar starting) {
    long msoff = starting.getTimeInMillis() - start.getTimeInMillis();
    int offset = (int) ((msoff + msover2 - 1.) / 1000. * rate);
    if (offset < 0) {
      return -1;
    }
    return offset;
  }

  /**
   * This returns an array of data for a given time and number of samples. The start time given is
   * updated to the actual time of the first sample
   *
   * @param starting Time desired of the first sample
   * @param needed Number of samples desired
   * @param d An array of ints to receive the data
   * @param allowGaps If true, data return might include fille values, if not, then returned data is
   * shortened to first fill
   * @return Number of samples returned or -1 if data is not in this buffer
   */
  public int getData(GregorianCalendar starting, int needed, int[] d, boolean allowGaps) {
    int offset = getIndexOfTime(starting);
    if (offset < 0) {
      return -1;
    }
    starting.setTimeInMillis(getMillisAt(offset));    // Set time to be time of first sample
    if (dbg) {
      Util.prt("getData starting =" + Util.asctime2(starting) + " buf start=" + Util.asctime2(start) + " offset=" + offset);
    }
    if (needed + offset > data.length) {
      needed = data.length - offset;
    }
    if (!allowGaps) {
      for (int i = 0; i < needed; i++) {
        d[i] = data[i + offset];
        if (data[i + offset] == fillValue) {
          return i;
        }
      }
    }
    System.arraycopy(data, offset, d, 0, needed);
    return needed;

  }

  /**
   * This returns an array of data for a given time and number of samples. The start time given is
   * updated to the actual time of the first sample
   *
   * @param starting Time desired of the first sample
   * @param needed Number of samples desired
   * @param d An array of doubles to receive the data
   * @param allowGaps If true, data return might include fille values, if not, then returned data is
   * shortened to first fill
   * @return Number of samples returned or -1 if data is not in this buffer
   */
  public int getData(GregorianCalendar starting, int needed, double[] d, boolean allowGaps) {
    int offset = getIndexOfTime(starting);
    if (offset < 0) {
      return -1;
    }
    starting.setTimeInMillis(getMillisAt(offset));    // Set time to be time of first sample
    if (dbg) {
      Util.prt("getData starting =" + Util.asctime2(starting) + " buf start=" + Util.asctime2(start) + " offset=" + offset);
    }
    if (needed + offset > data.length) {
      needed = data.length - offset;
    }
    if (!allowGaps) {
      for (int i = 0; i < needed; i++) {
        if (data[i + offset] == fillValue) {
          return i;  // if fills are not allowed
        }
        d[i] = data[i + offset];
      }
    } else {
      for (int i = 0; i < needed; i++) {
        d[i] = data[i + offset];
      }
    }
    return needed;

  }

  /**
   * This returns an array of data for a given time and number of samples. The start time given is
   * updated to the actual time of the first sample
   *
   * @param starting Time desired of the first sample
   * @param needed Number of samples desired
   * @param d An array of floats to receive the data
   * @param allowGaps If true, data return might include fille values, if not, then returned data is
   * shortened to first fill
   * @return Number of samples returned or -1 if data is not in this buffer
   */
  public int getData(GregorianCalendar starting, int needed, float[] d, boolean allowGaps) {
    int offset = getIndexOfTime(starting);
    if (offset < 0) {
      return -1;
    }
    starting.setTimeInMillis(getMillisAt(offset));    // Set time to be time of first sample
    if (dbg) {
      Util.prt("getData starting =" + Util.asctime2(starting) + " buf start=" + Util.asctime2(start) + " offset=" + offset);
    }
    if (needed + offset > data.length) {
      needed = data.length - offset;
    }
    if (!allowGaps) {
      for (int i = 0; i < needed; i++) {
        if (data[i + offset] == fillValue) {
          return i;  // if fills are not allowed
        }
        d[i] = data[i + offset];          // put data in array
      }
    } else {
      for (int i = 0; i < needed; i++) {
        d[i] = data[i + offset];
      }
    }
    return needed;

  }

  /**
   * get the time series as an array of ints
   *
   * @return The timeseries as an array of ints - this is the internal array so any changes will be
   * in this object
   */
  public int[] getData() {
    return data;
  }

  /**
   * get the ith time series value
   *
   * @param i THe index (starting with zero) of the data point to return.
   * @return The ith time series value
   */
  public int getData(int i) {
    return data[i];
  }

  /**
   * get a chunk of the data into an array,
   *
   * @param d The array to put the data in
   * @param off The offset in the internal data buffer to start
   * @param len The maximum length of data to return (d must be dimensioned >len)
   * @return The number of samples actually returned <=len
   */
  public int getData(int[] d, int off, int len) {
    int n = len;
    if ((nsamp - off) < len) {
      n = nsamp - off;
    }
    System.arraycopy(data, off, d, 0, n);
    return n;
  }

  /**
   * get number of data samples in time series (many might be zeros). This is the length of the
   * internal buffer from the original setup of the duration. If a larger time series is loaded via
   * the refill() method, this value will grow to that size. So, it represents the duration of this
   * buffer in samples.
   *
   * @return Number of samples in series
   */
  public int getNsamp() {
    return nsamp;
  }

  /**
   * return start time as a GregorianCalendar
   *
   * @return The start time
   */
  public GregorianCalendar getStart() {
    return start;
  }

  /**
   * return the max value of the time series ignoring any fill values
   *
   * @return Min value of the time series
   */
  public int getMin() {
    int min = 2147000000;
    for (int i = 0; i < nsamp; i++) {
      if (data[i] < min && data[i] != fillValue) {
        min = data[i];
      }
    }
    return min;
  }

  /**
   * return the max value of the time series
   *
   * @return Max value of the time series
   */
  public int getMax() {
    int max = -2147000000;
    for (int i = 0; i < nsamp; i++) {
      if (data[i] > max && data[i] != fillValue) {
        max = data[i];
      }
    }
    return max;
  }

  /**
   * return true if any portion of the allocated space has a "no data" or fill value. Trim number of
   * samples to reflect actual sample if end contains some no data points.
   *
   * @return true if there is at least on missing data value
   */
  public boolean hasGapsBeforeEnd() {
    int ns = nsamp;
    if (data[0] == fillValue) {
      return true;     // opening with fill
    }
    int i;
    for (i = nsamp - 1; i >= 0; i--) {
      if (data[i] != fillValue) {
        ns = i + 1;
        break;
      }
    }
    if (i <= 0 && ns == nsamp) {
      return false;      // no fill Values found looking for last one!
    }
    for (i = 0; i < ns; i++) {
      if (data[i] == fillValue) {
        return true;
      }
    }
    //nsamp=ns;
    return false;
  }

  /**
   * return true if any portion of the allocated space has a "no data" or fill value
   *
   * @return true if there is at least on missing data value
   */
  public boolean hasGaps() {
    for (int i = 0; i < nsamp; i++) {
      if (data[i] == fillValue) {
        return true;
      }
    }
    return false;
  }

  /**
   * return number of missing data points
   *
   *
   * @return Number of missing data points
   */
  public int getNMissingData() {
    int noval = 0;
    int first = -1;
    int last = nsamp + 1;
    for (int i = 0; i < nsamp; i++) {
      if (data[i] == fillValue) {
        noval++;
        last = i;
        if (noval == 1) {
          first = i;
        }
      } else {
        lastData = i;
      }
    }
    firstMissing = first;
    lastMissing = last;
    nmissing = noval;
    return noval;
  }

  /**
   * compare to ZeroFilledSpans for "equivalence"
   *
   * @return True if equivalent
   * @param z Another ZeroFilledSpan to compare against.
   */
  public String differences(ZeroFilledSpan z) {
    StringBuilder sb = new StringBuilder(1000);
    StringBuilder details = new StringBuilder(1000);
    sb.append("Summary ").append(toString()).append("\n");
    sb.append("Summary ").append(z.toString()).append("\n");

    if (getNMissingData() != z.getNMissingData()) {
      sb.append("*** # missing different ").append(getNMissingData()).append("!=").append(z.getNMissingData()).append("\n");
    }
    if (getNsamp() != z.getNsamp()) {
      sb.append("*** Nsamp different ").append(nsamp).append(" != ").append(z.getNsamp()).append(" diff = ").append(nsamp - z.getNsamp()).append("\n");
    }
    int gapStart = -1;
    int gapSize = 0;
    for (int i = 0; i < Math.min(nsamp, z.getNsamp()); i++) {
      if (data[i] != z.getData(i)) {
        if (gapStart == -1) {
          sb.append(" difference start at ").append(i).append(" ").append(timeAt(i));
          gapStart = i;
          gapSize++;
        } else {
          gapSize++;
        }
        details.append("*** ").append((i + "        ").substring(0, 8)).append(Util.leftPad((data[i] == fillValue ? "  nodata  " : "" + data[i]), 8)).append(Util.leftPad((z.getData(i) == fillValue ? "  nodata  " : "" + z.getData(i)), 8));
        if (data[i] == fillValue || z.getData(i) == fillValue) {
          details.append("\n");
        } else {
          details.append(Util.leftPad("df=" + (data[i] - z.getData(i)), 14)).append("\n");
        }
      } else {
        if (gapStart != -1) {
          sb.append(" ends at ").append(i).append(" ").append(timeAt(i)).append(" # diff=").append(gapSize).append("\n");
          gapStart = -1;
          gapSize = 0;
        }
      }
    }
    if (gapStart != -1) {
      sb.append(" ends at ").append(nsamp).append(" ").append(timeAt(nsamp)).append(" # diff=").append(gapSize).append("\n");
    }
    return sb.toString() + "\nDetails:\n" + details.toString();
  }

  /**
   * create a new instance of ZeroFilledSpan from a list of mini-seed blockettes
   *
   * @param list A list containing Mini-seed objects to put in this series
   */
  public ZeroFilledSpan(ArrayList<MiniSeed> list) {
    // Need to find first and last data block to calculate time span
    long first = Long.MAX_VALUE;
    long last = Long.MIN_VALUE;
    MiniSeed ms = null;
    double rt = -1;
    for (MiniSeed list1 : list) {
      if (list1.getNsamp() <= 0 && list1.getRate() <= 0) {
        continue;
      }
      if (list1.getTimeInMillis() < first) {
        first = list1.getTimeInMillis();
        ms = list1;
      }
      if (list1.getNextExpectedTimeInMillis() > last) {
        last = list1.getNextExpectedTimeInMillis();
      }
      if (rt > 0) {
        if (list1.getRate() > 0. && list1.getNsamp() > 0) {
          rt = list1.getRate();
          setRate(list1.getRate());
        }
      }
    }

    // calculate span and do itd
    double duration = (last - first) / 1000.;
    if (ms != null) {
      doZeroFilledSpan(list, ms.getGregorianCalendar(), duration, 0);
    }
  }

  /**
   * Creates a new instance of ZeroFilledSpan - this represents zero filled time series record
   *
   * @param list A list containing Mini-seed objects to put in this series
   * @param trim The start time - data before this time are discarded
   * @param duration Time in seconds that this series is to represent
   */
  public ZeroFilledSpan(ArrayList<MiniSeed> list, GregorianCalendar trim, double duration) {
    doZeroFilledSpan(list, trim, duration, 0);
  }

  /**
   * create a new instance of ZeroFilledSpan from a list of mini-seed blockettes
   *
   * @param list A list containing Mini-seed objects to put in this series
   * @param fill a integer to use to pre-fill the array, (the not a data value)
   */
  public ZeroFilledSpan(ArrayList<MiniSeed> list, int fill) {
    fillValue = fill;
    // Need to find first and last data block to calculate time span
    long first = Long.MAX_VALUE;
    long last = Long.MIN_VALUE;
    MiniSeed ms = null;
    double rt = -1;
    for (MiniSeed list1 : list) {
      if (list1.getNsamp() <= 0 || list1.getRate() <= 0) {
        continue;
      }
      if (list1.getTimeInMillis() < first) {
        first = list1.getTimeInMillis();
        ms = list1;
      }
      if (list1.getNextExpectedTimeInMillis() > last) {
        last = list1.getNextExpectedTimeInMillis();
      }
      if (rt > 0) {
        if (list1.getRate() > 0. && list1.getNsamp() > 0) {
          rt = list1.getRate();
          setRate(list1.getRate());
        }
      }
    }

    // calculate span and do it
    double duration = (last - first) / 1000.;
    if (ms != null) {
      doZeroFilledSpan(list, ms.getGregorianCalendar(), duration, fill);
    }
  }

  /**
   * Creates a new instance of ZeroFilledSpan - this represents zero filled time series record
   *
   * @param list A list containing Mini-seed objects to put in this series
   * @param trim The start time - data before this time are discarded
   * @param duration Time in seconds that this series is to represent
   * @param fill a integer to use to pre-fill the array, (the not a data value)
   */
  public ZeroFilledSpan(ArrayList<MiniSeed> list, GregorianCalendar trim, double duration, int fill) {
    doZeroFilledSpan(list, trim, duration, fill);
  }

  /**
   * Replace this with the data on this list. Make the data bigger if the duration calls for it,
   * this can be used to reuse one of these objects for totally unrelated data
   *
   * @param list Of miniSeed blocks to put in the span
   * @param trim Trim data to this time as the beginning
   * @param dur The duration of seconds of the buffer.
   * @param fill Fill value
   */
  public void refill(ArrayList<MiniSeed> list, GregorianCalendar trim, double dur, int fill) {
    if (list.isEmpty()) {
      return;
    }
    int j = 0;
    MiniSeed ms = list.get(j);
    while (ms.getRate() <= 0.) {
      ms = (MiniSeed) list.get(j++);
    }
    if (ms.getRate() <= 0.) {
      return;
    }
    double duration = dur;
    if (data.length < dur * ms.getRate() + 0.01) {
      data = new int[(int) (dur * ms.getRate() + 0.01)];
    }
    doZeroFilledSpan(list, trim, duration, fill);
  }

  /**
   * populate a zero filled span. Called by the constructors
   *
   * @param list ArrayList of mini-seed data
   * @param trim Start time - data before this time is discarded.
   * @param duration Time in seconds to do it
   * @param fill an integer to use to pre-fill the array (the not-a-data value)
   */
  private void doZeroFilledSpan(ArrayList<MiniSeed> list, GregorianCalendar trim, double duration, int fill) {
    decodeClean = true;
    fillValue = fill;
    lastTime = 0;     // this is a complete refill, so reset last time
    // Look through blocks until we find one that has a rate (i.end. probably data!)
    double rt = 0.;
    rate = 0.;
    int j = 0;
    MiniSeed ms = null;
    while (rt == 0. && j < list.size()) {
      ms = (MiniSeed) list.get(j);
      if (ms != null) {
        rt = ms.getRate();
        setRate(ms.getRate());
        seedname = ms.getSeedNameString();
      }
      j++;
    }
    if (rt == 0. || ms == null) {
      //Util.prt("Warning: There is no data in this span size="+list.size()+" time="+Util.ascdate(trim)+" "+Util.asctime(trim));
      nsamp = 0;
      data = new int[1];
      if (ms == null) {
        start.setTimeInMillis(trim.getTimeInMillis());
      } else {
        start.setTimeInMillis(ms.getGregorianCalendar().getTimeInMillis());
      }
      return;
    }
    int begoffset = (int) ((trim.getTimeInMillis() - ms.getGregorianCalendar().getTimeInMillis())
            * rate / 1000. + 0.01);
    if (dbg) {
      Util.prt(Util.ascdate(trim) + " " + Util.asctime(trim) + " start="
              + Util.ascdate(ms.getGregorianCalendar()) + " " + Util.asctime(ms.getGregorianCalendar()));
    }
    // The start time of this span is the time of first sample from first ms after 
    // the trim start time
    start.setTimeInMillis(ms.getGregorianCalendar().getTimeInMillis());
    start.add(Calendar.MILLISECOND, (int) (begoffset / rate * 1000.));// first in trimmed interval
    if (dbg) {
      Util.prt(Util.ascdate(start) + " " + Util.asctime(start) + " begoff=" + begoffset);
    }
    //MiniSeed msend = (MiniSeed) list.get(list.size()-1);
    nsamp = (int) (duration * ms.getRate() + 0.5);
    //Util.prt("duration="+duration+" nsf="+(duration*ms.getRate())+"nsamp="+nsamp);
    if (data == null) {
      data = new int[nsamp + 1];
    } else if (data.length < nsamp + 1) {
      data = new int[nsamp + 1];
    }
    if (fill != 0) {
      for (int i = 0; i < nsamp + 1; i++) {
        data[i] = fill;
      }
    }
    for (int i = list.size() - 1; i >= 0; i--) {
      addBlock(list.get(i));
    }           // end for each block in list
    runs.trim(start);
    getNMissingData();

  }

  public void shift(GregorianCalendar time) {
    int offset = this.getIndexOfTime(time);
    try {
      if (offset < 0 || offset >= data.length) {
        Util.prt("Shift: bad offset=" + offset
                + "time=" + Util.ascdate(time) + " " + Util.asctime2(time)
                + " Start=" + Util.ascdate(start) + " " + Util.asctime2(start)
                + " offset=" + offset + " length=" + data.length);
        start.setTimeInMillis(time.getTimeInMillis());
        for (int i = 0; i < data.length; i++) {
          data[i] = fillValue;
        }
      }
      System.arraycopy(data, offset, data, 0, data.length - offset);
      start.setTimeInMillis(this.getMillisAt(offset));
    } catch (ArrayIndexOutOfBoundsException e) {
      Util.prt("Shift array out of bounds time=" + Util.ascdate(time) + " " + Util.asctime2(time)
              + " Start=" + Util.ascdate(start) + " " + Util.asctime2(start)
              + " offset=" + offset + " length=" + data.length);
      throw e;
    }
    for (int i = data.length - offset; i < data.length; i++) {
      data[i] = fillValue;
    }
    getNMissingData();
  }

  /**
   * Set the data rate. This checks for change of rate
   *
   * @param r The rate to set in Hz
   */
  public final void setRate(double r) {
    if (rate != r && rate != 0.) {
      Util.prt(" **** Rate changed in ZeroFilled Span rnow=" + r + " vs " + toString());
    }
    rate = r;
    msover2 = (int) (1. / rate * 1000. / 2.);
  }         // 1/2 of a bin width in  millis

  /**
   * Shift the buffer so that shiftLength seconds of data before the first data time in the list of
   * new blocks is in the buffer before the first time. That is the first time has shiftLen seconds
   * of data before the first sample in the new data.
   *
   * @param list The list of miniseed blocks to add to this span
   * @param begin beginning time
   * @param end ending time
   * @param shiftLength The amount of old data before first new data to leave in the buffer
   */
  public void shiftAdd(ArrayList<MiniSeed> list, long begin, long end, double shiftLength) {
    long first = begin;
    long last = end;
    MiniSeed ms;
    double rt = -1;
    if (list != null) {
      for (MiniSeed list1 : list) {
        if (list1.getNsamp() <= 0 && list1.getRate() <= 0) {
          continue;
        }
        if (list1.getTimeInMillis() < first) {
          first = list1.getTimeInMillis();
        }
        if (list1.getNextExpectedTimeInMillis() > last) {
          last = list1.getNextExpectedTimeInMillis();
        }
        if (rt > 0) {
          if (list1.getRate() > 0. && list1.getNsamp() > 0) {
            rt = list1.getRate();
            setRate(list1.getRate());
          }
        }
      }
    }
    if (dbg) {
      Util.prt("Starting Zero:" + toString() + " first=" + Util.ascdate(first) + " " + Util.asctime2(first) + " last=" + Util.ascdate(last) + " " + Util.asctime2(last));
    }
    if (end > lastData || last > lastData) {
      //if(this.getMillisAt(data.length-1) < last) {  // need to do a shift
      shiftTime.setTimeInMillis(((long) (Math.max(begin, first) - shiftLength * 1000))); // This leaves shiftLength at beginning of buffer
      if (dbg) {
        Util.prt("Shifting data to time " + Util.ascdate(shiftTime) + " " + Util.asctime2(shiftTime) + " " + toString());
      }
      //shiftTime.setTimeInMillis((long)(last+shiftLength*1000 - data.length/rate*1000)); // Time of the desired first sample
      shift(shiftTime);
      if (dbg) {
        Util.prt("Shift after " + toString());
      }
    }
    long endtime = getMillisAt(data.length - 1);
    if (dbg) {
      Util.prt("addShift: Load low overlap=" + (lastTime - first) + " past end="
              + (last - ((long) (start.getTimeInMillis() + nsamp / rate * 1000)))
              + " data before " + Util.ascdate(endtime) + " " + Util.asctime2(endtime) + " size=" + (list == null ? "Null" : list.size()));
    }
//    if(dbg) Util.prt("Add blocks from "+Util.asctime2(first)+" to "+Util.asctime2(last)+" nblks="+list.size());
    if (list != null) {
      for (int i = list.size() - 1; i >= 0; i--) {
        if (list.get(i).getNsamp() > 0 && list.get(i).getRate() > 0) {
          if (list.get(i).getNextExpectedTimeInMillis() < endtime) {
            addBlock(list.get(i));
            if (dbg) {
              Util.prt("ADD  block[" + i + "] " + list.get(i));
            }
          } else if (dbg) {
            Util.prt("SKIP block[" + i + "] " + list.get(i));
          }
        }
      }           // end for each block in list
    }
    runs.trim(start);
    getNMissingData();
    if (dbg) {
      Util.prt("Ending Zero:" + toString() + " first=" + Util.ascdate(first) + " " + Util.asctime2(first) + " last=" + Util.ascdate(last) + " " + Util.asctime2(last));
    }

  }

  public boolean addBlock(MiniSeed ms) {

    if (ms == null) {
      return false;
    }
    if (ms.getRate() == 0. || ms.getNsamp() == 0) {
      return false;
    }
    int offset = (int) ((ms.getGregorianCalendar().getTimeInMillis()
            - start.getTimeInMillis() + msover2) * rate / 1000.);
    //long mod = (long)((ms.getGregorianCalendar().getTimeInMillis()-
    //   start.getTimeInMillis()+msover2)*rate) % 1000L;
    if (dbg) {
      Util.prt(Util.ascdate(start) + " " + Util.asctime(start) + " ms[0]="
              + Util.ascdate(ms.getGregorianCalendar()) + " " + Util.asctime(ms.getGregorianCalendar())
              + " offset=" + offset + " ns=" + ms.getNsamp());
    }

    // get the compression frames
    System.arraycopy(ms.getBuf(), ms.getDataOffset(), frames, 0, ms.getBlockSize() - ms.getDataOffset());
    if (ms.getEncoding() != 11 && ms.getEncoding() != 10) {
      boolean skip = false;
      for (int ith = 0; ith < ms.getNBlockettes(); ith++) {
        if (ms.getBlocketteType(ith) == 201) {
          skip = true;     // its a Murdock Hutt, skip it
        }
      }
      if (!skip) {
        Util.prt("ZeroFilledSpan: Cannot decode - not Steim I or II type=" + ms.getEncoding());
        decodeClean = false;
        Util.prt(ms.toString());
      }
      return false;
    }
    try {

      int[] samples = null;
      int reverse = 0;
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
      //reverse = samples[samples.length-1];
      // if the offset calculated is negative, shorten the transfer to beginning
      //Util.prt("offset="+offset+" ms.nsamp="+ms.getNsamp()+" bufsiz="+nsamp);
      int ns = 0;
      if (samples != null) {
        if (offset < 0) {
          if (ms.getNsamp() + offset - 1 > 0) {
            ns = Math.min(ms.getNsamp() + offset - 1, nsamp);
            System.arraycopy(samples, -offset + 1, data, 0, ns);
          } else {
            ns = 0;
          }
        } else if (nsamp - offset >= 0) {
          ns = Math.min(ms.getNsamp(), nsamp - offset);
          System.arraycopy(samples, 0, data, offset, ns);
        } else {
          ns = 0;
        }
        if (ms.getNextExpectedTimeInMillis() > lastTime) {
          lastTime = ms.getNextExpectedTimeInMillis();
        }
      }
      runs.add(ms);
      if (ns != ms.getNsamp() && dbg) {
        Util.prt("  * Short load of block ns=" + ns + " len=" + nsamp + " off=" + offset + " ms.nsamp=" + ms.getNsamp());
      }
      return (ns == ms.getNsamp());
    } catch (SteimException e) {
      Util.prt("block gave steim decode error. " + e.getMessage() + " " + ms);
      decodeClean = false;
    }
    return false;
  }

  public StringBuilder testRunsList() {
    boolean[] ok = new boolean[runs.size()];
    boolean inGap = false;
    StringBuilder sb = new StringBuilder(20000);
    int ngap = 0;
    int ndata = 0;
    int gapStart;
    int runStart = 0;
    GregorianCalendar gapTime = new GregorianCalendar();
    for (int i = 0; i < data.length; i++) {
      if (data[i] == fillValue) {
        if (inGap) {
          ngap++;
        } else {
          inGap = true;
          ngap = 0;
          gapStart = i;
          // Have the start of a gap, calculate times and look for a run
          long st = getGregorianCalendarAt(runStart, gapTime).getTimeInMillis();
          long end = getGregorianCalendarAt(gapStart).getTimeInMillis();
          for (int j = 0; j < runs.size(); i++) {
            sb.append("j=").append(i).append(" stdiff=").append(runs.get(i).getStart() - st).
                    append(" enddiff=").append(runs.get(i).getEnd() - end).
                    append(runs.get(j).toString()).append("\n");
            if (Math.abs(runs.get(i).getStart() - st) < msover2
                    && Math.abs(runs.get(i).getEnd() - end) < msover2) {
              ok[j] = true;
            }
          }
        }
      } else {
        if (!inGap) {
          inGap = false;
          runStart = i;
          ndata = 0;
        }
        ndata++;
      }
    }
    // Are all of the runs o.k.
    boolean allOk = true;
    for (int i = 0; i < runs.size(); i++) {
      sb.append(i).append(" ").append(runs.get(i)).append(" ").append(ok[i] ? " O.K." : " *** NOT FOUND ***").append("\n");
      if (!ok[i]) {
        allOk = false;
      }
    }
    if (!allOk) {
      sb.append("  TestRuns :  are NOT RIGHT.\n").append(sb.toString());
      return sb;
    } else {
      return null;
    }
  }
}
