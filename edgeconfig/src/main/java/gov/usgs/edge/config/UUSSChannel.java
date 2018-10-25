package gov.usgs.edge.config;

/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */

//package gov.usgs.edge.template;
//import java.util.Comparator;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * UUSSChannel.java
 * If the name of the class does not match the name of the field in the class you may
 * need to modify the rs.getSTring in the constructor.
 *
 * This UUSSChannel template for creating a database database object.  It is not
 * really needed by the UUSSChannelPanel which uses the DBObject system for
 * changing a record in the database file.  However, you have to have this
 * shell to create objects containing a database record for passing around
 * and manipulating.  There are two constructors normally given:
 *<br>
 * 1)  passed a result set positioned to the database record to decode
 * <br> 2) Passed all of the data arguments needed to build the same set
 *     of data.  
 *<br>
 * The UUSSChannel should be replaced with capitalized version (class name) for the
 * file.  uusschannel should be replaced with the lower case version (for variables).
 *
 * Both the constructors use the "makeUUSSChannel(list of data args) to set the
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
public class UUSSChannel     //implements Comparator
{   // static ArrayList enumName;   // this need to be populated in makeUUSSChannel()
  
  /** Creates a new instance of UUSSChannel */
  int ID;                   // This is the database ID (should alwas be named "ID"
  String uusschannel;             // This is the main key which should have same name
                            // as the table which it is a key to.  That is
                            // Table "category" should have key "category"
  String channel;           // Seed channel
  
  // USER: All fields of file go here
  //  double longitude

  
  // Put in correct detail constructor here.  Use makeUUSSChannel() by filling in all
  // the rs.get???("fieldName") for all of the arguments
  /** Create a instance of this table row from a positioned result set
   *@param rs The ResultSet already SELECTED
   *@exception SQLException SQLException If the ResultSet is Improper, network is down, or database is down
   */
  public UUSSChannel(ResultSet rs) throws SQLException {  //USER: add all data field here
    makeUUSSChannel(rs.getInt("ID"), rs.getString("uusschannel"), rs.getString("channel")
                            // ,rs.getDouble(longitude)
    );
  }
  
  // Detail Constructor, match set up with makeUUSSChannel(), this argument list should be
  // the same as for the result set builder as both use makeUUSSChannel()
  /** create a row record from individual variables
   *@param inID The ID in the database, normally zero for this type of construction
   *@param loc The key value for the database (same a name of database table)
   */
  public UUSSChannel(int inID, String loc, String chan   //USER: add fields, double lon
    ) {
    makeUUSSChannel(inID, loc , chan      //, lon
    );
  }
  
  // This routine transfers to the local variables of this object all of the data variables
  // associated with this file.
  /** internally set all of the field in our data to the passed data 
   *@param inID The row ID in the database
   *@param loc The key (same as table name)
   **/
  private void makeUUSSChannel(int inID, String loc ,String chan   //USER: add fields, double lon
  ) {
    ID = inID;  uusschannel=loc;     // longitude = lon
    channel=chan;
    //USER:  Put asssignments to all fields from arguments here
    
    // ENUM example:
    //if(qtypeEnum == null) qtypeEnum=FUtil.getEnum(JDBConnection.getConnection("DATABASE"), "nsnstation","quanterraType");
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
  public String toString() { return channel+"-"+uusschannel;}
  // getter
  
  // standard getters
  /** get the database ID for the row
   *@return The database ID for the row*/
  public int getID() {return ID;}
  /** get the key name for the row
   *@return The key name string for the row
   */
  public String getUUSSChannel() {return uusschannel;}
  public String getSeedChannel() {return channel;}
  
  
  //USER:  All field getters here
  //  public double getLongitude() { return longitude;}
  
  
  // This is needed if you need to implement Comparator
  /*  public class EquipCompare implements Comparator {
    public int compare(Object a, Object b) {
      return ((UUSSChannel) a).getUUSSChannel().compareTo(((UUSSChannel) b).getUUSSChannel());
    }
    public boolean equals(Object a, Object b) {
      if( ((UUSSChannel)a).getUUSSChannel().equals( ((UUSSChannel) b).getUUSSChannel())) return true;
      return false;
    }
//  }*/
  
}
