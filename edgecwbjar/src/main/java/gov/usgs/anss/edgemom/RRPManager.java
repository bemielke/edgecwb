/*
 *  This software is in the public domain because it contains materials 
 *  that originally came from the United States Geological Survey, 
 *  an agency of the United States Department of Interior. For more 
 *  information, see the official USGS copyright policy at 
 *  http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.edgemom;

import gov.usgs.alarm.SendEvent;
import gov.usgs.anss.db.DBConnectionThread;
import gov.usgs.anss.edgethread.EdgeThread;
import gov.usgs.anss.edgethread.ManagerInterface;
import gov.usgs.anss.util.Util;
import java.io.*;
import java.sql.ResultSet;
import java.sql.SQLException;
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
 * <
 * PRE>
 * switch    value    Description
 * -config  filepath  The output of this thread, i.e. the stations to be acquired (def=log/gsn.setup)
 * -nodb              If set, No database is used in the configuration - the theads are managed from the config file
 * -dbg               If present, more log output is generated.
 * </PRE> *
 *
 * @author davidketchum
 */
public final class RRPManager extends EdgeThread {

  TreeMap<String, ManagerInterface> thr = new TreeMap<>();
  private final ShutdownRRPManager shutdown;
  private static DBConnectionThread dbconn;
  //private static DBConnectionThread dbconnedge;
  private String configFile;
  private boolean dbg;
  private boolean noDB;
  private StringBuilder rrpsb = new StringBuilder(10000);
  private String dataPath = "RRP/";
  private StringBuilder runsb = new StringBuilder(1000);

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
    prta("RRPM: interrupted called!");
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
    statussb.append("RRPM: #client=").append(thr.size()).append(" config=").append(configFile);
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
    monitorsb.append("RRPNthread=").append(thr.size());

    monitorsb.append("\n");
    Iterator<ManagerInterface> itr = thr.values().iterator();
    long totalIn = 0;
    while (itr.hasNext()) {
      ManagerInterface obj = itr.next();
      totalIn += obj.getBytesIn();
    }
    monitorsb.append("RRPNytesIn=").append(totalIn).append("\n");
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
  public RRPManager(String argline, String tg) {
    super(argline, tg);
//initThread(argline,tg);
    String[] args = argline.split("\\s");
    dbg = false;
    configFile = "RRP/rrpserver_edge.setup";
    noDB = false;
    for (int i = 0; i < args.length; i++) {
      //prt(i+" arg="+args[i]);
      if (args[i].trim().length() == 0) {
        continue;
      }
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
        prt(Util.clear(runsb).append("RRPManager: unknown switch=").append(args[i]).
                append(" ln=").append(argline));
      }
    }
    if (Util.getProperty("DBServer") == null) {
      noDB = true;
    } else if (Util.getProperty("DBServer").equalsIgnoreCase("NoDB")) {
      noDB = true;
    } else if (Util.getProperty("DBServer").equals("")) {
      noDB = true;
    }
    prt(Util.clear(runsb).append("RRPM: created args=").append(argline).append(" tag=").append(tag));
    prt(Util.clear(runsb).append("RRPM: config=").append(configFile).append(" dbg=").append(dbg).
            append(" no DB=").append(noDB).append(" DBServer=").append(Util.getProperty("DBServer")));
    shutdown = new ShutdownRRPManager();
    Runtime.getRuntime().addShutdownHook(shutdown);
    if (configFile.contains(Util.FS)) {
      dataPath = configFile.substring(0, configFile.lastIndexOf(Util.FS) + 1);
    } else {
      dataPath = "";
    }
    Util.chkFilePath(configFile);

    setDaemon(true);
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
    if (dbconn == null && !noDB) {
      try {
        dbconn = new DBConnectionThread(Util.getProperty("DBServer"), "readonly", "edge",
                false, false, "edgeRRPM", getPrintStream());
        addLog(dbconn);
        if (!DBConnectionThread.waitForConnection("edgeRRPM")) {
          if (!DBConnectionThread.waitForConnection("edgeRRPM")) {
            if (!DBConnectionThread.waitForConnection("edgeRRPM")) {
              if (!DBConnectionThread.waitForConnection("edgeRRPM")) {
                prt("Failed to get GSN database access");
              }
            }
          }
        }
      } catch (InstantiationException e) {
        prta("InstantiationException: should be impossible");
        dbconn = DBConnectionThread.getThread("edgeRRPM");
      }
    }
    boolean first = true;
    while (true) {
      try {                     // Runtime exception catcher
        if (terminate || Util.isShuttingDown()) {
          break;
        }
        //String [] keys  = new String[thr.size()];
        //keys = thr.keySet().toArray(keys);
        while (true) {
          if (terminate) {
            break;
          }
          //prt("Do Pass= noDB="+noDB+" dbconn="+dbconn);
          if (!noDB && dbconn != null) {
            try {
              Util.clear(rrpsb);
              rrpStringBuilder((Util.getSystemName().equals("igskcicgltgm073") ? "gldketchum3" : Util.getSystemName()), rrpsb, first);
              prta(Util.clear(runsb).append("RRPM: top of loop len=").append(rrpsb.length()).
                      append(" noDB=").append(noDB).append(" dbconnOK=").append(dbconn.isOK()));
              first = false;

              // handle the GSN config file
              if (filesb.length() == 0) {
                Util.readFileToSB(configFile, filesb);
                prta(Util.clear(runsb).append("RRPM: read configuration from disk len=").append(filesb.length()));
              }
            } catch (FileNotFoundException e) {    // Likely this file does not yet exist.
              Util.clear(filesb);
            } catch (IOException e) {
              prta(Util.clear(runsb).append("RRPM: got IO exception reading or writing file e=").append(e));
              e.printStackTrace(getPrintStream());
            } catch (SQLException e) {
              prta(Util.clear(runsb).append("RRPM: got SQLException in rrpStringBuilder e=").append(e));
              e.printStackTrace(this.getPrintStream());
              dbconn.reopen();
              try {
                sleep(120000);
              } catch (InterruptedException expected) {
              }
              break;
            } catch (RuntimeException e) {
              prta(Util.clear(runsb).append("RRPM: got Runtime in rrpStringBuilder e=").append(e));
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
              prta(Util.clear(runsb).append("RRPM: got IO exception reading file ").append(configFile).
                      append(" e=").append(e));
              e.printStackTrace(getPrintStream());
            }
            Util.clear(rrpsb);
            rrpsb.append(filesb);
          }

          // If the file has not changed, check on down threads. If it has changed, always check to top
          if (!Util.stringBuilderEqual(rrpsb, filesb) && rrpsb.length() > 20) {
            try {
              Util.writeFileFromSB(configFile, rrpsb);         // write out the file
            } catch (IOException e) {
              prta(Util.clear(runsb).append("RRPM: got IO exception writing file ").append(configFile).
                      append(" e=").append(e));
              e.printStackTrace(getPrintStream());
            }

            prta("RRPM: *** files have changed ****");
            prta(Util.clear(runsb).append("RRPM: file len=").append(filesb.length()).append("\n").
                    append(filesb).append("|"));
            prta(Util.clear(runsb).append("RRPM: rrpsb len=").append(rrpsb.length()).append("\n").
                    append(rrpsb).append("|"));
            Util.clear(filesb);    // Force file to be reread next cycle
            break;
          } else {
            if (dbg || loop++ % 10 == 1) {
              prta(Util.clear(runsb).append("RRPM: files are same ").append(configFile).
                      append(" loop=").append(loop));
            }
            // Check to make sure all of our children threads are alive
            if (dbg) {
              prta(Util.clear(runsb).append("RRPM: file=").append(filesb.length()).append("\n").
                      append(filesb).append("|"));
              prta(Util.clear(runsb).append("RRPM: rrpsb=").append(rrpsb.length()).append("\n").
                      append(rrpsb).append("|"));
            }
            //thr.get(keys[2]).terminate();thr.get(keys[6]).terminate();   //DEBUG: force some down!

          }
          if (System.currentTimeMillis() % 86400000L < 240000) {
            dbconn.setLogPrintStream(getPrintStream());
            //dbconnedge.setLogPrintStream(getPrintStream());
          }
          try {
            sleep(120000);
          } catch (InterruptedException expected) {
          }        
        }     // while(true)
      } catch (RuntimeException e) {
        prta(Util.clear(runsb).append("RRPM: RuntimeErr e=").append(e));
        SendEvent.edgeSMEEvent("RuntimeExcp", "in RRPManager", this);
        e.printStackTrace(getPrintStream());
      }
      // The main loop has exited so the thread needs to exit

    }     // while(true)
    prta("RRPM: ** RRPManager terminated ");
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
  class ShutdownRRPManager extends Thread {

    /**
     * Default constructor does nothing. The shutdown hook starts the run()
     * thread.
     */
    public ShutdownRRPManager() {
      gov.usgs.anss.util.Util.prta("new ThreadShutdown " + getName() + " " + getClass().getSimpleName());
    }

    // This is called by the RunTime.shutdown at any termination 
    @Override
    public void run() {
      System.err.println("RRPM: RRPManager Shutdown() started...");
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
      System.err.println("RRPM: Shutdown() of RRPManager is complete.");
    }
  }

  /**
   * Given a edge or gaux, make a string.
   *
   * @param cpuname And edge node name normally.
   * @param sb A string builder to use with the output.
   * @param first Set true the first time to force full configuration
   * @throws SQLException
   */
  public void rrpStringBuilder(String cpuname, StringBuilder sb, boolean first) throws SQLException {

    if (!first) {
      String s = "SELECT * FROM edge.gsnstation WHERE longhaulprotocol in ('Edge','EdgeGeo') AND updated>TIMESTAMPADD(SECOND,-230,now()) ORDER BY gsnstation";
      ResultSet rs = dbconn.executeQuery(s);
      if (!rs.next()) {
        rs.close();
        return;
      }
      rs.close();
    }
    String s = "SELECT * FROM edge.gsnstation WHERE longhaulprotocol IN ('Edge','EdgeGeo') ORDER BY gsnstation";
    ResultSet rs = dbconn.executeQuery(s);
    Util.clear(sb);
    sb.append("#\n# Form is \"-t NNSSSS -ip nn.nn.nn.nn -f path/NNSSSS.ring [args from gsnstation table cpu=").append(cpuname).append("\n");
    while (rs.next()) {
      sb.append("-t ").append(rs.getString("gsnstation")).append(" -ip ").append(Util.cleanIP(rs.getString("longhaulip"))).
              append(" -f ").append(dataPath).append(rs.getString("gsnstation")).append(".state ").
              append((rs.getString("options").trim().equals("") ? "" : rs.getString("options"))).append("\n");
    }
  }

  public static void main(String[] args) {
    Util.setModeGMT();
    Util.init("edge.prop");
    RRPManager mgr = new RRPManager("-empty >>rrpmgr", "RRPMGR");
    while (mgr.isAlive()) {
      Util.sleep(1000);
    }
  }
}
