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
import gov.usgs.anss.edge.EdgeProperties;
import gov.usgs.anss.edge.ChannelSender;
import gov.usgs.alarm.SendEvent;
import gov.usgs.anss.db.DBConnectionThread;
import gov.usgs.anss.db.DBMessageQueuedClient;
import gov.usgs.anss.edge.*;
import gov.usgs.anss.edgeoutput.TraceBuf;
import gov.usgs.anss.edgethread.EdgeThread;
import gov.usgs.anss.edgethread.ManagerInterface;
import gov.usgs.anss.net.SNWSender;
import gov.usgs.anss.rtp.*;
import gov.usgs.anss.seed.Steim1;
import gov.usgs.anss.seed.Steim2;
import gov.usgs.anss.seed.SteimException;
import gov.usgs.anss.util.SeedUtil;
import gov.usgs.anss.util.Util;
import gov.usgs.edge.config.Channel;
import java.io.*;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.GregorianCalendar;
import java.util.Iterator;
import java.util.TreeMap;

/**
 * This client connects to a Reftek server, receives the data from that port and
 * puts it into an edge database. It has the ability to expand if mixed sized
 * packets are found. It will break 4k packets into 512 packets by default.
 *
 * <PRE>
 *Switch   arg        Description
 *-h       host    The host ip address or DNS translatable name of the RTPS
 *-p       pppp    The port to connect to on the RTPD (def=2543)
 *-dbg             Debug output is desired
 *-noudpchan       Do not send results to the UdpChannel server for display in things like channel display
 *-nohydra         Do not send the resulting data to Hydra (usually used on test systems to avoid Hydra duplicates)
 * </PRE>
 *
 * @author davidketchum
 */
public class ReftekClient extends EdgeThread implements ManagerInterface {

  public static int version = 2;
  private final TreeMap<String, Integer> pinnos = new TreeMap<>();
  private final TreeMap<String, Reftek> refteks = new TreeMap<>();
  // frp, Table 3 page 2 of manu :RTP Message Types
  public final short RTP_MSG_REFTEK = 0, RTP_MSG_CMDPKT = 1, RTP_MSG_NOP = 2,
          //              1                  2                4
          RTP_MSG_ATTR = 3, RTP_MSG_SOH = 4, RTP_MSG_START = 5, RTP_MSG_STOP = 6,
          //  8                 16           32 x20              64 x40
          RTP_MSG_FLUSH = 7, RTP_MSG_BREAK = 8, RTP_MSG_BUSY = 9, RTP_MSG_FAULT = 10, RTP_MSG_PID = 11,
          //  128 x80            256 x100           512 x200        1024 xa       2048  xb
          RTP_MSG_CONMSG = 12, RTP_MSG_REFTEK_XTND = 13, RTP_MSG_CMDPKT_XTND = 14, RTP_OOR = 15;
  //  4096 xc               8192  xd            16384 xe
  // From page 13 of manual Table 11 PMASK bits
  public final int RTP_PMASK_SPEC = 1, RTP_PMASK_AD = 0x2, RTP_PMASK_CD = 0x4, RTP_PMASK_DS = 0x8,
          RTP_PMASK_DT = 0x10, RTP_PMASK_EH = 0x20, RTP_PMASK_ET = 0x40, RTP_PMASK_OM = 0x80,
          RTP_PMASK_SH = 0x100, RTP_PMASK_SC = 0x200, RTP_PMASK_CMND = 0x400, RTP_PMASK_FD = 0x800,
          RTP_PMASK_130CC = 0x1000, RTP_PMASK_130CCCL = 0x2000, RTP_PMASK_MUXD = 0x4000, RTP_PMASK_SMSD = 0x8000,
          RTP_PMASK_SND = 0x10000, RTP_PMASK_160CC = 0x20000,
          RTP_PMASK_ALL = 0x3ffff, RTP_PMASK_NONE = 0;
  public final String[] RTP_TYPES = {"REFTEK", "CMDPKT", "NOP/HEARTBEAT", "ATTR",
    "SOH", "START", "STOP", "FLUSH", "BREAK", "BUSY", "FAULT", "PID", "CONMSG", "XTND", "CMD_XNTD", "OOR"};
  private static SNWSender snwsender;
  private static DBMessageQueuedClient statusClient;

  private int port;
  private String host;
  private int countMsgs;
  private int heartBeat;
  private Socket d;           // Socket to the server
  private long lastStatus;     // Time last status was received
  private InputStream in;
  private OutputStream outsock;
  private ChannelSender csend;
  private ReftekMonitor monitor;
  private long inbytes;
  private long outbytes;
  private long totalPackets;
  private boolean dbg;
  private boolean hydra;
  private boolean nosnwCommands;
  private boolean check;
  private String argsin;
  private int pt;
  private String h;
  private boolean nocsend;
  private final byte[] buf = new byte[1034];  // Data length of 1024 bytes + 6 byte header
  private final ByteBuffer bb;

  // Reftek stuff
  private String configFile = "config/reftek.setup";
  private int pid;          // This is the PID returned by the server
  private Reftek reftek;    // This is set to the current packets Reftek

  // these fields are decoded from a MSG_REFTEK header
  private char dt1, dt2;
  private byte experiment;
  private int year;
  private short unit;
  private final byte[] time = new byte[6];
  private short byteCount;
  private short sequence;
  private final StringBuilder stime = new StringBuilder(12);
  private int iday;
  private int sec;
  private int micros;
  private int activity;
  private int ioClock;
  private int quality;
  private int tQual;

  private boolean rawMode;
  private boolean rawRead;
  private RawDisk raw;
  private FileInputStream rawin;
  private TraceBuf tb;         // trace buf for hydra sender
  private final GregorianCalendar gnow = new GregorianCalendar();
  private final int[] ymd = new int[3];
  // These are decoded from DT packets
  private final byte[] decomp = new byte[1024];  // This is used to store buffer for steim decoding 15*64 frames
  private final int[] decode = new int[4096];    // This is used for non-steim decoding
  private final StringBuilder tmpsb = new StringBuilder(100);
  private final StringBuilder runsb = new StringBuilder(100);

  public static DBMessageQueuedClient getStatusSender() {
    return statusClient;
  }

  public static SNWSender getSNWSender() {
    return snwsender;
  }

  public void close() {
    if (reftek != null) {
      reftek.terminate();
    }
    reftek = null;
    pinnos.clear();
    refteks.clear();
  }

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

  public void setPrint(PrintStream it) {
    setPrintStream(it);
  }

  /**
   * Creates a new instance of ReftekCLient - which will try to stay connected
   * to the host/port source of data. This one gets its arguments from a command
   * line.
   *
   * @param argline EdgeThread args.
   * @param tg The EdgeThread tag.
   */
  public ReftekClient(String argline, String tg) {
    super(argline, tg);
    argsin = argline;
    bb = ByteBuffer.wrap(buf);
    restart(argline);
    create(h, pt);

  }

  /**
   * Open a Reftek client for file reading.
   *
   * @param dbserver The statusClient server to use.
   * @param argline An argline. The only working arg is -refdbg.
   * @param tg A tag for the EdgeThread.
   */
  public ReftekClient(String dbserver, String argline, String tg) {
    super(argline, tg);
    tag = tg;
    bb = ByteBuffer.wrap(buf);
    String[] args = argline.split("\\s");
    for (String arg : args) {
      if (arg.equals("-refdbg")) {
        dbg = true;
      }
    }
    prta(Util.clear(tmpsb).append(Util.ascdate()).append(" ReftekClient Start: arg=").append(argline));
  }
  private String processFilename;

  public void processFile(String filename) {
    prta(Util.clear(tmpsb).append("           %%%%%% Start file ").append(filename).append(" %%%%%%%%%%%"));
    processFilename = filename;
    ehs.clear();
    try {
      in = new FileInputStream(filename);
    } catch (FileNotFoundException e) {
      Util.prt(Util.clear(tmpsb).append("Could not open file=").append(filename));
      return;
    }
    // For each file, read in the config, so clear out all refteks
    Iterator<Reftek> itr = refteks.values().iterator();
    while (itr.hasNext()) {
      Reftek ref = itr.next();
      if (ref != null) {
        ref.terminate();
      }
    }
    refteks.clear();
    rawRead = true;
    int npack = 0;
    while (true) {     // Read file till EOF
      // Read data from the file and update/create the list of records
      int l;

      try {
        for (int i = 0; i < 1030; i++) {
          buf[i] = 0;
        }
        bb.position(0);
        bb.putShort(this.RTP_MSG_REFTEK);
        bb.putInt(1024);
        l = Util.readFully(in, buf, 6, 1024);
        inbytes += l;
        if (l <= 0) {
          break;      // EOF - close up - go to outer infinite loop
        }
        processPacket();
        npack++;
      } catch (IOException e) {
        if (e.getMessage().contains("Socket closed") || e.getMessage().contains("Stream closed")) {
          if (terminate) {
            prta("Doing termination via Socket close.");
            break;
          }
          prta(Util.clear(tmpsb).append(" ** Unexplained stream closed e=").append(e.getMessage()));
          break;
        } else {
          prta(Util.clear(tmpsb).append("ReftekClient: receive through IO exception e=").append(e.getMessage()));
          e.printStackTrace(getPrintStream());
        }
        break;      // Drop out of read loop to connect loop
      }

    }     // while(true) Get data
    try {
      in.close();
    } catch (IOException expected) {
    }
    prta(Util.clear(tmpsb).append("Process file complete on ").append(filename).append(" ").append(npack).append(" packets processed. "));
  }

  public final void restart(String argline) {
    String[] args = argline.split("\\s");
    dbg = false;
    h = null;
    pt = 2543;
    nocsend = false;
    hydra = true;
    boolean nosnw = false;
    nosnwCommands = false;

    for (int i = 0; i < args.length; i++) {
      //prt(i+" arg="+args[i]);
      if (args[i].equals("-h")) {
        h = args[i + 1];
        i++;
      } else if (args[i].equals("-p")) {
        pt = Integer.parseInt(args[i + 1]);
        i++;
      } else if (args[i].equals("-dbg")) {
        dbg = true;
      } else if (args[i].equals("-noudpchan")) {
        nocsend = true;
      } else if (args[i].equals("-nohydra")) {
        hydra = false;
      } else if (args[i].equals("-nosnw")) {
        nosnw = true;
      } else if (args[i].equals("-nosnwcmd")) {
        nosnwCommands = true;
      } else if (args[i].equals("-config")) {
        configFile = args[i + 1];
        i++;
      } else if (args[i].equals("-XX")) {
        rawMode = true;
        try {
          raw = new RawDisk("RTPD.raw", "rw");
          raw.seek(0L);
        } catch (IOException e) {
          e.printStackTrace();
        }
      } else if (args[i].equals("-rawread")) {
        rawRead = true;
        try {
          rawin = new FileInputStream(args[i + 1]);
          hydra = false;
          nocsend = true;
        } catch (FileNotFoundException e) {
          e.printStackTrace();
        }
        i++;
      } else if (args[i].substring(0, 1).equals(">")) {
        break;
      } else {
        prt(Util.clear(tmpsb).append("Reftek client unknown i=").append(i).append(" switch=").append(args[i]).append(" ln=").append(argline));
      }
    }
    // If locations are not set, assume they are null

    prt(Util.clear(tmpsb).append("ReftekClient: new line parsed to host=").append(h).append(" port=").append(pt).append(" dbg=").append(dbg));

    if (!nocsend && csend == null) {
      csend = new ChannelSender("  ", "ReftekClient", "RTC-" + tag);
    }
    try {
      if (snwsender == null && !nosnw) {
        snwsender = new SNWSender(100, 300);
      }
    } catch (UnknownHostException e) {
      snwsender = null;
      prt(Util.clear(tmpsb).append("  **** Reftek Client did not open a SNWSender!"));
      SendEvent.edgeSMEEvent("SNWSenderErr", "ReftekCLient did not set up SNWSender e=" + e, this);
    }
    if (hydra && tb == null) {
      tb = new TraceBuf(new byte[TraceBuf.TRACE_LENGTH]);        // Trace buf for sending data to HydraOutputer
    }
    if (statusClient == null && !rawRead) {
      String statusServer = Util.getProperty("StatusServer");
      if (statusServer == null) {
        statusServer = "localhost";
      }
      statusClient = new DBMessageQueuedClient(statusServer, 7985, 100, 300, this);  // 100 message queue, 300 in length

    }

    // If the socket is open, reopen it with the new parameters
    if (d != null) {
      if (!d.isClosed()) {
        try {
          d.close();
        } catch (IOException expected) {
        }
      }
    }
  }

  private void create(String h, int pt) {
    port = pt;
    host = h;
    countMsgs = 0;
    heartBeat = 0;
    tag += "RTC:";  //with String and type
    monitor = new ReftekMonitor(this);
    start();
  }

  public boolean readConfigDB(GregorianCalendar begin, String hexUnit, String table) {
    try {

      DBConnectionThread dbconn2 = DBConnectionThread.getThread("config");
      if (dbconn2 == null) {
        dbconn2 = new DBConnectionThread(Util.getProperty("DBServer"), "readonly", "anss",
                false, false, "config", getPrintStream());
        addLog(dbconn2);
        int loop = 0;
        while (!DBConnectionThread.waitForConnection("config")) {
          if (loop++ % 20 == 19) {
            prta("**** could not connect to  database! loop=" + loop);
          }
        }
      }
      String untmp = hexUnit;
      if (hexUnit.length() == 5) {
        untmp = hexUnit.substring(0, 2) + "0" + hexUnit.substring(2);
      }
      try (ResultSet rs = dbconn2.executeQuery("SELECT * FROM " + table + " WHERE serial='"
              + untmp.substring(2) + "' AND enddate >='" + Util.ascdate(begin) + " " + Util.asctime(begin).substring(0, 8) + "' ORDER BY enddate")) {
        if (rs.next()) {
          String stat = rs.getString("station");
          if (stat.indexOf(":") > 0) {
            stat = stat.substring(0, stat.indexOf(":"));
          }
          Reftek old = refteks.get(hexUnit);
          boolean codeA = rs.getString("comment").contains("codea");
          if (old != null) {
            prt(Util.clear(tmpsb).append("Closing hex =").append(hexUnit));
            old.terminate();
          }
          prta(Util.clear(tmpsb).append("New reftek stat=").append(stat).append(" hex=").append(hexUnit));
          Reftek ref = new Reftek(Integer.parseInt(hexUnit.substring(2), 16), "0.0.0.0",
                  rs.getString("network"), stat, this);
          refteks.put(hexUnit, ref);
          for (int i = 0; i < 6; i++) {
            String chans = rs.getString("chans" + (i + 1)).trim();
            String stream = rs.getString("stream" + (i + 1)).trim();
            String location = rs.getString("location" + (i + 1)).trim();
            String components = rs.getString("components" + (i + 1)).trim();
            String rate = "" + rs.getDouble("rate" + (i + 1));
            String band = rs.getString("band" + (i + 1)).trim().trim();

            reftek = refteks.get(hexUnit);
            int str = Integer.parseInt(stream.trim());
            double rt = Double.parseDouble(rate.trim());
            if (str > 0 && chans.length() >= 3 && components.length() >= 3 && rt > 0.) {
              if (reftek.addStream(str - 1, chans, components, location, rt, band)) // Streams are started at zero
              {
                prta(Util.clear(tmpsb).append("  Add stream ").append(reftek.getNetwork()).
                        append(reftek.getStation()).append(Util.ascdatetime(begin)).
                        append(" str=").append(str).append(" chs=").append(chans).append(" comps=").append(components).
                        append(" rt=").append(rt).append(" band=").append(band).append(" loc=").append(location));
              }
              if (codeA) {
                if (reftek.addStream(str - 1 + 4, chans, components, location, rt, band)) {
                  prta(Util.clear(tmpsb).append("  Add streamA").append(reftek.getNetwork()).
                          append(reftek.getStation()).append(Util.ascdatetime(begin)).
                          append(" str=").append(str + 4).append(" chs=").append(chans).append(" comps=").append(components).
                          append(" rt=").append(rt).append(" band=").append(band).append(" loc=").append(location));
                }
              }
            }
          }
        } else {
          prt(Util.clear(tmpsb).append("There is no translation record for unit=").append(hexUnit).append(" for time=").append(Util.ascdate(begin)));
          return false;
        }
      }
    } catch (SQLException e) {
      prta("Error reading from database");
    } catch (InstantiationException e) {
      prta("Error reading from database instantiation exception");
    }
    return true;
  }

  public boolean readConfig() {
    String line = "";
    int loop = 0;
    try {
      try (BufferedReader cin = new BufferedReader(new FileReader(configFile))) {
        while ((line = cin.readLine()) != null) {
          if (line.substring(0, 1).equals("#")) {
            continue;      // Its a comment
          }
          if (line.indexOf("#") > 0) {
            line = line.substring(0, line.indexOf("#"));
          }
          String network = line.substring(0, 2);
          String station = line.substring(2, 7).trim();
          boolean codeA = line.charAt(7) == '-';
          if (station.indexOf(":") > 0) {
            String old = station;
            station = station.substring(0, station.indexOf(":"));
            prta(Util.clear(runsb).append("readconfig set sstation from ").append(old).
                    append(" to ").append(station).append(" codea=").append(codeA).
                    append(" line=").append(line));
          }
          String serial = line.substring(8, 12).toLowerCase();
          if (serial.equalsIgnoreCase("ffff")) {
            continue;   // Not a station!
          }
          String ipadr = line.substring(13, 28);
          // Read the 3 possible channels
          int offset = 28;
          reftek = null;
          for (int i = 0; i < 6; i++) {
            if (line.length() < offset + 28) {
              continue;
            }
            String stream = line.substring(offset, offset + 2).trim();
            String location = line.substring(offset + 3, offset + 7).trim();
            String chans = line.substring(offset + 8, offset + 14).trim();
            String components = line.substring(offset + 15, offset + 21).trim();
            String rate = line.substring(offset + 22, offset + 29).trim();
            String band = line.substring(offset + 30, offset + 34).trim();
            offset += 35;
            reftek = refteks.get("0x" + serial);
            if (reftek == null) {
              reftek = new Reftek(Integer.parseInt(serial, 16), ipadr, network, station, this);
              prta(Util.clear(runsb).append("Create Reftek in read config=").append(reftek));
              refteks.put("0x" + serial, reftek);
              prta(Util.clear(runsb).append("Create Reftek in read 0x").append(serial).
                      append("|").append(Util.toHex((short) reftek.getUnit())).append(" config=").append(reftek));
            } else {
              if (!reftek.getIP().trim().equals(ipadr.trim()) || !reftek.getNetwork().trim().equals(network.trim())
                      || !reftek.getStation().trim().equals(station.trim())) {
                prta(Util.clear(runsb).append("Override new config new=").append(ipadr).append(" ").
                        append(network).append(" ").append(station).append(" OLD : ").append(reftek.getIP()).
                        append(" ").append(reftek.getNetwork()).append(" ").append(reftek.getStation()).
                        append("IP=").append(reftek.getIP().trim().equals(ipadr.trim())).
                        append(" net=").append(reftek.getNetwork().trim().equals(network.trim())).
                        append(" Station=").append(reftek.getStation().trim().equals(station.trim())).
                        append(" 0x").append(serial));
                reftek = new Reftek(Integer.parseInt(serial, 16), ipadr, network, station, this);
                prta(Util.clear(runsb).append("Override new config new=").append(ipadr).append(" ").
                        append(network).append(" ").append(station).append(" NOW : ").append(reftek.getIP()).
                        append(" ").append(reftek.getNetwork()).append(" ").append(reftek.getStation()).
                        append("IP=").append(reftek.getIP().trim().equals(ipadr.trim())).
                        append(" net=").append(reftek.getNetwork().trim().equals(network.trim())).
                        append(" Station=").append(reftek.getStation().trim().equals(station.trim())));
                refteks.put(Util.toHex((short) reftek.getUnit()).toString(), reftek);
                prta(Util.clear(runsb).append("Override Reftek in read 0x").append(serial).append("|").
                        append(Util.toHex((short) reftek.getUnit())).append(" config=").append(reftek));

              }
            }
            int str = Integer.parseInt(stream.trim());
            double rt = Double.parseDouble(rate.trim());
            if (str > 0 && chans.length() >= 3 && components.length() >= 3 && rt > 0.) {
              if (reftek.addStream(str - 1, chans, components, location, rt, band)) // Streams are started at zero
              {
                prta(Util.clear(runsb).append("  Add stream ").append(network).append(station).
                        append(" str=").append(str).append(" chs=").append(chans).append(" comps=").append(components).
                        append(" rt=").append(rt).append(" band=").append(band).append(" loc=").append(location));
              }
              if (codeA) {
                if (reftek.addStream(str - 1 + 4, chans, components, location, rt, band)) {
                  prta(Util.clear(runsb).append("  Add streamA").append(network).append(station).
                          append(" str=").append(str + 4).append(" chs=").append(chans).
                          append(" comps=").append(components).append(" rt=").append(rt).
                          append(" band=").append(band).append(" loc=").append(location));
                }
              }
            }
          }
          if (dbg && reftek != null) {
            prt(reftek.toStringBuilder(null));
          }
        }
      }
      return true;
    } catch (FileNotFoundException e) {
      prta("ReftekClient config file not found=" + configFile);
      try {
        sleep(30000);
      } catch (InterruptedException e2) {
      }
    } catch (IOException e) {
      prta("IOError reading configfile=" + e);
      e.printStackTrace(getPrintStream());
      try {
        sleep(30000);
      } catch (InterruptedException e2) {
      }
    } catch (NumberFormatException e) {
      prta(Util.clear(runsb).append("RuntimeException trying to parse line=").append(line).append("| len=").append(line.length()));
      e.printStackTrace(getPrintStream());
    }
    return false;
    // Wait to read the file
  }

  @Override
  public void terminate() {
    terminate = true;
    if (in != null) {
      prta(Util.clear(tmpsb).append(tag).append(" Terminate started. Close input unit."));
      try {
        in.close();
      } catch (IOException expected) {
      }
      interrupt();
    } else {
      prt(tag + " Terminate started. interrupt().");
      interrupt();
    }
    if (monitor != null) {
      monitor.terminate();
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
    sb.append(tag).append(" ").append(host).append("/").append(port).append(" nbIn=").append(inbytes).append(" #hb=").append(heartBeat);
    return sb;
  }

  /**
   * This Thread keeps a socket open to the host and port, reads in any
   * information on that socket, keeps a list of unique StatusInfo, and updates
   * that list when new data for the unique key comes in.
   */
  @Override
  public void run() {
    int nillegal = 0;
    running = true;
    long isleep = 30000;
    int length = 0;
    int tmp;
    boolean done = false;
    long lastCommand = System.currentTimeMillis() - 300000;
    long lastInbytes = 0;
    long lastConfig = 0;
    while (!done) {
      done = readConfig();
      length++;
      if (length % 10 == 0) {
        SendEvent.edgeSMEEvent("ReftekNoConfig", "The config file=" + configFile + " not found or corrupt ", this);
      }
      try {
        sleep(5000);
      } catch (InterruptedException expected) {
      }
    }
    while (true) {
      try {
        if (terminate) {
          break;
        }
        /* Keep trying until a connection is made */
        int loop = 0;
        lastStatus = System.currentTimeMillis();
        if (rawRead) {
          in = rawin;
        } else {  // Need to open the IP address
          while (true) {
            if (terminate) {
              break;
            }
            try {
              // Make sure anything we have open can be let go
              if (d != null) {
                try {
                  prta(Util.clear(runsb).append(tag).append(" closing socket d=").append(d));
                  if (!d.isClosed()) {
                    d.close();
                  }
                  if (in != null) {
                    in.close();
                  }
                  if (outsock != null) {
                    outsock.close();
                  }
                } catch (IOException expected) {
                }
              }
              prta(Util.clear(runsb).append(tag).append(" Open Port=").append(host).append("/").append(port));
              d = new Socket(host, port);
              in = d.getInputStream();        // Get input and output streams
              outsock = d.getOutputStream();

              // Build first 100 StatusInfo objects and fill with empty data
              prta(Util.clear(runsb).append(tag).append(" local=").append(d.getLocalPort()).append(" Socket is opened.  Start reads."));
              //tag =tag.substring(0,tag.indexOf(":")+1)+d.getLocalPort()+" ";
              prta(Util.clear(runsb).append(tag).append(" is now changed!"));
              lastStatus = System.currentTimeMillis();
              break;
            } catch (UnknownHostException e) {
              prt(Util.clear(runsb).append(tag).append(" Host is unknown=").append(host).append("/").append(port).append(" loop=").append(loop));
              if (loop % 30 == 1) {
                SendEvent.edgeEvent("HostUnknown", "Reftek host unknown=" + host + " " + Util.getAccount(), this);

              }
              loop++;
              try {
                sleep(120000L);
              } catch (InterruptedException expected) {
              }
            } catch (IOException e) {
              loop++;
              if (e.getMessage().equalsIgnoreCase("Connection refused")) {
                if (loop % 12 == 0) {
                  prta(Util.clear(runsb).append(tag).append(" Connection refused.  wait ").
                          append(isleep / 1000).append(" secs ...."));
                }
                /*isleep = isleep * 2;
                if(isleep > 14400000) isleep=14400000;*/
                if (loop % 720 == 0) {
                  SendEvent.edgeSMEEvent("ReftekRefuse", tag + " Reftek " + host + "/" + port + " repeat refused", this);
                }
                try {
                  sleep(5000);
                } catch (InterruptedException expected) {
                }
              } else if (e.getMessage().equalsIgnoreCase("Connection timed out")
                      || e.getMessage().equalsIgnoreCase("Operation timed out")) {
                prta(Util.clear(runsb).append(tag).append(" Connection timed out.  wait ").
                        append(isleep / 1000).append(" secs ...."));
                try {
                  sleep(isleep);
                } catch (InterruptedException expected) {
                }
                isleep = isleep * 2;
                if (isleep > 1800000) {
                  isleep = 1800000;
                }
                try {
                  sleep(isleep);
                } catch (InterruptedException expected) {
                }
              } else {
                Util.IOErrorPrint(e, tag + " IO error opening socket=" + host + "/" + port);
                try {
                  sleep(120000L);
                } catch (InterruptedException expected) {
                }
              }

            }
          }   // endif on else clause for rawRead

          //try {sleep(10000L);} catch (InterruptedException expected) {}
        }   // While true on opening the socket

        // Time to exchange the handshake
        // Send the version
        if (!rawRead) {
          try {
            bb.position(0);
            bb.putShort((short) version);
            prta("Send out version ");
            //bb.putShort((short) 1);   // DEBUG: Is this why?
            bb.putInt(0);
            outsock.write(buf, 0, 6);     // Send the version
            // Read back version
            in.read(buf, 0, 6);           // Read it back, it should be 1 and then datalen zero
            bb.position(0);
            short vers = bb.getShort();
            int rtplen = bb.getInt();
            prta(Util.clear(runsb).append(" got back version=").append(vers).append(" rtplen=").append(rtplen));

            // Version 2 is just like version 1 but with the new RTP_MSG_XTND packets (documented in sections 1.3.1 and 1.3.2 on pages 5 and 7)
            // We do not support these extensions here!
            if (vers == 1 || vers == 2) {
              if (rtplen != 0) {
                bb.position(0);
                prta(Util.clear(runsb).append(tag).
                        append(" ******* Read back of version was not correct. Should be vers 1 or 2 and 0 got vers=").
                        append(bb.getShort()).append(" 0=").append(bb.getInt()));
                try {
                  sleep(10000);
                } catch (InterruptedException expected) {
                }
                continue;
              }/// Make a new socket
            } else {
              prta(Util.clear(runsb).append(tag).append(" Got an unknown version=").append(vers).
                      append(" only version 1 and 2 are supported.  Panic!"));
              try {
                sleep(10000);
              } catch (InterruptedException expected) {
              }
              continue;
            }
            if (version != vers) {
              version = vers;
              continue;
            }
            // Send the pid packet
            bb.position(0);
            bb.putShort(RTP_MSG_PID);
            bb.putInt(36);        // The pid + 32 char of name
            bb.putInt((int) this.getId());
            bb.put("ReftekClient                      ".getBytes());
            prta("Send RTP_MSG_PID message");
            outsock.write(buf, 0, 42);     // Send the 6 byte header and 36 byte payload
            readbuf();
            bb.position(0);
            short resp = bb.getShort();
            prta("Got back RTP_MSG_PID=" + resp);
            if (resp != RTP_MSG_PID) {
              prta(Util.clear(runsb).append(tag).append(" got wrong message type in response to PID!"));
              continue;
            }
            bb.getInt();    // Skip 4 bytes
            pid = bb.getInt();
            prta(Util.clear(runsb).append("Set server pid to ").append(pid).append(" version=").append(vers));

            // Now send the attributes
            bb.position(0);
            bb.putShort(RTP_MSG_ATTR);
            bb.putInt(32);
            bb.putInt(0);       // All DASes
            //int pmask = RTP_PMASK_DT | RTP_PMASK_EH | RTP_PMASK_ET | RTP_PMASK_DS | RTP_PMASK_SH | RTP_PMASK_SPEC;
            int pmaskInit = RTP_PMASK_ALL;
            bb.putInt(pmaskInit);
            bb.putInt(0xff);      // All streams
            bb.putInt(30);        // Minimal timeout
            bb.putInt(1);         // Set block to true
            bb.putInt(0);         // Use system send_buf size
            bb.putInt(0);         // Use system rcvbuf size
            if (nosnwCommands) {
              bb.putInt(0); // Client does not have command ability
            } else {
              bb.putInt(1);              // This client does have command responsibility
            }
            prta("Send attr");
            outsock.write(buf, 0, 38);      // Send the 32 bytes + 6 byte header
            readbuf();                // Get the response. It should be the same type
            bb.position(0);
            resp = bb.getShort();
            prta("ATTR resp=" + resp);
            if (resp != RTP_MSG_ATTR) {
              prta(Util.clear(runsb).append(tag).append(" got wrong message type in response to ATTR!"));
              continue;
            }
            bb.getInt();    // Skip 4 bytes
            if ((tmp = bb.getInt()) != 0) {
              prta(Util.clear(runsb).append("Change in parmeter for DAS").append(tmp));
            }
            if ((tmp = bb.getInt()) != pmaskInit) {
              prta(Util.clear(runsb).append("Change in parmeter for PMASK").append(tmp));
            }
            if ((tmp = bb.getInt()) != 0xFF) {
              prta(Util.clear(runsb).append("Change in parmeter for STREAMS").append(tmp));
            }
            if ((tmp = bb.getInt()) != 30) {
              prta(Util.clear(runsb).append("Change in parmeter for timeout").append(tmp));
            }
            if ((tmp = bb.getInt()) != 1) {
              prta(Util.clear(runsb).append("Change in parmeter for BLOCKING").append(tmp));
            }
            if ((tmp = bb.getInt()) != 0) {
              prta(Util.clear(runsb).append("Change in parmeter for sendbuf").append(tmp));
            }
            if ((tmp = bb.getInt()) != 0) {
              prta(Util.clear(runsb).append("Change in parmeter for rcvbuf").append(tmp));
            }
            if ((tmp = bb.getInt()) != (nosnwCommands ? 0 : 1)) {
              prta("Change in parmeter for CMD ALLOW" + tmp);
            }
          } catch (IOException e) {
            prta(Util.clear(runsb).append("Got IOError trying to do handshake.  Clear and restart e=").append(e));
            e.printStackTrace(getPrintStream());
            continue;     // open socket again
          }
        }       // End if we need to do setup stuff (not in raw read mode)

        if (terminate) {
          break;
        }
        isleep = 60000;
        // Read data from the socket and update/create the list of records
        long now;
        int l = 0;
        while (true) {
          if (terminate) {
            break;
          }
          try {
            for (int i = 0; i < 1030; i++) {
              buf[i] = 0;
            }
            if (rawRead) {
              bb.position(0);
              bb.putShort(this.RTP_MSG_REFTEK);
              bb.putInt(1024);
              l = Util.readFully(in, buf, 6, 1024);
            } else {
              l = readbuf();          // Get one buffer
            }
            inbytes += l;
            if (l <= 0) {
              break;      // EOF - close up - go to outer infinite loop
            }
            if (rawMode && !rawRead) {
              prta(Util.clear(runsb).append("Write ").append(l + 6).append(" bytes"));
              raw.write(buf, 0, l + 6);
              continue;
            }
            if (rawRead) {
              try {
                sleep(2);
              } catch (InterruptedException expected) {
              }  // Limit to 500 packet/sec
            }
            now = System.currentTimeMillis();
            processPacket();
            nillegal = 0;                 // Must be a legal seed name
            if ((now - lastStatus) > 1800000) {
              long kbps = (inbytes - lastInbytes) * 8 / (now - lastStatus);
              prta(Util.clear(runsb).append(tag).append(" # Rcv=").append(countMsgs).append(" hbt=").append(heartBeat).append(" ").append(kbps) + " kbps");
              countMsgs = 0;
              lastStatus = now;
              lastInbytes = inbytes;
              for (Reftek r : refteks.values()) {
                prt(Util.clear(runsb).append(r.toStringLong()).append("\n").append(r.getSOH()));
              }
            }
            if (ReftekManager.configChanged() || (now - lastConfig) > 120000) {
              readConfig();
              lastConfig = now;
            }
            if (!nosnwCommands && (now - lastCommand) > 300000 && !rawRead) {
              readConfig();
              doCommand();
              lastCommand = now;
            }
          } catch (IOException e) {
            if (e.getMessage().contains("Socket closed") || e.getMessage().contains("Stream closed")) {
              if (terminate) {
                prta("Doing termination via Socket close.");
                break;
              } else {
                if (monitor.didInterrupt()) {
                  prta(" Socket closed by Monitor - reopen");
                  break;
                }
              }
              prta(Util.clear(runsb).append(" ** Unexplained socket closed e=").append(e.getMessage()));
              break;
            } else {
              prta(Util.clear(runsb).append("ReftekClient: receive through IO exception e=").append(e.getMessage()));
              e.printStackTrace(getPrintStream());
            }
            break;      // Drop out of read loop to connect loop
          }

        }     // while(true) get data
        try {
          if (l <= 0 && !monitor.didInterrupt()) {
            prta(Util.clear(runsb).
                    append(" exited due to EOF char=").append(l).append(" terminate=").append(terminate));
          } else if (terminate) {
            prt(" terminate found.");
          } else {
            prta(Util.clear(runsb).append(tag).append(" exited while(true) for read"));
          }
          if (d != null) {
            if (!d.isClosed()) {
              d.close();
            }
          }
        } catch (IOException e) {
          prta(Util.clear(runsb).append(tag).append(" socket- reopen e=").append(e == null ? "null" : e.getMessage()));
          if (e != null) {
            e.printStackTrace(this.getPrintStream());
          }
        }
        if (rawRead) {
          break;    // We have read the file
        }
      } catch (RuntimeException e) {
        prta(Util.clear(runsb).append(tag).append("LC: RuntimeException in ").
                append(this.getClass().getName()).append(" e=").append(e.getMessage()));
        if (getPrintStream() != null) {
          e.printStackTrace(getPrintStream());
        } else {
          e.printStackTrace();
        }
        if (e.getMessage() != null) {
          if (e.getMessage().contains("OutOfMemory")) {
            SendEvent.doOutOfMemory(tag, this);
          }
        }
      }
    }       // while(true) do socket open
    Iterator itr = refteks.values().iterator();
    prt(tag + " Start terminating Reftek Objects size=" + refteks.size());
    while (itr.hasNext()) {
      ((Reftek) itr.next()).terminate();
    }
    monitor.terminate();
    prta(tag + " is terminated.");
    if (csend != null) {
      csend.close();
    }
    running = false;
    terminate = false;
  }
  private short type;       // Type from the begiinning of the packet if its an RTP_MSG and not attn
  private int len;          // Length of the packet in bytes for the payload portion
  private int pmask;

  public void processPacket() {
    bb.position(0);
    type = bb.getShort();
    len = bb.getInt();      // Payload length
    if (type == RTP_MSG_REFTEK) {    // Type without a PMASK - old RTPD
      /*Util.clear(runsb).append("*** ATTN len=").append(len).append(" type=").append(type).append(" bytes=");
      for(int i=6; i<Math.min(len,40); i++) runsb.append(Util.toHex(buf[i])).append(" ");
      prta(runsb);      */
      if (buf[6] == -123 && buf[7] == 0) {
        doAttn(type);
        return;
      }
    } else if (type == RTP_MSG_REFTEK_XTND) {      // Type with a PMASK 
      /*prta(Util.clear(runsb).append("*** Command packet found len=").append(len).append(" ").
              append(buf[6]).append(" ").append(buf[7]).append(" ").append(buf[8]).append(" ").append(buf[9]).append(" ").append(buf[10]).append(" ").append(buf[12]));
      Util.clear(runsb).append("*** ATTN2 len=").append(len).append(" type=").append(type).append(" bytes=");
      for(int i=6; i<Math.min(len,40); i++) runsb.append(Util.toHex(buf[i])).append(" ");
      prta(runsb);*/
      if (buf[10] == -123 && buf[11] == 0) {
        doAttn(type);
        return;           // Packet has been completed
      }
    }
    if (type == RTP_MSG_BREAK || type == RTP_MSG_FAULT || type == RTP_MSG_PID || type == RTP_MSG_ATTR) {
      prta(Util.clear(runsb).append("Got a type=").append(type).append(" ").append(RTP_TYPES[type]).append(" breaking the connection."));
    } else {
      switch (type) {
        case RTP_MSG_REFTEK:  // Data payload
        case RTP_MSG_REFTEK_XTND:
          if (type == RTP_MSG_REFTEK) {
            dt1 = (char) buf[6];
            dt2 = (char) buf[7];  // Something like "D","T"
            bb.position(8);
          } else {
            dt1 = (char) buf[10];
            dt2 = (char) buf[11];
            bb.position(6);
            pmask = bb.getInt();
            bb.position(12);
          }

          experiment = bb.get();
          year = bb.get();
          year = ((year & 0xf0) >> 4) * 10 + (year & 0xf);
          year += 2000;
          unit = bb.getShort();
          bb.get(time);
          byteCount = Reftek.BCDtoInt(bb.getShort());    // BCD byte count
          sequence = Reftek.BCDtoInt(bb.getShort());      // BCD packet Sequence
          Reftek.timeToString(time, stime);
          iday = Integer.parseInt(stime.substring(0, 3));
          sec = Integer.parseInt(stime.substring(3, 5)) * 3600 + Integer.parseInt(stime.substring(5, 7)) * 60 + Integer.parseInt(stime.substring(7, 9));
          micros = Integer.parseInt(stime.substring(9, 12)) * 1000;
          //if(dbg) prta(dt1+dt2+" exp="+experiment+" yr="+year+":"+iday+
          //        " unit="+Util.toHex(unit)+" time="+stime+" "+sec+" "+micros+" sq="+sequence);
          reftek = refteks.get(Util.toHex(unit).toString());
          if (reftek == null) {
            if (rawRead) {
              SeedUtil.ymd_from_doy(year, iday, ymd);
              GregorianCalendar begin = new GregorianCalendar(ymd[0], ymd[1] - 1, ymd[2]);
              begin.setTimeInMillis(begin.getTimeInMillis() / 86400000L * 86400000L + sec * 1000);
              boolean success = readConfigDB(begin, Util.toHex(unit).toString(), "anss.portablereftek");
              if (!success) {
                success = readConfigDB(begin, Util.toHex(unit).toString(), "anss.reftek");
              }
              if (!success) {
                throw new RuntimeException(" ***** NOCONFIG for unit=" + Util.toHex(unit) + " for " + Util.ascdate(begin));
              }
              reftek = refteks.get(Util.toHex(unit).toString());
            } else {
              prta(Util.clear(runsb).append(" **** discovered a new Reftek that is not configured. ").append(Util.toHex((short) unit).substring(2)));
              prta(Util.clear(runsb).append(" **** discovered unit=").append(Util.toHex(unit)).append(" ty=").append(RTP_TYPES[type]).append("/").append(len).
                      append(" dt=").append(dt1).append(dt2).append(" ").
                      append(year).append(":").append(iday).append(":").append(stime).
                      append(" exp=").append(experiment).append(" #bytes=").append(byteCount).
                      append(" seq=").append(sequence).append(" pmask=").append(Util.toHex(pmask)));
              Util.clear(runsb).append("disc bytes=");
              for (int i = 0; i < Math.min(len, 100); i++) {
                runsb.append(Util.toHex(buf[i])).append(" ");
              }
              prta(runsb);
              reftek = new Reftek(unit, "0.0.0.0", "ZZ", "X" + Util.toHex(unit).substring(2), this);
              refteks.put(Util.toHex(unit).toString(), reftek);
            }
          }
          if (reftek.getNetwork().equals("ZZ")) {
            return;
          }
          double rate = 40.;
          if (dt1 == 'D' && dt2 == 'T') {
            doDT(len);
          } else if (dt1 == 'E' && dt2 == 'H') {
            doEH();
          } else if (dt1 == 'E' && dt2 == 'T') {
            doET();
          } else if (dt1 == 'S' && dt2 == 'C') {
            doSC();
          } else if (dt1 == 'O' && dt2 == 'M') {
            doOM();
          } else if (dt1 == 'D' && dt2 == 'S') {
            if (rawRead) {
              DataStreamInfo ds = new DataStreamInfo(0.);
              ds.load(bb);
              boolean error = chkDS(reftek, ds);
              if (error) {
                throw new RuntimeException("DSCONFIGERROR: There is an error in ds versus configuration");
              }
            } else {
              doDS();

            }
          } else if (dt1 == 'S' && dt2 == 'H') {
            doSH();
          } else {
            prta(Util.clear(runsb).append(tag).append(" ").append(reftek.getStation()).append(" ** got packet of unknown"
                    + " type=").append(dt1).append(dt2));
          }
          break;
        case RTP_MSG_CMDPKT:
        case RTP_MSG_CMDPKT_XTND:
          prta(" ** unexpected command packet!" + type);
          break;
        case RTP_MSG_NOP:
          prta(" heartbeat received.");
          heartBeat++;
          break;
        case RTP_MSG_SOH:
          prta(" ** unexpected SOH packet!");
          break;
        case RTP_MSG_START:
          prta(" ** unexpected START packet!");
          break;
        case RTP_MSG_STOP:
          prta(tag + " ** unexpected STOP packet!");
          break;
        case RTP_MSG_FLUSH:
          prta(" ** unexpected FLUSH packet!");
          break;
        case RTP_MSG_BUSY:
          prta(" ** unexpected BUSY packet!");
          break;
        default:
          prta(" ** got an unknown packet type.  Maybe a version 2 XTND message=" + type);
      }
    }
    countMsgs++;
    totalPackets++;
    reftek.incrementPacketCount();
  }

  private boolean chkDS(Reftek ref, DataStreamInfo ds) {
    int stream = ds.getStream() - 1;      // The measure streams using 1, 2, 3
    boolean error = false;
    DataStreamInfo refds = ref.getDataStreamInfo(stream);
    if (ds.getRate() != ref.getRate(stream)) {
      Util.prt(Util.clear(runsb).append("*** Rates disagree :  DS=").append(ds.getRate()).append(" config=").append(ref.getRate(stream)));
      prt(Util.clear(runsb).append("*** Rates disagree :  DS=").append(ds.getRate()).append(" config=").append(ref.getRate(stream)));
      error = true;
    }
    String chans = ds.getChannelsIncluded();
    String ch = refds.getChans();
    for (int i = 0; i < Math.min(chans.length(), 9); i++) {
      if (chans.charAt(i) == 'Y' || chans.charAt(i) == ('1' + i)) {// RT-130s use "Y" but RT-72 use digit.
        if (!ch.contains("" + (i + 1))) {
          prt(Util.clear(runsb).append("*** Channel ").append(i + 1).append(" is in DS (").append(chans).append(") but is not in config=").append(ch));
          Util.prt(Util.clear(runsb).append("*** Channel ").append(i + 1).append(" is in DS (").append(chans).append(") but is not in config=").append(ch));
          error = true;
        }
      } else {
        if (ch.contains("" + (i + 1))) {
          prt(Util.clear(runsb).append("*** Channel ").append(i + 1).append(" is not in DS (").append(chans).append(") but is in configuration=").append(ch));
          Util.prt(Util.clear(runsb).append("*** Channel ").append(i + 1).append(" is not in DS (").append(chans).append(") but is in configuration=").append(ch));
          error = true;
        }
      }
    }
    String band = refds.getBand().substring(0, 1);
    String errorString = "";
    if (band.equals("B") && (ref.getRate(stream) >= 80. || ref.getRate(stream) <= 10.)) {
      errorString += "*** band code " + band + " but rate is out of range=" + ref.getRate(stream);
      error = true;
    }
    if (band.equals("E") && ref.getRate(stream) < 80.) {
      errorString += "*** band code " + band + " but rate is out of range=" + ref.getRate(stream);
      error = true;
    }
    if (band.equals("S") && (ref.getRate(stream) >= 80. || ref.getRate(stream) <= 10.)) {
      errorString += "*** band code " + band + " but rate is out of range=" + ref.getRate(stream);
      error = true;
    }
    if (band.equals("H") && ref.getRate(stream) < 80.) {
      errorString += "*** band code " + band + " but rate is out of range=" + ref.getRate(stream);
      error = true;
    }
    if (band.equals("M") && (ref.getRate(stream) >= 10. || ref.getRate(stream) <= 1.)) {
      errorString += "*** band code " + band + " but rate is out of range=" + ref.getRate(stream);
      error = true;
    }
    if (band.equals("L") && ref.getRate(stream) != 1) {
      errorString += "*** band code " + band + " but rate is out of range=" + ref.getRate(stream);
      error = true;
    }
    if (!ref.getDataStreamInfo(stream).getComps().equals("ZNE")) {
      errorString += "*** Components are not ZNE as expected";
      prt(errorString);
      error = true;
    }
    if (band.length() == 4) {
      band = refds.getBand().substring(0, 1);
      if (band.equals("B") && (ref.getRate(stream) >= 80. || ref.getRate(stream) <= 10.)) {
        errorString += "*** band code " + band + " but rate is out of range=" + ref.getRate(stream);
        error = true;
      }
      if (band.equals("E") && ref.getRate(stream) < 80.) {
        errorString += "*** band code " + band + " but rate is out of range=" + ref.getRate(stream);
        error = true;
      }
      if (band.equals("S") && (ref.getRate(stream) >= 80. || ref.getRate(stream) <= 10.)) {
        errorString += "*** band code " + band + " but rate is out of range=" + ref.getRate(stream);
        error = true;
      }
      if (band.equals("H") && ref.getRate(stream) < 80.) {
        errorString += "*** band code " + band + " but rate is out of range=" + ref.getRate(stream);
        error = true;
      }
      if (band.equals("M") && (ref.getRate(stream) >= 10. || ref.getRate(stream) <= 1.)) {
        errorString += "*** band code " + band + " but rate is out of range=" + ref.getRate(stream);
        error = true;
      }
      if (band.equals("L") && ref.getRate(stream) != 1) {
        errorString += "*** band code " + band + " but rate is out of range=" + ref.getRate(stream);
        error = true;
      }
      if (!ref.getDataStreamInfo(stream).getComps().equals("ZNE")) {
        errorString += "*** Components are not ZNE as expected";
        error = true;
      }
    }
    if (error) {
      Util.prt(Util.clear(runsb).append("*** DS error found config=").append(refds).
              append("\n*** DS config from unit =").append(ds).append(" ").append(errorString));
      prt(Util.clear(runsb).append("*** DS error found config=").append(refds).
              append("\n*** DS config from unit =").append(ds).append(" ").append(errorString));
    }
    return error;
  }
  /**
   * Send command packets for AD, AQ, DS, XC to all units to cause status
   * gathering
   *
   */
  private final String[] subcommand = {"AQ", "AD", "DK", "US", "XC"};
  int commandSeq = 1;

  private void doCommand() {
    prta("Start do command");
    bb.position(0);
    // This is the client protocol header
    if (version == 2) {
      //.putShort((short) 0x4023);
      //bb.putShort((short) commandSeq++);

      bb.putShort((short) RTP_MSG_CMDPKT_XTND);  // Should this be _XTND?
      bb.putInt(40);
      bb.putInt(RTP_PMASK_130CC);// Try RTP_PMASK_SPEC RTP_PMASK_SPEC | RTP_PMASK_130CC | RTP_PMASK_130CCCL);    // per phil email he called them RTP_PMASK_SPEC | RTP_PMASK_STD_CMD | RTP_PMASK_CMD_LINE_CMD 1, 0x1000 0x2000
    } else {
      bb.putShort((short) RTP_MSG_CMDPKT);     // This is a command packet
      bb.putInt(40);              // Length of SS command packets
    }

    // This is the command header
    //bb.putShort((short) 0x936A);     // Unit as binary
    bb.putShort((short) 0x0);     // Unit as binary
    if (version == 2) {
      bb.putShort((short) 36);
    } else {
      bb.putShort((short) 36);    // ??44 SS payload + attn/0/hexunit/Length of command
    }    // This is the ATTN packet
    bb.put((byte) -124);        // The "attn" byte (-124 is 0x84)
    //bb.put((byte) (0x85 & 0xff));
    bb.put((byte) 0);           // Reserved
    int crcpos = bb.position();
    bb.put("0000".getBytes());  // All units code
    bb.put("0026".getBytes());  // The length of packet from the command code to delimiters
    bb.put("SS".getBytes());    // An SS command
    int pos = bb.position();    // We will need this to send the other commands
    bb.put("AQ".getBytes());
    for (int i = 0; i < 14; i++) {
      bb.put((byte) 32);
    }
    bb.put("SS".getBytes());
    int pos2 = bb.position();   // We will need this later
    int crc = CRC16.crcbuf(buf, crcpos, 28, 0xffff);
    String scrc = Util.toHex(crc & 0xffff).substring(2).toUpperCase();
    while (scrc.length() < 4) {
      scrc = "0" + scrc;
    }
    bb.put(scrc.getBytes());
    bb.put((byte) 13);
    bb.put((byte) 10);
    try {
      for (String subcommand1 : subcommand) {
        bb.position(pos);
        bb.put(subcommand1.getBytes());
        crc = CRC16.crcbuf(buf, crcpos, 28, 0xffff);
        scrc = Util.toHex(crc & 0xffff).substring(2).toUpperCase();
        while (scrc.length() < 4) {
          scrc = "0" + scrc;
        }
        bb.position(pos2);          // Position for CRC
        bb.put(scrc.getBytes());
        /*prta(Util.clear(runsb).append("Send ").append(subcommand1).append(" command crc=").append(Util.toHex(crc)).
                append(" scrc=").append(scrc).append(" pos=").append(bb.position()).
                append(" pos1=").append(pos).append(" pos2=").append(pos2));*/
        outsock.write(buf, 0, version == 2 ? 50 : 46);
        try {
          sleep(500);
        } catch (InterruptedException expected) {
        }
      }
    } catch (IOException e) {
      prta(Util.clear(runsb).append("IOError writting command.  Close socket= ").append(e));
      try {
        d.close();
      } catch (IOException expected) {
      }
    }
  }

  /**
   * THis is a testing routine only. It was used to figure out why commands were
   * not working with _XTND commands
   *
   */
  private void doID() {
    prta("Start doID command");
    bb.position(0);

    bb.putShort((short) RTP_MSG_CMDPKT_XTND);  // Should this be _XTND?
    bb.putInt(24);
    bb.putInt(RTP_PMASK_130CC);  // 0x1000
    bb.putShort((short) 0x0);     // Unit as binary - 0 is all units
    bb.putShort((short) 20);
    // This is the ATTN packet
    bb.put((byte) -124);        // The "attn" byte (-124 is 0x84)
    //bb.put((byte) (0x85 & 0xff));
    bb.put((byte) 0);           // Reserved
    int crcpos = bb.position();
    bb.put("0000".getBytes());  // All units code
    bb.put("0010".getBytes());  // The length of packet from the command code to delimiters
    bb.put("IDID".getBytes());    // An SS command
    int pos = bb.position();    // We will need this to send the other commands

    // Calc CRC and put it in packet
    int crc = CRC16.crcbuf(buf, crcpos, pos - crcpos, 0xffff);
    String scrc = Util.toHex(crc & 0xffff).substring(2).toUpperCase();
    while (scrc.length() < 4) {
      scrc = "0" + scrc;
    }
    bb.put(scrc.getBytes());

    // add cr/lf at end.
    bb.put((byte) 13);
    bb.put((byte) 10);
    //for(int i=0; i<bb.position(); i++) prt(i+" = "+Util.toHex(buf[i]));
    try {
      outsock.write(buf, 0, bb.position());
    } catch (IOException e) {
      prta(Util.clear(runsb).append("IOError writting command.  Close socket= ").append(e));
      try {
        d.close();
      } catch (IOException expected) {
      }
    }

  }

  private void doAttn(short type) {
    if (type == RTP_MSG_REFTEK) {
      bb.position(8);   // In command this is just after the attention bytes
    } else {
      bb.position(12);                         // further down because of PMASK
    }
    int crcpos = bb.position();              // CRC is at the attn byte
    bb.get(decomp, 0, 4);
    unit = (short) Integer.parseInt(new String(decomp, 0, 4), 16);
    reftek = refteks.get(Util.toHex(unit).toString());

    if (reftek == null) {
      prta(Util.clear(runsb).append(" **** discovered a new Reftek that is not configured.").append(Util.toHex(unit)));
      reftek = new Reftek(unit, "0.0.0.0", "ZZ", "X" + Util.toHex(unit).substring(2), this);
      refteks.put(Util.toHex(unit).toString(), reftek);
    }
    int pos = bb.position();
    bb.get(decomp, 0, 4);     // 4 character length in hex
    int length = Integer.parseInt(new String(decomp, 0, 4));
    bb.position(pos + 4 + length - 6);
    bb.get(decomp, 0, 4);
    int crc = Integer.parseInt(new String(decomp, 0, 4), 16);
    int calc = CRC16.crcbuf(buf, crcpos, length + 2, 0xffff);   // 8 is offset unit,
    // len is 8 bytes short since it does not include unit or length,
    // and crc does not include last 6 bytes (CRC +CR/LF)
    calc = calc & 0xffff;
    //Util.prt("crc="+Util.toHex(crc)+" calc="+Util.toHex(calc));
    if (calc != crc) {
      Util.prt(Util.clear(runsb).append(" ***** bad crc found on attn packet. crc=").append(Util.toHex(crc)).append(" calc=").append(Util.toHex(calc)));
    }
    bb.position(pos);
    reftek.loadAttn(bb, this);

  }
  GregorianCalendar ehtime = new GregorianCalendar();
  ArrayList<ReftekTrigger> ehs = new ArrayList<>(10);

  public ArrayList<ReftekTrigger> getEHList() {
    return ehs;
  }

  private void doEH() {
    DataStreamInfo ds = reftek.loadEH(bb);
    //prta("Got an EH packet = "+ds+" EH="+ds.getEH());
    if (!ds.getEH().getTriggerType().trim().equalsIgnoreCase("CON")) {
      String s = ds.getEH().getTriggerTime();
      try {
        int yr = Integer.parseInt(s.substring(0, 4));
        int doy = Integer.parseInt(s.substring(4, 7));
        int sc = Integer.parseInt(s.substring(7, 9)) * 3600 + Integer.parseInt(s.substring(9, 11)) * 60 + Integer.parseInt(s.substring(11, 13));
        int ms = Integer.parseInt(s.substring(13, 16));

        SeedUtil.ymd_from_doy(yr, doy, ymd);
        ehtime.set(ymd[0], ymd[1] - 1, ymd[2]);
        ehtime.setTimeInMillis(ehtime.getTimeInMillis() / 86400000L * 86400000L + sc * 1000 + ms);
        StringBuilder name = reftek.getSeedname(ds.getEH().getStream(), 0);
        prta(Util.clear(runsb).append("Trigger ").append(name).append(" at ").append(Util.ascdate(ehtime)).
                append(" ").append(Util.asctime2(ehtime)).append(" ").append(ds).
                append(" eh=").append(ds.getEH()).append(" file=").append(processFilename));
        if (ehs != null && name != null) {
          ehs.add(new ReftekTrigger(reftek.getSeedname(ds.getEH().getStream(), 0), ehtime.getTimeInMillis(),
                  (processFilename == null ? "RealTime"
                          : (processFilename.lastIndexOf("/") > 0 ? processFilename.substring(processFilename.lastIndexOf("/") + 1) : processFilename))));
        }
      } catch (RuntimeException e) {
        e.printStackTrace(this.getPrintStream());
      }
    }
  }

  private void doET() {
    //prta(" **** Got an ET packet ");
    reftek.loadET(bb);
  }

  private void doDS() {
    //prta(" **** Got a DS packet ");
    reftek.loadDS(bb);
  }

  private void doSH() {
    bb.get(decomp, 0, 8);
    String reserved = new String(decomp, 0, 8);
    bb.get(decomp, 0, 1000);
    String soh = new String(decomp, 0, 1000).trim();
    reftek.loadSH(soh, this);
    if (dbg) {
      prta(Util.clear(runsb).append("Got SOH reserved=").append(reserved).append(" soh=").append(soh));
    }
  }

  private void doSC() {
    reftek.loadSC(bb);
    if (rawRead) {
      StationChannel sc = reftek.getStationChannel();
      prt(Util.clear(runsb).append("SC=").append(sc));
    }
  }

  private void doOM() {
    reftek.loadOM(bb);
    if (rawRead) {
      OperatingMode om = reftek.getOperatingMode();
      prt(Util.clear(runsb).append("OM=").append(om));
    }
  }
  private final StringBuilder seednameSB = new StringBuilder(12);

  private void doDT(int len) {
    short evt = Reftek.BCDtoInt(bb.getShort());
    int stream = bb.get();
    int orgstream = stream;
    stream = Reftek.BCDtoInt(stream);
    int chan = bb.get();
    chan = Reftek.BCDtoInt(chan);
    int nsamp = Reftek.BCDtoInt(bb.getShort());
    byte flags = bb.get();
    int format = bb.get();
    format &= 0xff;
    int[] data = null;
    StringBuilder seedname = reftek.getSeedname(stream, chan);
    double rate = reftek.getRate(stream);
    if (seedname == null) {
      return;
    }
    if (dbg) {
      prta(Util.clear(runsb).append("DT str=").append(stream).append("/").append(Util.toHex(orgstream)).
              append(" ch=").append(chan).append(" ").append(seedname).append(" ").append(Util.toHex(unit)).
              append(" ns=").append(nsamp).append(" rt=").append(rate).append(" evt=").append(evt).
              append(" flg=").append(Util.toHex(flags)).append(" frm=").append(Util.toHex(format)).
              append(" ").append(year).append(" ").append(stime).append(" sq=").append(sequence));
    }
    //if(seedname.indexOf("XX") == 0) return;
    if (seedname.indexOf("XX") == 10) {
      return;    // Location code XX is marked no acquistion
    }    //if(seedname.substring(0,2).equals("NE") && chan>= 3) return;  // BC sends same thing on changes 1-3 and 4-6
    switch (format) {
      case 0x16:
        // 16 bit not compressed
        for (int i = 0; i < nsamp; i++) {
          decode[i] = bb.getShort();
        } data = decode;
        break;
      case 0x32:
        // 32 bit not compressed
        for (int i = 0; i < nsamp; i++) {
          decode[i] = bb.getInt();
        } data = decode;
        break;
      case 0x33:
        // 32 bit not compressed with overscale
        for (int i = 0; i < nsamp; i++) {
          decode[i] = bb.getInt();
        } data = decode;
        break;
      case 0xC0:
        // Binary compressed data STEIMI
        try {
          System.arraycopy(buf, bb.position() + 40, decomp, 0, 960);
          data = Steim1.decode(decomp, nsamp, false);
          if (data.length != nsamp) {
            prta(Util.clear(runsb).append("Steim1 decompressed to different length! data.len=").
                    append(data.length).append(" nsamp=").append(nsamp));
          }
        } catch (SteimException e) {
          prta(Util.clear(runsb).append("Steim error e=").append(e));
          return;
        } break;
      case 0xC1:
        // Binary compressed data with overscale flag Steim1
        prta(" *** Got Compressed overscale - not handled");
        break;
      case 0xC2:
        // Binary highly compressed data Steim2
        //prta(" *** Got Steim 2 compressed - NOT YET TESTED!!!!");
        try {
          System.arraycopy(buf, bb.position() + 40, decomp, 0, 960);
          data = Steim2.decode(decomp, nsamp, false);
          if (data.length != nsamp) {
            prta(Util.clear(runsb).append("Steim2 decompressed to different length! data.len=").
                    append(data.length).append(" nsamp=").append(nsamp));
          }
        } catch (SteimException e) {
          prta(Util.clear(runsb).append("Steim 2 error e=").append(e));
          return;
        } break;
      case 0xC3:
        // Binary Highly Compressed data with overscale flag Steim2
        prta(" *** Got Steim 2 compressed with overscale - not handled");
        break;
      default:
        prta(Util.clear(runsb).append(tag).append(" ** DT packet is encoded in undefined manner=").append(Util.toHex(format)));
        break;
    }
    activity = 0;
    ioClock = 0;
    quality = 0;
    tQual = 0;
    if ((flags & 0x80) != 0) {
      activity |= 1;      // Turn on cal signals present
    }
    if ((flags & 0x40) != 0) {
      quality |= 2;     // Turn on clipped data
    }
    if ((flags & 1) != 0) {
      activity |= 4;       // Begining of event
    }
    if ((flags & 2) != 0) {
      activity |= 8;       // End of event
    }
    if (reftek.getXC() != null) {
      tQual = reftek.getTimingQuality();
      if (reftek.getXC().getLastLockSecs() < 600) {
        ioClock |= 32;// Mark clock is locked if on in last 10 minutes
      }
      if (reftek.getXC().getLastLockSecs() > 160000) {
        quality |= 128;// If not locked for two days, set time tag questionable
      }
    } else {
      tQual = reftek.getTimeQuality();     // this comes from SH packets
    }
    if (dbg) {
      int min = 2147000000;
      int max = -2147000000;
      for (int i = 0; i < nsamp; i++) {
        if (min > data[i]) {
          min = data[i];
        }
        if (max < data[i]) {
          max = data[i];
        }
      }
      prta(Util.clear(runsb).append("  ").append(seedname).append(" ").append(Util.toHex((short) reftek.getUnit()).substring(2)).
              append(" ").append(stream).append("-").append(chan).append(" ").
              append(format == 0xc0 ? "Steim1" : (format == 0xc2 ? "Steim2" : "")).append(" min=").append(min).
              append(" max=").append(max).append(" act=").append(Util.toHex(activity)).
              append(" quality=").append(Util.toHex(quality)).append(" ioC=").append(Util.toHex(ioClock)).
              append(" tQ=").append(tQual));
    }

    if (rate > 0.05) {
      Util.clear(seednameSB).append(seedname);    // HACK : not ready to make seednames StringBuilders everywhere
      Util.rightPad(seednameSB, 12);
      RawToMiniSeed.addTimeseries(data, nsamp, seednameSB, year, iday, sec, micros,
              rate, activity, ioClock, quality, tQual, this);
      try {
        if (csend != null) {
          csend.send(SeedUtil.toJulian(year, iday), sec * 1000 + micros / 1000, // Milliseconds
                  seednameSB, nsamp, rate, byteCount);  // key,nsamp,rate and nbytes
        }
      } catch (IOException e) {
        prta(tag + "IOError writing to ChannelSender");
      }
      if (hydra) {
        Integer pinno = pinnos.get(seedname.toString());
        if (pinno == null) {
          Channel chn = EdgeChannelServer.getChannel(seedname);
          if (chn != null) {
            pinno = chn.getID();
          } else {
            pinno = 0;
          }
          pinnos.put(seedname.toString(), pinno);
        }
        // If the data rate is less than 10 samples per second, aggregate before sending
        SeedUtil.ymd_from_doy(year, iday, ymd);
        gnow.set(ymd[0], ymd[1] - 1, ymd[2]);
        // Now this calculation of time is right even if husec is < 0
        gnow.setTimeInMillis(gnow.getTimeInMillis() / 86400000L * 86400000L + sec * 1000 + micros / 1000);         // inst, module, etc set by the HydraOutputer
        //tb.setData(seedname, gnow.getTimeInMillis(), nsamp, rate, data, 0, 0,0,0,pinno.intValue());
        int offset = 0;
        long start = gnow.getTimeInMillis();
        while (offset < nsamp) {
          int ns = nsamp - offset;
          if (nsamp - offset > TraceBuf.TRACE_MAX_NSAMPS) {
            ns = TraceBuf.TRACE_MAX_NSAMPS;
          }
          tb.setData(seedname, start, ns, rate, data, offset,
                  TraceBuf.INST_USNSN, 0, 0, pinno);
          offset += ns;
          start += (int) (ns / rate * 1000. + 0.5);
          Hydra.sendNoChannelInfrastructure(tb);
        }
        //Hydra.sendNoChannelInfrastructure(tb);
        //prta(seedname+" "+Util.asctime2(gnow)+" rt="+nsamp+tb);
      }
    }

  }

  /**
   * Read a RTP packet - a short type, a long payload length, and the payload
   * (if present).
   *
   *
   * @return Length of the payload (excludes the 6 byte header), -1 if EOF.
   * @throws IOException If one is thrown by readFully.
   */
  public int readbuf() throws IOException {

    int length = Util.readFully(in, buf, 0, 6);       // Get instructions + the 4 byte length
    if (length == 0) {
      return -1;
    }
    bb.position(0);
    type = bb.getShort();
    length = bb.getInt();    // Get the length of payload
    if (type == RTP_MSG_REFTEK_XTND || type == RTP_MSG_CMDPKT_XTND) {
      length += 4;         // its 4 bigger for the PMASK which is assumed for this type
      if (type == RTP_MSG_CMDPKT_XTND) {
        prta("MSG: ** CMD XTND type " + type + " " + RTP_TYPES[Math.min(type, 15)] + " len=" + length);
      }
    } // PMASK in each of these types.
    else if (type != RTP_MSG_REFTEK) {
      prta("MSG: *** type=" + type + " " + RTP_TYPES[Math.min(type, 15)] + " len=" + length);
    }
    if (length > 0) {
      int l = Util.readFully(in, buf, 6, length);
      if (l == 0) {
        return -1;
      }
    }
    return length;
  }
  /**
   * Return the monitor string for Icinga.
   *
   * @return A String representing the monitor key value pairs for this
   * EdgeThread.
   */
  long lastMonCountMsgs;
  long lastMonBytesIn;

  @Override
  public StringBuilder getMonitorString() {
    Util.clear(monitorsb);
    long np = totalPackets - lastMonCountMsgs;
    long nb = inbytes - lastMonBytesIn;
    lastMonCountMsgs = totalPackets;
    lastMonBytesIn = inbytes;
    return monitorsb.append(tag).append("-NPacket=").append(np).append("\n").append(tag).
            append("-BytesIn=").append(nb).append("\n");
  }

  @Override
  public StringBuilder getStatusString() {
    Util.clear(statussb);
    statussb.append("In=").append(inbytes).append(" out=").append(outbytes).append(" #pck=").append(totalPackets);
    Iterator itr = refteks.values().iterator();
    while (itr.hasNext()) {
      statussb.append(((Reftek) itr.next()).getStatusString()).append("\n");
    }
    return statussb;
  }

  public StringBuilder getUnknown() {
    StringBuilder s = new StringBuilder(20000);
    Iterator itr = refteks.values().iterator();
    while (itr.hasNext()) {
      Reftek ref = (Reftek)  itr.next();
      s.append(ref.getUnknown());
      ref.clearUnknown();
      
    }
    return s;
  }

  @Override
  public StringBuilder getConsoleOutput() {
    return consolesb;
  } //  We use prt directly

  /**
   * Monitor the ReftekCLient and stop it if it does not receive heartBeats or
   * data.
   */
  final class ReftekMonitor extends Thread {

    boolean terminate;
    int lastHeartbeat;
    long lastInbytes;
    int msWait;
    boolean interruptDone;
    ReftekClient thr;

    public boolean didInterrupt() {
      return interruptDone;
    }

    public void terminate() {
      terminate = true;
      interrupt();
    }

    public ReftekMonitor(ReftekClient t) {
      thr = t;
      msWait = 360000;
      gov.usgs.anss.util.Util.prta("new ThreadMonitor " + getName() + " " + getClass().getSimpleName() + " t=" + t);
      start();
    }

    @Override
    public void run() {
      //try{sleep(msWait);} catch(InterruptedException expected) {}
      while (!terminate) {
        long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < msWait) {
          try {
            sleep(msWait - (System.currentTimeMillis() - start));
          } catch (InterruptedException expected) {
          }
          if (terminate) {
            break;
          }
        }
        if (terminate) {
          break;
        }
        if (lastHeartbeat == heartBeat && inbytes - lastInbytes < 2000) {
          thr.interrupt();      // Interrupt in case it is in a wait
          if (d != null) {
            try {
              if (!d.isClosed()) {
                d.close();  // Force I/O abort by closing the socket
              }
            } catch (IOException e) {
              prta(tag + " RTM: close socket IOException=" + e.getMessage());
            }
          }
          interruptDone = true;     // So interrupter can know it was us!
          prta(tag + " RTM: monitor has gone off HB=" + heartBeat + " lHB=" + lastHeartbeat + " in =" + inbytes + " lin=" + lastInbytes);
          try {
            sleep(msWait);
          } catch (InterruptedException expected) {
          }
          if (terminate) {
            break;
          }
          interruptDone = false;
        }
        lastInbytes = inbytes;
        lastHeartbeat = heartBeat;
      }
      prta(tag + " RTM: has been terminated");
    }

  }

  /**
   * This is the test code for ReftekClient.
   *
   * @param args The command line arguments.
   */
  public static void main(String[] args) {
    IndexFile.init();
    EdgeProperties.init();
    ReftekClient[] Reftek = new ReftekClient[10];
    IndexFile.setDebug(true);
    String argline = "";
    boolean rawread = false;
    for (String arg : args) {
      if (arg.equals("-rawread")) {
        rawread = true;
      }
      argline += " " + arg;
    }
    argline = argline.trim();
    if (rawread) {
      ReftekClient ref = new ReftekClient(argline, "TEST");
      for (;;) {
        if (ref.isAlive()) {
          Util.sleep(1000);
        } else {
          break;
        }
      }
    } else {
      ReftekManager mgr = new ReftekManager("-h " + Util.getLocalHostIP() + " -noudpchan -dbg -rawread -nosnw", "RTM");

      ReftekClient ref = null;
      while (ref == null) {
        ref = mgr.getReftekClient();
        Util.sleep(1000);
      }
      for (int i = 0; i < 1000000; i++) {
        if (i % 60 == 59 || !ref.isAlive() || !ref.isRunning()) {
          Util.prta(mgr.getStatusString() + "\n" + ref.getStatusString() + "\nUnknown:" + ref.getUnknown());
        }
        Util.sleep(1000);
        if (!ref.isAlive() || !ref.isRunning()) {
          break;
        }
      }

    }
    System.exit(0);
  }

}
