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

/**
 * FKSetup.java If the name of the class does not match the name of the field in the class you may
 * need to modify the rs.getSTring in the constructor.
 *
 * This FKSetup template for creating a database database object. It is not really needed by the
 * FKSetupPanel which uses the DBObject system for changing a record in the database file. However,
 * you have to have this shell to create objects containing a database record for passing around and
 * manipulating. There are two constructors normally given:
 * <br>
 * 1) passed a result set positioned to the database record to decode
 * <br> 2) Passed all of the data arguments needed to build the same set of data.
 * <br>
 * The FKSetup should be replaced with capitalized version (class name) for the file. fk should be
 * replaced with the lower case version (for variables).
 *
 * Both the constructors use the "makeFKSetup(list of data args) to set the local variables to the
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
public final class FKSetup {   //implements Comparator
  // static ArrayList enumName;   // this need to be populated in makeFKSetup()

  /**
   * Creates a new instance of FKSetup
   */
  private int ID;                   // This is the database ID (should alwas be named "ID"
  private String fk;             // This is the main key which should have same name
  // as the table which it is a key to.  That is
  // Table "category" should have key "category"

  // USER: All fields of file go here
  //  double longitude
  private int roleID;
  private double fkhpc, fklpc;
  private double beamWindow;
  private int ftlen;
  private double highpassfreq, lowpassfreq, kmax, snrLimit;
  private String channels, refchan, args, comment;
  private int npoles, npass, nk, beammethod, latencySec;

  // Put in correct detail constructor here.  Use makeFKSetup() by filling in all
  // the rs.get???("fieldName") for all of the arguments
  /**
   * Create a instance of this table row from a positioned result set
   *
   * @param rs The ResultSet already SELECTED
   * @exception SQLException SQLException If the ResultSet is Improper, network is down, or database
   * is down
   */
  public FKSetup(ResultSet rs) throws SQLException {  //USER: add all data field here
    reload(rs);
  }

  /**
   * reload the object data from a given result set
   *
   * @param rs The ResultSet to load the object from
   * @throws SQLException If one is thrown
   */
  public final void reload(ResultSet rs) throws SQLException {
    makeFKSetup(rs.getInt("ID"), rs.getString("fk"), rs.getInt("roleid"), rs.getDouble("fkhpc"),
            rs.getDouble("fklpc"), rs.getDouble("beamwindow"), rs.getInt("beammethod"), rs.getInt("ftlen"),
            rs.getInt("latencysecs"), rs.getDouble("highpassfreq"),
            rs.getDouble("lowpassfreq"), rs.getInt("npoles"), rs.getInt("npass"), rs.getDouble("kmax"),
            rs.getInt("nk"), rs.getString("args"), rs.getString("refchan"), rs.getString("channels"),
            rs.getDouble("snrlimit"), rs.getString("comment")
    // ,rs.getDouble(longitude)
    );
  }

  // Detail Constructor, match set up with makeFKSetup(), this argument list should be
  // the same as for the result set builder as both use makeFKSetup()
  /**
   * create a row record from individual variables
   *
   * @param inID The ID in the database, normally zero for this type of construction
   * @param loc The key value for the database (same a name of database table)
   * @param role roleID assigned to compute this FK
   * @param fkhpc fk frequency space lower frequency limit
   * @param fklpc fk frequence space higher frequence limit
   * @param beamwindow Minimum number of seconds to put in each window
   * @param beammethod 0 is normal time domain stacking, others for nth root
   * @param highpassfreq Time domain highpass filter corner for preprocessing, and post processing
   * @param latency seconds to wait for real time data
   * @param ftlen The FTEST smoothing interval
   * @param lowpassfreq The Time domain lowpass filter corner for preprocessing and post processing
   * @param npoles Number of post of time domain filters
   * @param npass Number of passes of the time domain filters
   * @param kmax The maximum k value to consider in the FK space k = 2*pi*f/Vapp or w s in slowness
   * @param nk The number of evaluation points in FK space (must be odd) from -kmax to kmax is
   * sampled over this many intervals
   * @param args Any additional arguments like -dbg
   * @param refchan The 12 character NSCL name of the reference channel, preceed with a '+' if it is
   * to be used in calculation
   * @param channels The list of channels to use in the computation
   * @param snrLimit Signal to noise limit
   * @param cmnt Comments about channel list (especially why disabled)
   */
  public FKSetup(int inID, String loc, int role, double fkhpc, double fklpc,
          double beamwindow, int beammethod, int ftlen, int latency,
          double highpassfreq, double lowpassfreq, int npoles, int npass, double kmax, int nk,
          String args, String refchan, String channels, double snrLimit, String cmnt //USER: add fields, double lon
  ) {
    makeFKSetup(inID, loc, role, fkhpc, fklpc, beamwindow, beammethod, ftlen, latency,
            highpassfreq, lowpassfreq, npoles, npass, kmax, nk, args, refchan, channels, snrLimit, cmnt//, lon
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
  private void makeFKSetup(int inID, String loc, int role, double fkhpc, double fklpc,
          double beamwindow, int beammethod, int ftlen, int latency,
          double highpassfreq, double lowpassfreq, int npoles, int npass, double kmax,
          int nk, String args, String refchan, String channels, double snrLim, String cmnt//USER: add fields, double lon
  ) {
    ID = inID;
    fk = loc;     // longitude = lon
    //USER:  Put asssignments to all fields from arguments here

    // ENUM example:
    //if(qtypeEnum == null) qtypeEnum=FUtil.getEnum(JDBConnection.getConnection("DATABASE"), "nsnstation","quanterraType");
    //qtype = FUtil.enumStringToInt(qtypeEnum, st); 
    // Boolean example where ENUM "True", "False
    // cont40 = FUtil.isTrue(c40);          // c40 is a string with "true" or false
    roleID = role;
    this.fkhpc = fkhpc;
    this.fklpc = fklpc;
    this.beamWindow = beamwindow;
    this.beammethod = beammethod;
    this.ftlen = ftlen;
    this.latencySec = latency;
    this.highpassfreq = highpassfreq;
    this.lowpassfreq = lowpassfreq;
    this.npoles = npoles;
    this.npass = npass;
    this.kmax = kmax;
    this.nk = nk;
    this.args = args;
    this.refchan = refchan;
    this.channels = channels;
    this.comment = cmnt;
    this.snrLimit = snrLim;

  }

  /**
   * return the key name as the string
   *
   * @return the key name
   */
  public String toString2() {
    return fk;
  }

  /**
   * return the key name as the string
   *
   * @return the key name
   */
  @Override
  public String toString() {
    return fk;
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
  public String getFKSetup() {
    return fk;
  }

  public int getRoleID() {
    return roleID;
  }

  public double getFKHPC() {
    return fkhpc;
  }

  public double getFKLPC() {
    return fklpc;
  }

  public double getHighPassFreq() {
    return highpassfreq;
  }

  public double getLowPassFreq() {
    return lowpassfreq;
  }

  public double getKMAX() {
    return kmax;
  }

  public int getNK() {
    return nk;
  }

  public int getNpoles() {
    return npoles;
  }

  public double getBeamWindow() {
    return beamWindow;
  }

  public int getFTLEN() {
    return ftlen;
  }

  public int getNpass() {
    return npass;
  }

  public String getArgs() {
    return args;
  }

  public String getRefChan() {
    return refchan;
  }

  public String getChannels() {
    return channels;
  }

  public String getComment() {
    return comment;
  }

  public int getLatencySec() {
    return latencySec;
  }

  public int getBeamMethod() {
    return beammethod;
  }

  public double getSNRLimit() {
    return snrLimit;
  }

  //USER:  All field getters here
  //  public double getLongitude() { return longitude;}
  // This is needed if you need to implement Comparator
  /*  public class EquipCompare implements Comparator {
    public int compare(Object a, Object b) {
      return ((FKSetup) a).getFKSetup().compareTo(((FKSetup) b).getFKSetup());
    }
    public boolean equals(Object a, Object b) {
      if( ((FKSetup)a).getFKSetup().equals( ((FKSetup) b).getFKSetup())) return true;
      return false;
    }
//  }*/
}
