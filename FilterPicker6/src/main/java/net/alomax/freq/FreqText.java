/*
 * This file is part of the Anthony Lomax Java Library.
 *
 * Copyright (C) 1999-2000 Anthony Lomax <anthony@alomax.net>
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

import net.alomax.text.LocalizedText;


/** A class for multi-language text
 *
 * All of the methods of this class are static.
 */

public class FreqText extends LocalizedText {
    
    // default en_US text
    
    // general freq exceptions
    public static String invalid_frequency_process = "invalid frequency process";
    public static String invalid_convolution_master_spectrum = "invalid convolution master spectrum";
    public static String invalid_convolution_master_channel = "invalid convolution master channel";
    public static String invalid_convolution_type = "invalid convolutions type";
    public static String invalid_master_reference_time_offset = "invalid convolution master reference time offset";
    public static String invalid_water_level = "invalid water level";
    
    // Butterworth filter exceptions
    public static String invalid_low_frequency_corner = "invalid low frequency corner";
    public static String invalid_high_frequency_corner = "invalid high frequency corner";
    public static String invalid_number_of_poles = "invalid number of poles";
    public static String low_corner_greater_than_high_corner = "low frequency corner greater than high frequency corner";
    
    // Gaussian filter exceptions
    public static String invalid_center_frequency = "invalid center frequency";
    public static String invalid_alpha_value = "invalid alpha value";
    
    // Response exceptions
    public static String transfer_function_not_found = "transfer function not found";
    public static String unsupperted_transfer_function_type = "unsupported transfer function type";
    public static String empty_transfer_function = "empty transfer function";
    public static String cannot_deterime_response_type = "cannot determine response type";
    public static String invalid_number_of_zeros = "invalid number of zeros";
    public static String cannot_divide_argument_is_not_a_PoleZeroResponse = "cannot divide: argument is not a PoleZeroResponse";
    public static String invalid_keyword = "invalid keyword";
    public static String error_reading_zero = "error reading zero";
    public static String error_reading_pole = "error reading pole";
    public static String too_many_zeros_read = "too many zeros read";
    public static String too_many_poles_read = "too many poles read";
    public static String invalid_frequency_taper_values = "invalid frequency taper values";
    public static String response_already_z = "response already in Z domain";
    public static String response_name_not_found = "response name not found";
    
    
    /** Sets locale and text */
    
    public static void setLocale(String locName) {
        
        LocalizedText.setLocale(locName);
        
        // French, France
        
        if (localeName.toLowerCase().startsWith("fr")) {
            
            
        }
        
        
        // Italian, Italy
        
        else if (localeName.toLowerCase().startsWith("it")) {
            
            
        }
        
        
        // default English, USA
        
        else {		// use default en_US
            
        }
        
    }
    
    
    
}	// end class FreqText

