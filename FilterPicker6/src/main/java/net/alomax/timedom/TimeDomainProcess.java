/*
 * This file is part of the Anthony Lomax Java Library.
 *
 * Copyright (C) 2004 Anthony Lomax <anthony@alomax.net www.alomax.net>
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
import net.alomax.timedom.fp6.FilterPicker6_old;

/**
 * TimeDomainProcess
 */
/**
 * An abstract class representing processes that acts on time domain, float arrays.
 *
 * @author Anthony Lomax
 * @version %I%, %G%
 * @see Cmplx
 */
public abstract class TimeDomainProcess {

    public static final double LARGE_DOUBLE = 1.0e99;
    protected TimeDomainMemory memory = null;
    protected boolean useMemory = false;

    /**
     * Does processing in the time domain.
     *
     * @param dt the time-domain sampling interval in seconds.
     * @param sample the array of float values to be processed.
     * @return the processed float values.
     */
    public abstract float[] apply(double dt, float[] sample) throws TimeDomainException;

    /**
     * Update fields in TimeSeries object.
     *
     * @param timeSeries the TimeSeries object.
     */
    public abstract void updateFields(TimeSeries timeSeries);

    /**
     * Checks process parameters
     *
     * @throws FreqException if a setting is invalid.
     */
    public abstract void checkSettings() throws TimeDomainException;

    /**
     * Returns true if this process modifies trace amplitude
     *
     * @return true if this process modifies trace amplitude.
     */
    public abstract boolean amplititudeModified();

    /**
     * Returns true if this process supports memory usage
     *
     * @return true if this process supports memory usage.
     */
    public boolean supportsMemory() {

        return (false);

    }

    /**
     * Returns the Memory object for this process
     *
     * @return Memory object for this process or null if no Memory object set.
     */
    public TimeDomainMemory getMemory() {

        return (memory);

    }

    /**
     * Clears the Memory object for this process
     *
     */
    public void clearMemory() {

        this.memory = null;

    }

    /**
     * Sets if a Memory object for this process should be intialized and used
     *
     * @param useMemory
     * @throws net.alomax.timedom.TimeDomainException
     */
    public void setUseMemory(boolean useMemory) throws TimeDomainException {

        if (useMemory && !supportsMemory()) {
            throw (new TimeDomainException(TimeDomainText.does_not_support_memory + ": " + this));
        }
        this.useMemory = useMemory;

    }

    /**
     * Returns a copy object for this process
     *
     * @param tdp
     * @return Copy of object for this process.
     * @throws net.alomax.timedom.TimeDomainException
     */
    public static TimeDomainProcess copy(TimeDomainProcess tdp) throws TimeDomainException {

        if (tdp instanceof RecursionFilter) {
            return (new RecursionFilter((RecursionFilter) tdp));
        }

        if (tdp instanceof TimeDomainProcesses) {
            return (new TimeDomainProcesses((TimeDomainProcesses) tdp));
        }

        if (tdp instanceof Smoothing) {
            return (new Smoothing((Smoothing) tdp));
        }

        if (tdp instanceof PeakSlidingWindow) {
            return (new PeakSlidingWindow((PeakSlidingWindow) tdp));
        }

        if (tdp instanceof PickAmplitudeAtFeature) {
            return (new PickAmplitudeAtFeature((PickAmplitudeAtFeature) tdp));
        }

        if (tdp instanceof FilterPicker5) {
            return (new FilterPicker5((FilterPicker5) tdp));
        }

        if (tdp instanceof FilterPicker6_old) {
            return (new FilterPicker6_old((FilterPicker6_old) tdp));
        }

        if (tdp instanceof InstantPeriod) {
            return (new InstantPeriod((InstantPeriod) tdp));
        }

        if (tdp instanceof ZeroCrossingPeriod) {
            return (new ZeroCrossingPeriod((ZeroCrossingPeriod) tdp));
        }

        throw (new TimeDomainException(TimeDomainText.does_not_support_copy + ": " + tdp));

    }

    /**
     * Method to get pick trigger indeces
     *
     * @return triggerPickData ArrayList if process creates picks and picks are available, null otherwise
     */
    public ArrayList getPickData() {

        return (null);

    }

    /**
     * Method to clear pick trigger indeces
     *
     */
    public void clearTriggerPickData() {
        ;

    }

    /**
     * Method to get pick trigger name prefix
     *
     * @return triggerPickData prefix if process creates picks, otherwise
     */
    public String getPickPrefix() {

        return ("X");

    }
}	// End

