/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.edge.alarm;
import gov.usgs.anss.util.Util;
import gov.usgs.anss.dbtable.DBTable;
import java.sql.SQLException;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.sql.Statement;
import java.sql.Connection;

/**
 * Alias.java If the name of the class does not match the name of the field in the class you may
 * need to modify the rs.getSTring in the constructor.
 *
 * This Alias templace for creating a MySQL database object. It is not really needed by the
 * AliasPanel which uses the DBObject system for changing a record in the database file. However,
 * you have to have this shell to create objects containing a MySQL record for passing around and
 * manipulating. There are two constructors normally given:
 * <br>
 * 1) passed a result set positioned to the MySQL record to decode
 * <br> 2) Passed all of the data arguments needed to build the same set of data.
 * <br>
 * The Alias should be replaced with capitalized version (class name) for the file. alias should be
 * replaced with the lower case version (for variables).
 *
 * Both the constructors use the "makeAlias(list of data args) to set the local variables to the
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
public final class Alias { //implements Comparator
  // static ArrayList enumName;   // this need to be populated in makeAlias()

  /**
   * Creates a new instance of Alias
   */
  private int ID;                   // This is the MySQL ID (should alwas be named "ID"
  private String alias;             // This is the main key which should have same name
  // as the table which it is a key to.  That is
  // Table "category" should have key "category"

  // USER: All fields of file go here
  private int aliasTargetID;             // The target is another alias
  private int targetID;             // The targetID in the target table
  private Timestamp effective;      // The effective date of this alias

  // Put in correct detail constructor here.  Use makeAlias() by filling in all
  // the rs.get???("fieldName") for all of the arguments
  /**
   * Create a instance of this table row from a positioned result set
   *
   * @param rs The ResultSet already SELECTED
   * @exception SQLException SQLException If the ResultSet is Improper, network is down, or database
   * is down
   */
  public Alias(ResultSet rs) throws SQLException {  //USER: add all data field here
    makeAlias(rs.getInt("ID"), rs.getString("alias"),
            rs.getInt("aliastargetID"), rs.getInt("targetid"), rs.getTimestamp("effective") // ,rs.getDouble(longitude)
    );
  }

  // Put in correct detail constructor here.  Use makeAlias() by filling in all
  // the rs.get???("fieldName") for all of the arguments
  /**
   * Create a instance of this table row from a positioned result set
   *
   * @param rs The ResultSet already SELECTED
   * @exception SQLException SQLException If the ResultSet is Improper, network is down, or database
   * is down
   */
  public Alias(DBTable rs) throws SQLException {  //USER: add all data field here
    reload(rs);
  }

  /**
   * Create a instance of this table row from a positioned result set
   *
   * @param rs The ResultSet already SELECTED
   * @exception SQLException SQLException If the ResultSet is Improper, network is down, or database
   * is down
   */
  public void reload(DBTable rs) throws SQLException {  //USER: add all data field here
    makeAlias(rs.getInt("ID"), rs.getString("alias"),
            rs.getInt("aliastargetID"), rs.getInt("targetid"), rs.getTimestamp("effective") // ,rs.getDouble(longitude)
    );
  }

  /**
   * Create a instance of this table row from a positioned result set
   *
   * @param rs The ResultSet already SELECTED
   * @exception SQLException SQLException If the ResultSet is Improper, network is down, or database
   * is down
   */
  public void reload(ResultSet rs) throws SQLException {  //USER: add all data field here
    makeAlias(rs.getInt("ID"), rs.getString("alias"),
            rs.getInt("aliastargetID"), rs.getInt("targetid"), rs.getTimestamp("effective") // ,rs.getDouble(longitude)
    );
  }

  // Detail Constructor, match set up with makeAlias(), this argument list should be
  // the same as for the result set builder as both use makeAlias()
  /**
   * create a row record from individual variables
   *
   * @param inID The ID in the database, normally zero for this type of construction
   * @param loc The key value for the database (same a name of database table)
   * @param aID alias target ID
   * @param tarID target ID
   * @param eff effective date
   */
  public Alias(int inID, String loc //USER: add fields, double lon
          ,
           int aID, int tarID, Timestamp eff) {
    makeAlias(inID, loc //, lon
            ,
             aID, tarID, eff
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
  private void makeAlias(int inID, String loc //USER: add fields, double lon
          ,
           int aID, int tarID, Timestamp eff
  ) {
    ID = inID;
    alias = loc;     // longitude = lon
    //USER:  Put asssignments to all fields from arguments here
    aliasTargetID = aID;
    targetID = tarID;
    effective = eff;
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
    return alias;
  }

  /**
   * return the key name as the string
   *
   * @return the key name
   */
  @Override
  public String toString() {
    return alias;
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
  public String getAlias() {
    return alias;
  }

  //USER:  All field getters here
  //  public double getLongitude() { return longitude;}
  public int getAliasTargetID() {
    return aliasTargetID;
  }

  public int getTargetID() {
    return targetID;
  }

  // This is needed if you need to implement Comparator
  /*  public class EquipCompare implements Comparator {
    public int compare(Object a, Object b) {
      return ((Alias) a).getAlias().compareTo(((Alias) b).getAlias());
    }
    public boolean equals(Object a, Object b) {
      if( ((Alias)a).getAlias().equals( ((Alias) b).getAlias())) return true;
      return false;
    }
//  }*/
  private static int[] targetList;
  private static int ntargets;
  private static int origID;
  private static boolean getDebug = true;

  static synchronized public int[] getTargetIDs(int aliasID, Connection mysql) {
    if (targetList == null) {
      targetList = new int[300];
    }
    ntargets = 0;
    origID = aliasID;
    if (getDebug) {
      Util.prt("      Start of getTargetIDs(" + aliasID + ")");
    }
    addTarget(aliasID, mysql);
    int[] ret = new int[ntargets];
//    for(int i=0; i<ntargets; i++) ret[i] = targetList[i];
    System.arraycopy(targetList, 0, ret, 0, ntargets);
    return ret;
  }

  static private void addTarget(int id, java.sql.Connection mysql) {
    try {
      try (Statement stmt = mysql.createStatement()) {
        if (getDebug) {
          Util.prt("      getTargetID start ntarget=" + ntargets + " for ID=" + id);
        }
        ResultSet rs = stmt.executeQuery("SELECT * FROM alias WHERE aliastargetID=" + id + " order by effective");
        long now = System.currentTimeMillis();

        int selectedID = 0;
        while (rs.next()) {
          if (rs.getTimestamp("effective").getTime() <= now) {
            selectedID = rs.getInt("targetID");
          }
        }
        // We did not process the last one, if any  had effective dates
        if (selectedID > 0) {
          ResultSet rs2 = stmt.executeQuery("SELECT * FROM target WHERE ID=" + selectedID);
          if (rs2.next()) {
            Target tg = new Target(rs2);
            if (tg.isAlias()) {
              addTarget(id, mysql);    // if its a alias, translate it
            } else {
              if (ntargets >= targetList.length) {
                Util.prt("      *** Probably an infinite recursion loop for aliasID=" + origID);
                return;
              }
              if (getDebug) {
                Util.prt("      target[" + ntargets + "]=" + tg.getID());
              }
              targetList[ntargets++] = tg.getID(); // not an alias add it to the list
            }
          } else {
            Util.prt("   **** Alias:addTarget Did not find target id=" + selectedID);
          }
        }
      }

    } catch (SQLException e) {
      Util.SQLErrorPrint(e, "Alias: addTarget() SQL error building targetIDs");
    }
  }

}
