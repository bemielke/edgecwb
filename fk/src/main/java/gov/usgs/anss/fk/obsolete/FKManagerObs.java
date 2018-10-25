/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */

package gov.usgs.anss.fk.obsolete;
import gov.usgs.alarm.SendEvent; 
import gov.usgs.anss.db.DBConnectionThread;
import gov.usgs.anss.edge.IndexFile;
import gov.usgs.anss.edgemom.EdgeChannelServer;
import gov.usgs.anss.edgethread.EdgeThread;
import gov.usgs.edge.config.FKSetup;
import gov.usgs.edge.config.Channel;
//import gov.usgs.anss.edgethread.ManagerInterface;
import gov.usgs.anss.util.Util;
import gov.usgs.anss.edgethread.ManagerInterface;
import gov.usgs.edge.config.Role;
import gov.usgs.anss.util.StaSrv;
import gov.usgs.adslserverdb.MDSChannel;
import gov.usgs.anss.fk.FK;
import java.io.*;
import static java.lang.Thread.sleep;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.TreeMap;
/** This class configures and runs FK objects to created FK beams as configured in the
 * database.  There is one line per array in the main configuration file and each of theses
 * lines contains the -s stationfile switch which points to a station file.  These station
 * files are also created by this program.  The database only allows specifying the channels, 
 * so this program looks up the coordinates and corrections if any, and the use flags based
 * on the input.  Stations beginning with dash are disabled in the calculation(use=false).
 * <p>
 * At this time no corrections are possible.
 *   
 * <PRE>
 * Switch  Arg      Description
 * -config filename  The filename of the output config file (def=config/fk.setup)
 * -nodb             This is to be run in no database mode - user must hand create all configuration files
 * -dbg              More output
 * -mdsip  ip.adr    The address of a metadata server to use to get coordinates (def=cwbpub.cr.usgs.gov)
 * -mdsport port     The port number of the MDS server to user (def=2052)
 * -forcenode node   To force the FK to be generated somewhere other than its home node like for a contingency site
 * </PRE>
 * @author davidketchum
 */
public class FKManagerObs extends EdgeThread  {
  private TreeMap<String, FK> thr = new TreeMap<>();
  private TreeMap<String, StringBuilder> chansb = new TreeMap<>();
  private final ArrayList<Role> roles = new ArrayList<>(2);
  private final ShutdownFKManager shutdown;
  private static DBConnectionThread dbconn;
  private static DBConnectionThread dbconnedge;
  private String configFile;
  private boolean dbg;
  private boolean noDB;
  private String stasrvIP;
  private int stasrvPort;
  private final StaSrv stasrv;
  private StringBuilder fksb = new StringBuilder(100);
  private StringBuilder chscr = new StringBuilder(100);
  private final StringBuilder tmpsb = new StringBuilder(50);
  private String forceNode;
  @Override
  public String toString() {return toStringBuilder(null).toString();}
  public StringBuilder toStringBuilder(StringBuilder tmp) {
    StringBuilder sb = tmp;
    if(sb == null) {
      sb=Util.clear(tmpsb);
    }
    synchronized(sb) {
      sb.append(tag).append(configFile).append(" #thr=").append(thr.size());
    }
    return sb;
  }  
  /** set debug state
   *@param t The new debug state */
  public void setDebug(boolean t) {dbg=t;}
  /** terminate thread (causes an interrupt to be sure) you may not need the interrupt! */
  @Override
  public void terminate() {terminate = true; prta("FKM: interrupted called!");interrupt();}
  /** return the status string for this thread 
   *@return A string representing status for this thread */
  @Override
  public StringBuilder getStatusString() {
    Util.clear(statussb);

    return statussb;
  }
  /** return the monitor string for Nagios
   *@return A String representing the monitor key value pairs for this EdgeThread */
  @Override
  public StringBuilder getMonitorString() {
    Util.clear(monitorsb).append("ConfigFile=").append(configFile).append("\nNthr=").append(thr.size()).append("\n");

    return monitorsb;
  }

  /** return console output - this is fully integrated so it never returns anything
   *@return "" since this cannot get output outside of the prt() system */
  @Override
  public StringBuilder getConsoleOutput() {return consolesb;}
   /** creates an new instance of LISSCLient - which will try to stay connected to the
   * host/port source of data.  This one gets its arguments from a command line
    * @param argline The command line
    * @param tg The logging tag
   */
  public FKManagerObs(String argline, String tg) {
    super(argline,tg);
//initThread(argline,tg);
    String [] args = argline.split("\\s");
    dbg=false;
    configFile = "config/fk.setup";
    noDB=false;
    stasrvIP = "cwbpub.cr.usgs.gov";
    stasrvPort=2052;
    for(int i=0; i<args.length; i++) {
      //prt(i+" arg="+args[i]);
      if(args[i].equals("-dbg")) dbg=true;
      else if(args[i].equals("-config")) {configFile = args[i+1];i++;}
      else if(args[i].equalsIgnoreCase("-nodb")) noDB=true;
      else if(args[i].equalsIgnoreCase("-mdsip")) {stasrvIP = args[i+1]; i++;}
      else if(args[i].equalsIgnoreCase("-mdsport")) {stasrvPort = Integer.parseInt(args[i+1]); i++;}
      else if(args[i].equalsIgnoreCase("-forcenode")) {forceNode = args[i+1]; i++;}
      // else if(args[i].equals("-h")) { host=args[i+1]; i++;}
      else if(args[i].substring(0,1).equals(">")) break;
      else prt("FKManager: unknown switch="+args[i]+" ln="+argline);
    }

    if(Util.getProperty("DBServer") == null) noDB=true;
    else if(Util.getProperty("DBServer").equalsIgnoreCase("NoDB")) noDB=true;
    else if(Util.getProperty("DBServer").equals("")) noDB=true;
    prt("FKM: created args="+argline+" tag="+tag);
    prt("FKM: config="+configFile+" dbg="+dbg+" no DB="+noDB+" DBServer="+Util.getProperty("DBServer"));
    stasrv = new StaSrv(stasrvIP, stasrvPort);
    shutdown = new ShutdownFKManager();
    Runtime.getRuntime().addShutdownHook(shutdown);
    if(dbconn == null && !noDB) {
      try {
        dbconn = new DBConnectionThread(Util.getProperty("DBServer"),"readonly","edge",
                false,false,"edgeFKM", getPrintStream());
        addLog(dbconn);
        if(!DBConnectionThread.waitForConnection("edgeFKM") )
          if(!DBConnectionThread.waitForConnection("edgeFKM") ) 
            prt("Failed to get FK database access");
        if(DBConnectionThread.getThread("anss") == null) {
          DBConnectionThread tmp = new DBConnectionThread(Util.getProperty("DBServer"),
                 "readonly", "anss", false, false, "anss", getPrintStream());
          if(!DBConnectionThread.waitForConnection("anss") )
            if(!DBConnectionThread.waitForConnection("anss") ) 
              prt("Failed to get anss FK access");
          addLog(tmp);
        }
        if(DBConnectionThread.getThread("edge") == null) {
          dbconnedge = new DBConnectionThread(Util.getProperty("DBServer"),"readonly",
                  "edge", false, false, "edge", getPrintStream());
          addLog(dbconnedge);
          if(!DBConnectionThread.waitForConnection("edge") )
            if(!DBConnectionThread.waitForConnection("edge") ) prt("Failed to get edge FK access");
        }
      }
      catch(InstantiationException e) {
        prta("InstantiationException: should be impossible");
        dbconn = DBConnectionThread.getThread("edgeFKM");
      }
    }
    start();
  }
  /** This Thread keeps a socket open to the host and port, reads in any information
   * on that socket, keeps a list of unique StatusInfo, and updates that list when new
   * data for the unique key comes in.
   */
  @Override
  public void run()
  { running=true;
   Iterator<FK> itr;
   BufferedReader in;
   StringBuilder filesb = new StringBuilder(100);
   StringBuilder chfilesb = new StringBuilder(100);
   int n;
   int loop=0;
   String s;
   try{sleep(2000);} catch(InterruptedException e) {} 
   boolean first=true;

    while(true) {
      try {                     // Runtime exception catcher
        if(terminate /*|| EdgeMom.shuttingDown*/) break;
        try {
          // clear all of the checks in the list
          itr = thr.values().iterator();
          while(itr.hasNext()) itr.next().setCheck(false);

          // Open file and read each line, process it through all the possibilities
          in = new BufferedReader(new FileReader(configFile));
          n=0;
          prta("Read in configfile for FK stations ="+configFile);
          while( (s = in.readLine()) != null) {
            if(s.length() < 1) continue;
            if(s.substring(0,1).equals("%")) break;       // debug: done reading flag
            if(!s.substring(0,1).equals("#") && !s.substring(0,1).equals("!")) {
              int comment = s.indexOf("#");
              if(comment >=0) s = s.substring(0,comment).trim();  // remove a later comment.
              String [] tk = s.split(":");
              //String [] tk = s.split(":");
              if(tk.length >= 2) {
                n++;
                String array=tk[0];
                String argline=tk[1];
                FK t  = thr.get(tk[0].trim());
                if(t== null) {
                  t = new FK(argline, array);
                  prta("FKM: New Station found start "+tk[0]+":"+tk[1]);
                  thr.put(array.trim(), t);
                  t.setCheck(true);
                }
                else if(!argline.trim().equals(t.getArgs().trim()) || !t.isAlive() || !t.isRunning()) {// if its not the same, stop and start it
                  t.setCheck(true);
                  prta("FKM: line changed or dead. alive="+t.isAlive()+" running="+t.isRunning()+"|"+tk[0]+"|"+tk[1]);
                  prt(argline.trim()+"|\n"+t.getArgs()+"|\n");
                  t.terminate();
                  while(t.isAlive()) {
                    try{sleep(10000);} catch(InterruptedException e) {}
                    if(t.isAlive()) prta("FKM: thread did not die in 10 seconds. "+array+" "+argline);
                  }
                  t = new FK(argline, array);
                  thr.put(array.trim(), t);
                  t.setCheck(true);
                } 
                else t.setCheck(true);
              }
            }
          }   // end of read config file lines
          in.close();
          itr = thr.values().iterator();
          while(itr.hasNext()) { 
            ManagerInterface t = itr.next();
            if(!t.getCheck()) {
              // This line is no longer in the configuration, shutdown the Q330
              prta("FKM: line must have disappeared "+t.toString()+" "+t.getArgs());
              t.terminate();
              itr.remove();
            }
          }
          try {sleep(10000);} catch(InterruptedException e2) {}
          
          // Now check that the file has not changed
          
       }
       catch(FileNotFoundException e) {
          prta("FKM: Could not find a config file!="+configFile+" "+e);
        }
        catch(IOException e) {
          prta("FKM: error reading the configfile="+configFile+" e="+e.getMessage());
          e.printStackTrace();
        }       
      }
      catch(RuntimeException e) {
        prta(tag+" RuntimeException in "+this.getClass().getName()+" e="+e.getMessage());
        if(getPrintStream() != null) e.printStackTrace(getPrintStream());
        else e.printStackTrace();
        if(e.getMessage() != null)
          if(e.getMessage().contains("OutOfMemory")) {
            SendEvent.edgeEvent("OutOfMemory","Out of Memory in "+Util.getProcess()+"/"+
                    this.getClass().getName()+" on "+IndexFile.getNode()+"/"+Util.getAccount(),this);
            throw e;
          }  
        // fkStringBuilder
      }
      String [] keys  = new String[thr.size()];
      keys = thr.keySet().toArray(keys);
      boolean changeChan=false;
      while(true) {
        if(terminate) break;
        //prt("Do Pass= noDB="+noDB+" dbconn="+dbconn);
        if(!noDB && dbconn != null) {
          try {
            Util.clear(fksb); 
            changeChan = fkStringBuilder((Util.getSystemName().equals("igskcicgltgm070") || Util.getSystemName().equals("gm044")?
                    "gldketchum3":Util.getSystemName()),fksb, chansb, first);
            prt("noDB="+noDB+" dbconn="+dbconn+"\n"+fksb.toString());
            first=false;
          }
          catch(RuntimeException e) {
            prta("FKM: got Runtime in gsnStringBuilder e="+e);
            e.printStackTrace(this.getPrintStream()) ;
          }
          // handle the FK config file
          readFile(configFile, filesb);
        }
        else {
          readFile(configFile,filesb);
          Util.clear(fksb);
          fksb.append(filesb);
        }
        

        // If the file has not changed check on down threads, if it has changed always to top
        if( (!Util.stringBuilderEqual(fksb,filesb) || changeChan)  && fksb.length() > 20) {
          prta("FKM: *** files have changed **** config changed="+Util.stringBuilderEqual(fksb,filesb)+" changeChan="+changeChan);
          writeFile(configFile, fksb);         // write out the file
          Util.clear(filesb).append(fksb);
          Iterator<StringBuilder> itrch = chansb.values().iterator();
          if(changeChan) {
            while(itrch.hasNext()) {
              StringBuilder sb = itrch.next();
              String filename = "config/"+sb.substring(0,sb.indexOf(" ")).replaceAll("-","")+".sta";
              writeFile(filename, sb);
            }
          }
          if(dbg) {
            prt("file="+filesb.length()+"\n"+filesb+"|");
            prt("fksb="+fksb.length()+"\n"+fksb+"|");
          }
          break;
        }
        else {
          if(dbg || loop++ % 10 == 1) prta("FKM: files are same");
          // check to make sure all of our children threads are alive!
          if(dbg) {
            prt("same file="+filesb.length()+"\n"+filesb+"|");
            prt("same fksb="+fksb.length()+"\n"+fksb+"|");
          }
          boolean breakout=false;
          for (String key : keys) {
            if (!thr.get(key).isAlive() || !thr.get(key).isRunning()) {
              prta("Found down thread alive=" + thr.get(key).isAlive() + " run=" + thr.get(key).isRunning() + " " + thr.get(key).toString());
              SendEvent.debugEvent("FKMgrThrRes", "Unusual restart of " + key, this);
              breakout=true;
              break;
            }
          }
          if(breakout) break;
          //thr.get(keys[2]).terminate();thr.get(keys[6]).terminate();   //DEBUG: force some down!
          try{sleep(120000);} catch(InterruptedException e) {}
        }
        if(System.currentTimeMillis() % 86400000L < 240000) {
          dbconn.setLogPrintStream(getPrintStream());
          dbconnedge.setLogPrintStream(getPrintStream());}
      }
      // The main loop has exited so the thread needs to exit
    
    }     // while(true)
    prta("FKM: start termination of ManagerInterfaces");
    itr = thr.values().iterator();
    while(itr.hasNext()) { 
      ManagerInterface t = itr.next();
      t.terminate();
    }
    prta("FKM: ** FKManager terminated ");
    try{Runtime.getRuntime().removeShutdownHook(shutdown);} catch(Exception e) {}
    running=false;            // Let all know we are not running
    terminate=false;          // sign that a terminate is no longer in progress
  }
  /** this internal class gets registered with the Runtime shutdown system and is
   * invoked when the system is shutting down.  This must cause the thread to exit
   */
  class ShutdownFKManager extends Thread {
    /** default constructor does nothing the shutdown hook starts the run() thread */
    public ShutdownFKManager() {
     gov.usgs.anss.util.Util.prta("new ThreadShutdown "+getName()+" "+getClass().getSimpleName());
    }
    
    // This is called by the RunTime.shutdown at any termination 
    @Override
    public void run() {
      prta("FKM: FKManager Shutdown() started...");
      terminate();          // send terminate to main thread and cause interrupt
      int loop=0;
      while (running) {
        // Do stuff to aid the shutdown
        try{sleep(1000);} catch(InterruptedException e) {}
        loop++;
        if(loop > 20) {prta("FKM: shutdown() is loop force exit"+loop); break;}
      }
      prta("FKM: Shutdown() of FKManager is complete.");
    }
  }
  static int ncalls=0;
  
  /** given a edge or gaux make a string
   * 
   * @param cpuname And edge node name normally
   * @param sb A string builder to use with the output
   * @param chansb An array of string builders to receive the channel configuration file
   * @param first True first time to do some setup.
   * @return true if something has changed in one of the StringBuilders for channels
   */ 
  public boolean fkStringBuilder (String cpuname, StringBuilder sb, TreeMap<String,StringBuilder> chansb, boolean first){
    boolean changed=false;
    int cpuID;
    if(forceNode != null) cpuname=forceNode;
    //cpuname = "gm044";    // DEBUG: force workstation 
    try {
      String s = "SELECT * FROM edge.fk WHERE updated>TIMESTAMPADD(SECOND,-230,now()) ORDER BY fk";
      if(first) s="SELECT * FROM edge.fk ORDER BY fk";
      ResultSet rs = dbconn.executeQuery(s);
      prta("Check FKM ncalls="+ncalls+" sb.len="+sb.length()+" cpu="+cpuname+" first="+first);
      if(!rs.next() && (ncalls++ % 10) != 0 && sb.length() != 0) {rs.close();return false;}
      rs.close();
      Util.clear(sb);       //    Boolean onlyone [] = new Boolean[200];
      //sb.append("#\n# Form is \"unique_key:fkargs [>][>>filename no extension]\"\n# for ").append(cpuname).append("\n");

      s = "SELECT * FROM edge.cpu WHERE cpu='"+cpuname+"' ;"; 
      rs = dbconn.executeQuery(s);
      if(rs.next()) {      
        cpuID = rs.getInt("ID");
      }
      else {
        prta("FKM: ***** no cpu found for cpu="+cpuname);
        return false;
      }
      rs.close();
      if(dbg) prta("FKM: cpuid="+cpuID+" for node="+cpuname);
      s = "SELECT * FROM edge.role where cpuid="+cpuID+" order by role";
      rs  = dbconn.executeQuery(s);
      roles.clear();
      while(rs.next()) {
        Role r = new Role(rs);
        roles.add(r);
        if(dbg) prta("FKM: add role="+r+" roles.size()="+roles.size());
      }
      rs.close();
    }
    catch (SQLException e) {
      Util.SQLErrorPrint(e,"GenEdgeStringBuilder on table anss.cpu SQL failed");
      prta("FKM: error reading db="+e);
    } 
    
    
    for (Role role : roles) {
      String roleip = role.getIP();
      if (dbg) {
        prta("Roleip is "+roleip+" for " + role.getRole());
      }
      FKSetup fk=null;
      try {
        String s = "SELECT * FROM edge.fk WHERE roleid=" + role.getID() + " ORDER BY fk";
        try (ResultSet rs = dbconn.executeQuery(s)) {
          sb.append("#\n# FK setup file for - ").append(role.getRole()).append("\n");
          int ich=0;
          while (rs.next()) {
            fk = new FKSetup(rs);
            sb.append(fk.getFKSetup()).append(":-s config/").append(fk.getFKSetup()).append(".sta").
                    append(" -fl ").append(fk.getHighPassFreq()).append("/").append(fk.getLowPassFreq()).
                    append("/").append(fk.getNpoles()).append("/").append(fk.getNpass()).append("/").
                    append(fk.getHighPassFreq() > 0.?"t":"f").
                    append("/").append(fk.getLowPassFreq() > 0. ?"t":"f").
                    append(" -fk ").append(fk.getKMAX()).append("/").append(fk.getNK()).append(" ").
                    append(" -bm ").append(fk.getFKHPC()).append("/").append(fk.getFKLPC()).
                    append(" -ftlen ").append(fk.getFTLEN()).append(" -l ").append(fk.getLatencySec() > 0?fk.getLatencySec():300).
                    append(" -beammethod ").append(fk.getBeamMethod()).
                    append(" -snr ").append(fk.getSNRLimit()).
                    append(" ").append(fk.getArgs()).
                    append(" >> ").append(fk.getFKSetup().toLowerCase()).append("\n");
            // Now need to construct the station file for this one
            /*StringBuilder csb;
            if(chansb.size() > ich) csb = chansb.get(i);
            else {
            csb = new StringBuilder(100);
            chansb.add(csb);
            }*/
            ich++;
            StringBuilder csb = chansb.get(fk.getFKSetup());
            if(csb == null) {
              csb = new StringBuilder(100);
              chansb.put(fk.getFKSetup(), csb);
            }
            Util.clear(chscr);
            String refchan = fk.getRefChan();
            String [] stats = fk.getChannels().split("\n");
            boolean use;
            if(refchan.charAt(0) == '+') {
              use =true;
              refchan=refchan.substring(1);
            }
            else use=false;
            refchan = (refchan+"         ").substring(0,12).replaceAll("-"," ").replaceAll("_", " ");
            double [] coord = stasrv.getMetaCoord(refchan);
            Channel c = EdgeChannelServer.getChannel(refchan);
            double rate;
            if(c == null) {
              SendEvent.edgeSMEEvent("FKBadRef", fk.getFKSetup()+" "+refchan+" does not have a channel record for the rate", this);
              String meta = stasrv.getSACResponse(refchan, null, "nm");
              MDSChannel mds = new MDSChannel(meta);
              rate = mds.getRate();
              prta(refchan+" ***** Does not have a channel record! rate from MDS="+rate);
              //return changed;
            }
            else rate = c.getRate();
            
            refchan = refchan.replaceAll(" ", "_");
            chscr.append(fk.getFKSetup()).append(" ").append(refchan.substring(0,2)).append(" ").
                    append(refchan.substring(2,7)).append(" ").append(refchan.substring(7,10)).append(" ").
                    append(refchan.substring(10,12)).append(" ").append(Util.df24(coord[0])).append(" ").
                    append(Util.df24(coord[1])).append(" ").append(Util.df23(coord[2]/1000.)).append(" ").
                    append("0.000 ").append(use?"1":"0").append(" ").append(Util.df24(rate)).append("\n");
            if(c == null) chscr.append("##RefChannel does not have a channel record!").append(refchan).append("\n");
            for(String stat: stats) {
              if(stat.charAt(0) == '-' || stat.charAt(0) == '#' || stat.charAt(0) == '!') {
                use =false;
                stat=stat.substring(1);
              }
              else use=true;
              stat = (stat+"         ").substring(0,12).replaceAll("-"," ").replaceAll("_", " ");
              coord = stasrv.getMetaCoord(stat);
              stat = stat.replaceAll(" ", "_");
              if(coord[0] == 0. || coord[1] == 0.)
                prta(stat+" bad coord? "+coord[0]+" "+coord[1]);
              chscr.append(fk.getFKSetup()).append(" ").append(stat.substring(0,2)).append(" ").
                      append(stat.substring(2,7)).append(" ").append(stat.substring(7,10)).append(" ").
                      append(stat.substring(10,12)).append(" ").append(Util.df24(coord[0])).append(" ").
                      append(Util.df24(coord[1])).append(" ").append(Util.df23(coord[2]/1000.)).append(" ").
                      append("0.000 ").append(use?"1":"0").append("\n");
            }
            // Is this the same as the original
            if(!Util.stringBuilderEqual(chscr, csb)) {
              changed=true;
              Util.clear(csb).append(chscr);    // substitute the new one
            }
          }
        }
      }catch (SQLException e) {
        e.printStackTrace(getPrintStream());
        Util.SQLErrorPrint(e,"makeEdgemomsetups() on table SQL failed");
      }catch(RuntimeException e) {
        prta("Got RuntimeException in gsnStringBuilder()"+fk+" e="+e);
        e.printStackTrace(getPrintStream());
      }
    }  
    return changed;
  }
  private byte [] buf = new byte[1000];
  public void writeFile(String filename, StringBuilder wfsb){
    //StringBuilder statussb = new StringBuilder(100);
    RandomAccessFile fileout;
    try {

      fileout = new RandomAccessFile(filename,"rw");
      fileout.seek(0L);
    }
     catch (IOException e) {
      prta("Error writing to file"+e.getMessage());
      return;
    }
    try {
      if(wfsb.length() > buf.length) buf = new byte[wfsb.length()*2];
      Util.stringBuilderToBuf(wfsb, buf);
      fileout.write(buf, 0, wfsb.length());
      fileout.setLength( (long) wfsb.length());
      fileout.close();
     }
     catch (IOException e) {  
       prta("Error writing to file"+e.getMessage());
     }    
  }
  private byte [] readbuf = new byte[1000];
  public void readFile(String filename, StringBuilder rfsb){
    RandomAccessFile filein;
    int length;
    try {
     filein = new RandomAccessFile(filename,"r");
    }
    catch (FileNotFoundException e) {  
      prta("Error reading from file"+e.getMessage());
      return;
    }
    try {
      length = (int) filein.length();
      if(length > readbuf.length) readbuf = new byte[length*2];
      filein.readFully(readbuf,0,length);
      filein.close();
      Util.clear(rfsb);
      for(int i=0; i<length; i++) rfsb.append((char) readbuf[i]);
//       for (int i = 1;i<b.length;i++) rfsb.append(b[i]);
    }
    catch (IOException e) {
      prta("Error reading file"+e.getMessage());
    }  
  }
  public static void main(String [] args) {
    Util.init("edge.prop");
    Util.setModeGMT();
    EdgeChannelServer echn = new EdgeChannelServer("-empty >>echnfk", "ECHN");
    while(!EdgeChannelServer.isValid()) Util.sleep(100);
    FKManagerObs fkm = new FKManagerObs("-config config/fk.setup -nodb >>fkm", "FKM");
    for(;;) Util.sleep(1000);
  }
}
