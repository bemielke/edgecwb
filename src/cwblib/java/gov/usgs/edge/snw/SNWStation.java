/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.edge.snw;

import gov.usgs.anss.dbtable.DBTable;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;

/**
 * SNWStation.java If the name of the class does not match the name of the field in the class you
 * may need to modify the rs.getSTring in the constructor.
 *
 * This SNWStation templace for creating a MySQL database object. It is not really needed by the
 * SNWStationPanel which uses the DBObject system for changing a record in the database file.
 * However, you have to have this shell to create objects containing a MySQL record for passing
 * around and manipulating. There are two constructors normally given:
 * <br>
 * 1) passed a result set positioned to the MySQL record to decode
 * <br> 2) Passed all of the data arguments needed to build the same set of data.
 * <br>
 * The SNWStation should be replaced with capitalized version (class name) for the file. snwstation
 * should be replaced with the lower case version (for variables).
 *
 * Both the constructors use the "makeSNWStation(list of data args) to set the local variables to
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
public final class SNWStation {   //implements Comparator
  // static ArrayList enumName;   // this need to be populated in makeSNWStation()

  /**
   * Creates a new instance of SNWStation
   */
  private int ID;                   // This is the MySQL ID (should alwas be named "ID"
  private String snwstation;             // This is the main key which should have same name
  // as the table which it is a key to.  That is
  // Table "category" should have key "category"

  // USER: All fields of file go here
  //  double longitude
  private String network;
  private String description;
  private double latitude;
  private double longitude;
  private double elevation;
  private long groupmask;
  private int snwruleID;
  private int operatorID;
  private int protocolID;
  private String helpstring;
  private String sort;
  private String process;
  private String cpu;
  private int disable;
  private String disableExpires;
  private String disableComment;
  private String remoteIP;
  private String remoteProcess;
  private int latencySaveInterval;

  // status stuff
  private Timestamp latencyTime;
  private int lastrecv, latency;

  private int nbytes;         // this is a convenience for the UdpChannel to add up bytes.

  // Put in correct detail constructor here.  Use makeSNWStation() by filling in all
  // the rs.get???("fieldName") for all of the arguments
  /**
   * Create a instance of this table row from a positioned result set
   *
   * @param rs The ResultSet already SELECTED
   * @exception SQLException SQLException If the ResultSet is Improper, network is down, or database
   * is down
   */
  public SNWStation(ResultSet rs) throws SQLException {  //USER: add all data field here
    makeSNWStation(rs.getInt("ID"),
             rs.getString("snwstation"),
             rs.getString("network"),
             rs.getString("description"),
             rs.getDouble("latitude"),
             rs.getDouble("longitude"),
             rs.getDouble("elevation"),
             rs.getLong("groupmask"),
             rs.getInt("snwruleID"),
             rs.getInt("operatorID"),
             rs.getInt("protocolID"),
             rs.getString("helpstring"),
             rs.getString("sort"),
             rs.getString("process"),
             rs.getString("cpu"),
             rs.getInt("disable"),
             rs.getString("disableExpires"),
             rs.getString("disableComment"),
             rs.getString("remoteIP"),
             rs.getString("remoteProcess"),
             rs.getInt("latencysaveinterval")
    );
    latencyTime = rs.getTimestamp("latencytime");
    latency = rs.getInt("latency");
    lastrecv = rs.getInt("lastrecv");
  }

  /**
   * Create a instance of this table row from a positioned result set
   *
   * @param rs The ResultSet alreay SELECTED
   * @exception SQLException SQLException If the ResultSet is Improper, network is down, or database
   * is down
   */
  public SNWStation(DBTable rs) throws SQLException {  //USER: add all data field here
    makeSNWStation(rs.getInt("ID"),
             rs.getString("snwstation"),
             rs.getString("network"),
             rs.getString("description"),
             rs.getDouble("latitude"),
             rs.getDouble("longitude"),
             rs.getDouble("elevation"),
             rs.getLong("groupmask"),
             rs.getInt("snwruleID"),
             rs.getInt("operatorID"),
             rs.getInt("protocolID"),
             rs.getString("helpstring"),
             rs.getString("sort"),
             rs.getString("process"),
             rs.getString("cpu"),
             rs.getInt("disable"),
             rs.getString("disableExpires"),
             rs.getString("disableComment"),
             rs.getString("remoteIP"),
             rs.getString("remoteProcess"),
             rs.getInt("latencysaveinterval")
    );
    latencyTime = rs.getTimestamp("latencytime");
    latency = rs.getInt("latency");
    lastrecv = rs.getInt("lastrecv");
  }

  /**
   * Reload an instance of this table row from a positioned DBTable
   *
   * @param rs The ResultSet already SELECTED
   * @exception SQLException SQLException If the ResultSet is Improper, network is down, or database
   * is down
   */
  public void reload(DBTable rs) throws SQLException {  //USER: add all data field here
    makeSNWStation(rs.getInt("ID"),
             rs.getString("snwstation"),
             rs.getString("network"),
             rs.getString("description"),
             rs.getDouble("latitude"),
             rs.getDouble("longitude"),
             rs.getDouble("elevation"),
             rs.getLong("groupmask"),
             rs.getInt("snwruleID"),
             rs.getInt("operatorID"),
             rs.getInt("protocolID"),
             rs.getString("helpstring"),
             rs.getString("sort"),
             rs.getString("process"),
             rs.getString("cpu"),
             rs.getInt("disable"),
             rs.getString("disableExpires"),
             rs.getString("disableComment"),
             rs.getString("remoteIP"),
             rs.getString("remoteProcess"),
             rs.getInt("latencysaveinterval")
    );
    latencyTime = rs.getTimestamp("latencytime");
    latency = rs.getInt("latency");
    lastrecv = rs.getInt("lastrecv");
  }

  /**
   * Update an instance of this table row from a positioned result set
   *
   * @param rs The ResultSet already SELECTED
   * @exception SQLException SQLException If the ResultSet is Improper, network is down, or database
   * is down
   */
  public void reload(ResultSet rs) throws SQLException {  //USER: add all data field here
    makeSNWStation(rs.getInt("ID"),
             rs.getString("snwstation"),
             rs.getString("network"),
             rs.getString("description"),
             rs.getDouble("latitude"),
             rs.getDouble("longitude"),
             rs.getDouble("elevation"),
             rs.getLong("groupmask"),
             rs.getInt("snwruleID"),
             rs.getInt("operatorID"),
             rs.getInt("protocolID"),
             rs.getString("helpstring"),
             rs.getString("sort"),
             rs.getString("process"),
             rs.getString("cpu"),
             rs.getInt("disable"),
             rs.getString("disableExpires"),
             rs.getString("disableComment"),
             rs.getString("remoteIP"),
             rs.getString("remoteProcess"),
             rs.getInt("latencysaveinterval")
    );
    latencyTime = rs.getTimestamp("latencytime");
    latency = rs.getInt("latency");
    lastrecv = rs.getInt("lastrecv");
  }

  // Detail Constructor, match set up with makeSNWStation(), this argument list should be
  // the same as for the result set builder as both use makeSNWStation()
  /**
   * create a row record from individual variables
   *
   * @param inID The ID in the database, normally zero for this type of construction
   * @param loc The key value for the database (same a name of database table)
   * @param network The network of the station
   * @param description The local description name
   * @param latitude Latitude in deg
   * @param longitude in Deg
   * @param elevation in Meters
   * @param groupmask using SNWGroups
   * @param snwruleID ID of rule in SNWRule table
   * @param operID ID of operator from operator table
   * @param protID ID of protocol
   * @param helpstring Some helpstring for the site
   * @param sort The sorting key
   * @param process The process for ths station
   * @param cpu The String CPU name acquiring this station
   * @param disable The disable flag
   * @param disableExpires The data the disable expires
   * @param disableComment THe disable comment
   * @param remIP the remote IP of the station
   * @param remProc The remote process if known
   * @param latSave The latency save time in minutes.
   */
  public SNWStation(int inID, String loc, String network, String description, double latitude,
          double longitude, double elevation, long groupmask, int snwruleID,
          int operID, int protID,
          String helpstring, String sort, String process, String cpu, int disable,
          String disableExpires, String disableComment //USER: add fields, double lon
          ,
           String remIP, String remProc, int latSave
  ) {
    makeSNWStation(inID, loc,
             "" //network
            ,
             "" //description
            ,
             0.0 //latitude
            ,
             0.0 //longitude
            ,
             0.0 //elevation
            ,
             0L //groupmask
            ,
             0 //snwruleid
            ,
             0 // operid
            ,
             0 // protocol id
            ,
             "" //helpstring
            ,
             "" //sort
            ,
             "" //process
            ,
             "" //cpu
            ,
             0 //disable
            ,
             "" //disableExpires
            ,
             "" //disableComment 
            ,
             "" // remoteip
            ,
             "" // remote process
            ,
             latSave // latency save interval
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
  private void makeSNWStation(int inID, String loc, String net, String desc, double lat,
          double longi, double elev, long grp, int snwID, int operID, int protID,
          String helpstr, String lsort, String proc, String lcpu, int dis,
          String disExp, String disComm //USER: add fields, double lon
          ,
           String remIP, String remProc, int latSave
  ) {
    ID = inID;
    snwstation = loc;     // longitude = lon
    //USER:  Put asssignments to all fields from arguments here
    network = net;
    description = desc;
    latitude = lat;
    longitude = longi;
    elevation = elev;
    groupmask = grp;
    snwruleID = snwID;
    operatorID = operID;
    protocolID = protID;
    helpstring = helpstr;
    sort = lsort;
    process = proc;
    cpu = lcpu;
    disable = dis;
    disableExpires = disExp;
    disableComment = disComm;
    remoteIP = remIP;
    remoteProcess = remProc;
    latencySaveInterval = latSave;
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
    return snwstation;
  }

  /**
   * return the key name as the string
   *
   * @return the key name
   */
  @Override
  public String toString() {
    return snwstation + "-" + network;
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
  public String getSNWStation() {
    return snwstation;
  }

  //USER:  All field getters here
  public String getNetwork() {
    return network;
  }

  public String getDescription() {
    return description;
  }

  public double getLongitude() {
    return longitude;
  }

  public double getLatitude() {
    return latitude;
  }

  public double getElevation() {
    return elevation;
  }

  public long getGroupmask() {
    return groupmask;
  }

  public int getSNWRuleID() {
    return snwruleID;
  }

  public int getOperatorID() {
    return operatorID;
  }

  public int getProtocolID() {
    return protocolID;
  }

  public String getHelpstring() {
    return helpstring;
  }

  public String getSort() {
    return sort;
  }

  public String getProcess() {
    return process;
  }

  public String getCpu() {
    return cpu;
  }

  public int getDisable() {
    return disable;
  }

  public String getDisableExpires() {
    return disableExpires;
  }

  public String getDisableComment() {
    return disableComment;
  }

  public String getRemoteIP() {
    return remoteIP;
  }

  public String getRemoteProcess() {
    return remoteProcess;
  }

  public int getLatencySaveInterval() {
    return latencySaveInterval;
  }

  // Status access
  public int getLatency() {
    return latency;
  }

  public int getLastRecv() {
    return lastrecv;
  }

  public Timestamp getLatencyTime() {
    return latencyTime;
  }

  // convenience methods to allow a user to accumulate byte count
  public void addNbytes(int nb) {
    nbytes += nb;
  }

  public int getNbytes() {
    return nbytes;
  }

  public void setNbytes(int nb) {
    nbytes = nb;
  }

  public static long setSNWGroupsForLinks(int lastMile, int altBackHaul, int useBH, boolean q330tunnel, String dasType, long groupMask) {
    ArrayList groups = SNWGroup.getGroups();
    //            Q330*,Q680*,UdpTunnel,G16,SM5*,GTN, BHpub,MPInt*,HZN1,SMex6 NLV BHGTN  BHNLV GTNVPN  NLVVPN
    int[] bits = {2, 3, 5, 6, 8, 11, 12, 13, 29, 45, 53, 54, 55, 56, 57};
    //             0     1        2       3    4   5   6       7     8    9  10       11    12    13  
    //SNWGroup ID -1 for various groups
    //int Q730=0;
    int Q330 = 0;
    int Q680 = 1;
    int TUNNEL = 2;
    int GG16 = 3;
    int GSATMEX5 = 4;
    //int GWILDBLUE=5;
    int HUGHESNOCGTN = 5;
    int PUBLICBH = 6;
    int GMPINTEGRAL = 7;
    int GHORIZONS1 = 8;
    int GSATMEX6 = 9;
    int HUGHESNOCNLV = 10;
    int GTNBH = 11;
    int NLVBH = 12;
    int GTNVPN = 13;
    int NLVVPN = 14;

    // Data of IDs for the various links.
    int SATMEX5 = 1;
    int WILDBLUE = 7;
    int PUBLIC = 4;
    int USGSLOCAL = 3;
    int HORIZONS1 = 8;
    int G16 = 6;
    int DFC = 5;
    int MPINTEGRAL = 10;
    int G11 = 11;
    int BHGTN = 12;
    int BHNLV = 13;
    int SATMEX6 = 15;

    long mask = 0;
    for (int i = 0; i < bits.length; i++) {
      mask |= 1L << (bits[i] - 1);    // mask of all IDs we will set
    }    //Util.prt("grous bef="+Util.toHex(groupMask));
    groupMask &= ~mask;       // remove these bits from the mask
    //Util.prt("aft="+Util.toHex(groupMask)+" mask="+Util.toHex(mask)+" inverted="+Util.toHex(~mask));
    //if(dasType.contains("Q330")) groupMask |= 2;
    //if(dasType.contains("Q730")) groupMask |= 1;
    //if(dasType.contains("Q680")) groupMask |= 4;

    if (q330tunnel) {
      groupMask |= 1L << (bits[TUNNEL] - 1);   // Set the tunnel flag if it is on
    }
    // See if the lastMile or Backhauls are VSATs
    if (altBackHaul == BHGTN) {
      if (useBH != 0) {
        groupMask |= 1L << (bits[GTNBH] - 1);
      } else {
        groupMask |= 1L << (bits[PUBLICBH] - 1) | 1L << (bits[GTNVPN] - 1); // Uses public internet on GTNVPN
      }
      groupMask |= 1L << (bits[HUGHESNOCGTN] - 1);
      if (lastMile != G16 && lastMile != SATMEX5 && lastMile != HORIZONS1 && lastMile != SATMEX6 && lastMile != G11) {
        groupMask |= 0; //1L<<(bits[PUBLICNON]-1);  // this does nothing
      }
    }
    if (altBackHaul == BHNLV) {
      if (useBH != 0) {
        groupMask |= 1L << (bits[NLVBH] - 1);
      } else {
        groupMask |= 1L << (bits[PUBLICBH] - 1) | 1L << (bits[NLVVPN] - 1);
      }
      groupMask |= 1L << (bits[HUGHESNOCNLV] - 1);
      if (lastMile != G16 && lastMile != SATMEX6 && lastMile != HORIZONS1) {
        groupMask |= 0; //1L<<(bits[PUBLICNON]-1);
      }
    }
    if (altBackHaul == DFC) {
      if (lastMile == HORIZONS1) {
        groupMask |= 1L << (bits[HUGHESNOCNLV] - 1);
      } else if (lastMile == G16 || lastMile == SATMEX5) {
        groupMask |= 1L << (bits[HUGHESNOCGTN] - 1);
      }
      if (lastMile != G16 && lastMile != SATMEX5 && lastMile != HORIZONS1) {
        groupMask |= 0; //1L<<(bits[PUBLICNON]-1);
      }
    }

    if (altBackHaul == USGSLOCAL) {
      groupMask |= 1L << (bits[PUBLICBH] - 1);
    }

    if (altBackHaul == PUBLIC) {
      groupMask |= 1L << (bits[PUBLICBH] - 1);
    }

    if (lastMile == SATMEX6) {
      groupMask |= 1L << (bits[GSATMEX6] - 1);
      groupMask |= 1L << (bits[HUGHESNOCNLV] - 1);
    }
    if (lastMile == G16) {
      groupMask |= 1L << (bits[GG16] - 1);
      groupMask |= 1L << (bits[HUGHESNOCGTN] - 1);
    }
    //if(lastMile == MPINTEGRAL) {
    //  groupMask |= 1L<<(bits[GMPINTEGRAL]-1);
    //  if(altBackHaul != PUBLIC) groupMask |= 0; //1L<<(bits[PUBLICNON]-1);      // There is no back haul so it has to go public
    //}
    if (lastMile == WILDBLUE) {
      groupMask |= 0; //1L<<(bits[GWILDBLUE]-1);
      if (altBackHaul != PUBLIC) {
        groupMask |= 0; //1L<<(bits[PUBLICNON]-1);
      }
    }
    if (lastMile == HORIZONS1) {
      groupMask |= 1L << (bits[GHORIZONS1] - 1);
      groupMask |= 1L << (bits[HUGHESNOCNLV] - 1);
    }

    if (lastMile == PUBLIC) {
      if (altBackHaul != PUBLIC) {
        groupMask |= 0; //1L<<(bits[PUBLICNON]-1);
      }
    }
    return groupMask;
  }

}
