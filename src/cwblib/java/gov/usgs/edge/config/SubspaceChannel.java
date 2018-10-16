/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.edge.config;

//package gov.usgs.edge.template;
//import java.util.Comparator;
import gov.usgs.anss.db.DBConnectionThread;
import gov.usgs.anss.util.Distaz;
import gov.usgs.anss.util.Util;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

/**
 * SubspaceChannel.java If the name of the class does not match the name of the field in the class
 * you may need to modify the rs.getSTring in the constructor.
 *
 * This SubspaceChannel template for creating a database database object. It is not really needed by
 * the SubspaceChannelPanel which uses the DBObject system for changing a record in the database
 * file. However, you have to have this shell to create objects containing a database record for
 * passing around and manipulating. There are two constructors normally given:
 * <br>
 * 1) passed a result set positioned to the database record to decode
 * <br> 2) Passed all of the data arguments needed to build the same set of data.
 * <br>
 * The SubspaceChannel should be replaced with capitalized version (class name) for the file.
 * subspaceChannel should be replaced with the lower case version (for variables).
 *
 * Both the constructors use the "makeSubspaceChannel(list of data args) to set the local variables
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
public final class SubspaceChannel {    //implements Comparator
  // static ArrayList enumName;   // this need to be populated in makeSubspaceChannel()

  public static ArrayList<SubspaceChannel> subspaceChannels;
  /**
   * Creates a new instance of SubspaceChannel
   */
  private int ID;                   // This is the database ID (should alwas be named "ID"
  private String subspaceChannel;             // This is the main key which should have same name
  // as the table which it is a key to.  That is
  // Table "category" should have key "category"

  // USER: All fields of file go here
  //  double longitude
  private int npoles, areaID;
  private String nscl1, nscl2, nscl3, filterType, detectionType;
  private double rate, hpCorner, lpCorner, detectionThreshold, templateDuration, averageDuration;
  private double latitude, longitude, elevation, preevent, completeness;
  private double distance;

  // Put in correct detail constructor here.  Use makeSubspaceChannel() by filling in all
  // the rs.get???("fieldName") for all of the arguments
  /**
   * Create a instance of this table row from a positioned result set
   *
   * @param rs The ResultSet already SELECTED
   * @exception SQLException SQLException If the ResultSet is Improper, network is down, or database
   * is down
   */
  public SubspaceChannel(ResultSet rs) throws SQLException {  //USER: add all data field here
    reload(rs);
  }

  /**
   * reload the object data from a given result set
   *
   * @param rs The ResultSet to load the object from
   * @throws SQLException If one is thrown
   */
  public final void reload(ResultSet rs) throws SQLException {
    makeSubspaceChannel(rs.getInt("ID"), rs.getString("subspacechannel"), rs.getInt("areaid"),
            rs.getString("nscl1"), rs.getString("nscl2"), rs.getString("nscl3"),
            rs.getDouble("rate"),
            rs.getDouble("hpcorner"), rs.getDouble("lpcorner"), rs.getInt("npoles"),
            rs.getString("filtertype"),
            rs.getDouble("detectionthreshold"), rs.getString("detectionthresholdtype"), rs.getDouble("templateduration"),
            rs.getDouble("latitude"), rs.getDouble("longitude"), rs.getDouble("elevation"), 
            rs.getDouble("preevent"), rs.getDouble("averagingduration"), rs.getDouble("completeness") // ,rs.getDouble(longitude)
    );
  }

  /**
   * create a row record from individual variables
   *
   * @param inID The ID in the database, normally zero for this type of construction
   * @param sschan Subspace channel name (can include wild cards)
   * @param area The subspace area ID
   * @param seed1 NSCL 1 12 character
   * @param seed2 NSCL 2 12 character
   * @param seed3 NSCL 3 12 character
   * @param rt In Hz
   * @param hp Highpass corner in Hz
   * @param lp Lowpass corner in Hz
   * @param np Number of poles on the high and low pass filters
   * @param ftype Type of filter (bandpass, highpass or lowpass)
   * @param detThres Detection threshold
   * @param detType Detection type normally 'constant' in operations
   * @param tempDur Template duration in seconds
   * @param lat Latitude of this channel
   * @param lng Longitude of this channel
   * @param elev elevation of this channel
   * @param pre Pre-event memory (normally zero)
   * @param avgDur averaging duration (probably not used)
   * @param completeness normally 0.9 parameter to the SVD eigenvalues routine
   */
  public final void reload(int inID, String sschan, int area, //USER: add fields, double lon
          String seed1, String seed2, String seed3, double rt,
          double hp, double lp, int np, String ftype,
          double detThres, String detType, double tempDur,
          double lat, double lng, double elev, double pre, double avgDur, double completeness
  ) {
    makeSubspaceChannel(inID, sschan, area, //, lon
            seed1, seed2, seed3, rt, hp, lp, np, ftype, detThres, detType, tempDur, lat, lng, elev, pre, avgDur, completeness
    );

  }

  // Detail Constructor, match set up with makeSubspaceChannel(), this argument list should be
  // the same as for the resultexit set builder as both use makeSubspaceChannel()
  /**
   * create a row record from individual variables
   *
   * @param inID The ID in the database, normally zero for this type of construction
   * @param sschan SubspaceChannel including while cards (NNSSSSSCCCLL)
   * @param area the area ID that this channel is associated with
   * @param seed1 NSCL 1 12 character
   * @param seed2 NSCL 2 12 character
   * @param seed3 NSCL 3 12 character
   * @param rt In Hz
   * @param hp Highpass corner in Hz
   * @param lp Lowpass corner in Hz
   * @param np Number of poles on the high and low pass filters
   * @param ftype Type of filter (bandpass, highpass or lowpass)
   * @param detThres Detection threshold
   * @param detType Detection type normally 'constant' in operations
   * @param tempDur Template duration in seconds
   * @param lat Latitude of this channel
   * @param lng Longitude of this channel
   * @param elev elevation of this channel
   * @param pre Pre-event memory (normally zero)
   * @param avgDur averaging duration (probably not used)
   * @param complete normally 0.9 parameter to the SVD eigenvalues routine
   */
  public SubspaceChannel(int inID, String sschan, int area //USER: add fields, double lon
          ,
           String seed1, String seed2, String seed3, double rt, double hp, double lp, int np, String ftype,
          double detThres, String detType, double tempDur,
          double lat, double lng, double elev, double pre, double avgDur, double complete
  ) {
    makeSubspaceChannel(inID, sschan, area, //, lon
            seed1, seed2, seed3, rt, hp, lp, np, ftype, detThres, detType, tempDur, lat, lng, elev, pre, avgDur, complete
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
  private void makeSubspaceChannel(int inID, String sschan, int area, //USER: add fields, double lon
          String seed1, String seed2, String seed3, double rt,
          double hp, double lp, int np, String ftype,
          double detThres, String detType, double tempDur,
          double lat, double lng, double elev, double pre, double avgDur, double complete
  ) {
    ID = inID;
    areaID = area;     // longitude = lon
    //USER:  Put asssignments to all fields from arguments here
    subspaceChannel = sschan;
    // ENUM example:
    //if(qtypeEnum == null) qtypeEnum=FUtil.getEnum(JDBConnection.getConnection("DATABASE"), "nsnstation","quanterraType");
    //qtype = FUtil.enumStringToInt(qtypeEnum, st); 
    // Boolean example where ENUM "True", "False
    // cont40 = FUtil.isTrue(c40);          // c40 is a string with "true" or false
    nscl1 = seed1;
    nscl2 = seed2;
    nscl3 = seed3;
    rate = rt;
    hpCorner = hp;
    lpCorner = lp;
    npoles = np;
    filterType = ftype.toLowerCase();
    detectionThreshold = detThres;
    detectionType = detType;
    templateDuration = tempDur;
    latitude = lat;
    longitude = lng;
    elevation = elev;
    preevent = pre;
    averageDuration = avgDur;
    completeness = complete;
    SubspaceArea area1 = SubspaceArea.getSubspaceAreaWithID(areaID);
    if(area1 != null) {
      double [] results = Distaz.distaz(latitude, longitude, area1.getLatitude(), area1.getLongitude());
      if(results != null) distance = results[1];
    }
  }

  /**
   * return the key name as the string
   *
   * @return the key name
   */
  public String toString2() {
    return nscl1 + " " + " hp=" + hpCorner + " lp=" + lpCorner + " np=" + npoles
            + " " + filterType + " lat/lng/elv=" + latitude + "/" + longitude + "/" + elevation;
  }

  /**
   * return the key name as the string
   *
   * @return the key name
   */
  @Override
  public String toString() {
    return (subspaceChannel+"      ").substring(0,12)+" "+Util.df22(distance);
  }
  // getter
  StringBuilder sb = new StringBuilder(10);

  public StringBuilder getFixedToString() {
    sb.append(nscl1).append(":").append(nscl2).append(":").append(nscl3).
            append(" rt=").append(rate).append(" ").
            append(" coord=").append(latitude).append(" ").append(longitude).append(" ").append(elevation);
    return sb;
  }

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
  //public String getSubspaceChannel() {return subspaceChannel;}

  //USER:  All field getters here
  //  public double getLongitude() { return longitude;}
  public int getAreaID() {
    return areaID;
  }

  public String getSubspaceChannel() {
    return subspaceChannel;
  }

  public String getNSCL1() {
    return nscl1;
  }

  public String getNSCL2() {
    return nscl2;
  }

  public String getNSCL3() {
    return nscl3;
  }

  public String getFilterType() {
    return filterType;
  }

  public String getDetectionType() {
    return detectionType;
  }

  public double getRate() {
    return rate;
  }

  public double getHPCorner() {
    return hpCorner;
  }

  public double getLPCorner() {
    return lpCorner;
  }

  public double getDetectionThreshold() {
    return detectionThreshold;
  }

  public double getTemplateDuration() {
    return templateDuration;
  }

  public double getCompleteness() {
    return completeness;
  }
  
  public double getLatitude() {
    return latitude;
  }

  public double getLongitude() {
    return longitude;
  }

  public double getElevation() {
    return elevation;
  }

  public double getDistance() {
    return distance;
  }

  public int getNpoles() {
    return npoles;
  }

  public static void sortListByDistance() {
    class OrderByDistance implements Comparator<SubspaceChannel>
    {
        @Override
        public int compare(SubspaceChannel s1, SubspaceChannel s2)
        {
            return Double.compare(s1.getDistance(),s2.getDistance());
        }
    }
    Collections.sort(subspaceChannels,new OrderByDistance());
  }

  public double getPreEvent() {return preevent;}
  public double getAveragingDuration () {return averageDuration;}
  
  
  // This is needed if you need to implement Comparator
  /*  public class EquipCompare implements Comparator {
    public int compare(Object a, Object b) {
      return ((SubspaceChannel) a).getSubspaceChannel().compareTo(((SubspaceChannel) b).getSubspaceChannel());
    }
    public boolean equals(Object a, Object b) {
      if( ((SubspaceChannel)a).getSubspaceChannel().equals( ((SubspaceChannel) b).getSubspaceChannel())) return true;
      return false;
    }
//  }*/
  public static SubspaceChannel getSubspaceChannelWithAreaChannel(int areaID, String chan) {
    if (subspaceChannels == null) {
      makeSubspaceChannels();
    }
    for (SubspaceChannel ch : subspaceChannels) {
      if (ch.getAreaID() == areaID && ch.getSubspaceChannel().equals(chan)) {
        return ch;
      }
    }
    return null;
  }

  public static SubspaceChannel getSubspaceChannelWithID(int ID) {
    if (subspaceChannels == null) {
      makeSubspaceChannels();
    }
    for (SubspaceChannel ch : subspaceChannels) {
      if (ch.getID() == ID) {
        return ch;
      }
    }
    return null;
  }

  public static void makeSubspaceChannels() {
    try {
      Statement stmt;
      if (DBConnectionThread.getConnection(DBConnectionThread.getDBSchema()) != null) {
        stmt = DBConnectionThread.getConnection(DBConnectionThread.getDBSchema()).createStatement();   // used for query
      } else {
        stmt = DBConnectionThread.getConnection("DBServer").createStatement();    // This is for call in the PickManager in QueryMom
      }
      makeSubspaceChannels(DBConnectionThread.getConnection(DBConnectionThread.getDBSchema()).createStatement() );
    } catch (SQLException e) {
      Util.SQLErrorPrint(e, "makeSubspaceChannels() on table SQL failed");
    }
  }
  // This routine should only need tweeking if key field is not same as table name
  public static void makeSubspaceChannels(Statement stmt) {
    if (subspaceChannels != null) {
      return;
    }
    subspaceChannels = new ArrayList<SubspaceChannel>(100);
    try {
      String s = "SELECT * FROM edge.subspacechannel ORDER BY nscl1;";
      try (ResultSet rs = stmt.executeQuery(s)) {
        while (rs.next()) {
          SubspaceChannel ch = new SubspaceChannel(rs);
          //        Util.prt("MakeSubspacChannels() i="+v.size()+" is "+event.getGroups());
          subspaceChannels.add(ch);
        }
      }
    } catch (SQLException e) {
      Util.SQLErrorPrint(e, "makeSubspaceChannels() on table SQL failed");
    }
  }
}
