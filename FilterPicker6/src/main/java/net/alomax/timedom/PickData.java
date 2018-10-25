/*
 * This file is part of the Anthony Lomax Java Library.
 *
 * Copyright (C) 2006 Anthony Lomax <anthony@alomax.net www.alomax.net>
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

public class PickData {

    public static final String NO_AMP_UNITS = "";
    public static final String DATA_AMP_UNITS = "_DATA";
    public static final String CHAR_FUNCT_AMP_UNITS = "_CF";
    public static final String INDEP_VAR_UNITS = "_INDEP_VAR";
    public static final int POLARITY_POS = 1;
    public static final int POLARITY_UNKNOWN = 0;
    public static final int POLARITY_NEG = -1;
    /**
     * The polarity of pick (POLARITY_POS, POLARITY_NEG, or POLARITY_UNKNOWN).
     */
    public int polarity = POLARITY_UNKNOWN;
    /**
     * An array of indices of the starting and ending points for the pick uncertainty within the data sample array passed to the picker.
     * The pick time may be defined as the mid-point between these two data samples.
     * The starting point may corresponds to the characteristic function rising above the backgrounds level.  The ending point may corresponds to the pick trigger.
     */
    public double[] indices = new double[2];
    /**
     * A value representative of the pick strength (e.g. maximum characteristic function value within pick detection window).
     */
    public double amplitude = 0.0;
    /**
     * Amplitude units for amplitude field (NO_AMP_UNITS, CHAR_FUNCT_AMP_UNITS, etc.).
     */
    public String amplitudeUnits = NO_AMP_UNITS;
    /**
     * A characteristic period for the pick.
     */
    public double period = 0.0;
    /**
     * Pick name, label or id.
     */
    public String name = null;

    /** constructor */
    public PickData() {
    }

    /** constructor */
    public PickData(double index0, double index1, int polarity, double amplitude, String amplitudeUnits, double period) {

        this.indices[0] = index0;
        this.indices[1] = index1;
        this.polarity = polarity;
        this.amplitude = amplitude;
        this.amplitudeUnits = amplitudeUnits;
        this.period = period;

    }

    /** constructor */
    public PickData(double index0, double index1, int polarity, double amplitude, String amplitudeUnits) {

        this.indices[0] = index0;
        this.indices[1] = index1;
        this.polarity = polarity;
        this.amplitude = amplitude;
        this.amplitudeUnits = amplitudeUnits;

    }
}


