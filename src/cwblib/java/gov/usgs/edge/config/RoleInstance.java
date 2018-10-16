/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.edge.config;

import gov.usgs.anss.db.DBConnectionThread;
import gov.usgs.anss.util.Util;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;

/**
 * RoleInstance.java If the name of the class does not match the name of the field in the class you
 * may need to modify the rs.getSTring in the constructor.
 *
 * This RoleInstance templace for creating a database database object. It is not really needed by
 * the RolePanel which uses the DBObject system for changing a record in the database file. However,
 * you have to have this shell to create objects containing a database record for passing around and
 * manipulating. There are two constructors normally given:
 * <br>
 * 1) passed a result set positioned to the database record to decode
 * <br> 2) Passed all of the data arguments needed to build the same set of data.
 * <br>
 * The RoleInstance should be replaced with capitalized version (class name) for the file. role
 * should be replaced with the lower case version (for variables).
 *
 * Both the constructors use the "makeRole(list of data args) to set the local variables to the
 * value. The result set just uses the rs.get???("fieldname") to get the data from the database
 * where the other passes the arguments from the caller.
 *
 * <br> Notes on Enums :
 * <br> * data class should have code like :
 * <br> import java.util.ArrayList; /// Import for Enum manipulation
 * <br> .
 * <br> static ArrayList fieldEnum; // This list will have Strings corresponding to enum
 * <br> .
 * <br> .
 * <br> // To convert an enum we need to create the fieldEnum ArrayList. Do only once(static)
 * <br> if(fieldEnum == null) fieldEnum = FUtil.getEnum(UC.getConnection(),"table","fieldName");
 * <br>
 * <br> // Get the int corresponding to an Enum String for storage in the data base and
 * <br> // the index into the JComboBox representing this Enum
 * <br> localVariable = FUtil.enumStringToInt(fieldEnum, enumString);
 * <br> Created on July 8, 2002, 1:23 PM
 *
 * @author D.C. Ketchum
 */
public final class RoleInstance {   //implements Comparator
  // static ArrayList enumName;   // this need to be populated in makeRole()

  public static ArrayList<RoleInstance> roleInstances;             // Vector containing objects of this RoleInstance Type

  /**
   * Creates a new instance of RoleInstance
   */
  private int ID;                   // This is the database ID (should alwas be named "ID"
  private String role;             // This is the main key which should have same name
  // as the table which it is a key to.  That is
  // Table "category" should have key "category"

  // USER: All fields of file go here
  //  double longitude
  private String description;
  private String ipadr;
  //String cpu;
  private int cpuID;
  private int hasdata;
  private String enabledAccounts;
  private int failoverCpuID, failover;
  private int alarm, alarmmem, alarmbhchecker, alarmcmdstatus, alarmconfig, alarmdbmsgsrv, alarmetchosts;
  private int alarmlatency, alarmroutes, alarmsnw, alarmsnwsender, alarmudpchannel, alarmwebserver;
  private String alarmargs;
  private int aftac, consoles, metadata, quakeml, querymom, rts, smgetter, tcpholdings;
  private String crontab;
  private long alarmProcMask;
  private int process1, process2, process3, process4, process5, process6, process7, process8, process9;

  // Put in correct detail constructor here.  Use makeRole() by filling in all
  // the rs.get???("fieldName") for all of the arguments
  /**
   * Create a instance of this table row from a positioned result set
   *
   * @param rs The ResultSet already SELECTED
   * @exception SQLException SQLException If the ResultSet is Improper, network is down, or database
   * is down
   */
  public RoleInstance(ResultSet rs) throws SQLException {  //USER: add all data field here
    makeRole(rs.getInt("ID"), rs.getString("role"),
            rs.getString("description"),
            rs.getString("ipadr"),
            rs.getInt("cpuID"),
            rs.getInt("hasdata"), rs.getString("accounts"),
            rs.getInt("failovercpuid"), rs.getInt("failover"),
            rs.getInt("alarm"), rs.getInt("alarmmem"), rs.getInt("alarmbhchecker"),
            rs.getInt("alarmcmdstatus"), rs.getInt("alarmconfig"), rs.getInt("alarmdbmsgsrv"),
            rs.getInt("alarmetchosts"),
            rs.getInt("alarmlatency"), rs.getInt("alarmroutes"), rs.getInt("alarmsnw"),
            rs.getInt("alarmsnwsender"), rs.getInt("alarmudpchannel"), rs.getInt("alarmwebserver"),
            rs.getString("alarmargs"), rs.getInt("aftac"), rs.getInt("consoles"),
            rs.getInt("metadata"), rs.getInt("quakeml"), rs.getInt("querymom"), rs.getInt("rts"),
            rs.getInt("smgetter"), rs.getInt("tcpholdings"),
            rs.getString("crontab"),
            rs.getLong("alarmprocmask"), rs.getInt("proc1mem"), rs.getInt("proc2mem"),
            rs.getInt("proc3mem"), rs.getInt("proc4mem"), rs.getInt("proc5mem"),
            rs.getInt("proc6mem"), rs.getInt("proc7mem"), rs.getInt("proc8mem"), rs.getInt("proc9mem")
    );
    // ,rs.getDouble(longitude)

  }

  /**
   * Reload instance of this table row from a positioned result set
   *
   * @param rs The ResultSet already SELECTED
   * @exception SQLException SQLException If the ResultSet is Improper, network is down, or database
   * is down
   */
  public void reload(ResultSet rs) throws SQLException {
    makeRole(rs.getInt("ID"), rs.getString("role"),
            rs.getString("description"),
            rs.getString("ipadr"),
            rs.getInt("cpuID"),
            rs.getInt("hasdata"), rs.getString("accounts"),
            rs.getInt("failovercpuid"), rs.getInt("failover"),
            rs.getInt("alarm"), rs.getInt("alarmmem"), rs.getInt("alarmbhchecker"),
            rs.getInt("alarmcmdstatus"), rs.getInt("alarmconfig"), rs.getInt("alarmdbmsgsrv"),
            rs.getInt("alarmetchosts"),
            rs.getInt("alarmlatency"), rs.getInt("alarmroutes"), rs.getInt("alarmsnw"),
            rs.getInt("alarmsnwsender"), rs.getInt("alarmudpchannel"), rs.getInt("alarmwebserver"),
            rs.getString("alarmargs"), rs.getInt("aftac"), rs.getInt("consoles"),
            rs.getInt("metadata"), rs.getInt("quakeml"), rs.getInt("querymom"), rs.getInt("rts"),
            rs.getInt("smgetter"), rs.getInt("tcpholdings"),
            rs.getString("crontab"),
            rs.getLong("alarmprocmask"), rs.getInt("proc1mem"), rs.getInt("proc2mem"),
            rs.getInt("proc3mem"), rs.getInt("proc4mem"), rs.getInt("proc5mem"),
            rs.getInt("proc6mem"), rs.getInt("proc7mem"), rs.getInt("proc8mem"), rs.getInt("proc9mem")
    );
  }

  // Detail Constructor, match set up with makeRole(), this argument list should be
  // the same as for the result set builder as both use makeRole()
  /**
   * create a row record from individual variables
   *
   * @param inID The ID in the database, normally zero for this type of construction
   * @param loc The key value for the database (role) (same a name of database table)
   * @param inDesc The description
   * @param inIPadr The IP address of the role;
   * @param inHasdata the Has data flag - normally this is modified by the GUI and not user editable
   * @param inCpuID THe ID of the CPU assigned this role
   * @param inAccounts Accounts to check
   * @param failCpuID The CPU ID to fail over to
   * @param fail If non-zero, fail over this system to the fail over Cpu ID
   * @param alarm boolean for Alarm, if zero, alarm does not run
   * @param almem memory allocation for alarm
   * @param albh boolean run HughesNOCChecker (NEIC only)
   * @param alcmd run CommandStatusServer
   * @param alcfg run EdgemomConfigInstanceThread
   * @param aldbmsg run DBMessageServer
   * @param aletc run MakeEtcHosts (NEIC only)
   * @param allatency run LatencyServer
   * @param alroutes run MakeVsatRoutes (NEIC only)
   * @param alsnw run the SNWChannel (gather SNW information)
   * @param alsnwsend run the SNWChannelSender (send it to a SNW Server)
   * @param aludpchan run UdpChannel
   * @param alweb run WebServer (NEIC)
   * @param alargs addition arguments to Alarm startup line
   * @param aftac memory for AftacHoldingsReport if zero do not run
   * @param consoles memory for AftacHoldingsReport if zero do not run
   * @param metadata memory for AftacHoldingsReport if zero do not run
   * @param quakeml memory for AftacHoldingsReport if zero do not run
   * @param querymom memory for AftacHoldingsReport if zero do not run
   * @param rts memory for AftacHoldingsReport if zero do not run
   * @param smgetter memory for AftacHoldingsReport if zero do not run
   * @param tcphold memory for AftacHoldingsReport if zero do not run
   * @param cron The Crontab associated with this role
   * @param alarmmask Mask of things to run in Alarm to extend the above possibilits
   * @param proc1 memory for extending above if zero do not run
   * @param proc2 memory for extending above if zero do not run
   * @param proc3 memory for extending above if zero do not run
   * @param proc4 memory for extending above if zero do not run
   * @param proc5 memory for extending above if zero do not run
   * @param proc6 memory for extending above if zero do not run
   * @param proc7 memory for extending above if zero do not run
   * @param proc8 memory for extending above if zero do not run
   * @param proc9 memory for extending above if zero do not run
   */
  public RoleInstance(int inID, String loc //USER: add fields, double lon
          ,
           String inDesc, String inIPadr, int inCpuID, int inHasdata, String inAccounts,
          int failCpuID, int fail,
          int alarm, int almem, int albh, int alcmd, int alcfg, int aldbmsg, int aletc,
          int allatency, int alroutes, int alsnw, int alsnwsend, int aludpchan, int alweb,
          String alargs, int aftac, int consoles, int metadata, int quakeml, int querymom,
          int rts, int smgetter, int tcphold, String cron, long alarmmask,
          int proc1, int proc2, int proc3,
          int proc4, int proc5, int proc6, int proc7, int proc8, int proc9
  ) {
    makeRole(inID, loc, inDesc, inIPadr, inCpuID, inHasdata, inAccounts, //, lon
            failCpuID, fail,
            alarm, almem, albh, alcmd, alcfg, aldbmsg, aletc,
            allatency, alroutes, alsnw, alsnwsend, aludpchan, alweb,
            alargs, aftac, consoles, metadata, quakeml, querymom,
            rts, smgetter, tcphold, cron, alarmmask, proc1, proc2, proc3,
            proc4, proc5, proc6, proc7, proc8, proc9
    );
  }

  // This routine transfers to the local variables of this object all of the data variables
  // associated with this file.
  /**
   * internally set all of the field in our data to the passed data
   *
   * @param inID The row ID in the database
   * @param loc The key (same as table name)
   *
   */
  private void makeRole(int inID, String loc //USER: add fields, double lon
          ,
           String inDesc, String inIPadr, int inCpuID, int inHasdata, String inAccounts,
          int failCpuID, int fail,
          int alarm, int almem, int albh, int alcmd, int alcfg, int aldbmsg, int aletc,
          int allatency, int alroutes, int alsnw, int alsnwsend, int aludpchan, int alweb,
          String alargs, int aftac, int consoles, int metadata, int quakeml, int querymom,
          int rts, int smgetter, int tcphold, String cron, long alarmmask,
          int proc1, int proc2, int proc3,
          int proc4, int proc5, int proc6, int proc7, int proc8, int proc9
  ) {
    ID = inID;
    role = loc;     // longitude = lon
    //USER:  Put asssignments to all fields from arguments here
    description = inDesc;
    ipadr = inIPadr;
    cpuID = inCpuID;
    hasdata = inHasdata;
    enabledAccounts = inAccounts;
    failover = fail;
    failoverCpuID = failCpuID;
    this.alarm = alarm;
    alarmmem = almem;
    alarmbhchecker = albh;
    alarmcmdstatus = alcmd;
    alarmconfig = alcfg;
    alarmdbmsgsrv = aldbmsg;
    alarmetchosts = aletc;
    alarmlatency = allatency;
    alarmroutes = alroutes;
    alarmsnw = alsnw;
    alarmsnwsender = alsnwsend;
    alarmudpchannel = aludpchan;
    alarmwebserver = alweb;
    alarmargs = alargs;
    this.aftac = aftac;
    this.consoles = consoles;
    this.metadata = metadata;
    this.quakeml = quakeml;
    this.querymom = querymom;
    this.rts = rts;
    this.smgetter = smgetter;
    tcpholdings = tcphold;
    crontab = cron;
    this.alarmProcMask = alarmmask;
    process1 = proc1;
    process2 = proc2;
    process3 = proc3;
    process4 = proc4;
    process5 = proc5;
    process6 = proc6;
    process7 = proc7;
    process8 = proc8;
    process9 = proc9;
    // ENUM example:
    //if(qtypeEnum == null) qtypeEnum=FUtil.getEnum(UC.getConnection(), "nsnstation","quanterraType");
    //qtype = FUtil.enumStringToInt(qtypeEnum, st); 
    // Boolean example where ENUM "True", "False
    // cont40 = FUtil.isTrue(c40);          // c40 is a string with "true" or false

  }

  /**
   * return the key name as the string
   *
   * @return the key name
   */
  public String toString2() {
    return role;
  }

  /**
   * return the key name as the string
   *
   * @return the key name
   */
  @Override
  public String toString() {
    return role;
  }
  // getter

  // standard getters
  /**
   * get the database ID for the row
   *
   * @return The database ID for the row
   */
  public int getID() {
    return ID;
  }

  /**
   * get the key name for the row
   *
   * @return The key name string for the row
   */
  public String getRole() {
    return role;
  }

  public String getIP() {
    return ipadr;
  }

  public String getDescription() {
    return description;
  }

  public int getCpuID() {
    return cpuID;
  }

  public int getHasData() {
    return hasdata;
  }

  public String getEnabledAccounts() {
    return enabledAccounts;
  }

  public boolean isFailedOver() {
    return failover == 1;
  }

  public int getFailoverCpuID() {
    return failoverCpuID;
  }

  public String getCrontab() {
    return crontab;
  }

  public boolean runAlarm() {
    return alarm == 1;
  }

  public int getAlarmMemory() {
    return alarmmem;
  }

  public boolean runBHChecker() {
    return alarmbhchecker == 1;
  }

  public boolean runCommandStatus() {
    return alarmcmdstatus == 1;
  }

  public boolean runCWBConfig() {
    return alarmconfig == 1;
  }

  public boolean runDBMsg() {
    return alarmdbmsgsrv == 1;
  }

  public boolean runEtcHosts() {
    return alarmetchosts == 1;
  }

  public boolean runLatency() {
    return alarmlatency == 1;
  }

  public boolean runRoutes() {
    return alarmroutes == 1;
  }

  public boolean runSNW() {
    return alarmsnw == 1;
  }

  public boolean runSNWSender() {
    return alarmsnwsender == 1;
  }

  public boolean runUdpChannel() {
    return alarmudpchannel == 1;
  }

  public boolean runWebServer() {
    return alarmwebserver == 1;
  }

  public String getAlarmArgs() {
    return alarmargs;
  }

  public int getAftacMemory() {
    return aftac;
  }

  public int getConsolesMemory() {
    return consoles;
  }

  public int getMetadataMemory() {
    return metadata;
  }

  public int getQuakeMLMemory() {
    return quakeml;
  }

  public int getQueryMomMemory() {
    return querymom;
  }

  public int getRTSMemory() {
    return rts;
  }

  public int getSMGetterMemory() {
    return smgetter;
  }

  public int getTcpHoldingsMemory() {
    return tcpholdings;
  }

  public long getAlarmProcessMask() {
    return alarmProcMask;
  }

  public static RoleInstance getRoleWithID(int ID) {
    makeRoles();
    for (RoleInstance role : roleInstances) {
      if (role.getID() == ID) {
        return role;
      }
    }
    return null;
  }
  // This routine should only need tweeking if key field is not same as table name

  public static void makeRoles() {
    if (roleInstances != null) {
      return;
    }
    roleInstances = new ArrayList<>(100);
    try {
      try (Statement stmt = DBConnectionThread.getConnection(DBConnectionThread.getDBSchema()).createStatement() // used for query
              ) {
        String s = "SELECT * FROM edge.role ORDER BY role;";
        try (ResultSet rs = stmt.executeQuery(s)) {
          while (rs.next()) {
            RoleInstance loc = new RoleInstance(rs);
//        Util.prt("MakeRole() i="+v.size()+" is "+loc.getRole());
            roleInstances.add(loc);
          }
        }
      }
    } catch (SQLException e) {
      Util.SQLErrorPrint(e, "makeRoles() on table SQL failed");
    }
  }
}
