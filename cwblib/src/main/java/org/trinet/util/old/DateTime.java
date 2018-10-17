package org.trinet.util.old;

import java.text.*;
import java.util.*;
//import org.trinet.jdbc.datatypes.DateStringFormatter;
//
// Refactored logic in code for UTC seconds -aww 2008/02
//
/**
 * Handle Epoch date/time in a reasonable fashion
 * Extends the Date class.
 * Added functions:
    1) will return seconds as a double with a fractional part,
    2) provides a simple toString method
    3) provides a julian second constructor
    4) provides constructors like those that were deprecated
    5) allows setting of toString format using SimpleDateFormat pattern syntax
 */

/*  JAVA notes:
    The Java epoch start time is : Jan. 1, 1970, 00:00 GMT
    The Date class is based on UTC (GMT) and doesn't deal with time zones.<p>

    In v1.1 Sun created a Calendar class to aid "internationalization" and
    deal with time zones. They deprecated the Date constructors that used
    calendar time because which assumed everything was GMT and didn't handle
    time zone conversions. The Calendar class and the use of "locales"
    complicated things alot and solved problems we didn't really care about.<p>

    NOTE: THE PRECISION OF THE JAVA DATE AND CALENDAR CLASSES ARE LIMITED MILLISECS<p>

    Month integers follow DATE: January=0, February=2...
*/
/* Deprecation Note:

    Deprecation warnings should be ignored.
    This class defines methods that have the same
    names as methods deprecated in Date. This generates the warnings.
*/

public class DateTime extends Date implements org.trinet.jdbc.datatypes.DateStringFormatter {

    //private GregorianCalendar calendar = new GregorianCalendar(TimeZone.getTimeZone("GMT")); // all internal work in GMT
    private GregorianCalendar calendar = new GregorianCalendar(TimeZone.getTimeZone("UTC")); // all internal work in GMT

    /** The default format string. NOTE: the "S" format returns milliseconds
     * as an integer. This is prone to unexpected formatting results.
     * For example, if the seconds part of a time = 12.03456. the "ss.SS" format returns "12.34" and
     * "ss.SSSS" returns "12.0034". Only "ss.SSS" gives a correct result. */
    //static String fmtString = "MMMM dd, yyyy HH:mm:ss.SSS";
    private static String fmtString = EpochTime.DEFAULT_FORMAT;

    /** The difference between the fractional part of seconds as stored in the millis
    precision of the Calendar and the actual value. It is added to the epoch to achieve
    the desired precision. A double can only have 16 significant digits, thus contemporary
    epoch seconds can only specify to microsecond precision. To allow for last digit round off
    error we store to nearest integral 10^-6.
    */
    private double extraFracSecs = 0.; // 0 means the precision is only to milliseconds

    private int leapSecs = 0;
/*

IMPORTANT: if you use a string with two decimal places in sec the parser screws up!
Current systemTime: October 21, 1999 22:39:55.379
Parse test of string: October 21, 1999 22:39:55.379
Parse result is     : October 21, 1999 22:39:55.379 <-- good parse
Current systemTime: October 21, 1999 22:39:55.37
Parse test of string: October 21, 1999 22:39:55.37
Parse result is     : October 21, 1999 22:39:55.03  <-- bad parse
*/

    /**
     * Create DateTime object with current date epoch time.
     */
    public DateTime() {
        super();
        calendar.setTime(this);     // keep the calendar object current
    }

    /**
     * Create DateTime object with input DateTime.
     */
    public DateTime(DateTime date) {
        super();
        setTime(date);
    }

    /**
     * Create DateTime object with input date nominal epoch time.
     */
    public DateTime(Date date) {
        super(date.getTime());
        calendar.setTime(this);  // must keep the calendar object current -aww 04/01/2009
        setTime(this);
    }

    /**
     * Create DateTime object with input epoch seconds in the time base specified (nominal or true UTC).
     */
    public DateTime(double secs, boolean isUTC) {
        super();
        if (isUTC) setTrueSeconds(secs);
        else setNominalSeconds(secs);
    }

    /**
     * Create DateTime object with the time description. Time is assumed to be GMT. Year is
     * 4 digits. Precision is 0.001 s. Month number follows Java convention where January=0.
     * Range overflows do not cause exceptions (i.e. day 32 of January would be February 1),
     * for correct initialization an input second value should only be set >= 60
     * if it is an offical leap second.
     */
    public DateTime(int year, int month, int day, int hr, int mn, double sec) {
        super();
        setTime(year, month, day, hr, mn, sec);
    }

    /**
     * Parse a string using the specified time format and return a DateTime object.
     * Sets the instance default date String format to the specified format.
     */
    public DateTime(String dateStr, String formatString) {
        super();
        if (formatString != null) this.fmtString = formatString; 
        setTime(dateStr, this.fmtString);
    }

    /**
     * Parse a string in standard date/time format and return a DateTime object.
     * Standard format is "yyyy-MM-dd HH:mm:ss.SSSS" in GMT timezone.
     * Example: "1999-01-23 22:10:25.000". 
     */
    public DateTime(String dateStr) {
        this(dateStr, null);
    }

    /** Return the fractional part of the input second.  */
    public static double fracOf(double sec) {
       return sec - Math.floor(sec);
    }

    /** Trim the trailing places of a double to microsecs 10^-6. This is useful
    * when precision jitter in double arithmetic causes extraneous digits.
    * For example: 0.01 * 301 = 3.0100000002 */
    public static double trimToMicros(double val) {
        return (double)Math.round(val*1.0E06)/1.0E06;
    }

    /** Returns 'true' id the two doubles are "equal" down to the 6th decimal place
    (microseconds). The decimal place is positive number
    Calculated values of epoch seconds will not always be exactly equal numerically
    because precision jitter in double arithmetic causes extraneous digits.
    For example: 0.01 * 301 = 3.0100000002
    */
    public static boolean areEqual(double d1, double d2) {
        return areEqual(d1, d2, 6);
    }

    /** Returns 'true' id the two doubles are "equal" within the to the nth decimal place.
    The decimal place is positive number
    Calculated values of epoch seconds will not always be exactly equal numerically
    because precision jitter in double arithmetic causes extraneous digits.
    For example: 0.01 * 301 = 3.0100000002
    */
    public static boolean areEqual(double d1, double d2, int decimalPlace) {
        double diff = Math.abs(d1 - d2);
        return (diff < Math.pow(10, -(decimalPlace+1)));
    }

    public boolean areEqual(Date date, int decimalPlace) {
        if (date instanceof DateTime)  
            return areEqual(((DateTime)date).getTrueSeconds(), getTrueSeconds(), decimalPlace);

        if (leapSecs != 0) return false; // Date does have leap seconds

        // otherwise check nominal times
        return areEqual((double)date.getTime()/1000., getNominalSeconds(), decimalPlace);
    }

    public double getExtraFracSeconds() {
        return extraFracSecs;
    }

    private void setExtraFracSeconds(double sec) {
        // the round is necessary because of twos-compliment precision issues
        //extraFracSecs = (int)Math.round((sec - Math.floor(sec))*1.0E05) - calendar.get(Calendar.MILLISECOND)*100; // 100Kth
        extraFracSecs = trimToMicros(sec - Math.floor(sec) - calendar.get(Calendar.MILLISECOND)/1000.); // 1 micro
    }

    /* Range overflows do not cause exceptions (i.e. day 32 of January would be February 1),
     * for correct initialization an input second value should only be set >= 60
     * if it is an offical leap second.
     */
    public void setTime(int year, int month, int day, int hr, int mn, double sec) {

        int isec = (int) sec;
        if (isec >= 60) leapSecs = (int) (isec - 59); 

        // use calendar object to "parse" date/time info
        calendar.set(year, month, day, hr, mn, isec-leapSecs);

        // get milliseconds part of the seconds. You must set this seperately because there is no set()
        // command in the Calendar class with millisecond precision. If you don't set it you "inherit"
        // the milliseconds that were stuffed in at the time it was instantiated.
        //calendar.set(Calendar.MILLISECOND, (int) Math.round((sec - (double)isec) * 1000.)); // round only if not setExtraFracSeconds(sec)
        calendar.set(Calendar.MILLISECOND, (int)((sec - (double)isec)*1000.));

        // this weirdness is because you can't get epoch milliseconds directly from a Calender
        // object. Calendar.getTime() returns a Date, then Date.getTime() returns millis
        super.setTime(calendar.getTimeInMillis());

        // keep additional precision beyond milliseconds
        setExtraFracSeconds(sec);
    }

    /**
     * Set time as nominal seconds past UNIX epoch.
     */
    public void setNominalSeconds(double nominalSecs) {
        //super.setTime(Math.round(nominalSecs * 1000.)); // millisec since epoch (use round if not extraFracSecs below)
        super.setTime((long)Math.floor(nominalSecs * 1000.)); // millisec since epoch (use floor when setting extraFracSecs)
        calendar.setTime(this); // keep the calendar object current

        // keep additional precision beyond milliseconds
        setExtraFracSeconds(nominalSecs);
    }

    public void setTrueSeconds(double trueSecs) {
        leapSecs = LeapSeconds.secondsLeptAt(trueSecs);
        setNominalSeconds(LeapSeconds.trueToNominal(trueSecs));
    }

    /** True time values includes leap seconds. */
    public void setTrueMillis(long trueMillisecs) {
        setTrueSeconds((double)trueMillisecs/1000.);
    }

    /**
     * Override of parent class method.
    /**
     * Override of parent class method.
     * Also sets calendar time using nominal milliseconds past epoch Jan 1, 1970,
     * NOTE: no true time values here, do not include leap seconds.
     */
    public void setTime(long millis) {
        super.setTime (millis);  // millisec since epoch
        calendar.setTime(this);  // keep the calendar object current
        extraFracSecs = 0.;
    }

    /**
     * Set nominal epoch time using the input Date object.
     */
    public void setTime(Date date) {
        if (date instanceof DateTime) setTime((DateTime)date);
        else setTime(date.getTime()); // millisec since epoch
    }

    /**
     * Set time using input DateTime object's member values.
     */
    public void setTime(DateTime dt) {
        this.leapSecs = dt.leapSecs;
        this.extraFracSecs = dt.extraFracSecs;
        super.setTime(dt.getTime()); // added as fix -aww 2009/04/01
        this.calendar.setTimeInMillis(dt.calendar.getTimeInMillis());
    }

    /**
     * Set time using input string parsed values and the default format.
     */
    public void setTime(String dateStr) {
        // Default used to be:  "MMMM dd, yyyy HH:mm:ss.SSS"
        // Default is now:  "yyyy-MM-dd HH:mm:ss.SSSS" in GMT timezone.
        setTime(dateStr, this.fmtString);
    }

    public void setTime(String dateStr, String fmtString) {
        setTrueSeconds(LeapSeconds.stringToTrue(dateStr, fmtString));
    }

    /**
     * Set the default date/time format used by class's String i/o methods for a
     * SimpleDateFormat pattern string.
     * Default is:  "yyyy-MM-dd HH:mm:ss.SSSS" in GMT timezone.
     * @see: java.text.SimpleDateFormat
     */
    public void setFormat(String formatString) {
        fmtString = formatString;
    }

    /**
     * Return DateTime formatted as specified in pattern string native GMT
     */
    public String toDateString(String pattern) {
        return LeapSeconds.trueToString(getTrueSeconds(), pattern);
    }

    /** Return this time as long value of true epoch milliseconds */ 
    public long getTrueMillis() {
       return Math.round(getTrueSeconds() * 1000.);
    }

    /** Return this date calendar time as nominal epoch seconds */ 
    public double getNominalSeconds() {
       return (double) calendar.getTimeInMillis()/1000.0 + (double)extraFracSecs;
    }

    /** Return this date calendar time as true epoch seconds */ 
    public double getTrueSeconds() { // method added 2008/02/05 -aww
       return LeapSeconds.nominalToTrue(getNominalSeconds()) +  leapSecs; 
    }

    public int getYear() {
        return calendar.get(Calendar.YEAR);
    }
    /** Julian Day */
    public int getDayOfYear() {
        return calendar.get(Calendar.DAY_OF_YEAR);
    }
    public int getMonth() {
        // January = 0
        return calendar.get(Calendar.MONTH);
    }
    public int getDay() {
        return calendar.get(Calendar.DAY_OF_MONTH);
    }
    public int getHour() {
        // 0 -> 23
        return calendar.get(Calendar.HOUR_OF_DAY);
    }
    public int getMinute() {
        return calendar.get(Calendar.MINUTE);
    }
    public int getSecond() {
        return calendar.get(Calendar.SECOND) + leapSecs;
    }

    public int getSecondMillis() {
        return calendar.get(Calendar.MILLISECOND);
    }

    /** Includes fractional part to micro-second accuracy. */
    public double getDoubleSecond() {
        return (double) calendar.get(Calendar.SECOND) +
               (double) calendar.get(Calendar.MILLISECOND)/1000. + extraFracSecs +
               (double) leapSecs;
    }

    /** Return a string for the seconds with the given precision. for example: if seconds
     * equals 2.01450 then getSecondsStringToPre(0) = "02" and
     * getSecondsStringToPre(3) = "02.015"*/
    public String getSecondsStringToPrecisionOf(int decimalPlaces) {
        int pre = 3;
        if (decimalPlaces < 1) pre = 2;
        // show leading zeros
        String fmtStr= "%0"+(pre+decimalPlaces)+"."+decimalPlaces+"f";
        Format fmt = new Format(fmtStr);
        return fmt.form(this.getDoubleSecond());
    }

    /** Return a string for the fractional part of seconds with the given precision.
     * For example: if seconds equals 2.01450 then getSecondsStringToPre(0) = "",
     * getSecondsStringToPre(2) = "02" and getSecondsStringToPre(3) = "015"*/
    public String getSecondsFractionStringToPrecisionOf(int decimalPlaces) {
        if (decimalPlaces < 1) return "";
        int num = decimalPlaces+3;
        // build a format string like "%5.2f"
        String fmtStr= "%"+num+"."+decimalPlaces+"f"; //
        Format fmt = new Format(fmtStr);
        String str = fmt.form(this.getDoubleSecond());
        return str.substring(3);  // trim off the leading whole seconds and "."
    }

    // below switched to UTC from nominal -aww 2008/02/12
    /** Return the true UTC seconds of the START of the year of this time. */
    public double getStartOfYear() {
       return (new DateTime(getYear(), 0, 0, 0, 0, 0.0)).getTrueSeconds();
    }
    /** Return the true UTC seconds of the START of the month of this time. */
    public double getStartOfMonth() {
       return (new DateTime(getYear(), getMonth(), 0, 0, 0, 0.0)).getTrueSeconds();
    }
    /** Return the true UTC seconds of the START of the day of this time. */
    public double getStartOfDay() {
       return (new DateTime(getYear(), getMonth(), getDay(), 0, 0, 0.0)).getTrueSeconds();
    }
    /** Return the true UTC seconds of the START of the hour of this time. */
    public double getStartOfHour() {
       return (new DateTime(getYear(), getMonth(), getDay(), getHour(), 0, 0.0)).getTrueSeconds();
    }
    /** Return the true UTC seconds of the START of the minute of this time. */
    public double getStartOfMinute() {
       return (new DateTime(getYear(), getMonth(), getDay(), getHour(), getMinute(), 0.0)).getTrueSeconds();
    }

    public boolean after(Date when) {
      return (compareTo(when) > 0);
    }

    public boolean before(Date when) {
      return (compareTo(when) < 0);
    }

    public int compareTo(Date anotherDate) {
        int ii = super.compareTo(anotherDate);
        if (ii != 0) return ii;
        if (anotherDate instanceof DateTime) {
            DateTime dt = (DateTime) anotherDate;
            double diff = (getDoubleSecond() - dt.getDoubleSecond()); 
            if (diff == 0) ii = 0; // added as bugfix 2013/01/19 -aww
            else {
                ii = (diff < 0) ? -1 : 1;
            }
        }
        return ii;
    }

    public boolean equals(Object obj) {
        boolean status = super.equals(obj);
        if (! status) return false;
        if (obj instanceof DateTime) {
            DateTime dt = (DateTime) obj;
            status = (Math.abs(this.extraFracSecs - dt.extraFracSecs) <= 5.E-07 && this.leapSecs == dt.leapSecs);
        }
        else status = false;
        return status;
    }

    /** Note STATIC method override returns UTC true millisecs, not nominal. */
    public static long parse(String str) {
        return Math.round(LeapSeconds.stringToTrue(str) * 1000.);
    }

    public String toLocaleString() {
        return DateFormat.getDateTimeInstance().format(this);
    }

    /**
     * Return DateTime as string as native GMT
     */
    public String toString() {
        return toDateString(fmtString);
    }

    public String toUTCString() {
        return toString();
    }

    public Object clone() {
        DateTime dt = (DateTime) super.clone();
        dt.calendar = (GregorianCalendar) this.calendar.clone(); 
        return dt;
    }
/*
  static public final class Tester {
 // Interpret command-line arg as epoch seconds and reports in default format.
 // If no arg reports current time.
    public static void main(String args[]) {

        // Today
        DateTime dtime = new DateTime();    // sets dtime to current time
        System.out.println("Today = "+ dtime.getYear() +"/"+ (dtime.getMonth()+1) +"/"+ dtime.getDay() +" "+
                dtime.getHour() +":"+ dtime.getMinute() +":"+ dtime.getSecondsStringToPrecisionOf(3));

        // Use input, any second arg implies true (leap) seconds as 1st arg value
        if (args.length > 0) { // translate epoch second on command-line value
          double sec = Double.parseDouble(args[0]);     // convert arg String to 'double'
          if (args.length > 1) dtime.setTrueSeconds(sec);
          else  dtime.setNominalSeconds(sec);
          System.out.println("Input seconds: " + sec + " isTrue: " + (args.length>1) +
                  " dsecs: " + dtime.getDoubleSecond());
        }

        System.out.println ("Nominal seconds = " + dtime.getNominalSeconds() +
                            " true seconds = " + dtime.getTrueSeconds() +
                            " dsecs: " + dtime.getDoubleSecond());
        System.out.println ( dtime.toString() );

        dtime = new DateTime(dtime.toString());
        System.out.println ( "String parse test: " + dtime.toString());

        //
        //Leap Span
        //
        System.out.println("Test spanning of a known leap interval ....");
        System.out.println("867715219 UTC secs = 1997/06/30 23:59:59 next second is LEAP");

        dtime = new DateTime(1997,0,181,23,59,59.9900000127);
        System.out.println("1997 181 23:59:59.9900000127 dt: " + dtime.toString() + " dsecs: " + dtime.getDoubleSecond());

        dtime = new DateTime(1997, 5,30,23,59,60.0000000127);
        System.out.println("1997 June 30 23:59:60.0000000127 dt: " + dtime.toString() + " dsecs: " + dtime.getDoubleSecond());

        dtime = new DateTime(867715219.98999999, true); //  1997/06/30 23:59:59.98999999
        System.out.println("867715219.98999999 dt: " + dtime.toString() + " leap: " + dtime.getTrueSeconds() +
                " dsecs: " + dtime.getDoubleSecond());
        System.out.println (" 59. Precision test ");
        for (int i=0; i<7; i++) {
          System.out.println (i + "  "+ dtime.getSecondsStringToPrecisionOf(i));
        }

        dtime = new DateTime(867715220.98999999, true); //  1997/06/30 23:59:60.98999999
        System.out.println("867715220.98999999 dt: " + dtime.toString() + " leap: " + dtime.getTrueSeconds() +
                " dsecs: " + dtime.getDoubleSecond());
        System.out.println (" 60. Precision test ");
        for (int i=0; i<7; i++) {
          System.out.println (i + "  "+ dtime.getSecondsStringToPrecisionOf(i));
        }

        dtime = new DateTime(867715221.98999999, true); //  1997/07/01 00:00:00.98999999
        System.out.println("867715221.98999999 dt: " + dtime.toString() + " leap: " + dtime.getTrueSeconds() +
                " dsecs: " + dtime.getDoubleSecond());
        System.out.println ("867715221.98999999 Precision test ");
        for (int i=0; i<7; i++) {
          System.out.println (i + "  "+ dtime.getSecondsStringToPrecisionOf(i));
        }

        dtime = new DateTime(867715221.1514168, true); //  1997/07/01 00:00:00.98999999
        System.out.println("867715221.1514168 dt: " + dtime.toString() + " leap: " + dtime.getTrueSeconds() +
                " dsecs: " + dtime.getDoubleSecond());
        System.out.println ("867715221.1514168 Precision test ");
        for (int i=0; i<7; i++) {
          System.out.println (i + "  "+ dtime.getSecondsStringToPrecisionOf(i));
        }
      //
      // Past year 1900
      //
        double epoch = -2208891354.98999999;
        System.out.println("epoch = 1900/01/02 03:04:05.01000001");

        SimpleDateFormat df = new SimpleDateFormat();
        Date date = new java.util.Date((long) Math.round(epoch*1000.));
        df.setTimeZone(TimeZone.getTimeZone("GMT"));

        DateTime etime = new DateTime(epoch, true);
        System.out.println (" Epoch test: "+ etime.getNominalSeconds() + " -> "+
                               etime.toString() + " sec= "+etime.getDoubleSecond());

        etime.setNominalSeconds(-2208891354.98999999);
        System.out.println (" Epoch test: "+ etime.getNominalSeconds() + " -> "+
                               etime.toString() + " sec= "+etime.getDoubleSecond() );

        etime.setTime(1900, 0, 2, 3, 4, 5.01000001);
        System.out.println (" Epoch test: "+ etime.getNominalSeconds() + " -> "+
                               etime.toString() + " sec= "+etime.getDoubleSecond());

        etime.setTime((long) (-2208891354l*1000.0));
        System.out.println (" Epoch test: "+ etime.getNominalSeconds() + " -> "+
                               etime.toString() + " sec= "+etime.getDoubleSecond());
      //
      // Future year 2127
      //
        System.out.println("EPOCH TEST VALUE IS: 4967896022.1234567");
        epoch = 4967896022.1234567;   // date 2127-06-05 19:07:02.1234567
        etime = new DateTime(epoch, false);
        System.out.println (" Epoch test: "+ etime.getNominalSeconds() + " -> "+
                               etime.toString() + " sec= "+etime.getDoubleSecond());

        etime.setNominalSeconds(4967896022.1234567);
        System.out.println (" Epoch test: "+ etime.getNominalSeconds() + " -> "+
                               etime.toString() + " sec= "+etime.getDoubleSecond() );

        etime.setTime(2127, 6-1, 5, 19, 7, 02.1234567);
        System.out.println (" Epoch test: "+ etime.getNominalSeconds() + " -> "+
                               etime.toString() + " sec= "+etime.getDoubleSecond());

        etime.setTime((long) (4967896022l * 1000.0));
        System.out.println (" Epoch test long ms, zero fraction: "+ etime.getNominalSeconds() + " -> "+
                               etime.toString() + " sec= "+etime.getDoubleSecond());

        etime.setTime((long) (0.0123 * 1000.0));
        System.out.println (" Epoch test 12. ms: "+ etime.getNominalSeconds() + " -> "+
                               etime.toString() + " sec= "+etime.getDoubleSecond());

        System.out.println (" Precision test 2.1234567");
        etime = new DateTime(2.1234567, false);
        for (int i=0; i<7; i++) {
          System.out.println (i + "  "+ etime.getSecondsStringToPrecisionOf(i));
        }

    } // end of main
  } // end of Tester
*/
} // end of DateTime
