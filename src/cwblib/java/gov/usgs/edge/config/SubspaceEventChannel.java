/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.edge.config;

import gov.usgs.anss.db.DBConnectionThread;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.sql.SQLException;
import gov.usgs.anss.util.Util;
import java.sql.Statement;
import java.util.ArrayList;

/**
 * SubspaceChannel.java If the name of the class does not match the name of the field in the class
 * you may need to modify the rs.getSTring in the constructor.
 *
 * This SubspaceChannel template for creating a database database object. It is not really needed by
 * the SubspaceChannelPanel which uses the DBObject system for changing a record in the database
 * file. However, you have to have this shell to create objects containing a database record for
 * passing around and manipulating. There are two constructors normally given:
 * <br>
 * 1) passed a result set positioned to the database record to decode
 * <br> 2) Passed all of the data arguments needed to build the same set of data.
 * <br>
 * The SubspaceChannel should be replaced with capitalized version (class name) for the file.
 * subspaceChannel should be replaced with the lower case version (for variables).
 *
 * Both the constructors use the "makeSubspaceChannel(list of data args) to set the local variables
 * to the value. The result set just uses the rs.get???("fieldname") to get the data from the
 * database where the other passes the arguments from the caller.
 *
 * <br> Notes on Enums :
 * <br> * data class should have code like :
 * <br> import java.util.ArrayList; /// Import for Enum manipulation
 * <br> .
 * <br> static ArrayList fieldEnum; // This list will have Strings corresponding to enum
 * <br> .
 * <br> .
 * <br> // To convert an enum we need to create the fieldEnum ArrayList. Do only once(static)
 * <br> if(fieldEnum == null) fieldEnum =
 * FUtil.getEnum(JDBConnection.getConnection("DATABASE"),"table","fieldName");
 * <br>
 * <br> // Get the int corresponding to an Enum String for storage in the data base and
 * <br> // the index into the JComboBox representing this Enum
 * <br> localVariable = FUtil.enumStringToInt(fieldEnum, enumString);
 * <br> Created on July 8, 2002, 1:23 PM
 *
 * @author D.C. Ketchum
 */
public final class SubspaceEventChannel {    //implements Comparator
  // static ArrayList enumName;   // this need to be populated in makeSubspaceChannel()

  /**
   * Creates a new instance of SubspaceChannel
   */
  private int ID;                   // This is the database ID (should alwas be named "ID"
  //String subspaceChannel;             // This is the main key which should have same name
  // as the table which it is a key to.  That is
  // Table "category" should have key "category"

  // USER: All fields of file go here
  //  double longitude
  private int eventID, channelID, disabled,reference; // reference to ssevent, sschannel table
  private String phase;
  private Timestamp arrivalTime = new Timestamp(100000L);

  /**
   * Create a instance of this table row from a positioned result set
   *
   * @param rs The ResultSet already SELECTED
   * @exception SQLException SQLException If the ResultSet is Improper, network is down, or database
   * is down
   */
  public SubspaceEventChannel(ResultSet rs) throws SQLException {  //USER: add all data field here
    reload(rs);
  }

  /**
   * reload the object data from a given result set
   *
   * @param rs The ResultSet to load the object from
   * @throws SQLException If one is thrown
   */
  public final void reload(ResultSet rs) throws SQLException {
    makeSubspaceChannel(rs.getInt("ID"), rs.getInt("eventid"), rs.getInt("channelid"),
             rs.getString("phase"), rs.getTimestamp("arrivaltime"), rs.getInt("disable"), rs.getInt("reference"));
  }

  /**
   * Reload the data into a SubspaceChannel from a ResultSet
   *
   * @param inID The ID in the database, normally zero for this type of construction
   * @param evtid The key value for the database (same a name of database table)
   * @param chID Channel ID of SubspaceChannel
   * @param ph Phase name
   * @param arrival Arrival time of the phase
   * @param disable If non-zero, this channel is disabled for this event
   * @param ref
   */
  public final void reload(int inID, int evtid //USER: add fields, double lon
          ,
           int chID, String ph, Timestamp arrival, int disable, int ref) {
    makeSubspaceChannel(inID, evtid //, lon
            ,
             chID, ph, arrival, disable, ref);
  }

  /**
   * create a row record from individual variables
   *
   * @param inID The ID in the database, normally zero for this type of construction
   * @param evtid The key value for the database (same a name of database table)
   * @param chID Channel ID of SubspaceChannel
   * @param ph Phase name
   * @param arrival Arrival time of the phase
   * @param disable If non-zero, this channel is disabled for this event
   * @param ref if not zero, its a reference channel
   */
  public SubspaceEventChannel(int inID, int evtid //USER: add fields, double lon
          ,
           int chID, String ph, Timestamp arrival, int disable, int ref) {
    makeSubspaceChannel(inID, evtid //, lon
            ,chID, ph, arrival, disable, ref);
  }

  /**
   * internally set all of the field in our data to the passed data
   *
   * @param inID The row ID in the database
   * @param loc The key (same as table name)
   *
   */
  private void makeSubspaceChannel(int inID, int evtid //USER: add fields, double lon
          ,
           int chID, String ph, Timestamp arrival, int disable, int ref) {
    ID = inID;
    eventID = evtid;     // longitude = lon
    //USER:  Put asssignments to all fields from arguments here

    // ENUM example:
    //if(qtypeEnum == null) qtypeEnum=FUtil.getEnum(JDBConnection.getConnection("DATABASE"), "nsnstation","quanterraType");
    //qtype = FUtil.enumStringToInt(qtypeEnum, st); 
    // Boolean example where ENUM "True", "False
    // cont40 = FUtil.isTrue(c40);          // c40 is a string with "true" or false
    phase = ph;
    channelID = chID;
    arrivalTime.setTime(arrival.getTime());
    disabled = disable;
    reference = ref;

  }

  /**
   * return the key name as the string
   *
   * @return the key name
   */
  public String toString2() {
    return eventID + " " + arrivalTime + " " + phase + " disable=" + (isDisabled());
  }

  /**
   * return the key name as the string
   *
   * @return the key name
   */
  @Override
  public String toString() {
    return eventID + " " + arrivalTime + " " + phase;
  }
  // getters
  StringBuilder sb = new StringBuilder(10);

  public StringBuilder getFixedToString() {
    sb.append(phase).append(" ").append(Util.ascdatetime(arrivalTime.getTime()));
    return sb;
  }

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
  //public String getSubspaceChannel() {return subspaceChannel;}

  //USER:  All field getters here
  //  public double getLongitude() { return longitude;}
  public String getPhase() {
    return phase;
  }

  public boolean isDisabled() {
    return disabled != 0;
  }

  public int getEventID() {
    return eventID;
  }

  public int getChannelID() {
    return channelID;
  }
  
  public boolean isReference() {
    return reference != 0;
  }

  public void setDisabled(boolean b) {
    disabled = (b ? 1 : 0);
  }
  public void setReference(boolean b) {
    reference = (b ? 1 : 0);
  }
  
  public Timestamp getArrivalTime() {
    return arrivalTime;
  }

  // This is needed if you need to implement Comparator
  /*  public class EquipCompare implements Comparator {
    public int compare(Object a, Object b) {
      return ((SubspaceChannel) a).getSubspaceChannel().compareTo(((SubspaceChannel) b).getSubspaceChannel());
    }
    public boolean equals(Object a, Object b) {
      if( ((SubspaceChannel)a).getSubspaceChannel().equals( ((SubspaceChannel) b).getSubspaceChannel())) return true;
      return false;
    }
//  }*/
  public static ArrayList<SubspaceEventChannel> channelEvents;

  public static SubspaceEventChannel getSubspaceEventChannelWithID(int ID) {
    if (channelEvents == null) {
      makeSubspaceEventChannels();
    }
    for (SubspaceEventChannel event : channelEvents) {
      if (event.getID() == ID) {
        return event;
      }
    }
    return null;
  }

  // This routine should only need tweeking if key field is not same as table name
    public static void makeSubspaceEventChannels() {
    try {
      Statement stmt;
      if (DBConnectionThread.getConnection(DBConnectionThread.getDBSchema()) != null) {
        stmt = DBConnectionThread.getConnection(DBConnectionThread.getDBSchema()).createStatement();   // used for query
      } else {
        stmt = DBConnectionThread.getConnection("DBServer").createStatement();    // This is for call in the PickManager in QueryMom
      }
      makeSubspaceEventChannels(DBConnectionThread.getConnection(DBConnectionThread.getDBSchema()).createStatement() );
    } catch (SQLException e) {
      Util.SQLErrorPrint(e, "makeSubspaceEventChannels() on table SQL failed");
    }
  }
  public static void makeSubspaceEventChannels(Statement stmt) {
    if (channelEvents != null) {
      return;
    }
    channelEvents = new ArrayList<SubspaceEventChannel>(100);
    try {
      String s = "SELECT * FROM edge.subspaceeventchannel ORDER BY channelid,eventid;";
      try (ResultSet rs = stmt.executeQuery(s)) {
        while (rs.next()) {
          SubspaceEventChannel event = new SubspaceEventChannel(rs);
          //        Util.prt("MakeSubspaceEvents() i="+v.size()+" is "+event.getGroups());
          channelEvents.add(event);
        }
      }
    } catch (SQLException e) {
      Util.SQLErrorPrint(e, "makeSubspaceEventChannels() on table SQL failed");
    }
  }
}
