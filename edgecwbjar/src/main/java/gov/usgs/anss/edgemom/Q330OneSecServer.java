/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.edgemom;

import gov.usgs.anss.edge.IndexFile;
import gov.usgs.anss.edgethread.EdgeThread;
import gov.usgs.anss.util.AnssPorts;
import gov.usgs.anss.util.Util;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.GregorianCalendar;
import java.util.Iterator;
import java.util.TreeMap;

/**
 * This class listens to a known port for connections which are supposed to
 * receive Q330 one second datablocks. Once the port is opened, an accept is
 * kept open at all times which will cause a Q330OneSecSocket to be created to
 * actually process data on the connection. The one second data is normally only
 * forwarded to Hydra since the anssq330 MiniSeed output is put into the edge
 * data base. This should be configured somewhere for every EdgeMom instance
 * running a Q330Manager configured to send 1 second packets.
 * <p>
 * Note: This class forwards one second data to Hydra. It depends on there being
 * a Hydra thread running, though it does not use it directly.
 * <p>
 * The termination is a little different as the holder may ask this thread to
 * terminate which causes a inner class ShutdownQ330OneSecServer to be created.
 * This inner class will start the termination on all of the connections and
 * then wait for all of them to be shutdown
 * <br>
 * NOTE:  This code only works with the 32 bit version of ANSSQ330 as the 64 bit changes
 * the 1 second packet.
 * <br>
 * <PRE>
 * switch   arg      Description
 * -p       pppp     The port to listen to.
 * -bind    nn.nn.nn The IP address of DNS translatable server name to bind to this port.
 * -dbg              Allow debug output from this and all children sockets
 * -nohydra          Disable output to Hydra (normally used on test systems to avoid duplications).
 * </PRE>
 *
 * @author davidketchum
 */
public final class Q330OneSecServer extends EdgeThread {

  TreeMap<String, Q330OneSecSocket> thr;

  ServerSocket ss;                // The socket we are "listening" to for new connections
  GregorianCalendar lastStatus;
  int port;
  String host;              // The local bind host name
  boolean dbg;
  boolean hydra;          // Send received MS to hydra

  @Override
  public String toString() {
    return host + "/" + port;
  }

  ShutdownQ330OneSecServer shutdown;

  /**
   * Creates a new instance of Q330SecServ.
   *
   * @param argline The EdgeThread arguments.
   * @param tg The EdgeThread tag.
   */
  public Q330OneSecServer(String argline, String tg) {
    super(argline, tg);
    thr = new TreeMap<>();
    String[] args = argline.split("\\s");
    dbg = false;
    host = "";
    running = false;
    hydra = true;
    port = AnssPorts.EDGEMOM_Q330_1SEC_SERVER;
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
      } else if (args[i].equals("-nohydra")) {
        hydra = false;
      } else if (args[i].substring(0, 1).equals(">")) {
        break;
      } else {
        prt("MSS: unknown switch=" + args[i] + " ln=" + argline);
      }
    }
    try { //HydraOutputer.setCommandLine(argline);
      prta("MSS: create server on host=" + host + " port=" + port + " dbg=" + dbg);
      EdgeMom.setNoConsole(false);
      if (host.equals("")) {
        ss = new ServerSocket(port);            // Create the listener
      } else {
        ss = new ServerSocket(port, 6, InetAddress.getByName(host));
      }
      lastStatus = new GregorianCalendar();   // Set the initial time
      start();
    } catch (UnknownHostException e) {
      prta("Got unknown host exception for host=" + host + " args=" + argline);
      running = false;
    } catch (IOException e) {
      Util.IOErrorPrint(e, "Q330SecServ : Cannot set up socket server on port " + port);
      Util.exit(10);
    }
    shutdown = new ShutdownQ330OneSecServer();
    Runtime.getRuntime().addShutdownHook(shutdown);
  }

  /**
   * This Thread does accepts() on the port and creates a new Q330OneSecSocket
   * class object to handle all of the i/o to the connection.
   */
  @Override
  public void run() {
    running = true;
    prta(Util.asctime() + " Q330SecServ : start accept loop.  I am "
            + ss.getInetAddress() + "-" + ss.getLocalSocketAddress() + "|" + ss);
    while (true) {
      prta(Util.asctime() + " Q330SecServ: call accept()");
      try {
        Socket s = accept();
        if (terminate) {
          break;
        }
        prta(Util.asctime() + " Q330SecServ: new socket=" + s + " at client=" + thr.size() + " dbg=" + dbg);
        Q330OneSecSocket mss = new Q330OneSecSocket(s, this, tag, hydra);
        mss.setDebug(dbg);
        thr.put(mss.getName(), mss);

        // Check the list of open sockets for ones that have gone dead and remove them
        Iterator itr = thr.values().iterator();
        int n = 0;
        while (itr.hasNext()) {
          mss = (Q330OneSecSocket) itr.next();
          n++;
          if (!mss.isAlive() && !mss.isRunning()) {
            prta(mss.getTag() + " has died.  Remove it");
            itr.remove();
          }
        }
      } catch (IOException e) {
        Util.SocketIOErrorPrint(e, "Q330SecServ: accept socket");
      }
      if (terminate) {
        break;
      }
    }
    prt("Q330OneSecServer has been stopped");
    running = false;
    terminate = false;
  }

  private Socket accept() throws IOException {
    prta("Q330OneSecServer in accept()");
    return ss.accept();
  }

  /**
   * Return the monitor string for Icinga.
   *
   * @return A String representing the monitor key value pairs for this
   * EdgeThread.
   */
  @Override
  public StringBuilder getMonitorString() {
    if (monitorsb.length() > 0) {
      monitorsb.delete(0, monitorsb.length());
    }
    return monitorsb.append("Q330OneSecNThreads=").append(thr.size()).append("\n");
  }

  /**
   * Since this server might have many Q330OneSecSocket threads, its
   * getStatusString() returns one line for each of the children.
   *
   * @return String with one line per child MSS.
   */
  @Override
  public StringBuilder getStatusString() {
    if (statussb.length() > 0) {
      statussb.delete(0, statussb.length());
    }
    Iterator itr = thr.values().iterator();
    boolean do24 = false;
    statussb.append("Q330OneSecServer has ").append(thr.size()).append(" sockets open  port=").append(port).append("\n");
    if ((System.currentTimeMillis() % 86400000L) < 600000) {
      do24 = true;
    }
    while (itr.hasNext()) {
      Q330OneSecSocket q = (Q330OneSecSocket) itr.next();
      statussb.append("      ").append(q.getStatusString()).append("\n");
      if (do24) {
        statussb.append("         ").append(q.getStatusString24()).append("\n");
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
    prt("Q330OneSecServer: terminate called");
    try {
      ss.close();
    } catch (IOException expected) {
    }
    //ShutdownQ330OneSecServer shutdown= new ShutdownQ330OneSecServer();
  }

  public class ShutdownQ330OneSecServer extends Thread {

    public ShutdownQ330OneSecServer() {
      gov.usgs.anss.util.Util.prta("new ThreadShutdown " + getName() + " " + getClass().getSimpleName());
    }

    @Override
    public void run() {
      terminate();

      Iterator itr = thr.values().iterator();
      prta("Q330OneSecServer: Shutdown - call terminate on all sockets");
      try {
        sleep(5000);
      } catch (InterruptedException expected) {
      }
      while (itr.hasNext()) {
        ((Q330OneSecSocket) itr.next()).terminate();
      }
      boolean done = false;
      // Wait for this to be done
      int nup = 0;
      while (!done) {
        try {
          sleep(1000);
        } catch (InterruptedException expected) {
        }
        itr = thr.values().iterator();
        done = true;
        // If any sub-thread is alive on is running, do not finish shutdown yet
        while (itr.hasNext()) {
          Q330OneSecSocket mss = (Q330OneSecSocket) itr.next();
          if (mss.isRunning() || mss.isAlive()) {
            done = false;
            nup++;
          }
        }
        prta("Q330OneSecServer: wait for all children to die! " + thr.values().size() + " nup=" + nup);
      }

      // Set terminate so main server thread is ready to terminate and close its socket
      // to force it to sense its termination
      prta("Shutdown Q330OneSecServer is complete.");
      terminate = true;
      try {
        ss.close();
      } catch (IOException e) {
        prta("Shutdown Q330OneSecServer socket close caused IOException");
        Util.IOErrorPrint(e, "Shutdown Q330OneSecServer socket close caused IOException");
      }
    }
  }

  static public void main(String[] args) {
    IndexFile.init();
    EdgeMom.prt("Q330OneSecServer startup");
    EdgeMom.setNoConsole(false);
    Q330OneSecServer mss = new Q330OneSecServer("-bind " + Util.getLocalHostIP() + " -p 4001", "Mss");
  }
}
