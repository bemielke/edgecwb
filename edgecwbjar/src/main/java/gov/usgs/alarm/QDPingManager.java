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
import gov.usgs.anss.util.UC;
import gov.usgs.anss.util.Util;
import gov.usgs.anss.edgethread.EdgeThread;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.TreeMap;

/**
 * Thread that pings Q330s and tracks their status. This is only used in the
 * Alarm process.
 *
 * @author U.S. Geological Survey  <ketchum at usgs.gov>
 */
public class QDPingManager extends EdgeThread {

  private final static TreeMap<String, QDPing> qdpings = new TreeMap<>();
  private int port = 5330;
  private boolean balerMode = false;
  private TreeMap pings = new TreeMap();
  private boolean singleMode = false;
  private DBConnectionThread dbconn2;
  private InetAddress rtsaddr, q330adr, saverts;
  private String ipadr = "";
  private boolean nooutput;
  private boolean noQDPing;
  private int delay = 60;
  private String tunnelports;

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

  public QDPingManager(String argline, String tg) {
    super(argline, tg);

    //StaSrv srv = new StaSrv(null, 2052);
    // this will keep a connection up to anss
    try {
      dbconn2 = new DBConnectionThread(Util.getProperty("DBServer"), "readonly", "anss",
              false, false, "QDPingAnss", Util.getOutput());
      if (!DBConnectionThread.waitForConnection("QDPingAnss")) {
        if (!DBConnectionThread.waitForConnection("QDPingAnss")) {
          if (!DBConnectionThread.waitForConnection("QDPingAnss")) {
            prta("Database for QDPING did not start promptly");
          }
        }
      }
    } catch (InstantiationException e) {
      prta("InstantiationException opening anss database in main() e=" + e.getMessage());
      System.exit(1);
    }
    //String inputfile="q330.out";

    String[] args = argline.split("\\s");
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
        prt("No output mode");
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
    prt("QDPing main() singlemode=" + singleMode + " balermode=" + balerMode + " noQDPing=" + noQDPing + " delay=" + delay + " noout=" + nooutput);
    setDaemon(true);
    running = true;
    start();
  }

  @Override
  public void run() {
    String station;
    int poc;
    byte[] rtsaddrbytes;
    if (singleMode) {
      prta("QDPing mail() in single mode for ip=" + ipadr);
      nooutput = true;
      QDPing.setNoOutput(true);
      prt("No output mode");
      QDPing[] single = null;
      if (!ipadr.contains(".")) {
        try {
          // Get user from the Inventory database user file
          ResultSet rs = dbconn2.executeQuery("SELECT * FROM anss.tcpstation where tcpstation='" + ipadr + "'");
          if (rs.next()) {
            UC.setConnection(dbconn2.getConnection());
            TCPStation t = new TCPStation(rs);
            prt(t.getQ330toString());
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
            prta(single1.toString());
          }
        } catch (InterruptedException expected) {
        }
      }
    }
    prta("noQDPing=" + noQDPing);
    if (noQDPing) {
      for (;;) {
        try {
          sleep(1000);
        } catch (InterruptedException expected) {
        }
      }
    }
    prta("Starting first database scan");
    while (!terminate) {
      try {
        // Get user from the Inventory database user file
        while (!terminate) {
          //ResultSet rs = dbconn2.executeQuery("SELECT * FROM anss.tcpstation order by tcpstation");
          ResultSet rs = dbconn2.executeQuery("SELECT tcpstation,ipadr,tunnelports,q330,allowpoc,seednetwork "
                  + "FROM anss.tcpstation,anss.nsnstation WHERE anss.tcpstation.tcpstation = anss.nsnstation.nsnstation ORDER BY tcpstation");
          while (rs.next()) {
            station = rs.getString("tcpstation");
            ipadr = rs.getString("ipadr");
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
              prta(tag + " UTS: Got bad RTS from config==" + tag + " " + ipadr + " discard.");
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
                        prta("Got a " + adrtokens[0] + " but POC is not on!!!!");
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
                        prta(tag + " UTS: Got bad RTS from config==" + tag + " " + ipadr + " discard.");
                        continue;
                      }
                      break;
                  }
                } else {
                  prta(tag + " UTS: *** got bad nunber of tokens i=" + i + " q330s=" + q330s[i] + " ntokens=" + tokens.length);
                }
                // The address of the Q330 is in rtsaddressbytes
                try {
                  q330adr = InetAddress.getByAddress(rtsaddrbytes);
                } catch (UnknownHostException e) {
                  prta("Unknow host exception bytes =" + rtsaddrbytes[0] + "." + rtsaddrbytes[1] + "."
                          + rtsaddrbytes[2] + "." + rtsaddrbytes[3]);
                  continue;
                }
                boolean old = false;
                Object[] thr = qdpings.values().toArray();

                for (Object thr1 : thr) {
                  QDPing q = (QDPing) thr1;
                  if (!q.isAlive()) {
                    prta("QDPing is no alive q=" + q);
                    continue;
                  }
                  //prt("tag="+tag+" to "+q.getTag()+" "+q.getIpadr()+" to "+q330adr.getHostAddress()+" tag "+tag.equals(q.getTag())+" ip "+
                  //        q.getIpadr().equals(q330adr.getHostAddress()));
                  if (tag.equals(q.getTag())) {
                    if (q.getIpadr().equals(q330adr.getHostAddress())) {
                      old = true;
                    } else {
                      prta(q.getTag() + " " + q.getIpadr() + "!=" + q330adr.getHostAddress() + " must be a POC change");
                      q.terminate();   // we have one at another address!
                    }
                  }
                }
                if (!old) {
                  prta("New QDPing to " + tag + " " + q330adr.getHostAddress() + "/" + (balerMode ? port + 14 : port));
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
                  prta("Ping is no alive p=" + p);
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
                  //prta("Check PingListener "+p.getIpadr()+" rts="+rtsaddr.getHostAddress()+" "+p.getIpadr().equals(rtsaddr.getHostAddress()));
                  if (p.getIpadr().equals(rtsaddr.getHostAddress())) {
                    old = true;
                  } else {
                    prta(p.getTag() + " " + p.getIpadr() + "!=" + rtsaddr.getHostAddress() + " must be a Ping only POC?");
                    p.terminate();
                  }
                }
                //}
              }
              if (!old) {
                //tag = srv.getTranslation("IR",station,"  ","BHZ").substring(0,2)+"-"+station;
                prta("New PingListener to RTS at " + tag + " " + rtsaddr.getHostAddress() + "/" + port);
                pings.put(tag, new PingListener(rtsaddr.getHostAddress(), tag));
              }
            }
          }
          prta("QDPing bottom #QDPing=" + qdpings.size() + " #Pings=" + pings.size());
          try {
            sleep(300000);
          } catch (InterruptedException expected) {
          }
        }
      } catch (SQLException e) {
        Util.SQLErrorPrint(e, "Error reading SQL stuff: e=" + e.getMessage());
      }
      try {
        sleep(30000);
      } catch (InterruptedException expected) {
      }
    }
    prta("QDPingManager is exiting.");
    running = false;
  }
}
