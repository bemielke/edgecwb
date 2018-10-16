/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.util;

import java.awt.Color;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.Connection;
import java.sql.Statement;
import java.sql.Time;
import java.util.ArrayList;
import java.util.NoSuchElementException;
import java.util.StringTokenizer;
import javax.swing.JComboBox;

/**
 *
 * All methods in this class are static so no instances of FUtil are allowed
 *
 * This is a helper class for fields represented as "JTextFields" that need to represent numbers,
 * dates, and other "verifiable" things. The textfield color will be changed to "UC.red" if it does
 * no parse or is out of range. Error information is kept in the "ErrorTrack" class object and allow
 * us to track the number of errors found and pass error messages for display on the form.
 *
 * Other helpers include the searchJComboBox() for aiding user typing in partial names to find
 *
 * @author D. Ketchum
 *
 */
public final class FUtil extends Object {

  /**
   * chkClass checks that the text in the JTextField is a valid fully qualified class
   *
   * @param t A JtextField with the class name
   * @param err The ErrorTrack object
   */
  public static void chkClass(javax.swing.JTextField t, ErrorTrack err) {
    String s = t.getText();
    if (s.equals("")) {
      return;
    }
    try {
      Class c = Class.forName(s);
    } catch (ClassNotFoundException e) {
      err.set();
      err.appendText("Class not found");
      t.setBackground(UC.red);
      return;
    } catch (LinkageError e) {
      err.set();
      err.appendText("Class linkage error");
      t.setBackground(UC.red);
      return;
    }
    t.setBackground(UC.white);
  }

  /**
   * chkTime validate are user entered logfile in a JTextField via the "ErrorTrack" system. If the
   * text is empty the earliest possible time is returned. User needs to enter time without
   * directory paths (no '/' allowed.
   *
   * @param t A JTextField containing the date
   * @param err The ErrorTrack field
   * @return The value of logfile decoded from the text
   */
  public static java.sql.Time chkTime(javax.swing.JTextField t, ErrorTrack err) {
    boolean dbg = false;
    String s = t.getText();
    java.sql.Time d = new java.sql.Time(0L);
    if (s.equals("")) {
      err.set(false);
      return d;
    }
    try {
      StringTokenizer tk = new StringTokenizer(s, ":.");
      if (tk.countTokens() == 2) {
        s = s + ":00";
      }
      t.setBackground(Color.white);
      d = java.sql.Time.valueOf(s);
      if (dbg) {
        Util.prt("ChkTime=" + s + " time->" + d);
      }
      err.set(false);
      t.setText("" + d);
    } catch (IllegalArgumentException e) {
      Util.prt("Error formatting time=" + s);
      err.set(true);
      err.appendText("Time format error");
      t.setBackground(UC.red);
    }
    return d;
  }

  /**
   * chkTime validate are user entered time in a JTextField via the "ErrorTrack" system. If the text
   * is empty the earliest possible time is returned. User needs to enter time in 00:00:00 format.
   *
   * @param t A JTextField containing the date
   * @param err The ErrorTrack field
   * @return The time decoded from the text
   */
  public static String chkLogfile(javax.swing.JTextField t, ErrorTrack err) {
    boolean dbg = false;
    String s = t.getText();
    String d = "";
    if (s.equals("")) {
      err.set(false);
      return d;
    }

    if (s.contains("/")) {
      Util.prt("Error with logfile - cannot contain '/'=" + s);
      err.set(true);
      err.appendText("Logfile format error - cannot contain '/'");
      t.setBackground(UC.red);
    } else {
      d = s;
    }

    return d;
  }

  /**
   * Check the JTextField for a properly formatted date. If blank the date is returned as a very
   * early SQL date. Will handle dates in mm/dd/yy or mm/dd/yyyy or yyyy/mm/dd form. Slashes can be
   * dashes as well. Two digit years are resolved by the "rule of 50" - 50> add 1900 <50 add 2000.
   * @param
   *
   *
   * t The textfield
   * @param err the ErrorTrack
   * @return a formated SQL date
   */
  public static java.sql.Date chkDate(javax.swing.JTextField t, ErrorTrack err) {
    boolean dbg = false;
    String s = t.getText().replace('/', '-');
    java.sql.Date d = new java.sql.Date((long) 0);
    if (s.equals("")) {
//      GregorianCalendar g = new GregorianCalendar();
//		Util.prt("Year="+d.get(Calendar.YEAR)+" mon="+d.get(Calendar.MONTH));
//		Util.prt("d="+d.toString());
//    Util.prt("time in millis="+d.getTimeInMillis());
//      d = new java.sql.Date(g.getTimeInMillis());
      err.set(false);
      return d;
    }
    // its in yyyy,ddd-hh:mm:ddd order
    if (s.contains(",")) {
      String[] tok = s.split(",");
      if (tok.length != 2) {
        err.set(true);
        err.appendText("YYYY,DDD dates is invalid");
        return null;
      }
      try {
        int year = Integer.parseInt(tok[0]);
        int doy = Integer.parseInt(tok[1]);
        int[] ymd = SeedUtil.ymd_from_doy(year, doy);

        d = java.sql.Date.valueOf((ymd[0] + "-" + Util.leftPad("" + ymd[1], 2) + "-" + Util.leftPad("" + ymd[2], 2)).replaceAll(" ", "0"));
        return d;
      } catch (NumberFormatException e) {
        err.set(true);
        err.appendText("YYYY,DDD is invalid");
        return d;
      }
    }

    try {
      StringTokenizer tk = new StringTokenizer(s, "-");
      String first = tk.nextToken();
      String second = tk.nextToken();
      String third = tk.nextToken();
      boolean yr_last = false;

      // Compensate for mm/dd/yy and mm/dd/yyyy format.  The correct form is yyyy-mm-dd
      // We assume if the last is two digits, it must be mm/dd/yy or if last is 4 digits
      // if first is 4 digits, assume year must be first
      if (third.length() == 4) {
        yr_last = true;
      }
      if (first.length() == 4) {
        yr_last = false;
      }
      if (third.length() <= 2 && first.length() <= 2) {
        yr_last = true;
      }
      //Util.prt("first="+first+" "+first.length()+" last="+third+" "+third.length());
      if (yr_last) {               // yr is last
        if (third.length() != 2 && third.length() != 4) {
          err.set(true);
          err.appendText("Bad year last format=" + s);
          t.setBackground(UC.red);
          return d;
        }
        if (third.length() <= 2) {  // Two digit year
          if (third.substring(0, 1).compareTo("7") > 0) {
            s = "19" + third + "-" + first + "-" + second;  // 1970 earliest
          } else {
            s = "20" + third + "-" + first + "-" + second;      // After 2000
          }
        } else {
          s = third + "-" + first + "-" + second;
        }
      } else {
        if (first.length() != 2 && first.length() != 4) {
          err.set(true);
          err.appendText("Bad year first format=" + s);
          t.setBackground(UC.red);
          return d;
        }
        if (first.length() <= 2) {       // two digit year
          if (first.substring(0, 1).compareTo("7") > 0) {
            s = "19" + first + "-" + second + "-" + third;
          } else {
            s = "20" + first + "-" + second + "-" + third;
          }
        }
      }

      // A time bomb - limit years to reasonable ranges in terms of the century
      if (s.substring(0, 4).compareTo("1950") < 0
              || s.substring(0, 4).compareTo("2050") > 0) {
        Util.prt("Year out of Range=" + s);
        err.set(true);
        err.appendText("Year out of range 1950-2050");
        t.setBackground(UC.red);
      }
      t.setBackground(Color.white);
      d = java.sql.Date.valueOf(s);
      if (dbg) {
        Util.prt("ChkDate=" + s + " date->" + d);
      }
      err.set(false);
      t.setText("" + d);
    } catch (IllegalArgumentException e) {
      Util.prt("Error formatting date=" + s);
      err.set(true);
      err.appendText("Date format error");
      t.setBackground(UC.red);
    } catch (NoSuchElementException e) {
      err.set(true);
      err.appendText("Date parsing error");
      t.setBackground(UC.red);
    }
    return d;
  }

  /**
   * Check the JTextField for a properly formatted date with MySQL datetime type ranges. If blank
   * the date is returned as a very early SQL date. Will handle dates in mm/dd/yy or mm/dd/yyyy or
   * yyyy/mm/dd form. Slashes can be dashes as well. Two digit years are resolved by the "rule of
   * 50" - 5 &gt add 1900 &lt 50 add 2000.
   *
   * @param t The textfield
   * @param err the ErrorTrack
   * @return a formatted SQL date
   */
  public static java.sql.Date chkDatetime2(javax.swing.JTextField t, ErrorTrack err) {
    boolean dbg = false;
    String s = t.getText().replace('/', '-');
    java.sql.Date d = new java.sql.Date((long) 0);
    if (s.equals("")) {
//      GregorianCalendar g = new GregorianCalendar();
//		Util.prt("Year="+d.get(Calendar.YEAR)+" mon="+d.get(Calendar.MONTH));
//		Util.prt("d="+d.toString());
//    Util.prt("time in millis="+d.getTimeInMillis());
//      d = new java.sql.Date(g.getTimeInMillis());
      err.set(false);
      return d;
    }
    // its in yyyy,ddd-hh:mm:ddd order
    if (s.contains(",")) {
      String[] tok = s.split(",");
      if (tok.length != 2) {
        err.set(true);
        err.appendText("YYYY,DDD dates is invalid");
        return null;
      }
      try {
        int year = Integer.parseInt(tok[0]);
        int doy = Integer.parseInt(tok[1]);
        int[] ymd = SeedUtil.ymd_from_doy(year, doy);

        d = java.sql.Date.valueOf(ymd[0] + "-" + ymd[1] + "-" + ymd[2]);
        return d;
      } catch (NumberFormatException e) {
        err.set(true);
        err.appendText("YYYY,DDD is invalid");
        return d;
      }
    }

    try {
      StringTokenizer tk = new StringTokenizer(s, "-");
      String first = tk.nextToken();
      String second = tk.nextToken();
      String third = tk.nextToken();
      boolean yr_last = false;

      // Compensate for mm/dd/yy and mm/dd/yyyy format.  The correct form is yyyy-mm-dd
      // We assume if the last is two digits, it must be mm/dd/yy or if last is 4 digits
      // if first is 4 digits, assume year must be first
      if (third.length() == 4) {
        yr_last = true;
      }
      if (first.length() == 4) {
        yr_last = false;
      }
      if (third.length() <= 2 && first.length() <= 2) {
        yr_last = true;
      }
      //Util.prt("first="+first+" "+first.length()+" last="+third+" "+third.length());
      if (yr_last) {               // yr is last
        if (third.length() != 2 && third.length() != 4) {
          err.set(true);
          err.appendText("Bad year last format=" + s);
          t.setBackground(UC.red);
          return d;
        }
        if (third.length() <= 2) {  // Two digit year
          if (third.substring(0, 1).compareTo("7") > 0) {
            s = "19" + third + "-" + first + "-" + second;  // 1970 earliest
          } else {
            s = "20" + third + "-" + first + "-" + second;      // After 2000
          }
        } else {
          s = third + "-" + first + "-" + second;
        }
      } else {
        if (first.length() != 2 && first.length() != 4) {
          err.set(true);
          err.appendText("Bad year first format=" + s);
          t.setBackground(UC.red);
          return d;
        }
        if (first.length() <= 2) {       // two digit year
          if (first.substring(0, 1).compareTo("7") > 0) {
            s = "19" + first + "-" + second + "-" + third;
          } else {
            s = "20" + first + "-" + second + "-" + third;
          }
        }
      }

      // A time bomb - limit years to reasonable ranges in terms of the century
      if (s.substring(0, 4).compareTo("1800") < 0
              || s.substring(0, 4).compareTo("9999") > 0) {
        Util.prt("Year out of Range=" + s);
        err.set(true);
        err.appendText("Year out of range 1800-9999");
        t.setBackground(UC.red);
      }
      t.setBackground(Color.white);
      d = java.sql.Date.valueOf(s);
      if (dbg) {
        Util.prt("ChkDate=" + s + " date->" + d);
      }
      err.set(false);
      t.setText("" + d);
    } catch (IllegalArgumentException e) {
      Util.prt("Error formatting date=" + s);
      err.set(true);
      err.appendText("Date format error");
      t.setBackground(UC.red);
    } catch (NoSuchElementException e) {
      err.set(true);
      err.appendText("Date parsing error");
      t.setBackground(UC.red);
    }
    return d;
  }

  /**
   * Verify a timestamp from the JTextField. If "now" set current time/date. If blank, return a very
   * early timestamp. The general form is yyyy-mm-dd hh:mm:ss Though the date can be any form
   * acceptable to chkDate() and check time. This routine uses the space to split the fields.
   *
   * @param t The textfield with a date/time
   * @param err the Errortrack variable
   * @return a valid timestamp possibly a very early one.
   */
  public static Timestamp chkTimestamp(javax.swing.JTextField t, ErrorTrack err) {
    String s = t.getText();
    if (s.equals("")) {
      return new Timestamp((long) 86400000);
    }
    if (s.equals("now")) {
      t.setText(Util.now().toString().substring(0, 19));
      return Util.now();
    }
    String save = s;
    StringTokenizer tk = new StringTokenizer(s, " ");
    String date = tk.nextToken();
    String time = "";
    if (tk.hasMoreTokens()) {
      time = tk.nextToken();
    }
    int cnt = err.errorCount();
    t.setText("" + date);
    //Util.prt("chkTimestamp date="+t.getText()+" - "+err.errorCount());
    java.sql.Date d = FUtil.chkDate(t, err);
    t.setText(time);
    //Util.prt("chkTimestamp time="+t.getText()+"-"+err.errorCount());
    java.sql.Time tt = FUtil.chkTime(t, err);
    t.setText(save);
    if (err.errorCount() != cnt) {
      return new Timestamp((long) 0);
    }
    s = "" + d + " " + tt;
    //Util.prt("chkTime stamp ="+s);
    try {
      Timestamp ts = Timestamp.valueOf(s);
      t.setText(ts.toString().substring(0, 19));
      return ts;
    } catch (IllegalArgumentException e) {
      Util.prt("Error formatting date=" + s);
      err.set(true);
      err.appendText("Date format error");
      t.setBackground(UC.red);
      return new Timestamp((long) 0);
    }
  }

  /**
   * Verify a timestamp which follows Datetime MySQL format. This allows a much bigger range of data
   * values (9999-12-31) from the JTextField. If "now" set current time/date. If blank, return a
   * very early timestamp (near 1970). The general form is yyyy-mm-dd hh:mm:ss Though the date can
   * be any form acceptable to chkDatetime2() and check time. This routine uses the space to split
   * the fields.
   *
   * @param t The textfield with a date/time
   * @param err the Errortrack variable
   * @return a valid timestamp possibly a very early one.
   */
  public static Timestamp chkDatetime(javax.swing.JTextField t, ErrorTrack err) {
    String s = t.getText();
    if (s.equals("")) {
      return new Timestamp((long) 86400000);
    }
    if (s.equals("now")) {
      t.setText(Util.now().toString().substring(0, 19));
      return Util.now();
    }
    String save = s;
    StringTokenizer tk = new StringTokenizer(s, " ");
    String date = tk.nextToken();
    String time = "";
    if (tk.hasMoreTokens()) {
      time = tk.nextToken();
    }
    int cnt = err.errorCount();
    t.setText("" + date);
    //Util.prt("chkTimestamp date="+t.getText()+" - "+err.errorCount());
    java.sql.Date d = FUtil.chkDatetime2(t, err);
    t.setText(time);
    //Util.prt("chkTimestamp time="+t.getText()+"-"+err.errorCount());
    java.sql.Time tt = FUtil.chkTime(t, err);
    t.setText(save);
    if (err.errorCount() != cnt) {
      return new Timestamp((long) 0);
    }
    s = "" + d + " " + tt;
    //Util.prt("chkTime stamp ="+s);
    try {
      Timestamp ts = Timestamp.valueOf(s);
      t.setText(ts.toString().substring(0, 19));
      return ts;
    } catch (IllegalArgumentException e) {
      Util.prt("Error formatting date=" + s);
      err.set(true);
      err.appendText("Date format error");
      t.setBackground(UC.red);
      return new Timestamp((long) 0);
    }
  }

  /**
   * Verify a timestamp from the JTextField. If "now" set current time/date. If blank, return a very
   * early timestamp. The general form is yyyy-mm-dd hh:mm:ss Though the date can be any form
   * acceptable to chkDate() and check time. This routine uses the space to split the fields.
   *
   * @param t The textfield with a date/time
   * @param err the Errortrack variable
   * @return a valid timestamp possibly a very early one.
   */
  public static Timestamp chkTimestampTimeOnly(javax.swing.JTextField t, ErrorTrack err) {
    String s = t.getText();
    if (s.equals("")) {
      return new Timestamp((long) 86400000);
    }
    if (s.equals("now")) {
      t.setText(Util.now().toString().substring(0, 19));
      return Util.now();
    }
    String save = s;
    StringTokenizer tk = new StringTokenizer(s, " ");
    String time = tk.nextToken();
    String date = "2000-01-01";
    int cnt = err.errorCount();
    t.setText("" + date);
    //Util.prt("chkTimestamp date="+t.getText()+" - "+err.errorCount());
    java.sql.Date d = FUtil.chkDate(t, err);
    t.setText(time);
    //Util.prt("chkTimestamp time="+t.getText()+"-"+err.errorCount());
    java.sql.Time tt = FUtil.chkTime(t, err);
    t.setText(save);
    if (err.errorCount() != cnt) {
      return new Timestamp((long) 0);
    }
    s = "" + d + " " + tt;
    //Util.prt("chkTime stamp ="+s);
    try {
      Timestamp ts = Timestamp.valueOf(s);
      t.setText(ts.toString().substring(11, 19));
      return ts;
    } catch (IllegalArgumentException e) {
      Util.prt("Error formatting date=" + s);
      err.set(true);
      err.appendText("Date format error");
      t.setBackground(UC.red);
      return new Timestamp((long) 0);
    }
  }

  /**
   * Convert a string to a Timestamp. If the time is not present, assume 00:00
   *
   * @param s The string to attempt to convert
   * @return The corresponding time stamp or the times stamp for 0
   */
  public static Timestamp stringToTimestamp(String s) {
    if (s.equals("")) {
      return new Timestamp((long) 0);
    }
    if (s.equalsIgnoreCase("now")) {
      return Util.now();
    }
    String save = s;
    StringTokenizer tk = new StringTokenizer(s, " ");
    String date = tk.nextToken();
    String time = "00:00:00";
    if (tk.hasMoreTokens()) {
      time = tk.nextToken();
    }
    String timeOrg = time;
    //Util.prt("strToTS date="+date);
    java.sql.Date d = Date.valueOf(date);
    //Util.prt("strToTS time="+time);

    // Allow user to use 00:00 by adding a :00 if so
    tk = new StringTokenizer(time, ":.");
    if (tk.countTokens() == 2) {
      time = time + ":00";
    }
    if (time.length() > 8) {
      time = time.substring(0, 8);
    }
    java.sql.Time tt = Time.valueOf(time);
    s = "" + d + " " + tt;
    //Util.prt("chkTime stamp ="+s);
    try {
      Timestamp ts = Timestamp.valueOf(s);
      if (timeOrg.length() > 8 && timeOrg.indexOf(".") == 8) {
        String millis = timeOrg.substring(9);
        if (millis.length() >= 3) {
          millis = millis.substring(0, 3);
        } else {
          millis = (millis + "000").substring(0, 3);
        }
        int ms = Integer.parseInt(millis);
        ts.setTime(ts.getTime() / 1000 * 1000 + ms);
      }
      return ts;
    } catch (IllegalArgumentException e) {
      Util.prt("Error formatting date=" + s);
      return new Timestamp((long) 0);
    }

  }

  /**
   * Validate an IP address in dotted number form nnn.nnn.nnn.nnn. This will insure there are 4
   * bytes in the address and that the digits are all numbers. between the dots the numbers can be
   * one, two or three digits long.
   *
   * @param t The textfield with a supposed IP adr
   * @param err errorTrack variable
   * @return String with the reformated to nnn.nnn.nnn.nnn form
   */
  public static String chkIP(javax.swing.JTextField t, ErrorTrack err) {
    return chkIP(t, err, false);
  }

  /**
   * validate an IP address in dotted number form nnn.nnn.nnn.nnn. This will insure there are 4
   * bytes in the address and that the digits are all numbers. between the dots the numbers can be
   * one, two or three digits long.
   *
   * @param t The textfield with a supposed IP adr
   * @param err errorTrack variable
   * @param nullOK If true and empty field is O.K and returns an empty string
   * @return String with the reformated to nnn.nnn.nnn.nnn form
   */
  public static String chkIP(javax.swing.JTextField t, ErrorTrack err, boolean nullOK) {
    // The string is in dotted form, we return always in 3 per section form  
    StringBuilder out = new StringBuilder(15);
    StringTokenizer tk = new StringTokenizer(t.getText(), ".");
    if (t.getText().equals("")) {
      if (nullOK) {
        return "";
      }
      err.set(true);
      err.appendText("IP format bad - its null");
      return "";
    }
    if (t.getText().charAt(0) < '0' || t.getText().charAt(0) > '9') {
      /*Util.prt("FUTIL: chkip Try to find station "+t.getText());
     TCPStation a = RTSStationPanel.getTCPStation(t.getText());
     if(a != null) {
       Util.prt("FUtil: chkip found station="+a);
       t.setText(a.getIP());
     }*/
      err.set(true);
      err.appendText("IP format bad digit");
      return "";
    }
    if (tk.countTokens() != 4) {
      err.set(true);
      err.appendText("IP format bad - wrong # of '.'");
      return "";
    }
    for (int i = 0; i < 4; i++) {
      String s = tk.nextToken();
      switch (s.length()) {
        case 3:
          out.append(s);
          break;
        case 2:
          out.append("0").append(s);
          break;
        case 1:
          out.append("00").append(s);
          break;
        default:
          err.set(true);
          err.appendText("IP byte wrong length=" + s.length());
          return "";
      }
      if (i < 3) {
        out.append(".");
      }
    }
    t.setText(out.toString());
    return out.toString();

  }

  /**
   * validate a string field contains a valid double. Report error via ErrorTrack.
   *
   * @param t The text
   * @param err The ErrorTrack
   * @return 0 if Double.parseDouble() fails, or field is blank, otherwise the conversion of the
   * text.
   */
  public static double chkDouble(javax.swing.JTextField t, ErrorTrack err) {
    double d = 0.;
    try {
      d = Double.parseDouble(t.getText());
      //Util.prt("FUtil.chkDouble t="+t.getText()+" d="+d);
      t.setBackground(Color.white);
      err.set(false);             // No error this time
    } catch (NumberFormatException e) {
      t.setBackground(UC.red);
      err.appendText("Decimal format err");
      err.set(true);
    }
    return d;
  }

  /**
   * validate a string field contains a valid double with upper and lower limits inclusive. Report
   * error via ErrorTrack.
   *
   * @param t The text
   * @param err The ErrorTrack
   * @param lower a double with the lower of acceptable range
   * @param upper a double with the upper of acceptable range
   * @return 0 if Double.parseDouble() fails, or field is blank, otherwise the conversion of the
   * text.
   */
  public static double chkDouble(javax.swing.JTextField t, ErrorTrack err,
          double lower, double upper) {
    return chkDouble(t, err, lower, upper, false);
  }

  /**
   * validate a string field contains a valid double with upper and lower limits inclusive. Report
   * error via ErrorTrack.
   *
   * @param t The text
   * @param err The ErrorTrack
   * @param lower a double with the lower of acceptable range
   * @param upper a double with the upper of acceptable range
   * @param blankOK If true, an empty field is o.k. and returns zero
   * @return 0 if Double.parseDouble() fails, or field is blank, otherwise the conversion of the
   * text.
   */
  public static double chkDouble(javax.swing.JTextField t, ErrorTrack err,
          double lower, double upper, boolean blankOK) {
    if (blankOK && t.getText().equals("")) {
      return 0.;
    }
    double d = chkDouble(t, err);
    if (!err.lastWasError()) {
      if (d < lower || d > upper) {
        t.setBackground(UC.red);
        err.set(true);
        err.appendText("Out of range [" + lower + ", " + upper + "]");
      }
    }
    return d;
  }

  /**
   * validate that the textfield contains a valid integer. Report via ErrorTrack. Blank field is
   * treated as an error (see other form to allow blank).
   *
   * @param t The textfield
   * @param err The ErrorTrack
   * @return The conversion if no error is returned
   */
  public static int chkInt(javax.swing.JTextField t, ErrorTrack err) {
    return chkInt(t, err, false);
  }

  /**
   * validate that the textfield contains a valid integer. Report via ErrorTrack. BlankOK allows
   * control of whether a blank field is acceptable.
   *
   * @param t The textfield
   * @param err The ErrorTrack
   * @param blankOK If true, a blank text returns 0. else its an error
   * @return The conversion if no error is returned
   */
  public static int chkInt(javax.swing.JTextField t, ErrorTrack err, boolean blankOK) {
    boolean dbg = false;
    int d = 0;
    if (blankOK && t.getText().equals("")) {
      return 0;
    }
    try {
      if (dbg) {
        Util.prt("FUtil.chkInt t=" + t.getText());
      }
      d = Integer.parseInt(t.getText());
      if (dbg) {
        Util.prt("FUtil.chkInt t=" + t.getText() + " d=" + d);
      }
      t.setBackground(Color.white);
      err.set(false);             // No error this time
    } catch (NumberFormatException e) {
      t.setBackground(UC.red);
      err.appendText("Integer format err");
      err.set(true);
    }
    return d;
  }

  /**
   * validate that the textfield contains a valid integer with inclusive range. Report via
   * ErrorTrack. Blank is an error
   *
   * @param t The textfield
   * @param err The ErrorTrack
   * @param lower The lower end of range
   * @param upper The higher end of Range.
   * @return The conversion if no error is returned
   */
  public static int chkInt(javax.swing.JTextField t, ErrorTrack err,
          int lower, int upper) {
    int d = chkInt(t, err);
    if (!err.lastWasError()) {
      if (d < lower || d > upper) {
        t.setBackground(UC.red);
        err.set(true);
        err.appendText("Out of range [" + lower + ", " + upper + "]");
      }
    }
    return d;
  }

  /**
   * validate that the textfield contains a valid integer with inclusive range. Report via
   * ErrorTrack. Blank is controlled by boolean argument.
   *
   * @param t The textfield
   * @param err The ErrorTrack
   * @param lower The lower end of range
   * @param upper The higher end of Range.
   * @param blankOK boolean if true, blank returns 0, else error
   * @return The conversion if no error is returned
   */
  public static int chkInt(javax.swing.JTextField t, ErrorTrack err,
          int lower, int upper, boolean blankOK) {
    if (blankOK && t.getText().trim().equals("")) {
      return 0;
    }
    return chkInt(t, err, lower, upper);
  }

  /**
   * validate that the textfield contains a valid integer. Report via ErrorTrack. BlankOK allows
   * control of whether a blank field is acceptable.
   *
   * @param t The textfield
   * @param err The ErrorTrack
   * @param blankOK If true, a blank text returns 0. else its an error
   * @return The conversion if no error is returned
   */
  public static long chkLong(javax.swing.JTextField t, ErrorTrack err, boolean blankOK) {
    boolean dbg = false;
    long d = 0;
    if (blankOK && t.getText().equals("")) {
      return 0L;
    }
    try {
      if (dbg) {
        Util.prt("FUtil.chkInt t=" + t.getText());
      }
      d = Long.parseLong(t.getText());
      if (dbg) {
        Util.prt("FUtil.chkInt t=" + t.getText() + " d=" + d);
      }
      t.setBackground(Color.white);
      err.set(false);             // No error this time
    } catch (NumberFormatException e) {
      t.setBackground(UC.red);
      err.appendText("Integer format err");
      err.set(true);
    }
    return d;
  }

  /**
   * validate that the textfield contains a valid integer with inclusive range. Report via
   * ErrorTrack. Blank is controlled by boolean argument.
   *
   * @param t The textfield
   * @param err The ErrorTrack
   * @param lower The lower end of range
   * @param upper The higher end of Range.
   * @param blankOK boolean if true, blank returns 0, else error
   * @return The conversion if no error is returned
   */
  public static long chkLong(javax.swing.JTextField t, ErrorTrack err,
          long lower, long upper, boolean blankOK) {
    if (blankOK && t.getText().trim().equals("")) {
      return 0;
    }
    long d = chkLong(t, err, blankOK);
    if (!err.lastWasError()) {
      if (d < lower || d > upper) {
        t.setBackground(UC.red);
        err.set(true);
        err.appendText("Out of range [" + lower + ", " + upper + "]");
      }
    }
    return d;

  }

  /**
   * validate that the textfield contains a valid integer. Report via ErrorTrack. Blank field is
   * treated as an error (see other form to allow blank).
   *
   * @param t The textfield
   * @param err The ErrorTrack
   * @return The conversion if no error is returned
   */
  public static int chkIntHex(javax.swing.JTextField t, ErrorTrack err) {
    return chkIntHex(t, err, false);
  }

  /**
   * validate that the textfield contains a valid integer. Report via ErrorTrack. BlankOK allows
   * control of whether a blank field is acceptable.
   *
   * @param t The textfield
   * @param err The ErrorTrack
   * @param blankOK If true, a blank text returns 0. else its an error
   * @return The conversion if no error is returned
   */
  public static int chkIntHex(javax.swing.JTextField t, ErrorTrack err, boolean blankOK) {
    boolean dbg = false;
    int d = 0;
    if (blankOK && t.getText().equals("")) {
      return 0;
    }
    try {
      if (dbg) {
        Util.prt("FUtil.chkInt t=" + t.getText());
      }
      if (t.getText().length() >= 2) {
        if (t.getText().substring(0, 2).equals("0x")) {
          d = Integer.parseInt(t.getText().substring(2), 16);
        } else {
          d = Integer.parseInt(t.getText(), 16);
        }
      } else {
        d = Integer.parseInt(t.getText(), 16);
      }
      if (dbg) {
        Util.prt("FUtil.chkInt t=" + t.getText() + " d=" + d);
      }
      t.setBackground(Color.white);
      err.set(false);             // No error this time
    } catch (NumberFormatException e) {
      t.setBackground(UC.red);
      err.appendText("Integer format err");
      err.set(true);
    }
    return d;
  }

  /**
   * validate that the textfield contains a valid integer with inclusive range. Report via
   * ErrorTrack. Blank is an error
   *
   * @param t The textfield
   * @param err The ErrorTrack
   * @param lower The lower end of range
   * @param upper The higher end of Range.
   * @return The conversion if no error is returned
   */
  public static int chkIntHex(javax.swing.JTextField t, ErrorTrack err,
          int lower, int upper) {
    int d = chkIntHex(t, err);
    if (!err.lastWasError()) {
      if (d < lower || d > upper) {
        t.setBackground(UC.red);
        err.set(true);
        err.appendText("Out of range [" + lower + ", " + upper + "]");
      }
    }
    return d;
  }

  /**
   * validate that the textfield contains a valid integer. Report via ErrorTrack. Blank field is
   * treated as an error (see other form to allow blank).
   *
   * @param t The textfield
   * @param err The ErrorTrack
   * @return The conversion if no error is returned
   */
  public static long chkLongHex(javax.swing.JTextField t, ErrorTrack err) {
    return chkLongHex(t, err, false);
  }

  /**
   * validate that the textfield contains a valid integer. Report via ErrorTrack. BlankOK allows
   * control of whether a blank field is acceptable.
   *
   * @param t The textfield
   * @param err The ErrorTrack
   * @param blankOK If true, a blank text returns 0. else its an error
   * @return The conversion if no error is returned
   */
  public static long chkLongHex(javax.swing.JTextField t, ErrorTrack err, boolean blankOK) {
    boolean dbg = false;
    long d = 0;
    if (blankOK && t.getText().equals("")) {
      return 0;
    }
    try {
      if (dbg) {
        Util.prt("FUtil.chkInt t=" + t.getText());
      }
      if (t.getText().length() >= 2) {
        if (t.getText().substring(0, 2).equals("0x")) {
          d = Long.parseLong(t.getText().substring(2), 16);
        } else {
          d = Long.parseLong(t.getText(), 16);
        }
      } else {
        d = Long.parseLong(t.getText(), 16);
      }
      if (dbg) {
        Util.prt("FUtil.chkInt t=" + t.getText() + " d=" + d);
      }
      t.setBackground(Color.white);
      err.set(false);             // No error this time
    } catch (NumberFormatException e) {
      t.setBackground(UC.red);
      err.appendText("Integer format err");
      err.set(true);
    }
    return d;
  }

  /**
   * validate that the textfield contains a valid integer with inclusive range. Report via
   * ErrorTrack. Blank is an error
   *
   * @param t The textfield
   * @param err The ErrorTrack
   * @param lower The lower end of range
   * @param upper The higher end of Range.
   * @return The conversion if no error is returned
   */
  public static long chkLongHex(javax.swing.JTextField t, ErrorTrack err,
          long lower, long upper) {
    long d = chkLongHex(t, err);
    if (!err.lastWasError()) {
      if (d < lower || d > upper) {
        t.setBackground(UC.red);
        err.set(true);
        err.appendText("Out of range [" + lower + ", " + upper + "]");
      }
    }
    return d;
  }

  /**
   * validate that the textfield contains a valid integer with inclusive range. Report via
   * ErrorTrack. Blank is controlled by boolean argument.
   *
   * @param t The textfield
   * @param err The ErrorTrack
   * @param lower The lower end of range
   * @param upper The higher end of Range.
   * @param blankOK boolean if true, blank returns 0, else error
   * @return The conversion if no error is returned
   */
  public static int chkIntHex(javax.swing.JTextField t, ErrorTrack err,
          int lower, int upper, boolean blankOK) {
    if (blankOK && t.getText().trim().equals("")) {
      return 0;
    }
    return chkIntHex(t, err, lower, upper);
  }

  /**
   * If a combobox does not have a unique selection, To use this make the JcomboBox "editable" and
   * call this first thing in the action handler. If the user types in something, it attempts to
   * find a unique match in the list. If allowNew is true, the box remains grey and -1 is returned.
   * If allowNew is false, the box turns yellow.
   *
   * @param b The jcombobox to search in
   * @param allowNew Boolean indicating weather a new one is allowed
   * @return The index of the selected item in JComboBox. If <0, No item is selected. This can
   * happen if multiple matches are found or if no match is found
   */
  public static int searchJComboBox(JComboBox b, boolean allowNew) {
    int i = FUtil.searchJComboBox(b);
    b.setBackground(Color.lightGray);
    if (allowNew) {
      return i;
    } else if (i <= -1) {
      b.setSelectedIndex(-1);
      b.setBackground(UC.yellow);
    }
    return i;
  }

  /**
   * If a combobox does not have a unique selection, To use this make the JcomboBox "editable" and
   * call this first thing in the action handler. If the user types in something, it attempts to
   * find a unique match in the list. If its not found, the JComboBox is turned RED indicating no
   * select made. If more than one item is found, it is also an error
   *
   * @param b The jcombobox to search in
   * @return The index of the selected item in JComboBox. If =-1 No item is matched-> new item If <
   * -1, the minus the number of matches found.
   */
  public static int searchJComboBox(JComboBox b) {
    if (b == null) {
      return -1;
    }
    if (b.getSelectedIndex() >= 0) {
      return b.getSelectedIndex();
    }
    if (b.getSelectedItem() == null) {
      return -1;
    }
    String s = b.getSelectedItem().toString().toUpperCase();
    if (s.equals("")) {
      return -1;
    }
    //Util.prt("searchJComboBox="+s);
    int nmatches = 0;
    int last = -1;
    int i;
    b.setBackground(Color.lightGray);
    for (i = 0; i < b.getItemCount(); i++) {
      //Util.prt("index of ="+b.getItemAt(i).toString().toUpperCase()+" "+
      if (b.getItemAt(i).toString().toUpperCase().contains(s)) {
        nmatches++;
        last = i;
      }
    }
    //Util.prt("Matches="+nmatches);
    if (nmatches == 1) {
      b.setSelectedIndex(last);

      return last;
    } else if (nmatches > 1) {
      b.setSelectedIndex(-1);
      b.setBackground(UC.red);
      return -nmatches;
    } else {            // getSelectedIndex() will return -1 but getSelectedItem() gets the string
      return -1;
    }
  }

  /**
   * If a combobox does not have a unique selection (must be at beginning, To use this make the
   * JcomboBox "editable" and call this first thing in the action handler. If the user types in
   * something, it attempts to find a unique match in the list. If allowNew is true, the box remains
   * grey and -1 is returned. If allowNew is false, the box turns yellow.
   *
   * @param b The jcombobox to search in
   * @param allowNew Boolean indicating weather a new one is allowed
   * @return The index of the selected item in JComboBox. If <0, No item is selected. This can
   * happen if multiple matches are found or if no match is found
   */
  public static int searchJComboBoxBeginMatch(JComboBox b, boolean allowNew) {
    int i = FUtil.searchJComboBoxBeginMatch(b);
    b.setBackground(Color.lightGray);
    if (allowNew) {
      return i;
    } else if (i <= -1) {
      b.setSelectedIndex(-1);
      b.setBackground(UC.yellow);
    }
    return i;
  }

  /**
   * If a combobox does not have a unique selection, To use this make the JcomboBox "editable" and
   * call this first thing in the action handler. If the user types in something, it attempts to
   * find a unique match in the list (beginning of string only). If its not found, the JComboBox is
   * turned RED indicating no select made. If more than one item is found, it is also an error
   *
   * @param b The jcombobox to search in
   * @return The index of the selected item in JComboBox. If =-1 No item is matched-> new item If <
   * -1, the minus the number of matches found.
   */
  public static int searchJComboBoxBeginMatch(JComboBox b) {
    if (b == null) {
      return -1;
    }
    if (b.getSelectedIndex() >= 0) {
      return b.getSelectedIndex();
    }
    if (b.getSelectedItem() == null) {
      return -1;
    }
    String s = b.getSelectedItem().toString().toUpperCase();
    if (s.equals("")) {
      return -1;
    }
    //Util.prt("searchJComboBox="+s);
    int nmatches = 0;
    int last = -1;
    int i;
    b.setBackground(Color.lightGray);
    for (i = 0; i < b.getItemCount(); i++) {
      //Util.prt("index of ="+b.getItemAt(i).toString().toUpperCase()+" "+
      //  b.getItemAt(i).toString().toUpperCase().indexOf(s);
      if (b.getItemAt(i).toString().toUpperCase().indexOf(s) == 0) {
        nmatches++;
        last = i;
      }
    }
    //Util.prt("Matches="+nmatches);
    if (nmatches == 1) {
      b.setSelectedIndex(last);

      return last;
    } else if (nmatches > 1) {
      b.setSelectedIndex(-1);
      b.setBackground(UC.red);
      return -nmatches;
    } else {            // getSelectedIndex() will return -1 but getSelectedItem() gets the string
      return -1;
    }
  }

  /**
   * If a combobox does not have a unique selection, To use this make the JcomboBox "editable" and
   * call this first thing in the action handler. If the user types in something, it attempts to
   * find a unique match in the list. If its not found, the JComboBox is turned RED indicating no
   * select made. If more than one item is found, it is also an error
   *
   * @param b The jcombobox to search in
   * @param match The string to match
   * @return The index of the selected item in JComboBox. If =-1 No item is matched-> new item If <
   * -1, the minus the number of matches found.
   */
  public static int searchJComboBox(JComboBox b, String match) {
    if (b == null) {
      return -1;
    }
    int nmatches = 0;
    int last = -1;
    int i;
    b.setBackground(Color.lightGray);
    for (i = 0; i < b.getItemCount(); i++) {
      //Util.prt("index of ="+b.getItemAt(i).toString().toUpperCase()+" "+
      //  b.getItemAt(i).toString().toUpperCase().indexOf(match);
      if (b.getItemAt(i).toString().toUpperCase().contains(match)) {
        nmatches++;
        last = i;
      }
    }
    //Util.prt("Matches="+nmatches);
    if (nmatches == 1) {
      b.setSelectedIndex(last);

      return last;
    } else if (nmatches > 1) {
      b.setSelectedIndex(-1);
      b.setBackground(UC.red);
      return -nmatches;
    } else {
      b.setSelectedIndex(-1);
      return -1;
    }
  }

  /**
   * Check that something is selected in a JComboBox. Report errors via error track.
   *
   * @param b The JComboBox
   * @param field Text to put append to ErrorTrack if nothing selected "No "+field+" selected!"
   * @param err The ErrorTrack
   */
  public static void chkJComboBox(JComboBox b, String field, ErrorTrack err) {
    if (b.getSelectedIndex() < 0) {
      err.set(true);
      err.appendText("No " + field + " selected!");
    }
  }

  /**
   * Given a JComboBox and a Value which represent the toString() of the items in the JComboBox,
   * return the index to the JComboBox matching the string. This does not change the selection of
   * the JComboBox, its just a lookup.
   *
   * @param b The JComboBox
   * @param val The string value to match again's entries in the box
   * @return the index in JComboBox for the match, -1 if no match
   */
  public static int getJComboBoxStringToInt(JComboBox b, String val) {
    for (int i = 0; i < b.getItemCount(); i++) {
      String s = b.getItemAt(i).toString();
      if (s.lastIndexOf('=') > 0) {
        s = s.substring(0, s.indexOf('=')).trim();
      }

      //Util.prt("item="+b.getItemAt(i).toString()+"->"+s+" val="+val);
      if (s.equalsIgnoreCase(val)) {
        return i;
      }
    }
    return -1;
  }

  /**
   * Creates a JComboBox with the values of a MySQL enum field.
   *
   * @param C a connection to MySQL
   * @param table The table name in MySQL
   * @param field The field in the table
   * @return A new JComboBox with the ENUM Strings in database enum order
   */
  public static JComboBox getEnumJComboBox(Connection C, String table, String field) {
    ArrayList<String> v = getEnum(C, table, field);
    JComboBox b = new JComboBox();
    for (Object v1 : v) {
      b.addItem(v1);
    }
    b.setMaximumRowCount(30);

    return b;
  }

  /**
   * Update the given JComboBox to reflect the enum. The JCombox is cleared and rebuilt from MySQl
   *
   * @param b The JComboBox to reform
   * @param C a connection to MySQL
   * @param table The table name in MySQL
   * @param field The field in the table
   */
  public static void updateEnumJComboBox(JComboBox b, Connection C, String table, String field) {
    ArrayList<String> v = getEnum(C, table, field);
    b.removeAllItems();
    for (Object v1 : v) {
      b.addItem(v1);
    }
    b.setMaximumRowCount(30);
  }

  /**
   * Given an ArrayList representing an EnumString, search for the given value and return the index
   * in the ArrayList containing a match. Return 0 if match not found. Remember enums alway have
   * zero as a "invalid" value since MySQL starts enums at 1
   *
   * @param a The array list to look in.
   * @param val The value to look for
   * @return The index of the ArrayList which matches. Note; zero is always "invalid" because enums
   * in MySQL start with one.
   */
  public static int enumStringToInt(ArrayList a, String val) {
    for (int i = 0; i < a.size(); i++) {
      //Util.prt(""+i+" a="+a.get(i)+" val="+val);
      if (a.get(i).equals(val)) {
        return i;
      }
    }
    return 0;
  }

  /**
   * Get the values of an Enum as an ArrayList. ArrayList[0] is always "invalid" because MySQL does
   * Enum starting at one.
   *
   * @param C a connection to MySQL
   * @param table The table name in MySQL
   * @param field The field in the table
   * @return The ArrayList with the enum values in it.
   */
  // 
  public static ArrayList<String> getEnum(Connection C, String table, String field) {
    int n = 0;
    try {
      Statement stmt = C.createStatement();    //  Use  statement  for  query
      String s = "SHOW COLUMNS FROM " + table + " LIKE " + Util.sqlEscape(field);
      ResultSet rs = stmt.executeQuery(s);
      rs.next();
      String enum2 = rs.getString(2);
      StringTokenizer tk = new StringTokenizer(enum2, ",");
      ArrayList<String> out = new ArrayList<>(tk.countTokens() + 2);
      int i = 1;
      out.add(0, "Invalid");                 // The "0" state says not set
      while (tk.hasMoreTokens()) {
        String tmp = tk.nextToken();          // Get the Token
        int j = tmp.indexOf('\'');
        if (tk.hasMoreTokens()) {
          tmp = tmp.substring(j + 1, tmp.length() - 1);
        } else {
          tmp = tmp.substring(j + 1, tmp.length());
        }
        if (!tk.hasMoreTokens()) {
          tmp = tmp.substring(0, tmp.length() - 2);
        }
        out.add(i, tmp);
        i++;
      }
      return out;
    } catch (SQLException e) {
      Util.SQLErrorPrint(e, "getEnum on table " + table + " field=" + field + "SQL failed");
      return null;
    }
  }

  /**
   * Make a ArrayList of a single field from a MySQL table. The index to the array list is the MySQL
   * ID for the row containing the String in the ArrayList.
   *
   * @param C a connection to MySQL
   * @param table The table name in MySQL
   * @param field The field in the table
   * @return The index to the array list is the MySQL ID for the row containing the String in the
   * ArrayList from the field
   *
   */
  public static ArrayList getFileList(Connection C, String table, String field) {
    int n = 0;
    try {
      Statement stmt = C.createStatement();    //  Use  statement  for  query
      String s = "SELECT ID," + field + " FROM " + table;
      ResultSet rs = stmt.executeQuery(s);
      ArrayList<String> out = new ArrayList<>(100);
      for (int j = 0; j < 100; j++) {
        out.add(j, "Unassigned");  // Large list unassigned
      }
      int max = 0;                // Track maximum "ID" set

      // For each row, set up a item in the Array list and track maximum set
      while (rs.next()) {
        String tmp = rs.getString(field);
        int j = rs.getInt("ID");
        out.add(j, tmp);
        if (j > max) {
          max = j;
        }
      }

      // Remove all unused element at the end and trim ArrayList to size
      for (int i = out.size() - 1; i > max; i--) {
        out.remove(i);
      }
      out.trimToSize();
      return out;
    } catch (SQLException e) {
      Util.SQLErrorPrint(e, "getFileList on table " + table + " field=" + field + "SQL failed");
      return null;
    }
  }

  /**
   * Give a string use MySQL to encode it to a password
   *
   * @param in The clear text with the password
   * @return The MySQL encoding of this password
   */
  public static String encodePassword(String in) {
    try {
      Statement stmt2 = UC.getConnection().createStatement();  // Use statement for query
      String text = "SELECT password(" + Util.sqlEscape(in) + ")";
      ResultSet rs2 = stmt2.executeQuery(text);
      rs2.next();
      return rs2.getString(1);
    } catch (SQLException E) {
      Util.prt("FUtil.encodePassword : SQL error encoding password");
      return "";
    }
  }

  /**
   * Checks to see if a String contains "true".
   *
   * @param s A string to be checked for "true"
   * @return 1 if string argument is "true", and zero otherwise including null s
   */
  public static int isTrue(String s) {
    if (s == null) {
      //Util.prt("call to isTrue with null string!");
      return 0;
    }
    if (s.compareToIgnoreCase("true") == 0) {
      return 1;
    } else {
      return 0;
    }
  }

  /**
   * Assuming an array in little endian order, build an int from 4 bytes
   *
   * @param b an array of bytes
   * @param offset the offset into b where to start getting the int
   * @return An integer assuming the bytes are in little endian order.
   */
  public static int intFromArray(byte[] b, int offset) {
    return (((int) b[offset + 3]) << 24 & 0xff000000) | 
            (((int) b[offset + 2]) << 16 & 0xFF0000) |
            (((int) b[offset + 1]) << 8 & 0xff00) | 
            ((int) b[offset] & 0xff);
  }

  /**
   * Assuming an array in little endian order, build an short from 2 bytes
   *
   * @param b an array of bytes
   * @param offset the offset into b where to start getting the short
   * @return An short assuming the bytes are in little endian order.
   */
  public static short shortFromArray(byte[] b, int offset) {
    return (short) ((((int) b[offset + 1]) << 8 & 0xff00) | ((int) b[offset] & 0xff));
  }

  /**
   * Assuming a array of bytes containing ASCII characters, build a string
   *
   * @param b the array of bytes
   * @param offset the index to beginning of string
   * @param len Then number of bytes to put in the String
   * @return The string with len bytes starting from b[offset]
   */
  public static String stringFromArray(byte[] b, int offset, int len) {
    StringBuilder sb = new StringBuilder(len + 2);
    for (int i = 0; i < len; i++) {
      sb.append((char) b[i + offset]);
    }
    return sb.toString();
  }

  /**
   * Assuming an array in little endian order, build an int from 4 bytes
   *
   * @param in The input integer
   * @param b an array of bytes
   * @param offset the offset into b where to start getting the int
   */
  public static void intToArray(int in, byte[] b, int offset) {
    b[offset + 3] = (byte) ((in >> 24) & 0xff);
    b[offset + 2] = (byte) ((in >> 16) & 0xff);
    b[offset + 1] = (byte) ((in >> 8) & 0xff);
    b[offset] = (byte) (in & 0xff);
  }

  /**
   * Assuming an array in little endian order, build an short from 2 bytes
   *
   * @param in The input short
   * @param b an array of bytes
   * @param offset the offset into b where to start getting the short
   */
  public static void shortToArray(short in, byte[] b, int offset) {
    b[offset + 1] = (byte) (((int) in) >> 8 & 0xff);
    b[offset] = (byte) (in & 0xff);
  }

  /**
   * Assuming a array of bytes containing ASCII characters, build a string
   *
   * @param s The string to put in the array
   * @param b the array of bytes
   * @param offset the index to beginning of string
   * @param len Then number of bytes to put in the String
   */
  public static void stringToArray(String s, byte[] b, int offset, int len) {
    byte[] buf = s.getBytes();
    System.arraycopy(buf, 0, b, offset, Math.min(len, buf.length));
  }

  /**
   * Assuming a array of bytes containing ASCII characters, build a string
   *
   * @param s The string to put in the array
   * @param b the array of bytes
   * @param offset the index to beginning of string
   * @param len Then number of bytes to put in the String
   */
  public static void stringToArray(StringBuilder s, byte[] b, int offset, int len) {

    for (int i = 0; i < len; i++) {
      b[i + offset] = (byte) s.charAt(i);
    }
  }

  /**
   * Unit test main
   *
   * @param args Usual command line args
   */
  public static void main(String[] args) {
    Util.setModeGMT();
    Timestamp ts = FUtil.stringToTimestamp("2011-11-10 01:42:13.42");
    Util.prt(Util.ascdatetime2(ts.getTime()) + " " + ts.getTime());
    ts = FUtil.stringToTimestamp("2011-11-10 01:42:22.252743");
    Util.prt(Util.ascdatetime2(ts.getTime()) + " " + ts.getTime());

    byte[] b = new byte[4];
    b[0] = 1;
    b[1] = 2;
    b[2] = 3;
    b[3] = 4;
    Util.prt("1234x 67305985=" + intFromArray(b, 0));
    b[3] = -1;
    Util.prt("010203ffx -16580095=" + intFromArray(b, 0));
    Util.prt("0102x 513 short=" + shortFromArray(b, 0));
    Util.prt("3FFx -253 short=" + shortFromArray(b, 2));
    b[0] = 'a';
    b[1] = 'b';
    b[2] = 'c';
    b[3] = 'Q';
    Util.prt("abcQ=" + FUtil.stringFromArray(b, 0, 4));

  }
}
