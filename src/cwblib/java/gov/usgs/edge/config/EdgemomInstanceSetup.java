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
//import java.util.ArrayList;

/**
 * Edgemomsetup.java If the name of the class does not match the name of the field in the class you
 * may need to modify the rs.getSTring in the constructor.
 *
 * This Edgemomsetup templace for creating a database database object. It is not really needed by
 * the EdgemomsetupPanel which uses the DBObject system for changing a record in the database file.
 * However, you have to have this shell to create objects containing a database record for passing
 * around and manipulating. There are two constructors normally given:
 * <br>
 * 1) passed a result set positioned to the database record to decode
 * <br> 2) Passed all of the data arguments needed to build the same set of data.
 * <br>
 * The Edgemomsetup should be replaced with capitalized version (class name) for the file.
 * edgemomsetup should be replaced with the lower case version (for variables).
 *
 * Both the constructors use the "makeEdgemomsetup(list of data args) to set the local variables to
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
public final class EdgemomInstanceSetup {    //implements Comparator
  // static ArrayList enumName;   // this need to be populated in makeEdgemomsetup()

  public static ArrayList<EdgemomInstanceSetup> edgemomInstanceSetups;             // ArrayList containing objects of this EdgeMomInstanceSetup Type

  /**
   * Creates a new instance of Edgemomsetup
   */
  private int ID;                   // This is the database ID (should alwas be named "ID"
  private String edgemomsetup;      // THis is the main key which is "role-account-tag"

  // USER: All fields of file go here
  //  double longitude
  private String tag;
  private int instanceID;
  private int priority;
  private int edgethreadID;
  private String args;
  private boolean disabled;
  private String logfile;
  private String comment;
  private int edgeFileID;
  private String configFile;
  private String config;

  // Put in correct detail constructor here.  Use makeEdgemomsetup() by filling in all
  // the rs.get???("fieldName") for all of the arguments
  /**
   * Create a instance of this table row from a positioned result set
   *
   * @param rs The ResultSet already SELECTED
   * @exception SQLException SQLException If the ResultSet is Improper, network is down, or database
   * is down
   */
  public EdgemomInstanceSetup(ResultSet rs) throws SQLException {  //USER: add all data field here
    makeEdgemomsetup(rs.getInt("ID"), "" + rs.getInt("ID"), rs.getString("tag"),
            // ,rs.getDouble(longitude)
            rs.getInt("instanceID"),
            rs.getInt("priority"),
            rs.getInt("edgethreadID"),
            rs.getString("args"),
            rs.getString("logfile"),
            rs.getString("comment"),
            rs.getInt("edgefileid"),
            rs.getString("configfile"),
            rs.getString("config"),
            rs.getInt("disabled")
    );
  }

  /**
   * Reload this with the variables in the result set
   *
   * @param rs The result set positioned to a row for this type
   * @throws SQLException
   */
  public void reload(ResultSet rs) throws SQLException {
    makeEdgemomsetup(rs.getInt("ID"), "" + rs.getInt("ID"), rs.getString("tag"),
            // ,rs.getDouble(longitude)
            rs.getInt("instanceID"),
            rs.getInt("priority"),
            rs.getInt("edgethreadID"),
            rs.getString("args"),
            rs.getString("logfile"),
            rs.getString("comment"),
            rs.getInt("edgefileid"),
            rs.getString("configfile"),
            rs.getString("config"),
            rs.getInt("disabled")
    );
  }

  // Detail Constructor, match set up with makeEdgemomsetup(), this argument list should be
  // the same as for the result set builder as both use makeEdgemomsetup()
  /**
   * create a row record from individual variables
   *
   * @param inID The ID in the database, normally zero for this type of construction
   * @param intag The value of the TAG in the file
   * @param inmom The edgeMom setup string
   * @param ininstanceID The roleID associated with this thread
   * @param inpriority The priority of this thread
   * @param inedgethreadID The ID of the edgethread table for this thread
   * @param inargs The arguments to pass to the thread on startup
   * @param inlogfile the log file name for the thread (set after >> on the line)
   * @param incomment A comment about the line
   * @param edgeID Which edge file ID
   * @param configfile A configuration file name
   * @param config The configuration file contents.
   * @param disab If true, this thread will be disabled.
   *
   */
  public EdgemomInstanceSetup(int inID, String inmom, String intag, //USER: add fields, double lon
          int ininstanceID,
          int inpriority,
          int inedgethreadID,
          String inargs,
          String inlogfile,
          String incomment,
          int edgeID,
          String configfile,
          String config,
          int disab
  ) {
    makeEdgemomsetup(inID, inmom, intag, //, lon
            ininstanceID,
            inpriority,
            inedgethreadID,
            inargs,
            inlogfile,
            incomment,
            edgeID, configfile, config, disab
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
  private void makeEdgemomsetup(int inID, String inmom, String intag,
          //USER: add fields, double lon
          int ininstanceID,
          int inpriority,
          int inedgethreadID,
          String inargs,
          String inlogfile,
          String incomment,
          int edgeID, String file, String config, int disab
  ) {
    ID = inID;
    edgemomsetup = inmom;
    tag = intag;     // longitude = lon
    //USER:  Put asssignments to all fields from arguments here
    instanceID = ininstanceID;
    priority = inpriority;
    edgethreadID = inedgethreadID;
    args = inargs;
    logfile = inlogfile;
    comment = incomment;
    configFile = file;
    this.config = config;
    disabled = (disab != 0);
    edgeFileID = edgeID;
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
    return "" + ID;
  }

  /**
   * return the key name as the string
   *
   * @return the key name
   */
  @Override
  public String toString() {
    return (getCommandLine().length() > 65 ? getCommandLine().substring(0, 65) : getCommandLine());
  }

  /**
   * return the text representing this string
   *
   * @return the key name
   */
  public String toString3() {
    String x, r;
    if (instanceID == 0) {
      r = "NoRole";
    } else {
      r = RoleInstance.getRoleWithID(instanceID).toString();
    }
    x = r.trim() + "-";
    x += getCommandLine();
    return (x.length() > 65 ? x.substring(0, 65) : x);
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

  public String getTag() {
    return tag;
  }

  public String getEdgemomsetup() {
    return edgemomsetup;
  }

  public int getInstanceID() {
    return instanceID;
  }

  public EdgeMomInstance getInstance() {
    return EdgeMomInstance.getEdgeMomInstanceWithID(instanceID);
  }

  public int getPriority() {
    return priority;
  }

  public int getEdgethreadID() {
    return edgethreadID;
  }

  public String getEdgethreadName() {
    return Edgethread.getEdgethreadWithID(edgethreadID).getEdgethread();
  }

  public String getArgs() {
    return args;
  }

  public String getLogfile() {
    return logfile;
  }

  public String getComment() {
    if (comment.length() == 0) {
      return "#";
    } else {
      return (comment.charAt(0) != '#' ? "#" : "") + comment;
    }
  }

  public int getEdgeFileID() {
    return edgeFileID;
  }

  public String getConfigFilename() {
    return configFile;
  }

  public String getConfigContent() {
    return config;
  }

  public boolean isDisabled() {
    return disabled;
  }

  public String getCommandLine() {
    return (disabled ? "#" : "") + tag + (priority == 0 ? "" : "/" + priority) + ":" + getEdgethreadName() + ":" + (args.trim().length() == 0 ? "-empty" : args.trim()) + (logfile.equals("") ? "" : " >>" + logfile.trim());
  }

  // This is needed if you need to implement Comparator
  /*  public class EquipCompare implements Comparator {
    public int compare(Object a, Object b) {
      return ((Edgemomsetup) a).getEdgemomsetup().compareTo(((Edgemomsetup) b).getEdgemomsetup());
    }
    public boolean equals(Object a, Object b) {
      if( ((Edgemomsetup)a).getEdgemomsetup().equals( ((Edgemomsetup) b).getEdgemomsetup())) return true;
      return false;
    }
//  }*/
  /**
   * Get the item corresponding to database ID
   *
   * @param ID the database Row ID
   * @return The EdgeMomInstanceSetup row with this ID
   */
  public static EdgemomInstanceSetup getEdgeMomInstanceSetupWithID(int ID) {
    if (edgemomInstanceSetups == null) {
      makeEdgeMomInstanceSetups();
    }
    for (EdgemomInstanceSetup setup : edgemomInstanceSetups) {
      if (setup.getID() == ID) {
        return setup;
      }
    }
    return null;
  }

  // This routine should only need tweeking if key field is not same as table name
  public static void makeEdgeMomInstanceSetups() {
    EdgeMomInstance.makeEdgeMomInstances();
    if (edgemomInstanceSetups != null) {
      return;
    }
    edgemomInstanceSetups = new ArrayList<>(100);
    try {
      //String s = "SELECT * FROM "+DBConnectionThread.getDBSchema()+".edgeMomInstance ORDER BY edgeMomInstance;";
      try (Statement stmt = DBConnectionThread.getConnection(DBConnectionThread.getDBSchema()).createStatement() // used for query
              ) {
        //String s = "SELECT * FROM "+DBConnectionThread.getDBSchema()+".edgeMomInstance ORDER BY edgeMomInstance;";
        String s = "SELECT * FROM edge.instancesetup,edge.instance where instance.id=instancesetup.instanceid ORDER BY tag,instance;";
        try (ResultSet rs = stmt.executeQuery(s)) {
          while (rs.next()) {
            EdgemomInstanceSetup loc = new EdgemomInstanceSetup(rs);
//        Util.prt("MakeEdgeMomInstanceSetup() i="+edgemomInstanceSetups.size()+" is "+loc.getEdgeMomInstanceSetup());
            edgemomInstanceSetups.add(loc);
          }
        }
      }
    } catch (SQLException e) {
      Util.SQLErrorPrint(e, "makeEdgeMomInstanceSetups() on table SQL failed");
    }
  }
}
