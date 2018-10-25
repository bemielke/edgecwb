/*
 * This file is part of the Anthony Lomax Java Library.
 *
 * Copyright (C) 2006-2009 Anthony Lomax <anthony@alomax.net www.alomax.net>
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
package net.alomax.timedom;

import net.alomax.math.*;

// _DOC_ =============================
// _DOC_ FilterPicker5
// _DOC_ =============================
/**
 *
 * @author anthony
 */
public class FilterPicker5 extends BasicPicker {

    /**
     *
     */
    public double filterWindow = 4.0;
    // _DOC_ the filter window (filterWindow) in seconds determines how far back in time the previous samples are examined.  The filter window will be adjusted upwards to be an integer N power of 2 times the sample interval (deltaTime).  Then numRecursive = N + 1 "filter bands" are created.  For each filter band n = 0,N  the data samples are processed through a simple recursive filter backwards from the current sample, and picking statistics and characteristic function are generated.  Picks are generated based on the maximum of the characteristic funciton values over all filter bands relative to the threshold values threshold1 and threshold2.
    private static final double WINDOW_MIN = Double.MIN_VALUE;
    private static final double WINDOW_MAX = Double.MAX_VALUE;
    /**
     *
     */
    public double longTermWindow = 10.0;
    // _DOC_ the long term window (longTermWindow) determines: a) a stabilisation delay time after the beginning of data; before this delay time picks will not be generated. b) the decay constant of a simple recursive filter to accumlate/smooth all picking statistics and characteristic functions for all filter bands.
    /**
     *
     */
    public double threshold1 = 10.0;    // threshold to intiatie trigger
    // _DOC_ threshold1 sets the threshold to trigger a pick event (potential pick).  This threshold is reached when the (clipped) characteristic function for any filter band exceeds threshold1.
    /**
     *
     */
    public double threshold2 = 10.0;       // threshold to maintain trigger
    // _DOC_ threshold2 sets the threshold to declare a pick (pick will be accepted when tUpEvent reached).  This threshold is reached when the integral of the (clipped) characteristic function for any filter band over the window tUpEvent exceeds threshold2 * tUpEvent (i.e. the average (clipped) characteristic function over tUpEvent is greater than threshold2)..
    /**
     *
     */
    public double tUpEvent = 0.5;
    // _DOC_ tUpEvent determines the maximum time the integral of the (clipped) characteristic function is accumlated after threshold1 is reached (pick event triggered) to check for this integral exceeding threshold2 * tUpEvent (pick declared).
    private static final double THRESHOLD_MIN = Double.MIN_VALUE;
    private static final double THRESHOLD_MAX = Double.MAX_VALUE;
    private static final double TIME_MIN = -Double.MAX_VALUE;
    private static final double TIME_MAX = Double.MAX_VALUE;
    private static final int INT_UNSET = -Integer.MAX_VALUE / 2;
    // instance variables needed for memory
    private FilterPicker5_Memory mem = null;
    // _DOC_ a memory structure/object is used so that this function can be called repetedly for packets of data in sequence from the same channel.
    private double deltaTime;

    /** constructor
     * @param localeText
     * @param longTermWindow
     * @param threshold1
     * @param tUpEvent
     * @param threshold2
     * @param direction
     * @param filterWindow
     */
    public FilterPicker5(String localeText, double longTermWindow, double threshold1, double threshold2,
            double tUpEvent, double filterWindow, int direction) {

        super(localeText, direction);

        this.longTermWindow = longTermWindow;
        this.threshold1 = threshold1;
        this.threshold2 = threshold2;
        this.tUpEvent = tUpEvent;
        this.filterWindow = filterWindow;

    }

    /** copy constructor
     * @param tp
     */
    public FilterPicker5(FilterPicker5 tp) {

        super(tp.direction);

        this.resultType = tp.resultType;

        this.longTermWindow = tp.longTermWindow;
        this.threshold1 = tp.threshold1;
        this.threshold2 = tp.threshold2;
        this.tUpEvent = tp.tUpEvent;
        this.filterWindow = tp.filterWindow;

    }

    /** Method to set longTermWindowValue
     * @param longTermWindowValue
     * @throws TimeDomainException
     */
    public void setLongTermWindow(double longTermWindowValue) throws TimeDomainException {
        if (longTermWindowValue < WINDOW_MIN || longTermWindowValue > WINDOW_MAX) {
            throw new TimeDomainException(
                    TimeDomainText.invalid_long_term_window_value + ": " + longTermWindowValue);
        }

        longTermWindow = longTermWindowValue;
    }

    /** Method to set longTermWindowValue
     * @param str
     * @throws TimeDomainException
     */
    public void setLongTermWindow(String str) throws TimeDomainException {

        double longTermWindowValue;

        try {
            longTermWindowValue = Double.valueOf(str).doubleValue();
        } catch (NumberFormatException e) {
            throw new TimeDomainException(TimeDomainText.invalid_long_term_window_value + ": " + str);
        }

        setLongTermWindow(longTermWindowValue);
    }

    /** Method to set threshold1Value
     * @param threshold1Value
     * @throws TimeDomainException
     */
    public void setThreshold1(double threshold1Value) throws TimeDomainException {
        if (threshold1Value < THRESHOLD_MIN || threshold1Value > THRESHOLD_MAX) {
            throw new TimeDomainException(TimeDomainText.invalid_threshold1_value + ": " + threshold1Value);
        }

        threshold1 = threshold1Value;
    }

    /** Method to set threshold1Value
     * @param str
     * @throws TimeDomainException
     */
    public void setThreshold1(String str) throws TimeDomainException {

        double threshold1Value;

        try {
            threshold1Value = Double.valueOf(str).doubleValue();
        } catch (NumberFormatException e) {
            throw new TimeDomainException(TimeDomainText.invalid_threshold1_value + ": " + str);
        }

        setThreshold1(threshold1Value);
    }

    /** Method to set threshold2Value
     * @param threshold2Value
     * @throws TimeDomainException
     */
    public void setThreshold2(double threshold2Value) throws TimeDomainException {
        if (threshold2Value < THRESHOLD_MIN || threshold2Value > THRESHOLD_MAX) {
            throw new TimeDomainException(
                    TimeDomainText.invalid_threshold2_value + ": " + threshold2Value);
        }

        threshold2 = threshold2Value;
    }

    /** Method to set threshold2Value
     * @param str
     * @throws TimeDomainException
     */
    public void setThreshold2(String str) throws TimeDomainException {

        double threshold2Value;

        try {
            threshold2Value = Double.valueOf(str).doubleValue();
        } catch (NumberFormatException e) {
            throw new TimeDomainException(TimeDomainText.invalid_threshold2_value + ": " + str);
        }

        setThreshold2(threshold2Value);
    }

    /** Method to set tUpEventValue
     * @param tUpEventValue
     * @throws TimeDomainException
     */
    public void setTUpEvent(double tUpEventValue) throws TimeDomainException {
        if (tUpEventValue < TIME_MIN || tUpEventValue > TIME_MAX) {
            throw new TimeDomainException(TimeDomainText.invalid_tUpEvent_value + ": " + tUpEventValue);
        }

        tUpEvent = tUpEventValue;
    }

    /** Method to set tUpEventValue
     * @param str
     * @throws TimeDomainException
     */
    public void setTUpEvent(String str) throws TimeDomainException {

        double tUpEventValue;

        try {
            tUpEventValue = Double.valueOf(str).doubleValue();
        } catch (NumberFormatException e) {
            throw new TimeDomainException(TimeDomainText.invalid_tUpEvent_value + ": " + str);
        }

        setTUpEvent(tUpEventValue);
    }

    /** Method to set filterWindowValue
     * @param filterWindowValue
     * @throws TimeDomainException
     */
    public void setFilterWindow(double filterWindowValue) throws TimeDomainException {
        if (filterWindowValue < WINDOW_MIN || filterWindowValue > WINDOW_MAX) {
            throw new TimeDomainException(TimeDomainText.invalid_filterWindow_value + ": " + filterWindowValue);
        }

        filterWindow = filterWindowValue;
    }

    /** Method to set filterWindowValue
     * @param str
     * @throws TimeDomainException
     */
    public void setFilterWindow(String str) throws TimeDomainException {

        double filterWindowValue;

        try {
            filterWindowValue = Double.valueOf(str).doubleValue();
        } catch (NumberFormatException e) {
            throw new TimeDomainException(TimeDomainText.invalid_filterWindow_value + ": " + str);
        }

        setFilterWindow(filterWindowValue);
    }

    /** Method to check settings
     * @throws TimeDomainException
     */
    @Override
    public void checkSettings() throws TimeDomainException {

        super.checkSettings();

        String errMessage = "";
        int badSettings = 0;

        if (longTermWindow < WINDOW_MIN || longTermWindow > WINDOW_MAX) {
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
        if (tUpEvent < TIME_MIN || tUpEvent > TIME_MAX) {
            errMessage += ": " + TimeDomainText.invalid_tUpEvent_value;
            badSettings++;
        }
        if (filterWindow < TIME_MIN || filterWindow > TIME_MAX) {
            errMessage += ": " + TimeDomainText.invalid_filterWindow_value;
            badSettings++;
        }

        if (badSettings > 0) {
            throw new TimeDomainException(errMessage + ".");
        }

    }

    /**  Update fields in TimeSeries object */
    @Override
    public void updateFields(TimeSeries timeSeries) {

        super.updateFields(timeSeries);

    }
    // TEST
    private static int index_recursive = 0;

    /** function to calculate picks  */
    public final float[] apply(double dt, float[] sample) {

        // _DOC_ =============================
        // _DOC_ apply algorithm

        // initialize instance variables needed for memory
        deltaTime = dt;
        // initialize memory object
        if (mem == null) {
            mem = new FilterPicker5_Memory(sample);
        }

        // create array for time-series results
        float[] sampleNew = null;
        if (resultType == TRIGGER || resultType == CHAR_FUNC) {
            sampleNew = new float[sample.length];
            //sampleNew[0] = sample[sample.length - 1] = 0.0f;
        }

        // _DOC_ set clipped limit of maximum char funct value to 5 * threshold1 to avoid long recovery time after strong events
        double maxCharFunctValue = 5.0 * threshold1;


        // _DOC_ =============================
        // _DOC_ loop over all samples
        boolean error1_not_printed = true;
        double charFunctValueTrigger = -1.0;  // AJL 20091216
        int indexUpEventTrigger = -1;
        int indexUncertaintyPick = -1;
        for (int n = 0; n < sample.length; n++) {

            boolean acceptedPick = false;

            // _DOC_ update index of nTUpEvent length up event window buffers
            int upEventBufPtrLast = mem.upEventBufPtr;
            mem.upEventBufPtr = (mem.upEventBufPtr + 1) % mem.nTUpEvent;

            // _DOC_ =============================
            // _DOC_ characteristic function is  (E2 - mean_E2) / mean_stdDev_E2
            // _DOC_    where E2 = (filtered band value current - filtered band value previous)**2
            // _DOC_    where value previous is taken futher back for longer filter bands
            double charFunct = 0.0;
            double charFunctClipped = 0.0;
            // _DOC_ evaluate current signal values
            double currentSample = sample[n];
            // _DOC_ filters are applied to first difference of signal values
            double currentDiffSample = currentSample - mem.lastSample;
            double currentFilteredSample;
            // _DOC_ loop over numRecursive filter bands
            for (int k = mem.numRecursive - 1; k >= 0; k--) {
                // _DOC_  apply two single-pole HP filters
                // _DOC_  http://en.wikipedia.org/wiki/High-pass_filter    y[i] := α * (y[i-1] + x[i] - x[i-1])
                currentFilteredSample = mem.highPassConst[k] * (mem.filteredSample[k][0] + currentDiffSample);
                double currentDiffSample2 = currentFilteredSample - mem.filteredSample[k][0];
                mem.filteredSample[k][0] = currentFilteredSample;
                currentFilteredSample = mem.highPassConst[k] * (mem.filteredSample[k][1] + currentDiffSample2);
                mem.filteredSample[k][1] = currentFilteredSample;
                // _DOC_  apply one single-pole LP filter
                // _DOC_  http://en.wikipedia.org/wiki/Low-pass_filter    y[i] := y[i-1] + α * (x[i] - y[i-1])
                currentFilteredSample = mem.filteredSample[k][2] + mem.lowPassConst[k] * (currentFilteredSample - mem.filteredSample[k][2]);
                mem.lastFilteredSample[k] = mem.filteredSample[k][2];
                mem.filteredSample[k][2] = currentFilteredSample;
                double dy = currentFilteredSample;
                // TEST - filtered signal
                mem.test[k] = dy;
                //
                mem.xRec[k] = dy * dy;
                double charFunctClippedTest = 0.0;  // AJL 20091214
                if (mem.mean_stdDev_xRec[k] <= Float.MIN_VALUE) {
                    if (mem.enableTriggering && error1_not_printed) {
                        System.out.println("ERROR: FilterPicker5: mem.mean_stdDev_xRec[k] <= Float.MIN_VALUE (this should not happen!) k=" + k);
                        error1_not_printed = false;
                    }
                } else {
                    double charFunctTest = (mem.xRec[k] - mem.mean_xRec[k]) / mem.mean_stdDev_xRec[k];
                    charFunctClippedTest = charFunctTest;    // AJL 20091214
                    // _DOC_ limit maximum char funct value to avoid long recovery time after strong events
                    if (charFunctClippedTest > maxCharFunctValue) {
                        charFunctClippedTest = maxCharFunctValue;
                        // save corrected mem.xRec[k]
                        mem.xRec[k] = maxCharFunctValue * mem.mean_stdDev_xRec[k] + mem.mean_xRec[k];
                    }
                    // TEST - CF band
                    //mem.test[k] = charFunctTest;
                    // _DOC_ characteristic function is maximum over numRecursive filter bands
                    if (charFunctTest >= charFunct) {
                        charFunct = charFunctTest;
                        charFunctClipped = charFunctClippedTest;
                        mem.charFuntNumRecursiveIndex[mem.upEventBufPtr] = k;
                    }
                    // _DOC_ trigger index is highest frequency with CF >= threshold1 over numRecursive filter bands
                    if (charFunctTest >= threshold1) {
                        mem.charFuntNumRecursiveIndex[mem.upEventBufPtr] = k;
                    }
                }
// AJL 20091214
                // _DOC_ =============================
                // _DOC_ update uncertainty and polarity fields
                // _DOC_ uncertaintyThreshold is at minimum char function or char funct increases past uncertaintyThreshold
                mem.charFunctUncertainty[k] = charFunctClippedTest;   // no smoothing
                // AJL 20091214 mem.charFunctLast = charFunctClipped;
                boolean upCharFunctUncertainty =
                        ((mem.charFunctUncertaintyLast[k] < mem.uncertaintyThreshold[k]) && (mem.charFunctUncertainty[k] >= mem.uncertaintyThreshold[k]));
                mem.charFunctUncertaintyLast[k] = mem.charFunctUncertainty[k];
                // _DOC_ each time characteristic function rises past uncertaintyThreshold store sample index and initiate polarity algoirithm
                if (upCharFunctUncertainty) {
                    mem.indexUncertainty[k][mem.upEventBufPtr] = n - 1;
                } else {
                    mem.indexUncertainty[k][mem.upEventBufPtr] = mem.indexUncertainty[k][upEventBufPtrLast];
                }
// END - AJL 20091214
                if (upCharFunctUncertainty) {
                    // _DOC_ initialize polarity algorithm, uses derivative of signal
                    mem.polarityDerivativeSum[k][mem.upEventBufPtr] = 0.0;
                    mem.polaritySumAbsDerivative[k][mem.upEventBufPtr] = 0.0;
                } else {
                    mem.polarityDerivativeSum[k][mem.upEventBufPtr] = mem.polarityDerivativeSum[k][upEventBufPtrLast];
                    mem.polaritySumAbsDerivative[k][mem.upEventBufPtr] = mem.polaritySumAbsDerivative[k][upEventBufPtrLast];
                }
                // _DOC_   accumulate derivative and sum of abs of derivative for polarity estimate
                // _DOC_   accumulate since last indexUncertainty
                double polarityderivativeIncrement = mem.filteredSample[k][2] - mem.lastFilteredSample[k];
                mem.polarityDerivativeSum[k][mem.upEventBufPtr] += polarityderivativeIncrement;
                mem.polaritySumAbsDerivative[k][mem.upEventBufPtr] += Math.abs(polarityderivativeIncrement);
            }


            // _DOC_ =============================
            // _DOC_ trigger and pick logic
            // _DOC_ only apply trigger and pick logic if past stabilisation time (longTermWindow)
            if (mem.enableTriggering || mem.nTotal++ > mem.indexEnableTriggering) {  // past stabilisation time

                mem.enableTriggering = true;

                // _DOC_ update charFunctClipped values, subtract oldest value, and save provisional current sample charFunct value
                // _DOC_ to avoid spikes, do not use full charFunct value, may be very large, instead use charFunctClipped
                mem.integralCharFunctClipped[mem.upEventBufPtr] =
                        mem.integralCharFunctClipped[upEventBufPtrLast] - mem.charFunctClippedValue[mem.upEventBufPtr] + charFunctClipped;
                mem.charFunctClippedValue[mem.upEventBufPtr] = charFunctClipped;
                mem.charFunctValue[mem.upEventBufPtr] = charFunct;

                // _DOC_ if new picks allowd, check if integralCharFunct over last tUpEvent window is greater than threshold
                if (mem.allowNewPickIndex != INT_UNSET && mem.integralCharFunctClipped[mem.upEventBufPtr] >= mem.criticalIntegralCharFunct) {

                    // _DOC_ find last point in tUpEvent window where charFunct rose past threshold1 and integralCharFunct greater than threshold back to this point
                    int m = mem.upEventBufPtr;
                    double integralCharFunctClippedWindow = mem.charFunctClippedValue[m];
                    int k = 0;
                    while (k++ < mem.nTUpEvent - 1 && n - k > mem.allowNewPickIndex) {
                        m--;
                        if (m < 0) {
                            m += mem.nTUpEvent;
                        }
                        integralCharFunctClippedWindow += mem.charFunctClippedValue[m];
                        if (mem.charFunctValue[m] >= threshold1) {
                            int l = m - 1;
                            if (l < 0) {
                                l += mem.nTUpEvent;
                            }
                            if (mem.charFunctValue[l] < threshold1) {
                                // integralCharFunct is integralCharFunct from current point back to point m
                                if (integralCharFunctClippedWindow >= mem.criticalIntegralCharFunct) {
                                    acceptedPick = true;
                                    // _DOC_ save characteristic function value as indicator of pick strenth
                                    charFunctValueTrigger = mem.charFunctValue[m];  // AJL 20091216
                                    mem.triggerNumRecursiveIndex = mem.charFuntNumRecursiveIndex[m];
                                    // _DOC_ set index for pick uncertainty begin and end
                                    indexUpEventTrigger = n - k;
                                    indexUncertaintyPick = mem.indexUncertainty[mem.triggerNumRecursiveIndex][m];  // AJL 20091214
                                    // _DOC_ evaluate polarity based on accumulated derivative
                                    // _DOC_    (=POS if derivative_sum > 0, = NEG if derivative_sum < 0,
                                    // _DOC_     and if ratio larger abs derivative_sum / abs_derivative_sum > 0.667,
                                    // _DOC_     =UNK otherwise)
                                    int iPolarity = m + 1;  // evaluate polarity at 1 point past trigger point
                                    if (iPolarity >= mem.nTUpEvent) {
                                        iPolarity -= mem.nTUpEvent;
                                    }
                                    mem.pickPolarity = PickData.POLARITY_UNKNOWN;
                                    if (mem.polarityDerivativeSum[mem.triggerNumRecursiveIndex][iPolarity] > 0.0
                                            && mem.polarityDerivativeSum[mem.triggerNumRecursiveIndex][iPolarity] / mem.polaritySumAbsDerivative[mem.triggerNumRecursiveIndex][iPolarity] > 0.667) {
                                        mem.pickPolarity = PickData.POLARITY_POS;
                                    } else if (mem.polarityDerivativeSum[mem.triggerNumRecursiveIndex][iPolarity] < 0.0
                                            && -mem.polarityDerivativeSum[mem.triggerNumRecursiveIndex][iPolarity] / mem.polaritySumAbsDerivative[mem.triggerNumRecursiveIndex][iPolarity] > 0.667) {
                                        mem.pickPolarity = PickData.POLARITY_NEG;
                                    }
                                    //System.out.println("" + mem.pickPolarity + "  mem.polarityCount[iPolarity]:" + mem.polaritySumAbsDerivative[mem.triggerNumRecursiveIndex][iPolarity] + "  indexUpEventTrigger:" + indexUpEventTrigger + "  indexUncertaintyPick:" + indexUncertaintyPick + " ratio:" + mem.polarityDerivativeSum[mem.triggerNumRecursiveIndex][iPolarity] / mem.polaritySumAbsDerivative[mem.triggerNumRecursiveIndex][iPolarity]);
                                    //System.out.println("mem.uncertaintyThreshold:" + mem.uncertaintyThreshold);
                                    mem.allowNewPickIndex = INT_UNSET;
                                    break;
                                }
                            }
                        }
                    }
                }

                // _DOC_ if no pick, check if charFunctUncertainty has dropped below threshold maxAllowNewPickThreshold to allow new picks
                if (!acceptedPick && mem.allowNewPickIndex == INT_UNSET) {  // no pick and no allow new picks
                    // AJL 20091214
                    int k = 0;
                    for (; k < mem.numRecursive; k++) {
                        if (mem.charFunctUncertainty[k] > mem.maxAllowNewPickThreshold) // do not allow new picks
                        {
                            break;
                        }
                    }
                    if (k == mem.numRecursive) {
                        mem.allowNewPickIndex = n;
                    }
                    // END AJL 20091214
                }
            }


            // _DOC_ =============================
            // _DOC_ update "true", long-term statistic based on current signal values based on long-term window
            // long-term decay formulation
            // _DOC_ update long-term means of x, dxdt, E2, var(E2), uncertaintyThreshold
            for (int k = 0; k < mem.numRecursive; k++) {
                mem.mean_xRec[k] = mem.mean_xRec[k] * mem.longDecayConst + mem.xRec[k] * mem.longDecayFactor;
                double dev = mem.xRec[k] - mem.mean_xRec[k];
                mem.mean_var_xRec[k] = mem.mean_var_xRec[k] * mem.longDecayConst + dev * dev * mem.longDecayFactor;
                // _DOC_ mean_stdDev_E2 is sqrt(long-term mean var(E2))
                mem.mean_stdDev_xRec[k] = Math.sqrt(mem.mean_var_xRec[k]);
                mem.uncertaintyThreshold[k] = mem.uncertaintyThreshold[k] * mem.longDecayConst + mem.charFunctUncertainty[k] * mem.longDecayFactor;
                if (mem.uncertaintyThreshold[k] > mem.maxUncertaintyThreshold) {
                    mem.uncertaintyThreshold[k] = mem.maxUncertaintyThreshold;
                } else if (mem.uncertaintyThreshold[k] < mem.minUncertaintyThreshold) {
                    mem.uncertaintyThreshold[k] = mem.minUncertaintyThreshold;
                }
            }


            // _DOC_ =============================
            //  _DOC_ act on result, save pick if pick accepted at this sample

            if (resultType == TRIGGER) {	// show triggers
                if (acceptedPick) {
                    // 20150216 AJL - bug fix
                    //sampleNew[n] = 1.0f;
                    sampleNew[indexUncertaintyPick] = 1.0f;
                    sampleNew[n] = 0.0f;
                }
                // TEST...
                //sampleNew[n] = (float) mem.test[0];
                //sampleNew[n] = (float) mem.test[index_recursive];
                //sampleNew[n] = (float) mem.test[mem.numRecursive - 1];
                //
            } else if (resultType == CHAR_FUNC) {	    // show char function
                sampleNew[n] = (float) charFunctClipped;
            } else {                // generate picks
                // PICK
                if (acceptedPick) {
                    // _DOC_ if pick accepted, save pick time, uncertainty, strength (integralCharFunct) and polarity
                    // _DOC_    pick time is uncertainty threshold (characteristic function rose past
                    // _DOC_    uncertaintyThreshold) and trigger time (characteristic function >= threshold1)
                    // _DOC_    pick begin is pick time - (trigger time - uncertainty threshold)
                    int indexBeginPick = indexUncertaintyPick - (indexUpEventTrigger - indexUncertaintyPick);
                    int indexEndPick = indexUpEventTrigger;
                    double triggerPeriod = mem.period[mem.triggerNumRecursiveIndex];
                    // check that uncertainty range is >= triggerPeriod / 8.0  // 20101014 AJL
                    double uncertainty = deltaTime * ((double) (indexEndPick - indexBeginPick));
                    if (uncertainty < triggerPeriod / 20.0) {
                        int ishift = (int) (0.5 * (triggerPeriod / 20.0 - uncertainty) / deltaTime);
                        // advance uncertainty index
                        indexBeginPick -= ishift;
                        // delay trigger index
                        indexEndPick += ishift;
                    }
                    PickData pickData = new PickData((double) indexBeginPick, (double) indexEndPick,
                            mem.pickPolarity, charFunctValueTrigger, // AJL 20091216
                            PickData.CHAR_FUNCT_AMP_UNITS, triggerPeriod);
                    triggerPickData.add(pickData);
                }
            }


            mem.lastSample = currentSample;
            mem.lastDiffSample = currentDiffSample;

        }
        if (useMemory) {
            // corect memory index values for sample length
            for (int i = 0; i < mem.nTUpEvent; i++) {
                // AJL 20091214
                for (int k = 0; k < mem.numRecursive; k++) {
                    mem.indexUncertainty[k][i] -= sample.length;
                }
                // END - AJL 20091214
            }
            if (mem.allowNewPickIndex != INT_UNSET) {
                mem.allowNewPickIndex -= sample.length;
            }
        } else {
            mem = null;
        }
        if (resultType == TRIGGER || resultType == CHAR_FUNC) {
            sample = sampleNew;
            // TEST
            index_recursive += 1;
        }
        return (sample);

    }

    /** Returns true if this process supports memory usage
     *
     * @return    true if this process supports memory usage.
     */
    @Override
    public boolean supportsMemory() {

        return (true);

    }

    /** custom memory class */
// _DOC_ =============================
// _DOC_ FilterPicker5_Memory object/structure
// _DOC_ =============================
    public class FilterPicker5_Memory extends TimeDomainMemory {

        // _DOC_ =============================
        // _DOC_ picker memory for realtime processing of packets of data
        double longDecayFactor = deltaTime / longTermWindow;
        double longDecayConst = 1.0 - longDecayFactor;
        int nLongTermWindow = 1 + (int) (longTermWindow / deltaTime);
        int indexEnableTriggering = nLongTermWindow;
        boolean enableTriggering = false;
        int nTotal = -1;
        // _DOC_ set up buffers and memory arrays for previous samples and their statistics
        int numRecursive = 1;   // number of powers of 2 to process
        int nTemp = 1;

        {
            int numPrevious = (int) (filterWindow / deltaTime);  // estimate of number of previous samples to bufer
            while (nTemp < numPrevious) {
                numRecursive++;
                nTemp *= 2;
            }
            numPrevious = nTemp;    // numPrevious is now a power of 2
            //System.out.println("TP DEBUG numPrevious, numRecursive " + numPrevious + ", " + numRecursive);
        }
        double[] xRec = new double[numRecursive];
        double[] test = new double[numRecursive];
        double[][] filteredSample = new double[numRecursive][3];
        double[] lastFilteredSample = new double[numRecursive];
        double[] mean_xRec = new double[numRecursive];
        double[] mean_stdDev_xRec = new double[numRecursive];
        double[] mean_var_xRec = new double[numRecursive];
        double[] period = new double[numRecursive];
        double[] lowPassConst = new double[numRecursive];
        double[] highPassConst = new double[numRecursive];
        double window = deltaTime / (2.0 * Math.PI);

        {
            for (int k = 0; k < numRecursive; k++) {
                mean_xRec[k] = 0.0;
                mean_stdDev_xRec[k] = 0.0;
                mean_var_xRec[k] = 0.0;
                period[k] = window * 2.0 * Math.PI;
                lowPassConst[k] = deltaTime / (window + deltaTime);
                highPassConst[k] = window / (window + deltaTime);
                /*System.out.println("TP DEBUG k, decayFactor[k], period[k] " + k + " "
                + lowPassConst[k] + " " + (float) period[k] + "s "
                + (float) (1.0 / period[k]) + "Hz " + (float) Math.log10(1.0 / period[k]) + "logHz");*/ window *= 2.0;
            }
        }
        double lastSample = Double.MAX_VALUE;
        double lastDiffSample = 0.0;
        // AJL 20091214
        double[] charFunctUncertainty = new double[numRecursive];
        double[] charFunctUncertaintyLast = new double[numRecursive];
        // AJL 20091214 double charFunctLast;
        double[] uncertaintyThreshold = new double[numRecursive];

        {
            for (int k = 0; k < numRecursive; k++) {
                uncertaintyThreshold[k] = threshold1 / 2.0;
            }
        }
        double maxUncertaintyThreshold = threshold1 / 2.0;
        // END - AJL 20091214
        double minUncertaintyThreshold = 0.5;
        double maxAllowNewPickThreshold = 2.0;
        int nTUpEvent = (int) Math.round(tUpEvent / deltaTime) + 1;

        {
            if (nTUpEvent < 1) {
                nTUpEvent = 1;
            }
        }
        int[][] indexUncertainty = new int[numRecursive][nTUpEvent];  // AJL 20091214
        double[][] polarityDerivativeSum = new double[numRecursive][nTUpEvent];
        double[][] polaritySumAbsDerivative = new double[numRecursive][nTUpEvent];
        // _DOC_ criticalIntegralCharFunct is tUpEvent * threshold2
        double criticalIntegralCharFunct = (double) (nTUpEvent) * threshold2;   // one less than number of samples examined
        // _DOC_ integralCharFunctClipped is integral of charFunct values for last nTUpEvent samples, charFunct values possibly limited if around trigger time
        double[] integralCharFunctClipped = new double[nTUpEvent];
        // flag to prevent next trigger until charFunc drops below threshold2
        int allowNewPickIndex = INT_UNSET;
        double[] charFunctClippedValue = new double[nTUpEvent];
        double[] charFunctValue = new double[nTUpEvent];
        int[] charFuntNumRecursiveIndex = new int[nTUpEvent];

        {
            for (int k = 0; k < nTUpEvent; k++) {
                // AJL 20091214
                for (int l = 0; l < numRecursive; l++) {
                    indexUncertainty[l][k] = 0;
                }
                // END - AJL 20091214
                charFunctClippedValue[k] = 0.0;
                charFunctValue[k] = 0.0;
                charFuntNumRecursiveIndex[k] = 0;
            }
        }
        int upEventBufPtr = 0;
        int pickPolarity = PickData.POLARITY_UNKNOWN;
        int triggerNumRecursiveIndex = -1;

        /**
         *
         * @param sample
         */
        public FilterPicker5_Memory(float[] sample) {

            // initialize previous samples to mean sample value

            int nmean = nLongTermWindow < sample.length ? nLongTermWindow : sample.length;
            double sample_mean = 0.0;
            for (int i = 0; i < nmean; i++) {
                sample_mean += sample[i];
            }
            sample_mean /= (double) nmean;


            for (int k = 0; k < numRecursive; k++) {
                for (int j = 0; j < 3; j++) {
                    filteredSample[k][j] = 0.0;     // set to 0.0 since derived from differential signal of sample
                }
            }

            lastSample = sample_mean;
        }
    }
}	// End class

