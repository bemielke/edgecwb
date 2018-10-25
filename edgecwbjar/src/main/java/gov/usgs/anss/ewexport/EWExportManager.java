/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */

package gov.usgs.anss.ewexport;
import gov.usgs.alarm.SendEvent;
import gov.usgs.anss.db.DBConnectionThread;
import gov.usgs.anss.edge.EdgeProperties;
import gov.usgs.anss.edge.IndexFile;
import gov.usgs.anss.edge.RawDisk;
import gov.usgs.anss.edgethread.EdgeThread;
import gov.usgs.anss.util.Util;
import gov.usgs.edge.config.Export;
import gov.usgs.edge.config.Role;
import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.TreeMap;
/** This thread keeps open a DBConnectionThread in order to write out all of the configuration files
 * if GUI configuration of the EXPORTs is done.  This creates the files that normally would be
 * hand crafted for the main configuration (one line per export) and the lists of channels to export
 * for each EXPORT.
 *
 * It basically creates the main control file for EXPORTs with one line per export as used by the
 * EWExport thread.  It then creates all of the list of channels for each export as needed by
 * each individual EWExportSocket.  This task gets a filename for the main file in this routine and
 * maintains the channel files for each export in the same directory as the main control file but
 * with a filename "$EXPORT.setup" where $EXPORT is replaced with the EXPORT name or tag.
 *
 *<br>
 *<PRE>
 * NOTE: this command line is actually parsed in the EWExport and is not user configurable except through there
 *switch    arg      Description
 *-config  file    The config file to use (default is EW/export.setup)
 *
 *-db   DBURL    The database url ip.adr/port:db:vendor:schema to read the edge database tables export and exportchan for configuration information
 *-dbg             Turn on debugging in this EWExportManager
 *</PRE>
 * @author davidketchum
 *
 * @author U.S. Geological Survey  <ketchum at usgs.gov>
 */
public final class EWExportManager extends EdgeThread {
  private String dirname;
  private String setupFilename;
  //private String host;
  private DBConnectionThread dbconnedge;
  //private StringBuilder sb;
  private byte [] buf = new byte[10240];
  private boolean dbg;
  private TreeMap<String, StringBuilder> sbs = new TreeMap<>();
  @Override
  public void terminate() {terminate=true; interrupt();}
  int loop;
  public int getLoop() {return loop;}
  @Override
  public StringBuilder getMonitorString() {return monitorsb;}
  @Override
  public StringBuilder getStatusString() {
    if(statussb.length() > 0) statussb.delete(0, statussb.length());
    return statussb.append(" has ").append(sbs.size()).append(" files being maintained loop=").append(loop).append("\n");
  }
  @Override
  public StringBuilder getConsoleOutput() {return consolesb;}

  public EWExportManager(String argline, String tg) { 
    super(argline,tg);
    setupFilename="EW"+Util.FS+"export.setup";
    if(argline.contains(">")) argline = argline.substring(0,argline.indexOf(">")-1).trim();
    String [] args = argline.replaceAll("  ", " ").replaceAll("  ", " ").split("\\s");
    for(int i=0; i<args.length; i++) {
      switch (args[i]) {
        case "-db":
          Util.setProperty("DBServer", args[i+1]);
          prta("User config DB at "+args[i+1]);
          i++;
          break;
        case "-config":
          setupFilename = args[i + 1];
          i++;
          break;
        case "-dbg":
          dbg = true;
          break;
        default:
          prta("EWExMan:Unknown config parameter="+args[i]);
          break;
      }
    }

    if(setupFilename.indexOf("/") > 0) {
      dirname = setupFilename.substring(0,setupFilename.lastIndexOf("/"));
    }
    else dirname=".";

    // open up the DBServer
    dbconnedge = DBConnectionThread.getThread("edge");
    if(dbconnedge == null) {
      try {
        dbconnedge = new DBConnectionThread(Util.getProperty("DBServer"),"update", "edge",true,false,"edge", getPrintStream());
        if(!DBConnectionThread.waitForConnection("edge"))
          if(!DBConnectionThread.waitForConnection("edge"))
            if(!DBConnectionThread.waitForConnection("edge"))
              if(!DBConnectionThread.waitForConnection("edge")) prta("********* edge did not connect!") ;
        addLog(dbconnedge);
      }
      catch(InstantiationException e) {
        prta("EWExMan:InstantiationException: should be impossible");
        dbconnedge = DBConnectionThread.getThread("edge");
      }
    }
    dbconnedge.setLogPrintStream(getPrintStream());

    prta("EWExMan: start dirname="+dirname+" setupfile="+setupFilename);
    gov.usgs.anss.util.Util.prta("new ThreadManager "+getName()+" "+getClass().getSimpleName()+" f="+setupFilename);
    start();
  }
  @Override
  public void run () {
    loop=0;
    StringBuilder sb = new StringBuilder(100000);
    StringBuilder list = new StringBuilder(1000);
    StringBuilder selection = new StringBuilder(100);
    StringBuilder sniff = new StringBuilder(100);
    String [] roles = Util.getRoles(null);
    for(int i=0; i<roles.length; i++) prt("Util.role ["+i+"] is "+roles[i]);
            
    ArrayList<Role> v = Role.getRoleVector();
    selection.append("roleid IN (");
    for(int i=0; i<roles.length; i++) {
      if(roles[i].equals("igskcicgwsgm044")) roles[i]= "gldketchum3";
      prta("Roles["+i+"] is "+roles[i]);
      for (Role v1 : v) {
        if (((Role) v1).getRole().equals(roles[i])) {
          selection.append(((Role) v1).getID()).append(",");
          prta("   Match found for " + ((Role) v1).toString());
        }
      }
    }
    prta("EWExMan: Selection string for role ids is "+selection);
    //selection = selection.substring(0, selection.length()-1)+") ";
    selection.delete(selection.length()-1, selection.length());
    selection.append(")");
    ArrayList<String> exports = new ArrayList<>(10);
    ArrayList<Timestamp> exportsUpdated = new ArrayList<>(10);
    ArrayList<String> exportsRE = new ArrayList<>(10);
    boolean first=true;
    int sendtoID=0;
    while(!terminate) {
      while(dbconnedge == null ) {
        prta("EWExMan: mysql is down wait...");
        try{sleep(30000);} catch(InterruptedException e) {}
      }
      try {
        if(sb.length() > 0) sb.delete(0, sb.length()-1);
        if(list.length() > 0) list.delete(0, list.length()-1);
        exports.clear();
        exportsRE.clear();
        exportsUpdated.clear();
        
        // Get Sendto ID if it is not yet known
        if(sendtoID <= 0) {
          ResultSet rs = dbconnedge.executeQuery("SELECT id,sendto FROM sendto WHERE sendto='^Export'");
          if(rs.next()) {
            sendtoID=rs.getInt("id");
            rs.close();
            prta("EWExMan: sendto IF for ^Export="+sendtoID);
          }
          else {
            rs.close();
            dbconnedge.executeUpdate("INSERT INTO sendto (sendto,description,filesize,filepath,updatems,nodes,autoset,class,args,updated,created_by,created) "+
                    "VALUES ('^Export','Exported data',500,'/data2',500,'.*','','',now(),99,now())");
            sendtoID = dbconnedge.getLastInsertID("sendto");
            prta("EWExMan: **** create sendto for ^Export at ID="+sendtoID);
          }    
        }
        ResultSet rs = dbconnedge.executeQuery("SELECT * FROM edge.export WHERE "+selection+" ORDER BY export");
        sniff.delete(0,sniff.length());
        while(rs.next()) {
          if(rs.getInt("nooutput") == 0) sb.append("#");
          // Tage:IPADR/port: 
          sb.append(rs.getString("export").trim()).append(":").append(Util.cleanIP(rs.getString("exportipadr")).trim()).
                  append("-").append(rs.getInt("exportport")).append("-").append(rs.getString("ringfile")).append(":");
          if( (rs.getInt("allowrestricted") & Export.MASK_SNIFFLOG) != 0) // If sniff log is turned on   
           sniff.append(rs.getString("export").trim()).append(":").append(Util.cleanIP(rs.getString("exportipadr")).trim()).
                  append("-").append(rs.getInt("exportport")).append("-").append(rs.getString("ringfile")).append(":");
          
          if(rs.getString("type").equals("Generic_ACTV")) sb.append("-client ").append(rs.getInt("exportport")).append(" ");
          else {
            sb.append("-server ");
            if(rs.getInt("exportport") > 0) sb.append("-p ").append(rs.getInt("exportport")).append(" ");
          }
          sb.append("-ring ").append(rs.getString("ringfile")).append(" ");
          if(rs.getString("type").equalsIgnoreCase("Ack")) sb.append("-ack ");
          if( (rs.getInt("allowrestricted") & Export.MASK_ALLOWRESTRICTED) != 0) sb.append("-allowrestricted ");
          if(rs.getInt("institution") > 0) sb.append("-inst ").append(rs.getInt("institution")).append(" ");
          if(rs.getInt("module") > 0) sb.append("-mod ").append(rs.getInt("module")).append(" ");
          if(rs.getInt("scn") != 0) sb.append("-scn ");
          if(rs.getInt("nooutput") == 0) sb.append("-nooutput ");
          else sb.append("-bw ").append(Math.abs(rs.getInt("nooutput"))).append(" ");
          if(rs.getInt("hbint") > 0) sb.append("-hbint ").append(rs.getInt("hbint")).append(" ");
          if(rs.getInt("hbto") > 0) sb.append("-hbto ").append(rs.getInt("hbto")).append(" ");
          if(!rs.getString("hbin").equals("")) sb.append("-hbin ").append(rs.getString("hbin").replaceAll(" ", "|")).append(" ");
          if(!rs.getString("hbout").equals("")) sb.append("-hbout ").append(rs.getString("hbout").replaceAll(" ", "|")).append(" ");
          if(rs.getInt("queuesize") > 0) sb.append("-q ").append(rs.getInt("queuesize")).append(" ");
          if(rs.getInt("maxlatency") > 0) sb.append("-lat ").append(rs.getInt("maxlatency")).append(" ");
          if(dbg) sb.append("-dbg ");
          sb.append("-state EW/").append(rs.getString("export")).append(".state ");
          sb.append("-c EW/").append(rs.getString("export")).append(".chan");
          sb.append("\n");
          exports.add(rs.getString("export"));
          exportsRE.add(rs.getString("chanlist"));
          exportsUpdated.add(rs.getTimestamp("updated"));
          
          // Check and see of the regular expression of channels is a $tag, in which case it  needs to be dereferenced.
          int loops=0;
          while(exportsRE.get(exportsRE.size()-1).startsWith("$")) {
            try {
              try (Statement stmt = dbconnedge.getNewStatement(false)) {
                String s = "SELECT * FROM edge.export WHERE export='"+exportsRE.get(exportsRE.size()-1).substring(1).replaceAll("\n","")+"'";
                try (ResultSet rs2 = stmt.executeQuery(s)) {
                  if(rs2.next()) {
                    if(dbg) prta("EWExMan: do substitution for "+exportsRE.get(exportsRE.size()-1)+" in "+exports.get(exports.size()-1));
                    exportsRE.set(exportsRE.size()-1, rs2.getString("chanlist"));
                    if(exportsUpdated.get(exportsUpdated.size()-1).getTime() < rs2.getTimestamp("updated").getTime())
                      exportsUpdated.get(exportsUpdated.size()-1).setTime(rs2.getTimestamp("updated").getTime());
                  }
                  else prta("EWExMan : Substitution failed for exportRE="+exportsRE.get(exportsRE.size()-1)+" s="+s);
                }
              }
              loops++;
              if(loops >10) {
                SendEvent.edgeSMEEvent("EWExpBadSub","Could not substitute for "+exportsRE.get(exportsRE.size()-1), this);
                break;
              }
            }
            catch(SQLException e) {
              e.printStackTrace(getPrintStream());
            }
          }
        }
        sb.append("#dbgDataExport=").append(sniff.length() == 0?"NONE":sniff.toString()).append("\n");
        rs.close();
                
        if(sb.length() < 10) {
          prta("EWExMan:  ********* This computer does not have any assigned Exports");
        }
        
        // Write out ths setup file if it is different than the one stored
        boolean wroteIt=false;
        try {
          StringBuilder sbold = sbs.get(setupFilename);
          if(sbold == null) {
            sbold = new StringBuilder(10000);
            sbs.put(setupFilename, sbold);
          }
          if(!sbold.toString().equals(sb.toString())) {
            Util.stringBuilderToBuf(sb, buf);
            writeFile(sb, sbold, setupFilename);
            wroteIt=true;
          }
          else if (dbg) prta("EWExMan: "+setupFilename+" is the same");
        }
        catch(IOException e) {
          prt("EWExMan:Got IOError writing out channel files e="+e);
          e.printStackTrace(getPrintStream());
        }

        // For each export, make a new StringBuffer with channel list, if its changed or its time
        long now=System.currentTimeMillis();
        if(dbg) prta("EWExMan: top chan first="+first+" #exports="+exports.size());
        for(int i=0; i<exports.size(); i++) { 
          if(dbg)prta(i+" export="+exports.get(i)+" time="+(now - exportsUpdated.get(i).getTime())+" RE="+exportsRE.get(i)+" loop="+loop);
          if(now - exportsUpdated.get(i).getTime() >120000 && !wroteIt && !first && loop % 30 != 1) continue;
          
          String re = exportsRE.get(i).replaceAll("\n\n","\n").replaceAll("\n\n", "\n").replaceAll("--\n","\\$\n").
                  replaceAll("\n","|");
           // --\n means no location code
          if(sb.length() > 0) sb.delete(0,sb.length());
          while (re.charAt(re.length()-1) == '|') re = re.substring(0,re.length()-1);
          prta("EWExMan: Do chans for exports="+exports.get(i)+" re="+re);
          rs = dbconnedge.executeQuery("SELECT channel,sendto FROM edge.channel WHERE channel regexp '"+re+"' ORDER BY channel");
          while(rs.next()) {
            if(rs.getString(1).trim().startsWith("$")) {
            }
             sb.append(rs.getString(1).trim()).append("\n");
          }
          rs.close();

          try {
            String f = dirname+"/"+exports.get(i)+".chan";
            StringBuilder sbold = sbs.get(f);
            if(sbold == null) {
              sbold=new StringBuilder(1000);
              sbs.put(f,sbold);
            }
            if(!sbold.toString().equals(sb.toString()) || first) {
              writeFile(sb, sbold,f);
              dbconnedge.executeUpdate("UPDATE edge.channel SET sendto = (sendto | (1<<("+sendtoID+"-1))) WHERE channel regexp '"+re+"'");
            }
            else if(dbg) prta("EXExMan:"+f+" is the same");
          }
          catch(IOException e) {
              prt("EWExMan:Got IOError writing out channel files e="+e);
              e.printStackTrace(getPrintStream());            
          }
        }
        first=false;
        loop++;
        try{sleep(60000);} catch(InterruptedException e) {}
          if(System.currentTimeMillis() % 86400000L < 90000) dbconnedge.setLogPrintStream(getPrintStream());
      } 
      catch (SQLException e) {
        prta("EWExMan: SQLError="+e);
        if(e != null) if(e.toString().contains("is not connected")) {DBConnectionThread.getThread("edge").terminate(); dbconnedge=null;}
        else Util.SQLErrorPrint(e,"EWExMan:: SQL error setting up channels for export db="+dbconnedge.toString(),getPrintStream());
        try{sleep(60000);} catch(InterruptedException e2) {}
        try {
          if(dbconnedge != null) dbconnedge.close();    // close to remove from the list off connections
          dbconnedge = new DBConnectionThread(Util.getProperty("DBServer"),"update", "edge",true,false,"edge", getPrintStream());
          addLog(dbconnedge);
          if(!DBConnectionThread.waitForConnection("edge"))
            if(!DBConnectionThread.waitForConnection("edge"))
              if(!DBConnectionThread.waitForConnection("edge"))
                if(!DBConnectionThread.waitForConnection("edge"))
                 if(!DBConnectionThread.waitForConnection("edge"))
                   prta("Could not reopen the database server - this is very bad");
          SendEvent.edgeSMEEvent("MySQLNotOpn", "ExportManager database is not connecting", this);
        }
        catch(InstantiationException e2) {
          prta("InstantiationException: should be impossible");
          dbconnedge = DBConnectionThread.getThread("edge");
        }
      }
      catch(RuntimeException e) {
        prt("EWExportManager: Got runtime error e="+e);
        SendEvent.debugEvent("EWExportManager RTEr","Runtime error in EWExportManager"+Util.getNode()+" "+IndexFile.getNode(),this);
        e.printStackTrace(getPrintStream());
        try{sleep(60000);} catch(InterruptedException e2) {}
      }

    }
    prta("EWExMan:: writer exiting.... terminate="+terminate);
  }
  private void writeFile(StringBuilder sb, StringBuilder sbold, String filename) throws IOException {
    prta("EWExMan:"+filename+" is different len new="+sb.length()+" old="+sbold.length());
    if(sb.length() > buf.length) buf = new byte[sb.length()*2];
    Util.stringBuilderToBuf(sb, buf);
    try (RawDisk rw = new RawDisk(filename,"rw")) {
      rw.writeBlock(0, buf, 0, sb.length());
      rw.setLength(sb.length());
    }
    if(sbold.length() > 0) sbold.delete(0,sbold.length());
    sbold.append(sb);
  }

  public static void main(String[] args) {

    IndexFile.init();
    EdgeProperties.init();
    EWExportManager qman =  new EWExportManager("-mysql gacqdb -config EW/export.setup  >>exportman","TAG");

  }
}