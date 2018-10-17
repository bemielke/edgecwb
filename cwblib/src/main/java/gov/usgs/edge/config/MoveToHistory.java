/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.edge.config;

import gov.usgs.alarm.SendEvent;
import gov.usgs.anss.db.DBConnectionThread;
import gov.usgs.anss.net.Holding;
import gov.usgs.anss.net.HoldingArray;
import gov.usgs.anss.util.SimpleSMTPThread;
import gov.usgs.anss.util.User;
import gov.usgs.anss.util.Util;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.GregorianCalendar;
import java.util.Calendar;
import java.util.TreeMap;
import java.util.Iterator;

/**
 * * MoveToHistory.java
 *
 * Created on July 17, 2006, 10:55 AM
 *
 * This class processes Holdings into Holding History. It moves any data holding which ends before
 * midnight 2 days ago or which spans that midnight. If it finds the candidate holding ID in
 * history, it updates the record, if it does not it creates it as a copy of the holding. If the
 * record is prior to the boundary it is deleted from holdings.
 * <br>
 * When the "move" pass is completed the holdinghist is then consolidated for all records in the
 * last 6 months. It is set up to get the list of channels, then process the 6 months holdings a
 * channel at a time so the result sets are not too large.
 * <br>
 * If all goes well, the current holdings are kept small so the working set is manageable. The
 * history basically contains all the holdings we generally manage.
 * <br>
 * This program moves data between active status tables (dasstatus,latency, ping qdping) and the
 * hist and hist2 tables. It also moves fetches to history if this option is selected. I will
 * periodically optimize all of the current tables.
 *
 * <PRE>
 * Switch   Args     Description
 * -movefetchlist   If present, fetchlist items are managed as well
 * </PRE>
 *
 * @author davidketchum
 */
public final class MoveToHistory {

  /**
   * Creates a new instance of MoveToHistory
   */
  public MoveToHistory() {
  }

  /**
   * @param args the command line arguments
   */
  public static void main(String[] args) {
    //JDBConnection  jcjbl;
    //Connection  C;
    TreeMap<String, String> comps = new TreeMap<>();
    ArrayList<Integer> deleteID = new ArrayList<>(100000);
    Util.setProcess("MoveToHistory");
    Util.init("edge.prop");
    User user = new User("dkt");
    Util.setNoInteractive(true);
    String begDate = "";
    String selType = "";
    boolean dbg = true;
    GregorianCalendar today = new GregorianCalendar();
    today.add(Calendar.HOUR, -3 * 24);
    StringBuilder endDate = new StringBuilder(20).append(Util.ascdate(today));
    GregorianCalendar months18 = new GregorianCalendar();
    months18.add(Calendar.HOUR, -550 * 24);
    StringBuilder endDate18Months = new StringBuilder(20).append(Util.ascdate(months18));
    String where = "";
    String conDate = "";
    boolean doFetchlist = false;
    Util.setModeGMT();
    boolean noUpdateMeta = false;
    for (int i = 0; i < args.length; i++) {
      if (args[i].equals("-t")) {
        selType = args[i + 1];
      }
      if (args[i].equals("-w")) {
        where = args[i + 1];
      }
      if (args[i].equals("-e")) {
        endDate = Util.clear(endDate).append(args[i + 1]);
      }
      if (args[i].equals("-c")) {
        conDate = args[i + 1];
      }
      if (args[i].equalsIgnoreCase("-movefetchlist")) {
        doFetchlist = true;
      }
      if (args[i].equalsIgnoreCase("-noupdateMeta")) {
        noUpdateMeta = true;
      }
      //if(args[i].equals("-b")) begDate=args[i+1];
      if (args[i].equals("-h")) {
        Util.setProperty("DBServer", args[i + 1]);
        i++;
      }
      if (args[i].equals("-meta")) {
        Util.setProperty("MetaDBServer", args[i + 1]);
        i++;
      }
      if (args[i].equals("-status")) {
        Util.setProperty("StatusDBServer", args[i + 1]);
        i++;
      }
      if (args[i].equals("-?")) {
        Util.prt("MoveToHistory [-t TT][-b yyyy-mm-dd][-e yyyy-mm-dd][-h host.name");
        Util.prt("    -t TT         A two letter holdings type to include.");
        //Util.prt("    -b yyyy-mm-dd The beginning date to include (records can lap this date)");
        Util.prt("    -e yyyy-mm-dd The ending date to include (records can lap this date)");
        Util.prt("    -c yyyy-mm-dd Consolidation starts at this date)");
        Util.prt("    -h host.name  The dotted address of the MySQL server with status.holdings");
        Util.prt("   -movefetchlist If fetchlist moves are needed");
        Util.prt("    -w 'remaining where clause joined by AND'");
        Util.prt("    -w 'remaining where clause joined by AND'");
        System.exit(0);
      }
    }
    Util.prta("\n******* " + Util.ascdate() + " move Holdings date cutoff=" + endDate + " 18 months cutoff=" + Util.ascdate(months18)
            + " DBServer=" + Util.getProperty("DBServer") + " meta=" + Util.getProperty("MetaDBServer") + " status=" + Util.getProperty("StatusDBServer"));
    Util.stringBuilderReplaceAll(endDate, '/', '-');
    Util.prta("End1 = " + endDate + " 00:00:00.000");
    DBConnectionThread dbedge = null;
    DBConnectionThread dbedge2 = null;
    DBConnectionThread dbmeta = null;
    DBConnectionThread dbstatus = null;
    DBConnectionThread dbstatus2 = null;
    DBConnectionThread stmt3 = null;
    try {
      if (DBConnectionThread.getThread("meta") == null && !noUpdateMeta) {
        dbmeta = new DBConnectionThread(Util.getProperty("MetaDBServer"), "update", "metadata", true, false, "meta", Util.getOutput());
        if (!DBConnectionThread.waitForConnection("meta")) {
          if (!DBConnectionThread.waitForConnection("meta")) {
            if (!DBConnectionThread.waitForConnection("meta")) {
              if (!DBConnectionThread.waitForConnection("meta")) {
                if (!DBConnectionThread.waitForConnection("meta")) {
                  Util.prta("Could not connect to meta database abort run" + Util.getProperty("MetaDBServer"));
                  SimpleSMTPThread.email(Util.getProperty("emailTo"), "MoveToHistory cannot open meta",
                          Util.ascdate() + " " + Util.asctime() + " MoveToHistory on " + Util.getNode()
                          + " Failed to connection to database on " + Util.getProperty("MetaDBServer"));
                  SendEvent.edgeSMEEvent("MoveHistDB", "MoveToHistory DB error " + Util.getProperty("MetaDBServer"), "MoveToHistory");
                  System.exit(1);
                }
              }
            }
          }
        }
      }
      if (DBConnectionThread.getThread("edge") == null) {
        dbedge2 = new DBConnectionThread(Util.getProperty("DBServer"), "update", "edge", true, false, "edge", Util.getOutput());
        if (!DBConnectionThread.waitForConnection("edge")) {
          if (!DBConnectionThread.waitForConnection("edge")) {
            if (!DBConnectionThread.waitForConnection("edge")) {
              if (!DBConnectionThread.waitForConnection("edge")) {
                if (!DBConnectionThread.waitForConnection("edge")) {
                  SimpleSMTPThread.email(Util.getProperty("emailTo"), "MoveToHistory cannot open edge",
                          Util.ascdate() + " " + Util.asctime() + " MoveToHistory on " + Util.getNode()
                          + " Failed to connection to database on " + Util.getProperty("DBServer"));
                  Util.prta("Could not connect to Edge abort run " + Util.getProperty("DBServer"));
                  SendEvent.edgeSMEEvent("MoveHistDB", "MoveToHistory DB error " + Util.getProperty("DBServer"), "MoveToHistory");
                  System.exit(1);
                }
              }
            }
          }
        }
      }
      dbedge = new DBConnectionThread(Util.getProperty("DBServer"), "update", "edge", true, false, "dbedge", Util.getOutput());
      if (!DBConnectionThread.waitForConnection("dbedge")) {
        if (!DBConnectionThread.waitForConnection("dbedge")) {
          if (!DBConnectionThread.waitForConnection("dbedge")) {
            if (!DBConnectionThread.waitForConnection("dbedge")) {
              if (!DBConnectionThread.waitForConnection("dbedge")) {
                Util.prta("Could not connect to MySQL abort run");
                SimpleSMTPThread.email(Util.getProperty("emailTo"), "MoveToHistory no mysql conn",
                        Util.ascdate() + " " + Util.asctime() + " MoveToHistory on " + Util.getNode()
                        + " Failed to connection to DBServer on " + Util.getProperty("DBServer"));
                SendEvent.edgeSMEEvent("MoveHistDB", "MoveToHistory DB error " + Util.getProperty("DBServer"), "MoveToHistory");
                System.exit(1);
              }
            }
          }
        }
      }
      dbstatus = new DBConnectionThread(Util.getProperty("StatusDBServer"), "update", "status", true, false, "dbstatusB", Util.getOutput());
      if (!DBConnectionThread.waitForConnection("dbstatusB")) {
        if (!DBConnectionThread.waitForConnection("dbstatusB")) {
          if (!DBConnectionThread.waitForConnection("dbstatusB")) {
            if (!DBConnectionThread.waitForConnection("dbstatusB")) {
              Util.prta("Could not connect to Status B abort run " + Util.getProperty("StatusDBServer"));
              SimpleSMTPThread.email(Util.getProperty("emailTo"), "MoveToHistory no mysql conn",
                      Util.ascdate() + " " + Util.asctime() + " MoveToHistory on " + Util.getNode()
                      + " Failed to connection to StatusDBServer on " + Util.getProperty("StatusDBServer"));
              Util.prta("Could not connect to Status B abort run " + Util.getProperty("StatusDBServer"));
              SendEvent.edgeSMEEvent("MoveHistDB", "MoveToHistory DB error " + Util.getProperty("StatusDBServer"), "MoveToHistory");
              System.exit(1);
            }
          }
        }
      }
      dbstatus.setTimeout(600);
      dbstatus2 = new DBConnectionThread(Util.getProperty("StatusDBServer"), "update", "status", true, false, "dbstatusA", Util.getOutput());
      if (!DBConnectionThread.waitForConnection("dbstatusA")) {
        if (!DBConnectionThread.waitForConnection("dbstatusA")) {
          if (!DBConnectionThread.waitForConnection("dbstatusA")) {
            if (!DBConnectionThread.waitForConnection("dbstatusA")) {
              SimpleSMTPThread.email(Util.getProperty("emailTo"), "MoveToHistory no mysql conn",
                      Util.ascdate() + " " + Util.asctime() + " MoveToHistory on " + Util.getNode()
                      + " Failed to connection to StatusDBServer on " + Util.getProperty("StatusDBServer"));
              Util.prta("Could not connect to Status A abort run " + Util.getProperty("StatusDBServer"));
              SendEvent.edgeSMEEvent("MoveHistDB", "MoveToHistory DB error " + Util.getProperty("StatusDBServer"), "MoveToHistory");
              System.exit(1);
            }
          }
        }
      }
      dbstatus2.setTimeout(600);

    } catch (InstantiationException e) {
      Util.prt("instantiation exception e=" + e.getMessage());
      System.exit(0);
    }
    if (dbstatus == null || dbstatus2 == null || (dbmeta == null && !noUpdateMeta) || dbedge == null || dbedge2 == null) {
      Util.prta("Some DB did not open! ");
      System.exit(1);
    } else {
      //conDate="2008-12-01";     // debug
      try {
        // Start out be removeing everything on the delete list
        ArrayList<String> stats = new ArrayList<String>(1000);
        try {
          BufferedReader infile = new BufferedReader(new FileReader(Util.fs + "home" + Util.fs + "vdl" + Util.fs + "holdings.purge"));
          int totalDeleted = 0;
          String line;
          while ((line = infile.readLine()) != null) {
            if (line.trim().length() >= 2 && !line.substring(0, 1).equals("#")) {
              int row = dbstatus.executeUpdate("DELETE FROM status.holdings WHERE seedname regexp '^" + line + "'");
              Util.prt(line + " deleted " + row + " rows.");
              totalDeleted += row;
            }
          }
          Util.prta("Total deleted by regexp = " + totalDeleted + " End3 = " + endDate + " line=" + line);
        } catch (IOException e) {

          Util.prt("Could not open ~vdl/holdings.purge");
        }
        if (!noUpdateMeta) {
          Util.prta("Start updateFromMetadata.updateChannelResponseMetadata");
          UpdateFromMetadata.updateChannelResponseFromMetadata();
          //Util.prta("Start UpdateFromMetadata.updateTriplet");
          //UpdateFromMetadata.updateTriplet();
          Util.prta("Start UpdateFromMetadata.setMetaFromMDS(true, true");
          UpdateFromMetadata.setMetaFromMDS(true, true, false);
          Util.prta("Update metadata phase completed move on to channels.... End4 = " + endDate);
        }
        // Build a list of distinct components
        //ResultSet rstat = dbstatus.executeQuery("SELECT DISTINCT seedname FROM holdingshist order by seedname");
        dbedge.waitForConnection();
        ResultSet rstat = dbedge.executeQuery("SELECT channel FROM edge.channel order by channel");
        String last = "";
        String seedname;
        while (rstat.next()) {
          String s = rstat.getString(1);
          s = (s + "            ").substring(0, 12);
          if (Util.isValidSeedName(s)) {
            stats.add(s);  // put the station names on the list
          } else {
            Util.prt("Invalid channel=" + s);
          }
        }
        Util.prt("Total seednames found " + stats.size() + " End2=" + endDate);
        String s;
        int n;
        if (conDate.equals("")) {
          s = "SELECT * FROM status.holdings WHERE ID>0 ";
          if (!selType.equals("")) {
            s += " AND type=" + Util.sqlEscape(selType);
          }
          if (!where.equals("")) {
            s += " AND " + where;
          }
          //s += " AND LEFT(seedname,10)='USCBN  BHE' ";
          //if(!begDate.equals("")) s += " AND start >='"+begDate+"'";
          s += " AND (ended <=" + Util.sqlEscape(endDate)
                  + " OR (start <" + Util.sqlEscape(endDate) + " AND ended >=" + Util.sqlEscape(endDate) + ")) "
                  + // is entire in interval
                  "ORDER BY seedname,type,start";
          Util.stringBuilderReplaceAll(endDate, '/', '-');
          Util.prta("End = " + endDate + " 00:00:00.000");
          Timestamp end = Timestamp.valueOf(endDate + " 00:00:00.000");

          //  Read in the result set matching command line args
          dbstatus.setTimeout(900);   // Set long running for out operations as they may take some time.
          Util.prta(Util.ascdate() + " s=REPLACE INTO status.holdingshist " + s);
          n = dbstatus.executeUpdate("REPLACE INTO status.holdingshist " + s);
          Util.prta("holdings replace nrec=" + n);
          n = dbstatus.executeUpdate("DELETE FROM status.holdings WHERE ended <=" + Util.sqlEscape(endDate));
          Util.prta("Holdings # deleted=" + n);
          // Do 18 moths replacement
          s = "SELECT * FROM status.holdingshist WHERE ID>0 ";
          if (!selType.equals("")) {
            s += " AND type=" + Util.sqlEscape(selType);
          }
          if (!where.equals("")) {
            s += " AND " + where;
          }
          //s += " AND LEFT(seedname,10)='USCBN  BHE' ";
          //if(!begDate.equals("")) s += " AND start >='"+begDate+"'";
          s += " AND (ended <=" + Util.sqlEscape(endDate18Months)
                  + " OR (start <" + Util.sqlEscape(endDate18Months) + " AND ended >=" + Util.sqlEscape(endDate18Months) + ")) "
                  + // is entire in interval
                  "ORDER BY seedname,type,start";
          Util.stringBuilderReplaceAll(endDate18Months, '/', '-');
          end = Timestamp.valueOf(endDate18Months + " 00:00:00.000");
          Util.prta("End time 18 months is " + end.toString());
          Util.prta(Util.ascdate() + " s=REPLACE INTO status.holdingshist2 " + s);
          n = dbstatus.executeUpdate("REPLACE INTO status.holdingshist2 " + s);
          Util.prta("holdingshist replace nrec=" + n);
          n = dbstatus.executeUpdate("DELETE FROM status.holdingshist WHERE ended <=" + Util.sqlEscape(endDate18Months));
          Util.prta("Holdings # deleted=" + n);
          if (today.get(Calendar.DAY_OF_YEAR) % 15 == 7 && dbstatus.getVendor().equalsIgnoreCase("mysql")) {
            n = dbstatus.executeUpdate("OPTIMIZE TABLE holdings");
            Util.prta("Optimize holdings=" + n);
          }

          // Latency history update
          today = new GregorianCalendar();
          today.add(Calendar.HOUR, -30 * 24);
          endDate = Util.ascdate(today);
          Util.stringBuilderReplaceAll(endDate, '/', '-');
          Util.prta("Start move of older latency before " + endDate);
          n = dbstatus.executeUpdate("REPLACE INTO status.latencyhist SELECT * FROM status.latency WHERE time<"
                  + Util.sqlEscape(endDate));
          Util.prta("Replace of older latency before " + endDate + " Completed. n=" + n);
          n = dbstatus.executeUpdate("DELETE FROM status.latency WHERE time <" + Util.sqlEscape(endDate));      // Latency history update
          Util.prta("#older latency deleted=" + n);
          // do 18 months
          Util.prta("Start move of older latencyhist before " + endDate18Months + " to latencyhist2");
          n = dbstatus.executeUpdate("REPLACE INTO status.latencyhist2 SELECT * FROM status.latencyhist WHERE time<"
                  + Util.sqlEscape(endDate18Months));
          Util.prta("Replace of older latencyhist before " + endDate18Months + " Completed. n=" + n);
          n = dbstatus.executeUpdate("DELETE FROM status.latencyhist WHERE time <" + Util.sqlEscape(endDate18Months));      // Latency history update
          Util.prta("#older latencyhist deleted=" + n);
          if (today.get(Calendar.DAY_OF_YEAR) % 15 == 6 && dbstatus.getVendor().equalsIgnoreCase("mysql")) {
            n = dbstatus.executeUpdate("OPTIMIZE TABLE latency");
            Util.prta("Optimize latency=" + n);
          }

          // Do the same for dasstatus
          Util.prta("Start move of older dasstatus before " + endDate);
          n = dbstatus.executeUpdate("REPLACE INTO status.dasstatushist SELECT * FROM status.dasstatus WHERE time<"
                  + Util.sqlEscape(endDate));
          Util.prta("Replace of older dasstatus before " + endDate + " Completed. n=" + n);
          n = dbstatus.executeUpdate("DELETE FROM status.dasstatus WHERE time <" + Util.sqlEscape(endDate));
          Util.prta("#older dasstatus deleted=" + n);
          // Do 18 months
          Util.prta("Start move of older dasstatushist before " + endDate18Months + " to dasstatushist2");
          n = dbstatus.executeUpdate("REPLACE INTO status.dasstatushist2 SELECT * FROM status.dasstatushist WHERE time<"
                  + Util.sqlEscape(endDate18Months));
          Util.prta("Replace of older dasstatushist before " + endDate18Months + " Completed. n=" + n);
          n = dbstatus.executeUpdate("DELETE FROM status.dasstatushist WHERE time <" + Util.sqlEscape(endDate18Months));
          Util.prta("#older dasstatushist deleted=" + n);
          if (today.get(Calendar.DAY_OF_YEAR) % 15 == 3 && dbstatus.getVendor().equalsIgnoreCase("mysql")) {
            n = dbstatus.executeUpdate("OPTIMIZE TABLE dasstatus");
            Util.prta("Optimize dasstatus=" + n);
          }

          // Do the same for ping
          Util.prta("Start move of older ping before " + endDate);
          n = dbstatus.executeUpdate("REPLACE INTO status.pinghist SELECT * FROM status.ping WHERE time<"
                  + Util.sqlEscape(endDate));
          Util.prta("Replace of older ping before " + endDate + " Completed. n=" + n);
          n = dbstatus.executeUpdate("DELETE FROM status.ping WHERE time <" + Util.sqlEscape(endDate));
          Util.prta("#older ping deleted=" + n);
          // 18 months
          Util.prta("Start move of older pinghist before " + endDate18Months);
          n = dbstatus.executeUpdate("REPLACE INTO status.pinghist2 SELECT * FROM status.pinghist WHERE time<"
                  + Util.sqlEscape(endDate18Months));
          Util.prta("Replace of older pinghist before " + endDate18Months + " Completed. n=" + n);
          n = dbstatus.executeUpdate("DELETE FROM status.pinghist WHERE time <" + Util.sqlEscape(endDate18Months));
          Util.prta("#older pinghist deleted=" + n);
          if (today.get(Calendar.DAY_OF_YEAR) % 15 == 2 && dbstatus.getVendor().equalsIgnoreCase("mysql")) {
            n = dbstatus.executeUpdate("OPTIMIZE TABLE ping");
            Util.prta("Optimize ping=" + n);
          }

          // Do the same for qdping
          Util.prta("Start move of older qdping before " + endDate);
          n = dbstatus.executeUpdate("REPLACE INTO status.qdpinghist SELECT * FROM status.qdping WHERE time<"
                  + Util.sqlEscape(endDate));
          Util.prta("Replace of older qdping before " + endDate + " Completed. n=" + n);
          n = dbstatus.executeUpdate("DELETE FROM status.qdping WHERE time <" + Util.sqlEscape(endDate));
          Util.prta("#older qdping deleted=" + n);
          // 18 Months
          Util.prta("Start move of older qdpinghist before " + endDate18Months);
          n = dbstatus.executeUpdate("REPLACE INTO status.qdpinghist2 SELECT * FROM status.qdpinghist WHERE time<"
                  + Util.sqlEscape(endDate18Months));
          Util.prta("Replace of older qdpinghist before " + endDate18Months + " Completed. n=" + n);
          n = dbstatus.executeUpdate("DELETE FROM status.qdpinghist WHERE time <" + Util.sqlEscape(endDate18Months));
          Util.prta("#older qdpinghist deleted=" + n);
          if (today.get(Calendar.DAY_OF_YEAR) % 15 == 1 && dbstatus.getVendor().equalsIgnoreCase("mysql")) {
            n = dbstatus.executeUpdate("OPTIMIZE TABLE qdping");
            Util.prta("Optimize qdping=" + n);
          }

          if (doFetchlist) {
            n = dbedge.executeUpdate("REPLACE INTO fetcher.fetchlisthist SELECT * "
                    + "FROM fetcher.fetchlist WHERE status!='open' AND start<" + Util.sqlEscape(endDate));
            Util.prta("Replace fetchlist for non 'open' before " + endDate + " Completed. n=" + n);
            n = dbedge.executeUpdate("DELETE FROM fetcher.fetchlist WHERE status!='open' AND start <" + Util.sqlEscape(endDate));
            Util.prta("#non 'open' fetchlist deleted=" + n);
            if (today.get(Calendar.DAY_OF_YEAR) % 15 == 11 && dbstatus.getVendor().equalsIgnoreCase("mysql")) {
              n = dbedge.executeUpdate("OPTIMIZE TABLE fetchlist");
              Util.prta("Optimize fetchlist=" + n);
            }
            
            // fetchlist RT
            n = dbedge.executeUpdate("REPLACE INTO fetcher.fetchlisthistrt SELECT * "
                    + "FROM fetcher.fetchlistrt WHERE start<" + Util.sqlEscape(endDate));
            Util.prta("Replace fetchlistrt before " + endDate + " Completed. n=" + n);
            n = dbedge.executeUpdate("DELETE FROM fetcher.fetchlistrt WHERE start <" + Util.sqlEscape(endDate));
            Util.prta("#non 'open' fetchlistrt deleted=" + n);
            if (today.get(Calendar.DAY_OF_YEAR) % 15 == 11 && dbstatus.getVendor().equalsIgnoreCase("mysql")) {
              n = dbedge.executeUpdate("OPTIMIZE TABLE fetchlistrt");
              Util.prta("Optimize fetchlistrt=" + n);
            }
          }
        }
        // Run a consolidate on each component found
        Iterator itr = stats.iterator();
        int total = 0;
        int totalrec = 0;
        int totalseed = 0;
        int back = 7;
        if (conDate.equals("")) {
          if (today.get(Calendar.DAY_OF_YEAR) % 100 == 8) {
            back = 3000;
          }
          if (today.get(Calendar.DAY_OF_YEAR) % 15 == 0) {
            back = 180;
          }
          today.add(Calendar.HOUR, -back * 24);
          Util.prt("Limit consolidate to " + back + " days.");
          endDate = Util.ascdate(today);
          Util.stringBuilderReplaceAll(endDate, '/', '-');
        } else {
          endDate = Util.clear(endDate).append(conDate);
        }
        HoldingArray ha = new HoldingArray();
        Holding.useHistory();           // This makes all holdings refer to history table!
        long queryTotal = 0;
        long consolidateTotal = 0;
        long purgeTotal = 0;
        long lastTime;
        while (itr.hasNext()) {
          seedname = (String) itr.next();
          seedname = (seedname + "            ").substring(0, 12);
          totalseed++;
          s = "SELECT * FROM status.holdingshist WHERE seedname='" + seedname + "'"
                  + " AND ended>='" + endDate + "' ORDER BY type,start";
          //if(dbg) Util.prta(Util.ascdate()+" s="+s);
          long startTime = System.currentTimeMillis();
          ResultSet rs = dbstatus.executeQuery(s);

          // for each returned row, put it in a HoldingArray and consolidate it.
          lastTime = 0;
          while (rs.next()) {
            totalrec++;
            ha.addEnd(rs);
            long end = rs.getTimestamp("ended").getTime();
            if (end > lastTime) {
              lastTime = end;
            }
          }

          // To set lastData, check in holdings also
          s = "SELECT * FROM status.holdings WHERE seedname='" + seedname + "'"
                  + " AND ended>='" + endDate + "' ORDER BY type,start";
          //if(dbg) Util.prta(Util.ascdate()+" s="+s);
          rs = dbstatus.executeQuery(s);

          // for each returned roll from holdings, set a later date if present
          while (rs.next()) {
            long end = rs.getTimestamp("ended").getTime();
            if (end > lastTime) {
              lastTime = end;
            }
          }
          n = ha.getSize();
          if (n > 0) {
            queryTotal += System.currentTimeMillis() - startTime;
            if (n > 20000 || (System.currentTimeMillis() - startTime) > 20000) {
              Util.prta(seedname + " nrec=" + Util.leftPad("" + n, 8) + " "
                      + Util.leftPad("" + (System.currentTimeMillis() - startTime), 8) + " ms");
            }
            startTime = System.currentTimeMillis();
            while(ha.consolidate(100000, true) > 0) {}
            if (n != ha.getSize()) {
              if (totalseed % 500 == 0 || n - ha.getSize() > 100) {
                Util.prta("  ***  " + seedname + " n=" + ha.getSize() + " was " + n + " diff=" + (n - ha.getSize())
                        + " ncomb=" + total + " #=" + totalseed + " of " + stats.size());
              }
              total = total + n - ha.getSize();
            }
            consolidateTotal += System.currentTimeMillis() - startTime;
            startTime = System.currentTimeMillis();
            ha.purgeOld(0);
            purgeTotal += System.currentTimeMillis() - startTime;
          }
          try (ResultSet rs3 = dbedge.executeQuery("SELECT lastData FROM edge.channel where channel = '" + seedname + "'")) {
            rs3.next();
            Timestamp lastT = rs3.getTimestamp("lastdata");
            if (lastT.getTime() < lastTime) {
              lastT.setTime(lastTime);
              int z = dbedge.executeUpdate("UPDATE edge.channel set lastdata='" + lastT.toString() + "' where channel='" + seedname + "'");
            }
          }
        }
        Util.prta("Chans processed=" + totalseed + " recs purged=" + total + " " + totalrec + " recs processed.");
        Util.prta("QueryTime = " + (queryTotal / 1000L) + " consolidate time=" + (consolidateTotal / 1000L)
                + " purge time=" + (purgeTotal / 1000L));
        today = new GregorianCalendar();
        today.add(Calendar.DATE, -45);
        endDate = Util.ascdate(today);
        Util.stringBuilderReplaceAll(endDate, '/', '-');
        int ndelete = dbstatus2.executeUpdate("DELETE FROM status.holdingshist where ended<'" + endDate + "' AND "
                + " NOT type regexp 'C.'");
        Util.prta("Purge of holdings hist telemetry over 45 days old " + endDate + "=" + ndelete + " recs.");
        DBConnectionThread.shutdown();
        Util.sleep(10000);
        Util.prt("Try to exit : \n" + Util.getThreadsString());

        Util.exit(0);

      } catch (SQLException e) {
        Util.prt("SQLError=" + e.getMessage());
        Util.SQLErrorPrint(e, " Main SQL unhandled=");
        SendEvent.edgeSMEEvent("MoveHoldSQL", "SQL error occured in MoveToHistory ", "MoveToHist");
        e.printStackTrace();
        System.err.println("SQLException  on  getting test Holdings");
      }
    }
    DBConnectionThread.shutdown();
    Util.sleep(10000);
    Util.prt("Try to exit after error : \n" + Util.getThreadsString());
    Util.exit(0);
  }
}
