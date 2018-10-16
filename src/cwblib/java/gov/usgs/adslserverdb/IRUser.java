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
 * IRUser.java
 * If the name of the class does not match the name of the field in the class you may
 * need to modify the rs.getSTring in the constructor.
 *
 * This IRUser templace for creating a MySQL irserver object.  It is not
 * really needed by the UserPanel which uses the DBObject system for
 * changing a record in the irserver file.  However, you have to have this
 * shell to create objects containing a MySQL record for passing around
 * and manipulating.  There are two constructors normally given:
 *<br>
 * 1)  passed a result set positioned to the MySQL record to decode
 * <br> 2) Passed all of the data arguments needed to build the same set
 *     of data.
 *<br>
 * The IRUser should be replaced with capitalized version (class name) for the
 * file.  user should be replaced with the lower case version (for variables).
 *
 * Both the constructors use the "makeUser(list of data args) to set the
 * local variables to the value.  The result set just uses the
 * rs.get???("fieldname") to get the data from the irserver where the other
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
 *<br>     if(fieldEnum == null) fieldEnum = FUtil.getEnum(JDBConnection.getConnection("irserver"),"table","fieldName");
 *  <br>
 * <br>   // Get the int corresponding to an Enum String for storage in the data base and
 *  <br>  // the index into the JComboBox representing this Enum
 * <br>   localVariable = FUtil.enumStringToInt(fieldEnum, enumString);
  *<br>  Created on July 8, 2002, 1:23 PM
 *
 * @author  D.C. Ketchum
 */
public class IRUser     //implements Comparator
{   // static ArrayList enumName;   // this need to be populated in makeUser()

  /** Creates a new instance of IRUser */
  private int ID;                   // This is the MySQL ID (should alwas be named "ID"
  private String user;             // This is the main key which should have same name
                            // as the table which it is a key to.  That is
                            // Table "category" should have key "category"

  // USER: All fields of file go here
  //  double longitude
  private int pocID;
  private String password;
  private int superuser;



  // Put in correct detail constructor here.  Use makeUser() by filling in all
  // the rs.get???("fieldName") for all of the arguments
  /** Create a instance of this table row from a positioned result set.
   *@param rs The ResultSet already SELECTED
   *@exception SQLException SQLException If the ResultSet is Improper, network is down, or irserver is down
   */
  public IRUser(ResultSet rs) throws SQLException {  //USER: add all data field here
    makeUser(rs.getInt("ID"), rs.getString("user"), rs.getInt("pocid"), rs.getString("password"), rs.getInt("superuser")
                            // ,rs.getDouble(longitude)
    );
  }

  // Detail Constructor, match set up with makeUser(), this argument list should be
  // the same as for the result set builder as both use makeUser()
  /** create a row record from individual variables.
   *@param inID The ID in the irserver, normally zero for this type of construction
   *@param loc The key value for the irserver (same a name of irserver table)
   * @param poc THe ID in the POC file for this user
   * @param pw The users password
   * @param su The superuser field with extra privileges
   */
  public IRUser(int inID, String loc   //USER: add fields, double lon
          ,int poc, String pw, int su
    ) {
    makeUser(inID, loc, poc, pw,su      //, lon
    );
  }

  // This routine transfers to the local variables of this object all of the data variables
  // associated with this file.
  /** internally set all of the field in our data to the passed data
   *@param inID The row ID in the irserver
   *@param loc The key (same as table name)
   **/
  private void makeUser(int inID, String loc    //USER: add fields, double lon
          , int poc, String pw, int su
  ) {
    ID = inID;  user=loc;     // longitude = lon
    //USER:  Put asssignments to all fields from arguments here

    // ENUM example:
    //if(qtypeEnum == null) qtypeEnum=FUtil.getEnum(JDBConnection.getConnection("irserver"), "nsnstation","quanterraType");
    //qtype = FUtil.enumStringToInt(qtypeEnum, st);
    // Boolean example where ENUM "True", "False
    // cont40 = FUtil.isTrue(c40);          // c40 is a string with "true" or false
    pocID = poc; password=pw; superuser=su;

  }
  /** Return the key name as the string.
   *@return the key name */
  public String toString2() { return user;}
  /** Return the key name as the string.
   *@return the key name */
  @Override
  public String toString() { return user;}
  // getter

  // standard getters
  /** Get the irserver ID for the row.
   *@return The irserver ID for the row*/
  public int getID() {return ID;}
  /** Get the key name for the row.
   *@return The key name string for the row
   */
  public String getUser() {return user;}


  //USER:  All field getters here
  //  public double getLongitude() { return longitude;}
  public int getPOCID() {return pocID;}
  public String getPassword(){ return password;}
  public int getSuperuser() {return superuser;}

  // This is needed if you need to implement Comparator
  /*  public class EquipCompare implements Comparator {
    public int compare(Object a, Object b) {
      return ((IRUser) a).getUser().compareTo(((IRUser) b).getUser());
    }
    public boolean equals(Object a, Object b) {
      if( ((IRUser)a).getUser().equals( ((IRUser) b).getUser())) return true;
      return false;
    }
//  }*/

}

