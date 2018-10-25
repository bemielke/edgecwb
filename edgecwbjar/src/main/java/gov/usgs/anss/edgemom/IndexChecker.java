/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.edgemom;

import gov.usgs.anss.edge.IndexBlockCheck;
import gov.usgs.anss.edge.MasterBlock;
import gov.usgs.anss.edge.IndexFile;
import gov.usgs.anss.edge.IndexFileReplicator;
import gov.usgs.anss.edge.IndexBlock;
import gov.usgs.anss.edge.EdgeProperties;
import gov.usgs.alarm.SendEvent;
import gov.usgs.anss.edge.IllegalSeednameException;
import gov.usgs.anss.edge.RawDisk;
import gov.usgs.anss.edge.EdgeFileReadOnlyException;
import gov.usgs.anss.edge.EdgeFileDuplicateCreationException;
import gov.usgs.anss.edgethread.EdgeThread;
import gov.usgs.anss.indexutil.IndexBlockUtil;
import gov.usgs.anss.indexutil.IndexBlockUtilPool;
import gov.usgs.anss.util.SeedUtil;
import gov.usgs.anss.util.Util;
import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.GregorianCalendar;

/**
 * This class checks a series of IndexBlocks against a series of
 * IndexCheckBLocks and sends requests to fill in any discrepancies to a
 * EdgeBlockServer. This Thread is started by the Replicator class when a file
 * is being replicated at the time the file is discovered. This class is not
 * normally setup to run from a edgemom.setup. It is responsible for checking
 * one file.
 * <br>
 * The class also periodically forces full index file (.idx) fetches from the
 * EdgeNodes
 * <p>
 * Method : 1) On startup, each channel is scanned through all of the Index
 * blocks from the beginning and IndexBlockUtil is used to check each block for
 * being complete. If so, the block is marked as done in the bit set. If not,
 * the bit is left unset for stage 2. 2) For each block marked as false on the
 * blkset BitSet, the idxblock from the remote computer is compared to the
 * IndexCheckBlock from the IndexFileWrite if it is the currently open one for
 * this channel, or the check block is read from disk. If there are missing
 * blocks, then requests are made and sent to the EdgeBlockClient to all of the
 * remote nodes. The one with the data will eventually send the data and this
 * test will show completed blocks.
 *
 * <br><br>
 * -dbg Run in debug mode.
 *
 * @author davidketchum
 */
public final class IndexChecker extends EdgeThread {

  private static final ArrayList<IndexChecker> checkers = new ArrayList<>(20);  // Of all objects of this type.
  private static final IndexBlockUtilPool pool = new IndexBlockUtilPool();  // Pool to control IndexBloclUtils as needed by all thread
  private static int countThreads = 0;
  private final int threadNumber;
  private RawDisk rw;       // RawFile to the index file of the IndexFileReplicator being checked
  private RawDisk chk;      // RawFile to the check index of the IndexFileReplicator being checked
  private RawDisk dataFile; // RawFile to the data file of the IndexFileReplicator being checked
  private IndexFileReplicator idx;// The IndexFileReplicator being checked
  private static long lastReload = System.currentTimeMillis();     // Time that last index file reload happened, used to insure they are spaced out
  private BitSet blkset;
  private static boolean blockRequests;   // When true, no requests will be issued

  // The ctl buf is the first block and contains pointers to the master blocks
  private final byte[] ctlbuf = new byte[512];   // The buffer wrapping the ctl ByteBuffer
  private final ByteBuffer ctl;           // The header portion of this file wrapping ctlbuf
  private int length;               // First bytes of ctl buf (no used but decoded from header)
  private int next_extent;          // Next place to write data from the ctl buf (not used but decoded from header)
  private short next_index;         // Next index block to use from the ctl buf
  private final short mBlocks[] = new short[IndexFile.MAX_MASTER_BLOCKS];  // Array of blocks currently master_blocks, 0=unallocated
  private long blocksRequested;// Total count of blocks requested
  private long lastBlocksRequested;// For status, get differentiation counter
  private long lastStatus;          // Time of last status
  private long lastGetFull;
  private Replicator replicator;    // The replicator if any, that created this thread.
  private int julianLimit;          // The limit in days that can be in replication
  private boolean cwbcwb;           // Is this a cwbcwb replication
  private int state;

  // These are the master blocks as master Block objects built by reading them in
  private final MasterBlock mb[] = new MasterBlock[IndexFile.MAX_MASTER_BLOCKS];  // Space for master block objects

  private void doExit() {
    prta(tag + "doExit idx=" + idx + " null out all structures");
    running = false;
    terminate = false;
    rw = null;
    chk = null;
    dataFile = null;
    idx = null;
    blkset = null;
    replicator = null;
    try {
      if (!EdgeMom.isShuttingDown()) {
        Runtime.getRuntime().removeShutdownHook(shutdown);
      }
    } catch (IllegalStateException e) {
    }
  }
  // Variables needed for debug and shutdown
  private ShutdownIndexChecker shutdown;
  private static boolean dbg;

  /**
   * Set the block Requests flag. Usually set by the main emptier of real time
   * data when the queue is getting too full. This stops blocks from being
   * requested when true.
   *
   * @param t The state to set the block Requests flag.
   */
  public static void setBlockRequests(boolean t) {
    blockRequests = t;
  }

  /**
   * Set debug state.
   *
   * @param t The new debug state.
   */
  public static void setDebug(boolean t) {
    Util.prt("IC set debug=" + t);
    dbg = t;
  }

  /**
   * Terminate thread (causes an interrupt to be sure). You may not need the
   * interrupt!
   */
  @Override
  public void terminate() {
    prta("IndexChecker: terminate called " + (idx == null?"null":idx.getFilename()));
    new RuntimeException("Tracking IndexCheck.terminate()").printStackTrace(getPrintStream());
    terminate = true;
    interrupt();
  }
  /**
   * Return the monitor string for Icinga
   *
   * @return A String representing the monitor key value pairs for this
   * EdgeThread.
   */
  long lastMonBlocksRequested;

  @Override
  public StringBuilder getMonitorString() {
    if (monitorsb.length() > 0) {
      monitorsb.delete(0, monitorsb.length());
    }
    long blks = blocksRequested - lastMonBlocksRequested;
    lastMonBlocksRequested = blocksRequested;
    String tg = tag.replaceAll("\\[", "").replaceAll("\\]", "");
    return monitorsb.append(tg).append("NBlocksRequested=").append(blks).append(" state=").append(state).append("\n");
  }
  private final StringBuilder tmpsb = new StringBuilder(50);

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
      sb.append("CHK: ").append(rw == null ? "Null" : rw.getFilename()).append(" running=").append(isRunning()).
              append(" state=").append(state);
    }
    return sb;
  }

  /**
   * These force the log output through the parent replicator so that the output
   * from multiple IndexCheckers do not interfere with each other in mid line.
   *
   * @param sb
   */
  /* This was commented when the setEdgeThreadParent class was used to cause all logging to parent thread.
  @Override
  public final void prt(StringBuilder sb) {if(replicator == null) super.prt(sb); else replicator.prt(sb);}
  @Override
  public final void prta(StringBuilder sb) {if(replicator == null) super.prta(sb); else replicator.prta(sb);}
  @Override
  public void prt(String sb) {if(replicator == null) super.prt(sb); else replicator.prt(sb);}
  @Override
  public void prta(String sb) {if(replicator == null) super.prta(sb); else replicator.prta(sb);}*/

  /**
   * Return a string with all of the Checkers status.
   *
   * @return The status string.
   */
  public static String getStatusStringAll() {
    String s = "";
    for (IndexChecker checker : checkers) {
      if (checker != null) {
        s += checker.getStatusString() + "\n";
      }
    }
    return s;
  }

  /**
   * Return the status string for this thread
   *
   * @return A string representing status for this thread.
   */
  @Override
  public StringBuilder getStatusString() {
    if (statussb.length() > 0) {
      statussb.delete(0, statussb.length());
    }
    long blks = blocksRequested - lastBlocksRequested;
    long diff = System.currentTimeMillis() - lastStatus;
    lastBlocksRequested = blocksRequested;
    lastStatus = System.currentTimeMillis();

    return statussb.append("   ").append(tag).append(" totblk=").append(blocksRequested).
            append(" blks=").append(blks).append(" ").append(diff == 0 ? 0 : (blks * 1000 / diff)).
            append(" b/s run=").append(isRunning()).append(" state=").append(state);
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
   * Creates a new instance of IndexChecker which checks the index blocks in one
   * IndexFileReplicator against the check blocks and makes requests for missing
   * data.
   *
   * @param argline The argline for passing the EdgeThead (only -dbg is used
   * here).
   * @param tg The EdgeThread tag to use for output.
   * @param id The IndexFileReplicator object that is to be checked (basically
   * links to the files).
   * @param rep The mother Replicator class this was started from, used to set
   * the output print stream.
   */
  public IndexChecker(String argline, String tg, IndexFileReplicator id, Replicator rep) {
    super(argline, tg);
//initThread(argline,tg);
    idx = id;
    if (rep != null) {
      //setPrintStream(rep.getPrintStream());
      setEdgeThreadParent(replicator);
      replicator = rep;
      julianLimit = rep.getJulianLimit();
      cwbcwb = rep.getCwbCwb();
    }
    threadNumber = countThreads++;
    lastStatus = System.currentTimeMillis();
    lastGetFull = lastStatus + (id.getJulian() % 50 + threadNumber % 5) * 30000;

    tag = tag + "[" + id.getFilename().substring(id.getFilename().indexOf("/20") + 6) + "]";
    rw = id.getRawDisk();
    chk = id.getCheckRawDisk();
    dataFile = idx.getDataRawDisk();
    ctl = ByteBuffer.wrap(ctlbuf);
    if (EdgeMom.isShuttingDown()) {
      return;
    }
    prta(Util.clear(tmpsb).append(tag).append(" Construct chk=").append(chk == null ? "null" : chk.toString()).
            append(" idx.length=").append(rw == null ? "null" : rw.toString()).
            append(" data.length=").append(dataFile == null ? "null" : dataFile.toString()));
    if (rw == null) {
      return;
    }
    for (int i = 0; i < IndexFile.MAX_MASTER_BLOCKS; i++) {
      mb[i] = null;
      mBlocks[i] = 0;
    }
    try {
      blkset = new BitSet(Math.max((int) (rw.length() / 512), 4000));
    } catch (IOException e) {
      Util.IOErrorPrint(e, tag + " Building a bitset in IndexChecker");
    }

    // Create the bit set we will use for determining what needs to be checked
    String[] args = argline.split("\\s");
    for (String arg : args) {
      //prt(i+" arg="+args[i]);
      if (arg.equals("-dbg")) {
        dbg = true;
        // else if(args[i].equals("-h")) { host=args[i+1]; i++;}
      } else if (arg.substring(0, 1).equals(">")) {
        break;
      } else if (arg.equals("-empty")) {
      } else {
        prt(Util.clear(tmpsb).append(tag).append(" IndexChecker: unknown switch=").append(arg).
                append(" ln=").append(argline));
      }
    }
    prta(Util.clear(tmpsb).append(tag).append(" IndexChecker created args=").append(argline).
            append(" dbg=").append(dbg).append(" data=").append(dataFile.getFilename()));
    shutdown = new ShutdownIndexChecker(this);
    Runtime.getRuntime().addShutdownHook(shutdown);
    running = true;
    start();
  }

  private void waitNice(long sleep) {
    while (sleep > 0) {
      try {
        sleep(Math.min(1000, sleep));
      } catch (InterruptedException e) {
      }
      sleep -= 1000;
      if (terminate) {
        break;
      }
    }
  }

  /**
   * Monitors the index blocks and the check blocks on the "replicator" end and
   * looks for blocks that need to be resent. Since it is intimately connected
   * to the indexBlock and checks block lists within IndexFile and
   * IndexBlockCheck, it has to be run.
   */
  @Override
  public void run() {
    checkers.add(this);               // Add to static known list of this type object
    running = true;
    IndexBlockUtil idxblk = null;
    IndexBlockUtil chkblk = null;
    // Index block storage
    byte[] idxbuf = new byte[512];     // Array for index block reads
    byte[] chkbuf = new byte[512];      // Array for check index block reads
    int[] missing = new int[67];
    ArrayList<IndexBlockUtil> finals = new ArrayList<>(1000);
    // Wait a minute - the IFR just opened and .idx has not yet been resent.
    waitNice(60000);
    setPriority(Thread.NORM_PRIORITY - 2);
    int loop = 0;
    long lastEmail = 0;
    boolean onFinals = false;
    GregorianCalendar now = new GregorianCalendar();
    int todayJulian = SeedUtil.toJulian(now);
    StringBuilder runsb = new StringBuilder(50);
    // If this is a julian day too far in the past for it to be on the Edge, exit
    if ((!cwbcwb && todayJulian - idx.getJulian() > 10) || (todayJulian - idx.getJulian() > julianLimit - 1)) {
      prta(Util.clear(runsb).append(tag).append(" ").append(idx.getFilename()).
              append(" is out of replication range, Terminate this IndexChecker."));
      terminate();
      doExit();
      checkers.remove(this);
      return;                 // This thread is ended
    }
    while (!readMasterBlocks()) {
      waitNice(300000);
      if (terminate || Replicator.terminating) {
        break;
      }
      Util.prta(Util.clear(runsb).append(tag).append(" waiting for master blocks ").append(loop++));
      if (System.currentTimeMillis() - lastEmail > 7200000) {
        lastEmail = System.currentTimeMillis();
        SendEvent.debugEvent("IdxChkRdErr", "IdxChk cannot read mb=" + idx.getFilename() + " check EdgeMom for errors", this);
        Util.prt(
                Util.ascdate() + " " + Util.asctime() + " " + Util.getNode()
                + " This happens when IndexCheck cannot read the master blocks of the index\n"
                + "It tries to heal itself by waiting and issuing a new EdgeBlockClient to read the index file\n"
                + "and trying again.  Look for Exceptions in e-mail from edgemom with details of read error\n");
      }
    }

    //EdgeThreadTimeout eto = new EdgeThreadTimeout(getTag(), this, 30000,60000);
    // We need to do a pass through all of the channels and the index blocks to check
    // for ones we need to continue checking.  This is reflected in the BitSet blkset as unset bits
    int nbad = 0;
    int nok = 0;
    int nlast = 0;
    for (int imast = 0; imast < IndexFile.MAX_MASTER_BLOCKS; imast++) {
      if (mBlocks[imast] == 0 || terminate) {
        break;          // Done, no more master blocks to check
      }
      if (mBlocks[imast] > 0) {
        blkset.set((int) mBlocks[imast], true);   // Set all the master blocks as O.K.
      }
      for (int i = 0; i < MasterBlock.MAX_CHANNELS; i++) {
        if (mb[imast].getSeedName(i).charAt(0) == ' ') {
          break;   // No more names
        }
        int iblk = mb[imast].getFirstIndexBlock(i);
        //prt("Check "+mb[imast].getSeedName(i)+" First index="+iblk);
        while (iblk > 0) {
          try {
            if (terminate) {
              break;
            }
            rw.readBlock(idxbuf, iblk, 512);        // Read the index block for this channel
            if (idxblk == null) {
              idxblk = pool.get(idxbuf, iblk);  // Create an IndexBlockUtil from this block
            } else {
              idxblk.reload(idxbuf, iblk);         // Except for first time, this happens
            }
            chk.readBlock(chkbuf, iblk, 512);
            if (chkblk == null) {
              chkblk = pool.get(chkbuf, iblk);   // Make an IndexBlockUtil from this block
            } else {
              chkblk.reload(chkbuf, iblk);
            }
            if (!Util.stringBuilderEqual(idxblk.seedName, chkblk.seedName)) // If seedname is different, something is really wrong!
            {
              prta(Util.clear(runsb).append("*** Init chk and idx block have different seednames! iblk=").
                      append(iblk).append(" file=").append(rw.getFilename()).
                      append(" idx=").append(idxblk.seedName).append("!=").append(chkblk.seedName).append("|"));
            }
            if (idxblk.compareBufsComplete(chkblk)) {        // Is this block complete and O.K.
              //if(dbg) 
              //  prt(Util.clear(runsb).append(tag).append(" startup ").append(mb[imast].getSeedName(i)).
              //        append(" ").append(SeedUtil.fileStub(idx.getJulian())).append("_").append(idx.getNode()).
              //        append(" indexblk=").append(iblk).append(" nxtidxblk=").append(idxblk.nextIndex).append(" is o.k. and complete"));
              blkset.set(iblk);             // Mark this block as O.K. on the list
              nok++;
            } else {
              if (idxblk.nextIndex != -1) {
                prt(Util.clear(runsb).append(tag).append(" startup ").append(mb[imast].getSeedName(i)).
                        append(" ").append(SeedUtil.fileStub(idx.getJulian())).append("_").append(idx.getNode()).
                        append(" indexblk=").append(iblk).append(" nxtidxblk=").append(idxblk.nextIndex).
                        append(" ** reason ").append(idxblk.getReason()));
                prt(idxblk.dumpBufs(chkblk));
                nbad++;
              } else {
                nlast++;
              }
            }
          } catch (EOFException e) { // The index file has not been written to full size yet
            try {
              sleep(2000);
            } catch (InterruptedException e2) {
            }
            continue;
          } /**
           * This occurs mainly when zero filled blocks are in the index (the
           * blocks got built in the file but were never updated with any data).
           * It is not a serious error as they will get build eventually.
           */
          catch (IllegalSeednameException e) {
            prt(Util.clear(runsb).append(tag).append(" Illegal seedname on init chk blk=").append(iblk).
                    append(" of ").append(idx.getFilename()).append(" ").append(e.getMessage()));
            break;      // No idxblk created, so nothing to do
            //if(getPrintStream() == null) e.printStackTrace();
            //else e.printStackTrace(this.getPrintStream());
          } catch (IOException e) {
            Util.IOErrorPrint(e, tag + " Reading idx or chk block");
            if (idx.getRawDisk() == null) {
              Util.prta(Util.clear(runsb).append(tag).append(" IFR has closed.  Terminating...."));
              terminate();   // Someone closed this file
              checkers.remove(this);
              doExit();
              return;
            }
          }
          if (idxblk != null) {
            if (iblk == idxblk.nextIndex) {
              break;   // Avoid an invalid infinite loop
            }
            iblk = idxblk.nextIndex;              // Point to the next block on linked list of index blocks and try again
          }
          //prt(Util.clear(runsb).append("Check "+mb[imast].getSeedName(i)+" next index="+iblk));
        }   // Loop untild iblk=0
      }
    }     // End of loop on master blocks
    if (idxblk != null) {
      pool.free(idxblk);
    }
    if (chkblk != null) {
      pool.free(chkblk);   // Return the blocks used in this loop
    }
    chkblk = null;
    idxblk = null;
    // Periodically look at all the blocks.  If anything changes, look again, else wait a bit
    prta(Util.clear(runsb).append(tag).append(" # chck blocks on rqst initial list #ok=").append(nok).
            append(" #bad=").append(nbad).append(" #last=").append(nlast).
            append(" ").append(idx.toStringBuilder(null)));
    boolean updated = true;
    StringBuilder sb = new StringBuilder(512);

    int nrequest;
    int nrequestBlks;
    int ncheck;

    waitNice(((idx.getJulian() % 12) * 10000 + (threadNumber % 10) * 1000) % 120000);
    try {     // Catch runtime exceptions
      long lastCheck = System.currentTimeMillis();

      while (!terminate && !Replicator.terminating) {
        state = 1;
        if (dbg) {
          Util.clear(runsb).append(tag).append(" Top of rqst loop upd=").append(updated ? "T" : "F").
                  append(" term=").append(terminate).append(" ").
                  append(idx.toStringBuilder(null)).append(" not day=").append((idx.getJulian() != SeedUtil.toJulian(now) ? "T" : "F")).
                  append(" ");
          pool.toStringBuilder(runsb);
          prta(runsb);
        }
        if (pool.getFree() > pool.getUsed() / 2 && pool.getFree() > 1000) {
          //SendEvent.edgeSMEEvent("IdxChkPoolBg", "IndexBlockPool has more than 1000 used! ="+pool.getUsed(), this);
          prta(Util.clear(runsb).append(tag).append("Drop some IndexUtilPool space used=").append(pool.getUsed()).
                  append(" free=").append(pool.getFree()));
          pool.trimList(pool.getFree() / 2);
        }
        state = 2;
        if (terminate) {
          break;
        }
        ncheck = 900000;
        if (updated) {
          ncheck = 60000;             // Shorter wait if something changed
        }
        if (System.currentTimeMillis() - lastCheck < ncheck) {
          waitNice(Math.max((ncheck - (System.currentTimeMillis() - lastCheck)), 1));
        }
        if (terminate) {
          continue;
        }
        state = 3;
        while (blockRequests) {
          try {
            sleep(1000);
          } catch (InterruptedException e) {
          } // No requests while blocked
          if (terminate) {
            break;
          }
        }
        if (terminate) {
          break;
        }
        lastCheck = System.currentTimeMillis();
        state = 4;

        now.setTimeInMillis(lastCheck);
        todayJulian = SeedUtil.toJulian(now);

        if (idx.getDataRawDisk() == null || idx.getRawDisk() == null) {
          prta(Util.clear(runsb).append(tag).append(" ").append(idx.getFilename()).
                  append(" has been closed by some means.  Terminate this IndexChecker."));
          terminate();
          state = 5;
          break;
        }
        // See if this is not the julian day for this file, that we have not gotten a index for awhile, and its at least 20 minutes
        // into a new julian day with some randomization of start times
        if (idx.getJulian() != todayJulian) {   // Is it some other day than today
          state = 6;
          // Has it been at least four hours and no other recent reloads
          if (lastCheck - lastGetFull > 14400000 && lastCheck - lastReload > 60000) {
            //prta(idx.getFilename()+" chk reload ="+(lastCheck - lastGetFull)+" "+(((idx.getJulian() % 50)+40)*30000)+" to "+(lastCheck % 86400000L)+"" +
            //    " rep="+replicator); 
            //if( ((idx.getJulian() % 50)+40+(threadNumber%5))*30000 < (lastCheck % 7200000L)) {
            prta(Util.clear(runsb).append("Ichk reload do ").append(idx.getFilename()).append(" idxbuf=").append(idxbuf));
            if (replicator != null) {
              if (replicator.getFullIndex(idx, idxbuf, false)) {
                lastGetFull = lastCheck;     // Cause a update of the full index
              } else {
                prta(Util.clear(runsb).append(tag).append(" **** Ichk reload failed - reschedule ").append(idx.getFilename()));
              }
            }
            lastReload = lastCheck;
            //}
          }
        }
        state = 7;
        updated = false;
        // Read the ctrl block and update what we know about master blocks
        try {
          rw.readBlock(ctlbuf, 0, 512);
        } catch (IOException e) {
          prt(Util.clear(runsb).append("ctl block read err").append(idx.getFilename()));
          terminate = true;
          continue;
        }
        fromCtlbuf();
        if (dbg) {
          prta(tag + " rqst_next_index=" + next_index);
        }

        // Expand the number of bits in the mask if it has increased and mark masterblocks as "ok"
        if (next_index > blkset.size()) {
          for (int i = blkset.size(); i < next_index; i++) {
            blkset.set(i, false); // Extend the blkset bit set by the requred number of blocks
          }
        }
        for (int i = 0; i < IndexFile.MAX_MASTER_BLOCKS; i++) {
          if (mBlocks[i] > 0) {
            blkset.set((int) mBlocks[i], true);   // Set all the master blocks as O.K.
          }
        }
        // Examine each index, and for ones missing data request data from one extent
        ncheck = 0;
        nrequest = 0;
        nrequestBlks = 0;
        state = 8;
        finals.clear();
        for (int iblk = 2; iblk < next_index; iblk++) {
          if(terminate || Replicator.terminating) break;
          if (!blkset.get(iblk)) {       // If it is not a MasterBlock and it is not marked fullfilled
            onFinals = false;
            try {
              state = 10;
              if (terminate) {
                break;          // We are terminating, do not do anything more
              }
              try {
                rw.readBlock(idxbuf, iblk, 512);  // Read current state of the index
              } // Sometimes the index file is not written yet, but the next_index has been updated (a race condition)
              // If this is the case where an EOF can happen, we just need to wait for these blocks to come in
              catch (EOFException e) {
                try {
                  prta(Util.clear(runsb).append("EOF in Index Checker skip rest! ").append(rw.getFilename()).
                          append(" at block=").append(iblk).append(" next_index=").append(next_index).
                          append(" file len=").append(rw.length()));
                  break;      // no need to do more if the file is not this big!
                } catch (IOException e2) {
                  prta(Util.clear(runsb).append("EOF in index Checker skip rest! ").append(rw.getFilename()).
                          append(" at block=").append(iblk).append(" next_index=").append(next_index));
                }
                //e.printStackTrace(getPrintStream() == null? System.out: getPrintStream());
                break;      // Leave loop on index blocks and go around again
              }
              state = 11;
              if (idxbuf[0] == 0 && idxbuf[1] == 0 && idxbuf[2] == 0) {
                continue;
              }
              if (idxblk != null) {
                prta(Util.clear(runsb).append("*** idx block not null! how can this be ").append(idxblk.seedName).
                        append(" iblk=").append(idxblk.iblk).append(" nblk=").append(idxblk.nextIndex).
                        append(rw.getFilename()).append(" iblk=").append(iblk));
              }
              state = 12;
              idxblk = pool.get(idxbuf, iblk);     // Map the currently incomplete block from the index file
              ncheck++;
              boolean checkLastExtent = false;

              // See if there is an active IndexBlockCheck for this one
              IndexBlockCheck ibcblk = idx.getIndexBlockCheck(iblk);
              if (ibcblk == null) {
                state = 13;
                // No, read the chk block from disk, so this block is not the current one IndexFileReplicator is using
                chk.readBlock(chkbuf, iblk, 512);
                if (chkblk == null) {
                  chkblk = pool.get(chkbuf, iblk);
                } else {
                  chkblk.reload(chkbuf, iblk);
                }
                checkLastExtent = true;         // Always do a full one if we read it from disk
              } else {
                state = 14;
                if (chkblk == null) {
                  chkblk = pool.get(ibcblk.getBuf(), iblk);
                } else {
                  chkblk.reload(ibcblk.getBuf(), iblk);
                }
                // This is to help LH, VH, etc which are not modified very often
                if (lastCheck - ibcblk.getLastIndexCheck() > 1800000
                        && lastCheck - ibcblk.getLastModified() > 180000) { // Check the last extent if it has been awhile
                  checkLastExtent = true;// Do this if it has been awhile
                  ibcblk.setLastIndexCheck(lastCheck);
                }
              }
              state = 15;
              if (!Util.stringBuilderEqual(idxblk.seedName, chkblk.seedName)) {
                prta(Util.clear(runsb).append(" *** chk and idx block have different seednames! iblk=").append(iblk).
                        append(" filedate=").append(rw.getFilename()).append(" idx=").append(idxblk.seedName).
                        append("!=").append(chkblk.seedName).append("|") + " ibcblk=" + (ibcblk == null ? "Null" : ibcblk.getSeedName()) + " " + idx.getFilename());
              }
              if (idxblk.compareBufsComplete(chkblk)) {
                state = 16;
                if (dbg) {
                  prta(Util.clear(runsb).append(tag).append(" iblk=").append(iblk).append(" ").
                          append(idx.toStringBuilder(null)).append(" rqst block is now complete o.k.!"));
                }
                blkset.set(iblk);       // Take this one off the list
                pool.free(idxblk);      // Free the indexBlock
                idxblk = null;
                continue;               // Nothing more to do
              }
              //else if(idxblk.nextIndex > 0) if(dbg) prta(Util.clear(runsb).append(idxblk.seedName).append(" ").
              //        append(idxblk.iblk).append(" nxt=").append(idxblk.nextIndex).append(idxblk.getReason()));
              // Are they substantially the same
              state = 17;
              missing[2] = -1;
              Util.clear(sb);
              // The strict parameter is whether the last extent is checked.  We should not do this too often
              // since it is being updated currently
              if (idxblk.compareBufsChecker(chkblk, missing,
                      ((System.currentTimeMillis() - idx.getLastUsed()) > 1200000) || checkLastExtent, sb)) {
                state = 18;
                pool.free(idxblk);
                idxblk = null;
                continue;
              }  // Nothing out of the ordinary
              //else prta(sb);
              state = 19;
              finals.add(idxblk);        // Accumulate list of open blocks, note that all done blocks continue above
              onFinals = true;
              Util.clear(sb);
              /*prt(tag+" "+sb.toString());*/
              // Missing now contains a list of blocks that are missing and need to be requested from
              // The far side
              //prta(idxblk.dumpBufs(chkblk));
              state = 20;
              if (missing[2] > 0) {
                updated = true;
                state = 21;

                Util.clear(sb);
                int start = missing[3];
                int end;
                int nout = 0;
                for (int i = 4; i < missing[2] + 3; i++) {
                  if (missing[i] != missing[i - 1] + 1) {
                    end = missing[i - 1];
                    sb.append(start).append("-").append(end).append(" ");
                    nout++;
                    if (nout % 10 == 9) {
                      sb.append("\n");
                    }
                    prta(Util.clear(runsb).append(tag).append(" rqst ").append(SeedUtil.fileStub(idx.getJulian())).
                            append("_").append(idx.getNode()).append(ibcblk == null ? " Disk" : " Open").
                            append(" ").append(Util.leftPad(start, 8)).append("_").append(Util.rightPad(end, 8)).
                            append(Util.leftPad((end - start + 1), 3)).append(" ").
                            append(Util.leftPad(missing[0], 4)).append("/").append(Util.leftPad(missing[1], 2)).
                            append(" ").append(idxblk.seedName).append(" ").
                            append(IndexBlock.timeToString(idxblk.earliestTime[missing[1]], null)).append(" ").
                            append(IndexBlock.timeToString(idxblk.latestTime[missing[1]], null)));
                    nrequest++;
                    nrequestBlks += (end - start + 1);
                    blocksRequested += (end - start + 1);
                    try {
                      while (blockRequests && !terminate) {
                        waitNice(1000);// If block, wait for block to expire
                      }
                      if (terminate) {
                        break;
                      }
                      if (!EdgeBlockClient.requestBlocks(idx.getJulian(), idx.getNode(),
                              start, end, missing[0], missing[1])) {
                        if (terminate) {
                          break;
                        }
                        waitNice(1000);
                      } else {
                        sleep(20);
                      }
                    } catch (InterruptedException e) {
                    }
                    start = missing[i];
                  }
                }
                sb.append(start).append("-").append(missing[missing[2] + 2]).append("\n");
                try {
                  prta(Util.clear(runsb).append(tag).append(" rqst ").append(SeedUtil.fileStub(idx.getJulian())).append("_").
                          append(idx.getNode()).append(ibcblk == null ? " Disk" : " Open").append(" ").
                          append(Util.leftPad(start, 8)).append("-").append(Util.rightPad(missing[missing[2] + 2], 8)).
                          append(Util.leftPad((missing[missing[2] + 2] - start + 1), 3)).append(" ").
                          append(Util.leftPad(missing[0], 4)).append("/").append(Util.leftPad(missing[1], 2)).append(" ").
                          append(idxblk.seedName).append(" ").
                          append(IndexBlock.timeToString(idxblk.earliestTime[missing[1]], null)).
                          append(" ").append(IndexBlock.timeToString(idxblk.latestTime[missing[1]], null)));
                } catch (RuntimeException e) {
                }
                nrequest++;
                state = 25;
                // Limit the number of requests per second from this file (spreads out the requests)
                try {
                  sleep(100);
                } catch (InterruptedException e) {
                }
                nrequestBlks += (missing[missing[2] + 2] - start + 1);
                blocksRequested += (missing[missing[2] + 2] - start + 1);
                // This is a throttle if the data block cannot be written (output pipe full)
                // Do a small wait and move on, this request will happen again during the next pass
                try {
                  state = 26;
                  while (blockRequests && !terminate) {
                    waitNice(1000);
                  }
                  state = 27;
                  if (terminate) {
                    break;
                  }
                  if (!EdgeBlockClient.requestBlocks(idx.getJulian(), idx.getNode(),
                          start, missing[missing[2] + 2], missing[0], missing[1])) {
                    sleep(1000);
                    if (terminate) {
                      break;
                    }
                  } else {
                    sleep(20);
                  }
                } catch (InterruptedException e) {
                }
                state = 28;
                //prt("Missing blocks="+missing[2]);
                //prt(sb.toString());

              }
              state = 29;
              idxblk = null;      // Block is in finals list as it is opened
            } catch (EOFException e) {
              state = 30;
              try {
                prta(Util.clear(runsb).append("EOF in Index Checker! at block=").append(iblk).
                        append("next_index=").append(next_index).append(" file len=").append(rw.length()));
              } catch (IOException e2) {
                prta("EOF in index Checker at block=" + iblk);
              }
              e.printStackTrace(this.getPrintStream());
              if (!onFinals) {
                pool.free(idxblk);
              }
              throw new RuntimeException("EOFException on " + idx.getFilename());

            } catch (IOException e) {
              state = 31;
              if (!onFinals) {
                pool.free(idxblk);
              }
              if (idx.getRawDisk() == null) {
                Util.prt(Util.clear(runsb).append(tag).append(" File has been closed.  Terminate this IndexChecker."));
                terminate();
                break;
              }
              Util.IOErrorPrint(e, tag + " Error in IC at block=" + iblk + " " + idx.getFilename());
              prta(Util.clear(runsb).append("IOerror reading index =").append(e.getMessage()));
            } catch (IllegalSeednameException e) {
              state = 32;
              prta(Util.clear(runsb).append(tag).append(" Illegal seedname when checking iblk=").append(iblk).
                      append(" ").append(idx.getFilename()).append(" ").append(e.getMessage()));
              if (!onFinals) {
                pool.free(idxblk);
              }
              e.printStackTrace(this.getPrintStream());
            }
          }
          //yield();
          state = 33;
        }     // For each index block

        state = 34;
        // There should only be one open finals block for each channel - look for duplicates and cause a full index get if there is more than one
        // This situation occurs when a final index update (index full) is lost for some reason and then a new index block is opened further on
        // We need to get the original index in this case
        boolean needReload = false;
        try {
          for (int i = 0; i < finals.size() - 1; i++) {
            for (int j = i + 1; j < finals.size(); j++) {
              state = 35;
              if (Util.stringBuilderEqual(finals.get(i).seedName, finals.get(j).seedName)) {
                state = 36;
                if (replicator != null) {
                  //prta(Util.clear(runsb).append(tag).append(" ** Ichk Dup blk1=").append(finals.get(i).toStringBuilder(null)));
                  //prta(Util.clear(runsb).append(tag).append(" ** Ichk Dup blk2=").append(finals.get(j).toStringBuilder(null)));
                  needReload = true;
                  break;
                }
              }
              state = 37;
            }
          }
        } catch (RuntimeException e) {   // So it did not print, go on
          prta("Runtime exception printing final e=" + e);
          e.printStackTrace(getPrintStream());
        }
        state = 38;
        for (int i = finals.size() - 1; i >= 0; i--) {
          pool.free(finals.get(i)); // This frees all of the blocks accumulated as open
        }
        Util.clear(runsb).append("Chk:").append(rw.getFilename()).append(" finals.size=").append(finals.size()).
                append(" pool ");
        state = 39;
        pool.toStringBuilder(runsb);
        state = 40;
        prta(runsb);
        finals.clear();
        idxblk = null;
        if (needReload) {
          state = 41;
          if (System.currentTimeMillis() - lastGetFull > 600000) {
            if (replicator.getFullIndex(idx, idxbuf, false)) {
              lastGetFull = System.currentTimeMillis();
              lastReload = lastGetFull;
            } // Cause a update of the full index
            else {
              prta(Util.clear(runsb).append(tag).append(" **** Ichk Dup open blocks index reload failed ").append(idx.getFilename()));
            }
          }
        }
        state = 42;
        //eto.resetTimeout();           // Reset the timeout
        prta(Util.clear(runsb).append(tag).append(" rqst end next=").append(next_index).append(" nchk=").append(ncheck).
                append(" nrqst=").append(nrequest).append(" ").append(nrequestBlks).
                append(" blks ").append(idx.toStringBuilder(null)));
      }       // while(!terminate) main loop
      state = 43;
      //prta("IChk: ** IndexChecker terminated TO.int="+eto.hasSentInterrupt()+" TO.destroy="+
      //    eto.hasSentDestroy());
      prta(Util.clear(runsb).append(tag).append(" ** IndexChecker terminated").append(idx.getFilename()).
              append(" term=").append(terminate));
      //eto.shutdown();           // Shutdown the timeout thread
      if (chkblk != null) {
        pool.free(chkblk);
      }
      //pool.free(idxblk);
      checkers.remove(this);
      running = false;
      state = 44;
      doExit();
      state = 45;
    } catch (RuntimeException e) {
      state = 46;
      prta(Util.clear(runsb).append(tag).append("IC: **** Runtime Xception in ").
              append(this.getClass().getName()).append(" e=").append(e));
      if (getPrintStream() != null) {
        e.printStackTrace(getPrintStream());
      } else {
        e.printStackTrace();
      }
      if (e.getMessage() != null) {
        if (e.getMessage().contains("OutOfMemory")) {
          SendEvent.doOutOfMemory("", this);
          throw e;
        }
        for (int i = finals.size() - 1; i >= 0; i--) {
          pool.free(finals.get(i)); // This frees all of the blocks accumulated as open
        }
        finals.clear();
        if (!onFinals) {
          pool.free(idxblk);
        }
      }
      terminate = true;
    }    // The main loop has exited so the thread needs to exit
    state = 47;
    running = false;
  }

  private boolean readMasterBlocks() {
    try {
      rw.readBlock(ctlbuf, 0, 512);
      fromCtlbuf();
      // For each allocated MasterBlock, create it as an object
      blkset.set(0);            // We do not check the control block
      for (int i = 0; i < IndexFile.MAX_MASTER_BLOCKS; i++) {
        if (mBlocks[i] != 0) {
          try {
            mb[i] = new MasterBlock((int) mBlocks[i], rw, false);
            blkset.set((int) mBlocks[i]);        // We do not check index blocks
          } catch (IOException e) {
            Util.IOErrorPrint(e, tag + " IOError reading MB at " + mBlocks[i] + " i=" + i + " len=" + (rw.length() / 512L)
                    + " in ichk idx=" + idx.toStringFull());
            return false;
          }
        } else {
          break;                       // Exit when we are out of allocated blocks
        }
      }
    } catch (IOException e) {
      Util.IOErrorPrint(e, tag + " IO error MB control block in IndexChecker idx=" + idx.toStringFull());
      return false;
    }
    return true;
  }

  /**
   * From the raw disk block, populate the ctl variables.
   */
  private void fromCtlbuf() {
    ctl.clear();
    length = ctl.getInt();
    next_extent = ctl.getInt();
    next_index = ctl.getShort();
    for (int i = 0; i < IndexFile.MAX_MASTER_BLOCKS; i++) {
      mBlocks[i] = ctl.getShort();
    }
  }

  /**
   * This internal class gets registered with the Runtime shutdown system and is
   * invoked when the system is shutting down. This must cause the thread to
   * exit.
   */
  class ShutdownIndexChecker extends Thread {

    IndexChecker checker;

    /**
     * Default constructor does nothing the shutdown hook starts the run()
     * thread.
     */
    public ShutdownIndexChecker(IndexChecker chk) {
      checker = chk;
      gov.usgs.anss.util.Util.prta("new ThreadShutdown " + getName() + " " + getClass().getSimpleName());
    }

    // This is called by the RunTime.shutdown at any termination 
    @Override
    public void run() {
      checker.prta(Util.asctime() + " " + tag + " IndexChecker Shutdown() started..." + checker.toStringBuilder(null));
      terminate();          // Send terminate to main thread and cause interrupt
      int loop = 0;
      while (running) {
        // Do stuff to aid the shutdown
        try {
          sleep(1000);
        } catch (InterruptedException e) {
        }
        loop++;
      }
      checker.prta(tag + " Shutdown() of IndexChecker is complete." + checker.toStringBuilder(null));
    }

    @Override
    public String toString() {
      return tag + " IndexChecker shutdown";
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
    String filename = "/Users/data/2007_003_1";
    boolean makeCheck = true;
    Util.setModeGMT();
    EdgeMom.setNoConsole(false);
    EdgeMom.prt("no console");
    Util.setNoInteractive(false);
    EdgeMom.prt("No console2");
    String node;
    for (int i = 0; i < args.length; i++) {
      if (args[i].equals("-f")) {
        filename = args[i + 1];
      }
    }
    node = filename.substring(filename.lastIndexOf("_") + 1);
    try {
      IndexFileReplicator idx = new IndexFileReplicator(filename, node, 0, false, true); // Test create
      IndexChecker idxchk = new IndexChecker("-empty", "IFC", idx, null);
    } catch (IOException e) {
      Util.IOErrorPrint(e, "ERror opening main test file");
    } catch (EdgeFileReadOnlyException e) {
      Util.prt("Edgefile read only exception opening file=" + filename);
    } catch (EdgeFileDuplicateCreationException e) {
      Util.prta("***** IFR: rare duplicate creation found.  Handle! e=" + e);
    }

  }
}
