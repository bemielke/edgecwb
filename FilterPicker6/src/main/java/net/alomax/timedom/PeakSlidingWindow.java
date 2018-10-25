/*
 * This file is part of the Anthony Lomax Java Library.
 *
 * Copyright (C) 2008 Anthony Lomax <anthony@alomax.net www.alomax.net>
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

import java.util.*;

public class PeakSlidingWindow extends TimeDomainProcess {

    public int windowWidth = 50;
    public int measurementStep = 0;
    public double threshold = LARGE_DOUBLE;
    public String errorMessage = " ";
    // picks
    protected ArrayList<PickData> peakPicks = new ArrayList<>();
    public static final int WINDOW_MIN = 1;
    public static final int WINDOW_MAX = Integer.MAX_VALUE;
    public static final int MEASURE_STEP_MIN = 0;
    public static final int MEASURE_STEP_MAX = Integer.MAX_VALUE;
    public static final double THRESHOLD_MIN = -Double.MAX_VALUE;
    public static final double THRESHOLD_MAX = Double.MAX_VALUE;

    /** constructor */
    public PeakSlidingWindow(String localeText, int windowWidth, double threshold) {

        this.windowWidth = windowWidth;
        this.threshold = threshold;

        TimeDomainText.setLocale(localeText);
    }

    /** copy constructor */
    public PeakSlidingWindow(PeakSlidingWindow peakSlidingWindow) {

        this.windowWidth = peakSlidingWindow.windowWidth;
        this.measurementStep = peakSlidingWindow.measurementStep;
        this.threshold = peakSlidingWindow.threshold;

        this.useMemory = peakSlidingWindow.useMemory;
        if (peakSlidingWindow.memory != null) {
            this.memory = new TimeDomainMemory(peakSlidingWindow.memory);
        }

    }

    /** Method to set peakSlidingWindow window width */
    public void setWindowWidth(int windowWidth) throws TimeDomainException {
        if (windowWidth < WINDOW_MIN || windowWidth > WINDOW_MAX) {
            throw new TimeDomainException(TimeDomainText.invalid_peakwin_width_value + ": " + windowWidth);
        }

        this.windowWidth = windowWidth;
    }

    /** Method to set peakSlidingWindow window measurement step */
    public void setMeasurementStep(int measurementStep) throws TimeDomainException {
        if (measurementStep < WINDOW_MIN || measurementStep > WINDOW_MAX) {
            throw new TimeDomainException(TimeDomainText.invalid_peakwin_measurement_step_value + ": " + measurementStep);
        }

        this.measurementStep = measurementStep;
    }

    /** Method to set peakSlidingWindow window width */
    public void setWindowWidth(String str) throws TimeDomainException {

        int width;

        try {
            width = Integer.parseInt(str);
        } catch (NumberFormatException e) {
            throw new TimeDomainException(TimeDomainText.invalid_peakwin_width_value + ": " + str);
        }

        setWindowWidth(width);
    }

    /** Method to set peakSlidingWindow window width */
    public void setThreshold(double threshold) throws TimeDomainException {

        if (threshold < THRESHOLD_MIN || threshold > THRESHOLD_MAX) {
            throw new TimeDomainException(TimeDomainText.invalid_peakwin_threshold_value + ": " + threshold);
        }

        this.threshold = threshold;
    }

    /** Method to set peakSlidingWindow window width */
    public void setThreshold(String str) throws TimeDomainException {

        double threshold;

        try {
            threshold = Double.parseDouble(str);
        } catch (NumberFormatException e) {
            throw new TimeDomainException(TimeDomainText.invalid_peakwin_threshold_value + ": " + str);
        }

        setThreshold(threshold);
    }

    /** Returns true if this process supports memory usage
     *
     * @return    true if this process supports memory usage or does not require memory.
     */
    public boolean supportsMemory() {

        return (true);

    }

    /** Method to check settings */
    public void checkSettings() throws TimeDomainException {

        String errMessage = "";
        int badSettings = 0;

        setWindowWidth(windowWidth);

        if (badSettings > 0) {
            throw new TimeDomainException(errMessage + ".");
        }

    }

    /*** function to apply peakSlidingWindow  */
    public final float[] apply(double dt, float[] sample) {

        int istepIndex = 0;
        float peakValue = 0.0f;

        if (useMemory) { // use stored memory
            if (memory == null) // no stored memory initialized
            {
                memory = new TimeDomainMemory(2, 0.0f, windowWidth, 0.0f);
            }
            istepIndex = Math.round(memory.input[0]);
            peakValue = memory.input[1];
        }

        peakPicks = new ArrayList<>();

        float[] newSample = new float[sample.length];

        int i1, i2;
        int icount;

        int istep = measurementStep;
        if (istep < 1) {
            istep = 1;
        }

        for (int i = 0; i < sample.length; i++) {

            // check if at measurement step
            istepIndex++;
            if (istepIndex % istep != 0) {  // not at measurement step
                newSample[i] = peakValue;
                continue;
            }
            istepIndex = 0;

            i1 = i - windowWidth;
            if (!useMemory && i1 < 0) // truncated
            {
                i1 = 0;
            }
            i2 = i;     // causal boxcar of width windowWidth

            peakValue = -Float.MAX_VALUE;
            icount = 0;
            float value = 0.0f;
            for (int n = i1; n < i2; n++) {
                if (useMemory && n < 0) {
                    value = memory.output[windowWidth + n];
                } else {
                    value = sample[n];
                }
                peakValue = value > peakValue ? value : peakValue;
                icount++;
            }
            if (icount > 0) {
                newSample[i] = peakValue;
                if (peakValue >= threshold) {
                    PickData pickData = new PickData(i1, i2,
                            PickData.POLARITY_UNKNOWN, peakValue, PickData.DATA_AMP_UNITS);
                    peakPicks.add(pickData);
                }

            } else {
                newSample[i] = 0.0f;
            }

        }

        // save memory if used
        if (useMemory) { // using stored memory
            memory.updateOutput(sample);
            memory.input[0] = istepIndex;
            memory.input[1] = peakValue;
        }

        return (newSample);

    }

    /** Method to get pick trigger indeces
     *
     * @return triggerPickData ArrayList of int[2] where int[0] is trigger index
     *  and int[1] is last index where STA/LTA ratio equaled 1.0
     */
    public ArrayList getPickData() {

        return (peakPicks);

    }

    /** Method to get pick trigger name prefix
     *
     * @return triggerPickData prefix if processs creates picks, otherwise
     */
    public String getPickPrefix() {

        return ("PkWin");

    }

    /**  Update fields in TimeSeries object */
    public void updateFields(TimeSeries timeSeries) {
        //timeSeries.ampUnits = PhysicalUnits.NO_UNITS;
    }

    /** Returns true if this process modifies trace amplitude
     *
     * @return    true if this process modifies trace amplitude.
     */
    public boolean amplititudeModified() {

        return (true);

    }
}	// End class GaussianFilter


