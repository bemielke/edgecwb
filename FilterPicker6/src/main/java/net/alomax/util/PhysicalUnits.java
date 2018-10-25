/*
 * This file is part of the Anthony Lomax Java Library.
 *
 * Copyright (C) 2001 Anthony Lomax <anthony@alomax.net www.alomax.net>
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


package net.alomax.util;



/** Class for definitions and conversions of physical units */

public class PhysicalUnits {

    public static final String UNKNOWN = "?";

    public static final String MILLIGRAMS = "mg";
    public static final String GRAMS = "g";
    public static final String KILOGRAMS = "kg";

    public static final String SECONDS = "sec";
    public static final String SECONDS_SHORT = "s";

    protected static final String TIME_INTEGRAL_EXT = "*" + SECONDS_SHORT;
    protected static final String TIME_DERIVATIVE_EXT = "/" + SECONDS_SHORT;
    protected static final String VEL_EXT = TIME_DERIVATIVE_EXT;
    protected static final String ACC_EXT = TIME_DERIVATIVE_EXT + TIME_DERIVATIVE_EXT;

    protected static final String SQUARED = "**2";

    public static final String DEGREES = "deg";

    // nm|micron|mm|cm|dm|m|volts|amps|counts
    public static final String NANOMETERS = "nm";
    public static final String MICROMETERS = "micron";
    public static final String MILLIMETERS = "mm";
    public static final String CENTIMETERS = "cm";
    public static final String DECIMETERS = "dm";
    public static final String METERS = "m";
    public static final String KILOMETERS = "km";

    public static final String VOLTS = "volts";
    public static final String AMPS = "amps";
    public static final String MILLIGALS = "mG";

    public static final String COUNTS = "counts";
    public static final String NO_UNITS = COUNTS;

    //protected static String defaultLengthUnits = NANOMETERS;
    protected static String defaultLengthUnits = METERS;  // 20131213 AJL - test

    /** Radians per degree etc */
    public static final double D2R = Math.PI / 180.0;
    public static final double R2D = 180.0 / Math.PI;
    public static final double RPD = Math.PI / 180.0;
    public static final double DEG2RAD = RPD;
    public static final double RAD2DEG = 1.0 / RPD;



    /** get default length units
     *
     * @return    String representation of default length units.
     *
     */

    public static String getDefaultLengthUnits() {

        return(defaultLengthUnits);

    }


    /** set default length units
     *
     * @param    String representation of default length units.
     *
     */

    public static void setDefaultLengthUnits(String units) {

        defaultLengthUnits = units;

    }


    /** Checks if given units are same physical type as reference unit for same physical type
     *
     * @param givenUnits the given units
     * @param referenceUnits the reference units
     * @return    the scale factor C (given=C*reference) or -1.0 if not same physical type of units
     *    or if relation of types not available.
     *
     */

    public static double scaleFactor(String givenUnits, String referenceUnits) {

        if (givenUnits == null || referenceUnits == null)
            return(-1.0);

        double givenScale = scaleToLengthUnit(givenUnits);
        double referenceScale = scaleToLengthUnit(referenceUnits);
        // compare types
        if (givenScale > 0.0 && referenceScale > 0.0)
            return(givenScale / referenceScale);

        givenScale = scaleToMassUnit(givenUnits);
        referenceScale = scaleToMassUnit(givenUnits);
        // compare types
        if (givenScale > 0.0 && referenceScale > 0.0)
            return(givenScale / referenceScale);

        // unknown
        return(-1.0);

    }



    /** Returns scale factor between given length units and base length unit (meter)
     *
     * @param givenUnits the given units
     * @return    the scale factor between given units and base unit (meter), or -1.0 if not available.
     *
     */

    public static double scaleToLengthUnit(String givenUnits) {

        if (givenUnits == null)
            return(-1.0);

        if (givenUnits.equalsIgnoreCase(NANOMETERS))
            return(1.0e-9);
        else if (givenUnits.equalsIgnoreCase(MICROMETERS))
            return(1.0e-6);
        else if (givenUnits.equalsIgnoreCase(MICROMETERS))
            return(1.0e-6);
        else if (givenUnits.equalsIgnoreCase(MILLIMETERS))
            return(1.0e-3);
        else if (givenUnits.equalsIgnoreCase(CENTIMETERS))
            return(1.0e-2);
        else if (givenUnits.equalsIgnoreCase(DECIMETERS))
            return(1.0e-1);
        else if (givenUnits.equalsIgnoreCase(METERS))
            return(1.0);
        else if (givenUnits.equalsIgnoreCase(KILOMETERS))
            return(1.0e3);

        return(-1.0);

    }


    /** Returns scale factor between given weight units and base mass unit (gram)
     *
     * @param givenUnits the given units
     * @return    the scale factor between given units and base unit (gram), or -1.0 if not available.
     *
     */

    public static double scaleToMassUnit(String givenUnits) {

        if (givenUnits == null)
            return(-1.0);

        if (givenUnits.equalsIgnoreCase(MILLIGRAMS))
            return(1.0e-3);
        else if (givenUnits.equalsIgnoreCase(GRAMS))
            return(1.0);
        else if (givenUnits.equalsIgnoreCase(KILOGRAMS))
            return(1.0e3);

        return(-1.0);

    }



    /** Converts a physical unit to its time integral
     *
     * @param units the original units
     * @return    the time integral form of the units.
     *
     */

    public static String timeIntegral(String units) {

        // if ends in derivative extension, truncate
        if (units.endsWith(TIME_DERIVATIVE_EXT))
            return(units.substring(0, units.lastIndexOf(TIME_DERIVATIVE_EXT)));

        // default, multiply by seconds
        return(units + TIME_INTEGRAL_EXT);

    }



    /** Converts a physical unit to its time derivative
     *
     * @param units the original units
     * @return    the time derivative form of the units.
     *
     */

    public static String timeDerivative(String units) {

        // if ends in integral extension, truncate
        if (units.endsWith(TIME_INTEGRAL_EXT))
            return(units.substring(0, units.lastIndexOf(TIME_INTEGRAL_EXT)));

        // default, divide by seconds
        return(units + TIME_DERIVATIVE_EXT);

    }



    /** Converts a physical unit to its square
     *
     * @param units the original units
     * @return    the squared form of the units.
     *
     */

    public static String square(String units) {

        // default
        return("(" + units + ")" + SQUARED);

    }



    /* main method for testing */

    public static void main(String argv[]) {

        String unit = COUNTS;
        System.out.println("original " + unit);

        unit = PhysicalUnits.METERS;
        System.out.println("METERS " + unit);
        unit = PhysicalUnits.timeIntegral(unit);
        System.out.println("timeIntegral " + unit);
        unit = PhysicalUnits.timeIntegral(unit);
        System.out.println("timeIntegral " + unit);
        unit = PhysicalUnits.timeDerivative(unit);
        System.out.println("timeDerivative " + unit);
        unit = PhysicalUnits.timeDerivative(unit);
        System.out.println("timeDerivative " + unit);
        unit = PhysicalUnits.timeDerivative(unit);
        System.out.println("timeDerivative " + unit);
        unit = PhysicalUnits.timeDerivative(unit);
        System.out.println("timeDerivative " + unit);

        unit = PhysicalUnits.NANOMETERS;
        System.out.println("NANOMETERS " + unit);
        unit = PhysicalUnits.timeDerivative(unit);
        System.out.println("timeDerivative " + unit);
        unit = PhysicalUnits.timeDerivative(unit);
        System.out.println("timeDerivative " + unit);
        unit = PhysicalUnits.timeIntegral(unit);
        System.out.println("timeIntegral " + unit);
        unit = PhysicalUnits.timeIntegral(unit);
        System.out.println("timeIntegral " + unit);

    }



}	// end class PhysicalUnits

