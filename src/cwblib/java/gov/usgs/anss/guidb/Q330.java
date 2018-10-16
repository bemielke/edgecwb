/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */

/*
 /* Q330.java
 * If the name of the class does not match the name of the field in the class you may
 * need to modify the rs.getSTring in the constructor.
 *
 * Created on July 8, 2002, 1:23 PM
 */

/**
 *
 * @author  ketchum
 * This Q330 templace for creating a database database object.  It is not
 * really needed by the Q330Panel which uses the DBObject system for
 * changing a record in the database file.  However, you have to have this
 * shell to create objects containing a database record for passing around
 * and manipulating.  There are two constructors normally given:
 * 1)  passed a result set positioned to the database record to decode
 * 2) Passed all of the data arguments needed to build the same set
 *     of data.  
 *
 * The Q330 should be replaced with capitalized version (class name) for the 
 * file.  Q330 should be replaced with the lower case version (for variables).
 *
 * Both the constructors use the "makeQ330(list of data args) to set the
 * local variables to the value.  The result set just uses the 
 * rs.get???("fieldname") to get the data from the database where the other
 * passes the arguments from the caller.
 * 
 * Notes on Enums :
 ** data class should have code like :
 * import java.util.ArrayList;          /// Import for Enum manipulation
 *   .
 *  static ArrayList fieldEnum;         // This list will have Strings corresponding to enum
 *       .
 *       .
 *    // To convert an enum we need to create the fieldEnum ArrayList.  Do only once(static)
 *    if(fieldEnum == null) fieldEnum = FUtil.getEnum(UC.getConnection(),"table","fieldName");
 *    
 *   // Get the int corresponding to an Enum String for storage in the data base and 
 *   // the index into the JComboBox representing this Enum 
 *   localVariable = FUtil.enumStringToInt(fieldEnum, enumString);
 
 */

package gov.usgs.anss.guidb;
//import java.util.Comparator;
import java.sql.ResultSet;
import java.sql.SQLException;

public class Q330     //implements Comparator
{   // static ArrayList enumName;   // this need to be populated in makeQ330()
  
  /** Creates a new instance of Q330 */
  int ID;                   // This is the database ID (should alwas be named "ID"
  String q330;             // This is the main key which should have same name
                            // as the table which it is a key to.  That is
                            // Table "category" should have key "category"
  
  // All fields of file go here
  //  double longitude
  String hexserial;
  long authcode;

  
  // Put in correct detail constructor here.  Use makeQ330() by filling in all
  // the rs.get???("fieldName") for all of the arguments
  public Q330(ResultSet rs) throws SQLException {
    makeQ330(rs.getInt("ID"), rs.getString("q330"),
       rs.getString("hexserial"), rs.getLong("authcode")// ,rs.getDouble(longitude)
    );
  }
  
  // Detail Constructor, match set up with makeQ330(), this argument list should be
  // the same as for the result set builder as both use makeQ330()
  public Q330(int inID, String loc,   //, double lon
      String sn, int auth) {
    makeQ330(inID, loc , sn, auth
    );
  }
  
  // This routine transfers to the local variables of this object all of the data variables
  // associated with this file.
 private void makeQ330(int inID, String loc ,   //, double lon
    String sn, long auth
  ) {
    ID = inID;  q330=loc;     // longitude = lon
    // Put asssignments to all fields from arguments here
    //if(qtypeEnum == null) qtypeEnum=FUtil.getEnum(UC.getConnection(), "nsnq330","quanterraType");
    //qtype = FUtil.enumStringToInt(qtypeEnum, st); 
    // cont40 = FUtil.isTrue(c40);          // c40 is a string with "true" or false
    hexserial=sn; 
    authcode=auth;
  }
  public String toString2() { return q330+" - "+hexserial;}
  @Override
  public String toString() { return q330;}
  // getter
  
  // standard getters
  public int getID() {return ID;}
  public String getq330() {return q330;}
  public String gethexserial() {return hexserial;}
  public long getAuthcode() {return authcode;}
  public long getCalcAuthcode() {return calcAuthcode(hexserial);}
  static public long calcAuthcode(String hex) {
    if(hex.length() < 7) return 0;
    int base = Integer.parseInt(hex.substring(hex.length()-6), 16);
    base = (~base & 0xffffff) ^ 0xdabcde;
    //Util.prt("hex="+hex+" becomes "+Util.toHex(base));
    return ((long) base) &0xffffffff;
  }
  static public long calcAuthcodeDataPort(String hex, int port) {
    if(port == 0) return calcAuthcode(hex);
    long [] ports = {0xaaaaaa,0xbbbbbb,0xcccccc, 0xdddddd};
    long base = calcAuthcode(hex);

    //Util.prt("hex="+hex+" becomes "+Util.toHex(base));
    return base ^ ports[port-1];
  }
//  }*/
  
}
