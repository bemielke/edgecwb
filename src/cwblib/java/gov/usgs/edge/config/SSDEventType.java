/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.edge.config;

//package gov.usgs.edge.template;
//import java.util.Comparator;
import gov.usgs.anss.db.DBConnectionThread;
import gov.usgs.anss.util.Util;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;

/**
 * SSDEventType.java If the name of the class does not match the name of the field in
 * the class you may need to modify the rs.getSTring in the constructor.
 *
 * This SSDEventType template for creating a database database object. It is not really
 * needed by the SSDEventTypePanel which uses the DBObject system for changing a record
 * in the database file. However, you have to have this shell to create objects
 * containing a database record for passing around and manipulating. There are
 * two constructors normally given:
 * <br>
 * 1) passed a result set positioned to the database record to decode
 * <br> 2) Passed all of the data arguments needed to build the same set of
 * data.
 * <br>
 * The SSDEventType should be replaced with capitalized version (class name) for the
 * file. ssdEventType should be replaced with the lower case version (for variables).
 *
 * Both the constructors use the "makeSSDEventType(list of data args) to set the local
 * variables to the value. The result set just uses the rs.get???("fieldname")
 * to get the data from the database where the other passes the arguments from
 * the caller.
 *
 * <br> Notes on Enums :
 * <br> * data class should have code like :
 * <br> import java.util.ArrayList; /// Import for Enum manipulation
 * <br> .
 * <br> static ArrayList fieldEnum; // This list will have Strings corresponding
 * to enum
 * <br> .
 * <br> .
 * <br> // To convert an enum we need to create the fieldEnum ArrayList. Do only
 * once(static)
 * <br> if(fieldEnum == null) fieldEnum =
 * FUtil.getEnum(JDBConnection.getConnection("DATABASE"),"table","fieldName");
 * <br>
 * <br> // Get the int corresponding to an Enum String for storage in the data
 * base and
 * <br> // the index into the JComboBox representing this Enum
 * <br> localVariable = FUtil.enumStringToInt(fieldEnum, enumString);
 * <br> Created on July 8, 2002, 1:23 PM
 *
 * @author D.C. Ketchum
 */
public class SSDEventType //implements Comparator
{   // static ArrayList enumName;   // this need to be populated in makeSSDEventType()

  public static ArrayList<SSDEventType> ssdEventTypes;             // ArrayList containing objects of this SSDEventType Type

  /**
   * Creates a new instance of SSDEventType
   */
  int ID;                   // This is the database ID (should alwas be named "ID"
  String ssdEventType;             // This is the main key which should have same name
  // as the table which it is a key to.  That is
  // Table "category" should have key "category"

  // USER: All fields of file go here
  //  double longitude
  // Put in correct detail constructor here.  Use makeSSDEventType() by filling in all
  // the rs.get???("fieldName") for all of the arguments
  /**
   * Create a instance of this table row from a positioned result set
   *
   * @param rs The ResultSet already SELECTED
   * @exception SQLException SQLException If the ResultSet is Improper, network
   * is down, or database is down
   */
  public SSDEventType(ResultSet rs) throws SQLException {  //USER: add all data field here
    reload(rs);
  }

  /**
   * reload the object data from a given result set
   *
   * @param rs The ResultSet to load the object from
   * @throws SQLException If one is thrown
   */
  public final void reload(ResultSet rs) throws SQLException {
    makeSSDEventType(rs.getInt("ID"), rs.getString("ssdEventType")
    // ,rs.getDouble(longitude)
    );
  }

  // Detail Constructor, match set up with makeSSDEventType(), this argument list should be
  // the same as for the result set builder as both use makeSSDEventType()
  /**
   * create a row record from individual variables
   *
   * @param inID The ID in the database, normally zero for this type of
   * construction
   * @param loc The key value for the database (same a name of database table)
   */
  public SSDEventType(int inID, String loc //USER: add fields, double lon
  ) {
    makeSSDEventType(inID, loc //, lon
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
  private void makeSSDEventType(int inID, String eventType //USER: add fields, double lon
  ) {
    ID = inID;
    ssdEventType = eventType;     
  }

  /**
   * return the key name as the string
   *
   * @return the key name
   */
  public String toString2() {
    return ssdEventType;
  }

  /**
   * return the key name as the string
   *
   * @return the key name
   */
  @Override
  public String toString() {
    return ssdEventType;
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
  public String getSSDEventType() {
    return ssdEventType;
  }

  //USER:  All field getters here
  //  public double getLongitude() { return longitude;}
  // This is needed if you need to implement Comparator
  /*  public class EquipCompare implements Comparator {
    public int compare(Object a, Object b) {
      return ((SSDEventType) a).getSSDEventType().compareTo(((SSDEventType) b).getSSDEventType());
    }
    public boolean equals(Object a, Object b) {
      if( ((SSDEventType)a).getSSDEventType().equals( ((SSDEventType) b).getSSDEventType())) return true;
      return false;
    }
//  }*/
  public static ArrayList<SSDEventType> getSSDEventTypeArrayList() {
    makeSSDEventTypes();
    return ssdEventTypes;
  }

  public static SSDEventType getSSDEventTypeWithID(int ID) {
    makeSSDEventTypes();
    for (SSDEventType instance : ssdEventTypes) {
      if (instance.getID() == ID) {
        return instance;
      }
    }
    return null;
  }
  /** Make the list of SSDEventTypes
   * 
   */
  public static void makeSSDEventTypes() {
    if (ssdEventTypes != null) {
      return;
    }
    // Create a temporary statement using the dbname as the tag, and create the ssdEventTypes
    try (Statement stmt = DBConnectionThread.getConnection(DBConnectionThread.getDBSchema()).createStatement()) {
      makeSSDEventTypes(stmt);
    } catch (SQLException e) {
      Util.SQLErrorPrint(e, "makeSSDEventTypes() on table SQL failed");
    }
  }

  /** Make a the ArrayList for SSDEventTypes using a given database statement, the user must close the statement
   * 
   * @param stmt A DB statement to execute the query on the ssdEventTypes table, it can be read only
   */
  public static void makeSSDEventTypes(Statement stmt) {
    if (ssdEventTypes != null) {
      return;
    }
    ssdEventTypes = new ArrayList<>(100);
    try {
      String s = "SELECT * FROM " + DBConnectionThread.getDBSchema() + ".ssdeventtype ORDER BY id;";              
      try (ResultSet rs = stmt.executeQuery(s)) {
        while (rs.next()) {
          SSDEventType eventType = new SSDEventType(rs);
          //Util.prt("MakeSSDEventType() i="+v.size()+" is "+loc.getSSDEventType());
          ssdEventTypes.add(eventType);
        }
      }
      //Util.prt("Number of instances="+v.size());
      //Util.prt("Number of instances="+v.size());
    } catch (SQLException e) {
      Util.SQLErrorPrint(e, "makeSSDEventTypes() on table SQL failed");
    }
  }
}
