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
import gov.usgs.anss.edgemom.Version;
import gov.usgs.anss.util.Subprocess;
import gov.usgs.anss.util.Util;
import gov.usgs.edge.config.Cpu;
import gov.usgs.edge.config.EdgeMomInstance;
import gov.usgs.edge.config.EdgemomInstanceSetup;
import gov.usgs.edge.config.QueryMomInstance;
import gov.usgs.edge.config.RoleInstance;
import java.io.IOException;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.BufferedReader;
import java.io.StringReader;
import java.io.RandomAccessFile;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.TreeMap;
import java.util.Enumeration;
import java.util.Iterator;

/**
 * This is a 24/7 thread which monitors changes in the edge database for changes
 * that require reconfiguration of an edge/cwb node. It is run as part of Alarm
 * process on nodes that need auto-configuration based on the instance database.
 * It insures the the 1) crontab for each active account is correct, 2) builds
 * EDGEMOM/* files, 3) Moves all role_* groups_* files to the right cpuAccounts,
 * 4) Moves edge.prop files to all active cpuAccounts (ones with non-zero
 * crontabs), 5) insures the IPs associated with the assigned cpuRoles are up
 * and operating on this node and that any cpuRoles not assigned to this node
 * are not being offered.
 * <p>
 * The crontab logic was expanded to allow tags in the crontab to control the
 * jobs that must be run as vdl, should only be run if the crontab is assigned
 * to the vdl account, or which should be run in the assigned account.
 * <PRE>
 * switch   values    Description
 * -db     DBURL     Override the DBServer property name with this URL
 * </PRE>
 *
 * @author davidketchum
 */
public final class EdgemomConfigInstanceThread extends EdgeThread {

  static String targetHome;     // This is the directory above the home account like "/home"
  private static final TreeMap<String, Cpu> cpus = new TreeMap<>();             // From edge.cpu
  private static final TreeMap<String, RoleInstance> roleMap = new TreeMap<>(); // From edge.newRole
  private static final TreeMap<String, EdgeMomInstance> instances = new TreeMap<>();  // From edge.instance
  private static final TreeMap<String, QueryMomInstance> querymominstances = new TreeMap<>();  // From edge.instance
  private static final TreeMap<String, TreeMap<String, EdgemomInstanceSetup>> threads = new TreeMap<>();// from edge.instancesetup
  private boolean killAlarm = false;
  private String configDir;
  private DBConnectionThread dbconnedge;
  private static boolean dbg = false;
  private final String acct;                // The account being configured
  private String node;                      // This is the Util.systemName()
  private final String slash;     // This is the path separator for thi system OS
  private final String home;      // This is the home directory like "/home/vdl"
  private StringBuilder tmpsb = new StringBuilder(10000);
  private StringBuilder scratch = new StringBuilder(1000);
  private boolean oneTime;
  private boolean nPlusOne;

  //private byte [] buf = new byte[10000];
  /**
   * set the debug flag
   *
   * @param t If true, debug output is turned on
   */
  public static void setDebug(boolean t) {
    dbg = t;
  }

  @Override
  public StringBuilder getStatusString() {
    return statussb;
  }

  @Override
  public StringBuilder getMonitorString() {
    return monitorsb;
  }

  @Override
  public StringBuilder getConsoleOutput() {
    return consolesb;
  }

  @Override
  public void terminate() {
    terminate = true;
    interrupt();
    dbconnedge.terminate();
  }

  /**
   * construct a new EdgemomConfigThread and start it.
   *
   * @param argline The argument line for EdgeMomConfig thread.
   * @param tg
   */
  public EdgemomConfigInstanceThread(String argline, String tg) {
    super(argline, tg);
    this.setDaemon(true);
    prta("ECIT: *** EdgemomConfigThread (Linux instance based) start up " + Util.ascdatetime(null));
    String[] args = argline.split("\\s");
    for (int i = 0; i < args.length; i++) {
      switch (args[i]) {
        case "-db":
          Util.setProperty("DBServer", args[i + 1]);
          i++;
          break;
        //else prta("ECIT: *** Unknown switch pos="+i+" "+args[i]);
        case "-init":
          oneTime = true;
          break;
        case "-empty":
          break;
        default:
          prta("ECIT: unknown argment i=" + i + "=" + args[i]);
          break;
      }
    }
    if (Util.getProperty("NPlusOne") != null) {
      nPlusOne = true;
    }
    prta("ECIT: starting db=" + Util.getProperty("DBServer")
            + " NPlusOne=" + Util.getProperty("NPlusOne") + " onetime=" + oneTime);
    slash = System.getProperty("file.separator");

    targetHome = System.getProperty("user.home");
    configDir = targetHome + slash + "EDGEMOM" + slash;
    targetHome = targetHome.substring(0, targetHome.lastIndexOf(slash)) + slash;
    node = Util.getSystemName();      // THis is like "edge1"
    if (node.equals("gldketchum3") || node.equals("gm044") || node.equals("gm073")
            || node.equals("igskcicglgm070") || node.startsWith("evpn")) {
      home = Util.fs + "home" + Util.fs + "vdl";
      //targetHome=Util.fs+"home"+Util.fs;
      targetHome = Util.homedir;
      configDir = Util.homedir + "vdl" + Util.fs + "EDGEMOM" + Util.fs;
      node = "gm073";
      acct = "vdl";
    } else {
      home = System.getProperty("user.home") + slash;
      acct = Util.getAccount();         // This is the account where this thread is running like "vdl"
    }
    prta("ECIT: configdir=" + configDir + " targetHome=" + targetHome + " acct=" + acct + " node=" + node
            + " nPlusOne=" + nPlusOne + " dbg=" + dbg + " oneTime=" + oneTime);
    File edgemomdir = new File(configDir);
    if (!edgemomdir.exists() || !edgemomdir.isDirectory()) {
      prta("RUN: ***** This node has not EDGEMOM directory and cannot be managed");
      return;
    }
    running = true;
    start();

  }

  /**
   * This method loads/reloads the TreeMap roleMap so that the newRole name to
   * Role object map is maintained. It reads in the newRole table and loads or
   * reloads every newRole into the tree map
   *
   * @throws java.sql.SQLException
   *
   */
  public final void loadRoleMap() throws SQLException {
    try {
      try (ResultSet rs = dbconnedge.executeQuery("SELECT * FROM edge.role")) {
        while (rs.next()) {
          RoleInstance r = roleMap.get(rs.getString("role"));
          if (r == null) {
            r = new RoleInstance(rs);
            roleMap.put(rs.getString("role"), r);
          } else {
            r.reload(rs);
          }
        }
        rs.close();
      }
      try (ResultSet rs = dbconnedge.executeQuery("SELECT * FROM edge.cpu ORDER BY cpu")) {
        while (rs.next()) {
          Cpu cpu = cpus.get(rs.getString("cpu"));
          if (cpu == null) {
            cpus.put(rs.getString("cpu"), new Cpu(rs));
          } else {
            cpu.reload(rs);
          }
        }
        rs.close();
      }
    } catch (SQLException e) {
      prta("LRP: *** could not load roles to IP e=" + e);
      throw e;
    }
  }

  public final void loadInstances() throws SQLException {
    try {
      try (ResultSet rs = dbconnedge.executeQuery("SELECT * FROM edge.querymominstance")) {
        while (rs.next()) {
          QueryMomInstance r = querymominstances.get(rs.getString("instance"));
          if (r == null) {
            r = new QueryMomInstance(rs);
            querymominstances.put(rs.getString("instance"), r);
          } else {
            r.reload(rs);
          }
        }
        rs.close();
      }

      try (ResultSet rs = dbconnedge.executeQuery("SELECT * FROM edge.instance")) {
        while (rs.next()) {
          EdgeMomInstance r = instances.get(rs.getString("instance"));
          if (r == null) {
            r = new EdgeMomInstance(rs);
            instances.put(rs.getString("instance"), r);
          } else {
            r.reload(rs);
          }
        }
        rs.close();
      }

      // Clear out the iterator threads so we can make sure moved ones are not duplicated and deleted ones disappear.
      Iterator<TreeMap<String, EdgemomInstanceSetup>> iter = threads.values().iterator();
      while (iter.hasNext()) {
        iter.next().clear();
      }

      try (ResultSet rs = dbconnedge.executeQuery("SELECT * FROM edge.instancesetup")) {
        while (rs.next()) {

          TreeMap<String, EdgemomInstanceSetup> inst = threads.get("" + rs.getInt("instanceid"));
          if (inst == null) {
            inst = new TreeMap<>();
            threads.put("" + rs.getInt("instanceid"), inst);
          }
          EdgemomInstanceSetup thread = inst.get(rs.getString("tag").toLowerCase() + "-" + rs.getInt("id"));
          if (thread == null) {
            thread = new EdgemomInstanceSetup(rs);
            inst.put(rs.getString("tag").toLowerCase() + "-" + rs.getInt("id"), thread);
          } else {
            thread.reload(rs);
          }
        }
        rs.close();
      }
    } catch (SQLException e) {
      prta("LI: *** could not load roles to IP e=" + e);
      e.printStackTrace(getPrintStream());
      throw e;
    }

  }

  /**
   * return the cpuRoles currently running on this computer according to the
   * roles_NODE file
   *
   * @param filename File name to parse for cpuRoles
   * @return Array of strings with each newRole currently on this node.
   */
  static public String[] parseRoles(String filename) {
    String[] roles = null;
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
                //prt("ParseRoles: get cpuRoles read files returns cpuRoles "+i+" "+cpuRoles[i]);
              }
              break;
            }
          }
        }
      }
    } catch (FileNotFoundException e) {
      Util.prt("ParseRoles: **** Did not find file =" + filename + " continue without it");
      //Util.IOErrorPrint(e,"ECIT: Trying to read "+filename);
    } catch (IOException e) {
      Util.IOErrorPrint(e, "ParseRoles: Trying to read " + filename);
    }
    return roles;
  }

  @Override
  public void run() {
    // Need an normall anss and edge tagged DBConnectionTHreads since other parts of the GUIs us it
    prta("ECIT: connect to db at " + Util.getProperty("DBServer"));
    try {
      if (DBConnectionThread.getThread("anss") == null) {
        DBConnectionThread tmp = new DBConnectionThread(Util.getProperty("DBServer"), "update", "anss",
                false, false, "anss", EdgeThread.getStaticPrintStream());
        EdgeThread.addStaticLog(tmp);
        if (!DBConnectionThread.waitForConnection("anss")) {
          if (!DBConnectionThread.waitForConnection("anss")) {
            if (!DBConnectionThread.waitForConnection("anss")) {
              prt("ECIT: ** Did not promptly connect to anss from EdgemomConfigThread");
            }
          }
        }
      }
    } catch (InstantiationException e) {
      prta("ECIT: ** Instantiation getting (impossible) edgero e=" + e.getMessage());
    }
    try {
      if (DBConnectionThread.getThread("edge") == null) {
        DBConnectionThread tmp = new DBConnectionThread(Util.getProperty("DBServer"), "update", "edge",
                false, false, "edge", EdgeThread.getStaticPrintStream());
        EdgeThread.addStaticLog(tmp);

        if (!DBConnectionThread.waitForConnection("edge")) {
          if (!DBConnectionThread.waitForConnection("edge")) {
            if (!DBConnectionThread.waitForConnection("edge")) {
              prt("ECIT: ** Did not promptly connect to anss from EdgemomConfigThread");
            }
          }
        }
      }
    } catch (InstantiationException e) {
      prta("ECIT: ** Instantiation getting (impossible) edge e=" + e.getMessage());
    }
    if (oneTime) {
      DBConnectionThread.init(Util.getProperty("DBServer"));
    }
    dbconnedge = DBConnectionThread.getThread("edgeConfig");
    if (dbconnedge == null) {
      try {
        prta("ECIT: connect to " + Util.getProperty("DBServer"));
        dbconnedge = new DBConnectionThread(Util.getProperty("DBServer"), "update", "edge",
                false, false, "edgeConfig", EdgeThread.getStaticPrintStream());
        EdgeThread.addStaticLog(dbconnedge);
        if (!DBConnectionThread.waitForConnection("edgeConfig")) {
          if (!DBConnectionThread.waitForConnection("edgeConfig")) {
            if (!DBConnectionThread.waitForConnection("edgeConfig")) {
              prt("ECIT: ** Did not promptly connect to edgeConfig from EdgemomConfigThread");
            }
          }
        }
        prta("ECIT:EdgemomConfigThread: connect to " + Util.getProperty("DBServer") + dbconnedge);
      } catch (InstantiationException e) {
        prta("ECIT: ** Instantiation getting (impossible) edgeConfig e=" + e.getMessage());
        dbconnedge = DBConnectionThread.getThread("edgeConfig");
      }
    }
    try {
      sleep(600);
    } catch (InterruptedException expected) {
    }

    while (true) {
      try {
        loadRoleMap();
        loadInstances();
        break;
      } catch (SQLException e) {
        prta("ECIT: cannot get roles or instances.  Wait 120 and try again e=" + e);
        try {
          sleep(120000);
        } catch (InterruptedException expected) {
        }
      }
    }
    makeAllFiles(configDir, cpus, roleMap, instances, threads, tmpsb);  // generate all of the configuration files
    int thisCpuID = 0;
    Cpu thisCpu = null;
    while (true) {
      try {
        try (ResultSet rs = dbconnedge.executeQuery("SELECT * FROM edge.cpu where cpu='" + node + "'")) {
          if (rs.next()) {
            thisCpuID = rs.getInt("ID");
            thisCpu = new Cpu(rs);
            rs.close();
            break;
          }
          rs.close();
        }
      } catch (SQLException e) {
        Util.SQLErrorPrint(e, "RUN: Trying to get cpuID for node=" + node);
        try {
          sleep(120000);
        } catch (InterruptedException expected) {
        }
      }
    }
    prta("RUN: starting for node=" + node + " cpuid=" + thisCpuID + " cpu=" + thisCpu);
    File roleFile = new File(configDir + "roles_" + node);
    if (roleFile.exists()) {
      try {
        copyTo(roleFile, targetHome + acct + slash + roleFile.getName());
      } catch (IOException e) {
        prt("RUN: *** FailedTo copy " + roleFile.getAbsolutePath() + " to " + targetHome + acct);
      }
    }
    String[] args = new String[0];
    String[] assRoles = parseRoles(configDir + "roles_" + node);// What cpuRoles do we have now!

    ArrayList<RoleInstance> assignedRoles = new ArrayList<>(assRoles.length);
    for (String assRole : assRoles) {
      RoleInstance r = roleMap.get(assRole);
      if (r != null) {
        assignedRoles.add(r);
      }
    }
    getAccounts(assignedRoles);
    moveFiles(assignedRoles, node);
    chkCrontab(assignedRoles, thisCpu);
    if (nPlusOne) {
      configRoles(assRoles, assignedRoles);      // insure all interfaces are configed or dropped on startup
    }
    if (oneTime) {
      prta("RUN:  oneTime mode completed.  Exiting...");
      System.exit(0);
    }   // If run in one time mode, just make all files, move them and set the crons
    boolean hasData;
    int loopCount = 0;
    boolean foundRole = false;
    prta("RUN: Initial setup complete.  Start loop roles[0]=" + assRoles[0]);

    // Start of infinite loop
    while (!terminate) {       // this runs forever!
      hasData = false;      // Assume no hasdata flags in cpu or in any assigned cpuRoles
      // DB updates occur in cron on the minute, be sure the DB update has had time to complete by waiting later into the minute
      long now = System.currentTimeMillis();
      if (now % 60000 < 15000) {
        try {
          prta("RUN: wait for 15 sec boundary");
          sleep(15000 - now % 60000);
        } catch (InterruptedException expected) {
        }
      }
      if (now % 60000 > 50000) {
        try {
          prta("RUN: wait for 15 sec boundary");
          sleep(15000 + 60000 - now % 60000);
        } catch (InterruptedException expected) {
        }
      }
      if (dbg || (loopCount % 20) == 1) {
        prta(Util.ascdate() + " ECIT: loop " + loopCount);
      }

      // check our IP interfaces/roles
      boolean roleChanged = false;

      // Check to see if anything has changed on the GUI side by examining the cpu.hasdata and roles.hasdata flags      
      try {
        ResultSet rs = dbconnedge.executeQuery("SELECT hasdata FROM edge.cpu WHERE cpu='" + node + "'");
        if (rs.next()) {
          hasData = rs.getInt("hasdata") == 1;
          if (hasData) {
            prt("RUN: cpu hasdata is true!");
          }
        }
        rs.close();
        if (hasData) {
          dbconnedge.executeUpdate("UPDATE edge.cpu SET hasdata=0 WHERE cpu='" + node + "'");
        }
        // Now check for configuration changes on this cpus cpuRoles
        for (RoleInstance assignedRole : assignedRoles) {
          rs = dbconnedge.executeQuery("SELECT hasdata FROM edge.role where role='" + assignedRole.getRole() + "'");
          if (rs.next()) {
            int tmp = rs.getInt("hasdata");
            if (tmp == 1) {
              prta("RUN: role " + assignedRole.getRole() + " has data is true");
            }
            hasData |= tmp == 1;
          }
          rs.close();
          if (hasData) {
            dbconnedge.executeUpdate("UPDATE edge.role SET hasdata=0 where role='" + assignedRole.getRole() + "'");
          }
        }

        // The CPU or one of the cpuRoles, or the roles have change,  do a reconfiguation pass.
        if (hasData || roleChanged) {     // something has changed, run the procedure
          prta(Util.ascdate() + " RUN: on " + node + " hasdata=" + hasData + " or roleChanged="
                  + roleChanged + " is true.  Run configuration foundRole=" + foundRole);
          loadRoleMap();              // update the role map
          loadInstances();            // update the instances
          //if(dbg) 
          prta("RUN: Make all files");
          makeAllFiles(configDir, cpus, roleMap, instances, threads, tmpsb);  // generate all of the configuration files

          // If this node is running N+1 configuration (private permanent IPs with switching roles), check on the roles
          if (nPlusOne) {
            String[] nowRoles = parseRoles(configDir + "roles_" + node); // What cpuRoles do we have now! They may have changed
            roleChanged = configRoles(nowRoles, assignedRoles);     // configure these cpuRoles on this node
            if (roleChanged) {               // if a role was added or dropped, rebuild the assigned Roles
              assRoles = nowRoles;
              assignedRoles.clear();
              for (String assRole : assRoles) {
                RoleInstance r = roleMap.get(assRole);
                if (r != null) {
                  assignedRoles.add(r);
                }
              }
              loadRoleMap();              // update the role map
              loadInstances();            // update the instances
              //if(dbg) 
              prta("RUN: Make all files after role change");
              makeAllFiles(configDir, cpus, roleMap, instances, threads, tmpsb);  // generate all of the configuration files
            }
          }
          // The cpuRoles must now be up,  Now move the files from EDGEMOM directory to where they belong for this node/role
          getAccounts(assignedRoles);
          moveFiles(assignedRoles, node);

          //if(dbg) 
          prta("RUN: configuration pass complete!");
        }
        loopCount++;

        // If the cpuRoles change or periodically check all cpuRoles and insure one not on this node, are not configured
        // If the above logic worked, this should have no effect but if a newRole came up in a bad way it might
        if (hasData || roleChanged || loopCount % 30 == 3 || loopCount == 1) { // if changed, periodically or first time
          chkCrontab(assignedRoles, thisCpu);
        }
        if (killAlarm) {
          prta("RUN: **** killAlarm is set.  Changed on combined crontabs must be asking Alarm to exit.  Exit....");
          Util.exit("RUN: *** Change on combined crontabs must be asking Alarm to exit");
        }
      } catch (SQLException e) {
        Util.SQLErrorPrint(e, "RUN: ** SQL Error getting node - reopen and retry");
        dbconnedge.reopen();
      }
      try {
        sleep(60000);
      } catch (InterruptedException expected) {
      }
    }
    prta("RUN: has terminated=" + terminate);
    running = false;
  }
  private final StringBuilder acctcron = new StringBuilder(200);
  private ArrayList<String> accounts = new ArrayList<>(5);

  /**
   * for a list of cpuRoles, figure out which cpuAccounts are active and set the
   * cpuAccounts variable
   *
   * @param assignedRoles List for cpuRoles on this cpu
   */
  public void getAccounts(ArrayList<RoleInstance> assignedRoles) {
    // More of the cron comes from each of the Instances.
    accounts.clear();
    for (RoleInstance role : assignedRoles) {
      if (role != null) {
        String[] accts = role.getEnabledAccounts().replaceAll("  ", " ").split("\\s");
        boolean found = false;
        for (int i = 0; i < accts.length; i++) {
          prt("getAccounts " + i + " " + accts[i] + " accounts.size=" + accounts.size());
          for (String account : accounts) {
            if (account.equals(accts[i])) {
              found = true;
              break;
            }
          }
          if (!found) {
            accounts.add(accts[i]);
          }
        }
      }
    }
  }

  /**
   * For all roles assigned to this server, create the crontab for every account
   * on the computer and load this crontab into the crontab file.
   *
   * @param assignedRoles The cpuRoles currently assigned on this CPU node
   */
  private void chkCrontab(ArrayList<RoleInstance> assignedRoles, Cpu thisCpu) {
    Util.clear(acctcron);
    //acctcron.append("#<cpu> ----- ").append(thisCpu.getCpu()).append(" -----\n");
    String thisCpuCron = thisCpu.getCrontab();
    if (assignedRoles.isEmpty()) {
      thisCpuCron += "# Minimum alarm of cpu with no roles\n* * * * * bash scripts/chkCWB alarm ^ 100 -nodbmsg -noudpchan >>LOG" + Util.fs + "chkcwb.log 2>&1 \n";  // the minimum alarm on a roleless cpu
      try {
        prta(" ***** No roles set minimum crontabs vdl");
        setCrontab(null, "vdl", thisCpuCron);
        for (String account : accounts) {
          if (!account.equals("vdl")) {
            prta(" ***** No roles set minimum crontabs " + account);
            setCrontab(null, account, "# No assigned roles\n");
          }
        }
        return;
      } catch (IOException expected) {

      }
    }
    acctcron.append(processCrontab(thisCpuCron));    // start with the CPU crontab

    // For each newRole add an newRole dependent crontab
    getCrontabRole(assignedRoles, acctcron);
    /*for(RoleInstance newRole: assignedRoles) {
      acctcron.append("# Crontab from newRole ").append(newRole.getRole()).append("\n");
      acctcron.append(processCrontab(newRole.getCrontab()));
      replaceAllVariables(acct, null, newRole);
    }*/
    getAccounts(assignedRoles);
    for (String account : accounts) {
      genCrontabInstance(assignedRoles, account, acctcron);

      // Load the  account from eventhing put in acctcron.
      try {
        if (dbg) {
          prta("CHKC: Load crontab " + account);
        }
        if (!assignedRoles.get(0).getRole().equals("gldketchum3")) {
          setCrontab(assignedRoles.get(0), account, acctcron.toString());
        }
        if (dbg) {
          prta("CHKC: Load crontab rtn");
        }
        Util.clear(acctcron);   // only the first account gets the newRole 
      } // Set the vdl account for all of the always and vdlonly lines
      catch (IOException e) {
        prta("CHKC: ** IOError trying to set " + acct + " crontab - this is bad!");
      }
      Util.clear(acctcron);     // for other accounts do not include cpu and newRole crontabs
    }

    // Now all active cpuAccounts must have an edge.prop file
    if (dbg) {
      prta("CHKC: Leaving chkCrontab");
    }
  }
  String lastcron = "";
  TreeMap<String, String> lastcrons = new TreeMap<>(); // Track last crons by account

  /**
   * Set the crontab to the given content if necessary. Kill any processes whose
   * lines have been dropped. Reset the edge.prop file from
   * EDGEMOM/edge.prop.newRole.acct every time the crontab is loaded. This uses
   * a "sudo -u account" if the account being set is not the one where this
   * process is running (variable acct).
   * <p>
   * Because the subprocess to do a crontab -l does not always return perfect
   * output, the command is run until two in a row have returned the save
   * crontab, then this crontab is compared to the desired crontab. If they are
   * different, the crontab is loaded and the edge.prop.newRole.acct is copied
   * again.
   *
   * @param role The newRole
   * @param account The account
   * @param content The content to make sure is in this crontag
   * @return True if the crontab was loaded or unchanged, false if something
   * went wrong
   * @throws IOException
   */
  private boolean setCrontab(RoleInstance role, String account, String content) throws IOException {
    // read the crontab until it is the same consequtively
    try {
      String cron = "";
      String cron2 = "$";
      int loops = 0;
      String croncmd = "sudo -u " + account + " crontab -l";
      if (acct.equals(account)) {
        croncmd = "crontab -l";
      }
      if (Util.getOS().contains("olaris") || Util.getOS().contains("Sun")) {
        croncmd = "crontab -l " + account;
      }
      if (dbg) {
        prt("CR: OS=" + Util.getOS() + " croncmd=" + croncmd);
      }
      lastcron = lastcrons.get(account);
      if (lastcron == null) {
        lastcron = "";
      }
      prta("CR: OS=" + Util.getOS() + " croncmd=" + croncmd + " " + account + " lastcron.len=" + (lastcron == null ? 0 : lastcron.length())
              + " content.len=" + content.length() + " lastcron.len=" + lastcron.length());
      while (!lastcron.equals(cron) || !cron2.equals(cron) || lastcron.equals("")) {
        lastcron = cron;
        Subprocess p = new Subprocess(croncmd);
        try {
          p.waitFor();
        } catch (InterruptedException e) {
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
          prta("CR:  **** get setCrontab() for role=" + role + " account=" + account + " loop=" + loops + " is crontab empty?");
        }
        // 
        Subprocess p2 = new Subprocess(croncmd);
        try {
          p2.waitFor();
        } catch (InterruptedException e) {
        }
        try {
          sleep(1000);
        } catch (InterruptedException expected) {
        }
        cron2 = p2.getOutput();
        if (!cron2.equals(cron)) {
          lastcron += "$";
          prta("CR:  ** cron did not reproduce - try again acct=" + account + " loop=" + loops + "\n1st:" + cron + "\n2nd:" + cron2);
        } else {
          prta("CR: cron and cron2 are the same! " + account + " lastcron.equals=" + lastcron.equals(cron)
                  + " cron.len=" + cron.length() + " lastcron.len=" + lastcron.length()
                  + " while=" + (!lastcron.equals(cron) || !cron2.equals(cron) || lastcron.equals("")));
          if (cron.length() == 0 || content.contains("is not active")) {
            break;
          }
        }
      }
      if (dbg) {
        prta("CR: got crontab for role=" + role + " account=" + account + " len=" + lastcron.length());
      }
      // Is it the same as the configuration?  If so return
      if (lastcron.equals(content)) {
        prta("CR: " + role + " " + account + " crontab is the same2. return " + lastcron.length() + " " + content.length());
        return true;
      }
      lastcrons.put(account, content);

      //if(dbg) 
      prt("CR:    *** cram crontab for account=" + account + " loops=" + loops + " match=" + cron.equals(lastcron)
              + "\nWas:" + lastcron + "\nNow:\n" + content + "\n# <EOF>");
      try ( // look for lines that are no longer in the desired crontab - and kill them !
              BufferedReader in = new BufferedReader(new StringReader(lastcron))) {
        String line;
        try {
          while ((line = in.readLine()) != null) {
            if (!content.contains(line)) {
              if (line.charAt(0) == '#' || line.charAt(0) == '!') {
                continue;    //skip continuation lines
              }
              prta("CR: Need to kill line=" + line);

              if (line.contains("chkJarProcess") || line.contains("chkCWB")
                      || line.contains("chkNEIC")) {
                line = line.replaceAll("  ", " ").replaceAll("  ", " ").replaceAll("  ", " ");
                String[] parts = line.split(" ");
                String killLine = "sudo -u " + account + " bash vdl" + Util.fs + "SCRIPTS" + Util.fs + "killgen ";
                if (acct.equals(account)) {
                  killLine = "bash vdl" + Util.fs + "SCRIPTS" + Util.fs + "killgen ";
                }
                for (int i = 0; i < parts.length; i++) {
                  if (parts[i].contains("chkCWB") || parts[i].contains("chkNEIC")) {
                    if (parts[i + 1].equals("alarm")) {
                      prta("CR: *** set killAlarm=true");
                      killAlarm = true;
                      continue;
                    }    // suicide might be painless, but its bad here!
                    String inst = parts[i + 2];     // instance follows the name
                    //if(dbg) 
                    prta("CR: Kill inst=" + inst);
                    if (line.contains("-tag")) {
                      for (int j = 0; j < parts.length; j++) {
                        if (parts[j].equals("-tag")) {
                          inst = parts[j + 1];
                          //if(dbg) 
                          prta("CR: Kill tag inst=" + inst);
                          break;
                        }
                      }
                      killLine += inst.replaceAll("Q", "q");
                    } else {
                      if (inst.equals("^")) {
                        inst = parts[i + 1].toLowerCase() + ".gc";
                      } else {
                        inst = parts[i + 1].toLowerCase() + "_" + inst.replaceAll("#", "_").replaceAll("Q", "q") + ".gc";
                      }
                      prta("CR: inst from inst=" + inst);
                      killLine += inst;
                    }
                    prt("CR:  *** chkCWB task :" + killLine);
                    Subprocess sp = new Subprocess(killLine);
                    try {
                      sp.waitFor();
                    } catch (InterruptedException expected) {
                    }
                    prt("CR: kill " + inst + "   returns" + sp.getOutput() + sp.getErrorOutput());
                    break;
                  } else if (parts[i].contains("chkJarProcessGCTag") || parts[i].contains("chkJarTestProcessGCTag")) {
                    killLine += parts[i + 4];
                    Subprocess sp = new Subprocess(killLine);
                    try {
                      sp.waitFor();
                    } catch (InterruptedException expected) {
                    }
                    prt("CR: kill TAG " + parts[i + 4] + "   returns" + sp.getOutput() + sp.getErrorOutput());
                    break;
                  } else if (parts[i].contains("chkJarProcess")) { // Anything left over assume only one process
                    killLine += parts[i + 1];
                    Subprocess sp = new Subprocess(killLine);
                    try {
                      sp.waitFor();
                    } catch (InterruptedException expected) {
                    }
                    prt("CR: kill " + parts[i + 1] + "   returns" + sp.getOutput() + sp.getErrorOutput());
                    break;
                  }
                } // for on parts[]

              } // the line contains chkJarProcess, chkNEIC or chkCWB
            } // if content does not contain line
          }   // whle read each line of lastcron
        } catch (IOException e) {
          prt("CR: *** IOError reading thru crontab!" + e);
        }
      }
      if (("" + role).equals("gldketchum3")) {
        return false;    // do not update the crontab on development computer!
      }
      // Write out the crontab in crontab.tmpsb, and use sudo crontab to load it
      try (RandomAccessFile outtmp = new RandomAccessFile("crontab.tmp", "rw")) {
        lastcron = content;
        outtmp.seek(0L);
        outtmp.write(content.getBytes(), 0, content.length());
        outtmp.setLength(content.length());
      }
      if (acct.equals(account)) {
        croncmd = "crontab crontab.tmp";
      } else {
        croncmd = "sudo -u " + account + " crontab crontab.tmp";
      }
      if (Util.getOS().contains("olaris") || Util.getOS().contains("Sun")) {
        croncmd = "sudo -u " + account + " crontab crontab.tmp";
      }
      if (dbg) {
        prta("CR: OS=" + Util.getOS() + " croncmd=" + croncmd);
      }
      Subprocess sp = new Subprocess(croncmd);
      try {
        sp.waitFor();
      } catch (InterruptedException e) {
        prta("CR: *** getCrontab setting crontab interrupted exception");
      }
      if (sp.getErrorOutput().length() > 0 || sp.getOutput().length() > 0) {
        prta("CR: ** crontab set err=" + sp.getErrorOutput() + "\nstdout=" + sp.getOutput());
      }
    } catch (RuntimeException e) {
      prta("CR: ** Runtime problem e=" + e);
      e.printStackTrace(getPrintStream());
    }
    return true;
  }

  /**
   * move the configuration files for this node and set of cpuRoles to their new
   * homes. Note: the edgemom.setup, roles_ and groups_ files have been named
   * with the cpu node when they were created by the EdgemomConfigFiles class -
   * however, the side files and crontab files have names based on the cpuRoles.
   *
   * @param roles The list of cpuRoles being used on this computer
   * @param node The server or CPU name.
   *
   */
  private void moveFiles(ArrayList<RoleInstance> roles, String node) {
    // Directory of the configuration directory
    if (dbg) {
      prta("MV: start moveFiles node=" + node + " configdir=" + configDir + " #roles=" + roles.size());
    }
    File dir = new File(configDir);
    //String edgemomStub = "edgemom.setup-"+node.trim()+"-";    // What do edgemom.setups look like
    // move cpuRoles, and groups file based on cpu name (not newRole)
    File[] files = dir.listFiles();
    for (File file : files) {
      try {
        if (file.getName().equals("roles_" + node.trim())) {
          prta("move " + file.getName() + " to " + home + slash + file.getName());
          copyTo(file, home + slash + file.getName());
        }
        if (file.getName().equals("groups_" + node.trim())) {
          prta("move " + file.getName() + " to " + home + slash + file.getName());
          copyTo(file, home + slash + file.getName());
        }
      } catch (IOException e) {
        prt("MV: IOException e=" + e);
        e.printStackTrace();
        SendEvent.edgeSMEEvent("BadRoleGrp", "IOError setting roles or group or edgemom.setup!", this);
      }
    }
    try {
      for (RoleInstance role : roles) {
        if (role == null) {
          continue;
        }
        if (role.getRole().trim().equals("")) {
          continue;
        }

        // For each account on this newRole, figure out which had edgeFile files need to be copied.
        if (dbg) {
          prta(" MV: do role=" + role + " account=" + acct);
        }

        // check on the edge_$SERVER.prop for this newRole and account
        File edgeProp = new File(configDir + "edge_" + node + ".prop");
        if (edgeProp.exists()) {
          try {
            prta("MV: Copy " + edgeProp.getAbsolutePath() + " to " + targetHome + acct + slash + "edge.prop");
            copyTo(edgeProp, targetHome + acct + slash + "edge.prop");             // old convention for older programs
            copyTo(edgeProp, targetHome + acct + slash + "edge_" + node + ".prop");   // new convention
          } catch (IOException e) {
            prta(" MV: ***** Did not copy a edge_" + node + ".prop to edge.prop file e=" + e);
          }
        } else {    // This is the old school method where the edge.prop is in edgefile rather than 
          prta("* MV: Did not find a " + configDir + "edge_" + node + ".prop to move to edge.prop try old method");
          edgeProp = new File(configDir + "edge.prop." + node.trim() + "." + acct);
          prta(" * MV: did not find :" + configDir + "edge_" + node + ".prop try " + configDir + "edge.prop." + node.trim() + "." + acct + " exist=" + edgeProp.exists());
          if (edgeProp.exists()) {
            try {
              copyTo(edgeProp, targetHome + acct + slash + "edge.prop");
            } catch (IOException e) {
              prta(" MV: **** did not copy a edge.prop file2 e=" + e);
            }
          } else {
            prta(" MV: ***** Did not find an edge.prop for " + role + "." + acct);
          }
        }
        // check on a queryserver.prop file for this newRole and account
        File queryServerProp = new File(configDir + "queryserver.prop." + role.getRole() + "." + acct);
        if (queryServerProp.exists()) {
          try {
            prta("MV: Copy " + queryServerProp.getAbsolutePath() + " to " + targetHome + acct + slash + "queryserver.prop");
            copyTo(queryServerProp, targetHome + acct + slash + "queryserver.prop");
          } catch (IOException e) {
            prta(" MV: ***** Did not copy a queryserver.prop file e=" + e);
          }
        }
        String[] accountsl = role.getEnabledAccounts().split("\\s");
        for (String account : accountsl) {
          // copy the roles and groups to each directory
          if (!account.equals(acct)) {
            try {
              File file = new File(configDir + "roles_" + node);
              copyTo(file, targetHome + account + slash + "roles_" + node);
              file = new File(configDir + "groups_" + node);
              if (file.exists()) {
                copyTo(file, targetHome + account + slash + "groups_" + node);
              }
              copyTo(edgeProp, targetHome + account + slash + "edge_" + node + ".prop");
              copyTo(edgeProp, targetHome + account + slash + "edge.prop");
            } catch (IOException e) {
              prta("MV: **** did not copy roles or groups to account " + account + " e=" + e);
              e.printStackTrace(getPrintStream());
            }
          }
          try {
            try (ResultSet rs = dbconnedge.executeQuery("SELECT edgefile FROM edge.edgefile WHERE edgefile regexp '." + role.getRole() + "." + account + "$'")) {
              String file;
              while (rs.next()) {
                file = rs.getString("edgefile");
                File config = new File(configDir + file);
                //prta("MV: checking for files ending in "+role.getRole()+"."+acct+" file="+config+" exists="+config.exists());
                if (config.exists()) {
                  try {
                    prta("MV: copy by ends with role.acct " + file + " "
                            + " to " + targetHome + account + slash + file.replaceAll("." + role.getRole() + "." + account, ""));
                    copyTo(config, targetHome + account + slash + file.replaceAll("." + role.getRole() + "." + account, ""));
                  } catch (IOException e) {
                    prta("MV: ***** did not copy edgefileid=" + file
                            + " file=" + config.getName() + " to " + targetHome + account + slash + file.replaceAll("." + role.getRole() + "." + account, ""));
                  }
                }
              }
            }
          } catch (SQLException e) {
            e.printStackTrace(getPrintStream());
          }
        }

        // For each instance, copy any configuration files in the instance thread lines
        Iterator<EdgeMomInstance> itr = instances.values().iterator();    // look at all of the instances
        while (itr.hasNext()) {
          EdgeMomInstance instance = itr.next();
          if (((!instance.isFailedOver() && instance.getRoleID() == role.getID())
                  || // normally on this newRole
                  (instance.isFailedOver() && instance.getFailoverID() == role.getID()))
                  && // is failed over onto this newRole
                  instance.getAccount().equalsIgnoreCase(acct)
                  && !instance.isDisabled()) {     // Is the instance on this newRole and enabled
            prta("MV: Role=" + role.getRole() + " instance " + instance.getEdgeMomInstance() + " copy config files");
            TreeMap<String, EdgemomInstanceSetup> thr = threads.get("" + instance.getID());
            if (thr != null) {
              Iterator<EdgemomInstanceSetup> itr2 = thr.values().iterator();
              while (itr2.hasNext()) {       // for every EdgeMomInstance setup line/thread
                EdgemomInstanceSetup setup = itr2.next();
                if (!setup.getConfigFilename().equals("") && !setup.getConfigFilename().contains(Util.fs + "NONE")) { // Is there a internal setup file
                  String file = replaceAllVariables(setup.getConfigFilename(), instance, role);
                  File config = new File(configDir + file);
                  if (config.exists()) {             // If the setup file exists, copy it
                    try {
                      prta("MV: copy EdgeMom configfile " + config.getAbsolutePath() + " to " + targetHome + acct + slash + file);
                      copyTo(config, targetHome + acct + slash + file);
                    } catch (IOException e) {
                      prta("MV: ***** did not copy config file=" + file
                              + " to " + targetHome + acct + slash + file);
                    }
                  }
                }
                if (setup.getEdgeFileID() > 0) {
                  try (ResultSet rs = dbconnedge.executeQuery("SELECT edgefile FROM edge.edgefile WHERE id=" + setup.getEdgeFileID())) {
                    if (rs.next()) {
                      String file = replaceAllVariables(rs.getString(1), instance, role);
                      File config = new File(configDir + file);
                      if (config.exists()) {
                        try {
                          prta("MV: copy by EdgeMom config edgeFileID " + setup.getEdgeFileID() + " "
                                  + config.getAbsolutePath() + " to " + targetHome + acct + slash + file);
                          copyTo(config, targetHome + acct + slash + file);
                        } catch (IOException e) {
                          prta("MV: ***** did not copy edgefileid=" + setup.getEdgeFileID()
                                  + " file=" + config.getName() + " to " + targetHome + acct + slash + file);
                        }
                      }
                    }
                  }
                }
              } // iterator on edgemom instance
            }
          }   // if the instance newRole matches this one and the account is correct
        }     // iterate on each instance
      }       // for each assigned newRole
    } catch (SQLException e) {
      Util.SQLErrorPrint(e, "MV: ** Getting crontab edge files ");
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
      prt(" CP: copy " + source.getAbsolutePath() + " to " + outfilename);
    }
    Util.chkFilePath(outfilename);
    try (RandomAccessFile raw = new RandomAccessFile(source, "r");
            RandomAccessFile output = new RandomAccessFile(outfilename, "rw")) {
      if (copyBuf.length < raw.length()) {
        copyBuf = new byte[(int) raw.length() * 2];
      }
      raw.seek(0L);
      raw.read(copyBuf, 0, (int) raw.length());
      output.seek(0L);
      output.write(copyBuf, 0, (int) raw.length());
      output.setLength(raw.length());
    }
    return true;
  }

  /**
   * This method creates all group_NODE, roles_NODE and edgemom.setup files from
   * the database for all cpuRoles and for the current cpuRoles-to-cpu mappings
   * and puts them in the EDGEMOM directory.
   *
   * It also writes all edgefile side files into the EDGEMOM directory.
   *
   * This is called by the EdgemomConfigThread.run() to create all the files
   * when a reconfiguration event is detected.
   *
   * @param configDir Path to the configuration directory
   * @param cpus A treemap of the CPUs defined on the system
   * @param roleMap
   * @param instances
   * @param threads
   * @param sb
   */
  public void makeAllFiles(String configDir, TreeMap<String, Cpu> cpus,
          TreeMap<String, RoleInstance> roleMap,
          TreeMap<String, EdgeMomInstance> instances,
          TreeMap<String, TreeMap<String, EdgemomInstanceSetup>> threads,
          StringBuilder sb) {
    if (configDir == null) {
      configDir = "EDGEMOM" + slash;
    }
    prta("MA: ECF.makeAllFiles() #cpus=" + cpus.size() + " #roles=" + roleMap.size() + " #instances=" + instances.size() + " configdir=" + configDir);

    File edir = new File(configDir.substring(0, configDir.length() - 1));
    if (edir.isDirectory()) {
      // Look through the EDGEMOM directory and delete all candidate files if in onetime mode
      if (oneTime) {
        File[] files = edir.listFiles();
        for (File file : files) {
          if (file.isDirectory()) {
            continue;
          }
          if (file.getName().contains(".setup")) {
            file.delete();
          } else if (file.getName().startsWith("groups_")) {
            file.delete();
          } else if (file.getName().startsWith("roles_")) {
            file.delete();
          } else if (file.getName().startsWith("crontab.")) {
            file.delete();
          } else if (file.getName().startsWith("edge_")) {
            file.delete();
          } else if (file.getName().contains("edge.prop")) {
            file.delete();
          } else if (file.getName().contains("queryserver.prop")) {
            file.delete();
          } else if (file.getName().contains("queryserver_")) {
            file.delete();
          }
        }
      }
    } else {
      prt("MA: There is no EDGEMOM directory!  Exit()");
      SendEvent.edgeSMEEvent("NoEDGEMOM", "There is no ~vdl" + Util.fs + "EDGEMOM directory!  Server cannot be configured", this);
      System.exit(2);
    }

    ArrayList<String> cpuAccounts = new ArrayList<>(5);
    // For each CPU known to us, accumulate the roles and accounts on that CPU and write out the files.
    ArrayList<RoleInstance> cpuRoles = new ArrayList<>(1);   // Accumulate the cpuRoles associated with this host
    Iterator<Cpu> itrcpu = cpus.values().iterator();
    while (itrcpu.hasNext()) {
      Cpu cpu = itrcpu.next();
      if (cpu == null) {
        continue;
      }
      if (cpu.getCpu().equals("NONE")) {
        continue;
      }
      if (dbg) {
        prta("MA: Start ECF.makeAllFiles() for cpu=" + cpu);
      }
      //String roleCrontab="";
      Util.clear(sb).append(cpu.getEdgeProp());
      if (sb.length() > 0) {
        try {
          replaceAllVariables(sb, null, null);
          Util.writeFileFromSB(configDir + "edge_" + cpu.getCpu() + ".prop", sb);  // edge_$SERVER
        } catch (IOException e) {
          prta("MA: Did not write " + configDir + "edge_" + cpu.getCpu() + "e=" + e);
        }
      }

      // Make list of cpuAccounts for all cpuRoles assigned to this cpu
      cpuAccounts.clear();
      cpuRoles.clear();
      Iterator<RoleInstance> itr = roleMap.values().iterator();
      while (itr.hasNext()) {
        RoleInstance role = itr.next();
        if (role.getCpuID() == cpu.getID() && !role.isFailedOver()) {
          cpuRoles.add(role);
        }
        // if the newRole is failed over, put it on is failed over ID
        if (role.isFailedOver() && role.getFailoverCpuID() == cpu.getID()) {
          cpuRoles.add(role);
        }
      }

      // Make a list of accounts on the CPU
      cpuAccounts.clear();
      for (RoleInstance role : cpuRoles) {
        if (role != null) {
          String[] accts = role.getEnabledAccounts().split("\\s");
          boolean found = false;
          for (String acct1 : accts) {
            for (String cpuAccount : cpuAccounts) {
              if (cpuAccount.equals(acct1)) {
                found = true;
                break;
              }
            }
            if (!found) {
              cpuAccounts.add(acct1);
            }
          }
        }
      }

      // Write out the roles_$Server file and any crontab_$ROLE.$ACCOUNT (old style) files.
      try {
        if (cpu.getCpu().equals("gm044") || cpu.getCpu().equals("gm073")) {
          prt("MA: gm044 for " + cpu.getCpu());
        }
        Util.writeFileFromSB(configDir + "roles_" + cpu.getCpu(), genRoleBuffer(cpuRoles, cpu, sb));  // roles_$NODE
        Util.clear(sb).append(processCrontab(cpu.getCrontab()));
        getCrontabRole(cpuRoles, sb);
        for (String account : cpuAccounts) {
          for (RoleInstance role : cpuRoles) {
            Util.writeFileFromSB(configDir + "crontab_" + role.getRole() + "." + account,
                    genCrontabInstance(cpuRoles, account, sb));
            Util.clear(sb);
          }
        }
      } catch (IOException e) {
        prta("MA: **** did not write a role or crontab file e=" + e);
      }
    }   // loop on each cpu

    // Write out the querymom_$INSTANCE files
    String qfilename = null;
    try (ResultSet rs = dbconnedge.executeQuery(
            "SELECT * FROM edge.querymominstance ORDER BY instance")) {
      while (rs.next()) {
        Util.clear(sb);
        qfilename = "querymom_" + rs.getString("instance") + ".setup";
        sb.append("#Written for querymominstance ").append(rs.getString("instance")).append("\n");
        sb.append(" Mom:EdgeMom:-empty\n"
                + "# The EdgeChannelServer is used for configuration parameters from channel table (FilterPicker)\n"
                + " Echn:EdgeChannelServer:-empty >>echnqm\n");
        if (rs.getInt("qsenable") != 0) {
          sb.append("QS:gov.usgs.cwbquery.EdgeQueryServer:").append(rs.getString("qsargs")).append(" >>queryserver\n");
        }
        if (rs.getInt("cwbwsenable") != 0) {
          sb.append("CWBWS:gov.usgs.anss.waveserver.CWBWaveServer:").append(rs.getString("cwbwsargs")).append(" >>cwbws\n");
        }
        if (rs.getInt("dlqsenable") != 0) {
          sb.append("DLQS:gov.usgs.cwbquery.DataLinkToQueryServer:").append(rs.getString("dlqsargs")).append(" >>dlqs\n");
        }
        if (rs.getInt("qscenable") != 0) {
          sb.append("QSC:gov.usgs.cwbquery.QuerySpanCollection:").append(rs.getString("qscargs")).append(" >>qsc\n");
        }
        if (rs.getInt("mscenable") != 0) {
          sb.append("MSC:gov.usgs.cwbquery.MiniSeedCollection:").append(rs.getString("mscargs")).append(" >>msc\n");
        }
        if (rs.getInt("twsenable") != 0) {
          sb.append("TWS:gov.usgs.anss.waveserver.TrinetServer:").append(rs.getString("twsargs")).append(" >>tws\n");
        }
        if (rs.getInt("fkenable") != 0) {
          sb.append("FKM:gov.usgs.anss.fk.FKManager:").append(rs.getString("fkargs")).append(" >>fkm\n");
        }
        if (rs.getInt("fpenable") != 0) {
          sb.append("PM:gov.usgs.anss.picker.PickerManager:").append(rs.getString("fpargs")).append(" >>pmgr\n");
        }
        if (rs.getInt("ssdenable") != 0) {
          sb.append("SSD:gov.usgs.anss.picker.CWBSubspaceManager:").append(rs.getString("ssdargs")).append(" >>ssd\n");
        }

        try {
          if (dbg) {
            prt("MA: Write " + qfilename + " " + sb);
          }
          Util.stringBuilderReplaceAll(sb, "  ", " ");
          Util.stringBuilderReplaceAll(sb, "  ", " ");
          Util.chkFilePath(configDir + qfilename.trim());
          Util.writeFileFromSB(configDir + qfilename.trim(), sb);
        } catch (IOException e) {
          prta("MA: **** Failed to write an edge querymom_N#I.config file=" + qfilename + " e=" + e);
        }
      }
      rs.close();
    } catch (SQLException e) {
      Util.SQLErrorPrint(e, "Trying to write edge config file=" + qfilename + " e=" + e);

    }

    // Now write out all of the side files
    String filename = null;
    try (ResultSet rs = dbconnedge.executeQuery(
            "SELECT * FROM edge.edgefile ORDER BY edgefile")) {
      while (rs.next()) {
        filename = rs.getString("edgefile");
        if ((filename.charAt(0) == '_' || filename.contains("/_")) && !filename.contains("NoDB")
                || filename.startsWith("edge.prop") || filename.startsWith("crontab.")) {
          if (dbg) {
            prt("MA: Skip " + filename);
          }
          continue;
        }
        String fullFilePath = "";
        String acctl = "vdl";
        try {
          if (dbg) {
            prt("MA: Write " + filename + " " + rs.getString("content").length());
          }
          Util.clear(sb).append(rs.getString("content"));
          if (filename.charAt(0) == '~') {
            acctl = filename.substring(1, filename.indexOf("/"));
            fullFilePath = Util.fs + "home" + Util.fs + filename.substring(1).trim();
            Util.chkFilePath(fullFilePath);
            Util.writeFileFromSB(fullFilePath, sb);
          } else {
            Util.stringBuilderReplaceAll(sb, "  ", " ");
            fullFilePath = configDir + filename.trim();
            Util.chkFilePath(fullFilePath);
            Util.writeFileFromSB(fullFilePath, sb);
          }
        } catch (IOException e) {
          prta("MA: **** Failed to write an edge config file=" + filename + " e=" + e);
          String path = "";
          String file;
          if (fullFilePath.contains("/")) {
            path = fullFilePath.substring(0, fullFilePath.lastIndexOf("/"));
            file = fullFilePath.substring(fullFilePath.lastIndexOf("/") + 1);
          } else {
            file = fullFilePath;
          }
          prta("MA: create path=" + path + " file=" + file + " fullPath=" + fullFilePath + " acct=" + acctl);
          if (!path.equals("")) {
            try {
              Subprocess sub = new Subprocess("sudo -u " + acctl + " mkdir -p " + path);
              sub.waitFor();
              prta("mkdir out=" + sub.getOutput() + " err=" + sub.getErrorOutput());
              sub = new Subprocess("sudo -u " + acctl + " chmod g+w " + path);
              prta("chmod out=" + sub.getOutput() + " err=" + sub.getErrorOutput());
              sub.waitFor();
            } catch (IOException | InterruptedException e2) {
              prta("MA: attempt to created path failed e=" + e);
            }
          }
          try {
            Subprocess sub = new Subprocess("sudo  -u " + acctl + " touch " + fullFilePath);
            sub.waitFor();
            prta("touch out=" + sub.getOutput() + " err=" + sub.getErrorOutput());
            sub = new Subprocess("sudo -u " + acctl + " chmod g+w " + fullFilePath);
            prta("mkdir out=" + sub.getOutput() + " err=" + sub.getErrorOutput());
            sub.waitFor();
          } catch (IOException | InterruptedException e2) {
            prta("MA: attempt to created path failed e=" + e);
          }
        }
      }
      rs.close();
    } catch (SQLException e) {
      Util.SQLErrorPrint(e, "Trying to write edge config file=" + filename + " e=" + e);
    }

    // For each instance, write out the instance based files
    Iterator<EdgeMomInstance> itr = instances.values().iterator();
    Util.clear(sb);
    try {
      while (itr.hasNext()) {
        EdgeMomInstance instance = itr.next();
        Util.clear(sb).append(instance.getEdgeProp());
        replaceAllVariables(sb, instance, null);
        Util.chkFilePath(configDir + "edge_" + instance.getEdgeMomInstance() + ".prop");
        Util.writeFileFromSB(configDir + "edge_" + instance.getEdgeMomInstance() + ".prop", sb);
        if (dbg) {
          prta("MA: Write " + configDir + "edge_" + instance.getEdgeMomInstance() + ".prop");
        }
        Iterator<RoleInstance> itrrole = roleMap.values().iterator();
        RoleInstance role = null;
        while (itrrole.hasNext()) {
          role = itrrole.next();
          if (role.getID() == instance.getRoleID()) {
            break;
          }
        }
        TreeMap<String, EdgemomInstanceSetup> setup = threads.get("" + instance.getID());
        getEdgeMomSetupSB(setup, instance, sb);
        replaceAllVariables(sb, instance, role);
        Util.writeFileFromSB(configDir + "edgemom_" + instance.getEdgeMomInstance() + ".setup", sb);

        // If the instancesetup lines have a configuration file, make sure we got it
        if (setup != null) {
          Iterator<EdgemomInstanceSetup> itr2 = setup.values().iterator();

          while (itr2.hasNext()) {
            EdgemomInstanceSetup s = itr2.next();
            if (!s.getConfigFilename().trim().equals("") && !s.getConfigFilename().contains("NONE")) {
              Util.clear(scratch).append(s.getConfigContent());
              replaceAllVariables(scratch, instance, role);
              if (scratch.length() > 0) {
                Util.chkFilePath(configDir + s.getConfigFilename());
                Util.writeFileFromSB(configDir + s.getConfigFilename(), scratch);
              }
              if (dbg) {
                prta("MA: Write edgesetup config file=" + s.getConfigFilename() + " size=" + scratch.length());
              }
            }
          }
        }
      }
    } catch (IOException e) {
      prta("MA: **** Failed to write a edgemom.setup or prop file");
    }
  }

  private StringBuilder genAlarm(StringBuilder sb, boolean alarm, int alarmmem,
          boolean bhcheck, boolean cmdstatus,
          boolean config, boolean dbmsg, boolean etchost, boolean latency, boolean routes,
          boolean snw, boolean snwsender, boolean udpchan, boolean web, long alarmprocmask,
          int aftac, int consoles, int metadata, int quakeml, int querymom, int rts,
          int smgetter, int tcpholdings, int proc1mem, int proc2mem, int proc3mem,
          int proc4mem, int proc5mem, int proc6mem, int proc7mem, int proc8mem, int proc9mem, String alarmArgs) {

    // create the alarm command line with its many options
    sb.append("* * * * * bash scripts").append(Util.fs).append("chkCWB alarm ^ ").
            append((alarmmem <= 0 ? 50 : alarmmem)).append(" ");
    sb.append(alarmArgs.isEmpty() ? "" : alarmArgs + " ");
    if (bhcheck) {
      sb.append("-checkbh ");
    }
    if (alarm) {
      sb.append("-alarm ");
    }
    if (!cmdstatus) {
      sb.append("-nocmd ");
    }
    if (!config) {
      sb.append("-nocfg ");
    }
    if (!dbmsg) {
      sb.append("-nodbmsg ");
    }
    if (etchost) {
      sb.append("-etchosts ");
    }
    if (routes) {
      sb.append("-routes ");
    }
    if (snw) {
      sb.append("-snw ");
    }
    if (snwsender) {
      sb.append("-snwsender ");
    }
    if (!udpchan) {
      sb.append("-noudpchan ");
    }
    if (web) {
      sb.append("-web ");
    }
    sb.append(" >>LOG").append(Util.fs).append("chkcwb.log 2>&1\n");
    //prta("AlarmArgs="+alarmArgs+" sb="+sb);

    // For each of the other special ones create aline if they have a memory allocation
    if (Math.abs(tcpholdings) > 0) {
      sb.append("* * * * * bash scripts").append(Util.fs).append("chkCWB TcpHoldings ^ ").append(Math.abs(tcpholdings)).
              append(tcpholdings < 0 ? " -test" : "").
              append(" >>LOG").append(Util.fs)
              .append("chkcwb.log 2>&1\n");
    }
    if (Math.abs(aftac) > 0) {
      sb.append("* * * * * bash scripts").append(Util.fs).append("chkNEIC AftacHoldingsServer ^ ").append(Math.abs(aftac)).
              append(aftac < 0 ? " -test" : "").
              append(" >>LOG").append(Util.fs).append("chkcwb.log 2>&1\n");
    }
    if (Math.abs(consoles) > 0) {
      sb.append("* * * * * bash scripts").append(Util.fs).append("chkNEIC ConsoleServer ^ ").append(Math.abs(consoles)).
              append(consoles < 0 ? " -test" : "").
              append(" >>LOG").append(Util.fs).append("chkcwb.log 2>&1\n");
    }
    if (Math.abs(metadata) > 0) {
      sb.append("* * * * * bash scripts").append(Util.fs).append("chkNEIC MetaDataServer ^ ").append(Math.abs(metadata)).
              append(metadata < 0 ? " -test" : "").
              append(" >>LOG").append(Util.fs).append("chkcwb.log 2>&1\n");
    }
    //if(quakeml > 0) 
    //  sb.append("* * * * * bash scripts/chkNEIC QuakeMLConversion ^ ").append(quakeml)
    //          .append(" >>LOG/chkcwb.log 2>&1\n");
    if (Math.abs(rts) > 0) {
      sb.append("* * * * * bash scripts").append(Util.fs).append("chkNEIC RTSServer ^ ").append(Math.abs(rts)).
              append(rts < 0 ? " -test" : "").
              append(" >>LOG").append(Util.fs).append("chkcwb.log 2>&1\n");
    }
    if (Math.abs(smgetter) > 0) {
      sb.append("* * * * * bash scripts").append(Util.fs).append("chkNEIC SMGetter ^ ").append(Math.abs(smgetter)).
              append(smgetter < 0 ? " -test" : "").
              append(" -pdl >>LOG").append(Util.fs).append("chkcwb.log 2>&1\n");
    }
    //if(proc1mem > 0) sb.append("* * * * * bash scripts/chkNEIC PROC1 ^ ".append(proc1mem);
    //prta("genAlarm sb is now :\n"+sb.toString());
    return sb;
  }

  /**
   * This method builds up a crontab for a list of assigned cpuRoles from the
   * crontab string in the newRole configuration. It uses the concept of the
   * newRole that is NOT failed over has precedence and then tries to add any
   * lines from the failed over cpuRoles that are unique - that is not already
   * in the crontab of the primary newRole. This prevents thinks like
   * updateHoldings.bash from being run multiple times
   *
   * @param assignedRoles A list of cpuRoles on the cpu for which a crontab is
   * needed
   * @param sb The stringbuilder to apppend this answer to, it will be cleared
   * here
   * @return
   */
  private StringBuilder getCrontabRole(ArrayList<RoleInstance> assignedRoles,
          StringBuilder sb) {
    // This is a list of all of the threads that can be run in the alarm
    // They are an "or" of all of the configuration of all of the cpuRoles
    boolean alarm = false, bhcheck = false, cmdstatus = false, config = false;
    boolean dbmsg = false, etchost = false, latency = false, routes = false;
    boolean snw = false, snwsender = false, udpchan = false, web = false;
    // Other tasks that can be run by having memory allocations > 0, They
    // are run by chosing the maximum value of the memory for this task for all cpuRoles
    int alarmmem = 0, aftac = 0, consoles = 0, metadata = 0, quakeml = 0, querymom = 0;
    int rts = 0, smgetter = 0, tcpholdings = 0;
    int proc1mem = 0, proc2mem = 0, proc3mem = 0, proc4mem = 0, proc5mem = 0, proc6mem = 0;
    int proc7mem = 0, proc8mem = 0, proc9mem = 0;
    long alarmprocmask = 0;
    String alarmArgs = "";
    // Is this the single newRole on the cpu case - then configure strictly from this one edgeFile
    if (assignedRoles.isEmpty()) {
      config = true;
      sb.append("#<role> ------- No roles are assigned to the cpu -------- ").append(Version.version).append("\n");

    } else {
      if (assignedRoles.size() == 1) {
        RoleInstance role = assignedRoles.get(0);
        alarmmem = role.getAlarmMemory();
        alarm = role.runAlarm();
        bhcheck = role.runBHChecker();
        cmdstatus = role.runCommandStatus();
        config = role.runCWBConfig();
        dbmsg = role.runDBMsg();
        etchost = role.runEtcHosts();
        latency = role.runLatency();
        routes = role.runRoutes();
        snw = role.runSNW();
        snwsender = role.runSNWSender();
        udpchan = role.runUdpChannel();
        web = role.runWebServer();
        sb.append("#<role> ----- ").append(assignedRoles.get(0).getRole()).append(" ------ ").append(Version.version).append("\n");
        sb.append(replaceAllVariables(processCrontab(assignedRoles.get(0).getCrontab()), null, role));
        alarmprocmask = role.getAlarmProcessMask();
        aftac = role.getAftacMemory();
        consoles = role.getConsolesMemory();
        metadata = role.getMetadataMemory();
        quakeml = role.getQuakeMLMemory();
        querymom = role.getQueryMomMemory();
        rts = role.getRTSMemory();
        smgetter = role.getSMGetterMemory();
        tcpholdings = role.getTcpHoldingsMemory();
        //proc1mem = newRole.getProc1Memory();
        alarmArgs += role.getAlarmArgs();

      } // There are multiple cpuRoles, or together the alarm enables and get max memory for all one time tasks
      else {
        // Only one newRole should be not failed over.  Find that one and get its index
        int n = 0;
        RoleInstance primaryRole = null;

        for (RoleInstance role : assignedRoles) {
          alarmmem = Math.max(alarmmem, role.getAlarmMemory());
          alarm |= role.runAlarm();
          bhcheck |= role.runBHChecker();
          cmdstatus |= role.runCommandStatus();
          config |= role.runCWBConfig();
          dbmsg |= role.runDBMsg();
          etchost |= role.runEtcHosts();
          latency |= role.runLatency();
          routes |= role.runRoutes();
          snw |= role.runSNW();
          snwsender |= role.runSNWSender();
          udpchan |= role.runUdpChannel();
          web |= role.runWebServer();
          alarmprocmask |= role.getAlarmProcessMask();
          aftac = Math.max(aftac, role.getAftacMemory());
          consoles = Math.max(consoles, role.getConsolesMemory());
          metadata = Math.max(metadata, role.getMetadataMemory());
          quakeml = Math.max(quakeml, role.getQuakeMLMemory());
          querymom = Math.max(querymom, role.getQueryMomMemory());
          rts = Math.max(rts, role.getRTSMemory());
          smgetter = Math.max(smgetter, role.getSMGetterMemory());
          tcpholdings = Math.max(tcpholdings, role.getTcpHoldingsMemory());
          if (role.getAlarmMemory() > 0) {
            alarmArgs += role.getAlarmArgs();
          }
          if (role.isFailedOver()) {
            prta("GCTR: * FailedOver role " + role);
          } else {
            if (primaryRole == null && n == 0) {
              primaryRole = role;   // Select the primary newRole (non-failovered assigned
              n++;                // Cound the non failed over cpuRoles - there should be one!
            }
          }
        }
        if (primaryRole == null) { // No primary newRole was chose - warn the user
          prta("GCTR: **** There is no primary role on a multi role computer! default to first one! n=" + n);
          for (RoleInstance role : assignedRoles) {
            prta("GCTR: role = " + role + " failed=" + role.isFailedOver() + " cpuid=" + role.getCpuID());
          }
          primaryRole = assignedRoles.get(0);
          sb.append("<role> ** no roles are primary - first one selected ").
                  append(assignedRoles.get(0).getRole()).append("\n");
        }

        if (n != 1) {
          prta("GCTR: ***** There are more than two primary roles!  How can this be");
        }
        sb.append("#<role> primary ").append(primaryRole.getRole()).append("\n");
        sb.append(primaryRole.getCrontab());
        String line;
        for (RoleInstance role : assignedRoles) {
          if (role.getRole().equals(primaryRole.getRole())) {
            continue;  // primary already in
          }
          sb.append("#<role> secondary ").append(role.getRole()).append("\n");

          try {
            try (BufferedReader in = new BufferedReader(new StringReader(role.getCrontab()))) {
              while ((line = in.readLine()) != null) {
                if (line.contains("scripts" + slash)) {
                  String[] parts = line.split("\\s");
                  boolean skip = false;
                  for (String part : parts) {
                    if (part.contains("scripts" + slash)) {
                      if (primaryRole.getCrontab().contains(part)) {
                        prta("GCTR: Combining roles skip line=" + line);
                        skip = true;
                        break;
                      }
                    }
                  }
                  if (skip) {
                    continue;
                  }
                  sb.append(line).append("\n");
                }
              }
            }
          } catch (IOException expected) {   // How can this happen?
          }
          replaceAllVariables(sb, null, role);    // convert any roles found
        }     // add each failed over newRole

      }
    }     // THere is at least one assigned newRole
    // Add on any lines needed by the Role configuration for alarm based threads
    // or one time processes.

    genAlarm(sb, alarm, alarmmem, bhcheck, cmdstatus, config, dbmsg, etchost,
            latency, routes, snw, snwsender,
            udpchan, web, alarmprocmask, aftac,
            consoles, metadata, quakeml, querymom, rts,
            smgetter, tcpholdings, proc1mem, proc2mem, proc3mem, proc4mem, proc5mem,
            proc6mem, proc7mem, proc8mem, proc9mem, alarmArgs);
    return Util.stringBuilderReplaceAll(sb, "  ", " ");
  }

  /**
   * Generate the crontab in a StringBuilder for a list of assigned roles(on one
   * CPU) and for all instances on this newRole and account. It takes into
   * account the disabled and failed over flags and the failover configuration.
   *
   * @param assignedRoles The cpuRoles assigned
   * @param account The account for the crontab
   * @param sb The StringBUilder to use
   */
  private StringBuilder genCrontabInstance(ArrayList<RoleInstance> assignedRoles,
          String account, StringBuilder sb) {
    // note we do not clear the SB because it might contain some newRole stuff
    for (RoleInstance role : assignedRoles) {
      if (role != null) {
        // Find all instance assigned to this newRole and amalgam the crontab from each
        Iterator<EdgeMomInstance> itr = instances.values().iterator();
        while (itr.hasNext()) {
          EdgeMomInstance instance = itr.next();
          if (!instance.isDisabled()) {
            if ((!instance.isFailedOver()
                    && // The instance is not failed over, check for right account/role
                    instance.getRoleID() == role.getID() && instance.getAccount().equals(account))
                    || instance.isFailedOver()
                    && // If the instance is marked Failovercheck failover newRole and account 
                    (instance.getFailoverID() == role.getID() && instance.getAccount().equals(account))) {
              sb.append("#<instance> ----- ").append(instance.getEdgeMomInstance()).
                      append(" ").append(role.getRole()).append(" ").append(account).append(" -----\n");
              if (instance.getHeap() < 10) {
                sb.append("#");
              }
              sb.append("* * * * * bash scripts").append(Util.fs).append("chkCWB edgemom ").
                      append(instance.getEdgeMomInstance()).
                      append(" ").append(instance.getHeap()).append(" ").append(instance.getArgs()).
                      append(" -f EDGEMOM").append(Util.fs).
                      append("edgemom_").append(instance.getEdgeMomInstance()).append(".setup ").
                      append(" >>LOG").append(Util.fs).append("chkcwb.log 2>&1\n");
              sb.append(processCrontab(instance.getCrontab()));
              replaceAllVariables(sb, instance, role);
            }
          }
        }
        Iterator<QueryMomInstance> itr2 = querymominstances.values().iterator();
        while (itr2.hasNext()) {
          QueryMomInstance qinstance = itr2.next();
          if (!qinstance.isDisabled()) {
            if ((!qinstance.isFailedOver()
                    && // The instance is not failed over, check for right account/role
                    qinstance.getRoleID() == role.getID() && qinstance.getAccount().equals(account))
                    || qinstance.isFailedOver()
                    && // If the instance is marked Failovercheck failover newRole and account 
                    (qinstance.getFailoverID() == role.getID() && qinstance.getAccount().equals(account))) {
              sb.append("#<querymom> ").append(qinstance.getQueryMomInstance()).append(" ").
                      append(role.getRole()).append(" ").append(account).append("\n");
              sb.append("* * * * * bash scripts").append(Util.fs).append("chkCWB querymom ").append(qinstance.getQueryMomInstance()).
                      append(" ").append(qinstance.getHeap()).append(" ").
                      append(qinstance.getArgs().isEmpty() ? "" : qinstance.getArgs() + " ").
                      append("-f EDGEMOM").append(Util.fs).append("querymom_").append(qinstance.getQueryMomInstance()).append(".setup ").
                      append(" >>LOG").append(Util.fs).
                      append("chkcwb.log 2>&1\n");
            }
          }
        }
        // Find all instance assigned to this newRole and amalgam the crontab from eagrech

      }
    }
    return Util.stringBuilderReplaceAll(sb, "  ", " ");
  }
  StringBuilder tmpcron = new StringBuilder(100);

  private StringBuilder processCrontab(String crontab) {
    String[] lines = crontab.split("\n");
    Util.clear(tmpcron);
    for (int i = 0; i < lines.length; i++) {
      if (lines[i].startsWith("chkCWB") || lines[i].startsWith("chkNEIC")) {
        lines[i] = "* * * * * bash scripts" + Util.fs + lines[i] + " >>LOG" + Util.fs + "chkcwb.log 2>&1";
      }
      tmpcron.append(lines[i]).append("\n");
    }
    return tmpcron;
  }
  StringBuilder tmpinst = new StringBuilder(4);

  private void doInstance(StringBuilder sb, String match, int minpnt) {
    int pnt = sb.indexOf(match, minpnt);
    if (pnt < 0) {
      return;
    }
    pnt = pnt + match.length();
    Util.clear(tmpinst);
    for (;;) {
      if (Character.isDigit(sb.charAt(pnt)) || sb.charAt(pnt) == '#') {
        tmpinst.append(sb.charAt(pnt));
      } else {
        break;
      }
      pnt++;
    }
    //prta("Doinstance inst="+tmpinst+" match="+match);
    ArrayList<EdgeMomInstance> instancesArray = EdgeMomInstance.getEdgeMomInstanceArrayList();
    for (EdgeMomInstance instance : instancesArray) {
      RoleInstance role = null;
      RoleInstance failoverrole = null;
      if (Util.stringBuilderEqual(tmpinst, instance.getEdgeMomInstance().trim())) {
        int roleid = instance.getRoleID();
        if (roleid > 0) {
          role = RoleInstance.getRoleWithID(roleid);
        }
        int failoverid = instance.getFailoverID();
        if (failoverid > 0) {
          failoverrole = RoleInstance.getRoleWithID(failoverid);
        }
        if (role != null) {
          if (match.contains("$INSTANCEIP-")) {
            Util.stringBuilderReplaceAll(sb, match + tmpinst, Util.cleanIP(role.getIP()));
          }
        }
        if (failoverrole != null) {
          if (match.contains("$INSTANCEFAILOVERIP-")) {
            Util.stringBuilderReplaceAll(sb, match + tmpinst, Util.cleanIP(failoverrole.getIP()));
          }
          if (match.contains("$INSTANCEFAILOVER-")) {
            Util.stringBuilderReplaceAll(sb, match + tmpinst, Util.cleanIP(failoverrole.getRole()));
          }

        } else {
          if (dbg) {
            prta("** INSTANCEFAILOVER[IP]- used for " + instance + " but this instance does not have a configured failover!");
          }
          if (match.contains("$INSTANCEFAILOVERIP-")) {
            Util.stringBuilderReplaceAll(sb, match + tmpinst, (role == null ? "Null" : Util.cleanIP(role.getIP())));
          }
          if (match.contains("$INSTANCEFAILOVER-")) {
            Util.stringBuilderReplaceAll(sb, match + tmpinst, (role == null ? "Null" : Util.cleanIP(role.getIP())));
          }
        }
        break;
      }
    }

  }

  private StringBuilder replaceAllVariables(StringBuilder sb, EdgeMomInstance instance, RoleInstance role) {
    Util.stringBuilderReplaceAll(sb, "\n\n", "\n");
    Util.stringBuilderReplaceAll(sb, "\n\n", "\n");
    Util.stringBuilderReplaceAll(sb, "\n\n", "\n");
    if (node != null) {
      Util.stringBuilderReplaceAll(sb, "$SERVER", node);
    }
    if (role != null) {
      Util.stringBuilderReplaceAll(sb, "$ROLEIP", Util.cleanIP(role.getIP()));
      Util.stringBuilderReplaceAll(sb, "$ROLE", role.getRole());
    }
    int loop = 0;
    int minpnt = 0;
    while (sb.indexOf("$INSTANCEIP-", minpnt) >= 0 || sb.indexOf("$INSTANCEFAILOVERIP-", minpnt) >= 0
            || sb.indexOf("$INSTANCEFAILOVER-", minpnt) >= 0) {
      int pnt = sb.indexOf("$INSTANCEIP-", minpnt);
      if (pnt >= 0) {
        doInstance(sb, "$INSTANCEIP-", minpnt);
        minpnt = pnt;
      }
      pnt = sb.indexOf("$INSTANCEFAILOVERIP-", minpnt);
      if (pnt >= 0) {
        doInstance(sb, "$INSTANCEFAILOVERIP-", minpnt);
        minpnt = pnt;
      }
      pnt = sb.indexOf("$INSTANCEFAILOVER-", minpnt);
      if (pnt >= 0) {
        doInstance(sb, "$INSTANCEFAILOVER-", minpnt);
        minpnt = pnt;
      }
      loop++;
      if (loop > 200) {
        prta("Stuck in translate loop for minpnt=" + minpnt + " sb=" + sb);
        minpnt++;
        if (minpnt > sb.length()) {
          break;
        }
      }
    }
    if (instance != null) {
      int instanceFailoverID = instance.getFailoverID();
      if (instanceFailoverID > 0) {
        RoleInstance roleFailover = RoleInstance.getRoleWithID(instanceFailoverID);
        Util.stringBuilderReplaceAll(sb, "$INSTANCEFAILOVERIP", Util.cleanIP(roleFailover.getIP()));
        Util.stringBuilderReplaceAll(sb, "$INSTANCEFAILOVER", roleFailover.getRole());
      }
      Util.stringBuilderReplaceAll(sb, "$INSTANCE", instance.getEdgeMomInstance());
    }
    return Util.stringBuilderReplaceAll(sb, "  ", " ");
  }

  private String replaceAllVariables(String s, EdgeMomInstance instance, RoleInstance role) {
    s = s.replaceAll("\n\n", "\n").replaceAll("\n\n", "\n").replaceAll("\n\n", "\n");
    if (node != null) {
      s = s.replaceAll("$SERVER", node);
    }
    if (role != null) {
      s = s.replaceAll("$ROLEIP", Util.cleanIP(role.getIP()));
      s = s.replaceAll("$ROLE", role.getRole());
    }
    // Do instance substitutions
    if (instance != null) {
      int instanceFailoverID = instance.getFailoverID();
      if (instanceFailoverID > 0) {
        RoleInstance roleFailover = RoleInstance.getRoleWithID(instanceFailoverID);
        s = s.replaceAll("$INSTANCEFAILOVERIP", Util.cleanIP(roleFailover.getIP()));
        s = s.replaceAll("$INSTANCEFAILOVER", roleFailover.getRole());
      }
      if (role != null) {
        s = s.replaceAll("$INSTANCEIP-", Util.cleanIP(role.getIP()));
      }
      s = s.replaceAll("$INSTANCE", instance.getEdgeMomInstance());
    }
    return s.replaceAll("  ", " ");
  }

  /**
   * this will add the threads for the given roll and account and return an
   * StringBuilder with the results. Duplicates tags and only threads will have
   * been eliminated if there are any in the given threads plus the added
   * threads from roll and account.
   *
   * @param threads List of threads, if not empty the roll/account will be added
   * to this list
   * @param instance the instance we are making and edgemom.setup for
   * @param sb The user String builder to use making this edgemom.setup
   * @return The string builder with the edgemom.setup file, and the amended set
   * of threads
   */
  public StringBuilder getEdgeMomSetupSB(TreeMap<String, EdgemomInstanceSetup> threads,
          EdgeMomInstance instance, StringBuilder sb) {
    Util.clear(sb);
    sb.append("#\n# Edgemomsetup file for - ").append(instance.getEdgeMomInstance()).append(" ");
    sb.append("\n#\n# Form is \"unique_key:Class_of_Thread:Argument line [>][>>filename no extension]\"\n#\n");
    if (threads != null) {
      Iterator<EdgemomInstanceSetup> itr = threads.values().iterator();
      while (itr.hasNext()) {
        EdgemomInstanceSetup thread = itr.next();
        sb.append(thread.getComment()).append("\n").append(thread.getCommandLine()).append("\n");
      }
    }
    replaceAllVariables(sb, instance, null);
    return Util.stringBuilderReplaceAll(sb, "  ", " ");
  }

  /**
   * given an array list of cpuRoles, make up a cpuRoles file
   *
   * @param roles ArrayList of cpuRoles
   * @param cpu The CPU which is having edgemom.setups built for (one or more
   * cpuRoles)
   * @param sb The StringBuilder to build this string in.
   * @return The text of the roles_nnnn file as a StringBuilder
   */
  public StringBuilder genRoleBuffer(ArrayList<RoleInstance> roles, Cpu cpu, StringBuilder sb) {
    Util.clear(sb).append("#!").append(Util.fs).append("bash -f\n# for node ").append(cpu.getCpu())
            .append("\nVDL_ROLES=\"");
    // Add on all of the given cpuRoles.
    for (RoleInstance role : roles) {
      sb.append(role.getRole().trim()).append(" ");
    }
    if (roles.size() > 0) {
      sb.deleteCharAt(sb.length() - 1);
    }
    sb.append("\"\nexport VDL_ROLES\n");
    return Util.stringBuilderReplaceAll(sb, "  ", " ");
  }

  /**
   * we have new roles. Make sure any new roles are down, and then bring up the
   * newRole.
   *
   * @param roles Array of strings with roles, that have been on this computer,
   * @param oldRoles List of roles now on this computer.
   * @return true, if anything has changed a dropped or added role
   */
  private boolean configRoles(String[] roles, ArrayList<RoleInstance> oldRoles) {

    // Now check each old newRole and insure it is still in new roles, if not, drop_ip that newRole.
    boolean ok;
    boolean ret = false;      // assume we will not do anything
    if (roles == null || oldRoles == null || !nPlusOne) {
      return false;
    }
    prta("CFRL: #roles=" + roles.length + " #assignedRoles=" + oldRoles);

    // Check for oldroles that have disappeared from the roles list (that is the role is no long on here
    for (RoleInstance oldRole : oldRoles) {
      ok = false;
      for (String role : roles) {
        if (oldRole.getRole().equals("") || role.equals("")) {
          continue;
        }
        if (oldRole.getRole().equals(role)) {
          if (dbg) {
            prta("CFRL: Found role " + oldRole + " in new roles.");
          }
          ok = true;
        }
      }
      if (!ok && !oldRole.getRole().trim().equals("")) {
        prta("CFRL: !!!!!!! configRoles need to drop removed role=" + oldRole);
        if (haveRole(oldRole.getRole()) != null) {
          drop_ip(oldRole);
          ret = true;       // dropped a role
        } else {
          prta("CRFL: !! role not on this node currently.  Skip drop_ip " + oldRole.getRole());
        }
      }
    }

    // Check the network interfaces for having a role which should not be assigned to us, if found, drop it.
    try {
      Enumeration<NetworkInterface> ifs = NetworkInterface.getNetworkInterfaces();
      while (ifs.hasMoreElements()) {
        Cpu cpu = cpus.get(Util.getSystemName());
        if (cpu != null) {
          int cpuID = cpu.getID();

          NetworkInterface ni = ifs.nextElement();
          Enumeration<InetAddress> adrs = ni.getInetAddresses();
          while (adrs.hasMoreElements()) {
            InetAddress adr = adrs.nextElement();
            String ipadr = adr.getHostAddress();
            if (ipadr.equals("127.0.0.1") || ipadr.contains(":")) {
              continue;
            }
            if (dbg) {
              prta("CFRL: NI=" + ni + " adr=" + adr + " hostAdr=" + ipadr);
            }
            Iterator<RoleInstance> iter = roleMap.values().iterator();
            while (iter.hasNext()) {
              RoleInstance r = iter.next();
              if (Util.cleanIP(r.getIP()).equals(ipadr)) {
                if (dbg) {
                  prta("CFRL: Found role=" + r + " matches " + ipadr);
                }
                if ((!r.isFailedOver() && r.getCpuID() != cpuID) || (r.isFailedOver() && r.getFailoverCpuID() != cpuID)) {
                  prta("CFRL: !!!!!!! Found role on this computer that should not be role=" + r
                          + " cpuid=" + cpuID + " r.cpuid=" + r.getCpuID() + " failed=" + r.isFailedOver() + " r.failCpuID=" + r.getFailoverCpuID() + " drop it");
                  drop_ip(r);
                }
              }
            }
          }
        }
      }

    } catch (SocketException e) {
      prta("CRFL: Socket exception getting interfaces e=" + e);
    }

    for (String newRole : roles) {  // for every role we are suppose to have
      if (newRole.equals("")) {
        continue;
      }
      // See if this role is alread on this computer
      ok = false;
      for (RoleInstance oldRole : oldRoles) {
        if (oldRole.getRole().equals("") || newRole.equals("")) {
          continue;
        }
        if (oldRole.getRole().equals(newRole)) {
          if (haveRole(newRole) != null) {
            ok = true;   // it has not been added 
          } else {
            for (String r : roles) {
              prta("role[] " + r);
            }
            for (RoleInstance rr : oldRoles) {
              prta("oldRole " + rr);
            }
            prta("CRFL: !! role should have already been assigned, but is not.  Add role =" + newRole);
          }
        }
      }
      if (ok) {
        if (dbg) {
          prta("CFRL: ! found role on both lists.  Skip become_ip " + newRole);
        }
        continue;
      }
      prta("CRFL: !!!!!!! restore state file for " + newRole);
      try {
        Subprocess sp = new Subprocess("bash restoreStateFiles.bash " + newRole);
        for (int i = 0; i < 10; i++) {
          try {
            sleep(500);
          } catch (InterruptedException expected) {
          }
          if (sp.exitValue() != -99) {
            break;
          }
          if (i == 9) {
            prta("CRFL: !!!!!! restoreStateFiles did not exit! terminate.");
            sp.terminate();
          }
        }
        prta("CRFL: !! restore state file stdout=" + sp.getOutput() + " err=" + sp.getErrorOutput());
      } catch (IOException e) {
        prta("CRFL: !!!!! IO error trying to restore state for " + newRole + " e=" + e);
      }
      String ipadr;
      InetAddress adr = null;
      Subprocess sp;
      prta("CRFL: !! new role found on node=" + newRole + " check for it being already up (bad), and if not become_ip on it");
      try {
        if ((ipadr = haveRole(newRole)) == null) {    // Do we have an interface for this address, we probably should not
          RoleInstance r = roleMap.get(newRole);
          if (r == null) {
            prta("CRFL: !!!!!!!  New roles is not on roles list.  How can this be " + newRole);
            continue;
          }
          ipadr = Util.cleanIP(r.getIP());
          // Need to make sure it is not responding on another node, ping it.
          int done = 0;
          int loop = 0;
          ret = true;
          while (done < 2) {
            prta("CFRL: !!!!!! Waiting for new role to go down on another server. Roles=" + ipadr);
            if (Util.getOS().contains("Linux")) {
              sp = new Subprocess("ping -n -c 2 -i 5 " + ipadr);
            } else {
              sp = new Subprocess("ping -n -c 2 -W 2 " + ipadr); // Unix -I MacOS -i
            }            //sp = new Subprocess("ping -n -i 60 "+ip);    // Unix -I MacOS -i
            sp.waitFor();
            String s = "Out: " + sp.getOutput() + "\nErr: " + sp.getErrorOutput();
            if (dbg) {
              prt("CRFL: status=" + sp.exitValue() + " " + s);
            }
            if (s.contains("icmp_seq=") && s.contains("time=")) {
              // its still up
              done = 0;
              loop++;
              if (loop % 10 == 0) {
                SendEvent.edgeSMEEvent("RoleHung", "The role at " + newRole + "/" + ipadr + " is not being given up", this);
              }

              if (loop % 40 == 0) {
                prta("CFRL: !!! Role for ip=" + ipadr + " is still up s=" + s);
                return ret;
              } else {
                prta("CRFL: Got some ping results loop=" + loop + " exitStatus=" + sp.exitValue());
              }
            } else {
              done++;
            }
          }
          prta("CFRL: !!!!!! Role is down. do become_ip " + newRole + "/" + ipadr);
          // The IP address is available, switch to it
          sp = new Subprocess("sudo bash " + Util.fs + "home" + Util.fs + "vdl" + Util.fs + "ROOT" + Util.fs + "become_ip " + newRole);
          sp.waitFor();
          if (sp.getOutput().length() > 0 || sp.getErrorOutput().length() > 0) {
            prta("CFRL: !! Become_ip output=" + sp.getOutput() + " " + sp.getErrorOutput());
          }
        } else {
          prta("CFRL: !! I am already ip=" + ipadr + " for new role=" + newRole);
        }
      } catch (SocketException e) {
        prt("CFRL: !!! Got socket exception looking up adr=" + adr);
      } catch (IOException e) {
        Util.IOErrorPrint(e, "CFRL: !! Trying to become a role");
      } catch (InterruptedException e) {
        prt("CFRL: !! Interrupted exception trying to become a role! e=" + e);
        e.printStackTrace();
      }
    }
    return ret;
  }

  /**
   * given a role string, check that an interface is up that has this address.
   *
   * @param newRole The role name
   * @return The address if the interface is found, else null.
   * @throws SQLException
   * @throws UnknownHostException
   */
  private String haveRole(String newRole) {
    RoleInstance role = roleMap.get(newRole);
    if (role == null) {
      return null;
    }
    String ipadr = Util.cleanIP(role.getIP());
    prta("HaveRole: newRole=" + newRole + " ipadr=" + ipadr);
    try {
      InetAddress adr = InetAddress.getByName(ipadr);
      NetworkInterface ni = NetworkInterface.getByInetAddress(adr);
      prta("HaveRole: adr=" + adr + " ni=" + ni);
      //ipadr = adr.getHostAddress();  // remove the annoying leading zeros
      if (ni == null) {
        return null;
      } else {
        return ipadr;      // This is not an address on the local node
      }
    } catch (SocketException | UnknownHostException e) {
      prta("haveRole() exception e=" + e);
      e.printStackTrace(getPrintStream());
    }
    return null;
  }

  private boolean drop_ip(RoleInstance role) {
    String ipadr = role.getIP();
    try {
      String s = "sudo bash " + Util.fs + "home" + Util.fs + "vdl" + Util.fs + "ROOT" + Util.fs + "drop_ip " + role.getRole();
      prta("Drop_IP: !!!! start process =" + s);
      Subprocess sp = new Subprocess(s);
      sp.waitFor();
      if (sp.getOutput().length() > 0 || sp.getErrorOutput().length() > 0) {
        prta("CFRL: !! drop ip out=" + sp.getOutput() + " err=" + sp.getErrorOutput());
      }
      if (ipadr == null) {
        return true;
      }
      InetAddress adr = InetAddress.getByName(ipadr);
      NetworkInterface ni = NetworkInterface.getByInetAddress(adr);
      ipadr = adr.getHostAddress();  // remove the annoying leading zeros
      if (ni == null) {      // This is not an address on the local node
        prta("CFRL: !!!!!! drop_ip was successful for " + role.getRole() + "/" + ipadr);
        return true;
      } else {
        prta("CFRL: !!!!!! drop_ip was NOT successful for " + role.getRole() + "/" + ipadr);
      }
    } catch (IOException e) {
      prta("CFRL: !! Drop_IP threw IOError e=" + e);
      e.printStackTrace();
    } catch (InterruptedException e) {
      prta("CFRL: !! Drop_IP threw Interrupted e=" + e);
    }
    return false;
  }

  /**
   * Test main routine
   *
   * @param args Unused command line args
   */
  public static void main(String[] args) {
    EdgeThread.setMainLogname("ecit");
    Util.init("edge.prop");
    String line = "";
    for (String arg : args) {
      line += arg + " ";
    }
    if (line.trim().equals("")) {
      line = "-init";
    }
    EdgemomConfigInstanceThread thr = new EdgemomConfigInstanceThread(line, "ECIT");
    EdgemomConfigInstanceThread.setDebug(true);
    //String [] cpuRoles = {"gtest1"};
    //String node = "gldketchum3";
    //EdgemomConfigFiles.main(args);  // generate all of the configuration files
    //thr.moveFiles(cpuRoles, node);
    //Util.prt("Become cpuRoles returned="+thr.configRoles(cpuRoles,cpuRoles));
    for (;;) {
      if (!thr.isAlive()) {
        Util.exit(1);
      }
      Util.sleep(10000);
    }
  }
}
