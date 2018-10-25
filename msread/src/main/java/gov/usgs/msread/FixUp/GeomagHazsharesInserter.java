/*
 *  This software is in the public domain because it contains materials 
 *  that originally came from the United States Geological Survey, 
 *  an agency of the United States Department of Interior. For more 
 *  information, see the official USGS copyright policy at 
 *  http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.msread.FixUp;

import gov.usgs.anss.util.Util;
import gov.usgs.anss.edge.RawInputClient;
import gov.usgs.anss.cwbqueryclient.EdgeQueryClient;
import gov.usgs.anss.db.DBConnectionThread;
import gov.usgs.anss.seed.MiniSeed;
import gov.usgs.anss.cwbqueryclient.ZeroFilledSpan;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Calendar;
import java.util.ArrayList;

import java.io.IOException;
import java.io.FileNotFoundException;
import java.io.BufferedReader;
import java.io.FileReader;
import java.sql.SQLException;
import java.sql.ResultSet;

/**
 * This is a utility to fill in old gaps in Geomag data by looking at the files created on the
 * stations and stored eventually in the
 * //hazshares/geomag/pub/observatories/PCDCPData/STAT/YYYYData/min/STATYYYYDOY.min files.
 *
 * <PRE>
 * Switch   arg                              Description
 * For all uses :
 * -basedir path                          The diretory path to the geomag directory - somethingk like "/Volumes/geomag" or "/mnt/geomag"
 * -raw     edgeIP:edgePort:cwbIP:cwbport The IPs and ports to the RawInputServers to use when the data is fetched. Not present, do not insert
 * For usage to try a single gap manually
 * -s NSCL                                The 12 character channel NSCL
 * -d dur                                 The duration in seconds
 * -b date                                Starting time of the gap (yyyy-mm-yy hh:mm:ss.ddd or yyyy,doy hh:mm:ss.dddd)
 * For using the exsiting fetchlist of 'nodata' to run a list of gaps
 * -fetchlist IP.ADD:SQL MySQL server address and a SQL expression like "select_*_FROM_Fetcher.fetchlist_WHERE_status='nodata'...
 *
 * </PRE>
 *
 * @author U.S. Geological Survey  <ketchum at usgs.gov>
 */
public final class GeomagHazsharesInserter {

  private boolean dbg;
  private final GregorianCalendar g = new GregorianCalendar();
  private final GregorianCalendar gtime = new GregorianCalendar();
  private final String cwbip;
  //private final String edgeip;
  //private final int cwbport;
  //private final int edgeport;
  private RawInputClient raw;
  private final int[] samples = new int[1];

  public GeomagHazsharesInserter(String cwbip, int cwbport, String edgeip, int edgeport) {
    if (cwbip != null) {
      raw = new RawInputClient("GeoMag", edgeip, edgeport, cwbip, cwbport);
    }
    this.cwbip = cwbip;
    //this.cwbport = cwbport;
    //this.edgeip = edgeip;
    //this.edgeport = edgeport;
  }

  public int doResultSet(ResultSet rs, String basedir) throws SQLException {
    int ninsert = 0;
    while (rs.next()) {
      String seedname = rs.getString("seedname");
      long start = rs.getTimestamp("start").getTime() + rs.getInt("start_ms");
      double duration = rs.getDouble("duration");
      char type = seedname.charAt(9);

      int nin = doGap(seedname, start, duration, type, basedir);
      if (nin > 0) {
        int expected = (int) Math.round(duration / 60.);
        int offset = nin + (int) Math.round((start % 86400000L) / 60000.);
        Util.prt("*" + seedname + " " + Util.toDOYString(start) + " type=" + type + " dur=" + duration + " off=" + offset + " " + expected + " df=" + (expected - nin) + " #ins=" + nin + (expected - nin == 0 ? " * " : ""));
      }
      ninsert += nin;
    }
    return ninsert;
  }

  public int doGap(String seedname, long start, double duration, char type, String basedir) {
    g.setTimeInMillis(start);
    String station = seedname.substring(2, 7).trim();
    String dir = basedir + station + "/" + g.get(Calendar.YEAR) + "Data/min/";
    int doy = g.get(Calendar.DAY_OF_YEAR);
    long baseTime = (start - 10) / 86400000L * 86400000L; // occasionnaly we get 1 ms add on, compensate for that
    long end = start + (long) (duration * 1000.) -10;   // come up just short of next sample
    // Read in the data from the server to make sure we do not put in anything that already exists
    ZeroFilledSpan span = null;
    String query = "-h " + cwbip + " -p 2000"+ " -b " + Util.stringBuilderReplaceAll(Util.ascdatetime2(start - 600000), " ", "-")
            + " -d " + (duration + 1200.) + " -s " + seedname.replaceAll(" ", "-") + " -t null";
    ArrayList<ArrayList<MiniSeed>> mss = EdgeQueryClient.query(query);
    if (mss != null) {
      for (ArrayList<MiniSeed> ms : mss) {
        if (!ms.isEmpty()) {
          span = new ZeroFilledSpan(ms, 2147000000);
        }
      }
    }
    int ninsert = 0;
    for (;;) {
      String file = station + g.get(Calendar.YEAR) + Util.df3(doy) + ".min";
      try {
        long time;
        if(dbg) {
          Util.prta("Open file " + dir + file + " " + seedname + " " + Util.ascdatetime2(start) + " dur=" + duration + " type=" + type);
        }
        int lastninsert;
        try (BufferedReader in = new BufferedReader(new FileReader(dir + file))) {
          lastninsert=ninsert;
          String header = in.readLine();
          header = header.replaceAll("  ", " ");
          header = header.replaceAll("  ", " ");
          header = header.replaceAll("  ", " ");
          header = header.replaceAll("  ", " ");
          header = header.replaceAll("  ", " ");
          String[] parts = header.split("\\s");
          String stat = parts[0];
          int year = Integer.parseInt(parts[1]);
          int day = Integer.parseInt(parts[2]);
          String date = parts[3];
          String chans = parts[4];
          String units = parts[5];
          double factor;
          try {
            double nTesla = Double.parseDouble(units.replaceAll("nT", ""));
            factor = nTesla / 0.001;        // This is the factor to convert to 0.001 nT
          } catch (NumberFormatException e) {
            Util.prta("************** Could not convert units=" + units + " skip this file =" + dir + file);
            in.close();
            continue;
          }
          if(dbg) {
            Util.prta("Conversion factor to nT is " + factor + " input units=" + units);
          }
          String scnl = "";
          String chan = "";
          int col = -1;
          for (int i = 0; i < chans.length(); i++) {
            if (type == chans.charAt(i)) {
              col = i + 1;
              break;
            }
          }
          String line;
          time = 0;
          while ((line = in.readLine()) != null) {
            int minute = Integer.parseInt(line.substring(0, 4));
            time = baseTime + minute * 60000;
            if (time >= start && time < end) {
              line = line.replaceAll("  ", " ");
              line = line.replaceAll("  ", " ");
              line = line.replaceAll("  ", " ");
              line = line.replaceAll("  ", " ");
              line = line.replaceAll("  ", " ");
              line = line.replaceAll("  ", " ");
              line = line.replaceAll("  ", " ");
              String[] lineparts = line.split("\\s");
              double value = Integer.parseInt(lineparts[col]);
              if (value == 9999999) {
                if (dbg) {
                  Util.prta("Skip " + Util.ascdatetime2(time) + " col=" + col + " scnl=" + seedname
                          + " val=" + lineparts[col] + " *SKIP* " + line);
                }
              } else {
                Util.prta("Insert " + Util.ascdatetime2(time) + " col=" + col + " scnl=" + seedname
                        + " val=" + lineparts[col] + " " + line);
                if (raw != null) {
                  gtime.setTimeInMillis(time);
                  samples[0] = (int) Math.round(value * factor);
                  if (span == null) {
                    raw.send(seedname, 1, samples, gtime, 1./60., 0, 0, 0, 0);    // No query data, assume its good

                  } else {
                    int i = span.getIndexOfTime(gtime);
                    if(i >= span.getNsamp() || i < 0) {
                      raw.send(seedname, 1, samples, gtime, 1./60., 0, 0, 0, 0);  // Not in query, so assume it good
                    }
                    else {
                      int data = span.getData(i);
                      if (data == 2147000000) {
                        raw.send(seedname, 1, samples, gtime, 1./60., 0, 0, 0, 0);
                      } else {
                        if(dbg) {
                          Util.prta("Skip have data " + Util.ascdatetime2(time)
                                + " data=" + data + " samp=" + samples[0] + " scnl=" + seedname + " val=" + lineparts[col] + " " + line);
                        }
                      }
                    }
                  }
                }
                ninsert++;
              }
            }
            if (time > end) {
              break;
            }
          } // while read a line
        }     // try on buffered reader    
        if (raw != null) {
          try {
            if(lastninsert != ninsert) {    // If we put anything in, then force it out.
              raw.forceout(seedname);
            }
          } catch (IOException expected) {
          }
        }         
        if (time > end) {
          break;
        }

      } catch (FileNotFoundException e) {
        if(dbg) {
          Util.prt("File not found e=" + e);
        }
      } catch (IOException e) {
        e.printStackTrace();
      }
      doy++;
      baseTime += 86400000L;
      if (baseTime > end) {
        break;
      }

    }// For each day
    if (raw != null) {
      try {
        if(ninsert > 0) {    // If we put anything in, then force it out.
          raw.forceout(seedname);
        }
      } catch (IOException expected) {
      }
    }     
    return ninsert;
  }

  public static void main(String[] args) {
    Util.setModeGMT();
    long start = 0;
    double duration = 0.;
    String station = null;
    String seedname = null;
    String basedir = "/mnt/geomag/";
    Date st;
    boolean dbg = false;
    char type = ' ';
    String cwbip = null;
    String edgeip = null;
    int cwbport = 0;
    int edgeport = 0;
    String mysqlserver = null;
    String sql = null;
    String table = null;
    //if(args.length == 0) args = "-s NTSJG--MVZR0 -b 2017,277,21:13:00.002 -d 266399.997".split("\\s");
    if (args.length == 0) {
      args
              = ("-basedir /Volumes/geomag/pub/observatories/PCDCPData/ "
                      + "-fetchlist hazdb:select_*_FROM_fetcher.fetchlist_WHERE_status='nodata'"
                      + "_AND_start>'2017-01-01'_AND_seedname_regexp_'NT...__M[VS][FEHZ]R0'_ORDER_BY_seedname,start "
                      + "-raw mage2:7989:mage2:7989").split("\\s");
    }

    for (int i = 0; i < args.length; i++) {
      switch (args[i]) {
        case "-s":
          seedname = args[i + 1].replaceAll("-", " ");
          station = seedname.substring(2, 7).trim();
          type = seedname.charAt(9);
          i++;
          break;
        case "-d":
          duration = Double.parseDouble(args[i + 1]);
          i++;
          break;
        case "-b":
          st = Util.stringToDate2(args[i + 1]);
          start = st.getTime();
          i++;
          break;
        case "-dbg":
          dbg = true;
          break;
        case "-fetchlist":
          String[] parts = args[i + 1].split(":");
          mysqlserver = parts[0];
          sql = parts[1].replaceAll("_", " ");
          i++;
          break;
        case "-basedir":
          basedir = args[i + 1];
          i++;
          break;
        case "-raw":
          parts = args[i + 1].split(":");
          edgeip = parts[0];
          if (parts.length > 1) {
            edgeport = Integer.parseInt(parts[1]);
          }
          if (parts.length > 2) {
            cwbip = parts[2];
          }
          if (parts.length > 3) {
            cwbport = Integer.parseInt(parts[3]);
          }
          i++;
          break;
        default:
          Util.prt("Did not decode switch = " + args[i] + " at " + i);
          Util.exit(0);
          break;
      }
    }
    GeomagHazsharesInserter insertor = new GeomagHazsharesInserter(cwbip, cwbport, edgeip, edgeport);
    if (cwbip == null && edgeip == null) {
      Util.prta("******* Warning: no data will be inserted - test run only *******");
    }

    // If sql mode, runt the query and call insertor.doResultSet(), if not, do the individual one
    if (sql != null) {
      try {
        DBConnectionThread dbconn = new DBConnectionThread(mysqlserver, "fetcher", "ro", "readonly",
                false, false, "fetch", "mysql", Util.getOutput());
        if (!dbconn.waitForConnection()) {
          if (!dbconn.waitForConnection()) {
            if (!dbconn.waitForConnection()) {
              if (!dbconn.waitForConnection()) {
                if (!dbconn.waitForConnection()) {
                  Util.prt("Did ont connect!");
                  System.exit(0);
                }
              }
            }
          }
        }
        ResultSet rs = dbconn.executeQuery(sql);
        insertor.doResultSet(rs, basedir);
      } catch (InstantiationException | SQLException e) {
        e.printStackTrace();
      }
      System.exit(0);
    }
    if (seedname == null || start == 0 || duration <= 0. || type == ' ') {
      Util.prt("You must supply all parameters ! nscl=" + seedname + " start=" + start + " dur=" + duration + " band=" + type);
      Util.exit(0);
    }
    insertor.doGap(seedname, start, duration, type, basedir);

    Util.prt("End of execution");
  }
}
