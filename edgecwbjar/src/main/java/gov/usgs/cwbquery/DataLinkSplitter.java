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
import java.io.FileReader;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.TreeMap;

/**
 * DataLinkSplitter - This class implements enough of the DataLink protocol
 * provided by a RingServer from IRIS to accept connections from clients
 * speaking that protocol. In QueryMom's case this is data from a
 * OutputInfrastructure RingServerSeedLink thread. Any such client should be
 * able to connection to this and insert miniSEED data. Normally a "sendto" is
 * defined to send some channel set to the QueryMom using a RingServerSeedLink
 * on one or more EdgeMoms. The RingServerSeedLinks connect to this server which
 * creates a DataLinkSplitterSocket for each connection. The handler thread
 * reads the data (RAW or MiniSEED) and attempts to send it to the
 * QuerySpanCollection and/or MiniSeedCollection which puts the data in the
 * memory based structures within QueryMom.
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
 * -p      port      Set the port number for the listener (def=16098)
 * -dbg              Turn on more debugging output
 *
 * </PRE>
 *
 * @author davidketchum
 */
public final class DataLinkSplitter extends EdgeThread {

  private final static TreeMap<String, DataLinkSplitterSocket> thr = new TreeMap<>();
  private static DataLinkSplitter theSplitter;
  private ServerSocket ss;                // the socket we are "listening" for new connections
  //private GregorianCalendar lastStatus;
  private int port;
  private String host;              // The local bind host name
  private boolean dbg;
  private int mswait;               // this is always zero here, I left it in should a bandwidth limit be needed
  private final DataLinkSplitter.ShutdownDataLinkServer shutdown;
  private String iplist;          // if set this file contains a list of permitted IP addresses
  // Queue related 
  private static int qsize = 100;
  private static int nextin = 0;
  private static final ArrayList<byte[]> queue = new ArrayList<>(100);
  private static int[] qlength;

  public static void write(byte[] bufhdr, int hdrlen, byte[] payload, int plen) {
    if (theSplitter == null) {
      return;
    }
    theSplitter.writeBufs(bufhdr, hdrlen, payload, plen);
  }

  public void writeBufs(byte[] bufhdr, int hdrlen, byte[] payload, int plen) {
    // Queue the data buffer
    synchronized (queue) {
      int next = (nextin + 1) % qsize;
      System.arraycopy(bufhdr, 0, queue.get(next), 0, hdrlen);
      System.arraycopy(payload, 0, queue.get(next), hdrlen, plen);
      nextin = next;
    }
    if (dbg) {
      prta(tag + " addQueue " + hdrlen + " buflen=" + plen + " next=" + nextin);
    }
  }

  @Override
  public String toString() {
    return "DLSP: port=" + port + " #sock=" + thr.size() + " isRunning="
            + isRunning() + " alive=" + isAlive() + " dbg=" + dbg;
  }

  @Override
  public long getBytesIn() {
    Iterator<DataLinkSplitterSocket> itr = thr.values().iterator();
    long sum = 0;
    while (itr.hasNext()) {
      sum += itr.next().getBytesIn();
    }
    return sum;
  }

  @Override
  public long getBytesOut() {
    Iterator<DataLinkSplitterSocket> itr = thr.values().iterator();
    long sum = 0;
    while (itr.hasNext()) {
      sum += itr.next().getBytesOut();
    }
    return sum;
  }

  /**
   * Creates a new instance of DLQServer
   *
   * @param argline The EdgeThread args.
   * @param tg The EdgeThread tag
   */
  public DataLinkSplitter(String argline, String tg) {
    super(argline, tg);
    String[] args = argline.split("\\s");
    dbg = false;
    host = "";
    running = false;
    port = 16098;
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
      } else if (args[i].equals("-qsize")) {
        qsize = Integer.parseInt(args[i + 1]);
        i++;
      } else if (args[i].equals("-empty")) ; else if (args[i].substring(0, 1).equals(">")) {
        break;
      } else {
        prt(tag + "DLSP: unknown switch=" + args[i] + " ln=" + argline);
      }
    }
    // create the queue
    for (int i = 0; i < qsize; i++) {
      queue.add(new byte[512]);
    }
    qlength = new int[qsize];
    nextin = qsize - 1;

    this.tag = tag + ":" + host + "/" + port;
    try {
      prta(tag + "DLSP: create server on bind host=" + host + " port=" + port + " iplist=" + iplist);
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
    shutdown = new DataLinkSplitter.ShutdownDataLinkServer();
    Runtime.getRuntime().addShutdownHook(shutdown);
    theSplitter = (DataLinkSplitter) this;

  }

  /**
   * This Thread does accepts() on the port an creates a new
   * DataLinkSplitterSocket class object to handle all i/o to the connection.
   */
  @Override
  public void run() {
    running = true;
    StringBuilder sb = new StringBuilder(100);      // Build up status in run()
    String line;
    //servers.put(""+port, this);
    prta(tag + " DLSP: start accept loop.  I am "
            + ss.getInetAddress() + "-" + ss.getLocalSocketAddress() + "|" + ss);
    while (true) {
      prta(tag + " DLSP: call accept()" + getName() + " #connections=" + thr.size());
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
        DataLinkSplitterSocket mss = new DataLinkSplitterSocket(s, this, tag, mswait);
        prta(tag + " DLSP: new socket=" + s + " at client=" + thr.size() + " new is " + mss.getName());
        thr.put(mss.getTag(), mss);
        mss.setDebug(dbg);

        // check the list of open sockets for ones that have gone dead, remove them
        Iterator itr = thr.values().iterator();
        int n = 0;
        while (itr.hasNext()) {
          mss = (DataLinkSplitterSocket) itr.next();
          n++;
          if (!mss.isAlive() && !mss.isRunning()) {
            prta(mss.getTag() + " has died.  Remove it");
            itr.remove();
          }
        }
      } catch (IOException e) {
        if (terminate && e.getMessage().equalsIgnoreCase("Socket closed")) {
          prta(tag + "DLSP: accept socket closed during termination");
        } else {
          Util.IOErrorPrint(e, "DLSP: accept gave unusual IOException!");
          prta(tag + "DLSP: e=" + e);
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
    prta(tag + "DataLinkSplitter stop children");
    try {
      sleep(1000);
    } catch (InterruptedException expected) {
    }  // give a bit more time for data to flush
    while (itr.hasNext()) {
      ((DataLinkSplitterSocket) itr.next()).terminate();
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
        DataLinkSplitterSocket mss = (DataLinkSplitterSocket) itr.next();
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
    Iterator<DataLinkSplitterSocket> itr = thr.values().iterator();
    while (itr.hasNext()) {
      monitorsb.append(itr.next().getMonitorString());
    }
    monitorsb.insert(0, "DLLTotalBytesIn=" + nb + "\nNThreads=" + thr.size() + "\n");
    return monitorsb;
  }

  /**
   * Since this server might have many DataLinkSplitterSocket threads, its
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
      statussb.append("      ").append(((DataLinkSplitterSocket) itr.next()).getStatusString()).append("\n");
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

  class DataLinkSplitterSocket extends Thread {

    Socket s;
    String tag;
    int mswait;
    DataLinkSplitter par;
    int nextout;
    boolean running;
    long bytesIn;
    long bytesOut;
    boolean dbg;
    StringBuilder sbmonitor = new StringBuilder(50);

    public long getBytesOut() {
      return bytesOut;
    }

    public long getBytesIn() {
      return bytesIn;
    }

    public String getTag() {
      return tag;
    }

    public void setDebug(boolean t) {
      dbg = t;
    }

    public boolean isRunning() {
      return running;
    }

    public void terminate() {
      terminate = true;
      if (s != null) {
        if (!s.isClosed()) {
          try {
            s.close();
          } catch (IOException expected) {
          }
        }
      }
    }

    public StringBuilder getMonitorString() {
      Util.clear(sbmonitor).append(tag).append(" #out=").append(bytesOut).append(" nextout=").append(nextout);
      return sbmonitor;
    }

    public StringBuilder getStatusString() {
      Util.clear(sbmonitor).append(tag).append(" #out=").append(bytesOut).append(" nextout=").append(nextout);
      return sbmonitor;
    }

    public DataLinkSplitterSocket(Socket s, DataLinkSplitter splitter, String tag, int mswait) {
      this.s = s;
      par = splitter;
      this.mswait = mswait;
      nextout = nextin;     // Start with next thing to come in
      this.tag = tag + ":" + s.getInetAddress().toString().substring(1);
      setDaemon(true);
    }

    @Override
    public void run() {
      running = true;
      try {
        while (!terminate) {
          if (nextout != nextin) {
            int next = (nextout + 1) % qsize;
            s.getOutputStream().write(queue.get(next), 0, qlength[next]);
            nextout = next;
            bytesOut += qlength[next];
          }
        }
      } catch (IOException e) {
        prta(tag + " shutdown on IOError e=" + e);
      }
      running = false;
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

  static public void main(String[] args) {
    IndexFile.init();
    EdgeMom.prt("DataLinkServer startup");
    EdgeMom.setNoConsole(false);
    DataLinkSplitter mss = new DataLinkSplitter("-bind " + Util.getLocalHostIP() + " -p 4001", "Mss");
  }
}
