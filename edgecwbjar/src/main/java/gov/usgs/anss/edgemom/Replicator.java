/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.edgemom;

import gov.usgs.anss.edge.IndexBlockCheck;
import gov.usgs.anss.edge.IndexFile;
import gov.usgs.anss.edge.IndexFileReplicator;
import gov.usgs.anss.edge.EdgeProperties;
import gov.usgs.anss.edge.ChannelSender;
import gov.usgs.alarm.SendEvent;
import gov.usgs.anss.edge.*;
import gov.usgs.anss.edgethread.EdgeThread;
import gov.usgs.anss.seed.MiniSeed;
import gov.usgs.anss.util.SeedUtil;
import gov.usgs.anss.util.Util;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.GregorianCalendar;
//import gov.usgs.anss.indexutil.IndexBlockUtil;

/**
 * This is an EdgeThread that takes any data from the EdgeBlockQueue, creates,
 * and writes the replicated files onto this node. It uses the
 * IndexFileReplicator for handling the files. It also creates IndexCheckers for
 * each file when it is created. This object does the reconciliation between the
 * index and check files and issues requests for missing blocks to insure all of
 * the blocks are replicated - including through software shutdowns. In it's
 * main loop, it also does common clean up duties like closing files which have
 * not been used recently. For a replication to occur one or more
 * EdgeBlockClients must be configured on this instance to get the data from
 * some remote instance which must be running an EdgeBlockServer and
 * IndexFileServer thread.
 * <br>
 * NOTES : Replicators running on a CWB-CWB connection must have a -cwbcwb
 * ip.adr on their command line. In Edge-to-CWB mode the address of the place to
 * get Index files on startup are obtained from the EdgeBlockClient going to
 * that node. In CWB-CWB there are no node-by-node EdgeBlockClients, just a
 * client to the cwb. This replicator switch lets the replicator know this is
 * the case and provides the IP address.
 * <br>
 * <PRE>
 * It is an EdgeThread with the following arguments :
 * Tag   arg          Description
 * -dbg                Turn on debugging
 * -dbgrep             Turn on debugging in IndexFileReplicator
 * -dbgibc             Turn on debugging in IndexFileCheck
 * -dbgebq             Turn on debugging in EdgeBlockQueue
 * -dbgichk            Turn on debugging in the IndexCheckers.
 * -cwbcwb            This is a cwb-to-cwb replicator so IP addressing is correct
 * -limit   n          Limit output files to n days ago - used on non-CWB like gldpsd.
 * -stalesec  nn       The number of seconds a file needs to be unused to qualify as stale
 * >[>]  filename     You can redirect output from this thread to the filename in the current logging directory
 * </PRE>
 *
 * @author davidketchum
 */
public class Replicator extends EdgeThread {

  private static int nextseq = -1;  // Next seq to process. If a replicator dies, this preserves the seq for next incarnation
  public static int free = 0;
  public static boolean terminating;
  long lastStatus;               // Timer for status
  ShutdownReplicatorThread shutdown;    // The shutdown thread for terminations
  CheckDeadlockReplicator deadlock;
  private static HoldingSender udpHoldings;
  private ChannelSender cs;       // Normally null as replicators do not send channels
  private boolean dbg;
  private boolean dbgichk;
  private int nindex;                     // Count index packets processed
  private int ndata;                      // Count data packets processed
  private int lastNindex;                 // getStatus last time nindex
  private int lastNdata;                  // "         "         ndata
  private long lastGetStatus;             // For getStatus() time calc
  private int julianLimit;                // How many days from present in past will we take replication
  private int today;                      // Julian day representing today
  private int stalems;
  private boolean cwbcwb;                 // True if this is a cwb-to-cwb replicator
  private String cwbIP;                   // If CWB-to-CWB, then this is the IP of that CWB
  private static long lastPage;   // Damp the pager
  private static int state;
  private StringBuilder tmpsb = new StringBuilder(100);

  public static int getRepState() {
    return state;
  }
  //public static void registerHoldingSender(HoldingSender hs) {udpHoldings=hs;}
  boolean blockRequests;

  public boolean getCwbCwb() {
    return cwbcwb;
  }

  public int getJulianLimit() {
    return julianLimit;
  }

  public static HoldingSender getHoldingSender() {
    return udpHoldings;
  }

  /**
   * Set debug state.
   *
   * @param t The new debug state.
   */
  public void setDebug(boolean t) {
    dbg = t;
  }

  /**
   * Terminate thread (causes an interrupt to be sure).
   */
  @Override
  public void terminate() {
    terminate = true;
    terminating = true;
    interrupt();
  }
  /**
   * Return the monitor string for Icinga.
   *
   * @return A String representing the monitor key value pairs for this
   * EdgeThread.
   */
  long lastMonNData;

  @Override
  public StringBuilder getMonitorString() {
    Util.clear(monitorsb);
    long nb = ndata - lastMonNData;
    lastMonNData = ndata;
    return monitorsb.append("RepEBQLeft=").append(EdgeBlockQueue.nleft(nextseq)).append("\nRepNBlocks=").append(nb).append("\n");
  }

  /**
   * Return the status string for this thread.
   *
   * @return A string representing status for this thread.
   */
  @Override
  public StringBuilder getStatusString() {
    Util.clear(statussb);
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
    return statussb.append("nsq=").append(nextseq).append(" blkRqst=").append(blockRequests).
            append(" nidx=").append(nind).append(" ndt=").append(ndat).append(" b/s=").
            append(blksec).append(" nleft=").append(EdgeBlockQueue.nleft(nextseq)).append(" ").append(" nxtsq=").
            append(EdgeBlockQueue.getNextSequence()).append("\n").append(IndexFileReplicator.getStatusAll()).
            append("\n").append(IndexChecker.getStatusStringAll());
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

  @Override
  public String toString() {
    return toStringBuilder(null).toString();
  }

  public StringBuilder toStringBuilder(StringBuilder tmp) {
    StringBuilder sb = tmp;
    if (sb == null) {
      sb = Util.clear(tmpsb);
    }
    synchronized (sb) {
      sb.append("limit=").append(julianLimit).append(" stale=").append(stalems).append(" state=").append(state);
    }
    return sb;
  }

  /**
   * Creates a new instance of ReplicatorThread.
   * <br>
   * <br> Arguments are :
   * <br> -dbg Turn on debugging
   * <br> -dbgrep Turn on debugging in IndexFileReplicator
   * <br> -dbgibc Turn on debugging in IndexFileCheck
   * <br> -dbgebq Turn on debugging in EdgeBlockQueue
   * <br> -limit n Limit output files to n days ago.
   * <br> > To redirect output from this thread to an individual file
   *
   * @param argline The edgemom.setup line which started this thread
   * @param tag The edgemom.setup tag portion to be used as the beginning of
   * this thread's tag
   */
  public Replicator(String argline, String tag) {
    super(argline, tag);

    dbg = false;
    if (nextseq <= 0) {
      nextseq = 1;       // Note, if this is not the first replicator, we preserve the last seq
    }
    String[] args = argline.split("\\s");
    dbg = false;
    boolean dbgibc = false;
    boolean dbgebq = false;
    boolean dbgrep = false;
    cwbcwb = false;
    cwbIP = "";
    dbgichk = false;
    julianLimit = 2400000;
    lastGetStatus = System.currentTimeMillis();
    stalems = 43200000;
    int dummy = 0;
    for (int i = 0; i < args.length; i++) {
      //prt(i+" arg="+args[i]);
      if (args[i].equals("-dbg")) {
        dbg = true;
      } else if (args[i].equals("-limit")) {
        julianLimit = Integer.parseInt(args[i + 1]);
        i++;
      } else if (args[i].length() > 0 && args[i].substring(0, 1).equals(">")) {
        break;
      } else if (args[i].equals("-dbgibc")) {
        dbgibc = true;
      } else if (args[i].equals("-dbgebq")) {
        dbgebq = true;
      } else if (args[i].equals("-dbgrep")) {
        dbgrep = true;
      } else if (args[i].equals("-dbgichk")) {
        dbgichk = true;
      } else if (args[i].equals("-chansend")) {
        cs = new ChannelSender("", "Replicator", "Rep");
      } else if (args[i].equals("-cwbcwb")) {
        cwbcwb = true;
        cwbIP = args[i + 1];
        i++;
      } else if (args[i].equals("-stalesec")) {
        stalems = Integer.parseInt(args[i + 1]) * 1000;
        i++;
      } else if (args[i].equals("-empty")) {
        dummy = 1;
      } else {
        prt(Util.clear(tmpsb).append("ReplicatorThread unknown switch=").append(args[i]).append(" ln=").append(argline));
      }
    }
    setDaemon(true);
    // Set up debugs
    if (dbgebq) {
      EdgeBlockQueue.setDebug(dbgebq);
    }
    IndexFileReplicator.setDebug(dbgrep);
    IndexBlockCheck.setDebug(dbgibc);
    IndexChecker.setDebug(dbgichk);
    prta(Util.clear(tmpsb).append(Util.ascdate()).append(" Rep: created args=").append(argline).
            append(" tag=").append(tag).append(" dbgichk=").append(dbgichk).append(" cwbcwb=").append(cwbcwb).
            append(" ip=").append(cwbIP));
    shutdown = new ShutdownReplicatorThread(this);
    Runtime.getRuntime().addShutdownHook(shutdown);
    deadlock = new CheckDeadlockReplicator();
    start();
  }

  /**
   * This run() reads any data as it comes into the EdgeBlockQueue and writes it
   * out to the data and index file. It also periodically closes files if they
   * have not been used for a long time!
   */
  @Override
  public void run() {
    running = true;
    StringBuilder runsb = new StringBuilder(100);
    prta(Util.clear(runsb).append(" Rep: is starting at seq=").append(nextseq));
    int julian;
    String node;
    EdgeBlock eb = new EdgeBlock();
    IndexFileReplicator file;
    IndexChecker indexChecker = null;
    long lastStale = System.currentTimeMillis();
    lastStatus = System.currentTimeMillis();
    GregorianCalendar todayIs = new GregorianCalendar();  // We will reuse this to calculate today
    today = SeedUtil.toJulian(todayIs);                   // Julian date of today
    try {
      sleep(2000);
    } catch (InterruptedException expected) {
    }   // Give EdgeBlockClients a chance to start
    byte[] buf = new byte[512];
    udpHoldings = HoldingSender.getHoldingSender();
    int ebqmax = EdgeBlockQueue.getMaxQueue();
    int blockLimit = ebqmax - ebqmax * 2 / 100; // Block when less than 98% of queue is free
    int blockRelease = ebqmax - ebqmax / 100;  // Release when more than 99% of queue is free
    int eventLimit = ebqmax / 10;                // SendEvent when 10% of queue is free (and other warnings at 70%, 80%)
    int nremark = 0;
    try {
      while (!terminate) {
        state = 1;
        while (nextseq != EdgeBlockQueue.getNextSequence()) {
          state = 2;
          // Tell the IndexChecker to stop making requests if the queue is threatening to overflow
          free = EdgeBlockQueue.nleft(nextseq);
          if (free < blockLimit) {
            if (!blockRequests) {
              blockRequests = true;
              IndexChecker.setBlockRequests(true);
              prta(Util.clear(runsb).append("  ****** Rep: blockLeft ").append(free).
                      append(" nxt=").append(nextseq).append(" seq=").append(EdgeBlockQueue.getNextSequence()).
                      append(" ndata=").append(ndata));
            }
          } else if (blockRequests && free > blockRelease) {
            blockRequests = false;
            IndexChecker.setBlockRequests(false);
            prta(Util.clear(runsb).append("  ****** Rep: releaseLeft ").append(free).
                    append(" nxt=").append(nextseq).append(" seq=").append(EdgeBlockQueue.getNextSequence()).
                    append(" ndata=").append(ndata));
          }
          state = 3;
          if (nextseq % 2000 == 0) {
            int nl = EdgeBlockQueue.nleft(nextseq);
            if (nl < eventLimit) {
              prta(Util.clear(runsb).append("Rep: EdgeBlockQueue reached 90% max=").append(ebqmax).
                      append(" sq=").append(EdgeBlockQueue.getNextSequence()).append(" nxt=").
                      append(nextseq).append(" ndata=").append(ndata).append(" Left=").append(free));
              SendEvent.debugEvent("EBQ 90% Full",
                      "max=" + ebqmax + " sq="
                      + EdgeBlockQueue.getNextSequence() + " nxt=" + nextseq + " Left=" + free, this);
            } else if (nl < 2 * eventLimit) {
              prta(Util.clear(runsb).append("Rep: EdgeBlockQueue reached 80% max=").append(ebqmax).
                      append(" sq=").append(EdgeBlockQueue.getNextSequence()).
                      append(" nxt=").append(nextseq).append(" ndata=").append(ndata).append(" Left=").append(free));
              SendEvent.debugEvent("EBQ 80% Full",
                      "max=" + ebqmax + " sq="
                      + EdgeBlockQueue.getNextSequence() + " nxt=" + nextseq + " Left=" + free, this);
            } else if (nl < 3 * eventLimit) {
              prta(Util.clear(runsb).append("Rep: EdgeBlockQueue reached 70% max=").append(ebqmax).
                      append(" sq=").append(EdgeBlockQueue.getNextSequence()).
                      append(" nxt=").append(nextseq).append(" ndata=").append(ndata).append(" Left=").append(free));
              SendEvent.debugEvent("EBQ 70% Full",
                      "max=" + ebqmax + " sq="
                      + EdgeBlockQueue.getNextSequence() + " nxt=" + nextseq + " Left=" + free, this);
            } else if (nl < 4 * eventLimit) {
              prta(Util.clear(runsb).append("Rep: EdgeBlockQueue reached 60% max=").append(ebqmax).
                      append(" sq=").append(EdgeBlockQueue.getNextSequence()).
                      append(" nxt=").append(nextseq).append(" ndata=").append(ndata).append(" Left=").append(free));
              SendEvent.debugEvent("EBQ 60% Full",
                      "max=" + ebqmax + " sq="
                      + EdgeBlockQueue.getNextSequence() + " nxt=" + nextseq + " Left=" + free, this);
            } else if (nextseq % 100000 == 0) {
              prta(Util.clear(runsb).append("Rep: EdgeBlockQueue o.k.  max=").append(ebqmax).
                      append(" sq=").append(EdgeBlockQueue.getNextSequence()).append(" nxt=").append(nextseq).
                      append(" ndata=").append(ndata).append(" Left=").append(free).append(" ").append(free * 100 / ebqmax).append(" %"));
            }
          }
          state = 4;

          //long trip = System.currentTimeMillis();
          // Loop on each sequence until we get one!
          while (!EdgeBlockQueue.getEdgeBlock(eb, nextseq)) {    // Get an EdgeBLock into eb
            state = 5;
            if (nextseq % 100000 == 0) {
              prta(Util.clear(runsb).append("Rep: I am way behind!!! ").append(nextseq).
                      append(" ebq.seq=").append(EdgeBlockQueue.getNextSequence()));
              SendEvent.pageSMEEvent("EBCQueOvrflw", "max=" + ebqmax + " sq="
                      + EdgeBlockQueue.getNextSequence() + " nxt=" + nextseq, this);
            }
            nextseq = EdgeBlockQueue.incSequence(nextseq);
          }
          state = 6;

          // Got a sequence in our local eb.  Process it
          nextseq = EdgeBlockQueue.incSequence(nextseq);
          julian = eb.getJulian();       // <0 means a continuation of MS (no seed header present)

          if (Util.stringBuilderEqual(eb.getSeedNameSB(), "HEARTBEAT!!!")) {
            prta(Util.clear(runsb).append("got heartbeat from ").append(eb.getNode()));
            continue;
          }

          // If this data is older than julianLimit days ago, ignore it
          if (today - julian >= julianLimit - 2) {
            continue;      // Data is out of range for us
          }
          // If this instance of EdgeMom is replicating and serving as a edge node, do not do any replication!
          node = eb.getNode();
          if (node.trim().equals(IndexFile.getNode().trim())) {
            //Util.prta("Skip Replicator block from this node ="+eb.getNode());
            continue;
          }
          // If the julian day is not in range 1970 - +2136 or node is not right, reject it
          if (julian < 2446800 || julian > 2500000
                  || (!Character.isLetterOrDigit(node.charAt(0)) && node.charAt(0) != ' ')) {
            prta(Util.clear(runsb).append(" **** bad julian date/node in EdgeBlock jul=").append(julian).
                    append(" nd=").append(node).append(" blk rejected!"));
            SendEvent.pageSMEEvent("RepBadJulian", "Bad julian date/node jul=" + julian + " nd=" + node, this);
            continue;    // obviously bogus block!
          }

          state = 7;
          // Get this block's destination file (julian +  node is unique) or create it
          file = IndexFileReplicator.getIndexFileReplicator(Math.abs(julian), node);
          try {
            if (file != null) {
              if (file.isReadOnly()) {   // This must be opened by someone else, close it and open a read/write
                file.close();
                prta(Util.clear(runsb).append("IndexFileRep: found a readonly version of the file open.  CLose it! file=").
                        append(file.toStringBuilder(null)));
                while ((file = IndexFileReplicator.getIndexFileReplicator(Math.abs(julian), node)) != null) {
                  try {
                    sleep(100);
                  } catch (InterruptedException expected) {
                  }
                  prta(Util.clear(runsb).append("IndexFileRep: Wait for close to work. file=").append(file.toStringBuilder(null)));
                }
              }
              if (Util.stringBuilderEqual(eb.getSeedNameSB(), "FORCELOADIT!")) {  // Manually cause a force load
                try {
                  sleep(2000);
                } catch (InterruptedException expected) {
                }   // in case its a new open,let it happen
                prta(Util.clear(runsb).append(" ** got a FORCELOADIT - do a getFullIndex in response to file=").append(file.getFilename()));
                eb.getData(buf);              // Get the 512 bytes of data
                if (!getFullIndex(file, buf, false)) {
                  prta(Util.clear(runsb).append("   ** did not get FORCELOADIT index first time for ").append(file.toStringBuilder(null)));
                  if (!getFullIndex(file, buf, false)) {
                    prta(Util.clear(runsb).append("  **** did not get FORCELOADIT index file for ").append(file.toStringBuilder(null)));
                    SendEvent.debugEvent("GetIdxFailed", "Could not get " + file + " index", this);
                  }
                }
              }
            }
            state = 8;
            if (file == null) {      // File is not open, open it and close any stale ones
              state = 0;
              if (IndexFileReplicator.isShuttingDown()) {
                prta(Util.clear(runsb).append("IFR: is shutting down.  Do not open new file ").
                        append(SeedUtil.fileStub(julian)).append("_").append(node));
                continue;
              }
              state = 10;
              prta(Util.clear(runsb).append("Rep:open new IFR ").append(julian).
                      append(" nd=").append(node).append("|").append(" ndata=").append(ndata));
              try {
                file = new IndexFileReplicator(julian, node, eb.getBlock(), false, false);
                state = 33;
                prta(Util.clear(runsb).append("Rep:new IFR is opened as ").append(file.toStringBuilder(null)));
              } catch (EdgeFileDuplicateCreationException e) {
                prta(Util.clear(runsb).append("***** IFR: rare duplicate creation found.  Handle! e=").append(e));
                SendEvent.debugEvent("DupIFRCreate", "IFR: duplicat file creation e=" + e, this);
                file = IndexFileReplicator.getIndexFileReplicator(Math.abs(julian), node);
                if (file == null) {
                  Util.prt("This is impossible - no index file rep found for duplicate creation!");
                  Util.exit(0);
                }
              }
              state = 11;

              if (udpHoldings == null) {
                udpHoldings = HoldingSender.getHoldingSender();
              }
              // Get the full index file for this new file, this should not fail in two attempts!
              try {
                sleep(2000);
              } catch (InterruptedException expected) {
              }
              if (!getFullIndex(file, buf, true)) {
                prta(Util.clear(runsb).append("   ** did not get initial index first time for ").append(file.toStringBuilder(null)));
                if (!getFullIndex(file, buf, true)) {
                  prta(Util.clear(runsb).append("  **** did not get initial index file for ").append(file.toStringBuilder(null)));
                  SendEvent.debugEvent("GetIdxFailed", "Could not get " + file + " index", this);
                }
              }
              state = 12;
              //prta("Rep:close Old");
              IndexFileReplicator.closeStale(stalems);   // Close up any stale files
              IndexFileReplicator.closeJulianLimit(julianLimit, 120000);
              prta(Util.clear(runsb).append("IFR: Full status ndata=").append(ndata).append("\n").append(IndexFileReplicator.getFullStatusSB()));
              indexChecker = new IndexChecker((dbgichk ? "-dbg" : "-empty"), getTag() + "chk", file, this);
              file.registerIndexChecker(indexChecker);
              state = 13;
            } // The file exists, make sure it's index checker is still running and restart if needed.
            else if (file.getIndexchecker() == null) {
              prta(Util.clear(runsb).append("  IFR: start an IndexChecker on IFR=").append(file.toStringBuilder(null)).
                      append(" opened by something else"));
              indexChecker = new IndexChecker((dbgichk ? "-dbg" : "-empty"), getTag() + "chk", file, this);
              file.registerIndexChecker(indexChecker);
            } else if (!((IndexChecker) file.getIndexchecker()).isRunning() && !EdgeMom.isShuttingDown()) {  // Restart the index checker
              prta(Util.clear(runsb).append("  IFR: ***** Exception: Unusual restart of index checker").
                      append(file.getFilename()).append(" indexChk=").append(file.getIndexchecker()));
              indexChecker = new IndexChecker((dbgichk ? "-dbg" : "-empty"), getTag() + "chk", file, this);
              file.registerIndexChecker(indexChecker);
            }
            state = 14;
            eb.getData(buf);              // Get the 512 bytes of data
            // This is a still zeroed index block, do not process it
            state = 15;
            if (Util.stringBuilderEqual(eb.getSeedNameSB(), "HEARTBEAT!!!") && buf[0] == 0 && buf[1] == 0) {
              prta(Util.clear(runsb).append("got heartbeat from ").append(eb.getNode()));
              continue;
            }
            if (Util.stringBuilderEqual(eb.getSeedNameSB(), "INDEXFILESRV") && buf[0] == 0 && buf[1] == 0) {
              continue;
            }
            if (Util.stringBuilderEqual(eb.getSeedNameSB(), "FORCELOADIT!")) {  // Manuall cause a force load
              prta("IFR: got FORCELOAD for " + eb.toStringBuilder(null));
              continue;
            }     // This block came to force a load of a file

            state = 16;
            if (eb.getBlock() >= 0) {      // Is it a data block
              state = 17;
              ndata++;
              //prta("Rep:writeData " +nextseq);
              if (dbg || nextseq % 50000 == 1 || eb.getIndexBlock() < 0 || eb.getExtentIndex() < 0) {
                prta(Util.clear(runsb).append("Rep: writeData() iblk=").append(eb.getBlock()).
                        append("  idx=").append(eb.getIndexBlock()).append("/").append(eb.getExtentIndex()).append("/").
                        append(eb.getBlock() % 64).append(" sq=").append(nextseq - 1).
                        append(" euse=").append(EdgeBlockQueue.nleft(nextseq)).append(" ").
                        append(50000000 / (Math.max(1, System.currentTimeMillis() - lastStatus))).append(" b/s ndata=").append(ndata));
                udpHoldings = HoldingSender.getHoldingSender();
                if (udpHoldings != null) {
                  prta(Util.clear(runsb).append("HS: writeData ").append(udpHoldings.getStatusString()));
                }
              }
              if (nextseq % 50000 == 1) {
                lastStatus = System.currentTimeMillis();
              }
              if (eb.getIndexBlock() < 0) {      // How can this happen!
                Util.prta(Util.clear(runsb).append(" Got write of data block with negative index!!!! ").append(eb.getSeedNameSB()).
                        append(" blk=").append(eb.getBlock()).append(" indexBlk=").append(eb.getIndexBlock())
                        .append(" index=").append(eb.getBlock() % 64).append(" cont=").append(eb.isContinuation()));
                Util.prt(Util.bytesToSB(buf, 64, runsb));
              }
              state = 18;
              if (file.writeDataBlock(eb.getSeedName(), buf, eb.getBlock(), eb.getIndexBlock(), eb.getExtentIndex(), eb.isContinuation())) {
                nremark++;
                if (eb.isContinuation()) {
                  prta(Util.clear(runsb).append("Rep: remark of continuation block ").append(eb.getSeedNameSB()).
                          append(" blk=").append(eb.getBlock()).append(" ").append(eb.getIndexBlock()).append("/").
                          append(eb.getExtentIndex()).append(" ").append(nremark));
                } else {
                  try {
                    prta(Util.clear(runsb).append("Rep: remark of data block for ").append(eb.getSeedNameSB()).append(" ").
                            append(MiniSeed.crackSeedname(buf)).append(" ").append(SeedUtil.fileStub(MiniSeed.crackJulian(buf))).
                            append(" blk=").append(eb.getBlock()).append(" ").append(eb.getIndexBlock()).append("/").
                            append(eb.getExtentIndex()).append(" ").append(nremark));
                  } catch (RuntimeException e) {
                    prta(Util.clear(runsb).append("Rep: remark of data block with Runtime Error in MiniSeed crackers data=").
                            append(buf[0]).append(" ").append(buf[1]).append(" ").append(buf[2]).append("  blk=").
                            append(eb.getBlock()).append(" ").append(eb.getIndexBlock()).append("/").
                            append(eb.getExtentIndex()).append(" ").append(nremark));
                    SendEvent.debugEvent("BlkRemark", "remark of blk=" + eb.getBlock() + " " + eb.getIndexBlock() + "/" + 
                            eb.getExtentIndex() + " " + nremark, this);
                  }
                }
              }
              state = 19;
              if (EdgeBlockQueue.getDebug()) {
                EdgeBlockQueue.dbgprt(eb.toString() + " WDB");
              }
              state = 199;
              if (udpHoldings != null && !eb.isContinuation()) {
                udpHoldings.send(buf);
              }
              state = 191;
              try {
                if (cs != null) {
                  state = 192;
                  int[] time = MiniSeed.crackTime(eb.getData());
                  cs.send(eb.getJulian(), time[0] * 3600000 + time[1] * 60000 + time[2] * 1000 + time[3] / 10, eb.getSeedNameSB(), // wasMiniSeed.crackSeedname(eb.getData()),
                          MiniSeed.crackNsamp(eb.getData()), MiniSeed.crackRate(eb.getData()), 512);
                }
              } catch (IllegalSeednameException e) {
                state = 193;
              }
              state = 20;
            } else {                      // It is an index block
              state = 21;
              if ((Util.stringBuilderEqual(eb.getSeedNameSB(), "INDEXFILESRV")
                      || Util.stringBuilderEqual(eb.getSeedNameSB(), "CONTROLBLK"))
                      && eb.getIndexBlock() < 4) {
                prta(eb.toStringBuilder(null));
              }
              //prta("Rep:writeIndex"+nextseq);
              if (dbg || nextseq % 50000 == 1) {
                prta(Util.clear(runsb).append("wrIdx").append(Util.leftPad("" + eb.getIndexBlock(), 5)).
                        append("/").append(Util.rightPad("" + eb.getExtentIndex(), 2)).append(" ").
                        append(eb.getJulian()).append("_").append(eb.getNode()).append(" seq=").
                        append(Util.rightPad("" + (nextseq - 1), 9)).append(" ebqused=").
                        append(Util.rightPad("" + (EdgeBlockQueue.getNextSequence() - nextseq), 5)).append(" ").
                        append(50000000 / (Math.max(1, System.currentTimeMillis() - lastStatus))).append(" blks/sec"));
              }
              if (nextseq % 50000 == 1) {
                lastStatus = System.currentTimeMillis();
              }
              try {
                file.writeIndexBlock(buf, eb.getIndexBlock(), eb.getExtentIndex(), eb.getSeedName());
              } catch (RuntimeException e) {
                prta(e.getMessage());
                prta(eb.toStringBuilder(null));
                e.printStackTrace(this.getPrintStream());
              }
              nindex++;
              state = 22;
            }
            //prta("Rep:bottom"+ (System.currentTimeMillis()-trip));
          } catch (FileNotFoundException e) {
            prta(Util.clear(runsb).append("Rep: file not found for ").
                    append(SeedUtil.fileStub(julian)).append("_").append(node).
                    append(" msg=").append(e == null ? "null" : e.getMessage()));
          } catch (IOException e) {
            prta(Util.clear(runsb).append("Rep: IO error opening/writing file for ").append(julian).
                    append(" ").append(SeedUtil.fileStub(julian)).append("_").append(node).
                    append(" msg=").append(e == null ? "null" : e.getMessage()));
            state = 23;
            if (e != null) {
              if (e.getMessage().contains("No space left")) {
                if (System.currentTimeMillis() - lastPage > 300000) {
                  lastPage = System.currentTimeMillis();
                  SendEvent.edgeEvent("NoSpaceLeft", "No space left " + this.getClass().getName() + " on " + IndexFile.getNode() + "/" + Util.getAccount(), this);
                }
              } else {
                e.printStackTrace(this.getPrintStream());
              }
            } else {
              SendEvent.debugEvent("RepDiskErr", "error opening/writing file for " + julian + "_" + node + " msg=" + (e == null ? "null" : e.getMessage()), this);
              if (e != null) {
                e.printStackTrace(this.getPrintStream());
              }
            }
          } catch (EdgeFileReadOnlyException e) {
            prta(Util.clear(runsb).append("Rep: ReadOnlyException for ").append(julian).append("_").
                    append(node).append(" msg=").append(e.getMessage()));
          } catch (IllegalSeednameException e) {
            prta(Util.clear(runsb).append("Rep: IllegalSeedname=").append(eb.getSeedNameSB()).
                    append("| nd=").append(eb.getNode()).append(" ").append(SeedUtil.fileStub(eb.getJulian())).
                    append(" blk=").append(eb.getBlock()).append(" idx=").append(eb.getIndexBlock()).append("/").
                    append(eb.getExtentIndex()));
            e.printStackTrace();
            e.printStackTrace(this.getPrintStream());
          }
          if (terminate) {
            break;        // Early out if termination is called for
          }
          state = 30;
        }             // While there are more sequences
        // If it is time, do some maintenance
        state = 24;
        if (System.currentTimeMillis() - lastStale > 600000) {
          lastStale = System.currentTimeMillis();
          IndexFileReplicator.writeStale();
          if (julianLimit < 2400000 && lastStale % 86400000L > 1200000) {
            IndexFileReplicator.closeJulianLimit(julianLimit, 120000);
            long deleted = IndexFileReplicator.deleteJulianLimit(julianLimit);
            if (deleted > 1000000) {
              Util.prta(Util.clear(runsb).append("Purge to limit of ").append(julianLimit).
                      append(" freed ").append(deleted / 1000000).append(" mB elapse=").
                      append(System.currentTimeMillis() - lastStale));
            }
          }
          todayIs.setTimeInMillis(lastStale);     // update time in todayIs to current
          today = SeedUtil.toJulian(todayIs);     // set today julian date
        }
        state = 25;
        try {
          sleep(10);
        } catch (InterruptedException expected) {
        }

      }               // While termination has not been set
    } catch (RuntimeException e) {
      state = 26;
      if (e != null) {
        if (e.getMessage() != null) {
          if (e.getMessage().contains("Shutdown in progress")) {
            prta(Util.clear(runsb).append(tag).append("Rep: Got Shutdown in progress"));
          } else if (e.getMessage().contains("OutOfMemory")) {
            SendEvent.doOutOfMemory(tag, this);
            throw e;
          } else {
            SendEvent.edgeSMEEvent("RepRuntime", "RuntimeExcept e=" + e.toString().replaceAll("Exception", "Except"), this);
            prta(Util.clear(runsb).append(tag).append("Rep: RuntimeException in ").append(this.getClass().getName()).append(" e=").append(e.getMessage()));
            e.printStackTrace(getPrintStream());
          }
        } else {
          SendEvent.edgeSMEEvent("RepRuntime", "RuntimeExcept e=" + e.toString().replaceAll("Exception", "Except"), this);
          prta(Util.clear(runsb).append(tag).append("Rep: RuntimException e=").append(e));
          e.printStackTrace(getPrintStream());
        }
      }
      state = 27;
      if (e != null) {
        if (getPrintStream() != null) {
          e.printStackTrace(getPrintStream());
        } else {
          e.printStackTrace();
        }
      }
      terminate();
    }
    state = 28;
    // Replicator should never exit unless terminated.  Normally an unhandled runtime exception
    if (!terminate) {
      SendEvent.edgeSMEEvent("RepExit", tag + " is exiting unterminated!", this);
    }
    prta("Rep: Thread is exitting");
    if (indexChecker != null) {
      indexChecker.terminate();
    }
    if (cs != null) {
      cs.close();
    }
    running = false;
    terminate = false;
    try {
      Runtime.getRuntime().removeShutdownHook(shutdown);
    } catch (Exception expected) {
    }
    state = 29;
    //IndexFileReplicator.closeAll();   // Not needed, a shutdown handler is registered in this class
  }

  /**
   * Connect to the IndexFileServer and get the index and inject it.
   *
   * @param file The IndexFileReplicator to do this for.
   * @param buf A buffer at least 512 bytes long to use as scratch space.
   * @param first First time, do raw writes to file.
   * @return True if index was successfully obtained.
   */
  public boolean getFullIndex(IndexFileReplicator file, byte[] buf, boolean first) {
    // Create the thread that will read in the index blocks, it will die when finished
    // The waitForIndex() has a time out at the parameter given, so we cannot be stuck here
    // Then write out all of the blocks from the index returned (skipping any zero filled buf)
    if (file == null) {
      return false;
    }
    int julian = file.getJulian();
    String node = file.getNode();
    prta(Util.clear(tmpsb).append("Start IFRIDX for ").append(SeedUtil.fileStub(julian)).
            append("_").append(node).append(" first=").append(first));
    try {
      long timeit = System.currentTimeMillis();
      EdgeBlockClient getIndex = new EdgeBlockClient("-p 7979 -j " + julian + " -node " + node.trim()
              + (cwbcwb ? " -h " + cwbIP : "") + " >>repidxfile", "IFR");
      ArrayList<EdgeBlock> idxBlocks = getIndex.waitForIndex(30000);  // Get index blocks

      if (idxBlocks == null) {
        prta(Util.clear(tmpsb).append("Failed to get IFRIDX index block in 20 seconds!  Time to Panic ").
                append(SeedUtil.fileStub(julian)).append(" node=").append(node).append(" elapse=").
                append(System.currentTimeMillis() - timeit));
        return false;
      } else {
        prta(Util.clear(tmpsb).append("Got IFRIDX index blocks size=").append(idxBlocks.size()).
                append(" ").append(SeedUtil.fileStub(julian)).append("_").append(node).
                append(" free=").append(EdgeBlockQueue.nleft(nextseq)).append("/").append(nextseq).
                append("/").append(EdgeBlockQueue.getNextSequence()).
                append(" elapse=").append(System.currentTimeMillis() - timeit));
        long last = System.currentTimeMillis();
        for (int i = 0; i < idxBlocks.size(); i++) {
          EdgeBlock block = idxBlocks.get(i);
          block.getData(buf);
          if (file.getRawDisk() == null) {
            getIndex.freeIndexBlocks();
            throw new RuntimeException("Replicator.getFullIndex() found file closed.");
          }
          try {
            //prta(i+" "+SeedUtil.fileStub(julian)+" node="+node+" write idx="+block.getIndexBlock()+" ext="+block.getExtentIndex());
            //IndexBlockUtil iutil = new IndexBlockUtil(buf, block.getIndexBlock());
            //prta(" DUMP : "+i+" "+iutil+" iblkd="+block.getIndexBlock()+" "+file.toString());
            //if(iutil.seedName.contains("MYSBM  BHE")) {
            //  prta(" Load block 1st="+first+" iblk="+block.getIndexBlock()+" to "+file.toString());
            //  prta(iutil.toString());
            //}
            if (buf[0] != 0 || buf[1] != 0 || buf[2] != 0 || buf[3] != 0 || buf[4] != 0) {
              if (first) {
                file.writeIndexBlock(buf, block.getIndexBlock(), block.getExtentIndex(), block.getSeedName());
              } else {
                file.getRawDisk().writeBlock(block.getIndexBlock(), buf, 0, 512);   // Write block (where ever it is) in .idx
              }
            }
          } catch (IllegalSeednameException | IOException e) {
            prta(i + " IFRIDX writing block e=" + e);
          } catch (RuntimeException e) {               // Normally this is failure to get the index
            prta(e.getMessage());
            prta(i + " IFRIDX Error on block=" + block.toStringBuilder(null) + " buf=" + Arrays.toString(buf) + " file=" + file + " raw=" + file.getRawDisk());
            if (getPrintStream() == null) {
              e.printStackTrace();
            } else {
              e.printStackTrace(this.getPrintStream());
            }
            if (EdgeMom.isShuttingDown()) {
              try {
                sleep(30000);
              } catch (InterruptedException expected) {
              }
              break;
            }
            getIndex.freeIndexBlocks(); // Free any blocks before leaving
            throw e;
          }
        }
        last = System.currentTimeMillis() - last;
        prta(Util.clear(tmpsb).append("Done IFRIDX writing initial index. ").
                append(SeedUtil.fileStub(julian)).append("_").append(node).
                append(" elapse=").append(last / 1000).append(" b/s=").append(idxBlocks.size() * 1000 / Math.max(last, 1)).
                append(" free=").append(EdgeBlockQueue.nleft(nextseq)).append("/").append(nextseq).append("/").append(EdgeBlockQueue.getNextSequence())
        );
      }
      getIndex.freeIndexBlocks();     // Free the blocks used to make this file
    } catch (RuntimeException e) {
      SendEvent.edgeSMEEvent("RepRuntime", "RuntimeExcept IFRIDX e=" + e.toString().replaceAll("Exception", "Except"), this);
      prta("Got Runtime trying to build IFRIDX.  This should not happen. "
              + SeedUtil.fileStub(julian) + "_" + node + " Index file not read e=" + e.toString().replaceAll("Exception", "Except"));
      e.printStackTrace(getPrintStream());
      return false;
    }
    return true;
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
   * Inner class to be the thread activated on shutdown() by the process.
   */
  class ShutdownReplicatorThread extends Thread {

    Replicator thr;

    /**
     * Default constructor does nothing. The shutdown hook starts the run()
     * thread.
     *
     * @param t The thread we will shutdown.
     */
    public ShutdownReplicatorThread(Replicator t) {
      thr = t;
      gov.usgs.anss.util.Util.prta("new ThreadShutdown " + getName() + " " + getClass().getSimpleName());
    }

    // This is called by the RunTime.shutdown at any termination 
    @Override
    public void run() {
      System.err.println(tag + " Rep: shutdown() started...");
      int loop = 0;
      // We want the edgeblock clients down before we shut down the replicator
      while (!EdgeBlockClient.isShutdown()) {
        Util.sleep(1000);
        loop++;
        if (loop > 20) {
          break;
        }
      }
      System.err.println(tag + " Rep: shutdown() EdgeBlockClient shutdown=" + EdgeBlockClient.isShutdown() + " loop=" + loop);
      loop = 0;
      thr.terminate();          // Send terminate to main thread and cause interrupt
      while (running && loop < 10) {
        System.err.println(tag + " " + Util.asctime() + " " + Util.ascdate()
                + " Rep: Shutdown() waiting for terminate to finish " + loop);
        try {
          sleep(4000L);
        } catch (InterruptedException expected) {
        }
        loop++;
        //if(loop++ > 10) {this.destroy(); Util.prt("Rep: shutdown() send destroy");break;}
      }
      if (running) {
        System.err.println(tag + " " + Util.asctime() + " Rep thread did not exit on its own!");
      }
      System.err.println(tag + " Rep: shutdown() of ReplicatorThread is complete.");
    }
  }

  class CheckDeadlockReplicator extends Thread {

    @Override
    public String toString() {
      return tag;
    }

    public CheckDeadlockReplicator() {
      gov.usgs.anss.util.Util.prta("new ThreadMonitor " + getName() + " " + getClass().getSimpleName());
      setDaemon(true);
      start();
    }

    @Override
    public void run() {
      int last = nextseq;
      int count = 0;
      while (!terminate) {
        try {
          sleep(60000);
        } catch (InterruptedException expected) {
        }
        if (nextseq == last) {
          if (count > 3) {
            SendEvent.pageSMEEvent("RepDeadlock", " is not making progress! st=" + Replicator.getRepState()
                    + " ebdbg=" + EdgeBlockQueue.getDebug()
                    + " ifr=" + IndexFileReplicator.getStateLock() + " " + nextseq, "Replicator");
          }
          count++;
          if (count > 15) {
            Util.prta("******** Replicator deadlock panic.  Call system.exit() " + nextseq);
            Util.exit(1);
          }
        } else {
          if (count > 3) {
            SendEvent.pageSMEEvent("RepDeadlkClr", "Replicator deadlock clear! cnt=" + count + " st=" + Replicator.getRepState() + " ifr="
                    + IndexFileReplicator.getStateLock(), "Replicator");
          }
          count = 0;
        }
        last = nextseq;
      }
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
    // Start something receiving blocks
    EdgeBlockClient ebc = new EdgeBlockClient("-p 7983 -h " + Util.getLocalHostIP(), "EGC1");// main() test
    Replicator rep = null;
    if (dbgrep) {
      rep = new Replicator("-dbg ", "RThr");
    } else {
      rep = new Replicator("", "RThr");
    }
    IndexFileReplicator.setDebug(dbgrep);
    //rep.setDebug(dbgrep);
    //IndexFileReplicator.setDebug(dbgrep);
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
    catch(IOException e) {Util.prt("IOException reading file"+e.getMessage()); Util.exit(0);}
    System.exit(0);*/
  }
}
