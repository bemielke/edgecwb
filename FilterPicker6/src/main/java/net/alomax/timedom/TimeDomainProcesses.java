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

import net.alomax.math.TimeSeries;
import net.alomax.util.PhysicalUnits;



public class TimeDomainProcesses extends TimeDomainProcess {

    protected static int ndx = -1;
    public static final int UNDEF = ndx++;
    // handled by external procedures
    public static final int INTEGRATE = ndx++;
    public static final int DIFFERENTIATE = ndx++;
    public static final int MULTIPLY = ndx++;
    public static final int ADD = ndx++;
    public static final int SQRT = ndx++;
    public static final int SQUARE = ndx++;
    public static final int ABS = ndx++;
    public static final int LOG = ndx++;
    public static final int LOG10 = ndx++;
    // handled by this class


    public int processID = UNDEF;

    public double[] parameters = new double[0];



    /** constructor */

    public TimeDomainProcesses(int processID) {

        this.processID = processID;

    }


    /** constructor */

    public TimeDomainProcesses(TimeDomainProcesses tdp) {

        this(tdp.processID, tdp.parameters);

    }


    /** constructor */

    public TimeDomainProcesses(int processID, double[] parameters) {

        this.processID = processID;
        double[] paramCopy = new double[parameters.length];
        System.arraycopy(parameters, 0, paramCopy, 0, parameters.length);
        this.parameters = paramCopy;

    }


    /** constructor */

    public TimeDomainProcesses(TimeDomainProcesses tdp, double[] parameters) {

        this(tdp.processID, parameters);

    }


    /**  Method to apply time domain processes
     */

    public float[] apply(double dt, float[] sample) {

        /*
        if (processID == SQUARE)
            return(applySquare(dt, sample));
         **/

        return(sample);

    }



    /**  Method to calculate square of data values
     */

    public float[] applySquare(double dt, float[] sample) {

        float ftemp;
        for (int i = 0; i < sample.length; i++) {
            ftemp = sample[i];
            sample[i] = ftemp * ftemp;
        }

        return(sample);

    }


    /**  Update fields in TimeSeries object */

    public void updateFields(TimeSeries timeSeries) {

        if (processID == SQUARE)
            timeSeries.ampUnits = PhysicalUnits.square(timeSeries.ampUnits);

    }


    /** Method to check settings */

    public void checkSettings() throws TimeDomainException {

        String errMessage = "";
        int badSettings = 0;

        if (processID == UNDEF) {
            errMessage += ": " + TimeDomainText.invalid_time_domain_process;
            badSettings++;
        }

        if (badSettings > 0) {
            throw new TimeDomainException(errMessage + ".");
        }

    }


    /** Returns true if this process modifies trace amplitude
     *
     * @return    true if this process modifies trace amplitude.
     */

    public boolean amplititudeModified() {

        if (processID == INTEGRATE)
            return(true);
        if (processID == DIFFERENTIATE)
            return(true);
        if (processID == MULTIPLY)
            return(true);
        if (processID == ADD)
            return(true);
        if (processID == SQRT)
            return(true);
        if (processID == SQUARE)
            return(true);
        if (processID == ABS)
            return(true);
        if (processID == LOG)
            return(true);
        if (processID == LOG10)
            return(true);

        return(true);
    }




    /** Returns true if this process supports memory usage
     *
     * @return    true if this process supports memory usage or does not require memory.
     */

    public boolean supportsMemory() {

        if (processID == INTEGRATE)
            return(true);
        if (processID == DIFFERENTIATE)
            return(true);
        if (processID == MULTIPLY)
            return(true);
        if (processID == ADD)
            return(true);
        if (processID == SQRT)
            return(true);
        if (processID == SQUARE)
            return(true);
        if (processID == ABS)
            return(true);
        if (processID == LOG)
            return(true);
        if (processID == LOG10)
            return(true);

        return(false);

    }


    /** Returns the Memory object for this process
     *
     * @return    Memory object for this process or null if no Memory object set.
     */

    public TimeDomainMemory getMemory() {

        if (this.useMemory && memory == null) {
            if (processID == INTEGRATE) {
                float[] input = new float[0];
                float[] output = {0.0f};
                memory = new TimeDomainMemory(input, output);
            }
            else if (processID == DIFFERENTIATE) {
                float[] input = new float[0];
                float[] output = {0.0f};
                memory = new TimeDomainMemory(input, output);
            }
        }

        return(memory);

    }






}	// End class TimeProcesses


