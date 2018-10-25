/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.edgemom;

import gov.usgs.anss.edge.RawToMiniSeed;
import gov.usgs.anss.edge.IndexFile;
import gov.usgs.anss.edge.IndexFileReplicator;
import gov.usgs.anss.edge.IndexBlock;
import gov.usgs.anss.edge.EdgeProperties;
import gov.usgs.anss.edge.ChannelSender;
import gov.usgs.alarm.SendEvent;
import gov.usgs.anss.db.DBConnectionThread;
import gov.usgs.anss.edge.EdgeBlockQueue;
import gov.usgs.anss.edge.TimeoutThread;
import gov.usgs.anss.edgeoutput.ChannelHolder;
import gov.usgs.anss.edgethread.EdgeThread;
import gov.usgs.anss.edgethread.EdgeThreadTimeout;
import gov.usgs.anss.edgethread.MemoryChecker;
import gov.usgs.anss.net.SNWSender;
import gov.usgs.anss.seed.MiniSeed;
import gov.usgs.anss.seed.Steim2;
import gov.usgs.anss.util.AnssPorts;
import gov.usgs.anss.util.LoggingStream;
import gov.usgs.anss.util.Util;
import java.io.IOException;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.io.FileNotFoundException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;
import java.util.Iterator;
import java.util.StringTokenizer;
import java.util.ConcurrentModificationException;

/**
 * * EdgeMom.java
 *
 * Created on June 16, 2005, 11:46 AM. One of the earliest of the Edge/CWB developments.
 * <h1><u>Overview</u></h1>
 * This process maintains all of the Threads on a running edge, monitors them, and periodically
 * rereads its input file and :<br><br>
 * 1) kills a thread if its line has gone away<br>
 * 2) kills and restarts a thread if its line has changed<br>
 * 3) adds a thread for any new lines in the file<br>
 * 4) Every 10 minutes dumps the status information from each thread into the log<br>
 * 5) Sends process monitoring information to a central place.<br>
 * 6) if it finds a thread not "running" for 30 seconds, stop and restarts it.<br>
 * 7) Runs the EdgeMomMonitorServer for monitoring via Nagios (key value pairs)<br>
 * 8) Runs the EdgeStatusServer which dumps a status line from each thread to any connection<br>
 * <br>
 * <h1><u>Logging files </u></h1>
 * This uses an internal TestStream to log using the usual 10 day schedule for closing the files. It
 * forces this TestStream into the gov.usgs.anss.util.Util so that other software being run under
 * this, will put their output into the edgemom.log? instead of a SESSION.OUT or other such.
 * <br><br>
 * The EdgeThread that run under here can either record information in the edgemom.log or they can
 * have their own file. This is implemented automatically in the EdgeThread portion according to
 * whether a "logname" for the thread has been set using the '>' or '>>' log output director. If
 * not, they appear in edgemom.log with the "tag" of the thread prepended.
 *
 * <p>
 * <h1><u>Memory Ring Buffer of MiniSEED</u></h1>
 * This thread also starts and maintains the EdgeBlockQueue (in RAM Buffer of MiniSEED blocks). This
 * queue is sized based on the -ebq nnn parameter or the setting of the eqbsize property in the
 * edge.prop file. This memory buffer is used by any output class to examine the data data as it was
 * processed into the EdgeMom and perform output operations. There are currently two main classes
 * that use the memory buffer :<br>
 * 1) OutputInfrastructure which starts threads of type RingFile or RingServerSeedLink to put data
 * into output ringfiles or use the datalink protocol to send the data to a ring server or QueryMom
 * process. 2) EdgeBlockServer - this class is part of the replication protocol to cause files on an
 * edge node to be created exactly the same on one or more CWB nodes. Every block inserted into the
 * RAM ring is forwarded to every attached EdgeBlock client along with the place to put the block in
 * the files and some index information. This is how most of the blocks get to the replicating
 * servers.
 *
 * <p>
 * <h1><u>Monitoring EdgeMom </u></h1>
 * If the ebqsize is defined (this is a EdgeMom instance and not some other usage of this class)
 * then and EdgeMomStatus and EdgeMomMonitor classes are started on the defined port for this
 * instance number on this computer. These two servers dump certain information to a socket for each
 * connection to the socket. The EdgeMomMonitor (port 7800 + instance) output is key-value pairs
 * generally used by ICINGA for monitoring the application performance. The EdgeMomStatus (7890 +
 * instance) server dump the same status output that happens every 10 minutes in the EdgeMom log
 * file - basically a list of information about the edgemom and a status call to each of the EdgeMom
 * managed threads. A user client can connect and see the latest status from the EdgeMom.
 *
 * <h1><u>The edge.prop properties file</u></h1>
 *
 * edge.prop - This property file is alway loaded, but the EDGEMOM/edge_$INSTANCE.prop file is
 * always loaded if the instance is not "^" immediately after. This property file contains many
 * parameters for the edgemom these include :
 *
 * <pre>
 * Parameter	Description
 *
 * ndatapath	The number of datapaths for storing data.  There needs to be a nday and and
 *            datapath definition for each.  That is if ndatapath=3, then nday, nday1 and nday2
 *            must be defined as well as datapath, datapath1, and datapath2.
 *
 * nday?	    The number of days of data held in edge mode in total (normally 10 on an edge node
 *            but 2000000 on a CWB node.  It is the total number of days of data that can be
 *            held on this node  on the ? path before the day files are reused.  Nday is for
 *            the first path, nday1 is for the 2nd path, etc.  For data received directly in
 *            this node (not via replication) if nday is less than 1000 the data files will be written
 *            on the first path only.  If nday > 1000 such files will be written on any path
 *            based on the free space available (this is CWB mode).  There must be a nday for each data path
 *            so if ndatapath=2, then nday and nday1 must be defined.
 *
 * Node	      This is the node name or instance name – this overrides the actual
 *            node name returned by the operating system, if present (it always should be present).
 *            The end of the node should be a unique integer value.  So if a computer
 *            is being used which does not have a unique number, this property can be used
 *            to override it.  Example : if the servers actual name is “bruce” the node
 *            parameter can be set “Node=3#19” and the files from this instance will
 *            contain the 19.  Instances should be unique so node names like 1#2 are encouraged.
 *
 * datapath? 	The path to some directory that will be used to store data.  If the
 *            corresponding nday is small, then this number of days of data will be
 *            on this path before reuse.  There must be a matching number of datapath?
 *            For the number of data paths (ndatapath), so if ndatapath=2, then datapath
 *            and datapath1 must be defined.  That is there must be one nday? for each datapath?\
 *            and there must be one for ndatapath and in order (nday, nday1, nday2, datapath,
 *            datapath1, datapath2 is legal, nday1, nday2, datapath1, datapath2 is not).
 *
 * datamask	 This mask is used to put data in subdirectories on the datapaths.  The form is
 *           YYYY_DDD and can be shortened.  So if the mask is YYYY_D then the subdirectories
 *           will be named like 2012_1 and all files for year 2012 days 100-199 will be put
 *           in this directory (not yet implemented feature).
 *
 * daysize	The number of blocks in a day file when it is first created.
 *
 * extendsize	The number of blocks to add to a day file when more space is needed
 * logfilepath	The path to the directory where the logs are to be written
 *
 * emailTo	 The email address to receive certain messages from the system.  The alarm
 *           system generally handles e-mails now, but certain older code still sends e-mails
 *           to this address
 *
 * DBServer   The dabase URL used to configure this system – this leads to the edge, anss, portables, databases.
 *            The form is IP.ADR/PORT:database:vendor:schema.
 *
 * StatusDBServer The database URL used for status information (DAS Status, latency, holdings, etc).
 *            The form is IP.ADR/PORT:database:vendor:schema.
 *
 * MetaDBServer The database URL used for the metadata database.
 *            The form is IP.ADR/PORT:database:vendor:schema.
 *
 * KillPages	[false]|[true] kill any pages that might originate in the older paging system
 *            (not those done through the alarm/event system).  Most such pages have been
 *            replaced and this  variable will eventually be eliminated
 * ebqsize	  The size of the edge block queue in blocks.  This overrides the default in
 *            edgemom of 10000 blocks.  This queue holds blocks as they are received.
 *            Various output threads take blocks out of this queue independently.  The
 *            Replicator, and OutputInfrastructure use this queue as the source of data.
 *
 * SNWHost 	If present, overrides where the SNWServer  SocketServer is expected to be running. Omit for no SNW
 *
 * SNWPort	If present, overrides where the SNWServer SocketServer is listening.
 *
 * SMTPServer  The IP address of the SMTP server which will handle outgoing e-mails from the SimpleSMTPThread or Alarm modules.
 *
 * SMTPFrom  This is the e-mail address to make as the “FROM” in e-mail messages.  This is
 *           often used like 'gacq3-vdl3@usgs.gov' so that the e-mails appear to come from
 *           certain nodes and users to make it easier to match e-mails with the source without looking at the body of the messages.
 *
 * statertms  Set the number of seconds to wait before a compression run becomes stale and needs to be forced out.  The default is
 *            1800.  This parameter can also be set on the command line with -stalertms nn.
 *
 * AlarmIP	The comma separated list of  IP addresses of the Alarm module server which
 *          should get UDP packets from the application SendAlarm generated messages.
 *
 * RTSServer	For use only with USGS stations using the RTS field modules, the IP address of the RTSServer software.
 *
 * StatusServer This is the IP address of the UdpChannel server(computer where status processing is handled for all nodes).
 * </PRE>
 * <br><br>
 * <h1><u>Threads under edgemom</u></h1>
 * See EdgeThread class document for latest details on the command line. By convention most lines
 * that can be started in a EdgeMom are in the package gov.usgs.anss.edgemom, but it is not
 * required. For instance this class is used in QueryMom and their many of the classes started are
 * not in this package. Note the EdgeConfig GUI system allong with the other configuration processes
 * generally are used to make files of this type. This is documented here both for nonGUI sites and
 * to make understanding the edgemom.setup file easier.
 * <br>
 * Line format:<br>
 * <PRE>
 * tag[!][/pri]:class:[args][>[>]filename]
 * tag       should be short and will be on all output from this thread through prt() to edgemom.log?.
 *           The ! indicates the thread does not support monitor calls (probably not a java thread)
 * pri       is the thread priority - if omitted it will be 5.  Min priority=1 max=10
 * class     if it does not contain any periods gov.usgs.anss.edgemom is presumed.  A fully qualified name can be used.
 * args      command line args to the thread, should be parsed to control set up
 * filename  output is redirected to filename.log? where the .log? is managed by the EdgeThread for the thread
 *           if a > is not present, output goes to edgemom.log? >> means append to the file on startup.
 * </PRE>
 * <br>
 * There must be an EdgeMom line in the configuration file and the tag must be ' mom' so it sorts
 * first in the output file. Here is an example edgemom.setup file :
 * <br>
 * <PRE>
 *#
 *#  Form is "unique_key:Class_of_Thread:Argument line [>][>>filename no extension]
 *# This is the mandatory ' mom' line.
 * mom:gov.usgs.anss.edgemom.Edgemom:-nosnw -empty
 *# Get data from AFTAC
 *RIAR:RawInputServer:-p 7209
 *Echn:gov.usgs.anss.edgemom.EdgeChannelServer:-empty
 *Hydra:Hydra:-mhost 1.2.3.4 -host 1.2.3.255 -wait 181 >>hydra
 *# Serve edge blocks to the CWB
 *IFSrv:IndexFileServer:-p 7979 >>ifs
 *EBSrv:EdgeBLockServer:-p 7983 >>ebq
 * </PRE>
 * <br>
 * <h1><u> Command Line arguments for EdgeMom</u></h1>
 * The command line arguments can be passed on the command line, or if the command line arguments
 * are not present they will be parsed from the "mom" line in the configuration file.
 * <br>
 * <PRE>
 * example : java -jar EdgeMom.jar [-f filename][-dbg][-dbgblk][.....]
 *It is EdgeThread and the mother of the configured EdgeThreads :
 * Tag   arg          Description
 * -f        filename file name of setup file def=edgemom.setup
 * -maxpurge n        Set limit to start purging open files that are 5 minutes unused to this level (def=500)
 * -nosnw             Turnoff any output to the SNW server via SNWSender from any thread within this EdgeMom
 * -stalesec nn       Set the stale setting for closing files to this number of seconds (def=43200)
 * -stalertms nn      Set the number of seconds to wait for no additional data to RTMS before closing out a compression run (def=1800)
 * -logname  file     Set the log file name to something other thant 'edgemom'
 * -i        nn#nn    Set the instance to this value, this overrides edge.prop property Node!
 * -prop     File     If present, this is the last property file to be loaded and will overrule any other
 * Debugging related switches:
 * -dbg               Turn on debugging
 * -dbgblk            Turn on logging for each data block written
 * -dbgebq            Turn on logging for each data block to replication
 * -dbgrtms           Turn on debugging in RawToMiniSeed
 * -dbgifr            Turn on debugging in IndexFileReplicator
 * -eqbsave filename  Turn on saving of EdgeBlockQueue to journal filename
 * -ebq    nnnn       Set size of the EdgeBLock Queue default="+ebqsize);
 * -console           Set use console flag on - cause console output
 * -notimeout         Turn off thread time outs - good for useing debugger
 * -traceSteim        Turn on logging of Steim2.traceBackErrors()
 * -notimeout         Set the EdgeThreadTimeout to no timeout mode
 * -rtmstps           Set the RawToMiniSEED tolerance per sample (default in RTMS is 2 that is 1/2 sample interval for gap detection)
 * -logname  name     Use name as the bases for log file (def=edgemom)
 * -stalesec nnnn     Number of seconds before a file is considered stale
 * -nozeroer          If present to no zero ahead, use this only on filesystems that return zero on unwritten blocks in a file
 * -zeroahead         The zeroer is to try to be this many megabytes ahead of the last data block in megabytes (def=1)
 * -?                 Listing of these options
 * </PRE>
 *
 * @author davidketchum
 */
public final class EdgeMom extends Thread {

  private static ArrayList<EdgeMom> edgemoms;
  private static String instance = "";
  private EdgeBlockQueue ebq;
  private final Map<String, SubThread> thr;  // ordered list of parsed lines kept in SubThread objects
  private final String filename;
  private static StringBuffer sb;
  private static boolean noCheckProperties;
  private static String extraProperties;
  //public static boolean shuttingDown;
  private boolean dbg;
  private final ShutdownEdgeMom shutdown;
  private static long lastday;              // Used to track day roll overs for log file changes
  public static LoggingStream out;             // Output log stream
  private final DecimalFormat df1;

  public static boolean isShuttingDown() {
    return Util.isShuttingDown;
  }
  private final MemoryChecker memoryChecker;
  public static String startedAt;
  public static int stalems;
  public static String roles;
  public static String ip;
  public static int zeroAhead = 1;
  public static int state;
  private EdgeMomMonitorServer emms;  // THis is used by Nagios/Icinga to get keyvalue pairs for monitoring
  private EdgeStatusServer ess;       // This is seldom used, but does dump the status strings when opened
  private final String thrtag;
  private final StringBuilder tmpsb = new StringBuilder(1000);
  private static int instanceNumber;
  private static int staleRTMS = 1800000;

  public static void setExtraProperties(String prop) {
    extraProperties = prop;
  }

  public static void setNoCheckProperties(boolean t) {
    noCheckProperties = t;
  }

  public static int getEdgeMomState() {
    return state;
  }

  public static String getInstance() {
    return instance;
  }

  public static int getInstanceNumber() {
    return instanceNumber;
  }

  public static void setInstance(String ins) {
    instance = ins;
  }

  @Override
  public String toString() {
    return "instance=" + instance + " " + Version.version + " #thr=" + thr.size() + " " + filename;
  }

  /**
   * print a string with the time prepended. Ends up in the file based on thread creation
   *
   * @param s The string to output.
   */
  public static void prta(String s) {
    EdgeThread.staticprta(s);
  }

  /**
   * Output a string. This also causes new log files to be created as days roll over based on the
   * Julian day of the system time. The output unit is chosen based on the redirect.
   *
   * @param s The string to print
   */
  public static void prt(String s) {
    EdgeThread.staticprt(s);
  }

  public static void prt(StringBuilder s) {
    EdgeThread.staticprt(s);
  }

  public static void prta(StringBuilder s) {
    EdgeThread.staticprta(s);
  }

  public void setDebug(boolean t) {
    dbg = t;
  }

  public static StringBuffer getStatusPage() {
    return sb;
  }

  public static void setNoConsole(boolean t) {
    prta("setNoConsole=" + t);
    LoggingStream.setNoConsole(t);
  }

  public void setSaveEdgeBlockQueue(String file) {
    ebq.rawSaveOn(file);
  }

  public static ArrayList<EdgeMom> getEdgeMoms() {
    return edgemoms;
  }

  public EdgeThread getEdgeThread(String tg) {
    SubThread t = thr.get(tg);
    if (t == null) {
      return null;
    } else {
      return (EdgeThread) t.getEdgeThread();
    }
  }

  /**
   * Creates a new instance of EdgeMom
   *
   * @param fl The filename of the configuration file. Default is edgemom.setup
   * @param ebqsize The size in blocks of the EdgeBlockQueue (which would feed a EdgeBlockServer)
   * @param con If true, allow output to console rather than log file
   * @param tg The tag to use during output
   */
  public EdgeMom(String fl, int ebqsize, boolean con, String tg) {
    if (edgemoms == null) {
      edgemoms = new ArrayList<>();
    }
    filename = fl;
    thr = (Map<String, SubThread>) Collections.synchronizedMap(new TreeMap<String, SubThread>());
    String[] r = Util.getRoles(null);
    roles = "";
    thrtag = tg;
    if (r != null) {
      for (String r1 : r) {
        roles += r1 + " ";
      }
    }
    ip = "Unknown";
    try {
      ip = InetAddress.getLocalHost().toString().substring(1);
    } catch (UnknownHostException e) {
    }
    EdgeThread.setUseConsole(con);
    LoggingStream.setNoInteractive(true);
    startedAt = Util.ascdate() + " " + Util.asctime();
    gov.usgs.anss.util.Util.prta(Util.clear(tmpsb).append("new Thread ").append(getName()).append(" ").append(getClass().getSimpleName()));
    prt(Util.clear(tmpsb).append(thrtag).append(" ").append(Version.version).
            append(" start up ").append(Util.ascdate()).append(" ").
            append(Util.asctime()).append(" as pid=").append(Util.getPID()).
            append(" on ").append(Util.getNode()).append("/").append(instance).
            append("/").append(System.getProperty("user.name")).
            append(" IndexNode=").append(IndexFile.getNode()).append(" zeroAhead=").append(zeroAhead).append(" ebqsz=").append(ebqsize));
    prta(Util.clear(tmpsb).append("setting up the EBQ for ebqsize=").append(ebqsize).append(" stalertms=").append(staleRTMS / 1000).
            append(" Java Vers=").append(System.getProperty("java.version")).
            append(" JRE Manuf=").append(System.getProperty("java.vendor")).
            append(" Install directory=").append(System.getProperty("java.home")));

    if (ebqsize > 0) {
      ebq = new EdgeBlockQueue(ebqsize);
    }
    shutdown = new ShutdownEdgeMom();
    LoggingStream.setNoInteractive(true);
    Runtime.getRuntime().addShutdownHook(shutdown);
    memoryChecker = new MemoryChecker(120, true, null);   // check memory every two minutes and exit if it says too low.
    df1 = new DecimalFormat("0.0");
    start();
  }

  @Override
  public void run() {
    edgemoms.add(this);
    try {
      if (!IndexFile.getNode().equals("11") && !Util.getSystemName().equals("gldpdf") && !IndexFile.getNode().equals("10")) {
        SendEvent.edgeEvent(thrtag + "Startup", filename.replaceAll("EDGEMOM/", "") + " on " + Util.getSystemName() + "/" + System.getProperty("user.name") + " " + roles + " " + ip,
                this);
      }
    } catch (RuntimeException e) {
      prta("Runtime when starting - host name must not be known?");
      Util.exit("EdgeMom startup panic");
    }
    String s = null;
    BufferedReader in = null;
    int n = 0;
    ArrayList<StringBuilder> longs = new ArrayList<>(100);
    StringTokenizer tk;
    String tag;
    String name;
    String args;
    Iterator itr;
    SubThread st;
    StringBuilder msPerf = new StringBuilder(50);

    int statusDivisor = 20;
    int statusRemainder = 3;
    sb = new StringBuffer(2000);
    String nd = IndexFile.getNode();
    instanceNumber = IndexFile.getInstanceNumber(IndexFile.getNode());  // note instanceNumber is now instance number
    if (ebq != null) {
      emms = new EdgeMomMonitorServer(AnssPorts.MONITOR_EDGEMOM_PORT + instanceNumber); // 7800 + instanceNumber
      ess = new EdgeStatusServer(AnssPorts.MONITOR_EDGEMOM_STATUS_PORT + instanceNumber); //7890 + instanceNumber
    } else {
      emms = new EdgeMomMonitorServer(AnssPorts.MONITOR_CWBWAVESERVER_PORT + instanceNumber); // 7860 + instanceNumber 
    }

    prta(Util.clear(tmpsb).append(Util.ascdate()).append(" ").append(thrtag).append(" startup on filename=").append(filename).
            append(" usernumber=").append(instanceNumber).
            append(" stalertms=").append(staleRTMS).append(" noChkProp=").
            append(noCheckProperties).append(" extraprop=").append(extraProperties));
    prta(IndexFile.getPathString());
    File f = new File(thrtag + "_" + instance + ".threads");
    if( f.exists()) {
      f.renameTo(new File(thrtag + "_" + instance + ".threads_old"));
    }
    // Loop forever montitoing configuration changes, restarting threads, printing status, etc.
    long count = 0;
    while (true) {
      state = 1;
      try {
        count++;
        Util.clear(sb);
        if (count % statusDivisor == statusRemainder) {
          state = 2;
          Util.clear(msPerf);
          MiniSeed.getPerfString(msPerf);
          sb.append(Util.asctime()).append(" ").append(Util.ascdate()).append(" ").append(thrtag).
                  append(" : file=").append(filename).append(" #SubThrs=").
                  append(thr.values().size()).append(" #thr=").
                  append(Thread.activeCount()).append(" RTMS:").append(RawToMiniSeed.getStatus()).append("\n").
                  append("  ").append(Util.getSBPoolStatus()).append("\n").
                  append("  ").append(msPerf).append("\n");
        }
        state = 3;
        try {
          // clear all of the checks in the list
          itr = thr.values().iterator();
          while (itr.hasNext()) {
            ((SubThread) itr.next()).setCheck(false);
          }

          // Open  setup file and read each line, process it through all the possibilities
          in = new BufferedReader(new FileReader(filename));
          state = 4;
          n = 0;
          while ((s = in.readLine()) != null) {
            if (s.length() < 1) {
              continue;
            }
            if (s.trim().equals("")) {
              continue;
            }
            if (s.substring(0, 1).equals("%")) {
              break;       // debug: done reading flag
            }
            if (!s.substring(0, 1).equals("#")) {
              int comment = s.indexOf("# ");
              s = s.replaceAll("  ", " ");
              s = s.replaceAll("  ", " ");
              s = s.replaceAll("  ", " ");
              state = 5;
              if (comment >= 0) {
                s = s.substring(0, comment).trim();  // remove a later comment.
              }
              tk = new StringTokenizer(s, ":");
              if (tk.countTokens() == 3) {
                state = 50;
                n++;
                tag = tk.nextToken().trim();
                if (tag.equalsIgnoreCase("mom")) {
                  continue;   // This line is handled by main!
                }
                name = tk.nextToken().trim();
                if (!name.contains(".")) {
                  name = "gov.usgs.anss.edgemom." + name; // Default package for threads.
                }
                args = tk.nextToken().trim().replaceAll(";", ":");
                state = 51;
                // Skip all threads but EdgeChannel Server and EdgeMom if this is the first time
                if (count == 1 && !name.contains("EdgeChannelServer") && !name.contains("EdgeMom")) {
                  continue;
                }
                st = (SubThread) thr.get(tag);

                // if null, then this is a new line, create a new subThread
                try {
                  if (st == null) {
                    state = 52;
                    prta(Util.clear(tmpsb).append(thrtag).append(": New Line found tag=").append(tag).append(" s=").append(s));
                    st = new SubThread(tag, name, args);
                    st.setCheck(true);
                    thr.put(tag, st);
                    state = 53;
                    int iwait = 0;
                    while (!st.isRunning() && iwait < 20) {
                      try {
                        sleep(100);
                      } catch (InterruptedException e) {
                      }
                      iwait++;
                    }
                    state = 54;
                    if (iwait >= 20) {
                      Util.prt(Util.clear(tmpsb).append("Starting Tread ").append(st.getTag()).append(" did not get to running in 2 sec!"));
                    }
                  } else {          // found, has it changed?
                    state = 55;
                    if (!st.getLine().equals(args) || st.checkRunning() || !st.isAlive()) {    // Yes, terminate and create new
                      state = 56;
                      prta(Util.clear(tmpsb).append(thrtag).append(": Need to terminate and restart ").
                              append(st.getTag()).append(st.getEdgeThread().toString()).
                              append(" chkRun=").append(st.checkRunning()).append(" isActive=").append(st.isAlive()));
                      prta(Util.clear(tmpsb).append(st.getStatusString()));
                      prta(Util.clear(tmpsb).append("   was:").append(st.getLine()).append("|"));
                      prta(Util.clear(tmpsb).append("    is:").append(args).append("|"));
                      state = 57;
                      st.terminate();
                      state = 58;
                      prta(Util.clear(tmpsb).append("    Termination done.").append(st.getStatusString()));
                      try {
                        sleep(100);
                      } catch (InterruptedException e) {
                      }
                      st = new SubThread(tag, name, args);
                      thr.put(tag, st);
                      state = 59;
                      st.setCheck(true);
                      try {
                        sleep(100);
                      } catch (InterruptedException e) {
                      }// give it a chance to start
                    } else {
                      st.setCheck(true);
                    } // its unchanged, check it off
                  }
                } catch (IllegalAccessException e) {
                  prta(Util.clear(tmpsb).append(thrtag).append(": Illegal Access exception creating/terminating :").append(s));
                } catch (ClassNotFoundException e) {
                  prta(Util.clear(tmpsb).append(thrtag).append(": ClassNotFoundExceptions for :").append(s));
                  e.printStackTrace();
                  e.printStackTrace(EdgeThread.getStaticPrintStream());
                } catch (NoSuchMethodException e) {
                  prta(Util.clear(tmpsb).append(thrtag).append(": NoSuchMethodException for :").append(s));
                } catch (InstantiationException e) {
                  prta(Util.clear(tmpsb).append(thrtag).append(": InstantiationException creating: ").append(s));
                } catch (InvocationTargetException e) {
                  prta(Util.clear(tmpsb).append(thrtag).append(": ****** InvocationTargetException creating :").append(s));
                  prta(Util.clear(tmpsb).append(thrtag).append(": ******InvocationTargetException cause=").
                          append(e.getCause()).append("=").append(e.getCause().getMessage()));
                  e.printStackTrace();
                  if (e.toString().contains("Too many open file")) {
                    Util.exit(109);
                  }
                  if (e.getMessage() != null) {
                    if (e.getMessage().contains("Too many open file")) {
                      Util.exit(109);
                    }
                  }
                  if (e.toString().contains("OutOfMemoryError")) {
                    prta(Util.clear(tmpsb).append(thrtag).append(": OutOfMemoryError trying to make an edge thread - exit"));
                    e.printStackTrace();
                  }
                } catch (java.lang.OutOfMemoryError e) {
                  Util.prt(Util.clear(tmpsb).append(" ****** ").append(thrtag).append(" start thread OutOfMemory.. system.exit ").
                          append(name).append(" ").append(tag).append(" args=").append(args));
                  SendEvent.edgeSMEEvent("OutOfMem", filename + " Edge mom got out-of-memory system.exit e=" + e, this);
                  e.printStackTrace();
                  Util.exit(1);
                }

              } else {
                prt(Util.clear(tmpsb).append(thrtag).append(" : line wrong format skipped at line=").append(n).append(":").append(s));
              }
            }       // end if this is not a comment line
          }         // While more input is returned
          // If this is the first time, then wait for the EdgeChannelServer and then start the other threads
          if (count == 1) {
            int i;
            for (i = 0; i < 1000; i++) {
              if (EdgeChannelServer.isValid()) {
                break;
              }
              if (i % 100 == 99) {
                Util.prta("EdgeMom: waiting for EdgeChannelServer " + i);
              }
              try {
                sleep(100);
              } catch (InterruptedException e) {
              }
            }
            Util.prta("EdgeMom: EdgeChannelServer is valid!");
            continue;     // go read the file again
          }
          state = 6;
        } catch (FileNotFoundException e) {
          prta(Util.clear(tmpsb).append(thrtag).append(" cannot find setup file=").append(filename));
          prta(Util.clear(tmpsb).append("File not found=").append(e.getMessage()).append(" ").append(e.toString()));
          SendEvent.edgeSMEEvent("CfgNotFound", filename + " Was not found.  count=" + count + " e=" + e, this);

          e.printStackTrace();
          if (count == 0) {
            Util.exit(0);    // This is fatal if the first time
          }
        } catch (IOException e) {
          prta(Util.clear(tmpsb).append(thrtag).append(" readLine gave IOException ").append(filename).
                  append(":").append(n).append(" ").append(e.getMessage()));
          prta(Util.clear(tmpsb).append("Last line=").append(s));
        }
        state = 7;
        if (in != null) {
          try {
            in.close();
          } catch (IOException e) {
            Util.IOErrorPrint(e, thrtag + ":err closing file");
          }
        }
        // Iterate through and make sure all of the SubThreads are accounted for 
        // (else drop it, its line must have been dropped in the file!)
        itr = thr.values().iterator();

        int nthr = 0;
        longs.clear();
        while (itr.hasNext()) {
          state = 8;
          nthr++;
          st = (SubThread) itr.next();

          // DEBUG: look for threads not running
          if (!st.isRunning()) {
            prta(Util.clear(tmpsb).append("isRunning False for ").append(st.getStatusString()));
          }

          // Get any output that has accumulated for this process
          st.doConsoleLog();

          // If its time, dump the status of each string 
          if (count % statusDivisor == statusRemainder) {
            state = 9;
            //prt(st.getTag()+"|"+st.getStatusString());
            longs.add(st.getStatusString());
            if (longs.get(longs.size() - 1).charAt(longs.get(longs.size() - 1).length() - 1) == '\n') {
              longs.get(longs.size() - 1).delete(longs.size() - 1, longs.size());
            }
          }
          state = 11;
          // if we find any that are not checked off, then they must be destroyed
          if (!st.getCheck()) {
            state = 10;
            prta(Util.clear(tmpsb).append(thrtag).append(": drop task =").append(st.getTag()).append(":").
                    append(st.getType()).append(":").append(st.getLine()));
            try {
              st.terminate();
            } catch (IllegalAccessException e) {
              prta(Util.clear(tmpsb).append(thrtag).append(": IllegalAccess - Dropped thread=").append(s));
            } catch (InvocationTargetException e) {
              prta(Util.clear(tmpsb).append(thrtag).append(": InvocationTarget - Dropped thread=").append(s));
            }
            itr.remove();
          }
          state = 12;
        }       // end iterate through each thread
        state = 13;

        // Reload the properties files periodically
        if (count % 10 == 1) {   // every 300 seconds
          if (!noCheckProperties) {
            chkProperties();      // reload the properties file.
          }
        }

        state = 14;
        // is it time for some status output?
        if (count % statusDivisor == statusRemainder) {
          state = 15;
          IndexBlock.writeStale(1800000);    // Update any index blocks that have not been use lately
          state = 151;
          IndexFile.closeStale(stalems);   // close any file we have not used lately
          state = 152;
          RawToMiniSeed.forceStale(staleRTMS);  // force out any RTMS that has not been used for 1/2 hour
          state = 153;
          prt(Util.clear(tmpsb).append(sb).append("\n"));
          state = 154;
          for (StringBuilder long1 : longs) {
            prt(long1);
          }
          state = 155;
          prt(IndexFile.getFullStatus());
          prt(DBConnectionThread.getStatus());
          state = 157;
          prt(ChannelSender.getMonitorString());
          state = 158;
          if ((count / statusDivisor) % 12 == 11) {
            prt(ChannelHolder.getSummary());
          }
        }
        state = 16;
        // Dump out the threads periodically
        if (count % 60 == 2) {
          state = 17;
          try {
            try (FileWriter outp = new FileWriter(thrtag + "_" + instance + ".threads")) {
              state = 171;
              StringBuilder tmp = Util.getThreadsString(20000);
              state = 172;
              for (int i = 0; i < tmp.length(); i++) {
                outp.write((byte) tmp.charAt(i));
              }
            }
            state = 173;
          } catch (IOException e) {
            prta("Could not write threads file");
          }

        }
        if (emms.poke()) {
          try {
            emms.terminate();
            sleep(1000);
          } catch (InterruptedException e) {
          }
          prta("***** Had to restart EdgeMomMonitorServer emss=" + emms + " alive=" + emms.isAlive()
                  + " shuttingdown=" + Util.isShuttingDown());
          if (emms.isAlive()) {
            SendEvent.edgeSMEEvent("EMMSNoExit", instance + " " + emms + " remains alive!", this);
          }
          if (ebq != null && !Util.isShuttingDown()) {
            emms = new EdgeMomMonitorServer(AnssPorts.MONITOR_EDGEMOM_PORT + instanceNumber); // 7800 + instanceNumber
            ess = new EdgeStatusServer(AnssPorts.MONITOR_EDGEMOM_STATUS_PORT + instanceNumber); //7890 + instanceNumber
          } else {
            if(!Util.isShuttingDown()) {
              emms = new EdgeMomMonitorServer(AnssPorts.MONITOR_CWBWAVESERVER_PORT + instanceNumber);// 7860 + instanceNumber
            }  
          }
        }
        state = 20;
        try {
          sleep(30000L);
        } catch (InterruptedException e) {
        }   // Wait 30 seconds
        state = 21;
        if (Util.isShuttingDown) {
          break;
        }
      } catch (RuntimeException e) {
        prta(Util.clear(tmpsb).append("RuntimeExcp in ").append(thrtag).append(" e=").append(e));
        e.printStackTrace();
        SendEvent.edgeSMEEvent("RunTimeMom", "Runtime in Mom e=" + e, this);
      }
    }     // While(true) forever loop!
    memoryChecker.terminate();
    state = 100;
    prt(Util.getThreadsString());
    prta(thrtag + " main run() exit.");
  }
  private static final StringBuilder propsb = new StringBuilder(100);

  private static void chkProperties() {
    Util.loadProperties("edge.prop");
    if (!instance.equals("^")) {
      Util.loadProperties("EDGEMOM/edge_" + instance.replaceAll("Q", "#") + ".prop");
    }
    if (Util.getProperty("stalertms") != null) {
      staleRTMS = Integer.parseInt(Util.getProperty("stalertms")) * 1000;
    }
    if (extraProperties != null) {
      Util.loadProperties(extraProperties);
    }
    if (Util.getProperty("stalesec") != null) {
      stalems = Integer.parseInt(Util.getProperty("stalesec")) * 1000;
    }
    prta(Util.clear(propsb).append("stalems=").append(stalems).append(" staleRTMS=").append(staleRTMS));
  }

  /**
   * this inner class keeps track of one line of the input files (one thread which might be a
   * subprocess. It can return its components, the thread object and call the terminator on the
   * thread
   */
  public final class SubThread implements Comparable {

    private String tag;
    private StringBuilder tagsb = new StringBuilder(5);
    private String type;
    private String line;
    private final EdgeThread obj;
    private int edgePriority;
    private final Constructor lineConstructor;
    private final Method terminateMethod;
    private final Method getConsoleOutputMethod;
    private final Method getMonitorStringMethod;
    private boolean check;
    private long lastRunMillis;
    public StringBuilder sb = new StringBuilder(100);

    @Override
    public String toString() {
      return "ST:" + tag + " alive=" + obj.isAlive() + " " + line;
    }

    public long getBytesIn() {
      return obj.getBytesIn();
    }

    public long getBytesOut() {
      return obj.getBytesOut();
    }

    public StringBuilder getStatusString() {
      if (sb.length() > 0) {
        sb.delete(0, sb.length());
      }
      sb.append(obj.getTag()).append(" alive=").append(("" + obj.isAlive()).substring(0, 1)).
              append(" ").append(("" + obj.isRunning()).substring(0, 1)).append(" ").
              append(obj.getStatusString());
      return sb;
    }

    /**
     * return the running flag from the object
     *
     * @return true if this SubThread is running
     */
    public boolean isRunning() {
      if (obj != null) {
        return obj.isRunning();
      }
      return false;
    }

    public boolean isAlive() {
      return obj.isAlive();
    }

    /**
     * if the SubThread is currently running or it has been down for less than 30 seconds return
     * false, if not return true
     *
     * @return True if object has not ran for 30 seconds
     */
    public boolean checkRunning() {
      if (obj.isRunning()) {
        lastRunMillis = System.currentTimeMillis();
        return false;
      }
      if ((System.currentTimeMillis() - lastRunMillis) > 30000) {
        prta(tag + " checkRunning is true.");
        return true;
      }
      return false;       // not long enough yet
    }

    public SubThread(String t, String ty, String l)
            throws ClassNotFoundException, NoSuchMethodException,
            IllegalAccessException, InstantiationException, InvocationTargetException {
      check = true;
      lastRunMillis = System.currentTimeMillis();
      tag = t;
      type = ty;
      line = l;
      tagsb.append("=1\n");
      String[] parts = tag.split("/");
      if (parts.length > 1) {
        tag = parts[0];
        try {
          edgePriority = Integer.parseInt(parts[1]);
          if (edgePriority < Thread.MIN_PRIORITY || edgePriority > Thread.MAX_PRIORITY) {
            Util.prta("Edge Tread priority out of range =" + edgePriority
                    + " MIN=" + Thread.MIN_PRIORITY + " MAX=" + Thread.MAX_PRIORITY);
            edgePriority = 0;
          }
        } catch (NumberFormatException e) {
          prta("Priority format error for " + parts[1] + " no action.");
          edgePriority = 0;
        }
      }
      Class[] em = new Class[2];
      // Build one string argument constructor for class type,Show its a one String argument
      em[0] = ty.getClass();
      em[1] = ty.getClass();
      //prta("ST:get string,string constructor for "+type);
      lineConstructor = Class.forName(type).getConstructor(em);

      // get method for terminating this thread (no arguments)
      em = new Class[0];
      //prta("ST:get terminate() for "+type);
      terminateMethod = Class.forName(type).getMethod("terminate", em);
      //prta("ST:get getConsoleOutput() for "+type);
      getConsoleOutputMethod = Class.forName(type).getMethod("getConsoleOutput", em);
      getMonitorStringMethod = Class.forName(type).getMethod("getMonitorString", em);
      // create an instance of this class using the line argument
      Object[] args = new Object[2];
      args[0] = line.replaceAll("  ", " ").replaceAll("  ", " ");
      args[1] = tag;
      //prta("ST:get newInstance()");
      obj = (EdgeThread) lineConstructor.newInstance(args);
      if (edgePriority != 0) {
        obj.setPriority(edgePriority);
      }
      gov.usgs.anss.util.Util.prta("new ThreadProcess " + getName() + " " + getClass().getSimpleName() + " tag=" + t + " ty=" + ty + " l=" + l);
      prta(ty + " EdgePriority=" + edgePriority + " is now=" + obj.getPriority() + " Min="
              + Thread.MIN_PRIORITY + " max=" + Thread.MAX_PRIORITY);
    }

    public StringBuilder getMonitorString() throws IllegalAccessException, InvocationTargetException {
      if (tag.contains("!")) {
        return tagsb;
      }
      return (StringBuilder) getMonitorStringMethod.invoke(obj, (Object[]) null);
    }

    //* cause the object thread to execute its terminate method */
    public void terminate() throws IllegalAccessException, InvocationTargetException {
      terminateMethod.invoke(obj, (Object[]) null);
    }

    public String getConsoleOutput() throws IllegalAccessException, InvocationTargetException {
      return (String) getConsoleOutputMethod.invoke(obj, (Object[]) null);
    }

    public Thread getEdgeThread() {
      return obj;
    }

    public String getTag() {
      return tag;
    }

    public String getType() {
      return type;
    }

    public String getLine() {
      return line;
    }

    public void setCheck(boolean b) {
      check = b;
    }

    public boolean getCheck() {
      return check;
    }

    @Override
    public int compareTo(Object o) {
      return tag.compareTo(((SubThread) o).getTag());
    }

    public void doConsoleLog() {
      ((EdgeThread) obj).doConsoleLog();
    }
  }

  /**
   * This implements the shutdown for a edgemom. It forces output, sends terminates to all the
   * SubThreads and waits for shutdown to be completed.
   */
  class ShutdownEdgeMom extends Thread {

    public ShutdownEdgeMom() {
      gov.usgs.anss.util.Util.prta("new ThreadShutdown " + getName() + " " + getClass().getSimpleName());
    }

    // This is called by the RunTime.shutdown at any termination 
    @Override
    public void run() {
      Util.isShuttingDown = true;
      prta(thrtag + " Shutdown: started..." + Util.getNode() + "/" + Util.getAccount());
      /*if(!IndexFile.getNode().equals("11") && !Util.getNode().equals("gldpdf") && !IndexFile.getNode().equals("10")) {
        SendEvent.edgeEvent(thrtag+"Sdwn",filename.replaceAll("EDGEMOM/","")+" on "+Util.getSystemName()+"/"+System.getProperty("user.name")+" "+roles+" "+ip,
            this);
      }*/

      // On nonwindows systems, insure a kill -9 is queued for a minute later to make sure it shuts down
      if (!Util.getOS().contains("Window")) {
        try {
          gov.usgs.anss.util.Subprocess sp = new gov.usgs.anss.util.Subprocess("bash -l kill9.bash " + Util.getPID() + " EdgeMom");
          Util.sleep(500);
          Util.prt("kill9.bash stdout=" + sp.getOutput() + " stderr=" + sp.getErrorOutput());
        } catch (IOException e) {
          e.printStackTrace();
        }
      }
      try {
        sleep(5000);
      } catch (InterruptedException e) {
      }
      // Iterate through and make sure all of the SubThreads are accounted for 
      // (else drop it, its line must have been dropped in the file!)
      prta(thrtag + " Shutdown: start force stales");
      RawToMiniSeed.forceStale(-1);     // force out any RTMS that has not been used for 1/2 hour
      IndexBlock.writeStale(-1);        // Update any index blocks that have not been use lately
      IndexFileReplicator.writeStale(); // FOrce out any IndexFileReplications
      prta(thrtag + " Shutdown: terminate all remaining threads");
      for (SubThread st : thr.values()) {
        prta(thrtag + " Shutdown: send terminate to " + st.getTag());
        try {
          st.terminate();
        } catch (IllegalAccessException e) {
          prt(thrtag + " Shutdown: Shutting down " + thrtag + " on " + st.getTag() + " IllegalAccessException");
          e.printStackTrace();
        } catch (InvocationTargetException e) {
          prt("InvocationTargetException shutting down " + st.getTag());
          e.printStackTrace();
        }
      }
      try {
        sleep(5000L);
      } catch (InterruptedException e) {
      }
      IndexFile.closeStale(-1);         // close any file we have not used lately
      // wait for the threads to all exit
      int nrunning = 1;
      int loops = 0;
      prta(thrtag + " Shutdown : check that all threads have exited");
      while (nrunning > 0) {
        loops++;
        if (loops > 30) {
          prta(thrtag + " Shutdown: some threads have not exited on timeout.");
          break;
        }
        Iterator itr = thr.values().iterator();
        nrunning = 0;
        String list = thrtag + " Shutdown: thrs=";
        while (itr.hasNext()) {
          SubThread st = (SubThread) itr.next();
          if (st.isRunning() && st.isAlive()) {
            nrunning++;
            list += " " + st.getTag();
            if (nrunning % 5 == 0) {
              list += "\n";
            }
          }
        }
        prta(thrtag + " Shutdown: waiting for " + nrunning + " threads to shutdown");
        if (nrunning == 0) {
          break;        // speed up the exit!
        }
        prta(list);
        try {
          sleep(1000L);
        } catch (InterruptedException e) {
        }
      }
      try {
        sleep(3000L);
      } catch (InterruptedException e) {
      } // allow time for any other shutdowns
      prta(Util.getThreadsString());
      prta(thrtag + " Shutdown: is complete.");
    }
  }

  /*
 * EdgeStatusServer.java - This class is a Server Template which listens on the given port
 * and starts an internal class EdgeStatusHandler to handle each socket connection.
 *
 * This server puts out a string of status information for the Edgemom and all SubThreads
   * when ever any connection is made
   *
   * @author davidketchum
   */
  public final class EdgeStatusServer extends Thread {

    private int port;
    private ServerSocket d;
    private int totmsgs;
    private boolean terminate;
    private final StringBuilder tmpsb = new StringBuilder(100);

    @Override
    public String toString() {
      return "p=" + port + " tot=" + totmsgs;
    }

    public void terminate() {
      terminate = true;
      interrupt();
      try {
        if (d != null && !d.isClosed()) {
          d.close();
        }
      } // cause the termination to begin
      catch (IOException e) {
      }
    }

    public int getNumberOfMessages() {
      return totmsgs;
    }

    /**
     * Creates a new instance of EdgeStatusServers
     *
     * @param porti The port to run the server on
     */
    public EdgeStatusServer(int porti) {
      port = porti;
      terminate = false;
      // Register our shutdown thread with the Runtime system.
      Runtime.getRuntime().addShutdownHook(new ShutdownEdgeStatus(this));
      gov.usgs.anss.util.Util.prta(Util.clear(tmpsb).append("new ThreadStatus ").append(getName()).
              append(" ").append(getClass().getSimpleName()));

      start();
    }

    @Override
    public void run() {
      boolean dbg = false;
      StringBuilder sb = new StringBuilder(10000);

      // OPen up a port to listen for new connections.
      while (true) {
        try {
          //server = s;
          if (terminate) {
            break;
          }
          Util.prt(Util.clear(tmpsb).append(Util.asctime()).append(" EdgeStatusServer: Open Port=").append(port));
          d = new ServerSocket(port);
          break;
        } catch (SocketException e) {
          if (e.getMessage().equals("Address already in use")) {
            try {
              Util.prt(Util.clear(tmpsb).append("EdgeStatusServer: Address in use - try again."));
              Thread.sleep(60000);
              port -= 100;
            } catch (InterruptedException E) {
            }
          } else {
            Util.prt(Util.clear(tmpsb).append("EdgeStatusServer:Error opening TCP listen port =").
                    append(port).append("-").append(e.getMessage()));
            try {
              Thread.sleep(60000);
            } catch (InterruptedException E) {
            }
          }
        } catch (IOException e) {
          Util.prt(Util.clear(tmpsb).append("EdgeStatusServer:ERror opening socket server=").
                  append(e.getMessage()));
          try {
            Thread.sleep(2000);
          } catch (InterruptedException E) {

          }
        }
      }

      while (true) {
        if (terminate) {
          break;
        }
        try {
          Socket s = accept(d);
          Util.prt(Util.clear(tmpsb).append("EdgeStatusServer: from ").append(s).append(EdgeMom.state).append(EdgeMom.getEdgeMomState()));
          EdgeStatusHandler a123 = new EdgeStatusHandler(s, tmpsb);
        } catch (IOException e) {
          Util.prta(Util.clear(tmpsb).append("EdgeStatusServer: accept IO error e=").append(e));
        }
      }       // end of infinite loop (while(true))
      //Util.prt("Exiting EdgeStatusServers run()!! should never happen!****\n");
      try {
        d.close();
      } catch (IOException e) {
      }
      Util.prt(Util.clear(tmpsb).append("EdgeStatusServer:read loop terminated"));
    }

    private Socket accept(ServerSocket din) throws IOException {
      Util.prt("EdgeStatusServer in accept()");
      return din.accept();
    }

    private class ShutdownEdgeStatus extends Thread {

      EdgeStatusServer thr;

      public ShutdownEdgeStatus(EdgeStatusServer t) {
        thr = t;
        gov.usgs.anss.util.Util.prta("new ThreadShutdown " + getName() + " " + getClass().getSimpleName());

      }

      /**
       * this is called by the Runtime.shutdown during the shutdown sequence cause all cleanup
       * actions to occur
       */
      @Override
      public void run() {
        thr.terminate();
        Util.prta("EdgeStatusServer: Shutdown started");
        int nloop = 0;

        Util.prta("EdgeStatusServer:Shutdown Done. CLient c");

      }
    }

    final class EdgeStatusHandler extends Thread {

      Socket s;
      StringBuilder tmpsb;

      public EdgeStatusHandler(Socket ss, StringBuilder tmpsb) {
        s = ss;
        this.tmpsb = tmpsb;
        gov.usgs.anss.util.Util.prta(Util.clear(tmpsb).append("new ThreadStatus ").
                append(getName()).append(" ").append(getClass().getSimpleName()).append(" ss=").append(ss));
        start();
      }

      @Override
      public void run() {
        StringBuilder sb = new StringBuilder(10000);
        if (sb.length() > 0) {
          sb.delete(0, sb.length() - 1);
        }
        sb.append(Util.asctime()).append(" ").append(Util.ascdate()).append(" EdgeMom: process file=").
                append(filename).append(" #Threads=").append(thr.values().size()).
                append(" #thr=").append(Thread.activeCount()).append("\n");
        Iterator itr = thr.values().iterator();
        int nthr = 0;
        int nlong = 0;
        while (itr.hasNext()) {
          nthr++;
          SubThread st = (SubThread) itr.next();
          //prt(st.getTag()+"|"+st.getStatusString());
          sb.append(st.getTag()).append(":").append(st.getType()).append(":").
                  append(st.getLine()).append("\n   ").append(st.getStatusString()).append("\n\n");
        }
        try {
          OutputStream out = s.getOutputStream();
          BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream()));
          while (!terminate) {
            // body of Handler goes here
            Util.prta(Util.clear(tmpsb).append("EdgeStatusServer out:"));
            Util.prt(sb);
            for (int i = 0; i < sb.length(); i++) {
              out.write((byte) sb.charAt(i));
            }
            //out.write(sb.toString().getBytes());
            break;
          }
        } catch (IOException e) {
          Util.SocketIOErrorPrint(e, "EdgeStatusServer: IOError on socket");
        }
        Util.prta(Util.clear(tmpsb).append("EdgeStatusServer: EdgeStatusHandler has exit on s=").append(s));
        if (s != null) {
          if (!s.isClosed()) {
            try {
              s.close();
            } catch (IOException e) {
              Util.prta("EdgeStatusServer: IOError closing socket");
            }
          }
        }
      }
    }
  }

  /**
   * This server sends a string of Key-Value pairs with monitoring information on the process. These
   * are generally used by Nagios or other monitoring software to track the state of this EdgeMom
   * instance.
   *
   * @author davidketchum
   */
  public final class EdgeMomMonitorServer extends Thread {

    private final int port;
    private ServerSocket d;
    private int totmsgs;
    private boolean terminate;
    private int state;
    private int pokeState;
    private int npokeSame;
    private SubThread st;
    private byte[] buf = new byte[1024];
    private final StringBuilder sb = new StringBuilder(10000);

    @Override
    public String toString() {
      return "EMMS("+getId()+") p=" + port + " tot=" + totmsgs + " state=" + state + " " + (state == 8 ? st : "");
    }

    public int getEMMState() {
      return state;
    }

    public void terminate() {
      terminate = true;
      interrupt();
      try {
        if (d != null && !d.isClosed()) {
          d.close();
        }
      } // cause the termination to begin
      catch (IOException expected) {
      }
    }

    public int getNumberOfMessages() {
      return totmsgs;
    }

    /**
     * if the state is stuck, terminate the process
     *
     * @return true if thread has been terminated.
     */
    public boolean poke() {
      if (state == 2) {
        npokeSame = 0;
        pokeState = state;
        return false;
      }
      if (pokeState == state) {
        npokeSame++;
        if (npokeSame > 3) {
          prta("EdgeMomMonitorServer: **** is stuck in state=" + state 
                  + " cnt=" + npokeSame + "st=" + st + " emms=" + toString() + "\nsb=" + sb);
          SendEvent.edgeSMEEvent("EMMSStuck", instance + " " + toString(), this);
          terminate();
          return true;
        }
      } else {
        npokeSame = 0;
      }
      pokeState = state;
      return false;
    }

    /**
     * Creates a new instance of EdgeStatusServers
     *
     * @param porti The port to listen to
     */
    public EdgeMomMonitorServer(int porti) {
      port = porti;
      terminate = false;
      // Register our shutdown thread with the Runtime system.
      Runtime.getRuntime().addShutdownHook(new ShutdownEdgeMomMonitor(this));
      gov.usgs.anss.util.Util.prta("new ThreadMonitor " + getName() + " " + getClass().getSimpleName() + " on port " + port);

      start();
    }

    @Override
    public void run() {
      boolean dbg = false;
      long lastLog = 0;
      // OPen up a port to listen for new connections.
      while (true) {
        state = 0;
        try {
          //server = s;
          if (terminate) {
            break;
          }
          Util.prta(" EdgeMomMonitorServer: Open Port=" + port);
          d = new ServerSocket(port);
          break;
        } catch (SocketException e) {
          if (e.getMessage().equals("Address already in use")) {
            try {
              Util.prt("EdgeMomMonitorServer: Address in use - try again. port=" + port);
              SendEvent.edgeSMEEvent("EdgeMomDup", "Duplicate " + filename + " " + Util.getSystemName() + "/" + Util.getNode() + " " + port, this);
              Thread.sleep(60000);
              //port -=100;
            } catch (InterruptedException expected) {
            }
          } else {
            Util.prt("EdgeMomMonitorServer:Error opening TCP listen port =" + port + "-" + e.getMessage());
            try {
              Thread.sleep(60000);
            } catch (InterruptedException expected) {

            }
          }
        } catch (IOException e) {
          Util.prt("EdgeMomMonitorServer:Error opening socket server=" + e.getMessage());
          try {
            Thread.sleep(2000);
          } catch (InterruptedException expected) {

          }
        }
      }
      int loop = 0;
      state = 1;
      while (!terminate) {
        state = 2;
        try {
          try ( // Each connection (accept) format up the key-value pairs and then close the socket
                  Socket s = accept()) {
            state = 3;
            if (terminate) {
              if (!s.isClosed()) {
                s.close();
              }
              break;
            }
            //prta("EdgeMomMonitorServer: from "+s);
            Util.clear(sb);
            state = 4;
            sb.append("MemoryFreePct=").append((Runtime.getRuntime().maxMemory() - Runtime.getRuntime().totalMemory()
                    + Runtime.getRuntime().freeMemory()) * 100 / Runtime.getRuntime().maxMemory()).append("\n");
            sb.append("ActiveThreads=").append(Thread.activeCount()).append("\n");
            sb.append("Account/Node/Roles=").append(Util.getAccount()).append("/").append(Util.getNode()).append("/");
            String[] roles = Util.getRoles(Util.getSystemName());
            for (String role : roles) {
              sb.append(role).append(" ");
            }
            state = 5;
            sb.append("\n");
            sb.append("NSubThreads=").append(thr.values().size()).append("\n");
            sb.append(IndexFile.getMonitorString());
            sb.append(IndexBlock.getMonitorString());
            sb.append(IndexFileReplicator.getMonitorString());
            sb.append(ChannelSender.getMonitorString());
            state = 6;
            //prta("EdgeMomMonitorServer: from "+s);
            Iterator itr = thr.values().iterator();
            totmsgs++;
            int nthr = 0;
            try {
              while (itr.hasNext()) {
                state = 7;
                nthr++;
                st = (SubThread) itr.next();
                state = 8;
                //Util.prta("EdgeMomMonitorServer: "+nthr+" "+ st.getTag()+" sb.len="+sb.length());
                try {
                  sb.append(st.getTag()).append("-").append(st.getMonitorString());
                } catch (IllegalAccessException e) {
                  prt("EdgeMomMonitorServer:IllegalAccessException getting monitor string for " + nthr + " st=" + st);
                } catch (InvocationTargetException e) {
                  prt("EdgeMomMonitorServer:InvocationTargetException getting monitor string for " + nthr + " st=" + st);
                  e.printStackTrace();
                }

              }
              state = 9;
            } catch (ConcurrentModificationException e) {
              prta("EdgeMomMonitorServer: concurrent modification skip");
            }
            state = 10;
            if (sb.length() > buf.length) {    // if the buffer is too small, make it bigger
              buf = new byte[sb.length() * 2];
            }
            for (int i = 0; i < sb.length(); i++) {
              buf[i] = (byte) sb.charAt(i);  // contents to byte array
            }
            state = 11;
            s.getOutputStream().write(buf, 0, sb.length());         // Write it out
            state = 12;
            loop++;
            if (loop % 50 == 1 || System.currentTimeMillis() - lastLog > 300000) {
              prta("EdgeMomMonitorServer: wrote len=" + sb.length() + " #msg=" + totmsgs + " to " + s + " " + loop);
              lastLog = System.currentTimeMillis();
            }
          }
        } catch (IOException e) {
          if (!EdgeMom.isShuttingDown() && !terminate) {
            Util.prta("EdgeMomMonitorServer: got IOError during acception or dump e=" + e);
            e.printStackTrace();
            Util.SocketIOErrorPrint(e, "EdgeMomMonitorServer: during accept or dump back. continue.");
          } else {
            prta("EdgeMomMonitorServer: exit on IOException while shutting down e=" + e);
            break;
          }     // terminating -leave
        } catch (RuntimeException e) {
          if (!EdgeMom.isShuttingDown() && !terminate) {
            prta("EdgeMomMonitorServer: continues after getting a e=" + e);
            e.printStackTrace();
          } else {
            prta("EdgeMomMonitorServer: exit on Runtime while shutting down e=" + e);
            break;
          }     // terminating - leave
        }
        state = 22;
      }       // end of infinite loop (while(true))
      //Util.prt("Exiting EdgeStatusServers run()!! should never happen!****\n");
      state = 13;
      try {
        if (!d.isClosed()) {
          d.close();
        }
      } catch (IOException expected) {
      }
      state = 14;
      Util.prt("EdgeMomMonitorServer:read loop terminated terminate=" + terminate);
    }

    public Socket accept() throws IOException {
      return d.accept();
    }

    private class ShutdownEdgeMomMonitor extends Thread {

      EdgeMomMonitorServer thr;

      public ShutdownEdgeMomMonitor(EdgeMomMonitorServer t) {
        thr = t;
        gov.usgs.anss.util.Util.prta("new ThreadShutdown " + getName() + " " + getClass().getSimpleName());

      }

      /**
       * this is called by the Runtime.shutdown during the shutdown sequence cause all cleanup
       * actions to occur
       */
      @Override
      public void run() {
        thr.terminate();
        Util.prta("EdgeMomMonitorServer: Shutdown started");
      }
    }
  }

  /**
   * The main program for an EdgeMom. This parses many parameters and sets thing up before starting
   * EdgeMom thread.
   *
   * @param args the command line arguments
   */
  public static void main(String[] args) {
    Util.setProcess("EdgeMom");
    Util.setModeGMT();
    Util.setNoInteractive(true);
    EdgeThread.setMainLogname("edgemom");

    EdgeProperties.init();
    int dummy = 0;
    boolean dbg = false;
    boolean dbgblk = false;
    boolean dbgebq = false;
    boolean ebqsave = false;
    boolean noSNW = false;
    String ebqfilename = "";
    String filename = "edgemom.setup";
    boolean useConsole = false;
    //int holdPort = 7997;
    //String holdIP="";
    //String holdType="UN";
    int ebqsize = 0;
    stalems = 43200000;

    if (args.length == 0) {
      try {
        try (BufferedReader in = new BufferedReader(new FileReader(filename))) {
          String s;
          while ((s = in.readLine()) != null) {
            if (s.length() < 1) {
              continue;
            }
            if (s.substring(0, 1).equals("%")) {
              break;       // debug: done reading flag
            }
            if (!s.substring(0, 1).equals("#")) {
              int comment = s.indexOf("#");
              if (comment > 0) {
                s = s.substring(0, comment).trim();  // strip off the comment
              }
              StringTokenizer tk = new StringTokenizer(s, ":");
              if (tk.countTokens() == 3) {
                String tag = tk.nextToken().trim();
                if (tag.equalsIgnoreCase("mom")) {
                  String name = tk.nextToken().trim();
                  args = tk.nextToken().trim().split(" ");
                  in.close();
                  break;
                }
              }
            }
          }
        }
      } catch (FileNotFoundException e) {
        e.printStackTrace();
      } catch (IOException e) {
        e.printStackTrace();
      }
    }

    for (int i = 0; i < args.length; i++) {
      switch (args[i]) {
        case "-f":
          filename = args[i + 1];
          i++;
          break;
        case "-prop":
          Util.loadProperties(args[i + 1]);
          extraProperties = args[i + 1];
          i++;
          break;
        case "-maxpurge":
          IndexFile.setMaxFilesBeforePurge(Integer.parseInt(args[i + 1]));
          break;
        case "-i":
        case "-instance":
          instance = args[i + 1].trim();
          i++;
          if (instance.length() > 4) {
            Util.prt("Instance to EdgeMom is invalid - must be NODE#ACCT and less than 4 characters");
          }
          Util.loadProperties("EDGEMOM/edge_" + instance + ".prop");
          break;
        case "-dbg":
          dbg = true;
          break;
        case "-dbgblk":
          dbgblk = true;
          break;
        case "-dbgebq":
          dbgebq = true;
          break;
        case "-dbgifr":
          IndexFileReplicator.setDebug(true);
          break;
        case "-dbgrtms":
          RawToMiniSeed.setDebug(true);
          break;
        case "-rtmstps":
          RawToMiniSeed.setTolerancePerSample(Integer.parseInt(args[i + 1]));
          i++;
          break;
        case "-ebqsave":
          ebqsave = true;
          ebqfilename = args[i + 1];
          i++;
          break;
        case "-ebq":
          ebqsize = Integer.parseInt(args[i + 1]);
          i++;
          break;
        case "-stalesec":
          stalems = Integer.parseInt(args[i + 1]) * 1000;
          i++;
          break;
        case "-stalertms":
          staleRTMS = Integer.parseInt(args[i + 1]) * 1000;
          break;
        case "-nochkprop":
          noCheckProperties = true;
          break;
        case "-console":
          useConsole = true;
          break;
        case "-empty":
          dummy = i;     // eliminate warning
          break;
        case "-traceSteim":
          Steim2.setTracebackErrors(true);
          break;
        case "-nosnw":
          noSNW = true;
          break;
        case "-nozeroer":
          IndexFile.setNoZeroer(true);
          break;
        /*case "-zeroahead":
          Util.prt("Zeroahead="+args[i+1]);
          zeroAhead=Integer.parseInt(args[i+1]);
          IndexFile.setZeroerBlocksAhead(zeroAhead*2000);
          i++;
          break;*/
        case "-logname":
          EdgeThread.setMainLogname(args[i + 1]);
          i++;
          break;
        case "-notimeout":
          EdgeThreadTimeout.setNoTimeout();
          TimeoutThread.setNoTimeout();
          break;
        case "-version":
          Util.prt(Version.version);
          System.exit(0);
        case "-?":
          Util.prt("java -jar EdgeMom.jar [-f filename][-dbg][-dbgblk][.....]");
          Util.prt("   -f filename file name of setup file def=edgemom.setup");
          Util.prt("   -i nn#i  Set the instance to this value");
          Util.prt("   -nosnw Turnoff any output to the SNW server via SNWSender");
          Util.prt("   -stalesec nnn  Set the number of seconds of no use for a file to be closed as stale");
          Util.prt("   -logname  file Set the log file name to something other than 'edgemom'");
          Util.prt("Debugging related switches:");
          Util.prt("   -dbg  Turn on debugging");
          Util.prt("   -dbgblk Turn on log file for each data block written");
          Util.prt("   -dbgebq Turn on log file for each data block to replication");
          Util.prt("   -dbgrtsm Turn on debugging in RawToMiniSeed");
          Util.prt("   -dbgifr Turn on debugging in IndexFileReplicator");
          Util.prt("   -eqbsave Turn on saving of EdgeBlockQueue to journal file");
          Util.prt("   -ebq nnnn Set size of the EdgeBLock Queue default=" + ebqsize);
          Util.prt("   -console Set use console flag on - cause console output");
          Util.prt("   -notimeout Turn off thread time outs - good for useing debugger");
          Util.prt("   -traceSteim Turn on tracebacks from Steim2 errors");
          Util.prt("   -nozeroer Turn off zeroer, only do on file systems the return zeros on unwritten blocks in a file");
          Util.prt("   -zeroahead n The zeroer needs to keep this many megabytes ahead of the writer (def=1)");
          break;
        default:
          Util.prt("Unknown switch is " + args[i]);
          break;
      }
    }
    chkProperties();
    if (!instance.equals("^")) {
      Util.loadProperties("EDGEMOM/edge_" + instance.replaceAll("Q", "#") + ".prop");
    }
    System.out.println("Edgemom " + Version.version + " start up at " + Util.ascdatetime(System.currentTimeMillis(), null) + " setup=" + filename);
    System.out.println(" prop.Node=" + Util.getProperty("Node") + " instance=" + instance
            + " IndexFile.Node=" + IndexFile.getNode() + " mysqlserver=" + Util.getProperty("DBServer"));
    //IndexFileReplicator.setDebug(true);
    try {
      Util.prt("dbgblk=" + dbgblk + " dbgebq=" + dbgebq + " dbg=" + dbg);
      IndexBlock.setDebugBlocks(dbgblk);
      Util.setNoInteractive(true);
      // If ebqsize was not set on command line, use the one from the property.
      if (ebqsize == 0) {
        ebqsize = Integer.parseInt(Util.getProperty("ebqsize"));
      }
      EdgeMom main = new EdgeMom(filename, ebqsize, useConsole, "EdgeMom");
      if (noSNW) {
        SNWSender.setDisabled(true);
      }
      EdgeBlockQueue.setDebug(dbgebq);
      if (ebqsave) {
        main.setSaveEdgeBlockQueue(ebqfilename);
      }
      main.setDebug(dbg);
      IndexFile.init();
      //if(dbg) main.setNoConsole(!dbg);
      int badState = 0;
      int nbad = 0;
      int ngood = 0;
      int currstate;
      StringBuilder sbstate = new StringBuilder(100);
      PrintStream stateStream = new PrintStream(new FileOutputStream("LOG/state_" + instance, true));
      stateStream.println(Util.ascdatetime2(Util.clear(sbstate)).append(" EdgeMom startup state=").
              append(EdgeMom.getEdgeMomState()).append(" ").append(instance));
      for (;;) {
        try {
          Thread.sleep(30000);
        } catch (InterruptedException e) {
        }
        currstate = EdgeMom.getEdgeMomState();
        if (currstate != 20) {
          stateStream.println(Util.ascdatetime2(Util.clear(sbstate)).append(" EdgeMom.state=").
                  append(EdgeMom.getEdgeMomState()).append(" nbad=").append(nbad));
          //Util.prta("EdgeMom.state="+currstate);
          if (currstate == badState) {
            nbad++;
            if (nbad % 5 == 4) {
              stateStream.println(Util.ascdatetime2(Util.clear(sbstate)).append(" EdgeMom.state=").
                      append(currstate).append(" nbad=").append(nbad).append(" we should abort!"));
              SendEvent.edgeEvent("EdgeMomStuck", "Instance " + instance + " EdgeMom thread stuck in state=" + currstate, "EdgeMom");
            }
          } else {
            nbad = 0;
            badState = currstate;
          }
        } else {
          nbad = 0;
          badState = currstate;
          ngood++;
          if (ngood % 120 == 1) {
            stateStream.println(Util.ascdatetime2(Util.clear(sbstate)).append(" ").
                    append(EdgeMom.getInstance()).append(" is o.k."));
          }
        }
        // DEBUG: this 
        if (!main.isAlive()) {
          Util.prta("EdgeMom Thread not alive! call system.exit()\n" + Util.getThreadsString());
          Util.exit(1);
        }
        //else Util.prta("EdgeMom alive.");
      }
    } catch (FileNotFoundException e) {
      System.err.println("Could not open state file");
    } catch (RuntimeException e) {
      Util.prta("EdgeMom: RuntimeException in EdgeMom.main()  call system.exit e=" + e.getMessage());
      e.printStackTrace();
      if (e.getMessage().contains("OutOfMemory")) {
        SendEvent.sendEvent("Edge", "OutOfMemory", filename + " Out of Memory on " + Util.getNode() + "/" + System.getProperty("user.name"),
                Util.getNode(), "EdgeMom");
      } else {
        SendEvent.edgeEvent("RuntimeExcp",
                filename + " RuntimeXception - exit() in EdgeMom on " + Util.getNode() + "/" + System.getProperty("user.name"), "EdgeMom");

      }
      System.exit(2);
    }
    Util.exit("EdgeMom - final");
  }

}
