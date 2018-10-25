/* This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.alarm;

import gov.usgs.anss.channelstatus.ChannelStatus;
import gov.usgs.anss.edge.EdgeProperties;
import gov.usgs.anss.edgemom.EdgeChannelServer;
import gov.usgs.anss.edgethread.EdgeThread;
import gov.usgs.anss.edgethread.MemoryChecker;
import gov.usgs.anss.edgethread.MonitorServer;
import gov.usgs.anss.util.Util;
import gov.usgs.anss.util.AnssPorts;
import gov.usgs.edge.config.Channel;
import gnu.trove.iterator.TLongObjectIterator;
import gov.usgs.anss.net.ServerThread;
import gov.usgs.anss.net.UdpProcess;

/**
 * This program creates a UDP listener for Channel data using the UDPProcess class. The list of
 * latest Channel objects is served to processes needing this information via a ServerThread class
 * (That is user processes can connect and get data from the ServerThread by using a
 * StatusSocketReader on the served port.
 * <PRE>
 * -web       Start a WebStationCLient to keep USGS operational web page status up-to-date (NEIC only)
 * -snw       Start a SNWChannelClient to check for rate changes, new stations and channels and update database
 * -snwsender Send information to SNW Server from the SNWChannelClient (i.e. you are running a SNW server and need flow data)
 * -mod50     Set the modulus for lastdata updates to 50 (def=20) - the lastdata will be updated about every modulus*3 minutes
 * </PRE>
 */
public final class UdpChannel extends EdgeThread {

  public static boolean isShuttingDown = false;
  private final ServerThread server;
  private final UdpProcess t;

  //DBConnectionThread dbconnedge;
  //MemoryChecker memoryChecker;
  /**
   * return the monitor string for ICINGA
   *
   * @return A String representing the monitor key value pairs for this EdgeThread
   */
  @Override
  public StringBuilder getMonitorString() {
    if (monitorsb.length() > 0) {
      monitorsb.delete(0, monitorsb.length());
    }
    monitorsb.append(MemoryChecker.getMonitorString());
    if (snwcc != null) {
      monitorsb.append(snwcc.getMonitorString());
    }
    monitorsb.append("UDPCSenders=").append(t.getNumberOfSenders()).append("\n");
    monitorsb.append("UDPCcount=").append(t.getNumberOfUDP()).append("\n");
    monitorsb.append("UDPCNmsgs=").append(t.getNumberOfMessages()).append("\n");
    return monitorsb;
  }

  @Override
  public StringBuilder getStatusString() {
    return statussb;
  }

  @Override
  public StringBuilder getConsoleOutput() {
    return consolesb;
  }

  @Override
  public void terminate() {
  }

  public boolean isShuttingDown() {
    return isShuttingDown;
  }

  public UdpChannel(String argline, String tg) {
    super(argline, tg);
    prta(Util.ascdate());

    String[] args = argline.split("\\s");
    int monOffset = 0;
    for (int i = 0; i < args.length; i++) {
      if (args[i].equals("-instance")) {
        if (args[i + 1].equals("^")) {
          monOffset = 0;
        } else {
          if (args[i + 1].contains("#")) {
            monOffset = Integer.parseInt(args[i + 1].substring(args[i + 1].indexOf("#") + 1));
          } else {
            monOffset = Integer.parseInt(args[i + 1]);
          }
        }
        i++;
      } else if (args[i].length() > 0) {
        if (args[i].substring(0, 1).equals(">")) {
          break;
        }
      }
    }
    MonitorServer monitor = new MonitorServer(monOffset < 100 ? AnssPorts.MONITOR_UDPCHANNEL_PORT + monOffset : monOffset, this);
    prta("Open server port " + AnssPorts.CHANNEL_SERVER_PORT);
    server = new ServerThread(AnssPorts.CHANNEL_SERVER_PORT, false, this);
    server.setMaxBuffers(250000);
    //memoryChecker = new MemoryChecker(600, this);
    prt("Open UDP receiver port " + AnssPorts.CHANNEL_UDP_PORT);
    t = new UdpProcess(ChannelStatus.class, AnssPorts.CHANNEL_UDP_PORT, server, false, this);
    t.setParent((UdpChannel) this);

    TLongObjectIterator<Channel> itr = EdgeChannelServer.getIterator();
    int i = 0;
    while (itr.hasNext()) {
      itr.advance();
      Channel c = itr.value();
      if (c.getExpected()) {
        t.forceKey((c.getChannel() + "   ").substring(0, 12));
        //prt(i+" Force "+c.getChannel()); 
        i++;
      }
    }
    prta(i + " # stations forced.");

    // We have started the ServerThread and the process thread.  All we need to do is monitor
    Runtime.getRuntime().addShutdownHook(new ShutdownUdpChannel());
    t.start();
    start();
  }

  @Override
  public void run() {
    int lastNumberMessages = 0;
    //int lastState=0;
    int nfailed = 0;
    int loop = 0;
    int sleepms = (Alarm.isNoDB() ? 30000 : 3000);
    while (true) {
      try {
        sleep(sleepms);
        if (loop++ % 10 == 9) {
          prta("UdpChannel : thr.state=" + t.getState() + " state=" + t.getState2() + " whohas=" + t.getWhoHas() + " totmsg=" + t.getNumberOfMessages()
                  + " nmsgs=" + t.getNumberOfUDP() + " nsenders=" + t.getNumberOfSenders() + " alive=" + t.isAlive() + "\n"
          //+t.getServerWriteStatus()
          );
          //prt(DBConnectionThread.getStatus());
        }
        if (lastNumberMessages == t.getNumberOfMessages() || t.getState().compareTo(State.BLOCKED) == 0) {
          nfailed++;
          prta("UdpChannel : messages same or BLOCKED nfailed=" + nfailed + " state=" + t.getState()
                  + " msg=" + lastNumberMessages + "/" + t.getNumberOfMessages());
          if (nfailed > 5) {
            SendEvent.edgeSMEEvent("UdpChStuck", "No new messages or thread BLOCKED, exiting", "UdpChannel");
          }
        } else {
          nfailed = 0;
        }
        lastNumberMessages = t.getNumberOfMessages();
      } catch (InterruptedException e) {
        prt("DebugTimer interupted");
      }
    }
  }

  private final class ShutdownUdpChannel extends Thread {

    public ShutdownUdpChannel() {

    }

    /**
     * this is called by the Runtime.shutdown during the shutdown sequence cause all cleanup actions
     * to occur.
     */
    @Override
    public void run() {
      /*SimpleSMTPThread.email(Util.getProperty("emailTo"),"SHUTDOWN:UdpChannel on "+
              Util.getNode()+"/"+Util.getSystemName()+" is Shuttingdown",
          "This is called whenever this Jar ShutsDown()");*/
      SendEvent.edgeSMEEvent("UdpChnShtdwn", "UpdChannel shutdown on " + Util.getNode() + "/" + Util.getSystemName(), "UdpChannel");

      isShuttingDown = true;
    }
  }
  public static SNWChannelClient snwcc;
  public static UdpChannel d;
  public static LatencyServer ls;
  public static WebStationClient wsc;

  public static void main(String[] args) {
    //Util.init();    
    Util.setProcess("UdpChannel");
    EdgeProperties.init();
    Util.setModeGMT();
    EdgeThread.setMainLogname("udpchannel");   // This sets the default log name in edge thread (def=edgemom!)
    Util.setNoconsole(false);      // Mark no dialog boxes
    Util.setNoInteractive(true);
    Util.prta("Starting UdpChannel on " + Util.getNode());
    boolean noWeb = true;
    boolean noSNW = true;
    boolean noSNWSender = true;
    int mod = -1;
    String argline = "";
    for (String arg : args) {
      argline += arg + " ";
      if (arg.equals("-snw")) {
        noSNW = false;
      }
      if (arg.equals("-web")) {
        noWeb = false;
      }
      if (arg.equals("-snwsender")) {
        noSNWSender = false;
      }
      if (arg.startsWith("-mod")) {
        mod = Integer.parseInt(arg.substring(4));
      }
      if (arg.equals("-?")) {
        Util.prt("-web       Send status information to the operational web server");
        Util.prt("-snw       SNW processing for new channels, snwstations to database, but no SNW updates without -snwsender");
        Util.prt("-snwsender SNW processing for new channels, snwstation to database and send SNW server updates");
        Util.prt("-mod40     Change modulus of lasltdata updates in SNW to 40 def=20");
      }
    }
    //if(DBConnectionThread.noDB) {noSNW=true; noWeb=true;}
    EdgeChannelServer echn = new EdgeChannelServer("-empty >> echnudpchan", "Echan");
    while (!EdgeChannelServer.isValid()) {
      try {
        Thread.sleep(100);
      } catch (InterruptedException expected) {
      }
    }
    Util.prt("Starting UDPChannel log area =" + Util.getProperty("logfilepath")
            + " nosnw=" + noSNW + " noweb=" + noWeb + " noSNW sender=" + noSNWSender + " argline=" + argline);
    d = new UdpChannel(argline + " >>udpchannel", "UDPCH");
    Util.sleep(5000);    // wait for UdpChannel to start.
    if (!noSNW) {
      Util.prt("Staring SNWChannelClient2 noSNWSender=" + noSNWSender);
      snwcc = new SNWChannelClient(null, "localhost", noSNWSender, d);   // This taps the UdpChannel and sends
      if (mod > 0) {
        snwcc.setLastDataModulus(mod);
      }
      ls = new LatencyServer(7956);
    }
    if (!noWeb) {
      Util.prta("Starting WebStationClient");
      wsc = new WebStationClient(snwcc.getStatusSocketReader(), d);
    }
    int loop = 0;
    int loopWSC = -1;
    int lastSNWCCState = -1;
    int badSNWCC = 0;
    for (;;) {
      for (int i = 0; i < 60; i++) {
        try {
          Thread.sleep(1000);
        } catch (InterruptedException expected) {
        }
        if (d.isShuttingDown()) {
          break;
        }
      }
      if (d.isShuttingDown()) {
        break;
      }
      if (!noWeb) {
        boolean restart = false;
        if (loop % 10 == 0) {
          if (loopWSC == wsc.getLoop()) {
            restart = true;
            Util.prt(Util.ascdate() + "WebStationClient does not seem to be looping-restart it");
          }
          loopWSC = wsc.getLoop();
        }
        if (!wsc.isAlive() || restart) {
          Util.prta(Util.ascdate() + " WebStationClient seems to be dead.  Restart it ");
          SendEvent.debugEvent("WebStaClDown", "WebStationClient found down in UdpChannel", "UdpChannel");
          wsc = new WebStationClient(snwcc.getStatusSocketReader(), d);
        }
      }
      if (!noSNW) {
        if (!snwcc.isAlive()) {
          Util.prta(Util.ascdate() + " SNWChannelClient seems to be dead.  Restart it ");
          SendEvent.debugEvent("SNWCCDown", "SNWChannelCLient found down in UdpChannel", "UdpChannel");
          snwcc = new SNWChannelClient(null, "localhost", noSNWSender, d);
          if (ls != null) {
            ls.terminate();    // stop the ls since the reader is nolonger good.
          }
          if (wsc != null) {
            wsc.terminate();
          }
        }
        // Check on the latency server
        if (!ls.isAlive()) {
          Util.prta(Util.ascdate() + " LatencyServer seems to be dead.  Restart it ");
          Util.prt(Util.getThreadsString());
          SendEvent.debugEvent("LatSrvDown", "LatencyServer found down in UdpChannel", "UdpChannel");
          if (snwcc == null) {
            ls = new LatencyServer(7956);
          } else {
            ls = new LatencyServer(7956);
          }
        }
      }

      loop++;
      if (loop % 10 == 0) {
        Util.prta((!noWeb ? wsc.toString() + " alive=" + wsc.isAlive() : "") + " " + (!noSNW ? snwcc.toString() + " alive=" + snwcc.isAlive() : ""));
      }
      if (!noSNW) {
        if (snwcc.getCurrentState() != 29 && snwcc.getCurrentState() == lastSNWCCState) {
          badSNWCC++;
          if (badSNWCC > 5) {
            Util.prta(" **** SNWCC detected dead state=" + snwcc.toString());
            SendEvent.debugEvent("UdpCSNWCDead", "SNWChannel client detected dead.  Try to restart", "UdpChannel");
            snwcc.terminate();
            snwcc = new SNWChannelClient(null, "localhost", noSNWSender, d);
          }
        } else {
          badSNWCC = 0;
        }
      }
    }
    if (wsc != null) {
      wsc.terminate();
    }
    if (snwcc != null) {
      snwcc.terminate();
    }
    if (ls != null) {
      ls.terminate();
    }
    Util.prta(" UdpChannel.main() is exiting!");
  }

}
