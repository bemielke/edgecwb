/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.cd11;

import gov.usgs.anss.edge.OutOfOrderRTMS;
import gov.usgs.anss.edge.OORChan;
import gov.usgs.anss.edge.RawToMiniSeed;
import gov.usgs.anss.edge.MasterBlock;
import gov.usgs.anss.edge.IndexFile;
import gov.usgs.anss.edge.ChannelSender;
import gov.usgs.alarm.SendEvent;
import gov.usgs.anss.edge.*;
import gov.usgs.anss.edgemom.EdgeChannelServer;
import gov.usgs.anss.edgemom.EdgeMom;
import gov.usgs.anss.edgethread.EdgeThread;
import gov.usgs.anss.util.SeedUtil;
import gov.usgs.anss.util.Util;
import gov.usgs.anss.net.TimeoutSocketThread;
import gov.usgs.edge.config.Channel;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.Calendar;
import java.util.GregorianCalendar;

/**
 * This class represents a single SocketServer waiting for connections from a CD1.1 data source.
 * When it gets a connection, it starts reading data and sending acks as needed. It is the
 * responsibility of the CD11ConnectionServer to make sure that the same source (array, station,
 * etc) connects each time because the gap list maintained here is still used on a reconnection.
 * This object is exclusively used by the CD11ConnectionServer and was designed with tight
 * integration to that class.
 * <p>
 * Once started, this connection server never exits. If a new connection request is made, the
 * CD11ConnectionServer will do a "reopen" of a previously opened object and the resulting data
 * connection will replace any currently in process.
 * <p>
 * It was implemented based on V0.3 of the manual dated 18 Dec 2002.
 * <p>
 * <
 * PRE>
 * Command lines to this thread look like :
 *
 * -b         ip.adr Overrides the default bind address from EdgeMom.setup line for CD11ConnectionServer
 * -p         port  The port to listen for data connection
 * -remoteHost host The IP address of the expected connections
 * -recsize   nnnn  The size of the records to put in the ring file in bytes (should be a multiple of 512)
 * -oorint    ms    The number of millis between calls to the process()for OORCHAN (30000 is good value)
 * -oordbg    name  An OORChannel to do detailed output in the OutOfOrderRTMS
 * -dbg             Turn on more debug output
 * -fn        NN    Force input data network to NN
 * -fs        SSSSS Force input data station name to SSSSS
 * -fl              Force input data location code to change if -loc is specified to that code, else to sp-sp
 * -loc       LL    Set the location code to this value
 * -noudpchan       Do not send output to the UdpChannel server for channel display
 * -nohydra         If present, data received on this link is not forwarded to hydra
 * -a        account This link is to be handled by the CD11ConnectionServer on this account only.
 * </PRE>
 *
 * @author davidketchum
 */
public class CD11LinkServer extends EdgeThread {

  private static long totalTotalBytes;
  private long totalBytes;   // Need to measure for all connections

  private ShutdownCD11LinkServer shutdown;
  private boolean dbg;

  private InetAddress ipaddr;       // The address of this computer (where listen is running)
  private InetAddress nataddr;      // If the remote is being NATted, then this is the address they have and sould be in the response
  private int serverPort;           // The port to listen on
  private String expectedHost;       // IP address from which this connection is expected. This may be the public NAT address
  private ServerSocket ss;           // We listen on this for the port
  private CD11LinkSocket currentSocket;// The socket reader currently running for this connection port
  private CD11Frame outFrame;
  private final boolean active;
  // Data file parameters
/*  String filename;
  String path;
  RawDisk ring;
  int nextRecOut;
  int maxRec;
  byte [] ringbuf = new byte[512];
  ByteBuffer bb;
  long lastHeaderWrite;               // Time of last header write
   */
  private int recSize;
  private long lastDataRead;
  private long lastReset;
  private boolean hydra;                      // if true, send data to hydra

  // sequence related variables
  private int lastSeries = -1;       // Series + sequence in the unique sequence number
  //private long  lastSeq = -1;         // Last sequence number processed
  private long firstSeq = -1;        // The first successfully processed sequence by this instance
  private int npacket;
  private int biggestPacket;
  private EdgeThread par; // The parent of this thread
  // ConnectionRequest contents;
  private int remotePort;
  private byte[] remoteIP = new byte[4];   // From the connection frame the actual IP address of the requestor (may not match expected host if NATed)
  private String stationType;
  private String serviceType;
  private String station;                 // This is the "station" or group ID, it is set up on constructionand tested on connection
  private int major;
  private int minor;
  private StringBuilder frameSet = new StringBuilder(20);        // This is the creator:destination from opening frame
  private StringBuilder creator = new StringBuilder(8);
  private StringBuilder destination = new StringBuilder(8);
  private int authID;
  // oterh variables.
  private GapList gaps;
  private String account;
  private int accountNumber;
  private long usecNotZero;

  private boolean dbgseq = true;

  // Processing to MiniSeed related variables
  private int oorint;     // The OOR interval in seconds
  private OutOfOrderRTMS oorProcessor;
  private int nprocess;
  private String network;         // Network to assign data received from this source
  private String overrideStation; // If not null, override the station name to be this for stations from this source
  private boolean overrideLocation;
  private String locationCode;
  private ChannelSender csend;
  private String oordbg;
  private GregorianCalendar nominal = new GregorianCalendar();
  private int state;
  private GregorianCalendar g2 = new GregorianCalendar();
  private StringBuilder sb = new StringBuilder(300);
  private byte[] timeScratch = new byte[20];
  private int[] samples = new int[400];
  private byte[] gapbuf = new byte[400];
  private StringBuilder runsb = new StringBuilder(100);

  @Override
  public String toString() {
    return tag + " IP=" + ipaddr + "/" + serverPort + " running=" + running + " currSock=" + currentSocket + " bigPck=" + biggestPacket;
  }

  public static long getTotalBytes() {
    return totalTotalBytes;
  }

  public int inState() {
    return state;
  }

  public void clearConnection() {

    prta(Util.clear(runsb).append(currentSocket).append(" - execute ClearConnection()"));
    if (currentSocket != null) {
      currentSocket.terminate();
    }
    currentSocket = null;
    try {
      sleep(10000);
    } catch (InterruptedException e) {
    }
    prta("clear connection gaps null done");
    gaps = null;
  }

  public void clearAck() {
    if (currentSocket != null) {
      currentSocket.clearAck();
    }
  }

  public String getAccount() {
    return account;
  }

  public boolean isListening() {
    return outFrame != null;
  } // If this is not the right account, no outFrame

  /**
   * Set debug state.
   *
   * @param t The new debug state.
   */
  public void setDebug(boolean t) {
    dbg = t;
  }

  public boolean isActive() {
    return active;
  }

  /**
   * Terminate thread (causes an interrupt to be sure). You may not need the interrupt!
   */
  @Override
  public void terminate() {
    terminate = true;
    if (currentSocket != null) {
      currentSocket.terminate();
    }
    //try{sleep(3000);} catch(InterruptedException e) {}  
    if (ss != null) {
      if (!ss.isClosed()) {
        try {
          ss.close();
        } catch (IOException e) {
        }
      }
    }
    interrupt();
  }

  /**
   * Return the status string for this thread.
   *
   * @return A string representing status for this thread.
   */
  @Override
  public StringBuilder getStatusString() {
    Util.clear(statussb);
    return statussb.append(tag).
            append(" st=").append(state).append(" #pck=").append(npacket).append(" Mxln=").
            append(biggestPacket).append(" lastSeq=").append(gaps == null ? "-1" : gaps.getHighestSeq()).
            append(" ngaps=").append(gaps != null ? gaps.getGapCount() : "null").append(" ").
            append(currentSocket == null ? "null" : currentSocket.toStringBuilder());
  }
  /**
   * Return the monitor string for ICINGA.
   *
   * @return A String representing the monitor key value pairs for this EdgeThread.
   */
  private long lastMonBytes;
  private long lastMonPacket;

  @Override
  public StringBuilder getMonitorString() {
    if (monitorsb.length() > 0) {
      monitorsb.delete(0, monitorsb.length());
    }
    long nb = totalBytes - lastMonBytes;
    long nf = npacket - lastMonPacket;
    lastMonBytes = totalBytes;
    lastMonPacket = npacket;
    return monitorsb.append(station).append("-BytesIn=").append(nb).append("\n");
  }

  /**
   * Return console output - this is fully integrated so it never returns anything.
   *
   * @return "" since this cannot get output outside of the prt() system.
   */
  @Override
  public StringBuilder getConsoleOutput() {
    return consolesb;
  }

  /**
   * Return the address of this computer bound to this socket.
   *
   * @return The bound local address.
   */
  public InetAddress getBindInetAddress() {
    return ipaddr;
  }

  /**
   * Get port bound.
   *
   * @return The port bound at this end (the local end).
   */
  public int getBindPort() {
    return serverPort;
  }

  /**
   * Return the station name per the connection packet.
   *
   * @return The station string.
   */
  public String getStation() {
    return station;
  }

  /**
   * Creates a new CD11LinkServer (the one that gets the data connection under CD1.1). These are
   * always created by CD11ConnectionServer, which is listening for the connection. This constructor
   * responds to the connection request with a connection response packet just after starting up the
   * server for that connection.
   * <br>
   * <PRE>
   * The command line is parsed for :
   * -p     port   The Server port that this link server will listen for connections on
   * -b     ip.adr The IP address on the local computer to bind
   * -nat   ip.adr The IP address of the local computer from the outside through the NAT (usually the NATing device addr)
   * -dbg          Turn on verbose output
   * -remoteHost ip.adr  The remote host IP address. Connections from other hosts are rejected.  This may be
   *                     the IP address of a NATting gate way.  The ConnectionRequest contains the address of the
   *                     computer on the other side of the NAT.
   * -recsize            The maximum size of the data frame payloads and the rec size in the ring file
   * </PRE>
   *
   * @param stat The station name or array name for tag.
   * @param creat The creator string for this FrameSet.
   * @param dest The destination string for this FrameSet.
   * @param line A command line to be parsed.
   * @throws IOException if bind address cannot be converted to an InetAddress.
   */
  public CD11LinkServer(String stat, StringBuilder creat, StringBuilder dest, String line) throws IOException {
    super(line.trim() + " >>" + stat.trim().toLowerCase() + ".cd11", stat);
//initThread(argline,tg);
    //par = parent;
    par = (CD11LinkServer) this;
    station = stat;
    dbg = false;
    creator = creat;
    destination = dest;
    line = line.replaceAll("  ", " ");   // Eliminate any extra white space
    line = line.replaceAll("  ", " ");
    line = line.replaceAll("  ", " ");
    String[] parts = line.split("\\s");
    expectedHost = null;
    serverPort = 0;
    /*    recSize=10240;
    maxRec=2000;
    path="";
 * */
    recSize = 10240;
    // Processor variables
    network = "IM";
    boolean nocsend = false;
    locationCode = "  ";
    oordbg = "";
    hydra = true;
    account = "vdl";
    for (int i = 0; i < parts.length; i++) {
      if (parts[i].equals("-p")) {
        serverPort = Integer.parseInt(parts[i + 1]);
        i++;
      } else if (parts[i].equals("-b")) {
        ipaddr = InetAddress.getByName(parts[i + 1]);
        i++;
      } else if (parts[i].equals("-nat")) {
        nataddr = InetAddress.getByName(parts[i + 1]);
        i++;
      } else if (parts[i].equals("-dbg")) {
        dbg = true;
      } else if (parts[i].equals("-remoteHost")) {
        expectedHost = parts[i + 1];
        i++;
      } else if (parts[i].equals("-recsize")) {
        recSize = Integer.parseInt(parts[i + 1]);
        i++;
      } //else if(parts[i].equals("-maxrec")) {maxRec = Integer.parseInt(parts[i+1]); i++;}
      //else if(parts[i].equals("-path")) {path = parts[i+1].trim(); if(path.charAt(path.length()-1) != '/') path+="/"; i++;}
      else if (parts[i].equals("-oorint")) {
        oorint = Integer.parseInt(parts[i + 1]);
        i++;
      } else if (parts[i].equals("-oordbg")) {
        oordbg = parts[i + 1].replaceAll("_", " ");
        i++;
      } else if (parts[i].equals("-dbg")) {
        dbg = true;
      } else if (parts[i].equals("-fn")) {
        network = (parts[i + 1] + "  ").substring(0, 2);
        i++;
      } else if (parts[i].equals("-fs")) {
        overrideStation = (parts[i + 1] + "      ").substring(0, 5);
        i++;
      } else if (parts[i].equals("-fl")) {
        overrideLocation = true;
      } else if (parts[i].equals("-loc")) {
        locationCode = (parts[i + 1] + "  ").substring(0, 2);
        i++;
      } else if (parts[i].equals("-noudpchan")) {
        nocsend = true;
      } else if (parts[i].equals("-nohydra")) {
        hydra = false;
      } else if (parts[i].equals("-a")) {
        account = parts[i + 1];
        i++;
        accountNumber = Util.getTrailingNumber(account);
      } else if (parts[i].charAt(0) == '>') {
        break;
      } else {
        prta("CD11Conn: rereadConfig() unknown option=" + parts[i] + " line=" + line);
      }
    }
    tag = station + ":C11LS[" + getId() + ":";
    prta(Util.clear(runsb).append(tag).append(" created ").append(stat).append(" local bind address=").append(ipaddr).
            append(" bound port=").append(serverPort).append(" remote Nat addr=").append(nataddr).
            append(" expHost=").append(expectedHost).
            append(" acct=").append(account).append(" ").append(Util.getAccount()));
    shutdown = new ShutdownCD11LinkServer();
    Runtime.getRuntime().addShutdownHook(shutdown);
    if (!Util.getAccount().trim().equalsIgnoreCase(account.trim()) && EdgeMom.getInstanceNumber() != accountNumber) {
      prta(Util.clear(runsb).append("Not this account ignore! ").append(account).append(" ").append(Util.getAccount()).
              append(" mominstance#=").append(EdgeMom.getInstanceNumber()).append(" acct#=").append(accountNumber));
      active = false;
      start();
      return;
    }
    outFrame = new CD11Frame(recSize, 100, creator, destination, 0, -1, 0, this);
    try {
      ss = new ServerSocket(serverPort, 10, ipaddr);  // 10 backlogged connections
    } catch (IOException e) {
      Util.IOErrorPrint(e, tag + " Binding server port=" + serverPort + " at " + ipaddr, getPrintStream());
    }

    if (!nocsend) {
      csend = new ChannelSender("  ", "CD11", "CD11-" + tag);
    }
    oorProcessor = new OutOfOrderRTMS(oorint, tag, par);
    active = true;
    start();
  }

  /*  private void writeHeader() throws IOException {
    bb.position(0);
    bb.putInt(nextRecOut);
    bb.putInt(maxRec);
    bb.putInt(recSize);
    ring.writeBlock(0, ringbuf, 0, 512);
    lastHeaderWrite = System.currentTimeMillis();
  }
 * */
  /**
   * Parse the connection request or information. Cause a COnnectionResponse based on the connection
   * request on port s.
   *
   * @param crFrame The Connection Request frame from CD11ConnectionServer.
   * @param s The connection to the CD11ConnectionServer.
   * @throws java.io.IOException if an IO error occurs.
   */

  public void reopen(CD11Frame crFrame, Socket s) throws IOException {
    // We need to respond on s with the ConnectionResponse and open
    if (shutdown == null) {
      return;    // We are not the live one, must be on another account
    }
    ByteBuffer bb2 = ByteBuffer.wrap(crFrame.getBody());
    bb2.position(0);             // Need to decode the ConnectionRequest
    major = bb2.getShort();
    minor = bb2.getShort();
    byte[] b = new byte[8];
    bb2.get(b, 0, 8);
    for (int i = 0; i < 8; i++) {
      if (b[i] == 0) {
        b[i] = 32;
      }
    }
    String stat = new String(b).trim();
    if (!stat.equalsIgnoreCase(station.trim())) {
      byte[] statb = stat.getBytes();
      byte[] stationb = station.getBytes();
      prta(Util.clear(runsb).append(tag).append(" Reopen occurred with a mismatch of the station name! got=").append(stat).
              append(" should be ").append(station).append(" len=").append(statb.length).append(" ").append(stationb.length));
      for (int i = 0; i < Math.min(statb.length, stationb.length); i++) {
        prta(i + " " + statb[i] + " " + stationb[i]);
      }
    }
    tag = station + ":C11LS[" + getId() + ":";
    bb2.get(b, 0, 4);         // Station type (IMS, IDC, NDC)
    for (int i = 0; i < 4; i++) {
      if (b[i] == 0) {
        b[i] = 32;
      }
    }
    stationType = new String(b, 0, 4).trim();
    bb2.get(b, 0, 4);
    for (int i = 0; i < 4; i++) {
      if (b[i] == 0) {
        b[i] = 32;
      }
    }
    serviceType = new String(b, 0, 4).trim();
    bb2.get(remoteIP);                    // This is the IP of the actual server which may be NATed
    remotePort = bb2.getShort();
    remotePort = remotePort & 0xffff;
    if (outFrame == null) {
      prta(Util.clear(runsb).append(" ***** NULL outframe=").append(outFrame).append(" or crFrame=").append(crFrame));
    }
    outFrame.setCreatorDestination(crFrame.getCreatorSB(), crFrame.getDestinationSB(), crFrame.getAuthID());
    if (nataddr == null) {
      outFrame.loadConnectionResponse(station, ipaddr, serverPort);
    } else {
      outFrame.loadConnectionResponse(station, nataddr, serverPort); // use the NAT device address used to get to us
    }
    creator = crFrame.getCreatorSB();
    destination = crFrame.getDestinationSB();
    authID = crFrame.getAuthID();
    // Set the frame set name based on creator and destination
    Util.clear(frameSet);
    for (int i = 0; i < crFrame.getCreatorSB().length(); i++) {
      if (crFrame.getCreatorSB().charAt(i) == ' ' || crFrame.getCreatorSB().charAt(i) == 0) {
        break;
      } else {
        frameSet.append(crFrame.getCreatorSB().charAt(i));
      }
    }
    frameSet.append(":");
    for (int i = 0; i < crFrame.getDestinationSB().length(); i++) {
      if (crFrame.getDestinationSB().charAt(i) == ' ' || crFrame.getDestinationSB().charAt(i) == 0) {
        break;
      } else {
        frameSet.append(crFrame.getDestinationSB().charAt(i));
      }
    }
    //frameSet.append(crFrame.getCreatorSB()).append(":").append(crFrame.getDestinationSB());

    byte[] tmp = new byte[200];
    ByteBuffer tmpbb = ByteBuffer.wrap(tmp);
    int len = outFrame.getOutputBytes(tmpbb);
    prta(Util.clear(runsb).append(tag).append(" Connection Request rcved station=").append(stat).
            append("/").append(stationType).append(" srv=").append(serviceType).
            append(" remoteIP=").append(((int) remoteIP[0]) & 0xff).append(".").
            append(((int) remoteIP[1]) & 0xff).append(".").append(((int) remoteIP[2]) & 0xff).
            append(".").append(((int) remoteIP[3]) & 0xff).append("/").append(remotePort).
            append(" expIP=").append(expectedHost).append(" fromIP=").append(s.getInetAddress().toString()).
            append(" respLen=").append(len).append(" frmSet=").append(frameSet).append("| authid=").append(authID));
    s.getOutputStream().write(tmpbb.array(), 0, len);
    prta(Util.clear(runsb).append("connection response sent s=").append(s).append(" len=").append(len).
            append(" for ").append(station).append(" ").
            append(nataddr == null ? "LOC :" + ipaddr : "NAT:" + nataddr).append("/").append(serverPort));
    if (currentSocket != null) {
      currentSocket.terminate();   // any current socket needs to be replaced
    }
  }

  /**
   * This Thread keeps a socket open to the host and port, reads in any information on that socket,
   * keeps a list of unique StatusInfo, and updates that list when new data for the unique key comes
   * in.
   */
  @Override
  public void run() {
    running = true;
    int loop = 0;
    while (!terminate) {
      state = 20;
      if (outFrame == null) {
        state = 99;
        try {
          sleep(10000);
        } catch (InterruptedException e) {
        }
        if (loop++ % 360 == 0) {
          prta(Util.clear(runsb).append(tag).append("Sleep - not a listen CD11LinkServer"));
        }
        continue;
      }
      prta(Util.clear(runsb).append(tag).append(" ").append(serverPort).append("  call accept()"));
      try {
        state = 21;
        Socket s = accept();          // Use accept method for performance analyzer
        state = 22;
        if (terminate) {
          break;          // If we are terminating, kill this socket. Do not start a new one
        }
        if (s == null) {
          break;          // This socket must be shutting down
        }
        prta(Util.clear(runsb).append(tag).append(" new data connection received from ").append(s).append(" ").
                append(currentSocket == null ? "1st connection" : "replacing existing connection=" + currentSocket));
        if (expectedHost != null) {
          if (!s.getInetAddress().toString().substring(1).equals(expectedHost)) {
            prta(Util.clear(runsb).append(tag).append(" New connection is not from expected host - reject!  got =").
                    append(s.getInetAddress().toString().substring(1)).append(" expected=").append(expectedHost));
            SendEvent.debugSMEEvent("CD11WrongIP", "CD1.1 rcv data conn from wrong host="
                    + s.getInetAddress().toString().substring(1) + " not " + expectedHost, this);
            s.close();
            continue;       // Connection rejected
          } else {
            prta(Util.clear(runsb).append(tag).append(" Connection is from expected host=").append(expectedHost));
          }
        }
        state = 23;
        if (currentSocket != null) {
          currentSocket.terminate();
        }
        currentSocket = new CD11LinkSocket(s, station, creator, destination, authID, this);

        tag = station + "[" + s.getInetAddress().getHostAddress() + "/" + s.getPort() + "/" + getId() + "] ";
      } catch (IOException e) {
        if (e.toString().contains("Socket closed")) {
          prta(Util.clear(runsb).append(tag).append(" Socket has closed. terminate=").append(terminate));
        } else {
          Util.IOErrorPrint(e, tag + "Accepting connections to  port =" + serverPort);
        }
        if (terminate) {
          break;
        }
      } // The main loop has exited so the thread needs to exit
      catch (RuntimeException e) {
        prta(tag + "RuntimeException in " + this.getClass().getName() + " e=" + e.getMessage());
        if (par.getPrintStream() != null) {
          e.printStackTrace(par.getPrintStream());
        } else {
          e.printStackTrace(par.getPrintStream());
        }
        if (e.getMessage() != null) {
          if (e.getMessage().contains("OutOfMemory")) {
            SendEvent.edgeEvent("OutOfMemory", "Out of Memory in " + this.getClass().getName() + " on " + IndexFile.getNode(),
                    this);
          }
        }
      }
    }
    par.prta(tag + " ** CD11LinkServer terminated");
    if (oorProcessor != null) {
      oorProcessor.terminate();   // Note: this will not return until all channels are purged or 30 seconds have passed
    }
    par.prta(tag + " has exited");
    if (currentSocket != null) {
      currentSocket.terminate();
    }
    try {
      Runtime.getRuntime().removeShutdownHook(shutdown);
    } catch (Exception e) {
    }
    running = false;            // Let all know we are not running
    terminate = false;          // Sign that a terminate is no longer in progress
  }

  /**
   * This is to isolate accept() calls for the performance analyzer.
   *
   * @return The socket from accept.
   */
  private Socket accept() throws IOException {
    if (ss == null) {
      return null;
    }
    if (ss.isClosed()) {
      return null;
    }
    return ss.accept();
  }

  /**
   * This internal class gets registered with the Runtime shutdown system and is invoked when the
   * system is shutting down. This must cause the thread to exit.
   */
  class ShutdownCD11LinkServer extends Thread {

    /**
     * Default constructor does nothing. The shutdown hook starts the run() thread.
     */
    public ShutdownCD11LinkServer() {
    }

    // This is called by the RunTime.shutdown at any termination 
    @Override
    public void run() {
      prta(tag + "CD11LinkServer Shutdown() started...");
      terminate();          // Send terminate to main thread and cause interrupt
      int loop = 0;
      while (running) {
        // Do stuff to aid the shutdown
        try {
          sleep(1000);
        } catch (InterruptedException e) {
        }
        loop++;
        if (loop > 30) {
          break;
        }
      }
      System.err.println(tag + "Shutdown() of CD11LinkServer is complete. loop=" + loop);
    }
  }

  /**
   * This inner class reads and writes data to a CD1.1 client with the data it needs to handle all
   * DataFrame, CommandFrames, and send out AckNacks.
   */
  class CD11LinkSocket extends Thread {

    private final TimeoutSocketThread timeout;
    private final Socket sd;
    private AckNackThread ack;
    private boolean terminate;
    private int nframes;
    private final CD11Frame frm;
    private final CD11Frame outFrame;           // And return connection response frames
    private final StringBuilder creator = new StringBuilder(8);
    private final StringBuilder destination = new StringBuilder(8);
    private final StringBuilder tmpsb = new StringBuilder(100);
    private final StringBuilder toString = new StringBuilder(100);
    private final String tag2;
    private final int authID;

    @Override
    public String toString() {
      return tag2 + " #fr=" + nframes + " " + totalBytes + " b " + totalTotalBytes
              + (ack != null ? " ackid=" + ack.getId() : "") + (timeout != null ? " TOid=" + timeout.getId() : "");
    }

    public StringBuilder toStringBuilder() {
      return Util.clear(toString).append(tag2).append(" #fr=").append(nframes).
              append(" ").append(totalBytes).append(" b ").append(totalTotalBytes);
    }

    public void terminate() {
      prta(Util.clear(tmpsb).append(toStringBuilder()).append(" terminate() done"));
      if (gaps != null) {
        gaps.writeGaps(true);
      }
      if (ack != null) {
        ack.close();
      }
      ack = null;
      terminate = true;
      try {
        if (sd != null) {
          sd.close();
        }
      } catch (IOException e) {
      }
      timeout.terminate();
    }

    /**
     * This class reads and writes data on an open socket to a CD1.1 data socket. It gets the
     * connection from the parent CD11LinkServer.
     *
     * @param s The socket on which to perform data I/O.
     * @param stat The station name for tags.
     */
    public CD11LinkSocket(Socket s, String stat, StringBuilder creat, StringBuilder dest, int auth, EdgeThread parent) {
      sd = s;
      Util.clear(creator).append(creat);
      Util.clear(destination).append(dest);
      authID = auth;
      frm = new CD11Frame(recSize, 100, parent);
      terminate = false;
      //if(dbg) CD11Frame.setDebug(dbg);
      tag2 = "CD11LSock:" + tag + "[" + this.getId() + "]";
      outFrame = new CD11Frame(10000, 100, creator, destination, 0, -1, auth, parent);
      timeout = new TimeoutSocketThread(tag + "TOT:", sd, 300000);
      timeout.enable(true);
      start();
    }

    public void clearAck() {
      gaps.clearGapsOnly();
      terminate();
    }

    @Override
    public void run() {
      StringBuilder sb = new StringBuilder(10000);
      prta(Util.clear(tmpsb).append(tag2).append(" Starting up ").append(creator).append(":").append(destination).append(" s=").append(sd));
      long[] lowgapsin = new long[1000];
      long[] highgapsin = new long[1000];
      int len;
      lastDataRead = System.currentTimeMillis();
      int status;
      long lastemail = lastDataRead;
      int newFramesetCount = 0;
      boolean gotFirstAck = false;
      StringBuilder taga = new StringBuilder(10);
      StringBuilder tagb = new StringBuilder(10);
      try {
        InputStream in = sd.getInputStream();
        //ack.sendAck();
        while (!terminate) {
          state = 1;
          if (gaps == null) {
            state = 2;
            gaps = new GapList(0, frm.getSeq(), "CD11/" + station + ".gaps", par);  // Once the series is known, check for prior gaps
            gaps.setDebug(dbg);
            firstSeq = gaps.getLowestSeq();
            prta(Util.clear(tmpsb).append(tag2).append(" start socket low=").append(firstSeq).
                    append(" hi=").append(gaps.getHighestSeq()).append(" #gap=").append(gaps.getGapCount()));
          }
          if (ack == null) {
            state = 3;
            ack = new AckNackThread(tag2, sd, creator, destination, authID, gaps, par);
            ack.setDebug(dbg);
          }
          state = 4;
          //if(tag2.indexOf("ZAL") >= 0) prta("Got to read Frame");
          len = frm.readFrame(in, null);    // change to par if details of reading frame needed.
          timeout.resetTimeout();         // reset the timeout, we got some input
          state = 5;
          //if(tag2.indexOf("ZAL") >= 0) prta("Return from read Frame len="+len+" "+frm.toString());
          if (len == 0) {
            terminate = true;
            prta(Util.clear(tmpsb).append(tag2).append("GOT EOF ON socket--- exit!"));
            continue;
          }
          totalBytes += len + CD11Frame.FRAME_HDR_LENGTH + CD11Frame.FRAME_TRAILER_LEN;
          totalTotalBytes += len + CD11Frame.FRAME_HDR_LENGTH + CD11Frame.FRAME_TRAILER_LEN;
          if (dbg) {
            prta(Util.clear(tmpsb).append("RCV ").append(frm.toString()).append(" ").
                    append(frm.getSeq() == gaps.getHighestSeq() + 1 ? "" : "*"));
          }

          if (!gotFirstAck && nframes % 10 == 9) {
            state = 6;
            ack.setEnableAcks(true);
            prta(Util.clear(tmpsb).append(tag2).append(" *** enable acts by 10 packets!"));
            gotFirstAck = true;
          }
          state = 7;
          if (frm.getType() != CD11Frame.TYPE_OPTION_REQUEST && frm.getType()
                  != CD11Frame.TYPE_ACKNACK && frm.getType() != CD11Frame.TYPE_ALERT) {
            state = 8;
            if (lastSeries == -1) {
              lastSeries = frm.getSeries();
              prta(Util.clear(tmpsb).append(tag2).append(" initial series=").append(lastSeries).
                      append(" seq=").append(frm.getSeq()));
              firstSeq = gaps.getLowestSeq();
              if (frm.getSeq() < firstSeq - 1000 || frm.getSeq() < gaps.getHighestSeq() - 2000) { // The ohter end is doing different sequences, clear our memory
                prta(Util.clear(tmpsb).append(tag2).append(" initial sequence is before gaps sequences - reset all gap and sequence info"));
                firstSeq = -1;
                gaps.clear(lastSeries);
                ack.sendAck();
              }
              /*if(frm.getSeq() > gaps.getHighestSeq()) {
                  gaps.newGap(gaps.getHighestSeq()+1, frm.getSeq() -1);
                 if(dbgseq) prta(Util.clear(tmpsb).append(tag2+" initial gap2 from "+(gaps.getHighestSeq()+1)+" to "+(frm.getSeq() -1))));
              }*/
            }
            if (frm.getSeries() != lastSeries) {
              prt(Util.clear(tmpsb).append(tag2).append(" Series has change from ").append(lastSeries).append(" to ").append(frm.getSeries()));
              lastSeries = frm.getSeries();
              gaps.clear(frm.getSeries());
              firstSeq = -1;
              ack.sendAck();
            }

            // Check the sequences
            // Is this the very first seq
            if (firstSeq == -1 || firstSeq == 0) {
              prta(Util.clear(tmpsb).append(tag2).append(" initial seq=").append(frm.getSeq()));
              firstSeq = frm.getSeq();   // Is this the first data sequence, if so , set it
              ack.sendAck();
            }
            status = gaps.gotSeq(frm.getSeq());
            // See if the status is not in the current frame set. If so, see if it is a "reset" and clear up the gaps
            if (status >= 5) {
              if (frm.getSeq() < 2000 && (frm.getSeq() < firstSeq - 1000 || frm.getSeq() < gaps.getHighestSeq() - 2000)) { // The other end is doing different sequences, clear our memory
                prta(Util.clear(tmpsb).append(tag2).append(" *** sequence is before gaps or way below high- reset all gap and sequence info"));
                firstSeq = -1;
                prta(Util.clear(tmpsb).append(gaps.toString()));
                gaps.clear(lastSeries);
                gaps.gotSeq(frm.getSeq());
                SendEvent.debugSMEEvent("CD11OOFrmRst", tag2 + " Out-of-framset cause reset seq=" + frm.getSeq() + " hi=" + gaps.getHighestSeq(), this);
              } else {
                // They sometimes send a packet not in a gap. If it is recent, it is just them being stupid
                // About what to send next, relative to what is acked. If so, ignore the packet. If not, it is an error
                if (gaps.getHighestSeq() - frm.getSeq() > 100) {
                  prta(Util.clear(tmpsb).append(tag2).append(" *** seq not in any gap and OLD! sq=").append(frm.getSeq()).
                          append(" ").append(frm.getDataNominalTime()).append(" st=").append(status).
                          append(" ").append(gaps.getHighestSeq() - frm.getSeq()).append(" dbg=").append(dbg).
                          append(" ").append(dbg ? gaps.toString() : ""));
                  SendEvent.debugSMEEvent("CD11OOGapSet", tag2 + " No gap for seq=" + frm.getSeq() + " hi=" + gaps.getHighestSeq() + " status=" + status, this);
                } else {    // In this case its just one of there, we resent it before the ack - ignore it
                  prta(Util.clear(tmpsb).append(tag2).append(" * seq not in any gap - discard. sq=").append(frm.getSeq()).
                          append(" ").append(frm.getDataNominalTime()).append(" st=").append(status).
                          append(" dbg=").append(dbg).append(" ").append(gaps.getHighestSeq() - frm.getSeq()).
                          append(" ").append(dbg ? gaps.toString() : ""));
                  continue;   // skip processing this one
                }
              }
            }
          }
          nframes++;          // Handle the various frame types
          npacket++;
          state = 9;
          if (frm.getType() == CD11Frame.TYPE_DATA || frm.getType() == CD11Frame.TYPE_CD1_ENCAPSULATION) {
            //doDataFrame();
            lastDataRead = System.currentTimeMillis();
            lastReset = lastDataRead;
            state = 10;
            processCD11Frame(frm.getBodyByteBuffer(), len + CD11Frame.FRAME_HDR_LENGTH + CD11Frame.FRAME_TRAILER_LEN);
          } /* I am not sure why, but AFTAC sends one ACK to us at start up.  It appears to
           * tell us the "frameset" name to use in our acks (incoming data is in frameset TXAR:*
           * but the frameset of the ack would be TXAR:0).  Right now this section loads the 
           * parsed frame set back and sets it in the ack frame so the frames will not be rejected.
           */ else if (frm.getType() == CD11Frame.TYPE_ACKNACK) {
            state = 11;
            ByteBuffer bb = ByteBuffer.wrap(frm.getBody());
            bb.position(0);
            byte[] set = new byte[20];
            bb.get(set);
            for (int i = 0; i < 20; i++) {
              if (set[i] == 0) {
                set[i] = 32;
              }
            }
            String frmset = new String(set);
            long lo = bb.getLong();
            long hi = bb.getLong();
            int ngaps = bb.getInt();
            int l = frmset.indexOf(" ");
            if (l >= 0) {
              frmset = frmset.substring(0, l).trim();
            }
            prta(Util.clear(tmpsb).append(tag2).
                    append("Ack RCV frmset=").append(Util.toAllPrintable(frmset)).
                    append(" low=").append(lo).append(" high=").append(hi).
                    append(" #gaps=").append(ngaps).append(" authid=").append(frm.getAuthID()));
            if (ngaps > lowgapsin.length) {
              lowgapsin = new long[ngaps * 2];
              highgapsin = new long[ngaps * 2];
            }
            for (int i = 0; i < ngaps; i++) {
              lowgapsin[i] = bb.getLong();
              highgapsin[i] = bb.getLong();
//              if(dbg) {prta(Util.clear(tmpsb).append(tag2+"Ack RCV frmset="+Util.toAllPrintable(frmset)+" "+i+" gap "+lowgapsin[i]+"-"+highgapsin[i]));
            }

            try {
              try (RawDisk gapsout = new RawDisk("CD11/" + station + ".gapsin", "rw")) {
                Util.clear(sb);
                sb.append("Ack RCV frmset=").append(Util.toAllPrintable(frmset)).append(" low=").
                        append(lo).append(" high=").append(hi).append(" #gaps=").append(ngaps).
                        append(" authid=").append(frm.getAuthID()).append("\n");
                for (int i = 0; i < ngaps; i++) {
                  sb.append(lowgapsin[i]).append("-").append(highgapsin[i]).append(" ").
                          append(highgapsin[i] - lowgapsin[i]).append(" ").
                          append(hi - lowgapsin[i]).append("\n");
                }
                if (gapbuf.length < sb.length()) {
                  gapbuf = new byte[sb.length() * 2];
                }
                Util.stringBuilderToBuf(sb, gapbuf);
                gapsout.writeBlock(0, gapbuf, 0, sb.length());
                gapsout.setLength(sb.length());
              }
            } catch (IOException e) {

            }
            gotFirstAck = true;
            ack.setEnableAcks(gotFirstAck);
            boolean newFrameset = gaps.rcvAck(lo, hi, ngaps, lowgapsin, highgapsin, tag2);
            if (newFrameset) {
              prta(Util.clear(tmpsb).append(tag2).
                      append("       ****** Ack received maybe a new frameset, old frameset may have been replaced!"));
              SendEvent.debugSMEEvent("CD11FrmReset", tag2 + " Rcv ACK reset the the frameset possible!", this);
              newFramesetCount++;
            }
            String[] parts = frmset.split(":");
            ack.setCreatorDestination(Util.clear(taga).append(parts[0]), Util.clear(tagb).append(parts[1]), frm.getAuthID());

            if (System.currentTimeMillis() - lastDataRead > 7200000 || newFramesetCount > 60) {
              prta(Util.clear(tmpsb).append(tag2).append(" ").append(station).
                      append(" ** no data received in a long time or newFrameset>60=").append(newFramesetCount));
              newFramesetCount = 0;
              lastDataRead = System.currentTimeMillis();
              SendEvent.debugSMEEvent("CD11NoData", "CD1.1 " + station + " is connnected but nodata or new frameset=" + newFramesetCount, this);
              terminate();
            }
            //ack.sendAck();
          } else if (frm.getType() == CD11Frame.TYPE_ALERT) {
            state = 12;
            prta(Util.clear(tmpsb).append(tag2).append(" Alert frame.  close down this link"));
            terminate = true;
            break;
          } else if (frm.getType() == CD11Frame.TYPE_COMMAND_REQUEST) {
            state = 13;
            prta(Util.clear(tmpsb).append(tag2).append("   *** got an unhandled COMMAND_REQUEST"));
          } // These seem to be useless, but are in the 0.3 manual 4.6 (about page 41)
          else if (frm.getType() == CD11Frame.TYPE_OPTION_REQUEST) {
            state = 14;
            ByteBuffer bb = ByteBuffer.wrap(frm.getBody());
            bb.position(0);
            int optionCount = bb.getInt();
            prt(Util.clear(tmpsb).append("Option Request count=").append(optionCount));
            for (int jj = 0; jj < optionCount; jj++) {
              int type = bb.getInt();      // Get the type
              int optionNchar = bb.getInt();
              byte[] optbuf = new byte[optionNchar];
              bb.get(optbuf);

              prt(Util.clear(tmpsb).append("Option ").append(jj).append(" type=").append(type).
                      append(" l=").append(optionNchar).append(" : ").append(Util.toAllPrintable(new String(optbuf))));
              for (int k = 0; k < optionNchar; k++) {
                prt(k + "=" + optbuf[k]);
              }

            }
            outFrame.loadOptionResponse(frm.getBody(), frm.getBodyLength());
            byte[] out = new byte[200];
            bb = ByteBuffer.wrap(out);
            bb.position(0);
            len = outFrame.getOutputBytes(bb);
            prt(Util.clear(tmpsb).append("Option Response len=").append(len));
            for (int i = 0; i < len; i++) {
              prt((i + "   ").substring(0, 3) + " " + out[i]);
            }
            sd.getOutputStream().write(out, 0, len);

          } else if (frm.getType() == CD11Frame.TYPE_CD1_ENCAPSULATION) {
            prta(Util.clear(tmpsb).append(tag2).append("   *** got an unhandled CD1_ENCAPSULATION"));

          } else if (frm.getType() == CD11Frame.TYPE_CONNECTION_REQUEST) {
            prta(Util.clear(tmpsb).append(tag2).append("   *** got an unhandled CONNECTION_REQUEST"));

          } else {
            prta(Util.clear(tmpsb).append(tag2).append(" Got unexpected frame type=").append(frm.getType()));
            //frm.sendAlert("Link unexpected type");
          }
          state = 16;
          if (nframes % 1000 == 10) {
            if (!oordbg.equals("")) {
              OORChan.setDebug(oordbg, true);
              RawToMiniSeed.setDebugChannel(oordbg);
            }
            prta(Util.clear(tmpsb).append(toString()));
          }
        }
      } catch (IOException e) {
        Util.SocketIOErrorPrint(e, tag2 + " getting streams or reading", getPrintStream());
      } catch (RuntimeException e) {
        prta(Util.clear(tmpsb).append(tag2).append("Got RuntimeError e=").append(e));
        e.printStackTrace(getPrintStream());
      }
      prta(Util.clear(tmpsb).append(tag2).append(" main look exit"));

      if (!sd.isClosed()) {
        if (ack != null) {
          ack.close();
          ack.terminate();
        }
      }// DCK 11/09/ change from ack outside if(!sd and added not null)
      //try {writeHeader();} catch(IOException e) {}
      if (!sd.isClosed()) {
        try {
          sd.close();
        } catch (IOException e) {
        }
      }
      timeout.terminate();
      ack.terminate();
      prta(Util.clear(tmpsb).append(tag2).append(" has exited"));

    }

    /**
     * This processes an input data frame to MiniSeed (does not use the ring buffer method).
     *
     * @param bb A byte buffer to the body of the frame.
     */
    private void processCD11Frame(ByteBuffer bb, int frameLen) {
      int dataNChan;
      int dataFrameMS;
      String nominalTime;
      int channelStringCount;
      ChannelSubframe csf = null;
      String seedname;
      StringBuilder seednameSB = new StringBuilder(12);
      int dataQuality;
      int ioFlags;
      int clockQuality;
      int activity;
      int usec;
      GregorianCalendar g;
      // Got data to process
      // Decode parts of the data frame header
      bb.position(0);
      dataNChan = bb.getInt();
      dataFrameMS = bb.getInt();   // Length of time represented by this data frame
      bb.get(timeScratch);         // Get 20 bytes of time
      nominalTime = new String(timeScratch);
      state = 30;
      CD11Frame.fromCDTimeString(nominalTime, nominal, this);
      state = 31;
      channelStringCount = bb.getInt();
      int org = channelStringCount;
      if (channelStringCount % 4 != 0) {
        channelStringCount += 2;
      }
      boolean cd10 = (dataNChan & 0xff0000) != 0;
      dataNChan = dataNChan & 0xffff;
      //if(dbg) 
      par.prta(Util.clear(tmpsb).append("Fr: data nch=").append(Util.toHex(dataNChan)).
              append(" len=").append(dataFrameMS).append(" ms ").append(nominalTime).
              append(" ").append(Util.ascdate(nominal)).append(" ").
              append(Util.asctime2(nominal)).append(" chStrCnt=").append(channelStringCount).
              append("/").append(org).append(" cd10=").append(cd10 ? "t" : "f"));
      state = 32;
      bb.position(bb.position() + channelStringCount);
      //if(!nominalTime.equals(lastNominal) ) {
      // Process all of the channels
      Util.clear(sb);
      sb.append(nominalTime).append("@");
      for (int ich = 0; ich < dataNChan; ich++) {
        try {

          try {
            state = 33;
            if (csf == null) {
              csf = new ChannelSubframe(bb, par);
            } else {
              csf.load(bb);
            }
          } catch (RuntimeException e) {
            if (e.toString().contains("BufferUnderflow")) {
              SendEvent.debugEvent("CD11RPUnderF", tag + " Buffer too small csf=" + csf, this);
              break;      // Do not process this one
            } else {

            }
            state = 34;
            par.prta(Util.clear(tmpsb).append(tag).append(" Error trying to decode frame.  Skip this sequence ich=").
                    append(ich).append(" of ").append(dataNChan));
            e.printStackTrace(par.getPrintStream());
            break;
          }
          //if(dbg) par.prt("ch="+ich+" "+csf.toString());
          if (dbg) {
            sb.append(csf.getStation().substring(0, 8)).append(" ").append(csf.getNsamp()).append(" ");
            if (ich % 8 == 7 && ich != dataNChan - 1) {
              sb.append("\n").append(nominalTime).append("@");
            }
          }
          if (csf.getNsamp() > samples.length) {
            samples = new int[csf.getNsamp()];// Self adjust size of this array
          }
          boolean compressError = false;
          try {
            state = 35;
            try {
              csf.getSamples(samples);
            } catch (CanadaException e) {
              if (e.toString().contains("not a mult")) {
                par.prta(Util.clear(tmpsb).append(tag).append(" CanadaException: not mult of 4 on allow through ").
                        append(csf.toStringBuilder()).append(" e=").append(e));
              } else {
                par.prta(Util.clear(tmpsb).append(tag).append(" CanadaException: CD11L trying to do packet from ").
                        append(csf.toStringBuilder()).append(" e=").append(e));
                compressError = true;
              }
            }
            state = 36;
            if (!compressError) {
              state = 37;
              if (overrideStation != null) {
                seedname = network + overrideStation + csf.getStation().substring(5);
              } else {
                seedname = network + csf.getStation();
              }
              if (overrideLocation) {
                seedname = seedname.substring(0, 10) + locationCode;
              }
              // Look at status bytes for stuff we can pass on
              byte[] status = csf.getStatusBytes();
              dataQuality = 0;
              ioFlags = 0;
              clockQuality = 0;
              activity = 0;
              long cycles = 10;

              if (status != null) {
                //if(status.length < 5) par.prta("Wierd status not null an < 5 "+status.length);
                if (status[0] == 1) {    // status per 0.3 manual table 4.22
                  if (status.length >= 2) {
                    if ((status[1] & 2) != 0) {
                      dataQuality |= 16;     // Mark channel as padded
                    }
                    if ((status[1] & 4) != 0) {
                      dataQuality |= 2;       // Mark channel as clipped
                    }
                    if ((status[1] & 8) != 0) {
                      activity |= 1;         // Calibration underway
                    }
                  }
                  if (status.length >= 4) {
                    if ((status[3] & 4) == 0) {
                      ioFlags |= 32;         // Mark clock as locked
                    }
                    clockQuality = status[3] & 7;                    // Put GPS status at bottom
                  }
                  String lastLock = null;
                  if (csf.getStatusSize() >= 28) {
                    for (int i = 8; i < 28; i++) {
                      if (status[i] == 0) {
                        status[i] = 32;
                      }
                    }
                    lastLock = new String(status, 8, 20).trim();     // Get the last lock string
                  }
                  if (lastLock == null) {
                    if ((ioFlags & 32) != 0) {
                      cycles = 1;     // if the clock is locked but no last lock string assume it pretty good
                    } else {
                      cycles = 10;
                    }
                  } else if (lastLock.length() < 19) {
                    if ((ioFlags & 32) != 0) {
                      cycles = 1;     // if the clock is locked but no last lock string assume it pretty good
                    } else {
                      cycles = 10;
                    }
                  } else {
                    try {
                      state = 38;
                      CD11Frame.fromCDTimeString(lastLock, g2, this);
                      long lockDiff = csf.getGregorianTime().getTimeInMillis() - g2.getTimeInMillis();
                      cycles = lockDiff / 3600000L;
                      if (cycles < 0 || cycles > 10) {
                        par.prt(Util.clear(tmpsb).append(tag).append(" Unlocked clock: lastLock.length=").
                                append(lastLock.length()).append(" ").append(lastLock).append(": cyc=").append(cycles));
                        cycles = 10;
                      }
                    } catch (RuntimeException e) {
                      if (e.toString().contains("impossible yr")) {
                        par.prt(Util.clear(tmpsb).append(tag).append(" Got bad year in last lock field.  Ignore..."));
                      } else {
                        par.prt(Util.clear(tmpsb).append(tag).append(" Got Runtime CD11LinkSock()  e=").append(e));
                        e.printStackTrace(par.getPrintStream());
                        StackTraceElement[] stack = e.getStackTrace();
                        for (StackTraceElement stack1 : stack) {
                          par.prt(stack1.toString());
                        }
                      }
                    }
                  }
                  state = 39;
                  clockQuality |= (11 - cycles) * 8;
                  if (status.length >= 32) {
                    usec = status[28] << 24 | status[29] << 16 | status[30] << 8 | status[31];
                  } else {
                    usec = 0;
                  }
                  if (usec != 0) {
                    if (usecNotZero++ % 100 == 1 && dbg) {
                      par.prt(Util.clear(tmpsb).append("Found usec not zero =").append(usec).append(" #=").
                              append(usecNotZero).append(" ").append(csf.toStringBuilder()));
                    }
                  }
                }
                if (cycles > 0 || status[3] != 0 || clockQuality < 50) {
                  par.prta(Util.clear(tmpsb).append("Fr: status ** [0]=").append(Util.toHex(status[0])).
                          append(" [1]=").append(status.length >= 2 ? Util.toHex(status[1]) : "null").
                          append(" [2]=").append(status.length >= 3 ? Util.toHex(status[2]) : " null").
                          append(" [3]=").append(status.length >= 4 ? Util.toHex(status[3]) : " null").
                          append(status.length >= 4 ? ((status[3] & 1) != 0 ? " Clk Diff Large" : "") : "").
                          append(status.length >= 4 ? ((status[3] & 2) != 0 ? " GPS off" : "") : "").
                          append(status.length >= 4 ? ((status[3] & 4) != 0 ? " GPS Unlocked" : "") : "").
                          append(" act=").append(activity).append(" IOFlag=").append(ioFlags).
                          append(" dQ=").append(dataQuality).
                          append(" tQ=").append(clockQuality).append(" cyc=").append(cycles)
                  );
                }
              }
              state = 40;
              g = csf.getGregorianTime();
              try {
                MasterBlock.checkSeedName(seedname);
              } catch (IllegalSeednameException e) {
                par.prt(Util.clear(tmpsb).append(tag).append(" Bad seed channel name =").append(seedname).append(" e=").append(e));
                continue;
              }
              state = 41;
              seedname = seedname.toUpperCase();
              if (network.equals("GT") || seedname.substring(2, 6).equals("ATTU")) {
                String loc = "  ";
                if (seedname.substring(6, 7).equals("1")) {
                  loc = "10";
                }
                if (seedname.substring(6, 7).equals("B")) {
                  loc = "00";
                }
                seedname = seedname.substring(0, 6) + " " + seedname.substring(7, 10) + loc;
                if (loc.equals("  ") && !seedname.substring(2, 6).equals("BDFB")) {
                  par.prta(Util.clear(tmpsb).append(tag).append(" ***** bad GT seedname translation=").append(seedname));
                }
              }
              // check that this channel exists
              Channel c = EdgeChannelServer.getChannel(seedname);
              if (dbg) {
                par.prta(Util.clear(tmpsb).append(tag).append(" CD11 process data").
                        append(seedname).append(" rtn=").
                        append((c == null ? "Null" : c.getSendtoMask() + " rt=" + c.getRate())));
              }

              if (c == null) {
                par.prta(Util.clear(tmpsb).append(tag).append(" CD11: need to create channel ").
                        append(seedname).append(" rt=").append(csf.getRate()));
                EdgeChannelServer.createNewChannel(seedname, csf.getRate(), this);
                //c = EdgeChannelServer.getChannel(seedname);
              }

              Util.clear(seednameSB).append(seedname);    // HACK : not ready to make seednames StringBuilders everywhere
              Util.rightPad(seednameSB, 12);
              state = 42;
              //if(seednameSB.charAt(0) == 'A' && seednameSB.charAt(1) == 'Y') par.prt(seednameSB+" "+Util.ascdatetime2(g)+" ns="+csf.getNsamp());
              oorProcessor.addBuffer(samples, csf.getNsamp(), seednameSB, g.get(Calendar.YEAR), g.get(Calendar.DAY_OF_YEAR),
                      (int) ((g.getTimeInMillis() % 86400000L) / 1000L), (int) ((g.getTimeInMillis() % 1000L) * 1000), csf.getRate(),
                      activity, ioFlags, dataQuality, clockQuality, hydra);
              if (csend != null) {
                csend.send(SeedUtil.toJulian(g.get(Calendar.YEAR), g.get(Calendar.DAY_OF_YEAR)),
                        (int) (g.getTimeInMillis() % 86400000L), seednameSB,
                        csf.getNsamp(), csf.getRate(), frameLen / dataNChan); // Give it's proportion of the whole packet
              }
            }     // If not a compression error
            state = 43;
          } catch (IOException e) {
            par.prt(Util.clear(tmpsb).append(tag).append(" IOException sending channel e=").append(e));
            e.printStackTrace(par.getPrintStream());
          } catch (RuntimeException e) {
            par.prt(Util.clear(tmpsb).append(tag).append(" Got Runtime2 CD11LinkSock() Skip channel. ns=").
                    append(csf.getNsamp()).append(" ").append(samples.length).append(" doing ich=").
                    append(ich).append(" of ").append(dataNChan).append(" e=").append(e).append(" sb=").append(sb));
            e.printStackTrace(par.getPrintStream());
            StackTraceElement[] stack = e.getStackTrace();
            for (StackTraceElement stack1 : stack) {
              par.prt(stack1.toString());
            }
          }
        } catch (RuntimeException e) {
          par.prta(Util.clear(tmpsb).append(tag).append(" Got runtime exception doing subframe  ich=").append(ich).
                  append(" of ").append(dataNChan).append(" strcnt=").append(channelStringCount).
                  append(" sb=").append(sb).append(" e=").append(e));
          e.printStackTrace(par.getPrintStream());
          break;
        }
      }
      state = 44;
      if (csf != null) {
        sb.append("trn=").append(csf.getTransform()).append(" ucf=").append(csf.getUncompressedFormat());
      }
      if (dbg && sb.length() > 0) {
        par.prt(sb.toString());
      }
      nprocess++;
    }
  }     // end of inner class CD11LinkSocket
}
