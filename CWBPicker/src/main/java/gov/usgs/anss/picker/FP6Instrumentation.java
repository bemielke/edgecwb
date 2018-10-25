/*
 *  This software is in the public domain because it contains materials 
 *  that originally came from the United States Geological Survey, 
 *  an agency of the United States Department of Interior. For more 
 *  information, see the official USGS copyright policy at 
 *  http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.picker;

import java.util.ArrayList;
import java.util.GregorianCalendar;
import net.alomax.timedom.PickData;
import net.alomax.timedom.fp6.USGSFP6Extension;

/** This interface allows instrumentation of the internals of the FilterPicker6 code.  It
 * provide the interface such that any implementer of this class can get callbacks from the
 * FP6 class after each apply and if any picks are found.
 *
 * @author U.S. Geological Survey  <ketchum at usgs.gov>
 */
public interface FP6Instrumentation {
  /** this will be called each time picks are generated.  NOte the ArrayList of picks is actually
   * of type FilterPicker6PickData which contains more data than the parent PickData.  
   * 
   * @param picker This is the USGSFP6Extension that subclasses the FitlerPicker6 class to allow access to internals
   * @param nscl The NNSSSSSCCCLL for the channel
   * @param picks This is actually a ArrayList of FilterPicker6PickData which contains more data than a PickData
   * @param time The time of the first sample of the timeseries that triggered this pick, time calculations are relative to this
   */
  abstract void doPicks(USGSFP6Extension picker, String nscl, ArrayList<PickData> picks, GregorianCalendar time);
  /** this will be called every blocksize so that internal status of the picker can be examined
   * 
   * @param picker This is the USGSFP6Extension that subclasses the FitlerPicker6 class to allow access to internals
   * @param nscl The NNSSSSSCCCLL for the channel
   * @param time The time of the first sample of the timeseries that triggered this pick, time calculations are relative to this
   * @param npts Number of data points in data
   * @param data The data points submitted to the FP6
   * @param rate The data rate in Hz
   * @param makefiles Flag on whether the Picker has been set to make data files from internals.
   */
  abstract void doApply(USGSFP6Extension picker, String nscl, GregorianCalendar time, 
          int npts, int [] data, double rate,  boolean makefiles);

}
