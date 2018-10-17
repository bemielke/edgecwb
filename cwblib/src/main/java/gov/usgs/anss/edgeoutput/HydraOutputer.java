/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.edgeoutput;

import gov.usgs.alarm.SendEvent;
import gov.usgs.anss.edge.IllegalSeednameException;
import gov.usgs.anss.edge.IndexFile;
import gov.usgs.anss.edge.TimeSeriesBlock;
import gov.usgs.anss.edgemom.EdgeChannelServer;
import gov.usgs.anss.edgemom.LHDerivedToQueryMom;
import gov.usgs.anss.edgemom.Hydra;
import gov.usgs.anss.edgethread.EdgeThread;
import gov.usgs.anss.filter.LHDerivedProcess;
import gov.usgs.anss.filter.LHOutputer;
import gov.usgs.anss.seed.MiniSeed;
import gov.usgs.anss.seed.Steim1;
import gov.usgs.anss.seed.Steim2;
import gov.usgs.anss.seed.SteimException;
import gov.usgs.anss.util.Util;
import gov.usgs.edge.config.Channel;
import java.io.IOException;
import java.net.*;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.GregorianCalendar;

/**
 * This class is a ChannelInfrastructureSubscriber which forwards data to Hydra
 * via UDP broadcast TraceBuf packets. It accepts both MiniSeed and TraceBufs
 * from the ChannelHolders, processes them to correct TraceBufs and puts them in
 * a queue for broadcast. An initter class HydraOutputThread dequeues the data,
 * applies an bandwidth limiter, and sends out the UDP packets.
 * <p>
 * An interesting bit is that this class is generally created as a default
 * subscriber to ChannelHolder. Hence it is configured by a static method call
 * to "setCommandLine()" which setup which allows a configuration to be done
 * once before the ChannelInfrastructure is started an any object of this type
 * are created. This class is always instantiated by the setup of a Hydra
 * object.
 * <br>
 * <PRE>
 * *switch   arg       Description
 *-dbg               Turn debug output on
 *-host   nn.nn.nn   IP address to transmit UDP to (should be a broadcast address def=192.168.18.255) (HydraOutput param)
 *-mhost  nn.nn.nn   The IP address to bind this UPD transmission to on this host (def=edge?-hydra.cr.usgs.gov)(HydraOutput param)
 *-port   pppp       Target output port number (def=40010) (HydraOutput param)
 *-aport   pppp      Target output port number for array data(def=40020) (HydraOutput param)
 *-mport  pppp       Local system UDP transmitter port to bind (def=40005)(HydraOutput param)
 *-gpport pppp       Local system UDP transmitter port to bind for GlobalPicks(def=40080)(HydraOutput param)
 *-module nn         Earthworm Module ID (def=2) (HydraOutput param)
 *-inst   ii         Earthworm institution ID (def=13 USGS)(HydraOutput param)
 *-wait   nn         Wait time in seconds for OutputInfrastructer (def=180) (HydraOutput param)
 *-qsize  nnnn       Number of message to allow in Hydra Input queue (def=10000)(HydraOutput param)
 *-msgmax nnnn       Message size max (def=1500) (HydraOutput param)
 * -bw    nn         The bandwidth limit on output in megabits/s (default 8)
 *-ooroffset nn      Offset to selected port for out-of-order data (zero do not send oor data)
 * -oordbg           If set turn on logging for oor data to oor offset
 * -noderiveLH       If set, no derived LH is allowed
 *-dbgch  seedname   ChannelHolder channel to do debug printing (passed to ChannelHolder)
 *-blockoutput       Do not send any actual output (HydraOutput param)
 * -deriveLHONLY     Debugging flag to send deriveLH only
 * -derivedLHPort pp Set the derived LH port and turn on deriving LH data (default=0 and no derived LH)
 * -derive1sec       Allow channels that are in the one sec protocol, to create b and h data from MiniSEED packets
 *                   This is used to send derive such data on a node that does not have access to the 1 seconds packets but only
 *                   MiniSEED packets
 * -scn              If present, use SCN tracebufs 1.0 instead of the default SCNL tracebuf 2.0
 * </PRE>
 *
 * @author davidketchum
 */
public final class HydraOutputer extends ChannelInfrastructureSubscriber implements LHOutputer {

  static final long ARRAY_MASK = 256; // This is the mask for the FLAG HydraArrayPort in flags table
  private static DecimalFormat df1 = new DecimalFormat("0.0");
  private LHDerivedProcess deriveLH;        // if derive LH is being done, this is the processing class for LP filter and decimation
  private TraceBuf tblh;                    // scratch tracebuf for LH data, only not null if derired is on
  private StringBuilder seednameLH = new StringBuilder(12);
  private final GregorianCalendar desired = new GregorianCalendar();       // next desired time
  private final GregorianCalendar earliest = new GregorianCalendar();       // start time of last packet received
  private final String seedname;
  private final StringBuilder seednameSB = new StringBuilder(12);
  private final int waitTime;
  private int npacket;
  private int npacketOOR;
  private int npacketArray;
  private int npacketNormal;
  private int npacketArrayOOR;
  private int npacketDLH;
  private int npacketDLHOOR;
  private byte[] frames;
  private TraceBuf tb;
  private final int pinno;
  private double rate;
  private long lastTime;          // This is the time of the end of the last packet sent
  private int rateDisableCount;
  private long rateDisableTime;
  private String rateMessage;

  // This static area is for all of the queues
  private static int queueSize = 10000;
  private static int queueMsgMax;
  private static final Integer queueMutex = Util.nextMutex();
  private static ArrayList<byte[]> queue;
  private static int[] length;
  private static int[] outOfOrder;
  private static int nextin;
  private static int nextout;
  private static long nenqueue;
  private static long lastNenqueue;
  private static int ndiscards;
  private static int maxUsed;
  private static int defaultWait;
  private static HydraOutputThread thr;
  private static int seq;
  private static int pinnoNext;
  private static int institution = 13;
  private static int module = 0;
  private static String commandArgs;
  private static boolean scn;
  private static String host;         // The destination address (normally a network broadcast address)
  private static int port;         // Destination port for the broadcast UDP packets (non -array)
  private static int arrayPort;       // Destination port for broadcast UDP packets (arrays)
  private static int oorOffset;       // Offset to add to port for OOR data, if zero, disable OOR data
  private static boolean oordbg;
  private static boolean noDeriveLH;       // If true, no OOR processing can be done or is desired.
  private static int derivedLHPort;   // Destination port for broadcast UDP Packets (arrays)
  private static boolean derive1sec;  // 1 second channels are to be converted from MiniSEED
  private static int globalPickPort;  // The destination port if this is a GlobalPickFormated data
  private static String mhost;        // The local host IP (ties to an interface normally)
  private static int mport;        // The port to bind for this process, if zero an ethereal port its used)
  private MiniSeed ms;
  private static int throttleMS;    // each 50k bytes must take this amount of time, defualt of 8 mbit/s is 50 ms wait

  private static boolean dbg;
  private static boolean blockOutput;
  private static boolean deriveLHONLY;
  private static long npackStatus;
  private static EdgeThread par;    // the parent edge thread for output purposes.
  private static StringBuilder tmpsb = new StringBuilder(100);

  public static long getNpacketStatus() {
    return npackStatus;
  }

  public static void setParent(EdgeThread parent) {
    par = parent;
  }

  @Override
  public void shutdown() {
    prta("HO: for " + seedname + " is shutingdown");
    thr.shutdown();
  }

  private static void prt(String a) {
    if (par == null) {
      Util.prt(a);
    } else {
      par.prt(a);
    }
  }

  private static void prta(String a) {
    if (par == null) {
      Util.prta(a);
    } else {
      par.prta(a);
    }
  }

  private static void prt(StringBuilder a) {
    if (par == null) {
      Util.prt(a);
    } else {
      par.prt(a);
    }
  }

  private static void prta(StringBuilder a) {
    if (par == null) {
      Util.prta(a);
    } else {
      par.prta(a);
    }
  }

  public static String getCommandArgs() {
    return commandArgs;
  }
  private StringBuilder dbmsg = new StringBuilder(10);
  static String dbgSeedname = "ZZZZZZ";

  /**
   * hydra requires strictly increasing packets, do not allow overlaps
   *
   * @return alway false
   */
  @Override
  public boolean getAllowOverlaps() {
    return false;
  }

  /**
   * This processes a command line for a HydraOutputer - generally this is
   * called as part of the EdgeThread parents set up to change or set the
   * parameters for all HydraOutputers created by the ChannelHolders
   *
   * @param s The command line string
   */
  public synchronized static void setCommandLine(String s) {
    int gt = s.indexOf(">");
    if (gt > 0) {
      commandArgs = s.substring(0, gt);
    } else {
      commandArgs = s;
    }
    host = "192.168.18.255";    // broadcast address
    mhost = Util.getSystemName() + "-hydra.cr.usgs.gov";     // local host address (selects interface!)
    mport = 40005;                // local host port
    port = 40010;
    arrayPort = 40020;
    globalPickPort = 40080;
    derivedLHPort = 0;
    String[] args2 = commandArgs.split("\\s");
    int narg = 0;
    defaultWait = 180;
    queueMsgMax = 1500;
    queueSize = 10000;
    noDeriveLH = false;
    throttleMS = 50;
    dbg = false;
    scn = false;
    String[] args = new String[args2.length];
    if (ChannelInfrastructure.getParent() != null) {
      par = ChannelInfrastructure.getParent();    // Get the parent thread for loggin purposes
    }
    for (String args21 : args2) {
      args[narg] = args21.trim();
      if (!args[narg].equals("")) {
        narg++;
      }
    }

    for (int i = 0; i < narg; i++) {
      if (args[i].equals("-dbg")) {
        dbg = true;
      } else if (args[i].equalsIgnoreCase("-host")) {
        host = args[i + 1];
        i++;
      } else if (args[i].equalsIgnoreCase("-mhost")) {
        mhost = args[i + 1];
        i++;
      } else if (args[i].equalsIgnoreCase("-port")) {
        port = Integer.parseInt(args[i + 1]);
        i++;
      } else if (args[i].equalsIgnoreCase("-mport")) {
        mport = Integer.parseInt(args[i + 1]);
        i++;
      } else if (args[i].equalsIgnoreCase("-aport")) {
        arrayPort = Integer.parseInt(args[i + 1]);
        i++;
      } else if (args[i].equalsIgnoreCase("-gpport")) {
        globalPickPort = Integer.parseInt(args[i + 1]);
        i++;
      } else if (args[i].equalsIgnoreCase("-ooroffset")) {
        oorOffset = Integer.parseInt(args[i + 1]);
        i++;
      } else if (args[i].equalsIgnoreCase("-dlhport")) {
        derivedLHPort = Integer.parseInt(args[i + 1]);
        i++;
      } else if (args[i].equalsIgnoreCase("-derive1sec")) {
        derive1sec = true;
      } else if (args[i].equalsIgnoreCase("-module")) {
        module = Integer.parseInt(args[i + 1]);
        i++;
      } else if (args[i].equalsIgnoreCase("-inst")) {
        institution = Integer.parseInt(args[i + 1]);
        i++;
      } else if (args[i].equalsIgnoreCase("-wait")) {
        defaultWait = Integer.parseInt(args[i + 1]);
        i++;
      } else if (args[i].equalsIgnoreCase("-qsize")) {
        queueSize = Integer.parseInt(args[i + 1]);
        i++;
      } else if (args[i].equalsIgnoreCase("-msgmax")) {
        queueMsgMax = Integer.parseInt(args[i + 1]);
        i++;
      } else if (args[i].equalsIgnoreCase("-dbgch")) {
        dbgSeedname = args[i + 1].replaceAll("_", " ");
        ChannelHolder.setDebugSeedname(args[i + 1]);
        i++;
      } else if (args[i].equalsIgnoreCase("-blockoutput")) {
        blockOutput = true;
      } else if (args[i].equalsIgnoreCase("-deriveLHONLY")) {
        deriveLHONLY = true;
      } else if (args[i].equalsIgnoreCase("-oordbg")) {
        oordbg = true;
      } else if (args[i].equalsIgnoreCase("-noderiveLH")) {
        noDeriveLH = true;
      } else if (args[i].equalsIgnoreCase("-bw")) {
        double mb = Double.parseDouble(args[i + 1]);
        throttleMS = (int) (400 / (Math.max(8., mb)));
        prt(Util.clear(tmpsb).append("Set bandwidth=").append(mb).append(" Mb/s").append(" throttleMS=").append(throttleMS));
        i++;
      } else if (args[i].equalsIgnoreCase("-scn")) {
        scn = true;
      } else if (args[i].equalsIgnoreCase("-state")) {
        i++;
      } else if (args[i].equalsIgnoreCase("-empty")) ; else {
        prt(Util.clear(tmpsb).append("Unknown arg to HydraOutputer = ").append(args[i]));
      }
    }
    if (module == 0) {
      String node = IndexFile.getNode();
      // either [1-9] or [1-9]#[1=9] or empty string is possible
      switch (node.length()) {
        case 0:
          module = 9;
          break;
        case 1:
          module = (node.charAt(0) - '0') * 10;
          break;
        default:
          node = node.replaceAll("#", "");
          module = Integer.parseInt(node);
          break;
      }
      prta(Util.clear(tmpsb).append("Module not set on command line.  Computed to be ").append(module));
    }
    //new RuntimeException("TraceBack creator of Hydra!").printStackTrace();
    prta(Util.clear(tmpsb).append("HO: setup host=").append(host).append("/").append(port).
            append(" mhost=").append(mhost).append("/").append(mport).append(" wait=").append(defaultWait).
            append(" qsize=").append(queueSize).append("/").append(queueMsgMax).
            append(" inst=").append(institution).append(" mod=").append(module).
            append(" blockout=").append(blockOutput).append(" dbg=").append(dbgSeedname).
            append("|" + " dbg=").append(dbg).append(" scn=").append(scn).append(" ooroffset=").append(oorOffset).
            append(" throtms=").append(throttleMS));
    prta(Util.clear(tmpsb).append("HO: deriveLHport=").append(derivedLHPort).
            append(" noDeriveLP=").append(noDeriveLH).append(" LHOny=").append(deriveLHONLY));
    //queue = new ArrayList<byte []>(queueSize);
    queue = new ArrayList<byte[]>(queueSize);
    for (int i = 0; i < queueSize; i++) {
      queue.add(i, new byte[queueMsgMax]);
    }
    nextin = 0;
    nextout = 0;
    ndiscards = 0;
    length = new int[queueSize];
    outOfOrder = new int[queueSize];
  }
  private static final StringBuilder queueSum = new StringBuilder(100);

  /**
   * return String with state of the queue
   *
   * @return The String status of the queue
   */
  static public StringBuilder queueSummary() {
    Util.clear(queueSum).append("HO: in=").append(nextin).append(" out=").append(nextout).
            append(" used=").append(getNused()).append(" #discard=").append(ndiscards).
            append(" maxused=").append(maxUsed).append(" #enq=").append(nenqueue).
            append(" size=").append(queueSize).append(" max=").append(queueMsgMax).
            append(" ooroffset=").append(oorOffset);
    return queueSum;
  }
  /* static public void setupDefaultParameters(int wait, int qSize, int msgMax) {
    defaultWait = wait;
    queueSize = qSize;
    queueMsgMax = msgMax;
    queue = new ArrayList<byte []>(queueSize);
    for(int i=0; i<queueSize; i++) queue.add(i, new byte[queueMsgMax]);
    length = new int[queueSize];
    nextin=0;
    nextout=0;
    ndiscards=0;
   
  }*/
  private StringBuilder rejectLH = new StringBuilder(100);

  private void doDeriveLHSetup(Channel chn) {
    if (derivedLHPort == 0) {
      return;
    }
    if (rejectLH.indexOf(seedname) >= 0) {
      return;     // previously rejected
    }
    if ((seednameSB.charAt(7) == 'B' || seednameSB.charAt(7) == 'H') && seednameSB.charAt(8) == 'H'  && rate <= 101.) {
      //if(seedname.substring(7,9).equals("BH") || seedname.substring(7,9).equals("HH")) {
      if (chn != null) {
        if (!derive1sec && (chn.getProtocolID() == 11 || chn.getProtocolID() == 16)) {
          if (npacket % 10000 == 0) {
            prt(Util.clear(tmpsb).append("reject derived - one second ").append(seedname).
                    append(" **** disabled!!!").append(npacket));
            rejectLH.append("|").append(seedname);
          }
          deriveLH = null;
          return;
        }
        // one seconds data already
        if ((chn.getFlags() & Channel.CHANNEL_FLAGS_HAS_METADATA) == 0) {
          prt(Util.clear(tmpsb).append("DLH: reject derived - no metadata ").
                  append(seedname).append(" ").append(chn.getProtocolID()));
          rejectLH.append("|").append(seedname);
          deriveLH = null;
          return;
        }    // it has not metadata so whats the point
        if (seednameSB.charAt(7) == 'H') {
          seednameSB.replace(7, 8, "B");
          Channel chnbh = EdgeChannelServer.getChannelNoTraceback(seednameSB);  // May return null
          seednameSB.replace(7, 8, "H");
          if (chnbh != null) {
            if (System.currentTimeMillis() - chnbh.getLastData().getTime() < 864000000L) { // data in last 10 days?
              prt(Util.clear(tmpsb).append("DLH: reject derived - has recent BH data ").append(seedname));
              rejectLH.append("|").append(seedname);
              deriveLH = null;
              return;
            }
          }
        }
        if ((chn.getFlags() & Channel.CHANNEL_FLAGS_DERIVELH_DISABLE) != 0) {
          prt(Util.clear(tmpsb).append("DLH: reject derived - deriveLH is disabled ").
                  append(seedname).append(" ").append(chn.getProtocolID()));
          rejectLH.append("|").append(seedname);
          deriveLH = null;
          return;
        }   // it has not metadata so whats the point
        if (deriveLH == null) {
          Util.clear(seednameLH).append(seedname);
          char band = seednameLH.charAt(7);    // Change to little b or little h
          band = Character.toLowerCase(band);
          seednameLH.delete(7, 8).insert(7, band);
          try {
            deriveLH = new LHDerivedProcess((int) (rate + 0.000001), seedname, par);
            deriveLH.setOutputer(this);
            if (seedname.contains(dbgSeedname)) {
              deriveLH.setDebug(true);
            }
            tblh = new TraceBuf();
          } catch (RuntimeException e) {
            prta(Util.clear(tmpsb).append("DLH: ").append(seedname).append(" starting up channel Run time - bad rate? ").append(rate));
            deriveLH = null;
          }
        }
      }
    } else {
      deriveLH = null;
    }
  }

  private void processDerived(TraceBuf tb) {
    if (noDeriveLH) {
      return;
    }
    // Every 100 input packets packets (about 10-20 minutes) check to see if this channel is still supposed to process
    if (npacket % 100 == 0) {
      Channel chn = EdgeChannelServer.getChannel(seedname);
      if (chn != null) {
        doDeriveLHSetup(chn);
      }
    }
    if (deriveLH != null) {
      deriveLH.processToLH(tb);
    }
  }

  /**
   * this is required by the LHOutputer interface
   *
   * @param time Time as a millisecond
   * @param chan Channel name
   * @param data The data array to send on
   * @param nsamp The number of samples in data
   */
  @Override
  public void sendOutput(long time, StringBuilder chan, int[] data, int nsamp) {
    if (nsamp <= 0) {
      return;
    }
    tblh.setData(seednameLH, time, nsamp, 1., data, 0, institution, module, 0, pinno);
    if (dbg) {
      prt("DLH : send LH TB " + tblh);
    }
    LHDerivedToQueryMom.sendDataQM(seednameLH, time, 1., nsamp, data);
    try {
      if (Hydra.checkInorder(tblh)) {
        queue(tblh.getBuf(), TraceBuf.TRACE_HDR_LENGTH + nsamp * 4, 0);
      } else {
        queue(tblh.getBuf(), TraceBuf.TRACE_HDR_LENGTH + nsamp * 4, 1);
      }
    } catch (RuntimeException e) {
      if (e.getMessage().contains("Packet from future")) {
        throw e;
      }
    }
  }

  public HydraOutputer(StringBuilder seed, int wait, long startTimeMS, double rt) {
    this(seed.toString(), wait, startTimeMS, rt);
  }

  /**
   * Creates a new instance of HydraOutputer
   *
   * @param seed A 12 character seed name
   * @param wait The length of time this outputer is willing to wait for
   * complete data
   * @param startTimeMS A Millisecond time (like for GregorianCalendar) of the
   * start time desired.
   * @param rt The data rate in
   */
  public HydraOutputer(String seed, int wait, long startTimeMS, double rt) {
    if (host == null) {
      for (int i = 0; i < 30; i++) {// Alow the Hydra thread some time to start
        Util.sleep(1000);
        if (host != null) {
          prta(Util.clear(tmpsb).append("HO: Hydra thread started during wait after ").append(i).append(" secs.   Whew...."));
          break;
        }
      }
      if (host == null) {
        prta(Util.clear(tmpsb).append("HO: WARNING:  ***** There is no Hydra thread - using default setup!!!!!"));
        setCommandLine("");// If user has not set these parameters - set them to defaults
      }
    }// If user has not set these parameters - set them to defaults
    seedname = seed;
    Util.clear(seednameSB).append(seed);
    while (seednameSB.length() < 12) {
      seednameSB.append(" ");
    }
    waitTime = wait;
    desired.setTimeInMillis(startTimeMS);
    earliest.setTimeInMillis(startTimeMS);
    prta(Util.clear(tmpsb).append("HO: looking to start a HydraOutputThread =").append(thr));
    synchronized (queueMutex) {
      if (thr == null) {
        thr = new HydraOutputThread();
        prta(Util.clear(tmpsb).append("HO: start an output thread mhost=").append(mhost).
                append("/").append(mport).append(" host=").append(host).append("/").append(port));
      }
    }
    pinno = ++pinnoNext;
    tb = new TraceBuf(new byte[TraceBuf.TRACE_ONE_BUF_LENGTH]); // reserve space for one tracebuf
    tb.setSCNMode(scn);
    Channel chn = EdgeChannelServer.getChannel(seedname);
    if (chn != null) {
      rate = chn.getRate();
      doDeriveLHSetup(chn);
    } else {
      prta(Util.clear(tmpsb).append("HO: cannot find rate for channel=").append(seedname));
      EdgeChannelServer.createNewChannel(seednameSB, rt, (HydraOutputer) this);
      rate = rt;
    }
    if (rate < 0.01 || rate > 250.) {
      rate = 5.;
    }
    rateDisableCount = 11;
    prta(Util.clear(tmpsb).append("HO: new ").append(seedname).append(" rt=").append(rate).
            append(" wait=").append(wait).append(" used=").append(getNused()).
            append(" scn=").append(scn).append(" ").append(tb.scn).append(" derive=").append(deriveLH));
  }

  public HydraOutputer(StringBuilder seed, int wait, long startTimeMS) {
    this(seed.toString(), wait, startTimeMS);
  }

  /**
   * Creates a new instance of HydraOutputer
   *
   * @param seed A 12 character seed name
   * @param wait The length of time this outputer is willing to wait for
   * complete data
   * @param startTimeMS A Millisecond time (like for GregorianCalendar) of the
   * start time desired.
   */
  public HydraOutputer(String seed, int wait, long startTimeMS) {
    if (host == null) {
      setCommandLine("");// If user has not set these parameters - set them to defaults
    }
    seedname = seed;
    Util.clear(seednameSB).append(seed);
    while (seednameSB.length() < 12) {
      seednameSB.append(" ");
    }
    waitTime = wait;
    desired.setTimeInMillis(startTimeMS);
    earliest.setTimeInMillis(startTimeMS);
    synchronized (queueMutex) {
      if (thr == null) {
        thr = new HydraOutputThread();
        prta(Util.clear(tmpsb).append("HO: start1 an output thread mhost=").
                append(mhost).append("/").append(mport).append(" host=").append(host).append("/").append(port));
      }
    }
    pinno = ++pinnoNext;
    if (seedname.equals("PARAMS")) {
      prta("Setup the PARAMS hydraoutputer");
    } else {
      tb = new TraceBuf(new byte[TraceBuf.TRACE_ONE_BUF_LENGTH]); // reserve space for one tracebuf
      tb.setSCNMode(scn);
      Channel chn = EdgeChannelServer.getChannel(seedname);
      if (chn != null) {
        rate = chn.getRate();
        doDeriveLHSetup(chn);
      } else {
        prta(Util.clear(tmpsb).append("HO: cannot find rate for channel=").append(seedname));
        EdgeChannelServer.createNewChannel(seednameSB, 0.000001, (HydraOutputer) this);
        rate = 0.000001;
      }
    }
    if (rate < 0.01 || rate > 200.) {
      rate = 5.;
    }
    rateDisableCount = 11;
    prta(Util.clear(tmpsb).append("HO: new ").append(seedname).append(" rt=").append(rate).
            append(" wait=").append(wait).append(" used=").append(getNused()).
            append(" scn2=").append(scn).append(" ").append(tb.scn).append(" derive=").append(deriveLH));

  }

  /**
   * get as string representing this HydraOutputer
   *
   * @return The representative string
   */
  @Override
  public String toString() {
    return toStringBuilder(null).toString();
  }

  @Override
  public StringBuilder toStringBuilder(StringBuilder tmp) {
    StringBuilder sb = tmp;
    if (sb == null) {
      sb = Util.clear(tmpsb);
    }
    synchronized (sb) {
      sb.append("HO: ").append(seedname).append(" wait=").append(waitTime).append(" ").
              append(Util.ascdatetime2(desired, null)).append(" #pkt=").append(npacket).
              append(" dbg=").append(dbgSeedname).append("|");
    }
    return sb;
  }

  /**
   * I do not think this is used in the final design
   */
  @Override
  public void tickle() {
  }

  /**
   * return the desired time a GregorianCalendar
   *
   * @return The desired time
   */
  @Override
  public GregorianCalendar getDesired() {
    return desired;
  }

  /**
   * return the desired time a GregorianCalendar
   *
   * @return The desired time
   */
  @Override
  public GregorianCalendar getEarliest() {
    return earliest;
  }

  /**
   * return the wait time for this in seconds
   *
   * @return The waittime (time allowed to wait for in order data) in seconds
   */
  @Override
  public int getWaitTime() {
    return waitTime;
  }

  /**
   * get number of used buffers in the queue
   *
   * @return The number of used buffers
   */
  public static int getNused() {
    int nused = nextin - nextout;         // nextout is chasing nexting
    if (nused < 0) {
      nused += queueSize;    // correct if nextin is < next out
    }
    if (nused > maxUsed) {
      maxUsed = nused;  // Keep tran of maximum 
    }
    return nused;
  }

  private boolean checkBadRate(double r) {
    if (Math.abs(r / rate - 1.) < 0.01) {
      return false;
    }
    // Every 10 packets, or 10 minutes, check the rate again
    if (rateDisableCount > 10 || (System.currentTimeMillis() - rateDisableTime) > 600000) {
      rate = EdgeChannelServer.getChannel(seedname).getRate();
      rateDisableTime = System.currentTimeMillis();
      if (Math.abs(r / rate - 1.) < 0.01) {
        prta(Util.clear(tmpsb).append("HO: ").append(seedname).append(" rate is now ok. rate=").append(rate));
        return false;
      }
      rateMessage = "** Bad rate channel file rate=" + rate + " data rate in data=" + r;
      prta(Util.clear(tmpsb).append("HO: ").append(seedname).append(" ").append(rateMessage));
      rateDisableCount = 0;
    }
    // Rate does not agree, do e-mail and other tests
    rateDisableCount++;
    return true;
  }

  /**
   * queue one MiniSeed for output. This method may use more than one output que
   * as they are in TraceBuf format
   *
   * @param ms A mini Seed packet
   * @param oor If non-zero, this is an out-of-order packet
   */
  public synchronized void queue(MiniSeed ms, int oor) {
    long start = ms.getTimeInMillis();
    int nsamp = ms.getNsamp();
    if (checkBadRate(ms.getRate())) {
      rate = ms.getRate();
      return;     // reject on rate test
    }
    int[] samples = null;
    if (ms.getSeedNameSB().indexOf(dbgSeedname) >= 0) {
      prta(Util.clear(tmpsb).append("queue(miniseed").append(tb.scn).append(" ").
              append(ms.toStringBuilder(null)));
    }
    if (oordbg && oor != 0) {
      prt(Util.clear(tmpsb).append("queye MS oor ").append(ms.toStringBuilder(null)));
    }

    if (nsamp == 0 || rate <= 0.) {
      return;              // We cannot process such data in hydra
    }    //prta("Q ms="+ms);psjgr
    if (ms.getEncoding() == 11 || ms.getEncoding() == 10) {
      try {
        if (frames == null || frames.length < ms.getBlockSize() - ms.getDataOffset()) {
          frames = new byte[ms.getBlockSize() - ms.getDataOffset()];
        }
        System.arraycopy(ms.getBuf(), ms.getDataOffset(), frames, 0, ms.getBlockSize() - ms.getDataOffset());
        if (ms.getEncoding() == 11) {
          samples = Steim2.decode(frames, ms.getNsamp(), ms.isSwapBytes());
          if (Steim2.hadReverseError()) {
            prt(Util.clear(tmpsb).append("    ").append(Steim2.getReverseError()).append(" ").append(ms.toStringBuilder(null)));
          }
        }
        if (ms.getEncoding() == 10) {
          samples = Steim1.decode(frames, ms.getNsamp(), ms.isSwapBytes());
        }
        int offset = 0;
        while (offset < nsamp) {
          int ns = nsamp - offset;
          if (nsamp - offset > TraceBuf.TRACE_MAX_NSAMPS) {
            ns = TraceBuf.TRACE_MAX_NSAMPS;
          }
          tb.setData(ms.getSeedNameSB(), start, ns, ms.getRate(), samples, offset,
                  TraceBuf.INST_USNSN, module, 0, pinno);
          offset += ns;
          start += (int) (ns / rate * 1000. + 0.5);
          if (ms.getSeedNameSB().indexOf(dbgSeedname) >= 0) {
            prta(Util.clear(tmpsb).append("Queue(ms) send tb=").append(tb.toStringBuilder(null)));
          }
          queue(tb.getBuf(), TraceBuf.TRACE_HDR_LENGTH + ns * 4, oor);
          //if(deriveLH != null)
          if (oor == 0) {
            processDerived(tb);
          }
        }
      } catch (SteimException e) {
        prt(Util.clear(tmpsb).append("   *** steim2 err=").append(e.getMessage()).append(" ms=").append(ms.toStringBuilder(null)));
      }
    }   // unknown encoding!
  }

  @Override
  public synchronized void queue(TimeSeriesBlock tb, int oor) {
    if (oordbg && oor != 0) {
      prt(Util.clear(tmpsb).append("queue TSB oor ").append(tb.toStringBuilder(null)));
    }
    if (tb.getClass().getName().contains("MiniSeed")) {
      queue((MiniSeed) tb, oor);
    } else {
      queue((TraceBuf) tb, oor);
    }
  }

  /**
   * queue one TraceBuf. It might have been assembled from frags so break it up
   * if needed. This method may use more than one output Que they are in
   * TraceBuf format
   *
   * @param ms A trace buf packet (per haps bigger than one UDP packet)
   * @param oor If non-zero, this is an out-of-order packet
   */
  public synchronized void queue(TraceBuf ms, int oor) {
    long start = ms.getTimeInMillis();
    int nsamp = ms.getNsamp();
    if (checkBadRate(ms.getRate())) {
      return;     // reject on rate test
    }
    int[] samples = ms.getData();
    if (nsamp == 0 || rate <= 0.) {
      return;              // We cannot process such data in hydra
    }    //prta("Q ms="+ms);
    int offset = 0;

    while (offset < nsamp) {
      int ns = nsamp - offset;
      if (nsamp - offset > TraceBuf.TRACE_MAX_NSAMPS) {
        ns = TraceBuf.TRACE_MAX_NSAMPS;
      }
      tb.setData(ms.getSeedNameSB(), start, ns, ms.getRate(), samples, offset,
              TraceBuf.INST_USNSN, module, 0, pinno);
      //Util.prt("off="+offset+" "+tb.toString());
      offset += ns;
      start += (int) (ns / rate * 1000. + 0.5);
      if (ms.getSeedNameSB().indexOf(dbgSeedname) >= 0) {
        prta(Util.clear(tmpsb).append("queue(TB) stb=").append(tb.toStringBuilder(null)));
      }
      if (oordbg && oor != 0) {
        prt(Util.clear(tmpsb).append("queue TB oor ").append(tb.toStringBuilder(null)));
      }
      queue(tb.getBuf(), TraceBuf.TRACE_HDR_LENGTH + ns * 4, oor);
      //if(deriveLH != null)
      if (oor == 0) {
        processDerived(tb);
      }
    }
  }

  /**
   * this is the general call to put data in this HydraOutputer. The supported
   * TimeSeriesBlocks are MiniSeed and TraceBuf
   *
   * @param buf A buffer with the raw data bytes
   * @param len The length of the buffer in bytes
   * @param cl THe class of the buffer (MiniSeed or Tracebuf!)
   * @param oor If not zero, this is an oor packet
   */
  @Override
  public synchronized void queue(byte[] buf, int len, Class cl, int oor) {
    prta(Util.clear(tmpsb).append("queue 3 arg class=").append(cl).append(" len=").append(len).
            append(" tb.scn=").append(tb.scn).append(" dbgseed=").append(dbgSeedname));
    if (cl.getSimpleName().contains("MiniSeed")) {
      try {
        if (ms == null) {
          ms = new MiniSeed(buf);
        } else {
          ms.load(buf);
        }
        if (ms.getSeedNameSB().indexOf(dbgSeedname) >= 0) {
          prta(Util.clear(tmpsb).append("HO: qMS ").append(ms.toStringBuilder(null)));
        }
        if (ms.getTimeInMillis() >= lastTime - 2) {
          lastTime = Math.min(ms.getNextExpectedTimeInMillis(), System.currentTimeMillis() + 600000);
          queue(ms, oor);
        } else {
          prt(Util.clear(tmpsb).append("Rejected data that is too soon ").append(seedname).
                  append(" ms early=").append(Util.ascdatetime2(ms.getNextExpectedTimeInMillis(), null)).append(" ").
                  append(ms.getTimeInMillis() - lastTime).append(" rt=").append(ms.getRate()).
                  append(" ns=").append(ms.getNsamp()));
        }
      } catch (IllegalSeednameException e) {
        prt("Illegal seedname to queue() as Miniseed");
      }
    } else if (cl.getSimpleName().contains("TraceBuf")) {
      tb.load(buf);
      if (tb.getSeedNameSB().indexOf(dbgSeedname) >= 0) {
        prta(Util.clear(tmpsb).append("HO: qTB ").append(checkBadRate(tb.getRate())).append(" ").append(rate).
                append(" ").append(tb.getRate()).append(" ").append(dbgSeedname).append(" ")
                .append(tb.getTimeInMillis() >= lastTime - 2).append(" ").append(tb.toStringBuilder(null)));
      }
      if (checkBadRate(tb.getRate())) {
        
        return;
      }
      if (tb.getTimeInMillis() >= lastTime - 2) {
        lastTime = Math.min(tb.getEndtime(), System.currentTimeMillis() + 600000);
        queue(tb.getBuf(), len, oor);        // It must be a TraceBuf - if not other elses go here!
        //if(deriveLH != null)
        if (oor == 0) {
          processDerived(tb);
        }
      } else {
        prt(Util.clear(tmpsb).append("rejected data too soon for tb ").append(tb.getSeedNameSB()).
                append(" ").append(tb.toStringBuilder(null)).append(" ms early=").append(tb.getTimeInMillis() - lastTime));
      }
    } else {
      prt(Util.clear(tmpsb).append("Illegal TimeSeries class sent to HydraOutputer =").append(cl.getName()));
    }
  }

  /**
   * This just queues the raw buffer and the user is responsible for insuring it
   * is a valid UDP packet format (in particular the byte[1] must be the correct
   * type
   *
   * @param type This must be a valid Earthworm type (TraceBuf=19, tracebuf1=20,
   * GlobalPick=228 (-28)
   * @param buf The buffer including all 6 pre-payload bytes (3 for logo, 3 for
   * UDP overhead
   * @param len The length of the buf including the 6 header and payload bytes.
   */
  public synchronized void queue(int type, byte[] buf, int len) {
    buf[1] = (byte) type;
    queue(buf, len, 0);
  }

  /**
   * put a raw buffer (which must contain a TraceBuf) into the queue
   *
   * @param buf The raw TraceBuf buffer
   * @param len The length of the buffer in bytes
   */
  private synchronized void queue(byte[] buf, int len, int oor) {
    // deal with the packet
    if (length == null) {
      return;
    }
    synchronized (queueMutex) {
      //prta("QTB nin="+nextin+" "+new TraceBuf(buf));
      if ((nextin + 1) % queueSize == nextout) {
        ndiscards++;
        if (ndiscards % 1000 == 1) {
          prta(Util.clear(tmpsb).append("Q overflow nin=").append(nextin).append(" nout=").append(nextout).
                  append(" ndis=").append(ndiscards).append(" mxuse=").append(maxUsed).
                  append(buf[1] == TraceBuf.TRACEBUF_TYPE || buf[1] == TraceBuf.TRACEBUF1_TYPE ? TraceBuf.getSeednameSB(buf, null) : ""));
        }
        if (ndiscards % 1000 == 999) {
          SendEvent.edgeSMEEvent("HydraQovfl",
                  "Hydra Q overflow=" + Util.getSystemName() + " " + Util.getAccount() + " " + Util.getNode() + " #dis=" + ndiscards, this);
        }
        return;
      }
      npacket++;
      if (getNused() > maxUsed) {
        maxUsed = getNused();
      }
      // put it in the queue
      if (buf[1] == TraceBuf.TRACEBUF_TYPE || buf[1] == TraceBuf.TRACEBUF1_TYPE) {
        if (TraceBuf.getSeednameSB(buf, null).indexOf(dbgSeedname) >= 0) {
          prt(Util.clear(tmpsb).append(TraceBuf.getSeednameSB(buf, null)).append(" as nextin=").append(nextin));
          if (oordbg && oor != 0) {
            prt(Util.clear(tmpsb).append(TraceBuf.getSeednameSB(buf, null)).append(" OOR data QUEUED"));
          }
        }
      }
      if (len > queueMsgMax) {
        SendEvent.edgeSMEEvent("HydraMsgLen", "Message to Hydra queue=" + len + " max=" + queueMsgMax, this);
        prta(Util.clear(tmpsb).append(" **** Message too long len=").append(len).
                append(" max=").append(queueMsgMax).append(" ").append(TraceBuf.getSeednameSB(buf, null)));
        ndiscards++;
      } else {
        System.arraycopy(buf, 0, queue.get(nextin), 0, len);
        queue.get(nextin)[0] = (byte) institution;
        queue.get(nextin)[2] = (byte) module;
        queue.get(nextin)[4] = (byte) (seq++);
        length[nextin] = len;
        outOfOrder[nextin] = oor;
        nextin = (nextin + 1) % queueSize;
        nenqueue++;
      }
    }

  }

  /*public synchronized byte[] dequeue() {
    if(nextin == nextout) return null;
    int n = nextout;
    nextout = (nextout +1) % queueSize;
    return queue.get(n);
  }*/
  private final class HydraOutputThread extends Thread {

    boolean terminate;
    long lastThrottle;
    long lastStatus;
    long lastNpackets;
    int throttleBytes;
    boolean isShuttingDown;
    long npackets;
    long npacketsSent = 0;
    StringBuilder runsb = new StringBuilder(50);

    public void shutdown() {
      if (isShuttingDown) {
        return;
      }
      prta("HO:[HydraOutputThread] is shutting down");
      terminate = true;
    }

    public HydraOutputThread() {
      gov.usgs.anss.util.Util.prta("new ThreadOutput " + getName() + " " + getClass().getSimpleName());
      start();
    }

    @Override
    public void run() {
      boolean sendit = false;
      String tag = getName();
      tag = "[" + tag.substring(tag.indexOf("-") + 1) + "]";
      lastThrottle = System.currentTimeMillis();
      DatagramSocket ds = null;
      InetAddress ipaddr;
      DatagramPacket dp = null;
      int seqarray = 0;
      int seqnonarray = 0;
      int seqderived = 0;
      int seqarrayoor = 0;
      int seqnonarrayoor = 0;
      int seqderivedoor = 0;
      int detailCount = 0;
      int nthrottle = 0;
      long totthrottle = 0;
      long nbytesCycle = 0;
      int maxlength = 0;
      long nbytesOOR = 0;
      long lastNpacketOOR = 0;
      StringBuilder ch = new StringBuilder(12);

      prta(Util.clear(runsb).append(tag).append(" HOT: starting thread mport=").append(mport).
              append(" mhost=").append(mhost).append(" host=").append(host).append(" port=").append(port).
              append(" throttleMS=").append(throttleMS).append("/").append(400 / throttleMS));
      while (length == null) {
        try {
          sleep(100);
        } catch (InterruptedException expected) {
        }
      }
      try {                       // catch all runtime exceptions
        while (ds == null || dp == null) {
          try {
            ipaddr = InetAddress.getByName(host);
            if (ipaddr == null) {
              prt(Util.clear(runsb).append("    ***** Host did not translate = ").append(host));
            }
            if (length == null) {
              prt(Util.clear(runsb).append("    ***** length=null!"));
            }
            dp = new DatagramPacket(new byte[TraceBuf.UDP_SIZ], length[nextout],
                    ipaddr, port);
            if (!blockOutput) {   // do not need socket if no
              if (mhost.equals("")) {
                ds = new DatagramSocket(mport);
              } else {
                ds = new DatagramSocket(mport, InetAddress.getByName(mhost));
              }
            } else {
              break;
            }
            prta(Util.clear(runsb).append(tag).append("HOT: Open UDP DP=").append(host).append("/").append(port).
                    append("/array=").append(arrayPort).append("" + " derivedLHport=").append(derivedLHPort).
                    append(" mhost=").append(mhost).append("/").append(mport).append(" block output=").append(blockOutput));
          } catch (UnknownHostException e) {
            prta(Util.clear(runsb).append(tag).append(" HOT: Unknown host exception. e=").append(e));
            SendEvent.debugEvent("HydUnkHost", "Hydra could not translate host=" + host, this);
            if (par == null) {
              e.printStackTrace();
            } else {
              e.printStackTrace(par.getPrintStream());
            }
          } catch (SocketException e) {
            ds = null;
            prta(Util.clear(runsb).append(tag).append(" HOT: cannot set up DatagramSocket to ").
                    append(host).append("/").append(port).append(" bind=").append(mhost).
                    append("/").append(mport).append(" ").append(e.getMessage()));
            SendEvent.edgeEvent("HydraNoDGram", "Cannot set up DGram to " + host + "/" + port + " e=" + e, "HydraOutputThread");
            port = port + (int) (Math.random() * 25) + 1;     // see if another port helps!
            try {
              sleep(30000);
            } catch (InterruptedException expected) {
            }
          }
        }

        //EdgeThreadTimeout eto = new EdgeThreadTimeout(getTag(), this, 30000,60000);
        int lastseq = 0;
        boolean packetTypeOK;
        while (!terminate) {
          if (nextin == nextout) {
            try {
              sleep(1);
            } catch (InterruptedException expected) {
            }
          }
          while (nextin != nextout) {
            try {     // catch any Runtimes and go on!
              if (throttleBytes > 50000) {
                long elapsed = System.currentTimeMillis() - lastThrottle;
                if (elapsed < throttleMS) {
                  //prta(tag+" HOT: throttle="+Math.max(1L, 50 - elapsed));
                  try {
                    sleep(Math.max(1L, throttleMS - elapsed));
                  } catch (InterruptedException expected) {
                  }
                  nthrottle++;
                  totthrottle += Math.max(1L, throttleMS - elapsed);
                }
                throttleBytes = 0;
                lastThrottle = System.currentTimeMillis();
              }
              // Check that the sequence number from the queue are correct.
              // We are going to override the seq with one that increments by output port
              // SO I am not sure this is very useful except to debug queue/dequeue errors
              if (lastseq == 127) {
                lastseq = -129;
              }
              if (lastseq + 1 != queue.get(nextout)[4]) {
                prta(Util.clear(runsb).append(tag).append(" HOT: *** Seq error got ").
                        append(queue.get(nextout)[4]).append(" vs ").append(lastseq + 1));
              }
              lastseq = queue.get(nextout)[4];

              // check for the type of the packet to dispatch to right place
              packetTypeOK = true;
              switch ((int) queue.get(nextout)[1]) {
                case TraceBuf.GLOBAL_PICK_TYPE:
                  dp.setPort(globalPickPort);
                  break;
                case TraceBuf.TRACEBUF1_TYPE:
                case TraceBuf.TRACEBUF_TYPE:
                  Util.clear(ch);
                  TraceBuf.getSeednameSB(queue.get(nextout), ch);
                  Util.stringBuilderReplaceAll(ch, "-", " ");
                  // If the trace bufs are being converted to SCN from SCNL the channel name in trace buf maybe incomplete
                  if (scn && ch.length() < 12 && seedname.length() == 12) {
                    Util.clear(ch).append(seedname);
                  }
                  if (ch.length() < 8) {
                    Util.clear(ch).append(seedname);
                  }
                  Channel chan = null;
                  if (ch.charAt(7) <= 'Z') { // lower case means derived, do not ask for the channel
                    try {
                      chan = EdgeChannelServer.getChannel(ch);
                    } catch (RuntimeException expected) {
                    }
                  } // Set the port and sequence if the data is part of in-order stream
                  dp.setPort(port);   // Assume normal port
                  if (outOfOrder[nextout] == 0) {
                    if (chan != null) {
                      if (ch.charAt(7) == 'b' || ch.charAt(7) == 'h') {
                        dp.setPort(derivedLHPort);
                        queue.get(nextout)[2] = (byte) (module + 30);   // This is a hack so derived LH and regular data go to different ports.
                        queue.get(nextout)[4] = (byte) seqderived++;
                      } else if ((chan.getFlags() & ARRAY_MASK) != 0) {
                        dp.setPort(arrayPort);
                        queue.get(nextout)[4] = (byte) seqarray++;
                        npacketArray++;
                        if (npacketArray % 1000 == 1) {
                          prta(Util.clear(runsb).append("UDP arrayport=").append(npacketArray).
                                  append(" ").append(TraceBuf.getSeednameSB(queue.get(nextout), null)));
                        }
                      } else {
                        queue.get(nextout)[4] = (byte) seqnonarray++;
                        npacketNormal++;
                      }
                    } else {    // If channel is not in, send on the
                      if (ch.charAt(7) == 'b' || ch.charAt(7) == 'h') {
                        dp.setPort(derivedLHPort);
                        queue.get(nextout)[4] = (byte) seqderived++;
                        queue.get(nextout)[2] = (byte) (module + 30);   // This is a hack so derived LH and regular data go to different ports.
                        npacketDLH++;
                      } else {
                        queue.get(nextout)[4] = (byte) seqnonarray++;
                        npacketNormal++;
                      }
                    }
                  } else if (oorOffset != 0) {    //OOR data, set port and seqence
                    if (oordbg || npacketOOR % 1000 == 1) {
                      prta(Util.clear(runsb).append("UDP oor data off=").append(oorOffset).
                              append(" #p=").append(npacketOOR).append(" ").append(TraceBuf.getSeednameSB(queue.get(nextout), null)));
                    }
                    dp.setPort(port + oorOffset);
                    if (chan != null) {
                      if (ch.charAt(7) == 'b' || ch.charAt(7) == 'h') {
                        prta(Util.clear(runsb).append(" ******* Cannot derive OOR data - somethings is wrong ").
                                append(TraceBuf.getSeednameSB(queue.get(nextout), null)));
                        dp.setPort(derivedLHPort + oorOffset);
                        queue.get(nextout)[2] = (byte) (module + 30);   // This is a hack so derived LH and regular data go to different ports.
                        queue.get(nextout)[4] = (byte) seqderivedoor++;
                        npacketDLHOOR++;
                      } else if ((chan.getFlags() & ARRAY_MASK) != 0) {
                        dp.setPort(arrayPort + oorOffset);
                        queue.get(nextout)[4] = (byte) seqarrayoor++;
                        npacketArrayOOR++;
                      } else {
                        queue.get(nextout)[4] = (byte) seqnonarrayoor++;
                        npacketOOR++;
                      }
                    } else {    // If channel is not in, send on the
                      if (ch.charAt(7) == 'b' || ch.charAt(7) == 'h') {
                        dp.setPort(derivedLHPort + oorOffset);
                        prta(Util.clear(runsb).append(" ******* Cannot derive OOR data - somethings is wrong ").
                                append(TraceBuf.getSeednameSB(queue.get(nextout), null)));
                        queue.get(nextout)[4] = (byte) seqderivedoor++;
                        queue.get(nextout)[2] = (byte) (module + 30);   // This is a hack so derived LH and regular data go to different ports.
                        npacketDLHOOR++;
                      } else {
                        queue.get(nextout)[4] = (byte) seqnonarrayoor++;
                        npacketOOR++;
                      }
                    }
                    if (oordbg) {
                      prta(Util.clear(runsb).append("HOT: OOR DEBUG ").append(dp.getPort()).
                              append(" ").append(new TraceBuf(queue.get(nextout))));
                    }
                  } else if (oordbg) {
                    prt(Util.clear(runsb).append("oor is set buf oorOffset is zero ").
                            append(oorOffset).append(" ").append(outOfOrder[nextout]));
                  }
                  break;
                default:
                  prta(Util.clear(runsb).append(tag).append("HOT: unknown type of packet=").append(queue.get(nextout)[1]));
                  packetTypeOK = false;
                  break;
              }
              if (packetTypeOK && (outOfOrder[nextout] == 0 || (outOfOrder[nextout] != 0 && oorOffset != 0))) { // do not send oor if its not configured
                dp.setData(queue.get(nextout), 0, length[nextout]);
                // Send the data
                try {
                  //if(dbg) prta("Send dp="+dp.toString()+" "+dp.getAddress()+"/"+dp.getPort()+" l="+dp.getLength());
                  //if(dbg) prta(tag+" seqarr="+((byte) seqarray)+" seqnonarr="+((byte) seqnonarray)+" chan="+chan+" ch="+ch);
                  if (queue.get(nextout)[1] == TraceBuf.TRACEBUF1_TYPE || queue.get(nextout)[1] == TraceBuf.TRACEBUF_TYPE) {
                    if (TraceBuf.getSeednameSB(queue.get(nextout), null).indexOf(dbgSeedname) >= 0 || dbg) {
                      prta(Util.clear(runsb).append(tag).append("HOT: Udp:").append(nextout).append("-").append(nextin).
                              append(" #u=").append(getNused()).append(" block=").append(blockOutput).
                              append(" dLHONLY=").append(deriveLHONLY).append("+ deriveLHPort=").append(derivedLHPort).
                              append(" ").append(dp.getPort()).append(" ").append(new TraceBuf(queue.get(nextout))).
                              append(" dp=").append(dp.getSocketAddress()).append(" ds=").
                              append(ds == null ? "null" : ds.getLocalSocketAddress()));
                    }
                  }
                  /*prta("Udp:"+nextout+"-"+nextin+" l="+length[nextout]+" "+queue.get(nextout)[0]+" "+
                      queue.get(nextout)[1]+" "+queue.get(nextout)[2]+" "+queue.get(nextout)[3]+" "+
                      queue.get(nextout)[4]+" "+queue.get(nextout)[5]);*/

                  if (!blockOutput) {
                    if (!deriveLHONLY || dp.getPort() == derivedLHPort) {
                      if(ds != null) ds.send(dp);
                      // If OOR packet, it does not count in the throttle, unlimited output possible right now
                      if (outOfOrder[nextout] != 0 && oorOffset != 0) {
                        nbytesOOR += length[nextout];
                        npacketOOR++;
                      } else {  // In order packet, track rate and throttle parameters
                        throttleBytes += length[nextout];
                        nbytesCycle += length[nextout];
                        npacketsSent++;
                      }
                      if (length[nextout] > maxlength) {
                        maxlength = length[nextout];
                      }
                    }
                  }
                } catch (IOException e) {
                  prt(Util.clear(runsb).append(tag).append(" HOT: IOException send broadcast UDP packet ").
                          append(e.getMessage()));
                }
              } else if (oordbg) {
                prt(Util.clear(runsb).append("HOT: data is oor but no offset ").
                        append(oorOffset).append(" ").append(outOfOrder[nextout]));
              }
              //yield();
              // This is a bad ideas!try{sleep(1L);} catch(InterruptedException expected) {} // slight pause between packets

              //if(dbg) prta("Sent ");
              npackets++;
              if (System.currentTimeMillis() - lastStatus > 60000) {
                npackStatus = npackets - lastNpackets;
                prta(Util.clear(runsb).append(tag).append(" HOT: # tot pack=").append(npackets).
                        append(" #p=").append(npackStatus).append(" #sent=").append(npacketsSent).
                        append(" #e=").append(nenqueue - lastNenqueue).append(" ").append(nbytesCycle * 8 / 60).
                        append(" bps mxl=").append(maxlength).append(" OOR:#p=").append(npacketOOR - lastNpacketOOR).
                        append(" ").append(nbytesOOR * 8 / 60).append(" bps #throt=").append(nthrottle).
                        append(" tot throt=").append(totthrottle).append(" ms ").append(throttleMS).
                        append(" ms dbg=").append(dbgSeedname).append(" used=").append(getNused()));
                prta(Util.clear(runsb).append(tag).append(" HOT: #array=").append(npacketArray).
                        append(" #arrayOOR=").append(npacketArrayOOR).append(" #DLH=").append(npacketDLH).
                        append(" #DLHOOR=").append(npacketDLHOOR).append(" #norm=").append(npacketNormal).
                        append(" #normOOR=").append(npacketOOR));
                if (nbytesOOR * 8 / 60 > 3000000) {
                  SendEvent.edgeSMEEvent("HyOutOORbps", "*** Rate of OOR data to Hydra large=" + nbytesOOR * 8 / 60, this);
                }
                if (detailCount++ % 120 == 7) {
                  prta(ChannelHolder.getSummary());
                  maxUsed = 0;
                }
                lastStatus = System.currentTimeMillis();

                npacketsSent = 0;
                lastNpacketOOR = npacketOOR;
                lastNpackets = npackets;
                lastNenqueue = nenqueue;
                maxlength = 0;
                nbytesCycle = 0;
                nbytesOOR = 0;
              }
            } catch (RuntimeException e) {
              Util.clear(runsb).append("HOT: RuntimeException in HydraOutputer  attempt to go on! ch=").append(ch).append(" e=").append(e);
              toStringBuilder(runsb);
              prta(runsb);
              if (e != null) {
                if (par == null) {
                  e.printStackTrace();
                } else {
                  e.printStackTrace(par.getPrintStream());
                }
              }
            }
            nextout = (nextout + 1) % queueSize;
          }
        }
        if (terminate) {
          if (ds != null) {
            if (!ds.isClosed()) {
              ds.close();
            }
          }
        }
      } catch (RuntimeException e) {
        Util.clear(runsb).append("HOT: RuntimeException in HydraOutputer ");
        toStringBuilder(runsb);
        runsb.append((e == null ? "null" : e.getMessage()));
        if (e != null) {
          e.printStackTrace();
        }
        prta(runsb);
      }
      prta(Util.clear(runsb).append("HOT:[HydraOutputThread] on ").append(seedname).append(" is shutdown."));
    }
  }
}
