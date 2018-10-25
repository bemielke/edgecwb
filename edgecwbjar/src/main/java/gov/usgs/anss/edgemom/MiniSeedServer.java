/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.edgemom;

import gov.usgs.anss.edge.ChannelSender;
import gov.usgs.anss.edge.IndexFile;
import gov.usgs.anss.edgethread.EdgeThread;
import gov.usgs.anss.util.Util;
import java.io.IOException;
//import java.net.*;
import java.net.Socket;
import java.net.UnknownHostException;
import java.net.InetSocketAddress;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.util.Iterator;
import java.util.TreeMap;

/**
 * This class listens to a known port for connections which are suppose to
 * receive mini-seed formatted data blocks. Once the port is opened, an accept
 * is kept open at all times which will cause a MiniSeedSocket to be created to
 * actually process data on the connection. This listener launches
 * MiniSeedSockets on each connection to actually move the data. The parameters
 * are passed to the MiniSeedSocket.
 * <p>
 * The termination is a little different as the holder may ask this thread to
 * terminate, which causes a inner class ShutdownMiniSeedServer to be created.
 * This inner class will start the termination on all of the connections and
 * then wait for all of them to be shutdown.
 * <p>
 * Usage: This class is used for re-requested data from the balers, or for
 * msread -edge mode to insert data into the edge or CWB, and by the Q330Manager
 * class to create places for Q330 data from anssq330 to go.
 * <br>
 * <PRE>
 * switch			arg      Description
 * -p					pppp     The port to listen to.
 * -bind			nn.nn.nn The IP address of the DNS translatable server name to bind to this port.
 * -dbg								 Allow debug output.
 * -noudpchan					 Do not send summary of data to UdpChannel for ChannelDisplay.
 * -nohydra						 Disable output to Hydra (normally used on test systems to avoid duplications).
 * -allow4k						 Allow 4k packets to go to database, default is to break 4k into 512s.
 * -mswait		nnnnn    Millisecond wait between each block (normally zero).
 * -q330							 If set, do Q330 checking to eliminate bad blocks.
 * -scn								 If set, strip any location code before loading.
 * </PRE>
 *
 * @author davidketchum
 */
public final class MiniSeedServer extends EdgeThread {

  private TreeMap<String, MiniSeedSocket> thr;
  private static final TreeMap<String, MiniSeedServer> servers = new TreeMap<>();
  private ServerSocket ss;                // The socket we are "listening" for new connections
  //private GregorianCalendar lastStatus;
  private int port;
  private String host;              // The local bind host name
  private boolean dbg;
  private ChannelSender csend;
  private final ShutdownMiniSeedServer shutdown;
  private boolean allow4k;
  private boolean hydra;          // Send received MS to hydra
  private int mswait;
  private boolean scn;
  private boolean q330check;
  private final StringBuilder tmpsb = new StringBuilder(100);

  public static MiniSeedServer getMiniSeedServer(int port) {
    return servers.get("" + port);
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
      sb.append("MSS: port=").append(port).append(" #sock=").append(thr.size()).
              append(" isRunning=").append(isRunning()).append(" alive=").append(isAlive()).
              append(" 4k=").append(allow4k).append(" dbg=").append(dbg).append(" wait=").append(mswait);
    }
    return sb;
  }

  @Override
  public long getBytesIn() {
    Iterator<MiniSeedSocket> itr = thr.values().iterator();
    long sum = 0;
    while (itr.hasNext()) {
      sum += itr.next().getBytesIn();
    }
    return sum;
  }

  @Override
  public long getBytesOut() {
    Iterator<MiniSeedSocket> itr = thr.values().iterator();
    long sum = 0;
    while (itr.hasNext()) {
      sum += itr.next().getBytesOut();
    }
    return sum;
  }

  /**
   * Creates a new instance of MSServer..
   *
   * @param argline The EdgeThread args.
   * @param tg The EdgeThread tag.
   */
  public MiniSeedServer(String argline, String tg) {
    super(argline, tg);
    thr = new TreeMap<>();
    String[] args = argline.split("\\s");
    dbg = false;
    host = "";
    running = false;
    boolean nocsend = false;
    hydra = true;
    q330check = false;
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
      } else if (args[i].equals("-noudpchan")) {
        nocsend = true;
      } else if (args[i].equals("-nohydra")) {
        hydra = false;
      } else if (args[i].equals("-empty")) ; else if (args[i].equals("-allow4k")) {
        allow4k = true;
      } else if (args[i].equals("-q330")) {
        q330check = true;
      } else if (args[i].equals("-mswait")) {
        mswait = Integer.parseInt(args[i + 1]);
        i++;
      } else if (args[i].equals("-scn")) {
        scn = true;
      } else if (args[i].substring(0, 1).equals(">")) {
        break;
      } else {
        prt(tag + "MSS: unknown switch=" + args[i] + " ln=" + argline);
      }
    }
    try {
      prta(Util.clear(tmpsb).append(tag).append("MSS: create server on host=").append(host).
              append(" port=").append(port).append(" hydra=").append(hydra).
              append(" noudpchan=").append(nocsend).append(" allow4k=").append(allow4k).
              append(" mswait=").append(mswait).append(" q330=").append(q330check));
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
      } // Else use the default buffer size
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
      Util.IOErrorPrint(e, "MSServer : ***** Cannot set up socket server on port " + port);
      //Util.exit(10);
    }
    if (!nocsend) {
      csend = new ChannelSender("  ", "MSServer", "MSS-" + tg);
    }
    shutdown = new ShutdownMiniSeedServer();
    Runtime.getRuntime().addShutdownHook(shutdown);
  }

  /**
   * This Thread does accepts() on the port and creates a new MiniSeedSocket
   * class object to handle all i/o to the connection.
   */
  @Override
  public void run() {
    running = true;
    servers.put("" + port, this);
    StringBuilder runsb = new StringBuilder(50);
    prta(Util.clear(runsb).append(tag).append(" MSServer : start accept loop.  I am ").
            append(ss.getInetAddress()).append("-").append(ss.getLocalSocketAddress()).append("|").append(ss));
    while (true) {
      prta(Util.clear(runsb).append(tag).append(" MSServer: call accept()").append(getName()));
      try {
        Socket s = ss.accept();
        if (terminate) {
          break;
        }
        MiniSeedSocket mss = new MiniSeedSocket(s, this, tag, csend, hydra, allow4k, mswait, q330check, scn);
        prta(Util.clear(runsb).append(tag).append(" MSServer: new socket=").append(s).
                append(" at client=").append(thr.size()).append(" new is ").append(mss.getName()));
        thr.put(mss.getName(), mss);
        mss.setDebug(dbg);

        // Check the list of open sockets for ones that have gone dead and remove them
        Iterator itr = thr.values().iterator();
        int n = 0;
        while (itr.hasNext()) {
          mss = (MiniSeedSocket) itr.next();
          n++;
          if (!mss.isAlive() && !mss.isRunning()) {
            prta(Util.clear(runsb).append(mss.getTag()).append(" has died.  Remove it"));
            itr.remove();
          }
        }
      } catch (IOException e) {
        if (terminate && e.getMessage().equalsIgnoreCase("Socket closed")) {
          prta(Util.clear(runsb).append(tag).append("MSServer: accept socket closed during termination"));
        } else {
          Util.IOErrorPrint(e, "MSServer: accept gave unusual IOException!");
          prta(Util.clear(runsb).append(tag).append("MSServer: e=").append(e));
        }
      }
      if (terminate) {
        break;
      }
    }
    if (csend != null) {
      csend.close();
    }
    running = false;
    terminate = false;
    if (!EdgeMom.isShuttingDown()) {
      Runtime.getRuntime().removeShutdownHook(shutdown);
    }
    prta(Util.clear(runsb).append(tag).append("MiniSeedServer has been stopped isRunning()=").append(isRunning()));
  }
  /**
   * Return the monitor string for Icinga.
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
    Iterator<MiniSeedSocket> itr = thr.values().iterator();
    while (itr.hasNext()) {
      monitorsb.append(itr.next().getMonitorString());
    }
    monitorsb.insert(0, "MSSTotalBytesIn=" + nb + "\n");
    return monitorsb;
  }

  /**
   * Since this server might have many MiniSeedSocket threads, its
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
    int loop = 0;
    statussb.append("MiniSeedServer has ").append(thr.size()).append(" sockets open  port=").append(port).append("\n");
    if (thr.size() > 0) {
      while (itr.hasNext()) {
        statussb.append("      ").append(loop++).append(" ").
                append(((MiniSeedSocket) itr.next()).getStatusString()).append("\n");
      }
    }
    return statussb;
  }

  @Override
  public StringBuilder getConsoleOutput() {
    return consolesb;
  }

  /**
   * We need to terminate all of the children threads before we can be exited.
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

  public final class ShutdownMiniSeedServer extends Thread {

    public ShutdownMiniSeedServer() {
      gov.usgs.anss.util.Util.prta("new ThreadShutdown " + getName() + " " + getClass().getSimpleName());
    }

    @Override
    public void run() {
      Iterator itr = thr.values().iterator();
      terminate = true;
      prta(tag + "Shutdown MiniSeedServer started");
      try {
        sleep(1000);
      } catch (InterruptedException expected) {
      }  // Give more time for data to flush
      while (itr.hasNext()) {
        ((MiniSeedSocket) itr.next()).terminate();
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

        // If any sub-thread is alive on is running, do not finish shutdown yet
        while (itr.hasNext()) {
          MiniSeedSocket mss = (MiniSeedSocket) itr.next();
          if (mss.isRunning() || mss.isAlive()) {
            done = false;
          }
        }
        if (loop++ > 30) {
          prta(tag + " ** not all sockets closed!");
          break;
        }
      }

      // Set terminate so main server thread is ready to terminate and close its socket
      // to force it to sense its termination
      prta(tag + "Shutdown MiniSeedServer is complete.");
      try {
        if (ss != null) {
          ss.close();
        }
      } catch (IOException e) {
        prta(tag + "Shutdown MiniSeedServer socket close caused IOException");
        Util.IOErrorPrint(e, "Shutdown MiniSeedServer socket close caused IOException");
      }
    }
  }

  static public void main(String[] args) {
    IndexFile.init();
    EdgeMom.prt("MiniSeedServer startup");
    EdgeMom.setNoConsole(false);
    MiniSeedServer mss = new MiniSeedServer("-bind " + Util.getLocalHostIP() + " -p 4001", "Mss");
  }
}
