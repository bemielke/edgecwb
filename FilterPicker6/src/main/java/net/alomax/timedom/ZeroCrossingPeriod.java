/*
 * This file is part of the Anthony Lomax Java Library.
 *
 * Copyright (C) 2009 Anthony Lomax <anthony@alomax.net www.alomax.net>
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

public class ZeroCrossingPeriod extends TimeDomainProcess {

    public String errorMessage;

    /** constructor */
    public ZeroCrossingPeriod(String localeText) {
        this.errorMessage = " ";

        TimeDomainText.setLocale(localeText);
    }

    /** copy constructor */
    public ZeroCrossingPeriod(ZeroCrossingPeriod intantPeriodWindowd) {

        this.useMemory = intantPeriodWindowd.useMemory;
        if (intantPeriodWindowd.memory != null) {
            this.memory = new TimeDomainMemory(intantPeriodWindowd.memory);
        }

    }

    /** Returns true if this process supports memory usage
     *
     * @return    true if this process supports memory usage or does not require memory.
     */
    public boolean supportsMemory() {

        return (true);

    }

    /** Clears the Memory object for this process
     *
     */
    @Override
    public void clearMemory() {

        this.memory = null;

    }

    /** Method to check settings */
    public void checkSettings() throws TimeDomainException {

        String errMessage = "";
        int badSettings = 0;

        /*if (windowWidth < WIDTH_MIN || windowWidth > WIDTH_MAX) {
        errMessage += ": " + TimeDomainText.invalid_window_width_value;
        badSettings++;
        }*/
        if (badSettings > 0) {
            throw new TimeDomainException(errMessage + ".");
        }

    }

    /*** function to calculate instantaneous period in a fixed window (Tau_c)
     *
     * implements eqs 1-3 in :
     *  Allen, R.M., and H. Kanamori,
     *     The Potential for Earthquake Early Warning in Southern California,
     *     Science, 300 (5620), 786-789, 2003.
     *
     * except use a fixed width window instead of decay function
     */
    public final float[] apply(double dt, float[] sample) {

        float[] newSample = new float[sample.length];

        useMemory = true;   // alway use memory (simplifies recursive code below)

        if (useMemory) { // use stored memory
            if (memory == null) // no stored memory initialized
            {
                memory = new TimeDomainMemory(1, 0.0f, 2, 0.0f);
            }
        }

        float sampleLast = 0.0f;
        int lastZeroCrossingIndex = -1;
        double periodLast = 0.0;
        if (useMemory) { // using stored memory
            lastZeroCrossingIndex = Math.round(memory.output[0]);
            periodLast = memory.output[1];
        }

        for (int i = 0; i < sample.length; i++) {

            if ((sample[i] >= 0.0 && sampleLast < 0.0) || (sample[i] < 0.0 && sampleLast >= 0.0)) {
                periodLast = 2.0 * dt * (i - lastZeroCrossingIndex);
                lastZeroCrossingIndex = i;
            }
            sampleLast = sample[i];
            newSample[i] = (float) periodLast;

        }

        // save memory if used
        if (useMemory) { // using stored memory
            memory.input[0] = sampleLast;
            memory.output[0] = lastZeroCrossingIndex - sample.length;
            memory.output[1] = (float) periodLast;
        }

        return (newSample);

    }

    /**  Update fields in TimeSeries object */
    public void updateFields(TimeSeries timeSeries) {

        timeSeries.ampUnits = timeSeries.timeUnits;

    }

    /** Returns true if this process modifies trace amplitude
     *
     * @return    true if this process modifies trace amplitude.
     */
    public boolean amplititudeModified() {

        return (true);

    }
}	// End class GaussianFilter


