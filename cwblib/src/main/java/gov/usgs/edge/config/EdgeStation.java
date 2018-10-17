/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.edge.config;

import gov.usgs.anss.db.DBConnectionThread;
import gov.usgs.anss.util.FUtil;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;

/**
 * EdgeStation.java If the name of the class does not match the name of the field in the class you
 * may need to modify the rs.getSTring in the constructor.
 *
 * This EdgeStation templace for creating a database database object. It is not really needed by the
 * GSNStationPanel which uses the DBObject system for changing a record in the database file.
 * However, you have to have this shell to create objects containing a database record for passing
 * around and manipulating. There are two constructors normally given:
 * <br>
 * 1) passed a result set positioned to the database record to decode
 * <br> 2) Passed all of the data arguments needed to build the same set of data.
 * <br>
 * The EdgeStation should be replaced with capitalized version (class name) for the file. gsnstation
 * should be replaced with the lower case version (for variables).
 *
 * Both the constructors use the "makeGSNStation(list of data args) to set the local variables to
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
public final class EdgeStation {    //implements Comparator
  // static ArrayList enumName;   // this need to be populated in makeGSNStation()

  /**
   * Creates a new instance of EdgeStation
   */
  private int ID;                   // This is the database ID (should alwas be named "ID"
//  int edgeGSNStationID;
  private String gsnstation;             // This is the main key which should have same name
  // as the table which it is a key to.  That is
  // Table "category" should have key "category"

  // USER: All fields of file go here
  //  double longitude
  private static ArrayList lhtypeEnum;

  private String longhaulIP;
  private int longhaulport;
  private String longhaulprotocol;
  private int lhp;
  private String requestIP;
  private int requestport;
  private Timestamp disableRequestUntil;
  private String options;
  private int roleID;

  // Put in correct detail constructor here.  Use makeGSNStation() by filling in all
  // the rs.get???("fieldName") for all of the arguments
  /**
   * reload an instance with this Result set
   *
   * @param rs The result set to load
   * @throws SQLException if one occurs.
   */
  public void reload(ResultSet rs) throws SQLException {
    makeGSNStation(rs.getInt("ID"),
            rs.getString("gsnstation"),
            rs.getString("longhaulIP"),
            rs.getInt("longhaulport"),
            rs.getString("longhaulprotocol"),
            rs.getString("requestIP"),
            rs.getInt("requestport"),
            rs.getTimestamp("disableRequestUntil"),
            rs.getString("options"),
            rs.getInt("roleid")
    );

  }

  /**
   * Create a instance of this table row from a positioned result set
   *
   * @param rs The ResultSet already SELECTED
   * @exception SQLException SQLException If the ResultSet is Improper, network is down, or database
   * is down
   */
  public EdgeStation(ResultSet rs) throws SQLException {  //USER: add all data field here
    makeGSNStation(rs.getInt("ID"),
            rs.getString("gsnstation"),
            rs.getString("longhaulIP"),
            rs.getInt("longhaulport"),
            rs.getString("longhaulprotocol"),
            rs.getString("requestIP"),
            rs.getInt("requestport"),
            rs.getTimestamp("disableRequestUntil"),
            rs.getString("options"),
            rs.getInt("roleid")
    );
  }

  // Detail Constructor, match set up with makeGSNStation(), this argument list should be
  // the same as for the result set builder as both use makeGSNStation()
  /**
   * create a row record from individual variables
   *
   * @param inID The ID in the database, normally zero for this type of construction
   * @param ingsnstation The key value for the database (same a name of database table)
   * @param inlonghaulIP The IP address for the real-time long haul
   * @param inlonghaulport The port for real time protocol
   * @param inlonghaulprotocol The protocol from the enum for this link
   * @param inrequestIP the IP address to make re-requests for stations supporting one
   * @param inrequestport the port of the request service
   * @param indisableRequestUntil The date until requests are disabled
   * @param opt The options string for this protocol (often -throttle)
   * @param role The computer role this station should be received upon
   */
  public EdgeStation(int inID, String ingsnstation, //USER: add fields, double lon
          String inlonghaulIP,
          int inlonghaulport,
          String inlonghaulprotocol,
          String inrequestIP,
          int inrequestport,
          Timestamp indisableRequestUntil,
          String opt,
          int role
  ) {
    makeGSNStation(inID, ingsnstation, //, lon
            inlonghaulIP,
            inlonghaulport,
            inlonghaulprotocol,
            inrequestIP,
            inrequestport,
            indisableRequestUntil,
            opt,
            role
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
  private void makeGSNStation(int inID, String ingsnstation, //USER: add fields, double lon
          String inlonghaulIP,
          int inlonghaulport,
          String inlonghaulprotocol,
          String inrequestIP,
          int inrequestport,
          Timestamp indisableRequestUntil,
          String opt,
          int role
  ) {
    ID = inID;
    gsnstation = (ingsnstation + "            ").substring(0, 12);     // longitude = lon
    longhaulIP = inlonghaulIP;
    longhaulport = inlonghaulport;
    longhaulprotocol = inlonghaulprotocol;
    requestIP = inrequestIP;
    requestport = inrequestport;
    disableRequestUntil = indisableRequestUntil;
    options = opt;
    roleID = role;

    //USER:  Put asssignments to all fields from arguments here
    // ENUM example:
    if (lhtypeEnum == null) {
      lhtypeEnum = FUtil.getEnum(DBConnectionThread.getConnection(DBConnectionThread.getDBSchema()),
              "gsnstation", "longhaulprotocol");
    }
    lhp = FUtil.enumStringToInt(lhtypeEnum, longhaulprotocol);
    // Boolean example where ENUM "True", "False
    // cont40 = FUtil.isTrue(c40);          // c40 is a string with "true" or false

  }

  public String longhaulIP() {
    return longhaulIP;
  }

  public int longhaulport() {
    return longhaulport;
  }

  public int longhaulprotocol() {
    return lhp;
  }

  public String longhaulprotocolString() {
    return lhtypeEnum.get(lhp).toString();
  }

  public String requestIP() {
    return requestIP;
  }

  public int requestport() {
    return requestport;
  }

  public Timestamp disableRequestUntil() {
    return disableRequestUntil;
  }

  public int getRoleID() {
    return roleID;
  }

  public String getOptions() {
    return options;
  }

  /**
   * return the key name as the string
   *
   * @return the key name
   */
  @Override
  public String toString() {
    return gsnstation;
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
  //;
  public String getGSNStation() {
    return gsnstation;
  }

  //USER:  All field getters here
  //  public double getLongitude() { return longitude;}
  // This is needed if you need to implement Comparator
  /*  public class EquipCompare implements Comparator {
    public int compare(Object a, Object b) {
      return ((EdgeStation) a).getGSNStation().compareTo(((EdgeStation) b).getGSNStation());
    }
    public boolean equals(Object a, Object b) {
      if( ((EdgeStation)a).getGSNStation().equals( ((EdgeStation) b).getGSNStation())) return true;
      return false;
    }
//  }*/
}
