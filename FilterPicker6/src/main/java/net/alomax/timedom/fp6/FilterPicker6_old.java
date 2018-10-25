/*
 * This file is part of the Anthony Lomax Java Library.
 *
 * Copyright (C) 2006-2015 Anthony Lomax <anthony@alomax.net www.alomax.net>
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
package net.alomax.timedom.fp6;

import java.util.StringTokenizer;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.alomax.math.TimeSeries;
import net.alomax.timedom.BasicPicker;
import net.alomax.timedom.PickData;
import net.alomax.timedom.TimeDomainException;
import net.alomax.timedom.TimeDomainMemory;
import net.alomax.timedom.TimeDomainText;
import net.alomax.timedom.fp6.FilterPicker6_BandParameters.DisplayMode;
import net.alomax.util.NumberFormat;

// _DOC_ =============================
// _DOC_ FilterPicker6_old
// _DOC_ =============================
/**
 *
 * The FP6 algorithm processes a set of bands with center periods Tn and bandwidth Wb given by the ratio Tn/Tn-1. For each filter band the data
 * samples are processed through a Butterworth bandpass filter, and picking statistics and a characteristic function (CF) are generated. Picks are
 * triggered and declared based on analysis of the maximum of the CF values over all filter bands. This analysis involves the level and integrals of
 * the maximum CF within tUpEvent and tUpEventMin, and uses the threshold values threshold1 and threshold2.
 * <p>
 * For details on the FP6 algorithm see net/alomax/timedom/fp6/doc/FilterPicker6_algorithm.pdf and net/alomax/timedom/fp6/*.java source files.
 * <p>
 Declared picks are stored in an ArrayList of FilterPicker6PickData objects, public ArrayList FilterPicker6_old.getPickData() should be called after
 each call to FilterPicker6_old.apply() to recover new picks.
 <p>
 FilterPicker6_old uses an automatically created memory object FilterPicker6_Memory to allow support re-entry to picker for contiguous data samples from
 the same channel. Consequently, an instance of FilterPicker6_old can only be used for contiguous data from a single channel. If there is a gap in the
 data, FilterPicker6_old.initializeMemory() should be invoked before the next call to FilterPicker6_old.apply()
 <p>
 Pseudocode example of how FilterPicker6_old can be used:
 <p>
 * <blockquote><pre>
 // Create a FilterPicker6_old object:
 // initialize: String localeText, double longTermWindowFactor,
 //     double threshold1, double threshold2,
 //     double tUpEvent, double tUpEventMin
 ...
 // EITHER:
 // construct a FilterPicker6_old object  with a filterWindow ...
 // initialize: double filterWindow
 ...
 FilterPicker6_old filterPicker6 = new FilterPicker6_old(locale, longTermWindowFactor, threshold1, threshold2, tUpEvent, tUpEventMin, filterWindow);
 // OR:
 // construct a FilterPicker6_old object  with a filterWindow
 // initialize: FilterPicker6_BandParameters[] bandParameters
 ...
 FilterPicker6_old filterPicker6 = new FilterPicker6_old(locale, bandParameters, longTermWindowFactor, threshold1, threshold2, tUpEvent, tUpEventMin, filterWindow);

 // check settings:
 try {
 filterPicker6.checkSettings();
 } catch (TimeDomainException fe) {
 throw (fe);
 }

 // apply picker to one or more trace segments:
 // set double dt
 ...
 // loop over contiguous data trace segments
 float[] sample;
 while ((sample = nextSample() != null) {
 // apply
 try {
 float[] modified_sample = filterPicker6.apply(dt, sample);
 } catch (TimeDomainException tde) {
 ...
 }
 // On the first call of apply(), a FilterPicker6_Memory object will be initialized and updated,
 //     on subsequent calls, the FilterPicker6_Memory object will be used and updated.
 // handle picks:
 ArrayList\<FilterPicker6PickData\> picks = (ArrayList\<FilterPicker6PickData\>) filterPicker6.getPickData();
 *          ...
 *          // clear pick array
 *          filterPicker6.clearTriggerPickData();
 *      }
 * </pre></blockquote>
 * <p>
 For examples of how FilterPicker6_old can be used through calls to its get and set methods and in an interactive environment, see
 net.alomax.seisgram2k.toolmanager.FilterPicker6ToolManager.java.
 <p>
 *
 *
 * @author anthony
 */
public class FilterPicker6_old extends BasicPicker {

    /**
     *
     */
    public static final long serialVersionUID = 493587L;

    /**
     *
     */
    public static final double FILTER_WINDOW_DEFAULT = 10.0;

    /**
     *
     */
    public static final double FILTER_WINDOW_NOT_USED = Double.NEGATIVE_INFINITY;
    // 20150527 AJL

    /**
     *
     */
    public static final double LONG_TERM_WINDOW_FACTOR_DEFAULT = 30.0;

    /**
     *
     */
    public static final double THRESHOLD1_DEFAULT = 10.0;

    /**
     *
     */
    public static final double THRESHOLD2_DEFAULT = 8.0;

    /**
     *
     */
    public static final double TUPEVENT_DEFAULT = 5.0;
    //

    /**
     *
     */
    public static final double TUPEVENTMIN_DEFAULT = 1.0;
    //

    /**
     *
     */
    public static final double BANDWIDTH_FACTOR_DEFAULT = 1.25;

    /**
     *
     */
    public static final double FRACTION_PERIOD_PHASE_SHIFT_PER_2_POLES = 8.0;

    /**
     * Array of nBand filter band parameters (band periods and thresholdScaleFactor), set explicitly in the constructor or through the setBandParams()
     * method. Note that the ratio of each band period band to previous shorter band period must be approximately constant, see bandWidthFactor.
     */
    protected FilterPicker6_BandParameters[] bandParameters = new FilterPicker6_BandParameters[0];
    /**
     * // _DOC_ Filter window (filterWindow) in seconds sets the approximate longest filter band period when the bands are not explicitly set. The
     * filter window will be adjusted upwards to be an integer N power of 2 times the sample interval (deltaTime). Then nBand = N + 1 "filter bands"
     * are created, starting with start from bandWidthFactor * Nyquist period (2*deltaTime).
     */
    protected double filterWindow = FILTER_WINDOW_DEFAULT;

    /**
     * // _DOC_ The long term window factor (longTermWindowFactor) determines the decay constant of a simple recursive filter to accumulate/smooth
     * all picking statistics and characteristic functions for all filter bands.
     */
    protected double longTermWindowFactor = LONG_TERM_WINDOW_FACTOR_DEFAULT;
    /**
     * // _DOC_ threshold1 sets the threshold to trigger a pick event (potential pick). This threshold is reached when the (clipped) characteristic
     * function for any filter band nBand exceeds threshold1 * thresholdScaleFactor[nBand]. threshold1 is also used to confirm a pick, see
     * tUpEventMin.
     */
    protected double threshold1 = THRESHOLD1_DEFAULT;    // threshold to initiate a pick trigger
    /**
     * // _DOC_ threshold2 sets the threshold to declare a pick event (pick will be accepted when tUpEvent reached). This threshold is reached when
     * the integral of the maximum (clipped) characteristic function over all filter bands within the window tUpEvent exceeds threshold2 * tUpEvent
     * (i.e. the average (clipped) characteristic function over window tUpEvent is greater than threshold2).
     */
    protected double threshold2 = THRESHOLD2_DEFAULT;       // threshold to maintain trigger
    /**
     * // _DOC_ tUpEvent sets the maximum time window after threshold1 is reached (pick event triggered) in which the integral of the (clipped)
     * characteristic function is accumulated to declare a pick. A pick is declared if this integral exceeds threshold2 * tUpEvent.
     */
    protected double tUpEvent = TUPEVENT_DEFAULT;
    /**
     * // _DOC_ tUpEventMin sets a minimum time window after threshold1 is reached (pick event triggered) in which an integral of the (clipped)
     * characteristic function is accumulated to confirm a pick. A pick is confirmed if this integral exceeds threshold1 * tUpEventMin.
     */
    protected double tUpEventMin = TUPEVENTMIN_DEFAULT;

    /**
     * Band width factor use for initializing band pass filter. Bandwidth is measured at an amplitude of 0.707 of filter peak; must be approximately
     * ratio of each band period band to previous shorter band period.
     *
     * bandWidthFactor is set automatically if band parameters are initialized explicitly.
     */
    protected double bandWidthFactor = BANDWIDTH_FACTOR_DEFAULT;
    /**
     * number of poles for band-pass filter
     */
    protected int numPolesBandPass = 4;
    /**
     * number of filter bands
     */
    protected int numBands = 0;
    //
    // private fields for error checking
    private static final double LT_WINDOW_FACT_MIN = Double.MIN_VALUE;
    private static final double LT_WINDOW_FACT_MAX = Double.MAX_VALUE;
    private static final double FILTER_WINDOW_MIN = Double.MIN_VALUE;
    private static final double FILTER_WINDOW_MAX = Double.MAX_VALUE;
    private static final double THRESHOLD_MIN = Double.MIN_VALUE;
    private static final double THRESHOLD_MAX = Double.MAX_VALUE;
    private static final double TIME_UP_EVENT_MIN = Double.MIN_VALUE;
    private static final double TIME_UP_EVENT_MAX = Double.MAX_VALUE;
    private static final int INT_UNSET = -(Integer.MAX_VALUE / 2);
    //
    private static final double BAND_WIDTH_MIN = 1.1;
    private static final double BAND_WIDTH_MAX = 100.0;
    private static final double NUM_POLES_BANDPASS_MIN = 4;
    private static final double NUM_POLES_BANDPASS_MAX = 16;

    /**
     * memory structure to hold results of last pick pass on sequential data from same channel
     */
    // _DOC_ a memory structure/object is used so that this function can be called repeatedly for packets of data in sequence from the same channel.
    protected FilterPicker6_Memory fp6Memory = null;
    /**
     * sampling interval of data, not known until first call of method apply()
     */
    protected double deltaTime = -1.0;

    /**
     * Constructs a new FilterPicker6 object setting filter bands using the specified bandParameters.
     *
     * @param localeText locale for information, warning and error messages
     * @param bandParameters bands to use
     * @param longTermWindowFactor long term window factor
     * @param threshold1 threshold to trigger a pick event
     * @param threshold2 threshold to declare a pick
     * @param tUpEvent maximum time window after trigger for declaring a pick
     * @param tUpEventMin minimum time window after trigger for declaring a pick
   * @throws net.alomax.timedom.TimeDomainException
     */
    public FilterPicker6_old(String localeText, FilterPicker6_BandParameters[] bandParameters, double longTermWindowFactor, double threshold1, double threshold2,
            double tUpEvent, double tUpEventMin) throws TimeDomainException {

        super(localeText);

        // new, FilterPicker6_old data fields
        setBandParams(bandParameters);

        this.longTermWindowFactor = longTermWindowFactor;
        this.threshold1 = threshold1;
        this.threshold2 = threshold2;
        this.tUpEvent = tUpEvent;
        this.tUpEventMin = tUpEventMin;

        // cannot use both bandParameters and filterWindow parameters
        this.filterWindow = FILTER_WINDOW_NOT_USED;

    }

    /**
     * Constructs a new FilterPicker6 object setting filter bands automatically based on filterWindow and sample interval of data
     *
     * @param localeText locale for information, warning and error messages
     * @param longTermWindowFactor long term window factor
     * @param threshold1 threshold to trigger a pick event
     * @param threshold2 threshold to declare a pick
     * @param tUpEvent maximum time window after trigger for declaring a pick
     * @param tUpEventMin minimum time window after trigger for declaring a pick
     * @param filterWindow approximate longest filter band period
     */
    public FilterPicker6_old(String localeText, double longTermWindowFactor, double threshold1, double threshold2,
            double tUpEvent, double tUpEventMin, double filterWindow) {

        super(localeText);

        this.longTermWindowFactor = longTermWindowFactor;
        this.threshold1 = threshold1;
        this.threshold2 = threshold2;
        this.tUpEvent = tUpEvent;
        this.tUpEventMin = tUpEventMin;
        this.filterWindow = filterWindow;

        // cannot use both bandParameters and filterWindow parameters
        this.bandParameters = null;  // changing filterWindow will change picker parameters (numBands)

    }

    /**
     * Constructs a new FilterPicker6 object by copying a specified FilterPicker6 object
     *
     * @param tp a FilterPicker6 object
   * @throws net.alomax.timedom.TimeDomainException
     */
    public FilterPicker6_old(FilterPicker6_old tp) throws TimeDomainException {

        super();

        this.resultType = tp.resultType;

        setBandParams(tp.bandParameters);
        this.numBands = tp.numBands;
        this.longTermWindowFactor = tp.longTermWindowFactor;
        this.threshold1 = tp.threshold1;
        this.threshold2 = tp.threshold2;
        this.tUpEvent = tp.tUpEvent;
        this.tUpEventMin = tp.tUpEventMin;
        this.filterWindow = tp.filterWindow;

    }

    /**
     * Initializes FilterPicker6_Memory object for this picker instance. This method should be called if, for example, there is a gap in the data
     * since the last call to apply()
     *
     */
    @Override
    public void clearMemory() {

        fp6Memory = null;

    }

    @Override
    public TimeDomainMemory getMemory() {
        return (fp6Memory);
    }

    /**
     * Method to get prefix test for pick names
     *
     * @return triggerPickData prefix if process creates picks
     */
    @Override
    public String getPickPrefix() {

        return ("X");

    }

    /**
     * Returns bandParameters
     *
     * @return bandParameters
     */
    public FilterPicker6_BandParameters[] getBandParameters() {
        return bandParameters;
    }

    /**
     * Sets filter band parameters
     *
     * @param paramStr
     * @throws net.alomax.timedom.TimeDomainException
     */
    public void setBandParams(String paramStr) throws TimeDomainException {

        try {
            ArrayList<FilterPicker6_BandParameters> paramVect = new ArrayList<>();

            StringTokenizer strTzr0 = new StringTokenizer(paramStr, "#");
            // example: "0.05#0.1#0.2#0.4#0.8#1.6#3.2#6.4#12.8#25.6"
            while (strTzr0.hasMoreTokens()) {
                StringTokenizer strTzr = new StringTokenizer(strTzr0.nextToken(), "/");
                // check if period or frequency
                double period;
                DisplayMode displayMode;
                String bandStr = strTzr.nextToken();
                if (bandStr.toLowerCase().endsWith("hz")) {   // frequency
                    period = 1.0 / Double.parseDouble(bandStr.substring(0, bandStr.length() - 2));
                    displayMode = DisplayMode.DISPLAY_MODE_FREQ;
                } else if (bandStr.toLowerCase().endsWith("s")) { // period
                    period = Double.parseDouble(bandStr.substring(0, bandStr.length() - 1));
                    displayMode = DisplayMode.DISPLAY_MODE_PERIOD;
                } else {    // assume period with no units
                    period = Double.parseDouble(bandStr);
                    displayMode = DisplayMode.DISPLAY_MODE_PERIOD;
                }
                double thresholdScaleFactor = 1.0;
                if (strTzr.hasMoreTokens()) {
                    thresholdScaleFactor = Double.parseDouble(strTzr.nextToken());
                }
                // check for increasing period
                if (paramVect.size() > 1 && period <= paramVect.get(paramVect.size() - 1).period) {
                    throw new TimeDomainException(TimeDomainText.invalid_band_parameters + ": must be in order of increasing period: " + bandStr);
                }
                FilterPicker6_BandParameters params = new FilterPicker6_BandParameters(period, thresholdScaleFactor, displayMode);
                paramVect.add(params);
            }
            if (paramVect.isEmpty()) {
                this.bandParameters = null;
                return;
            }
            setBandParams(paramVect.toArray(new FilterPicker6_BandParameters[0]));
        } catch (NumberFormatException | TimeDomainException e) {
            throw new TimeDomainException(TimeDomainText.invalid_band_parameters + ": " + e);
        }

    }

    /**
     * Returns a String representation of the current band parameters
     *
     * @return band parameters string using period "s" display mode
     */
    public String getBandParamsString() {
        return (getBandParamsString(null));
    }

    /**
     * Returns a String representation of the current band parameters using the specified display mode (period "s" or frequency "Hz")
     *
     * @param displayMode DisplayMode.DISPLAY_MODE_FREQ or DisplayMode.DISPLAY_MODE_PERIOD
     * @return band parameters string
     */
    public String getBandParamsString(DisplayMode displayMode) {

        if (bandParameters == null) {
            return (null);
        }
        String params = "";
        boolean disp_freq;
        for (FilterPicker6_BandParameters param : bandParameters) {
            disp_freq = (displayMode != null && displayMode == DisplayMode.DISPLAY_MODE_FREQ) || param.displayMode == DisplayMode.DISPLAY_MODE_FREQ;
            if (disp_freq) {
                params += (float) (1.0 / param.period) + "Hz";
            } else {
                params += (float) param.period + "s";
            }
            params += "/";
            params += (float) param.thresholdScaleFactor;
            params += "#";
        }
        int ndx;
        if ((ndx = params.lastIndexOf('#')) >= 0) {
            params = params.substring(0, ndx);
        }

        return (params);

    }

    /**
     * Sets band parameters
     *
     * @param bandParameters band parameters array to set
   * @throws net.alomax.timedom.TimeDomainException */
    public final void setBandParams(FilterPicker6_BandParameters[] bandParameters) throws TimeDomainException {

        this.bandParameters = bandParameters;
        setBandWidthFactor(bandParameters);

    }

    /**
     * Returns current band parameters
     *
     * @return current band parameters array
     */
    public FilterPicker6_BandParameters[] getBandParams() {
        return bandParameters;
    }

    /**
     * Returns current number of bands
     *
     * @return number of bands
     */
    public int getNumBands() {
        return numBands;
    }

    /**
     * Sets bandWidthFactor
     *
     * @param bandParameters band parameters whose period ratios will determine bandWidthFactor
     * @throws TimeDomainException
     */
    public void setBandWidthFactor(FilterPicker6_BandParameters[] bandParameters) throws TimeDomainException {

        if (bandParameters.length < 2) {
            bandWidthFactor = BANDWIDTH_FACTOR_DEFAULT;
        }

        // get mean band width between band periods
        double periodRatioSum = 0.0;
        for (int n = 1; n < bandParameters.length; n++) {
            periodRatioSum += bandParameters[n].period / bandParameters[n - 1].period;
        }
        double meanBandWidthFactor = periodRatioSum / (bandParameters.length - 1);

        // check for highly variable bandwidths between band periods
        boolean bwWarning = false;
        boolean bwError = false;
        for (int n = 1; n < bandParameters.length; n++) {
            double bw = bandParameters[n].period / bandParameters[n - 1].period;
            double var = Math.abs(1.0 - bw / meanBandWidthFactor) * 100.0;
            if (var > BANDWIDTH_THRES_ERROR) {
                bwError = true;
            }
            if (var > BANDWIDTH_THRES_WARNING) {
                bwWarning = true;
            }
        }
        if (meanBandWidthFactor < BAND_WIDTH_MIN || meanBandWidthFactor > BAND_WIDTH_MAX) bwError=true;

        if (bwWarning || bwError) {
            String message
                    = "Bandwith factor between band periods varies from mean bandwidth factor=" + (float) meanBandWidthFactor + " by > "
                    + (bwError ? BANDWIDTH_THRES_ERROR : BANDWIDTH_THRES_WARNING)
                    + "%";
            Logger.getLogger(FilterPicker6_old.class.getName()).log(Level.INFO, message);
            String text = message + System.getProperty("line.separator") + "   Bands: " + System.getProperty("line.separator");
            boolean disp_freq;
            for (int n = 1; n < bandParameters.length; n++) {
                disp_freq = bandParameters[n].displayMode == DisplayMode.DISPLAY_MODE_FREQ;
                text += "      n=" + (n - 1) + "->" + n + ":";
                if (disp_freq) {
                    text += ", freq=" + (float) (1.0 / bandParameters[n - 1].period) + "->" + (float) (1.0 / bandParameters[n].period) + "Hz";
                } else {
                    text += ", per=" + (float) bandParameters[n - 1].period + "->" + (float) bandParameters[n].period + "s";
                }
                double bw = bandParameters[n].period / bandParameters[n - 1].period;
                text += ", bandwidth factor=" + (float) bw;
                double var = Math.abs(1.0 - bw / meanBandWidthFactor) * 100.0;
                text += ", var=" + (float) Math.round(var) + "%";
                text += System.getProperty("line.separator");
            }
            Logger.getLogger(FilterPicker6_old.class.getName()).log(Level.INFO, text);
            if (bwError) {
                throw new TimeDomainException(message);
            }
        }

        bandWidthFactor = meanBandWidthFactor;

        if (bandWidthFactor < BAND_WIDTH_MIN || bandWidthFactor > BAND_WIDTH_MAX) {
            throw new TimeDomainException(
                    TimeDomainText.invalid_band_width_value_value + ": " + bandWidthFactor);
        }

        //this.bandWidthFactor = bandWidthFactor;
    }
    protected static double BANDWIDTH_THRES_ERROR = 10.0;
    protected static double BANDWIDTH_THRES_WARNING = 2.0;

    /**
     * Sets bandWidthFactor
     *
     * @param bandWidthFactor
     * @throws TimeDomainException
     */
    public void setBandWidthFactor(double bandWidthFactor) throws TimeDomainException {
        if (bandWidthFactor < BAND_WIDTH_MIN || bandWidthFactor > BAND_WIDTH_MAX) {
            throw new TimeDomainException(
                    TimeDomainText.invalid_band_width_value_value + ": " + bandWidthFactor);
        }

        this.bandWidthFactor = bandWidthFactor;
    }

    /**
     * Sets bandWidthFactor
     *
     * @param str band width factor (floating point string)
     * @throws TimeDomainException
     */
    public void setBandWidthFactor(String str) throws TimeDomainException {
        try {
          setBandWidthFactor(Double.valueOf(str));
        } catch (NumberFormatException e) {
            throw new TimeDomainException(TimeDomainText.invalid_band_width_value_value + ": " + str);
        }
    }

    /**
     * Returns bandWidthFactor
     *
     * @return bandWidthFactor
     */
    public double getBandWidthFactor() {
        return bandWidthFactor;
    }

    /**
     * Sets the number of poles for the band pass filter
     *
     * @param numPolesBandPass number of poles to use (integer, multiple of 4)
     * @throws TimeDomainException
     */
    public void setNumPolesBandPass(int numPolesBandPass) throws TimeDomainException {
        if (numPolesBandPass < NUM_POLES_BANDPASS_MIN || numPolesBandPass > NUM_POLES_BANDPASS_MAX
                || numPolesBandPass % 4 != 0) {
            throw new TimeDomainException(
                    TimeDomainText.invalid_number_of_poles_value + ": " + numPolesBandPass);
        }

        this.numPolesBandPass = numPolesBandPass;
    }

    /**
     * Sets the number of poles for the band pass filter
     *
     * @param str number of poles to use (integer string, multiple of 4)
     * @throws TimeDomainException
     */
    public void setNumPolesBandPass(String str) throws TimeDomainException {

        int numPolesLowPassValue;

        try {
            numPolesLowPassValue = Integer.valueOf(str);
        } catch (NumberFormatException e) {
            throw new TimeDomainException(TimeDomainText.invalid_number_of_poles_value + ": " + str);
        }

        FilterPicker6_old.this.setNumPolesBandPass(numPolesLowPassValue);
    }

    /**
     * Sets long term window factor
     *
     * @param longTermWindowFactor long term window factor
     * @throws TimeDomainException
     */
    public void setLongTermWindowFactor(double longTermWindowFactor) throws TimeDomainException {
        if (longTermWindowFactor < LT_WINDOW_FACT_MIN || longTermWindowFactor > LT_WINDOW_FACT_MAX) {
            throw new TimeDomainException(
                    TimeDomainText.invalid_long_term_window_value + ": " + longTermWindowFactor);
        }

        this.longTermWindowFactor = longTermWindowFactor;
    }

    /**
     * Sets long term window factor
     *
     * @param str long term window factor (floating point string)
     * @throws TimeDomainException
     */
    public void setLongTermWindowFactor(String str) throws TimeDomainException {

        double longTermWindowValue;

        try {
            longTermWindowValue = Double.valueOf(str);
        } catch (NumberFormatException e) {
            throw new TimeDomainException(TimeDomainText.invalid_long_term_window_value + ": " + str);
        }

        FilterPicker6_old.this.setLongTermWindowFactor(longTermWindowValue);
    }

    /**
     * Returns longTermWindowFactor
     *
     * @return longTermWindowFactor
     */
    public double getLongTermWindowFactor() {
        return longTermWindowFactor;
    }

    /**
     * Sets threshold1, threshold to trigger a pick event
     *
     * @param threshold1 threshold to trigger a pick event
     * @throws TimeDomainException
     */
    public void setThreshold1(double threshold1) throws TimeDomainException {
        if (threshold1 < THRESHOLD_MIN || threshold1 > THRESHOLD_MAX) {
            throw new TimeDomainException(TimeDomainText.invalid_threshold1_value + ": " + threshold1);
        }

        this.threshold1 = threshold1;
    }

    /**
     * Sets threshold1, threshold to trigger a pick event
     *
     * @param str threshold to trigger a pick event (floating point string)
     * @throws TimeDomainException
     */
    public void setThreshold1(String str) throws TimeDomainException {

        double threshold1Value;

        try {
            threshold1Value = Double.valueOf(str);
        } catch (NumberFormatException e) {
            throw new TimeDomainException(TimeDomainText.invalid_threshold1_value + ": " + str);
        }

        setThreshold1(threshold1Value);
    }

    /**
     * Returns threshold1
     *
     * @return threshold1
     */
    public double getThreshold1() {
        return threshold1;
    }

    /**
     * Sets threshold2, threshold to to declare a pick
     *
     * @param threshold2 threshold to declare a pick
     * @throws TimeDomainException
     */
    public void setThreshold2(double threshold2) throws TimeDomainException {
        if (threshold2 < THRESHOLD_MIN || threshold2 > THRESHOLD_MAX) {
            throw new TimeDomainException(
                    TimeDomainText.invalid_threshold2_value + ": " + threshold2);
        }

        this.threshold2 = threshold2;
    }

    /**
     * Sets threshold2, threshold to to declare a pick
     *
     * @param str threshold to to declare a pick (floating point string)
     * @throws TimeDomainException
     */
    public void setThreshold2(String str) throws TimeDomainException {
        try {
            setThreshold2(Double.valueOf(str));
        } catch (NumberFormatException e) {
            throw new TimeDomainException(TimeDomainText.invalid_threshold2_value + ": " + str);
        }
    }

    /**
     * Returns threshold1
     *
     * @return threshold2
     */
    public double getThreshold2() {
        return threshold2;
    }

    /**
     * Sets maximum time window after trigger for declaring a pick
     *
     * @param tUpEvent maximum time window
     * @throws TimeDomainException
     */
    public void setTUpEvent(double tUpEvent) throws TimeDomainException {
        if (tUpEvent < TIME_UP_EVENT_MIN || tUpEvent > TIME_UP_EVENT_MAX) {
            throw new TimeDomainException(TimeDomainText.invalid_tUpEvent_value + ": " + tUpEvent);
        }

        this.tUpEvent = tUpEvent;
    }

    /**
     * Sets maximum time window after trigger for declaring a pick
     *
     * @param str maximum time window (floating point string)
     * @throws TimeDomainException
     */
    public void setTUpEvent(String str) throws TimeDomainException {
        try {
            setTUpEvent(Double.valueOf(str));
        } catch (NumberFormatException e) {
            throw new TimeDomainException(TimeDomainText.invalid_tUpEvent_value + ": " + str);
        }
    }

    /**
     *
     * @return tUpEvent
     */
    public double getTUpEvent() {
        return tUpEvent;
    }

    /**
     * Sets maximum time window after trigger for declaring a pick
     *
     * @param tUpEventMin maximum time window
     * @throws TimeDomainException
     */
    public void setTUpEventMin(double tUpEventMin) throws TimeDomainException {
        if (tUpEventMin < TIME_UP_EVENT_MIN || tUpEventMin > TIME_UP_EVENT_MAX) {
            throw new TimeDomainException(TimeDomainText.invalid_tUpEventMin_value + ": " + tUpEventMin);
        }

        this.tUpEventMin = tUpEventMin;
    }

    /**
     * Sets maximum time window after trigger for declaring a pick
     *
     * @param str maximum time window (floating point string)
     * @throws TimeDomainException
     */
    public void setTUpEventMin(String str) throws TimeDomainException {
        try {
            setTUpEventMin(Double.valueOf(str));
        } catch (NumberFormatException e) {
            throw new TimeDomainException(TimeDomainText.invalid_tUpEventMin_value + ": " + str);
        }
    }

    /**
     *
     * @return tUpEventMin
     */
    public double getTUpEventMin() {
        return tUpEventMin;
    }

    /**
     * Checks picker settings
     *
     * @throws TimeDomainException if an error in any settings
     */
    @Override
    public void checkSettings() throws TimeDomainException {

        super.checkSettings();

        String errMessage = "";
        int badSettings = 0;

        // check parameter settings which are independent of sample data
        if (numBands > 0 && bandParameters != null && bandParameters.length > 0) {
            for (FilterPicker6_BandParameters param : bandParameters) {
                try {
                    param.checkParams();
                } catch (TimeDomainException timeDomainException) {
                    errMessage += ": " + timeDomainException.getMessage();
                    badSettings++;
                }
            }
        }

        // following checks support backwards compatibility with FilterPicker5
        if (longTermWindowFactor < LT_WINDOW_FACT_MIN || longTermWindowFactor > LT_WINDOW_FACT_MAX) {
            errMessage += ": " + TimeDomainText.invalid_long_term_window_value;
            badSettings++;
        }
        if (threshold1 < THRESHOLD_MIN || threshold1 > THRESHOLD_MAX) {
            errMessage += ": " + TimeDomainText.invalid_threshold1_value;
            badSettings++;
        }
        if (threshold2 < THRESHOLD_MIN || threshold2 > THRESHOLD_MAX) {
            errMessage += ": " + TimeDomainText.invalid_threshold2_value;
            badSettings++;
        }
        if (threshold1 < threshold2) {
            errMessage += ": " + TimeDomainText.invalid_threshold1_value_must_be_ge_threshold2;
            badSettings++;
        }
        if (tUpEvent < TIME_UP_EVENT_MIN || tUpEvent > TIME_UP_EVENT_MAX) {
            errMessage += ": " + TimeDomainText.invalid_tUpEvent_value;
            badSettings++;
        }
        if (tUpEventMin < TIME_UP_EVENT_MIN || tUpEventMin > TIME_UP_EVENT_MAX) {
            errMessage += ": " + TimeDomainText.invalid_tUpEventMin_value;
            badSettings++;
        }
        if (tUpEventMin >= tUpEvent) {
            errMessage += ": " + TimeDomainText.tUpEventMin_ge_tUpEvent + ": tUpEventMin=" + tUpEventMin + ": tUpEvent=" + tUpEvent;
            badSettings++;
        }
        if (filterWindow != FILTER_WINDOW_NOT_USED && (filterWindow < FILTER_WINDOW_MIN || filterWindow > FILTER_WINDOW_MAX)) {
            errMessage += ": " + TimeDomainText.invalid_filterWindow_value;
            badSettings++;
        }

        if (badSettings > 0) {
            throw new TimeDomainException(errMessage + ".");
        }

    }

    /**
     * Update fields in TimeSeries object
     *
     * @param timeSeries the TimeSeries object.
     */
    @Override
    public void updateFields(TimeSeries timeSeries) {

        super.updateFields(timeSeries);

    }
    // DEBUG & ANALYSIS
    private static int index_band = 2;
    public static final boolean DEBUG = false;
    public static final boolean DEBUG_PICK = false;
    // working arrays
    protected double[] charFunctValueTrigger = null;
    protected double[] charFunctValueMaxInUpEventWindow = null;
    protected int[] indexBandTrigger = null;
    protected float[] sampleNew = null;

    /**
     * Applies picker algorithm to specified data sample
     *
     * @param dt the time-domain sampling interval in seconds.
     * @param sample the array of float values to be processed.
     * @return modified sample array if resultType == TRIGGER || resultType == CHAR_FUNC, returns samples if PICK
     * @throws net.alomax.timedom.TimeDomainException
     */
    @Override
    public final float[] apply(double dt, float[] sample) throws TimeDomainException {

        // _DOC_ =============================
        // _DOC_ apply algorithm
        // initialize sampling interval
        if (deltaTime > 0.0 && Math.abs(deltaTime - dt) > Float.MIN_NORMAL) {
            throw new TimeDomainException(TimeDomainText.data_sample_interval_changed + ": " + deltaTime + " -> " + dt);
        }
        deltaTime = dt;

        // initialize memory object and working arrays
        if (fp6Memory == null) {
            fp6Memory = new FilterPicker6_Memory();
            if (DEBUG) {
                System.out.println("DEBUG: FilterPicker6: bands " + this.getBandParamsString());
            }
            // check settings which depend on sample data
            if (numBands < 1 || bandParameters == null || bandParameters.length < 1) {
                throw new TimeDomainException(TimeDomainText.filter_parameters_not_set);
            } else {
                for (FilterPicker6_BandParameters param : bandParameters) {
                    param.checkParamsSampleDependent(deltaTime);
                }
            }
            // 20150226 AJL - FP5->FP6  double charFunctValueTrigger = -1.0;  // AJL 20091216
            // 20150226 AJL - save trigger char funct value for all bands
            charFunctValueTrigger = new double[numBands];
            charFunctValueMaxInUpEventWindow = new double[numBands];
            indexBandTrigger = new int[numBands];
        }

        // create array for processed time-series results
        if (resultType == TRIGGER || resultType == BANDS || resultType == CHAR_FUNC) {
            if (sampleNew == null || sampleNew.length != sample.length) {
                sampleNew = new float[sample.length];
            }
            //sampleNew[0] = sample[sample.length - 1] = 0.0f;
        } else {
            sampleNew = sample; // returns orignal data samples array
        }

        // _DOC_ =============================
        // _DOC_ loop over all samples
        boolean error1_not_printed = true;

        for (int nband = 0; nband < numBands; nband++) {
            charFunctValueTrigger[nband] = -1.0;
            charFunctValueMaxInUpEventWindow[nband] = -1.0;
            indexBandTrigger[nband] = -1;
        }
        int nSamplesUpEventUsed = -1;

        int indexUpEventTrigger = -1;
        int indexUncertaintyPick = -1;
        for (int nsamp = 0; nsamp < sample.length; nsamp++) {

            boolean acceptedPick = false;

            // _DOC_ update index of nSampleMemory length sample memory window buffers
            int sampleMemBufPtrLast = fp6Memory.sampleMemBufPtr;
            fp6Memory.sampleMemBufPtr = (fp6Memory.sampleMemBufPtr + 1) % fp6Memory.nSampleMemory;

            // _DOC_ =============================
            // _DOC_ characteristic function is  (E2 - mean_E2) / mean_stdDev_E2
            // _DOC_    where E2 = (filtered band value current - filtered band value previous)**2
            // _DOC_    where value previous is taken futher back for longer filter bands
            double charFunctMaxOverBands = -9999.0;
            double charFunctClippedMaxOverBands = -9999.0;
            // _DOC_ evaluate current signal values
            double currentSample = sample[nsamp];
            // _DOC_ filters are applied to first difference of signal values // 20150314 AJL - removed, use signal directly
            // 20150314 AJL  double currentDiffSample = currentSample - fp6Memory.lastSample;
            // 20150314 AJL fp6Memory.lastSample = currentSample;
            double currentFilteredSample = 0.0;
            // _DOC_ loop over nBand filter bands
            for (int nband = 0; nband < numBands; nband++) {
                /*   // 20150314 AJL
                 // _DOC_  apply nHP single-pole HP filters
                 // _DOC_  http://en.wikipedia.org/wiki/High-pass_filter    y[i] := α * (y[i-1] + x[i] - x[i-1])
                 int fsIndex = 0;
                 double currentDiffSample2 = currentDiffSample;
                 for (int np = 0; np < numPolesHighPass; np++) {
                 currentFilteredSample = fp6Memory.coeff_b1[nband] * (fp6Memory.filteredSample[nband][fsIndex] + currentDiffSample2);
                 currentDiffSample2 = currentFilteredSample - fp6Memory.filteredSample[nband][fsIndex];
                 fp6Memory.filteredSample[nband][fsIndex] = currentFilteredSample;
                 fsIndex++;
                 }
                 // _DOC_  apply nLP single-pole LP filters
                 // _DOC_  http://en.wikipedia.org/wiki/Low-pass_filter    y[i] := y[i-1] + α * (x[i] - y[i-1])
                 for (int np = 0; np < numPolesBandPass; np++) {
                 currentFilteredSample = fp6Memory.filteredSample[nband][fsIndex] + fp6Memory.coeff_a0[nband] * (currentFilteredSample - fp6Memory.filteredSample[nband][fsIndex]);
                 fp6Memory.lastFilteredSample[nband] = fp6Memory.filteredSample[nband][fsIndex];
                 fp6Memory.filteredSample[nband][fsIndex] = currentFilteredSample;
                 fsIndex++;
                 }
                 */

                // _DOC_  apply N-pole BP filter
                for (int np = 0; np < numPolesBandPass / 4; np++) {
                    //
                    fp6Memory.w0[nband][np] = fp6Memory.d1[nband][np] * fp6Memory.w1[nband][np] + fp6Memory.d2[nband][np] * fp6Memory.w2[nband][np]
                            + fp6Memory.d3[nband][np] * fp6Memory.w3[nband][np] + fp6Memory.d4[nband][np] * fp6Memory.w4[nband][np] + currentSample;
                    currentFilteredSample = fp6Memory.A[nband][np] * (fp6Memory.w0[nband][np] - 2.0 * fp6Memory.w2[nband][np] + fp6Memory.w4[nband][np]);
                    fp6Memory.w4[nband][np] = fp6Memory.w3[nband][np];
                    fp6Memory.w3[nband][np] = fp6Memory.w2[nband][np];
                    fp6Memory.w2[nband][np] = fp6Memory.w1[nband][np];
                    fp6Memory.w1[nband][np] = fp6Memory.w0[nband][np];

                }
                fp6Memory.currentFilteredSample[nband] = currentFilteredSample;
        // _DOC_  apply N-pole BP filter
                /*
                 currentFilteredSample = currentSample;
                 for (int np = 0; np < numPolesBandPass; np++) {
                 fp6Memory.prevSample[nband][np][0] = currentFilteredSample;
                 currentFilteredSample
                 = fp6Memory.coeff_a0[nband] * fp6Memory.prevSample[nband][np][0]
                 + fp6Memory.coeff_a1[nband] * fp6Memory.prevSample[nband][np][1]
                 + fp6Memory.coeff_a2[nband] * fp6Memory.prevSample[nband][np][2];
                 currentFilteredSample
                 += fp6Memory.coeff_b1[nband] * fp6Memory.prevFilteredSample[nband][np][1]
                 + fp6Memory.coeff_b2[nband] * fp6Memory.prevFilteredSample[nband][np][2];
                 fp6Memory.prevSample[nband][np][2] = fp6Memory.prevSample[nband][np][1];
                 fp6Memory.prevSample[nband][np][1] = fp6Memory.prevSample[nband][np][0];
                 fp6Memory.prevFilteredSample[nband][np][2] = fp6Memory.prevFilteredSample[nband][np][1];
                 fp6Memory.prevFilteredSample[nband][np][1] = currentFilteredSample;
                 }*/
                //
                double dy = currentFilteredSample;
                // ANALYSIS - filtered signal
                fp6Memory.test[nband] = dy;
                //
                // 20150821 AJL
                fp6Memory.xRec[nband] = dy * dy;
                //mem.xRec[nband] = Math.abs(dy);  // 20150821 AJL - Major change, use abs(data) instead of data^2.  May improve noise detection
                double charFunctClippedTest;  // AJL 20091214
                if (fp6Memory.mean_stdDev_xRec[nband] <= Float.MIN_VALUE) {
                    if (fp6Memory.enableTriggering && error1_not_printed) {
                        Logger.getLogger(FilterPicker6_old.class.getName()).log(Level.SEVERE, 
                                "FilterPicker6: mem.mean_stdDev_xRec[k] <= Float.MIN_VALUE (this should not happen!) k={0}", nband);
                        error1_not_printed = false;
                    }
                } else {
                    // 20150226 AJL - FP5->FP6  double charFunctTest = (fp6Memory.xRec[nband] - fp6Memory.mean_xRec[nband]) / fp6Memory.mean_stdDev_xRec[nband];
                    double charFunctTest = fp6Memory.thresholdScaleFactor[nband] * (fp6Memory.xRec[nband] - fp6Memory.mean_xRec[nband]) / fp6Memory.mean_stdDev_xRec[nband];  // 20150226 AJL - FP5->FP6
                    charFunctTest -= 1.0;  // 20150316 AJL - only part of CF > 1.0 contributes to integral  IMPORTANT CHANGE?
                    //if (charFunctTest < 0.0) {  // 20150821 AJL - only part of CF > 1.0 contributes to integral  IMPORTANT CHANGE?
                    //    charFunctTest = 0.0;
                    //}
                    charFunctClippedTest = charFunctTest;    // AJL 20091214
                    // _DOC_ limit maximum char funct value to avoid long recovery time after strong events
                    if (charFunctClippedTest > fp6Memory.maxCharFunctValue) {
                        charFunctClippedTest = fp6Memory.maxCharFunctValue;
                        // save corrected fp6Memory.xRec[nband]
                        // 20150318 AJL - FP5->FP6  fp6Memory.xRec[nband] = fp6Memory.maxCharFunctValue * fp6Memory.mean_stdDev_xRec[nband] + fp6Memory.mean_xRec[nband];
                        fp6Memory.xRec[nband] = fp6Memory.maxCharFunctValue * fp6Memory.mean_stdDev_xRec[nband] / fp6Memory.thresholdScaleFactor[nband] + fp6Memory.mean_xRec[nband];
                    }
                    // ANALYSIS - CF band
                    //mem.test[nband] = charFunctTest;
                    // _DOC_ characteristic function is maximum over nBand filter bands
                    if (charFunctTest >= charFunctMaxOverBands) {
                        charFunctMaxOverBands = charFunctTest;
                        charFunctClippedMaxOverBands = charFunctClippedTest;
                        fp6Memory.bandCharFuncMax[fp6Memory.sampleMemBufPtr] = nband;
                    }
                    // _DOC_ save longest period with CF >= threshold1 over nBand filter bands
                    if (charFunctTest >= threshold1) {
                        fp6Memory.longestPerBandTriggered[fp6Memory.sampleMemBufPtr] = nband;
                    }
                    // save CF for all bands // 20150302 AJL - FP5->FP6
                    fp6Memory.charFunctValue[nband][fp6Memory.sampleMemBufPtr] = charFunctTest;
                }
            } // Loop over all bands
            for (int nband = 0; nband < numBands; nband++) {
// AJL 20091214
                // _DOC_ =============================
                // _DOC_ update uncertainty and polarity fields
                // _DOC_ uncertaintyThreshold is at minimum char function or char funct increases past uncertaintyThreshold
                // 20150316 AJL fp6Memory.charFunctUncertainty[nband] = charFunctClippedTest;   // no smoothing
                fp6Memory.charFunctUncertainty[nband] = charFunctClippedMaxOverBands;     // independant of band! TODO: remove band arrays
                // AJL 20091214 fp6Memory.charFunctLast = charFunctClippedMaxOverBands;
                boolean upCharFunctUncertainty // char funct increases past uncertaintyThreshold
                        = ((fp6Memory.charFunctUncertaintyLast[nband] < fp6Memory.uncertaintyThreshold[nband]) && 
                        (fp6Memory.charFunctUncertainty[nband] >= fp6Memory.uncertaintyThreshold[nband]));
                //boolean upCharFunctUncertainty // at minimum of char function  // 20150401 AJL - added
                //        = ((fp6Memory.charFunctUncertainty[nband] - fp6Memory.charFunctUncertaintyLast[nband]) > 0.0 && fp6Memory.charFunctUncertaintyDiffLast[nband] < 0.0);
                //mem.charFunctUncertaintyDiffLast[nband] = fp6Memory.charFunctUncertainty[nband] - fp6Memory.charFunctUncertaintyLast[nband];
                fp6Memory.charFunctUncertaintyLast[nband] = fp6Memory.charFunctUncertainty[nband];
                // _DOC_ each time characteristic function rises past uncertaintyThreshold store sample index and initiate polarity algoirithm
                if (upCharFunctUncertainty) {
                    fp6Memory.indexUncertainty[nband][fp6Memory.sampleMemBufPtr] = nsamp - 1;
                    //mem.indexUncertainty[nband][fp6Memory.sampleMemBufPtr] = nsamp;
                } else {
                    fp6Memory.indexUncertainty[nband][fp6Memory.sampleMemBufPtr] = fp6Memory.indexUncertainty[nband][sampleMemBufPtrLast];
                }
// END - AJL 20091214
                /*
                 if (upCharFunctUncertainty) {
                 // _DOC_ initialize polarity algorithm, uses derivative of signal
                 fp6Memory.polarityDerivativeSum[nband][fp6Memory.sampleMemBufPtr] = 0.0;
                 fp6Memory.polaritySumAbsDerivative[nband][fp6Memory.sampleMemBufPtr] = 0.0;
                 } else {
                 fp6Memory.polarityDerivativeSum[nband][fp6Memory.sampleMemBufPtr] = fp6Memory.polarityDerivativeSum[nband][sampleMemBufPtrLast];
                 fp6Memory.polaritySumAbsDerivative[nband][fp6Memory.sampleMemBufPtr] = fp6Memory.polaritySumAbsDerivative[nband][sampleMemBufPtrLast];
                 }*/
                // _DOC_   accumulate derivative and sum of abs of derivative for polarity estimate
                // _DOC_   accumulate since last indexUncertainty
                // derivative
                double polarityderivativeIncrement = fp6Memory.currentFilteredSample[nband] - fp6Memory.lastFilteredSample[nband];
                fp6Memory.lastFilteredSample[nband] = fp6Memory.currentFilteredSample[nband];  // 20150314 AJL
                // signal
                //double polarityderivativeIncrement = fp6Memory.currentFilteredSample[nband];
                fp6Memory.polarityDerivativeSum[nband][fp6Memory.sampleMemBufPtr] = polarityderivativeIncrement;
                fp6Memory.polaritySumAbsDerivative[nband][fp6Memory.sampleMemBufPtr] = Math.abs(polarityderivativeIncrement);

            }

            // _DOC_ =============================
            // _DOC_ trigger and pick logic
            // _DOC_ only apply trigger and pick logic if past stabilisation time (CF < 1 or long-term window of longest period band)
            if (fp6Memory.enableTriggering || fp6Memory.nTotal++ > fp6Memory.indexEnableTriggering) {  // past stabilisation time

                fp6Memory.enableTriggering = true;

                // _DOC_ update charFunctClippedMaxOverBands values, subtract oldest value, and save provisional current sample charFunctMaxOverBands value
                // _DOC_ to avoid spikes, do not use full charFunctMaxOverBands value, may be very large, instead use charFunctClippedMaxOverBands
                int beginTUpEventPtrLast = fp6Memory.sampleMemBufPtr - fp6Memory.nTUpEvent;      // to accumulate integral over nTupEvent window, need to subtract CF value nTupEvent samples before current
                if (beginTUpEventPtrLast < 0) {
                    beginTUpEventPtrLast += fp6Memory.nSampleMemory;
                }
                if (beginTUpEventPtrLast < 0) {
                    Logger.getLogger(FilterPicker6_old.class.getName()).log(Level.SEVERE, "FilterPicker6: beginTUpEventPtr < 0 (this should not happen!)");
                }

                int beginTUpEventPtrShort = fp6Memory.sampleMemBufPtr - fp6Memory.nTUpEventMin;      // to accumulate integral over nTupEvent window, need to subtract CF value nTupEvent samples before current
                if (beginTUpEventPtrShort < 0) {
                    beginTUpEventPtrShort += fp6Memory.nSampleMemory;
                }
                if (beginTUpEventPtrShort < 0) {
                    Logger.getLogger(FilterPicker6_old.class.getName()).log(Level.SEVERE, "FilterPicker6: beginTUpEventPtrShort < 0 (this should not happen!)");
                }

                /*
                 // accumulate integral over nTupEvent window
                 fp6Memory.charFunctClippedValue[fp6Memory.sampleMemBufPtr] = charFunctClippedMaxOverBands;  // 20150821 AJL - moved from below
                 // 20150526 AJL  fp6Memory.integralCharFunctClipped[fp6Memory.sampleMemBufPtr]
                 //       = fp6Memory.integralCharFunctClipped[sampleMemBufPtrLast] - fp6Memory.charFunctClippedValue[beginTUpEventPtrLast] + charFunctClippedMaxOverBands;
                 fp6Memory.integralCharFunctClipped[fp6Memory.sampleMemBufPtr] = 0.0;
                 // 20150821 AJL - remove this check and zeroing out of CF, seems to degrade picking performance for complex energy onsets
                 /*if (charFunctClippedMaxOverBands <= fp6Memory.maxAllowNewPickThreshold) {  // CF too low, zero out past CF so they cannot contribute to integral
                 for (int n = beginTUpEventPtrLast; n <= fp6Memory.sampleMemBufPtr; n++) {
                 fp6Memory.charFunctClippedValue[n] = 0.0;
                 }/*
                 // accumulate integral over nTupEvent window
                 fp6Memory.charFunctClippedValue[fp6Memory.sampleMemBufPtr] = charFunctClippedMaxOverBands;  // 20150821 AJL - moved from below
                 // 20150526 AJL  fp6Memory.integralCharFunctClipped[fp6Memory.sampleMemBufPtr]
                 //       = fp6Memory.integralCharFunctClipped[sampleMemBufPtrLast] - fp6Memory.charFunctClippedValue[beginTUpEventPtr] + charFunctClippedMaxOverBands;
                 fp6Memory.integralCharFunctClipped[fp6Memory.sampleMemBufPtr] = 0.0;
                 // 20150821 AJL - remove this check and zeroing out of CF, seems to degrade picking performance for complex energy onsets
                 /*if (charFunctClippedMaxOverBands <= fp6Memory.maxAllowNewPickThreshold) {  // CF too low, zero out past CF so they cannot contribute to integral
                 for (int n = beginTUpEventPtr; n <= fp6Memory.sampleMemBufPtr; n++) {
                 fp6Memory.charFunctClippedValue[n] = 0.0;
                 }/*
                 // accumulate integral over nTupEvent window
                 mem.charFunctClippedValue[mem.sampleMemBufPtr] = charFunctClippedMaxOverBands;  // 20150821 AJL - moved from below
                 // 20150526 AJL  mem.integralCharFunctClipped[mem.sampleMemBufPtr]
                 //       = mem.integralCharFunctClipped[sampleMemBufPtrLast] - mem.charFunctClippedValue[beginTUpEventPtrLast] + charFunctClippedMaxOverBands;
                 mem.integralCharFunctClipped[mem.sampleMemBufPtr] = 0.0;
                 // 20150821 AJL - remove this check and zeroing out of CF, seems to degrade picking performance for complex energy onsets
                 /*if (charFunctClippedMaxOverBands <= mem.maxAllowNewPickThreshold) {  // CF too low, zero out past CF so they cannot contribute to integral
                 for (int n = beginTUpEventPtrLast; n <= mem.sampleMemBufPtr; n++) {
                 mem.charFunctClippedValue[n] = 0.0;
                 }/*
                 // accumulate integral over nTupEvent window
                 mem.charFunctClippedValue[mem.sampleMemBufPtr] = charFunctClippedMaxOverBands;  // 20150821 AJL - moved from below
                 // 20150526 AJL  mem.integralCharFunctClipped[mem.sampleMemBufPtr]
                 //       = mem.integralCharFunctClipped[sampleMemBufPtrLast] - mem.charFunctClippedValue[beginTUpEventPtr] + charFunctClippedMaxOverBands;
                 mem.integralCharFunctClipped[mem.sampleMemBufPtr] = 0.0;
                 // 20150821 AJL - remove this check and zeroing out of CF, seems to degrade picking performance for complex energy onsets
                 /*if (charFunctClippedMaxOverBands <= mem.maxAllowNewPickThreshold) {  // CF too low, zero out past CF so they cannot contribute to integral
                 for (int n = beginTUpEventPtr; n <= mem.sampleMemBufPtr; n++) {
                 mem.charFunctClippedValue[n] = 0.0;
                 }
                 } else {*/ // 20150821 AJL
                // accumulate integral over nTupEvent window
                /*for (int n = beginTUpEventPtrLast; n <= fp6Memory.sampleMemBufPtr; n++) {
                 fp6Memory.integralCharFunctClipped[fp6Memory.sampleMemBufPtr] += fp6Memory.charFunctClippedValue[n];
                 }*/
                // 20150821 AJL - moved to above  fp6Memory.charFunctClippedValue[fp6Memory.sampleMemBufPtr] = charFunctClippedMaxOverBands;
                // 20150821 AJL                }
                //if (DEBUG) System.out.println("DEBUG: fp6Memory.integralCharFunctClipped[nband_eval:" + nband_eval + "][sampleMemBufPtr_band:" + sampleMemBufPtr_band + "]: " + fp6Memory.integralCharFunctClipped[nband_eval][sampleMemBufPtr_band] + ", crit=" + fp6Memory.criticalIntegralCharFunct + ", allowNewPickIndex=" + fp6Memory.allowNewPickIndex);
                // _DOC_ update charFunctClipped values, subtract oldest value, and save provisional current sample charFunct value
                // _DOC_ to avoid spikes, do not use full charFunct value, may be very large, instead use charFunctClipped
                fp6Memory.integralCharFunctClipped[fp6Memory.sampleMemBufPtr]
                        = fp6Memory.integralCharFunctClipped[sampleMemBufPtrLast] - 
                          fp6Memory.charFunctClippedValue[beginTUpEventPtrLast] + 
                          charFunctClippedMaxOverBands;
                fp6Memory.integralCharFunctClippedShort[fp6Memory.sampleMemBufPtr]
                        = fp6Memory.integralCharFunctClippedShort[sampleMemBufPtrLast] - 
                          fp6Memory.charFunctClippedValue[beginTUpEventPtrShort] + 
                          charFunctClippedMaxOverBands;
                fp6Memory.charFunctClippedValue[fp6Memory.sampleMemBufPtr] = charFunctClippedMaxOverBands;
                fp6Memory.charFunctValueMax[fp6Memory.sampleMemBufPtr] = charFunctMaxOverBands;

                // _DOC_ if new picks allowed, check if integralCharFunct over last tUpEvent window is greater than threshold
                if (fp6Memory.allowNewPickIndex != INT_UNSET && 
                    fp6Memory.integralCharFunctClipped[fp6Memory.sampleMemBufPtr] >= fp6Memory.criticalIntegralCharFunct) {

                    // _DOC_ find first point in tUpEvent window where charFunctMaxOverBands rose past threshold1
                    //double integralCharFunctClippedWindow = fp6Memory.charFunctClippedValue[isamp_test];
                    //for (int nband = 0; nband < numBands; nband++) {
                    //    charFunctValueMaxInUpEventWindow[nband] = fp6Memory.charFunctValue[nband][isamp_test];
                    //}
                    for (int nband = 0; nband < numBands; nband++) {
                        charFunctValueMaxInUpEventWindow[nband] = -Double.MAX_VALUE;
                        indexBandTrigger[nband] = -1;
                    }
                    int isamp_test = (beginTUpEventPtrLast + 1) % fp6Memory.nSampleMemory;
                    int icount = fp6Memory.nTUpEvent; // counts back from current end of tUpEvent window
                    double threshold1_x_nTUpEventMin = threshold1 * (double) fp6Memory.nTUpEventMin;
                    int nSampUpEventTrigger = -1;
                    //boolean have_threshold1 = false; // 20150316 AJL - thres1 in up event window
                    while (--icount >= 0 && nsamp - icount > fp6Memory.allowNewPickIndex) {
                        //integralCharFunctClippedWindow += fp6Memory.charFunctClippedValue[isamp_test];
                        // _DOC_ check thresholds to determine if to accept pick
                        if (!acceptedPick) {
                            // _DOC_ check if charFunctMaxOverBands is greater than threshold1
                            if (fp6Memory.charFunctValueMax[isamp_test] >= threshold1) {
                                // _DOC_ check if integralCharFunctClippedShort is greater than threshold1 * nTUpEventMin
                                int isamp_test_short = isamp_test;  // start at current point
                                int ncount = 0;     // check up to nTUpEventMin points
                                while (ncount < fp6Memory.nTUpEventMin && isamp_test_short != fp6Memory.sampleMemBufPtr) {
                                    //while (isamp_test_short != fp6Memory.sampleMemBufPtr) {
                                    if (fp6Memory.integralCharFunctClippedShort[isamp_test_short] >= threshold1_x_nTUpEventMin) {
                                        fp6Memory.bandTriggerMax = fp6Memory.bandCharFuncMax[isamp_test];
                                        // _DOC_ set index for pick uncertainty begin and end
                                        //int iphaseshift = calculatePhaseShiftInSamples(bandParameters[fp6Memory.bandTriggerMax].period);
                                        indexUpEventTrigger = nsamp - icount;
                                        indexUncertaintyPick = fp6Memory.indexUncertainty[fp6Memory.bandTriggerMax][isamp_test];  // AJL 20091214
                                        // pick uncertainty should not exceed N * trigger band period   // 20150819 AJL
                                        int maxUnc = (int) (2.0 * bandParameters[fp6Memory.bandTriggerMax].period / deltaTime);
                                        if (indexUpEventTrigger - indexUncertaintyPick > maxUnc) {
                                            indexUncertaintyPick = indexUpEventTrigger - maxUnc;
                                        }
                                        // do not allow new picks if their lower uncertainty overaps upper uncertainty of previous pick
                                        if (indexUncertaintyPick > fp6Memory.canAllowNewPickIndex) {  // 20150611 AJL - added
                                            // set some trigger values
                                            nSamplesUpEventUsed = icount + 1;
                                            nSampUpEventTrigger = isamp_test;
                                            // _DOC_ save characteristic function value as indicator of pick strength
                                            fp6Memory.shortestPerBandTriggered = -1;
                                            for (int nband = numBands - 1; nband >= 0; nband--) {
                                                // 20150302 AJL - FP5->FP6
                                                charFunctValueTrigger[nband] = fp6Memory.charFunctValue[nband][isamp_test];  // AJL 20091216
                                                if (charFunctValueTrigger[nband] > threshold1) {
                                                    fp6Memory.shortestPerBandTriggered = nband;
                                                }
                                            }
                                            acceptedPick = true;
                                            break;  // success, end check if integralCharFunctClippedShort is greater than threshold1 * nTUpEventMin
                                        }
                                    }
                                    isamp_test_short = (isamp_test_short + 1) % fp6Memory.nSampleMemory;
                                    ncount++;
                                }
                            }
                        }
                        if (acceptedPick) {
                            // _DOC_ set max CF value and trigger index for each band in window from pick accepted to current sample (where pick was declared)
                            for (int nband = 0; nband < numBands; nband++) {
                                if (fp6Memory.charFunctValue[nband][isamp_test] > charFunctValueMaxInUpEventWindow[nband]) {
                                    charFunctValueMaxInUpEventWindow[nband] = fp6Memory.charFunctValue[nband][isamp_test];
                                    if (indexBandTrigger[nband] < 0 && fp6Memory.charFunctValue[nband][isamp_test] > threshold1) {
                                        indexBandTrigger[nband] = isamp_test;
                                    }
                                }
                            }
                        }

                        // increment sample
                        isamp_test = (isamp_test + 1) % fp6Memory.nSampleMemory;
                    }

                    if (acceptedPick) {
                        fp6Memory.canAllowNewPickIndex = indexUpEventTrigger;
                        fp6Memory.allowNewPickIndex = INT_UNSET;
                        // adjust pick for bp filter phase shifts
                        int iphaseshift = calculatePhaseShiftInSamples(bandParameters[fp6Memory.shortestPerBandTriggered].period);
                        //iphaseshift = 0;  // TEMP TESTING! REMOVE!
                        //AJL 20150612
                        indexUpEventTrigger -= iphaseshift;    // AJL 20150318, 20150824
                        //AJL 20150612
                        indexUncertaintyPick -= iphaseshift;    // AJL 20150318, 20150824
                        // evaluate polarity on waveform w/o phase shift corr: segment of interest for polarity is shifted by filter
                        //
                        /*
                         fp6Memory.bandTriggerPolarity = -1;
                         // measure polarity on longest period band that triggered in tUpEvent window for polarity
                         for (int nband = numBands - 1; nband >= 0; nband--) {
                         //if (charFunctValueMaxInUpEventWindow[nband] >= threshold1) {
                         if (indexBandTrigger[nband] >= 0) {
                         fp6Memory.bandTriggerPolarity = nband;
                         break;
                         }
                         }
                         if (DEBUG_PICK) {
                         System.out.println("\nDEBUG: fp6Memory.bandTriggerPolarity: " + fp6Memory.bandTriggerPolarity
                         + ", T: " + (float) bandParameters[fp6Memory.bandTriggerPolarity].period
                         + ", CFMAX: " + charFunctValueMaxInUpEventWindow[fp6Memory.bandTriggerPolarity]
                         + ", amp:" + indexBandTrigger[fp6Memory.bandTriggerPolarity]
                         );
                         }
                         */
                        // label polarity on band with maximum CF during trigger
                        fp6Memory.bandTriggerPolarity = fp6Memory.bandTriggerMax;
                        // label polarity on longest per band that triggered
                        //mem.bandTriggerPolarity = fp6Memory.longestPerBandTriggered[isamp_test];
                        //
                        //int halfUnc = 1 + (indexUpEventTrigger - indexUncertaintyPick) / 2;
                        int halfUnc = (indexUpEventTrigger - indexUncertaintyPick) / 2;
                        int iPick = nSampUpEventTrigger - halfUnc; // start polarity evaluation at pick point
                        //int iPick = isampUpEventTrigger - 1; // evaluate from upEvent Trigger - 1   // 20150401 AJL
                        if (iPick < 0) {
                            iPick += fp6Memory.nSampleMemory;
                        }
                        if (iPick < 0) {
                            Logger.getLogger(FilterPicker6_old.class.getName()).log(Level.SEVERE, "FilterPicker6: iPick < 0 (this should not happen!)");
                        }
                        double pickPolaritySum = 0.0;
                        double pickPolaritySumWeight = 0.0;
                        for (int nband = 0; nband < numBands; nband++) {
                            //if (charFunctValueTrigger[nband] >= threshold1) {   // measure polarity on all bands that triggered
                            if (indexBandTrigger[nband] >= 0) {   // measure polarity on all bands that triggered in tUpEvent window
                                //if (nband == fp6Memory.bandTriggerMax) {   // measure polarity on band with maximum CF during trigger
                                //int iPolarity = indexBandTrigger[nband];
                                //int iPolarity = (isampUpEventTrigger + 1) % fp6Memory.nSampleMemory; // evaluate polarity at 1 point past trigger point w/o phase shift corr
                                int ioffset = (int) (bandParameters[nband].period / deltaTime / 4.0);  // evaluate polarity to 1/4 period past pick
                                //ioffset = Math.max(ioffset, halfUnc + 1); // evaluate to upEvent Trigger if greater than 1/4 period
                                ioffset = Math.min(ioffset, halfUnc + 1); // evaluate only to upEvent Trigger if less than 1/4 period
                                ioffset = Math.min(ioffset, fp6Memory.nSampleMemory / 2); // cannot go too far in past
                                //int iPolOffset = 0;  // evaluate polarity at upEvent Trigger
                                ioffset = Math.max(ioffset, 2);
                                int iPolarityStart = iPick - ioffset;
                                while (iPolarityStart < 0) {
                                    iPolarityStart += fp6Memory.nSampleMemory;
                                }
                                int iPolarityEnd = (iPick + ioffset) % fp6Memory.nSampleMemory;  // evaluate polarity in window past pick
                                //iPolarity = isampUpEventTrigger; // evaluate to upEvent Trigger
                                //iPolarityEnd = (nSampUpEventTrigger + 1) % fp6Memory.nSampleMemory; // evaluate to upEvent Trigger + 1   // 20150401 AJL
                                if (DEBUG_PICK) {
                                    System.out.println("DEBUG: " + "mem.bandTriggerMax " + fp6Memory.bandTriggerMax + ", nband " + nband + ", T " + bandParameters[nband].period
                                            + ", halfUnc " + halfUnc + ", ioffset " + ioffset + ", iPolarity:" + (iPick - ioffset) + "->" + (iPick + ioffset)
                                            + ", iPick " + iPick + ", mem.nTotal " + fp6Memory.nTotal + ", indexUpEventTrigger " + indexUpEventTrigger + ", indexUncertaintyPick "
                                            + indexUncertaintyPick);
                                }
                                // integrate polDeriv and its abs
                                double polDerivSum = 0.0;
                                double polSumAbsDeriv = 0.0;
                                for (int n = iPolarityStart; n <= iPolarityEnd; n++) {
                                    polDerivSum += fp6Memory.polarityDerivativeSum[nband][n];
                                    polSumAbsDeriv += fp6Memory.polaritySumAbsDerivative[nband][n];
                                }
                                int pickPolarity = 0;
                                double ratio = -999.9;
                                final double PICK_POLARIY_DERIV_QUALITY_CUTOFF = 0.667;
                                //final double PICK_POLARIY_DERIV_QUALITY_CUTOFF = 0.5;
                                if (Math.abs(polSumAbsDeriv) > Float.MIN_NORMAL) {
                                    ratio = polDerivSum / polSumAbsDeriv;
                                    if (ratio > PICK_POLARIY_DERIV_QUALITY_CUTOFF) {
                                        pickPolarity = 1;
                                    } else if (-ratio > PICK_POLARIY_DERIV_QUALITY_CUTOFF) {
                                        pickPolarity = -1;
                                    }
                                }
                                if (pickPolarity != 0) {    // only accumulate defined polarities
                                    double weight;
                                    //weight = (Math.abs(ratio) - PICK_POLARIY_DERIV_QUALITY_CUTOFF) / PICK_POLARIY_DERIV_QUALITY_CUTOFF;
                                    //weight *= Math.sqrt(bandParameters[nband].period); // give more weight to longer periods, like(?) integrating signal
                                    //weight *= bandParameters[nband].period; // give more weight to longer periods, like(?) integrating signal
                                    weight = bandParameters[nband].period / bandParameters[fp6Memory.bandTriggerMax].period;
                                    if (weight > 1.0) {
                                        weight = 1.0 / weight;
                                    }
                                    pickPolaritySum += pickPolarity * weight;
                                    pickPolaritySumWeight += weight;
                                }
                                if (DEBUG_PICK) {
                                    System.out.println("DEBUG: nband:" + nband
                                            + "  T: " + (float) bandParameters[nband].period
                                            + "s/" + (float) (1.0 / bandParameters[nband].period) + "Hz"
                                            + "  Pol: " + pickPolarity
                                            + "  iPick:" + iPick + "  iPolarity:" + iPolarityStart + "->" + iPolarityEnd
                                            + "  PolaritySum:" + (float) pickPolaritySum
                                            + "  nPolaritySum:" + (float) pickPolaritySumWeight
                                            + "  polDerivSum:" + (float) fp6Memory.polarityDerivativeSum[nband][iPolarityEnd]
                                            + "- " + (float) fp6Memory.polarityDerivativeSum[nband][iPolarityStart]
                                            + "=polDerivSum:" + (float) polDerivSum
                                            + "  polSumAbsDeriv:" + (float) fp6Memory.polaritySumAbsDerivative[nband][iPolarityEnd]
                                            + "- " + (float) fp6Memory.polaritySumAbsDerivative[nband][iPolarityStart]
                                            + "=polSumAbsDeriv:" + (float) polSumAbsDeriv
                                            + " ratio:" + (float) ratio
                                            + " cfmax:" + (float) charFunctValueMaxInUpEventWindow[nband]
                                    );
                                }
                            }
                        }  // loop on all bands
                        fp6Memory.pickPolarity = PickData.POLARITY_UNKNOWN;
                        if (pickPolaritySumWeight > Float.MIN_NORMAL) {
                            if (pickPolaritySum / pickPolaritySumWeight > 0.0) {
                                fp6Memory.pickPolarity = PickData.POLARITY_POS;
                            } else if (pickPolaritySum / pickPolaritySumWeight < 0.0) {
                                fp6Memory.pickPolarity = PickData.POLARITY_NEG;
                            }
                        }
                        if (DEBUG_PICK) {
                            System.out.println("DEBUG: " + fp6Memory.pickPolarity + "  pickPolaritySum: " + pickPolaritySum + "  nPickPolaritySum: " + pickPolaritySumWeight
                                    + "  iUncPick:" + indexUncertaintyPick + "  iUpEvTrig:" + indexUpEventTrigger
                                    + "  mem.nSampleMemory:" + fp6Memory.nSampleMemory
                            );
                        }
                    }   // if acceptedPick

                }   // if greater than threshold

                // _DOC_ if no pick, check if charFunctUncertainty has dropped below threshold maxAllowNewPickThreshold to allow new picks
                if (!acceptedPick && fp6Memory.allowNewPickIndex == INT_UNSET) {  // no pick and no allow new picks
                    // AJL 20091214
                    int nband = 0;
                    for (; nband < numBands; nband++) {
                        //if (fp6Memory.charFunctUncertainty[nband] > fp6Memory.maxAllowNewPickThreshold) // do not allow new picks
                        if (fp6Memory.charFunctUncertainty[nband] <= fp6Memory.maxAllowNewPickThreshold) // allow new picks // 20150611 AJL
                        {
                            break;
                        }
                    }
                    if (nband == numBands) {
                        fp6Memory.allowNewPickIndex = nsamp;
                    }
                    // END AJL 20091214
                }
            } else {
                //charFunctClippedMaxOverBands = -1.0; // so CHAR_FUNC plot shows -1
                charFunctClippedMaxOverBands = 0.0; // so CHAR_FUNC plot shows 0.0
            }

            // _DOC_ =============================
            // _DOC_ update "true", long-term statistic based on current signal values based on long-term window
            // long-term decay formulation
            // _DOC_ update long-term means of x, dxdt, E2, var(E2), uncertaintyThreshold
            for (int nband = 0; nband < numBands; nband++) {
                fp6Memory.mean_xRec[nband] = fp6Memory.mean_xRec[nband] * fp6Memory.longDecayConst[nband] + fp6Memory.xRec[nband] * fp6Memory.longDecayFactor[nband];
                double dev = fp6Memory.xRec[nband] - fp6Memory.mean_xRec[nband];
                fp6Memory.mean_var_xRec[nband] = fp6Memory.mean_var_xRec[nband] * fp6Memory.longDecayConst[nband] + dev * dev * fp6Memory.longDecayFactor[nband];
                // _DOC_ mean_stdDev_E2 is sqrt(long-term mean var(E2))
                fp6Memory.mean_stdDev_xRec[nband] = Math.sqrt(fp6Memory.mean_var_xRec[nband]);
                fp6Memory.uncertaintyThreshold[nband] = fp6Memory.uncertaintyThreshold[nband] * fp6Memory.longDecayConst[nband] + fp6Memory.charFunctUncertainty[nband] * fp6Memory.longDecayFactor[nband];
                if (fp6Memory.uncertaintyThreshold[nband] > fp6Memory.maxUncertaintyThreshold) {
                    fp6Memory.uncertaintyThreshold[nband] = fp6Memory.maxUncertaintyThreshold;
                } else if (fp6Memory.uncertaintyThreshold[nband] < fp6Memory.minUncertaintyThreshold) {
                    fp6Memory.uncertaintyThreshold[nband] = fp6Memory.minUncertaintyThreshold;
                }
            }

            // _DOC_ =============================
            //  _DOC_ act on result, save pick if pick accepted at this sample
            if (resultType == PICKS) {                // PICK
                if (acceptedPick) {
                    // _DOC_ if pick accepted, save pick time, uncertainty, strength (integralCharFunct) and polarity
                    // _DOC_    pick time is uncertainty threshold (characteristic function rose past
                    // _DOC_    uncertaintyThreshold) and trigger time (characteristic function >= threshold1)
                    // _DOC_    already adjusted for any phase shift correction
                    // phase shift: http://nbviewer.ipython.org/github/demotu/BMC/blob/master/notebooks/DataFiltering.ipynb
                    // _DOC_    pick begin is pick time - (trigger time - uncertainty threshold)
                    // 20150316 AJL  int indexBeginPick = indexUncertaintyPick - (indexUpEventTrigger - indexUncertaintyPick);
                    int indexBeginPick = indexUncertaintyPick; // 20150316 AJL
                    int indexEndPick = indexUpEventTrigger;
                    double triggerPeriod = bandParameters[fp6Memory.bandTriggerMax].period;
                    // check that uncertainty range is >= triggerPeriod / minFracPerForUnc  // 20101014 AJL
                    double minFracPerForUnc = 4.0;
                    double uncertainty = deltaTime * (indexEndPick - indexBeginPick);
                    if (uncertainty < triggerPeriod / minFracPerForUnc) {
                        int ishift = (int) (0.5 * (triggerPeriod / minFracPerForUnc - uncertainty) / deltaTime);
                        // advance uncertainty index
                        indexBeginPick -= ishift;
                        // delay trigger index
                        indexEndPick += ishift;
                    }
                    // 20150302 AJL - FP5->FP6
                    // get maximum CF over all bands in integralCharFunct window
                    /*double charFunctValueTriggerMax = -1.0;
                     for (int nband = 0; nband < numBands; nband++) {
                     charFunctValueTriggerMax = Math.max(charFunctValueTriggerMax, charFunctValueTrigger[nband]);
                     }*/
                    double charFunctValueTriggerMax = charFunctValueTrigger[fp6Memory.bandTriggerMax];
                    FilterPicker6PickData pickData = new FilterPicker6PickData((double) indexBeginPick, (double) indexEndPick,
                            fp6Memory.pickPolarity, charFunctValueTriggerMax, // AJL 20091216
                            PickData.CHAR_FUNCT_AMP_UNITS, triggerPeriod,
                            // 20150302 AJL - FP5->FP6
                            nSamplesUpEventUsed,
                            bandParameters, charFunctValueTrigger, charFunctValueMaxInUpEventWindow
                    );
                    // DEBUG
                    pickData.name = NumberFormat.removeTrailingZeros(NumberFormat.doubleString(1.0 / triggerPeriod, 2)); // DEBUG
                    triggerPickData.add(pickData);
                    // DEBUG
                    if (DEBUG_PICK) {
                        System.out.println("DEBUG: pick: P" + (triggerPickData.size() - 1)
                                + " nUp: " + nSamplesUpEventUsed + " (" + (float) (deltaTime * nSamplesUpEventUsed) + "s)"
                                + " -------------------------------------------------------");
                        for (int nband = 0; nband < numBands; nband++) {
                            double cftrig = pickData.charFunctValueTrigger[nband] / threshold1;
                            double cfmaxtrig = pickData.charFunctValueMaxInUpEventWindow[nband] / threshold1;
                            System.out.println("DEBUG: " + nband
                                    + "  T: " + (float) pickData.bandParameters[nband].period
                                    + "s/" + (float) (1.0 / pickData.bandParameters[nband].period) + "Hz"
                                    + "  scale: " + (float) (pickData.bandParameters[nband].thresholdScaleFactor)
                                    + "  mean_sd: " + (float) (fp6Memory.mean_stdDev_xRec[nband])
                                    + "  CFmax: " + (float) (pickData.charFunctValueMaxInUpEventWindow[nband])
                                    + " (" + (float) cfmaxtrig + ")"
                                    + (cfmaxtrig >= 1.0 ? " +" : "  ")
                                    + "  CFtrig: " + (float) pickData.charFunctValueTrigger[nband]
                                    + " (" + (float) cftrig + ")"
                                    + (cftrig >= 1.0 ? " T" : "  ")
                                    + (nband == fp6Memory.bandTriggerPolarity ? " Pol" : " ")
                                    + (nband == fp6Memory.bandTriggerMax ? " MAX" : " ")
                            );
                        }
                        System.out.println("DEBUG: --------END\n");
                    }
                    //
                }
            } else if (resultType == TRIGGER) {	// show triggers
                if (acceptedPick) {
                    sampleNew[indexUncertaintyPick] = 1.0f;
                    sampleNew[nsamp] = 0.0f;
                }
            } else if (resultType == BANDS) {	// show bands
                sampleNew[nsamp] = (float) fp6Memory.test[index_band];
                //sampleNew[nsamp] = (float) fp6Memory.test[0];
                //sampleNew[nsamp] = (float) fp6Memory.test[numBands - 1];
            } else if (resultType == CHAR_FUNC) {	    // show char function
                sampleNew[nsamp] = (float) charFunctClippedMaxOverBands;

            }

            //mem.lastDiffSample = currentDiffSample;
        }
        if (useMemory) {
            // correct memory index values for sample length
            for (int i = 0; i < fp6Memory.nSampleMemory; i++) {
                // AJL 20091214
                for (int nband = 0; nband < numBands; nband++) {
                    fp6Memory.indexUncertainty[nband][i] -= sample.length;
                }
                // END - AJL 20091214
            }
            if (fp6Memory.allowNewPickIndex != INT_UNSET) {
                fp6Memory.allowNewPickIndex -= sample.length;
            }
            fp6Memory.canAllowNewPickIndex -= sample.length;
        } else {
            fp6Memory = null;
        }
        if (resultType == BANDS) {
            if (DEBUG) {
                System.out.println("DEBUG: Plot BANDS: index:" + index_band + ", period:" + bandParameters[index_band].period);
            }
            // ANALYSIS
            //index_band += 1;
            index_band += 3;
        }
        return (sampleNew);

    }

    /**
     * Returns true if this process supports memory usage
     *
     * @return true if this process supports memory usage, false otherwise
     */
    @Override
    public boolean supportsMemory() {

        return (true);

    }

    // following methods support backwards compatibility with FilterPicker5
    /**
     * Sets the approximate longest filter band period when the bands are not explicitly set
     *
     * @param filterWindow approximate longest filter band period
     * @throws TimeDomainException
     */
    public void setFilterWindow(double filterWindow) throws TimeDomainException {

        if (filterWindow != FILTER_WINDOW_NOT_USED && (filterWindow < FILTER_WINDOW_MIN || filterWindow > FILTER_WINDOW_MAX)) {
            throw new TimeDomainException(TimeDomainText.invalid_filterWindow_value + ": " + filterWindow);
        }

        if (this.filterWindow != filterWindow) {
            this.filterWindow = filterWindow;
            if (filterWindow != FILTER_WINDOW_NOT_USED) {
                this.bandParameters = null;  // changing filterWindow will change picker parameters (numBands)
            }
        }

    }

    /**
     * Returns filterWindow
     *
     * @return filterWindow
     */
    public double getFilterWindow() {
        return filterWindow;
    }

    /**
     * Sets the approximate longest filter band period when the bands are not explicitly set
     *
     * @param str approximate longest filter band period (floating point string)
     * @throws TimeDomainException
     */
    public void setFilterWindow(String str) throws TimeDomainException {
        try {
            setFilterWindow(Double.valueOf(str));
        } catch (NumberFormatException e) {
            throw new TimeDomainException(TimeDomainText.invalid_filterWindow_value + ": " + str);
        }
    }

    /**
     * Initialize filter bands automatically. Bands will be spaced by bandWidthFactor from bandWidthFactor * deltaTime * 2.0 to first period greater
     * than specified filterWindow; thresholdScaleFactor is set to 1.0
     *
     * gives backwards compatibility with FilterPicker5
     *
     * @param filterWindow
     * @throws TimeDomainException
     */
    protected void initializeBandsAuto(double filterWindow) throws TimeDomainException {

        // 20150318 AJL - start from bandwidth above dt
        //double period0 = bandWidthFactor * deltaTime;
        // 20150522 AJL - start from bandWidthFactor * Nyquist period (2*dt)
        double period0 = bandWidthFactor * deltaTime * 2.0;

        // determine number of bands
        //double period = 2.0 * deltaTime;    // 20150315 AJL - start from Nyquist (FP5 started from dt)
        double period = period0;    // 20150318 AJL - start from bandwidth above dt
        int nBand = 1;   // number of powers of 2 to process
        while (period < filterWindow) {
            nBand++;
            period *= bandWidthFactor;
        }
        numBands = nBand;

        // initialize data fields
        FilterPicker6_BandParameters[] params = new FilterPicker6_BandParameters[numBands];
        period = period0;
        double thresholdScaleFactor = 1.0;
        for (int nband = 0; nband < numBands; nband++) {
            //thresholdScaleFactor = Math.sqrt(1.0 + Math.log(period / period0)) ;   // 20150325 AJL - test, threshold prop to period, downweight HF
            params[nband] = new FilterPicker6_BandParameters(period, thresholdScaleFactor, DisplayMode.DISPLAY_MODE_PERIOD);
            period *= bandWidthFactor;
        }
        setBandParams(params);

    }

    // phase shift: http://nbviewer.ipython.org/github/demotu/BMC/blob/master/notebooks/DataFiltering.ipynb
    /**
     * Calculated a phase shift corresponding to the applied bandpass filter at the specified period
     *
     * @param period
     * @return phase shift
     */
    protected int calculatePhaseShiftInSamples(double period) {
        int iphsift = (int) ((double) (numPolesBandPass / 2)
                * (period / FRACTION_PERIOD_PHASE_SHIFT_PER_2_POLES / deltaTime));    // shift / 2 poles
        //iphsift *= 2;
        return (iphsift);
    }

    /**
     * custom memory class to support re-entry to picker for contiguous data samples from the same channel
     */
// _DOC_ =============================
// _DOC_ FilterPicker6_Memory object/structure
// _DOC_ =============================
    public class FilterPicker6_Memory extends TimeDomainMemory {

        // _DOC_ =============================
        // _DOC_ picker memory for realtime processing of packets of data
        // initialize bands and filter parameters if number of bands and band periods have not been specified
        {
            if (bandParameters == null || bandParameters.length < 1) {
                try {
                    initializeBandsAuto(filterWindow);
                } catch (TimeDomainException ex) {
                    Logger.getLogger(FilterPicker6_old.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
            numBands = bandParameters.length;
            if (DEBUG) {
                System.out.println("DEBUG: FilterPicker6_Memory:");
                System.out.println(" -> bands " + getBandParamsString(DisplayMode.DISPLAY_MODE_PERIOD));
                System.out.println(" -> bands " + getBandParamsString(DisplayMode.DISPLAY_MODE_FREQ));
            }
        }

        // initialize long term window constants
        double[] longDecayFactor = new double[numBands];
        double[] longDecayConst = new double[numBands];

        {
            // set long-term window for each band in proporition to band period // 20150821 AJL - Major change, longTermWindow set back to absolute time
            for (int nband = 0; nband < numBands; nband++) {
                // 20150821 AJL  double ltw = bandParameters[nband].period * longTermWindowFactor;
                double ltw = longTermWindowFactor;  // 20150821 AJL - Major change, longTermWindow set back to absolute time for all bands!  May improve noise detection
                longDecayFactor[nband] = deltaTime / ltw;
                longDecayConst[nband] = 1.0 - longDecayFactor[nband];
                //if (DEBUG) System.out.println("DEBUG: period " + bandParameters[nband].period + ", ltw " + ltw + ", longDecayFactor " + longDecayFactor[nband] + ", longDecayConst " + longDecayConst[nband]);
            }
        }
        // nLongTermWindow determined from long-term window for longest period band
        // 20150821 AJL  int nLongTermWindow = 1 + (int) (bandParameters[numBands - 1].period * longTermWindowFactor / deltaTime);
        int nLongTermWindow = 1 + (int) (longTermWindowFactor / deltaTime); // 20150821 AJL - Major change, longTermWindow set back to absolute time for all bands!  May improve noise detection
        int indexEnableTriggering = nLongTermWindow;
        boolean enableTriggering = false;
        int nTotal = -1;
        // _DOC_ set up buffers and memory arrays for previous samples and their statistics
        /* 20150214 AJL - FP5->FP6
         int nBand = 1;   // number of powers of 2 to process

         {
         int nTemp = 1;
         int numPrevious = (int) (filterWindow / deltaTime);  // estimate of number of previous samples to bufer
         while (nTemp < numPrevious) {
         nBand++;
         nTemp *= 2;
         }
         numPrevious = nTemp;    // numPrevious is now a power of 2
         //System.out.println("TP DEBUG numPrevious, nBand " + numPrevious + ", " + nBand);
         }*/

        // initalize filter parameters
        {
            // normalize thresholdScaleFactor'
            double sfmax = -Float.MAX_VALUE;
            for (FilterPicker6_BandParameters param : bandParameters) {
                sfmax = Math.max(sfmax, param.thresholdScaleFactor);
            }
            if (sfmax > Float.MIN_NORMAL) {
                for (FilterPicker6_BandParameters param : bandParameters) {
                    param.thresholdScaleFactor /= sfmax;
                }
            }
        }

        double[] xRec = new double[numBands];
        double[] test = new double[numBands];
        // 20150314 AJL  double[][] filteredSample = new double[numBands][numPolesHighPass + numPolesBandPass];
        double[] currentFilteredSample = new double[numBands];
        double[] lastFilteredSample = new double[numBands];

        // 20150315 AJL - changed LP+HP filters to bandpass filter
        // http://www.exstrom.com/journal/sigproc/ -> bwbpf.c (net/alomax/timedom/fp6/bwbpf.c)
        //double[][][] prevSample = new double[numBands][numPolesBandPass][3];
        //double[][][] prevFilteredSample = new double[numBands][numPolesBandPass][3];
        double[][] w0 = new double[numBands][numPolesBandPass / 4];
        double[][] w1 = new double[numBands][numPolesBandPass / 4];
        double[][] w2 = new double[numBands][numPolesBandPass / 4];
        double[][] w3 = new double[numBands][numPolesBandPass / 4];
        double[][] w4 = new double[numBands][numPolesBandPass / 4];
        double[][] A = new double[numBands][numPolesBandPass / 4];
        double[][] d1 = new double[numBands][numPolesBandPass / 4];
        double[][] d2 = new double[numBands][numPolesBandPass / 4];
        double[][] d3 = new double[numBands][numPolesBandPass / 4];
        double[][] d4 = new double[numBands][numPolesBandPass / 4];

        {
            // http://www.exstrom.com/journal/sigproc/
            if (DEBUG_PICK) {
                System.out.print("DEBUG: FilterPicker6_Memory: periods added:");
            }
            double nyquist = 0.5 / deltaTime;
            for (int nband = 0; nband < numBands; nband++) {
                if (DEBUG_PICK) {
                    System.out.print(" " + nband + ":" + (float) bandParameters[nband].period);
                }
                //
                double freq = 1.0 / bandParameters[nband].period;
                double fmultiplier = Math.pow(bandWidthFactor, 1.5);    // gives best reconstruction of delta function
                double f1 = freq * fmultiplier;
                f1 = Math.min(f1, nyquist);
                double f2 = freq / fmultiplier;
                double s = 1.0 / deltaTime;
                //
                double a = Math.cos(Math.PI * (f1 + f2) / s) / Math.cos(Math.PI * (f1 - f2) / s);
                double a2 = a * a;
                double b = Math.tan(Math.PI * (f1 - f2) / s);
                double b2 = b * b;
                double r;
                //
                int n = numPolesBandPass / 4;
                for (int i = 0; i < n; ++i) {
                    w0[nband][i] = 0.0;
                    w1[nband][i] = 0.0;
                    w2[nband][i] = 0.0;
                    w3[nband][i] = 0.0;
                    w4[nband][i] = 0.0;
                    r = Math.sin(Math.PI * (2.0 * i + 1.0) / (4.0 * n));
                    s = b2 + 2.0 * b * r + 1.0;
                    A[nband][i] = b2 / s;
                    d1[nband][i] = 4.0 * a * (1.0 + b * r) / s;
                    d2[nband][i] = 2.0 * (b2 - 2.0 * a2 - 1.0) / s;
                    d3[nband][i] = 4.0 * a * (1.0 - b * r) / s;
                    d4[nband][i] = -(b2 - 2.0 * b * r + 1.0) / s;
                }
            }

            if (DEBUG_PICK) {
                System.out.println("");
            }
        }
        /*
         // http://www.dspguide.com/ch19/3.htm
         double[][][] prevSample = new double[numBands][numPolesBandPass][3];
         double[][][] prevFilteredSample = new double[numBands][numPolesBandPass][3];
         double[] coeff_a0 = new double[numBands];
         double[] coeff_a1 = new double[numBands];
         double[] coeff_a2 = new double[numBands];
         double[] coeff_b1 = new double[numBands];
         double[] coeff_b2 = new double[numBands];
         {
         // http://www.dspguide.com/ch19/3.htm
         System.out.print("FilterPicker6_Memory: periods added:");
         for (int nband = 0; nband < numBands; nband++) {
         System.out.print(" " + (float) bandParameters[nband].period);
         // Filter coefficients from:
         // The Scientist and Engineer's Guide to Digital Signal Processing By Steven W. Smith, Ph.D.
         // Hard Cover, 1997, ISBN 0-9660176-3-3
         // http://www.dspguide.com/ch19/3.htm
         // To use these equations, first select the center frequency, f, and the bandwidth, BW.
         // Both of these are expressed as a fraction of the sampling rate, and therefore in the
         // range of 0 to 0.5. Next, calculate R, and then K, and then the recursion coefficients.
         double freq = deltaTime / bandParameters[nband].period;
         double bandwidth = deltaTime * 2.0 * (bandWidthFactor * freq - freq);
         double r = 1.0 - 3.0 * bandwidth;
         double cos_2pi_freq = Math.cos(2.0 * Math.PI * freq);
         double k = (1.0 - 2.0 * r * cos_2pi_freq + r * r) / (2.0 - 2.0 * cos_2pi_freq);
         coeff_a0[nband] = 1.0 - k;
         coeff_a1[nband] = 2.0 * (k - r) * cos_2pi_freq;
         coeff_a2[nband] = r * r - k;
         coeff_b1[nband] = 2.0 * r * cos_2pi_freq;
         coeff_b2[nband] = -r * r;
         }
         System.out.println("");
         }*/
        // 20150314 AJL  double lastSample = Double.MAX_VALUE;
        //double lastDiffSample = 0.0;
        // _DOC_ set clipped limit of maximum char funct value to 5 * threshold1 to avoid long recovery time after strong events
        // 20150318 AJL
        double maxCharFunctValue = 5.0 * threshold1;
        //double maxCharFunctValue = 5.0 * threshold2; // 20150318 AJL - changed to threshold2 since threshold1 could be set very low.
        //double maxCharFunctValue = Double.MAX_VALUE; // 201500821 AJL - TEST no limit, seems to make no difference for Parkfield_2004/bdsn_cit/PKD_mainshock/PKD_Z.sac !!!
        double[] mean_xRec = new double[numBands];
        double[] mean_stdDev_xRec = new double[numBands];
        double[] mean_var_xRec = new double[numBands];
        // AJL 20091214
        double[] charFunctUncertainty = new double[numBands];
        double[] charFunctUncertaintyLast = new double[numBands];
        double[] charFunctUncertaintyDiffLast = new double[numBands];
        // AJL 20091214 double charFunctLast;
        double[] uncertaintyThreshold = new double[numBands];
        // 20150226 AJL - FP5->FP6
        double[] thresholdScaleFactor = new double[numBands];

        {
            for (int nband = 0; nband < numBands; nband++) {
                mean_xRec[nband] = 0.0;
                mean_stdDev_xRec[nband] = maxCharFunctValue;
                mean_var_xRec[nband] = 0.0;
                //
                charFunctUncertaintyLast[nband] = 0.0;
                charFunctUncertaintyDiffLast[nband] = 0.0;
                uncertaintyThreshold[nband] = threshold1 / 2.0;
                thresholdScaleFactor[nband] = bandParameters[nband].thresholdScaleFactor;
            }
        }
        double maxUncertaintyThreshold = threshold1 / 2.0;
        // END - AJL 20091214
        // 20150316 AJL  double minUncertaintyThreshold = 0.5;
        //double minUncertaintyThreshold = 0.0; // 20150316 AJL - CF shifted by -1.0
        double minUncertaintyThreshold = -0.5; // 20150605 AJL - CF shifted by -1.0
        //double minUncertaintyThreshold = 1.0; // 20150401 AJL - CF shifted by -1.0
        //double maxAllowNewPickThreshold = 2.0;
        double maxAllowNewPickThreshold = Float.MIN_NORMAL; // 20150901 AJL - CF shifted by -1.0
        // TEST double maxAllowNewPickThreshold = 1e6;
        int nTUpEvent = (int) Math.round(tUpEvent / deltaTime) + 1;
        int nTUpEventMin = (int) Math.round(tUpEventMin / deltaTime) + 1;

        {
            if (nTUpEvent < 1) {
                nTUpEvent = 1;
            }
        }
        // _DOC_ total sample memory needed is nTUpEvent + approx samples to make maximum period
        int ipermax = (int) (bandParameters[numBands - 1].period / deltaTime);
        int nSampleMemory = nTUpEvent + 1 + ipermax;
        //int nSampleMemory = nTUpEvent;  // polarity estimated w/o phase shift
        int[][] indexUncertainty = new int[numBands][nSampleMemory];  // AJL 20091214
        double[][] polarityDerivativeSum = new double[numBands][nSampleMemory];
        double[][] polaritySumAbsDerivative = new double[numBands][nSampleMemory];
        // _DOC_ criticalIntegralCharFunct is tUpEvent * threshold2
        double criticalIntegralCharFunct = (double) (nTUpEvent) * threshold2;   // one less than number of samples examined
        // _DOC_ integralCharFunctClipped is integral of charFunctMaxOverBands values for last nTUpEvent samples, charFunctMaxOverBands values possibly limited if around trigger time
        double[] integralCharFunctClipped = new double[nSampleMemory];
        double[] integralCharFunctClippedShort = new double[nSampleMemory];
        // flag to prevent next trigger until charFunc drops below threshold2
        int allowNewPickIndex = INT_UNSET;
        int canAllowNewPickIndex = -1;  // 20150611 AJL - added
        double[] charFunctClippedValue = new double[nSampleMemory];
        double[] charFunctValueMax = new double[nSampleMemory];     // maximum CF within nTUpEvent window
        double[][] charFunctValue = new double[numBands][nSampleMemory];     // CF for each band within nTUpEvent window  // 20150302 AJL - FP5->FP6
        int[] bandCharFuncMax = new int[nSampleMemory];
        int[] longestPerBandTriggered = new int[nSampleMemory];
        int shortestPerBandTriggered = -1;

        {
            for (int k = 0; k < nSampleMemory; k++) {
                // AJL 20091214
                for (int nband = 0; nband < numBands; nband++) {
                    indexUncertainty[nband][k] = 0;
                    // 20150302 AJL - FP5->FP6
                    charFunctValue[nband][k] = -1.0;
                    polarityDerivativeSum[nband][k] = 0.0;
                    polaritySumAbsDerivative[nband][k] = 0.0;
                }
                // END - AJL 20091214
                charFunctClippedValue[k] = 0.0;
                charFunctValueMax[k] = 0.0;
                bandCharFuncMax[k] = 0;
                longestPerBandTriggered[k] = 0;
            }
        }
        int sampleMemBufPtr = 0;
        int pickPolarity = PickData.POLARITY_UNKNOWN;
        int bandTriggerMax = -1;
        int bandTriggerPolarity = -1;

        /** constructor
         */
        public FilterPicker6_Memory() {

        }
    }
}	// End class

