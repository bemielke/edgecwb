/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */

package gov.usgs.anss.rtp;
import gov.usgs.anss.db.DBConnectionThread;
import gov.usgs.anss.edge.IllegalSeednameException;
import gov.usgs.anss.edge.MiniSeedOutputHandler;
import gov.usgs.anss.edge.RawDisk;
import gov.usgs.anss.edge.RawToMiniSeed;
import gov.usgs.anss.edgemom.ReftekClient;
import gov.usgs.anss.seed.MiniSeed;
import gov.usgs.anss.util.Util;
import java.io.File;
import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.TreeMap;
import java.util.Arrays;
import java.util.Iterator;
import java.util.GregorianCalendar;

/**
 * This class loads a reftek file an creates triggers based on it as well in
 * portables.reftektriggers
 * <br>
 * <PRE>
 * Switch Args    Description
 * -h     dot.adr The address of the MySQLServer with portable configuration data (obsolete)
 * -db    tag     The full database access URL like localhost/3306:edge:mysql:edge
 * -refdbg        Set longer output
 * -dir           This is a directory scan call, all files on list are at the day level YYYYDDD
 * -chk           Only to a check of the 0 stream and compare them to the configuration
 * -outdir path   Put all output miniseed files in this directory
 * -logdir path   Put all log files from the conversion in this directory
 * -source ss     Source to add to triggers
 * </PRE>
 *
 * @author davidketchum
 */
public final class LoadReftekFile implements MiniSeedOutputHandler {

  private static final TreeMap<String, RawDisk> files = new TreeMap<>();
  private boolean configError;
  private String outdir = ".";
  private ReftekClient ref;                     // This does the real work of decoding the data
  private long lastClosePass;
  private int nwritten;
  private DBConnectionThread dbconn = null;    // to update reftektrigger
  private String source;                 // Source for the triggers

  @Override
  public void close() {
    RawToMiniSeed.forceStale(-10);
    ref.terminate();
    ref.closeLog();
    ref.close();
  }

  @Override
  public void putbuf(byte[] buf, int len) {
    try {
      MiniSeed ms = new MiniSeed(buf, 0, len);
      String file = ms.getSeedNameSB().substring(0, 12).replaceAll(" ", "_") + "_" + ms.getTimeString().substring(0, 7).replaceAll(" ", "_");
      RawDisk msout = files.get(file);
      if (msout == null) {
        msout = new RawDisk((outdir + "/" + file + "0.ms").replaceAll("//", "/"), "rw");
        ref.prta("Open : " + (outdir + "/" + file + "0.ms").replaceAll("//", "/")
                + " len=" + msout.length() + (msout.length() > 0 ? " ***** warning File reopened ******" : ""));
        msout.setLength(0L);      // always a new file if opened during run.
        files.put(file, msout);
        if (System.currentTimeMillis() - lastClosePass > 60000) {
          Util.prta("do close pass ");
          long now = System.currentTimeMillis();
          lastClosePass = now;
          RawToMiniSeed.forceStale(120000);   // Force out any trailers not updated recently
          Iterator<RawDisk> itr = files.values().iterator();
          while (itr.hasNext()) {
            RawDisk rw = itr.next();
            if (now - rw.getLastUsed() > 600000) {
              ref.prta("Close :  " + rw.getFilename() + " len=" + (rw.length() / 512));
              itr.remove();
            }
          }
        }
      }
      int iblk = (int) (msout.length() / 512);
      msout.writeBlock(iblk, buf, 0, len);
      nwritten++;
    } catch (IOException e) {
      ref.prt("cannot open output file e=" + e);

    } catch (IllegalSeednameException e) {
      Util.prt("Unknown exception ");
      e.printStackTrace();
    }
  }

  public LoadReftekFile(String argline, String tag) {
    RawToMiniSeed.setStaticOutputHandler((LoadReftekFile) this);
    String[] args = argline.split("\\s");
    String logdir = "";
    source = "RT";
    for (int i = 0; i < args.length; i++) {
      if (args[i].equals("-h")) {
        Util.setProperty("MySQLServer", args[i + 1]);
      }
      if (args[i].equals("-db")) {
        Util.setProperty("DBServer", args[i + 1]);
      }
      if (args[i].equals("-outdir")) {
        outdir = args[i + 1];
      }
      if (args[i].equals("-source")) {
        source = args[i + 1];
      }
    }
    argline += " >> loadreftek";
    ref = new ReftekClient(Util.getProperty("DBServer"), argline, "TEST");    // This actuall process any file
    lastClosePass = System.currentTimeMillis();
    if (dbconn == null) {
      try {
        dbconn = new DBConnectionThread(Util.getProperty("DBServer"), "update", "portables",
                false, false, "Portables", Util.getOutput());
        if (!DBConnectionThread.waitForConnection("Portables")) {
          if (!DBConnectionThread.waitForConnection("Portables")) {
            ref.prta("MDRun: Did not connect to DB promptly Portables " + Util.getProperty("DBServer"));
          }
        }
      } catch (InstantiationException e) {
        Util.prta("InstantiationException opening edge database in DBServer e=" + e.getMessage());
        Util.exit(1);
      }
    }
    Util.prta(Util.ascdate() + " Start : " + argline);
  }
  private GregorianCalendar trigtime = new GregorianCalendar();

  public void processFile(String file) {
    ref.processFile(file);

    // The process file has built up a list of triggers.  Process them to database
    ArrayList<ReftekTrigger> ehs = ref.getEHList();
    int ninsert = 0;
    int nupdate = 0;
    ref.prta("Do  triggers to db=" + dbconn);
    for (int i = 0; i < ehs.size(); i++) {
      ref.prt(i + " Trigger at " + ehs.get(i) + " source=" + source);
      try {
        String eventFile = ehs.get(i).getFilename();
        if (ehs.get(i).getFilename().length() > 20) {
          eventFile = eventFile.substring(0, 20);
        }
        trigtime.setTimeInMillis(ehs.get(i).getTriggerTime());
        ResultSet rs = dbconn.executeQuery("SELECT * FROM portables.reftektrigger WHERE seedname='" + ehs.get(i).getSeedname().toString().trim()
                + "' AND evtfile='" + eventFile
                + "' AND ABS(DATEDIFF('" + Util.ascdate(trigtime) + "',trigtime)) < 2");
        if (rs.next()) {
          int ID = rs.getInt("id");
          rs.close();
          dbconn.executeUpdate("UPDATE portables.reftektrigger SET trigtime='"
                  + Util.ascdate(trigtime) + " " + Util.asctime2(trigtime) + "',source='" + source + "',updated=now() WHERE id=" + ID);
          nupdate++;
        } else {
          rs.close();
          dbconn.executeUpdate(
                  "INSERT INTO portables.reftektrigger (seedname,trigtime,evtfile,source,updated,created_by, created) VALUES ('"
                  + ehs.get(i).getSeedname() + "','" + Util.ascdate(trigtime) + " " + Util.asctime2(trigtime) + "','"
                  + eventFile + "','" + source + "',now(),0, now())");
          ninsert++;
        }
      } catch (SQLException e) {
        e.printStackTrace(ref.getPrintStream());
        e.printStackTrace(System.err);
        Util.exit(2);
      }
      ref.prt("#inserted=" + ninsert + " #updated=" + nupdate);
    }
  }

  public boolean hadConfigError() {
    return configError;
  }

  /**
   * This is the test code for ReftekClient
   *
   * @param args the command line arguments
   */
  public static void main(String[] args) {
    Util.setModeGMT();
    Util.setProcess("loadreftek");
    Util.init("edge.prop");
    Util.setNoInteractive(true);
    Util.setProperty("logfilepath", "");
    String argline = "";
    boolean rawread = false;
    boolean dirScan = false;
    int istart = 0;
    boolean configScan = false;
    if (args.length == 0) {
      Util.prt("Switch Args    Description");
      Util.prt("-db    db.url  The URL of the Database with portable configuration data like : localhost/3306:edge:mysql:edge");
      Util.prt("-refdbg        Set longer output");
      Util.prt("-dir           This is a directory scan call, all files on list are at the day level YYYYDDD");
      Util.prt("-chk           Only to a check of the 0 stream and compare them to the configuration");
      Util.prt("-outdir path   Put all output miniseed files in this directory");
      Util.prt("-logdir path   Put all log files from the conversion in this directory");
      Util.prt("-source ss     Source to add to triggers");
    }
    for (int i = 0; i < args.length; i++) {
      if (args[i].equals("-h")) {
        istart = i + 2;
        argline += " " + args[i] + " " + args[i + 1];
        i++;
      }
      if (args[i].equals("-refdbg")) {
        istart = i + 1;
        argline += " " + args[i];
      }
      if (args[i].equals("-dir")) {
        istart = i + 1;
        dirScan = true;
      }
      if (args[i].equals("-chk")) {
        istart = i + 1;
        configScan = true;
      }
      if (args[i].equals("-outdir")) {
        istart = i + 2;
        argline += " " + args[i] + " " + args[i + 1];
      }
      if (args[i].equals("-source")) {
        istart = i + 2;
        argline += " " + args[i] + " " + args[i + 1];
      }
      if (args[i].equals("-logdir")) {
        istart = i + 2;
        argline += " " + args[i] + " " + args[i + 1];
        Util.setProperty("logfilepath", (args[i + 1] + "/").replaceAll("//", "/"));
      }
      if (args[i].equals("-db")) {
        istart = i + 2;
        argline += " " + args[i] + " " + args[i + 1];
        Util.setProperty("DBServer", args[i + 1]);
      }
    }
    argline = argline.trim();
    // The top dir is always the YYYYDDD directory.  This will scan
    LoadReftekFile loader = new LoadReftekFile(argline, "REFLOAD");
    if (dirScan) {
      for (int i = istart; i < args.length; i++) {
        File dayfile = new File(args[i]);
        boolean ok = true;
        if (!dayfile.isDirectory()) {
          ok = false;
        }
        if (dayfile.getName().length() != 7) {
          ok = false;
        } else {
          String dayname = dayfile.getName();
          for (int k = 0; k < 7; k++) {
            if (!Character.isDigit(dayname.charAt(k))) {
              ok = false;
            }
          }
        }
        if (ok) {
          File[] units = dayfile.listFiles();
          if (units == null) {
            Util.prt("Found no unit files in dayfile=" + dayfile + " units=" + Arrays.toString(units) + " is dir=" + dayfile.isDirectory());
            continue;
          }
          for (File unit : units) {
            if (unit.isDirectory() && unit.getName().length() == 4) {
              File[] streams = unit.listFiles();
              for (File stream : streams) {
                ok = true;
                if (stream.getName().length() != 1) {
                  ok = false;
                } else if (!Character.isDigit(stream.getName().charAt(0))) {
                  ok = false;
                }
                if (configScan && stream.getName().charAt(0) != '0') {
                  ok = false;
                }
                if (ok) {
                  File[] datafiles = stream.listFiles();
                  Arrays.sort(datafiles);
                  for (File datafile : datafiles) {
                    ok = true;
                    if (datafile.getName().length() != 18) {
                      ok = false;
                    } else if (!datafile.getName().substring(9, 10).equals("_")) {
                      ok = false;
                    } else {
                      String name = datafile.getName();
                      for (int k = 0; k < 9; k++) {
                        if (!Character.isDigit(name.charAt(k)) && k != 9) {
                          ok = false;
                        }
                      }
                    }
                    if (ok) {
                      Util.prta("         %%%%%  " + datafile.getPath() + " %%%%%%");
                      try {
                        loader.processFile(datafile.getPath());
                      } catch (RuntimeException e) {
                        if (e.getMessage() != null) {
                          if (e.getMessage().contains("DSCONFIGERR")) {
                            Util.prt("*** There are errors in the configuration for " + datafile.getPath() + " e=" + e);
                          } else if (e.getMessage().contains("NOCONFIG")) {
                            Util.prt("*** " + e.getMessage());
                          } else {
                            e.printStackTrace();
                          }
                        } else {
                          e.printStackTrace();
                        }
                      }
                    } else {
                      Util.prt("Do not process data file=" + datafile.getPath());
                    }
                  }
                } else if (!configScan) {
                  Util.prt("Skipping what should be a stream directory " + stream.getPath());
                }
              }
            } else {
              Util.prt("Skipping file in unit - not right " + unit.getPath());
            }
          }
        } else {
          Util.prt("skipping day directory - not right form for day directory " + dayfile.getPath());
        }
      }
    } else {
      for (int i = istart; i < args.length; i++) {
        loader.processFile(args[i]);
      }
    }
    loader.close();
    System.exit(0);
  }
}
