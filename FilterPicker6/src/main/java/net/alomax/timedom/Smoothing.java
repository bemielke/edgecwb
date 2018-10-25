/*
 * This file is part of the Anthony Lomax Java Library.
 *
 * Copyright (C) 20054 Anthony Lomax <anthony@alomax.net www.alomax.net>
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

public class Smoothing extends TimeDomainProcess {

    public static final int TYPE_BOXCAR = 0;
    public static final int TYPE_TRIANGLE = 1;
    public static final int TYPE_CAUSAL_BOXCAR = 2;
    public static final int TYPE_CAUSAL_TRIANGLE = 3;
    public static final int TYPE_COSINE = 4;
    public static final int TYPE_MEDIAN = 5;
    private static final int NUM_TYPES = 6;
    public int type = TYPE_BOXCAR;
    // types - !!! MUST MATCH TYPES AND ORDER ABOVE
    public static final String NAME_BOXCAR = "BOXCAR";
    public static final String NAME_TRIANGLE = "TRIANGLE";
    public static final String NAME_CAUSAL_BOXCAR = "CAUSAL_BOXCAR";
    public static final String NAME_CAUSAL_TRIANGLE = "CAUSAL_TRIANGLE";
    public static final String NAME_COSINE = "COSINE";
    public static final String NAME_MEDIAN = "MEDIAN";
    public int windowHalfWidth = 50;
    public String errorMessage = " ";
    private static final int WINDOW_MIN = 1;
    private static final int WINDOW_MAX = Integer.MAX_VALUE;

    /** constructor */
    public Smoothing(String localeText, int type, int windowHalfWidth) {
        this.type = type;
        this.windowHalfWidth = windowHalfWidth;

        TimeDomainText.setLocale(localeText);
    }

    /** copy constructor */
    public Smoothing(Smoothing smoothing) {

        this.type = smoothing.type;
        this.windowHalfWidth = smoothing.windowHalfWidth;

        this.useMemory = smoothing.useMemory;
        if (smoothing.memory != null) {
            this.memory = new TimeDomainMemory(smoothing.memory);
        }

    }

    /** Method to set smoothing window width */
    public void setWindowHalfWidth(int windowHalfWidth) throws TimeDomainException {
        if (windowHalfWidth < WINDOW_MIN || windowHalfWidth > WINDOW_MAX) {
            throw new TimeDomainException(TimeDomainText.invalid_smoothing_half_width_value + ": " + windowHalfWidth);
        }

        this.windowHalfWidth = windowHalfWidth;
    }

    /** Method to set smoothing window width */
    public void setWindowHalfWidth(String str) throws TimeDomainException {

        int halfWidth;

        try {
            halfWidth = Integer.parseInt(str);
        } catch (NumberFormatException e) {
            throw new TimeDomainException(TimeDomainText.invalid_smoothing_half_width_value + ": " + str);
        }

        setWindowHalfWidth(halfWidth);
    }

    /** Method to set smoothing type */
    public void setType(String typeStr) throws TimeDomainException {

        if (NAME_BOXCAR.startsWith(typeStr.toUpperCase())) {
            type = TYPE_BOXCAR;
        } else if (NAME_TRIANGLE.startsWith(typeStr.toUpperCase())) {
            type = TYPE_TRIANGLE;
        } else if (NAME_CAUSAL_BOXCAR.startsWith(typeStr.toUpperCase())) {
            type = TYPE_CAUSAL_BOXCAR;
        } else if (NAME_CAUSAL_TRIANGLE.startsWith(typeStr.toUpperCase())) {
            type = TYPE_CAUSAL_TRIANGLE;
        } else if (NAME_COSINE.startsWith(typeStr.toUpperCase())) {
            type = TYPE_COSINE;
        } else if (NAME_MEDIAN.startsWith(typeStr.toUpperCase())) {
            type = TYPE_MEDIAN;
        } else {
            throw new TimeDomainException(TimeDomainText.invalid_smoothing_type + ": " + typeStr);
        }


    }

    /** Method to set smoothing type */
    public void setType(int type) throws TimeDomainException {

        if (type >= 0 && type < NUM_TYPES) {
            this.type = type;
        } else {
            throw new TimeDomainException(TimeDomainText.invalid_smoothing_type + ": " + type);
        }


    }

    /** Method to get smoothing type */
    public int getType() {

        return (type);

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

        setWindowHalfWidth(windowHalfWidth);
        setType(type);

        if (badSettings > 0) {
            throw new TimeDomainException(errMessage + ".");
        }

    }

    /*** function to apply smoothing  */
    public final float[] apply(double dt, float[] sample) {

        float[] newSample = sample;

        // useMemory = true forces causal filter
        if (type == TYPE_CAUSAL_BOXCAR || type == TYPE_CAUSAL_TRIANGLE) {
            useMemory = true;
        }

        if (useMemory) { // use stored memory
            if (memory == null) // no stored memory initialized
            {
                memory = new TimeDomainMemory(2 * windowHalfWidth, 0.0f, 0, 0.0f);
            }
        }

        if (type == TYPE_BOXCAR || type == TYPE_CAUSAL_BOXCAR) {
            newSample = applyBoxcar(dt, sample);
        }

        if (type == TYPE_TRIANGLE || type == TYPE_CAUSAL_TRIANGLE) {
            newSample = applyTriangle(dt, sample);
        }

        // save memory if used
        if (useMemory) { // using stored memory
            memory.updateInput(sample);
        }

        return (newSample);

    }

    /** function to apply smoothing
     *
     */
    // WARNING: inefficient version with double loop
    public final float[] applyBoxcar_OLD(double dt, float[] sample) {


        float[] newSample = new float[sample.length];

        int i1, i2;
        double sum;
        int icount;

        for (int i = 0; i < sample.length; i++) {

            if (!useMemory) {
                i1 = i - windowHalfWidth;
                if (i1 < 0) // truncated
                {
                    i1 = 0;
                }
                i2 = i + windowHalfWidth;     // a-causal boxcar of width 2 * windowHalfWidth
                if (i2 > sample.length) {
                    i2 = sample.length;
                }
            } else {
                i1 = i - 2 * windowHalfWidth;
                i2 = i;     // causal boxcar of width 2 * windowHalfWidth
            }
            //System.out.println("DEBUG: filter windowHalfWidth: " + windowHalfWidth + "  i1,12: " + i1 + "," + i2);

            sum = 0.0;
            icount = 0;
            float value = 0.0f;
            // 20110919 AJL  for (int n = i1; n < i2; n++) {
            for (int n = i1; n < i2 + 1; n++) {   // 20110919 AJL - bug fix, should inlcude sample i2 in initial sum
                if (useMemory && n < 0) {
                    value = memory.input[2 * windowHalfWidth + n];
                } else {
                    value = sample[n];
                }
                sum += value;
                icount++;
            }
            if (icount > 0) {
                newSample[i] = (float) (sum / (double) icount);
            } else {
                newSample[i] = 0.0f;
            }

        }

        return (newSample);

    }

    /** function to apply smoothing
     *
     */
    // NOTE: efficient version with single loop (20110920 AJL - added)
    public final float[] applyBoxcar(double dt, float[] sample) {

        float[] newSample = new float[sample.length];

        int i1, i2, i;

        i = 0;
        if (!useMemory) {
            i1 = i - windowHalfWidth;
            i2 = i + windowHalfWidth; // a-causal boxcar of width 2 * windowHalfWidth
        } else {
            i1 = i - 2 * windowHalfWidth;
            i2 = i; // causal boxcar of width 2 * windowHalfWidth
        }

        double sum = 0.0;
        int icount = 0;

        int num_samples = sample.length;

        for (i = 0; i < num_samples; i++) {

            if (!useMemory) {
                if (i1 < 0) // truncated
                {
                    i1 = 0;
                }
                if (i2 > num_samples) {
                    i2 = num_samples;
                }
            }

            float value = 0.0f;
            if (icount == 0) { // first pass, accumulate sum
                int n;
                // 20110919 AJL  for (n = i1; n < i2; n++) {
                for (n = i1; n < i2 + 1; n++) {   // 20110919 AJL - bug fix, should inlcude sample i2 in initial sum
                    if (useMemory && n < 0) {
                        value = memory.input[2 * windowHalfWidth + n];
                    } else {
                        value = sample[n];
                    }
                    sum += value;
                    icount++;
                }
            } else { // later passes, update sum
                if (useMemory && (i1 - 1) < 0) {
                    value = memory.input[2 * windowHalfWidth + (i1 - 1)];
                } else {
                    value = sample[(i1 - 1)];
                }
                sum -= value;
                if (useMemory && i2 < 0) {
                    value = memory.input[2 * windowHalfWidth + i2];
                } else {
                    value = sample[i2];
                }
                sum += value;
            }
            if (icount > 0) {
                newSample[i] = (float) (sum / (double) icount);
            } else {
                newSample[i] = 0.0f;
            }

            i1++;
            i2++;

        }
        return (newSample);

    }

    /*** function to apply smoothing  */
    public final float[] applyTriangle(double dt, float[] sample) {

        float[] newSample = new float[sample.length];

        int i1, i2, iwt;
        double sum;
        double weight;

        double[] wt = new double[2 * windowHalfWidth + 1];
        for (int n = 0; n < windowHalfWidth; n++) {
            wt[n] = wt[2 * windowHalfWidth - n] = 1.0 - ((double) (windowHalfWidth - n) / (double) windowHalfWidth);
        }
        wt[windowHalfWidth] = 1.0;


        for (int i = 0; i < sample.length; i++) {

            if (!useMemory) {
                i1 = i - windowHalfWidth;
                if (i1 < 0) // truncated
                {
                    i1 = 0;
                }
                i2 = i + windowHalfWidth;     // a-causal triangle of width 2 * windowHalfWidth
                if (i2 > sample.length) {
                    i2 = sample.length;
                }
                iwt = i1 - i + windowHalfWidth;     // 0 if not truncated
            } else {
                i1 = i - 2 * windowHalfWidth;
                i2 = i;     // causal triangle of width 2 * windowHalfWidth
                iwt = i1 - i + 2 * windowHalfWidth;     // 0 if not truncated
            }

            sum = 0.0;
            weight = 0.0;
            float value = 0.0f;
            for (int n = i1; n < i2; n++) {
                if (useMemory && n < 0) {
                    value = memory.input[2 * windowHalfWidth + n];
                } else {
                    value = sample[n];
                }
                sum += value * wt[iwt];
                weight += wt[iwt];
                iwt++;
            }
            if (weight > 0.0) {
                newSample[i] = (float) (sum / weight);
            } else {
                newSample[i] = 0.0f;
            }

        }

        return (newSample);

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

