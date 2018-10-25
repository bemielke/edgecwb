/*
 *  This software is in the public domain because it contains materials 
 *  that originally came from the United States Geological Survey, 
 *  an agency of the United States Department of Interior. For more 
 *  information, see the official USGS copyright policy at 
 *  http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.picker;

import gov.usgs.anss.util.Util;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import net.alomax.timedom.PickData;
import net.alomax.timedom.fp6.FilterPicker6PickData;
import net.alomax.timedom.fp6.USGSFP6Extension;

/**
 * This is a sample instrumentation class DCK wrote which prints out picks in
 * more detail and creates full time series of the characteristic function
 * values. Many other parameters from the FP6 could be written out as well.
 *
 * @author U.S. Geological Survey  <ketchum at usgs.gov>
 */
public class FP6InstrumentationDCK implements FP6Instrumentation {

  private final String[] numbers = {"0", "1", "2", "3", "4", "5", "6", "7", "8", "9"};
  private final StringBuilder tmpsb = new StringBuilder(100);
  private final StringBuilder nsclsb = new StringBuilder(12);
  int numberofpicks;
  private final double[] bandTemp1 = new double[2];
  private final double[] bandTemp2 = new double[2];

  // Pick related variables
  private float pickoffset;
  private float pickerror;
  private final GregorianCalendar picktime = new GregorianCalendar();

  @Override
  public void doApply(USGSFP6Extension picker, String nscl, GregorianCalendar time,
          int npts, int[] data, double rate, boolean makefiles) {
    int nbands = picker.getNumBands();
    int[] bands = picker.getIndexBandTrigger();
    double[] charf = picker.getCharFuncValueMaxInUpEventWindow();
    Util.clear(tmpsb);
    boolean print = false;
    for (int i = 0; i < nbands; i++) {
      if (bands[i] != -1.) {
        print = true;
      }
      tmpsb.append(bands[i]).append(" ");
    }
    for (int i = 0; i < nbands; i++) {
      if (charf[i] > 0.) {
        print = true;
      }
      tmpsb.append(Util.ef6(charf[i])).append("  ");
    }
    if (print) {
      picker.prt(tmpsb);
    }
    if (makefiles) {
      float[][] charFunct = picker.getBandCharFunc();
      int secs = (int) (time.getTimeInMillis() % 86400000L) / 1000;
      int usec = (int) (time.getTimeInMillis() % 1000) * 1000;
      picker.getCWBFilterPicker6().getMaker().loadTSIncrement(
              nscl, npts, data, time.get(Calendar.YEAR), time.get(Calendar.DAY_OF_YEAR),
              secs, usec, rate, 0, 0, 0, 0);

      // for each band write out the values of the charFunction for the npts
      for (int i = 0; i < nbands; i++) {
        nsclsb.replace(7, 9, "CF");
        nsclsb.replace(9, 10, numbers[i]);
        for (int j = 0; j < npts; j++) {
          data[j] = (int) (charFunct[i][j] * 100. + 0.5);
        }
        picker.getCWBFilterPicker6().maker.loadTSIncrement(
                nsclsb, npts, data, time.get(Calendar.YEAR), time.get(Calendar.DAY_OF_YEAR),
                secs, usec, rate, 0, 0, 0, 0);
      }
    }
  }

  @Override
  public void doPicks(USGSFP6Extension picker, String nscl, ArrayList<PickData> picks, GregorianCalendar time) {
    numberofpicks = numberofpicks + picks.size();
    double rate = picker.getCWBFilterPicker6().getRate();
    for (int i = 0; i < picks.size(); i++) {
      FilterPicker6PickData pk = (FilterPicker6PickData) picks.get(i);
      boolean[] isTriggered = picker.isTriggered(pk, tmpsb);
      int[] bandIndices = picker.getBandWidthTriggered(isTriggered);
      picker.getBandCorners(bandIndices[0], bandTemp1);
      picker.getBandCorners(bandIndices[1], bandTemp2);
      double lowFreq = Math.min(bandTemp1[0], bandTemp2[0]);
      double highFreq = Math.max(bandTemp1[1], bandTemp2[1]);

      pickoffset = (float) ((pk.indices[0] + pk.indices[1]) / 2);
      pickerror = (float) ((pk.indices[1] - pk.indices[0]) / rate);
      picktime.setTimeInMillis(time.getTimeInMillis());
      picktime.add(Calendar.MILLISECOND, (int) (1000 * pickoffset / rate));
      if (tmpsb.length() > 0) {
        picker.prta(tmpsb);
      }
      picker.prta(Util.clear(tmpsb).append(i).append(" ").append(nscl).append(" ").append(Util.ascdatetime2(picktime)).
              append(" ").append(pickerror).append(" " + " polar=").append(pk.polarity).
              append(" indices 1=").append(pk.indices[0]).append(" 2=").append(pk.indices[1]).
              append(" amp=").append(Util.rightPad(Util.df22(pk.amplitude), 6)).append(" units=").append(pk.amplitudeUnits).
              append(" per=").append(Util.rightPad("" + pk.period, 6)).append(" ").
              append(Util.df23(lowFreq)).append("-").append(Util.df23(highFreq)));
      // Send pick to picker system.
      //pick(pickerType, picktime, pk.amplitude, pk.period, "P", pickerror,
      //        (pk.polarity== PickData.POLARITY_POS?"up":(pk.polarity==PickData.POLARITY_NEG?"down":null)),
      //        null, lowFreq, highFreq, -1, -1., 0.);
    }
  }
}
