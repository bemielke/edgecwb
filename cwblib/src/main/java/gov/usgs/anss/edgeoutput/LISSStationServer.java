/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.edgeoutput;

import gov.usgs.alarm.SendEvent;
import gov.usgs.anss.db.DBConnectionThread;
import gov.usgs.anss.edge.IllegalSeednameException;
import gov.usgs.anss.edgethread.EdgeThread;
import gov.usgs.anss.seed.MiniSeed;
import gov.usgs.anss.util.Util;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.TreeMap;

/**
 * LISSStationServer.java - This controls the queue for one station for LISS output from the
 * ChannelInfrastructure. It keeps the ring in memory so that it can be used by LISSOutputers which
 * service a LISSSocket. Its thread listens for connections to the assigned port and creates a
 * LISSSocket for each incoming connection. There is logic which should be called periodically from
 * another thread which will prune off dead connections.
 *
 *
 *
 * Created on March 29, 2007, 5:22 PM
 *
 * @author davidketchum
 *
 * To change this template, choose Tools | Template Manager and open the template in the editor.
 */
public final class LISSStationServer extends Thread {

  private final static TreeMap<String, LISSStationServer> servers = new TreeMap<String, LISSStationServer>();
  public static int RING_SIZE = 200;        // we do not know how many channels with how many block might dump
  // in here when a "gap" is completed.  Be generous on spac
  private static DBConnectionThread dbconn;
  private static final Integer dbmutex = Util.nextMutex();
  private static long lastDB;
  private int queueMsgMax;
  private final ArrayList<byte[]> queue;
  //private int []  length;
  private long latencyAvg;
  private long nlatency;
  private long minLatency;
  private long maxLatency;
  private int nextin;
  private final String station;       // 7 characer NNSSSSS seedname
  private int port;             // listen port
  private ServerSocket ss;      // The listener socket
  private final String bindhost;
  private final ArrayList<LISSSocket> thr;
  private final LISSStationServerShutdown shutdown;
  private long lastTotalPackets;
  private static EdgeThread par;
  private boolean terminate;
  private boolean dbg;

  public static void setParent(EdgeThread p) {
    par = p;
  }

  private static void prt(String s) {
    if (par == null) {
      Util.prt(s);
    } else {
      par.prt(s);
    }
  }

  private static void prta(String s) {
    if (par == null) {
      Util.prta(s);
    } else {
      par.prta(s);
    }
  }

  /**
   * given a seed name, return the LISSStationServer handling this station. If it does not exist,
   * create it.
   *
   * @param seedname The station portion of this seedname is used to find the LISSStationServer
   * @return The LISSStationServer
   */
  public static synchronized LISSStationServer getServer(String seedname) {
    String stat = (seedname + "      ").substring(0, 7);
    LISSStationServer s = servers.get(stat);
    if (s == null || !s.isAlive()) {
      if (s == null && seedname.length() >= 10) {
        if (seedname.substring(7, 10).equals("LOG")) {
          return null;
        }
      }
      int loop = 0;
      if (par == null) {
        SendEvent.edgeSMEEvent("LISSBadPrt", "Could not get parent for logging in reasonable time", "LISSStationServer");
        Util.prta("LISSStationServ: getServer for seedname=" + seedname + " not found.  Start a LISSStationServer" + loop);
      } else {
        par.prta("LISSStationServ: getServer for seedname=" + seedname + " not found.  Start a LISSStationServer" + loop);
      }
      s = new LISSStationServer(stat);
      servers.put(stat, s);
    }
    return s;
  }

  public String getStation() {
    return station;
  }

  /**
   * terminate the given LISSSocket object if it is on the list
   *
   * @param obj A LISSSocket object
   */
  public void terminate(LISSSocket obj) {
    if (thr != null) {
      if (thr.remove(obj)) {
        obj.terminate();
      }
    }
    /*try { // this was commented as it seems to lead to run away looping when clearDeadConnections ran
      ss.close();
    }
    catch(IOException e) {}*/
  }

  public ArrayList<LISSSocket> getSockets() {
    return thr;
  }

  /**
   * clear out any connections that have not written to the other end in the given interval
   *
   * @param timeOut Timeout interval in mills
   */
  public static void clearDeadConnections(long timeOut) {
    long now = System.currentTimeMillis();
    Iterator<LISSStationServer> itr = servers.values().iterator();
    while (itr.hasNext()) {
      LISSStationServer st = itr.next();
      ArrayList<LISSSocket> socks = st.getSockets();
      if (socks != null) {
        for (int i = socks.size() - 1; i >= 0; i--) {
          if (now - socks.get(i).getLastWrite() > timeOut) {
            prta("LISSStationServer: Drop connection on timeout " + socks.get(i).toString());
            st.terminate(socks.get(i));
          }
        }
      }
    }
  }

  /**
   * return a string with status on each LISSStationServer and connections to same
   *
   * @return The string.
   */
  public static String getStatus() {
    StringBuilder sb = new StringBuilder(1000);
    sb.append(Util.ascdate()).append(" ").append(Util.asctime()).append(" LISSStationServer status :\n");
    Iterator<LISSStationServer> itr = servers.values().iterator();
    while (itr.hasNext()) {
      LISSStationServer st = itr.next();
      sb.append(st.toString()).append(" ").append(st.getStatistics()).append("\n");
      ArrayList<LISSSocket> socks = st.getSockets();
      if (socks != null) {
        for (int i = 0; i < socks.size(); i++) {
          sb.append("   ").append(i).append(" ").
                  append(socks.get(i).toString()).append(" ").append(socks.get(i).getStatistics()).append("\n");
        }
      }
    }
    return sb.toString();
  }

  /**
   * return a string with status on each LISSStationServer and connections to same
   *
   * @return The string.
   */
  public static long getTotalPackets() {
    long total = 0;
    Iterator<LISSStationServer> itr = servers.values().iterator();
    while (itr.hasNext()) {
      LISSStationServer st = itr.next();
      ArrayList<LISSSocket> socks = st.getSockets();
      if (socks != null) {
        for (int i = 0; i < socks.size(); i++) {
          total += socks.get(i).getNPackets();
        }
      }
    }
    return total;
  }

  /**
   * return a string with latency statistics and reset the statistics
   *
   * @return The string
   */
  public String getStatistics() {
    String s = station + " Latency min=" + minLatency + " max=" + maxLatency + " #pck=" + nlatency + " avg=" + (nlatency > 0 ? (latencyAvg / nlatency) : 0);
    nlatency = 0;
    latencyAvg = 0;
    minLatency = 100000000;
    maxLatency = -100000000;
    return s;
  }

  @Override
  public String toString() {
    return "LISSStaServ: " + station + " #sock=" + (thr == null ? "null" : thr.size()) + " queue : sz=" + RING_SIZE + " nextin=" + nextin + " port=" + port;
  }

  public boolean dequeue(int nextout, byte[] buf) {
    if (nextout == nextin) {
      return false;
    }
    System.arraycopy(queue.get(nextout), 0, buf, 0, 512);
    return true;
  }

  public void queue(byte[] buf, int len) {
    try {
      if (len > 512) {
        return;
      }
      int[] t = MiniSeed.crackTime(buf);
      long ms = t[0] * 3600000L + t[1] * 60000 + t[2] * 1000 + t[3] / 10 + ((long) (MiniSeed.crackNsamp(buf) * 1000. / MiniSeed.crackRate(buf)));
      ms = System.currentTimeMillis() % 86400000L - ms;
      //if(MiniSeed.crackSeedname(buf).substring(7,10).equals("LOG")) prta("LOG recout2 "+MiniSeed.crackSeedname(buf));
      if (MiniSeed.crackSeedname(buf).substring(7, 10).equals("BHZ")) {
        if (ms < minLatency) {
          minLatency = ms;
        }
        if (ms > maxLatency) {
          maxLatency = ms;
        }
        latencyAvg += ms;
        nlatency++;
      }
      if (MiniSeed.crackSeedname(buf).substring(0, 6).equals(ChannelHolder.getDebugSeedname())) {
        prta("LSSS: Queue data " + MiniSeed.crackSeedname(buf) + " " + MiniSeed.crackNsamp(buf) + " " + nextin + " "
                + t[0] + ":" + t[1] + ":" + t[2] + "." + t[3] + " lat=" + ms);
      }
      System.arraycopy(buf, 0, queue.get(nextin), 0, 512);
      nextin = (nextin + 1) % RING_SIZE;
    } catch (IllegalSeednameException e) {
      prt("LISSStationServer got a non-miniseed =" + MiniSeed.toStringRaw(buf));
      e.printStackTrace();
    }
  }

  /**
   * given a nextout into the ring buffer, return the next one
   *
   * @param nextout The current ring offset
   * @return THe next offset
   */
  public int next(int nextout) {
    return (nextout + 1) % RING_SIZE;
  }

  /**
   * Creates a new instance of LISSStationServer
   *
   * @param name A seed name, or at least station portion of a seedname for this server
   */
  public LISSStationServer(String name) {
    station = name;
    bindhost = "";
    queue = new ArrayList<byte[]>(RING_SIZE);
    for (int i = 0; i < RING_SIZE; i++) {
      queue.add(new byte[512]);
    }
    thr = new ArrayList<LISSSocket>(10);
    shutdown = new LISSStationServerShutdown();
    Runtime.getRuntime().addShutdownHook(shutdown);
    start();
  }

  @Override
  public void run() {
    port = 0;
    int loop = 0;
    while (par == null) {
      par = ChannelInfrastructure.getParent();
      try {
        sleep(1000);
      } catch (InterruptedException e) {
      }
      if (loop++ % 60 == 0) {
        SendEvent.edgeSMEEvent("LISSNoLog", "Waiting on log port in LISSStationServer " + station + " " + port, this);
      }
    }
    boolean success = false;
    loop = 0;
    while (!success && !terminate) {
      synchronized (dbmutex) {
        if ((dbconn != null && System.currentTimeMillis() - lastDB > 600000) || loop > 0) {
          prta("Reopen database - its been " + ((System.currentTimeMillis() - lastDB) / 1000) + " secs loop=" + loop);
          dbconn.reopen();
          prta("Reopen database - complete");
        }
      }
      lastDB = System.currentTimeMillis();
      loop++;
      synchronized (dbmutex) {
        if (dbconn == null) {
          try {
            dbconn = new DBConnectionThread(Util.getProperty("DBServer"), "readonly", "edge", false, false,
                    "LISSStationRO", (par == null ? Util.getOutput() : par.getPrintStream()));
            if (par != null) {
              par.addLog(dbconn);
            }
            if (!DBConnectionThread.waitForConnection("LISSStationRO")) {
              if (!DBConnectionThread.waitForConnection("LISSStationRO")) {
                prt("Slow connection to edge");
              }
            }
          } catch (InstantiationException e) {
            prta("InstantiationException: should be impossible");
            dbconn = DBConnectionThread.getThread("LISSStationRO");
          }
        }
      }
      prta("Starting LISSStationServer for " + station);
      int sleepFor = 4000;
      synchronized (dbmutex) {
        try {
          sleepFor = 4000;
          try (ResultSet rs = dbconn.executeQuery("SELECT id FROM edge.snwstation WHERE network='"
                  + station.substring(0, 2) + "' AND snwstation='" + station.substring(2, 7).trim() + "'")) {
            if (rs.next()) {
              port = rs.getInt("id") + 24000;
              prta("LISSStationServer: try create " + station.trim() + ":" + bindhost + " -p " + port + " -l 512 loop=" + loop);
              if (port > 0) {
                if (bindhost.equals("")) {
                  ss = new ServerSocket(port);            // Create the listener
                } else {
                  ss = new ServerSocket(port, 6, InetAddress.getByName(bindhost));
                }
                success = true;
              } else {
                if (loop % 15 == 0) {
                  SendEvent.debugEvent("NoSNWStation", "LISSStation could not find a snwstation for " + station, this);
                }
                prta("LISSStationServ:  No port for station wait and try again....");
              }
            } else {
              prt("LISSStationServer: did not get a SNWStation for " + station);
            }
          }
        } catch (SQLException e) {
          prta("LISSStationServ: SQL error looking for station=" + station + " e=" + e.getMessage());
          Util.SQLErrorPrint(e, "LISSStationServ: SQL error looking in snwstation for " + station);
        } catch (IOException e) {
          prta("LISSStationServ: IOException opening listener on " + station + " socket = " + bindhost + "/" + port + " e=" + e.getMessage());
          if (e.getMessage().contains("already")) {
            SendEvent.edgeSMEEvent("LISSDupPort", "Port already open " + station + " " + port, this);
            sleepFor = 30000;
          }
        }
      }
      if (!success) {
        try {
          sleep(sleepFor);
        } catch (InterruptedException e) {
        }
      }
    }
    prta("LISSStationServ success=" + success + " loops=" + loop + " for " + station);
    prta(Util.asctime() + " LISSStationServer : start accept loop.  I am " + station + " "
            + ss.getInetAddress() + "-" + ss.getLocalSocketAddress() + "|" + ss);
    while (true && !terminate) {
      prta(Util.asctime() + " LISSStationServer: call accept() " + station);
      try {
        Socket s = ss.accept();
        if (terminate) {
          break;
        }
        prta(Util.asctime() + " LISSStationServ: new socket=" + station + " " + s + " at client=" + thr.size());
        LISSSocket mss = new LISSSocket(s, this, nextin, station, par);
        thr.add(mss);
        // check the list of open sockets for ones that have gone dead, remove them
        Iterator<LISSSocket> itr = thr.iterator();
        int n = 0;
        while (itr.hasNext()) {
          mss = itr.next();
          n++;
          if (!mss.isAlive() && !mss.isRunning()) {
            prta(mss.getTag() + " has died.  Remove it");
            itr.remove();
          }
        }
      } catch (IOException e) {
        if (terminate && e.getMessage().equalsIgnoreCase("Socket closed")) {
          prta(station + " LISSStationServ: accept socket closed during termination");
        } else if (e.getMessage().contains("Socket closed")) {
          prta(station + " LISSStaitonServ: **** accept socket close NOT during terminate.  exit.");
          try {
            sleep(1000);
          } catch (InterruptedException e2) {
          }
          terminate = true;
        } else {
          Util.IOErrorPrint(e, station + " LISSStationServ: **** accept gave unusual IOException!");
        }
        terminate = true;
      }
      if (terminate) {
        break;
      }
    }
  }

  /**
   * this internal class gets registered with the Runtime shutdown system and is invoked when the
   * system is shutting down. This must cause the thread to exit
   */
  class LISSStationServerShutdown extends Thread {

    /**
     * default constructor does nothing the shutdown hook starts the run() thread
     */
    public LISSStationServerShutdown() {
    }

    // This is called by the RunTime.shutdown at any termination 
    @Override
    public void run() {
      prta(station + "LISSS:  Shutdown() started...");
      terminate = true;
      try {
        ss.close();
      } catch (IOException e) {
      }
      for (int i = 0; i < thr.size(); i++) {
        thr.get(i).terminate();
      }
      int loop = 0;
      prta(station + "LISSS: shutdown() is complete.");
    }
  }
}
