/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.edge.config;

import gov.usgs.anss.db.DBConnectionThread;
import gov.usgs.anss.util.Util;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;

/**
 * SubspaceEvent.java If the name of the class does not match the name of the field in the class you
 * may need to modify the rs.getSTring in the constructor.
 *
 * This SubspaceEvent template for creating a database database object. It is not really needed by
 * the SubspaceEventPanel which uses the DBObject system for changing a record in the database file.
 * However, you have to have this shell to create objects containing a database record for passing
 * around and manipulating. There are two constructors normally given:
 * <br>
 * 1) passed a result set positioned to the database record to decode
 * <br> 2) Passed all of the data arguments needed to build the same set of data.
 * <br>
 * The SubspaceEvent should be replaced with capitalized version (class name) for the file.
 * subspaceEvent should be replaced with the lower case version (for variables).
 *
 * Both the constructors use the "makeSubspaceEvent(list of data args) to set the local variables to
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
public final class SubspaceEvent {    //implements Comparator
  // static ArrayList enumName;   // this need to be populated in makeSubspaceEvent()

  public static ArrayList<SubspaceEvent> events;
  /**
   * Creates a new instance of SubspaceEvent
   */
  private int ID;
  private String subspaceEvent;             // This is the main key which should have same name
  // as the table which it is a key to.  That is
  // Table "category" should have key "category"

  // USER: All fields of file go here
  //  double longitude
  private int areaID;
  private double latitude, longitude, depth, magnitude;
  private String magMethod;
  private long originTime;

  // Put in correct detail constructor here.  Use makeSubspaceEvent() by filling in all
  // the rs.get???("fieldName") for all of the arguments
  /**
   * Create a instance of this table row from a positioned result set
   *
   * @param rs The ResultSet already SELECTED
   * @exception SQLException SQLException If the ResultSet is Improper, network is down, or database
   * is down
   */
  public SubspaceEvent(ResultSet rs) throws SQLException {  //USER: add all data field here
    reload(rs);
  }

  /**
   * reload the object data from a given result set
   *
   * @param rs The ResultSet to load the object from
   * @throws SQLException If one is thrown
   */
  public final void reload(ResultSet rs) throws SQLException {
    makeSubspaceEvent(rs.getInt("ID"), rs.getString("subspaceEvent"),
            rs.getInt("areaid"), rs.getDouble("latitude"), rs.getDouble("longitude"),
            rs.getDouble("depth"), rs.getDouble("magnitude"), rs.getString("magmethod"), rs.getTimestamp("origintime").getTime()
    // ,rs.getDouble(longitude)
    );
  }

  // Detail Constructor, match set up with makeSubspaceEvent(), this argument list should be
  // the same as for the result set builder as both use makeSubspaceEvent()
  /**
   * create a row record from individual variables
   *
   * @param inID The ID in the database, normally zero for this type of construction
   * @param loc The key value for the database (same a name of database table)
   * @param areaID
   * @param lat
   * @param lng
   * @param dep
   * @param mag
   * @param magType
   * @param origin
   */
  public SubspaceEvent(int inID, String loc //USER: add fields, double lon
          ,
           int areaID, double lat, double lng, double dep, double mag, String magType, long origin
  ) {
    makeSubspaceEvent(inID, loc //, lon
            ,
             areaID, lat, lng, dep, mag, magType, origin
    );
  }

  // This routine transfers to the local variables of this object all of the data variables
  // associated with this file.
  /**
   * internally set all of the field in our data to the passed data
   *
   * @param inID The row ID in the database
   * @param loc The key (same as table name)
   *
   */
  private void makeSubspaceEvent(int inID, String loc //USER: add fields, double lon
          ,
           int areaID, double lat, double lng, double dep, double mag, String magType, long origin
  ) {
    ID = inID;
    subspaceEvent = loc;     // longitude = lon
    //USER:  Put asssignments to all fields from arguments here

    // ENUM example:
    //if(qtypeEnum == null) qtypeEnum=FUtil.getEnum(JDBConnection.getConnection("DATABASE"), "nsnstation","quanterraType");
    //qtype = FUtil.enumStringToInt(qtypeEnum, st); 
    // Boolean example where ENUM "True", "False
    // cont40 = FUtil.isTrue(c40);          // c40 is a string with "true" or false
    this.areaID = areaID;
    latitude = lat;
    longitude = lng;
    depth = dep;
    magnitude = mag;
    magMethod = magType;
    originTime = origin;

  }

  /**
   * return the key name as the string
   *
   * @return the key name
   */
  public String toString2() {
    return subspaceEvent + " " + Util.ascdatetime2(originTime) + " " + latitude + " " + longitude + " " + depth + " " + magnitude + " " + magMethod;
  }

  /**
   * return the key name as the string
   *
   * @return the key name
   */
  @Override
  public String toString() {
    return subspaceEvent;
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
  public String getSubspaceEvent() {
    return subspaceEvent;
  }

  public double getLatitude() {
    return latitude;
  }

  public double getLongitude() {
    return longitude;
  }

  public double getDepth() {
    return depth;
  }

  public String getMagMethod() {
    return magMethod;
  }

  public double getMagnitude() {
    return magnitude;
  }

  public long getOriginTime() {
    return originTime;
  }

  public int getAreaID() {
    return areaID;
  }

  //USER:  All field getters here
  //  public double getLongitude() { return longitude;}
  // This is needed if you need to implement Comparator
  /*  public class EquipCompare implements Comparator {
    public int compare(Object a, Object b) {
      return ((SubspaceEvent) a).getSubspaceEvent().compareTo(((SubspaceEvent) b).getSubspaceEvent());
    }
    public boolean equals(Object a, Object b) {
      if( ((SubspaceEvent)a).getSubspaceEvent().equals( ((SubspaceEvent) b).getSubspaceEvent())) return true;
      return false;
    }
//  }*/
  public static SubspaceEvent getSubspaceEventWithID(int ID) {
    if (events == null) {
      makeEvents();
    }
    for (SubspaceEvent event : events) {
      if (event.getID() == ID) {
        return event;
      }
    }
    return null;
  }
  public static void makeEvents() {
    try {
      Statement stmt;
      if (DBConnectionThread.getConnection(DBConnectionThread.getDBSchema()) != null) {
        stmt = DBConnectionThread.getConnection(DBConnectionThread.getDBSchema()).createStatement();   // used for query
      } else {
        stmt = DBConnectionThread.getConnection("DBServer").createStatement();    // This is for call in the PickManager in QueryMom
      }
      makeEvents(stmt );
    } catch (SQLException e) {
      Util.SQLErrorPrint(e, "makeSubspaceEvents() on table SQL failed");
    }
  }
  // This routine should only need tweeking if key field is not same as table name
  public static void makeEvents(Statement stmt) {
    if (events != null) {
      return;
    }
    events = new ArrayList<SubspaceEvent>(100);
    try {
      String s = "SELECT * FROM edge.subspaceevent ORDER BY subspaceevent;";
      try (ResultSet rs = stmt.executeQuery(s)) {
        while (rs.next()) {
          SubspaceEvent event = new SubspaceEvent(rs);
          //        Util.prt("MakeSubspaceEvents() i="+v.size()+" is "+event.getGroups());
          events.add(event);
        }
      }
    } catch (SQLException e) {
      Util.SQLErrorPrint(e, "makeSubspaceEvents() on table SQL failed");
    }
  }
}
