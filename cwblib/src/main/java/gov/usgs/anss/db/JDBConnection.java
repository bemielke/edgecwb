/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.db;

import gov.usgs.alarm.SendEvent;
import gov.usgs.anss.util.UC;
import gov.usgs.anss.util.Util;
import java.net.URISyntaxException;
import java.sql.Statement;
import java.sql.SQLException;
import java.sql.ResultSet;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Driver;
import java.util.HashMap;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This is a static holder for JDBC connections to the MySQL database. Connections are accessed by
 * keys, which are just Strings. Some methods do not take a key; they act as if null were provided
 * as the key.
 */
public final class JDBConnection {

  /**
   * This contains the default properties used by every connection. It is initialized in the static
   * initializer below.
   */
  private static final Properties DEFAULT_PROPERTIES;
  private boolean oracle;
  private boolean postgres;
  private boolean mysql;
  private String vendor;
  private String host;
  private String name;
  private String catalog;

  public boolean isPostgres() {
    return postgres;
  }

  public boolean isMySQL() {
    return mysql;
  }

  public boolean isOracle() {
    return oracle;
  }

  /**
   * This stores the association between connections and keys.
   */
  private static HashMap<String, Connection> connections = new HashMap<>();

  static {
    DEFAULT_PROPERTIES = new Properties();
    /* Try to re-establish bad connections. */
    DEFAULT_PROPERTIES.setProperty("autoReconnect", "true");
    /* Don't allow non-SSL connections when "useSSL=true". This has no effect
       otherwise. */
    DEFAULT_PROPERTIES.setProperty("requireSSL", "true");
  }

  public void useOracle() {
    oracle = true;
  }

  /**
   * Create a connection with the specified host, catalog, user, and password and make it the
   * default connection. If useSSL is true, have the connection use SSL.
   *
   * @param host the host name of the server to connect to
   * @param catalog the catalog to use once connected. If null, don't connect to any particular
   * catalog
   * @param user the user name to use when connecting
   * @param password the password to use when connecting
   * @param useSSL if true, use SSL
   * @param vendor Normally mysql, oracle or postgres, null or blank=mysql;
   * @exception SQLException usually network or database is down
   */
  /* public JDBConnection(String host, String catalog, String user,
          String password, boolean useSSL, String vendor) throws SQLException
  { this(host,catalog,user,password, useSSL, (String) null, vendor);
  }*/
  /**
   * Create a connection with the specified host, catalog, user, and password and make it the
   * default connection. If useSSL is true, have the connection use SSL.
   *
   * @param host the host name of the server to connect to
   * @param catalog the catalog to use once connected. If null, don't connect to any particular
   * catalog
   * @param user the user name to use when connecting
   * @param password the password to use when connecting
   * @param useSSL if true, use SSL
   * @exception SQLException usually network or database is down
   */
  public JDBConnection(String host, String catalog, String user,
          String password, boolean useSSL) throws SQLException {
    this(host, catalog, user, password, useSSL, (String) null, "mysql");
  }

  /**
   * Create a connection with the specified host, catalog, user, and password and make it the
   * default connection. If useSSL is true, have the connection use SSL.
   *
   * @param host the host name of the server to connect to
   * @param catalog the catalog to use once connected. If null, don't connect to any particular
   * catalog
   * @param user the user name to use when connecting
   * @param password the password to use when connecting
   * @param useSSL if true, use SSL
   * @param name The name of this thread
   * @exception SQLException usually network or database is down
   */
  public JDBConnection(String host, String catalog, String user,
          String password, boolean useSSL, String name) throws SQLException {
    this(host, catalog, user, password, useSSL, name, "mysql");
  }

  /**
   * Create a connection with the specified host, catalog, user, and password and make it the
   * default connection. If useSSL is true, have the connection use SSL.
   *
   * @param host the host name of the server to connect to
   * @param catalog the catalog to use once connected. If null, don't connect to any particular
   * catalog
   * @param user the user name to use when connecting
   * @param password the password to use when connecting
   * @param useSSL if true, use SSL
   * @param name String name of tag to associate with this connection
   * @param vend If blank, mysql, else use oracle, postgres, etc.
   * @exception SQLException usually network or database is down
   */
  public JDBConnection(String host, String catalog, String user,
          String password, boolean useSSL, String name, String vend) throws SQLException {
    Connection c;
    Properties props;
    this.name = name;
    this.host = host;
    this.catalog = catalog;
    if (useSSL && Util.getProperty("SSLEnabled") == null) {//HACK: no ssl connections - expired certificate
      new RuntimeException("SSL JDBConnection to " + host + "/" + catalog + "/" + user + "/" + name + "/" + vend).printStackTrace();
      SendEvent.edgeSMEEvent("SSLConn", "An SSL connection was requested and converted", "JDBConnection");
      useSSL = false;
    }
    if (vend == null) {
      vendor = "mysql";
      mysql = true;
    } else if (vend.equalsIgnoreCase("oracle")) {
      vendor = "oracle";
      oracle = true;
    } else if (vend.equalsIgnoreCase("postgres")) {
      vendor = "postgres";
      postgres = true;
    } else {
      vendor = "mysql";
      mysql = true;
    }
    /*try {
      if(oracle) Util.prta("register driver=oracle.jdbc.driver.OracleDriver");
      else if(postgres) Util.prta("register driver=org.postgresql.Driver");
      else Util.prta("register driver="+UC.JDBCDriver());
    }catch(RuntimeException e) {
      Util.prta("The MySQL driver is not available!");
      Util.exit(0);
    }*/

    if (oracle) {
      registerDriver("oracle.jdbc.driver.OracleDriver");
    } else if (postgres) {
      registerDriver("org.postgresql.Driver");
    } else {
      registerDriver(UC.JDBCDriver());
    }
    //Util.prta("JDBConnection1 try to "+host+" driver="+getDriver()+" db="+catalog+" user="+user+" ssl="+useSSL+" as "+name);

    props = new Properties(DEFAULT_PROPERTIES);
    props.setProperty("user", user);
    props.setProperty("password", password);
    if (useSSL) {
      props.setProperty("useSSL", "true");
    }
    //props.list(Util.getOutput());
    props.setProperty("autoReconnect", "true");
    try {
      //Util.prta("getConnection: "+host+" "+catalog+" "+makeURL(host, catalog));
      c = DriverManager.getConnection(makeURL(host, catalog), props);
      connections.put(name, c);
      //Util.prta("JDBConnection1 o.k. to "+host+" driver="+getDriver(name)+" db="+catalog+" user="+user+" ssl="+useSSL+" as "+name);
    } catch (URISyntaxException e) {
      //Util.prta("JDBConnection1 FAILED to "+host+" driver="+getDriver(name)+" db="+catalog+" user="+user+" ssl="+useSSL+" as "+name);
      throw new SQLException(e.getMessage());
    }
  }

  @Override
  public String toString() {
    return name + " to " + host + " db=" + catalog + " driver=" + vendor;
  }

  /**
   * Create a connection with the specified host, catalog, user, and password and make it the
   * default connection.
   *
   * @param host the host name of the server to connect to
   * @param catalog the catalog to use once connected. If null, don't connect to any particular
   * catalog
   * @param user the user name to use when connecting
   * @param password the password to use when connecting
   * @exception SQLException usually network or database is down
   */
  public JDBConnection(String host, String catalog, String user,
          String password) throws SQLException {
    this(host, catalog, user, password, false);
  }

  /**
   * Create a connection from a driver and a URL and associate it with name, closing any previous
   * connection associated with name.
   *
   * @param driver String with the driver to use
   * @param url String with the connection information to use
   * @param name the name under which to register the connection
   * @exception SQLException Usually network or database is down
   */
  /*  public JDBConnection(String driver, String url, String name)
    throws SQLException
  {
    Connection c;
    String host;

    c = getConnection(name);
    if (c != null) {
      DatabaseMetaData metadata = c.getMetaData();

      if (driver.equals(metadata.getDriverName())
        && url.equals(metadata.getURL())) {
        // This is an exact duplicate.
        Util.prt("**** JDBConnect duplicate:"
                 + "\n\tdriver: " + driver
                 + "\n\turl: " + url
                 + "\n\tname: " + name);
        return;
      } else {
        close(name);
      }
    }

    registerDriver(driver);

    host = getHostFromURL(url);
    Util.prta("JDBConnection try connect to " + host + " driver=" + driver);
    try
    {
      c = DriverManager.getConnection(url, DEFAULT_PROPERTIES);
      connections.put(name, c);
      Util.prta("JDBConnection2 o.k. to "+host+" driver="+driver);
    }
    catch (SQLException E)
    {
      Util.SQLErrorPrint(E, "Cannot connect to JDBC database.");
      throw E;      // E.printStackTrace();
    }
  }*/
  /**
   * Create a connection from a driver and a URL and make it the default connection.
   *
   * @param driver String with the driver to use
   * @param url String with the connection information to use
   * @exception SQLException usually network or database is down
   */
  /*  public JDBConnection(String driver, String url) throws SQLException
  {
    this(driver, url, null);
  }*/
  /**
   * Add a named connection to the list
   *
   * @param C a Connection to add
   * @param name The name of the connection
   */
  public static void addConnection(Connection C, String name) {
    connections.put(name, C);
  }

  /**
   * Load the class named by driver.
   *
   * @param driver the name of a JDBC driver class
   */
  public final void registerDriver(String driver) {
    try {
      // The newInstance() call is a work around for some
      // broken Java implementations.
      Class.forName(driver).newInstance();
    } catch (ClassNotFoundException | InstantiationException | IllegalAccessException e) {
      System.err.println("Unable to load " + driver);
      e.printStackTrace();
    }
  }

  /**
   * Set the database (JDBC calls this a "catalog") to use for the connection associated with name.
   *
   * @param catalog the name of the catalog
   * @param name the key associated with the connection
   */
  public static void setCatalog(String catalog, String name) {
    Connection c;

    c = getConnection(name);
    if (c == null) {
      return;
    }
    try {
      c.setCatalog(catalog);
    } catch (SQLException e) {
    }
  }

  /**
   * Set the database to use for the default connection.
   *
   * @param catalog the name of the catalog
   */
  public static void setCatalog(String catalog) {
    setCatalog(catalog, null);
  }

  /**
   * Return the connection associated with name.
   *
   * @param name the key associated with the connection
   * @return the database connection, or null if none has been associated with name
   */
  public static Connection getConnection(String name) {
    if (name == null) {
      return (Connection) connections.get(null);
    }
    if (name.equals("")) {
      return (Connection) connections.get(null);
    }
    return (Connection) connections.get(name);
  }

  /**
   * Return the host.
   *
   * @param name the key associated with the connection
   * @return the name of the host serving the database
   */
  public static String getServer(String name) {
    Connection c;

    c = getConnection(name);
    if (c == null) {
      return null;
    }
    try {
      return getHostFromURL(c.getMetaData().getURL());
    } catch (SQLException e) {
      return null;
    }
  }

  /**
   * Return the driver.
   *
   * @param name the key associated with the connection
   * @return the name of the driver used to access the database
   */
  public static String getDriver(String name) {
    Driver driver;

    try {
      driver = DriverManager.getDriver(getURL(name));
      return driver.getClass().getName();
    } catch (SQLException e) {
      return null;
    }
  }

  /**
   * Return the URL.
   *
   * @param name the key associated with the connection
   * @return the URL used to access the database
   */
  public static String getURL(String name) {
    Connection c;

    c = getConnection(name);
    if (c == null) {
      return null;
    }
    try {
      return c.getMetaData().getURL();
    } catch (SQLException e) {
      return null;
    }
  }

  /**
   * Get the current database (JDBC calls this a "catalog") in use by the connection associated with
   * name.
   *
   * @param name the key associated with the connection
   * @return the current catalog
   */
  public static String getCatalog(String name) {
    Connection c;

    c = getConnection(name);
    if (c == null) {
      return null;
    }

    try {
      return c.getCatalog();
    } catch (SQLException e) {
      return null;
    }
  }

  /**
   * Close the database connection associated with name.
   *
   * @param name the key associated with the connection
   */
  public static void close(String name) {
    Connection c;

    c = getConnection(name);
    if (c == null) {
      return;
    }
    Util.prt("JDBConnection " + name + " closed " + getDriver(name) + "\nC=" + getURL(name));
    connections.remove(name);
    try {
      c.close();
    } catch (SQLException e) {
      Util.SQLErrorPrint(e, "Closing connection SQL error");
    }
  }

  /**
   * Return the default connection.
   *
   * @return the default database connection, or null if none exists
   */
  public static Connection getConnection() {
    return getConnection(null);
  }

  public static void setConnection(Connection C) {
    connections.put(null, C);
  }

  /**
   * Return the host of the default connection.
   *
   * @return the name of the host serving the database
   */
  public static String getServer() {
    return getServer(null);
  }

  /**
   * Return the driver of the default connection.
   *
   * @return the name of the driver used to access the database
   */
  public static String getDriver() {
    return getDriver(null);
  }

  /**
   * Return the URL of the default connection.
   *
   * @return the URL used to access the database
   */
  public static String getURL() {
    return getURL(null);
  }

  /**
   * Get the current database in use by the default connection.
   *
   * @return the current catalog
   */
  public static String getCatalog() {
    return getCatalog(null);
  }

  /**
   * Close the default connection.
   */
  public static void close() {
    close(null);
  }

  /**
   * Create a URL string suitable for accessing a MySQL database on host.
   *
   * @param host the name of the host to connect to
   * @return a URL for accessing the database
   */
  private String makeURL(String host, String catalog) throws URISyntaxException {
    /* We use a URI instead of just pasting strings together because the URI
       constructor performs percent encoding. */
    if (oracle) {
      String s = "jdbc:oracle:thin:@" + host + ":1521:" + catalog;

      return s;
    }
    if (postgres) {
      String s = "jdbc:postgresql://" + host.replaceAll("/", ":") + "/" + catalog;
      return s;
    }
    String s = "jdbc:mysql://" + host.replaceAll("/", ":") + "/" + catalog;
    return s;
    //return new URI("jdbc:mysql", host,
    //        (catalog == null) ? "/" : "/" + catalog, null).toString();
  }

  /**
   * Extract the host name from a JDBC URL. This may be specific to MySQL Connector/J URLs.
   *
   * @param url the URL from which to extract the host name
   * @return the host name
   */
  private static String getHostFromURL(String url) {
    Matcher matcher;

    matcher = Pattern.compile("//([^/]+)/").matcher(url);
    if (!matcher.find()) {
      return null;
    }

    return matcher.group(1);
  }

  public static String doTags(String input, Statement oracleght) {
    String[] tokens = input.split("[<>]");
    if (tokens.length == 1) {
      return input;
    }
    StringBuilder sb = new StringBuilder(input.length() + 300);
    StringBuilder refs = new StringBuilder(200);
    for (int i = 0; i < tokens.length; i++) {
      if (i % 2 == 1) {
        if (tokens[i].contains("&")) {    // Its a name, look it up
          String[] name = tokens[i].split("[&^]");
          String s = "SELECT * FROM anss_poc_people_vw WHERE last_name ='" + name[0].trim() + "'";
          if (name.length > 1) {
            s += " AND first_name LIKE'" + name[1].trim() + "%'";
          }
          try {
            try (ResultSet rs = oracleght.executeQuery(s)) {
              if (rs.next()) {
                if (name.length == 2) {
                  sb.append(" ").append(rs.getString("first_name")).append(" ").
                          append(rs.getString("last_name")).append(" <").
                          append(rs.getString("emp_email")).append("> Office Ph:").
                          append(rs.getString("emp_off_ph")).append(" ");
                }
                if (name.length >= 3) {
                  name[2] = name[2].replaceAll("%e", rs.getString("emp_email") == null ? "Not on file" : rs.getString("emp_email"));
                  name[2] = name[2].replaceAll("%l", rs.getString("last_name") == null ? "Not on file" : rs.getString("last_name"));
                  name[2] = name[2].replaceAll("%f", rs.getString("first_name") == null ? "Not on file" : rs.getString("first_name"));
                  name[2] = name[2].replaceAll("%o", rs.getString("emp_off_ph") == null ? "Not on file" : rs.getString("emp_off_ph"));
                  name[2] = name[2].replaceAll("%h", rs.getString("emp_home_ph") == null ? "Not on file" : rs.getString("emp_home_ph"));
                  name[2] = name[2].replaceAll("%c", rs.getString("emp_cell_ph") == null ? "Not on file" : rs.getString("emp_cell_ph"));
                  name[2] = name[2].replaceAll("%t", rs.getString("title") == null ? "Not on file" : rs.getString("title"));
                  name[2] = name[2].replaceAll("%a", rs.getString("anss_tag") == null ? "Not on file" : rs.getString("title"));
                  sb.append(name[2]);
                }
              } else {
                sb.append(" <").append(tokens[i]).append("> ");      // if no name is found, leave tag
              }
            }
          } catch (SQLException e) {
            Util.prt("Could not find name matching tag=<" + tokens[i]);
            e.printStackTrace();
          }

        } else {        // Try an organization
          try {
            String s = "SELECT * FROM anss_poc_orgs_vw WHERE name LIKE '%" + tokens[i] + "%'";
            ResultSet rs = oracleght.executeQuery(s);
            if (rs.next()) {
              int org_id = rs.getInt("org_id");
              rs.close();
              rs = oracleght.executeQuery("SELECT * from anss_poc_people_vw WHERE org_id=" + org_id);
              while (rs.next()) {
                refs.append(rs.getString("first_name")).append(" ").append(rs.getString("last_name")).
                        append(" ").append(rs.getString("emp_email")).append(" ").
                        append(rs.getString("emp_off_ph")).append(" ").
                        append(rs.getString("title")).append("\n");
              }
            }
          } catch (SQLException e) {
            Util.prt("Could not find organization or people in it for org=" + tokens[i]);
            e.printStackTrace();
          }
        }
      } else {
        sb.append(tokens[i]);
      }
    }
    return sb.toString();
  }

  /**
   * This main displays the form Pane by itself
   *
   * @param args command line args ignored
   */
  public static void main(String args[]) {
    JDBConnection jcjbl;
    Connection C;

    try {
      JDBConnection mysql = new JDBConnection("gacqdb", "edge", "ro", "readonly", true, "edge", "mysql");
      Statement stmtmysql = JDBConnection.getConnection("edge").createStatement();
      // Get user from the Inventory database user file
      jcjbl = new JDBConnection("igskcicgasordb2.cr.usgs.gov", "PROD1", "adm_sel", "ham11*ret", true, "poc", "oracle");
      C = JDBConnection.getConnection("poc");
      Statement stmt = C.createStatement();
      // Get a new connection to the ANSS database
      //UC.setConnection(JDBConnection.getConnection("poc"));
      ResultSet rs = stmt.executeQuery("SELECT * FROM anss_poc_people_vw ORDER BY FULL_NAME");
      while (rs.next()) {
        Util.prt(" tag=" + rs.getString("ANSS_TAG") + " full=" + rs.getString("FULL_NAME")
                + " empNum=" + rs.getInt("EMP_OR") + " Home=" + rs.getString("EMP_HOME_PH")
                + " work=" + rs.getString("EMP_OFF_PH") + " cell=" + rs.getString("EMP_CELL_PH")
                + " orgName=" + rs.getString("ORG_NAME"));
      }
      rs.close();
      rs = stmt.executeQuery("SELECT * FROM anss_poc_orgs_vw ORDER BY name");
      while (rs.next()) {
        Util.prt("name=" + rs.getString("name"));
        Util.prt(" ORG_ID=" + rs.getInt("org_id"));
        Util.prt(" ANSSTAG=" + rs.getString("anss_tag"));

      }
      rs.close();
      rs = stmtmysql.executeQuery("select * from snwgroup order by snwgroup");
      while (rs.next()) {
        String tags = doTags(rs.getString("documentation"), stmt);
        Util.prt(rs.getString("snwgroup") + tags);
      }

    } catch (SQLException e) {
      System.err.println("SQLException on getting Role" + e.getMessage());
      e.printStackTrace();
      Util.SQLErrorPrint(e, "SQLExcepion on gettning Role");
    }
  }
}
