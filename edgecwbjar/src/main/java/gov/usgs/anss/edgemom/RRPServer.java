/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.edgemom;

import gov.usgs.anss.edgethread.EdgeThread;
import gov.usgs.anss.edgeoutput.RingFile;
import gov.usgs.anss.util.Util;
import gov.usgs.alarm.SendEvent;
import java.io.RandomAccessFile;
import java.io.IOException;
import java.util.ArrayList;
import java.net.*;
import java.nio.ByteBuffer;

/**
 * This class runs the "remote" end of an RRP client server. This software
 * offers connections to a port, accepts one-and-only-one connection from each
 * of a list of IP addresses and writes out the ring file. For most uses the
 * list will only contain one IP address since the most likely use is one
 * connection from Golden. The list of IP addresses for all clients must be set
 * with the -ip switch so that stray connections or connections from scans do
 * not interrupt the connection. This ring is a replication of the ring at the
 * RRPClient. The class insures all data are received correctly and written to
 * the file and that the file pointer is consistently updated when new data are
 * available.
 * <br>
 * Configuration of multiple connections can be done on the command line where
 * the -ip, -wait, -s, and -f are a colon separated list for each connection in
 * corresponding order. Example:
 * <br>
 * -ip 1.2.3.4:4.5.6.7 -wait 1000:100 -s 200:1000 -f RING1.ring:RING2.ring
 * <p>
 * or multiple connections can be setup by having these 4 switches on separate
 * lines in a configuration file specified with the -config switch. So if there
 * is a config file "rrps.setup" containing:
 * <br>
 * <PRE>
 * -ip 1.2.3.4 -wait 1000 -s 200 -f RING1.ring
 * -ip 4.5.6.7 -wait 100 -s 200 -f RING2.ring
 * </PRE>
 * <br>
 * would accomplish the same configuration as the above command line. All other
 * parameters apply to the entire RRPServer process regardless of which method
 * is used to configure the allowed connections.
 * <br>
 * Typical command : java -jar RRPServer.jar -ip 1.2.3.4 -f NEIC.ring -s 200 -u
 * 500 -log logfile.log
 * <br><br>
 * Command line arguments :
 * <br>
 * <PRE>
 * switch   arg                 Description
 * -p nnnn              Set port number (def=22223)
 * -config  file        The configuration file contains the configuration of all of the links (mandatory)
 * -log filename        Set log file name (def=rrpserver)
 * Switchs used by RRPManager :
 * -db     URL          A path to override DBServer for database access
 * -nodb                If present, do not run in DB mode.
 * Configuration file switches:
 * -gsn                 Same as "-w 1000 -u 120000 -up 210000000 -a 210000000 -ap 100" suitable for USGS stations
 * -ip      ip.adr      The IP address allowed to connect to this RRPServer (mandatory)
 * -t       tg          The tg name (normally station name) used to track this connection (mandatory)
 * -f       file.ring   The ring file name to use for this connection and status (mandatory)
 * -wait    Millis      The number of millis to wait for 256 packets (about 1 mBit) (def=1000)
 * -writering           Actually write out the data portion of the ring file rather than just the status block
 * -u       ms          Set time interval between updates of ring metadata file to ms milliseconds (def=120000)
 * -up      mod         Set packets interval between updates of ring meta data file to every 'mod' packets (def=never)
 * -a       nnnn        Set number of milliseconds between acks to client (def = 30000)
 * -ap      nnnn        Set number of packets between acks to client (def = 1000)
 * -dbg                 Turn on verbose output
 * </PRE>
 *
 * @author davidketchum Original from May 2, 2007
 */
public final class RRPServer extends EdgeThread {

  public static final int MODULO_SEQ = RingFile.MAX_SEQ;
  private int port;                 // Port to listen for connections on
  private ServerSocket d;           // The listen socket server
  private int totmsgs;

  private final ArrayList<RRPServerSocket> handlers = new ArrayList<>(10);
  private long configFileModified = 0;
  private boolean noDB;
  byte[] buf = new byte[100];
  private String configFile = "config/rrpserver.config";
  private String logfile;
  private final ReadConfig reader;
  private RRPManager rrpManager;
  private boolean dbg;
  private StringBuilder tmpsb = new StringBuilder(100);
  private StringBuilder runsb = new StringBuilder(100);

  @Override
  public void terminate() {
    terminate = true;
    interrupt();
  }   // Cause the termination to begin

  /**
   * return the monitor string for Icinga
   *
   * @return A String representing the monitor key value pairs for this
   * EdgeThread
   */
  @Override
  public StringBuilder getMonitorString() {
    return monitorsb;
  }

  @Override
  public StringBuilder getStatusString() {
    Util.clear(statussb);
    statussb.append("   ").append(tag).append(" port=").append(port).append(" #msg=").append(totmsgs).append("\n");
    for (RRPServerSocket r : handlers) {
      statussb.append("      ").append(r.toStringBuilder(null)).append("\n");
    }
    return statussb;
  }

  @Override
  public StringBuilder getConsoleOutput() {
    return consolesb;
  } //  We use prt directly

  @Override
  public String toString() {
    return toStringBuilder(null).toString();
  }

  public StringBuilder toStringBuilder(StringBuilder tmp) {
    StringBuilder sb = tmp;
    if (sb == null) {
      sb = Util.clear(tmpsb);
    }
    sb.append(tag).append(" port=").append(port).append(" #msg=").append(totmsgs);
    return sb;
  }

  public int getNumberOfMessages() {
    return totmsgs;
  }

  /**
   * Creates a new instance of RRPServers.
   *
   * @param argline The command line arguments
   * @param tag The tg for edgemom logging
   */
  public RRPServer(String argline, String tag) {
    super(argline, tag);
    terminate = false;
    port = 22223;
    String[] args = argline.split("\\s");
    String rrpManagerLine = "";
    for (int i = 0; i < args.length; i++) {

      if (args[i].equals("-p")) {
        port = Integer.parseInt(args[i + 1]);
        i++;
      } else if (args[i].equalsIgnoreCase("")) {
      } else if (args[i].equalsIgnoreCase("-dbg")) {
        dbg = true;
      } else if (args[i].equalsIgnoreCase("-config")) {
        configFile = args[i + 1];
        rrpManagerLine += "-config " + args[i + 1] + " ";
        i++;
      } else if (args[i].equalsIgnoreCase("-log")) {
        logfile = args[i + 1];
        this.setNewLogName(logfile);
      } else if (args[i].equalsIgnoreCase("-nodb")) {
        noDB = true;
        rrpManagerLine += "-nodb ";
      } else if (args[i].equals("-?") || args[i].indexOf("help") > 0) {
        prt("-p nnnn      Set port number to something other than 22223");
        prt("-config file Get the -f, -ip, -s and -waut out of this file for each connections");
        prt("-log file   Set log file name (def=rrpserver");
        prta("RRPManager switches :");
        prta("-db  DBURL  Override DBServer property with this url");
        prta("-nodb       If present, configuration files is not generated from the database");
        prt("Config file line switches");
        prt("-t tag       Set the tag used to track this connection (normally a station name");
        prt("-f file1     Set the filename for the ring file");
        prt("-ip ip.adr   Set the only acceptable client source IP address");
        prt("-writering   If present, the ring file is actually written rather than just the status block");
        prt("-s nnnn      Set the file size to nnnn MB (must divide evenly into 1,000,000");
        prt("-wait ms     Set the ms to wait for each 256 blocks (def = 1000 = 1 mb/s)");
        prt("-u ms        Set interval between updates of ring metadata file to ms milliseconds (def=1000)");
        prt("-up ms       Set interval between udpates of ring meta data file  to ever ms packets (def=never)");
        prt("-a  nnnn     Set number of millseconds between acks to client (def = 5000)");
        prt("-ap nnnn     Set number of packets between acks to client (def = 1000)");
        prt("-dbg        Turn on verbose output");
        prt("-?          Print this message");
        prt("Other useful mode for watching an ring file java -cp RRPServer.jar gov.usgs.anss.net.Ringfile (no argument for usage)");
        System.exit(0);
      } else if (args[i].charAt(0) == '>') {
        break;
      } else {
        prt(Util.clear(runsb).append("argument ").append(args[i]).append(" is unknown to this program.  Try again!"));
      }
    }

    // Setup the RRP Manager if a DB is present
    if (!noDB) {
      rrpManager = new RRPManager(rrpManagerLine + " >>rrpmgr", "RRPMgr");
    }
    try {
      sleep(3000);
    } catch (InterruptedException e) {
    }
    if (configFile != null) {
      readConfigFile();
    } else {
      prta("No configuration file!");
    }
    reader = new ReadConfig();      // Start the reader of configuration
    // Register our shutdown thread with the Runtime system.
    Runtime.getRuntime().addShutdownHook(new ShutdownRRPServer());

    start();
  }

  /**
   * Read lines from the configuration file and start RRPServerSocket threads
   * for each configured connection. If the argline changes, reconfigure the
   * thread.
   */
  private void readConfigFile() {
    long lastMod = Util.isModifyDateNewer(configFile, configFileModified);
    if (lastMod == 0) {
      return;      // File is not updated
    } else if (lastMod > 0) {
      configFileModified = lastMod;
    } else {
      prta(Util.clear(runsb).append(tag).append("Config file does not exist!"));
      return;
    }
    String content;
    String[] lines;
    try {
      try (RandomAccessFile rw = new RandomAccessFile(configFile, "r")) {
        if (buf.length < rw.length()) {
          buf = new byte[(int) rw.length() * 2];
        }
        rw.seek(0L);
        rw.read(buf, 0, (int) rw.length());
      }
      content = new String(buf, 0, (int) buf.length);
      lines = content.split("\\n");
    } catch (IOException e) {
      prta(Util.clear(runsb).append(tag).append(" ** Config file does not exist ").append(configFile));
      SendEvent.edgeSMEEvent("RRPSrvNoConf", "Config file does not exist file=" + configFile, "RRPServer");
      return;
    }
    for (String line : lines) {
      if (line.trim().length() == 0) {
        continue;
      }
      if (line.charAt(0) == '#' || line.charAt(0) == '!' || line.trim().length() < 5) {
        continue;
      }
      String[] args = line.split("\\s");
      String tg = "";
      for (int k = 0; k < args.length; k++) {
        if (args[k].equals("-t")) {
          tg = args[k + 1];
          k++;
        }
      }
      boolean found = false;
      for (RRPServerSocket handler : handlers) {
        if (handler.getTag().equals(tg)) {
          found = true;
          if (!handler.getArgline().equals(line)) {
            prta(Util.clear(runsb).append("Config line changed:\nwas:\n").append(handler.getArgline()).
                    append("now:\n").append(line));
            handler.setConfigLine(line);
            break;
          }
          if (found) {
            break;
          }
        }
      }
      if (!found) {
        // Its a new line, create the handler
        prta(Util.clear(runsb).append(tag).append("Create handler for ").append(line));
        handlers.add(new RRPServerSocket(line, tag, this));
      }
    }
  }

  /**
   * Listen for connections from the client, process them by creating new
   * handler objects and closing any old ones.
   */
  @Override
  public void run() {
    // Open up a port to listen for new connections.
    running = true;
    while (true) {
      try {
        //server = s;
        if (terminate) {
          break;
        }
        prta(Util.clear(runsb).append(tag).append(" Open Port=").append(port));
        d = new ServerSocket(port);
        break;
      } catch (SocketException e) {
        if (e.getMessage().equals("Address already in use")) {
          try {
            prta(Util.clear(runsb).append(tag).append("Address in use - try again."));
            Thread.sleep(2000);
          } catch (InterruptedException expected) {
          }
        } else {
          prta(Util.clear(runsb).append(tag).append("Error opening TCP listen port =").append(port).
                  append("-").append(e.getMessage()));
          try {
            Thread.sleep(2000);
          } catch (InterruptedException expected) {
          }
        }
      } catch (IOException e) {
        prta(Util.clear(runsb).append(tag).append("Error opening socket server=").append(e.getMessage()));
        try {
          Thread.sleep(2000);
        } catch (InterruptedException expected) {
        }
      }
    }

    // Do accepts and create a RRPServerSocket for each one
    RRPServerSocket handler;
    while (true) {
      if (terminate) {
        break;
      }
      try {
        Socket s = d.accept();
        prta(tag + " " + Util.ascdate() + " RRP: from " + s + " # handlers=" + handlers.size());

        // Look for this address in list of addresses allowed
        int n = -1;
        for (int i = 0; i < handlers.size(); i++) {
          if (handlers.get(i) != null) {
            if (s.getInetAddress().getHostAddress().equals(handlers.get(i).getClientIP())) {
              handler = handlers.get(i);
              prta(Util.clear(runsb).append(tag).append(" Found matching handler - set socket ").
                      append(handler.toStringBuilder(null)));
              handler.closeSocket();
              handler.assignSocket(s);
              n = i;
              break;
            }
          }
        }
        if (n == -1) {
          prta(Util.clear(runsb).append(tag).append(" connection from unexpected client rejected. got ").
                  append(s.getInetAddress().getHostAddress()));
          for (RRPServerSocket rrp : handlers) {
            prt(Util.clear(runsb).append(" Possibles : ").append(rrp.getClientIP()));
          }
          s.close();
        }
      } catch (IOException e) {
        prt(Util.clear(runsb).append(tag).append("receive through IO exception e=").append(e));
        e.printStackTrace();
      } catch (RuntimeException e) {
        prta(Util.clear(runsb).append("Runtime error e=").append(e));
        e.printStackTrace(getPrintStream());
      }
    }       // End of infinite loop (while(true))
    prt("Exiting RRPServers run()!! terminate=" + terminate + "\n");
    try {
      if (d != null) {
        if (!d.isClosed()) {
          d.close();
        }
      }
    } catch (IOException expected) {
    }
    int loop = 0;
    if (rrpManager != null) {
      rrpManager.terminate();
      while (rrpManager.isAlive()) {
        try {
          sleep(100);
        } catch (InterruptedException expected) {
        }
        if (loop++ > 100) {
          break;
        }
      }
      if (rrpManager.isAlive()) {
        prta(tag + " RRPManager ***** isAlive=" + rrpManager.isAlive());
      }
    }
    loop = 0;
    while (reader.isAlive()) {
      try {
        sleep(100);
      } catch (InterruptedException expected) {
      }
      if (loop++ > 100) {
        break;
      }
    }
    if (reader.isAlive()) {
      prta(tag + " Reader ****** isAlive()=" + reader.isAlive());
    }
    for (RRPServerSocket s : handlers) {
      prta(tag + " terminate RRPServerSocket=" + s);
      if (s != null) {
        s.terminate();
      }
    }
    loop = 0;
    boolean done = false;
    while (!done) {
      done = true;
      for (RRPServerSocket s : handlers) {
        if (s.isAlive()) {
          done = false;
        }
      }
      if (loop++ == 200) {
        prta(tag + " **** handlers all done=" + done);
        break;
      } // Allow 20 seconds for all to terminate.
      try {
        sleep(100);
      } catch (InterruptedException expected) {
      }
    }
    prta(tag + " fully terminated");
    running = false;
  }

  private final class ReadConfig extends Thread {

    public ReadConfig() {
      setDaemon(true);
      start();
    }

    @Override
    public void run() {
      while (!terminate) {
        for (int i = 0; i < 120; i++) {
          try {
            sleep(1000);
          } catch (InterruptedException expected) {
          }
          if (terminate) {
            break;
          }
        }
        readConfigFile();
      }
      prta("RRPS: readConfig exiting");
    }
  }

  private class ShutdownRRPServer extends Thread {

    public ShutdownRRPServer() {

    }

    /**
     * This is called by the Runtime.shutdown during the shutdown sequence and
     * causes all cleanup actions to occur
     */
    @Override
    public void run() {
      for (RRPServerSocket handler : handlers) {
        if (handler != null) {
          prta(tag + "RRPS: Shutdown handler=" + handler);
          handler.updateControl(); // Always write out latest info on the way down
          handler.closeSocket();
        }
        terminate = true;
        interrupt();
        prta(tag + "RRPS:Shutdown Done.");
        try {
          d.close();
        } catch (IOException e) {
          prt(Util.asctime() + "IOException trying to close ringfile or closing socket=" + e.getMessage());
        }
      }
    }
  }

  public static String safeLetter(byte b) {
    char c = (char) b;
    return (Character.isLetterOrDigit(c) || c == ' ') ? "" + c : Util.toHex((byte) c).toString();
  }

  public static StringBuilder toStringRaw(byte[] buf) {
    ByteBuffer bb = ByteBuffer.wrap(buf);
    StringBuilder tmp = new StringBuilder(100);
    bb.position(0);
    for (int i = 0; i < 6; i++) {
      tmp.append(safeLetter(bb.get()));
    }
    tmp.append(" ");
    bb.position(18);
    for (int i = 0; i < 2; i++) {
      tmp.append(safeLetter(bb.get()));
    }
    bb.position(8);
    for (int i = 0; i < 5; i++) {
      tmp.append(safeLetter(bb.get()));
    }
    bb.position(15);
    for (int i = 0; i < 3; i++) {
      tmp.append(safeLetter(bb.get()));
    }
    bb.position(13);
    for (int i = 0; i < 2; i++) {
      tmp.append(safeLetter(bb.get()));
    }
    bb.position(20);
    short i2 = bb.getShort();
    tmp.append(" ").append(i2).append(" ").append(Util.toHex(i2));
    i2 = bb.getShort();
    tmp.append(" ").append(i2).append(" ").append(Util.toHex(i2));
    tmp.append(" ").append(bb.get()).append(":").append(bb.get()).append(":").append(bb.get());
    bb.get();
    i2 = bb.getShort();
    tmp.append(".").append(i2).append(" ").append(Util.toHex(i2));
    i2 = bb.getShort();
    tmp.append(" ns=").append(i2);
    i2 = bb.getShort();
    tmp.append(" rt=").append(i2);
    i2 = bb.getShort();
    tmp.append("*").append(i2);
    bb.position(39);
    tmp.append(" nb=").append(bb.get());
    bb.position(44);
    i2 = bb.getShort();
    tmp.append(" d=").append(i2);
    i2 = bb.getShort();
    tmp.append(" b=").append(i2);
    return tmp;
  }

  /**
   * This internal class gets registered with the Runtime shutdown system and is
   * invoked when the system is shutting down. This must cause the thread to
   * exit.
   */
  class RRPServerShutdown extends Thread {

    /**
     * Default constructor does nothing. The shutdown hook starts the run()
     * thread.
     */
    public RRPServerShutdown() {
    }

    // This is called by the RunTime.shutdown at any termination 
    @Override
    public void run() {
      prta(tag + "RRPServer Shutdown() started...");
      try {
        sleep(2000);
      } catch (InterruptedException expected) {
      }    // Let a squirt of data pass
      terminate();          // Send terminate to main thread and cause interrupt
      prta(tag + "RRPServer shutdown() is complete.");
    }
  }

  /**
   * This main just sets up the Util package and starts up a RRPServer object.
   *
   * @param args The command line arguments.
   */
  public static void main(String[] args) {
    Util.init("edge.prop");
    Util.setModeGMT();
    String logfile = "rrpserver";
    String argline = "";
    for (int i = 0; i < args.length; i++) {
      argline += args[i] + " ";
      if (args[i].equals("-log")) {
        logfile = args[i + 1];
      }
    }
    if (args.length == 0) {
      argline = "-p 22224 -dbg -config RRP/rrpserver_edge.config >>rrpmxe";
    }
    EdgeThread.setMainLogname(logfile);

    EdgeThread.staticprt(Util.asctime());
    RRPServer t = new RRPServer(argline.trim(), "TEST");

  }

}
