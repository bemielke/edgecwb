/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.edgemom;

import gov.usgs.alarm.SendEvent;
import gov.usgs.anss.cd11.CD11Frame;
import gov.usgs.anss.cd11.CD11LinkServer;
import gov.usgs.anss.cd11.GapList;
import gov.usgs.anss.edge.IndexFile;
import gov.usgs.anss.edge.RTMSBuf;
import gov.usgs.anss.edge.RawToMiniSeed;
import gov.usgs.anss.edgethread.EdgeThread;
import gov.usgs.anss.util.Util;
import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import gnu.trove.map.hash.TLongObjectHashMap;
import gnu.trove.iterator.TLongObjectIterator;

/**
 * This server implements the CD1.1 port server which takes in Connection Requests and sets up the
 * data connection. See the CD1.1 protocol description for detail. The original version of this
 * class was coded based on the 0.2 version of the protocol description. This server never exits. It
 * will accept and configure connections that are described in the configuration file. Connections
 * so configured will consistently use the same data port. To truly understand the configuration of
 * a CD1.1 you also need to review the CD11LinkServer and CD11RingProcessor classes. A "to do" might
 * be a GUI/DB table to help with this configuration.
 * <p>
 * Connections that arrive for stations (or groups) which are not pre-configured will connect, but a
 * floating pool of connections will be used, the highest of which will be given by command line
 * argument highport. These will be assigned sequentially downward for all connections which are not
 * pre-configured.
 * <p>
 * Notes on ForwardPort:
 * <p>
 * At the NEIC it was desired to run a single ConnectionServer but for the data to be processed as
 * two separate vdl instances so that the output goes in separate data files (for private and public
 * data). This allows the public data to be replicated to the public cwb and the private data to be
 * limited to internal systems. To do this, two CD11ConnectionServers are started. One handles
 * connections from the sender with a -forwardport setup. The other has its listener to the forward
 * port. The primary handler handles all connections and sets up a CD11LinkServer for each possible
 * connection, but the CD11LinkServer knows which account it is suppose to run in. When a connection
 * comes in, the CD11LinkServer is consulted for the assigned account and if it is not a match to
 * the account where the EdgeMom instance is running, the connection is passed onto the forward port
 * on the same computer. So, the two instances will have CD11LinkServer but some will be place
 * holders if they are for another account, and others will actively be listening for the Connection
 * hand offs. So, use -a in the configuration file for the CD11LinkServers to set the data sources
 * accounts, and make sure the actual listener has the right forward port for the connections not
 * assigned to it.
 * <p>
 *
 * The CDLinkServer needs to be matched with a CD11RingProcessor.
 * <p>
 * NOTE: The configuration files are reread periodically and any new lines cause the link to be
 * started. However, there is no provision for checking for changes in parameters and restarting the
 * data collection thread. To reconfigure the EdgeMom threads running this thread, they must be
 * restarted.
 *
 * <PRE>
 *It is an EdgeThread with the following arguments :
 * Tag			 arg          Description
 * -p				 ppppp        Run the server on this port (def 2020).
 * -b				 def.bin.adr  The address to bind listeners by default (can be overridden in config file).
 * -c				 configFile   The file name containing configuration of listeners (see config file format below).
 * -pool		 port					The top most port of the pool of ports to use for unassigned connections (def 2079).
 * -path		 Path         Path for scratch space for rings.
 * -gaplimit nnnn					Set the acknack gap list limit to nnnn.  Default is 100.
 * -dbg										Run debug output on this server.
 * >[>]			 filename     You can redirect output from this thread to the filename in the current logging directory.
 *
 * The configuration lines look like (command line to CD11LinkServer) :
 * TAG:-p port [-b bind.addr] -remoteHost nn.nn.nn -recsize nnnn -maxrec nnnn -path /path
 *
 * TAG									Generally the station or array name.
 * -b						ip.adr	Overrides the default bind address from EdgeMom.setup line for CD11ConnectionServer.
 * -p						port		The port to listen to for data connections.
 * -remoteHost	host		The IP address of the expected connections.
 * -recsize			nnnn		The size of the records to put in the ring file in bytes (should be a multiple of 512).
 * -oorint			ms			The number of millis between calls to the process() for OORCHAN (30000 is good value).
 * -oordbg			name		An OORChannel to do detailed output in the OutOfOrderRTMS.
 * -dbg									Turn on more debug output.
 * -fn					NN			Force input data network to NN.
 * -fs					SSSSS		Force input data station name to SSSSS.
 * -fl									Force input data location code to change if -loc is specified to that code, else to sp-sp.
 * -loc					LL			Set the location code to this value.
 * -noudpchan						Do not send output to the UdpChannel server for channel display.
 * -forwardport pp			This is used when a port forward for non-listening servers is used.  It is not used outside of the USGS.
 * -a						account This link is to be handled by the CD11ConnectionServer on this account only (used to separate public and non-public data at USGS).
 *
 *  This also runs a CD11CommandServer.  This server can be used by telneting to port 7957
 *  and using the following commands :
 * clear,ARRAY   - drop the connection to ARRAY - this must match the source name in list command
 * resetack,ARRAY - reset the frame set for the connection to ARRAY - this is needed
 *                  if the frameset of sender has been changed due to failover or other
 *                  context loss.
 * list            List status from all of the source - good way to lookup valid ARRAY values.
 * HELP or QUIT  - terminate connection
 * </PRE>
 *
 * @author davidketchum
 */
public final class CD11ConnectionServer extends EdgeThread {

  private ServerSocket ss;                // The socket we are "listening" for new connections
  private final TLongObjectHashMap<CD11LinkServer> clients = new TLongObjectHashMap<>();
  //private final Map<String,CD11LinkServer> clients = 
  //     Collections.synchronizedMap(new TreeMap<String, CD11LinkServer>());// Create the list of clients attached (CD11LinkServers)
  // note this is in a Collections.synchronizedList() to support
  // synchronized iterators
  //GregorianCalendar lastStatus;
  //TODO: Delete above commented-out code if it is not possibly needed in the future.
  private int port;                       // Port we listen on 
  private InetAddress bindAddress;        // The interface we are to listen on
  private int highport;                   // The range to assign ports for unconfigured senders
  private String configFile;              // Config file name
  private ShutdownCD11ConnectionServer shutdown;
  //Status status;
  private String cmdline;
  private boolean cwbcwb;        // Passed to CD11LinkServer constructor via cmdline)
  private boolean dbg;
  private String ringPath = "";
  private int forwardPort;      // If this is non-zero, any connections not listening on this account get forwarded to this port
  private CD11CommandServer cmdsrv;
  private final StringBuilder tmpsb = new StringBuilder(100);
  private final StringBuilder tmpdest = new StringBuilder(4);
  private final StringBuilder runsb = new StringBuilder(50);
  // buffer space and manipulation
  //byte [] b;
  //ByteBuffer bb;
  private CD11Frame frame;

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
      sb.append("CD11CS: port=").append(port).append(" bind=").append(bindAddress).append(" config=").
              append(configFile).append(" fwdprt=").append(forwardPort);
    }
    return sb;
  }

  @Override
  public void terminate() {
    setTerminate(true);
    if (ss != null) {
      try {
        ss.close();
      } catch (IOException expected) {
      }
    }
  }

  public void setTerminate(boolean t) {
    terminate = t;
    if (terminate) {
      try {
        ss.close();
      } catch (IOException expected) {
      }
    }
  }

  public void setDebug(boolean t) {
    dbg = t;
  }

  /**
   * Creates a new instance of ServerThread.
   *
   * @param argline The argument line described in the class section.
   * @param tg The Edgethread tag. This will appear on all line output from this modules.
   * @throws FileNotFoundException if the configuration file cannot be found.
   * @throws UnknownHostException If the bind address cannot be converted to an InetAddress.
   */
  public CD11ConnectionServer(String argline, String tg) throws FileNotFoundException, UnknownHostException {
    super(argline, tg);
    cmdline = argline;
    String[] args = argline.split("\\s");
    dbg = false;
    //int len=512;
    //String h=null;
    port = 2020;
    highport = 2079;
    bindAddress = null;
    frame = new CD11Frame(CD11Frame.CONNECTION_BODY_LENGTH * 2, 100, this);
    forwardPort = 0;
    for (int i = 0; i < args.length; i++) {
      //prt(i+" arg="+args[i]);
      if (args[i].equals("-p")) {
        port = Integer.parseInt(args[i + 1]);
        i++;
      } else if (args[i].equals("-pool")) {
        highport = Integer.parseInt(args[i + 1]);
        i++;
      } else if (args[i].equals("-b")) {
        bindAddress = InetAddress.getByName(args[i + 1]);
        i++;
        prt("Bind adr=" + bindAddress);
      } else if (args[i].equals("-c")) {
        configFile = args[i + 1];
        i++;
      } else if (args[i].equals("-dbg")) {
        dbg = true;
      } else if (args[i].equals("-frmdbg")) {
        CD11Frame.setDebug(true);// this is likely a bad idea!
      } else if (args[i].equals("-path")) {
        ringPath = args[i + 1];
        i++;
      } else if (args[i].equals("-gaplimit")) {
        GapList.setGapLimit(Integer.parseInt(args[i + 1]));
        i++;
      } else if (args[i].equals("-forwardport")) {
        forwardPort = Integer.parseInt(args[i + 1]);
        i++;
      } else if (args[i].substring(0, 1).equals(">")) {
        break;
      } else {
        prt(Util.clear(tmpsb).append("CD11ConnectionServer unknown switch=").append(args[i]).append(" ln=").append(argline));
      }
    }
    if (bindAddress == null) {
      throw new FileNotFoundException("No host has been bound");
    }
    prta(Util.clear(tmpsb).append("CD11ConnectionServer starting on port=").append(port).
            append(" cwbcwb=").append(cwbcwb).append(" forwardPort=").append(forwardPort));
    shutdown = new ShutdownCD11ConnectionServer(this);
    Runtime.getRuntime().addShutdownHook(shutdown);
    // set up debugs
    if (cmdsrv == null) {
      cmdsrv = new CD11CommandServer(7957 - (port == 29000 ? 2 : 0));            // Create the listener
    }
    int loop = 0;
    start();
    //status=new Status();              // 5 minute status output

  }

  /**
   * This Thread does accepts() on the port and creates a new ClientSocket inner class object to
   * track its progress. Basically, it holds information about the client and detects writes that
   * are hung via a timeout Thread.
   */
  @Override
  public void run() {
    int len;
    //CD11Frame.setDebug(dbg);
    CD11Frame.setDebug(false);
    running = true;
    int loop = 0;
    while (true) {
      try {
        if (terminate) {
          break;
        }
        ss = new ServerSocket(port, 5, bindAddress);
        //lastStatus = new GregorianCalendar();   // Set initial time
        rereadConfigFile();
        break;
      } catch (IOException e) {
        loop++;
        if (loop % 40 == 39) {
          SendEvent.edgeSMEEvent("CD11BadPort", "Cannot setup " + bindAddress + "/" + port + "e=" + e, this);
        }
        prta(Util.clear(runsb).append("CD11ConnectionServer : Cannot set up socket server on port ").
                append(bindAddress).append("/").append(port).append(" e=").append(e));
        try {
          sleep(30000);
        } catch (InterruptedException expected) {
        }
      }
    }
    byte[] st = new byte[8];
    prta(Util.clear(runsb).append(" CD11Conn:").append(tag).append(" ").append(port).
            append(" start accept loop.  From ").append(ss.getInetAddress()).append("-").
            append(ss.getLocalSocketAddress()).append("|").append(ss));
    Socket s = null;
    try {
      while (!terminate) {
        try {
          if (dbg) {
            prta(Util.clear(runsb).append(" CD11Conn:").append(tag).append(" ").append(port).append("  call accept()"));
          }
          s = accept();          // Use accept method for performance analyzer
          if (terminate) {
            break;
          }
          rereadConfigFile();           // Make sure we have opened all configured ports
          if (terminate) {
            break;          // If we are terminating, kill this socket. Do not start a new one!!!
          }          // Read in the connection request
          prta(Util.clear(runsb).append("CD11Conn: accept returned connection ").append(s));
          len = frame.readFrame(s.getInputStream(), this);
          if (len == 0) {
            try {
              s.close();
            } catch (IOException expected) {
            }
            continue;        // Got an EOF reading connection, probably a probe
          }
          if (dbg) {
            prta(Util.clear(runsb).append("CD11Conn: readFrame returned type=").
                    append(frame.getType()).append(" len=").append(len));
          }
          if (frame.getType() == CD11Frame.TYPE_CONNECTION_REQUEST) {
            byte[] b = frame.getBody();
            ByteBuffer bb = ByteBuffer.wrap(b);
            bb.position(4);
            bb.get(st);
            len = 8;
            for (int i = 0; i < 8; i++) {
              if (st[i] == 0) {
                st[i] = 32;
              }
            }
            String station = new String(st, 0, len).trim();
            prta(Util.clear(runsb).append(Util.ascdate()).append("CD11Conn: got a new connection for ").append(station).
                    append(" l=").append(station.length()).append(" frmSet=").append(frame.getCreatorSB()).
                    append(":").append(frame.getDestinationSB()).append(" from ").append(s));
            if (frame.getBodyLength() != CD11Frame.CONNECTION_BODY_LENGTH) {
              prta(Util.clear(runsb).append("CD11Conn: got odd body length for connection=").append(frame.getBodyLength()));
            }
            if (station.length() == 0) {  // We got one of these and it crashed our service
              prta(Util.clear(runsb).append("CD11Conn: zero length name - abort connection"));
              s.close();
              continue;
            }

            // Now find a handler for this one
            CD11LinkServer server = clients.get(Util.getHashFromSeedname(station));
            /*Object [] keys= clients.keySet().toArray();
            for(int i=0; i<keys.length; i++) if(station.trim().equals(keys[i])) 
              prta(Util.clear(runsb).append("key match i=").append(i).append(" ").
                      append(station.trim()).append("=").append(keys[i]).append("|"));*/

            if (server == null) {    // No server opened so it is not a preconfigured one
              String line = "-b " + s.getLocalAddress().toString().substring(1) + " -p " + (highport--) + (dbg ? " -dbg" : "")
                      + " -remoteHost " + s.getInetAddress().toString().substring(1)
                      + " -recsize 81920 -oorint 30002 -fl -a vdl1 -dbg"
                      + (ringPath.length() > 0 ? " -path " + ringPath : "");
              prta(Util.clear(runsb).append("CD11Conn: ***** server is new create it =").append(station).append(" line=").append(line));
              SendEvent.edgeSMEEvent("CD11NewConn", "Conn is not configured for " + station
                      + " from " + s.getInetAddress().toString().substring(1), this);
              server = new CD11LinkServer(station, frame.getCreatorSB(), frame.getDestinationSB(), line);
              server.reopen(frame, s);
              synchronized (clients) {
                clients.put(Util.getHashFromSeedname(station), server);
              }
              SendEvent.debugEvent("CD11NoConfig", "CD1.1 got a connection which is not configured station=" + station, this);
            } else {
              if (server.isListening()) {
                if (dbg) {
                  prta(Util.clear(runsb).append("CD11Conn: server exists =").append(server));
                }
                server.reopen(frame, s);    // There is a server already, reopen it
              } else if (forwardPort != 0) {      // This must be listening on another account, forward to it
                loop = 0;
                for (;;) {
                  try {
                    prta(Util.clear(runsb).append("CD11Conn: server exists but is not listen forward to port =").append(forwardPort));
                    try (Socket tmp = new Socket(bindAddress, forwardPort)) {
                      frame.writeFrameForConnect(tmp.getOutputStream());  // Send copy of the frame just received
                      int l;
                      while ((l = tmp.getInputStream().read(b)) > 0) { // Read back response and forward it
                        s.getOutputStream().write(b, 0, l);
                        prta(Util.clear(runsb).append("CD11Conn: forward answer l=").append(l));
                      }
                      tmp.close();          // Done, close socket to forwarded server
                    } // Send copy of the frame just received
                    break;                // success- leave
                  } catch (IOException e) {  // Probably the forwarder is not yet up, wait up to a couple of minutes
                    prt(Util.clear(runsb).append("IOError trying to open socket to forward.  try=").append(loop++));
                    try {
                      sleep(5000);
                    } catch (InterruptedException expected) {
                    }
                    if (loop % 12 == 0) {
                      SendEvent.pageSMEEvent("CD11FwderDwn", "Connection forwarder not open=" + e, this);
                    }
                    if (loop > 48) {
                      break;
                    }
                  }
                }
              }
            }

          } else {
            prta(Util.clear(runsb).append("CD11Conn: got non-connection type=").append(frame.getType()));
          }
          s.close();
          prta(Util.clear(runsb).append(" CD11Conn:").append(tag).append(" ").append(port).
                  append(" new connection from ").append(s));
        } catch (IOException e) {
          if (!e.getMessage().equals("Socket closed")) {
            if (e.getMessage().contains("OOB")) {
              prta("" + e);
            } else {
              Util.IOErrorPrint(e, "CD11Conn: UdpCD11ConnectionServerThread: accept gave IOException!", getPrintStream());
            }
          } else {
            prta(Util.clear(runsb).append(" CD11Conn:").append(tag).append(" ").append(port).append(" server socket closed"));
          }
          if (s != null) {
            try {
              s.close();
            } catch (IOException expected) {
            }
          }
        } catch (RuntimeException e) {
          prta(Util.clear(runsb).append(tag).append("CD11Conn: RuntimeException in ").append(this.getClass().getName()).
                  append(" e=").append(e.getMessage()));
          if (getPrintStream() != null) {
            e.printStackTrace(getPrintStream());
          } else {
            e.printStackTrace();
          }
          if (e.getMessage() == null) {
            throw e;
          }
          if (e.getMessage().contains("OutOfMemory")) {
            SendEvent.edgeEvent("OutOfMemory", "Out of Memory in " + Util.getProcess() + "/" + this.getClass().getName() + " on " + IndexFile.getNode(), this);
            //throw e;
            Util.exit("Outofmem");
          }
        }
      }
    } catch (RuntimeException e) {
      prta(Util.clear(runsb).append(tag).append("CD11Conn: RuntimeException in ").append(this.getClass().getName()).
              append(" e=").append(e.getMessage()));
      if (getPrintStream() != null) {
        e.printStackTrace(getPrintStream());
      } else {
        e.printStackTrace();
      }
      if (e.getMessage() == null) {
        throw e;
      }
      if (e.getMessage().contains("OutOfMemory")) {
        SendEvent.edgeEvent("OutOfMemory", "Out of Memory in " + Util.getProcess() + "/" + this.getClass().getName() + " on " + IndexFile.getNode(), this);
        throw e;
      }
      terminate();
    }
    prta(Util.clear(runsb).append(tag).append("CD11Conn: stop all clients"));
    //synchronized (clients) {
    TLongObjectIterator<CD11LinkServer> itr = clients.iterator();
    int n = 0;
    CD11LinkServer u;
    while (itr.hasNext()) {
      itr.advance();
      u = (CD11LinkServer) itr.value();
      prta(tag + "CD11Conn: terminate u=" + u.toString());
      u.terminate();
    }
    //}
    try {
      sleep(1000);
    } catch (InterruptedException expected) {
    }
    try {
      if (ss != null) {
        ss.close();
      }
    } catch (IOException expected) {
    }
    prta(tag + "CD11Conn: RTMS force all out!");
    RawToMiniSeed.forceStale(-1);     // If there is anything left in RTMS, force it out!
    prta(tag + "CD11Conn: exiting.");
    running = false;
    terminate = false;
  }

  /**
   * This is used to isolate accept() calls for the performance analyzer.
   *
   * @return the socket from accept
   */
  private Socket accept() throws IOException {
    return ss.accept();
  }

  /**
   *
   * @return
   */
  @Override
  public StringBuilder getConsoleOutput() {
    return consolesb;
  } // Use prt and prta directly

  /**
   * Returns the monitor string for Icinga.
   *
   * @return A String representing the monitor key value pairs for this EdgeThread.
   */
  long monBytesIn;
  long lastMonBytesIn;

  @Override
  public StringBuilder getMonitorString() {
    Util.clear(monitorsb);
    synchronized (clients) {
      TLongObjectIterator<CD11LinkServer> itr = clients.iterator();
      monitorsb.append("CD11CS-NClient=").append(clients.size()).append("\n");
      monBytesIn = 0;
      while (itr.hasNext()) {
        itr.advance();
        CD11LinkServer thr = itr.value();
        if (thr.isActive()) {
          monBytesIn += CD11LinkServer.getTotalBytes();
          monitorsb.append(thr.getMonitorString());
        }
      }
    }
    long nb = monBytesIn - lastMonBytesIn;
    lastMonBytesIn = monBytesIn;
    monitorsb.insert(0, "CD11TotalBytesIn=" + nb + "\n");
    return monitorsb;
  }

  @Override
  public StringBuilder getStatusString() {
    Util.clear(statussb);
    long current = System.currentTimeMillis();
    CD11LinkServer u;
    statussb.append("CD11ConnectionServer has ").append(clients.size()).append(" subthreads port=").
            append(port).append(" #Mbytes=").append(CD11LinkServer.getTotalBytes() / 1000000).
            append(" ").append(RTMSBuf.getStatus()).append(" tid=").append(getId()).append("\n");
    synchronized (clients) {
      TLongObjectIterator<CD11LinkServer> itr = clients.iterator();
      int n = 0;
      while (itr.hasNext()) {
        itr.advance();
        u = (CD11LinkServer) itr.value();
        if (u.inState() != 99) {
          statussb.append("      ").append(u.getStatusString()).append("\n");
        }
        n++;
        //if( n % 2 == 0 && itr.hasNext()) statussb.append("\n");
        if (!u.isAlive() && u.isListening()) {
          prta("Close stale connection " + u.getTag());
          u.terminate();
          itr.remove();
        }
      }
    }
    return statussb;
  }

  /**
   * The shutdown() class for this server.
   */
  class ShutdownCD11ConnectionServer extends Thread {

    CD11ConnectionServer thr;

    public ShutdownCD11ConnectionServer(CD11ConnectionServer t) {
      thr = t;
    }

    // This is called by the RunTime.shutdown at any termination 
    @Override
    public void run() {
      prta("CD11Conn:  Shutdown() started...");
      thr.terminate();

      int nrunning = 1;
      int loops = 0;
      // close down the accept() socket
      try {
        ss.close();
        prta("CD11Conn: Shutdown() close accept socket");
      } catch (IOException expected) {
      }

      // Close down all of the CD11LinkServer threads we know about
      /*synchronized(clients) {
        Iterator itr = clients.values().iterator();
        while(itr.hasNext()) {
          CD11LinkServer q = (CD11LinkServer) itr.next();
          System.err.println("CD11Conn: shutdown() send terminate() to "+q.getTag());        
          q.terminate();
        }
      }

      try{sleep(2000L);} catch(InterruptedException expected) {}
      System.err.println("CD11Conn: In Shutdown() wait for all threads to exit");
      
      
      // wait for the threads to all exit
      while(nrunning > 0) {
        loops++;
        if(loops > 5) break;
        nrunning=0;
        String list="Threads still up : ";
        synchronized(clients) {
          Iterator itr = clients.values().iterator();
          while(itr.hasNext()) {
            CD11LinkServer q = (CD11LinkServer) itr.next();       
            if(q.isRunning() && q.isAlive()) 
            {nrunning++; list+=" "+q.getTag();if(nrunning % 5 == 0) list+="\n";}
          }
        }

        System.err.println("CD11Conn: Shutdown() waiting for "+nrunning+" threads. "+list);
        if(nrunning == 0) break;        // speed up the exit!
        try {sleep(4000L);} catch (InterruptedException expected) {}
      }*/
      System.err.println("CD11Conn: Shutdown of CD11ConnectionServer is complete.");
    }
  }

  /**
   * Reread the config file and make sure our list of LinkServers is correct. The configuration
   * lines look like :
   *
   * TAG:-p port [-b bind.addr]
   */
  private void rereadConfigFile() throws FileNotFoundException {
    try {
      try (BufferedReader in = new BufferedReader(new FileReader(configFile))) {
        String line;
        int p = 0;
        InetAddress bindaddr = bindAddress;
        String user = Util.getAccount();
        while ((line = in.readLine()) != null) {
          if (line.length() < 3) {
            continue;
          }
          if (line.charAt(0) == '#') {
            continue;
          }
          String[] tags = line.split(":");
          if (tags.length <= 1) {
            prta(Util.clear(runsb).append("CD11Conn: rereadConfig() bad line=").append(line));
            continue;
          }
          synchronized (clients) {
            CD11LinkServer srv = clients.get(Util.getHashFromSeedname(tags[0].trim()));
            if (srv == null) {
              prta(Util.clear(runsb).append("rereadConfig(): has a new station ").append(tags[0]).
                      append(" bind=").append(bindaddr).append(" ").append(tags[1]).
                      append(" client=").append(clients.size()));
              srv = new CD11LinkServer(tags[0].trim(), Util.clear(runsb).append(tags[0]), Util.clear(tmpdest).append("0"), tags[1]);
              clients.put(Util.getHashFromSeedname(tags[0].trim()), srv);
            }
          }
        }
      }
    } catch (IOException e) {
      e.printStackTrace(getPrintStream());
      Util.IOErrorPrint(e, "Reading config file=" + configFile, getPrintStream());
      if (e.getMessage().contains("Too many open")) {
        SendEvent.edgeEvent("TooManyOpn", "Too many open file reported in CD11. rereadconfig()", this);
        Util.exit(103);
      }

    }
  }

  /**
   * CD11CommandServerServer.java - This server looks for a telnet type connection, accepts a
   * message of the form yyyy,doy,node\n and forms up a FORCELOADIT! block for the EdgeBlockQueue.
   * This triggers the opening of the file in Replicator and causes the file to be checked.
   *
   *
   * @author davidketchum
   */
  private final class CD11CommandServer extends Thread {

    int port;
    ServerSocket d;
    int totmsgs;
    boolean terminate;
    boolean running;

    public boolean isRunning() {
      return running;
    }

    public void terminate() {
      terminate = true;
      interrupt();
      if (d != null) {
        try {
          d.close();
        } catch (IOException expected) {
        }
      }
    }   // cause the termination to begin

    public int getNumberOfMessages() {
      return totmsgs;
    }

    /**
     * Creates a new instance of a CD11CommandServerServer.
     *
     * @param porti The port that will be used for this service ( its normally 7960
     * AnssPorts.EDGEMOM_FORCE_CHECK_PORT).
     */
    public CD11CommandServer(int porti) {
      port = porti;
      terminate = false;
      // Register our shutdown thread with the Runtime system.
      Runtime.getRuntime().addShutdownHook(new ShutdownCD11CommandServer(this));
      running = true;
      start();
    }

    @Override
    public String toString() {
      return "CD11Cmd: " + port + " d=" + d + " port=" + port + " totmsgs=" + totmsgs;
    }

    @Override
    public void run() {
      boolean dbg = false;
      long now;
      StringBuilder sb = new StringBuilder(10000);
      byte[] bf = new byte[512];
      byte[] sbbuf = new byte[1000];
      // Open up a port to listen for new connections.
      while (true) {
        try {
          //server = s;
          if (terminate) {
            break;
          }
          prta(Util.clear(tmpsb).append(tag).append(" CD11Cmd: ").append(port).append(" Open Port=").append(port));
          d = new ServerSocket(port);
          break;
        } catch (SocketException e) {
          if (e.getMessage().contains("Address already in use")) {
            prta(Util.clear(tmpsb).append(tag).append(" CD11Cmd: ").append(port).append(" Address in use - exit "));
            return;
          } else {
            prta(Util.clear(tmpsb).append(tag).append(" CD11Cmd: ").append(port).
                    append(" Error opening TCP listen port =").append(port).append("-").
                    append(e.getMessage()));
            try {
              Thread.sleep(2000);
            } catch (InterruptedException E) {
            }
          }
        } catch (IOException e) {
          prta(Util.clear(tmpsb).append(tag).append(" CD11Cmd: ").append(port).append("ERror opening socket server=").append(e.getMessage()));
          try {
            Thread.sleep(2000);
          } catch (InterruptedException E) {
          }
        }
      }

      String help = "Usage : clear|resetAck,ARRAY\n   clear - clear TCP/IP connection\n"
              + "   resetAck - clear TCP/IP connection and ack frameset\nAdditional commands:\nlist - print status list\n"
              + "exit, quit - terminate connection to CD11CommandServer\nhelp - print this message\n";
      while (true) {
        if (terminate) {
          break;
        }
        try {
          prta(Util.clear(tmpsb).append(tag).append(" CD11Cmd: ").append(port).append(" at accept"));
          Socket s = d.accept();
          prta(Util.clear(tmpsb).append(tag).append(" CD11Cmd: ").append(port).append(" from ").append(s));
          try {
            OutputStream out = s.getOutputStream();
            out.write(help.getBytes());

            BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream()));
            String line;
            OUTER:
            while ((line = in.readLine()) != null) {
              if (line.length() < 2) {
                break;
              }
              line = line.replaceAll("  ", " ");
              String[] parts = line.split("[,\\s]");
              for (int i = 0; i < parts.length; i++) {
                prta(Util.clear(tmpsb).append("CMD: ").append(i).append(" ").append(parts[i]));
              }
              switch (parts.length) {
                case 1:
                  if (parts[0].equalsIgnoreCase("HELP") || parts[0].equals("?")) {
                    out.write(help.getBytes());
                  } else if (parts[0].equalsIgnoreCase("EXIT") || parts[0].equalsIgnoreCase("QUIT")) {
                    out.write("Exiting\n".getBytes());
                    break OUTER;
                  } else if (parts[0].equalsIgnoreCase("LIST")) {
                    Util.stringBuilderToBuf(getStatusString(), sbbuf);
                    out.write(sbbuf);
                  } else {
                    out.write(("CMD: Unknown Command!\n" + help).getBytes());
                  }
                  break;
                case 2:
                  if (parts[0].equalsIgnoreCase("clear")) {
                    CD11LinkServer link = clients.get(Util.getHashFromSeedname(parts[1].toUpperCase()));
                    if (link != null) {
                      link.clearConnection();
                    } else {
                      out.write("ARRAY or STATION not found\n".getBytes());
                    }
                  } else if (parts[0].equalsIgnoreCase("resetack")) {
                    CD11LinkServer link = clients.get(Util.getHashFromSeedname(parts[1].toUpperCase()));
                    if (link != null) {
                      link.clearAck();
                    } else {
                      out.write("ARRAY or STATION not found\n".getBytes());
                    }
                  } else {
                    out.write(("Unknown Command!\n" + help).getBytes());
                  }
                  break;
                default:
                  out.write(help.getBytes());
                  break;
              }
            }
          } catch (IOException e) {
            Util.SocketIOErrorPrint(e, "CD11Cmd:" + port + " IOError on socket");
          } catch (RuntimeException e) {
            prt(Util.clear(tmpsb).append(tag).append(" CD11Cmd:").append(port).
                    append(" RuntimeException in EBC CD11Cmd e=").append(e).append(" ").
                    append(e == null ? "" : e.getMessage()));
            if (e != null) {
              e.printStackTrace(getPrintStream());
            }
          }
          prta(Util.clear(tmpsb).append(tag).append(" CD11Cmd:").append(port).
                  append(" CD11CommandServerHandler has exit read loop EOF on s=").append(s));
          if (s != null) {
            if (!s.isClosed()) {
              try {
                s.close();
              } catch (IOException e) {
                prta(Util.clear(tmpsb).append(tag).append(" CD11Cmd:").append(port).
                        append(" IOError closing socket"));
              }
            }
          }
        } catch (IOException e) {
          if (e.getMessage().contains("operation interrupt")) {
            prt(Util.clear(tmpsb).append(tag).append(" CD11Cmd:").append(port).
                    append(" interrupted.  continue terminate=").append(terminate));
            continue;
          }
          if (terminate) {
            break;
          }
        }
      }       // end of infinite loop (while(true))
      //prt("Exiting CD11CommandServerServers run()!! should never happen!****\n");
      prt(tag + " CD11Cmd: " + port + " read loop terminated");
      running = false;
    }

    private class ShutdownCD11CommandServer extends Thread {

      CD11CommandServer thr;

      public ShutdownCD11CommandServer(CD11CommandServer t) {
        thr = t;
      }

      /**
       * This is called by the Runtime.shutdown during the shutdown sequence. It causes all cleanup
       * actions to occur.
       */
      @Override
      public void run() {
        thr.terminate();
        prta(tag + " CD11Cmd: " + port + "Shutdown Done.");
      }
    }
  }

}
