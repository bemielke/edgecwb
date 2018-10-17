/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.edge.config;

import gov.usgs.anss.util.PNZ;
import gov.usgs.anss.util.StaSrv;
import gov.usgs.anss.util.Util;
import gov.usgs.anss.dbtable.DBTable;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;

/**
 * Channel.java If the name of the class does not match the name of the field in the class you may
 * need to modify the rs.getSTring in the constructor.
 *
 * This Channel template for creating a database object. It is not really needed by the ChannelPanel
 * which uses the DBObject system for changing a record in the database file. However, you have to
 * have this shell to create objects containing a database record for passing around and
 * manipulating. There are two constructors normally given:
 * <br>
 * 1) passed a result set positioned to the database record to decode
 * <br> 2) Passed all of the data arguments needed to build the same set of data.
 * <br>
 * The Channel should be replaced with capitalized version (class name) for the file. channel should
 * be replaced with the lower case version (for variables).
 *
 * Both the constructors use the "makeChannel(list of data args) to set the local variables to the
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
 * <br> if(fieldEnum == null) fieldEnum = FUtil.getEnum(UC.getConnection(),"table","fieldName");
 * <br>
 * <br> // Get the int corresponding to an Enum String for storage in the data base and
 * <br> // the index into the JComboBox representing this Enum
 * <br> localVariable = FUtil.enumStringToInt(fieldEnum, enumString);
 * <br> Created on July 8, 2002, 1:23 PM
 * <PRE>
 * Table : edge.channel
 * field name    Description
 * ID            The unique ID for the row
 * channel       A NNSSSSSCCCLL fixed field with the SEED name of the channel
 * commgroupid   The communication group this channel is in (optional)
 * operatorid    The ID in the operator table of the operator (optional)
 * protocolid    The ID in the protocol table use to get this channel (optional)
 * sendto        This is a 64 bit mask where 1 <<(edge.sendto.id-1)
 *                is set if this channel is in the sendto
 * flags         This is a 64 bit mask to the edge.flags table
 * links         This is a 64 bit mask to the edge.link table
 * delay         This field was to implement output delays and was never used
 * sort1         A key for sorting used by ChannelDisplay of optional ordering
 * sort2         A key for sorting used by ChannelDisplay for optional ordering
 * rate          The digitizing rate in Hertz
 * nsnnet        OBSOLETE : used when VMS contributed data from the NSN
 * nsnnode        "         "
 * nsnchan        "         "
 * expected      If true, this channel is part of the based set for monitoring.
 *               If set, programs like channel display will always create a row for this channel
 * gaptype       Fetchers use this type to identify which fetcher method or access is to be used
 * lastData      Approximate time of the last data from this channel.  Time is exact if older than 1 day.
 * mdsflags      A 64 bit mask based on the
 * hydraflags    A 64 bit mask of processing characteristics used by Hydra
 * hydravalue    The figure of merit for this channels, used by Hydra
 * </PRE> @author D.C. Ketchum
 */
public final class Channel {    //implements Comparator

  public static final long FLAG_RESPONSE_IN_MDS = 1;
  public static final long FLAG_RESPONSE_BAD_PDF = 2;
  public static final long RESP_FLAG_DO_NOT_USE = 4;
  public static final long RESP_FLAG_BAD = 8;
  public static final long RESP_FLAG_A0_WARN = 16;
  public static final long RESP_FLAG_A0_BAD = 32;
  public static final long RESP_FLAG_SENSITIVITY_WARN = 64;
  public static final long RESP_FLAG_SENSITIVITY_BAD = 128;
  public static final long RESP_FLAG_ELEVATION_INCONSISTENT = 256;
  public static final long RESP_FLAG_MIXED_POLES = 512;       // True if mixed poles are found.
  public final static long CHANNEL_FLAGS_HAS_METADATA = 1L << (10 - 1);     // HACK : this is the mask for channel.flags for current metadata
  public final static long CHANNEL_FLAGS_DERIVELH_DISABLE = 1L << (11 - 1); // HACK : this is the mask for channel.flags for deriveLH disabled
  public final static long CHANNEL_FLAGS_FP0 = 1L << (12 - 1);               // HACK : this is the maks for channel.flags for FP0
  public final static long CHANNEL_FLAGS_FP1 = 1L << (13 - 1);               // HACK : this is the maks for channel.flags for FP1
  public final static long CHANNEL_FLAGS_FP2 = 1L << (14 - 1);               // HACK : this is the maks for channel.flags for FP2
  public final static long CHANNEL_FLAGS_FP3 = 1L << (15 - 1);               // HACK : this is the maks for channel.flags for FP3
  public final static long CHANNEL_FLAGS_FP4 = 1L << (16 - 1);               // HACK : this is the maks for channel.flags for FP4

  // static ArrayList enumName;   // this need to be populated in makeChannel()
  private static StaSrv stasrv;     // A single client to get meta-data
  /**
   * Creates a new instance of Channel
   */
  private int ID;                   // This is the database ID (should alwas be named "ID"
  private String channel;             // This is the main key which should have same name
  // as the table which it is a key to.  That is
  // Table "category" should have key "category"

  // USER: All fields of file go here
  //  double longitude
  private final StringBuilder channelsb = new StringBuilder(12);
  private int commgroupID;      // pointer to comm group
  private int netgroupID;       // Pointer to the net group
  private int operatorID;
  private int protocolID;       // pointer to the protocol table.
  private long sendto;          ////  Mask of send to  places
  private long flags;
  private long links;
  private int delay;
  private double rate;
  private String sort1;
  private String sort2;
  private int nsnnet;   // This has been hijacked as the picker id now.
  private int nsnnode;
  private int nsnchan;
  int expected;
  private String gapType;     // the two character gap type
  Timestamp lastData;
  private long mdsflags;
  private long hydraflags;
  private double hydraValue;
  private Timestamp created;

  // These fields are not part of the database channel table, but are other data
  // associated with a channel
  private long lastMetaTime;    // Last time this data was update, used to pace updates
  private double[] coord;      // coordinant
  private String coordText;
  private double[] orient;     // orientation
  private String orientText;
  private PNZ cookedResponse;   // Standard poles and zeros with a0 response
  private String cookedText;
  private String[] comment;    // comment[0] - long name, comment[1] = network
  private String ircode;
  private String otherAlias;
  private String aliasText;

  public int getFilterPickerID() {
    return (int) ((flags >> 11) & 0x1f);
  }

  public int getPickerID() {
    return nsnnet;
  }

  public String[] getComment() {
    return comment;
  }

  public String getCommentText() {
    String s = "";
    for (String comment1 : comment) {
      s += comment1 + "\n";
    }
    s += "\n";
    return s;
  }

  /**
   * get the latitude, longitude and elevation in meters as an array
   *
   * @return double[] with lat, long and elevation in meters, zeros if unknown
   */
  public double[] getCoordinates() {
    //chkMetaUpdate();
    return coord;
  }

  /**
   * get the latitude, longitude and elevation in meters as an String
   *
   * @return String lat, long and elevation in meters, zeros if unknown
   */
  public String getCoordinatesText() {
    return coordText;
  }

  /**
   * get the azimuth, dip and depth of burial, if available
   *
   * @return double[] with azimuth (cw from N), dip (vert=-90) and depth (M), null if unknown
   */
  public double[] getOrientation() {
    //chkMetaUpdate();
    return orient;
  }

  /**
   * get the azimuth, dip and depth of burial, if available as a string
   *
   * @return String with azimuth (cw from N), dip (vert=-90) and depth (M), null if unknown
   */
  public String getOrientationText() {
    return orientText;

  }

  /**
   * get the cooked response as a PNZ object
   *
   * @return PNZ object with response or null if unknown
   */
  public PNZ getCookedResponse() {
    //chkMetaUpdate();
    return cookedResponse;
  }

  /**
   * get the cooked response as a String
   *
   * @return String with response or null if unknown
   */
  public String getCookedResponseText() {
    return cookedText;
  }

  /**
   * get IR code
   *
   * @return the IR code
   */
  public String getIRcode() {
    return ircode;
  }

  /**
   * get other alias string
   *
   * @return other alias string (groups of 8 characters each)
   */
  public String getOtherAlias() {
    return otherAlias;
  }

  /**
   * get the text for the aliases
   *
   * @return the text
   */
  public String getAliasText() {
    return aliasText;
  }

  public void setSendto(long mask) {
    sendto = mask;
  }

  public boolean setCoordinates(double[] co, String t) {
    boolean ret = t.equals(coordText == null ? "" : coordText);
    coordText = t;
    if (coord == null) {
      coord = new double[3];
    }
    if (!ret) {
      System.arraycopy(co, 0, coord, 0, 3);
    }
    return ret;
  }

  public boolean setOrientation(double[] co, String t) {
    boolean ret = t.equals(orientText == null ? "" : orientText);
    if (!ret) {
      orientText = t;
      Util.prt("Replace orientation " + channel + " was \n|" + orientText + "\n|" + t + "|");
    }
    if (orient == null) {
      orient = new double[3];
    }
    if (!ret && co != null) {
      System.arraycopy(co, 0, orient, 0, 3);
    }
    return ret;
  }

  public boolean setComment(String[] co) {
    boolean ret = true;
    if (comment == null) {
      comment = new String[co.length];
    }
    if (comment.length < co.length) {
      comment = new String[co.length];
    }
    for (int i = 0; i < co.length; i++) {
      if (comment[i] == null) {
        comment[i] = "";
      }
      comment[i] = co[i];
      if (!comment[i].equals(co[i])) {
        ret = false;
      }
    }
    return ret;
  }

  public boolean setAlias(String ir, String other, String[] text) {
    boolean ret = true;
    if (!ir.trim().equals((ircode == null ? null : ircode.trim()))) {
      ircode = ir;
      ret = false;
    }
    if (!other.trim().equals((otherAlias == null ? null : otherAlias.trim()))) {
      otherAlias = other;
      ret = false;
    }
    String s = "";
    if (text == null) {
      s = "";
    } else {
      for (String text1 : text) {
        s += text1 + "\n";
      }
    }
    if (!s.trim().equals((aliasText == null ? null : aliasText.trim()))) {
      aliasText = s;
      ret = false;
    }
    return ret;
  }

  public boolean setCookedResponse(PNZ pz, String t) {
    boolean ret = t.equals(cookedText == null ? "" : cookedText);
    if (!ret) {
      Util.prt("replace cooked response " + channel + "\n" + cookedText + "|\n" + t + "\n" + cookedResponse + "" + pz);
      cookedText = t;
      cookedResponse = pz;
    }
    return ret;
  }

  public void setLastData(long ms) {
    lastData.setTime(ms);
  }

  // Put in correct detail constructor here.  Use makeChannel() by filling in all
  // the rs.get???("fieldName") for all of the arguments
  /**
   * Create a instance of this table row from a positioned result set
   *
   * @param rs The ResultSet already SELECTED
   * @exception SQLException SQLException If the ResultSet is Improper, network is down, or database
   * is down
   */
  public Channel(ResultSet rs) throws SQLException {  //USER: add all data field here
    makeChannel(rs.getInt("ID"), rs.getString("channel"), rs.getInt("commgroupid"),
            rs.getInt("operatorid"), rs.getInt("protocolid"), rs.getLong("sendto"), rs.getLong("flags"),
            rs.getLong("links"), rs.getInt("delay"),
            rs.getString("sort1"), rs.getString("sort2"), rs.getDouble("rate"), rs.getInt("nsnnet"), rs.getInt("nsnnode"),
            rs.getInt("nsnchan"), rs.getInt("expected"), rs.getString("gaptype"),
            rs.getTimestamp("lastdata"), rs.getLong("mdsflags"), rs.getLong("hydraflags"), rs.getDouble("hydraValue"), rs.getTimestamp("created")
    // ,rs.getDouble(longitude)
    );

  }

  /**
   * Create a instance of this table row from a positioned result set
   *
   * @param rs The DBTable already SELECTED
   */
  public Channel(DBTable rs) {  //USER: add all data field here
    makeChannel(rs.getInt("ID"), rs.getString("channel"), rs.getInt("commgroupid"),
            rs.getInt("operatorid"), rs.getInt("protocolid"), rs.getLong("sendto"), rs.getLong("flags"),
            rs.getLong("links"), rs.getInt("delay"),
            rs.getString("sort1"), rs.getString("sort2"), rs.getDouble("rate"), rs.getInt("nsnnet"), rs.getInt("nsnnode"),
            rs.getInt("nsnchan"), rs.getInt("expected"), rs.getString("gaptype"),
            rs.getTimestamp("lastdata"), rs.getLong("mdsflags"), rs.getLong("hydraflags"), rs.getDouble("hydraValue"), rs.getTimestamp("created")
    // ,rs.getDouble(longitude)
    );

  }

  /**
   * reload an instance using the ResultSet
   *
   * @param rs The Result set
   * @throws java.sql.SQLException
   */
  public void reload(ResultSet rs) throws SQLException {
    makeChannel(rs.getInt("ID"), rs.getString("channel"), rs.getInt("commgroupid"),
            rs.getInt("operatorid"), rs.getInt("protocolid"), rs.getLong("sendto"), rs.getLong("flags"),
            rs.getLong("links"), rs.getInt("delay"),
            rs.getString("sort1"), rs.getString("sort2"), rs.getDouble("rate"), rs.getInt("nsnnet"), rs.getInt("nsnnode"),
            rs.getInt("nsnchan"), rs.getInt("expected"), rs.getString("gaptype"),
            rs.getTimestamp("lastdata"), rs.getLong("mdsflags"), rs.getLong("hydraflags"), rs.getDouble("hydraValue"), rs.getTimestamp("created")
    // ,rs.getDouble(longitude)
    );
  }

  /**
   * reload an instance using the DBTable
   *
   * @param rs The DBTable
   * @throws java.sql.SQLException
   */
  public void reload(DBTable rs) throws SQLException {
    makeChannel(rs.getInt("ID"), rs.getString("channel"), rs.getInt("commgroupid"),
            rs.getInt("operatorid"), rs.getInt("protocolid"), rs.getLong("sendto"), rs.getLong("flags"),
            rs.getLong("links"), rs.getInt("delay"),
            rs.getString("sort1"), rs.getString("sort2"), rs.getDouble("rate"), rs.getInt("nsnnet"), rs.getInt("nsnnode"),
            rs.getInt("nsnchan"), rs.getInt("expected"), rs.getString("gaptype"),
            rs.getTimestamp("lastdata"), rs.getLong("mdsflags"), rs.getLong("hydraflags"), rs.getDouble("hydraValue"), rs.getTimestamp("created")
    // ,rs.getDouble(longitude)
    );
  }

  // Detail Constructor, match set up with makeChannel(), this argument list should be
  // the same as for the result set builder as both use makeChannel()
  /**
   * create a row record from individual variables
   *
   * @param inID The ID in the database, normally zero for this type of construction
   * @param loc The key value for the database (same a name of database table)
   * @param commid The Communication group id
   * @param operid The operator ID
   * @param protid THe protocol ID
   * @param send The sendto mask
   * @param flgs The flags mask
   * @param lnks The links mask
   * @param del The delay interval
   * @param srt1 The sorting id
   * @param srt2 The secondary sorting id
   * @param rt The digitizing rate
   * @param net The VMS net id
   * @param node The VMS node ID
   * @param chan The VMS channel id
   * @param expect The expected flag
   * @param gapt The 2 letter gap type for rerequest processing
   * @param lastdat Last data field
   * @param mdsflags MDS flags
   * @param hydraflags Flags for Hydra
   * @param hydravalue The value of merit for Hydra
   * @param created created date
   *
   */
  public Channel(int inID, String loc, int commid, int operid, int protid, long send, long flgs,
          long lnks, int del,
          String srt1, String srt2, double rt, int net, int node, int chan, int expect, String gapt//USER: add fields, double lon
          ,
           Timestamp lastdat, long mdsflags, long hydraflags, double hydravalue, Timestamp created
  ) {
    makeChannel(inID, loc, commid, operid, protid, send, flgs, lnks, del, srt1, srt2, rt, net, node, chan, expect, gapt, //, lon
            lastdat, mdsflags, hydraflags, hydravalue, created
    );
    lastData = new Timestamp(System.currentTimeMillis());
    this.created = new Timestamp(created.getTime());
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
  private void makeChannel(int inID, String loc, int commid, int operid, int protid, long send, long flgs,
          long lnks, int del,
          String srt1, String srt2, double rt, int net, int node, int chan, int expect, String gtype, //USER: add fields, double lon
          Timestamp lastdat, long mdsflags, long hydraflags, double hydravalue, Timestamp creat
  ) {
    ID = inID;
    channel = loc;     // longitude = lon
    Util.clear(channelsb).append(channel);
    //USER:  Put asssignments to all fields from arguments here

    // ENUM example:
    //if(qtypeEnum == null) qtypeEnum=FUtil.getEnum(UC.getConnection(), "nsnstation","quanterraType");
    //qtype = FUtil.enumStringToInt(qtypeEnum, st); 
    // Boolean example where ENUM "True", "False
    // cont40 = FUtil.isTrue(c40);          // c40 is a string with "true" or false
    commgroupID = commid;
    operatorID = operid;
    protocolID = protid;
    sendto = send;
    flags = flgs;
    links = lnks;
    delay = del;
    sort1 = srt1;
    sort2 = srt2;
    nsnnet = net;
    nsnnode = node;
    nsnchan = chan;
    expected = expect;
    rate = rt;
    gapType = gtype;
    if (lastData == null) {
      lastData = new Timestamp(lastdat.getTime());
    } else {
      lastData.setTime(lastdat.getTime());
    }
    this.mdsflags = mdsflags;
    this.hydraflags = hydraflags;
    this.hydraValue = hydravalue;
    if (created == null) {
      created = new Timestamp(creat.getTime());
    } else {
      created.setTime(creat.getTime());
    }
  }

  public void updateDataShort(ResultSet rs) throws SQLException {
    commgroupID = rs.getInt("commgroupid");
    operatorID = rs.getInt("operatorid");
    protocolID = rs.getInt("protocolid");
    sendto = rs.getLong("sendto");
    flags = rs.getLong("flags");
    links = rs.getLong("links");
    rate = rs.getDouble("rate");
    expected = rs.getInt("expected");
    if (lastData == null) {
      lastData = new Timestamp(rs.getTimestamp("lastdata").getTime());
    } else {
      lastData.setTime(rs.getTimestamp("lastData").getTime());
    }
    if (created == null) {
      created = new Timestamp(rs.getTimestamp("created").getTime());
    } else {
      created.setTime(rs.getTimestamp("created").getTime());
    }
  }

  /**
   * update this instance of this table row from a positioned result set
   *
   * @param rs The ResultSet already SELECTED
   * @exception SQLException SQLException If the ResultSet is Improper, network is down, or database
   * is down
   */
  public void updateData(ResultSet rs) throws SQLException {  //USER: add all data field here
    makeChannel(rs.getInt("ID"), rs.getString("channel"), rs.getInt("commgroupid"),
            rs.getInt("operatorid"), rs.getInt("protocolid"), rs.getLong("sendto"), rs.getLong("flags"),
            rs.getLong("links"), rs.getInt("delay"),
            rs.getString("sort1"), rs.getString("sort2"), rs.getDouble("rate"), rs.getInt("nsnnet"), rs.getInt("nsnnode"),
            rs.getInt("nsnchan"), rs.getInt("expected"), rs.getString("gaptype"),
            rs.getTimestamp("lastdata"), rs.getLong("mdsflags"), rs.getLong("hydraflags"), rs.getDouble("hydraValue"), rs.getTimestamp("created")
    // ,rs.getDouble(longitude)
    );
    if (lastData == null) {
      lastData = new Timestamp(rs.getTimestamp("lastdata").getTime());
    } else {
      lastData.setTime(rs.getTimestamp("lastData").getTime());
    }
    if (created == null) {
      created = new Timestamp(rs.getTimestamp("created").getTime());
    } else {
      created.setTime(rs.getTimestamp("created").getTime());
    }
  }

  /**
   * update this instance of this table row from a positioned result set
   *
   * @param rs The DBTable already SELECTED
   */
  public void updateData(DBTable rs) {  //USER: add all data field here
    makeChannel(rs.getInt("ID"), rs.getString("channel"), rs.getInt("commgroupid"),
            rs.getInt("operatorid"), rs.getInt("protocolid"), rs.getLong("sendto"), rs.getLong("flags"),
            rs.getLong("links"), rs.getInt("delay"),
            rs.getString("sort1"), rs.getString("sort2"), rs.getDouble("rate"), rs.getInt("nsnnet"), rs.getInt("nsnnode"),
            rs.getInt("nsnchan"), rs.getInt("expected"), rs.getString("gaptype"),
            rs.getTimestamp("lastdata"), rs.getLong("mdsflags"), rs.getLong("hydraflags"), rs.getDouble("hydraValue"), rs.getTimestamp("created")
    // ,rs.getDouble(longitude)
    );
    if (lastData == null) {
      lastData = new Timestamp(rs.getTimestamp("lastdata").getTime());
    } else {
      lastData.setTime(rs.getTimestamp("lastData").getTime());
    }
    if (created == null) {
      created = new Timestamp(rs.getTimestamp("created").getTime());
    } else {
      created.setTime(rs.getTimestamp("created").getTime());
    }
  }

  /**
   * return the key name as the string
   *
   * @return the key name
   */
  public String toString2() {
    return channel + " exp=" + expected;
  }

  /**
   * return the key name as the string
   *
   * @return the key name
   */
  @Override
  public String toString() {
    return channel;
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
  public String getChannel() {
    return channel;
  }

  //USER:  All field getters here
  //  public double getLongitude() { return longitude;}
  public StringBuilder getChannelSB() {
    return channelsb;
  }

  public int getCommGroupID() {
    return commgroupID;
  }

  public int getOperatorID() {
    return operatorID;
  }

  public int getProtocolID() {
    return protocolID;
  }

  public long getSendtoMask() {
    return sendto;
  }

  public long getFlags() {
    return flags;
  }

  public long getLinksMask() {
    return links;
  }

  public int getDelay() {
    return delay;
  }

  public double getRate() {
    return rate;
  }

  public String getSort1() {
    return sort1;
  }

  public String getSort2() {
    return sort2;
  }

  public int getNsnNetwork() {
    return nsnnet;
  }

  public int getNsnNode() {
    return nsnnode;
  }

  public int getNsnChan() {
    return nsnchan;
  }

  public boolean getExpected() {
    return (expected != 0);
  }

  public String getGapType() {
    return gapType;
  }

  public Timestamp getLastData() {
    return lastData;
  }

  public long getMDSFlags() {
    return mdsflags;
  }

  public long getHydraFlags() {
    return hydraflags;
  }

  public double getHydraValue() {
    return hydraValue;
  }

  public Timestamp getCreated() {
    return created;
  }
  // This is needed if you need to implement Comparator
  /*  public class EquipCompare implements Comparator {
    public int compare(Object a, Object b) {
      return ((Channel) a).getChannel().compareTo(((Channel) b).getChannel());
    }
    public boolean equals(Object a, Object b) {
      if( ((Channel)a).getChannel().equals( ((Channel) b).getChannel())) return true;
      return false;
    }
//  }*/

}
