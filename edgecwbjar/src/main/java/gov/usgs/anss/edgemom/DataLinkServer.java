/*
 *  This software is in the public domain because it contains materials 
 *  that originally came from the United States Geological Survey, 
 *  an agency of the United States Department of Interior. For more 
 *  information, see the official USGS copyright policy at 
 *  http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.edgemom;

import gov.usgs.alarm.SendEvent;
import gov.usgs.anss.edge.ChannelSender;
import gov.usgs.anss.edge.IndexFile;
import gov.usgs.anss.edgethread.EdgeThread;
import gov.usgs.anss.util.Util;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Iterator;
import java.util.TreeMap;

/**
 * DataLinkServer - This class implements enough of the DataLinkServer protocol
 * provided by a RingServer from IRIS to accept data from a DataLink client like
 * ORB2RINGSERVER. Any such client should be able to make a connection to this
 * and insert miniSEED data. It is normally used by orb2ringserver to insert
 * data from an Antelope orb.
 *
 * <PRE>
 * switch			Args			Description
 * -iplist		ipfile    A list of IP addresses (one IP per line) that are allowed to connect are in this file (number addresses only).
 * -bind			ip.adr    Bind the listener to this IP address.
 * -p					port      Set the port number for the listener.
 * -dbg									Turn on more debugging output
 * -noudpchan						If present, no data is sent via UDP (The data sent summarizes this channel to UdpChannel or ChannelDisplay).
 * -nohydra							Data received on this port will not be broadcast to hydra on the Edge wire.
 * -mswait		nn				Wait nn milliseconds between each packet received to limit input bandwidth (100 ms = 41kbps).
 * -scn									If true, data is SCN and needs the location codes overridden to two spaces.
 * -allow4k							If true, data received in 4k packets will be preserved as 4k data (seems unlikely Antelope has 4k Data)
 * -q330								Test each packet for known Q330 errors (nsamp not agreeing - seems unlikely to help).
 *
 * </PRE>
 *
 * @author davidketchum
 */
public final class DataLinkServer extends EdgeThread {

  private TreeMap<String, DataLinkSocket> thr;
  private static final TreeMap<String, DataLinkServer> servers = new TreeMap<>();
  private ServerSocket ss;                // The socket we are "listening" to for new connections
  private int port;
  private String host;              // The local bind host name
  private boolean dbg;
  private ChannelSender csend;
  private final DataLinkServer.ShutdownDataLinkServer shutdown;
  private boolean allow4k;
  private String iplist;          // If set, this file contains a list of permitted IP addresses.
  private boolean hydra;          // Send received MS to hydra.
  private int mswait;
  private boolean scn;
  private boolean q330check;
  private boolean rawSend;
  private double rawMaxRate = 10;

  public static DataLinkServer getDataLinkServer(int port) {
    return servers.get("" + port);
  }

  @Override
  public String toString() {
    return "DLS: port=" + port + " #sock=" + thr.size() + " isRunning="
            + isRunning() + " alive=" + isAlive() + " 4k=" + allow4k + " dbg=" + dbg + " wait=" + mswait;
  }

  @Override
  public long getBytesIn() {
    Iterator<DataLinkSocket> itr = thr.values().iterator();
    long sum = 0;
    while (itr.hasNext()) {
      sum += itr.next().getBytesIn();
    }
    return sum;
  }

  @Override
  public long getBytesOut() {
    Iterator<DataLinkSocket> itr = thr.values().iterator();
    long sum = 0;
    while (itr.hasNext()) {
      sum += itr.next().getBytesOut();
    }
    return sum;
  }

  /**
   * Creates a new instance of DataLinkServer
   *
   * @param argline The EdgeThread args
   * @param tg The EdgeThread tag
   */
  public DataLinkServer(String argline, String tg) {
    super(argline, tg);
    thr = new TreeMap<>();
    String[] args = argline.split("\\s");
    dbg = false;
    host = "";
    running = false;
    boolean nocsend = false;
    hydra = true;
    port = 16000;
    q330check = false;

    for (int i = 0; i < args.length; i++) {
      if (args[i].equals("-p")) {
        port = Integer.parseInt(args[i + 1]);
        i++;
      } else if (args[i].equals("-bind")) {
        host = args[i + 1];
        i++;
      } else if (args[i].equals("-dbg")) {
        dbg = true;
      } else if (args[i].equals("-iplist")) {
        iplist = args[i + 1];
        i++;
      } else if (args[i].equals("-noudpchan")) {
        nocsend = true;
      } else if (args[i].equals("-nohydra")) {
        hydra = false;
      } else if (args[i].equals("-empty")) ; 
      else if (args[i].equals("-allow4k")) {
        allow4k = true;
      } else if (args[i].equals("-q330")) {
        q330check = true;
      } else if (args[i].equals("-mswait")) {
        mswait = Integer.parseInt(args[i + 1]);
        i++;
      } else if (args[i].equals("-scn")) {
        scn = true;
      } else if (args[i].equals("-rsend")) {
        rawSend = true;
        rawMaxRate = Double.parseDouble(args[i + 1]);
        i++;
      } else if (args[i].substring(0, 1).equals(">")) {
        break;
      } else {
        prt(tag + "DLS: unknown switch=" + args[i] + " ln=" + argline);
      }
    }

    try {

      prta(tag + "DLS: create server on host=" + host + " port=" + port + " hydra=" + hydra
              + " noudpchan=" + nocsend + " allow4k=" + allow4k + " mswait=" + mswait + 
              " q330=" + q330check + " iplist=" + iplist);

      EdgeMom.setNoConsole(false);
      // If we are limiting bandwidth in by waits, open ServerSocket with low input buffer size
      if (mswait != 0) {
        ss = new ServerSocket();
        ss.setReceiveBufferSize(4096);        // This must be changed before the bind to work
        InetSocketAddress adr;
        if (host.equals("")) {
          adr = new InetSocketAddress(port);
        } else {
          adr = new InetSocketAddress(host, port);
        }
        ss.bind(adr, 6);
      } // else use the default buffer size
      else {
        if (host.equals("")) {
          ss = new ServerSocket(port);
        } else {
          ss = new ServerSocket(port, 6, InetAddress.getByName(host));
        }
      }            // Create the listener
      start();
    } catch (UnknownHostException e) {
      prta(tag + "Got unknown host exception for host=" + host + " args=" + argline);
      running = false;
    } catch (IOException e) {
      Util.IOErrorPrint(e, "DLServer : ***** Cannot set up socket server on port " + port);
      //Util.exit(10);
    }
    if (!nocsend) {
      csend = new ChannelSender("  ", "DLServer", "DLS-" + tg);
    }
    shutdown = new DataLinkServer.ShutdownDataLinkServer();
    Runtime.getRuntime().addShutdownHook(shutdown);

  }

  /**
   * This Thread does accepts() on the port an creates a new DataLinkSocket
   * class object to handle all i/o to the connection.
   */
  @Override
  public void run() {
    running = true;
    StringBuilder sb = new StringBuilder(100);
    String line;
    servers.put("" + port, this);
    prta(tag + " DLServer : start accept loop.  I am "
            + ss.getInetAddress() + "-" + ss.getLocalSocketAddress() + "|" + ss);
    while (true) {
      prta(tag + " DLServer: call accept()" + getName());
      try {
        Socket s = ss.accept();
        if (terminate) {
          break;
        }
        if (iplist != null) {
          String cmp = s.getInetAddress().toString().substring(1);
          try {
            if (sb.length() > 0) {
              sb.delete(0, sb.length());
            }
            sb.append("In IP ").append(cmp).append(" : ");
            boolean found;
            try (BufferedReader ipin = new BufferedReader(new FileReader(iplist))) {
              found = false;
              while ((line = ipin.readLine()) != null) {
                sb.append(line).append(" ");
                if (line.trim().equals(cmp)) {
                  found = true;
                  break;
                }
              }
            }
            if (!found) {
              prta(tag + " *** connection for unknown IP=" + cmp + " do not allow connection" + sb);
              SendEvent.edgeSMEEvent("DLUnknIP", "Unknown IP " + cmp + " has tried to connect and is not permitted", this);
              s.close();
              continue;
            }
            else {
              prta(tag + "IP found in -iplist "+cmp);
            }
          } catch (IOException e) {
            prta(tag + " **** could not read IPLIST of configuration!  Allow connection and warn!!!!" + sb);
            SendEvent.edgeSMEEvent("DLBadConfig", "Error reading config file " + iplist + " " + e, this);
          }
        }
        DataLinkSocket mss = new DataLinkSocket(s, this, tag,
                csend, hydra, allow4k, mswait, q330check, scn, rawSend, rawMaxRate);
        prta(tag + " DLServer: new socket=" + s + " at client=" + thr.size() + " new is " + mss.getName() + " tag=" + mss.getTag());
        thr.put(mss.getTag(), mss);
        mss.setDebug(dbg);

        // check the list of open sockets for ones that have gone dead, remove them
        Iterator itr = thr.values().iterator();
        int n = 0;
        while (itr.hasNext()) {
          mss = (DataLinkSocket) itr.next();
          n++;
          if (!mss.isAlive() && !mss.isRunning()) {
            prta(mss.getTag() + " has died.  Remove it");
            itr.remove();
          }
        }
      } catch (IOException e) {
        if (terminate && e.getMessage().equalsIgnoreCase("Socket closed")) {
          prta(tag + "DLServer: accept socket closed during termination");
        } else {
          Util.IOErrorPrint(e, "DLServer: accept gave unusual IOException!");
          prta(tag + "DLServer: e=" + e);
        }
      }
      if (terminate) {
        break;
      }
    }
    cleanup();
    running = false;
    terminate = false;
    if (csend != null) {
      csend.close();
    }
    if (!EdgeMom.isShuttingDown()) {
      Runtime.getRuntime().removeShutdownHook(shutdown);
    }
    prta(tag + "DataLinkServer has been stopped isRunning()=" + isRunning());
  }

  /**
   * Does a terminate cleanup. Close any open sockets, close listener socket.
   */
  public void cleanup() {
    Iterator itr = thr.values().iterator();
    terminate = true;
    prta(tag + "DataLinkServer stop children");
    try {
      sleep(1000);
    } catch (InterruptedException expected) {
    }  // Give a bit more time for data to flush
    while (itr.hasNext()) {
      ((DataLinkSocket) itr.next()).terminate();
    }
    boolean done = false;
    // Wait for done
    int loop = 0;
    while (!done) {
      try {
        sleep(500);
      } catch (InterruptedException expected) {
      }
      itr = thr.values().iterator();
      done = true;

      // If any sub-thread is alive on is running, do  not finish shutdown yet
      while (itr.hasNext()) {
        DataLinkSocket mss = (DataLinkSocket) itr.next();
        if (mss.isRunning() || mss.isAlive()) {
          done = false;
        }
      }
      if (loop++ > 30) {
        prta(tag + " ** not all sockets closed!");
        break;
      }
    }
    try {
      if (ss != null) {
        ss.close();
      }
    } catch (IOException e) {
      prta(tag + "Shutdown DataLinkServer socket close caused IOException");
      Util.IOErrorPrint(e, "Shutdown DataLinkServer socket close caused IOException");
    }

  }
  /**
   * Returns the monitor string for Icinga.
   *
   * @return A String representing the monitor key value pairs for this
   * EdgeThread.
   */
  long lastMonBytes;
  long lastMonpublic;

  @Override
  public StringBuilder getMonitorString() {
    if (monitorsb.length() > 0) {
      monitorsb.delete(0, monitorsb.length());
    }
    long bi = getBytesIn();
    long nb = bi - lastMonBytes;
    lastMonBytes = bi;
    Iterator<DataLinkSocket> itr = thr.values().iterator();
    while (itr.hasNext()) {
      monitorsb.append(itr.next().getMonitorString());
    }
    monitorsb.insert(0, "DLLTotalBytesIn=" + nb + "\nNThreads=" + thr.size() + "\n");
    return monitorsb;
  }

  /**
   * Returns one line for each of the children, as this server may have many
   * DataLinkSocket threads.
   *
   * @return String with one line per child MSS
   */
  @Override
  public StringBuilder getStatusString() {
    if (statussb.length() > 0) {
      statussb.delete(0, statussb.length());
    }
    Iterator itr = thr.values().iterator();
    statussb.append("DataLinkServer has ").append(thr.size()).append(" sockets open  port=").append(port).append("\n");
    while (itr.hasNext()) {
      statussb.append("      ").append(((DataLinkSocket) itr.next()).getStatusString()).append("\n");
    }
    return statussb;

  }

  @Override
  public StringBuilder getConsoleOutput() {
    return consolesb;
  }

  /**
   * We need to terminate all of the children threads before we can exit.
   */
  @Override
  public void terminate() {
    terminate = true;
    if (ss != null) {
      try {
        ss.close();
      } catch (IOException expected) {
      }
    }
  }

  public class ShutdownDataLinkServer extends Thread {

    public ShutdownDataLinkServer() {
      gov.usgs.anss.util.Util.prta("new ThreadShutdown " + getName() + " " + getClass().getSimpleName());
    }

    @Override
    public void run() {
      cleanup();

      // Set terminate so main server thread is ready to terminate and close its socket
      // to force it to sense its termination.
      prta(tag + "Shutdown DataLinkServer is complete.");
    }
  }

  static public void main(String[] args) {
    IndexFile.init();
    EdgeMom.prt("DataLinkServer startup");
    EdgeMom.setNoConsole(false);
    DataLinkServer mss = new DataLinkServer("-bind localhost -p 4001", "Mss");
  }
}
