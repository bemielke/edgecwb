/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */


package gov.usgs.anss.fetcher;
import gov.usgs.alarm.SendEvent;
import gov.usgs.anss.db.DBConnectionThread;
import gov.usgs.anss.edge.RawDisk;
import gov.usgs.anss.edgethread.EdgeThread;
import gov.usgs.anss.seed.MiniSeed;
import gov.usgs.anss.edgethread.MemoryChecker;
import gov.usgs.anss.util.Subprocess;
import gov.usgs.anss.util.Util;
import gov.usgs.edge.config.RoleInstance;
import gov.usgs.edge.config.RequestType;
import gov.usgs.edge.config.FetchServer;
import java.io.IOException;
import java.io.File;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.TreeMap;
/**  This class is the main for fetchers that get many servers/channels from a multistation server
 like Query Server, SeedLink, Trinet Wave Server, Earthworm Wave server.  It creates an instance of
 * the Fetcher subclass for each fetch server in the fetchserver table that is assigned to the role this
 * process is started on.  
 * <p>
 * The fetchserver table contains information on the ip, port of the server, the requesttype to use (controls
 * the output to a CWB or Edge pair, the class to use for fetches, and fetch table).  It also specifies the role
 * where this fetchserver is to run, the gap type, and a channel regular expression which must match the channel/location 
 * code of the 12 character NNSSSSSCCCLL seedname.  This class creates a fetcher of the class from the request type,
 * and specifies all of the creation line arguments based on the contents of the request type and fetch server.
 * 
 * <PRE>
 * switch              arg   description
 * -ignoreholdings           If present, data obtained from a fetch does not have to pass the holdings test for duplicate data
 * -logpath          path    Put the log files from this process in this directory.
 * </PRE>
 * TODO:  Single mode has not been tested.
 * @author U.S. Geological Survey  <ketchum at usgs.gov>
 */
public final  class FetchServerMain extends Thread {
  String gapType="";
  String fetchServer = Util.getProperty("DBServer");
  String seedname=null;
  boolean singleMode=false;
  boolean localCWB=false;
  String filename=null;
  long startTime=System.currentTimeMillis();
  String [] args;
  public static int state=0;
  public FetchServerMain(String [] args2) {
    args=args2;
    String badNonSingleArg=null;
    state=1;
    String singleModeClassname="SeedLinkServer";
    for(int i=0; i<args.length; i++) {
      if(args[i].equals("-t")) gapType=args[i+1];
      if(args[i].equals("-db")) fetchServer = args[i+1];
      if(args[i].equals("-statre")) seedname=args[i+1];
      if(args[i].equals("-1")) {singleMode=true; singleModeClassname=args[i+1];i++;}
      if(args[i].equals("-f")) filename=args[i+1];
      if(args[i].equals("-localcwb")) localCWB=true;
      if(args[i].equals("-logpath")) {
        File d = new File(args[i+1]);
        if(d.exists()) {
          if(!d.isDirectory()) {
            EdgeThread.staticprt("****** Log path is not a directory !!!!!");
            Util.exit(0);
          }
        }
        else {
          Util.chkFilePath(args[i+1]+Util.fs+"tmp.tmp");    // create the directory
        }
      }

      if(args[i].equals("-table") || args[i].equals("-cwbip") || args[i].equals("-c")
              || (args[i].equals("-s")) || args[i].equals("-h")|| 
              args[i].equals("-throttle")|| args[i].equals("-p") ||
              args[i].equals("-cwbport")|| args[i].equals("-edgeport")|| args[i].equals("-edgeip")
              ) {
        badNonSingleArg=args[i];
      }
    }
    EdgeThread.staticprt("FetcherMain fetchServer="+fetchServer+" seedname="+seedname+" 1mode="+singleMode+
            "/"+singleModeClassname+
            " file="+filename+
            " localcwb="+localCWB);

    // Single mode is designed to do a single fetch of on channel from any requestor
    // Normally such data is written to a file!
    if(singleMode) {
      try {
        String classname="SeedlinkRequest";
        EdgeThread.staticprta("****** FetchServermain: single mode is completely untested!!!!");
        if(!classname.contains(".")) classname="gov.usgs.anss.fetcher."+classname;  // add full path
        Class cl = Class.forName(classname);
        Class [] types = new Class[1];
        types[0] = args.getClass();
        Object [] objargs = new Object[1];
        objargs[0] = args;
        Constructor cons = cl.getConstructor(types);
        try {
          Fetcher fetch =(Fetcher) cons.newInstance(objargs);
          if(filename == null) {    // not destined for a file, process it using the thread to CWB
            fetch.startit();
            while(fetch.isAlive()) Util.sleep(1000);
          }
          else {      // This is destined for a file
            ArrayList<MiniSeed> mss = fetch.getData(fetch.getFetchList().get(0));
            if(mss == null) EdgeThread.staticprt("Single fetch returned 'nodata'");
            else {
              EdgeThread.staticprt("Single fetch returned "+mss.size()+" blks write to file="+filename);
              for (MiniSeed ms : mss) {
                EdgeThread.staticprt(ms.toString());
              }
              try (RawDisk rw = new RawDisk(filename,"rw")) {
                int iblk=0;
                for (MiniSeed ms : mss) {
                  rw.writeBlock(iblk, ms.getBuf(), 0, ms.getBlockSize());
                  iblk += ms.getBlockSize() / 512;
                }
                rw.setLength(iblk*512);
              }
            }
          }
        } catch (InstantiationException ex) {
          EdgeThread.staticprt(" ** Instantiation error e="+ex);
        } catch (IllegalAccessException ex) {
          EdgeThread.staticprt(" ** IllegalAccept Exception e="+ex);
        } catch (IllegalArgumentException ex) {
          EdgeThread.staticprt(" ** IllegalArgument e="+ex);
        } catch (InvocationTargetException ex) {
          EdgeThread.staticprt(" ** InvocationTarget e="+ex);
          ex.printStackTrace();
        }
        catch(IOException e) {
          EdgeThread.staticprt(" **** IOException getData() on single fetch");
          Util.exit(1);
        }
      }
      catch(ClassNotFoundException e) {
        EdgeThread.staticprt(" ** Got a ClassNotFound for class="+singleModeClassname+" e="+e);
        e.printStackTrace();
      }
      catch(NoSuchMethodException e) {
        EdgeThread.staticprt(" ** Got a NoSuchMethod for class="+singleModeClassname+" e="+e);
        e.printStackTrace();
      }
      System.exit(0);
    }
    state=2;
    // This is a group fetch, check to make sure no switches are set to interfere with this
    if(badNonSingleArg != null) {
      EdgeThread.staticprt(" ******* For a non-single fetch you cannot specify switch="+badNonSingleArg);
      Util.exit(0);
    }
    start();
  }
  @Override
  public void run() {
    DBConnectionThread dbconn=null;
    state=3;
    MemoryChecker memchk = new MemoryChecker(600,null);
    while(dbconn == null) {
      try {
        //if(DBConnectionThread.getConnection("anss") == null) new DBConnectionThread(fetchServer, "anss","ro","readonly", false, false, "anss");
        dbconn = DBConnectionThread.getThread("edgeMAIN");
        if(dbconn == null) {
          dbconn = new DBConnectionThread(fetchServer, "update", "edge",  true, false,"edgeMAIN", Util.getOutput());

          if(!DBConnectionThread.waitForConnection("edgeMAIN"))
            if(!DBConnectionThread.waitForConnection("edgeMAIN"))
              if(!DBConnectionThread.waitForConnection("edgeMAIN")) {
                EdgeThread.staticprt(" **** Could not connect to database "+fetchServer);
                Util.exit(1);
              }
        }
      }
      catch(InstantiationException e) {
        EdgeThread.staticprta(" **** Impossible Instantiation exception e="+e);
        Util.exit(0);
      }
    }
    state=4;
    SendEvent.debugSMEEvent("FetchServerStart", "Fetcher for "+gapType+" is starting", this);
    // Make a list of servers with this gap type
    TreeMap<String, Fetcher> fetchers = new TreeMap<>();
    TreeMap<String, FetchServer> servers = new TreeMap<>();
    TreeMap<String, RequestType> requestTypes = new TreeMap<>();
    StringBuilder sb = new StringBuilder(1000);
    StringBuilder argLine = new StringBuilder(100);
    Iterator itr;
    boolean first=true;
    long now = System.currentTimeMillis();
    long lastStatus=System.currentTimeMillis()-1500000;
    boolean done=false;
    int loop=0;
    String cpuname = Util.getSystemName();
    int cpuID=0;
    ArrayList<RoleInstance> roles = new ArrayList<>(10);
    String inRole="";
    try {
      String s  = "SELECT * FROM edge.cpu WHERE cpu='"+cpuname+"'";
      try (ResultSet rs = dbconn.executeQuery(s)) {
        if(rs.next()) {
          cpuID = rs.getInt("ID");
        }
        else {
          EdgeThread.staticprta("FSL: ***** no cpu found for cpu="+cpuname);
          return;
        }
      }
      EdgeThread.staticprta("FSL: cpuid="+cpuID+" for node="+cpuname);
    }
    catch (SQLException e) {
      Util.SQLErrorPrint(e,"Getting CPU to match "+cpuname+" SQL failed");
      EdgeThread.staticprta("FSL: error reading db="+e);
      Util.exit(1);
    }  
    long lastRequestType=2000000;
    /* This is the main loop which checks on configuration changes, and individual
     * fetchers.  It is repeated every 10 seconds (wait done at bottom of loop).
     */
    while(!done) {
      state=5;
      // These task are done every 30 loops or 5 minutes
      if(loop % 30 == 0) {
        state=6;
        if(!first) {
          state=7;
          EdgeThread.staticprta(Util.ascdate()+" * Main() : Read the fetchserver table and type files looking for new serverss. "+loop);
        }
        
        // Refigure the roles and request types
        try {
          String s = "SELECT * FROM edge.role where cpuid="+cpuID+" OR failovercpuid="+cpuID+" order by role";
          ResultSet rs  = dbconn.executeQuery(s);
          roles.clear();
          inRole="";
          while(rs.next()) {
            RoleInstance r = new RoleInstance(rs);
            if( (!r.isFailedOver() && r.getCpuID() == cpuID) || (r.isFailedOver() && r.getFailoverCpuID() == cpuID)) {
              roles.add(r);
              inRole += r.getID()+",";
              EdgeThread.staticprta("FSL: add role="+r+" roles.size()="+roles.size()+" cpuid="+cpuID+" failed="+r.isFailedOver()+
                        " role.cpuid="+r.getCpuID()+" failCpuid="+r.getFailoverCpuID());
            }
          }
          rs.close();
          s = "SELECT * FROM requesttype WHERE updated > '"+Util.ascdatetime(lastRequestType)+"' ORDER BY id";
          rs = dbconn.executeQuery(s);
          int ncnt=0;
          while(rs.next()) {
            RequestType tp = new RequestType(rs);
            requestTypes.put(""+tp.getID(), tp);
            ncnt++;
          }
          lastRequestType = System.currentTimeMillis();
          EdgeThread.staticprt("RequestTypes updated="+ncnt+" size="+requestTypes.size());
          rs.close();
        }
        catch (SQLException e) {
          Util.SQLErrorPrint(e,"GenEdgeStringBuilder on table anss.cpu SQL failed");
          EdgeThread.staticprta("FSL: error reading db="+e);
          Util.exit(1);
        }     
        inRole = inRole.substring(0, inRole.length()-1);
        
        // Examine the fetchserver table for things assigned to this role
        boolean ok=false;
        state=11;
        while(!ok && !Fetcher.shuttingDown) {
          state=12;
          try {
            String s = "SELECT * FROM fetchserver WHERE roleid IN ("+inRole+") AND gaptype!='' AND discoverymethod!='NONE' ORDER BY fetchserver";
            Object [] keys =  fetchers.keySet().toArray();
            state=41;
            EdgeThread.staticprta("Start query cycle for request changes loop="+loop+" "+
                    DBConnectionThread.getThreadList()+" "+DBConnectionThread.getThread("edgeMAIN").toString());
            try (ResultSet rs = dbconn.executeQuery(s)) {
              EdgeThread.staticprta("End query cycle for request changes s="+s);
              // loop through all matching fetchserver entries.
              while(rs.next()) {
                state=13;
                
                // If this server does not exist, create it
                if(servers.get(rs.getString("fetchserver")) == null) {      // Does this fetchsever not exist?
                  FetchServer srv = new FetchServer(rs);
                  servers.put(srv.getFetchServer(), srv);
                  // Get the request type for this fetcher

                  RequestType requestType = requestTypes.get(""+srv.getFetchRequestTypeID());
                  if(requestType == null) {
                    EdgeThread.staticprt("For fetch server ="+srv+" there is not a valid request type!  Skip");
                    continue;
                  }
                  String [] arg = new String[21+args.length];
                  arg[0]="-chanre";
                  arg[1]="^......."+srv.getChanRE();
                  arg[2]="-t";
                  arg[3]=""+srv.getGapType();
                  arg[4]="-cwbip";
                  arg[5]=requestType.getCWBIP();
                  arg[6]="-cwbport";
                  arg[7]=""+requestType.getCWBPort();
                  arg[8]="-edgeip";
                  arg[9]=requestType.getEdgeIP();
                  arg[10]="-edgeport";
                  arg[11]=""+requestType.getEdgePort();
                  arg[12]="-h";
                  arg[13]=srv.getFetchIpadr();
                  arg[14]="-p";
                  arg[15]=""+srv.getFetchPort();
                  arg[16]="-table";
                  arg[17]="fetcher."+requestType.getTableName();
                  arg[18]="-tag";
                  arg[19]=srv.getFetchServer().replaceAll(" ","");
                  arg[20+args.length]=requestType.getRequestClass();
                  for(int i=20; i<20+args.length; i++) {
                    arg[i] = args[i-20];
                  }
                  String [] args2 = new String[arg.length-1];
                  //for(int j=0; j<arg.length-1; j++) args2[j] = arg[j];  // copy command line to temp array
                  System.arraycopy(arg, 0, args2, 0, arg.length-1);   // copy command line to temp array
                  String classname = arg[20+args.length];
                  if(first) {
                    state=14;
                    //EdgeThread.setMainLogname(srv.getFetchServer());
                    first=false;
                  }
                  // This sets the default log name in edge thread (def=edgemom!)
                  state=17;
                  Util.clear(argLine);
                  for (String args21 : args2) {
                    argLine.append(args21).append(" ");
                  }
                  EdgeThread.staticprta("Main() start "+srv+" "+classname+" "+argLine);
                  
                  try {
                    if(!classname.contains(".")) classname="gov.usgs.anss.fetcher."+classname;  // add full path
                    
                    Class cl = Class.forName(classname);
                    Class [] types = new Class[1];
                    types[0] = args2.getClass();
                    Object [] objargs = new Object[1];
                    objargs[0] = args2;
                    Constructor cons = cl.getConstructor(types);
                    try {
                      state=18;
                      Fetcher f = (Fetcher) cons.newInstance(objargs);
                      fetchers.put(srv.getFetchServer(), f);
                      EdgeThread.staticprt(f+" created");
                      f.startit();
                      state=19;
                    } catch (InstantiationException ex) {
                      EdgeThread.staticprt(" ** Main() Instantiation error e="+ex);
                      ex.printStackTrace();
                    } catch (IllegalAccessException ex) {
                      EdgeThread.staticprt(" ** Main() IllegalAccess Exception e="+ex);
                      ex.printStackTrace();
                    } catch (IllegalArgumentException ex) {
                      EdgeThread.staticprt(" ** Main() IllegalArgument e="+ex);
                      ex.printStackTrace();
                    } catch (InvocationTargetException ex) {
                      EdgeThread.staticprt(" ** Main() InvocationTarget e="+ex);
                      ex.printStackTrace();
                    }
                  }
                  catch(ClassNotFoundException e) {
                    EdgeThread.staticprt(" ** Main() Got a ClassNotFound for class="+classname+" Did you mistype the class name? e="+e);
                    e.printStackTrace();
                  }
                  catch(NoSuchMethodException e) {
                    EdgeThread.staticprt(" ** Main() Got a NoSuchMethod for class="+classname+" e="+e);
                    e.printStackTrace();
                  }
                }       // It is a new fetcher
                else {  // eixting fetch serer
                  // Mark each known station out of the list of keys, if any keys remain, they are no longer configured
                  for(int i=0; i<keys.length; i++) {
                    if(keys[i] != null) {
                      //EdgeThread.staticprt(((String) keys[i])+"| to "+rs.getString("requeststation")+"|"+ ((String) keys[i]).equals(rs.getString("requeststation")));
                      if(((String) keys[i]).equals(rs.getString("fetchserver")))
                        keys[i] = null;     // Use null to indicate this fetch server is still is in the result set.
                    }
                  }
                  
                }   // if on each fetch server
                state=21;
              }   // rs.next()
              state=22;
            }   // end of resultset on fetchservers
            state=23;

            // Any servers remaining on the list of keys are not longer configured, stop their fetchers
            ok=true;
            for (Object key : keys) {
              if (key != null) {
                EdgeThread.staticprta("Main() need to drop " + key);
                Fetcher f = fetchers.get((String) key);
                EdgeThread.staticprt("Main() Stop fetcher="+f);
                f.terminate();
                fetchers.remove((String) key);
                servers.remove((String) key);
              }           
            }
            state=24;
          }
          catch(SQLException e) {
            state=25;
            Util.SQLErrorPrint(e," ** Main() Could not get information from request tables loop="+loop);
            e.printStackTrace();
            //Util.exit(1);
          }
        } // If !ok && !shutting down
      }   // IF time to check database for new servers (loop % 30 == 0)
      state=26;
      // Create all of the baler as separate threads
      Util.clear(sb);
      int count=0;
      done=true;
      itr = fetchers.values().iterator();
      long curr = System.currentTimeMillis();
      if(/*curr - startTime > 86400000L ||*/ Fetcher.shuttingDown)
        EdgeThread.staticprta("Main() 24 hours is up or shutdown started - close all fetching threads."+Fetcher.shuttingDown);
      while(itr.hasNext()) {
        Fetcher f = (Fetcher) itr.next();
        if(f != null) {
          if(f.isAlive()) {done=false; count++;}
          if(f.getFetchList().size() > 0 || loop % 30 == 0 || f.isZombie()) sb.append(Util.asctime()).append(" ").append(f.toString()).append("\n");
          if(/*curr - startTime > 86400000 ||*/ Fetcher.shuttingDown) {
            f.terminate();
          }
          else {
            if(f.isZombie() ) {
              EdgeThread.staticprta("Zombie found. Need to restart "+servers.get(f.getSeedname().substring(0,7).trim())+" f="+f);

            }
          }
        }
      }
      state=27;
      if(/*System.currentTimeMillis() - startTime > 86400000 ||*/ Fetcher.shuttingDown) {
        state=28;
        EdgeThread.staticprta(Util.ascdate()+"Main()  24 hours is up or shutting down exiting (hopefully gracefully)!"+Fetcher.shuttingDown);
        Util.sleep(30000);
        done=true;
        lastStatus=0;
      }
      if(System.currentTimeMillis() - lastStatus > 1800000 || done) {
        state=29;
        EdgeThread.staticprta("Main() FetcherServer has "+count+" threads still alive of "+fetchers.size()+" loop="+loop+" done="+done);
        lastStatus=System.currentTimeMillis();
        EdgeThread.staticprt(sb.toString());
        //DBConnectionThread.keepFresh();
      }
      state=30;
      loop++;
      Util.sleep(10000);
    }     // while !done
    state=31;
    SendEvent.debugSMEEvent("FetchServerStop", "FetchServer for "+cpuname+" is exiting. "+Fetcher.shuttingDown, this);
    EdgeThread.staticprta(Util.ascdate()+"Main()  Out of done loop.  Check for running servers.");
    Util.sleep(10000);
    itr = fetchers.values().iterator();
    int count=0;
    Util.clear(sb);
    sb.append("Main() Final thread check \n");
    while(itr.hasNext()) {
      Fetcher f = (Fetcher) itr.next();
      if(f != null) {
        if(f.isAlive()) {count++;}
        if(f.getFetchList().size() > 0 || loop % 30 == 0) sb.append(Util.asctime()).append(" ").append(f.toString()).append("\n");
      }
    }
    state=32;
    EdgeThread.staticprta(sb.toString()+"\n"+Util.asctime()+" Main() Out of main done loop.  Exitting #alive="+count+" wait 30 and do last check");
    Util.sleep(30000);
    DBConnectionThread.shutdown();
    EdgeThread.staticprt(Util.getThreadsString());
    EdgeThread.staticprta("Main() FetcherServerMain.thread is exiting....");
    Util.sleep(2000);
    state=33;
  }
  /**
  * @param args the command line arguments
  */
  public static void main(String[] args) {
    Util.setModeGMT();
    EdgeThread.setMainLogname("FetchServer");
    Util.init("edge.prop");
    Util.setProcess("FetchServer");
    Util.setNoconsole(false);
    Util.setNoInteractive(true);
    if(args.length == 0) {
      args = new String[3];
      args[0]="-logpath";
      args[1]="logreq";
      args[2]="-ignoreholdings";
      
    }
    if(args.length < 0) {
      Util.prt("Usage: FetcherSeedLing -1 -s NNSSSSSCCCLL -b yyyy/mm/dd hh:mm:ss -d dddd -c class -h gsn.stat.ip.adr [-p port (def=4003)][-logpath path][-f file] ");
      Util.prt("  OR   Fetcher -t XX [-statre re][-b yyyy/mm/dd hh:mm}][-e yyyy/mm/dd hh:mm][-db fetch.list.db][-logpatn path][-latip ip][-latport port]"); // Get all the fetchlist list type XX on fetchlist server");
      Util.prt("In the NNSSSSSCCCLL use - for all spaces.  The name must be 12 characters long.");
      Util.prt("For non-fetchlist mode (-1 mode) :");
      Util.prt("    A filename based on the NNSSSSSCCCLL (spaces become _) with .ms extension will be created");
      Util.prt("    -h  ip.adr      IP address of the server providing data ");
      Util.prt("    -p  port        Port providing the request data on server ");
      Util.prt("    -c  class       The class of the requestor (BalerRequest, GSNQ680Request, ...) def=BalerRequest");
      Util.prt("    -e and -b yyyy/mm/dd hh:mm restrict the start time of the gap to between these two times.");
      Util.prt("    -d  secs        The duration in seconds of the made up fetchlist ");
      Util.prt("    -f  filename    Write returned data to a file and not to CWB/Edge");
      Util.prt("  Throttle related :");
      Util.prt("   -throttle baud   Set maximum baud rate");
      Util.prt("    -latip  ip.adr   IP address of latency server ");
      Util.prt("    -latport port    The port of the LatencyServer ");
      Util.prt("For fetchlist mode: ");
      Util.prt("  Switches set on command line :");
      Util.prt("    -t    type       Set the type of requests to use ");
      Util.prt("    -e and -b yyyy/mm/dd hh:mm restrict the start time of the gap to between these two times.");
      Util.prt("    -logpath path    Put the log files in this path directory instead of ./log");
      Util.prt("    -dbg             Turn on more logging output ");
      Util.prt("    -statre  regexp  Do fetches only on stations which match this station regular expression.");
      Util.prt("    -db  ip.adr      Override the IP address of the fetchlist property DBServer ");
      Util.prt("    -latip   ip.adr  Use this latency server instead of property DBServer");
      Util.prt("    -latport ppppp   User this port instead of 7956 for latency server ");
      Util.prt("    -ignoreHolding   Set ignore holdings flag");
      Util.prt("  Switches set from RequestType and RequestStation tables :");
      Util.prt("    -h   ip.adr.req  Set this address as the IP address to make the request (generally a station)");
      Util.prt("    -p    nnnnn      Set the port for this rerequest on the field station");
      Util.prt("    -s regexp        Channel regular expression for channels matching this run");
      Util.prt("    -cwbip ip.adr    override cwb ip address 'null' means do not use cwbip (def=gcwb)");
      Util.prt("    -cwbport nnn     CWB port override from 2062");
      Util.prt("    -edgeip ip.adr   override edge ip address 'null' means do not use edgeip (def=gacq1)");
      Util.prt("    -edgeport nnnn   Set Edge Port (def=7974)");
      Util.prt("    -dbg             Turn on debugging");
      Util.prt("    -b44dbg          Turn on full debugging in the Baler44Request");
      Util.prt("    -throttle nnnn   Make throttle upper bound the nnnn value");
      Util.prt("    -table  tbname   the fetchlist table to run this request against (from RequestType table)");
      System.exit(0);
    }
    String localGapType="";
    EdgeThread.setMainLogname("FetchServer");
    for (String arg : args) {
    }
    FetchServerMain maincode = new FetchServerMain(args);
    int loop=0;
    int state9=0;
    Util.sleep(100000);
    while(maincode.isAlive() || state != 33) { // wait for main thread to exit (state 33 means it thinks it has!)
      loop++;
      if(loop%180 == 1) EdgeThread.staticprta(Util.ascdate()+" Main() stat="+state);
      Util.sleep(10000);
      if(state != 30) state9++;
      else state9=0;
      if(state9 > 360) {
        if(state == 41) {
          DBConnectionThread thr = DBConnectionThread.getThread("edgeMAIN");
          if(thr != null) thr.terminate();
        }
        SendEvent.debugSMEEvent("FetcherStuck9","Fetcher for "+localGapType+" is stuck state="+state, "Fetcher");
        try {
          Subprocess hammer = new Subprocess("bash scripts/hammerFetchers.bash");
          Util.sleep(30000);
          EdgeThread.staticprt("Hammer output="+hammer.getOutput()+"\nErr="+hammer.getErrorOutput());
          Util.exit(102);
        }
        catch(IOException e) {}
        state9=1;
      }
    }
    EdgeThread.staticprt("FetcherMain.main() has detected Shutdown state="+state);
    System.exit(101);
  }
}
