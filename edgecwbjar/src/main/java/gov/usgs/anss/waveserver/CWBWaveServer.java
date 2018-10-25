/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.waveserver;

import gnu.trove.map.hash.TLongObjectHashMap;
import gnu.trove.iterator.TLongObjectIterator;
import gov.usgs.alarm.SendEvent;
import gov.usgs.anss.db.DBConnectionThread;
import gov.usgs.anss.db.DBAccess;
import gov.usgs.anss.edge.EdgeProperties;
import gov.usgs.anss.edgeoutput.TraceBuf;
import gov.usgs.anss.edgeoutput.TraceBufPool;
import gov.usgs.anss.edgethread.EdgeThread;
import gov.usgs.anss.edgemom.Version;
import gov.usgs.anss.net.StatusSocketReader;
import gov.usgs.anss.cwbqueryclient.EdgeQueryClient;
import gov.usgs.anss.seed.MiniSeedPool;
import gov.usgs.anss.edge.IllegalSeednameException;
import gov.usgs.anss.edge.TimeSeriesBlock;
import gov.usgs.anss.seed.MiniSeed;
import gov.usgs.anss.edge.StaSrvEdge;
import gov.usgs.anss.edgemom.EdgeChannelServer;
import gov.usgs.anss.channelstatus.ChannelStatus;
import gov.usgs.anss.util.Util;
import gov.usgs.anss.util.AnssPorts;
import gov.usgs.anss.util.SeedUtil;
import gov.usgs.cwbquery.EdgeQuery;
import gov.usgs.cwbquery.QuerySpanThread;
import gov.usgs.cwbquery.QuerySpanCollection;
import gov.usgs.cwbquery.EdgeQueryServer;
import gov.usgs.cwbquery.EdgeQueryInternal;
import gov.usgs.cwbquery.QuerySpan;
import gov.usgs.edge.config.Channel;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.GregorianCalendar;
import java.util.concurrent.Semaphore;
//import java.util.zip.DataFormatException;
import java.util.zip.Deflater;

/**
 * This program provides client services for a WaveServer interface to the CWB. It has been extended
 * to also provides Winston Wave Server options and hence does support Swarm.
 * <br>
 * The configuration trade offs on menus are the most complicated. If -holdings is set, the
 * StatusDBServer or the url from -holdingsmysql is used to scan the holdings, holdinghist and
 * holdingshist2 at daysbackmsup to establish the early and late times at daysbackms up. If
 * -holdings is not set, then the daysbackmsing times are taken from the created field of the
 * seedname in the seedname table.
 * <br>
 * For the ending times if the StatusServer is available then a StatusSocketReader will connect to
 * UdpChannel and the ending times will be taken from the last message received for that seedname if
 * it is in the list. If it is not in the list, then it will be the seedname lastdata attribute and
 * will be set to current time if that time is within the last day. If no StatusServer is available,
 * the ending times are based on the lastdata attribute from the seedname table, which is updated
 * lazily (the menu builder will return the current time for any lastdata within the last day).
 *
 * <PRE>
 * switch         Value               Description
 * Common Switches:
 * -p             pppp   Run this service on port pppp (def=2060)
 * -allowrestricted      If true, this server can process restricted seednames
 * -noudpchan            Do not use a SocketServerReader to get status from a UdpChannel for the latest data
 * -daysback      nnn    Allow the MENU list to go nnn days into the past when looking for a seedname (def=unlimited)
 * -maxthread     nn     Have a maximum of nn threads available to run requests simultaneously (def=1000)
 * -minthread     nn     Have a minimum of nn threads available to run requests simultaneously at daysbackmsup (def=20)
 * -mdsip         ip.adr Use this IP address for MetaDataServer requests instead for 137.227.230.1
 * -mdsport       port   User this port for the MDS (def=2052). Set to zero to disable all MDS access (no Winston metadata!)
 * -nofilter             Do not filter down the list of channels to reasonable seismic seednames (def=VLBESMHDC-VHNLSD-ZNE123FGH)
 * -filter   inst-band-comp Change the allowed characters for the instrument, band code and component - do not use with -nofilter!(def=[VLBESMHDC][VHNLSD][ZNE123FGH])
 * -menure     regexp    Limit MENUs to channels matching this regular expression (def=no filter)
 * -quiet                Really cut down the amount of output.
 * -cdelim       char    Use this character instead of space to delimit seednames names for METADATA: CHANNELS command ($ is most common)
 * -lowpri subnet:subnet If a connection is from a IP address containing any of the substring between colons, lower its priority.
 *
 * Debugging Switches :
 * -dbgchan      NSCL    If the seedname matches this name, turn debug on
 * -dbg                  Run with more verbose output
 * -dbgmsp               Run with verbose output on MiniSeedPool
 *
 * Specialized settings :
 * -cwbhost       ip.adr Run requests against this CWB (def=use this querymom to access data)
 * -holdings             NOT PREFERRED : Get MENULIST information by querying the holdings databases instead
 * of using channel table created for start times
 * (StatusDBServer must be defined or -holdingsmysql must be used),
 * but do recent end from seednames or if StatusServer is set, from UdpChannel information.
 * If this is not selected, early times are taken from seedname creation time in seedname table.
 * -holdingsmysql dburl  Use for holdings instead of the StatusDBServer property or -dbconnedge switch
 * -dbedge        dburl  Use to look at channel table in edge for last data or if no holdings seedname creation (def=property DBServer)
 * -dbstatus      dburl  Use this server to look at holdings in the status database (def=property StatusDBServer),
 * used to fine new channels in holdings if -holdings is off
 * -dbmeta        dburl  Use this server to look at metadata (def=property MetaDBServer)
 * -instance n           Overide instance from crontab startup.  This is generally a bad idea.
 * If this is not the only instance on a node, set this to a positive instance number (1-10),
 * or a port to use for monitoring.
 * -mdtable      table   Use this table to look up metadata (no not use with MDS, more for non-dataless/stationXML clients)
 * -queryall             If present, always do queries even if menu says time ranges is out-of-bounds.  Helps with systems loading lots of data.
 * -subnet path/to/config This is a top level configuration directory for subnetogram.
 * It would normally contain on directory per observatory with .config files for each subnet
 * </PRE>
 *
 * @author davidketchum
 */
public final class CWBWaveServer extends EdgeThread {

  private static StatusSocketReader channels;
  private static final TLongObjectHashMap< StationList> stationList = new TLongObjectHashMap<>();
  private static final TLongObjectHashMap<HeliFilterIIR> filters = new TLongObjectHashMap<>();
  private static final TLongObjectHashMap<LastRawData> lastGetRawMS = new TLongObjectHashMap<>();// This tracks last WaveRaw request for each cahnnel
  private static int whoHasChannelList;
  protected static int whoHasStationList;
  protected static int whoHasStats;
  // data structures shared by getRAWWAVE
  //private static ZeroFilledSpan rawspan;
  private static final Integer waveRawMutex = Util.nextMutex();
  private static byte[] waveRawBuf;
  private static ByteBuffer waveRawbb;
  private static BufferedReader mdsin;

  private static int thrcount;
  //private  static final DecimalFormat df3 =new DecimalFormat("000");
  //private static final DecimalFormat dff1 = new DecimalFormat("0.0");
  //private static final DecimalFormat dff3 = new DecimalFormat("0.000");
  //private static final DecimalFormat dff6 = new DecimalFormat("0.000000");
  private static String dbgSeedname = "XXXXXXXXXX";
  private static boolean dbg;
  private static boolean quiet;
  private static boolean noDB;
  private static boolean queryAllCases;
  public static long year2000MS;
  public static double year2000sec;
  private String[] priorityBumps;
  protected static CWBWaveServer thisThread;
  private int monOffset;
  private final ArrayList<CWBWaveServer.CWBWaveServerHandler> handlers;
  private final TraceBufPool traceBufPool;
  private String instCodes = "VLBESMHDC";
  private String bandCodes = "VHNLSDQ";
  private String compCodes = "ZNE123FHG";

  private static final TLongObjectHashMap<CWBWaveServer.Stats> stats = new TLongObjectHashMap<>();

  /**
   * This thread contains the structure for generating the earthworm menu and the Winston wave
   * server instrument and location metadata
   *
   */
  private static MenuBuilder menuThread;
  //private static MonitorServer monitor;
  //private static MemoryChecker memchk ;
  private static byte[] compressedBytes = new byte[1000000];   // writeBytesCompressed use this for compression
  //ChannelStatus cs[];
  private int maxThreads;               // The max number of CWBWaveServerHandlers to allow in handlers
  private int minThreads;               // The initial number of meta data handlers
  private int usedThreads;              // Count the number of threads currently int use 
  private final ShutdownCWBWS shutdown;
  private int port;
  private ServerSocket d;
  private int totmsgs;
  private final ChannelStatus fake = new ChannelStatus("IUANMO BHZ00");     // NOTE: This is synchronized via msgsMutex
  private final CWBWaveServerMonitor mon;
  protected long daysBack = 500;
  private boolean noudpchan;
  private boolean noFilter;
  private int menuBufLength;
  private int poolSize;
  private int bufSize;
  //private char[] menuchar = new char[100000];
  private byte[] menu = new byte[100000];
  private long menuLastUpdate;

  // WWS metadata structures
  private int instrumentLength;
  private long instrumentUpdate;
  private int instrumentListSize;
  //private char[] instrumentchar=new char[10000];
  private byte[] instrument = new byte[10000];
  private int channelLength;
  private long channelUpdate;
  private int channelListSize;
  //private char [] channelchar=new char[10000];
  private byte[] channelbytes = new byte[10000];
  private int getChannelLength, getChannelMetadataLength;
  private final Integer channelBytesMutex = Util.nextMutex();
  private final Integer getChannelBytesMutex = Util.nextMutex();
  private final Integer getChannelMetadataBytesMutex = Util.nextMutex();
  private byte[] getChannelBytes = new byte[10000], getChannelMetadataBytes = new byte[10000];
  //private  char [] getChannelChar= new char[10000], getChannelMetadataChar= new char[10000];
  protected String mdsIP = "137.227.224.97";
  protected int mdsPort = 2052;
  protected String mdTable;
  private boolean doMDSNow = false;
  protected int mdsto = 5000;
  protected static StaSrvEdge stasrv;
  protected String winstonChannelDelimiter = " ";
  protected boolean allowrestricted = false;
  protected SubnetConfig subnetConfig;

  private String host;
  private String menuRE;
  private final StringBuilder tmpsb = new StringBuilder(100);

  /**
   * get the channelList object for the given channel
   *
   * @param chansb A NNSSSSSCCCLL to get.
   * @return The ChannelList object matching or null.
   */
  public static ChannelList getChannelList(StringBuilder chansb) {
    return menuThread.getChannelList(chansb);
  }

  /**
   *
   * @return Get the entire list of ChannelList objects
   */
  public static Collection<ChannelList> getChannelListValues() {
    return menuThread.getChannelListValues();
  }

  public TLongObjectHashMap<CWBWaveServer.Stats> getStats() {
    return stats;
  }

  @Override
  public String toString() {
    return tag + "[" + this.getId() + "] port=" + port + " thrs=" + handlers.size();
  }

  public String getStates() {
    return tag + " [" + this.getId() + "] whoHasCh=" + whoHasChannelList + " whoHasStat=" + whoHasStationList
            + " whoHasStats=" + whoHasStats + " " + menuThread.toStringBuilder(null);
  }

  public String getHost() {
    return host;
  }

  public static String y2kToString(double t) {
    long t2 = (long) (t * 1000. + year2000MS);
    return Util.ascdate(t2) + " " + Util.asctime2(t2);
  }

  @Override
  public void terminate() {
    terminate = true;
    interrupt();
  }   // cause the termination to begin

  public int getNumberOfMessages() {
    return totmsgs;
  }

  /**
   * return the monitor string for ICINGA
   *
   * @return A String representing the monitor key value pairs for this EdgeThread
   */
  @Override
  public StringBuilder getMonitorString() {
    Util.clear(monitorsb).append(menuThread.getSummary()).append(mon.getMonitorString());
    if (QuerySpanCollection.getQuerySpanCollection() != null) {
      monitorsb.append(QuerySpanCollection.getQuerySpanCollection().getSummary());
    }
    return monitorsb;
  }

  @Override
  public StringBuilder getStatusString() {
    Util.clear(statussb);
    statussb.append("Threads : used=").append(usedThreads).append(" size=").
            append(handlers.size()).append(" min=").append(minThreads).append(" max=").append(maxThreads)
            .append(" waveraw=").append(waveRawBuf.length).append(" compbuf=").append(compressedBytes.length)
            .append(" menu=").append(menu.length * 3)
            .append(" instr=").append(instrument.length * 3).append(" chanlist=").append(channelbytes.length * 3)
            .append(" chan=").append(getChannelBytes.length * 3).append(" metabuf=").append(getChannelMetadataBytes.length * 3)
            .append(" tbp=").append(traceBufPool.toString()).append("\n");
    TLongObjectIterator<HeliFilterIIR> itr = filters.iterator();
    long helitot = 0;
    while (itr.hasNext()) {
      itr.advance();
      HeliFilterIIR f = itr.value();
      statussb.append("      ").append(f.getStatusString().append("\n"));
      helitot += f.getMemoryUsage();
    }
    return statussb.append("      heliTotal=").append(Util.df22(helitot / 1000000.)).append("mB");
  }

  @Override
  public StringBuilder getConsoleOutput() {
    return consolesb;
  }

  /**
   * * write out a buffer in compressed for with a requestID #bytes{newline} header synchronized
   * because all callers use same compressedBytes buffer.
   *
   * @param b The buffer of data to write out
   * @param off The offset of first byte to write out in compressed form
   * @param end The last byte of b to put into compressed form
   * @param out The OutputStrem to get the data
   * @return The number of bytes of data sent
   * @throws java.io.IOException
   *
   */
  public static int writeBytes(byte[] b, int off, int end, OutputStream out) throws IOException {
    int offset = off;
    int written = 0;
    int nb;
    while (offset < end) {
      nb = end - offset;
      if (nb > 10000) {
        nb = 10000;
      }
      out.write(b, offset, nb);
      offset += nb;
      written += nb;
    }
    return written;
  }
  private static final StringBuilder tmpwbc = new StringBuilder(40);  // for writeCompressedBytes()
  private static final byte[] tmpwbcb = new byte[40];

  /**
   * * write out a buffer in compressed for with a requestID #bytes followed by newline header
   * synchronized because all callers use same compressedBytes buffer.
   *
   * @param rid The requestID string
   * @param b The buffer of data to write out
   * @param off The offset of first byte to write out in compressed form
   * @param end The last byte of b to put into compressed form
   * @param out The OutputStrem to get the data
   * @return The number of bytes of compressed data
   * @throws java.io.IOException
   *
   */
  public static int writeBytesCompressed(StringBuilder rid, byte[] b, int off, int end, OutputStream out) throws IOException {
    int nret = 0;
    synchronized (tmpwbc) {
      if (compressedBytes.length < end) {
        compressedBytes = new byte[end * 2];
      }
      while (nret <= 0) {
        try {
          nret = Compressor.compress(b, Deflater.BEST_SPEED, off, end, compressedBytes);
        } catch (RuntimeException e) {
          Util.prt("**** Runtime on decompress (outbytes for compression too small?) continue e=" + e);
          e.printStackTrace();
          compressedBytes = new byte[compressedBytes.length * 2]; // double the size of compressedBytes until it fits
        }
      }
      if (dbg) {
        thisThread.prta("Compression in=" + (end - off) + " #comp=" + nret + " rat=" + Util.df21(((double) (end - off)) / nret));
      }
      Util.clear(tmpwbc).append(rid).append(" ").append(nret).append("\n");
      Util.stringBuilderToBuf(tmpwbc, tmpwbcb);
      out.write(tmpwbcb, 0, tmpwbc.length());
      //out.write((rid+" "+nret+"\n").getBytes());
      out.write(compressedBytes, 0, nret);
    }
    return nret;
  }

  /**
   * Creates a new instance of CWBWaveServer
   *
   * @param argline The command lin string
   * @param tg The tag string for loggin
   */
  public CWBWaveServer(String argline, String tg) {
    super(argline, tg);
    Util.setModeGMT();
    waveRawBuf = new byte[100000];
    waveRawbb = ByteBuffer.wrap(waveRawBuf);

    dbg = false;
    port = 2060;
    GregorianCalendar g = new GregorianCalendar(2000, 0, 1);
    g.setTimeInMillis(g.getTimeInMillis() / 86400000L * 86400000L + 86400000L / 2);
    year2000MS = g.getTimeInMillis();
    year2000sec = year2000MS / 1000.;
    prta("year2000sec=" + Util.df21(year2000sec) + " ms=" + year2000MS);
    maxThreads = 1000;
    host = null;
    minThreads = 20;
    noudpchan = false;
    prta("Argline=" + argline);
    boolean useHoldings = false;
    String holdingsMySQLServer = null;
    noFilter = false;
    poolSize = 1000;
    bufSize = TraceBuf.UDP_SIZ;
    monOffset = 0;
    int dummy = 0;

    String[] args = argline.split("\\s");
    for (int i = 0; i < args.length; i++) {
      prt(i + " arg=" + args[i]);
      if (args[i].equals("-dbg")) {
        dbg = true;
      } else if (args[i].equals("-dbgmsp")) {
        EdgeQueryClient.setDebugMiniSeedPool(true);
      } else if (args[i].equals("-empty")) {
        dummy = 1; // Do nothing, supress NB warnings
      } else if (args[i].equals("-quiet")) {
        quiet = true;
      } else if (args[i].equals("-p")) {
        port = Integer.parseInt(args[i + 1]);
        i++;
      } // Port for the server
      else if (args[i].equals("-maxthreads")) {
        maxThreads = Integer.parseInt(args[i + 1]);
        i++;
      } else if (args[i].equals("-minthreads")) {
        minThreads = Integer.parseInt(args[i + 1]);
        i++;
      } else if (args[i].equals("-instance")) {
        monOffset = Integer.parseInt(args[i + 1]);
        i++;
      } else if (args[i].equals("-noudpchan")) {
        noudpchan = true;
      } else if (args[i].equals("-cdelim")) {
        winstonChannelDelimiter = args[i + 1];
        i++;
      } else if (args[i].equals("-cwbhost")) {
        host = args[i + 1];
        i++;
      } else if (args[i].equalsIgnoreCase("-nodb")) {
        noDB = true;
      } else if (args[i].equals("-holdings")) {
        useHoldings = true;
        prta("Set holdings mode at arg=" + i);
      } else if (args[i].equals("-holdingsmysql")) {
        holdingsMySQLServer = args[i + 1].replaceAll(";", ":");
        i++;
      } else if (args[i].equals("-dbedge")) {
        Util.setProperty("DBServer", args[i + 1]);
        i++;
      } else if (args[i].equals("-dbstatus")) {
        Util.setProperty("StatusDBServer", args[i + 1]);
        i++;
      } else if (args[i].equals("-dbmeta")) {
        Util.setProperty("MetaDBServer", args[i + 1]);
        i++;
      } else if (args[i].equals("-daysback")) {
        daysBack = Integer.parseInt(args[i + 1]);
        i++;
      } else if (args[i].equals("-nofilter")) {
        noFilter = true;
      } else if (args[i].equals("-filter")) {
        String[] parts = args[i + 1].split("-");
        if (parts.length == 3) {
          instCodes = parts[0];
          bandCodes = parts[1];
          compCodes = parts[2];
        }
        i++;
      } else if (args[i].equals("-mdsip")) {
        mdsIP = args[i + 1];
        i++;
      } else if (args[i].equals("-mdtable")) {
        mdTable = args[i + 1];
        i++;
      } else if (args[i].equals("-dbgchan")) {
        dbgSeedname = args[i + 1].replaceAll("-", " ");
        i++;
      } else if (args[i].equals("-mdsport")) {
        mdsPort = Integer.parseInt(args[i + 1]);
        i++;
      } else if (args[i].equals("-mdsto")) {
        mdsto = Integer.parseInt(args[i + 1]);
        i++;
      } else if (args[i].equals("-allowrestricted")) {
        allowrestricted = true;
      } else if (args[i].equals("-queryall")) {
        queryAllCases = true;
      } else if (args[i].equals("-menure")) {
        menuRE = args[i + 1];
        i++;
      } else if (args[i].equals("-subnet")) {
        subnetConfig = new SubnetConfig(args[i + 1]);
        prta("Subnet config: " + subnetConfig.toString());
        i++;
      } else if (args[i].equals("-lowpri")) {
        priorityBumps = args[i + 1].split(":");
        i++;
      } // else if(args[i].equals("-h")) { host=args[i+1]; i++;}
      else if (args[i].length() > 0) {
        if (args[i].substring(0, 1).equals(">")) {
          break;
        } else {
          prt(tag + "CWBWaveServer: unknown switch=" + args[i] + " ln=" + argline);
        }
      }
    }
    if (Util.getProperty("DBServer") == null) {
      noDB = true;
    } else if (Util.getProperty("DBServer").toLowerCase().contains("nodb")) {
      noDB = true;
    } else if (Util.getProperty("DBServer").equals("")) {
      noDB = true;
    } else if (DBConnectionThread.noDB) {
      noDB = true;
    }
    prta("Starting version " + Version.version);
    if (Util.getProperty("StatusServer") == null) {
      prt("CWS: *** StatusServer is null - set -noudpchan and -holdings");
      noudpchan = true;
      useHoldings = true;
    }
    if (!noudpchan) {
      prta(tag + Util.ascdate() + " CWS: SSR start on " + Util.getProperty("StatusServer") + "/" + AnssPorts.CHANNEL_SERVER_PORT);
      channels = new StatusSocketReader(ChannelStatus.class, Util.getProperty("StatusServer"),
              AnssPorts.CHANNEL_SERVER_PORT);
      prta(tag + " CWS: SSR has started.");
    }
    traceBufPool = new TraceBufPool(poolSize, bufSize, this);
    menuThread = new MenuBuilder(useHoldings, holdingsMySQLServer);
    //memchk = new MemoryChecker(60, this);
    prta(tag + Util.ascdate() + " CWS: created2 args=" + argline + " tag=" + tag + "host=" + host
            + " port=" + port + " useHoldings=" + useHoldings + " daysback=" + daysBack + " sec2000=" + year2000sec
            + " quiet=" + quiet + " allowrestrict=" + allowrestricted + " dbgch=" + dbgSeedname + " nodb=" + noDB);
    prta(tag + " CWS: nofilter=" + noFilter + " instCodes=" + instCodes + " bandCodes=" + bandCodes + " compCodes=" + compCodes);
    handlers = new ArrayList<>(maxThreads);
    for (int i = 0; i < minThreads; i++) {
      handlers.add(new CWBWaveServerHandler(null));
    }
    shutdown = new ShutdownCWBWS();
    Runtime.getRuntime().addShutdownHook(shutdown);
    this.setDaemon(true);
    running = true;
    mon = new CWBWaveServerMonitor();
    /*if(!noDB) while ( true) {
        Util.sleep(200);
        DBConnectionThread thr = DBConnectionThread.getThread("edge");
        if(thr != null) break;
      }*/

    start();
  }

  private Socket accept() throws IOException {
    return d.accept();
  }

  @Override
  public void run() {
    if (thisThread != null) {
      prt(" **** Duplicate creation of CWBWaverServer! Panic!");
      Util.exit(1);
    }
    thisThread = this;
    //monitor = new MonitorServer(monOffset < 100?AnssPorts.MONITOR_CWBWAVESERVER_PORT+monOffset:-monOffset, this);
    //GregorianCalendar now;
    //StringBuilder runsb=new StringBuilder(10000);
    long lastStatus = System.currentTimeMillis();
    long lastMemoryCheck = lastStatus;
    setPriority(getPriority() + 2);
    int loop = 0;
    if (!noDB) {
      /* We are no longer using DB withing the program
      while ( true) {
        Util.sleep(200);
        DBConnectionThread thr = DBConnectionThread.getThread("edge");
        if(thr != null) break;
        if(loop++ % 50 == 1) prta(tag+" *** waiting for edge db to open!");
      }
       */
    }

    // OPen up a port to listen for new connections.
    while (!terminate) {
      try {
        //server = s;
        prta(Util.clear(tmpsb).append(tag).append(" CWS: Open Port=").append(port));
        d = new ServerSocket(port);
        break;
      } catch (SocketException e) {
        if (e.getMessage().contains("Address already in use")) {
          try {
            prt(Util.clear(tmpsb).append(tag).append(" CWS: Address in use - try again."));
            Thread.sleep(2000);
          } catch (InterruptedException Expected) {
          }
        } else {
          prt(Util.clear(tmpsb).append(tag).append(" CWS:Error opening TCP listen port =").append(port).
                  append("-").append(e.getMessage()));
          try {
            Thread.sleep(2000);
          } catch (InterruptedException Expected) {
          }
        }
      } catch (IOException e) {
        prt(Util.clear(tmpsb).append(tag).append(" CWS:Error opening socket server=").append(e.getMessage()));
        try {
          Thread.sleep(2000);
        } catch (InterruptedException Expected) {

        }
      }
    }

    long key;
    Socket s = null;
    String from;
    int priority;
    StringBuilder ip = new StringBuilder(16);
    while (!terminate) {
      try {
        s = accept();
        //prta(tag+" CWS: accept from "+s);
        from = s.getInetAddress().toString();
        priority = Thread.NORM_PRIORITY;
        if (priorityBumps != null) {
          for (String match : priorityBumps) {
            if (from.contains(match)) {
              priority -= 2;
              break;
            }
          }
        }

        Util.clear(ip).append(s.getInetAddress().toString());
        key = s.getInetAddress().hashCode();
        synchronized (stats) {
          whoHasStats = 3;
          Stats s2 = stats.get(key);
          if (s2 == null) {
            s2 = new Stats(ip);
            stats.put(key, s2);
            s2.incNconn();
          }
        }
        whoHasStats = -3;

        // find a thread to assign this to
        boolean assigned = false;
        while (!assigned) {
          try {
            //prta(tag+" try to assign");
            // look through pool of connections and assign the first null one found
            for (int i = 0; i < handlers.size(); i++) {
              if (!handlers.get(i).isAlive()) {    // If the thread has failed, make a new one
                prta(Util.clear(tmpsb).append(tag).append("[").append(i).append("] is not alive ***** , replace it! ").
                        append(handlers.get(i).toStringBuilder(null)));
                SendEvent.edgeSMEEvent("CWBWSThrDown", tag + "[" + i + "] is not alive ***** , replace it! ", this);
                handlers.set(i, new CWBWaveServerHandler(null));
              }
              // If the handler is not assigned a socket, use it
              if (handlers.get(i).getSocket() == null) {
                if (dbg) {
                  prta(Util.clear(tmpsb).append(tag).append("[").append(i).append("] Assign socket to ").
                          append(i).append("/").append(handlers.size()).append("/").append(maxThreads).
                          append(" ").append(handlers.get(i).toStringBuilder(null)));
                }
                handlers.get(i).assignSocket(s, priority);
                usedThreads++;
                assigned = true;
                break;
              }
            }
          } catch (RuntimeException e) {
            SendEvent.edgeSMEEvent("RuntimeExcp", "CWBWS: got Runtime assigning socket=" + e, this);
            prta("Runtime assigning socket.  continue e=" + e);    // sometimes the socket closes during the if above from the thread.
            e.printStackTrace(getPrintStream());
          }

          // If we did not assign a connection, time out the list, create a new one, and try again
          if (!assigned) {
            long nw = System.currentTimeMillis();
            int nfreed = 0;
            int maxi = -1;
            long maxaction = 0;
            for (int i = 0; i < handlers.size(); i++) {
              if (dbg) {
                prta(Util.clear(tmpsb).append(tag).append(" CWS: check ").append(i).append(" ").append(handlers.get(i)));
              }
              // only dead sockets and long left analysts could be more than an hour old
              if (nw - handlers.get(i).getLastActionMillis() > 3600000 && handlers.get(i).getSocket() != null) {
                prta(Util.clear(tmpsb).append(tag).append(" CWS: Free connection ").append(i).
                        append(" ").append(handlers.get(i).toStringBuilder(null)));
                handlers.get(i).closeConnection();
                nfreed++;
              } else if (handlers.get(i).getSocket() != null) { // The socket is assigned, make sure its not closed
                Socket s2 = handlers.get(i).getSocket();
                if (s2 != null) {                          // Sometimes the sockets get closed in another thread, so check again
                  if (s2.isClosed()) {                     // its closed, free up everything
                    prta(Util.clear(tmpsb).append(tag).append(" CWS: found [").append(i).append("] is closed, free it"));
                    handlers.get(i).closeConnection();
                    nfreed++;
                  } else {      // Trace the maximum last action time and its index for handlers with a socket
                    if (maxaction < (nw - handlers.get(i).getLastActionMillis())) {
                      maxaction = nw - handlers.get(i).getLastActionMillis();
                      maxi = i;
                    }
                  }
                }
              }

            }
            if (nfreed > 0) {
              continue;        // go use one of the freed ones
            }            // If we are under the max limit, create a new one to handle this connection, else, have to wait!
            if (handlers.size() < maxThreads) {
              prta(Util.clear(tmpsb).append(tag).append(" create new CWBWSH ").append(handlers.size()).append(" s=").append(s));
              handlers.add(new CWBWaveServerHandler(s));
              usedThreads++;
              assigned = true;
            } else {
              if (maxi >= 0) {
                prta(Util.clear(tmpsb).append(tag).append(" CWS: ** No free connections and maxthreads reached.  Dropped oldest action=").
                        append(maxaction).append(" i=").append(maxi).append(" ").append(handlers.get(maxi).toStringBuilder(null)));
                SendEvent.debugEvent("CWBWSThrFull", "CWBWS thread list is full - deleting oldest", this);
                handlers.get(maxi).closeConnection();
                prta(Util.clear(tmpsb).append(tag).append(" after close ").append(handlers.get(maxi).toStringBuilder(null)));
                continue;
              }

              prta(Util.clear(tmpsb).append(tag).append(" CWS: **** There is no room for more threads. Size=").
                      append(handlers.size()).append(" s=").append(s));
              SendEvent.edgeSMEEvent("CWBWSMaxThread", "There is not more room for threads!", this);
              try {
                sleep(500);
              } catch (InterruptedException expected) {
              }
            }
          }
          if (terminate) {
            break;
          }
        } // Until something is assigned
      } catch (IOException e) {
        if (e.toString().contains("Too many open files")) {
          SendEvent.edgeEvent("TooManyOpen", "Panic, too many open files in CWBWaveServer", this);
          Util.exit(1);
        }
        Util.SocketIOErrorPrint(e, "in CWS setup - aborting", getPrintStream());
      } catch (OutOfMemoryError e) {
        prta(Util.clear(tmpsb).append("CWBWS: got out of memory - try to terminate!"));
        prt(Util.getThreadsString());
        Util.exit(101);
      } catch (RuntimeException e) {
        SendEvent.edgeSMEEvent("RuntimeExcp", "CWBWS: got Runtime=" + e, this);
        prta(Util.clear(tmpsb).append("Runtime in CWBWS continue: ip=").append(ip).append(" s=").append(s));
        e.printStackTrace(getPrintStream());
      }
      long now = System.currentTimeMillis();
    }       // end of infinite loop (while(true))
    for (CWBWaveServerHandler handler : handlers) {
      if (handler != null) {
        handler.terminate();
      }
    }
    //prt("Exiting CWBWaveServers run()!! should never happen!****\n");
    prta(tag + " CWS:read loop terminated");
    running = false;
  }

  private class ShutdownCWBWS extends Thread {

    public ShutdownCWBWS() {

    }

    /**
     * this is called by the Runtime.shutdown during the shutdown sequence cause all cleanup actions
     * to occur
     */
    @Override
    public void run() {
      terminate = true;
      interrupt();
      prta(tag + " CWS: Shutdown started");
      int nloop = 0;
      if (d != null) {
        if (!d.isClosed()) {
          try {
            d.close();
          } catch (IOException expected) {
          }
        }
      }
      prta(tag + " CWS:Shutdown Done.");
      try {
        sleep(10000);
      } catch (InterruptedException expected) {
      }
      //if(!quiet) 
      prta(Util.getThreadsString());
      prta(tag + " CWS:Shutdown Done exit");

    }
  }

  /**
   * This class builds up a channelLIst which consists of a TLongObjectHashMap of that name pointing
   * to ChannelList objects. This is the object that needs synchronization to make sure the
   * ChannelList map is not being updated or accessed during an update. It potentially uses the msgs
   * structure of ChannelStatus from UdpChannel StatusSocketReader to get the end times for the MENU
   * (else it uses the current time!). This object also must be synchronized with modified of
   * accessed to insure not simultaneous update.
   * <br>
   * This class can use holdings to build up the channelLIst used to make up menus. In either case
   * the edge.channel table is used to make a list of channels where created and lastData is used to
   * set the times if they are outside of the range from the optional holdings.
   *
   */
  private final class MenuBuilder extends Thread {

    private DBConnectionThread /*dbconnedge,*/ dbconnHoldings; // dbconnedge is used for seedname table, dbconnHoldings for holdings table
    private final StringBuilder sb = new StringBuilder(50000);
    private final StringBuilder sbins = new StringBuilder(50000);
    private final StringBuilder sbchan = new StringBuilder(50000);
    private final StringBuilder sbgetchan = new StringBuilder(50000);
    private final StringBuilder sbgetchanmd = new StringBuilder(50000);
    private final StringBuilder tmpsb = new StringBuilder(10000);
    private final StringBuilder runsb = new StringBuilder(100);
    //private DecimalFormat df3 = new DecimalFormat("0.000");
    private final TLongObjectHashMap<ChannelList> channelList = new TLongObjectHashMap<>();// All update/access to channelList should be synchronized on this object
    private final GregorianCalendar g = new GregorianCalendar();
    private PreparedStatement newHoldings = null;
    private final PreparedStatement[] currentHoldings = new PreparedStatement[3];
    //private  PreparedStatement channelQuery=null;
    //private  PreparedStatement updateCreated;
    private final boolean useHoldings;
    private String holdingServer;
    private int nmsgs;
    private Object[] msgs = new Object[2000];
    private final Integer msgsMutex = Util.nextMutex(); // All update or access to msgs need to synchronize on this
    private int msgsState = 0;
    private long lastMsgsUpdate;
    private int menustate;
    private int lastmenustate;
    private int runmenustate;
    private boolean mdsOK;

    public boolean isMDSOK() {
      return mdsOK;
    }

    public int getMenuState() {
      return menustate;
    }

    public int getRunMenuState() {
      return runmenustate;
    }

    public int getLastMenuState() {
      return lastmenustate;
    }

    public int getWWSChannelListState() {
      return getWWSChannelListState;
    }

    public int getMsgsState() {
      return msgsState;
    }

    @Override
    public String toString() {
      return toStringBuilder(tmpsb).toString();
    }

    /**
     *
     * @param sb If not null, this is added to the given one. If null, an internal string builder is
     * returned
     * @return
     */
    public synchronized StringBuilder toStringBuilder(StringBuilder sb) {
      if (sb == null) {
        sb = Util.clear(tmpsb);
      }
      return sb.append("MB: menustate=").append(runmenustate).append("/").append(menustate).
              append("/").append(lastmenustate).append(" WWSChannelListState=").append(getWWSChannelListState).
              append(" msgsState=").append(msgsState).append(" whoHasStat=").append(whoHasStationList).
              append(" whoHasChan=").append(whoHasChannelList);
    }

    public void reopenDB() {
      try {
        if (newHoldings != null) {
          newHoldings.close();
          newHoldings = null;
        }
      } catch (SQLException expected) {
      }
      //try {if(channelQuery != null) {channelQuery.close(); channelQuery=null;}} catch(SQLException e) {}
      for (int i = 0; i < currentHoldings.length; i++) {
        try {
          if (currentHoldings[i] != null) {
            currentHoldings[i].close();
            currentHoldings[i] = null;
          }
        } catch (SQLException expected) {
        }
      }
      if (dbconnHoldings != null) {
        dbconnHoldings.reopen();
      }
      //dbconnedge.reopen();
    }

    public void getWWSInstrumentList() {
      lastmenustate = menustate;
      menustate = 1;
      Util.clear(sbins);
      synchronized (stationList) {   // We use this so that the buf access cannot be caught in an inconsistent menustate
        whoHasStationList = 1;
        instrumentListSize = stationList.size();
        if (menuKeys.length < stationList.size() * 12 / 10) {
          menuKeys = new long[stationList.size() * 12 / 10];
        }
        menuKeys = stationList.keys(menuKeys);
        Arrays.sort(menuKeys, 0, instrumentListSize);
        //while(itr.hasNext()) {
        //  itr.advance();
        for (int i = 0; i < instrumentListSize; i++) {
          //sbins.append(itr.value().getInstrumentString()).append("\n");
          sbins.append(stationList.get(menuKeys[i]).getInstrumentString()).append("\n");
        }
        instrumentLength = sbins.length();
        if (instrumentLength > instrument.length) {
          instrument = new byte[instrumentLength * 2];
          //instrumentchar = new char[instrumentLength*2];
        }
        Util.stringBuilderToBuf(sbins, instrument);
        //sbins.getChars(0,instrumentLength, instrumentchar, 0);
        //for(int i=0; i<instrumentLength; i++) instrument[i] = (byte) instrumentchar[i];
        instrumentUpdate = System.currentTimeMillis();
      }
      whoHasStationList = -1;
      lastmenustate = menustate;
      menustate = -1;
    }
    int getWWSChannelListState;

    public void getWWSChannelList() {
      lastmenustate = menustate;
      menustate = 2;
      //prta("getWWSChannelList need to sync whoHas="+whoHasChannelList+" getWWSstatin="+getWWSChannelListState);
      getWWSChannelListState = 1;
      synchronized (channelList) {   // We use this so that the buf access cannot be caught in an inconsistent menustate
        //prta("getWWSChannelList synced channelListSize="+channelList.size());
        menustate = 21;
        getWWSChannelListState = 2;
        whoHasChannelList = 1;
        Util.clear(sbchan);
        Util.clear(sbgetchan);
        Util.clear(sbgetchanmd);
        //TLongObjectIterator<ChannelList> itr = channelList.iterator();
        if (menuKeys.length < channelList.size() * 12 / 10) {
          menuKeys = new long[channelList.size() * 12 / 10];
        }
        int size = channelList.size();
        menuKeys = channelList.keys(menuKeys);
        Arrays.sort(menuKeys, 0, size);
        channelListSize = channelList.size();
        //while(itr.hasNext()) {
        //  itr.advance();
        //  ChannelList ch = itr.value();
        menustate = 22;
        for (int i = 0; i < size; i++) {
          ChannelList ch = channelList.get(menuKeys[i]);
          sbchan.append(ch.getWWSChannelString(getEnd(ch.getChannelSB()))); // note this computes strings needed next
          sbgetchan.append(ch.getChannelString()).append("\n");
          //sbgetchanmd.append(ch.getChannelMetadataString()).append("\n");
          if (ch.getChannelString().length() > 0) {
            if (ch.getChannelString().indexOf("\n") >= 0) {
              prta("getWWSChannelList: ** Offset i=" + i + " channelString has newline ! "
                      + ch.getChannelString().indexOf("\n") + " " + ch.getChannelString());
            }
            if (ch.getMetadataEnd().indexOf("\n") >= 0) {
              prta("getWWSChannelList: ** Offset i=" + i + " metadataEnd string has newline! :"
                      + ch.getMetadataEnd().indexOf("\n") + " " + ch.getMetadataEnd());
            }
            sbgetchanmd.append(ch.getChannelString()).
                    append(Util.stringBuilderReplaceAll(ch.getMetadataEnd(), "\n", "").append("\n"));
          } else {
            prta("getWWSChannelList: ** Offset i=" + i + " channel is empty");// looking for extra newlins
          }
        }
        int l = 0;
        while (l != sbgetchanmd.length()) { // eliminate extra lines
          l = sbgetchanmd.length();
          Util.stringBuilderReplaceAll(sbgetchanmd, "\n\n", "\n");
          if (l != sbgetchanmd.length()) {
            prta("getWWSChannelList: ** multiple newlines found! l=" + l + " sblen=" + sbgetchanmd.length());
          }
        }

      }
      whoHasChannelList = -1;
      menustate = 23;
      getWWSChannelListState = 3;
      channelLength = sbchan.length();
      //prta("getWWSChannelList need channelBytesMutex");
      synchronized (channelBytesMutex) {
        menustate = 24;
        //prta("got channelBytesMutex");
        if (channelLength > channelbytes.length) {
          channelbytes = new byte[channelLength * 2];
          //channelchar = new char[channelLength*2];
        }
        Util.stringBuilderToBuf(sbchan, channelbytes);
        //sbchan.getChars(0,channelLength, channelchar, 0);
        //for(int i=0; i<channelLength; i++) channelbytes[i] = (byte) channelchar[i];
      }

      // do the same for the getChannel command
      menustate = 25;
      getWWSChannelListState = 4;
      getChannelLength = sbgetchan.length();
      //prta("getWWSChannelList need getChannelBytesMutex");
      synchronized (getChannelBytesMutex) {
        menustate = 26;
        //prta("got getChannelBytesMutex");
        if (getChannelLength > getChannelBytes.length) {
          getChannelBytes = new byte[getChannelLength * 2];
          //getChannelChar = new char[getChannelLength*2];
        }
        Util.stringBuilderToBuf(sbgetchan, getChannelBytes);
        //sbgetchan.getChars(0, getChannelLength, getChannelChar,0);
        //for(int i=0; i<getChannelLength; i++) getChannelBytes[i] = (byte) getChannelChar[i];
      }
      getWWSChannelListState = 5;
      // do the same for the getChannel metadata command
      getChannelMetadataLength = sbgetchanmd.length();
      //prta("getWWSChannelList need getChannelMetadataBytesMutex");
      menustate = 27;
      synchronized (getChannelMetadataBytesMutex) {
        //prta("got getChannelMetadataBytesMutex");
        menustate = 28;
        if (getChannelMetadataLength > getChannelMetadataBytes.length) {
          getChannelMetadataBytes = new byte[getChannelMetadataLength * 2];
          //getChannelMetadataChar = new char[getChannelMetadataLength*2];
        }
        Util.stringBuilderToBuf(sbgetchanmd, getChannelMetadataBytes);
        //sbgetchanmd.getChars(0, getChannelMetadataLength, getChannelMetadataChar,0);
        //for(int i=0; i<getChannelMetadataLength; i++) getChannelMetadataBytes[i] = (byte) getChannelMetadataChar[i];
      }
      menustate = 29;
      //menu = runsb.toString().getBytes();  // This is shorter but creates strings that build up
      getWWSChannelListState = 6;
      channelUpdate = System.currentTimeMillis();

      //prta("getWWSChannelList done!");
      whoHasChannelList = -1;
      menustate = -2;
      getWWSChannelListState = -1;
    }

    public MenuBuilder(boolean holdings, String holdingServer) {
      useHoldings = holdings;
      this.holdingServer = holdingServer;
      sb.append("\n");
      menu[0] = '\n';
      //menuchar[0] = (char) '\n';
      menuBufLength = 1;
      setDaemon(true);
      start();
    }

    private void makeNoDB() {
      long daysbackms = (System.currentTimeMillis() - daysBack * 86400000L) / 86400000L * 86400000L;
      while (!EdgeChannelServer.isValid()) {
        try {
          sleep(1000);
        } catch (InterruptedException expected) {
        }
      }
      while (!terminate) {
        TLongObjectIterator itr = EdgeChannelServer.getIterator();
        synchronized (channelList) {
          whoHasChannelList = 2;
          if (itr != null) {
            while (itr.hasNext()) {
              itr.advance();
              Channel c = (Channel) itr.value();
              ChannelList cl = channelList.get(Util.getHashFromSeedname(c.getChannelSB()));
              if (cl == null) {
                cl = new ChannelList(c.getChannelSB(), daysbackms, c.getLastData().getTime());
                channelList.put(Util.getHashFromSeedname(c.getChannelSB()), cl);
              }
            }
          }
        }
        whoHasChannelList = -2;
        for (int i = 0; i < 10; i++) {
          try {
            sleep(1000);
          } catch (InterruptedException expected) {
          }
          if (terminate) {
            break;
          }
        }
      }
    }
    int updated;

    @Override
    public void run() {
      String[] holdingsTables = {"holdingshist", "holdings", "holdingshist2"};
      prta(Util.clear(runsb).append("MenuBuilder is in holdingsMode=").append(useHoldings));
      int loop = 0;
      long lastMDSUpdate = 0;
      boolean doMDS;
      try {
        sleep(4000);
      } catch (InterruptedException expected) {
      }
      Timestamp ts = new Timestamp(900000L);
      if (noDB) {
        makeNoDB();
      } else {
        while (/*dbconnedge == null |*/dbconnHoldings == null && useHoldings) {
          try {
            /* dbconnedge = DBConnectionThread.getThread("edge");
            if(dbconnedge == null) {
              dbconnedge = new DBConnectionThread(Util.getProperty("DBServer"), "readonly", "edge", false, false,"edge", getPrintStream());
              addLog(dbconnedge);
              if(!DBConnectionThread.waitForConnection("edge"))
                if(!DBConnectionThread.waitForConnection("edge"))
                  if(!DBConnectionThread.waitForConnection("edge")) {
                    EdgeThread.staticprt(" **** Could not connect to database "+Util.getProperty("DBServer"));
                  }
            }*/
            // We try to open a holdingSever if one is set in the StatusDBServer variable
            if (holdingServer == null) {
              holdingServer = Util.getProperty("StatusDBServer");
            }
            if (holdingServer != null) {
              if (!holdingServer.equals("")) {
                dbconnHoldings = DBConnectionThread.getThread("status");
                if (dbconnHoldings == null) {
                  dbconnHoldings = new DBConnectionThread(holdingServer, "readonly", "status", false, false, "status", getPrintStream());
                  addLog(dbconnHoldings);
                  if (!DBConnectionThread.waitForConnection("status")) {
                    if (!DBConnectionThread.waitForConnection("status")) {
                      if (!DBConnectionThread.waitForConnection("status")) {
                        if (!DBConnectionThread.waitForConnection("status")) {
                          if (!DBConnectionThread.waitForConnection("status")) {
                            if (!DBConnectionThread.waitForConnection("status")) {
                              if (!DBConnectionThread.waitForConnection("status")) {
                                if (!DBConnectionThread.waitForConnection("status")) {
                                  EdgeThread.staticprt(" **** Could not connect to status database " + holdingServer);
                                  prta(" **** could not connect to status DB " + holdingServer);
                                }
                              }
                            }
                          }
                        }
                      }
                    }
                  }
                  prta("DB to holdings is connected db=" + holdingServer);
                  break;
                }
              }
            }
          } catch (InstantiationException e) {
            EdgeThread.staticprt(" **** Impossible Instantiation exception e=" + e);
            Util.exit(0);
          }
          if (dbconnHoldings == null) {
            try {
              sleep(30000);
            } catch (InterruptedException expected) {
            }
          }
        }
      }
      prta("CWS: MENULIST Waiting for thread startups");
      while (thisThread == null) {
        try {
          sleep(1000);
        } catch (InterruptedException expected) {
        }
      }
      prta("CWS: MENULIST waiting for ChannelServer ready=" + EdgeChannelServer.isValid());
      while (!EdgeChannelServer.isValid()) {
        try {
          sleep(1000);
        } catch (InterruptedException expected) {
        }
      }
      prta("CWS: MENULIST starting - channels are valid");
      // the wait at the bottom of the loop is 15 minutes except during setup.
      while (!terminate) {
        try {

          int count;
          updated = 0;
          long now = System.currentTimeMillis();
          g.setTimeInMillis(now - daysBack * 86400000L);
          doMDS = false;
          // If its been a day since we updated from the MDS, do it again or if the user did a UPDATEMDS command
          if (now - lastMDSUpdate > 86400000L || doMDSNow || loop == 3) {
            doMDS = true;
            lastMDSUpdate = now;
            doMDSNow = false;
            loop = 0;
          }
          ResultSet rs;
          count = 0;
          updated = 0;
          Util.clear(sb);

          if (useHoldings) {// Get daysbackms stop from holdings
            int ok = 0;
            for (;;) {
              try {
                if (loop >= 3) {
                  // We want to find any new channels first 
                  prta("CWS: MENULIST start find new");
                  if (newHoldings == null) {
                    newHoldings = dbconnHoldings.prepareStatement(
                            "SELECT seedname,min(start),max(ended) FROM status.holdings "
                            + " WHERE created > now() - INTERVAL 1000 SECOND GROUP BY seedname ORDER BY seedname", false);
                  }
                  //rs = dbconnHoldings.executeQuery("SELECT seedname,min(daysbackms),max(ended) FROM status.holdings "+
                  //        " WHERE created > now() - INTERVAL 1000 SECOND GROUP BY seedname ORDER BY seedname");
                  rs = newHoldings.executeQuery();
                  runmenustate = 111;
                  int n = doResultSet(rs, true, true);   // Do not do update from MDS during main loads!
                  runmenustate = 112;
                  prta(Util.clear(runsb).append("CWS:  MENULIST find new n=").append(n).append(" upd=").append(updated));
                  count += n;
                  rs.close();
                }
                if (loop == 3) {       // DO MDS pass 
                  long begin = System.currentTimeMillis();
                  int cnt = 0;

                  prta(Util.clear(runsb).append("CWS: MENULIST start updateMDS() pass on channels n=").append(channelList.size()));

                  synchronized (channelList) {
                    whoHasChannelList = 9;
                    TLongObjectIterator<ChannelList> itr = channelList.iterator();
                    while (itr.hasNext()) {
                      itr.advance();
                      ChannelList ch = itr.value();
                      cnt++;
                      if (cnt % 1000 == 1) {
                        prta(Util.clear(runsb).append("CWS: MENULIST updateMDS cnt=").append(cnt));
                      }
                      try {
                        if (ch != null) {
                          ch.updateMDS();
                        }
                      } catch (IOException e) {
                        prta("CWS: MENULIST: aborting channel MDS update - cannot connect to server " + ch + " e=" + e);
                        break;
                      }
                    }
                    mdsOK = true;
                    prta(Util.clear(runsb).append("CWS: MENULIST finished updateMDS pass elapse=").append(System.currentTimeMillis() - begin));
                  }
                }
                whoHasChannelList = -9;
                // If we are in the startup, or 3 hours after startup, perform the Holdings search
                if (loop % 12 < 3) {
                  // Note we want to do a periodic look through holdings to discover any recently added channels
                  // If this is a Holdings pass (after initialization) look for new ones, never do hist or hist 2 after startup
                  if (loop < 3 || loop % 3 == 1) {    // the first passes, or always for holdings for latest changes.
                    if (!quiet) {
                      prta(Util.clear(runsb).append("CWS: MENULIST currentHoldings ").append(holdingsTables[loop % 3]).
                              append(" ").append(Util.ascdate(g)).append(" ").append(Util.asctime(g)).
                              append(" days=").append(daysBack).append(" ").append(holdingsTables[loop % 3]).append(" loop=").append(loop));
                    }
                    dbconnHoldings.setTimeout(600);
                    prta(Util.clear(runsb).append("currentHoldings=").append(Arrays.toString(currentHoldings)).append(" dbh=").append(dbconnHoldings));
                    if (currentHoldings[loop % 3] == null) {
                      currentHoldings[loop % 3] = dbconnHoldings.prepareStatement(
                              "SELECT seedname,min(start),max(ended) FROM status." + holdingsTables[loop % 3]
                              + " WHERE ended>? GROUP BY seedname ORDER BY seedname", false);
                    }
                    //rs = dbconnHoldings.executeQuery("SELECT seedname,min(daysbackms),max(ended) FROM status."+holdingsTables[loop%3]+
                    //        " WHERE ended>'"+Util.ascdate(g).substring(0,10).replaceAll("/","-")+"'"+
                    //        " GROUP BY seedname ORDER BY seedname");
                    ts.setTime(g.getTimeInMillis());
                    currentHoldings[loop % 3].setTimestamp(1, ts);
                    rs = currentHoldings[loop % 3].executeQuery();
                    prta(Util.clear(runsb).append("CWS: MENULIST in doResult:").append(holdingsTables[loop % 3]));
                    runmenustate = 113;
                    count += doResultSet(rs, true, false);
                    runmenustate = 114;
                    prta(Util.clear(runsb).append("CWS: MENULIST out of doResult:").append(holdingsTables[loop % 3]));
                    rs.close();
                    break;    // leave infinite loop
                  }
                }
                break;    // nothing to do, leave
              } catch (SQLException e) {
                if (ok == 1) {
                  prta("CWS: MENULIST Error loading from holdings e=" + e);
                  SendEvent.edgeSMEEvent("CWBWSDBerror", "Error loading holdings did not clear e=" + e, this);
                  e.printStackTrace(getPrintStream());
                }
                ok++;
                dbconnHoldings.reopen();
                newHoldings = null;
                for (int i = 0; i < currentHoldings.length; i++) {
                  currentHoldings[i] = null;
                }
                try {
                  sleep(4000);
                } catch (InterruptedException expected) {
                }
              }
            }   // end of infinite loop
          }     // end if holdings mode
          try {
            sleep(2000);
          } catch (InterruptedException expected) {
          }
          runmenustate = 104;
          /*ts.setTime(g.getTimeInMillis());
              channelQuery.setTimestamp(1, ts);
              rs = channelQuery.executeQuery();*/
          runmenustate = 105;
          //prta("CWS: in doresult");
          count += doChannelUpdate(loop % 10 == 0 && mdsPort != 0);  // if loop > 3, do MDS on all new channels if there is an mds
          runmenustate = 106;

          prta(Util.clear(runsb).append("CWS: Run MENULIST channel list = ").append(System.currentTimeMillis() - now).append("ms #new chn=").append(count).append(" updated=").append(updated).append(useHoldings ? " holdings=" + useHoldings + (loop % 12 < 3 ? " " + holdingsTables[loop % 3] : "") : "").append(" loop=").append(loop));
          // The first loop (if not holdings) do an MDS pass
          runmenustate = 108;
          loop++;
          runmenustate = 109;
          if (loop <= 3 && useHoldings) {
            try {
              sleep(15000);
            } catch (InterruptedException expected) {
            }
            continue;
          }
          if (loop == 3 && dbg) {
            synchronized (channelList) {
              whoHasChannelList = 200;
              TLongObjectIterator<ChannelList> itr = channelList.iterator();
              while (itr.hasNext()) {
                itr.advance();
                prt(itr.value().toStringBuilder(null));
              }
              // We have all of the old holdings, if we have a good StatusSocketReader, we only need to add any new ones           
            }
            whoHasChannelList = -200;
          }
          runmenustate = 100;
          for (int i = 0; i < 900; i++) {
            try {
              sleep(1000);
            } catch (InterruptedException expected) {
            }    // Update the last data, mds, etc every 15 minutes.
            if (terminate) {
              break;
            }
          }
          if (terminate) {
            break;
          }
        } catch (RuntimeException e) {
          prta(Util.clear(runsb).append("CWS: MENULIST BUILDER: Runtime error e=").append(e));
          e.printStackTrace(getPrintStream());
          try {
            sleep(90000);
          } catch (InterruptedException expected) {
          }
        }
      }
      //if(dbconnedge != null) dbconnedge.close();
      if (dbconnHoldings != null) {
        dbconnHoldings.close();
      }
      prta("CWS:  MENULIST is exiting");

    }
    /**
     * This is now really used only for isHoldings true. doCHannelUpdate is used for access to the
     * channel table.
     *
     * @param rs The result set
     * @param isHoldings Is this from a holdings result
     * @param doMDS If this channel is new, do the MDS update
     * @return count of new channels
     * @throws SQLException
     */
    StringBuilder dorssb = new StringBuilder(12);
    StringBuilder seed = new StringBuilder(12);
    StringBuilder chan = new StringBuilder(12);

    private int doResultSet(ResultSet rs, boolean isHoldings, boolean doMDS) throws SQLException {
      ChannelList ch;
      int count = 0;
      int nrestricted = 0;
      long start;
      runmenustate = 3;
      prta(Util.clear(runsb).append("CWS: MENULIST start doResultSet isHoldings=").append(isHoldings).
              append(" whoHasChanList=").append(whoHasChannelList).append(" doMDS=").append(doMDS));
      int loop = 0;
      while (whoHasChannelList > 0) {
        try {
          sleep(100);
          if (loop++ % 600 == 599) {
            prta(Util.clear(runsb).append("CWS: MENULIST stuck waiting for whoHasChannelList < 0 = ").
                    append(whoHasChannelList).append(" loop=").append(loop));
            if (loop > 6000) {
              break;
            }
          }
        } catch (InterruptedException expected) {
        }
      }
      try {
        prta(Util.clear(runsb).append("CWS: MENULIST loop doResultSet isHoldings=").append(isHoldings));

        while (rs.next()) {
          start = System.currentTimeMillis();
          //String seed = (rs.getString(1)+"            ").substring(0,12);
          Util.clear(seed).append(rs.getString(1));
          Util.stringBuilderRightPad(seed, 12);
          Util.clear(chan).append(seed.charAt(7)).append(seed.charAt(8)).append(seed.charAt(9));

          if (!noFilter) {
            // if any part of channel is not in the allowed ranges, skip this channel
            if (!instCodes.contains(chan.substring(0, 1)) || !bandCodes.contains(chan.substring(1, 2)) || !compCodes.contains(chan.substring(2, 3))) {
              continue;
            }
            /*if( chan.charAt(0) != 'M' && chan.charAt(0) != 'E' && chan.charAt(0) != 'S' &&
                chan.charAt(0) != 'H' && chan.charAt(0) != 'B' && chan.charAt(0) != 'L' &&
                chan.charAt(0) != 'D' && chan.charAt(0) != 'C' ) continue;
            if(chan.charAt(1) != 'H' && chan.charAt(1) != 'L' && chan.charAt(1) != 'N' && 
              chan.charAt(1) != 'D' && chan.charAt(1) != 'S' && chan.charAt(1) != 'V' && 
              chan.charAt(1) != 'Q') continue;
            if(chan.charAt(2) != 'Z' && chan.charAt(2) != 'N' && chan.charAt(2) != '1' &&
              chan.charAt(2) != 'E' && chan.charAt(2) != '2' && chan.charAt(2) != '3' &&
              chan.charAt(2) != 'F' && chan.charAt(2) != 'H' && chan.charAt(2) != 'G') continue;*/
            if (chan.substring(0, 2).equals("LN")) {
              continue;
            }
            //if(seed.substring(0,2).equals("IM") && chan.charAt(0) == 'S' && !seed.substring(10).equals("FB")) continue;
            if (seed.substring(0, 2).equals("XX") || seed.substring(0, 2).equals("FB")) {
              continue;
            }

            //if(seed.substring(0,2).equals("IM") && Character.isDigit(seed.charAt(5)) && seed.charAt(4) != '3') continue;
          } else {
            boolean notOK = false;
            for (int i = 0; i < seed.length(); i++) {
              int c = seed.charAt(i);
              if (!Character.isUpperCase(c) && !Character.isDigit(c) && c != ' ') {
                prta("Illegal Seedname " + seed + "| at " + i + " is " + c + "| not uppercase, number of space! skip this channel");
                notOK = true;
              }
            }
            if (notOK) {
              continue;
            }
          }
          if (seed.indexOf("XXTEMP  LHE") == 0) {
            prta("GECSS start");
          }
          //if(count < 100) prta("CWS: MENULIST get seedname "+seed);
          if (!allowrestricted) {
            Channel c = EdgeChannelServer.getChannelNoTraceback(seed);
            if (c != null) {
              if (EdgeChannelServer.isChannelNotPublic(c)) {
                nrestricted++;
                continue;
              }
              if (EdgeChannelServer.isChannelArrayPort(c)) {
                continue;
              }
            }

          } else {
            Channel c = EdgeChannelServer.getChannelNoTraceback(seed);
            if (c != null) {
              if (EdgeChannelServer.isChannelArrayPort(c)) {
                continue;
              }
            }

          }
          //prta("CWS: MENULIST DEBUG  get Channel"+rs.getString(1)); 
          Util.clear(dorssb).append(rs.getString(1));
          Util.stringBuilderRightPad(dorssb, 12);
          runmenustate = 31;
          synchronized (channelList) {
            whoHasChannelList = 2;
            runmenustate = 32;
            ch = channelList.get(Util.getHashFromSeedname(dorssb));
          }
          whoHasChannelList = -2;
          if (ch == null) {
            count++;
            runmenustate = 38;
            ch = new ChannelList(rs, isHoldings);
            synchronized (channelList) {
              whoHasChannelList = 22;
              channelList.put(Util.getHashFromSeedname(dorssb), ch);
            }
            whoHasChannelList = -22;
            if (!quiet) {
              if (count % 100 == 1) {
                prta(Util.clear(runsb).append("CWS: MENULIST Create ").append(rs.getString(1)).
                        append(" cnt=").append(count).append(" clistSize=").append(channelList.size()));
              }
            }
            runmenustate = 39;
          } else {
            updated++;
            runmenustate = 33;
            ch.update(rs, isHoldings);
            if (doMDS) {
              menustate = 34;
              try {
                ch.updateMDS();
              } catch (IOException e) {
                prta(Util.clear(runsb).append("CWBWS MENU doUpdate() io Err getting MDS=").append(e));
                try {
                  sleep(5000);
                } catch (InterruptedException expected) {
                } // MDS is probably not up, give it a chance.
              }
              runmenustate = 35;
            }
          }
          runmenustate = 36;
          if (count % 1000 == 1 || updated % 1000 == 1) {
            prta(Util.clear(runsb).append("CWS: MENULIST progress doResultSet count=").append(count).
                    append(" upd=").append(updated).append(" restricted=").append(nrestricted));
          }

          if (System.currentTimeMillis() - start > 100) {
            prta(Util.clear(runsb).append("CWS: doResultSet Slow ").append(seed).append(" ").append(System.currentTimeMillis() - start));
          }
        }
        runmenustate = 37;
      } catch (SQLException e) {
        runmenustate = -33;
        throw e;
      }
      rs.close();
      runmenustate = -3;
      return count;
    }

    /**
     * this is just like doResultSet, but runs from DBAccess channel table
     *
     * @param doMDS Update the MDS on every channel this time
     * @return Number of new channels created
     */
    private int doChannelUpdate(boolean doMDS) {
      ChannelList chlist;
      int count = 0;
      int nrestricted = 0;
      long start;
      try {
        runmenustate = 3;
        prta(Util.clear(runsb).append("CWS: MENULIST start doChannelUpdate ").
                append(" whoHasChanList=").append(whoHasChannelList).append(" doMDS=").append(doMDS));
        int loop = 0;
        while (whoHasChannelList > 0) {
          try {
            sleep(100);
            if (loop++ % 600 == 599) {
              prta(Util.clear(runsb).append("CWS: MENULIST stuck waiting from whoHasChannelList=").
                      append(whoHasChannelList).append(" loop=").append(loop));
              if (loop > 6000) {
                break;
              }
            }
          } catch (InterruptedException expected) {
          }
        }
        DBAccess dba = DBAccess.getAccess();
        prta(Util.clear(runsb).append("CWS: MENULIST loop doChannelUpdate #chans=").append(dba.getChannelSize()));
        for (int i = 0; i < dba.getChannelSize(); i++) {
          Channel c = dba.getChannel(i);
          start = System.currentTimeMillis();
          //String seed = (rs.getString(1)+"            ").substring(0,12);
          Util.clear(seed).append(c.getChannelSB());
          Util.stringBuilderRightPad(seed, 12);
          Util.clear(chan).append(seed.charAt(7)).append(seed.charAt(8)).append(seed.charAt(9));

          if (!noFilter) {
            // if any part of channel is not in the allowed ranges, skip this channel
            if (!instCodes.contains(chan.substring(0, 1)) || !bandCodes.contains(chan.substring(1, 2)) || !compCodes.contains(chan.substring(2, 3))) {
              continue;
            }
            /*if( chan.charAt(0) != 'M' && chan.charAt(0) != 'E' && chan.charAt(0) != 'S' &&
                chan.charAt(0) != 'H' && chan.charAt(0) != 'B' && chan.charAt(0) != 'L' &&
                chan.charAt(0) != 'D' && chan.charAt(0) != 'C') continue;
            if(chan.charAt(1) != 'H' && chan.charAt(1) != 'L' && chan.charAt(1) != 'N' && 
              chan.charAt(1) != 'D' && chan.charAt(1) != 'Q') continue;
            if(chan.charAt(2) != 'Z' && chan.charAt(2) != 'N' && chan.charAt(2) != '1' &&
              chan.charAt(2) != 'E' && chan.charAt(2) != '2' && chan.charAt(2) != '3' &&
              chan.charAt(2) != 'F') continue;*/
            if (chan.substring(0, 2).equals("LN")) {
              continue;
            }
            //if(seed.substring(0,2).equals("IM") && chan.charAt(0) == 'S' && !seed.substring(10).equals("FB")) continue;
            if (seed.substring(0, 2).equals("XX") || seed.substring(0, 2).equals("FB")) {
              continue;
            }

            //if(seed.substring(0,2).equals("IM") && Character.isDigit(seed.charAt(5)) && seed.charAt(4) != '3') continue;
          } else {
            boolean notOK = false;
            for (int j = 0; j < seed.length(); j++) {
              int ch = seed.charAt(j);
              if (!Character.isUpperCase(ch) && !Character.isDigit(ch) && ch != ' ') {
                prta("Illegal Seedname " + seed + "| at " + j + " is " + ch + "| not uppercase, number of space! skip this channel");
                notOK = true;
              }
            }
            if (notOK) {
              continue;
            }
          }
          if (seed.indexOf("XXTEMP  LHE") == 0) {
            prta("GECSS start");
          }
          //if(count < 100) prta("CWS: MENULIST get seedname "+seed);
          if (!allowrestricted) {
            if (EdgeChannelServer.isChannelNotPublic(c)) {
              nrestricted++;
              continue;
            }
            if (EdgeChannelServer.isChannelArrayPort(c)) {
              continue;
            }

          } else {
            if (EdgeChannelServer.isChannelArrayPort(c)) {
              continue;
            }
          }
          //prta("CWS: MENULIST DEBUG  get Channel"+rs.getString(1)); 
          Util.clear(dorssb).append(c.getChannelSB());
          Util.stringBuilderRightPad(dorssb, 12);
          runmenustate = 31;
          synchronized (channelList) {
            whoHasChannelList = 8;
            runmenustate = 32;
            chlist = channelList.get(Util.getHashFromSeedname(dorssb));
          }
          whoHasChannelList = -8;
          if (chlist == null) {
            count++;
            runmenustate = 38;

            chlist = new ChannelList(dorssb, c.getCreated().getTime(), c.getLastData().getTime());
            //ch = new ChannelList(rs, isHoldings);
            synchronized (channelList) {
              whoHasChannelList = 23;
              channelList.put(Util.getHashFromSeedname(dorssb), chlist);
            }
            whoHasChannelList = -23;
            if (!quiet) {
              if (count % 100 == 1) {
                prta(Util.clear(runsb).append("CWS: MENULIST Create2 ").append(seed).
                        append(" cnt=").append(count).append(" clistSize=").append(channelList.size()));
              }
            }
            runmenustate = 39;
          } else {
            updated++;
            runmenustate = 33;
            chlist.update(c);
            if (doMDS) {
              runmenustate = 34;
              try {
                chlist.updateMDS();
              } catch (IOException e) {
                prta(Util.clear(runsb).append("CWS: MENULIST doCHannelUpdate() io Err getting MDS=").append(e));
                try {
                  sleep(5000);
                } catch (InterruptedException expected) {
                } // MDS is probably not up, give it a chance.
              }
              runmenustate = 35;
            }
          }
          runmenustate = 36;
          if (count % 1000 == 1 || updated % 1000 == 1) {
            prta(Util.clear(runsb).append("CWS: MENULIST progress doChannelUpdate count=").append(count).
                    append(" upd=").append(updated).append(" restricted=").append(nrestricted).append(" ").append(toStringBuilder(null)  ));
          }

          if (System.currentTimeMillis() - start > 100) {
            prta(Util.clear(runsb).append("CWS: Slow ").append(seed).append(" ").append(System.currentTimeMillis() - start));
          }
        }
        runmenustate = -3;
        return count;
      } catch (Exception e) {
        prta("CWS: doChannelUpdate runtime e=" + e);
        e.printStackTrace(getPrintStream());
        throw e;
      }
    }

    public double getStart(StringBuilder channel) {
      if (Character.isLowerCase(channel.charAt(7))) {
        return System.currentTimeMillis() / 1000. - QuerySpanCollection.getDuration();
      }
      double start;
      long now = System.currentTimeMillis();
      lastmenustate = menustate;
      menustate = 4;
      synchronized (g) {
        g.setTimeInMillis(now - daysBack * 86400000L);

        ChannelList ch = getChannelList(channel);
        if (ch == null && !quiet) {
          Util.prt(Util.clear(tmpsb).append("getStart failed channel=").append(channel).append(" ch=").append(ch));
        }

        if (ch != null) {
          if (ch.getCreated() > g.getTimeInMillis()) {
            start = ch.getCreated() / 1000.;
          } else {
            start = g.getTimeInMillis() / 1000.;
          }
        } else {
          start = g.getTimeInMillis() / 1000.;
        }
      }
      menustate = -4;
      return start;
    }

    /**
     * various things need the SSR times, do not load too often and maintain the msgs objects
     *
     * @return T
     */
    private boolean loadMsgs() {  // was synchronized but why
      if (System.currentTimeMillis() - lastMsgsUpdate > 10000) {
        if (channels != null) {
          msgsState = 1;
          synchronized (msgsMutex) {
            nmsgs = channels.length();
            if (msgs.length < nmsgs + 10) {
              msgs = new Object[nmsgs * 2];
            }
            channels.getObjects(msgs);
          }
          msgsState = -1;
        } else {
          nmsgs = 0;
        }
        prta(Util.clear(tmpsb).append("loadMsgs returned ").append(msgs == null ? "Null" : msgs.length).
                append(" channels=").append(channels));
        lastMsgsUpdate = System.currentTimeMillis();
        return nmsgs > 0;
      }
      return false;
    }

    public double getEnd(StringBuilder channel) {
      double end;
      lastmenustate = menustate;
      menustate = 5;
      if (Character.isLowerCase(channel.charAt(7))) {
        menustate = -5555;
        return System.currentTimeMillis() / 1000.;
      }
      //synchronized (fake) {
      lastmenustate = menustate;
      menustate = 55;
      long now = System.currentTimeMillis();
      //int nmsgs=0;
      if (dbg) {
        prta("CDupd: Update start");
      }
      //g.setTimeInMillis(now - daysBack*86400000L);
      QuerySpan qspan = QuerySpanCollection.getQuerySpan(channel);
      lastmenustate = menustate;
      menustate = -551;
      if (qspan != null) {
        return qspan.getLastTime() / 1000.;
      }
      // Try to get messages from UdpChannel/SSR
      lastmenustate = menustate;
      menustate = 552;
      loadMsgs();
      lastmenustate = menustate;
      menustate = 553;

      ChannelList ch = getChannelList(channel);
      lastmenustate = menustate;
      menustate = 554;
      if (nmsgs > 0) {   // If we have this information us it
        lastmenustate = menustate;
        menustate = 555;
        if (ch == null) {
          //if(dbg) 
          prta(Util.clear(tmpsb).append(" ***** getEnd did not find channel ").append(channel).append(" ").append(getChannelList(channel)));
          menustate = -5;
          return System.currentTimeMillis() / 1000.;
        }
        lastmenustate = menustate;
        menustate = 556;
        msgsState = 2;
        synchronized (msgsMutex) {
          fake.setKey(channel.toString());
          lastmenustate = menustate;
          menustate = 557;
          int pos = Arrays.binarySearch(msgs, 0, nmsgs, fake);
          lastmenustate = menustate;
          menustate = 558;
          if (pos < 0) {
            end = Math.min(ch.getLastData() + 86400000, now) / 1000.;
          } // If this seedname is not currently coming in, estimate based on last data
          else {
            end = Math.max(ch.getLastData(), ((ChannelStatus) msgs[pos]).getNextPacketTime()) / 1000.;
          } // THis seedname is currently coming in
        }
        msgsState = -2;
      } else {    // Last resort use lastData time and guess.
        lastmenustate = menustate;
        menustate = 559;
        if (ch == null) {
          //if(dbg) 
          prta(Util.clear(tmpsb).append(" ***** getEnd did not find channel ").append(channel));
          menustate = -55;
          return System.currentTimeMillis() / 1000.;
        }
        if (now - ch.getLastData() < 86400000L) {
          end = Math.min(ch.getLastData() + 86400000, now) / 1000.;   // Ether a day after last data or now which ever is lower
        } else {
          end = ch.getLastData() / 1000;       // Its the last data time
        }
      }
      //}
      menustate = -5;
      return end;
    }

    /* public TLongObjectHashMap<ChannelList> getChannelListTreeMap() {
      return channelList;
    }*/
    public Collection<ChannelList> getChannelListValues() {
      return channelList.valueCollection();
    }

    public ChannelList getChannelList(CharSequence channel) {
      lastmenustate = menustate;
      menustate = 6;
      ChannelList ch;
      synchronized (channelList) {
        whoHasChannelList = 11;
        ch = channelList.get(Util.getHashFromSeedname(channel));
      }
      whoHasChannelList = -11;
      menustate = -6;
      return ch;
    }
    private final StringBuilder menuscnl = new StringBuilder(80);

    public int getMenuSCNL(StringBuilder channel, byte[] b) {
      lastmenustate = menustate;
      menustate = 7;
      synchronized (menuscnl) {
        Util.clear(menuscnl).append("0 ").append(channel.substring(2, 7).trim()).append(" ").
                append(channel.substring(7, 10).trim()).append(" ").append(channel.substring(0, 2).trim()).
                append(" ").append((channel + "--").substring(10, 12).replaceAll(" ", "-")).append(" ").
                append(Util.df23(getStart(channel))).append(" ").append(Util.df23(getEnd(channel))).append(" s4 ");
        Util.stringBuilderToBuf(menuscnl, b);
      }
      menustate = -7;
      return menuscnl.length();
    }

    public ChannelStatus getChannelStatus(StringBuilder channel) {
      if (noudpchan) {
        return null;
      }
      //Object msgs[]=null;

      //int nmsgs=0;
      if (dbg) {
        prta("CDupd: Update start");
      }
      int count = 0;
      if (channels != null) {
        while (nmsgs <= 0 || msgs == null) {
          loadMsgs();
          if (nmsgs == 0) {
            try {
              sleep(1000);
              Util.clear(tmpsb).append(tag).append("getChannelStatus() waiting for SSR to give msgs! ");
              toStringBuilder(tmpsb);
              prta(tmpsb.append(" chans=").append(channels));
            } catch (InterruptedException expected) {
            }  // only happens if SSR is trying to connect
            if (count++ > 30) {
              prta(Util.clear(tmpsb).append(tag).
                      append(" getChannelStatus() *** could not get SSR msgs ssr=").append(channels));
              break;
            }  // bail after 30 secodns
          }
        }
      }
      ChannelStatus ch = null;
      if (nmsgs == 0) {
        return ch;
      }
      msgsState = 3;
      synchronized (msgsMutex) {
        fake.setKey(channel.toString());
        int pos = Arrays.binarySearch(msgs, 0, nmsgs, fake);
        if (pos < 0) {
          return null;
        }
        ch = ((ChannelStatus) msgs[pos]); // THis seedname is currently coming in
      }
      msgsState = -3;
      return ch;
    }
    private int dropped;
    private long latest;
    private long latestSSR;
    private int nssr;
    private int nlastdata;
    private int nldovr;
    private int nnow;
    private int ncurr;
    private int maxcurr;
    private int nprimary;
    StringBuilder menuDbg = new StringBuilder(1000);
    StringBuilder sbsum = new StringBuilder(100);
    private long[] menuKeys = new long[1000];

    public StringBuilder getSummary() {
      Util.clear(sbsum);
      getMenuList();    // This is a bad idea
      sbsum.append("Nstation=").append(nprimary).append("\nNstacurr=").append(ncurr).
              append("\nNstamaxcur=").append(maxcurr).append("\ncurrpct=").append(ncurr * 100 / Math.max(1, maxcurr)).
              append("\nNchnssr=").append(nssr).
              append("\nNchnlast=").append(nlastdata).append("\n");
      return sbsum;
    }
    /** only one actual processing should happen at a time, so synchronize on this method
     * 
     */
    public synchronized void getMenuList() {
      long now = System.currentTimeMillis();
      double dnow = now / 1000.;
      lastmenustate = menustate;
      menustate = 8;
      if (now - menuLastUpdate < 20000) {
        menustate = -8888;
        return;
      }
      menuLastUpdate = now;
      double start;
      double end = 0;
      //Object msgs[]=null;

      //int nmsgs=0;
      if (dbg) {
        prta(tag + "CDupd: MENU Update start");
      }
      loadMsgs();
      if (msgs == null) {
        prta("CDUpd: getMenuList msgs is null!");
      }
      prta(Util.clear(tmpsb).append("CDUpd: getMenuList nmsgs=").append(nmsgs).append(" ").
              append(msgs.length).append(" chlist=").append(channelList.size())
              .append(" ").append(msgs.length > 0 ? msgs[0] : ""));
      String channel;
      // run the query of channels we will claim to have, create runsb with string in MENU format.
      g.setTimeInMillis(now - daysBack * 86400000L);
      dropped = 0;
      latest = 0;
      latestSSR = 0;
      nssr = 0;
      nlastdata = 0;
      nldovr = 0;
      nnow = 0;
      ncurr = 0;
      nprimary = 0;

      Util.clear(menuDbg);
      menustate = 88;
      int size = 0;
      synchronized (channelList) {
        whoHasChannelList = 3;
        if (menuKeys.length < channelList.size() * 12 / 10) {
          menuKeys = new long[channelList.size() * 12 / 10];
        }
        size = channelList.size();
        menuKeys = channelList.keys(menuKeys);
      }
      whoHasChannelList = -3;
      Arrays.sort(menuKeys, 0, size);
      //TLongObjectIterator<ChannelList> itr = channelList.iterator();
      prta("GML: size=" + size);
      Util.clear(sb);
      for (int i = 0; i < size; i++) {
        //while(itr.hasNext()) {
        //itr.advance();
        //ChannelList ch = itr.value();
        ChannelList ch;
        synchronized (channelList) {
          whoHasChannelList = 35;
          ch = channelList.get(menuKeys[i]);
        }
        whoHasChannelList = -35;
        if (ch == null) {
          //MiniSeed.getSeednameFromLong(menuKeys[i], sbsum);
          Util.ungetHash(menuKeys[i], sbsum);
          prta(" got a null for key=" + menuKeys[i] + " value=" + sbsum);
          continue;
        }
        channel = ch.getChannel();
        if (menuRE != null) {    // Does it match the RE?
          if (!channel.matches(menuRE)) {
            continue;
          }
        }
        if (channel.length() < 12) {
          channel = (channel + "    ").substring(0, 12);
        }
        //end = now / 1000.;
        // If we have holdings, daysbackms is first holding, end is either 1)  UdpChannel value, 2) now or a day later than the last value
        // Start is measured time or days back which ever is later
        if (ch.getCreated() > g.getTimeInMillis()) {
          start = ch.getCreated() / 1000.;
        } else {
          start = g.getTimeInMillis() / 1000.;
        }
        // End comes from the UdpChannel if we are using one, if not it is from  the last data
        try {
          if (nmsgs > 0) {
            msgsState = 4;
            ChannelStatus chstat = null;    // This will contain the channel status if it is available
            synchronized (msgsMutex) {
              fake.setKey(channel);
              msgsState = 41;
              int pos = Arrays.binarySearch(msgs, 0, nmsgs, fake);
              if(pos >= 0) chstat = (ChannelStatus) msgs[pos];
            }
            msgsState = 42;
            //prta("CDUpd: ch="+channel+"! pos="+pos+" ch.last="+Util.ascdatetime2(ch.getLastData())+" msg="+(pos>=0?msgs[pos]:"Null"));
            if (chstat == null) {    // If its not in the SSR messages, use last data + 1 day or now
              end = Math.min(ch.getLastData() + 86400000, now) / 1000.;
              nnow++;
              if (dbg && channel.contains("BHZ")) {
                prta("NOSSR: " + channel + " " + Util.ascdatetime2(ch.getLastData()));
              }
            } // If this seedname is not currently coming in, estimate based on last data
            else {  // Its in the SSR, Use the SSR value
              end = (chstat.getNextPacketTime() - (long) (1000 / chstat.getRate() + 1)) / 1000.; // THis seedname is currently coming in
              if (dbg && channel.contains("BHZ")) {
                prta("SSR: " + channel + " msgs=" + Util.ascdatetime2(chstat.getNextPacketTime() - (long) (1000 / chstat.getRate() + 1)));
              }
              nssr++;
              if (end * 1000. > latestSSR) {
                latestSSR = (long) (1000. * end);
                //prta("Set latestSSr="+Util.ascdatetime2(latestSSR)+" "+pos);
              }
            }
            msgsState = 43;
            //prta("CHUpd: end1="+Util.ascdatetime2(end*1000));
            if (end < ch.getLastData() / 1000 - 10) {   // if the selectd end is older than last data, then override to last data
              end = ch.getLastData() / 1000;
              nldovr++;
              if (channel.charAt(7) != 'V') {
                menuDbg.append(channel).append(" ");
              }
              //prta("LDOVR: "+channel+" "+Util.ascdatetime2(end*1000));
            }
            // If we are running a memory cache, then look at its newest time
            msgsState = 44;
            if (QuerySpanCollection.isUp()) {
              msgsState = 45;
              QuerySpan span = QuerySpanCollection.getQuerySpan(ch.getChannelSB());
              if (span != null) {
                if (end < span.getLastTime() / 1000) {
                  end = span.getLastTime() / 1000.;
                }
              }
            }
            //prta("CHUpd: end="+Util.ascdatetime2(end *1000));

          } else {    // We do not have an SSR to UdpCHannel - last data is all we have so it+ one day or now
            if (now - ch.getLastData() < 86400000L) {
              end = Math.min(ch.getLastData() + 86400000, now) / 1000.;
              nnow++;
            } else {
              end = ch.getLastData() / 1000;
              nlastdata++;
              if (end > latest) {
                latest = (long) end;
              }

            }       // It must be older - give its real last data time
            /*else {
              nnow++;
              end=now/1000;
            }          // Assume its up to date*/
          }
          if (end < g.getTimeInMillis() / 1000) {
            if (dbg) {
              prt("menu drop " + channel + " end=" + end + " daysback=" + g.getTimeInMillis());
            }
            dropped++;
            continue;
          }
        } catch (RuntimeException e) {
          e.printStackTrace();
        }
        /*prta(fake+ " End for non-holdings pos="+pos+" ch.last="+Util.ascdate(ch.getLastData())+" "+Util.asctime(ch.getLastData())+
                (pos >=0?" pos="+Util.ascdate(((ChannelStatus) msgs[pos]).getNextPacketTime())+" "+
                Util.asctime(((ChannelStatus) msgs[pos]).getNextPacketTime()):""));*/
        if (channel.substring(0, 2).trim().length() != 0) {
          if (channel.equals("USRLMT BHZ00")) {
            prta(" " + channel + " " + Util.ascdatetime2(end * 1000) + " end=" + end);
          }
          sb.append(" 0 ").append(channel.substring(2, 7).trim()).append(" ").append(channel.substring(7, 10).trim()).append(" ").
                  append(channel.substring(0, 2).trim()).append(" ") // Kragness likes them this way (2014/07/16
                  //append(channel.substring(0,2).replaceAll(" ","-")).append(" ") // Since this was the code perhaps someone else likes them like this
                  .append((channel + "--").substring(10, 12).replaceAll(" ", "-")).append(" ").
                  append(Util.df23(start)).append(" ").append(Util.df23(end)).append(" s4 ");
        } else {
          prta("* Menu contains a bad network code " + channel + " " + ch);
        }
        if (channel.substring(7, 10).equals("BHZ") || channel.substring(7, 10).equals("HHZ")
                || channel.substring(7, 10).equals("MVZ") || channel.substring(7, 10).equals("SVZ")) {
          nprimary++;
          if (dnow - end < 600.) {
            ncurr++;
          }
        }
        
      }

      if (ncurr > maxcurr) {
        maxcurr = ncurr;
      }
      menustate = 89;
      sb.append("\n");
      synchronized (stats) {   // We use this so that the buf access cannot be caught in an inconsistent menustate
        menustate = 87;
        whoHasStats = 1;
        menuBufLength = sb.length();
        if (menuBufLength > menu.length) {
          prta("MENU.run() expanding bufs to " + menuBufLength * 2);
          menu = new byte[menuBufLength * 2];
          //menuchar = new char[menuBufLength*2];
        }
        Util.stringBuilderToBuf(sb, menu);
        //sb.getChars(0,menuBufLength, menuchar, 0);
        //for(int i=0; i<menuBufLength; i++) menu[i] = (byte) menuchar[i];
        //menu = runsb.toString().getBytes();  // This is shorter but creates strings that build up
      }
      menustate = -8;
      whoHasStats = -1;
      prta("CWS: get MENULIST time=" + (System.currentTimeMillis() - now) + " #ch=" + channelList.size()
              + " menuBufLength=" + menuBufLength + " dropped=" + dropped + " nmsg=" + nmsgs
              + " #ssr=" + nssr + " " + Util.ascdatetime2(latestSSR)
              + " #lastData=" + nlastdata + " " + Util.ascdatetime(latest * 1000)
              + " #ldrecent=" + nnow + " #ldovr=" + nldovr + " " + menuDbg + " curr=" + ncurr 
              + " maxcurr=" + maxcurr + " nprim=" + nprimary
      );
      if (nmsgs > 0 && now - latestSSR > 900000) {
        prta("CWS: **** SSR does not appear to be working - should we build another?");
        SendEvent.edgeSMEEvent("CWBWSSSRDwb", "CWBWS has access to UdpChannel but its not updating! ", this);
      }

    }
  }
  static final StringBuilder updateChannelSB = new StringBuilder(12);

  public static void updateChannel(String ch, double lat, double lon, double elev, StringBuilder longname) {
    //Util.prta("SSL:updateChannel "+ch+" "+lat+" "+lon+" "+elev+" "+longname);
    synchronized (stationList) {   // Note: also synchronizing updateChannelSB
      Util.clear(updateChannelSB).append(ch);
      updateChannelSB.delete(7, updateChannelSB.length());
      updateChannelSB.append("     ");
      StationList station = stationList.get(Util.getHashFromSeedname(updateChannelSB));
      if (station == null) {
        station = new StationList(ch, longname, lat, lon, elev, "GMT");
        stationList.put(Util.getHashFromSeedname(updateChannelSB), station);
      } else {
        station.updateChannel(ch, lat, lon, elev, longname);
      }
    }
  }

  /**
   * this class handles a single socket connection to the thread. It is a pool thread so it is
   * created, and waits to be assigned a socket. It runs until that socket is closed or potentially
   * timed out by the pool management in the outer class
   */
  private final class CWBWaveServerHandler extends Thread {

    private Semaphore semaphore = new Semaphore(1);
    private Socket s;
    private StringBuilder ip = new StringBuilder(16);
    private long ipkey;
    private long lastAction;
    private int ntrans;       // number of transactions
    private final int ithr;         // Thread number assigned to this CWBWaveServer handler - haded out at creation
    private StringBuilder ttag = new StringBuilder(20);
    private StringBuilder command = new StringBuilder(15);   // The command is the first part of the line
    private StringBuilder lastCommand = new StringBuilder(120);
    private int state;        // variable to track menustate of this handler
    private StringBuilder requestID = new StringBuilder(10); // The request ID is the 2nd part of the line
    private StringBuilder channel = new StringBuilder(12);
    private double start;       // starting time normally same as word[4]
    private double end;         // ending time normally same as word[5]
    //private double start2000;   // daysbackms in base 2000
    //private double end2000;     // end in base 2000
    private double duration;    // End - daysbackms in seconds
    private boolean dbgChan;     // for seedname oriented command, use this dbg to match single seedname
    private OutputStream out;
    private StringBuilder[] word = new StringBuilder[20];     // these are space separated parts after the command and requestID so third item on
    // Normally this is the Station[0], Channel[1], Network[2], Location[3], daysbackms[4] and end[5] times
    private StringBuilder location = new StringBuilder(2);
    private StringBuilder locationDashes = new StringBuilder(2);
    private long startMS;   // Time command started to run
    private final GregorianCalendar g = new GregorianCalendar();
    private final Timestamp tsnow;
    //private final Steim2Object steim2 = new Steim2Object();
    private EdgeQueryInternal queryint;   // Used by queryWithRaw() if on internal server
    private QuerySpan span;
    private final byte[] b = new byte[2048];   // scratch byte space for getSCNL, et al
    private final StringBuilder tmpsb = new StringBuilder(500);

    public int state() {
      return state;
    }

    public void terminate() {
      closeConnection();
    }

    /**
     * create a socket handler. If ss is not null, the socket will daysbackms handling right away
     *
     * @param ss A socket to use on creation, null to set up a waiting pool socket
     */
    public CWBWaveServerHandler(Socket ss) {
      ithr = thrcount++;
      Util.clear(ttag).append(tag).append("[").append(ithr).append("]-").append(this.getId()).append("{}");
      try {
        semaphore.acquire();
      } catch (InterruptedException expected) {
      }
      assignSocket(ss, Thread.NORM_PRIORITY);
      for (int i = 0; i < 20; i++) {
        word[i] = new StringBuilder(30);
      }
      tsnow = new Timestamp(10000L);
      tsnow.setTime(System.currentTimeMillis());
      lastAction = System.currentTimeMillis();
      setDaemon(true);
      start();
    }
    private final StringBuilder tmpsb2 = new StringBuilder(100);

    @Override
    public String toString() {
      return toStringBuilder(null).toString();
    }

    public StringBuilder toStringBuilder(StringBuilder tmp) {
      StringBuilder sb = tmp;
      if (sb == null) {
        sb = Util.clear(tmpsb2);
      }
      sb.append(ttag).append("MDH: ");
      if (s == null) {
        sb.append("not connected");
      } else {
        sb.append(s.getInetAddress()).append(" lastip=").append(ip).append(" idle=").
                append((System.currentTimeMillis() - lastAction) / 1000).
                append("s #trans=").append(ntrans).append(" cmd=").append(lastCommand).
                append(" state=").append(state).append(" ");
        menuThread.toStringBuilder(sb).append(" alive=").append(isAlive()).
                append(span != null ? "Span: len=" + span.getData().length : "");
      }
      return sb;
    }

    public final void assignSocket(Socket ss, int priority) {
      lastAction = System.currentTimeMillis();
      setPriority(priority);
      s = ss;
      if (s != null) {
        if (s.isClosed()) {
          s = null;
        }
      }
      if (s == null) {
        Util.clear(ip).append("null");
        ipkey = -1;
      } else {
        Util.clear(ip).append(s.getInetAddress().toString());
        ipkey = s.getInetAddress().hashCode();
        try {
          s.setTcpNoDelay(true);
        } catch (SocketException e) {
          prt(ttag + "Socket error setting TCPNoDelay e=" + e);
          e.printStackTrace(getPrintStream());
        }
        ttag.delete(ttag.indexOf("{"), ttag.length());
        try {
          if (s != null) {
            ttag.append("{");
            if (s.getInetAddress() != null) {
              ttag.append(s.getInetAddress().toString().substring(1));
            } else {
              ttag.append("NULL");
            }
            ttag.append(":").append(s.getPort()).append("}");
          } else {
            ttag.append("{null}");
          }
        } catch (RuntimeException e) {
          prta("Something is null ttag=" + ttag + " s=" + s);
          ttag.append("{null}");
        }
        semaphore.release(1);

      }
      //interrupt();    // wake it up from sleeping
    }

    /**
     * return the socket open on this thread. If null, then no socket is assigned.
     *
     * @return The open socket or null if not assigned.
     */
    public Socket getSocket() {
      return s;
    }

    /**
     * get the time in millis of the last usage of this socket
     *
     * @return the time in millis
     */
    public long getLastActionMillis() {
      return lastAction;
    }

    /**
     * close the connection if it is open
     */
    public synchronized void closeConnection() {
      if (s == null) {
        return;
      }
      if (!s.isClosed()) {
        try {
          s.close();
        } catch (IOException expected) {
        }
      }
      usedThreads--;
      s = null;
    }

    private void doGetChannels(StringBuilder[] word) throws IOException {
      prta(Util.clear(tmpsb).append(ttag).append("Starting do getChannels ").append(word[0]).append(" whohasch=").append(whoHasChannelList).
              append(" WWSChanState=").append(menuThread.getWWSChannelListState()));
      try {
        if (System.currentTimeMillis() - channelUpdate > 50000) {
          prta(Util.clear(tmpsb).append(ttag).append(" need to update WWSChannelList()"));
          menuThread.getWWSChannelList();
          if (!quiet) {
            prta(Util.clear(tmpsb).append("Got the WWSChannelList updated"));
          }
        }
        if (Util.stringBuilderEqual(word[0], "METADATA")) {
          //prta(ttag+"Need to sync on getChannelMetadataBytes");
          synchronized (getChannelMetadataBytesMutex) {
            whoHasChannelList = 12;
            //prta(ttag+"got GetChannelMetadataBytes len="+getChannelMetadataLength+" channelLIstSize="+channelListSize);
            Util.clear(tmpsb).append(requestID).append(" ").append(channelListSize).append("\n");
            Util.stringBuilderToBuf(tmpsb, b);
            out.write(b, 0, tmpsb.length());
            //prta(new String(getChannelMetadataBytes, 0, getChannelMetadataLength));
            int written = writeBytes(getChannelMetadataBytes, 0, getChannelMetadataLength, out);
            //out.write(getChannelMetadataBytes, 0, getChannelMetadataLength);
            //prta(ttag+"out of write metadata");
            stats.get(ipkey).incGetMetadata(System.currentTimeMillis() - startMS, getChannelMetadataLength);
            if (!quiet) {
              prta(Util.clear(tmpsb).append(ttag).append(" METADATA getChannels() ").append(getChannelMetadataLength).append(" bytes"));
            }
          }
          whoHasChannelList = -12;
        } else {
          //prta(ttag+"need sync on getChannelBYtesMutex");
          synchronized (getChannelBytesMutex) {
            //prta(ttag+" got getChannelBytesMutex len="+getChannelLength+" channelLIstSize="+channelListSize);
            whoHasChannelList = 13;
            Util.clear(tmpsb).append(requestID).append(" ").append(channelListSize).append("\n");
            write(tmpsb);
            out.write(getChannelBytes, 0, getChannelLength);
            stats.get(ipkey).incGetChannels(System.currentTimeMillis() - startMS, getChannelLength);
            if (!quiet) {
              prta(Util.clear(tmpsb).append(ttag).append(" CHANNELS getChannels() ").append(getChannelLength).append(" bytes"));
            }
          }
          whoHasChannelList = -13;
        }
        //}
        //whoHasChannelList=-4;

      } catch (IOException e) {
        whoHasChannelList = -4;
        throw e;
      } catch (RuntimeException e) {
        prta(Util.clear(tmpsb).append("CWS:MenuList.getdoGetChannel() error e=").append(e));
        e.printStackTrace(getPrintStream());
        SendEvent.debugSMEEvent("CWBWgtChRn", "Runtime errror doGetChannels()", this);
        whoHasChannelList = -4;
      }
      prta(Util.clear(tmpsb).append(ttag).append(" getChannels() done"));
    }

    private void doMetaData() throws IOException {
      if (word.length == 0) {
        prta("DoMetaData: no metadata type found!");
      } else if (Util.stringBuilderEqual(word[0], "CHANNEL")) {
        synchronized (channelBytesMutex) { // channelList is synchronized in getWWSChannelList
          try {
            whoHasChannelList = 5;
            if (System.currentTimeMillis() - channelUpdate > 10000) {
              menuThread.getWWSChannelList();
            }
            Util.clear(tmpsb).append(requestID).append(" ").append(channelListSize).append("\n");
            write(tmpsb);
            out.write(channelbytes, 0, channelLength);
            stats.get(ipkey).incGetMetadata(System.currentTimeMillis() - startMS, channelLength);
            if (!quiet) {
              prta(Util.clear(tmpsb).append(ttag).append(" CHANNEL getMetaData() ").append(channelLength).append(" bytes"));
            }
          } catch (IOException e) {
            whoHasChannelList = -5;
            throw e;
          }
        }
        whoHasChannelList = -5;
      } else if (Util.stringBuilderEqual(word[0], "INSTRUMENT")) {
        synchronized (stationList) {
          whoHasStationList = 2;
          if (System.currentTimeMillis() - instrumentUpdate > 300000) {
            menuThread.getWWSInstrumentList();
          }
          Util.clear(tmpsb).append(requestID).append(" ").append(instrumentListSize).append("\n");
          write(tmpsb);
          //out.write((requestID+" "+instrumentListSize+"\n").getBytes());
          out.write(instrument, 0, instrumentLength);
          stats.get(ipkey).incGetMetadata(System.currentTimeMillis() - startMS, instrumentLength);
          if (!quiet) {
            prta(Util.clear(tmpsb).append(ttag).append(" INSTRUMENT getMetaData() ").append(instrumentLength).append(" bytes"));
          }
        }
        whoHasStationList = -2;
      } else {
        prta(Util.clear(tmpsb).append("DoMetaData: unknown type of metadata=").append(word[0]));
      }
    }

    @Override
    public void run() {
      String line = "";
      setPriority(getPriority() + 1);
      while (!terminate) {
        ArrayList<TraceBuf> tsb = new ArrayList<>(100);
        ArrayList<MiniSeed> msb = new ArrayList<>(100);
        StringBuilder runsb = new StringBuilder(100);
        while (s == null) {
          try {
            semaphore.acquire(1);
            if (dbg) {
              prta(Util.clear(runsb).append("Semaphore acquired s=").append(s));
            }
          } catch (InterruptedException expected) {
          }
        }
        state = 2;
        try {
          if (s == null) {
            continue;
          }
          if (s.isClosed()) {
            continue;
          }
          BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream()));
          while (!terminate) {
            // body of Handler goes here
            while ((line = in.readLine()) != null) {
              state = 3;
              startMS = System.currentTimeMillis();
              try {
                ntrans++;
                lastAction = System.currentTimeMillis();
                line = line.replaceAll("  ", " ");
                line = line.replaceAll("  ", " ");
                line = line.replaceAll("  ", " ");
                dbgChan = false;
                if (!quiet) {
                  prta(Util.clear(runsb).append(ttag).append(" CWSH : line=").append(line).
                          append("| msp.used=").append(EdgeQueryClient.getMiniSeedPool().getUsedList().size()).
                          append(" QMSP used=").append(EdgeQuery.getMiniSeedPool().getUsedList().size()).
                          append(" TBPool.used=").append(traceBufPool.getUsed()));
                }
                int pos = line.indexOf(":");
                if (pos <= 0) {
                  pos = line.indexOf(" ");
                  if (pos <= 0) {
                    if (line.contains("VERSION") || line.contains("STATUS")) {
                      pos = line.length();
                      line = line + " ";
                    } else {
                      prta(Util.clear(runsb).append(ttag).append("CWSH: Illegal command - no colon or space! line=").append(line));
                      continue;
                    }
                  }
                }
                totmsgs++;
                Util.clear(command).append(line.substring(0, pos));
                Util.clear(lastCommand).append(line);
                String[] parts = line.substring(pos + 1).trim().split("\\s");
                Util.clear(requestID).append(parts[0]);
                if (parts.length > 1) {
                  for (int i = 0; i < 20; i++) {
                    Util.clear(word[i]);
                  }
                  // copy the parts after the first one into the word array, change first 4 to change hyphen to space
                  for (int i = 1; i < parts.length; i++) {
                    Util.clear(word[i - 1]).append(i < 5 ? parts[i].replaceAll("-", " ").trim() : parts[i].trim());
                  }
                }
                out = s.getOutputStream();
                if (Util.stringBuilderEqual(command, "GETMETADATA")) {
                  doMetaData();
                } else if (Util.stringBuilderEqual(command, "GETCHANNELS")) {
                  state = 90;
                  doGetChannels(word);
                  prta(Util.clear(runsb).append(ttag).append("doGetChannels completed ").append(word[0]));
                  state = 91;
                } else if (Util.stringBuilderEqual(command, "VERSION")) {
                  if (dbg) {
                    prta(Util.clear(runsb).append(ttag).append("Do version command "));
                  }
                  Util.clear(runsb).append("PROTOCOL_VERSION: 3\n");
                  prta(runsb);
                  write(runsb);
                  //out.write("PROTOCOL_VERSION: 3\n".getBytes());

                } else if (Util.stringBuilderEqual(command, "STATUS")) {
                  Util.clear(runsb);
                  int lines = 0;
                  runsb.append(String.format("Connection count: %d\n", maxThreads));
                  lines++;

                  runsb.append(String.format("Median data age: %s\n", 10.));
                  lines++;
                  runsb.insert(0, "GC: " + lines + "\n");
                  write(runsb);
                  //out.write(("GC: " + lines + "\n" + runsb.toString()).getBytes());                 
                } else if (Util.stringBuilderEqual(command, "UPDATEMDS")) {
                  doMDSNow = true;
                } else if (Util.stringBuilderEqual(command, "EXITNOW")) {
                  prta(Util.clear(runsb).append(ttag).append(" asking for an EXIT - do it now"));
                  Util.exit(102);
                } else if (Util.stringBuilderEqual(command, "MENUSUM")) {
                  Util.clear(runsb).append(menuThread.getSummary()).
                          append((QuerySpanCollection.getQuerySpanCollection() == null ? "" : QuerySpanCollection.getQuerySpanCollection().getSummary()));
                  Util.stringBuilderReplaceAll(runsb, "\n", " ");
                  runsb.append("\n");
                  write(runsb);
                  //out.write((menuThread.getSummary().toString().replaceAll("\n"," ")+"\n").getBytes());
                } else if (Util.stringBuilderEqual(command, "MENU")) {
                  state = 4;
                  long menuElapse = System.currentTimeMillis();
                  if (word != null) {
                    if (!Util.stringBuilderEqual(word[0], "SCNL")) {
                      prta(Util.clear(runsb).append(ttag).append("Not a SCNL abandon"));
                      b[0] = '\n';
                      out.write(b, 0, 1);
                    } else {
                      state = 44;
                      //requestID = requestID.replaceAll("ID","00");
                      Util.clear(runsb).append(requestID).append(" ");
                      write(runsb);
                      //out.write((requestID+" ").getBytes());

                      if (System.currentTimeMillis() - menuLastUpdate > 20000 && whoHasChannelList < 0) {
                        if (dbg || !quiet) {
                          prta(Util.clear(runsb).append(ttag).append(" need to getMenuList() whohas=").append(whoHasChannelList));
                        }
                        state = 45;
                        menuThread.getMenuList();
                        state = 46;
                        if (dbg || !quiet) {
                          prta(Util.clear(runsb).append(ttag).append(" getMenuList done."));
                        }
                      }
                      int nsent = 0;
                      synchronized (stats) {   // Insure the menu cannot be changed during transmission
                        whoHasStats = 2;
                        try {
                          state = 47;
                          /*while(nsent < menuBufLength) {
                            int len = 10240;
                            if(menuBufLength - nsent < len) len = menuBufLength - nsent;
                            out.write(menu, nsent, len);
                            nsent += len;
                          }*/
                          out.write(menu, 0, menuBufLength);
                        } catch (IOException e) {
                          if (e != null) {
                            if (e.toString().contains("Connection reset")) {
                              prta(Util.clear(runsb).append(ttag).
                                      append("MENU: connection was reset before end of write (Hydra?)? nsent=").
                                      append(nsent).append(" of ").append(menuBufLength).append(" elapse=").
                                      append(System.currentTimeMillis() - menuElapse));
                              line = null;
                            } else {
                              prta(Util.clear(runsb).append(ttag).append("MENU: connect error=").append(e));
                            }
                          }
                        }
                        state = 48;
                        stats.get(ipkey).incMenu(System.currentTimeMillis() - startMS, menuBufLength);
                        if (!quiet) {
                          prta(Util.clear(runsb).append(ttag).append("MENU: sent ").
                                  append(menuBufLength).append(" bytes elapse=").
                                  append(System.currentTimeMillis() - menuElapse));
                        }
                        //prta((dbg?new String(menu, 0, menuBufLength):new String(menu, 0, 100)));
                      }
                      state = 49;
                      whoHasStats = -2;
                    }
                  } else {
                    prta(Util.clear(runsb).append(ttag).append("Not a MENU SCNL abandon"));
                    b[0] = '\n';
                    out.write(b, 0, 1);
                  }
                } else if (Util.stringBuilderEqual(command, "MENUPIN")) {
                  state = 5;
                  prta(Util.clear(runsb).append(ttag).append("CWSH: **** MENUPIN is not implemented!"));
                } else if (Util.stringBuilderEqual(command, "MENUSCNL")) {
                  state = 6;
                  Util.clear(locationDashes).append(word[3]);
                  if (locationDashes.length() < 2) {
                    for (int i = locationDashes.length(); i < 2; i++) {
                      locationDashes.append("-");
                    }
                  }
                  Util.clear(location).append(locationDashes);
                  Util.stringBuilderReplaceAll(location, "-", " ");
                  Util.clear(channel).append(Util.stringBuilderRightPad(word[2], 2)).
                          append(Util.stringBuilderRightPad(word[0], 5)).
                          append(Util.stringBuilderRightPad(word[1], 3)).
                          append(Util.stringBuilderRightPad(word[3], 2));
                  Util.trim(word[0]);
                  Util.trim(word[1]);
                  Util.trim(word[2]);
                  Util.trim(word[3]);
                  Util.clear(runsb).append(requestID).append(" ");
                  write(runsb);
                  //out.write((requestID+" ").getBytes());
                  int len = menuThread.getMenuSCNL(channel, b);
                  b[len] = '\n';
                  out.write(b, 0, len + 1);
                  stats.get(ipkey).incMenuSCNL(System.currentTimeMillis() - startMS, len);
                  if (!quiet) {
                    prt(Util.clear(runsb).append(ttag).append("Do MENUSCNL for ").append(channel).append(" return ").append(len));
                  }
                } else if (Util.stringBuilderEqual(command, "METADATA")) {
                  prta(Util.clear(runsb).append(ttag).append("CWSH: **** METADATA is not implemented!"));

                } else if (Util.stringBuilderEqual(command, "GETPIN")) {
                  prta(ttag + "CWSH: **** GETPIN is not implemented!");
                } else if (Util.stringBuilderEqual(command, "GETSCNLHELIRAW")) {
                  Util.clear(locationDashes).append(word[3]);
                  if (locationDashes.length() < 2) {
                    for (int i = locationDashes.length(); i < 2; i++) {
                      locationDashes.append(" ");
                    }
                  }
                  Util.clear(location).append(locationDashes);
                  Util.stringBuilderReplaceAll(location, "-", " ");
                  Util.clear(channel).append(Util.stringBuilderRightPad(word[2], 2)).
                          append(Util.stringBuilderRightPad(word[0], 5)).
                          append(Util.stringBuilderRightPad(word[1], 3)).
                          append(Util.stringBuilderRightPad(word[3], 2));
                  Util.trim(word[0]);
                  Util.trim(word[1]);
                  Util.trim(word[2]);
                  Util.trim(word[3]);
                  long key = Util.getHashFromSeedname(channel) + s.getRemoteSocketAddress().hashCode() + s.getPort() * Integer.MAX_VALUE;
                  HeliFilterIIR f;
                  synchronized (filters) {
                    f = filters.get(key);
                    if (f == null) {
                      f = new HeliFilterIIR(channel, s.getRemoteSocketAddress().toString().substring(1), thisThread);
                      if (dbg) {
                        f.setDebug(dbg);
                      }
                      Channel c = EdgeChannelServer.getChannelNoTraceback(channel);
                      Util.clear(runsb).append(ttag).append("CHSH: setup filter for ").append(channel).
                              append(" key=").append(key).append(" c=");
                      if (c == null) {
                        runsb.append("Null");
                      } else {
                        runsb.append(" flgs=").append(c.getFlags()).append(" sendto=").append(c.getSendtoMask()).
                                append(" ").append(c.getRate()).append(" ").append(c.getLastData());
                      }
                      Util.prta(runsb);
                      f.setDebugChannel(dbgSeedname);
                      filters.put(key, f);
                    }
                  }
                  int nb = f.doSCNLHeliRaw(requestID, command, word, host, out);
                  stats.get(ipkey).incHeli(System.currentTimeMillis() - startMS, nb);
                  if (!quiet) {
                    prta(Util.clear(runsb).append(ttag).append(" End GETSCNLHELIRAW ").append(channel).append(" ").append(nb).append(" bytes returned"));
                  }
                } else if (Util.stringBuilderEqual(command, "GETSCNL") || Util.stringBuilderEqual(command, "GETSCN")
                        || Util.stringBuilderEqual(command, "GETSCNLRAW") || Util.stringBuilderEqual(command, "GETSCNRAW")
                        || Util.stringBuilderEqual(command, "GETWAVERAW")) {
                  try {
                    state = 7;
                    if (!Util.stringBuilderEqual(command, "GETWAVERAW")) {
                      Util.stringBuilderToBuf(requestID, b);
                      out.write(b, 0, requestID.length());
                    }

                    // This is a data fetch, parse out the daysbackms,end make a command line for CWBQuery and run the query
                    // word [0] station, word[1] seedname, word[2] network, word[3] location,
                    //word[4] daysbackms time, word[5] endtime, word[6] fill string 
                    if (Util.stringBuilderEqual(command, "GETSCNRAW") || Util.stringBuilderEqual(command, "GETSCN")) {
                      start = Double.parseDouble(word[3].toString());
                      end = Double.parseDouble(word[4].toString());
                      Util.clear(location).append("  ");
                      Util.clear(locationDashes).append("--");
                    } else {
                      start = Double.parseDouble(word[4].toString());
                      end = Double.parseDouble(word[5].toString());
                      Util.clear(location).append(word[3]);
                      if (location.length() < 2) {
                        for (int i = location.length(); i < 2; i++) {
                          location.append(" ");
                        }
                      }
                      Util.clear(locationDashes).append(location);
                      Util.stringBuilderReplaceAll(locationDashes, " ", "-");
                    }
                    Util.clear(channel).append(Util.stringBuilderRightPad(word[2], 2)).
                            append(Util.stringBuilderRightPad(word[0], 5)).
                            append(Util.stringBuilderRightPad(word[1], 3)).
                            append(location);
                    dbgChan = Util.stringBuilderEqual(channel, dbgSeedname);
                    //prta(Util.clear(runsb).append(ttag).append("dbgChan=").
                    //        append(dbgChan).append("|").append(channel).append("|").append(dbgSeedname).append("|").
                    //        append(channel.length()).append(" ").append(dbgSeedname.length()));
                    Util.trim(word[0]);
                    Util.trim(word[1]);
                    Util.trim(word[2]);
                    Util.trim(word[3]);
                    // Special case GETWAVERAW are often abusive, so check to see if its been long enough and ignore if not
                    // also this gives time in epoch since 2000, so convert it to normal 1970 epoch
                    if (Util.stringBuilderEqual(command, "GETWAVERAW")) {
                      state = 81;
                      g.setTimeInMillis((long) ((start + year2000sec) * 1000.));  // given time is secs since 2000
                      prta(Util.clear(runsb).append("GETWAVERAW set time start=").append(start).
                              append(" yr200sec=").append(year2000sec).append(" g=").append(Util.ascdatetime2(g)));
                      LastRawData last = lastGetRawMS.get(Util.getHashFromSeedname(channel));
                      long lastms;
                      long avgms;
                      if (last == null) {
                        lastms = 0;
                        avgms = 0;
                      } else {
                        lastms = last.getMS();
                        avgms = last.getAvgMS();
                      }

                      String s2 = Util.ascdate() + " " + Util.asctime() + " last data=" + Util.ascdate(lastms) + " " + Util.asctime2(lastms) + " avg=" + avgms;
                      if (System.currentTimeMillis() < lastms + avgms + 3000 && lastms - g.getTimeInMillis() < 300000) {
                        Util.clear(runsb).append(requestID).append(" 0\n");
                        write(runsb);
                        //out.write((requestID+" 0\n").getBytes());
                        if (dbg || dbgChan) {
                          prta(Util.clear(runsb).append(ttag).append(channel).append(" BLOW off request ").append(System.currentTimeMillis() - lastms).append("avg=").append(avgms).append(" s=").append(s2));
                        }
                        continue;
                      }
                    } else {
                      g.setTimeInMillis((long) (start * 1000.)); // Other request are already versus 1970
                    }
                    duration = end - start + 1.;              // alway do one second more
                    if (duration < 10) {
                      duration = 10;
                    }
                    if (duration > 63244800.) { // is this request for more than 2 years time- abort it!
                      prta(Util.clear(runsb).append(ttag).append(" *** Query duration is way to long - shorten to 2 years! ").
                              append(channel).append(" ").append(duration / 86400.).append(" days"));
                      duration = 63244800.;
                      end = start + duration;
                    }
                    // If this has a little h or little b channel, reject it.
                    if (channel.charAt(7) >= 'a') {
                      queryWithRaw(msb, tsb);
                      prta(Util.clear(runsb).append(ttag).append(" little query ret tsb=").append(tsb.size()));
                    } else {
                      long lastDataMillis = (long) (menuThread.getEnd(channel) * 1000.);
                      state = 24;
                      if (lastDataMillis + 864000000L <= g.getTimeInMillis() && !queryAllCases) {  // All data is before the requested time less 10 days do not check on disk
                        state = 25;
                        if (!quiet || dbgChan) {
                          prta(Util.clear(runsb).append(ttag).append(channel).append(" data request ").append(command).
                                  append(" return:").append(Util.ascdatetime2(g)).append(" later than ").
                                  append(Util.ascdatetime2(lastDataMillis)).append(" FR " + "0 ").
                                  append(word[0]).append(" ").append(word[1]).append(" ").append(word[2]).
                                  append(" ").append(word[3]).append(" FR ?? ").append(Util.df23((lastDataMillis / 1000.))));
                        }
                      } else {      // Time looks possible, check for restricted
                        if (!allowrestricted) {
                          Channel c = EdgeChannelServer.getChannelNoTraceback(channel);
                          if (c != null) {
                            if (EdgeChannelServer.isChannelNotPublic(c)) {
                              prta(Util.clear(runsb).append(ttag).append(" request for restricted channel rejected!=").append(channel));
                            } else {
                              queryWithRaw(msb, tsb);      // Not restricted, get MiniSeed and tracefuls in this interval
                            }
                          } else {
                            prta(Util.clear(runsb).append(ttag).append(" ** Cannot check for restricted on channel ").append(channel).append(" c=").append(c));
                            queryWithRaw(msb, tsb);
                          }
                        } else {
                          queryWithRaw(msb, tsb);        // Get MiniSeed and tracebufs for this query interval
                        }
                      }
                    }

                    if (Util.stringBuilderEqual(command, "GETWAVERAW")) {
                      state = 84;
                      doGetWaveRaw(msb, tsb);
                    } else if (Util.stringBuilderEqual(command, "GETSCNL") || Util.stringBuilderEqual(command, "GETSCN")) {
                      state = 85;
                      doGetSCNL(command, msb, tsb);
                    } else if (Util.stringBuilderEqual(command, "GETSCNLRAW") || Util.stringBuilderEqual(command, "GETSCNRAW")) {
                      state = 86;
                      doSCNLRaw2(command, msb, tsb);
                    }
                  } catch (IOException e) {
                    if (e.toString().contains("Broken pipe")) {
                      prta(Util.clear(runsb).append(ttag).append("Broken pipe found doing SCN/RAW waves"));
                      line = null;
                      //if(!quiet) e.printStackTrace(getPrintStream());
                    } else if (e.toString().contains("ocket close")) {
                      prta(Util.clear(runsb).append(ttag).append(" Socket closed"));
                    } else if (e.toString().contains("onnection reset")) {
                      prta(Util.clear(runsb).append(ttag).append(" Socket reset"));
                    } else {
                      prta(Util.clear(runsb).append(ttag).append("Got IO exception e=").append(e));
                      e.printStackTrace();
                    }
                    state = 8;
                    freeBlocks(msb, tsb);
                    break;
                  } catch (NumberFormatException e) {
                    e.printStackTrace(getPrintStream());
                    freeBlocks(msb, tsb);
                    break;
                  } catch (OutOfMemoryError e) {
                    prta(Util.clear(runsb).append(ttag).append("CWSH: got out of memory - try to terminate!"));
                    prt(Util.getThreadsString());
                    Util.exit(101);
                  } catch (RuntimeException e) {
                    if (e.toString().indexOf("OutOfMemory") > 0) {
                      prta(Util.clear(runsb).append(ttag).append("CWSH: got out of memory - try to terminate!"));
                      prt(Util.getThreadsString());
                      Util.exit(101);
                    } else {
                      prta(Util.clear(runsb).append(ttag).append("CWSH: got runtime e=").append(e));
                      e.printStackTrace(getPrintStream());
                    }
                    freeBlocks(msb, tsb);
                    break;
                  }
                  freeBlocks(msb, tsb);
                } else {
                  state = 9;
                  prta(Util.clear(runsb).append(ttag).append("CWSH: **** Unknown command ").append(command).append("|"));
                  SendEvent.debugSMEEvent("CWBWSUnknCmd", "Command: " + command + "|", this);
                }
                //if(s != null) s.close();
                //prta(ttag+" CWSH: Go to readLine() "+traceBufPool+" "+EdgeQueryClient.getMiniSeedPool());
                state = 11;
              } catch (InterruptedIOException e) {
                prta(Util.clear(runsb).append("IOInterrupted break connection e=").append(e));
                break;
              } catch (IOException e) {
                if (e.toString().contains("Broken pipe")) {
                  prta(Util.clear(runsb).append(ttag).append("Broken pipe exception found out of waves2"));
                  if (!quiet) {
                    e.printStackTrace(getPrintStream());
                  }
                } else if (e.toString().contains("ocket close")) {
                  prta(Util.clear(runsb).append(ttag).append(" Socket closed2"));
                } else if (e.toString().contains("onnection reset")) {
                  prta(Util.clear(runsb).append(ttag).append(" Socket reset2 Exception"));
                  if (!quiet) {
                    e.printStackTrace(getPrintStream());
                  }
                } else {
                  prta(Util.clear(runsb).append(ttag).append("Got IO exception 2 e=").append(e));
                  e.printStackTrace();
                }
                // All error conditions require us to close the sock and leave
                line = null;    // This causes outer infinte loop
                break;
              } catch (RuntimeException e) {
                if (e.toString().indexOf("OutOfMemory") > 0) {
                  prta(Util.clear(runsb).append("CWSH: got out of memory - try to terminate!2"));
                  SendEvent.edgeSMEEvent("OutOfMemory", "CWBWaveServ out of memory - exiting in panic", this);
                  prt(Util.getThreadsString());
                  Util.exit(101);
                }
                prt(Util.clear(runsb).append(ttag).append(" CWS: Handler RuntimeException caught e=").append(e).
                        append(" msg=").append(e.getMessage() == null ? "null" : e.getMessage()).append(s));
                e.printStackTrace(getPrintStream());
                try {
                  Util.clear(runsb).append("* Your query caused a RuntimeError e=").append(e.toString());
                  Util.stringBuilderReplaceAll(runsb, "\n", "\n*");
                  runsb.append("\n").append("* <EOR>\n");
                  write(runsb);
                  //s.getOutputStream().write(("* Your query caused a RuntimeError e="+e.toString().replaceAll("\n","\n* ")+"\n").getBytes());
                  //s.getOutputStream().write("* <EOR>\n".getBytes());
                  line = null;
                  break;      // close this socket
                } catch (IOException expected) {

                }
              }
            }// End of while reading a line

            state = 10;
            if (line == null) {
              if (!quiet) {
                prta(Util.clear(runsb).append(ttag).append(" connection done - go to idle "));
              }
              state = 33;
              //if(in != null) 
              in.close();
              closeConnection();
              break;    // need to leave infinite loop on reading lines and go to wait for new socket
            }
          }// Inner Infinite loop

        } catch (IOException e) {
          if (e.toString().contains("Broken pipe")) {
            prta(Util.clear(runsb).append(ttag).append(":").append(ithr).append(" Broken pipe CWS cmd - abort"));
            if (!quiet) {
              e.printStackTrace(getPrintStream());
            }
          } else if (e.toString().contains("Socket closed")) {
            prta(Util.clear(runsb).append(ttag).append(": Socket closed CWS cmd - abort"));
          } else if (e.toString().contains("Socket is closed")) {
            prta(Util.clear(runsb).append(ttag).append(": Socket closed CWS cmd - abort"));
          } else if (e.toString().contains("Connection reset")) {
            prta(Util.clear(runsb).append(ttag).append(": Socket reset CWS cmd - abort"));
          } else {
            Util.SocketIOErrorPrint(e, ttag + ": in Reading CWS cmd", getPrintStream());
            e.printStackTrace(getPrintStream());
          }
          try {
            sleep(100);
          } catch (InterruptedException expected) {
          }    // this might be a close in progress, let it finish before checking
          if (s != null) {
            if (!s.isClosed()) {
              try {
                s.close();
              } catch (IOException expected) {
                prta(Util.clear(runsb).append(ttag).append(" ").append(s).
                        append("CWSH: IOError on socket. close it=").append(s.isClosed()).append(" e=").append(e));
                e.printStackTrace(getPrintStream());
              }
            }
          }
        } catch (RuntimeException e) {
          if (e.toString().indexOf("OutOfMemory") > 0) {
            prta(Util.clear(runsb).append(tag).append("CWSH: got out of memory - try to terminate!"));
            prt(Util.getThreadsString());
            Util.exit(101);
          }
          prt(Util.clear(runsb).append(ttag).append(" CWS: Handler RuntimeException caught e=").append(e).append(" msg=").append(e.getMessage() == null ? "null" : e.getMessage()).append(s));
          e.printStackTrace(getPrintStream());
          try {
            Util.clear(runsb).append("* Your query caused a RuntimeError e2=").append(e.toString());
            Util.stringBuilderReplaceAll(runsb, "\n", "\n*");
            runsb.append("\n").append("* <EOR>\n");
            write(runsb);
            //s.getOutputStream().write(("* Your query caused a RuntimeError e="+e.toString().replaceAll("\n","\n* ")+"\n").getBytes());
            //s.getOutputStream().write("* <EOR>\n".getBytes());
          } catch (IOException expected) {

          }
        }
        freeBlocks(msb, tsb);
        state = 11;
        closeConnection();
        if (terminate) {
          break;
        }
      }   // For ever outer infinite loop.
      state = 13;
      prta(ttag + " CWSH: exiting terminate=" + terminate);
    }     // end of run

    /**
     * free up all of the pool space consumed by a request to queryWithRaw(). The MiniSeedPool used
     * for freeing depends on whether this method is using the client/server internet method or
     * whether this is connected only to the local data via one QueryMom (designated by whether host
     * == null). There is only one TraceBufPool created in the CWBWaveServer.
     *
     * @param msb The ArrayList of MiniSeed blocks from a disk based request method
     * @param tsb The ArrayList of TraceBuf blocks from the QuerySpan.makeTraceBufs()
     */
    private void freeBlocks(ArrayList<MiniSeed> msb, ArrayList<TraceBuf> tsb) {
      MiniSeedPool msp;
      if (host == null) {
        msp = EdgeQuery.getMiniSeedPool();   // We are getting blocks over the internet
      } else {
        msp = EdgeQueryClient.getMiniSeedPool();         // we are using the internal (part of a QueryMom)
      }
      TimeSeriesBlock ts;
      for (int i = tsb.size() - 1; i >= 0; i--) {
        traceBufPool.free(tsb.get(i));
        tsb.remove(i);
      }
      for (int i = msb.size() - 1; i >= 0; i--) {
        msp.free(msb.get(i));
        msb.remove(i);
      }
    }

    /**
     * This is the main way to query data when it is needed. The ArrayList<MiniSeed>
     * contains data which came from disk based queries and the ArrayList<TraceBuf> contains data
     * which came from the QuerySpan (RAM based) data. They may overlap so the user need to trim any
     * MiniSeed blocks the the daysbackms time of the first TraceBuf. The user must also call
     * freeBlocks(mss, tsb) when done with the results to free these blocks from their respective
     * pools.
     *
     * @param mss An ArryList<MiniSeed> to receive data blocks from a query to disk
     * @param tsb An ArrayList<TraceBuf> to contain blocks from the QuerySpan memory based buffers
     */
    private void queryWithRaw(ArrayList<MiniSeed> mss, ArrayList<TraceBuf> tsb) {
      // Save the daysbackms time, and duration so this can be split up into two requests (disk and RAM)
      //if(channel.charAt(7) >= 'a') return;      // Its a NEIC little b or h channels
      long st = g.getTimeInMillis();
      long endat = st + (long) (duration * 1000. + 1000.001);   // set end time for duration + 1 second to be sure
      QuerySpanThread qscthr = QuerySpanCollection.getQuerySpanThr(channel);
      QuerySpan qscspan = null;
      if (qscthr != null) {
        qscspan = qscthr.getQuerySpan();
      }
      //prta(ttag+"queryWIthRaw: "+channel+"| span="+qscspan);
      if (qscspan != null && qscthr != null) {
        /*        if(!qscthr.isReady()) {   // If we are starting up and the qscthr is still building, skip checking in memory
          // delay no longer than 3 seconds
          if(st > System.currentTimeMillis() - qscspan.getDuration()*1000) {
            for(int i=0; i<30; i++) {
              if(qscthr.isReady()) break;
              try{sleep(100);} catch(InterruptedException expected) {}
              if(i % 100 == 29) prta(tag+"* queryWithRaw waiting for span to be ready! "+i);
            }
          }
        }*/
        if (qscthr.isReady()) {
          qscspan.makeTraceBuf(g.getTimeInMillis(), duration + 1., traceBufPool, tsb);
        } else {
          prta(Util.clear(tmpsb).append(ttag).append(" * queryWithRaw span not ready! skip makeTraceBuf()"));
        }

        if (tsb.size() > 0) {
          endat = tsb.get(0).getTimeInMillis();   // set end time to beginning of data from memory to get from disk
        }
        if (dbg | dbgChan) {
          prta(Util.clear(tmpsb).append(ttag).append("queryWithRaw span returned tsb.size=").append(tsb.size()).
                  append(qscthr.isReady() ? "" : " ** Memory is not yet ready").append(" remain=").append(endat - st).append(" ms"));
        }
      }

      // Is there anything left to get?
      if (!(qscspan != null && (endat - st) < 500. / qscspan.getRate())) {    // Is it already satisfied (new endat is less than st)
        if (qscspan != null) {

          // get the miniseed blocks from either a remote server on the local QueryMom based one
          // If this is a sizeable request, make sure there is pool space
          if (endat - st > 1000000) {
            MiniSeedPool msp = EdgeQuery.getMiniSeedPool();
            int loop = 0;
            while (msp.getUsedList().size() > 400000) {
              prta(Util.clear(tmpsb).append(ttag).append(" ** queryWithRaw: wait for space on local MiniSeed Pool in queryWithRaw() ").append(msp.getUsedList().size()));
              try {
                sleep(4000);
              } catch (InterruptedException expected) {
              }
              loop++;
              if (loop > 10) {
                prta(Util.clear(tmpsb).append(ttag).append("******* queryWithRaw: wait for space on local MSP is infinite.  Abort"));
                SendEvent.edgeSMEEvent("CWBWSMSPStuc", "Wait for local MSP is stuck above 400000" + msp.getUsedList().size(), this);
                Util.exit("CWBWSMSP Stuck");
              }
            }
          }
          //prta(Util.clear(tmpsb).append(ttag).append("queryWithRaw: call span.doQuery ").
          //        append(Util.ascdatetime2(st)).append(" dur=").append((endat-st)/1000.)); 
          qscspan.setDebug(dbgChan | dbg);
          qscspan.doQuery(st, (endat - st) / 1000., mss);
          if (dbgChan & !dbg) {
            qscspan.setDebug(false);
          }
          if (dbg | dbgChan) {
            prta(Util.clear(tmpsb).append(ttag).append("queryWithRaw: call span.doQuery ret mss.size()=").
                    append(mss.size()).append(" ").append(mss.size() > 0 ? mss.get(0).toString() : "").
                    append(Util.ascdatetime2(st)).append(" dur=").append((endat - st) / 1000.));
          }
          Collections.sort(mss);
        } else {
          // Are we connected to a local
          if (host == null) {
            try {
              if (queryint == null) {
                queryint = new EdgeQueryInternal(ttag + "EQI", EdgeQueryServer.isReadOnly(), true, thisThread);
              }
              queryint.query(channel.toString(), st, (endat - st) / 1000., mss);
              Collections.sort(mss);
              if (dbg || dbgChan) {
                prta(Util.clear(tmpsb).append(ttag).append("queryWithRaw: internal query ret mss.size=").
                        append(mss == null ? "null" : mss.size()).append(" ").append(channel).
                        append(" st=").append(Util.ascdatetime2(st)).append(" dur=").append((endat - st) / 1000.).append(" "));
              }
            } catch (IOException e) {
              prta(Util.clear(tmpsb).append(ttag).append("** IOError on internal query! e=").append(e));
            } catch (IllegalSeednameException e) {
              prta(ttag + "** queryWithRaw: Illegal Seedname on internal query e=" + e);
            } catch (InstantiationException e) {
              prta(ttag + "***** queryWithRaw: Instantiation exception setup up EQI!");

            }
          } else {
            // We need to query a remote server with EdgeQueryClient
            String str = "-s " + channel.toString().replaceAll(" ", "-") + " -uf -q -b " + Util.stringBuilderReplaceAll(Util.ascdate(st), "/", "-") + "-" + Util.asctime2(st)
                    + " -d " + Util.df23((endat - st) / 1000.) + " -nonice -h " + host + " -t null";
            prta(Util.clear(tmpsb).append(ttag).append("queryWithRaw: query from remote server str=").append(str));

            if (dbg || dbgChan) {
              prta(Util.clear(tmpsb).append(ttag).append(command).append(" str beg=").append(str));
            }
            // If this is a sizeable request, make sure there is pool space
            if (duration > 1000) {
              state = 82;
              int loop = 0;
              while (EdgeQueryClient.getMiniSeedPool().getUsedList().size() > 400000) {
                prta(ttag + " ** queryWithRaw: wait for space on miniseed pool GETSCN " + EdgeQueryClient.getMiniSeedPool().getUsedList().size());
                Util.sleep(4000);
                loop++;
                if (loop > 10) {
                  prta(ttag + " ******** queryWithRaw: wait for space looks infinite - stop the process");
                  SendEvent.edgeSMEEvent("CWBWSMSPStuc", "EQC MSP is stuck above 400000 blocks "
                          + EdgeQueryClient.getMiniSeedPool().getUsedList().size(), this);

                  Util.exit("queryWithRaw: MSP space panic!");
                }
              }
            }
            state = 83;
            ArrayList<ArrayList<MiniSeed>> msstmp = EdgeQueryClient.query(str);
            prta(Util.clear(tmpsb).append(ttag).append("queryWithRaw: ret msstmp.size()=").append(msstmp == null ? "null" : msstmp.size()));
            try {
              if (msstmp != null && msstmp.size() > 0 && msstmp.get(0).size() > 0) {
                for (ArrayList<MiniSeed> blks : msstmp) {
                  if (blks != null) {
                    if (blks.size() > 1) {
                      Collections.sort(blks);
                      MiniSeed firstBlk = null;
                      for (MiniSeed blk : blks) {
                        if (blk.getRate() > 0.0001 && blk.getNsamp() > 0) {
                          firstBlk = blk;
                          break;
                        }
                      }
                    }
                    mss.addAll(blks);
                    blks.clear();
                  }
                  if (dbg || dbgChan) {
                    prta(Util.clear(tmpsb).append(ttag).append("queryWithRaw: ret blks.size()=").append(blks == null ? "null" : blks.size()));
                    prta(Util.clear(tmpsb).append(ttag).append(command).append(" str=").append(str).append(" return=").append(blks == null ? "Null" : blks.size()));
                  }
                }
              }
            } catch (RuntimeException expected) {

            }
            if (msstmp != null) {
              msstmp.clear();
            }
          }
        }
      }   // Satisfied out of memory
      long retStart = 0;
      long retEnd = 0;
      if (mss != null) {
        if (!mss.isEmpty()) {
          retStart = mss.get(0).getTimeInMillis();
        }
      }
      if (retStart == 0) {
        if (!tsb.isEmpty()) {
          retStart = tsb.get(0).getTimeInMillis();
        }
      }
      if (!tsb.isEmpty()) {
        retEnd = tsb.get(tsb.size() - 1).getEndtime();
      }
      if (retEnd == 0) {
        if (mss != null) {
          if (!mss.isEmpty()) {
            retEnd = mss.get(mss.size() - 1).getNextExpectedTimeInMillis();
          }
        }
      }

      if (!quiet | dbgChan) {
        Util.clear(tmpsb).append(ttag).append("queryWithRaw: return: mss.size=").append(mss == null ? "Null" : mss.size()).
                append(" tsb.size=").append(tsb.size()).append(" elapse=").append(System.currentTimeMillis() - lastAction).
                append(" ");
      }
      if (retStart == 0) {
        prta(tmpsb.append("Empty"));
      } else {
        prta(tmpsb.append(Util.ascdatetime2(retStart)).append("-").append(Util.asctime2(retEnd)).append(" ").
                append(" bef=").append(st - retStart).append(" aft=").append(retEnd - st - (long) (duration * 1000.)).
                append(" ").append(duration).append(" ").append((retEnd - retStart) / 1000.).append(" s"));
      }
    }

    private void doGetWaveRaw(ArrayList<MiniSeed> mss, ArrayList<TraceBuf> tsb) throws IOException {
      TimeSeriesBlock firstBlk = doQuerySpan(mss, tsb);     // load the span attribute with data from this query
      try {
        if (firstBlk == null) {
          Util.clear(tmpsb).append(requestID).append(" 0\n");
          write(tmpsb);
          //out.write((requestID+" 0\n").getBytes());
          return;
        }
        g.setTimeInMillis((long) ((start + year2000sec) * 1000.));
        double dur = (end - start);
        int maxns = (int) (dur * firstBlk.getRate() + 0.5);
        // For SCN, we are going to decode each block and write out the timeseries as ascii
        state = 888;
        prta(ttag + firstBlk.getSeedNameString() + "dur=" + dur + " " + duration + " maxns=" + maxns + " Span: " + span);
        //prta(firstBlk.toString()+"\n"+firstBlk.getSeedNameString()+" "+span);
        double n = start;
        double m = 1 / firstBlk.getRate();

        double dif = n % m;
        double registrationOffset;
        if (dif >= m / 2) {
          registrationOffset = (m - dif);
        } else {
          registrationOffset = -dif;
        }
        synchronized (waveRawMutex) {
          if (waveRawBuf.length < span.getNsamp() * 4 + 18) {
            waveRawBuf = new byte[span.getNsamp() * 8];
            waveRawbb = ByteBuffer.wrap(waveRawBuf);
          }

          int ns = Math.min(maxns, span.getNsamp());    // ???
          int[] data = span.getData();
          int offset = span.getIndexOfTime(g.getTimeInMillis());
          int firstMissing = span.getFirstMissing();
          ns = Math.min(ns, firstMissing);
          //if(dbg  || dbgChan)
          //  prta("WAVERAW span="+span+" offset="+offset+" d[]="+span.getData()[0]+" "+span.getData()[1]+" "+span.getData()[2]+" "+span.getData()[3]);
          //prta(ttag+firstBlk.getSeedNameString()+" maxns="+maxns+" nsamp="+span.getNsamp()+" firstMissing="+span.getFirstMissing()+" ns="+ns);
          if (ns < 0) {
            ns = 0;
          }
          waveRawbb.position(0);
          waveRawbb.putDouble((span.getStart().getTimeInMillis() - year2000MS) / 1000.);
          waveRawbb.putDouble(span.getRate());
          waveRawbb.putDouble(registrationOffset);
          waveRawbb.putInt(ns);
          for (int i = 0; i < ns; i++) {
            waveRawbb.putInt(data[i + offset]);
          }
          LastRawData last = lastGetRawMS.get(Util.getHashFromSeedname(channel));
          long ms = (long) (span.getStart().getTimeInMillis() + ns / span.getRate() * 1000);
          long now = System.currentTimeMillis();
          long lat = now - ms;
          if (now - ms > 10000) {
            ms = now;
          }
          if (last == null) {
            lastGetRawMS.put(Util.getHashFromSeedname(channel),
                    new LastRawData(ms, (long) (firstBlk.getNextExpectedTimeInMillis() - firstBlk.getTimeInMillis())));
          } else {
            last.setMS(ms);
          }

          if (dbg || dbgChan) {
            prta(Util.clear(tmpsb).append(ttag).append(" WAVERAW ").append(firstBlk.getSeedNameString()).
                    append(" Return ns=").append(ns).append(" rate=").append(span.getRate()).
                    append(" start=").append(Util.ascdatetime2(span.getStart())).
                    append(" off=").append(offset).append(" lat=").append(lat));
          }
          waveRawbb.put((byte) 's').put((byte) '4');
          boolean compress = true;
          if (word.length >= 6) {
            compress = word[6].charAt(0) != '0';
          }
          if (compress) {
            int nb = writeBytesCompressed(requestID, waveRawBuf, 0, waveRawbb.position(), out);
            stats.get(ipkey).incGetRawWave(System.currentTimeMillis() - startMS, nb);
            if (!quiet || dbgChan) {
              prta(Util.clear(tmpsb).append(ttag).append(ip).append(" ").append(firstBlk.getSeedNameString()).
                      append("RAW compressed nb=").append(nb).append("/").append(waveRawbb.position()).append("/").
                      append(Util.df21((double) waveRawbb.position() / nb)).append(" ns=").
                      append(ns).append(" off=").append(offset).
                      append(" elapse=").append(System.currentTimeMillis() - lastAction)
              );
            }
          } else {
            Util.clear(tmpsb).append(requestID).append(" ").append(waveRawbb.position()).append("\n");
            write(tmpsb);
            //out.write((requestID+" "+waveRawbb.position()+"\n").getBytes());
            out.write(waveRawBuf, 0, waveRawbb.position());
            stats.get(ipkey).incGetRawWave(System.currentTimeMillis() - startMS, waveRawbb.position());
            if (!quiet || dbgChan) {
              prta(Util.clear(tmpsb).append(ttag).append(ip).append(" ").append(firstBlk.getSeedNameString()).
                      append("RAW nb=").append(waveRawbb.position()).append(" ns=").append(ns).
                      append(" off=").append(offset).
                      append(" elapse=").append(System.currentTimeMillis() - lastAction)
              );
            }
          }
        } // Mutex
      } catch (RuntimeException e) {
        prta(ttag + "Runtime problem e=" + e);
        e.printStackTrace(getPrintStream());
        SendEvent.debugSMEEvent("CWBWSRunWRaw", "Runtime error GETWAVERAW ", this);
        Util.clear(tmpsb).append(requestID).append(" 0\n");
        write(tmpsb);
        //out.write((requestID+" 0\n" ).getBytes());
      }

    }

    private void doSCNLRaw2(StringBuilder command, ArrayList<MiniSeed> mss, ArrayList<TraceBuf> tbsram) throws IOException {
      if (mss == null || tbsram == null) {
        prta(ttag + " error mss to doSCNLRAW) is null");
        return;
      }
      ArrayList<TraceBuf> tbsmss = null;
      //word[3].append("  ").delete(2,word[3].length());
      //Util.stringBuilderReplaceAll(word[3]," ","-");              
      if (dbgChan || dbg) {
        prta(Util.clear(tmpsb).append(ttag).append("DocSCNLRAW2 ").append(command).append(" mss.size=").append(mss.size()).
                append(" tbsram.size=").append(tbsram.size()).append(" word[3]=").append(word[3]).
                append(" location ").append(location).append(" ").append(locationDashes));
      }
      try {
        int totlen = 0;
        // If there are miniSEED blocks, put them in the Thread qscspan and make TraceBufs for them
        if (mss.size() >= 1) {
          long endat;
          if (tbsram.size() >= 1) {
            endat = tbsram.get(0).getStarttime() - (long) (1000 / tbsram.get(0).getRate() - 2);  // End time for MiniSeed is daysbackms of any memory traces
          } else {
            endat = (long) (end * 1000. + 1000);        // if no memory traces, set end at query end
          }
          TimeSeriesBlock firstBlk = doQuerySpan(mss, null);      // Make up the qscspan
          long st = 0;
          if (firstBlk != null) {
            st = firstBlk.getTimeInMillis();                   // daysbackms time is from first block
          }
          if (st < start * 1000) {
            st = (long) (start * 1000. + 0.01);
          }
          int nsamp = (int) ((endat - st) / 1000. / span.getRate());// set number of samples
          if (dbg || dbgChan) {
            prta(Util.clear(tmpsb).append(ttag).append("DoSCNLRAW2 st=").append(Util.ascdatetime2(st)).
                    append(" endat=").append(Util.ascdatetime2(endat)));
          }
          tbsmss = new ArrayList<TraceBuf>(Math.max(5, nsamp / TraceBuf.TRACE_MAX_NSAMPS + 1));
          span.makeTraceBuf(st, (endat - st) / 1000., traceBufPool, tbsmss);
          if (tbsmss.size() > 0) {
            for (TraceBuf tbsms : tbsmss) {
              totlen += tbsms.getNsamp() * 4 + TraceBuf.TRACE_HDR_LENGTH - 6; // -6 because 6 byte EW transport wrapper not sent
            }
            if (dbg || dbgChan) {
              prta(Util.clear(tmpsb).append(ttag).append("DoSCNLRAW2 make tbsmss.size=").append(tbsmss.size()).
                      append(" totlen=").append(totlen).append(" tbsram.size=").append(tbsram.size()));
            }

          }

        }
        // If there are raw tracebufs from memory QuerySpans, add into the total bytes returned
        if (tbsram.size() > 0) {
          for (TraceBuf tbsram1 : tbsram) {
            totlen += tbsram1.getNsamp() * 4 + TraceBuf.TRACE_HDR_LENGTH - 6; // -6 because 6 byte EW transport wrapper not sent
          }
        }

        // tbsmss contains Traces made from Miniseed, and tbsmss contains traces from the QuerySpan memory buffers (if any)
        if (tbsmss == null && tbsram.isEmpty()) {      // If the request is empty, send out an FR, FG, etc.
          // Need to check and see if request is entirely out of bounds or just a gap in the middle
          if (dbg || dbgChan) {
            prta(Util.clear(tmpsb).append(ttag).append("SCNLRAW No data returned try it as FG Gap"));
          }
          state = 20;
          ChannelList ch = menuThread.getChannelList(channel);
          state = 21;
          ChannelStatus cs = menuThread.getChannelStatus(channel);
          state = 22;
          long begin = (long) (menuThread.getStart(channel) * 1000.);
          state = 23;
          double rate = 0.;
          long lastDataMillis = (long) (menuThread.getEnd(channel) * 1000.);
          state = 24;
          if (lastDataMillis <= g.getTimeInMillis()) {  // All data is before the requested time, send FR
            state = 25;
            if (!quiet || dbgChan) {
              prta(Util.clear(tmpsb).append(ttag).append("SCNLRAW return:").append(Util.ascdatetime2(g)).
                      append(" later than ").append(Util.ascdatetime2(lastDataMillis)).
                      append(" elapse=").append(System.currentTimeMillis() - lastAction).
                      append(" FR " + "0 ").append(word[0]).append(" ").append(word[1]).
                      append(" ").append(word[2]).append(" ").append(locationDashes).append(" FR ?? ").
                      append(Util.df23((lastDataMillis / 1000.))));
            }
            Util.clear(tmpsb).append(" 0 ").append(word[0]).append(" ").append(word[1]).
                    append(" ").append(word[2]).append(Util.stringBuilderEqual(command, "GETSCNLRAW") ? " " + locationDashes : "").
                    append(" FR ?? ").append(Util.df23(lastDataMillis / 1000.)).append(" \n");
            write(tmpsb);
            //out.write((" 0 "+word[0]+" "+word[1]+" "+word[2]+(Util.stringBuilderEqual(command,"GETSCNLRAW")?" "+locationDashes:"")+" FR ?? "+
            //        Util.df23(lastDataMillis/1000.)+" \n").getBytes());
          } else if (g.getTimeInMillis() + duration * 1000. < begin) {// all data is after the end time, send FL
            state = 27;
            if (!quiet || dbgChan) {
              prta(Util.clear(tmpsb).append(ttag).append("SCNLRAW return:end rqst=").
                      append(Util.ascdatetime2(g.getTimeInMillis() + (long) (duration * 1000))).
                      append(" < begin=").append(Util.ascdatetime2(begin)).
                      append(" elapse=").append(System.currentTimeMillis() - lastAction).
                      append(" FL =" + "0 ").
                      append(word[0]).append(" ").append(word[1]).append(" ").append(word[2]).
                      append(" ").append(locationDashes).append(" FL ?? ").append(Util.df23(begin / 1000.)));
            }
            Util.clear(tmpsb).append(" 0 ").append(word[0]).append(" ").append(word[1]).
                    append(" ").append(word[2]);
            if (Util.stringBuilderEqual(command, "GETSCNLRAW")) {
              tmpsb.append(" ").append(locationDashes);
            }
            tmpsb.append(" FL ?? ").append(Util.df23(begin / 1000.)).append(" \n");
            write(tmpsb);
            //out.write((" 0 "+word[0]+" "+word[1]+" "+word[2]+(Util.stringBuilderEqual(command,"GETSCNLRAW")?" "+locationDashes:"")+" FL ?? "+
            //       Util.df23(begin/1000.)+" \n").getBytes());
          } else {  // Its neither, call it a full gap
            state = 28;
            if (cs != null) {
              rate = cs.getRate();
            }

            state = 29;
            if (!quiet || dbgChan) {
              prta(Util.clear(tmpsb).append(ttag).append("SCNLRAW return:").
                      append(Util.ascdatetime2(g)).append(" mss.size=").append(mss.size()).
                      append(" tbsram.size=").append(tbsram.size()).
                      append(" elapse=").append(System.currentTimeMillis() - lastAction).
                      append(" in Gap FG 0 ").
                      append(word[0]).append(" ").append(word[1]).append(" ").append(word[2]).
                      append(" ").append(locationDashes).append(" FG ?? ").
                      append(Util.df23(menuThread.getStart(channel))).append(" ").
                      append(Util.df23(rate)).append(" rt=").append(rate));
            }
            // Not totally out-of-bounds,  so it must be in a gap
            Util.clear(tmpsb).append(" 0 ").append(word[0]).append(" ").append(word[1]).append(" ").append(word[2]);
            if (Util.stringBuilderEqual(command, "GETSCNLRAW")) {
              tmpsb.append(" ").append(locationDashes);
            }
            tmpsb.append(" FG ?? ").append(Util.df23(menuThread.getStart(channel))).
                    append(" ").append(Util.df23(rate)).append(" \n");
            write(tmpsb);
            //out.write((" 0 "+word[0]+" "+word[1]+" "+word[2]+(Util.stringBuilderEqual(command,"GETSCNLRAW")?" "+locationDashes:"")+" FG ?? "+
            //        Util.df23(menuThread.getStart(channel))+" "+Util.df23(rate)+" \n").getBytes());
          }
        } else {    // request is not empty send out summary string and data trace bufs
          long strt = 0;
          long endat = 0;
          if (tbsmss == null) {     //msstart == null?(tbsram.isEmpty()?0.:tbsram.get(0).getTimeInMillis()/1000.):msstart.getTimeInMillis()
            if (!tbsram.isEmpty()) {// no MiniSEED, but some raw, take it all from the Raw bufs
              strt = tbsram.get(0).getTimeInMillis();
              endat = tbsram.get(tbsram.size() - 1).getLastTimeInMillis() - (long) (1000. / tbsram.get(0).getRate());
            }
          } else if (tbsmss.isEmpty()) {
            if (!tbsram.isEmpty()) {
              strt = tbsram.get(0).getTimeInMillis();
              endat = tbsram.get(tbsram.size() - 1).getLastTimeInMillis() - (long) (1000. / tbsram.get(0).getRate());
            }
          } else {
            strt = tbsmss.get(0).getTimeInMillis();  // Start time comes from MiniSeed Traces
            if (!tbsram.isEmpty()) {                  // if there is raw, then get end from there
              endat = tbsram.get(tbsram.size() - 1).getLastTimeInMillis() - (long) (1000. / tbsram.get(0).getRate());
              if (!quiet || dbgChan) {
                prta(ttag + " GETSCNLRAW check boundary " + word[0] + " " + word[1] + " " + word[2] + " " + locationDashes
                        + " " + (tbsmss.get(tbsmss.size() - 1).getNextExpectedTimeInMillis() - tbsram.get(0).getTimeInMillis()) + "\n"
                        + " boundary end=" + tbsmss.get(tbsmss.size() - 1).toString() + "\n boundary big=" + tbsram.get(0).toString());
              }
            } else // only MiniSEED get from last tracebuf from the MiniSEED
            {
              endat = tbsmss.get(tbsmss.size() - 1).getLastTimeInMillis();
            }
          }

          String str = " 0 " + word[0] + " " + word[1] + " " + word[2]
                  + (Util.stringBuilderEqual(command, "GETSCNLRAW") ? " " + locationDashes : "")
                  + " F ?? " + Util.df26(strt / 1000.) + " " + Util.df26(endat / 1000.) + " " + totlen + "\n";
          Util.clear(tmpsb).append(" 0 ").append(word[0]).append(" ").append(word[1]).append(" ").
                  append(word[2]).append(Util.stringBuilderEqual(command, "GETSCNLRAW") ? " " + locationDashes : "").
                  append(" F ?? ").append(Util.df26(strt / 1000.)).append(" ").append(Util.df26(endat / 1000.)).
                  append(" ").append(totlen).append("\n");
          Util.stringBuilderToBuf(tmpsb, b);
          state = 861;
          out.write(b, 0, tmpsb.length());

          // Write out the accumulated TraceBufs.
          int nb = 0;
          if (tbsmss != null) {
            for (int i = 0; i < tbsmss.size(); i++) {
              if (dbg || dbgChan) {
                prt(Util.clear(tmpsb).append(ttag).append(i).append(" ").append(tbsmss.get(i)));
              }
              state = 862;
              out.write(tbsmss.get(i).getBuf(), 6, TraceBuf.TRACE_HDR_LENGTH + 4 * tbsmss.get(i).getNsamp() - 6);// Send skipping the EW transport wrapper
              nb += TraceBuf.TRACE_HDR_LENGTH + 4 * tbsmss.get(i).getNsamp() - 6;
            }
          }

          // If we have TraceBufs from the RAM/QuerySpan, send them as well
          for (int i = 0; i < tbsram.size(); i++) {
            if (dbg || dbgChan) {
              prt(Util.clear(tmpsb).append(ttag).append(i).append(" ").append(tbsram.get(i)));
            }
            state = 862;
            out.write(tbsram.get(i).getBuf(), 6, TraceBuf.TRACE_HDR_LENGTH + 4 * tbsram.get(i).getNsamp() - 6);// Send skipping the EW transport wrapper
            nb += TraceBuf.TRACE_HDR_LENGTH + 4 * tbsram.get(i).getNsamp() - 6;
          }

          state = 863;
          // NOTE  : the tbsmss will be cleared by the caller!  Do not do it here!
          if (tbsmss != null) {
            for (int i = tbsmss.size() - 1; i >= 0; i--) {
              traceBufPool.free(tbsmss.get(i));
            }
            tbsmss.clear();
          }
          if (!quiet || dbgChan) {
            prta(Util.clear(tmpsb).append(ttag).append("SCNLRAW return: success ").append(Util.ascdatetime2(strt)).
                    append("-").append(Util.ascdatetime2(endat)).append(" mss.size=").append(mss.size()).
                    append(" tbsram.size=").append(tbsram.size()).append(" ").append((endat - strt) / 1000.).
                    append("s elapse=").append(System.currentTimeMillis() - lastAction).append(" str=").append(str.substring(0, str.length() - 1)).append(" bytes"));
          }
          stats.get(ipkey).incGetSCNLRaw(System.currentTimeMillis() - startMS, nb);
        }
      } catch (IOException e) {
        if (e.toString().contains("onnection reset")) {
          prta(Util.clear(tmpsb).append(ttag).append("IOError in getSCNLRaw2() connection reset"));
        } else if (e.toString().contains("roken pipe")) {
          prta(Util.clear(tmpsb).append(ttag).append("IOError in getSCNLRaw2() broken pipe"));
        } else if (e.toString().contains("ocket close")) {
          prta(Util.clear(tmpsb).append(ttag).append("IOError in getSCNLRaw2() socket closed"));
        } else {
          prta(Util.clear(tmpsb).append(ttag).append(" IOError in getSNCLRaw2() - e=").append(e));
          if (!quiet) {
            e.printStackTrace(getPrintStream());
          }
        }
        if (tbsmss != null) {
          for (TraceBuf tbsms : tbsmss) {
            traceBufPool.free(tbsms);
          }
          tbsmss.clear();
        }
        state = 864;
        if (s == null) {   // If its not closed, send a gap indication - this probably never works.
          Util.clear(tmpsb).append(" 0 ").append(word[0]).append(" ").append(word[1]).append(" ").append(word[2]);
          if (Util.stringBuilderEqual(command, "GETSCNLRAW")) {
            tmpsb.append(" ").append(locationDashes);
          }
          tmpsb.append(" FG ?? ").append(Util.df23(menuThread.getStart(channel))).append(" ").append(Util.df23(40.)).append(" \n");
          write(tmpsb);
        } else if (!s.isClosed()) {
          Util.clear(tmpsb).append(" 0 ").append(word[0]).append(" ").append(word[1]).append(" ").append(word[2]);
          if (Util.stringBuilderEqual(command, "GETSCNLRAW")) {
            tmpsb.append(" ").append(locationDashes);
          }
          tmpsb.append(" FG ?? ").append(Util.df23(menuThread.getStart(channel))).append(" ").append(Util.df23(40.)).append(" \n");
          write(tmpsb);
        }
        throw e;      // let caller deal with any clean up.
        //out.write((" 0 "+word[0]+" "+word[1]+" "+word[2]+(Util.stringBuilderEqual(command,"GETSCNLRAW")?" "+locationDashes:"")+" FG ?? "+
        //    Util.df23(menuThread.getStart(channel))+" "+Util.df23(40.)+" \n").getBytes());        

      } catch (RuntimeException e) {
        if (tbsmss != null) {
          for (TraceBuf tbsms : tbsmss) {
            traceBufPool.free(tbsms);
          }
          tbsmss.clear();
        }
        Util.clear(tmpsb).append(" 0 ").append(word[0]).append(" ").append(word[1]).append(" ").append(word[2]);
        if (Util.stringBuilderEqual(command, "GETSCNLRAW")) {
          tmpsb.append(" ").append(locationDashes);
        }
        tmpsb.append(" FG ?? ").append(Util.df23(menuThread.getStart(channel))).append(" ").append(Util.df23(40.)).append(" \n");
        write(tmpsb);
        //out.write((" 0 "+word[0]+" "+word[1]+" "+word[2]+(Util.stringBuilderEqual(command,"GETSCNLRAW")?" "+locationDashes:"")+" FG ?? "+
        //        Util.df23(menuThread.getStart(channel))+" "+Util.df23(40.)+" \n").getBytes());  
        prta("CWSH: SCNLRAW had runtime exception.  SEnd FG e=" + e);

        if (e.toString().indexOf("OutOfMemory") > 0) {
          prta("CWSH: SCNLRAW got out of memory - try to terminate!");
          prt(Util.getThreadsString());
          Util.exit(101);
        }
        throw e;
      }
    }

    private void write(StringBuilder tmpsb) throws IOException {
      Util.stringBuilderToBuf(tmpsb, b);
      out.write(b, 0, tmpsb.length());
    }
    byte[] fillValue = new byte[12];
    int fillLen = 0;

    private void doGetSCNL(StringBuilder command, ArrayList<MiniSeed> mss, ArrayList<TraceBuf> tsb) throws IOException {
      try {
        TimeSeriesBlock ms = doQuerySpan(mss, tsb);   // this populates attribute span with the data
        //word[3]= (word[3]+"  ").substring(0,2).replaceAll(" ","-");

        if (ms != null) {      // There is some data in here somewhere
          Util.clear(tmpsb).append(word[6]).append(" ");
          Util.stringBuilderToBuf(tmpsb, fillValue);
          fillLen = tmpsb.length();
          //byte [] fillValue = (word[6]+" ").getBytes();     // GETSCNL has a fill value, what ever this string is put in ascii dump
          if (!quiet || dbg || dbgChan) {
            prta(Util.clear(tmpsb).append(ttag).append("GETSCNL: Span=").append(span).append(" g=").
                    append(Util.ascdatetime2(g)).append(" fill=").append(word[6]).
                    append("|").append(word[6]).append("|"));
          }

          // Send the <pin#> <s><c><n><l> F <datatype> <starttime> <sampling-rate> datatype is ?? for ASCII
          //String str = " 0 "+word[0].trim()+" "+word[1].trim()+" "+word[2].trim()+" "+
          //        word[3]+" F ?? "+Util.df23(g.getTimeInMillis()/1000.)+" "+span.getRate()+" ";
          Util.clear(tmpsb).append(" 0 ").append(word[0]).append(" ").append(word[1]).append(" ").
                  append(word[2]).append(" ").append(locationDashes).append(" F ?? ").
                  append(Util.df23(g.getTimeInMillis() / 1000.)).append(" ").append(span.getRate()).append(" ");
          if (dbg || dbgChan) {
            prt(Util.clear(tmpsb).append(ttag).append("GETSCNL: Return String :").append(tmpsb));
          }
          write(tmpsb);
          long sum = 0;
          /*
          prt(ttag+qscspan.getData(qscspan.getNsamp()-102)+" "+qscspan.getData(qscspan.getNsamp()-101)+" "
                  +qscspan.getData(qscspan.getNsamp()-100)+" "+qscspan.getData(qscspan.getNsamp()-99)+" "+
                  qscspan.getData(qscspan.getNsamp()-98));
          prt(ttag+qscspan.getData(qscspan.getNsamp()-5)+" "+qscspan.getData(qscspan.getNsamp()-4)+" "
                  +qscspan.getData(qscspan.getNsamp()-3)+" "+qscspan.getData(qscspan.getNsamp()-2)+" "+
                  qscspan.getData(qscspan.getNsamp()-1));*/
          int off = 0;
          int lenout = tmpsb.length();
          int ns = (int) ((end - start) * span.getRate() + 0.01);
          // Hydra does not want fill values to the end, so short the nsamp to be to the last non Fill value
          for (int i = ns - 1; i >= 0; i--) {
            if (span.getData(i) != QuerySpan.FILL_VALUE) {
              if (ns != i + 1) {
                prt(Util.clear(tmpsb).append(" Shorten return to ns=").append(i - 1).
                        append(" was ").append(ns).append(" to remove trailing fill values"));
              }
              ns = i + 1;
              break;
            }
          }
          int nfill = 0;
          for (int i = 0; i < ns; i++) {
            // If the data is a fill value, substitute what ever string they like
            if (span.getData(i) == QuerySpan.FILL_VALUE) {
              System.arraycopy(fillValue, 0, b, off, fillLen);
              off += fillLen;
              nfill++;
              //Util.prt(" i is fill="+i+" fill="+fillValue+"|");
            } else {    // not a fill value, put the ascii of the data value into the buffer
              off = intToASCIIBytes(span.getData(i), b, off);
              b[off++] = 32;
            }
            if (off > 2000) {
              out.write(b, 0, off);
              lenout += off;
              off = 0;
            }
          }
          b[off] = '\n';
          out.write(b, 0, off + 1);
          lenout += off + 1;
          if (!quiet || dbgChan) {
            prta(Util.clear(tmpsb).append(ttag).append("Send GETSCNL ns=").append(ns).
                    append(" nsamp=").append(span.getNsamp()).append(" nfill=").append(nfill).
                    append(" sum=").append(sum).
                    append(" elapse=").append(System.currentTimeMillis() - lastAction).
                    append(" len=").append(lenout));
          }
          stats.get(ipkey).incGetSCNL(System.currentTimeMillis() - startMS, lenout);
          //try{sleep(4000);} catch(InterruptedException expected) {}

        } else {
          // Need to check and see if request is entirely out of bounds or just a gap in the middle
          // I do not think the rate should be on FR and FL returns, only FG, but this seems to be in dispute.
          state = 60;
          if (dbg || dbgChan) {
            prta(Util.clear(tmpsb).append(ttag).append("GETSCNL no data returned - try it as FR, FL or F?"));
          }
          ChannelList ch = menuThread.getChannelList(channel);
          ChannelStatus cs = menuThread.getChannelStatus(channel);
          long begin = (long) (menuThread.getStart(channel) * 1000.);
          double rate = 0.;
          long lastDataMillis = (long) (menuThread.getEnd(channel) * 1000.);
          if (cs != null) {
            rate = cs.getRate();
          }
          if (rate > 1.e10 || rate < 1.e-10) {
            rate = 0.;
          }
          if (dbg || dbgChan) {
            prta(Util.clear(tmpsb).append(ttag).append("GETSCNL ch=").append(ch == null ? "null" : "ok").
                    append(" cs=").append(cs == null ? "null" : "ok").append(" last=").append(lastDataMillis).
                    append(" begin=").append(begin).append(" start=").append(g.getTimeInMillis()));
          }
          if (lastDataMillis <= g.getTimeInMillis()) {
            if (!quiet || dbgChan) {
              prta(Util.clear(tmpsb).append(ttag).append("GETSCNL ").
                      append(" elapse=").append(System.currentTimeMillis() - lastAction).
                      append(" return FR 0 ").
                      append(word[0]).append(" ").append(word[1]).append(" ").append(word[2]).append(" ").
                      append(locationDashes).append(" FR ?? ").append(Util.df23((lastDataMillis / 1000.))).append(" 0.000 "));
            }
            Util.clear(tmpsb).append(" 0 ").append(word[0]).append(" ").append(word[1]).append(" ").
                    append(word[2]).append(" ").append(locationDashes).append(" FR ?? ").
                    append(Util.df23((lastDataMillis / 1000.))).append(" 0.000 \n");
            write(tmpsb);
            //out.write((" 0 "+word[0]+" "+word[1]+" "+word[2]+" "+locationDashes+" FR ?? "+
            //       Util.df23((lastDataMillis/1000.))+" 0.000 \n").getBytes());
            return;// done with request
          }
          if (g.getTimeInMillis() + duration * 1000. < begin) {
            if (!quiet || dbgChan) {
              prta(Util.clear(tmpsb).append(ttag).append("GETSCNL ").
                      append(" elapse=").append(System.currentTimeMillis() - lastAction).
                      append(" return FL " + "0 ").
                      append(word[0]).append(" ").append(word[1]).append(" ").append(word[2]).append(" ").
                      append(locationDashes).append(" FL ?? ").append(Util.df23(begin / 1000.)).append(" 0.000"));
            }
            Util.clear(tmpsb).append(" 0 ").append(word[0]).append(" ").append(word[1]).
                    append(" ").append(word[2]).append(" ").append(locationDashes).
                    append(" FL ?? ").append(Util.df23(begin / 1000.)).append(" 0.000\n");
            write(tmpsb);
            //out.write((" 0 "+word[0]+" "+word[1]+" "+word[2]+" "+locationDashes+" FL ?? "+Util.df23(begin/1000.)+" 0.000\n").getBytes());
            return;
          }

          // Not totally out-of-bounds,  so it must be in a gap
          if (!quiet || dbgChan) {
            prta(Util.clear(tmpsb).append(ttag).append("GETSCNL").
                    append(" elapse=").append(System.currentTimeMillis() - lastAction).
                    append(" return FG1 ??" + " 0 ").append(word[0]).
                    append(" ").append(word[1]).append(" ").append(word[2]).append(" ").append(locationDashes).
                    append(" FG ?? ").append(Util.df23(begin / 1000.)).append(" ").append(Util.df23(rate)).
                    append(" rt=").append(rate));
          }
          Util.clear(tmpsb).append(" 0 ").append(word[0]).append(" ").append(word[1]).
                  append(" ").append(word[2]).append(" ").append(locationDashes).
                  append(" FG ?? ").append(Util.df23(begin / 1000.)).append(" ").
                  append(Util.df23(rate)).append(" \n");
          write(tmpsb);
          //out.write((" 0 "+word[0]+" "+word[1]+" "+word[2]+" "+locationDashes+" FG ?? "+Util.df23(begin/1000.)+" "+
          //      Util.df23(rate)+" \n").getBytes());
        }
      } catch (IOException e) {
        prta(ttag + "GETSCNL: exception e=" + e);
        long begin = (long) (menuThread.getStart(channel) * 1000.);
        Util.clear(tmpsb).append(" 0 ").append(word[0]).append(" ").append(word[1]).append(" ").
                append(word[2]).append(" ").append(locationDashes).append(" FG ?? ").
                append(Util.df23(begin / 1000.)).append(" ").append(Util.df23(40.)).append(" \n");
        write(tmpsb);
        //out.write(                (" 0 "+word[0]+" "+word[1]+" "+word[2]+" "+locationDashes+" FG ?? "+Util.df23(begin/1000.)+" "+
        //        Util.df23(40.)+" \n").getBytes());       
        if (!quiet) {
          e.printStackTrace(thisThread.getPrintStream());
        }
      } catch (RuntimeException e) {
        long begin = (long) (menuThread.getStart(channel) * 1000.);
        Util.clear(tmpsb).append(" 0 ").append(word[0]).append(" ").append(word[1]).
                append(" ").append(word[2]).append(" ").append(locationDashes).append(" FG ?? ").
                append(Util.df23(begin / 1000.)).append(" ").append(Util.df23(40.)).append(" \n");
        write(tmpsb);
        //out.write(                (" 0 "+word[0]+" "+word[1]+" "+word[2]+" "+locationDashes+" FG ?? "+Util.df23(begin/1000.)+" "+
        //        Util.df23(40.)+" \n").getBytes());       
        prta("GETSCNL: return FG got runtime=" + e);
        e.printStackTrace(thisThread.getPrintStream());
        throw e;
      }
    }

    /**
     * This loads the QuerySpan span with the miniseed data and trace data from a disk query /
     * memory query
     *
     * @param mss The ArrayList of MiniSEED blocks
     * @param tsb The arrayList of TraceBufs
     * @return A valid time series block for caller to get seednames, rates, etc, if null, there is
     * no data
     */
    private TimeSeriesBlock doQuerySpan(ArrayList<MiniSeed> mss, ArrayList<TraceBuf> tsb) {
      TimeSeriesBlock ms = null;
      long endat = 0;
      if (mss != null) {
        for (MiniSeed ms1 : mss) {
          if (ms1.getNsamp() > 0 && ms1.getRate() > 0.) {
            if (ms == null) {
              ms = ms1;
            } else if (ms1.getTimeInMillis() < ms.getTimeInMillis()) {
              ms = ms1;
            }
            endat = Math.max(ms1.getNextExpectedTimeInMillis(), endat);
          }
        }
      }
      if (ms == null && tsb != null) {
        for (TraceBuf tsb1 : tsb) {
          if (tsb1.getNsamp() > 0 && tsb1.getRate() > 0.) {
            if (ms == null) {
              ms = tsb1;
            }
            endat = Math.max(tsb1.getNextExpectedTimeInMillis(), endat);
          }
        }
      }
      if (ms != null) {
        // For SCN, we are going to decode each block and write out the timeseries as ascii
        double d = (endat - ms.getTimeInMillis() + 60000) / 1000.;
        if (span == null) {
          prta("doQuerySpan -thread needs a span - create it");
          span = new QuerySpan(null, 0, channel.toString(), ms.getTimeInMillis(),
                  Math.max(d, 3600.), Math.max(d, 3600.) * .95, ms.getRate(), thisThread);
        }

        // This is the weird part - we must call span.refill with either mss or tsb which every is not empty.
        // So we do the mss if possible, but if it empty we do it on tsb.  If both are not empty,
        // fill with the mss (which must be earlier), and then add on the tbs using span.addRealTime()
        if (mss != null) {
          if (mss.size() > 0) {
            span.refill(mss, g, duration * 1.2);   // 1.2 factor so no shifts occur when filling this or later TBs
          }
        }
        if (mss == null) {
          span.refillTB(tsb, g, duration * 1.2);
        } else if (mss.size() <= 0) {
          span.refillTB(tsb, g, duration * 1.2);
        } else {
          // The span already has miniseed at earlier time, so we need to add the TBs one at a time.
          if (tsb != null) {
            for (TraceBuf tsb1 : tsb) {
              span.addRealTime(tsb1.getTimeInMillis(), tsb1.getData(), tsb1.getNsamp(), 0.);
            }
          }
        }
        if (dbg || dbgChan) {
          prta("doQuerySpan span=" + span.toStringBuilder(null));
        }
      }   // if(ms != null) no real data so just skip it
      return ms;
    }
  }       // end of class CWBWaveServerHandler
  static final byte minus = 45;
  static final byte zero = 48;

  /**
   * create a string of ascii bytes in a byte array from an int
   *
   * @param i The int to translate
   * @param b The byte array for the data
   * @param off starting offset in the byte array for the first byte of the ascii
   * @return next byte in array that is free
   */
  static int intToASCIIBytes(int i, byte[] b, int off) {
    int orgoff = off;
    //int iorg=i;
    int l = 0;
    if (i < 0) {
      b[off++] = minus;
      i = -i;
      l++;
    }

    int ioff = off + 10;
    if (i == 0) {
      b[off++] = zero;
      return off;
    }
    while (i > 0) {
      b[ioff--] = (byte) (i % 10 + zero);
      i /= 10;
      l++;
    }
    System.arraycopy(b, ioff + 1, b, off, l);
    //Util.prt("iorg="+iorg+" "+new String(b,orgoff, l));
    return orgoff + l;
  }

  /**
   * convert a julian day to "yy_DOY"
   *
   * @param julian the julian date to transform
   * @return String of form "yy_doy"
   */
  static String yrday(int julian) {
    int[] ymd = SeedUtil.fromJulian(julian);
    int doy = SeedUtil.doy_from_ymd(ymd);
    return "" + Util.df3(ymd[0] % 100).substring(1, 3) + "_" + Util.df3(doy);
  }

  public class LastRawData {

    long ms;
    long avgms;
    long counter;
    long lastGetMS;
    long now;

    public LastRawData(long ms, long avg) {
      this.ms = ms;
      avgms = avg;
      lastGetMS = System.currentTimeMillis();
    }

    public void setMS(long ms) {
      if (ms > this.ms) {
        this.ms = ms;
      }
    }

    public long getCounter() {
      return counter;
    }

    public long getAvgMS() {
      return avgms;
    }

    public long getMS() {
      return ms;
    }
  }

  public class Stats {

    StringBuilder ipstats = new StringBuilder(16);
    int nconn;
    int nquery;
    int nold;
    int menu;
    int menuscnl;
    int getscnl;
    int getscnlraw;
    int getrawwave;
    int getheli;
    int getmetadata;
    int getchannels;
    long msquery;
    long msnold;
    long msmenu;
    long msmenuscnl;
    long msgetscnl;
    long msgetscnlraw;
    long msheli;
    long msrawwave;
    long msmetadata;
    long mschannels;
    int nbquery, nbold, nbmenu, nbmenuscnl, nbscnl, nbscnlraw, nbrawwave, nbheli, nbmetadata, nbchannels;
    StringBuilder tmpsb = new StringBuilder(12);

    public int getNQuery() {
      return nquery;
    }

    public int getNMenu() {
      return menu;
    }

    public long getMSMenu() {
      return msmenu;
    }

    public int getNBMenu() {
      return nbmenu;
    }

    public int getNMenuSCNL() {
      return menuscnl;
    }

    public long getMSMenuSCNL() {
      return msmenuscnl;
    }

    public int getNBMenuSCNL() {
      return nbmenuscnl;
    }

    public int getNSCNL() {
      return getscnl;
    }

    public long getMSSCNL() {
      return msgetscnl;
    }

    public int getNBSCNL() {
      return nbscnl;
    }

    public int getNSCNLRaw() {
      return getscnlraw;
    }

    public long getMSSCNLRaw() {
      return msgetscnlraw;
    }

    public int getNBSCNLRaw() {
      return nbscnlraw;
    }

    public int getNRawwave() {
      return getrawwave;
    }

    public long getMSRawwave() {
      return msrawwave;
    }

    public int getNBRawwave() {
      return nbrawwave;
    }

    public int getNHeli() {
      return getheli;
    }

    public long getMSHeli() {
      return msheli;
    }

    public int getNBHeli() {
      return nbheli;
    }

    public int getNMetadata() {
      return getmetadata;
    }

    public long getMSMetadata() {
      return msmetadata;
    }

    public int getNBMetadata() {
      return nbmetadata;
    }

    public int getNChannels() {
      return getchannels;
    }

    public long getMSChannels() {
      return mschannels;
    }

    public int getNBChannels() {
      return nbchannels;
    }

    public Stats(StringBuilder name) {
      Util.clear(ipstats).append(name);
      lastNbytes = System.currentTimeMillis();
      //prta("New Stats IP="+ipstats+"|");
      nquery = 0;
    }

    public void incNewCount(long ms) {
      nquery++;
      msquery += ms;
    }

    public void incMenu(long ms, int nb) {
      menu++;
      msmenu += ms;
      nbmenu += nb;
    }

    public void incMenuSCNL(long ms, int nb) {
      menuscnl++;
      msmenuscnl += ms;
      nbmenuscnl += nb;
    }

    public void incGetSCNL(long ms, int nb) {
      getscnl++;
      msgetscnl += ms;
      nbscnl += nb;
    }

    public void incGetSCNLRaw(long ms, int nb) {
      getscnlraw++;
      msgetscnlraw += ms;
      nbscnlraw += nb;
    }

    public void incGetMetadata(long ms, int nb) {
      getmetadata++;
      msmetadata += ms;
      nbmetadata += nb;
    }

    public void incGetChannels(long ms, int nb) {
      getchannels++;
      mschannels += ms;
      nbchannels += nb;
    }

    public void incGetRawWave(long ms, int nb) {
      getrawwave++;
      msrawwave += ms;
      nbrawwave += nb;
    }

    public void incHeli(long ms, int nb) {
      getheli++;
      msheli += ms;
      nbheli += nb;
    }

    public void incNconn() {
      nconn++;
    }
    long lastNbytes;

    public int getNConnect() {
      return nconn;
    }
    public static final String title = "IP address        nconn  #query rnquery   #menu  rnmenu #menusl rnmensl #gtSCNL rnGtSCN #getraw rngtraw #gtchan getchan #gtheli   heli  #getraw rawwav #gtmeta metadata";
    public static final String title2 = "IP address   KBPS: menu mnuscnl    SCNL SCNLRAW METADAT CHANNEL RAWWAVE    HELI   TOTAL #BrawWav #BHeli";

    @Override
    public String toString() {
      return toStringBuilder(null).toString();
    }

    public StringBuilder toStringBuilder(StringBuilder tmp) {
      StringBuilder sb = tmp;
      if (sb == null) {
        sb = Util.clear(tmpsb);
      }
      sb.append(Util.rightPad(ipstats, 15)).append(Util.leftPad(nconn, 8)).
              append(Util.leftPad(nquery, 8)).append(Util.leftPad(Util.df21(msquery / 1000.), 8)).
              append(Util.leftPad(menu, 8)).append(Util.leftPad(Util.df21(msmenu / 1000.), 8)).
              append(Util.leftPad(menuscnl, 8)).append(Util.leftPad(Util.df21(msmenuscnl / 1000.), 8)).
              append(Util.leftPad(getscnl, 8)).append(Util.leftPad(Util.df21(msgetscnl / 1000.), 8)).
              append(Util.leftPad(getscnlraw, 8)).append(Util.leftPad(Util.df21(msgetscnlraw / 1000.), 8)).
              append(Util.leftPad(getchannels, 8)).append(Util.leftPad(Util.df21(mschannels / 1000.), 8)).
              append(Util.leftPad(getheli, 8)).append(Util.leftPad(Util.df21(msheli / 1000.), 8)).
              append(Util.leftPad(getrawwave, 8)).append(Util.leftPad(Util.df21(msrawwave / 1000.), 8)).
              append(Util.leftPad(getmetadata, 8)).append(Util.leftPad(Util.df21(msmetadata / 1000.), 8));
      return sb;
    }

    public StringBuilder toStringNBytes() {
      double elapse = (Math.max(1, System.currentTimeMillis() - lastNbytes)) / 8.;  // convert to kbits per second
      Util.clear(tmpsb);
      tmpsb.append("").append(Util.rightPad(ipstats, 15)).append(Util.leftPad(Util.df21(nbmenu / elapse), 8)).
              append(Util.leftPad(Util.df21(nbmenuscnl / elapse), 8)).
              append(Util.leftPad(Util.df21(nbscnl / elapse), 8)).
              append(Util.leftPad(Util.df21(nbscnlraw / elapse), 8)).
              append(Util.leftPad(Util.df21(nbmetadata / elapse), 8)).
              append(Util.leftPad(Util.df21(nbchannels / elapse), 8)).
              append(Util.leftPad(Util.df21(nbrawwave / elapse), 8)).
              append(Util.leftPad(Util.df21(nbheli / elapse), 8));
      tmpsb.append(Util.leftPad(Util.df21((nbmenu + nbmenuscnl + nbscnl + nbscnlraw + nbmetadata + nbchannels + nbrawwave + nbheli) / elapse), 8));
      tmpsb.append(Util.leftPad("" + nbrawwave, 8)).append(Util.leftPad("" + nbheli, 8));
      nbmenu = 0;
      nbmenuscnl = 0;
      nbscnl = 0;
      nbscnlraw = 0;
      nbmetadata = 0;
      nbchannels = 0;
      nbrawwave = 0;
      nbheli = 0;
      lastNbytes = System.currentTimeMillis();
      return tmpsb;
    }

    public int getTotalBytes() {
      return nbmenu + nbmenuscnl + nbscnl + nbscnlraw + nbmetadata + nbchannels + nbrawwave + nbheli;
    }

    public int getTotalQueries() {
      return menu + menuscnl + getscnl + getscnlraw + getrawwave + getheli + getmetadata + getchannels;
    }
  }

  private final class CWBWaveServerMonitor extends Thread {

    private final StringBuilder monitorString = new StringBuilder(1000);
    private final StringBuilder bps = new StringBuilder(1000);
    private final StringBuilder tmpsb = new StringBuilder(200);
    int threadsBusy;
    int threadsDead;
    int loop;

    @Override
    public String toString() {
      return "CWBWSMon: monsize=" + monitorString.length() + " bps.len=" + bps.length() + " thrbusy=" + threadsBusy + " thrdead=" + threadsDead + " loop=" + loop;
    }

    public StringBuilder getMonitorString() {
      return monitorString;
    }

    public CWBWaveServerMonitor() {
      monitorString.append("MonitorNotYetAvailable=1\n");
      setDaemon(true);
      start();
    }

    @Override
    public void run() {
      loop = 0;
      prta("CWBWSM: starting");
      long lastStatus = System.currentTimeMillis();
      long now;
      int countWhoHasChan = 0;
      int lastWhoHasChan = 0;
      int mspBig = 0;
      int badRunMenuStateCount = 0;
      int badMenuStateCount = 0;
      while (!terminate) {
        try {
          try {
            sleep(10000);
          } catch (InterruptedException expected) {
          }
          loop++;
          if (traceBufPool.getUsed() > 10000) {
            prta(Util.clear(tmpsb).append("CWBWSM: Warning traceBuf pool highly used").append(traceBufPool));
          }
          if (EdgeQueryClient.getMiniSeedPool().getUsedList().size() > 25000) {
            mspBig++;
            if (mspBig % 6 == 0) {     // its been a minute
              prta("CWBWSM: MiniSeedPool is big " + EdgeQueryClient.getMiniSeedPool().getUsed());
              SendEvent.debugSMEEvent("MSPBig", "MSP used=" + EdgeQueryClient.getMiniSeedPool().getUsed(), this);
              for (int i = 0; i < EdgeQueryClient.getMiniSeedPool().getUsed(); i = i + 100) {
                prt("ms[" + i + "] dump " + EdgeQueryClient.getMiniSeedPool().getUsedList().get(i).toString().substring(0, 70));
              }
            }
          } else {
            mspBig = 0;
          }
          if (EdgeQuery.getMiniSeedPool().getUsed() > 25000) {
            mspBig++;
            if (mspBig % 6 == 0) {     // its been a minute
              prta("CWBWSM: MiniSeedPool is big " + EdgeQuery.getMiniSeedPool().getUsed());
              SendEvent.debugSMEEvent("MSPBig", "MSP used=" + EdgeQuery.getMiniSeedPool().getUsed(), this);
              for (int i = 0; i < EdgeQuery.getMiniSeedPool().getUsed(); i = i + 100) {
                prt("ms[" + i + "] dump " + EdgeQuery.getMiniSeedPool().getUsedList().get(i).toString().substring(0, 70));
              }
            }
          } else {
            mspBig = 0;
          }
          if (whoHasChannelList == lastWhoHasChan && lastWhoHasChan > 0) {
            countWhoHasChan++;
          } else {
            countWhoHasChan = 0;
          }
          lastWhoHasChan = whoHasChannelList;
          if (countWhoHasChan > 30) {
            SendEvent.edgeSMEEvent("CWBWSStuck", "whoHasChan=" + lastWhoHasChan + " stuck "
                    + menuThread.toStringBuilder(null), "CWBWaveServer");
            prta("CWSSM: *** Seem to be stuck int whoHasChan=" + lastWhoHasChan + " loop=" + countWhoHasChan);
            if (lastWhoHasChan == 2 && countWhoHasChan < 90) {     // Its in a doResultSet()
              menuThread.reopenDB();
              SendEvent.edgeSMEEvent("CWBWStuckRO", "Trying to reopen DB on stuck", this);
            } else {
              Util.exit(102);
            }
          }
          // If the Menu menustate is never 100, something is wrong
          if (menuThread.getRunMenuState() == 100 || menuThread.getRunMenuState() == 109 || menuThread.getRunMenuState() < 0) {
            badRunMenuStateCount = 0;
          } else {
            badRunMenuStateCount++;
            prta("* The runmenustate is not 100 or 109 or negative  states=" + menuThread.toStringBuilder(null)
                    + " #bad=" + badRunMenuStateCount);
            if (badRunMenuStateCount % 180 == 179) {  // Was 179n for 30 minutes now 5 minutes.
              prta(" **** The runmenustate has not been 100 or negative for 30 minutes.  Something is wrong. "
                      + menuThread.toStringBuilder(null) + " cnt=" + badRunMenuStateCount);
              SendEvent.edgeSMEEvent("BadMenuState", "Runmenustate has not been idle cnt="
                      + badRunMenuStateCount, "QueryMom");
              Util.setProperty("stackdump", "true");
              Util.exit("RunMenustate stuck in non-idle");
            } else if (badRunMenuStateCount % 180 == 59) {
              prta("**** menustate bad for 10 minutes, try reopen DB "
                      + menuThread.toStringBuilder(null) + " cnt=" + badRunMenuStateCount);
              menuThread.reopenDB();
            }
          }
          if (menuThread.getMenuState() > 0) {
            badMenuStateCount++;
            if (badMenuStateCount % 50 == 59) {
              Util.clear(tmpsb).append(" **** The menustate has not been negative for 10 minutes.  Something is wrong. ");
              prta(menuThread.toStringBuilder(tmpsb).append(" cnt=").append(badMenuStateCount));
              SendEvent.edgeSMEEvent("BadMenuState", "Menustate has not been negative cnt=" + badMenuStateCount, "QueryMom");
              Util.setProperty("stackdump", "true");
              Util.exit("Menustate stuck in non-idle");
            } else {
              if (badMenuStateCount > 1) {
                Util.clear(tmpsb).append("* menustate not negative ");
                prta(menuThread.toStringBuilder(tmpsb).append(" cnt=").append(badMenuStateCount));
              }
            }
          } else {
            badMenuStateCount = 0;
          }

          if (loop % 60 == 12) {
            threadsBusy = 0;
            threadsDead = 0;
            Util.clear(tmpsb).append("TracePool: ");
            traceBufPool.toStringBuilder(tmpsb);
            tmpsb.append("\nEQC.msp=");
            EdgeQueryClient.getMiniSeedPool().toStringBuilder(tmpsb);
            tmpsb.append("\nEQ.msp=");
            EdgeQuery.getMiniSeedPool().toStringBuilder(tmpsb);
            prta(tmpsb);

            for (CWBWaveServerHandler handler : handlers) {
              if (!handler.isAlive()) {
                threadsDead++;
              }
              if (handler.getSocket() != null || handler.state() != 1) {
                prta(Util.clear(tmpsb).append("CWBWSM:").append(handler.toString()));
                threadsBusy++;
              }
            }

            MiniSeedPool msp = EdgeQueryClient.getMiniSeedPool();
            Util.clear(tmpsb).append(msp.toString()).append(" highwater=").append(msp.getHighwater()).
                    append(" whoHas: stats=").append(whoHasStats).append(" station=").append(whoHasStationList).
                    append(" chans=").append(whoHasChannelList).append(" ");
            prta(menuThread.toStringBuilder(tmpsb));
            if (msp.getUsed() < 10 && msp.getFreeList().size() > 50000) {
              msp.trimList(50000);
              prta("Aft trimSize " + msp.toString());
            }

          }
          if (loop % 60 == 0) {
            now = System.currentTimeMillis();
            TLongObjectIterator<Stats> itr = stats.iterator();
            prta("Status: " + (System.currentTimeMillis() - lastStatus) / 1000. + " s ago");
            prt(Stats.title);
            Util.clear(bps).append("\n" + Stats.title2 + "\n");

            long totalbytes = 0;
            long totalqueries = 0;
            while (itr.hasNext()) {
              itr.advance();
              Stats s = itr.value();
              prt(s.toStringBuilder(null));
              totalbytes += s.getTotalBytes();
              totalqueries += s.getTotalQueries();
              bps.append(s.toStringNBytes()).append("\n");
            }
            prt(bps);
            prt("Total BPS =" + Util.df21(totalbytes * 8 / 1000. / ((now - lastStatus) / 1000.)) + " kbps");
            //if (!quiet && loop % 360 == 0) {
            //  prt("Threads: " + Util.getThreadsString(30));
            //}

            Util.clear(monitorString).append("CWBWSQueries=").append(totalqueries).append("\nCWBWSBytes=").
                    append(totalbytes).append("\nTBPoolUsed=").append(traceBufPool.getUsed()).
                    append("\nThreadsBusy=").append(threadsBusy).append("\nThreadsDead=").append(threadsDead).append("\n");
            lastStatus = now;
          }
        } catch (RuntimeException e) {
          prta("Runtime errror e=" + e);
          e.printStackTrace(thisThread.getPrintStream());
        }
      }
    }
  }

  public static void main(String[] args) {
    Util.setProcess("CWBWaveServer");
    EdgeProperties.init();
    Util.setNoconsole(false);
    Util.setNoInteractive(true);

    Util.setModeGMT();
    EdgeThread.setMainLogname("cwbws");
    Util.prt("Starting CWBWaveServer");
    byte[] b = new byte[1000];
    int off = 0;
    off = intToASCIIBytes(101, b, off);
    b[off++] = 32;
    off = intToASCIIBytes(-101, b, off);
    b[off++] = 32;
    off = intToASCIIBytes(2147000000, b, off);
    b[off++] = 32;
    off = intToASCIIBytes(-2147000000, b, off);
    b[off++] = 32;
    off = intToASCIIBytes(1012, b, off);
    b[off++] = 32;
    off = intToASCIIBytes(10123, b, off);
    b[off++] = 32;
    off = intToASCIIBytes(101234, b, off);
    b[off++] = 32;
    off = intToASCIIBytes(-1012345, b, off);
    b[off++] = 32;
    off = intToASCIIBytes(10123456, b, off);
    b[off++] = 32;
    off = intToASCIIBytes(-101234567, b, off);

    Util.prt("Result=" + new String(b, 0, off) + "|");

    String argline = "";
    for (String arg : args) {
      argline += arg + " ";
    }
    try {
      CWBWaveServer srv = new CWBWaveServer(argline, "CWS");
      Util.prt("Startup line=" + argline);
      int loop = 0;
      for (;;) {
        try {
          Thread.sleep(10000);
        } catch (InterruptedException expected) {
        }
        if (!srv.isRunning()) {
          break;
        }
        if (loop++ % 60 == 0) {
          Util.prta(Util.ascdate() + " " + srv.getMonitorString() + "\n" + srv.getStatusString());
        }
      }
    } catch (RuntimeException e) {
      Util.prt("Runtime exception caught by CWS main() shutdown e=" + e);
      Util.prt("msg=" + (e == null ? "null" : e.getMessage()));
      if (e != null) {
        e.printStackTrace();
      }
    }
    System.exit(1);
  }
}
