/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */


package gov.usgs.anss.mysql;
import gov.usgs.alarm.SendEvent;
import gov.usgs.anss.util.JDBConnectionOld;
import gov.usgs.anss.util.SimpleSMTPThread;
import gov.usgs.anss.util.Util;
import java.io.PrintStream;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;
import javax.swing.JOptionPane;

/** MySQLConnectionThreadOld.java This thread tries to keep a MySQL connection and statement
 open to a Mysql Database.  If it is unsuccessful, it tries again ever 30 seconds.
 When successful, it checks every second for the connection to close.  If it does,
 it tries to reopen it.  The user can force a reopen with the reopen() method.  As it
 is a thread a terminate() method is provided to allow the user a way to kill this thing.
 Each such MySQLConnectionThreadOld is named and it is an error to create another one of the
 same name.  Each group of Threads using one of these should first use the static method
 getThread() to see if the named thread exists and use it before attempting to create
 a new one.  In this way the returned object is always good for the life of the thread
 (though the connection may be down).  If the user always uses the executeUpdate() and
 executeQuery() methods to do the SQL statements, they are synchronized and can safely
 use the same object.
 
The intended user is one who needs to write to a Database from a thread and cannot tolerate
 the thread hanging up for long on DB stuff.

 Created on October 15, 2006, 4:05 PM
 *
 *
 * @author davidketchum
 */
  public class MySQLConnectionThreadOld extends Thread {
    private Connection C;         // A connection  from the user class
    private static final Map<String,MySQLConnectionThreadOld> list =
            Collections.synchronizedMap(new TreeMap<String,MySQLConnectionThreadOld>());
    private Statement stmt2;      // A statement in the caller class
    private String host;          // Host of the database
    private String localHostIP;
    private String database;
    private String user;
    private String password;
    private String name;
    private String vendor;
    private int activity;
    private int noconnect;        // count querys or updates blown off with no connection
    private boolean writable;
    private boolean useSSL;
    private long lastSuccess;
    private static long lastPage; // time of last page for damping
    private static boolean shuttingDown;
    private MySQLCTWatchdog watchdog;
    boolean terminate;
    private String tag;
    private int instate;
    private int queryInProgress;
    private int updateInProgress;
    private PrintStream par;
    public String getVendor(){ return vendor;}
    public void setLogPrintStream(PrintStream p) { par = p;}
    public int getInState() {return instate;}
    public void prt(String s) {if(par != null) par.println(s); else Util.prt(s);}
    public final void prta(String s) {if(par != null) par.println(Util.asctime()+" "+s); else Util.prta(s);}
    public String getDatabase() {return database;}
    public static void keepFresh() {
      if(list == null) return;
      Util.prta("Start keepFresh()");
      synchronized(list) {
        Iterator<MySQLConnectionThreadOld> itr = list.values().iterator();
        while(itr.hasNext()) {
          MySQLConnectionThreadOld thr = itr.next();
          try {
            if(!thr.vendor.equals("oracle") && !thr.vendor.equals("postgres")) thr.executeQuery("SHOW TABLES");
          }
          catch(SQLException e) {Util.prta(thr.name+" SQLError Thread keeping connection alive");}
        }
      }
      Util.prta("End keepFresh()");
    }
    /** cause this thread to terminate at is next converience.  Normally called by users shutdown()
     */
    public void terminate() {terminate=true; prt("MSQLCT terminate/remove "+name);list.remove(name); try{C.close();} catch(SQLException e){}}

    /**
   * return the MySQLThreadConnection of a given name
   * @param name Some extra text from caller to include in the message
   * @return null if named connection does not exist, else the MySQLConnectionThreadOld  of that name
   */
    public static MySQLConnectionThreadOld getThread(String name) {
      if(list == null) {Util.prt("call to MSQLCT getTHread() but list is uninitialized!"); return null; }
      else return  list.get(name);
    }
    /** Return list of names of threads
     *
     * @return The list
     */
    static final StringBuilder status = new StringBuilder(1000);
    public static String getStatus() {
      if(status.length() > 0) status.delete(0,status.length());
      Iterator<MySQLConnectionThreadOld> itr = list.values().iterator();
      while(itr.hasNext()) {
        status.append(itr.next()).append("\n");
      }
      return status.toString();
    }
    /** Return list of names of threads
     * 
     * @return The list
     */
    public static String getThreadList() {
      String result="MSQLT list :";
      Iterator<MySQLConnectionThreadOld> itr = list.values().iterator();
      while(itr.hasNext()) {
        result += itr.next().name+" ";
      }
      Iterator<String> itr2 =list.keySet().iterator();
      result += "Keys=";
      while(itr2.hasNext()) result += itr2.next()+" ";
      return result;
    }
    /** return number of times in a row that querys/updates have found no connection
     *@return The # of times in a row that the querys/updates have found no connection
     */
    public int getNoconnects(){return noconnect;}
    /** at startup the Thread may not yet have achieved a connection.  This waits up to 5 seconds
     * for a connection to be made.  I
     *@return True if the connection has been made, if false, the database is very busy or down.
     */
    public boolean waitForConnection() {
      for(int i=0; i<50; i++) {
        if(getConnection() != null) return true;
        try {sleep(100);} catch(InterruptedException e) {}
      }
      return false;
    }
    /** at startup the Thread may not yet have achieved a connection.  This waits up to 5 seconds
     * for a connection to be made.  I
     *@param name The connection name to wait for
     *@return True if the connection has been made, if false, the database is very busy or down.
     */
    public static  boolean waitForConnection(String name) {
      for(int i=0; i<50; i++) {
        if(getConnection(name) != null)
          return true;
        Util.sleep(100);
      }
      return false;
    }
    /**
   * create a thread which maintains a connection to a MySQL server and database
   * @param h The host on which MySQL is running
   * @param db The name of the desired database for this connection
   * @param u The user name to log into MySQL with
   * @param pw The password for that user
   * @param write If true, this connection and INSERT and UPDATE records
   * @param ssl if true, use SSL on this connection
   * @param nm THe name to give this connection
   * @throws InstantiationException Thrown if this connection cannot be built
   */
    public MySQLConnectionThreadOld(String h, String db, String u, String pw, boolean write,
        boolean ssl, String nm)
      throws InstantiationException {
      this(h,db,u,pw,write,ssl,nm, "mysql", null);
    }
    /**
   * create a thread which maintains a connection to a MySQL server and database
   * @param h The host on which MySQL is running
   * @param db The name of the desired database for this connection
   * @param u The user name to log into MySQL with
   * @param pw The password for that user
   * @param write If true, this connection and INSERT and UPDATE records
   * @param ssl if true, use SSL on this connection
   * @param nm THe name to give this connection
     * @param out THe printstream to use, user might call setLogPrintStream() if this changes with time
   * @throws InstantiationException Thrown if this connection cannot be built
   */
    public MySQLConnectionThreadOld(String h, String db, String u, String pw, boolean write,
        boolean ssl, String nm, PrintStream out)
      throws InstantiationException {
      this(h,db,u,pw,write,ssl,nm, "mysql", out);
    }
    /**
   * create a thread which maintains a connection to a MySQL server and database
   * @param h The host on which MySQL is running
   * @param db The name of the desired database for this connection
   * @param u The user name to log into MySQL with
   * @param pw The password for that user
   * @param write If true, this connection and INSERT and UPDATE records
   * @param ssl if true, use SSL on this connection
   * @param nm THe name to give this connection
   * @param vend The vendor for the database (mysql, oracle, postgres, ....)
   * @throws InstantiationException Thrown if this connection cannot be built
   */
    public MySQLConnectionThreadOld(String h, String db, String u, String pw, boolean write,
        boolean ssl, String nm, String vend)
      throws InstantiationException {
      this(h,db,u,pw,write,ssl,nm, vend, null);
    }
      /**
   * create a thread which maintains a connection to a MySQL server and database
   * @param h The host on which MySQL is running
   * @param db The name of the desired database for this connection
   * @param u The user name to log into MySQL with
   * @param pw The password for that user
   * @param write If true, this connection and INSERT and UPDATE records
   * @param ssl if true, use SSL on this connection
   * @param nm THe name to give this connection
   * @param vend The vendor for the database (mysql, oracle, postgres, ....)
   * @param out Set printstream to this, user might want to call setLogPrintStream if file changes.
   * @throws InstantiationException Thrown if this connection cannot be built
   */
    public MySQLConnectionThreadOld(String h, String db, String u, String pw, boolean write,
        boolean ssl, String nm, String vend, PrintStream out)
      throws InstantiationException {
      par = out;
      host = h; database=db; user=u; password=pw; writable=write; name=nm; useSSL=ssl;
      tag=name+":"+vend+":"+database+":"+user+":"+writable+":"+useSSL+":"+Util.getNode()+":"+Util.getAccount()+":"+Util.getProcess();
      try {
         localHostIP = InetAddress.getLocalHost().toString();
      }
      catch(UnknownHostException e) {}
      vendor=vend;
      prta("new ThreadMySQL attempt to get list for put!");
      synchronized(list) {
        if(list.get(name) != null)
          throw new InstantiationError("Attempt to create a new MySQLConnectionThread with name that already exists="+name);
      }
      // Register our shutdown thread with the Runtime system.
      //Runtime.getRuntime().addShutdownHook(new ShutdownMySQLConnectionThread());
      prta("new ThreadMySQL "+getName()+" "+getClass().getSimpleName()+" "+host+"/"+database+" "+user+" as "+name+" "+vendor);
      watchdog = new MySQLCTWatchdog(name, this);
      start();
    }
@Override
    public void run() {
      synchronized(list) {list.put(name,this);}
      int timer=0;
      try{sleep(1000);} catch(InterruptedException e) {}
      while(!terminate) {
        instate=1;
        try {
          if(C == null || C.isClosed()) {
            stmt2=null;
            timer++;
            prta("MySQLCTrun(): attempt connection to "+tag);
            instate=2;
            new JDBConnectionOld(host,database,user,password,useSSL,name,vendor);
            C = JDBConnectionOld.getConnection(name);
            instate=3;
            if(writable) {
              stmt2=C.createStatement(
                ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE);
              instate=4;
            }
            else {
              instate=5;
              stmt2 = C.createStatement();
              timer=0;
              instate=6;
            }
            prta("MySQLCTrun(): to "+tag+" is open.");
          }
          instate=7;
        }
        catch (SQLException e) {
          instate=8;
          prta("MySQLCTrun(): error Opening JDBConnection TO "+host+":"+database+":"+user+" "+name+" "+localHostIP);
          if(e.getMessage().indexOf("trustAnchors") >=0) {
             if(!Util.getNoInteractive())JOptionPane.showMessageDialog(null,
            "Trust store is not set up for this user.  \nPlease set it up with 'ImportCert' button or unclick 'Use SSL', if your MySQL allows it.\n",
            "TrustStore Error",JOptionPane.ERROR_MESSAGE);
             else prt("No trust anchors set up in a batch process "+tag);
             terminate=true;
             break;
          }
          else if(e.getMessage().indexOf("Access denied") >= 0) {
            prt("Access denied - terminate this connection. "+tag);
             terminate=true;
             break;
          }
          else if(e.getMessage().indexOf("Unknown database")>= 0) {
            prt("Unknown database errors - destroy this MySQLConnection "+tag);
            synchronized(list) {list.remove(name);}      // remove this connection from the list
            terminate=true;
            SimpleSMTPThread.email(Util.getProperty("emailTo"),

                "MySQL unknown database! "+tag,
                Util.ascdate()+" "+tag+"\n"+
                "MySQLConnectionThead cannot make connection to "+tag+"\n"+
                "This e-came from a Java Process using the MySQLConnectionThread when it created\n"+
                "such a thread which returned an 'Unknown database error'.\n\n"+Util.getThreadsString()+
                "\n"+MySQLConnectionThreadOld.getStatus());
            terminate=true;
            break;
          }
          instate=9;
          prta(name+ " Try to open connection again in 30 seconds. e="+e);
          Util.SQLErrorPrint(e,"MySQLCTrun() did not get thread to open "+name);
          C=null;
          stmt2=null;
          timer++;
          if(timer > 2 && System.currentTimeMillis() - lastPage > 300000) {
            prta(name+" MySQLCTrun():We cannot open this MySQLConnection send mail to dave. timer="+timer);

            SimpleSMTPThread.email(Util.getProperty("emailTo"),
                "MySQL failed to "+tag,
                Util.ascdate()+" "+Util.asctime()+" "+tag+"\n"+
                "MySQLConnectionThead cannot make connection to "+host+":"+database+"/"+user+" wr="+writable+" name="+name+
                " from "+Util.getNode()+"\n"+
                "This e-came from a Java Process using the MySQLConnectionThread to keep a connection to a \n"+
                "database open and it has failed to open for 20 minutes!\n\n"+Util.getThreadsString());
            SendEvent.edgeSMEEvent("MySQLFailed","MySQLConnection fail "+tag,"MySQLConenctionThread");
            timer=-200; 
            lastPage=System.currentTimeMillis();
          }
          try {sleep(30000);} catch(InterruptedException e2) {} 
        }

        catch(RuntimeException e) {
          instate=10;
          prta("MySQLCTrun: got a runtime error opening the connection to "+tag);
          e.printStackTrace();
          try{sleep(30000);} catch(InterruptedException e2) {}
          continue;
        }
        instate=12;
        prta("MySQLCTrun(): Enter maintenance loop "+list.get(name));
        lastSuccess = System.currentTimeMillis();
        //if(list.get(name) != null) prt("MySQLCTrun(): conn="+list.get(name).getConnection());//DEBUG
        int loop=0;
        while (C != null && !terminate) {
          instate=13;
          try{sleep(1000);} catch(InterruptedException e) {}
          try {
            if(C == null || stmt2 == null) {
              if(stmt2 != null) stmt2.close();
              if(C != null) if(!C.isClosed()) C.close();
              C=null;
              stmt2=null;
              prta("MySQLCTrun(): C or stmt2 is null.  Remake "+toString());
              break;
            }
            if(C.isClosed() ) {
              instate=14;
              if(stmt2 != null) stmt2.close();
              prta("MySQLCTrun(): connection has closed "+name);
              C=null;
              stmt2=null;
            }
            loop++;
            instate=15;
            if(loop % 60 == 0) {
              instate=16;
              if(System.currentTimeMillis() - lastSuccess > 3720000 && queryInProgress == 0 && updateInProgress == 0) {
                prta("MySQLCTrun() : keep alive  for "+tag);
                instate=17;
                if(!vendor.equals("oracle") ) {
                  // This cannot be synchronized so we cannot use stmt2, create a new statement for this purpose
                  Statement stmt = C.createStatement();
                  watchdog.arm(true, "keep alive2");
                  ResultSet rs = stmt.executeQuery("SELECT SUBSTRING('Poke "+tag+"',1,"+(tag.length()+5)+")");
                  rs.close();
                  stmt.close();
                  watchdog.arm(false, "");
                }
                lastSuccess=System.currentTimeMillis();

              }
            }
          }
          catch(SQLException e) {
            watchdog.arm(false,"");
            instate=18;
            prta("MySQLCTrun(): keep alive or close loop error "+name+" e="+e.getMessage());
            try {
              if(stmt2 != null) stmt2.close();
              if(C != null) if(!C.isClosed()) C.close();
            }
            catch(SQLException e2) {}
            C = null;
            stmt2=null;
            break;
          }
        }

        instate=19;
        prta("MySQLCTrun(): Bottom of maintenance loop "+name+" "+toString());
      }
      terminate=false;
      try {
        instate=20;
        if(stmt2 != null) stmt2.close();
        if(C != null) C.close();
      }
      catch(SQLException e) {}
      instate=21;
      prta("MySQLCTrun() has exitted. host="+tag);
      synchronized(list) {list.remove(name);}        // This name does not exist, remove it.
    }
    /**
   * return the current connection to the database. Not normally needed as user can
   * use the executeUdpate() and executeQuery methods.
   * @return A SQL connection to the database
   * @param name The name of the desired connection
   */
    public static Connection getConnection(String name) {
      if(list == null) return null;
      MySQLConnectionThreadOld t =  list.get(name);
      if(t == null) return null;
      return t.getConnection();
    }
    public Connection getConnection() {      activity++; return C;}
    /**
   * return the current statement for MySQL access.Not normally needed as user can
   * use the executeUdpate() and executeQuery methods.
   * @param nm The name of the desired thread
   * @return A MySQL/SQL statement
   */
    public static Statement getStatement(String nm) {
      MySQLConnectionThreadOld t =  list.get(nm);
      if(t == null) {
        return null;
      }
      return t.getStatement();
    }
    /** return the current statement for MySQL access.Not normally needed as user can
     * use the executeUdpate() and executeQuery methods.
     *@return A MySQL/SQL statement
     */
    public Statement getStatement() { activity++; return stmt2;}
    /** getNewStatement -
     *  @param writable - if true, set scroll and concur_updatable
     *  @throws SQLException if one is thrown by class
     * @returns the statement or null or throws a SQL Exception trying
     */
    public Statement getNewStatement(boolean writable) throws SQLException {
      try {
        if(writable) {
          return C.createStatement(
            ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE);
        }
        else {
          return C.createStatement();
        }
      }
      catch(SQLException e) {
        prta("GetNewStatement - got exception creating statement, build a new connections and try again");
        e.printStackTrace();
        closeConnection();
        waitForConnection();
        if(writable) {
          return C.createStatement(
            ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE);
        }
        else {
          return C.createStatement();
        }
      }
    }
    /**
   * This executes a query on the named MyQLConnectionThread.  It looks up the thread
   * in a static list and then uses the instance executeQuery() to perform the query.
   * @param nm The name of the MySQLConnectionThreadOld to use
   * @param sql An sql query statement
   * @return The ResultSet from the query
   * @throws SQLException after doing a reopen to clear up any problems
   */
    public static ResultSet executeQuery(String nm, String sql) throws SQLException {
      MySQLConnectionThreadOld t =  list.get(nm);
      if(t == null) {
        Util.prta("No thread with name="+nm+" quere not done="+sql);
        return null;
      }
      else return t.executeQuery(sql);
    }
    /**
   * This executes a give SQL query in this thread.  It is synchronized so that many
   * different threads can use this object to performed transactions over a single
   * MySQL connection.
   * @param sql An sql query statement
   * @return The ResultSet from the query
   * @throws SQLException after doing a reopen to clear up any problems
   */
    public synchronized ResultSet executeQuery(String sql) throws SQLException {
      activity++;
      queryInProgress=1;
      int wait=0;
      while(C == null) {try{sleep(100);} catch(InterruptedException e) {}wait++; if(wait>100) break;}
      if(wait > 1) prta(tag+"MySQLCT: update() not connected waited "+wait+" tenths");
      if(wait > 100) reopen();
      if(C == null) {
        noconnect++;
        if(System.currentTimeMillis() - lastPage > 3600000) lastPage=System.currentTimeMillis();
        if(System.currentTimeMillis() - lastPage > 300000) {
          SimpleSMTPThread.email(Util.getProperty("emailTo"),
              "MySQL hung "+tag+" as "+name+" wr="+writable+"noconnect",
              Util.ascdate()+" "+Util.asctime()+" "+Util.getNode()+"/"+Util.getProcess()+"\n"+
              "MySQLConnectionThead.executeQuery cannot make connection to "+tag+"\n"+
              "This e-came from a Java Process using the MySQLConnectionThread to do querys/updates and occurs \n"+
              "is damped to every 2 minutes.  Noconnect="+noconnect+"\n"+
              (System.currentTimeMillis()-lastPage)+" ms since last page.\n"+Util.getThreadsString());
          SendEvent.edgeSMEEvent( "MySQLHung",
              tag+" nc="+noconnect,"MySQLConnectionThread");
          lastPage = System.currentTimeMillis();
        }
        queryInProgress=20;
        throw new SQLException("This MySQLConnectionThread is not connected. "+tag);
      }
      ResultSet rs = null;
      // If we caught the thread not connected, wait for it.
      int loop =0;
      queryInProgress=2;
      if(stmt2 == null) prt(tag+"Waiting for stmt to become active "+name);
      while(stmt2 == null && loop<200) try {sleep(500);loop++;} catch(InterruptedException e) {}
      if(stmt2 == null) {queryInProgress=21;throw new SQLException(name+" MySQLConnectionThread: is not connected and has timed out on this querys="+sql);}
      for(int i=0; i<4; i++) {
        queryInProgress=3;

        try {
          watchdog.arm(true, sql);
          if(i>0) prta("MySQLCT.query() retry"+i+" "+name+" started sql="+sql);
          rs = stmt2.executeQuery(sql);
          if(i>0) prta("MySQLCT.query() retry"+i+" "+name+" successful.");
          watchdog.arm(false,"");
          if(i >= 2) SendEvent.debugEvent("MySQLCTW_OK","Try"+i+" "+tag+" on "+Util.getIDText(), this);
          queryInProgress=4;
          noconnect=0;
          lastSuccess=System.currentTimeMillis();
          break;
        }
        catch(SQLException e) {
          queryInProgress=5;
          watchdog.arm(false,"");
          if(e.getMessage().indexOf("Connection reset") >=0) prta(name+" MySQLConnectionThread.executeQuery()"+i+" : Connection reset on query try reopen sql="+sql);
          else if(e.getMessage().indexOf("EOFException") >=0) prta(name+" MySQLConnectionThread.executeQuery()"+i+" : EOF on query try reopen sql="+sql);
          else if(e.getMessage().indexOf("Communications link fail") >=0) prta(name+" MySQLConnectionThread.executeQuery()"+i+" : Communications failure reopen sql="+sql);
          else prta(Util.ascdate()+" "+tag+" SQLException MySQLThread.query()"+i+" reopen sql="+sql+" e="+e.getMessage()+"\n<EOM>\n");
          if(i > 1) SendEvent.debugEvent("MySQLCTFAIL", "Try"+i+" "+tag+" QueRetry", this);
          queryInProgress=6;
          boolean reopened = reopen();
          if(i >= 3 || !reopened) throw e;
        }
      }
      queryInProgress=0;
      return rs;
    }
    /**
   * This executes a sql update on the named MyQLConnectionThread.  It looks up the thread
   * in a static list and then uses the instance executeUpdate() to perform the update.
   * @param nm The name of the MySQLConnectionThreadOld to use
   * @param sql An sql query statement
   * @return The ResultSet from the query
   * @throws SQLException after doing a reopen to clear up any problems
   */
    public static int executeUpdate(String nm, String sql) throws SQLException {

      MySQLConnectionThreadOld t =  list.get(nm);
      if(t == null) {
        Util.prta("No thread with name="+nm+" quere not done="+sql);
        return 0;
      }
      return t.executeUpdate(sql);
    }
    /**
   * This executes a give SQL update in this thread.  It is synchronized so that many
   * different threads can use this object to performed transactions over a single
   * MySQL connection.
   * @param sql An sql query statement
   * @return The ResultSet from the query or -1 if a duplicate entry occurred or -2 if a data Truncation occurred.
   * @throws SQLException after doing a reopen to clear up any problems
   */
    public synchronized int executeUpdate(String sql) throws SQLException {
      activity++;
      updateInProgress=1;
      int wait=0;
      while(C == null) {try{sleep(100);} catch(InterruptedException e) {}wait++; if(wait>100) break;}
      if(wait > 1) prta(tag+"MySQLCT: update() not connected waited "+wait+" tenths");
      if(wait > 100) reopen();
      if(C == null) {
        noconnect++;
        if(System.currentTimeMillis() - lastPage > 120000) {
          updateInProgress=2;
          lastPage = System.currentTimeMillis();
          SimpleSMTPThread.email(Util.getProperty("emailTo"),
              "MySQL hung to "+tag+" as "+name+" wr="+writable,
              Util.ascdate()+" "+Util.asctime()+" "+tag+"\n"+
              "MySQLConnectionThead.executeUpdate cannot make connection to "+tag+"\n"+
              "This e-came from a Java Process using the MySQLConnectionThread to do querys/updates and occurs \n"+
              "is damped to ever 2 minutes.  Noconnect="+noconnect+"\n"+
              "\n"+Util.getThreadsString());
          SendEvent.edgeSMEEvent ("MySQLHung",
              tag+" nc="+noconnect,"MySQLConnectionThread");
        }
        throw new SQLException("This MySQLConnection thread is not connected. "+tag);
      }
      int ret=-1;
      for(int i=0; i<4; i++) {
        updateInProgress=3;
        try {
          watchdog.arm(true, sql);
          if(i > 0) prta("MySQLCT.update() retry"+i+" "+name+" sql="+sql);
          ret = stmt2.executeUpdate(sql);
          if(i > 1) SendEvent.debugEvent("MySQLCTW_OK","Try"+i+" "+tag+ "UpdRetryOK"+Util.getIDText(), this);
          if(i > 0) prta("MySQLCT.update() retry"+i+" "+name+" successful.");
          updateInProgress=4;
          watchdog.arm(false,"");
          lastSuccess=System.currentTimeMillis();
          noconnect=0;
          break;
        }
        catch(SQLException e) {
          updateInProgress=20;

          watchdog.arm(false,"");
          if(e.getMessage().indexOf("Data truncation") >=0) {
            prta(Util.ascdate()+" "+name+" MySQLTHread.update() SQLExcept data trunc "+e.getMessage()+"\n"+sql);
            updateInProgress=21;
            return -2;
          }
          else if(e.getMessage().indexOf("marked as crashed") >=0) {
            prta(Util.ascdate()+" "+tag+" table is marked as crashed and need repair!");
            SendEvent.edgeSMEEvent("MySQLTblBad", tag+" "+e.getMessage(), this);
            updateInProgress=22;
          }
          else if(e.getMessage().indexOf("Duplicate entry") >= 0) {
            prta(Util.ascdate()+" "+name+" MySQLThread.update() SQLExcept duplicate entry "+e.getMessage()+"sql="+sql+"\n");
            updateInProgress=23;
            return -1; 
          }
          else if(e.getMessage().indexOf("Connection reset") >= 0)
            prta(name+" MySQLConnectionThread: Connection reset on update"+i+".  reopen sql="+sql);
          else if(e.getMessage().indexOf("EOFException") >= 0) prta(name+" MySQLConnectionThread: EOF on update+"+i+".  reopen sql="+sql);
          else  prta(Util.ascdate()+" "+name+" SQLException MySQLThread.update() reopen"+i+".  e="+e.getMessage()+"sql ="+sql);
          SendEvent.debugEvent("MySQLCTWFAIL", "Try"+i+" "+tag+" MySQLCT.update", this);
          updateInProgress=5;
          boolean reopened = reopen();
          updateInProgress=6;
          // retry operation
          if(i >= 3 || !reopened) throw e;
        }
      }
      updateInProgress=0;
      return ret;
    }
    /** Cause the connection to be shutdown and closed.  Normally the user would call this
     * when a SQLStatement caused an exception (in particular for no connection) to force the
     * Thread to make a new one.
     */
    public synchronized void closeConnection() {
      try {
        if(stmt2 != null) stmt2.close();
      }catch(SQLException e) {
        prta("MySQLConnectionThread : closeCOnnection() "+name+" close gave e="+e.getMessage());
      }
      try{
        if(C != null) C.close();
      }
      catch(SQLException e) {
        prta("MySQLConnectionThread : closeConnection() "+name+" close gave e="+e.getMessage());
      }
      prta( "MySQLConnectionThread : closeConnection() "+name+" done "+toString());
      C = null;
      stmt2=null;
      this.interrupt();           // wake up the connection thread.
    }
    /** close and forget a coonnection and drop it from the list
     *
     */
    public synchronized void close() {
      prta("MySQLConnectionThread() : close() permanently close connection!");
      list.remove(name);
      terminate=true;
      closeConnection();
    }
    /** Cause the connection to be shutdown and closed.  Normally the user would call this
     * when a SQLStatement caused an exception (in particular for no connection) to force the
     * Thread to make a new one.
     * @return true if reopen was successful
     */
    public boolean reopen() {
      try {
        if(C != null) C.close();
      }
      catch(SQLException e) {
        prta("MySQLConnectionThread : reopen() "+name+" close gave e="+e.getMessage());
        
      }
      C = null;
      stmt2=null;
      this.interrupt();           // wake up the connection thread.
      Util.sleep(1000);       // give the backup loop a second to get reconnected
      prta("Wait for connection in reopen wait for "+name);
      int loop=0;
      while(loop++ < 12 && !waitForConnection()) {  // each wait is 5 seconds
        prta("MySQLConnectionThread: Reopen of "+name+" has failed after a 5 second waitForConnection "+toString());
        //prta("MySQLConnectionThreadOld: Second waitForConnection()+"+waitForConnection(name));
      }
      if(!waitForConnection()) {SendEvent.edgeSMEEvent("MySQLCTRopen","Failed "+tag, this);return false;}
      int loop2;
      for(loop2=0; loop2< 100; loop2++) {
        if(stmt2 != null) break;
        Util.sleep(100);
      }
      if(stmt2 == null) {SendEvent.edgeSMEEvent("MySQLCTRopen","Failed2 "+tag, this); return false;}
      prta("Connection "+name+" has reopened()!  loop="+loop+" loop2="+loop2+" "+toString());
      return true;
    }
    /** this shuts down all open MySQLConnections.  It should be called as part of a
     * main exit handler.  It can be called by multiple shutdowns.  Only the first one
     * will have any affect.  
     */
    static public void shutdown() {
      if(shuttingDown) return;
      shuttingDown=true;
      Util.prta("MySQLConnThr:shutdown() started "+list.values().size());
      Object [] objs = list.values().toArray();
      for(int i=0; i<objs.length; i++) {
        MySQLConnectionThreadOld t = (MySQLConnectionThreadOld) objs[i];
        t.terminate();
        t.interrupt();
        Util.prt("MySQLConnectionThr:shutdown() "+t+" is being terminated.");
     }
     /** this appears more correct but often gave a Concurrent modification exception! */
      /*synchronized(list) {
        Iterator<MySQLConnectionThread> itr = list.values().iterator();
        while(itr.hasNext()) {
          //try {
            MySQLConnectionThreadOld t = itr.next();
            t.terminate();
            t.interrupt();
          /*}
          catch(RuntimeException e) {
            prt("MySQLConnectionThreadOld shutdown e="+e);
            break;
          }
        }
      }*/
      Util.prt("MySQLConnectionThr:shutdown() is down.");
    }
  /** return a string describing this thread
   *@return The string
   */
  @Override
  public String toString() {
    String closed="";
    try {
      if(C != null)
        if(C.isClosed()) closed="Closed";
    }
    catch(SQLException e) {closed="Closed error";}
    return
      "MySQLCT:"+host+":"+database+"/"+user+" #nocon="+noconnect+" wr?="+writable+" state="+instate+" "+
      (System.currentTimeMillis() - lastSuccess)/1000+"s query="+queryInProgress+" upd="+
      updateInProgress+" as "+name+" "+(C == null?" no connection":closed)+(stmt2==null?" No statement":"");
  }
  private class ShutdownMySQLConnectionThread extends Thread {
    public ShutdownMySQLConnectionThread() {
      gov.usgs.anss.util.Util.prta("new ThreadShutdown "+getName()+" "+getClass().getSimpleName());
      
    }
    
    /** this is called by the Runtime.shutdown during the shutdown sequence
     *cause all cleanup actions to occur
     */
    
    @Override
    public void run() {
      terminate=true;
      int lactivity=-1;
      int loop=0;
      while(activity - lactivity > 0 && loop++ <30) {
        prta("MySQLConnection shutdown activity="+activity);
        lactivity=activity;
        try{sleep(10000);} catch(InterruptedException e) {}
        
      }
      prt(name+" MySQLConnThr: no activity for 10 seconds - finish shutdown"+activity);
      interrupt();
      prta(name+" MySQLConnThr: Shutdown started");
      int nloop=0;

       prta(name+" MySQLConnThr:Shutdown Done.");
     
    }
  }

   class MySQLCTWatchdog extends Thread {
    boolean armmed;
    int loop=0;
    String tag;
    String message;
    MySQLConnectionThreadOld s;
    public void arm(boolean t, String msg) {armmed=t; message=msg; loop=0;}
    public MySQLCTWatchdog(String tg, MySQLConnectionThreadOld s2) {
      s = s2;
      tag=tg;
      start();
    }
    @Override
    public void run() {
      while(!terminate && s.isAlive()) {
        try{sleep(10000);} catch(InterruptedException e) {}
        if(armmed) {
          loop++;
          if(loop % 30 == 0) {
            prta(tag+" MySQLCT: ***** Watchdog has gone off.  force a reopen"+message+" loop="+loop+" state="+s.getInState());
            if( (loop/12) > 1)
              SendEvent.edgeSMEEvent("MySQLCTWdog", tag+(loop/12)+" FAILURE! On "+Util.getIDText()+" "+message, "MySQLConnectionThread");
            else
              SendEvent.debugEvent("MySQLCTWdog", tag+(loop/12)+" WARN On "+Util.getIDText()+" "+message, "MySQLConnectionThread");
            s.closeConnection();
          }
        }
      }
      prta("MySQLMsgSrv exiting - call exit()");
    }
  }
  public static void main(String [] args) {
    Util.init();
    Util.setNoInteractive(true);
    MySQLConnectionThreadOld  mysql = MySQLConnectionThreadOld.getThread("HoldingStatus");
    if(mysql == null) {
      try {
        mysql = new MySQLConnectionThreadOld("gldketchum", "status","ketchum3",
          "paully",true,false,"HoldingStatus");
        for(int i=0; i<50; i++) {
          if(mysql.getConnection() != null) {Util.prt("connection opened at i="+i);break;}
          try{Thread.sleep(100);} catch(InterruptedException e2) {}
        }
      }
      catch(InstantiationException e ) {
        Util.prt("Instantiation error on status db impossible");
        mysql = MySQLConnectionThreadOld.getThread("HoldingStatus");
      }
    }    
    for(int i=0; i<10200; i++) {
      try{Thread.sleep(1000l);} catch(InterruptedException e) {}
      //prta("Count="+i);
    }
    Util.prta("Exiting MySQLConnection");
    mysql.terminate();
  }
}
  
 