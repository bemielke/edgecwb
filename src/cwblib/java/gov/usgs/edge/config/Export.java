/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.edge.config;

import gov.usgs.anss.db.DBConnectionThread;
import gov.usgs.anss.util.FUtil;
import gov.usgs.anss.util.Util;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;

/**
 * Export.java If the name of the class does not match the name of the field in the class you may
 * need to modify the rs.getSTring in the constructor.
 *
 * This Export templace for creating a database database object. It is not really needed by the
 * ExportPanel which uses the DBObject system for changing a record in the database file. However,
 * you have to have this shell to create objects containing a database record for passing around and
 * manipulating. There are two constructors normally given:
 * <br>
 * 1) passed a result set positioned to the database record to decode
 * <br> 2) Passed all of the data arguments needed to build the same set of data.
 * <br>
 * The Export should be replaced with capitalized version (class name) for the file. export should
 * be replaced with the lower case version (for variables).
 *
 * Both the constructors use the "makeExport(list of data args) to set the local variables to the
 * value. The result set just uses the rs.get???("fieldname") to get the data from the database
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
 * <br> if(fieldEnum == null) fieldEnum =
 * FUtil.getEnum(JDBConnection.getConnection("DATABASE"),"table","fieldName");
 * <br>
 * <br> // Get the int corresponding to an Enum String for storage in the data base and
 * <br> // the index into the JComboBox representing this Enum
 * <br> localVariable = FUtil.enumStringToInt(fieldEnum, enumString);
 * <br> Created on July 8, 2002, 1:23 PM
 *
 * @author D.C. Ketchum
 */
public final class Export {     //implements Comparator
  // static ArrayList enumName;   // this need to be populated in makeExport()

  public static ArrayList<String> typeEnum;
  public static final int MASK_ALLOWRESTRICTED = 1;
  public static final int MASK_SNIFFLOG = 2;
  // NOTE : here define all variables general.  "Vector v" is used for main Comboboz
  static ArrayList<Export> v;             // Vector containing objects of this Export Type

  /**
   * Creates a new instance of Export
   */
  private int ID;                   // This is the database ID (should alwas be named "ID"
  private String export;             // This is the main key which should have same name
  // as the table which it is a key to.  That is
  // Table "category" should have key "category"

  // USER: All fields of file go here
  //  double longitude
  private String password;
  private String exportIP;
  private int exportPort;
  private String chansText;
  private int type;
  private boolean scn;
  private int maxLatency;
  private int roleID;

  // Put in correct detail constructor here.  Use makeExport() by filling in all
  // the rs.get???("fieldName") for all of the arguments
  /**
   * Create a instance of this table row from a positioned result set
   *
   * @param rs The ResultSet already SELECTED
   * @exception SQLException SQLException If the ResultSet is Improper, network is down, or database
   * is down
   */
  public Export(ResultSet rs) throws SQLException {  //USER: add all data field here
    makeExport(rs.getInt("ID"), rs.getString("export"),
            rs.getString("password"), rs.getString("exportipadr"), rs.getInt("exportport"), rs.getString("type"),
            rs.getInt("scn"), rs.getString("chanlist"), rs.getInt("roleid"), rs.getInt("maxlatency")
    // ,rs.getDouble(longitude)
    );
  }

  // Detail Constructor, match set up with makeExport(), this argument list should be
  // the same as for the result set builder as both use makeExport()
  /**
   * create a row record from individual variables
   *
   * @param inID The ID in the database, normally zero for this type of construction
   * @param loc The key value for the database (same a name of database table)
   * @param pw The password for this account, encoded per database
   * @param ip The ip address of the IMPORT
   * @param port If this is a ACTV, the port of the passive IMPORT
   * @param typ The type from the enum of TYPE
   * @param scnint The integer value, if non-zero, then convert data to SCN before shipping
   * @param clist Channel list
   * @param role Role name
   * @param maxLat Maximum latency
   *
   */
  public Export(int inID, String loc //USER: add fields, double lon
          ,
           String pw, String ip, int port, String typ, int scnint, String clist, int role, int maxLat
  ) {
    makeExport(inID, loc, pw, ip, port, typ, scnint, clist, role, maxLat //, lon
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
  private void makeExport(int inID, String loc //USER: add fields, double lon
          ,
           String pw, String ip, int port, String typ, int scnint, String clist, int role, int maxLat
  ) {
    ID = inID;
    export = loc;     // longitude = lon
    //USER:  Put asssignments to all fields from arguments here

    // ENUM example:
    //if(qtypeEnum == null) qtypeEnum=FUtil.getEnum(JDBConnection.getConnection("DATABASE"), "nsnstation","quanterraType");
    //qtype = FUtil.enumStringToInt(qtypeEnum, st); 
    // Boolean example where ENUM "True", "False
    // cont40 = FUtil.isTrue(c40);          // c40 is a string with "true" or false
    exportIP = ip;
    password = pw;
    exportPort = port;
    type = FUtil.enumStringToInt(Export.typeEnum, typ);
    roleID = role;
    scn = (scnint != 0);
    chansText = clist;
    maxLatency = maxLat;
  }

  /**
   * return the key name as the string
   *
   * @return the key name
   */
  public String toString2() {
    return export;
  }

  /**
   * return the key name as the string
   *
   * @return the key name
   */
  @Override
  public String toString() {
    return export;
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
  public String getExport() {
    return export;
  }

  //USER:  All field getters here
  //  public double getLongitude() { return longitude;}
  public String getChannelText() {
    return chansText;
  }

  public String getIP() {
    return exportIP;
  }

  public int getPort() {
    return exportPort;
  }

  public String getPassword() {
    return password;
  }

  public int getTypeInt() {
    return type;
  }

  public int getRoleID() {
    return roleID;
  }

  public int getMaxLatency() {
    return maxLatency;
  }

  public String getType() {
    return Export.typeEnum.get(type);
  }

  // This is needed if you need to implement Comparator
  /*  public class EquipCompare implements Comparator {
    public int compare(Object a, Object b) {
      return ((Export) a).getExport().compareTo(((Export) b).getExport());
    }
    public boolean equals(Object a, Object b) {
      if( ((Export)a).getExport().equals( ((Export) b).getExport())) return true;
      return false;
    }
//  }*/
  /**
   * Get the item corresponding to database ID
   *
   * @param ID the database Row ID
   * @return The Export row with this ID
   */
  public static Export getExportWithID(int ID) {
    if (v == null) {
      makeExports();
    }
    for (Export export : v) {
      if (export.getID() == ID) {
        return export;
      }
    }
    return null;
  }

  // This routine should only need tweeking if key field is not same as table name
  public static void makeExports() {
    if (v != null) {
      return;
    }
    v = new ArrayList<Export>(100);
    try {
      try (Statement stmt = DBConnectionThread.getConnection(DBConnectionThread.getDBSchema()).createStatement() // used for query
              ) {
        String s = "SELECT * FROM edge.export ORDER BY export;";
        try (ResultSet rs = stmt.executeQuery(s)) {
          while (rs.next()) {
            Export loc = new Export(rs);
            //Util.prt("MakeExport() i="+v.size()+" is "+loc.getExport());
            v.add(loc);
          }
        }
      }
    } catch (SQLException e) {
      Util.prt("e=" + e);
      e.printStackTrace();
      Util.SQLErrorPrint(e, "makeExports() on table SQL failed");
    }
  }
}
