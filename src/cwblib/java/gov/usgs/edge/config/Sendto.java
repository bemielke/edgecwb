/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.edge.config;

import gov.usgs.anss.dbtable.DBTable;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Sendto.java - this wraps a single record from the mysql edge.sendto table. Its schema looks like
 * :
 * <PRE>
 * ID              | int
 * sendto          | char(20)        The name of this sendto, this will be the filenames for RingFiles
 * mask            | int             This is (1 leftshift ID-1), the mask of this sendto in edge.channel
 * description     | char(50)        User description of the purpose of this sendto
 * hasdata         | int             Not currently used, meant to flag external data is needed
 * filesize        | int             Size of ring file in mB, not used for some other output classes
 * filepath        | char(40)        The path where the ring will be created
 * updatems        | int             How often the first block is to be updated with last seq written
 * class           | varchar(100)    If empty, use RingFile, else name of the output class
 * args            | varchar(100)    Arguments to send to output class when it is created
 * allowrestricted | int             If not zero, this output method can send channels marked as restricted
 * nodes           | char(60)        A list of nodes or Regular expressions where this sendto is active
 * </PRE>
 *
 * @author D.C. Ketchum
 */
public final class Sendto {   //implements Comparator
  // static ArrayList enumName;   // this need to be populated in makeSendto()

  /**
   * Creates a new instance of Sendto
   */
  private int ID;                   // This is the database ID (should alwas be named "ID"
  private String sendto;             // This is the main key which should have same name
  // as the table which it is a key to.  That is
  // Table "category" should have key "category"

  // All fields of file go here
  //  double longitude
  private String description;
  private int hasdata;
  private int allowRestricted;      // if not zero, allow restricted channels
  private int filesize;             // size of output file in MB, if 0, then look to class info
  private String filepath;          // path to the file
  private String className;            // THe class name if not a Ring file
  private Class outputerClass;      // The outputer class if not a RingFile
  private int updateMS;             // Update interval for class
  private String nodes;
  private String args;              // arguments for the outputer class
  private String autoset;

  // Put in correct detail constructor here.  Use makeSendto() by filling in all
  // the rs.get???("fieldName") for all of the arguments
  /**
   * Create a instance of this table row from a positioned result set
   *
   * @param rs The ResultSet already SELECTED
   * @exception SQLException SQLException If the ResultSet is Improper, network is down, or database
   * is down
   */
  public Sendto(ResultSet rs) throws SQLException {
    makeSendto(rs.getInt("ID"), rs.getString("sendto"), rs.getString("description"),
            rs.getInt("hasdata"), rs.getInt("filesize"), rs.getString("filepath"),
            rs.getInt("updatems"), rs.getString("class"), rs.getString("args"),
            rs.getInt("allowrestricted"), rs.getString("nodes"),
            rs.getString("autoset")
    // ,rs.getDouble(longitude)
    );
  }

  /**
   * Create a instance of this table row from a positioned result set
   *
   * @param rs The ResultSet already SELECTED
   */
  public Sendto(DBTable rs) {
    makeSendto(rs.getInt("ID"), rs.getString("sendto"), rs.getString("description"),
            rs.getInt("hasdata"), rs.getInt("filesize"), rs.getString("filepath"),
            rs.getInt("updatems"), rs.getString("class"), rs.getString("args"),
            rs.getInt("allowrestricted"), rs.getString("nodes"),
            rs.getString("autoset")
    // ,rs.getDouble(longitude)
    );
  }

  /**
   * Reload an instance of this table row from a positioned DBTable
   *
   * @param rs The DBTable already SELECTED
   * @throws java.sql.SQLException
   */
  public void reload(DBTable rs) throws SQLException {
    makeSendto(rs.getInt("ID"), rs.getString("sendto"), rs.getString("description"),
            rs.getInt("hasdata"), rs.getInt("filesize"), rs.getString("filepath"),
            rs.getInt("updatems"), rs.getString("class"), rs.getString("args"),
            rs.getInt("allowrestricted"), rs.getString("nodes"),
            rs.getString("autoset")
    // ,rs.getDouble(longitude)
    );
  }

  /**
   * Reload an instance of this table row from a positioned result set
   *
   * @param rs The ResultSet already SELECTED
   * @throws java.sql.SQLException
   */
  public void reload(ResultSet rs) throws SQLException {
    makeSendto(rs.getInt("ID"), rs.getString("sendto"), rs.getString("description"),
            rs.getInt("hasdata"), rs.getInt("filesize"), rs.getString("filepath"),
            rs.getInt("updatems"), rs.getString("class"), rs.getString("args"),
            rs.getInt("allowrestricted"), rs.getString("nodes"),
            rs.getString("autoset")
    // ,rs.getDouble(longitude)
    );
  }

  // Detail Constructor, match set up with makeSendto(), this argument list should be
  // the same as for the result set builder as both use makeSendto()
  /**
   * create a row record from individual variables
   *
   * @param inID The ID in the database, normally zero for this type of construction
   * @param loc The key value for the database (same a name of database table)
   * @param desc The description of this sendto target
   * @param has If true, the side table contains data for this sendto
   * @param sizeMB Size of the ringfile in MB
   * @param path The path to the ring file
   * @param updatems Millis between updates of the status file (filename of ring is this path /
   * sendto name)
   * @param cl The class to use for this Sendto (currently not used)
   * @param arg The arguments to pass to the output
   * @param allow Allow restricted channels to this source
   * @param nodes A regular expression for all nodes on which this type of send to ring should be
   * active
   * @param auto A regular expression for all channels which should have this set on creation
   */
  public Sendto(int inID, String loc, String desc, int has, int sizeMB, String path,
          int updatems, String cl, String arg, int allow, String nodes, String auto//, double lon
  ) {
    makeSendto(inID, loc, desc, has, sizeMB, path, updatems, cl, arg, allow, nodes, auto //, lon
    );
  }

  /**
   * give a results set update this sendto with the data
   *
   * @param rs The result set to use for the update
   * @throws SQLException if one occurs on the positioned result set
   */
  public void updateData(ResultSet rs) throws SQLException {
    makeSendto(rs.getInt("ID"), rs.getString("sendto"), rs.getString("description"),
            rs.getInt("hasdata"), rs.getInt("filesize"), rs.getString("filepath"),
            rs.getInt("updatems"), rs.getString("class"), rs.getString("args"), rs.getInt("allowrestricted"),
             rs.getString("nodes"), rs.getString("autoset")
    // ,rs.getDouble(longitude)
    );
  }

  /**
   * give a results set update this sendto with the data
   *
   * @param rs The result set to use for the update
   */
  public void updateData(DBTable rs) {
    makeSendto(rs.getInt("ID"), rs.getString("sendto"), rs.getString("description"),
            rs.getInt("hasdata"), rs.getInt("filesize"), rs.getString("filepath"),
            rs.getInt("updatems"), rs.getString("class"), rs.getString("args"), rs.getInt("allowrestricted"),
             rs.getString("nodes"), rs.getString("autoset")
    // ,rs.getDouble(longitude)
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
  private void makeSendto(int inID, String loc, String desc, int has, int sizeMB, String path,
          int updms, String cl, String arg, int allow, String nds, String auto//, double lon
  ) {
    ID = inID;
    sendto = loc.trim();     // longitude = lon
    // Put asssignments to all fields from arguments here
    //if(qtypeEnum == null) qtypeEnum=FUtil.getEnum(DBConnectionThread.getConnection("edge"), "nsnstation","quanterraType");
    //qtype = FUtil.enumStringToInt(qtypeEnum, st); 
    // cont40 = FUtil.isTrue(c40);          // c40 is a string with "true" or false
    description = desc.trim();
    hasdata = has;
    filesize = sizeMB;
    filepath = path.trim();
    updateMS = updms;
    className = cl.trim();
    args = arg.trim();
    allowRestricted = allow;
    nodes = nds.trim();
    autoset = auto.trim();
    if (!className.equals("")) {
      try {
        outputerClass = Class.forName(className);
      } catch (ClassNotFoundException | LinkageError e) {
        outputerClass = null;
      }
    }

  }

  public boolean matchNode(String node) {

    String[] res = nodes.trim().split(",");
    //Util.prt(sendto +"Sendto matchNode() node="+node+"| to "+nodes+"|");
    for (String re : res) {
      if (node.matches(re)) {
        return true;
      }
    }
    return false;
  }

  /**
   * return the key name as the string
   *
   * @return the key name
   */
  public String toString2() {
    return sendto;
  }

  /**
   * return the key name as the string
   *
   * @return the key name
   */
  @Override
  public String toString() {
    return sendto;
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
  public String getSendto() {
    return sendto;
  }

  public String getNodes() {
    return nodes;
  }

  public String getDescription() {
    return description;
  }

  public boolean hasData() {
    return (hasdata != 0);
  }

  public boolean allowRestricted() {
    return (allowRestricted != 0);
  }

  /**
   * return the file size in MB
   *
   * @return the filesize in MB
   */
  public int getFilesize() {
    return filesize;
  }

  /**
   * return the file size in MB
   *
   * @return the filesize in MB
   */
  public String getFilepath() {
    return filepath;
  }

  public long getMask() {
    return 1L << (ID - 1);
  }

  public int getUpdateMS() {
    return updateMS;
  }

  public Class getOutputerClass() {
    return outputerClass;
  }

  public String getOutputerClassName() {
    return className;
  }

  public String getArgs() {
    return args;
  }

  public String getAutoSet() {
    return autoset;
  }

  public long getAutoSetMask(String channel) {
    if (autoset.equals("")) {
      return 0;
    }
    if (channel.matches(autoset)) {
      return 1L << (ID - 1);
    }
    return 0;
  }
  // All field getters here
  //  public double getLongitude() { return longitude;}

  // This is needed if you need to implement Comparator
  /*  public class EquipCompare implements Comparator {
    public int compare(Object a, Object b) {
      return ((Sendto) a).getSendto().compareTo(((Sendto) b).getSendto());
    }
    public boolean equals(Object a, Object b) {
      if( ((Sendto)a).getSendto().equals( ((Sendto) b).getSendto())) return true;
      return false;
    }
//  }*/
}
