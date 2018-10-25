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


package net.alomax.freq.mkfilter;

import net.alomax.math.*;


/** A class to represent a color mapping.
 *
 */


public class PoleZeroRep {
    
    public static int MAXPZ = 512; // .ge. 2*MAXORDER, to allow for doubling of poles in BP filter; high values needed for FIR filters
    
    public Cmplx[] poles = new Cmplx[MAXPZ];
    public Cmplx[] zeros = new Cmplx[MAXPZ];
    
    public int numpoles = 0;
    public int numzeros = 0;
    
    
    /** constructor */
    
    public PoleZeroRep() {
        
    }
    
    
    
    
}  // End - class


