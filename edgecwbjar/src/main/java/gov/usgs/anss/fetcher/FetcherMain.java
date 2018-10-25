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
import gov.usgs.anss.edgemom.EdgeChannelServer;
import gov.usgs.anss.util.Util;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.TreeMap;
/** This is the Main class for staring a fetcher.  A single fetcher deals with only one specialization (external
 * data source) protocol.  So there are separate fetchers for Balers or EarthWorm Wave Servers, etc.
 * 
 * <PRE>
 *  Normally fetchers are setup in the role crontab with this minimal line 
 * chkCWB fetcher ^ 350 -tag GP -t 'GP|QL|GZ' -ignoreholdings -logpath logreq 
 *
 * The -tag is used when more than one fetcher is run on a server to distinguish them and the -t is mandatory
 * to set the gap/fetch types that the fetcher will use.  Putting the logs in a separate directory is recommended
 * to ease the management of these files.
 *       
 * Util.prt("Usage: Fetcher -1 -s NNSSSSSCCCLL -b yyyy/mm/dd hh:mm:ss -d dddd -c class -h gsn.stat.ip.adr [-p port (def=4003)][-logpath path][-f file] 
 *   OR   Fetcher -t XX [-statre re][-b yyyy/mm/dd hh:mm}][-e yyyy/mm/dd hh:mm][-db fetch.list.db][-logpatn path][-latip ip][-latport port] // Get all the fetchlist list type XX on fetchlist server
 * In the NNSSSSSCCCLL use - for all spaces.  The name must be 12 characters long.
 * For non-fetchlist mode (-1 mode) :
 *     A filename based on the NNSSSSSCCCLL (spaces become _) with .ms extension will be created
 *     -h  ip.adr      IP address of the server providing data 
 *     -p  port        Port providing the request data on server 
 *     -c  class       The class of the requestor (BalerRequest, GSNQ680Request, ...) def=BalerRequest
 *     -e and -b yyyy/mm/dd hh:mm restrict the start time of the gap to between these two times.
 *     -d  secs        The duration in seconds of the made up fetchlist 
 *     -f  filename    Write returned data to a file and not to CWB/Edge
 *   Throttle related :
 *    -throttle baud   Set maximum baud rate
 *     -latip  ip.adr   IP address of latency server 
 *     -latport port    The port of the LatencyServer 
 * For fetchlist mode: 
 *   Switches set on command line :
 *     -t    type       Set the type of requests to use 
 *     -e and -b yyyy/mm/dd hh:mm restrict the start time of the gap to between these two times.
 *     -logpath path    Put the log files in this path directory instead of ./log
 *     -dbg             Turn on more logging output 
 *     -statre  regexp  Do fetches only on stations which match this station regular expression.
 *     -db  ip.adr      Override the IP address of the fetchlist property DBServer 
 *     -latip   ip.adr  Use this latency server instead of property DBServer
 *     -latport ppppp   User this port instead of 7956 for latency server 
 *     -ignoreHolding   Set ignore holdings flag
 *     -localbaler      Set to use a local baler at 20.99 for this fetchlist
 *   Switches set from RequestType and RequestStation tables :
 *     -h   ip.adr.req  Set this address as the IP address to make the request (generally a station)
 *     -p    nnnnn      Set the port for this rerequest on the field station
 *     -s regexp        Channel regular expression for channels matching this run
 *     -cwbip ip.adr    override cwb ip address 'null' means do not use cwbip (def=gcwb)
 *     -cwbport nnn     CWB port override from 2062
 *     -edgeip ip.adr   override edge ip address 'null' means do not use edgeip (def=gacq1)
 *     -edgeport nnnn   Set Edge Port (def=7974)
 *     -dbg             Turn on debugging
 *     -b44dbg          Turn on full debugging in the Baler44Request
 *     -throttle nnnn   Make throttle upper bound the nnnn value
 *     -table  tbname   the fetchlist table to run this request against (from RequestType table)
 * 
 * @author U.S. Geological Survey  <ketchum at usgs.gov>
 */
public class FetcherMain extends Thread {
  public static EdgeChannelServer channels;
  String gapType="";
  String fetchServer = Util.getProperty("DBServer");
  String seedname=null;
  boolean singleMode=false;
  boolean localBaler=false;
  boolean localCWB=false;
  String classname="BalerRequest";
  String filename=null;
  long startTime=System.currentTimeMillis();
  
  String [] args;
  public static int state=0;
  public FetcherMain(String [] args2) {
    args=args2;
    String badNonSingleArg=null;
    //EdgeThread.setMainLogname("Fetcher");
    state=1;
    for(int i=0; i<args.length; i++) {
      if(args[i].equals("-t")) gapType=args[i+1];
      if(args[i].equals("-db")) fetchServer = args[i+1];
      if(args[i].equals("-statre")) seedname=args[i+1];
      if(args[i].equals("-1")) singleMode=true;
      if(args[i].equals("-f")) filename=args[i+1];
      if(args[i].equals("-c")) classname=args[i+1];
      if(args[i].equals("-localbaler")) localBaler=true;
      if(args[i].equals("-localcwb")) localCWB=true;

      if(args[i].equals("-table") || args[i].equals("-cwbip") || args[i].equals("-c")
              || (args[i].equals("-s") && !localBaler) || args[i].equals("-h")|| 
              args[i].equals("-throttle")|| args[i].equals("-p") ||
              args[i].equals("-cwbport")|| args[i].equals("-edgeport")|| args[i].equals("-edgeip")
              ) {
        badNonSingleArg=args[i];
      }
    }
    Util.prt("FetcherMain fetchServer="+fetchServer+" seedname="+seedname+" 1mode="+singleMode+
            " file="+filename+" class="+classname+" localbaler="+localBaler+" localcwb="+localCWB);

    // Single mode is designed to do a single fetch of on channel from any requestor
    // Normally such data is written to a file!0
    if(singleMode) {
      try {
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
            if(mss == null) Util.prt("Single fetch returned 'nodata'");
            else {
              Util.prt("Single fetch returned "+mss.size()+" blks write to file="+filename);
              for (MiniSeed ms : mss) {
                Util.prt(ms.toString());
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
          Util.prt(" ** Instantiation error e="+ex);
        } catch (IllegalAccessException ex) {
          Util.prt(" ** IllegalAccept Exception e="+ex);
        } catch (IllegalArgumentException ex) {
          Util.prt(" ** IllegalArgument e="+ex);
        } catch (InvocationTargetException ex) {
          Util.prt(" ** InvocationTarget e="+ex);
          ex.printStackTrace();
        }
        catch(IOException e) {
          Util.prt(" **** IOException getData() on single fetch");
          Util.exit(1);
        }
      }
      catch(ClassNotFoundException e) {
        Util.prt(" ** Got a ClassNotFound for class="+classname+" e="+e);
        e.printStackTrace();
      }
      catch(NoSuchMethodException e) {
        Util.prt(" ** Got a NoSuchMethod for class="+classname+" e="+e);
        e.printStackTrace();
      }
      System.exit(0);
    }
    channels = new EdgeChannelServer("-empty","echnfetch");
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
    DBConnectionThread dbanss=null;
    state=3;
    MemoryChecker memchk = new MemoryChecker(120,null);
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
      dbanss = DBConnectionThread.getThread("anss");

      if(dbanss == null) {
        dbanss = new DBConnectionThread(fetchServer, "update", "anss",  true, false,"anss", Util.getOutput());

        if(!dbanss.waitForConnection())
          if(!dbanss.waitForConnection())
            if(!DBConnectionThread.waitForConnection("anss")) {
              EdgeThread.staticprt(" **** Could not connect to database "+fetchServer);
              Util.exit(1);
            }
      }
    }
    catch(InstantiationException e) {
      EdgeThread.staticprta(" **** Impossible Instantiation exception e="+e);
      Util.exit(0);
    }
    state=4;
    SendEvent.debugSMEEvent("FetcherStart", "Fetcher for "+gapType+" is starting", this);
    // Make a list of stations with this gap type
    TreeMap<String, String []> stations = new TreeMap<>();
    TreeMap<String, Fetcher> fetchers = new TreeMap<>();
    StringBuilder sb = new StringBuilder(1000);
    Iterator itr;
    boolean first=true;
    long now = System.currentTimeMillis();
    long lastStatus=System.currentTimeMillis()-1500000;
    boolean done=false;
    int loop=0;
    while(!EdgeChannelServer.isValid()) {
      try {sleep(1000);} catch(InterruptedException e) {}
      EdgeThread.staticprta("*** Waiting for EdgeChannelServer to be Valid");
    }

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
          EdgeThread.staticprta(Util.ascdate()+" * Main() : Read the requeststation and type files looking for new stations. "+loop);
          // Every hour reread the main configuration tables
            // This stuff is in NEICFetcherConfig now
          /*if(loop % 360 == 0) {
            state=8;
            if(gapType.contains("GP") || gapType.contains("QL")) {
              state=9;EdgeThread.staticprta(" * Main() : makeANSSBalerTables");BalerRequest.makeANSSBalerTables(fetchServer);}
            if(gapType.contains("IU") || gapType.contains("IS") || gapType.equals("C1") || gapType.equals("C2")) {
              state=10;EdgeThread.staticprt(" * Main() : makeANSSBalerTables");GSNQ680Request.makeANSSGSNTables(fetchServer);}
            if(gapType.contains("RT") || gapType.contains("RZ")) {
              state=10;EdgeThread.staticprt(" * Main() : makeReftekTables");RTPDArchiveRequest.makeReftekTables(fetchServer);}
          }*/

        }

        // Examine the
        boolean ok=false;
        state=11;
        while(!ok && !Fetcher.shuttingDown) {
          state=12;
          try {
            String s = "SELECT requeststation.id,requeststation,channelre,disablerequestuntil,"+
                    "requestclass FROM edge.requeststation,edge.requesttype WHERE requesttype.id=requestid"+
                    " AND fetchtype regexp '"+gapType+"'";
            if(localBaler) s += " AND requeststation REGEXP 'local'";
            else if(localCWB) s += " AND requeststation REGEXP 'CWB'";
            else if(seedname != null) s += " AND requeststation REGEXP '"+seedname+"'";
            s += " ORDER BY requeststation";
            Object [] keys =  fetchers.keySet().toArray();
            state=41;
            Util.prta("Start query cycle for request changes loop="+loop+" "+
                    DBConnectionThread.getThreadList()+" "+DBConnectionThread.getThread("edgeMAIN").toString());
            ResultSet rs;
            if(loop%100 == 99) rs = dbanss.executeQuery(s);
            else rs = dbconn.executeQuery(s);
            Util.prta("End query cycle for request changes s="+s);
            while(rs.next()) {
              if(now <  rs.getTimestamp("disablerequestuntil").getTime()) continue;
              if(rs.getString("channelre").indexOf("....") == 0 && !localBaler && !localCWB) continue;
              state=13;

              // If this station does not exist, create it
              if(stations.get(rs.getString("requeststation")) == null) {
                String [] arg = new String[5+args.length];
                arg[0]="-ID";
                arg[1]=""+rs.getInt("requeststation.id");
                arg[2]="-ID";
                arg[3]=""+rs.getInt("requeststation.id");
                arg[4+args.length]=rs.getString("requestclass");
                for(int i=4; i<4+args.length; i++) {
                  arg[i] = args[i-4];
                }
                String stat = rs.getString("requeststation");
                stations.put(stat, arg);
                String [] args2 = new String[arg.length-1];
                //for(int j=0; j<arg.length-1; j++) args2[j] = arg[j];  // copy command line to temp array
                System.arraycopy(arg, 0, args2, 0, arg.length-1);   // copy command line to temp array
                classname = arg[4+args.length];
                if(first) {
                  state=14;
                  EdgeThread.setMainLogname(classname+gapType.replaceAll("\\|", ""));
                  first=false;
                  // This is done in NEICFetcherConfig
                  /*if(gapType.contains("GP") && !localBaler && !localCWB) {state=15;BalerRequest.makeANSSBalerTables(fetchServer);}
                  if(gapType.contains("IU") || gapType.contains("IS")) {state=16; GSNQ680Request.makeANSSGSNTables(fetchServer);}
                  if(gapType.contains("RT") ) {state=16; RTPDArchiveRequest.makeReftekTables(fetchServer);}*/
                }   // This sets the default log name in edge thread (def=edgemom!)
                state=17;
                EdgeThread.staticprta("Main() start "+stat+" "+classname+" "+rs.getString("channelre")+" "+args2[0]+" "+args2[1]+" "+args2[2]+" "+args2[3]);

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
                    fetchers.put(stat, f);
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
                  EdgeThread.staticprt(" ** Main() Got a ClassNotFound for class="+classname+" e="+e);
                  e.printStackTrace();
                }
                catch(NoSuchMethodException e) {
                  EdgeThread.staticprt(" ** Main() Got a NoSuchMethod for class="+classname+" e="+e);
                  e.printStackTrace();
                }
              }
              // If this is an known station
              else {
                state=20;
                //EdgeThread.staticprta("**** known station ="+rs.getString("requeststation")+" keys.size="+keys.length+" stations.size="+stations.size());
                // Mark each known station out of the list of keys, if any keys remain, they are no longer configured
                for(int i=0; i<keys.length; i++) {
                  if(keys[i] != null) {
                    //EdgeThread.staticprt(((String) keys[i])+"| to "+rs.getString("requeststation")+"|"+ ((String) keys[i]).equals(rs.getString("requeststation")));
                    if(((String) keys[i]).equals(rs.getString("requeststation")))
                      keys[i] = null;
                  }
                }
              }
              state=21;
            }   // while(rs.next())
            state=22;
            rs.close();
            state=23;

            // Any stations remaining on the list of keys are not longer configured, stop their fetchers
            ok=true;
            for (Object key : keys) {
              if (key != null) {
                EdgeThread.staticprta("Main() need to drop " + key);
                Fetcher f = fetchers.get((String) key);
                EdgeThread.staticprt("Main() Stop fetcher="+f);
                f.terminate();
                fetchers.remove((String) key);
                stations.remove((String) key);
              }           
            }
            state=24;
          }
          catch(SQLException e) {
            state=25;
            Util.SQLErrorPrint(e," ** Main() Could not get information from request tables loop="+loop);
            e.printStackTrace();
          }
        }
      }   // IF time to check database for new stations (loop % 30 == 0)
      state=26;
      // Create all of the baler as separate threads
      if(sb.length() > 0) sb.delete(0, sb.length());
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
              EdgeThread.staticprta("Zombie found. Need to restart "+Arrays.toString(stations.get(f.getSeedname().substring(0,7).trim()))+" f="+f);

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
        EdgeThread.staticprta("Main() Fetcher has "+count+" threads still alive of "+fetchers.size()+" loop="+loop+" done="+done);
        lastStatus=System.currentTimeMillis();
        EdgeThread.staticprt(sb.toString());
        //DBConnectionThread.keepFresh();
      }
      state=30;
      loop++;
      Util.sleep(10000);
    }     // while !done
    state=31;
    SendEvent.debugSMEEvent("FetcherStop", "Fetcher for "+gapType+" is exiting. "+Fetcher.shuttingDown, this);
    EdgeThread.staticprta(Util.ascdate()+"Main()  Out of done loop.  Check for running stations.");
    Util.sleep(10000);
    itr = fetchers.values().iterator();
    int count=0;
    if(sb.length() > 0) sb.delete(0,sb.length());
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
    EdgeThread.staticprta("Main() FetcherMain.thread is exiting....");
    Util.sleep(2000);
    state=33;
  }
  /**
  * @param args the command line arguments
  */
  public static void main(String[] args) {
    Util.setModeGMT();
    Util.init("edge.prop");
    Util.setProcess("Fetcher");
    Util.setNoconsole(false);
    Util.setNoInteractive(true);
    if(args.length <=1) {
      Util.prt("Usage: Fetcher -1 -s NNSSSSSCCCLL -b yyyy/mm/dd hh:mm:ss -d dddd -c class -h gsn.stat.ip.adr [-p port (def=4003)][-logpath path][-f file] ");
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
      Util.prt("    -localbaler      Set to use a local baler at 20.99 for this fetchlist");
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
    for(int i=0; i<args.length; i++) {
      if(args[i].equals("-t")) {
        if(args[i+1].contains("GP") || args[i+1].startsWith("G")) {
          EdgeThread.setMainLogname("BalerRequest"+args[i+1].replaceAll("\\|",""));
        }
        else if(args[i+1].contains("IS") ) {
          EdgeThread.setMainLogname("GSNQ680Request"+args[i+1].replaceAll("\\|",""));
        }
        else if(args[i+1].contains("RT") ) {
          EdgeThread.setMainLogname("ReftekRequest"+args[i+1].replaceAll("\\|",""));
        }
        else if(args[i+1].contains("IC") ) {
          EdgeThread.setMainLogname("CWBRequest"+args[i+1].replaceAll("\\|",""));
        }
        else if(args[i+1].startsWith("C")) {
          EdgeThread.setMainLogname("CWBRequest"+args[i+1].replaceAll("\\|",""));
        }
        else EdgeThread.setMainLogname("Fetcher"+args[i+1].replaceAll("\\|",""));
        localGapType=args[i+1];
      }
      if(args[i].equals("-localcwb")) EdgeThread.setMainLogname("CWBRequest");
      if(args[i].equals("-b44dbg")) Baler44Request.bdbg=true;
      if(args[i].equals("-npermit")) {
        int npermit = Integer.parseInt(args[i+1]);
        Fetcher.setNpermit(npermit);
      }
    }
    FetcherMain maincode = new FetcherMain(args);
    int loop=0;
    int state9=0;
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
        EdgeThread.staticprta("Mail loop seems to be stuck in state="+state);
        SendEvent.debugSMEEvent("FetcherStuck9","Fetcher for "+localGapType+" is stuck state="+state, "Fetcher");
        Util.exit(102);
        state9=1;
      }
    }
    EdgeThread.staticprt("FetcherMain.main() has detected Shutdown state="+state);
    Util.exit(101);
  }
}
