/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */

package gov.usgs.anss.guidb;
/*
 * CommLink.java
 * If the name of the class does not match the name of the field in the class you may
 * need to modify the rs.getSTring in the constructor.
 *
 * Created on July 8, 2002, 1:23 PM
 */

/**
 *
 * @author  ketchum
 * This CommLink templace for creating a database database object.  It is not
 * really needed by the CommLinkPanel which uses the DBObject system for
 * changing a record in the database file.  However, you have to have this
 * shell to create objects containing a database record for passing around
 * and manipulating.  There are two constructors normally given:
 * 1)  passed a result set positioned to the database record to decode
 * 2) Passed all of the data arguments needed to build the same set
 *     of data.  
 *
 * The CommLink should be replaced with capitalized version (class name) for the 
 * file.  commlink should be replaced with the lower case version (for variables).
 *
 * Both the constructors use the "makeCommLink(list of data args) to set the
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
 *    if(fieldEnum == null) fieldEnum = FUtil.getEnum(DBConnectionThread.getConnection("anss"),"table","fieldName");
 *    
 *   // Get the int corresponding to an Enum String for storage in the data base and 
 *   // the index into the JComboBox representing this Enum 
 *   localVariable = FUtil.enumStringToInt(fieldEnum, enumString);
 
 */


//import java.util.Comparator;
import gov.usgs.anss.db.DBConnectionThread;
import gov.usgs.anss.util.Util;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
//import java.util.ArrayList;

public class CommLink     //implements Comparator
{   // static ArrayList enumName;   // this need to be populated in makeCommLink()
  public static ArrayList<CommLink> v;             // Vector containing objects of this CommLink Type

  /** Creates a new instance of CommLink */
  int ID;                   // This is the database ID (should alwas be named "ID"
  String commlink;             // This is the main key which should have same name
                            // as the table which it is a key to.  That is
                            // Table "category" should have key "category"
  
  // All fields of file go here
  //  double longitude
  boolean enabled;
  int backupID;
  String description;
  String gateway;

  
  // Put in correct detail constructor here.  Use makeCommLink() by filling in all
  // the rs.get???("fieldName") for all of the arguments
  public CommLink(ResultSet rs) throws SQLException {
    makeCommLink(rs.getInt("ID"), rs.getString("commlink"),
       rs.getString("description"), rs.getInt("enabled"),rs.getInt("backupID"), rs.getString("gateway")                     // ,rs.getDouble(longitude)
    );
  }
  
  // Detail Constructor, match set up with makeCommLink(), this argument list should be
  // the same as for the result set builder as both use makeCommLink()
  public CommLink(int inID, String loc,   //, double lon
      String desc, int enb, int bid, String gt) {
    makeCommLink(inID, loc , desc,     //, lon
      enb, bid, gt
    );
  }
  
  // This routine transfers to the local variables of this object all of the data variables
  // associated with this file.
  private void makeCommLink(int inID, String loc ,   //, double lon
    String desc, int enb, int bid, String gate
  ) {
    ID = inID;  commlink=loc;     // longitude = lon
    // Put asssignments to all fields from arguments here
    //if(qtypeEnum == null) qtypeEnum=FUtil.getEnum(DBConnectionThread.getConnection("anss"), "nsnstation","quanterraType");
    //qtype = FUtil.enumStringToInt(qtypeEnum, st); 
    // cont40 = FUtil.isTrue(c40);          // c40 is a string with "true" or false
    description = desc;  enabled=(enb == 0 ? false:true); backupID=bid; gateway=gate;

    
  }
  public String toString2() { return commlink;}
  @Override
  public String toString() {String s =(commlink+" - "+description); return s.substring(0,Math.min(40, s.length()));}
  // getter
  
  // standard getters
  public int getID() {return ID;}
  public String getCommLink() {return commlink;}
  public int getBackupID() {return backupID;}
  public String getDescription() {return description;}
  public String getGateway() {return gateway;}
  public boolean isEnabled() { return enabled;}
  
  
  // All field getters here
  //  public double getLongitude() { return longitude;}
  
  
  // This is needed if you need to implement Comparator
  /*  public class EquipCompare implements Comparator {
    public int compare(Object a, Object b) {
      return ((CommLink) a).getCommLink().compareTo(((CommLink) b).getCommLink());
    }
    public boolean equals(Object a, Object b) {
      if( ((CommLink)a).getCommLink().equals( ((CommLink) b).getCommLink())) return true;
      return false;
    }
//  }*/
  
  public static CommLink getItemWithID(int ID) {
    makeCommLinks();
    for(CommLink comm: v) {
      if(comm.getID() == ID) return comm;
    }
    return null;
  }
  // This routine should only need tweeking if key field is not same as table name
  public static void makeCommLinks() {
    if (v != null) return;
    v=new ArrayList<CommLink>(100);
    try {
      Statement stmt = DBConnectionThread.getConnection(DBConnectionThread.getDBSchema()).createStatement();   // used for query
      String s = "SELECT * FROM anss.commlink ORDER BY commlink";
      ResultSet rs = stmt.executeQuery(s);
      while (rs.next()) {
        CommLink loc = new CommLink(rs);
//        Util.prt("MakeCommLink() i="+v.size()+" is "+loc.getCommLink());
        v.add(loc);
      }
      rs.close();
      stmt.close();
    }
    catch (SQLException e) {
      Util.SQLErrorPrint(e,"makeCommLinks() on table SQL failed");
      
    }    
  }  
}
