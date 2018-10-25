/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright 
 */

package gov.usgs.anss.edgemom;

/*
 * This file is part of the ORFEUS Java Library.
 *
 * Copyright (C) 2004 Anthony Lomax <anthony@alomax.net www.alomax.net>
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the ho pe that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */

 /*
 * SeedLinkClient.java
 *
 * Created on 05 April 2004, 11:31
 *
 * @author  Anthony Lomax
 */
import gov.usgs.alarm.SendEvent;
import gov.usgs.anss.edge.ChannelSender;
import gov.usgs.anss.edge.IllegalSeednameException;
import gov.usgs.anss.edge.IndexBlock;
import gov.usgs.anss.edge.RawDisk;
import gov.usgs.anss.edgethread.EdgeThread;
import gov.usgs.anss.edgethread.EdgeThreadTimeout;
import gov.usgs.anss.seed.MiniSeed;
import gov.usgs.anss.util.Util;
import gnu.trove.map.hash.TLongObjectHashMap;
import gov.usgs.anss.edgethread.ManagerInterface;
import gov.usgs.edge.config.Channel;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.net.InetAddress;
import java.io.File;
import java.net.UnknownHostException;
import nl.knmi.orfeus.seedlink.SLLog;
import nl.knmi.orfeus.seedlink.SLPacket;
import static nl.knmi.orfeus.seedlink.SLPacket.SLHEADSIZE;
import static nl.knmi.orfeus.seedlink.SLPacket.SLRECSIZE;
import nl.knmi.orfeus.seedlink.SeedLinkException;
import nl.knmi.orfeus.seedlink.client.SeedLinkConnection;
import nl.knmi.orfeus.seedlink.client.SLState;

/**
 *
 * Basic class to create and use a connection to a SeedLink server using a
 * Anthony Lomax's SeedLink library (now modified into USGSSeedLink).
 *<p>
 * A new SeedLink application can be created by subclassing SeedLinkClient and
 * overriding at least the packetHandler method of SeedLinkClient.
 *<p>
 *
 * This client has been modified to be an EdgeThread from its original source by
 * Anthony Lomax. It makes a connection to a SeedLink server, uses its command
 * line or configuration file to do setup and select channels, and then returns
 * MiniSeed blocks. It then writes the data to the edge database using the
 * IndexBlock.writeMiniSeedCheckMidnight() method.
 * <p>
 * If the -l option is used which specifies a file with a list of stations to
 * monitor, then this file is monitored every 2 minutes for changes. This causes
 * the thread to terminate and it will be restarted by the EdgeMom on its next
 * thread check.  
 * 
 * <p>
 * Note that the SeedLinkConfigBuilder can be use to create a list of stations given
 * a network code and SeedLink server for all stations in that network.  This is preferable
 * if all stations from a network are needed as it will learn about new stations and cause them
 * to come in shortly after they are found in the SeedLink server.
 *
 * <br>
 * <PRE>
 *USGS arguments    description
 * -dbg              Turn on debugging of the SeedLinkClient thread (not of the SeedLink library routines)
 * -noudpchan        Do not send data summary to UdpChannel (ChannelDisplay)
 * -nohydra          Do not send data to Hydra
 * -noevent         Do not send any connection events out (used for single stations that are down)
 * -etna2            If set do the mapping for status channels that is standard for Etna2 (deg=DEG, vep=VEP,....)
 * -hydraoor         If the data should be sent through the ChannelInfrasture in Hydra (this data is often out of order)
 * -OS was=to/was1=to1/.....  Change NNSSSSS[CCC[[LL]] 'was' to 'to' on input (allow network and station name chan code and location code
 * -no OV%N1%N2,O1%n3 Change N1, N2, network codes to OV, n3 to O1, others unchanged
 * -dlc XX         Change any "empty" location codes to XX
 *
 *    SeedLink class parameters :
 *## General program options ##
 *-V             Report program version
 *-h             Show this usage message
 *-v             Set verbosity "-vv" is more verbose, this turns on debug output from the SeedLink library
 *-p             Print details of data packets
 *-nd delay      Network re-connect delay (seconds), default 30
 *-nt timeout    Network timeout (seconds), re-establish connection if no
 *                 data/keepalives are received in this time, default 120
 *-k interval    Send keepalive (heartbeat) packets this often (seconds) (if not set, it will be network timeout/2)
 *-x statefile   Save/restore stream state information to this file
 *-i infolevel   Request this INFO level, write response to std out, and exit
 *                 infolevel is one of: ID, STATIONS, STREAMS, GAPS, CONNECTIONS, ALL
 *
 *## Data stream selection ##
 * -lc            Use self configuration from the info STREAMS to build stream list
 * -lcx  Regexp   Exclude the NNSSSSS which match regular expression from inclusion
 *                in self configuration (uses String.match())
 *-l listfile     read a stream list from this file for multi-station mode
 *                The file is of the form NN SSSS [SELECTORS] one per line.  If -s SELECTORS is
 *                omitted the selectors are on the lines in the file.
 *       Examples :  AK ATKA      # gets all channels
 *                   MN AQU BH? HH?
 *                   US KSU1 00BH? 10BH? 00LH? 10LH? 20HN?
 *-s selectors   selectors for uni-station or default for multi-station.  Ex: BH?&LH?&HH?
 *-S streams     select streams for multi-station (requires SeedLink &gt;= 2.5)
 *  'streams' = 'stream1[/selectors1],stream2[/selectors2],...'
 * selectors must be separated by '&' the selector format is [LL]BH? where location code can be omitted if blanks
 *       'stream' is in NET_STA format, for example:
 *          -S "IUANMO/???,IU_KONO/00BH?,GE_WLF/BH?&LH?,MN_AQU:HH?.D"
 *     &lt;[host]/port&gt;[/bind_adr]  Address of the SeedLink server in host:port format\n"
 *                 if host is omitted (i.e. '/18000'), localhost is assumed.
 *                 Note adding the bind address is not part of the original package so USGS code required.
 *                 The station can be selected with wild cards like HV_???,HV_????,HV_????? but this is
 *                 a bad idea (at least at the NEIC, as the state file does not have station by station
 *                 state.  Note that HV_??? only matches 3 character station names so to bet all stations
 *                 All of the above selectors are necessary,
 *
 *
 * Examples :
 *# in BUD case the bud_sl just has &lt;net&gt; &lt;Station&gt; and channel descriptor are from the command line
 * BUDSL:SeedLinkClient:-s BH?&LH? -l bud_sl.setup -nt 600 -k 30 -dbg bud.iris.washington.edu/18000 &gt;&gt;bud_sl
 * AUSL:SeedLinkClient:-S AU_ARMA/BH?&LH?,AU_ARMA/BH?&LH? 192.104.43.81/18000
 *
 * The bud_sl.setup would look like (# can be used for comments in this file) :
 * US DUG           # this uses the -s selectors
 * US ACSO BH?      # get all BH? regardless of location code (you cannot select only BHZ if there are LLBHZ
 * US NEW 00BH?     # get all BH? with location code '00'
 * US DUG 00BH? LH? # get all BH? with location code '00' and all LH? channel regardless of location code
 * .
 * .
 * The selectors can be entirely in the setup file (omit -s or -S on command line) :
 * US AAM 00BH? 00LH?
 * US KSU1 00BH? 00LH? 10BH? 10LH? 20HN?
 * .
 * 
 * -cfg stationregex/chanregex  Use the SeedLinkConfig builder to create a configuration file
 *                              that will be used for choosing stations.  The stations
 *                              are found using slinktool -Q.
 *                              !!!This flag is not compatible with the -s and -S switches.!!!
 *                              If -cfg is used any values in -s and -S will be ignored!!
 *                              Below is how to use stationregex and chanregex.
 *                stationregex  This is the Network then station code i.e USAGMN, regular
 *                              expressions are allowed, US|UW  or PTKHU|GSOK.|USAH.D
 *                              NOTE: underscores are converted to spaces
 *                channelregex  This is the channel the location code, again regular
 *                              expressions are allowed, HN.|HH.|BH.00|LH.   Note: the location
 *                              code comes after the channel and when tested the two are
 *                              put together as one word.
 *                              NOTE: underscores are converted to spaces
 *  
 *                              Both stationregex and chanregex must have a minimum
 *                              value of at least a period i.e. :
 *                              -cfg ./.
 * 
 *
 * </PRE>
 */
public final class SeedLinkClient extends EdgeThread implements ManagerInterface {
  private static final String [] ETNA2MAPIN =  {"cpu","deg","dsk","lce","lcq","vec","vep","rng"};
  private static final String [] ETNA2MAPOUT = {"ACP","ADG","ADK","LCE","LCQ","LEC","LEP","ARG"};
  // Constants
  /**
   * The full class name.
   */
  public static final String PACKAGE = "nl.knmi.orfeus.seedlink.client.SeedLinkClient";

  /**
   * The class name.
   */
  public static final String CLASS_NAME = "SeedLinkClient";

  /**
   * The version of this class.
   */
  public static final String VERSION = "1.0.0X02";

  public static final String VERSION_YEAR = "2004";
  public static final String VERSION_DATE = "07Jun" + VERSION_YEAR;
  public static final String COPYRIGHT_YEAR = /*"2004-" + */ VERSION_YEAR;

  public static final String PROGRAM_NAME = "SeedLinkClient v" + VERSION;
  public static final String VERSION_INFO = PROGRAM_NAME + " (" + VERSION_DATE + ")";

  public static final String[] BANNER = {
    VERSION_INFO,
    "Copyright " + (char) 169 + " " + COPYRIGHT_YEAR
    + " Anthony Lomax (www.alomax.net)",
    "SeedLinkClient comes with ABSOLUTELY NO WARRANTY"
  };

  private ChannelSender csend;
  private boolean noevent;
  private boolean hydraoor;
  private String orgtag;
  private final TLongObjectHashMap<StringBuilder> overrides = new TLongObjectHashMap<>();
  StringBuilder nnsssss = new StringBuilder("123456789012");
  StringBuilder tmpsb = new StringBuilder(100);
  private SeedLinkConfigBuilder slcConfigBuilder = null;
  // parameters

  /**
   * SeedLinkConnection object for communicating with the SeedLinkServer over a
   * socket.
   */
  public SeedLinkConnection slconn = null;

  /**
   * Verbosity level, 0 is lowest.
   */
  public int verbose = 0;

  /**
   * Flag to indicate show detailed packet information.
   */
  public boolean ppackets = false;

  /**
   * Name of file containing stream list for multi-station mode.
   */
  public String streamfile = null;
  private String streamfileContents;

  /**
   * Selectors for uni-station or default selectors for multi-station.
   */
  public String selectors = null;

  /**
   * Selectors for multi-station.
   */
  public String multiselect = null;

  /**
   * Name of file for reading (if exists) and storing state.
   */
  public String statefile = null;

  /**
   * INFO LEVEL for info request only.
   */
  public String infolevel = null;

  /**
   * Logging object.
   */
  public SLLog sllog = null;
  ShutdownSeedLinkClient shutdown;

  /**
   * ANSS variables.
   */
  private String[] netoverrides;
  private int npackets;
  private int lastNpackets;
  private boolean selfConfig;
  private String currentConfig;   // If self config, this is the current config
  private byte[] defaultLocation; // If not-null, blank or missing location codes are this
  private String selfConfigExcludes;
  private long lastStatus;
  private int nstations;
  boolean hydra;
  boolean dbg;
  boolean nocsend;
  // Manager thread stuff
  private boolean check;
  private long inbytes;
  private long outbytes;
  private String argsin;

  @Override
  public void setCheck(boolean t) {
    check = t;
  }

  @Override
  public boolean getCheck() {
    return check;
  }

  @Override
  public String getArgs() {
    return argsin;
  }

  public void setDebug(boolean t) {
    dbg = t;
  }

  @Override
  public long getBytesIn() {
    return inbytes;
  }

  @Override
  public long getBytesOut() {
    return outbytes;
  }

  /**
   * Terminate thread (causes an interrupt to be sure). You may not need the
   * interrupt!
   */
  @Override
  public void terminate() {
    terminate = true;
    prta(tag + "SLC: terminate called");
    interrupt();
    //if(slconn.isConnected()) {
    prta(tag + "SLC: terminate() called");
    slconn.terminate();
    if (slcConfigBuilder != null) slcConfigBuilder.terminate();
    //}
    prta(tag + "SLC: terminate() exiting");
  }
  /**
   * Return the monitor string for Icinga.
   *
   * @return A String representing the monitor key value pairs for this
   * EdgeThread.
   */
  long lastMonPackets;

  @Override
  public StringBuilder getMonitorString() {
    Util.clear(monitorsb);
    long np = npackets - lastMonPackets;
    lastMonPackets = npackets;
    return monitorsb.append(tag).append("SeedLinkNrecs=").append(np).append("\n");
  }

  /**
   * Return the status string for this thread
   *
   * @return A string representing status for this thread.
   */
  @Override
  public StringBuilder getStatusString() {
    Util.clear(statussb);
    return statussb.append("#pck=").append(lastNpackets).append(" hydra=").append(hydra).
            append(" dbg=").append(dbg).append(" select=").append(selectors).append(" #stat=").
            append(nstations).append(" config=").append(streamfile);
  }

  /**
   * Return console output - this is fully integrated so it never returns
   * anything.
   *
   * @return "" Since this cannot get output outside of the prt() system.
   */
  @Override
  public StringBuilder getConsoleOutput() {
    return consolesb;
  }

  /**
   *
   * Creates a new instance of SeedLinkClient with the specified logging object.
   *
   * @param argline EdgeThread argument
   * @param tg The EdgeThread tag
   *
   */
  public SeedLinkClient(String argline, String tg) {
    super(argline, tg);
    if(argline.contains("#")) argline = argline.substring(0, argline.indexOf("#"));
    String[] args = argline.split("\\s");
    boolean etna2 = false;
    String cfgArgs = null;
    argsin = argline;
    dbg = false;
    nocsend = false;
    hydra = true;
    orgtag = tg;
    if (orgtag.contains("-")) {
      orgtag = orgtag.substring(0, orgtag.indexOf("-"));
    }
    for (int i = 0; i < args.length; i++) {
      prt(Util.clear(tmpsb).append(i).append(" arg=").append(args[i]));
      if (args[i].equals("-dbg")) {
        dbg = true;
      } else if (args[i].equals("-noudpchan")) {
        nocsend = true;
      } else if (args[i].equals("-nohydra")) {
        hydra = false;
      } else if (args[i].equals("-hydraoor")) {
        hydraoor = false;
      } else if (args[i].equals("-noevent")) {
        noevent = true;
      } else if (args[i].substring(0, 1).equals(">")) {
        break;
      } else if (args[i].equals("-OS")) {    // Its an override list
        String[] pairs = args[i + 1].replaceAll("_"," ").replaceAll("-"," ").split("/");
        for (String pair : pairs) {
          String[] parts = pair.split("=");
          if (parts.length == 2 && parts[0].length() == parts[1].length()) {
            StringBuilder tmp = new StringBuilder(7);
            tmp.append((parts[1] + "       ").substring(0, 7));
            if(parts[1].length() > 7) tmp.append((parts[1].substring(7)+"     ").substring(0,5));
            if(parts[0].length() < 7) parts[0] = (parts[0]+"       ").substring(0,7);
            else parts[0] = (parts[0] + "     ").substring(0,12);
            prta(Util.clear(tmpsb).append("Change ").append(parts[0]).append("| to ").append(tmp).append("|"));
            overrides.put(Util.getHashFromSeedname(parts[0]), tmp);
          } else {
            prta(Util.clear(tmpsb).append("Format error in overrides (not two parts or bad lengths) list =").append(args[i + 1]));
          }
        }
        i++;
      }
      else if(args[i].equals("-etna2") || args[i].equals("-etna")) {
        etna2 = true;
      }
      else if(args[i].equals("-V") || args[i].equals("-v") || args[i].equals("-p") || args[i].equals("-h") || 
              args[i].equals("-dbg") ) ;
      else if(args[i].equals("-nt") || args[i].equals("-nd") || args[i].equals("-k") || args[i].equals("-l") ||
              args[i].equals("-no") ||args[i].equals("-dlc") ||args[i].equals("-lc") ||args[i].equals("-s") ||
              args[i].equals("-S") ||args[i].equals("-x") ||args[i].equals("-i")) i++;
      else if (args[i].equals("-cfg") ) {
        cfgArgs = args[++i];
      }
      else prt("SLC :  client unknown switch="+i+" "+args[i]+" ln="+argline);
    }
    this.setDaemon(true);

    prt(Util.clear(tmpsb).append("SLC: new line parsed to  dbg=").append(dbg).
            append(" noscend=").append(nocsend).append(" hydra=").append(hydra).append(" hydraoor=").append(hydraoor));
    shutdown = new ShutdownSeedLinkClient();
    Runtime.getRuntime().addShutdownHook(shutdown);
    if (terminate) {
      return;
    }
    slconn = new SeedLinkConnection(sllog);
    parseCmdLineArgs(args);
    // For etna setup standard channel translations for status channels.
    if(etna2) {
      String nn_sssss = multiselect.substring(0, multiselect.indexOf(":"));
      nn_sssss = nn_sssss.replaceAll("_", "");
      nn_sssss = (nn_sssss + "       ").substring(0,7);
      for(int i=0; i<ETNA2MAPIN.length; i++ ) {
        StringBuilder tmp = new StringBuilder(12);
        String in = (nn_sssss + ETNA2MAPIN[i] + "          ").substring(0,12);
        Util.clear(tmp).append(nn_sssss).append((ETNA2MAPOUT[i] + "         ").substring(0,5));
        prta(Util.clear(tmpsb).append("Change etna2 ").append(in).append("| to ").append(tmp).append("|"));
        overrides.put(Util.getHashFromSeedname(in), tmp);
      }
    }
    
    if (cfgArgs != null) {
      if (selectors != null || multiselect != null) {
        Util.prta("Warning!! -cfg and -s or -S have been set.  -cfg options will override!");
      }
      selectors = null;
      multiselect = null;
      String[] cfgExps = cfgArgs.split("/");
      if (cfgExps.length != 2) {
        Util.prt("!!!Error!!! Insufficient, too many or improper arguments in -cfg parameter!");
        return;
      }
      String cfgSendArgs = new String("-h " + slconn.getSLAddress().replaceAll(":", "/") + 
              " -s " + cfgExps[0] + " -c " +cfgExps[1] + " -f " + streamfile + 
              " >> " + getLogFilename().substring(0,getLogFilename().indexOf(".log")) + "Cfg");
      slcConfigBuilder = new SeedLinkConfigBuilder(this,cfgSendArgs,tg + "Cfg");
      if (!slcConfigBuilder.waitForStart(600)) {
        Util.prt("!!!Error!!! SeedlinkConfigBuilder failed to start.  Exiting!");
        slcConfigBuilder.terminate();
        return;
      } 
    }
    
    start();
  }

  /**
   *
   * Parses the command line arguments.
   *
   * @param args The main method arguments.
   *
   *
   * @return -1 on error, 1 if version or help argument found, 0 otherwise.
   *
   */
  public final int parseCmdLineArgs(String[] args) {

    if (args.length < 1) {
      printUsage(false);
      return (1);
    }

    int optind = 0;

    while (optind < args.length) {

      if (args[optind].equals("-V")) {
        prta(VERSION_INFO);
        return (1);
      } else if (args[optind].equals("-h")) {
        printUsage(false);
        return (1);
      } else if (args[optind].startsWith("-v")) {
        verbose += args[optind].length() - 1;
      } else if (args[optind].equals("-p")) {
        ppackets = true;
      } else if (args[optind].equals("-nt")) {
        slconn.setNetTimout(Integer.parseInt(args[++optind]));
      } else if (args[optind].equals("-nd")) {
        slconn.setNetDelay(Integer.parseInt(args[++optind]));
      } else if (args[optind].equals("-k")) {
        slconn.setKeepAlive(Integer.parseInt(args[++optind]));
      } else if (args[optind].equals("-l")) {
        streamfile = args[++optind];
        try {
          byte[] b;
          try (RawDisk rw = new RawDisk(streamfile, "r")) {
            b = new byte[(int) rw.length()];
            rw.read(b, 0, (int) rw.length());
          }
          streamfileContents = new String(b);
        } catch (IOException e) {
          prta("self config does not have a starting file!");
          streamfileContents = "";
        }
      } else if (args[optind].equals("-no")) {
        netoverrides = args[++optind].split(",");
      } else if (args[optind].equals("-dlc")) {
        defaultLocation = (args[++optind] + "  ").substring(0, 2).getBytes();

      } else if (args[optind].equals("-lc")) {
        streamfile = args[++optind];
        selfConfig = true;
        try {
          byte[] b;
          try (RawDisk rw = new RawDisk(streamfile, "r")) {
            b = new byte[(int) rw.length()];
            rw.read(b, 0, (int) rw.length());
          }
          currentConfig = new String(b);
        } catch (IOException e) {
          prta("self config does not have a starting file!");
          currentConfig = "";
        }
      } else if (args[optind].equals("-lcx")) {
        selfConfigExcludes = args[++optind].replaceAll("-", " ").replaceAll("&", " ");
      } else if (args[optind].equals("-s")) {
        selectors = args[++optind];
        selectors = selectors.replaceAll("&", " ").replaceAll("-"," ").replaceAll("_"," ");
        prta(Util.clear(tmpsb).append("Channel selectors=").append(selectors));
      } else if (args[optind].equals("-S")) {
        multiselect = args[++optind].replaceAll("&", " ").replaceAll("-"," ");
        multiselect = multiselect.replaceAll("/", ":");
        prta(Util.clear(tmpsb).append("Stations selected=").append(multiselect));
      } else if (args[optind].equals("-x")) {
        statefile = args[++optind];
      } else if (args[optind].equals("-i")) {
        infolevel = args[++optind];
      } else if (args[optind].equals("-dbg")) {
        dbg = true;
      } else if (args[optind].equals("-noudpchan") || args[optind].equalsIgnoreCase("-noevent")); 
      else if (args[optind].equals("-nohydra") || args[optind].equals("-hydraoor")) ; 
      else if (args[optind].equals("-OS") || args[optind].equals("-cfg")) {
        optind++;
      } else if (args[optind].startsWith("-")) {
        prta(Util.clear(tmpsb).append("Unknown option2: ").append(args[optind]));
        //return(-1);
      } else if (slconn.getSLAddress() == null) {
        prta(Util.clear(tmpsb).append("Setting IP with ").append(args[optind]));
        slconn.setSLAddress(args[optind].replaceAll("/", ":"));
      }
      /*else {
				prta("Unknown option: " + args[optind]);
				return(-1);
			}*/
      optind++;

    }
    
    // Check keepalive and network timeout to make sure the values make sense
    if(slconn.getKeepAlive() == 0 && slconn.getNetTimout() >=30) {
      slconn.setKeepAlive(slconn.getNetTimout()/2);
      prta(Util.clear(tmpsb).append("**** keepalive not specified set to network timeout/2=").
              append(slconn.getKeepAlive()));
    }
    if (slconn.getKeepAlive() > 0 && slconn.getKeepAlive()*2 < slconn.getNetTimout()) {
      slconn.setKeepAlive(slconn.getNetTimout()/2);
      prta(Util.clear(tmpsb).append("*** network timeout=").append(slconn.getNetTimout()).
              append(" keepalive=").append(slconn.getKeepAlive()).append(" incompatible"));    
    }
    return (0);

  }

  /**
   *
   * Initializes this SLCient.
   *
   * @exception SeedLinkException on error.
   * @exception UnknownHostException if no IP address for the local host could
   * be found.
   *
   */
  public void init() throws UnknownHostException, SeedLinkException, IOException {

    // Make sure a server was specified
    if (slconn.getSLAddress() == null) {
      String message = "no SeedLink server specified";
      throw (new SeedLinkException(message));
    }
    prta(Util.clear(tmpsb).append("SLC: init() host=").append(slconn.getSLAddress()));

    // Initialize the log object
    if (sllog == null) {
      sllog = new SLLog(this);
    }
    //sllog = new SLLog(verbose, getPrintStream(), null, null, null);
    slconn.setLog(sllog);

    // Report the program version
    //sllog.log(false, 1, VERSION_INFO);
    // If verbosity is 2 or greater print detailed packet infor
    //if ( verbose >= 2 )
    //	ppackets = true;
    // If no host is given for the SeedLink server, add 'localhost'
    if (slconn.getSLAddress().startsWith(":")) {
      slconn.setSLAddress(InetAddress.getLocalHost().toString() + slconn.getSLAddress());
    }

    // Load the stream list from a file if specified
    prta(Util.clear(tmpsb).append("stream file load=").append(streamfile));
    if (streamfile != null) {
      nstations = slconn.readStreamList(streamfile, selectors);
    }

    // Parse the 'multiselect' string following '-S'
    prta(Util.clear(tmpsb).append("Muliselect load = ").append(multiselect));
    if (multiselect != null) {
      slconn.parseStreamlist(multiselect, selectors);
    } else if (streamfile == null) // No 'streams' array, assuming uni-station mode
    {
      slconn.setUniParams(selectors, -1, null);
    }

    // Attempt to recover sequence numbers from state file
    prta(Util.clear(tmpsb).append("set state file=").append(statefile));

    if (statefile != null) {
      File f = new File(statefile);
      if(!f.exists()) {
        prta(Util.clear(tmpsb).append("Create empty statefile=").append(statefile));
        f.createNewFile();
      }      
      slconn.setStateFile(statefile);
    }
    prta(Util.clear(tmpsb).append("SLC: init() host URL=").append(slconn.getSLAddress()));

    //slconn.lastpkttime = true;
  }

  /**
   *
   * Start this SLCient.
   */
  @Override
  public void run() {
    running = true;
    long now;
    long lastConfig = System.currentTimeMillis();
    long lastWaitCheck = System.currentTimeMillis() - 1000;
    int count1MBPS=0;
    SLPacket slpack = null;
    //DCK : create the slpacket for reuse
    try {
      slpack = new SLPacket(new byte[SLHEADSIZE + SLRECSIZE], 0);
    } catch (SeedLinkException e) {
      // This cannot happen
      prta("SeedLinkException creating SeedLinkConnection slpacket- impossible");
    } // DCK: end create slpacket		
    if (terminate) {
      return;
    }
    StringBuilder chkConfig = new StringBuilder(100);
    byte[] bc = new byte[2000];
    try {
      init();
      //slconn.connect();
    } catch (IOException e) {
      prta(Util.clear(tmpsb).append("SLC: IOexception init or connect e=").append(e.getMessage()));
      return;
    } catch (SeedLinkException e) {
      prta(Util.clear(tmpsb).append("SLC: SeedLinkException init e=").append(e.getMessage()));
      running = false;
      return;
    }
    if (!nocsend && csend == null) {
      csend = new ChannelSender("  ", "SeedLinkCl", "SLC-" + tag);
    }
    prta(Util.clear(tmpsb).append("SLC: start() slconn=").append(slconn.toString()));
    EdgeThreadTimeout eto = new EdgeThreadTimeout(getTag(), this, 900000, 1200000);
    try {
      prta(Util.clear(tmpsb).append(tag).append("SLC: Doing request Info as part of startup infolevel=").append(infolevel));
      // If selfconfig is set, we need to issue a requestInfo first to build the file
      if (selfConfig) {
        String infostr = "STATIONS";
        slconn.requestInfo(infostr);
      } else if (infolevel != null) {
        slconn.requestInfo(infolevel);
      }
    } catch (SeedLinkException e) {
      prta(Util.clear(tmpsb).append(tag).append("SLC: SeedLinkException on requestInfo() e=").append(e.getMessage()));
    }
    try {
      // Loop with the connection manager
      eto.resetTimeout();
      MiniSeed ms;
      int count = 1;
      long minLatency = 7200000;
      long lastMinLatency = 0;
      boolean minLatencyDisconnect = false;
      int minLatencyCount = 0;
      try {
        if (dbg) {
          prta(Util.clear(tmpsb).append(tag).append("SLC: Enter collect()"));
        }
        while (!terminate) {

          while ((slpack = slconn.collect(slpack)) != null) {
            if (terminate) {
              break;
            }
            eto.resetTimeout();
            if (slpack == SLPacket.SLTERMINATE || slconn.getState().state == SLState.SL_DOWN) {
              prta(Util.clear(tmpsb).append(tag).append("SLC: slconn.collect() has packet in SLTERMINATE or slconn is in state SLDOWN ").
                      append(slpack == SLPacket.SLTERMINATE).append(" ").append(slconn.getState().state == SLState.SL_DOWN));
              terminate = true;
              break;
            }

            try {
              // Check if not a complete packet
              if (slpack == null || slpack == SLPacket.SLNOPACKET || slpack == SLPacket.SLERROR) {
                prta(Util.clear(tmpsb).append(tag).append("SLC: got slpack null, SLNOPACKET or SLERROR").
                        append(slpack).append(" NOPACKET=").append(SLPacket.SLNOPACKET).
                        append(" SLERROR=").append(SLPacket.SLERROR));
                continue;
              }

              // Get basic packet info
              int seqnum = slpack.getSequenceNumber();
              int type = slpack.getType();

              // Process INFO packets here
              if (type == SLPacket.TYPE_SLINF) {
                //prta(tag+"SLC: Unterminated INFO packet: [" + (new String(slpack.msrecord, 0, 20)) + "]");
                continue;
              }
              // This type indicates the Info packet is in and complete; analyze it
              if (type == SLPacket.TYPE_SLINFT) {
                String s = slconn.getInfoString();
                prta(Util.clear(tmpsb).append(tag).append("SLC: Complete INFO:").append(s).append("<end>"));
                // selfconfig means we need to parse the station message and make a new file
                if (selfConfig) {
                  if (s.indexOf("station") > 0) { // It is a station one
                    String config = parseConfig(s);
                    if (!currentConfig.equals(config)) {
                      currentConfig = config;
                      prta(Util.clear(tmpsb).append(tag).append(" Configuration has changed.  Write new file and reconnect"));
                      try {
                        try (RawDisk rw = new RawDisk(streamfile, "rw")) {
                          rw.writeBlock(0, currentConfig.getBytes(), 0, currentConfig.length());
                          rw.setLength((long) currentConfig.length());
                        }
                        eto.resetTimeout();
                        slconn.disconnect();
                        init();
                        //boolean connected=false;
                        while (true) {
                          try {
                            eto.resetTimeout();
                            slconn.connect();
                            eto.resetTimeout();
                            break;
                          } catch (SeedLinkException e) {
                            prt(Util.clear(tmpsb).append(tag).append("SLC: SeedLinkException connnecting2 e=").append(e.getMessage()));
                          } catch (IOException e) {
                            prta(Util.clear(tmpsb).append(tag).append(" IOError trying to reconnect to server").append(e));
                            if (!noevent) {
                              SendEvent.edgeSMEEvent("SLNoConn", tag + " Cannot connect " + (slconn == null ? "null" : slconn.getSLAddress()), this);
                            }
                            e.printStackTrace(getPrintStream());
                          }
                          try {
                            sleep(10000);
                          } catch (InterruptedException e) {
                          }
                        }
                      } catch (IOException e) {
                        prta(Util.clear(tmpsb).append(tag).append("Error writing station list file"));
                      }
                    } else {
                      prta(Util.clear(tmpsb).append(tag).append(" Configuration is unchanged."));
                    }
                  } else {
                    prta("  ***** Got an info, but it is not a station list!");
                  }
                }

                continue;
              }

              // Can send an in-line INFO request here
              /*if (count % 10000 == 0) {
                prta(tag+"SLC: requesting Info periodically count="+count+" type="+type);
                String infostr = "ID";
                slconn.requestInfo(infostr);
              }*/
              // If here, must be a blockette
              //if(dbg) prta(CLASS_NAME + ": packet seqnum: " + seqnum + ": packet type: " + type);
              // Check to see if an override is needed, if so do it
              Util.clear(nnsssss).append("1234567");
              for (int j = 18; j < 20; j++) {
                nnsssss.setCharAt(j - 18, (slpack.msrecord[j] == 0 ? ' ' : (char) slpack.msrecord[j]));
              }
              for (int j = 8; j < 13; j++) {
                nnsssss.setCharAt(j - 6, (slpack.msrecord[j] == 0 ? ' ' : (char) slpack.msrecord[j]));
              }
              if (netoverrides != null) {
                //String nn = nnsssss.substring(0,2);
                for (String netoverride : netoverrides) {
                  if (netoverride.charAt(3) == nnsssss.charAt(0) && netoverride.charAt(4) == nnsssss.charAt(1)) {
                    //if(netoverrides[j].indexOf(nn) > 0) {
                    if (dbg) {
                      prta(Util.clear(tmpsb).append("override net ").append(nnsssss).append(" to ").append(netoverride));
                    }
                    slpack.msrecord[18] = (byte) netoverride.charAt(0);
                    slpack.msrecord[19] = (byte) netoverride.charAt(1);
                    slpack.reload();
                    break;
                  }
                }
              }
              if (overrides.get(Util.getHashFromSeedname(nnsssss)) != null) {    // Its on the override list, change it
                StringBuilder b = overrides.get(Util.getHashFromSeedname(nnsssss));
                for (int j = 18; j < 20; j++) {
                  slpack.msrecord[j] = (byte) b.charAt(j - 18);
                }
                for (int j = 8; j < 13; j++) {
                  slpack.msrecord[j] = (byte) b.charAt(j - 6);
                }
                slpack.reload();
                if (dbg) {
                  prta(Util.clear(tmpsb).append("Do override on ").append(nnsssss).
                          append(" to ").append(b).append(" ms=").append(slpack.getMiniSeed()));
                }
              }
              // Add the channel and location to nnsssss and test for changes
              nnsssss.append("89012");
              for(int j=15; j<18; j++) {
                nnsssss.setCharAt(j-8, (slpack.msrecord[j] == 0 ? ' ' : (char) slpack.msrecord[j]));
              }
              for(int j=13; j<15; j++) {
                nnsssss.setCharAt(j-3, (slpack.msrecord[j] == 0 ? ' ' : (char) slpack.msrecord[j]));
              }          
              
              if (overrides.get(Util.getHashFromSeedname(nnsssss)) != null) {
                StringBuilder b = overrides.get(Util.getHashFromSeedname(nnsssss));
                for (int j=15; j<18; j++) {    // substitue the channel name as well
                  slpack.msrecord[j] = (byte) b.charAt(j - 8);
                }
                if (b.length() > 10) {     // Does the mask include the location code.
                  for(int j=13; j<15; j++) {
                    slpack.msrecord[j] = (byte) b.charAt( j - 3);
                  }
                }
                slpack.reload();
                if (dbg) {
                  prta(Util.clear(tmpsb).append("Do override chan ").append(nnsssss).
                          append(" to ").append(b).append(" ms=").append(slpack.getMiniSeed()));
                }  
             }
              // If the location code is not alphanumeric, set default if given
              if (defaultLocation != null) {
                if (slpack.msrecord[13] < '0' && slpack.msrecord[14] < '0') {
                  slpack.msrecord[13] = defaultLocation[0];
                  slpack.msrecord[14] = defaultLocation[1];
                  slpack.reload();
                  if (dbg) {
                    prta(Util.clear(tmpsb).append("Do override location code ").append(nnsssss).append(" to ").
                            append(defaultLocation[0]).append(defaultLocation[1]));
                  }
                }

              }

              //try {
              ms = slpack.getMiniSeed();
              //if(ms == null) ms = new MiniSeed(slpack.msrecord);
              //else if (ms.getBlockSize() < slpack.msrecord.length) ms = new MiniSeed(slpack.msrecord);
              //else ms.load(slpack.msrecord);
              //}
              //catch(IllegalSeednameException e) {
              //  prta("IllegalSeedname Let it go. e="+e);
              //  continue;
              //}

              npackets++;
              inbytes = inbytes + 520;
              Channel c = EdgeChannelServer.getChannel(ms.getSeedNameSB());
              //if(seedname.indexOf("REPV15")>= 0)  par.prta(seedname+": "+c.toString()+" flags="+c.getFlags()+" mask="+mask+" hydra="+hydra);
              if (c == null) {
                prta(Util.clear(tmpsb).append(tag).append("SLC: ***** new channel found=").append(ms.getSeedNameSB()));
                SendEvent.edgeSMEEvent("ChanNotFnd", "SLC: MiniSEED Channel not found=" + ms.getSeedNameSB() + " new?", this);
                EdgeChannelServer.createNewChannel(ms.getSeedNameSB(), ms.getRate(), this);
              }
              IndexBlock.writeMiniSeedCheckMidnight(ms, false, tag, this); // Write to database, but not to Hydra this way
              if (hydra) {
                if(hydraoor) {
                  Hydra.send(ms);   // if it might be out of order.
                }
                else {
                  Hydra.sendNoChannelInfrastructure(ms);
                }        // Do Hydra via No Infrastructure
              }              // Send to UdpChannel server
              try {
                if (csend != null) {
                  int millis = (int) (ms.getTimeInMillisTruncated() % 86400000);
                  csend.send(ms.getJulian(),
                          millis , // Milliseconds
                          ms.getSeedNameSB(), ms.getNsamp(), ms.getRate(), ms.getBlockSize());  // key,nsamp,rate and nbytes
                }
              } catch (IOException e) {
                prta(Util.clear(tmpsb).append(tag).append("SLC: IOException sending Channel UDP=").append(e.getMessage()));
              }
              now = System.currentTimeMillis();
              if (dbg || ppackets || count % 10000 == 1) {
                //long latency = ms.getNextExpectedTimeInMillis() - System.currentTimeMillis();
                prta(Util.clear(tmpsb).append("cnt=").append(count).append(" ").append(ms.toStringBuilder(null)).
                        append(" lat=").append((now - ms.getNextExpectedTimeInMillis()) / 1000).append("/").
                        append(minLatency / 1000).
                        append(" s").append(" sq=").append(seqnum));
                sllog.setLog(this.getPrintStream());
              }

              // set a maximum input sustained rate of 1 mbps (240 blocks is about 1 mbits so make sure it takes one second
              // Check every 240 blocks, count how many consecutive in count1MPS, slow things down if its more than 10 consecutive cycles
              if (count % 240 == 1) {
                if (now - lastWaitCheck < 1000) {
                  if(count1MBPS++ % 100 == 99) {
                    SendEvent.edgeSMEEvent("SLWait1mbps", tag + " Is waiting because its > 1 mbps", this);
                  }
                  if(count1MBPS >= 3) {
                    prta(Util.clear(tmpsb).append(tag).
                          append(" *** Data flow above 1 mbps - slow things down if cnt>10 ").append(1000 - now + lastWaitCheck).
                          append(" ms").append(" cnt=").append(count1MBPS));
                  }
                  if(count1MBPS > 10) {   // 10 consecutive times, slow down this link
                    try {
                      sleep(Math.max(1000 - now + lastWaitCheck, 1));
                    } catch (InterruptedException expected) {
                    }
                  }
                  else {
                    try {
                      sleep(Math.max(500 - now + lastWaitCheck, 1));
                    } catch (InterruptedException expected) {
                    }                    
                  }
                }
                else count1MBPS=0;
                lastWaitCheck = now;        // Set new time measure
              }

              // Check for reconfig
              // keep track of the minimum latency to see if latency is growing
              if (now - ms.getNextExpectedTimeInMillis() < minLatency) {
                minLatency = now - ms.getNextExpectedTimeInMillis();
              }
              if (now - lastConfig > 120000) {
                lastConfig = now;
                if (streamfile != null) {
                  try {
                    int len;
                    try (RawDisk rw = new RawDisk(streamfile, "r")) {
                      if (rw.length() > bc.length) {
                        bc = new byte[(int) rw.length() * 2];
                      }
                      rw.readBlock(bc, 0, (int) rw.length());
                      len = (int) rw.length();
                    }
                    Util.clear(chkConfig);
                    for (int i = 0; i < len; i++) {
                      chkConfig.append((char) bc[i]);
                    }
                    if (!Util.stringBuilderEqual(chkConfig, streamfileContents)) {
                      prta(Util.clear(tmpsb).append(tag).append(" *** config has changed l1=").
                              append(chkConfig.length()).append(" l2=").
                              append(streamfileContents.length()).append(" terminate"));
                      terminate();
                    } else if (dbg || count % 10 == 0) {
                      prta(Util.clear(tmpsb).append(tag).append(" config file unchanged l=").append(len));
                    }
                  } catch (IOException e) {
                    streamfileContents = "";
                    prta(Util.clear(tmpsb).append(" **** could not read ").append(streamfile).append(" e=").append(e));
                  }
                }
              }

              // Status message
              if (now - lastStatus > 600000) {
                prta(Util.clear(tmpsb).append(tag).append("SLC: status #pack=").append(npackets).
                        append(" ").append(npackets * 4160 / 600000).append(" kbps ").append(" minLat=").append(minLatency / 1000).
                        append(" ").append(streamfile).append(" ").append(now - lastConfig));
                lastStatus = now;
                lastNpackets = npackets;
                npackets = 0;
                if (minLatency > 600000 && npackets > 100) {  // Are we latent 600 seconds and not just starting up
                  prta(Util.clear(tmpsb).append(tag).append("SLC: latency over 10 minutes lat=").append(minLatency));
                  // If this latency is building then we might do something
                  if ((minLatency - lastMinLatency) > 300000) {
                    if (minLatencyCount++ % 4 == 3) {
                      minLatencyDisconnect = true; // every 30 minutes cause a reconnect
                    }
                    prta(Util.clear(tmpsb).append(tag).append("SLC: **** latency is growing! minLat=").
                            append(minLatency).append(" lastLat=").append(lastMinLatency).
                            append(" minLatCounter=").append(minLatencyCount));
                  }
                }
                lastMinLatency = minLatency;
                minLatency = Long.MAX_VALUE;
              }
              if (terminate) {
                break;
              }

            } catch (SeedLinkException sle) {
              prta(Util.clear(tmpsb).append(CLASS_NAME + ": ").append(sle));
            }

            count++;
          }  // end if collect() != null
          prta(Util.clear(tmpsb).append("SLC: collect() loop exited terminated=").
                  append(terminate).append(" eto.interrupt=").append(eto.hasSentInterrupt()));
          if (eto.hasSentInterrupt() || minLatencyDisconnect) {
            minLatencyDisconnect = false;
            slconn.disconnect();
            eto.resetInterruptSent();     // Resarm the interrupt sent
            //boolean connected=false;
            prta(Util.clear(tmpsb).append(tag).append("SLC: ETO or minLatency has gone off, try to make new connection").
                    append(eto.hasSentInterrupt()).append(" minlat=").append(minLatencyDisconnect));
            while (true) {
              try {
                eto.resetTimeout();
                slconn.connect();
                break;
              } catch (SeedLinkException e) {
                prta(Util.clear(tmpsb).append(tag).append("SLC: SeedLinkException TO connecting e=").append(e.getMessage()));
                if (!noevent) {
                  SendEvent.edgeSMEEvent("SLNoConn", tag + " Cannot connect2 " + (slconn == null ? "null" : slconn.getSLAddress()), this);
                }
                if (terminate) {
                  break;
                }
              } catch (IOException e) {
                prta(Util.clear(tmpsb).append(tag).append(" IOError trying to reconnect to server").append(e));
                e.printStackTrace(getPrintStream());
                if (terminate) {
                  break;
                }
              }
              try {
                sleep(10000);
              } catch (InterruptedException e) {
              }
            }
            if (eto.hasSentInterrupt() || minLatencyDisconnect) {
              terminate = false;      // override an ETO terminate, just reconnect
            }
          }
        }
        prta(Util.clear(tmpsb).append(tag).append("SLC:Leaving main loop, must be terminated.  Link must have give up").append(terminate));
      } catch (SeedLinkException e) {
        prt(tag + "SLC: SeedLinkException over main loop.  Exit. e=" + e.getMessage());
        e.printStackTrace(getPrintStream());
        try {
          sleep(30000);
        } catch (InterruptedException e2) {
        } // If there are no stations or something, do not try too often.
      } catch (IllegalSeednameException e) {
        prt(tag + "SLC: IllegalSeednameException e= " + e.getMessage());
        e.printStackTrace(getPrintStream());
      }
      /*catch(EdgeFileCannotAllocateException e) {
        prta("SLC: EdgeFileCannotAllocateException e="+e.getMessage());
      }
      catch(EdgeFileReadOnlyException e) {
        prta("SLC: EdgeFileReadOnlyException e="+e.getMessage());
      }
      catch(IOException e) {
        prta("SLC: IOException e="+e.getMessage());
      }*/
    } catch (RuntimeException e) {
      prta(tag + "SLC: RuntimeException in " + this.getClass().getName() + " e=" + e.getMessage());
      if (getPrintStream() != null) {
        e.printStackTrace(getPrintStream());
      } else {
        e.printStackTrace();
      }
      if (e.getMessage() != null) {
        if (e.getMessage().contains("OutOfMemory")) {
          SendEvent.doOutOfMemory(tag, this);
          throw e;
        }
      }
      terminate();
    }
    prta(tag + "SLC: run loop exiting " + shutdown.isAlive());
    running = false;
    // Close the SeedLinkConnection
    eto.shutdown();
    if (slconn.isConnected()) {
      slconn.close();
    }// This saves the state file too
    if (csend != null) {
      csend.close();
    }
    try {
      if (!EdgeMom.isShuttingDown()) {
        Runtime.getRuntime().removeShutdownHook(shutdown);
      }
    } catch (IllegalStateException e) {
    }
    shutdown = null;
    prta(tag + "SLC: run loop exited.");
    terminate = false;

  }
  StringBuilder sbc = new StringBuilder(1000);

  private String parseConfig(String s) {
    Util.clear(sbc);
    BufferedReader in = new BufferedReader(new StringReader(s));
    String line;
    String name;
    String net;
    try {
      int nstats = 0;
      while ((line = in.readLine()) != null) {
        if (line.contains("<station")) {
          String[] parts = line.split("\\s");
          name = "";
          net = "";
          for (int i = 1; i < 4; i++) {
            if (parts[i].substring(0, 4).equalsIgnoreCase("name")) {
              name = parts[i].substring(parts[i].indexOf("=") + 1);
              name = name.replaceAll("\\\"", "");
            } else if (parts[i].substring(0, 7).equalsIgnoreCase("network")) {
              net = parts[i].substring(parts[i].indexOf("=") + 1);
              net = net.replaceAll("\\\"", "").trim();
            }
          }

          if (!name.equals("") && !net.equals("")) {
            // If the selfConfig includes exclusions, do not put those in the file
            if (selfConfigExcludes != null) {
              String ns = (net + "  ").substring(0, 2) + (name + "     ").substring(0, 5);
              if (ns.matches(selfConfigExcludes)) {
                prta(Util.clear(tmpsb).append("Data from ").append(ns).append(" has been excluded by ").append(selfConfigExcludes));
                continue;
              }
            }

            sbc.append(net).append(" ").append(name).append("\n");
            nstats++;
          }
        } else {
          sbc.append("#").append(line).append("\n");
        }
      }
      prta(Util.clear(tmpsb).append(tag).append(" Station INFO parsed to ").append(nstats).append(" stations"));
      in.close();
    } catch (IOException e) {
      prta("IOException parsing " + e);
    }
    return sbc.toString();
  }

  /**
   *
   * Prints the usage message for this class.
   *
   * @param concise Print only a short description.
   */
  public void printUsage(boolean concise) {
    prta(Util.clear(tmpsb).append("\nUsage: java [-cp classpath] " + PACKAGE + " [options] <[host]:port>\n"));
    if (concise) {
      prta("Use '-h' for detailed help");
      return;
    }
    prta(" ## General program options ##\n"
            + " -V             report program version\n"
            + " -h             show this usage message\n"
            + " -v             be more verbose, multiple flags can be used\n"
            + " -p             print details of data packets\n\n"
            + " -nd delay      network re-connect delay (seconds), default 30\n"
            + " -nt timeout    network timeout (seconds), re-establish connection if no\n"
            + "                  data/keepalives are received in this time, default 600\n"
            + " -k interval    send keepalive (heartbeat) packets this often (seconds)\n"
            + " -x statefile   save/restore stream state information to this file\n"
            + " -i infolevel   request this INFO level, write response to std out, and exit \n"
            + "                  infolevel is one of: ID, STATIONS, STREAMS, GAPS, CONNECTIONS, ALL \n"
            + "\n"
            + " ## Data stream selection ##\n"
            + " -l listfile    read a stream list from this file for multi-station mode\n"
            + " -s selectors   selectors for uni-station or default for multi-station\n"
            + " -S streams     select streams for multi-station (requires SeedLink >= 2.5)\n"
            + "   'streams' = 'stream1[:selectors1],stream2[:selectors2],...'\n"
            + "        'stream' is in NET_STA format, for example:\n"
            + "        -S \"IU_KONO:BHE BHN,GE_WLF,MN_AQU:HH?.D\"\n"
            + "\n"
            + " <[host]:port>  Address of the SeedLink server in host:port format\n"
            + "                  if host is omitted (i.e. ':18000'), localhost is assumed\n\n");

  }

  /**
   * This internal class gets registered with the Runtime shutdown system and is
   * invoked when the system is shutting down. This must cause the thread to
   * exit.
   */
  class ShutdownSeedLinkClient extends Thread {

    /**
     * Default constructor does nothing. The shutdown hook starts the run()
     * thread.
     */
    public ShutdownSeedLinkClient() {
      gov.usgs.anss.util.Util.prta("new ThreadShutdown " + getName() + " " + getClass().getSimpleName());
    }

    // This is called by the RunTime.shutdown at any termination 
    @Override
    public void run() {
      prta(tag + "SLC: terminate Shutdown() started..." + slconn.isConnected());
      terminate();          // Send terminate to main thread and cause interrupt
      int loop = 0;
      while (running && loop < 60) {
        // Do stuff to aid the shutdown
        try {
          sleep(1000);
        } catch (InterruptedException e) {
        }
        loop++;
        if (loop == 20) {
          prta(tag + "SLC: terminate Shutdown() Forcing SeedLink software close after 20 seconds.");
          slconn.close();
        }     // Call the close routine and force an exit
      }
      prta(tag + "SLC: terminate () of SeedLinkClient is complete. loop=" + loop + " " + slconn.isConnected());
    }
  }

  /**
   *
   * Main method.
   *
   * @param args
   */
  /*public static void main(String[] args) {
		
		SeedLinkClient SeedLinkClient = null;
		
		try {
			SeedLinkClient = new SeedLinkClient();
			int rval = SeedLinkClient.parseCmdLineArgs(args);
			if (rval != 0)
				Util.exit(rval);
			SeedLinkClient.init();
			SeedLinkClient.run();
		} catch (SeedLinkException sle) {
			if (SeedLinkClient != null)
				SeedLinkClient.sllog.log(true, 0, sle.getMessage());
			else {
				prta("ERROR: "+ sle.getMessage());
				sle.printStackTrace();
			}
		} catch (Exception e) {
			prta("ERROR: "+ e.getMessage());
			e.printStackTrace();
		}
	}*/
  /**
   *
   * @param args
   */
  public static void main(String[] args) {
    Util.setModeGMT();
    Util.init("edge.prop");
    long val = Long.MIN_VALUE;
    int remain = (int) (val % 86400000L);
    Util.prta("val="+val+" remain="+remain);
    SLLog sllog = null;
    SeedLinkClient slc2 = new SeedLinkClient("-noevent -S GS_ADOK/??? -k 30 -nt 60 -x gsadok.state -etna2 166.140.190.154/18000","SLCETNA");
    Util.sleep(100000);
    SeedLinkClient slc = new SeedLinkClient("-S BR_BOAV/??? -k 30 -x slc.state seisrequest.iag.usp.br/18000","SLCTEST");
    
    SeedLinkConnection slconn = new SeedLinkConnection(sllog);
    try {
      slconn.setSLAddress("geofon.gfz-potsdam.de:18000");
      String infoLevel = "STATIONS";
      slconn.requestInfo(infoLevel);
      SLPacket packet;
      while ((packet = slconn.collect(null)) != null) {
        switch (packet.getType()) {
          case SLPacket.TYPE_SLINF:
            break;
          case SLPacket.TYPE_SLINFT: {
            String s = slconn.getInfoString();
            System.err.println(s);
            break;
          }
          default: {
            System.err.println("Unexpected type=" + packet.getType());
            String s = slconn.getInfoString();
            System.err.println(s);
            break;
          }
        }
      }
    } catch (SeedLinkException e) {
      System.err.println("Got seedlink err=" + e);
      e.printStackTrace();

    }
  }

}
