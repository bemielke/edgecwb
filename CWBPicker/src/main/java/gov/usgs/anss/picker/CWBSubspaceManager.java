/*
 *  This software is in the public domain because it contains materials 
 *  that originally came from the United States Geological Survey, 
 *  an agency of the United States Department of Interior. For more 
 *  information, see the official USGS copyright policy at 
 *  http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.picker;

import gov.usgs.cwbquery.QuerySpanCollection;
import gov.usgs.alarm.SendEvent;
import gov.usgs.anss.db.DBConnectionThread;
import gov.usgs.anss.edge.IndexFile;
import gov.usgs.anss.edgethread.EdgeThread;
import gov.usgs.anss.edgethread.FakeThread;
import gov.usgs.anss.edgethread.ManagerInterface;
import gov.usgs.edge.config.*;
import gov.usgs.anss.util.Util;
//import gov.usgs.edge.config.Picker;
import java.io.*;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.sql.Statement;
import java.util.Iterator;
import java.util.TreeMap;
import java.util.Set;
//import java.util.Collections;
import subspacepreprocess.CWBSubspacePreprocess;

/**
 * This EdgeThreads looks at the Subspace Areas that are configured, computes whether any of the
 * channels in the subspace are on this node (actually all of them should be on this node!), and
 * configures the CWBSubspaceDetector. It also checks for changes in the status of the subspace has
 * been changed such that the user is shutting down the subspace, or is redesigning it :
 * <p>
 * 1) Initial Design - do not run this until the user sets it as Operational
 * <p>
 * 2) Operational - this CWBSSD should be running with current preprocessing 3) Review - The user is
 * changing the design, so run the old one until a new Preprocess is run
 * <p>
 * 3) Review - The area is under review and it should not be preprocessed until it is moved back to operational..
 * This protects the configuration while it is being changed routinely.
 * <p>
 * 4) Disabled - take this CWBSSD down permanently (at least until it is made operational again)
 * <p>
 * 5) Research/Offline - This SSD is not being run operationally but is being run in batch mode by a
 * researcher.
 * <p>
 * Normally the configPath is set to ./SSD which puts the configuration file for this Manager in
 * ./SSD/ssd.setup (if not changed by the user switch -config. From the configPath there will be a
 * subdirectory for each SSD area, and within the SSD area, there will be a directory for each
 * channel group. In the channel group directory will be all of the templates and other SSD
 * configuration stuff including the output configuration files from the preprocessor.
 * <p>
 * The basic control for doing PreProcessor runs is 1) The subspacearea.update &gt last time it was
 * checked, 2) The subspacearea.lastpreprocessor &lt subspacearead.updated. The subspace area status
 * must be operational.
 * <p>
 * Whenever a Preprocessor runs is made, the configuration file of CWBSubspaceProcessors is
 * calculated.
 * <p>
 * <PRE>
 * Switch    Argument    Description
 * -config    filename  The configuration file name to use def="./SSD/ssd.setup", if contains a slash, this becomes the configpath for templates as well
 * -logpath   path      This path will be added to the property "logfilepath" for log files, default="./SSDPL"\
 * -dbpick    DBURL     Use this for the PickDBServer property rather than one from the edge.prop or other prop files.
 * -nodb                If present, the configuration will just be read from the file and no configuration update from DB is done
 * -dbg                 If present more log file output.
 * -auth      authority Set the authcode
 * -agency    agency    Set the agency
 * -json       1;2&3;4   @see gov.usgs.picker.JsonDetectionSender for details (only needed if PickerManager is not running)
 * -noapply             DEBUG/DEV use only, run this thread with -noapply set in all configuration for testing
 * -topic    topic      Override the kafka topic to use when sending pickers from these pickers (def=null)
 * </PRE>
 *
 * @author davidketchum
 */
public final class CWBSubspaceManager extends EdgeThread {

  private static CWBSubspaceManager thisManager;
  //private static final TreeMap<String, SubspaceArea> areas = new TreeMap<>();
  //private static final TreeMap<String, ArrayList<SubspaceChannel>> ssdchans = new TreeMap<>();      // Index is subspace area
  //private static final TreeMap<String, ArrayList<SubspaceChannel>> ssdevents = new TreeMap<>();     // Index is subspace area
  //private static final TreeMap<String, ArrayList<SubspaceChannel>> ssdeventchans = new TreeMap<>(); // Index is subpspace area

  private static final ArrayList<String> chans = new ArrayList<>(100);
  public static JsonDetectionSender jsonPickSender; 
  private TreeMap<String, ManagerInterface> thr = new TreeMap<>();
  private final CWBSubspaceManager.ShutdownCWBSubspaceManager shutdown;
  private static String cwbip;            // CWB address where the templates are to be read
  private static int cwbPort;             // Port of QueryServer to get data for templates
  private static EdgeThread thisThread;
  private static MaintainDB maintainDBEdge;
  private String configFile = "ssd.setup";
  private String configPath = "./SSD/";
  private String authCode = "SSD-TEST";
  private String agency = "US-NEIC";
  private boolean dbg;
  private boolean noDB;
  private boolean noapply;
  private int loop = 0;
  //private static final byte [] wrbuf = new byte[100000];      // buffer for writefile to put long string
  private String logpath = "SSDPL/";
  private StringBuilder ssdsb = new StringBuilder(10000);
  private StringBuilder ssdstate = new StringBuilder(50);
  //private GlobalPickSender pickSender;
  private final StringBuilder sb = new StringBuilder(10);
  private final StringBuilder runsb = new StringBuilder(100);
  //public static DBConnectionThread getDBThread() {return maintainDBStatus.getDBThread();}

  public StringBuilder toStringBuilder() {
    return Util.clear(sb).append(" #ssdarea=").
            append(SubspaceArea.subspaceAreas == null?"0":SubspaceArea.subspaceAreas.size()).
            append(" #thr=").append(thr.size());
  }

  @Override
  public String toString() {
    return toStringBuilder().toString();
  }

  /**
   * set debug state
   *
   * @param t The new debug state
   */
  public void setDebug(boolean t) {
    dbg = t;
  }

  /**
   * terminate thread (causes an interrupt to be sure) you may not need the interrupt!
   */
  @Override
  public void terminate() {
    terminate = true;
    prta("SSDM: terminate called!");
    new RuntimeException(" SSDM terminated by ").printStackTrace(getPrintStream());
    interrupt();
  }

  /**
   * return the status string for this thread
   *
   * @return A string representing status for this thread
   */
  @Override
  public StringBuilder getStatusString() {
    if (statussb.length() > 0) {
      statussb.delete(0, statussb.length());
    }
    statussb.append("SSDM: #client=").append(thr.size()).append(" config=").append(configFile);
    statussb.append("\n");
    //Iterator<ManagerInterface> itr = thr.values().iterator();
    //while(itr.hasNext()) statussb.append("   ").append(itr.next().toString()).append("\n");
    return statussb;
  }

  /**
   * return the monitor string for Nagios
   *
   * @return A String representing the monitor key value pairs for this EdgeThread
   */
  @Override
  public StringBuilder getMonitorString() {
    if (monitorsb.length() > 0) {
      monitorsb.delete(0, monitorsb.length());
    }
    monitorsb.append("SSDMNthread=").append(thr.size()).append("\n");
    Iterator<ManagerInterface> itr = thr.values().iterator();
    long totalIn = 0;
    while (itr.hasNext()) {
      ManagerInterface obj = itr.next();
      totalIn += obj.getBytesIn();
    }
    monitorsb.append("SSDMBytesIn=").append(totalIn).append("\n");
    return monitorsb;
  }

  /**
   * return console output - this is fully integrated so it never returns anything
   *
   * @return "" since this cannot get output outside of the prt() system
   */
  @Override
  public StringBuilder getConsoleOutput() {
    return consolesb;
  }

  /**
   * creates an new instance of LISSCLient - which will try to stay connected to the host/port
   * source of data. This one gets its arguments from a command line
   *
   * @param argline The command line
   * @param tg The logging tag
   */
  public CWBSubspaceManager(String argline, String tg) {
    super(argline, tg);
    String[] args = argline.split("\\s");
    if (thisManager != null) {
      prta("***** restarting a  SSD manager????");
      new RuntimeException("*** restarting CWBSubspaceManager ").printStackTrace(getPrintStream());
    }
    thisManager = (CWBSubspaceManager) this;
    if (thisThread == null) {
      thisThread = (EdgeThread) this;
    }
    dbg = false;
    noDB = false;
    for (int i = 0; i < args.length; i++) {
      //prt(i+" arg="+args[i]);
      if (args[i].equals("-dbg")) {
        dbg = true;
      } else if (args[i].equals("-config")) {
        configFile = args[i + 1];
        if (configFile.contains(Util.FS)) {
          configPath = configFile.substring(0, configFile.lastIndexOf(Util.FS));
          configFile = configFile.substring(configFile.lastIndexOf(Util.FS));
        }
        i++;
      } else if (args[i].equalsIgnoreCase("-nodb")) {
        noDB = true;
      } else if (args[i].equalsIgnoreCase("-logpath")) {
        logpath = args[i + 1];
        i++;
        if (!logpath.endsWith("/")) {
          logpath += "/";
        }
      } else if (args[i].equals("-empty")) ; else if (args[i].substring(0, 1).equals(">")) {
        break;
      } else if (args[i].equals("-auth")) {
        authCode = args[i + 1];
        i++;
      } else if (args[i].equals("-agency")) {
        agency = args[i + 1];
        i++;
      } else if (args[i].equalsIgnoreCase("-json")) {
        jsonPickSender = new JsonDetectionSender("-json " + args[i + 1], this);
        if (PickerManager.getJSONSender() == null) {
          PickerManager.setJsonDetectionSender(jsonPickSender);
        }
        i++;
      } else if (args[i].equals("-dbpick")) {
        Util.setProperty("PickDBServer", args[i + 1]);
        i++;
      } else if (args[i].equals("-noapply")) {
        noapply=true;
      } else {
        prt("CWBSubspaceManager: unknown switch=" + args[i] + " ln=" + argline);
      }
    }
    if (Util.getProperty("DBServer") == null) {
      noDB = true;
    }
    if (Util.getProperty("DBServer").equals("")) {
      noDB = true;
    }
    prta("SSDM: created args=" + argline + " tag=" + tag);
    Util.chkFilePath(configPath + configFile);
    prta("SSDM: config=" + configPath + "/" + configFile + " dbg=" + dbg + " noDB=" + noDB 
            + " DBServer=" + Util.getProperty("DBServer"));

    shutdown = new CWBSubspaceManager.ShutdownCWBSubspaceManager();
    Runtime.getRuntime().addShutdownHook(shutdown);
    running = true;

    start();
  }

  /**
   * This thread periodically reads the database and makes the basic configuration file.
   */
  @Override
  public void run() {
    thisThread = this;
    StringBuilder filesb = new StringBuilder(10000);
    prta("SSDM: run() started");

    if (maintainDBEdge == null) {            // This is a first call, create the DBconnection and maintainit
      maintainDBEdge = new MaintainDB("DBServer", thisThread);
    }
    prta("SSDM: wait for DB to edge and status to start");
    while (maintainDBEdge.getDBThread() == null) {
      Util.sleep(1000);
    }
    while (!maintainDBEdge.getDBThread().isOK()) {
      Util.sleep(1000);
    }

    prta("SSDM: wait for QuerySpanCollection to be up");
    /*DBG: while (!QuerySpanCollection.isUp()) {
      try {
        sleep(100);
      } catch (InterruptedException e) {
      }
      if (terminate) {
        break;
      }
    }
DBG*/

    int n = 0;
    String s;
    prta("SSDM: wait 60 s for memory system to populate");
    if (!terminate) {
      try {
        sleep(5000);
      } catch (InterruptedException expected) {
      } // TODO: make 1 minute
    }
    boolean first = true;
    long startup = System.currentTimeMillis();
    while (!terminate) {
      //DBG: try {                     // Runtime exception catcher
      if (terminate || Util.isShuttingDown()) {
        break;
      }

      try {
        manageThreads();        // This starts, stops, restarts, discovers new threads etc
        if (terminate) {
          break;
        }
        makeConfig(first, filesb, startup);
        if (terminate) {
          break;
        }
        first = false;
        // Open file and read each line, process it through all the possibilities

      } catch (RuntimeException e) {
        prta(Util.clear(runsb).append(tag).append("SSDM:  RuntimeException in ").
                append(this.getClass().getName()).append(" e=").append(e.getMessage()));
        if (getPrintStream() != null) {
          e.printStackTrace(getPrintStream());
        } else {
          e.printStackTrace(getPrintStream());
        }
        if (e.getMessage() != null) {
          if (e.getMessage().contains("OutOfMemory")) {
            SendEvent.edgeEvent("OutOfMemory", "Out of Memory in " + Util.getProcess() + "/"
                 + this.getClass().getName() + " on " + IndexFile.getNode() + "/" + Util.getAccount(), this);
            throw e;
          }
        }
      }
    }     // while(!terminate);
    // The main loop has exited so the thread needs to exit
    prta("SSDM: start termination of Pickers term=" + terminate);
    Iterator<ManagerInterface> itr = thr.values().iterator();
    while (itr.hasNext()) {
      ManagerInterface t = itr.next();
      t.terminate();
    }
    prta("SSDM: ** CWBSubspaceManager terminated ");
    try {
      Runtime.getRuntime().removeShutdownHook(shutdown);
    } catch (Exception expected) {
    }
    running = false;            // Let all know we are not running
    terminate = false;          // sign that a terminate is no longer in progress
  }

  private void makeConfig(boolean first, StringBuilder filesb, long startup) {
    // Configuration thread creation and monitoring is now complete, start looping for config changes or thread deaths
    // Wait two minutes.  On startup the QueryMom needs some time to setup the QuerySpanCollection
    if (first) {
      prta(Util.clear(runsb).append("SSDM: Wait 10 s for everything to start before doing a configuration"));
      try {
        sleep(10000);
        if (terminate) {
          prta("SSDM: terminate on startup wait");
          return;
        }
      } catch (InterruptedException e) {
      }
      // read the disk config file
      try {
        Util.clear(filesb);
        Util.readFileToSB(configPath + configFile, filesb);
      } catch (IOException e) {
        prta(Util.clear(runsb).append("SSDM: initial read of configuration file failed e=").append(e));
      }
    }
    if (terminate) {
      return;
    }
    if(first) {
      prta("Initial config noDB="+noDB+" : \n"+filesb);
    }

    // Now check that the file has not changed
    String[] keys = new String[thr.size()];
    keys = thr.keySet().toArray(keys);
    boolean written = false;
    while (true) {      // Keep looping until file changes or a Thread goes down
      if (terminate) {
        break;
      }
      if (dbg) {
        prta(Util.clear(runsb).append("Do Pass= noDB=").append(noDB));
      }
      try {
        if (!noDB) {
          try {
            if (ssdsb.length() > 100) {
              Util.clear(ssdsb);
            }
            Util.clear(ssdstate);
            
            // Check for changes and do an preprocesses
            written = cwbSubspaceStringBuilder(ssdsb, ssdstate, configPath, first);
            prta(Util.clear(runsb).append("SSDM: build noDB=").append(noDB).
                    append(" len=").append(ssdsb.length()).append(" first=").append(first).
                    append(" written=").append(written));//+"\n"+fpmsb.toString());
          } catch (RuntimeException e) {
            prta(Util.clear(runsb).append("SSDM: got Runtime in fpmStringBuilder e=").append(e));
            e.printStackTrace(this.getPrintStream());
          }
        } else {     // Hand configuration, force the file and fpmsb to agree
          Util.readFileToSB(configFile, filesb);
          Util.clear(ssdsb);
          ssdsb.append(filesb);
        }
      } catch (IOException e) {
        prta(Util.clear(runsb).append("SSDM: got IO exception reading file ").append(configFile).
                append(" e=").append(e));
        e.printStackTrace(getPrintStream());
      }

      //prt("file=\n"+filesb+"|");
      //prt("fpmsb="+fpmsb.length()+"\n"+fpmsb+"|");
      // If the file has not changed check on down threads, if it has changed always to top
      try {
        if ((!Util.stringBuilderEqual(ssdsb, filesb) && ssdsb.length() > 100) && written) {
          Util.writeFileFromSB(configPath + configFile, ssdsb);// write out the file
          Util.clear(filesb).append(ssdsb);
          File f = new File(configPath + configFile.replaceAll(".cfg", ".state"));
          if (!f.exists()) { // state files are only written if the state file does not exist.
            Util.writeFileFromSB(configPath + configFile.replaceAll(".cfg", ".state"), ssdstate);
          }
          prta(Util.clear(runsb).append("SSDM: *** files have changed **** old=").append(filesb.length()).
                  append(" new=").append(ssdsb.length()).
                  append(" startup=").append(!(System.currentTimeMillis() - startup > 300000)));
          break;
        } else {
          if (dbg || loop++ % 10 == 1) {
            prta("SSDM: files are same json=" + PickerManager.getJSONSender());
          }
          // check to make sure all of our children threads are alive!
          boolean breakout = false;
          for (String key : keys) {
            if (!thr.get(key).isAlive() || !thr.get(key).isRunning()) {
              prta(Util.clear(runsb).append("SSDM: Found down thread ").
                      append(" alive=").append(thr.get(key).isAlive()).
                      append(" run=").append(thr.get(key).isRunning()).append(" ").
                      append(key).append(" ").append(thr.get(key)));
              SendEvent.debugEvent("FPMgrThrRes", "Unusual restart of " + key, this);
              breakout = true;
              break;
            }
          }
          if (breakout) {
            break;     // Some thread is down - break out to go to configuration loop
          }
          if (System.currentTimeMillis() - startup > 300000 && first) {
            Util.writeFileFromSB(configPath + configFile, ssdsb); // write out the file
            first = false;
          }
          try {
            sleep(120000);     //DEBUG: set to 120 seconds.
          } catch (InterruptedException e) {
          }
          if (terminate) {
            prta("SSDM: terminate on sleep");
            break;
          }
        }
      } catch (IOException e) {
        prta(Util.clear(runsb).append("SSDM: got IO exception writing file ").append(configFile).
                append(" e=").append(e));
        e.printStackTrace(getPrintStream());
      }
      if (System.currentTimeMillis() % 86400000L < 240000) {
        DBConnectionThread db = maintainDBEdge.getDBThread();
        if (db != null) {
          db.setLogPrintStream(getPrintStream());
        }
      }
    }
  }

   private void manageThreads() {

    BufferedReader in;
    // clear all of the checks in the list
    Iterator<ManagerInterface> itr = thr.values().iterator();
    while (itr.hasNext()) {
      itr.next().setCheck(false);
    }
    try {
      in = new BufferedReader(new FileReader(configPath + configFile));
      int n = 0;
      String s;
      prta(Util.clear(runsb).append("SSDMT: Read in configfile for SSD Areas=").append(configPath).append(configFile));
      while ((s = in.readLine()) != null) {
        try {
          if (s.length() < 1) {
            continue;
          }
          if (s.substring(0, 1).equals("%")) {
            break;       // DEBUG: done reading flag, what is this for?
          }
          if (!s.substring(0, 1).equals("#") && !s.substring(0, 1).equals("!")) {
            int comment = s.indexOf("#");
            if (comment >= 0) {
              s = s.substring(0, comment).trim();  // remove a later comment.
            }
            String[] tk = s.split("[:]");
            // lines look like subspaceArea:Class:args - this is a list of all know subspace detector classes
            // TODO : how to communicate channel, event or other changes need to be done
            if (tk.length == 3) {
              n++;
              String channel = tk[0];
              String pickClass = tk[1];
              String argline = tk[2].trim();
              String cmdLine = argline;
              if(cmdLine.indexOf(">>") > 0) cmdLine = cmdLine.substring(0,cmdLine.indexOf(">>") -1).trim();
              ManagerInterface t = thr.get(tk[0]);
              if (t == null) {
                if (pickClass.contains("CWBSubspaceDetector")) {
                  prta(Util.clear(runsb).append("SSDMT: * Start new ").append(pickClass).append(" ").append(channel).
                          append(" args=").append(argline));
                  //t = new CWBPicker(argline, tk[0], null);
                  String key = isAreaInKeyset(tk[0].substring(0,tk[0].length() -4));
                  if( key != null) {
                    t = thr.get(key);
                    if( t != null) {
                      prta(Util.clear(runsb).append("SSDMT: * key found with different hex time - terminate it old key=").
                              append(key).append(" new key=").append(tk[0]));
                      t.terminate();
                      thr.remove(key);    // Take the old one off the list
                      try {
                        sleep(250);
                      }
                      catch(InterruptedException expected) {
                      }
                    }
                  }
                  try {
                    t = new CWBSubspaceDetector(argline + " >> " + logpath + channel.replaceAll("-", "_"), tk[0]);
                    thr.put(tk[0], t);
                    t.setCheck(true);                  
                  }
                  catch(FileNotFoundException e) {
                    boolean found = false;
                    String lookingFor = tk[0].substring(tk[0].indexOf("-"));
                    prta(Util.clear(runsb).append("SSDMT: configuration file not found, cause a new preprocess ").append(lookingFor));
                    for(String area : preprocessAreas) {
                      if( area.equals(lookingFor)) found=true;
                    }
                    if(!found) {
                      prta("SSDMT: adding area");
                      preprocessAreas.add(lookingFor);
                    }
                  }

                } // Implement other picker classes here
                else {
                  prta(Util.clear(runsb).append("SSDMT: Unknown picker class=").append(pickClass));
                  continue;
                }
                // if its not the same, stop and start it
              } else if (!cmdLine.trim().equals(t.getArgs().trim()) || !t.isAlive() || !t.isRunning()) {
                t.setCheck(true);
                prta(Util.clear(runsb).append("SSDMT: * line changed or dead. alive=").append(t.isAlive()).
                        append(" running=").append(t.isRunning()).append("|").append(tk[0]).
                        append("|").append(tk[1]).append("|").append(tk[1]));
                prt("SSDMT:"+argline.trim() + "|\n" + t.getArgs() + "|\n");
                t.terminate();
                int loop2 = 0;
                while (t.isAlive()) {
                  try {
                    sleep(1000);
                  } catch (InterruptedException expected) {
                  }
                  if (terminate) {
                    break;
                  }
                  if (loop2++ >= 10) {
                    if (t.isAlive()) {
                      prta(Util.clear(runsb).append("SSDMT: *** thread did not die in 10 seconds. ").
                              append(tk[0]).append(" ").append(argline));
                    }
                    break;
                  }
                }
                t = new CWBSubspaceDetector(argline + " >> " + channel.replaceAll("-", "_"), tk[0]);
                thr.put(tk[0], t);
                t.setCheck(true);
              } else {
                t.setCheck(true);
              }
            } else {
              prta(Util.clear(runsb).append("SSDMT: Line is wrong format :").append(s));
            }
          }
        } catch (IOException e) {
          prta(Util.clear(runsb).append("SSDMT: *** error reading the configfile=").append(configFile).
                  append(" e=").append(e.getMessage()));
          e.printStackTrace(getPrintStream());     
        } catch (RuntimeException e) {
            prta(Util.clear(runsb).append("SSDMT: *** runtime error reading config file ").append(s));
          e.printStackTrace(getPrintStream());
        }
      }   // end of read config file lines

      prta(Util.clear(runsb).append("SSDMT: ").append(n).append(" lines read without comment"));
      in.close();

      itr = thr.values().iterator();
      while (itr.hasNext()) {
        ManagerInterface t = itr.next();
        if (!t.getCheck()) {
          // This line is no longer in the configuration, shutdown the Q330
          prta(Util.clear(runsb).append("SSDMT: * line must have disappeared stop it.").append(t.toString()).
                  append(" ").append(t.getArgs()));
          t.terminate();
          itr.remove();
        }
      }
    } catch (FileNotFoundException e) {
      prta(Util.clear(runsb).append("SSDMT: Could not find a config file! proably never created=").append(configFile).
            append(" ").append(e));        
    } catch (IOException e) {
      prta(Util.clear(runsb).append("SSDMT: error handling threads=").append(configFile).
              append(" e=").append(e.getMessage()));
      e.printStackTrace(getPrintStream());
    } catch (RuntimeException e) {
        prta(Util.clear(runsb).append("SSDMT: runtime error reading config file ").append(configFile));
      e.printStackTrace(getPrintStream());  
    }
  }
   /** check to see if an given area is in the key set excluding the time hex
    * 
    * @param keyTag
    * @return The full key tag with hex time from the thr list
    */
  private String isAreaInKeyset(String keyTag) {
    Set<String> keyset = thr.keySet();
   for(String key: keyset) {
     if(key.indexOf(keyTag) == 0) {
        return key;
     }
   }
   return null;
  }
  private long lastRun = 0;               // Last time the preprocess check cwbSubspaceSB was run
  private ArrayList<String> preprocessAreas = new ArrayList<>(10);    // Only attribute for reuse in cwbSubspaceSB
  private StringBuilder chanPath = new StringBuilder(30);
  /**
   * < PRE>
   * 1)  Make a list of areas that need to be preprocessed (subspacearea.updated > last run)
   * 2)  For each such area, do the preprocessor run if the DB indicates it has not been done since subspacearea.updated
   * 3)  Update the subspacearea.lastpreprocess = now() to step 2 will skip this the next time
   * 4)  Create the list of CWBSubpaceDetector threads that are operational (use by thread manager) in sb
   * TODOL: is there a danger of something changing the config that does not trigger a preprocessor?
   *
   *
   * @param sb This gets the list of threads for all SubspaceAreas so they can be started (operational only)
   * @param state Gets the contents of the state file, NSCL, start, stop times etc
   * @param cfgPath The path to the configuration, the subspace areas are subdirectories on this
   * path.
   * @param first If true, this is the first time this has been called
   * @return true if some area had preprocessor run. If false, then the DB is not setup or nothing
   * has changed.
   */
  private boolean cwbSubspaceStringBuilder(StringBuilder sb, StringBuilder state, String cfgPath, boolean first) {
    // If we do not have access to the pickers, then we should do nothing
    try {
      SubspaceArea.subspaceAreas = null;
      SubspaceArea.makeSubspaceAreas(maintainDBEdge.getDBThread().getNewStatement(false));
    } catch (SQLException e) {
      e.printStackTrace(getPrintStream());
      return false;
    }

    if (first) {     // only preprocess areas modified in the last day rather than all of them
      lastRun = System.currentTimeMillis() -  86400000L; 
    }
    // read in the template
    QuerySpanCollection qsc = QuerySpanCollection.getQuerySpanCollection();
    if (qsc != null) {
      qsc.getChannels(chans);
    }
    if (dbg) {
      prta(Util.clear(runsb).append("PM: qsc.getChannels() #=").append(chans.size()));
    }
    Subspace subspace = null;
    try {
      Statement stmtArea = maintainDBEdge.getDBThread().getNewStatement(true);

      // Create a Subspace object with the defaults (CWBIP, ports, picktable URL, etc)
      ResultSet rssub = stmtArea.executeQuery("SELECT * FROM subspace");
      if (rssub.next()) {
        subspace = new Subspace(rssub);
        cwbip = subspace.getSSDCWBIP();
        cwbPort = subspace.getSSDCWBPort();
      }

      // Create a list of areas that need to be preprocessed (subspacearea.updated > lastRun)
      long oldlastRun = lastRun;
      try (ResultSet rsarea = stmtArea.executeQuery(
              "SELECT subspacearea FROM edge.subspacearea WHERE updated > '"
              + Util.ascdatetime2(lastRun) + "' ORDER BY subspacearea;")) {
        lastRun = System.currentTimeMillis() - 30000;
        while (rsarea.next()) {        // There are no new areas, leave the configuration alone
          preprocessAreas.add(rsarea.getString("subspacearea"));
        }
        rsarea.close();

        // Now look for the configuration directory for each operational area, and if it does not exist, mark it for preprocessing
        // If there are no changes, then do not change the configuration, just return
        if (preprocessAreas.isEmpty() && !first) {
          prta(Util.clear(runsb).append("* No changes to any area needing a preprocessor run ").
                  append(Util.ascdatetime2(oldlastRun)));
          return false;      // No changes have been made
        }
      }

      // We have to rebuild the configuration, so read in all of the DB information
      SubspaceChannel.subspaceChannels = null;
      SubspaceChannel.makeSubspaceChannels(stmtArea);

      // Run the preprocessor on the changed areas
      for (String name : preprocessAreas) {
        SubspaceArea area = null;
        try {
          for (int i = 0; i < SubspaceArea.subspaceAreas.size(); i++) {
            if (name.equals(SubspaceArea.subspaceAreas.get(i).getSubspaceArea())) {
              area = SubspaceArea.subspaceAreas.get(i);
              break;
            }
          }
          if (area != null) {
            if (area.getStatusString().equalsIgnoreCase("Operational") && // DEBUG: !operationalThe area is operational
                    area.getUpdated() > area.getLastPreprocess()) {     // Its been updated recently
              for (SubspaceChannel ssch : SubspaceChannel.subspaceChannels) {
                if (ssch.getAreaID() != area.getID()) {      // TODO: should there be a disable on the channels?
                  continue;
                }

                // Do the preprocessor pass - note the state is not updated as this code should pick up where
                // it left off.  If there is no state file, it might be used.
                String chan = ssch.getSubspaceChannel();    // something like ./SSD/Montana2017/GSMT01_HH./
                String preprocessorPath = cfgPath + area.getSubspaceArea().replaceAll(" ", "_")
                        + Util.FS + chan.replaceAll(" ", "_").trim();
                String currentFile = preprocessorPath + Util.FS + chan.replaceAll(" ", "_").trim() + ".cfg";
                Util.chkFilePath(currentFile);
                SubspaceArea.createPreprocessorConfigSB(stmtArea, area, ssch, true,
                        preprocessorPath, cwbip, cwbPort, subspace, sb, state);

                Util.writeFileFromSB(currentFile, sb);
                Util.clear(sb);
                prta("\n******************* starting Preprocess for " + currentFile + "cwbip="+cwbip+" *************************");
                CWBSubspacePreprocess preproc = new CWBSubspacePreprocess(currentFile, true, this);
                preproc.cleanUpFiles();
                preproc.setConfiguration(currentFile);
                preproc.buildTemplates();
              }
              // update the lastPreprocess field so this does not happen again.
              try {
                stmtArea.executeUpdate("UPDATE edge.subspacearea SET lastpreprocess=now() WHERE id=" + area.getID());
              } catch (SQLException e) {
                e.printStackTrace(getPrintStream());
              }
            }
          }
        } catch (IOException e) {
          e.printStackTrace(getPrintStream());
        } catch (RuntimeException e) {
          prta("Runtime exception building files using SubspacePreprocessor");
          e.printStackTrace(getPrintStream());
        }
      }
      preprocessAreas.clear();

      // For each of the enabled and operational areas, create one line to start a CWBSubspaceDetector on each channel
      prta("Start creation of config for "+SubspaceArea.subspaceAreas.size());
      for (SubspaceArea area : SubspaceArea.subspaceAreas) {
        if ((area.getStatusString().equalsIgnoreCase("Operational")  || 
                area.getStatusString().equalsIgnoreCase("Review")) &&
                (area.getEndTime() < 200000 || area.getEndTime() > System.currentTimeMillis())) {

          // Find all SSD channels in this area, and create one line of output for each one
          for (SubspaceChannel sschan : SubspaceChannel.subspaceChannels) {

            if (sschan.getAreaID() == area.getID()) {
              // Get the base of the Tag and the hex time based on the configuration
              String keyTag = area.getSubspaceArea()+"-"+sschan.getNSCL1().replaceAll(" ", "_");
              String hexTime = Util.toHex(area.getUpdated()/1000 % Short.MAX_VALUE).substring(2);
              // If an area is in "review" then we should not change the time part to keep from taking the thread down
              // before it is made opearational
              CWBSubspaceDetector det = null;
              if(area.getStatusString().equals("Review")) {
                String key = isAreaInKeyset(keyTag);
                if( key != null) {
                    hexTime = key.substring(key.length() - 4);  // Override time hex to be the old one
                    det = (CWBSubspaceDetector) thr.get(key);
                }
              }
              sb.append(keyTag).append(hexTime).
                      append(":").append("gov.usgs.anss.picker.CWBSubspaceDetector:");
              if(det != null) {
                sb.append(det.getArgs());       // If review use the argline currently running
              }
              else {                            // Actuall create a new args portion
                sb.append("-c ").
                        append(sschan.getNSCL1().replaceAll(" ","_"));
                // If it is multichannel, add them on.
                if (sschan.getNSCL2() != null) {
                  if (sschan.getNSCL2().length() > 8) {
                    sb.append(",").append(sschan.getNSCL2().replaceAll(" ", "_"));
                    sb.append(",").append(sschan.getNSCL3().replaceAll(" ", "_"));
                  }
                }
                if(noapply) {
                  sb.append(" -noapply");
                }     
                Util.clear(chanPath).append(cfgPath).
                        append(area.getSubspaceArea().replaceAll(" ", "_")).append(Util.FS).
                        append(sschan.getSubspaceChannel().replaceAll(" ", "_")).append(Util.FS);
                sb.append(" -path ").append(chanPath).append(" -config ").append(chanPath).
                        append(sschan.getSubspaceChannel().substring(0,7).replaceAll(" ", "_")).append(".cfg").
                        append(" -auth ").append(authCode).append(" -agency ").append(agency);
              }
              // Add logging
              sb.append(" >> ").append(logpath).append(area.getSubspaceArea().replaceAll(" ","_")).append("-").
                      append(sschan.getSubspaceChannel().replaceAll(" ", "_")).append("\n");
            }
          }
        } else {      // This is not an operational area, just output that it exists and its status.
          sb.append("#").append(area.getSubspaceArea()).append("-").
                append(Util.toHex(area.getUpdated()/1000 % Short.MAX_VALUE).substring(2)).
                append(" status=").append(area.getStatusString()).append("|").append(Util.ascdate(area.getEndTime())).
                append(!area.getStatusString().equalsIgnoreCase("Operational")  && 
                !area.getStatusString().equalsIgnoreCase("Review")?
                " - Not Operational or review ":"").
                append( area.getEndTime() < System.currentTimeMillis() -40000 && area.getEndTime() > 200000?
                " - End time is past ":"").
                append("\n");
        }
      }
    } catch (SQLException e) {
      Util.SQLErrorPrint(e, "makeSubspaceAreas() on table SQL failed");
    }
    prta("End creation of config for "+SubspaceArea.subspaceAreas.size()+" sb.len="+sb.length());

    return true;
  }

  /**
   * this internal class gets registered with the Runtime shutdown system and is invoked when the
   * system is shutting down. This must cause the thread to exit
   */
  class ShutdownCWBSubspaceManager extends Thread {

    /**
     * default constructor does nothing the shutdown hook starts the run() thread
     */
    public ShutdownCWBSubspaceManager() {
      gov.usgs.anss.util.Util.prta("new ThreadShutdown " + getName() + " "
              + getClass().getSimpleName());
    }

    // This is called by the RunTime.shutdown at any termination 
    @Override
    public void run() {
      System.err.println("SSDM: CWBSSDManager Shutdown() started...");
      terminate();          // send terminate to main thread and cause interrupt
      int loop = 0;
      while (running) {
        // Do stuff to aid the shutdown
        try {
          sleep(1000);
        } catch (InterruptedException e) {
        }
        loop++;
      }
      System.err.println("SSDM: Shutdown() of CWBSSDManager is complete.");
    }
  }

  public static void main(String[] args) {
    Util.init("edge.prop");
    Util.setModeGMT();
    String pickDB = "localhost/3306:edge:mysql:edge";
    Util.setProperty("PickDBServer", pickDB);
    CWBSubspaceManager ssdmgr = new CWBSubspaceManager(/*-noapply */"-empty >>ssdmgr", "SSDMGR");
    Util.sleep(1000);
    FakeThread fake = new FakeThread("-empty >>fake", "FAKE");
    for (;;) {
      Util.sleep(5000);
      if (!ssdmgr.isAlive()) {
        break;
      }
    }
    Util.prt("Done");
    System.exit(1);
  }
}
