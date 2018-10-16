/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.edge.alarm;

import gov.usgs.anss.util.SeedUtil;
import gov.usgs.anss.util.Util;
import gov.usgs.anss.dbtable.DBTable;
import java.sql.*;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.SimpleTimeZone;

/**
 * Schedule.java If the name of the class does not match the name of the field in the class you may
 * need to modify the rs.getSTring in the constructor.
 *
 * This Schedule templace for creating a MySQL database object. It is not really needed by the
 * SchedulePanel which uses the DBObject system for changing a record in the database file. However,
 * you have to have this shell to create objects containing a MySQL record for passing around and
 * manipulating. There are two constructors normally given:
 * <br>
 * 1) passed a result set positioned to the MySQL record to decode
 * <br> 2) Passed all of the data arguments needed to build the same set of data.
 * <br>
 * The Schedule should be replaced with capitalized version (class name) for the file. schedule
 * should be replaced with the lower case version (for variables).
 *
 * Both the constructors use the "makeSchedule(list of data args) to set the local variables to the
 * value. The result set just uses the rs.get???("fieldname") to get the data from the database
 * where the other passes the arguments from the caller.
 *
 * <br> Notes on Enums :
 * <br> * data class should have code like :
 * <br> import java.util.ArrayList; /// Import for Enum manipulation
 * <br> .
 * <br> static ArrayList fieldEnum; // This list will have Strings corresponding to enum
 * <br> .
 * <br> .
 * <br> // To convert an enum we need to create the fieldEnum ArrayList. Do only once(static)
 * <br> if(fieldEnum == null) fieldEnum = FUtil.getEnum(UC.getConnection(),"table","fieldName");
 * <br>
 * <br> // Get the int corresponding to an Enum String for storage in the data base and
 * <br> // the index into the JComboBox representing this Enum
 * <br> localVariable = FUtil.enumStringToInt(fieldEnum, enumString);
 * <br> Created on July 8, 2002, 1:23 PM
 *
 * @author D.C. Ketchum
 */
public final class Schedule //implements Comparator
{   // static ArrayList enumName;   // this need to be populated in makeSchedule()

  public static SimpleTimeZone denverTimeZone;

  /**
   * Creates a new instance of Schedule
   */
  private int ID;                   // This is the MySQL ID (should alwas be named "ID"
  private String schedule;             // This is the main key which should have same name
  // as the table which it is a key to.  That is
  // Table "category" should have key "category"

  // USER: All fields of file go here
  //  double longitude
  private int aliasID;              // The ID being scheduled (the permanent ID like 'duty'
  private int onlineID;             // The alias ID that the 'aliasID' will target if in the right period
  private int otherwiseID;          // The julian ID for out of hours on the schedule
  private int julian;               // A Julian day, if zero this is a wild card day
  private String whyjulian;       // What is significance of julian day?
  private int dowMask;            // A mask based on days of week this schedule is in effect
  private Timestamp startTime;           // in ms since midnight of the start of this period.
  private Timestamp endTime;             // In ms since midnight of the end of this period

  // Put in correct detail constructor here.  Use makeSchedule() by filling in all
  // the rs.get???("fieldName") for all of the arguments
  /**
   * Create a instance of this table row from a positioned result set
   *
   * @param rs The ResultSet already SELECTED
   * @exception SQLException SQLException If the ResultSet is Improper, network is down, or database
   * is down
   */
  public Schedule(ResultSet rs) throws SQLException {  //USER: add all data field here
    makeSchedule(rs.getInt("ID"), rs.getString("schedule"), rs.getInt("aliasID"), rs.getInt("onlineID"),
            rs.getInt("otherwiseID"), rs.getInt("julian"), rs.getString("whyjulian"),
            rs.getInt("dayOfWeek"), rs.getTimestamp("startTime"), rs.getTimestamp("endTime")
    // ,rs.getDouble(longitude)
    );
  }

  // Put in correct detail constructor here.  Use makeSchedule() by filling in all
  // the rs.get???("fieldName") for all of the arguments
  /**
   * Create a instance of this table row from a positioned result set
   *
   * @param rs The ResultSet alreay SELECTED
   * @exception SQLException SQLException If the ResultSet is Improper, network is down, or database
   * is down
   */
  public Schedule(DBTable rs) throws SQLException {  //USER: add all data field here
    reload(rs);
  }

  /**
   * Create a instance of this table row from a positioned result set
   *
   * @param rs The ResultSet alreay SELECTED
   * @exception SQLException SQLException If the ResultSet is Improper, network is down, or database
   * is down
   */
  public final void reload(DBTable rs) throws SQLException {  //USER: add all data field here
    makeSchedule(rs.getInt("ID"), rs.getString("schedule"), rs.getInt("aliasID"), rs.getInt("onlineID"),
            rs.getInt("otherwiseID"), rs.getInt("julian"), rs.getString("whyjulian"),
            rs.getInt("dayOfWeek"), rs.getTimestamp("startTime"), rs.getTimestamp("endTime")
    // ,rs.getDouble(longitude)
    );
  }

  /**
   * Create a instance of this table row from a positioned result set
   *
   * @param rs The ResultSet alreay SELECTED
   * @exception SQLException SQLException If the ResultSet is Improper, network is down, or database
   * is down
   */
  public final void reload(ResultSet rs) throws SQLException {  //USER: add all data field here
    makeSchedule(rs.getInt("ID"), rs.getString("schedule"), rs.getInt("aliasID"), rs.getInt("onlineID"),
            rs.getInt("otherwiseID"), rs.getInt("julian"), rs.getString("whyjulian"),
            rs.getInt("dayOfWeek"), rs.getTimestamp("startTime"), rs.getTimestamp("endTime")
    // ,rs.getDouble(longitude)
    );
  }

  // Detail Constructor, match set up with makeSchedule(), this argument list should be
  // the same as for the result set builder as both use makeSchedule()
  /**
   * create a row record from individual variables
   *
   * @param inID The ID in the database, normally zero for this type of construction
   * @param sched The key value for the database (same a name of database table)
   * @param alias The aliasID being scheduled (line SEISMIC DUTY)
   * @param online The targetID of the entity for IN HOURS scheduling
   * @param other The target ID of the entity for OUT OF HOURS scheduling
   * @param jul The julian date this schedule applies, zero of if this is a day of week schedule
   * @param why If julian is non zero, a short text explaining the julian override (Christmas, etc).
   * @param dow The day of week mask that this schedule applies on (sunday=1, mon=2, tues=4, etc.)
   * @param start The start time of IN HOURS (date portion ignored)
   * @param end The end time of IN HOURS (date portion ignored)
   */
  public Schedule(int inID, String sched, //USER: add fields, double lon
          int alias, int online, int other, int jul, String why, int dow, Timestamp start, Timestamp end
  ) {
    makeSchedule(inID, sched, //, lon
            alias, online, other, jul, why, dow, start, end
    );
  }

  // This routine transfers to the local variables of this object all of the data variables
  // associated with this file.
  /**
   * internally set all of the field in our data to the passed data
   *
   * @param inID The row ID in the database
   * @param sched The key (same as table name)
   * @param alias The aliasID being scheduled (line SEISMIC DUTY)
   * @param online The targetID of the entity for IN HOURS scheduling
   * @param other The target ID of the entity for OUT OF HOURS scheduling
   * @param why If julian is non zero, a short text explaining the julian override (Christmas, etc).
   * @param jul The julian date this schedule applies, zero of if this is a day of week schedule
   * @param dow The day of week mask that this schedule applies on (sunday=1, mon=2, tues=4, etc.)
   * @param start The start time of IN HOURS (date portion ignored)
   * @param end The end time of IN HOURS (date portion ignored)
   *
   */
  private void makeSchedule(int inID, String sched, //USER: add fields, double lon
          int alias, int online, int other, int jul, String why, int dow, Timestamp start, Timestamp end
  ) {
    ID = inID;
    schedule = sched;     // longitude = lon
    //USER:  Put asssignments to all fields from arguments here
    aliasID = alias;
    onlineID = online;
    julian = jul;
    whyjulian = why;
    dowMask = dow;
    startTime = start;
    endTime = end;
    otherwiseID = other;
    // ENUM example:
    //if(qtypeEnum == null) qtypeEnum=FUtil.getEnum(UC.getConnection(), "nsnstation","quanterraType");
    //qtype = FUtil.enumStringToInt(qtypeEnum, st); 
    // Boolean example where ENUM "True", "False
    // cont40 = FUtil.isTrue(c40);          // c40 is a string with "true" or false

  }

  /**
   * return the key name as the string
   *
   * @return the key name
   */
  public String toString2() {
    return schedule;
  }

  /**
   * return the key name as the string
   *
   * @return the key name
   */
  @Override
  public String toString() {
    return schedule;
  }
  // getter

  // standard getters
  /**
   * get the database ID for the row
   *
   * @return The database ID for the row
   */
  public int getID() {
    return ID;
  }

  /**
   * get the key name for the row
   *
   * @return The key name string for the row
   */
  public String getSchedule() {
    return schedule;
  }

  //USER:  All field getters here
  //  public double getLongitude() { return longitude;}
  /**
   * get the aliasID being schedule
   *
   * @return the alias id being scheduled.
   */
  public int getAliasID() {
    return aliasID;
  }

  /**
   * get the target ID of the IN HOURS being schedule
   *
   * @return the targetID of the IN HOURS being scheduled.
   */
  public int getOnlineID() {
    return onlineID;
  }

  /**
   * get the target ID of the OUT OF HOURS being schedule
   *
   * @return the targetID of the OUT OF HOURS being scheduled.
   */
  public int getOtherwiseID() {
    return otherwiseID;
  }

  /**
   * the mask of the days of week if this is a dow type schedule.
   *
   * @return the mask (1 bit = Sun, 2=Mon, 4=Tues, 8=Wed, etc.)
   */
  public int getDayOfWeekMask() {
    return dowMask;
  }

  /**
   * get the start time of IN HOURS
   *
   * @return the start time of IN HOURS (ignore the date portion)
   */
  public Timestamp getStartTime() {
    return startTime;
  }

  /**
   * get the end time of OUT OF HOURS
   *
   * @return the end time of OUT OF HOURS (ignore the date portion)
   */
  public Timestamp getEndTime() {
    return endTime;
  }

  // This is needed if you need to implement Comparator
  /*  public class EquipCompare implements Comparator {
    public int compare(Object a, Object b) {
      return ((Schedule) a).getSchedule().compareTo(((Schedule) b).getSchedule());
    }
    public boolean equals(Object a, Object b) {
      if( ((Schedule)a).getSchedule().equals( ((Schedule) b).getSchedule())) return true;
      return false;
    }
//  }*/
  /**
   * this method checks a target ID in the schedule and returns the possibly modified target based
   * on the schedule
   *
   * @param targetID The input target ID to check in the schedule (normally a alias like SEISMIC
   * DUTY). Algorithm if a julian day is set for this day and target, use it as the schedule if no
   * julian day is set, use the days-of-the-week and target ID to find an applicable schedule if
   * neither is found, return input ID. The return is further affected by the hours in the schedule
   * allowing an "in hours" and "out of hours" ID to be selected.
   * @param C A connection to the database to use
   * @return The target id of the target or alias scheduled or if no schedule applies the input
   * target ID
   */
  static public int checkSchedule(int targetID, Connection C) {
    GregorianCalendar now = new GregorianCalendar();
    if (denverTimeZone == null) {
      denverTimeZone = new SimpleTimeZone(
              -7 * 3600000, // Normally 7 hours earlier than GMT
              "America/Denver",
              Calendar.MARCH, 8, -Calendar.SUNDAY, 7200000, // 2nd Sunday in March at 2:00 am
              Calendar.NOVEMBER, 1, -Calendar.SUNDAY, 7200000, // first sunday in November
              3600000);   // Jump one hour ahead.
    }
    now.setTimeZone(denverTimeZone);
    long n = now.get(Calendar.HOUR_OF_DAY) * 3600000L + now.get(Calendar.MINUTE) * 60000L + now.get(Calendar.SECOND) * 1000L;
    int julian = SeedUtil.toJulian(now);        // todays julian date
    try {
      Statement stmt = C.createStatement();
      // Are there any julian day specific ones for this
      ResultSet rs = stmt.executeQuery("SELECT * FROM alarm.schedule WHERE julian=" + julian + " AND aliasID=" + targetID);
      if (rs.next()) {
        // The start and end time for the schedule need to be evaluated in local time.  Computer from UTC time and timezone offset now
        long start = (rs.getTimestamp("starttime").getTime()/*+denverTimeZone.getOffset(now.getTimeInMillis())*/) % 86400000L;
        long end = (rs.getTimestamp("endtime").getTime()/*+denverTimeZone.getOffset(now.getTimeInMillis())*/) % 86400000L + 999L; // only reports second so round up fraction
        Util.prt("      Eval schedule hours start=" + start + " now=" + n + " end=" + end + (n >= start && n < end ? " IS ONLINE" : " IS OTHERWISE"));
        if (n >= start && n < end) {
          return rs.getInt("onlineID");
        } else {
          return rs.getInt("otherwiseID");
        }
      }

      // There is no julian day overriding rule.  Now we need to check the day of week
      int todayMask = 1 << (now.get(Calendar.DAY_OF_WEEK) - 1);
      rs = stmt.executeQuery("SELECT * from alarm.schedule WHERE julian=0 and aliasid=" + targetID + " AND (dayofweek&" + todayMask + ")!=0");
      if (rs.next()) {
        long start = (rs.getTimestamp("starttime").getTime()/*+denverTimeZone.getOffset(now.getTimeInMillis())*/) % 86400000L;
        long end = (rs.getTimestamp("endtime").getTime()/*+denverTimeZone.getOffset(now.getTimeInMillis())*/) % 86400000L + 999L; // only reports second so round up fraction
        Util.prt("      Eval schedule hours start=" + start + " now=" + n + " end=" + end + (n >= start && n < end ? " IS ONLINE" : " IS OTHERWISE"));
        if (n >= start && n < end) {
          return rs.getInt("onlineID");
        } else {
          return rs.getInt("otherwiseID");
        }
      }
    } catch (SQLException e) {
      Util.SQLErrorPrint(e, "Getting the schedule for target=" + targetID);
    }
    return targetID;
  }
}
