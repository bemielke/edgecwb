/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.net;
//import java.util.Arrays;

import gov.usgs.anss.db.DBConnectionThread;
import gov.usgs.anss.util.Util;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Comparator;

/**
 * This is a utility for scanning holdings and consolidating any abutting or overlapping holdings.
 * Such holdings do exist as the data are inserted in various ways at various times and holdings
 * migrate from holdings, to holdingshist, to holdinghist2.
 *
 * <PRE>
 * switch   args    Description
 * -t       TT       The holdings type to consolidate (def=do all types)
 * -b     yyyy/mm/dd The earliest date to consider for consolidation (def=1990-01-01)
 * -e     yyyy/mm/dd The latest date to consider for consolitation (def=30 days ago)
 * -h     ip.of.db   The database URL to use (def=property StatusDBServer) form ip.adr/port:dbname:dbvendor:dbschema
 * -hist             Set the table to consolidate to holdings hist (def=holdings table)
 * -hist2            Set the table to consolidate to holdings hist (def=holdings table)
 * </pre>
 *
 * @author davidketchum
 */
public final class HoldingsConsolidate {

  /**
   * Creates a new instance of HoldingsConsolidate
   */
  public HoldingsConsolidate() {
  }

  private static void doHelp() {
    Util.prt("HoldingsConsolidate [-t TT][-b yyyy-mm-dd][-e yyyy-mm-dd][-h host.name");
    Util.prt("    -t TT         A two letter holdings type to include.");
    Util.prt("    -b yyyy-mm-dd The beginning date to include (records can lap the date)");
    Util.prt("    -e yyyy-mm-dd The ending date to include (records can lap this date) def=now-45days");
    Util.prt("    -h host.name  The dotted address of the MySQL server with status.holdings");
    Util.prt("    -hist         Use the holdings history file");
    Util.prt("    -hist2         Use the holdings history2 file");
    Util.prt("    -maxperday    If number of records for a channel exceeds the max records, purge each day down to this number");
    Util.prt("    -maxrecords    If number of records for a channel exceeds the max records, purge each day down to this number");
    Util.prt("    -safenets NN-N1_N2_N3 set the listed networks as safe from purging by max per day");
  }

  /**
   * @param args the command line arguments
   */
  public static void main(String[] args) {
    Util.init("edge.prop");
    String selType = "";
    String beginDate = "1990-01-01";
    GregorianCalendar end = new GregorianCalendar();
    end.add(Calendar.DATE, -30);
    String endDate = Util.ascdate(end).toString();
    String table = "holdings";
    int maxRecords = 0;
    int maxPerDay = 24;
    String safeNetworks = "US_IU_II_IW_CU_GS_NE";

    Util.setModeGMT();
    if (args.length == 0) {
      doHelp();
    }
    if (args.length == 0) {
      args = "-maxperday 0 -h cwbhy/3306:status:mysql:status -e 2018-02-01".split("\\s");
    }
    DBConnectionThread db = null;
    for (int i = 0; i < args.length; i++) {
      if (args[i].equals("-t")) {
        selType = args[i + 1];
      }
      if (args[i].equals("-b")) {
        beginDate = args[i + 1];
      }
      if (args[i].equals("-e")) {
        endDate = args[i + 1];
      }
      if (args[i].equals("-h")) {
        Util.setProperty("StatusDBServer", args[i + 1]);
      }
      if (args[i].equals("-hist")) {
        table = "holdingshist";
        Holding.useHistory();
      }
      if (args[i].equals("-hist2")) {
        table = "holdingshist2";
        Holding.useHistory2();
      }
      if (args[i].equals("-maxlimit")) {
        maxRecords = Integer.parseInt(args[i + 1]);
        i++;
      }
      if (args[i].equals("-maxperday")) {
        maxPerDay = Integer.parseInt(args[i + 1]);
        i++;
      }
      if (args[i].equals("-safenets")) {
        safeNetworks = args[i + 1];
        i++;
      }
      if (args[i].equals("-?")) {
        doHelp();
        System.exit(0);
      }
    }
    Util.prt(table + " consolidate maxperday=" + maxPerDay + " maxRec=" + maxRecords + " safe=" + safeNetworks
            + " ty=" + selType + " " + beginDate + "-" + endDate + " statusDB=" + Util.getProperty("StatusDBServer"));
    try {
      try {
        db = new DBConnectionThread(Util.getProperty("StatusDBServer"), "update", "status", true,
                false, "holding", Util.getOutput());
        if (!DBConnectionThread.waitForConnection("holding")) {
          if (!DBConnectionThread.waitForConnection("holding")) {
            if (!DBConnectionThread.waitForConnection("holding")) {
              Util.prt("COuld not connect to holdings " + Util.getProperty("StatusDBServer") + " "
                      + Util.getProperty("ServerStatus"));
              Util.exit(1);
            }
          }
        }
        Holding.setHoldingsConnection(db);
      } catch (InstantiationException e) {
        Util.prt("Could not open dbconnectionghread e=" + e);
        Util.exit(0);
      }
      HoldingArray ha = new HoldingArray();
      ArrayList<String> stats = new ArrayList<>(1000);
      Statement stmt = db.getConnection().createStatement(
              ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE);
      Statement stmtReplace = db.getConnection().createStatement(
              ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE);
      ResultSet rstat = stmt.executeQuery("SELECT DISTINCT seedname FROM status." + table
              //+ " WHERE seedname='DRSDD  BHZ'"
              + " ORDER BY seedname");
      String last = "";
      String seedname;
      while (rstat.next()) {
        stats.add(rstat.getString(1));  // put the station names on the list
      }
      rstat.close();

      //stats.add("DRSDD  BHZ");  //DEBUG if not using SQL
      //stats.add("DRSDD  BHN");  // DEBUG
      //stats.add("DRSDD  BHE");  // DEBUG
      int total = 0;
      int totalrec = 0;
      int totdel = 0;
      Util.prta(Util.ascdate() + "Consolidate: # of stations found=" + stats.size() + " table=" + table
              + " host=" + Util.getProperty("StatusDBServer") + " selType=" + selType + " beg=" + beginDate + " to " + endDate);
      for (int i = 0; i < stats.size(); i++) {
        //if(stats.get(i).indexOf("GTBOSA")>=0)
        //  Util.prt(stats.get(i));
        String s = "SELECT * FROM " + table + " WHERE seedname='" + stats.get(i) + "'";
        if (!selType.equals("")) {
          s += " AND type=" + Util.sqlEscape(selType);
        }
        //s += " AND LEFT(seedname,10)='USCBN  BHE' ";
        s += " AND start >'1971-01-01' AND ("
                + // preclude zero starts
                "(start <=" + Util.sqlEscape(beginDate) + " AND ended>=" + Util.sqlEscape(beginDate) + ") OR "
                + // laps be beginDate
                "(start <=" + Util.sqlEscape(endDate) + " AND ended >=" + Util.sqlEscape(endDate) + ") OR  "
                + // laps the endDate
                "(start >=" + Util.sqlEscape(beginDate) + " AND ended <=" + Util.sqlEscape(endDate) + ")) "
                + // is entire in interval
                "ORDER BY type,start";

        //Util.prta(Util.ascdate()+" s="+s);
        ResultSet rs = stmt.executeQuery(s);
        int lastTotal = totalrec;
        String lastType = "";
        while (rs.next()) {
          totalrec++;
          if (rs.getString("type").equalsIgnoreCase(lastType)) {
            ha.addEnd(rs);
          } else {
            if (!lastType.equals("")) {

              int n = ha.getSize();
              //Util.prta("      "+lastSeedName+" "+lastType+" nrec="+n);
              while (ha.consolidate(100000, true) > 0) {  // data is ordered by start date
              }
              if (n != ha.getSize()) {
                Util.prta("  *** " + stats.get(i) + " " + lastType + " n=" + ha.getSize() + " was " + n + " diff=" + (n - ha.getSize()));
                total = total + n - ha.getSize();
              }
              ha.purgeOld(0);       // write them all out
            }
            ha.addEnd(rs);
            lastType = rs.getString("type");
          }
        }
        int n = ha.getSize();
        while (ha.consolidate(100000, true) > 0) {
        }
        if (n != ha.getSize()) {
          Util.prta("  *** " + stats.get(i) + " " + lastType + " n=" + ha.getSize() + " was " + n + " diff=" + (n - ha.getSize()));
          total = total + n - ha.getSize();
        }
        // See if there are too many records
        String network = ha.getHolding(0).getSeedName().substring(0, 2);
        if (maxRecords > 0) {
          long temp = totdel;
          if (ha.getSize() > maxRecords && !safeNetworks.contains(network)) {
            totdel += ha.moveHackedUpToPurge(maxPerDay, stmtReplace, table);
            if (totdel != temp) {
              Util.prta(stats.get(i) + " purgeHack=" + (totdel - temp) + " totHack=" + totdel);
            }
          }
        }
        ha.close();
        if (totalrec - lastTotal > 1000 || i % 200 == 0) {
          Util.prta(stats.get(i) + " had " + (totalrec - lastTotal) + " recs. i=" + i + ((totalrec - lastTotal) > 100000 ? " **" : ""));
        }
      }     // end of for each station
      Util.prta(" total purged=" + total + " " + totalrec + " processed. purgeHackedUp=" + totdel);
      System.exit(1);
    } catch (SQLException e) {
      Util.SQLErrorPrint(e, " Main SQL unhandled=");
      e.printStackTrace();
      System.err.println("SQLException on getting test Holdings");
    }
    System.exit(1);
  }

}
