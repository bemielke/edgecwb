/*
 *  This software is in the public domain because it contains materials 
 *  that originally came from the United States Geological Survey, 
 *  an agency of the United States Department of Interior. For more 
 *  information, see the official USGS copyright policy at 
 *  http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.alarm;

import gov.usgs.anss.db.DBConnectionThread;
import gov.usgs.anss.net.UdpQueuedServer;
import gov.usgs.anss.util.Util;
import gov.usgs.anss.edgethread.EdgeThread;
import java.net.UnknownHostException;
import java.net.DatagramPacket;
import java.util.ArrayList;
import java.net.InetAddress;
import java.sql.*;
import java.io.IOException;

/**
 * This thread sends a UdpPacket from bound port for Alarm, to port 6334
 * (default) at a remote site. This is to insure and "stateful" UDP firewalls
 * have a packet from the inside. Packets are sent every couple of minutes all
 * edge configured stations to make sure the incoming Alarm packets are
 * permitted from the stations.
 *
 * @author U.S. Geological Survey <ketchum at usgs.gov>
 */
public final class UDPHeartBeat extends Thread {

  private boolean terminate;
  private int nmsg;
  //private UdpQueuedServer udpin;
  private final UdpEventProcess udp;
  private DBConnectionThread dbconnedge;
  private final EdgeThread par;
  private final int port = 6334;
  private final byte[] buf = new byte[140];
  private final DatagramPacket dp = new DatagramPacket(buf, 140);

  @Override
  public String toString() {
    return " #msg=" + nmsg;
  }

  //HeartBeatSender sender;
  private void prt(String s) {
    if (par == null) {
      Util.prt(s);
    } else {
      par.prt(s);
    }
  }

  private void prta(String s) {
    if (par == null) {
      Util.prta(s);
    } else {
      par.prta(s);
    }
  }

  public UDPHeartBeat(UdpEventProcess udp, EdgeThread par) {
    this.udp = udp;
    this.par = par;
    if (!DBConnectionThread.noDB) {
      if (dbconnedge == null) {
        dbconnedge = DBConnectionThread.getThread("edge"); // Note we should get the one from Alarm here!
      }
      if (dbconnedge == null) {
        try {
          dbconnedge = new DBConnectionThread(Util.getProperty("DBServer"), "readonly",
                  "edge", false, false, "edge", par.getPrintStream());
          if (!dbconnedge.waitForConnection()) {
            if (!dbconnedge.waitForConnection()) {
              if (!dbconnedge.waitForConnection()) {
                if (!dbconnedge.waitForConnection()) {
                  prta("***** could not connect edge database");
                }
              }
            }
          }
        } catch (InstantiationException e) {
          prta("Impossible Instantiation problem");
          e.printStackTrace(par.getPrintStream());
        }
      }
    }
    setDaemon(true);
    start();
  }

  @Override
  public void run() {
    Statement stmt = null;
    ArrayList<String> ipadrs = new ArrayList<>();
    String ip = null;
    String station = null;
    dp.setPort(port);
    UdpQueuedServer udpin = udp.getUdpQueuedServer();

    // This is the infinite loops
    while (true) {
      while (stmt == null) {
        try {
          // wait for a new packet, put it in buf
          stmt = dbconnedge.getNewStatement(false);
        } catch (SQLException e) {
          try {
            if (stmt != null) {
              stmt.close();
            }
          } catch (SQLException e2) {
          }
          stmt = null;
          try {
            sleep(30000);
          } catch (InterruptedException e2) {
          }
        }
      }
      try {
        try ( // the result set gives IP and station for everon
                ResultSet rs = stmt.executeQuery(
                        "SELECT gsnstation,ipadr FROM gsnstation WHERE longhaulprocotol='Edge' or longhaulprotocol='EdgeGeo' ORDER BY tcpstation")) {
          ipadrs.clear();
          while (rs.next()) {
            try {
              station = rs.getString(1);
              ip = Util.cleanIP(rs.getString(2));
              dp.setAddress(InetAddress.getByName(ip.trim()));    // Set UDP packet target
              udpin.sendPacket(dp, 10);                           // Send it.
            } catch (UnknownHostException e) {
              prta("UnknownHost in UDPHeartbeat " + ip + " " + station);
            } catch (IOException e) {
              prta("IOEerror in UDPHeartbeat " + ip + " " + station + " e=" + e);
            }
          }
        }
        try {
          par.sleep(120000);
        } catch (InterruptedException e) {
        }
      } catch (SQLException e) {
        prta("SQLError reading IP addresses - close statement e=" + e);
        try {
          stmt.close();
        } catch (SQLException e2) {
        }
        stmt = null;
        dbconnedge.reopen();
        try {
          par.sleep(30000);
        } catch (InterruptedException e2) {
        }
      }

    }   // while !terminate or
  }
}
