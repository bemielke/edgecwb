/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.picker;

import gov.usgs.cwbquery.QuerySpanCollection;
import gov.usgs.alarm.SendEvent;
import gov.usgs.anss.db.DBConnectionThread;
import gov.usgs.anss.edge.IndexFile;
import gov.usgs.anss.edgemom.EdgeChannelServer;
import gov.usgs.anss.edgethread.EdgeThread;
import gov.usgs.anss.edgethread.FakeThread;
import gov.usgs.anss.edgethread.ManagerInterface;
//import gov.usgs.anss.edgeoutput.GlobalPickSender;
import gov.usgs.anss.util.Util;
//import gov.usgs.detectionformats.Pick;
import gov.usgs.edge.config.Channel;
import gov.usgs.edge.config.Picker;
import java.io.*;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.GregorianCalendar;
import java.util.Iterator;
import java.util.TreeMap;
import java.util.Collections;

/**This EdgeThreads looks at the QuerySpans available and
 * if that seedname is configured for a picker, it starts up a picker and
 * monitors it for running or changes in configuration. Originally created to handle
 * FilterPicker6 and possibly other picker types.  
 * <p>
 * This class has two purposes- As a static class it can be used to get unique
 * pick IDs either from the status.cwbpick table or from an internal hashed
 * one if the DB is not available. There is logic on this static side so that if
 * the DB is not online it realizes this quickly and passes out the hashed pick
 * ID. The hashed pick ID are the days since 1970 * 1000000000 + minute since
 * midnight *1000000 (max 1440000000) This is set at startup and should be
 * unique under all circumstances when combined with the source host number.
 * <p>
 * When this class is instantiated, it controls the running of continuous
 * pickers on all channels for which a picker template has been chosen. It
 * creates the picker file, which starts the pickers (that is it relies on the
 * last file rather than the DB. If the DB is up, then the configuration file is
 * periodically updated to pick up any configuration changes. Each picker is
 * started as its own thread within its own EdgeThread via the super class
 * CWBPicker and hence gets its own log file. The log files end up in
 * Pickers/NNSSSSSCCLL.log
 * <p>
 * <PRE>
 * Switch    Argument    Description
 * -config    filename  The configuration file name to use def="./Picker/picker.setup", if contains a slash, 
 * this becomes the configpath for templates as well
 * -logpath   path      This path will be added to the property "logfilepath" for log files, default="./PL"\
 * -dbpick    DBURL     Use this for the PickDBServer property rather than one from the edge.prop or other prop files.
 * -nodb                If present, the configuration will just be read from the file and no configuration update from DB is done
 * -dbg                 If present more log file output.
 * -nojsonoutput        If present, suppress all outout to Kafka via JSON, but the kafka is still setup
 * -jsonaddon  TAG      If present, this TAG is added as the override topic for all 
 * -nothreads           If present, this will not create a config file nor maintain any threads (used if only other types of pickers SSD in use)
 * JSONPickGenerator:
 * -jsonpath  path      The path to write all JSON output
 * GlobalPickSender (this is generally obsolete, but is still embedded in the code) :
 * -gpick ip.adr,port[[[,qize],mhost],mport] Alternate way of specifying -host -port -qsize -mhost -mport
 * OldStyle:
 * -qsize     nnn       Set the queue size for the GLobalPickSender
 * -host      ip.adr    Broadcast address to use to send UDP GlobalPicks
 * -port      p         Port to broadcast picks to
 * -mhost     ip.adr    Bind this interface for this broadcast (normally the address of this system on broadcast subnet)
 * -mport     p         The port to bind this end of the broadcast
 * </PRE>
 *
 * @author davidketchum
 */
public final class PickerManager extends EdgeThread {

  //private static final int ncalls=0;
  private static PickerManager thisManager;
  private static final TreeMap<String, Picker> pickerIDTemplate = new TreeMap<>();
  private static final TreeMap<String, Picker> pickerTemplate = new TreeMap<>();
  private static final ArrayList<String> chans = new ArrayList<>(100);
  private TreeMap<String, ManagerInterface> thr = new TreeMap<>();
  private final PickerManager.ShutdownPickerManager shutdown;
  //private static DBConnectionThread  dbpick;
  private static PreparedStatement insertPick, insertBeam;
  private static final Timestamp pickTime = new Timestamp(0L);
  private static final Timestamp endTime = new Timestamp(0L);
  private static EdgeThread thisThread;
  private static MaintainDB maintainDBStatus;
  private static MaintainDB maintainDBEdge;
  //private static DBConnectionThread dbconnedge;
  private String configFile = "picker.setup";
  private String configPath = "./Picker/";
  private String jsonTopicAddon;
  private static boolean noJsonOutput;
  private static boolean noThreads;   // If true, not threads are started by the manager, but JSON etc is setup
  private boolean dbg;
  private boolean noDB;
  //private static final byte [] wrbuf = new byte[100000];      // buffer for writefile to put long string
  private String logpath = "PL/";
  private StringBuilder fpmsb = new StringBuilder(10000);
  //private GlobalPickSender pickSender;
  private static JsonDetectionSender jsonPickSender;
  private final StringBuilder sb = new StringBuilder(10);
  private final StringBuilder runsb = new StringBuilder(100);
  private static long nextSeq = (System.currentTimeMillis() / 86400000L) * 1000000000L
          + // Days since 1970 in upper work
          ((System.currentTimeMillis() % 86400000L) / 60000 * 100000);    // Minute of the day separated by 100000

  public String getJsonTopicAddon() {
    return jsonTopicAddon;
  }
  public static long getNextSeq() {
    return nextSeq++;
  }
  //public static DBConnectionThread getDBThread() {return maintainDBStatus.getDBThread();}

  public static JsonDetectionSender getJSONSender() {
    return jsonPickSender;
  }

  public static void setJsonDetectionSender(JsonDetectionSender send) {
    if (jsonPickSender == null) {
      jsonPickSender = send;
      jsonPickSender.setNooutput(noJsonOutput);
    } else {
      if (thisThread == null) {
        new RuntimeException("Attempt to reset the jsonPicksender with sender=" + send).printStackTrace();
      } else {
        new RuntimeException("Attempt to reset the jsonPicksender with sender=" + send).printStackTrace(thisThread.getPrintStream());
      }
    }

  }

  public static PickerManager getPickerManager() {
    return thisManager;
  }

  public StringBuilder toStringBuilder() {
    return Util.clear(sb).append(" #template=").append(pickerTemplate.size()).append(" #thr=").append(thr.size());
  }

  @Override
  public String toString() {
    return toStringBuilder().toString();
  }

  /**
   * return the picker template from the picker table with the given ID
   *
   * @param id The DB ID of this picker
   * @return The picker with this ID or null if it is not found
   */
  public static Picker getPickerWithID(int id) {
    return pickerIDTemplate.get("" + id);
  }

  /**
   * return the picker template from the picker table with the given name
   *
   * @param s The string from the picker field of the picker table
   * @return The picker with this ID or null if it is not found
   */
  public static Picker getPickerWithName(String s) {
    return pickerTemplate.get(s);
  }
  
  private String getPickerNameFromArgs(String argline) {
    String[] args = argline.split("\\s");
    int pickerID = -1;
    String pickerName = null;
    for (int i = 0; i < args.length; i++) {
      if (args[i].contains(">")) {
        break;
      }  else if (args[i].equalsIgnoreCase("-id")) {
        pickerID = Integer.parseInt(args[i + 1]);
        i++;
      }      
    }
    if (pickerID != -1) {
      Picker pick = getPickerWithID(pickerID);
      if (pick != null) pickerName = pick.getPicker();
    }
    return pickerName;
  }

  /**
   * Any one can get a unique long ID for a new picker throught this routine. If
   * the pick is to be modified, call modifyPickToDB() instead.
   *
   * @param agency The agency
   * @param auth Author
   * @param picktime Time of the pick as an epochal time in millis
   * @param channel A NNSSSSSCCCLL fixed length field for the channel per SEED
   * @param amp Amplitude of the signal in meters
   * @param per Period of the signal in seconds
   * @param phase A standard phase code (usually p or s)
   * @param sequence A sequence number, normally zero for a new pick
   * @param version A picker dependent version number
   * @param pickerr The quality is an estimate of the error of the pick in
   * seconds
   * @param polarity Follows the 'U' or 'D' or '?'
   * @param onset ???? 'e' or 'i' or '?'
   * @param hipass The hipass filter corner in Hz
   * @param lowpass The lowpass filter corner in Hz
   * @param backazm The back azimuth in degrees from true north
   * @param backazmerr error estimate of back azimuth in degrees
   * @param slow Slowness in seconds/degree???
   * @param snr Signal to noise ration estimate. The larger the stronger the
   * signal
   * @param powerRatio Form beams, the power ratio maximum during the beam
   * @param pickerID from the picker table
   * @param distance If associated, the distance to the epicenter in degrees
   * @param azimuth If assocated, the azimuth to the epicenter in degrees 
   * @param residual If associated, the residual the the location in seconds
   * @param sigma If associated, the sigma of the association
   * @param ps A EdgeThread to use for logging if none has been specified at
   * creation
   * @return The unique pick id from the DB tables when this is inserted
   */
  public static synchronized long pickToDB(String agency, String auth, long picktime,
          String channel, double amp, double per, String phase,
          int sequence, int version, double pickerr, String polarity, String onset,
          double hipass, double lowpass, int backazm, int backazmerr, double slow,
          double snr, double powerRatio, int pickerID,
          double distance, double azimuth, double residual, double sigma, EdgeThread ps) {

    if (thisThread == null) {
      thisThread = ps;
    }
    
    // If this is the first time, setup the databases.
    if (maintainDBStatus == null) {            // This is a first call, create the DBconnection and maintain it
      if(Util.getProperty("PickDBServer") != null)
        if(Util.getProperty("PickDBServer").equalsIgnoreCase("nodb")) return 0;
      if(Util.getProperty("DBServer") != null) 
        if(Util.getProperty("DBServer").equalsIgnoreCase("nodb")) return 0;
      if(Util.getProperty("PickDBServer") == null) Util.setProperty("PickDBServer", "localhost/3306:status:mysql:status");
      if(Util.getProperty("PickDBServer").equals("")) Util.setProperty("PickDBServer", "localhost/3306:status:mysql:status");
      if(Util.getProperty("DBServer") == null) Util.setProperty("DBServer", "localhost/3306:edge:mysql:edge");
      if(Util.getProperty("DBServer").equals(""))  Util.setProperty("DBServer", "localhost/3306:edge:mysql:edge");
      
      maintainDBStatus = new MaintainDB("PickDBServer", thisThread);
      maintainDBEdge = new MaintainDB("DBServer", thisThread);
      try {
        loadPickerTemplate(100000L, null);     // load the picker template
      } catch (IOException e) {
        e.printStackTrace(thisThread.getPrintStream());
      }
    }
    DBConnectionThread dbpick = maintainDBStatus.getDBThread();  // A null is returned if the DB is not up
    if (dbpick == null) {
      return getNextSeq();       // something is wrong with DB, just return
    }
    if (!dbpick.isOKFast()) {
      return getNextSeq();   // If the DB does not look healthy, return we cannot do it.
    }
    try {
      if (insertPick == null) {
        insertPick
                = dbpick.prepareStatement("INSERT INTO status.cwbpick (author,picktime,picktimems,channel, amplitude,period"
                        + ",phase,sequence,version,quality,polarity,onset,hipassfreq,lowpassfreq,"
                        + "backazm,backazmerr,slowness,snr,agency,powerratio,"
                        + "assocdist,assocazimuth,assocresidual, assocsigma,pickerid,updated,created_by,created) VALUES "
                        + "(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?, now(), 0, now())", true);
      }
      if (picktime < 86400000L) {
        thisThread.prta("pickToDB() ****  bad time " + picktime);
      }
      pickTime.setTime(Math.max(picktime, 86400000L) / 1000 * 1000);   // earliest Jan 2, 1970/

      int ms = (int) (picktime % 1000);
      insertPick.setString(1, (auth.length() > 10 ? auth.substring(0, 10) : auth));
      insertPick.setTimestamp(2, pickTime);
      insertPick.setInt(3, ms);
      insertPick.setString(4, channel);
      insertPick.setFloat(5, (float) Util.roundToSig(amp, 4));
      insertPick.setFloat(6, (float) Util.roundToSig(per, 6));
      insertPick.setString(7, (phase.length() > 6 ? phase.substring(0, 6) : phase));
      insertPick.setInt(8, sequence);
      insertPick.setInt(9, version);
      insertPick.setFloat(10, (float) Util.roundToSig(pickerr, 6));
      insertPick.setString(11, (polarity == null ? "" : polarity.substring(0, 1)));
      insertPick.setString(12, (onset == null ? "" : onset.substring(0, 1)));
      insertPick.setFloat(13, (float) Util.roundToSig(hipass, 6));
      insertPick.setFloat(14, (float) Util.roundToSig(lowpass, 6));
      insertPick.setInt(15, backazm);
      insertPick.setInt(16, backazmerr);
      insertPick.setFloat(17, (float) Util.roundToSig(slow, 6));
      insertPick.setFloat(18, (float) Util.roundToSig(snr, 4));
      insertPick.setString(19, (agency.length() > 10 ? agency.substring(0, 10) : agency));
      insertPick.setFloat(20, (float) Util.roundToSig(powerRatio, 4));
      insertPick.setFloat(21, (float) Util.roundToSig(distance, 8));
      insertPick.setFloat(22, (float) Util.roundToSig(azimuth, 4));
      insertPick.setFloat(23, (float) Util.roundToSig(residual, 4));
      insertPick.setFloat(24, (float) Util.roundToSig(sigma, 4));
      insertPick.setInt(25, pickerID);
      insertPick.executeUpdate();
      long id = dbpick.getLastInsertID("status.cwbpick");
      return id;
    } catch (SQLException | RuntimeException e) {
      thisThread.prta("PM: ** SQLError " + channel + " " + pickTime + " " + agency + " " + auth + " e=" + e);
      e.printStackTrace(thisThread.getPrintStream());
      insertPick = null;
      maintainDBStatus.setBad();
      thisThread.prta("PM : database pick update not working ");
      SendEvent.edgeSMEEvent("PMPickDBErr", "Bad pick to DBe=" + e, thisThread);
    }

    return getNextSeq();
  }

  /* Any one can get a unique long ID for a new picker through this routine.  If the pick
   * is to be modified, call modifyPickToDB() instead.
   * 
   * @param agency The agency
   * @param auth Author
   * @param picktime Time of the pick as an epochal time in millis
   * @param endtime Time off for this beam
   * @param channel A NNSSSSSCCCLL fixed length field for the channel per SEED
   * @param version A version number for this pick
   * @param backazm The back azimuth in degrees from true north
   * @param azmerr estimate of the back azimuth error
   * @param slow Slowness in seconds/degree???
   * @param slowerr Estimate of the slowness error
   * @param snr Signal to noise ration estimate.  The larger the stronger the signal
   * @param ps A EdgeThread to use for logging if none has been specified at creation
   * @return The unique pick id from the DB tables when this is inserted
   */
 /*public static synchronized long beamToDB(
          String agency, String auth, long picktime, long endtime, String channel, int version, 
          int backazm, int azmerr, double slow, double slowerr, double snr, EdgeThread ps) {
    DBConnectionThread dbpick;
    if(thisThread == null) thisThread=ps;  
    if(maintainDBStatus == null) {            // This is a first call, create the DBconnection and maintainit
      maintainDBStatus = new MaintainDB("PickDBServer",thisThread);
      maintainDBEdge = new MaintainDB("DBServer", thisThread);
      try {
        loadPickerTemplate(10000L, null);     // load the picker template
      }
      catch(IOException e) {
        e.printStackTrace(thisThread.getPrintStream());
      }
    }
    dbpick = maintainDBStatus.getDBThread();  // A null is returned if the DB is not up
    if(dbpick == null) return getNextSeq();       // something is wrong with DB, just return
    if(!dbpick.isOKFast()) return getNextSeq();   // If the DB does not look healthy, return we cannot do it.
    try {
      if(insertBeam == null) insertBeam = 
        dbpick.prepareStatement("INSERT INTO status.beam (agency,author,picktime,endtime,channel,"+
            "version,backazm,backazmerr,slowness,slownesserr, snr,updated,created_by,created) VALUES "+
            "(?,?,?,?,?,?,?,?,?,?,?, now(), 0, now())", true);
      if(picktime < 86400000L || endtime < 86400000L) thisThread.prta("beamToDB() ****  bad time "+picktime+" "+endtime);
      pickTime.setTime(Math.max(picktime,86400000L)/1000*1000);     // no sooner than Jan 2, 1970
      endTime.setTime(Math.max(endtime,86400000L)/1000*1000);
      int ms = (int) (picktime % 1000);
      insertBeam.setString(1, agency);
      insertBeam.setString(2, auth);
      insertBeam.setTimestamp(3, pickTime);
      insertBeam.setTimestamp(4, endTime);
      insertBeam.setString(5, channel);
      insertBeam.setInt(6, version);
      insertBeam.setInt(7, backazm);
      insertBeam.setInt(8, azmerr);
      insertBeam.setDouble(9, Util.roundToSig(slow, 6));
      insertBeam.setDouble(10, Util.roundToSig(slowerr, 5));
      insertBeam.setDouble(11, Util.roundToSig(snr,6));
      insertBeam.executeUpdate();
      long id = dbpick.getLastInsertID("status.beam");
      return id;
    }
    catch(SQLException | RuntimeException e) {
      thisThread.prta("PM: ** SQLError "+channel+" "+pickTime+" "+endTime+" "+agency+" "+auth+" e="+e);
      e.printStackTrace(thisThread.getPrintStream());
      insertBeam=null;
      maintainDBStatus.setBad();
      SendEvent.edgeSMEEvent("PMPickDBErr","Bad beams to DB e="+e, thisThread);
    }

    return getNextSeq();
  }*/
  /**
   * set debug state
   *
   * @param t The new debug state
   */
  public void setDebug(boolean t) {
    dbg = t;
  }

  /**
   * terminate thread (causes an interrupt to be sure) you may not need the
   * interrupt!
   */
  @Override
  public void terminate() {
    terminate = true;
    prta("PM: terminate called!");
    interrupt();
  }

  /**
   * return the status string for this thread
   *
   * @return A string representing status for this thread
   */
  @Override
  public StringBuilder getStatusString() {
    Util.clear(statussb);
    statussb.append("PM: #client=").append(thr.size()).append(" config=").append(configFile);
    statussb.append("\n");
    //Iterator<ManagerInterface> itr = thr.values().iterator();
    //while(itr.hasNext()) statussb.append("   ").append(itr.next().toString()).append("\n");
    return statussb;
  }

  /**
   * return the monitor string for Nagios
   *
   * @return A String representing the monitor key value pairs for this
   * EdgeThread
   */
  @Override
  public StringBuilder getMonitorString() {
    Util.clear(monitorsb);
    monitorsb.append("FPMNthread=").append(thr.size()).append("\n");
    Iterator<ManagerInterface> itr = thr.values().iterator();
    long totalIn = 0;
    while (itr.hasNext()) {
      ManagerInterface obj = itr.next();
      totalIn += obj.getBytesIn();
    }
    monitorsb.append("FPMBytesIn=").append(totalIn).append("\n");
    return monitorsb;
  }

  /**
   * return console output - this is fully integrated so it never returns
   * anything
   *
   * @return "" since this cannot get output outside of the prt() system
   */
  @Override
  public StringBuilder getConsoleOutput() {
    return consolesb;
  }

  /**
   * creates an new instance of LISSCLient - which will try to stay connected to
   * the host/port source of data. This one gets its arguments from a command
   * line
   *
   * @param argline The command line
   * @param tg The logging tag
   */
  public PickerManager(String argline, String tg) {
    super(argline, tg);
    String[] args = argline.split("\\s");
    if (thisManager != null) {
      prta("***** restarting a picker manager????");
      new RuntimeException("*** restarting PickerManager ").printStackTrace(getPrintStream());
    }
    thisManager = (PickerManager) this;
    if (thisThread == null) {
      thisThread = (EdgeThread) this;
    }
    dbg = false;
    noDB = false;
    int sendPort = 0;
    int sendMPort = 0;
    String sendHost = null;
    String sendMHost = null;
    int sendQueueSize = 100;
    String jsonPath = null;
    for (int i = 0; i < args.length; i++) {
      //prt(i+" arg="+args[i]);
      if (args[i].equals("-dbg")) {
        dbg = true;
      } else if (args[i].equals("-config")) {
        configFile = args[i + 1];
        if (configFile.contains(Util.FS)) {
          configPath = configFile.substring(0, configFile.lastIndexOf(Util.FS)+1);
          configFile = configFile.substring(configFile.lastIndexOf(Util.FS)+1);
        }
        i++;
      } else if (args[i].equalsIgnoreCase("-nodb")) {
        noDB = true;
      } else if (args[i].equalsIgnoreCase("-logpath")) {
        logpath = args[i + 1];
        i++;
        if (!logpath.endsWith("/")) {
          logpath += "/";
        }
      }
      else if (args[i].equalsIgnoreCase("-gpick")) {
        String[] parts = args[i + 1].split(",");
        sendHost = parts[0];
        sendPort = Integer.parseInt(parts[1]);
        if (parts.length >= 3) {
          sendQueueSize = Integer.parseInt(parts[2]);
        }
        if (parts.length >= 4) {
          sendMHost = parts[3];
        }
        if (parts.length >= 5) {
          sendMPort = Integer.parseInt(parts[4]);
        }
      } else if (args[i].equalsIgnoreCase("-dbpick")) {
        Util.setProperty("PickDBServer", args[i + 1]);
        i++;
      } else if (args[i].equalsIgnoreCase("-json")) {
        jsonPickSender = new JsonDetectionSender("-json " + args[i + 1], this);
        jsonPath = args[i + 1];
        i++;
      } else if (args[i].equals("-empty")) ; else if (args[i].substring(0, 1).equals(">")) {
        break;
      } else if (args[i].equals("-jsonaddon")) {
        jsonTopicAddon = args[i+1];
        i++;
      } else if (args[i].equals("-nojsonoutput")) {
        noJsonOutput = true;
      } else if (args[i].equals("-nothreads")) {
        noThreads=true;
      } else {
        prt("PickerManager: unknown switch=" + args[i] + " ln=" + argline);
      }
    }
    if (Util.getProperty("DBServer") == null) {
      noDB = true;
    }
    if (Util.getProperty("DBServer").equals("")) {
      noDB = true;
    }
    prta("PM: created args=" + argline + " tag=" + tag);
    Util.chkFilePath(configPath + configFile);
    prta("PM: config=" + configFile + " path="+configPath+" dbg=" + dbg + " noDB=" + noDB + " DBServer=" + Util.getProperty("DBServer")
            + " Sender: ip=" + sendHost + "/" + sendPort + " mhost=" + sendMHost + "/" + sendMPort
            + " jsondir=" + jsonPath + " json=" + jsonPickSender);
    if(jsonPickSender != null) {
      jsonPickSender.setNooutput(noJsonOutput);
    }
    //if(sendHost != null && sendPort > 0) pickSender = 
    //        new GlobalPickSender(sendQueueSize, sendHost,sendPort,sendMHost, sendMPort, this);

    shutdown = new PickerManager.ShutdownPickerManager();
    Runtime.getRuntime().addShutdownHook(shutdown);
    running = true;

    start();
  }

  /**
   * This Thread keeps a socket open to the host and port, reads in any
   * information on that socket, keeps a list of unique StatusInfo, and updates
   * that list when new data for the unique key comes in.
   */
  @Override
  public void run() {
    thisThread = this;
    Iterator<ManagerInterface> itr;
    BufferedReader in;
    StringBuilder filesb = new StringBuilder(10000);
    prta("PM: run() started");
    maintainDBStatus = new MaintainDB("PickDBServer", thisThread);
    maintainDBEdge = new MaintainDB("DBServer", thisThread);
    if (maintainDBStatus == null) {            // This is a first call, create the DBconnection and maintainit
      maintainDBStatus = new MaintainDB("PickDBServer", thisThread);
      maintainDBEdge = new MaintainDB("DBServer", thisThread);
    }
    prta("PM: wait for DB to edge and status to start");
    while (maintainDBStatus.getDBThread() == null || maintainDBEdge.getDBThread() == null) {
      Util.sleep(1000);
    }
    while (!maintainDBStatus.getDBThread().isOK() || !maintainDBEdge.getDBThread().isOK()) {
      Util.sleep(1000);
    }

    prta("PM: wait for QuerySpanCollection to be up");
    while (!QuerySpanCollection.isUp()) {
      try {
        sleep(100);
      } catch (InterruptedException e) {
      }
      if (terminate) {
        break;
      }
    }
    try {
      loadPickerTemplate(pickerLastUpdated, configPath);
    } catch (IOException e) {
      prta(Util.clear(runsb).append("PM: IOError getting picker templates e=").append(e.toString()));
    }
    int n = 0;
    int loop = 0;
    String s;
    prta("PM: wait 60 s for memory system to populate");
    if (!terminate) {
      try {
        sleep(20000);
      } catch (InterruptedException expected) {
      } // TODO: make 1 minute
    }
    boolean first = true;
    long startup = System.currentTimeMillis();
    while (!terminate) {
      try {                     // Runtime exception catcher
        if (terminate || Util.isShuttingDown()) {
          break;
        }
        // In noThreads mode, just idle until termination
        if(noThreads) {
          for(;;) {
            try {
              sleep(10000);
            }
            catch(InterruptedException expected) {
            }
            if(terminate) break;
          }
          if(terminate) continue;
        }
        try {
          // clear all of the checks in the list
          itr = thr.values().iterator();
          while (itr.hasNext()) {
            itr.next().setCheck(false);
          }

          // Open file and read each line, process it through all the possibilities
          in = new BufferedReader(new FileReader(configPath + configFile));
          n = 0;
          prta(Util.clear(runsb).append("Read in configfile for FP channels =").append(configPath).append(configFile));
          while ((s = in.readLine()) != null) {
            try {
              if (s.length() < 1) {
                continue;
              }
              if (s.substring(0, 1).equals("%")) {
                break;       // debug: done reading flag
              }
              if (!s.substring(0, 1).equals("#") && !s.substring(0, 1).equals("!")) {
                int comment = s.indexOf("#");
                if (comment >= 0) {
                  s = s.substring(0, comment).trim();  // remove a later comment.
                }
                String[] tk = s.split("[:]");
                // lines look like 
                if (tk.length == 3) {
                  n++;
                  String channel = tk[0];
                  String pickClass = tk[1];
                  String argline = tk[2].trim();
                  ManagerInterface t = thr.get(tk[0]);
                  if (t == null) {
                    if (pickClass.equalsIgnoreCase("CWBFilterPicker")) {
                      prta(Util.clear(runsb).append("Start new ").append(pickClass).append(" ").append(channel).append(" args=").append(argline));
                      //t = new CWBPicker(argline, tk[0], null);
                      t = new CWBFilterPicker6(argline + " >> " + logpath + channel.replaceAll("-", "_") , tk[0], null);
                      thr.put(tk[0], t);
                      //DEBUG:t = new USGSFP6(argline+" >>"+logpath+channel.replaceAll("-","_"), 
                      //        "FP_"+channel, pickSender, jsonPickSender);
                    } // Implement other picker classes here
                    else {
                      prta(Util.clear(runsb).append("Unknown picker class=").append(pickClass));
                      continue;
                    }
                    t.setCheck(true);
                  } else if (!argline.trim().equals(t.getArgs().trim()) || !t.isAlive() || !t.isRunning()) {// if its not the same, stop and start it
                    t.setCheck(true);
                    prta(Util.clear(runsb).append("PM: line changed or dead. alive=").append(t.isAlive()).
                            append(" running=").append(t.isRunning()).append("|").append(tk[0]).append("|").append(tk[1]).append("|").append(tk[1]));
                    prt(argline.trim() + "|\n" + t.getArgs() + "|\n");
                    t.terminate();
                    int loop2 = 0;
                    while (t.isAlive()) {
                      try {
                        sleep(1000);
                      } catch (InterruptedException expected) {
                      }
                      if (terminate) {
                        break;
                      }
                      if (loop2++ >= 10) {
                        if (t.isAlive()) {
                          prta(Util.clear(runsb).append("PM: thread did not die in 10 seconds. ").append(tk[0]).append(" ").append(argline));
                        }
                        break;
                      }
                    }
                    t = new CWBFilterPicker6(argline + " >> " + channel.replaceAll("-", "_") , tk[0], null);
                    thr.put(tk[0], t);
                    t.setCheck(true);
                  } else {
                    t.setCheck(true);
                  }
                } else {
                  prta(Util.clear(runsb).append("Line is wrong format :").append(s));
                }
              }
            } catch (RuntimeException e) {
              prta(Util.clear(runsb).append("PM: runtime error reading config file ").append(s));
              e.printStackTrace(getPrintStream());
            }
          }   // end of read config file lines
          prta(Util.clear(runsb).append("PM: ").append(n).append(" lines read without comment"));
          in.close();
          itr = thr.values().iterator();
          while (itr.hasNext()) {
            ManagerInterface t = itr.next();
            if (!t.getCheck()) {
              // This line is no longer in the configuration, shutdown the Q330
              prta(Util.clear(runsb).append("PM: line must have disappeared ").append(t.toString()).append(" ").append(t.getArgs()));
              t.terminate();
              itr.remove();
            }
          }
        } catch (FileNotFoundException e) {
          prta(Util.clear(runsb).append("PM: Could not find a config file!=").append(configFile).append(" ").append(e));
        } catch (IOException e) {
          prta(Util.clear(runsb).append("PM: error reading the configfile=").append(configFile).append(" e=").append(e.getMessage()));
          e.printStackTrace();
        }
        // Configuration thread creation and monitoring is now complete, start looping for config changes or thread deaths
        // Wait two minutes.  On startup the QueryMom needs some time to setup the QuerySpanCollection
        if (first && n > 0) {
          prta(Util.clear(runsb).append("PM: Wait 10 s for everything to start before doing a configuration"));
          try {
            sleep(10000);
            if (terminate) {
              prta("PM: terminate on startup wait");
              break;
            }
          } catch (InterruptedException e) {
          }
          // handle the disk config file
          try {
            Util.clear(filesb);
            Util.readFileToSB(configPath + configFile, filesb);
          } catch (IOException e) {
            prta(Util.clear(runsb).append("PM: initial read of configuration file failed e=").append(e));
          }
        }
        if (terminate) {
          break;
        }

        // Now check that the file has not changed
        String[] keys = new String[thr.size()];
        keys = thr.keySet().toArray(keys);
        boolean written = false;
        while (true) {     // Keep looping until file changes or a Thread goes down
          if (terminate) {
            break;
          }
          if (dbg) {
            prta(Util.clear(runsb).append("Do Pass= noDB=").append(noDB));
          }
          try {
            if (!noDB) {
              try {
                if (fpmsb.length() > 100) {
                  Util.clear(fpmsb);
                }
                written = pickerStringBuffer(fpmsb, configPath, first);
                prta(Util.clear(runsb).append("PM: build noDB=").append(noDB).
                        append(" len=").append(fpmsb.length()).append(" first=").append(first).
                        append(" written=").append(written));//+"\n"+fpmsb.toString());
              } catch (RuntimeException e) {
                prta(Util.clear(runsb).append("PM: got Runtime in fpmStringBuilder e=").append(e));
                e.printStackTrace(this.getPrintStream());
              }
            } else {     // Hand configuration, force the file and fpmsb to agree
              Util.readFileToSB(configFile, filesb);
              Util.clear(fpmsb);
              fpmsb.append(filesb);
            }
          } catch (IOException e) {
            prta(Util.clear(runsb).append("PM: got IO exception reading file ").append(configFile).append(" e=").append(e));
            e.printStackTrace(getPrintStream());
          }

          //prt("file=\n"+filesb+"|");
          //prt("fpmsb="+fpmsb.length()+"\n"+fpmsb+"|");
          // If the file has not changed check on down threads, if it has changed always to top
          try {
            if (!fpmsb.toString().equals(filesb.toString()) && fpmsb.length() > 100 && written) {
              Util.writeFileFromSB(configPath + configFile, fpmsb);// write out the file
              Util.clear(filesb).append(fpmsb);
              first = false;
              prta(Util.clear(runsb).append("PM: *** files have changed **** old=").append(filesb.length()).append(" new=").append(fpmsb.length()).
                      append(" startup=").append(!(System.currentTimeMillis() - startup > 300000)));
              break;
            } else {
              if (dbg || loop++ % 10 == 1) {
                prta("PM: files are same json=" + jsonPickSender);
              }
              // check to make sure all of our children threads are alive!
              boolean breakout = false;
              for (String key : keys) {
                if (!thr.get(key).isAlive() || !thr.get(key).isRunning()) {
                  prta(Util.clear(runsb).append("PM: Found down thread alive=").append(thr.get(key).isAlive()).
                          append(" run=").append(thr.get(key).isRunning()).append(" ").append(thr.get(key)));
                  SendEvent.debugEvent("FPMgrThrRes", "Unusual restart of " + key, this);
                  breakout = true;
                  break;
                }
              }
              if (breakout) {
                break;     // Some thread is down - break out to go to configuration loop
              }
              if (System.currentTimeMillis() - startup > 300000 && first) {
                Util.writeFileFromSB(configPath + configFile, fpmsb); // write out the file
                first = false;
              }
              try {
                sleep(120000);
              } catch (InterruptedException e) {
              }
              if (terminate) {
                prta("PM: terminate on sleep");
                break;
              }
            }
          } catch (IOException e) {
            prta(Util.clear(runsb).append("PM: got IO exception writing file ").append(configFile).append(" e=").append(e));
            e.printStackTrace(getPrintStream());
          }
          if (System.currentTimeMillis() % 86400000L < 240000) {
            DBConnectionThread db = maintainDBStatus.getDBThread();
            if (db != null) {
              db.setLogPrintStream(getPrintStream());
            }
            //dbconnedge.setLogPrintStream(getPrintStream());
          }
        }
      } catch (RuntimeException e) {
        prta(Util.clear(runsb).append(tag).append("PM:  RuntimeException in ").append(this.getClass().getName()).append(" e=").append(e.getMessage()));
        if (getPrintStream() != null) {
          e.printStackTrace(getPrintStream());
        } else {
          e.printStackTrace();
        }
        if (e.getMessage() != null) {
          if (e.getMessage().contains("OutOfMemory")) {
            SendEvent.edgeEvent("OutOfMemory", "Out of Memory in " + Util.getProcess() + "/"
                    + this.getClass().getName() + " on " + IndexFile.getNode() + "/" + Util.getAccount(), this);
            throw e;
          }
        }
      }
    }     // while(!terminate);
    // The main loop has exited so the thread needs to exit
    prta("PM: start termination of Pickers term=" + terminate);
    itr = thr.values().iterator();
    while (itr.hasNext()) {
      ManagerInterface t = itr.next();
      t.terminate();
    }
    prta("PM: ** PickerManager terminated ");
    try {
      Runtime.getRuntime().removeShutdownHook(shutdown);
    } catch (Exception expected) {
    }
    running = false;            // Let all know we are not running
    terminate = false;          // sign that a terminate is no longer in progress
  }

  /**
   * this internal class gets registered with the Runtime shutdown system and is
   * invoked when the system is shutting down. This must cause the thread to
   * exit
   */
  class ShutdownPickerManager extends Thread {

    /**
     * default constructor does nothing the shutdown hook starts the run()
     * thread
     */
    public ShutdownPickerManager() {
      gov.usgs.anss.util.Util.prta("new ThreadShutdown " + getName() + " " + getClass().getSimpleName());
    }

    // This is called by the RunTime.shutdown at any termination 
    @Override
    public void run() {
      System.err.println("PM: PickerManager Shutdown() started...");
      terminate();          // send terminate to main thread and cause interrupt
      int loop = 0;
      while (running) {
        // Do stuff to aid the shutdown
        try {
          sleep(1000);
        } catch (InterruptedException e) {
        }
        loop++;
      }
      System.err.println("PM: Shutdown() of PickerManager is complete.");
    }
  }

  /**
   *
   * @param lastUpdated A epoch time where each picker description is read if
   * newer than this time
   * @param path A path on which to write the template files, if null, no
   * templates are written
   * @throws IOException If writing the template files produces one.
   */
  private static void loadPickerTemplate(long lastUpdated, String path) throws IOException {
    DBConnectionThread dbpick = maintainDBEdge.getDBThread();
    if (dbpick == null) {
      return;
    }
    try {
      try (ResultSet rs = dbpick.executeQuery("SELECT id,picker,classname,args,template FROM edge.picker WHERE updated > '"
              + Util.ascdatetime(lastUpdated) + "'")) {
        while (rs.next()) {
          Picker p = pickerTemplate.get(rs.getString("picker"));
          if (p == null) {
            p = new Picker(rs);
            pickerIDTemplate.put(rs.getInt("id") + "", p);
            pickerTemplate.put(rs.getString("picker"), p);
          } else {
            p.reload(rs);
          }
          if (path != null) {
            Util.writeFileFromSB(path + p.getPicker() + ".template", p.getTemplate());
          }
        }
      }
    } catch (SQLException e) {
      Util.SQLErrorPrint(e, "Query for Picker configs failed");
      thisThread.prta("PM: error reading db=" + e);
    }
  }
  private long pickerLastUpdated = 100000L;

  /**
   * given a edge or gaux make a string
   *
   * @param sb The string buffer to return configuration text
   * @param path The path for any template files.
   * @param first Is this the first call
   * @return
   */
  public boolean pickerStringBuffer(StringBuilder sb, String path, boolean first) {
    Util.clear(sb);
    // If we do not have access to the pickers, then we should do nothing
    try {
      Picker.pickers = null;
      Picker.makePickers(maintainDBEdge.getDBThread().getNewStatement(false));
    } catch (SQLException e) {
      prta("PM: ** SQLError in Picker.makePickers e=" + e);
      e.printStackTrace(getPrintStream());
      return false;
    }
    if (Picker.pickers == null) {
      return false;
    }
    if (Picker.pickers.isEmpty()) {
      return false;
    }
    Picker.loadChannelsForAllPickers();

    // read in the template
    try {
      loadPickerTemplate(pickerLastUpdated, path);
    } catch (IOException e) {
      prta(Util.clear(runsb).append("PM: IOError getting picker templates e=").append(e.toString()));
      return false;
    }
    QuerySpanCollection qsc = QuerySpanCollection.getQuerySpanCollection();
    if (qsc == null) {
      if (dbg) {
        prta(Util.clear(runsb).append("PM: QSP is clear, cannot build a configuration string!"));
      }
      return false;
    }
    chans.clear();
    qsc.getChannels(chans);
    Collections.sort(chans);
    if (dbg) {
      prta(Util.clear(runsb).append("PM: qsc.getChannels() #=").append(chans.size()));
    }
    int nconfig = 0;
    int nnofp = 0;
    int nnone = 0;
    for (String chan : chans) {
      if (chan.charAt(7) == 'b' || chan.charAt(7) == 'h') {
        continue;
      }
      Channel ch = EdgeChannelServer.getChannel(chan);
      if (chan.contains("USAAM  BHZ") && dbg) {
        prta("PM:" + chan);
      }
      if (ch == null) {
        if (chan.charAt(7) != 'b' && chan.charAt(7) != 'h') {
          prta(Util.clear(runsb).append("PM: ** Weird data channel ").append(chan).append(" is not in the channel table!"));
        }
      } else {
        boolean hasPicker = false;
        for (Picker pick : Picker.getPickersForChannel(chan)) {           
          if(chan.charAt(7) != 'B' && chan.charAt(7) != 'H' && chan.charAt(7) != 'S' && chan.charAt(7) != 'E') {
            prta(Util.clear(runsb).append("PM:  **** non BHES channel has picker set to ").append(pick.getID()).append(" ").append(chan));
          } else {
            if (pick.getPicker().equals("NONE")) {
              //sb.append("#").append(chan).append(" NONE Picker\n");
              nnone++;
            } else {
              hasPicker = true;
              sb.append(chan.replaceAll(" ", "-")).append(pick.getPicker()).append(":").append(pick.getClassname()).append(":").
                      append("-c ").append(chan.replaceAll(" ", "-")).append(" -id ").append(pick.getID()).
                      append(" -rt ").append(ch.getRate()).
                      append(" -template ").append(path).append(pick.getPicker()).append(".template ").
                      append("-path ").append(path).
                      append((pick.getArgs().length() > 0) ? pick.getArgs() : "").
                      append("\n");
              nconfig++;
            }
          }           
        }
        if (!hasPicker) {
          nnofp++;
//          sb.append("#").append(chan).append(" No Picker\n");
        }
      }
    }
    sb.append("#config=").append(nconfig).append(" #NONE=").append(nnone).append(" #nopickID=").append(nnofp).append("\n");
    if (dbg) {
      prta(Util.clear(runsb).append("PM: #config=").append(nconfig).append("#NONE=").append(nnone).
              append(" #noFP=").append(nnofp));
    }
    return nconfig > 3;

  }
  public ArrayList<PickTrack> picks = new ArrayList<>(100);

  /**
   * record every pick with the PickerManager in case a list of recent picks
   * comes into play
   *
   * @param pickID
   * @param agency
   * @param author
   * @param nscl
   * @param pickerType
   * @param picktime
   * @param amplitude
   * @param period
   * @param phase
   * @param error_window
   * @param polarity
   * @param onset
   * @param hipass
   * @param lowpass
   * @param backasm
   * @param slow
   * @param snr
   */
  public final void addPick(long pickID, String agency, String author, String nscl, String pickerType, GregorianCalendar picktime,
          double amplitude, double period,
          String phase, double error_window,
          String polarity, String onset,
          double hipass, double lowpass, int backasm, double slow, double snr) {
    long now = System.currentTimeMillis();
    int index = -1;
    for (int i = 0; i < picks.size(); i++) {
      PickTrack pick = picks.get(i);
      if (pick.pickTime - now > 300000) {
        pick.pickTime = 0L;
        if (index < 0) {
          index = i;
        }
      }
    }
    if (index < 0) {
      PickTrack pick = new PickTrack(pickID, agency, author, nscl, pickerType, picktime,
              amplitude, period,
              phase, error_window,
              polarity, onset,
              hipass, lowpass, backasm, slow, snr);
      picks.add(pick);
    } else {
      picks.get(index).reload(pickID, agency, author, nscl, pickerType, picktime,
              amplitude, period,
              phase, error_window,
              polarity, onset,
              hipass, lowpass, backasm, slow, snr);
    }
  }

  public class PickTrack {

    long pickID, pickTime;
    String agency, author, nscl, pickerType, phase, polarity, onset;
    double amplitude, period, error_window, hipass, lowpass, slow, snr;
    int backasm;

    public PickTrack(long pickID, String agency, String author, String nscl, String pickerType, GregorianCalendar picktime,
            double amplitude, double period,
            String phase, double error_window,
            String polarity, String onset,
            double hipass, double lowpass, int backasm, double slow, double snr) {
      reload(pickID, agency, author, nscl, pickerType, picktime,
              amplitude, period,
              phase, error_window,
              polarity, onset,
              hipass, lowpass, backasm, slow, snr);
    }

    public final void reload(long pickID, String agency, String author, String nscl, String pickerType, GregorianCalendar picktime,
            double amplitude, double period,
            String phase, double error_window,
            String polarity, String onset,
            double hipass, double lowpass, int backasm, double slow, double snr) {
      this.pickID = pickID;
      this.agency = agency;
      this.author = author;
      this.nscl = nscl;
      this.pickerType = pickerType;
      pickTime = picktime.getTimeInMillis();
      this.amplitude = amplitude;
      this.period = period;
      this.phase = phase;
      this.error_window = error_window;
      this.polarity = polarity;
      this.onset = onset;
      this.hipass = hipass;
      this.lowpass = lowpass;
      this.backasm = backasm;
      this.slow = slow;
      this.snr = snr;
    }
  }

  public static void main(String[] args) {
    Util.init("edge.prop");
    Util.setModeGMT();
    PickerManager pickmgr = new PickerManager("-empty >>pmgr", "PMGR");
    Util.sleep(1000);
    int seq = (int) (System.currentTimeMillis() / 1000);
    String pickDB = "localhost/3306:edge:mysql:edge";
    Util.setProperty("PickDBServer", pickDB);
    FakeThread fake = new FakeThread("-empty >>fake", "FAKE");
    /*
      public static synchronized long pickToDB(String agency, String auth, long picktime, double pickerr,
          String channel, double amp, double per, String phase,
          int sequence, int version, double quality, String polarity, String onset, 
          double hipass,double lowpass, int backazm, int backazmerr, double slow, 
          double snr, double powerRatio, int pickerid, 
          double distance, double azimuth, double residual, double sigma, EdgeThread ps) {
     */
    for (int i = 0; i < 10; i++) {
      long id = pickToDB("AGENT", "AUTH", System.currentTimeMillis(), "XXDUG  BHZ99", 1., 2., "P", seq++,
              0, 0., "U", "e", .1, 20., 11, 1, 0., 10., 10., 4, 1., 2., 3., 4., fake);
      Util.prta(i + " id=" + id);
      Util.sleep(1000);
    }
    maintainDBEdge.getDBThread().closeConnection();

    for (int i = 0; i < 10; i++) {
      long id = pickToDB("AGENT", "AUTH", System.currentTimeMillis(), "XXDUG  BHZ99", 1., 2., "P", seq++,
              0, 0., "U", "e", .1, 20., 11, 1, 0., 10., 10., 0, 0., 0., 0., 0., fake);
      Util.prta(i + " id=" + id);
      Util.sleep(1000);
    }
    Util.prt("Done");
  }
}
