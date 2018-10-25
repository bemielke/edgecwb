/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
/**
 *
 * @author  ketchum
 * This Vsat template for creating a database database object.  It is not
 * really needed by the VsatPanel which uses the DBObject system for
 * changing a record in the database file.  However, you have to have this
 * shell to create objects containing a database record for passing around
 * and manipulating.  There are two constructors normally given:
 * 1)  passed a result set positioned to the database record to decode
 * 2) Passed all of the data arguments needed to build the same set
 *     of data.
 *
 * The Vsat should be replaced with capitalized version (class name) for the
 * file.  Vsat should be replaced with the lower case version (for variables).
 *
 * Both the constructors use the "makeVsat(list of data args) to set the
 * local variables to the value.  The result set just uses the
 * rs.get???("fieldname") to get the data from the database where the other
 * passes the arguments from the caller.
 *
 * Notes on Enums :
 ** data class should have code like :
 * import java.util.ArrayList;          /// Import for Enum manipulation
 *   .
 *  static ArrayList fieldEnum;         // This list will have Strings corresponding to enum
 *       .
 *       .
 *    // To convert an enum we need to create the fieldEnum ArrayList.  Do only once(static)
 *    if(fieldEnum == null) fieldEnum = FUtil.getEnum(UC.getConnection(),"table","fieldName");
 *
 *   // Get the int corresponding to an Enum String for storage in the data base and
 *   // the index into the JComboBox representing this Enum
 *   localVariable = FUtil.enumStringToInt(fieldEnum, enumString);
 *
 */
package gov.usgs.anss.gui;

import java.sql.ResultSet;
import java.sql.SQLException;

public final class Vsat {    //implements Comparator
  // static ArrayList enumName;   // this need to be populated in makeVsat()

  /**
   * Creates a new instance of Vsat
   */
  private int ID;                   // This is the database ID (should alwas be named "ID"
  private String station;             // This is the main key which should have same name
  // as the table which it is a key to.  That is
  // Table "category" should have key "category"

  // All fields of file go here
  //  double longitude
  private String comment;
  private String serialnumber;
  private String siteid;
  private String ipadr;
  private String san;
  private String pin;
  private String account;
  private String macaddr;
  private int dynamic;

  // Put in correct detail constructor here.  Use makeVsat() by filling in all
  // the rs.get???("fieldName") for all of the arguments
  public Vsat(ResultSet rs) throws SQLException {
    makeVsat(rs.getInt("ID"), rs.getString("station"),
            rs.getString("serialnumber"), rs.getString("siteid"), rs.getString("ipadr"),
            rs.getString("comment")// ,rs.getDouble(longitude)
            ,
             rs.getString("san"), rs.getString("pin"), rs.getString("account"), rs.getString("macaddr"),
            rs.getInt("dynamic")
    );
  }

  // Detail Constructor, match set up with makeVsat(), this argument list should be
  // the same as for the result set builder as both use makeVsat()
  public Vsat(int inID, String loc, //, double lon
          String sn, String sid, String ip, String cmt,
          String san, String pin, String acc, String mac, int dyn) {
    makeVsat(inID, loc, sn, sid, ip, cmt, san, pin, acc, mac, dyn
    );
  }

  // This routine transfers to the local variables of this object all of the data variables
  // associated with this file.
  private void makeVsat(int inID, String loc, //, double lon
          String sn, String sid, String ip, String cmt,
           String san2, String pn, String acc, String mac, int dyn
  ) {
    ID = inID;
    station = loc;     // longitude = lon
    // Put asssignments to all fields from arguments here
    //if(qtypeEnum == null) qtypeEnum=FUtil.getEnum(UC.getConnection(), "nsnstation","quanterraType");
    //qtype = FUtil.enumStringToInt(qtypeEnum, st); 
    // cont40 = FUtil.isTrue(c40);          // c40 is a string with "true" or false
    serialnumber = sn;
    siteid = sid;
    ipadr = ip;
    comment = cmt;
    san = san2;
    pin = pn;
    account = acc;
    macaddr = mac;
    dynamic = dyn;

  }

  public String toString2() {
    return station + " - " + serialnumber;
  }

  @Override
  public String toString() {
    return station;
  }
  // getter

  // standard getters
  public int getID() {
    return ID;
  }

  public String getStation() {
    return station;
  }

  public String getSiteID() {
    return siteid;
  }

  public String getComment() {
    return comment;
  }

  public String getSerialNumber() {
    return serialnumber;
  }

  public String getSan() {
    return san;
  }

  public String getPin() {
    return pin;
  }

  public String getAccount() {
    return account;
  }

  public String getMACAddress() {
    return macaddr;
  }

  public boolean isDynamic() {
    return (dynamic != 0);
  }
//  }*/

}
