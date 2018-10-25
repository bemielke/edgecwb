/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.tsplot;

import gov.usgs.anss.seed.MiniSeed;
import gov.usgs.anss.seed.SteimException;
import gov.usgs.anss.util.Util;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.GregorianCalendar;

/*
 * OffsetSpan.java
 *
 * Created on July 10, 2006, 9:31 AM
 * By Jeremy Powell
 *
 * This class represents a time series chunk of data which shows the spacing if 
 * data is missing, or offsets the graph if data is repeated.  
 */
public final class OffsetSpan {

  private int nsamp = 0;              // number of data 
  private GregorianCalendar start;    // the starting time of the first miniseed
  private GregorianCalendar end;      // the ending time of the last miniseed
  private double rate = 0.;
  private boolean dbg = false;
  private String seedName;            // the name of the seed 
  private ArrayList<ContSpanData> csdSpans;         // list of the csdSpans, ordered by start time
  // earliest start time comes first

  /**
   * string represting this time series
   *
   * @return a String with nsamp, rate and start date/time
   */
  @Override
  public String toString() {
    return "Span: n=" + seedName + " ns=" + nsamp + " rt=" + rate + " "
            + Util.ascdate(start) + " " + Util.asctime(start);
  }

  /**
   * get the name of the seed this span represents
   *
   * @return - String containing the name of the seed
   */
  public String getSeedName() {
    return seedName;
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
   * get the time series as an arry of ints
   *
   * @return The timeseries as an array of ints
   */
  public ArrayList<ContSpanData> getData() {
    return csdSpans;
  }

  /**
   * get number of data samples in timeseries (many might be zeros)
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

  public GregorianCalendar getEnd() {
    return end;
  }

  /**
   * Creates a new instance of OffsetSpan
   *
   * @param list A list containing Mini-seed objects to put in this series
   */
  public OffsetSpan(ArrayList<MiniSeed> list) {
    Collections.sort(list);
    MiniSeed ms = (MiniSeed) list.get(0);
    MiniSeed msend = (MiniSeed) list.get(list.size() - 1);
    double duration = (msend.getGregorianCalendar().getTimeInMillis()
            - ms.getGregorianCalendar().getTimeInMillis()) / 1000. + msend.getNsamp() / msend.getRate();
    doOffsetSpan(list, ms.getGregorianCalendar(), duration);
  }

  /**
   * Creates a new instance of OffsetSpan
   *
   * @param list A list containing Mini-seed objects to put in this series
   * @param trim The start time - data before this time are discarded
   * @param duration Time in seconds that this series is to represent
   */
  public OffsetSpan(ArrayList<MiniSeed> list, GregorianCalendar trim, double duration) {
    doOffsetSpan(list, trim, duration);
  }

  private void doOffsetSpan(ArrayList<MiniSeed> list, GregorianCalendar trim, double duration) {
    // initialize list of contSpanData, make it only have one b/c we don't know if 
    // we need more than one
    csdSpans = new ArrayList<ContSpanData>(1);

    MiniSeed ms = (MiniSeed) list.get(0);
    seedName = ms.getSeedNameString();

    rate = ms.getRate();
    int begoffset = (int) ((trim.getTimeInMillis() - ms.getGregorianCalendar().getTimeInMillis())
            * rate / 1000. + 0.01);
    if (dbg) {
      Util.prt(Util.ascdate(trim) + " " + Util.asctime(trim) + " start="
              + Util.ascdate(ms.getGregorianCalendar()) + " " + Util.asctime(ms.getGregorianCalendar()));
    }
    // The start time of this span is the time of first sample from first ms after 
    // the trim start time
    start = new GregorianCalendar();
    start.setTimeInMillis(ms.getGregorianCalendar().getTimeInMillis());
    start.add(Calendar.MILLISECOND, (int) (begoffset / rate * 1000.));// first in trimmed interval
    if (dbg) {
      Util.prt(Util.ascdate(start) + " " + Util.asctime(start) + " begoff=" + begoffset);
    }
    MiniSeed msend = (MiniSeed) list.get(list.size() - 1);
    int numSamp = (int) (duration * ms.getRate());

    int count = 0;
    for (int i = 0; i < list.size(); i++) {
      ms = (MiniSeed) list.get(i);
      int offset = (int) ((ms.getGregorianCalendar().getTimeInMillis()
              - start.getTimeInMillis()) * rate / 1000. + 0.05);
      count += ms.getNsamp();

      // get the compression frames
      if (ms.getEncoding() != 11 && ms.getEncoding() != 10) {
        boolean skip = false;
        for (int ith = 0; ith < ms.getNBlockettes(); ith++) {
          if (ms.getBlocketteType(ith) == 201) {
            skip = true;     // its a Murdock Hutt, skip it
          }
        }
        if (!skip) {
          Util.prt("Span: Cannot decode - not Steim I or II type=" + ms.getEncoding() + " blk=" + i);
          Util.prt(ms.toString());
        }
        continue;
      }
      int reverse = 0;
      try {
        int[] samples = ms.decomp();
        if (samples != null) {
          // find the start/end time of the current miniseed
          GregorianCalendar msStart = ms.getGregorianCalendar();
          // *1000 to convert to millisec
          long durInMillisec = (long) (ms.getNsamp() / ms.getRate() * 1000);
          GregorianCalendar msEnd = new GregorianCalendar();
          msEnd.setTimeInMillis(msStart.getTimeInMillis() + durInMillisec);

          if (csdSpans.isEmpty()) {  // first CSD, so just create and add it
            ContSpanData csd = new ContSpanData(samples, msStart, msEnd, numSamp);
            csdSpans.add(csd);
          } else {   // otherwise check the other CSD's and see where the new data goes
            // flag to see if we found a spot for the new MS or not
            boolean foundSpot = false;

            for (int j = 0; j < csdSpans.size(); ++j) {
              ContSpanData csd = (ContSpanData) csdSpans.get(j);

              // see if this miniseed comes directly AFTER an existing CSD
              // end of csd = start of this ms 
              if (Math.abs(csd.getEnd().getTimeInMillis() - msStart.getTimeInMillis()) <= 1) {
                csd.addToTailOfData(samples, msEnd);
                foundSpot = true;
                break;
              } // see if this miniseed comes directly BEFORE an existing CSD
              // begin of csd = end of this ms 
              else if (Math.abs(csd.getStart().getTimeInMillis() - msEnd.getTimeInMillis()) <= 1) {
                csd.addToHeadOfData(samples, msStart);
                foundSpot = true;
                break;
              }
            }
            if (!foundSpot) {
              // the MS is inside a csd, or far outside... so just create new csd
              csdSpans.add(new ContSpanData(samples, msStart, msEnd, samples.length));
            }
          }
        }
      } catch (SteimException e) {
        Util.prta("Got a steim exception e=" + e);
      }

    }           // end for each block in list

    // set the end time for the CSD
    if (csdSpans.size() > 0) {
      end = ((ContSpanData) csdSpans.get(csdSpans.size() - 1)).getEnd();
    }

    for (int i = 0; i < csdSpans.size(); ++i) {
      ContSpanData csd = (ContSpanData) csdSpans.get(i);
      nsamp += csd.getDataLen();   // find how much data is in the csd's
    }
  }
}
