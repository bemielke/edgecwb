/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */ 

/*
 * ChannelEpoch.java
 *
 * Created on October 11, 2007, 5:23 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package gov.usgs.anss.metadatagui;
//package gov.usgs.edge.template;
//import java.util.Comparator;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;

/**
 * ChannelEpoch.java
 * If the name of the class does not match the name of the field in the class you may
 * need to modify the rs.getSTring in the constructor.
 *
 * This ChannelEpoch templace for creating a MySQL database object.  It is not
 * really needed by the ChannelEpochPanel which uses the DBObject system for
 * changing a record in the database file.  However, you have to have this
 * shell to create objects containing a MySQL record for passing around
 * and manipulating.  There are two constructors normally given:
 *<br>
 * 1)  passed a result set positioned to the MySQL record to decode
 * <br> 2) Passed all of the data arguments needed to build the same set
 *     of data.
 *<br>
 * The ChannelEpoch should be replaced with capitalized version (class name) for the
 * file.  channelEpoch should be replaced with the lower case version (for variables).
 *
 * Both the constructors use the "makeChannelEpoch(list of data args) to set the
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
 *<br>     if(fieldEnum == null) fieldEnum = FUtil.getEnum(UC.getConnection(),"table","fieldName");
 *  <br>
 * <br>   // Get the int corresponding to an Enum String for storage in the data base and
 *  <br>  // the index into the JComboBox representing this Enum
 * <br>   localVariable = FUtil.enumStringToInt(fieldEnum, enumString);
  *<br>  Created on July 8, 2002, 1:23 PM
 *
 * @author  D.C. Ketchum
 */
public class ChannelEpoch     //implements Comparator
{   // static ArrayList enumName;   // this need to be populated in makeChannelEpoch()

  /** Creates a new instance of ChannelEpoch */
  int ID;                   // This is the MySQL ID (should alwas be named "ID"
  String channel;      // This is the main key which should have same name
                            // as the table which it is a key to.  That is
                            // Table "category" should have key "category"

  // USER: All fields of file go here
  //  double longitude
  String orientation;
  String comment;
  String cookedresponse;
  String coordinates;
  String seedcoordinates;

  long flags;
  double rate;
  Timestamp effective;
  Timestamp endingDate;
  // Added seed fields
  double dip;
  double azimuth;
  double depth;
  String seedflags;
  String instrumentType;
  double instrumentGain;
  String units;
  double a0, a0calc, a0freq,sensitivity,sensitivityCalc;
  double seedlatitude,seedlongitude, elevationCalc;

  // Put in correct detail constructor here.  Use makeChannelEpoch() by filling in all
  // the rs.get???("fieldName") for all of the arguments
  /** Create a instance of this table row from a positioned result set
   *@param rs The ResultSet alreay SELECTED
   *@exception SQLException SQLException If the ResultSet is Improper, network is down, or database is down
   */
  public ChannelEpoch(ResultSet rs) throws SQLException {  //USER: add all data field here
    makeChannelEpoch(rs.getInt("ID"), 
            rs.getString("channel"),
            rs.getString("orientation"), 
            rs.getString("comment"), 
            rs.getString("cookedresponse"),
            rs.getString("coordinates"), 
            rs.getString("seedcoordinates"),
            rs.getInt("flags"), 
            rs.getDouble("rate"), 
            rs.getTimestamp("effective"), 
            rs.getTimestamp("endingDate"),
            rs.getDouble("dip"),
            rs.getDouble("azimuth"),
            rs.getDouble("depth"),
            rs.getString("seedflags"),
            rs.getString("instrumenttype"),
            rs.getDouble("instrumentgain"),
            rs.getString("units"),
            rs.getDouble("a0"),
            rs.getDouble("a0calc"),
            rs.getDouble("a0freq"),
            rs.getDouble("sensitivity"),
            rs.getDouble("sensitivitycalc"),
            rs.getDouble("seedlatitude"),
            rs.getDouble("seedlongitude"), 
            rs.getDouble("elevationcalc")
                            // ,rs.getDouble(longitude)
    );
  }
  /** Create a instance of this table row from a positioned result set
   *@param rs The ResultSet alreay SELECTED
   *@exception SQLException SQLException If the ResultSet is Improper, network is down, or database is down
   */
  public void reload(ResultSet rs) throws SQLException {  //USER: add all data field here
    makeChannelEpoch(rs.getInt("ID"), 
            rs.getString("channel"),         
            rs.getString("orientation"), 
            rs.getString("comment"), 
            rs.getString("cookedresponse"),
            rs.getString("coordinates"), 
            rs.getString("seedcoordinates"),
            rs.getLong("flags"), 
            rs.getDouble("rate"), 
            rs.getTimestamp("effective"), 
            rs.getTimestamp("endingDate"),
            rs.getDouble("dip"),
            rs.getDouble("azimuth"),
            rs.getDouble("depth"),
            rs.getString("seedflags"),
            rs.getString("instrumenttype"),
            rs.getDouble("instrumentgain"),
            rs.getString("units"),
            rs.getDouble("a0"),rs.getDouble("a0calc"),
            rs.getDouble("a0freq"),
            rs.getDouble("sensitivity"),
            rs.getDouble("sensitivitycalc"),
            rs.getDouble("seedlatitude"),
            rs.getDouble("seedlongitude"), 
            rs.getDouble("elevationcalc")
                            // ,rs.getDouble(longitude)
    );
  }

  // Detail Constructor, match set up with makeChannelEpoch(), this argument list should be
  // the same as for the result set builder as both use makeChannelEpoch()
  /** create a row record from individual variables
   *@param inID The ID in the database, normally zero for this type of constructin
   *@param loc The key value for the database (same a name of database table)
   */
  public ChannelEpoch(int inID, String loc   //USER: add fields, double lon
      , String orient, String cmnt, String cooked, String coord, String scoord,
      long fl, double rt, Timestamp eff, Timestamp end,
      double dp, double az, double dpth, String sfl, String insttype, double instgain, String un,
      double a0in, double a0calcin, double a0fr, double sens, double senscalc,
      double seedlat, double seedlong, double elev

    ) {
    makeChannelEpoch(inID, loc       //, lon,
        ,orient, cmnt, cooked, coord, scoord,
        fl, rt, eff, end, dp, az, dpth, sfl,insttype,instgain,un,
        a0in, a0calcin, a0fr, sens,senscalc, seedlat,seedlong, elev
    );
  }

  // This routine transfers to the local variables of this object all of the data variables
  // associated with this file.
  /** internally set all of the field in our data to the passsed data
   *@param inID The row ID in the database
   *@param loc The key (same as table name)
   **/
  private void makeChannelEpoch(int inID, String loc    //USER: add fields, double lon
      , String orient, String cmnt, String cooked, String coord, String scoord,
      long fl, double rt, Timestamp eff, Timestamp end,
      double dp, double az, double dpth, String sfl, String insttype, double instgain, String un,
      double a0in, double a0calcin, double a0fr, double sens, double senscalc,
      double seedlat, double seedlong, double elev

  ) {
    ID = inID;  channel=loc;     // longitude = lon
    //USER:  Put asssignments to all fields from arguments here
    orientation=orient; comment=cmnt; cookedresponse=cooked; coordinates=coord; seedcoordinates=scoord;
    flags=fl; rate = rt; effective = eff; endingDate = end;
    dip=dp; azimuth=az; depth=dpth; seedflags=sfl; instrumentType=insttype; instrumentGain=instgain;
    units=un; a0=a0in; a0calc=a0calcin; sensitivity=sens; sensitivityCalc=senscalc;
    seedlatitude=seedlat; seedlongitude=seedlong; elevationCalc=elev;a0freq=a0fr;
    // ENUM example:
    //if(qtypeEnum == null) qtypeEnum=FUtil.getEnum(UC.getConnection(), "nsnstation","quanterraType");
    //qtype = FUtil.enumStringToInt(qtypeEnum, st);
    // Boolean example where ENUM "True", "False
    // cont40 = FUtil.isTrue(c40);          // c40 is a string with "true" or false


  }
  /** return the key name as the string
   *@return the key name */
  public String toString2() { return channel;}
  /** return the key name as the string
   *@return the key name */
  @Override
  public String toString() { return channel+" "+effective+"-"+endingDate;}
  // getter

  // standard getters
  /** get the database ID for the row
   *@return The database ID for the row*/
  public int getID() {return ID;}
  /** get the key name for the row
   *@return The key name string for the row
   */
  public String getChannel() {return channel;}
  public String getChannelEpoch() {return channel;}


  //USER:  All field getters here
  //  public double getLongitude() { return longitude;}
  public String getOrientation() {return orientation;}
  public String getCoordinates() {return coordinates;}
  public String getSeedCoordinates() {return seedcoordinates;}
  public String getComment() {return comment;}
  public String getCookedResponse() {return cookedresponse;}
  public double getRate() {return rate;}
  public long getFlags() {return flags;}
  public Timestamp getEffective() {return effective;}
  public Timestamp getEndingDate() {return endingDate;}

  // Get the raw stuff
  public double getDip() {return dip;}
  public double getAzimuth() {return azimuth;}
  public double getDepth() {return depth;}
  public double getSeedLatitude() {return seedlatitude;}
  public double getSeedLongitude() {return seedlongitude;}
  public double getElevationCalc() {return elevationCalc;}
  public double getA0() {return a0;}
  public double getA0calc() {return a0calc;}
  public double getA0freq() {return a0freq;}
  public double getSensitivity() {return sensitivity;}
  public double getSensitivityCalc() {return sensitivityCalc;}
  public double getInstrumentGain() {return instrumentGain;}
  public String getUnits() {return units;}
  public String getSeedFlags() {return seedflags;}
  public String getInstrumentType() {return instrumentType;}

  // This is needed if you need to implement Comparator
  /*  public class EquipCompare implements Comparator {
    public int compare(Object a, Object b) {
      return ((ChannelEpoch) a).getChannelEpoch().compareTo(((ChannelEpoch) b).getChannelEpoch());
    }
    public boolean equals(Object a, Object b) {
      if( ((ChannelEpoch)a).getChannelEpoch().equals( ((ChannelEpoch) b).getChannelEpoch())) return true;
      return false;
    }
//  }*/

}

