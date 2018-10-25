/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.alarm;

import gov.usgs.anss.db.DBConnectionThread;
import gov.usgs.anss.edgethread.EdgeThread;
import gov.usgs.anss.guidb.TCPStation;
import gov.usgs.anss.net.Route;
import gov.usgs.anss.util.Subprocess;
import gov.usgs.anss.util.UC;
import gov.usgs.anss.util.Util;
import java.io.*;
import java.net.InetAddress;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * This class maintains the /etc/hosts file for computers and stations at the
 * NEIC using the database to get hostnames and ip addresses in the DB.
 *
 * @author davidketchum
 */
public final class MakeEtcHosts extends EdgeThread {

  private final StringBuffer sb= new StringBuffer(1000);
  private String last;
  private DBConnectionThread dbconnanss;

  @Override
  public void terminate() {
    terminate = true;
    interrupt();
    dbconnanss.terminate();
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
   * Creates a new instance of MakeEtcHosts
   *
   * @param argline
   * @param tg
   */
  public MakeEtcHosts(String argline, String tg) {
    super(argline, tg);
    try {
      if (DBConnectionThread.getThread("anssHosts") == null) {
        DBConnectionThread temp = new DBConnectionThread(Util.getProperty("DBServer"), "update", "anss",
                false, false, "anssHosts", EdgeThread.getStaticPrintStream());
        EdgeThread.addStaticLog(temp);
        if (!DBConnectionThread.waitForConnection("anssHosts")) {
          if (!DBConnectionThread.waitForConnection("anssHosts")) {
            if (!DBConnectionThread.waitForConnection("anssHosts")) {
              prt("Did not promptly connect to anss from MakeEtcHosts");
            }
          }
        }
      }
      UC.setConnection(DBConnectionThread.getConnection("anssHosts"));
    } catch (InstantiationException e) {
      prta("Instantiation getting (impossible) anssro e=" + e.getMessage());
    }
    dbconnanss = DBConnectionThread.getThread("anssMakeEtc");
    if (dbconnanss == null) {
      try {
        prt("MakeEtcHost: connect to " + Util.getProperty("DBServer"));
        dbconnanss = new DBConnectionThread(Util.getProperty("DBServer"), "update", "anss",
                false, false, "anssMakeEtc", EdgeThread.getStaticPrintStream());
        EdgeThread.addStaticLog(dbconnanss);
        if (!DBConnectionThread.waitForConnection("anssMakeEtc")) {
          if (!DBConnectionThread.waitForConnection("anssMakeEtc")) {
            if (!DBConnectionThread.waitForConnection("anssMakeEtc")) {
              prt("Did not promptly connect to anssMakeEtc from MakeEtcHosts");
            }
          }
        }
        prt("MakeEtcHost: connect to " + Util.getProperty("DBServer") + dbconnanss);
      } catch (InstantiationException e) {
        prta("Instantiation getting (impossible) anssro e=" + e.getMessage());
        dbconnanss = DBConnectionThread.getThread("anssMakeEtc");
      }
    }

    start();
  }

  @Override
  public void run() {
    String node = Util.getSystemName();
    int pnt;
    int nq330;
    // This DBConnection must be made for TCPstation to be initialized.  Without it there is only mysery
    prta("EtcHosts: wait for anss connection");
    while (DBConnectionThread.getThread("anss") == null) {
      try {
        sleep(1000);
      } catch (InterruptedException expected) {
      }
    }
    prta("EtcHosts: done wait for anss connection node="+node);
    try {
      int iwait = 1;
      while (!terminate) {
        if (node.length() >= 4) {
          if (!node.substring(0, 4).equals("edge") && !node.substring(0, 4).equals("gaux")
                  && !node.substring(0, 3).equals("cwb") && !node.substring(0, 4).equals("gcwb")
                  && !node.substring(0, 4).equals("dcwb") && !node.substring(0, 4).equals("dedg")
                  && !node.substring(0, 4).equals("cedg") && !node.substring(0, 5).equals("acqdb")) {
            prt("EtcHosts: **** NOT A APPROPRIATE NODE " + node);
            break;
          }     // not an edge node, do not run!
        }
        try {
          sleep(iwait);
        } catch (InterruptedException expected) {
        }
        iwait = 300000;
        Util.clear(sb);
        if (Util.getOS().equals("Linux")) {
          sb.append("# Do not remove the following line, or various programs\n"
                  + "# that require network functionality will fail.\n");
          sb.append("127.0.0.1         ").append(node).append(".cr.usgs.gov ").append(" ").append(node).append(" ").
                  append(node).append(" localhost localhost.localdomain localhost4 localhost4.localdomain4\n");
          sb.append("::1        localhost localhost.localdomain localhost 6 localhost6.localdomain6\n");
        }
        sb.append("#\n# These come from anss.cpulinkip.  Tsunaminet addresses are 192.168.30.n for each gacqN node.\n#\n");
        int cpucount = 0;
        int rtscount = 0;
        try {
          try (ResultSet rs = dbconnanss.executeQuery("SELECT * FROM anss.cpulinkip order by cpulinkip")) {
            if (rs == null) {
              continue;
            }
            while (rs.next()) {
              String cpu = rs.getString("cpulinkip");
              cpucount++;
              pnt = cpu.indexOf("-Public");
              if (pnt > 0) {
                cpu = cpu.substring(0, pnt);
                if (!rs.getString("ipadr").equals("000.000.000.000")) {
                  sb.append((Route.clean(rs.getString("ipadr")) + "               ").substring(0, 18)).
                          append((cpu + "                    ").substring(0, 18)).append(" ").
                          append(cpu).append(".cr.usgs.gov\n");
                }
              }
            }
          }
          sb.append("#\n#RTS stations and any Q330s at them from TCPStation\n#\n");
          ResultSet rs2 = dbconnanss.executeQuery("SELECT * FROM anss.tcpstation ORDER BY tcpstation");
          if (rs2.next()) {
            //HACK:  for some reason, still unfathomed, the first call to new TCPStations causes
            // the ResultSet to be closed.  By doing this first one and then rebuiling the resultset
            // it always works

            TCPStation t = new TCPStation(rs2);
            rs2 = dbconnanss.executeQuery("SELECT * FROM anss.tcpstation ORDER BY tcpstation");
            //prt("Look at tcpstations "+dbconnanss);
            if (rs2 == null) {
              continue;
            }
            while (rs2.next()) {
              //prt("Process TCPStations "+rs2.getString("tcpstation"));
              rtscount++;
              int ipint = Route.intFromIP(rs2.getString("ipadr"));
              if (rs2.getInt("allowpoc") == 0) {
                ipint--;       // router sites do not have different gateways
              }
              sb.append((Route.clean(Route.stringFromInt(ipint)) + "                  ").substring(0, 18));
              sb.append(rs2.getString("tcpstation").toLowerCase()).append("-gw\n");
              sb.append((Route.clean(rs2.getString("ipadr")) + "                   ").substring(0, 18));
              sb.append(rs2.getString("tcpstation").toLowerCase()).append("\n");
              t = new TCPStation(rs2);
              String[] tags = t.getTags();
              if (tags != null) {
                nq330 = t.getNQ330s();
                InetAddress[] ips = t.getQ330InetAddresses();
                for (int i = 0; i < nq330; i++) {
                  sb.append((Route.clean(ips[i].getHostAddress()) + "                    ").substring(0, 18)).
                          append(tags[i].toLowerCase()).append("-q330\n");
                }
              }
            }
            rs2.close();
            sb.append("#\n#Reftek stations in anss.reftek\n#\n");
            rs2 = dbconnanss.executeQuery("SELECT station,ipadr FROM anss.reftek WHERE ipadr!='' ORDER BY station");
            while (rs2.next()) {
              int ipint = Route.intFromIP(rs2.getString("ipadr"));
              sb.append((Route.clean(Route.stringFromInt(ipint)) + "               ").substring(0, 18)).
                      append(rs2.getString("station").toLowerCase()).append("-rt\n");
            }
            rs2.close();
          }
          sb.append("#\nGSN stations on ISI, CTBTO, netserv or comserv\n#\n");
          rs2 = dbconnanss.executeQuery("SELECT * FROM edge.gsnstation ORDER BY gsnstation");
          while (rs2.next()) {
            if (!rs2.getString("longhaulprotocol").equals("LISS") && !rs2.getString("longhaulprotocol").equals("NONE")
                    && rs2.getString("longhaulip").length() > 8) {
              sb.append((Route.clean(rs2.getString("longhaulip")) + "                        ").substring(0, 18));
              sb.append(rs2.getString("gsnstation").toLowerCase().substring(2).trim()).append("-st\n");
            }
          }
          rs2.close();
        } catch (SQLException | RuntimeException e) {
          prta("SQL or Runtime error getting cpulinkip or other anss table.  reopen and try again e=" + e);
          e.printStackTrace(getPrintStream());
          dbconnanss.reopen();
          try {
            sleep(120000);
          } catch (InterruptedException expected) {
          }
          continue;           // do not replace file
        }

        sb.append("#\n# Make up the edge local network addresses\n#\n");
        for (int i = 1; i < 20; i++) {
          String nd = "edge" + i;
          sb.append("172.24.24.").append(i).append("       ").append(nd);
          if (nd.equals(node)) {
            sb.append("  loghost");
          }
          sb.append("     ").append(nd).append(".cr.usgs.gov\n");
        }
        for (int i = 1; i < 20; i++) {
          String nd = "gaux" + i;
          sb.append("172.24.25.").append(i + 40).append("       ").append(nd);
          sb.append("     ").append(nd).append(".cr.usgs.gov\n");
        }
        sb.append("#\n#make up he Hydra trace wire, IMPORT and CBTBO network addresses\n#\n");
        for (int i = 1; i < 20; i++) {
          String nd = "edge" + i + "-hydra";
          sb.append("192.168.18.").append(i).append("      ").append(nd);
          if (nd.equals(node)) {
            sb.append("  loghost");
          }
          sb.append("     ").append(nd).append(".cr.usgs.gov\n");
          nd = "edge" + i + "-import";
          sb.append("192.168.8.").append(140 + i).append("      ").append(nd);
          sb.append("    ").append(nd).append(".cr.usgs.gov\n");
          nd = "edge" + i + "-cbtbo";
          sb.append("10.86.2.").append(i + 10).append("        ").append(nd);
          sb.append("     ").append(nd).append(".cr.usgs.gov\n");
          //nd = "edge"+i+"-tsunami";
          //sb.append("192.168.30.").append(i + 10).append("        ").append(nd);
          //sb.append("     ").append(nd).append(".cr.usgs.gov\n");
        }

        // If the home directory contains an etchosts.local add it to the end
        String[] roles = Util.getRoles(node);
        for (int i = 0; i < roles.length + 1; i++) {
          try {
            try (BufferedReader in = new BufferedReader(new FileReader("ROOT" + Util.fs + "etchosts.local_" + (i >= roles.length ? node : roles[i])))) {
              String s;
              while ((s = in.readLine()) != null) {
                sb.append(s).append("\n");
              }
            }
          } catch (FileNotFoundException expected) {
          } catch (IOException e) {
            prt("IOException trying to add etchosts.local_" + (i >= roles.length ? node : roles[i]) + "=" + (e == null ? "null" : e.getMessage()));
          }
        }

        if (rtscount < 10 || cpucount < 10) {
          prt("Cpucount=" + cpucount + " #rts=" + rtscount + " " + sb.toString());
        }
        if (!sb.toString().equals(last)) {
          //prta("\n"+sb.toString());
          last = sb.toString();
          try {
            try (PrintStream lpt = new PrintStream(
                    new BufferedOutputStream(
                            new FileOutputStream("hosts.new")))) {
              lpt.println("# created by MakeEtcHosts.jar on " + Util.ascdate() + " " + Util.asctime());
              lpt.println("#Start with local host and mail server");
              lpt.print(last);
            }
            Subprocess sb2 = new Subprocess("diff " + Util.fs + "etc" + Util.fs + "hosts hosts.new");
            try {
              sb2.waitFor();
              prta("/etc/hosts to /etc/hosts.new Diff :\n" + sb2.getOutput() + sb2.getErrorOutput());
              sb2 = new Subprocess("sudo mv hosts.new " + Util.fs + "etc" + Util.fs + "hosts");
              sb2.waitFor();
              prta(Util.ascdate() + " mv /etc/hosts : " + sb2.getOutput() + sb2.getErrorOutput());
            } catch (InterruptedException e) {
              prta("InterruptException on diff and mv");
            }

          } catch (FileNotFoundException e) {
            prta("FileNotFOund exception - am I not running as root? e=" + e.getMessage());
            e.printStackTrace(getPrintStream());
          } catch (IOException e) {
            prta("IOException writing /etc/hosts file e=" + e.getMessage());
          }
        } //else prta("/etc/hosts not changed");
      }
    } catch (RuntimeException e) {
      prt("Runtime exception caught! exiting e=" + e);
      e.printStackTrace(getPrintStream());
      //System.exit(11);
    }
    prta("MakeEtcHosts thread has exited.  Is this not an edge node?=" + node);
    dbconnanss.terminate();
    Util.showShutdownThreads("MakeEtcHosts");
  }

  private String formIP(String ip) {
    String[] s = ip.split("\\.");
    String ret = "";
    if (s.length != 4) {
      return ip;
    }
    for (int i = 0; i < 4; i++) {
      if (s[i].length() == 1) {
        s[i] = "00" + s[i];
      } else if (s[i].length() == 2) {
        s[i] = "0" + s[i];
      }
      ret += s[i];
      if (i != 3) {
        ret += ".";
      }
    }
    return ret;

  }

  public static void main(String[] args) {
    Util.init("edge.prop");
    Util.setNoInteractive(true);
    MakeEtcHosts h = new MakeEtcHosts("-empty >>etchosts", "ETC");

  }
}
