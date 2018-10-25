/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.alarm;

import gov.usgs.anss.edgemom.EdgeChannelServer;
import gov.usgs.anss.util.Util;
import gov.usgs.edge.alarm.Event;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.GregorianCalendar;

/**
 * EventMonitorServer.java - This class is a Server Template which dumps a line
 * for each active event/node to the caller.
 *
 * Created on January 20, 2008, 4:12 PM
 */
public final class EventMonitorServer extends Thread {

  private final int port;
  private ServerSocket d;
  private int totmsgs;
  private boolean terminate;

  public void terminate() {
    terminate = true;
    interrupt();
  }   // cause the termination to begin

  public int getNumberOfMessages() {
    return totmsgs;
  }

  /**
   * Creates a new instance of EventMonitorServers
   *
   * @param porti Port to use for this service
   */
  public EventMonitorServer(int porti) {
    port = porti;
    terminate = false;
    // Register our shutdown thread with the Runtime system.
    Runtime.getRuntime().addShutdownHook(new ShutdownEventMonitor());

    start();
  }

  @Override
  public void run() {
    boolean dbg = false;
    GregorianCalendar now;
    StringBuilder sb = new StringBuilder(10000);

    // Open up a port to listen for new connections.
    while (true) {
      try {
        //server = s;
        if (terminate) {
          break;
        }
        Util.prt(Util.asctime() + " EMON: Open Port=" + port);
        d = new ServerSocket(port);
        break;
      } catch (SocketException e) {
        if (e.getMessage().equals("EMON:Address already in use")) {
          try {
            Util.prt("EMON: Address in use - try again.");
            Thread.sleep(2000);
          } catch (InterruptedException Expected) {
          }
        } else {
          Util.prt("EMON:Error opening TCP listen port =" + port + "-" + e.getMessage());
          try {
            Thread.sleep(2000);
          } catch (InterruptedException Expected) {
          }
        }
      } catch (IOException e) {
        Util.SocketIOErrorPrint(e, "EMON:Eror opening socket server");
        try {
          Thread.sleep(2000);
        } catch (InterruptedException Expected) {
        }
      }
    }

    while (true) {
      if (terminate) {
        break;
      }
      try {
        Socket s = d.accept();
        Util.prt("EMON: from " + s);
        for (int i = 0; i < EdgeChannelServer.getDBAccess().getEventSize(); i++) {
          Event event = EdgeChannelServer.getDBAccess().getEvent(i);
          if (event != null) {
            String txt = event.getMonitorText();
            if (txt.length() > 0) {
              Util.prt(txt);
              s.getOutputStream().write(txt.getBytes());
            }
          }
        }
        try {
          s.close();
        } catch (IOException expected) {
        }
      } catch (IOException e) {
        Util.prt("EMON:receive through IO exception");
      }
    }       // end of infinite loop (while(true))
    //Util.prt("Exiting EventMonitorServers run()!! should never happen!****\n");
    Util.prt("EMON:read loop terminated");
  }

  private final class ShutdownEventMonitor extends Thread {

    public ShutdownEventMonitor() {

    }

    /**
     * this is called by the Runtime.shutdown during the shutdown sequence cause
     * all cleanup actions to occur
     */
    @Override
    public void run() {
      terminate = true;
      interrupt();
      Util.prta("EMON: Shutdown started");
      int nloop = 0;

      Util.prta("EMON:Shutdown Done.");

    }
  }
}
