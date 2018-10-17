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
 * Role.java If the name of the class does not match the name of the field in the class you may need
 * to modify the rs.getSTring in the constructor.
 *
 * This Role templace for creating a database database object. It is not really needed by the
 * RolePanel which uses the DBObject system for changing a record in the database file. However, you
 * have to have this shell to create objects containing a database record for passing around and
 * manipulating. There are two constructors normally given:
 * <br>
 * 1) passed a result set positioned to the database record to decode
 * <br> 2) Passed all of the data arguments needed to build the same set of data.
 * <br>
 * The Role should be replaced with capitalized version (class name) for the file. role should be
 * replaced with the lower case version (for variables).
 *
 * Both the constructors use the "makeRole(list of data args) to set the local variables to the
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
public final class Role {    //implements Comparator
  // static ArrayList enumName;   // this need to be populated in makeRole()

  public static ArrayList<Role> roles;             // Vector containing objects of this Role Type

  /**
   * Creates a new instance of Role
   */
  private int ID;                   // This is the database ID (should alwas be named "ID"
  private String role;             // This is the main key which should have same name
  // as the table which it is a key to.  That is
  // Table "category" should have key "category"

  // USER: All fields of file go here
  //  double longitude
  private String description;
  private String ipadr;
  //String cpu;
  private int cpuID;
  private int hasdata;
  private String enabledAccounts;

  // Put in correct detail constructor here.  Use makeRole() by filling in all
  // the rs.get???("fieldName") for all of the arguments
  /**
   * Create a instance of this table row from a positioned result set
   *
   * @param rs The ResultSet already SELECTED
   * @exception SQLException SQLException If the ResultSet is Improper, network is down, or database
   * is down
   */
  public Role(ResultSet rs) throws SQLException {  //USER: add all data field here
    makeRole(rs.getInt("ID"), rs.getString("role"),
            rs.getString("description"),
            rs.getString("ipadr"),
            rs.getInt("cpuID"),
            rs.getInt("hasdata"),
            rs.getString("accounts")
    );
    // ,rs.getDouble(longitude)

  }

  /**
   * Reload instance of this table row from a positioned result set
   *
   * @param rs The ResultSet already SELECTED
   * @exception SQLException SQLException If the ResultSet is Improper, network is down, or database
   * is down
   */
  public void reload(ResultSet rs) throws SQLException {
    makeRole(rs.getInt("ID"), rs.getString("role"),
            rs.getString("description"),
            rs.getString("ipadr"),
            rs.getInt("cpuID"),
            rs.getInt("hasdata"), rs.getString("accounts")
    );
  }

  // Detail Constructor, match set up with makeRole(), this argument list should be
  // the same as for the result set builder as both use makeRole()
  /**
   * create a row record from individual variables
   *
   * @param inID The ID in the database, normally zero for this type of construction
   * @param loc The key value for the database (role) (same a name of database table)
   * @param inDesc The description
   * @param inIPadr The IP address of the role;
   * @param inHasdata the Has data flag - normally this is modified by the GUI and not user editable
   * @param inCpuID THe ID of the CPU assigned this role
   * @param inAccounts
   */
  public Role(int inID, String loc //USER: add fields, double lon
          ,
           String inDesc, String inIPadr, int inCpuID, int inHasdata, String inAccounts
  ) {
    makeRole(inID, loc, inDesc, inIPadr, inCpuID, inHasdata, inAccounts //, lon
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
  private void makeRole(int inID, String loc //USER: add fields, double lon
          ,
           String inDesc, String inIPadr, int inCpuID, int inHasdata, String inAccounts
  ) {
    ID = inID;
    role = loc;     // longitude = lon
    //USER:  Put asssignments to all fields from arguments here
    description = inDesc;
    ipadr = inIPadr;
    cpuID = inCpuID;
    hasdata = inHasdata;
    enabledAccounts = inAccounts;

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
    return role;
  }

  /**
   * return the key name as the string
   *
   * @return the key name
   */
  @Override
  public String toString() {
    return role;
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
  public String getRole() {
    return role;
  }

  public String getIP() {
    return ipadr;
  }

  public String getDescription() {
    return description;
  }

  public int getCpuID() {
    return cpuID;
  }

  public int getHasData() {
    return hasdata;
  }

  public String getEnabledAccounts() {
    return enabledAccounts;
  }

  public static ArrayList<Role> getRoleVector() {
    makeRoles();
    return roles;
  }

  public static Role getRoleWithID(int id) {
    makeRoles();
    for (Role role : roles) {
      if (role.getID() == id) {
        return role;
      }
    }
    return null;
  }

  // This routine should only need tweeking if key field is not same as table name
  public static void makeRoles() {
    if (roles != null) {
      return;
    }
    roles = new ArrayList<Role>(100);
    try {
      try (Statement stmt = DBConnectionThread.getConnection(DBConnectionThread.getDBSchema()).createStatement() // used for query
              ) {
        String s = "SELECT * FROM edge.role ORDER BY role;";
        try (ResultSet rs = stmt.executeQuery(s)) {
          while (rs.next()) {
            Role loc = new Role(rs);
            //        Util.prt("MakeRole() i="+roles.size()+" is "+loc.getRole());
            roles.add(loc);
          }
        }
      }
    } catch (SQLException e) {
      Util.SQLErrorPrint(e, "makeRoles() on table SQL failed");
    }
  }
}
