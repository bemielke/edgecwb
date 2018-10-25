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


/* From Class java.util.Date in JavaTM Platform 1.1.5 Core API Specification

 In all methods of class Date that accept or return year, month, date, hours, minutes, and seconds values, the following
 representations are used:

 A year y is represented by the integer y - 1900.
 A month is represented by an integer form 0 to 11; 0 is January, 1 is February, and so forth; thus 11 is December.
 A date (day of month) is represented by an integer from 1 to 31 in the usual manner.
 An hour is represented by an integer from 0 to 23. Thus, the hour from midnight to 1 a.m. is hour 0, and the hour from
 noon to 1 p.m. is hour 12.
 A minute is represented by an integer from 0 to 59 in the usual manner.
 A second is represented by an integer from 0 to 60; the value 60 occurs only for leap seconds and even then only in
 Java implementations that actually track leap seconds correctly.

 In all cases, arguments given to methods for these purposes need not fall within the indicated ranges; for example, a date may
 be specified as January 32 and is interpreted as meaning February 1.

 */

/* NOTE: for Date2:

 A year includes the century
 A month is represented internally by an integer from 0 to 11; 0 is January, 1 is February, and so forth; thus 11 is December.
 A month is passed or recieved by an integer from 1 to 12; 1 is January, 2 is February, and so forth; thus 12 is December.
 A date (day of month) is represented by an integer from 1 to 31 in the usual manner.

 */
/**
 * A custom Date-like class with century and day of year
 */
public class Date2 implements Serializable {

    private static String className;

    protected static SimpleTimeZone timezone = new SimpleTimeZone(0, "GMT");
    protected static net.alomax.util.AJLGregorianCalendar calendar = new net.alomax.util.AJLGregorianCalendar(timezone);

    public static final int YYYYMONTHDD = 0;
    public static final int YYYYMONTHDD_DOY = 1;
    public static final int YYYYMMDD = 2;
    public static final int YYYYMMDD_NO_SPACES = 3;

    static String monthName[][] = {
        {"JAN", "FEB", "MAR", "APR", "MAY", "JUN",
            "JUL", "AUG", "SEP", "OCT", "NOV", "DEC", "???"},
        {"JAN", "FEV", "MAR", "AVR", "MAI", "JUI",
            "JUL", "AOU", "SEP", "OCT", "NOV", "DEC", "???"},
        {"JAN", "FEB", "MAR", "APR", "MAY", "JUN",
            "JUL", "AUG", "SEP", "OCT", "NOV", "DEC", "???"}
    };

    static int monthNumDays[][] = {
        {31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31, 0},
        {31, 29, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31, 0}
    };

    static final int en_US = 0;
    static final int fr_FR = 1;
    static final int it_IT = 2;
    private static int defaultLocale = en_US;
    protected int locale = defaultLocale;

    protected int year = -1;
    protected int month = -1;
    protected int date = -1;
    protected int dayOfYear = -1;

    /**
     * Constructor
     */
    public Date2() {
        this(1970, 0, 1);
    }

    /**
     * Constructor with day of year
     */
    public Date2(int year, int doy) {

        className = this.getClass().getName() + ": ";

        this.dayOfYear = doy;

        // 20140130 AJL - removed these corrections, they may give errors when displaying very large time windows
        // try to catch and correct year errors
        // set year 0 - 20 to 2000 - 2020
        if (year <= 20) {
            System.out.println(className + "WARNING: Apparent invalid year in date: " + year
            // 20140130 AJL + ": adding 2000 to year: " + (year + 2000)
            );
            // 20140130 AJL year += 2000;
        }
        // set year 20 - 200 to 1920 - 2100
        if (year < 200) {
            System.out.println(className + "WARNING: Apparent invalid year in date: " + year
            // 20140130 AJL + ": adding 1900 to year: " + (year + 1900)
            );
            // 20140130 AJL year += 1900;
        }

        int iLeap = 0;
        if (year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)) {
            iLeap = 1;
        }

        int iMonth = 0;
        int iSumTest = 0;
        int iDayToBegMonth = 0;
        while (iMonth < 12
                && (iSumTest += monthNumDays[iLeap][iMonth]) < doy) {
            iMonth++;
            iDayToBegMonth = iSumTest;
        }

        if (iMonth == 12) {
            System.out.println(className
                    + "WARNING: Invalid day of year in date: " + doy);
        }

        this.year = year;
        this.month = iMonth;
        this.date = doy - iDayToBegMonth;
    }

    /**
     * Constructor with day of year and local
     */
    public Date2(int yr, int doy, String localeName) {

        this(yr, doy);

        setLocale(localeName);

    }

    /**
     * Constructor from year/month/day date
     */
    public Date2(int year, int month, int date) {

        className = this.getClass().getName() + ": ";

        this.year = year;
        this.month = month - 1;
        this.date = date;

        int iLeap = 0;
        if (year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)) {
            iLeap = 1;
        }

        int iMonth = 0;
        int doy = 0;
        while (iMonth < this.month && iMonth < 13) {
            doy += monthNumDays[iLeap][iMonth];
            iMonth++;
        }

        this.dayOfYear = doy + this.date;
    }

    /**
     * Constructor from year/month/day date and locale
     */
    Date2(int year, int month, int date, String localeName) {

        this(year, month, date);

        setLocale(localeName);

    }

    /**
     * Constructor with milliseconds since January 1, 1970, 00:00:00 GMT
     */
    public static Date2 create(long longTime) {

        calendar.clear();
        calendar.setTimeInMillis(longTime);
        // set time to midnight
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        return new Date2(calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH) + 1, calendar.get(Calendar.DAY_OF_MONTH));

        /*
         Date dateTmp = new Date(longTime - timezoneOffset());
         //Date dateTmp = new Date(longTime);

         // zero out time
         dateTmp.setHours(0);
         dateTmp.setMinutes(0);
         dateTmp.setSeconds(0);
         return new Date2(dateTmp.getYear() + 1900, dateTmp.getMonth() + 1, dateTmp.getDate());
         */
    }

    /**
     * converts String month to integer month
     */
    public static int str2intMonth(String chrMonth) {

        for (int month = 0; month < monthName[defaultLocale].length; month++) {
            if (monthName[defaultLocale][month].equalsIgnoreCase(chrMonth)) {
                return (month + 1);
            }
        }
        return (0);

    }

    /**
     * Test equality with another Date2
     */
    public boolean equals(Date2 other) {

        if (this.date != other.date) {
            return (false);
        }
        if (this.month != other.month) {
            return (false);
        }
        if (this.year != other.year) {
            return (false);
        }

        return (true);

    }

    /**
     * sets the locale
     */
    public static void setDefaultLocale(String localeName) {

        if (localeName == null) {
            return;
        }

        if (localeName.toLowerCase().startsWith("fr")) {
            defaultLocale = fr_FR;
        } else if (localeName.equalsIgnoreCase("it_IT")) {
            defaultLocale = it_IT;
        } else {
            defaultLocale = en_US;
        }

    }

    /**
     * sets the locale
     */
    public void setLocale(String localeName) {

        if (localeName == null) {
            locale = defaultLocale;
            return;
        }

        if (localeName.equalsIgnoreCase("fr_FR")) {
            locale = fr_FR;
        } else if (localeName.equalsIgnoreCase("it_IT")) {
            locale = it_IT;
        }

    }

    /**
     * gets the timezone offset
     */
    /*

     public static long timezoneOffset() {
     System.out.println(
     "Date: " + (new Date(70, 0, 1, 0, 0, 0)).getTime()
     + " Date.UTC: " + Date.UTC(70, 0, 1, 0, 0, 0)
     + " timezoneOffset: " + ((new Date(70, 0, 1, 0, 0, 0)).getTime() - Date.UTC(70, 0, 1, 0, 0, 0))
     );
     return((new Date(70, 0, 1, 0, 0, 0)).getTime() - Date.UTC(70, 0, 1, 0, 0, 0));
     }
     */
    /**
     * Returns the number of milliseconds since January 1, 1970, 00:00:00 GMT represented by this TimeInstant
     */
    public long getTime() {

        // ms time of date
        //long longTime = (new Date(year - 1900, month, date, 0, 0, 0)).getTime() + timezoneOffset();
        //long longTime1 = Date.UTC(year - 1900, month, date, 0, 0, 0);
        calendar.clear();
        calendar.set(year, month, date, 0, 0, 0);
        long longTime = calendar.getTimeInMillis();

        //System.out.println("longTime old: " + longTime1 + " longTime new: " + longTime);
        return (longTime);
    }

    /**
     * Returns a Date object corresponding to the calendar time of this TimeInstant
     */
    public Date getDateObject() {

        calendar.clear();
        calendar.set(year, month, date, 0, 0, 0);
        Date date = calendar.getTime();

        //System.out.println("longTime old: " + longTime1 + " longTime new: " + longTime);
        return (date);
    }

    /**
     * Get year
     */
    public int getYear() {
        return (year);
    }

    /**
     * Get day of year
     */
    public int getDoY() {
        return (dayOfYear);
    }

    /**
     * Get month
     */
    public int getMonth() {
        return (month + 1);
    }

    /**
     * Get date
     */
    public int getDate() {
        return (date);
    }

    /**
     * Convert month to character
     */
    public String getChrMonth() {

        return (monthName[locale][month]);

    }

    /**
     * Convert date to string
     */
    public String toString2(int format) {

        String chrMonth, chrDoY = "";
        if (format == Date2.YYYYMONTHDD) {
            chrMonth = getChrMonth();
        } else if (format == Date2.YYYYMONTHDD_DOY) {
            chrMonth = getChrMonth();
            chrDoY = " (" + NumberFormat.intString(getDoY(), 3) + ")";
        } else if (format == Date2.YYYYMMDD
                || format == Date2.YYYYMMDD_NO_SPACES) {
            chrMonth = NumberFormat.intString(getMonth(), 2);
        } else {
            chrMonth = "???";
        }

        if (format == Date2.YYYYMMDD_NO_SPACES) {
            return (new String(NumberFormat.intString(getYear(), 4)
                    + chrMonth
                    + NumberFormat.intString(getDate(), 2)));
        } else {
            return (new String(
                    NumberFormat.intString(getYear(), 4) + " "
                    + chrMonth + " "
                    + NumberFormat.intString(getDate(), 2)
                    + chrDoY));
        }

    }

    /**
     * Convert date to delimited string
     */
    public String toDelimitedString(String delimiter) {

        StringBuffer sbuf = new StringBuffer();

        sbuf.append(NumberFormat.intString(this.year, 4)).append(delimiter);
        sbuf.append(NumberFormat.intString(this.month + 1, 2)).append(delimiter);
        sbuf.append(NumberFormat.intString(this.date, 2));

        return (sbuf.toString());

    }

}	// end class Date2

