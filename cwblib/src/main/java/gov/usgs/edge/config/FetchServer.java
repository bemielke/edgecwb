/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.edge.config;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;

/**
 * FetchServer.java If the name of the class does not match the name of the field in the class you
 * may need to modify the rs.getSTring in the constructor.
 *
 * This FetchServer template for creating a database database object. It is not really needed by the
 * SeedlinkServerPanel which uses the DBObject system for changing a record in the database file.
 * However, you have to have this shell to create objects containing a database record for passing
 * around and manipulating. There are two constructors normally given:
 * <br>
 * 1) passed a result set positioned to the database record to decode
 * <br> 2) Passed all of the data arguments needed to build the same set of data.
 * <br>
 * The FetchServer should be replaced with capitalized version (class name) for the file.
 * fetchServer should be replaced with the lower case version (for variables).
 *
 * Both the constructors use the "makeSeedlinkServer(list of data args) to set the local variables
 * to the value. The result set just uses the rs.get???("fieldname") to get the data from the
 * database where the other passes the arguments from the caller.
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
public final class FetchServer {   //implements Comparator
  // static ArrayList enumName;   // this need to be populated in makeSeedlinkServer()

  /**
   * Creates a new instance of FetchServer
   */
  private int ID;                   // This is the database ID (should alwas be named "ID"
  private String fetchServer;             // This is the main key which should have same name
  // as the table which it is a key to.  That is
  // Table "category" should have key "category"

  // USER: All fields of file go here
  //  double longitude
  private String ipadr, gaptype, chanre;//,cwbip,edgeip,fetchtable;
  private int port, fetchRequestTypeID; //,edgeport,cwbport;
  private String discoveryMethod;
  private String fetchIpadr;         // The IP of the the remote data center
  private int fetchPort;           // The port to use for what ever method is selected
  private int throttle;            // Max BPS on fetches from this source
  private final Timestamp disableRequestUntil = new Timestamp(86400000L);

  // Put in correct detail constructor here.  Use makeSeedlinkServer() by filling in all
  // the rs.get???("fieldName") for all of the arguments
  /**
   * Create a instance of this table row from a positioned result set
   *
   * @param rs The ResultSet already SELECTED
   * @exception SQLException SQLException If the ResultSet is Improper, network is down, or database
   * is down
   */
  public FetchServer(ResultSet rs) throws SQLException {  //USER: add all data field here
    reload(rs);
  }

  /**
   * reload the object data from a given result set
   *
   * @param rs The ResultSet to load the object from
   * @throws SQLException If one is thrown
   */
  public final void reload(ResultSet rs) throws SQLException {
    makeSeedlinkServer(rs.getInt("ID"), rs.getString("fetchServer"),
            rs.getString("ipadr"), rs.getInt("port"), rs.getString("gaptype"), rs.getString("chanre"),
            rs.getString("discoverymethod"),
            rs.getString("fetchipadr"), rs.getInt("fetchport"), rs.getInt("throttle"), rs.getInt("fetchrequesttypeid")
    //Util.cleanIP(rs.getString("cwbip")), rs.getInt("cwbport"), Util.cleanIP(rs.getString("edgeip")),
    //        rs.getInt("edgeport"), rs.getString("fetchtable")
    // ,rs.getDouble(longitude)
    );
  }

  // Detail Constructor, match set up with makeSeedlinkServer(), this argument list should be
  // the same as for the result set builder as both use makeSeedlinkServer()
  /**
   * create a row record from individual variables
   *
   * @param inID The ID in the database, normally zero for this type of construction
   * @param loc The key value for the database (same a name of database table)
   * @param ipadr
   * @param port
   * @param gaptype
   * @param chanre
   * @param discMethod
   * @param fetchIP
   * @param fetchPt
   * @param throt
   * @param fetchReqType
   */
  public FetchServer(int inID, String loc //USER: add fields, double lon
          ,
           String ipadr, int port,
          String gaptype, String chanre, String discMethod,
          String fetchIP, int fetchPt, int throt, int fetchReqType
  //String cwbip, int cwbport, String edgeip, int edgeport, String table

  ) {
    makeSeedlinkServer(inID, loc, ipadr, port, gaptype, chanre, discMethod, fetchIP, fetchPt, throt, fetchReqType //, lon
    //cwbip, cwbport, edgeip, edgeport, table
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
  private void makeSeedlinkServer(int inID, String loc //USER: add fields, double lon
          ,
           String ipadr, int port, String gaptype, String chanre, String discMethod,
          String fetchIP, int fetchPt, int throt, int fetchReqTyp
  //String cwbip, int cwbport, String edgeip, int edgeport, String table
  ) {
    ID = inID;
    fetchServer = loc;     // longitude = lon
    //USER:  Put asssignments to all fields from arguments here

    // ENUM example:
    //if(qtypeEnum == null) qtypeEnum=FUtil.getEnum(JDBConnection.getConnection("DATABASE"), "nsnstation","quanterraType");
    //qtype = FUtil.enumStringToInt(qtypeEnum, st); 
    // Boolean example where ENUM "True", "False
    // cont40 = FUtil.isTrue(c40);          // c40 is a string with "true" or false
    this.ipadr = ipadr;
    this.port = port;
    this.gaptype = gaptype;
    this.chanre = chanre;
    this.discoveryMethod = discMethod;
    fetchIpadr = fetchIP;
    fetchPort = fetchPt;
    throttle = throt;
    fetchRequestTypeID = fetchReqTyp;
    //this.cwbip = cwbip; this.edgeip = edgeip; this.edgeport = edgeport; this.cwbport = cwbport; fetchtable=table;
  }

  /**
   * return the key name as the string
   *
   * @return the key name
   */
  public String toString2() {
    return fetchServer + "/" + ipadr + ":" + port;
  }

  /**
   * return the key name as the string
   *
   * @return the key name
   */
  @Override
  public String toString() {
    return fetchServer;
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
  public String getFetchServer() {
    return fetchServer;
  }

  //USER:  All field getters here
  //  public double getLongitude() { return longitude;}
  public String getIP() {
    return ipadr;
  }

  public String getGapType() {
    return gaptype;
  }

  public String getChanRE() {
    return chanre;
  }

  public int getPort() {
    return port;
  }

  public String getDiscoveryMethod() {
    return discoveryMethod;
  }

  public int getFetchRequestTypeID() {
    return fetchRequestTypeID;
  }

  public String getFetchIpadr() {
    return fetchIpadr;
  }

  public int getFetchPort() {
    return fetchPort;
  }

  public int getThrottle() {
    return throttle;
  }

  //public String getEdgeIP() {return edgeip;}
  // String getCWBIP() {return cwbip;}
  //public int getEdgePort() {return edgeport;}
  //public int getCWBPort() {return cwbport;}
  //public String getFetchTable() {return fetchtable;}
  // This is needed if you need to implement Comparator
  /*  public class EquipCompare implements Comparator {
    public int compare(Object a, Object b) {
      return ((FetchServer) a).getSeedlinkServer().compareTo(((FetchServer) b).getSeedlinkServer());
    }
    public boolean equals(Object a, Object b) {
      if( ((FetchServer)a).getSeedlinkServer().equals( ((FetchServer) b).getSeedlinkServer())) return true;
      return false;
    }
//  }*/
}
