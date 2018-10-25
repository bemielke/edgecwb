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


import net.alomax.util.*;

import java.io.Serializable;


/** A basic geographic position object */

/**
 * A class representing a geographic position.
 *
 * @author  Anthony Lomax
 * @version %I%, %G%
 */

public class Position extends Constants implements Serializable {
    
    
    /** Latitude */
    public double latitude = UNDEF_DOUBLE;
    /** Longitude */
    public double longitude = UNDEF_DOUBLE;
    /** Elevation (km) */
    public double elevation = UNDEF_DOUBLE;
    /** Depth below elevation (km) */
    public double depth = UNDEF_DOUBLE;
    
    
    /** Empty constructor */
    
    public Position() {
        
        ;
        
    }
    
    
    /** Constructor */
    
    public Position(double latitude, double longitude) {
        
        this(latitude, longitude, 0.0, 0.0);
        
    }
    
    
    /** Constructor */
    
    public Position(double latitude, double longitude, double depth) {
        
        this(latitude, longitude, 0.0, depth);
        
    }
    
    
    /** Constructor */
    
    public Position(double latitude, double longitude, double elevation, double depth) {
        
        this.latitude = latitude;
        this.longitude = longitude;
        this.elevation = elevation;
        this.depth = depth;
        
    }
    
    
    /** Copy constructor */
    
    public Position(Position pos) {
        
        this.latitude = pos.latitude;
        this.longitude = pos.longitude;
        this.elevation = pos.elevation;
        this.depth = pos.depth;
        
    }
    
    
    
    /** Returns a string representation of the object.
     *
     * @return a string representation of the object
     */
    public String toString() {
        
        String endline = System.getProperty("line.separator");
        
        return(new String(
        "latitude = " +  latitude + endline +
        "longitude = " +  longitude + endline +
        "elevation = " +  elevation + endline +
        "depth = " +  depth + endline
        ));
        
    }
    

    /** Returns a one-line, geographic (lat long depth) string representation of the object.
     *
     * @return a string representation of the object
     */
    public String toStringGeog(int ndec) {
                
        return(new String(
        "lat:" + NumberFormat.floatString((float) latitude, ndec + 3, ndec) + " "
        + "lon:" + NumberFormat.floatString((float) longitude, ndec + 3, ndec) + " "
        + "depth:" + NumberFormat.floatString((float) (depth - elevation), ndec + 3, ndec) + "km"
        ));
        
    }
    
}	// end class Position


