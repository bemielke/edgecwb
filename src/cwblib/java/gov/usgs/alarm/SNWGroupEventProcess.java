/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.alarm;

import gov.usgs.anss.channelstatus.ChannelStatus;
import gov.usgs.anss.db.DBConnectionThread;
import gov.usgs.anss.db.JDBConnection;
import gov.usgs.anss.edgethread.EdgeThread;
import gov.usgs.anss.net.StatusSocketReader;
import gov.usgs.anss.util.Util;
import gov.usgs.anss.util.AnssPorts;
import gov.usgs.anss.util.UC;

import gov.usgs.edge.snw.SNWGroup;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.TreeMap;

/**
 * This class built the original events for the SNW groups. It sends events if the mins for bad and
 * warnings are not met. The StatusServer property must be set via properties in edge.prop
 *
 * @author davidketchum
 */
public final class SNWGroupEventProcess extends Thread {

  private static DBConnectionThread dbconn;
  private final StatusSocketReader channels;
  private final TreeMap<String, Network> networks;
  private long lastDBRefresh;
  private final int[] totstations;
  private final int[] bad;
  private final int[] yellow;
  private final boolean[] isWarn;
  private final boolean[] isBad;
  private ArrayList<SNWGroup> groups;
  private boolean dbg;
  private final boolean sendEvents;     // If this is running in a channel display, do not send events.
  private boolean terminate;
  long disabledMask;
  private final EdgeThread par;

  public long getDisabledMask() {
    return disabledMask;
  }

  public boolean[] getIsWarn() {
    return isWarn;
  }

  public boolean[] getIsBad() {
    return isBad;
  }

  public String getMonitorString() {
    return "SNWGroupEventProcess monitor string";
  }

  public String getStatusString() {
    return "SNWGroupEventProcess status string";
  }

  public String getConsoleOutput() {
    return "";
  }

  public StatusSocketReader getChannelSSR() {
    return channels;
  }

  public static void clearMySQL() {
    DBConnectionThread.getThread("edge").terminate();
    dbconn = null;
  }

  /**
   * return Vector with the SNWGroups
   *
   * @return the Vector with SNWGroups
   */
  public ArrayList<SNWGroup> getGroups() {
    return groups;
  }

  /**
   * * return array of total number of stations in SWNGroup
   *
   * @return array of ints with # of stations in SNWGroup indexed by SNWGroup ID
   */
  public int[] getTotalStations() {
    return totstations;
  }

  /**
   * get array of which SNWGroups are warn
   *
   * @return an array of booleans of which SNWGroups are Warn. Index is SNWGroup ID
   */
  public int[] getNBadStations() {
    return bad;
  }

  /**
   * get array of which SNWGroups are bad
   *
   * @return an array of booleans of which SNWGroups are bad. Index is SNWGroup ID
   */
  public int[] getNWarnStations() {
    return yellow;
  }

  private void prt(String s) {
    if (par == null) {
      Util.prt("SNWGEP:" + s);
    } else {
      par.prt("SNWGEP:" + s);
    }
  }

  private void prta(String s) {
    if (par == null) {
      Util.prta("SNWGEP:" + s);
    } else {
      par.prta("SNWGEP:" + s);
    }
  }

  /**
   * Creates a new instance of SNWGroupEventProcess
   *
   * @param sendEvt boolean if true, events are sent to Alarm system
   * @param parent The parent thread for logging
   */
  public SNWGroupEventProcess(boolean sendEvt, EdgeThread parent) {
    Util.prt("Connect to status server at " + Util.getProperty("StatusServer") + " sendEvt=" + sendEvt);
    channels = new StatusSocketReader(ChannelStatus.class, Util.DBURL2IP(Util.getProperty("StatusServer")),
            AnssPorts.CHANNEL_SERVER_PORT, parent);
    networks = new TreeMap<>();
    par = parent;
    if (dbconn != null) {
      dbconn.terminate();
      while (dbconn.isAlive()) {
        Util.sleep(100);
      }
      dbconn = null;
    }
    dbconn = DBConnectionThread.getThread("edge");
    if (dbconn == null) {
      try {
        dbconn = new DBConnectionThread(Util.getProperty("DBServer"), "readonly", "edge", false, false, "edge", Util.getOutput());
        if (!dbconn.waitForConnection()) {
          if (!dbconn.waitForConnection()) {
            if (!dbconn.waitForConnection()) {
              Util.prt("***** did not promptly make a connection");
            }
          }
        }
      } catch (InstantiationException e) {
        prta("Instantiation exception e=" + e);
        System.exit(0);
      }
    }
    totstations = new int[64];
    sendEvents = sendEvt;
    bad = new int[64];
    yellow = new int[64];
    isWarn = new boolean[64];
    isBad = new boolean[64];
    start();
  }

  public void terminate() {
    terminate = true;
    interrupt();
  }

  /**
   * thread run loop - process the channels every 60 seconds and send events as needed
   */
  @Override
  public void run() {
    int lastMsgCount = -1;
    Object msgs[] = new Object[2000];

    int nmsgs;
    try {
      sleep(20000);
    } catch (InterruptedException expected) {
    }
    while (!terminate) {
      try {

        long now = System.currentTimeMillis();
        if (!channels.isConnected() || lastMsgCount == channels.getCountMsgs()) {
          if (now % 600000 < 60000) {
            prta("SSR Status:" + channels.toString());
          }
          prta("CDupd:  detected dead SSR connection.  Remake it.");
          SendEvent.debugEvent("SNWGroupSSR", "Alarm: SNWGroupEventProcess rebuild SSR", this);
          channels.reopen();
        }
        lastMsgCount = channels.getCountMsgs();
        if (now - lastDBRefresh > 3600000) {
          //DBConnectionThread.keepFresh();
          UC.setConnection(dbconn.getConnection());
          JDBConnection.addConnection(dbconn.getConnection(), "edge");

          lastDBRefresh = now;
          SNWGroup.makeSNWGroups();
          groups = SNWGroup.getGroups();
          disabledMask = 0;
          for (SNWGroup group : groups) {
            if (group.getSNWGroup().equals("_Disable Monitor")) {
              disabledMask = 1L << (group.getID() - 1);
            }
          }
        }
        ChannelStatus ps, ps2, ps3;

        if (dbg) {
          prta("CDupd: Update start");
        }
        if (channels != null) {
          nmsgs = channels.length();
          if (msgs.length < nmsgs + 10) {
            msgs = new Object[nmsgs * 2];
          }
          channels.getObjects(msgs);
        } else {
          return;
        }
        if (dbg) {
          prta("CDupd: nmsg=" + nmsgs);
        }
        if (nmsgs > 0) {
          now = Util.getTimeInMillis();
          for (int i = 0; i < 64; i++) {
            totstations[i] = 0;
            yellow[i] = 0;
            bad[i] = 0;
          }
          Iterator<Network> itr = networks.values().iterator();
          while (itr.hasNext()) {
            itr.next().clear();
          }
          int nchan = 0;

          // See if any of the selections are set
          // Run each network and source through the learning box to see if anything new
          if (dbg) {
            prta("SNWGEP: Update boxes n=" + nmsgs);
          }
          String lastKey = "             ";
          for (int ii = 0; ii < nmsgs; ii++) {
            if (msgs[ii] != null) {
              nchan++;
              ps = (ChannelStatus) msgs[ii];
              if (!ps.getKey().substring(7, 10).equals("BHZ") && !ps.getKey().substring(7, 10).equals("SHZ")
                      && !ps.getKey().substring(7, 10).equals("HHZ") && !ps.getKey().substring(7, 10).equals("EHZ")) {
                continue;
              }
              if (lastKey.substring(0, 7).equals(ps.getKey().substring(0, 7))
                      && lastKey.substring(7, 10).equals("BHZ")
                      && (ps.getKey().substring(7, 10).equals("HHZ") || ps.getKey().substring(7, 10).equals("EHZ"))) {
                continue;
              }
              lastKey = ps.getKey();
              int status = 0;
              if (ps.getNetwork().equals("IC")) {
                if (ps.getLatency() > 7200) {
                  status = 1;
                }
                if (ps.getLatency() > 8200) {
                  status = 2;
                }
                if (ps.getAge() > 3.) {
                  status = 1;
                }
                if (ps.getAge() > 60.) {
                  status = 2;
                }
              } else if ((ps.snwGroupMask() & (1L << 33)) != 0) {  // BUD at IRIS is in 10 minute blocks
                if (ps.getLatency() > 1080) {
                  status = 1;
                }
                if (ps.getLatency() > 1500) {
                  status = 2;
                }
                if (ps.getAge() > 20.) {
                  status = 1;
                }
                if (ps.getAge() > 60.) {
                  status = 2;
                }
              } else {
                if (ps.getLatency() > 180) {
                  status = 1;
                }
                if (ps.getLatency() > 1000) {
                  status = 2;
                }
                if (ps.getAge() > 3.) {
                  status = 1;
                }
                if (ps.getAge() > 60.) {
                  status = 2;
                }
              }

              // Analyze the network group
              if (ps.getNetwork().charAt(0) != 'X') {
                Network net = networks.get(ps.getNetwork());
                if (net == null) {
                  net = new Network(ps.getNetwork());
                  networks.put(ps.getNetwork(), net);
                }
                net.inc();
                if (status == 1) {
                  net.incYellow();
                }
                if (status == 2) {
                  net.incBad();
                }

                // Look at each possible group for membership and add them up
                for (int i = 0; i < groups.size(); i++) {
                  int id = groups.get(i).getID() - 1;
                  if ((ps.snwGroupMask() & groups.get(i).getMask()) != 0 && (ps.snwGroupMask() & disabledMask) == 0) {
                    if (id > 64) {
                      prta("Got a group out of range ps.snwgroupmask=" + Util.toHex(ps.snwGroupMask()) + " groups.size=" + groups.size() + " i=" + i);
                    } else {
                      totstations[id]++;
                      if (status == 1) {
                        yellow[id]++;
                      }
                      if (status == 2) {
                        bad[id]++;
                      }
                    }
                  }
                }
              }
            }
          }     // for each channel
        }       // if there are some messages!
        // Data gathering done, Decide about groups.
        for (SNWGroup group : groups) {
          if (!group.isEventable()) {
            continue;
          }
          int id = group.getID() - 1;
          isBad[id] = false;
          isWarn[id] = false;
          int badpct = group.getpctbad();
          int badwarnpct = group.getpctbadwarn();
          if (totstations[id] > 4) {
            //prt("Group="+groups.get(i)+" #="+totstations[id]+" bad="+bad[id]+" yellow="+yellow[id]+" badpct="+badpct+" warnpct="+badwarnpct);
            double pct = ((double) (bad[id])) / ((double) totstations[id]);
            if (pct > badpct / 100.) {
              isBad[id] = true;
              if (sendEvents) {
                prta("   *** Group = " + group.toString() + " too many bad =" + bad[id] + " of " + totstations[id] + " conn=" + channels.isConnected());
                String grp = group.toString();
                grp = grp.replaceAll("IMPORT ", "IM:");
                grp = grp.replaceAll("SeedLink ", "SL:");
                grp = grp.replaceAll("-", ":");
                grp = grp.replaceAll(" ", "_");
                if (grp.length() > 9) {
                  grp = grp.substring(0, 9);
                }
                if (channels.isConnected()) {
                  SendEvent.sendEvent("SNW", "Bad" + grp,
                          "Bad " + ((int) (pct * 100.)) + "% in " + group.toString() + " exceeded " + badpct + "% " + bad[id] + " of " + totstations[id],
                          Util.getSystemName(), "SNWGrpEvtPrc");
                }
              }
            }
            pct = ((double) (yellow[id] + bad[id])) / ((double) totstations[id]);
            if (pct > badwarnpct / 100.) {
              isWarn[id] = true;
              if (sendEvents && !isBad[id]) {
                prta("   *** Group = " + group.toString() + " too many bad or yellow bad=" + bad[id] + " yellow=" + yellow[id] + " of " + totstations[id] + " conn=" + channels.isConnected());
                String grp = group.toString();
                grp = grp.replaceAll("IMPORT ", "IM:");
                grp = grp.replaceAll("SeedLink ", "SL:");
                grp = grp.replaceAll("-", ":");
                grp = grp.replaceAll(" ", "_");
                if (grp.length() > 9) {
                  grp = grp.substring(0, 9);
                }
                if (channels.isConnected()) {
                  SendEvent.sendEvent("SNW", "Wrn" + grp,
                          "Bad/Warn " + ((int) (pct * 100.)) + "% in " + group.toString() + " exceeded " + badwarnpct + "% bad=" + bad[id] + " warn=" + yellow[id] + " of " + totstations[id],
                          Util.getSystemName(), "SNWGrpEvtPrc");
                }
              }
            }
          }
        }

        // Now interate networks for same
        String badNets = "";
        String warnNets = "";
        if (now % 86400000L < 7200000L || now % 86400000L > 54000000L) {
          Iterator<Network> itr = networks.values().iterator();
          while (itr.hasNext()) {
            Network net = itr.next();
            int badpct = 76;
            int badwarnpct = 91;
            if (net.getNStations() > 10) {
              //prt("Network="+net.getNetwork()+"  bad="+net.getBad()+
              //      " yellow="+net.getYellow()+" of "+net.getNStations());
              double pct = ((double) (net.getBad())) / ((double) net.getNStations());
              if (sendEvents && pct > badpct / 100.) {
                badNets += net.getNetwork() + "(" + ((int) (pct * 100. + 0.5)) + ">" + badpct + "%B) ";
                prta("   *** Network = " + net.getNetwork() + " too many bad =" + net.getBad()
                        + " of " + net.getNStations() + " " + badNets);
              } else {
                pct = ((double) (net.getYellow() + net.getBad())) / ((double) net.getNStations());
                if (sendEvents && pct > badwarnpct / 100.) {
                  warnNets += net.getNetwork() + "(" + ((int) (pct * 100. + 0.5)) + ">" + badwarnpct + "%W) ";
                  prta("   *** Network = " + net.getNetwork() + " too many bad or yellow bad=" + net.getBad()
                          + " yellow=" + net.getYellow() + " of " + net.getNStations() + "  " + warnNets);
                }
              }
            }
          }
          /*if(badNets.length() > 1 && channels.isConnected() && (badNets.length() > 3 || badNets.charAt(0) != 'X'))
            SendEvent.sendEvent("SNW","NetBadHigh", "Bad Net "+badNets,"SNW", "SNWGrpEvtPrc");
          if(warnNets.length() > 1 && channels.isConnected())
            SendEvent.sendEvent("SNW","NetBadWarnHi", "Bad/Warn "+warnNets, "SNW", "SNWGrpEvtPrc");
           */
        }
        try {
          sleep(60000);
        } catch (InterruptedException expected) {
        }
      } catch (Exception e) {
        prta("*** Runtime Exception found e=" + e);
        if (par == null) {
          e.printStackTrace();
        } else {
          e.printStackTrace(par.getPrintStream());
        }
      }
    }         // infinite for loop
    // sometimes this is not run from an EdgeThread, logging is minimal!
    if (par == null) {
      Util.prt("SNWGEP: Is exiting term=" + terminate);
    } else {
      prt("SNWGEP: Is exiting term=" + terminate);
    }
  }

  /**
   * inner class used to do calculations on network codes
   */
  private final class Network {

    String network;
    int bad;
    int yellow;
    int totalstations = 0;

    public Network(String net) {
      network = net;
      yellow = 0;
      bad = 0;
    }

    public void clear() {
      bad = 0;
      yellow = 0;
      totalstations = 0;
    }

    public void incYellow() {
      yellow++;
    }

    public void incBad() {
      bad++;
    }

    public void inc() {
      totalstations++;
    }

    public int getYellow() {
      return yellow;
    }

    public int getBad() {
      return bad;
    }

    public int getNStations() {
      return totalstations;
    }

    public String getNetwork() {
      return network;
    }

    @Override
    public String toString() {
      return network + " #stations=" + totalstations + " #bad=" + bad + " #yellow=" + yellow;
    }

  }

  /**
   * main test class
   *
   * @param args the command line arguments
   */
  public static void main(String args[]) {
    Util.setModeGMT();
    Util.setNoInteractive(true);
    Util.prt("starting argc=" + args.length);
    for (int i = 0; i < args.length; i++) {
      Util.prt("arg " + i + " is " + args[i]);
    }
    SNWGroupEventProcess snwGroupEventProcess = new SNWGroupEventProcess(true, null);

  }

}
