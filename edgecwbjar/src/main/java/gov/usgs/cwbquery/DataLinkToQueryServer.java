/*
 *  This software is in the public domain because it contains materials 
 *  that originally came from the United States Geological Survey, 
 *  an agency of the United States Department of Interior. For more 
 *  information, see the official USGS copyright policy at 
 *  http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.cwbquery;

import gov.usgs.alarm.SendEvent;
import gov.usgs.anss.edge.IndexFile;
import gov.usgs.anss.edgethread.EdgeThread;
import gov.usgs.anss.util.Util;
import gov.usgs.anss.edgemom.EdgeMom;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.FileReader;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Iterator;
import java.util.TreeMap;

/**
 * DataLinkToQueryServer - This class implements enough of the DataLink protocol
 * provided by a RingServer from IRIS to accept connections from clients
 * speaking that protocol. In QueryMom's case this is data from a
 * OutputInfrastructure RingServerSeedLink thread. Any such client should be
 * able to connection to this and insert miniSEED data and/or use the raw data
 * insertion formats. Normally a "sendto" is defined to send some channel set to
 * the QueryMom using a RingServerSeedLink on one or more EdgeMoms. The
 * RingServerSeedLinks connect to this server which creates a
 * DataLinkToQuerySocket for each connection. The handler thread reads the data
 * (RAW or MiniSEED) and attempts to send it to the QuerySpanCollection and/or
 * MiniSeedCollection which puts the data in the memory based structures within
 * QueryMom for the memory cache.
 * <p>
 * This server can be promiscuous or it an have a predefined set of IP address
 * from which it will accept connections.
 * <p>
 * This is an unfinished bandwidth limit parameter. I could not see why this
 * would be useful, but I left it incase it might be needed later.
 *
 * <PRE>
 * switch   Args     Description
 * -iplist ipfile    List of IPs that are allowed to connect to this server (number addresses only!) (def allow all)
 * -bind   ip.adr    Bind listener to this IP address (def=do not bind)
 * -p      port      Set the port number for the listener (def=16099)
 * -dbg              Turn on more debugging output
 *
 * </PRE>
 *
 * @author davidketchum
 */
public final class DataLinkToQueryServer extends EdgeThread {

  private final TreeMap<String, DataLinkToQuerySocket> thr = new TreeMap<>();
  ;
  private static final TreeMap<String, DataLinkToQueryServer> servers = new TreeMap<>();
  private ServerSocket ss;                // the socket we are "listening" for new connections
  //private GregorianCalendar lastStatus;
  private int port;
  private String host;              // The local bind host name
  private boolean dbg;
  private int mswait;               // this is always zero here, I left it in should a bandwidth limit be needed
  private final DataLinkToQueryServer.ShutdownDataLinkServer shutdown;
  private String iplist;          // if set this file contains a list of permitted IP addresses

  public static DataLinkToQueryServer getDataLinkServer(int port) {
    return servers.get("" + port);
  }
  private int splitterPort = 0;
  private int splitterQsize = 100;
  private DataLinkSplitter splitter;

  @Override
  public String toString() {
    return "DLS: port=" + port + " #sock=" + thr.size() + " isRunning="
            + isRunning() + " alive=" + isAlive() + " dbg=" + dbg;
  }

  @Override
  public long getBytesIn() {
    Iterator<DataLinkToQuerySocket> itr = thr.values().iterator();
    long sum = 0;
    while (itr.hasNext()) {
      sum += itr.next().getBytesIn();
    }
    return sum;
  }

  @Override
  public long getBytesOut() {
    Iterator<DataLinkToQuerySocket> itr = thr.values().iterator();
    long sum = 0;
    while (itr.hasNext()) {
      sum += itr.next().getBytesOut();
    }
    return sum;
  }

  /**
   * Creates a new instance of DLQServer
   *
   * @param argline The EdgeThread args
   * @param tg The EdgeThread tag
   */
  public DataLinkToQueryServer(String argline, String tg) {
    super(argline, tg);
    String[] args = argline.split("\\s");
    dbg = false;
    host = "";
    running = false;
    boolean nocsend = false;
    port = 16099;
    mswait = 0;     // This 

    for (int i = 0; i < args.length; i++) {
      //prt(i+" arg="+args[i]);
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
      } else if (args[i].equals("-split")) {
        String[] parts = args[i].split(";");
        if (parts.length > 1) {
          splitterPort = Integer.parseInt(parts[1]);
        }
        if (parts.length == 1) {
          splitterPort = 16098;
        }
        if (parts.length > 2) {
          splitterQsize = Integer.parseInt(parts[2]);
        }
      } else if (args[i].equals("-empty")) ; else if (args[i].substring(0, 1).equals(">")) {
        break;
      } else {
        prt(tag + "DLS: unknown switch=" + args[i] + " ln=" + argline);
      }
    }

    try {
      prta(tag + "DLS: create server on bind host=" + host + " port=" + port + " iplist=" + iplist);
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
      //lastStatus = new GregorianCalendar();   // Set initial time
      start();
    } catch (UnknownHostException e) {
      prta(tag + "Got unknown host exception for host=" + host + " args=" + argline);
      running = false;
    } catch (IOException e) {
      Util.IOErrorPrint(e, "DLQServer : ***** Cannot set up socket server on port " + port);
      //Util.exit(10);
    }
    shutdown = new DataLinkToQueryServer.ShutdownDataLinkServer();
    Runtime.getRuntime().addShutdownHook(shutdown);
    if (splitterPort > 0) {
      splitter = new DataLinkSplitter("-p " + splitterPort + " -qsize " + splitterQsize + " >>dlsp", "DLSP");
    }
  }

  /**
   * This Thread does accepts() on the port an creates a new
   * DataLinkToQuerySocket class object to handle all i/o to the connection.
   */
  @Override
  public void run() {
    running = true;
    StringBuilder sb = new StringBuilder(100);      // Build up status in run()
    String line;
    servers.put("" + port, this);
    prta(tag + " DLQServer: start accept loop.  I am "
            + ss.getInetAddress() + "-" + ss.getLocalSocketAddress() + "|" + ss);
    while (true) {
      prta(tag + " DLQServer: call accept()" + getName() + " #connections=" + thr.size());
      try {
        Socket s = ss.accept();
        if (terminate) {
          break;
        }
        if (iplist != null) {
          String cmp = s.getInetAddress().toString().substring(1);
          try {
            Util.clear(sb).append("In IP ").append(cmp).append(" : ");
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
          } catch (IOException e) {
            prta(tag + " **** could not read IPLIST of configuration!  Allow connection and warn!!!!" + sb);
            SendEvent.edgeSMEEvent("DLBadConfig", "Error reading config file " + iplist + " " + e, this);
          }
        }
        DataLinkToQuerySocket mss = new DataLinkToQuerySocket(s, this, tag, mswait);
        prta(tag + " DLQServer: new socket=" + s + " at client=" + thr.size() + " new is " + mss.getName());
        thr.put(mss.getTag(), mss);
        mss.setDebug(dbg);

        // check the list of open sockets for ones that have gone dead, remove them
        Iterator itr = thr.values().iterator();
        int n = 0;
        while (itr.hasNext()) {
          mss = (DataLinkToQuerySocket) itr.next();
          n++;
          if (!mss.isAlive() && !mss.isRunning()) {
            prta(mss.getTag() + " has died.  Remove it");
            itr.remove();
          }
        }

      } catch (IOException e) {
        if (terminate && e.getMessage().equalsIgnoreCase("Socket closed")) {
          prta(tag + "DLQServer: accept socket closed during termination");
        } else {
          Util.IOErrorPrint(e, "DLQServer: accept gave unusual IOException!");
          prta(tag + "DLQServer: e=" + e);
        }
      }
      if (terminate) {
        break;
      }
    }
    cleanup();
    running = false;
    terminate = false;
    if (!EdgeMom.isShuttingDown()) {
      Runtime.getRuntime().removeShutdownHook(shutdown);
    }
    prta(tag + "DataLinkServer has been stopped isRunning()=" + isRunning());
  }

  /**
   * do terminate cleanup. Close any open sockets, close listener socket
   */
  public void cleanup() {
    Iterator itr = thr.values().iterator();
    terminate = true;
    prta(tag + "DataLinkServer stop children");
    try {
      sleep(1000);
    } catch (InterruptedException expected) {
    }  // give a bit more time for data to flush
    while (itr.hasNext()) {
      ((DataLinkToQuerySocket) itr.next()).terminate();
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
        DataLinkToQuerySocket mss = (DataLinkToQuerySocket) itr.next();
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
   * return the monitor string for Nagios
   *
   * @return A String representing the monitor key value pairs for this
   * EdgeThread
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
    Iterator<DataLinkToQuerySocket> itr = thr.values().iterator();
    while (itr.hasNext()) {
      monitorsb.append(itr.next().getMonitorString());
    }
    monitorsb.insert(0, "DLLTotalBytesIn=" + nb + "\nNThreads=" + thr.size() + "\n");
    return monitorsb;
  }

  /**
   * Since this server might have many DataLinkToQuerySocket threads, its
   * getStatusString() returns one line for each of the children
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
      statussb.append("      ").append(((DataLinkToQuerySocket) itr.next()).getStatusString()).append("\n");
    }
    return statussb;

  }

  @Override
  public StringBuilder getConsoleOutput() {
    return consolesb;
  }

  /**
   * we need to terminate all of the children threads before we can be exited
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

      // set terminate so main server thread is ready to terminate and close its socket
      // to force it to sense its termination
      prta(tag + "Shutdown DataLinkServer is complete.");
    }
  }

  static public void main(String[] args) {
    IndexFile.init();
    EdgeMom.prt("DataLinkServer startup");
    EdgeMom.setNoConsole(false);
    DataLinkToQueryServer mss = new DataLinkToQueryServer("-bind " + Util.getLocalHostIP() + " -p 4001", "Mss");
  }
}
