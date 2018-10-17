/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.adslserverdb;
import java.sql.*;

/**
 * Epoch.java
 * If the name of the class does not match the name of the field in the class you may
 * need to modify the rs.getSTring in the constructor.
 *
 * This Epoch templace for creating a MySQL database object.  It is not
 * really needed by the EpochPanel which uses the DBObject system for
 * changing a record in the database file.  However, you have to have this
 * shell to create objects containing a MySQL record for passing around
 * and manipulating.  There are two constructors normally given:
 *<br>
 * 1)  passed a result set positioned to the MySQL record to decode
 * <br> 2) Passed all of the data arguments needed to build the same set
 *     of data.  
 *<br>
 * The Epoch should be replaced with capitalized version (class name) for the
 * file.  epoch should be replaced with the lower case version (for variables).
 *
 * Both the constructors use the "makeEpoch(list of data args) to set the
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
public class ADSLEpoch     //implements Comparator
{   // static ArrayList enumName;   // this need to be populated in makeEpoch()
  
  /** Creates a new instance of Epoch */
  int ID;                   // This is the MySQL ID (should alwas be named "ID"
  String epoch;             // This is the main key which should have same name
                            // as the table which it is a key to.  That is
                            // Table "category" should have key "category"
  
  // USER: All fields of file go here
  //  double longitude
  Timestamp Aeff, Aend,Deff,Dend,Seff,Send,Leff,Lend;
  String A, D, S, L;
  double latitude,longitude,elevation,depthOfBurial,overrideLatitude,
          overrideElevation,overrideLongitude;
  double localx,localy,localz;
  String localDatum,sitename,ISCOverrideSitename,fdsnNetworkCode,seedChannels,seedRates,source;
  int stationID, locationID;
  int attributable, alias;
  
  // Put in correct detail constructor here.  Use makeEpoch() by filling in all
  // the rs.get???("fieldName") for all of the arguments
  /** Create a instance of this table row from a positioned result set.
   *@param rs The ResultSet already SELECTED
   *@exception SQLException SQLException If the ResultSet is Improper, network is down, or database is down
   */
  public ADSLEpoch(ResultSet rs) throws SQLException {  //USER: add all data field here
    makeEpoch(rs.getInt("ID"), 
            rs.getString("A"),rs.getTimestamp("Aeff"), rs.getTimestamp("Aend"),
            rs.getString("D"),rs.getTimestamp("Deff"), rs.getTimestamp("Dend"),
            rs.getString("S"),rs.getTimestamp("Seff"), rs.getTimestamp("Send"),
            rs.getString("L"),rs.getTimestamp("Leff"), rs.getTimestamp("Lend"),
            rs.getDouble("latitude"),rs.getDouble("longitude"),rs.getDouble("elevation"),rs.getDouble("depthofburial"),
            rs.getDouble("overridelatitude"),rs.getDouble("overridelongitude"),
            rs.getDouble("overrideelevation"),rs.getDouble("localx"),rs.getDouble("localy"),
            rs.getDouble("localz"),rs.getString("localdatum"),rs.getString("seedchannels"),
            rs.getString("seedrates"),rs.getString("sitename"),rs.getString("iscoverridesitename"),
            rs.getString("fdsnnetworkcode"),rs.getInt("attributable"), rs.getInt("alias"),
            rs.getInt("stationid"), rs.getInt("locationid"), rs.getString("source")
                            // ,rs.getDouble(longitude)
    );
  }
  
  // Detail Constructor, match set up with makeEpoch(), this argument list should be
  // the same as for the result set builder as both use makeEpoch()
  /** Create a row record from individual variables.
   *@param inID The ID in the database, normally zero for this type of construction
   *@param loc The key value for the database (same a name of database table)
   */
  public ADSLEpoch(int inID,  String Ain,Timestamp A1, Timestamp A2,
          String Din,Timestamp D1, Timestamp D2, String Sin,Timestamp S1, Timestamp S2,
          String Lin,Timestamp L1, Timestamp L2, double lat,double lng,double elev,double depth,
          double overlat, double overlng, double overelev, double locx, double locy, double locz,
          String locdatum, String seedCh, String seedRt, String site, String ISCsite,
          String fdsnNet, int attrib, int aliasin, int statID, int locID, String source
    ) {
    makeEpoch(inID,  Ain,A1,  A2,
          Din,D1,  D2,  Sin, S1, S2,
           Lin, L1,  L2, lat, lng, elev,depth,
          overlat, overlng,  overelev,  locx,  locy,  locz,
           locdatum,  seedCh, seedRt, site,  ISCsite,
          fdsnNet, attrib, aliasin, statID, locID, source
    );
  }
  
  // This routine transfers to the local variables of this object all of the data variables
  // associated with this file.
  /** Internally set all of the field in our data to the passed data. 
   *@param inID The row ID in the database
   *@param loc The key (same as table name)
   **/
  private void makeEpoch(int inID,    //USER: add fields, double lon
          String Ain,Timestamp A1, Timestamp A2,
          String Din,Timestamp D1, Timestamp D2, String Sin,Timestamp S1, Timestamp S2,
          String Lin,Timestamp L1, Timestamp L2, double lat,double lng,double elev,double depth,
          double overlat, double overlng, double overelev, double locx, double locy, double locz,
          String locdatum, String seedCh, String seedRt, String site, String ISCsite,
          String fdsnNet, int attrib, int aliasin, int statID, int locID, String src
  ) {
    ID = inID;  
    //USER:  Put asssignments to all fields from arguments here
    
    // ENUM example:
    //if(qtypeEnum == null) qtypeEnum=FUtil.getEnum(JDBConnection.getConnection("DATABASE"), "nsnstation","quanterraType");
    //qtype = FUtil.enumStringToInt(qtypeEnum, st); 
    // Boolean example where ENUM "True", "False
    // cont40 = FUtil.isTrue(c40);          // c40 is a string with "true" or false
    A=(Ain+"     ").substring(0,5); D=(Din+"        ").substring(0,8); S=(Sin+"     ").substring(0,5); L=(Lin+"    ").substring(0,4);
    Aeff = new Timestamp(A1.getTime());
    Deff = new Timestamp(D1.getTime());
    Seff = new Timestamp(S1.getTime());
    Leff = new Timestamp(L1.getTime());
    Aend = new Timestamp(A2.getTime());
    Dend = new Timestamp(D2.getTime());
    Send = new Timestamp(S2.getTime());
    Lend = new Timestamp(L2.getTime());
    latitude=lat; longitude=lng; elevation=elev; depthOfBurial=depth; overrideLatitude=overlat;
    overrideLongitude=overlng; overrideElevation=overelev; localx=locx;localy=locy; localz=locz;
    localDatum=locdatum; seedChannels=seedCh; seedRates=seedRt; sitename=site;
    ISCOverrideSitename=ISCsite; fdsnNetworkCode=fdsnNet;attributable=attrib; 
    alias=aliasin; stationID=statID; locationID=locID; source=src;
    
  }
  public String getKey() {return A+D+S+L;}
  public String userKey() {return A.trim()+"."+D.trim()+"."+S.trim()+"."+L.trim();}
  /** return the key name as the string
   *@return the key name */
  public String toString2() { return A+"."+D+"."+S+"."+L+" "+latitude+" "+longitude+" "+elevation+" "+
          Aeff+" "+Aend+" "+Deff+" "+Dend+" "+Leff+" "+Lend+" alias="+alias+" src="+source;}
  /** return the key name as the string
   *@return the key name */
  @Override
  public String toString() { return A+"."+D+"."+S+"."+L+" "+latitude+" "+longitude+" "+elevation+" "+
          Aeff+" "+Aend+" "+Deff+" "+Dend+" "+Seff+" "+Send+" "+Leff+" "+Lend;}
  // getter
  
  // standard getters
  /** Get the database ID for the row.
   *@return The database ID for the row */
  public int getID() {return ID;}
  /** Get the key name for the row.
   *@return The key name string for the row
   */
  public String getEpoch() {return "";}
  
  
  //USER:  All field getters here
  //  public double getLongitude() { return longitude;}
  public String getAgency() {return A;}
  public String getDeployment() {return D;}
  public String getStation() {return S;}
  public String getLocation() {return L;}
  public Timestamp getAgencyEffective() {return Aeff;}
  public Timestamp getDeploymentEffective() {return Deff;}
  public Timestamp getStationEffective() {return Seff;}
  public Timestamp getLocationEffective() {return Leff;}
  public Timestamp getAgencyEnddate() {return Aend;}
  public Timestamp getDeploymentEnddate() {return Dend;}
  public Timestamp getStationEnddate() {return Send;}
  public Timestamp getLocationEnddate() {return Lend;}
  public String getSitename() {return sitename;}
  public String getISCOverrideSitename() {return ISCOverrideSitename;}
  public double getLatitude() {return latitude;}
  public double getLongitude() {return longitude;}
  public double getElevation() {return elevation;}
  public double getDepthOfBurial() {return depthOfBurial;}
  public double getOverrideLatitude() {return overrideLatitude;}
  public double getOverrideLongitude() {return overrideLongitude;}
  public double getOverrideElevation() {return overrideElevation;}
  public int getAttributable() {return attributable;}
  public boolean isAlias() {return (alias != 0);}
  public String getSEEDChannels() {return seedChannels;}
  public String getSEEDRates() {return seedRates;}
  public int getStationID() {return stationID;}
  public int getLocationID() {return locationID;}
  public String getFDSNNetworkCode() {return fdsnNetworkCode;}
  public String getSource() {return source;}

  // This is needed if you need to implement Comparator
  /*  public class EquipCompare implements Comparator {
    public int compare(Object a, Object b) {
      return ((Epoch) a).getEpoch().compareTo(((Epoch) b).getEpoch());
    }
    public boolean equals(Object a, Object b) {
      if( ((Epoch)a).getEpoch().equals( ((Epoch) b).getEpoch())) return true;
      return false;
    }
//  }*/
  
}
