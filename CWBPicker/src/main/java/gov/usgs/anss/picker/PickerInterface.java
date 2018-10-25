/*
 *  This software is in the public domain because it contains materials 
 *  that originally came from the United States Geological Survey, 
 *  an agency of the United States Department of Interior. For more 
 *  information, see the official USGS copyright policy at 
 *  http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.picker;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.GregorianCalendar;

/**
 * All pickers need to implement this class. The interface is used by CWBPicker
 * to cause the picker to be called with time series data via apply() and to be
 * reset on gap with clearMemory(). The writeState() is use to add picker
 * specific information to the state file.
 *
 * @author U.S. Geological Survey  <ketchum at usgs.gov>
 */
public interface PickerInterface {

  /**
   * This method builds a StringBuilder of the state file parameters specific to
   * the extending picker and then calls the parent writeState(StringBUilder) to
   * actually combine and write the state file. The writestate()
   *
   * @throws FileNotFoundException
   * @throws IOException
   */
  abstract void writestate() throws FileNotFoundException, IOException;

  /**
   * this will be called when a gap in the data is detected to allow the picker
   * to reset its history or other parameters.
   */
  abstract void clearMemory();

  /**
   * The parent thread is terminating. This routine allows the actual picker a
   * chance to clean up
   *
   */
  abstract void terminate();

  /**
   *
   * @param time
   * @param rate Data rate in hertz
   * @param rawdata Raw data as integers, this array can be modified in the
   * implementation
   * @param npts The number of points in rawdata that are valid
   */
  abstract void apply(GregorianCalendar time, double rate, int[][] rawdata, int npts);

  /**
   * The CWBPicker handles many arguments from the state file itself. These are
   * : Title, Starttime, Endtime, NSCL, CWBipaddress,CWBport Blocksize, Agency,
   * Author and PickDBServer Each picker can have addition data specific to that
   * picker. The extending class needs to define these are read the in setArgs()
   * and provide them as a StringBuilder in the writestate() method when it
   * calls writeStateFile of the parent class. The user can use the static
   * statesb variable to make the state, but needs to synchronize its usage.
   * Example :
   * <PRE>
   *   @Override
   *  protected final void writestate() throws FileNotFoundException, IOException {
   *    synchronized(statesb) {
   *      Util.clear(statesb);
   *       statesb.append("filterWindow# ").append(Util.df24(filterWindow)).append("\n");
   *      .  //write out all of the filter specific key#value pairs.
   *      writeStateFile(statesb);
   *   }
   * }
   * </PRE>
   *
   * @param s String with all of the state file parameters not handled by
   * CWBPicker - i.e. those for the specific picker
   * @throws FileNotFoundException
   * @throws IOException
   */
  abstract void setArgs(String s) throws FileNotFoundException, IOException;

  abstract long getWarmupMS();

  abstract boolean isReady();
}
