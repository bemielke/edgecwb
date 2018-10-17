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

/**
 * This class encapsulates one row of the EdgeThread table form the DB. If the name of the class
 * does not match the name of the field in the class you may need to modify the rs.getSTring in the
 * constructor.
 * <p>
 * This Edgethread template for creating a database database object. It is not really needed by the
 * HelpPanel which uses the DBObject system for changing a record in the database file. However, you
 * have to have this shell to create objects containing a database record for passing around and
 * manipulating. There are two constructors normally given:
 * <br>
 * 1) passed a result set positioned to the database record to decode
 * <br> 2) Passed all of the data arguments needed to build the same set of data.
 * <br>
 * The Edgethread should be replaced with capitalized version (class name) for the file. classname
 * should be replaced with the lower case version (for variables).
 *
 * Both the constructors use the "makeHelp(list of data args) to set the local variables to the
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
 *
 * @author D.C. Ketchum
 */
public final class Edgethread {    //implements Comparator
  // static ArrayList enumName;   // this need to be populated in makeHelp()

  public static ArrayList<Edgethread> edgetThreads;             // Vector containing objects of this Edgethread Type

  /**
   * Creates a new instance of Edgethread
   */
  private int ID;                   // This is the database ID (should alwas be named "ID"
  private String classname;             // This is the main key which should have same name
  // as the table which it is a key to.  That is
  // Table "category" should have key "category"

  // USER: All fields of file go here
  //  double longitude
  private int onlyone;
  private String help;
  private String javapack;
  private int edgemom;
  private int querymom;

  // Put in correct detail constructor here.  Use makeHelp() by filling in all
  // the rs.get???("fieldName") for all of the arguments
  /**
   * Create a instance of this table row from a positioned result set
   *
   * @param rs The ResultSet already SELECTED
   * @exception SQLException SQLException If the ResultSet is Improper, network is down, or database
   * is down
   */
  public Edgethread(ResultSet rs) throws SQLException {  //USER: add all data field here
    makeEdgethread(rs.getInt("ID"), rs.getString("classname"), rs.getInt("onlyone"), rs.getString("help"),
            rs.getInt("edgemom"), rs.getInt("querymom"), rs.getString("package")
    // ,rs.getDouble(longitude)
    );
  }

  // Detail Constructor, match set up with makeHelp(), this argument list should be
  // the same as for the result set builder as both use makeHelp()
  /**
   * create a row record from individual variables
   *
   * @param inID The ID in the database, normally zero for this type of construction
   * @param loc The key value for the database (same a name of database table)
   * @param inonlyone
   * @param indescription
   * @param inhelp Javadoc string
   * @param edge edgemom type thread
   * @param query querymom type thread
   * @param pack java package
   */
  public Edgethread(int inID, String loc, int inonlyone, String indescription,
          String inhelp, int edge, int query, String pack //USER: add fields, double lon
  ) {
    makeEdgethread(inID, loc, inonlyone, indescription, edge, query, pack //, lon
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
  private void makeEdgethread(int inID, String loc, int inonlyone, String inhelp, int edge, int query, String pack //USER: add fields, double lon
  ) {
    ID = inID;
    classname = loc;
    onlyone = inonlyone;
    help = inhelp;
    edgemom = edge;
    querymom = query;
    javapack = pack;  // longitude = lon
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
    return classname;
  }

  /**
   * return the key name as the string
   *
   * @return the key name
   */
  @Override
  public String toString() {
    return classname + (edgemom != 0 ? "-EdgeMom" : "") + (querymom != 0 ? "-QueryMom" : "");
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
  public String getEdgethread() {
    return classname;
  }

  public String getHelp() {
    return help;
  }

  public String getJavaPackage() {
    return javapack;
  }

  public int getEdgeMom() {
    return edgemom;
  }

  public int getQueryMom() {
    return querymom;
  }

  public int getOnlyOne() {
    return onlyone;
  }

  //USER:  All field getters here
  //  public double getLongitude() { return longitude;}
  // This is needed if you need to implement Comparator
  /*  public class EquipCompare implements Comparator {
    public int compare(Object a, Object b) {
      return ((Edgethread) a).getHelp().compareTo(((Edgethread) b).getHelp());
    }
    public boolean equals(Object a, Object b) {
      if( ((Edgethread)a).getHelp().equals( ((Edgethread) b).getHelp())) return true;
      return false;
    }
//  }*/
  public static Edgethread getEdgethreadWithID(int ID) {
    makeEdgethreads();
    for (Edgethread thr : edgetThreads) {
      if (thr.getID() == ID) {
        return thr;
      }
    }
    return null;
  }

  // This routine should only need tweeking if key field is not same as table name
  public static void makeEdgethreads() {
    if (edgetThreads != null) {
      return;
    }
    edgetThreads = new ArrayList<>(100);
    try {
      //Util.prt("EdgeThreadPanel makeEdgeThreads DBC.getDBSchema="+DBConnectionThread.getDBSchema());
      try (Statement stmt = DBConnectionThread.getConnection(DBConnectionThread.getDBSchema()).createStatement() // used for query
              ) {
        String s = "SELECT * FROM edge.edgethread ORDER BY classname;";
        try (ResultSet rs = stmt.executeQuery(s)) {
          while (rs.next()) {
            Edgethread loc = new Edgethread(rs);
//        Util.prt("MakeEdgethread() i="+edgetThreads.size()+" is "+loc.getEdgethread());
            edgetThreads.add(loc);
          }
        }
      }
    } catch (SQLException e) {
      Util.SQLErrorPrint(e, "makeEdgethreads() on table SQL failed");
    }
  }
}
