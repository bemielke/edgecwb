/*
 * AJLGregorianCalendar.java
 *
 * Created on 10 August 2004, 09:32
 */

package net.alomax.util;

import java.text.*;
import java.util.*;
import java.io.Serializable;


/**
 *
 * @author  Anthony Lomax
 */

/* NOTE: this class is needed to override java.util.GregorianCalendar because before j2se 1.4 the method getTimeInMillis() was protected. */

public class AJLGregorianCalendar extends java.util.GregorianCalendar implements Serializable {
    
    /**
     * Constructs a GregorianCalendar based on GMT time
     * with the default locale.
     * @param zone the given time zone.
     */
    public AJLGregorianCalendar() {
        this(new SimpleTimeZone(0,"UTC"));
    }
    
    
    /**
     * Constructs a GregorianCalendar based on the current time
     * in the given time zone with the default locale.
     * @param zone the given time zone.
     */
    public AJLGregorianCalendar(TimeZone zone) {
        super(zone);
    }
    
    
    /**
     * Gets this Calendar's current time as a long.
     * @return the current time as UTC milliseconds from the epoch.
     * @see #getTime
     * @see #setTimeInMillis
     */
    public long getTimeInMillis() {
        return super.getTimeInMillis();
    }
    
    /**
     * Sets this Calendar's current time from the given long value.
     * @param millis the new time in UTC milliseconds from the epoch.
     * @see #setTime
     * @see #getTimeInMillis
     */
    public void setTimeInMillis(long millis) {
        super.setTimeInMillis(millis);
    }
    
    
    /** returns a string representation using YYYY.MM.DD HH:MM:SS */
    
    public String toStringTechnical() {
        
        
        return(
        net.alomax.util.NumberFormat.intString(get(Calendar.YEAR), 4)
        + "." + net.alomax.util.NumberFormat.intString((get(Calendar.MONTH) + 1), 2)
        + "." + net.alomax.util.NumberFormat.intString(get(Calendar.DAY_OF_MONTH), 2)
        + " " + net.alomax.util.NumberFormat.intString(get(Calendar.HOUR_OF_DAY), 2)
        + ":" + net.alomax.util.NumberFormat.intString(get(Calendar.MINUTE), 2)
        + ":" + net.alomax.util.NumberFormat.intString(get(Calendar.SECOND), 2)
        + " " + getTimeZone().getID()
        );
        
        
    }
    
    
    /** Convert to delimited string */
    
    public String toDelimitedString(String delimiter) {
        
        StringBuffer sbuf = new StringBuffer();
        
        sbuf.append(net.alomax.util.NumberFormat.intString(get(Calendar.YEAR), 4)).append(delimiter);
        sbuf.append(net.alomax.util.NumberFormat.intString((get(Calendar.MONTH) + 1), 2)).append(delimiter);
        sbuf.append(net.alomax.util.NumberFormat.intString(get(Calendar.DAY_OF_MONTH), 2)).append(delimiter);
        sbuf.append(net.alomax.util.NumberFormat.intString(get(Calendar.HOUR_OF_DAY), 2)).append(delimiter);
        sbuf.append(net.alomax.util.NumberFormat.intString(get(Calendar.MINUTE), 2)).append(delimiter);
        sbuf.append(net.alomax.util.NumberFormat.intString(get(Calendar.SECOND), 2)).append(delimiter);
        
        return(sbuf.toString());
        
    }
    
    
    
    
}








