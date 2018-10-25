/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.edgemom;

import gnu.trove.map.hash.TLongObjectHashMap;
import gov.usgs.alarm.SendEvent;
import gov.usgs.anss.edge.ChannelSender;
import gov.usgs.anss.edge.IllegalSeednameException;
import gov.usgs.anss.edge.MasterBlock;
import gov.usgs.anss.edge.RawToMiniSeed;
import gov.usgs.anss.edge.RawDisk;
import gov.usgs.anss.edgeoutput.Module;
import gov.usgs.anss.edgeoutput.TraceBuf;
import gov.usgs.anss.ew.EWMessage;
import gov.usgs.anss.net.SNWSender;
import gov.usgs.anss.util.SeedUtil;
import gov.usgs.anss.util.Util;
import gov.usgs.edge.config.Channel;
import gov.usgs.anss.picker.JsonDetectionSender;
import gov.usgs.anss.picker.PickerManager;
import java.io.IOException;
import java.io.FileNotFoundException;
import java.util.zip.Inflater;
import java.util.zip.DataFormatException;
import java.net.Socket;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.ArrayList;

/**
 * This thread is created by the EdgeImportServer for each configured import. It
 * implements both the client and server mode. In client mode it maintains the
 * connection to the export. In server mode, the EWImport server will reset the
 * socket to use when a new connection is received from the import
 * (newSocket()). This thread implements all type of supported export types in a
 * case like series of ifs in the run() method. Not all EW packet types are
 * supported as not all types have ever been used by the Edge and some types are
 * not expected to never be supported.
 * <p>
 *
 *
 * In the case here we are only handling messages of types : TYPE_HEARTBEAT,
 * TYPE_TRACEBUF, TYPE_TRACEBUF2, TYPE_GEOMAG_DATA, TYPE_GEOMAG_DATA2,
 * TYPE_ERROR
 *
 * TYPE_TRACE2_COMP_UA is heard but not translated.
 *
 * For ASCII messages of type :<br>
 * TYPE_PICK_SCNL, TYPE_CODA_SCNL, TYPE_HYP2000ARC, TYPE_HINVARC,TYPE_H71SUM
 * TYPE_PICK2K,EWMessage.TYPE_PICK2, TYPE_CODA2K TYPE_CODA2, TYPE_PICK2K,
 * TYPE_PICK2K, TYPE_CUBIC, TYPE_STRONGMOTIONII , TYPE_STRONGMOTION,
 * TYPE_LOC_GLOBAL
 * <br>
 * Can be forwarded to an EdgeWire for parametric data, but they are not
 * processed by the Edge/CWB in any way.
 * <p>
 * The IMPORT protocol sends EW messages of the form : ascii logo
 * (STX+%3d%3d%3d) for institution ID, ModuleIN and logotype message body. The
 * body is encoded with and escape before every STX, ETX or ESC which appears in
 * the body of the message. That is every STX or ETX or ESC which is in the
 * binary message is converted to ESC-STX, EXC-ETX or ESC-ESC. In this way naked
 * STX and ETX can be use to pick out the messages. The size of the messages are
 * unknown.
 * <p>
 * Geomag has a different way of sending data using IMPORT. In this mode the
 * payload is gzipped and the expectation is that receiving a data packet also
 * beats our heart (no heartbeats come in if the data packets are arriving
 * normally). This is handled in the processGeomagData() method.
 * <PRE>
 *the line format is :
 * TAG:IPADDRES:args
 *
 * where TAG is used to identify threads in the log and IPADDRESS is the address of
 * the ip address of the EXPORT (-client or -server defines direction of connection)
 *
 * args          Description
 * -client port This IMPORT is a client end and needs to make connection to host:port
 * -server port This IMPORT is a server end and waits for connections from host:port
 * -ack          This IMPORT uses acks for every packet
 * -hbout msg    Set the heart beat message to send to EXPORT (importalive)
 * -hbin msg     The message expected from the EXPORT end (exportalive)
 * -hbint int    Seconds between heartbeats to the EXPORT
 * -hbto int    seconds to allow for receipt of heartbeat from EXPORT before declaring a dead connection
 * -inst iiii    This ends heart beats will use this institution code
 * -mod  iiii    This ends heart beats will use thiis module code
 * -nooutput     If present, no data will be send to be compressed and stored.
 * -obsrio      ObsRio zip payload, Do not expect escaping of special characters - process them all
 * -inorder type If present, in order checking creates gaps of the given type in addition to logging gaps.
 * -excludes str Exclude an channel which matchs in the following string RE
 * -rsend maxRate   Send Raw data to OutputInfrastructure if rate is less than the given rate - normally used to QueryMom RAM population
 *
 * For parameter imports :
 * -param:brd.ip.addr:portoff[:inst:module] This is a params import, export to edgewire
 * -agency str   Set the agency for JSON messages
 * -json method1&method2&method3    # send pick messages via JSON.  Only TYPE_PICK_SCNL=8 are supported.
 *
 * where each method consists of key;arg1;arg2;arg3...
 *
 * method      Format
 * path       path;[SOME PATH TO DIRECTORY] - write JSON methods as files on this path
 * kafka      kafka;type;server:port
 *            type matches detection-formats like 'beam' or 'pick'
 *            server is like : 'igskcicgvmkafka.cr.usgs.gov/9092' Note: slash is changed to ':' in the server tags
 * </PRE>
 *
 * @author davidketchum  <ketchum at usgs.gov>
 */
public final class EWImportSocket extends Thread {

  private static final int MAX_UDP_LENGTH = 1350;
  private final static int OFF_INST = 0;
  private final static int OFF_TYPE = 1;
  private final static int OFF_MOD = 2;
  private final static int OFF_FRAGNO = 3;
  private final static int OFF_SEQNO = 4;
  private final static int OFF_LAST = 5;
  private static final int EXPECTING_START = 1;
  private static final int SEARCHING_START = 2;
  private static final int IN_MESSAGE = 3;
  private static final byte STX = 2;
  private static final byte ETX = 3;
  private static final byte ESC = 27;
  private static final int LOGO_SIZE = 10;    // STX%3d%3d%3d with logo
  private static String dbgdataExport = "";
  private static final int ACK_BUF_SIZE = 20;
  private static long lastClientCheckForServer = 0;
  private static final byte[] result = new byte[12000];

  private static final long MASK = 1 << (8 - 1);
  //private static MySQLMessageQueuedClient mysql;
  private static SNWSender snwsender;
  private static int threadCount;
  private final int threadNumber;
  private long nTBRateMismatch;
  //private final TLongObjectHashMap<Chan> chans = new TLongObjectHashMap<>();    // replace by GapsFromInorderMaker
  //TreeMap<String, Chan> chans = new TreeMap<String, Chan>();
  private final GregorianCalendar start = new GregorianCalendar();
  private int instate;
  private int state;
  private long lastBytesIn;
  private long lastPackets;
  private long bytesIn;
  private long bytesOut;
  private long zipIn;
  private long zipOut;
  private int npackets;
  private int nchanges;
  private int maxSize;
  private int nsyncError;
  private long latencySum;
  private int nlatency;
  private int ndisconnect;
  private int skipped;
  private long lastStatusOut;
  private final EWImportServer par;
  //private GapList gaps;
  private boolean hydra;
  private String export;
  private boolean nooutput;

  private byte[] buf = new byte[8192];               // Message is assembled here

  private final byte[] ackbuf = new byte[ACK_BUF_SIZE];
  private final ByteBuffer ackbb;
  private final byte[] b;
  private String tag;
  private TraceBuf tb;         // trace buf for hydra sender
  private boolean running;
  private boolean terminate;
  private boolean dbg;
  private boolean dbgdata;
  private String dbgchan;
  private Socket s;
  // EXPORT/IMPORT behavior flags
  private String orgArgline;
  private boolean doAcks;       // If true, perform per packet acking
  private boolean clientMode;   // IF true, this is a client and needs to maintain a connection
  private String exportHost;   // only needed in client mode, the target host
  private int exportPort;       // only needed in client mode, the tagret port
  private String bindadr;
  private InetAddress bindAddr;
  private int hbInstitution;
  private int hbModule;
  private int hbInterval;       // the interval in seconds between heartbeats
  private int hbIntervalTimeout; // seconds to allow for non-receipt of heartbeat from EXPORT before breaking connection.
  private String hbMsgOut;
  private String hbMsgIn;
  private String excludes;
  private String translationsFile;
  private String inorderGapType;
  private final TLongObjectHashMap<StringBuilder> translations = new TLongObjectHashMap<>();
  //private final TreeMap<String, String> translations = new TreeMap<String, String>();
  private boolean nocsend;
  private boolean noDB;
  private boolean scn;  // if try override location code.
  private ChannelSender csend;
  private EWHeartBeatHandler hbHandler;

  // Data needed to send non-tracebufs to edgeWire
  private boolean paramImport;
  private DatagramSocket ds = null;
  private InetAddress ipaddr;
  private DatagramPacket dp = null;
  private byte[] ubuf;
  private int paramPortOffset;
  private String udpBroadcastAddress;
  private long nZeroRate;
  private final byte[] seq = new byte[256];
  private double rawMaxRate = 10.;
  private boolean rawSend;
  private boolean obsRioZip;

  // JSON related
  private JsonDetectionSender jsonSender;
  private String jsonConfig;
  private String agency = "NONE";
  private final StringBuilder tmpsb = new StringBuilder(100);
  private final StringBuilder runsb = new StringBuilder(100);
  private final StringBuilder dbmsg = new StringBuilder(10);

  public String getArgline() {
    return orgArgline;
  }

  public static void setDbgDataExport(String s) {
    dbgdataExport = s.trim();
  }

  public boolean isRunning() {
    return running;
  }

  public String getStatusString() {
    double elapsed = Math.max(1., (System.currentTimeMillis() - lastStatusOut) / 1000.);
    lastStatusOut = System.currentTimeMillis();
    long nb = bytesIn - lastBytesIn;
    long np = npackets - lastPackets;
    lastBytesIn = bytesIn;
    lastPackets = npackets;
    long avgLat = latencySum / Math.max(1, nlatency);
    latencySum = 0;
    nlatency = 0;
    return export + (s == null ? "null" : (s.isClosed() ? "closed" : "") + " acks=" + doAcks
            + " #pkt=" + np + " " + ((int) (np * 60 / elapsed + 0.5)) + "p/m #bin=" + (nb / 1000) + "kB " + ((int) (nb * 8 / elapsed)
            + " b/s avglat=" + avgLat + "ms Err:#sync=" + nsyncError + " dcon=" + ndisconnect
            + " maxPkt=" + maxSize + "b hbTO=" + hbHandler.getHeartBeatTimeOuts()
            + " state=" + instate + "/" + state));
  }

  public long getBytesIn() {
    return bytesIn;
  }

  public long getBytesOut() {
    return bytesOut;
  }
  long lastMonPackets;
  long lastMonBytesIn;

  public String getMonitorString() {
    long nb = bytesIn - lastMonBytesIn;
    long np = npackets - lastMonPackets;
    lastMonBytesIn = bytesIn;
    lastMonPackets = npackets;
    return tag + "-BytesIn=" + nb + "\n" + tag + "-PacketsIn=" + np + "\n";
  }

  public String getConsoleOutput() {
    return "";
  }

  public void terminate() {
    terminate = true;
    if (hbHandler != null) {
      hbHandler.terminate();
    }
    if (par != null) {
      par.prta(tag + " EWImport terminate called");
    } else {
      Util.prta(tag + " EWImport terminate called.");
    }
    try {
      if (s != null) {
        if (!s.isClosed()) {
          long now = System.currentTimeMillis();
          while (System.currentTimeMillis() - now < 500) {
            if (s != null) {
              if (s.getInputStream().available() > 0) {
                try {
                  sleep(5);
                } catch (InterruptedException e) {
                }
              } else {
                break;
              }
            } else {
              break;
            }
          }
          if (par != null) {
            par.prta(tag + " socket closed after " + (System.currentTimeMillis() - now) + " ms");
          }
        }
      }
      if (s != null) {
        s.close();
      }
    } catch (IOException e) {
    }
  }

  @Override
  public String toString() {
    return toStringBuilder(null).toString();
  }

  public StringBuilder toStringBuilder(StringBuilder tmp) {
    StringBuilder sb = tmp;
    if (sb == null) {
      sb = Util.clear(tmpsb);
    }
    synchronized (sb) {
      sb.append(tag).append(" s=").append(s == null ? "null" : s.getInetAddress().toString()
              + "/" + s.getPort() + " " + s.isClosed()).append(" hydra=").append(hydra);
    }
    return sb;
  }

  public void setDebug(boolean t) {
    dbg = t;
    par.prt(tag + " set debug to " + t);
  }

  /**
   * set a new socket to receive from. This is only called when IMPORT is in
   * server (passive) mode
   *
   * @param ss The socket to use going forward
   */
  public synchronized void newSocket(Socket ss) {
    if (s != null) {
      if (!s.isClosed()) {
        try {
          s.close();
        } catch (IOException e) {
        }
      }
    }
    //try{sleep(1000);} catch(InterruptedException e) {}
    dbgdata = false;
    if (dbgdataExport.equals(exportHost.trim())) {
      dbgdata = true;
    }
    par.prt(Util.clear(tmpsb).append(tag).append(" ").append(export).append(" New connection set to ").
            append(ss).append(" dbgdata=").append(dbgdata).append(" ").append(dbgdataExport));
    s = ss;

  }
  byte[] btran = new byte[8192];

  public final void setArgs(String argline) {
    String[] parts = argline.split(":");
    exportHost = parts[1];
    tag = "[" + parts[0] + "]";
    String[] args = parts[2].split("\\s");
    orgArgline = argline;
    hydra = !par.getNoHydra();
    doAcks = false;
    clientMode = false;
    hbMsgOut = "importalive";
    hbMsgIn = "exportalive";
    exportPort = -1;
    hbInterval = 60;
    hbIntervalTimeout = 120;
    hbModule = 28;
    hbInstitution = 13;
    nooutput = false;
    dbgchan = "ZZZZZZ";
    paramImport = false;
    paramPortOffset = 40100;
    nocsend = par.getNoChannelSend();
    rawSend = false;
    rawMaxRate = 10.;
    noDB = false;
    String mhost;
    for (int i = 0; i < args.length; i++) {
      if (args[i].equals("-ack")) {
        doAcks = true;
      } else if (args[i].equals("-inst")) {
        hbInstitution = Integer.parseInt(args[i + 1]);
        i++;
      } else if (args[i].equals("-mod")) {
        hbModule = Integer.parseInt(args[i + 1]);
        i++;
      } else if (args[i].equals("-hbint")) {
        hbInterval = Integer.parseInt(args[i + 1]);
        i++;
      } else if (args[i].equals("-hbto")) {
        hbIntervalTimeout = Integer.parseInt(args[i + 1]);
        i++;
      } else if (args[i].equals("-hbout")) {
        hbMsgOut = args[i + 1];
        i++;
      } else if (args[i].equals("-hbin")) {
        hbMsgIn = args[i + 1];
        i++;
      } else if (args[i].equals("-excludes")) {
        excludes = args[i + 1];
        i++;
      } else if (args[i].equals("-inorder")) {
        inorderGapType = args[i + 1];
        i++;
      } else if (args[i].equals("-dbgch")) {
        dbgchan = args[i + 1].replaceAll("_", " ").replaceAll("-", " ");
        i++;
      } else if (args[i].equals("-dbg")) {
        dbg = true;
      } else if (args[i].equalsIgnoreCase("-nodb")) {
        noDB = true;
      } else if (args[i].equals("-nooutput")) {
        nooutput = true;
      } else if (args[i].equals("-scn")) {
        scn = true;
      } else if (args[i].equals("-obsrio")) {
        obsRioZip = true;
      } else if (args[i].equals("-tran")) {
        translationsFile = args[i + 1];
        i++;
        try {
          String trans;
          try (RawDisk rw = new RawDisk(translationsFile, "r")) {
            if (rw.length() > btran.length) {
              btran = new byte[(int) rw.length() * 2];
            }
            rw.readBlock(btran, 0, (int) rw.length());
            trans = new String(btran, 0, (int) rw.length());
          }
          String[] lines = trans.split("\\n");
          for (String line : lines) {
            if (line.charAt(0) == '#') {
              continue;
            }
            parts = line.split(":");
            if (parts.length != 2) {
              par.prt(Util.clear(tmpsb).append(tag).append("***Translation has weird line=").append(line).append(" skipping"));
            } else {
              StringBuilder to = new StringBuilder(12);
              to.append((parts[1] + "            ").substring(0, 12));
              translations.put(Util.getHashFromSeedname((parts[0] + "           ").substring(0, 12)), to);
            }
          }
        } catch (FileNotFoundException e) {
          par.prta("Translation file not found =" + translationsFile);
        } catch (IOException e) {
          par.prta("IOError on translation file e=" + e);
        }
      } else if (args[i].equals("-rsend")) {
        rawSend = true;
        rawMaxRate = Double.parseDouble(args[i + 1]);
        i++;
      } else if (args[i].equals("-bind")) {
        bindadr = args[i + 1];
        try {
          bindAddr = InetAddress.getByName(bindadr);
        } catch (UnknownHostException e) {
          par.prt("**** Could not find address for bind adr=" + bindadr + " e=" + e);
        }
        i++;
      } else if (args[i].indexOf("-param") == 0) {
        paramImport = true;
        ubuf = new byte[MAX_UDP_LENGTH];
        parts = args[i].split("!");
        if (parts.length < 3) {
          par.prt("Illegal syntax for -params - no host or offset and they are required!");
          return;
        }
        if (parts.length >= 4) {
          ubuf[OFF_INST] = Byte.parseByte(parts[3]);
        } else {
          ubuf[OFF_INST] = (byte) hbInstitution;
        }
        if (parts.length >= 5) {
          ubuf[OFF_MOD] = Byte.parseByte(parts[4]);
        } else {
          ubuf[OFF_MOD] = (byte) hbModule;
        }
        if (parts.length >= 6) {
          mhost = parts[5];
        } else {
          mhost = Util.getSystemName() + "-hydra.cr.usgs.gov";
        }
        if (parts[2].length() > 0) {
          paramPortOffset = Integer.parseInt(parts[2]);
        }
        while (ds == null) {
          try {
            udpBroadcastAddress = parts[1];
            ipaddr = InetAddress.getByName(parts[1]);
            if (ipaddr == null) {
              par.prt("    ***** Host did not translate = " + parts[1]);
            }
            ds = new DatagramSocket(paramPortOffset - threadNumber, InetAddress.getByName(mhost));
            dp = new DatagramPacket(new byte[MAX_UDP_LENGTH], MAX_UDP_LENGTH,
                    ipaddr, paramPortOffset);
            par.prta(tag + " Open UDP DP=" + udpBroadcastAddress + "/" + (paramPortOffset - threadNumber));
          } catch (UnknownHostException e) {
            par.prta(tag + " Unknown host exception. e=" + e);
            SendEvent.debugEvent("HydUnkHost", "EWIMport parameters could not translate host=" + udpBroadcastAddress, this);
            e.printStackTrace(par.getPrintStream());
          } catch (SocketException e) {
            ds = null;
            par.prt(tag + " cannot set up DatagramSocket to " + udpBroadcastAddress + "/" + paramPortOffset + " " + e.getMessage());
            try {
              sleep(30000);
            } catch (InterruptedException e2) {
            }
          }
        }

      } else if (args[i].equals("-client") || args[i].equals("-server")) {
        exportPort = Integer.parseInt(args[i + 1]);
        if (args[i].equals("-client")) {
          clientMode = true;
        }
        i++;
      } else if (args[i].equalsIgnoreCase("-agency")) {
        agency = args[i + 1];
        i++;
      } else if (args[i].indexOf("-json") == 0) {
        if (Util.getProperty("PickDBServer") == null) {
          par.prta(" ******** cannot run picker to json without the PickDBServer property *****");
        } else {
          ubuf = new byte[MAX_UDP_LENGTH];
          jsonConfig = args[i + 1];
          jsonSender = new JsonDetectionSender("-json " + jsonConfig, par);   // Make a sender
        }
        i++;
      } else {
        par.prt(Util.clear(tmpsb).append(tag).append("Unknown argument=").
                append(args[i]).append(" at ").append(i).append(" line=").append(argline));
      }

    }
    if (!nocsend && csend == null) {
      csend = new ChannelSender("  ", "EWImp-" + tag, "EWI-" + tag);
    }
    ackbb.position(0);
    ackbb.put((byte) STX);
    String ackstring = "" + Util.leftPad("" + hbInstitution, 3) + Util.leftPad("" + hbModule, 3) + Util.leftPad("" + EWMessage.TYPE_ACK, 3) + "ACK:";
    ackbb.put(ackstring.getBytes());

    if (exportPort == -1) {
      throw new RuntimeException("No port configured");
    }
    tb = new TraceBuf(new byte[TraceBuf.TRACE_LENGTH]);        // Trace buf for sending data to HydraOutputer
    export = (tag + ":" + exportHost + "/" + exportPort);
    hbHandler = new EWHeartBeatHandler((byte) hbInstitution, (byte) hbModule, hbMsgOut, hbInterval, hbIntervalTimeout);

    par.prta(Util.clear(tmpsb).append(tag).append("CFG: ack=").append(doAcks).
            append(" hydra=").append(hydra).append(" no out=").append(nooutput).
            append(" HB(i/m=").append(hbInstitution).append("/").append(hbModule).
            append(" ").append(hbMsgOut).append(">").append(hbMsgIn).append("< ").
            append(hbInterval).append("s TO=").append(hbIntervalTimeout).
            append("s') client=").append(clientMode).append(" Export:").append(exportHost).
            append(":").append(exportPort).append(" inordertype=").append(inorderGapType).append(" bind=").append(bindadr).
            append(" nooutput=").append(nooutput).append(" dbg=").append(dbg).
            append("/").append(dbgchan));

  }

  /**
   * Create a EW socket - whether it is a server or client (normal) style is
   * determinted by the command line
   *
   * @param argline The argline as documented above
   * @param tg The tag to use on output
   * @param parent The parent EdgeThread to use for logging.
   */
  public EWImportSocket(String argline, String tg, EWImportServer parent) {
    //super(argline, tg);
    tag = "[" + tg + "]";
    par = parent;
    b = new byte[buf.length];            // raw data goes here
    ackbb = ByteBuffer.wrap(ackbuf);
    threadNumber = threadCount++;
    lastStatusOut = System.currentTimeMillis();
    setArgs(argline);     // Set arguments per the input command line
    start();
  }

  private void openSocket() {
    int loop = 0;
    int isleep = 16000;
    int sleepMax = Util.isNEICHost(exportHost) ? 10000 : 600000;
    instate = 1;
    while (true && !terminate && !EdgeMom.isShuttingDown()) {
      try { // Make sure anything we have open can be let go
        if (s != null) {
          try {
            if (!s.isClosed()) {
              s.close();
            }
          } catch (IOException e) {
          }
        }
        par.prta(Util.clear(runsb).append(tag).append(" Open Port=").append(exportHost).
                append("/").append(exportPort).append(" isleep=").append(isleep / 1000).
                append(" bind=").append(bindAddr));
        if (bindAddr != null) {
          s = new Socket(exportHost, exportPort, bindAddr, 0);
          par.prta(Util.clear(runsb).append(tag).append(" Open port bound=").append(exportHost).
                  append("/").append(exportPort).append(" on ").append(s.getLocalAddress()).
                  append("/").append(s.getLocalPort()));
        } else {
          s = new Socket(exportHost, exportPort);
        }
        par.prta(Util.clear(runsb).append(tag).append(" Open Port=").append(exportHost).append("/").
                append(exportPort).append(" successful! s=").append(s));
        break;
      } catch (UnknownHostException e) {
        par.prt(Util.clear(runsb).append(tag).append("* Host is unknown=").append(exportHost).
                append("/").append(exportPort).append(" loop=").append(loop));
        if (loop % 30 == 1) {
          SendEvent.edgeEvent("HostUnknown", "IMPORT client host unknown=" + exportHost + " " + tag, this);
        }
        loop++;
        try {
          sleep(120000L);
        } catch (InterruptedException e2) {
        }
      } catch (IOException e) {
        if (e.getMessage().equalsIgnoreCase("Connection refused")) {
          par.prta(Util.clear(runsb).append(tag).append("* Connection refused.  wait ").
                  append(isleep / 1000).append(" secs ...."));
          if (isleep >= sleepMax) {
            SendEvent.edgeSMEEvent("IMPORTBadCon", "Conn refused " + tag + " " + exportHost + "/" + exportPort, this);
          }
        } else if (e.getMessage().equalsIgnoreCase("Connection timed out")
                || e.getMessage().equalsIgnoreCase("Operation timed out")) {
          par.prta(Util.clear(runsb).append(tag).append("* Connection timed out.  wait ").
                  append(isleep / 1000).append(" secs ...."));
          SendEvent.edgeSMEEvent("IMPORTBadCon", "Conn timeout " + tag + " " + exportHost + "/" + exportPort, this);
        } else {
          //Util.IOErrorPrint(e,tag+"** IO error opening socket="+exportHost+"/"+exportPort);
          par.prta(Util.clear(runsb).append(tag).append("** IOError opening socket to ").
                  append(exportHost).append("/").append(exportPort).append(" e=").append(e));
          SendEvent.edgeSMEEvent("IMPORTBadCon", "IOErr " + tag + " " + exportHost + "/" + exportPort + " " + e, this);
        }
        isleep = isleep * 2;
        if (isleep > sleepMax) {
          isleep = sleepMax;
        }
        for (int i = 0; i < isleep / 1000; i++) {
          try {
            sleep(1000);
            if (terminate || EdgeMom.isShuttingDown()) {
              break;
            }
          } catch (InterruptedException e2) {
          }
        }

      }

      //try {sleep(10000L);} catch (InterruptedException e) {}
    }   // While True on opening the socket
    hbHandler.heartBeatIn();
    instate = 2;
  }

  @Override
  public void run() {
    running = true;
    state = EXPECTING_START;
    int pnt = 0;
    long lastDaily = System.currentTimeMillis();
    hbHandler.heartBeatIn();
    int lastSeq = 0;
    int l;
    boolean escaping = false;
    String str;
    boolean logoDone = false;
    byte[] ackseq = new byte[3];
    long lstBytesIn = 0;
    long lastBytesInTime = lastDaily;
    String ackStr = null;
    while (!terminate) {
      try {
        if (clientMode) {
          openSocket();
          instate = 3;
          if (terminate) {
            break;
          }
          hbHandler.heartBeatIn();
          par.prta(Util.clear(runsb).append(tag).append("Socket is open to ").append(s).append(" #bin=").append(bytesIn));
          state = EXPECTING_START;
          escaping = false;
        }
        if (s == null) {
          instate = 4;
          state = EXPECTING_START;
          escaping = false;
          try {
            sleep(1000);
          } catch (InterruptedException e) {
          }
          continue;
        } // probably server mode, do nothing
        if (terminate) {
          break;
        }
        int nsleft = 0;
        long now;
        int avail = 0;
        int nreset = 0;
        int ziplen = 0;
        int newlineAt = 0;
        int timeoutForAvail = hbIntervalTimeout * 10 * 4;
        if (timeoutForAvail < 3000) {
          timeoutForAvail = 3000; // has to be at least 5 minutes.
        }
        while (true) {
          instate = 5;
          try {
            int loop = 0;
            boolean isClosed = false;
            while ((avail = s.getInputStream().available()) <= 0 && !terminate) {
              try {
                sleep(100);
              } catch (InterruptedException e) {
              }
              isClosed = true;
              if (s != null) {
                isClosed = s.isClosed();
              }
              if (loop++ > timeoutForAvail || terminate || isClosed) {
                par.prta(Util.clear(runsb).append(tag).append(
                        "** no data available for a heart beat interval, terminated, or socket closed - force new connections term=").
                        append(terminate).append(" loop=").append(loop).append(" isClosed=").append(isClosed));
                break;
              }
            }
            if (loop > timeoutForAvail || isClosed) {
              break;    // we timed out waiting for some data
            }
            if (terminate && avail <= 0) {
              break;
            }
            if (terminate && avail > 0) {
              par.prt(Util.clear(runsb).append(tag).append("** read last bit before terminate=").append(s.getInputStream().available()));
            }
            // Get the header
            l = s.getInputStream().read(b, 0, Math.min(b.length, avail));
            if (l == 0) {
              break;       // EOF found skip
            }
            bytesIn += l;
            instate = 6;
            for (int i = 0; i < l; i++) {
              instate = 100 + state;
              switch (state) {
                case IN_MESSAGE:
                  if (b[i] == ESC && !obsRioZip) {    // If escaping is turned off, never use the escape logic
                    if (escaping) {        // escaping an escape!
                      buf[pnt++] = b[i];
                      escaping = false;
                    } else {
                      escaping = true;
                    }
                  } else {                  // Its not an escaped character
                    if (pnt >= buf.length) {
                      par.prt(Util.clear(runsb).append(tag).
                              append("* Got IMPORT buf longer than length double buf size. pnt=").
                              append(pnt).append(" buf.len=").append(buf.length));
                      byte[] tmp = new byte[buf.length * 2];
                      System.arraycopy(buf, 0, tmp, 0, buf.length);
                      buf = tmp;
                    }
                    buf[pnt++] = b[i];

                    // If this is GEOMAG data from an ObsRio - fine the message end
                    if (pnt >= LOGO_SIZE && buf[EWMessage.LOGO_TYPE_OFFSET] == EWMessage.TYPE_GEOMAG_DATA && obsRioZip) {
                      // This message is GZIP with a number of bytes, header line and zip message
                      if (ziplen == 0) {   // Have we decoded the size yet?
                        if (buf[pnt - 1] == '\n') {    // new line, we can now get the length of the message
                          newlineAt = pnt - 1;
                          //String line = new String(buf, 3, pnt-3);
                          ziplen = 0;
                          for (int ii = pnt - 6; ii >= 0; ii--) {
                            if (buf[ii] == 's' && buf[ii + 1] == 'i' && buf[ii + 2] == 'z' && buf[ii + 3] == 'e' & buf[ii + 4] == ':') {
                              for (int j = ii + 5; j < pnt; j++) {
                                if (buf[j] == ' ') {
                                  continue;
                                }
                                if (buf[j] == '\n') {
                                  break;
                                }
                                if (buf[j] >= '0' && buf[j] <= '9') {
                                  ziplen = ziplen * 10 + (buf[j] - '0');
                                }
                              }
                              break;
                            }
                          }
                          //par.prt(Util.clear(runsb).append(tag).append(" line=").append(line).append(" Zip header found!  ziplen=").append(ziplen));
                        }
                      } else {      // just filling in the zip len
                        if (pnt < ziplen + newlineAt + 2) {
                          continue;    // Skip all other processing for ETX
                        }                        // This character shouild be a ETX - if so unzip and build up message
                        if (buf[pnt - 1] != ETX) {
                          par.prta(Util.clear(runsb).append(tag).append(" *** Size does not end with ETX pnt=").append(pnt).
                                  append(" ziplen=").append(ziplen).append(" newLineAt=").append(newlineAt));
                          continue;
                        }
                        Inflater decomp = new Inflater();
                        zipIn += ziplen;
                        decomp.setInput(buf, newlineAt + 1, ziplen);
                        try {
                          synchronized (result) {
                            int ll = decomp.inflate(result);
                            zipOut += ll;
                            if (dbg) {
                              par.prta(Util.clear(runsb).append(tag).append("Decomp len=").append(ll).
                                      append(" newLineAt=").append(newlineAt).append(" ziplen=").append(ziplen).
                                      append(" pnt=").append(pnt - 1));
                            }
                            if (ll + newlineAt + 1 > buf.length) {
                              byte[] tmp = new byte[(ll + newlineAt) * 3 / 2];
                              par.prt(Util.clear(runsb).append(tag).
                                      append("* Got IMPORT decompress buf longer than length increase buf to size=").
                                      append(tmp.length).append(" buf.len=").append(buf.length));
                              System.arraycopy(buf, 0, tmp, 0, buf.length);
                              buf = tmp;
                            }
                            System.arraycopy(result, 0, buf, newlineAt + 1, ll);
                            pnt = newlineAt + ll + 1;
                          }
                        } catch (DataFormatException e) {
                          e.printStackTrace(par.getPrintStream());
                        }
                        ziplen = 0;       // 
                      }
                      // to get here the zip part has been acquired, next should be ETX
                    }
                    // IF its time to process the logo, do so
                    if (pnt == LOGO_SIZE && !logoDone) {      // The logo bytes are in convert them
                      if (buf[1] == 'S' && buf[2] == 'Q' && buf[3] == ':') {
                        ackStr = new String(buf, 1, 6);
                        ackseq[0] = buf[4];
                        ackseq[1] = buf[5];
                        ackseq[2] = buf[6];
                        try {
                          lastSeq = Integer.parseInt(ackStr.substring(3).trim());
                        } catch (NumberFormatException e) {
                        }
                        if (dbg) {
                          par.prta(Util.clear(runsb).append(tag).append("* Getting an ackable sequence").append(ackStr).
                                  append("|").append(Util.toAllPrintable(new String(ackseq))).append("|"));
                        }
                        if (!doAcks) {
                          par.prta(Util.clear(runsb).append(tag).append(" ** ACKs found in stream, turn on ACKing"));
                          doAcks = true;
                        }
                        buf[1] = buf[7];    // move the first 3 bytes of the ascii logo down
                        buf[2] = buf[8];
                        buf[3] = buf[9];
                        pnt = 4;
                        continue;         // need to get more characters
                      }
                      str = new String(buf, 1, 9);
                      try {
                        buf[2] = (byte) Integer.parseInt(str.substring(0, 3).trim());        // inst - Proper LOGO structure order
                        buf[1] = (byte) Integer.parseInt(str.substring(3, 6).trim());        // module
                        buf[0] = (byte) Integer.parseInt(str.substring(6, 9).trim());        // type
                      } catch (NumberFormatException e) {
                        par.prt(Util.clear(runsb).append(tag).append("****** Got parsing error doing logo=").append(e));
                        e.printStackTrace(par.getPrintStream());
                      }
                      //if(dbg) par.prt("Decode logo IMT="+str+"|"+str.substring(0,3).trim()+"|"+str.substring(3,6).trim()+"|"+str.substring(6,9).trim()+
                      //        "| t="+ buf[0]+" m="+buf[1]+" i="+buf[2]);
                      pnt = 3;
                      logoDone = true;
                    }
                    if (b[i] == ETX && !escaping) {
                      state = EXPECTING_START;
                      instate = 8;
                      /*if(dbg) par.prt(Util.clear(runsb).append(tag).append(tag+ " Processing message len="+pnt+" skipped="+skipped+" "+
                              buf[0]+" "+buf[1]+" "+buf[2]+" "+buf[3]+" "+buf[4]+" "+buf[5]+" "+buf[6]+" "+
                              buf[7]+" "+buf[8]+" "+buf[9]+" "+buf[10]+" "+
                              buf[11]+" "+buf[12]+" "+buf[13]+" "+bufGE[14]+" "+buf[15]+" "+
                              buf[16]+" "+buf[17]+" "+buf[18]+" "+buf[19]+" "+buf[20]+" "+buf[21]+" last="+buf[pnt-1]));*/
                      // If this is a ACKing link, send back the ACK
                      if (doAcks) { // This is an acknowlege exporter
                        instate = 9;
                        sendAck(ackseq);
                        //System.arraycopy(buf, 6, buf, 0, pnt);  // strip off the SQ:nnn from beginning of message
                      }
                      if (pnt > maxSize) {
                        maxSize = pnt;
                      }
                      if (buf[EWMessage.LOGO_TYPE_OFFSET] == EWMessage.TYPE_HEARTBEAT) {
                        instate = 10;
                        if (dbg || tag.contains("ObsRio")) {
                          par.prta(Util.clear(runsb).append(tag).append(" * Heart beat in ").
                                  append(new String(buf, 3, pnt - 4)).append(" avail=").append(s.getInputStream().available()));
                        }
                        hbHandler.heartBeatIn();
                      } else if (buf[EWMessage.LOGO_TYPE_OFFSET] == EWMessage.TYPE_ERROR) {
                        instate = 11;
                        par.prt(Util.clear(runsb).append(tag).append("* Got error type len=").append(pnt).
                                append(" type=").append(buf[EWMessage.LOGO_TYPE_OFFSET]).append(" mod=").
                                append(buf[EWMessage.LOGO_MODULE_OFFSET]).append(" inst=").
                                append(buf[EWMessage.LOGO_INSTITUTION_OFFSET]).append(" ").
                                append(buf[3]).append(" ").append(buf[4]).append(" ").append(buf[5]).
                                append(" ").append(buf[6]).append(" ").append(buf[7]).append(" ").
                                append(buf[8]).append(" ").append(buf[9]).append(" ").append(buf[10]).
                                append(" ").append(buf[11]).append(" ").append(buf[12]).append(" ").
                                append(buf[13]).append(" ").append(buf[14]).append(" ").append(buf[15]).
                                append(" ").append(buf[16]).append(" ").append(buf[17]).append(" ").
                                append(buf[18]).append(" ").append(buf[19]).append(" ").append(buf[20]).
                                append(" ").append(buf[21]).append(" last=").append(buf[pnt - 1]));
                      } else if (buf[EWMessage.LOGO_TYPE_OFFSET] == EWMessage.TYPE_TRACEBUF
                              || buf[EWMessage.LOGO_TYPE_OFFSET] == EWMessage.TYPE_TRACEBUF2) {
                        processTraceBuf(buf, pnt - 1);
                      } else if (buf[EWMessage.LOGO_TYPE_OFFSET] == EWMessage.TYPE_TRACE2_COMP_UA
                              || buf[EWMessage.LOGO_TYPE_OFFSET] == EWMessage.TYPE_TRACE_COMP_UA) {
                        instate = 12;
                        SendEvent.edgeSMEEvent("TRACE_UA_IMP", "Trace is in UA format ", this);
                        par.prta(Util.clear(runsb).append(tag).append("** TRACE is in UA form ***** ").
                                append(buf[EWMessage.LOGO_TYPE_OFFSET]).append(" ").append(buf[0]).append(" ").
                                append(buf[1]).append(" ").append(buf[2]).append(" ").append(buf[3]).append(" ").
                                append(buf[4]).append(" ").append(buf[5]).append(" ").append(buf[6]).append(" ").
                                append(buf[7]).append(" ").append(buf[8]).append(" ").append(buf[9]).append(" ").
                                append(buf[10]).append(" ").append(buf[11]).append(" ").append(buf[12]).append(" ").
                                append(buf[13]).append(" ").append(buf[14]).append(" ").append(buf[15]).append(" ").
                                append(buf[16]).append(" ").append(buf[17]).append(" ").append(buf[18]).append(" ").
                                append(buf[19]).append(" ").append(buf[20]).append(" ").append(buf[21]).
                                append(" last=").append(buf[pnt - 1]));
                      } else if (buf[EWMessage.LOGO_TYPE_OFFSET] == EWMessage.TYPE_GEOMAG_DATA) {
                        hbHandler.heartBeatIn();
                        processGeomagData(buf, pnt - 1, ackStr);
                      } else if (buf[EWMessage.LOGO_TYPE_OFFSET] == EWMessage.TYPE_GEOMAG_DATA2) { // non magnetic message ignore
                        if (dbg) {
                          par.prta(Util.clear(runsb).append(tag).append("GEOMAG_DATA2 message ignored len=").append(pnt));
                        }
                      } else {

                        instate = 13;
                        byte ty = buf[EWMessage.LOGO_TYPE_OFFSET];
                        if (ty != EWMessage.TYPE_PICK_SCNL && ty != EWMessage.TYPE_CODA_SCNL
                                && ty != EWMessage.TYPE_HYP2000ARC
                                && ty != EWMessage.TYPE_HINVARC && ty != EWMessage.TYPE_H71SUM
                                && ty != EWMessage.TYPE_PICK2K && ty != EWMessage.TYPE_PICK2
                                && ty != EWMessage.TYPE_CODA2K && ty != EWMessage.TYPE_CODA2
                                && ty != EWMessage.TYPE_PICK2K && ty != EWMessage.TYPE_PICK2K
                                && ty != EWMessage.TYPE_CUBIC && ty != EWMessage.TYPE_STRONGMOTIONII
                                && ty != EWMessage.TYPE_STRONGMOTION && ty != EWMessage.TYPE_LOC_GLOBAL) {
                          par.prt(Util.clear(runsb).append(tag).append("* Unhandled message type=").
                                  append(buf[EWMessage.LOGO_TYPE_OFFSET]).append(" ").append(buf[0]).append(" ").
                                  append(buf[1]).append(" ").append(buf[2]).append(" ").append(buf[3]).append(" ").
                                  append(buf[4]).append(" ").append(buf[5]).append(" ").append(buf[6]).append(" ").
                                  append(buf[7]).append(" ").append(buf[8]).append(" ").append(buf[9]).append(" ").
                                  append(buf[10]).append(" ").append(buf[11]).append(" ").append(buf[12]).append(" ").
                                  append(buf[13]).append(" ").append(buf[14]).append(" ").append(buf[15]).append(" ").
                                  append(buf[16]).append(" ").append(buf[17]).append(" ").append(buf[18]).append(" ").
                                  append(buf[19]).append(" ").append(buf[20]).append(" ").append(buf[21]).
                                  append(" last=").append(buf[pnt - 1]).append(" len=").append(pnt));
                        } else {
                          processParam(buf, pnt - 1);
                        }
                      }

                      npackets++;
                      if (npackets % (obsRioZip ? 50 : 1000) == 0) {
                        now = System.currentTimeMillis();
                        if (now - lastClientCheckForServer > 120000) {
                          par.processConfigFile(null);
                          lastClientCheckForServer = now;
                        }
                        par.prta(Util.clear(runsb).append(tag).append("#pkt=").append(npackets).
                                append(" in=").append((bytesIn - lstBytesIn) / 1000).append(" kB ").
                                append((bytesIn - lstBytesIn) * 8 / (now - lastBytesInTime)).append(" kbps").
                                append(zipIn > 0 ? " zipRatio=" + Util.df21(zipOut / (double) zipIn) : ""));
                        lstBytesIn = bytesIn;
                        lastBytesInTime = System.currentTimeMillis();
                        //hbHandler.heartBeatIn();
                      }
                    }
                    escaping = false;
                  }   // else - its a legitimite keeper character
                  break;
                case SEARCHING_START:
                  if (b[i] == STX) {
                    if (!escaping) {
                      state = EXPECTING_START;
                      par.prt(Util.clear(runsb).append(tag).append("Skipped ").append(skipped).append(" to get resynced"));
                    }
                  } else {
                    skipped++;
                    break;
                  }
                case EXPECTING_START:
                  if (b[i] == STX) {   // GOt it, if not bad news
                    pnt = 0;
                    buf[pnt++] = b[i];
                    state = IN_MESSAGE;
                    logoDone = false;
                  } else {
                    nsyncError++;
                    par.prt(Util.clear(runsb).append(tag).append("**** lost sync.  STX not where expected #=").append(nsyncError));
                    state = SEARCHING_START;
                    pnt = 0;
                  }
                  break;
                default:
                  par.prt(Util.clear(runsb).append(tag).append("*** Default - this should never happen"));
              }
            }
          } catch (IOException e) {
            if (e.toString().contains("Connection reset") || e.toString().contains("roken socket")) {
              par.prta(Util.clear(runsb).append(tag).append("* Connection reset or broken - reconnect"));
              nreset++;
              if (nreset > 10) {
                par.prta(tag + " got 10 resets.  Terminate...");
                terminate();
                break;
              }
            } else if (e.toString().contains("closed")) {
              par.prta(Util.clear(runsb).append(tag).append("* Socket is closed.  Start reopen."));
            } else {
              par.prta(Util.clear(runsb).append(tag).append("* IOError on socket e=").append(e));
              if (!terminate) {
                e.printStackTrace(par.getPrintStream());
              }
            }
            break;
          }
        }       // while true forever loop
        par.prta(Util.clear(runsb).append(tag).append("** Socket must be closed. terminate=").append(terminate));
        ndisconnect++;
        if (s != null) {
          if (!s.isClosed()) {
            try {
              s.close();
            } catch (IOException expected) {
            }
          }
        }
        s = null;
        if (!terminate) {
          try {
            sleep(45000);
          } catch (InterruptedException expected) {
          }
        }
      } // try on any outer loop IOError/Runtime exception // try on any outer loop IOError/Runtime exception // try on any outer loop IOError/Runtime exception // try on any outer loop IOError/Runtime exception
      catch (RuntimeException e) {
        par.prta(Util.clear(runsb).append(tag).append("* Got runtime exception e=").append(e));
        e.printStackTrace(par.getPrintStream());
        if (s != null) {
          if (!s.isClosed()) {
            try {
              s.close();
            } catch (IOException expected) {
            }
          }
        }
        s = null;
      }
    }   // ENd of loop to open socket

    if (s != null) {
      if (!s.isClosed()) {
        try {
          s.close();
        } catch (IOException expected) {
        }
      }
    }
    s = null;
    if (ds != null) {
      ds.close();
    }
    par.prta(tag + " has exited");
    if (csend != null) {
      csend.close();
    }
    running = false;
  }
  /**
   * Process a ObsRio geomag message. This format is
   * <inst><mod><type=160><Seq><Length>
   * <pre>
   * Earthworm logo for the ObsRio messages:
   * Inst ID = 59 (INST_USGSMAG in earthworm_global.d)
   * Module ID variable depending on source observatory (= 77 for test system)
   * Message Type ID = 160 for geomag data
   *
   *
   * Message format. The messages are space-delimited multi-line ascii. The first two lines are header information, followed by one or more data rows.    *
   * The first header line contains SCNL info: Network code and Station code, followed by one or more pairs of Channel and Location codes. The channel-loc pairs correspond to columns in the data rows.
   *
   * The second header line gives the complete timestamp of the first data line as MM/DD/YY hh:mm:ss
   *
   * The remaining lines are data rows containing integers. Column 1 is the time of day in milliseconds (0 - 86400000). The time value of the first data row should match the hh:mm:ss value in the header. Columns 2, 3, 4, etc., are data values for the channel-loc pairs listed in the header.
   *
   *
   * Sample 1-minute data message. Minute messages typically contain a single data row unless a backlog has built up:
   *
   * 1406678435 Received <inst: 59> <mod: 77> <type:160> <seq: 63> <Length:   122>
   * NT TST MVH R0 MVE R0 MVZ R0 MVF R0 MSF R0
   * First Element Time: 07/29/14 23:59:00
   * 86340000 -235068 -226466 -499949 597070 0
   *
   *
   * Sample 1-second data message. This message crosses the midnight boundary between 7/29 and 7/30:
   *
   * 1406678425 Received <inst: 59> <mod: 77> <type:160> <seq: 62> <Length:   871>
   * NT TST SVH R0 SVE R0 SVZ R0 SVF R0 SSF R0
   * First Element Time: 07/29/14 23:59:52
   * 86392000 -235067 -226466 -499955 597074 0
   * 86393000 -235067 -226465 -499957 597076 0
   * 86394000 -235068 -226465 -499953 597073 0
   * 86395000 -235068 -226465 -499944 597066 0
   * 86396000 -235068 -226466 -499940 597063 0
   * 86397000 -235068 -226466 -499944 597066 0
   * 86398000 -235068 -226466 -499946 597068 0
   * 86399000 -235068 -226466 -499946 597068 0
   * 0 -235067 -226466 -499945 597067 0
   * 1000 -235067 -226465 -499943 597065 0
   * 2000 -235067 -226466 -499946 597067 0
   * 3000 -235068 -226465 -499945 597066 0
   * 4000 -235068 -226465 -499948 597069 0
   * 5000 -235067 -226465 -499947 597068 0
   * 6000 -235067 -226465 -499950 597070 0
   * 7000 -235067 -226465 -499942 597064 0
   * 8000 -235067 -226466 -499947 597068 0
   * 9000 -235068 -226466 -499947 597068 0
   * 10000 -235068 -226466 -499951 597072 0
   * 11000 -235067 -226466 -499954 597074 0
   * </pre>
   *
   * @param b An ObsRio message described above
   * @param len The length of the ObsRio message including 3 byte logo.
   */
  ArrayList<StringBuilder> nscl = new ArrayList<>(8);
  GregorianCalendar geotime = new GregorianCalendar();
  int[] geoval = new int[1];
  private final int GEOHDR1 = 0;
  private final int GEOHDR2 = 1;
  private final int GEODATA = 2;

  public void processGeomagData(byte[] b, int len, String ackstr) {
    byte inst = b[EWMessage.LOGO_INSTITUTION_OFFSET];   // Geomag is 59
    byte module = b[EWMessage.LOGO_MODULE_OFFSET];      // This corresponds to a station test station=77
    byte type = b[EWMessage.LOGO_TYPE_OFFSET];          // Should always be 160
    String stmp = new String(buf, 3, len - 3);

    String[] lines = stmp.split("\n");

    boolean dbg2 = dbg;
    int phase = GEOHDR1;
    String[] hdr1 = null;
    String hdr2;
    String net;
    String station;
    long timebase = 0;
    long baseMillis = -1;           // Base is the time of the first sample of this packet in millis since midnight
    double rate = 1.;
    int nsamp = 0;
    boolean sizeLine = false;
    boolean emptyLine = false;
    int nlines = 0;
    for (String line1 : lines) {
      // USe the phse to decode the lines, is it a HDR1
      switch (phase) {
        case GEOHDR1:
          try {
            if (line1.length() > 10) {
              hdr1 = line1.split("\\s");
              if (hdr1[0].equals("0.01667")) {
                rate = 1 / 60.;
              } else {
                rate = Double.parseDouble(hdr1[0]);
              }
              net = hdr1[1];
              station = hdr1[2];
              int off = 3;
              for (StringBuilder nscl1 : nscl) {
                Util.clear(nscl1); // Clear the StringBuilders
              }
              int i = 0;
              while (off < hdr1.length) {
                String chan = hdr1[off];
                String loc = hdr1[off + 1];
                if (i >= nscl.size()) {
                  nscl.add(i, new StringBuilder(12));
                }
                nscl.get(i).append(net.concat("  ").substring(0, 2)).
                        append(station.concat("     ").substring(0, 5)).
                        append(chan.concat("   ").substring(0, 3)).
                        append(loc.concat("  ").substring(0, 2));
                if (nscl.get(i).indexOf(dbgchan) >= 0) {
                  dbg2 = true; // is there a debug channel
                  par.prta(tag + " Packet dbg : sq=" + ackstr + "|");
                  for (int ii = 0; ii < Math.min(8, lines.length); ii++) {
                    par.prta(ii + ":" + lines[ii]);
                  }
                }
                i++;
                off += 2;
              }

              phase = GEOHDR2;
            }
          } catch (NumberFormatException e) {
            par.prta(Util.clear(runsb).append(tag).append("PGEO: Runtime trying to parse GEOMAG HDR1 - e=").
                    append(e).append(" line=").append(line1).append("| hdr1=").
                    append(hdr1 == null ? "Null" : hdr1.length).append("|"));
            e.printStackTrace(par.getPrintStream());
          } catch (RuntimeException e) {
            par.prta(Util.clear(runsb).append(tag).append("PGEO: Runtime trying to parse GEOMAG HDR1 - e=").
                    append(e).append(" line=").append(line1).append("| hdr1=").
                    append(hdr1 == null ? "Null" : hdr1.length).append("|"));
            e.printStackTrace(par.getPrintStream());
          }
          break;
        case GEOHDR2:
          try {
            if (line1.contains("size:")) {
              if (dbg2) {
                par.prta(Util.clear(runsb).append(tag).append("Size line :").append(line1));
              }
              sizeLine = true;
              continue;
            }
            if (line1.trim().length() < 10) {
              emptyLine = true;
              continue;  // sometimes an empty line
            }
            hdr2 = line1;
            if (dbg2) {
              par.prta(Util.clear(runsb).append(tag).append(" date line:").append(hdr2));
            }
            // Decode the time from the odd mm/dd/yy hh:mm:ss.dddd format
            String[] dateTime = hdr2.substring(hdr2.indexOf(":") + 1).trim().split("/");
            int yr = Integer.parseInt(dateTime[2].substring(0, dateTime[2].indexOf(" ")));
            if (yr < 2000) {
              yr += 2000;
            }
            int mon = Integer.parseInt(dateTime[0].trim());
            int dom = Integer.parseInt(dateTime[1].trim());
            nsamp = Integer.parseInt(hdr2.substring(hdr2.lastIndexOf(":") + 1).trim());
            if (dbg2) {
              par.prta(Util.clear(runsb).append(tag).append("Geomag HDR2 :").append(line1).append("|"));
            }
            if (nsamp != lines.length - 2 - (emptyLine ? 1 : 0) - (sizeLine ? 1 : 0)) {
              par.prta(Util.clear(runsb).append(tag).append("PGEO: number of samples disagrees with number of lines nsamp=").
                      append(nsamp).append(" nlists=").append(lines.length) + " size=" + sizeLine + " empty=" + emptyLine);
            }

            geotime.set(yr, mon - 1, dom);                           // This is the beginning of the day
            timebase = geotime.getTimeInMillis() / 86400000L * 86400000L; // cleanup so its exactly the day boundary
            phase = GEODATA;
          } catch (NumberFormatException e) {
            par.prta(Util.clear(runsb).append(tag).append("PGEO: Runtime trying to parse GEOMAG HDR2 e=").
                    append(e).append(" line=").append(line1));
            e.printStackTrace(par.getPrintStream());
          }
          break;
        // end of if GEODATA
        case GEODATA:
          try {
            // Data lines look like (space delimited)
            // millisSinceMidnight data1 data2 .... datan
            nlines++;
            String line = line1;
            String[] vals = line.split("\\s");                      // Split the line on spaces
            if (vals.length < 2) {
              continue;                           // An empty or malformed line
            }
            long timeInMills = Long.parseLong(vals[0]);             // millis since last midnight
            if (baseMillis < 0) {
              baseMillis = timeInMills;            // Save millis of first packet
            }
            if ((timeInMills < baseMillis)) // If this is smaller, its now tomorrow
            {
              timeInMills += 86400000L;                              // if its < -1/2 day old, the data just crossed midnight of next day
            }
            geotime.setTimeInMillis(timebase + timeInMills);        // Time of the sample
            for (int i = 1; i < vals.length; i++) {
              geoval[0] = Integer.parseInt(vals[i]);
              if (i >= nscl.size() + 1) { // Too many data samples
                par.prta(Util.clear(runsb).append(tag).append(
                        "PGEO: Too many data Sample on line for number of channels.   Skip sample i=").
                        append(i).append("line=").append(line1).append("\npacket=").append(stmp));
                continue;
              }
              StringBuilder seedname = nscl.get(i - 1);

              RawToMiniSeed.addTimeseries(geoval, 1, seedname,
                      geotime.get(Calendar.YEAR), geotime.get(Calendar.DAY_OF_YEAR),
                      ((int) (geotime.getTimeInMillis() % 86400000) / 1000), ((int) (geotime.getTimeInMillis() % 1000) * 1000), rate,
                      0, 0, 0, 0, par);
              Channel c = EdgeChannelServer.getChannel(seedname);
              //if(seedname.indexOf("REPV15")>= 0)  par.prta(seedname+": "+c.toString()+" flags="+c.getFlags()+" mask="+mask+" hydra="+hydra);
              if (c != null) {
                if ((c.getFlags() & MASK) != 0) {
                  if (npackets % 10 == 0) {
                    par.prt(Util.clear(runsb).append(tag).append(seedname).append(" **** is marked no trace input- skip"));
                  }
                  return;
                }     // Channel is disable from trace wire input

                if (Math.abs(c.getRate() - rate) / c.getRate() > 0.01) {
                  par.prta(Util.clear(runsb).append(tag).append("PGEO: ***** rates mismatch ").append(seedname).
                          append(" chan=").append(c.getRate()).append(" trace=").append(rate).
                          append(" buf rate=").append(c.getRate()));
                  SendEvent.debugSMEEvent("TBLBadRate", "rates mismatch " + seedname + " chan=" + c.getRate() + " trace=" + rate, this);
                }
              } else {
                par.prta(Util.clear(runsb).append(tag).append("PGEO: ***** new channel found=").append(seedname));
                SendEvent.edgeSMEEvent("ChanNotFnd", "TraceBuf Channel not found=" + seedname + " new?", this);
                /*Util.clear(dbmsg).append("edge^channel^channel=").append(seedname).
                append(";commgroupid=0;operatorid=0;protocolid=0;sendto=0;flags=0;links=0;delay=0;sort1=new;sort2=;rate=").
                append(rate).append(";nsnnet=0;nsnnode=0;nsnchan=0;created_by=0;created=current_timestamp();");*/
                EdgeChannelServer.createNewChannel(seedname, rate, this);
              }
              if (rawSend) { // Normally lower rate data to QueryMom so it can be in RAM spans earlier than MiniSeed
                if (rate <= rawMaxRate) {
                  if ((dbg2 || nscl.get(i - 1).indexOf(dbgchan) >= 0) && (nlines < 4 || nlines > nsamp - 3) && i == 1) {
                    par.prta(Util.clear(runsb).append(tag).append(nlines).
                            append(":OI.sendRaw() ").append(nscl.get(i - 1)).append(" ").
                            append(Util.ascdatetime2(geotime)).append(" ").append(geoval[0]).
                            append(" rt=").append(rate).append(" ns=1 nsamp=").append(nsamp));
                  }
                  OutputInfrastructure.sendRaw(c, nscl.get(i - 1), geotime.getTimeInMillis(), rate, 1, geoval);
                }
              }
            }
          } catch (NumberFormatException e) {
            par.prta(Util.clear(runsb).append(tag).append("PGEO: Runtime trying to parse GEOMAG Data e=").
                    append(e).append(" line=").append(line1));
            e.printStackTrace(par.getPrintStream());
          }
          break;
        default:
          par.prta(Util.clear(runsb).append(tag).append("Impossible phase in processGeomagData=").append(phase));
          break;
      }
    } // end for loop on each line
  }

  /**
   * process a non tracebuf - send it on port 40100+type as UDP, frag if
   * necessary
   *
   * @param b The buffer with the message
   * @param len The length of the message in bytes (this include 3 logo bytes at
   * beginning)
   */
  public void processParam(byte[] b, int len) {
    ubuf[OFF_INST] = b[EWMessage.LOGO_INSTITUTION_OFFSET];    // Switch the Institution, module and type to UDP position from logo position
    ubuf[OFF_MOD] = b[EWMessage.LOGO_MODULE_OFFSET];
    ubuf[OFF_TYPE] = b[EWMessage.LOGO_TYPE_OFFSET];
    ubuf[OFF_SEQNO] = Module.processSeqOut(ubuf, paramPortOffset + ubuf[OFF_TYPE], par);// get next sequence for this mod, inst and port
    ubuf[OFF_FRAGNO] = 0;

    // see if this is a pick SCNL message for kafka
    if (jsonSender != null && ubuf[OFF_TYPE] == EWMessage.TYPE_PICK_SCNL) {
      String msg = new String(b, 6, len);
      //par.prta("inst="+ubuf[OFF_INST]+" MOD="+ubuf[OFF_MOD]+" type="+ubuf[OFF_TYPE]+" seq="+ubuf[OFF_SEQNO]+" msg="+msg+"|");
      String[] str = msg.split("\\s");
      //for(int i=0; i<s.length; i++) par.prt(i+" = "+s[i]);
      String[] scnl = str[3].split("\\.");
      String seedname = Util.makeSeedname(scnl[2], scnl[0], scnl[1], scnl[3]);
      seedname = seedname.replaceAll("-", " ");
      int yr = Integer.parseInt(str[5].substring(0, 4));
      int mon = Integer.parseInt(str[5].substring(4, 6));
      int day = Integer.parseInt(str[5].substring(6, 8));
      int hr = Integer.parseInt(str[5].substring(8, 10));
      int min = Integer.parseInt(str[5].substring(10, 12));
      int sec = Integer.parseInt(str[5].substring(12, 14));
      double fract = Double.parseDouble(str[5].substring(14).trim());
      geotime.set(yr, mon - 1, day, hr, min, sec);
      geotime.setTimeInMillis(geotime.getTimeInMillis() / 1000 * 1000 + (int) (fract * 1000. + 0.5));
      String polarity = str[4].substring(0, 1);
      String author = Util.toHex(ubuf[OFF_INST]).substring(2)
              + Util.leftPad(Util.toHex(ubuf[OFF_MOD]).substring(2), 2)
              + Util.leftPad(Util.toHex(ubuf[OFF_TYPE]).substring(2), 2);
      author = author.replaceAll(" ", "0");

      long pickID = PickerManager.pickToDB(agency, author, geotime.getTimeInMillis(),
              seedname, 0., 0., "P", 0, // nscl, amp, period, phase, seq
              1, 0., polarity, "?", 0., // version, pickerr, polar, onset hipass
              0., 0, 0, 0., 0., 0., 0, 0., 0., 0., 0., par); // low, backazm, bazerr, slow,snr, power, pickerid, dist, azm, resid, sigma
      jsonSender.sendPick(Util.getSystemNumber() + "_" + pickID, null, 
              agency, author, geotime, seedname,
              0., 0., "P", 0., // amp, per, phase,
              (polarity.equals("U") ? "up" : (polarity.equals("D") ? "down" : null)),
              "questionable", "earthworm", 0., 0., 0.); // onset, pickerTYpe, hi,lo,snr
    }

    // Turn this into UDP packets to send on the Edge wire if its configured.
    if (len > 1350) {
      par.prt(tag + "len=" + len + "|" + new String(b, 3, len - 3) + "|");
    }
    int off = 3;
    if (hydra && !nooutput && dp != null) {      // Send out UDP packets if true
      dp.setPort(paramPortOffset + ubuf[OFF_TYPE]);// Point packet to right port
      while (off < len) {
        int ll = (len - off > MAX_UDP_LENGTH - 6 ? MAX_UDP_LENGTH - 6 : len - off);// number of bytes to use from b this time
        System.arraycopy(b, off, ubuf, 6, ll);
        if (ll == (len - off)) {
          ubuf[OFF_LAST] = 1;    // This is the last UDP to send
        } else {
          ubuf[OFF_LAST] = 0;      // This is not the last, do frags
        }
        dp.setData(ubuf, 0, ll + 6);
        dp.setLength(ll + 6);
        par.prta(Util.clear(runsb).append(tag).append("Send2 i=").append(ubuf[OFF_INST]).
                append(" m=").append(ubuf[OFF_MOD]).append(" ty=").append(ubuf[OFF_TYPE]).
                append(" sq=").append(((int) ubuf[OFF_SEQNO]) & 255).append(" frg=").append(ubuf[OFF_FRAGNO]).
                append(" last=").append(ubuf[OFF_LAST]).append(" len=").append(ll + 6).append(" ").
                append(EWMessage.getTypeString(ubuf[OFF_TYPE])).append(" ").
                append(Util.toAllPrintable(new String(ubuf, 6, ll))));
        try {
          if (hydra && !nooutput) {
            ds.send(dp);
          }
        } catch (IOException e) {
          par.prta(Util.clear(runsb).append(tag).append("****** IOError2=").append(e));
        }
        ubuf[OFF_FRAGNO]++;
        off += ll;
      }
    }
  }

  /**
   * process a raw trace buf int miniSeed
   *
   * @param b The array with a trace buf including logo
   * @param len The length of b
   */
  public void processTraceBuf(byte[] b, int len) {

    // check for sequence error from the modules
    //if(len > -20000000) return;
    instate = 13;
    try {
      tb.setDataMessageFormat(b, 0, len);
    } catch (IndexOutOfBoundsException e) {
      par.prt(Util.clear(runsb).append(tag).append("**** message is too long len=").append(len));
      return;
    }
    StringBuilder seedname = tb.getSeedNameSB();
    if (scn) {
      seedname.replace(10, 12, "  ");
    }
    boolean dbg2;
    if (seedname.indexOf(dbgchan) >= 0) {
      dbg2 = true;
    } else {
      dbg2 = dbg;
    }
    if (seedname.substring(7, 10).trim().length() < 3) {
      //continue;
      String chn = seedname.substring(7, 10).trim().toUpperCase();
      if (Character.isLowerCase(seedname.charAt(6))) {
        return;   // This gets HVO time code signals.
      }
      switch (chn) {
        case "SZ":
          seedname.replace(7, 10, "SHZ");
          break;
        case "BZ":
          seedname.replace(7, 10, "BHZ");
          break;
        case "SE":
          seedname.replace(7, 10, "SHE");
          break;
        case "BE":
          seedname.replace(7, 10, "BHE");
          break;
        case "SN":
          seedname.replace(7, 10, "SHN");
          break;
        case "BN":
          seedname.replace(7, 10, "BHN");
          break;
        case "LZ":
          seedname.replace(7, 10, "LHZ");
          break;
        case "LN":
          seedname.replace(7, 10, "LHN");
          break;
        case "LE":
          seedname.replace(7, 10, "LHE");
          break;
        default:
          par.prta(Util.clear(runsb).append(tag).append("** Unknown channel type in EWImportSocket ").append(seedname));
          return;
      }
      if ((nchanges++ % 1000) == 0) {
        par.prta(Util.clear(runsb).append(tag).
                append(" Change ").append(tb.getSeedNameString()).append(" to ").append(seedname));
      }
      tb.setSeedname(seedname);
    }
    if (excludes != null) {
      if (seedname.toString().trim().matches(excludes)) {
        return;    // Note we have to convert to String to use matches
      }
    }
    if (translationsFile != null) {
      StringBuilder trans = translations.get(Util.getHashFromSeedname(seedname));
      if (trans != null) {
        tb.setSeedname(trans);
        //par.prta(tag+"Translate Seedname from "+seedname+" to "+trans+" "+tb.toString());
        seedname = trans;
      }
    }
    if (dbg2) {
      par.prta(tag + "PTBL: l=" + len + " " + seedname + " " + tb.toString());
    }
    //int [] data = tb.getData();
    instate = 14;
    try {
      instate = 141;
      MasterBlock.checkSeedName(seedname);
    } catch (IllegalSeednameException e) {
      instate = 142;
      par.prta(Util.clear(runsb).append(tag).append("** Bad name = ").append(e));
      return;
    }
    instate = 143;
    Channel c = EdgeChannelServer.getChannel(seedname);
    //if(seedname.indexOf("REPV15")>= 0)  par.prta(seedname+": "+c.toString()+" flags="+c.getFlags()+" mask="+mask+" hydra="+hydra);
    if (c != null) {
      instate = 144;
      if ((c.getFlags() & MASK) != 0) {
        if (npackets % 10 == 0) {
          par.prt(seedname + " **** is marked no trace input- skip");
        }
        return;
      }     // Channel is disable from trace wire input
      instate = 145;
      if (Math.abs(c.getRate() - tb.getRate()) / c.getRate() > 0.01) {
        if (nTBRateMismatch++ % 100 == 0) {
          par.prta(Util.clear(runsb).append(tag).append("PTBL: ***** rates mismatch ").append(seedname).
                  append(" E=").append(nTBRateMismatch).
                  append(" chan=").append(c.getRate()).append(" trace=").append(tb.getRate()).
                  append(" buf rate=").append(tb.getRate()));
          SendEvent.debugSMEEvent("TBLBadRate", "rates mismatch " + seedname + " chan=" + c.getRate() + " trace=" + tb.getRate() + " #" + nTBRateMismatch, this);
        }
        tb.setRate(c.getRate());

      }
    } else {
      instate = 146;
      par.prta(Util.clear(runsb).append(tag).append("PTBL: ***** new channel found=").append(seedname));
      SendEvent.edgeSMEEvent("ChanNotFnd", "TraceBuf Channel not found=" + seedname + " new?", this);
      /*Util.clear(dbmsg).append("edge^channel^channel=").append(seedname).
              append(";commgroupid=0;operatorid=0;protocolid=0;sendto=0;flags=0;links=0;delay=0;sort1=new;sort2=;rate=").
              append(tb.getRate()).append(";nsnnet=0;nsnnode=0;nsnchan=0;created_by=0;created=current_timestamp();");*/
      EdgeChannelServer.createNewChannel(seedname, tb.getRate(), this);
      instate = 147;
    }
    instate = 15;

    // The Chan process is looking for gaps and gathering statistics
    //boolean oorSkip = false;
    /*Chan ch;
    synchronized(chans) {
      ch = chans.get(Util.getHash(seedname));
      if(ch == null) {
        ch = new Chan(seedname, tb, par);
        chans.put(Util.getHash(seedname), ch);
        oorSkip=true;   // since we created it we do not need to process it
      }
    }
    if(!oorSkip) oorSkip = ch.process(tb);*/
    // Process the data via RawToMiniSeed, send to channel digest, etc.
    start.setTimeInMillis(tb.getStarttime());
    if (nooutput && dbg2) {
      par.prta(Util.clear(runsb).append(tag).append("TBL: nooutput TS=").append(seedname).append(" ").
              append(Util.ascdatetime2(start, null)).
              append(" n=").append(tb.getNsamp()).append(" rt=").append(tb.getRate()).
              append(" dbg=").append(dbg));
    }
    if (!nooutput) {
      // do latency calculation
      long lat = (System.currentTimeMillis() - tb.getNextExpectedTimeInMillis() - (int) (1000 / tb.getRate() + .45));
      latencySum += Math.min(300000, lat);
      nlatency++;
      if (dbg2) {
        par.prta(Util.clear(runsb).append(tag).append("TBL: addTs ").append(seedname).
                append(" ").append(Util.ascdatetime2(start)).append(" n=").append(tb.getNsamp()).
                append(" rt=").append(tb.getRate()).append(" lat=").append(lat));
      }
      if (dbg2) {
        RawToMiniSeed.setDebugChannel(dbgchan);
      }

      instate = 16;
      if (tb.getRate() <= 0.) {
        if (nZeroRate++ % 1000 == 0) {
          par.prta(Util.clear(runsb).append("Discard packet with zero rate #=").append(nZeroRate).
                  append(" ").append(tb.toStringBuilder(null)));
        }
      } else {
        RawToMiniSeed.addTimeseries(tb.getData(), tb.getNsamp(), seedname,
                start.get(Calendar.YEAR), start.get(Calendar.DAY_OF_YEAR),
                ((int) (start.getTimeInMillis() % 86400000) / 1000), ((int) (start.getTimeInMillis() % 1000) * 1000), tb.getRate(),
                0, 0, 0, 0, par);
      }

      if (rawSend) // Normally lower rate data to QueryMom so it can be in RAM spans earlier than MiniSeed
      {
        if (tb.getRate() <= rawMaxRate) {
          if (dbg2) {
            par.prta(Util.clear(runsb).append(tag).append("OI.sendRaw() ").append(seedname).
                    append(" ").append(Util.ascdatetime2(start)).append(" rt=").append(tb.getRate()).
                    append(" ns=").append(tb.getNsamp()));
          }
          OutputInfrastructure.sendRaw(c, seedname, start.getTimeInMillis(), tb.getRate(), tb.getNsamp(), tb.getData());
        }
      }
      try {
        instate = 17;
        if (!nocsend) {
          csend.send(SeedUtil.toJulian(start.get(Calendar.YEAR), start.get(Calendar.DAY_OF_YEAR)),
                  (int) (start.getTimeInMillis() % 86400000L), seedname,
                  tb.getNsamp(), tb.getRate(), len);
        }
      } catch (IOException e) {
        if (e.getMessage().contains("operation interrupted")) {
          par.prta(Util.clear(runsb).append(tag).append("TBS: got Interrupted sending channel data"));
        } else {
          par.prta(Util.clear(runsb).append(tag).append("TBS: got IOException sending channel ").append(e.getMessage()));
        }
      }

      instate = 18;
      if (hydra) {
        Hydra.sendNoChannelInfrastructure(tb);    // send data straight to Hydra
      }
      if (!noDB) {
        GapsFromInorderMaker.processChannel(start.getTimeInMillis(), seedname, tb.getNsamp(), tb.getRate(), inorderGapType);
      }
    }
    instate = 19;
  }

  private void sendAck(byte[] buf) throws IOException {
    ackbb.position(14);       // the STX, Logo (9 bytes) and ACK: are preset
    ackbb.put(buf, 0, 3);   // Add the ack bytes
    ackbb.put((byte) ETX);

    if (dbg) {
      par.prta(Util.clear(runsb).append(tag).append(export).append(" ACK sq=").
              append(new String(buf, 0, 3)).append("|").
              append(Util.toAllPrintable(new String(ackbuf, 0, ackbb.position()))));
    }
    s.getOutputStream().write(ackbuf, 0, ackbb.position());
    bytesOut += ackbb.position();
  }

  public static String toTimeString(short yrdoy, int husecs) {
    int yr = (yrdoy / 367) + 2000;
    int doy = yrdoy % 367;
    if (husecs < 0) {
      int julian = SeedUtil.toJulian(yr, doy);
      julian--;
      int[] ymd = SeedUtil.fromJulian(julian);
      yr = ymd[0];
      doy = SeedUtil.doy_from_ymd(ymd);
      husecs += 864000000;
    }
    int hr = husecs / 36000000;
    husecs -= hr * 36000000;
    int min = husecs / 600000;
    husecs -= min * 600000;
    int sec = husecs / 10000;
    husecs -= sec * 10000;
    return "" + yr + " " + Util.df3(doy) + ":" + Util.df2(hr) + ":" + Util.df2(min) + ":" + Util.df2(sec) + "." + Util.df4(husecs);
  }

  private final class EWHeartBeatHandler extends Thread {

    byte[] hb;
    String hbAsString;
    int interval;
    int rcvinterval;
    long lastHeartBeatIn;
    int nhbTimeouts;
    boolean terminate;
    StringBuilder runsb = new StringBuilder(50);

    public void terminate() {
      terminate = true;
      this.interrupt();
    }

    public void heartBeatIn() {
      lastHeartBeatIn = System.currentTimeMillis();
    }

    public int getHeartBeatTimeOuts() {
      return nhbTimeouts;
    }

    public EWHeartBeatHandler(byte inst, byte module, String msg, int intersec, int recintsec) {
      hb = new byte[10 + msg.length() + 1];
      hb[0] = STX;
      hb[10 + msg.length()] = ETX;
      String s2 = Util.df3(inst) + Util.df3(module) + Util.df3(EWMessage.TYPE_HEARTBEAT);
      System.arraycopy(s2.getBytes(), 0, hb, 1, 9);
      System.arraycopy(msg.getBytes(), 0, hb, 10, msg.length());
      hbAsString = Util.toAllPrintable(new String(hb)).toString();
      interval = intersec;
      rcvinterval = recintsec;
      start();
    }

    @Override
    public void run() {
      long curr;
      long lastWrite = System.currentTimeMillis();
      while (!terminate) {
        try {
          sleep(5000);
        } catch (InterruptedException expected) {
        }
        if (s != null) {     // if we have a connection
          try {
            if (!s.isClosed()) {
              curr = System.currentTimeMillis();
              if (curr - lastWrite >= interval * 1000) {
                if (dbg) {
                  par.prta(Util.clear(runsb).append(tag).append(" * Write HeartBeat|").append(hbAsString).append("|"));
                }
                s.getOutputStream().write(hb);
                bytesOut += hb.length;
                lastWrite = curr;
              }
              if (curr - lastHeartBeatIn > rcvinterval * 1000) {      // has it been too long for inbound heartbeats
                par.prta(Util.clear(runsb).append(tag).append("** No heartbeats from EXPORT end in ").append((System.currentTimeMillis() - lastHeartBeatIn) / 1000).append(" secs"));
                nhbTimeouts++;
                lastHeartBeatIn = curr;   // let it time out again.
                try {
                  if (!s.isClosed()) {
                    s.close();
                  }
                } catch (IOException e) {
                  par.prt(Util.clear(runsb).append(tag).append("IOError closing socket on heartbeat time out e=").append(e));
                }
              }
            }
          } catch (IOException e) {
            par.prta(Util.clear(runsb).append("IOError in EWHeartBeat handler ").append(s.isClosed() ? "closed" : "open").append(" s=").append(s));
            if (e.toString().contains("Connection reset")) {
              par.prta(tag + " EW Heartbeat error.  Close socket and continue e=Connection reset ");
            } else if (e.toString().contains("Broken pipe")) {
              par.prta(tag + " EW Heartbeat error.  Close socket and continue e=Broken pipe");
            } else if (e.toString().contains("Socket close")) {
              par.prta(tag + " EW Heartbeat error.  Close socket and continue e=Socket close");
            } else {
              par.prta(tag + " EW Heartbeat error.  Close socket and continue  e=" + e);
              e.printStackTrace(par.getPrintStream());
            }
            if (s != null) {
              if (!s.isClosed()) {
                try {
                  s.close();
                } catch (IOException expected) {
                }
              }
            }
          }
        }
      }
      par.prta(tag + "EWHeartBeatHandler is exiting.");
    }

  }

}
