/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */

package gov.usgs.anss.guidb;
/*
 * TCPStation.java
 * If the name of the class does not match the name of the field in the class you may
 * need to modify the rs.getSTring in the constructor.
 *
 * Created on July 8, 2002, 1:23 PM
 */

/**
 *
 * @author  ketchum
 * This TCPStation templace for creating a DBConnectionTh database object.  It is not
 * really needed by the TCPStationPanel which uses the DBObject system for
 * changing a record in the database file.  However, you have to have this
 * shell to create objects containing a DBConnectionTh record for passing around
 * and manipulating.  There are two constructors normally given:
 * 1)  passed a result set positioned to the DBConnectionTh record to decode
 * 2) Passed all of the data arguments needed to build the same set
 *     of data.  
 *
 * The TCPStation should be replaced with capitalized version (class name) for the 
 * file.  tcpstation should be replaced with the lower case version (for variables).
 *
 * Both the constructors use the "makeTCPStation(list of data args) to set the
 * local variables to the value.  The result set just uses the 
 * rs.get???("fieldname") to get the data from the database where the other
 * passes the arguments from the caller.
 * 
 * Notes on ENums:
 * data class should have code like :
 * import java.util.ArrayList;          /// Import for Enum manipulation
 *   .
 *  static ArrayList fieldEnum;         // This list will have Strings corresponding to enum
 *       .
 *       .
 *    // To convert an enum we need to create the fieldEnum ArrayList.  Do only once(static)
 *    if(fieldEnum == null) fieldEnum = FUtil.getEnum(DBConnectionThread.getConnection("anss"),"table","fieldName");
 *    
 *   // Get the int corresponding to an Enum String for storage in the data base and 
 *   // the index into the JComboBox representing this Enum 
 *   localVariable = FUtil.enumStringToInt(fieldEnum, enumString);
 */


//import java.util.Comparator;

import gov.usgs.anss.gui.UC;
import gov.usgs.anss.db.DBConnectionThread;
//import gov.usgs.anss.gui.CommLinkPanel;
//import gov.usgs.anss.gui.CpuLinkIPPanel;
//Cpu.import gov.usgs.anss.gui.CpuPanel;
//import gov.usgs.anss.gui.RTSStationPanel;
import gov.usgs.anss.util.*;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.StringTokenizer;



public class TCPStation     //implements Comparator
{
  public static ArrayList<TCPStation> tcpstations;             // ArrayList containing objects of this RTSStation Type
  final public static int RTS=1,CONSOLE=2,DATA=3,GPS=4,MSHEAR=5, NONE=6;
  static final ArrayList stateEnum=FUtil.getEnum(DBConnectionThread.getConnection("anss"), "tcpstation","state");
  static final ArrayList powerTypeEnum = FUtil.getEnum(DBConnectionThread.getConnection("anss"), "tcpstation", "powerType");
  static final ArrayList rtsEnum = FUtil.getEnum(DBConnectionThread.getConnection("anss"),"tcpstation", "rtsport1");
  /** Creates a new instance of TCPStation */
  int ID;                   // This is the DBConnectionTh ID (should alwas be named "ID"
  String tcpstation;             // This is the main key which should have same name
                            // as the table which it is a key to.  That is
                            // Table "category" should have key "category"
  
  // All fields of file go here
  //  double longitude
  String ipadr, consoleIP, powerIP;
  int port, consolePort, powerPort, localPort;
  int timeout, consoleTimeout;
  java.sql.Date suppressDate;
  int gomberg, rollback, ctrlq, state;
  int powerType;
  int rtsport1, rtsport2,rtsport3,rtsport4; // The configuration of each rtsport
  int rtsClient1, rtsClient2, rtsClient3, rtsClient4;
  int commlinkID,commlinkOverrideID;
  int cpuID;
  
  // Q330 related variables
  String q330;
  String tunnelPorts;
  int allowpoc;
  InetAddress rtsaddr;
  InetAddress [] q330adr;
  InetAddress [] q330NatAdr;
  int [] q330ports;
  int [] tunnelports;
  int [] hostPorts;
  int [] dataPort;
  String [] kmiTags;
  String [] tags;           // station + an optional number if #q330 >1
  String [] hexSerials;
  String [] authCodes;
  long lastGetHexSerials;
  String [] q330s;          // serial # of one or more q330 (if q330 is not "");"
  String [] q330stations;   // The station names in each one WMOK, WWO1, etc.
  String seedNetwork;
  boolean isTunneled;
  boolean useBH;
  private static DBConnectionThread dbconn;
    
 // Put in correct detail constructor here.  Use makeTCPStation() by filling in all
  // the rs.get???("fieldName") for all of the arguments
  public TCPStation(ResultSet rs) throws SQLException {
    makeTCPStation(rs.getInt("ID"), rs.getString("tcpstation"),rs.getInt("commlinkID"),
        rs.getInt("commlinkOverrideID"),
        rs.getString("ipadr"), rs.getInt("port"), rs.getInt("timeout"), rs.getInt("localPort"),
        rs.getString("state"),
        rs.getInt("cpuID"), rs.getDate("suppressDate"),      // Main port parameters
        FUtil.isTrue(rs.getString("gomberg")),FUtil.isTrue(rs.getString("rollback")), 
        FUtil.isTrue(rs.getString("ctrlq")),
        rs.getString("consoleIP"), rs.getInt("consolePort"), rs.getInt("consoleTimeout"),
        rs.getString("powerType"), rs.getString("powerIP"), rs.getInt("powerPort"),
        rs.getString("rtsport1"), rs.getString("rtsport2"), 
        rs.getString("rtsport3"), rs.getString("rtsport4"),
        rs.getInt("rtsclient1"),rs.getInt("rtsclient2"),
        rs.getInt("rtsclient3"),rs.getInt("rtsclient4"),
        rs.getString("q330"),
        rs.getString("tunnelports"),
        rs.getInt("allowpoc"), FUtil.isTrue(rs.getString("q330tunnel")),rs.getInt("usebh")
    );
  }
  // Detail Constructor, match set up with makeTCPStation(), this argument list should be
  // the same as for the result set builder as both use makeTCPStation() 
  public TCPStation(int inID, String loc   //, double lon
    , int comID, int comoverID, String ip, int pt, int to, int lp, String st, 
    int nd, java.sql.Date dt, int gb, int rl, int cq,
    String cip, int cpt, int cto, 
    String pwrtyp, String pwrIP, int pwrpt, 
    String rts1, String rts2, String rts3, String rts4, 
    int cl1, int cl2, int cl3, int cl4,
      String q3, String tports, int poc, int tunnelon, int usebh
    ) {
    makeTCPStation(inID, loc ,      //, lon
        comID, comoverID, ip, pt, to, lp,  st, nd, dt, gb, rl,cq, cip, cpt, cto, pwrtyp, pwrIP, pwrpt, 
        rts1, rts2, rts3,rts4, cl1, cl2, cl3, cl4, q3, tports, poc, tunnelon, usebh
    );
  }
  
  // This routine transfers to the local variables of this object all of the data variables
  // associated with this file.  ENum notes :  the enums are created with Strings which come
  // from the data base.  They must be converted in ints for use here.  For each enum class 
  // create a 
    /*    if(fieldEnum == null) fieldEnum = FUtil.getEnum(ConnectionThread.getConnection("anss"),"table","fieldName");
 *    
 *   // Get the int corresponding to an Enum String for storage in the data base and 
 *   // the index into the JComboBox representing this Enum 
 *   localVariable = FUtil.enumStringToInt(fieldEnum, enumString);*/
  private void makeTCPStation(int inID, String loc    //, double lon
    ,int comID, int commoverID, String ip, int pt, int to, int lp, String st, 
    int nd, java.sql.Date dt, int gb, int rl, int cq,
    String cip, int cpt, int cto, 
    String pwrtyp, String pwrIP, int pwrpt, 
    String rts1, String rts2, String rts3, String rts4, 
    int cl1, int cl2, int cl3, int cl4,
      String q3, String tports, int poc, int tunnelon, int usebh) {
    ID = inID;  tcpstation=loc;     // longitude = lon
    if(DBConnectionThread.getConnection("anss")== null) {
      Util.prt("  **** makeTCPStation had to make an anss connection!");
      try {
        DBConnectionThread temp = new DBConnectionThread(Util.getProperty("DBServer"), "readonly","anss",
                false, false, "anss", Util.getOutput());
      }
      catch(InstantiationException  e) {
        e.printStackTrace();
      }
    }
    // Put asssignments to all fields from arguments here
    commlinkID=comID;
    commlinkOverrideID=commoverID;
    ipadr = ip; port = pt; timeout = to; localPort=lp; gomberg=gb; rollback=rl; ctrlq=cq; 
    state = FUtil.enumStringToInt(stateEnum, st); 
    cpuID= nd;
    consoleIP=cip; consolePort=cpt; consoleTimeout=cto;
    powerType = FUtil.enumStringToInt(powerTypeEnum, pwrtyp);
    powerIP = pwrIP; powerPort = pwrpt;
    rtsport1 = FUtil.enumStringToInt(rtsEnum, rts1);
    rtsport2 = FUtil.enumStringToInt(rtsEnum, rts2);
    rtsport3 = FUtil.enumStringToInt(rtsEnum, rts3);
    rtsport4 = FUtil.enumStringToInt(rtsEnum, rts4);
    rtsClient1 = cl1; rtsClient2 = cl2; rtsClient3 = cl3; rtsClient4 = cl4;
    
    // parse and save the Q330 parameters
    q330=q3; tunnelPorts=tports; allowpoc=poc; isTunneled=(tunnelon!=0);
    useBH = (usebh != 0);

    InetAddress saverts;
    q330adr=null;
    q330NatAdr=null;
    kmiTags=null;
    q330s = null;
    hexSerials=null;
    authCodes=null;
    q330ports=null;
    tunnelports=null;
    hostPorts=null;
    byte [] rtsaddrbytes;
    q330stations = null;
 

    try {
      rtsaddr= InetAddress.getByName(ipadr);
      saverts = rtsaddr;
    }
    catch(UnknownHostException e) {
      Util.prta(" TCPStation: Got bad RTS from ip="+ipadr+" discard.");
      return;
    }
    if(q330.length() > 0) {
      //String [] q330s = tunnelPorts.split(",");
      kmiTags = q330.split(",");
      //int mx = Math.max(kmiTags.length, kmiTags.length);

      int mx=kmiTags.length;
      hexSerials = new String[mx];
      authCodes = new String[mx];
      q330adr = new InetAddress[mx];
      q330NatAdr = new InetAddress[mx];
      tunnelports = new int[mx];
      hostPorts = new int[mx];
      q330ports = new int[mx]; 
      tags = new String[mx];
      q330stations = new String[mx];
      dataPort = new int[mx];
      for(int i=0; i<kmiTags.length; i++) {
        String [] parts = kmiTags[i].split(":");
        if(parts.length > 1) {
          kmiTags[i] = parts[0];
          dataPort[i] = Integer.parseInt(parts[1]);
        }       
        else dataPort[i] = 1;
      }
      hexSerials = getHexSerials();       // return hex serial, and hostPorts
      for(int i=0; i<kmiTags.length; i++) {
        q330ports[i] = 5330;              // default ports

        //if(isTunneled) 
        tunnelports[i] = hostPorts[i] - 2000;    // This is the 9000 range
        tags[i] = tcpstation;
        if(i > 0) {
          if(tags[i].length()>= 4) {
            if(tags[i].substring(3,4).equals("1")) tags[i] = tags[i].substring(0,3)+"2";
            else tags[i] = tags[i].substring(0,3)+i;
          }
          else tags[i] = tags[i]+i;
        }
        q330stations[i] = tcpstation;
        if(i > 0) {
          if(q330stations[i].length() >= 4) {
            if(q330stations[i].substring(3,4).equals("1")) 
              q330stations[i]=q330stations[i].substring(0,3)+"2";
            else q330stations[i] = q330stations[i].substring(0,3)+i;
          }
          else q330stations[i] = q330stations[i]+i;     // 3 character names.
        }

        rtsaddrbytes = saverts.getAddress();
        if(allowpoc == 0) rtsaddrbytes[3] += (i+1);
        else q330ports[i]= 5330+i*1000;
        try {
          q330adr[i] = InetAddress.getByAddress(rtsaddrbytes);
          q330NatAdr[i]= q330adr[i];    // default to public address
          if(allowpoc == 1) {
            q330NatAdr[i] = InetAddress.getByName("192.168.1."+(i+3));
          }
        }
        catch(UnknownHostException e) {
          Util.prt("Unknown host exception in adr derived from RTS base="+
              rtsaddrbytes[0]+"."+rtsaddrbytes[1]+"."+ rtsaddrbytes[2]+"."+rtsaddrbytes[3]);
        }
      
      }
      
      /*for(int i=0; i<q330s.length; i++) {
        tags[i] = tcpstation;
        if(i > 0) {
          if(tags[i].length()>= 4) {
            if(tags[i].substring(3,4).equals("1")) tags[i] = tags[i].substring(0,3)+"2";
            else tags[i] = tags[i].substring(0,3)+i;
          }
          else tags[i] = tags[i]+i;
        }
        q330stations[i] = tcpstation;
        if(i > 0) {
          if(q330stations[i].length() >= 4) {
            if(q330stations[i].substring(3,4).equals("1")) 
              q330stations[i]=q330stations[i].substring(0,3)+"2";
            else q330stations[i] = q330stations[i].substring(0,3)+i;
          }
          else q330stations[i] = q330stations[i]+i;     // 3 character names.
        }
        rtsaddrbytes = saverts.getAddress();
        String [] tokens = q330s[i].split(":");
        String outputPort = tokens[0];
        if(tokens.length == 1 ) {
          if( poc == 0) rtsaddrbytes[3]+=1+i;
          port=5330;
        } 
        else {

          String [] adrtokens = tokens[1].split("/");
          port =5330;
          if(adrtokens.length > 1) port = Integer.parseInt(adrtokens[1]);
          if(adrtokens[0].substring(0,1).equals(".")) {
            // This is a poc site so do not disturb the addres
            if(allowpoc == 0) Util.prta("Got a "+adrtokens[0]+" but POC is not on!!!!");
          }
          else if(adrtokens[0].substring(0,1).equals("+")) {  //its a + address
            int number = Integer.parseInt(adrtokens[0].substring(1));
            rtsaddrbytes[3] += number;
          }
          else {          // It must be a full IP address
            try {
              rtsaddr= InetAddress.getByName(adrtokens[0]);
              rtsaddrbytes = rtsaddr.getAddress();
            }
            catch(UnknownHostException e) {
              Util.prta(" TCPStation: Got bad RTS from config=="+tags[i]+" "+ipadr+" discard.");
              continue;
            } 
          }
        }
        // The address of the Q330 is in rtsaddressbytes
        try {
         
          q330ports[i] = port;
          q330adr[i] = InetAddress.getByAddress(rtsaddrbytes);
          tunnelports[i]=-1;
          if(outputPort.trim().equals("")) tunnelports[i]=-1;
          else tunnelports[i] = Integer.parseInt(outputPort);
        }
        catch(NumberFormatException e) {
          Util.prt("the tunnelports gives number format exception at "+tcpstation+" string="+tunnelports);
        }
        catch(UnknownHostException e) {
          Util.prta("Unknow host exception bytes ="+rtsaddrbytes[0]+"."+rtsaddrbytes[1]+"."+
              rtsaddrbytes[2]+"."+rtsaddrbytes[3]);
          continue;
        }    
      }   //for each q330
      */
    }     // if q330 field is not """

  }
  public String toString2() { return tcpstation;}
  @Override
  public String toString() { return tcpstation;}
  // getter
  
  // standard getters
  public int getID() {return ID;}
  public int getCPUID() {return cpuID;}
  public String getTCPStation() {return tcpstation;}
  public String getIP() { return ipadr;}
  public int getPort() { return port;}
  public int getState() { return state;}
  public String getStateString() { return stateEnum.get(state).toString();}
  public int getCommLinkID() {return commlinkID;}
  public int getCommlinkOverrideID() {return commlinkOverrideID;}
  public String getCpuTunnelIP() {
    String ans = getCpuLinkIP();
    if(ans.contains("10.177")) {   // on new NOC tunnels must go on public address unless useBH is on
      if(useBH) return ans;
      return CpuLinkIP.getCpuLinkIPfromIDs(cpuID, 4).getIP();
    }
    return ans;
  }
  public String getCpuLinkIP() {
    CpuLinkIP p;
    if(commlinkID == commlinkOverrideID) 
      p =  CpuLinkIP.getCpuLinkIPfromIDs(cpuID, commlinkID);
    else 
      p =  CpuLinkIP.getCpuLinkIPfromIDs(cpuID, commlinkOverrideID);
      
    if(p == null) return "000.000.000.000";
    else return p.getIP();
  }
  
  // All field getters here
  //  public double getLongitude() { return longitude;}
  
  
  // This is needed if you need to implement Comparator
  /*  public class EquipCompare implements Comparator {
    public int compare(Object a, Object b) {
      return ((TCPStation) a).getTCPStation().compareTo(((TCPStation) b).getTCPStation());
    }
    public boolean equals(Object a, Object b) {
      if( ((TCPStation)a).getTCPStation().equals( ((TCPStation) b).getTCPStation())) return true;
      return false;
    }
//  }*/
  StringBuffer sb;  
  private int getCommLinkIDorBackup() {
    int commID = commlinkID;        // Assume it is the "assigned" link
    CommLink c = CommLink.getItemWithID(commlinkID);
    /*Util.prt("getCommlinkIDorBackup:commlinkID="+commlinkID+" c="+c.toString()+
      " override="+commlinkOverrideID);*/
    if(commlinkID != commlinkOverrideID && commlinkOverrideID >0) {
      c=CommLink.getItemWithID(commlinkOverrideID);
      commID=commlinkOverrideID;
      Util.prt("getCommlinkIDorBackup: is in override to "+c.toString());
    }
    //Util.prt("getCommlinkIDorBackup: id="+commID+" c="+c.toString());
    
    // If the commlink is not enabled, then we need to set the commID to the backup
    if(!c.isEnabled()) {
      commID=c.getBackupID();
      if(commID == 0) {
        Util.prt("Commlink "+c+" is disabled with no backup.");
        sb.append("Commlink ").append(c).append(" is disabled with no backup.\n");
        commID=c.getID();
      } else {
        CommLink c2 = CommLink.getItemWithID( commID);
        Util.prt("Commlink "+c+" is disabled us the backupID="+c2);
        sb.append("CommLink ").append(c).append(" is disabled.  Backup=").append(c2).append("\n");
      }
    }   
    return commID;
  }

  public void programeRTSUseBackupIP(boolean flag) {
    RTSUtilityCommand rts = new RTSUtilityCommand(Util.getProperty("RTSServer"));
    rts.setUseBackupIP(ipadr, flag);
  }
  public StringBuffer programRTSCommandStatus(String overrideCPU) {
    sb = new StringBuffer(1000);
    RTSUtilityCommand rts = new RTSUtilityCommand(Util.getProperty("RTSServer"));
    int commID = getCommLinkIDorBackup();

    // Program the status IP/port - do twice, sometimes first gets lost
    int cpu = Cpu.getIDForCpu(Util.getProperty("RTSServer"));
    if(commID == 12 || commID == 13) cpu=22;    // If on HN7700 back hauls, use gacq1
    if(overrideCPU.equals("L"))cpu = 20;
    else if(overrideCPU.equals("A")) {cpu=22;}
    else if(overrideCPU.equals("C")) cpu=18;    // Force to contingency site
    for(int i=0; i<2; i++) {
      Util.prt("Set UP cpu="+Util.getProperty("RTSServer")+" commid="+commID+" to "+CpuLinkIP.getIPfromIDs(cpu,commID));
      sb.append("SetStatusIP - ").append(rts.setStatusIP(ipadr, CpuLinkIP.getIPfromIDs(cpu, 4), AnssPorts.RTS_STATUS_UDP_PORT, tcpstation));
      synchronized (this) { try { wait(1000);} catch(InterruptedException e) {}}
    }


    //Program the COmmand IP
    if(Util.getProperty("RTSServer").indexOf(".") > 0) {    // its in dotted format
      try {
        String ip = Util.stringFromIP(InetAddress.getByName(Util.getProperty("RTSServer")).getAddress(), 0).toString();
        String alt = Util.getProperty("RTSServerAlt");
        if(alt == null) alt="067.047.201.004";
        else Util.stringFromIP(InetAddress.getByName(Util.getProperty("RTSServerAlt")).getAddress(), 0);
        if(Util.getProperty("RTSServer").equals("136.177.36.242")) cpu = 18;
        if(overrideCPU.equals("L")) {ip="136.177.24.145"; alt="136.177.24.145";}
        else if(overrideCPU.equals("A")) {ip="136.177.24.147"; alt="136.177.24.147";}
        else if(overrideCPU.equals("C")) {ip="136.177.36.242"; alt="136.177.36.242";}
        Util.prt("Set CommandIP ipadr.="+ipadr+" to "+ Util.getProperty("RTSServer")+"->"+ip+" alt="+alt);
        sb.append("SetCommandIP - ").append(rts.setCommandIP(ipadr, ip, alt, AnssPorts.RTS_COMMAND_SOCKET_PORT));
      }
      catch(UnknownHostException e) {
        Util.prt("Got unknown host trying to process RTTServer dotted IP="+Util.getProperty("RTSServer"));
      }    
    }
    else {
      cpu=Cpu.getIDForCpu(Util.getProperty("RTSServer"));
      if(overrideCPU.equals("L")) cpu=20;
      else if(overrideCPU.equals("A")) cpu = 22;    //gacq1
      else if(overrideCPU.equals("C")) cpu = 18;    //vdldfc9
      Util.prt("Set CommandIP ipadr="+ipadr+" to "+ CpuLinkIP.getIPfromIDs(cpu, 4)+
              " Alt="+CpuLinkIP.getIPfromIDs(cpu, commID));
      sb.append("SetCommandIP - ").append(
              rts.setCommandIP(ipadr, CpuLinkIP.getIPfromIDs(cpu, 4), CpuLinkIP.getIPfromIDs(cpu, commID),
              AnssPorts.RTS_COMMAND_SOCKET_PORT));
    }

    
     synchronized (this) { try { wait(3000);} catch(InterruptedException e) {}}
    
    // Reprogramming the port has the problem it closes the connection so save might not work
    rts.saveConfig(ipadr);
    synchronized (this) { try { wait(1000);} catch(InterruptedException e) {}}
    
    //synchronized (this) { try { wait(3000);} catch(InterruptedException e) {}}
    Util.prt("program RTScommand="+sb.toString());
    rts.close();
    return sb;
  }
  public StringBuffer programRTSUdpTunnel(boolean enable, String overrideCPU) {
    sb = new StringBuffer(1000);
    RTSUtilityCommand rts = new RTSUtilityCommand(Util.getProperty("RTSServer"));
    int commID = getCommLinkIDorBackup();
    

    // Program the status IP/port - do twice, sometimes first gets lost
    int cpu = Cpu.getIDForCpu(UC.DEFAULT_TUNNEL_NODE);
    if(cpuID > 0) cpu=cpuID;
    for(int i=0; i<2; i++) {
      Util.prt("Set UP cpu="+UC.DEFAULT_TUNNEL_NODE+" commid="+commID);
      int port2=AnssPorts.RTS_TUNNEL_PORT;
      if(!enable) port2=0;
      Util.prt("programUdpTunnel ip="+ipadr+" port="+port2+" enabled="+enable);
      if(overrideCPU.equals("V")) sb.append("SetTunnelIP - ").append(rts.setTunnelIP(ipadr, CpuLinkIP.getIPfromIDs(cpu, commID), CpuLinkIP.getIPfromIDs(cpu, 4), port2));
      else if(overrideCPU.equals("C")) sb.append("SetTunnelIP - ").append(rts.setTunnelIP(ipadr, CpuLinkIP.getIPfromIDs(18, commID), CpuLinkIP.getIPfromIDs(18, 4), port2));

      else sb.append("SetTunnelIP - ").append(rts.setTunnelIP(ipadr, CpuLinkIP.getIPfromIDs(cpu, 4), CpuLinkIP.getIPfromIDs(cpu, commID), port2));
      synchronized (this) { try { wait(200);} catch(InterruptedException e) {}}
    }

    // Reprogramming the port has the problem it closes the connection so save might not work
    rts.saveConfig(ipadr);
    synchronized (this) { try { wait(1000);} catch(InterruptedException e) {}}
    
    //synchronized (this) { try { wait(3000);} catch(InterruptedException e) {}}
    Util.prt("program RTScommand="+sb.toString());
    rts.close();
    return sb;
  }

  public StringBuffer programRTSnoreboot(String cpuOverride) {
    RTSUtilityCommand rts= doProgramRTS(cpuOverride);
    Util.prt("program RTS="+sb.toString());
    rts.close();
    return sb;
    
  }
  public StringBuffer programRTS(String cpuOverride) {
    RTSUtilityCommand rts= doProgramRTS(cpuOverride);
    Util.prta("do reset");
    rts.reset(ipadr);
    Util.prt("program RTS="+sb.toString());
    rts.close();
    return sb;
  }
  private RTSUtilityCommand doProgramRTS(String cpuOverride) {
    
    sb = new StringBuffer(1000);
    RTSUtilityCommand rts = new RTSUtilityCommand(Util.getProperty("RTSServer"));
    doPort(rts, 0, rtsport1, rtsClient1, cpuOverride);
    doPort(rts, 2, rtsport3, rtsClient3, cpuOverride);
    doPort(rts, 3, rtsport4, rtsClient4, cpuOverride);
    synchronized (this) { try { wait(3000);} catch(InterruptedException e) {}}
    doPort(rts, 1, rtsport2, rtsClient2, cpuOverride);// do last so others are visible!
    sb.append("Save Config\n");
    rts.saveConfig(ipadr);
    synchronized (this) { try { wait(1000);} catch(InterruptedException e) {}}
    sb.append("Program RTS completed.\n");
    return rts;

  }
  /** Program a serial port to a port type, and set client mode 
   * @param cmd  The pointer to the RTSUtility cmd object
   * @param serial Port number (0 is first port) on RTS
   * @param port Port type 
   * @param client if zero, this is a server 
   * @param overrideCPU a CPU override of the one in the database
   */
  private void doPort(RTSUtilityCommand cmd, int serial, int port, int client,String overrideCPU) {
    Util.prta("doPort on serial port"+serial+" port type="+port+" "+rtsEnum.get(port).toString()+" client="+client);
    String usgsNode, altNode;
    int cpu;
    boolean vpnup=false;
    if(overrideCPU.equals("V")) vpnup=true;
    
    // Assume the assigned Commlink.  If it is not enabled, use its backup comm link
    cpu = cpuID;
    int commID = getCommLinkIDorBackup();
    if(commID == 0) return;

    // Note : "port type" here is one more than "port type" on the RTS itself (database to c enum mapping)
    sb.append("Program port").append(serial + 1).append(" type=").append(rtsEnum.get(port)).append("-");
    String ret="";
    switch(port) {
      case MSHEAR:
        altNode=CpuLinkIP.getIPfromIDs(cpuID, commID); // Using the backhaul
        usgsNode=CpuLinkIP.getIPfromIDs(cpuID, 4);      // public route
        Util.prta("Mshear  ip="+usgsNode+" alt="+altNode+" cpu="+cpuID+" commID="+commID);
        sb.append(usgsNode).append(" ").append(altNode).append("-");
        if(vpnup) ret=cmd.setUsgsType(ipadr, serial, port-1,  altNode, usgsNode, localPort, true);
        else ret=cmd.setUsgsType(ipadr, serial, port-1,   usgsNode, altNode, localPort, true);
        break; 
      case DATA:
        if(client == 1)  {
          altNode=CpuLinkIP.getIPfromIDs(cpuID, commID); // Using back haul
          usgsNode=CpuLinkIP.getIPfromIDs(cpuID, 4);       // Using the public internet
          if(overrideCPU.equals("L")) {altNode="136.177.24.145"; usgsNode="136.177.24.145";}
          Util.prta("Data  ip="+usgsNode+" alt="+altNode+" cpuID="+cpuID);
          sb.append(usgsNode).append(" ").append(altNode).append("-");
          if(vpnup) ret=cmd.setUsgsType(ipadr, serial, port-1, altNode, usgsNode, 2004, true);
          else ret=cmd.setUsgsType(ipadr, serial, port-1, usgsNode, altNode, 2004, true);
        } else 
        { ret = cmd.setUsgsType(ipadr, serial, port-1, "0.0.0.0","0.0.0.0", 0, false);
        }
        break;
      case RTS:
        if(client == 1) {
          cpu=Cpu.getIDForCpu(UC.DEFAULT_RTS_VMS_NODE+overrideCPU);
          altNode=CpuLinkIP.getIPfromIDs(cpu, commID);  // Using the backhaul
          usgsNode=CpuLinkIP.getIPfromIDs(cpuID, 4);      // Using the public internet
          Util.prta("RTS ip="+usgsNode+" alt="+altNode);
          if(vpnup) ret=cmd.setUsgsType(ipadr, serial, port-1,altNode, usgsNode, 2006, true);
          else ret=cmd.setUsgsType(ipadr, serial, port-1,usgsNode,altNode,  2006, true);
        } else{
          ret=cmd.setUsgsType(ipadr, serial, port-1, "0.0.0.0", "0.0.0.0", 0, false);
        }
        break;
      case CONSOLE:
        if(client == 1) {
          cpu=Cpu.getIDForCpu(UC.DEFAULT_CONSOLE_VMS_NODE+overrideCPU);
          if(cpuID == 3) cpu=cpuID; 
          if(cpuID == 18) cpu = cpuID;
          altNode=CpuLinkIP.getIPfromIDs(cpu, commID);  // Using the backahul
          usgsNode=CpuLinkIP.getIPfromIDs(cpu, 4);      // Using the public internet
          if(altNode.equals("000.000.000.000")) altNode=usgsNode;
          /*if(commID == 12 || commID == 13) {
            if(commID == 12) {      // 12 is GT_BH1 - Germantown Backhault
              Util.prt("HN7700 : override console to 10.177.28.2 (gacq1)");
              usgsNode ="10.177.28.2";
              altNode="136.177.24.147";
            }
            if(commID == 13) {    // 13 is NLV_BH2 - N. Las Vegas BackHaul
             Util.prt("HN7700 : override console to 10.177.0.2 (gacq1)");
              usgsNode ="10.177.0.2";
              altNode="136.177.24.147";

            }
          }*/
          if(overrideCPU.equals("K")) {usgsNode="136.177.30.35";altNode="136.177.30.35";}
          else if(overrideCPU.equals("L") ) {usgsNode="136.177.24.145"; altNode="136.177.24.145";}
          else if(overrideCPU.equals("A")) {usgsNode="136.177.24.147"; altNode="136.177.24.147";}
          else if(overrideCPU.equals("N")) {usgsNode="136.177.24.67"; altNode="136.177.24.67";}
          else if(overrideCPU.equals("C")) {usgsNode="136.177.36.242"; altNode="136.177.36.242";}

          Util.prta("Console  ip="+usgsNode+" alt="+altNode+" cpudef="+UC.DEFAULT_CONSOLE_VMS_NODE+overrideCPU);
          sb.append(usgsNode).append(" ").append(altNode).append("-");
          if(vpnup) ret=cmd.setUsgsType(ipadr, serial, port-1, altNode, usgsNode, 2007, true);
          else ret=cmd.setUsgsType(ipadr, serial, port-1, usgsNode, altNode, 2007, true);
        } else{
          ret=cmd.setUsgsType(ipadr, serial, port-1, "0.0.0.0", "0.0.0.0", 0, false);
        }
        break;      
      case GPS:
        if(client == 1) {
          cpu=Cpu.getIDForCpu(UC.DEFAULT_GPS_VMS_NODE+overrideCPU);
          altNode=CpuLinkIP.getIPfromIDs(cpu, commID);  // Using a back haul
          usgsNode=CpuLinkIP.getIPfromIDs(cpuID, 4);      // Using the public internet
          Util.prta("GPS ip="+usgsNode+" alt="+altNode);
          sb.append(usgsNode).append(" ").append(altNode).append("-");
          if(vpnup) ret=cmd.setUsgsType(ipadr, serial, port-1, altNode, usgsNode, 2009, true);
          else ret=cmd.setUsgsType(ipadr, serial, port-1, usgsNode, altNode, 2009, true);
        } else{
          ret=cmd.setUsgsType(ipadr, serial, port-1, "0.0.0.0", "0.0.0.0", 0, false);
        }
        break;         
      case NONE:
        ret=cmd.setUsgsType(ipadr,serial, port-1, "0.0.0.0.0", "0.0.0.0", 0, false);
        break;
      default:
        Util.prta("**** port program is illegal!="+port);
        break;
    } 
    //if(ret.indexOf("O.K.") < 0) sb.append("suggest ^R^D?-");
    sb.append(ret);
  }
  
  
   public void sendForm() {
     return;
    /* This is obsolete - no VMS systems any more!
     String [] hosts=UC.getFormHosts();
    FormUDP [] us = new FormUDP[hosts.length];
    for(int i=0; i<hosts.length; i++) {

      FormUDP u = new FormUDP(2099, hosts[i], tcpstation);
      Util.prta("send tcp config to "+hosts[i]+"/2099");

      u.append("ipadr", ipadr);
      u.append("port", port);
      u.append("timeout", timeout);
      Util.prta("SendForm : ip="+ipadr+" port="+port+" timeout="+timeout+" cpuid="+cpuID);
       if(state != 3) u.append("node", 0);
        else {
          int node=0;
          String nsn="None";
          if(cpuID != 0) nsn = CpuPanel.getCpuForID(cpuID).getCpu();
          if(nsn.substring(0,3).equals("nsn")) node = nsn.charAt(3) - '0';
          Util.prta("Server Node translated to "+node+" from "+nsn);
          u.append("node", node);
        }
        u.append("gomberg", (gomberg == 1 ? 1 : 0) );
        u.append("rollback", (rollback == 1 ? 1 : 0));
        u.append("ctrlq", (ctrlq == 1 ? 1 : 0));
        u.append("console", consoleIP);
        u.append("conport", consolePort);
        u.append("local_port", localPort);
        u.append("consoletimeo", consoleTimeout);
        u.append("power_type", powerType);
        u.append("power_ip", powerIP);
        u.append("power_port", powerPort);
        if(commlinkID != commlinkOverrideID && commlinkOverrideID > 0) 
             u.append("commlink",commlinkOverrideID);
        else u.append("commlink", commlinkID);
        u.send();
        u.reset();
    }*/
  } 
   /** true if sites is POC
    * @return true if site is a 1 IP site with NATting device
    */
  public boolean getAllowPOC() {return (allowpoc != 0);}
  /** # of Q330s
   * @return Number of Q330s at this site (and the size of the arrays for the other get*()
   */
  public int getNQ330s() {if(kmiTags == null) return 0; else return kmiTags.length;}
  /** TAGs with appended # for additional units 
   * @return station name with unit added so WMOK, WMOK1
   */
  public String [] getTags() {return tags;}
  /** Station names like WMOK, WMOK1
   * @return station name in the Q330 using the 4 character rule with unit added so WMOK, WMO1, KSU1, KSU2
   */
  public String [] getQ330Stations() {return q330stations;}
  private void setupDB() {
    //try {
     if(dbconn == null) 
      try {
        dbconn = DBConnectionThread.getThread("TcpStationRO");
        if(dbconn == null) dbconn = new DBConnectionThread(Util.getProperty("DBServer"),"readonly","anss",
            false,false,"TcpStationRO", Util.getOutput());
        if(!DBConnectionThread.waitForConnection("TcpStationRO"))
          if(!DBConnectionThread.waitForConnection("TcpStationRO"))
            if(!DBConnectionThread.waitForConnection("TcpStationRO"))
              if(!DBConnectionThread.waitForConnection("TcpStationRO"))
                if(!DBConnectionThread.waitForConnection("TcpStationRO"))
                  Util.prt("****** TcpStation: cannot open readonly connection to anss database");
      }
      catch(InstantiationException e) {
        Util.prta("WBSC: InstantiationException on db connection is impossible!");
        dbconn = DBConnectionThread.getThread("TcpStationRO"); 
      }
    /*}
    catch(SQLException e) {
      Util.SQLErrorPrint(e,"Cannot setup database connection for TCPstation access");
    }*/
  }
  /** get the seed network code for this station from Nsnstation table
   * @return the descriptive String with SeedNetwork
   */
  public String getSeedNetwork() 
  { if(seedNetwork != null) return seedNetwork;
    if(dbconn == null) setupDB();
    synchronized(rtsEnum) {   // A proxy for dbconn which is not final
       try{
        ResultSet rs = dbconn.executeQuery("SELECT * FROM nsnstation where nsnstation='"+tcpstation+"'");
        if(rs.next()) {
          String s = rs.getString("seednetwork");
          if(s == null || s.equals("")) {
            s = "US";
          }
          seedNetwork=s;
          rs.close();
          return seedNetwork;
        }
      } catch(SQLException e) {
        Util.SQLErrorPrint(e,"Error reading hex serial");
      }
    }
    return "  ";    
  }
  /** get hexserials
   * @return the hex serials as a string from each q330
   */
  public String [] getHexSerials() {
    if(dbconn == null) setupDB();
    if(System.currentTimeMillis() - lastGetHexSerials > 120000) {
      lastGetHexSerials=System.currentTimeMillis();
      try {
        for(int i=0; i<hexSerials.length; i++) {
          synchronized(rtsEnum) {   // A proxy for dbconn which is not final
          
            ResultSet rs = dbconn.executeQuery("SELECT ID,hexserial,authcode FROM anss.q330 where q330='"+kmiTags[i]+"'");
            if(rs.next()) {
              hexSerials[i] = rs.getString("hexserial");
              hostPorts[i] = rs.getInt("ID")*4 + 11000;
              authCodes[i] = Util.toHex(rs.getLong("authcode")).toString();
            }
            else {hexSerials[i]=kmiTags[i]+" is not in q330 file"; authCodes[i]="0x0";}
            rs.close();
          }
        }
      } catch(SQLException e) {
        Util.SQLErrorPrint(e,"Error reading hex serial");
      }
    }
    return hexSerials;
  }
  /** fro a given Q330 unit at the site and the data port (dataport 0=admin port)
   * return the current authorization code for the unit.  If the admin port is authcode
   * zero, then all data ports will also return zero.
   *
   * @param unit Q330 unit number, normally 0 or 1 if two Q330s are at the site
   * @param port The port number 0=admin port, 1=dataport 1, etc.
   * @return A string with the hex auth code
   */
  public String getAuthCode(int unit, int port) {
    getHexSerials();
    if(authCodes[unit].equals("0x0")) return "0x0";

    // ASL sometimes does not follow our conventions (see SLBS) to support this
    // if the admin port authcode does not match the expected one, always return that code
    if(!authCodes[unit].equals(Util.toHex(Q330.calcAuthcode(hexSerials[unit])).toString())) {
      if(!getSeedNetwork().equals("IU"))
        Util.prt(tcpstation+" Warning Q330 with non-standard admin port code auth="+authCodes[unit]+
              " calc="+Util.toHex(Q330.calcAuthcode(hexSerials[unit])));
      return authCodes[unit];
    }
    if(port == 0) return authCodes[unit];     // port=0 Always return the admin auth code
    return Util.toHex(Q330.calcAuthcodeDataPort(hexSerials[unit], port)).toString(); // return calculated data port code
  }
  /** get hexserials
   * @return the host ports (ID*4)+11000 to be used for a Q330
   */
  public int [] getHostPorts() {
    if(hexSerials[0] == null) getHexSerials();
    return hostPorts;
  }
  /** InetAddresses 
   * @return array with InetAddress form of the IP address */
  public InetAddress [] getQ330InetAddresses() {return q330adr;}
  /** get the address behind the NAT if any
   *@return array with InetAddress form of the IP in the NATed space */
  public InetAddress [] getQ330NatAddresses() {return q330NatAdr;}
  /** Normally dataport 1 is used, but there might be another via the config
   * @return array of ints with the dataports for each Q330 (normally 1) */
  public int [] getQ330DataPorts() {return dataPort;}
  /** 5330 or 7330
   * @return array of ints with the Q330 ports (normally 5330 or 6330) */
  public int [] getQ330Ports() {return q330ports;}
  /** Tunnel unix ports
   * @return aray of ints with the Unix Tunnel ports to use */
  public int [] getTunnelPorts() {return tunnelports;}
  /** KMI tags
   * @return array of Strings with 4 character KMI property tag numbers (serial numbers)*/
  public String [] getKMITags() {return kmiTags;}
  /** is this a tunneled connection
   *@return true if the tunnel is on */
  public boolean isTunneled() {return isTunneled;}
  /** return console port number, -1 if none is configured
   * @return  console port
   */
  public int getConsolePort() {
    if(rtsport1 == CONSOLE) return 1;
    if(rtsport2 == CONSOLE) return 2;
    if(rtsport3 == CONSOLE) return 3;
    if(rtsport4 == CONSOLE) return 4;
    return -1;
  }
  /** A string with one line per Q330
   * @return a String with one line per Q330 with all the parameters printed out */
  public String getQ330toString() {
    StringBuilder sb2 = new StringBuilder(100);
    InetAddress [] q330adr2 = getQ330InetAddresses();
    
    int [] qports = getQ330Ports();
    int [] tports = getTunnelPorts();
    String [] tags2 = getTags();
    String [] q330names = getQ330Stations();
    String [] kmi = getKMITags();
    String [] hex = getHexSerials();
    for(int i=0; i<getNQ330s(); i++) sb2.append(i).append(" ").append(getSeedNetwork()).append(" ").
            append(tags2[i]).append(" ").append(q330names[i]).append(" ").append(kmi[i]).
            append(" ").append(hex[i]).append(" " + " hostport=").append(hostPorts[i]).append("->").
            append(tports[i]).append(":").append(q330adr2[i].getHostAddress()).append("/").
            append(qports[i]).append(isTunneled() ? " Tunnel" : " Notunnel").append("\n");
    return sb2.toString();
  }
  public TCPStation getTCPStationWithID(int ID) {
    makeTCPStations();
    for(TCPStation tcp: tcpstations) if(tcp.getID() == ID) return tcp;
    return null;
  }
  public static TCPStation getTCPStation(String s) {
    if(TCPStation.tcpstations == null) makeTCPStations();
    for(TCPStation tcp : tcpstations) if(tcp.getTCPStation().equalsIgnoreCase(s)) return tcp;
    return null;
  }  // This routine should only need tweeking if key field is not same as table name
  public static void makeTCPStations() {
    if (tcpstations != null) return;
    tcpstations = new ArrayList<TCPStation>(100);
    try {
      Statement stmt = DBConnectionThread.getConnection(DBConnectionThread.getDBSchema()).createStatement();   // used for query
      String s = "SELECT * FROM anss.tcpstation ORDER BY tcpstation";
      ResultSet rs = stmt.executeQuery(s);
      while (rs.next()) {
        TCPStation loc = new TCPStation(rs);
//        Util.prt("MakeRTSStation() i="+tcpstations.size()+" is "+loc.getRTSStation());
        tcpstations.add(loc);
      }
      rs.close();
      stmt.close();
    }
    catch (SQLException e) {
      Util.SQLErrorPrint(e,"makeRTSStations() on table SQL failed");
    }    
  }
  /** validate an IP address in dotted number form nnn.nnn.nnn.nnn.  This will
   * insure there are 4 bytes in the address and that the digits are all numbers.
   * between the dots the numbers can be one, two or three digits long.
   * @param t The textfield with a supposed IP adr
   * @param err errorTrack variable
   * @return String with the reformated to nnn.nnn.nnn.nnn form
   */
  public static String chkIP(javax.swing.JTextField t, ErrorTrack err) {
    // The string is in dotted form, we return always in 3 per section form  
   StringBuilder out = new StringBuilder(15);
   StringTokenizer tk= new StringTokenizer(t.getText(),".");
   if(t.getText().equals("")) {
     err.set(true);
     err.appendText("IP format bad - its null");
     return "";
   }
   if(t.getText().charAt(0) < '0' || t.getText().charAt(0) > '9') {
     Util.prt("FUTIL: chkip Try to find station "+t.getText());
     TCPStation a = TCPStation.getTCPStation(t.getText());
     String station=t.getText();
     if(a != null) {
       Util.prt("FUtil: chkip found station="+a);
       t.setText(a.getIP());
       err.appendText(a.getTCPStation());
       tk = new StringTokenizer(t.getText(),".");
     }
     else {
       err.set(true);
       err.appendText("IP format bad digit");
       return "";
     }
   }
   if(tk.countTokens() != 4) {
       err.set(true);
       err.appendText("IP format bad - wrong # of '.'");
       return "";
   }
   for(int i=0; i<4 ; i++) {
       String s = tk.nextToken();
       if(s.length() == 3) out.append(s);
       else if (s.length() == 2) out.append("0").append(s);
       else if (s.length() == 1) out.append("00").append(s);
       else {
           err.set(true);
           err.appendText("IP byte wrong length="+s.length());
           return "";
       }
       if(i < 3) out.append(".");
   }
   t.setText(out.toString());
   return out.toString();
  }       
}
