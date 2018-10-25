/*
 *  This software is in the public domain because it contains materials 
 *  that originally came from the United States Geological Survey, 
 *  an agency of the United States Department of Interior. For more 
 *  information, see the official USGS copyright policy at 
 *  http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.msread;

import gov.usgs.anss.edge.IllegalSeednameException;
import gov.usgs.anss.edge.IndexBlock;
import gov.usgs.anss.edge.IndexFile;
import gov.usgs.anss.edge.EdgeBlockQueue;
import gov.usgs.anss.edge.MiniSeedOutputHandler;
import gov.usgs.anss.edge.RawDisk;
import gov.usgs.anss.edge.RawToMiniSeed;
import gov.usgs.anss.edge.RunList;
import gov.usgs.anss.edgethread.EdgeThread;
import gov.usgs.anss.edgethread.FakeThread;
import gov.usgs.anss.edgethread.MemoryChecker;
import gov.usgs.anss.seed.MiniSeed;
import gov.usgs.anss.seed.MiniSeedPool;
import gov.usgs.anss.seed.SteimException;
import gov.usgs.anss.util.Util;
import java.nio.ByteBuffer;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.TreeMap;
import java.util.Set;

/**
 * This class takes a miniSEED file from an EdgeCWB system and creates a new one while compressing
 * out badly compressed or intentionally short data, and puts the data out in large sections channel
 * by channel so that queries work faster. The class uses the RawDiskBigBuffer class to read big
 * swaths of the file into a memory buffer, and then hand out these data as though they are blocks
 * read from disk. This reduces the number of IOs for input by a huge amount.
 * 
 * <p>
 * The process creates a computation object for each channel and then a user controlled number of
 * computation threads which round robin the computation object each time a new section of data is
 * read in. When each new section is read the first block of each extent is read and a structure
 * containing all of the extents for each channel is created.
 * <p>
 * For each channel computation: From this list of extents for this channel create an array of
 * miniSEED blocks for all extents in this channel in the RawDiskBigBuffer.  Sort these blocks and eliminate any duplicate blocks.
 * For each remaining block call the status object to collect status on
 * number of blocks, number of out-of-order blocks, total Steim frames used. 
 * 
 * <p>  If the -compress switch is used, then if the average number of used 
 * frames per block is less than 6, then the assembled blocks are decompressed and RawToMiniSeed is used
 * to compress the data into new miniSEED blocks.   If average used frames > 6, then preserve the
 * miniSEED blocks as they are 
 * and put them in the output file unchanged (duplicates having been eliminated).
 * <p>
 * For massively duplicated data files (data loops usually), the -massive option performs a search of
 * the last NNNN blocks as the blocks are assembled and eliminates adding a new block if a duplicate is
 * found.  This was necessary because massively duplicated blocks created very large number
 * of miniSEED blocks from the MiniSeedPool causing memory to run out before the normal "load all the blocks, 
 * sort them and then eliminate" them occurred.  Do not use this on files with small amounts of duplicates
 * as the look back for each block is compute intensive.
 * <p>
 * On completion of a file, the statistics gathered during the processing are output. This include
 * the number of input blocks, the number of output blocks, the number of data frames in the input,
 * the number of blocks that are out-of-order on the input, the number of duplicate blocks
 * eliminated, and some percentages based on this values. In addition a listing of the "Runs". that is,
 * contiguous data runs is output with a limit of 10 Runs.
 *<p>
 * This software was funded in large part by Cal Tech as a means to shrink their archive files and to 
 * reorder the data so that large queries would run faster for reorganized files in the archive.  It is based
 * on software already in the EdgeCWB project used to build a index file from the miniSEED file should
 * the index be lost or corrupted.  It also uses elements of the software used to do the nightly scan of
 * data files to produce statistics, spot oddities, and create fetchlist entries (updateHoldings scan).
 * <pre>
 *
 * Usage :  java -XmxNNNNNm $PATHTOBIN/msread.jar -datapath path [-compress][-mb nnnn][-npermit nn]] [-massive NNNNN] [MiniSEED FILES]
 * This program is normally invoked by the reorgDay.bash script.
 * Note the size of the buffer used depends on the setting of the -XmxNNNNNm which must be 2000 mB bigger that the buffer
 *  switch   args     Description
 * -reorg           This switch tells msread to run the Reorganizer file function
 * -compress        If present, allow recompression of channels that would benefit from it.
 * -mb      NNNNN   Use a input buffer size of this amount, the bigger the better in mB.
 * -alloc   NNNNN   Size in mB of each allocated buffer in the Disk cache
 * -datapath path   This is the path where the output files will be created.
 * -npermit  nn     This is the number of threads used to process the data (def=16)
 * -massive nnnn    If a file has massive duplications, find them by looking back nnnn blocks as loading (def=0, 20000 is safe)
 * </PRE>
 *
 * @author U.S. Geological Survey  <ketchum at usgs.gov>
 */
public final class ReorganizeFile implements MiniSeedOutputHandler {

  private static FakeThread fake;
  private static final MiniSeedPool msp = new MiniSeedPool();
  private static MonitorReorg monitor;
  //private static Semaphore semaphore;
  protected TreeMap<String, ArrayList<Integer>> chans = new TreeMap<>();
  //protected TreeMap<String, Integer> isCompressable = new TreeMap<>();
  protected TreeMap<String, Status> status = new TreeMap<>();
  private ArrayList<ChannelThread> thrs = new ArrayList<>(10);
  private int massiveDups;
  private boolean threadFree;
  private TreeMap<String, ChannelProcess> processes = new TreeMap<>();
  private StringBuilder report = new StringBuilder(2000);
  private RawDiskBigBuffer bigRaw;
  private int nextExtent;
  private int length;     // length of the index file
  private int julian;
  private String fileout;  // The stem of the output file yyyy_ddd_n#s
  private byte[] buf = new byte[512];
  private byte[] idxbuf = new byte[1024*1024];
  private boolean compressPartial;
  private MiniSeed mstmp;
  private static boolean dbg;
  private static String dbgChan = "ZZADO  LHE";
  private static final Integer decompMutex = Util.nextMutex();
  private boolean firstBlock = true;

  /**
   *
   * @param t Set debug on or off.
   */
  public void setDebug(boolean t) {
    dbg = t;
  }

  /**
   *
   * @param chan Set the channel name which will have more output (uses contains()) to match
   */
  public static void setDebugChannel(String chan) {
    dbgChan = chan;
  }

  /** 
   *
   * @param compressPartial If true, channels that appear to be less than fully compressed can be
   * compressed
   * @param msFilename Filename(full path) of the .ms file to process
   * @param mbBufAlloc the size in megabytes of each buffer segment 
   * @param mbBuf The amount of memory to put in the RawDiskBigBuffer for reading the files
   * efficiently
   * @param npermit The number of semaphores for channels to run simultaneously (0 = run all)
   * @param massive If true, do scan for massive duplications
   */
  public ReorganizeFile(boolean compressPartial, String msFilename, int mbBufAlloc, int mbBuf, int npermit,
          int massive) {
    RawToMiniSeed.setStaticOutputHandler((MiniSeedOutputHandler) this);
    this.compressPartial = compressPartial;
    int lastSlash = msFilename.lastIndexOf("/");
    massiveDups = massive;
    if(monitor == null) {
      monitor = new MonitorReorg();

    }
    fileout = msFilename;
    if (lastSlash >= 0) {
      fileout = msFilename.substring(lastSlash + 1);
    }
    fileout = fileout.replaceAll(".ms","");
    String reportFile = Util.getProperty("datapath") + fileout +  ".txt";   //if (npermit > 0) {
    Util.prt("Starting file "+ msFilename+" fileout="+fileout+" massive="+massive+" report="+reportFile+" msp="+msp);
    //  semaphore = new Semaphore(npermit);
    //}
    long startTime = System.currentTimeMillis();
    try {
      // Create the processing threads up to npermit
      for (int i = 0; i < npermit; i++) {
        ChannelThread thr = new ChannelThread();
        thrs.add(thr);
      }
      // Open the disk files, get the etents and create a processor for each channel
      bigRaw = new RawDiskBigBuffer(msFilename, "r", mbBufAlloc, mbBuf);
      Util.prta("BigRaw="+bigRaw);
      try (RawDisk idx = new RawDisk(msFilename.replaceAll(".ms", ".idx"), "r")) {
        idx.readBlock(buf, 0, 512);
        ByteBuffer idxbb = ByteBuffer.wrap(buf);
        idxbb.position(0);
        length = idxbb.getInt();
        nextExtent = idxbb.getInt();
        Util.prt("Index file nextExtent = " + nextExtent + " len=" + length);
      }
      julian = getExtents();
      Set<String> keys = chans.keySet();
      Util.prt("#keys=" + keys.size());
      for (String key : keys) {
        ChannelProcess proc = new ChannelProcess(key);
        processes.put(key, proc);
        //Util.prt("Start processes.size()="+processes.size()+" "+proc);
      } // loop on keys
      long lastThreadDump = System.currentTimeMillis();
      for (;;) {
        // For each processes, assign them to a free thread as they are available
        keys = processes.keySet();
        for (String key : keys) {
          // Find a free processing thread or wait
          boolean assigned = false;
          while (!assigned) {
            for (int i = 0; i < thrs.size(); i++) {
              if (thrs.get(i).isFree()) {
                thrs.get(i).assign(processes.get(key));
                assigned = true;
                break;
              }
            }
            while(firstBlock) {
              Util.sleep(2000);
              Util.prta("Wait 2 for file to create");
            }
            long now = System.currentTimeMillis();
            if (now - lastThreadDump > 120000) {
              StringBuilder sb = Util.getThreadsString();
              sb.append(Util.ascdatetime2(now)).append("msp=").append(msp).append(" \n");
              MemoryChecker.checkMemory(false);
              sb.append(MemoryChecker.getLastSB()).append("\n");

              Util.writeFileFromSB("reorg.threads", sb);
              lastThreadDump = now; 
            }            
            if (!assigned) {
              threadFree = false;     // When a thread become free it sets this variable
              int loop = 0;
              while (!threadFree) {
                Util.sleep(2);
                if (loop++ == 10000) {
                  Util.prta("No free process in 20 seconds?");
                  break;
                }
              }
            }
          }
        }     // End of loop on channels


        // Wait for all processing threads to be free before reading more data
        boolean allFree = true;
        while (!allFree) {
          allFree = true;
          for (int i = 0; i < thrs.size(); i++) {
            if (!thrs.get(i).isFree()) {
              allFree = false;
            }
          }
        }
        // Now we need to wait for ALL compute threads to be done
        boolean done = false;
        while (!done) {
          done = true;
          for (ChannelThread thr : thrs) {
            if (!thr.isFree()) {
              done = false;
            }
          }
          if (!done) {
            Util.sleep(100);
          }
        }
        // Ready for another section, clear the extents list in chans, read it and build new extents
        chans.clear();
        if (bigRaw.getNextBuffer()) {
          break;
        }
        julian = getExtents();
        // find any new channels
        keys = chans.keySet();
        for (String key : keys) {
          ChannelProcess proc = processes.get(key);
          if (proc == null) {     // Its a new channel
            proc = new ChannelProcess(key);
            processes.put(key, proc);
            if (dbg || key.contains(dbgChan)) {
              Util.prt("Start another Channel process key=" + key);
            }
          }
        }
      }   // end of for(;;)
      // print the statistics for this set of channels
    
      bigRaw.close();
      keys = status.keySet();
      long totalIn = 0;
      long totalOut = 0;
      Util.clear(report);
      long totalDups = 0;
      for (String key : keys) {
        Status stats = status.get(key);
        report.append(stats).append("\n");
        if (stats != null) {
          totalIn += stats.getTotalBlocks();
          totalOut += stats.getOutBlocks();
          totalDups += stats.getNDuplicates();
        }
      }
      Util.prt("Summary : in=" + totalIn + " out=" + totalOut + " dups="+ totalDups
              +" savings=" + Util.df21(100.-((double) totalOut / totalIn *100)) + "%");
      report.append("Summary : in=").append(totalIn).append(" out=").append(totalOut).
              append(" dups=").append(totalDups).
              append(" savings=").
              append(Util.df21(100.- ((double) totalOut / totalIn)*100.)).append("%\n");
      Util.writeFileFromSB(reportFile, report);
      Util.prt("REORG: forceStale out ()");
      RawToMiniSeed.forceStale(-1);
      // Terminate all of threads
      for (int i = 0; i < thrs.size(); i++) {
        thrs.get(i).terminate();
      }
      int trim = renameFiles(msFilename, julian);
      Util.prt("REORG: returning trim=" + trim + " blks elapse="
              + ((System.currentTimeMillis() - startTime) / 1000) + "s " + msFilename);
    } catch (IOException e) {
      e.printStackTrace();
    } catch (RuntimeException e) {
      Util.prt("REORG: IOErr=" + e);
      e.printStackTrace();
    }
  }

  /**
   * rename the .ms and .idx files from whatever they are name on this system, to the same name as
   * the input file.
   *
   * @param msFilename The input file name including path
   * @param julian The julian day returned by the getextents routine
   */
  private int renameFiles(String msFilename, int julian) {
    try {
      // Calculate julian day from filename and check against some data from the file
      int jul = IndexFile.getJulianFromFilename(msFilename);
      if (jul != julian) {
        Util.prt("Julian from data and file name do not agree???? file=" + julian + " filename=" + julian + " " + msFilename);
      }
      // get the filename of the index file
      IndexFile ifl = IndexFile.getIndexFile(julian);
      ifl.close();
      fileout = ifl.getFilename();
      while(ifl.getDataRawDisk() != null) { // let all of the writing occur on close
        Util.prta("Wait for IndexFile to close "+ifl.getDataRawDisk());
        Util.sleep(1000);
      }
      int lastSlash = fileout.lastIndexOf("/");
      if(lastSlash >= 0) fileout = fileout.substring(lastSlash+1);
      int lastUnderOut = fileout.lastIndexOf("_");
      int lastUnder = msFilename.lastIndexOf("_");
      String instance = msFilename.substring(lastUnder + 1, msFilename.length() - 3);
      int trim = msread.trimsize(Util.getProperty("datapath") + fileout + ".ms");
      try {
        File f = new File(Util.getProperty("datapath") + fileout + ".ms");

        if (f.exists()) {
          File rename = new File(Util.getProperty("datapath") + fileout.substring(0, lastUnderOut + 1) + instance + ".ms");
          f.renameTo(rename);
          File index = new File(Util.getProperty("datapath") + fileout + ".idx");
          if (index.exists()) {
            String idx = Util.getProperty("datapath") + fileout.substring(0, lastUnderOut + 1) + instance + ".idx";
            rename = new File(idx);
            index.renameTo(rename);

            // create a .chk file from the new index
            try ( 
                  RawDisk newIdx = new RawDisk(idx, "r");
                  RawDisk newChk = new RawDisk(idx.replaceAll(".idx",".chk"), "rw") ) {
              if(newIdx.length() > idxbuf.length) {
                idxbuf = new byte[(int) (newIdx.length() * 3 / 2)];
              } 
              int len = newIdx.readBlock(idxbuf, 0, (int) newIdx.length());
              newChk.writeBlock(0, idxbuf, 0, (int) newIdx.length());
            }
            catch(IOException e) {
              e.printStackTrace();
            }
          } else {
            Util.prt("**** .idx file does not exist " + Util.getProperty("datapath") + fileout + ".idx");
          }
        } else {
          Util.prta("***** .ms file does not exist " + Util.getProperty("datapath") + fileout + ".ms");
        }
      }
      catch(Exception e) {
        e.printStackTrace();
      }
      return trim;
    } catch (IOException | RuntimeException e) {
      e.printStackTrace();
    }
    return -1;
  }
  /** This routine reads all of the extents within the range of blocks in the RawDiskBigBuffer
   * and builds up the tree map of an array list of all extents in a channel indexed by channel.
   * That is all of the extents end up in one of the array lists so that the processing threads
   * knows what extents to read in processing a single channel
   * 
   * @return julian day from one of the channels
   * @throws IOException  Should one occur
   */
  private int getExtents() throws IOException {
    int first = bigRaw.getFirstBlock();
    int last = bigRaw.getLastBlock();
    // Need to build and channel structure with extents for each channel
    Util.prta(bigRaw.toString() + " #chan=" + processes.size());
    int julianDay = 0;
    for (int blk = first; blk < last; blk += 64) {    // for each extent
      bigRaw.readBlockFromBuffer(buf, blk, 512, false, "index");
      if (buf[0] != 0 || buf[1] != 0 && buf[2] != 0 || buf[3] != 0 || buf[4] != 0) {
        String chan = MiniSeed.crackSeedname(buf);
        ArrayList<Integer> extents = chans.get(chan);
        if (dbg || chan.contains(dbgChan)) {
          Util.prta(dbgChan);
        }
        if (extents == null) {
          extents = new ArrayList<>(100);
          chans.put(chan, extents);
        }
        if (julianDay == 0) {
          try {
            if (mstmp == null) {
              mstmp = msp.get(buf, 0, 512);
            } else {
              mstmp.load(buf, 0, 512);
            }
            julianDay = mstmp.getJulian();
          } catch (IllegalSeednameException e) {
            e.printStackTrace();
          }
        }
        extents.add(blk);
      } else {
        if (blk >= nextExtent) {
          Util.prta("Getting extents zero block after nextExtent blk=" + blk + " nextExtent=" + nextExtent);
          break;
        } else {
          Util.prta("*** Getting extents got a zero block=" + blk + " nextExtent=" + nextExtent);
        }
      }
    }
    return julianDay;
  }

  @Override
  public void close() {   // for RTMS MiniSeedOutputHandler
    Util.prt("close");
  }

  /**
   * Handle the output from the RawToMiniSeed.
   *
   * @param msbuf
   * @param len
   */
  @Override
  public synchronized void putbuf(byte[] msbuf, int len) {
    try {
      if (mstmp == null) {
        mstmp = msp.get(msbuf, 0, len);
      } else {
        mstmp.load(msbuf, 0, len);
      }
      if (dbg || mstmp.getSeedNameString().contains(dbgChan)) {
        Util.prt("pb l=" + len + " " + mstmp);
      }
      Status stats = status.get(mstmp.getSeedNameString());
      if (stats != null) {
        stats.incOutBlocks();
      } else {
        Util.prt("*** PutBuf stats do not exist  - how can this be " + mstmp);
      }

      IndexBlock.writeMiniSeed(mstmp, "reorg");
    } catch (IllegalSeednameException e) {
      e.printStackTrace();
    }
  }
  /** This class is a thread which runs one ChannelProcess at a time, and then makes itself free
   * to run another channel.  The number of these threads is the parallelism for processing 
   * the files.
   * 
   */
  public final class ChannelThread extends Thread {

    private ChannelProcess process;
    private boolean terminate;
    private boolean gotit;
    private int state;
    private final StringBuilder sb = new StringBuilder(50);
    /** Assign a ChannelProcess to be processed by this thread.
     * 
     * @param proc A ChannelProcess to run
     */
    public void assign(ChannelProcess proc) {
      process = proc;
      interrupt();
    }
    /**
     * 
     * @return true if this ChannelThread is free (idle).
     */
    public boolean isFree() {
      return process == null;
    }

    public void terminate() {
      terminate = true;
    }

    @Override
    public String toString() {
      return toStringBuilder().toString();
      
    }

    public StringBuilder toStringBuilder() {
      Util.clear(sb).append("CT:[").append(getId()).append("] curr=").
              append(process == null?"Null": process.toStringBuilder()).append(" st=").append(state);
      return sb;
    }
    public ChannelThread() {
      start();
    }

    @Override
    public void run() {
      String tag = "CT["+this.getId()+"]";
      Util.prt(tag+" Thread is starting process="+process);
      while (!terminate) {    // forever loop
        try {
          state = 1;
          if (process != null) {
            state = 2;
            process.process();  // process the data
            state = 3;
            if (dbg || process.getChannel().contains(dbgChan)) {
              Util.prt(tag+"Segment done stats=" + status.get(process.getChannel()));
            }
            state = 4;
            process = null;       // mark this thread as free
            threadFree = true;
            state = 5;
          } else {
            state = 6;
            try {
              sleep(10000);         // Generally this is interrupted when a new ChannelProcess is assigned
            } catch (InterruptedException e) {
            }
            state = 10;
          }
        } catch (RuntimeException e) {   // exceptions should be rare
          e.printStackTrace();
          process = null;     // must be done!
          threadFree = true;
        }
      }

      Util.prt(tag+"ChannelThread exiting " + this.getName());
      if(process != null) {
        process.close();
      }
      process = null;
      threadFree = true;
    }
  }   // end of class ChannelThread
  public final class MonitorReorg extends Thread {
    public MonitorReorg() {
      setDaemon(true);
      start();
    }
    @Override
    public void run() {
      for(;;) {
        try {
          sleep(30000);
        }
        catch(InterruptedException expected) {
        }
        Util.prta("Monitor: "+bigRaw+" "+msp.toString()/*+"\n"+Util.getThreadsString()*/);
      }
    }
    
  }
  /** This thread rProcesses a segment in the RawDiskBuffer and gathers statistics.  It is generally
   * run by one of the ChannelThreads in the pool to allow parallel processing in different cores.
   * 
   */
  public final class ChannelProcess {
    private final String chan;
    private final byte[] buf = new byte[512];
    private final StringBuilder seedname = new StringBuilder(12);
    private int ndupscan;
    private int ndups;
    //private MiniSeed ms;
    private final ArrayList<MiniSeed> msblks = new ArrayList<>(64);
    private final int[] rawdata = new int[730];
    private Status stats;
    private int state;
    private final StringBuilder sb = new StringBuilder(100);
    //private boolean gotit = false;
    public void close() {
      if(msblks.size() > 0) {
        Util.prta("ChannelProcessClose msblks not empty="+msblks.size());
        for(int i=msblks.size() -1; i >=0; i--) {
          MiniSeed ms = msblks.get(i);
          if(ms != null) {
            msp.free(msblks.get(i));
            msblks.remove(i);
          }
        }
      }
      
    }
    @Override
    public String toString() {
      return toStringBuilder().toString();
    }
    public StringBuilder toStringBuilder() {
      Util.clear(sb).append(chan).append(" msblks.size()=").append(msblks.size()).
              append(" st=").append(state).append(" #dups=").append(ndups).append("/").append(ndupscan);
      return sb;
    }
    /** Create the channel process and setup the status object to collect statistics.
     * 
     * @param chan A NSCL for this process.  
     */
    public ChannelProcess(String chan) {
      this.chan = chan;
      Util.clear(seedname).append(chan);
      stats = status.get(chan);
      if (chan == null || chan.equals(dbgChan)) {
        Util.prta("Create channelProcess with null or " + chan + "?");
      }
      if (stats == null) {
        stats = new Status(chan);   // Add this status to the treemap
        status.put(chan, stats);
      }
    }
    /**
     * 
     * @return The NSCL to which this is assigned.
     */
    public String getChannel() {
      return chan;
    }
    /** This method does all of the processing for a given channel within the band of data in the
     * RawDiskBigBuffer.  It uses the chans treemap to get the list of extents, and then processes
     * all of the data in those extents.
     * 
     */
    public void process() {
      if (chan == null || chan.contains(dbgChan)) {
        Util.prta(dbgChan);
      }
      int maxoff = 0;
      int dups=0;
      int ndupscanStart = ndupscan;
      try {
        ArrayList<Integer> extents = chans.get(chan);
        if (extents == null) {
          if (dbg || chan.contains(dbgChan)) {
            Util.prta(chan + " no extents to process");
          }
          state = -1;
        } else {
          // There are extents, process them
          state = 1;
          // read in all of the blocks in all extents available and put in msblks.
          long lastOOR = stats.getOORBlocks();
          for (int iextent = 0; iextent < extents.size(); iextent++) {    // For each extent
            int extent = extents.get(iextent);
            for (int i = 0; i < 64; i++) {
              try {
                bigRaw.readBlockFromBuffer(buf, extent + i, 512, false, "Reorg");
              } catch (IOException e) {
                e.printStackTrace();
              }
              if (buf[0] != 0 || buf[1] != 0 || buf[2] != 0 || buf[3] != 0 || buf[8] != 0) {
                MiniSeed ms = msp.get(buf, 0, 512);
                if(massiveDups > 0) {
                  // If massiveDups is > 0, then lookback up to massiveDups blocks for duplicates and drop this one if found
                  for(int jj = msblks.size()-1; jj>=Math.max(msblks.size() - massiveDups,0); jj--) {
                    if(ms.isDuplicate(msblks.get(jj))) {
                      ndupscan++;
                      maxoff = Math.max(maxoff, msblks.size() - jj);
                      stats.incDups();
                      if(ndupscan % 1000 == 1) {
                        Util.prt("Scan found dup ms="+ms+" ndupscan="+ndupscan+" off="+(msblks.size()-jj)+" maxoff="+maxoff);
                      }
                      msp.free(ms);
                      ms = null;
                      break;
                    }
                  }
                }
                
                // If the block is not null, add it to msblks
                if(ms != null) {
                  msblks.add(ms);
                  stats.doMS(ms);
                }
              } else {
                if (iextent != extents.size() - 1) {
                  Util.prt(" ****** Got used extents past a zero block key=" + chan
                          + " extent=" + extent + " blk=" + i + " on " + iextent
                          + " size=" + extents.size());
                }
                for (int blk = extent + i + 1; blk < extent + 64; blk++) {
                  try {
                    bigRaw.readBlockFromBuffer(buf, blk, 512, false, "Reorg");
                  } catch (IOException e) {
                    e.printStackTrace();
                  }                    // confirm they are all zero blocks to the end
                  if (buf[0] != 0 || buf[1] != 0 && buf[2] != 0 || buf[3] != 0 || buf[4] != 0) {
                    Util.prt("Got non-zero block after a zero block key=" + chan
                            + " extent=" + extent + " blk=" + blk);
                  }
                }
                break;    // move on
              }
            }
          }

          
          state = 2;
          // If there are OOR blocks, sort the array
          long sortms = System.currentTimeMillis();
          if (stats.getOORBlocks() != lastOOR) {
            state = 3;
            Collections.sort(msblks);
            state = 4;
          }
          sortms = System.currentTimeMillis() - sortms;
          if(sortms > 60000) {
            Util.prta(chan+" ** sort of blks slow "+sortms+" ms msblks="+msblks.size());
          }
          state = 5;
          
          // Eliminate any duplicates
          long dupms = System.currentTimeMillis();
          for (int i = msblks.size() - 1; i > 0; i--) {
            if (msblks.get(i).isDuplicate(msblks.get(i - 1))) {
              msp.free(msblks.get(i));
              msblks.remove(i);
              stats.incDups();
              dups++;
              ndups++;
            }

          }
          
          // Do the statistics
          
          
          boolean compress = stats.isCompressable() & compressPartial;  // are there a lot of unfull blocks
          if(dups > 10 || ndupscan - ndupscanStart > 10) {
            Util.prta(chan+" *** #dups="+ndups+"/"+ndupscan+" #msblks="+msblks.size()+" "+(System.currentTimeMillis() - dupms)+" ms");
            report.append(chan).append(" *** #dups=").append(ndups).append("/").append(ndupscan).
                    append(" #msblks=").append(msblks.size()).append(" ").append(System.currentTimeMillis() - dupms).
                    append(" ms maxoff=").append(maxoff).append("\n");
          }

          // Build up the RunList in the statistics.
          for (int i = 0; i < msblks.size(); i++) {
            stats.doRun(msblks.get(i));
          }
          state = 6;
          boolean prior;
          boolean next;
          // For each block (now in sorted order), see if it needs to be recompressed, etc.
          for (int i = 0; i < msblks.size(); i++) {
            MiniSeed ms = msblks.get(i);
            boolean contiguous = false;
            state = 7;
            // If the last, this block and the next block have one continuity, then recompress, else no sense to do so
            if (compress
                    && // Not the last or first block
                    msblks.get(i).getRate() > 0. && msblks.get(i).getNsamp() > 0) {// compressable!
              next = true;          // Is the next block continuous, assume true if last one
              if (i < msblks.size() - 2
                      && msblks.get(i).getNextExpectedTimeInMillis() - msblks.get(i + 1).getTimeInMillis()
                      > 1000 / msblks.get(0).getRate()) {
                next = false;
              }
              prior = true;         // Was prior block continous, assume true if first one
              if (i > 0
                      && msblks.get(i - 1).getNextExpectedTimeInMillis() - msblks.get(i).getTimeInMillis()
                      > 1000 / msblks.get(0).getRate()) {
                prior = false;
              }
              if (prior) { // compression is continuing 
                contiguous = true;
              }
              if (!prior && next) {  // If prior was a gap but the next is not, then start compression with this block
                contiguous = true;
              }
              if (!contiguous && dbg) {
                Util.prt("Non continous prior=" + prior + " next=" + next
                        + " bef=" + (msblks.get(i - 1).getNextExpectedTimeInMillis() - msblks.get(i).getTimeInMillis())
                        + " aft=" + (msblks.get(i).getNextExpectedTimeInMillis() - msblks.get(i + 1).getTimeInMillis()));
              }
            }
            state = 8;
            try {
              if (compress && !contiguous) {
                state = 9;
                RawToMiniSeed.forceout(seedname); // This block breaks compression, force out any leftovers
              }
              if (compress && contiguous) {
                state = 10;
                try {
                  Object dummy;
                  //synchronized(decompMutex) {
                  dummy = ms.decomp(rawdata);
                  //}
                  if (dummy != null) {     // If no decompression errors
                    int secs = (int) ((ms.getTimeInMillisTruncated() % 86400000L) / 1000);
                    int usecs = ms.getUseconds();
                    if(usecs < 0) {
                      Util.prta("***** Useconds < 0 = "+usecs+" ms="+ms);
                    }
                    if (dbg || chan.contains(dbgChan)) {
                      Util.prt("compress ms=" + ms);
                    }
                    state = 14;
                    RawToMiniSeed.addTimeseries(rawdata, ms.getNsamp(), ms.getSeedNameSB(),
                            ms.getYear(), ms.getDay(), secs, usecs, ms.getRate(),
                            ms.getActivityFlags(), ms.getIOClockFlags(),
                            ms.getDataQualityFlags(), ms.getTimingQuality(), null);
                    state = 15;
                  } else {
                    Util.prt("****** Decompression error on blks =" + ms);
                  }
                } catch (SteimException e) {
                  state = 16;
                  Util.prta("MDP: **** Got a steim exception from ms=" + ms);
                  e.printStackTrace();
                  IndexBlock.writeMiniSeed(ms, "reorg2");  // Write it out so it is not lost even through it is partial
                  stats.incOutBlocks();
                  state = 17;
                }
              } else {    // Do not recompress, just write the block
                state = 11;
                IndexBlock.writeMiniSeed(ms, "reorg");
                stats.incOutBlocks();
                state = 98;
              }
            } catch (IllegalSeednameException e) {
              e.printStackTrace();
            }
            
            // If we are starting up, and just created the file, let the zeroer get going
            if(firstBlock && IndexFile.getIndexFile(julian) != null) {
              Util.prta("First block process sleep");
              Util.sleep(1000);
              firstBlock = false;
            }
            if(firstBlock) {
              Util.prta(" * first block but no file so far "+i);
            }
          }       // for each block in msblks
          state = 12;
          for (int i = msblks.size() - 1; i >= 0; i--) {
            msp.free(msblks.get(i));
            msblks.remove(i);
          }
          state = 13;
        }   // else extents== null
      } catch (RuntimeException e) {
        e.printStackTrace();
      }
      state = 99;
      if (dbg) {
        Util.prta("REORGCT: return " + chan+" maxoff="+maxoff+" #dups"+ndups+" #dupscan="+(ndupscan - ndupscanStart));
      }
    }
  }   // end of class ChannelTHread

  private final class Status {

    private String channel;
    private long totalBlocks;
    //boolean compress;
    private long oorBlocks;
    private long totalFrames;
    private long outBlocks;
    private long lastStart;
    private long duplicates;
    private double rate;
    private final RunList runs = new RunList(10, false);
    private final StringBuilder sb = new StringBuilder(100);

    public long getTotalBlocks() {
      return totalBlocks;
    }

    public long getOORBlocks() {
      return oorBlocks;
    }

    public long getTotalFrames() {
      return totalFrames;
    }

    public long getNDuplicates() {
      return duplicates;
    }

    public long getOutBlocks() {
      return outBlocks;
    }

    public void incOutBlocks() {
      outBlocks++;
    }

    public void incDups() {
      duplicates++;
    }

    @Override
    public String toString() {
      return toStringBuilder().toString();
    }

    public StringBuilder toStringBuilder() {
      runs.consolidate();
      double avgFrames = (double) totalFrames / Math.max(1, totalBlocks);
      double reduction = 100. - ((double) outBlocks / Math.max(1, totalBlocks) * 100.);
      Util.clear(sb).append(channel).append(" #in=").append(totalBlocks).
              append(" #out=").append(outBlocks).
              append(" pct=").append(Util.df21(reduction)).
              append("% oorBlks=").append(oorBlocks).
              append(" #fr=").append(totalFrames).append(" dups=").append(duplicates).
              append(" %oor=").append(oorBlocks * 100 / Math.max(1, totalBlocks)).
              append(" avg frm=").append(Util.df21(avgFrames)).append(" #runs=").append(runs.size());
      if (oorBlocks > 0 || duplicates > 0 || avgFrames < 6.) {
        sb.append(" ***");
      }
      sb.append("\n");
      if (runs.getRuns().size() > 10) {
        for (int i = 0; i < 10; i++) {
          sb.append(runs.getRuns().get(i).toStringBuilder(null)).append("\n");
        }
      } else {
        sb.append(runs.toStringFull());
      }
      return sb;
    }

    public Status(String chan) {
      channel = chan;
    }

    public void doMS(MiniSeed ms) {
      totalBlocks++;
      totalFrames += ms.getUsedFrameCount();
      if(ms.getDataOffset() > 64) {
        totalFrames += 3;
        if(Util.stringBuilderEqual(ms.getSeedNameSB(),"AZFRD  VEP  ")) Util.prt(" **** AZFRD  VEP found #fr="+totalFrames+" #Blk="+totalBlocks+" ms="+ms);
      }
      if (ms.getTimeInMillis() < lastStart) {
        oorBlocks++;
      }
      lastStart = ms.getTimeInMillis();
      if (channel == null) {
        channel = ms.getSeedNameString();
      }
    }

    public void doRun(MiniSeed ms) {
      if (ms.getRate() > 0. && ms.getNsamp() > 0) {
        //if(channel.equals("CIABL  EHZ  ")) 
        //  Util.prt("CIABL");
        runs.add(ms);
      }

    }

    public boolean isCompressable() {
      if(channel.equals("AZFRD  VEP  ")) Util.prt("**** iscompressable "+toString());
      return totalFrames < 6 * totalBlocks;
    }
  }

  public static void main(String[] args) {
    Util.init("msread.prop");
    Util.setModeGMT();
    Util.setProperty("SNWHost", "");
    boolean compressPartial = false;
    int sizeMB = 1024;
    int allocMB = 256;
    int start = 0;
    int massive=0;
    int npermit = 16;        // DEBUG: not a great default, change to 30 later
    String argline = "-mb 2048 -alloc 256 -compress -npermit 1 -datapath /data/ /data2/2017_180_6#0.ms /data2/2017_180_6#1.ms";
    if (args.length == 0) {
      args = argline.split("\\s");
    }
    if (args.length <= 1 && args[0].equals("-reorg")) {
      args = argline.split("\\s");
    }
    for (int i = 0; i < args.length; i++) {
      switch (args[i]) {
        case "-compress":
          compressPartial = true;
          start = i + 1;
          break;
        case "-mb":
          sizeMB = Integer.parseInt(args[i + 1]);
          i++;
          start = i + 1;
          break;
        case "-alloc":
          allocMB = Integer.parseInt(args[i + 1]);
          i++;
          start = i + 1;
          break;
        case "-datapath":
          if (!args[i + 1].endsWith("/")) {
            args[i + 1] += "/";
          }
          Util.setProperty("datapath", args[i + 1]);
          Util.setProperty("ndatapath", "1");
          Util.setProperty("nday", "30000000");
          i++;
          start = i + 1;
          break;
        case "-npermit":
          npermit = Integer.parseInt(args[i + 1]);
          i++;
          start = i + 1;
          break;
        case "-massive":
          massive = Integer.parseInt(args[i+1]);
          break;
        case "-reorg":
          start = i + 1;
          break;
        default:
          break;
      }
    }
    setDefaultUncaughtExceptionHandler();
    EdgeThread.setMainLogname(null);
    MemoryChecker memchk = new MemoryChecker(30, fake);
    Util.setProperty("extendsize", "200000");
    EdgeBlockQueue ebq = new EdgeBlockQueue(100000);    // Got the arguments, now process each file
    try {
      for (int i = start; i < args.length; i++) {
        ReorganizeFile file = new ReorganizeFile(compressPartial, args[i], allocMB, sizeMB, npermit, massive);
        System.gc();
      }
    } catch (RuntimeException e) {
      Util.prta("Runtime err=" + e);
      e.printStackTrace();
    }
    Util.prt("End of execution");
    Util.exit(0);
  }
  /**
   *
   */
  private static void setDefaultUncaughtExceptionHandler() {
    try {
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {

            @Override
            public void uncaughtException(Thread t, Throwable e) {
              Util.prta("********** Uncaught e="+e);
               e.printStackTrace();
               System.exit(1);
            }
        });
    } catch (SecurityException e) {
    }
}
}
