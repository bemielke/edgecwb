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

package net.alomax.seis;

import java.io.Serializable;


/** A basic item object */

/**
  * A base class representing a seismic item.
  *   <P>
  *
  * @author  Anthony Lomax
  * @version %I%, %G%
  */

public class BasicItem implements Serializable {
	
	// debug - level
	public static int DEBUG_LEVEL = 0;

    /** Invalid or un-initialized char value. */
	public static final char UNDEF_CHAR ='?';
    /** Invalid or un-initialized String value. */
	public static final String UNDEF_STRING = new String("?");
    /** Invalid or un-initialized integer value. */
	public static final int UNDEF_INT = Integer.MAX_VALUE;
    /** Invalid or un-initialized double value. */
	public static final double UNDEF_DOUBLE = Double.MAX_VALUE;
    /** Invalid or un-initialized float value. */
	public static final float UNDEF_FLOAT = Float.MAX_VALUE;



	/** Compare String fields */

	public static int compareFields(String field1, String field2) {

		if (field1.compareTo(UNDEF_STRING) == 0 || field2.compareTo(UNDEF_STRING) == 0)
			return(0);

		return(field1.compareTo(field2));

	}


	/** Compare int fields */

	public static int compareFields(int field1, int field2) {

		if (field1 == UNDEF_INT || field2 == UNDEF_INT)
			return(0);

		if (field1 - field2 > 0)
			return(1);
		if (field1 - field2 < 0)
			return(-1);

		return(0);

	}


	/** Compare double fields */

	public static int compareFields(double field1, double field2, double tolerance) {

		if (field1 == UNDEF_DOUBLE || field2 == UNDEF_DOUBLE)
			return(0);

		if (field1 - field2 > tolerance)
			return(1);
		if (field1 - field2 < -tolerance)
			return(-1);

		return(0);

	}




}	// end class BasicItem


