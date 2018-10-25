/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.tsplot;

import java.util.GregorianCalendar;
import gov.usgs.anss.util.Util;

/*
 * ContSpanData.java
 * Continuous Span Data
 *
 * Created on July 10, 2006, 10:07 AM
 * By Jeremy Powell
 *
 * A data structure that contains the data of a time series which are continuous.
 * Contains a start and end time of the data.
 */
public final class ContSpanData {

  private GregorianCalendar start;       // the start and end times of the data
  private GregorianCalendar end;
  private int[] data;
  private int numData;

  @Override
  public String toString() {
    return Util.ascdate(start) + " " + Util.asctime2(start) + "-" + Util.asctime2(end);
  }

  /**
   * Creates a new instance of ContSpanData
   *
   * @param s The start time of segment
   * @param d The data array with ints
   * @param e The end time of the data
   * @param numSamp the number of samples of data
   */
  public ContSpanData(int[] d, GregorianCalendar s, GregorianCalendar e, int numSamp) {
    data = new int[numSamp];   // make it large enough to hold every data
    System.arraycopy(d, 0, data, 0, d.length);
    numData = d.length;
    start = s;
    end = e;
  }

  /**
   * Returns the full set of data in this continuous span data object
   *
   * @return an array of integers representing a timeseries
   */
  public int[] getData() {
    return data;
  }

  /**
   * Returns a single value of data in this continuous span data object located at index
   *
   * @param index - the index of the value
   * @return a single value of data in the timeseries
   */
  public int getData(int index) {
    return data[index];
  }

  /**
   * Returns the number of data points in the continuous timeseries
   *
   * @return The number of samples
   */
  public int getDataLen() {
    return numData;
  }

  /**
   * Returns the start time/date of the timeseries
   *
   * @return start time of data
   */
  public GregorianCalendar getStart() {
    return start;
  }

  /**
   * Returns the end time/date of the timeseries
   *
   * @return The end time of the data
   */
  public GregorianCalendar getEnd() {
    return end;
  }

  /**
   * Add an array of data to the end of the current timeseries.
   *
   * @param newData - an array containing the new data that is being added to the end of the
   * contSpanData obj
   * @param e - the ending date/time for the data being added to the tail
   */
  public void addToTailOfData(int[] newData, GregorianCalendar e) {
    // check to see if new data can fit inside current array
    if (newData.length + numData >= data.length) {
      // not big enough, increase size: taken from array list
      // (oldSize*3)/2 + 1   dont change something that works, right?
      int size = (data.length * 3) / 2 + 1;
      if (size < newData.length + numData) {
        size = newData.length + numData;
      }
      int[] tmp = new int[size];
      // add old data to begining of temp array
      System.arraycopy(data, 0, tmp, 0, data.length);

      // set objects data to the temp array
      data = tmp;
    }
    // add new data to end of old data
    System.arraycopy(newData, 0, data, numData, newData.length);
    end = e; // set the end date/time
    numData += newData.length;   // set the number of data
  }

  /**
   * Add an array of data to the begining of the current timeseries.
   *
   * @param newData - an array containing the new data that is being added to the begining of the
   * contSpanData obj
   * @param s - the GregorianCalendar object that is the starting date/time for the data being added
   * to the head of the data
   */
  public void addToHeadOfData(int[] newData, GregorianCalendar s) {
    int[] tmp = new int[newData.length + data.length];
    // add new data to begining of temp array
    System.arraycopy(newData, 0, tmp, 0, newData.length);
    // add old data to end of new data in temp array
    System.arraycopy(data, 0, tmp, newData.length, data.length);

    // set objects data to the temp array
    data = tmp;
    start = s;   // set the start date/time
  }
}
