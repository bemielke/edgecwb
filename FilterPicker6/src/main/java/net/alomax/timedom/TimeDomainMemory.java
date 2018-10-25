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


public class TimeDomainMemory {
    
    public float[] input = null;
    public float[] output = null;
    
    
    /** constructor */
    
    public TimeDomainMemory() {
        
    }
    
    
    /** constructor */
    
    public TimeDomainMemory(int lengthInput, int lengthOutput) {
        
        input = new float[lengthInput];
        output = new float[lengthOutput];
        
    }
    
    
    /** constructor */
    
    public TimeDomainMemory(int lengthInput, float inputInitalValue, int lengthOutput, float outputInitalValue) {
        
        input = new float[lengthInput];
        for (int i = 0; i < input.length; i++)
            input[i] = inputInitalValue;
        output = new float[lengthOutput];
        for (int i = 0; i < output.length; i++)
            output[i] = outputInitalValue;
        
    }
    
    
    
    /** constructor */
    
    public TimeDomainMemory(float[] input, float[] output) {
        
        this.input = new float[input.length];
        System.arraycopy(input, 0, this.input, 0, input.length);
        this.output = new float[output.length];
        System.arraycopy(output, 0, this.output, 0, output.length);
        
    }
    
    
    
    /** copy constructor */
    
    public TimeDomainMemory(TimeDomainMemory tdm) {
        
        if (tdm.input != null) {
            this.input = new float[tdm.input.length];
            System.arraycopy(tdm.input, 0, this.input, 0, tdm.input.length);
        }
        if (tdm.output != null) {
            this.output = new float[tdm.output.length];
            System.arraycopy(tdm.output, 0, this.output, 0, tdm.output.length);
        }
        
    }
    
    
    
    /** update memory using specifed number of points from end of specified array */
    
    protected void update(float[] array, float[] sample) {
        
        if (sample.length >= array.length) {
            int sampleIndex = sample.length - array.length;
            for (int n = 0; n < array.length; n++) {
                array[n] = sample[sampleIndex];
                sampleIndex++;
            }
        }
        else {  // sample length less than memory length
            // shift data in memory
            for (int n = 0; n < array.length - sample.length; n++) {
                array[n] = array[n + sample.length];
            }
            // append samle data
            int sampleIndex = 0;
            for (int n = array.length - sample.length; n < array.length; n++) {
                array[n] = sample[sampleIndex];
                sampleIndex++;
            }
        }
        
    }
    
    
    
    /** update output memory using specifed number of points from end of specified array */
    
    public void updateOutput(float[] sample) {
        
        update(output, sample);
        
    }
    
    
    
    /** update input memory using specifed number of points from end of specified array */
    
    public void updateInput(float[] sample) {
        
        update(input, sample);
        
    }
    
    
    
}


