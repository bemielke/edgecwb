package org.trinet.util.old;

import java.lang.*;
import java.lang.Math.*;
import java.sql.*;
import java.text.*;
import java.text.SimpleDateFormat;
import java.util.*;

// below needed for JDBC source plsql TRUETIME test
//import org.trinet.jasi.DataSource;
//import org.trinet.jasi.TestDataSource;

// AWW - 4/2000 cleaned up the methods and added a few for Date to string etc.
// AWW - Added Sarala's DateConversion exception trapping to this code plus

// default to GMT if no UTC timezone.

/**
 * Allow simple handling of epoch times. Epoch times are elapse seconds since
 * epoch start. Our epoch start time is Jan. 1, 1970 at 00:00. This class
 * provides static methods for conversion from strings to double and back.
 * Conversion methods are provided to convert between the epoch nominal time
 * used internal to the Java classes and true "leap" seconds time.
<p>
<strong>KNOWN "BUG" IN SimpleDateFormat:</strong><p>

NOTE: the "S" format of SimpleDateFormat returns milliseconds
as an integer. Sun maintains that this is correct and that the "bug" is a misinterpretation
of the class's behavior. If you expect the "S" format symbol to behave like "s" or "m"
you will get unexpected formatting results. It behaves more like "Y".
For example, if the seconds part of a time = 12.03456. the "ss.SS" format returns "12.34"
which is the rightmost 2 digits of the integer millisecs!
"ss.SSSS" returns "12.0034", millisecs left-paddes with zeros!
Therefore, only "ss.SSS" gives a correct result.

<strong>THERFORE</strong> this class supports a format symbol ("f") for fractional seconds.
<pre>
Example:
"MMMM dd, yyyy HH:mm:ss.SSS"   =>  February 07, 2003 02:51:37.031
"MMMM dd, yyyy HH:mm:ss.SS"    =>  February 07, 2003 02:51:37.31 <Error

New symbol:
"MMMM dd, yyyy HH:mm:ss.ff"    =>  February 07, 2003 02:51:37.03

<strong>WARNING:</strong> the "f" format is not supported as a parsing format.

 </pre>
 *
 * <p>
 * <strong>Time Format Syntax:</strong>
 * <p>
 * To specify the time format use a <em>time pattern</em> string.
 * In this pattern, all ASCII letters are reserved as pattern letters,
 * which are defined as the following:
 * <blockquote>
 * <pre>
 * Symbol   Meaning                 Presentation        Example
 * ------   -------                 ------------        -------
 * G        era designator          (Text)              AD
 * y        year                    (Number)            1996
 * M        month in year           (Text & Number)     July & 07
 * d        day in month            (Number)            10
 * h        hour in am/pm (1~12)    (Number)            12
 * H        hour in day (0~23)      (Number)            0
 * m        minute in hour          (Number)            30
 * s        second in minute        (Number)            55
 * S        millisecond             (Number)            978
 *<strong>
 * f        fractional seconds      (Number)            012</strong>
 * E        day in week             (Text)              Tuesday
 * D        day in year             (Number)            189
 * F        day of week in month    (Number)            2 (2nd Wed in July)
 * w        week in year            (Number)            27
 * W        week in month           (Number)            2
 * a        am/pm marker            (Text)              PM
 * k        hour in day (1~24)      (Number)            24
 * K        hour in am/pm (0~11)    (Number)            0
 * z        time zone               (Text)              Pacific Standard Time
 * '        escape for text         (Delimiter)
 * ''       single quote            (Literal)           '
 * </pre>
 * </blockquote>

 */

public class EpochTime {


  // "yyyy-MM-dd HH:mm:ss.ff"
  public static final String YY_NO_ZONE_FORMAT   =  "yy-MM-dd HH:mm:ss.SSS";
  public static final String YYYY_NO_ZONE_FORMAT =  "yyyy-MM-dd HH:mm:ss.SSS";

  public static final String DEFAULT_FORMAT      =  YYYY_NO_ZONE_FORMAT;

  /** Default millisecs to add to current UTC to get the current local time; includes Daylight savings correction.
   * */
    public static long LOCAL_TZ_OFFSET = 0l;
    private static final Calendar gc = GregorianCalendar.getInstance();
    static {
        long millis = 0l;
        if (gc.isSet(Calendar.ZONE_OFFSET)) millis = gc.get(Calendar.ZONE_OFFSET);
        if (gc.isSet(Calendar.DST_OFFSET)) millis += gc.get(Calendar.DST_OFFSET);
        LOCAL_TZ_OFFSET = millis;
    }

  /** Returns a SimpleDataFormat with the specified Date string pattern for UTC time zone.
   */
  public static SimpleDateFormat getDateFormat(String pattern) {
    // "UTC" was causing a filenotfound exception deep in the java/sun calls to Locale DDG 10/7/04
    //return getDateFormat(pattern, "GMT");
    return getDateFormat(pattern, "UTC");
  }

  /** Returns a SimpleDataFormat with the specified Date string pattern for specified time zone.
   */
  public static SimpleDateFormat getDateFormat(String pattern, String zone) {
    TimeZone timeZone = TimeZone.getTimeZone(zone);
    //if (timeZone == null) timeZone = TimeZone.getTimeZone("GMT");
    if (timeZone == null) timeZone = TimeZone.getTimeZone("UTC");
    SimpleDateFormat df = new SimpleDateFormat(pattern);
    df.setTimeZone(timeZone);
    df.setLenient(true);
    return df;
  }
  /** Returns a date String formatted using the default pattern for UTC Time zone.
   * The default pattern is: yyyy-MM-dd HH:mm:ss.SSS */
  public static String dateToString(java.util.Date date) {
    return dateToString(date, DEFAULT_FORMAT);
  }
  /** Returns a date String formatted using the specified pattern for UTC Time zone. */
  public static String dateToString(java.util.Date date, String pattern) {
    //return dateToString(date, pattern, "GMT");   // changed from :UTC: DDG 10/7/04
    return dateToString(date, pattern, "UTC");   // changed from :UTC: DDG 10/7/04
  }
  /** Returns a date String formatted using the specified pattern and time zone parameters. */
  public static String dateToString(java.util.Date date, String pattern, String zone) {
//  Split pattern before and after 'fff' string

    String cstr = null;   // return string
    String subStr1 = "", subStr2 = "", fstr = "";
    int knt = 0;

    if (date == null) return "null";
    if (pattern == null  || pattern.length() == 0) {
      pattern = DEFAULT_FORMAT;
    }
    // Example: "yyyy-MM-dd HH:mm:ss.ff z"
    //           012345678901234567890123456
    // will split into "yyyy-MM-dd HH:mm:ss." & " z"
    try {
      int pos = pattern.indexOf('f');    // test if this pattern uses 'f' format
      if (pos == -1) {    // no 'f' in pattern
        cstr = getDateFormat(pattern, zone).format(date);
      } else {
        // //////////////////////
        if (pos > 0) {
          String subPat1 = pattern.substring(0, pos);
          subStr1 = getDateFormat(subPat1, zone).format(date);
        }
        // count f's
        for (int i = pos; i < pattern.length(); i++) {
          if (pattern.charAt(i) != 'f') break;
          knt++;
        }
        String subPat2 = pattern.substring(pos+knt);
        subStr2 = getDateFormat(subPat2, zone).format(date);

        DateTime datetime = new DateTime(date);
        fstr = datetime.getSecondsFractionStringToPrecisionOf(knt);
        cstr = subStr1 + fstr + subStr2;
      }

      // /////////////////////
    }
    catch (IllegalArgumentException ie){
      System.err.println("EpochTime - dateToString : Requested Pattern is not available");
    }
    catch (NullPointerException ne){
      System.err.println("EpochTime - dateToString: NullPointerException converting the Date");
    }
    return cstr;
  }

/**
 * Returns dateToString(...) using the DEFAULT_FORMAT pattern, UTC time zone:
 * "yyyy-MM-dd HH:mm:ss.SSS"
 * @see #dateToString(java.util.Date, String)
 */
  public static String toString(java.util.Date date) {
    return dateToString(date, DEFAULT_FORMAT);
  }

  /**
   * Same as dateToString(...), returns the String date using specified pattern, UTC time zone.
   * @see #dateToString(java.util.Date, String)
   */
  public static String toString(java.util.Date date, String pattern) {
    return dateToString(date, pattern);
  }

  /**
   * Same as epochToString(...), returns the epoch time as a string for DEFAULT format:
   * "yyyy-MM-dd HH:mm:ss.SSS"
   */
  // STATIC METHOD CALLED BY ORACLE STORED PROCEDURE !
  public static String toString(double dateTime) {
    return epochToString(dateTime, DEFAULT_FORMAT);
  }
  /**
   * Same as dateToString(...), returns the epoch time using specified pattern, UTC time zone.
   * @see #dateToString(java.util.Date, String)
   */
  // STATIC METHOD CALLED BY ORACLE STORED PROCEDURE !
  public static String toString(double dateTime, String pattern) {
    return epochToString(dateTime, pattern);
  }

  public static String toNoZoneYYString(double dateTime) {
    return epochToString(dateTime, YY_NO_ZONE_FORMAT);
  }
  public static String toNoZoneYYYYString(double dateTime) {
    return epochToString(dateTime, YYYY_NO_ZONE_FORMAT);
  }

  /**
   * Returns the epoch time as a string for default format:
   */
  public static String epochToString(double dateTime) {
    return epochToString(dateTime, DEFAULT_FORMAT);
  }

  /**
   * Returns the epoch time as a string with a SimpleDateTime format given in
   * 'pattern' for UTC time zone. If 'pattern' is null or zero length string is
   * returned as the default pattern format is used. Sets time to floor of total milliseconds value.
   * @see java.text.SimpleDateFormat
   */
  public static String epochToString(double dateTime, String pattern ) {
    //return dateToString(new java.util.Date((long) Math.round(dateTime*1000.)), pattern );
    return dateToString(new java.util.Date((long) Math.floor(dateTime*1000.)), pattern ); // floor, for compatibility with setNominalSecs of DateTime -aww 2010/06/14
  }

  /**
   * Returns the epoch time as a string with a SimpleDateTime format given in
   * 'pattern' for specified input time zone. If 'pattern' is null or zero
   * length string default pattern format is used. Sets time to floor of total milliseconds value.
   * @see java.text.SimpleDateFormat.
   */
  // STATIC METHOD CALLED BY ORACLE STORED PROCEDURE !
  public static String epochToString(double dateTime, String pattern, String zone ) {
    //return dateToString(new java.util.Date((long) Math.round(dateTime*1000.)), pattern, zone );
    return dateToString(new java.util.Date((long) Math.floor(dateTime*1000.)), pattern, zone ); // floor, for compatibility with setNominalSecs of DateTime -aww 2010/06/14
  }

  /**
   * Returns the epoch time as a string with a SimpleDateTime format given in
   * 'pattern' for PST time zone. If 'pattern' is null or zero length string
   * the default pattern format is used.
   * @see java.text.SimpleDateFormat
   */
  public static String epochToPST(double dateTime, String pattern ) {
    return epochToString(dateTime, pattern, "PST");
  }

  /**
   * Returns the epoch time as a string with a SimpleDateTime format given in
   * default pattern for PST time zone. If 'pattern' is null or zero length string
   * is returned as "yyyy-MM-dd HH:mm:ss.SSS".
   * @see java.text.SimpleDateFormat
   */
  public static String epochToPST(double dateTime) {
    return epochToString(dateTime, DEFAULT_FORMAT, "PST");
  }

  /**
   * Parse a string of default format "yyyy-MM-dd HH:mm:ss.SSS" and return epoch seconds.
   */
  public static double stringToEpoch(String UTC) {
    return stringToEpoch(UTC, DEFAULT_FORMAT);
  }

  /**
   * Parse a string of format given by 'pattern' and return epoch seconds.
   */
  // STATIC METHOD CALLED BY ORACLE STORED PROCEDURE !
  public static double stringToEpoch(String UTC, String pattern) {
    java.util.Date myDate = stringToDate(toPosixString(UTC, pattern), pattern);
    return (myDate == null) ? 0.0 : (double) myDate.getTime()/1000.;
  }

  /**
   * Parse a string of format "yyyy-MM-dd HH:mm:ss.SSS" and return a Date.
   */
  public static java.util.Date stringToDate(String UTC) {
    return stringToDate(UTC, DEFAULT_FORMAT);
  }

  /**
   * Parse a string of format given by 'pattern' and return a Date.
   * NOTE: the 'f' format will not work here.
   * Returns null if the input UTC string is null or empty.
   */
  public static java.util.Date stringToDate(String UTC, String pattern) {
    if (UTC == null || UTC.length() == 0) return null;
    return getDateFormat(pattern).parse(toPosixString(UTC, pattern), new ParsePosition(0));
  }

  /** Return date String with leap seconds removed from string, i.e. 60 or 61 seconds becomes 59.*/
  public static final String toPosixString(String UTC, String pattern) { // added -aww 2008/01/24
    if (UTC == null || UTC.length() == 0) return UTC;
    int idx = pattern.indexOf("ss");
    StringBuffer posix = new StringBuffer(UTC);
    if (idx >= 0 && posix.length() >= idx+2) { // added second test for case of short input, e.g. no seconds -aww 2009/09/23
      if (posix.substring(idx,idx+2).equals("60") ||
          posix.substring(idx,idx+2).equals("61") ) posix.replace(idx,idx+2,"59");
    }
    return posix.toString();
  }

  /**
   * Convert an java.util.Date to epoch double.
   */
  public static double dateToEpoch(java.util.Date date) {
    return (double) date.getTime() / 1000. ;
  }

  /**
   * Convert an epoch time double to a java.util.Date, Sets time to floor of total milliseconds value.
   */
  public static java.util.Date epochToDate(double epochTime) {
    //return new java.util.Date(Math.round(epochTime * 1000.));
    return new java.util.Date((long)Math.floor(epochTime * 1000.)); // floor, for compatibility with setNominalSecs of DateTime -aww 2010/06/14
  }
  /** Return the current time in epoch seconds */
  public static double currentEpoch() {
    return ((double) System.currentTimeMillis())/1000.0;
  }
  /** Return absolute value of seconds between end and start times */
  public static double elapsedSeconds(java.sql.Timestamp tsStart, java.sql.Timestamp tsEnd) {
    double startMillis = (double) tsStart.getTime() + (double) tsStart.getNanos()/1000000.;
    double endMillis = (double) tsEnd.getTime() + (double) tsEnd.getNanos()/1000000.;
    return Math.abs(endMillis - startMillis)/1000.;
  }
  /** Return absolute value of seconds between end and start times */
  public static double elapsedSeconds(Calendar calendarStart, Calendar calendarEnd) {
    return Math.abs((double) calendarEnd.getTime().getTime() - (double) calendarStart.getTime().getTime())/1000.;
  }
  /** Return absolute value of seconds between end and start times */
  public static double elapsedSeconds(java.util.Date dateStart, java.util.Date dateEnd) {
    return Math.abs((double) dateEnd.getTime() - (double) dateStart.getTime())/1000.;
  }

  protected static final int SECS_PER_MIN  = 60;
  protected static final int MINS_PER_HOUR = 60;
  protected static final int HOURS_PER_DAY = 24;
  protected static final int SECS_PER_HOUR = SECS_PER_MIN * MINS_PER_HOUR;
  protected static final int SECS_PER_DAY  = SECS_PER_HOUR * HOURS_PER_DAY;

  /**
   * Return epoch time string with format like 0d 3h 25m 45s. This is used
   * primarily for formatting elapse time or time differences.
   */
  public static String elapsedTimeToText(Number isecs){
    return elapsedTimeToText(isecs.intValue());
  }
  /**
   * Return epoch time string with format like 0d 3h 25m 45s. This is used
   * primarily for formatting elapse time or time differences.
   */
  public static String elapsedTimeToText(double secs){
    return elapsedTimeToText((int) secs);
  }
  /**
   * Return epoch time string with format like 0d 3h 25m 45s. This is used
   * primarily for formatting elapse time or time differences.
   */
  public static String elapsedTimeToText(int interval){
    int days = interval/SECS_PER_DAY;
    interval -= days * SECS_PER_DAY;
    int hours = interval/SECS_PER_HOUR;
    interval -= hours * SECS_PER_HOUR;
    int minutes = interval/SECS_PER_MIN;
    int seconds = interval - minutes * SECS_PER_MIN;
    String retVal = days + "d " + hours + "h " + minutes + "m " + seconds + "s";
    return retVal;
  }

  /*
  // Test routine:
  public static void main(String args []) {
    System.out.println("Local tz offset hrs = " + LOCAL_TZ_OFFSET/3600000l);
    long millis =  System.currentTimeMillis();
     System.out.println(EpochTime.stringToDate("2010.02.26 00:00:00.000", "yyyy.MM.dd HH:mm:ss.SSS"));
     System.out.println(EpochTime.stringToDate("2010-02-26", "yyyy-MM-dd HH:mm:ss.SSS"));
     System.out.println(EpochTime.stringToDate("2010 02 26", "yyyy MM dd HH:mm:ss.SSS"));
//        System.out.println("Current systemTime: " + millis);
//        System.out.println("java.sql.Date.toString: \"" + new java.sql.Date(millis).toString() + "\"");
//        System.out.println("java.sql.Time.toString: \"" + new java.sql.Time(millis).toString() + "\"");
//        System.out.println("java.sql.Timestamp.toString: \"" + new java.sql.Timestamp(millis).toString() + "\"");
//        System.out.println("Current time dateToString(date,pattern) \"" + dateToString(new java.util.Date(millis), "yyyy-MM-dd") + "\"");
//
//        for (int i = 0;i<100;i++) {
//        millis =  System.currentTimeMillis();
//        java.util.Date date = new java.sql.Date(millis);
//        System.out.println(fdateToString(date, "MMMM dd, yyyy HH:mm:ss.ff z", "UTC"));
//        System.out.println(fdateToString(date, "MMMM dd, yyyy HH:mm:ss.f z", "UTC"));
//        System.out.println(fdateToString(date, "MMMM dd, yyyy HH:mm:ss.fff z", "UTC"));
//        System.out.println(fdateToString(date, "MMMM dd, yyyy HH:mm:ss.ffff z", "UTC"));
//        System.out.println(fdateToString(date, "MMMM dd, yyyy HH:mm:ss.fffff z", "UTC"));
//        System.out.println(dateToString(date, "MMMM dd, yyyy HH:mm:ss.SSS z", "UTC"));
//        }

    double secs = ((double) millis)/1000.0;
    String nowString = EpochTime.epochToString(secs);
    System.out.println("Current systemTime: " + nowString + " length: " + nowString.length());

    System.out.println("Date result is    : " + EpochTime.toString(EpochTime.stringToDate(nowString, DEFAULT_FORMAT)));
    nowString = EpochTime.epochToPST(secs);
    System.out.println("Current PST time: " + nowString + " length: " + nowString.length());
    nowString = EpochTime.toNoZoneYYString(secs);
    System.out.println("Current short time: " + nowString + " length: " + nowString.length());

// Test precision creep
    secs = 1044669017.0;

    for (int i = 0; i < 999; i++) {

      nowString = EpochTime.epochToString(secs, "MMMM dd, yyyy HH:mm:ss.ffff z");

      System.out.println(secs + "  "+nowString);
      secs += 0.001;
    }
    System.out.println("Elapsed time (12345): " + EpochTime.elapsedTimeToText(new Integer(12345)));
    System.out.println("MaxInteger time     : " + EpochTime.epochToString((double) Float.MAX_VALUE));

    nowString = "1981-01-01 00:00:00.000";
    System.out.println("Test time: " + nowString + " length: " + nowString.length());
    System.out.println("Date result is    : " + EpochTime.toString(EpochTime.stringToDate(nowString, DEFAULT_FORMAT)));

    // JDBC Db TRUETIME code test
    //DataSource ds = TestDataSource.create();
    //nominal = 1072102160.123;
    //leap = EpochTime.nominal2Leap(nominal);
    //System.out.println("\nmominal2Leap(double) test should have 22.0 leap seconds:");
    //System.out.println("nominal2Leap("+df.form(nominal)+") "+df.form(leap));
    //System.out.println("leap2Nominal("+df.form(leap)+") "+df.form(EpochTime.leap2Nominal(leap)));
    //nominal = 10.123;
    //leap = EpochTime.nominal2Leap(nominal);
    //System.out.println("\nRelative time seconds nominal=10.123 should be leap= 10.123");
    //System.out.println("nominal2Leap("+df.form(nominal)+") "+df.form(leap));
    //if (ds != null) ds.close();
    // end of leap secs db test
    //

    //
    System.out.println("\n*** Demonstrate ERROR in parsing of ss.ss format ***");

    // test custom formatting and parsing
    String pat = "MMMM dd, yyyy HH:mm:ss.SSS";
    nowString = epochToString(secs, pat);
    System.out.println("Current systemTime : " + nowString + " length: " + nowString.length());
    System.out.println("Date result        : " + EpochTime.stringToDate(nowString, pat).toString());

    double dt = 0.;
    // 'f' wildcard in format pattern croaks in Java 1.4
    // Exception in thread "main" java.lang.IllegalArgumentException:
    //     Illegal pattern character 'f'
    //
    //pat = "MMMM dd, yyyy HH:mm:ss.fff";
    //dt = EpochTime.stringToEpoch(nowString, pat);
    //System.out.println("Parse test of string: " + nowString);
    //System.out.println("Parse result is     : " + EpochTime.epochToString(dt, pat));
    //
    //
    // test custom formatting and parsing
    pat = "MMMM dd, yyyy HH:mm:ss.ff";
    System.out.println("Test Pattern "+pat);
    nowString = epochToString(secs, pat);
    System.out.println("Current systemTime: " + nowString);


    dt = 0.009;
    pat = EpochTime.DEFAULT_FORMAT;
    System.out.println("Test Pattern "+pat);
    System.out.println("Default format : " + EpochTime.epochToString(dt, pat));
    System.out.println("Default format : " + EpochTime.epochToString(dt));

    String teststr = EpochTime.epochToString(dt);
    java.util.Date testdate = EpochTime.stringToDate(teststr);
    System.out.println("Created  string : " + teststr);
    System.out.println("Reparsed format : " + EpochTime.dateToString(testdate));

    dt = 1234567.0567;
    pat = "MMMM dd, yyyy HH:mm:ss.SS";
    System.out.println("Test : "+pat+"  "+ epochToString(dt, pat) );

    pat = "MMMM dd, yyyy HH:mm:ss.SSS";
    System.out.println("Test : "+pat+"  "+ epochToString(dt, pat) );

    pat = "MMMM dd, yyyy HH:mm:ss.SSSS";
    System.out.println("Test : "+pat+"  "+ epochToString(dt, pat) );

    dt = 1234567.567;
    pat = "MMMM dd, yyyy HH:mm:ss.SS";
    System.out.println("Test : "+pat+"  "+ epochToString(dt, pat) );

    pat = "MMMM dd, yyyy HH:mm:ss.SSS";
    System.out.println("Test : "+pat+"  "+ epochToString(dt, pat) );

    pat = "MMMM dd, yyyy HH:mm:ss.SSSS";
    System.out.println("Test : "+pat+"  "+ epochToString(dt, pat) );

    pat = "MMMM dd, yyyy HH:mm:ss.ff";
    System.out.println("Test : "+pat+"  "+ epochToString(dt, pat) );

    pat = "MMMM dd, yyyy HH:mm:ss.fff";
    System.out.println("Test : "+pat+"  "+ epochToString(dt, pat) );

    pat = "MMMM dd, yyyy HH:mm:ss.ffff";
    System.out.println("Test : "+pat+"  "+ epochToString(dt, pat) );

  }
  */

  /*
  //
  // Below code requires a DataSource connection to a database
  // that has the NCEDC schema TRUETIME package installed
  //
  static CallableStatement csStmtToLeap    = null;
  static CallableStatement csStmtToNominal = null;

  public static double leap2Nominal(double time) {
    double newTime = Double.NaN;
    double wholeTime =  Math.floor(time);
    double frac = time - wholeTime;
    try {
      if (csStmtToNominal == null) {
        Connection conn = DataSource.getConnection();
        if (conn == null) throw new SQLException("Null DataSource connection");
        csStmtToNominal = conn.prepareCall("{ ? = call TRUETIME.TRUE2NOMINAL(?) }"); // time conversion
        csStmtToNominal.registerOutParameter(1, Types.NUMERIC);
      }
      // now bind statement input value
      csStmtToNominal.setDouble(2, wholeTime);
      boolean rs = csStmtToNominal.execute();
      newTime = csStmtToNominal.getDouble(1) + frac;
    }
    catch (SQLException ex) {
      ex.printStackTrace();
    }
    return newTime;
  }
  public static double nominal2Leap(double time) {
    double newTime = Double.NaN;
    double wholeTime =  Math.floor(time);
    double frac = time - wholeTime;
    try {
      if (csStmtToLeap == null) {
        Connection conn = DataSource.getConnection();
        if (conn == null) throw new SQLException("Null DataSource connection");
        csStmtToLeap = conn.prepareCall("{ ? = call TRUETIME.NOMINAL2TRUE(?) }"); // time conversion
        csStmtToLeap.registerOutParameter(1, Types.NUMERIC);
      }
      // now bind statement input value
      csStmtToLeap.setDouble(2, wholeTime);
      boolean rs = csStmtToLeap.execute();
      newTime = csStmtToLeap.getDouble(1) + frac;
    }
    catch (SQLException ex) {
      ex.printStackTrace();
    }
    return newTime;
  }
  // If jdbc statement source is used for leap time seconds
  public void finalize() {
    try {
      if (csStmtToLeap != null) csStmtToLeap.close(); // release resources
      if (csStmtToNominal != null) csStmtToNominal.close(); // release resources
    }
    catch(SQLException ex) { }
  }
*/
}
