/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.edgemom;

import gov.usgs.anss.edgeoutput.RRPRingFile;
import gov.usgs.anss.edge.IndexBlockCheck;
import gov.usgs.anss.edge.MasterBlock;
import gov.usgs.anss.edge.EdgeProperties;
import gov.usgs.alarm.SendEvent;
import gov.usgs.anss.edge.EdgeBlockQueue;
import gov.usgs.anss.edge.EdgeBlock;
import gov.usgs.anss.edge.IllegalSeednameException;
import gov.usgs.anss.edgeoutput.EdgeOutputer;
import gov.usgs.anss.edgeoutput.RingFile;
import gov.usgs.anss.edgeoutput.RingServerSeedLink;
import gov.usgs.anss.edgethread.EdgeThread;
import gov.usgs.anss.seed.MiniSeed;
import gov.usgs.anss.util.Util;
import gov.usgs.edge.config.Channel;
import java.io.IOException;
import java.util.ArrayList;
import java.util.TreeMap;
import java.util.Iterator;
import gnu.trove.map.hash.TLongObjectHashMap;
import gnu.trove.iterator.TLongObjectIterator;
import gov.usgs.anss.edgeoutput.LHDerivedFromOI;
import gov.usgs.anss.edgethread.ManagerInterface;
import java.io.BufferedReader;
import java.io.FileReader;

/**
 * This is an EdgeThread that takes any data from the EdgeBlockQueue and sends
 * all data blocks to the various OutputInfrastructure handlers. The class of
 * the output handler associated with a Sendto comes from the "class" attribute
 * in the Sendto table. If this is empty, then then class defaults to a
 * RingFile. Adding a new class requires modification of this class to recognize
 * and instantiate the new class. The RingServerSeedLink is the only other
 * output class supported so far.
 * <p>
 * This class reads a configuration file of the form :
 * <pre>
 * TAG:Classs:args
 * </PRE> This file can be hand maintained, but normally it is created from the
 * configuration database by the OutputInfraStructureManager thread which builds
 * the file from the sendto table.
 * <p>
 * It has to work in full seed blocks so to handle >512 byte it has to
 * reassemble the blocks from the input blocks. Configuring this class on the
 * EdgeMom enables the output structures to be built for that instance. The
 * actual implementation of the output method (LISS, SeedLink, EWExport, ...)
 * uses the ring file or direct output from the outputer to get data for its
 * method.
 * <p>
 * For each configured output method in the Sendto table, a thread is created
 * for the class specified for that sendto - most are RingFiles but other direct
 * classes like RingServerSeedlink are possible. Each block that is examined
 * from the EdgeBlockQueue is presented to each output threads processBlock()
 * method along with the Channel object for this channel. Those processes use
 * the sendtomask in the Channel object to determine if this data is to be
 * processed by this method (i.e. added to the ring file, or forwarded to the
 * ringserver).
 * <p>
 * The output convention is :<br>
 * 1) If the filesize >is greater than zero in the Sendto database, use the
 * RingFile outputer of that size.<br>
 * 2) If not, use the class specified in the sendto database.<br>
 * 3) If the sendto regular expressions of hosts does not match this host, no
 * outputer is created.
 * <p>
 * Most outputers run from the RingFiles as this decouples the data from the
 * main EdgeMom tasks of acquiring data and getting it into the datafiles and
 * indices. All of the outputers are kept in a Vector called outputers. When
 * ever a block is received it is presented to each active outputer's
 * processBlock() method along with the channel object. The outputer then
 * determines if this block is destined for it or if it just returns.
 * <br>
 * <PRE>
 * NOTE: this command line is passed to the OutputInfrastructureManager which handls many of the options.
 *switch          Description
 *-dbg            Turn on verbose output.
 *-dbgibc         Turn on verbose output in IndexBlockCheck.
 *-dbgebq         Turn on verbose output in EdgeBlockQueue.
 * These switches are for the OutputInfrastructureManager:
 * -nodb            If present, no database is available and the config file is manually configured
 * -config filename The path and file for the configuration file
 * -dbg             If present, more logging
 *
 * </PRE>
 *
 * @author davidketchum
 */
public final class OutputInfrastructure extends EdgeThread {

  private static final StringBuilder REQUESTEDBLK = new StringBuilder(12).append("REQUESTEDBLK");
  private final TreeMap<String, ManagerInterface> thr = new TreeMap();
  private int nextseq;
  private static boolean isRunningDBG;
  private long lastStatus;               // Timer for 
  private final OutputInfrastructureManager oimgr;
  private final ShutdownOutputInfrastructureThread shutdown;    // The shutdown thread for terminations
  private boolean dbg;
  private int nindex;                     // Count index packets processed
  private int ndata;                      // Count data packets processed
  private int lastNindex;                 // getStatus last time nindex
  private int lastNdata;                  // "         "         ndata
  private long lastGetStatus;             // For getStatus(), calculate time
  private ArrayList<EdgeOutputer> outputers;    // A vector of outputers, each of these will be called for each block
  //private int noutputers;                 // Number of outputers used in the Vector.
  private final TLongObjectHashMap<AccreteMiniSeed> accreters = new TLongObjectHashMap<>();
  private final String configFile;
  private final StringBuilder tmpsb = new StringBuilder(100);
  private final StringBuilder runsb = new StringBuilder(100);

  @Override
  public String toString() {
    return toStringBuilder(null).toString();
  }

  public StringBuilder toStringBuilder(StringBuilder tmp) {
    StringBuilder sb = tmp;
    if (sb == null) {
      sb = Util.clear(tmpsb);
    }
    sb.append(tag).append(" #out=").append(outputers.size());
    return sb;
  }
  private static OutputInfrastructure theOutput;

  public static void insertBlock(byte[] buf) {
    if (theOutput == null) {
      return;
    }
    theOutput.insert(buf);
  }

  private void insert(byte[] buf) {
    for (EdgeOutputer outputer : outputers) {
      outputer.processBlock(buf, null);
    }
  }

  public static void sendRaw(Channel c, StringBuilder seedname, long time, double rate, int nsamp, int[] data) {
    if (theOutput == null) {
      return;
    }
    theOutput.sendRawBlock(c, seedname, time, rate, nsamp, data);
  }

  private void sendRawBlock(Channel c, StringBuilder seedname, long time, double rate, int nsamp, int[] data) {
    for (EdgeOutputer outputer : outputers) {
      outputer.processRawBlock(c, seedname, time, rate, nsamp, data);
    }
  }

  /**
   * Set debug state.
   *
   * @param t The new debug state
   */
  public void setDebug(boolean t) {
    dbg = t;
  }

  /**
   * Add an output to the list, these means every MiniSeed block found will pass
   * through this object's processOutput() method.
   *
   * @param out The output object to add
   */
  /*private synchronized  void addOutputer(EdgeOutputer out) {
    prt(Util.clear(tmpsb).append("OI: Add outputer ").append(noutputers).append(" ").append(out.toStringBuilder(null)));
    outputers.add(noutputers++, out);
  }*/
  /**
   * Terminate thread. This causes an interrupt to be sure it is terminated.
   */
  @Override
  public void terminate() {
    terminate = true;
    interrupt();
  }
  /**
   * Return the monitor string for Icinga
   *
   * @return A String representing the monitor key value pairs for this
   * EdgeThread.
   */
  long lastMonBlocks;

  @Override
  public StringBuilder getMonitorString() {
    if (monitorsb.length() > 0) {
      monitorsb.delete(0, monitorsb.length());
    }
    long nb = ndata - lastMonBlocks;
    lastMonBlocks = ndata;
    monitorsb.insert(0, "OITotalBlocksOut=" + nb + "\nNThreads=" + outputers.size() + "\n");
    monitorsb.append(oimgr.getMonitorString());
    return monitorsb;
  }

  /**
   * Return the status string for this thread
   *
   * @return A string representing the status for this thread.
   */
  @Override
  public StringBuilder getStatusString() {
    if (statussb.length() > 0) {
      statussb.delete(0, statussb.length());
    }
    int nind = nindex - lastNindex;
    int ndat = ndata - lastNdata;
    int blksec = (int) ((nind + ndat) * 1000
            / (Math.max(System.currentTimeMillis() - lastGetStatus, 1)));
    lastNdata = ndata;
    lastNindex = nindex;
    if (nindex > 2000000000 || ndata > 2000000000) {
      nindex = 0;
      ndata = 0;
      lastNdata = 0;
      lastNindex = 0;
    }
    lastGetStatus = System.currentTimeMillis();
    statussb.append("nsq=").append(nextseq).append(" nidx=").append(nind).append(" ndt=").append(ndat).
            append(" b/s=").append(blksec).append("\n");
    for (EdgeOutputer outputer : outputers) {
      statussb.append("       ").append(outputer.toStringBuilder(null)).append("\n");
    }
    return statussb;
  }

  /**
   * Return console output - this is fully integrated so it never returns
   * anything.
   *
   * @return "" since this cannot get output outside of the prt() system.
   */
  @Override
  public StringBuilder getConsoleOutput() {
    return consolesb;
  }

  /**
   * Creates a new instance of OutputInfrastructureThread.
   * <PRE>
   *Arguments are :
   *-dbg				Turn on debugging
   *-dbgrep		Turn on debugging in IndexFileOutputInfrastructure
   *-dbgibc		Turn on debugging in IndexFileCheck
   *-dbgebq		Turn on debuggin in EdgeBlockQueue
   *					To redirect output from this thread to an individual file
   * </pre>
   *
   * @param argline The edgemom.setup line which started this thread
   * @param tag The edgemom.setup tag portion to be used as the beginning of
   * this threads tag
   */
  public OutputInfrastructure(String argline, String tag) {
    super(argline, tag);
    if (theOutput != null) {
      prta("Attempt to build more than one OI!");
    }
    dbg = false;
    nextseq = EdgeBlockQueue.getNextSequence() - EdgeBlockQueue.getMaxQueue() * 9 / 10;
    if (nextseq <= 0) {
      nextseq = 1;
    }
    String[] args = argline.split("\\s");
    outputers = new ArrayList<>(64);
    //noutputers=0;
    boolean dbgibc = false;
    boolean dbgebq = false;
    boolean dbgoi = false;
    boolean noDB = false;
    lastGetStatus = System.currentTimeMillis();
    int dummy = 0;
    for (int i = 0; i < args.length; i++) {
      if (args[i].equals("-dbg")) {
        dbg = true;
      } else if (args[i].length() > 0 && args[i].substring(0, 1).equals(">")) {
        break;
      } else if (args[i].equals("-dbgibc")) {
        dbgibc = true;
      } else if (args[i].equals("-dbgebq")) {
        dbgebq = true;
      } else if (args[i].equals("-dbgoi")) {
        dbgoi = true;
      } else if (args[i].equals("-empty")) {
        dummy = 1;
      } else if (args[i].equals("-nodb")) {
        noDB = true;

      } else if (args[i].equals("-config")) {
        i++;
      } else {
        prt(Util.clear(tmpsb).append("OI: OutputInfrastructureThread unknown switch=").
                append(args[i]).append(" ln=").append(argline));
      }
    }
    // Set up debugs
    if (dbgebq) {
      EdgeBlockQueue.setDebug(dbgebq);
    }
    dbg = dbgoi;
    IndexBlockCheck.setDebug(dbgibc);
    prta(Util.ascdate() + " OI: created args=" + argline + " tag=" + tag);
    if (isRunningDBG) {
      prt("There is a OI running error.  Panic......");
      Util.exit(1);
    }
    //if(argline.contains(">")) argline = argline.substring(0, argline.indexOf(">"))+">> oim";
    argline = argline.trim() + "mgr";
    oimgr = new OutputInfrastructureManager(argline, tag);
    configFile = oimgr.getConfigFile();
    Util.chkFilePath(configFile);
    isRunningDBG = true;
    shutdown = new ShutdownOutputInfrastructureThread(this);
    Runtime.getRuntime().addShutdownHook(shutdown);

    // The ring buffer output is specified when the "sendto" panel has a positive filesize.
    start();
  }

  private void checkConfig() {
    StringBuilder config = oimgr.hasChanged();
    if (config == null) {
      prta("CheckConfig - Nothing to do!");
      return;
    }        // nothing to do
    try (BufferedReader in = new BufferedReader(new FileReader(configFile))) {
      String s;
      int n = 0;
      prta("Read in configfile for OI outputers =" + configFile);
      boolean changed = false;
      while ((s = in.readLine()) != null) {
        if (s.length() < 1) {
          continue;
        }
        if (s.substring(0, 1).equals("%")) {
          break;       // debug: done reading flag
        }
        if (!s.substring(0, 1).equals("#") && !s.substring(0, 1).equals("!")) {
          String[] tk = s.split(":");
          if (tk.length >= 3) {
            n++;
            String key = tk[0].trim();
            String type = tk[1].trim();
            String argline = tk[2].trim();
            ManagerInterface t = thr.get(key);
            if (t == null) {
              if (type.equalsIgnoreCase("RRPRingFile")) {
                t = new RRPRingFile(argline, this);
              } else if (type.equalsIgnoreCase("RingFile")) {
                t = new RingFile(argline, this);
              } else if (type.equalsIgnoreCase("RingServerSeedLink")) {
                t = new RingServerSeedLink(argline, key, this);
              } else {
                prta("Unknown type for new station! type=" + type + " stat=" + key + " argline=" + argline + " s=" + s);
              }
              if (t != null) {
                prta("OI: New Outputer found start " + key + " " + type + " " + t.hashCode() + ":" + argline);
                thr.put(key.trim(), t);
                t.setCheck(true);
              }
              changed = true;
            } else if (!argline.equals(t.getArgs()) || !t.isAlive() || !t.isRunning()) {// if its not the same, stop and start it
              t.setCheck(true);
              prta("OI: line changed or dead. alive=" + t.isAlive() + " running=" + t.isRunning() + "|" + key + "|" + type);
              prt(argline.trim() + "|\n" + t.getArgs() + "|\n");
              t.terminate();
              while (t.isAlive()) {
                try {
                  sleep(10000);
                } catch (InterruptedException expected) {
                }
                if (t.isAlive()) {
                  prta("OI: thread did not die in 10 seconds. " + key + " " + argline);
                }
              }
              if (type.equalsIgnoreCase("RRPRingFile")) {
                t = new RRPRingFile(argline, this);
              } else if (type.equalsIgnoreCase("RingFile")) {
                t = new RingFile(argline, this);
              } else if (type.equalsIgnoreCase("RingServerSeedLink")) {
                t = new RingServerSeedLink(argline, key, this);
              } else {
                prta("Unknown type for restart! type=" + type + " stat=" + key + " argline=" + argline + " s=" + s);
              }
              thr.put(key, t);
              t.setCheck(true);
              changed = true;
            } else {
              t.setCheck(true);
            }
          } else {
            prta("Line does not parse to 3 parts on colons " + s);
          }
        }   // If not a comment
      }   // end of read config file lines
      in.close();

      // now look through the outputers for any that have disappeared, and drop them.  In the same loop make outputers arraylist
      Iterator<ManagerInterface> itr = thr.values().iterator();
      outputers.clear();
      while (itr.hasNext()) {
        ManagerInterface t = itr.next();
        if (!t.getCheck()) {
          // This line is no longer in the configuration, shutdown the Q330
          prta("OI: line must have disappeared " + t.toString() + " " + t.getArgs());
          t.terminate();
          itr.remove();
          changed = true;
        } else {
          outputers.add((EdgeOutputer) t);      // Put it on the list of outputers
        }
      }
    } catch (IOException e) {
      e.printStackTrace(getPrintStream());
    }
  }

  /**
   * This run() reads any data as it comes into the EdgeBlockQueue and writes it
   * out to the data and index files. It also periodically closes files if they
   * have not been used for a long time!
   */
  @Override
  public void run() {
    theOutput = this;
    running = true;
    prta(" OI: is starting at seq=" + nextseq);
    int julian;
    EdgeBlock eb = new EdgeBlock();
    long lastStale = System.currentTimeMillis();
    long lastConfig = lastStale;
    lastStatus = System.currentTimeMillis();
    byte[] buf = new byte[512];
    boolean success;
    ArrayList<AccreteMiniSeed> asm = null;
    try {
      sleep(1000);
    } catch (InterruptedException expected) {
    }  // Allow OIManager to create file
    checkConfig();      // Inital builder of outputers

    int nskipped = 0;
    int nprocessed = 0;
    int nprocbig = 0;
    int nillegalSeed = 0;
    StringBuilder lastseedname = new StringBuilder(12);
    while (!terminate) {
      try {         // This will catch any runtime exceptions and keep this thread running
        while (nextseq != EdgeBlockQueue.getNextSequence()) {
          //long trip = System.currentTimeMillis();
          // Loop on each sequence until we get one!
          int ncnt = 0;
          while (!EdgeBlockQueue.getEdgeBlock(eb, nextseq)) {
            ncnt++;
            nskipped++;
            if (nskipped % 10000 == 0) {
              prta(Util.clear(runsb).append("OI: I am way behind!!! #skip=").append(nskipped).
                      append(" sq=").append(nextseq).append(" ebq.seq=").append(EdgeBlockQueue.getNextSequence()));
              SendEvent.edgeSMEEvent("OIWayBehind", "10000 skipped indices in OInfra", this);
            }
            nextseq = EdgeBlockQueue.incSequence(nextseq);
          }
          if (ncnt > 0) {
            prta(Util.clear(runsb).append("OI: ").append(ncnt).append(" skipped Left=").append(EdgeBlockQueue.nleft(nextseq)));
          }

          // Got a sequence in our local eb.  Process it.
          nextseq = EdgeBlockQueue.incSequence(nextseq);
          julian = eb.getJulian();       // <0 means a continuation of MS (no seed header present)
          // If the julian day is not in range 1970 - +2136 or node is not right, reject it.
          if (julian < 2446800 || julian > 2500000) {
            prta(Util.clear(runsb).append("OI: **** bad julian date/node in EdgeBlock jul=").
                    append(julian).append(" nd=").append(eb.getNodeSB()).append(" blk rejected!"));
            continue;    // Obviously a bogus block!
          }

          // Get this blocks destination file (julian + node is unique) or creates it
          if (eb.getBlock() >= 0) {      // Is it a data block?
            eb.getData(buf);              // Get the 512 byte of data
            try {

              StringBuilder seednameSB = eb.getSeedNameSB();
              if (Util.stringBuilderEqual(seednameSB, REQUESTEDBLK)) {
                MiniSeed.crackSeedname(buf, Util.clear(seednameSB));
              }
              try {
                MasterBlock.checkSeedName(seednameSB);
              } catch (IllegalSeednameException e) {
                // Sometimes this is a requested block and a continuation so its seednameSB is unknown
                if (Util.stringBuilderEqual(eb.getSeedNameSB(), REQUESTEDBLK) && eb.isContinuation()) { // this is a requested continuation
                  //if(dbg || nextseq % 50000 ==1 || eb.getIndexBlock()< 0 || eb.getExtentIndex() < 0) {
                  prta(Util.clear(runsb).append("OI: wrDat ").append(lastseedname).append(" eblk=").append(nextseq).
                          append(" RQBLK cnt=").append(eb.isContinuation() ? "t" : "f").
                          append(" iblk=").append(eb.getBlock()).append("  idx=").append(eb.getIndexBlock()).append("/").
                          append(eb.getExtentIndex()).append("/").append(eb.getBlock() % 64));
                  //}
                  seednameSB = lastseedname;
                  AccreteMiniSeed ms = accreters.get(Util.getHashFromSeedname(seednameSB));
                  if (ms == null) {
                    prta("OI: REQUESTED continuation block discarded.  No accrete is open for it!");
                    continue;
                  }
                  // See if this binary block will acccrete to any of the open AccreteMiniSeed objects
                  if (ms.willAccrete(eb)) {    // It will accrete to this one!
                    success = ms.add(eb);     // Add it, if it completes the block, and process it
                    if (success) {
                      Channel c = EdgeChannelServer.getChannel(ms.getSeedName());
                      if (c != null) {
                        nprocbig++;
                        prta(Util.clear(runsb).append("Accrete done ms=").append(MiniSeed.toStringRaw(ms.getData())));
                        for (EdgeOutputer outputer : outputers) {
                          outputer.processBlock(ms.getData(), c);
                        }                          
                        LHDerivedFromOI.sendToLHDerived(ms.getData());

                        ms.clear();
                      }
                    }
                    continue;
                  } else {
                    prta(Util.clear(runsb).append("OI: req blk coud not accrete - discard" + " eblk=").append(nextseq).
                            append(" cnt=").append(eb.isContinuation() ? "t" : "f").
                            append(" block skipped iblk=").append(eb.getBlock()).
                            append("  idx=").append(eb.getIndexBlock()).append("/").
                            append(eb.getExtentIndex()).append("/").append(eb.getBlock() % 64));
                    continue;
                  }
                }
                nillegalSeed++;
                prta(Util.clear(runsb).append("OI: illegal seedname found=").append(Util.toAllPrintable(seednameSB)).
                        append("|").append(eb.getSeedNameSB()).append(" eblk=").append(nextseq).append(" ").
                        append(EdgeBlockQueue.getNextSequence()).append(" cnt=").
                        append(eb.isContinuation() ? "t" : "f").append(" block skipped" + " iblk=").
                        append(eb.getBlock()).append("  idx=").append(eb.getIndexBlock()).append("/").
                        append(eb.getExtentIndex()).append("/").append(eb.getBlock() % 64));
                continue;
              }
              ndata++;
              if (nextseq % 50000 == 1) {
                prta(Util.clear(runsb).append("OI : nxtseq=").append(nextseq).
                        append(" EBQ.next=").append(EdgeBlockQueue.getNextSequence()).
                        append(" left=").append(EdgeBlockQueue.nleft(nextseq)).append("/").
                        append(EdgeBlockQueue.getMaxQueue()));
                lastStatus = System.currentTimeMillis();
              }
              if (EdgeBlockQueue.getDebug()) {
                EdgeBlockQueue.dbgprt(eb.toString() + " OI");
              }
              Channel c = EdgeChannelServer.getChannel(seednameSB);

              if (dbg || nextseq % 50000 == 1 || eb.getIndexBlock() < 0
                      || eb.getExtentIndex() < 0 || c == null
                      || (eb.isContinuation() && !Util.stringBuilderEqual(eb.getSeedNameSB(), REQUESTEDBLK))
                      || // continuation but not REQUESTED
                      (!eb.isContinuation() && MiniSeed.crackBlockSize(buf) > 512)) {
                prta(Util.clear(runsb).append("OI: wrDat ").append(seednameSB).append(" eblk=").append(nextseq).
                        append(" ").append(c != null ? Util.toHex(c.getSendtoMask()) : "null").
                        append(" cnt=").append(eb.isContinuation() ? "t" : "f").append(" iblk=").append(eb.getBlock()).
                        append("  idx=").append(eb.getIndexBlock()).append("/").
                        append(eb.getExtentIndex()).append("/").append(eb.getBlock() % 64));
              }

              // If there is not an existing channel record, note this and create one
              if (c == null) {
                prta(Util.clear(runsb).append("OI: channel not found create it ").append(seednameSB).
                        append(" rt=").append(MiniSeed.crackRate(buf)).append(" valid=").append(Util.isValidSeedName(seednameSB)));
                if (Util.isValidSeedName(seednameSB)) {
                  EdgeChannelServer.createNewChannel(seednameSB, MiniSeed.crackRate(buf), this);
                  c = EdgeChannelServer.getChannel(seednameSB);
                }
              }

              // If we have a channel record, process the data
              if (c != null) {
                if (c.getSendtoMask() == 0) {
                  continue;      // There are no outputs, so do not process it
                }                // If the channel is marked not for public distribution, inhibit it from the OI unless overriden by a -allowrestricted

                if (!eb.isContinuation() && MiniSeed.crackBlockSize(buf) == 512) {
                  nprocessed++;
                  for (EdgeOutputer outputer : outputers) {
                    outputer.processBlock(buf, c);
                  }
                  LHDerivedFromOI.sendToLHDerived(buf);
                } else {        // It needs to be processed by accretion
                  if (/*dbg && */!eb.isContinuation()) {
                    try {
                      //MiniSeed tmp = new MiniSeed(buf);
                      //prta("OI: long ms="+tmp.toString());
                      prt(Util.clear(runsb).append("OI: Long buffer = ").
                              append(MiniSeed.crackBlockSize(buf)).append(" ").append(buf.length).
                              append(" sd=").append(seednameSB));
                    } /*catch(IllegalSeednameException e) {
                      prt("OI: IllegalSeednameException on dbg conversion="+e.getMessage());
                    }*/ catch (RuntimeException e) {
                      prta(Util.clear(runsb).append(tag).append("OI: RuntimeException cracking MiniSeed (non fatal) e=").append(e.getMessage()));
                      if (getPrintStream() != null) {
                        e.printStackTrace(getPrintStream());
                      } else {
                        e.printStackTrace();
                      }
                    }
                  }
                  AccreteMiniSeed ms = accreters.get(Util.getHashFromSeedname(seednameSB));
                  if (ms == null) {       // There is no such list yet
                    if (!eb.isContinuation()) {
                      ms = new AccreteMiniSeed(eb, seednameSB);
                      accreters.put(Util.getHashFromSeedname(seednameSB), ms);
                      Util.clear(lastseedname).append(seednameSB);
                    } else {
                      if (!Util.stringBuilderEqual(eb.getSeedNameSB(), "REQUESTEDBLK")) {
                        prta(Util.clear(runsb).append("OI: discard continuation as first block of accretion").
                                append(seednameSB).append(eb));
                      }
                    }
                  } else {      // There already is a list. Try adding this block to each accreter
                    if (ms.willAccrete(eb)) {
                      success = ms.add(eb);
                      if (success) {
                        nprocbig++;
                        for (EdgeOutputer outputer : outputers) {
                          outputer.processBlock(ms.getData(), c);
                        }
                        LHDerivedFromOI.sendToLHDerived(ms.getData());
                        ms.clear();
                      }
                    } else {     // it did not accrete, if its a new one, then accreter is incomplete.
                      if (!eb.isContinuation()) {
                        prta(Util.clear(runsb).
                                append("OI: discard an accreter - new first block found and accreter is not empty").
                                append(ms));
                        ms.clear();
                        ms.add(eb);
                        Util.clear(lastseedname).append(seednameSB);
                      } else {
                        if (!Util.stringBuilderEqual(eb.getSeedNameSB(), "REQUESTEDBLK")) {
                          prta(Util.clear(runsb).append("OI: discard continuation as first block of accretion").
                                  append(seednameSB).append(eb.toStringBuilder(null)));
                        }
                      }
                    }
                  }
                }
              }
            } catch (IllegalSeednameException e) {
              prta(Util.clear(runsb).append("OI: block is not mini seed ").append(MiniSeed.toStringRaw(buf)));
              e.printStackTrace(getPrintStream());
            }
          } // End of a data block
          //prta("OI:bottom"+ (System.currentTimeMillis()-trip));
          if (terminate) {
            break;        // Early out if termination called for
          }
        }             // While there are more sequences

        // CHeck on change in configuration
        long now = System.currentTimeMillis();
        // Is it time to read the configuration
        if (now - lastConfig > 120000) {
          checkConfig();
          lastConfig = now;
        }
        // If its time, do some maintenance
        if (now - lastStale > 600000) {
          lastStale = System.currentTimeMillis();
          prta(Util.clear(runsb).append(Util.ascdate()).append(" OI: status nproc=").append(nprocessed).
                  append(" big=").append(nprocbig).append(" nillegalSeed=").append(nillegalSeed).
                  append(" nskip=").append(nskipped).append(" Left=").append(EdgeBlockQueue.nleft(nextseq)));
          if (nillegalSeed > 0) {
            SendEvent.edgeSMEEvent("BadSEEDOI", nillegalSeed + " bad names in OI", this);
          }
          nillegalSeed = 0;
          nprocessed = 0;
          nprocbig = 0;
          synchronized (accreters) {
            TLongObjectIterator<AccreteMiniSeed> itr = accreters.iterator();
            while (itr.hasNext()) {
              itr.advance();
              AccreteMiniSeed ms = itr.value();
              if ((now - ms.getLastUpdated()) > 600000 && ms.getNbytes() > 0) {
                try {
                  prt(Util.clear(runsb).append("OI: abandoned a stale MiniSeed Accrete ").append(new MiniSeed(ms.getData()).toStringBuilder(null)));
                } catch (IllegalSeednameException e) {
                  //byte [] bf = ms.getData();
                  prta(Util.clear(runsb).append("OI: IllegalSeedname in abandon accreted Miniseed=").
                          append(MiniSeed.crackSeedname(ms.getData())).append("| nd=").append(eb.getNodeSB()).
                          append(" ").append(eb.getJulian()).append(" blk=").append(eb.getBlock()));
                }
                ms.clear();          // Abandon it as hopeless
              }
            }
          }
        }
        try {
          sleep(10);
        } catch (InterruptedException expected) {
        }

      } // While termination has not been set
      catch (RuntimeException e) {
        prta(Util.clear(runsb).append(tag).append("OI: RuntimeException in ").append(this.getClass().getName()).append(" e=").append(e.getMessage()));
        if (getPrintStream() != null) {
          e.printStackTrace(getPrintStream());
        } else {
          e.printStackTrace();
        }
        if (e.getMessage() != null) {
          if (e.getMessage().contains("OutOfMemory")) {
            SendEvent.doOutOfMemory(tag, this);
            throw e;
          }
        }
      }
    }
    prta("OI: Thread is exitting");
    running = false;
    terminate = false;
    isRunningDBG = false;
    try {
      Runtime.getRuntime().removeShutdownHook(shutdown);
    } catch (Exception expected) {
    }
    theOutput = null;
  }

  /**
   * This just pauses for given number of millis - used by non-threads to wait
   * on this one.
   *
   * @param ms The number of milliseconds to pause before returning ( might
   * return early).
   */
  public void pause(int ms) {
    try {
      sleep(ms);
    } catch (InterruptedException expected) {
    }
  }

  /**
   * Inner class that will be the thread activated on shutdown() by the process.
   */
  class ShutdownOutputInfrastructureThread extends Thread {

    OutputInfrastructure thr;

    /**
     * Default constructor that does nothing. The shutdown hook starts the run()
     * thread.
     *
     * @param t The OutputInfrastructure thread that this object is in charge of
     * shutting down.
     */
    public ShutdownOutputInfrastructureThread(OutputInfrastructure t) {
      thr = t;
      gov.usgs.anss.util.Util.prta("new ThreadShutdown " + getName() + " " + getClass().getSimpleName());
    }

    // This is called by the RunTime.shutdown at any termination 
    @Override
    public void run() {
      prta(tag + " OI:  Shutdown() started... EBC.isShutdown=" + EdgeBlockClient.isShutdown());
      // We want the edgeblock clients down before we shut down the OI
      int loop = 0;
      while (!EdgeBlockClient.isShutdown()) {
        Util.sleep(1000);
        loop++;
        prta("OI: shutdown loop=" + loop);
        if (loop > 20) {
          System.err.println("OI: shutdown timed out waiting for EBC shutdown.");
          break;
        }
      }
      prta("OI: shutdown start outputers close");
      for (EdgeOutputer o : outputers) {
        if (o != null) {
          o.close();
        }
      }
      prta(tag + " OI: shutdown() EdgeBlockClient shutdown=" + EdgeBlockClient.isShutdown() + " loop=" + loop);
      thr.terminate();          // Send terminate to main thread and cause interrupt
      loop = 0;
      while (running) {
        prta(tag + " " + Util.asctime() + " " + Util.ascdate()
                + " OI: Shutdown() waiting for terminate to finish");
        try {
          sleep(4000L);
        } catch (InterruptedException expected) {
        }
        if (loop++ > 10) {
          prt("OI: shutdown() timeout- break out running=" + running + " active=" + thr.isAlive());
          break;
        }
      }
      prta(tag + " OI: Shutdown() of OutputInfrastructureThread is complete.");
    }
  }

  /**
   * The data comes here in EdgeBlocks with 512 bytes of payload. If a MiniSeed
   * block is longer than 512, we need to accumulate several to build up the
   * bigger block. The block with headers creates one of these, and each
   * following block accretes until its done.
   */
  public final class AccreteMiniSeed {

    StringBuilder seedname = new StringBuilder(12);
    byte[] b;            // Sized to the MiniSeed size
    int nbytes;
    boolean jumpExpected;
    int blk;                // The block number of the staring block
    long lastUpdated;       // Time of last change (used to time out blocks)
    int desired, mask;      // The desired bit mask for all of the records and the current mask
    int nblksIn;
    final StringBuilder tmpsb = new StringBuilder(50);

    public byte[] getData() {
      return b;
    }

    public long getLastUpdated() {
      return lastUpdated;
    }

    public StringBuilder getSeedName() {
      return seedname;
    }

    public int getNbytes() {
      return nbytes;
    }

    @Override
    public String toString() {
      return "AMS:" + seedname + " blk=" + blk + " mask=" + Util.toHex(mask) + " " + Util.toHex(desired) + " nb=" + nbytes;
    }

    public AccreteMiniSeed(EdgeBlock eb, StringBuilder name) {
      Util.clear(seedname).append(name);
      Util.rightPad(seedname, 12);
      prta(Util.clear(tmpsb).append("OI:AMS: blk=").append(blk).append(" seedname=").append(seedname).append(" len=").append(eb.get().length));
      b = new byte[4096];
      nbytes = 0;
      add(eb);
      /*if(dbg) */
      prt(Util.clear(tmpsb).append("OI:AMS: create AccreteMS =").append(seedname).
              append(" blk=").append(blk).append(" nb=").append(nbytes).
              append(" desired=").append(Util.toHex(desired)));
    }

    /**
     * Return true if this one will accrete.
     *
     * @param eb The edge block candidate to accrete to this one.
     * @return True if it will accrete to this one.
     */
    public boolean willAccrete(EdgeBlock eb) {
      if (nbytes == 0 && !eb.isContinuation()) {
        return true; // If its a 1st block and this is clear, return true
      }
      // Make sure this block fits in with the other blocks we have seen
      int tblk = blk;
      if (jumpExpected && eb.getBlock() % 64 == 0) {
        //prt("OI:AMS:block is expected to span allocation reset block="+blk+" nin="+nblksIn+" eb.blk="+eb.getBlock()+" msk="+Util.toHex(mask));
        tblk = eb.getBlock() - nblksIn;    // Reset to expect this block
      }
      return eb.getBlock() >= tblk && eb.getBlock() < tblk + (nbytes + 511) / 512;
//      if(eb.getBlock() < tblk || eb.getBlock() >= tblk+(nbytes+511)/512) return false;

    }

    /**
     * Add the edge block to this if it is the range.
     *
     * @param eb The edgeblock to add.
     * @return True if the block is now complete!
     */
    public boolean add(EdgeBlock eb) {
      try {
        // Is this the first block, insure its a good one
        if (nbytes == 0) {
          eb.getData(b);          // Get the data into the new, bigger buffer.
          nbytes = MiniSeed.crackBlockSize(b);           // Number so far
          if (nbytes != 4096) {
            prt(Util.clear(tmpsb).append("OI:AMS: got a AccreteMiniSeries 1st block with blocksize out of range!").append(nbytes));
            nbytes = 0;
            return false;
          }
          if (!MiniSeed.crackSeedname(b).equals(seedname.toString())) {
            prt(Util.clear(tmpsb).append("OI:AMS: accrete init found seedname did not match= do not startit ").
                    append(seedname).append(" ").append(MiniSeed.crackSeedname(b)));
            nbytes = 0;
            return false;
          }
          blk = eb.getBlock();
          desired = 0;
          for (int i = 0; i < (nbytes + 511) / 512; i++) {
            desired = (desired << 1) | 1;// Build up mask
          }
          jumpExpected = false;
          mask = 1;
          nblksIn = 0;
          prt(Util.clear(tmpsb).append("OI:AMS: init AccreteMS =").append(seedname).
                  append(" blk=").append(blk).append(" nb=").append(nbytes).
                  append(" desired=").append(Util.toHex(desired)));
        }
        if (jumpExpected && eb.getBlock() % 64 == 0) {
          prt("OI:AMS: block is expected to span allocation reset block=" + blk + " nin=" + nblksIn + " eb.blk=" + eb.getBlock() + " msk=" + Util.toHex(mask));
          blk = eb.getBlock() - nblksIn;    // Reset to expect this block
        }
        if (eb.getBlock() < blk || eb.getBlock() >= blk + (nbytes + 511) / 512) {
          prt("OI:AMS: Attempt to add a block out of range blk=" + blk + " eblock=" + eb.getBlock() + " size=" + nbytes);
          return false;
        }
        lastUpdated = System.currentTimeMillis();
        eb.getData(b, (eb.getBlock() - blk) * 512);
        mask = mask | (1 << (eb.getBlock() - blk));
        /*if(dbg)*/ prt(Util.clear(tmpsb).append("OI:AMS: add() to miniseed ").append(seedname).
                append(" blk=").append(blk).append(" eb.block()=").append(eb.getBlock()).
                append(" msk=").append(Util.toHex(mask)).append(" ").append(Util.toHex(desired)).
                append(" done=").append(mask == desired));
        if (mask == desired) {
          return true;
        }
        nblksIn++;
        jumpExpected = eb.getBlock() % 64 == 63;
        return false;
      } catch (IllegalSeednameException e) {
        Util.prt(Util.clear(tmpsb).append("Cracking a non seed block ").append(MiniSeed.toStringRaw(b)));
        e.printStackTrace();
        return false;
      }
    }

    public void clear() {
      nbytes = 0;
    }
  }

  /**
   * Unit test main - reads in a captured stream of bytes from a server and
   * plays them back.
   *
   * @param args Command line args.
   */
  public static void main(String[] args) {
    EdgeProperties.init();
    byte[] buf = new byte[EdgeBlock.BUFFER_LENGTH];
    String filename = "/Users/data/ebqsave.ebq";
    int waitms = 300;
    boolean dbgrep = false;
    boolean dbgibc = false;
    boolean dbgebq = false;
    for (int i = 0; i < args.length; i++) {
      if (args[i].equals("-f")) {
        filename = args[i + 1];
      }
      if (args[i].equals("-w")) {
        waitms = Integer.parseInt(args[i + 1]);
      }
      if (args[i].equals("-dbgall")) {
        dbgrep = true;
        dbgibc = true;
        dbgebq = true;
      }
      if (args[i].equals("-dbgrep")) {
        dbgrep = true;
      }
      if (args[i].equals("-dbgibc")) {
        dbgibc = true;
      }
      if (args[i].equals("-dbgebc")) {
        dbgebq = true;
      }
    }
    EdgeBlockQueue ebq = new EdgeBlockQueue(10000);
    EdgeBlockQueue.setDebug(dbgebq);
    // Start something that will be receiving blocks
    EdgeBlockClient ebc = new EdgeBlockClient("-p 7983 -h " + Util.getLocalHostIP(), "EGC1"); // Main() test
    OutputInfrastructure rep = null;
    if (dbgrep) {
      rep = new OutputInfrastructure("-dbg ", "RThr");
    } else {
      rep = new OutputInfrastructure("", "RThr");
    }
    //rep.setDebug(dbgrep);
    //IndexFileOutputInfrastructure.setDebug(dbgrep);
    IndexBlockCheck.setDebug(dbgibc);
    /*try {
      RawDisk file = new RawDisk(filename, "r");
      int len=0;
      int loop=0;
      while( (len = file.read(buf)) == EdgeBlock.BUFFER_LENGTH) {
        EdgeBlockQueue.queue(buf);
        if(waitms > 0) rep.pause(waitms);
        if(loop++ < 1000) rep.pause(2);      // during startup wait a bit to get going
        if(loop % 5 == 0) rep.pause(1);
      }
    }
    catch(IOException e) {prt("IOException reading file"+e.getMessage()); System.exit(0);}
    System.exit(0);*/
  }
}
