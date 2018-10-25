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

import net.alomax.geog.*;

import java.io.Serializable;

/** A basic Seismogram object */

/**
 * A base class representing a basic data channel.
 *   <P>
 *
 * @author  Anthony Lomax
 * @version %I%, %G%
 */

public class BasicChannel extends BasicItem implements Serializable {
    
    
    
    
    /** The network name. */
    public String network = UNDEF_STRING;
    /** The station name. */
    public String staName = UNDEF_STRING;
    /** The instrument name. */
    public String instName = UNDEF_STRING;
    /** The channel name. */
    public String chanName = UNDEF_STRING;
    /** The component name. */
    public String compName = UNDEF_STRING;
    /** The location name. */
    public String locName = UNDEF_STRING;   // AJL 20071119 - added for compatibility with SEED naming conventions
    
    /** The location name. */
    public String typeName = UNDEF_STRING;   // AJL 20071207 - added for compatibility with SeisComP Data Structure (SDS) definition
    
    // AJL 20070607 - Bug fix to allow correct grouping of GSE21 traces
    /** An auxilliary channel identifaction name. (e.g., needed for SeisDataGSE21)*/
    public String auxChannelIdName = UNDEF_STRING;
    
    /** The station position. */
    public Position staPosition = new Position();
    
    /** The component azimuth (degrees clockwise from North). */
    public double azimuth = UNDEF_DOUBLE;
    /** The component inclination (degrees downwards from UP). */
    public double inclination = UNDEF_DOUBLE;
    
    
    
    
    /** Empty constructor.
     *
     */
    public BasicChannel() {
        ;
    }
    
    
    
    /** Full constructor.
     *
     */
    public BasicChannel(String network, String staName, String instName, String chanName, String locName, String compName,
    String auxChannelIdName, Position staPosition, double azimuth,  double inclination) {
        
        if (network != null)
            this.network = network;
        if (staName != null)
            this.staName = staName;
        if (instName != null)
            this.instName = instName;
        if (chanName != null)
            this.chanName = chanName;
        if (locName != null)
            this.locName = locName;
        if (compName != null)
            this.compName = compName;
        if (auxChannelIdName != null)
            this.auxChannelIdName = auxChannelIdName;
        if (staPosition != null)
            this.staPosition = staPosition;
        this.azimuth = azimuth;
        this.inclination = inclination;
        
    }
    
    
    
    
    
    /** returns a SeisCompP file name */
    
    /*
A SeisComP Data Structure (SDS) definition
Aug. 13, 2003
SeisComP Data Structure (SDS) 1.0
Purpose:
Define a simple directory and file structure for data files. The SDS
provides a basic level of standardization and portability when
adapting data servers (AutoDRM, NetDC, etc.), analysis packages
(Seismic Handler, SNAP, etc.) and other classes of software that need
direct access to data files.
The basic directory and file layout is defined as:
<SDSdir>/Year/NET/STA/CHAN.TYPE/NET.STA.LOC.CHAN.TYPE.YEAR.DAY
Definitions of fields:
SDSdir : arbitrary base directory
YEAR : 4 digit YEAR
NET : Network code/identifier, 1-8 characters, no spaces
STA : Station code/identifier, 1-8 characters, no spaces
CHAN : Channel code/identifier, 1-8 characters, no spaces
TYPE : 1 characters indicating the data type, recommended types are:
’D’ - Waveform data
’E’ - Detection data
’L’ - Log data
’T’ - Timing data
’C’ - Calibration data
’R’ - Response data
’O’ - Opaque data
LOC : Location identifier, 1-8 characters, no spaces
DAY : 3 digit day of year, padded with zeros
The dots, ’.’, in the file names must always be present regardless if
neighboring fields are empty.
Additional data type flags may be used for extended structure definition.
     */
    
    public String toSeisComPFileName() {
        
        // NET.STA.LOC.CHAN.TYPE.YEAR.DAY
        
        StringBuffer filename = new StringBuffer();
        if (this.network != UNDEF_STRING)
            filename.append(this.network);
        filename.append('.');
        if (this.staName != UNDEF_STRING)
            filename.append(this.staName);
        filename.append('.');
        if (this.locName != UNDEF_STRING)
            filename.append(this.locName);
        filename.append('.');
        if (this.chanName != UNDEF_STRING)
            filename.append(this.chanName);
        if (this.compName != UNDEF_STRING)
            filename.append(this.compName);
        filename.append('.');
        if (this.typeName != UNDEF_STRING)
            filename.append(this.typeName);
        filename.append('.');
        // year
        filename.append('.');
        // day
        
        return(filename.toString());
        
    }
    
    
    
    
    
    
    
    /** Returns a string representation of the object.
     *
     * @return a string representation of the object
     */
    public String toString() {
        
        String endline = System.getProperty("line.separator");
        
        String theString =
        "network = " +  network + endline +
        "staName = " +  staName + endline;
        
        if (staPosition != null)
            theString += staPosition.toString();
        
        theString +=
        "instName = " +  instName + endline +
        "chanName = " +  chanName + endline +
        "locName = " +  locName + endline +
        "compName = " +  compName + endline +
        "azimuth = " +  azimuth + endline +
        "inclination = " +  inclination + endline;
        
        if (!auxChannelIdName.equals(UNDEF_STRING))
            theString += 		"auxChannelIdName = " +  auxChannelIdName + endline;
        
        
        return(theString);
        
    }
    
    
    /**  Compares this object to the specified object.
     *  The result is true if and only if the argument is not null and is a BasicChannel object that contains the same int value as this object.
     *
     * @return true if the objects are the same; false otherwise.
     *
     */
    public boolean equals(Object obj) {
        
        BasicChannel other = null;
        try {
            other = (BasicChannel) obj;
        } catch (Exception e) {
            return(false);
        }
        
        //System.out.println("BasicChannel.equals() this: " + this.toString() + "\n");
        //System.out.println("BasicChannel.equals() other: " + other.toString() + "\n");
        
        if (
        this.network.equals(other.network) &&
        this.staName.equals(other.staName) &&
        this.instName.equals(other.instName) &&
        this.chanName.equals(other.chanName) &&
        this.locName.equals(other.locName) &&
        this.auxChannelIdName.equals(other.auxChannelIdName) &&
        this.compName.equals(other.compName) &&
        this.azimuth == other.azimuth &&
        this.inclination == other.inclination
        // add later?
        //&& this.staPosition.equals(other.staPosition)
        )
            return(true);
        
        return(false);
    }
    
    
}	// end class BasicChannel


