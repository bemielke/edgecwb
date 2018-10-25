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


package net.alomax.util;


import java.util.*;
import java.io.Serializable;


/** Time instant object */

public class TimeInstant extends Date2 implements Serializable {
    
    public final static double SEC_PER_MIN = 60.0;
    public final static double SEC_PER_HOUR = SEC_PER_MIN * 60.0;
    public final static double SEC_PER_DAY = SEC_PER_HOUR * 24.0;
    
    protected int ihour, imin;
    protected double sec;
    
    
    
    /** Returns a TimeInstant from a delimited String containing year, month and day of month and time */
    
    public static TimeInstant create(String timeString, String separator) throws Exception {
        
        String[] tokens = StringExt.parse(timeString, separator);
        
        int iyear = Integer.parseInt(tokens[0]);
        int imonth = Integer.parseInt(tokens[1]);
        int idate = Integer.parseInt(tokens[2]);
        int ihour = Integer.parseInt(tokens[3]);
        int imin = Integer.parseInt(tokens[4]);
        double sec = Double.parseDouble(tokens[5]);
        
        return(new TimeInstant(iyear, imonth, idate, ihour, imin, sec));
        
    }
    
    
    
    /** Returns a TimeInstant from a delimited String containing year, month and day of month and time */
    
    public static TimeInstant create(String timeString, String dateSeparator, String dateTimeSeparator, String timeSeparator) throws Exception {
        
        String[] tokens = StringExt.parse(timeString, dateTimeSeparator);
        
        String[] tokens0 = StringExt.parse(tokens[0], dateSeparator);
        int iyear = Integer.parseInt(tokens0[0]);
        int imonth = Integer.parseInt(tokens0[1]);
        int idate = Integer.parseInt(tokens0[2]);
        
        String[] tokens1 = StringExt.parse(tokens[1], timeSeparator);
        int ihour = Integer.parseInt(tokens1[0]);
        int imin = Integer.parseInt(tokens1[1]);
        double sec = Double.parseDouble(tokens1[2]);
        
        return(new TimeInstant(iyear, imonth, idate, ihour, imin, sec));
        
    }
    
    
    
    /** Copy constructor */
    public TimeInstant(TimeInstant ti) {
        super(ti.year, ti.dayOfYear);
        this.ihour = ti.ihour;
        this.imin = ti.imin;
        this.sec = ti.sec;
    }
    
    
    /** Constructor with year, month and day of month and time */
    public TimeInstant(int iyear, int imonth, int idate, int ihour, int imin, double sec) {
        super(iyear, imonth, idate);
        this.ihour = ihour;
        this.imin = imin;
        this.sec = sec;
    }
    
    
    /** Constructor with year, month and day of month */
    public TimeInstant(int iyear, int imonth, int iday) {
        this(iyear, imonth, iday, 0, 0, 0.0);
    }
    
    
    /** Constructor with year and day of year */
    public TimeInstant(int iyear, int ijday, int ihour, int imin, double sec) {
        super(iyear, ijday);
        this.ihour = ihour;
        this.imin = imin;
        this.sec = sec;
    }
    
    
    /** Constructor with year, day of year and locale */
    public TimeInstant(int iyear, int ijday, String localeName,
    int ihour, int imin, double sec) {
        super(iyear, ijday, localeName);
        this.ihour = ihour;
        this.imin = imin;
        this.sec = sec;
    }
    
    
    /** Constructor with milliseconds since January 1, 1970, 00:00:00 LOCAL TIME */
    public TimeInstant(double milliseconds) throws Exception {
        this(milliseconds, null);
    }
    
    
    /** Constructor with milliseconds since January 1, 1970, 00:00:00 LOCAL TIME
     *    and locale */
    public TimeInstant(double milliseconds, String localeName) throws Exception {
        
        super.setLocale(localeName);
        
        long longTime = (long) milliseconds;
        double remainder = milliseconds - (double) longTime;
        // check if was before 1970, negative millisec time
        // AJL 20040522
        if (remainder < 0.0) {
            remainder += 1.0;
            longTime -= 1;
        }
        
        // create date2 (will have time set to midnight)
        Date2 date2 = Date2.create(longTime);
        
        // get time in seconds from midnight
        double tsec = (remainder + (double) (longTime - date2.getTime())) / 1000.0;
        
        if (tsec < 0.0 || tsec > SEC_PER_DAY) {
            String message = this.getClass().getName() + ": " +
            "ERROR: Invalid seconds offset from 00h:" +
            date2.getYear() + "/" + date2.getMonth() + "/" + date2.getDate()
            + ", offset: " + tsec + ", milliseconds: " + milliseconds;
            throw (new Exception(message));
                        /*
                         System.out.println(message);
                        this.ihour = 0;
                        this.imin = 0;
                        this.sec = 0;
                        super.year = 0;
                        super.month = 0;
                        super.date = 0;
                        super.dayOfYear = 0;
                        return;
                         */
        }
        
        // get sec, min, hour
        int jmin = 0;
        int jhour = 0;
        while (tsec >= 60.0) {
            tsec -= 60.0;
            jmin += 1;
        }
        if (tsec == -0.0)		// prevent -0.0
            tsec = 0.0;
        while (tsec < 0.0) {	// prevent negative values
            tsec += 60.0;
            jmin -= 1;
        }
        while (jmin >= 60) {
            jmin -= 60;
            jhour += 1;
        }
        while (jmin < 0) {
            jmin += 60;
            jhour -= 1;
        }
        
        //if (jhour >= 24)	// cannot happen unless millisecond roll-over
        //    System.out.println(this.getClass().getName() + ": " + "WARNING: Hour >= 24: " + jhour);
        
        this.ihour = jhour;
        this.imin = jmin;
        this.sec = tsec;
        
        super.year = date2.getYear();
        super.month = date2.getMonth() - 1;
        super.date = date2.getDate();
        super.dayOfYear = date2.getDoY();
    }
    
    
    
    /** Test equality with another TimeInstant */
    
    public boolean equals(TimeInstant other) {
        
        if (!super.equals((Date2) other))
            return(false);
        if (this.ihour != other.ihour)
            return(false);
        if (this.imin != other.imin)
            return(false);
        if (this.sec != other.sec)
            return(false);
        
        return(true);
        
    }
    
    
    
    /** Get hours */
    
    public int getHours() {
        return(ihour);
    }
    
    
    /** Get minutes */
    
    public int getMinutes() {
        return(imin);
    }
    
    
    /** Get seconds */
    
    public double getSeconds() {
        return(sec);
    }
    
    
    
    
    
    
    /** Returns a new TimeInstants equal to this TimeInstant + seconds */
    
    public TimeInstant add(double seconds) throws Exception {
        
        return(new TimeInstant(getMillisecondTime() + 1000.0 * seconds));
        
    }
    
    
    
    
    /** Returns a new TimeInstants equal to this TimeInstant - seconds */
    
    public TimeInstant subtract(double seconds) throws Exception {
        
        return(new TimeInstant(getMillisecondTime() - 1000.0 * seconds));
        
    }
    
    
    
    /** Calculate difference between two TimeInstants in seconds */
    
    public static double secondsDifference(TimeInstant ti1, TimeInstant ti2) {
        
        // get difference between times
        double diff = ti1.getMillisecondTime() - ti2.getMillisecondTime();
        diff /= 1000.0;	// getMillisecondTime is in milliseconds
        
        return(diff);
    }
    
    
    
    /** Returns the earlier of two TimeInstants */
    
    public static TimeInstant min(TimeInstant ti1, TimeInstant ti2) {
        
        if (ti1 == null)
            return(ti2);
        else if (ti2 == null)
            return(ti1);
        else if (secondsDifference(ti1, ti2) > 0.0)
            return(ti2);
        else
            return(ti1);
    }
    
    
    /** Returns the later of two TimeInstants */
    
    public static TimeInstant max(TimeInstant ti1, TimeInstant ti2) {
        
        if (ti1 == null)
            return(ti2);
        else if (ti2 == null)
            return(ti1);
        else if (secondsDifference(ti1, ti2) < 0.0)
            return(ti2);
        else
            return(ti1);
    }
    
    
    /** Returns the number of milliseconds since
     * January 1, 1970, 00:00:00 LOCAL TIME
     * represented by this TimeInstant
     * (double) version of Date.getTime()
     */
    
    public double getMillisecondTime() {
        
        // ms time of date
        long longTime = super.getTime();
        
        // include hours/min/seconds
        double doubleTime = (double) longTime + 1000.0 *
        (sec + 60.0 * ((double) imin + 60.0 * (double) ihour));
        
        return(doubleTime);
    }
    
    
    /** convert to string default */
    
    public String toString() {
        return(toString(Date2.YYYYMONTHDD, 3, true));
    }
    
    
    /** convert to string formatted */
    
    public String toString(int format, int ndec, boolean pretty) {
        
        if (pretty)
            return(new String(
            toString2(format) + "  "
            + NumberFormat.intString(ihour, 2) + "h"
            + NumberFormat.intString(imin, 2) + "m"
            + NumberFormat.floatString((float) sec, ndec + 3, ndec) + "s" ));
        else
            return(new String(
            toString2(format) + " "
            + NumberFormat.intString(ihour, 2)
            + NumberFormat.intString(imin, 2) + " "
            + NumberFormat.floatString((float) sec, ndec + 3, ndec)));
        
    }
    
    
    /** convert to string formatted */
    
    public String toStringSeparated(String dateSeparator, String dateTimeSeparator, String timeSeparator, int ndec) {
        
        String secStr = NumberFormat.floatString((float) sec, ndec > 0 ? ndec + 3 : 3, ndec);
        if (ndec <= 0)
            secStr = secStr.substring(0, secStr.indexOf('.'));
        
        return(new String(
        super.toDelimitedString(dateSeparator)
        + dateTimeSeparator
        + NumberFormat.intString(ihour, 2) + timeSeparator
        + NumberFormat.intString(imin, 2) + timeSeparator
        + secStr
        ));
        
    }
    
    
    /** convert to string formatted, with time before date */
    
    public String toStringInvert(int format, int ndec, boolean pretty) {
        
        if (pretty)
            return(new String(
            NumberFormat.intString(ihour, 2) + "h"
            + NumberFormat.intString(imin, 2) + "m"
            + NumberFormat.floatString((float) sec,
            ndec + 3, ndec) + "s"
            + "  " + toString2(format)
            ));
        else
            return(new String(
            NumberFormat.intString(ihour, 2)
            + NumberFormat.intString(imin, 2) + " "
            + NumberFormat.floatString(
            (float) sec, ndec + 3, ndec)
            + " " + toString2(format)
            ));
        
    }
    
    
    /** convert to string formatted, with date only */
    
    public String toStringDateOnly(int format, int ndec, boolean pretty) {
        
        return(new String(toString2(format)));
        
    }
    
    
    /** Convert date to delimited string */
    
    public String toDelimitedString(String delimiter) {
        
        return(toDelimitedString(delimiter, null));
        
    }
    
    
    /** Convert date to delimited string */
    
    public String toDelimitedString(String delimiter, java.text.NumberFormat numberFormat) {
        
        StringBuffer sbuf = new StringBuffer();
        
        sbuf.append(super.toDelimitedString(delimiter)).append(delimiter);
        sbuf.append(NumberFormat.intString(ihour, 2)).append(delimiter);
        sbuf.append(NumberFormat.intString(imin, 2)).append(delimiter);
        if (numberFormat != null)
            sbuf.append(numberFormat.format(this.sec));
        else
            sbuf.append((float) this.sec);
        
        return(sbuf.toString());
        
    }
    
    
    
    
    /** Convert date to delimited string with integer seconds */
    
    public String toDelimitedStringIntSec(String delimiter) {
        
        StringBuffer sbuf = new StringBuffer();
        
        sbuf.append(super.toDelimitedString(delimiter)).append(delimiter);
        sbuf.append(NumberFormat.intString(ihour, 2)).append(delimiter);
        sbuf.append(NumberFormat.intString(imin, 2)).append(delimiter);
        sbuf.append(NumberFormat.intString((int) sec, 2));
        
        return(sbuf.toString());
        
    }
    
    
    
    
    /** returns the time zone ID */
    
    public String getTimeZoneID() {
        
        return(calendar.getTimeZone().getID());
        
    }
    
    
}	// end class TimeInstant

