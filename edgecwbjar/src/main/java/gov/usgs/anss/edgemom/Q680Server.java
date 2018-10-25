/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.edgemom;

import gov.usgs.alarm.SendEvent;
import gov.usgs.anss.edge.ChannelSender;
import gov.usgs.anss.edge.IndexFile;
import gov.usgs.anss.edge.RawToMiniSeed;
import gov.usgs.anss.edgethread.EdgeThread;
import gov.usgs.anss.util.Util;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Iterator;
import java.util.TreeMap;

/**
 * This class listens to a known port for connections which are suppose to
 * receive 1 second formated packets from a Legacy 680 or 730 running front1sec.
 * Since the Q680 and Q730 are not in use, this is likely obsolete.
 *
 * Once the port is openned, an accept is kept open at all times which will
 * cause a Q680Socket to be created to actually process data on the connection.
 * This listener launches Q680Sockets on each connection to actually move the
 * data. The parameters are passed to the Q680Socket.
 * <p>
 * The termination is a little different as the holder may ask this thread to
 * terminate which causes a inner class ShutdownQ680Server to be created. This
 * inner class will start the termination on all of the connections and then
 * wait for all of them to be shutdown.
 * <p>
 *
 * <br>
 * <PRE>
 * switch   arg      Description
 * -p       pppp     The port to listen to
 * -bind    nn.nn.nn The IP address of DNS translatable server name to bind to this port
 * -dbg              Allow debug output
 * -noudpchan        Do not send summary of data to UdpChannel for ChannelDisplay
 * -nohydra          Disable output to Hydra (normally used on test systems to avoid duplications)
 * </PRE>
 *
 * @author davidketchum
 */
public final class Q680Server extends EdgeThread {

  private TreeMap<String, Q680Socket> thr;
  private static Q680Manager manager;
  private ServerSocket ss;                // the socket we are "listening" for new connections
  private long lastStatus;
  private int port;
  private String host;              // The local bind host name
  private String orgArgline;
  private boolean dbg;
  private ChannelSender csend;
  private final ShutdownQ680Server shutdown;
  private boolean hydra;          // send received MS to hydra

  @Override
  public long getBytesIn() {
    Iterator<Q680Socket> itr = thr.values().iterator();
    long sum = 0;
    while (itr.hasNext()) {
      sum += itr.next().getBytesIn();
    }
    return sum;
  }

  @Override
  public long getBytesOut() {
    Iterator<Q680Socket> itr = thr.values().iterator();
    long sum = 0;
    while (itr.hasNext()) {
      sum += itr.next().getBytesOut();
    }
    return sum;
  }

  /**
   * Creates a new instance of MSServer
   *
   * @param argline The EdgeThread args
   * @param tg The EdgeThread tag
   */
  public Q680Server(String argline, String tg) {
    super(argline, tg);
    thr = new TreeMap<>();
    String[] args = argline.split("\\s");
    orgArgline = argline;
    dbg = false;
    host = "";
    running = false;
    port = 2004;
    boolean nocsend = false;
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
      } else if (args[i].equals("-rtmsdbg")) {
        RawToMiniSeed.setDebugChannel(args[i + 1]);
        i++;
      } else if (args[i].equals("-noudpchan")) {
        nocsend = true;
      } else if (args[i].equals("-nohydra")) {
        hydra = false;
      } else if (args[i].equals("-dbgdata")) {
        Q680Socket.setDbgDataStation(args[i + 1]);
        i++;
      } else if (args[i].equals("-empty")) ; else if (args[i].substring(0, 1).equals(">")) {
        break;
      } else {
        prt("Q680S: unknown switch=" + args[i] + " ln=" + argline);
      }
    }
    if (manager == null) {
      manager = new Q680Manager("-empty", "Q680M");
    }
    try {
      prta("Q680S: create server on host=" + host + " port=" + port + " hydra=" + hydra + " noudpchan=" + nocsend);
      if (host.equals("")) {
        ss = new ServerSocket(port);            // Create the listener
      } else {
        ss = new ServerSocket(port, 6, InetAddress.getByName(host));
      }
      lastStatus = System.currentTimeMillis();   // Set initial time
      start();
    } catch (UnknownHostException e) {
      prta("Got unknown host exception for host=" + host + " args=" + argline);
      running = false;
    } catch (IOException e) {
      Util.IOErrorPrint(e, "Q680S: ***** Cannot set up socket server on port " + port);
      //System.exit(10);
    }
    if (!nocsend) {
      csend = new ChannelSender("  ", "Q680Server", "Q680-" + tg);
    }
    shutdown = new ShutdownQ680Server();
    Runtime.getRuntime().addShutdownHook(shutdown);

  }

  private Socket accept() throws IOException {
    return ss.accept();
  }

  /**
   * This Thread does accepts() on the port an creates a new Q680Socket class
   * object to handle all i/o to the connection.
   */
  @Override
  public void run() {
    running = true;
    try {
      sleep(2000);
    } catch (InterruptedException e) {
    }
    prta(Util.asctime() + " Q680S: start accept loop.  I am "
            + ss.getInetAddress() + "-" + ss.getLocalSocketAddress() + "|" + ss);
    while (true) {
      prta(Util.asctime() + " Q680S: call accept() " + tag + " " + getName());
      try {
        Socket s = accept();
        prta(Util.asctime() + " Q680S: accept=" + s.getInetAddress() + "/" + s.getPort()
                + " to " + s.getLocalAddress() + "/" + s.getLocalPort());
        if (terminate) {
          break;
        }
        BufferedReader in = new BufferedReader(new StringReader(manager.getConfig().toString()));
        String line;
        String incomingIP = s.getInetAddress().toString().substring(1);
        Q680Socket q = null;
        while ((line = in.readLine()) != null) {
          if (line.length() < 3) {
            continue;
          }
          if (line.substring(0, 1).equals("#") || line.substring(0, 1).equals("!")) {
            continue;
          }
          line = line.replaceAll("  ", " ");
          line = line.replaceAll("  ", " ");
          line = line.replaceAll("  ", " ");
          String[] parts = line.split(":");
          String ipadr = parts[1].substring(3).trim();
          if (ipadr.indexOf(" ") > 0) {
            ipadr = ipadr.substring(0, ipadr.indexOf(" ")).trim();
          }
          if (ipadr.equals(incomingIP)) {
            q = thr.get(parts[0]);
            if (q == null) {
              q = new Q680Socket(parts[1] + " " + orgArgline + parts[0].toLowerCase(), parts[0], this);
              thr.put(parts[0], q);
              q.setDebug(dbg);
              prta(Util.asctime() + " Q680S: new socket=" + s.getInetAddress() + "/" + s.getPort()
                      + " to " + s.getLocalAddress() + "/" + s.getLocalPort()
                      + " at client=" + thr.size() + " new is " + q.getName());
            } else {
              q.setArgs(parts[1] + " " + orgArgline);
              prta(Util.asctime() + " Q680S: reuse socket=" + s.getInetAddress() + "/" + s.getPort()
                      + " to " + s.getLocalAddress() + "/" + s.getLocalPort() + " at client=" + thr.size() + " new is " + q.getName());
            }
            q.newSocket(s);
            break;
          }
        }
        if (q == null) {
          prta("Q680S: Socket connection from unknown IP to Q680 ip=" + incomingIP);
          SendEvent.debugSMEEvent("Q680Uknown", "Q680Connection from an unknown IP=" + incomingIP, this);
          s.close();
        }

      } catch (IOException e) {
        if (terminate && e.getMessage().equalsIgnoreCase("Socket closed")) {
          prta("Q680S: accept socket closed during termination");
        } else {
          Util.IOErrorPrint(e, "Q680S: accept gave unusual IOException!");
        }
      }
      if (terminate) {
        break;
      }
    }
    prt("Q680Server has been stopped");
    Iterator itr = thr.values().iterator();
    while (itr.hasNext()) {
      ((Q680Socket) itr.next()).terminate();
    }
    running = false;
    if (csend != null) {
      csend.close();
    }
    terminate = false;
    running = false;
  }
  /**
   * return the monitor string for Nagios
   *
   * @return A String representing the monitor key value pairs for this
   * EdgeThread
   */
  long bytesMonIn;

  @Override
  public StringBuilder getMonitorString() {
    if (monitorsb.length() > 0) {
      monitorsb.delete(0, monitorsb.length());
    }
    long bi = getBytesIn();
    long nb = bi - bytesMonIn;
    bytesMonIn = bi;
    return monitorsb.append("Q680NThreads=").append(thr.size()).append("\nQ680NBytesIn=").append(nb).append("\n");
  }

  /**
   * Since this server might have many Q680Socket threads, its getStatusString()
   * returns one line for each of the children
   *
   * @return String with one line per child MSS
   */
  @Override
  public StringBuilder getStatusString() {
    if (statussb.length() > 0) {
      statussb.delete(0, statussb.length());
    }
    Iterator itr = thr.values().iterator();
    statussb.append("Q680Server has ").append(thr.size()).append(" sockets open  port=").append(port).append("\n");
    while (itr.hasNext()) {
      statussb.append("      ").append(((Q680Socket) itr.next()).getStatusString()).append("\n");
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
      } catch (IOException e) {
      }
    }
  }

  public class ShutdownQ680Server extends Thread {

    public ShutdownQ680Server() {
      gov.usgs.anss.util.Util.prta("new ThreadShutdown " + getName() + " " + getClass().getSimpleName());
    }

    @Override
    public void run() {
      Iterator itr = thr.values().iterator();
      prta("Shutdown Q680Server started - iterate on Q680Sockets");
      while (itr.hasNext()) {
        ((Q680Socket) itr.next()).terminate();
      }
      boolean done = false;
      int loop = 0;
      // Wait for done
      while (!done) {
        try {
          sleep(500);
        } catch (InterruptedException e) {
        }
        itr = thr.values().iterator();
        done = true;

        // If any sub-thread is alive on is running, do  not finish shutdown yet
        while (itr.hasNext()) {
          Q680Socket mss = (Q680Socket) itr.next();
          if (mss.isRunning() || mss.isAlive()) {
            done = false;
            prta("ShutQ680Server : sock=" + mss.toString() + " is still up. " + loop);
          }
        }
        loop++;
        if (loop % 30 == 0) {
          break;
        }
      }
      terminate = true;

      // set terminate so main server thread is ready to terminate and close its socket
      // to force it to sense its termination
      try {
        ss.close();
      } catch (IOException e) {
        prta("Shutdown Q680Server socket close caused IOException");
        Util.IOErrorPrint(e, "Shutdown Q680Server socket close caused IOException");
      }
      prta("Shutdown Q680Server is complete.");
    }
  }

  static public void main(String[] args) {
    IndexFile.init();
    gov.usgs.anss.edgemom.EdgeMom.prt("Q680Server startup");
    gov.usgs.anss.edgemom.EdgeMom.setNoConsole(false);
    Q680Server mss = new Q680Server("-dbg -noudpchan -nohydra", "Q680");
  }
}
