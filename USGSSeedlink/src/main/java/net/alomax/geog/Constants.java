/*
 * This file is part of the Anthony Lomax Java Library.
 *
 * Copyright (C) 2001 Anthony Lomax <anthony@alomax.net>
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


package net.alomax.geog;



import java.io.Serializable;


/** A basic geographic constants object */

/**
  * A class defining geographic Constants.
  *
  * @author  Anthony Lomax
  * @version %I%, %G%
  */

public class Constants implements Serializable {


    /** Invalid or un-initialized char value. */
	public static final char UNDEF_CHAR ='?';
    /** Invalid or un-initialized String value. */
	public static final String UNDEF_STRING = new String("?");
    /** Invalid or un-initialized integer value. */
	public static final int UNDEF_INT = Integer.MAX_VALUE;
    /** Invalid or un-initialized double value. */
	public static final double UNDEF_DOUBLE = Double.MAX_VALUE;


    /** uninitialized units */
	public static final int UNKNOWN = 0;
    /** km units */
	public static final int KILOMETERS = 1;
    /** degree units */
	public static final int DEGREES = 2;

        
    /** degree unit char */
	public static final char DEGREES_CHAR = (char) 186;

    /** Radians per degree */
	public static final double RPD = Math.PI / 180.0;
	public static final double DEG2RAD = RPD;
	public static final double RAD2DEG = 1.0 / RPD;

    /** Kilometers per degree */
	public static final double KPD = 10000.0 / 90.0;
	public static final double DEG2KM = KPD;
	public static final double KM2DEG = 1.0 / KPD;

    /** Earth radius in kilometers */
	public static final double EARTH_RADIUS = 6371.0;


}	// end class Constants


