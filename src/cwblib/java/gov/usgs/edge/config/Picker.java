/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.edge.config;

import gov.usgs.anss.db.DBConnectionThread;
import gov.usgs.anss.util.Util;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/**
 * Picker.java This encapsulates one row of the picker DB table. If the name of the class does not
 * match the name of the field in the class you may need to modify the rs.getSTring in the
 * constructor.
 *
 * This Picker template for creating a database database object. It is not really needed by the
 * PickerPanel which uses the DBObject system for changing a record in the database file. However,
 * you have to have this shell to create objects containing a database record for passing around and
 * manipulating. There are two constructors normally given:
 * <br>
 * 1) passed a result set positioned to the database record to decode
 * <br> 2) Passed all of the data arguments needed to build the same set of data.
 * <br>
 * The Picker should be replaced with capitalized version (class name) for the file. picker should
 * be replaced with the lower case version (for variables).
 *
 * Both the constructors use the "makePicker(list of data args) to set the local variables to the
 * value. The result set just uses the rs.get???("fieldname") to get the data from the database
 * where the other passes the arguments from the caller.
 *
 * <br> Notes on Enums :
 * <br>
 * data class should have code like :
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
public final class Picker {   //implements Comparator
  // static ArrayList enumName;   // this need to be populated in makePicker()

  public static ArrayList<Picker> pickers;

  /**
   * Creates a new instance of Picker
   */
  private int ID;                   // This is the database ID (should alwas be named "ID"
  private String picker;             // This is the main key which should have same name
  // as the table which it is a key to.  That is
  // Table "category" should have key "category"

  // USER: All fields of file go here
  //  double longitude
  private String classname, args, template;
  
  //A lightweight list of channels associated with this picker. That is loaded
  //only on demand.
  private HashSet<String>channels = null;

  // Put in correct detail constructor here.  Use makePicker() by filling in all
  // the rs.get???("fieldName") for all of the arguments
  /**
   * Create a instance of this table row from a positioned result set
   *
   * @param rs The ResultSet already SELECTED
   * @exception SQLException SQLException If the ResultSet is Improper, network is down, or database
   * is down
   */
  public Picker(ResultSet rs) throws SQLException {  //USER: add all data field here
    reload(rs);
  }

  /**
   * reload the object data from a given result set
   *
   * @param rs The ResultSet to load the object from
   * @throws SQLException If one is thrown
   */
  public final void reload(ResultSet rs) throws SQLException {
    makePicker(rs.getInt("ID"), rs.getString("picker"),
            rs.getString("classname"), rs.getString("args"), rs.getString("template")
    // ,rs.getDouble(longitude)
    );
  }

  // Detail Constructor, match set up with makePicker(), this argument list should be
  // the same as for the result set builder as both use makePicker()
  /**
   * create a row record from individual variables
   *
   * @param inID The ID in the database, normally zero for this type of construction
   * @param loc The key value for the database (same
   * @param cl a name of database table)
   * @param args Arguments if this type of configuration is to be done, else blank for template
   * config
   * @param template Template key-values, args must be blank if template config is being used.
   */
  public Picker(int inID, String loc //USER: add fields, double lon
          ,
           String cl, String args, String template
  ) {
    makePicker(inID, loc, cl, args, template //, lon
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
  private void makePicker(int inID, String loc //USER: add fields, double lon
          ,
           String cl, String args, String template
  ) {
    ID = inID;
    picker = loc;     // longitude = lon
    //USER:  Put asssignments to all fields from arguments here

    // ENUM example:
    //if(qtypeEnum == null) qtypeEnum=FUtil.getEnum(JDBConnection.getConnection("DATABASE"), "nsnstation","quanterraType");
    //qtype = FUtil.enumStringToInt(qtypeEnum, st); 
    // Boolean example where ENUM "True", "False
    // cont40 = FUtil.isTrue(c40);          // c40 is a string with "true" or false
    classname = cl;
    this.args = args;
    this.template = template;

  }

  /**
   * return the key name as the string
   *
   * @return the key name
   */
  public String toString2() {
    return picker;
  }

  /**
   * return the key name as the string
   *
   * @return the key name
   */
  @Override
  public String toString() {
    return picker;
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
  public String getPicker() {
    return picker;
  }

  //USER:  All field getters here
  //  public double getLongitude() { return longitude;}
  public String getClassname() {
    return classname;
  }

  public String getArgs() {
    return args;
  }

  public String getTemplate() {
    return template;
  }
  
  private void clearPickerChannels() {
    channels.clear();
  }
  
  private void addPickerChannel(String channelName) {
    channels.add(channelName);
  }

  // This is needed if you need to implement Comparator
  /*  public class EquipCompare implements Comparator {
    public int compare(Object a, Object b) {
      return ((Picker) a).getPicker().compareTo(((Picker) b).getPicker());
    }
    public boolean equals(Object a, Object b) {
      if( ((Picker)a).getPicker().equals( ((Picker) b).getPicker())) return true;
      return false;
    }
//  }*/
  /**
   * Get the item corresponding to database ID
   *
   * @param ID the database Row ID
   * @return The Picker row with this ID
   */
  public static Picker getPickerWithID(int ID) {
    if (pickers == null) {
      makePickers();
    }
    for (Picker picker : pickers) {
      if (picker.getID() == ID) {
        return picker;
      }
    }
    return null;
  }
  
  public static List<Picker> getPickersForChannel(String channelName) {
    List<Picker> channelPickers = new ArrayList<>();
    for (Picker picker : pickers) {
      if (picker.hasChannel(channelName)) {
        channelPickers.add(picker);
      }
    }
    return channelPickers;
  }
  
  private boolean hasChannel(String channelName) {
    if (channels != null) {
      return channels.contains(channelName.trim());
    } else {
      return false;
    }
  }
  /**
   * This method loops the list of already loaded pickers and loads the
   * channel name for each channel associated with each picker and
   * stores that information for later use
   */
  public static void loadChannelsForAllPickers() {
    for (Picker picker : pickers) {
      picker.loadChannelsForPicker();
    }
  }
  
  public void loadChannelsForPicker() {
    if (channels == null) {
      channels = new HashSet<>();
    }
    try {
      Statement stmt;
      if (DBConnectionThread.getConnection(DBConnectionThread.getDBSchema()) != null) {
        stmt = DBConnectionThread.getConnection(DBConnectionThread.getDBSchema()).createStatement();   // used for query
      } else {
        stmt = DBConnectionThread.getConnection("DBServer").createStatement();    // This is for call in the PickManager in QueryMom
      }
      String sql = "select ch.channel channelName from " + DBConnectionThread.getDBSchema() + ".pickerchannels pc, " +
              DBConnectionThread.getDBSchema() + ".channel ch where pc.channelID = ch.ID and pc.pickerID = " +
              this.getID() + ";";
      try (ResultSet rs = stmt.executeQuery(sql)) {
        this.clearPickerChannels();
        while (rs.next()) {
          this.addPickerChannel(rs.getString("channelName").trim());
        }
      }
      stmt.close();
    } catch (SQLException e) {
      Util.SQLErrorPrint(e, "loadChannelsForPicker on table SQL failed.");
    }
  }

  // This routine should only need tweeking if key field is not same as table name
  public static void makePickers() {
    try {
      Statement stmt;
      if (DBConnectionThread.getConnection(DBConnectionThread.getDBSchema()) != null) {
        stmt = DBConnectionThread.getConnection(DBConnectionThread.getDBSchema()).createStatement();   // used for query
      } else {
        stmt = DBConnectionThread.getConnection("DBServer").createStatement();    // This is for call in the PickManager in QueryMom
      }
      makePickers(stmt);
    } catch (SQLException e) {
      Util.SQLErrorPrint(e, "makePickers() on table SQL failed");
    }
  }

  public static void makePickers(Statement stmt) {
    if (pickers != null) {
      return;
    }
    pickers = new ArrayList<Picker>(100);
    try {
      String s = "SELECT * FROM " + DBConnectionThread.getDBSchema() + ".picker ORDER BY picker;";
      try (ResultSet rs = stmt.executeQuery(s)) {
        while (rs.next()) {
          Picker loc = new Picker(rs);
          //        Util.prt("MakePicker() i="+pickers.size()+" is "+loc.getPicker());
          pickers.add(loc);
        }
      }
      stmt.close();
    } catch (SQLException e) {
      Util.SQLErrorPrint(e, "makePickers() on table SQL failed");
      e.printStackTrace();
    }
  }
}
