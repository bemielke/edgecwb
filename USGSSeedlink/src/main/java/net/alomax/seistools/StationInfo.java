/*
 * This file is part of the Anthony Lomax Java Library.
 *
 * Copyright (C) 2007 Anthony Lomax <anthony@alomax.net>
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

/*
 * StationInfo.java
 *
 * Created on 16 November 2007, 16:28
 */

/** The development of this software is supported by institutional funds of 
 *  the Istituto Nazionale di Geofisica e Vulcanologia (INGV), 'Centro Nazionale Terremoti', Roma, Italy.
 *  Please acknowledge INGV whenever using or referring to this software.
 */

package net.alomax.seistools;

/**
 *
 * @author  anthony
 */

import net.alomax.geog.*;
import net.alomax.util.*;

import java.io.*;


public class StationInfo implements Comparable, Serializable {
    
/*
AFHM, 39.0418,-120.7913, 1064.0,MNLO,Open,Forest Hill Site
AFI,-13.9094,-171.7772,  706.0,WEL,Open,Afiamalu
AFIF, 24.1010,  43.1800,  950.0,RYD,Open,`Afif
AFL, 46.5300,  12.1800, 2235.0,TRI,Open,Alpe Faloria
 */
    
    /** The network name. */
    public String network = null;
    /** The station name. */
    public String staName = null;
    /** The station position. */
    public Position staPosition = null;
    
    /** Empty Constructor
     *
     */
    public StationInfo() {
        ;        
    }
    
    
    
    /** Constructor from station list file line
     *
     */
    public StationInfo(String network, String staName, double latitude, double longitude, double elevation) {
        
        this.network = network;
        this.staName = staName;
        this.staPosition = new Position(latitude, longitude, elevation, 0.0);
        
    }
    
    
    
    /** Constructor from station list file line
     *
     */
    public StationInfo(String line) {
        
        String tokens[] = StringExt.parse(line, ",");
        
        network = tokens[4];
        staName = tokens[0];
        double latitude = Double.parseDouble(tokens[1]);
        double longitude = Double.parseDouble(tokens[2]);
        double elevation = Double.parseDouble(tokens[3]) / 1000.0;
        staPosition = new Position(latitude, longitude, elevation, 0.0);
        
    }
    
    
    
    /** Returns a string representation of the object.
     *
     * @return a string representation of the object
     */
    public String toString() {
        
        String theString = network +  staName + "\n" + staPosition;
        
        return(theString);
        
    }
    
    
/** Compares this object with the specified object for order. 
**/
/* 
    Returns a negative integer, zero, or a positive integer as this object is less than, equal to, 
    or greater than the specified object.

    In the foregoing description, the notation sgn(expression) designates the mathematical signum function, 
    which is defined to return one of -1, 0, or 1 according to whether the value of expression is 
    negative, zero or positive. The implementor must ensure sgn(x.compareTo(y)) == -sgn(y.compareTo(x)) for all x and y. 
    (This implies that x.compareTo(y) must throw an exception iff y.compareTo(x) throws an exception.)

    The implementor must also ensure that the relation is transitive: 
    (x.compareTo(y)>0 && y.compareTo(z)>0) implies x.compareTo(z)>0.

    Finally, the implementer must ensure that x.compareTo(y)==0 implies that sgn(x.compareTo(z)) == sgn(y.compareTo(z)), 
    for all z.

    It is strongly recommended, but not strictly required that (x.compareTo(y)==0) == (x.equals(y)). 
    Generally speaking, any class that implements the Comparable interface and violates this condition should 
    clearly indicate this fact. The recommended language is "Note: this class has a natural ordering that is 
    inconsistent with equals." 
*/
    
    public int compareTo(Object obj) {
        
        return(staName.compareTo(((StationInfo) obj).staName));
        
    }   
    
    
}
