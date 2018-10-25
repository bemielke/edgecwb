/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.alarm;

import gov.usgs.anss.channelstatus.ChannelStatus;
//import gov.usgs.alarm.SendEvent;
import gov.usgs.anss.db.DBConnectionThread;
import gov.usgs.anss.edgethread.EdgeThread;
import gov.usgs.anss.edgemom.Logger;
import gov.usgs.anss.net.StatusSocketReader;
import gov.usgs.anss.util.Util;
import gov.usgs.anss.util.AnssPorts;
import gov.usgs.anss.util.SimpleSMTPThread;
import gov.usgs.anss.util.UC;
import gov.usgs.edge.snw.SNWStation;
import java.io.PrintStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.FileReader;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.TreeMap;
import java.util.ArrayList;

/**
 * This class populates the WebStation netops_station table in one or more databases. This database is also in the metadata
 * database at the USGS so the MetaDBServer must be set up. This software is of no use to anyone
 * outside of the USGS. Disable it in UdpChannel via the -noweb option.
 *
 * @author davidketchum
 */
public final class WebStationClient extends Thread {

  private StatusSocketReader channels;
  private final TreeMap<String, SNWStation> map;
  private final ArrayList<DBConnectionThread> dbs = new ArrayList<>(2);
  private long lastPage;
  private boolean terminate;
  private int nupd;
  private int state;
  private int loop;
  private boolean dbg;
  EdgeThread par;
  private StringBuilder runsb = new StringBuilder(100);

  public StatusSocketReader getStatusSocketReader() {
    return channels;
  }

  @Override
  public String toString() {
    return "WBSC: #upd=" + nupd + " #stations=" + map.size() + " state=" + state;
  }

  public int getLoop() {
    return loop;
  }

  private PrintStream getPrintStream() {
    if (par == null) {
      return Util.getOutput();
    } else {
      return par.getPrintStream();
    }
  }

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

  private void prt(StringBuilder s) {
    if (par == null) {
      Util.prt(s);
    } else {
      par.prt(s);
    }
  }

  private void prta(StringBuilder s) {
    if (par == null) {
      Util.prta(s);
    } else {
      par.prta(s);
    }
  }

  public void terminate() {
    terminate = true;
  }

  /**
   * Creates a new instance of WebStationClient
   *
   * @param rt The StatusSocketReader to used to get the channel status
   * @param d A EdgeThread through which logging should be done
   */
  public WebStationClient(StatusSocketReader rt, EdgeThread d) {
    if (Util.getProperty("StatusServer") == null) {
      Util.setProperty("StatusServer", "localhost");
    }
    par = d;
    if (rt != null) {
      channels = rt;
    } else {
      channels = new StatusSocketReader(ChannelStatus.class, Util.getProperty("StatusServer"),
              AnssPorts.CHANNEL_SERVER_PORT, par);
    }

    channels.appendTag("WEBSC");
    map = new TreeMap<>();
    prta("WBSC: created to " + Util.getProperty("DBServer") + " StatusServer=" + Util.getProperty("StatusServer") + " Meta=" + Util.getProperty("MetaDBServer"));

    if (!dbs.isEmpty() ) {
      for(DBConnectionThread db : dbs) {
        db.reopen();
      }
    }
    else {
      String line;
      try (BufferedReader config = new BufferedReader(new FileReader("config/webstationclient.config"))) {
        while( (line = config.readLine()) != null) {
          if(line.charAt(0) == '#' || line.charAt(0) == '!') continue;
          try {
              String [] parts = line.split(",");
              DBConnectionThread db = new DBConnectionThread(parts[0], parts[1], parts[2], parts[3],true, false, parts[4],"mysql", getPrintStream());
              dbs.add(db);
              par.addLog(db);
          } catch (InstantiationException e) {
            prta("WBSC: InstantiationException on DB status is impossible!"+line);
          }

        }
      }
      catch(IOException  e) {
        prta("WBSC: config file IO error ="+e);
      }
    }

    try {
      sleep(30000);
    } catch (InterruptedException expected) {
    }
    // Wait for all the connections to be open
    while (true) {
      boolean allOK = true;
      for(DBConnectionThread db : dbs) {
        allOK = allOK && db.waitForConnection();
      }
      if (!allOK) {
        prta(Util.clear(runsb).append("WBSC: Could not yet connect to web server database or local response database.   Waitting..."));
        SendEvent.debugEvent("WebStatNoCon", "Not yet connected from WebStationClient to web server database or responses", "WebStationClient");
        try {
          sleep(60000);
        } catch (InterruptedException expected) {
        }
      } else {
        break;
      }
    }
    prta("WBSC: about to start thread2");
    start();
  }
  int nmsgs;
  Object[] msgs = new Object[2000];
  StringBuilder stationsNotFound = new StringBuilder(200);
  StringBuilder stationsOK = new StringBuilder(200);
  StringBuilder stationsUpdated = new StringBuilder(200);
  StringBuilder updateTime = new StringBuilder(15);

  private int doUpdate(DBConnectionThread db) throws SQLException {
    String s = "";
    ChannelStatus cs = null;
    prta("WBSC: doUpdate() db=" + db.getTag());
    String station = "";
    int notfound = 0;
    Util.clear(updateTime);
    int nok = 0;
    int nrows = 0;
    nupd = 0;
    Util.ascdatetime(System.currentTimeMillis(), updateTime);
    try (ResultSet rs = db.executeQuery("SELECT id,network_code,station_code,telemetry FROM netops_station order by network_code,station_code")) {
      state = 4;
      try (Statement stmt = db.getConnection().createStatement(
              ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE)) {
        int pnt = 0;
        state = 5;
        stationsNotFound.append("\nWBSC: NotFound: ").append(Util.rightPad(db.getTag(),8)).append(": ");
        stationsOK.append("\nWBSC: OK: ").append(Util.rightPad(db.getTag(),8)).append(": ");
        stationsUpdated.append("\nWBSC: Updated: ").append(Util.rightPad(db.getTag(),8)).append(": ");
        while (rs.next()) {
          nrows++;
          try {
            state = 6;
            String desired = rs.getString("network_code") + rs.getString("station_code").trim();
            state = 7;
            int ID = rs.getInt("id");
            double minage = 100000001.;
            int value = -1;
            // Find the best match in the list of channels
            for (int i = 0; i <= nmsgs; i++) {
              if (i == nmsgs) {

                station = "DUMMYWB     ";
              } else {
                cs = (ChannelStatus) msgs[i];
                station = cs.getKey().substring(0, 7).trim();
              }
              if (station.equals(desired)) {
                if (cs != null) {
                  if (cs.getAge() < minage) {
                    minage = cs.getAge();
                  }
                }
                state = 8;
                if (minage > 100000000.) {
                  value = -1;
                } // we did not find this station
                else if (minage < 10) {
                  value = 3;
                } else if (minage < 1440) {
                  value = 2;
                } else {
                  value = 1;
                }
              }
            }
            // if minage is unchnaged, then channels was not found
            if(minage == 100000001.) {
              if(rs.getInt("telemetry") != 0) {
                prta("WBSC: ** Got to nmsgs limit desired="+desired+" set status to 0 was "+rs.getInt("telemetry"));
                stmt.executeUpdate("UPDATE netops_station set telemetry=0,updated='" + updateTime + "' WHERE ID=" + ID);
              }
              stationsNotFound.append(" ").append(desired);
              notfound++;
            }
            else {
              if (value != rs.getInt("telemetry") && value != -1) {
                state = 9;
                stmt.executeUpdate("UPDATE netops_station set telemetry=" + value + ",updated='" + updateTime + "' WHERE ID=" + ID);
                state = 10;
                prta(Util.clear(runsb).append("WBSC: Update2 ").append(desired).append(" ID=").append(ID).append(" to ").
                        append(value).append(" was ").append(rs.getInt("telemetry")).append(" ").append(updateTime).append(" age=").append(Util.df22(minage)));
                nupd++;
                stationsUpdated.append(" ").append(desired);
              } else {
                nok++;
                stationsOK.append(" ").append(desired);
              }
              state = 11;
            }
            
          } catch (RuntimeException e) {
            state = 19;
            prta(Util.clear(runsb).append("WBSC: caught a RuntimeException e=").append(e.getMessage()));
            prta(Util.clear(runsb).append("WBSC: Station=").append(station).append(":"));
            e.printStackTrace();
            if (System.currentTimeMillis() - lastPage > 300000) {
              lastPage = System.currentTimeMillis();
              SimpleSMTPThread.email("ketchum", "WebStationClient got runtime exception e=" + e.getMessage(),
                      Util.asctime() + " " + Util.ascdate()
                      + " This generally comes from the WebStationClient in UdpChannel\n");
              SendEvent.debugEvent("WebStaRunExc", "WebStationClient got runtime e=" + e, this);
            }
          }
        }
      }
    } catch (SQLException  e) {
      prta("WBSC: SQL exception in doUpdate() db="+db.getTag()+" e=" + e);
      e.printStackTrace(getPrintStream());
      throw e;
    }
    catch(RuntimeException e) {
      prta("WBSC: RuntimeException e="+e);
      e.printStackTrace(getPrintStream());
    }
    prta(Util.clear(runsb).append("WBSC: doUpdate return nrow=").append(nrows).
            append(" nok=").append(nok).append(" upd=").append(nupd).
            append(" notfound=").append(notfound).append(" db=").append(db.getTag()));
    return notfound;
  }

  @Override
  public void run() {
    prta("WBSC: start");
    long start;
    long end;
    nupd = 0;
    loop = 0;
    try {
      sleep(15000);
    } catch (InterruptedException expected) {
    }  // Allow time for database threads to open
    while (!terminate) {
      int notfound = 0;
      state = 1;
      nupd = 0;
      start = System.currentTimeMillis();
      loop++;
      nmsgs = channels.length();
      //String nowtime=Util.now().toString().substring(0,19);// coordinated time for the sample
      if (msgs.length < nmsgs + 10) {
        msgs = new Object[nmsgs * 2];
      }
      channels.getObjects(msgs);
      prta(Util.clear(runsb).append(Util.ascdate()).append(" WBSC: outside db.size=").append(dbs.size()).append(" nmsgs=").append(nmsgs).append(" loop=").append(loop));
      state = 3;
      if (channels != null) {
        state = 2;

        Util.clear(stationsNotFound);
        Util.clear(stationsOK);
        Util.clear(stationsUpdated);
        for (DBConnectionThread db : dbs) {

          try {
            notfound += doUpdate(db);
            state = 14;
          } catch (SQLException e) {
            Util.SQLErrorPrint(e, "WBSC: SQL error updating Web status table - wait and try again", getPrintStream());
            db.reopen();
            state = 18;
            SendEvent.debugEvent("WebStaSQLDwn", "WBSC: SQL error e=" + e, this);
            try {
              sleep(60000L);
            } catch (InterruptedException e2) {
            }
          } catch (RuntimeException e) {
            state = 19;
            prta(Util.clear(runsb).append("WBSC: caught a RuntimeException e=").append(e.getMessage()));
            e.printStackTrace(getPrintStream());
            if (System.currentTimeMillis() - lastPage > 300000) {
              lastPage = System.currentTimeMillis();
              SimpleSMTPThread.email("ketchum", "WebStationClient got runtime exception e=" + e.getMessage(),
                      Util.asctime() + " " + Util.ascdate()
                      + " This generally comes from the WebStationClient in UdpChannel\n");
              SendEvent.debugEvent("WebStaRunExc", "WebStationClient got runtime e=" + e, this);
            }
          }

        }   // end of loop on DBs
        end = System.currentTimeMillis();
        prta(Util.clear(runsb).append("WBSC: Update ").append(end - start).append(" ms upd=").append(nupd).
                append(" Not Found=").append(notfound).append(" ").append(stationsNotFound));
        prta("WBSC: Updated : " + stationsUpdated);      
      } else {
        state = 12;
        prta("WBSC: ** channels is null - wait for it to come back up.");
        channels = new StatusSocketReader(ChannelStatus.class, Util.getProperty("StatusServer"),
                AnssPorts.CHANNEL_SERVER_PORT, par);
        state = 13;
        end = System.currentTimeMillis();
      }
      try {
        state = 15;
        if ((600000L - (end - start)) < 56000L) {
          prta(Util.clear(runsb).append("WBSC: wait short ").append(60000L - (end - start)));
        }
        if ((600000L - (end - start)) < 0L) {
          if ((600000L - (end - start)) < -600000) {
            SendEvent.debugEvent("WBSChWait", "WBSChannel negative short wait " + (60000L - (end - start)), this);
          }
          SimpleSMTPThread.email("ketchum", "WBSChannel negative short wait ",
                  Util.ascdate() + " " + Util.asctime() + " something is slowing us way down! " + (60000L - (end - start)) + " ms");
        }
        state = 16;
        sleep(Math.max(1L, 600000L - (end - start)));
        state = 17;
        //sleep(30000);
      } catch (InterruptedException expected) {
      } catch (RuntimeException e) {
        e.printStackTrace(getPrintStream());
      }
      //prta("WBSC: Out of wait ");
      state = 20;
    }
  }

  public static void main(String[] args) {
    Util.init();
    Util.loadProperties(UC.getPropertyFilename());
    Util.setModeGMT();
    Util.prt("starting argc=" + args.length);
    Logger log = new Logger("-empty >>webstationclient", "Tag");
    WebStationClient WBSC = new WebStationClient(null, log);
  }
}
