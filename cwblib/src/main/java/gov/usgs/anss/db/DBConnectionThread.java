/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.db;

import gov.usgs.alarm.SendEvent;
import gov.usgs.anss.util.LoggerInterface;
import gov.usgs.anss.util.SimpleSMTPThread;
import gov.usgs.anss.util.Util;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintStream;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.sql.*;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;
import javax.swing.JOptionPane;
//import gov.usgs.alarm.SendEvent;

/**
 * DBConnectionThread.java This thread tries to keep a MySQL/Postgres/Oracle connection and
 * statement open to a Database. If it is unsuccessful, it tries again every 30 seconds. When
 * successful, it checks every second for the connection to close. If it does, it tries to reopen
 * it. The dbacct can force a reopen with the reopen() method. As it is a thread a terminate()
 * method is provided to allow the dbacct a way to kill this thing. Each such DBConnectionThread is
 * named and it is an error to create another one of the same name. Each group of Threads using one
 * of these should first use the static method getThread() to see if the named thread exists and use
 * it before attempting to create a new one. In this way the returned object is always good for the
 * life of the thread (though the connection may be down). If the dbacct always uses the
 * executeUpdate() and executeQuery() methods to do the SQL statements, they are synchronized and
 * can safely use the same object.
 *<p>
 * The intended dbacct is one who needs to write to a Database from a thread and cannot tolerate the
 * thread hanging up for long on DB stuff.
 *
 * Created on October 15, 2006, 4:05 PM
 *
 *
 * @author davidketchum <ketchum at stw-software.com>
 */
public final class DBConnectionThread extends Thread implements LoggerInterface {

  private Connection C;         // A connection  from the dbacct class
  private static final Map<String, DBConnectionThread> list
          = Collections.synchronizedMap(new TreeMap<String, DBConnectionThread>());
  private static String JDBC_DRIVER_NAME = "com.mysql.jdbc.Driver";
  private static String JDBC_HOST = "";
  private static String JDBC_VENDOR = "mysql";
  private static String JDBC_CATALOG = "";
  private static String JDBC_SCHEMA = "";
  private static String DEFAULT_USER = "readonly";
  private static String DEFAULT_PASSWORD = "";
  private static boolean dbg;
  public static boolean noDB;
  public boolean connected;
  private final Integer mutexStmt2 = Util.nextMutex();
  private Statement stmt2;      // A statement in the caller class
  private String host;          // Host of the database
  private String localHostIP;
  private String database;
  //private String dbacct;
  private String connectTag;
  private String connectLocal;
  //private String dbpw;
  private String name;
  private String vendor;
  private String schema;
  private int activity;
  private int noconnect;        // count queries or updates blown off with no connection
  private boolean writable;
  private boolean useSSL;
  private long lastSuccess;
  private static long lastPage; // time of last page for damping
  private static boolean shuttingDown;
  private DBWatchDog watchdog;
  boolean terminate;
  private String tag;
  private int instate;
  private int queryInProgress;
  private int updateInProgress;
  private PrintStream par;

  /**
   * Test if this connection looks good to do something good.
   *
   * @return true if the connection is connected and is alive, and is not terminating and is not
   * reopening.
   */
  public boolean isOK() {
    return isOKFast();
  }
  /**  set the shuttingDown variable back to false.  If DBConnectionThread.shutdown() has been called,
   * this needs to be called after the shutdown is complete to allow DBConnectionThread to be used again.
   * This is needed in ChannelDisplay when shifting between alert mode and back again.
   */
  public static void resetShuttingDown() {
    shuttingDown = false;
  }
  public boolean isOKFast() {
    if (!connected || C == null || !isAlive() || terminate || reopening) {
      return false;
    }
    try {
      if (C.isClosed()) {
        return false;
      }
      if( vendor.equals("postgres")) {  // postgres does not support "isValid()"
        return true;
      }  
      else {
        if (C.isValid(4)) {
          return true;
        }
      }
      prta("DBCT: isOK failed valid");
    } catch (SQLException e) {
      prta("DBCT: connection isValid() failed e=" + e);
      if (par == null) {
        e.printStackTrace();
      } else {
        e.printStackTrace(par);
      }
    }
    return false;
  }

  public static void setDebug(boolean t) {
    dbg = t;
  }

  public static String getDBServer() {
    return JDBC_HOST;
  }

  public static String getDBVendor() {
    return JDBC_VENDOR;
  }

  public static String getDBCatalog() {
    return JDBC_CATALOG;
  }

  public static String getDBSchema() {
    return JDBC_SCHEMA;
  }

  public static String getDBDefaultUser() {
    return DEFAULT_USER;
  }

  public static String getDBDefaultPassword() {
    return DEFAULT_PASSWORD;
  }

  public static String JDBCDriver() {
    return JDBC_DRIVER_NAME;
  }

  public String getVendor() {
    return vendor;
  }

  @Override
  public void setLogPrintStream(PrintStream p) {
    par = p;
  }

  public int getInState() {
    return instate;
  }

  public final void prt(String s) {
    if (par != null) {
      par.println(s);
    } else {
      Util.prt(s);
    }
  }

  public final void prta(String s) {
    if (par != null) {
      par.println(Util.asctime() + " " + s);
    } else {
      Util.prta(s);
    }
  }

  public String getDatabase() {
    return database;
  }

  public String getTag() {
    return name;
  }

  public static void setDefaultUser(String u) {
    DEFAULT_USER = u;
  }

  public static void setDefaultPassword(String p) {
    DEFAULT_PASSWORD = p;
  }

  /**
   * Set the host, catalog (database), vendor and schema based on an input string or properties if
   * the input is null or empty.
   *
   * @param dbServer A database URL host/port:database:vendor:schema
   */
  public static void init(String dbServer) {
    String line = dbServer;
    if (line == null) {
      line = Util.getProperty("DBServer");
    } else if (line.equals("")) {
      line = Util.getProperty("DBServer");
    }
    if (line == null) {
      line = Util.getProperty("MySQLServer");
    }
    if (line == null) {
      return;    // Nothing to parse!!!
    }
    if (!line.contains(":") && !line.contains("/") && !line.equalsIgnoreCase("NoDB")) {
      String db = "";
      if(Util.getPropertyFilename() != null) {
        int lastSlash = Util.getPropertyFilename().lastIndexOf("/");
        if(lastSlash < 0) db = Util.getPropertyFilename().replaceAll(".prop","").replaceAll("edgecon","edge");
      }
      Util.setProperty("DBServer", line.trim() + "/3306:" + db + ":mysql:" + db);
      line = Util.getProperty("DBServer");
      Util.getProperties().remove("MySQLServer");
    }
    if (Util.getProperty("DBServer").equalsIgnoreCase("nodb")) {
      noDB = true;
    }
    String[] parts = line.split(":");
    JDBC_HOST = "localhost";
    JDBC_VENDOR = "mysql";
    JDBC_CATALOG = "mcr";

    if (parts.length >= 1) {
      JDBC_HOST = parts[0];
    }
    if (parts.length >= 2) {
      JDBC_CATALOG = parts[1];
    }
    if (parts.length >= 3) {
      JDBC_VENDOR = parts[2].toLowerCase();
    }
    if (parts.length >= 4) {
      JDBC_SCHEMA = parts[3];
    }
    if (JDBC_SCHEMA.equals("")) {
      JDBC_SCHEMA = JDBC_CATALOG;
    }
    if (JDBC_VENDOR.equals("oracle")) {
      JDBC_DRIVER_NAME = "oracle.jdbc.driver.OracleDriver";
    }
    if (JDBC_VENDOR.equals("postgres")) {
      JDBC_DRIVER_NAME = "org.postgresql.Driver";
    }
    if (dbg) {
      Util.prt("DBCnnection.init(): defaults host=" + JDBC_HOST + " catalog=" + JDBC_CATALOG + " vendor=" + JDBC_VENDOR + " schema=" + JDBC_SCHEMA + " DBServer=" + line);
    }
  }

  /**
   * cause this thread to terminate at is next convenience. Normally called by users shutdown()
   */
  public void terminate() {
    terminate = true;
    prt("DBCT terminate/remove " + name);
    list.remove(name);
    if (C != null) {
      try {
        C.close();
      } catch (SQLException expected) {
      }
    }
  }

  /**
   * return the DBConnectionThread of a given name
   *
   * @param name Some extra text from caller to include in the message
   * @return null if named connection does not exist, else the DBConnectionThread of that name
   */
  public static DBConnectionThread getThread(String name) {
    if (list == null) {
      Util.prt("call to DBCT getTHread() but list is uninitialized!");
      return null;
    } else {
      return list.get(name);
    }
  }
  /**
   * Return list of names of threads
   *
   * @return The list
   */
  static final StringBuilder status = new StringBuilder(1000);

  public static StringBuilder getStatus() {
    Util.clear(status);
    Iterator<DBConnectionThread> itr = list.values().iterator();
    while (itr.hasNext()) {
      status.append(itr.next()).append("\n");
    }
    return status;
  }

  /**
   * Return list of names of threads
   *
   * @return The list
   */
  public static String getThreadList() {
    String result = "DBCT list :";
    Iterator<DBConnectionThread> itr = list.values().iterator();
    while (itr.hasNext()) {
      result += itr.next().name + " ";
    }
    Iterator<String> itr2 = list.keySet().iterator();
    result += "Keys=";
    while (itr2.hasNext()) {
      result += itr2.next() + " ";
    }
    return result;
  }

  /**
   * return number of times in a row that queries/updates have found no connection
   *
   * @return The # of times in a row that the queries/updates have found no connection
   */
  public int getNoconnects() {
    return noconnect;
  }

  /**
   * at startup the Thread may not yet have achieved a connection. This waits up to 5 seconds for a
   * connection to be made. I
   *
   * @return True if the connection has been made, if false, the database is very busy or down.
   */
  public boolean waitForConnection() {
    for (int i = 0; i < 50; i++) {
      if (getConnection() != null) {
        return true;
      }
      try {
        sleep(100);
      } catch (InterruptedException expected) {
      }
    }
    return false;
  }

  /**
   * at startup the Thread may not yet have achieved a connection. This waits up to 5 seconds for a
   * connection to be made. I
   *
   * @param name The connection name to wait for
   * @return True if the connection has been made, if false, the database is very busy or down.
   */
  public static boolean waitForConnection(String name) {
    for (int i = 0; i < 50; i++) {
      if (getConnection(name) != null) {
        return true;
      }
      Util.sleep(100);
    }
    return false;
  }

  /**
   * create a thread which maintains a connection to a database server and database
   *
   * @param h The host on which Database is running
   * @param db The name of the desired database for this connection
   * @param u The db user name to log into the database with
   * @param pw The dbpw for that dbacct
   * @param write If true, this connection and INSERT and UPDATE records
   * @param ssl if true, use SSL on this connection
   * @param nm THe name to give this connection
   * @throws InstantiationException Thrown if this connection cannot be built
   */
  /*    public DBConnectionThread(String h, String db, String u, String pw, boolean write,
      boolean ssl, String nm)
    throws InstantiationException {
    this(h,db,u,pw,write,ssl,nm, "mysql", null);
  }*/
  /**
   * create a thread which maintains a connection to a Database server and database
   *
   * @param h The host on which Database is running
   * @param db The name of the desired database for this connection
   * @param u The dbacct name to log into Database with
   * @param pw The dbpw for that dbacct
   * @param write If true, this connection and INSERT and UPDATE records
   * @param ssl if true, use SSL on this connection
   * @param nm THe name to give this connection
   * @param out THe printstream to use, dbacct might call setLogPrintStream() if this changes with
   * time
   * @throws InstantiationException Thorwn if this connection cannot be built
   */
  /*    public DBConnectionThread(String h, String db, String u, String pw, boolean write,
      boolean ssl, String nm, PrintStream out)
    throws InstantiationException {
    this(h,db,u,pw,write,ssl,nm, "mysql", out);
  }*/
  /**
   * create a thread which maintains a connection to a database server and database
   *
   * @param h The host on which database is running
   * @param db The name of the desired database for this connection
   * @param u The db user name to log into database with
   * @param pw The db password for that user
   * @param write If true, this connection and INSERT and UPDATE records
   * @param ssl if true, use SSL on this connection
   * @param nm THe name to give this connection
   * @param vend The vendor for the database (db, oracle, postgres, ....)
   * @throws InstantiationException Thrown if this connection cannot be built
   */
  public DBConnectionThread(String h, String db, String u, String pw, boolean write,
          boolean ssl, String nm, String vend)
          throws InstantiationException {
    this(h, db, u, pw, write, ssl, nm, vend, null);
  }

  /**
   * This makes a connection using the defaults for host, catalog, vendor
   *
   * @param url The URL which specifies the server, port, database, vendor and schema
   * @param connectTag The connection tag that will be decrypted from the user/password store for
   * this connection
   * @param db The database schema to use
   * @param write Set true to allow updates to database (and dbacct has such permissions)
   * @param ssl Use SSL on this connection
   * @param nm The connection's name
   * @param out A Printstream for logging, if null the Util.prt() methods print to the default log
   * or console.
   * @throws InstantiationException
   */
  public DBConnectionThread(String url, String connectTag, String db, boolean write, boolean ssl, String nm, PrintStream out)
          throws InstantiationException {
    String[] parts = url.split(":");
    host = parts[0];
    vendor = JDBC_VENDOR;
    schema = JDBC_CATALOG;
    if (parts.length > 1) {
      database = parts[1];
    }
    if (parts.length > 2) {
      vendor = parts[2];
    }
    if (parts.length > 3) {
      schema = parts[3];
    }
    if (vendor.contains("mysql")) {
      schema = db;
    }
    this.connectTag = connectTag;
    String s = getDBContainer(vendor, host, connectTag);
    if (s == null) {
      throw new InstantiationException("No user in configuration for user=" + connectTag);
    }
    parts = s.split("=");
    if (parts.length != 2) {
      throw new InstantiationException("User tag is in wrong form user=" + connectTag);
    }
    par = out;
    if (par == null) {
      par = Util.getOutput();
    }
    writable = write;
    name = nm;
    if (host.contains("localhost") || host.contains("127.0.0.1")) {
      useSSL = false;   // never use ssl for a local connection
    } else {
      useSSL = ssl;
    }
    tag = name + ":" + vendor + ":" + host + ":" + database + ":" + schema + ":" + connectTag + ":" + Util.tf(writable) + ":"
            + Util.tf(useSSL) + ":" + Util.getNode() + ":" + Util.getAccount() + ":" + Util.getProcess();
    try {
      localHostIP = InetAddress.getLocalHost().toString();
    } catch (UnknownHostException expected) {
    }
    synchronized (list) {
      if (list.get(name) != null) {
        throw new InstantiationError("Attempt to create a new DBConnectionThread with name that already exists=" + name);
      }
    }
    prta("new ThreadDBConnURL1 " + getName() + " " + getClass().getSimpleName() + " " + host + "/" + database + "/" + schema + " as " + name + " " + vendor);
    watchdog = new DBWatchDog(name, this);
    start();
  }

  public static String getDBContainer(String vendor, String host, String user) throws InstantiationException {
    String key = "DBConnection";

    try {
      try (BufferedReader in = new BufferedReader(new FileReader(System.getProperty("user.home") + System.getProperty("file.separator")
              + ".dbconn" + System.getProperty("file.separator") + "dbconn.conf"))) {
        key = in.readLine();
        key = DBContainer.decrypt(key, DBContainer.getMask());
      }
    } catch (IOException e) {
      throw new InstantiationException("DBContainer problem finding .dbconn/dbconn.conf e=" + e);
    }
    String s = null;
    try {
      Util.prta("Make DBContainer " + System.getProperty("user.home") + System.getProperty("file.separator")
              + ".dbconn" + System.getProperty("file.separator") + "dbconn_" + vendor + "_" + host.replaceAll("/", "_") + ".conf");
      s = new DBContainer(System.getProperty("user.home") + System.getProperty("file.separator") + ".dbconn" + System.getProperty("file.separator")
              + "dbconn_" + vendor + "_" + host.replaceAll("/", "_") + ".conf", key).getKey(user);
    } catch (IOException e) {
      e.printStackTrace();
      InstantiationException e2 = new InstantiationException("Container for DB logins is not set up for ~/.dbconn/dbconn.conf e=" + e);
      e2.printStackTrace(Util.getOutput());
      throw e2;

    }
    return s;
  }

  /**
   * create a thread which maintains a connection to a database server and database
   *
   * @param h The host on which database is running
   * @param db The name of the desired database for this connection
   * @param u The dbacct name to log into database with
   * @param pw The dbpw for that dbacct
   * @param write If true, this connection and INSERT and UPDATE records
   * @param ssl if true, use SSL on this connection
   * @param nm THe name to give this connection
   * @param vend The vendor for the database (db, oracle, postgres, ....)
   * @param out Set printstream to this, this use Util.prt() if this is null.
   * @throws InstantiationException Thrown if this connection cannot be built
   */
  public DBConnectionThread(String h, String db, String u, String pw, boolean write,
          boolean ssl, String nm, String vend, PrintStream out)
          throws InstantiationException {
    par = out;
    if (par == null) {
      par = Util.getOutput();
    }
    if (h.indexOf(":") > 0) {
      String[] parts = h.split(":");
      h = parts[0];
    }
    if (db.contains(":")) {
      String[] parts = db.split(":");
      schema = parts[1];
      database = parts[0];
    } else {
      database = db;
      schema = db;
    }
    if (vend.toLowerCase().contains("postgres")) {
      vend = "postgres";
    }
    host = h;
    writable = write;
    name = nm;
    useSSL = ssl;
    vendor = vend;
    if (host.contains("localhost") || host.contains("127.0.0.1")) {
      useSSL = false;   // never use ssl for a local connection
    }
    connectLocal = DBContainer.encrypt(u.trim() + "=" + pw.trim(),
            Util.toHex((name.trim() + vendor.trim() + host.trim()).hashCode()).toString()
            + Util.toHex((database.trim() + schema.trim()).hashCode()).toString());
    DBContainer.encrypt(u.trim() + "=" + pw.trim(), name.trim() + vendor.trim() + host.trim() + db.trim() + schema);
    tag = name + ":" + vend + ":" + host + ":" + database + ":" + schema + ":" + writable + ":" + useSSL + ":" + Util.getNode() + ":" + Util.getAccount() + ":" + Util.getProcess();
    try {
      localHostIP = InetAddress.getLocalHost().toString();
    } catch (UnknownHostException expected) {
    }
    synchronized (list) {
      if (list.get(name) != null) {
        throw new InstantiationError("Attempt to create a new DBConnectionThread with name that already exists=" + name);
      }
    }
    prta("new ThreadDBConnPW " + getName() + " " + getClass().getSimpleName() + " " + host + "/" + database + "/" + schema + " as " + name + " " + vendor);
    watchdog = new DBWatchDog(name, this);
    start();
  }

  @Override
  public void run() {
    if (par == Util.getOutput()) {
      Util.addLog(this);
    }
    if (shuttingDown) {
      return;
    }
    synchronized (list) {
      list.put(name, this);
    }
    int timer = 0;
    try {
      sleep(1000);
    } catch (InterruptedException expected) {
    }
    long sleepInterval = 30000;
    connected = false;
    while (!terminate && !DBConnectionThread.shuttingDown) {
      instate = 1;
      try {
        if (C == null || C.isClosed()) {
          connected = false;
          stmt2 = null;
          timer++;
          //if(dbg)
          prta("DBCTRun(): attempt connection to " + tag + " timer=" + timer + "connectTag=" + connectTag);
          instate = 2;
          // Note : the account and password will only be decoded in this block and then wiped out again.
          String dbacct;
          String dbpw;
          if (connectTag != null) {
            try {
              instate = 301;
              String s = getDBContainer(vendor, host, connectTag);
              instate = 303;
              if (s == null) {
                throw new InstantiationException("No user in configuration for user=" + connectTag);
              }
              String[] parts = s.split("=");
              if (parts.length != 2) {
                throw new InstantiationException("User tag is in wrong form user=" + connectTag);
              }
              instate = 304;
              dbacct = parts[0];
              dbpw = parts[1];
            } catch (InstantiationException e) {
              instate = 305;
              prta("DBCTRun: ****** Something is very wrong - go Instantiation problem opening DB connect that has been vetted e=" + e);
              SendEvent.edgeSMEEvent("DBFailed", "A connect tag did not translate as it did when connection made - was .dbconn changed", this);
              try {
                sleep(15000);
              } catch (InterruptedException expected) {
              }
              instate = 309;
              continue;
            }
          } else {
            instate = 306;
            String[] parts = DBContainer.decrypt(connectLocal,
                    Util.toHex((name.trim() + vendor.trim() + host.trim()).hashCode()).toString()
                    + Util.toHex((database.trim() + schema.trim()).hashCode())).split("=");
            instate = 307;
            dbacct = parts[0];
            dbpw = parts[1];
            parts[0] = "";
            parts[1] = "";
          }
          //useSSL=false;  // HACK: no ssl connections - expired certificate
          instate = 308;
          JDBConnection tmp = new JDBConnection((vendor.contains("mysql") ? host.replaceAll("/", ":") : host),
                  (vendor.contains("mysql") ? schema : database), dbacct, dbpw, useSSL, name, vendor);
          dbacct = "";    // YES, this is not used again
          dbpw = "";
          instate = 102;
          C = JDBConnection.getConnection(name);
          prta("DBCTRun: connection o.k. to " + C + " " + tmp.toString());
          if (C == null) {
            continue;
          }
          instate = 3;
          if (writable) {
            stmt2 = C.createStatement(
                    ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE);
            instate = 4;
          } else {
            instate = 5;
            stmt2 = C.createStatement();
            timer = 0;
            instate = 6;
          }
          //if(vendor.equals("postgres")) stmt2.executeUpdate("set search_path = "+schema+",pg_catalog");
          if (dbg) {
            prta("DBCTRun(): to " + tag + " is open." + dbacct + dbpw);
          }
          connected = true;
        }
        instate = 7;
      } catch (SQLException e) {
        instate = 8;
        prta("DBCTRun(): error Opening JDBConnection TO " + tag + " " + localHostIP + " e=" + e);
        if (e.getMessage().contains("trustAnchors")) {
          if (!Util.getNoInteractive()) {
            JOptionPane.showMessageDialog(null,
                    "Trust store is not set up for this user.  \nPlease set it up with 'ImportCert' button or unclick 'Use SSL', if your database allows it.\n",
                    "TrustStore Error", JOptionPane.ERROR_MESSAGE);
          } else {
            prt("No trust anchors set up in a batch process " + tag);
          }
          SendEvent.edgeSMEEvent("DBFailed", "DBConnection fail NoTruststore" + name + " e=" + e, "DBConnectionThread");
          terminate = true;
          break;
        } else if (e.getMessage().contains("OutOfMemory")) {
          prt("Out of Memory error - panic and try to exit");
          SendEvent.edgeSMEEvent("OutOfMemory", "Out of memory in DBConnThr ", this);
          Util.exit(1);
        } else if (e.getMessage().contains("Access denied")) {
          prt("Access denied - terminate this connection. " + tag);
          SendEvent.debugSMEEvent("DBFailed", "DBConnection fail access denied" + name + " e=" + e, "DBConnectionThread");
          terminate = true;
          break;
        } else if (e.getMessage().contains("Unknown database")) {
          prt("Unknown database errors - destroy this DBConnection " + tag);
          synchronized (list) {
            list.remove(name);
          }      // remove this connection from the list
          terminate = true;
          SimpleSMTPThread.email(Util.getProperty("emailTo"),
                  "Unknown database! " + tag,
                  Util.ascdate() + " " + tag + "\n"
                  + "DBConnectionThead cannot make connection to " + tag + "\n"
                  + "This e-came from a Java Process using the DBConnectionThread when it created\n"
                  + "such a thread which returned an 'Unknown database error'.\n\n" + Util.getThreadsString()
                  + "\n" + DBConnectionThread.getStatus());
          SendEvent.edgeSMEEvent("DBFailed", "DBConnection fail UnknownDatabase" + name + " e=" + e, "DBConnectionThread");
          terminate = true;
          break;
        } else if (e.getMessage().contains("NonTransient")) {
          prt("NonTransient connection error - this normally means the server has been cut off on internet");
          synchronized (list) {
            list.remove(name);
          }      // remove this connection from the list
          terminate = true;
          SendEvent.edgeSMEEvent("DBFailed", "DBConnection fail NonTransient" + name + " e=" + e, "DBConnectionThread");
          SimpleSMTPThread.email(Util.getProperty("emailTo"),
                  "NonTransient connection error! " + tag,
                  Util.ascdate() + " " + tag + "\n"
                  + "DBConnectionThead cannot make connection to " + tag + " as it is probably unreachable\n"
                  + "This e-came from a Java Process using the DBConnectionThread when it created\n"
                  + "such a thread which returned an 'Unknown database error'.\n\n" + Util.getThreadsString()
                  + "\n" + DBConnectionThread.getStatus());
        } else if (par == null) {
          e.printStackTrace();
        } else {
          e.printStackTrace(par);
        }
        instate = 9;
        prta(name + " Try to open connection again in 30 seconds. timer=" + timer + " e=" + e);
        Util.SQLErrorPrint(e, "DBCTrun() did not get thread to open " + name);
        C = null;
        stmt2 = null;
        timer++;
        if (timer > 2 && System.currentTimeMillis() - lastPage > 300000) {
          prta(name + " DBCTRun():We cannot open this DBConnection send mail to dave. timer=" + timer);

          SimpleSMTPThread.email(Util.getProperty("emailTo"),
                  "database failed to " + tag,
                  Util.ascdate() + " " + Util.asctime() + " " + tag + "\n"
                  + "ConnectionThead cannot make connection to " + tag
                  + " from " + Util.getNode() + "\n"
                  + "This e-came from a Java Process using the DBConnectionThread to keep a connection to a \n"
                  + "database open and it has failed to open for 20 minutes!\n\n" + Util.getThreadsString());
          SendEvent.edgeSMEEvent("DBFailed", "DBConn fail unhandled " + name + " e=" + e + tag, "DBConnectionThread");
          timer = -200;
          lastPage = System.currentTimeMillis();
        }
        try {
          sleep(sleepInterval);
        } catch (InterruptedException expected) {
        }
        sleepInterval *= 2;
        if (sleepInterval > 600000) {
          sleepInterval = 600000;
        }
      } catch (RuntimeException e) {
        instate = 10;
        prta("DBCTRun: got a runtime error opening the connection to " + tag);
        if (par == null) {
          e.printStackTrace();
        } else {
          e.printStackTrace(par);
        }
        try {
          sleep(30000);
        } catch (InterruptedException expected) {
        }
        continue;
      }
      instate = 12;
      if (dbg) {
        prta("DBCTRun(): Enter maintenance loop " + list.get(name));
      }
      lastSuccess = System.currentTimeMillis();
      //if(list.get(name) != null) prt("DBCTruR(): conn="+list.get(name).getConnection());//DEBUG
      int loop = 0;
      while (C != null && !terminate) {
        sleepInterval = 30000;
        instate = 13;
        try {
          sleep(1000);
        } catch (InterruptedException expected) {
        }
        try {
          if (C == null || stmt2 == null) {
            if (stmt2 != null) {
              stmt2.close();
            }
            if (C != null) {
              if (!C.isClosed()) {
                C.close();
              }
            }
            C = null;
            stmt2 = null;
            connected = false;
            if (dbg) {
              prta("DBCTRun(): C or stmt2 is null.  Remake " + toString());
            }
            break;
          }
          if (C.isClosed()) {
            instate = 14;
            if (stmt2 != null) {
              stmt2.close();
            }
            if (dbg) {
              prta("DBCTRun(): connection has closed " + name);
            }
            C = null;
            stmt2 = null;
            connected = false;
            break;
          }
          loop++;
          instate = 15;
          if (loop % 60 == 0) {
            instate = 16;
            if (System.currentTimeMillis() - lastSuccess > 900000 && queryInProgress == 0 && updateInProgress == 0) {
              if (dbg) {
                prta("DBCTRun() : keep alive  for " + tag);
              }
              instate = 17;
              if (!vendor.equals("oracle")) {
                try ( // This cannot be synchronized so we cannot use stmt2, create a new statement for this purpose
                        Statement stmt = C.createStatement()) {
                  watchdog.arm(true, "keep alive2");
                  ResultSet rs = stmt.executeQuery("SELECT SUBSTRING('Poke " + tag + "',1," + (tag.length() + 5) + ")");
                  rs.close();
                }
                watchdog.arm(false, "");
              } else {
                if (!C.isValid(10)) {
                  prta("DBCTRun: Connection is not Valid.  Close it and start reopen");
                  C.close();
                }
              }
              lastSuccess = System.currentTimeMillis();
            }
          }
        } catch (SQLException | RuntimeException e) {
          try {
            watchdog.arm(false, "");
            instate = 18;
            prta("DBCTRun(): keep alive or close loop error " + name + " e=" + e);
            if (stmt2 != null) {
              synchronized (mutexStmt2) {
                stmt2.close();
              }
            }
            if (C != null) {
              if (!C.isClosed()) {
                C.close();
              }
            }
          } catch (SQLException | RuntimeException expected) {
          }
          C = null;
          stmt2 = null;
          connected = false;
          break;
        }
      }

      instate = 19;
      if (dbg) {
        prta("DBCTRun(): Bottom of maintenance loop " + name + " " + toString());
      }
    }
    terminate = false;
    try {
      instate = 20;
      if (stmt2 != null) {
        synchronized (mutexStmt2) {
          stmt2.close();
        }
      }
      if (C != null) {
        C.close();
      }
    } catch (SQLException expected) {
    }
    instate = 21;
    prta("DBCTRun() has exitted. host=" + tag);
    if(watchdog != null) {
      watchdog.terminate();     // exit any watch dog now
    }
    synchronized (list) {
      list.remove(name);
    }        // This name does not exist, remove it.
  }

  /**
   * return the current connection to the database. Not normally needed as dbacct can use the
   * executeUdpate() and executeQuery methods.
   *
   * @return A SQL connection to the database
   * @param name The name of the desired connection
   */
  public static Connection getConnection(String name) {
    if (list == null) {
      return null;
    }
    DBConnectionThread t = list.get(name);
    if (t == null) {
      return null;
    }
    return t.getConnection();
  }

  public Connection getConnection() {
    activity++;
    return C;
  }

  /**
   * return the current statement for database access.Not normally needed as dbacct can use the
   * executeUdpate() and executeQuery methods.
   *
   * @param nm The name of the desired thread
   * @return A database/SQL statement
   */
  public static Statement getStatement(String nm) {
    DBConnectionThread t = list.get(nm);
    if (t == null) {
      return null;
    }
    return t.getStatement();
  }

  /**
   * return the current statement for database access. Not normally needed as dbacct can use the
   * executeUdpate() and executeQuery methods. This is dangerous as all synchronization is lost.
   *
   * @return A database/SQL statement
   */
  public Statement getStatement() {
    activity++;
    return stmt2;
  }

  /**
   * Create a prepared statement with the give sql and writable if needed
   *
   * @param sql The SQL to use to prepare the statement (insert into table(f1,f2) VALUES (?,?))
   * @param writable If true, the prepared statement will be writable and scroll insensitive
   * @return The prepared statement
   * @throws SQLException If one is thrown
   */
  public PreparedStatement prepareStatement(String sql, boolean writable) throws SQLException {
    if (C == null) {
      throw new SQLException("Connection is null.  Need to reopen!" + toString());
    }
    try {
      if (writable) {
        return C.prepareStatement(sql,
                ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE);
      } else {
        return C.prepareStatement(sql);
      }
    } catch (SQLException e) {
      prta("DBConn: prepareStatement - got exception creating statement, build a new connections and try again");
      if (par == null) {
        e.printStackTrace();
      } else {
        e.printStackTrace(par);
      }
      closeConnection();
      waitForConnection();
      if (writable) {
        return C.prepareStatement(sql,
                ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE);
      } else {
        return C.prepareStatement(sql);
      }
    }
  }

  /**
   * getNewStatement -
   *
   * @param writable - if true, set scroll and concur_updatable
   * @throws SQLException if one is thrown by class
   * @return the statement or null or throws a SQL Exception trying
   */
  public Statement getNewStatement(boolean writable) throws SQLException {
    try {
      if (writable) {
        return C.createStatement(
                ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE);
      } else {
        return C.createStatement();
      }
    } catch (SQLException e) {
      prta("DBConn: GetNewStatement - got exception creating statement, build a new connections and try again");
      if (par == null) {
        e.printStackTrace();
      } else {
        e.printStackTrace(par);
      }
      closeConnection();
      waitForConnection();
      if (writable) {
        return C.createStatement(
                ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE);
      } else {
        return C.createStatement();
      }
    }
  }

  /**
   * This executes a query on the named MyQLConnectionThread. It looks up the thread in a static
   * list and then uses the instance executeQuery() to perform the query.
   *
   * @param nm The name of the DBConnectionThread to use
   * @param sql An sql query statement
   * @return The ResultSet from the query
   * @throws SQLException after doing a reopen to clear up any problems
   */
  public static ResultSet executeQuery(String nm, String sql) throws SQLException {
    DBConnectionThread t = list.get(nm);
    if (t == null) {
      Util.prta("DBConn: No thread with name=" + nm + " quere not done=" + sql);
      return null;
    } else {
      return t.executeQuery(sql);
    }
  }

  /**
   * This executes a give SQL query in this thread. It is synchronized so that many different
   * threads can use this object to performed transactions over a single database connection.
   *
   * @param sql An sql query statement
   * @return The ResultSet from the query
   * @throws SQLException after doing a reopen to clear up any problems
   */
  public ResultSet executeQuery(String sql) throws SQLException {
    activity++;
    queryInProgress = 1;
    int wait = 0;
    while (C == null) {
      Util.sleep(100);
      wait++;
      if (wait > 100) {
        break;
      }
    }
    if (wait > 1) {
      prta(tag + "DBCT: executeQuery() not connected waited " + wait + " tenths");
    }
    if (wait > 100) {
      reopen();
    }
    if (C == null) {
      noconnect++;
      if (System.currentTimeMillis() - lastPage > 3600000) {
        lastPage = System.currentTimeMillis();
      }
      if (System.currentTimeMillis() - lastPage > 300000) {
        lastPage = System.currentTimeMillis();
      }
      SendEvent.edgeSMEEvent("DBHung", tag + " nc=" + noconnect, "DBConnectionThread");
      queryInProgress = 20;
      throw new SQLException("This DBConnectionThread is not connected. " + tag);
    }
    ResultSet rs = null;
    // If we caught the thread not connected, wait for it.
    int loop = 0;
    queryInProgress = 2;
    if (stmt2 == null) {
      prt(tag + "Waiting for stmt to become active " + name);
    }
    while (stmt2 == null && loop < 200) {
      Util.sleep(500);
    }
    loop++;
    if (stmt2 == null) {
      queryInProgress = 21;
      throw new SQLException(name + " DBConnection: is not connected and has timed out on this querys=" + sql);
    }
    for (int i = 0; i < 4; i++) {
      queryInProgress = 3;

      try {
        synchronized (mutexStmt2) {
          watchdog.arm(true, sql);
          if (i > 0) {
            prta("DBCT.query() retry" + i + " " + name + " started sql=" + sql);
          }
          rs = stmt2.executeQuery(sql);
          if (i > 0) {
            prta("DBCT.query() retry" + i + " " + name + " successful.");
          }
          watchdog.arm(false, "");
        }
        if (i >= 2) {
          SendEvent.debugEvent("DBCTW_OK", "Try" + i + " " + tag + " on " + Util.getIDText(), this);
        }
        queryInProgress = 4;
        noconnect = 0;
        lastSuccess = System.currentTimeMillis();
        break;
      } catch (SQLException e) {
        queryInProgress = 5;
        watchdog.arm(false, "");
        if (e.getMessage().contains("Connection reset")) {
          prta(name + " DBConnectionThread.executeQuery()" + i + " : Connection reset on query try reopen sql=" + sql);
        } else if (e.getMessage().contains("EOFException")) {
          prta(name + " DBConnectionThread.executeQuery()" + i + " : EOF on query try reopen sql=" + sql);
        } else if (e.getMessage().contains("Communications link fail")) {
          prta(name + " DBConnectionThread.executeQuery()" + i + " : Communications failure reopen sql=" + sql);
        } else {
          prta(Util.ascdate() + " " + tag + " SQLException DBThread.query()" + i + " reopen sql=" + sql + " e=" + e.getMessage() + "\n<EOM>\n");
        }
        if (i > 1) {
          SendEvent.debugEvent("DBCTFAIL", "Try" + i + " " + tag + " QueRetry", this);
        }
        queryInProgress = 6;
        boolean reopened = reopen();
        if (i >= 3 || !reopened) {
          throw e;
        }
      } catch (RuntimeException e) {
        prta("RuntimeException in DBCT continue e=" + e);
        if (par != null) {
          e.printStackTrace(par);
        } else {
          e.printStackTrace();
        }
      }
    }
    queryInProgress = 0;
    return rs;
  }

  /**
   * Set the watchdog timeout to some other link. Remember to set it back
   *
   * @param sec Set the timeout to be this number of seconds rather than the default of 300, if =0,
   * set back to default of 300
   */
  public void setTimeout(int sec) {
    watchdog.setTimeout(sec);
  }

  /**
   * This executes a sql update on the named MyQLConnectionThread. It looks up the thread in a
   * static list and then uses the instance executeUpdate() to perform the update.
   *
   * @param nm The name of the DBConnectionThread to use
   * @param sql An sql query statement
   * @return The ResultSet from the query
   * @throws SQLException after doing a reopen to clear up any problems
   */
  public static int executeUpdate(String nm, String sql) throws SQLException {

    DBConnectionThread t = list.get(nm);
    if (t == null) {
      Util.prta("DBCT: No thread with name=" + nm + " quere not done=" + sql);
      return 0;
    }
    return t.executeUpdate(sql);
  }

  /**
   * This executes a give SQL update in this thread. It is synchronized so that many different
   * threads can use this object to performed transactions over a single database connection.
   *
   * @param sql An sql query statement
   * @return The ResultSet from the query or -1 if a duplicate entry occurred or -2 if a data
   * Truncation occurred.
   * @throws SQLException after doing a reopen to clear up any problems
   */
  public int executeUpdate(String sql) throws SQLException {
    activity++;
    updateInProgress = 1;
    int wait = 0;
    while (C == null) {
      Util.sleep(100);
      wait++;
      if (wait > 100) {
        break;
      }
    }
    if (wait > 1) {
      prta(tag + "DBCT: update() not connected waited " + wait + " tenths");
    }
    if (wait > 100) {
      reopen();
    }
    if (C == null) {
      noconnect++;
      if (System.currentTimeMillis() - lastPage > 120000) {
        updateInProgress = 2;
        lastPage = System.currentTimeMillis();
        SimpleSMTPThread.email(Util.getProperty("emailTo"),
                "DB hung to " + tag + " as " + name + " wr=" + writable,
                Util.ascdate() + " " + Util.asctime() + " " + tag + "\n"
                + "DBConnectionThead.executeUpdate cannot make connection to " + tag + "\n"
                + "This e-came from a Java Process using the DBConnectionThread to do querys/updates and occurs \n"
                + "is damped to ever 2 minutes.  Noconnect=" + noconnect + "\n"
                + "\n" + Util.getThreadsString());
        SendEvent.edgeSMEEvent("DBHung",
                tag + " nc=" + noconnect, "DBConnectionThread");
      }
      throw new SQLException("This DBConnection thread is not connected. " + tag);
    }
    int ret = -1;
    for (int i = 0; i < 4; i++) {
      updateInProgress = 3;
      try {
        watchdog.arm(true, sql);
        if (i > 0) {
          prta("DBCT.update() retry" + i + " " + name + " sql=" + sql);
        }
        synchronized (mutexStmt2) {
          ret = stmt2.executeUpdate(sql);
        }
        if (i > 1) {
          SendEvent.debugEvent("DBCTW_OK", "Try" + i + " " + tag + "UpdRetryOK" + Util.getIDText(), this);
        }
        if (i > 0) {
          prta("DBCT.update() retry" + i + " " + name + " successful.");
        }
        updateInProgress = 4;
        watchdog.arm(false, "");
        lastSuccess = System.currentTimeMillis();
        noconnect = 0;
        break;
      } catch (SQLException e) {
        updateInProgress = 20;

        watchdog.arm(false, "");
        if (e.getMessage().contains("Data truncation")) {
          prta(Util.ascdate() + " " + name + " DBCTHread.update() SQLExcept data trunc " + e.getMessage() + "\n" + sql);
          updateInProgress = 21;
          return -2;
        } else if (e.getMessage().contains("marked as crashed")) {
          prta(Util.ascdate() + " " + tag + " table is marked as crashed and needs repair (repair attempted!");
          SendEvent.edgeSMEEvent("DBTblBad", tag + " " + e.getMessage(), this);
          String table = e.getMessage();
          int is = table.indexOf("'");
          table = table.substring(is + 1);
          is = table.indexOf("'");
          table = table.substring(0, is);
          if (table.charAt(0) == '.') {
            table = table.substring(1);
          }
          if (table.charAt(0) == '/') {
            table = table.substring(1);
          }
          table = table.replace("/", ".");
          Util.prta(tag + " table name for repair is " + table + "|");
          try {
            stmt2.executeUpdate("REPAIR TABLE " + table);
          } catch (SQLException e2) {
            prta(tag + " REPAIR of table " + table + " did not work e=" + e2);
            SendEvent.edgeSMEEvent("DBTblBad", "Repair of " + table + " did not work e=" + e2, this);
          }
          updateInProgress = 22;
        } else if (e.getMessage().contains("Duplicate entry")) {
          prta(Util.ascdate() + " " + name + " DBCThread.update() SQLExcept duplicate entry " + e.getMessage() + "sql=" + sql + "\n");
          updateInProgress = 23;
          return -1;
        } else if (e.getMessage().contains("Deadlock found when trying to get lock")) {
          prta(Util.ascdate() + " DBThread.update() deadlock found - wait and try again try=" + i + " e=" + e
                  + " sql=" + sql.substring(0, Math.min(sql.length(), 100)));
          try {
            sleep(3000);
          } catch (InterruptedException expected) {
          }
          if (i < 2) {
            continue;
          }
        } else if (e.getMessage().contains("Connection reset")) {
          prta(name + " DBConnectionThread: Connection reset on update" + i + ".  reopen sql=" + sql);
        } else if (e.getMessage().contains("EOFException")) {
          prta(name + " DBConnectionThread: EOF on update+" + i + ".  reopen sql=" + sql);
        } else {
          prta(Util.ascdate() + " " + name + " SQLException DBThread.update() reopen" + i + ".  e=" + e.getMessage() + "sql =" + sql);
        }
        if (i > 0) {
          SendEvent.debugEvent("DBCTWFAIL", "Try" + i + " " + tag + " DBCT.update", this);
        }
        updateInProgress = 5;
        boolean reopened = reopen();
        //if(e.getMessage().contains("Deadlock"))try{sleep(3000);} catch(InterruptedException expected) {}// do not try for a few seconds after the reopen.
        updateInProgress = 6;
        // retry operation
        if (i >= 3 || !reopened) {
          throw e;
        }
      }
    }
    updateInProgress = 0;
    return ret;
  }

  /**
   * Cause the connection to be shutdown and closed. Normally the dbacct would call this when a
   * SQLStatement caused an exception (in particular for no connection) to force the Thread to make
   * a new one.
   */
  public synchronized void closeConnection() {
    try {
      if (stmt2 != null) {
        stmt2.close();
      }
    } catch (SQLException e) {
      prta("DBConnectionThread : closeCOnnection() " + name + " close gave e=" + e.getMessage());
    }
    try {
      if (C != null) {
        C.close();
      }
    } catch (SQLException e) {
      prta("DBConnectionThread : closeConnection() " + name + " close gave e=" + e.getMessage());
    }
    prta("DBConnectionThread : closeConnection() " + name + " done " + toString());
    C = null;
    stmt2 = null;
    this.interrupt();           // wake up the connection thread.
  }

  /**
   * close and forget a connection and drop it from the list
   *
   */
  public synchronized void close() {
    prta("DBConnectionThread() : close() permanently close connection!");
    list.remove(name);
    terminate = true;
    closeConnection();
  }
  private boolean reopening;

  /**
   * Cause the connection to be shutdown and closed. Normally the dbacct would call this when a
   * SQLStatement caused an exception (in particular for no connection) to force the Thread to make
   * a new one.
   *
   * @return true if reopen was successful
   */

  public boolean reopen() {
    try {
      if (!isAlive() || DBConnectionThread.getThread(name) == null) {
        prta("DBCT: reopen thread is not alive or name is no longer in map alive=" + isAlive()
                + " thr=" + DBConnectionThread.getThread(name));
        return false;
      }
      if (reopening) {
        int loop = 0;
        while (reopening) {
          try {
            sleep(100);
          } catch (InterruptedException expected) {
          }
          loop++;
          if (loop % 100 == 99) {
            prta("DBCT: ** reopening in wait state stack dump follows loop=" + loop + " " + toString());
            if (par != null) {
              new RuntimeException("Reopen of DBCT stuck show stack loop=" + loop).printStackTrace(par);
            } else {
              new RuntimeException("** Reopen of DBCT stuck loop=" + loop + " " + toString()).printStackTrace();
            }
            if (loop > 5000) {
              prta("DBCT: ** Reopen stuck for 500 seconds.  Try to reopen " + toString());
              SendEvent.edgeSMEEvent("DBCTReopnStk", "Reopen of " + name + " is stuck!", this);
              break;
            }
          }

        }
        if (!reopening) {
          return true;
        }
      }
      reopening = true;
      try {
        if (C != null) {
          C.close();
        }
      } catch (SQLException e) {
        prta("DBCT: reopen() " + name + " close gave e=" + e.getMessage());

      }
      C = null;
      stmt2 = null;
      this.interrupt();           // wake up the connection thread.
      Util.sleep(1000);       // give the backup loop a second to get reconnected
      prta("DBCT: Wait for connection in reopen wait for " + name);
      int loop = 0;
      while (loop++ < 240 && !waitForConnection()) {  // each wait is 5 seconds
        prta("DBCT: Reopen of " + name + " has failed after a 5 second waitForConnection " + toString());
        if (loop > 60) {
          if (!this.isAlive()) {
            prta("DBCT: ****** Reopen of " + name + " has totally failed.  Panic and exit.");
            SendEvent.edgeSMEEvent("DBConnPanic", name + " is not reopening and is DEAD - causing exit " + Util.getProcess(), this);
          } else if (loop % 120 == 0) {
            SendEvent.edgeSMEEvent("DBConnPanic", name + " is not reopening and is not dead - causing exit", this);
          }
          Util.exit("DBCONNPanic on failure to reopen");
        }
        //prta("DBConnectionThread: Second waitForConnection()+"+waitForConnection(name));
      }
      if (!waitForConnection()) {
        SendEvent.edgeSMEEvent("DBCTRopen", "Failed " + tag, this);
        reopening = false;
        return false;
      }
      int loop2;
      for (loop2 = 0; loop2 < 100; loop2++) {
        if (stmt2 != null) {
          break;
        }
        Util.sleep(100);
      }
      if (stmt2 == null) {
        SendEvent.edgeSMEEvent("DBCTRopen", "Failed2 " + tag, this);
        reopening = false;
        return false;
      }
      prta("DBCT: " + name + " has reopened()!  loop=" + loop + " loop2=" + loop2 + " " + toString());
      reopening = false;
      return true;
    } catch (RuntimeException e) {
      prta("DBCT: RuntimeException in reopen=" + reopening + " name=" + name);
      e.printStackTrace();
      reopening = false;
    }
    return false;
  }

  /**
   * Get the last inserted value for given table
   *
   * @param table The table(note for db, this is ignored and last inserted id is returned)
   * @return The ID
   * @throws SQLException
   */
  public int getLastInsertID(String table) throws SQLException {
    String function = "last_insert_id()";
    if (getVendor().equalsIgnoreCase("postgres")) {
      function = "currval('" + table + "_id_seq')";
    }
    try (ResultSet rs = executeQuery("SELECT " + function)) {
      if (rs.next()) {
        int id = rs.getInt(1);
        rs.close();
        return id;
      }
    }
    return 0;
  }

  /**
   * this shuts down all open DBConnections. It should be called as part of a main exit handler. It
   * can be called by multiple shutdowns. Only the first one will have any affect.
   */
  static public void shutdown() {
    if (shuttingDown) {
      return;
    }
    shuttingDown = true;
    Util.prta("DBCT:shutdown() started " + list.values().size());
    Object[] objs = list.values().toArray();
    for (Object obj : objs) {
      DBConnectionThread t = (DBConnectionThread) obj;
      t.terminate();
      t.interrupt();
      Util.prt("DBCThr:shutdown() " + t + " is being terminated.");
    }
    Util.prt("DBCT:shutdown() is down.");
  }

  /**
   * return a string describing this thread
   *
   * @return The string
   */
  @Override
  public String toString() {
    String closed = "";
    try {
      if (C != null) {
        if (C.isClosed()) {
          closed = "Closed";
        }
      }
    } catch (SQLException e) {
      closed = "Closed error";
    }
    return "DBCT:" + tag + " #nocon=" + noconnect + " wr?=" + Util.tf(writable) + " st=" + instate + " "
            + (System.currentTimeMillis() - lastSuccess) / 1000 + "s St.query=" + queryInProgress + " St.upd="
            + updateInProgress + " " + (C == null ? " no connection" : closed) + (stmt2 == null ? " No statement" : "") + " alive=" + isAlive();

  }

  /**
   * Once a DB operation (update or query) is about to start, the caller arms this thread. If it
   * times out close the connection.
   */
  private final class DBWatchDog extends Thread {

    boolean armmed;
    int loop = 0;
    String tag;
    String message;
    int loopTimeOut = 30;
    DBConnectionThread s;

    public void setTimeout(int sec) {
      loopTimeOut = (sec + 9) / 10;
      if (loopTimeOut <= 1) {
        loopTimeOut = 30;
      }
    }

    public void arm(boolean t, String msg) {
      armmed = t;
      message = msg;
      loop = 0;
    }

    @Override
    public String toString() {
      return name;
    }

    public DBWatchDog(String tg, DBConnectionThread s2) {
      s = s2;
      tag = tg;
      start();
    }
    public void terminate() {
      this.interrupt();     // exit the sleep immediately
    }
    @Override
    public void run() {
      try {
        sleep(30000);
      } catch (InterruptedException expected) {
      }
      while (!terminate && s.isAlive()) {

        try {
          sleep(10000);
        } catch (InterruptedException expected) {
        }
        if (armmed) {
          loop++;
          if (loop % loopTimeOut == 0) {
            prta(tag + " DBCT: ***** Watchdog has gone off.  force a reopen" + message + " loop=" + loop + " state=" + s.getInState());
            if (s.getInState() != 17) // This is a check poke, so no need to warn if this happens
            {
              if ((loop / 12) > 2) {
                SendEvent.edgeSMEEvent("DBCTWdog", tag + " " + (loop / 12) + " FAILURE! On " + message + " " + Util.getIDText(), "DBConnectionThread");
              } else {
                SendEvent.debugEvent("DBCTWdog", tag + " " + (loop / 12) + " WARN On " + message + " " + Util.getIDText(), "DBConnectionThread");
              }
            }
            s.closeConnection();
          }
        }
      }
      prta("DBCTWD: exiting - call exit() term=" + terminate + " s=" + s);
    }
  }

  public static void main(String[] args) {
    Util.init();
    Util.setNoInteractive(true);
    String s = "Table './fetcher/fetchlist' is marked as crashed and should be repaired";

    DBConnectionThread db = DBConnectionThread.getThread("TestDBCONN");
    if (db == null) {
      try {
        //       dbconn = new DBConnectionThread(Util.getProperty("StatusDBServer"), "update", fields[0],
        //                true,false,"DBMsgSrv-"+tag+fields[0].trim(), Util.getOutput());
        db = new DBConnectionThread(Util.getProperty("StatusDBServer"), "readonly",
                "status", false, false, "TestDBCONN", Util.getOutput());
        for (int i = 0; i < 50; i++) {
          if (db.getConnection() != null) {
            Util.prt("connection opened at i=" + i);
            break;
          }
          try {
            Thread.sleep(100);
          } catch (InterruptedException expected) {
          }
        }
      } catch (InstantiationException e) {
        Util.prta("Instantiation error on status db impossible");
        db = DBConnectionThread.getThread("TestDBCONN");
      }
    }
    try {
      try (ResultSet rs = db.getConnection().getMetaData().getClientInfoProperties()) {
        while (rs.next()) {
          Util.prta(rs.getString("NAME") + " " + rs.getInt("MAX_LEN") + " " + rs.getString("DEFAULT") + " " + rs.getString("DESCRIPTION"));
        }
      }
    } catch (SQLException e) {
      e.printStackTrace();
    }
    for (int i = 0; i < 10200; i++) {
      try {
        Thread.sleep(1000l);
      } catch (InterruptedException expected) {
      }
      //prta("Count="+i);
    }
    Util.prta("Exiting DBConnection");
    db.terminate();
  }
}
