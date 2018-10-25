/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */

package gov.usgs.anss.metadatagui;
//package gov.usgs.edge.template;
//import java.util.Comparator;
import gov.usgs.anss.util.Util;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;

/**
 * Station.java
 * If the name of the class does not match the name of the field in the class you may
 * need to modify the rs.getSTring in the constructor.
 *
 * This Station templace for creating a  database object.  It is not
 * really needed by the StationPanel which uses the DBObject system for
 * changing a record in the database file.  However, you have to have this
 * shell to create objects containing a  record for passing around
 * and manipulating.  There are two constructors normally given:
 *<br>
 * 1)  passed a result set positioned to the database record to decode
 * <br> 2) Passed all of the data arguments needed to build the same set
 *     of data.  
 *<br>
 * The Station should be replaced with capitalized version (class name) for the 
 * file.  station should be replaced with the lower case version (for variables).
 *
 * Both the constructors use the "makeStation(list of data args) to set the
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
 *<br>     if(fieldEnum == null) fieldEnum = FUtil.getEnum(DBConnectionThread.getConnection("metadata"),"table","fieldName");
 *  <br>   
 * <br>   // Get the int corresponding to an Enum String for storage in the data base and 
 *  <br>  // the index into the JComboBox representing this Enum 
 * <br>   localVariable = FUtil.enumStringToInt(fieldEnum, enumString);
  *<br>  Created on July 8, 2002, 1:23 PM
 *
 * @author  D.C. Ketchum
 */
public class Station     //implements Comparator
{   // static ArrayList enumName;   // this need to be populated in makeStation()
  
  /** Creates a new instance of Station */
  int ID;                   // This is the  ID (should alwas be named "ID"
  String station;             // This is the main key which should have same name
                            // as the table which it is a key to.  That is
                            // Table "category" should have key "category"
  
  // USER: All fields of file go here
  //  double longitude
  String region;
  String seedSiteName;
  String irname;
  int typemask;
  String irowner;
  String seedOwner;
  int groupmask;
  int flags;
  Timestamp opened;
  Timestamp closed;
  String seedurl;
  String xmlurl;
  double latitude;
  double longitude;
  double elevation;
  double seedlatitude;
  double seedlongitude;
  double seedelevation;
  String ircode;
  String otheralias;
  int status;
  
  //Difference Tolerances
  public static final int OPENED_TOLERANCE = 60000; //60 seconds;
  public static final int CLOSED_TOLERANCE = 60000; //60 seconds;
  public static final double LATITUDE_TOLERANCE = .0004d; //degrees
  public static final double LONGITUDE_TOLERANCE = .0004d; //degrees
  public static final double ELEVATION_TOLERANCE = 1.0d; //meters
  public static final double SEED_LATITUDE_TOLERANCE = .0004d; //degrees
  public static final double SEED_LONGITUDE_TOLERANCE = .0004d; //degrees
  public static final double SEED_ELEVATION_TOLERANCE = 1.0d; // meters
  

  
  // Put in correct detail constructor here.  Use makeStation() by filling in all
  // the rs.get???("fieldName") for all of the arguments
  /** Create a instance of this table row from a positioned result set
   *@param rs The ResultSet alreay SELECTED
   *@exception SQLException SQLException If the ResultSet is Improper, network is down, or database is down
   */
  public Station(ResultSet rs) throws SQLException {  //USER: add all data field here
    makeStation(rs.getInt("ID"), rs.getString("station"),
              rs.getString("region"),
              rs.getString("seedSiteName"),
              rs.getString("irname"),
              rs.getInt("typemask"),
              rs.getString("irowner"),
              rs.getString("seedOwner"),
              rs.getInt("groupmask"),
              rs.getInt("flags"),
              rs.getTimestamp("opened"),
              rs.getTimestamp("closed"),
              rs.getString("seedurl"),
              rs.getString("xmlurl"),
              rs.getDouble("latitude"),
              rs.getDouble("longitude"),
              rs.getDouble("elevation"),
              rs.getDouble("seedlatitude"),
              rs.getDouble("seedlongitude"),
              rs.getDouble("seedelevation"),
              rs.getString("ircode"),
              rs.getString("otheralias"),
              rs.getInt("status")
                            // ,rs.getDouble(longitude)
    );
  }
  
  // Detail Constructor, match set up with makeStation(), this argument list should be
  // the same as for the result set builder as both use makeStation()
  /** create a row record from individual variables
   *@param inID The ID in the database, normally zero for this type of constructin
   *@param loc The key value for the database (same a name of database table)
   */
  public Station(int inID, String loc,   //USER: add fields, double lon
            String region,
            String seedSiteName,
            String irname,
            int typemask,
            String irowner,
            String seedOwner,
            int groupmask,
            int flags,
            Timestamp opened,
            Timestamp closed,
            String seedurl,
            String xmlurl,
            double latitude,
            double longitude,
            double elevation,
            double seedlatitude,
            double seedlongitude,
            double seedelevation,
            String ircode,
            String otheralias,
            int status
          
    ) {
    makeStation(inID, loc,       //, lon
              region,
              seedSiteName,
              irname,
              typemask,
              irowner,
              seedOwner,
              groupmask,
              flags,
              opened,
              closed,
              seedurl,
              xmlurl,
              latitude,
              longitude,
              elevation,
              seedlatitude,
              seedlongitude,
              seedelevation,
              ircode,
              otheralias,
              status
    );
  }
  
  // This routine transfers to the local variables of this object all of the data variables
  // associated with this file.
  /** internally set all of the field in our data to the passsed data 
   *@param inID The row ID in the database
   *@param loc The key (same as table name)
   **/
  public void makeStation(int inID, String loc,    //USER: add fields, double lon
            String inregion,
            String inseedSiteName,
            String inirname,
            int intypemask,
            String inirowner,
            String inseedOwner,
            int ingroupmask,
            int inflags,
            Timestamp inopened,
            Timestamp inclosed,
            String inseedurl,
            String inxmlurl,
            double inlatitude,
            double inlongitude,
            double inelevation,
            double inseedlatitude,
            double inseedlongitude,
            double inseedelevation,
            String inircode,
            String inotheralias,
            int instatus
  ) {
    ID = inID;  station=loc;     // longitude = lon
    //USER:  Put asssignments to all fields from arguments here
      region = inregion;
      seedSiteName = inseedSiteName;
      irname = inirname;
      typemask = intypemask;
      irowner = inirowner;
      seedOwner = inseedOwner;
      groupmask = ingroupmask;
      flags = inflags;
      opened = inopened;
      closed = inclosed;
      seedurl = inseedurl;
      xmlurl = inxmlurl;
      latitude = inlatitude;
      longitude = inlongitude;
      elevation = inelevation;
      seedlatitude = inseedlatitude;
      seedlongitude = inseedlongitude;
      seedelevation = inseedelevation;
      ircode = inircode;
      otheralias = inotheralias;
      status = instatus;
    
    // ENUM example:
    //if(qtypeEnum == null) qtypeEnum=FUtil.getEnum(DBConnectionThread.getConnection("metadata"), "nsnstation","quanterraType");
    //qtype = FUtil.enumStringToInt(qtypeEnum, st); 
    // Boolean example where ENUM "True", "False
    // cont40 = FUtil.isTrue(c40);          // c40 is a string with "true" or false

    
  }
  /** return the key name as the string
   *@return the key name */
  public String toString2() { return station;}
  /** return the key name as the string
   *@return the key name */
  @Override
  public String toString() { return Util.rightPad(station,7)+"- " + opened.toString().substring(0,10);}
  // getter
  
  // standard getters
  /** get the database ID for the row
   *@return The database ID for the row*/
  public int getID() {return ID;}
  /** get the key name for the row
   *@return The key name string for the row
   */
  public String getStation() {return station;}
  
  
  //USER:  All field getters here
  //  public double getLongitude() { return longitude;}
  public String getRegion()  {return region ;}
  public String getSeedSiteName()  {return seedSiteName ;}
  public String getIrname()  {return irname ;}
  public int getTypemask()  {return typemask ;}
  public String getIrowner()  {return irowner ;}
  public String getSeedOwner()  {return seedOwner ;}
  public int getGroupmask()  {return groupmask ;}
  public int getFlags()  {return flags ;}
  public Timestamp getOpened()  {return opened ;}
  public Timestamp getClosed()  {return closed ;}
  public String getSeedurl()  {return seedurl ;}
  public String getXmlurl()  {return xmlurl ;}
  public double getLatitude()  {return (latitude == 0.&& seedlatitude != 0.)?seedlatitude: latitude ;}
  public double getLongitude()  {return  (longitude == 0. && seedlongitude != 0.? seedlongitude: longitude) ;}
  public double getElevation()  {return (latitude == 0. && seedlatitude != 0.) ?seedelevation:elevation;}
  public double getSeedlatitude()  {return seedlatitude ;}
  public double getSeedlongitude()  {return seedlongitude ;}
  public double getSeedelevation()  {return seedelevation ;}
  public String getIrcode()  {return ircode ;}
  public String getOtheralias()  {return otheralias ;}
  public int getStatus()  {return status ;}  
  public String getAlias() {
    StringBuilder sb = new StringBuilder(otheralias.length()+20);
    sb.append(station).append("\n");
    for(int i=0; i<otheralias.length(); i=i+7) sb.append(otheralias.substring(i,i+7)).append("\n");
    if(ircode != null) {
      if(ircode.equals("")) return sb.toString();
      else return (ircode+"       ").substring(0,7)+"\n"+sb.toString();
    }
    else {
      return otheralias;
    }
  }
  /** compare this station to another station and return results to string builder
   * 
   * @param s1 The other station epoch to compare
   * @param sb The StringBuilder to put any variances in
   * @param strict Should strings be strictly compared
   * @return true if the station epochs are identical (within reason).
   */
  public boolean diff(Station s1, StringBuilder sb, boolean strict) {
    boolean error = false;       // assume its o.k
    if(Math.abs(opened.getTime() - s1.getOpened().getTime()) > 60000 ||
       Math.abs(closed.getTime() - s1.getClosed().getTime()) > 60000    ) {
      sb.append("* Different epoch times Open: ").
              append(Util.toDOYString(opened)).append("-").append(Util.toDOYString(s1.getOpened())).append(" Closed:").
              append(Util.toDOYString(closed)).append("-").append(Util.toDOYString(s1.getClosed())).append("\n");
      error = true;  
    }
    // TODO: compare rest of the fields
    error |= UtilGUI.diffStrings(region, s1.getRegion(), strict, toString()+" Region:", sb);
    error |= UtilGUI.diffStrings(seedSiteName, s1.getSeedSiteName(), strict, toString()+" SeedSiteName:", sb);
    error |= UtilGUI.diffStrings(irname, s1.getIrname(), strict, toString()+" Irname:", sb);
    error |= UtilGUI.diffInts(typemask, s1.getTypemask(), 0, toString()+" Typemask:", sb);
    error |= UtilGUI.diffStrings(irowner, s1.getIrowner(), strict, toString()+" Irowner:", sb);    
    error |= UtilGUI.diffStrings(seedOwner, s1.getSeedOwner(), strict, toString()+" SeedOwner:", sb);
    error |= UtilGUI.diffInts(groupmask, s1.getGroupmask(), 0, toString()+" GroupMask:", sb);
    error |= UtilGUI.diffStrings(seedurl, s1.getSeedurl(), strict, toString()+" SeedUrl:", sb);
    error |= UtilGUI.diffStrings(xmlurl, s1.getXmlurl(), strict, toString()+" XmlUrl:", sb);
    error |= UtilGUI.diffDoubles(latitude, s1.getLatitude(), LATITUDE_TOLERANCE, toString()+" Latitude:", sb);
    error |= UtilGUI.diffDoubles(longitude, s1.getLongitude(), LONGITUDE_TOLERANCE, 
            toString() + " Longitude:", sb);
    error |= UtilGUI.diffDoubles(elevation, s1.getElevation(), ELEVATION_TOLERANCE,
            toString() + " Elevation:", sb);    
    error |= UtilGUI.diffDoubles(seedlatitude, s1.getSeedlatitude(),SEED_LATITUDE_TOLERANCE,
            toString()+" SeedLatitude:", sb);
    error |= UtilGUI.diffDoubles(seedlongitude, s1.getSeedlongitude(), SEED_LONGITUDE_TOLERANCE, 
            toString() + " SeedLongitude:", sb);
    error |= UtilGUI.diffDoubles(seedelevation, s1.getSeedelevation(), SEED_ELEVATION_TOLERANCE,
            toString() + " SeedElevation:", sb);
    error |= UtilGUI.diffStrings(otheralias, s1.getOtheralias(), strict, toString()+" OtherAlias:", sb);
    error |= UtilGUI.diffInts(status, s1.getStatus(), 0, toString()+" Status:", sb);
           
    return !error;
  }
  
  // This is needed if you need to implement Comparator
  /*  public class EquipCompare implements Comparator {
    public int compare(Object a, Object b) {
      return ((Station) a).getStation().compareTo(((Station) b).getStation());
    }
    public boolean equals(Object a, Object b) {
      if( ((Station)a).getStation().equals( ((Station) b).getStation())) return true;
      return false;
    }
//  }*/
  
}
