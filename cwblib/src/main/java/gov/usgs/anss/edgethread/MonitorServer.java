/*
 *  This software is in the public domain because it contains materials 
 *  that originally came from the United States Geological Survey, 
 *  an agency of the United States Department of Interior. For more 
 *  information, see the official USGS copyright policy at 
 *  http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.edgethread;

import gov.usgs.alarm.SendEvent;
import gov.usgs.anss.util.Util;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;

/**
 * This server sends a string of Key-Value pairs with monitoring information on the process. These
 * are generally used by ICINGA or other monitoring software to track the state of this EdgeMom
 * instance.
 *
 * @author davidketchum
 */
public final class MonitorServer extends Thread {

  private final int port;
  private ServerSocket d;
  private int totmsgs;
  private boolean terminate;
  private final EdgeThread parent;
  private byte[] buf = new byte[1000];

  public void terminate() {
    terminate = true;
    interrupt();
    try {
      if (d != null && !d.isClosed()) {
        d.close();
      }
    } // cause the termination to begin
    catch (IOException e) {
    }
  }

  public int getNumberOfMessages() {
    return totmsgs;
  }

  /**
   * Creates a new instance of EdgeStatusServers
   *
   * @param porti The port to listen to
   * @param parent The EdgeThread to ask for its Monitor String when this port is hit
   */
  public MonitorServer(int porti, EdgeThread parent) {
    port = porti;
    this.parent = parent;
    terminate = false;
    // Register our shutdown thread with the Runtime system.
    Runtime.getRuntime().addShutdownHook(new ShutdownMonitorServer(this));
    gov.usgs.anss.util.Util.prta("new ThreadMonitor " + getName() + " " + getClass().getSimpleName() + " par=" + parent.getClass().getSimpleName());
    setDaemon(true);
    start();
  }

  @Override
  public void run() {
    boolean dbg = false;
    StringBuilder sb = new StringBuilder(10000);

    // OPen up a port to listen for new connections.
    while (true) {
      try {
        //server = s;
        if (terminate) {
          break;
        }
        parent.prt(Util.asctime() + " MonitorServer: Open Port=" + port);
        d = new ServerSocket(port);
        break;
      } catch (SocketException e) {
        if (e.getMessage().equals("Address already in use")) {
          try {
            parent.prt("MonitorServer: Address in use - try again.port=" + port);
            SendEvent.edgeSMEEvent("MonSrvDup", "Duplicate edgemom port=" + port, this);
            sleep(60000);
            //port -=100;

          } catch (InterruptedException E) {

          }
        } else {
          parent.prt("MonitorServer:Error opening TCP listen port =" + port + "-" + e.getMessage());
          try {
            sleep(60000);

          } catch (InterruptedException E) {

          }
        }
      } catch (IOException e) {
        parent.prt("MonitorServer:Error opening socket server=" + e.getMessage());
        try {
          sleep(2000);

        } catch (InterruptedException E) {

        }
      }
    }

    while (true) {
      if (terminate) {
        break;
      }
      try {
        try ( // Each connection (accept) format up the key-value pairs and then close the socket
                Socket s = accept()) {
          if (terminate) {
            if (!s.isClosed()) {
              s.close();
            }
            break;
          }
          //prta("MonitorServer: from "+s);

          totmsgs++;
          StringBuilder ss = parent.getMonitorString();
          if (ss == null) {
            s.getOutputStream().write("MonitorNotYetAvailable=1\n".getBytes());
          } else if (ss.length() <= 0) {
            s.getOutputStream().write("MonitorNotYetAvailable=1\n".getBytes());
          } else {
            if (ss.length() > buf.length) {
              buf = new byte[ss.length() * 2];
            }
            Util.stringBuilderToBuf(ss, buf);
            s.getOutputStream().write(buf, 0, ss.length());
          }
          parent.prta("MonitorServer: wrote " + sb.length() + " #msg=" + totmsgs);
        }
        //prta("MonitorServer: from "+s);
      } catch (IOException e) {
        if (!terminate) {
          Util.SocketIOErrorPrint(e, "MonitorServer: during accept or dump back");
        }
      }
    }       // end of infinite loop (while(true))
    //parent.prt("Exiting EdgeStatusServers run()!! should never happen!****\n");
    try {
      if (d != null) {
        if (!d.isClosed()) {
          d.close();
        }
      }
    } catch (IOException e) {
    }
    parent.prt("MonitorServer:read loop terminated");
  }

  public Socket accept() throws IOException {
    return d.accept();
  }

  private class ShutdownMonitorServer extends Thread {

    MonitorServer thr;

    public ShutdownMonitorServer(MonitorServer t) {
      thr = t;
      parent.prta("new ThreadShutdown " + getName() + " " + getClass().getSimpleName());
    }

    /**
     * this is called by the Runtime.shutdown during the shutdown sequence cause all cleanup actions
     * to occur
     */

    @Override
    public void run() {
      thr.terminate();
      parent.prta("EdgeMomMonitorServer: Shutdown started for " + parent.toString());
    }
  }
}
