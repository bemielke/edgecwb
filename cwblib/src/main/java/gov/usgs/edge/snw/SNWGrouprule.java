/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
/**
 * SNWGrouprule.java
 * If the name of the class does not match the name of the field in the class you may
 * need to modify the rs.getSTring in the constructor.
 *
 * This SNWGrouprule templace for creating a MySQL database object.  It is not
 * really needed by the SNWGrouprulePanel which uses the DBObject system for
 * changing a record in the database file.  However, you have to have this
 * shell to create objects containing a MySQL record for passing around
 * and manipulating.  There are two constructors normally given:
 * <br>
 * 1) passed a result set positioned to the MySQL record to decode
 * <br> 2) Passed all of the data arguments needed to build the same set of data.
 * <br>
 * The SNWGrouprule should be replaced with capitalized version (class name) for the file.
 * snwgrouprule should be replaced with the lower case version (for variables).
 *
 * Both the constructors use the "makeSNWGrouprule(list of data args) to set the local variables to
 * the value. The result set just uses the rs.get???("fieldname") to get the data from the database
 * where the other passes the arguments from the caller.
 *
 * <br> Notes on Enums :
 * <br> * data class should have code like :
 * <br> import java.util.ArrayList; /// Import for Enum manipulation
 * <br> .
 * <br> static ArrayList fieldEnum; // This list will have Strings corresponding to enum
 * <br> .
 * <br> .
 * <br> // To convert an enum we need to create the fieldEnum ArrayList. Do only once(static)
 * <br> if(fieldEnum == null) fieldEnum = FUtil.getEnum(UC.getConnection(),"table","fieldName");
 * <br>
 * <br> // Get the int corresponding to an Enum String for storage in the data base and
 * <br> // the index into the JComboBox representing this Enum
 * <br> localVariable = FUtil.enumStringToInt(fieldEnum, enumString);
 * <br> Created on July 8, 2002, 1:23 PM
 *
 * @author D.C. Ketchum
 */
package gov.usgs.edge.snw;
//package gov.usgs.edge.template;
//import java.util.Comparator;

import java.sql.ResultSet;
import java.sql.SQLException;
//import java.util.Iterator;

public final class SNWGrouprule {    //implements Comparator
  // static ArrayList enumName;   // this need to be populated in makeSNWGrouprule()

  /**
   * Creates a new instance of SNWGrouprule
   */
  private int ID;                   // This is the MySQL ID (should alwas be named "ID"
  private String snwgrouprule;             // This is the main key which should have same name

  // as the table which it is a key to.  That is
  // Table "category" should have key "category"
  // USER: All fields of file go here
  //  double longitude
  private String format;
  private long typid;
  private double min;
  private double max;
  private String keyname;
  private int snwgroupid;
  private String bitmask;
  private double damping1;
  private double damping2;
  private long eventid;

  // Put in correct detail constructor here.  Use makeSNWGrouprule() by filling in all
  // the rs.get???("fieldName") for all of the arguments
  /**
   * Create a instance of this table row from a positioned result set
   *
   * @param rs The ResultSet already SELECTED
   * @exception SQLException SQLException If the ResultSet is Improper, network is down, or database
   * is down
   */
  public SNWGrouprule(ResultSet rs) throws SQLException {  //USER: add all data field here
    makeSNWGrouprule(rs.getInt("ID"),
            rs.getString("snwgrouprule"),
            rs.getString("format"),
            rs.getLong("typeid"),
            rs.getDouble("min"),
            rs.getDouble("max"),
            rs.getString("keyname"),
            rs.getInt("snwgroupid"),
            rs.getString("bitmask"),
            rs.getDouble("damping1"),
            rs.getDouble("damping2"),
            rs.getLong("eventID")
    // ,rs.getDouble(longitude)
    );
  }

  // Detail Constructor, match set up with makeSNWGrouprule(), this argument list should be
  // the same as for the result set builder as both use makeSNWGrouprule()
  /**
   * create a row record from individual variables
   *
   * @param inID The ID in the database, normally zero for this type of construction
   * @param loc The key value for the database (same a name of database table)
   * @param format Documentation
   * @param typid
   * @param min Percent bad pct
   * @param max Percent warn max
   * @param keyname
   * @param snwgroupid
   * @param bitmask
   * @param damping1
   * @param damping2
   * @param eventid
   */
  public SNWGrouprule(int inID,
          String loc,
          String format,
          long typid,
          double min,
          double max,
          String keyname,
          int snwgroupid,
          String bitmask,
          double damping1,
          double damping2,
          long eventid
  ) {
    makeSNWGrouprule(inID,
            loc,
            format,
            typid,
            min,
            max,
            keyname,
            snwgroupid,
            bitmask,
            damping1,
            damping2,
            eventid
    );
  }

  // This routine transfers to the local variables of this object all of the data variables
  // associated with this file.
  /**
   * internally set all of the field in our data to the passed data
   *
   * @param inID The row ID in the database
   * @param loc The key (same as table name)
   *
   */
  private void makeSNWGrouprule(
          int inID,
          String inloc,
          String informat,
          long intypid,
          double inmin,
          double inmax,
          String inkeyname,
          int insnwgroupid,
          String inbitmask,
          double indamping1,
          double indamping2,
          long ineventid
  ) {
    ID = inID;
    snwgrouprule = inloc;     // longitude = lon
    format = informat;
    typid = intypid;
    min = inmin;
    max = inmax;
    keyname = inkeyname;
    snwgroupid = insnwgroupid;
    bitmask = inbitmask;
    damping1 = indamping1;
    damping2 = indamping2;
    eventid = ineventid;
    //USER:  Put asssignments to all fields from arguments here

    // ENUM example:
    //if(qtypeEnum == null) qtypeEnum=FUtil.getEnum(UC.getConnection(), "nsnstation","quanterraType");
    //qtype = FUtil.enumStringToInt(qtypeEnum, st); 
    // Boolean example where ENUM "True", "False
    // cont40 = FUtil.isTrue(c40);          // c40 is a string with "true" or false
  }

  /**
   * return the key name as the string
   *
   * @return the key name
   */
  public String toString2() {
    return snwgrouprule;
  }

  /**
   * return the key name as the string
   *
   * @return the key name
   */
  @Override
  public String toString() {
    return snwgrouprule;
  }
  // getter

  // standard getters
  /**
   * get the database ID for the row
   *
   * @return The database ID for the row
   */
  public int getID() {
    return ID;
  }

  /**
   * get the key name for the row
   *
   * @return The key name string for the row
   */

  public SNWGrouprule getSNWGrouprule() {
    return this;
  }

  public String getName() {
    return snwgrouprule;
  }

  public String getFormat() {
    return format;
  }

  public long getTypid() {
    return typid;
  }

  public double getMin() {
    return min;
  }

  public double getMax() {
    return max;
  }

  public String getKeyname() {
    return keyname;
  }

  public int getSNWGroupid() {
    return snwgroupid;
  }
//  public String getGroupname() {return SNWGroup. getGroupname(snwgroupid);}

  public String getBitmask() {
    return bitmask;
  }

  public double getDamping1() {
    return damping1;
  }

  public double getDamping2() {
    return damping2;
  }

  public long getEventid() {
    return eventid;
  }

  //USER:  All field getters here
  //  public double getLongitude() { return longitude;}
  // This is needed if you need to implement Comparator
  /*  public class EquipCompare implements Comparator {
    public int compare(Object a, Object b) {
      return ((SNWGrouprule) a).getSNWGrouprule().compareTo(((SNWGrouprule) b).getSNWGrouprule());
    }
    public boolean equals(Object a, Object b) {
      if( ((SNWGrouprule)a).getSNWGrouprule().equals( ((SNWGrouprule) b).getSNWGrouprule())) return true;
      return false;
    }
//  }*/
}
