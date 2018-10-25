/*
 * This file is part of the Anthony Lomax Java Library.
 *
 * Copyright (C) 2005 Anthony Lomax <anthony@alomax.net>
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
import net.alomax.io.*;
import net.alomax.seis.*;
import net.alomax.util.*;

import sdsu.io.ASCIIInputStream;

import java.io.*;
import java.net.*;
import java.util.*;


/** class for frequency domain GSE response format */


public class GSEPoleZeroResponse extends PoleZeroResponse {
    
    protected static final boolean DEBUG = false;
    
    protected static final int VERSION_UNKNOWN = -1;
    protected static final int VERSION_GSE_20 = 0;
    protected static final int VERSION_GSE_21 = 1;
    
    // gse version
    protected int gseVersion = -1;
    
    // CAL2
    // Sta a5 station code
    protected String sta = BasicItem.UNDEF_STRING;
    // 12-14 Chan a3 FDSN channel code
    protected String chan = BasicItem.UNDEF_STRING;
    //  Auxid a4 auxiliary identification code
    protected String auxid = BasicItem.UNDEF_STRING;
    //  Instype a6 instrument type (see Table 7).
    protected String instype = BasicItem.UNDEF_STRING;
    //  Calib* e10.2 system sensitivity (nm/count) at reference period (calper)
    protected double calib = -1.0;
    //  Calper* f7.3 calibration reference period (seconds)
    protected double calper = -1.0;
    //  Samprat* f11.5 system output sample rate (Hz)
    protected double samprat = -1.0;
    //  Ondate** i4,a1,i2,a1,i2 effective start date (yyyy/mm/dd)
    protected String ondate = BasicItem.UNDEF_STRING;
    //  Ontime** i2,a1,i2 effective start time (hh:mm)
    protected String ontime = BasicItem.UNDEF_STRING;
    //  Offdate** i4,a1,i2,a1,i2 effective end date (yyyy/mm/dd)
    protected String offdate = BasicItem.UNDEF_STRING;
    //  Offtime** i2,a1,i2 effective end time (hh:mm)
    protected String offtime = BasicItem.UNDEF_STRING;
    
    // PAZ2
    // Snum i2 stage sequence number
    protected int snum = -1;
    // Ounits* a1 output units code (V=volts, A=amps, C=counts)
    protected String ounits = BasicItem.UNDEF_STRING;
    // Sfactor** e15.8 scale factor
    protected double sfactor = -1.0;
    // Deci*** i4 decimation (blank if analog)
    protected int deci = -1;
    // Corr*** f8.3 group correction applied (seconds)
    protected double corr = -1.0;
    // Npole**** i3 number of poles
    protected int npole = -1;
    // Nzero i3 number of zeros
    protected int nzero = -1;
    // Descrip a25 description
    protected String descrip = BasicItem.UNDEF_STRING;
    
    
    
    /** empty constructor */
    
    public GSEPoleZeroResponse() {
        
    }
    
    
    
    /** constructor from data */
    
    public GSEPoleZeroResponse(double gain, Cmplx[] poles, Cmplx[] zeros, String shortName, String longName, int type)
    //throws ResponseException
    {
        
        this.gain = gain;
        this.type = type;
        
        if (poles != null) {
            this.poles = new Cmplx[poles.length];
            for (int i = 0; i < poles.length; i++)
                this.poles[i] = new Cmplx(poles[i]);
        }
        if (zeros != null) {
            this.zeros = new Cmplx[zeros.length];
            for (int i = 0; i < zeros.length; i++)
                this.zeros[i] = new Cmplx(zeros[i]);
        }
        if (shortName != null)
            this.shortName = shortName;
        if (longName != null)
            this.longName = longName;
        
        //System.out.println(this);
    }
    
    
    /** constructor from data */
    
    public GSEPoleZeroResponse(double gain, Cmplx[] poles, Cmplx[] zeros, String shortName, String longName, int type,
    String[] beforeUnits, String[] afterUnits, String beforeInst, String afterInst)
    //throws ResponseException
    {
        
        this(gain, poles, zeros, shortName, longName, type);
        setBeforeUnits(beforeUnits);
        setAfterUnits(afterUnits);
        setBeforeName(beforeInst);
        setAfterName(afterInst);
        
    }
    
    
    /** constructor from file */
    
    public GSEPoleZeroResponse(URL documentBase, String URLName, TimeInstant refTimeInstant,
    BasicChannel channel, TimeSeries timeSeries) throws ResponseException {
        
        try{
            
            // open input stream
            InputStream is = null;
            is = GeneralInputStream.openStream(documentBase, URLName, true);
            
            ASCIIInputStream ais = new ASCIIInputStream(new BufferedInputStream(is));
            read(ais, refTimeInstant, channel, timeSeries);
            ais.close();
            
            
            shortName = longName = URLName;
            
            // ?? normalization / gain - not clear what is correct!
            //
            // OPTION 1 --------------------
            // doc from GSETT-2 Assembled Data Set 05-001 sec "4.0 INSTRUMENT RESPONSE FORMAT"
            //    at IRIS (http://www.iris.edu/data/reports/2005/05-001_GSETT.pdf) says:
            /*
             By defining the various "calibration" values in units of nm/count at a specific period in the Center databases,
             the scaling of the response curves is explicitly defined. Thus, the responses stored in the external files need
             only preserve the true shape of the response curve, not the amplitude. The responses defined by poles and zeros,
             how- ever, do include a "normalization" factor in the format. It is included primarily to remain consistent with
             the response information as it is received at the Center. Although the Center will include these normalization
             features in the response files, we will not vouch for their appropriateness. We strongly recommend using the
             calibration and calibration period values to scale the response curve properly.
             ...
             To get the response of a particular instrument, the calibration and calibration period values must be known.
             The response shape curve defined in the external file is adjusted so that its displacement value is one at the
             calibration period. The calibration value can then be used to scale the curve to the appropriate value. If the
             displacement response is desired, this would be nm/count. Velocity or acceleration responses can also be
             obtained by multiply- ing the response curve by iw or -w2, respectively. The best estimate of the response at
             the time of the recording will be obtained using calib and calper in the wfdisc and sensor tables. The nominal
             response is found using ncalib and ncalper in the instrument table.
             */
            // implies following ??:
            //this.gain = this.sfactor / this.calib;  // ??
            //    but this doc also implies that the value sfactor (scale factor = normalization factor (?)) is not reliable!
            //    Thus must one "adjust the response shape curve defined in the external file so that its displacement
            //    value is one at the calibration period" and then muliply by calibration value ???
            // imples following ??
            // calculate scale factor following eq. 10.7-1 and 10.7-2 in Scherbaum, F. , Of Poles and Zeros Fundamentals of Digital Seismology
            // Series: Modern Approaches in Geophysics, Vol. 15
            gain = 1.0;
            Cmplx calibResponse = evaluateResponse(1.0 / this.calper);
            double calcGain = 1.0 / (calib * calibResponse.mag());
            double nominalGain = sfactor / calib;
            boolean test = Math.abs(nominalGain - calcGain) > (nominalGain + calcGain) / 2000.0;
            if (true || DEBUG || test) {
                if (test)
                    System.out.println("WARNING: GSEPoleZeroResponse: Nominal and Calculated scale factors differ:");
                else
                    System.out.println("INFO: GSEPoleZeroResponse: Nominal and Calculated scale factors:");
                System.out.println("  " + sta + " " + chan + " " +  auxid + " " + instype + " " + calib + " " + calper);
                System.out.println("  Nominal Ao=" + (sfactor / calib) + ", Calc Ao=" + calcGain);
            }
            
            gain = calcGain;
            //
            // OPTION 2 --------------------
            /*
                SEISAN doc  page 236 (www.geo.uib.no/seismo/SOFTWARE/SEISAN_8.0/seisan_8.0.pdf) says:
             In the simplest case, the response is given by the PAZ and a scaling factor. It is common (like in SEED) to have
             two scaling constants, one that normalizes the PAZ to amplitude 1 at a calibration period, and another constant
             that gives the amplitudes in the physical units. This is NOT the case with the GSE2 format. The GSE2 response
             for PAZ normally contains at least two parts, the CAL2 line and a PAZ2 line. The scaling factor should scale
             the PAZ to output/input units, NOT normalize. In the CAL2 line, the system sensitivity at a calibration period
             is given in units input/output, but is generally not needed.
             The total response is given by the PAZ, multiplied with the PAZ2 scaling factor, or the product of several stages.
             */
            // implies following ??:
            //this.gain = this.sfactor;  // ??
            /*
             This is how SEISAN reads the response, however, if it finds that the PAZ2 gives normalized values at the
             calibration period, the response is multiplied with the sensitivity given in the CAL2 line (this is done
             because such GSE files have been seen).
             */
            // implies following ??:
            //this.gain = this.sfactor / this.calib;  // ??
            //
            // ???
            
            
            
            
            
            if (this.snum == 1) {
                String[] beforeUnits = {PhysicalUnits.NANOMETERS};
                this.setBeforeUnits(beforeUnits);
            } else {
                String[] beforeUnits = {PhysicalUnits.COUNTS};   // not correct if previous stage does not output counts!
                this.setBeforeUnits(beforeUnits);
            }
            if (this.ounits.equalsIgnoreCase("V")) {
                String[] afterUnits = {PhysicalUnits.VOLTS};
                this.setAfterUnits(afterUnits);
            }
            else if (this.ounits.equalsIgnoreCase("A")) {
                String[] afterUnits = {PhysicalUnits.AMPS};
                this.setAfterUnits(afterUnits);
            }
            else if (this.ounits.equalsIgnoreCase("C")) {
                String[] afterUnits = {PhysicalUnits.COUNTS};
                this.setAfterUnits(afterUnits);
            }
            this.setBeforeName(GROUND_DISP_NAME);
            this.setAfterName(instype);
            this.setType(Response.OTHER_CONVERSION);
            
            
            if (timeSeries != null)
                this.dt = timeSeries.sampleInt;
            
            
        } catch (IOException ioe) {
            throw new ResponseException(ioe.getMessage());
        }
        
        shortName = longName = URLName;
        
        if (DEBUG) System.out.println(this);
        
        
        //System.out.println(this);
    }
    
    
    /**
     * reads a GSE2 PAZ2 pole-zero file.
     *
     *
     */
    
    protected void read(ASCIIInputStream ais, TimeInstant refTimeInstant, BasicChannel channel, TimeSeries timeSeries) throws ResponseException, IOException {
        
        gain = 1.0;		// default gain is 1.0
        
        boolean inCAL2Block = false;
        
        String word;
        String line;
        
        
        int nz = 0, np = 0;
        
        while ((line = ais.readLine()) != null) {
            
            StringTokenizer strTokenizer =
            new StringTokenizer(line);
            
            // empty lines
            if (!strTokenizer.hasMoreTokens())
                continue;
            
            word = strTokenizer.nextToken();
            if (DEBUG) System.out.println(word);
            
            // check for special keywords
            
            if (word.equalsIgnoreCase("DATA_TYPE")) {
                // DATA_TYPE RESPONSE GSE2.0
                word = strTokenizer.nextToken();
                if (!word.equalsIgnoreCase("RESPONSE"))
                    continue;
                word = strTokenizer.nextToken();
                if (word.equalsIgnoreCase("GSE2.0"))
                    gseVersion = VERSION_GSE_20;
                else if (word.equalsIgnoreCase("GSE2.1"))
                    gseVersion = VERSION_GSE_21;
                continue;
            } else if (word.equalsIgnoreCase("CAL2")) {
                if (inCAL2Block)    // at next CAL2 block
                    return;
                inCAL2Block = readLineCAL2(line, refTimeInstant, channel, timeSeries);
                continue;
            } else {
                if (!inCAL2Block)    // not yet found CAL2 block
                    continue;
            }
            
            // a comment line
            if (word.startsWith("*") || word.startsWith("#") || word.startsWith("("))
                continue;
            
            // check for keywords
            if (word.equalsIgnoreCase("STOP")) {
                return;
            }
            else if (word.equalsIgnoreCase("FAP2") || word.equalsIgnoreCase("GEN2") ||
            word.equalsIgnoreCase("DIG2") || word.equalsIgnoreCase("FIR2")) {
                throw(new ResponseException("unsupported GSE2 response section: " + word));
            }
            else if (word.equalsIgnoreCase("PAZ2")) {
                readPAZ2(line, ais);
                continue;
            }
            else {
                // invalid keyword
                throw(new ResponseException(FreqText.invalid_keyword + ": " + word));
            }
        }
        
        // response not found
        if (snum < 1)
            throw(new ResponseException(FreqText.transfer_function_not_found + ": "
            + channel.staName + " " + channel.chanName + " " + channel.compName + " " + channel.instName));
        
        
        return;
        
    }
    
    
    /** reads GSE CAL2 line
     *
     * returns true if channel not null and response for channel found, true otherwise with first response in file
     *
     */
    
    public boolean readLineCAL2(String line, TimeInstant refTimeInstant, BasicChannel channel, TimeSeries timeSeries) {
        
        
          /* read CAL2 line
                Position Name Format Description
                1-4 �CAL2� a4 must be �CAL2�
                6-10 Sta a5 station code
                12-14 Chan a3 FDSN channel code
                16-19 Auxid a4 auxiliary identification code
                21-26 Instype a6 instrument type (see Table 7).
                28-42 Calib* e10.2 system sensitivity (nm/count) at reference period (calper)
                44-50 Calper* f7.3 calibration reference period (seconds)
                52-62 Samprat* f11.5 system output sample rate (Hz)
                64-73 Ondate** i4,a1,i2,a1,i2 effective start date (yyyy/mm/dd)
                75-79 Ontime** i2,a1,i2 effective start time (hh:mm)
                81-90 Offdate** i4,a1,i2,a1,i2 effective end date (yyyy/mm/dd)
                92-96 Offtime** i2,a1,i2 effective end time (hh:mm)
           
VERSION_GSE_20
CAL2 MTLF  SHZ      ZM500    4.76e-02   1.000   50.00000 1996/07/02 00:00
CAL2 STU   HHZ      STS-2        1.63  6.2832      100.0 2000/01/01 00:00
CAL2 ARSA  BHE      sts2_v  1.6700000   0.000   20.00000 1997/05/30 00:00
VERSION_GSE_20
CAL2 ARSA  BHE      sts2_v  1.67000000e+00   0.000    20.00000 1997/05/30 00:00
           
           */
        
        if (DEBUG) System.out.println("CAL2");
        
        int verShift = 0;
        
        try {
            sta = line.substring(5, 10).trim();
            if (sta.length() < 1)
                sta = BasicItem.UNDEF_STRING;
        } catch (Exception e) {
            System.out.println("WARNING: GSEPoleZeroResponse: Parse error: sta: " + e + " in: " + line);
        }
        if (DEBUG) System.out.println(sta);
        
        try {
            chan = line.substring(11, 14).trim();
            if (chan.length() < 1)
                chan = BasicItem.UNDEF_STRING;
        } catch (Exception e) {
            System.out.println("WARNING: GSEPoleZeroResponse: Parse error: chan: " + e + " in: " + line);
        }
        if (DEBUG) System.out.println(chan);
        
        try {
            auxid = line.substring(15, 19).trim();
            if (auxid.length() < 1)
                auxid = BasicItem.UNDEF_STRING;
        } catch (Exception e) {
            System.out.println("WARNING: GSEPoleZeroResponse: Parse error: auxid: " + e + " in: " + line);
        }
        if (DEBUG) System.out.println(auxid);
        
        try {
            instype = line.substring(20, 26).trim();
            if (instype.length() < 1)
                instype = BasicItem.UNDEF_STRING;
        } catch (Exception e) {
            System.out.println("WARNING: GSEPoleZeroResponse: Parse error: instype: " + e + " in: " + line);
        }
        if (DEBUG) System.out.println(instype);
        
        if (gseVersion == VERSION_GSE_20)
            verShift = -5;
        
        String calibString = BasicItem.UNDEF_STRING;
        try {
            calibString = line.substring(27, 42 + verShift).trim();
            calib = Double.parseDouble(calibString);
        } catch (Exception e) {
            System.out.println("WARNING: GSEPoleZeroResponse: Parse error: calib: " + e + " in: " + line);
        }
        if (DEBUG) System.out.println(calib);
        
        // check for matching channel
        // NOTE!! - must agree with assignments in class net.alomax.seis.SeisDataGSE21 !!!
        if (channel != null) {
            if (DEBUG) System.out.println("sta channel.staName : <" + sta + "> <" + channel.staName);
            if (!sta.equals(BasicItem.UNDEF_STRING) && !channel.staName.equals(BasicItem.UNDEF_STRING)
            && !sta.equalsIgnoreCase(channel.staName))
                return(false);
            if (DEBUG) System.out.println("chan channel.chanName : <" + chan + "> <" + channel.chanName);
            if (!chan.equals(BasicItem.UNDEF_STRING) && !channel.chanName.equals(BasicItem.UNDEF_STRING)
            && !chan.equalsIgnoreCase(channel.chanName))
                return(false);
            if (DEBUG) System.out.println("auxid channel.compName : <" + auxid + "> <" + channel.compName);
            if (!auxid.equals(BasicItem.UNDEF_STRING) && !channel.compName.equals(BasicItem.UNDEF_STRING)
            && !auxid.equalsIgnoreCase(channel.compName))
                return(false);
            if (DEBUG) System.out.println("instype channel.instName : <" + instype + "> <" + channel.instName);
            if (!instype.equals(BasicItem.UNDEF_STRING) && !channel.instName.equals(BasicItem.UNDEF_STRING)
            && !instype.equalsIgnoreCase(channel.instName))
                return(false);
            // AJL 20070607 - Bug fix to allow correct grouping of GSE21 traces
            if (true || DEBUG) System.out.println("calibString channel.auxChannelIdName : <" + calibString + "> <" + channel.auxChannelIdName);
            if (!calibString.equals(BasicItem.UNDEF_STRING) && !channel.auxChannelIdName.equals(BasicItem.UNDEF_STRING)
            && !calibString.equalsIgnoreCase(channel.auxChannelIdName))
                return(false);
        }
        
        if (gseVersion == VERSION_GSE_20)
            verShift = -6;
        
        try {
            calper = Double.parseDouble(line.substring(43 + verShift, 50 + verShift).trim());
        } catch (Exception e) {
            System.out.println("WARNING: GSEPoleZeroResponse: Parse error: calper: " + e + " in: " + line);
        }
        if (DEBUG) System.out.println(calper);
        
        try {
            samprat = Double.parseDouble(line.substring(51 + verShift, 62 + verShift).trim());
        } catch (Exception e) {
            System.out.println("WARNING: GSEPoleZeroResponse: Parse error: samprat: " + e + " in: " + line);
        }
        if (DEBUG) System.out.println(samprat);
        
        
        // check for matching timeSeries
        // must agree with assignments in class net.alomax.seis.SeisDataGSE21 !!!
        if (timeSeries != null) {
            if (DEBUG) System.out.println("samprat timeSeries.sampleInt : <" + samprat + "> <" + (1.0 / timeSeries.sampleInt));
            if (!(Math.abs(samprat - 1.0 / timeSeries.sampleInt) < samprat / 1000.0))
                return(false);
        }
        
        try {
            ondate = line.substring(63 + verShift, 73 + verShift).trim();
        } catch (Exception e) {
            System.out.println("WARNING: GSEPoleZeroResponse: Parse error: ondate: " + e + " in: " + line);
        }
        if (DEBUG) System.out.println(ondate);
        
        try {
            ontime = line.substring(74 + verShift, 79 + verShift).trim();
        } catch (Exception e) {
            System.out.println("WARNING: GSEPoleZeroResponse: Parse error: ontime: " + e + " in: " + line);
        }
        if (DEBUG) System.out.println(ontime);
        
        
        // check on date/time
        if (refTimeInstant != null) {
            if (DEBUG) System.out.println("on date/time refTimeInstant : <" + ondate + "-" + ontime + "> <" + refTimeInstant);
            if (compareDateTime(refTimeInstant, ondate, ontime) < 0) {
                System.out.println("INFO: GSEPoleZeroResponse: on date/time INVALID: " +
                "on date/time refTimeInstant : <" + ondate + "-" + ontime + "> <" + refTimeInstant + "> " +
                " <" + sta + "> <" + chan + "> <" + auxid + "> <" + instype + ">");
                System.out.println("refTimeInstant.getMonth(): " + refTimeInstant.getYear());
                System.out.println("refTimeInstant.getMonth(): " + refTimeInstant.getMonth());
                System.out.println("refTimeInstant.getMonth(): " + refTimeInstant.getDate());
                return(false);
            }
            if (DEBUG) System.out.println("on date/time OK.");
        }
        
        
        // if the response is still valid, the off date-time should be left blank.
        if (line.length() < 90 + verShift)
            return(true);
        
        try {
            offdate = line.substring(80 + verShift, 90 + verShift).trim();
        } catch (Exception e) {
            System.out.println("WARNING: GSEPoleZeroResponse: Parse error: offdate: " + e + " in: " + line);
        }
        if (DEBUG) System.out.println(offdate);
        
        try {
            offtime = line.substring(91 + verShift, 96 + verShift).trim();
        } catch (Exception e) {
            System.out.println("WARNING: GSEPoleZeroResponse: Parse error: offtime: " + e + " in: " + line);
        }
        if (DEBUG) System.out.println(offtime);
        
        
        // check off date/time
        if (refTimeInstant != null) {
            if (DEBUG) System.out.println("off date/time refTimeInstant : <" + offdate + "-" + offtime + "> <" + refTimeInstant);
            if (compareDateTime(refTimeInstant, offdate, offtime) > 0) {
                System.out.println("INFO: GSEPoleZeroResponse: off date/time INVALID: " +
                "off date/time refTimeInstant : <" + offdate + "-" + offtime + "> <" + refTimeInstant + "> " +
                " <" + sta + "> <" + chan + "> <" + auxid + "> <" + instype + ">");
                System.out.println("off date/time INVALID: <" + sta + "> <" + chan + "> <" + auxid + "> <" + instype + ">");
                return(false);
            }
            if (DEBUG) System.out.println("off date/time OK.");
        }
        
        
        return(true);
        
    }
    
    
    
    /** compare a TimeInstant to a GSE format date and time
     *
     * @returns -1/1 if TimeInstant is earlier/later than GSE date/time, 0 otherwise
     *
     */
    
    public int compareDateTime(TimeInstant timeInstant, String gseDate, String gseTime) {
        
        // gseDate i4,a1,i2,a1,i2 effective start date (yyyy/mm/dd)
        // gseTime i2,a1,i2 effective start time (hh:mm)
        
        String[] dateComps = StringExt.parse(gseDate, "/");
        // check year
        int year = Integer.parseInt(dateComps[0]);
        if (timeInstant.getYear() < year)
            return(-1);
        if (timeInstant.getYear() > year)
            return(1);
        // check month
        int month = Integer.parseInt(dateComps[1]);
        if (timeInstant.getMonth() < month)
            return(-1);
        if (timeInstant.getMonth() > month)
            return(1);
        // check month
        int day = Integer.parseInt(dateComps[2]);
        if (timeInstant.getDate() < day)
            return(-1);
        if (timeInstant.getDate() > day)
            return(1);
        
        String[] timeComps = StringExt.parse(gseTime, ":");
        // check hour
        int hour = Integer.parseInt(timeComps[0]);
        if (timeInstant.getHours() < hour)
            return(-1);
        if (timeInstant.getHours() > hour)
            return(1);
        // check minute
        int minute = Integer.parseInt(timeComps[1]);
        if (timeInstant.getMinutes() < minute)
            return(-1);
        if (timeInstant.getMinutes() > minute)
            return(1);
        
        return(0);
        
    }
    
    
    
    /** reads GSE PAZ2 section */
    
    public void readPAZ2(String line, ASCIIInputStream ais) throws ResponseException {
        
        /*
            Header
            1-4 �PAZ2� a4 must be �PAZ2�
            6-7 Snum i2 stage sequence number
            9 Ounits* a1 output units code (V=volts, A=amps, C=counts)
            11-25 Sfactor** e15.8 scale factor
            27-30 Deci*** i4 decimation (blank if analog)
            32-39 Corr*** f8.3 group correction applied (seconds)
            41-43 Npole**** i3 number of poles
            45-47 Nzero i3 number of zeros
            49-73 Descrip a25 description
            Data
            2-16 Rroot e15.8 real part of pole or zero
            18-32 Iroot e15.8 imaginary part of pole or zero
         
         * Output units are V for volts, A for amps, and C for counts. Seismometers
            typically output volts or amps while an IIR filter would output counts.
            However, a simple response might give the seismometer with an output
            directly in counts implying that the digitizer response is included.
         ** The scale factor is in output units per input units. If this is the first
            (seismometer) section the input units are nm. Otherwise, the input units are
            the output units of the previous section.
         *** The decimation factor and group correction must be blank for an analogue
            filter and must be non-blank (zero for no decimation or no group correction)
            for a digital filter.
         ****For an analogue filter the poles and zeros specify the Laplace transform.
            For an IIR filter, they specify the Z-transform.
         
PAZ2  1 C  3.56980574e+09    0   -1.000   5   3
 -4.44000000e+00 -4.44000000e+00
 -4.44000000e+00  4.44000000e+00
 -1.25600000e+03  2.17600000e+03
 -1.25600000e+03 -2.17600000e+03
 -2.51200000e+03  0.00000000e+00
  0.00000000e+00  0.00000000e+00
  0.00000000e+00  0.00000000e+00
  0.00000000e+00  0.00000000e+00
         
         
VERSION_GSE_20
PAZ2 1  C 0.0976E+00                    2   3   Laplace transform
PAZ2  1 C  3.56980574e+09    0   -1.000   5   3
VERSION_GSE_21
PAZ2  1 V  2.38732415e+00                5   3
         
         */
        
        if (DEBUG) System.out.println("PAZ2");
        
        try {
            snum = Integer.parseInt(line.substring(5, 7).trim());
        } catch (Exception e) {
            System.out.println("WARNING: GSEPoleZeroResponse: Parse error: snum: " + e + " in: " + line);
        }
        if (DEBUG) System.out.println(snum);
        
        try {
            ounits = line.substring(8, 9).trim();
        } catch (Exception e) {
            System.out.println("WARNING: GSEPoleZeroResponse: Parse error: ounits: " + e + " in: " + line);
        }
        if (DEBUG) System.out.println(ounits);
        
        try {
            sfactor = Double.parseDouble(line.substring(10, 25).trim());
        } catch (Exception e) {
            System.out.println("WARNING: GSEPoleZeroResponse: Parse error: sfactor: " + e + " in: " + line);
        }
        if (DEBUG) System.out.println(sfactor);
        
        try {
            deci = Integer.parseInt(line.substring(26, 30).trim());
        } catch (Exception e) {
            System.out.println("WARNING: GSEPoleZeroResponse: Parse error: deci: " + e + " in: " + line);
        }
        if (DEBUG) System.out.println(deci);
        
        try {
            corr = Double.parseDouble(line.substring(31, 39).trim());
        } catch (Exception e) {
            System.out.println("WARNING: GSEPoleZeroResponse: Parse error: corr: " + e + " in: " + line);
        }
        if (DEBUG) System.out.println(corr);
        
        try {
            npole = Integer.parseInt(line.substring(40, 43).trim());
        } catch (Exception e) {
            System.out.println("WARNING: GSEPoleZeroResponse: Parse error: npole: " + e + " in: " + line);
        }
        if (DEBUG) System.out.println(npole);
        
        try {
            nzero = Integer.parseInt(line.substring(44, 47).trim());
        } catch (Exception e) {
            System.out.println("WARNING: GSEPoleZeroResponse: Parse error: nzero: " + e + " in: " + line);
        }
        if (DEBUG) System.out.println(nzero);
        
        try {
            descrip = line.substring(48, 73).trim();
        } catch (Exception ignored) {
            // description not required;
        }
        if (DEBUG) System.out.println(descrip);
        
        
        // read poles
        {
            int np = 0;
            try {
                poles = new Cmplx[npole];
                for (; np < npole; np++) {
                    double zr = ais.readDouble();
                    double zi = ais.readDouble();
                    poles[np] = new Cmplx(zr, zi);
                }
            } catch (IOException ioe) {
                throw(new ResponseException(FreqText.error_reading_pole + ": np= " + np + " / " + npole));
            }
        }
        
        // read zeros
        {
            int nz = 0;
            try {
                zeros = new Cmplx[nzero];
                for (; nz < nzero; nz++) {
                    double zr = ais.readDouble();
                    double zi = ais.readDouble();
                    zeros[nz] = new Cmplx(zr, zi);
                }
            } catch (IOException ioe) {
                throw(new ResponseException(FreqText.error_reading_zero + ": nz= " + nz + " / " + nzero));
            }
        }
        
        try {
            ais.flushLine();
        } catch (IOException ignored) {;}
        
    }
    
    /** returns the String value of this response */
    
    public String toString() {
        
        String str = super.toString();
        
        return(str);
    }
    
    
    
    
    // main to test class
    
    public static void main(String argv[]) {
        
        GSEPoleZeroResponse pzr = null;
        
        try {
            pzr = new GSEPoleZeroResponse(null, argv[0], null, null, null);
            if (DEBUG) System.out.println(pzr);
        } catch (Exception e) {
            System.out.println(e);
            e.printStackTrace();
            return;
        }
        
        try {
            PoleZeroResponse newPzr = (PoleZeroResponse) pzr.div(pzr);
            if (DEBUG) System.out.println(newPzr);
            Cmplx[] cmplxResp = newPzr.response(64, 0.1);
            if (DEBUG) {
                for (int i = 0; i < cmplxResp.length; i++)
                    System.out.println(cmplxResp[i]);
            }
        } catch (Exception e) {
            System.out.println(e);
            e.printStackTrace();
            return;
        }
        
        try {
            Cmplx[] cmplxResp = pzr.response(64, 0.1);
            if (DEBUG)  {
                for (int i = 0; i < cmplxResp.length; i++)
                    System.out.println(cmplxResp[i]);
            }
        } catch (Exception e) {
            System.out.println(e);
            e.printStackTrace();
            return;
        }
        
        
    }
    
    
}	// End class GSEPoleZeroResponse


