/*
 *  This software is in the public domain because it contains materials 
 *  that originally came from the United States Geological Survey, 
 *  an agency of the United States Department of Interior. For more 
 *  information, see the official USGS copyright policy at 
 *  http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.waveserver;

import gov.usgs.anss.db.DBConnectionThread;
import gov.usgs.anss.edgethread.EdgeThread;
import gov.usgs.anss.util.Util;
import java.util.Set;
import java.util.Iterator;
import java.util.TreeMap;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * This is a fix it class which examines holdings on the "StatusDBServer" and
 * updates the channel table on "DBServer" if the earliest found dates in
 * holdings is before the created time of the channel. This makes it so that
 * CWBWaveServer running without -holdings has more accurate starting times for
 * the channels. This can be run on its own, though it is often run with the
 * '-updatecreated' in QueryMom.
 *
 * @author U.S. Geological Survey <ketchum at usgs.gov>
 */
public class UpdateCreatedFromHoldings {

  public static boolean main(String[] args) {
    DBConnectionThread dbconnHoldings;
    DBConnectionThread dbconnChannel;
    Util.setProcess("updcreated");
    Util.init("edge.prop");
    Util.prta("Starting UpdateCreatedFromHoldings");
    boolean createChan = false;
    for (String arg : args) {
      Util.prta(arg);
      if (arg.equalsIgnoreCase("-create")) {
        createChan = true;
      }
    }
    TreeMap<String, Timestamp[]> chans = new TreeMap<>();
    dbconnHoldings = DBConnectionThread.getThread("statusCreated");
    dbconnChannel = DBConnectionThread.getThread("channelCreated");
    try {
      if (dbconnHoldings != null) {
        dbconnHoldings.reopen();
      } else {
        String holdingServer = null;
        if (holdingServer == null) {
          holdingServer = Util.getProperty("StatusDBServer");
        }
        if (holdingServer.equals("")) {
          holdingServer = Util.getProperty("StatusDBServer");
        }
        dbconnHoldings = new DBConnectionThread(holdingServer, "update", "status", true, false, "statusCreated", null);
        if (!DBConnectionThread.waitForConnection("statusCreated")) {
          if (!DBConnectionThread.waitForConnection("statusCreated")) {
            if (!DBConnectionThread.waitForConnection("statusCreated")) {
              if (!DBConnectionThread.waitForConnection("statusCreated")) {
                if (!DBConnectionThread.waitForConnection("statusCreated")) {
                  if (!DBConnectionThread.waitForConnection("statusCreated")) {
                    if (!DBConnectionThread.waitForConnection("statusCreated")) {
                      if (!DBConnectionThread.waitForConnection("statusCreated")) {
                        EdgeThread.staticprt(" **** Could not connect to status database " + holdingServer);
                        Util.prta(" **** could not connect to status DB " + holdingServer);
                        dbconnHoldings.close();
                        return false;
                      }
                    }
                  }
                }
              }
            }
          }
        }
        Util.prta("DB to holdings is connected db=" + holdingServer);
      }

      if (dbconnChannel != null) {
        dbconnChannel.reopen();
      } else {
        String channelServer = null;
        if (channelServer == null) {
          channelServer = Util.getProperty("DBServer");
        }
        if (channelServer.equals("")) {
          channelServer = Util.getProperty("DBServer");
        }
        Util.prta("UpdateCreatedFromHoldings: dbserver=" + channelServer);
        dbconnChannel = new DBConnectionThread(channelServer, "update", "edge", true, false, "channelCreated", null);
        if (!DBConnectionThread.waitForConnection("channelCreated")) {
          if (!DBConnectionThread.waitForConnection("channelCreated")) {
            if (!DBConnectionThread.waitForConnection("channelCreated")) {
              if (!DBConnectionThread.waitForConnection("channelCreated")) {
                if (!DBConnectionThread.waitForConnection("channelCreated")) {
                  if (!DBConnectionThread.waitForConnection("channelCreated")) {
                    if (!DBConnectionThread.waitForConnection("channelCreated")) {
                      if (!DBConnectionThread.waitForConnection("channelCreated")) {
                        EdgeThread.staticprt(" **** Could not connect tochannel database " + channelServer);
                        Util.prta("UpdateCreatedFromHoldings: **** could not connect to config DB " + channelServer);
                        dbconnChannel.close();
                        dbconnHoldings.close();
                        return false;
                      }
                    }
                  }
                }
              }
            }
          }
        }
      }
    } catch (InstantiationException e) {
      EdgeThread.staticprt("UpdateCreatedFromHoldings: **** Impossible Instantiation exception e=" + e);
      if (dbconnChannel != null) {
        dbconnChannel.close();
      }
      if (dbconnHoldings != null) {
        dbconnHoldings.close();
      }
      return false;
    }
    try {
      Util.prta("UpdateCreatedFromHoldings: started");
      long started = System.currentTimeMillis();
      String[] tables = {"holdingshist2", "holdingshist", "holdings"};
      for (String table : tables) {
        try {
          Util.prta("UpdateCreatedFromHoldings: start query on " + table);
          try (ResultSet rs = dbconnHoldings.executeQuery("SELECT seedname,min(start),max(ended) FROM " + table + " GROUP BY seedname ORDER BY seedname,start")) {
            while (rs.next()) {
              Timestamp[] ts = chans.get(rs.getString(1));
              if (ts == null) {
                ts = new Timestamp[2];
                ts[0] = rs.getTimestamp(2);
                ts[1] = rs.getTimestamp(3);
                chans.put(rs.getString(1), ts);

              } else {
                if (ts[0].getTime() > rs.getTimestamp(2).getTime()) {
                  ts[0].setTime(rs.getTimestamp(2).getTime());
                }
                if (ts[1].getTime() < rs.getTimestamp(3).getTime()) {
                  ts[1].setTime(rs.getTimestamp(3).getTime());
                }
              }
            }
          }
        } catch (SQLException e) {
          e.printStackTrace();
          Util.prta("UpdateCreatedFromHoldings: SQL error causing exit.");
          return false;
        }
      }
      int nupdate = 0;
      int nproc = 0;
      try {
        try (Statement stmt = dbconnChannel.getNewStatement(true);
                ResultSet rs = dbconnChannel.executeQuery(
                        "SELECT channel, created, lastdata FROM edge.channel ORDER BY channel")) {
          while (rs.next()) {
            Timestamp[] ts = chans.get(rs.getString(1));
            nproc++;
            if (ts != null) {
              if (ts[0].getTime() < rs.getTimestamp(2).getTime() - 1000) {
                Util.prta("UpdateCreatedFromHoldings: Set created=" + ts[0].toString()
                        + " for Channel=" + rs.getString(1) + " was " + rs.getTimestamp(2)
                        + " diff=" + Util.df23((ts[0].getTime() - rs.getTimestamp(2).getTime()) / 86400000.)
                        + " days");
                nupdate++;
                stmt.executeUpdate("UPDATE edge.channel SET created='" + ts[0].toString()
                        + "' WHERE channel='" + rs.getString(1) + "'");
              }
              if (ts[1].getTime() > rs.getTimestamp(3).getTime() + 21600000) {   // is it more the 6 hours ahead for lastdata?
                Util.prta("UpdateCreatedFromHoldings: Set udpated=" + ts[1].toString() 
                        + " for Channel=" + rs.getString(1) + " was " + rs.getTimestamp(3) 
                        + " diff=" + Util.df23((rs.getTimestamp(3).getTime() - ts[1].getTime()) / 86400000.) 
                        + " days");
                nupdate++;
                stmt.executeUpdate("UPDATE edge.channel SET lastdata='" + ts[1].toString() 
                        + "' WHERE channel='" + rs.getString(1) + "'");
              }
              chans.remove(rs.getString(1));    // indicate its done
            }
          }
        }
      } catch (SQLException e) {
        e.printStackTrace();
        Util.prta("UpdateCreatedFromHoldings: SQL error causing exit. number updated=" + nupdate 
                + " nproc=" + nproc + " size=" + chans.size());
        return false;
      }

      Set<String> keys = chans.keySet();
      Iterator<String> itr = keys.iterator();
      while (itr.hasNext()) {
        String key = itr.next();
        if (key != null) {
          Timestamp[] ts = chans.get(key);
          if (ts != null) {
            Util.prta("UpdateCreatedFromHoldings: Got holding for a channel not in channel!  " + key 
                    + " created=" + createChan);
            if (createChan) {
              try {
                dbconnChannel.executeUpdate("INSERT INTO channel (channel,lastdata,updated,created) VALUES ('" 
                        + key + "','" + ts[1].toString() + "',now(),'" + ts[0].toString() + "')");
              } catch (SQLException e) {
                e.printStackTrace();
                Util.prta("UpdateCreatedFromHoldings: SQLError inserting channel " + key + " e=" + e);
              }
            }
          }
        }
      }
      Util.prta("UpdateCreatedFromHoldings: nupdate=" + nupdate + " nproc=" + nproc 
              + " #not in channel table=" + chans.size()
              + " elapsed=" + (System.currentTimeMillis() - started));
    } catch (RuntimeException e) {
      Util.prt("UpdateCreatedFromHoldings: error e=" + e);
      e.printStackTrace();
      dbconnChannel.close();
      dbconnHoldings.close();
      return false;
    }
    Util.prta("UpdateCreatedFromHoldings: ending");
    dbconnChannel.close();
    dbconnHoldings.close();
    return true;
  }
}
