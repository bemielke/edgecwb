/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.edgemom;

import gov.usgs.anss.edgethread.EdgeThread;
import gov.usgs.anss.edgethread.ManagerInterface;
import gov.usgs.anss.util.Util;
import gov.usgs.edge.config.Flags;
import gov.usgs.edge.config.Sendto;
import java.io.*;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Iterator;

/**
 * This thread in invoked by the OutputInfrastructure thread to build the
 * OutputInfrastructure configuration file. Since it is invoked from the OI
 * thread it will get the OI arguments as well as args intended for this thread.
 * This thread uses the sendto and flags structure from EdgeChannelServer as the
 * basis for creating the config file. If the -nodb flag is on, This thread does
 * nothing.
 *
 * <pre>
 * switch   value    Description
 * -nodb            If present, no database is available and the config file is manually configured
 * -config filename The path and file for the configuration file
 * -dbg             If present, more logging
 * 
 * Sendto fields and meanings:
 * filesize  nnn    SIze of a ring file in mB
 * path      /data2 Where to put any physical ring files
 * update    nnn    Milliseconds between updates of the last block written.
 * roles     regex  Regexp of roles than have this kind of data ie. cwb1|cwb2|cwb3
 * Create    regex  The channel regexp which automatically get this sendto on createsion eg ^US.....[VLBH][HN][ZNE12].*
 * Class     name   If empty, a RingFile is created, most common other would be RingServerSeedLink
 * args      arg    Depends on output class :
 * 
 * For RingFIles :
 * -override i#1,nn:i#2,n2 Change the size from the default to this size for these listed instances, sizes in mB
 * For RingServerSeedeLink (see this class for more documentation) :
 * -h     ipadr    The address of the ringserver datalink  (def=localhost)
 * -p     port     The datalink port number on the ringserver (def=18002)
 * -allowlog       If set LOG records will go out (normally not a good idea)
 * -qsize nnnn     The number of miniseed blocks to buffer through the in memory queue (512 bytes each, def=1000)
 * -file  filenam  The filename of the file to use for backup buffering (def=ringserver.queued)
 * -maxdisk nnn    Maximum amount of disk space to use in mB (def=1000 mB).
 * -allowrestricted true  If argument is 'true', then allow restricted channels
 * -allowraw       This must be set if this RSSL is being used to send raw data to a QueryMom DLTQS.
 * -xinst          A regular expression of instances that are blocked output blocked
 * -dbg            Set more voluminous log output
 * -dbgchan        Channel tag to match with contains

 * </pre> * @author davidketchum ketchum at usgs.gov
 */
public final class OutputInfrastructureManager extends EdgeThread {

  static byte[] readbuf = new byte[2000];
  private final ShutdownOIManager shutdown;
  private final String instance;
  //private static DBConnectionThread dbconn;
  private String configFile;
  private boolean dbg;
  private boolean noDB;
  private final StringBuilder oisb = new StringBuilder(10000);
  private final StringBuilder filesb = new StringBuilder(10000);
  private final String nodeUser;
  //private int cpuID;
  private String[] roles;
  private static final StringBuilder runsb = new StringBuilder(100);
  private boolean hasChanged;

  /**
   * return the changed config file if it has changed, else return null
   *
   * @return null if no change, else the configuration file
   */
  public StringBuilder hasChanged() {
    while (filesb.length() < 20) {
      try {
        prta("Wait for filesb len=" + filesb.length() + " configfile=" + configFile);
        sleep(1000);
      } catch (InterruptedException expected) {
      }
    }
    StringBuilder sb = (hasChanged ? filesb : null);
    hasChanged = false;
    return sb;
  }

  public String getConfigFile() {
    return configFile;
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
   * terminate thread (causes an interrupt to be sure) you may not need the
   * interrupt!
   */
  @Override
  public void terminate() {
    terminate = true;
    prta("OIM: interrupted called!");
    interrupt();
  }

  @Override
  public String toString() {
    return configFile;
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
    statussb.append("OIM: ").append(" config=").append(configFile).append(" roles: ");
    for (String role : roles) {
      statussb.append(role).append(" ");
    }
    statussb.append("\n");
    return statussb;
  }

  /**
   * return the monitor string for Nagios
   *
   * @return A String representing the monitor key value pairs for this
   * EdgeThread
   */
  @Override
  public StringBuilder getMonitorString() {
    Util.clear(monitorsb).append("OIMRoles=");
    if (roles != null) {
      for (String role : roles) {
        monitorsb.append(role).append(" ");
      }
    }
    monitorsb.append("\n");
    return monitorsb;
  }

  /**
   * return console output - this is fully integrated so it never returns
   * anything
   *
   * @return "" since this cannot get output outside of the prt() system
   */
  @Override
  public StringBuilder getConsoleOutput() {
    return consolesb;
  }

  /**
   * creates an new instance of LISSCLient - which will try to stay connected to
   * the host/port source of data. This one gets its arguments from a command
   * line
   *
   * @param argline The command line
   * @param tg The logging tag
   */
  public OutputInfrastructureManager(String argline, String tg) {
    super(argline, tg);
//initThread(argline,tg);
    String[] args = argline.split("\\s");
    dbg = false;
    instance=EdgeMom.getInstance();
    configFile = "config/oi_" + instance + ".setup";
    noDB = false;
    for (int i = 0; i < args.length; i++) {
      if (args[i].trim().length() <= 0) {
        continue;
      }
      if (args[i].equals("-dbg")) {
        dbg = true;
      } else if (args[i].equals("-config")) {
        configFile = args[i + 1];
        i++;
      } else if (args[i].equalsIgnoreCase("-nodb")) {
        noDB = true;
      } else if (args[i].substring(0, 1).equals(">")) {
        break;
      } else {
        prt("OIManager: unknown switch=" + args[i] + " ln=" + argline);
      }
    }
    if (Util.getProperty("DBServer") == null) {
      noDB = true;
    } else if (Util.getProperty("DBServer").equalsIgnoreCase("NoDB")) {
      noDB = true;
    } else if (Util.getProperty("DBServer").equals("")) {
      noDB = true;
    }
    prt("OIM: created args=" + argline + " tag=" + tag);
    prt("OIM: config=" + configFile + " dbg=" + dbg + " no DB=" + noDB + " DBServer=" + Util.getProperty("DBServer"));
    nodeUser = (EdgeMom.getInstance().equals("^")) ? "" : EdgeMom.getInstance();
    shutdown = new ShutdownOIManager();
    Runtime.getRuntime().addShutdownHook(shutdown);
    start();
  }

  /**
   * This Thread keeps a socket open to the host and port, reads in any
   * information on that socket, keeps a list of unique StatusInfo, and updates
   * that list when new data for the unique key comes in.
   */
  @Override
  public void run() {
    running = true;
    Iterator<ManagerInterface> itr;
    BufferedReader in;
    int n;
    int loop = 0;
    String s;
    boolean first = true;
    try {
      sleep(5000);
    } catch (InterruptedException e) {
    }   // Let all the configurations run first
    prta(Util.clear(runsb).append("Waiting for valid EdgeChannelServer"));
    while (!EdgeChannelServer.isValid()) {
      try {
        sleep(100);
      } catch (InterruptedException e) {
      }
    }
    ArrayList<Sendto> sendtos = EdgeChannelServer.getSendtos();
    try {
      sleep(100);
    } catch (InterruptedException e) {
    }   // Do not cycle too fast
    if (sendtos != null) {
      if (sendtos.size() < 64) {
        sendtos = null;
      }
    }
    prta(Util.clear(runsb).append("OI: EdgeChannelServer valid after ").
            append(" sendtos=").append(sendtos).append(" valid=").append(EdgeChannelServer.isValid()));
    //readFile(configFile, filesb);
    hasChanged = true;
    while (true) {
      try {                   // Runtime exception catcher
        if (terminate || Util.isShuttingDown()) {
          break;
        }
        while (true) {
          if (terminate) {
            break;
          }
          roles = Util.getRoles(null);
          if (roles.length == 1 && roles[0].contains("evpn")) // when debugging on eras it has to be my test system
          {
            roles[0] = "gldketchum3";          //prt("Do Pass= noDB="+noDB+" dbconn="+dbconn);
          }
          if (!noDB) {
            try {
              Util.clear(oisb);
              oiStringBuilder(oisb, first);
              if (dbg) {
                prta(Util.clear(runsb).append("iosb= noDB=").append(noDB).
                        append("\n").append(oisb.toString()));
              }
              first = false;
              // handle the OI config file
            } catch (SQLException e) {
              prta("OIM: got SQLException in oiStringBuilder e=" + e);
              e.printStackTrace(this.getPrintStream());
              try {
                sleep(120000);
              } catch (InterruptedException e2) {
              }
              break;
            } catch (RuntimeException e) {
              prta("OIM: got Runtime in oiStringBuilder e=" + e);
              e.printStackTrace(this.getPrintStream());
              try {
                sleep(120000);
              } catch (InterruptedException expected) {
              }
              break;
            }
          } else {
            try {
              Util.readFileToSB(configFile, filesb);    // Fake it to never mess with the file
              if (!oisb.toString().equals(filesb.toString()) && oisb.length() > 20) {
                hasChanged = true;
              }
              oisb.delete(0, oisb.length());
              oisb.append(filesb);
            } catch (IOException e) {
              prta("Could not read in " + configFile + " e=" + e);
              e.printStackTrace(getPrintStream());
            }
          }

          // If the file has not changed check on down threads, if it has changed always to top
          if (!oisb.toString().equals(filesb.toString()) && oisb.length() > 20) {
            try {
              Util.writeFileFromSB(configFile, oisb);
            } // write out the file
            catch (IOException e) {
              prta("Could not read in " + configFile + " e=" + e);
              e.printStackTrace(getPrintStream());
            }
            prta(Util.clear(runsb).append("OIM: *** files have changed ****"));
            prta(Util.clear(runsb).append("file=").append(filesb.length()).append("\n").append(filesb).append("|"));
            prta(Util.clear(runsb).append("oisb=").append(oisb.length()).append("\n").append(oisb).append("|"));
            Util.clear(filesb).append(oisb);      // put copy of write in filesb to keep for comparisons
            hasChanged = true;
          } else {
            if (dbg || loop++ % 10 == 1) {
              prta("OIM: files are same");
            }
            // check to make sure all of our children threads are alive!
            if (dbg) {
              prta(Util.clear(runsb).append("file=").append(filesb.length()).append("\n").append(filesb).append("|"));
              prta(Util.clear(runsb).append("oisb=").append(oisb.length()).append("\n").append(oisb).append("|"));
            }
            //thr.get(keys[2]).terminate();thr.get(keys[6]).terminate();   //DEBUG: force some down!
          }
          try {
            sleep(120000);
          } catch (InterruptedException expected) {
          }
          /*if(System.currentTimeMillis() % 86400000L < 240000) {
            dbconn.setLogPrintStream(getPrintStream());
          }*/
        }
      } catch (RuntimeException e) {
        prta("Got a runtime exception making the OI config file!");
        e.printStackTrace(getPrintStream());
      }
      // The main loop has exited so the thread needs to exit
    }     // while(true)
    prta("OIM: ** OIManager terminated ");
    try {
      Runtime.getRuntime().removeShutdownHook(shutdown);
    } catch (Exception expected) {
    }
    running = false;            // Let all know we are not running
    terminate = false;          // sign that a terminate is no longer in progress
  }

  /**
   * this internal class gets registered with the Runtime shutdown system and is
   * invoked when the system is shutting down. This must cause the thread to
   * exit
   */
  class ShutdownOIManager extends Thread {

    /**
     * default constructor does nothing the shutdown hook starts the run()
     * thread
     */
    public ShutdownOIManager() {
      gov.usgs.anss.util.Util.prta("new ThreadShutdown " + getName() + " " + getClass().getSimpleName());
    }

    // This is called by the RunTime.shutdown at any termination 
    @Override
    public void run() {
      System.err.println("OIM: OIManager Shutdown() started...");
      terminate();          // send terminate to main thread and cause interrupt
      int loop = 0;
      while (running) {
        // Do stuff to aid the shutdown
        try {
          sleep(1000);
        } catch (InterruptedException expected) {
        }
        loop++;
      }
      System.err.println("OIM: Shutdown() of OIManager is complete.");
    }
  }

  /**
   * given a edge or gaux make a string
   *
   * @param cpuname And edge node name normally
   * @param statussb A string builder to use with the output
   */
  static int ncalls = 0;
  private StringBuilder filetmp = new StringBuilder(20);

  public void oiStringBuilder(StringBuilder sb, boolean first) throws SQLException {
    //cpuname="gldketchum3";    // DEBUG: force my workstation
    Util.clear(sb);//    Boolean onlyone [] = new Boolean[200];

    try {
      ArrayList<Sendto> sendtos = null;
      ArrayList<Flags> flags = null;
      int loops = 0;
      while (sendtos == null || !EdgeChannelServer.isValid()) {
        sendtos = EdgeChannelServer.getSendtos();
        if (sendtos == null) {
          loops++;
          if (loops % 600 == 0) {
            prta("OI:  waiting for valid sendtos from EdgeChannelServer");
          }
        }
        flags = EdgeChannelServer.getFlags();
        try {
          sleep(100);
        } catch (InterruptedException expected) {
        }   // Do not cycle too fast
        if (sendtos != null) {
          if (sendtos.size() < 64) {
            sendtos = null;
          }
        }
      }

      //String [] roles = Util.getRoles(null);
      if (dbg) {
        for (int i = 0; i < roles.length; i++) {
          prta(Util.clear(runsb).append(i).append(" Roles : ").append(roles[i]).append(" #sendtos=").append(sendtos.size()));
        }
      }
      for (int i = 0; i < 64; i++) {        // For each possible Sendto
        //prt(i+" sendto "+sendtos.get(i));
        if (sendtos.get(i) != null) {
          if (sendtos.get(i).getSendto().substring(0, 1).equals("_")) {
            continue;
          }
          if (sendtos.get(i).getNodes().equals("")) {
            continue;      // Not enabled on any nodes!
          }
          boolean ok = false;
          String matchRole = "";
          for (String role1 : roles) {
            try {
              if (sendtos.get(i).matchNode(role1)) {
                ok = true;
                matchRole = role1;
                break;
              }
            } catch (RuntimeException e) {
              e.printStackTrace(getPrintStream());
            }
          }
          if (!ok) {
            if (dbg) {
              prt(Util.clear(runsb).append("OI: no ring for ").append(sendtos.get(i)).append(" not on role=").append(matchRole));
            }
            continue;
          }
          long disableMask = 0;
          if (flags != null) {
            for (int j = 0; j < 64; j++) {
              if (flags.get(j) != null) {
                if (flags.get(j).getFlags().contains(sendtos.get(i).getSendto())
                        && flags.get(j).getFlags().contains("Disable")) {
                  disableMask = flags.get(j).getMask();
                }
              }
            }
          }
          if (dbg) {
            prta(Util.clear(runsb).append("sendto ").append(sendtos.get(i).getSendto()).
                    append(" sz=").append(sendtos.get(i).getFilesize()).
                    append(" args=").append(sendtos.get(i).getArgs()).
                    append(" cl=").append(sendtos.get(i).getClass()));
          }
          if (sendtos.get(i).getFilesize() > 0) {  // Is it ring file based?
            // Figure out if this is > user vdl (add vdl # to file name)

            if (sendtos.get(i).getArgs().contains("-rrp")) {
              Util.clear(filetmp).append(sendtos.get(i).getSendto()).append(nodeUser);
              Util.stringBuilderReplaceAll(filetmp, ' ', '_');
              String file = (sendtos.get(i).getSendto() + nodeUser).replace(" ", "_");
              sb.append(filetmp).append(":RRPRingFile:-p ").append(sendtos.get(i).getFilepath()).
                      append(" -f ").append(filetmp).
                      append(" -u ").append(sendtos.get(i).getUpdateMS() <= 0 ? 400 : sendtos.get(i).getUpdateMS()).
                      append(" -s ").append(sendtos.get(i).getFilesize()).
                      append(" -m ").append(1L << (sendtos.get(i).getID() - 1)).
                      append(" -dm ").append(disableMask).
                      append(" -allowrestricted ").append(sendtos.get(i).allowRestricted());
              if (sendtos.get(i).getArgs().trim().length() > 0) {
                sb.append(" -args ").append(sendtos.get(i).getArgs()).append("\n");
              } else {
                sb.append("\n");
              }
              /*RRPRingFile rf = new RRPRingFile(sendtos.get(i).getFilepath(),
                (sendtos.get(i).getSendto()+nodeUser).replace(" ","_"),
                sendtos.get(i).getUpdateMS() <= 0 ? 400 : sendtos.get(i).getUpdateMS(),
                sendtos.get(i).getFilesize(),
                (1L << (sendtos.get(i).getID()-1)), disableMask, sendtos.get(i).allowRestricted(), 
                      sendtos.get(i).getArgs(),this);              
              rf.setDebug(dbg);
              addOutputer(rf);                // Add it to the list*/
            } else {
              Util.clear(filetmp).append(sendtos.get(i).getSendto()).append(nodeUser);
              Util.stringBuilderReplaceAll(filetmp, ' ', '_');
              int filesize = sendtos.get(i).getFilesize();
              if(sendtos.get(i).getArgs().length() > 0) {
                try {
                  String [] args = sendtos.get(i).getArgs().split("\\s");
                  for(int j=0; j<args.length; j++) {
                    if(args[j].equalsIgnoreCase("-override")) {
                      String [] parts = args[j+1].split(":");
                      for(int jj=0; jj<parts.length; jj++) {
                        if(parts[jj].contains(instance)) {
                          String [] sizes = parts[jj].split(",");
                          int overrideSize = Integer.parseInt(sizes[1].trim());
                          prta("OIM: Filesize override to "+overrideSize+" from "+filesize+" for "+sendtos.get(i)+" on "+instance);
                          filesize=overrideSize;
                        }
                      }
                    }
                  }
                }
                catch(RuntimeException e) {
                  prta("OIM: error parsing "+sendtos.get(i).getSendto()+" args="+sendtos.get(i).getArgs()+" e="+e);
                  e.printStackTrace(getPrintStream());
                }
              }
              sb.append(filetmp).append(":Ringfile:-p ").append(sendtos.get(i).getFilepath()).
                      append(" -f ").append(filetmp).
                      append(" -u ").append(sendtos.get(i).getUpdateMS() <= 0 ? 400 : sendtos.get(i).getUpdateMS()).
                      append(" -s ").append(filesize).
                      append(" -m ").append(1L << (sendtos.get(i).getID() - 1)).
                      append(" -dm ").append(disableMask).
                      append(" -allowrestricted ").append(sendtos.get(i).allowRestricted()).
                      append(" -args ").append(sendtos.get(i).getArgs()).append("\n");
              /*RingFile rf = new RingFile(sendtos.get(i).getFilepath(),
                (sendtos.get(i).getSendto()+nodeUser).replace(" ","_"),
                sendtos.get(i).getUpdateMS() <= 0 ? 400 : sendtos.get(i).getUpdateMS(),
                sendtos.get(i).getFilesize(),
                (1L << (sendtos.get(i).getID()-1)), disableMask, sendtos.get(i).allowRestricted(), 
                      sendtos.get(i).getArgs(),this);
              rf.setDebug(dbg);
              addOutputer(rf);                // Add it to the list*/
            }

          } else if (sendtos.get(i).getOutputerClassName().equalsIgnoreCase("RingServerSeedLink")) {
            // This is where non-RingFile outputers would have to instantiate
            Util.clear(filetmp).append(sendtos.get(i).getSendto()).append(nodeUser);
            Util.stringBuilderReplaceAll(filetmp, ' ', '_');
            sb.append(sendtos.get(i).getSendto()).append(":RingServerSeedLink:-allowrestricted ").append(sendtos.get(i).allowRestricted()).
                    append(" -m ").append(1L << (sendtos.get(i).getID() - 1)).
                    append(" -dm ").append(disableMask).
                    append(" ").append(sendtos.get(i).getArgs()).append("\n");
            /*RingServerSeedLink rssl = new RingServerSeedLink(sendtos.get(i),"RSSL:", this);
            addOutputer(rssl);*/
          } else {
            prta(Util.clear(runsb).append("OI: Unexpected non-ring instantiation class=").
                    append(sendtos.get(i).getSendto()).append(" ").append(sendtos.get(i).getOutputerClassName()));
          }
        }
      }
    } catch (RuntimeException e) {
      prta("Got RuntimeException in oiStringBuilder() e=" + e);
      e.printStackTrace(getPrintStream());
      throw e;
    }
    if (sb.length() < 20) {
      sb.append("# there are no OutputInfrastructure configuration on this instance\n");
      prta("OI: ** there is nothing configured on this node!");
    }
  }
}
