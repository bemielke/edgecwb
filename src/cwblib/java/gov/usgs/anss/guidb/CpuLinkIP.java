/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */

package gov.usgs.anss.guidb;
/*
 * CpuLinkIP.java
 * If the name of the class does not match the name of the field in the class you may
 * need to modify the rs.getSTring in the constructor.
 *
 * Created on July 8, 2002, 1:23 PM
 */

/**
 *
 * @author  ketchum
 * This CpuLinkIP template for creating a database database object.  It is not
 * really needed by the CpuLinkIPPanel which uses the DBObject system for
 * changing a record in the database file.  However, you have to have this
 * shell to create objects containing a database record for passing around
 * and manipulating.  There are two constructors normally given:
 * 1)  passed a result set positioned to the database record to decode
 * 2) Passed all of the data arguments needed to build the same set
 *     of data.  
 *
 * The CpuLinkIP should be replaced with capitalized version (class name) for the 
 * file.  cpuLinkIP should be replaced with the lower case version (for variables).
 *
 * Both the constructors use the "makeCpuLinkIP(list of data args) to set the
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

public class CpuLinkIP     //implements Comparator
{   // static ArrayList enumName;   // this need to be populated in makeCpuLinkIP()
  public static ArrayList<CpuLinkIP> v;             // Vector containing objects of this CpuLinkIP Type
  
  /** Creates a new instance of CpuLinkIP */
  int ID;                   // This is the database ID (should alwas be named "ID"
  String cpuLinkIP;  
  // All fields of file go here
  //  double longitude
  int cpuID;
  int commlinkID;
  String ipadr;

  
  // Put in correct detail constructor here.  Use makeCpuLinkIP() by filling in all
  // the rs.get???("fieldName") for all of the arguments
  public CpuLinkIP(ResultSet rs) throws SQLException {
    makeCpuLinkIP(rs.getInt("ID"),rs.getString("cpulinkip"), 
     rs.getInt("cpuID"), rs.getInt("commlinkID"), rs.getString("ipadr")                      // ,rs.getDouble(longitude)
    );
  }
  
  // Detail Constructor, match set up with makeCpuLinkIP(), this argument list should be
  // the same as for the result set builder as both use makeCpuLinkIP()
  public CpuLinkIP(int inID,  String loc,//, double lon
      int cpu, int comm, String ip
    ) {
    makeCpuLinkIP(inID,loc,     //, lon
      cpu, comm, ip
    );
  }
  
  // This routine transfers to the local variables of this object all of the data variables
  // associated with this file.
  private void makeCpuLinkIP(int inID, String loc,  //, double lon
    int cpu, int comm, String ip
  ) {
    ID = inID;      // longitude = lon
    cpuLinkIP=loc;
    // Put asssignments to all fields from arguments here
    //if(qtypeEnum == null) qtypeEnum=FUtil.getEnum(UC.getConnection(), "nsnstation","quanterraType");
    //qtype = FUtil.enumStringToInt(qtypeEnum, st); 
    // cont40 = FUtil.isTrue(c40);          // c40 is a string with "true" or false
    cpuID=cpu; commlinkID=comm; ipadr=ip;

    
  }
  @Override
  public String toString() { return cpuLinkIP;}
  // getter
  
  // standard getters
  public int getID() {return ID;}
  public int getCpuID() {return cpuID;}
  public String getCpuLinkIP() {return cpuLinkIP;}
  public int getCommLinkID(){return commlinkID;}
  public String getIP() {return ipadr;}

  
  
  // All field getters here
  //  public double getLongitude() { return longitude;}
  
  
  // This is needed if you need to implement Comparator
  /*  public class EquipCompare implements Comparator {
    public int compare(Object a, Object b) {
      return ((CpuLinkIP) a).getCpuLinkIP().compareTo(((CpuLinkIP) b).getCpuLinkIP());
    }
    public boolean equals(Object a, Object b) {
      if( ((CpuLinkIP)a).getCpuLinkIP().equals( ((CpuLinkIP) b).getCpuLinkIP())) return true;
      return false;
    }
//  }*/
  public static CpuLinkIP getCpuLinkIPfromIDs(int cpu, int commlink) {
    makeCpuLinkIPs();
    for(int i=0; i<CpuLinkIP.v.size(); i++) {
      CpuLinkIP c = CpuLinkIP.v.get(i);
      if(c.getCpuID() == cpu && c.getCommLinkID() == commlink) return c;
    }
    return null;
  }
  public static String getIPfromIDs(int cpu, int commlink) {
    CpuLinkIP l = getCpuLinkIPfromIDs(cpu,commlink);

    if(l != null) {
      Util.prta("CpuLinkIPPanel: getIPfromIDs() cpuID="+cpu+" comlinkID="+commlink+" Returned="+l);
      return l.getIP();
    }
    Util.prta("CpuLinkIPPanel: getIPfromIDs() cpuID="+cpu+" comlinkID="+commlink+" Returned=NULL");
    return "000.000.000.000";
  }
  // This routine should only need tweeking if key field is not same as table name
  public static void makeCpuLinkIPs() {
    if (v != null) return;
    v=new ArrayList<CpuLinkIP>(100);
    try {
      Statement stmt = DBConnectionThread.getConnection(DBConnectionThread.getDBSchema()).createStatement();   // used for query
      String s = "SELECT * FROM anss.cpulinkip ORDER BY cpuLinkIP";
      ResultSet rs = stmt.executeQuery(s);
      while (rs.next()) {
        CpuLinkIP loc = new CpuLinkIP(rs);
//        Util.prt("MakeCpuLinkIP() i="+v.size()+" is "+loc.getCpuLinkIP());
        v.add(loc);
      }
      rs.close();
      stmt.close();
    }
    catch (SQLException e) {
      Util.SQLErrorPrint(e,"makeCpuLinkIPs() on table SQL failed");
     
    }    
  }  
}
