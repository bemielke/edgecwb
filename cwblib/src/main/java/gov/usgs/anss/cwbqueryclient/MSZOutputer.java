/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.cwbqueryclient;

import gov.usgs.anss.db.DBConnectionThread;
import gov.usgs.anss.edge.MiniSeedOutputFile;
import gov.usgs.anss.edge.RawToMiniSeed;
import gov.usgs.anss.edge.RunList;
import gov.usgs.anss.edge.RawDisk;
import gov.usgs.anss.edgemom.HoldingSender;
import gov.usgs.anss.seed.MiniSeed;
import gov.usgs.anss.util.SeedUtil;
import gov.usgs.anss.util.Util;
import java.io.IOException;
import java.net.UnknownHostException;
import java.sql.SQLException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.GregorianCalendar;

/**
 * This outputs a MiniSEED file and fills any gaps with miniSEED blocks with the fill value (def=0).
 * It has a bunch of options which take advantage of this class knowing about gaps.
 * <pre>
 * switch  arg     Description
 * -fill   val     Use val instead of zero to fill the gaps.
 * -gaps           If present, make a listing of all of the gaps
 * -gapfiles       If present, make a file with the gaps as a list of start times and duration
 * -list           Print out a list of runs, but do not necessarly make files.
 * -makefetch NN   Create fetch list entries of type NN in the fetchlist table
 * -table          The table to use for any -makefetch entries (def=fetchlist)
 * -updhold        If present, update the holdings database via a HoldingsSender to TcpHoldings
 * -dbgz           Turn on more output in this routine, a -dbg applies to all of the CWBQuery so this is more specific
 * -msgaps         If turned on gaps will be in their own miniSEED blocks rather than just no-data values interspersed
 * </PRE>
 *
 * @author davidketchum
 */
public class MSZOutputer extends Outputer {

  private static DBConnectionThread dbconn;
  private boolean dbg;
  int blocksize;
  private static final DecimalFormat df3 = new DecimalFormat("0.000");
  ;
  private static final DecimalFormat df4 = new DecimalFormat("0.0000");

  ;
  /** Creates a new instance of SacOutputer
   *@param blkSize Set the block size for this outputer
   */
  public MSZOutputer(int blkSize) {
    blocksize = blkSize;
  }

  @Override
  public void makeFile(String lastComp, String filename, String filemask, ArrayList<MiniSeed> blks,
          java.util.Date beg, double duration, String[] args) throws IOException {

    // Process the args for things that affect us
    Util.setModeGMT();
    boolean gaps = false;       // if true, generate a list of any gaps in the data
    boolean list = false;
    boolean doHoldings = false;
    boolean msgaps = false;
    dbg = false;
    boolean makeFetch = false;
    String table = "fetcher.fetchlist";
    int fill = -12345;
    String gapType = "IU";
    boolean gapfile = false;
    StringBuilder gapFile = new StringBuilder(1000);
    String dbHost = Util.getProperty("DBServer");
    double totalDuration = 300.;
    for (int i = 0; i < args.length; i++) {
      if (args[i].equals("-fill")) {
        fill = Integer.parseInt(args[i + 1]);
      }
      if (args[i].equals("-gaps")) {
        fill = 2147000000;
        gaps = true;
      }
      if (args[i].equals("-gapfiles")) {
        fill = 2147000000;
        gaps = true;
        gapfile = true;
      }
      if (args[i].equals("-list")) {
        fill = 2147000000;
        gaps = true;
        list = true;
      }
      if (args[i].equals("-makefetch")) {
        makeFetch = true;
        gapType = args[i + 1];
        i++;
      }
      if (args[i].equals("-table")) {
        table = args[i + 1];
        i++;
      }
      if (args[i].equals("-updhold")) {
        doHoldings = true;
      }
      if (args[i].equals("-dbgz")) {
        dbg = true;
      }
      if (args[i].equals("-msgaps")) {
        msgaps = true;
      }
      if (args[i].equals("-d")) {
        if (args[i + 1].endsWith("d") || args[i + 1].endsWith("D")) {
          totalDuration = Integer.parseInt(args[i + 1].substring(0, args[i + 1].length() - 1)) * 86400.;
        } else {
          totalDuration = Integer.parseInt(args[i + 1]);
        }
      }
    }
    boolean nontimeseries = false;
    // In the case of H channels, they are non continuous time series
    if (lastComp.substring(7, 10).equals("ACE") || lastComp.substring(7, 10).equals("LOG")
            || lastComp.substring(7, 10).equals("OCF") /*|| (!list && lastComp.substring(7,8).equals("H"))*/) {
      nontimeseries = true;
    }
    for (MiniSeed blk : blks) {
      if (blk != null) {
        if (blk.getRate() <= 0.00) {
          nontimeseries = true;
        }
        if (blk.getNsamp() == 0) {
          nontimeseries = true;
        }
        break;
      }
    }
    if (nontimeseries && makeFetch) {
      Util.prt("**** " + lastComp + " is a non-timeseries or non-continuous channel - do not make fetchlist");
    }
    if (nontimeseries) {
      return;
    }
    GregorianCalendar start = new GregorianCalendar();
    start.setTimeInMillis(beg.getTime());
    GregorianCalendar gapStart = new GregorianCalendar();
    GregorianCalendar gapEnd = new GregorianCalendar();

    // If this calls for making fetches, insure we have a MySQLConnection open to use
    if (makeFetch) {
      if (dbconn == null) {
        try {
          dbconn = new DBConnectionThread(dbHost, "update", "edge", true, false, "MSZOutput", Util.getOutput());
          if (!dbconn.waitForConnection()) {
            if (!dbconn.waitForConnection()) {
              if (!dbconn.waitForConnection()) {
                Util.prt("***** could not open db connection !");
                System.exit(1);
              }
            }
          }
        } catch (InstantiationException e) {
          Util.prt(" DB thread instantiation error ");
          System.exit(1);
        }
      }
    }
    //ZeroFilledSpan span = new ZeroFilledSpan(blks);
    int threshold = 1000;
    RunList runlist = new RunList(100);
    if (gaps && !nontimeseries) {
      HoldingSender hs = null;
      // process the gaps
      Collections.sort(blks);
      double rate = 0.;
      int gapThreshold = 24;
      // create some number of runs to put together into a  long one.
      boolean found = false;
      MiniSeed ms2 = null;
      for (int i = 0; i < blks.size(); i++) {
        if (i % 20000 == 0 && i > 0) {
          runlist.consolidate();
          Util.prta(i + " process run of " + blks.size() + " #runs=" + runlist.getRuns().size());
        }
        ms2 = (MiniSeed) blks.get(i);
        if (doHoldings) {
          if (hs == null) {
            try {
              hs = new HoldingSender("-h " + Util.getProperty("StatusServer") + " -p 7996 -t CW -q 10000 -tcp -quiet -noeto", "");
            } catch (UnknownHostException e) {
              Util.prt("Unknown host exception host=" + Util.getProperty("StatusServer"));
              doHoldings = false;
            }
          }
        }
        found = false;
        if (ms2.getRate() > rate) {
          rate = ms2.getRate();
          gapThreshold = (int) (1000. / rate + 0.5);
          gapThreshold--;
        }
        runlist.add(ms2);
        if (ms2.getNBlockettes() == 2) {
          if (ms2.getBlocketteType(0) == 1000 && ms2.getBlocketteType(1) == 1001) {
            rate = ms2.getRate();
            gapThreshold = (int) (1000. / rate + 0.5);
            gapThreshold--;
          }
        }
        /*if(!found) {
          runs.add(new Run(ms2));
        }*/
      }
      runlist.consolidate();
      ArrayList<gov.usgs.anss.edge.Run> runs = runlist.getRuns();
      Collections.sort(runs);
      if (list) {
        double total = 0.;
        double totalGaps = 0;
        double gaplength = 0.;
        for (int i = 0; i < runs.size(); i++) {
          total += runs.get(i).getLength();
          if (i < runs.size() - 1) {
            gaplength = ((runs.get(i + 1)).getStart() - runs.get(i).getEnd()) / 1000.;
            totalGaps += gaplength;
          }
          if (runs.get(i).getLength() >= totalDuration - 1 / rate) {
            Util.prt("Run for  " + runs.get(i).getSeedname() + " is complete 100%.");
          } else {
            Util.prt(runs.get(i).toString() + " rt=" + Util.df24(ms2 == null ? 0. : ms2.getRate())
                    + (i < runs.size() - 1 ? " gap follows "
                    + Util.leftPad(df3.format(gaplength), 10) + " s" : ""));
          }
          //Util.prt(runlist.toString());
        }
        if (runs.size() > 1 || runs.get(0).getLength() < totalDuration - 1. / rate) {
          Util.prt(runs.get(0).getSeedname() + " Total sec=" + df3.format(total) + " " + df4.format(total / totalDuration * 100.) + "%"
                  + " #gaps=" + Util.leftPad((runs.size() - 1) + "", 4) + " Gap sec=" + Util.leftPad(df3.format(totalGaps), 9) + " "
                  + df4.format(totalGaps / totalDuration * 100.) + "% #blks=" + Util.leftPad("" + blks.size(), 5));
        }
        return;
      }
      if (dbg) {
        for (int i = 0; i < runs.size(); i++) {
          Util.prt(i + " " + runs.get(i));
        }
      }
      long expected = runs.get(0).getStart();
      //long today = expected;
      if (expected - beg.getTime() > gapThreshold + 1) {

        gapStart.setTimeInMillis(beg.getTime());     // previous midnight
        gapEnd.setTimeInMillis(expected);
        Util.prt("Gap: " + Util.toDOYString(gapStart) + " to " + Util.toDOYString(gapEnd)
                + " (" + ((expected % 86400000L) / 1000.) + " secs) Start-of-interval -s "
                + lastComp.replaceAll(" ", "-") + " -b " + Util.toDOYString(gapStart) + " -d "
                + df3.format((gapEnd.getTimeInMillis() - gapStart.getTimeInMillis()) / 1000.));
        if (makeFetch) {
          if (gapType.equals("CW")) {
            Util.prt("java -jar ~vdl/bin/CWBQuery.jar -s " + lastComp.replace(" ", "-") + " -b "
                    + Util.ascdate(gapStart) + "-" + Util.asctime2(gapStart).substring(0, 11)
                    + " -d " + ((expected % 86400000L) / 1000.) + " -t ms -o %N_%y_%j_%h_%m.ms");

          } else {
            String s = "INSERT INTO " + table + " (seedname,start,start_ms,duration,status,type,updated,created) VALUES ("
                    + "'" + lastComp + "','" + Util.stringBuilderReplaceAll(Util.ascdate(gapStart), "/", "-") + " " + Util.asctime(gapStart).substring(0, 8)
                    + "',0," + ((expected % 86400000L) / 1000.)
                    + ",'open','" + gapType + "',now(),now());";
            Util.prt(s);
            try {
              dbconn.executeUpdate(s);
            } catch (SQLException e) {
              Util.SQLErrorPrint(e, "Could not insert start day gap into fetchlist on " + dbHost);
            }
          }
        }
      }
      for (int i = 0; i < runs.size(); i++) {
        if (hs != null) {
          hs.send(lastComp,
                  runs.get(i).getStart(), // start time of span 
                  (double) (runs.get(i).getEnd() - runs.get(i).getStart()) / 1000.);
        }
        // Is the end of this run before the expected - if so skip run
        if (runs.get(i).getEnd() < expected) {
          if (dbg) {
            Util.prt(i + " not needed. before expected");
          }
          continue;
        }
        // does this run span the expected, if yes last block is new expected
        if (runs.get(i).getStart() <= expected
                && runs.get(i).getEnd() > expected) {
          expected = runs.get(i).getEnd();
          if (dbg) {
            Util.prt(i + " spanning run new expect=" + runs.get(i).getEnd());
          }
          continue;
        }
        if (runs.get(i).getStart() - expected > gapThreshold) {
          gapStart.setTimeInMillis(expected - gapThreshold + 1);
          gapEnd.setTimeInMillis(runs.get(i).getStart() - 1);  // start time of run end end time of gap
          Util.prt("Gap: " + Util.toDOYString(gapStart) + " to "
                  + Util.toDOYString(gapEnd)
                  + " (" + df3.format((gapEnd.getTimeInMillis() - gapStart.getTimeInMillis()) / 1000.)
                  + " secs) Mid -s " + lastComp.replaceAll(" ", "-") + " -b " + Util.toDOYString(gapStart) + " -d "
                  + df3.format((gapEnd.getTimeInMillis() - gapStart.getTimeInMillis()) / 1000.));
          gapFile.append(Util.ascdatetime2(gapStart, null)).append("|").
                  append(df3.format((gapEnd.getTimeInMillis() - gapStart.getTimeInMillis()) / 1000.)).append("\n");
          if (makeFetch) {
            double dur = (gapEnd.getTimeInMillis() - gapStart.getTimeInMillis()) / 1000.;
            double maxinc = 10800.;
            if (lastComp.charAt(7) == 'L') {
              maxinc = 86400.;
            }
            if (lastComp.charAt(7) == 'V' || lastComp.charAt(7) == 'U' || lastComp.charAt(7) == 'A') {
              maxinc = 3 * 86400.;
            }
            GregorianCalendar st = new GregorianCalendar();
            st.setTimeInMillis(gapStart.getTimeInMillis());
            while (dur > 0.) {
              double time = dur;
              if (time > maxinc) {
                time = maxinc;
              }
              if (gapType.equals("CW")) {
                Util.prt("java -jar ~vdl/bin/CWBQuery.jar -s " + lastComp.replace(" ", "-") + " -b "
                        + Util.ascdate(st) + "-" + Util.asctime2(st).substring(0, 11)
                        + " -d " + df3.format(time) + " -t ms -o %N_%y_%j_%h_%m.ms");

              } else {
                String s = "INSERT INTO " + table + " (seedname,start,start_ms,duration,status,type,updated,created) VALUES ("
                        + "'" + lastComp + "','" + Util.stringBuilderReplaceAll(Util.ascdate(st), "/", "-") + " " + Util.asctime(st).substring(0, 8) + "',"
                        + (st.getTimeInMillis() % 1000L) + ","
                        + df3.format(time)
                        + ",'open','" + gapType + "',now(),now());";
                Util.prt(s);
                try {
                  dbconn.executeUpdate(s);
                } catch (SQLException e) {
                  Util.SQLErrorPrint(e, "Could not insert start day gap into fetchlist on " + dbHost);
                }
              }
              dur -= time;
              st.setTimeInMillis(st.getTimeInMillis() + (long) (time * 1000. + 0.001));
            }
          }
        }
        expected = runs.get(i).getEnd();
      }
      //long gp = ((today / 86400000L +1) *86400000L) - expected;
      long gp = (long) (beg.getTime() + duration * 1000.) - expected;
      if (gp > gapThreshold) {
        gapStart.setTimeInMillis(expected / 1000L * 1000L);
        gapEnd.setTimeInMillis(beg.getTime() + (long) (duration * 1000.));
        Util.prt("Gap: " + Util.toDOYString(gapStart) + " to " + Util.toDOYString(gapEnd) + " (" + (gp / 1000.)
                + " secs) End-of-interval -s " + lastComp.replaceAll(" ", "-") + " -b " + Util.toDOYString(gapStart) + " -d "
                + df3.format((gapEnd.getTimeInMillis() - gapStart.getTimeInMillis()) / 1000.));
        if (makeFetch) {
          double dur = gp / 1000.;
          double maxinc = 10800.;
          if (lastComp.charAt(7) == 'L') {
            maxinc = 86400.;
          }
          if (lastComp.charAt(7) == 'V' || lastComp.charAt(7) == 'U' || lastComp.charAt(7) == 'A') {
            maxinc = 3 * 86400.;
          }
          GregorianCalendar st = new GregorianCalendar();
          st.setTimeInMillis(gapStart.getTimeInMillis());
          while (dur > 0.) {
            double time = dur;
            if (time > maxinc) {
              time = maxinc;
            }
            if (gapType.equals("CW")) {
              Util.prt("java -jar ~vdl/bin/CWBQuery.jar -s " + lastComp.replace(" ", "-") + " -b "
                      + Util.ascdate(st) + "-" + Util.asctime2(st).substring(0, 11)
                      + " -d " + df3.format(time) + " -t ms -o %N_%y_%j_%h_%m.ms");

            } else {
              String s = "INSERT INTO " + table + " (seedname,start,start_ms,duration,status,type,updated,created) VALUES ("
                      + "'" + lastComp + "','" + Util.stringBuilderReplaceAll(Util.ascdate(st), "/", "-") + " " + Util.asctime(st).substring(0, 8) + "',"
                      + (expected % 1000L) + ","
                      + df3.format(time)
                      + ",'open','" + gapType + "',now(),now());";
              Util.prt(s);
              try {
                dbconn.executeUpdate(s);
              } catch (SQLException e) {
                Util.SQLErrorPrint(e, "Could not insert start day gap into fetchlist on " + dbHost);
              }
            }
            dur -= time;
            st.setTimeInMillis(st.getTimeInMillis() + (long) (time * 1000. + 0.001));
          }
        }

      }
      //Util.prt("expected="+expected+" bound="+((today / 86400000L +1) *86400000L)+" gp="+gp);

      if (hs != null && doHoldings) {
        hs.close();
      }
      if (gapfile) {
        try {
          try (RawDisk rw = new RawDisk(lastComp.substring(0, 2) + "." + lastComp.substring(2, 7).trim() + "."
                  + lastComp.substring(10).trim() + "." + lastComp.substring(7, 10).trim() + ".gap", "rw")) {
            rw.position(0);
            rw.write(gapFile.toString().getBytes());
            rw.setLength(gapFile.length());
          }
        } catch (IOException e) {
          e.printStackTrace();
        }
      }
      return;  // we only want to process the gaps
    }

    // build the zero filled area (either with exact limits or with all blocks)
    ZeroFilledSpan span = new ZeroFilledSpan(blks, start, duration, fill);
    if (dbg) {
      Util.prt("ZeroSpan=" + span.toString());
    }
    if (filemask.equals("%N")) {
      filename += ".ms";
    }
    filename = filename.replaceAll("[__]", "_");
    // Make an output file and link it to the RawToMiniSeed createor
    MiniSeedOutputFile outms = new MiniSeedOutputFile(filename);
    MiniSeed ms = (MiniSeed) blks.get(0);
    GregorianCalendar st = span.getStart();
    StringBuilder tmp = new StringBuilder(12);    // Hack : no yet ready to convert to StringBuilders
    tmp.append(lastComp);
    Util.rightPad(tmp, 12);
    RawToMiniSeed rwms = new RawToMiniSeed(tmp, ms.getRate(),
            blocksize / 64 - 1,
            ms.getYear(), ms.getDay(),
            (int) ((st.getTimeInMillis() % 86400000L) / 1000L),
            (int) ((st.getTimeInMillis() % 1000L) * 1000L),
            700000, null);
    rwms.setOutputHandler(outms);
    int len = 12000;
    int n;
    double secadd;
    int[] d = new int[len];
    int[] d2 = new int[len];
    int year = span.getStart().get(Calendar.YEAR);
    int doy = span.getStart().get(Calendar.DAY_OF_YEAR);
    int sec = (int) ((span.getStart().getTimeInMillis() % 86400000L) / 1000);
    int micros = (int) ((span.getStart().getTimeInMillis() % 1000L) * 1000);
    int jul = SeedUtil.toJulian(year, doy);
    boolean forceout = false;
    for (int off = 0; off < span.getNsamp(); off = off + len) {
      n = span.getData(d, off, len);
      //Util.prt("comp: n="+n+" "+year+" "+doy+" "+RawToMiniSeed.timeFromUSec(sec*1000000L+micros));

      int offstart;
      int end;
      // create miniseed from the data buffer.  Look for fill and compress the data, call forceout at gaps.
      if (msgaps) {
        int bufoff = 0;
        while (bufoff < n) {
          offstart = -1;
          for (int i = bufoff; i < n; i++) {
            if (d[i] != fill) {
              offstart = i;
              break;
            }  // found first non-fill, find next
          }
          if (offstart == -1) {
            break;      // No non fill the rest of the way
          }
          end = -1;
          for (int i = offstart; i < n; i++) {
            if (d[i] == fill) {
              end = i;
              forceout = true;
              break;
            }
          }
          if (end == -1) {
            end = n;
          }
          System.arraycopy(d, offstart, d2, 0, end - offstart);
          secadd = offstart / ms.getRate();
          int secoff = (int) secadd;
          int microoff = (int) ((secadd - secoff) * 1000000.);
          rwms.process(d2, end - offstart, year, doy, sec + secoff, micros + microoff, 0, 0, 0, 0, 0);
          if (forceout) {
            rwms.forceOut();
            forceout = false;
          }
          bufoff = end;
        }
      } else {
        rwms.process(d, n, year, doy, sec, micros, 0, 0, 0, 0, 0);
      }

      // add to the time and submit somemore data
      secadd = n / ms.getRate();
      sec = sec + (int) secadd;
      micros = micros + (int) ((sec - Math.floor(sec)) * 1000000. + 0.0001);
      while (micros >= 1000000) {
        micros -= 1000000;
        sec++;
      }
      while (sec >= 86400) {
        sec -= 86400;
        jul++;
        int[] ymd = SeedUtil.fromJulian(jul);
        year = ymd[0];
        doy = SeedUtil.doy_from_ymd(ymd);
      }
    }
    rwms.forceOut();
    outms.close();
  }
  /**
   * This class creates a list of contiguous blocks. A block can be added to it and will be rejected
   * if it is not contiguous at the end. The user just attempts to add the next data block in time
   * to each of the known runs, and creates a new run with the block when none of the existing ones
   * accepts it
   */
  /*class Run implements Comparable<Run> {
    ArrayList<MiniSeed> blks;     // List of sequenctial contiuous Mini-seed blocks
    GregorianCalendar start;      // start time of this run
    GregorianCalendar end;        // current ending time of this run (expected time of next block)
    /** return the start time of the run
     *@return the start time as GregorianCalendar*
    public GregorianCalendar getStart() {return start;}
    ** return the end time of the run (Actually the time of the next expected sample)
     *@return the end time as GregorianCalendar*
    public GregorianCalendar getEnd() {return end;}
    ** return duration of run in seconds
     *@return The duration of run in seconds*
    public double getLength() { return (end.getTimeInMillis()-start.getTimeInMillis())/1000.;}
    ** string representation
     *@return a String representation of this run *
    @Override
    public String toString() {return 
        "Run from "+Util.ascdate(start)+" "+Util.asctime2(start)+" to "+
        Util.ascdate(end)+" "+Util.asctime2(end)+" "+getLength()+" s #blks="+blks.size();
    }
    ** return the ith miniseed block
     *@param Index of desired Mini-seed block
     *@return the Miniseed block *
    public MiniSeed getMS(int i) { return blks.get(i);}
    ** retun length of miniseed list for this run
     *@return The length of the miniseed list for this run *
    public int getNBlocks() {return blks.size();}
    /** clear the list (used mainly to free up associated memory)*
    public void clear() {blks.clear(); start=null; end=null;}
    ** implement Comparable
     *@param the Run to compare this to
     *@return -1 if <, 0 if =, 1 if >than *
    public int compareTo(Run r) {
      return start.compareTo(((Run) r).getStart());
    }
    ** create a new run with the given miniseed as initial block
     *@param ms The miniseed block to first include *
    public Run(MiniSeed ms) {
      start = ms.getGregorianCalendar();
      blks = new ArrayList<MiniSeed>(1000);
      blks.add(ms);
      end = ms.getGregorianCalendar();
      end.setTimeInMillis(end.getTimeInMillis()+((long) (ms.getNsamp()/ms.getRate()*1000+0.49)));
      
    }
    ** see if this miniseed block will add contiguously to the end of this run
     *@param the miniseed block to consider for contiguousnexx, add it if is is
     *@return true, if block was contiguous and was added to this run, false otherwise*
    public boolean add(MiniSeed ms) {
      
      // Is the beginning of this one near the end of the last one!
      if( Math.abs(ms.getGregorianCalendar().getTimeInMillis() - end.getTimeInMillis()) <
          500./ms.getRate()) {
        // add this block to the list
        blks.add(ms);
        end = ms.getGregorianCalendar();
        end.setTimeInMillis(end.getTimeInMillis()+
            ((long) (ms.getNsamp()/ms.getRate()*1000+0.49)));
        return true;
      }
      else return false;
    }
  }*/
}
