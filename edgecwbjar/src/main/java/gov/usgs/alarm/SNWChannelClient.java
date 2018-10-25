/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */

 /*
 * SNWChannelClient.java
 *
 * Created on September 7, 2006, 10:34 AM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */
package gov.usgs.alarm;

import gov.usgs.anss.channelstatus.ChannelStatus;
//import gov.usgs.alarm.SendEvent;
import gov.usgs.anss.db.DBAccess;
import gov.usgs.anss.db.DBMessageQueuedClient;
import gov.usgs.anss.edgemom.EdgeChannelServer;
import gov.usgs.anss.edgethread.EdgeThread;
import gov.usgs.anss.util.Util;
import gov.usgs.anss.util.StaSrv;
import gov.usgs.anss.util.AnssPorts;
import gov.usgs.anss.util.SimpleSMTPThread;
import gov.usgs.anss.util.UC;
import gov.usgs.anss.dbtable.DBTable;
import gov.usgs.anss.net.SNWSender;
import gov.usgs.anss.net.StatusSocketReader;
import gov.usgs.edge.config.Channel;
import gov.usgs.edge.snw.SNWStation;
import java.net.UnknownHostException;
import java.sql.Timestamp;
import java.text.DecimalFormat;
import java.util.TreeMap;
import java.io.IOException;

/**
 * This take the inputs from the UdpChannel collection of channel latency and
 * flow and does database updates of
 * <pre>
 * snw tables : snwstation (latency, last received), create new ones if network/station is new
 * channel table : rate changes, last data
 * status tables : latency (creates new ones per policy)
 * </pre>
 * <p>
 * If there is an snwsender, then data for SNW is also sent to the SNW instance.
 * <p>
 * It is run by alarm when the -snw flag is set.
 * <p>
 * In 2016 this code was reworked to not connect directly to the MySQL database,
 * but to use the DBMessageQueueClient or DBAccess for all of its updates. There
 * are two of these, one with few queue elements but allow really long updates
 * (mainly for updating lastdata in channel), and a much bigger depth queue but
 * with shorter messages. The code selects the correct one based on the length
 * of the message.
 * <p>
 * This routine maintains the on disk DB/edge_snwstation.txt table when in NoDB mode.  This
 * mainly is to create new SNW stations and to very occasionally update latency, latency time,
 * and last minutes received.  Alarm is not normally the master so it only updates this one
 * table that is not updated by the EdgeMom master for the DB tables.
 * <p>
 *
 * If there is an snwsender, then data for SNW is also sent to the SNW instance.
 *
 * @author davidketchum
 */
public final class SNWChannelClient extends Thread {

  private static final int MSG_SHORT_LENGTH = 120;          // 
  private final StatusSocketReader channels;
  private final DecimalFormat df2 = new DecimalFormat("##0.0");
  private final TreeMap<String, byte[]> lastLatency = new TreeMap<>();   // keep track of last latency by channel
  private final TreeMap<String, CheckRate> rates = new TreeMap<>();       // keep track of channel reates by channel

  private long lastPage;
  private final EdgeThread par;
  private final boolean nosnwsender;
  private boolean dbg;
  private StaSrv stasrv;
  private int nsnw = 0;
  private int nnew = 0;
  private int nupdate = 0;
  private long nlatency = 0;
  private int state;
  private boolean terminate;
  private int lastDataModulus = 20;

  // logging and performance data
  private final StringBuilder runsb = new StringBuilder(100);

  // Database related variables
  private DBAccess dbaccess;                        // access to update disk files with DB
  private DBMessageQueuedClient dbMessageClientBig; // The long line but fewere queue for outgoing updates to MySQL
  private DBMessageQueuedClient dbMessageClient;    // the short line, but many queue for udpates to MySQL
  private final StringBuilder dbmsg = new StringBuilder(10); // Used to build all of the messages to be queued to a DBMQC

  @Override
  public String toString() {
    return "SNWC: #snw=" + nsnw + " #new=" + nnew + " #upd=" + nupdate + " #lat=" + nlatency + " st=" + state;
  }
  StringBuilder monitorsb = new StringBuilder(100);

  /**
   * Set the modulus for updating the last data to given value. Approximately
   * 1/modulus of total channels have the lastdata value in the channel table
   * updated each 3 minutes. Def=60 so about 3 hours to update them all.
   *
   * @param mod
   */
  public void setLastDataModulus(int mod) {
    lastDataModulus = mod;
  }

  public StringBuilder getMonitorString() {
    Util.clear(monitorsb);
    monitorsb.append("#snw=").append(nsnw).append("\n#new=").append(nnew).
            append("\n#upd=").append(nupdate).append("\n#lat=").append(nlatency).append("\nst=").append(state).append("\n");
    return monitorsb;

  }

  public StatusSocketReader getStatusSocketReader() {
    return channels;
  }

  public TreeMap<String, byte[]> latencyMap() {
    return lastLatency;
  }

  private void prt(String s) {
    if (par == null) {
      Util.prt(s);
    } else {
      par.prt(s);
    }
  }

  private void prta(String s) {
    if (par == null) {
      Util.prta(s);
    } else {
      par.prta(s);
    }
  }

  private void prt(StringBuilder s) {
    if (par == null) {
      Util.prt(s);
    } else {
      par.prt(s);
    }
  }

  private void prta(StringBuilder s) {
    if (par == null) {
      Util.prta(s);
    } else {
      par.prta(s);
    }
  }

  public int getCurrentState() {
    return state;
  }

  public void terminate() {
    terminate = true;
  }

  /**
   * Creates a new instance of SNWChannelClient If a preexisting
   * StatusSOcketReader exists, it can be passed in, otherwise a new one will be
   * opened
   *
   * @param rs The StatusSocketReader to use if any - either this or udpServer
   * should be specified
   * @param udpServer The IP address of a UdpChannel server, used to create one
   * if rs is null
   * @param nosnwsend Do not send results to the SNW server
   * @param parent The EdgeThread for logging
   */
  public SNWChannelClient(StatusSocketReader rs, String udpServer, boolean nosnwsend, EdgeThread parent) {
    // either use the passed SSR or create a new one.
    if (rs != null) {
      channels = rs;
    } else {
      channels = new StatusSocketReader(ChannelStatus.class, udpServer, AnssPorts.CHANNEL_SERVER_PORT, parent);
    }
    par = parent;
    nosnwsender = nosnwsend;
    channels.appendTag("SNWCC");

    prta("SNWC: created to2 udpChannel=" + udpServer);
    setDaemon(true);      // we can shutdown without any clean up.
    start();
  }

  /**
   * put the message in either the short but deep or the very long but shallow
   * DBMessageQueuedClient
   *
   * @param msg The
   */
  private void queueDBMsg(StringBuilder msg) {
    if (msg.length() < MSG_SHORT_LENGTH) {
      if (dbMessageClient == null) {
        return;
      }
      dbMessageClient.queueDBMsg(msg);
      if (dbMessageClient.getPctFull() > 90) {
        try {
          sleep(100);
        } catch (InterruptedException expected) {
        }
      }
    } else {
      if (dbMessageClientBig == null) {
        return;
      }
      dbMessageClientBig.queueDBMsg(msg);
    }
  }

  @Override
  public void run() {
    int nmsgs;
    Object[] msgs = new Object[2000];

    prta("SNWC: start");
    StringBuilder sb = new StringBuilder(200);
    StringBuilder chans = new StringBuilder(200);
    StringBuilder sbrate = new StringBuilder(200);
    Timestamp lastdata = new Timestamp(340000);
    long start;
    long end;
    String testStation = "IUYSS  ";
    String s = "";
    SNWStation snw = null;
    SNWStation chksnw = null;
    String station = "";
    String channel = "";
    String location = "";
    ChannelStatus cs = null;
    SNWSender snwsender = null;
    String justCreated = "";
    String tmp = "";
    int agehr = 0;
    int loop = 0;
    int lastDataSave = 0;
    long lastRateCheck = 0;
    try {
      sleep(60000);
    } catch (InterruptedException expected) {
    }  // /DEBUG : normally 60 Allow time for DB threads to open, UdpChannel to populate
    while (DBAccess.getAccess() == null) {
      try {
        sleep(500);
      } catch (InterruptedException expected) {
      }
    }
    prta("SNWC: got DBACCESS ");
    dbaccess = DBAccess.getAccess();
    if (!Alarm.isNoDB()) {
      dbMessageClientBig = new DBMessageQueuedClient(EdgeChannelServer.getDBMessageServer(), 7985, 10, 10000, par); // 10 deep queue of 10000 characters each.
    }
    else {
      dbaccess.getSNWStationDBTable();
      while(dbaccess.getSNWStationDBTable().getNRows() <= 0) {
        try {sleep(1000);} catch(InterruptedException expected) {}
      }
      prta("SNWC: NoDB mode snwstation #rows="+dbaccess.getSNWStationDBTable().getNRows());
    }
    
    StringBuilder sbLastDataList = new StringBuilder(1000);
    Timestamp now = Util.now();
    long lastNlatency = 0;
    int lastSNWProcessID = 0;
    String last = "";                     // Look for station changes.
    int lasti=0;
    while (!terminate) {
      try {
        state = 1;
        loop++;
        start = System.currentTimeMillis();
        Util.clear(sbrate);
        Util.clear(sbLastDataList);
        Util.clear(chans);
        if (channels != null) {
          nsnw = 0;
          nmsgs = channels.length();
          if (dbMessageClient == null && !Alarm.isNoDB()) {
            dbMessageClient = new DBMessageQueuedClient(EdgeChannelServer.getDBMessageServer(), 7985, Math.max(nmsgs * 2, 20), MSG_SHORT_LENGTH, par);
            prta(Util.clear(runsb).append("SNWC: * Make dbMessageClient queue size=").append(nmsgs * 2));
          } // 10 deep queue of 10000 characters each.
          else {
            if (dbMessageClient != null) {
              if (nmsgs > dbMessageClient.getQsize()) {
                prta(Util.clear(runsb).append("SNWC: ***** dbMessageClient Qsize might be too small=").append(dbMessageClient.getQsize()).
                        append(" #msg=").append(nmsgs));
              }
            }
          }

          now.setTime(System.currentTimeMillis());
          String nowtime = now.toString().substring(0, 19);// coordinated time for the sample
          if (msgs.length < nmsgs + 10) {
            msgs = new Object[nmsgs * 2];
          }
          channels.getObjects(msgs);            // get a copy of the ChannelStatus objects from the SSR
          ChannelStatus csbhz = null;
          ChannelStatus csbhzhr = null;         // for two Q330 sites.
          //if(loop % 10 == 0) 
          prta(Util.clear(runsb).append(Util.ascdate()).append(" SNWC: nmsgs=").append(nmsgs).append(" ").append(last).
                  append(" loop=").append(loop).append(" new stat=").append(nnew).
                  append(" upd stat=").append(nupdate).append(" lat/m=").append(nlatency - lastNlatency).
                  append(" save=").append(lastDataSave).append(" li=").append(lasti).
                  append(" ").append(Util.ascdatetime2(lastRateCheck + 180000)));
          lastNlatency = nlatency;
          last = "";
          state = 2;
          while (channels.length() == 0) {
            try {
              prta("SNWC: No msgs ");
              sleep(2000);
            } catch (InterruptedException expected) {
            }
          }
          nupdate=0;
          for (int i = 0; i <= nmsgs; i++) {
            lasti=i;
            try {
              if (i == nmsgs) {
                station = "DUMMYSN    ";
              } else {
                // set our working variables for this channel
                cs = (ChannelStatus) msgs[i];
                channel = cs.getKey().substring(7, 10);
                station = cs.getKey().substring(0, 7);
                location = cs.getKey().substring(10, 12);
                if (channel.substring(1, 3).equals("DA") || channel.substring(1, 3).equals("DF")) {
                  continue;
                }
              }

              if (station.substring(0, 2).equals("ZZ") || station.substring(0, 2).equals("??")) {
                continue;// testing stuff
              }
              state = 3;
              // Do a rate and last data check every 3 minutes
              if (start - lastRateCheck > 180000) {
                if (cs != null) {
                  /*prta(Util.clear(runsb).append("SNWC: cs time=").append(Util.ascdatetime2(cs.getPacketTime())).append(" i=").append(i).
                          append(" ns=").append(cs.getNsamp()).append(" rt=").append(cs.getRate()).
                          append(" doit=").append(cs.getNextPacketTime() > start-600000 && (i % 60) == (lastDataSave % 60)).
                          append(" ").append(cs.getKey()).append(" "));*/
                  if (cs.getNextPacketTime() > start - 600000 && (i % lastDataModulus) == (lastDataSave % lastDataModulus)) { // If this packet time is fairly recent and its the right one
                    if (cs.getNsamp() > 0 && cs.getRate() > 0) {
                      sbLastDataList.append("'").append(station).append(channel).append(location.trim()).append("',");
                      //int irow = dbaccess.getChannel(cs.getKey());
                      //if(irow >= 0) dbaccess.getChannelDBTable().updateTimestamp(irow, "lastdata",now);    // Put new time in channel table
                    }
                  }
                  if (cs.getRate() > 0.0009 && cs.getNsamp() > 0) {  // If rates and NSamp are ok
                    CheckRate chk = rates.get(station + channel + location);
                    if (chk == null) {
                      chk = new CheckRate(cs.getRate());
                      rates.put(station + channel + location, chk);
                    }
                    if (chk.add(cs.getRate())) {   // If the rate is stable for at least 20 cycles
                      Channel chn = EdgeChannelServer.getChannel(station + channel + location);
                      if (chn != null) {
                        double ratio = chn.getRate() / cs.getRate();
                        if (ratio < 0.98 || ratio > 1.02) {
                          if (!station.startsWith("REQUEST")) {
                            sbrate.append(station).append(channel).append(location).append(" ECS.rate=").
                                    append(chn.getRate()).append("!=").append(cs.getRate()).append("\n");
                            prta(Util.clear(runsb).append("SNWC: rates do not agree ").
                                    append(station).append(channel).append(location).append(" ECS.rate=").append(chn.getRate()).
                                    append("!=").append(cs.getRate()).append(" ").append(chk));
                            //chk.clear();
                            // Finde the channel and update it via DBAccess and dbmsg
                            if(!cs.getSource().contains("EWI")) {     // Import cannot change their rates
                              for (int ii = 0; ii < dbaccess.getChannelSize(); ii++) {
                                if (cs.getKey().trim().equals(dbaccess.getChannel(ii).getChannel())) {
                                  Channel chan = dbaccess.getChannel(ii);
                                  //dbaccess.getChannelDBTable().updateDouble(ii, "rate", cs.getRate());    // update in dbaccess
                                  Util.clear(dbmsg).append("edge^channel^id=").append(chan.getID()).append(";rate=").append(cs.getRate()).append(";\n");
                                  prta(Util.clear(runsb).append("SNWC: Changing rate in DB for ").append(cs.getKey()).append(" : ").append(dbmsg));
                                  queueDBMsg(dbmsg);   // update the database
                                  break;
                                }
                              }
                            }
                          }
                        }
                      }
                    }
                    //else if(station.startsWith("TAP33M")) prta(cs.getKey()+" is not stable chk="+chk);
                  }
                }
              }     // end of rate check if
              nsnw++;
              if (i == 0) {
                last = station;
              }
              if (station.substring(0, 7).trim().equals(testStation.trim()) && csbhz != null) {
                prt(Util.clear(runsb).append("SNWC:csbhz=").append(csbhz).append(" ").append(snw).append(" "));
              }
              state = 4;
              if ((!last.equals(station) || i == nmsgs - 1) && csbhz != null && snw != null) {
                Util.clear(sb);

                sb.append(csbhz.getKey().substring(0, 2).replaceAll(" ", "_")).append("-").append(csbhz.getKey().substring(2, 7).trim());
                sb.append(":10:");
                sb.append("latency=").append(df2.format(csbhz.getLatency()).trim());
                int age = (int) (csbhz.getAge() + 0.5);
                sb.append(";lastRcvMinutes=").append(age);
                sb.append(";nbytes=").append(snw.getNbytes());
                sb.append(";otherStreamLatency=").append(Math.min(Math.max(-32767, csbhz.getOtherStreamLatency()), 32767));
                sb.append(";cpu=").append(csbhz.getCpu().trim());
                sb.append(";sort=").append(csbhz.getSort().trim());
                sb.append(";source=").append(csbhz.getSource().trim());
                sb.append(";process=").append(csbhz.getProcess().trim());
                sb.append(";channel=").append(csbhz.getKey().substring(7, 12)).append(":").append(chans);
                sb.append(";rate=").append(df2.format(csbhz.getRate()).trim());
                state = 5;
                nupdate++;
                /*chksnw = map.get(csbhz.getKey().substring(0,7));
                if(chksnw != null) 
                  if(chksnw.getDisable() != 0) {
                    sb.append(";disable_comment="+chksnw.getDisableComment());
                    sb.append(";disable_expires="+chksnw.getDisableExpires());
                }*/
                sb.append("\n");
                if (csbhz.getKey().substring(2, 7).trim().equals(testStation.substring(2).trim()) || dbg) {
                  prta(Util.clear(runsb).append("SNWC:").append(csbhz.toString()).append(" age=").append(csbhz.getAge()).append("\n").append(sb));
                }
                if (sb.indexOf(testStation) >= 0) {
                  prt(Util.clear(runsb).append("SNWC:").append(csbhz.toString()).append(" ").append(sb));
                }

                // If a sender to SNW is not yet create, make one and send this data
                if (snwsender == null && !nosnwsender) {
                  try {
                    prta("SNWC: open SNWSender ");
                    snwsender = new SNWSender(350, 1200);

                  } catch (UnknownHostException e) {
                    prta("SNWC: open sender got a host not found going to gsnw");
                    snwsender = null;
                  }
                } else if (!nosnwsender && snwsender != null) {
                  boolean ok = snwsender.queue(sb);
                  if (station.substring(0, 7).trim().equals(testStation.trim())) {
                    prt(Util.clear(runsb).append("SNWC: snwsend=").append(ok).append(" ").append(sb));
                  }
                }
                state = 6;
                // If this is the first time we have seen this channel create it
                // If this site has an HR, then send it also!
                if (csbhzhr != null) {
                  Util.clear(sb);
                  tmp = csbhzhr.getKey().substring(2, 7);
                  if (tmp.substring(3, 4).equals("1")) {
                    tmp = tmp.substring(0, 3) + "2";
                  } else {
                    tmp = tmp.substring(0, 3) + "1";
                  }
                  sb.append(csbhzhr.getKey().substring(0, 2).replaceAll(" ", "_")).append("-").append(tmp.trim());
                  sb.append(":10:");
                  sb.append("latency=").append(df2.format(csbhzhr.getLatency()).trim());
                  agehr = (int) (csbhzhr.getAge() + 0.5);
                  sb.append(";lastRcvMinutes=").append(agehr);
                  sb.append(";nbytes=").append(snw.getNbytes());
                  sb.append(";otherStreamLatency=").append(Math.min(Math.max(-32767, csbhzhr.getOtherStreamLatency()), 32767));
                  sb.append(";cpu=").append(csbhzhr.getCpu().trim());
                  sb.append(";sort=").append(csbhzhr.getSort().trim());
                  sb.append(";source=").append(csbhzhr.getSource().trim());
                  sb.append(";process=").append(csbhzhr.getProcess().trim());
                  sb.append(";channel=").append(csbhzhr.getKey().substring(7, 12)).append(":").append(chans);
                  sb.append(";rate=").append(df2.format(csbhzhr.getRate()).trim());
                  sb.append("\n");
                  state = 7;
                  nupdate++;
                  //prta(Util.clear(runsb).append("SNWC: HR:").append(sb));
                  // If a sender to SNW is not yet create, make one and send this data
                  if (snwsender != null) {
                    snwsender.queue(sb);
                  }
                }
                Util.clear(chans);
                state = 8;
                if (testStation.trim().equals(csbhz.getKey().substring(0, 7).trim())) {
                  prt(Util.clear(runsb).append("SNWC: latency save=").append(snw.getLatencySaveInterval()).append(" loop=").append(loop));
                }

                // The structure lastLatency stores the ChannelStatus every minute for each station
                byte[] b = lastLatency.get(csbhz.getKey().substring(0, 7));
                if (b == null) {
                  b = new byte[ChannelStatus.getMaxLength()];
                  lastLatency.put(csbhz.getKey().substring(0, 7), b);
                }
                System.arraycopy(csbhz.getData(), 0, b, 0, ChannelStatus.getMaxLength());
                state = 9;
                // randomize the minute by the the ID
                if ((loop + snw.getID()) % ((snw.getLatencySaveInterval()) == 0 ? 600 : snw.getLatencySaveInterval()) == 0) {
                  state = 10;
                  // Create a new latency record in status and update the latency time in snwstation
                  Util.clear(dbmsg).append("status^latency^station=").append(csbhz.getKey().substring(0, 7)).
                          append(";time=").append(nowtime).
                          append(";latency=").append((int) Math.max(Math.min(csbhz.getLatency() + 0.5, 32767.), -32767.)).
                          append(";lastrecv=").append((short) Math.min(age, 32767)).
                          append(";nbytes=").append(Math.min((snw.getNbytes() + 500) / 1000, 32767)).
                          append(";otherstreamlatency=").append((int) Math.max(-32767, Math.min(csbhz.getOtherStreamLatency(), 32767))).append(";");
                  queueDBMsg(dbmsg);
                  if (csbhz.getKey().substring(0, 7).trim().equals(testStation.trim()) || dbg) {
                    prta(Util.clear(runsb).append("SNWC:dbmsg=").append(dbmsg));
                  }
                  boolean updateOk = false;
                  if (snw.getLatencySaveInterval() >= 10) {
                    updateOk = true;
                  } else if ((loop + snw.getID()) % 10 == 0) {
                    updateOk = true;
                  }
                  int irow;
                  if (updateOk) {
                    irow = dbaccess.getSNWStation(station, false);
                    if (irow >= 0) {
                      dbaccess.getSNWStationDBTable().updateInt(irow, "latency", ((int) Math.max(Math.min(csbhz.getLatency() + 0.5, 32767.), -32767.)));
                      dbaccess.getSNWStationDBTable().updateTimestamp(irow, "latencytime", now);
                      dbaccess.getSNWStationDBTable().updateInt(irow, "lastrecv", ((short) Math.min(age, 32767)));
                      SNWStation snwstation = dbaccess.getSNWStation(irow);
                      Util.clear(dbmsg).append("edge^snwstation^id=").append(snwstation.getID()).
                              append(";latencytime=").append(nowtime).
                              append(";latency=").append((int) Math.max(Math.min(csbhz.getLatency() + 0.5, 32767.), -32767.)).
                              append(";lastrecv=").append((short) Math.min(age, 32767)).append(";");
                      queueDBMsg(dbmsg);
                    }
                  }
                  if (dbg) {
                    prta(Util.clear(runsb).append("SNWC: snwlat:").append(age).append(" ").append(dbmsg));
                  }
                  nlatency++;
                  // if this is a 2nd Q330, do its update
                  if (csbhzhr != null) {
                    Util.clear(dbmsg).append("status^latency^station=").append(csbhzhr.getKey().substring(0, 2)).append(tmp).
                            append(";time=").append(nowtime).
                            append(";latency=").append((int) Math.max(-32767, ((int) Math.min(csbhzhr.getLatency() + 0.5, 32767.)))).
                            append(";lastrecv=").append((short) Math.min(agehr, 32767)).
                            append(";nbytes=").append(Math.min((snw.getNbytes() + 500) / 1000, 32767)).
                            append(";otherstreamlatency=").append(((int) Math.max(-32767, Math.min(csbhzhr.getOtherStreamLatency(), 32767)))).append(";");
                    queueDBMsg(dbmsg);

                    irow = dbaccess.getSNWStation(csbhzhr.getKey().substring(0, 2) + tmp.trim(), false);
                    if (irow >= 0) {
                      dbaccess.getSNWStationDBTable().updateInt(irow, "latency", ((int) Math.max(Math.min(csbhz.getLatency() + 0.5, 32767.), -32767.)));
                      dbaccess.getSNWStationDBTable().updateTimestamp(irow, "latencytime", now);
                      dbaccess.getSNWStationDBTable().updateInt(irow, "lastrecv", (short) Math.min(age, 32767));
                      SNWStation snwstation = dbaccess.getSNWStation(irow);
                      Util.clear(dbmsg).append("edge^snwstation^id=").append(snwstation.getID()).
                              append(";latencytime=").append(nowtime).
                              append(";latency=").append((int) Math.max(Math.min(csbhz.getLatency() + 0.5, 32767.), -32767.)).
                              append(";lastrecv=").append((short) Math.min(agehr, 32767)).append(";");
                      queueDBMsg(dbmsg);
                      if (dbg) {
                        prta(Util.clear(runsb).append("SNWC: snwlat:hr").append(age).append(" ").append(dbmsg));
                      }
                    }
                    nlatency++;
                  }
                }
                state = 11;
                if (snw.getNbytes() > 32000000) {
                  snw.setNbytes(0);
                }
                csbhz = null;
                csbhzhr = null;
                last = station;
                Util.clear(chans);
                state = 12;
                if (i == nmsgs) {
                  continue;        // bail from loop.
                }
              }

              // If this is a new channel, add it to the database
              state = 13;
              if (cs != null) {
                nnew++;
                if (!cs.getKey().contains("%") && EdgeChannelServer.getChannel(cs.getKey()) == null && Util.isValidSeedName(cs.getKey())) {
                  prta("SNWC: Create new channel " + cs.getKey());
                  Util.clear(dbmsg).append("edge^channel^channel=").append(cs.getKey()).
                          append(";commgroupid=0;operatorid=0;protocolid=0;sendto=0;flags=0;links=0;delay=0;sort1=new;sort2=;rate=").
                          append(cs.getRate()).append(";nsnnet=0;nsnnode=0;nsnchan=0;created_by=0;lastdata=current_timestamp();created=current_timestamp();");
                  EdgeChannelServer.createNewChannel(dbmsg);
                  prta("SNWC: CHAN create=" + dbmsg);
                }

                if ((channel.equals("BHZ") || channel.equals("SHZ") || channel.equals("HHZ") || channel.equals("EHZ") || channel.equals("HNZ"))
                        && !location.equals("HR")) {
                  state = 14;
                  if (csbhz == null) {
                    csbhz = cs;
                  } else if (csbhz.getAge() > cs.getAge() && !channel.equals("HNZ")) {  // if we have one, do not override with HNZ
                    csbhz = cs;    // among many, choose one with least age
                  }
                }
              }
              state = 15;
              if (location.equals("HR") && channel.equals("BHZ") && !station.contains("%")) {
                if (csbhzhr == null) {
                  csbhzhr = cs;
                }
                prta("SNWC: HR found=" + (cs == null ? "null" : cs.getKey()));
              }
              if (cs == null) {
                prta(Util.clear(runsb).append("SNWC: cs is null i=").append(i).append(" chans=").append(chans));
                continue;
              }
              if (chans.length() < 150) {
                chans.append(cs.getKey().substring(7, 12)).append(" ");
              } else {
                chans.append(".");
              }

              // Get the snw object for this one, if its not on list, add it
              snw = null;
              int irow = dbaccess.getSNWStation(station, false);
              if (irow >= 0) {
                snw = dbaccess.getSNWStation(irow);
              }
              if (snw == null) {     // need to create an SNWStation
                if (justCreated.equals(station)) {
                  prta(Util.clear(runsb).append("SNWC: * prevent duplicate snwstation create=").append(station));
                  continue; // nothing more to do
                }
                justCreated = station;
                if (stasrv == null) {    // create a stasrv, if it is missing.
                  state = 191;
                  prta("SNWC: Open stasrv");
                  stasrv = new StaSrv("137.227.224.97", 2052);
                  prta("SNWC: Stasrv is open");
                }
                double[] coord = stasrv.getMetaCoord(station.substring(0, 7) + channel);
                String[] names = stasrv.getMetaComment(station.substring(0, 7));
                names[0] = Util.toAllPrintable(Util.deq(names[0].trim())).toString();
                Util.clear(dbmsg).append("edge^snwstation^snwstation=").append(station.substring(2).trim()).
                        append(";network=").append(station.substring(0, 2).trim()).
                        append(";snwruleid=1;cpu=").append(cs.getCpu().trim()).
                        append(";sort=").append(cs.getSort().trim()).
                        append(";process=").append(cs.getProcess().trim()).
                        append(";latitude=").append(coord[0]).
                        append(";longitude=").append(coord[1]).
                        append(";elevation=").append(coord[2]).
                        append(";description=").append(names[0]).
                        append(";latencysaveinterval=600;created=now();created_by=0\n");
                prta("SNW create : " + dbmsg);
                queueDBMsg(dbmsg);
                // SNWStation is only maintained here, so this thread must write out any changes even in noDB mode and not master
                if(Alarm.isNoDB()) {
                  int size = dbaccess.getSNWStationSize();
                  DBTable snwtable = dbaccess.getSNWStationDBTable();
                  snwtable.newRow();
                  snwtable.updateString("network", station.substring(0, 2).trim());
                  snwtable.updateString("snwstation", station.substring(2).trim());
                  snwtable.updateInt("snwruleid", 1);
                  snwtable.updateString("cpu", cs.getCpu().trim());
                  snwtable.updateString("process", cs.getProcess().trim());
                  snwtable.updateString("description", names[0]);
                  snwtable.updateInt("latencysaveinterval", 600);
                  snwtable.updateTimestamp("created", now);
                  snwtable.updateRow();
                  try {
                    snwtable.writeTable(); 
                    snwtable.invalidate();
                    dbaccess.forceUpdatesNow();               // do it now
                    int looper = 0;
                    irow = -1;
                    while(dbaccess.getSNWStationSize() == size && irow < 0) {
                      try{sleep(1000);} catch(InterruptedException expected) {}
                      if(looper++ > 10) {
                        prta("SNWC: ** new SNWStation insert taking to long! size="+size+" "+dbaccess.getSNWStationSize()+" "+snwtable.getNRows());
                        break;
                      }
                      irow = dbaccess.getSNWStation(station, false);
                      //prta("SNWC: ** wait for insert size="+size+" "+dbaccess.getSNWStationSize()+" "+snwtable.getNRows());
                    }
                  }
                  catch(IOException | RuntimeException e) {
                    prta("SNWC: *** exception creating SNW record in snwstation table continue e="+e+" "+snwtable.toString());
                    if(par != null) e.printStackTrace(par.getPrintStream());
                    justCreated = "";
                  }
                }
                prta(Util.clear(runsb).append("SNWC: new SNWStation found ").append(station).append(" cpu=").append(cs.getCpu())
                        .append("#rows=").append(dbaccess.getSNWStationSize()));
                irow = dbaccess.getSNWStation(station, false);
                if(irow < 0) 
                  prta(Util.clear(runsb).append("SNWCC: *** snwstation new record did not return a row!").append(dbaccess.getSNWStationDBTable().toString()));
                else
                  prta(Util.clear(runsb).append("SNWCC: new snwstation row=").append(irow));
                state = 16;
              } else {        // snw is not null, check to see if anything change
                state = 22;
                if (snw.getID() != lastSNWProcessID) {
                  if ((!snw.getRemoteIP().trim().equalsIgnoreCase(cs.getRemoteIP().trim())
                          || !snw.getRemoteProcess().trim().equalsIgnoreCase(cs.getRemoteProcess().trim())
                          || !snw.getCpu().trim().equalsIgnoreCase(cs.getCpu().trim())
                          || !snw.getSort().trim().equalsIgnoreCase(cs.getSort().trim())
                          || !snw.getProcess().trim().equalsIgnoreCase(cs.getProcess().trim()))
                          && !cs.getProcess().trim().equals("PrcUnkwn")) { // No data yet, do not update
                    state = 23;
                    Util.clear(dbmsg).append("edge^snwstation^id=").append(snw.getID()).
                            append(";remoteip=").append(cs.getRemoteIP()).
                            append(";cpu=").append(cs.getCpu().trim()).
                            append(";remoteprocess=").append(cs.getRemoteProcess().trim()).
                            append(";sort=").append(cs.getSort().trim()).
                            append(";process=").append(cs.getProcess()).append(";\n");
                    queueDBMsg(dbmsg);
                    prta(Util.clear(runsb).append("FSNWC: update data ").append(station).append(" remip=").append(snw.getRemoteIP()).
                            append("|").append(cs.getRemoteIP()).append(snw.getRemoteIP().trim().equalsIgnoreCase(cs.getRemoteIP().trim())).
                            append(" cpu=").append(snw.getCpu()).append("|").append(cs.getCpu()).append(snw.getCpu().trim().equalsIgnoreCase(cs.getCpu().trim())).
                            append(" remProc=").append(snw.getRemoteProcess()).append("|").append(cs.getRemoteProcess()).append(snw.getRemoteProcess().trim().equalsIgnoreCase(cs.getRemoteProcess().trim())).
                            append(" sort=").append(snw.getSort()).append("|").append(cs.getSort()).append(snw.getSort().trim().equalsIgnoreCase(cs.getSort().trim())).
                            append(" proc=").append(snw.getProcess()).append("|").append(cs.getProcess()).append(snw.getProcess().trim().equalsIgnoreCase(cs.getProcess().trim())));
                    lastSNWProcessID = snw.getID();
                  }
                }
              }       // else we have an snwstation
              state = 25;
              cs.updateLastNbytes();     // This makes the "change" once per minute in this ChannelStatus
              if (snw == null) {
                prta(Util.clear(runsb).append("SNWC:SNW is null st=").append(station).append(" cs=").append(cs));
              } else {
                snw.addNbytes(cs.getNbytesChange()); // add up the minute changes.
              }
            }
            catch(RuntimeException e) {
              prta("SNWC: ** caught a RuntimeExceptionA e=" + e.getMessage());
              prt("SNWC: * StationA=" + station + ":");
              if(par != null) e.printStackTrace(par.getPrintStream());
              
            }
          }  // loop on each channel message

          // If we did a rate, last data check, do the database updates.
          if (start - lastRateCheck > 180000) {
            state = 26;
            lastDataSave++;
            if (sbLastDataList.length() > 10) {
              prta("SNWC: Lastdata updated " + sbLastDataList.length() / 15);
              sbLastDataList.deleteCharAt(sbLastDataList.length() - 1);   // remove trailing comma
              sbLastDataList.insert(0, "UPDATE edge.channel SET lastdata='" + Util.ascdatetime2(start - 600000).substring(0, 19) + "' WHERE channel IN (");
              sbLastDataList.append(");\n");
              if(lastDataSave % 10 == 0 && Alarm.isNoDB()) {
                try {
                  prta("SNWC: write snwstation table latency!");
                  dbaccess.getSNWStationDBTable().writeTable();
                }
                catch(IOException e) {
                  prta("SNWC: *** exception creating lastData in snwstation table e="+e);
                  
                }
              }   // we changed channel, so let the DBTable know.
              if(!Alarm.isNoDB()) dbaccess.getChannelDBTable().setUpdateOccurred(true);
              queueDBMsg(sbLastDataList);
            }

            // Process the bad rates changes.
            if (sbrate.length() > 10) {
              prta(Util.clear(runsb).append("SNWCC: rate change message - ").append(sbrate.toString()));
              SimpleSMTPThread.email(Util.getProperty("emailTo"), "_Change of channel rate " + Util.getNode()
                      + " " + Util.getSystemName() + " " + Util.getAccount() + " " + Util.getProcess(),
                      Util.ascdate() + " " + Util.asctime()
                      + " This comes from SNWChannelClient inUdpChannel when rates change on " + Util.getNode() + " " + Util.getSystemName()
                      + "\n\nChannel       ESC  new  Rate\n" + sbrate);
              if (Util.getProperty("RateChangeEmailTo") != null) {
                SimpleSMTPThread.email(Util.getProperty("RateChangeEmailTo"),
                        "_Change of channel rate " + Util.getNode() + " " + Util.getSystemName() + " " + Util.getAccount() + " " + Util.getProcess(),
                        Util.ascdate() + " " + Util.asctime()
                        + " This comes from SNWChannelClient inUdpChannel when rates change on " + Util.getNode() + " " + Util.getSystemName()
                        + "\n\nChannel       ESC  new  Rate\n" + sbrate);
              }
            }
            lastRateCheck = start;
          }   // last data and rate check time
          state = 27;

        } // if channels not empty
        end = System.currentTimeMillis();
        try {
          long sleepTime = (60000L - (end - start));
          if (sleepTime < 45000L && sleepTime >= 0) {
            prta("SNWC: wait short " + sleepTime + " " + nsnw + " " + nnew);
          }
          if (sleepTime < 0L) {
            state = 28;
            prta("SNWC: ** neg wait on " + Util.getNode() + " " + (sleepTime / 1000) + " s");
            if (sleepTime < -90000) {
              SendEvent.debugSMEEvent("SNWChSendNeg", "Neg wait on " + Util.getNode() + " " + (sleepTime / 1000) + " s", this);
            }
          }
          state = 29;
          sleep(Math.max(1L, sleepTime));
        } catch (InterruptedException expected) {
        }
        state = 30;
        //prta("SNWC: Out of wait ");
      } catch (RuntimeException e) {
        prta("SNWC: ** caught a RuntimeException e=" + e.getMessage());
        prt("SNWC: * Station=" + station + ":");
        if(par != null) {
          e.printStackTrace(par.getPrintStream());
        }
        if (System.currentTimeMillis() - lastPage > 300000) {
          lastPage = System.currentTimeMillis();
          SimpleSMTPThread.email(Util.getProperty("emailTo"), "SNWChannelClient got runtime exception " + Util.getNode() + " e=" + e,
                  Util.asctime() + " " + Util.ascdate() + " " + Util.getNode()
                  + " This generally comes from the SNWCHannelClient in UdpChannel\n");
          SendEvent.edgeSMEEvent("SNWCHRuntime", "SNWChannelClient got runtime exception node=" + Util.getNode() + " e=" + e,
                  this);
          if (par != null) {
            e.printStackTrace(par.getPrintStream());
          } else {
            e.printStackTrace();
          }
        }
      }
      state = 31;
    }
    prta("SNWC:  has terminated");

  }

  class CheckRate {

    double rate;
    int nmsg;

    @Override
    public String toString() {
      return "#=" + nmsg + " rate=" + rate;
    }

    public CheckRate(double rt) {
      rate = rt;
      nmsg = 0;
    }

    public void clear() {
      nmsg = 0;
    }

    /**
     * add a rate to the tracking, if it is stable return true;
     *
     * @param rt The current rate
     */
    public boolean add(double rt) {
      if (rate / rt > 0.98 && rate / rt < 1.02) {
        nmsg++;
        return nmsg > 2;// DEBUG > 20
      }
      nmsg = 0;
      rate = rt;
      return false;
    }
  }

  public static void main(String[] args) {
    Util.init();
    Util.loadProperties(UC.getPropertyFilename());
    Util.setModeGMT();
    Util.prt("starting argc=" + args.length);
    EdgeChannelServer ecs = new EdgeChannelServer("-empty", "ECS");
    while (!EdgeChannelServer.isValid()) {
      Util.sleep(100);
    }
    Util.setNoInteractive(true);
    SNWChannelClient SNWC = new SNWChannelClient(null, Util.getProperty("StatusServer"), true, null);
  }
}
