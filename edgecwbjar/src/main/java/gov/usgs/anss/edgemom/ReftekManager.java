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
import gov.usgs.anss.edgethread.EdgeThread;
import gov.usgs.anss.edgethread.ManagerInterface;
import gov.usgs.anss.util.Util;
import java.io.BufferedReader;
import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Iterator;

/**
 * ReftekManager.java - This thread writes out a configuration file with one
 * line for each reftek configured in the anss.reftek table. That table is
 * dedicated to real time refteks coming from an RTPD server. This thread starts
 * a RefekClient thread which actuall uses the configuration file to interpret
 * the packets coming from the RTPD. The config file contains information about
 * how to change serial number to network and seed station code, and how to
 * change stream names into channel codes.
 *
 * <PRE>
 * Switch  arg        Description
 * -config filepath The configuration file is kept here (default=config/reftek.setup)
 * -dbg             Debug output is desired
 *
 * These switches are for the ReftekClient task (a child to this one)
 *Switch   arg        Description
 *-h       host    The host ip address or DNS translatable name of the RTPD server
 *-p       pppp    The port to connect to on the RTPD (def=2543)
 *-dbg             Debug output is desired
 *-noudpchan       Do not send results to the UdpChannel server for display in things like channel display
 *-nohydra         Do not send the resulting data to Hydra (usually used on test systems to avoid Hydra duplicates)
 * -nosnw          Do not send any data to SeisNetWatch
 * -nosnwcmd
 * </PRE>
 *
 * @author davidketchum ketchum at usgs.gov
 */
public final class ReftekManager extends EdgeThread {

  private ReftekClient thr;
  private final ShutdownReftekManager shutdown;
  private static DBConnectionThread dbconn;
  private static DBConnectionThread dbconnedge;
  private static boolean configChanged;
  private String configFile;
  private boolean dbg;
  private StringBuilder refsb = new StringBuilder(10000);
  //private DecimalFormat df2 = new DecimalFormat("0.00");
  private String arglineorg;
  //private ArrayList<Role> roles = new ArrayList<Role>(10);
  private byte[] b = new byte[10000];

  public static boolean configChanged() {
    return configChanged;
  }

  /**
   * Set debug state.
   *
   * @param t The new debug state
   */
  public void setDebug(boolean t) {
    dbg = t;
  }

  /**
   * terminate thread (causes an interrupt to be sure) you may not need the
   * interrupt!
   */
  @Override
  public void terminate() {
    terminate = true;
    interrupt();
  }

  /**
   * Return the status string for this thread.
   *
   * @return A string representing status for this thread.
   */
  @Override
  public StringBuilder getStatusString() {
    if (statussb.length() > 0) {
      statussb.delete(0, statussb.length());
    }
    statussb.append("RTM: #client=").append(thr).append(" config=").append(configFile); //.append(" roles: ");
    //for(int i=0; i<roles.size(); i++) statussb.append(roles.get(i).getRole()).append(" ");
    statussb.append("\n");
    statussb.append(thr.getStatusString());

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
    if (thr != null) {
      return thr.getMonitorString();
    } else {
      return monitorsb;
    }
  }

  /**
   * Return console output - this is fully integrated so it never returns
   * anything.
   *
   * @return "" since this cannot get output outside of the prt() system
   */
  @Override
  public StringBuilder getConsoleOutput() {
    return consolesb;
  }

  /**
   * Creates an new instance of LISSCLient - which will try to stay connected to
   * the host/port source of data. This one gets it's arguments from a command
   * line.
   *
   * @param argline The command line.
   * @param tg The logging tag.
   */
  public ReftekManager(String argline, String tg) {
    super(argline, tg);
//initThread(argline,tg);
    String[] args = argline.split("\\s");
    dbg = false;
    configFile = "config/reftek.setup";
    arglineorg = argline;
    int dummy = 0;
    for (int i = 0; i < args.length; i++) {
      //prt(i+" arg="+args[i]);
      if (args[i].equals("-dbg")) {
        dbg = true;
      } else if (args[i].equals("-config")) {
        configFile = args[i + 1];
        i++;
      } // These switches are handled by the child ReftekClient
      else if (args[i].equals("-h")) {
        i++;
      } else if (args[i].equals("-noudpchan")) {
        dummy = 1;
      } else if (args[i].equals("-rawread")) {
        dummy = 1;
      } else if (args[i].equals("-XX")) {
        dummy = 1;
      } else if (args[i].equals("-nohyra")) {
        dummy = 1;
      } else if (args[i].equals("-nosnw")) {
        dummy = 1;
      } else if (args[i].equals("-nosnwcmd")) {
        dummy = 1;
      } else if (args[i].substring(0, 1).equals(">")) {
        break;
      } else {
        prt("ReftekManager: unknown switch=" + args[i] + " ln=" + argline);
      }
    }
    prt("RTM: created args=" + argline + " tag=" + tag);
    shutdown = new ShutdownReftekManager();
    Runtime.getRuntime().addShutdownHook(shutdown);
    if (dbconn == null) {
      try {
        if (DBConnectionThread.getThread("anss") == null) {
          dbconn = new DBConnectionThread(Util.getProperty("DBServer"), "readonly", "anss", false, false, "anss", getPrintStream());
          if (!DBConnectionThread.waitForConnection("anss")) {
            if (!DBConnectionThread.waitForConnection("anss")) {
              prt("RTM: Failed to get anss GSN access");
            }
          }
          addLog(dbconn);
        }
        if (DBConnectionThread.getThread("edge") == null) {
          dbconnedge = new DBConnectionThread(Util.getProperty("DBServer"), "readonly", "edge", false, false, "edge", getPrintStream());
          addLog(dbconnedge);
          if (!DBConnectionThread.waitForConnection("edge")) {
            if (!DBConnectionThread.waitForConnection("edge")) {
              prt("RTM: Failed to get edge Reftek access");
            }
          }
        }
      } catch (InstantiationException e) {
        prta("RTM: InstantiationException: should be impossible");
        dbconn = DBConnectionThread.getThread("edgeRTM");
      }
    }
    start();
  }

  public ReftekClient getReftekClient() {
    return thr;
  }

  /**
   * This Thread keeps a socket open to the host and port, reads in any
   * information on that socket, keeps a list of unique StatusInfo, and updates
   * that list when new data for the unique key comes in.
   */
  @Override
  public void run() {
    running = true;
    Iterator<ManagerInterface> itr = null;
    BufferedReader in = null;
    StringBuilder filesb = new StringBuilder(10000);

    // If this ReftekManager has been restarted, it may have an active ReftekClient - kill it or die trying
    if (thr != null) {
      thr.terminate();
      int loop = 0;
      while (thr.isAlive()) {
        try {
          sleep(1000);
        } catch (InterruptedException expected) {
        }
        loop++;
        if (loop % 60 == 59) {
          SendEvent.edgeEvent("RefkClNoExit", "ReftekMan - client will not exit.  Stop EdgeMom", this);
          Util.exit(1);
        }
      }
    }
    thr = new ReftekClient(arglineorg + "cl", tag + "RTC");      // Start the connection to the RTPD for all of the stations
    int n = 0;
    int loop = 0;
    String s;
    try {
      sleep(2000);
    } catch (InterruptedException expected) {
    }
    while (true) {
      try {
        if (dbconn == null || dbconnedge == null) {
          try {
            prta("RTM: Open DBCONN to " + Util.getProperty("DBServer"));
            if (DBConnectionThread.getThread("anss") == null) {
              dbconn = new DBConnectionThread(Util.getProperty("DBServer"), "readonly", "anss", false, false, "anss", getPrintStream());
              if (!DBConnectionThread.waitForConnection("anss")) {
                if (!DBConnectionThread.waitForConnection("anss")) {
                  prt("RTM: Failed to get anss GSN access");
                }
              }
              addLog(dbconn);
            } else {
              dbconn = DBConnectionThread.getThread("anss");
            }
            if (DBConnectionThread.getThread("edge") == null) {
              dbconnedge = new DBConnectionThread(Util.getProperty("DBServer"), "readonly", "edge", false, false, "edge", getPrintStream());
              addLog(dbconnedge);
              if (!DBConnectionThread.waitForConnection("edge")) {
                if (!DBConnectionThread.waitForConnection("edge")) {
                  prt("RTM: Failed to get edge Reftek access");
                }
              }
            } else {
              dbconnedge = DBConnectionThread.getThread("edge");
            }
          } catch (InstantiationException e) {
            prta("RTM: InstantiationException: should be impossible");
            dbconn = DBConnectionThread.getThread("edgeRTM");
          }
        }
        if (terminate) {
          break;
        }
        try {
          reftekStringBuilder((Util.getNode().equals("gldketchum3") ? "gldketchum3" : Util.getNode()), refsb);
        } catch (RuntimeException e) {
          prta("RTM: got Runtime in gsnStringBuilder e=" + e);
          e.printStackTrace(this.getPrintStream());
        }

        try {
          // handle the Reftek config file
          Util.readFileToSB(configFile, filesb);

          // If the file has not changed check on down threads, if it has changed always to top
          if (!Util.stringBuilderEqual(refsb, filesb) && refsb.length() > 20) {
            Util.writeFileFromSB(configFile, refsb);         // write out the file
            prta("RTM: *** files have changed ****");
            configChanged = true;
          } else {
            configChanged = false;
            if (dbg || loop++ % 10 == 1) {
              prta("RTM: files are same");
            }
            //thr.get(keys[2]).terminate();thr.get(keys[6]).terminate();   //DEBUG: force some down!
            try {
              sleep(120000);
            } catch (InterruptedException expected) {
            }
          }
        } catch (IOException e) {
          prta("RTM: got IO exception reading/writing file " + configFile + " e=" + e);
          e.printStackTrace(getPrintStream());
        }
        if (dbconn == null || dbconnedge == null) {
          prta("RTM: ***** dbconn is null or dbedge is null " + dbconn + " " + dbconnedge);
        }
        if (System.currentTimeMillis() % 86400000 < 240000) {
          dbconn.setLogPrintStream(getPrintStream());
          dbconnedge.setLogPrintStream(getPrintStream());
        }
      } catch (RuntimeException e) {
        prta("RTM: got runtime exception e=" + e);
        SendEvent.edgeSMEEvent("RefRuntime", "Reftek manager runtime exception e=" + e, this);
        e.printStackTrace();
      }
    }

    // The main loop has exited so the thread needs to exit
    prta("RTM: start termination of ManagerInterfaces thr=" + thr);
    thr.terminate();
    prta("RTM: ** ReftekManager terminated ");
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
  class ShutdownReftekManager extends Thread {

    /**
     * Default constructor does nothing. The shutdown hook starts the run()
     * thread.
     */
    public ShutdownReftekManager() {
      gov.usgs.anss.util.Util.prta("RTM: new ThreadShutdown " + getName() + " " + getClass().getSimpleName());
    }

    // This is called by the RunTime.shutdown at any termination
    @Override
    public void run() {
      System.err.println("RTM: ReftekManager Shutdown() started...");
      thr.terminate();
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
      System.err.println("RTM: Shutdown() of ReftekManager is complete.");
    }
  }

  /**
   * Given a edge or gaux, make a string.
   *
   * @param cpuname And edge node name normally.
   * @param sb A string builder to use with the output.
   */
  public void reftekStringBuilder(String cpuname, StringBuilder sb) {

    if (sb.length() > 0) {
      sb.delete(0, sb.length());//    Boolean onlyone [] = new Boolean[200];
    }
    try {
      //Statement stmt = JDBConnection.getConnection("edge").createStatement();   // Used for query
      String s = "SELECT * FROM anss.reftek ORDER BY network,station;";
      if (dbconn == null) {
        prta("DBCONN is null!!! " + dbconn);
      }
      try (ResultSet rs = dbconn.executeQuery(s)) {
        String station;
        String network;
        String serial;
        String ipadr;
        while (rs.next()) {
          station = rs.getString("station");
          network = rs.getString("network");
          serial = rs.getString("serial");
          ipadr = rs.getString("ipadr");
          String codea = " ";
          if (rs.getString("comment").contains("codea")) {
            codea = "-";
          }
          sb.append(network).append((station + "     ").substring(0, 5)).append(codea).
                  append((serial + "    ").substring(0, 4)).append(" ").
                  append((ipadr + "                 ").substring(0, 15));
          for (int j = 1; j < 7; j++) {
            int stream = rs.getInt("stream" + j);
            double rate = rs.getDouble("rate" + j);
            String chans = rs.getString("chans" + j);
            String components = rs.getString("components" + j);
            String location = rs.getString("location" + j);
            String band = rs.getString("band" + j);
            if (chans.equals("") || rate == 0.) {  // Nothing is configured, check that this is true
              if (rate != 0.0 && (stream != 0 || !components.equals("")
                      || !location.equals("") || chans.length() != 3 || components.length() != 3)) {
                prta("Stream " + j + " is not complete configured as empty!");
                SendEvent.edgeSMEEvent("ReftekCnfErr",
                        "Stream " + j + " is not complete " + ipadr + " "
                        + network + " " + station + " " + serial + " chans=" + chans + " comp=" + components, this);
              }
            } else {
              sb.append((stream + "  ").substring(0, 2)).append(" ").
                      append((location + "     ").substring(0, 5)).
                      append((chans + "      ").substring(0, 6)).append(" ").
                      append((components + "      ").substring(0, 6)).append(" ").
                      append((Util.df22.format(rate) + "     ").substring(0, 7)).append(" ").
                      append((band + "  ").substring(0, 4)).append(" ");
            }
          }       // For each channel defined
          sb.append("\n");
        }
      }
    } catch (SQLException e) {
      Util.SQLErrorPrint(e, "makeEdgemomsetups() on table SQL failed");
    } catch (RuntimeException e) {
      prta("RTM: Got RuntimeException in reftekStringBuilder() e=" + e);
      e.printStackTrace(getPrintStream());
    }
  }
}
