
/*
 *  This software is in the public domain because it contains materials 
 *  that originally came from the United States Geological Survey, 
 *  an agency of the United States Department of Interior. For more 
 *  information, see the official USGS copyright policy at 
 *  http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.cwbquery;

import gov.usgs.alarm.SendEvent;
import gov.usgs.anss.edge.EdgeProperties;
import gov.usgs.anss.edge.IndexFile;
import gov.usgs.anss.edgemom.EdgeMom;
import gov.usgs.anss.edgethread.EdgeThread;
import gov.usgs.anss.seed.MiniSeed;
import gov.usgs.anss.util.Util;
import gov.usgs.anss.waveserver.WaveServerClient;
import gov.usgs.anss.waveserver.UpdateCreatedFromHoldings;
import gov.usgs.anss.edgemom.EdgeChannelServer;
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.StringTokenizer;
import java.io.FileOutputStream;
import java.io.PrintStream;

/**
 *
 * <p>
 * <h1><u>Overview</u></h1>
 * This is the mom process for running other threads : CWBWaveServer,
 * QueryServer, TrinetServer, Memory Caches (QuerySpanCollection and
 * MiniSeedCollection), and at the USGS certain compute intensive tasks needing
 * access to the in-the-clear data from the QuerySpanCollection. These compute
 * intensive task include the FilterPicker, FK beam former, and the
 * SubspaceDetector. The QuerySpanCollector creates in-the-clear memory
 * structures - that is the data are stored as arrays of simple intergers and
 * not in any compressed or formatted form.
 * <p>
 *
 * QueryMom actually runs the EdgeMom.java class to do the thread startup and
 * control. Any special utilities related to QueryMom might be implemented in
 * this class - i.e. the UpdateCreatedFromHoldings is run with the
 * -updatecreated to this process and this performs its work and exits without
 * actually starting a QueryMom itself.
 * <p>
 *
 * <h1><u>Setup file:</u></h1>
 * QueryMoms are configured via a querymom.setup file in the home directory.
 * Each controlled thread appears on its own line starting with a short 'tag',
 * the class of the thread to run, the arguments to that thread, and optionally
 * an output log file. In the sample querymom.setup all of the tags are short
 * uppercase strings. There are a limited number of threads and options which
 * can be started so there is a model querymom.setup in the EdgeConfig GUI
 * SideFiles querymom.setup.example (see below). The installation should create
 * a side file named queryom.setup.$NODE.$ACCT (e.g. queryom.setup.gacq1.vdl)
 * and edit this file for the parameters on this station.
 * <p>
 * <h1><u>Basic rules</u></h1>
 *
 * This querymom.setup file is replicated below. Here are a few basic rules :
 * <p>
 * 1) The " Echn" ( EdgeChannelServer), " Mom" (EdgeMom) and "QS" (QueryServer)
 * tags must always be enabled.
 * <p>
 * 2) The "CWBWS" (CWBWaveServer) provides earthworm and Winston wave server
 * access, so must be enabled for these services.
 * <p>
 * 3) If there is going to be memory buffering of data the the "DLQS"
 * (DataLinkToQueryServer) must be enabled. For this to work there must be
 * EdgeMoms with an OutputInfrastructure 'sendto' using RingServerSeedLink
 * threads pointed at this service to populate it with data.
 * <p>
 * 4) Either "QSC" (QuerySpanCollection) and/or "MSC" (MiniSeedCollection) can
 * be enabled to make memory buffering. The QSC is used to populate in-the-clear
 * data for either earthworm/Winston cached in memory. The MSC is used to
 * populate the memory cache with MiniSEED data used by the TrinetServer.
 * <p>
 * 5) The TrinetServer requires that a "CWBWS" be running to populate the
 * metadata structures.
 * <p>
 * 6) Both/either CWBWS or TWS will run fine without memory buffering - all data
 * then comes from disk.
 * <p>
 *
 * <h1><u>Load switch</u></h1>
 * Use of the -load switch - The use of the -load in both QSC and MSC is
 * optional. This switch causes the memory buffers to be populated from disk at
 * startup. This can be a CPU intensive process. The -load argument is the
 * number of threads to allow to do this population of memory. The more threads
 * the faster it will populate and the more CPU and other resources will be
 * consumed. If there are lot of cores on the system, then more threads can be
 * allowed but it is really a tuning exercise to determine the number of threads
 * to use. For a 3000-channel system of BH and HH data, a -load 2 kept 8 cores
 * about 40-80 % busy and took 20 minutes to load 3 hours of data in memory.
 * Your milage may vary, so beware setting -load too high.
 * <p>
 * <h1><u>CWBWaveServer Thread - populating the menu</u></h1>
 * <u>Preferred method</u> - The preferred way to populate the menu is to NOT
 * use -holdings. In this mode the start time from each channel is taken from
 * the channel table's 'created' field. Normally, the first data from than
 * channel is available shortly after startup. However, if data are being loaded
 * in the past, the ;created' value will not reflect the earliest data. The
 * QueryMom.jar can be run with the switch "-updatecreated" and this will
 * connect to the holdings database, look for the earliest data from each
 * channel, and set the created date to this earliest time. This should be done
 * periodically on any system which is getting data loaded from the past in
 * order to keep this field up-to-date. The latest time in the MENU will
 * normally come from the UdpChannel server monitoring real time data
 * acquisition (this is the same service as ChannelDisplay uses to get
 * information). For this to work, the edge.prop StatusServer property must
 * point to the server running UdpChannel. If this is all setup, the ending time
 * in MENUs will be very close to the time of the last data received. If a
 * UdpChannel is not available and 'lastdata' field of the channel table is
 * within the last day, the latest time will be set to the current time. If the
 * 'lastdata' field is older than one day, the latest time in the MENU will be
 * the 'lastdata' time. The 'lastdata' field is normally up to 6 hours out of
 * date which is the reason for this algorithm. Note that the beginning time of
 * the menu will be shortened by the -daysback switch. So, on a node only
 * keeping 10 days worth of data, setting '-daysback 10' makes all menus report
 * a beginning time 10 days in the past (assuming this is after the channel
 * table's 'created' field).
 * <p>
 * <u>Holdings_method</u> - This method is not recommended unless absolutely
 * necessary. This method gets the menu starting time for each channel by
 * scanning the holdings, holdingshist, and holdingshist2 tables for the
 * earliest holdings. This can be a very time consuming as the holdings tables
 * are not bounded. The latest time in the menus comes from a UdpChannel if the
 * StatusServer property in edge.prop is setup and a connection to the
 * UdpChannel is possible. If a UdpChannel is not available and channel tables's
 * 'lastdata' field is within the last day, the latest time will be set to the
 * current time. The value of the field 'lastdata' is older than one day, the
 * latest data value will be set to the value of the 'lastdata' field. Last data
 * is normally up to 6 hours out of date which is the reason for this algorithm.
 * <p>
 * <h1><u>Winston Metadata emulation</u></h1>
 * The Winston wave server provides metadata such as coordinates and instruments
 * as part of its services. This information comes from the NEIC MetaDataServer.
 * By default CWBWaveServer uses the NEIC public MDS to populate these values.
 * This is done after other data intensive processes related to gathering MENU
 * beginning and end times is completed, so generally at least a minute or five
 * after startup. The setting of the metadata can take a few minutes depending
 * on the number of channels which have to be loaded from the MDS. The Winston
 * commands requiring metadata will return default values (like zero
 * coordinates) soon after startup, but the real metadata might not be populated
 * for several minutes after startup. Because of this, there can be problems
 * with starting a client like Swarm which is building a map and hence needs the
 * coordinates populated, if it is started soon after a QueryMom start up.
 * Simply restarting Swarm after the warmup period will solve this problem.
 *
 * <p>
 * <h1><u>Example Setup file </u></h1>
 * Below is the example querymom.setup file. Its latest version is kept in the
 * databases as querymom.setup.example. There are enough comments about the
 * switches to allow a user familiar with QueryMom to setup a new one without
 * referring to the javadocs for more details. For more information see the
 * javadocs for each individual thread started from this configuration file.
 * <PRE>
 * # EdgeMom thread controls staring and logging from all of the other thread (mandatory)
 * Mom:EdgeMom:-empty
 * # The EdgeChannelServer is used for configuration parameters from channel table
 * Echn:EdgeChannelServer:-empty >>echnqm
 * # FilterPickerManager makes the configuration file for FilterPickers from filterpicker table of args and channel table
 * #FP:gov.usgs.anss.picker.FilterPickerManager:-dbpick localhost/3306;status;mysql;status -qsize 100 -host 192.168.18.80 -port 16099 -empty >>fpm
 * # EdgeQueryServer : [-p port][-rw][-allowrestricted][-mdsip ip][-mdsport port][-maxthread nn][-dbg][-dbgall]
 * #                       2061                        137.227.224.97  2052        200
 * QS:gov.usgs.cwbquery.EdgeQueryServer:-p 2061 -mdsip 137.227.224.97 -mdsport 2052 -allowrestricted  >>queryserver
 * # Start a CWBWaveServer:
 * # -noudpchan            Do not use a SocketServerReader to get status from a UdpChannel for the latest data
 * # -holdings             NOT PREFERRED.  Get MENULIST information by querying the holdings databases instead of channels
 * #                        (StatusDaBServer must be defined or -holdingsmysql must be used),
 * #                       but do recent end from channels or if StatusServer is set, from UdpChannel information.
 * #                       If this is not selected, early times are taken from channel creation time in channel table, and
 * #                       ending times from the UdpChannel information.  Not using holdings is recommended.
 * # -holdingsmysql dburl  Use this server for holdings instead of the StatusDBServer property or -dbconnedge switch
 * # -dbedge        dburl  Use this server to look at channel table in edge for last data or if no holdings channel creation (def=property DBServer)
 * # -dbstatus      dburl  Use this server to look at holdings in the status database (def=propery StatusDBServer)
 * # -dbmeta        dburl  Use this server to look at metadata (def=property MetadataDBServer)
 * # -maxthread     nn     Have a maximum of nn threads available to run requests simultaneously (def=1000)
 * # -minthread     nn     Have a minimum of nn threads available to run requests simultaneously at startup (def=20)
 * # -p             pppp   Run this service on port pppp (def=2060)
 * # -instance n       If this is not the only instance on a node, set this to a positive instance number (1-10), or a port to use for monitoring
 * # -cwbhost       ip.adr Run requests against this CWB (def=gcwb) instead of this instance
 * # -allowrestricted      Allow requests for restricted channels
 * # -nofilter             Do not filter down the list of channels to just reasonable seismic channels [BSHLMECD][HND][ZNE12F]
 * # -daysback      nnn    Allow the MENU list to go nnn days into the past when looking for a channel that stop coming in.
 * # -mdsip         ip.adr Use this IP address for MetaDataServer requests instead for 137.227.230.1
 * # -mdsport       port   User this port for the MDS instead for 2052
 * # -mdsto         millis Milliseconds to timeout connections to the MDS (def is 5000)
 * # -quiet                Really cut down the amount of output.
 * # -cdelim       char    Use this character instead of space to delimit seednames names for METADATA: CHANNELS command ($ is most common)
 * # -dbg                  Run with more verbose output
 * # -nodb                 Run in "no database" mode - use if no databases are to be used
 * # -dbgmsp               Run with verbose output on MiniSeedPool
 * -dbgmscr              Run with debug of MiniSeed creates (creates a runtime stack dump every 500 creates after 15 mintues and 100000 creates)
 * # -subnet     path/to/config This is a top level configuration directory for subnetogram (VHP).  It would normally contain on directory per observator with .config files for each subnet
 * ##CWBWaveServer 1600 "" -instance 0 -mdsip cwbpub.cr.usgs.gov -p 2060 -cwbhost cwbpub.cr.usgs.gov -daysback 10000 -maxthreads 50  >>LOG/CWBWaveServer.log 2>&1
 * #CWBWS:gov.usgs.anss.waveserver.CWBWaveServer:-p 2060 -mdsip cwbpub.cr.usgs.gov -mdsport 2052 -daysback 10000 -maxthreads 50 >> cwbws
 * # Set up the receiver of data from one or more EdgeMom based outputer (RingServerSeedLink)
 * # -iplist filename      If present, the list of IP addresses (one per line) is used to validate incoming connections
 * #                       If not present, accept all connections
 * # [-p port][-bind ip][-iplist filename][-dbg]
 * #  16099     NONE        NONE
 * ##DLQS:gov.usgs.cwbquery.DataLinkToQueryServer:-empty >>dlqs
 * #DLQS:gov.usgs.cwbquery.DataLinkToQueryServer:-empty >>dlqs
 * # -h  host              Normally do not specify so host is null and internal access to files is used
 * # -d  secs              Depth of in-the-clear data in seconds
 * # -pre pct              % of memory to leave in memory on before the current time (def=75)
 * # -bands band-list      two character band codes separated by dashes to include in the memory cache (def=BH-LH-SH-EH-HH-)
 * # -load  Nthread        Set the number of threads to use to populate memory cache from disk (def=2), if 0, no population is done.
 * ##QSC:QuerySpanCollection:[-h cwbip][-p cwbport][-d secs][-pre pct][-bands]s
 * ##                     Internal     2061     3600       75      BH-LH-SH-EH-HH-
 * QSC:gov.usgs.cwbquery.QuerySpanCollection:-d 3600 -bands BH-LH-SH-EH-HH-  >>qsc
 * ## Memory structure for TrinetServer emulator
 * # -h  host              Normally do not specify so host is null and internal access to files is used
 * # -d  secs              Depth of in-the-clear data in seconds
 * # -pre pct              % of memory to leave in memory on before the current time (def=75)
 * # -bands band-list      two character band codes separated by dashes to include in the memory cache (def=BH-LH-SH-EH-HH-)
 * # -load  Nthread        Set the number of threads to use to populate memory cache from disk (def=2), if 0, no population is done.
 * # -dbgchan chanRD       A NSCL regular expression which will generate debug output
 * ##MSC:gov.usgs.cwbquery.MiniSeedCollection:[-dbg][-d nsec][-load n][-dbgchan chanRE][-bands BN-BN-] >>msc
 * ##                                                3600           5                      BH-LH-SH-EH-HH-
 * #MSC:gov.usgs.cwbquery.MiniSeedCollection:-d 3600 -load 5  -bands BH-EL-LH-HH-EH-HN-BN- >>msc
 * #
 * # Setup the trinet part of the server
 * # -p      Port         The server port (def=2063)
 * # -allowrestricted     If present, restricted channels can be served by this server
 * # -maxthreads  NN      Maximum number of threads to permit simultaneously (def 1000)
 * # -minthreads  NN      The number of TrinetHandler threads to start initially (def 20)
 * # -quiet               If present, reduce the verbosity of log output
 * # -dbg                 Increase logging for debugging purposes
 * # -dbgmsp              If present, set debugging output for the MiniSeedPool
 * #TWS:gov.usgs.anss.waveserver.TrinetServer:-p 9101 -maxthread 50 >>tws
 * </pre>
 * <p>
 * <h1><u>Command Line Considerations</u></h1>
 * <u>Setting the heap size</u> - When using memory caching the total heap size
 * needs to be set to some value bigger than the actual memory used when
 * QueryMom reaches equilibrium after several hours. The memory heap can be
 * adjusted by looking in the log file querymom.logN for the string "maxMem".
 * These log lines are generated every 10 minutes or so. After the system is
 * static the total heap size can be adjust to 20% bigger than the values of
 * "totMem". The heap space above "totMem" has never been allocated by the
 * program so setting the heap at this level insures some room for growth. On
 * linux systems you need to have more physical memory than can be used, so
 * larger memories are needed on QueryMom systems with memory caching. If there
 * is not enough memory, the system will start paging and performance will be
 * very bad.
 * <p>
 * <u>Garbage Collection when using memory cache </u>- The java garbage
 * collector can do some odd "stop-the-world" collections if the QueryMom is
 * started with default values. There are specialized scripts for starting
 * QueryMom which set various memory tuning parameters to smooth out these.
 * Basically it has been found that forcing the size of the Eden area
 * (-XX:NewSize=200m -XX:MaxNewSize=300m) keeps things so shorter garbage
 * collections run more frequently. The -XX:MaxGCPauseMillis=100 has also been
 * shown to help with this process. Generally, the frequent garbage collections
 * will run in under 100 millis so the responsiveness is good. However, until
 * the memory areas are fully populated and QueryMom has been running for
 * several hours, there will be stop-the-world (sometime called full) garbage
 * collections that will last several seconds. Since some objects need to be
 * moved from Eden to Old as the program runs, some stop-the-world events seem
 * to be inevitable. However, using these parameters has made it so these happen
 * very rarely after the program has been running several hours so users
 * generally to not observe them.
 * <p>
 *
 * <h1><u> Non standard execution</u></h1>
 * All other configuration for a QueryMom process in a non-standard manner. The
 * switches below are <b>NEVER</b> used to run QueryMom in its normal manner.
 * They allow other special functions to be run such as tests on certain classes
 * and to allow the WaveServerClient to be run from the normal .jar file.
 * <p>
 * <pre>
 * switch   Args    Description
 * -rsc            Run the RateSpanCalculator.main() and exits when its done. More info in that clasee
 * -wsc    many    Run the WaverServerClient from the QueryMom - all arguments are passed to WaveServerClient - see that class for details
 * -cwbws  many    Ditto - another way to invoke the WaveServerClient
 * -updatecreated  This runs the UpdateCreatedFromHoldings class which scans holdings and updates channel.created to the earliest appearance of a channel
 * </pre>
 *
 * @author U.S. Geological Survey <ketchum at usgs.gov>
 */
public class QueryMom {

  public static String version = "1.09 May 16, 2015";

  public static void main(String[] args) {
    Util.setProcess("QueryMom");
    Util.setModeGMT();
    Util.setNoInteractive(true);
    for (String arg : args) {
      switch (arg) {
        case "-rsc":
          RateSpanCalculator.main(args);
          System.exit(0);
        case "-wsc":
          WaveServerClient.main(args);
          System.exit(0);
        case "-cwbws":
          WaveServerClient.main(args);
          System.exit(0);
        case "-updatecreated":
          EdgeThread.setMainLogname("updatecreated");
          UpdateCreatedFromHoldings.main(args);
          System.exit(0);
        case "-?":
        case "-help":
          Util.prta("Normally CWBQuery takes no arguments and is configured via querymom.setup");
          Util.prta("switch     Description");
          Util.prta("-updatecreated [-create] Update the created field in channel to beginning of holdings and optionally create new channels if they exist in holdings but not in channel");
          Util.prta("-cwbws     Run the test WaveServerClient program which can be used to check CWBWaveServer services");
          Util.prta("-wsc       '                              '                     '");
          Util.prta("-rsc       Test the RateSpanCalculater module");
          System.exit(0);
      }
    }
    EdgeThread.setMainLogname("querymom");
    Util.prtProperties();
    IndexFile.init();
    EdgeProperties.init();
    Util.init("querymom.prop");

    int ebqsize = 0;
    String filename = "querymom.setup";
    boolean useConsole = false;
    boolean dbg = true;

    // Switch the args to be the ones on the "EdgeMom" thread in the setup file.
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
      } catch (FileNotFoundException expected) {
      } catch (IOException expected) {
      }
    }

    // This arguments are from the EdgeMom configuration line in the querymom.setup file
    for (int i = 0; i < args.length; i++) {
      if (args[i].equals("-f")) {
        filename = args[i + 1];
        i++;
      } else if (args[i].equals("-dbg")) {
        dbg = true;
      } else if (args[i].equalsIgnoreCase("-dbgmscr")) {
        MiniSeed.setDebugCreate(true);
      } else if (args[i].equals("-empty")) {
        ebqsize = 0;
      } else if (args[i].equals("-i") || args[i].equals("-instance")) {
        if (!args[i + 1].equals("^") && !args[i + 1].equals("null")) {
          EdgeThread.setInstance(args[i + 1]);
          EdgeMom.setInstance(args[i + 1]);
        } else {
          EdgeThread.setInstance(null);
        }
        i++;
      } else if (args[i].equals("-prop")) {
        EdgeMom.setExtraProperties(args[i+1]);
        i++;
      } else if(args[i].equals("-nochkprop")) {
        EdgeMom.setNoCheckProperties(true);
      }else {
        Util.prt("QueryMom: Unknown argument to QueryMom [" + i + "] is " + args[i]);
      }
    }
    PrintStream stateStream = null;
    StringBuilder sbstate = new StringBuilder(10);
    EdgeMom main = null;
    //IndexFileReplicator.setDebug(true);
    while (stateStream == null || main == null) {
      try {
        Util.setNoInteractive(true);
        main = new EdgeMom(filename, ebqsize, useConsole, "QueryMom");
        main.setDebug(dbg);
        stateStream = new PrintStream(new FileOutputStream("LOG/state_" + EdgeThread.getInstance(), true));
        stateStream.println(Util.ascdatetime2(Util.clear(sbstate)).append(" QueryMom startup state=").
                append(EdgeMom.getEdgeMomState()).append(" ").append(EdgeThread.getInstance()));
        try {
          Thread.sleep(600000);
        } catch (InterruptedException expected) {
        }
        String[] args2 = new String[1];
        args2[0] = "-create";
        if (!EdgeChannelServer.isNoDB()) {
          boolean ok = UpdateCreatedFromHoldings.main(args2); // Once after startup has gotten a ways, do a lastdata and created update from Holdings);
          if (!ok) {
            SendEvent.edgeSMEEvent("UpdCreateErr", "Could not run UpdateCreatedFromHoldings ", main);
            EdgeMom.prta("QueryMom: ****** UpdateCreatedFromHoldings failed!");
          }
        }

      } catch (FileNotFoundException | RuntimeException e) {
        EdgeMom.prta("QueryMom: could not open LOG/state_" + EdgeMom.getInstance());
        e.printStackTrace(EdgeThread.getStaticPrintStream());
        if (main == null) {
          Util.exit("QueryMom: could not start edgemom thread!");
        }
      }
    }

    int currstate;
    int nbad = 0;
    int badState = 0;
    int ngood = 0;
    // THis loop is looking for hangs that have occurred in EdgeMom thread processing.  It is trying to determine the state of 
    // the EdgeMom when it is stuck.
    for (;;) {
      try {
        try {
          Thread.sleep(30000);
        } catch (InterruptedException expected) {
        }
        currstate = EdgeMom.getEdgeMomState();
        if (currstate != 20) {   // 20 is the normal idle state
          stateStream.println(Util.ascdatetime2(Util.clear(sbstate)).append(" QueryMom.state=").
                  append(EdgeMom.getEdgeMomState()).append(" nbad=").append(nbad));
          //Util.prta("QueryMom.state="+currstate);
          if (currstate == badState) {
            nbad++;
            if (nbad % 5 == 4) {
              stateStream.println(Util.ascdatetime2(Util.clear(sbstate)).append(" QueryMom.state=").
                      append(currstate).append(" nbad=").append(nbad).append(" we should abort!"));
              SendEvent.edgeEvent("QueryMmStuck", "Instance " + EdgeThread.getInstance() + " QueryMom thread stuck in state=" + currstate, "QueryMom");
            }
          } else {
            nbad = 0;
            badState = currstate;
          }
        } else {    // Its idle, reset everything
          nbad = 0;
          badState = currstate;
          ngood++;
          if (ngood % 120 == 1) {
            stateStream.println(Util.ascdatetime2(Util.clear(sbstate)).append(" ").
                    append(EdgeThread.getInstance()).append(" is o.k."));
          }
        }
        // DEBUG: this 
        if (!main.isAlive()) {
          EdgeMom.prta("QueryMom: EdgeMom Thread not alive!\n" + Util.getThreadsString() + "\nQueryMom exiting");
          Util.exit(1);
        }
      } catch (RuntimeException e) {
        EdgeMom.prta("QueryMom: RuntimeException in QueryMom.main() e=" + e);
        e.printStackTrace();
        if (e.getMessage() != null) {
          if (e.getMessage().contains("OutOfMemory")) {
            SendEvent.sendEvent("Edge", "OutOfMemory", "Out of Memory in QueryMom on " + Util.getNode() + "/" + System.getProperty("user.name"),
                    Util.getNode(), "QueryMom");
            Util.exit("OutOfMemory");
          }
          SendEvent.edgeEvent("RuntimeExcp",
                  "RuntimeXception - exit() in QueryMom on " + Util.getNode() + "/" + System.getProperty("user.name"), "QueryMom");
        }
      }
    }
  }
}
