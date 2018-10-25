/*
 * This file is part of the Anthony Lomax Java Library.
 *
 * Copyright (C) 2015 Anthony Lomax <anthony@alomax.net www.alomax.net>
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

import net.alomax.timedom.PickData;

/**
 * For this picker:
 * <p>
 * PickData.indices: The starting point corresponds to the characteristic function rising above the backgrounds level. The ending
 * point corresponds to the pick trigger. The indices are corrected (advanced) by a phase shift corresponding to the bandpass filter used for the
 * shortest period band that triggered.
 * <p>
 * PickData.amplitude: The maximum unclipped characteristic function value at the trigger point.
 * <p>
 * PickData.period:
 * The band period corresponding to the maximum unclipped characteristic function value at the trigger point.
 */
public class FilterPicker6PickData extends PickData {

    /**
     * number of samples after trigger required for the integral of the (clipped) characteristic function to exceeds threshold2 * tUpEvent (e.g.
     * number of samples needed to declare this pick)
     */
    public int nSamplesUpEventUsed;
    /**
     * array over bands of filter band parameters (band periods and thresholdScaleFactor) used for this pick.
     */
    public FilterPicker6_BandParameters[] bandParameters = null;
    /**
     * array over bands of unclipped characteristic function values at trigger sample for this pick.
     */
    public double[] charFunctValueTrigger = null;
    /**
     * array over bands of maximum, unclipped characteristic function values within window from trigger to in window from pick accepted to sample at
     * which pick was declared.
     */
    public double[] charFunctValueMaxInUpEventWindow = null;
    /*** DCK - this was added to allow reuse of FilterPicker6PickData objects by DCK.  It simply mirrors the constructor
     * 
     * @param index0
     * @param index1
     * @param polarity
     * @param amplitude
     * @param amplitudeUnits
     * @param period
     * @param nSamplesUpEventUsed
     * @param bandParameters
     * @param charFunctValueTrigger
     * @param charFunctValueMaxInUpEventWindow 
     */
    public void reload(double index0, double index1, int polarity, double amplitude, String amplitudeUnits, double period,
            int nSamplesUpEventUsed,
            FilterPicker6_BandParameters[] bandParameters, double[] charFunctValueTrigger, double[] charFunctValueMaxInUpEventWindow) {
      // super class items
      this.indices[0] = index0;
      this.indices[1] = index1;
      this.polarity = polarity;
      this.amplitude = amplitude;
      this.amplitudeUnits = amplitudeUnits;
      this.period = period;
      // chile class items
      this.nSamplesUpEventUsed = nSamplesUpEventUsed;
      this.bandParameters = bandParameters.clone();
      this.charFunctValueTrigger = charFunctValueTrigger.clone();
      this.charFunctValueMaxInUpEventWindow = charFunctValueMaxInUpEventWindow.clone();              

    }

    public FilterPicker6PickData(double index0, double index1, int polarity, double amplitude, String amplitudeUnits, double period,
            int nSamplesUpEventUsed,
            FilterPicker6_BandParameters[] bandParameters, double[] charFunctValueTrigger, double[] charFunctValueMaxInUpEventWindow) {

        super(index0, index1, polarity, amplitude, amplitudeUnits, period);

        this.nSamplesUpEventUsed = nSamplesUpEventUsed;
        this.bandParameters = bandParameters.clone();
        this.charFunctValueTrigger = charFunctValueTrigger.clone();
        this.charFunctValueMaxInUpEventWindow = charFunctValueMaxInUpEventWindow.clone();

    }

    public FilterPicker6PickData(double index0, double index1, int polarity, double amplitude, String amplitudeUnits, double period) {
        super(index0, index1, polarity, amplitude, amplitudeUnits, period);
    }

    public FilterPicker6PickData(double index0, double index1, int polarity, double amplitude, String amplitudeUnits) {
        super(index0, index1, polarity, amplitude, amplitudeUnits);
    }

}
