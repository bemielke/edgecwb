/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.edge.config;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Cpu.java If the name of the class does not match the name of the field in the class you may need
 * to modify the rs.getSTring in the constructor.
 *
 * This Cpu templace for creating a database database object. It is not really needed by the
 * CpuPanel which uses the DBObject system for changing a record in the database file. However, you
 * have to have this shell to create objects containing a database record for passing around and
 * manipulating. There are two constructors normally given:
 * <br>
 * 1) passed a result set positioned to the database record to decode
 * <br> 2) Passed all of the data arguments needed to build the same set of data.
 * <br>
 * The Cpu should be replaced with capitalized version (class name) for the file. cpu should be
 * replaced with the lower case version (for variables).
 *
 * Both the constructors use the "makeCpu(list of data args) to set the local variables to the
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
public final class Cpu {    //implements Comparator
  // static ArrayList enumName;   // this need to be populated in makeCpu()

  /**
   * Creates a new instance of Cpu
   */
  private int ID;                   // This is the database ID (should alwas be named "ID"
  private String cpu;             // This is the main key which should have same name
  // as the table which it is a key to.  That is
  // Table "category" should have key "category"

  // USER: All fields of file go here
  //  double longitude
  private String ipadr;
  private String os;
  private int hasdata;
  private String edgeprop;
  private String crontab;
  private int nodeNumber;

  // Put in correct detail constructor here.  Use makeCpu() by filling in all
  // the rs.get???("fieldName") for all of the arguments
  /**
   * Create a instance of this table row from a positioned result set
   *
   * @param rs The ResultSet already SELECTED
   * @exception SQLException SQLException If the ResultSet is Improper, network is down, or database
   * is down
   */
  public Cpu(ResultSet rs) throws SQLException {  //USER: add all data field here
    makeCpu(rs.getInt("ID"), rs.getString("cpu"),
            // ,rs.getDouble(longitude)
            rs.getString("ipadr"),
            rs.getString("os"),
            rs.getInt("hasdata"), rs.getString("edgeprop"),
            rs.getString("crontab"),
            rs.getInt("nodenumber")
    );
  }

  public void reload(ResultSet rs) throws SQLException {
    makeCpu(rs.getInt("ID"), rs.getString("cpu"),
            // ,rs.getDouble(longitude)
            rs.getString("ipadr"),
            rs.getString("os"),
            rs.getInt("hasdata"),
            rs.getString("edgeprop"),
            rs.getString("crontab"),
            rs.getInt("nodeNumber")
    );
  }

  // Detail Constructor, match set up with makeCpu(), this argument list should be
  // the same as for the result set builder as both use makeCpu()
  /**
   * create a row record from individual variables
   *
   * @param inID The ID in the database, normally zero for this type of construction
   * @param loc The key value for the database (same a name of database table)
   * @param inIPadr The IP address
   * @param inOS The operating system
   * @param inHasdata The has data flag
   * @param edgeprop contents of the edge.prop file for the server.
   * @param cron contents of the crontab for the server
   * @param nodeNum Nod number
   */
  public Cpu(int inID, String loc //USER: add fields, double lon
          ,
           String inIPadr, String inOS, int inHasdata, String edgeprop, String cron, int nodeNum
  ) {
    makeCpu(inID, loc //, lon
            ,
             inIPadr, inOS, inHasdata, edgeprop, cron, nodeNum
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
  private void makeCpu(int inID, String loc, //USER: add fields, double lon
          String inIPadr, String inOS, int inHasdata, String edgeprop, String cron,
          int nodeNum) {
    ID = inID;
    cpu = loc;     // longitude = lon
    //USER:  Put asssignments to all fields from arguments here
    ipadr = inIPadr;
    os = inOS;
    hasdata = inHasdata;
    this.edgeprop = edgeprop;
    crontab = cron;
    nodeNumber = nodeNum;

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
    return cpu;
  }

  /**
   * return the key name as the string
   *
   * @return the key name
   */
  @Override
  public String toString() {
    return cpu;
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
  public String getCpu() {
    return cpu;
  }

  public String getEdgeProp() {
    return edgeprop;
  }

  public String getCrontab() {
    return crontab;
  }

  public int getNodeNumber() {
    return nodeNumber;
  }
  //USER:  All field getters here
  //  public double getLongitude() { return longitude;}

  // This is needed if you need to implement Comparator
  /*  public class EquipCompare implements Comparator {
    public int compare(Object a, Object b) {
      return ((Cpu) a).getCpu().compareTo(((Cpu) b).getCpu());
    }
    public boolean equals(Object a, Object b) {
      if( ((Cpu)a).getCpu().equals( ((Cpu) b).getCpu())) return true;
      return false;
    }
//  }*/
}
