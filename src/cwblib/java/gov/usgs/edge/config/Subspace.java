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
 * Subspace.java If the name of the class does not match the name of the field in the class you may
 * need to modify the rs.getSTring in the constructor.
 *
 * This Subspace template for creating a database database object. It is not really needed by the
 * SubspacePanel which uses the DBObject system for changing a record in the database file. However,
 * you have to have this shell to create objects containing a database record for passing around and
 * manipulating. There are two constructors normally given:
 * <br>
 * 1) passed a result set positioned to the database record to decode
 * <br> 2) Passed all of the data arguments needed to build the same set of data.
 * <br>
 * The Subspace should be replaced with capitalized version (class name) for the file. subspace
 * should be replaced with the lower case version (for variables).
 *
 * Both the constructors use the "makeSubspace(list of data args) to set the local variables to the
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
public class Subspace //implements Comparator
{   // static ArrayList enumName;   // this need to be populated in makeSubspace()

  public static ArrayList<Subspace> subspaces;             // ArrayList containing objects of this Subspace Type

  /**
   * Creates a new instance of Subspace
   */
  int ID;                   // This is the database ID (should alwas be named "ID"
  //String subspace;             // This is the main key which should have same name
  String cwbip, ssdcwbip, opsargs, researchargs;
  int cwbport, ssdcwbport, insertport;

  // as the table which it is a key to.  That is
  // Table "category" should have key "category"
  // USER: All fields of file go here
  //  double longitude
  // Put in correct detail constructor here.  Use makeSubspace() by filling in all
  // the rs.get???("fieldName") for all of the arguments
  /**
   * Create a instance of this table row from a positioned result set
   *
   * @param rs The ResultSet already SELECTED
   * @exception SQLException SQLException If the ResultSet is Improper, network is down, or database
   * is down
   */
  public Subspace(ResultSet rs) throws SQLException {  //USER: add all data field here
    reload(rs);
  }

  /**
   * reload the object data from a given result set
   *
   * @param rs The ResultSet to load the object from
   * @throws SQLException If one is thrown
   */
  public final void reload(ResultSet rs) throws SQLException {
    makeSubspace(rs.getInt("ID"), rs.getString("cwbip"), rs.getInt("cwbport"),
            rs.getString("ssdcwbip"), rs.getInt("ssdcwbport"),
            rs.getInt("ssdinsertport"), rs.getString("opsargs"), rs.getString("researchargs")
    // ,rs.getDouble(longitude)
    );
  }

  // Detail Constructor, match set up with makeSubspace(), this argument list should be
  // the same as for the result set builder as both use makeSubspace()
  /**
   * create a row record from individual variables
   *
   * @param inID The ID in the database, normally zero for this type of construction
   * @param cwbip
   * @param cwbport
   * @param ssdcwbip
   * @param ssdcwbport
   * @param insport
   * @param ops
   * @param research
   */
  public Subspace(int inID, String cwbip, int cwbport, String ssdcwbip, int ssdcwbport,
          int insport, String ops, String research
  ) {
    makeSubspace(inID, cwbip, cwbport, ssdcwbip, ssdcwbport, insport, ops, research //, lon
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
  private void makeSubspace(int inID, //USER: add fields, double lon
          String cwbip, int cwbport, String ssdcwbip, int ssdcwbport, int insport,
          String ops, String research
  ) {
    ID = inID;
    this.cwbip = cwbip;     // longitude = lon
    this.cwbport = cwbport;
    this.ssdcwbip = ssdcwbip;
    this.ssdcwbport = ssdcwbport;
    this.insertport = insport;
    this.researchargs = research;
    this.opsargs = ops;
    //USER:  Put asssignments to all fields from arguments here

    // ENUM example:
    //if(qtypeEnum == null) qtypeEnum=FUtil.getEnum(JDBConnection.getConnection("DATABASE"), "nsnstation","quanterraType");
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
    return "subspace";
  }

  /**
   * return the key name as the string
   *
   * @return the key name
   */
  @Override
  public String toString() {
    return "subspace";
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
  public String getCWBIP() {
    return cwbip;
  }

  public String getSSDCWBIP() {
    return ssdcwbip;
  }

  public int getSSDCWBPort() {
    return ssdcwbport;
  }

  public int getCWBPort() {
    return cwbport;
  }

  public int getSSDInsertPort() {
    return insertport;
  }

  public String getOpsArgs() {
    return opsargs;
  }

  public String getResearchArgs() {
    return researchargs;
  }

  public static ArrayList<Subspace> getSubspaceArrayList() {
    makeSubspaces();
    return subspaces;
  }

  public static Subspace getSubspaceWithID(int ID) {
    makeSubspaces();
    for (Subspace instance : subspaces) {
      if (instance.getID() == ID) {
        return instance;
      }
    }
    return null;
  }

  /**
   * Make the list of Subspaces
   *
   */
  public static void makeSubspaces() {
    if (subspaces != null) {
      return;
    }
    // Create a temporary statement using the dbname as the tag, and create the subspaces
    try (Statement stmt = DBConnectionThread.getConnection(DBConnectionThread.getDBSchema()).createStatement()) {
      makeSubspaces(stmt);
    } catch (SQLException e) {
      Util.SQLErrorPrint(e, "makeSubspaces() on table SQL failed");
    }
  }

  /**
   * Make a the ArrayList for Subspaces using a given database statement, the user must close the
   * statement
   *
   * @param stmt A DB statement to execute the query on the subspaces table, it can be read only
   */
  public static void makeSubspaces(Statement stmt) {
    if (subspaces != null) {
      return;
    }
    subspaces = new ArrayList<>(100);
    try {
      String s = "SELECT * FROM " + DBConnectionThread.getDBSchema() + ".instance,"
              + DBConnectionThread.getDBSchema() + ".role WHERE role.id=instance.roleid ORDER BY role,instance;";
      try (ResultSet rs = stmt.executeQuery(s)) {
        while (rs.next()) {
          Subspace loc = new Subspace(rs);
          //Util.prt("MakeSubspace() i="+v.size()+" is "+loc.getSubspace());
          subspaces.add(loc);
        }
      }
      //Util.prt("Number of instances="+v.size());
      //Util.prt("Number of instances="+v.size());
    } catch (SQLException e) {
      Util.SQLErrorPrint(e, "makeSubspaces() on table SQL failed");
    }
  }
}
