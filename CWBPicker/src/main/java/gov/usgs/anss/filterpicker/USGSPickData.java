
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
package gov.usgs.anss.filterpicker;

public class USGSPickData {

    public static final String NO_AMP_UNITS = "";
    public static final String DATA_AMP_UNITS = "_DATA";
    public static final String CHAR_FUNCT_AMP_UNITS = "_CF";
    public static final String INDEP_VAR_UNITS = "_INDEP_VAR";
    public static final int POLARITY_POS = 1;
    public static final int POLARITY_UNKNOWN = 0;
    public static final int POLARITY_NEG = -1;
    public int polarity = POLARITY_UNKNOWN;
    public double[] indices = new double[2];
    public double amplitude = 0.0;
    public String amplitudeUnits = NO_AMP_UNITS;
    public double period = 0.0;
    public double leastPeriod;
    public String name = null;

    /** constructor */
    public USGSPickData() {
    }

    /** constructor */
    public USGSPickData(double index0, double index1, int polarity, double amplitude, String amplitudeUnits, double period, double leastPeriod) {

        this.indices[0] = index0;
        this.indices[1] = index1;
        this.polarity = polarity;
        this.amplitude = amplitude;
        this.amplitudeUnits = amplitudeUnits;
        this.period = period;
        this.leastPeriod=leastPeriod;

    }

    /** constructor */
    public USGSPickData(double index0, double index1, int polarity, double amplitude, String amplitudeUnits) {

        this.indices[0] = index0;
        this.indices[1] = index1;
        this.polarity = polarity;
        this.amplitude = amplitude;
        this.amplitudeUnits = amplitudeUnits;

    }
}
