/*
 *  This software is in the public domain because it contains materials 
 *  that originally came from the United States Geological Survey, 
 *  an agency of the United States Department of Interior. For more 
 *  information, see the official USGS copyright policy at 
 *  http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.alarm;

import gov.usgs.anss.db.DBAccess;
import gov.usgs.anss.db.DBConnectionThread;
import gov.usgs.anss.edge.EdgeProperties;
import gov.usgs.anss.edgethread.EdgeThread;
import gov.usgs.anss.util.SimpleSMTPThread;
import gov.usgs.anss.util.Util;
import gov.usgs.anss.util.AnssPorts;
import gov.usgs.anss.edgemom.EdgeChannelServer;
import gov.usgs.anss.edgemom.Version;
import gov.usgs.anss.edgethread.MemoryChecker;
import gov.usgs.anss.edgemom.IndexFileServer;
import java.sql.Timestamp;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.FileNotFoundException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;

/**
 * This class is the main class for the alarm process. It create the various
 * listeners and the SNWGroupEventProcess to generate pages based on networks
 * and SNW groups. These threads then process incoming events and process them
 * according to the damping and subscriptions to send pages and e-mail based on
 * the events. This main() builds up an argument string based on the input and
 * submits it to ALL of the threads created so this string has switches intended
 * for many independent threads. As such, all of these threads have to ignore
 * all switches that are not theirs and coordinate the argument passing so that
 * no two threads use the same switches.
 * <p>
 * In 2015 the alarm process was augmented to run the UdpChannel server and
 * DBMessageServer. These two use to be in separate process/project (UdpChannel
 * and POCMySQLServer projects). All source from these projects was merged to
 * cwblib.
 * <p>
 * The main process here manages and monitors several EdgeThreads:
 * <p>
 * Alarm The main process for monitoring UDP alarm messages and sending pages
 * and emails
 * <p>
 * UdpChannel The thread that listens for ChannelStatus packets from EdgeMoms.
 * This server can then send this information out to TCP/IP clients like
 * ChannelDisplay. This process also optionally can collect information on SNW
 * groups for sending Alarm messages when they are in a warning or warning
 * state. It also optionally can send information to a real SNW server. Started
 * by default (see -noudpchan). Object kept in static attribute of UdpChannel
 * for historical reasons, that is, when UdpChannel was its own project it
 * started a lot of threads within itself that Alarm is now starting via
 * CWB.java.
 *
 * <p>
 * DBMessageServer - This server takes messages via TCP/IP that cause inserts to
 * be done in the appropriate database. This is used to create new channels,
 * etc. when discovered by Edge related processes.
 * <p>
 * SNWChannelClient - this gathers information about channels and analyzes SNW
 * groups. This updates the snwstation table with information about new
 * stations, latest data, etc. Object kept in static attribute of UdpChannel for
 * historical reasons.
 * <p>
 * SNWGroupEventProcess - This sends out events to alarm when SNW groups get bad
 * <p>
 * WebStationClient This thread is used only at the NEIC for updating one of the
 * MySQL database used by the Web group to show status of ANSS and GSN stations.
 * Object kept in static attribute of UdpChannel for historical reasons.
 * <p>
 * LatencyServer - This server can be inquired about the current latency for any
 * channel. It is used mostly by the Fetcher classes to throttle back fills when
 * real time data is behind. Object kept in static attribute of UdpChannel for
 * historical reasons.
 * <p>
 * CommandStatusServer - This process listens for connections from the
 * EdgeConfig GUI and executes commands through the command maintenance
 * interface. (-nocmd)
 * <p>
 * NEIC only threads - Two threads which should only be useful at the NEIC are
 * started automatically if the server name matches one at the NEIC.
 * <p>
 * NOChecker - THis thread checks on the VPN and VSAT by passes on the Hughes
 * network to the two Hughes NOCs.
 * <p>
 * QDPingManager - This thread sends ping and QDPings to all Q330 stations and
 * tracks the responses.
 * <p>
 * MakeVsatRoutes - This thread is used at the NEIC to modify the routing table
 * if a system is set to "bypass". THis mode shifts all VSAT based traffic off
 * of the VPNs to the NOC and onto the local VSAT back hauls.
 * <p>
 * EdgemomConfigThread - This thread does "non-Instanced" based configuration.
 * It uses the edgefile table to control crontab, and configuration files to
 * where they are needed, builds all of the files into the EDGEMOM directory.
 * <p>
 * MakeEtcHosts - This thread is used at the NEIC to maintain the /etc/hosts
 * file to include the names and paths to all of the Q330s, RTS/Slate/station
 * process boxes, VSAT gateways at the site so that users can just "ping
 * station-gw" or "ping station".
 * <p>
 * The UdpEventProcess class is the listener for UDP packets to alarm from EdgeMoms etc.  Its port
 * is normally 7964, but an entire system can use the AlarmPort property to change everything on that
 * system to use some other port (This include SendEvent).  There are cases where the Alarm process
 * might want to listen on another port for UDP packets and not interrupt the configuration of which port
 * is used for SendEvent.  An example is a special alarm is being setup to listen for events from field
 * stations.  In this case the -udpport option allows such an alarm to be setup to listen on another port
 * while any SendEvent uses will use the normal port.
 * <PRE>
 * Arg    Description
 * Switches to the main() :
 * -noalarm    Disable processing of UDPEvent packets and alarming by this node (no SNW work is possible)
 * -web        Start a WebStationCLient to keep USGS operational web page status up-to-date (NEIC only)
 * -snw        Start a SNWChannelClient and make it available to the UdpChannel thread so SNW processing can be done
 * -snwsender  When a SNWChannelClient is started, it is marked to send output to a SeisNetWatch server
 * -web        Start a WebStationCLient to keep USGS operational web page status up-to-date (NEIC only)
 * -udpport nn Start the UdpEventProcess on this port instead of the property AlarmPort or def 7964.
 * Alarm thread switches :
 * -notrap     Do not set up a SNMP trap handler (this is now the default)
 * -trap       Set up a SNMP trap handler
 * -nocfg      Do not run a configuration thread
 * -replicate  Forward all UDP events to the given IP and port
 * -repIP      The replication target IP address (NAGIOS)
 * -repPort    The port n the repIP to send the replication packets
 * -snw        SeisNetWatch is allowed.  This will start a SNWGroupEvent thread to cause SNW events as well (def=false)
 * -nosnwevent If present, suppress UDP alarm packets about the SNW groups
 * -dbgmail    If present turn on debug output when s SimpleSMTPTHread tries to send mail
 * -bind IP1:IP2:port1:port2 Use these two ips are ports to bind the local end of the connection, emailAlt will use 2nd binding
 * -dbg        Turn on more debug output
 * -qmom file  File with lines like [!]ip:cwbport:qport:CH1:CH2... Set up a test monitor of a QueryMom on the given ip and ports and chans
 *                (channels have - for space).  ! to turn on debugging of this connection
 * -qport file Read in the config file and setup MonitorPorts for each.  Form of file is "ipadr:port:[process[:account[:action]]]
 * -noDB       This Alarm is only for relaying or logging messages, it has no DB so it cannot take any action
 * -udphb      Send heartbeat UDP packets to configured edge based field stations (no normally used outside NEIC)
 * -nodbmsg    Do not start a DBMessageServer on port 7985 - this would be very unusual as it does not harm
 * -noudpchan  Do not start a UdpChannel server - this would be very unusual as it does no harm
 * -noaction   Alarm will not allow any emails or pages to be sent, but just logged
 *
 * Switches for the UdpChannel thread which feeds channel data to ChannelDisplay and other clients
 * -mod50     Set the modulus for lastdata updates to 50 (def=20) - the lastdata will be updated about every modulus*3 minutes
 *
 * Switches for DBMessageServer :
 * -p    port   Set the port number (default=7985)
 *
 * </PRE>
 *
 * @author davidketchum
 */
public final class Alarm extends EdgeThread {

  private boolean dbg;
  private boolean replicate;
  private String replicateIP;
  private int replicatePort;
  private int replicateBindPort;
  private int overrideUdpPort = 0;
  private static boolean noDB;
  private final ShutdownAlarm shutdown;
  private final AlarmMonitorServer alarmMonitorServer;
  private final UdpEventProcess udp;
  private UDPHeartBeat udphb;
  private SNWGroupEventProcess snwEvent;
  private MultiThreadedTrapReceiver snmpTrapHandler;
  //private static DBConnectionThread dbconnalarm, dbconnedge;
  private static DBAccess dbaccess;
  private final EventMonitorServer evtServer;
  private ArrayList<MonitorPort> monitorPorts = new ArrayList<>(10);
  private ArrayList<QueryMomMonitor> monitorQueryMoms = new ArrayList<>(10);
  private String qportConfig;
  private String querymomConfig;
  private boolean heartBeats;
  private String statusIP = null;
  private boolean snmpTrap = false;
  private boolean noSNW = true;
  private boolean noSNWEvent = false;
  private boolean noalarm;
  public static boolean isShuttingDown;
  private static Alarm theAlarm;

  public static Alarm getTheAlarm() {
    return theAlarm;
  }

  public static boolean isNoDB() {
    return noDB;
  }

  @Override
  public StringBuilder getStatusString() {
    Util.clear(statussb).append("#monitorQueryMoms=").append(monitorQueryMoms.size());
    return statussb;
  }

  @Override
  public StringBuilder getConsoleOutput() {
    return consolesb;
  }

  @Override
  public void terminate() {
    terminate = true;
    interrupt();
  }

  @Override
  public StringBuilder getMonitorString() {
    Util.clear(monitorsb).append("#monitorQueryMoms=").append(monitorQueryMoms.size());
    return monitorsb;
  }

  /**
   * Creates a new instance of Alarm
   *
   * @param argline The command line to use to build this Thread
   * @param tg The logging tag
   */
  public Alarm(String argline, String tg) {
    super(argline, tg);
    prta(Util.ascdate() + " start up " + Version.version);
    if (Util.getNode().indexOf("edge") == 0) {
      replicate = true;
    }
    String[] args = argline.split("\\s");
    dbg = false;
    replicateIP = null;
    replicatePort = 7207;
    replicateBindPort = 6330;
    if (theAlarm != null) {
      prta("**** apparent duplicate creation of Alarm thread !");
    }
    theAlarm = (Alarm) this;
    EventHandler.setAlarm((Alarm) this);
    //new RuntimeException("who created alarm "+args.length).printStackTrace(getPrintStream());

    int dummy = 0;
    for (int i = 0; i < args.length; i++) {
      prt(i + "Alarm : arg=" + args[i]);
      if (args[i].equals("-dbg")) {
        dbg = true;
      } else if (args[i].equalsIgnoreCase("-dbgmail")) {
        SimpleSMTPThread.setDebug(true);
      } else if (args[i].equalsIgnoreCase("-empty")) {
      }
      if (args[i].equals("-instance") || args[i].equals("-i")) {
        i++;
      } else if (args[i].equalsIgnoreCase("-replicate")) {
        replicate = true;
      } else if (args[i].equalsIgnoreCase("-noaction")) {
        EventHandler.setNoAction(true);
      } else if (args[i].equalsIgnoreCase("-udpport")) {
        overrideUdpPort = Integer.parseInt(args[i + 1]);
        i++;
      } else if (args[i].equalsIgnoreCase("-repIP")) {
        replicateIP = args[i + 1];
        i++;
      } else if (args[i].equalsIgnoreCase("-repPort")) {
        replicatePort = Integer.parseInt(args[i + 1]);
        i++;
      } else if (args[i].equalsIgnoreCase("-repBindPort")) {
        replicateBindPort = Integer.parseInt(args[i + 1]);
        i++;
      } else if (args[i].substring(0, 1).equals(">")) {
        break;
      } else if (args[i].equalsIgnoreCase("-trap")) {
        snmpTrap = true;
      } else if (args[i].equalsIgnoreCase("-notrap")) {
        snmpTrap = false;
      } else if (args[i].equalsIgnoreCase("-snw")) {
        noSNW = false;
      } else if (args[i].equalsIgnoreCase("-nosnwevent")) {
        noSNWEvent = true;
      } else if (args[i].equalsIgnoreCase("-nodb")) {
        noDB = true;
      } else if (args[i].equalsIgnoreCase("-statusip")) {
        statusIP = args[i + 1];
        i++;
      } else if (args[i].equalsIgnoreCase("-udphb")) {
        heartBeats = true;
      } else if (args[i].equalsIgnoreCase("-qport")) {
        qportConfig = args[i + 1];
        i++;
      } else if (args[i].equalsIgnoreCase("-qmom")) {
        querymomConfig = args[i + 1];
        i++;
      } else if (args[i].equalsIgnoreCase("-noalarm")) {
        noalarm = true;
      } else if (args[i].equalsIgnoreCase("-bind")) {
        String[] parts = args[i + 1].split(":");
        if (parts.length != 4) {
          prta("-bind does not have 4 arguments must be IP:IPalt:port:portAlt");
        } else {
          SimpleSMTPThread.setBinding(parts[0], parts[1], Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
          prta("Set Binding to " + parts[0] + " " + parts[1] + " " + parts[2] + " " + parts[3]);
        }
        i++;
      }
    }
    prta("Alarm: ECS: created args=" + argline + " tag=" + tag);
    prta("Alarm: start noSNW=" + noSNW + " nodB=" + noDB + " qport=" + qportConfig + " qmom=" + querymomConfig + " noalarm=" + noalarm + " repl=" + replicate);
    if (!noDB) {
      if (Util.getProperty("DBServer") == null) {
        noDB = true;
      } else if (Util.getProperty("DBServer").equalsIgnoreCase("NoDB")) {
        noDB = true;
      } else if (Util.getProperty("DBServer").equals("")) {
        noDB = true;
      }
    }
    // If alarm is running it is the master for writing the files if the DB is up, else it should not write the files
    if (dbaccess == null) {
      if (noDB) {
        dbaccess = new DBAccess("-empty >>alaccess", "DBAC");      // If there is noDB, alarm cannot be the master some EdgeMom must be
      } else {
        dbaccess = new DBAccess("-master >>alaccess", "DBAC");
      }
    }
    shutdown = new ShutdownAlarm();
    Runtime.getRuntime().addShutdownHook(shutdown);
    if (!noalarm) {
      udp = new UdpEventProcess(overrideUdpPort, replicate, replicateIP, replicatePort, replicateBindPort, noDB);
    } else {
      udp = null;
    }
    if (heartBeats) {
      udphb = new UDPHeartBeat(udp, this);
    }
    evtServer = new EventMonitorServer(7206);
    alarmMonitorServer = new AlarmMonitorServer(AnssPorts.MONITOR_ALARM_PORT);
    start();
  }

  @Override
  public void run() {
    //dbthread = new AlarmDBThread();
    if (!noSNW) {
      prta("noSWN=" + noSNW + " starting SNWGroupEventProcess");
      String tmp = "";
      if (statusIP != null) {
        tmp = Util.getProperty("StatusServer");
        Util.setProperty("StatusServer", statusIP);
      }
      try {
        sleep(5000);
      } catch (InterruptedException expected) {
      }
      if (!noDB) {
        snwEvent = new SNWGroupEventProcess(!noSNWEvent, this);
      }
      if (statusIP == null) {
        Util.setProperty("StatusServer", tmp);
      }
    }
    if (snmpTrap) {
      snmpTrapHandler = new MultiThreadedTrapReceiver();
      snmpTrapHandler.run();
    }
    // configure the MonitorPorts if any
    if (qportConfig != null) {
      try {
        try (BufferedReader in = new BufferedReader(new FileReader(qportConfig))) {
          String line;
          while ((line = in.readLine()) != null) {
            line = line.trim();
            if (line.length() < 10) {
              continue;
            }
            if (line.charAt(0) == '#') {
              continue;
            }
            monitorPorts.add(new MonitorPort(line, this));
          }
        }
      } catch (FileNotFoundException e) {
        prta("MonitorPort configuration file not found=" + qportConfig);
      } catch (IOException e) {
        prta("Got a IOError reading " + qportConfig + " e=" + e);
      }
    }
    // Configure any QueryMomMonitors
    if (querymomConfig != null) {
      try {
        try (BufferedReader in = new BufferedReader(new FileReader(querymomConfig))) {
          String line;
          while ((line = in.readLine()) != null) {
            if (line.length() < 10) {
              continue;
            }
            if (line.charAt(0) == '#') {
              continue;
            }
            String[] parms = line.split(";");
            for (String parm : parms) {
              monitorQueryMoms.add(new QueryMomMonitor(parm, this));
            }
          }
        }
      } catch (FileNotFoundException e) {
        prta("MonitorPort configuration file not found=" + qportConfig);
      } catch (IOException e) {
        prta("Got a IOError reading " + qportConfig + " e=" + e);
      }
    }

    int nloop = 0;
    //int nsql=0;
    while (!terminate) {
      try {
        Thread.sleep(10000);
      } catch (InterruptedException expected) {
      }
      nloop++;
      if (nloop % 180 == 0) {
        prta("Alarm loop=" + nloop);
      }
    }
    if (udp != null) {
      udp.terminate();
    }
    for (MonitorPort mp : monitorPorts) {
      mp.terminate();
    }
  }
  Timestamp now = new Timestamp(100000L);

  private class ShutdownAlarm extends Thread {

    public ShutdownAlarm() {

    }

    /**
     * this is called by the Runtime.shutdown during the shutdown sequence cause
     * all cleanup actions to occurs
     */
    @Override
    public void run() {
      isShuttingDown = true;
      if (snwEvent != null) {
        snwEvent.terminate();
      }
      udp.terminate();
      SimpleSMTPThread.email(Util.getProperty("emailTo"), "SHUTDOWN:Alarm on "
              + Util.getNode() + "/" + Util.getSystemName() + "/" + Util.getAccount() + " is Shuttingdown",
              "This is called whenever this Jar ShutsDown()");
      prta("ShutdownUdpChannel started");
      try {
        sleep(4000);
      } catch (InterruptedException expected) {
      }
    }
  }

  /**
   * This server sends a string of Key-Value pairs with monitoring information
   * on the process. These are generally used by Nagios or other monitoring
   * software to track the state of this EdgeMom instance.
   *
   * @author davidketchum
   */
  public final class AlarmMonitorServer extends Thread {

    int port;
    ServerSocket d;
    int totmsgs;
    boolean terminate;
    int state;
    byte[] buf = new byte[1024];

    @Override
    public String toString() {
      return "EMMS p=" + port + " tot=" + totmsgs + " state=" + state;
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
     * Creates a new instance of EdgeStatusServers
     *
     * @param porti The port to listen to
     */
    public AlarmMonitorServer(int porti) {
      port = porti;
      terminate = false;
      // Register our shutdown thread with the Runtime system.
      Runtime.getRuntime().addShutdownHook(new ShutdownAlarmMonitor(this));
      gov.usgs.anss.util.Util.prta("new ThreadMonitor " + getName() + " " + getClass().getSimpleName() + " on port " + port);

      start();
    }

    @Override
    public void run() {
      boolean dbg = false;
      StringBuilder sb = new StringBuilder(10000);
      long lastLog = 0;
      // OPen up a port to listen for new connections.
      while (true) {
        state = 0;
        try {
          //server = s;
          if (terminate) {
            break;
          }
          Util.prta(" AlarmMonitorServer: Open Port=" + port);
          d = new ServerSocket(port);
          break;
        } catch (SocketException e) {
          if (e.getMessage().equals("Address already in use")) {
            try {
              Util.prt("AlarmMonitorServer: Address in use - try again. port=" + port);
              SendEvent.edgeSMEEvent("AlarmDup", "Duplicate " + Util.getSystemName() + "/" + Util.getNode() + " " + port, this);
              Thread.sleep(60000);
              //port -=100;
            } catch (InterruptedException Expected) {
            }
          } else {
            Util.prt("AlarmMonitorServer:Error opening TCP listen port =" + port + "-" + e.getMessage());
            try {
              Thread.sleep(60000);
            } catch (InterruptedException Expected) {

            }
          }
        } catch (IOException e) {
          Util.prt("AlarmMonitorServer:Error opening socket server=" + e.getMessage());
          try {
            Thread.sleep(2000);
          } catch (InterruptedException Expected) {
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
            //prta("AlarmMonitorServer: from "+s);
            Util.clear(sb);
            state = 4;
            sb.append("MemoryFreePct=").append((Runtime.getRuntime().maxMemory() - Runtime.getRuntime().totalMemory()
                    + Runtime.getRuntime().freeMemory()) * 100 / Runtime.getRuntime().maxMemory()).append("\n");
            sb.append("ActiveThreads=").append(Thread.activeCount()).append("\n");
            state = 5;
            if (dbMessageServer != null) {
              sb.append(dbMessageServer.getMonitorString());
            }
            if (edgeConfigFiles != null) {
              sb.append("EdgeConfigFiles=active\n");
            } else {
              sb.append("EdgeConfigFiles=inactive\n");
            }
            if (UdpChannel.d != null) {
              sb.append(UdpChannel.d.getMonitorString());
            }
            state = 6;

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
              prta("AlarmMonitorServer: wrote len=" + sb.length() + " #msg=" + totmsgs + " to " + s + " " + loop);
              lastLog = System.currentTimeMillis();
            }
          }
        } catch (IOException e) {
          Util.prta("AlarmMonitorServer: got IOError during acception or dump e=" + e);
          e.printStackTrace();
          Util.SocketIOErrorPrint(e, "AlarmMonitorServer: during accept or dump back. continue.");
        } catch (RuntimeException e) {
          prta("AlarmMonitorServer: continues after getting a e=" + e);
          e.printStackTrace();
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
      Util.prt("AlarmMonitorServer:read loop terminated terminate=" + terminate);
    }

    public Socket accept() throws IOException {
      return d.accept();
    }

    private class ShutdownAlarmMonitor extends Thread {

      AlarmMonitorServer thr;

      public ShutdownAlarmMonitor(AlarmMonitorServer t) {
        thr = t;
        gov.usgs.anss.util.Util.prta("new ThreadShutdown " + getName() + " " + getClass().getSimpleName());

      }

      /**
       * this is called by the Runtime.shutdown during the shutdown sequence
       * cause all cleanup actions to occur
       */

      @Override
      public void run() {
        thr.terminate();
        Util.prta("AlarmMonitorServer: Shutdown started");
      }
    }
  }

  private static DBMessageServer dbMessageServer;
  public static Alarm alarm;
  public static CommandStatusServer commandStatus;
  public static HughesNOCChecker bhChecker;
  public static QDPingManager qdpManager;
  public static MakeVsatRoutes makeVSATRoutes;
  public static MakeEtcHosts etcHosts;
  public static EdgemomConfigThread edgeConfig;
  private static EdgemomConfigInstanceThread edgeConfigFiles;
  private static IndexFileServer indexFileServer;
  public static int state;
  public static int loop;

  public static void aprt(String s) {
    if (alarm == null) {
      Util.prt(s);
    } else {
      alarm.prt(s);
    }
  }

  public static void aprta(String s) {
    if (alarm == null) {
      Util.prta(s);
    } else {
      alarm.prta(s);
    }
  }

  public static void aprt(StringBuilder s) {
    if (alarm == null) {
      Util.prt(s);
    } else {
      alarm.prt(s);
    }
  }

  public static void aprta(StringBuilder s) {
    if (alarm == null) {
      Util.prta(s);
    } else {
      alarm.prta(s);
    }
  }

  public static void main(String[] args) {
    //Util.init();   
    try {
      Util.setProcess("Alarm");
      Util.addDefaultProperty("StatusDBServer", "localhost/3306:status:mysql:status");
      EdgeProperties.init();

      Util.setModeGMT();
      Util.setNoInteractive(true);
      EdgeThread.setMainLogname("alarm");   // This sets the default log name in edge thread (def=edgemom!)
      //aprta(" ************************ Start logging for UdpChannel "+Util.ascdate());
      boolean noAlarm = true;
      boolean noWeb = true;
      boolean noSNW = true;
      boolean noSNWSender = true;
      boolean noCommandStatus = false;
      boolean noDBMessageServer = false;
      boolean qdping = false;       // These are NEIC only processes
      boolean checkBH = false;      //  "       "
      boolean noUdpChannel = false;
      boolean noCWBConfig = false;
      boolean noRoutes = true;
      boolean makeHosts = false;
      boolean dbg = false;
      int latencyPort = 7956;
      int indexFileServerPort = 7979;
      aprta("Starting up");
      if (MemoryChecker.checkMemory()) {
        aprta("**** Memory checker recommends shutdown on startup!");
      }
      aprta("mon=" + MemoryChecker.getMonitorString());
      int mod = -1;
      String argline = "-empty ";
      for (int i = 0; i < args.length; i++) {
        argline += args[i] + " ";
        if (args[i].equalsIgnoreCase("-noudpchan")) {
          noUdpChannel = true;
        } else if (args[i].equals("-ifs")) {
          indexFileServerPort = Integer.parseInt(args[i + 1]);
          i++;
        } else if (args[i].equals("-latport")) {
          latencyPort = Integer.parseInt(args[i + 1]);
          argline = argline.replaceAll("-latport ", "");
          aprta("Lat port=" + latencyPort);
          i++;
        } else if (args[i].equals("-min")) {
          noAlarm = true;
          noSNW = true;
          noRoutes = true;
          makeHosts = false;
          noUdpChannel = true;
          qdping = false;
          checkBH = false;
          noWeb = true;
        } else if (args[i].equals("-alarm")) {
          noAlarm = false;
        } else if (args[i].equals("-snw")) {
          noSNW = false;
        } else if (args[i].equals("-web")) {
          noWeb = false;
        } else if (args[i].equals("-snwsender")) {
          noSNWSender = false;
        } else if (args[i].startsWith("-mod")) {
          mod = Integer.parseInt(args[i].substring(4));
        } else if (args[i].equals("-nocmd")) {
          noCommandStatus = true;
        } else if (args[i].equals("-nodbmsg")) {
          noDBMessageServer = true;
        } else if (args[i].equalsIgnoreCase("-nodb")) {
          noDB = true;
          noDBMessageServer = true;
        } // they together
        else if (args[i].equals("-nocfg")) {
          noCWBConfig = true;
        } else if (args[i].equals("-routes")) {
          noRoutes = false;
          aprt("Starting MakeVsatRoutes");

          makeVSATRoutes = new MakeVsatRoutes(argline + ">>alvsatroutes", "MVR", false, dbg);
        } else if (args[i].equals("-etchosts")) {
          makeHosts = true;
        } else if (args[i].equals("-qdping")) {
          qdping = true;   // This only makes sense at the NEIC
        } else if (args[i].equals("-checkbh")) {
          checkBH = true; // NEIC only   
        }
      }
      if (noSNW && !noWeb) {
        aprt("You must run SNW to have the WebStation thread");
        noSNW = false;
      }
      aprta("noalarm=" + noAlarm + " noSNW=" + noSNW + " nosnwsend=" + noSNWSender + " noCmd=" + noCommandStatus
              + " noDBMsg=" + noDBMessageServer + " noudpch=" + noUdpChannel + " nocfg=" + noCWBConfig);
      aprta("NEIC: qdping=" + qdping + " chkBH=" + checkBH + " noroutes=" + noRoutes
              + " etchost=" + makeHosts + " web=" + noWeb + " noDB=" + noDB);
      aprta("argline:" + argline);
      if (!noDBMessageServer && !noDB) {
        aprta("Starting DBMessageServer " + argline);
        dbMessageServer = new DBMessageServer(argline + " >>aldbmsgsrv", "DBMS");
      } else {
        aprta("* Disabled: DBMessageServer noDB=" + noDB + " noDBMSG=" + noDBMessageServer);
      }
      if (dbaccess == null) {
        if (noDB) {
          dbaccess = new DBAccess("-empty >>alaccess", "DBAC");      // If there is noDB, alarm cannot be the master some EdgeMom must be
        } else {
          dbaccess = new DBAccess("-master >>alaccess", "DBAC");
        }
      }
      if (!noAlarm) {
        aprta("Starting Alarm " + argline);
        alarm = new Alarm(argline, "ALRM");
      } else {
        aprta("Start Alarm - no processing");
        alarm = new Alarm("-noalarm >>alarm", "ALRMN");
      }

      EdgeChannelServer echn = new EdgeChannelServer("-empty >> alechn", "Echan");
      int looper = 0;
      while (!EdgeChannelServer.isValid()) {
        try {
          Thread.sleep(100);
        } catch (InterruptedException expected) {
        }
        if (looper++ % 100 == 99) {
          aprta("Alarm:  waiting for EdgeChannelServer loop=" + looper);
        }
      }
      // start up a config thread
      if (!noCWBConfig) {
        if (Util.getProperty("instanceconfig") == null && Util.getProperty("AllowInstance") == null) {
          aprta("Starting EdgeConfigThread");
          edgeConfig = new EdgemomConfigThread(argline + " >> alcwbconfig", "ECT");
          if (dbg) {
            EdgemomConfigThread.setDebug(true);
          }
        } else {
          aprta("Starting EdgeConfigInstanceThread");
          edgeConfigFiles = new EdgemomConfigInstanceThread(argline + " >> alcwbconfig", "ECIT");
          if (dbg) {
            EdgemomConfigInstanceThread.setDebug(true);
          }
        }
      } else {
        aprta("*** Disabled: EdgeConfigThread");
      }
      if (makeHosts) {
        aprt("Starting MakeEtcHosts");
        etcHosts = new MakeEtcHosts(argline + " >>aletchosts", "ETC");
      }
      // At the NEIC these threads check certain communications links, run on gacq1 only
      String roles = System.getenv("VDL_ROLES");
      if (roles != null) {
        if (roles.contains("gacq1")) {
          qdping = true;
          checkBH = true;
        }
      }
      if (checkBH) {
        bhChecker = new HughesNOCChecker();
        aprta("Starting HughesNOCChecker");
      }
      if (qdping) {
        aprta("Starting a QDPmanager");
        qdpManager = new QDPingManager(argline + ">> alqdping", "QDP");
      } else {
        aprta("* Disabled: QDPManager");
      }

      // Setup the EdgeChannel server
      if (DBConnectionThread.noDB) {
        noWeb = true;
      } else {
        while (DBConnectionThread.getThread("edge") == null) {
          Util.sleep(1000);
          Util.prt("Waiting for edge DBConn to start");
        }
      }
      // Start a UdpChannel server
      if (!noUdpChannel) {
        aprta("Starting UdpChannel");
        aprta("Starting UDPChannel log area =" + Util.getProperty("logfilepath")
                + " nosnw=" + noSNW + " noweb=" + noWeb + " noSNW sender=" + noSNWSender + " argline=" + argline);
        UdpChannel.d = new UdpChannel(argline + " >>aludpchannel", "UDPCH");
      } else {
        aprta("* Disabled: UdpChannel");
      }
      Util.sleep(5000);    // wait for UdpChannel to start.
      if (!noSNW) {
        aprta("Staring SNWChannelClient2 noSNWSender=" + noSNWSender);
        UdpChannel.snwcc = new SNWChannelClient(null, "localhost", noSNWSender, UdpChannel.d);   // This taps the UdpChannel and sends
        if (mod > 0) {
          UdpChannel.snwcc.setLastDataModulus(mod);
        }
        UdpChannel.ls = new LatencyServer(latencyPort);
      } else {
        aprta("* Disabled: SNWChannelCLient &  Latency server noSNW=" + noSNW);
      }

      if (!noCommandStatus) {
        aprta("Starting CommandStatusServer");
        commandStatus = new CommandStatusServer(argline + ">>alcmdstatus", "CMD");
      } else {
        aprta("* Disabled: CommandStatusServer");
      }
      if (!noWeb) {
        aprta("Starting WebStationClient");
        UdpChannel.wsc = new WebStationClient(UdpChannel.snwcc.getStatusSocketReader(), UdpChannel.d);
      }
      indexFileServer = new IndexFileServer("-p " + indexFileServerPort + " >>alidxfilesrv", "IFSrv");
      //aprta("Alarm start line="+line);

      MemoryChecker memCheck = new MemoryChecker(60, true, alarm);  // check every 60 seconds and allow shutdowns on short mem
      AlarmChecker alarmChecker = new AlarmChecker();
      int loopWSC = -1;
      int lastSNWCCState = -1;
      int badSNWCC = 0;
      // In this look, insure all of the children are working
      for (;;) {
        state = 1;
        for (int i = 0; i < 60; i++) {
          try {
            Thread.sleep(1000);
          } catch (InterruptedException expected) {
          }
          if (Alarm.isShuttingDown || UdpChannel.isShuttingDown
                  || DBMessageServer.isShuttingDown) {
            aprta("Start shudown alarm=" + Alarm.isShuttingDown + " UdpChan=" + UdpChannel.isShuttingDown
                    + " DBMsg=" + DBMessageServer.isShuttingDown);
            break;    // Out of for nloop
          }
        }
        if (Alarm.isShuttingDown || UdpChannel.isShuttingDown
                || DBMessageServer.isShuttingDown) {
          break;
        }
        if (looper % 5 == 0) {
          Util.loadProperties("edge.prop");
        }
        state = 2;
        if (!noWeb) {
          boolean restart = false;
          if (looper % 10 == 0) {
            if (loopWSC == UdpChannel.wsc.getLoop()) {
              restart = true;
              aprta(Util.ascdate() + "WebStationClient does not seem to be looping-restart it");
            }
            loopWSC = UdpChannel.wsc.getLoop();
          }
          if (!UdpChannel.wsc.isAlive() || restart) {
            aprta(Util.ascdate() + " ** WebStationClient seems to be dead.  Restart it ");
            SendEvent.debugEvent("WebStaClDown", "WebStationClient found down in UdpChannel", "UdpChannel");
            UdpChannel.wsc = new WebStationClient(UdpChannel.snwcc.getStatusSocketReader(), UdpChannel.d);
          }
        }
        state = 3;
        if (!noSNW) {
          if (!UdpChannel.snwcc.isAlive()) {
            aprta(Util.ascdate() + " ** SNWChannelClient seems to be dead.  Restart it ");
            SendEvent.debugEvent("SNWCCDown", "SNWChannelCLient found down in UdpChannel", "UdpChannel");
            UdpChannel.snwcc = new SNWChannelClient(null, "localhost", noSNWSender, UdpChannel.d);
            if (UdpChannel.ls != null) {
              UdpChannel.ls.terminate();    // stop the ls since the reader is nolonger good.
            }
            if (UdpChannel.wsc != null) {
              UdpChannel.wsc.terminate();
            }
          }
          // Check on the latency server
          if (!UdpChannel.ls.isAlive()) {
            aprta(Util.ascdate() + " ** LatencyServer seems to be dead.  Restart it ");
            aprt(Util.getThreadsString());
            SendEvent.debugEvent("LatSrvDown", "LatencyServer found down in UdpChannel", "UdpChannel");
            if (UdpChannel.snwcc == null) {
              UdpChannel.ls = new LatencyServer(latencyPort);
            } else {
              UdpChannel.ls = new LatencyServer(latencyPort);
            }
          }
        }
        state = 4;
        if (!noDBMessageServer && !noDB) {
          if (!dbMessageServer.isAlive()) {
            aprta(Util.ascdate() + " ** DBMessageServer seems to be dead.  Restart it");
            dbMessageServer = new DBMessageServer(argline + " >>aldbmsgsrv", "DBMS");
          }
        }

        state = 5;
        looper++;
        if (looper % 10 == 0) {
          aprta(" Alarm:" + (!noWeb ? UdpChannel.wsc.toString() + " alive=" + UdpChannel.wsc.isAlive() : "") + " "
                  + (!noSNW ? UdpChannel.snwcc.toString() + " alive=" + UdpChannel.snwcc.isAlive() : "")
                  + (!noDBMessageServer ? dbMessageServer.toString() + " alive=" + dbMessageServer.isAlive() : "")
                  + (!noWeb ? UdpChannel.wsc.toString() + " alive=" + UdpChannel.wsc.isAlive() : "")
                  + (!noAlarm ? alarm.toString() + " alive=" + alarm.isAlive() : "")
                  + (commandStatus != null ? commandStatus.toString() + " alive=" + commandStatus.isAlive() : "")
                  + (edgeConfigFiles != null ? edgeConfigFiles.toString() + " alive=" + edgeConfigFiles.isAlive() : "")
                  + (etcHosts != null ? etcHosts.toString() + " alive=" + etcHosts.isAlive() : "")
                  + (bhChecker != null ? bhChecker.toString() + " alive=" + bhChecker.isAlive() : "")
                  + (commandStatus != null ? commandStatus.toString() + " alive=" + commandStatus.isAlive() : "")
          );
        }
        state = 6;
        if (!noSNW) {
          if (UdpChannel.snwcc.getCurrentState() != 29 && UdpChannel.snwcc.getCurrentState() == lastSNWCCState) {
            badSNWCC++;
            if (badSNWCC > 5) {
              aprta(" **** SNWCC detected dead state=" + UdpChannel.snwcc.toString());
              SendEvent.debugEvent("UdpCSNWCDead", "SNWChannel client detected dead.  Try to restart", "UdpChannel");
              UdpChannel.snwcc.terminate();
              UdpChannel.snwcc = new SNWChannelClient(null, "localhost", noSNWSender, UdpChannel.d);
            }
          } else {
            badSNWCC = 0;
          }
        }

        // 
        state = 7;
        if (UdpChannel.d != null) {
          if (!UdpChannel.d.isAlive()) {
            aprta("** UdpChannel is not running -  restart it ");
            UdpChannel.d = new UdpChannel(argline + ">>alupdchannel", "UDPCH");
          }
        }
        // 
        state = 8;
        if (bhChecker != null) {
          if (!bhChecker.isAlive()) {
            aprta("** HughesNOCCHecker is not running -  restart it ");
            bhChecker = new HughesNOCChecker();
          }
        }
        // 
        state = 9;
        if (qdpManager != null) {
          if (!qdpManager.isAlive()) {
            aprta("** QDPingManager is not running -  restart it ");
            qdpManager = new QDPingManager(argline + ">> alqdping", "QDP");
          }
        }
        // 
        state = 10;
        if (commandStatus != null) {
          if (!commandStatus.isAlive()) {
            aprta("** CommandStatusServer is not running -  restart it ");
            commandStatus = new CommandStatusServer(argline + ">>alcmdstatus", "CMD");
          }
        }
        // 
        state = 11;
        if (edgeConfig != null) {
          if (!edgeConfig.isAlive()) {
            aprta("** EdgemomConfigThread is not running -  restart it ");
            edgeConfig = new EdgemomConfigThread(argline + ">>alcwbconfig", "ECT");
            if (dbg) {
              EdgemomConfigThread.setDebug(true);
            }
          }
        }
        state = 12;
        if (edgeConfigFiles != null) {
          if (!edgeConfigFiles.isAlive()) {
            aprta("* EdgeConfigInstanceThread is not running - restart it");
            edgeConfigFiles = new EdgemomConfigInstanceThread(argline + ">>alcwbconfig", "ECIT");
            if (dbg) {
              EdgemomConfigInstanceThread.setDebug(true);
            }
          }
        }
        state = 13;
        if (etcHosts != null) {
          if (!etcHosts.isAlive()) {
            aprta("** EtcHosts is not running -  restart it ");
            etcHosts = new MakeEtcHosts(argline + " >>aletchosts", "ETC");
          }
        }
        state = 14;
        if (makeVSATRoutes != null) {
          if (!makeVSATRoutes.isAlive()) {
            aprta("** UdpChannel is not running -  restart it ");
            makeVSATRoutes = new MakeVsatRoutes(argline + ">>alvsatroutes", "MVR", true, dbg);
          }
        }
        state = 15;
        if (UdpChannel.d != null) {
          if (!UdpChannel.d.isAlive()) {
            aprta("** UdpChannel is not running -  restart it ");
            UdpChannel.d = new UdpChannel(argline + ">>alupdchannel", "UDPCH");
          }
        }
        if (!indexFileServer.isAlive()) {
          aprta("** IndexFileServer is not running -  restart it ");
          indexFileServer = new IndexFileServer("-p " + indexFileServerPort + " >>7979", "IFSrv");
        }
        state = 16;

      }   // for(;;)
      aprta("Alarm: *** Main loop has exited");
      state = 17;
      // If the infinite nloop exits, then UdpChannel has exited and every thing must go.
      if (UdpChannel.wsc != null) {
        UdpChannel.wsc.terminate();
      }
      if (UdpChannel.snwcc != null) {
        UdpChannel.snwcc.terminate();
      }
      if (UdpChannel.ls != null) {
        UdpChannel.ls.terminate();
      }
      if (dbMessageServer != null) {
        dbMessageServer.terminate();
      }
      if (UdpChannel.d != null) {
        UdpChannel.d.terminate();
      }
      if (commandStatus != null) {
        commandStatus.terminate();
      }
      if (bhChecker != null) {
        bhChecker.terminate();
      }
      if (makeVSATRoutes != null) {
        makeVSATRoutes.terminate();
      }
      if (edgeConfig != null) {
        edgeConfig.terminate();
      }
      if (etcHosts != null) {
        etcHosts.terminate();
      }
      if (qdpManager != null) {
        qdpManager.terminate();
      }
      if (alarm != null) {
        alarm.terminate();
      }
      try {
        Thread.sleep(3000);
      } catch (InterruptedException expected) {
      }
      aprta(" Alarm.main() is exiting1! " + Util.getThreadsString());
      try {
        Thread.sleep(20000);
      } catch (InterruptedException expected) {
      }
      aprta(" Alarm.main() is exiting2! " + Util.getThreadsString());
      DBConnectionThread.shutdown();
    } catch (RuntimeException e) {
      e.printStackTrace();
      aprta("Got a runtime error in alarm.  Bail out.");
      Util.exit(1);
    }
    Util.exit(1);
  }

}
