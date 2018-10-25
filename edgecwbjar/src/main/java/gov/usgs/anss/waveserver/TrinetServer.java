/*
 *  This software is in the public domain because it contains materials 
 *  that originally came from the United States Geological Survey, 
 *  an agency of the United States Department of Interior. For more 
 *  information, see the official USGS copyright policy at 
 *  http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.waveserver;

import gov.usgs.alarm.SendEvent;
import gov.usgs.anss.edge.EdgeProperties;
import gov.usgs.anss.edge.IllegalSeednameException;
import gov.usgs.anss.util.Util;
import gov.usgs.anss.edgethread.EdgeThread;
import gov.usgs.anss.cwbqueryclient.EdgeQueryClient;
import gov.usgs.anss.seed.MiniSeedPool;
import gov.usgs.anss.seed.MiniSeed;
import gov.usgs.cwbquery.EdgeQuery;
import gov.usgs.cwbquery.EdgeQueryInternal;
import gov.usgs.cwbquery.EdgeQueryServer;
import gov.usgs.cwbquery.MiniSeedArray;
import gov.usgs.cwbquery.MiniSeedCollection;
import gov.usgs.cwbquery.MiniSeedThread;
//import gov.usgs.anss.util.LeapSecondDay;
//import gov.usgs.anss.util.SeedUtil;

import java.io.IOException;
import static java.lang.Thread.sleep;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Collection;
import java.util.GregorianCalendar;
import java.util.Iterator;
import java.util.TreeMap;
import java.util.concurrent.Semaphore;
//import org.trinet.waveserver.rt.*;
import org.trinet.util.DateTime;    // This is the Leap second corrected version
import org.trinet.waveserver.rt.TrinetReturnCodes;
import org.trinet.jasi.*;
import org.trinet.jdbc.datasources.*;
import org.trinet.util.LeapSeconds;

import static org.trinet.waveserver.rt.TrinetTCPMessageTypes.TN_TCP_GETCHAN_REQ;
import static org.trinet.waveserver.rt.TrinetTCPMessageTypes.TN_TCP_GETDATA_REQ;
import static org.trinet.waveserver.rt.TrinetTCPMessageTypes.TN_TCP_GETRATE_REQ;
import static org.trinet.waveserver.rt.TrinetTCPMessageTypes.TN_TCP_GETTIMES_REQ;
import static org.trinet.waveserver.rt.TrinetTCPMessageTypes.TN_TCP_ERROR_RESP;
import static org.trinet.waveserver.rt.TrinetTCPMessageTypes.TN_TCP_GETCHAN_RESP;
import static org.trinet.waveserver.rt.TrinetTCPMessageTypes.TN_TCP_GETDATA_RESP;
import static org.trinet.waveserver.rt.TrinetTCPMessageTypes.TN_TCP_GETRATE_RESP;
import static org.trinet.waveserver.rt.TrinetTCPMessageTypes.TN_TCP_GETTIMES_RESP;

/**
 * This server accepts connections and inputs in Trinet WaveServer format and returns responses.
 * There are 4 commands possible the only one used widely is to get a time series chunk. The
 * commands are : GETDAATA, GETRATE, GETTIMES, and GETCHAN. The command and responses use a
 * particular object wrapping which is not implemented here as a wrapping, but as a simpler
 * PacketBuilder which parses or composes the received raw data per the Trinet protocol using a
 * ByteBuffer on the binary contents to parse or compose packets. This server spawns internal class
 * TrinetHandler threads to handle each connection.
 * <p>
 * The TrinetHandlers using the PacketBuilder for the interchange uses data structures in the
 * CWBWaveServer to get data about the channels and metadata for those channels. These structures
 * were originally developed in the CWBWaveServer in order to respond to Earthworm MENU requests and
 * Winston metadata requests. It seemed easier to just require a CWBWaveServer be run and for this
 * server to use those data structures. As such a CWBWaveServer must always be run in the QueryMom
 * to supply these structures even if the QueryMom is primarily for providing Trinet server
 * functions.
 * <p>
 * <PRE>
 * switch   arg     Description
 * -p      Port         The server port (def=2063)
 * -allowrestricted     If present, restricted channels can be served by this server
 * -maxthreads  NN      Maximum number of threads to permit simultaneously (def=1000)
 * -minthreads  NN      The number of TrinetHandler threads to start immediately (def=20)
 * -quiet               If present, reduce the verbosity of log output
 * -dbg                 Increase logging for debugging purposes
 * -dbgmsp              If present, set debugging output for the MiniSeedPool
 * -eqdbg               Turn on logging of EdgeQuery for getting data.
 * </PRE>
 *
 * @author U.S. Geological Survey  <ketchum at usgs.gov>
 */
public final class TrinetServer extends EdgeThread {

  static TrinetServer trinetThread;
  static CWBWaveServer cwbws;
  static int thrcount;
  private int port;
  private boolean dbg, eqdbg;
  private int maxThreads, minThreads;
  private String host;
  private boolean quiet;
  private boolean allowrestricted;
  private int daysBack;
  private String dataSourceFile;
  private int usedThreads;
  private ArrayList<TrinetServerHandler> handlers = new ArrayList<TrinetServerHandler>(maxThreads);
  private TreeMap<String, CWBWaveServer.Stats> stats;
  private MiniSeedPool miniSeedPool;   // This is the miniSEED pool used by memory fetches of blocks

  private String dbgSeedname;
  private ServerSocket d;
  private final ShutdownTrinetServer shutdown;
  long perfSetup = 0;
  long perfSend = 0;
  long perfQueryDisk = 0;
  long perfQueryMem = 0;
  long perfAssign;
  long perfReceive;
  int ndataquery = 0;
  private StringBuilder runsb = new StringBuilder(100);

  @Override
  public void terminate() {
    terminate = true;
    try {
      d.close();
    } catch (IOException e) {
    }
    interrupt();
  }   // cause the termination to begin

  /**
   * return the monitor string for ICINGA
   *
   * @return A String representing the monitor key value pairs for this EdgeThread
   */
  @Override
  public StringBuilder getMonitorString() {
    Util.clear(monitorsb).append("Nquery=").append(ndataquery).append("\nPerfassign=").append(perfAssign).
            append("\nrcv=").append(perfReceive).append("\nsetup+rcv=").append(perfSetup).
            append("\nSend=").append(perfSend).append("\nMem=").append(perfQueryMem).
            append("\nwithRaw=").append(perfQueryDisk).append("\n");

    return monitorsb;
  }

  @Override
  public StringBuilder getStatusString() {
    if (statussb.length() > 0) {
      statussb.delete(0, statussb.length());
    }
    statussb.append("TWS : #q=").append(ndataquery).append(" Perf: assign=").append(perfAssign).
            append(" rcv=").append(perfReceive).append(" setup+rcv=").append(perfSetup).
            append(" Send=").append(perfSend).append(" Mem=").append(perfQueryMem).
            append(" withRaw=").append(perfQueryDisk);
    return statussb;
  }

  @Override
  public StringBuilder getConsoleOutput() {
    return consolesb;
  }

  public static void main(String[] args) {
    Util.setProcess("TrinetWaveServer");
    EdgeProperties.init();
    Util.setNoconsole(false);
    Util.setNoInteractive(true);
    Util.setModeGMT();
    EdgeThread.setMainLogname("triws");
    Util.prt("Starting TrinetWaveServer");

    TrinetServer trinet = new TrinetServer("-p 6000 -datasource /home/vdl/datasource.prop >tws", "TRI");
    for (;;) {
      Util.sleep(1000);

    }

  }

  public TrinetServer(String argline, String tg) {
    super(argline, tg);
    Util.setModeGMT();
    dbg = false;
    port = 2063;
    maxThreads = 1000;
    host = null;
    minThreads = 20;
    prta(Util.clear(runsb).append("TWS: Argline=").append(argline));
    int dummy = 0;

    String[] args = argline.split("\\s");
    for (int i = 0; i < args.length; i++) {
      if (args[i].equals("-dbg")) {
        dbg = true;
      } else if (args[i].equals("-eqdbg")) {
        eqdbg = true;
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
      } else if (args[i].equals("-allowrestricted")) {
        allowrestricted = true;
      } else if (args[i].equals("-datasource")) {
        dataSourceFile = args[i + 1];
        i++;
      } else if (args[i].length() > 0) {
        if (args[i].substring(0, 1).equals(">")) {
          break;
        } else {
          prt(tag + "TrinetServer: unknown switch=" + i + " " + args[i] + " ln=" + argline);
        }
      }
    }
    if (dataSourceFile != null) {
      prta(Util.clear(runsb).append("Open data source returned =").append(openDataSource(dataSourceFile)));
    }

    prta(Util.clear(runsb).append(tag).append(Util.ascdate()).append(" TWS: created2 args=").
            append(argline).append(" tag=").append(tag).append("host=").append(host).
            append(" port=").append(port).append(" daysback=").append(daysBack).
            append(" quiet=").append(quiet).append(" allowrestrict=").append(allowrestricted).
            append(" dbgch=").append(dbgSeedname).append(" thr=").append(getId()));
    for (int i = 0; i < minThreads; i++) {
      handlers.add(new TrinetServerHandler(null));
    }
    this.setDaemon(true);
    shutdown = new ShutdownTrinetServer();
    Runtime.getRuntime().addShutdownHook(shutdown);
    running = true;
    start();
  }

  /**
   * This creates a DB connection so the LeapSeconds can be read from the DB into LeapSeconds.
   *
   * @param propFileName A property file with enough stuff to make a valid connection to and oracle
   * data source.
   * @return true if the data source appears to have worked
   */
  public final boolean openDataSource(String propFileName) {
    JasiDatabasePropertyList newProps = new JasiDatabasePropertyList(propFileName, null);
    Util.prta("Reading properties from file: " + propFileName + " #props=" + newProps.size());
    // setup the db connection info
    DbaseConnectionDescription dbDescription = newProps.getDbaseDescription();
    boolean ret = true;
    try {
      if (!dbDescription.isValid()) {
        prta("Invalid dbase spec: " + dbDescription.toString());
        ret = false;
      }

      // Connect to the dbase 
      DataSource.createDefaultDataSource();
      if (!DataSource.set(dbDescription)) {
        prta("Invalid dbase: " + dbDescription.toString());
        ret = false;
      }
    } catch (Exception e) {
      prta("Exception thrown setting up data source.");
      e.printStackTrace();
    }
    LeapSeconds.getLeapSecsAtNominal(System.currentTimeMillis() / 1000.);    // this should set the leap seconds from the DB, warning if no
    return ret;
  }

  @Override
  public void run() {
    if (trinetThread != null) {
      prt("TWS: **** Duplicate creation of CWBWaverServer! Panic!");
      Util.exit(1);
    }
    try {
      sleep(3000);
    } catch (InterruptedException e) {
    } // give CWBWaveServer a chance to come up.
    trinetThread = this;
    while ((cwbws = CWBWaveServer.thisThread) == null) {
      try {
        sleep(3000);
      } catch (InterruptedException e) {
      }
      prta("Waiting for CWBWaveServer!");
    }
    if (host == null) {
      miniSeedPool = EdgeQuery.getMiniSeedPool();   // We are getting blocks over the internet
    } else {
      miniSeedPool = EdgeQueryClient.getMiniSeedPool();         // we are using the internal (part of a QueryMom)
    }    // = new MonitorServer(monOffset < 100?AnssPorts.MONITOR_CWBWAVESERVER_PORT+monOffset:-monOffset, this);
    //GregorianCalendar now;
    //StringBuilder sbstatus=new StringBuilder(10000);
    long lastStatus = System.currentTimeMillis();
    setPriority(getPriority() + 2);
    if (dbg) {
      prta(Util.getThreadsString(200));
    }

    // OPen up a port to listen for new connections.
    while (!terminate) {
      try {
        //server = s;
        prta(Util.clear(runsb).append(tag).append(" TWS: Open Port=").append(port));
        d = new ServerSocket(port);
        break;
      } catch (SocketException e) {
        if (e.getMessage().equals(tag + " TWS: Address already in use")) {
          try {
            prt(Util.clear(runsb).append(tag).append(" TWS: Address in use - try again."));
            Thread.sleep(2000);
          } catch (InterruptedException E) {

          }
        } else {
          prt(Util.clear(runsb).append(tag).append(" TWS:Error opening TCP listen port =").
                  append(port).append("-").append(e.getMessage()));
          try {
            Thread.sleep(2000);
          } catch (InterruptedException E) {

          }
        }
      } catch (IOException e) {
        prt(Util.clear(runsb).append(tag).append(" TWS:Error opening socket server=").append(e.getMessage()));
        try {
          Thread.sleep(2000);
        } catch (InterruptedException E) {

        }
      }
    }

    String ip = "";
    Socket s = null;
    while (!terminate) {
      try {
        s = accept();
        //prta(tag+" TWS: accept from "+s);
        ip = s.getInetAddress().toString();

        // find a thread to assign this to
        boolean assigned = false;
        while (!assigned) {
          //prta(tag+" try to assign");
          // look through pool of connections and assign the first null one found
          for (int i = 0; i < handlers.size(); i++) {
            if (!handlers.get(i).isAlive()) {    // If the thread has failed, make a new one
              prta(Util.clear(runsb).append(tag).append("[").append(i).
                      append("]TWS: is not alive ***** , replace it! ").
                      append(handlers.get(i).toStringBuilder(null)));
              SendEvent.edgeSMEEvent("CWBTRIWSThrDown", tag + "[" + i + "] is not alive ***** , replace it! ", this);
              handlers.set(i, new TrinetServerHandler(null));
            }
            // If the handler is not assigned a socket, use it
            if (handlers.get(i).getSocket() == null) {
              if (dbg) {
                prta(Util.clear(runsb).append(tag).append("[").append(i).append("]TWS: Assign socket to ").
                        append(i).append("/").append(handlers.size()).append("/").append(maxThreads).
                        append(" ").append(handlers.get(i).toStringBuilder(null)));
              }
              handlers.get(i).assignSocket(s);
              usedThreads++;
              assigned = true;
              break;
            } else if (handlers.get(i).getSocket().isClosed()) { // The socket is assigned, make sure its not closed
              prta(Util.clear(runsb).append(tag).append(" TWS: found [").append(i).append("] is closed, free it"));
              handlers.get(i).closeConnection();
              handlers.get(i).assignSocket(s);
              usedThreads++;
              assigned = true;
            }
          }

          // If we did not assign a connection, time out the list, create a new one, and try again
          if (!assigned) {
            long nw = System.currentTimeMillis();
            int nfreed = 0;
            int maxi = -1;
            long maxaction = 0;
            for (int i = 0; i < handlers.size(); i++) {
              if (dbg) {
                prta(Util.clear(runsb).append(tag).append(" TWS: check ").append(i).append(" ").append(handlers.get(i)));
              }
              // only dead sockets and long left analysts could be more than an hour old
              if (nw - handlers.get(i).getLastActionMillis() > 3600000 && handlers.get(i).getSocket() != null) {
                prta(Util.clear(runsb).append(tag).append(" TWS: Free connection ").append(i).append(" ").append(handlers.get(i).toStringBuilder(null)));
                handlers.get(i).closeConnection();
                nfreed++;
              } else {
                if (maxaction < (nw - handlers.get(i).getLastActionMillis())) {
                  maxaction = nw - handlers.get(i).getLastActionMillis();
                  maxi = i;
                }
              }
            }
            if (nfreed > 0) {
              continue;        // go use one of the freed ones
            }            // If we are under the max limit, create a new one to handle this connection, else, have to wait!
            if (handlers.size() < maxThreads) {
              prta(Util.clear(runsb).append(tag).append("TWS: create new CWBWSH ").append(handlers.size()).append(" s=").append(s));
              handlers.add(new TrinetServerHandler(s));
              usedThreads++;
              assigned = true;
            } else {
              if (maxi >= 0) {
                prta(Util.clear(runsb).append(tag).append(" TWS: ** No free connections and maxthreads reached.  Dropped oldest action=").
                        append(maxaction).append(" ").append(handlers.get(maxi).toStringBuilder(null)));
                SendEvent.debugEvent("TRIWSThrFull", "TRIWS thread list is full - deleting oldest", this);
                handlers.get(maxi).closeConnection();
                continue;
              }

              prta(Util.clear(runsb).append(tag).append(" TWS: **** There is no room for more threads. Size=").append(handlers.size()).append(" s=").append(s));
              SendEvent.edgeSMEEvent("TRIWSMaxThread", "There is not more room for threads!", this);
              try {
                sleep(500);
              } catch (InterruptedException e) {
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
        Util.SocketIOErrorPrint(e, "in TRIWS setup - aborting", getPrintStream());
      } catch (RuntimeException e) {
        SendEvent.edgeSMEEvent("RuntimeExcp", "TRIWS: got Runtime=" + e, this);
        prta(Util.clear(runsb).append("TWS: Runtime in CWBWS: ip=").append(ip).append(" s=").append(s));
        e.printStackTrace(getPrintStream());
      }
      long now = System.currentTimeMillis();
    }       // end of infinite loop (while(true))
    for (TrinetServerHandler handler : handlers) {
      if (handler != null) {
        handler.terminate();
      }
    }
    prta(Util.clear(runsb).append(tag).append(" TWS:read loop terminated"));
    running = false;
  }

  private Socket accept() throws IOException {
    return d.accept();
  }

  // This class actually handles the requests.
  private final class TrinetServerHandler extends Thread {

    Semaphore semaphore = new Semaphore(1);     // a one size semaphone
    Socket s;
    long lastAction;
    String ttag;
    PacketBuilder pkt;
    StringBuilder tmpsb = new StringBuilder(100);
    StringBuilder runsb = new StringBuilder(100);
    int ithr;
    private EdgeQueryInternal queryint;   // Used by queryWithRaw() if on internal server

    @Override
    public String toString() {
      return ttag + " lastaction=" + (System.currentTimeMillis() - lastAction) / 1000 + " s=" + s;
    }

    public StringBuilder toStringBuilder(StringBuilder tmp) {
      StringBuilder sb = tmp;
      if (sb == null) {
        sb = Util.clear(tmpsb);
      }
      synchronized (sb) {
        sb.append(ttag).append(" lastaction=").append((System.currentTimeMillis() - lastAction) / 1000).append(" s=").append(s);
      }
      return sb;
    }

    public Socket getSocket() {
      return s;
    }

    public void terminate() {
      closeConnection();
    }

    /**
     * the main server thread starts up a handler by assigning it a socket. This is where this is
     * done and the thread will startup right after!
     *
     * @param ss The socket to use
     */
    public final void assignSocket(Socket ss) {
      if (ss != null) {
        perfAssign -= System.currentTimeMillis();
      }
      s = ss;
      if (s != null) {
        ttag = ttag.substring(0, ttag.indexOf("{"))
                + "{" + s.getInetAddress().toString().substring(1) + ":" + s.getPort() + "}";
        semaphore.release();
      }
      lastAction = System.currentTimeMillis();
    }

    /**
     * close the connection if it is open
     */
    public void closeConnection() {
      if (dbg) {
        prta(ttag + " CloseConnection " + s);
      }
      if (s == null) {
        return;
      }
      if (!s.isClosed()) {
        try {
          s.close();
        } catch (IOException e) {
        }
      }
      usedThreads--;
      s = null;
    }

    public long getLastActionMillis() {
      return lastAction;
    }

    public TrinetServerHandler(Socket s) {
      ithr = thrcount++;
      ttag = tag + "[" + ithr + "]-" + getId() + "{}";
      try {
        queryint = new EdgeQueryInternal(ttag + "EQI", EdgeQueryServer.isReadOnly(), true, trinetThread);
        if (eqdbg) {
          queryint.setQueryDebug(eqdbg);
        }
      } catch (InstantiationException e) {
        prta(Util.clear(runsb).append(ttag).append("TWSH: ***** could not make and EdgeQueryInteral!"));
      }
      try {
        semaphore.acquire(1);
      } catch (InterruptedException e) {
      }   // we have the block
      assignSocket(s);
      prta(Util.clear(runsb).append(ttag).append("Starting TSWH: "));
      start();
    }

    @Override
    public void run() {
      //boolean ok;
      int msgType;
      int requestSeq;
      pkt = new PacketBuilder(trinetThread);      // Build a packet object for building packets, log through this log
      GregorianCalendar g = new GregorianCalendar();
      DateTime startTime = new DateTime();
      DateTime endTime = new DateTime();
      TrinetChannel chan = new TrinetChannel();
      ArrayList<MiniSeed> mss = new ArrayList<MiniSeed>(100);
      ArrayList<MiniSeed> msmem = new ArrayList<MiniSeed>(100);
      StringBuilder chansb = new StringBuilder(12);
      ByteBuffer bb;
      double start, end;
      ChannelList ch;
      while (!terminate) {
        try {
          while (s == null) {
            try {
              semaphore.acquire(1);
            } catch (InterruptedException e) {
            }
            if (dbg) {
              prta(Util.clear(runsb).append(ttag).append("Acquired semaphore s=").append(s).
                      append(" semaphore=").append(semaphore));
            }
            perfAssign += System.currentTimeMillis();
          }
          /*while(s == null) {
            try{sleep(10);
            } catch(InterruptedException e) {}
            if(terminate) break;
          }*/
          if (terminate) {
            break;
          }
          if (s == null) {
            continue;
          }
          if (dbg) {
            prta(Util.clear(runsb).append(ttag).append("TWSH: Got a socket s=").append(s));
          }

          //ok = conn.processRequest(30000);
          // msgType=conn.getMsgType();
          long now = System.currentTimeMillis();
          //ok=false;
          try {
            int len = pkt.receive(s);
            msgType = pkt.getMessageType();
            pkt.getNextField();
            requestSeq = pkt.getFieldInt();
            if (dbg) {
              prta(Util.clear(runsb).append(ttag).append("TWSH: receive packet len=").append(len).
                      append(" msgType=").append(msgType).append(" requestSeq=").append(requestSeq));
            }
            //ok=true;
            perfReceive += System.currentTimeMillis() - now;
          } catch (IOException e) {
            if (e.toString().contains("Failed to read 20 byte")) {
              prta(Util.clear(runsb).append(ttag).append("TWSH: EOF - client has disconnected - closed=").append(s.isClosed()));
            } else if (e.toString().contains("Failed to read ")) {
              prta(Util.clear(runsb).append(ttag).append("TWSH: EOF on body - client has disconnected - closed=").append(s.isClosed()));
            } else if (e.toString().toLowerCase().contains("ocket close")) {
              prta(Util.clear(runsb).append(ttag).append(" socket closed."));
            } else {
              prta(Util.clear(runsb).append(ttag).append("TWSH: IOError reading packet e=").append(e));
            }
            closeConnection();
            continue;
          }
          lastAction = System.currentTimeMillis();
          try {
            //prta("msg type="+conn.getMsgType()+" ch="+conn.getChannel()+" wind="+conn.getTimeWindow()+" reqid="+conn.getReqID()+" "+conn.getMsgString());
            switch (msgType) {
              case TN_TCP_GETDATA_REQ:
                //Channel chan =conn.getChannel();
                //prta(chan.toIdString());
                ndataquery++;
                pkt.getNextField();     // This is the channel portion
                chan.load(pkt.getByteBufferPayload(), pkt.getFieldLength());
                pkt.getNextField();    // // This got 16 bytes with two doublsstart time in true seconds
                bb = pkt.getByteBufferPayload();
                start = bb.getDouble();
                end = bb.getDouble();
                startTime.setTrueSeconds(start);
                endTime.setTrueSeconds(end);
                if (dbg) {
                  prta(Util.clear(runsb).append(ttag).append("TWSH: getData() ").append(chan.getSeedname()).
                          append(" ").append(Util.ascdatetime2((long) (startTime.getNominalSeconds() * 1000.), null)).
                          append(" duration=").append(end - start));
                }
                g.setTimeInMillis((long) (startTime.getNominalSeconds() * 1000. + 0.5));  // Start time in nominal time
                // hit the CWBWaveServer for the data
                mss.clear();
                perfSetup += (System.currentTimeMillis() - now);
                //queryWithRaw(chan.getSeedname().replaceAll("-"," "), g, end-start, mss);
                queryWithRaw(chan.getSeednameBuilder(), g, end - start, mss);
                //TimeWindow timeWindow = conn.getTimeWindow();cat tws..log5
                now = System.currentTimeMillis();
                //Collections.sort(mss);
                MiniSeed startmss = null;
                for (MiniSeed ms : mss) {
                  if (ms.getNsamp() > 0 && ms.getRate() > 0) {
                    startmss = ms;
                    break;
                  }
                }
                // If no data was returne, send the error
                if (mss.size() <= 0 || startmss == null) {
                  //if(dbg)
                  prta(Util.clear(runsb).append(ttag).append("TWSH: getData() ").append(chan.getSeedname()).
                          append(" ").append(Util.ascdatetime2(g)).append(" dur=").append(end - start).
                          append(" returned no data"));
                  pkt.reset();
                  pkt.addIntField(requestSeq);
                  pkt.addIntField(TrinetReturnCodes.TN_NODATA);
                  pkt.rebuildHeader(TN_TCP_ERROR_RESP, 1, 1);
                  pkt.send(s);
                  perfSend += (System.currentTimeMillis() - now);
                } else {
                  // How many packets are we going to need
                  int pktTotal = mss.size() / 30;
                  if (mss.size() % 30 != 0) {
                    pktTotal++;    // there a few left over
                  }
                  pkt.reset();              // Set up for fresh packet
                  pkt.addIntField(requestSeq);          // the request sequence number
                  pkt.addDoubleField(startmss.getRate());
                  pkt.addIntField(mss.size());
                  int segSeq = 1;
                  int packetSeq = 1;
                  //if(dbg) 
                  prta(Util.clear(runsb).append(ttag).append("TWSH: getData() start ms=").
                          append(startmss).append(" mss.size=").append(mss.size()).append(" #pck=").append(pktTotal));
                  Util.clear(runsb).append(ttag).append("TWSH: getData() return from ");
                  Util.ascdatetime2(mss.get(0).getTimeInMillis(), runsb).append(" - mss(size-1)=");
                  Util.ascdatetime2(mss.get(mss.size() - 1).getTimeInMillis(), runsb).append(" end=");
                  Util.ascdatetime2(mss.get(mss.size() - 1).getNextExpectedTimeInMillis(), runsb);
                  prta(runsb);
                  // DEBUG: data seems to be out of order sometimes!
                  long last = 0;
                  boolean ordered = true;
                  for (int i = 1; i < mss.size(); i++) {
                    if (!Util.stringBuilderEqual(mss.get(i).getSeedNameSB(), mss.get(i - 1).getSeedNameSB())) {
                      prta(Util.clear(runsb).append(i).append(" **** chans not same ").append(mss.get(i).getSeedNameSB()).
                              append("|").append(mss.get(i - 1).getSeedNameSB().append("|")));
                      ordered = false;
                    }
                    if (mss.get(i - 1).getTimeInMillis() > mss.get(i).getTimeInMillis()) {
                      prta(Util.clear(runsb).append(i).append(" **** times out of order ").
                              append(Util.ascdatetime2(mss.get(i - 1).getTimeInMillis())).append(" ").
                              append(Util.ascdatetime2(mss.get(i).getTimeInMillis())));
                      ordered = false;
                    }
                  }
                  if (!ordered) {
                    prta(" **** data is unsorted! ");
                    for (int i = 0; i < mss.size(); i++) {
                      prt(i + " " + mss.get(i).toStringBuilder(null));
                    }
                    Collections.sort(mss);
                  }
                  // End fo DEBUG looking for unsorted returned mss

                  for (int i = 0; i < mss.size(); i++) {
                    startTime.setNominalSeconds(mss.get(i).getTimeInMillis() / 1000.);
                    pkt.addMiniSeed(mss.get(i), startTime.getTrueSeconds());

                    if (i == mss.size() - 1 || i % 30 == 29) {   // Time to send the packet
                      pkt.rebuildHeader(TN_TCP_GETDATA_RESP, packetSeq++, pktTotal);
                      if (dbg) {
                        prta(Util.clear(runsb).append(ttag).append("TWSH: getData() Send ").
                                append(pkt).append(" ").append(i).append(" of ").
                                append(mss.size()).append(" ms blks dataseq=").append(segSeq));
                      }
                      pkt.send(s);
                      pkt.reset();
                    }
                  }
                  freeBlocks(mss);
                  perfSend += (System.currentTimeMillis() - now);
                }
                break;
              case TN_TCP_GETTIMES_REQ:
                //chan = conn.getChannel();
                pkt.getNextField();
                chan.load(pkt.getByteBufferPayload(), pkt.getFieldLength());
                Util.clear(chansb).append(chan.getSeednameBuilder());
                Util.stringBuilderReplaceAll(chansb, "-", " ");
                ch = CWBWaveServer.getChannelList(chansb);
                if (ch == null) {
                  // The channel does not exist, send an error
                  prta(Util.clear(runsb).append(ttag).append("TWSH: getTimes() ").append(chan.getSeednameBuilder()).
                          append(" not in ChannelLIst - return error"));
                  pkt.addIntField(requestSeq);
                  pkt.addIntField(-1);
                  pkt.rebuildHeader(TN_TCP_ERROR_RESP, 1, 1);
                  pkt.send(s);
                } else {  // Send out the times
                  // get start and end times as decimal seconds from record
                  start = ch.getCreated() / 1000.;
                  end = ch.getLastData() / 1000.;
                  startTime.setNominalSeconds(start); // load the time structure with the nominal times
                  endTime.setNominalSeconds(end);
                  prta(Util.clear(runsb).append(ttag).append("TWSH: getTimes() ").append(chan.getSeednameBuilder()).
                          append(" return ").append(Util.ascdatetime2(start)).append(" to ").append(Util.ascdatetime2(end)));
                  pkt.reset();
                  pkt.addIntField(requestSeq);// send the request packet id
                  pkt.addIntField(1);     // only one time span to return
                  pkt.addInt(24);         // number of bytes in data field byte field
                  pkt.addInt(4);          // it is a byte field
                  pkt.addInt(16);         // length of byte field
                  pkt.addDouble(startTime.getTrueSeconds());  // Return start in true seconds
                  pkt.addDouble(endTime.getTrueSeconds());    // return end in true seconds
                  pkt.rebuildHeader(TN_TCP_GETTIMES_RESP, 1, 1);
                  pkt.send(s);
                }
                break;
              case TN_TCP_GETRATE_REQ:
                pkt.getNextField();
                chan.load(pkt.getByteBufferPayload(), pkt.getFieldLength());
                if (dbg) {
                  prta(Util.clear(runsb).append(ttag).append("TWSH: GetRate() channel=").append(chan.toString()));
                }
                //String seedname = chan.getSeedname();
                Util.clear(chansb).append(chan.getSeednameBuilder());
                Util.stringBuilderReplaceAll(chansb, "-", " ");
                ch = CWBWaveServer.getChannelList(chansb);// get the channel
                pkt.reset();
                if (ch == null) {  // Does the channel exist
                  // No return errorhe channel exist
                  prta(Util.clear(runsb).append(ttag).append("TWSH: getRate() ").
                          append(chan.getSeedname()).append(" is not in channel list"));
                  pkt.addIntField(requestSeq);
                  pkt.addIntField(-1);
                  pkt.rebuildHeader(TN_TCP_ERROR_RESP, 1, 1);
                  pkt.send(s);
                } else {
                  // yes, return the rate
                  prta(Util.clear(runsb).append(ttag).append("TWSH: getRate() ").
                          append(chan.getSeedname()).append(" rate=").append(ch.getMDSRate()));
                  pkt.addIntField(requestSeq);
                  pkt.addDoubleField(ch.getMDSRate());
                  pkt.rebuildHeader(TN_TCP_GETRATE_RESP, 1, 1);
                  pkt.send(s);
                }
                break;
              case TN_TCP_GETCHAN_REQ:
                // Use the CWBWaveServer Menu structures to return a list of channels
                Collection<ChannelList> clist = CWBWaveServer.getChannelListValues();
                pkt.reset();
                pkt.addIntField(requestSeq);      // this request ID
                pkt.addIntField(clist.size());    // the number of channels to return
                int i = 0;                          // count the channels for cut off into packets
                int pktSeq = 1;                     // this packet sequend
                int totalPck = clist.size() / 151;  // How many sequences
                if (clist.size() % 151 > 0) {
                  totalPck++; // including unfull packet
                }
                prta(Util.clear(runsb).append(ttag).append("TWSH: getChan() nchan=").append(clist.size()).append(" npkt=").append(totalPck));
                Iterator<ChannelList> itr = clist.iterator();
                // for each channel, load it into the packet
                while (itr.hasNext()) {
                  chan.load(itr.next());            // put it into a TrinetChannel structure
                  chan.packetLoad(pkt);             // Load the packet with this channel
                  if (i == clist.size() - 1 || i % 151 == 150) { // Is the packet now full or is this the last channel
                    // Send out the packet and then reset for more channels
                    pkt.rebuildHeader(TN_TCP_GETCHAN_RESP, pktSeq++, totalPck);
                    if (dbg) {
                      prta(Util.clear(runsb).append(ttag).append("TWSH: getChan() send ").
                              append(pkt).append(" ").append(i).append(" of ").append(clist.size()));
                    }
                    pkt.send(s);
                    pkt.reset();
                  }
                  i++;              // count the channels
                }
                break;
              default:
                prta(Util.clear(runsb).append(ttag).
                        append(">>>Error TCPConnServerClient.processRequest(int) Unrecognized request type in message"));
            }
          } catch (IOException e) {
            prta(Util.clear(runsb).append(ttag).append("TWSH: got IOerror e=").append(e));
            closeConnection();
          }
        } catch (RuntimeException e) {
          if (e.toString().indexOf("OutOfMemory") > 0) {
            prta(Util.clear(runsb).append(ttag).append("TWSH: got out of memory - try to terminate!"));
            prt(Util.getThreadsString());
            Util.exit(101);
          }
          prt(Util.clear(runsb).append(ttag).append(" TWSH: Handler RuntimeException caught e=").append(e).
                  append(" msg=").append(e.getMessage() == null ? "null" : e.getMessage()).append(s));
          e.printStackTrace(getPrintStream());
          closeConnection();
        }
      }
      //prt("Exiting CWBWaveServers run()!! should never happen!****\n");
      prta(Util.clear(runsb).append(ttag).append(" TWSH: terminated"));
    }

    /**
     * This is the main way to query data when it is needed. The ArrayList<MiniSeed>
     * contains data which came from disk based queries supplemented with data which came from the
     * MiniSeedThread (RAM based) data. They may overlap so the user beware. The user must also call
     * freeBlocks(mss) when done with the results to free these blocks from their respective pools.
     *
     * @param channel The NNSSSSSCCCLL of the channel to query
     * @param g The start time of the query in nominal time
     * @param duration The length of the query in seconds
     * @param mss An ArryList<MiniSeed> to receive data blocks from a query to disk
     */
    private void queryWithRaw(StringBuilder channel, GregorianCalendar g, double duration, ArrayList<MiniSeed> mss) {
      // Save the daysbackms time, and duration so this can be split up into two requests (disk and RAM)
      long now = System.currentTimeMillis();
      long st = g.getTimeInMillis();
      long endat = st + (long) (duration * 1000. + 1000.001);   // set end time for duration + 1 second to be sure
      if (dbg) {
        prta(Util.clear(runsb).append(ttag).append("Start Trinet QueryWithRaw ").append(channel).
                append(" ").append(Util.ascdatetime2(g)).append(" dur=").append(duration).
                append(" mss.size=").append(mss.size()));
      }
      // qscthr = MiniSeedCollection.getMiniSeedThr(channel.replaceAll("-"," "));
      MiniSeedThread qscthr = MiniSeedCollection.getMiniSeedThr(Util.getHashFromSeedname(channel));
      MiniSeedArray msarr;
      if (qscthr != null) {
        msarr = qscthr.getMiniSeedArray();
        int n = msarr.query(st, endat, mss, miniSeedPool);
        // If we got some blocks, adjust endat to be the time before the first block
        if (n > 0) {
          if (mss.get(0).getTimeInMillis() < endat) {
            endat = mss.get(0).getTimeInMillis();  // shorten by length of these blocks
          }
        }
        prta(Util.clear(runsb).append(ttag).append("Trinet QueryWIthRaw after memory mss.size=").append(mss.size()).
                append(" ").append(channel).append(" st=").
                append(Util.ascdatetime2(st)).append(" dur=").append(duration).append(" remaindur=").append(endat - st));
        perfQueryMem += (System.currentTimeMillis() - now);
        if (endat - st < 500. / msarr.getRate()) {
          return;      // No, its all in the memory MiniSeed or query is ill formed
        }
      }
      // Is there anything left to get?grep 
      // Are we connected to a local
      now = System.currentTimeMillis();
      if (cwbws.getHost() == null) {
        try {
          if (dbg) {
            prta(Util.clear(runsb).append(ttag).append("Trinet QueryWithRaw do queryint with ").
                    append(channel).append(" ").append(Util.ascdatetime2(st)).append(" dur=").
                    append(endat - st).append(" mss.size=").append(mss.size()));
          }
          queryint.query(channel.toString().replaceAll("-", "  "), st, (endat - st) / 1000., mss);
          Collections.sort(mss);
          //if(dbg)
          prta(Util.clear(runsb).append(ttag).append("Trinet QueryWithRaw: internal query ret=").
                  append(mss.size()).append(" ").append(channel).append(" st=").append(Util.ascdatetime2(st, null)).
                  append(" dur=").append((endat - st) / 1000.));
        } catch (IOException e) {
          prta(Util.clear(runsb).append(ttag).append("** IOError on internal query! ").append(channel).
                  append(" ").append(Util.ascdatetime2(st, null)).append(" dur=").
                  append((endat - st) / 1000.).append(" e=").append(e));
        } catch (IllegalSeednameException e) {
          prta(Util.clear(runsb).append(ttag).append("** Illegal Seedname on internal query ").append(channel).append(" e=").append(e));
        }
      } else {
        // We need to query a remote server with EdgeQueryClient -- THIS CODE IS UNTESTED!
        prta(Util.clear(runsb).append(ttag).append(" ***** Attempt to query from remote disk is not tested !!!!!"));
        String str = "-s " + channel.toString().replaceAll(" ", "-") + " -uf -q -b " + Util.ascdate(st).toString().replaceAll("/", "-") + "-" + Util.asctime2(st)
                + " -d " + Util.df23((endat - st) / 1000.) + " -nonice -h " + host + " -t null";
        prta(Util.clear(runsb).append(ttag).append("getData): queryWithRaw: query from remote server str=").append(str));

        if (dbg) {
          prta(ttag + " str beg=" + str);
        }
        // If this is a sizeable request, make sure there is pool space
        if (duration > 1000) {
          while (EdgeQueryClient.getMiniSeedPool().getUsedList().size() > 100000) {
            prta(ttag + " ** wait for space on miniseed pool GETSCN " + EdgeQueryClient.getMiniSeedPool().getUsedList().size());
            Util.sleep(4000);
          }
        }
        ArrayList<ArrayList<MiniSeed>> msstmp = EdgeQueryClient.query(str);
        prta(ttag + "getData(): queryWithRaw: ret msstmp.size()=" + (msstmp == null ? "null" : msstmp.size()));
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
              prta(ttag + "getData(): queryWithRaw: ret blks.size()=" + (blks == null ? "null" : blks.size()));
              if (dbg) {
                prta(ttag + "getData): str=" + str + " return=" + (blks == null ? "Null" : blks.size()));
              }
            }
          }
        } catch (RuntimeException e) {
          prta(ttag + "getData(): queryWithRaw runtim error e=" + e);
          e.printStackTrace(getPrintStream());
        }
        if (msstmp != null) {
          msstmp.clear();
        }
      }
      perfQueryDisk += (System.currentTimeMillis() - now);
    }

    /**
     * free up all of the pool space consumed by a request to queryWithRaw(). The local miniSeedPool
     * is used for both disk and memory based blocks
     *
     * @param msb The ArrayList of MiniSeed blocks from a disk based request method
     */
    private void freeBlocks(ArrayList<MiniSeed> msb) {
      // Free all of the blocks
      if (msb != null) {
        for (int i = msb.size() - 1; i >= 0; i--) {
          miniSeedPool.free(msb.get(i));
          msb.remove(i);
        }
      }
    }
  }   // End of TrinetHandler class

  private class ShutdownTrinetServer extends Thread {

    public ShutdownTrinetServer() {

    }

    /**
     * this is called by the Runtime.shutdown during the shutdown sequence cause all cleanup actions
     * to occur
     */
    @Override
    public void run() {
      terminate = true;
      interrupt();
      prta(Util.clear(runsb).append(tag).append(" CWS: Shutdown started"));
      int nloop = 0;
      if (d != null) {
        if (!d.isClosed()) {
          try {
            d.close();
          } catch (IOException e) {
          }
        }
      }
      prta(Util.clear(runsb).append(tag).append(" CWS:Shutdown Done."));
      try {
        sleep(10000);
      } catch (InterruptedException e) {
      }
      //if(!quiet) 
      prta(Util.getThreadsString());
      prta(Util.clear(runsb).append(tag).append(" CWS:Shutdown Done exit"));

    }
  }

}
