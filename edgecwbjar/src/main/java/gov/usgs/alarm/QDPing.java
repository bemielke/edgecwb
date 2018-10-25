/*
 *  This software is in the public domain because it contains materials 
 *  that originally came from the United States Geological Survey, 
 *  an agency of the United States Department of Interior. For more 
 *  information, see the official USGS copyright policy at 
 *  http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.alarm;

import gov.usgs.anss.db.DBConnectionThread;
import gov.usgs.anss.guidb.TCPStation;
import gov.usgs.anss.net.SNWSender;
import gov.usgs.anss.q330.Q330;
//import gov.usgs.anss.util.StaSrv;
import gov.usgs.anss.util.UC;
import gov.usgs.anss.util.Util;
import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.DecimalFormat;
import java.util.GregorianCalendar;
import java.util.Iterator;
import java.util.TreeMap;

/**
 * This class sends a QDPing to a Q330 which causes the Q330 to return a UDP
 * response.
 *
 * @author davidketchum
 */
public final class QDPing extends Thread {

  private final static TreeMap<String, QDPing> qdpings = new TreeMap<String, QDPing>();
  private static SNWSender snw;
  private int delay;
  private long lastSent;            // milliseconds at last send
  private long lastRec;             // milliseconds since last received
  private long lastQDPtrip;         // age of last received packet to last sent (last good QDPing trip time)
  private static DecimalFormat df6;
  private long totaldelay;
  private long totalok;
  private int port;
  private int nsent;
  private int nrcv;
  private static boolean noOutput;    // disable pingListeners, SNW output, dbconn2 updates
  private DatagramSocket out;
  private boolean terminate;
  private final InetSocketAddress q330;
  private short seq;
  private ListenSocket listener;
  private String ipadr;
  private String tag;
  private boolean dbg;
  private PingListener pinger;
  private static DBConnectionThread dbconn;
  private static final Integer dbmutex = Util.nextMutex();

  static public void setNoOutput(boolean b) {
    noOutput = b;
  }

  @Override
  public String toString() {
    GregorianCalendar last = new GregorianCalendar();
    if (df6 == null) {
      df6 = new DecimalFormat("######");
    }
    last.setTimeInMillis(lastRec);
    long now = System.currentTimeMillis();
    String s = (tag + "        ").substring(0, 8)
            + (ipadr + "         ").substring(0, 15)
            +//" #x="+Util.leftPad(df6.format(nsent),6)+
            " #r=" + Util.leftPad(df6.format(nrcv), 6)
            + Util.leftPad("" + ((int) (((double) nrcv) * 100. / ((double) nsent))), 4)
            + "% lst=" + Util.leftPad("" + Math.min(lastQDPtrip, 999999) / 1000., 6)
            + " s avg=" + Util.leftPad("" + (totaldelay / 1000. / totalok), 4) + " s ";
    if (pinger != null) {
      s
              += " png=" + Util.leftPad("" + pinger.getTime() / 1000., 5)
              + //" sq="+Util.leftPad(df6.format(pinger.getLastSeq()),5)+
              " " + ((Math.min(now - pinger.getLastRcvMillis(), 99999999) / 1000.) + "    ").substring(0, 5)
              + " ago "
              + Util.ascdate(last).substring(5) + " " + Util.asctime(last).substring(0, 5) + (now - lastRec < 60000 ? " OK" : " Down");
    }
    return s;
  }

  public static void setSNWSender(SNWSender s) {
    snw = s;
  }

  public String getTag() {
    return tag;
  }

  public String getIpadr() {
    return ipadr;
  }

  public static String getStatusAll() {
    if (qdpings.isEmpty()) {
      return "QDPing is not running on this node\n";
    }
    if (df6 == null) {
      df6 = new DecimalFormat("######");
    }
    StringBuilder sb = new StringBuilder(10000);
    synchronized (qdpings) {
      for (Iterator<QDPing> it = qdpings.values().iterator(); it.hasNext();) {
        QDPing q = it.next();
        sb.append(q.toString()).append("\n");
      }
    }
    return sb.toString();
  }

  public void setDebug(boolean b) {
    dbg = b;
  }

  /**
   * Creates a new instance of QDPing
   */
  public void terminate() {
    terminate = true;
    pinger.shutdown();
  }

  public QDPing(String tg, String ip, int pt, int sec) {
    delay = sec;
    dbg = false;
    try {
      if (snw == null && !noOutput) {
        snw = new SNWSender(300, 140);
      }
    } catch (UnknownHostException e) {
      Util.prt("Unknown host setting ud SNWSender!");
      snw = null;
    }
    tag = tg;
    ipadr = ip;
    port = pt;
    terminate = false;
    nsent = 0;
    nrcv = 0;
    lastRec = System.currentTimeMillis();
    totaldelay = 2000;        // fake these so you do not get divide by zero later
    totalok = 1;
    dbconn = DBConnectionThread.getThread("QDPingStatus");
    if (dbconn == null) {
      try {
        dbconn = new DBConnectionThread(Util.getProperty("StatusDBServer"), "update", "status",
                true, false, "QDPingStatus", Util.getOutput());
        if (!dbconn.waitForConnection()) {
          if (!dbconn.waitForConnection()) {
            if (!dbconn.waitForConnection()) {
              Util.prt("****** Could not connect to status database");
            }
          }
        }
      } catch (InstantiationException e) {
        Util.prta("QDPing database instantiation exception is impossilble!" + e.getMessage());
        dbconn = DBConnectionThread.getThread("QDPingStatus");
      }
    }

    try {
      out = new DatagramSocket();
    } catch (SocketException e) {
      Util.prt("Error opening local Datagram socket to q330");
    }
    q330 = new InetSocketAddress(ip, port);
    qdpings.put(tag, (QDPing) this);
    //if(!noOutput) pinger = new PingListener(ip, tag);   // no longer do a PING for a station with a Q330, use QDPing
    start();
  }

  @Override
  public void run() {
    byte[] buf = new byte[16];
    ByteBuffer b = ByteBuffer.wrap(buf);
    //int [] b1 = {0xb6,0x1e,0xb6,0xd8,0x38,0x02,0,4,0,16,0,0,0,0,0,16};
    long startRcv = System.currentTimeMillis();
    long stopRcv = System.currentTimeMillis();
    PreparedStatement insert = null;
    buf[4] = (byte) 0x38;
    buf[5] = (byte) 2;
    buf[6] = (byte) 0;
    buf[7] = (byte) 4;     // command, version, 16 bytes long
    buf[10] = (byte) 0;
    buf[11] = (byte) 0;                // Ack number
    buf[12] = (byte) 0;
    buf[13] = (byte) 0;                // Type = 0, to Q330
    listener = new ListenSocket(out);// create listener for responses
    boolean first = false;
    GregorianCalendar rectime = new GregorianCalendar();
    StringBuilder tmp = new StringBuilder(100);
    Util.prta("QDPing run() started for " + ipadr + " delay=" + delay + " port=" + port + " noOutput=" + noOutput);
    while (!terminate) {
      if (ipadr.equals("67.47.200.99") && dbg) {
        Util.prta("Top " + ipadr);
      }
      seq++;
      buf[8] = (byte) ((seq >> 8) & 0xff);            // 16 bit sequence
      buf[9] = (byte) (seq & 0xff);
      buf[14] = (byte) ((seq >> 8) & 0xff);            // id
      buf[15] = (byte) (seq & 0xff);

      lastSent = System.currentTimeMillis();
      //for(int i=0; i<b1.length; i++) buf[i]= (byte) b1[i];// override with known good packet
      byte[] crc = Q330.calcCRC(buf, 4, buf.length);
      System.arraycopy(crc, 0, buf, 0, 4);
      //for(int i=0; i<4; i++) buf[i]=crc[i];
      try {
        if (ipadr.equals("67.47.200.99") && dbg) {
          Util.prta("Send " + ipadr);
        }
        DatagramPacket p = new DatagramPacket(buf, 16, InetAddress.getByName(ipadr), port);
        out.send(p);
        nsent++;
        if (nsent % 100 == 0 || (ipadr.equals("67.47.200.99") && dbg)) {
          Util.prta(tag + " " + ipadr + " #sent=" + nsent + " #rcv=" + nrcv
                  + (" " + ((double) nrcv) * 100. / ((double) nsent) + "     ").substring(0, 6)
                  + "% last diff=" + (lastRec - lastSent)
                  + (" avg=" + (totaldelay / 1000. / totalok) + "    ").substring(0, 8));
        }
      } catch (IOException e) {
        Util.IOErrorPrint(e, "Error writing datagram packet");
      }
      if (ipadr.equals("67.47.200.99") && dbg) {
        Util.prta("Sleep " + ipadr);
      }
      try {
        sleep(delay * 1000L);
      } catch (InterruptedException expected) {
      }
      rectime.setTimeInMillis(lastRec);
      long now = System.currentTimeMillis();
      Util.clear(tmp).append(tag).append(":4:QDPnsent=").append(nsent).append(";QDPpctRcv=").append(((double) nrcv) * 100. / ((double) nsent)).
              append(";QDPtime=").append((Math.max((lastRec - lastSent) / 1000., -999.))).
              append(";QDPlastrcv=").append(Util.ascdatetime2(rectime, null).delete(0, 5));
      //tmp = tag+":4:QDPnsent="+nsent+
      //    ";QDPpctRcv="+(((double) nrcv)*100./((double) nsent)+"     ").substring(0,6).trim()+
      //    ";QDPtime="+(Math.max((lastRec-lastSent)/1000.,-999.)+"     ").substring(0,5).trim()+
      //    ";QDPlastrcv="+Util.ascdate(rectime).substring(5)+" "+Util.asctime(rectime).substring(0,8);
      //Util.prt(tmp);
      if (!noOutput && snw != null) {
        snw.queue(tmp);
      }
      if (ipadr.equals("67.47.200.99") && dbg) {
        Util.prt("SNW to " + ipadr + " " + tmp);
      }

      if (System.currentTimeMillis() - lastRec > 2 * delay * 1000L && first) {
        Util.prta(tag + " " + ipadr + " has quit responding after "
                + (System.currentTimeMillis() - startRcv) / 1000
                + " sec #sent=" + nsent + " #rcv=" + nrcv);
        if (ipadr.equals("67.47.200.99") && dbg) {
          Util.prta("Stop " + ipadr + " noout=" + noOutput);
        }
        stopRcv = System.currentTimeMillis();
        int ok = 0;
        while (ok < 2) {
          try {
            if (!noOutput) {
              synchronized (dbmutex) {
                if (insert == null) {
                  insert = dbconn.prepareStatement("INSERT INTO qdping (time,station,type,duration,nsent,nrcv) VALUES "
                          + "(NOW(),?,'2',?,?,?)", true);
                }
                insert.setString(1, tag);
                insert.setInt(2, (int) ((System.currentTimeMillis() - startRcv) / 1000));
                insert.setInt(3, nsent);
                insert.setInt(4, nrcv);
                insert.executeUpdate();
                break;
                //dbconn.executeUpdate(
                //        "INSERT INTO qdping (time,station,type,duration,nsent,nrcv) VALUES "+
                //        "(NOW(),'"+tag+"','2','"+((System.currentTimeMillis()-startRcv)/1000)+"','"+
                //        nsent+"','"+nrcv+"')");
              }
            }
          } catch (SQLException e) {
            if (ok > 0) {
              Util.SQLErrorPrint(e, tag + "QDPing: ** SQLerror writing to status.qdping retry=" + ok + " e=" + e.getMessage());
            }
            dbconn.reopen();
            ok++;
            insert = null;
          }
        }
        first = false;
      } else if (System.currentTimeMillis() - lastRec < 2 * delay * 1000L && !first) {
        Util.prta(tag + " " + ipadr + " Start receiving UDP pings after "
                + (System.currentTimeMillis() - stopRcv) / 1000 + " "
                + "sec delay=" + (lastRec - lastSent)
                + " #sent=" + nsent + " #rcv=" + nrcv);
        startRcv = System.currentTimeMillis();
        if (ipadr.equals("67.47.200.99") && dbg) {
          Util.prta("Start " + ipadr + " nooutput=" + noOutput);
        }
        try {
          if (!noOutput) {
            dbconn.executeUpdate(
                    "INSERT INTO qdping (time,station,type,duration,nsent,nrcv) VALUES "
                    + "(NOW(),'" + tag + "','1','" + ((System.currentTimeMillis() - startRcv) / 1000) + "','" + nsent
                    + "','" + nrcv + "')");
          }
          if (ipadr.equals("67.47.200.99") && dbg) {
            Util.prta("Aft Start Insert " + ipadr);
          }
        } catch (SQLException e) {
          Util.SQLErrorPrint(e, tag + "QDPing: SQLerror writing to status.qdping e=" + e.getMessage());
          dbconn.reopen();
        }
        first = true;
      }
      if (ipadr.equals("67.47.200.99") && dbg) {
        Util.prta("Bottom " + ipadr);
      }
    }
    Util.prta(tag + " " + ipadr + " send thread terminated.");
  }

  private final class ListenSocket extends Thread {

    private final DatagramSocket in;
    private int lastseq;
    private boolean dbg;

    public void setDebug(boolean t) {
      dbg = t;
    }

    public ListenSocket(DatagramSocket i) {
      in = i;
      //dbg=true;
      start();

    }

    @Override
    public void run() {
      byte[] buf = new byte[20];
      DatagramPacket d = new DatagramPacket(buf, 20);
      StringBuilder sb = new StringBuilder(100);
      String msg;
      while (!terminate) {
        try {
          in.receive(d);
          nrcv++;
          lastRec = System.currentTimeMillis();
          lastQDPtrip = lastRec - lastSent;
          byte[] b = d.getData();
          short inseq = (short) (((short) b[8]) << 8 | ((short) b[9] & (short) 0xff));
          short id = (short) (((short) b[14]) << 8 | ((short) b[15] & (short) 0xff));
          byte[] chksum = Q330.calcCRC(b, 4, d.getLength());
          if (sb.length() > 0) {
            sb.delete(0, sb.length());
          }

          if (b[1] == chksum[1] && b[0] == chksum[0] && b[2] == chksum[2] && b[3] == chksum[3]) {
            msg = "OK";
          } else {
            msg = "chksum error";
          }
          if (dbg) {
            Util.prt(tag + " " + ipadr + " Rcv  inseq=" + inseq + " id=" + id + " len=" + d.getLength()
                    + " crc=" + msg
                    + " diff=" + (lastRec - lastSent) + " avg=" + totaldelay / totalok);
          }
          if (id != seq) {
            Util.prta(tag + " " + ipadr + " Rcv seq not last got " + id + " expect " + seq
                    + " late est=" + (seq - id) * delay + " in=" + in.getLocalPort() + " " + in.getInetAddress() + " d=" + d.getPort() + " d=" + d.getAddress() + " d=" + d);
          } else {
            totaldelay += (lastRec - lastSent);
            totalok++;
          }
        } catch (IOException e) {
          Util.prt("Error receiving QDP Ping packet" + e.getMessage());
          Util.IOErrorPrint(e, "Errror receiving QDPPing packet");
        }
      }
      Util.prta(tag + " " + ipadr + " rcv thread terminated.");
    }
  }   // end of closee ListenSocket

  public static void main(String[] args) {
    Util.setModeGMT();
    Util.prt(Util.asctime());
    Util.setNoInteractive(true);
    String ipadr = "69.35.50.109";
    String tag = "TEST";
    String station;
    InetAddress rtsaddr, q330adr, saverts;
    int poc;
    String tunnelports;
    byte[] rtsaddrbytes;
    boolean tcpip = false;
    int delay = 60;
    boolean terminate = false;
    boolean noQDPing = false;
    boolean nooutput = false;
    //StaSrv srv = new StaSrv(null, 2052);
    // this will keep a connection up to anss
    DBConnectionThread dbconn2 = null;
    try {
      dbconn2 = new DBConnectionThread(Util.getProperty("DBServer"), "readonly", "anss",
              false, false, "QDPingAnss", Util.getOutput());
      if (!DBConnectionThread.waitForConnection("QDPingAnss")) {
        if (!DBConnectionThread.waitForConnection("QDPingAnss")) {
          if (!DBConnectionThread.waitForConnection("QDPingAnss")) {
            Util.prta("Database for QDPING did not start promptly");
          }
        }
      }
    } catch (InstantiationException e) {
      Util.prta("InstantiationException opening anss database in main() e=" + e.getMessage());
      System.exit(1);
    }
    //String inputfile="q330.out";
    int port = 5330;
    boolean balerMode = false;
    TreeMap pings = new TreeMap();
    boolean singleMode = false;
    for (int i = 0; i < args.length; i++) {
      if (args[i].equals("-s")) {
        delay = Integer.parseInt(args[i + 1]);
      }
      if (args[i].equals("-noQDPing")) {
        noQDPing = true;
      }
      if (args[i].equals("-nooutput")) {
        nooutput = true;
        QDPing.setNoOutput(true);
        Util.prt("No output mode");
      }
      if (args[i].equals("-balers")) {
        balerMode = true;
      }
      if (args[i].equals("-p")) {
        port = Integer.parseInt(args[i + 1]);
        i++;
      }
      if (args[i].equals("-ip")) {
        singleMode = true;
        ipadr = args[i + 1];
        i++;
      }
    }
    Util.prt("QDPing main() singlemode=" + singleMode + " balermode=" + balerMode + " noQDPing=" + noQDPing + " delay=" + delay + " noout=" + nooutput);
    if (singleMode) {
      Util.prta("QDPing mail() in single mode for ip=" + ipadr);
      nooutput = true;
      QDPing.setNoOutput(true);
      Util.prt("No output mode");
      QDPing[] single = null;
      if (!ipadr.contains(".")) {
        try {
          // Get user from the Inventory database user file
          ResultSet rs = dbconn2.executeQuery("SELECT * FROM anss.tcpstation where tcpstation='" + ipadr + "'");
          if (rs.next()) {
            UC.setConnection(dbconn2.getConnection());
            TCPStation t = new TCPStation(rs);
            Util.prt(t.getQ330toString());
            //ipadr = t.getQ330InetAddresses()[0].toString().substring(1);
            //station = rs.getString("tcpstation");
            single = new QDPing[t.getNQ330s()];
            for (int i = 0; i < t.getNQ330s(); i++) {
              single[i] = new QDPing(tag, t.getQ330InetAddresses()[i].toString().substring(1),
                      (balerMode ? t.getQ330Ports()[i] + 14 : t.getQ330Ports()[i]), delay);
            }
          }
        } catch (SQLException expected) {
        }
      } else {
        single = new QDPing[1];
        single[0] = new QDPing(tag, ipadr, (balerMode ? port + 14 : port), delay);
      }
      for (;;) {
        try {
          sleep(10000);
          for (QDPing single1 : single) {
            Util.prta(single1.toString());
          }
        } catch (InterruptedException expected) {
        }
      }
      //System.exit(0);
    }
    Util.prta("noQDPing=" + noQDPing);
    if (noQDPing) {
      for (;;) {
        try {
          sleep(1000);
        } catch (InterruptedException expected) {
        }
      }
    }
    Util.prta("Starting first database scan");
    for (;;) {
      try {
        // Get user from the Inventory database user file
        while (!terminate) {
          //ResultSet rs = dbconn2.executeQuery("SELECT * FROM anss.tcpstation order by tcpstation");
          ResultSet rs = dbconn2.executeQuery("SELECT tcpstation,ipadr,tunnelports,q330,allowpoc,seednetwork,cpuid "
                  + "FROM anss.tcpstation,anss.nsnstation WHERE anss.tcpstation.tcpstation = anss.nsnstation.nsnstation ORDER BY tcpstation");
          while (rs.next()) {
            station = rs.getString("tcpstation");
            ipadr = rs.getString("ipadr");
            if (rs.getInt("cpuid") <= 0) {
              continue;   // Not assigned, do not monitor
            }
            if (station.contains("%") && !station.contains("LABV%")) {
              continue;
            }
            if (station.substring(0, 3).equalsIgnoreCase("TST")) {
              continue;
            }
            if (station.substring(0, 3).equalsIgnoreCase("DCK")) {
              continue;
            }
            if (station.substring(0, 3).equalsIgnoreCase("GLD")) {
              continue;
            }
            if (station.length() >= 4) {
              if (station.substring(0, 4).equalsIgnoreCase("TEST")) {
                continue;
              }
            }
            if (rs.getString("seednetwork").equals("XX")) {
              continue;
            }
            if (ipadr.equals("001.001.001.001")) {
              continue;
            }

            try {
              rtsaddr = InetAddress.getByName(ipadr);
              saverts = rtsaddr;
            } catch (UnknownHostException e) {
              Util.prta(tag + " UTS: Got bad RTS from config==" + tag + " " + ipadr + " discard.");
              continue;
            }
            tunnelports = rs.getString("tunnelports");
            poc = rs.getInt("allowpoc");
            if (rs.getString("q330").length() > 0) {
              String[] q330s = tunnelports.split(",");
              for (int i = 0; i < q330s.length; i++) {
                tag = station;
                if (i > 0) {
                  if (tag.length() == 3) {
                    tag = tag.substring(0, 3) + "1";
                  } else if (tag.length() == 4) {
                    if (tag.charAt(3) == '1') {
                      tag = tag.substring(0, 3) + (i + 1);
                    } else {
                      tag = tag.substring(0, 3) + i;
                    }
                  }
                }
                tag = (rs.getString("seednetwork") + "-" + tag).replaceAll(" ", "_");
                rtsaddrbytes = saverts.getAddress();
                String[] tokens = q330s[i].split(":");
                String outputPort = tokens[0];
                if (tokens.length == 1) {
                  if (poc == 0) {
                    rtsaddrbytes[3] += 1 + i;
                  }
                } else if (tokens.length > 1) {

                  String[] adrtokens = tokens[1].split("/");
                  port = 5330;
                  if (adrtokens.length > 1) {
                    port = Integer.parseInt(adrtokens[1]);
                  }
                  switch (adrtokens[0].substring(0, 1)) {
                    case ".":
                      // This is a poc site so do not disturb the addres
                      if (poc == 0) {
                        Util.prta("Got a " + adrtokens[0] + " but POC is not on!!!!");
                      }
                      break;
                    case "+":
                      //its a + address
                      int number = Integer.parseInt(adrtokens[0].substring(1));
                      rtsaddrbytes[3] += number;
                      break;
                    default:
                      // It must be a full IP address
                      try {
                        rtsaddr = InetAddress.getByName(adrtokens[0]);
                        rtsaddrbytes = rtsaddr.getAddress();
                      } catch (UnknownHostException e) {
                        Util.prta(tag + " UTS: Got bad RTS from config==" + tag + " " + ipadr + " discard.");
                        continue;
                      }
                      break;
                  }
                } else {
                  Util.prta(tag + " UTS: *** got bad nunber of tokens i=" + i + " q330s=" + q330s[i] + " ntokens=" + tokens.length);
                }
                // The address of the Q330 is in rtsaddressbytes
                try {
                  q330adr = InetAddress.getByAddress(rtsaddrbytes);
                } catch (UnknownHostException e) {
                  Util.prta("Unknow host exception bytes =" + rtsaddrbytes[0] + "." + rtsaddrbytes[1] + "."
                          + rtsaddrbytes[2] + "." + rtsaddrbytes[3]);
                  continue;
                }
                boolean old = false;
                Object[] thr = qdpings.values().toArray();

                for (Object thr1 : thr) {
                  QDPing q = (QDPing) thr1;
                  if (!q.isAlive()) {
                    Util.prta("QDPing is no alive q=" + q);
                    continue;
                  }
                  //Util.prt("tag="+tag+" to "+q.getTag()+" "+q.getIpadr()+" to "+q330adr.getHostAddress()+" tag "+tag.equals(q.getTag())+" ip "+
                  //        q.getIpadr().equals(q330adr.getHostAddress()));
                  if (tag.equals(q.getTag())) {
                    if (q.getIpadr().equals(q330adr.getHostAddress())) {
                      old = true;
                    } else {
                      Util.prta(q.getTag() + " " + q.getIpadr() + "!=" + q330adr.getHostAddress() + " must be a POC change");
                      q.terminate();   // we have one at another address!
                    }
                  }
                }
                if (!old) {
                  Util.prta("New QDPing to " + tag + " " + q330adr.getHostAddress() + "/" + (balerMode ? port + 14 : port));
                  synchronized (qdpings) {
                    qdpings.put(tag, new QDPing(tag, q330adr.getHostAddress(), (balerMode ? port + 14 : port), delay));
                  }   // Note: a PingListener is created within the QDPing
                }
              }
            } else {      // Its just an RTS - start the ping lisener
              if (nooutput) {
                continue;
              }
              boolean old = false;
              Object[] thr = pings.values().toArray();
              tag = (rs.getString("seednetwork") + "-" + station).replaceAll(" ", "_");
              for (Object thr1 : thr) {
                PingListener p = (PingListener) thr1;
                if (!p.isAlive()) {
                  Util.prta("Ping is no alive p=" + p);
                  continue;
                }
                //String [] tagPargs = p.getTag().split("[-\\[\\]]");   // parse out the tag to net, station, thrd#
                //if(tagPargs.length > 1) {
                //  if(station.equals(tagPargs[1])) {
                String ptag = p.getTag();
                if (ptag.contains("[")) {
                  ptag = ptag.substring(0, ptag.indexOf("["));
                }
                if (ptag.equals(tag)) {
                  //Util.prta("Check PingListener "+p.getIpadr()+" rts="+rtsaddr.getHostAddress()+" "+p.getIpadr().equals(rtsaddr.getHostAddress()));
                  if (p.getIpadr().equals(rtsaddr.getHostAddress())) {
                    old = true;
                  } else {
                    Util.prta(p.getTag() + " " + p.getIpadr() + "!=" + rtsaddr.getHostAddress() + " must be a Ping only POC?");
                    p.terminate();
                  }
                }
                //}
              }
              if (!old) {
                //tag = srv.getTranslation("IR",station,"  ","BHZ").substring(0,2)+"-"+station;
                Util.prta("New PingListener to RTS at " + tag + " " + rtsaddr.getHostAddress() + "/" + port);
                pings.put(tag, new PingListener(rtsaddr.getHostAddress(), tag));
              }
            }
          }
          Util.prta("QDPing bottom #QDPing=" + qdpings.size() + " #Pings=" + pings.size());
          try {
            sleep(300000);
          } catch (InterruptedException e) {
          }
        }
      } catch (SQLException e) {
        Util.SQLErrorPrint(e, "Error reading SQL stuff: e=" + e.getMessage());
      }
      try {
        sleep(30000);
      } catch (InterruptedException e) {
      }
    }
  }
}
