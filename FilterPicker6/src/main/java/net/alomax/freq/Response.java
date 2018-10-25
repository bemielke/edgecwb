/*
 * This file is part of the Anthony Lomax Java Library.
 *
 * Copyright (C) 2001 Anthony Lomax <lomax@geoazur.unice.fr>
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
package net.alomax.freq;

import net.alomax.math.*;
import net.alomax.util.*;
import net.alomax.seis.*;

import java.net.*;

/**
 * abstract base class for frequency domain Response functions
 */
public abstract class Response {

    private static int n_static_init = 0;
    public static int UNKNOWN_TYPE = n_static_init++;
    public static int SCALING = n_static_init++;
    public static int INTEGRAL = n_static_init++;
    public static int DERIVATIVE = n_static_init++;
    public static int DOUBLE_DERIVATIVE = n_static_init++;
    public static int OTHER_CONVERSION = n_static_init++;
    public static int NO_CONVERSION = n_static_init++;
    protected int type = UNKNOWN_TYPE;
    public static String GROUND_DISP_NAME = "grnd_disp";
    //
    // 20131213 AJL
    //public static String DEFAULT_BEFORE_UNITS = PhysicalUnits.METERS;
    public static String DEFAULT_BEFORE_UNITS = PhysicalUnits.getDefaultLengthUnits();  // METERS
    public static String DEFAULT_AFTER_UNITS = PhysicalUnits.COUNTS;
    // unit conversion mappings for SCALING and OTHER_CONVERSION types
    protected String[] beforeUnits = new String[0];
    protected String[] afterUnits = new String[0];
    // name conversion mappings
    public static String ANY_NAME = "_$%&";
    public static String UNKNOWN_NAME = "_$$$";
    protected String beforeName = UNKNOWN_NAME;
    protected String afterName = UNKNOWN_NAME;
    protected String shortName = "?";
    protected String longName = "?";
    protected double dt = 1.0;

    /**
     * empty constructor
     */
    protected Response() {
    }

    /**
     * copy constructor
     */
    protected Response(Response r) {

        this.type = r.type;
        this.beforeUnits = r.beforeUnits;
        this.afterUnits = r.afterUnits;
        this.beforeName = r.beforeName;
        this.afterName = r.afterName;
        this.shortName = r.shortName;
        this.longName = r.longName;
        this.dt = dt;

    }

    /**
     * construct a Response object of appropriate type
     */
    public static Response createResponse(URL documentBase, String responseFileName,
            String responseFileFormat, TimeInstant refTimeInstant, BasicChannel channel, TimeSeries timeSeries) throws ResponseException {

        if (responseFileFormat.equalsIgnoreCase("POLE_ZERO")) {
            if (responseFileName.equalsIgnoreCase("INTERNET_SERVICE_POLE_ZERO")) {
                return (new PoleZeroResponse(refTimeInstant, channel, timeSeries));
            } else {
                return (new PoleZeroResponse(documentBase, responseFileName, refTimeInstant, channel, timeSeries));
            }
        } else if (responseFileFormat.equalsIgnoreCase("GSE")) {
            return (new GSEPoleZeroResponse(documentBase, responseFileName, refTimeInstant, channel, timeSeries));
        }

        throw (new ResponseException(FreqText.cannot_deterime_response_type));

    }

    /**
     * set short name
     */
    public void setShortName(String name) {
        shortName = name;
    }

    /**
     * sets long name
     */
    public void setLongName(String name) {
        longName = name;
    }

    /**
     * returns short name
     */
    public String getShortName() {
        return (new String(shortName));
    }

    /**
     * returns long name
     */
    public String getLongName() {
        return (new String(longName));
    }

    /**
     * returns gain or constant amplication factor
     */
    public abstract double getGain();

    /**
     * Returns the response at a given frequency. following eq. 10.11 in Scherbaum, F. , Of Poles and Zeros Fundamentals of Digital Seismology Series:
     * Modern Approaches in Geophysics, Vol. 15
     *
     * @param frequency frequency in Hz at which to evaluate response.
     * @return the complex response.
     */
    public abstract Cmplx evaluateResponse(double frequency);

    /**
     * gets beforeUnits
     */
    public String[] getBeforeUnits() {

        return (beforeUnits);

    }

    /**
     * gets afterUnits
     */
    public String[] getAfterUnits() {

        return (afterUnits);

    }

    /**
     * sets beforeUnits
     */
    public void setBeforeUnits(String[] beforeUnits) {

        if (beforeUnits == null) {
            return;
        }

        this.beforeUnits = new String[beforeUnits.length];
        for (int i = 0; i < beforeUnits.length; i++) {
            this.beforeUnits[i] = new String(beforeUnits[i]);
        }

    }

    /**
     * sets afterUnits
     */
    public void setAfterUnits(String[] afterUnits) {

        if (afterUnits == null) {
            return;
        }

        this.afterUnits = new String[afterUnits.length];
        for (int i = 0; i < afterUnits.length; i++) {
            this.afterUnits[i] = new String(afterUnits[i]);
        }

    }

    /**
     * sets beforeUnits
     */
    public void setBeforeUnits(String beforeUnits) {

        if (beforeUnits == null) {
            return;
        }

        this.beforeUnits = new String[1];
        this.beforeUnits[0] = new String(beforeUnits);

    }

    /**
     * sets afterUnits
     */
    public void setAfterUnits(String afterUnits) {

        if (afterUnits == null) {
            return;
        }

        this.afterUnits = new String[1];
        this.afterUnits[0] = new String(afterUnits);

    }

    /**
     * sets beforeName
     */
    public void setBeforeName(String beforeName) {

        if (beforeName == null) {
            this.beforeName = UNKNOWN_NAME;
        }

        this.beforeName = beforeName;

    }

    /**
     * sets afterName
     */
    public void setAfterName(String afterName) {

        if (afterName == null) {
            this.afterName = UNKNOWN_NAME;
        }

        this.afterName = afterName;

    }

    /**
     * gets type
     */
    public int getType() {

        return (type);

    }

    /**
     * sets type
     */
    public void setType(int type) {

        this.type = type;

    }

    /**
     * Returns the physical unit forward conversion of this response.
     *
     * @param units units before convolution.
     * @return the units after convolution with this response.
     */
    public String convertUnitsForward(String units) {

        /* AJL 20100927
         if (type == UNKNOWN_TYPE) {
         return (units);
         } else if (type == INTEGRAL) {
         return (PhysicalUnits.timeIntegral(units));
         } else if (type == DERIVATIVE) {
         return (PhysicalUnits.timeDerivative(units));
         } else if (type == SCALING || type == OTHER_CONVERSION) {
         for (int i = 0; i < beforeUnits.length; i++) {
         if (units.equals(beforeUnits[i])) {
         return (afterUnits[i]);
         }
         }
         } else if (type == NO_CONVERSION) {
         return (units);
         }
         */
        if (type == UNKNOWN_TYPE || type == NO_CONVERSION) {
            return (units);
        }
        //System.out.println("          Response: units=" + units);
        for (int i = 0; i < beforeUnits.length; i++) {
            //System.out.println("          Response: beforeUnits[i]=" + beforeUnits[i] + " -> afterUnits[i]=" + afterUnits[i]);
            if (units.equals(beforeUnits[i])) {
                //System.out.println("          Response: FOUND! -> " + afterUnits[i]);
                return (afterUnits[i]);
            }
        }
        if (type == INTEGRAL) {
            return (PhysicalUnits.timeIntegral(units));
        } else if (type == DERIVATIVE) {
            return (PhysicalUnits.timeDerivative(units));
        }

        return (PhysicalUnits.UNKNOWN);

    }

    /**
     * Returns the physical unit backwards conversion of this response.
     *
     * @param units units before convolution.
     * @return the units after convolution with this response.
     */
    public String convertUnitsBackward(String units) {

        /* AJL 20100927
         if (type == UNKNOWN_TYPE) {
         return (units);
         } else if (type == INTEGRAL) {
         return (PhysicalUnits.timeDerivative(units));
         } else if (type == DERIVATIVE) {
         return (PhysicalUnits.timeIntegral(units));
         } else if (type == SCALING || type == OTHER_CONVERSION) {
         for (int i = 0; i < beforeUnits.length && i < afterUnits.length; i++) {
         System.out.println("convertUnitsBackward i=" + i + "afterUnits[i]=" + afterUnits[i] + "beforeUnits[i]=" + beforeUnits[i]);
         if (units.equals(afterUnits[i])) {
         return (beforeUnits[i]);
         }
         }
         } else if (type == NO_CONVERSION) {
         return (units);
         }
         */
        if (type == UNKNOWN_TYPE || type == NO_CONVERSION) {
            return (units);
        }
        //System.out.println("          Response: units=" + units);
        for (int i = 0; i < beforeUnits.length; i++) {
            //System.out.println("          Response: afterUnits[i]=" + afterUnits[i] + " -> beforeUnits[i]=" + beforeUnits[i]);
            if (units.equals(afterUnits[i])) {
                //System.out.println("          Response: FOUND! -> " + beforeUnits[i]);
                return (beforeUnits[i]);
            }
        }
        if (type == INTEGRAL) {
            return (PhysicalUnits.timeDerivative(units));
        } else if (type == DERIVATIVE) {
            return (PhysicalUnits.timeIntegral(units));
        }

        return (PhysicalUnits.UNKNOWN);

    }

    /**
     * Returns the name forward conversion of this response.
     *
     * @param name units before convolution.
     * @return the name after convolution with this response.
     */
    public String convertNameForward(String name) {

        if (type == NO_CONVERSION) {
            return (name);
        }

        if (beforeName.equals(ANY_NAME) || name.equals(beforeName)) {
            return (afterName);
        } else {
            return (name);
        }

    }

    /**
     * Returns the name backwards conversion of this response.
     *
     * @param name units before convolution.
     * @return the name after convolution with this response.
     */
    public String convertNameBackward(String name) {

        if (type == NO_CONVERSION) {
            return (name);
        }

        if (afterName.equals(ANY_NAME) || name.equals(afterName)) {
            return (beforeName);
        } else {
            return (name);
        }

    }

    /**
     * Returns the division of this response by another response.
     *
     * @param pzr2 a response.
     * @return the combined response this/pzr2.
     */
    public abstract Response div(Response pzr2) throws ResponseException;

    /**
     * Returns the frequency response from the pole-zero-gain information.
     *
     * converted to Java from Earthworm function response in source file transfer.c
     *
     * @param nfft the number of points that will be used in the FFT
     * @param deltat the time interval between data points in the time-domain
     *
     * @return an array of Cmplx's containing the frequency response
     */
    public abstract Cmplx[] response(int nfft, double deltat);

    /**
     *
     * converted to Java from Earthworm function convertWave in source file transfer.c
     *
     * !!! following doc from original c source
     *
     * convertWave: converts a waveform (time series) from its original response function to a new response function. This conversion is done in the
     * frequency domain. The frequency response of the transfer function may be tapered. The input data will be padded in the time-domain. The amount
     * of padding is determined automatically unless the user provides her own pad length. Arguments: input: array of data for preocessing npts:
     * number of data points to process deltat: time interval between samples, in seconds origRS: structure defining process that generated the input
     * data that is, the response function to be removed finalRS: structure defining desired response function freq: array of four frequencies (f0,
     * f1, f2, f3) defining the taper to be applied to the frequency response function before it is convolved with the data. Below f0 and above f3,
     * the taper is 0; between f2 and f3 the taper is 1; between f0-f1 and f2-f3 is a cosine taper. retFD: flag to return result in frequency-domain
     * (if retFD == 1) or in time-domain (if retFD == 0) If the output is to stay in the frequency domain, be sure you understand how the results are
     * laid out. See the comments in the FFT package: currently sing.c padlen: The pad length to be applied to data before transforming to frequency
     * domain. If padlen < 0, pad length will be estimated here and the value chosen will be returned in this return-value parameter. nfft: The size
     * of the FFT chosen, based on npts + *padlen If the returned value of nfft + padlen is less than npts, then convertWave had to effectively
     * truncate the raw trace in order to fit the processed trace in the limit of outBufLen. output: array of values output from the conversion This
     * array must be allocated by the caller. outBufLen: size of `output' array. work: a work array that must be allocated by the caller. Its size
     * must be outBufLen+2 workFFT: a work array needed by fft99. Its size must be outBufLen+1
     *
     * Returns: 0 on success -1 on out-of-memory errors -2 on too-small impulse response -3 on invalid arguments -4 on FFT error
     */
    public static final Cmplx[] convertWave(Cmplx[] input, double deltat,
            Response origRS, Response finalRS,
            double freq0, double freq1, double freq2, double freq3)
            throws ResponseException {


        // validate arguments
        if (origRS == null) {
            throw (new ResponseException("convertWave: null original response function"));
        }
        if (finalRS == null) {
            throw (new ResponseException("convertWave: null final response function"));
        }
        if (input == null) {
            throw (new ResponseException("convertWave: null input spectrum"));
        }
        if (input.length < 2) {
            throw (new ResponseException("convertWave: input spectrum length < 2"));
        }
        if (deltat <= 0.0) {
            throw (new ResponseException("convertWave: delta time <= 0.0"));
        }
        if (freq0 > freq1 || freq1 >= freq2 || freq2 > freq3) {
            throw (new ResponseException(FreqText.invalid_frequency_taper_values));
        }


        // combine the response functions into one: finalRS / origRS
        Response rs = finalRS.div(origRS);

        // calculate the frequency response of the combined response function;
        Cmplx[] freqResponse = rs.response(input.length, deltat);


        // Convolve the tapered frequency response with the data. Since we
        // are in the frequency domain, convolution becomes `multiply'.
        // We skip the zero-frequency part; this only affects the mean
        // of the data, which should have been removed long ago.
        Cmplx[] output = new Cmplx[input.length];
        double f, tpr, dre, dim;
        double delfreq = 1.0 / ((double) input.length * deltat);
        output[0] = new Cmplx(0.0, 0.0);		 // remove the mean, if there is any
        int i2;
        for (int i1 = 1; i1 <= output.length / 2; i1++) {
            i2 = output.length - i1;
            f = (double) i1 * delfreq;
            tpr = ftaper(f, freq1, freq0) * ftaper(f, freq2, freq3);
            if (i1 != output.length / 2) {
                if (tpr > 0.0) {
                    output[i1] = Cmplx.mul(input[i1], freqResponse[i1]).mul(tpr);
                    output[i2] = Cmplx.mul(input[i2], freqResponse[i2]).mul(tpr);
                } else {
                    output[i1] = new Cmplx(0.0, 0.0);
                    output[i2] = new Cmplx(0.0, 0.0);
                }
            } else {
                if (tpr > 0.0) {
                    output[i1] = Cmplx.mul(input[i1], freqResponse[i1]).mul(tpr * 0.5);
                } else {
                    output[i1] = new Cmplx(0.0, 0.0);
                }
            }
        }

        return output;
    }

    /**
     *
     * converted to Java from Earthworm function ftaper in source file transfer.c
     *
     * !!! following doc from original c source
     *
     * ftaper: produce a cosine taper between unity (beyond fon) and zero (beyond foff). The cosine taper is between fon and foff. Arguments: freq:
     * the frequency at which the taper value is desired fon: the unity end of the taper foff: the zero end of the taper if fon and foff are equal,
     * then taper returns 1.0, the all-pass filter. returns: the value of the taper
     */
    public static final double ftaper(double freq, double fon, double foff) {

        double t;

        if (fon == foff) {			// all-pass taper
            t = 1.0;
        } else if (fon > foff) // high-pass taper
        {
            if (freq < foff) {
                t = 0.0;
            } else if (freq > fon) {
                t = 1.0;
            } else {
                t = 0.5 * (1.0 - Math.cos(Math.PI * (freq - foff) / (fon - foff)));
            }
        } else // (fon < foff) - low-pass taper
        {
            if (freq < fon) {
                t = 1.0;
            } else if (freq > foff) {
                t = 0.0;
            } else {
                t = 0.5 * (1.0 + Math.cos(Math.PI * (freq - fon) / (foff - fon)));
            }
        }
        //if (t != 0.0 && t != 1.0)
        //System.out.println("freq fon foff t " + freq+" "+fon+" "+foff+" "+t);

        return t;

    }
}	// End class Response

