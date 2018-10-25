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
import net.alomax.io.*;
import net.alomax.util.*;
import net.alomax.seis.*;


import sdsu.io.ASCIIInputStream;

import java.io.*;
import java.net.*;
import java.util.*;

/** base class for frequency domain PoleZeroResponse functions */
public class PoleZeroResponse extends Response {

    protected static final boolean DEBUG = false;
    private static int n_static_init = 0;
    public static int LAPLACE = n_static_init++;
    public static int Z = n_static_init++;
    protected int transformType = LAPLACE;
    public double gain = 1.0;
    public Cmplx poles[] = new Cmplx[0];
    public Cmplx zeros[] = new Cmplx[0];

    /** empty constructor */
    public PoleZeroResponse() {
    }

    /** copy constructor */
    public PoleZeroResponse(PoleZeroResponse pzr) {

        super(pzr);

        transformType = pzr.transformType;
        gain = pzr.gain;
        poles = new Cmplx[pzr.poles.length];
        System.arraycopy(pzr.poles, 0, poles, 0, pzr.poles.length);
        zeros = new Cmplx[pzr.zeros.length];
        System.arraycopy(pzr.zeros, 0, zeros, 0, pzr.zeros.length);

    }

    /** constructor from data */
    public PoleZeroResponse(double gain, Cmplx[] poles, Cmplx[] zeros, String shortName, String longName, int type) //throws ResponseException
    {

        this.gain = gain;
        this.type = type;

        if (poles != null) {
            this.poles = new Cmplx[poles.length];
            for (int i = 0; i < poles.length; i++) {
                this.poles[i] = new Cmplx(poles[i]);
            }
        }
        if (zeros != null) {
            this.zeros = new Cmplx[zeros.length];
            for (int i = 0; i < zeros.length; i++) {
                this.zeros[i] = new Cmplx(zeros[i]);
            }
        }
        if (shortName != null) {
            this.shortName = shortName;
        }
        if (longName != null) {
            this.longName = longName;
        }

        if (DEBUG) {
            System.out.println(this);
        }
        //System.out.println(this);
    }

    /** constructor from data */
    public PoleZeroResponse(double gain, Cmplx[] poles, Cmplx[] zeros,
            String shortName, String longName,
            int type, String[] beforeUnits, String[] afterUnits,
            String beforeInst, String afterInst) //throws ResponseException
    {

        this(gain, poles, zeros, shortName, longName, type);
        setBeforeUnits(beforeUnits);
        setAfterUnits(afterUnits);
        setBeforeName(beforeInst);
        setAfterName(afterInst);

        if (DEBUG) {
            System.out.println(this);
        }

    }

    /** constructor from file */
    public PoleZeroResponse(URL documentBase, String URLName, TimeInstant refTimeInstant, BasicChannel channel,
            TimeSeries timeSeries) throws ResponseException {

        try {

            // open input stream
            InputStream is = null;
            is = GeneralInputStream.openStream(documentBase, URLName, true);

            ASCIIInputStream ais = new ASCIIInputStream(new BufferedInputStream(is));
            readPAZ(ais);
            ais.close();

        } catch (IOException ioe) {
            throw new ResponseException(ioe.getMessage());
        }

        if (URLName != null) {
            shortName = new String(URLName);
            longName = new String(URLName);
        }

        initDefaults(channel, timeSeries);

        if (DEBUG) {
            System.out.println(this);
        }

        //System.out.println(this);
    }
    /** constructor from Internet service */
    // http://www.iris.edu/resp/resp.do?net=IU&sta=SDV&loc=00&cha=BHZ&time=2009.261-2009.262
    public static String INTERNET_SEED_RESP_SERVICE_URL = "http://www.iris.edu/resp/resp.do";

    public PoleZeroResponse(TimeInstant refTimeInstant, BasicChannel channel, TimeSeries timeSeries) throws ResponseException {

        String net = channel.network;
        String sta = channel.staName;
        String loc = channel.locName;
        if (loc == null || loc.equals(BasicChannel.UNDEF_STRING)) {
            if (channel.chanName.trim().length() >= 6) {
                loc = channel.chanName.substring(4, 6);
            } else {
                loc = "--";
            }
        }
        if (loc.trim().length() < 2) {
            loc = "--";
        }
        String cha = channel.chanName.substring(0, 3);
        String time = "" + refTimeInstant.getYear() + "." + NumberFormat.intString(refTimeInstant.getDoY(), 3);
        String URLName = INTERNET_SEED_RESP_SERVICE_URL + "?" + "net=" + net + "&sta=" + sta + "&loc=" + loc + "&cha=" + cha + "&time=" + time;
        System.out.println("Response request URL: " + URLName);
        try {

            // open input stream
            InputStream is = null;
            is = GeneralInputStream.openStream(null, URLName, false);

            ASCIIInputStream ais = new ASCIIInputStream(new BufferedInputStream(is));
            readSEED_RESP(ais);
            ais.close();

        } catch (IOException ioe) {
            throw new ResponseException(ioe.getMessage());
        }

        if (URLName != null) {
            shortName = new String(URLName);
            longName = new String(URLName);
        }

        initDefaults(channel, timeSeries);

        if (DEBUG) {
            System.out.println(this);
        }

        //System.out.println(this);
    }

    /** initialize default values for certain fields */
    public void initDefaults(BasicChannel channel, TimeSeries timeSeries) {

        String[] beforeUnits = {PhysicalUnits.getDefaultLengthUnits()};
        this.setBeforeUnits(beforeUnits);
        String[] afterUnits = {PhysicalUnits.COUNTS};
        this.setAfterUnits(afterUnits);
        this.setBeforeName(GROUND_DISP_NAME);
        if (channel != null) {
            this.setAfterName(channel.instName);
        }
        this.setType(Response.DERIVATIVE);  // default: assume result of application of response function is velocity

        if (timeSeries != null) {
            this.dt = timeSeries.sampleInt;
        }

    }

    /** returns gain or constant amplication factor */
    public double getGain() {

        return (gain);

    }

    /** sets transformType */
    public void setTransformType(int transformType) {

        this.transformType = transformType;

    }

    /** sets transformType */
    public int getTransformType() {

        return (transformType);

    }

    /** given S-plane poles & zeros, compute Z-plane poles & zeros, by bilinear transform */
    public PoleZeroResponse convertToZ(double sampleInterval) throws ResponseException {

        if (transformType == Z) {
            throw (new ResponseException(FreqText.response_already_z));
        }

        PoleZeroResponse pzr = new PoleZeroResponse(this);

        if (zeros.length < poles.length) {
            pzr.zeros = new Cmplx[poles.length];
        }

        for (int i = 0; i < zeros.length; i++) {
            pzr.zeros[i] = fromSplaneToZplane(zeros[i], sampleInterval);
        }
        for (int i = zeros.length; i < poles.length; i++) {
            pzr.zeros[i] = new Cmplx(-1.0, 0.0);
        }

        for (int i = 0; i < poles.length; i++) {
            pzr.poles[i] = fromSplaneToZplane(poles[i], sampleInterval);
        }

        pzr.transformType = PoleZeroResponse.Z;

        return (pzr);

    }

    /** converts poles and zeros from Z plane to S plane
     *
     * @param pz
     * @param sampleInterval
     * @return
     */
    protected static final Cmplx fromSplaneToZplane(Cmplx pz, double sampleInterval) {

        // bilinear transform
        // return (2.0 + pz) / (2.0 - pz);
        //return ((new Cmplx(2.0 / 0.05, 0.0)).add(pz).div((new Cmplx(2.0 / 0.05, 0.0)).sub(pz)));

        // exact mapping of the z-plane to the s-plane
        return(Cmplx.exp(Cmplx.mul(pz, sampleInterval)));
        //return (Cmplx.exp(Cmplx.mul(pz, 1.0)));
    }

    /**
     * reads a SAC-format pole-zero file.
     *
     * converted to Java from Earthworm function readPZ in source file transfer.c
     *
     *            Pole-zero-gain files must be for input displacement in
     *            nanometers, output in digital counts, poles and zeros of
     *            the LaPlace transform, frequency in radians per second.
     */
    private static final int KEY = 0;
    private static final int POLE = 1;
    private static final int ZERO = 2;

    protected void readPAZ(ASCIIInputStream ais) throws ResponseException, IOException {

        boolean foundConstant = false;

        gain = 1.0;		// default gain is 1.0
        poles = new Cmplx[0];
        zeros = new Cmplx[0];

        int state = KEY;

        String word;
        String line;

        int nz = 0, np = 0;

        while ((line = ais.readLine()) != null) {

            StringTokenizer strTokenizer =
                    new StringTokenizer(line);

            // empty lines
            if (!strTokenizer.hasMoreTokens()) {
                continue;
            }

            word = strTokenizer.nextToken();

            // a comment line
            if (word.startsWith("*") || word.startsWith("#")) {
                continue;
            }

            // check for keywords
            if (word.equalsIgnoreCase("CONSTANT")) {
                foundConstant = true;
                gain = Double.valueOf(strTokenizer.nextToken()).doubleValue();
                state = KEY;
                continue;
            } else if (word.equalsIgnoreCase("ZEROS")) {
                int iNumZeros = Integer.valueOf(strTokenizer.nextToken()).intValue();
                if (iNumZeros < 0) {
                    throw (new ResponseException(
                            FreqText.invalid_number_of_zeros + ": " + iNumZeros));
                }
                zeros = new Cmplx[iNumZeros];
                for (int i = 0; i < zeros.length; i++) {
                    zeros[i] = new Cmplx(0.0, 0.0);		// unspecified zeros will be at origin
                }
                if (iNumZeros > 0) {
                    state = ZERO;		// some zeros may follow
                    continue;
                }
                state = KEY;		// got the number of zeros: none
                continue;
            } else if (word.equalsIgnoreCase("POLES")) {
                int iNumPoles = Integer.valueOf(strTokenizer.nextToken()).intValue();
                if (iNumPoles < 0) {
                    throw (new ResponseException(FreqText.invalid_number_of_poles + ": " + iNumPoles));
                }
                poles = new Cmplx[iNumPoles];
                for (int i = 0; i < poles.length; i++) {
                    poles[i] = new Cmplx(0.0, 0.0);		// unspecified poles will be at origin
                }
                if (iNumPoles > 0) {
                    state = POLE;		// some poles may follow
                    continue;
                }
                state = KEY;		// got the number of poles: none
                continue;
            } else {
                switch (state) {

                    case KEY:		// were looking for next keyword
                        // invalid keyword
                        //throw(new ResponseException(FreqText.invalid_keyword + ": " + word));
                        continue;

                    case ZERO:
                        // looking for Zeros
                        if (nz >= zeros.length) {
                            throw (new ResponseException(FreqText.too_many_zeros_read));
                        }
                        zeros[nz] = new Cmplx(
                                Double.valueOf(word).doubleValue(),
                                Double.valueOf(strTokenizer.nextToken()).doubleValue());
                        if (++nz == zeros.length) {
                            state = KEY;	// found all the zeros we expected
                        }
                        continue;

                    case POLE:
                        // looking for poles
                        if (np >= poles.length) {
                            throw (new ResponseException(FreqText.too_many_poles_read));
                        }
                        poles[np] = new Cmplx(
                                Double.valueOf(word).doubleValue(),
                                Double.valueOf(strTokenizer.nextToken()).doubleValue());
                        if (++np == poles.length) {
                            state = KEY;	// found all the poles we expected
                        }
                        continue;
                }
            }
        }

        if (!foundConstant && poles.length == 0 && zeros.length == 0) {
            throw (new ResponseException(FreqText.empty_transfer_function + ": " + ais.toString()));
        }

        return;

    }

    /**
     * reads a SEED_RESP-format pole-zero file.
     *
     */
    @SuppressWarnings("empty-statement")
    protected void readSEED_RESP(ASCIIInputStream ais) throws ResponseException, IOException {

        /*
        B053F03     Transfer function type:                A
        B053F04     Stage sequence number:                 1
        B053F05     Response in units lookup:              M/S - Velocity in Meters Per Second
        B053F06     Response out units lookup:             V - Volts
        B053F07     A0 normalization factor:               +8.60830E+04
        B053F08     Normalization frequency:               +2.00000E-02
        B053F09     Number of zeroes:                      2
        B053F14     Number of poles:                       5
        #              Complex zeroes:
        #              i  real          imag          real_error    imag_error
        B053F10-13     0  +0.00000E+00  +0.00000E+00  +0.00000E+00  +0.00000E+00
        B053F10-13     1  +0.00000E+00  +0.00000E+00  +0.00000E+00  +0.00000E+00
        #              Complex poles:
        #              i  real          imag          real_error    imag_error
        B053F15-18     0  -5.94313E+01  +0.00000E+00  +0.00000E+00  +0.00000E+00
        B053F15-18     1  -2.27121E+01  +2.71065E+01  +0.00000E+00  +0.00000E+00
        B053F15-18     2  -2.27121E+01  -2.71065E+01  +0.00000E+00  +0.00000E+00
        B053F15-18     3  -4.80040E-03  +0.00000E+00  +0.00000E+00  +0.00000E+00
        B053F15-18     4  -7.31990E-02  +0.00000E+00  +0.00000E+00  +0.00000E+00


        B058F03     Stage sequence number:                 0
        B058F04     Sensitivity:                           +8.64730E+08
        B058F05     Frequency of sensitivity:              +2.00000E-02
        B058F06     Number of calibrations:                0
         */

        boolean notSupported = false;

        gain = 1.0;		// default gain is 1.0
        poles = new Cmplx[0];
        zeros = new Cmplx[0];

        boolean inTransferFunctionType_A = false;
        boolean inTransferFunctionType_B = false;
        boolean isTransferFunctionType_B = false;

        boolean inStageSequence_0 = false;
        boolean inStageSequence_1 = false;

        int gamma = -1;
        double A0_norm = Double.NaN;
        double f_norm = Double.NaN;
        double Sd_chan = Double.NaN;
        double f_chan = Double.NaN;

        String word;
        String line;

        int nz = 0, np = 0;

        while ((line = ais.readLine()) != null) {

            StringTokenizer strTokenizer = new StringTokenizer(line);

            // empty lines
            if (!strTokenizer.hasMoreTokens()) {
                continue;
            }

            word = strTokenizer.nextToken();

            // a comment line
            if (word.startsWith("*") || word.startsWith("#")) {
                continue;
            }

            // check for keywords

            if (word.equalsIgnoreCase("B053F03")) {
                //B053F03     Transfer function type:                A
                inTransferFunctionType_A = false;
                inTransferFunctionType_B = false;
                while (!(word = strTokenizer.nextToken()).endsWith(":"));
                word = strTokenizer.nextToken();
                if (word.equals("A")) {
                    inTransferFunctionType_A = true;
                } else if (word.equals("B")) {
                    inTransferFunctionType_B = true;
                    isTransferFunctionType_B = true;
                }
                continue;
            }
            if (word.equalsIgnoreCase("B053F04") || word.equalsIgnoreCase("B058F03")) {
                //B053F04     Stage sequence number:                 1
                //B058F03     Stage sequence number:                 0
                inStageSequence_0 = false;
                inStageSequence_1 = false;
                while (!(word = strTokenizer.nextToken()).endsWith(":"));
                word = strTokenizer.nextToken();
                if (word.equals("0")) {
                    inStageSequence_0 = true;
                } else if (word.equals("1")) {
                    inStageSequence_1 = true;
                }
                continue;
            }

            if ((inTransferFunctionType_A || inTransferFunctionType_B) && inStageSequence_1) {
                if (word.equalsIgnoreCase("B053F05")) {
                    //B053F05     Response in units lookup:              M/S - Velocity in Meters Per Second
                    /*
                     * ah_resp.c
                     * Convert to displacement :
                     * if acceleration, gamma=2    \
                     * elseif velocity,     gamma=1 \
                     * elseif displacement, gamma=0  \___Done above
                     */
                    while (!(word = strTokenizer.nextToken()).endsWith(":"));
                    word = strTokenizer.nextToken();
                    if (word.equals("M")) {
                        setType(Response.NO_CONVERSION);
                        gamma = 0;
                        continue;
                    } else if (word.equals("M/S")) {
                        setType(Response.DERIVATIVE);
                        gamma = 1;
                        continue;
                    } else if (word.equals("NM/S")) {
                        setType(Response.DERIVATIVE);
                        gamma = 1;
                        continue;
                    } else if (word.equals("M/S**2")) {
                        setType(Response.DOUBLE_DERIVATIVE);
                        gamma = 2;
                        continue;
                    }
                    notSupported = true;
                    break;
                } else if (word.equalsIgnoreCase("B053F06")) {
                    //B053F06     Response out units lookup:             V - Volts
                    while (!(word = strTokenizer.nextToken()).endsWith(":"));
                    word = strTokenizer.nextToken();
                    if (word.equals("V")) {
                        continue;
                    }
                    if (word.equals("COUNTS")) {    // NOTE: AJL 20100212 Is it OK to simply do nothing special if COUNTS instead of V, try: http://www.iris.edu/resp/resp.do?net=CN&sta=GAC&loc=--&cha=BHZ&time=2010.001
                        continue;     // uncomment here if OK
                    }
                    notSupported = true;
                    break;
                } else if (word.equalsIgnoreCase("B053F07")) {
                    //B053F07     A0 normalization factor:               +8.60830E+04
                    while (!(word = strTokenizer.nextToken()).endsWith(":"));
                    A0_norm = Double.valueOf(strTokenizer.nextToken()).doubleValue();
                    continue;
                } else if (word.equalsIgnoreCase("B053F08")) {
                    //B053F08     Normalization frequency:               +2.00000E-02
                    while (!(word = strTokenizer.nextToken()).endsWith(":"));
                    f_norm = Double.valueOf(strTokenizer.nextToken()).doubleValue();
                    continue;
                } else if (word.equalsIgnoreCase("B053F09")) {
                    //B053F09     Number of zeroes:                      2
                    while (!(word = strTokenizer.nextToken()).endsWith(":"));
                    int iNumZeros = Integer.valueOf(strTokenizer.nextToken()).intValue();
                    if (iNumZeros < 0) {
                        throw (new ResponseException(
                                FreqText.invalid_number_of_zeros + ": " + iNumZeros));
                    }
                    zeros = new Cmplx[iNumZeros];
                    for (int i = 0; i < zeros.length; i++) {
                        zeros[i] = new Cmplx(0.0, 0.0);		// unspecified zeros will be at origin
                    }
                    continue;
                } else if (word.equalsIgnoreCase("B053F14")) {
                    //B053F14     Number of poles:                       5
                    while (!(word = strTokenizer.nextToken()).endsWith(":"));
                    int iNumPoles = Integer.valueOf(strTokenizer.nextToken()).intValue();
                    if (iNumPoles < 0) {
                        throw (new ResponseException(FreqText.invalid_number_of_poles + ": " + iNumPoles));
                    }
                    poles = new Cmplx[iNumPoles];
                    for (int i = 0; i < poles.length; i++) {
                        poles[i] = new Cmplx(0.0, 0.0);		// unspecified poles will be at origin
                    }
                } else if (word.equalsIgnoreCase("B053F10-13")) {
                    //#              Complex zeroes:
                    //#              i  real          imag          real_error    imag_error
                    //B053F10-13     0  +0.00000E+00  +0.00000E+00  +0.00000E+00  +0.00000E+00
                    if (nz >= zeros.length) {
                        throw (new ResponseException(FreqText.too_many_zeros_read));
                    }
                    strTokenizer.nextToken();
                    zeros[nz] = new Cmplx(
                            Double.valueOf(strTokenizer.nextToken()).doubleValue(),
                            Double.valueOf(strTokenizer.nextToken()).doubleValue());
                    nz++;
                } else if (word.equalsIgnoreCase("B053F15-18")) {
                    //#              Complex poles:
                    //#              i  real          imag          real_error    imag_error
                    //B053F15-18     0  -5.94313E+01  +0.00000E+00  +0.00000E+00  +0.00000E+00
                    if (np >= poles.length) {
                        throw (new ResponseException(FreqText.too_many_poles_read));
                    }
                    strTokenizer.nextToken();
                    poles[np] = new Cmplx(
                            Double.valueOf(strTokenizer.nextToken()).doubleValue(),
                            Double.valueOf(strTokenizer.nextToken()).doubleValue());
                    np++;
                }
            }

            if (inStageSequence_0) {
                if (word.equalsIgnoreCase("B058F04")) {
                    //B058F04     Sensitivity:                           +8.64730E+08
                    while (!(word = strTokenizer.nextToken()).endsWith(":"));
                    Sd_chan = Double.valueOf(strTokenizer.nextToken()).doubleValue();
                    continue;
                } else if (word.equalsIgnoreCase("B058F05")) {
                    //B058F05     Frequency of sensitivity:              +2.00000E-02
                    while (!(word = strTokenizer.nextToken()).endsWith(":"));
                    f_chan = Double.valueOf(strTokenizer.nextToken()).doubleValue();
                    continue;
                }
            }

        }

        System.out.println("gamma A0_norm f_norm Sd_chan f_chan poles.length zeros.length");
        System.out.println("" + gamma + " " + A0_norm + " " + f_norm + " " + Sd_chan + " " + f_chan + " " + poles.length + " " + zeros.length);

        if (notSupported) {
            throw (new ResponseException(FreqText.unsupperted_transfer_function_type + ": " + line));
        }
        if ((gamma < 0 || Double.isNaN(A0_norm) || Double.isNaN(f_norm) || Double.isNaN(Sd_chan) | Double.isNaN(f_chan)) || (poles.length == 0 && zeros.length == 0)) {
            throw (new ResponseException(FreqText.empty_transfer_function + ": " + ais.toString()));
        }


        /*
         * First, AH assumes the units of the poles and zeros are rad/sec,
         * so we convert Type B (Hz) to Type A (rad/sec) if necessary.
         *
         * If Type==B then convert to type A format by:
         *
         * P(n) = 2*pi*P(n)      { n=1...Np }
         * Z(m) = 2*pi*Z(m)      { m=1...Nz }
         * A0   = A0 * (2*pi)**(Np-Nz)
         */

        if (isTransferFunctionType_B) {
            for (int i = 0; i < poles.length; i++) {
                poles[i].r *= 2.0 * Math.PI;
                poles[i].i *= 2.0 * Math.PI;
            }
            for (int i = 0; i < zeros.length; i++) {
                zeros[i].r *= 2.0 * Math.PI;
                zeros[i].i *= 2.0 * Math.PI;
            }
            /* A0   = A0 * (2*pi)**(Np-Nz) */
            A0_norm = A0_norm * Math.pow(2.0 * Math.PI, (double) (poles.length - zeros.length));
        }       /* if transfer function was analog - 'B' */


        /*
         * ah_resp.c
         *
         * Convert to displacement :
         * if acceleration, gamma=2    \
         * elseif velocity,     gamma=1 \
         * elseif displacement, gamma=0  \___Done above
         * else  print error message     /
         * endif                        /
         *
         * Sd = Sd * (2*pi*fs)**gamma
         * Nz = Nz + gamma
         * set values of new zeros equal zero
         * A0 = A0 / (2*pi*fn)**gamma
         * Units = M - Displacement Meters
         */

        /*
         * ah_resp.c
         *
         * add gamma zeros
         * Nz = Nz + gamma
         * set values of new zeros equal zero
         */
        if (gamma > 0) {
            Cmplx[] new_zeros = new Cmplx[zeros.length + gamma];
            System.arraycopy(zeros, 0, new_zeros, 0, zeros.length);
            for (int i = zeros.length; i < new_zeros.length; i++) {
                new_zeros[i] = new Cmplx(0.0, 0.0);		// unspecified zeros will be at origin
            }
            zeros = new_zeros;
        }


        /*
         * ah_resp.c
         *
         * Sd = Sd * (2*pi*fs)**gamma
         * A0 = A0 / (2*pi*fn)**gamma
         * Units = M - Displacement Meters
         */
        if (f_norm != f_chan) {
            A0_norm = A0_norm / Math.pow(2.0 * Math.PI * f_norm, gamma);
            Sd_chan = Sd_chan * Math.pow(2.0 * Math.PI * f_chan, gamma);
        }

        //2007.180.18.04.30.1650.MN.TIR..BHZ.R.SAC
        /*
         * Third, there is no place in the AH header to specify either
         * the frequency of normalization or the frequency of the
         * digital sensitivity.  This is not a problem as long as these
         * two are the same.  If they are different then evaluate the
         * normalization at the frequency of the digital sensitivity.
         *
         *
         * if fn is not equal to fs then
         *  A0 = abs(prod{n=1...Np} [2*pi*i*fs - P(n)] /
        prod{m=1..Nz} [2*pi*i*fs - Z(m)])
         * endif
         */
        if (f_norm != f_chan) {
            A0_norm = calc_A0(poles, zeros, f_chan);
        }

        this.gain = A0_norm * Sd_chan;

        System.out.println("ZEROS " + zeros.length);
        for (int i = 0; i < zeros.length; i++) {
            System.out.println("" + zeros[i]);
        }
        System.out.println("POLES " + poles.length);
        for (int i = 0; i < poles.length; i++) {
            System.out.println("" + poles[i]);
        }
        System.out.println("CONSTANT " + this.gain);

        return;

    }

    /**
     * Utiltiy funciton from ah_resp.c
     */
    protected double calc_A0(Cmplx[] poles, Cmplx[] zeros, double ref_freq) {
        int i;

        Cmplx numer = new Cmplx();
        Cmplx denom = new Cmplx();
        Cmplx f0 = new Cmplx();
        Cmplx hold = new Cmplx();

        double a0;


        f0.r = 0;

        f0.i = 2 * Math.PI * ref_freq;

        hold.i = zeros[0].i;
        hold.r = zeros[0].r;
        denom = Cmplx.sub(f0, hold);

        for (i = 1; i < zeros.length; i++) {
            hold.i = zeros[i].i;
            hold.r = zeros[i].r;

            denom = Cmplx.mul(denom, Cmplx.sub(f0, hold));

        }

        hold.i = poles[0].i;
        hold.r = poles[0].r;

        numer = Cmplx.sub(f0, hold);

        for (i = 1; i < poles.length; i++) {
            hold.i = poles[i].i;
            hold.r = poles[i].r;

            numer = Cmplx.mul(numer, Cmplx.sub(f0, hold));

        }

        a0 = Cmplx.div(numer, denom).mag();

        return a0;

    }

    /**
     * Returns the frequency response from the pole-zero-gain information.
     *
     * converted to Java from Earthworm function response in source file transfer.c
     *
     * @param     nfft    the number of points that will be used in the FFT
     * @param     deltat  the time interval between data points in the time-domain
     *
     * @return    an array of Cmplx's containing the frequency response
     */
    public final Cmplx[] response(int nfft, double deltat) {


        int ntr = nfft;

        Cmplx[] cxResp = new Cmplx[ntr];


        double domega = 2.0 * Math.PI / ((double) nfft * deltat);
        double sr, si, rtmp, itmp, mag2;

        /*
        // The (almost) zero frequency term
        // The zeros, in the numerator
        double srn, sin, srd, sid;
        srn = 1.0;
        sin = 0.0;
        double omega = domega * 0.001;
        for (int j = 0; j < this.zeros.length; j++)
        {
        sr = - this.zeros[j].r;
        si = omega - this.zeros[j].i;
        rtmp = srn * sr - sin * si;
        itmp = srn * si + sin * sr;
        srn = rtmp;
        sin = itmp;
        }
        // The poles; in the denominator
        srd = 1.0;
        sid = 0.0;
        for (int j = 0; j < this.poles.length; j++)
        {
        sr = - this.poles[j].r;
        si = omega - this.poles[j].i;
        rtmp = srd * sr - sid * si;
        itmp = srd * si + sid * sr;
        srd = rtmp;
        sid = itmp;
        }

        double rtmp, itmp;
        // Combine numerator, denominator and gain using complex arithemetic
        mag2 = this.gain / (srd * srd + sid * sid);
        rtmp = mag2 * (srn * srd + sin * sid);
        itmp = 0.0; // Actually the Nyqust part; we don't want it
        cxResp[0] = new Cmplx(rtmp, itmp);
         */
        cxResp[0] = new Cmplx(0.0, 0.0);

        // The non-zero frequency parts
        double omega1;
        double srn1, sin1, srd1, sid1;
        int i2;
        for (int i1 = 1; i1 <= ntr / 2; i1++) {
            i2 = ntr - i1;
            omega1 = domega * (double) i1;

            // The zeros, in the numerator
            srn1 = 1.0;
            sin1 = 0.0;
            for (int j = 0; j < this.zeros.length; j++) {
                sr = -this.zeros[j].r;
                // first half
                si = omega1 - this.zeros[j].i;
                rtmp = srn1 * sr - sin1 * si;
                itmp = srn1 * si + sin1 * sr;
                srn1 = rtmp;
                sin1 = itmp;
            }

            // The poles; in the denominator
            srd1 = 1.0;
            sid1 = 0.0;

            for (int j = 0; j < this.poles.length; j++) {
                sr = -this.poles[j].r;
                // first half
                si = omega1 - this.poles[j].i;
                rtmp = srd1 * sr - sid1 * si;
                itmp = srd1 * si + sid1 * sr;
                srd1 = rtmp;
                sid1 = itmp;
            }

            // Combine numerator, denominator and gain using complex arithemetic
            if (i1 != ntr / 2) {
                // first half
                mag2 = this.gain / (srd1 * srd1 + sid1 * sid1);
                rtmp = mag2 * (srn1 * srd1 + sin1 * sid1);
                itmp = -mag2 * (sin1 * srd1 - srn1 * sid1);
                cxResp[i1] = new Cmplx(rtmp, itmp);
                // second half
                cxResp[i2] = new Cmplx(rtmp, -itmp);
            } else {
                mag2 = 0.5 * this.gain / (srd1 * srd1 + sid1 * sid1);
                rtmp = mag2 * (srn1 * srd1 + sin1 * sid1);
                itmp = mag2 * (sin1 * srd1 - srn1 * sid1);
                cxResp[i1] = new Cmplx(rtmp, itmp);
            }
        }

        return (cxResp);

    }

    /** Returns the division of this response by another response.
     *
     * @param     resp  a response.
     * @return    the combined response this/resp.
     */
    public final Response div(Response resp) throws ResponseException {

        PoleZeroResponse pzr1 = this;
        PoleZeroResponse pzr2 = null;
        try {
            pzr2 = (PoleZeroResponse) resp;
        } catch (ClassCastException cce) {
            throw (new ResponseException(FreqText.cannot_divide_argument_is_not_a_PoleZeroResponse));
        }
        PoleZeroResponse newPzr = new PoleZeroResponse();

        // Combine the response functions into one: pzr1 / pzr2 */
        newPzr.gain = pzr1.gain / pzr2.gain;
        newPzr.poles = new Cmplx[pzr1.poles.length + pzr2.zeros.length];
        newPzr.zeros = new Cmplx[pzr1.zeros.length + pzr2.poles.length];
        // Copy the poles and zeros, using structure copy
        int nz = 0, np = 0;
        for (int i = 0; i < pzr1.poles.length; i++) {
            newPzr.poles[np++] = new Cmplx(pzr1.poles[i]);
        }
        for (int i = 0; i < pzr1.zeros.length; i++) {
            newPzr.zeros[nz++] = new Cmplx(pzr1.zeros[i]);
        }
        for (int i = 0; i < pzr2.poles.length; i++) {
            newPzr.zeros[nz++] = new Cmplx(pzr2.poles[i]);
        }
        for (int i = 0; i < pzr2.zeros.length; i++) {
            newPzr.poles[np++] = new Cmplx(pzr2.zeros[i]);
        }

        //System.out.println(this);
        //System.out.println(pzr2);
        //System.out.println(newPzr);

        return (newPzr);
    }

    /** Returns the response at a given frequency.
     *  following eq. 10.11 in Scherbaum, F. , Of Poles and Zeros Fundamentals of Digital Seismology
     *   Series: Modern Approaches in Geophysics, Vol. 15
     *
     * @param     frequency  frequency in Hz at which to evaluate response.
     * @return    the complex response.
     */
    public final Cmplx evaluateResponse(double frequency) {

        Cmplx jw = new Cmplx(0.0, frequency * 2.0 * Math.PI);

        Cmplx numerator = new Cmplx(1.0, 0.0);
        for (int i = 0; i < zeros.length; i++) {
            numerator = numerator.mul(new Cmplx(Cmplx.sub(jw, zeros[i])));
        }

        Cmplx denominator = new Cmplx(1.0, 0.0);
        for (int i = 0; i < poles.length; i++) {
            denominator = denominator.mul(new Cmplx(Cmplx.sub(jw, poles[i])));
        }

        Cmplx response = Cmplx.mul(Cmplx.div(numerator, denominator), this.gain);

        return (response);

    }

    /** returns the String value of this response */
    public String toString() {

        String str = "";

        str += "-----------\n";
        str += "TRANSFORM_TYPE " + (this.transformType == this.LAPLACE ? "Laplace\n" : "Z\n");
        str += "SHORT_NAME " + this.shortName + "; ";
        str += "LONG_NAME " + this.longName + "; ";
        str += "TYPE " + this.type + "\n";
        str += "CONSTANT " + this.gain + "\n";
        str += "POLES " + this.poles.length + "\n";
        for (int i = 0; i < this.poles.length; i++) {
            str += "   " + this.poles[i].r + "  " + this.poles[i].i + "\n";
        }
        str += "ZEROS " + this.zeros.length + "\n";
        for (int i = 0; i < this.zeros.length; i++) {
            str += "   " + this.zeros[i].r + "  " + this.zeros[i].i + "\n";
        }
        str += "-----------\n";

        if (DEBUG) {
            double mag = 0.0;
            for (int i = 0; i < this.poles.length; i++) {
                mag += this.poles[i].mag();
            }
            str += "POLES MAG: " + mag + "\n";
            mag = 0.0;
            for (int i = 0; i < this.zeros.length; i++) {
                mag += this.zeros[i].mag();
            }
            str += "ZEROS MAG: " + mag + "\n";
        }

        return (str);
    }

    // main to test class
    public static void main(String argv[]) {

        PoleZeroResponse pzr = null;

        try {
            pzr = new PoleZeroResponse(null, argv[0], null, null, null);
            System.out.println(pzr);
        } catch (Exception e) {
            System.out.println(e);
            return;
        }

        try {
            PoleZeroResponse newPzr = (PoleZeroResponse) pzr.div(pzr);
            System.out.println(newPzr);
        } catch (Exception e) {
            System.out.println(e);
            return;
        }

        try {
            Cmplx[] cmplxResp = pzr.response(64, 0.1);
            for (int i = 0; i < cmplxResp.length; i++) {
                System.out.println(cmplxResp[i]);
            }
        } catch (Exception e) {
            System.out.println(e);
            return;
        }


    }
}	// End class PoleZeroResponse

