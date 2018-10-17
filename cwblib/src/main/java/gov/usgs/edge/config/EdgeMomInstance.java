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
 * EdgeMomInstance.java If the name of the class does not match the name of the field in the class
 * you may need to modify the rs.getSTring in the constructor.
 *
 * This EdgeMomInstance template for creating a database database object. It is not really needed by
 * the EdgeMomInstancePanel which uses the DBObject system for changing a record in the database
 * file. However, you have to have this shell to create objects containing a database record for
 * passing around and manipulating. There are two constructors normally given:
 * <br>
 * 1) passed a result set positioned to the database record to decode
 * <br> 2) Passed all of the data arguments needed to build the same set of data.
 * <br>
 * The EdgeMomInstance should be replaced with capitalized version (class name) for the file.
 * instance should be replaced with the lower case version (for variables).
 *
 * Both the constructors use the "makeEdgeMomInstance(list of data args) to set the local variables
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
 * FUtil.getEnum(JDBConnection.getConnection("DATABASE"),"table","fieldName");
 * <br>
 * <br> // Get the int corresponding to an Enum String for storage in the data base and
 * <br> // the index into the JComboBox representing this Enum
 * <br> localVariable = FUtil.enumStringToInt(fieldEnum, enumString);
 * <br> Created on July 8, 2002, 1:23 PM
 *
 * @author D.C. Ketchum
 */
public final class EdgeMomInstance {   //implements Comparator
  // static ArrayList enumName;   // this need to be populated in makeEdgeMomInstance()

  public static ArrayList<EdgeMomInstance> edgemomInstances;             // ArrayList containing objects of this EdgeMomInstance Type

  /**
   * Creates a new instance of EdgeMomInstance
   */
  private int ID;                   // This is the database ID (should alwas be named "ID"
  private String instance;             // This is the main key which should have same name
  // as the table which it is a key to.  That is
  // Table "category" should have key "category"

  // USER: All fields of file go here
  //  double longitude
  private int roleID;
  private String account;
  private String description;
  private String args;
  private String crontab;
  private String edgeprop;
  private int failoverID;
  private boolean failover;
  private boolean disabled;
  private int heap;

  // Put in correct detail constructor here.  Use makeEdgeMomInstance() by filling in all
  // the rs.get???("fieldName") for all of the arguments
  /**
   * Create a instance of this table row from a positioned result set
   *
   * @param rs The ResultSet already SELECTED
   * @exception SQLException SQLException If the ResultSet is Improper, network is down, or database
   * is down
   */
  public EdgeMomInstance(ResultSet rs) throws SQLException {  //USER: add all data field here
    reload(rs);
  }

  /**
   * reload the object data from a given result set
   *
   * @param rs The ResultSet to load the object from
   * @throws SQLException If one is thrown
   */
  public final void reload(ResultSet rs) throws SQLException {
    makeEdgeMomInstance(rs.getInt("ID"), rs.getString("instance"),
            rs.getString("description"), rs.getInt("roleid"), rs.getString("account"),
            rs.getInt("heap"), rs.getString("args"),
            rs.getString("crontab"), rs.getString("edgeprop"), rs.getInt("failoverid"), rs.getInt("failover"),
            rs.getInt("disabled")
    // ,rs.getDouble(longitude)
    );
  }

  // Detail Constructor, match set up with makeEdgeMomInstance(), this argument list should be
  // the same as for the result set builder as both use makeEdgeMomInstance()
  /**
   * create a row record from individual variables
   *
   * @param inID The ID in the database, normally zero for this type of construction
   * @param loc The key value for the database (same a name of database table)
   * @param desc A description of the instance
   * @param roleID The normal role on which this instance runs
   * @param account Something like "vdl" or "reftek"
   * @param heap size in mB
   * @param args Any additional arguments for the EdgeMom
   * @param cron Crontab entries related to this instance, (RRPClients etc)
   * @param prop edge_NN#I.prop contents - the instance edge.prop
   * @param failID The cpuID to fail over to
   * @param fail If not zero, fail over this node.
   * @param disable Disable this instance
   */
  public EdgeMomInstance(int inID, String loc //USER: add fields, double lon
          ,
           String desc, int roleID, String account, int heap, String args, String cron, String prop,
          int failID, int fail, int disable) {
    makeEdgeMomInstance(inID, loc //, lon
            ,
             desc, roleID, account, heap, args, cron, prop, failID, fail, disable
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
  private void makeEdgeMomInstance(int inID, String loc //USER: add fields, double lon
          ,
           String desc, int roleID, String account, int heap, String args, String cron, String prop,
          int failID, int fail, int disable
  ) {
    ID = inID;
    instance = loc;     // longitude = lon
    //USER:  Put asssignments to all fields from arguments here

    // ENUM example:
    //if(qtypeEnum == null) qtypeEnum=FUtil.getEnum(JDBConnection.getConnection("DATABASE"), "nsnstation","quanterraType");
    //qtype = FUtil.enumStringToInt(qtypeEnum, st); 
    // Boolean example where ENUM "True", "False
    // cont40 = FUtil.isTrue(c40);          // c40 is a string with "true" or false
    description = desc;
    this.roleID = roleID;
    this.account = account;
    this.args = args;
    crontab = cron;
    edgeprop = prop;
    failoverID = failID;
    failover = fail != 0;
    disabled = disable != 0;
    this.heap = heap;

  }

  /**
   * return the key name as the string
   *
   * @return the key name
   */
  public String toString2() {
    return instance;
  }

  /**
   * return the key name as the string
   *
   * @return the key name
   */
  @Override
  public String toString() {
    return instance + "-" + RoleInstance.getRoleWithID(roleID);
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
  public String getEdgeMomInstance() {
    return instance;
  }

  //USER:  All field getters here
  //  public double getLongitude() { return longitude;}
  public String getArgs() {
    return args;
  }

  public int getRoleID() {
    return roleID;
  }

  public String getAccount() {
    return account;
  }

  public String getDescription() {
    return description;
  }

  public String getCrontab() {
    return crontab;
  }

  public String getEdgeProp() {
    return edgeprop;
  }

  public int getFailoverID() {
    return failoverID;
  }

  public boolean isFailedOver() {
    return failover;
  }

  public boolean isDisabled() {
    return disabled;
  }

  public int getHeap() {
    return heap;
  }

  // This is needed if you need to implement Comparator
  /*  public class EquipCompare implements Comparator {
    public int compare(Object a, Object b) {
      return ((EdgeMomInstance) a).getEdgeMomInstance().compareTo(((EdgeMomInstance) b).getEdgeMomInstance());
    }
    public boolean equals(Object a, Object b) {
      if( ((EdgeMomInstance)a).getEdgeMomInstance().equals( ((EdgeMomInstance) b).getEdgeMomInstance())) return true;
      return false;
    }
//  }*/
  public static ArrayList<EdgeMomInstance> getEdgeMomInstanceArrayList() {
    makeEdgeMomInstances();
    return edgemomInstances;
  }

  public static EdgeMomInstance getEdgeMomInstanceWithID(int ID) {
    makeEdgeMomInstances();
    for (EdgeMomInstance instance : edgemomInstances) {
      if (instance.getID() == ID) {
        return instance;
      }
    }
    return null;
  }

  // This routine should only need tweeking if key field is not same as table name
  public static void makeEdgeMomInstances() {
    if (edgemomInstances != null) {
      return;
    }
    edgemomInstances = new ArrayList<>(100);
    try {
      try (Statement stmt = DBConnectionThread.getConnection(DBConnectionThread.getDBSchema()).createStatement() // used for query
              ) {
        String s = "SELECT * FROM " + DBConnectionThread.getDBSchema() + ".instance,"
                + DBConnectionThread.getDBSchema() + ".role WHERE role.id=instance.roleid ORDER BY role,instance;";
        try (ResultSet rs = stmt.executeQuery(s)) {
          while (rs.next()) {
            EdgeMomInstance loc = new EdgeMomInstance(rs);
//        Util.prt("MakeEdgeMomInstance() i="+v.size()+" is "+loc.getEdgeMomInstance());
            edgemomInstances.add(loc);
          }
        }
        //Util.prt("Number of instances="+v.size());
        //Util.prt("Number of instances="+v.size());
      }
    } catch (SQLException e) {
      Util.SQLErrorPrint(e, "makeEdgeMomInstances() on table SQL failed");
    }
  }
}
