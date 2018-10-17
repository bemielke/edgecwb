/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.util;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import jcexceptions.JCJBLBadPassword;

/**
 * This class encapsulate the notion of a user of the system per the inventory system. This could be
 * used in other GUIs if a person file is created an maintained.
 *
 * @author david ketchum
 * @version 1.00
 */
public final class User {

  static int userID = 0;
  static String username = "";        // Use initials or ID
  static String master = "";          // Master priveleges
  static String name = "";            // User name in person space
  static String password = "";        // Pasword in the database
  static String phoneHome = "";
  static String phoneWork = "";
  static String phoneCell = "";
  static String phoneFax = "";
  static String email = "";
  static String role = "";
  static String allowedRoles = "";
  static boolean inPersons;
  String temp;

  public User(String u) {
    Util.init();
    temp = u;
    username = u;
    master = "Y";
    userID = 99;
    role = "M";
  }

  public User() {
    Util.init();
    username = "dkt";
    master = "Y";
    userID = 99;
    role = "M";
  }

  @Override
  public String toString() {
    return "user=" + username + " master=" + master
            + "name=" + name + " roles=" + role + " inPersons=" + inPersons;
  }
//
// Getter Methods
//

  public static String UserString() {
    return "user=" + username + " master=" + master
            + "name=" + name + " role=" + role;
  }

  public static String getEncodedPassword() {
    return password;
  }

  public static String getUser() {
    return username;
  }

  public static int getUserID() {
    return userID;
  }

  public static String getMaster() {
    return master.toUpperCase();
  }

  public static String getAllowedRoles() {
    return allowedRoles;
  }

  public static String getRoles() {
    return role;
  }

  public static boolean setRoles(String req) {
    if (allowedRoles.indexOf('M') < 0) {     // The master does not get checked
      for (int i = 0; i < req.length(); i++) {
        if (allowedRoles.indexOf(req.charAt(i)) < 0) {
          return false;
        }
      }
    }
    role = req;
    return true;
  }

  public static boolean isRole(char rolein) {
    if (role.indexOf(rolein) >= 0) {
      return true;
    }
    return role.indexOf('M') >= 0;
  }

  /**
   * return true if the user has any of the roles in the string list
   *
   * @param roles A list of single character roles
   * @return True if any of the roles are found in the users role string
   */
  public static boolean isRole(String roles) {
    for (int i = 0; i < roles.length(); i++) {
      if (isRole(roles.charAt(i))) {
        return true;
      }
    }
    return false;
  }

  /* Like User(Connection, String, String) without the password checking.
     All it does is set the password, role, etc. */
  public User(Connection C, String user) throws SQLException {
    Util.init();

    try {
      try (Statement stmt = C.createStatement(); ResultSet rs = stmt.executeQuery("SELECT * FROM inv.person"
              + " WHERE person = " + Util.sqlEscape(user))) {
        rs.next();
        userID = Util.getInt(rs, "ID");
        username = Util.getString(rs, "Person");
        master = Util.getString(rs, "Master");
        name = Util.getString(rs, "Name");
        phoneHome = Util.getString(rs, "PhoneHome");
        phoneWork = Util.getString(rs, "PhoneWork");
        phoneCell = Util.getString(rs, "PhoneCell");
        phoneFax = Util.getString(rs, "PhoneFax");
        email = Util.getString(rs, "Email");
        role = Util.getString(rs, "Role");
        allowedRoles = role;
        inPersons = true;
      }
    } catch (SQLException E) {
      allowedRoles = "M";
      role = allowedRoles;
      Util.prta("  ****** User " + user + " is not in anss.person assume master since roles are not set up");
      throw E;
    }
  }

  /* Like User(Connection, String, String) without the password checking.
     All it does is set the password, role, etc. */
  public User(String database, Connection C, String user) {
    Util.init();

    try {
      try (Statement stmt = C.createStatement(); ResultSet rs = stmt.executeQuery("SELECT * FROM " + database + ".person"
              + " WHERE person = " + Util.sqlEscape(user))) {
        rs.next();
        userID = Util.getInt(rs, "ID");
        username = Util.getString(rs, "Person");
        master = Util.getString(rs, "Master");
        name = Util.getString(rs, "Name");
        phoneHome = Util.getString(rs, "PhoneHome");
        phoneWork = Util.getString(rs, "PhoneWork");
        phoneCell = Util.getString(rs, "PhoneCell");
        phoneFax = Util.getString(rs, "PhoneFax");
        email = Util.getString(rs, "Email");
        role = Util.getString(rs, "Role");
        allowedRoles = role;
        inPersons = true;
      }
    } catch (SQLException E) {
      allowedRoles = "M";
      role = allowedRoles;
      username = user;
      Util.prta("  ****** User " + user + " is not in " + database + ".person assume master since roles are not set up");
      //Util.SQLErrorPrint(E,"SQL User failed");
      //E.printStackTrace();
    }
  }

  public User(Connection C, String user, String passwdEntered)
          throws SQLException, JCJBLBadPassword {
    //Util.prt("USER : " + user + " pwd:" + passwdEntered + " c="+C);
    Util.init();
    try {
      String passReturned;
      try (Statement stmt2 = C.createStatement() // Use statement for query
              ;
               ResultSet rs2 = stmt2.executeQuery("SELECT password("
                      + Util.sqlEscape(passwdEntered) + ")")) {
        rs2.next();
        passReturned = rs2.getString(1).substring(0, 16);
        Util.prt("Encode password gave " + passReturned);
      }
      Statement stmt = C.createStatement();
      try (ResultSet rs = stmt.executeQuery("SELECT * FROM inv.person"
              + " WHERE Person = " + Util.sqlEscape(user))) {
        rs.next();
        password = Util.getString(rs, "password");
//      Util.prt("Password for user = " + user +" is " + password);
        if (password.compareTo(passReturned) != 0) {
          rs.close();
          Util.prt("not equal|" + password + "|" + passReturned + "|"
                  + password.length() + passReturned.length()
                  + " ct:" + password.compareTo(passReturned));
          throw new JCJBLBadPassword();
        }
        userID = Util.getInt(rs, "ID");
        username = Util.getString(rs, "Person");
        master = Util.getString(rs, "Master");
        name = Util.getString(rs, "Name");
        phoneHome = Util.getString(rs, "PhoneHome");
        phoneWork = Util.getString(rs, "PhoneWork");
        phoneCell = Util.getString(rs, "PhoneCell");
        phoneFax = Util.getString(rs, "PhoneFax");
        email = Util.getString(rs, "Email");
        role = Util.getString(rs, "Role");
        inPersons = true;
      }
    } catch (SQLException E) {
      role = "*";
      allowedRoles = role;
      Util.prta("  ****** User " + user + " is not in anss.person assume master since roles are not set up");
      //Util.SQLErrorPrint(E,"SQL User failed");
      E.printStackTrace();
      throw E;
    }

  }
}
