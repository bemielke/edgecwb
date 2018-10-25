/*
 *  This software is in the public domain because it contains materials 
 *  that originally came from the United States Geological Survey, 
 *  an agency of the United States Department of Interior. For more 
 *  information, see the official USGS copyright policy at 
 *  http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.alarm;

import gov.usgs.anss.db.DBConnectionThread;
import gov.usgs.anss.edgethread.EdgeThread;
import gov.usgs.anss.util.Subprocess;
import gov.usgs.anss.util.Util;
import gov.usgs.edge.config.EdgemomConfigFiles;
import gov.usgs.edge.config.Role;
import java.io.*;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.TreeMap;

/**
 * This is a 24/7 thread which monitors changes in the edge database for changes
 * that require reconfiguration of an edge/cwb node. It is run as part of the
 * MakeVsatRoute process on nodes that need auto-configuration based on the
 * database. It insures the the 1) crontab for each active account is correct,
 * 2) builds edgemom.setup files when the threads configured change, 3) Moves
 * all role_* groups_* files to the right accounts, 4) Moves edge.prop files to
 * all active accounts (ones with non-zero crontabs), 5) insures the IPs
 * associated with the assigned roles are up and operating on this node and that
 * any roles not assigned to this node are not being offered.
 * <p>
 * The crontab logic was expanded to allow tags in the crontab to control the
 * jobs that must be run as vdl, should only be run if the crontab is assiged to
 * the vdl account, or which should be run in the assigned account.
 * <p>
 * When this is not run as root the account which it is in has to have
 * permission in sudo to user kill and crontab on the other accounts. On NEIC
 * systems this is done like :
 * <p>
 * vdl ALL = (vdl,vdl1,vdl2,vdl3,vdl4,reftek,metadata,sysop) NOPASSWD:
 * /usr/bin/kill,/user/bin/crontab
 * <p>
 *
 *
 *
 * @author davidketchum
 */
public final class EdgemomConfigThread extends EdgeThread {

  private static final TreeMap<String, Role> rolesToIP = new TreeMap<>();
  private DBConnectionThread dbconnedge;
  private static boolean dbg = false;
  private final PanicThread panicThread;

  /**
   * set the debug flag
   *
   * @param t If true, debug output is turned on
   */
  public static void setDebug(boolean t) {
    dbg = t;
  }

  @Override
  public void terminate() {
    terminate = true;
    interrupt();
    dbconnedge.terminate();
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
   * This method loads/reloads the TreeMap rolesToIP so that the role name to
   * Role object map is maintained. It reads in the role table and loads or
   * reloads every role into the tree map.
   *
   */
  public final void loadRolesToIP() {
    try {
      try (ResultSet rs = dbconnedge.executeQuery("SELECT * FROM edge.role")) {
        while (rs.next()) {
          Role r = rolesToIP.get(rs.getString("role"));
          if (r == null) {
            r = new Role(rs);
            rolesToIP.put(rs.getString("role"), r);
          } else {
            r.reload(rs);
          }
        }
      }
    } catch (SQLException e) {
      prta("ECT: could not load roles to IP e=" + e);
    }
  }

  /**
   * construct a new EdgemomConfigTrhead and start it.
   *
   * @param argline The command arg line
   * @param tg The logging tag
   */
  public EdgemomConfigThread(String argline, String tg) {
    super(argline, tg);
    setDaemon(true);
    prta("EdgemomConfigThread started");
    EdgemomConfigFiles.setParent((EdgeThread) this);

    panicThread = new PanicThread("EdgemomConfig Panic", 240);
    try {
      if (DBConnectionThread.getThread("anss") == null) {
        DBConnectionThread tmp = new DBConnectionThread(Util.getProperty("DBServer"), "update", "anss",
                false, false, "anss", EdgeThread.getStaticPrintStream());
        EdgeThread.addStaticLog(tmp);
        if (!DBConnectionThread.waitForConnection("anss")) {
          if (!DBConnectionThread.waitForConnection("anss")) {
            if (!DBConnectionThread.waitForConnection("anss")) {
              prt("Did not promptly connect to anss from EdgemomConfigThread");
            }
          }
        }
      }
    } catch (InstantiationException e) {
      prta("Instantiation getting (impossible) edgero e=" + e.getMessage());
    }
    try {
      if (DBConnectionThread.getThread("edge") == null) {
        DBConnectionThread tmp = new DBConnectionThread(Util.getProperty("DBServer"), "update", "edge",
                false, false, "edge", getPrintStream());
        EdgeThread.addStaticLog(tmp);

        if (!DBConnectionThread.waitForConnection("edge")) {
          if (!DBConnectionThread.waitForConnection("edge")) {
            if (!DBConnectionThread.waitForConnection("edge")) {
              prt("Did not promptly connect to edge from EdgemomConfigThread");
            }
          }
        }
      }
    } catch (InstantiationException e) {
      prta("Instantiation getting (impossible) edge e=" + e.getMessage());
    }
    dbconnedge = DBConnectionThread.getThread("edgeconfig");
    if (dbconnedge == null) {
      try {
        prt("MakeEtcHost: connect to " + Util.getProperty("DBServer"));
        dbconnedge = new DBConnectionThread(Util.getProperty("DBServer"), "update", "edge",
                false, false, "edgeConfig", EdgeThread.getStaticPrintStream());
        EdgeThread.addStaticLog(dbconnedge);
        if (!DBConnectionThread.waitForConnection("edgeConfig")) {
          if (!DBConnectionThread.waitForConnection("edgeConfig")) {
            if (!DBConnectionThread.waitForConnection("edgeConfig")) {
              prt("Did not promptly connect to edgeConfig from EdgemomConfigThread");
            }
          }
        }
        prt("EdgemomConfigThread: connect to " + Util.getProperty("DBServer") + dbconnedge);
      } catch (InstantiationException e) {
        prta("Instantiation getting (impossible) anssro e=" + e.getMessage());
        dbconnedge = DBConnectionThread.getThread("edgeconfig");
      }
    }
    loadRolesToIP();
    start();

  }

  /**
   * Parse the accounts present on the path (normally /home) and make a list of
   * legal accounts. If path is illegal or is not a directly, only the vdl
   * account is returned. All vdl accounts are returned along with metadata,
   * reftek.
   *
   * @param path The path - normally /home
   * @return The arraylist of strings with all of the accounts on the path
   */
  private ArrayList<String> parseAccounts(String path) {
    File home = new File(path);
    ArrayList<String> accts = new ArrayList<>(10);
    if (!home.exists() || !home.isDirectory()) {
      prt("Accounts are not in /home - assume only vdl");
      accts.add("vdl");
      return accts;

    }
    File[] accounts = home.listFiles();
    for (File account : accounts) {
      if (account.getName().startsWith("vdl") || account.getName().startsWith("reftek")
              || account.getName().startsWith("metadata")) {
        accts.add(account.getName());
        if (dbg) {
          prt("ParseAccounts : " + account.getName());
        }
      }
    }
    return accts;
  }

  /**
   * return the roles currently running on this computer according to the
   * /vdl/home/roles_NODE file
   *
   * @param filename File name to parse for roles
   * @return Array of strings with each role currently on this node.
   */
  public String[] parseRoles(String filename) {
    String[] roles = null;
    //prt("call getroles returns="+System.getenv("VDL_ROLES"));
    String line;
    try {
      try (BufferedReader in = new BufferedReader(new FileReader(filename))) {
        while ((line = in.readLine()) != null) {
          if (line.length() > 9) {
            if (line.substring(0, 9).equals("VDL_ROLES")) {
              line = line.substring(11).replaceAll("\"", "");
              roles = line.split("[\\s,]");
              for (int i = 0; i < roles.length; i++) {
                roles[i] = roles[i].trim();
                //prt("get roles read files returns roles "+i+" "+roles[i]);
              }
              break;
            }
          }
        }
      }
    } catch (FileNotFoundException e) {
      prt("**** Did not find file =" + filename + " continue without it");
      //Util.IOErrorPrint(e,"ECT: Trying to read "+filename);
    } catch (IOException e) {
      Util.IOErrorPrint(e, "ECT: Trying to read " + filename);
    }
    return roles;
  }

  @Override
  public void run() {
    try {
      sleep(600);
    } catch (InterruptedException expected) {
    }
    File edgemomdir = new File("EDGEMOM");
    if (!edgemomdir.exists() || !edgemomdir.isDirectory()) {
      prta("ECT: This node has not EDGEMOM directory and cannot be managed");
      return;
    }

    EdgemomConfigFiles.makeAllFiles(null);  // generate all of the configuration files
    String node = Util.getSystemName();
    int thisCpuID = 0;
    try {
      try (ResultSet rs = dbconnedge.executeQuery("SELECT ID FROM edge.cpu where cpu='" + node + "'")) {
        if (rs.next()) {
          thisCpuID = rs.getInt("ID");
        }
      }
    } catch (SQLException e) {
      Util.SQLErrorPrint(e, "Trying to get cpuID for node=" + node);
    }
    prta("ECT: starting for node=" + node + " cpuid=" + thisCpuID);
    String[] args = new String[0];
    String[] assignedRoles = Util.getRoles(null);
    moveFiles(assignedRoles, node);
    String[] nowRoles = parseRoles("EDGEMOM" + Util.fs + "roles_" + node);
    configRoles(assignedRoles, nowRoles);
    assignedRoles = nowRoles;
    chkAllCrontabs(assignedRoles);
    boolean hasData;
    int loopCount = 0;
    boolean foundRole = false;
    prta("ECT: Initial setup complete.  Start loop roles[0]=" + assignedRoles[0]);
    while (!terminate) {       // this runs forever!
      if (dbg || (loopCount % 20) == 1) {
        prta(Util.ascdate() + " ECT: loop " + loopCount);
      }
      panicThread.setArmed(true);
      try {
        hasData = false;      // Assume no hasdata flags in cpu or in any assigned roles
        ResultSet rs = dbconnedge.executeQuery("SELECT hasdata FROM edge.cpu WHERE cpu='" + node + "'");
        if (rs.next()) {
          hasData = rs.getInt("hasdata") == 1;
        }
        rs.close();
        if (hasData) {
          dbconnedge.executeUpdate("UPDATE edge.cpu SET hasdata=0 WHERE cpu='" + node + "'");
        }
        for (String assignedRole : assignedRoles) {
          rs = dbconnedge.executeQuery("SELECT hasdata FROM edge.role where role='" + assignedRole + "'");
          if (rs.next()) {
            hasData |= rs.getInt("hasdata") == 1;
          }
          rs.close();
          if (hasData) {
            dbconnedge.executeUpdate("UPDATE edge.role SET hasdata=0 where role='" + assignedRole + "'");
          }
        }

        // The CPU or one of the roles requires a reconfiguation pass, do it.
        if (hasData || foundRole) {     // something has changed, run the procedure
          prta(Util.ascdate() + " ECT: on " + node + " has data is true.  Run configuration foundRole=" + foundRole);

          foundRole = false;            // we have handled a found role
          if (dbg) {
            prta("ECT: Make all files");
          }
          EdgemomConfigFiles.makeAllFiles(null);  // generate all of the configuration files
          nowRoles = parseRoles("EDGEMOM" + Util.fs + "roles_" + node);// What roles do we have now!
          boolean newRole = false;
          if (dbg) {
            for (int i = 0; i < assignedRoles.length; i++) {
              prta(i + " Assigned Role=" + assignedRoles[i] + "|");
            }
            for (int i = 0; i < nowRoles.length; i++) {
              prta(i + " Now Role=" + nowRoles[i] + "|");
            }
          }

          if (assignedRoles.length != nowRoles.length) {
            newRole = true;
          } else {
            for (int i = 0; i < assignedRoles.length; i++) {
              if (!assignedRoles[i].equals(nowRoles[i])) {
                newRole = true;
              }
            }
          }
          if (newRole) {
            prta("ECT: Need to configure roles.  They are different!");
            configRoles(nowRoles, assignedRoles);      // configure these roles on this node
            assignedRoles = nowRoles;   // Make our assigned roles reflect the changes
          }

          // The roles must now be up,  Now move the files from EDGEMOM directory to where they belong for this node/role
          moveFiles(assignedRoles, node);
          chkAllCrontabs(assignedRoles);
          if (dbg) {
            prta("ECT: configuration pass complete!");
          }
        }
        loopCount++;

        // If the roles change or periodically check all roles and insure one not on this node, are not configured
        // If the above logic worked, this should have no effect but if a role came up in a bad way it might
        if (hasData || loopCount % 30 == 3 || loopCount == 1) { // if changed, periodically or first time
          foundRole = chkDroppedRoles(assignedRoles, foundRole, thisCpuID);
          chkAllCrontabs(assignedRoles);
        }
      } catch (SQLException e) {
        Util.SQLErrorPrint(e, "ECT: Error getting node - retry");
      }
      panicThread.setArmed(false);
      try {
        sleep(60000);
      } catch (InterruptedException expected) {
      }
    }
    prta("ECT: has terminated=" + terminate);
  }

  /**
   * For all accounts on this computer, check that the crontabs are right and as
   * expected. This routine builds a list of accounts needed on this computer
   * for all of the roles assigned to this cpu and then finds all of the
   * crontabs in the edgefile table. It then parses the content into the vdlcron
   * portion based on
   * <br>
   * #
   * <vdlonly> (run these threads if this is vdl account)
   * #<alwaysvdl> (run these thread in the vdl account regardless of which
   * account this cront is for
   * #<account> (run these threads in the account)
   *
   * It also set null accounts into any accounts on this computer which are not
   * active accounts
   *
   * @param assignedRoles The roles currently assigned on this CPU node
   */
  private void chkAllCrontabs(String[] assignedRoles) {
    StringBuilder vdlcron = new StringBuilder(200);   // to contain everything that runs as vdl
    ArrayList<String> allAccounts = parseAccounts(Util.fs + "home");
    // Figure out which accounts are suppose to be active on this node
    ArrayList<String> activeAccounts = new ArrayList<>(10);

    try {
      Statement stmt2 = dbconnedge.getConnection().createStatement();
      for (int i = 0; i < assignedRoles.length; i++) {
        if (dbg) {
          prta(" chkAllCrontab for assigned role=" + assignedRoles[i]);
        }
        try (ResultSet rs = dbconnedge.executeQuery("SELECT accounts FROM edge.role WHERE role='" + assignedRoles[i] + "'")) {
          if (rs.next()) {
            String acct = rs.getString("accounts");
            if (dbg) {
              prta(" chkAllCrontabs: active accounts for " + assignedRoles[i] + ":" + acct);
            }
            acct = acct.replaceAll("  ", " ").replaceAll("  ", " ");
            String[] accts = acct.split(" ");
            for (String acct1 : accts) {
              activeAccounts.add(acct1);
              try (ResultSet rs2 = stmt2.executeQuery("SELECT * FROM edge.edgefile WHERE edgefile='crontab." + assignedRoles[i] + "." + acct1 + "'")) {
                while (rs2.next()) {
                  String content = rs2.getString("content");
                  boolean alwaysvdl = false;
                  boolean vdlonly = false;
                  boolean account = false;
                  StringBuilder acctcron = new StringBuilder(200);
                  try {
                    BufferedReader in = new BufferedReader(new StringReader(content));
                    String line;
                    while ((line = in.readLine()) != null) {
                      line = line.trim();
                      if (line.indexOf("#<vdlonly>") == 0) {
                        vdlonly = true;
                        alwaysvdl = false;
                        account = false;
                        vdlcron.append("#<vdlonly> ").append(assignedRoles[i]).append(" ").append(acct1).append("\n");
                        //continue;
                      } else if (line.indexOf("#<alwaysvdl>") == 0) {
                        vdlonly = false;
                        alwaysvdl = true;
                        account = false;
                        vdlcron.append("#<alwaysvdl> ").append(assignedRoles[i]).append(" ").append(acct1).append("\n");
                        //continue;
                      } else if (line.indexOf("#<account>") == 0) {
                        vdlonly = false;
                        alwaysvdl = false;
                        account = true;
                        acctcron.append("#<account> ").append(assignedRoles[i]).append(" ").append(acct1).append("\n");
                        //continue;
                      } else {
                        if (vdlonly && accts[i].equals("vdl")) {
                          vdlcron.append(line).append("\n");
                        } else if (alwaysvdl) {
                          vdlcron.append(line).append("\n");
                        } else if (account) {
                          acctcron.append(line).append("\n");
                        }
                      }
                    }
                    // we have sorted the lines into acctcron and put some in vdlcron
                    if (acct1.equals("vdl")) {
                      vdlcron.append(acctcron.toString()); // add the account stuff to vdl cront
                    } else {
                      prta("Call setCrontab() 1!" + acct1);
                      setCrontab(assignedRoles[i], acct1, acctcron.toString());
                      prta("Call setCrontab() 1 rtn!" + acct1);
                    } // Set this account crontab account only portion;
                  } catch (IOException e) {
                    e.printStackTrace();    // Totally unexpected!
                  }
                }
              }
            } // end for on accts
          }  // if rs.next() on roles.
        }
      }     // for on assigned roles
    } catch (SQLException e) {
      Util.SQLErrorPrint(e, "Trying to get accounts");
      return;
    }

    // Load the vdl account from eventhing put in vdlcront.
    try {
      prta("Load vdl crontab ");
      setCrontab(assignedRoles[0], "vdl", vdlcron.toString());
      prta("Load vdl crontab rtn");
    } // Set the vdl account for all of the always and vdlonly lines
    catch (IOException e) {
      prta("ECT: IOError trying to set VDL crontab - this is bad!");
    }

    // Now all active accounts must have an edge.prop file
    for (String allAccount : allAccounts) {
      boolean isActive = false;
      for (String activeAccount : activeAccounts) {
        if (activeAccount.equals(allAccount)) {
          isActive = true;
        }
      }
      if (allAccount.equals("vdl") && vdlcron.length() > 15) {
        if (!isActive) {
          prta("ECT: FOUND vdl account with non-zero vdlcron =" + allAccount + " crontab=" + vdlcron);
        }
        isActive = true;
      }
      if (!isActive) {
        // This account is not active, insure the crontab is empty
        try {
          prta("setCrontab for inactive account " + allAccount);
          setCrontab(assignedRoles[0], allAccount, "# Account " + allAccount + " is not active\n");
          prta("setCrontab return for inactive account " + allAccount);
        } catch (IOException e) {
          prta("ECT: IOError trying to set empty crontab to account=" + allAccount);
        }
      }
    }
    prta("ECT: Leaving chkAllCrontabs");
  }

  /**
   * Look through the roles currently on this computer and if they are not
   * assigned to this CPU, drop them. Also look for roles that are assigned, but
   * not up on this CPU. This routine should never do much unless the role
   * assignments have been fouled up by reboot.
   *
   * @param assignedRoles The roles that should be on this computer.
   * @param foundRole
   * @param thisCpuID The server CPU id
   * @return true if there appears to be a role assigned, but not being done by
   * this cpu
   */
  private boolean chkDroppedRoles(String[] assignedRoles, boolean foundRole, int thisCpuID) {
    loadRolesToIP();
    Iterator<String> itr = rolesToIP.keySet().iterator();
    while (itr.hasNext()) {
      String role = itr.next();
      String ipadr = rolesToIP.get(role).getIP();
      try {
        InetAddress adr = InetAddress.getByName(ipadr);
        NetworkInterface ni = NetworkInterface.getByInetAddress(adr);
        ipadr = adr.getHostAddress();  // remove the annoying leading zeros
        boolean isAssigned = false;
        if (ni != null) {
          for (String assignedRole : assignedRoles) {
            if (role.equalsIgnoreCase(assignedRole)) {
              isAssigned = true; // we are assigned this, do not take it down
            }
          }
          // Need to make sure it is not responding on another node, ping it
          if (!isAssigned) {
            prta("ECT: Need to drop role " + role + " not assigned to this node.");
            drop_ip(role);
          }
        } else {
          // It is not on this node, should it be?
          if (rolesToIP.get(role).getCpuID() == thisCpuID) {
            prta("** Unusual - found a role assigned to this node, but without has data triggered! role=" + role + " cpuid=" + thisCpuID);
            foundRole = true;
          } else if (dbg) {
            prta("ECT: check of role=" + role + "/" + ipadr + " is not on this host.  O.K.");
          }
        }
      } catch (UnknownHostException e) {
        prta("ECT: ** Checking role=" + role + " threw UnknownHost e=" + e);
      } catch (SocketException e) {
        prta("ECT: ** Checking roles=" + role + " threw SocketError e=" + e);
      }
    }
    return foundRole;
  }

  /**
   * move the configuration files for this node and set of roles to their new
   * homes. Note: the edgemom.setup, roles_ and groups_ files have been named
   * with the cpu node when they were created by the EdgemomConfigFiles class -
   * however, the side files and crontab files have names based on the roles.
   *
   * @param roles The list of roles being used on this computer
   * @param node The server or CPU name.
   *
   */
  private void moveFiles(String[] roles, String node) {

    ArrayList<String> accounts = new ArrayList<>(10);
    String targetHome = Util.fs + "home" + Util.fs;
    String configDir = Util.fs + "home" + Util.fs + "vdl" + Util.fs + "EDGEMOM";
    if (node.equals("gldketchum3") || node.equals("igskcicgwsgm044")) {
      targetHome = Util.fs + "Users" + Util.fs + "ketchum" + Util.fs + "TESTING" + Util.fs;
      configDir = Util.fs + "Users" + Util.fs + "ketchum" + Util.fs + "TESTING" + Util.fs + "vdl" + Util.fs + "EDGEMOM";
      node = "igskcicgwsgm044";
      roles[0] = "gldketchum3";
    }
    // Directory of the configuration directory
    if (dbg) {
      prta("** start moveFiles node=" + node);
    }
    File dir = new File(configDir);
    String edgemomStub = "edgemom.setup-" + node.trim() + "-";    // What do edgemom.setups look like
    File[] files = dir.listFiles();
    for (File file : files) {
      try {
        if (file.getName().contains(edgemomStub)) {
          String account = file.getName().substring(file.getName().lastIndexOf("-") + 1);
          accounts.add(account);
          copyTo(file, targetHome + account + Util.fs + file.getName().substring(0, file.getName().indexOf("-")));
        }
        if (file.getName().equals("roles_" + node.trim())) {
          copyTo(file, targetHome + "vdl" + Util.fs + file.getName());
        }
        if (file.getName().equals("groups_" + node.trim())) {
          copyTo(file, targetHome + "vdl" + Util.fs + file.getName());
        }
      } catch (IOException e) {
        prt("IOException e=" + e);
        e.printStackTrace();
        SendEvent.edgeSMEEvent("BadRoleGrp", "IOError setting roles or group or edgemom.setup!", this);
      }
    }
    try {
      for (String role : roles) {
        if (role.trim().equals("")) {
          continue;
        }
        if (dbg) {
          prta(" ** do role=" + role);
        }
        for (String account : accounts) {
          if (dbg) {
            prta(" ** do role=" + role + " account=" + account);
          }
          // check on the edge.prop for this role and account
          File edgeProp = new File(configDir + Util.fs + "edge.prop." + role.trim() + "." + account.trim());
          if (edgeProp.exists()) {
            try {
              copyTo(edgeProp, targetHome + account.trim() + Util.fs + "edge.prop");
            } catch (IOException e) {
              prta(" ***** Did not copy a edge.prop file e=" + e);
            }
          } else {
            edgeProp = new File(configDir + Util.fs + "edge.prop." + node.trim() + "." + account.trim());
            prta(" * did not find edge.prop.role try " + configDir + Util.fs + "edge.prop." + node.trim() + "." + account.trim() + " exist=" + edgeProp.exists());
            if (edgeProp.exists()) {
              try {
                copyTo(edgeProp, targetHome + account.trim() + Util.fs + "edge.prop");
              } catch (IOException e) {
                prta(" **** did not copy a edge.prop file2 e=" + e);
              }
            } else {
              prta(" ***** Did not find an edge.prop for " + role + "." + account);
            }
          }
          copyExtra(configDir, role.trim(), account.trim());
          try (ResultSet rs = dbconnedge.executeQuery("SELECT edgefile FROM edge.edgemomsetup,edge.role,edge.edgefile WHERE role.id=roleid AND role.role='" + role + "' AND account='" + account + "' AND edgefile.id=edgefileid AND edgefileid != 0 AND NOT edgefile.edgefile regexp 'NONE'")) {
            while (rs.next()) {
              File config = new File(configDir + Util.fs + rs.getString("edgefile"));
              if (dbg) {
                prta("   ** looking for file : " + configDir + Util.fs + rs.getString("edgefile"));
              }
              if (config.exists()) {
                try {
                  copyTo(config, targetHome + account + Util.fs + rs.getString("edgefile"));
                } catch (IOException e) {
                  prta("ECT: Failed to copy " + config + " to " + targetHome + account + Util.fs + rs.getString("edgefile"));
                  SendEvent.edgeSMEEvent("CopyFail", "Config copy filed on " + role + " for " + rs.getString("edgefile"), this);
                }
              } else {
                SendEvent.edgeSMEEvent("NoEdgeFile", "Expected edgefile (config) does not exist!" + rs.getString("edgefile"), this);
                prta("ECT: Expected configfile does not exist " + config.getAbsolutePath());
              }
            }
          }
        }
      }
    } catch (SQLException e) {
      Util.SQLErrorPrint(e, "Getting crontab edge files ");
    }

  }

  private void copyExtra(String configDir, String role, String acct) {
    // check on a queryserver.prop file for this role and account
    File configs = new File(configDir);
    //prt("consider directory "+configDir+" "+role+" "+acct);
    //prta("Configs : "+configDir);
    //prta(" isDir="+configs.isDirectory());
    File[] filenames = configs.listFiles();
    for (File filename : filenames) {
      if (filename.isDirectory()) {
        copyExtra(filename.getAbsolutePath(), role, acct);
      }
      String fname = filename.getAbsolutePath();
      //prt("consider file="+fname);

      int l = fname.lastIndexOf("EDGEMOM" + Util.fs);
      if (l > 0) {
        fname = fname.substring(l + 8);
      }
      if (fname.endsWith(role + "." + acct)) {
        String target = Util.fs + "home" + Util.fs + acct + Util.fs + fname.replaceAll("." + role + "." + acct, "");
        try {
          if (fname.startsWith("crontab")) {
            prta("Skip copying extra file " + fname);
            continue;
          }
          copyTo(filename, target);
        } catch (IOException e) {
          prta(" ***** Did not copy " + fname + " to /home/" + acct + " e=" + e);
        }
        prta("copying extra file " + fname + " to " + target);
      }
    }
  }
  byte[] copyBuf = new byte[100000];

  /**
   * copy the given File to the given path. The path must exist for this to be
   * successful
   *
   * @param source The source File to copy
   * @param outfilename The full path to the file to be copied
   * @return
   * @throws java.io.IOException Usually if the output file path does not exist.
   */
  private boolean copyTo(File source, String outfilename) throws IOException {
    if (dbg) {
      prt("  *** copy " + source.getAbsolutePath() + " to " + outfilename);
    }
    Util.chkFilePath(outfilename);
    try (RandomAccessFile raw = new RandomAccessFile(source, "r");
            RandomAccessFile outtmp = new RandomAccessFile(outfilename, "rw")) {
      if (copyBuf.length < raw.length()) {
        copyBuf = new byte[(int) raw.length() * 2];
      }
      raw.seek(0L);
      raw.read(copyBuf, 0, (int) raw.length());
      outtmp.seek(0L);
      outtmp.write(copyBuf, 0, (int) raw.length());
      outtmp.setLength(raw.length());
    }
    return true;
  }
  String lastcron = "";
  TreeMap<String, String> lastcrons = new TreeMap<>();

  private boolean setCrontab(String role, String account, String content) throws IOException {

    // read the crontab until it is the same consequtively
    String cron = "";
    String cron2 = "$";
    int loops = 0;
    if (role.equals("gldketchum3")) {
      return false;
    }
    String croncmd = "sudo -u " + account + " crontab -l";
    if (Util.getAccount().equals(account)) {
      croncmd = "crontab -l";
    }
    if (Util.getOS().contains("olaris") || Util.getOS().contains("Sun")) {
      croncmd = "crontab -l " + account;
    }
    lastcron = lastcrons.get(account);
    if (lastcron == null) {
      lastcron = "";
    }
    prta("OS=" + Util.getOS() + " croncmd=" + croncmd + " " + account + " lastcron.len=" + lastcron.length()
            + " content.len=" + content.length());
    while (!lastcron.equals(cron) || !cron2.equals(cron) || lastcron.equals("")) {
      lastcron = cron;
      Subprocess p = new Subprocess(croncmd);
      try {
        p.waitFor();
      } catch (InterruptedException expected) {
      }
      try {
        sleep(1000);
      } catch (InterruptedException expected) {
      }
      cron = p.getOutput();

      if (loops++ > 10) {
        break;
      }
      if (loops > 3) {
        prta(" **** get setCrontab() for role=" + role + " account=" + account + " loop=" + loops + " is crontab empty?");
      }
      // 
      Subprocess p2 = new Subprocess(croncmd);
      try {
        p2.waitFor();
      } catch (InterruptedException expected) {
      }
      try {
        sleep(1000);
      } catch (InterruptedException expected) {
      }
      cron2 = p2.getOutput();
      if (!cron2.equals(cron)) {
        lastcron += "$";
        prta(" ** cron did not reproduce - try again acct=" + account + " loop=" + loops + "\n1st:" + cron + "\n2nd:" + cron2);
      } else {
        prta("cron and cron2 are the same! " + account + " lastcron.equals=" + lastcron.equals(cron)
                + " cron.len=" + cron.length() + " lastcron.len=" + lastcron.length()
                + " while=" + (!lastcron.equals(cron) || !cron2.equals(cron) || lastcron.equals("")));
        if (cron.length() == 0 && content.contains("is not active")) {
          break;
        }
      }
    }
    prta("got crontab for role=" + role + " account=" + account + " len=" + lastcron.length());
    // Is it the same as the configuration?
    if (lastcron.equals(content)) {
      prta(account + " crontab is the same2. return");
      return true;
    }
    lastcrons.put(account, content);

    // Anytime we set a crontab, we need to set and edge.prop if present
    if (!content.contains("is not active")) {
      File edgeProp = new File("EDGEMOM" + Util.fs + "edge.prop." + role + "." + account);
      if (edgeProp.exists()) {
        try {
          copyTo(edgeProp, Util.fs + "home" + Util.fs + account.trim() + Util.fs + "edge.prop");
        } catch (IOException e) {
          prta(" ***** Did not copy a edge.prop file e=" + e);
        }
      } else {
        prta(" ***** Did not find an edge.prop for " + role + "." + account);
      }
    }

    prt("   *** cram crontab for account=" + account + " loops=" + loops + " match=" + cron.equals(lastcron)
            + "\nWas:" + lastcron + "\nNow:\n" + content + "\n# <EOF>");

    // look for lines that are no longer in the desired crontab - and kill them ! 
    BufferedReader in = new BufferedReader(new StringReader(lastcron));
    String line;
    try {
      while ((line = in.readLine()) != null) {
        if (!content.contains(line)) {
          if (dbg) {
            prta("Need to kill line=" + line);
          }

          if (line.contains("chkJarProcess") || line.contains("chkCWB")) {
            line = line.replaceAll("  ", " ").replaceAll("  ", " ").replaceAll("  ", " ");
            String[] parts = line.split(" ");
            for (int i = 0; i < parts.length; i++) {
              if (parts[i].contains("chkJarProcessGCTag") || parts[i].contains("chkJarProcessGCTag")) {
                Subprocess sp = new Subprocess("bash " + Util.fs + "home" + Util.fs + "vdl" + Util.fs + "vdl" + Util.fs + "SCRIPTS" + Util.fs + "killgen " + parts[i + 4]);
                try {
                  sp.waitFor();
                } catch (InterruptedException e) {
                }
                //if(dbg) 
                prt("kill TAG " + parts[i + 4] + "   returns" + sp.getOutput() + sp.getErrorOutput());
                break;
              } else if (parts[i].contains("chkJarProcess")) {
                Subprocess sp = new Subprocess("sudo -u " + account + " bash +" + Util.fs + "home" + Util.fs + "vdl" + Util.fs + "vdl" + Util.fs + "SCRIPTS" + Util.fs + "killgen " + parts[i + 1]);
                try {
                  sp.waitFor();
                } catch (InterruptedException e) {
                }
                //if(dbg) 
                prt("kill " + parts[i + 1] + "   returns" + sp.getOutput() + sp.getErrorOutput());
                break;
              } else if (parts[i].equals("chkCWB")) {
                prt(" *** chkCWB task : sudo -u " + account + " bash " + Util.fs + "home" + Util.fs + "vdl" + Util.fs + "vdl" + Util.fs + "SCRIPTS" + Util.fs + "killgen " + parts[i + 1]);
                Subprocess sp = new Subprocess("sudo -u " + account + " bash " + Util.fs + "home" + Util.fs + "vdl" + Util.fs + "vdl" + Util.fs + "SCRIPTS" + Util.fs + "killgen " + parts[i + 1]);
                try {
                  sp.waitFor();
                } catch (InterruptedException expected) {
                }
              }
            }
          }
        }
      }
    } catch (IOException e) {
      prt("IOError reading thru crontab!" + e);
    }
    if (Util.getNode().equals("gldketchum3")) {
      return false;
    }
    try (RandomAccessFile outtmp = new RandomAccessFile("crontab.tmp", "rw")) {
      lastcron = content;
      outtmp.seek(0L);
      outtmp.write(content.getBytes(), 0, content.length());
      outtmp.setLength(content.length());
    }
    if (Util.getAccount().equals(account)) {
      croncmd = "crontab crontab.tmp";
    } else {
      croncmd = "sudo -u " + account + " crontab crontab.tmp";
    }
    if (Util.getOS().contains("olaris") || Util.getOS().contains("Sun")) {
      croncmd = "sudo -u " + account + " crontab crontab.tmp";
    }
    prta("OS=" + Util.getOS() + " croncmd=" + croncmd);
    Subprocess sp = new Subprocess(croncmd);
    try {
      sp.waitFor();
    } catch (InterruptedException e) {
      prta("setCrontab gave interrupted exception");
    }
    prta("crontab set err=" + sp.getErrorOutput() + "\nstdout=" + sp.getOutput());
    return true;
  }

  /**
   * we have new roles. Make sure any new roles are down, and then bring up the
   * role.
   *
   * @param roles Array of strings with roles this node should have.
   */
  private boolean configRoles(String[] roles, String[] oldRoles) {

    // Now check each old role and insure it is still in new roles, if not, drop_ip that role.
    boolean ok = true;
    if (roles == null || oldRoles == null) {
      return true;
    }
    for (String oldRole : oldRoles) {
      for (String role : roles) {
        if (oldRole.equals("") || role.equals("")) {
          continue;
        }
        if (oldRole.equals(role)) {
          if (dbg) {
            prta("Found role " + oldRole + " in new roles.");
          }
          ok = true;
        }
      }
      if (!ok && !oldRole.trim().equals("")) {
        prta("ECT: configRoles need to drop removed role=" + oldRole);
        drop_ip(oldRole);
      }
    }
    for (String role1 : roles) {
      String ipadr = "";
      InetAddress adr = null;
      try {
        ResultSet rs = dbconnedge.executeQuery("SELECT * FROM edge.role WHERE role='" + role1 + "'");
        if (rs.next()) {       // is there such a role
          ipadr = rs.getString("ipadr");
          String role = rs.getString("role");
          adr = InetAddress.getByName(ipadr);
          NetworkInterface ni = NetworkInterface.getByInetAddress(adr);
          ipadr = adr.getHostAddress();  // remove the annoying leading zeros
          if (ni == null) {      // This is not an address on the local node
            // Need to make sure it is not responding on another node, ping it.
            Subprocess sp;
            boolean done = false;
            int loop = 0;
            prta("ECT: Waiting for new role to go down on another server. Roles=" + ipadr);
            while (!done) {
              if (Util.getOS().contains("Linux")) {
                sp = new Subprocess("ping -n -c 2 " + ipadr);
              } else {
                sp = new Subprocess("ping -n -c 2 " + ipadr); // Unix -I MacOS -i
              }              //sp = new Subprocess("ping -n -i 60 "+ip);    // Unix -I MacOS -i
              sp.waitFor();
              String s = sp.getOutput();
              if (s.indexOf("icmp_seq=") > 0 && s.contains("time=")) {
                // its still up
                loop++;
                if (loop % 10 == 0) {
                  prt("ECT: Role for ip=" + ipadr + " is still up s=" + s);
                  SendEvent.edgeSMEEvent("RoleHung", "The role at " + role + "/" + ipadr + " is not being given up", this);
                  return false;
                }
              } else {
                done = true;
              }
            }
            prta("ECT: Role is down. do become_ip " + role + "/" + ipadr);
            // The IP address is available, switch to it
            sp = new Subprocess("bash " + Util.fs + "home" + Util.fs + "vdl" + Util.fs + "ROOT" + Util.fs + "become_ip " + role);
            sp.waitFor();
            prta("Become_ip output=" + sp.getOutput() + " " + sp.getErrorOutput());
          }
        }
      } catch (SocketException e) {
        prt("ECT: Got socket exception looking up adr=" + adr);
      } catch (UnknownHostException e) {
        prt("ECT: Got unknown host looking up ip=" + ipadr);
      } catch (SQLException e) {
        Util.SQLErrorPrint(e, "ECT: Trying to get IP address for role");
      } catch (IOException e) {
        Util.IOErrorPrint(e, "ECT: Trying to become a role");
      } catch (InterruptedException e) {
        prt("Interrupted exception trying to become a role! e=" + e);
        e.printStackTrace();
      }
    }
    return true;
  }

  private boolean drop_ip(String role) {
    String ipadr = rolesToIP.get(role).getIP();
    try {
      Subprocess sp = new Subprocess("bash " + Util.fs + "home" + Util.fs + "vdl" + Util.fs + "ROOT" + Util.fs + "drop_ip " + role);
      sp.waitFor();
      prta("ECT: drop ip out=" + sp.getOutput() + " err=" + sp.getErrorOutput());
      if (ipadr == null) {
        return true;
      }
      InetAddress adr = InetAddress.getByName(ipadr);
      NetworkInterface ni = NetworkInterface.getByInetAddress(adr);
      ipadr = adr.getHostAddress();  // remove the annoying leading zeros
      if (ni == null) {      // This is not an address on the local node
        prta("ECT: drop_ip was successful for " + role + "/" + ipadr);
        return true;
      } else {
        prta("ECT: drop_ip was NOT successful for " + role + "/" + ipadr);
      }
    } catch (IOException e) {
      prta("ECT: ** Drop_IP threw IOError e=" + e);
      e.printStackTrace();
    } catch (InterruptedException e) {
      prta("ECT: ** Drop_IP threw Interrupted e=" + e);
    }
    return false;
  }

  /**
   * Test main routine
   *
   * @param args Unused command line args
   */
  public static void main(String[] args) {
    EdgeThread.setMainLogname("ect");
    Util.init("edge.prop");
    EdgemomConfigThread thr = new EdgemomConfigThread("", "ECT");
    String[] roles = {"gtest1"};
    String node = "gldketchum3";
    EdgemomConfigFiles.main(args);  // generate all of the configuration files
    thr.moveFiles(roles, node);
    Util.prt("Become roles returned=" + thr.configRoles(roles, roles));
    System.exit(0);
  }
}
