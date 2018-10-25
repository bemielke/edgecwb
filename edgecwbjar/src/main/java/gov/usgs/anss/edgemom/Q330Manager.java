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
import gov.usgs.anss.guidb.TCPStation;
import gov.usgs.anss.util.UC;
import gov.usgs.anss.util.Util;
import java.io.*;
import java.net.InetAddress;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Iterator;
import java.util.TreeMap;

/**
 * Q330ManagerEdgeThread - This is an EdgeThread which reads the Q300
 * configuration file and configures Q330 receivers (anssq330) to run and
 * creates the MiniSeedServers for each Q330 to receive the data. It
 * periodically monitors changes in the Q330 configuration file and starts or
 * restarts Q330s as needed. It periodically rereads the database and changes
 * the Q330s configuration file as needed. If one second output is selected,
 * then a Q330OneSecondServer must be set up on the target node and port. Note
 * that since one second data is normally setup to feed Hydra, the -nohydra
 * option is set here to prevent the 512 blocks from also feeding to Hydra.
 * <br>
 * <PRE>
 *switch			arg      Description
 *-f					file     The config file to use (default is ./Q330s.setup).
 *-roles			gacq?    A regular expression matching the Q330 assigned cpu roles to process (normally same as role on this cpu).
 *-edgeip			nn.nn.nn The IP address this ConfigServer is running on (the ip target for anssq330).
 *-edgeport		pppp		 The port to send Q330 miniseed data.
 *-onesecip		nn.nn.nn The IP address of the target for 1 second packets.
 *-onesecport pppp		 The port to send the one second data.
 *-verbosity	vvv			 Set Q330LIB verbosity to vvv (2 is default 15, is pretty much everything).
 *-noudpchan					 Do not send data summary to UdpChannel for ChannelDisplay (MiniSeedServer option).
 *-nohydra						 Do not send data received to Hydra (normally set since 1 sec feeds hydra) MiniSeedServer option.
 *-dbgchan						 Set the -dbg flag on all of the MiniSeedServers.
 * -nodb							 If set, no configuration from a database is done - the last configuration file is used.
 *-dbg								 Turn on debugging in this Q330Manager.
 * </PRE>
 *
 * @author davidketchum
 */
public final class Q330Manager extends EdgeThread {

  private TreeMap<String, Q330> thr;          // Tree map to the threads running the Q330s
  private long lastStatus;     // The time that the last status was received
  private DBConnectionThread dbconn;
  private static FileQ330 fileThread;
  private long inbytes;         // The number of input bytes processed
  private long outbytes;        // The number of output bytes processed
  private String configFile;            // Config file name (one line per q330 on line)
  private String edgeip;                // Edge ip address to send Q330 data
  private int edgeport;                 // Edge port offset to add to IPPORT for each Q330 for MiniSeedServer
  private String onesecip;              // Edge computer to receive 1 second data for hydra
  private int onesecport;               // Edge port for receiving ip data
  private String dbHost;
  private String miniSeedServerLine;
  private int dataport;                 // Default data port to use
  private boolean noDB;                 // If try, hand configuration
  private String roles;                 // In a SQL Query, this is to match the cpu assignment (gacq1 default)
  private int verbosity;
  private final ShutdownQ330Manager shutdown;

  @Override
  public String toString() {
    return configFile + " #thr=" + thr.size();
  }

  private boolean dbg;

  /**
   * Return the number of bytes processed as input.
   *
   * @return The number of bytes processed as input.
   */
  public long getInbytes() {
    Iterator<Q330> itr = thr.values().iterator();
    long nb = 0;
    while (itr.hasNext()) {
      nb = itr.next().getBytesIn();
    }
    return nb;
  }

  /**
   * Return the number of bytes processed as input.
   *
   * @return The number of bytes processed as input.
   */
  public long getOutbytes() {
    return outbytes;
  }

  /**
   * Set the debug flag.
   *
   * @param t What to set debug to!
   */
  public void setDebug(boolean t) {
    dbg = t;
  }

  /**
   * Creates a new instance of Q330ManagerEdgeThread - This one gets its
   * arguments from a command line.
   *
   * @param argline The EdgeThread arguments.
   * @param tg The EdgeThread tag for output.
   */
  public Q330Manager(String argline, String tg) {
    super(argline, tg);
    String[] args = argline.split("\\s");
    dbg = false;
    edgeip = "127.0.0.1";       // Local host default
    edgeport = 10000;           // This is really an offset to the "ipport" for the q330
    onesecip = "127.0.0.1";
    onesecport = 7968;
    dbHost = Util.getProperty("DBServer");
    configFile = "Q330s.setup";
    miniSeedServerLine = "";
    roles = "gacq1";
    verbosity = 2;
    dataport = 0;
    noDB = false;
    if (thr == null) {
      thr = new TreeMap<>();
    }
    for (int i = 0; i < args.length; i++) {
      //prt(i+" arg="+args[i]);
      if (args[i].equals("-dbg")) {
        dbg = true;
      } else if (args[i].equals("-roles")) {
        roles = args[i + 1];
        i++;
      } else if (args[i].equals("-f")) {
        configFile = args[i + 1];
        i++;
      } else if (args[i].equals("-dataport")) {
        dataport = Integer.parseInt(args[i + 1]);
        i++;
      } else if (args[i].equals("-edgeip")) {
        edgeip = args[i + 1];
        i++;
      } else if (args[i].equals("-onesecip")) {
        onesecip = args[i + 1];
        i++;
      } else if (args[i].equals("-edgeport")) {
        edgeport = Integer.parseInt(args[i + 1]);
        i++;
      } else if (args[i].equals("-onesecport")) {
        onesecport = Integer.parseInt(args[i + 1]);
        i++;
      } else if (args[i].equals("-noudpchan")) {
        miniSeedServerLine += "-noudpchan ";         // MiniSeedServer option
      } else if (args[i].equals("-nohydra")) {
        miniSeedServerLine += "-nohydra ";           // MiniSeedServer option
      } else if (args[i].equals("-dbgchan")) {
        miniSeedServerLine += "-dbg ";           // MiniSeedServer option
      } else if (args[i].equals("-empty")) {
      } // Allow this for totally empty command lines
      else if (args[i].equalsIgnoreCase("-nodb")) {
        noDB = true;
      } else if (args[i].equals("-verbosity")) {
        verbosity = Integer.parseInt(args[i + 1]);
        i++;
      } else if (args[i].substring(0, 1).equals(">")) {
        break;
      } else {
        prt("Q330ManagerEdgeThread unknown switch=" + args[i] + " ln=" + argline);
      }
    }
    if (Util.getProperty("DBServer") == null) {
      noDB = true;
    } else if (Util.getProperty("DBServer").equalsIgnoreCase("NoDB")) {
      noDB = true;
    } else if (Util.getProperty("DBServer").equals("")) {
      noDB = true;
    }
    prt("Q330M: 1sec=" + onesecip + "/" + onesecport + " dbhost=" + dbHost + " edge=" + edgeip + "/" + edgeport
            + " config=" + configFile + " dbg=" + dbg + " verb=" + verbosity + " noDB=" + noDB);
    tag = tg;
    shutdown = new ShutdownQ330Manager(this);
    Runtime.getRuntime().addShutdownHook(shutdown);
    start();
  }

  @Override
  public void terminate() {
    // Set terminate to interrupt.  If the IO might be blocking, it should be closed here
    if (terminate == true) {
      return;     // Already been called
    }
    terminate = true;
    interrupt();
    fileThread.terminate();
  }

  /**
   * This Thread keeps a socket open to the host and port, reads in any
   * information on that socket, keeps a list of unique StatusInfo, and updates
   * that list when new data for the unique key comes in.
   */
  @Override
  public void run() {
    running = true;               // Mark that we are running
    Iterator<Q330> itr;
    BufferedReader in;
    int n;
    String s;
    long lastFileCheck = System.currentTimeMillis();
    int lastLoop = 0;
    try {
      sleep(1000);
    } catch (InterruptedException expected) {
    }
    dbconn = DBConnectionThread.getThread("anss");
    if (dbconn == null && Util.getProperty("DBServer") != null && !noDB) {
      try {
        dbconn = new DBConnectionThread(Util.getProperty("DBServer"), "readonly", "anss", false, false, "anss", getPrintStream());
        if (!DBConnectionThread.waitForConnection("anss")) {
          if (!DBConnectionThread.waitForConnection("anss")) {
            prta("Q330M:did not connection to anss.");
          }
        }
        addLog(dbconn);

      } catch (InstantiationException e) {
        prta("Q330Manager opeing anss thread. e=" + e);
      }
    }
    if (fileThread == null) {
      fileThread = new FileQ330(dbHost, configFile);
    }
    try {
      sleep(10000);
    } catch (InterruptedException expected) {
    }

    while (!terminate) {
      try {                     // Runtime exception catcher
        if (fileThread == null) {
          fileThread = new FileQ330(dbHost, configFile);
        } else if (!fileThread.isAlive()) {
          fileThread = new FileQ330(dbHost, configFile);
          prta("Q330M: starting a new FileQ330. It was not alive! db host=" + dbHost + " config=" + configFile);
          SendEvent.debugEvent("Q330MFileErr", "restart FileQ330 on " + Util.getNode() + " " + IndexFile.getNode(), this);
        } // There is an unexplained reason the FileQ330 thread quits.  Check it and restart it  04/2009
        else if (System.currentTimeMillis() - lastFileCheck > 600000 && !noDB) {
          lastFileCheck = System.currentTimeMillis();
          if (fileThread.getLoop() == lastLoop) {
            prta("Q330M:  FileQ330 seems to have stopped.  Restart it. lastLoop=" + lastLoop + " getLoop=" + fileThread.getLoop() + " noDB=" + noDB);
            SendEvent.debugEvent("Q330FileHung", "FileQ330 appears to be hung.  restart", this);
            fileThread.terminate();
            fileThread = new FileQ330(dbHost, configFile);
          }
          lastLoop = fileThread.getLoop();
        }
        try {
          // Clear all of the checks in the list
          itr = thr.values().iterator();
          while (itr.hasNext()) {
            itr.next().setCheck(false);
          }
          if (terminate) {
            break;
          }

          // Open file and read each line, process it through all the possibilities
          in = new BufferedReader(new FileReader(configFile));
          n = 0;
          prta("Read in configfile for q330s =" + configFile);
          while ((s = in.readLine()) != null) {
            if (s.length() < 1) {
              continue;
            }
            if (s.substring(0, 1).equals("%")) {
              break;       // Debug: done reading flag
            }
            s = s.trim();
            if (!s.substring(0, 1).equals("#") && !s.substring(0, 1).equals("!")) {
              int comment = s.indexOf("#");
              if (comment >= 0) {
                s = s.substring(0, comment).trim();  // Remove a later comment
              }
              String[] tk = new String[2];
              tk[1] = s;
              String[] args = s.split("\\s");
              for (int i = 0; i < args.length; i++) {
                if (args[i].equals("-s")) {
                  tk[0] = args[i + 1];
                }
              }
              //String [] tk = s.split(":");
              if (tk.length == 2) {
                n++;
                Q330 q330 = thr.get(tk[0].trim());
                if (q330 == null) {
                  prta("Q330M: New Q330 found start " + tk[0] + " " + tk[1]);
                  q330 = new Q330(tk[0].trim(), tk[1].trim());
                  thr.put(tk[0].trim(), q330);
                  q330.setCheck(true);
                  //Util.sleep(3000);
                } else if (!tk[1].trim().equals(q330.getArgs()) || !q330.isAlive()) {// If its not the same, stop and start it
                  q330.setCheck(true);
                  prta("Q330M: line changed alive=" + q330.isAlive() + "|" + tk[0] + "|" + tk[1]);
                  prta(tk[1].trim() + "|\n" + q330.getArgs() + "|\n");
                  try {
                    //killQ330(tk[0], 0);
                    Util.sleep(1000);
                    q330.restartQ330(tk[1]);
                  } catch (IOException e) {
                    prta("Q330M: Got an IOException trying to start " + tk[0] + ":" + tk[1]);
                  }
                } else {
                  q330.setCheck(true);
                }
              }
            }
          }   // End of read config file lines
          in.close();
          itr = thr.values().iterator();
          while (itr.hasNext()) {
            Q330 q330 = itr.next();
            if (!q330.getCheck()) {
              // This line is no longer in the configuration, shutdown the Q330
              prta("Q330M: line must have disappeared " + q330.getQ330() + " " + q330.getArgs());
              q330.terminate();
              itr.remove();
            }
          }
          for (int i = 0; i < 60; i++) {
            try {
              sleep(1000);
            } catch (InterruptedException expected) {
            }
            if (terminate) {
              break;
            }
          }
          if (System.currentTimeMillis() % 86400000L < 90000 && !noDB) {
            dbconn.setLogPrintStream(getPrintStream());
          }
        } catch (FileNotFoundException e) {
          prta("Q330M: Could not find a config file!=" + configFile + " " + e);
          prta("Q330M: Could not find messge=" + e.getMessage());
          try {
            sleep(30000);
          } catch (InterruptedException expected) {
          }
        } catch (IOException e) {
          prta("Q330M: error reading the configfile=" + configFile + " e=" + e.getMessage());
          e.printStackTrace();
        }
        // while(true) Get data

      } catch (RuntimeException e) {
        prta(tag + " RuntimeException in " + this.getClass().getName() + " e=" + e.getMessage());
        if (getPrintStream() != null) {
          e.printStackTrace(getPrintStream());
        } else {
          e.printStackTrace();
        }
        if (e.getMessage() != null) {
          if (e.getMessage().contains("OutOfMemory")) {
            SendEvent.edgeEvent("OutOfMemory", "Out of Memory in " + Util.getProcess() + "/" + this.getClass().getName() + " on " + IndexFile.getNode(), this);
            throw e;
          }
        }
      }
    }       // while(true) do socket open
    fileThread.terminate();

    prta(tag + "Q330M: is terminated.  wait 10....");
    try {
      sleep(10000);
    } catch (InterruptedException expected) {
    }
    prta(tag + "Q330M: is exiting...");
    running = false;
    terminate = false;
  }

  /**
   * Return the monitor string for Icinga
   *
   * @return A String representing the monitor key value pairs for this
   * EdgeThread.
   */
  @Override
  public StringBuilder getMonitorString() {
    if (monitorsb.length() > 0) {
      monitorsb.delete(0, monitorsb.length());
    }
    monitorsb.append("Q330BytesIn=").append(getBytesIn()).append("\n");
    /*Iterator<Q330> itr = thr.values().iterator();
    while(itr.hasNext()){
      monitorsb.append(((Q330) itr.next()).getMonitorString());
    }*/

    return monitorsb;
  }

  @Override
  public StringBuilder getStatusString() {
    if (statussb.length() > 0) {
      statussb.delete(0, statussb.length());
    }
    statussb.append("  ").append(tag).append("in=").append(inbytes).append(" out=").
            append(outbytes).append(" config=").append(configFile).append(" edge=").
            append(edgeip).append("/").append(edgeport).append(" 1sec=").append(onesecip).
            append("/").append(onesecport).append(" db=").append(dbHost).append(" role=").append(roles).append("\n");
    Iterator<Q330> itr = thr.values().iterator();
    while (itr.hasNext()) {
      statussb.append("    ").append(((Q330) itr.next()).getStatusString());
    }

    return statussb;
  }

  @Override
  public StringBuilder getConsoleOutput() {
    return consolesb;
  } //  We use prt directly

  final class FileQ330 extends Thread {

    String filename;
    String host;
    DBConnectionThread dbconn;
    StringBuilder sb;
    boolean terminate;

    @Override
    public String toString() {
      return filename + " " + host;
    }

    public void terminate() {
      prta("FileQ330: terminate called...");
      terminate = true;
      interrupt();
    }
    int loop;

    public int getLoop() {
      return loop;
    }

    public FileQ330(String db, String f) {
      filename = f;
      host = db;
      if (host.contains(":")) {
        host = host.substring(0, host.indexOf(":"));
      }
      dbconn = DBConnectionThread.getThread("Q330RO");
      if (dbconn == null && !noDB) {
        try {
          dbconn = new DBConnectionThread(host, "readonly", "anss", false, false, "Q330RO", getPrintStream());
          if (!DBConnectionThread.waitForConnection("Q330RO")) {
            if (!DBConnectionThread.waitForConnection("Q330RO")) {
              if (!DBConnectionThread.waitForConnection("Q330RO")) {
                if (!DBConnectionThread.waitForConnection("Q330RO")) {
                  prta("********* Q330RO did not connect!");
                }
              }
            }
          }
          addLog(dbconn);
        } catch (InstantiationException e) {
          prta("InstantiationException: should be impossible");
          dbconn = DBConnectionThread.getThread("Q330RO");
        }
        dbconn.setLogPrintStream(getPrintStream());

      }
      gov.usgs.anss.util.Util.prta("new ThreadManager " + getName() + " " + getClass().getSimpleName() + " f=" + f);
      start();
    }

    @Override
    public void run() {
      InetAddress rtsaddr = null;
      InetAddress q330addr = null;
      if (sb == null) {
        sb = new StringBuilder(1000);
      }
      Util.clear(sb);
      StringBuilder rrpconfig = new StringBuilder(1000);
      StringBuilder lastrrpconfig = new StringBuilder(1000);
      byte[] scratch = new byte[2000];
      String ipadr = "";
      String ports = "";
      String station = "";
      StringBuilder lastString = new StringBuilder(1000);
      loop = 0;
      while (!terminate) {
        loop++;
        while (dbconn == null || noDB) {
          if (!noDB) {
            prta("FileQ330: DB connection is down wait... noDB=" + noDB);
          }
          try {
            sleep(30000);
          } catch (InterruptedException expected) {
          }
        }
        try {
          boolean doit;
          if (loop % 20 != 1) {
            try (ResultSet rs = dbconn.executeQuery("SELECT tcpstation,updated FROM tcpstation WHERE updated>TIMESTAMPADD(SECOND,-120,now())")) {
              if (rs.next()) {
                doit = true;
                prta("FileQ330: DB has new configs");
              } else {
                doit = false;
              }
            }
          } else {
            doit = true;
          }
          if (loop % 20 == 0 || doit) {
            prta("FileQ330: loop starting term=" + terminate);
          }
          if (doit) {
            // It may have been a long time since this thread (internal to TCPStation) has been used, reopen it
            if (DBConnectionThread.getThread("anss") != null) {
              DBConnectionThread.getThread("anss").reopen();
            }
            if (sb.length() > 0) {
              sb.delete(0, sb.length());
            }
            if (dbg) {
              prta("FileQ330 for roles=" + roles);
            }
            try (ResultSet rs = dbconn.executeQuery("SELECT * FROM tcpstation LEFT JOIN cpu ON cpu.id=cpuid "
                    + "WHERE q330!='' AND NOT q330 REGEXP '#' AND cpu REGEXP '" + roles
                    + "' ORDER BY tcpstation")) {
              if (UC.getConnection() == null) {
                UC.setConnection(dbconn.getConnection());
              }
              while (rs.next()) {
                boolean tunnel = (rs.getString("q330tunnel").equals("True"));
                TCPStation stat = new TCPStation(rs);
                if (dbg) {
                  prta("FileQ330 : do " + stat + " " + stat.getQ330Stations()[0]);
                }
                if (stat.getQ330Stations()[0].contains("LAB")
                        && !Util.getNode().equals("edge3") && (!Util.getNode().equals("edge1") && !Util.getNode().equals("edge7")
                        && !Util.getNode().equals("vdldfc9") && !Util.getNode().equals("edge4") && !Util.getNode().equals("glab3"))) {
                  continue;  // Skip LAB
                }
                if (stat.getQ330Stations()[0].contains("%")
                        && !Util.getNode().equals("edge3") && !Util.getNode().equals("edge1") && !Util.getNode().equals("edge7")
                        && !Util.getNode().equals("gldketchum3") && !Util.getNode().equals("glab3")) {
                  continue;  // Skip % stations
                }                // make up a line for a possible RTS2/Slate2
                rrpconfig.append("-t ").append(stat.getTCPStation()).append(" -ip ").append(Util.cleanIP(stat.getIP())).
                        append(" -f ").append(stat.getTCPStation()).append(".ring -wait 5000 -s 10 -a 30000 -ap 100 -dbg\n");

                // For each Q330, create a line for Q330s.setup
                for (int i = 0; i < stat.getNQ330s(); i++) {
                  if (stat.getQ330Stations()[i].contains("SLB")) {
                    prt(stat.getQ330Stations()[i] + " -x " + stat.getHexSerials()[i] + " -auth " + stat.getAuthCode(i, 1));
                  }
                  if (tunnel) {
                    String cpuip = stat.getCpuTunnelIP();
                    cpuip = cpuip.replaceAll("\\.0", ".");
                    while (cpuip.charAt(0) == '0') {
                      cpuip = cpuip.substring(1);
                    }
                    sb.append(!stat.getQ330Stations()[i].contains("LAB") && Util.getNode().equals("edge9") ? "!" : "").
                            append("anssq330 -dbgms -s ").
                            append(stat.getQ330Stations()[i]).append(" -verb ").append(verbosity).
                            append(dataport > 0 ? " -dataport " + dataport : ""). // DEBUG : force wrong data port

                            append(" -x ").append(stat.getHexSerials()[i]).append(" -auth ").append(stat.getAuthCode(i, 1)).
                            append(stat.getQ330Stations()[i].equals(stat.getTCPStation()) ? " " : " -fs " + stat.getTCPStation()).append(" -qport ").
                            append(stat.getTunnelPorts()[i]).append(" -hport ").append(stat.getHostPorts()[i] + dataport * 1000).
                            append(" -ip ").append(cpuip).append(" -edgeip ").append(edgeip).append(" -edgeport ").
                            append(stat.getTunnelPorts()[i] + edgeport + dataport * 1000).append(" -secip ").append(onesecip).
                            append(" -secport ").append(onesecport).append(stat.getSeedNetwork().equals("XX") ? " -fn XX" : "").append("\n");
                    if (dbg) {
                      prta("CPULink ip for stat=" + stat + " is " + cpuip);
                    }
                  } else {
                    sb.append(!stat.getQ330Stations()[i].contains("LAB") && Util.getNode().equals("edge9") ? "!" : "").
                            append("anssq330 -dbgms -s ").append(stat.getQ330Stations()[i]).append(" -verb ").append(verbosity).
                            append(dataport > 0 ? " -dataport " + dataport : ""). // DEBUG : force wrong data port
                            append(stat.getQ330DataPorts()[i] != 1 ? " -dataport " + stat.getQ330DataPorts()[i] : "").
                            append(" -x ").append(stat.getHexSerials()[i]).append(" -auth ").append(stat.getAuthCode(i, 1)).
                            append(stat.getQ330Stations()[i].equals(stat.getTCPStation()) ? "" : " -fs " + stat.getTCPStation()).
                            append(" -qport ").append(stat.getQ330Ports()[i]).append(" -hport ").
                            append(stat.getHostPorts()[i] + dataport * 1000).
                            append(" -ip ").append(stat.getQ330InetAddresses()[i].toString().substring(1)).append(" -edgeip ").
                            append(edgeip).append(" -edgeport ").append(stat.getTunnelPorts()[i] + edgeport + dataport * 1000).append(" -secip ").
                            append(onesecip).append(" -secport ").append(onesecport).append(stat.getSeedNetwork().equals("XX") ? " -fn XX" : "")
                            .append("\n");
                  }
                }
              }
            }
            if (dbg) {
              prta("FileQ330 sb.size()=" + sb.length() + " is same =" + Util.stringBuilderEqual(sb,lastString));
            }

            // If our file has changed, write it out
            File f = new File(configFile);
            if (!Util.stringBuilderEqual(sb, lastString) || f.length() < 5) {
              if (sb.length() >= scratch.length) {
                scratch = new byte[sb.length() * 2];  // Insure enough space
              }
              try {
                try (RandomAccessFile rf = new RandomAccessFile(configFile, "rw")) {
                  Util.stringBuilderToBuf(sb, scratch);
                  rf.seek(0l);
                  rf.write(scratch, 0, sb.length());
                  rf.setLength(sb.length());
                }
                //FileWriter out = new FileWriter(configFile);
                //out.write(sb.toString());
                //out.close();
                Util.clear(lastString).append(sb);
                prta("FileQ330: Writing out config file " + configFile + " f.length=" + f.length());
              } catch (IOException e) {
                prt("FileQ330: error writing config file=" + configFile + " e=" + e.getMessage());
              }
            }        // Done, rerun this periodically

            // Handle the Q330 on RRP/MXE 
            Util.clear(rrpconfig);
            try (ResultSet rs = dbconn.executeQuery("SELECT * FROM tcpstation LEFT JOIN cpu ON cpu.id=cpuid "
                    + "WHERE q330!='' AND NOT q330 REGEXP '#' ORDER BY tcpstation")) {
              while (rs.next()) {
                TCPStation stat = new TCPStation(rs);
                if (dbg) {
                  prta("FileQ330 : do " + stat + " " + stat.getQ330Stations()[0]);
                }
                // make up a line for a possible RTS2/Slate2
                rrpconfig.append("-t ").append(stat.getTCPStation()).append(" -ip ").append(Util.cleanIP(stat.getIP())).
                        append(" -f ").append(stat.getTCPStation()).append(".ring -wait 5000 -s 10 -a 30000 -ap 100 -dbg\n");
              }
            }
            prta("FILEQ330: DEBUG size of rrpconfig =" + rrpconfig.length() + " last=" + lastrrpconfig.length() + " loop=" + loop
                    + " " + Util.stringBuilderEqual(rrpconfig, lastrrpconfig));
            if (!Util.stringBuilderEqual(rrpconfig, lastrrpconfig) && rrpconfig.length() > 0) {
              Util.clear(lastrrpconfig).append(rrpconfig);    // Make it the same
              if (rrpconfig.length() >= scratch.length) {
                scratch = new byte[rrpconfig.length() * 2];// Insure enough space
              }
              try {
                prta("FILEQ330: write out config/rrpq330.config size=" + rrpconfig.length());
                try (RandomAccessFile rf = new RandomAccessFile("config/rrpq330.config", "rw")) {
                  Util.stringBuilderToBuf(rrpconfig, scratch);
                  rf.seek(0l);
                  rf.write(scratch, 0, rrpconfig.length());
                  rf.setLength(rrpconfig.length());
                }
              } catch (IOException e) {
                prta("FileQ330: error writing rrpconfig file=config/rrpq330.config e=" + e);
              }
            }
          }
          for (int i = 0; i < 60; i++) {
            try {
              sleep(1000);
            } catch (InterruptedException expected) {
            }
            if (terminate) {
              break;
            }
          }
          if (System.currentTimeMillis() % 86400000L < 90000) {
            dbconn.setLogPrintStream(getPrintStream());
          }
        } catch (SQLException e) {
          prta("FileQ330: SQLError=" + e);
          if (e != null) {
            if (e.toString().contains("is not connected")) {
              DBConnectionThread.getThread("Q330RO").terminate();
              dbconn = null;
            } else {
              Util.SQLErrorPrint(e, "FileQ330: Error setting up query for Q330 config DB=" + dbconn.toString(), getPrintStream());
            }
          }
          try {
            sleep(60000);
          } catch (InterruptedException expected) {
          }
          try {
            dbconn.close();
            dbconn = new DBConnectionThread(host, "readonly", "anss", false, false, "Q330RO", getPrintStream());
            addLog(dbconn);
            DBConnectionThread.waitForConnection("Q330RO");
          } catch (InstantiationException e2) {
            prta("InstantiationException: should be impossible");
            dbconn = DBConnectionThread.getThread("Q330RO");
          }
        } catch (RuntimeException e) {
          prta("FileQ330: Got runtime error e=" + e);
          SendEvent.debugEvent("FileQ330RTEr", "Runtime error in FileQ330 " + Util.getNode() + " " + IndexFile.getNode(), this);
          e.printStackTrace(getPrintStream());
          try {
            sleep(60000);
          } catch (InterruptedException expected) {
          }
        }
      }
      prta("FileQ330: writer exiting.... terminate=" + terminate);
    }
  }

  final class Q330 extends Thread {

    boolean inRestart;
    String tag;
    String argline;
    String[] args;
    MiniSeedServer mss;
    Subprocess anssq330;
    String anssq330String;
    String station;
    int hport;
    //Subprocess anssq330;
    boolean terminate;
    boolean check;

    long lastBytesIn;
    String socketLine;

    public void setCheck(boolean t) {
      check = t;
    }

    public boolean getCheck() {
      return check;
    }

    public void terminate() {
      prta(tag + " terminate called");
      terminate = true;
      interrupt();
    }

    //public Subprocess getAnssq330() {return anssq330;}
    public MiniSeedServer getMiniSeedServer() {
      return mss;
    }

    public String getQ330() {
      return tag;
    }

    public String getArgs() {
      return argline;
    }

    @Override
    public String toString() {
      return tag + " " + argline;
    }

    public long getBytesIn() {
      return (mss == null ? 0 : mss.getBytesIn());
    }

    public String getMonitorString() {
      return tag + (mss != null ? mss.getMonitorString() : "") + "\n";
    }

    public String getStatusString() {
      return tag + " " + (mss != null ? mss.getStatusString() : "");
      //+"      "+(anssq330!= null?anssq330.getStatusString():"")+"\n";
    }

    public void restartQ330(String ags) throws IOException {
      if (terminate) {
        return;
      }
      if (inRestart) {
        prta(tag + "dup call to restart!!!");
        return;
      }
      inRestart = true;
      argline = ags;
      int edgeport = 0;
      args = argline.split("\\s");
      for (int i = 0; i < args.length; i++) {
        if (args[i].equals("-edgeport")) {
          edgeport = Integer.parseInt(args[i + 1]);
        }
      }
      if (anssq330 != null) {
        anssq330.terminate();   // If there is an old one, make it leave
        int loops = 0;
        while (anssq330.isRunning()) {
          try {
            sleep(100);
          } catch (InterruptedException expected) {
          }
          loops++;
          if (loops > 100) {
            break;
          }
        }
        if (anssq330.isRunning()) {
          prta(tag + " anssq330 is stil running after a terminate(). Try killQ330()" + anssq330);
          killQ330(station, 0);
          try {
            sleep(4000);
          } catch (InterruptedException expected) {
          }
        }
        if (anssq330.isRunning()) {
          prta(tag + "Failed to shutdown MiniSeed=" + mss.isRunning() + " or anssq330=" + anssq330.isRunning() + " arg=" + argline);
          if (mss.isRunning()) {
            prta("Failed mss=" + mss.toString() + " " + mss.getStatusString());
          }
          SendEvent.edgeEvent("Q330Fail", tag + " Q330 will not stop", this);
        }
      }
      MiniSeedServer m = MiniSeedServer.getMiniSeedServer(edgeport);
      boolean mssNeeded = false;
      if (m == null) {
        mssNeeded = true;
      } else if (!m.isRunning() || !m.isAlive()) {
        mssNeeded = true;
        prta(tag + "MSS: *** is present but not running.  Reconfigure it " + m.toString());
      } else {
        mss = m;
      }
      prta(tag + "m=" + m + " mssneeded=" + mssNeeded + " edgeport=" + edgeport + " mss=" + mss);
      if (mssNeeded) {
        String line = miniSeedServerLine + " -q330 -p " + (edgeport) + " >> Q330MS";
        line = line.trim();
        line = line.replaceAll("  ", " ");
        line = line.replaceAll("  ", " ");
        line = line.replaceAll("  ", " ");
        prta(tag + "Start MSS |" + line);
        mss = new MiniSeedServer(line, tag);
      } else {
        prta(tag + "MiniSeedServer on port " + edgeport + " is running " + m);
      }

      // We wait 45 seconds because if there is an existing anssq330 running, it might take 30 seconds to reconnect
      long oldBytesIn = mss.getBytesIn();
      try {
        sleep(45000);
      } catch (InterruptedException expected) {
      }
      if (mss.getBytesIn() - oldBytesIn > 500) {
        prta(tag + " *** are there two anssq330s running?  I got data during the lull=" + mss.getBytesIn());
        SendEvent.edgeSMEEvent("Q330TwoRun", tag + " two anssq330s may be running", this);
      }
      //anssq330String = "[bash --login -c \"killQ330 "+tag+"\"]bash --login -c \"cd log;"+argline+" -dbgms \" >>Q330"+tag;
      anssq330String = "bash --login -c '/home/vdl/bin/" + argline + "' >> Q330SP"; //q330/"+tag.toLowerCase()+"'"; // Add dbgms for more output
      prta(tag + " Subprocess:R " + anssq330String);
      anssq330 = new gov.usgs.anss.edgemom.Subprocess(anssq330String, tag);
      inRestart = false;
    }

    public Q330(String tg, String ags) {
      station = tg;
      tag = tg + "[" + this.getName().substring(7) + "]";
      argline = ags;
      gov.usgs.anss.util.Util.prta(tag + " new ThreadStation " + getName() + " " + getClass().getSimpleName() + " tag=" + tag + " args=" + ags);

      start();

    }

    @Override
    public void run() {
      int sleepFor = 600000;
      long lasttimeout = 0;
      int sum = 0;
      for (int i = 0; i < tag.length(); i++) {
        sum += tag.charAt(i);
      }
      // This is an entry, so start up the anssq330 and miniseed server
      try {
        restartQ330(argline);
      } catch (IOException e) {
        prt(tag + " IOException trying to start Q330 " + argline + ":" + argline + " e=" + e.getMessage());
      } catch (RuntimeException e) {
        prt(tag + " Runtime exception trying inital start Q330 " + argline + e);
        e.printStackTrace(getPrintStream());
      }
      try {
        sleep((sum % 10) * 60000);
      } catch (InterruptedException expected) {
      } // Randomize monitor startup
      while (!terminate) {
        try {
          long timeout = 0;
          // We check the Q330 for running every 10 seconds for 10 minutes
          // Each 10 minutes we check to see if its time to do a "no data" check (sleepFor). If it is, do it
          while (timeout < 600000) {
            if (terminate) {
              break;
            }
            try {
              sleep(10000);
            } catch (InterruptedException expected) {
            }
            if (anssq330 == null || !anssq330.isRunning() || !anssq330.isRunning2()) {
              // The ANSS Q330 is down, we need to do a restart
              try {
                if (terminate) {
                  prta(tag + " ANSSQ330 has exitted and terminate is true.  ");
                  break;
                } else {
                  if (inRestart) {
                    prta(tag + " ANSSQ330 has exited due to someone calling restart.  Ignore from run()");
                    continue;
                  }
                  if (anssq330 == null) {
                    prta(tag + " ANSSQ330 is now null!");
                  } else {
                    prta(tag + " ANSSQ330 has exitted.  Restart it... running=" + anssq330.isRunning() + " " + anssq330.isRunning2()
                            + " Output=" + anssq330.getOutput().toString().replaceAll("\n", "|") + "\n      "
                            + tag + " Error=" + anssq330.getErrorOutput().toString().replaceAll("\n", "|"));
                    if (anssq330.getPID() > 0) {
                      prta(tag + " try to do a : " + "kill " + anssq330.getPID() + "");
                      gov.usgs.anss.util.Subprocess killer = new gov.usgs.anss.util.Subprocess("kill " + anssq330.getPID());
                      while (killer.exitValue() == -99) {
                        prta(tag + " waiting for to die ");
                        try {
                          sleep(500);
                        } catch (InterruptedException expected) {
                        }
                        if (terminate) {
                          break;
                        }
                      }
                      prta(tag + " exit killPID=" + killer.exitValue() + " stdout=" + killer.getOutput().replaceAll("\n", "|")
                              + "\n       err=" + killer.getErrorOutput().replaceAll("\n", "|"));
                      SendEvent.edgeSMEEvent("Q330Exit", tag + " exited.  restart", this);
                    } else {
                      killQ330(station, 0);
                      prta(tag + " pid not known???****** try to kill with killQ330");
                    }
                  }
                }
                if (terminate) {
                  break;
                }
                restartQ330(argline);
              } catch (IOException e) {
                prta(tag + "IOException try to restart the ANSSQ330 code " + e);
              }
            }
            timeout += 10000;   // 10 seconds has gone by
            lasttimeout += 10000;
          } // Wait for 10 minutes of loops
          prta(tag + " bytes last =" + (mss.getBytesIn() - lastBytesIn) + " sleepfor=" + sleepFor + " last=" + lasttimeout);
          if (terminate) {
            break;          // If terminating, do not attempt a restart. Lets bail
          }          // If it is not clear, restarting this code ever helps
          if (mss.getBytesIn() - lastBytesIn < 10240) {   // Is there too little traffic for 10 minutes, 20 blocks
            if (lasttimeout >= sleepFor) {      // Is it time for traffic check
              prta(tag + " (((( no data threshold exceeded. Restart ANSSQ330 (DISABLED) #b=" + (mss.getBytesIn() - lastBytesIn) + " <10240");
              sleepFor = 2 * sleepFor;
              if (sleepFor > 28800000) {
                sleepFor = 28800000; // It was 28800000
              }
              lasttimeout = 0;
            }
          } else {
            lasttimeout = 0;
            sleepFor = 600000;
          }       // We had enough traffic for this 10 minutes, rearm the check to 10 minutes

          lastBytesIn = mss.getBytesIn();
        } catch (RuntimeException e) {
          prta(tag + "Q330: runtime error in main loop e=" + e);
          e.printStackTrace(getPrintStream());
        }
      } // while(!terminate)
      // The main loop has exitted, probably by terminate.  Clean up by doing a kill on any anssq330 job still running
      try {
        prta("Q330 kill for " + tag + " after run() terminate");
        killQ330(station, (anssq330 != null ? anssq330.getPID() : 0));
      } catch (IOException e) {
        prta("IOException issuing killQ330 on " + tag + " e=" + e.getMessage());
      }

      prta("Q330 for " + tag + " is exiting.");
    }
  }

  /**
   * Kill an ANSSQ330 that is running by sending a bash "killQ330 STAT" command.
   *
   * @param tg The station to kill.
   * @param pid The pid for the anssq330.
   * @throws IOException If one occurs.
   */
  public void killQ330(String tg, int pid) throws IOException {
    prta(tag + "KillQ330 " + tg + " pid=" + pid);
    Subprocess killer = new gov.usgs.anss.edgemom.Subprocess("bash --login -c 'killQ330 " + tg + (pid > 0 ? " " + pid : "")
            + " '>> q330/" + tg.toLowerCase() + "\n", "kill" + getTag());
    // Wait for killQ330 command to complete, then dump any output for debugging
    int loop = 0;
    while (killer.exitValue() == -99) {
      try {
        sleep(50);
      } catch (InterruptedException expected) {
      }
      loop++;
      if (terminate && loop >= 200) {
        prta(tag + "KillQ330 failed to stop job but terminate is on!");
        break;
      }
    }
    prta(tg + " loop=" + loop + " exit=" + killer.exitValue() + " stdout=" + killer.getOutput().toString().replaceAll("\n", "|")
            + "\n       err=" + killer.getErrorOutput().toString().replaceAll("\n", "|"));
  }

  public class ShutdownQ330Manager extends Thread {

    Q330Manager manager;

    public ShutdownQ330Manager(Q330Manager man) {
      manager = man;
      gov.usgs.anss.util.Util.prta("new ThreadShutdown " + getName() + " " + getClass().getSimpleName());
    }

    @Override
    public void run() {
      prta("ShutdownQ330: kill each Q330");
      // Send a terminate() to every Q330 object
      Iterator itr = thr.values().iterator();
      terminate = true;
      while (itr.hasNext()) {
        ((Q330) itr.next()).terminate();
      }
      boolean done = false;
      // Wait for done.  All Q330s are reported as down
      while (!done) {
        try {
          sleep(500);
        } catch (InterruptedException expected) {
        }
        itr = thr.values().iterator();
        done = true;

        // If any sub-thread is alive on is running, do not finish the shutdown yet
        while (itr.hasNext()) {
          Q330 q330 = (Q330) itr.next();
          if (q330.isAlive()) {
            done = false;
          }
        }
      }

      // All Q330s are down, issue a pkill anssq330 just to be sure.  The MiniSeedServer is shutdown with a time delay
      try {
        prta("ShutdownQ330: all are down.  Do a pkill to be sure!");
        gov.usgs.anss.util.Subprocess killer = new gov.usgs.anss.util.Subprocess("pkill anssq330");
        Util.sleep(2000);
        prta("ShutdownQ330 : pkill command results exit=" + killer.exitValue() + " std="
                + killer.getOutput().replaceAll("\n", "|") + " err=" + killer.getErrorOutput().replaceAll("\n", "|"));
      } catch (IOException e) {
        prta("Q330M: got IOError doing 'pkill anssq330' e=" + e);
      }
      manager.terminate();
      prta("ShutdownQ33 is complete.");
    }
  }

  /**
   * Test routine.
   *
   * @param args The command line arguments.
   */
  public static void main(String[] args) {
    IndexFile.init();
    EdgeProperties.init();
    DBConnectionThread dbconn = DBConnectionThread.getThread("edge");
    if (dbconn == null) {
      try {
        dbconn = new DBConnectionThread(Util.getProperty("DBServer"), "readonly", "anss", false, false, "edge", null);
        if (!DBConnectionThread.waitForConnection("edge")) {
          if (!DBConnectionThread.waitForConnection("edge")) {
            if (!DBConnectionThread.waitForConnection("edge")) {
              if (!DBConnectionThread.waitForConnection("edge")) {
                Util.prta("********* Q330RO did not connect!");
              }
            }
          }
        }
      } catch (InstantiationException e) {
        Util.prta("InstantiationException: should be impossible");
        dbconn = DBConnectionThread.getThread("Q330RO");
      }
      Util.prt("dbconn="+dbconn);
    }

    Q330Manager qman = new Q330Manager("-roles gacq1 -edgeip gacq1 -onesecip gacq1 -onesecport 7968 -nohydra -dbg >>q330", "Tag");

  }

}
