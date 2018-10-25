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


package net.alomax.freq;

import net.alomax.math.*;



/** FrequencyDomainProcess */

/**
  * An interface representing processes that acts on 
  *   frequency domain, complex arrays.
  *
  * @author  Anthony Lomax
  * @version %I%, %G%
  * @see     Cmplx
  */
	  
public interface  FrequencyDomainProcess {

	/**  Does pre-processing on the time-domain data.
	  *
      * @param     dt  the time-domain sampling interval in seconds. 
      * @param     x  the array of float values to be processed. 
      * @return    the processed float values.
	  */
 
	public float[] preProcess(double dt, float[] x);


        
        
        /**  Does post-processing on the time-domain data.
	  *
      * @param     dt  the time-domain sampling interval in seconds. 
      * @param     x  the array of float values to be processed. 
      * @return    the processed float values.
	  */
 
	public float[] postProcess(double dt, float[] x);
        
        
	/**  Returns true if pre-processing on the time-domain data changes samples.
	  *
      * @return    true if post-processing on the time-domain data changes samples, false otherwise.
	  */
 
        public boolean sampleChangedInPreProcess();

        
	/**  Returns true if post-processing on the time-domain data changes samples.
	  *
      * @return    true if post-processing on the time-domain data changes samples, false otherwise.
	  */
 
        public boolean sampleChangedInPostProcess();
        

	/**  Does processing in the frequency domain.
	  *
      * @param     dt  the time-domain sampling interval in seconds. 
      * @param     cx  the array of complex values to be processed. 
      * @return    a new array of processed complex vaulues.
	  */
 
	public Cmplx[] apply(double dt, Cmplx[] cx);


	/**  Update fields in TimeSeries object.
	  *
      * @param     timeSeries  the TimeSeries object. 
	  */
 
	public void updateFields(TimeSeries timeSeries);


	/** Checks process parameters
	  *
      * @throws     FreqException  if a setting is invalid. 
	  */

	public void checkSettings() throws FreqException;



}	// End interface FrequencyDomainProcess


