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

import java.util.*;

public class PickAmplitudeAtFeature extends TimeDomainProcess {

    public String featureID = "";
    public String featureRef = "";
    public double featureOffset = 0.0;
    public FeatureFinder featureFinder = null;
    public String errorMessage = " ";
    protected ArrayList<PickData> ampPicks;
    public static final double OFFSET_MIN = -Double.MAX_VALUE;
    public static final double OFFSET_MAX = Double.MAX_VALUE;

    /** constructor */
    public PickAmplitudeAtFeature(String localeText, String featureID, String featureRef, double featureOffset, FeatureFinder featureFinder) {

        this.ampPicks = new ArrayList<>();

        this.featureID = featureID;
        this.featureRef = featureRef;
        this.featureOffset = featureOffset;
        this.featureFinder = featureFinder;

        this.useMemory = false;

        TimeDomainText.setLocale(localeText);
    }

    /** copy constructor */
    public PickAmplitudeAtFeature(PickAmplitudeAtFeature pickAmplitudeAtFeature) {

        this.ampPicks = new ArrayList<>();

        this.featureID = pickAmplitudeAtFeature.featureID;
        this.featureRef = pickAmplitudeAtFeature.featureRef;
        this.featureOffset = pickAmplitudeAtFeature.featureOffset;
        this.featureFinder = pickAmplitudeAtFeature.featureFinder;

        this.useMemory = pickAmplitudeAtFeature.useMemory;
        if (pickAmplitudeAtFeature.memory != null) {
            this.memory = new TimeDomainMemory(pickAmplitudeAtFeature.memory);
        }

    }

    /** Method to set pickAmplitudeAtFeature reference name */
    public void setFeatureFinder(FeatureFinder featureFinder) throws TimeDomainException {

        this.featureFinder = featureFinder;
    }

    /** Method to set pickAmplitudeAtFeature ID */
    public void setFeatureID(String featureID) throws TimeDomainException {

        this.featureID = featureID;
    }

    /** Method to set pickAmplitudeAtFeature reference name */
    public void setFeatureRef(String featureRef) throws TimeDomainException {

        this.featureRef = featureRef;
    }

    /** Method to set pickAmplitudeAtFeature window width */
    public void setFeatureOffset(double featureOffset) throws TimeDomainException {

        if (featureOffset < OFFSET_MIN || featureOffset > OFFSET_MAX) {
            throw new TimeDomainException(TimeDomainText.invalid_amplitude_at_feature_offset_value + ": " + featureOffset);
        }

        this.featureOffset = featureOffset;
    }

    /** Method to set pickAmplitudeAtFeature window width */
    public void setFeatureOffset(String str) throws TimeDomainException {

        double featureOffset;

        try {
            featureOffset = Double.parseDouble(str);
        } catch (NumberFormatException e) {
            throw new TimeDomainException(TimeDomainText.invalid_amplitude_at_feature_offset_value + ": " + str);
        }

        setFeatureOffset(featureOffset);
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

        setFeatureOffset(featureOffset);

        if (badSettings > 0) {
            throw new TimeDomainException(errMessage + ".");
        }

    }

    /*** function to apply pickAmplitudeAtFeature  */
    public final float[] apply(double dt, float[] sample) {

        /*
        if (useMemory) { // use stored memory
        if (memory != null) // stored memory initialized
        {
        int istepIndex = Math.round(memory.input[0]);
        }
        }
         */

        ampPicks = new ArrayList<>();

        //System.out.println("");
        Feature[] features = featureFinder.getOffsetsToFeature(featureRef, featureOffset);
        //System.out.println("sample.length: " + sample.length);

        for (int i = 0; i < features.length; i++) {
            //System.out.println("featureIndices[" + i + "]: " + features[i].name + " " + features[i].index);

            int index = features[i].index;
            if (index >= 0 && index < sample.length) {
                PickData pickData = new PickData(index, index,
                        PickData.POLARITY_UNKNOWN, sample[index], PickData.DATA_AMP_UNITS);
                pickData.name = features[i].name;
                ampPicks.add(pickData);
                //System.out.println("pickData: " + pickData.name);
            }

        }

        /*
        // save memory if used
        if (useMemory) { // using stored memory
        memory = new TimeDomainMemory(2, 0.0f, 0, 0.0f);
        }
         */

        return (sample);

    }

    /** Method to get pick date
     *
     * @return PickData
     */
    public ArrayList<PickData> getPickData() {

        return (ampPicks);

    }

    /** Method to get pick trigger name prefix
     *
     * @return triggerPickData prefix if process creates picks, otherwise
     */
    public String getPickPrefix() {

        return (featureID);

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

        return (false);

    }
}	// End class GaussianFilter


