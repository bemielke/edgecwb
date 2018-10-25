/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.tsplot;

import gov.usgs.anss.seed.MiniSeed;
import java.util.ArrayList;
import java.util.GregorianCalendar;

/*
 * MSGroup.java
 *
 * Created on July 6, 2006, 2:33 PM
 * By Jeremy Powell
 *
 * This class contains the all data which corresponds to a single survey site, so 
 * it's one graph.  This class contains an array list of break times and values for 
 * non-continuous data.  The breakValues correspond to the what index in the data
 * the values are no longer continuous.  The breakTimes correspond to the begining 
 * times of the non-continuous data.
 */
public class MSGroup {

  protected int[] data;                 // array of data for this MSGroup
  protected GregorianCalendar start;    // the start time of the first miniseed
  protected GregorianCalendar end;      // the end time of the last miniseed
  protected double rate;                // the rate of the data
  protected String name;                // the seed name of the MS data
  protected int[] breakValues;          // values where the data is broken
  protected long[] breakTimes;          // begining times of the non continuous data

  public MSGroup() {
    // do nothing
  }

  /**
   * Creates a new instance of MSGroup Takes an arrayList of blocks of miniseeds and converts them
   * into a single array of data, creating a time series where each value can be gotten.
   *
   * @param blk - the arrayList of miniseeds that has the data
   */
  public MSGroup(ArrayList<MiniSeed> blk) {
    // the offsetSpan object that extracts the data
    OffsetSpan span = new OffsetSpan(blk);

    // collect the data from the offset span
    name = span.getSeedName();
    start = span.getStart();
    end = span.getEnd();
    rate = span.getRate();
    ArrayList<ContSpanData> csdSpans = span.getData();

    // build data
    int numSpans = csdSpans.size();
    int arrayLen = 0;
    breakValues = new int[numSpans];   // initialize the breakValues/Times arrays
    breakTimes = new long[numSpans];

    // loop through csdSpans and get the total size
    for (int i = 0; i < numSpans; ++i) {
      arrayLen += ((ContSpanData) csdSpans.get(i)).getDataLen();
    }

    // combine all the data into one array to return
    data = new int[arrayLen];
    int placeHolder = 0;
    breakValues[0] = 0;
    breakTimes[0] = ((ContSpanData) csdSpans.get(0)).getStart().getTimeInMillis();
    for (int i = 0; i < numSpans; ++i) {
      ContSpanData csd = (ContSpanData) csdSpans.get(i);
      System.arraycopy(csd.getData(), 0, data, placeHolder, csd.getDataLen());
      placeHolder += csd.getDataLen();
      // get next csd's start time, but dont want to get next if on last csd
      if (i != numSpans - 1) {
        breakValues[i + 1] = placeHolder;
        breakTimes[i + 1] = ((ContSpanData) csdSpans.get(i + 1)).getStart().getTimeInMillis();
      }
    }

    // reset span and csdSpans arraylist to null to free up mem since we no long need it
    csdSpans.clear();
  }

  /**
   * Returns the entire array(int) of the groups data
   *
   * @return
   */
  public int[] getAllData() {
    return data;
  }

  /**
   * Returns the data at a specified index i
   *
   * @param i - the index of the data the user wants, must be less that data.length
   * @return
   */
  public int getData(int i) {
    assert (i < data.length);
    return data[i];
  }

  /**
   * Returns the start time of the MSGroup object
   *
   * @return
   */
  public GregorianCalendar getStartTime() {
    return start;
  }

  /**
   * Returns the end time of the MSGroup object
   *
   * @return
   */
  public GregorianCalendar getEndTime() {
    return end;
  }

  /**
   * Returns the duration of the MiniSeedGroup in milliseconds
   *
   * @return
   */
  public long getDuration() {
    return (end.getTimeInMillis() - start.getTimeInMillis());
  }

  /**
   * Returns the rate of the MSGroup obj. (really the rate of the first miniseed, but that value
   * will not change across the other miniseeds by much
   *
   * @return
   */
  public double getRate() {
    return rate;
  }

  /**
   * Returns the number of data that this MSGroup is holding
   *
   * @return
   */
  public int getNumData() {
    return data.length;
  }

  /**
   * Returns the name of the seed/site that the MSGroup obj represents
   *
   * @return
   */
  public String getSeedName() {
    return name;
  }

  /**
   * returns an array with the integer values (indexes) of where the data is not continuous Index i
   * corresponds to index i of the breakTimes array
   *
   * @return
   */
  public int[] getBreakValues() {
    return breakValues;
  }

  /**
   * Returns an array with the time (in milliseconds) of the begining times of the noncontinuous
   * data Index i corresponds to index i of the breakValues array
   *
   * @return
   */
  public long[] getBreakTimes() {
    return breakTimes;
  }
}
