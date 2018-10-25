/*
 *  This software is in the public domain because it contains materials 
 *  that originally came from the United States Geological Survey, 
 *  an agency of the United States Department of Interior. For more 
 *  information, see the official USGS copyright policy at 
 *  http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.edge.config;

import gov.usgs.anss.db.DBConnectionThread;
import gov.usgs.anss.util.Util;
import java.sql.*;
import java.util.*;
import java.io.*;

/**
 * This class is to convert from the non-instance (role and account) method to the instance based
 * method.
 *
 * 1) Discover all role/account combinations and create a instance table entry 2) For each
 * edgemomsetup in a role/account convert to a row in the instancesetup table 3) for instancecrontab
 * for the role and account, split them up into instance portion and the role portion, and update
 * role and instance tables. 4) Move edge.prop.role.acct into the edge.prop section in instance
 * table, change Node= to be instance, 5) Find RRPClients which correspond to this instance in
 * crontable and put in instance cront portion (convert filename?)
 *
 *
 * @author U.S. Geological Survey  <ketchum at usgs.gov>
 */
public final class EdgeMomInstanceConvert {

  DBConnectionThread dbedge;
  private final StringBuilder instancecrontab = new StringBuilder(1000);
  private final StringBuilder cpuCrontab = new StringBuilder(1000);
  private final TreeMap<String, StringBuilder> roleAccountCrontabs = new TreeMap<>();
  private final StringBuilder roleVdlCrontab = new StringBuilder(1000);

  private final StringBuilder instanceEdgeProp = new StringBuilder(1000);
  private final TreeMap<String, String> cpuProperties = new TreeMap<>();
  private final StringBuilder cpuEdgeProp = new StringBuilder(1000);
  private final StringBuilder moveRing = new StringBuilder(1000);
  private final TreeMap<String, String> edgeproperties = new TreeMap();
  private final ArrayList<String> roleIDs = new ArrayList(10);
  private final ArrayList<String> accts = new ArrayList(10);
  private String node;
  private String instance;
  private String instanceArgs;
  private int instanceMemory;
  private int nodeNumber;
  private int nd;
  private int inst;
  private boolean dbg;
  private boolean alarm = false;
  private final boolean bhcheck = false;
  private boolean cmdstatus = false, config = false;
  private boolean dbmsg = false;
  private boolean etchost = false;
  private final boolean latency = false;
  private boolean routes = false;
  private boolean snw = false, snwsender = false, udpchan = false, web = false, trap = false;
  // Other tasks that can be run by having memory allocations > 0, They
  // are run by chosing the maximum value of the memory for this task for all roles
  private String alarmArgs = "";
  private int alarmmem = 50;
  private int aftac = 0;
  private int consoles = 0;
  private int metadata = 0;
  private final int quakeml = 0;
  private int querymom = 0;
  private int rts = 0, smgetter = 0, tcpholdings = 0;
  private final int proc1mem = 0;
  private final int proc2mem = 0;
  private final int proc3mem = 0;
  private final int proc4mem = 0;
  private final int proc5mem = 0;
  private final int proc6mem = 0;
  private final int proc7mem = 0;
  private final int proc8mem = 0;
  private final int proc9mem = 0;
  private final long alarmprocmask = 0;

  public void setDebug(boolean t) {
    dbg = t;
  }

  private String getAlarmOpts() {// this is not needed
    String s = "";
    if (!alarm) {
      s += "-noalarm ";
    }
    if (snw) {
      s += "-snw ";
    }
    if (snwsender) {
      s += "-snwsender ";
    }
    if (web) {
      s += "-web";
    }
    if (!udpchan) {
      s += "-noudpchan ";
    }
    if (!cmdstatus) {
      s += "-nocmd ";
    }
    if (!dbmsg) {
      s += "-nodbmsg ";
    }
    if (!config) {
      s += "-nocfg ";
    }
    if (routes) {
      s += "-routes ";
    }
    if (etchost) {
      s += "-etchosts ";
    }
    return s;
  }

  public StringBuilder getMoveRing() {
    return moveRing;
  }

  public EdgeMomInstanceConvert(DBConnectionThread db) {
    dbedge = db;

  }

  private void doAccounts(RoleInstance role) throws SQLException {
    // for each role, find each account
    Statement rems = dbedge.getNewStatement(false);

    ResultSet rs = rems.executeQuery("SELECT DISTINCT account FROM edgemomsetup WHERE roleid="
            + role.getID() + " ORDER BY account");
    while (rs.next()) {
      accts.add(rs.getString(1));
    }
    rs.close();
    // Now iterate on each role and account to make a new instance record
    String[] parts = role.getEnabledAccounts().split("\\s");
    boolean found = false;
    for (String acct : parts) {
      for (String a : accts) {
        if (a.equals(acct)) {
          found = true;
          break;
        }
      }
      if (!found) {
        accts.add(acct);
      }
    }
    rs.close();
  }

  public void processProperties(String props, Cpu cpu, RoleInstance role, String account) throws SQLException {
    String line;
    try (BufferedReader in = new BufferedReader(new StringReader(props))) {
      while ((line = in.readLine()) != null) {
        instanceEdgeProp.append(line).append("\n");
        //Util.prt("edge.prop line="+line);
        if (line.length() == 0) {
          continue;
        }
        if (line.charAt(0) == '#' || line.charAt(0) == '!') {
          continue;
        }
        if (line.contains("=")) {
          String[] parts = line.split("=");
          if (parts.length == 1) {
            String tmp = parts[0];
            parts = new String[2];
            parts[0] = tmp;
            parts[1] = "";
          }
          // Pick off properties that belong on the cpu
          if (parts[0].equalsIgnoreCase("Node")) {
            node = parts[1];
          } else if (parts[0].startsWith("nday") || parts[0].startsWith("datapath")
                  || parts[0].startsWith("ndatapath") || parts[0].startsWith("MySQLServer")
                  || parts[0].startsWith("QMLDBServer") || parts[0].startsWith("DBServer")
                  || parts[0].startsWith("MetaDBServer") || parts[0].startsWith("StatusDBServer")
                  || parts[0].startsWith("StatusServer") || parts[0].startsWith("logfilepath")
                  || parts[0].startsWith("SMTPServer") || parts[0].startsWith("emailTo")
                  || parts[0].startsWith("AlarmIP") || parts[0].startsWith("datamask")
                  || parts[0].startsWith("NPlusOne")
                  || parts[0].startsWith("SNWServer") || parts[0].startsWith("SNWPort")
                  || parts[0].startsWith("RTSServer") || parts[0].startsWith("AlarmPort")
                  || parts[0].startsWith("instanceconfig")) {
            String tmp = cpuProperties.get(parts[0].trim());
            if (tmp == null) {
              if (dbg) {
                Util.prt("Add to cpu edge.prop " + parts[0] + "=" + parts[1]);
              }
              cpuProperties.put(parts[0].trim(), parts[1].trim());
            } else if (!tmp.equals(parts[1].trim())) {
              Util.prt(" ** Instance for " + node + " mismatch prop=" + parts[0] + " is " + tmp + " but changed to " + parts[1]);
              edgeproperties.put(parts[0].trim(), parts[1].trim());
            }
          } else if (parts[0].startsWith("daysize") || parts[0].startsWith("extendsize")
                  || parts[0].startsWith("KillPages") || parts[0].startsWith("ebqsize")
                  || parts[0].startsWith("SMTPFrom")) {
            if (parts[0].startsWith("SMTPFrom") && parts[1].contains("-")) {
              int hyp = parts[1].indexOf("-");
              parts[1] = parts[1].substring(0, hyp) + "-$INSTANCE";
            }
            Util.prt("Add to instance edge.prop " + parts[0] + "=" + parts[1]);
            edgeproperties.put(parts[0].trim(), parts[1].trim());
          } else if (parts[0].startsWith("KetchumPage") || parts[0].startsWith("KetchumOverrid")) {
            if (dbg) {
              Util.prt("Dropping " + parts[0]);
            }
          } else {
            Util.prt("*** Found a property not in list prop=" + parts[0] + "=" + parts[1] + " acct=" + account + " node=" + node);
            edgeproperties.put(parts[0].trim(), parts[1].trim());
          }
        }
      }
      //Util.prt("edge.prop= :\n"+instanceEdgeProp);
      //Util.prt("edgeproperties \n"+edgeproperties);
    } catch (IOException e) {
      Util.prt("Odd I/O error reading cron string");
    }
  }

  public void doInstancesRoles(String cpuString) {
    Cpu cpu = null;
    String args;
    String s;
    int nodeNumber = -1;
    cpuCrontab.append("#<cpu> ----- ").append(cpuString).append(" -----\n");
    ArrayList<RoleInstance> roles = new ArrayList<>(4);
    try {
      ResultSet rscpu = dbedge.executeQuery("SELECT * FROM cpu WHERE cpu='" + cpuString + "'");
      Statement stmt = dbedge.getNewStatement(false);
      Statement stmt2 = dbedge.getNewStatement(false);
      Statement stmt3 = dbedge.getNewStatement(false);
      if (rscpu.next()) {
        cpu = new Cpu(rscpu);
        int nroles = 0;
        nodeNumber = cpu.getNodeNumber();
        ResultSet rsrole = stmt2.executeQuery("SELECT * FROM role WHERE cpuid=" + cpu.getID());
        while (rsrole.next()) {
          RoleInstance role = new RoleInstance(rsrole);
          roles.add(role);
          nroles++;
          ResultSet rsinstance = stmt3.executeQuery("SELECT * FROM instance WHERE roleid=" + role.getID());
          while (rsinstance.next()) {
            EdgeMomInstance instance = new EdgeMomInstance(rsinstance);
            int n1 = dbedge.executeUpdate("DELETE FROM instancesetup WHERE instanceid=" + instance.getID());
            int n2 = dbedge.executeUpdate("DELETE FROM instance WHERE id=" + instance.getID());
            if (dbg) {
              Util.prt("DELETE instancesetup n=" + n1 + " DELETE instance n=" + n2 + " role=" + role + " nroles=" + nroles);
            }
          }
          int n2 = dbedge.executeUpdate("DELETE FROM querymominstance WHERE roleid=" + role.getID());

        }
      } else if (cpu == null) {
        Util.prt("**** EdgemomInstanceConvertd did not find cpu=" + cpuString);
        return;
      }
    } catch (SQLException e) {

    }
    try {
      Statement rems = dbedge.getNewStatement(false);
      Statement readEdgeFile = dbedge.getNewStatement(false);
      Statement writeInstance = dbedge.getNewStatement(true);
      Statement writeRole = dbedge.getNewStatement(true);
      String origEdgeProp = null;     // This is a list of all of the threads that can be run in the alarm
      // They are an "or" of all of the configuration of all of the roles
      try {
        for (RoleInstance role : roles) {
          accts.clear();
          doAccounts(role);     // this populates the accts on this node

          // If there is a role crontab, start with it - this would be unusual
          Util.clear(roleVdlCrontab);
          alarmArgs = "";

          for (String account : accts) {

            Util.prt("Start role=" + role.getID() + "/" + role.getRole() + " acct=" + account
                    + " role Enabled accts=" + role.getEnabledAccounts());
            ResultSet rs = rems.executeQuery("SELECT content FROM edgefile WHERE edgefile="
                    + "'edge.prop." + role.getRole() + "." + account + "'");
            Util.clear(instanceEdgeProp);
            node = null;            // lose any prior node information
            edgeproperties.clear();
            if (rs.next()) {
              origEdgeProp = rs.getString(1);
              // read in the edge.prop for this one
              processProperties(origEdgeProp, cpu, role, account);
            } else {
              Util.prt("** Did not find a edge.prop." + role.getRole() + "." + account);
            }
            rs.close();
            instanceArgs = "";

            // Get the account crontab
            String origCrontab = null;
            rs = rems.executeQuery("SELECT content FROM edgefile WHERE edgefile='crontab."
                    + role.getRole() + "." + account + "'");
            if (rs.next()) {
              origCrontab = rs.getString(1);
            } else {
              Util.prt("** Did not find crontab." + role.getRole() + "." + account);
            }
            rs.close();

            // We are ready to create the instances and update role with its part of the instancecrontab
            if (node == null) {
              Util.prt("**** The node is not set by property Node!  How can this be??? node=" + node + " role=" + role + " acct=" + account);
            }

            // figure out the nodeNumber, instance number, etc from instance (from Node property)
            instance = node;
            inst = Util.getTrailingNumber(account);
            if (account.equals("reftek")) {
              inst = 9;
            }
            if (account.equals("metadata")) {
              inst = 8;
            }
            if (account.equals("snw")) {
              inst = 7;
            }
            if (instance == null) {    // I do not think this can be executed!
              Util.prt("*** this account does not have a instance - create one!");
              instance = nodeNumber + "#" + inst;
            }
            if (!instance.contains("#")) {
              instance = nodeNumber + "#" + inst;
            }
            if (nodeNumber != Util.getLeadingNumber(instance)) {
              Util.prt("****** The node from the instance does not aggee with CPU.nodeNumber.  This should not happen");
              return;
            }
            if (inst != Util.getTrailingNumber(instance)) {
              Util.prt("****** The instance number is not as expected instance=" + instance + " node#=" + nodeNumber + " inst=" + inst);
              return;
            }
            // create property SB
            doProperty(cpu);

            Util.prt("Set instance=" + instance + " Node from prop file");
            // Find the instancecrontab for this role and account
            try {
              Util.clear(instancecrontab);
              rs = rems.executeQuery("SELECT * FROM edgefile WHERE edgefile='querymom.setup."
                      + role.getRole() + "." + account + "'");
              String querysetup = "";
              if (rs.next()) {
                querysetup = rs.getString("content");
              }
              rs.close();
              processCrontab(origCrontab, cpu, role, account, querysetup);
            } catch (IOException e) {
              Util.prt("** error reading crontab!! ");
              e.printStackTrace();
            }

            int disabled = 0;
            if (!role.getEnabledAccounts().contains(account)) {
              disabled = 1;
            }
            Util.stringBuilderReplaceAll(cpuEdgeProp, "  ", " ");
            Util.stringBuilderReplaceAll(instanceEdgeProp, "  ", " ");
            Util.stringBuilderReplaceAll(cpuCrontab, "  ", " ");
            Util.stringBuilderReplaceAll(roleVdlCrontab, "  ", " ");
            if (dbg) {
              Util.stringBuilderReplaceAll(instancecrontab, "  ", " ");
              Util.prt("====edgeProp original : \n" + origEdgeProp);
              Util.prt("====cpu edge.prop : \n" + cpuEdgeProp);
              Util.prt("====instance edge_" + instance + ".prop : \n" + instanceEdgeProp);
              Util.prt(" ----- original crontab :\n" + origCrontab);
              Util.prt(" ----- CpuCrontab :\n" + cpuCrontab);
              Util.prt(" ----- RoleCrontab : \n" + roleVdlCrontab);
            }
            s = "INSERT INTO instance (instance, roleid, account, heap,args, "
                    + "description, disabled, edgeprop, crontab,updated) VALUES ("
                    + "'" + instance + "'," + role.getID() + "," + (account.startsWith("vdl") ? "'vdl'," : "'" + account + "',")
                    + instanceMemory + ",'" + instanceArgs
                    + "','Converted from " + role.getRole() + " acct=" + account + "'," + disabled + ",'"
                    + deq(instanceEdgeProp.toString()) + "','" + deq(instancecrontab.toString()) + "',now())";
            if (dbg) {
              Util.prt(s);
            }
            dbedge.executeUpdate(s);
            int instanceID = dbedge.getLastInsertID("instance");
            s = "UPDATE role SET crontab='" + deq(roleVdlCrontab.toString()) + "',alarm=" + (alarm ? 1 : 0)
                    + ",alarmmem=" + alarmmem + ",alarmbhchecker=" + (bhcheck ? 1 : 0) + ",alarmcmdstatus=" + (cmdstatus ? 1 : 0)
                    + ",alarmconfig=" + (config ? 1 : 0) + ",alarmdbmsgsrv=" + (dbmsg ? 1 : 0) + ",alarmetchosts=" + (etchost ? 1 : 0)
                    + ",alarmroutes=" + (routes ? 1 : 0) + ",alarmsnw=" + (snw ? 1 : 0) + ",alarmsnwsender=" + (snwsender ? 1 : 0)
                    + ",alarmudpchannel=" + (udpchan ? 1 : 0) + ",alarmwebserver=" + (web ? 1 : 0)
                    + ",alarmargs='" + alarmArgs + "'"
                    + ",aftac=" + aftac + ",consoles=" + consoles + ",metadata=" + metadata + ",querymom=" + querymom
                    + ",rts=" + rts + ",smgetter=" + smgetter + ",tcpholdings=" + tcpholdings
                    + " WHERE id=" + role.getID();
            if (dbg) {
              Util.prt("Update role: " + s);
            }
            writeRole.executeUpdate(s);
            // update the cpu
            s = "UPDATE cpu SET crontab='" + cpuCrontab.toString() + "',edgeprop='" + cpuEdgeProp.toString()
                    + "' WHERE id=" + cpu.getID();
            if (dbg) {
              Util.prt("Update cpu : " + s);
            }
            writeRole.executeUpdate(s);

            // Now find all of the edgemomsetup lines and convert them to instancesetup lines
            rs = rems.executeQuery("SELECT * FROM edgemomsetup WHERE roleid=" + role.getID()
                    + " AND account ='" + account + "' ORDER BY tag");
            while (rs.next()) {
              // Look up any edgefile and put it in the instance
              ResultSet rsedgefile = readEdgeFile.executeQuery("SELECT * FROM edgefile WHERE id=" + rs.getInt("edgefileid"));
              String configFilename = Util.fs + "NONE";  // slash none
              String configContent = "";
              if (rsedgefile.next()) {
                configFilename = rsedgefile.getString("edgefile");
                configContent = rsedgefile.getString("content");
              }
              // If this is an RRP2Edge thread, check and fix the argument for the ring file
              args = deq(rs.getString("args"));
              args = args.replaceAll(Util.cleanIP(role.getIP()), "\\$ROLEIP");
              s = "INSERT INTO instancesetup (instanceid, tag, priority, edgethreadid,"
                      + "args,logfile,comment,configfile,config,edgefileid,disabled,updated,created) VALUES (" + instanceID + ",'"
                      + rs.getString("tag") + "'," + rs.getInt("priority") + "," + rs.getInt("edgethreadid") + ",'" + deq(args).trim()
                      + "','" + rs.getString("logfile") + "','" + deq(rs.getString("comment")) + "','" + configFilename + "','"
                      + deq(configContent).trim() + "',"
                      + "0" + "," + rs.getInt("disabled") + ",now(),now())";
              if (dbg) {
                Util.prt(s);
              }
              dbedge.executeUpdate(s);

            }
            Util.prt("End of loop for role=" + role + " acct=" + account);
          }   // for each account on the role
        }
      } catch (SQLException e) {
        Util.prt("*** SQL Error=" + e);
        e.printStackTrace();
      }
    } catch (SQLException e) {
      Util.prt("*** SQL error e=" + e);
      e.printStackTrace();
    } catch (RuntimeException e) {
      Util.prt("*** RuntimeException e=" + e);
      e.printStackTrace();
    }
  }

  private void doProperty(Cpu cpu) {
    Util.clear(cpuEdgeProp);
    cpuEdgeProp.append("# cpu edge.prop for cpu=").append(cpu.getCpu()).append("\n").append("Node=").
            append(cpu.getNodeNumber()).append("\n");
    Util.clear(instanceEdgeProp);
    instanceEdgeProp.append("# instance edge_").append(instance).append(".prop\n");
    Set<String> props = cpuProperties.keySet();
    Iterator<String> iter = props.iterator();
    while (iter.hasNext()) {
      String key = iter.next();
      String value = cpuProperties.get(key);
      cpuEdgeProp.append(key).append("=").append(value).append("\n");
    }
    if (cpuProperties.get("instanceconfig") == null) {
      cpuEdgeProp.append("instanceconfig=true");  // This program is to make instance configs!
    }
    props = edgeproperties.keySet();
    iter = props.iterator();
    while (iter.hasNext()) {
      String key = iter.next();
      String value = edgeproperties.get(key);
      instanceEdgeProp.append(key).append("=").append(value).append("\n");
    }
  }

  private void doChkCWB(StringBuilder cron, String[] parts, Cpu cpu, RoleInstance role,
          String account, boolean nice, boolean max) {
    if (parts[7].equalsIgnoreCase("querymom")) {
      Util.prt("QueryMom role=" + role + " acct=" + account);
    }
    cron.append("chkCWB ").append(parts[7].toLowerCase()).append(" ^ ");
    cron.append(parts[6].contains("chkJar") ? parts[8] : parts[9]).append(" ");  // memory
    if (max) {
      cron.append("-max ");
    }
    if (nice) {
      cron.append("-nice ");
    }
    if (parts[6].contains("Tag")) {
      cron.append("-tag ").append(parts[10]).append(" ");
      for (int i = 11; i < parts.length; i++) {
        if (parts[i].contains(">")) {
          break;
        }
        if (!parts[i].equals("\"\"")) {
          parts[i] = parts[i].replaceAll("'", "");
          if (parts[i].contains("|")) {
            parts[i] = "'" + parts[i] + "'";
          }
          cron.append(parts[i]).append(" ");
        }
      }
    } else {
      for (int i = (parts[6].contains("chkJar") ? 9 : 10); i < parts.length; i++) {
        if (parts[i].contains(">")) {
          break;
        }
        if (!parts[i].equals("\"\"")) {
          parts[i] = parts[i].replaceAll("'", "");
          if (parts[i].contains("|")) {
            parts[i] = "'" + parts[i] + "'";
          }
          cron.append(parts[i]).append(" ");
        }
      }
    }
    cron.append("\n");
    Util.stringBuilderReplaceAll(cron, "  ", " ");
  }

  private void doChkNEIC(StringBuilder cron, String[] parts, Cpu cpu, RoleInstance role,
          String account, boolean nice, boolean max) {
    cron.append("chkNEIC ").append(parts[7]).append(" ^ ");
    cron.append(parts[6].contains("chkJar") ? parts[8] : parts[9]).append(" ");  // memory
    if (max) {
      cron.append("-max ");
    }
    if (nice) {
      cron.append("-nice ");
    }
    if (parts[6].contains("Tag")) {
      cron.append("-tag ").append(parts[10]).append(" ");
      for (int i = 11; i < parts.length; i++) {
        if (parts[i].contains(">")) {
          break;
        }
        if (!parts[i].equals("\"\"")) {
          cron.append(parts[i].replaceAll("'", "")).append(" ");
        }
      }
    } else {
      for (int i = (parts[6].contains("chkJar") ? 9 : 10); i < parts.length; i++) {
        if (parts[i].contains(">")) {
          break;
        }
        cron.append(parts[i].replaceAll("'", "")).append(" ");
      }
    }
    cron.append("\n");
  }

  private void processCrontab(String cron, Cpu cpu, RoleInstance role, String account,
          String querysetup) throws IOException {
    String line;
    String lastComment = "";
    // If the accounts are 'vdl' put it all in one role crontab,
    // If this is an odd account like reftek or metadata, put everything in instance crontab
    StringBuilder roleCrontab = this.roleVdlCrontab;
    instanceMemory = 0;     // do not assume a edgemom is present
    if (!account.startsWith("vdl")) {
      roleCrontab = instancecrontab;
    }
    try (BufferedReader in = new BufferedReader(new StringReader(cron))) {
      String mode = "#<account>";
      while ((line = in.readLine()) != null) {
        if (line.length() == 0) {
          continue;
        }
        line = line.replaceAll("'", "");
        if (line.startsWith("#<account")) {
          mode = "#<account>";
          continue;
        } else if (line.startsWith("#<vdlonly")) {
          mode = "#<vdlonly>";
          continue;
        } else if (line.startsWith("#<vdlalways")) {
          mode = "#<vdlalways>";
          continue;
        } else if (line.startsWith("#<alwaysvdl")) {
          mode = "#<alwaysvdl>";
          continue;
        }
        if (line.startsWith("#")) {
          lastComment = line;
          continue;
        }
        boolean nice = false;
        if (line.contains("nice ") && (line.contains("chkJar") || line.contains("chkNEIC"))) {
          nice = true;
          line = line.replaceAll("nice ", "");
        }
        boolean max = false;
        String[] parts = line.split("\\s");

        if (parts.length >= 8 && (parts[6].contains("chkJar") || parts[6].contains("chkCWB") || parts[6].contains("chkNEIC"))) {
          // look at each line and set the alarm booleans and memory sizes for various tasks, if we find it, it will all be configured
          // via the role
          String memory = parts[8];
          if (parts[6].contains("Max")) {
            max = true;    // set max heap mode
          }
          if (parts[6].contains("chkCWB") || parts[6].contains("chkNEIC")) {
            memory = parts[9];
          }

          if (parts[6].contains("chkJarProductClient")) {  // chkJar but special version
            if (lastComment.length() > 0) {
              roleCrontab.append(lastComment).append("\n");
            }
            roleCrontab.append(line).append("\n");
          } else if (parts[7].equalsIgnoreCase("TcpHoldings")
                  || parts[7].equalsIgnoreCase("Fetcher")
                  || parts[7].equalsIgnoreCase("EWExport")
                  || parts[7].equalsIgnoreCase("DailyFileWriter")
                  || parts[7].equalsIgnoreCase("LISSServer")
                  || parts[7].equalsIgnoreCase("nanohttpd")
                  || parts[7].equalsIgnoreCase("RRPServer")) {
            if (parts[7].equalsIgnoreCase("TcpHoldings")) {
              tcpholdings = Integer.parseInt(memory);
            } else {
              if (lastComment.length() > 0) {
                roleCrontab.append(lastComment).append("\n");
              }
              doChkCWB(roleCrontab, parts, cpu, role, account, nice, max);
            }
          } else if (parts[7].equalsIgnoreCase("AftacHoldingsServer")
                  || parts[7].equalsIgnoreCase("SMGetter")
                  || parts[7].equalsIgnoreCase("ConsoleServer")
                  || parts[7].equalsIgnoreCase("RTSServer")
                  || parts[7].equalsIgnoreCase("MetaDataServer")
                  || parts[7].equalsIgnoreCase("HoldingsConsolidate")
                  || parts[7].equalsIgnoreCase("UdpTunnel")
                  || parts[7].equalsIgnoreCase("MakeConfigServerXML")) {
            if (parts[7].equalsIgnoreCase("AftacHoldingsServer")) {
              aftac = Integer.parseInt(memory);
            } else if (parts[7].equalsIgnoreCase("SMGetter")) {
              smgetter = Integer.parseInt(memory);
            } else if (parts[7].equalsIgnoreCase("ConsoleServer")) {
              consoles = Integer.parseInt(memory);
            } else if (parts[7].equalsIgnoreCase("RTSServer")) {
              rts = Integer.parseInt(memory);
            } else if (parts[7].equalsIgnoreCase("MetaDataServer")) {
              metadata = Integer.parseInt(memory);
            } else {
              if (lastComment.length() > 0) {
                roleCrontab.append(lastComment).append("\n");
              }
              doChkNEIC(roleCrontab, parts, cpu, role, account, nice, max);
            }
          } else if (parts[7].equalsIgnoreCase("QueryMom") || parts[7].equalsIgnoreCase("QueryServer")) {
            querymom = Integer.parseInt(memory);
            boolean qsenable = false, cwbwsenable = false, dlqsenable = false, qscenable = false;
            boolean mscenable = false, twsenable = false, fpenable = false, fkenable = false, ssdenable = false;
            String qsArgs = "", cwbwsArgs = "", dlqsArgs = "", qscArgs = "", mscArgs = "", twsArgs = "", fpArgs = "", fkArgs = "", ssdArgs = "";
            String qinstance = instance.replaceAll("#", "Q");

            if (parts[7].equalsIgnoreCase("QueryServer")) {
              qsenable = true;
              for (int i = 9; i < parts.length; i++) {
                if (parts[i].equals("\"\"")) {
                  continue;
                }
                if (parts[i].contains(">")) {
                  break;
                }
                qsArgs += parts[i] + " ";
              }
            } else {
              if (parts[8].contains("#")) {
                qinstance = parts[8].replaceAll("#", "Q");
              }
              if (line.contains("-f") || parts[8].contains("#")) {
                for (int i = 7; i < parts.length; i++) {
                  if (parts[i].equals("-f")) {
                    try {
                      try (ResultSet rs = dbedge.executeQuery("SELECT content FROM edgefile WHERE edgefile='"
                              + parts[i + 1] + "." + role.getRole() + "." + account + "'")) {
                        if (rs.next()) {
                          querysetup = rs.getString(1);
                        }
                      }
                    } catch (SQLException e) {
                      Util.prt(" *** converting querymom/queryserver e=" + e);
                    }
                  }
                  if (parts[6].equalsIgnoreCase("chkCWB")) {
                    qinstance = parts[8].replaceAll("#", "Q");
                  }
                }
              }
              try (BufferedReader qsin = new BufferedReader(new StringReader(querysetup))) {
                String l;
                while ((l = qsin.readLine()) != null) {
                  if (l.startsWith("#") || l.startsWith("!")) {
                  } else if (l.contains("EdgeQueryServer")) {
                    qsenable = true;
                    qsArgs = getQArgs(l);
                  } else if (l.contains("CWBWaveServer")) {
                    cwbwsenable = true;
                    cwbwsArgs = getQArgs(l);
                  } else if (l.contains("DataLinkToQueryServer")) {
                    dlqsenable = true;
                    dlqsArgs = getQArgs(l);
                  } else if (l.contains("QuerySpanCollection")) {
                    qscenable = true;
                    qscArgs = getQArgs(l);
                  } else if (l.contains("MiniSeedCollection")) {
                    mscenable = true;
                    mscArgs = getQArgs(l);
                  } else if (l.contains("TrinetServer")) {
                    twsenable = true;
                    twsArgs = getQArgs(l);
                  } else if (l.contains("FilterPickerManager")) {
                    fpenable = true;
                    fpArgs = getQArgs(l);
                  } else if (l.contains("FKManager")) {
                    fkenable = true;
                    fkArgs = getQArgs(l);
                  } else if (l.contains("SubSpaceDetector")) {
                    ssdenable = true;
                    ssdArgs = getQArgs(l);
                  }
                }
              }
            }
            String querymomArgs = "";
            if (line.contains("-max") || max) {
              querymomArgs += "-max ";
            }
            if (line.contains("-test")) {
              querymomArgs += "-test";
            }
            String s;
            try {
              s = "INSERT INTO querymominstance (instance,roleid,description,failoverid,failover,account,heap,args,disabled,"
                      + "qsenable,qsargs,cwbwsenable,cwbwsargs,dlqsenable,dlqsargs,qscenable,qscargs,"
                      + "fkenable,fkargs,mscenable,mscargs,twsenable,twsargs,fpenable,fpargs,ssdenable,ssdargs,"
                      + "updated, created) VALUES ('"
                      + qinstance + "'," + role.getID() + ",'Converted from " + role.getRole() + " acct=" + account + "',0,0,"
                      + (account.startsWith("vdl") ? "'vdl'," : "'" + account + "',")
                      + querymom + ",'" + querymomArgs
                      + "',0," + (qsenable ? 1 : 0) + ",'" + deq(qsArgs) + "'," + (cwbwsenable ? 1 : 0) + ",'" + deq(cwbwsArgs) + "',"
                      + (dlqsenable ? 1 : 0) + ",'" + deq(dlqsArgs) + "',"
                      + (qscenable ? 1 : 0) + ",'" + deq(qscArgs) + "'," + (fkenable ? 1 : 0) + ",'" + deq(fkArgs) + "',"
                      + (mscenable ? 1 : 0) + ",'" + deq(mscArgs) + "'," + (twsenable ? 1 : 0) + ",'" + deq(twsArgs) + "',"
                      + (fpenable ? 1 : 0) + ",'" + deq(fpArgs) + "'," + (ssdenable ? 1 : 0) + ",'" + deq(ssdArgs)
                      + "',now(),now())";
              dbedge.executeUpdate(s);
            } catch (SQLException e) {
              Util.prta("*** SQL querymom instance error=" + e);
            }
          } else if (parts[7].equalsIgnoreCase("Alarm")) {
            alarmmem = Integer.parseInt(memory);
            alarm = true;
            alarmArgs = "";
            if (parts[6].contains("chkCWB")) { // If already chkCWB different defaults
              alarm = true;
              web = false;
              snw = false;
              snwsender = false;
              cmdstatus = true;
              dbmsg = true;
              config = true;
              udpchan = true;
            } else {
              alarm = true;
              cmdstatus = true;
              dbmsg = false;
              snw = true;
              trap = false;   // this is likely opposite of default, but traps are never used
              udpchan = false;
            }
            for (int i = 10; i < parts.length; i++) {
              switch (parts[i]) {
                case "-snw":
                  snw = true;
                  break;
                case "-nosnw":
                  snw = false;
                  break;
                case "-nosnwsender":
                  snwsender = false;
                  break;
                case "-snwsender":
                  snwsender = true;
                  break;
                case "-web":
                  web = true;
                  break;
                case "-noweb":
                  web = false;
                  break;
                case "-nodbmsg":
                  dbmsg = false;
                  break;
                case "-noudpchan":
                  udpchan = false;
                default:
                  if (!parts[i].contains(">")) {
                    alarmArgs += parts[i] + " ";
                  }
              }
            }
          } else if (parts[7].equalsIgnoreCase("UdpChannel")) {
            udpchan = true;
            snw = line.contains("-snw");
            snwsender = line.contains("-snwsender");
            web = line.contains("-web");
          } else if (parts[7].equalsIgnoreCase("CommandStatusServer")) {
            cmdstatus = true;
          } else if (parts[7].equalsIgnoreCase("POCMySQLServer")) {
            dbmsg = true;
          } else if (parts[7].equalsIgnoreCase("MakeVsatRoutes")) {
            config = !line.contains("-noedgeconfig");
            routes = !line.contains("-noroutes");
            etchost = !line.contains("-noetchosts");
          } // These are things for the instance crontab
          else if (parts[7].equalsIgnoreCase("EdgeMom")) {
            if (parts[6].contains("chkCWB") && node == null) {
              node = parts[8];
            }
            if (lastComment.length() > 0) {
              instancecrontab.append(lastComment).append("\n");
            }
            instanceMemory = Integer.parseInt(memory);
            instancecrontab.append("####chkCWB edgemom ").append(instance).append(" ").
                    append(instanceMemory).append(" ");
            for (int i = (parts[6].contains("chkCWB") ? 10 : 9); i < parts.length; i++) { // Note parts[8] is memory size
              if (parts[i].contains(">")) {
                instancecrontab.append(instanceArgs).append(" ");
                instancecrontab.append(">> LOG").append(Util.fs).append("cwblog.log 2>&1\n");
                break;
              } else if (!parts[i].equals("\"\"")) {
                instanceArgs += parts[i] + " ";
                instancecrontab.append(parts[i]).append(" ");
              }
            }

          } else if (parts[7].equalsIgnoreCase("RRPClient")) {
            // We need to hunt down the instance reference in the filename and substitute for it
            if (lastComment.length() > 0) {
              instancecrontab.append(lastComment).append("\n");
            }
            instancecrontab.append("chkCWB rrpclient ").append(" ").append("$INSTANCE ");
            doRing(instancecrontab, parts, inst, instance);
          } else if (parts[7].equalsIgnoreCase("EWExport")) {
            if (lastComment.length() > 0) {
              instancecrontab.append(lastComment).append("\n");
            }
            instancecrontab.append("chkCWB ewexport ").append("$INSTANCE").append(" ");
            doRing(instancecrontab, parts, inst, instance);
          } else {
            if (lastComment.length() > 0) {
              roleCrontab.append(lastComment).append("\n");
            }
            roleCrontab.append("# This line was not modified!!!!!").
                    append(line).append("\n");
            Util.prt("*** Unhandled chkJar line=" + line);
          }
        } // its a 'chkJar' or 'chkCWB'
        // These are lines which do not have "chkJar"
        else {
          if (line.contains("bkedge") || line.contains("chkEdgeAnssSQL")
                  || line.contains("moveHoldings") || line.contains("chkMonitorProcess")
                  || line.contains("chkMinfree")) {
            if (lastComment.length() > 0) {
              cpuCrontab.append(lastComment).append("\n");
            }
            cpuCrontab.append(line).append("\n");
          } else if (line.contains("chkClassProc")) {
            if (parts[8].contains("SeedLinkConfigBuilder")) {
              roleCrontab.append("chkCWB slconfig ^ ");
              for (int i = 9; i < parts.length; i++) {
                if (parts[i].contains(">")) {
                  break;
                }
                if (!parts[i].equals("\"\"")) {
                  roleCrontab.append(parts[i]).append(" ");
                }
              }
            } else {
              Util.prt("** Found unhandle chkClassProc" + line);
            }
            //Util.prt("done ");
          } else if (line.contains("updateHoldings") || line.contains("chkRingServer")
                  || line.contains("processPastFile") || line.contains("SNW")
                  || line.contains("snw") || line.contains("chkInvToSEED")
                  || line.contains("ConfigServer") || line.contains("gldqc2push")
                  || line.contains("moveLastData") || line.contains("makeResearchFetchlist")
                  || line.contains("getISCEpoch") || line.contains("aftac_qml")
                  || line.contains("chkMonitor") || line.contains("SEEDIMPORT/bin")
                  || line.contains("chkirsan") || line.contains("chkoob")
                  || line.contains("makeTA")
                  || line.contains("purgeNQF") || line.contains("chkNE")
                  || line.contains("chkreftek") || line.contains("killrtcc")
                  || line.contains("chkVdldfcSQL") || line.contains("econ2remotedb")
                  || line.contains("moveChanRate") || line.contains("makeUUSS")
                  || line.contains("moveNewResp") || line.contains("javadocLoader")
                  || line.contains("chkPensive") || line.contains("chkSubnetogram")
                  || line.contains("cleanup") || line.contains("gen_pqlx_data")
                  || line.contains("fetchResearch.bash") || line.contains("chkGeomag")
                  || line.contains("checkday") || line.contains("processNSMP")
                  || line.contains("doAllSeed") || line.contains("TcpConnection")) {
            if (lastComment.length() > 0) {
              roleCrontab.append(lastComment).append("\n");
            }
            roleCrontab.append(line).append("\n");
          } else {
            if (lastComment.length() > 0) {
              roleCrontab.append(lastComment).append("\n");
            }
            roleCrontab.append("# This line was not Handled!!!!!\n").
                    append(line).append("\n");
            Util.prt("*** Unhandled line=" + line);
          }
          //instancecrontab.append(line).append("\n");
        }
        lastComment = "";   // If we just did something, erase last comment
      }     // process one line
    }

    // To support sub threads we need to examine all of the flags better than this!
    //if(alarmmem > 0) 
    //  roleVdlCrontab.append("chkCWB alarm ^ ").append(alarmmem).append(" ").append(alarmArgs).append(" >>LOG/chkcwb.log 2>&1\n");
  }

  private static String getQArgs(String l) {
    String[] p = l.split(":");
    int pos = p[2].indexOf(">");
    String qsArgs;
    if (pos > 0) {
      qsArgs = p[2].substring(0, pos);
    } else {
      qsArgs = p[2];
    }
    return qsArgs;
  }

  private void doRing(StringBuilder instancecrontab, String[] parts, int inst, String instance) {
    boolean found = false;
    int istart = (parts[6].contains("chkJar") ? 8 : 9);
    instancecrontab.append(parts[istart]).append(" ");    // Add the memory
    istart = istart + 1;
    if (parts[6].contains("Tag")) {
      instancecrontab.append("-tag ").append(parts[istart + 1]).append(" ");
      istart += 2;
    }
    for (int i = istart; i < parts.length; i++) {
      parts[i] = parts[i].replaceAll("'", "");
      if (parts[i].equals("\"\"")) {
        continue;  // skip ""
      }
      if (parts[i].equals("-ring") || parts[i].equals("'-ring")) {
        String ring = parts[i + 1].replaceAll(".ring", "");
        if (inst == 0) {     // It might be zero or it might be blank
          if (ring.endsWith(instance)) {
            if (dbg) {
              Util.prt("*Ring file already uses instance! " + parts[i + 1] + " " + instance);
            }
            instancecrontab.append("-ring ").append(ring.replaceAll(instance, "")).
                    append("$INSTANCE").append(".ring ");
            moveRing.append("mvring.bash ").append(parts[i + 1]).append(" ").append(instance).append("\n");
          } else if (!ring.endsWith("" + inst)) {
            instancecrontab.append("-ring ").append(ring).append("$INSTANCE").append(".ring ");
            moveRing.append("mvring.bash ").append(parts[i + 1]).append(" ").append(instance).append("\n");
            found = true;
          } else {
            instancecrontab.append("-ring ").append(ring.substring(0, ring.length() - 1)).
                    append("$INSTANCE").append(".ring ");
            moveRing.append("mvring.bash ").append(parts[i + 1]).append(" ").append(instance).append("\n");
          }
          found = true;
        } else {   // ring ends with instance number
          if (ring.endsWith(instance)) {
            if (dbg) {
              Util.prt("*Ring file already uses instance! " + parts[i + 1] + " " + instance);
            }
            instancecrontab.append("-ring ").append(ring.replaceAll(instance, "")).
                    append("$INSTANCE").append(".ring ");
          } else {
            instancecrontab.append("-ring ").append(ring.substring(0, ring.length() - 1)).
                    append("$INSTANCE").append(".ring ");
            moveRing.append("mvring.bash ").append(parts[i + 1]).append(" ").append(instance).append("\n");
          }
          found = true;
        }
        i++;        // skip the ring file name 
      } else {
        if (parts[i].contains(">")) {
          instancecrontab.append("\n");
          break;
        }
        instancecrontab.append(parts[i]).append(" ");
      }
    }     // looping over the RRPClient line
    if (!found) {
      StringBuilder line = new StringBuilder(100);
      for (String part : parts) {
        line.append(part).append(" ");
      }
      Util.prt("** crontab for " + instance + " line=" + line + " cannot substitute $instance");
    }
  }

  public static String deq(String s) {
    // remove all backslashes and backslash any single quotes.
    if (s == null) {
      return "Null";
    }
    return s.replaceAll("\\\\", "").replaceAll("\\'", "''");
  }

  public static void main(String[] args) {
    Util.init("edge.prop");
    Util.prt("");
    Util.setNoInteractive(true);
    String toDo = "";
    int istart = 0;
    Util.setProperty("DBServer", "localhost/3306:edge:mysql:edge");  // DEBUG: override config
    if (args.length <= 0) {    // Manual runs at the USGUS used this
      args = "volc1 volc2 pwb18".split("\\s");
      String done = "dcwb1 edge5 cedg9 dedg7 dedg8 acqdb edge1 edge2 edge3 edge4 cwbhy cwbrs gaux8 cwbpub dpqlx acqdb";
      Util.setProperty("DBServer", "136.177.24.152/3306:edge:mysql:edge");  // DEBUG: override config
    } else {
      for (int i = 0; i < args.length; i++) {
        if (args[i].equalsIgnoreCase("-db")) {
          Util.setProperty("DBServer", args[i + 1]);
          i++;
          istart = i + 1;
        }
      }
    }
    try {
      Util.prt("EMIC: connect to " + Util.getProperty("DBServer"));
      //if(!Util.getProperty("DBServer").contains("localhost")) {Util.prt("THis has to be run on localhost"); System.exit(1);}

      DBConnectionThread tmp
              = new DBConnectionThread(Util.getProperty("DBServer"), "update", "edge", true, false, "edgeConvert", Util.getOutput());

      if (!DBConnectionThread.waitForConnection("edgeConvert")) {
        if (!DBConnectionThread.waitForConnection("edgeConvert")) {
          if (!DBConnectionThread.waitForConnection("edgeConvert")) {
            Util.prt("Did not promptly connect to edge from EdgemomInstanceConvert");
            System.exit(1);
          }
        }
      }
      Util.prt("** EdgemomConfigThread: connect to " + Util.getProperty("DBServer"));

      for (int i = istart; i < args.length; i++) {
        String arg = args[i];
        EdgeMomInstanceConvert convertor = new EdgeMomInstanceConvert(tmp);
        convertor.doInstancesRoles(arg);
        Util.prt("MoveRing : \n" + convertor.getMoveRing());
        try {
          Util.writeFileFromSB(System.getProperty("user.home") + System.getProperty("file.separator") + arg + "_mvring.bash", convertor.getMoveRing());
        } catch (IOException e) {
          e.printStackTrace();
        }
      }
    } // we never got an DB connection or a runtime occurred.
    catch (InstantiationException e) {
      Util.prta("**** Instantiation getting (impossible) anssro e=" + e.getMessage());
    } catch (RuntimeException e) {
      e.printStackTrace();
    }
    Util.prt("End of execution");
    System.exit(0);
  }
}
