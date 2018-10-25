/*
 *  This software is in the public domain because it contains materials 
 *  that originally came from the United States Geological Survey, 
 *  an agency of the United States Department of Interior. For more 
 *  information, see the official USGS copyright policy at 
 *  http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.picker;

import gov.usgs.anss.utility.MakeRawToMiniseed;
import gov.usgs.anss.edgethread.EdgeThread;
//import gov.usgs.anss.edgeoutput.GlobalPickSender;
//import gov.usgs.anss.edgeoutput.JSONPickGenerator;
import gov.usgs.detectionformats.Pick;
import java.util.GregorianCalendar;

/**
 * This is the interface that the CWBPicker (or any later supplier of data to a
 * picker) uses. It allow the pickers themselves to be free of code related to
 * reporting picks, saving state, making files, etc.
 *
 * @author U.S. Geological Survey  <ketchum at usgs.gov>
 */
public interface PickerCallback {

  abstract void pick(String pickerName, GregorianCalendar picktime,
          double amplitude, double period,
          String phase, double error_window,
          String polarity, String onset,
          double hipass, double lowpass, int backasm, double slow, double snr);

  abstract MakeRawToMiniseed getMaker();

  abstract Pick getJSON();

  //abstract GlobalPickSender getGlobalPickSender();
  abstract void writeStateFile(StringBuilder sb);

  abstract EdgeThread getEdgeThread();

  abstract double getRate();

  abstract StringBuilder getNSCL();

  abstract String getTitle();

  abstract boolean getMakeFiles();

  abstract GregorianCalendar getBeginTime();
}
