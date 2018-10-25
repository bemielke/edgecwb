/*
 *  This software is in the public domain because it contains materials 
 *  that originally came from the United States Geological Survey, 
 *  an agency of the United States Department of Interior. For more 
 *  information, see the official USGS copyright policy at 
 *  http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.fetcher;

import gov.usgs.anss.db.DBConnectionThread;
import gov.usgs.anss.edgethread.EdgeThread;
import gov.usgs.anss.guidb.Cpu;
import gov.usgs.anss.guidb.DasConfig;
import gov.usgs.anss.guidb.TCPStation;
import gov.usgs.anss.util.Util;
import gov.usgs.edge.config.EdgeStation;
import gov.usgs.edge.config.RequestType;
import gov.usgs.edge.config.RoleInstance;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;

/**  This is an all static class that sets up the requeststation, and channel gaptypes for
 * Q330 stations, Reftek stations, and EdgeStations at the NEIC.  It is unlikely this code is
 * useful elsewhere as it contains many reference to host names at the NEIC.
 *
 * @author U.S. Geological Survey  <ketchum at usgs.gov>
 */
public class NEICFetchConfig {
  private static DBConnectionThread dbconn;   // To edge database as 'edge'
  private static DBConnectionThread anss;     // to anss database as 'anss'
  private static boolean dbg;                 // If true, then do lots of logging.

  /**
   * from and ANSS.jar database for TCPStations, nsnstations, cpu, etc. 
   * create the request station entries for all baler/Q330s that are live.
   *
   */
  public static void makeANSSBalerTables() {
    Statement stmtro;
    Statement stmt;
    try {
      //anss2 = DBConnectionThread.getThread("anss");   // An anss is needed for the TCPStation ENUM() lookup
      if(dbg) Util.prta("MBAL: makeANSSBalerTables() anss2=" +  DBConnectionThread.getThread("anss") 
              + " fetchserver list=" + DBConnectionThread.getThreadList());
      if ( DBConnectionThread.getThread("anss") == null) {
        Util.prta("MBAL:***** makeANSSBalerTables cannot run - no anss DB connection");
      }
      stmtro = DBConnectionThread.getConnection("anss").createStatement();
      stmt = DBConnectionThread.getConnection("edge").createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE);

      if(dbg) Util.prta("MBAL: makeANSSBalerTables() edge=" + dbconn);
      if (DBConnectionThread.getConnection("edge") == null) {
        Util.prta("MBAL: Waiting for edge Db connection");
        Util.sleep(5000);
      }
    } catch (SQLException e) {
      Util.prta("MBAL:***** makeANSSBalerTables() could not set up DB connections - abort run.");
      return;
    }
    ArrayList<Cpu> cpus = new ArrayList<>(10);
    String cpuIDs = "";
    try {
      try (ResultSet rs = stmtro.executeQuery("SELECT * FROM anss.cpu ORDER BY cpu")) {
        while (rs.next()) {
          Cpu cpu = new Cpu(rs);
          if(cpu.getCpu().startsWith("gacq")) {
            cpuIDs += cpu.getID()+",";
          }
          cpus.add(cpu);
        }
      }
    } catch (SQLException e) {
      Util.prt("MBAL:*** Did not find cpu table!!");
      Util.exit(1);
    }
    cpuIDs = cpuIDs.substring(0, cpuIDs.length() -1);   // remove trailing ,
    int balerTypeID;
    int balerType2ID;
    int balerType3ID;
    int balerType4ID;
    if(dbg) Util.prta("MBAL:makeANSSBalerTables() lookup requesttpe for baler");
    ResultSet rs2 = null;
    try {
      rs2 = stmt.executeQuery("SELECT ID FROM edge.requesttype WHERE requesttype='Baler'");
      if (rs2.next()) {
        balerTypeID = rs2.getInt("ID");
      } else {
        Util.prt("MBAL: ** Did not find baler type in requesttype table!");
        rs2.close();
        //if(stmt != null) stmt.close();
        return;
      }
      rs2.close();
      rs2 = stmtro.executeQuery("SELECT ID FROM edge.requesttype WHERE requesttype='Baler2'");
      if (rs2.next()) {
        balerType2ID = rs2.getInt("ID");
      } else {
        Util.prt("MBAL: ** Did not find baler2 type in requesttype table!");
        rs2.close();
        //if(stmt != null) stmt.close();
        return;
      }
      rs2.close();
      rs2 = stmtro.executeQuery("SELECT ID FROM edge.requesttype WHERE requesttype='Baler3'");
      if (rs2.next()) {
        balerType3ID = rs2.getInt("ID");
      } else {
        Util.prt("MBAL: ** Did not find baler3 type in requesttype table!");
        rs2.close();
        //if(stmt != null) stmt.close();
        return;
      }
      rs2.close();
      rs2 = stmtro.executeQuery("SELECT ID FROM edge.requesttype WHERE requesttype='Baler4'");
      if (rs2.next()) {
        balerType4ID = rs2.getInt("ID");
      } else {
        Util.prt("MBAL: ** Did not find baler4 type in requesttype table!");
        rs2.close();
        //if(stmt != null) stmt.close();
        return;
      }
      rs2.close();

    } catch (SQLException e) {
      Util.prt("MBAL: ** Did not find baler in request type table!!");
      try {
        if (rs2 != null) {
          rs2.close();
        }
        //if(stmt != null) stmt.close();
      } catch (SQLException e2) {
      }
      return;
    }
    if(dbg) Util.prta("MBAL:makeANSSBalerTables() dasconfig");

    try {
      ResultSet rs = stmtro.executeQuery("SELECT MAX(ID) from anss.dasconfig");
      rs.next();
      int maxid = rs.getInt(1) + 1;
      rs.close();
      rs = stmtro.executeQuery("SELECT * FROM anss.dasconfig");
      ArrayList<DasConfig> das = new ArrayList<DasConfig>(maxid);
      for (int i = 0; i < maxid; i++) {
        das.add(i, null);
      }
      while (rs.next()) {
        DasConfig d = new DasConfig(rs);
        das.add(rs.getInt("ID"), d);
      }
      rs.close();

      // get a list of all of he active Q730 and Q680s
      if(dbg) Util.prta("MBAL:makeANSSBalerTables() Q680/Q730");
      rs = stmtro.executeQuery(
              "SELECT nsnstation,seednetwork,dasconfigid,ipadr,quanterratype,seismometer,netid,nodeid,stationtype,lowgain,highgain "
              + "FROM anss.nsnstation,anss.tcpstation "
              + "WHERE tcpstation=nsnstation AND stationtype='nsn' AND (quanterratype='Q680' or quanterratype='Q730') "
              + "AND stationtype != 'Nsn1Sec' AND tcpstation.cpuid IN ("+cpuIDs+") ORDER BY nsnstation");
      // build list of NNSSSSS excluding any duplicates or % stations
      while (rs.next()) {
        String name = (rs.getString("seednetwork") + "  ").substring(0, 2) + (rs.getString("nsnstation") + "    ").substring(0, 5);
        if (!name.contains("%") && !name.contains(":")) {
          String[] chans = "BHZ,BHN,BHE,LHZ,LHN,LHE".split(",");
          for (String chan1 : chans) {
            String chan = (name + chan1 + "   ").substring(0, 12).trim();
            String s = "UPDATE edge.channel SET gaptype='GN' WHERE channel REGEXP '" + chan + "'"; //XN
            if(dbg) Util.prt("MBAL:" + s);
            int n = dbconn.executeUpdate(s);
          }
        }
      }
      rs.close();

      // Make a list of all of the ANSS style Q330s
      if(dbg) Util.prta("MBAL:makeANSSBalerTables() nsnstation Q330s");
      rs = stmtro.executeQuery(
              "SELECT nsnstation,seednetwork,dasconfigid FROM anss.nsnstation WHERE "
              + "quanterratype='Q330SR' or quanterratype='Q330HR' ORDER BY nsnstation");
      while (rs.next()) {
        String name = (rs.getString("seednetwork") + "  ").substring(0, 2) + (rs.getString("nsnstation") + "    ");
        DasConfig d = das.get(rs.getInt("dasconfigid"));
        if (!name.contains("%") && d != null) {
          String[] chans = d.getExpectedChannels().split(",");
          //EdgeThread.static.prt("MBAL:Add "+name+" "+d.getExpectedChannels()+" "+d.getFillType());
          if (name.contains(":")) {
            name = (name.substring(0, name.indexOf(":")) + "   ").substring(0, 7);
          } else {
            name = (name + "    ").substring(0, 7);
          }
          for (String chan : chans) {
            String s = "UPDATE edge.channel SET gaptype='" + d.getFillType().substring(0, 2) + "' WHERE channel REGEXP '"
                    + (name + chan + "    ").substring(0, 12).trim() +
                    "$' and NOT channel REGEXP '^.......OCF' AND NOT channel regexp '^.......AC[PE]'"
                    + " AND NOT channel REGEXP '^.......BC";
            int n = dbconn.executeUpdate(s);
          }
        }
      }
      rs.close();
    } catch (SQLException e) {
      Util.SQLErrorPrint(e, "makeANSSBalerTables() Building list of stations for gap types on Q330, and NSN");
      e.printStackTrace();
    }

    // Make a list of Q330 stations with this gap type
    if(dbg) Util.prta("MBAL:makeANSSBalerTables() Q330 station list");
    ArrayList<String> stations = new ArrayList<String>(100);
    try {
      try (ResultSet rs = stmtro.executeQuery(
              "SELECT left(seedname,7) from fetcher.fetchlist where type='GP'"
                      + " group by left(seedname,7) order by seedname,start")) {
        while (rs.next()) {
          stations.add(rs.getString(1).trim());
        }
      }
      if(dbg) Util.prt("MBAL:makeANSSBalerTables() # Q330 sites=" + stations.size());
    } catch (SQLException e) {
      Util.SQLErrorPrint(e, "MBAL:makeANSSBalerTables() ** SQLError building list of stations.  Abort...");
    }

    // For all of the Q330s, there might be more than one Q330 at each station, supplement the list
    int size = stations.size();
    if(dbg) Util.prta("MBAL:makeANSSBalerTables() Q330 station look for multiple das size=" + size);

    for (int i = 0; i < size; i++) {
      try {
        try (ResultSet rs = stmtro.executeQuery(
                "SELECT * FROM anss.tcpstation WHERE tcpstation='"
                        + stations.get(i).substring(2) + "'")) {
          if (rs.next()) {
            TCPStation tcp = new TCPStation(rs);
            int cpuID = tcp.getCPUID();
            Cpu cpu = null;
            for (Cpu cpu1 : cpus) {
              if (cpuID == cpu1.getID()) {
                cpu = cpu1;
                break;
              }
            }
            if (cpu == null) {
              continue;
            }
            for (int j = 0; j < tcp.getNQ330s(); j++) {
              String chanre;
              //if(chanre.indexOf("CBKS") >=0)
              //  Util.prt("MBAL:chanre="+chanre);
              if (j == 0) {
                if (stations.get(i).substring(0, 5).equals("IUSLB")) {
                  chanre = "IUSLBS ...00|IUSLBS ...91|IUSLBS ...20|IUSLBS LDO30|IUSLBS.BC0";
                } else if (stations.get(i).substring(0, 2).equals("IU")) {
                  chanre = (stations.get(i) + "        ").substring(0, 7) + "...00";
                } else if (stations.get(i).substring(0, 2).equals("CU")) {
                  chanre = (stations.get(i) + "        ").substring(0, 7) + "...";
                }
                else if (stations.get(i).substring(0, 2).equals("US")) {
                  chanre = (stations.get(i) + "        ").substring(0, 7) 
                          + "...$|^" + (stations.get(i) + "       ").substring(0, 7) + "...[029][01]";
                } else {
                  chanre = (stations.get(i) + "        ").substring(0, 7) + "...";
                }
              } else {  // 2nd Q330
                if (stations.get(i).substring(0, 5).equals("IUSLB")) {
                  chanre = "IUSLBS ...10|IUSLBS ...92|IUSLBS.BC1";
                } else if (stations.get(i).substring(0, 2).equals("IU")) {
                  chanre = (stations.get(i) + "      ").substring(0, 7) + "...10";
                } else if (stations.get(i).substring(0, 2).equals("CU")) {
                  chanre = (stations.get(i) + "        ").substring(0, 7) + "...";
                } else {
                  chanre = (stations.get(i) + "      ").substring(0, 7) 
                          + "...[H19][R02]|" + (stations.get(i) + "      ").substring(0, 7) + "LDO";
                }
              }
              if (cpu.getCpu().startsWith("gacq")) {  // Do not do NONE
                // Is the baler, baler2, baler3, baler4
                int rID = 0;
                if(cpu.getCpu().equalsIgnoreCase("gacq1")) rID = balerTypeID;
                if(cpu.getCpu().equalsIgnoreCase("gacq2")) rID = balerType2ID;
                if(cpu.getCpu().equalsIgnoreCase("gacq3")) rID = balerType3ID;
                if(cpu.getCpu().equalsIgnoreCase("gacq4")) rID = balerType4ID;
                rs2 = dbconn.executeQuery("SELECT * FROM edge.requeststation WHERE requeststation='" + tcp.getSeedNetwork() + tcp.getQ330Stations()[j] + "'");
                if (rs2.next()) {    // does it exist"
                  int ID = rs2.getInt("ID");
                  rs2.close();
                  
                  String s = "UPDATE edge.requeststation SET requeststation='"
                          + tcp.getSeedNetwork() + tcp.getQ330Stations()[j]
                          + "',requestID=" + rID + ",fetchtype='GP',"
                          + "requestip='" + tcp.getQ330InetAddresses()[j].toString().substring(1)
                          + "',requestport=" + (tcp.getQ330Ports()[j] + 24)
                          + ",channelre='" + chanre + "',updated=now() WHERE ID=" + ID;
                  if(dbg) Util.prt("MBAL:" + stations.get(i) + " " + j + " s=" + s);
                  dbconn.executeUpdate(s);
                } else {
                  rs2.close();
                  String s = "INSERT INTO requeststation "
                          + "(requeststation,requestid,fetchtype,throttle,requestip,requestport,channelre,updated,created) VALUES"
                          + "('" + tcp.getSeedNetwork() + tcp.getQ330Stations()[j] + "',"
                          + rID + ",'GP',30000,'"
                          + tcp.getQ330InetAddresses()[j].toString().substring(1) + "',"
                          + (tcp.getQ330Ports()[j] + 24) + ",'" + chanre + "',now(),now())";
                  if(dbg) Util.prt("MBAL:makeANSSBalerTables() INSERT " + stations.get(i) + " " + j + " s=" + s);
                  dbconn.executeUpdate(s);
                }
              }
            }
          } else {
            Util.prt("MBAL: ******* did not find Q330 tcpstation " + stations.get(i));
          }
        }
      } catch (SQLException e) {
        Util.prta("MBAL:makeANSSBalerTables() ** SQLError building list of station! e=" + e);
        Util.SQLErrorPrint(e, "MBAL:makeANSSBalerTables() ** SQLError bulding list of tcpstations");
        break;      // do not do the rest
      }
    }
    if(dbg) Util.prta("MBAL:makeANSSBalerTables() exiting");

  }

  /**
   * Make up the requestStations table for GSN configured stations. All GSN style stations use the
   * GSNQ680Request mechanism so this mainly insures the is a record for each one with the proper
   * rerequest IP and port. The channel regexp is set to NNSSSS[BLV]......
   *
   */
  public static void makeANSSGSNTables() {
    Statement stmt;
    Statement stmtgsnro;
    try {

      if (dbconn == null) {
        Util.prt("MGSN: ****** makeANSSGSNTables() cannot be run - no dbconn connection");
        return;
      }
      stmt = dbconn.getConnection().createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE);
      stmtgsnro = dbconn.getConnection().createStatement();
    } catch (SQLException e) {
      Util.prt("MGSN:Could not create updatable statement.  must not be Golden!");
      return;
    }
    if (stmt == null || stmtgsnro == null) {
      Util.prta("MGSN:GSNQ680request - database statement is null! stmt=" + stmt + " stmt2=" + stmtgsnro);
      return;
    }   // This should not happen, but if it does we cannot run
    if(dbg) Util.prt("MGSN:dbconn =" + dbconn);
    // Gather the roles information
    ArrayList<RoleInstance> roles = new ArrayList<>(10);
    try {
      try (ResultSet rs = stmt.executeQuery("SELECT * FROM edge.role ORDER BY role")) {
        while (rs.next()) {
          roles.add(new RoleInstance(rs));
        }
      }
    } catch (SQLException e) {
      Util.prt("MGSN:Did not find roles table!! e=" + e);
      Util.exit(1);
    }
    ArrayList<RequestType> rtypes = new ArrayList<RequestType>(10);
    try {
      try (ResultSet rs2 = stmt.executeQuery("SELECT * FROM edge.requesttype")) {
        while (rs2.next()) {
          rtypes.add(new RequestType(rs2));
        }
      }
    } catch (SQLException e) {
      Util.prt("MGSN:Did not find baler in request type table!!");
      Util.exit(1);
    }

    // set the various request type parameters.
    int n = 0;
    int gsnTypeID = 0;
    int[] cwbrtype = new int[10];
    for (RequestType type : rtypes) {
      if(dbg) Util.prt("MGSN:Consider rtype=" + type);
      if (type.getRequestType().equalsIgnoreCase("GSNQ680")) {
        gsnTypeID = type.getID();
       if(dbg)  Util.prt("MGSN:Set gsnTypeID=" + gsnTypeID);
      } else if (type.getRequestType().startsWith("CWB") && type.getRequestType().length() <= 4) {
        char num = type.getRequestType().charAt(type.getRequestType().length() - 1);
        if (Character.isDigit(num)) {
         if(dbg)  Util.prt("MGSN:Set CWB " + type.getRequestType() + " num = " + num + " char="
                  + type.getRequestType().charAt(type.getRequestType().length() - 1));
          cwbrtype[num - '0'] = type.getID();
        }
      }
    }
    // Make a list of Q330 stations with this gap type
    try {
      try (ResultSet rs = stmtgsnro.executeQuery(
              "SELECT * FROM edge.gsnstation where requestip!='' AND requestport!=0"
              + " order by gsnstation")) {
        Timestamp recent = Util.TimestampNow();
        recent.setTime(recent.getTime() - 15 * 86400000L);

        while (rs.next()) {
          EdgeStation edgeStation = new EdgeStation(rs);
          int rtypeID = 0;
          String rgaptype = "";       // Gap type to put in RequestStation table
          String chgaptype = "";      // Gap type to put in channel table
                  
          String channelRE = ".....";
          switch (edgeStation.longhaulprotocolString()) {
            case "ISI":
              rgaptype = "IS";
              rtypeID = gsnTypeID;            
              break;
            case "NetServ":
              rgaptype = "IU";
              channelRE = "...";
              rtypeID = gsnTypeID;   
              break;
            case "Edge":
              int roleid = edgeStation.getRoleID();
              for (RoleInstance role : roles) {
                if (roleid == role.getID()) {
                  char num = role.getRole().charAt(role.getRole().length() - 1);
                  if (Character.isDigit(num)) {
                    rgaptype = "CW";
                    chgaptype= rgaptype;
                    rtypeID = cwbrtype[num - '0'];
                  } else {
                    rgaptype = "CW";
                    rtypeID = 0;
                  }
                }
              }
              channelRE = "...|^" + (edgeStation.getGSNStation() + "       ").substring(0, 7) + "...[029][01]";
              break;
            case "SeedLink":
            case "NONE":
            case "LISS":
            case "Comserv":
            case "EdgeGeo":
            case "Invalid":
              break;
            default:
              Util.prta("MGSN: Found station with unknown protocol "+edgeStation);
          }
          
         /* if (rs.getString("longhaulprotocol").equals("ISI")) {

          } else if (rs.getString("longhaulprotocol").equals("NONE")) {
            gaptype = "";
          } else if (rs.getString("longhaulprotocol").equals("LISS")) {
            gaptype = "";
          } else if (rs.getString("longhaulprotocol").equals("Comserv")) {
            gaptype = "";
          } else if (rs.getString("longhaulprotocol").equalsIgnoreCase("NetServ")) {
            gaptype = "IU";
            channelRE = "...";
            rtypeID = gsnTypeID;
          } else if (rs.getString("longhaulprotocol").equalsIgnoreCase("Edge")) {
          }*/
          String chanre = (edgeStation.getGSNStation() + "       ").substring(0, 7) + channelRE;

          ResultSet rs2 = stmt.executeQuery("SELECT * FROM edge.requeststation WHERE requeststation='" + chanre.substring(0, 7).trim() + "'");

          if (rs2.next()) {    // does it exist"
            int ID = rs2.getInt("ID");
            String s = "UPDATE edge.requeststation SET requeststation='" + chanre.substring(0, 7).trim()
                    + "',requestID=" + rtypeID + ",fetchtype='" + rgaptype + "',"
                    + "requestip='" + edgeStation.requestIP() + "',requestport=" + edgeStation.requestport()
                    + ",channelre='" + chanre + "',updated=now(),disablerequestuntil='"
                    + edgeStation.disableRequestUntil().toString().substring(0, 10)
                    + "' WHERE ID=" + ID;
            if(dbg) Util.prt("MGSN:" + chanre + " s=" + s);
            rs2.close();
            n = stmt.executeUpdate(s);
          } else {
            rs2.close();
            String s = "INSERT INTO edge.requeststation "
                    + "(requeststation,requestid,fetchtype,throttle,requestip,requestport,channelre,disablerequestuntil,updated,created) VALUES"
                    + "('" + chanre.substring(0, 7).trim() + "'," + rtypeID + ",'" + rgaptype + "',2000,'"
                    + edgeStation.requestIP() + "',"
                    + edgeStation.requestport() + ",'" + chanre + "','" + edgeStation.disableRequestUntil()
                    + "',now(),now())";
            if(dbg) Util.prt("MGSN:" + chanre + " s=" + s);
            n = stmt.executeUpdate(s);
          }
          // Find when we last saw data from this station
          String s = "SELECT channel,lastdata FROM edge.channel where channel regexp '" 
                  + chanre.substring(0, 7).trim() + "'";
          if (!chgaptype.equals("")) {     // no need to do this for null gap types
            rs2 = stmt.executeQuery(s);
            // Find maximum lastdata for stations in regexp
            recent.setTime(10000000L);
            while (rs2.next()) {
              if (rs2.getTimestamp("lastdata").compareTo(recent) > 0) {
                recent.setTime(rs2.getTimestamp("lastdata").getTime());
              }
            }
            rs2.close();
          }
          recent.setTime(recent.getTime() - 8 * 86400000L);

          // now set the gap type to all be blank for everything matching this channel and reset to gap type for data we have gotten
          s = "UPDATE edge.channel SET gaptype='' WHERE channel REGEXP '" + chanre.substring(0, 7).trim() + "'";
          n = stmt.executeUpdate(s);
          s = "UPDATE edge.channel SET gaptype='" + chgaptype + "' WHERE channel REGEXP '" + chanre
                  + "' AND lastdata>'" + recent.toString().substring(0, 10) + "' AND substring(channel,8,1)!='H' AND "
                  + "SUBSTRING(channel,6,3)!='ACE' AND substring(channel,6,3)!='OCF' and substring(channel,6,3)!='LOG'";
          if (!chgaptype.equals("")) {
            if(dbg) Util.prt("MGSN:channel " + s);
            n = stmt.executeUpdate(s);   // If not null gap type
          }
        }
      }
      stmt.close();
      stmtgsnro.close();

    } catch (SQLException e) {
      Util.SQLErrorPrint(e, "MGSN:SQLError building list of tcpstations");
      e.printStackTrace();
    }
  }

  public static void makeReftekTables() {
    int nupdate = 0;
    anss = DBConnectionThread.getThread("anss");
    if (anss == null) {
      Util.prt("MRT:***** makeReftekTables cannot run - no anss DB connection");
      return;
    }

    try {
      Statement stmtro;
      try (Statement stmt = dbconn.getConnection().createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE)) {
        stmtro = dbconn.getConnection().createStatement();
        int RTPDTypeID = 0;
        ResultSet rs = stmtro.executeQuery("SELECT id FROM edge.requesttype WHERE requesttype='RTPDArc'");
        if (rs.next()) {
          RTPDTypeID = rs.getInt("id");
        }
        rs.close();
        rs = stmtro.executeQuery("SELECT DISTINCT network,station,enddate FROM anss.reftek ORDER BY network,station");
        Timestamp recent = Util.TimestampNow();
        recent.setTime(recent.getTime() - 15 * 86400000L);
        int n;
        while (rs.next()) {
          String chanre = ((rs.getString("network") + "  ").substring(0, 2) + rs.getString("station") + "      ").
                  substring(0, 7) + "...";
          if (rs.getTimestamp("enddate").getTime() < System.currentTimeMillis() - 30 * 86400000L) {
            n = stmt.executeUpdate("DELETE FROM edge.requeststation WHERE requeststation='"
                    + chanre.substring(0, 7).trim() + "'");
            Util.prt("MRT:  * station past close date " + chanre);
            if (n > 0) {
              Util.prt("MRT: ****** DELETING requeststation for " + chanre + " 30 days past close date");
            }
            n = stmt.executeUpdate("UPDATE edge.channel SET gaptype='' WHERE channel regexp '^" + chanre + "'");
            if (n > 0) {
              Util.prt("MRT: **** turn off gaptype for " + chanre + " 30 days past close date");
            }
            continue;
          }
          // This station is not closed for 30 days, configure it in requeststation
          String gaptype = "RT";
          if (chanre.substring(0, 2).equals("XX") || chanre.substring(0, 2).equals("ZZ")) {
            continue;
          }
          ResultSet rs2 = stmt.executeQuery("SELECT * FROM edge.requeststation WHERE requeststation='"
                  + chanre.substring(0, 7).trim() + "'");

          if (rs2.next()) {    // does it exist"
            int ID = rs2.getInt("ID");
            String s = "UPDATE edge.requeststation SET requeststation='" + chanre.substring(0, 7).trim()
                    + "',requestID=" + RTPDTypeID + ",fetchtype='" + gaptype + "',"
                    + "requestip='127.0.0.1',requestport=0"
                    + ",channelre='" + chanre 
                    + "',updated=now(),disablerequestuntil='1971-01-01' WHERE ID=" + ID;
            if(dbg) Util.prt("MRT:" + chanre + " s=" + s);
            rs2.close();
            n = stmt.executeUpdate(s);
            nupdate += n;
          } else {
            rs2.close();
            String s = "INSERT INTO edge.requeststation "
                    + "(requeststation,requestid,fetchtype,throttle,requestip,requestport,channelre,"
                    + "disablerequestuntil,updated,created_by,created) VALUES"
                    + "('" + chanre.substring(0, 7).trim() + "'," + RTPDTypeID + ",'" 
                    + gaptype + "',2000,'127.0.0.1',0,'"
                    + chanre + "','1971-01-01 00:00:00',now(),99,now())";
            if(dbg) Util.prt("MRT:" + chanre + " s=" + s);
            n = stmt.executeUpdate(s);
            nupdate += n;
          }

          // now set the gap type to all be blank for everything matching this channel and reset to gap type for data we have gotten
          String s = "UPDATE edge.channel SET gaptype='' WHERE channel REGEXP '^"
                  + chanre.substring(0, 7).trim() + "'";
          n = stmt.executeUpdate(s);
          nupdate += n;
          /*s = "UPDATE edge.channel SET gaptype='"+gaptype+"' WHERE id in (SELECT * (SELECT channel.id FROM channel,metadata.response "+
          "WHERE channel.channel=metadata.response.channel AND channel.channel REGEXP '^"+chanre.substring(0,7).trim()+
          "' AND lastdata > subdate(now(), INTERVAL 30 DAY) AND metadata.response.seedflags regexp 'C') as temp)";*/
          s = " UPDATE edge.channel SET gaptype='" + gaptype
                  + "' WHERE id in (SELECT * FROM (SELECT channel.id "
                  + "FROM edge.channel,metadata.response "
                  + "WHERE channel.channel=metadata.response.channel AND "
                  + "channel.channel REGEXP '" + chanre.substring(0, 7).trim() 
                  + "' AND lastdata > subdate(now(), INTERVAL 30 DAY) AND "
                  + "metadata.response.seedflags regexp 'C') as temp)";
          n = stmt.executeUpdate(s);
          nupdate += n;
        }
        rs.close();
      }
      stmtro.close();

    } catch (SQLException e) {
      Util.SQLErrorPrint(e, "MRT:SQLError bulding list of tcpstations");
      e.printStackTrace();
    }
  }

  public static void main(String[] args) {
    String fetchServer = null;
    Util.init("edge.prop");
    if (fetchServer == null) {
      fetchServer = Util.getProperty("DBServer");
    }
    if (fetchServer == null) {
      fetchServer = Util.getProperty("MySQLServer");
    }
    if (fetchServer.equals("")) {
      fetchServer = Util.getProperty("MySQLServer");
    }
    if (!Util.isNEICHost()   // comment in production code.
            ) {
      Util.exit(101);     // Only do this at the NEIC
    }
    try {
      // Set up DBConn for "edgeMain" to edge and "anss" to anss.
      dbconn = DBConnectionThread.getThread("edge");
      if (dbconn == null) {
        dbconn = new DBConnectionThread(fetchServer, "update", "edge", true, false, "edge", Util.getOutput());
        if (!dbconn.waitForConnection()) {
          if (!dbconn.waitForConnection()) {
            if (!dbconn.waitForConnection()) {
              EdgeThread.staticprt(" **** Could not connect to edge database " + fetchServer);
              Util.exit(1);
            }
          }
        }
      }
      DBConnectionThread dbanss = DBConnectionThread.getThread("anss");
      if (dbanss == null) {
        dbanss = new DBConnectionThread(fetchServer, "update", "anss", true, false, "anss", Util.getOutput());

        if (!dbanss.waitForConnection()) {
          if (!dbanss.waitForConnection()) {
            if (!DBConnectionThread.waitForConnection("anss")) {
              EdgeThread.staticprt(" **** Could not connect to database " + fetchServer);
              Util.exit(1);
            }
          }
        }
      }
    } catch (InstantiationException e) {
      EdgeThread.staticprta(" **** Impossible Instantiation exception e=" + e);
      Util.exit(0);
    }

    // Do all of the 
    if (args.length == 0) {
      args = new String[1];
      args= "-dbg -all".split("\\s");
    }
    for (String arg : args) {
      switch (arg) {
        case "-dbg":
          dbg=true;
          break;
        case "-gsn":
          makeANSSGSNTables();
          break;
        case "-reftek":
          makeReftekTables();
          break;
        case "-baler":
          makeANSSBalerTables();
          break;
        case "-all":
          makeANSSGSNTables();
          makeReftekTables();
          makeANSSBalerTables();
          break;
        default:
          break;
      }
    }

  }
}
