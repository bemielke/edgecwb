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
 * LocationEpoch.java
 * If the name of the class does not match the name of the field in the class you may
 * need to modify the rs.getSTring in the constructor.
 *
 * This LocationEpoch templace for creating a MySQL database object.  It is not
 * really needed by the LocationEpochPanel which uses the DBObject system for
 * changing a record in the database file.  However, you have to have this
 * shell to create objects containing a MySQL record for passing around
 * and manipulating.  There are two constructors normally given:
 *<br>
 * 1)  passed a result set positioned to the MySQL record to decode
 * <br> 2) Passed all of the data arguments needed to build the same set
 *     of data.  
 *<br>
 * The LocationEpoch should be replaced with capitalized version (class name) for the
 * file.  locationepoch should be replaced with the lower case version (for variables).
 *
 * Both the constructors use the "makeLocationEpoch(list of data args) to set the
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
public class LocationEpoch     //implements Comparator
{   // static ArrayList enumName;   // this need to be populated in makeLocationEpoch()
  
  /** Creates a new instance of LocationEpoch */
  private int ID;                   // This is the MySQL ID (should alwas be named "ID"
  //private String locationepoch;             // This is the main key which should have same name
                            // as the table which it is a key to.  That is
                            // Table "category" should have key "category"
  
  // USER: All fields of file go here
  //  double longitude
  private int locationID;
  private String locationCode;
  private int ownerID;
  private Timestamp effective;
  private Timestamp enddate;
  private double latitude,longitude,elevation,depthOfBurial,localx, localy, localz;
  private String localDatum;
  private String SEEDChannels;
  private String SEEDRates;
  
  // Put in correct detail constructor here.  Use makeLocationEpoch() by filling in all
  // the rs.get???("fieldName") for all of the arguments
  /** Create a instance of this table row from a positioned result set
   *@param rs The ResultSet already SELECTED
   *@exception SQLException SQLException If the ResultSet is Improper, network is down, or database is down
   */
  public LocationEpoch(ResultSet rs) throws SQLException {  //USER: add all data field here
    makeLocationEpoch(rs.getInt("ID"), 
            rs.getInt("locationid"), rs.getString("locationcode"),rs.getInt("ownerid"),
            rs.getTimestamp("effective"), rs.getTimestamp("enddate"),
            rs.getDouble("latitude"),rs.getDouble("longitude"),rs.getDouble("elevation"),
            rs.getDouble("depthofburial"),rs.getDouble("localx"),
            rs.getDouble("localy"),rs.getDouble("localz"),rs.getString("localDatum"),
            rs.getString("seedchannels"), rs.getString("seedrates")
    );
  }
  
  // Detail Constructor, match set up with makeLocationEpoch(), this argument list should be
  // the same as for the result set builder as both use makeLocationEpoch()
  /** create a row record from individual variables
   *@param inID The ID in the database, normally zero for this type of construction
   *@param loc The key value for the database (same a name of database table)
   */
  public LocationEpoch(int inID, int locID, String locCode, int owner,  Timestamp eff,
          Timestamp end, double lat, double lng, double elev, double depth,
          double locx, double locy, double locz, String datum, 
          String chans, String rates
    ) {
    makeLocationEpoch(inID, locID, locCode, owner, eff,end,lat,lng,elev,depth,
            locx,locy, locz,datum, chans,rates
    );
  }
  
  // This routine transfers to the local variables of this object all of the data variables
  // associated with this file.
  /** internally set all of the field in our data to the passed data 
   *@param inID The row ID in the database
   *@param loc The key (same as table name)
   **/
  private void makeLocationEpoch(int inID, int locID, String locCode, int owner, Timestamp eff,
          Timestamp end, double lat, double lng, double elev, double depth,
          double locx, double locy, double locz, String datum, 
          String chans, String rates
  ) {
    ID = inID;       // longitude = lon
    //USER:  Put asssignments to all fields from arguments here
    
    // ENUM example:
    //if(qtypeEnum == null) qtypeEnum=FUtil.getEnum(JDBConnection.getConnection("DATABASE"), "nsnstation","quanterraType");
    //qtype = FUtil.enumStringToInt(qtypeEnum, st); 
    // Boolean example where ENUM "True", "False
    // cont40 = FUtil.isTrue(c40);          // c40 is a string with "true" or false
    locationID=locID; locationCode=locCode; ownerID=owner; 
    effective=eff; enddate=end; latitude=lat; longitude=lng; elevation=elev; depthOfBurial=depth;
    localx=locx; localy=locy; localz=locz; localDatum=datum; 
    SEEDChannels=chans; SEEDRates=rates;
    locationCode = (locationCode+"  ").substring(0,2).replaceAll(" ", "-");
  }

  /** return the key name as the string
   *@return the key name */
  public String toString2() { return locationCode+" "+effective.toString().substring(0,16)+"-"+enddate.toString().substring(0,16);}
  public String toString3() { return locationCode+" "+effective.toString().substring(0,16)+"-"+enddate.toString().substring(0,16)+
          " "+latitude+" "+longitude+" "+elevation;}
  /** return the key name as the string
   *@return the key name */
  @Override
  public String toString() { return locationCode+" "+effective.toString().substring(0,16)+"-"+enddate.toString().substring(0,16);}
  // getter
  
  // standard getters
  /** get the database ID for the row
   *@return The database ID for the row*/
  public int getID() {return ID;}
  /** get the key name for the row
   *@return The key name string for the row
   */
  //public String getLocationEpoch() {return locationepoch;}
  
  
  //USER:  All field getters here
  //  public double getLongitude() { return longitude;}
  public int getLocationID() {return locationID;}
  public String getLocationCode() {return locationCode;}
  public int getOwnerID() {return ownerID;}
  public Timestamp getEffective() {return effective;}
  public Timestamp getEnddate() {return enddate;}
  public double getLatitude() {return latitude;}
  public double getLongitude() {return longitude;}
  public double getElevation() {return elevation;}
  public double getDepthOfBurial() {return depthOfBurial;}
  public double getLocalX() {return localx;}
  public double getLocalY() {return localy;}
  public double getLocalZ() {return localz;}
  public String getLocalDatum() {return localDatum;}
  public String SEEDChannels() {return SEEDChannels;}
  public String SEEDRates() {return SEEDRates;}


  
  // This is needed if you need to implement Comparator
  /*  public class EquipCompare implements Comparator {
    public int compare(Object a, Object b) {
      return ((LocationEpoch) a).getLocationEpoch().compareTo(((LocationEpoch) b).getLocationEpoch());
    }
    public boolean equals(Object a, Object b) {
      if( ((LocationEpoch)a).getLocationEpoch().equals( ((LocationEpoch) b).getLocationEpoch())) return true;
      return false;
    }
//  }*/
  
}
