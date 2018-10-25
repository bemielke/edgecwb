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
import gov.usgs.anss.edge.EdgeProperties;
import gov.usgs.anss.edge.IndexFile;
import gov.usgs.anss.edgethread.EdgeThread;
import gov.usgs.anss.util.Util;
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * This is a template for an EdgeThread (one that can be run by EdgeMom). It
 * includes a timeout on the run() and a registered shutdown handler.  Since Q680 
 * and Q730s are no longer in use, this is likely obsolete.
 *
 * At a minimum Q680Manager should be replaced to the new class And GSNM
 * replaces with a short hand for the class. (ETT??).
 *
 * @author davidketchum
 */
public final class Q680Manager extends EdgeThread {

  private ShutdownQ680Manager shutdown;
  private static DBConnectionThread dbconn;
  private String configFile;
  private boolean dbg;
  private StringBuilder gsnsb = new StringBuilder(10000);

  /**
   * Set the debug state.
   *
   * @param t The new debug state
   */
  public void setDebug(boolean t) {
    dbg = t;
  }

  /**
   * Terminate the thread (causes an interrupt to be sure). You may not need the
   * interrupt!
   */
  @Override
  public void terminate() {
    terminate = true;
    interrupt();
  }

  public StringBuilder getConfig() {
    return gsnsb;
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
    return statussb.append("Q680M: config=").append(configFile);
  }

  /**
   * Return the monitor string for Icinga.
   *
   * @return A String representing the monitor key value pairs for this
   * EdgeThread
   */
  @Override
  public StringBuilder getMonitorString() {
    if (monitorsb.length() > 0) {
      monitorsb.delete(0, monitorsb.length());
    }
    return monitorsb;
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
   * the host/port source of data. This one gets its arguments from a command
   * line
   *
   * @param argline The command line
   * @param tg The logging tag
   */
  public Q680Manager(String argline, String tg) {
    super(argline, tg);
//initThread(argline,tg);
    String[] args = argline.split("\\s");
    dbg = false;
    configFile = "q680.setup";
    for (int i = 0; i < args.length; i++) {
      //prt(i+" arg="+args[i]);
      if (args[i].equals("-dbg")) {
        dbg = true;
      } else if (args[i].equals("-config")) {
        configFile = args[i + 1];
        i++;
      } else if (args[i].equals("-rtmsdbg")) ; // Pass this on only
      else if (args[i].equals("-empty")) ; else if (args[i].equals("-dbgdata")) {
        i++;
      } // else if(args[i].equals("-h")) { host=args[i+1]; i++;}
      else if (args[i].substring(0, 1).equals(">")) {
        break;
      } else {
        prt("Q680Manager: unknown switch=" + args[i] + " ln=" + argline);
      }
    }
    prt("Q680M: created args=" + argline + " tag=" + tag + " configfile=" + configFile);
    shutdown = new ShutdownQ680Manager();
    Runtime.getRuntime().addShutdownHook(shutdown);
    if (dbconn == null) {
      try {
        dbconn = new DBConnectionThread(Util.getProperty("DBServer"), "readonly", "anss", false, false, "anssQ680", getPrintStream());
        if (!DBConnectionThread.waitForConnection("anssQ680")) {
          if (!DBConnectionThread.waitForConnection("anssQ680")) {
            if (!DBConnectionThread.waitForConnection("anssQ680")) {
              if (!DBConnectionThread.waitForConnection("anssQ680")) {
                prt("****** Failed to get Q680/ANSS database access");
              }
            }
          }
        }
        addLog(dbconn);
      } catch (InstantiationException e) {
        prta("InstantiationException: should be impossible");
        dbconn = DBConnectionThread.getThread("anssQ680");
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
    BufferedReader in = null;
    StringBuilder filesb = new StringBuilder(10000);
    int n = 0;
    int loop = 0;
    running = true;
    while (true) {
      if (terminate) {
        break;
      }
      try {
        q680StringBuilder(gsnsb);
      } catch (RuntimeException e) {
        prta("Q680M: got Runtime in gsnStringBuilder e=" + e);
        e.printStackTrace(this.getPrintStream());
      }

      // If the file has not changed check on down threads. If it has changed always to top
      if (!gsnsb.toString().equals(filesb.toString()) && gsnsb.length() > 20) {
        writeFile(configFile, gsnsb);         // write out the file
        prta("Q680M: *** files have changed ****");
        readFile(configFile, filesb);
      } else {
        if (dbg || loop++ % 10 == 1) {
          prta("Q680M: files are same");
        }
        //thr.get(keys[2]).terminate();thr.get(keys[6]).terminate();   //DEBUG: force some down!
        try {
          sleep(120000);
        } catch (InterruptedException expected) {
        }
        if (System.currentTimeMillis() % 86400000L < 240000) {
          dbconn.setLogPrintStream(getPrintStream());
        }
      }
    }    // while(true)
    prta("Q680M: ** Q680Manager terminated ");
    try {
      Runtime.getRuntime().removeShutdownHook(shutdown);
    } catch (Exception expected) {
    }
    running = false;            // Let all know we are not running
    terminate = false;          // sign that a terminate is no longer in progress
  }

  /**
   * This internal class gets registered with the Runtime shutdown system and is
   * invoked when the system is shutting down. This must cause the thread to
   * exit.
   */
  class ShutdownQ680Manager extends Thread {

    /**
     * Default constructor does nothing. The shutdown hook starts the run()
     * thread.
     */
    public ShutdownQ680Manager() {
      gov.usgs.anss.util.Util.prta("new ThreadShutdown " + getName() + " " + getClass().getSimpleName());
    }

    // This is called by the RunTime.shutdown at any termination 
    @Override
    public void run() {
      System.err.println("Q680M: Q680Manager Shutdown() started...");
      terminate();          // send terminate to main thread and cause interrupt
      int loop = 0;
      while (running) {
        // Do stuff to aid the shutdown
        try {
          sleep(1000);
        } catch (InterruptedException expected) {
        }
        loop++;
      }
      System.err.println("Q680M: Shutdown() of Q680Manager is complete.");
    }
  }

  /**
   *
   * @author rjackson
   */
  /**
   * Given a edge or gaux, make a string.
   *
   * @param sb A string builder to use with the output.
   */
  public void q680StringBuilder(StringBuilder sb) {

    if (sb.length() > 0) {
      sb.delete(0, sb.length());//    Boolean onlyone [] = new Boolean[200];
    }
    int failures = 0;
    while (true) {
      sb.append("#\n# Form is \"STATION:-ip ip.dot.ted.adr  [>][>>filename no extension]\"\n");
      try {
        //                    1         2      3            4         5         6       7      8
        String s = "SELECT tcpstation,ipadr,nsnstation.id,maxtbase,starttbase,cont40,cont20,expbias,"
                + // 9       10        11         12      13       14          15          16            17
                "lowgain,highgain,smtriglevel,dac480,attn480,seismometer,seismometer2,quanterratype,seednetwork "
                + "FROM nsnstation,tcpstation left join cpu on cpu.id=cpuid  WHERE tcpstation=nsnstation AND "
                + "(quanterratype='Q680' OR quanterratype='Q730') AND (cpu regexp 'gacq1' or cpu regexp 'vdldfc9') ORDER BY tcpstation;";
        try (ResultSet rs = dbconn.executeQuery(s)) {
          while (rs.next()) {
            sb.append(rs.getString(1)).append(":-ip ");
            sb.append(Util.cleanIP(rs.getString(2)));
            sb.append(" -id ").append(rs.getInt(3)).append(" -tbmax ").append(rs.getInt(4));
            sb.append(" -tb ").append(rs.getInt(5)).append(" -c40 ").append(rs.getString(6));
            sb.append("" + " -c20 ").append(rs.getString(7)).append(" -exp ");
            sb.append(rs.getInt(8)).append(" -hg ").append(rs.getString(10)).append(" -sm ");
            sb.append(rs.getString(9)).append(" -smlev ").append(rs.getInt(11) < 5000 ? 5000 : rs.getInt(11));
            sb.append(" -dac480 ").append(rs.getInt(12)).append(" -attn480 ").append(rs.getInt(13));
            sb.append(" -seistype ").append(rs.getString(14)).append(" -seistype2 ");
            sb.append(rs.getString(15)).append(" -qtype ").append(rs.getString(16));
            sb.append(" -net ").append(rs.getString(17)).append("\n");
          }
        }
        break;
      } catch (SQLException e) {
        Util.SQLErrorPrint(e, "q680StringBuilder() on table SQL failed = try again nfail=" + failures);
        prta("q680StringBuilder() query failed on tcpstation - try again.  nfail=" + failures + " ...");
        dbconn.reopen();
        e.printStackTrace(getPrintStream());
      } catch (RuntimeException e) {
        prta("Got RuntimeException in q680StringBuilder() continue e=" + e);
        e.printStackTrace(getPrintStream());
      }
      try {
        sleep(10000);
      } catch (InterruptedException expected) {
      }
      failures++;
      if (failures > 20) {
        SendEvent.debugEvent("Q680MFail", "Repeated failure to read config in Q680 manager on " + Util.getNode(), this);
      }
    }
  }

  public static void writeFile(String filename, StringBuilder wfsb) {
    //StringBuilder sb = new StringBuilder(100);
    RandomAccessFile fileout;
    try {

      fileout = new RandomAccessFile(filename, "rw");
    } catch (FileNotFoundException e) {
      System.err.println("Error writing to file" + e.getMessage());
      return;
    }
    try {
      String r = wfsb.toString();
      fileout.writeBytes(r);
      fileout.setLength((long) r.length());
      fileout.close();
    } catch (IOException e) {
      System.err.println("Error writing to file" + e.getMessage());
    }
  }

  public void readFile(String filename, StringBuilder rfsb) {
    //StringBuilder sb = new StringBuilder(100);
    RandomAccessFile filein;
    //boolean noteof=true;
    String f = null;
    int length;
    try {
      filein = new RandomAccessFile(filename, "r");
    } catch (FileNotFoundException e) {
      System.err.println("Error reading from file" + e.getMessage());
      return;
    }
    try {
      length = (int) filein.length();
      byte b[] = new byte[length];
      filein.readFully(b, 0, length);
      filein.close();
//       StringBuilder r = new StringBuilder();
      if (rfsb.length() > 0) {
        rfsb.delete(0, rfsb.length());
      }
      rfsb.append(new String(b));
//       for (int i = 1;i<b.length;i++) rfsb.append(b[i]);
    } catch (IOException e) {
      System.err.println("Error writing to file" + e.getMessage());
    }

  }

  public static void main(String[] args) {

    IndexFile.init();
    EdgeProperties.init();
    Q680Manager q680Manager = new Q680Manager("-empty", "Q680M:");
  }
}
