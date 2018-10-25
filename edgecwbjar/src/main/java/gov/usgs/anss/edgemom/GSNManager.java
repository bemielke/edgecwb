/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.edgemom;

import gov.usgs.alarm.SendEvent;
import gov.usgs.anss.db.DBConnectionThread;
import gov.usgs.anss.edge.IndexFile;
import gov.usgs.anss.edgethread.EdgeThread;
import gov.usgs.anss.edgethread.ManagerInterface;
import gov.usgs.anss.util.Util;
import gov.usgs.edge.config.EdgeStation;
import gov.usgs.edge.config.RoleInstance;
import java.io.File;
import java.io.IOException;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.BufferedReader;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.TreeMap;

/**
 * This class reads the gsnstation database table and creates a configuration
 * file of all of the stations assigned to the roles on this server. It starts
 * and manages a thread for each stations. The configuration contains the
 * protocol of connections (ISI, DADP, LISS, NetServ, Q330) to be use as well as
 * the IP address and ports for the main acquisition (long haul) and for data
 * fetches.
 * <p>
 * If the database is used, all information for creating the config file comes
 * from the database. The gsnstation table is maintained through the
 * EdgeConfig.jar GUI. If the -nodb option is used, then the database is ignored
 * and whatever is in the configuration file is started.
 * <p>
 * The manager part that creates and manages threads will restart threads if
 * they go down, and will remove threads if the station is removed from the
 * configuration file. New lines added to the configuration file, are started
 * each cycle as well.
 * <p>
 * <PRE>
 * switch    value    Description
 * -config  filepath  The output of this thread, i.e. the stations to be acquired (def=log/gsn.setup)
 * -nodb              If set, No database is used in the configuration - the theads are managed from the config file
 * -dbg               If present, more log output is generated.
 * </PRE> 
 *
 * @author davidketchum
 */
public final class GSNManager extends EdgeThread {

  TreeMap<String, ManagerInterface> thr = new TreeMap<>();
  private final ShutdownGSNManager shutdown;
  private static DBConnectionThread dbconn;
  private static DBConnectionThread dbconnedge;
  static int ncalls = 0;
  private String configFile;
  private String dadpConfigFile = "vdl/PARMS/vdlmom.setupdadp";
  private String dadpConfigFile2 = "vdl/dadp/vdlmom.setupdadp";
  private boolean dbg;
  private boolean noDB;
  private StringBuilder gsnsb = new StringBuilder(10000);
  private ArrayList<RoleInstance> roles = new ArrayList<>(10);
  private StringBuilder dadp = new StringBuilder(1000);
  private StringBuilder netserv = new StringBuilder(1000);

  //private String lastdadp="";
  //private String lastnetserv = "";
  /**
   * set debug state
   *
   * @param t The new debug state.
   */
  public void setDebug(boolean t) {
    dbg = t;
  }

  /**
   * Terminate thread. This causes an interrupt to be sure, but you may not need
   * the interrupt!
   */
  @Override
  public void terminate() {
    terminate = true;
    prta("GSNM: interrupted called!");
    interrupt();
  }

  @Override
  public String toString() {
    return configFile + " #thr=" + thr.size();
  }

  /**
   * Return the status string for this thread.
   *
   * @return A string representing status for this thread.
   */
  @Override
  public StringBuilder getStatusString() {
    Util.clear(statussb);
    statussb.append("GSNM: #client=").append(thr.size()).append(" config=").append(configFile).append(" roles: ");
    for (RoleInstance role : roles) {
      statussb.append(role.getRole()).append(" ");
    }
    statussb.append("\n");
    Iterator<ManagerInterface> itr = thr.values().iterator();
    while (itr.hasNext()) {
      statussb.append("   ").append(itr.next().toString()).append("\n");
    }
    return statussb;
  }

  /**
   * Return the monitor string for Icinga.
   *
   * @return A String representing the monitor key value pairs for this
   * EdgeThread.
   */
  @Override
  public StringBuilder getMonitorString() {
    if (monitorsb.length() > 0) {
      monitorsb.delete(0, monitorsb.length());
    }
    monitorsb.append("GSNNthread=").append(thr.size());
    for (RoleInstance role : roles) {
      statussb.append(role.getRole()).append(" ");
    }
    monitorsb.append("\n");
    Iterator<ManagerInterface> itr = thr.values().iterator();
    long totalIn = 0;
    while (itr.hasNext()) {
      ManagerInterface obj = itr.next();
      totalIn += obj.getBytesIn();
    }
    monitorsb.append("GSNBytesIn=").append(totalIn).append("\n");
    return monitorsb;
  }

  /**
   * Return console output - this is fully integrated so it never returns
   * anything.
   *
   * @return "" since this cannot get output outside of the prt() system.
   */
  @Override
  public StringBuilder getConsoleOutput() {
    return consolesb;
  }

  /**
   * Creates an new instance of LISSCLient - which will try to stay connected to
   * the host/port source of data. This one gets its arguments from a command
   * line.
   *
   * @param argline The command line.
   * @param tg The logging tag.
   */
  public GSNManager(String argline, String tg) {
    super(argline, tg);
//initThread(argline,tg);
    String[] args = argline.split("\\s");
    dbg = false;
    configFile = "config/gsn.setup";
    noDB = false;
    for (int i = 0; i < args.length; i++) {
      //prt(i+" arg="+args[i]);
      if (args[i].equals("-dbg")) {
        dbg = true;
      } else if (args[i].equals("-config")) {
        configFile = args[i + 1];
        i++;
      } else if (args[i].equalsIgnoreCase("-nodb")) {
        noDB = true;
      } // else if(args[i].equals("-h")) { host=args[i+1]; i++;}
      else if (args[i].substring(0, 1).equals(">")) {
        break;
      } else {
        prt("GSNManager: unknown switch=" + args[i] + " ln=" + argline);
      }
    }
    if (Util.getProperty("DBServer") == null) {
      noDB = true;
    } else if (Util.getProperty("DBServer").equalsIgnoreCase("NoDB")) {
      noDB = true;
    } else if (Util.getProperty("DBServer").equals("")) {
      noDB = true;
    }
    prt("GSNM: created args=" + argline + " tag=" + tag);
    prt("GSNM: config=" + configFile + " dbg=" + dbg + " no DB=" + noDB + " DBServer=" + Util.getProperty("DBServer"));
    shutdown = new ShutdownGSNManager();
    Runtime.getRuntime().addShutdownHook(shutdown);
    if (dbconn == null && !noDB) {
      try {
        dbconn = new DBConnectionThread(Util.getProperty("DBServer"), "readonly", "edge",
                false, false, "edgeGSNM", getPrintStream());
        addLog(dbconn);
        if (!DBConnectionThread.waitForConnection("edgeGSNM")) {
          if (!DBConnectionThread.waitForConnection("edgeGSNM")) {
            prt("Failed to get GSN database access");
          }
        }
        if (DBConnectionThread.getThread("anss") == null) {
          DBConnectionThread tmp = new DBConnectionThread(Util.getProperty("DBServer"),
                  "readonly", "anss", false, false, "anss", getPrintStream());
          if (!DBConnectionThread.waitForConnection("anss")) {
            if (!DBConnectionThread.waitForConnection("anss")) {
              prt("Failed to get anss GSN access");
            }
          }
          addLog(tmp);
        }
        if (DBConnectionThread.getThread("edge") == null) {
          dbconnedge = new DBConnectionThread(Util.getProperty("DBServer"), "readonly",
                  "edge", false, false, "edge", getPrintStream());
          addLog(dbconnedge);
          if (!DBConnectionThread.waitForConnection("edge")) {
            if (!DBConnectionThread.waitForConnection("edge")) {
              prt("Failed to get edge GSN access");
            }
          }
        }
      } catch (InstantiationException e) {
        prta("InstantiationException: should be impossible");
        dbconn = DBConnectionThread.getThread("edgeGSNM");
      }
    }
    start();
  }

  /**
   * This Thread keeps a socket open to the host and port, reads in any
   * information on that socket, keeps a list of unique StatusInfo, and updates
   * that list when new data for the unique key comes in.
   */
  @Override
  public void run() {
    running = true;
    Iterator<ManagerInterface> itr;
    BufferedReader in;
    StringBuilder filesb = new StringBuilder(10000);
    int n;
    int loop = 0;
    String s;
    File dadpFile = new File(dadpConfigFile);
    File dadp2File = new File(dadpConfigFile2);
    try {
      sleep(2000);
    } catch (InterruptedException expected) {
    }
    boolean first = true;
    int dummy = 0;
    while (true) {
      try {                     // Runtime exception catcher
        if (terminate || Util.isShuttingDown()) {
          break;
        }
        try {
          // Clear all of the checks in the list
          itr = thr.values().iterator();
          while (itr.hasNext()) {
            itr.next().setCheck(false);
          }

          // Open file and read each line, process it through all the possibilities
          in = new BufferedReader(new FileReader(configFile));
          n = 0;
          prta("Read in configfile for GSN stations =" + configFile);
          while ((s = in.readLine()) != null) {
            if (s.length() < 1) {
              continue;
            }
            if (s.substring(0, 1).equals("%")) {
              break;       // debug: done reading flag
            }
            if (!s.substring(0, 1).equals("#") && !s.substring(0, 1).equals("!")) {
              int comment = s.indexOf("#");
              if (comment >= 0) {
                s = s.substring(0, comment).trim();  // Remove a later comment.
              }
              String[] tk = s.split("[:]");
              //String [] tk = s.split(":");
              if (tk.length >= 3) {
                n++;
                String station = tk[0];
                String type = tk[1];
                String argline = tk[2];
                ManagerInterface t = thr.get(tk[0].trim());
                if (t == null) {
                  if (type.equalsIgnoreCase("LISSClient")) {
                    t = new LISSClient(argline, station);
                  } else if (type.equalsIgnoreCase("ISILink")) {
                    t = new ISILink(argline, station);
                  } else if (type.equalsIgnoreCase("NetservClient")) {
                    t = new NetservClient(argline, station);
                  } else if (type.equalsIgnoreCase("SeedLinkClient")) {
                    t = new SeedLinkClient(argline, station);
                  } else if (type.equals("Comserv")) {
                    dummy = 1;
                  } else if (type.equals("NONE")) {
                    dummy = 1;
                  } else {
                    prta("Unknown type for new station! type=" + type + " stat=" + station + " argline=" + argline + " s=" + s);
                  }
                  if (t != null) {
                    prta("GSNM: New Station found start " + tk[0] + " " + tk[1] + ":" + tk[2]);
                    thr.put(station.trim(), t);
                    t.setCheck(true);
                  }
                } else if (!argline.trim().equals(t.getArgs().trim()) || !t.isAlive() || !t.isRunning()) {// if its not the same, stop and start it
                  t.setCheck(true);
                  prta("GSNM: line changed or dead. alive=" + t.isAlive() + " running=" + t.isRunning() + "|" + tk[0] + "|" + tk[1]);
                  prt(argline.trim() + "|\n" + t.getArgs() + "|\n");
                  t.terminate();
                  while (t.isAlive()) {
                    try {
                      sleep(10000);
                    } catch (InterruptedException expected) {
                    }
                    if (t.isAlive()) {
                      prta("GSNM: thread did not die in 10 seconds. " + station + " " + argline);
                    }
                  }
                  if (type.equalsIgnoreCase("LISSClient")) {
                    t = new LISSClient(argline, station);
                  } else if (type.equalsIgnoreCase("ISILink")) {
                    t = new ISILink(argline, station);
                  } else if (type.equalsIgnoreCase("NetservClient")) {
                    t = new NetservClient(argline, station);
                  } else if (type.equalsIgnoreCase("SeedLinkClient")) {
                    t = new SeedLinkClient(argline, station);
                  } else {
                    prta("Got unknown type in restart type=" + type + " argline=" + argline);
                  }
                  thr.put(station.trim(), t);
                  t.setCheck(true);
                } else {
                  t.setCheck(true);
                }
              }
            }
          }   // End of read config file lines
          in.close();
          itr = thr.values().iterator();
          while (itr.hasNext()) {
            ManagerInterface t = itr.next();
            if (!t.getCheck()) {
              // This line is no longer in the configuration, shutdown the Q330
              prta("GSNM: line must have disappeared " + t.toString() + " " + t.getArgs());
              t.terminate();
              itr.remove();
            }
          }
          try {
            sleep(10000);
          } catch (InterruptedException expected) {
          }

          // Now check that the file has not changed
        } catch (FileNotFoundException e) {
          prta("GSNM: Could not find a config file!=" + configFile + " " + e);
        } catch (IOException e) {
          prta("GSNM: error reading the configfile=" + configFile + " e=" + e.getMessage());
          e.printStackTrace();
        }
      } catch (RuntimeException e) {
        prta(tag + " RuntimeException in " + this.getClass().getName() + " e=" + e.getMessage());
        if (getPrintStream() != null) {
          e.printStackTrace(getPrintStream());
        } else {
          e.printStackTrace();
        }
        if (e.getMessage() != null) {
          if (e.getMessage().contains("OutOfMemory")) {
            SendEvent.edgeEvent("OutOfMemory", "Out of Memory in " + Util.getProcess() + "/"
                    + this.getClass().getName() + " on " + IndexFile.getNode() + "/" + Util.getAccount(), this);
            throw e;
          }
        }
        // gsnStringBuilder
      }
      String[] keys = new String[thr.size()];
      keys = thr.keySet().toArray(keys);
      while (true) {
        if (terminate) {
          break;
        }
        //prt("Do Pass= noDB="+noDB+" dbconn="+dbconn);
        if (!noDB && dbconn != null) {
          try {
            if (gsnsb.length() > 0) {
              gsnsb.delete(0, gsnsb.length());
            }
            gsnStringBuilder((Util.getSystemName().equals("igskcicgltgm073") ? "gldketchum3" : Util.getSystemName()), gsnsb, first);
            prt("gsnsb= noDB=" + noDB + " dbconn=" + dbconn + "\n" + gsnsb.toString());
            first = false;

            // Handle the dadp config file
            if (dadpFile.exists()) {
              Util.readFileToSB(dadpConfigFile, filesb);
              if (!dadp.toString().equals(filesb.toString()) && dadp.length() > 20) {
                Util.writeFileFromSB(dadpConfigFile, dadp);
                Util.writeFileFromSB(dadpConfigFile2, dadp);
                prta("GSNM: ***** dadp config file has changed ****");
              }
            }
            // handle the GSN config file
            Util.readFileToSB(configFile, filesb);
          } catch (IOException e) {
            prta("GSNM: got IO exception reading or writing file e=" + e);
            e.printStackTrace(getPrintStream());
          } catch (SQLException e) {
            prta("GSNM: got SQLException in gsnStringBuilder e=" + e);
            e.printStackTrace(this.getPrintStream());
            try {
              sleep(120000);
            } catch (InterruptedException expected) {
            }
            break;
          } catch (RuntimeException e) {
            prta("GSNM: got Runtime in gsnStringBuilder e=" + e);
            e.printStackTrace(this.getPrintStream());
            try {
              sleep(120000);
            } catch (InterruptedException expected) {
            }
            break;
          }
        } else {
          try {
            Util.readFileToSB(configFile, filesb);
          } catch (IOException e) {
            prta("GSNM: got IO exception reading file " + configFile + " e=" + e);
            e.printStackTrace(getPrintStream());
          }
          gsnsb.delete(0, gsnsb.length());
          gsnsb.append(filesb);
        }

        // If the file has not changed, check on down threads. If it has changed, always check to top
        if (!gsnsb.toString().equals(filesb.toString()) && gsnsb.length() > 20) {
          try {
            Util.writeFileFromSB(configFile, gsnsb);         // write out the file
          } catch (IOException e) {
            prta("GSNM: got IO exception writing file " + configFile + " e=" + e);
            e.printStackTrace(getPrintStream());
          }

          prta("GSNM: *** files have changed ****");
          prt("file=\n" + filesb + "|");
          prt("gsnsb=" + gsnsb.length() + "\n" + gsnsb + "|");
          break;
        } else {
          if (dbg || loop++ % 10 == 1) {
            prta("GSNM: files are same");
          }
          // Check to make sure all of our children threads are alive
          if (dbg) {
            prt("file=\n" + filesb + "|");
            prt("gsnsb=" + gsnsb.length() + "\n" + gsnsb + "|");
          }
          boolean breakout = false;
          for (String key : keys) {
            if (!thr.get(key).isAlive() || !thr.get(key).isRunning()) {
              prta("Found down thread alive=" + thr.get(key).isAlive() + " run=" + thr.get(key).isRunning() + " " + thr.get(key).toString());
              SendEvent.debugEvent("GSNMgrThrRes", "Unusual restart of " + key, this);
              breakout = true;
              break;
            }
          }
          if (breakout) {
            break;
          }
          //thr.get(keys[2]).terminate();thr.get(keys[6]).terminate();   //DEBUG: force some down!
          try {
            sleep(120000);
          } catch (InterruptedException expected) {
          }
        }
        if (System.currentTimeMillis() % 86400000L < 240000) {
          dbconn.setLogPrintStream(getPrintStream());
          dbconnedge.setLogPrintStream(getPrintStream());
        }
      }
      // The main loop has exited so the thread needs to exit

    }     // while(true)
    prta("GSNM: start termination of ManagerInterfaces");
    itr = thr.values().iterator();
    while (itr.hasNext()) {
      ManagerInterface t = itr.next();
      t.terminate();
    }
    prta("GSNM: ** GSNManager terminated ");
    try {
      Runtime.getRuntime().removeShutdownHook(shutdown);
    } catch (Exception expected) {
    }
    running = false;            // Let all know we are not running
    terminate = false;          // Sign that a terminate is no longer in progress
  }

  /**
   * This internal class gets registered with the Runtime shutdown system and is
   * invoked when the system is shutting down. This must cause the thread to
   * exit.
   */
  class ShutdownGSNManager extends Thread {

    /**
     * Default constructor does nothing. The shutdown hook starts the run()
     * thread.
     */
    public ShutdownGSNManager() {
      gov.usgs.anss.util.Util.prta("new ThreadShutdown " + getName() + " " + getClass().getSimpleName());
    }

    // This is called by the RunTime.shutdown at any termination 
    @Override
    public void run() {
      System.err.println("GSNM: GSNManager Shutdown() started...");
      terminate();          // Send terminate to main thread and cause interrupt
      int loop = 0;
      while (running) {
        // Do stuff to aid the shutdown
        try {
          sleep(1000);
        } catch (InterruptedException expected) {
        }
        loop++;
      }
      System.err.println("GSNM: Shutdown() of GSNManager is complete.");
    }
  }

  /**
   * Given a edge or gaux, make a string.
   *
   * @param cpuname And edge node name normally.
   * @param sb A string builder to use with the output.
   * @param first This should be true only the first time this routine is called.
   * @throws SQLException
   */
  public void gsnStringBuilder(String cpuname, StringBuilder sb, boolean first) throws SQLException {

    int cpuID;
    Util.clear(sb);//    Boolean onlyone [] = new Boolean[200];

    try {
      String s = "SELECT * FROM edge.gsnstation WHERE updated>TIMESTAMPADD(SECOND,-230,now()) ORDER BY gsnstation";
      if (first) {
        s = "SELECT * FROM edge.gsnstation ORDER BY gsnstation";
      }
      ResultSet rs = dbconn.executeQuery(s);
      prta("Check gsn ncalls=" + ncalls + " sb.len=" + sb.length() + " cpu=" + cpuname + " first=" + first);
      if (!rs.next() && (ncalls++ % 10) != 0 && sb.length() != 0) {
        rs.close();
        return;
      }
      rs.close();
      sb.append("#\n# Form is \"unique_key:Class_of_Thread:Argument line [>][>>filename no extension]\"\n# for ").append(cpuname).append("\n");
      if (netserv.length() > 0) {
        netserv.delete(0, netserv.length());//    Boolean onlyone [] = new Boolean[200];
      }
      if (dadp.length() > 0) {
        dadp.delete(0, dadp.length());//    Boolean onlyone [] = new Boolean[200];
      }
      s = "SELECT * FROM edge.cpu WHERE cpu=\"" + cpuname + "\" ;";
      rs = dbconn.executeQuery(s);
      if (rs.next()) {
        cpuID = rs.getInt("ID");
      } else {
        prta("GSNM: ***** no cpu found for cpu=" + cpuname);
        return;
      }
      rs.close();
      if (dbg) {
        prta("GSNM: cpuid=" + cpuID + " for node=" + cpuname);
      }
      s = "SELECT * FROM edge.role where cpuid=" + cpuID + " OR failovercpuid=" + cpuID + " order by role";
      rs = dbconn.executeQuery(s);
      roles.clear();
      while (rs.next()) {
        RoleInstance r = new RoleInstance(rs);
        if ((!r.isFailedOver() && r.getCpuID() == cpuID) || (r.isFailedOver() && r.getFailoverCpuID() == cpuID)) {
          roles.add(r);
        }
        if (dbg) {
          prta("GSNM: add role=" + r + " roles.size()=" + roles.size() + " cpuid=" + cpuID + " failed=" + r.isFailedOver()
                  + " role.cpuid=" + r.getCpuID() + " failCpuid=" + r.getFailoverCpuID());
        }
      }
      rs.close();
    } catch (SQLException e) {
      Util.SQLErrorPrint(e, "GenEdgeStringBuilder on table anss.cpu SQL failed");
      prta("GSNM: error reading db=" + e);
      throw e;
    }

    for (RoleInstance role : roles) {
      sb.append("#\n# GSN setup file for - ").append(role.getRole()).append("\n");
      String roleip = "localhost";
      try {
        ResultSet rs2 = DBConnectionThread.getThread("anss").executeQuery("SELECT * FROM anss.cpu WHERE cpu='" + role.getRole().trim() + "'");
        if (rs2.next()) {
          roleip = rs2.getString("dotted_name");
          if (dbg) {
            prta("Roleip is " + roleip + " for " + role.getRole() + " role.ip=" + role.getIP());
          }
        }
      } catch (SQLException e) {
        throw e;
      }
      EdgeStation loc = null;
      try {
        String s = "SELECT * FROM edge.gsnstation WHERE roleid=" + role.getID() + " ORDER BY gsnstation;";
        try (ResultSet rs = dbconn.executeQuery(s)) {
          while (rs.next()) {
            loc = new EdgeStation(rs);
            String t = loc.longhaulprotocolString();
            switch (t) {
              case "ISI":
                sb.append(loc.getGSNStation().trim()).append(":ISILink:-s ISI/").
                        append(loc.getGSNStation().trim()).append(".seq ");
                break;
              case "LISS":
                sb.append(loc.getGSNStation().trim()).append(":LISSClient:");
                break;
              case "Comserv":
                sb.append("#").append(loc.getGSNStation().trim()).append(":Comserv\n");

                dadp.append("comserv4 ").append(loc.getGSNStation().substring(2).trim()).
                        append("\ncs2edge -S ").append(loc.getGSNStation().substring(2).trim()).
                        append(" -i ").append(loc.getGSNStation().substring(2).trim().toLowerCase()).
                        append(" -edgeip ").append(roleip).append(" -p 7958 ").
                        append(loc.getOptions()).append("\n");

                continue;
              case "NetServ":
                sb.append(loc.getGSNStation().trim()).append(":NetservClient:");
                break;
              case "SeedLink":
                sb.append(loc.getGSNStation().trim()).append(":SeedLinkClient:").
                        append(loc.getOptions().trim().equals("") ? "" : loc.getOptions().trim() + " ").
                        append(Util.cleanIP(loc.longhaulIP())).
                        append("/").append(loc.longhaulport()).append("/").append(role.getRole());

                break;
              case "NONE":
                sb.append("#").append(loc.getGSNStation().trim()).append(":NONE\n");
                continue;
              default:
                sb.append("#").append(loc.getGSNStation().trim()).
                        append(": does not have a valid configuration=").append(t).append("\n");
                prt(loc + " does not have valid long haul protocoal=" + t + " skip.");
                continue;
            }
            if (!t.equalsIgnoreCase("SeedLink")) {
              sb.append("-h ").append(Util.cleanIP(loc.longhaulIP())).append(" -p ").
                      append(loc.longhaulport()).
                      append(loc.getOptions().trim().equals("") ? "" : " " + (loc.getOptions()).trim());
            }
            if (t.equals("ISI") || t.equals("NetServ")) {
              sb.append(" >> ").append(loc.getGSNStation());
            }
            if (t.equals("SeedLink")) {
              sb.append(" >> sl_").append(loc.getGSNStation().trim().toLowerCase());
            }
            sb.append("\n");
          }
        }
      } catch (SQLException e) {
        Util.SQLErrorPrint(e, "makeEdgemomsetups() on table SQL failed");
        throw e;
      } catch (RuntimeException e) {
        prta("Got RuntimeException in gsnStringBuilder()" + loc + " e=" + e);
        e.printStackTrace(getPrintStream());
        throw e;
      }
    }
  }
}
