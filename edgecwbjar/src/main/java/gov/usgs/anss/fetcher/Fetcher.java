/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.fetcher;

import gov.usgs.alarm.SendEvent;
import gov.usgs.anss.db.DBConnectionThread;
import gov.usgs.anss.edge.FetchList;
import gov.usgs.anss.edge.IllegalSeednameException;
import gov.usgs.anss.edgemom.EdgeChannelServer;
import gov.usgs.edge.config.Channel;
import gov.usgs.anss.edge.RunList;
import gov.usgs.anss. edge.Run;
import gov.usgs.anss.net.HoldingArray;
import gov.usgs.anss.channelstatus.LatencyClient;
import gov.usgs.anss.seed.MiniSeed;
import gov.usgs.anss.channelstatus.ChannelStatus;
import gov.usgs.anss.cwbqueryclient.EdgeQueryClient;
import gov.usgs.anss.util.FUtil;
import gov.usgs.anss.util.SeedUtil;
import gov.usgs.anss.util.Util;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.RandomAccessFile;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.Socket;
import java.net.UnknownHostException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.concurrent.Semaphore;

/**
 * This class represents the common functions of doing a fetch. There are really two modes supported
 * here the main one is able to : 1) Build a fetch list from the edge.fetchlist table given start
 * and end times, seedname,and type 2) Process each fetch using generally the subclass fetch method,
 * 3) Set the fetchlist entry status based on the data returned and build any new gaps, 4) Put the
 * data into the target CWB optionally discarding packets for holdings
 * <p>
 * The other mode is the single fetch mode (-1 mode). In this case the command line arguments
 * contain the full information to do a single fetch (seedname, start time, duration, type) and in
 * this mode the thread is not started as this would put the data into the CWB. So, when using the
 * main() in this class the user should either use -f to set a filename to be written, or if -f is
 * not set, the returned data is processed to cwb/edge so those ips and options should be set.
 * <p>
 * If the main() is not being used and a single mode is set up : 1) To get data directly call
 * getData(getFetchList().get(0)) directly if processing the results is desired, or 2) call
 * startit() to cause this single fetch to be done and processed to edge and CWB.
 * <p>  
 * Subclasses can optionally set the exception variable if they have to bail out so that the run()
 * process can return exception errors
 * <p>
 * If the subclass calls doDynamicThrottle() after each block or group of bytes, then the speed on
 * the fetch link will be limited to available bandwidth based on current data latency or the
 * maximum bandwidth allowed (-throttle). User either -throttle or setup a latency server. This
 * class waits the minimum amount of time for the number of bytes, and then evaluates if the latency
 * indicates the band width should be reduced or increased.
 * <p>
 * If the subclass calls waitForLatency() or waitForChangedLatency() then the process is suspended
 * if the latency is above the latencyWaitMin value (1/2 of latencySignificant (-latlevel). If the
 * -npermit is used stations which have recently been in a latency wait must obtain a semaphore
 * restricting the number of station that can be fetching simultaneously but having latency waits.
 * This feature was put in for Earthquake Early Warning centers that wish to limit the number of
 * station that were latent due to fetching to a small number.
 * <p>
 * The subclass should increment connectFails each time it appears the resource is not answering do
 * to connection resets or connection timed out to allow the user to decide to abort the thread as
 * hopeless. it should also increment nsuccess for each query getting a positive result.
 * <p>
 * The idea is to isolate the protocol related to doing a fetch in the subclasses, but implement the
 * the process of doing fetches and the resulting book keeping in this class.
 * <PRE>
 * Switch     Value     Description
 * -1                 Sets single request mode
 * -dbg               More output when set
 * -b44dbg            More output from the Baler44Request module.
 * -s      NSCL       The NNSSSSSCCCLL fixed field seedname
 * -b      datetime   yyyy/mm/dd hh:mm:ss.mmm
 * -d      secs       The duration in seconds
 * -t      Gaptype    Two letter gap type
 * -h      host.ip    Of service being used to get the data
 * -p      port       Port number of service to get the data
 * -edgeip edge.ip    The IP address to insert the data if within the 10 day window
 * -edgeport port     The port on the edgenode for recent data
 * -cwbip  CWB.IP     The IP address of the CWB to get the data if outside of the 10 day window.
 * -cwbport port      The port on the CWB to insert the data
 *
 *             Dynamic Throttle switches
 * -throttle kbps     Bits per second to allow on fetches (subclasses would have to call dynamicThrotte()
 * -latip Ip.add      If a latencyServer thread running in an alarm of UdpChannel instance, used with throttling
 * -latport port      The port of the latency server (def=7956)
 * -nooutput          If present, any received data is NOT put into the Edge or CWB ports.
 *
 *            Database mode (when doing multiple fetches from a database tables of fetchlist entries) :
 * -db     dbname     The database name with the data
 * -table  tablename  The table within the database to use for getting fetches
 * -edgeip
 * -e      date/time  yyyy/mm/dd hh:mm:ss If set, database reads will not include fetch start times after this date.
 *            Other switches :
 * -localbaler        If set the data is coming from a local baler and lots of stuff is overridden in this mode
 * -npermit           The number of fetchers that can be getting data simultaneously if they have had to wait for latency recently.
 *                    Default is unlimited number of stations can be latent simultaneously.
 * -latlevel  nnn     Number of seconds of latency considered significant for dropping throttle rate (def=10).  1/2 of this is
 *                    the number of seconds that the waitForLatency() requires the data to drop below before continuing.
 * </PRE>
 *
 * @author davidketchum
 */
abstract public class Fetcher extends Thread {

  private static final DecimalFormat df3 = new DecimalFormat("000");
  private static LatencyClient latencyClient;
  protected static PrintStream console;
  public static boolean shuttingDown;
  private final ArrayList<String> haveSemaphores = new ArrayList<>(10);
  private final ArrayList<FetchList> fetchlist;
  /*  Not that the dbconnedge and stmt are shared by all Fetchers.  Any use of these for database
   * access must be surrounded by a  synchronized(mutex) to insure only one thread uses them
   * at a time
   */
  protected static final Integer edgedbMutex = Util.nextMutex();    // Mutex for controlling database access
  protected static final Integer mutexFetchlist = Util.nextMutex();// Mutex for fetchlist DB access
  protected static final Integer statusMutex = Util.nextMutex();   // Mutex for status acces
  protected static long mutexElapse;
  //protected static boolean fullySerial;
  protected static DBConnectionThread dbconnedge;             // database thread for edge access
  protected static DBConnectionThread dbconnstatus;
  protected static DBConnectionThread dbconnfetchlist;        // database connection for fetchlist only
  private static DBConnectionMonitor dbMonitor;
  protected static Semaphore semaphores;         // Mutex for forcing only a limit number of simultaneous fetchers to be using the network at a time
  protected static int npermit = 0;                      // number of semaphores (simultaneous latency affected fetchers)
  protected boolean mutexdbg = false;
  //protected static Statement stmt;                          // writeable statement for updates
  private ShutdownFetcher shutdown;
  private boolean ignoreHoldings;
  private OutputStream out;
  private OutputStream outCWB;
  private Exception exception;
  private String writeDataPath;
  protected String fetchServer;
  protected String statusServer;
  private String table;
  protected String cwbIP;
  protected String edgeIP;
  protected int cwbPort;
  protected int edgePort;
  protected boolean chkCWBFirst;          // if set, the extender should check the output CWB first
  private int fetchesDone;
  //private String gaptype;   // Gap type
  private int ID;           // ID in the request station table
  protected int connectFails;
  protected int nsuccess;
  private boolean nooutput;
  private boolean dbg;
  protected String tag;
  // Throttle and feedback variables
  protected int throttleUpper;
  protected long lastThrottleCheck;
  protected int throttle;           // the current throttle value <= throttleUpper
  private int throttleBytes;
  private double latencySignificant = 10;
  private int lastLatency;
  private long lastLatencyCheck;

  // variable used by waitForLatency and waitForChangingLatency to insure latency goes down and a limited number of fetchers are in that state
  protected double latencyWaitMin = 5.;
  protected long lastLatencyWait;                   // epoch time of the last time this thread waited for latency to decline
  protected boolean haveSemaphore;
  protected double lastLat = Double.MIN_VALUE;      // For wait for changing latency keep track of the last one we had.

  // parameters not used by fetcher, but possible used by clients
  private double duration;
  private Timestamp starttime;
  private Timestamp endtime;
  protected String host;
  protected int port;
  protected String seedname;
  protected String channelRE;
  protected String orgSeedname;
  private String type;
  private Timestamp disableUntil;
  private int edgeDays = 10;
  // Internal variables
  private Socket sockcwb, sockedge;
  private long lastReadRequestStation;    // prevent reading request database too often.
  protected boolean singleRequest;
  protected String latencyServer;
  protected int latencyPort;
  protected boolean terminate;
  private String logpath = "log";
  protected boolean localBaler;
  private long freshTime;
  boolean IOErrorDuringFetch;
  private StringBuilder tmpch = new StringBuilder(20);

  // The list of channels is maintained statically so they can be shared across all Fetcher threads
  private static final Integer chansMutex = Util.nextMutex(); // Mutex for coordinating changes to chans array
  private static Object[] chans;              // The chans
  private static long lastChansRead;          // track last time chans loaded
  private final ArrayList<String> chanList = new ArrayList<>(10);
  private long lastChanScan;                  // control getChannel refresh rate
  private ChannelStatus retChannelStatus = null;

  @Override
  public String toString() {
    return tag + this.getClass().getSimpleName() + " " + seedname + " "
            + Util.rightPad(host + "/" + port + " " + isAlive(), 26)
            + " #fetch done=" + fetchesDone + " of " + fetchlist.size() + " #success=" + nsuccess + " sema=" + haveSemaphore
            + (isZombie() ? " ** Zombie? " + (System.currentTimeMillis() - terminateTime) / 1000 : "");
  }

  // Simple getters.
  public PrintStream getLog() {
    if (log == null) {
      return Util.getOutput();
    } else {
      return log;
    }
  }

  public final String getSeedname() {
    return seedname;
  }

  public Timestamp getStarttime() {
    return starttime;
  }

  public Timestamp getEndtime() {
    return endtime;
  }

  public ArrayList<FetchList> getFetchList() {
    return fetchlist;
  }

  public double getDuration() {
    return duration;
  }

  public String getHost() {
    return host;
  }

  public int getPort() {
    return port;
  }

  public final String getType() {
    return type;
  }

  public Exception getException() {
    return exception;
  }

  public String getFetchServer() {
    return fetchServer;
  }

  public boolean isSingleRequest() {
    return singleRequest;
  }

  public int getConnectFails() {
    return connectFails;
  }

  public int nsuccessful() {
    return nsuccess;
  }

  public int getFetchesDone() {
    return fetchesDone;
  }
  protected PrintStream log;
  private long terminateTime;

  public final void prt(String s) {
    if (log == null) {
      Util.prt(s);
    } else {
      log.println(s);
    }
  }

  public final void prta(String s) {
    if (log == null) {
      Util.prta(s);
    } else {
      log.println(Util.asctime2() + " " + s);
    }
  }

  public void prt(StringBuilder s) {
    if (log == null) {
      Util.prt(s);
    } else {
      log.println(s);
    }
  }

  public void prta(StringBuilder s) {
    if (log == null) {
      Util.prta(s);
    } else {
      log.println(Util.asctime2() + " " + s);
    }
  }

  public PrintStream getPrintStream() {
    if (log == null) {
      return Util.getOutput();
    } else {
      return log;
    }
  }

  public void terminate() {
    terminateTime = System.currentTimeMillis();
    prta(seedname + " has been set to terminate!");
    new RuntimeException(seedname+" set to terminate").printStackTrace(log);
    terminate = true;
    interrupt();
  }

  public long getTerminateTime() {
    return terminateTime;
  }

  public boolean isZombie() {
    return (terminate == true && isAlive() && System.currentTimeMillis() - terminateTime > 120000);
  }

  public double getLatencySignificant() {
    return latencySignificant;
  }

  public static void setNpermit(int i) {
    npermit = i;
    semaphores = new Semaphore(npermit);
  }

  abstract public void closeCleanup();

  /**
   * each subclass needs to define its own getData method which does what ever physical I/O is
   * needed to get some mini-seed blocks.
   *
   * @param f A FetchList object with the desired fetch
   * @return If null no data is returned and no data is possible, if not, return the blocks which
   * satisfy If a fetch desires to return no data, but not mark the fetch as nodata then it should
   * return a ArrayList of size() 0.
   * @throws IOException If the subclass has an IOException processing the request.
   */
  abstract public ArrayList<MiniSeed> getData(FetchList f) throws IOException;

  /**
   * Put any code in startup() that needs to be executed at the start of the run(), but which might
   * take too long to make part of the constructor (Baler turnons is an example - it might take
   * several minutes to fail.
   */
  abstract public void startup();

  public Fetcher(String[] args) throws SQLException {
    host = "140.247.18.162";
    dbg = false;
    Util.setModeGMT();
    Util.setNoInteractive(true);
    //String host = "192.168.1.102";
    port = 4003;
    starttime = FUtil.stringToTimestamp("2006-10-01 12:00:00");
    endtime = null;
    duration = 3600.;
    seedname = "";
    fetchServer = Util.getProperty("DBServer");
    statusServer = Util.getProperty("StatusDBServer");
    cwbIP = "null";
    edgeIP = "null";
    cwbPort = 2062;
    edgePort = 7974;
    type = "";
    table = "fetcher.fetchlist";
    throttleUpper = 2000;
    singleRequest = false;
    tag = "";
    latencyServer = Util.getProperty("StatusServer").split("[/:]")[0];

    //gaptype = "";
    latencyPort = 7956;
    localBaler = false;
    String argline = "";
    channelRE = "";
    for (String arg : args) {
      argline += arg + " ";
    }
    for (int i = 0; i < args.length; i++) {
      //Util.prt(i+" "+args[i]+" "+(i<args.length-2?args[i+1]:""));
      switch (args[i]) {
        case "-h":
          host = args[i + 1];
          i++;
          break;
        case "-dbg":
          dbg = true;
          break;
        case "-p":
          port = Integer.parseInt(args[i + 1]);
          i++;
          break;
        case "-cwbport":
          cwbPort = Integer.parseInt(args[i + 1]);
          i++;
          break;
        case "-edgeport":
          edgePort = Integer.parseInt(args[i + 1]);
          i++;
          break;
        case "-cwbip":
          cwbIP = args[i + 1];
          i++;
          break;
        case "-chkcwb":
          chkCWBFirst = true;
          break;
        case "-edgeip":
          edgeIP = args[i + 1];
          i++;
          break;
        case "-edgedays":
          edgeDays = Integer.parseInt(args[i + 1]);
          i++;
          break;
        case "-throttle":
          throttleUpper = Integer.parseInt(args[i + 1]);
          i++;
          break;
        case "-latip":
          latencyServer = args[i + 1];
          i++;
          break;
        case "-logpath":
          logpath = args[i + 1];
          i++;
          break;
        case "-latport":
          latencyPort = Integer.parseInt(args[i + 1]);
          i++;
          break;
        case "-latlevel":
          latencySignificant = Double.parseDouble(args[i + 1]);
          latencyWaitMin = latencySignificant / 2.;
          i++;
          break;
        case "-nooutput":
          nooutput = true;
          break;
        case "-ignoreholdings":
          ignoreHoldings = true;
          break;
        case "-ID":
          ID = Integer.parseInt(args[i + 1]);
          i++;
          break;
        case "-localbaler":
          localBaler = true;
          break;
        case "-localcwb":
          localBaler = true;
          break;
        case "-b": {
          String s = args[i + 1].replaceAll("/", "-") + " " + args[i + 2];
          starttime = FUtil.stringToTimestamp(s);
          i = i + 2;
          break;
        }
        case "-e": {
          String s = args[i + 1].replaceAll("/", "-") + " " + args[i + 2];
          endtime = FUtil.stringToTimestamp(s);
          //prt("endtime="+args[i+1]);
          i = i + 2;
          break;
        }
        case "-d":
          duration = Double.parseDouble(args[i + 1]);
          i++;
          break;
        case "-s":
          seedname = (args[i + 1] + "             ").substring(0, 12).replaceAll("-", " ");
          i++;
          break;
        case "-chanre":
          channelRE = args[i + 1].replaceAll("-", " ");
          i++;
          break;
        case "-db":
          fetchServer = args[i + 1];
          i++;
          break;
        case "-table":
          table = args[i + 1];
          prt("Override table=" + table);
          i++;
          break;
        case "-t":
          type = args[i + 1];
          i++;
          break;
        case "-1":
          singleRequest = true;
          break;
        // Statre is not used internally
        case "-statre":
          orgSeedname = args[i + 1];
          i++;
          break;
        case "-f":
          i++;          // Filenames not used internally
          break;
        case "-c":
          i++;          // Class name not used internally
          break;
        case "-instance":
          i++;
          break;
        case "-tag":
          tag = args[i + 1];
          i++;
          break;
        case "-savefiles":
          writeDataPath = args[i + 1];
          i++;
          break;
        case "-npermit":
          i++;
          break;
        case "-mutexdbg":
          mutexdbg = true;
          break;
        default:
          if ((i != 0 || args[i].charAt(0) == '-') && !args[i].equals("GP") && !args[i].equals("IS")) {
            prta(tag + "Fetch" + getName().substring(getName().indexOf("-")) + ":" + " i=" + i + " *** Unknown option=" + args[i]);
          }
          break;
      }
    }
    //if(npermit > 0 && semaphores == null) semaphores = new Semaphore(npermit, true);

    if (!tag.equals("")) {
      tag += getName().substring(getName().indexOf("-")) + ":";
    } else {
      tag += "Fetch" + getName().substring(getName().indexOf("-")) + ":";
    }
    /**
     */
    throttle = throttleUpper;

    // This mode is generally used for testing new ones that are not desired to
    // send data to the CWB and edge.
    fetchlist = new ArrayList<>(10);
    if (singleRequest) {
      prt("Single request mode for " + seedname + " " + starttime + " " + duration + " " + type);
      FetchList fetch = new FetchList(seedname, starttime, 0, duration, type, "open");
      fetchlist.add(fetch);
      return;
    }
    dbconnedge = DBConnectionThread.getThread("edge");
    if (dbconnedge == null) {
      synchronized (edgedbMutex) {
        if (mutexdbg) {
          prta(tag + "Got Mutex1");
        }
        try {
          dbconnedge = new DBConnectionThread(fetchServer, "update", "edge", true, false, "edge", (log != null ? log : Util.getOutput()));
          //"fetcher"+getName().substring(getName().indexOf("-")));
          if (!dbconnedge.waitForConnection()) {
            if (!dbconnedge.waitForConnection()) {
              if (!dbconnedge.waitForConnection()) {
                prt(tag + " **** Could not connect to database " + fetchServer);
                Util.exit(1);
              }
            }
          }
          //stmt = dbconnedge.getConnection().createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE);
          freshTime = System.currentTimeMillis();

        } catch (InstantiationException e) {
          prta(tag + " **** Impossible Instantiation exception e=" + e);
          Util.exit(0);
        }
        try {
          dbconnfetchlist = new DBConnectionThread(fetchServer, "update", "edge", true, false, "fetchlist", (log != null ? log : Util.getOutput()));
          //"fetcher"+getName().substring(getName().indexOf("-")));
          if (!dbconnfetchlist.waitForConnection()) {
            if (!dbconnfetchlist.waitForConnection()) {
              if (!dbconnfetchlist.waitForConnection()) {
                prt(tag + " **** Could not connect to database fetchlist " + fetchServer);
                Util.exit(1);
              }
            }
          }
          //stmt = dbconnedge.getConnection().createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE);
          freshTime = System.currentTimeMillis();

        } catch (InstantiationException e) {
          prta(tag + " **** Impossible Instantiation exception e=" + e);
          Util.exit(0);
        }
        dbconnstatus = DBConnectionThread.getThread("status");
        if (dbconnstatus == null) {
          try {
            dbconnstatus = new DBConnectionThread(statusServer, "update", "status", true, false, "status", (log != null ? log : Util.getOutput()));
            //"fetcher"+getName().substring(getName().indexOf("-")));
            if (!dbconnstatus.waitForConnection()) {
              if (!dbconnstatus.waitForConnection()) {
                if (!dbconnstatus.waitForConnection()) {
                  prt(tag + " **** Could not connect to database " + statusServer);
                  Util.exit(1);
                }
              }
            }
            //stmt = dbconnstatus.getConnection().createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE);
            freshTime = System.currentTimeMillis();

          } catch (InstantiationException e) {
            prta(tag + " **** Impossible Instantiation exception e=" + e);
            Util.exit(0);
          }
        }
        dbMonitor = new DBConnectionMonitor();
        if (mutexdbg) {
          prta(tag + "Rel Mutex1");
        }      
      }
    }

    // This is not single request mode, the requests are driven by the fetchlist and databases
    if (seedname.equals("") && type.equals("")) {
      prt(" **** Neither a Seedname nor a gap type.  This fetch is ill formed!");
      Util.exit(0);
    }
    // We must be in fetchlist mode, connect to the database and build the fetchlist

    dbconnedge = DBConnectionThread.getThread("edge");
    if (dbconnedge == null) {
      synchronized (edgedbMutex) {
        if (mutexdbg) {
          prta(tag + "Got Mutex2");
        }        
        try {
          dbconnedge = new DBConnectionThread(fetchServer, "update", "edge", true, false, "edge", (log != null ? log : Util.getOutput()));
          //"fetcher"+getName().substring(getName().indexOf("-")));
          if (!dbconnedge.waitForConnection()) {
            if (!dbconnedge.waitForConnection()) {
              if (!dbconnedge.waitForConnection()) {
                prt(tag + " **** Could not connect to database " + fetchServer);
                Util.exit(1);
              }
            }
          }
          //stmt = dbconnedge.getConnection().createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE);
        } catch (InstantiationException e) {
          prta(tag + " **** Impossible Instantiation exception e=" + e);
          Util.exit(0);
        }
        try {
          dbconnfetchlist = new DBConnectionThread(fetchServer, "update", "edge", true, false, "fetchlist", (log != null ? log : Util.getOutput()));
          //"fetcher"+getName().substring(getName().indexOf("-")));
          if (!dbconnfetchlist.waitForConnection()) {
            if (!dbconnfetchlist.waitForConnection()) {
              if (!dbconnfetchlist.waitForConnection()) {
                prt(tag + " **** Could not connect to database fetchlist " + fetchServer);
                Util.exit(1);
              }
            }
          }
          //stmt = dbconnedge.getConnection().createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE);
        } catch (InstantiationException e) {
          prta(tag + " **** Impossible Instantiation exception e=" + e);
          Util.exit(0);
        }        
        if (mutexdbg) {
          prta(tag + "Rel Mutex2");
        }      
      }

    }
    if (ID > 0) {
      readRequestStation();
    }
    checkNewDay();      // force log file open BAD IDEA seed name is not yet set
    prt(tag + " latencyServer=" + latencyServer + " level=" + latencySignificant + " table=" + table + " Fetcher argline=" + argline);
    prt(tag + " edgedays=" + edgeDays + " savePath=" + writeDataPath + " lat=" + latencyServer + "/" + latencyPort + "/" + latencySignificant + " npermit=" + npermit + " noout=" + nooutput);
    throttle = throttleUpper;
  }

  /**
   * startit() starts the thread. It should be used when a single fetch has been built with the -1
   * option and the program desires this module to do the fetch (call getData()) and process the
   * results to the CWB and edge. Do not call this routine if processing of the fetch is desired to
   * be done by the originator. In that case call getData() and process the ArrayList of MiniSeed.
   */
  public void startit() {
    this.setDaemon(true);
    start();
  }
  GregorianCalendar nowday = new GregorianCalendar();
  int lastdoy;

  protected final void checkNewDay() {
    nowday.setTimeInMillis(System.currentTimeMillis());
    int doy = nowday.get(Calendar.DAY_OF_YEAR);
    int year = nowday.get(Calendar.YEAR);
    if (doy != lastdoy) {
      lastdoy = doy;
      try {
        String name = (seedname + "             ").substring(0, 12);
        if (name.contains("[")) {
          name = name.substring(0, name.indexOf("["));
        }
        if (name.contains("|")) {
          name = name.substring(0, name.indexOf("|"));
        }
        if (channelRE.contains("029")) {
          name = name.trim();
        }
        if (channelRE.contains("H19")) {
          name = name.trim() + "10";
        }
        String file = logpath + "/" + type + "_" + year + "_" + df3.format(doy) + ".log";
        //prta("New day file seedname="+seedname+" name="+name);
        if (seedname.equals("")) {
          nowday.getTimeInMillis();  // do nothing intentionally
        } else if (localBaler) {
          file = logpath + "/" + orgSeedname.replaceAll("\\.", "").replaceAll("$", "").trim() + "_" + year + "_" + df3.format(doy) + ".log";
        } else if (name.startsWith("^....")) ; 
        else {
          file = logpath + "/" + name.substring(0,7).replaceAll("[ .$]", "") + "_" + year + "_" + df3.format(doy) + ".log";
        }
        boolean done = false;
        char append = 'a';
        prta("New day detected file=" + file + " seedname=" + seedname + " org=" + orgSeedname);
        while (!done) {
          File f = new File(file);
          if (f.exists()) {
            file = file.replace(append + ".", ".");
            append = (char) (((int) append) + 1);
            file = file.replace(".", append + ".");
          } else {
            done = true;
          }
        }
        prta(Util.ascdate() + " New day found. Create new log stream. " + file);
        if (log != null) {
          log.close();
        }
        log = new PrintStream(file);
        prta(Util.ascdate() + " open log file " + name + " ID=" + ID);
      } catch (FileNotFoundException e) {
        prta("**** Could not open PrintStream is the path bad? e=" + e);
        Util.exit(1);
      }
    }
  }
  protected RunList gapPresentRunList = new RunList(10);

  /**
   * Given a fetch list entry, try to see if its already in the CWB.
   *
   * @param fetch the fetchlist entry
   * @param cwbip The cwb which migh have the data
   * @param cwbport the CWBport if zero, 2061.
   * @return True if there is a gap in the fetchlist interval for one or more channels matching the
   * seedname.
   */
  private boolean isGapPresent(FetchList fetch, String cwbip, int cwbport) {
    gapPresentRunList.clear();
    if (cwbport == 0) {
      cwbport = 2061;
    }
    long start = fetch.getStart().getTime() / 1000 * 1000 + fetch.getStartMS();
    String s = "-h " + cwbip + " -s " + fetch.getSeedname().replaceAll(" ", "-").replaceAll("\\?", ".")
            + " -b " + Util.ascdatetime2(start).toString().replaceAll(" ", "-")
            + " -d " + fetch.getDuration() + " -uf -t null";
    //prta("isGapPresent: "+s);
    ArrayList<ArrayList<MiniSeed>> mslist = EdgeQueryClient.query(s); // Some data over the fetch interval
    if (mslist == null) {
      //prta("isGapPresent: No data found (null) - do fetch");
      return true;             // nothing returned, its a gap
    }
    if (mslist.isEmpty()) {
      //prta("isGapPresent: No data found - do fetch");
      return true;           // nothing returned, its a gap
    }
    for (ArrayList<MiniSeed> mss : mslist) {     // for each channel returned add it to the RunList
      for (int j = 0; j < mss.size(); j++) {
        if (mss.get(j) != null) {
          gapPresentRunList.add(mss.get(j));

          if (mss.get(j).getBuf()[7] == '~') {
            mss.set(j, null);     // This block was in outputCWB, do not include it
          }
        }
      }
      EdgeQueryClient.freeBlocks(mss, "Fetch", null);   // Free up blocks from the query
    }
    gapPresentRunList.consolidate();
    ArrayList<RunList> list = gapPresentRunList.getRunListByChannel();
    int isOK = 0;
    prt(gapPresentRunList.toStringFull());
    for (RunList rlist : list) {    // For each channel returned.
      for (Run run : rlist.getRuns()) {
        if (run.getStart() <= start + 500 && run.getEnd() >= start + (long) (fetch.getDuration() * 1000. -500)) {
          prt("isGapPresent:"+fetch.toString()+" "+Util.ascdatetime2(run.getStart())+" - "+Util.ascdatetime2(run.getEnd()));
          isOK++;
        }
      }
    }
    //prta("isGapPresent: isOK="+isOK+" list.size() ="+list.size()+" do fetch = "+!(isOK == list.size()));
    return isOK == 0;
  }

  @Override
  public void run() {
    shutdown = new ShutdownFetcher(this);     // startup clean routine
    Runtime.getRuntime().addShutdownHook(shutdown);
    HoldingArray ha = new HoldingArray();
    ha.setDeferUpdate(true);
    RunList runlist = new RunList(100);
    startup();  //DEBUG
    String oldSelect = "";
    int nzeroFetch = 0;
    long noFetchFound = 30000L;

    long elapse = 0;
    double totalElapse = 0.;
    Timestamp fetchstart = new Timestamp(100000L);
    int nquery = (int) (Math.random() * 30.);
    int lastNFetch = -1;
    int nskipNFetch =0;
    int orgSize=0;
    try {
      for (;;) {
        if (terminate) {
          break;
        }
        checkNewDay();
        if (!singleRequest) {
          if (fetchlist.size() > 0) {
            fetchlist.clear();     // remove old fetchlist
          }
        }
        checkNewDay();
        if (ID != 0) {
          readRequestStation();
        }
        if (disableUntil != null) {
          int cnt = 0;
          while (disableUntil.getTime() > System.currentTimeMillis()) {
            if (cnt++ % 60 == 1) {
              prta(tag + " Sleeping until " + disableUntil + " passes.");
            }
            try {
              sleep(60000);
            } catch (InterruptedException expected) {
            }
            if (ID != 0) {
              readRequestStation();
            }
          }
        }

        fetchstart.setTime(starttime.getTime());
        if (System.currentTimeMillis() - freshTime > 1200000) {
          synchronized (edgedbMutex) {
            if (mutexdbg) {
              prta(tag + "Got Mutex5");
            }
            try {
              dbconnedge.executeQuery("SELECT * FROM edge.version");
              freshTime = System.currentTimeMillis();
              //if(stmt != null) try {stmt.close();} catch(SQLException expected) {}
              //stmt = dbconnedge.getConnection().createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE);
            } catch (SQLException e) {
              Util.SQLErrorPrint(e, "SQLError trying to build fresh statement ", log);
              e.printStackTrace(log);

            }
            if (mutexdbg) {
              prta(tag + "Rel Mutex5"); 
            }
          }
        }        
        String select = makeFetchList(fetchstart);
        if(terminate) {
          break;
        }
        if (fetchlist.isEmpty()) {
          releaseSemaphoreForced();       // Fetch is empty, always release any semaphore
          //if ((nzeroFetch++ % 12) == 1) {
            prta(tag + " No new fetches found.  Wait.....  elapse=" + elapse
                    + " tot=" + totalElapse + " avg=" + (totalElapse / nquery) + " " 
                    + (noFetchFound / 1000) + " " + nzeroFetch + " sel=" + select);
          //}
          if (localBaler) {
            terminate = true;
            break;
          }
          try {
            sleep(noFetchFound);
          } catch (InterruptedException expected) {
          }
          noFetchFound = 2 * noFetchFound;
          if (noFetchFound > 120000) {
            noFetchFound = 120000;
          }
          if (terminate) {
            break;
          }

        }
        // Fetchlist is not empty
        else {  // scan for duplicates and drop them
          long now1 = System.currentTimeMillis();
          prta(tag+" "+fetchlist.size() + " fetch list entries found before purge");
          
          for(int i = fetchlist.size()-1; i> 0; i--) {
            boolean removed=false;
            if(fetchlist.get(i).getSeedname().equals(fetchlist.get(i-1).getSeedname()) &&
                    fetchlist.get(i).isSame(fetchlist.get(i-1))) {
              prta(tag + "* duplicate fetchlist found id="+fetchlist.get(i).getID() +" "
                      +fetchlist.get(i)+" "+fetchlist.get(i-1));
              try {
                dbconnedge.executeUpdate(
                      "UPDATE fetcher.fetchlist SET status='nodata' WHERE id="+fetchlist.get(i).getID());
              }
              catch(SQLException e) {
                e.printStackTrace();
              }
              fetchlist.remove(i);
              removed = true;
            }

          }
          // If this fetchlist entry is recent, or an hour has past since the last one, try to get the data
          orgSize = fetchlist.size();
          for(int i=fetchlist.size() -1; i>= 0; i--) {
            long diff = now1 - fetchlist.get(i).getCreated();
            long wait = 86400000;        // longest wait is one full day
            if(diff < 86400000) wait = 600000;    // If created in the last day, do every 10 minutes
            else if(diff < 7*86400000) wait = diff/86400000*3600000;
            if( now1 - fetchlist.get(i).getUpdated() < wait &&          // If the last one is less than wait
                now1 - fetchlist.get(i).getCreated() > 7200000) {   // always do recently created
              //prta(tag+ i + " * fetch is not real recent or an hour has not past wait=" + wait
              //        + " " + diff + " " + fetchlist.get(i));
              fetchlist.remove(i);
            } 
          }
          prta(tag+" "+fetchlist.size()+" entries after purge");
          if(fetchlist.isEmpty()) {     // no remaining fetches
            prta(tag + " Fetchlist is empty - wait one minute and try again? orgSize="+orgSize);
            try {
              sleep(60000);
            }
            catch(InterruptedException expected) {   
            }
          }
          // If the fetchlist is not empty, then process it
          else  {
            noFetchFound = 30000;
            prta(tag + " #fetches found=" + fetchlist.size() + " lastNFetch=" + lastNFetch
                    + " elapse=" + elapse + " tot=" + totalElapse + 
                    " avg=" + (totalElapse / nquery));        


            // If we got the same number of open fetches and there were none successful last time, skip this
            if(fetchlist.size() == lastNFetch ) {
              try {
                prta(tag+" Same number of fetches, wait 1 minute "+nskipNFetch);
                sleep(60000);
                if(terminate) break;
                nskipNFetch++;
              }
              catch(InterruptedException expected) {
              }
            }      

            lastNFetch = fetchlist.size();
            for (int i = 0; i < fetchlist.size(); i++) {
          // do any preliminaries (like turning the baler on!
              if (terminate) {
                break;
              }              
              checkNewDay();
              if (ID != 0) {
                readRequestStation();
              }
              if (terminate) {
                break;
              }
              if (disableUntil != null) {
                int cnt = 0;
                while (disableUntil.getTime() > System.currentTimeMillis()) {
                  if (cnt++ % 60 == 1) {
                    prta(tag + " Sleeping until " + disableUntil + " passes2.");
                  }
                  try {
                    sleep(60000);
                  } catch (InterruptedException expected) {
                  }
                  if (ID != 0) {
                    readRequestStation();   // pick up if user changes there mind
                  }
                }
                if (cnt > 100) {
                  break;    // leave this request loop
                }
              }

              if (terminate) {
                break;
              }
              todayJulian = SeedUtil.toJulian(new GregorianCalendar());
              runlist.clear();
              FetchList fetch = fetchlist.get(i);


              ArrayList<MiniSeed> mss = null;
              lastThrottleCheck = System.currentTimeMillis();
              fetchesDone = i;

              // Get the data from the subclass as a miniseed list
              try {
                prta(i + "/" + fetchlist.size() + " "+ Util.df21((System.currentTimeMillis() - fetch.getUpdated())/3600000.) 
                        + "hr ago Start " + fetch + " thr=" + throttle + " cwb=" + cwbIP + "/" + cwbPort
                        + " live=" + edgeIP + "/" + edgePort + " ID=" + fetch.getID());
                if (isGapPresent(fetch, cwbIP, cwbPort)) {
                  mss = getData(fetch);
                } else {
                  prta(tag + " ** Gap does not exist on CWB - skip this fetch");
                  dbconnedge.executeUpdate("UPDATE " + table + " SET status='nodata',updated=now() WHERE id = " + fetch.getID());
                }
                releaseSemaphoreForced();       // End of request, always release the semaphore

              } catch (IOException e) {
                prt(tag + " *** getData() threw IOException e=" + e);
                SendEvent.debugSMEEvent("FetchIOErr", "getData() IOerr=" + e, this);
                exception = e;
                if (terminate) {
                  break;
                }
              } catch (SQLException e) {
                Util.SQLErrorPrint(e, "SQLError trying to mark no gap fetchlist as nodata " + fetch, log);
                e.printStackTrace(log);
              } catch (RuntimeException e) {
                prt(tag + " *** getData() threw Runtime e=" + e);
                exception = e;
                if (terminate) {
                  break;
                }
              }
              nredundant = 0;
              nlivechan = 0;
              ncwbchan = 0;
              //if(mss != null) {mss.clear(); prta(tag+" **** output is disabled for debugging!");}         // DEBUG: do not load the data
              // If no data is possible, set the fetchlist entry to no data
              if (mss == null) {
                prt(tag + "Fetch returned null.  Mark nodata");
              } else if (mss.isEmpty()) {    // There is no data returned, update the updated field
                prt(tag + "No blocks returned for " + fetch);
                synchronized(edgedbMutex) {
                  if(mutexdbg) {
                    prta(tag + "Got Mutex 4a");
                  }
                  try {
                    dbconnedge.executeUpdate("UPDATE " + table + " SET updated=now() WHERE id = " + fetch.getID());
                  }
                  catch(SQLException e) {
                    Util.SQLErrorPrint(e, "SQLError trying to set updated on noData returned " + fetch, log);
                    e.printStackTrace(log);            
                  }
                  if(mutexdbg) {
                    prta(tag+" Rel Mutex 4a");
                  }
                }
              } else {
                doFetchProcessing(mss, fetch, ha, runlist);
              }
              // set no data under some circumstances (realy short and this fetch did not return any useful blocks)
              if (mss == null || (fetch.getDuration() < 0.1 && nredundant == 0 && ncwbchan == 0 && nlivechan == 0)) {
                if (!localBaler) {
                  prta(tag + "Fetch is null or very short. Set status='nodata' for " + fetch);
                  synchronized (edgedbMutex) {
                    if (mutexdbg) {
                      prta(tag + "Got Mutex4");
                    }
                    try {
                      dbconnedge.executeUpdate("UPDATE " + table + " set status='nodata',updated=now() where ID=" + fetch.getID());
                    } catch (SQLException e) {
                      Util.SQLErrorPrint(e, tag + " ** Trying to update table with nodata");
                      SendEvent.edgeSMEEvent("FetchNoHold", "Could not get holdings e=" + e, this);
                      Util.sleep(30000);
                      // Try to build a new statement from the dbconnedge connection
                      try {
                        dbconnedge.executeUpdate("UPDATE " + table + " set status='nodata',updated=now() where ID=" + fetch.getID());
                        prta(" *** fetch null recovered with new statment" + fetch.getID());
                      } catch (SQLException e2) {
                        Util.SQLErrorPrint(e2, tag + " *** trying to get new statement! " + fetch.getSeedname());
                      }
                    }
                    if (mutexdbg) {
                      prta(tag + "Rel Mutex4");
                    }
                  }
                }
              }

              prta(tag + "#blks=" + (mss == null ? "null" : mss.size()) + " #redundant=" + nredundant
                      + " #cwb=" + ncwbchan + " #live=" + nlivechan + " for " + fetch);
              //if(localBaler) console.println(Util.asctime().substring(0,8)+" "+"#blk="+(mss==null?"null":mss.size())+" #redun="+nredundant+
              //        " #cwb="+ncwbchan+" #live="+nlivechan+" "+fetch);
              // If we have a stmt and this is not a single request, update the fetchlist and make new gaps if needed
              if (mss != null) {
                if(!mss.isEmpty()) {
                  synchronized (edgedbMutex) {
                    if (mutexdbg) {
                      prta(tag + "Got Mutex7");
                    }
                    try {
                      if (/*stmt != null &&*/!singleRequest && mss.size() > 0) {
                        boolean changed = 
                          fetch.updateFetchWithRuns(runlist, dbconnedge.getStatement(), table, tag, 
                                  IOErrorDuringFetch, (log == null ? System.out : log));
                      }
                    } catch (SQLException e) {
                      prta("SQLError trying to updateFetchlistWithRuns e=" + e);
                      Util.SQLErrorPrint(e, "SQLError trying to update fetchlist " + fetch, log);
                      e.printStackTrace(log);
                    }
                    if (mutexdbg) {
                      prta(tag + "Rel Mutex7");
                    }
                  }
                  mss.clear();          // Let all of the miniSEED blocks go
                }
              }
            }// for each fetchlist
          }   // if fetchlist not empty
          prta(tag+" Fetchlist completed! size="+fetchlist.size());
        }
        if (singleRequest || terminate) {
          break;      // we have done the request, exit.
        }
        if (localBaler) {
          break;
        }
        try {
          sleep(30000);
        } catch (InterruptedException expected) {
        }

      }       // for(;;) loop
    }
    catch(RuntimeException e) {
      prta(tag+toString()+ " runtime found e="+e);
      e.printStackTrace(log);
      throw e;
    }
    /*if(stmt != null)
      try  {stmt.close();} catch(SQLException expected) {}
    stmt=null;*/
    if(semaphores != null) {
      semaphores.release(npermit);
      removeSemaphore(tag);
    }
    prta(tag + " is exiting. terminate=" + terminate + " singlerequest=" + singleRequest + " " + toString());
  }
  private void removeSemaphore(String tg) {
    synchronized(haveSemaphores) {
      for(int i=0; i<haveSemaphores.size(); i++) {
        if( tg.equals(haveSemaphores.get(i))) {
          haveSemaphores.remove(i);
          return;
        }
      }
    }
    prta(tag + " Could not remove semaphore from list! "+tg);
  }
  private int nredundant;
  private int ncwbchan;
  private int nlivechan;
  private int todayJulian;
  private String oldSelect;
  private long elapse;
  private int nquery;
  private long totalElapse;
  private String  makeFetchList(Timestamp fetchstart) {
        // here we are in fetchlist mode, build up the fetch list
    String select = "SELECT * FROM " + table.trim();
    if (localBaler) {
      select += " WHERE (status='open' OR status='nodata')";
    } else {
      select += " WHERE status='open'";
    }
    select += " AND start>='" + fetchstart.toString().substring(0, 16) + "'";
    if (!type.equals("")) {
      select += " AND type regexp '" + type + "' ";
    }
    if (endtime != null) {
      select += " AND start<'" + endtime.toString().substring(0, 16) + "' ";
    }
    if (localBaler) {
      seedname = orgSeedname.trim();
      channelRE = seedname + channelRE.substring(seedname.length());
    }
    /*if(!seedname.equals("")) select += " AND seedname REGEXP '"+
            seedname.trim()+(seedname.substring(10).equals("HR")?"|"+seedname.substring(0,7)+"LDO":
              "' AND NOT seedname regexp '"+seedname.substring(0,7)+"LDO")+"' ";*/
    if (!channelRE.equals("")) {
      select += " AND seedname REGEXP '" + channelRE.trim()
              + (channelRE.indexOf("$") > 0 ? "|^" + channelRE.trim().substring(0, 7) + "H" : "") + "'";  // for non-location code add possible SM fetch
    } else if (!seedname.equals("")) {
      select += " AND seedname REGEXP '"
              + seedname.trim() + (seedname.substring(10).equals("HR") ? "|" + seedname.substring(0, 7) + "LDO"
              : "' AND NOT seedname regexp '" + seedname.substring(0, 7) + "LDO") + "' ";
    }
    select += " ORDER BY start desc,seedname";
    if (!select.equals(oldSelect)) {
      prta(tag + "Select! : " + select);
    }
    prta(tag + "Wait for DB mutex3 fetchlist");
    synchronized (mutexFetchlist) {
      
      if (mutexdbg) {
        prta(tag + "Got Mutex3 fetchlist");
        mutexElapse = System.currentTimeMillis();
      }
      if (terminate) {
        return select;
      }
      try {
        elapse = System.currentTimeMillis();
        nquery++;
        try (ResultSet rs = dbconnfetchlist.executeQuery(select)) {
          elapse = System.currentTimeMillis() - elapse;
          totalElapse += elapse / 1000.;
          oldSelect = select;
          while (rs.next()) {
            // check for null location codes in the seedname if we are not using a RE
            //if(seedname.indexOf("[") < 0)
            //  if(!seedname.substring(10,12).equals( (rs.getString("seedname")+"      ").substring(10,12))) continue;
            Timestamp st = rs.getTimestamp("start");
            st.setTime(st.getTime() + rs.getShort("start_ms"));
            fetchlist.add(new FetchList(rs));
          }
        }
      } catch (SQLException e) {
        Util.SQLErrorPrint(e, tag + " getting fetchlist");
        prta("Got error reading fetchlist e=" + e);
      }
      catch(RuntimeException e) {
        prta("Got runtime exception bulding fetchlist - reopen size=" + fetchlist.size());
        e.printStackTrace(getPrintStream());
        dbconnfetchlist.reopen();
      }
      if (mutexdbg) {
        prta(tag + "Rel Mutex3 fetchlist elapse="+(System.currentTimeMillis() - mutexElapse)/1000.);
      }
    }
    return select;
  }

  private void doFetchProcessing(ArrayList<MiniSeed> mss, FetchList fetch, HoldingArray ha, RunList runlist) {
    long fetchElapse = System.currentTimeMillis();
  
    prta(tag + "Fetch returned=" + mss.size() + " blks for " + fetch 
            + " elapse=" + (System.currentTimeMillis() - fetchElapse) / 1000.);

    if (!ignoreHoldings) {
      boolean holdingSQLOK = false;
      while (!holdingSQLOK) {
        synchronized (statusMutex) {
          if (mutexdbg) {
            prta(tag + "Got Mutex6 Status");
          }
          try {
            Timestamp st = new Timestamp(mss.get(0).getTimeInMillis() - 86400000);
            Timestamp en = new Timestamp(mss.get(0).getTimeInMillis() + 86400000 * 10);
            ha.clearNoWrite();
            String sql = "SELECT * FROM status.holdingshist WHERE seedname='" + mss.get(0).getSeedNameString() + "' AND type='CW' "
                    + "AND not (ended <'" + st.toString().substring(0, 10) + "' OR start >'" + en.toString().substring(0, 10) + "') ORDER BY start";
            ResultSet rs = dbconnstatus.executeQuery(sql);
            while (rs.next()) {
              ha.addEnd(rs);
            }
            rs.close();
            sql = sql.replaceAll("hist", "");
            rs = dbconnstatus.executeQuery(sql);
            while (rs.next()) {
              ha.addEnd(rs);
            }
            rs.close();
            prta(tag + "Got holdings for " + fetch.getSeedname() + " size=" + ha.getSize());
            if (dbg) {
              prt(ha.toStringDetail());
            }
            holdingSQLOK = true;
          } catch (SQLException e) {
            Util.SQLErrorPrint(e, tag + " *** SQL error getting holdings for seedname=" + fetch.getSeedname());
            e.printStackTrace();
            SendEvent.edgeSMEEvent("FetchNoHold", "Could not get holdings e=" + e, this);
            Util.sleep(30000);
          }
          if (mutexdbg) {
            prta(tag + "Rel Mutex6 Status");
          }
        }
      }
    }

    // Cull the list of blocks based on the holdings, build up the RunList
    for (int j = 0; j < mss.size(); j++) {
      if (mss.get(j) != null) {
        runlist.add(mss.get(j));
        if (mss.get(j).getBuf()[7] == '~') {
          mss.set(j, null);     // This block was in outputCWB, do not include it
        }
        if (fetch.getDuration() > 120. && !ignoreHoldings && !fetch.getType().contains("Z")) {
          if (ha.containsFully(mss.get(j).getTimeInMillis(),
                  (int) (mss.get(j).getNextExpectedTimeInMillis() - mss.get(j).getTimeInMillis()))) {
            prt("Holdings drop :" + mss.get(j) + " " + j + " of " + mss.size());
            if (nredundant > 10) {
              mss.set(j, null);  // If there are just a few, let them in
            }
            nredundant++;
          }
        }
      }
    }
    if (dbg) {
      prt(runlist.toString());
    }

    // Open the edge and cwb sockets if they are not null
    boolean cwbOK = false;
    boolean edgeOK = false;
    while (!cwbOK || !edgeOK) {
      try {
        if (!cwbIP.equals("null") && sockcwb == null) {
          prta("Open CWB " + cwbIP + "/" + cwbPort);
          sockcwb = new Socket(cwbIP, cwbPort);
          outCWB = sockcwb.getOutputStream();
          cwbOK = true;
        } else {
          cwbOK = true;    // Not configurated or already open
        }
      } catch (UnknownHostException e) {
        prta(tag + " *** Got Unknown host err opening CWB " + cwbIP + "/" + cwbPort + " e=" + e);
        SendEvent.debugSMEEvent("FetchUnknown", "Unknown host=" + e, this);
        cwbOK = false;
      } catch (IOException e) {
        prta(tag + " *** IOException err opening CWB socketshost=" + cwbIP + "/" + cwbPort + " e=" + e);
        SendEvent.debugSMEEvent("FetchIOErr2", tag + "Opening cwb host=" + cwbIP + "/" + cwbPort + "  IOError=" + e, this);
        e.printStackTrace(log);
        e.printStackTrace();
        cwbOK = false;
      }
      try {
        if (!edgeIP.equals("null") && sockedge == null) {
          prta("Open Edge " + edgeIP + "/" + edgePort);
          sockedge = new Socket(edgeIP, edgePort);
          out = sockedge.getOutputStream();
          edgeOK = true;
        } else {
          edgeOK = true;   // No configured ir already open
        }
      } catch (UnknownHostException e) {
        prta(tag + " *** Got Unknown host err opening edge " + edgeIP + "/" + edgePort + " e=" + e);
        SendEvent.debugSMEEvent("FetchUnknown", "Unknown host opening Edge " + edgeIP + "/" + edgePort + " =" + e, this);
        edgeOK = false;
      } catch (IOException e) {
        prta(tag + " *** IOException err opening Edge socketshost=" + edgeIP + "/" + edgePort + " e=" + e);
        SendEvent.debugSMEEvent("FetchIOErr2", tag + "Opening edge socket. host=" + edgeIP + "/" + edgePort + "  IOError=" + e, this);
        e.printStackTrace(log);
        e.printStackTrace();
        edgeOK = false;
      }
      if (!cwbOK || !edgeOK) {
        prta(tag + " * Wait 30 - some socket open did not work!");
        try {
          sleep(30000);
        } catch (InterruptedException expected) {
        }
      }
    }
    // Process all remaining blocks
    for (int j = 0; j < mss.size(); j++) {
      if (mss.get(j) == null) {
        continue;        // Block was eliminated
      }
      try {
        int ns = MiniSeed.getNsampFromBuf(mss.get(j).getBuf(), mss.get(j).getEncoding());
        if (ns < mss.get(j).getNsamp()) {
          prta(" *** Q330 ns fail=" + ns + " ms=" + mss.get(j));
          SendEvent.edgeSMEEvent("FetchBadBlk", "Bad ns " + ns + "!=" + mss.get(j).getNsamp() + " " + mss.get(j).toString().substring(0, 50), this);
          mss.get(j).setNsamp(ns);
          continue;
        }
      } catch (IllegalSeednameException e) {
        prta("Got illegal seedname exception trying to check number of samples! e=" + e);
      }

      if (mss.get(j).getJulian() > todayJulian - edgeDays) {   // put in current edge
        if (dbg) {
          prt("EDGE:" + mss.get(j) + " " + j + " of " + mss.size());
        }
        if (out != null) {
          try {
            if (!nooutput) {
              out.write(mss.get(j).getBuf(), 0, mss.get(j).getBlockSize());
            }
            nlivechan++;
          } catch (IOException e) {
            // Open the edge and cwb sockets if they are not null
            try {
              if (!edgeIP.equals("null")) {
                sockedge = new Socket(edgeIP, edgePort);
                out = sockedge.getOutputStream();
                if (!nooutput) {
                  out.write(mss.get(j).getBuf(), 0, mss.get(j).getBlockSize());
                }
                nlivechan++;
              }
            } catch (UnknownHostException e2) {
              prt(tag + " ** Got Unknown host edgeIP=" + edgeIP + "/" + edgePort + " reopening edge socket e=" + e);
              SendEvent.edgeSMEEvent("FetchNoEdge", "Live unknownhost! edgeIP=" + edgeIP + "/" + edgePort, this);
              exception = e2;
              break;
            } catch (IOException e2) {
              prt(tag + "  *** IOException reopening edgeip=" + edgeIP + "/" + edgePort + " sockets e=" + e2);
              SendEvent.edgeSMEEvent("FetchNoEdge", "Live 2nd open IO error! edgeIP=" + edgeIP + "/" + edgePort, this);
              exception = e2;
              break;
            }

          }
        } else {
          prt(tag + " *** Write to edge when edge is not configured!  edgeIP=" + edgeIP + "/" + edgePort);
          SendEvent.edgeSMEEvent("FetchNoLive", "Live is not configured! edgeIP=" + edgeIP + "/" + edgePort, this);
        }
      } else {                      // put it CWB
        //prt("CWB write "+mslist.get(j)+" gbs="+mslist.get(j).getBlockSize());
        if (dbg) {
          prt("CWB:" + mss.get(j) + " " + j + " of " + mss.size());
        }
        if (outCWB != null) {
          try {
            if (!nooutput) {
              outCWB.write(mss.get(j).getBuf(), 0, mss.get(j).getBlockSize());
            }
            ncwbchan++;
          } catch (IOException e) {
            try {
              if (!cwbIP.equals("null")) {
                sockcwb = new Socket(cwbIP, cwbPort);
                outCWB = sockcwb.getOutputStream();
                if (!nooutput) {
                  outCWB.write(mss.get(j).getBuf(), 0, mss.get(j).getBlockSize());
                }
                ncwbchan++;
              }
            } catch (UnknownHostException e2) {
              SendEvent.edgeSMEEvent("FetchNoCWB", "CWB is unknown!  cwbIP=" + cwbIP + "/" + cwbPort, this);
              prt(tag + "*** Got Unknown host cwbIP=" + cwbIP + "/" + cwbPort + " resetting up CWB  e=" + e2);
              exception = e;
              break;
            } catch (IOException e2) {
              prt(tag + " *** IOException Opening sockets cwbIP=" + cwbIP + "/" + cwbPort + " resetting up CWB e=" + e2);
              SendEvent.edgeSMEEvent("FetchNoCWB", "CWB 2nd connect IOerror!  cwbIP=" + cwbIP + "/" + cwbPort, this);
              exception = e;
              break;
            }

          }
        } else {
          prt(tag + " *** Data destined to CWB, but CWB is not configured! cwbIP=" + cwbIP + "/" + cwbPort + " " + mss.get(j));
          SendEvent.edgeSMEEvent("FetchNoCWB", "CWB is not configured! cwbIP=" + cwbIP + "/" + cwbPort, this);
        }
      }
    }

    // close any open sockets to CWB so they do not get stale
    if (sockcwb != null) {
      try {
        sockcwb.close();
        sockcwb = null;
        outCWB = null;
      } catch (IOException e) {
        prt(tag + "  *** IO Exception trying to close CWB e=" + e);
      }
    }
    if (sockedge != null) {
      try {
        sockedge.close();
        sockedge = null;
        out = null;
      } catch (IOException e) {
        prt(tag + "  *** IO Exception trying to close LIVE e=" + e);
      }
    }
    // If saving the files has been set via "-savefiles", write this data onto that path
    if (writeDataPath != null) {
      String savename = writeDataPath + Util.FS + fetch.getType() + Util.FS + Util.stringBuilderReplaceAll(mss.get(0).getSeedNameSB(), " ", "_")
              + "_" + fetch.getID() + "_" + Util.df4(mss.get(0).getYear()) + Util.df3(mss.get(0).getDoy())
              + "_" + Util.df2(mss.get(0).getHour()) + Util.df2(mss.get(0).getMinute()) + Util.df2(mss.get(0).getSeconds())
              + ".ms.tmp";
      try {
        prta(tag + " save data to file " + savename);
        Util.chkFilePath(savename);
        try (RandomAccessFile save = new RandomAccessFile(savename, "rw")) {
          save.setLength(0);
          save.seek(0L);
          for (MiniSeed ms : mss) {
            save.write(ms.getBuf(), 0, ms.getBlockSize());
          }
        }
        File f = new File(savename);
        File f2 = new File(savename.replaceAll(".tmp", ""));
        if (f.exists()) {
          f.renameTo(f2);
        }
      } catch (IOException e) {
        prta(tag + " ***** could not save data to file" + savename + " e=" + e);
        e.printStackTrace(log);
      }
    }

  }
  protected final boolean getSemaphoreIfNeeded() {
    if (haveSemaphore) {
      return true;
    }
    IOErrorDuringFetch = false;     // this might be set by the implementor.
    // If we have had to wait for a latency is the past hour, then we need to get one of the semaphores to continue.
    if (System.currentTimeMillis() - lastLatencyWait < 3600000 && lastLatencyWait > 0) {
      prta(tag + " **** Latency wait for semaphore enabled");
    }
    if (semaphores != null && System.currentTimeMillis() - lastLatencyWait < 3600000
            && lastLatencyWait > 0) {  // if using semaphores
      while (!haveSemaphore) {
        try {
          prta(tag + " is waiting to acquire semaphore avail=" + semaphores.availablePermits());
          long elapse = System.currentTimeMillis();
          semaphores.acquire();
          prta(tag + " has just acquired a semaphore avail=" + semaphores.availablePermits() + " wait=" + (System.currentTimeMillis() - elapse) + " ms");
          haveSemaphore = true;
        } catch (InterruptedException e) {
          prta(tag + " * interrupted while getting a Semaphore!");
        }
      }
    } else {
      lastLatencyWait = 0;
    }
    return haveSemaphore;
  }

  protected final void releaseSemaphore() {
    if(semaphores == null) return;
    if (haveSemaphore && System.currentTimeMillis() - lastLatencyWait > 3600000) {
      semaphores.release();
      prta(tag + " release a semaphore avail=" + semaphores.availablePermits() + " of " + npermit);
      haveSemaphore = false;
    }
  }

  protected final void releaseSemaphoreForced() {
    if(semaphores == null) return;
    if (haveSemaphore) {
      semaphores.release();
      prta(tag + " release a semaphoreF avail=" + semaphores.availablePermits() + " of " + npermit);
      haveSemaphore = false;
    }

  }

  protected final void readRequestStation() {
    if (System.currentTimeMillis() - lastReadRequestStation < 60000) {
      return;
    }
    lastReadRequestStation = System.currentTimeMillis();
    synchronized (edgedbMutex) {
      if (mutexdbg) {
        prta(tag + "Got Mutex8");
      }
      if (terminate) {
        return;
      }
      try {
        String s = "SELECT requeststation,throttle,requestip,requestport,channelre,disablerequestuntil,"
                + "requestclass, cwbip,cwbport,edgeip,edgeport,tablename,fetchtype FROM requeststation,requesttype "
                + "WHERE requeststation.id=" + ID + " AND requesttype.id=requestid";
        try (ResultSet rs = dbconnedge.executeQuery(s)) {
          if (rs.next()) {
            seedname = rs.getString("channelre");
            channelRE = seedname;
            seedname = (seedname + "       ").substring(0, 12).replaceAll("-", " ");
            throttleUpper = rs.getInt("throttle");
            if (localBaler) {
              throttle = throttleUpper;
            }
            host = rs.getString("requestip");
            port = rs.getInt("requestport");
            cwbIP = rs.getString("cwbip");
            cwbPort = rs.getInt("cwbport");
            if (localBaler) {
              cwbPort = 2062;
            }
            edgeIP = rs.getString("edgeip");
            edgePort = rs.getInt("edgeport");
            if (localBaler) {
              edgePort = 7974;
            }

            table = "fetcher." + rs.getString("tablename");

            disableUntil = rs.getTimestamp("disablerequestuntil");
            if (!rs.getString("fetchtype").matches(type)) {
              prt(tag + " Found type of fetch has changed in requeststation.  Exit.");
              rs.close();
              return;
            }
          }
        }
      } catch (SQLException | RuntimeException e) {
        if(log != null) {
          e.printStackTrace(log);
        }
      }
      if (mutexdbg) {
        prta(tag + "Rel Mutex8");
      }
    }
  }

  /**
   * the loader routine calls this routine each time it receives some data. This routine implements
   * the feedback loop to modify the throttle rate based on input and latency increase
   *
   * @param nbytes The number of bytes in the last packet
   * @return The new throttle rate
   */
  int nwaits;

  public int doDynamicThrottle(int nbytes) {
    throttleBytes += nbytes;
    if (throttleBytes < 512) {
      return throttle;
    }
    long minms = throttleBytes * 8000 / throttle - (System.currentTimeMillis() - lastThrottleCheck);
    if (minms > 0) {
      try {
        if (nwaits++ % 200 == 3) {
          prta("Throttle " + minms + " ms nb=" + throttleBytes
                  + " elapsed=" + (System.currentTimeMillis() - lastThrottleCheck)
                  + " #waits=" + nwaits + " srv=" + (lastThrottleCheck - lastLatencyCheck));
        }
        sleep(minms);
      } catch (InterruptedException expected) {
      } // Wait for minimum time to go by
    }
    lastThrottleCheck = System.currentTimeMillis();
    throttleBytes = 0;
    if (throttle > throttleUpper) {
      throttle = throttleUpper;
    }

    // Now check on the Latency.  If it is above the minimum and trending up, we need to back down the throttle
    if (localBaler) {
      return throttle;       // Local balers do not effect station throughput so no throttle changes
    }
    if (lastThrottleCheck - lastLatencyCheck > 120000) {
      lastLatencyCheck = lastThrottleCheck;
      if (latencyClient == null) {
        prta("Open latency server at " + latencyServer + "/" + latencyPort);
        latencyClient = new LatencyClient(latencyServer, latencyPort);
        if (latencyClient == null) {
          prta("Could not open latency server at " + latencyServer + "/" + latencyPort);
        } else {
          prta("Latency server is open");
        }
      }
      if (latencyClient != null) {
        try {
          int old = throttle;
          ChannelStatus cs = latencyClient.getStation(seedname.substring(0, 7));
          if (cs != null) {
            prta("Latency check lat=" + cs.getLatency() + " oldlat=" + lastLatency + " throttle=" + throttle + " max=" + throttleUpper);
            if (cs.getLatency() > latencySignificant) {
              if (cs.getLatency() > lastLatency) {      // Need to slow down
                throttle = throttle * 3 / 4;
                if (throttle < 100) {
                  throttle = 100;
                }
              }
            } else {
              if (cs.getLatency() < latencySignificant / 2. && (cs.getLatency() < lastLatency || cs.getLatency() < 3.)) {
                throttle = throttle * 5 / 4;  // It o.k. and decreasing
              }
              if (throttle > throttleUpper) {
                throttle = throttleUpper;
              }
            }
            if (throttle != old) {
              prta(tag + seedname + " throttle adjust to " + throttle + " from " + old
                      + " lat=" + cs.getLatency() + " lastLat=" + lastLatency + " max=" + throttleUpper);
            }
            lastLatency = (int) cs.getLatency();
          } else {
            prta("Latency check failed to get a cs record.  " + seedname);
          }
        } catch (IOException e) {
          prt(tag + "Could not get latency for station=" + seedname + " e=" + e);
        }
      }
    }
    return throttle;
  }

  /**
   * Wait for the latency to be inside the range set in latencyWaitMin
   *
   * @return The latency last obtained.
   */
  public double waitForLatency() {
    int loop = 0;
    ChannelStatus cs = getStationStatus();
    if (dbg) {
      prta("WaitForLatency min=" + latencyWaitMin + " cs=" + cs);
    }
    for (;;) {
      cs = getStationStatus();
      if (cs.getLatency() > latencyWaitMin && cs.getAge() < 0.3) {
        try {
          sleep(30000);
        } catch (InterruptedException expected) {
        }
        if (loop++ % 2 == 1) {
          prta("* Waiting for latency to recover " + cs.getLatency() + " > " + latencyWaitMin
                  + " lrcv-m=" + Util.df21(cs.getAge()));
        }
        lastLatencyWait = System.currentTimeMillis();
      } else {
        if (loop > 2) {
          prta("*  WaitForLatency is now o.k. " + cs.getLatency() + " <  " + latencyWaitMin
                  + " lrcv-m=" + Util.df21(cs.getAge()) + " " + cs.getKey());
        }
        break;
      }
    }
    return cs.getLatency();
  }

  /**
   * Not only wait for latency in range, but make sure the latency is changing. Set the
   * lastLatencyWait time so that semaphore to restrict stations that might be latent are effective.
   *
   * 1) If the latency value or the channel returned by getStationStatus() changes, then the latency
   * must be a new one - evaluate. 2) If 1 does not happen for 10 seconds, then wait until something
   * in 1 does happen
   *
   */
  public void waitForChangingLatency() {
    if (terminate) {
      throw new RuntimeException("Terminate is set! " + seedname);
    }
    this.releaseSemaphore();
    ChannelStatus cs = getChannelStatus();
    if (cs == null) {
      return;      // no latency possible
    }
    if (lastLat == Double.MIN_VALUE) {
      while ((lastLat = cs.getLatency()) > latencyWaitMin) {
        Util.sleep(2000);
        lastLatencyWait = System.currentTimeMillis();
      }
    }
    int loop = 0;
    for (;;) {
      cs = getChannelStatus();
      //prta("WaitForChangingLatency: new lat="+cs.getLatency()+" age="+cs.getAge()+" "+cs); 
      double lat2 = cs.getLatency();
      if ((lastLat != lat2 || !lastLatencyChan.equals(cs.getKey())) && lat2 < latencyWaitMin && cs.getAge() < 0.3) {
        if (dbg) {
          prta("WFCL:  has changed old=" + lastLat + " new=" + lat2
                  + " lmrcv=" + Util.df21(cs.getAge()) + " " + cs.getKey());
        }

        if (loop == 0 && throttle != throttleUpper) {
          throttle = Math.min(throttle * 10 / 8, throttleUpper);
          prta("WFCL: *** throttle up=" + throttle + "/" + throttleUpper + " lastlat=" + lastLat + " lat2=" + lat2
                  + " age=" + Util.df21(cs.getAge()) + " " + cs.getKey() + "/" + lastLatencyChan
                  + " elapseWait=" + (System.currentTimeMillis() - lastLatencyWait) / 1000.);
        }
        lastLat = lat2;
        lastLatencyChanged = System.currentTimeMillis();
        lastLatencyChan = cs.getKey();
        getSemaphoreIfNeeded();        // We have 
        break;
      }

      // nothing has changed, if its been less than 2* latencyWaitMin interval assume its ok
      if (System.currentTimeMillis() - lastLatencyChanged < latencyWaitMin / 2 * 1000) {
        //prta("WFCL: in grace period waiting for change "+(System.currentTimeMillis() - lastLatencyChanged));
        break;
      }
      if (lat2 >= latencyWaitMin && lastLat != lat2 && cs.getAge() < 0.3 && loop == 0) {
        lastLatencyWait = System.currentTimeMillis();
        throttle = throttle * 8 / 10;      // reduce throttle rate (effects size of curls or other commands
        if (throttle < 100) {
          throttle = 100;
        }
        prta("WFCL: *** throttle down=" + throttle + " lat=" + cs.getLatency() + " age=" + cs.getAge() + " " + cs);
      }
      if (loop++ % 30 == 29) {
        prta("WFCL: * waiting long old=" + lastLat + " new=" + lat2 + " lmrcv=" + Util.df21(cs.getAge()) + " "
                + cs.getKey() + "/" + lastLatencyChan + " lp=" + loop + " " + (System.currentTimeMillis() - lastLatencyChanged));
      }
      loop++;
      Util.sleep(1000);
    }
  }
  private long lastLatencyChanged;
  private String lastLatencyChan = "";

  /**
   * Get the ChannelStatus including latency from the latency server. The returned is the high rate
   * channel from the station which came in last (minimum last received minutes and the latency is
   * the latency of channel.
   *
   * @return
   */
  protected ChannelStatus getChannelStatus() {
    //ChannelStatus cs = null;
    for (int i = 0; i < 5; i++) {
      if (latencyClient == null) {
        prta("Open latency server at " + latencyServer + "/" + latencyPort);
        latencyClient = new LatencyClient(latencyServer, latencyPort);
        if (latencyClient == null) {
          prta("Could not open latency server at " + latencyServer + "/" + latencyPort);
        } else {
          prta("Latency server is open");
        }
      }
      if (latencyClient != null) {
        if (System.currentTimeMillis() - lastChanScan > 900000) {
          buildChannelList();       // create a list of high rate channels
        }
        double minAge = Double.MAX_VALUE;
        

        for (String seed : chanList) {
          try {
            ChannelStatus cs;
            if(chanList.size() == 1 && chanList.get(0).contains("...")) {
              cs = latencyClient.getStation(chanList.get(0).substring(0,7));
            }            
            else {
              if(seed.contains("...")) {
                prta("getChannelStatus bad seed ="+seed+" chanList.size()="+chanList.size());
                for (String s : chanList) prta(s);
                continue;
              }
              cs = latencyClient.getSeedname(seed);
            }
            if (cs != null) {
              if (retChannelStatus == null) {
                retChannelStatus = new ChannelStatus(cs.getData());       // Even if nothing is in range, return something
              }
              if (cs.getAge() < minAge) {
                retChannelStatus.reload(cs.getData());
                minAge = cs.getAge();
                //prta(cs.getKey()+" is min age="+cs.getAge()+" lat="+cs.getLatency());
              }
            }
          } catch (IOException e) {
            prta(tag + " loop=" + i + " got IOExcepting getting ChannelStatus from latency e=" + e);
            try {
              sleep(2000);
            } catch (InterruptedException expected) {
            }
          }
        }
        if (retChannelStatus != null) {
          break;
        }
      }
    }
    return retChannelStatus;

  }

  private void buildChannelList() {
    long now2 = System.currentTimeMillis();
    lastChanScan = now2;
    synchronized (chansMutex) {
      if (now2 - lastChansRead > 900000) {     // every 15 minutes reload the chans
        chans = EdgeChannelServer.getChannels();
        lastChansRead = now2;
      }
      String station = seedname.substring(0, 7);
      Util.clear(tmpch);
      // For every channel, see it is a match
      for (Object chan : chans) {
        Channel ch = (Channel) chan;
        if (ch.getChannel().contains(station)) {
          if (ch.getChannel().matches(seedname.substring(0, 7) + "[BCDHES][HN][ZNE12].*") && (now2 - ch.getLastData().getTime()) < 86400000) {
            try {
            if(ch.getChannel().contains("...")) {
              prta("Fetcher: buildChannelList() for seedname does not look right "+ch.getChannel());
              new RuntimeException("Tracebad seedname").printStackTrace(log);
            }              
            ChannelStatus cs = latencyClient.getSeedname(ch.getChannel());
              if (cs != null) {
                if (cs.getLatency() < 300. && cs.getAge() < 600) {
                  boolean found = false;
                  for (String c : chanList) {
                    if (c.equals(ch.getChannel())) {
                      found = true;
                      break;
                    }
                  }
                  if (!found) {
                    tmpch.append(ch.getChannel().substring(7)).append(" ");
                    chanList.add(ch.getChannel());
                  }
                }
              }
            } catch (IOException e) {
              break;      // if error, then just leave list alone
            }
          }
        }
      }
    }
    prta(tag + " chanList for " + seedname + " size=" + chanList.size() + " " + tmpch);
    if (chanList.isEmpty()) {
      chanList.add(seedname);
    }
  }

  /**
   * Get the ChannelStatus including latency from the latency server.
   *
   * @return
   */
  public ChannelStatus getStationStatus() {
    ChannelStatus cs = null;
    for (int i = 0; i < 5; i++) {
      if (latencyClient == null) {
        prta("Open latency server at " + latencyServer + "/" + latencyPort);
        latencyClient = new LatencyClient(latencyServer, latencyPort);
        if (latencyClient == null) {
          prta("Could not open latency server at " + latencyServer + "/" + latencyPort);
        } else {
          prta("Latency server is open");
        }
      }
      if (latencyClient != null) {
        try {
          cs = latencyClient.getStation(seedname.substring(0, 7));
        } catch (IOException e) {
          if (i == 3) {
            prta(tag + " loop=" + i + " got IOException getting ChannelStatus from latency e=" + e);
          }
          try {
            sleep(2000);
          } catch (InterruptedException expected) {
          }
        }
      }
    }
    return cs;

  }
  final private class DBConnectionMonitor extends Thread  {
    public DBConnectionMonitor() {
      setDaemon(true);
      start();
    }
    @Override
    public void run() {
      while(!terminate) {
        try {
          sleep(600000);
          
        }
        catch(InterruptedException expected) {
          
        }
        if(!dbconnedge.isOK()) {
          Util.prta("DBConnedge is not o.k. *****  reopen ");
          dbconnedge.reopen();
        }                
        if(!dbconnstatus.isOK()) {
          Util.prta("DBConnstatus is not o.k. ****  reopen ");
          dbconnstatus.reopen();
        }
        if(!dbconnfetchlist.isOK()) {
          Util.prta("DBConnfetchlist is not o.k. **** reopen ");
          dbconnfetchlist.reopen();
        }
        Util.prta("DBConnectionMonitor: has run");
      }
    }
  } 
  /**
   * this internal class gets registered with the Runtime shutdown system and is invoked when the
   * system is shutting down. This must cause the thread to exit
   */
  final class ShutdownFetcher extends Thread {

    /**
     * default constructor does nothing the shutdown hook starts the run() thread
     */
    Fetcher theFetcher;

    public ShutdownFetcher(Fetcher f) {
      theFetcher = f;
    }

    // This is called by the RunTime.shutdown at any termination
    @Override
    public void run() {
      prta(Util.ascdate() + " " + tag + seedname + " Fetcher: Fetcher Shutdown() started...");
      terminate();          // send terminate to main thread and cause interrupt
      shuttingDown = true;    // Mark that all fetchers are shutting down for main()
      int loop = 0;
      while (theFetcher.isAlive()) {
        // Do stuff to aid the shutdown
        try {
          sleep(1000);
        } catch (InterruptedException expected) {
        }
        loop++;
        if (loop > 600) {
          prta(tag + " " + seedname + " Fetcher: Fetcher Shutdown() exited by timeout");
          break;
        }   // The fetcher may have a lot of data to process, wait for it to exit.
      }
      prta(Util.ascdate() + " " + tag + seedname + " Fetcher: Shutdown() of Fetcher is complete. " + loop);
    }
  }
 
}
