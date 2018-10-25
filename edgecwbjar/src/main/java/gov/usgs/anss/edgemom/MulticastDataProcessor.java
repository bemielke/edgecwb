/*
 *  This software is in the public domain because it contains materials 
 *  that originally came from the United States Geological Survey, 
 *  an agency of the United States Department of Interior. For more 
 *  information, see the official USGS copyright policy at 
 *  http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.edgemom;

import gov.usgs.anss.edge.RawToMiniSeed;
import gov.usgs.anss.edge.ChannelSender;
import gnu.trove.map.hash.TLongObjectHashMap;
import gnu.trove.iterator.TLongObjectIterator;
import gov.usgs.alarm.SendEvent;
import gov.usgs.anss.RRPUtil.SeedUtil;
import gov.usgs.anss.edge.IllegalSeednameException;
import gov.usgs.anss.edge.IndexBlock;
import gov.usgs.anss.edgethread.EdgeThread;
import gov.usgs.anss.seed.MiniSeed;
import gov.usgs.anss.seed.Steim2;
import gov.usgs.anss.util.Util;
import gov.usgs.edge.config.Channel;
import gov.usgs.anss.seed.SteimException;
import java.io.IOException;
import java.io.FileNotFoundException;
import java.io.BufferedReader;
import java.io.FileReader;
import java.nio.ByteBuffer;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This class in started by an EdgeMom to process multi-cast packets. It instantiates one or many
 * MulticastListeners which handle the actual network activity. The MulticastListeners feed data
 * into the single MulticastQueue created by this thread. This thread then dequeues the packets and
 * processes them as either MiniSEED or one seconds packets using the normal edge methods of writing
 * or converting the data to MiniSEED. Both one second and MiniSEED can be sent via the
 * OutputInfrastructure rawSend routines.
 * <PRE>
 *  switch     args     Description
 * -chanre   Regxp      Only channels matching this regular expression are processed by this instance (dangerous)
 * -dbgchan  chanStr    If this string appears as the beginning of a channel, the list debug information
 * -inorder  type       Write gaps of this types for all channels if any are found
 * -noudpchan           Do not write packet summaries to a UdpChannel Server
 * -nohydra             Do not send data as trace bufs on an EdgeWire to Hydra
 * -q330                If present run checks miniSEED the nsamps of packet and calculated from headers agree
 * -1sec								If present, process the 1 second data into MiniSEED. If not, ignore any one second packets found.
 *
 *  ARGS passed to MulticastListener
 *
 *  -p        nnnnn     Set the port to listen to (ONLY one port is setup - do not user with -msconfig)
 *  -msrange  low;high  Set the upper and lower ports inclusive to listen to - use a semicolon between port  (do not use with -msconfig)
 *  -msconfig filename[,off][;filename,off]   this is a semicolon separated of configuration filenames and column offsets
 *                       (0=first column) for ports to get (do not use with -msrange!)
 *  -mip      ip.adr    The Multicast address to listen for
 *  -if       device    Use this interface for listening
 *
 *  -qsize    nn				Number of buffers of qlen to reserve for the queue.
 *  -rsize    nn				The number of KB to ask for the receive buffers on the Multi-cast sockets.
 *  -qlen     nn				The length of the buffering in the queue(def=512).
 *  -bind     ip.adr		Bind the received port to this IP address of the possible multi-homed addresses.
 *  -rsend							If present, send all data via the Raw send to OutputInfrastructure.rawSend().
 *  -dbg								Create more output.
 *
 * </PRE>
 *
 * @author U.S. Geological Survey <ketchum at usgs.gov>
 */
public final class MulticastDataProcessor extends EdgeThread {

  private static final TLongObjectHashMap<MulticastListener> listeners = new TLongObjectHashMap<>();    // Static list of all of the channels this server is keeping
  private boolean dbg;
  private static long mask;       // TraceInputDisable mask
  private boolean nocsend;        // Do not send data to ChannelSender
  private String argline;
  private long lastReadConfig;

  // Miniseed processing variables
  private MiniSeed ms;
  private long nmseed;
  private final StringBuilder seedname = new StringBuilder(12);
  private boolean q330check;
  private boolean hydra;
  private ChannelSender csend;
  private boolean allow4k = false;
  private boolean rawSend;
  private String chanRE;
  private Matcher chanMatcher;
  private String dbgChan = "ZZZZZZZZ";
  private final MulticastQueue queue;
  // Configuration parameters
  private String[] configFiles;
  private int[] configOffsets;   // This is the one for the q330_channels.cfg under earthworm
  private int portLow;            // If not zero, then user port range configuration
  private int portHigh;           // If not zero, then use port range configuration
  private String inorderGapType;  // if not null, create gaps of this type
  private long bad1Sec;

  // One second processing variables
  private GregorianCalendar g = new GregorianCalendar();
  private byte[] stationb = new byte[5];
  private byte[] chanb = new byte[3];
  private byte[] locationb = new byte[2];
  private byte[] networkbb = new byte[2];
  private int[] rawdata = new int[721];    // Guess at the maximum rate
  private int rawMaxRate = 40;
  private boolean processOneSecond;
  private long nOneSec;
  private int[] l1zfiller = new int[1];
  private boolean l1zgapfill = true;
  private final StringBuilder tmpsb = new StringBuilder(100);
  private final StringBuilder runsb = new StringBuilder(100);
  private final StringBuilder dbmsg = new StringBuilder(10);

  @Override
  public String toString() {
    return getStatusString().toString();
  }

  public void setDebug(boolean t) {
    dbg = t;
  }

  @Override
  public void terminate() {
    queue.terminate();
    TLongObjectIterator<MulticastListener> itr = listeners.iterator();
    while (itr.hasNext()) {
      itr.advance();
      itr.value().terminate();
    }
    terminate = true;
  }

  /**
   * Return the monitor string for Icinga.
   *
   * @return A String representing the monitor key value pairs for this EdgeThread.
   */
  @Override
  public StringBuilder getMonitorString() {
    Util.clear(monitorsb);
    monitorsb.append("MCQLastRecs=").append(queue.getLastTotalRecs()).append("\n").append("MSQDiscards=").append(queue.getNDiscard()).append("\n");
    return monitorsb;
  }

  /**
   * Return a status string for this type of thread.
   *
   * @return A status string with identifier and output measures.
   */
  @Override
  public StringBuilder getStatusString() {
    Util.clear(statussb);
    return statussb.append(getTag()).append("#MCL=").append(listeners.size()).
            append(" #ms=").append(nmseed).append(" #n1sec=").append(nOneSec).
            append(" ").append(queue.toString());
  }

  /**
   * Return console output. For this there is none.
   *
   * @return The console output which is always empty.
   */
  @Override
  public StringBuilder getConsoleOutput() {
    return consolesb;
  }

  /**
   * Creates a new instance of TraceBufQueuedClient.
   *
   * @param argline EdgeThread command line.
   * @param tag Tag for logging.
   * @throws java.io.IOException, if the Multi-cast socket or DatagramPacket cannot be setup on the
   * configured address and port.
   */
  public MulticastDataProcessor(String argline, String tag) throws IOException {
    super(argline, tag);
    l1zfiller[0] = -1;
    this.argline = argline;
    String[] args = argline.split("\\s");
    nocsend = false;
    hydra = true;
    q330check = false;
    processOneSecond = false;
    int port = 0;
    for (int i = 0; i < args.length; i++) {
      prt(i + " arg=" + args[i] + " args[i+1]=" + (i + 1 < args.length ? args[i + 1] : ""));
      if (args[i].equals("-p")) {
        port = Integer.parseInt(args[i + 1]);
        i++;
      } else if (args[i].equalsIgnoreCase("-bind") || args[i].equalsIgnoreCase("-mip")
              || args[i].equalsIgnoreCase("-qsize") || args[i].equalsIgnoreCase("-qlen")
              || args[i].equalsIgnoreCase("-if") || args[i].equalsIgnoreCase("-rsize")) {
        i++;
      } else if (args[i].equalsIgnoreCase("-dbg")) {
        dbg = true;
      } else if (args[i].equalsIgnoreCase("-noudpchan")) {
        nocsend = true;
      } else if (args[i].equalsIgnoreCase("-nohydra")) {
        hydra = false;
      } else if (args[i].equalsIgnoreCase("-empty") || args[i].equalsIgnoreCase("-dbgmcast")) {
      } else if (args[i].equalsIgnoreCase("-inorder")) {
        inorderGapType = args[i + 1];
        i++;
      } else if (args[i].equalsIgnoreCase("-q330")) {
        q330check = true;
      } else if (args[i].equalsIgnoreCase("-1sec")) {
        processOneSecond = true;
      } else if (args[i].equalsIgnoreCase("-l1zgapfill")) {
        l1zfiller[0] = Integer.parseInt(args[i + 1]);
        l1zgapfill = l1zfiller[0] < 0;
      } else if (args[i].equalsIgnoreCase("-chanre")) {
        chanRE = args[i + 1];
        i++;
      }else if (args[i].equalsIgnoreCase("-dbgchan")) {
        dbgChan = args[i + 1];
        i++;
      } else if (args[i].equalsIgnoreCase("-msrange")) {
        String[] parts = args[i + 1].split("[;:]");
        if (parts.length != 2) {
          prta(tag + " ***** -msrange plower:pupper is in the wrong form " + args[i + 1]);
          portLow = 7000;
          portHigh = 8999;
        } else {
          portLow = Integer.parseInt(parts[0]);
          portHigh = Integer.parseInt(parts[1]);

        }
        i++;
      } else if (args[i].equalsIgnoreCase("-msconfig")) {
        configFiles = args[i + 1].split(":");
        configOffsets = new int[configFiles.length];
        for (int ii = 0; ii < configFiles.length; ii++) {
          String[] tmp = configFiles[ii].split(",");
          if (tmp.length > 1) {
            configOffsets[ii] = Integer.parseInt(tmp[1]);
            configFiles[ii] = tmp[0];
          } else {
            configOffsets[ii] = 4;
          }
        }
        i++;
      } else if (args[i].equals("-rsend")) {
        rawSend = true;
        rawMaxRate = Integer.parseInt(args[i + 1]);
        i++;
      } else if (args[i].substring(0, 1).equals(">")) {
        break;
      } else {
        prt(Util.clear(tmpsb).append(tag).append("MDP:: unknown switch=").append(args[i]).append(" ln=").append(argline));
      }
    }
    prta("MDP: Waiting for EdgeChannelServer to go valid");
    for (int i = 0; i < 100; i++) {
      if (EdgeChannelServer.isValid()) {
        try {
          sleep(100);
        } catch (InterruptedException e) {
        }
      } else {
        break;
      }
    }
    mask = EdgeChannelServer.getFlagMask("TraceInputDisable");
    prta("MDP: staring MulticastQueue");
    queue = new MulticastQueue(argline, tag, (EdgeThread) this);
    // Create the MulticastListener
    if (portLow > 0 && portHigh > 0) {
      for (int pt = portLow; pt <= portHigh; pt++) {
        listeners.put(pt, new MulticastListener(argline.replaceAll("-mip", "-p " + pt + " -mip"), tag, (EdgeThread) this, queue));
      }
    } else if (configFiles == null) {
      listeners.put(1, new MulticastListener(argline, tag, (EdgeThread) this, queue));
    } else {
      readConfig();
    }
    prta(Util.clear(tmpsb).append("MDP: setup hydra=").append(hydra).append(" nocsend=").append(nocsend).
            append(" q330chk=").append(q330check).append(" mask=").append(Util.toHex(mask)).
            append(" process1sec=").append(processOneSecond).
            append(" gaptyp=").append(inorderGapType).append(" args=").append(argline));
    setDaemon(true);
    if (chanRE != null) {
      chanMatcher = Pattern.compile(chanRE).matcher("TESTING");
    }
    if (!nocsend) {
      csend = new ChannelSender("  ", "MultiC", "MC-" + tag);
      prta(Util.clear(tmpsb).append("MPD: create csend=").append(csend));
    }
    start();
  }

  /**
   * Read in a config file. These files are comma separated and the configuration offset gives the
   * port that the channels are to use.
   *
   */
  private void readConfig() {
    if (configFiles == null) {
      return;
    }
    for (int i = 0; i < configFiles.length; i++) {
      try {
        try (BufferedReader in = new BufferedReader(new FileReader(configFiles[i]))) {
          String line;
          while ((line = in.readLine()) != null) {

            String[] tmp = line.split(" ");
            if (tmp.length < configOffsets[i] + 1) {
              continue;
            }
            try {
              int port = Integer.parseInt(tmp[configOffsets[i]]);
              boolean found = false;
              int ilist = -1;
              MulticastListener mcl = listeners.get(port);
              if (mcl == null) {
                prta(Util.clear(tmpsb).append("ReadConfig: create new listener for port=").append(port));
                try {
                  listeners.put(port, new MulticastListener(argline.replaceAll("-mip", "-p " + port + " -mip"), "", (EdgeThread) this, queue));
                } catch (IOException e) {
                  prta(Util.clear(tmpsb).append("ReadConfig: IOException setting up listener for line=").
                          append(line).append(" e=").append(e));
                  if (!e.toString().contains("already bound")) {
                    e.printStackTrace(getPrintStream());
                  }
                }
              } else {
                if (!mcl.isRunning() || !mcl.isAlive()) {
                  prta(Util.clear(tmpsb).append("ReadConfig : *** listener has died!!! restart it").
                          append(listeners.get(ilist).toStringBuilder(null)));
                  mcl.close();
                  listeners.put(port, new MulticastListener(argline.replaceAll("-mip", "-p " + port + " -mip"), "", (EdgeThread) this, queue));
                }
              }
            } catch (NumberFormatException e) {
              prta(Util.clear(tmpsb).append("ReadConfig: Could not configure a listener for line=").append(line));
            }
          }
        }
      } catch (FileNotFoundException e) {
        prta(Util.clear(tmpsb).append("ReadConfig:Could not find configuration file =").
                append(configFiles[i]));
      } catch (IOException e) {
        prta(Util.clear(tmpsb).append("ReadConfig:IOException reading config file=").
                append(configFiles[i]).append(" e=").append(e));
        e.printStackTrace(getPrintStream());
      }
    }   // For on configFiles
    lastReadConfig = System.currentTimeMillis();
    prta(Util.clear(tmpsb).append("ReadConfig: has ").append(listeners.size()).
            append(" listeners #configFiles=").append(configFiles.length));
  }

  /**
   * Dequeue packets and process them as Q330 or 1 second packets based on their content.
   *
   */
  @Override
  public void run() {
    running = true;
    byte[] buf = new byte[queue.getMaxLength()];
    ByteBuffer bb = ByteBuffer.wrap(buf);
    int len;
    int port;
    while (!terminate) {
      try {
        while ((len = queue.dequeue(buf)) <= 0 && !terminate) {
          try {
            sleep(10);
          } catch (InterruptedException e) {
          }
        }
        if (terminate) {
          break;
        }
        if (dbg) {
          prta(Util.clear(runsb).append(tag).append(queue.getSockAddress()).append(" got len=").append(len).
                  append(" =0").append(buf[0]).append(" 1=").append(buf[1]).append(" 2=").append(buf[2]).
                  append(" 3=").append(buf[3]).append(" 4=").append(buf[4]).append(" 5=").append(buf[5]).
                  append(" 6=").append(buf[6]).append(" 7=").append(buf[7]));
        }
        if (buf[7] == 32 && len == 512 && Character.isDigit((char) buf[0])
                && Character.isDigit((char) buf[1])
                && Character.isDigit((char) buf[2])
                && Character.isDigit((char) buf[3])
                && Character.isDigit((char) buf[4])
                && Character.isDigit((char) buf[5])
                && (Character.isDigit((char) buf[8]) || Character.isUpperCase((char) buf[8]) || (char) buf[8] == ' ')
                && (Character.isDigit((char) buf[9]) || Character.isUpperCase((char) buf[9]) || (char) buf[9] == ' ')
                && (Character.isDigit((char) buf[10]) || Character.isUpperCase((char) buf[10]) || (char) buf[10] == ' ')
                && (Character.isDigit((char) buf[11]) || Character.isUpperCase((char) buf[11]) || (char) buf[11] == ' ')
                && (Character.isDigit((char) buf[12]) || Character.isUpperCase((char) buf[12]) || (char) buf[12] == ' ')
                && (Character.isDigit((char) buf[13]) || Character.isUpperCase((char) buf[13]) || (char) buf[13] == ' ')) {
          processMiniSEED(buf, len);
        } else {
          processOneSec(bb, len);
        }
        if ((nmseed + nOneSec) % 1000 == 999) {
          if (System.currentTimeMillis() - lastReadConfig > 300000) {
            if (portLow <= 0 && portHigh <= 0 && configFiles != null) {
              readConfig(); // If hand configured, check config
            } else if (portLow > 0 && portHigh > 0) {
              // Check that all of the listeners are still running!
              TLongObjectIterator<MulticastListener> itr = listeners.iterator();
              while (itr.hasNext()) {
                //for(int i=0; i<listeners.size(); i++) {
                itr.advance();
                MulticastListener mcl = itr.value();
                if (!mcl.isRunning() || !mcl.isAlive()) {
                  Util.prta(Util.clear(runsb).append("*** Range listener has died.  Restart ").append(mcl.toStringBuilder(null)));
                  port = mcl.getPort();
                  try {
                    mcl.close();
                    listeners.put(port, new MulticastListener(argline.replaceAll("-mip", "-p " + port + " -mip"), "", (EdgeThread) this, queue));
                  } catch (IOException e) {
                    prta(Util.clear(runsb).append("*** Error restarting a listener port=").append(port).append(" e=").append(e));
                  }
                }
              }
            } else {
              lastReadConfig = System.currentTimeMillis(); // If not using config, defeat this check running too often
            }
          }
        }
      } catch (RuntimeException e) {
        prta(Util.clear(runsb).append(seedname).append(" Got runtime. continue. e=").append(e));
        e.printStackTrace(getPrintStream());
      }
    }
    running = false;
    prta(Util.clear(runsb).append(tag).append(" is exiting in.isRunning=").append(queue.isRunning()).
            append(" q=").append(queue.toStringBuilder(null)));
  }

  /**
   * Process a raw buf that is known to be miniseed. Data is written to
   * IndexBlock.writeMiniSeedCheckMidnight() to ensure it is processed correctly for midnight
   * spanning packets. Send data to Hydra (wire) outputer or to UdpHoldings if configured to do so.
   *
   * @param buf Raw bytes of miniseed.
   * @param len Length of miniseed packet.
   */
  private void processMiniSEED(byte[] buf, int len) {
    int ns;
    int nillegal = 0;
    nmseed++;
    try {
      if (ms == null) {
        ms = new MiniSeed(buf, 0, len);
      } else {
        ms.load(buf, 0, len);
      }
      if(ms.getSeedNameSB().indexOf(dbgChan) == 0) {
        prta(Util.clear(runsb).append("DBGCHAN:").append(ms));
      }
      if (chanMatcher != null) {
        if (dbg) {
          prta(Util.clear(runsb).append("Chk ").append(ms.getSeedNameSB()).append(" with ").append(chanMatcher).append(" chanre=").append(chanRE));
        }
        chanMatcher.reset(ms.getSeedNameSB());
        if (!chanMatcher.matches()) {
          return;      //DEBUG : check this!
        }
      }
      if (seedname.length() == 0) {
        Util.clear(seedname).append(ms.getSeedNameSB().substring(0, 7));
      }

      nillegal = 0;                 // Must be a legal seed name
      if (dbg || nmseed % 10000 == 0) {
        //      || (quer && ms.getSeedNameSB().substring(0,6).equals("USBRAL") && ms.getActivityFlags() > 0.0001 && ms.getNsamp() >0 )) {
        StringBuilder tmp = ms.toStringBuilder(null);
        //tmp.delete(72, tmp.length());
        Util.clear(runsb).append(tag).append(" ").append(tmp).
                append(" #pkt=").append(nmseed).append(" ").append(len).
                append(" ").append(queue.getSockAddress()).append(":").append(queue.getPort());
        prta(runsb);
      }

      if (q330check && ms.getRate() > 0.00001 && ms.getNsamp() > 0) {
        ns = MiniSeed.getNsampFromBuf(buf, ms.getEncoding());
        if (ns < ms.getNsamp()) {
          prta(Util.clear(runsb).append(tag).append(" *** Q330 ns fail=").append(ns).append(" ms=").append(ms));
          SendEvent.debugEvent("Q330BadBlk", tag + "Bad ns " + ns + "!=" + ms.getNsamp() + " " + ms.toString().substring(0, 50), this);
          return;       // Skip putting this data into databases
        }
      }
      if (ms.getBlockSize() > 512 || ms.getNsamp() <= 0) {
        prt(Util.clear(runsb).append(tag).append("Skipping non time series block of ").
                append(ms.getBlockSize()).append(" and no data ms=").append(ms));
        return;
      }
      if (ms.getBlockSize() < 512 || len < 512) {
        prt(Util.clear(runsb).append(tag).append(" ** Short MiniSeed block < 512").
                append(ms.getBlockSize()).append(" l=").append(len).append(" ").append(ms));
      }
      Channel c = EdgeChannelServer.getChannel(ms.getSeedNameSB());
      //if(seedname.indexOf("REPV15")>= 0)  par.prta(seedname+": "+c.toString()+" flags="+c.getFlags()+" mask="+mask+" hydra="+hydra);
      if (c == null) {
        prta(Util.clear(runsb).append(tag).append("MDPMS: ***** new channel found=").append(ms.getSeedNameSB()));
        SendEvent.edgeSMEEvent("ChanNotFnd", "MDP: MiniSEED Channel not found=" + ms.getSeedNameSB() + " new?", this);
        /*Util.clear(dbmsg).append("edge^channel^channel=").append(seedname).
                append(";commgroupid=0;operatorid=0;protocolid=0;sendto=0;flags=0;links=0;delay=0;sort1=new;sort2=;rate=").
                append(rate).append(";nsnnet=0;nsnnode=0;nsnchan=0;created_by=0;created=current_timestamp();");*/
        EdgeChannelServer.createNewChannel(ms.getSeedNameSB(), ms.getRate(), this);
      }

      // If the block is not full, decompress and load using RTMS
      if (ms.getUsedFrameCount() < 7 && ms.getNsamp() > 0 && ms.getRate() > 0.) { // If miniseed is not fully compressed, use RTMS
        try {
          int[] dummy = ms.decomp(rawdata);
          if (dummy == null) {
            prta("MDP: **** Got a Steim2 exception from " + (Steim2.hadReverseError() ? Steim2.getReverseError() : "")
                    + " " + (Steim2.hadSampleCountError() ? Steim2.getSampleCountError() : "") + " ms=" + ms);
            IndexBlock.writeMiniSeedCheckMidnight(ms, false, allow4k, tag, this);  // Write it out so it is not lost even through it is partial
          } else {
            int secs = (int) ((ms.getTimeInMillisTruncated() % 86400000L) / 1000);
            int usecs = ms.getUseconds();
            if (ms.getSeedNameSB().charAt(7) == 'L' && ms.getSeedNameSB().charAt(8) == '1'
                    && ms.getSeedNameSB().charAt(9) == 'Z' && l1zgapfill) {
              RawToMiniSeed rtms = RawToMiniSeed.getRawToMiniSeed(ms.getSeedNameSB());
              long gap = -1;
              long gaporg = -1;
              int ns2 = -1;
              if (rtms != null) {
                gap = rtms.gapCalc(ms.getYear(), ms.getDay(), secs, usecs);
                gaporg = gap;
                ns2 = (int) ((gap + 1000) / 1000000);
                //if(Util.stringBuilderEqual(ms.getSeedNameSB(), "BCSJX  L1Z  ") ) {
                while (gap > 0 && gap < 721000000) {
                  rtms.process(l1zfiller, 1, ms.getYear(), ms.getDay(),
                          (int) (secs - (gap + 1000) / 1000000), usecs,
                          ms.getActivityFlags(), ms.getIOClockFlags(), ms.getDataQualityFlags(),
                          ms.getTimingQuality(), 0);
                  gap -= 1000000;
                }
                //}
              }
              if (gap != 0) {
                prta("L1Z filler gap=" + gaporg + " nsfill=" + ns2 + " [0]=" + rawdata[0] + " [" + (ms.getNsamp() - 1) + "]=" + rawdata[ms.getNsamp() - 1] + " ms=" + ms);
              }
              long time = System.currentTimeMillis();
              time = time - rawdata[ms.getNsamp() - 1];
              g.setTimeInMillis(time);
              int julian = SeedUtil.toJulian(g);
              try {
                if (csend != null) {
                  csend.send(julian, (int) (time % 86400000), // Milliseconds
                          ms.getSeedNameSB(), 1, ms.getRate(), ms.getBlockSize());
                }
              } catch (IOException e) {
                prta("MDP: write error L1Z CSend? e=" + e);
              }
            }
            RawToMiniSeed.addTimeseries(rawdata, ms.getNsamp(), ms.getSeedNameSB(), ms.getYear(), ms.getDay(),
                    secs, usecs, ms.getRate(),
                    ms.getActivityFlags(), ms.getIOClockFlags(), ms.getDataQualityFlags(),
                    ms.getTimingQuality(), this);
          }
        } catch (SteimException e) {
          prta("MDP: **** Got a steim exception from ms=" + ms);
          e.printStackTrace(getPrintStream());
          IndexBlock.writeMiniSeedCheckMidnight(ms, false, allow4k, tag, this);  // Write it out so it is not lost even through it is partial
        }

      } else {      // Full miniSEED load and go.
        IndexBlock.writeMiniSeedCheckMidnight(ms, false, allow4k, tag, this);  // Do not use Chan Infra for Miniseed data
      }

      // Process the block to hydra, send it using channel sender, and make the gaps.
      if (hydra) {
        Hydra.sendNoChannelInfrastructure(ms);            // Send with no channel Infra
      }      // Send this channel infor to the channel server for latency etc.
      if (csend != null) {
        try {
          if (!(ms.getSeedNameSB().charAt(7) == 'L' && ms.getSeedNameSB().charAt(8) == '1' && ms.getSeedNameSB().charAt(9) == 'Z')) {
            csend.send(ms.getJulian(), (int) (ms.getTimeInMillis() % 86400000), // Milliseconds
                    ms.getSeedNameSB(), ms.getNsamp(), ms.getRate(), ms.getBlockSize());  // Key, nsamp, rate and nbytes
          }
        } catch (IOException e) {
          prta("MDP: write error CSend? e=" + e);
        }
      }
      GapsFromInorderMaker.processChannel(ms.getTimeInMillis(), ms.getSeedNameSB(), ms.getNsamp(), ms.getRate(), inorderGapType);
    } catch (IllegalSeednameException e) {
      nillegal++;
      if (ms != null) {
        prta(Util.clear(runsb).append(tag).append(" IllegalSeedName =").
                append(nillegal).append(" ").append(Util.toAllPrintable(ms.getSeedNameSB())).
                append(" ").append(e.getMessage()));
      } else {
        prta(tag + " IllegalSeedName =" + nillegal + " ms is null. "
                + e.getMessage());
      }
      for (int i = 0; i < 48; i++) {
        prt(Util.clear(runsb).append(i).append(" = ").append(buf[i]).append(" ").append((char) buf[i]));
      }
      if (nillegal > 3) {
        terminate = true;    // if 3 in a row, then close connection     
      }
    } catch (RuntimeException e) {
      prta(Util.clear(runsb).append(tag).append(" ** processOneSec runtime e=").append(e.toString()).
              append(" ").append(queue.getSockAddress().toString()));
      e.printStackTrace(getPrintStream());
    }
  }
  private final StringBuilder network = new StringBuilder(2);
  private final StringBuilder station = new StringBuilder(5);
  private final StringBuilder channel = new StringBuilder(3);
  private final StringBuilder location = new StringBuilder(2);
  private final StringBuilder errsb = new StringBuilder(100);

  /**
   * Process the Caltech or Mountainair style one second packet. Data is sent to RawToMiniSeed for
   * conversion to miniseed.
   *
   * <PRE>
   * #define __onesec_pkt_h
   *
   *
   * #define Q330_TIME_OFFSET_FROM_UNIX  946684800
   *
   * #define MAX_ONESEC_PKT_RATE 1000
   *
   * #define ONESEC_PKT_NET_LEN      4
   * #define ONESEC_PKT_STATION_LEN  16
   * #define ONESEC_PKT_CHANNEL_LEN  16
   * #define ONESEC_PKT_LOCATION_LEN 4
   *
   * struct onesec_pkt{
   *   char net[ONESEC_PKT_NET_LEN];           //0
   *   char station[ONESEC_PKT_STATION_LEN];   //4
   *   char channel[ONESEC_PKT_CHANNEL_LEN];   //20
   *   char location[ONESEC_PKT_LOCATION_LEN]; //36
   *   uint32_t rate;                          //40
   *   uint32_t timestamp_sec;                 //44
   *   uint32_t timestamp_usec;                //48
   *   int32_t samples[MAX_ONESEC_PKT_RATE];   //52
   * };
   * </PRE>
   *
   * @param bb ByteBuffer with raw one second packet.
   * @param len Length of the packet in bytes (not used).
   */

  private void processOneSec(ByteBuffer bb, int len) {
    try {
      nOneSec++;
      bb.position(0);     // position network and station, build up a Seedname NNSSSSSCCCLL
      zero(networkbb);
      bb.get(networkbb);
      fix(networkbb);
      Util.clear(network).append((char) networkbb[0]).append((char) networkbb[1]);  // network 
      //String network = new String(networkbb);
      bb.position(4);
      zero(stationb);
      bb.get(stationb);
      fix(stationb);
      Util.clear(station).append((char) stationb[0]).append((char) stationb[1]). // Station
              append((char) stationb[2]).append((char) stationb[3]).append((char) stationb[4]);

      //String station = new String(stationb);
      bb.position(20);
      zero(chanb);
      bb.get(chanb);
      fix(chanb);
      Util.clear(channel).append((char) chanb[0]).append((char) chanb[1]).append((char) chanb[2]); // create the channel
      //String channel = new String(chanb);
      bb.position(36);
      zero(locationb);
      bb.get(locationb);
      if (locationb[0] == 0) {
        locationb[1] = 0;
      }
      fix(locationb);
      Util.clear(location).append((char) locationb[0]).append((char) locationb[1]); // Location code
      //String location = new String(locationb);
      Util.clear(seedname).append(network).append(station).append(channel).append(location);// Full seedname

      for (int i = 0; i < seedname.length(); i++) {
        if (!Character.isUpperCase(seedname.charAt(i)) && seedname.charAt(i) != ' ' && !Character.isDigit(seedname.charAt(i))) {
          if (queue.getPort() == 5678) {
            return;       // This seems to be some Mikrotik Router Discovery packets.
          }
          if (len == 70 && queue.getPort() == 7993) {
            return;      // These are UdpChannel packets.
          }          
          bad1Sec++;
          prta(Util.clear(runsb).append(tag).append(" ** Bad seedname |").append(seedname).
                  append("| reject packet ").append(queue.getSockAddress().toString()).
                  append(" len=").append(len).append(" nbad=").append(bad1Sec).
                  append(queue.getSockAddress()).append(":").append(queue.getPort()));
          Util.bufToSB(bb.array(), 0, len, tmpsb);
          prta(Util.toAllPrintable(tmpsb));
          return;
        }
      }
      bb.position(40);
      // Decode the packet into rate, time, samples
      int rate = bb.getInt();
      if (rate <= 0 || rate > 1000) {
        prta(Util.clear(runsb).append(tag).append(" ** Bad rate=").append(rate).append(" for ").append(seedname).
                append("reject packet ").append(queue.getSockAddress().toString()));
        return;
      }
      int secs = bb.getInt();
      int usecs = bb.getInt();
      g.setTimeInMillis((secs / 86400 * 86400) * 1000L);  // Time at beginning of day
      int year = g.get(Calendar.YEAR);
      int doy = g.get(Calendar.DAY_OF_YEAR);
      secs = secs % 86400;      // seconds into the day
      if (rawdata.length < rate) {
        rawdata = new int[rate];      // set to maximum rate encoundered
      }
      for (int i = 0; i < rate; i++) {
        rawdata[i] = bb.getInt();
      }
      if (dbg || nOneSec % 10000 == 9999 || seedname.indexOf(dbgChan) == 0) {
        g.setTimeInMillis(g.getTimeInMillis() + secs * 1000 + usecs / 1000);
        prta(Util.clear(runsb).append(tag).append(" MDP1s: Got 1sec ").append(nOneSec).append(" ").append(seedname).
                append(" ").append(Util.ascdatetime2(g)).append(" rt=").append(rate).
                append(" d[0]=").append(rawdata[0]).append(" 1=").append(rawdata[1]).
                append(" 2=").append(rawdata[2]));

      }
      Channel c = EdgeChannelServer.getChannel(seedname);
      //if(seedname.indexOf("REPV15")>= 0)  par.prta(seedname+": "+c.toString()+" flags="+c.getFlags()+" mask="+mask+" hydra="+hydra);
      if (c != null) {
        if ((c.getFlags() & mask) != 0) {
          if (nOneSec % 10 == 0) {
            prt(Util.clear(runsb).append(seedname).append(" **** is marked no trace input- skip"));
          }
          return;
        }     // Channel is disable from trace wire input

        if (Math.abs(c.getRate() - rate) / c.getRate() > 0.01) {
          prta(Util.clear(runsb).append(tag).append("MDP1S: ***** rates mismatch ").append(seedname).
                  append(" chan=").append(c.getRate()).append(" 1sec=").append(rate));
          SendEvent.debugSMEEvent("MDPBadRate", "rates mismatch " + seedname + " chan=" + c.getRate() + " trace=" + rate, this);

        }
      } else {
        prta(Util.clear(runsb).append(tag).append("MDP1S: ***** new channel found=").append(seedname));
        SendEvent.edgeSMEEvent("ChanNotFnd", "MDP: OneSecond Channel not found=" + seedname + " new?", this);
        /*Util.clear(dbmsg).append("edge^channel^channel=").append(seedname).
              append(";commgroupid=0;operatorid=0;protocolid=0;sendto=0;flags=0;links=0;delay=0;sort1=new;sort2=;rate=").
              append(rate).append(";nsnnet=0;nsnnode=0;nsnchan=0;created_by=0;created=current_timestamp();");*/
        EdgeChannelServer.createNewChannel(seedname, rate, this);
      }
      if (processOneSecond) {
        RawToMiniSeed.addTimeseries(rawdata, rate, seedname, g.get(Calendar.YEAR), g.get(Calendar.DAY_OF_YEAR),
                secs, usecs, rate, 0, 0, 0, 0, this);
        if (csend != null) {
          int julian = SeedUtil.toJulian(g.get(Calendar.YEAR), g.get(Calendar.DAY_OF_YEAR));
          try {
            csend.send(julian, (int) (g.getTimeInMillis() % 86400000L), // Milliseconds
                    seedname, rate, (double) rate, len);  // Key, nsamp, rate and nbytes
          } catch (IOException e) {
            e.printStackTrace(getPrintStream());
          }
        }
      }

      if (rawSend) // Normally lower rate data to QueryMom so it can be in RAM spans earlier than MiniSeed
      {
        if (rate <= rawMaxRate) {
          if (dbg) {
            prta(Util.clear(runsb).append(tag).append("OI.sendRaw() ").append(seedname).append(" ").
                    append(Util.ascdatetime2(g)).append(" rt=").append(rate).append(" ns=").append(rate));
          }
          OutputInfrastructure.sendRaw(c, seedname, g.getTimeInMillis() + secs * 86400000L + usecs / 1000, rate, rate, rawdata);
        }
      }
    } catch (RuntimeException e) {
      prta(Util.clear(runsb).append(tag).append(" ** processOneSec runtime e=").append(e.toString()).
              append(" ").append(queue.getSockAddress().toString()));
      e.printStackTrace(getPrintStream());
    }
  }

  private void fix(byte[] b) {
    boolean zero = false;
    for (int i = 0; i < b.length; i++) {
      if (b[i] == 0) {
        zero = true;
      }
      if (b[i] < 32 || zero) {
        b[i] = 32;
      }
    }
  }

  private void zero(byte[] b) {
    for (int i = 0; i < b.length; i++) {
      b[i] = 0;
    }
  }
}
