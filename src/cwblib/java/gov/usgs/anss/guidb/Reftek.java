/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package gov.usgs.anss.guidb;
import gov.usgs.anss.util.Util;
import java.sql.ResultSet;
import java.sql.SQLException;
/**
 * Reftek.java
 * If the name of the class does not match the name of the field in the class you may
 * need to modify the rs.getSTring in the constructor.
 *
 * This Reftek templace for creating a database database object.  It is not
 * really needed by the ReftekPanel which uses the DBObject system for
 * changing a record in the database file.  However, you have to have this
 * shell to create objects containing a database record for passing around
 * and manipulating.  There are two constructors normally given:
 *<br>
 * 1)  passed a result set positioned to the database record to decode
 * <br> 2) Passed all of the data arguments needed to build the same set
 *     of data.
 *<br>
 * The Reftek should be replaced with capitalized version (class name) for the
 * file.  reftek should be replaced with the lower case version (for variables).
 *
 * Both the constructors use the "makeReftek(list of data args) to set the
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
 *<br>     if(fieldEnum == null) fieldEnum = FUtil.getEnum(DBConnectionThread.getConnection("DATABASE"),"table","fieldName");
 *  <br>
 * <br>   // Get the int corresponding to an Enum String for storage in the data base and
 *  <br>  // the index into the JComboBox representing this Enum
 * <br>   localVariable = FUtil.enumStringToInt(fieldEnum, enumString);
  *<br>  Created on July 8, 2002, 1:23 PM
 *
 * @author  D.C. Ketchum
 */
public class Reftek     //implements Comparator
{   // static ArrayList enumName;   // this need to be populated in makeReftek()

  /** Creates a new instance of Reftek */
  private int ID;                   // This is the database ID (should alwas be named "ID"
  private String reftek;             // This is the main key which should have same name
                            // as the table which it is a key to.  That is
                            // Table "category" should have key "category"

  // USER: All fields of file go here
  //  double longitude
  private String network;
  private String serial;
  private String ipadr;
  private String netmask;
  private String gateway;
  private int stream1;
  private double rate1;
  private String chans1;
  private String comps1;
  private String location1;
  private String band1;
  private int stream2;
  private double rate2;
  private String chans2;
  private String comps2;
  private String location2;
  private String band2;
  private int stream3;
  private double rate3;
  private String chans3;
  private String comps3;
  private String location3;
  private String band3;
  private String comment;


  // Put in correct detail constructor here.  Use makeReftek() by filling in all
  // the rs.get???("fieldName") for all of the arguments
  /** Create a instance of this table row from a positioned result set
   *@param rs The ResultSet already SELECTED
   *@exception SQLException SQLException If the ResultSet is Improper, network is down, or database is down
   */
  public Reftek(ResultSet rs) throws SQLException {  //USER: add all data field here
    makeReftek(rs.getInt("ID"), rs.getString("station"), rs.getString("network"),
            rs.getString("serial"),rs.getString("ipadr"),rs.getString("netmask"), rs.getString("gateway"),
            rs.getInt("stream1"), rs.getDouble("rate1"), rs.getString("chans1"), rs.getString("components1"), rs.getString("location1"), rs.getString("band1"),
            rs.getInt("stream2"), rs.getDouble("rate2"), rs.getString("chans2"), rs.getString("components2"), rs.getString("location2"), rs.getString("band2"),
            rs.getInt("stream3"), rs.getDouble("rate3"), rs.getString("chans3"), rs.getString("components3"), rs.getString("location3"), rs.getString("band3"),
            rs.getString("comment")
                            // ,rs.getDouble(longitude)
    );
  }

  // Detail Constructor, match set up with makeReftek(), this argument list should be
  // the same as for the result set builder as both use makeReftek()
  /** create a row record from individual variables
   *@param inID The ID in the database, normally zero for this type of construction
   *@param loc The key value for the database (same a name of database table)
   */
  public Reftek(int inID, String loc   //USER: add fields, double lon
          ,String net, String sn, String ip, String netm, String gate,
          int st1, double rt1, String ch1, String cp1, String lc1, String bnd1,
          int st2, double rt2, String ch2, String cp2, String lc2, String bnd2,
          int st3, double rt3, String ch3, String cp3, String lc3, String bnd3, String comment    ) {
    makeReftek(inID, loc, net, sn, ip,netm, gate, st1, rt1, ch1, cp1, lc1,
            bnd1, st2, rt2, ch2, cp2, lc2, bnd2, st3, rt3, ch3, cp3, lc3, bnd3, comment       //, lon
    );
  }

  // This routine transfers to the local variables of this object all of the data variables
  // associated with this file.
  /** internally set all of the field in our data to the passed data
   *@param inID The row ID in the database
   *@param loc The key (same as table name)
   **/
  private void makeReftek(int inID, String loc,    //USER: add fields, double lon
          String net, String sn, String ip, String netm, String gate,
          int st1, double rt1, String ch1, String cp1, String lc1, String bnd1,
          int st2, double rt2, String ch2, String cp2, String lc2, String bnd2,
          int st3, double rt3, String ch3, String cp3, String lc3, String bnd3, String cmt
  ) {
    ID = inID;  reftek=loc;     // longitude = lon
    //USER:  Put asssignments to all fields from arguments here

    // ENUM example:
    //if(qtypeEnum == null) qtypeEnum=FUtil.getEnum(DBConnectionThread.getConnection("DATABASE"), "nsnstation","quanterraType");
    //qtype = FUtil.enumStringToInt(qtypeEnum, st);
    // Boolean example where ENUM "True", "False
    // cont40 = FUtil.isTrue(c40);          // c40 is a string with "true" or false
    network = net; serial = sn; ipadr = ip; netmask=netm; gateway=gate;
    stream1=st1; rate1 = rt1; chans1 = ch1; comps1 = cp1; location1 = lc1; band1 = bnd1;
    stream1=st2; rate2 = rt2; chans1 = ch2; comps1 = cp2; location1 = lc2; band1 = bnd2;
    stream1=st3; rate3 = rt3; chans1 = ch3; comps1 = cp3; location1 = lc3; band1 = bnd3;
    comment = cmt;

  }
  /** return the key name as the string
   *@return the key name */
  public String toString2() { return reftek;}
  /** return the key name as the string
   *@return the key name */
  @Override
  public String toString() { return reftek;}
  // getter

  // standard getters
  /** get the database ID for the row
   *@return The database ID for the row*/
  public int getID() {return ID;}
  /** get the key name for the row
   *@return The key name string for the row
   */
  public String getReftek() {return reftek;}

  public String getNetwork() {return network;}
  public String getSerial() {return serial;}
  public String getIP() {return ipadr;}
  public String getNetmask() {return netmask;}
  public String getGateway() {return gateway;}
  public int getStream() {return stream1;}
  public double getRate1() {return rate1;}
  public String getChans1() {return chans1;}
  public String getComps1() {return comps1;}
  public String getLocation1() {return location1;}
  public String getBand1() {return band1;}
  public int getStream2() {return stream2;}
  public double getRate2() {return rate2;}
  public String getChans2() {return chans2;}
  public String getComps2() {return comps2;}
  public String getLocation2() {return location2;}
  public String getBand2() {return band2;}
  public int getStream3() {return stream3;}
  public double getRate3() {return rate3;}
  public String getChans3() {return chans3;}
  public String getComps3() {return comps3;}
  public String getLocation3() {return location3;}
  public String getBand3() {return band3;}
  //USER:  All field getters here
  //  public double getLongitude() { return longitude;}


  // This is needed if you need to implement Comparator
  /*  public class EquipCompare implements Comparator {
    public int compare(Object a, Object b) {
      return ((Reftek) a).getReftek().compareTo(((Reftek) b).getReftek());
    }
    public boolean equals(Object a, Object b) {
      if( ((Reftek)a).getReftek().equals( ((Reftek) b).getReftek())) return true;
      return false;
    }
//  }*/
  public static short BCDtoInt(short i2) {
    return (short) (BCDtoInt((int) i2) & 0xffff);
  }
  public static int BCDtoInt(int i4) {
    int mult=1;
    int ans=0;
    for(int i=0; i<8; i++) {
      ans += (i4 & 0xF)*mult;
      mult *= 10;
      i4 = i4 >> 4;
    }
    return ans;
  }
  public static  String BCDtoString(short i2) {
    return BCDtoString((int) i2).substring(4);
  }
  public static String BCDtoString(int i4) {
    String s = "";
    for(int i=0; i<8; i++) {
      s = (char) ((i4 & 0xf) + 32)+s;
    }
    return s;
  }
  public static String timeToString(byte [] time) {
    String s = "";
    int t;
    for(int i=0; i<6; i++) {
      t = time[i];
      t &= 0xff;
      s +=(char) (((t >> 4) & 0xf) +'0');
      s += (char) ((t & 0xf) +'0');
    }
    if(s.indexOf(":") >=0)
      Util.prta("Got illegal character in time string "+s+" "+Util.toHex(time[0])+" "+
              Util.toHex(time[1])+" "+Util.toHex(time[2])+" "+Util.toHex(time[3])+" "+Util.toHex(time[4])+" "+Util.toHex(time[5]));

    return s;
  }

}
