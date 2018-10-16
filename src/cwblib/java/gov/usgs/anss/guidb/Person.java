/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.guidb;
/*
 * Person.java
 * If the name of the class does not match the name of the field in the class you may
 * need to modify the rs.getSTring in the constructor.   This descended from the Inv
 * person class but we removed all phone numbers, etc for PII reasons.
 *
 * Created on July 8, 2002, 1:23 PM
 */

/**
 *
 * @author  ketchum
 */


//import java.util.Comparator;
import java.sql.ResultSet;
import java.sql.SQLException;

public class Person     //implements Comparator
{
  
  /** Creates a new instance of Person */
  int ID;
  String Person;
  
  // All fields of file go here
  //  double longitude
  String master;
  String password;
  String role;
  String name;
  
  
  
  // Put in correct detail constructor here
  public Person(ResultSet rs) throws SQLException {
    makePerson(rs.getInt("ID"), rs.getString("Person"), rs.getString("name")
        // ,rs.getDouble(longitude)
    );
    master = rs.getString("master"); role= rs.getString("role"); 
    password=rs.getString("password");
    
  }
  
  // Detail Constructor, match set up
  public Person(int inID, String loc,String nm, String pH, String pW,
    String pC, String pF, String pP, String em    //, double lon
  ) {
    makePerson(inID, loc, nm        //, lon
    );
  }
  public final void makePerson(int inID, String loc, String nm    //, double lon
  ) {
    ID = inID;  Person=loc;     // longitude = lon
    
    // Put asssignments to all fields from arguments here
    name = nm; 
    
    
  }
  public String toString2() { return Person;}
  @Override
  public String toString() { return Person;}
  // getter
  
  // standard getters
  public int getID() {return ID;}
  public String getPerson() {return Person;}
  
  
  // All field getters here
  //  public double getLongitude() { return longitude;}
  public String getName() {return name;}
  public String getMaster() {return master;}
  public String getRole() {return role;}
  
  
  // This is needed if you need to implement Comparator
  /*  public class EquipCompare implements Comparator {
    public int compare(Object a, Object b) {
      return ((Person) a).getPerson().compareTo(((Person) b).getPerson());
    }
    public boolean equals(Object a, Object b) {
      if( ((Person)a).getPerson().equals( ((Person) b).getPerson())) return true;
      return false;
    }
//  }*/
  
}
