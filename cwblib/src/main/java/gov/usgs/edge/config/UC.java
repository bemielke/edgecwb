/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.edge.config;

import gov.usgs.anss.util.User;
import gov.usgs.anss.util.Util;

/**
 * This class holds constants and other (mostly) static data. It also holds one reference to a
 * Connection object for global access.
 *
 * The static methods can be hidden in a subclass if some settings need to be changed. Beware:
 * because of the way static inheritance works, you need to define the value-returning methods in
 * the subclass, not just the constants they return. So, to change the property filename in a
 * subclass, define getPropertyFilename, not PROPERTY_FILENAME.
 */
public final class UC extends gov.usgs.anss.util.UC {

  /* These users have a special hard-coded database server. Using any of these
     users will cause isTestDB to return true. */
  private static final String[] TEST_USERS = {"dkt", "local", "test"};

  private static final String[] FORM_HOSTS = {"NEISA.CR.USGS.GOV"};

  //private static String JDBC_DRIVER_NAME = "com.mysql.jdbc.Driver";
  private static final String JDBC_CATALOG = null;
  private static final String JDBC_TEST_HOST = "gldketchum";

  private static final String FEDERAL_CENTER_IP_ADDR = "cedg9";

  private static final String PROPERTY_FILENAME = "edgecon.prop";

  public static final int XSIZE = 800;
  public static final int YSIZE = 875;

  /**
   * Return an array of form host names.
   *
   * @return An array of host names.
   */
  public static String[] getFormHosts() {
    return FORM_HOSTS;
  }

  /**
   * Return true if the user of the database (as defined by User.getUser()) is one of a few test
   * users; false otherwise.
   *
   * @return A boolean indicating whether the user is a test user.
   */
  public static boolean isTestDB() {
    int i;

    for (i = 0; i < TEST_USERS.length; i++) {
      if (User.getUser().equalsIgnoreCase(TEST_USERS[i])) {
        return true;
      }
    }

    return false;
  }

  /**
   * Return the host name of the MySQL server. This is taken from the user's properties unless a
   * test database is being used.
   *
   * @return The host name of the MySQL server.
   */
  public static String JDBCHost() {
    if (isTestDB()) {
      return JDBC_TEST_HOST;
    } else {
      return Util.getProperty("MySQLServer");
    }
  }

  /**
   * Return the name of the database to use (JDBC calls this a "catalog").
   *
   * @return The name of the database.
   */
  public static String JDBCCatalog() {
    return JDBC_CATALOG;
  }

  /**
   * Return a URL containing the location of the database to connect to. The host name and catalog
   * are calculated using {@link #JDBCCatalog()} and {@link #JDBCHost()}.
   *
   * @return A JDBC URL.
   */
  public static String JDBCDatabase() {
    Util.prt("database user: " + User.getUser());

    if (isTestDB()) {
      Util.prt("\n   *** Note: TEST database in use ****\n");
    } else {
      Util.prt("\n   *** Live data base in use ****\n");
    }

    return "jdbc:mysql://" + JDBCHost() + "/" + JDBCCatalog()
            + "?user=" + DEFAULT_USER + "&password=" + DEFAULT_PASSWORD;
  }

  /**
   * Return the name of the file that holds user properties.
   *
   * @return The property filename
   */
  public static String getPropertyFilename() {
    return PROPERTY_FILENAME;
  }

}
