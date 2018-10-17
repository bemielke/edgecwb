/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.edge.snw;

import gov.usgs.anss.db.DBConnectionThread;
import gov.usgs.anss.dbtable.DBTable;
import gov.usgs.anss.util.Util;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;

/**
 * SNWGroup.java If the name of the class does not match the name of the field in the class you may
 * need to modify the rs.getSTring in the constructor.
 *
 * This SNWGroup template for creating a MySQL database object. It is not really needed by the
 * SNWGroupPanel which uses the DBObject system for changing a record in the database file. However,
 * you have to have this shell to create objects containing a MySQL record for passing around and
 * manipulating. There are two constructors normally given:
 * <br>
 * 1) passed a result set positioned to the MySQL record to decode
 * <br> 2) Passed all of the data arguments needed to build the same set of data.
 * <br>
 * The SNWGroup should be replaced with capitalized version (class name) for the file. snwgroup
 * should be replaced with the lower case version (for variables).
 *
 * Both the constructors use the "makeSNWGroup(list of data args) to set the local variables to the
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
public final class SNWGroup implements Comparable {
  // static ArrayList enumName;   // this need to be populated in makeSNWGroup()

  public static ArrayList<SNWGroup> snwgroups;             // Vector containing objects of this SNWGroup Type

  /**
   * Creates a new instance of SNWGroup
   */
  private int ID;                   // This is the MySQL ID (should alwas be named "ID"
  private String snwgroup;          // This is the main key which should have same name
  private int pctbad;               // as the table which it is a key to.  That is
  private int pctbadwarn;           // Table "category" should have key "category"

  // USER: All fields of file go here
  //  double longitude
  private String description;
  private String documentation;
  private long lastUpdated;
  private boolean analyst;

  // Put in correct detail constructor here.  Use makeSNWGroup() by filling in all
  // the rs.get???("fieldName") for all of the arguments
  /**
   * Create a instance of this table row from a positioned result set
   *
   * @param rs The ResultSet already SELECTED
   * @exception SQLException SQLException If the ResultSet is Improper, network is down, or database
   * is down
   */
  public SNWGroup(ResultSet rs) throws SQLException {  //USER: add all data field here
    makeSNWGroup(rs.getInt("ID"),
            rs.getString("snwgroup"),
            rs.getString("description"), rs.getString("documentation"),
            rs.getInt("pctbad"),
            rs.getInt("pctbadwarn")
    // ,rs.getDouble(longitude)
    );
  }

  /**
   * Create a instance of this table row from a positioned result set
   *
   * @param rs The ResultSet already SELECTED
   * @exception SQLException SQLException If the ResultSet is Improper, network is down, or database
   * is down
   */
  public SNWGroup(DBTable rs) throws SQLException {  //USER: add all data field here
    makeSNWGroup(rs.getInt("ID"),
            rs.getString("snwgroup"),
            rs.getString("description"), rs.getString("documentation"),
            rs.getInt("pctbad"),
            rs.getInt("pctbadwarn")
    // ,rs.getDouble(longitude)
    );
  }

  /**
   * Update a instance of this table row from a positioned result set
   *
   * @param rs The ResultSet already SELECTED
   * @exception SQLException SQLException If the ResultSet is Improper, network is down, or database
   * is down
   */
  public void refresh(ResultSet rs) throws SQLException {  //USER: add all data field here
    makeSNWGroup(rs.getInt("ID"),
            rs.getString("snwgroup"),
            rs.getString("description"), rs.getString("documentation"),
            rs.getInt("pctbad"),
            rs.getInt("pctbadwarn")
    // ,rs.getDouble(longitude)
    );
  }

  public void reload(ResultSet rs) throws SQLException {
    refresh(rs);
  }

  /**
   * Create a instance of this table row from a positioned result set
   *
   * @param rs The ResultSet alreay SELECTED
   * @exception SQLException SQLException If the ResultSet is Improper, network is down, or database
   * is down
   */
  public void reload(DBTable rs) throws SQLException {  //USER: add all data field here
    makeSNWGroup(rs.getInt("ID"),
            rs.getString("snwgroup"),
            rs.getString("description"), rs.getString("documentation"),
            rs.getInt("pctbad"),
            rs.getInt("pctbadwarn")
    // ,rs.getDouble(longitude)
    );
  }  // Detail Constructor, match set up with makeSNWGroup(), this argument list should be
  // the same as for the result set builder as both use makeSNWGroup()

  /**
   * create a row record from individual variables
   *
   * @param inID The ID in the database, normally zero for this type of construction
   * @param loc The key value for the database (same a name of database table)
   * @param description Of this group
   * @param doc Documentation string
   * @param pbad Percentage for group to be bad
   * @param pbadwarn Percentage for group to be warning
   */
  public SNWGroup(int inID, String loc, String description, String doc, int pbad, int pbadwarn //USER: add fields, double lon
  ) {
    makeSNWGroup(inID, loc, "", doc, pbad, pbadwarn //, lon
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
  private void makeSNWGroup(int inID, String loc, String desc, String doc, int pbad, int pbadwarn //USER: add fields, double lon
  ) {
    ID = inID;
    snwgroup = loc;     // longitude = lon
    //USER:  Put asssignments to all fields from arguments here
    description = desc;
    documentation = doc;
    pctbad = pbad;
    pctbadwarn = pbadwarn;
    // ENUM example:
    //if(qtypeEnum == null) qtypeEnum=FUtil.getEnum(UC.getConnection(), "nsnstation","quanterraType");
    //qtype = FUtil.enumStringToInt(qtypeEnum, st); 
    // Boolean example where ENUM "True", "False
    // cont40 = FUtil.isTrue(c40);          // c40 is a string with "true" or false
    lastUpdated = System.currentTimeMillis();
    analyst = desc.contains("<ANALYST>");
  }

  /**
   * return the key name as the string
   *
   * @return the key name
   */
  public String toString2() {
    return snwgroup;
  }

  /**
   * return the key name as the string
   *
   * @return the key name
   */
  @Override
  public String toString() {
    return snwgroup;
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
  public String getSNWGroup() {
    return snwgroup;
  }

  public String getSNWGroupAnalyst() {
    return (analyst ? "+" : "") + snwgroup;
  }

  public int getpctbad() {
    return pctbad;
  }

  public int getpctbadwarn() {
    return pctbadwarn;
  }

  public long getMask() {
    return 1L << (ID - 1);
  }

  public long getLastUpdated() {
    return lastUpdated;
  }

  public boolean isEventable() {
    return (pctbad > 1 || pctbadwarn > 1);
  }

  public String getDocumentation() {
    return documentation;
  }

  public boolean isAnalystGroup() {
    return analyst;
  }

  //USER:  All field getters here
  //  public double getLongitude() { return longitude;}
  // This is needed if you need to implement Comparator
  /*  public class EquipCompare implements Comparator {
    public int compare(Object a, Object b) {
      return ((SNWGroup) a).getSNWGroup().compareTo(((SNWGroup) b).getSNWGroup());
    }
    public boolean equals(Object a, Object b) {
      if( ((SNWGroup)a).getSNWGroup().equals( ((SNWGroup) b).getSNWGroup())) return true;
      return false;
    }
//  }*/
  @Override
  public int compareTo(Object o) {
    if (o == null) {
      return -1;
    }
    SNWGroup g = (SNWGroup) o;
    if (analyst && !g.isAnalystGroup()) {
      return -1;
    }
    if (!analyst && g.isAnalystGroup()) {
      return 1;
    }
    return snwgroup.compareTo(g.getSNWGroup());
  }

  public static ArrayList<SNWGroup> getGroups() {
    return snwgroups;
  }

  // This routine should only need tweeking if key field is not same as table name
  public static void makeSNWGroups() {
    if (snwgroups != null) {
      return;
    }
    snwgroups = new ArrayList<SNWGroup>(100);
    try {
      try (Statement stmt = DBConnectionThread.getConnection(DBConnectionThread.getDBSchema()).createStatement() // used for query
              ) {
        String s = "SELECT * FROM edge.snwgroup ORDER BY snwgroup;";
        try (ResultSet rs = stmt.executeQuery(s)) {
          while (rs.next()) {
            SNWGroup loc = new SNWGroup(rs);
            //        Util.prt("MakeSNWGroup() i="+v.size()+" is "+loc.getSNWGroup());
            snwgroups.add(loc);
          }
        }
      }
      Collections.sort(snwgroups);
    } catch (SQLException e) {
      Util.SQLErrorPrint(e, "makeSNWGroups() on table SQL failed " + DBConnectionThread.getConnection(DBConnectionThread.getDBSchema()));
    } catch (NullPointerException e) {
      Util.prt("Must be NoDB! schema=" + DBConnectionThread.getDBSchema() + " DBCT=" + DBConnectionThread.getDBSchema());
    }
  }
}
