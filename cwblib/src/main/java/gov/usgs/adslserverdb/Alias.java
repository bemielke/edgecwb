/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.adslserverdb;

//package gov.usgs.edge.template;
//import java.util.Comparator;
import java.sql.*;

/**
 * Alias.java
 * If the name of the class does not match the name of the field in the class you may
 * need to modify the rs.getSTring in the constructor.
 *
 * This Alias template for creating a database database object.  It is not
 * really needed by the AliasPanel which uses the DBObject system for
 * changing a record in the database file.  However, you have to have this
 * shell to create objects containing a database record for passing around
 * and manipulating.  There are two constructors normally given:
 *<br>
 * 1)  passed a result set positioned to the database record to decode
 * <br> 2) Passed all of the data arguments needed to build the same set
 *     of data.  
 *<br>
 * The Alias should be replaced with capitalized version (class name) for the 
 * file.  alias should be replaced with the lower case version (for variables).
 *
 * Both the constructors use the "makeAlias(list of data args) to set the
 * local variables to the value.  The result set just uses the 
 * rs.get???("fieldname") to get the data from the database where the other
 * passes the arguments from the caller.
 * 
 * <br> Notes on Enums :
 *<br> * data class should have code like :
 *<br>  import java.util.ArrayList;          /// Import for Enum manipulation
 *<br>    .
 * <br>  static ArrayList fieldEnum;         // This list will have Strings corresponding to enum
 *<br>        .
 *<br>        .
 *<br>     // To convert an enum we need to create the fieldEnum ArrayList.  Do only once(static)
 *<br>     if(fieldEnum == null) fieldEnum = FUtil.getEnum(JDBConnection.getConnection("DATABASE"),"table","fieldName");
 *  <br>   
 * <br>   // Get the int corresponding to an Enum String for storage in the data base and 
 *  <br>  // the index into the JComboBox representing this Enum 
 * <br>   localVariable = FUtil.enumStringToInt(fieldEnum, enumString);
  *<br>  Created on July 8, 2002, 1:23 PM
 *
 * @author  D.C. Ketchum
 */
public class Alias     //implements Comparator
{   // static ArrayList enumName;   // this need to be populated in makeAlias()
  
  /** Creates a new instance of Alias */
  int ID;                   // This is the database ID (should alwas be named "ID"

  
  // USER: All fields of file go here
  //  double longitude
  int agencyEpochID, deploymentEpochID,aliasedAgencyEpochID,aliasedDeploymentID,aliasedStationID,aliasedLocationID;
  Timestamp effective,enddate;
  String station,location,description;
  int attributable;

  
  // Put in correct detail constructor here.  Use makeAlias() by filling in all
  // the rs.get???("fieldName") for all of the arguments
  /** Create a instance of this table row from a positioned result set.
   *@param rs The ResultSet already SELECTED
   *@exception SQLException SQLException If the ResultSet is Improper, network is down, or database is down
   */
  public Alias(ResultSet rs) throws SQLException {  //USER: add all data field here
    reload(rs);
  }
  /** Reload the object data from a given result set.
   *
   * @param rs The ResultSet to load the object from
   * @throws SQLException If one is thrown
   */
  public final void reload(ResultSet rs) throws SQLException {
     makeAlias(rs.getInt("ID"), rs.getInt("agencyepochid"), rs.getInt("deploymentepochid"),
             rs.getTimestamp("effective"), rs.getTimestamp("enddate"), rs.getInt("aliasedagencyepochid"),
             rs.getInt("aliaseddeploymentid"), rs.getInt("aliasedstationid"), 
             rs.getInt("aliasedlocationid"), rs.getString("station"),rs.getString("location"),
             rs.getString("description"), rs.getInt("attributable")
                            // ,rs.getDouble(longitude)
    );
  }
  // Detail Constructor, match set up with makeAlias(), this argument list should be
  // the same as for the result set builder as both use makeAlias()
  /** Create a row record from individual variables.
   *@param inID The ID in the database, normally zero for this type of construction
   *@param loc The key value for the database (same a name of database table)
   */
  public Alias(int inID,    //USER: add fields, double lon
      int agencyID, int deployID, Timestamp eff, Timestamp ended, int aliasedAgencyID,
      int aliasedDeployID, int  aliasedStatID,  int aliasedLocID, String stat, String loc,
      String desc, int attrib
    ) {
    makeAlias(inID, agencyID, deployID, eff, ended,aliasedAgencyID, aliasedDeployID,
            aliasedStatID, aliasedLocID, stat,loc,desc,attrib
    );
  }
  
  // This routine transfers to the local variables of this object all of the data variables
  // associated with this file.
  /** Internally set all of the field in our data to the passed data. 
   *@param inID The row ID in the database
   *@param loc The key (same as table name)
   **/
  private void makeAlias(int inID,     //USER: add fields, double lon
      int agencyID, int deployID, Timestamp eff, Timestamp ended, int aliasedAgencyID,
      int aliasedDeployID, int  aliasedStatID,  int aliasedLocID, String stat, String loc,
      String desc, int attrib
  ) {
    ID = inID;  
    //USER:  Put asssignments to all fields from arguments here
    
    // ENUM example:
    //if(qtypeEnum == null) qtypeEnum=FUtil.getEnum(JDBConnection.getConnection("DATABASE"), "nsnstation","quanterraType");
    //qtype = FUtil.enumStringToInt(qtypeEnum, st); 
    // Boolean example where ENUM "True", "False
    // cont40 = FUtil.isTrue(c40);          // c40 is a string with "true" or false
    agencyEpochID= agencyID; deploymentEpochID=deployID; effective=eff; enddate=ended;
    aliasedAgencyEpochID=aliasedAgencyID; aliasedDeploymentID=aliasedDeployID;
    aliasedStationID=aliasedStatID; aliasedLocationID=aliasedLocID; station=stat;
    location=loc;description=desc;attributable=attrib;
    
  }
  /** Return the key name as the string.
   *@return the key name */
  public String toString2() { return station+" "+location+" aa="+aliasedAgencyEpochID+
          " ad="+aliasedDeploymentID+" as="+aliasedStationID+" deploy="+deploymentEpochID+
          " agency="+agencyEpochID+" eff="+effective.toString().substring(0,16)+
          " end="+enddate.toString().substring(0,16);}
  
  /** Return the key name as the string.
   *@return the key name */
  @Override
  public String toString() { return station+" "+location+" aa="+aliasedAgencyEpochID+
          " ad="+aliasedDeploymentID+" as="+aliasedStationID+" deploy="+deploymentEpochID+
          " agency="+agencyEpochID+" eff="+effective.toString().substring(0,16)+
          " end="+enddate.toString().substring(0,16);}
  // getter
  
  // standard getters
  /** Get the database ID for the row.
   *@return The database ID for the row*/
  public int getID() {return ID;}
  /** Get the key name for the row.
   *@return The key name string for the row
   */
  
  
  //USER:  All field getters here
  //  public double getLongitude() { return longitude;}
  public int getAgencyEpochID() {return agencyEpochID;}
  public int getDeploymentEpochID() {return deploymentEpochID;}
  public int getAliasedAgencyEpochID() {return aliasedAgencyEpochID;}
  public int getAliasedDeploymentID() {return aliasedDeploymentID;}
  public int getAliasedLocationID() {return aliasedLocationID;}
  public int getAttributable() {return attributable;}
  public String getStationCode() {return station;}
  public String getLocationCode() {return location;}
  public String getDescription() {return description;}
  public Timestamp getEffective() {return effective;}
  public Timestamp getEnddate() {return enddate;}
  
  
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
  
}
