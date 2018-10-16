/*
 * Copyright 2010, United States Geological Survey or
 * third-party contributors as indicated by the @author tags.
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package gov.usgs.anss.net;

import gov.usgs.anss.db.DBConnectionThread;
import gov.usgs.anss.edgethread.MemoryChecker;
import gov.usgs.anss.util.SeedUtil;
import gov.usgs.anss.util.SimpleSMTPThread;
import gov.usgs.anss.util.Util;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.GregorianCalendar;

/**
 *
 * @author davidketchum
 */
public class MakeHoldingSummary {

  private static final DecimalFormat df2 = new DecimalFormat("00");
  private static HoldingsBySize lengthCompare;

  public static void main(String[] args) {
    Util.setProcess("MakeHoldingSummary");
    Util.init("edge.prop");
    Util.setNoInteractive(true);
    Util.setModeGMT();
    String type = "SM";
    int year = 0;
    int doy = 0;
    lengthCompare = new HoldingsBySize();
    for (int i = 0; i < args.length; i++) {
      if (args[i].equals("-t")) {
        type = args[i + 1];
      }
      if (args[i].equals("-b")) {
        String[] parts = args[i + 1].split(",");
        year = Integer.parseInt(parts[0]);
        doy = Integer.parseInt(parts[1]);
      }
      if (args[i].equals("-h")) {
        Util.setProperty("StatusDBServer", args[i + 1]);
      }
      if (args[i].equals("-?")) {
        Util.prt("MoveToHistory [-t TT][-b yyyy,doy][-h host.name");
        Util.prt("    -t  TT         The sumamary type (def=SM)");
        Util.prt("    -b  yyyy,doy  DOY to build");
        Util.prt("    -h host.name  The dotted address of the MySQL server with status.holdings");
        System.exit(0);
      }
    }
    if (year == 0 || doy == 0) {
      Util.prt("You must use the -b switch");
      System.exit(0);
    }
    DBConnectionThread dbro = null;
    DBConnectionThread dbupdate = null;
    Util.prt("Start analysis : StatusDBServer=" + Util.getProperty("StatusDBServer") + " " + year + "_" + doy + " type=" + type);
    try {
      dbro = new DBConnectionThread(Util.getProperty("StatusDBServer"), "readonly", "status", false, false, "dbstatus", Util.getOutput());
      if (!DBConnectionThread.waitForConnection("dbstatus")) {
        if (!DBConnectionThread.waitForConnection("dbstatus")) {
          if (!DBConnectionThread.waitForConnection("dbstatus")) {
            if (!DBConnectionThread.waitForConnection("dbstatus")) {
              if (!DBConnectionThread.waitForConnection("dbstatus")) {
                Util.prta("Could not connect to MySQL abort run");
                SimpleSMTPThread.email(Util.getProperty("emailTo"), "MoveToHistory no mysql conn",
                        Util.ascdate() + " " + Util.asctime() + " MoveToHistory on " + Util.getNode()
                        + " Failed to connection to MySQL on " + Util.getProperty("StatusDBServer"));
              }
            }
          }
        }
      }
      dbupdate = new DBConnectionThread(Util.getProperty("StatusDBServer"), "update", "status", false, false, "dbupdate", Util.getOutput());
      if (!DBConnectionThread.waitForConnection("dbupdate")) {
        if (!DBConnectionThread.waitForConnection("dbupdate")) {
          if (!DBConnectionThread.waitForConnection("dbupdate")) {
            if (!DBConnectionThread.waitForConnection("dbupdate")) {
              if (!DBConnectionThread.waitForConnection("dbupdate")) {
                Util.prta("Could not connect to MySQL abort run");
                SimpleSMTPThread.email(Util.getProperty("emailTo"), "MoveToHistory no mysql conn",
                        Util.ascdate() + " " + Util.asctime() + " MoveToHistory on " + Util.getNode()
                        + " Failed to connection to MySQL on " + Util.getProperty("StatusDBServer"));
              }
            }
          }
        }
      }
      Holding.setHoldingsConnection(dbupdate);
    } catch (InstantiationException e) {
      Util.prt("instantiation exception e=" + e.getMessage());
      System.exit(0);
    }
    MemoryChecker.checkMemory(true);
    int julian = SeedUtil.toJulian(year, doy);
    int[] ymd = SeedUtil.fromJulian(julian);
    int[] ymd1 = SeedUtil.fromJulian(julian + 1);
    GregorianCalendar lower = new GregorianCalendar(ymd[0], ymd[1] - 1, ymd[2]);
    lower.setTimeInMillis(lower.getTimeInMillis() / 86400000L * 86400000L);

    String s = "SELECT * FROM holdingshist WHERE type ='CW' AND NOT (start >'"
            + ymd1[0] + "-" + df2.format(ymd1[1]) + "-" + df2.format(ymd1[2]) + "' OR "
            + "ended <'" + ymd[0] + "-" + df2.format(ymd[1]) + "-" + df2.format(ymd[2]) + "') ORDER BY seedname,type,start";
    String lastStation = "";
    MemoryChecker.checkMemory(true);
    HoldingArray ha = new HoldingArray();
    Holding.useSummary();
    Holding.setDoNotAutoWrite();
    try {
      Util.prta("Start delete");
      int nval = dbupdate.executeUpdate("DELETE FROM holdingssummary WHERE start >= '"
              + ymd[0] + "-" + df2.format(ymd[1]) + "-" + df2.format(ymd[2]) + "' AND "
              + "ended <='" + ymd1[0] + "-" + df2.format(ymd1[1]) + "-" + df2.format(ymd1[2]) + "'");
      Util.prta("Start select for " + year + "," + doy + " s=" + s + " #deleted=" + nval);
      dbro.setTimeout(1200);
      ResultSet rs = dbro.executeQuery(s);
      Util.prta("Start processing records");
      int nrec = 0;
      int outrec = 0;
      int nbuild = 0;
      while (rs.next()) {
        nrec++;
        String seedname = rs.getString("seedname");
        if (lastStation.equals("")) {
          lastStation = seedname.substring(0, 7);
        }
        long start = rs.getTimestamp("start").getTime() / 1000 * 1000 + rs.getShort("start_ms");
        long end = rs.getTimestamp("ended").getTime() / 1000 * 1000 + rs.getShort("end_ms");
        if (!seedname.substring(0, 7).equals(lastStation)) {
          //Util.prta("process "+lastStation+" nrec="+nrec);
          outrec += process(ha, lastStation, nbuild, lower);
          ha.clearNoWrite();
          lastStation = seedname.substring(0, 7);
          nbuild = 0;
        }
        //Util.prta("Add on "+lastStation+" "+type+" "+Util.asctime2(start)+" "+(end-start)/1000.);
        ha.addOn(lastStation + "MHZ  ", type, start, end - start);
        nbuild++;
        //if(nrec % 1000 == 0) Util.prta(nrec+" processed "+" outrec="+outrec+" "+lastStation);
      }
      process(ha, lastStation, nbuild, lower);  // process the ending group
      Util.prt("Processed " + nrec + " records. outrec=" + outrec);
    } catch (SQLException e) {
      Util.SQLErrorPrint(e, "Trying to read holdings");
    }
    Util.prt(Util.getThreadsString());
    DBConnectionThread.shutdown();
    System.exit(0);
  }

  private static int process(HoldingArray ha, String lastStation, int nbuild, GregorianCalendar lower) {
    Holding[] h = ha.getHoldings();
    for (int i = 0; i < h.length; i++) {
      if (h[i] != ha.hempty) {
        if (h[i].getStart() < lower.getTimeInMillis()) {
          h[i].start = lower.getTimeInMillis();
        }
        if (h[i].getEnd() > lower.getTimeInMillis() + 86400000L) {
          h[i].finish = lower.getTimeInMillis() + 86400000L;
        }
      }
    }
    ha.consolidate(10000000);
    h = ha.getHoldings();
    int n = 0;
    for (int i = 0; i < h.length; i++) {
      if (h[i] == ha.hempty) {
        n = i;
        break;
      }
    }
    double sec = 0.;
    int nh = n;
    if (n > 9) {
      Arrays.sort(h, 0, n, lengthCompare);
      //for(int i=0; i<n; i++) Util.prt("start["+i+"]"+h[i]+" "+(h[i].getLengthInMillis()/1000.));
      for (int i = 5; i < n; i++) {
        if (h[i].getLengthInMillis() < 900000 || i >= 10) {
          sec += h[i].getLengthInMillis() / 1000.;
          h[i].delete();
          h[i] = ha.hempty;
        } else {
          nh = i + 1;
        }
      }
      //for(int i=0; i<n; i++) Util.prt("limit["+i+"]"+h[i]+" "+(h[i].getLengthInMillis()/1000.));
      //for(int i=0; i<n; i++) Util.prt("Final["+i+"]="+h[i]+" "+(h[i].getLengthInMillis()/1000.));
    }
    int nwrite = 0;
    try {
      for (int i = 0; i < nh; i++) {
        if (h[i] != ha.hempty) {
          h[i].manualWrite();
          nwrite++;
        }// holdings do not write because we turned auto write off
      }
    } catch (SQLException e) {
      Util.SQLErrorPrint(e, "Trying to write holding summary");
    }
    if (n > 9) {
      Util.prt(" **** Write records for " + nwrite + " of " + (n + 1) + " discard " + df2.format(sec) + " secs from " + nbuild + " on " + lastStation);
    }
    return nh;
  }
}
