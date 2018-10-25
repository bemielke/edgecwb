/*
 * This file is part of the Anthony Lomax Java Library.
 *
 * Copyright (C) 2007 Anthony Lomax <anthony@alomax.net www.alomax.net>
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

import net.alomax.freq.*;
import net.alomax.freq.mkfilter.MakeFilter;
import net.alomax.math.*;

public class RecursionFilter extends TimeDomainProcess {

    public static final boolean VERBOSE = false;
    public static final int TYPE_DEFAULT = 0;
    public int type = TYPE_DEFAULT;
    private static final int NUM_TYPES = 1;
    // types - !!! MUST MATCH TYPES AND ORDER ABOVE
    public static final String NAME_DEFAULT = "DEFAULT";
    public String errorMessage;
    //private static final int WINDOW_MIN = 1;
    //private static final int WINDOW_MAX = Integer.MAX_VALUE;
    protected double[] inputCoeffs = null;
    protected double[] outputCoeffs = null;
    protected double gain = 0.0;
    protected PoleZeroResponse poleZeroResponse = null;
    protected double sampleInterval = 1.0;
    protected String ampUnitsAfter = null;

    /**
     * constructor
     */
    public RecursionFilter(String localeText, int type) {

        this.type = type;
        this.errorMessage = " ";

        TimeDomainText.setLocale(localeText);
    }

    /**
     * copy constructor
     */
    public RecursionFilter(RecursionFilter rf) {

        this.useMemory = rf.useMemory;
        if (rf.memory != null) {
            this.memory = new TimeDomainMemory(rf.memory);
        }

        this.type = rf.type;
        this.errorMessage = rf.errorMessage;
        if (rf.inputCoeffs != null) {
            this.inputCoeffs = new double[rf.inputCoeffs.length];
            System.arraycopy(rf.inputCoeffs, 0, this.inputCoeffs, 0, rf.inputCoeffs.length);
        }
        if (rf.outputCoeffs != null) {
            this.outputCoeffs = new double[rf.outputCoeffs.length];
            System.arraycopy(rf.outputCoeffs, 0, this.outputCoeffs, 0, rf.outputCoeffs.length);
        }
        this.gain = rf.gain;
        this.sampleInterval = rf.sampleInterval;

    }

    /**
     * Method to set ampUnitsAfter
     */
    public void setAmpUnitsAfter(String ampUnitsAfter) {

        this.ampUnitsAfter = ampUnitsAfter;

    }

    /**
     * Method to set type
     */
    public void setType(String typeStr) throws TimeDomainException {

        if (NAME_DEFAULT.startsWith(typeStr.toUpperCase())) {
            type = TYPE_DEFAULT;
        } else {
            throw new TimeDomainException(TimeDomainText.invalid_recursion_filter_type + ": " + typeStr);
        }

    }

    /**
     * Method to set recursion filter type
     */
    public void setType(int type) throws TimeDomainException {

        if (type >= 0 && type < NUM_TYPES) {
            this.type = type;
        } else {
            throw new TimeDomainException(TimeDomainText.invalid_recursion_filter_type + ": " + type);
        }

    }

    /**
     * Method to get recursion filter type
     */
    public int getType() {

        return (type);

    }

    /**
     * Method to check settings
     */
    public void checkSettings() throws TimeDomainException {

        String errMessage = "RecursionFilter";
        int badSettings = 0;

        try {
            setType(type);
        } catch (Exception e) {
            errMessage += ": " + e.getMessage();
        }

        if (badSettings > 0) {
            throw new TimeDomainException(errMessage + ".");
        }

    }

    /**
     * * function to apply recursion filter
     */
    public final float[] apply(double dt, float[] sample) throws TimeDomainException {

        if (type == TYPE_DEFAULT) {
            return (applyDefault(dt, sample));
        }

        return (sample);

    }

    /**
     * function to apply recursion filter
     */
    public final float[] applyDefault(double dt, float[] sample) throws TimeDomainException {

        if (sample.length < inputCoeffs.length || sample.length < outputCoeffs.length) {
            throw new TimeDomainException("RecursionFilter: " + TimeDomainText.recursion_filter_number_coefficients);
        }

        //float factor = (float) (1.0 / (gain));
        double factor = gain;

        TimeDomainMemory localMemory = null;

        if (useMemory) { // use stored memory
            if (memory == null) // no stored memory initialized
            {
                memory = new TimeDomainMemory(inputCoeffs.length, sample[0], outputCoeffs.length, (float) (sample[0] * factor));
            }
            localMemory = memory;
        } else {
            localMemory = new TimeDomainMemory(inputCoeffs.length, sample[0], outputCoeffs.length, (float) (sample[0] * factor));
        }

        // !!! IMPORTANT - must use a double array to accumulate results to help maintain precision
        double[] newSample = new double[sample.length];

        int imemory = Math.max(inputCoeffs.length, outputCoeffs.length);

        for (int n = 0; n < imemory && n < sample.length; n++) {

            double value = 0.0;
            for (int j = 0; j < inputCoeffs.length; j++) {
                int index = n - j;
                if (index >= 0) {
                    value += inputCoeffs[j] * (double) sample[index];
                } else {
                    value += inputCoeffs[j] * localMemory.input[inputCoeffs.length + index];
                }
            }
            value *= factor;
            for (int j = 1; j < outputCoeffs.length; j++) {
                int index = n - j;
                if (index >= 0) {
                    value += outputCoeffs[j] * newSample[index];
                } else {
                    value += outputCoeffs[j] * localMemory.output[outputCoeffs.length + index];
                }
            }

            newSample[n] = value;

        }
        for (int n = imemory; n < sample.length; n++) {

            double value = 0.0;
            for (int j = 0; j < inputCoeffs.length; j++) {
                value += inputCoeffs[j] * (double) sample[n - j];
            }
            value *= factor;
            for (int j = 1; j < outputCoeffs.length; j++) {
                value += outputCoeffs[j] * newSample[n - j];
            }

            newSample[n] = value;

        }

        // TODO: following is not correct, but works for simple int and diff
        //double scale = gain * Math.pow(dt, outputCoeffs.length - inputCoeffs.length);
        //double scale = dt;
        //float scale = (float) (1.0 / gain);
        //for (int n = 0; n < newSample.length; n++)
        //    newSample[n] *= scale;
        float[] newFloatSample = new float[newSample.length];
        for (int n = 0; n < newFloatSample.length; n++) {
            newFloatSample[n] = (float) newSample[n];
        }

        // save memory if used
        if (useMemory) { // using stored memory
            memory.updateInput(sample);
            memory.updateOutput(newFloatSample);
            //System.arraycopy(sample, sample.length - memory.input.length, memory.input, 0, memory.input.length);
            //System.arraycopy(newFloatSample, newFloatSample.length - memory.output.length, memory.output, 0, memory.output.length);
        }

        return (newFloatSample);

    }

    /**
     * Update fields in TimeSeries object
     */
    public void updateFields(TimeSeries timeSeries) {

        if (poleZeroResponse != null) {
            timeSeries.ampUnits = poleZeroResponse.convertUnitsForward(timeSeries.ampUnits);
        } else if (ampUnitsAfter != null) {
            timeSeries.ampUnits = ampUnitsAfter;
        }

    }

    /**
     * Returns true if this process modifies trace amplitude
     *
     * @return true if this process modifies trace amplitude.
     */
    public boolean amplititudeModified() {

        return (true);

    }

    /**
     * Returns true if this process supports memory usage
     *
     * @return true if this process supports memory usage.
     */
    public boolean supportsMemory() {

        return (true);

    }

    /**
     * convert poles and zeros to filter coefficients
     *
     * adapted from http://www-users.cs.york.ac.uk/~fisher/software/mkfilter/current/mkfilter.C
     */
    public void setPoleZeroResponse(PoleZeroResponse pzr, double sampleInterval, double calibFreq) {

        this.sampleInterval = sampleInterval;

        if (VERBOSE) {
            System.out.println(pzr.toString());
        }

        if (pzr.getTransformType() != PoleZeroResponse.Z) {
            try {
                poleZeroResponse = pzr.convertToZ(sampleInterval);
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            poleZeroResponse = pzr;
        }

        // check computed coeffs of z^k are all real
        for (int i = 0; i < poleZeroResponse.poles.length; i++) {
            if (poleZeroResponse.poles[i].hypot() >= 1.0000001) {
                System.out.println("ERROR: RecursionFilter.setPoleZeroResponse(): pole " + i
                        + ": " + poleZeroResponse.poles[i].toString() + " mag=" + (float) poleZeroResponse.poles[i].hypot()
                        + ": is not within the unit circle: filter should be unstable.");
            } else if (poleZeroResponse.poles[i].hypot() >= 0.98) {
                System.out.println("WARNING: RecursionFilter.setPoleZeroResponse(): pole " + i
                        + ": " + poleZeroResponse.poles[i].toString() + " mag=" + (float) poleZeroResponse.poles[i].hypot()
                        + ": is on or only margninally within the unit circle: filter may be unstable.");
            }
        }

        Cmplx zeros[] = null;
        Cmplx poles[] = null;
        // copy poles and zeros to local arrays
        if (true) {
            zeros = poleZeroResponse.zeros;
            poles = poleZeroResponse.poles;
        } else {
            // TEST: copy poles and zeros to local arrays and make sure num zeros is same as num poles
            int lmax = Math.max(poleZeroResponse.poles.length, poleZeroResponse.zeros.length);
            zeros = new Cmplx[lmax];
            poles = new Cmplx[lmax];
            for (int i = 0; i < lmax; i++) {
                if (i < poleZeroResponse.poles.length) {
                    poles[i] = poleZeroResponse.poles[i];
                } else {
                    poles[i] = new Cmplx(1.0, 0.0);
                }
                if (i < poleZeroResponse.zeros.length) {
                    zeros[i] = poleZeroResponse.zeros[i];
                } else {
                    zeros[i] = new Cmplx(-1.0, 0.0);
                }
            }
        }

        if (VERBOSE) {
            System.out.println(poleZeroResponse.toString());
        }
        Cmplx[] topcoeffs = expand(zeros);
        Cmplx[] botcoeffs = expand(poles);
        if (VERBOSE) {
            // Display the feedForward and feedBack coefficients
            System.out.println("Numerator Coefficients:");
            for (int cnt = topcoeffs.length - 1; cnt >= 0; cnt--) {
                System.out.println("z^" + cnt + " " + topcoeffs[cnt]);
            }
            System.out.println("Denominator Coefficients:");
            for (int cnt = botcoeffs.length - 1; cnt >= 0; cnt--) {
                System.out.println("z^" + cnt + " " + botcoeffs[cnt]);
            }
        }

        // set gain
        Cmplx dc_gain = MakeFilter.evaluate(topcoeffs, topcoeffs.length - 1, botcoeffs, botcoeffs.length - 1, new Cmplx(1.0, 0.0));
        Cmplx hf_gain = MakeFilter.evaluate(topcoeffs, topcoeffs.length - 1, botcoeffs, botcoeffs.length - 1, new Cmplx(-1.0, 0.0));
        Cmplx calib_gain = new Cmplx(1.0, 0.0);
        if (calibFreq >= 0.0) {
            double theta = 2.0 * Math.PI * sampleInterval * calibFreq; // "jwT" for 1-Hz freq.
            calib_gain = MakeFilter.evaluate(topcoeffs, topcoeffs.length - 1, botcoeffs, botcoeffs.length - 1, Cmplx.exp(0.0, theta));
        }
        if (VERBOSE) {
            System.out.println("TEST: dc_gain: " + dc_gain);
            System.out.println("TEST: calib_gain: " + calib_gain);
            System.out.println("TEST: hf_gain: " + hf_gain);
        }

        if (VERBOSE) {
            System.out.println("topcoeffs.length: " + topcoeffs.length
                    + " zeros.length: " + zeros.length);
            System.out.println("botcoeffs.length: " + botcoeffs.length
                    + " poles.length: " + poles.length);

        }
        double normalization = botcoeffs[botcoeffs.length - 1].r;
        if (VERBOSE) {
            System.out.println("Normalization: " + normalization);
        }
        double[] xcoeffs = new double[zeros.length + 1];
        for (int i = 0; i < xcoeffs.length; i++) {
            xcoeffs[xcoeffs.length - i - 1] = topcoeffs[i].r / normalization;
        }
        double[] ycoeffs = new double[poles.length + 1];
        for (int i = 0; i < ycoeffs.length; i++) {
            ycoeffs[ycoeffs.length - i - 1] = -botcoeffs[i].r / normalization;
        }

        // set class data values
        inputCoeffs = xcoeffs;
        outputCoeffs = ycoeffs;
        gain = poleZeroResponse.gain / calib_gain.hypot();

        if (VERBOSE) {
            // Display the feedForward and feedBack coefficients
            System.out.println("Input or Feed-Forward (Numerator) Coefficients :");
            double sum = 0.0;
            for (int cnt = inputCoeffs.length - 1; cnt >= 0; cnt--) {
                System.out.println("   x[n-" + cnt + "] " + inputCoeffs[cnt]);
                sum += inputCoeffs[cnt];
            }
            System.out.println("   Sum: " + sum);
            System.out.println("Output or Feedback (Denominator) Coefficients :");
            sum = 0.0;
            for (int cnt = outputCoeffs.length - 1; cnt >= 0; cnt--) {
                System.out.println("   y[n-" + cnt + "] " + outputCoeffs[cnt]);
                sum += outputCoeffs[cnt];
            }
            System.out.println("   Sum: " + sum);
            System.out.println("Gain: " + gain);
        }

    }
    /**
     * compute product of poles or zeros as a polynomial of z
     */
    protected double TOLERANCE = 1.0e-12;

    protected Cmplx[] expand(Cmplx pz[]) {

        Cmplx[] coeffs = new Cmplx[pz.length + 1];

        coeffs[0] = new Cmplx(1.0, 0.0);
        for (int i = 1; i < coeffs.length; i++) {
            coeffs[i] = new Cmplx(0.0, 0.0);
        }
        for (int i = 0; i < pz.length; i++) {
            coeffs = multin(pz[i], coeffs);
        }

        // check computed coeffs of z^k are all real
        for (int i = 0; i < pz.length + 1; i++) {
            if (Math.abs(coeffs[i].i) > TOLERANCE) {
                System.out.println("ERROR: RecursionFilter.expand(): coeff of z^" + i
                        + " (imag = " + Math.abs(coeffs[i].i) + ") is not real; poles/zeros are not Cmplx conjugates");
            }
            coeffs[i].i = 0.0;
        }

        return (coeffs);

    }

    /**
     * multiply factor (z-w) into coeffs
     */
    protected Cmplx[] multin(Cmplx w, Cmplx coeffs[]) {

        Cmplx nw = w.mul(-1.0);

        for (int i = coeffs.length - 1; i >= 1; i--) {
            coeffs[i].mul(nw).add(coeffs[i - 1]);
        }
        coeffs[0].mul(nw);

        return (coeffs);

    }

    // main to test class
    public static void main(String argv[]) {

        System.out.println("Usage: java net.alomax.timedom.RecursionFilter pz_file filter_name calib_freq sample_rate [sample_rate]...");

        if (argv.length < 4) {
            return;
        }

        try {

            String commandLineRoot = "java net.alomax.timedom.RecursionFilter";

            String pzFileName = argv[0];
            commandLineRoot += " " + argv[0];
            String filterNameRoot = argv[1];
            commandLineRoot += " " + filterNameRoot;
            double calibFreq = Double.valueOf(argv[2]);
            commandLineRoot += " " + calibFreq;

            String code = "";
            for (int nfilt = 0; nfilt < argv.length - 3; nfilt++) {
                String sample_rate = argv[nfilt + 3];
                double sampleInterval = 1.0;
                sampleInterval = 1.0 / Double.valueOf(sample_rate);
                String commandLine = commandLineRoot + " " + sample_rate;
                String filterName = "RECURSION_FILTER_" + sample_rate;
                filterName = filterNameRoot + "_" + sample_rate;
                filterName += "sps";
                RecursionFilter rf = new RecursionFilter(null, RecursionFilter.TYPE_DEFAULT);
                PoleZeroResponse pzr = new PoleZeroResponse(null, argv[0], null, null, null);
                rf.setPoleZeroResponse(pzr, sampleInterval, calibFreq);
                code += rf.toCcode(rf, filterName, calibFreq, commandLine);
            }
            System.out.println(code);

        } catch (Exception e) {
            System.out.println(e);
            return;
        }

    }

    /**
     * output recursion filter as C code
     */
    public static String toCcode(RecursionFilter rf, String filterName, double calibFreq, String commandLine) {

        String gainstr = "GAIN_" + filterName;

        String code = "\n\n\n"
                + "/** Digital filter designed by net.alomax.timedom.RecursionFilter\n"
                + "  Command line: " + commandLine + "\n"
                + "*/\n";

        code += "#define " + gainstr + "  " + (1.0 / rf.gain) + "  // evaluated at " + calibFreq + "Hz\n"
                + "void filter_" + filterName + "_impl(float* sample, int num_samples, float* sampleNew) {\n";

        code += "     int n;\n"
                + "\n"
                + "     for (n = 0; n < num_samples; n++) {\n"
                + "\n";
        for (int nx = 1; nx < rf.inputCoeffs.length; nx++) {
            code += "         xv[" + (nx - 1) + "] = xv[" + nx + "];\n";
        }

        code += "         xv[" + (rf.inputCoeffs.length - 1) + "] = (double) sample[n] / " + gainstr + ";\n";

        for (int ny = 1; ny < rf.outputCoeffs.length; ny++) {
            code += "         yv[" + (ny - 1) + "] = yv[" + ny + "];\n";
        }

        code += "             yv[" + (rf.outputCoeffs.length - 1) + "] = 0.0\n";
        for (int nx = 0; nx < rf.inputCoeffs.length; nx++) {
            code += "              + " + rf.inputCoeffs[rf.inputCoeffs.length - nx - 1] + " * xv[" + nx + "]\n";
        }
        for (int ny = 0; ny < rf.outputCoeffs.length - 1; ny++) {
            code += "              + " + rf.outputCoeffs[rf.outputCoeffs.length - ny - 1] + " * yv[" + ny + "]\n";
        }
        code += "         ;\n";
        code += "        sampleNew[n] = (float) yv[" + (rf.outputCoeffs.length - 1) + "];\n";

        code += "    }\n";

        code += "}\n";

        return (code);

    }
}	// End class GaussianFilter

