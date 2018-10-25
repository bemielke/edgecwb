/*
 * This file is part of the Anthony Lomax Java Library.
 *
 * Copyright (C) 1999 Anthony Lomax <lomax@faille.unice.fr>
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
package net.alomax.math;

import net.alomax.freq.FrequencyDomainProcess;
import net.alomax.timedom.TimeDomainException;
import net.alomax.timedom.TimeDomainProcess;
import net.alomax.util.PhysicalUnits;

/** Generic TimeSeries object */
/**
 * An abstract base class representing a generic time series.
 * <P>
 * <B>Note: Extending classes must instantiate a numeric
 *   array to contain the time series samples.</B>
 *
 * @author  Anthony Lomax
 * @version %I%, %G%
 * @see     TimeSeriesFloat
 */
public abstract class TimeSeries {

    /** Invalid data value. */
    public static final double INVALID_AMPLITUDE = -Double.MAX_VALUE;
    /** The sampling interval in seconds. */
    public double sampleInt = 0.0;
    /** The lag time in seconds (currently unused). */
    public double lagTime = 0.0;
    /** The amplitude units. */
    public String ampUnits = PhysicalUnits.UNKNOWN;
    /** The time units (must be "Seconds"). */
    public String timeUnits = PhysicalUnits.UNKNOWN;
    // calculated fields
    /** The minimum amplitude in the time series. */
    public double ampMin;
    /** The maximum amplitude in the time series. */
    public double ampMax;
    //    /** The mean amplitude value of the time series. */
    //    protected double sampleMean = 0.0;
    /** The previous mean amplitude value of the time series. */
    protected double lastSampleMean = INVALID_AMPLITUDE;

    /** Empty Constructor */
    public TimeSeries() {
    }

    /** Copy Constructor */
    public TimeSeries(TimeSeries timeSeries) {

        this(timeSeries.sampleInt, timeSeries.lagTime, timeSeries.ampUnits, timeSeries.timeUnits);
    }

    /** Constructor */
    public TimeSeries(double sampleInt, double lagTime, String ampUnits, String timeUnits) {

        this.sampleInt = sampleInt;
        this.lagTime = lagTime;
        this.ampUnits = new String(ampUnits);
        this.timeUnits = new String(timeUnits);

    }

    /** set ampUnits */
    public void setAmpUnits(String ampUnits) {

        this.ampUnits = ampUnits;

    }

    //    /** Returns the time series sample mean
    //     *
    //     * @return    the number of samples.
    //    */
/*
    public double getSampleMean() {
    return(sampleMean);
    }
     */
    /** Returns the amount of memory required for data array
     *
     * @return    the memory size required for storing the data, measured in bytes.
     */
    public abstract long getDataMemorySize();

    /** Calculates and returns the variance of the time series samples.
     *
     */
    public abstract double calculateVariance();

    /** Calculates and returns the mean of the time series samples.
     */
    public abstract double calculateMean();

    /** Calculates and returns the rms amplitude of the time series samples.
     *
     */
    public abstract double calculateRmsAmplitude();

    /** Calculates and returns the rms amplitude of a subset of the samples in this time series.
     */
    public abstract double calculateRmsAmplitude(int startIndex, int endIndex);

    /** Calculates and returns the minimum and maximum amplitude
     *   in a subset of the time series samples. The subset begins at the specified <CODE>beginIndex</CODE>
     *   and extends to the sample at index <CODE>endIndex - 1</CODE>.
     *
     * @param     beginIndex  the beginning index, inclusive.
     * @param     endIndex  the ending index, exclusive.
     * @param     setOutOfRangeValuesToZero  if true sets
     *              out of range amplitude values in the
     *              time series to 0.0.
     * @return    the amplitude range.
     */
    public abstract RangeDouble calculateAmplitudeRange(int beginIndex, int endIndex, boolean setOutOfRangeValuesToZero);

    /** Calculates and sets the minimum and maximum amplitude
     *   in the complete time series.  Sets ampMin and ampMax.
     *
     * @param     setOutOfRangeValuesToZero  if true sets
     *              out of range amplitude values in the
     *              time series to 0.0.
     * @see	#ampMin
     * @see	#ampMax
     */
    public abstract void calculateAndSetAmplitudeRange(boolean setOutOfRangeValuesToZero);

    // return sample array - must override one of these functions in extending class
    /** Returns the samples as a float array.
     *
     * @return    the samples as a float array or null if not implemented
     *              by an extending class.
     */
    public float[] getSampleAsFloat() {
        return (null);
    }

    /** Returns a new float array that is a subset of the samples in this
     *   time series. The subset begins at the specified <CODE>beginIndex</CODE>
     *   and extends to the sample at index <CODE>endIndex - 1</CODE>.
     *
     * @param     beginIndex  the beginning index, inclusive.
     * @param     endIndex  the ending index, exclusive.
     * @return    the subset of samples as a float array or null if not implemented
     *              by an extending class.
     */
    public float[] getSampleSubsetAsFloat(int beginIndex, int endIndex) {
        return (null);
    }

    /** Sets a float array as the samples in this
     *   time series.
     *
     */
    public void setSampleFloat(float[] newSample) {
        ;
    }

    /** Appends a float array to the samples in this
     *   time series.
     *
     * @param     discardToIndex  samples of the current time series
     *              up to this index (exclusive) will be discarded.
     * @param     appendArray  the array to append.
     * @param     startIndexBeforeDiscard the index in the current time series
     *              (before discardin samples) to begin appending.
     * @return    the number of samples appended.
     */
    public int appendFloat(int discardToIndex, float[] appendArray, int startIndexBeforeDiscard) {
        return (0);
    }

    /** Returns the samples as a double array.
     *
     * @return    the samples as a double array or null if not implemented
     *              by an extending class.
     */
    public double[] getSampleAsDouble() {
        return (null);
    }

    /** Returns a new double array that is a subset of the samples in this
     *   time series. The subset begins at the specified <CODE>beginIndex</CODE>
     *   and extends to the sample at index <CODE>endIndex - 1</CODE>.
     *
     * @param     beginIndex  the beginning index, inclusive.
     * @param     endIndex  the ending index, exclusive.
     * @return    the subset of samples as a double array or null if not implemented
     *              by an extending class.
     */
    public double[] getSampleSubsetAsDouble(int startIndex, int endIndex) {
        return (null);
    }

    /** Appends a double array to the samples in this
     *   time series.
     *
     * @param     discardToIndex  samples of the current time series
     *              up to this index (exclusive) will be discarded.
     * @param     appendArray  the array to append.
     * @param     startIndexBeforeDiscard the index in the current time series
     *              (before discardin samples) to begin appending.
     * @return    the number of samples appended.
     */
    public int appendDouble(int discardToIndex, double[] appendArray,
            int startIndexBeforeDiscard) {
        return (0);
    }

    /** Returns the time series sample length
     *
     * @return    the number of samples.
     */
    public abstract int sampleLength();

    /** Returns a time series sample value.
     *
     * @param     index  the sample index.
     * @return    the sample value.
     */
    public abstract double sampleAt(int index);

    /** Inverts the polarity of the time series samples.  Multiplies
     *   each sample by -1.
     *
     */
    public abstract void invertPolarity();

    /** A least squares curve fit of a straight line to the data is calculated.
     *  This straight line or trend is then "subtracted" from the data.
     *
     */
    public abstract void removeTrend();

    //    /** Calculates and sets the mean of the time series samples.
    //     *   Sets sampleMean.
    //     *
    //     * @see	sampleMean
    //     */
    //    public abstract void calculateAndSetMean();
    /** Removes the mean of the time series samples.
     *   updates ampMin, ampMax.
     *
     */
    public abstract void removeMean();

    /** Removes the mean of the time series samples.
     *   updates ampMin, ampMax.
     *
     * @param     nPointsFromBegin  the number of points from beginning of time series to use to calculate mean.
     *
     */
    public abstract void removeMean(int nPointsFromBegin);

    /** Removes the mean of the time series samples.
     *   updates ampMin, ampMax.
     *
     * @param     nStart  the start index of window to use to calculate mean.
     * @param     nEnd  the end index of window to use to calculate mean.
     *
     */
    public abstract void removeMean(int nStart, int nEnd);

    /** Undo the last remove mean.
     *   ampMin, ampMax.
     *
     * @see	removeMean
     */
    public abstract void unDoRemoveMean();

    /** Applies a cosine taper to each end of the time series samples.
     *
     * @param    taperPercent the amount of taper as percent of time series length
     *
     */
    public abstract void cosineTaper(double taperPercent);

    /** Cuts time range of time series from time1 to time2.
     *   Updates ampMin, ampMax, ampUnits.
     *
     */
    public abstract void cutInMemory(int startIndex, int endIndex);

    /** Applies test time domain processing to a set of seismograms
     *
     */
    public abstract void test();

    /** Applies time-domain integration to the time series samples.
     *   Uses trapeziod rule, replaces sample values with the integral.
     *   Updates ampMin, ampMax, ampUnits.
     *
     */
    public abstract void integrate(float startValue);

    /** Applies time-domain integration of maximum of positive and negatige samples to the time series samples.
     *
     *  Integrates abs val of positive and negative values separately, loads case with maximum integral into samples
     *
     * @param peakOnly if true, then only returns integral of largest abs value of positive or negative peak
     *
     * @return     the time window of integration for the maximum positive or negative value
     *
     */
    public abstract double integrateMaxOfPosNeg(boolean peakOnly);

    /** Applies time-domain differentiation to the time series samples.
     *   INVALID_AMPLITUDEfirst differences, replaces sample values with the dirivative.
     *   Updates ampMin, ampMax, ampUnits.
     *
     * @param vlast
     */
    public abstract void differentiate(float vlast);

    /** Multiply each of the time series samples by a constant value.
     *
     * @param     value the multiplier value
     */
    public abstract void multiply(double value);

    /** Take the natural logarithm of each of the time series samples.
     *
     */
    public abstract void log();

    /** Take the base 10 logarithm of each of the time series samples.
     *
     */
    public abstract void log10();

    /** Take square root of each of the time series samples.
     *
     */
    public abstract void sqrt();

    /** Normalize each of the time series samples relative to its maximum abs.
     *
     */
    public abstract void norm();

    /** Method to chop each of the time series samples (set equal to zero if negative)
     *
     */
    public abstract void chop();

    /** Multiply each of the time series samples by a constant value which varies linearly.
     *
     * @param     value1 the multiplier value at the first sample
     * @param     value2 the multiplier value at the last sample
     */
    public abstract void multiplyLinear(double value1, double value2);

    /** Returns the index of the maximum zero-to-peak amplitude in the seismogram.
     *
     * @return    the index of the maximum amplitude or -1 if not available
     */
    public int indexOfAmpZeroToPeakMax() {
        return (-1);
    }

    /** Returns the indices of the maximum peak-to-peak amplitude within a time window.
     *
     * @return    the indices of the maximum amplitude or null if not available
     */
    public RangeInteger indicesOfAmpPeakToPeakMax(double timeWindowWidth) {
        return (null);
    }

    /** Returns the index of the first occurence of the target cumulative value as a proportion of the maxium zero to peak value on the TimeSeries.
     *
     * @param value target cumulative value as a proportion of the value at the end of the TimeSeries
     * @return    the index of the first occurence of the value or -1 if not available
     */
    public int indexOfCumulative(double value) {
        return (-1);
    }

    /** Returns
     *  (1) the index of the first value on the TimeSeries where a line from the pivot point is further from the value
     *  than distanceLimit proportion of its length, and
     *  (2) the index of the first value before (1) where the line from the pivot point is further from the value
     *  than distanceLimit - errorWidth proportion of its length
     *
     *
     * @see	TimeSeries
     * @param pivotIndex index of the pivot point
     * @param linePivotDelay time delay from pivot point to start checking for distanceLimit
     * @param distanceLimit max distance of pivot line from values as a proportion of the height of the pivot line
     * @param errorWidth distance of pivot line from values for error determination as a proportion of the height of the pivot line
     * @return    the index of the first occurence of the value or -1 if not available
     */
    public int[] indexOfPivotLimit(int pivotIndex, double linePivotDelay, double distanceLimit, double errorWidth) {
        return (null);
    }

    /** Performs FFT on time series and returns Complex spectrum.
     *
     * @param     taperPercent the amount of taper as percent of time series length
     * @param     trailingZeroPad the number of points of trailing zero padding to apply
     */
    public abstract Cmplx[] getFFT(double taperPercent, int trailingZeroPad);

    /** Applies a frequency domain process
     *   to the time series samples.
     *
     * @param     freqProcess  the frequency domain process to apply.
     * @param     taperPercent the amount of taper as percent of time series length
     * @param     params process dependent parameters
     * @param     doFftInverse if true, do inverse fft, if false do not do inverse fft - implies next operation is freq process that can start from complex fft array
     * @see	FrequencyDomainProcess
     */
    public abstract void applyFreqProcess(FrequencyDomainProcess freqProcess, double taperPrecent, Object[] params, boolean doFftInverse);

    /** Applies a time domain process
     *   to the time series samples.
     *
     * @param     timeProcess  the time domain process to apply.
     * @see	TimeDomainProcess
     */
    public abstract void applyTimeDomainProcess(TimeDomainProcess timeProcess) throws TimeDomainException;

    /** Returns a string representation of the object.
     *
     * @return a string representation of the object
     */
    public String toString() {

        return (new String(
                "sampleInt = " + sampleInt + "\n"
                + "lagTime = " + lagTime + "\n"
                + "ampUnits = " + ampUnits + "\n"
                + "timeUnits = " + timeUnits + "\n"
                + "ampMin = " + ampMin + "\n"
                + "ampMax = " + ampMax + "\n" //+
                //"sampleMean = " + sampleMean + "\n"
                ));

    }
}
