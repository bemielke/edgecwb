/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.edge.alarm;

import gov.usgs.anss.dbtable.DBTable;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;

/**
 * Subscription.java If the name of the class does not match the name of the field in the class you
 * may need to modify the rs.getSTring in the constructor.
 *
 * This Subscription templace for creating a MySQL database object. It is not really needed by the
 * SubscriptionPanel which uses the DBObject system for changing a record in the database file.
 * However, you have to have this shell to create objects containing a MySQL record for passing
 * around and manipulating. There are two constructors normally given:
 * <br>
 * 1) passed a result set positioned to the MySQL record to decode
 * <br> 2) Passed all of the data arguments needed to build the same set of data.
 * <br>
 * The Subscription should be replaced with capitalized version (class name) for the file.
 * subscription should be replaced with the lower case version (for variables).
 *
 * Both the constructors use the "makeSubscription(list of data args) to set the local variables to
 * the value. The result set just uses the rs.get???("fieldname") to get the data from the database
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
public final class Subscription {//implements Comparator
  // static ArrayList enumName;   // this need to be populated in makeSubscription()

  /**
   * Creates a new instance of Subscription
   */
  private int ID;                   // This is the MySQL ID (should alwas be named "ID"
  private String subscription;             // This is the main key which should have same name
  // as the table which it is a key to. It is made up of the 
  // aliasID description and the event description

  // USER: All fields of file go here
  private String source;            // if not blank, then this is a default subsciption for a source.
  private int eventID;              // The event this subscription is for
  private int targetID;             // The person or alias subscribing to this event
  private Timestamp startTime;      // the time frame start for the default action
  private Timestamp endTime;        // The end time of the default action
  private int action;               // The action to take in the interval
  private int action2;              // The second action to take during the second interval
  private String nodeRegexp;        // Node limiting Regular expression
  private String processRegexp;     // process limiting Regular expression

  // Put in correct detail constructor here.  Use makeSubscription() by filling in all
  // the rs.get???("fieldName") for all of the arguments
  /**
   * Create a instance of this table row from a positioned result set
   *
   * @param rs The ResultSet already SELECTED
   * @exception SQLException SQLException If the ResultSet is Improper, network is down, or database
   * is down
   */
  public Subscription(ResultSet rs) throws SQLException {  //USER: add all data field here
    reload(rs);
  }

  /**
   * Create a instance of this table row from a positioned result set
   *
   * @param rs The ResultSet alreay SELECTED
   * @exception SQLException SQLException If the ResultSet is Improper, network is down, or database
   * is down
   */
  public Subscription(DBTable rs) throws SQLException {  //USER: add all data field here
    reload(rs);
  }

  public final void reload(ResultSet rs) throws SQLException {
    makeSubscription(rs.getInt("ID"), rs.getString("subscription"), rs.getString("source"),
            rs.getInt("eventID"), rs.getInt("targetID"), rs.getInt("action"),
            rs.getTimestamp("starttime"), rs.getTimestamp("endtime"),
            //rs.getTime("starttime2"), rs.getTime("endtime2"),
            rs.getInt("action2"), rs.getString("noderegexp"), rs.getString("processRegexp")
    // ,rs.getDouble(longitude)
    );
  }

  public final void reload(DBTable rs) throws SQLException {
    makeSubscription(rs.getInt("ID"), rs.getString("subscription"), rs.getString("source"),
            rs.getInt("eventID"), rs.getInt("targetID"), rs.getInt("action"),
            rs.getTimestamp("starttime"), rs.getTimestamp("endtime"),
            //rs.getTime("starttime2"), rs.getTime("endtime2"),
            rs.getInt("action2"), rs.getString("noderegexp"), rs.getString("processRegexp")
    // ,rs.getDouble(longitude)
    );
  }

  // Detail Constructor, match set up with makeSubscription(), this argument list should be
  // the same as for the result set builder as both use makeSubscription()
  /**
   * create a row record from individual variables
   *
   * @param inID The ID in the database, normally zero for this type of construction
   * @param loc The key value for the database (same a name of database table)
   * @param src The source to subscribe to
   * @param eID The eventID of the subscription (either source or eventID should be chosen)
   * @param tID The ID of the target
   * @param act The action to take
   * @param start The start time of day for the in day time for action
   * @param end The ending time of day for the action
   * @param act2 The out of day time action
   * @param nodeRE The node base regular expression
   * @param procRE The process based regular expression
   */
  public Subscription(int inID, String loc //USER: add fields, double lon
          ,
           String src, int eID, int tID, int act, Timestamp start, Timestamp end,
          //Timestamp start2, Timestamp end2,
          int act2, String nodeRE, String procRE
  ) {
    makeSubscription(inID, loc //, lon
            ,
             src, eID, tID, act, start, end, //start2, end2, 
            act2, nodeRE, procRE
    );
  }

  // This routine transfers to the local variables of this object all of the data variables
  // associated with this file.
  /**
   * internally set all of the field in our data to the passed data
   *
   * @param inID The row ID in the database
   * @param loc The key (same as table name)
   * @param src The source to subscribe to
   * @param eID The eventID of the subscription (either source or eventID should be chosen)
   * @param tID The ID of the target
   * @param act The action to take
   * @param start The start time of day for the in day time for action
   * @param end The ending time of day for the action
   * @param act2 The out of day time action
   * @param nodeRE The node base regular expression
   * @param procRE The process based regular expression
   *
   */
  private void makeSubscription(int inID, String loc //USER: add fields, double lon
          ,
           String src, int eID, int tID, int act, Timestamp start, Timestamp end, //Timestamp start2, Timestamp end2,
          int act2, String nodeRE, String procRE
  ) {
    ID = inID;
    subscription = loc;     // longitude = lon
    //USER:  Put asssignments to all fields from arguments here
    eventID = eID;
    targetID = tID;
    source = src;
    startTime = start;
    endTime = end;
    action = act;
    nodeRegexp = nodeRE;
    processRegexp = procRE;
    //startTime2=start2; endTime2=end2; 
    action2 = act2;

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
    return subscription;
  }

  /**
   * return the key name as the string
   *
   * @return the key name
   */
  @Override
  public String toString() {
    // If the ventPanel has been pared down, we do not get everything.  Make it rebuild it.
    if (eventID != 0 && Event.getItemWithID(eventID) == null) {
      Event.events = null;
      Event.makeEvents();
    }
    String s = ((eventID == 0) ? source : Event.getItemWithID(eventID)) + "->" + Target.getItemWithID(targetID);
    if (s.contains("null")) {
      s = s + " ??";
    }
    return s;
  }

  public String toString3() {
    return subscription + " source=" + source + " eID=" + eventID + " tID=" + targetID + " act=" + action + " act2=" + action2
            + " from " + startTime.toString().substring(11) + " to " + endTime.toString().substring(11) + " UTC ndRE=" + nodeRegexp + " prRE=" + processRegexp;
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
  public String getSubscription() {
    return subscription;
  }

  //USER:  All field getters here
  //  public double getLongitude() { return longitude;}
  public int getEventID() {
    return eventID;
  }

  public int getTargetID() {
    return targetID;
  }

  public int getAction() {
    return action;
  }

  public Timestamp getStartTime() {
    return startTime;
  }

  public Timestamp getEndTime() {
    return endTime;
  }

  public int getOtherwiseAction() {
    return action2;
  }

  public String getNodeRegexp() {
    return nodeRegexp;
  }

  public String getProcessRegexp() {
    return processRegexp;
  }

  //public TimestampgetStartTime2() {return startTime2;}
  //public TimestampgetEndTime2() {return endTime2;}
  public String getSource() {
    return source;
  }

  // This is needed if you need to implement Comparator
  /*  public class EquipCompare implements Comparator {
    public int compare(Object a, Object b) {
      return ((Subscription) a).getSubscription().compareTo(((Subscription) b).getSubscription());
    }
    public boolean equals(Object a, Object b) {
      if( ((Subscription)a).getSubscription().equals( ((Subscription) b).getSubscription())) return true;
      return false;
    }
//  }*/
}
