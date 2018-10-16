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
 * QueryMomInstance.java If the name of the class does not match the name of the field in the class
 * you may need to modify the rs.getSTring in the constructor.
 *
 * This QueryMomInstance template for creating a database database object. It is not really needed
 * by the EdgeMomInstancePanel which uses the DBObject system for changing a record in the database
 * file. However, you have to have this shell to create objects containing a database record for
 * passing around and manipulating. There are two constructors normally given:
 * <br>
 * 1) passed a result set positioned to the database record to decode
 * <br> 2) Passed all of the data arguments needed to build the same set of data.
 * <br>
 * The QueryMomInstance should be replaced with capitalized version (class name) for the file.
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
public final class QueryMomInstance {    //implements Comparator
  // static ArrayList enumName;   // this need to be populated in makeEdgeMomInstance()

  public static ArrayList<QueryMomInstance> v;             // ArrayList containing objects of this QueryMomInstance Type

  /**
   * Creates a new instance of QueryMomInstance
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
  private int failoverID;
  private boolean failover;
  private boolean disabled;
  private int heap;
  private String args;
  private boolean qsenable, cwbwsenable, dlqsenable, qscenable, fkenable, mscenable, twsenable, fpenable, ssdenable;
  private String qsargs, cwbwsargs, dlqsargs, qscargs, fkargs, mscargs, twsargs, fpargs, ssdargs;

  // Put in correct detail constructor here.  Use makeEdgeMomInstance() by filling in all
  // the rs.get???("fieldName") for all of the arguments
  /**
   * Create a instance of this table row from a positioned result set
   *
   * @param rs The ResultSet already SELECTED
   * @exception SQLException SQLException If the ResultSet is Improper, network is down, or database
   * is down
   */
  public QueryMomInstance(ResultSet rs) throws SQLException {  //USER: add all data field here
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
            rs.getInt("failoverid"), rs.getInt("failover"),
            rs.getInt("disabled"),
            rs.getInt("qsenable"), rs.getString("qsargs"),
            rs.getInt("cwbwsenable"), rs.getString("cwbwsargs"),
            rs.getInt("dlqsenable"), rs.getString("dlqsargs"),
            rs.getInt("qscenable"), rs.getString("qscargs"),
            rs.getInt("fkenable"), rs.getString("fkargs"),
            rs.getInt("mscenable"), rs.getString("mscargs"),
            rs.getInt("twsenable"), rs.getString("twsargs"),
            rs.getInt("fpenable"), rs.getString("fpargs"),
            rs.getInt("ssdenable"), rs.getString("ssdargs")
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
   * @param failID The cpuID to fail over to
   * @param fail If not zero, fail over this node.
   * @param disable Disable this instance
   * @param qsenb Enable the QueryServer
   * @param qsarg Args to the QueryServer
   * @param cwbwsenb enable the CWBWaveServer
   * @param cwbwsarg args to "
   * @param dlqsenb enable the DataLinkToQueryServer
   * @param dlqsarg args to "
   * @param qscenb Enable the QuerySpanCollection
   * @param qscarg args to "
   * @param fkenb Enable FKManager
   * @param fkarg args to "
   * @param mscenb Enable the MiniSeedCollection
   * @param mscarg args to "
   * @param twsenb enable the TrinetWaveServer
   * @param twsarg args to "
   * @param fpenb enable the FilterPickerManager
   * @param fparg args to "
   * @param ssdenb Enable the SubSpaceDetectorManager
   * @param ssdarg args to "
   */
  public QueryMomInstance(int inID, String loc //USER: add fields, double lon
          ,
           String desc, int roleID, String account, int heap, String args,
          int failID, int fail, int disable,
          int qsenb, String qsarg, int cwbwsenb, String cwbwsarg, int dlqsenb, String dlqsarg,
          int qscenb, String qscarg, int fkenb, String fkarg, int mscenb, String mscarg,
          int twsenb, String twsarg, int fpenb, String fparg, int ssdenb, String ssdarg
  ) {
    makeEdgeMomInstance(inID, loc //, lon
            ,
             desc, roleID, account, heap, args, failID, fail, disable,
            qsenb, qsarg, cwbwsenb, cwbwsarg, dlqsenb, dlqsarg,
            qscenb, qscarg, fkenb, fkarg, mscenb, mscarg,
            twsenb, twsarg, fpenb, fparg, ssdenb, ssdarg
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
           String desc, int roleID, String account, int heap, String args,
          int failID, int fail, int disable,
          int qsenb, String qsarg, int cwbwsenb, String cwbwsarg, int dlqsenb, String dlqsarg,
          int qscenb, String qscarg, int fkenb, String fkarg, int mscenb, String mscarg,
          int twsenb, String twsarg, int fpenb, String fparg, int ssdenb, String ssdarg
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
    failoverID = failID;
    failover = fail != 0;
    disabled = disable != 0;
    this.heap = heap;

    qsenable = (qsenb != 0);
    qsargs = qsarg;
    cwbwsenable = (cwbwsenb != 0);
    cwbwsargs = cwbwsarg;
    dlqsenable = (dlqsenb != 0);
    dlqsargs = dlqsarg;
    qscenable = (qscenb != 0);
    qscargs = qscarg;
    fkenable = (fkenb != 0);
    fkargs = fkarg;
    mscenable = (mscenb != 0);
    mscargs = mscarg;
    twsenable = (twsenb != 0);
    twsargs = twsarg;
    fpenable = (fpenb != 0);
    fpargs = fparg;
    ssdenable = (ssdenb != 0);
    ssdargs = ssdarg;

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
  public String getQueryMomInstance() {
    return instance;
  }

  //USER:  All field getters here
  //  public double getLongitude() { return longitude;}
  public boolean isQueryServer() {
    return qsenable;
  }

  public String getQueryServerArgs() {
    return qsargs;
  }

  public boolean isCWBWaveServer() {
    return cwbwsenable;
  }

  public String getCWBWaveServerArgs() {
    return cwbwsargs;
  }

  public boolean isDataLinkToQueryServer() {
    return dlqsenable;
  }

  public String getDataLinkToQueryServerArgs() {
    return dlqsargs;
  }

  public boolean isQuerySpanCollector() {
    return qscenable;
  }

  public String getQuerySpanCollectorArgs() {
    return qscargs;
  }

  public boolean isFKManager() {
    return fkenable;
  }

  public String getFKManagerArgs() {
    return fkargs;
  }

  public boolean isMiniSeedCollection() {
    return mscenable;
  }

  public String getMiniSeedCollectionArgs() {
    return mscargs;
  }

  public boolean isTrinetWaveServer() {
    return twsenable;
  }

  public String getTrinetWaveServerArgs() {
    return twsargs;
  }

  public boolean isFilterPickerManager() {
    return fpenable;
  }

  public String getFilterPickerManagerArgs() {
    return fpargs;
  }

  public boolean isSubSpaceDetector() {
    return ssdenable;
  }

  public String getSubSpaceDetectorArgs() {
    return ssdargs;
  }

  public int getRoleID() {
    return roleID;
  }

  public String getArgs() {
    return args;
  }

  public String getAccount() {
    return account;
  }

  public String getDescription() {
    return description;
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
      return ((QueryMomInstance) a).getEdgeMomInstance().compareTo(((QueryMomInstance) b).getEdgeMomInstance());
    }
    public boolean equals(Object a, Object b) {
      if( ((QueryMomInstance)a).getEdgeMomInstance().equals( ((QueryMomInstance) b).getEdgeMomInstance())) return true;
      return false;
    }
//  }*/
  /**
   * Get the item corresponding to database ID
   *
   * @param ID the database Row ID
   * @return The QueryMomInstance row with this ID
   */
  public static QueryMomInstance getEdgeMomInstanceWithID(int ID) {
    if (v == null) {
      makeEdgeMomInstances();
    }
    for (QueryMomInstance querymom : v) {
      if (querymom.getID() == ID) {
        return querymom;
      }
    }
    return null;
  }

  // This routine should only need tweeking if key field is not same as table name
  public static void makeEdgeMomInstances() {
    if (v != null) {
      return;
    }
    v = new ArrayList<>(100);
    try {
      try (Statement stmt = DBConnectionThread.getConnection(DBConnectionThread.getDBSchema()).createStatement() // used for query
              ) {
        String s = "SELECT * FROM " + DBConnectionThread.getDBSchema() + ".querymominstance,"
                + DBConnectionThread.getDBSchema() + ".role WHERE role.id=roleid ORDER BY role,instance;";
        try (ResultSet rs = stmt.executeQuery(s)) {
          while (rs.next()) {
            QueryMomInstance loc = new QueryMomInstance(rs);
//        Util.prt("MakeEdgeMomInstance() i="+v.size()+" is "+loc.getEdgeMomInstance());
            v.add(loc);
          }
        }
      }
    } catch (SQLException e) {
      Util.SQLErrorPrint(e, "makeEdgeMomInstances() on table SQL failed");
    }
  }
}
