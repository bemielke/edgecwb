/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */

package gov.usgs.anss.guidb;
/*
 * Cpu.java
 * If the name of the class does not match the name of the field in the class you may
 * need to modify the rs.getSTring in the constructor.
 *
 * Created on July 8, 2002, 1:23 PM
 */

/**
 *
 * @author  ketchum
 * This Cpu templace for creating a database database object.  It is not
 * really needed by the CpuPanel which uses the DBObject system for
 * changing a record in the database file.  However, you have to have this
 * shell to create objects containing a database record for passing around
 * and manipulating.  There are two constructors normally given:
 * 1)  passed a result set positioned to the database record to decode
 * 2) Passed all of the data arguments needed to build the same set
 *     of data.  
 *
 * The Cpu should be replaced with capitalized version (class name) for the 
 * file.  cpu should be replaced with the lower case version (for variables).
 *
 * Both the constructors use the "makeCpu(list of data args) to set the
 * local variables to the value.  The result set just uses the 
 * rs.get???("fieldname") to get the data from the database where the other
 * passes the arguments from the caller.
 * 
 * Notes on Enums :
 ** data class should have code like :
 * import java.util.ArrayList;          /// Import for Enum manipulation
 *   .
 *  static ArrayList fieldEnum;         // This list will have Strings corresponding to enum
 *       .
 *       .
 *    // To convert an enum we need to create the fieldEnum ArrayList.  Do only once(static)
 *    if(fieldEnum == null) fieldEnum = FUtil.getEnum(UC.getConnection(),"table","fieldName");
 *    
 *   // Get the int corresponding to an Enum String for storage in the data base and 
 *   // the index into the JComboBox representing this Enum 
 *   localVariable = FUtil.enumStringToInt(fieldEnum, enumString);
 
 */


//import java.util.Comparator;
import gov.usgs.anss.db.DBConnectionThread;
import gov.usgs.anss.util.Util;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;

public class Cpu     //implements Comparator
{   // static ArrayList enumName;   // this need to be populated in makeCpu()
  public static ArrayList<Cpu> cpus;             // ArrayList containing objects of this Cpu Type
  
  /** Creates a new instance of Cpu */
  int ID;                   // This is the database ID (should alwas be named "ID"
  String cpu;             // This is the main key which should have same name
                            // as the table which it is a key to.  That is
                            // Table "category" should have key "category"
  
  // USER: All fields of file go here
  //  double longitude
  String dottedName;
  String os;
  public static final int ROLE_ANSS_DATA=16, ROLE_ANSS_MSHEAR=1,ROLE_ANSS_CONSOLE=2, ROLE_ANSS_GPS=4;
  public static final int ROLE_ANSS_RTS=8;
  int roles;

  
  // Put in correct detail constructor here.  Use makeCpu() by filling in all
  // the rs.get???("fieldName") for all of the arguments
  public Cpu(ResultSet rs) throws SQLException {
    makeCpu(rs.getInt("ID"), rs.getString("cpu"),
                            // ,rs.getDouble(longitude)
     rs.getString("dotted_name"), rs.getString("os"),
    rs.getInt("roles")
    );
  }
  
  // Detail Constructor, match set up with makeCpu(), this argument list should be
  // the same as for the result set builder as both use makeCpu()
  public Cpu(int inID, String loc   //, double lon
      ,String dotted, String osin, int roles
    ) {
    makeCpu(inID, loc ,     //, lon
     dotted, osin, roles
    );
  }
  
  // This routine transfers to the local variables of this object all of the data variables
  // associated with this file.
  private void makeCpu(int inID, String loc ,   //, double lon
    String dotted, String osin, int r
  ) {
    ID = inID;  cpu=loc;     // longitude = lon
    // Put asssignments to all fields from arguments here
    //if(qtypeEnum == null) qtypeEnum=FUtil.getEnum(UC.getConnection(), "nsnstation","quanterraType");
    //qtype = FUtil.enumStringToInt(qtypeEnum, st); 
    // cont40 = FUtil.isTrue(c40);          // c40 is a string with "true" or false
    dottedName=dotted; os=osin; roles=r;

    
  }
  public String toString2() { return cpu;}
  public String toString() { return cpu;}
  // getter
  
  // standard getters
  public int getID() {return ID;}
  public String getCpu() {return cpu;}
  
  
  // All field getters here
  //  public double getLongitude() { return longitude;}
  public String getDottedName() { return dottedName;}
  public String getOS() { return os;}
  public int getRoles() { return roles;}
  public boolean isConsoleAllowed() {return (roles & ROLE_ANSS_CONSOLE) != 0;}
  public boolean isRTSAllowed() {return (roles & ROLE_ANSS_RTS) != 0;}
  public boolean isGPSAllowed() {return (roles & ROLE_ANSS_GPS) != 0;}
  public boolean isDataAllowed() {return (roles & ROLE_ANSS_DATA) != 0;}
  public boolean isMShearAllowed() {return (roles & ROLE_ANSS_MSHEAR) != 0;}
  
  
  // This is needed if you need to implement Comparator
  /*  public class EquipCompare implements Comparator {
    public int compare(Object a, Object b) {
      return ((Cpu) a).getCpu().compareTo(((Cpu) b).getCpu());
    }
    public boolean equals(Object a, Object b) {
      if( ((Cpu)a).getCpu().equals( ((Cpu) b).getCpu())) return true;
      return false;
    }
//  }*/
  
  public Cpu getCpuWithID( int ID) {
    makeCpus();
    for(Cpu cpu: cpus) {
      if(cpu.getID() == ID) return cpu;
    }
    return null;
  }
  public static int getIDForCpu(String cpu) {
    makeCpus();
    for(Cpu cpu1 : cpus) {  
      if(cpu1.getCpu().equalsIgnoreCase(cpu)) return cpu1.getID();
    }
    return 0;
  }
  // This routine should only need tweeking if key field is not same as table name
  public static void makeCpus() {
    if (cpus != null) return;
    cpus=new ArrayList<Cpu>(100);
    try {
      try (Statement stmt = DBConnectionThread.getConnection(DBConnectionThread.getDBSchema()).createStatement() // used for query
      ) {
        String s = "SELECT * FROM anss.cpu ORDER BY cpu";
        try (ResultSet rs = stmt.executeQuery(s)) {
          while (rs.next()) {
            Cpu loc = new Cpu(rs);
            //Util.prt("MakeCpu() i="+v.size()+" is "+loc.getCpu());
            cpus.add(loc);
          }
        }
      }
    }
    catch (SQLException e) {
      Util.SQLErrorPrint(e,"makeCpus() on table SQL failed");

    }    
  }  
}
