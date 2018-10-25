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
 * SeedChannel.java
 *
 * Created on 16 November 2007, 16:29
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
import net.alomax.seis.*;
import net.alomax.util.*;


public class SeedChannel extends BasicChannel implements Comparable {
    
    
    /** The hypocenter-station distance. */
    public double distance = -1.0;
    
    /** The P arrival time. */
    public TimeInstant pTime = null;
    
    /** The start time of available data. */
    public TimeInstant startTime = null;
    
    /** The stop time of available data. */
    public TimeInstant stopTime = null;
    
    /** The begin time of data to use. */
    public TimeInstant beginTime = null;
    
    /** The end time of data to use. */
    public TimeInstant endTime = null;
    
    protected static final int SORT_DISTANCE = 0;
    protected static int sortType = SORT_DISTANCE;
    
    
    
    /** Empty constructor.
     *
     */
    public SeedChannel() {
        ;
    }
    
    
    
    /** Constructor.
     *
     */
    public SeedChannel(String network, String staName, String chanName, String compName, String locName, String typeName) {
        
        if (network != null)
            this.network = network;
        if (staName != null)
            this.staName = staName;
        if (chanName != null)
            this.chanName = chanName;
        if (compName != null)
            this.compName = compName;
        if (locName != null)
            this.locName = locName;
        if (typeName != null)
            this.typeName = typeName;
        
    }
    
    
    
    /** Constructor.
     *
     */
    public SeedChannel(String network, String staName, String chanName, String compName, String locName) {
        
        this(network, staName, chanName, compName, locName, null);
        
    }
    
    
    
    
    /** Constructor.
     *
     */
    public SeedChannel(StationInfo stationInfo, String chanName, String compName, String locName) {
        
        this(stationInfo.network, stationInfo.staName, chanName, compName, locName, null);
        this.staPosition = stationInfo.staPosition;
        
    }
    
    
    
    
    
    /** Returns a string representation of the object.
     *
     * @return a string representation of the object
     */
    public String toString() {
        
        String theString = network + staName + "." + chanName + compName + locName;
        
        return(theString);
        
    }
    
    
    /** Returns a string representation of the object.
     *
     * @return a string representation of the object
     */
    public String toStringNetSta() {
        
        // NET_STA format, for example:
        // IU_KONO:BHE BHN,GE_WLF,MN_AQU:HH?.D
        
        //String theString = network + "_" + staName + ":" + chanName + compName + "." + locName;
        String theString = network + "_" + staName + ":" + locName + chanName + compName;
        
        return(theString);
        
    }
    
    
    
    
    
    /** Returns a string representation of the object.
     *
     * @return a string representation of the object
     */
    public String toStringLong() {
        
        String theString = network + staName + "." + chanName + compName + locName;
        theString += " startTime=" + startTime;
        theString += " stopTime=" + stopTime;
        theString += " pTime=" + pTime;
        
        return(theString);
        
    }
    
    
    /**
     * implements Comparable
     * Compares this object with the specified object for order.
     * Returns a negative integer, zero, or a positive integer as this object is less than, equal to, or greater than the specified object.
     **/
    
    public int compareTo(Object o){
        
        SeedChannel other = (SeedChannel) o;
        
        if (other.equals(this))
            return(0);
        
        int compare = 0;
        
        if (sortType == SORT_DISTANCE) {
            if (other.distance < 0.0 && this.distance < 0.0)
                return(0);
            if (other.distance < 0.0)
                return(-1);
            if (this.distance < 0.0)
                return(1);
            if (other.distance > this.distance)
                return(-1);
            if (this.distance > other.distance)
                return(1);
            return(0);
        }
        
        return(0);
    }
    
    
    
    
    
    
}



