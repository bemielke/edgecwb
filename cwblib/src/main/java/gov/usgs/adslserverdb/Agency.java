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
import gov.usgs.anss.db.DBConnectionThread;
import gov.usgs.anss.util.Util;
import java.sql.*;
import java.util.ArrayList;
//import java.util.*;
/**
 * Agency.java
 * If the name of the class does not match the name of the field in the class you may
 * need to modify the rs.getSTring in the constructor.
 *
 * This Agency templace for creating a MySQL database object.  It is not
 * really needed by the AgencyPanel which uses the DBObject system for
 * changing a record in the database file.  However, you have to have this
 * shell to create objects containing a MySQL record for passing around
 * and manipulating.  There are two constructors normally given:
 *<br>
 * 1)  passed a result set positioned to the MySQL record to decode
 * <br> 2) Passed all of the data arguments needed to build the same set
 *     of data.  
 *<br>
 * The Agency should be replaced with capitalized version (class name) for the
 * file.  agency should be replaced with the lower case version (for variables).
 *
 * Both the constructors use the "makeAgency(list of data args) to set the
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
public class Agency     //implements Comparator
{   // static ArrayList enumName;   // this need to be populated in makeAgency()
  public static ArrayList<Agency> agencies;             // ArrayList containing objects of this Agency Type
  
  /** Creates a new instance of Agency */
  private int ID;                   // This is the MySQL ID (should alwas be named "ID"
  private String agency;             // This is the main key which should have same name
                            // as the table which it is a key to.  That is
                            // Table "category" should have key "category"
  private String longname;
  private Timestamp effective;
  private Timestamp enddate;
  private int ownerID;
  private int POCID;

  
  // USER: All fields of file go here
  //  double longitude

  
  // Put in correct detail constructor here.  Use makeAgency() by filling in all
  // the rs.get???("fieldName") for all of the arguments
  /** Create a instance of this table row from a positioned result set.
   *@param rs The ResultSet already SELECTED
   *@exception SQLException SQLException If the ResultSet is Improper, network is down, or database is down
   */
  public Agency(ResultSet rs) throws SQLException {  //USER: add all data field here
    makeAgency(rs.getInt("ID"), rs.getString("agency"),
            rs.getString("longname"),rs.getInt("pocid"),rs.getInt("ownerid"), rs.getTimestamp("effective"),rs.getTimestamp("enddate")
                            // ,rs.getDouble(longitude)
    );
  }
  
  // Detail Constructor, match set up with makeAgency(), this argument list should be
  // the same as for the result set builder as both use makeAgency()
  /** Create a row record from individual variables.
   *@param inID The ID in the database, normally zero for this type of construction
   *@param loc The key value for the database (same a name of database table)
   * @param lname The long name for the agency
   * @param poc The ID of a POC record for this agency
   * @param owner The ID of the owner of this agency
   * @param eff The effective date of this agency's validity
   * @param end The end date for this agency's validity
   */
  public Agency(int inID, String loc   //USER: add fields, double lon
    , String lname, int poc, int owner, Timestamp eff, Timestamp end) {
    makeAgency(inID, loc, lname, poc, owner, eff,end       //, lon
    );
  }
  
  // This routine transfers to the local variables of this object all of the data variables
  // associated with this file.
  /** Internally set all of the field in our data to the passed data. 
   *@param inID The row ID in the database
   *@param loc The key (same as table name)
   **/
  private void makeAgency(int inID, String loc    //USER: add fields, double lon
          , String lname, int poc, int owner, Timestamp eff, Timestamp end
  ) {
    ID = inID;  agency=loc;     // longitude = lon
    //USER:  Put asssignments to all fields from arguments here
    longname=lname; effective=eff; enddate=end; POCID=poc; ownerID=owner;
    
    // ENUM example:
    //if(qtypeEnum == null) qtypeEnum=FUtil.getEnum(JDBConnection.getConnection("DATABASE"), "nsnstation","quanterraType");
    //qtype = FUtil.enumStringToInt(qtypeEnum, st); 
    // Boolean example where ENUM "True", "False
    // cont40 = FUtil.isTrue(c40);          // c40 is a string with "true" or false

    
  }
  /** Return the key name as the string.
   *@return the key name */
  public String toString2() { return agency;}
  /** Return the key name as the string.
   *@return the key name */
  @Override
  public String toString() { return agency;}
  // getter
  
  // standard getters
  /** Get the database ID for the row.
   *@return The database ID for the row*/
  public int getID() {return ID;}
  /** Get the key name for the row.
   *@return The key name string for the row
   */
  public String getAgency() {return agency;}
  
  
  //USER:  All field getters here
  //  public double getLongitude() { return longitude;}
  public String getLongName() {return longname;}
  public int getPOCID() {return POCID;}
  public Timestamp getEffective() {return effective;}
  public Timestamp getEndDate() {return enddate;}
  public int getOwnerID() {return ownerID;}
  // This is needed if you need to implement Comparator
  /*  public class EquipCompare implements Comparator {
    public int compare(Object a, Object b) {
      return ((Agency) a).getAgency().compareTo(((Agency) b).getAgency());
    }
    public boolean equals(Object a, Object b) {
      if( ((Agency)a).getAgency().equals( ((Agency) b).getAgency())) return true;
      return false;
    }
//  }*/
  // This routine should only need tweeking if key field is not same as table name
  public static void makeAgencys() {
    if (agencies != null) return;
    agencies=new ArrayList<Agency>(100);
    try {
      try (Statement stmt = DBConnectionThread.getThread(DBConnectionThread.getDBSchema()).getNewStatement(false) // used for query
      ) {
        String s = "SELECT * FROM agency ORDER BY agency;";
        try (ResultSet rs = stmt.executeQuery(s)) {
          while (rs.next()) {
            Agency loc = new Agency(rs);
            //Util.prt("MakeAgency() i="+v.size()+" is "+loc.getAgency());
            agencies.add(loc);
          }
        }
      }
    }
    catch (SQLException e) {
      Util.SQLErrorPrint(e,"makeAgencys() on table SQL failed");
    }    
  }  
  
  public static Agency getAgencyWithID( int ID) {
    if(agencies == null) makeAgencys();
    for(Agency agency: agencies) {
      if(agency.getID() == ID) return agency;
    }
    return null;
  }
}
