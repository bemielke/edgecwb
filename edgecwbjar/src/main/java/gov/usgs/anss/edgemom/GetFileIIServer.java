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
import java.net.ServerSocket;
import java.net.UnknownHostException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.GregorianCalendar;
import java.util.Iterator;
import java.util.TreeMap;

/**
 * This class listens to a known port for connections which are supposed to
 * receive mini-seed formatted data blockettes. Once the port is opened, an
 * accept is kept open at all times which will cause a GetFileIISocket to be
 * created to actually process data on the connection. This listener launches
 * GetFileIISockets on each connection to actually move the data. The parameters
 * are passed to the GetFileIISocket.
 * <p>
 * The termination is a little different as the holder may ask this thread to
 * terminate, which causes a inner class ShutdownGetFileIIServer to be created.
 * This inner class will start the termination on all of the connections and
 * then wait for all of them to be shutdown.
 * <p>
 * The spawned GetFileSockets implement a strategy to detect continuous data
 * (RBF) and suppress insertion of triggered and user request data (TRG -USR) if
 * it continuous data that is coming in. This avoids duplication of data by
 * having the trigger move the Hydra picker forward in time before the
 * continuous data comes in. The side effect is data is only available after the
 * delay for acquiring continuous data.
 * <br>
 * <PRE>
 * switch			arg      Description
 * -p					pppp     The port to listen to (default=5009).
 * -bind			nn.nn.nn The IP address of DNS translatable server name to bind to this port.
 * -dbg								 Allow debug output.
 * -noudpchan					 Do not send summary of data to UdpChannel for ChannelDisplay.
 * -nohydra						 Disable output to Hydra (normally used on test systems to avoid duplications).
 * -allow4k						 Allow 4k packets to go to database, default is break 4k into 512s.
 * -mswait		nnnnn    Millisecond wait between each block (normally zero).
 * -path			path/    File goes in this directory.
 * </PRE>
 *
 * @author davidketchum
 */
public final class GetFileIIServer extends EdgeThread {

  private final TreeMap<String, GetFileIISocket> thr = new TreeMap<>();
  private ServerSocket ss;                // The socket we are "listening" to for new connections
  private GregorianCalendar lastStatus;
  private int port;
  private String host;              // The local bind host name
  private boolean dbg;
  private ChannelSender csend;
  private final ShutdownGetFileIIServer shutdown;
  private boolean allow4k;
  private boolean hydra;          // Send received MS to hydra
  private int mswait;
  private String path = "./";

  @Override
  public long getBytesIn() {
    long sum = 0;
    synchronized (thr) {
      Iterator<GetFileIISocket> itr = thr.values().iterator();
      while (itr.hasNext()) {
        sum += itr.next().getBytesIn();
      }
    }
    return sum;
  }

  @Override
  public long getBytesOut() {
    long sum = 0;
    synchronized (thr) {
      Iterator<GetFileIISocket> itr = thr.values().iterator();
      while (itr.hasNext()) {
        sum += itr.next().getBytesOut();
      }
    }
    return sum;
  }

  /**
   * Creates a new instance of GFServer.
   *
   * @param argline The EdgeThread args.
   * @param tg The EdgeThread tag.
   */
  public GetFileIIServer(String argline, String tg) {
    super(argline, tg);
    String[] args = argline.split("\\s");
    dbg = false;
    host = "";
    running = false;
    boolean nocsend = false;
    port = 5009;
    hydra = true;
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
      } else if (args[i].equals("-mswait")) {
        mswait = Integer.parseInt(args[i + 1]);
        i++;
      } else if (args[i].equals("-path")) {
        path = args[i + 1];
        i++;
      } else if (args[i].substring(0, 1).equals(">")) {
        break;
      } else {
        prt("GFS: unknown switch=" + args[i] + " ln=" + argline);
      }
    }
    try {
      prta("GFS: create server on host=" + host + " port=" + port + " hydra=" + hydra
              + " noudpchan=" + nocsend + " allow4k=" + allow4k + " mswait=" + mswait + " path=" + path);

      EdgeMom.setNoConsole(false);
      // If we are limiting bandwidth-in by waits, open ServerSocket with low input buffer size
      if (mswait != 0) {
        ss = new ServerSocket();
        ss.setReceiveBufferSize(4096);        // This must be changed before the bind for it to work
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
      lastStatus = new GregorianCalendar();   // Set initial time
      start();
    } catch (UnknownHostException e) {
      prta("Got unknown host exception for host=" + host + " args=" + argline);
      running = false;
    } catch (IOException e) {
      Util.IOErrorPrint(e, "GFServer : ***** Cannot set up socket server on port " + port);
      //Util.exit(10);
    }
    if (!nocsend) {
      csend = new ChannelSender("  ", "GFServer", "MSS-" + tg);
    }
    shutdown = new ShutdownGetFileIIServer();
    Runtime.getRuntime().addShutdownHook(shutdown);

  }

  /**
   * This Thread does accepts() on the port and creates a new GetFileIISocket
   * class object to handle all i/o to the connection.
   */
  @Override
  public void run() {
    running = true;
    prta(Util.asctime() + " GFServer : start accept loop.  I am "
            + ss.getInetAddress() + "-" + ss.getLocalSocketAddress() + "|" + ss);
    while (true) {
      prta(Util.asctime() + " GFServer: call accept()" + getName());
      try {
        Socket s = ss.accept();
        if (terminate) {
          break;
        }
        GetFileIISocket mss = new GetFileIISocket(s, this, tag, csend, hydra, allow4k, mswait, path);
        prta(Util.asctime() + " GFServer: new socket=" + s + " at client=" + thr.size() + " new is " + mss.getName());
        synchronized (thr) {
          thr.put(mss.getName(), mss);
          mss.setDebug(dbg);

          // Check the list of open sockets for ones that have gone dead and remove them
          Iterator itr = thr.values().iterator();
          int n = 0;
          while (itr.hasNext()) {
            mss = (GetFileIISocket) itr.next();
            n++;
            if (!mss.isAlive() && !mss.isRunning()) {
              prta(mss.getTag() + " has died.  Remove it");
              itr.remove();
            }
          }
        }
      } catch (IOException e) {
        if (terminate && e.getMessage().equalsIgnoreCase("Socket closed")) {
          prta("GFServer: accept socket closed during termination");
        } else {
          Util.IOErrorPrint(e, "GFServer: accept gave unusual IOException!");
        }
      }
      if (terminate) {
        break;
      }
    }
    prt("GetFileIIServer has been stopped");
    if (csend != null) {
      csend.close();
    }
    running = false;
    terminate = false;
  }
  /**
   * Return the monitor string for Icinga
   *
   * @return A String representing the monitor key value pairs for this
   * EdgeThread
   */
  long lastMonBytes;

  @Override
  public StringBuilder getMonitorString() {
    if (monitorsb.length() > 0) {
      monitorsb.delete(0, monitorsb.length());
    }
    long bi = getBytesIn();
    long nb = bi - lastMonBytes;
    lastMonBytes = bi;
    Iterator<GetFileIISocket> itr = thr.values().iterator();
    while (itr.hasNext()) {
      monitorsb.append(itr.next().getMonitorString());
    }
    monitorsb.insert(0, "GFSTotalBytesIn=" + nb + "\nGFSNThreads=" + thr.size() + "\n");
    return monitorsb;
  }

  /**
   * Since this server might have many GetFileIISocket threads, its
   * getStatusString() returns one line for each of the children.
   *
   * @return String with one line per child MSS
   */
  @Override
  public StringBuilder getStatusString() {
    if (statussb.length() > 0) {
      statussb.delete(0, statussb.length());
    }
    synchronized (thr) {
      Iterator itr = thr.values().iterator();
      statussb.append("GetFileIIServer has ").append(thr.size()).append(" sockets open  port=").append(port).append("\n");
      while (itr.hasNext()) {
        statussb.append("      ").append(((GetFileIISocket) itr.next()).getStatusString()).append("\n");
      }
    }
    return statussb;

  }

  @Override
  public StringBuilder getConsoleOutput() {
    return consolesb;
  }

  /**
   * We need to terminate all of the children threads before we can be exited
   */
  @Override
  public void terminate() {
    terminate = true;
    if (ss != null) {
      try {
        ss.close();
      } catch (IOException e) {
      }
    }
  }

  public class ShutdownGetFileIIServer extends Thread {

    public ShutdownGetFileIIServer() {
      gov.usgs.anss.util.Util.prta("new ThreadShutdown " + getName() + " " + getClass().getSimpleName());
    }

    @Override
    public void run() {
      Iterator itr = thr.values().iterator();
      terminate = true;
      while (itr.hasNext()) {
        ((GetFileIISocket) itr.next()).terminate();
      }
      boolean done = false;
      // Wait for done
      while (!done) {
        try {
          sleep(500);
        } catch (InterruptedException e) {
        }
        itr = thr.values().iterator();
        done = true;

        // If any sub-thread is alive on is running, do not finish shutdown yet
        while (itr.hasNext()) {
          GetFileIISocket mss = (GetFileIISocket) itr.next();
          if (mss.isRunning() || mss.isAlive()) {
            done = false;
          }
        }
      }

      // Set terminate, so main server thread is ready to terminate and close its socket
      // as to force it to sense its termination
      prta("Shutdown GetFileIIServer is complete.");
      try {
        ss.close();
      } catch (IOException e) {
        prta("Shutdown GetFileIIServer socket close caused IOException");
        Util.IOErrorPrint(e, "Shutdown GetFileIIServer socket close caused IOException");
      }
    }
  }

  static public void main(String[] args) {
    IndexFile.init();
    EdgeMom.prt("GetFileIIServer startup");
    EdgeMom.setNoConsole(false);
    GetFileIIServer mss = new GetFileIIServer("-bind localhost -p 4001", "Gfs");
  }
}
