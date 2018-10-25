/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */

package gov.usgs.edge.report;

import gov.usgs.anss.db.DBConnectionThread;
import gov.usgs.anss.edge.RunList;
import gov.usgs.anss.edge.Run;
import gov.usgs.anss.util.FUtil;
import gov.usgs.anss.util.Util;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.GregorianCalendar;
import java.util.Iterator;
import java.util.TreeMap;

/**
 *
 * arg Values Description -c channelExp There are many channel possibilities 1) netre=REGEXP, 2)
 * WHERE SQL selection, 3) EXPECTED - use the expected flag -b date1:date2 Run the report for all of
 * these beginning dates to corresponding ending dates -e edate1:edate2 The ending dates for all of
 * the desired ranges -hz This flag says use only "?HZ" channel codes -cg If seedflags are "CG" or
 * if seed flags contain "G" but not "T" (trying to find only continuous channels)
 * <p>
 * 2017-10-08 Changes to make it work better for GeoMag reporting.
 *
 * @author U.S. Geological Survey  <ketchum at usgs.gov>
 */
public class Availability {

  static TreeMap<String, String> channels = new TreeMap<String, String>();
  static boolean dbg;
  static TreeMap<String, RunList> runlists = new TreeMap<String, RunList>();
  static ArrayList<String> netchans = new ArrayList<String>(1000);
  static DBConnectionThread mysqlmetaC, mysqlstatusC, mysqledgeC;
  static Statement mysqlmeta, mysqlstatus, mysqledge;
  //static RunList runs = new RunList(1000);
  static StringBuilder sb = new StringBuilder(1000);

  public static String report(String chans, String begins, String ends, String flags) {
    if (chans.equals("")) {
      chans = "WHERE channel regexp 'NT.....[SM]VHR0'"; //netre=IU|NM|US|CI|NC|UW|UU|LD|HV";
    }
    if (begins.equals("")) {
      begins = "2015-09-01";
    }
    if (ends.equals("")) {
      ends = "2016-10-01";
    }
    if (flags.equals("")) {
      flags = "";
    }
    Util.prt("chans=" + chans + " beg=" + begins + " ends=" + ends + " flags=" + flags);
    if (sb.length() > 0) {
      sb.delete(0, sb.length());
    }
    Util.setModeGMT();
    GregorianCalendar early;
    GregorianCalendar late;
    if (mysqlmeta == null) {
      try {
        mysqlmetaC = new DBConnectionThread(Util.getProperty("MetaDBServer"), "readonly", "metadata",
                false, false, "metadata", Util.getOutput());
        if (!mysqlmetaC.waitForConnection()) {
          if (!mysqlmetaC.waitForConnection()) {
            if (!mysqlmetaC.waitForConnection()) {
              if (!mysqlmetaC.waitForConnection()) {
                Util.prt("Did not connect to Metadata!");
                return "Could not connect to database " + Util.getProperty("MetaDBServer");
              }
            }
          }
        }

        mysqlmeta = DBConnectionThread.getConnection("metadata").createStatement();
        mysqlstatusC = new DBConnectionThread(Util.getProperty("StatusDBServer"), "readonly", "status", false, false, "status", Util.getOutput());
        if (!mysqlstatusC.waitForConnection()) {
          if (!mysqlstatusC.waitForConnection()) {
            if (!mysqlstatusC.waitForConnection()) {
              if (!mysqlstatusC.waitForConnection()) {
                Util.prt("Did not connect to Status!");
                return "Could not connect to database " + Util.getProperty("StatusDBServer");
              }
            }
          }
        }
        mysqlstatus = DBConnectionThread.getConnection("status").createStatement();

        mysqledgeC = new DBConnectionThread(Util.getProperty("DBServer"), "readonly", "edge", false, false, "edgeAvail", Util.getOutput());
        if (!mysqledgeC.waitForConnection()) {
          if (!mysqledgeC.waitForConnection()) {
            if (!mysqledgeC.waitForConnection()) {
              if (!mysqlstatusC.waitForConnection()) {
                Util.prt("Did not connect to Status!");
                return "Could not connect to database " + Util.getProperty("StatusDBServer");
              }
            }
          }
        }
        mysqledge = DBConnectionThread.getConnection("edgeAvail").createStatement();

      } catch (InstantiationException e) {
        Util.prta("InstantiationException opening edge database in MetaDataServer e=" + e.getMessage());
        System.exit(1);
      } catch (SQLException e) {
        Util.prt("SQL exception making statements d=" + e);

        e.printStackTrace();
      }
    }
    String sql;
    // No get the holdings in the date ranges.  Need to break up the dates and look for mins and max
    String[] starts = begins.split("[:;]");
    GregorianCalendar[] gstarts = new GregorianCalendar[starts.length];
    String[] stops = ends.split("[:;]");
    if (starts.length != stops.length) {
      Util.prt("Begining dates and ending dates must agree in number");
      return "**** begin and end do not agree in number";
    }
    GregorianCalendar[] gends = new GregorianCalendar[stops.length];
    long earliest = Long.MAX_VALUE;
    long latest = Long.MIN_VALUE;
    for (int i = 0; i < starts.length; i++) {
      gstarts[i] = new GregorianCalendar();
      gends[i] = new GregorianCalendar();
      Timestamp d = FUtil.stringToTimestamp(starts[i]);
      gstarts[i].setTimeInMillis(d.getTime() / 86400000L * 86400000L);
      d = FUtil.stringToTimestamp(stops[i]);
      gends[i].setTimeInMillis(d.getTime() / 86400000L * 86400000L);
      if (gstarts[i].getTimeInMillis() < earliest) {
        earliest = gstarts[i].getTimeInMillis();
      }
      if (gstarts[i].getTimeInMillis() > latest) {
        latest = gstarts[i].getTimeInMillis();
      }
      if (gends[i].getTimeInMillis() < earliest) {
        earliest = gends[i].getTimeInMillis();
      }
      if (gends[i].getTimeInMillis() > latest) {
        latest = gends[i].getTimeInMillis();
      }

    }
    early = new GregorianCalendar();
    late = new GregorianCalendar();
    early.setTimeInMillis(earliest);
    late.setTimeInMillis(latest);
    StringBuilder date1 = new StringBuilder(20);
    StringBuilder date2 = new StringBuilder(20);
    try {
      if (chans.equalsIgnoreCase("expected")) {
        sql = "SELECT channel FROM edge.channel WHERE (expected&1) != 0 ORDER BY channel";
        Util.prt(sql);
        try (ResultSet rs = mysqledge.executeQuery(sql)) {
          while (rs.next()) {
            String ch = rs.getString("channel");
            if (ch.length() < 12) {
              ch = (ch + "_______").substring(0, 12);
            }
            channels.put(ch, ch);
          }
        }
      } else if (chans.toLowerCase().indexOf("netre") == 0) {
        String[] parts = chans.split("=");
        if (parts.length != 2) {
          sb.append("***** NETRE=regexp does not contain two parts separted by equals sign=").append(chans);
          return sb.toString();
        }
        sql = "SELECT channel FROM edge.channel WHERE substring(channel,1,2) regexp '" + parts[1]
                + "' and lastdata>='" + Util.stringBuilderReplaceAll(Util.ascdate(earliest, Util.clear(date1)), "/", "-") + "' ORDER BY channel";
        Util.prt(sql);
        try (ResultSet rs = mysqledge.executeQuery(sql)) {
          while (rs.next()) {
            String ch = rs.getString("channel");
            if (ch.length() < 12) {
              ch = (ch + "_______").substring(0, 12);
            }
            channels.put(ch, ch);
          }
        }

      } else if (chans.toLowerCase().substring(0, 5).equals("where")) {
        sql = "SELECT channel FROM edge.channel " + chans + " ORDER BY channel";
        Util.prt(sql);
        try (ResultSet rs = mysqledge.executeQuery(sql)) {
          while (rs.next()) {
            String ch = rs.getString("channel");
            if (ch.length() < 12) {
              ch = (ch + "_______").substring(0, 12);
            }
            channels.put(ch, ch);
          }
        }

      } else {
        sb.append("**** Channels Expression makes no sense = ").append(chans).append("\n");
        return sb.toString();
      }

      if (flags.contains("-cg")) {
        Iterator<String> itr = channels.values().iterator();
        while (itr.hasNext()) {
          String ch = itr.next();
          sql = "SELECT seedflags FROM metadata.response where channel='" + ch.replaceAll("_", "").trim()
                  + "' and NOT effective>'" + Util.ascdate(late.getTimeInMillis(),Util.clear(date1)) 
                  + "' and NOT endingDate<'" + Util.ascdate(early.getTimeInMillis(), Util.clear(date2)) + "'";
          Util.prt(sql);
          ResultSet rs = mysqlmeta.executeQuery(sql);
          if (rs.next()) {
            String seedflags = rs.getString(1).toUpperCase();
            if (!(seedflags.contains("C") && seedflags.contains("G"))
                    || (seedflags.contains("G")) && (seedflags.contains("T"))) {
              if (dbg) {
                Util.prt("remove " + ch + " flags=" + seedflags);
              }
              itr.remove();
            }
          } else {
            itr.remove();
            if (dbg) {
              Util.prt("Channel not in metadata ch=" + ch);
            }
          }
        }
      }
      if (flags.contains("-hz")) {
        Iterator<String> itr = channels.values().iterator();
        while (itr.hasNext()) {
          String ch = itr.next();
          char c = ch.charAt(7);
          if ((c != 'S' && c != 'B' && c != 'H' && c != 'E') || !ch.substring(8, 10).equals("HZ")) {
            itr.remove();
            if (dbg) {
              Util.prt("Channel not HZ=" + ch);
            }
          }
        }
      }

      // Build up the inlist of channels
      StringBuilder inlist = new StringBuilder(100000);
      Util.prta("#channels=" + channels.size());
      Iterator<String> itr = channels.values().iterator();
      while (itr.hasNext()) {
        String ch = itr.next();
        inlist.append("'").append(ch).append("',");
      }
      if (inlist.length() >= 1) {
        inlist.deleteCharAt(inlist.length() - 1);
      }

      int nholdings = 0;
      String lastNet = "";
      Iterator<String> itr2 = channels.values().iterator();
      inlist.delete(0, inlist.length());
      while (itr2.hasNext()) {
        String channel = itr2.next();
        if (lastNet.equals("")) {
          lastNet = channel.substring(0, 2);
        }
        if (channel.substring(0, 2).equals(lastNet) && itr2.hasNext()) {
          inlist.append("'").append(channel.replaceAll("_", " ").trim()).append("',");
          netchans.add(channel);
        } else {
          if (!itr2.hasNext()) {
            inlist.append("'").append(channel.replaceAll("_", " ").trim()).append("',");
            netchans.add(channel);
          }
          inlist.deleteCharAt(inlist.length() - 1);
          runlists.clear();
          Util.prta("Start query for inlist=" + inlist);
          sb.append(Util.asctime2(System.currentTimeMillis())).append(" Start holdings query");
          sql = "SELECT seedname, start, start_ms, ended, end_ms from status.holdings "
                  + "WHERE type='CW' AND seedname IN(" + inlist.toString() + ") AND NOT (ended <='"
                  + Util.ascdate(early.getTimeInMillis(), Util.clear(date1)) + "' OR start >='" + Util.ascdate(late.getTimeInMillis(), Util.clear(date2)) + "') ORDER BY seedname,start, start_ms";
          Util.prt(sql);
          ResultSet rs = mysqlstatus.executeQuery(sql);
          RunList runs = null;
          String lastStat = "";
          while (rs.next()) {
            String ch = rs.getString("seedname");
            if (ch.length() < 12) {
              ch = (ch + "____").substring(0, 12);
            }
            String s = channels.get(ch);
            if (s == null) {
              continue;
            }
            if (runs == null || !ch.substring(0, 7).equals(lastStat)) {
              runs = runlists.get(ch.substring(0, 7));
              lastStat = ch.substring(0, 7);
            }
            if (runs == null) {
              runs = new RunList(100);
              runlists.put(ch.substring(0, 7), runs);
              lastStat = ch.substring(0, 7);
            }
            runs.addHolding(rs);
            nholdings++;
          }
          rs.close();
          sb.append(Util.asctime2(System.currentTimeMillis())).append(" Start holdingshist query");
          Util.prta("StartQuery for history");
          sql = "SELECT seedname, start, start_ms, ended, end_ms from status.holdingshist "
                  + "WHERE type='CW' AND seedname IN(" + inlist.toString() + ") AND NOT (ended <='"
                  + Util.ascdate(early.getTimeInMillis(), Util.clear(date1)) + "' OR start >='" 
                  + Util.ascdate(late.getTimeInMillis(), Util.clear(date2)) + "') ORDER BY seedname,start, start_ms";
          Util.prt(sql);
          rs = mysqlstatus.executeQuery(sql);
          while (rs.next()) {
            String ch = rs.getString("seedname");
            if (ch.length() < 12) {
              ch = (ch + "____").substring(0, 12);
            }
            String s = channels.get(ch);
            if (s == null) {
              continue;
            }
            if (runs == null || !ch.substring(0, 7).equals(lastStat)) {
              runs = runlists.get(ch.substring(0, 7));
              lastStat = ch.substring(0, 7);
            }
            if (runs == null) {
              runs = new RunList(100);
              runlists.put(ch.substring(0, 7), runs);
              lastStat = ch.substring(0, 7);
            }
            runs.addHolding(rs);
            nholdings++;
            if (nholdings % 10000 == 0) {
              Util.prta("Holdings " + nholdings);
            }
          }
          rs.close();
          sb.append(Util.asctime2(System.currentTimeMillis())).append(" Start holdingshist2 query");
          Util.prta("StartQuery for history2");
          sql = "SELECT seedname, start, start_ms, ended, end_ms from status.holdingshist2 "
                  + "WHERE type='CW' AND seedname IN(" + inlist.toString() + ") AND NOT (ended <='"
                  + Util.ascdate(early.getTimeInMillis(), Util.clear(date1)) + "' OR start >='" 
                  + Util.ascdate(late.getTimeInMillis(), Util.clear(date2)) + "') ORDER BY seedname,start, start_ms";
          Util.prt(sql);
          rs = mysqlstatus.executeQuery(sql);
          while (rs.next()) {
            String ch = rs.getString("seedname");
            if (ch.length() < 12) {
              ch = (ch + "____").substring(0, 12);
            }
            String s = channels.get(ch);
            if (s == null) {
              continue;
            }
            if (runs == null || !ch.substring(0, 7).equals(lastStat)) {
              runs = runlists.get(ch.substring(0, 7));
              lastStat = ch.substring(0, 7);
            }
            if (runs == null) {
              runs = new RunList(100);
              runlists.put(ch.substring(0, 7), runs);
              lastStat = ch.substring(0, 7);
            }
            runs.addHolding(rs);
            nholdings++;
            if (nholdings % 10000 == 0) {
              Util.prta("Holdings " + nholdings);
            }
          }
          rs.close();
          if(runlists.isEmpty()) {
            Util.prta("This run resulted in no runlist entries - either the dates or channel selection must be bad!");
            System.exit(0);
          }
          Util.prta("Start consolidation run");
          double totalTime = late.getTimeInMillis() - early.getTimeInMillis();
          Iterator<RunList> itr4 = runlists.values().iterator();
          try {
            try (PrintWriter out = new PrintWriter(lastStat.substring(0, 2) + "_"
                    + Util.stringBuilderReplaceAll(Util.ascdate(early.getTimeInMillis(), Util.clear(date1)), "/", "-") + "_"
                    + Util.stringBuilderReplaceAll(Util.ascdate(late.getTimeInMillis(), Util.clear(date2)), "/", "-") + ".runs")) {
              double total = 0.;
              while (itr4.hasNext()) {
                RunList r = itr4.next();
                int was = r.getRuns().size();
                int overlaps = r.consolidate();
                for (int i = 0; i < r.getRuns().size(); i++) {
                  Run run = r.getRuns().get(i);
                  long start = run.getStart();
                  long end = run.getEnd();
                  if(start < earliest) start = earliest; // trim to beginning
                  if(end > latest) end = latest;
                  total += (end - start)/1000.;
                  //total += r.getRuns().get(i).getLength();  // This was the old way where limits were not set.
                  if(i <= r.getRuns().size() -2) {
                    double gapFollows = (Math.max(r.getRuns().get(i + 1).getStart(), earliest) - end) / 1000.;
                    if(gapFollows < 0.  && Util.stringBuilderEqual(run.getSeedname(), r.getRuns().get(i+1).getSeedname()))
                      Util.prta("negative gap follows "+gapFollows);
                  }
                  out.println(run.getSeedname()+" "+Util.ascdatetime2(start, Util.clear(date1))+"-"+Util.ascdatetime2(end, Util.clear(date2)) +
                          Util.leftPad(Util.df23((end - start)/1000.),14) + 
                          (i <= r.getRuns().size() - 2 && Util.stringBuilderEqual(run.getSeedname(), r.getRuns().get(i+1).getSeedname())
                                  ? " gap follows " + Util.leftPad(Util.df23((Math.max(r.getRuns().get(i + 1).getStart(), earliest) - end) / 1000.),10): ""));
                }
                Util.prta(r.getRuns().get(0).getSeedname() + " has #runs=" + r.getRuns().size() + " was " + was
                        + " overlaps=" + overlaps + " " + df2.format(total / totalTime * 10.) + "%");
              }
            }
          } catch (IOException e) {
            Util.prt("IO error writing runs file");
          }
          sb.append(Util.asctime2(System.currentTimeMillis())).append(" Start reporting cycle nholdings=").append(nholdings).append(" net=").append(lastNet);
          if (runlists.size() > 0) {
            for (int i = 0; i < gstarts.length; i++) {
              Util.prta("start report for " + Util.ascdate(gstarts[i]) + " to " + Util.ascdate(gends[i]));
              itr = netchans.iterator();
              nextOne = (itr.next() + "    ").substring(0, 12);
              doStation(itr, runlists, gstarts[i], gends[i], flags);
            }
          }
          inlist.delete(0, inlist.length());
          inlist.append("'").append(channel).append("',");
          netchans.clear();
          netchans.add(channel);
          lastNet = channel.substring(0, 2);
        }
      }
    } catch (SQLException e) {
      Util.prt("SQLException e=" + e);
      e.printStackTrace();
    }
    return "Done.";
  }
  static String nextOne;
  public static final DecimalFormat df2 = new DecimalFormat("0.000");
  public static GregorianCalendar gmeta1 = new GregorianCalendar();
  public static GregorianCalendar gmeta2 = new GregorianCalendar();

  public static void doStation(Iterator<String> itr, TreeMap<String, RunList> runlist, GregorianCalendar gstart, GregorianCalendar gend, String flags) {
    StringBuilder s = new StringBuilder(10000);
    String check = "IUMAJO";
    String lastStation = "";
    String ans;
    int nchan = 0;
    double totpct = 0.;
    for (;;) {  // while in the same network
      if (!lastStation.equals(nextOne.substring(0, 7))) {
        s.append(lastStation.equals("") ? "" : "\n").append(nextOne.substring(0, 2).trim()).append(",").append(nextOne.substring(2, 7).trim());
        lastStation = nextOne.substring(0, 7);
        if (lastStation.contains(check)) {
          Util.prta("Got check=" + check);
        }
      }
      RunList runs = runlist.get(nextOne.substring(0, 7));
      ans = "";
      if (flags.contains("-md")) {
        try {
          RunList meta = new RunList(10);
          double duration;
          ResultSet rs = mysqlmeta.executeQuery("SELECT channel,effective,endingdate FROM response WHERE channel='"
                  + nextOne.replaceAll("_", " ").trim() + "'");
          while (rs.next()) {
            gmeta1.setTimeInMillis(rs.getTimestamp("effective").getTime() / 1000 * 1000);
            gmeta2.setTimeInMillis(rs.getTimestamp("endingdate").getTime() / 1000 * 1000);
            meta.add(nextOne, gmeta1, (gmeta2.getTimeInMillis() - gmeta1.getTimeInMillis()) / 1000.);
          }
          meta.consolidate();
          duration = (gend.getTimeInMillis() - gstart.getTimeInMillis()) / 1000.;
          if (meta.secsOverlap(gstart.getTimeInMillis(), duration) >= duration - 100.) {
            ans = "y";
          } else {
            ans = "n";
          }
        } catch (SQLException e) {
          Util.prt("SQLException looking for metadata");
        }
      }
      if (runs == null) {
        Util.prt("Did not return a tree entry for " + nextOne.substring(0, 7) + " is " + runlist.get(nextOne.substring(0, 7)));
        nchan++;
        s.append(",").append(nextOne.substring(10).replaceAll(" ", "_")).append(nextOne.substring(7, 10)).append(ans).append(",0.00");
      } else {
        double pct = runs.availability(nextOne, gstart, gend);
        nchan++;
        totpct += pct;
        s.append(",").append(nextOne.substring(10).replaceAll(" ", "_")).append(nextOne.substring(7, 10)).append(ans).append(",").append(df2.format(pct));
      }
      if (!itr.hasNext()) {
        break;
      }
      nextOne = (itr.next() + "    ").substring(0, 12);
    }
    s.append("\nNet Pct=").append(df2.format(totpct / nchan)).append("\n");
    try {
      try (PrintWriter out = new PrintWriter(lastStation.substring(0, 2) + "_"
              + Util.stringBuilderReplaceAll(Util.ascdate(gstart), "/", "-") + "_"
              + Util.stringBuilderReplaceAll(Util.ascdate(gend), "/", "-") + ".avail")) {
        out.println(s.toString());
      }
      Util.prt("For network " + lastStation.substring(0, 2) + " " + Util.ascdate(gstart) + "-" + Util.ascdate(gend) + " nextOne=" + nextOne + "\n" + s.toString());
    } catch (IOException e) {
      Util.prt("IOException writing results");
    }
  }

  /**
   * Main for testing and parsing parameters
   *
   * @param args
   */
  public static void main(String[] args) {
    Util.init("edge.prop");
    //String chans = "netre=US|AK|CI|IU";
    //String begins="2011-01-01:2011-06-01";
    //String ends = "2011-07-01:2011-07-01";
    String chans = "";
    String begins = "";
    String ends = "";
    String flags = "";
    if (args.length == 0) {
      Util.prt("Usage : availability -c [netre=N1|N2|N3][expected][WHERE clause edge.chan][-b yyyy-mm-dd:yyyy-mm-dd...]"
              + "[-e yyyy-mm-dd:yyyy-mm-dd][-hz][-md][-cg]");
      Util.prt("Switch    Args         Description");
      Util.prt("-c     netre=N1|N2|N3 Run this for all channels in network N1 or N2 or N3");
      Util.prt("-c     expected       Run this for all channels with expected flag set to true");
      Util.prt("-c     WHERE ....     This is a where clause for a SELECT channel FROM edge.channel WHERE ...");
      Util.prt("-b     yyyy-mm-dd[:.. This is a list of beginning dates");
      Util.prt("-e     yyyy-mm-dd[:.. This is a list of endding dates (must equal number of dates in -b");
      Util.prt("-hz                   Use one BHZ, HHZ, SHZ, and EHZ channels");
      Util.prt("-cg                   If present, drop any channel whose metadata does not indicate its a 'CG' channel");
      Util.prt("-md                   If present a y or n is added to the report.  y=metadata is present for whole interval, n if not");
      //System.exit(0);
    }
    for (int i = 0; i < args.length; i++) {
      switch (args[i]) {
        case "-c":
          chans = args[i + 1];
          i++;
          break;
        case "-b":
          begins = args[i + 1];
          i++;
          break;
        case "-e":
          ends = args[i + 1];
          i++;
          break;
        case "-dbg":
          dbg = true;
          break;
        default:
          flags += args[i];
          break;
      }
    }
    String s = report(chans, begins, ends, flags);
    Util.prt("Report feedback is " + s);
    DBConnectionThread.shutdown();
    System.exit(0);
  }
}
