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
import gov.usgs.anss.edgemom.Version;
import gov.usgs.anss.edgethread.EdgeThread;
import gov.usgs.anss.net.Route;
import gov.usgs.anss.util.SimpleSMTPThread;
import gov.usgs.anss.util.Subprocess;
import gov.usgs.anss.util.Util;
import java.io.IOException;
import java.io.BufferedOutputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.StringTokenizer;

/**
 * MakeVsatRoutes.java - This reads the MySQL tcpconnections records, compares
 * its IP and link information against the output of a "netstat -nr" command and
 * makes routing table changes based on the results.
 *
 * Modified to do the changes automatically rather than just creating a command
 * file
 * <PRE>
 * MakeVsatRoutes [-m][-dbg][-nohosts][-noroutes][-watchmysql]
 * -m   Make a vsatroutes command file (suitable for startup)
 * -dbg Run but do not actually change the routing table.
 * -nohosts Do not create and update hosts table
 * -noroutes Do not create a MakeVsatRoutes thread
 * -watchmysql Do create a WatchMySQL thread (obsolete)
 * -noedgeconfig Do not run an edgeconfig thread (autoswitch over, reIP software).
 * -dbconn host.adr Run against configuration in this MySQL rather than the one in Edge.prop
 * -dbconn host.adr Run against configuration in this MySQL rather than the one in Edge.prop
 * </PRE>
 *
 * @author davidketchum
 */
public final class MakeVsatRoutes extends EdgeThread {

  private final ArrayList<Route> routes = new ArrayList(100);
  private final ArrayList<Route> newroutes = new ArrayList(100);
  private final boolean makeFile;
  private final boolean debugOnly;
  private final StringBuilder rawRoutingTable = new StringBuilder(1000);
  private DBConnectionThread dbconn;
  static MakeVsatRoutes thisThread;

  @Override
  public void terminate() {
    prt("MakeVsatRoutes.terminate()");
    if (thisThread != null) {
      thisThread.interrupt();
    }
    terminate = true;
  }

  /**
   * return the monitor string for ICINGA
   *
   * @return A String representing the monitor key value pairs for this
   * EdgeThread
   */
  @Override
  public StringBuilder getMonitorString() {
    Util.clear(monitorsb);
    return monitorsb;
  }

  @Override
  public StringBuilder getStatusString() {
    return statussb;
  }

  @Override
  public StringBuilder getConsoleOutput() {
    return consolesb;
  }

  public static void terminatePanic() {
    if (thisThread != null) {
      thisThread.terminate();
    }
  }

  /**
   * Creates a new instance of MakeVsatRoutes
   *
   * @param argline args
   * @param tag logging tag
   * @param mf If true, makefile is on
   * @param dbg if true, debugOnly is on
   */
  public MakeVsatRoutes(String argline, String tag, boolean mf, boolean dbg) {
    super(argline, tag);
    makeFile = mf;
    debugOnly = dbg;
    Runtime.getRuntime().addShutdownHook(new ShutdownMakeRoutes());
    running = true;
    start();
  }

  @Override
  public void run() {
    thisThread = this;
    String output = "";
    String err = "";
    int lastemail = 0;
    boolean done = false;
    String node = Util.getNode();
    prt(Util.ascdate() + " " + Util.asctime() + " start up " + Version.version);
    long nsleep = 1;
    int loop = 0;
    boolean linux = Util.getOS().equals("Linux");
    dbconn = DBConnectionThread.getThread("anss");
    if (dbconn == null) {
      try {
        dbconn = new DBConnectionThread(Util.getProperty("DBServer"), "update", "anss", false, false, "anss", EdgeThread.getStaticPrintStream());
        EdgeThread.addStaticLog(dbconn);
        if (!DBConnectionThread.waitForConnection("anss")) {
          if (!DBConnectionThread.waitForConnection("anss")) {
            prta("Did not promptly connect to anss database!");
          }
        }
      } catch (InstantiationException e) {
        prta("InstantiationException: should be impossible");
        dbconn = DBConnectionThread.getThread("anss");
      }
    }
    StringBuilder sb = new StringBuilder(1000);
    while (!done && !terminate) {
      try {
        // Use the OS to sleep for 60 seconds and do it all again
        try {
          sleep(nsleep);
        } catch (InterruptedException expected) {
        }
        //Util.loadProperties("edge.prop");
        try {
          sleep(nsleep);
        } catch (InterruptedException expected) {
        }
        if (System.currentTimeMillis() % 86400000 < 30000) {
          prta(Util.ascdate() + " Day change ");
          dbconn.setLogPrintStream(EdgeThread.getStaticPrintStream());
        }
        //prta("MakeVsatRoutes start pass");
        nsleep = 30000L;
        Util.clear(sb);

        // as a side effect, create a bash command file to build or delete routes
        String cmd = null;
        boolean changed = (loop == 0);    // first time true, else false
        String gateway;
        String ip;
        sb.append("#bin/bash\ntype=$1\ntype=${type='add'}\n");

        // get and check the existing routing table, email if inconsistent
        getRoutingTable((loop % 120) == 0);
        if (isConsistent(routes).length() > 0) {
          prta(Util.ascdate() + " Routing table is inconsistent : \n" + isConsistent(routes));
          if (lastemail % 120 == 0) {
            SendEvent.edgeSMEEvent("MVSATRtErr", "Routes on " + node + " are inconsistent!", this);
            SimpleSMTPThread tmp = new SimpleSMTPThread("", Util.getProperty("emailTo"), "makevsathosts@" + node + ".cr.usgs.gov",
                    "Routes on " + node + " are inconsistent!",
                    "The routes on node " + node + " are inconsistent in some way :\n"
                    + isConsistent(routes) + "\nRaw Routing table:\n" + rawRoutingTable + "\n");
          }
          lastemail++;
          continue;
        }

        // get the routes needed by tcpstation, check for consistency and e-mail if not
        // The new routes are all of the expected routes for tcpip stations
        getNewRoutes((++loop % 120) == 0);         // Populate the newroutes based on MySQL tcpstation
        // if new routes are not consistent send e-mail but do not apply them!
        if (isConsistent(newroutes).length() > 0) {
          prt(isConsistent(newroutes).toString());
          if (lastemail % 120 == 0) {
            prta(Util.ascdate() + " Proposed route is inconsistent2 : \n" + isConsistent(routes));
            SimpleSMTPThread tmp = new SimpleSMTPThread("", Util.getProperty("emailTo"), "makevsathosts@" + Util.getNode() + ".cr.usgs.gov",
                    "Proposed Vsat Routes to " + Util.getNode() + " are inconsistent2!",
                    "The routes derived from anss.tcpstation are inconsistent in some way :\n"
                    + isConsistent(newroutes) + "\nRaw Routing table:\n" + rawRoutingTable + "\n");
          }
          lastemail++;
          continue;
        }
        prta("newroutes.size()=" + newroutes.size() + " routes.size()=" + routes.size() + " loop=" + loop);
        // For each tcpstation derived route, check to see if the routing table needs to be
        // modified (base on the gateway or mask changing)
        for (Object newroute : newroutes) {
          Route r = (Route) newroute;
          ip = r.getDestination();
          gateway = r.getGateway();
          if(Util.isNEICHost(r.getDestination())) {
            continue;     // do not redo routes in USGS domain!
          }          // Based on link, decide which of the two backhauls should be routed+
          if (r.getGateway() == null) {
            continue;         // no gateway found
          }          //if(gateway.equals("")) continue;      // no gateway found
          // no leading zeros to confuse route to think we are speaking octal
          if (ip.startsWith("0")) {
            ip = ip.substring(1);
          }
          if (ip.startsWith("0")) {
            ip = ip.substring(1);
          }
          ip = ip.replaceAll("\\.0", ".");
          ip = ip.replaceAll("\\.0", ".");
          ip = ip.replaceAll("\\.\\.", ".0.");
          // at vdldfc (using the DFC dbconn server) always use fed ctr gateway
          // If by accident we get a local USGS address, do not process it!
          if (ip.length() < 7) {
            prta("** Route ip is < 7 ip=" + ip + "|" + r);
            SendEvent.edgeSMEEvent("MakeVBadRte", "IP=" + ip + " is a bad addr in MakeVsatRoutes", this);
            continue;
          }
          if (Util.isNEICHost(ip)) {
            gateway = "";
            prt("Local route override! " + ip);
          }
          // Get current routing for this VSAT
          Route oldroute = getRouteForIP(ip, r.getMask());
          String oldGate = "";
          int oldnbits = -1;
          if (oldroute != null) {
            oldGate = oldroute.getGateway();
            oldnbits = oldroute.getMaskNbits();
          }
          //prt("For IP="+ip+" route gateway="+gateway+" gatewayForIP="+oldGate);

          // If the gateways agree do nothing
          if (gateway.equals(oldGate) && r.getMaskNbits() == oldnbits) {
            //prt("Its o.K. leave alone");
          } // If this was not routed before, create a new route
          else if (oldGate.equals("")) {
            if (linux) {
              if (r.getMaskNbits() <= 31) {
                cmd = "sudo route add -net " + r.getNetworkAddress() + "/" + r.getMaskNbits() + " gw " + gateway;
              } else {
                cmd = "sudo route add -host " + ip + " gw " + gateway;
              }
            } else {
              cmd = "sudo route add " + ip + "/" + r.getMaskNbits() + " " + gateway;
            }
            try {
              if (!debugOnly && !gateway.equals("")) {
                Subprocess sub = new Subprocess(cmd);
                sub.waitFor();
                changed = true;
                prta(Util.ascdate() + " " + r.getTag() + " '" + cmd + "'"
                        + " err1=" + sub.getErrorOutput() + " out(oldgate='')=" + sub.getOutput());
              } else if (debugOnly) {
                prt(r.getTag() + " Debug: cmd would have been : " + cmd);
              }
            } catch (IOException e) {
              prta("IOExecption adding adding a route:" + cmd);
            } catch (InterruptedException e) {
              prta("Interrupted Exception adding a route:" + cmd);
            }
          } // The gateway has been changed, delete the old route and add new one
          else {       // It is not o.k. and old one does exist
            try {
              if (!debugOnly && oldroute != null) {      // if we have an old route, delete it!
                if (linux) {
                  if (r.getMaskNbits() <= 31) {
                    cmd = "sudo route del -net " + oldroute.getNetworkAddress() + "/" + oldroute.getMaskNbits() + " gw " + oldGate;
                  } else {
                    cmd = "sudo route del -host " + oldroute.getDestination() + " gw " + oldGate;
                  }
                } else {
                  cmd = "sudo route delete " + oldroute.getDestination() + "/" + oldroute.getMaskNbits() + " " + oldGate;
                }
                if (debugOnly) {
                  prt(r.getTag() + " Debug: cmd would have been :" + cmd);
                }
                {
                  Subprocess sub = new Subprocess(cmd);
                  sub.waitFor();
                  changed = true;
                  prta(Util.ascdate() + " " + r.getTag() + " '" + cmd + "'"
                          + " err2=" + sub.getErrorOutput() + " out(del)=" + sub.getOutput());
                }
              }

              // if we have a gateway, we need to add in the route
              if (!debugOnly && !gateway.equals("")) {
                if (linux) {
                  if (r.getMaskNbits() < 31) {
                    cmd = "route add -net " + r.getNetworkAddress() + "/" + r.getMaskNbits() + " gw " + gateway;
                  } else {
                    cmd = "sudo route add -host " + ip + " gw " + gateway;
                  }
                } else {
                  cmd = "sudo route add " + ip + "/" + r.getMaskNbits() + " " + gateway;
                }
                if (debugOnly) {
                  prt(r.getTag() + " Debug: cmd would have been : " + cmd);
                }
                Subprocess sub = new Subprocess(cmd);
                sub.waitFor();
                changed = true;
                prta(Util.ascdate() + " " + r.getTag() + " '" + cmd + "'"
                        + " err3=" + sub.getErrorOutput() + " out(add gate chg)=" + sub.getOutput());
              }
            } catch (IOException e) {
              prta("IOException adding adding a route:" + cmd);
            } catch (InterruptedException e) {
              prta("Interrupted Exception adding a route:" + cmd);
            }
          }
          //prt("route ${1} "+ip+"/29 "+gateway+" # "+rs.getString("tcpstation"));
          if (!gateway.equals("")) {
            if (linux) {
              if (r.getMaskNbits() < 31) {
                sb.append("route ${1} -net ").append(r.getNetworkAddress()).
                        append("/").append(r.getMaskNbits()).append(" gw ").append(gateway).
                        append(" # ").append(r.getTag()).append("\n");
              } else {
                sb.append("route ${1} -host ").append(ip).append(" gw ").append(gateway).
                        append(" # ").append(r.getTag()).append("\n");
              }
            } else {
              sb.append("route ${1} ").append(ip).append("/").append(r.getMaskNbits()).
                      append(" ").append(gateway).append(" # ").append(r.getTag()).append("\n");
            }
          }
        } // for each newroute

        if (makeFile || changed) {
          try (PrintStream lpt = new PrintStream(
                  new BufferedOutputStream(
                          new FileOutputStream("vsatroutes")))) {
            lpt.println(sb.toString());
          }
        }

      } catch (FileNotFoundException e) {
        prta("File not found");
        System.exit(0);
      }
      if (makeFile || debugOnly) {
        break;
      }
    }   // while(!done);
    running = false;
    prt("MakeVsatRoutes - run loop exited done=" + done + " terminate=" + terminate
            + " makefile=" + makeFile + " dbgonly=" + debugOnly);
    dbconn.terminate();
    System.exit(8);
  }
  StringBuilder sbi = new StringBuilder(10000);

  private StringBuilder isConsistent(ArrayList routes) {
    Util.clear(sbi);
    for (int i = 0; i < routes.size() - 1; i++) {
      for (int j = i + 1; j < routes.size(); j++) {
        Route r1 = (Route) routes.get(i);
        Route r2 = (Route) routes.get(j);
        if (!r1.isCompatible(r2)) {
          sbi.append("#Incompatible ").append(r1).append(" to ").append(r2).append("\n");
        }
      }
    }
    if (sbi.length() > 0) {
      sbi.append("Decoded routing table:\n");
      for (Object route : routes) {
        sbi.append(route).append("\n");
      }
    }
    return sbi;
  }

  /**
   * build up a list of routes from the Solaris routing table
   */
  private synchronized void getRoutingTable(boolean dbg) {
    // Make routes a list of the current routing table
    String output = "";
    String output2 = "dsf";
    String err = "";
    Util.clear(rawRoutingTable);
    
    try {
      routes.clear();
      // Do a netstat -nr and get output
      while (!output.equals(output2)) {
        Subprocess sub = new Subprocess("netstat -nrv");
        sub.waitFor();
        output = sub.getOutput();
        err = sub.getErrorOutput();
        sub = new Subprocess("netstat -nrv");
        sub.waitFor();
        output2 = sub.getOutput();
        if (!output.equals(output2)) {
          prta("  ****** Routing table gets do not agree\n" + output + "\n" + output2);
        }
      }
      rawRoutingTable.append(output);
      //prt("err="+err);
      //prt("out="+output);

    } catch (IOException e) {
      prt("IO exception getting output of netstat command");
      System.exit(1);

    } catch (InterruptedException e) {
      prt("Interrupted Exception");
    }

    // Break the netstat -nvr into lines of output and create a new route from each one
    StringTokenizer tk = new StringTokenizer(output, "\n");
    while (tk.hasMoreTokens()) {
      String s = tk.nextToken().replaceAll("    ", " ");
      s = s.replaceAll("  ", " ");
      //prt("Routing = "+s);

      // If this line starts with a number, then it is probably a real route
      if (s.length() > 0) {
        if (s.charAt(0) >= '0' && s.charAt(0) <= '9') {
          Route r = new Route("RT", s);
          //prt("rt="+r.toString());
          if (dbg) {
            prt("dest=" + r.getDestination()
                    + " gateway=" + r.getGateway() + " mask=" + r.getMask() + "/" + r.getMaskNbits() + " isOK=" + r.isOK() + " s=" + s);
          }
          if (r.isOK()) {
            routes.add(r);
          }
        }
      }
    }
  }

  /**
   * build up a list of routes from the tcpstation table
   */
  private void getNewRoutes(boolean dbg) {
    // Get current information from the tcpstation table for VSATs
    newroutes.clear();
    String[] links = new String[100];
    try {
      String s = "SELECT * FROM anss.commlink";
      ResultSet rs = dbconn.executeQuery(s);
      if (rs == null) {
        return;
      }
      while (rs.next()) {
        String gateway = rs.getString("gateway");
        if (!gateway.equals("")) {
          while (gateway.charAt(0) == '0') {
            gateway = gateway.substring(1);
          }
          gateway = gateway.replaceAll("\\.0", ".");
          gateway = gateway.replaceAll("\\.0", ".");
        }
        links[rs.getInt("ID")] = gateway;
      }
      //s = "SELECT * FROM tcpstation where commlinkid=1 or commlinkid=2 or commlinkid=6  order by tcpstation";
      // The "commlinkID" represents the "last mile" to the site, we need to do the routing based
      // on how we want the data from here to go out.
      //s = "SELECT * FROM tcpstation where commlinkoverrideid=1 or commlinkoverrideid = 2 or commlinkoverrideid=6  or commlinkoverrideid=4 order by tcpstation";
      s = "SELECT tcpstation,ipadr,ipmask,allowpoc,commlinkid,commlinkoverrideid,gateway,usebh "
              + "FROM anss.tcpstation,anss.commlink where commlink.id=commlinkoverrideid and "
              + "(commlinkoverrideid=4 or gateway !='') order by tcpstation";   // its public routed or gateway given
      rs = dbconn.executeQuery(s);
      if (rs == null) {
        return;        // connection is down, do nothing
      }
      String cmd = "";
      while (rs.next()) {

        // Get the link and override information to find link currently desired
        int link = rs.getInt("commlinkID");
        int override = rs.getInt("commlinkOverrideID");
        if (override > 0) {
          link = override;
        }
        boolean useBH = (rs.getInt("usebh") != 0);
        if (!(link == 1 || link == 2 || link == 6 || link == 4 || link == 12 || link == 13 || link == 14)) {
          prt("** GetNewRoutes: Unexpected gateway=" + links[link] + " link=" + link + " for " + rs.getString("tcpstation") + " " + rs.getString("ipadr"));
        }
        String ip = rs.getString("ipadr");

        // Get the "mask" and see how many bits long it is and if its a valid mask
        String ipmask = rs.getString("ipmask");
        int mask = Route.intFromIP(ipmask);
        //qprt("New Route ="+rs.getString("tcpstation")+" ip="+ip+" msk="+ipmask+" links="+links[link]+" link="+link);
        // We want the default rout if the link has no gateway (length=0) or if its the NLV or GTN BH and not use backhaul
        Route r = new Route(rs.getString("tcpstation"), ip, ipmask,
                ((links[link].length() == 0 || ((link == 12 || link == 13) && !useBH)) ? "000.000.000.000" : links[link]));
        if (dbg) {
          prt("getNewRoutes:" + rs.getString("tcpstation") + " " + ip + " " + r.toString());
        }
        if (rs.getInt("allowpoc") != 0 && r.getMaskNbits() < 29) {
          //prt("** GetNewRoutes: POC site is not < mask 29 not routed "+r.toString());
          continue;
        }
        newroutes.add(r);
        //prt("mask="+Util.toHex(intFromIP(ipmask))+" is "+nmaskbits+" of bit mask! remain="+Util.toHex(mask));
      }
    } catch (SQLException e) {
      prta("SQLException getting links or tcpstation data=" + e.getMessage());
      try {
        sleep((long) 60000);
      } catch (InterruptedException expected) {
      }
    }
  }

  /**
   * Given an IP for a VSAT, decide return its route in the current routing
   * table
   *
   * @param ipadr An IP address properly striped of leading zeros of a VSAT
   * @param ipmask A mask to decide if its in that domain
   * @return The gateway through which this 8 IP address space is routed
   * currently
   */
  public Route getRouteForIP(String ipadr, String ipmask) {
    boolean dbg = false;
    int[] b;

    int ip = Route.intFromIP(ipadr);
    int mask = Route.intFromIP(ipmask);
    int ib;

    if (dbg) {
      prt("GetGateway =" + Util.toHex(ip) + " " + ipadr/*+"| ipb="+ipb[0]+" "+ipb[1]+" "+ipb[2]+" "+ipb[3]*/);
    }
    if (dbg) {
      prt("GetGateway =" + Util.toHex(mask) + " " + ipmask/*+"| maskb="+ipb[0]+" "+ipb[1]+" "+ipb[2]+" "+ipb[3]*/);
    }
    boolean match = false;          // assome no match is found
    for (int i = 0; i < routes.size(); i++) {
      b = ((Route) routes.get(i)).getDestinationBytes();
      ib = b[0] << 24 | b[1] << 16 | b[2] << 8 | b[3];
      if (dbg) {
        prt(i + " mask=" + Util.toHex(mask) + " ip=" + Util.toHex(ip) + " dest=" + Util.toHex(ib) + " gate=" + Util.toHex(ib));
      }
      if ((ip & mask) == (ib & mask)) {
        if (dbg) {
          prt("Match found!");
        }
        return (Route) routes.get(i);
      }
    }

    return null;
  }

  private final class ShutdownMakeRoutes extends Thread {

    public ShutdownMakeRoutes() {

    }

    /**
     * this is called by the Runtime.shutdown during the shutdown sequence cause
     * all cleanup actions to occur
     */
    @Override
    public void run() {
      SimpleSMTPThread.email(Util.getProperty("emailTo"), "SHUTDOWN:MakeVsatRoutes on " + Util.getNode() + " is Shuttingdown",
              "This is called whenever this Jar ShutsDown()");
      prta("ShutdownMakeVsatRoutes started");
      terminate = true;
    }
  }

  /**
   * This main displays the form Pane by itself
   *
   * @param args command line args
   */
  public static void main(String args[]) {
    // Connect to the ANSS database on gacqdb
    EdgeProperties.init();
    Util.setProcess("MakeVsatRoutes");
    Util.setModeGMT();
    EdgeThread.setMainLogname("makevsatroutes");   // This sets the default log name in edge thread (def=edgemom!)
    Util.setNoconsole(false);      // Mark no dialog boxes
    Util.setNoInteractive(true);
    boolean makefile = false;
    boolean dbg = false;
    boolean nohosts = false;
    boolean noroutes = false;
    boolean noedgeconfig = false;
    boolean watchMysql = false;
    Util.prta(Util.ascdate() + " Startup : get os2 =" + Util.getOS() + " db=" + Util.getProperty("DBServer")
            + " Host=" + Util.getNode() + "/" + Util.getProperty("Node") + " args.length=" + args.length);
    String argline = "";
    for (int i = 0; i < args.length; i++) {
      argline += args[i] + " ";
      //Util.prt("args["+i+"]="+args[i]);
      if (args[i].equals("-m")) {
        makefile = true;
      }
      if (args[i].equals("-mysql") || args[i].equals("-db")) {
        Util.prta(" **** override db for config to " + args[i + 1]);
        Util.setProperty("DBServer", args[i + 1]);
        i++;
      }
      if (args[i].equals("-dbg")) {
        dbg = true;
      }
      if (args[i].equals("-nohosts")) {
        nohosts = true;
      }
      if (args[i].equals("-noroutes")) {
        noroutes = true;
      }
      if (args[i].equals("-watchmysql")) {
        watchMysql = true;
      }
      switch (args[i]) {
        case "-noedgeconfig":
          noedgeconfig = true;
          break;
        case "-i":
        case "-instance":
          if (!args[i + 1].equals("^") && !args[i + 1].equals("null")) {
            EdgeThread.setInstance(args[i + 1]);
          } else {
            EdgeThread.setInstance(null);
          }
          break;
      }
      if (args[i].equals("-?")) {
        Util.prt("MakeVsatRoutes [-m][-dbg][-nohosts][-noroutes][-watchmysql]");
        Util.prt("  -m   Make a vsatroutes command file (suitable for startup)");
        Util.prt("  -dbg Run but do not actually change the routing table.");
        Util.prt("  -nohosts Do not create and update hosts table");
        Util.prt("  -noroutes Do not create a MakeVsatRoutes thread");
        Util.prt("  -watchmysql Do create a WatchMySQL thread");
        Util.prt("  -noedgeconfig Do not run EdgeConfigThread to handle config");
      }
    }
    MakeEtcHosts makehosts = null;
    MakeVsatRoutes vsat = null;
    //WatchMySQL watcher = null;
    EdgemomConfigThread edgeconfig = null;
    try {
      if (!noroutes) {
        vsat = new MakeVsatRoutes(argline, "MVR", makefile, dbg);
      } else {
        Util.prt("Vsat routings is suppressed.");
      }
      Util.sleep(15000);
      if (!nohosts && !makefile) {
        makehosts = new MakeEtcHosts("-empty >>etchosts", "ETC");
      } else {
        Util.prt("Making /etc/inet/hosts is suppressed");
      }
      //if(watchMysql && !makefile) watcher = new WatchMySQL();
      if (!noedgeconfig) {
        edgeconfig = new EdgemomConfigThread("", "ECT");
        if (dbg) {
          EdgemomConfigThread.setDebug(true);
        }
      }
      int loop = 0;
      for (;;) {
        try {
          loop++;
          if (makefile) {
            break;
          }
          if (vsat != null) {
            if (!vsat.isAlive() || !vsat.isRunning()) {
              break;
            }
          }
          Thread.sleep(10000);
          if (loop % 60 == 0) {
            Util.prta(Util.ascdate() + " MakeVsatRoutes : status: for " + Util.getNode());
            Util.prt(DBConnectionThread.getStatus());

          }
        } catch (InterruptedException expected) {
        }
      }
    } catch (Exception e) {
      Util.prt("Main of MakeVsatRoutes caught exception e=" + e.getMessage());
      e.printStackTrace();
      System.exit(1);

    }
    Util.prta("MakeVSAT Routes is trying to shutdown");
    DBConnectionThread.shutdown();
    if (edgeconfig != null) {
      edgeconfig.terminate();
    }
    //if(watcher != null) watcher.terminate();
    if (makehosts != null) {
      makehosts.terminate();
    }
    Util.sleep(10000);
    Util.prt(Util.getThreadsString());
    System.exit(7);
  }
}
