/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.edge.config;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;

/**
 * RequestStation.java If the name of the class does not match the name of the field in the class
 * you may need to modify the rs.getSTring in the constructor.
 *
 * This RequestStation template for creating a database database object. It is not really needed by
 * the RequestStationPanel which uses the DBObject system for changing a record in the database
 * file. However, you have to have this shell to create objects containing a database record for
 * passing around and manipulating. There are two constructors normally given:
 * <br>
 * 1) passed a result set positioned to the database record to decode
 * <br> 2) Passed all of the data arguments needed to build the same set of data.
 * <br>
 * The RequestStation should be replaced with capitalized version (class name) for the file.
 * requestStation should be replaced with the lower case version (for variables).
 *
 * Both the constructors use the "makeRequestStation(list of data args) to set the local variables
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
 * FUtil.getEnum(DBConnectionThread.getConnection("DATABASE"),"table","fieldName");
 * <br>
 * <br> // Get the int corresponding to an Enum String for storage in the data base and
 * <br> // the index into the JComboBox representing this Enum
 * <br> localVariable = FUtil.enumStringToInt(fieldEnum, enumString);
 * <br> Created on July 8, 2002, 1:23 PM
 *
 * @author D.C. Ketchum
 */
public final class RequestStation {    //implements Comparator
  // static ArrayList enumName;   // this need to be populated in makeRequestStation()

  /**
   * Creates a new instance of RequestStation
   */
  private int ID;                   // This is the database ID (should alwas be named "ID"
  private String requestStation;             // This is the main key which should have same name
  // as the table which it is a key to.  That is
  // Table "category" should have key "category"

  // USER: All fields of file go here
  //  double longitude
  private int requestID;    // ID in table requesttype
  private String fetchType;
  private int throttle;
  private String requestIP;
  private int requestPort;
  private String channelRE;
  private Timestamp disableRequestUntil;

  // Put in correct detail constructor here.  Use makeRequestStation() by filling in all
  // the rs.get???("fieldName") for all of the arguments
  /**
   * Create a instance of this table row from a positioned result set
   *
   * @param rs The ResultSet already SELECTED
   * @exception SQLException SQLException If the ResultSet is Improper, network is down, or database
   * is down
   */
  public RequestStation(ResultSet rs) throws SQLException {  //USER: add all data field here
    makeRequestStation(rs.getInt("ID"), rs.getString("requestStation"),
            rs.getInt("requestid"), rs.getString("fetchtype"), rs.getInt("throttle"), rs.getString("requestip"),
            rs.getInt("requestport"), rs.getString("channelre"), rs.getTimestamp("disableRequestUntil")
    // ,rs.getDouble(longitude)
    );
  }

  // Detail Constructor, match set up with makeRequestStation(), this argument list should be
  // the same as for the result set builder as both use makeRequestStation()
  /**
   * create a row record from individual variables
   *
   * @param inID The ID in the database, normally zero for this type of construction
   * @param loc The key value for the database (same a name of database table)
   * @param rid request ID
   * @param ftype fetch type
   * @param throt throttle bps
   * @param rip request IP
   * @param rport request port
   * @param chanre channel regexp for matching to fetches
   * @param until disable until this date
   */
  public RequestStation(int inID, String loc //USER: add fields, double lon
          ,
           int rid, String ftype, int throt, String rip, int rport, String chanre, Timestamp until
  ) {
    makeRequestStation(inID, loc //, lon
            ,
             rid, ftype, throt, rip, rport, chanre, until
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
  private void makeRequestStation(int inID, String loc //USER: add fields, double lon
          ,
           int rid, String ftype, int throt, String rip, int rport, String chanre, Timestamp until
  ) {
    ID = inID;
    requestStation = loc;     // longitude = lon
    //USER:  Put asssignments to all fields from arguments here

    // ENUM example:
    //if(qtypeEnum == null) qtypeEnum=FUtil.getEnum(DBConnectionThread.getConnection("DATABASE"), "nsnstation","quanterraType");
    //qtype = FUtil.enumStringToInt(qtypeEnum, st);
    // Boolean example where ENUM "True", "False
    // cont40 = FUtil.isTrue(c40);          // c40 is a string with "true" or false
    requestID = rid;
    fetchType = ftype;
    throttle = throt;
    requestIP = rip;
    requestPort = rport;
    channelRE = chanre;
    disableRequestUntil = until;

  }

  /**
   * return the key name as the string
   *
   * @return the key name
   */
  public String toString2() {
    return requestStation;
  }

  /**
   * return the key name as the string
   *
   * @return the key name
   */
  @Override
  public String toString() {
    return requestStation + "-" + fetchType;
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
  public String getRequestStation() {
    return requestStation;
  }

  //USER:  All field getters here
  //  public double getLongitude() { return longitude;}
  public int getRequestID() {
    return requestID;
  }

  public String getFetchType() {
    return fetchType;
  }

  public int getThrottle() {
    return throttle;
  }

  public String getRequestIP() {
    return requestIP;
  }

  public int getRequestPort() {
    return requestPort;
  }

  public String getChannelRE() {
    return channelRE;
  }

  public Timestamp getDisableUtil() {
    return disableRequestUntil;
  }

  // This is needed if you need to implement Comparator
  /*  public class EquipCompare implements Comparator {
    public int compare(Object a, Object b) {
      return ((RequestStation) a).getRequestStation().compareTo(((RequestStation) b).getRequestStation());
    }
    public boolean equals(Object a, Object b) {
      if( ((RequestStation)a).getRequestStation().equals( ((RequestStation) b).getRequestStation())) return true;
      return false;
    }
//  }*/
}
