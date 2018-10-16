/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.edge.alarm;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import gov.usgs.anss.dbtable.DBTable;

/**
 * Source.java If the name of the class does not match the name of the field in the class you may
 * need to modify the rs.getSTring in the constructor.
 *
 * This Source templace for creating a MySQL database object. It is not really needed by the
 * SourcePanel which uses the DBObject system for changing a record in the database file. However,
 * you have to have this shell to create objects containing a MySQL record for passing around and
 * manipulating. There are two constructors normally given:
 * <br>
 * 1) passed a result set positioned to the MySQL record to decode
 * <br> 2) Passed all of the data arguments needed to build the same set of data.
 * <br>
 * The Source should be replaced with capitalized version (class name) for the file. source should
 * be replaced with the lower case version (for variables).
 *
 * Both the constructors use the "makeSource(list of data args) to set the local variables to the
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
public class Source //implements Comparator
{   // static ArrayList enumName;   // this need to be populated in makeSource()

  /**
   * Creates a new instance of Source
   */
  private int ID;                   // This is the MySQL ID (should alwas be named "ID"
  private String source;             // This is the main key which should have same name
  // as the table which it is a key to.  That is
  // Table "category" should have key "category"

  // USER: All fields of file go here
  //  double longitude
  private int defaultDamp;
  private int defaultDamp2;
  private int defaultTargetID;
  private int action;
  private Timestamp suppressAll;

  // Put in correct detail constructor here.  Use makeSource() by filling in all
  // the rs.get???("fieldName") for all of the arguments
  /**
   * Create a instance of this table row from a positioned result set
   *
   * @param rs The ResultSet already SELECTED
   * @exception SQLException SQLException If the ResultSet is Improper, network is down, or database
   * is down
   */
  public Source(ResultSet rs) throws SQLException {  //USER: add all data field here
    reload(rs);
  }

  /**
   * Create a instance of this table row from a positioned result set
   *
   * @param rs The ResultSet alreay SELECTED
   * @exception SQLException SQLException If the ResultSet is Improper, network is down, or database
   * is down
   */
  public final void reload(ResultSet rs) throws SQLException {  //USER: add all data field here
    makeSource(rs.getInt("ID"), rs.getString("source"), rs.getInt("defaultDamp"),
            rs.getInt("defaultDamp2"), rs.getInt("defaultTargetID"), rs.getInt("action"),
            rs.getTimestamp("suppressAll")
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
  public Source(DBTable rs) throws SQLException {  //USER: add all data field here
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
    makeSource(rs.getInt("ID"), rs.getString("source"), rs.getInt("defaultDamp"),
            rs.getInt("defaultDamp2"), rs.getInt("defaultTargetID"), rs.getInt("action"),
            rs.getTimestamp("suppressAll")
    // ,rs.getDouble(longitude)
    );
  }

  // Detail Constructor, match set up with makeSource(), this argument list should be
  // the same as for the result set builder as both use makeSource()
  /**
   * create a row record from individual variables
   *
   * @param inID The ID in the database, normally zero for this type of constructin
   * @param loc The key value for the database (same a name of database table)
   * @param dd Default damping
   * @param dd2 Default damping 2
   * @param dt Default target
   * @param act Action id
   * @param sup Suppression all date
   */
  public Source(int inID, String loc //USER: add fields, double lon
          ,
           int dd, int dd2, int dt, int act, Timestamp sup
  ) {
    makeSource(inID, loc, dd, dd2, dt, act, sup //, lon
    );
  }

  // This routine transfers to the local variables of this object all of the data variables
  // associated with this file.
  /**
   * internally set all of the field in our data to the passsed data
   *
   * @param inID The row ID in the database
   * @param loc The key (same as table name)
   *
   */
  private void makeSource(int inID, String loc //USER: add fields, double lon
          ,
           int dd, int dd2, int dt, int act, Timestamp sup
  ) {
    ID = inID;
    source = loc;     // longitude = lon
    //USER:  Put asssignments to all fields from arguments here
    defaultDamp = dd;
    defaultDamp2 = dd2;
    defaultTargetID = dt;

    action = act;
    suppressAll = sup;

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
    return source + " damp=" + defaultDamp + " damp2=" + defaultDamp2;
  }

  /**
   * return the key name as the string
   *
   * @return the key name
   */
  @Override
  public String toString() {
    return source;
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
  public String getSource() {
    return source;
  }

  public int getDefaultTargetID() {
    return defaultTargetID;
  }

  public int getAction() {
    return action;
  }

  public int getDefaultDamping() {
    return defaultDamp;
  }

  public int getDefaultDamping2() {
    return defaultDamp2;
  }

  public Timestamp getSuppressAllTime() {
    return suppressAll;
  }

  //USER:  All field getters here
  //  public double getLongitude() { return longitude;}
  public boolean suppressAll() {
    return (System.currentTimeMillis() < suppressAll.getTime());
  }

  // This is needed if you need to implement Comparator
  /*  public class EquipCompare implements Comparator {
    public int compare(Object a, Object b) {
      return ((Source) a).getSource().compareTo(((Source) b).getSource());
    }
    public boolean equals(Object a, Object b) {
      if( ((Source)a).getSource().equals( ((Source) b).getSource())) return true;
      return false;
    }
//  }*/
}
