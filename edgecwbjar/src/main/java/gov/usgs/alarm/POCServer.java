/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.alarm;

import gov.usgs.anss.db.DBConnectionThread;
import gov.usgs.anss.edge.EdgeProperties;
import gov.usgs.anss.edgethread.EdgeThread;
import gov.usgs.anss.guidb.TCPStation;
import gov.usgs.anss.util.SimpleSMTPThread;
import gov.usgs.anss.util.UC;
import gov.usgs.anss.util.Util;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.GregorianCalendar;

/**
 * This task handles POC messages from Q330s with dynamic IP addresses and DB
 * status update requests through a message queue.
 *
 * <PRE>
 * -p nnnn  Set the port to something other than 2254 (a generally bad idea)");
 * -nopoc   Do not set up a POC thread for Q330s on dynamic addresses");
 * -nomysql Do not set up a DB Message thread for processing DB updates");
 * -nodb    Do not set up a DB Message thread for processing DB updates");
 * -?            Print this message");
 * </PRE>
 *
 * @author davidketchum
 */
public final class POCServer extends EdgeThread {

  public static final int MAX_LENGTH = 52;
  private int port;
  private DatagramSocket d;

  private long lastStatus;
  private int msgLength = 52;
  private int state;
  private int totmsgs;
  private DBConnectionThread dbconn;
  private StringBuffer sb = new StringBuffer(1000);
  private final POCTimeout timeout;

  public int getState2() {
    return state;
  }

  public int getNumberOfMessages() {
    return totmsgs;
  }

  @Override
  public void terminate() {
    terminate = true;
  }

  @Override
  public StringBuilder getMonitorString() {
    Util.clear(monitorsb);
    return monitorsb;
  }

  @Override
  public StringBuilder getStatusString() {
    Util.clear(statussb);
    return statussb;
  }

  @Override
  public StringBuilder getConsoleOutput() {
    return consolesb;
  } //  we use prt directly

  /**
   * Creates a new instance of POCServers
   *
   * @param porti The port receiving POC requests (normally 2254)
   */
  public POCServer(int porti) {
    super("-empty >>pocserver", "POC");
    port = porti;
    if (port == 0) {
      port = 2254;
    }
    terminate = false;
    // Register our shutdown thread with the Runtime system.
    Runtime.getRuntime().addShutdownHook(new ShutdownPOCServers());
    // Open DB connection
    // this will keep a connection up to anss
    try {
      dbconn = new DBConnectionThread(Util.getProperty("DBServer"), "update", "anss",
              true, false, "anss", Util.getOutput());
      if (!DBConnectionThread.waitForConnection("anss")) {
        if (!DBConnectionThread.waitForConnection("anss")) {
          if (!DBConnectionThread.waitForConnection("anss")) {
            if (!DBConnectionThread.waitForConnection("anss")) {
              prta(" ***** DBConnect to anss failed " + Util.getProperty("DBServer"));
              System.exit(1);
            }
          }
        }
      }
    } catch (InstantiationException e) {
      prta("InstantiationException opening edge database in EdgeConfigServer e=" + e.getMessage());
      System.exit(1);
    }
    timeout = new POCTimeout();
    setDaemon(true);
    start();
  }

  @Override
  public void run() {
    boolean dbg = false;
    byte[] b = new byte[MAX_LENGTH];
    byte[] buf = new byte[msgLength + 100];
    StateTrack tracker = new StateTrack();
    Util.prta("Starting run ");

    GregorianCalendar now;
    long lastEmail = System.currentTimeMillis();
    prt(Util.asctime() + " POCServers: Open Port=" + port);
    try {
      int loop = 0;
      while (true) {
        try {
          //server = s;
          if (terminate) {
            break;
          }
          d = new DatagramSocket(port);
          lastStatus = System.currentTimeMillis();
          break;
        } catch (SocketException e) {
          if (e.getMessage().equals("Address already in use")) {
            try {
              loop++;
              if (loop % 200 == 0) {
                prt("POCServers: Address in use - try again. " + EdgeThread.getInstance());
              }
              Thread.sleep(60000);
            } catch (InterruptedException Expected) {
            }
          } else {
            prt("Error opening UDP port =" + port + " " + EdgeThread.getInstance() + "-" + e.getMessage());

            try {
              Thread.sleep(60000);
            } catch (InterruptedException Expected) {

            }
          }
        }
      }

      prta("run init code");
      state = 2;

      DatagramPacket p = new DatagramPacket(buf, msgLength);
      ByteBuffer bb = ByteBuffer.wrap(buf);

      // This loop receive UDP packets 
      boolean error;
      ResultSet rs;
      ResultSet rs2;
      short base;
      short portNumber;
      short webbps;
      short flags;
      short accessTO;
      short serialBaud;
      long balerHex = 0;
      byte[] q330ip = new byte[4];
      byte[] pocip = new byte[4];
      byte[] mask = new byte[4];
      long serialHex;
      boolean found = false;
      while (true) {
        if (terminate) {
          break;
        }
        try {
          error = false;
          state = 1;
          for (int i = 0; i < MAX_LENGTH; i++) {
            buf[i] = 0;  // wipe out buffer so no reuse of old data
          }
          d.receive(p);
          totmsgs++;
          state = 4;
          String subject = "DO NOT REPLY: POC error found.";
          sb.append(Util.ascdate()).append(" ").append(Util.asctime()).append("\n");
          if (p.getLength() > msgLength) {
            prta("POCServers: Datagram wrong size=" + msgLength
                    + "!=" + p.getLength() + " rcv adr=" + p.getSocketAddress());
            sb.append("POCServers: Datagram wrong size=").append(msgLength).append("!=").append(p.getLength()).append(" rcv adr=").append(p.getSocketAddress()).append("\n");
            error = true;
          }
          for (int i = 0; i < p.getLength(); i++) {
            sb.append(Util.leftPad("" + Util.toHex(buf[i]), 4)).append(" ");
            if (i % 16 == 15) {
              sb.append("\n");
            }
          }
          bb.position(12);
          serialHex = bb.getLong();
          bb.get(q330ip);
          bb.get(pocip);
          bb.get(mask);
          base = bb.getShort();
          portNumber = bb.getShort();
          webbps = bb.getShort();
          flags = bb.getShort();
          accessTO = bb.getShort();
          serialBaud = bb.getShort();
          balerHex = bb.getLong();
          sb.append("rcv from ").append(p.getAddress()).append(" port=").append(p.getPort()).
                  append("on ").append(Util.getNode()).append("\n");
          sb.append("POC msg q330=").append(Util.toHex(serialHex)).append(" q330ip=").
                  append(((int) q330ip[0]) & 0xff).append(".").append(((int) q330ip[1]) & 0xff).
                  append(".").append(((int) q330ip[2]) & 0xff).append(".").append(((int) q330ip[3]) & 0xff).
                  append(" pocip = ").append(((int) pocip[0]) & 0xff).append(".").
                  append(((int) pocip[1]) & 0xff).append(".").append(((int) pocip[2]) & 0xff).append(".").
                  append(((int) pocip[3]) & 0xff).append(" mask=").append(((int) mask[0]) & 0xff).
                  append(".").append(((int) mask[1]) & 0xff).append(".").append(((int) mask[2]) & 0xff).
                  append(".").append(((int) mask[3]) & 0xff).append(" bas=").append(base).
                  append(" bps=").append(webbps).append(" pt=").append(portNumber).
                  append(" flgs=").append(Util.toHex(flags)).append(" accessTO=").
                  append(accessTO).append(" baud=").append(serialBaud).append(" Hex=").
                  append(Util.toHex(serialHex)).append("\n");
          if ((base != 5330 && base != 6330) || buf[4] != -59) {
            error = true;
            sb.append("  ***** Rejected - not a C5 or base address invalide base=").append(base).
                    append(" cmd=").append(buf[4]).append("\n");
          }

          // Build a list of computers that are sending to us for statistics purposes
          now = new GregorianCalendar();
          state = 5;
          found = false;
          try {
            rs = dbconn.executeQuery("SELECT * FROM anss.q330 where hexserial='" + Util.toHex(serialHex) + "'");
            if (rs.next()) {
              sb.append(Util.asctime()).append(" ").append(Util.ascdate()).append(" Found hexserial = ").
                      append(Util.toHex(serialHex)).append(" at ").append(rs.getInt("ID")).
                      append(" serial=").append(rs.getString("q330")).append("\n");
              prt(Util.asctime().substring(0, 8) + " POC:adr=" + p.getSocketAddress()
                      + //" len="+p.getLength()+
                      " tot=" + totmsgs
                      + " on ip = " + (((int) pocip[0]) & 0xff) + "." + (((int) pocip[1]) & 0xff) + "."
                      + (((int) pocip[2]) & 0xff) + "." + (((int) pocip[3]) & 0xff)
                      + //" Hex="+Util.toHex(serialHex)+
                      " q330=" + rs.getString("q330")
              );
              String unit = rs.getString("q330");
              state = 6;
              rs.close();
              rs2 = dbconn.executeQuery("SELECT * FROM anss.tcpstation where q330 REGEXP '" + unit + "'");
              if (rs2.next()) {
                state = 15;
                sb.append("Found q330=").append(unit).append(" at ID=").append(rs2.getInt("ID")).
                        append(" allocpoc=").append(rs2.getInt("allowpoc")).append("\n");
                if (rs2.getInt("allowPOC") != 0) {
                  state = 16;
                  if (!InetAddress.getByName(rs2.getString("ipadr")).getHostAddress().equals(
                          p.getAddress().toString().substring(1))) {
                    state = 17;
                    String oldip = rs2.getString("ipadr");
                    String stats = rs2.getString("tcpstation");
                    sb.append(Util.asctime()).append(" ").append(Util.ascdate()).
                            append(" POC update address for Q330=").append(serialHex).
                            append("/").append(unit).append(":").append(base).append(" at ").
                            append(rs2.getString("tcpstation")).append(" to ").append(p.getAddress()).
                            append("\nrcved on POCServer at").append(((int) pocip[0]) & 0xff).
                            append(".").append(((int) pocip[1]) & 0xff).append(".").
                            append(((int) pocip[2]) & 0xff).append(".").append(((int) pocip[3]) & 0xff).
                            append("\n");

                    int ID = rs2.getInt("ID");   // save ID for VMS updat
                    sb.append("The IP address of station ").append(stats).
                            append(" has been changed." + "\n\nNew address = ").
                            append(p.getAddress().toString().substring(1)).
                            append(" was ").append(oldip).append("\n");
                    subject = "_DO NOT REPLY: " + stats + " has changed";
                    if (error) {
                      state = 18;
                      prt("Update not done, some error found.");
                      sb.append("Update not done, some error found.\n");
                      subject = "_DO NOT REPLY: " + stats + " POC address change was rejected!";
                    } else {
                      state = 19;
                      rs2.updateString("ipadr", p.getAddress().toString().substring(1));
                      rs2.updateRow();
                      prta(Util.ascdate() + " " + subject);
                      // Special case - we updated the DB so now update the Form stuff on VMS
                      rs2 = dbconn.executeQuery("SELECT * FROM anss.tcpstation where ID=" + ID);
                      rs2.next();
                      UC.setConnection(dbconn.getConnection());
                      boolean ok = false;
                      while (!ok) {
                        try {
                          DBConnectionThread.executeQuery("TcpStationRO", "SHOW TABLES");
                          ok = true;
                        } catch (SQLException e) {
                          Util.prta("Got exception trying to get TcpStationRO =" + e);
                          SendEvent.debugEvent("POCSQLprob", "POCServer had SQL failure in tcpstation", "POCServer");
                          Util.sleep(10000);
                        }
                      }
                      TCPStation loc = new TCPStation(rs2);    // read back what we just updated 
                      loc.sendForm();                         // Send it to VMS
                      dbconn.executeUpdate("UPDATE anss.vsat SET ipadr='"
                              + p.getAddress().toString().substring(1)
                              + "' WHERE station='" + stats + "'");
                    }
                    error = true;   // this is not really an error but we want to print out on success too.
                    state = 20;
                  } //else sb.append("The address is the same!");
                } else {
                  state = 21;
                  prta("    **** Did not update. POC not configured "
                          + rs2.getString("tcpstation") + " is " + rs2.getString("ipadr") + " poc=" + p.getAddress());
                  sb.append("    **** Did not update POC not allowed on this unit=").
                          append(Util.toHex(serialHex)).append(" at ").
                          append(rs2.getString("tcpstation")).append("\n");
                  //error=true;
                  error = false;
                }
                state = 22;
                rs2.close();

              } else {
                sb.append("   **** Did not find unit=").append(unit).append(" in tcpstation table\n");
                error = true;
                rs2.close();
              }
            } else {
              rs.close();
              state = 7;
              if (!Util.isNEICHost(p.getAddress().getAddress())) {
                prta(Util.ascdate() + " POC:adr=" + p.getSocketAddress()
                        + //" len="+p.getLength()+
                        " totmsg=" + totmsgs
                        + " on ip = " + (((int) pocip[0]) & 0xff) + "." + (((int) pocip[1]) & 0xff) + "."
                        + (((int) pocip[2]) & 0xff) + "." + (((int) pocip[3]) & 0xff)
                //+" Hex="+Util.toHex(serialHex)+" No Q330 found"
                );
                sb.append("    *****Did not find hex serial=").append(Util.toHex(serialHex)).append(" in q330 table\n");

                error = true;
              }
            }
          } catch (SQLException e) {
            e.printStackTrace();
            Util.SQLErrorPrint(e, " Getting info from database or updating RTS info!");
            if (System.currentTimeMillis() - lastEmail > 600000) {
              SimpleSMTPThread.email(Util.getProperty("emailTo"), "POCServer SQLException " + (System.currentTimeMillis() - lastEmail),
                      Util.ascdate() + " " + Util.asctime()
                      + " This is called whenever an SQLException occurs e=" + e.getMessage());
              SendEvent.edgeSMEEvent("POCSSQLExc", "POCMySQLServer had SQL problem" + (System.currentTimeMillis() - lastEmail), this);
              lastEmail = System.currentTimeMillis();
            }
            dbconn.reopen();
          }
          if (dbg || error) {
            prt("Email time=" + (System.currentTimeMillis() - lastEmail) + " " + sb.toString());
          }
          state = 8;
          if (error) {
            state = 9;
            if (System.currentTimeMillis() - lastEmail > 600000) {
              SimpleSMTPThread thr = SimpleSMTPThread.email(Util.getProperty("emailTo"),// to
                      subject, sb.toString());               // from, subj, body
              thr.waitForDone();
              if (!thr.wasSuccessful()) {
                prta("1st Ketchum did not work! " + thr.getSendMailError());
                thr = SimpleSMTPThread.email(Util.getProperty("emailTo"),// to
                        subject + "2", sb.toString());
                thr.waitForDone();
                if (!thr.wasSuccessful()) {
                  prta("2nd ketchum did not work " + thr.getSendMailError());
                }
              }
              lastEmail = System.currentTimeMillis();
            }
          }
          sb.delete(0, sb.length());

        } catch (IOException e) {
          prt("POCServers:receive through IO exception");
        }
        state = 10;
        if (System.currentTimeMillis() - lastStatus > 600000) {
          lastStatus = System.currentTimeMillis();
          prta(Util.ascdate() + " msgs=" + totmsgs);
          if (System.currentTimeMillis() % 86400000 < 600000) {
            SimpleSMTPThread.email(Util.getProperty("emailTo"), "POCServer midnight message - I am alive",
                    "Messages processed by this instance=" + totmsgs + "\n");
          }
        }
      }       // end of infinite loop (while(true))
      state = 12;
    } catch (RuntimeException e) {
      prt("RuntimeException in POCServer thread=" + e.getMessage());
      e.printStackTrace();

      SimpleSMTPThread.email(Util.getProperty("emailTo"), "SHUTDOWN:POCServer is Shuttingdown RunTimeException",
              "This is called whenever this JarCatches ar RuntimeException");
      DBConnectionThread.shutdown();

    }
    //prt("Exiting POCServers run()!! should never happen!****\n");
    prt("POCServers:read loop terminated");
    state = 11;
    System.exit(0);
  }

  private final class POCTimeout extends Thread {

    public POCTimeout() {
      setDaemon(true);
      start();
    }

    @Override
    public void run() {
      int last = totmsgs;
      while (true) {
        try {
          sleep(3600000);
        } catch (InterruptedException expected) {
        }
        if (last == totmsgs && totmsgs > 1) {
          prta("POCTimeout has gone off.  Exit.");
          SendEvent.debugEvent("POCRestartTO", "POCServer has restarted due to timeout", "POCServer");
          System.exit(1);
        }
        last = totmsgs;
      }
    }
  }

  private final class StateTrack extends Thread {

    public StateTrack() {
      setDaemon(true);
      start();
    }

    @Override
    public void run() {
      while (!terminate) {
        try {
          sleep(1800000);
        } catch (InterruptedException expected) {
        }
        prta(Util.ascdate() + " msgs=" + totmsgs + " state=" + state + (state == 1 ? "" : ":" + sb.toString()));
      }
    }
  }

  private class ShutdownPOCServers extends Thread {

    public ShutdownPOCServers() {

    }

    /**
     * this is called by the Runtime.shutdown during the shutdown sequence cause
     * all cleanup actions to occur
     */
    @Override
    public void run() {
      terminate = true;
      SimpleSMTPThread.email(Util.getProperty("emailTo"), "SHUTDOWN:POCServer is Shuttingdown",
              "This is called whenever this Jar ShutsDown()");
      prta("ShutdownPOCServers started");
      int nloop = 0;

      prta("ShutdownPOCServers: Done. CLient c");

    }
  }

  /**
   * @param args the command line arguments
   */
  public static void main(String[] args) {
    Util.setProcess("POCServer");
    EdgeThread.setMainLogname("pocserver");
    EdgeProperties.init();
    Util.prt(Util.asctime());
    Util.setModeGMT();
    Util.setNoInteractive(true);
    Util.setNoconsole(false);
    Util.prt(Util.asctime() + " DBServer=" + Util.getProperty("DBServer"));
    boolean poc = true;
    boolean useDB = true;
    //ServerThread server = new ServerThread(AnssPorts.PROCESS_SERVER_PORT, false);
    DBMessageServer m = null;
    POCServer t = null;
    int port = 2254;
    //try  {
    for (int i = 0; i < args.length; i++) {
      if (args[i].equals("-p")) {
        port = Integer.parseInt(args[i + 1]);
      }
      if (args[i].equals("-?") || args[i].indexOf("help") > 0) {
        Util.prt("-p nnnn Set the port to something other than 2254 (a generally bad idea)");
        Util.prt("-nopoc  Do not set up a POC thread for Q330s on dynamic addresses");
        Util.prt("-nomysql Do not set up a DBMessage thread for processing DB updates");
        Util.prt("-nodb   Do not set up a DBMessage thread for processing DB updates");
        Util.prt("-?            Print this message");
        System.exit(0);
      }
      if (args[i].equals("-nopoc")) {
        poc = false;
      }
      if (args[i].equals("-nomysql") || args[i].equalsIgnoreCase("-nodb")) {
        useDB = false;
      }
    }
    if (Util.getProperty("DBServer") == null) {
      useDB = false;
    } else if (Util.getProperty("DBServer").equalsIgnoreCase("NoDB")) {
      useDB = false;
    } else if (Util.getProperty("DBServer").equals("")) {
      useDB = false;
    }
    if (poc) {
      t = new POCServer(port);
    }
    if (useDB) {
      m = new DBMessageServer("-empty >>dbmsgsrv", "DBMS");
    }

    for (;;) {
      Util.sleep(30000);
      if (useDB && m != null) {
        if (!m.isAlive()) {
          Util.prt("DBMessage server has died - exit");
          System.exit(1);
          break;
        }
      }
      if (poc && t != null) {
        if (!t.isAlive()) {
          Util.prt("POCServer has died - exit ");
          System.exit(1);
          break;
        }
      }
      if (System.currentTimeMillis() % 86400000L <= 30000) {
        Util.prt(Util.ascdate() + " " + Util.asctime() + " its a new day");
      }
    }
    SendEvent.debugEvent("POCSrvDown", "POCServer on " + Util.getRoles(null)[0] + "/" + Util.getNode() + " has exitted", "POCServer");
    //MakeEconDump d = new MakeEconDump();
  }

}
