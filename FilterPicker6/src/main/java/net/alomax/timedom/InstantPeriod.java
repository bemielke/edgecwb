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



public class InstantPeriod extends TimeDomainProcess {
    
    public double alpha;
    
    public String errorMessage;
    
    private static final double ALPHA_MIN = Double.MIN_VALUE;
    private static final double ALPHA_MAX = Double.MAX_VALUE;
    
    
    /** constructor */
    
    public InstantPeriod(String localeText, double alpha) {
        this.alpha = alpha;
        this.errorMessage = " ";
        
        TimeDomainText.setLocale(localeText);
    }
    
    

    /** copy constructor */
    public InstantPeriod(InstantPeriod intantPeriod) {

        this.alpha = intantPeriod.alpha;

        this.useMemory = intantPeriod.useMemory;
        if (intantPeriod.memory != null) {
            this.memory = new TimeDomainMemory(intantPeriod.memory);
        }

    }
    
    /** Method to set alpha */
    
    public void setAlpha(double alphaValue)
    throws TimeDomainException {
        if (alphaValue < ALPHA_MIN || alphaValue > ALPHA_MAX) {
            throw new TimeDomainException(
            TimeDomainText.invalid_alpha_value);
        }
        
        alpha = alphaValue;
    }
    
    
    /** Method to set alpha */
    
    public void setAlpha(String str)
    throws TimeDomainException {
        
        double alphaValue;
        
        try {
            alphaValue = Double.valueOf(str).doubleValue();
        } catch (NumberFormatException e) {
            throw new TimeDomainException(
            TimeDomainText.invalid_alpha_value);
        }
        
        setAlpha(alphaValue);
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
        
        if (alpha < ALPHA_MIN || alpha > ALPHA_MAX) {
            errMessage += ": " + TimeDomainText.invalid_alpha_value;
            badSettings++;
        }
        
        if (badSettings > 0) {
            throw new TimeDomainException(errMessage + ".");
        }
        
    }
    
    
    
    /*** function to calculate instantaneous period  (Tau_p)
     *
     * implements eqs 1-3 in :
     *  Allen, R.M., and H. Kanamori,
     *     The Potential for Earthquake Early Warning in Southern California,
     *     Science, 300 (5620), 786-789, 2003.
     */
    
    public final float[] apply(double dt, float[] sample) {
        
        if (useMemory) { // use stored memory
            if (memory == null) // no stored memory initialized
            {
                memory = new TimeDomainMemory(1, 0.0f, 2, 0.0f);
            }
        }

        double twopi = (float) (2.0 * Math.PI);
        float dtf = (float) dt;
        float decayConst = (float) (1.0 - dt / alpha);
        
        float xval = 0.0f;
        float dval = 0.0f;
        float sampleLast = 0.0f;
        if (useMemory) { // using stored memory
            sampleLast = memory.input[0];
            xval = memory.output[0];
            dval = memory.output[1];
        }
        
        for (int i = 0; i < sample.length; i++) {
            
            xval = xval * decayConst + sample[i] * sample[i];
            float deriv = (sample[i] - sampleLast) / dtf;
            dval = dval * decayConst + deriv * deriv;
            sampleLast = sample[i];
            sample[i] = (float) (twopi * Math.sqrt(xval / dval));
            
        }
        
        // save memory if used
        if (useMemory) { // using stored memory
            memory.input[0] = sampleLast;
            memory.output[0] = xval;
            memory.output[1] = dval;
        }

        return(sample);
        
    }
    
    
    
    /**  Update fields in TimeSeries object */
    
    public void updateFields(TimeSeries timeSeries) {
        
        timeSeries.ampUnits = timeSeries.timeUnits;
        
    }
    
    
    /** Returns true if this process modifies trace amplitude
     *
     * @return    true if this process modifies trace amplitude.
     */
    
    public boolean amplititudeModified() {
        
        return(true);
        
    }
    
    
    
}	// End class GaussianFilter


