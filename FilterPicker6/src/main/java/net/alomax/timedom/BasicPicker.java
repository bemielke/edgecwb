/*
 * This file is part of the Anthony Lomax Java Library.
 *
 * Copyright (C) 2005 Anthony Lomax <anthony@alomax.net www.alomax.net>
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
import net.alomax.util.PhysicalUnits;

import java.util.ArrayList;

public abstract class BasicPicker extends TimeDomainProcess {

    public static final String DIR_FORWARD = "FORWARD";
    public static final String DIR_BACKWARD = "BACKWARD";
    public int direction = 1;
    public static final String RESULT_PICKS = "PICKS";
    public static final String RESULT_CHAR_FUNC = "CHAR_FUNC";
    public static final String RESULT_TRIGGER = "TRIGGERS";
    public static final String RESULT_BANDS = "BANDS";
    public static final String RESULT_ERROR = "ERROR";
    public static final int PICKS = 0;
    public static final int CHAR_FUNC = 1;
    public static final int TRIGGER = 2;
    public static final int BANDS = 3;
    public int resultType = PICKS;
    public String errorMessage = " ";
    // picks
    protected ArrayList<PickData> triggerPickData = new ArrayList<PickData>();

    /**
     * constructor
     */
    public BasicPicker(String localeText, int direction) {

        this.direction = direction;

        TimeDomainText.setLocale(localeText);

    }

    /**
     * constructor
     */
    public BasicPicker(String localeText) {

        TimeDomainText.setLocale(localeText);

    }

    /**
     * constructor
     */
    public BasicPicker(int direction) {

        this.direction = direction;

    }

    /**
     * constructor
     */
    public BasicPicker() {

    }

    /**
     * Method to set direction
     */
    public void setDirection(int direction) throws TimeDomainException {

        if (direction != 1 && direction != -1) {
            throw new TimeDomainException(
                    TimeDomainText.invalid_direction_value + ": " + direction);
        }

        this.direction = direction;
    }

    /**
     * Method to set direction
     */
    public void setDirection(String str) throws TimeDomainException {

        if (DIR_FORWARD.startsWith(str.toUpperCase())) {
            direction = 1;
        } else if (DIR_BACKWARD.startsWith(str.toUpperCase())) {
            direction = -1;
        } else {
            throw new TimeDomainException(TimeDomainText.invalid_direction_value + ": " + str);
        }

    }

    /**
     * Method to get direction as String
     */
    public String getDirectionString() {

        if (direction == 1) {
            return (this.DIR_FORWARD);
        } else {
            return (this.DIR_BACKWARD);
        }

    }

    /**
     * Method to get direction as String
     */
    public int getDirection() {

        return (direction);

    }

    /**
     * Method to set results mode
     */
    public void setResultsType(String str) throws TimeDomainException {

        if (RESULT_PICKS.startsWith(str.toUpperCase())) {
            resultType = PICKS;
        } else if (RESULT_CHAR_FUNC.startsWith(str.toUpperCase())) {
            resultType = CHAR_FUNC;
        } else if (RESULT_TRIGGER.startsWith(str.toUpperCase())) {
            resultType = TRIGGER;
        } else if (RESULT_BANDS.startsWith(str.toUpperCase())) {
            resultType = BANDS;
        } else {
            throw new TimeDomainException(TimeDomainText.invalid_result_value + ": " + str);
        }

    }

    /**
     * Method to get results mode as String
     */
    public String getResultsTypeString() {

        if (resultType == PICKS) {
            return (RESULT_PICKS);
        } else if (resultType == CHAR_FUNC) {
            return (RESULT_CHAR_FUNC);
        } else if (resultType == TRIGGER) {
            return (RESULT_TRIGGER);
        } else if (resultType == BANDS) {
            return (RESULT_BANDS);
        } else {
            return (RESULT_ERROR);
        }

    }

    /**
     * Method to get results mode
     */
    public int getResultsType() {

        return (resultType);

    }

    /**
     * Method to get pick trigger indeces
     *
     * @return triggerPickData ArrayList of int[2] where int[0] is trigger index and int[1] is last index where STA/LTA ratio equaled 1.0
     */
    @Override
    public ArrayList<PickData> getPickData() {

        return (triggerPickData);

    }

    /**
     * Method to clear pick trigger indeces
     *
     */
    public void clearTriggerPickData() {

        triggerPickData.clear();

    }

    /**
     * Method to get pick trigger name prefix
     *
     * @return triggerPickData prefix if process creates picks, otherwise
     */
    public String getPickPrefix() {

        return ("T");

    }

    /**
     * Method to check settings
     */
    public void checkSettings() throws TimeDomainException {

        String errMessage = "";
        int badSettings = 0;

        if (direction != 1 && direction != -1) {
            errMessage += ": " + TimeDomainText.invalid_direction_value;
            badSettings++;
        }

        if (badSettings > 0) {
            throw new TimeDomainException(errMessage + ".");
        }

        // initialoze persistant fields
        clearTriggerPickData();

    }

    /**
     * Applies picker algorithm to specified data sample
     *
     * @param dt the time-domain sampling interval in seconds.
     * @param sample the array of float values to be processed.
     * @return modified sample array if resultType == TRIGGER || resultType == CHAR_FUNC
     * @throws net.alomax.timedom.TimeDomainException
     */
    @Override
    public abstract float[] apply(double dt, float[] sample) throws TimeDomainException;

    /**
     * Update fields in TimeSeries object
     *
     * @param timeSeries the TimeSeries object.
     */
    @Override
    public void updateFields(TimeSeries timeSeries) {

        timeSeries.ampUnits = PhysicalUnits.NO_UNITS;

    }

    /**
     * Returns true if this process modifies trace amplitude
     *
     * @return true if this process modifies trace amplitude.
     */
    @Override
    public boolean amplititudeModified() {

        if (resultType == PICKS) {
            return (false);
        }

        return (true);

    }
}	// End class GaussianFilter

