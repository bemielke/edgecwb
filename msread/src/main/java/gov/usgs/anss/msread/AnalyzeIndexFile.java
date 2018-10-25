/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
 /*
 * AnalyzeIndexFile.java
 *r
 * Created on July 24, 2006, 3:24 PM
 *
 * This class is used in the HoldingsScan from msread which can also be used to make gaps.
 */
package gov.usgs.anss.msread;

import gov.usgs.alarm.SendEvent;
import gov.usgs.anss.edge.DuplicateIndexBlockCreatedException;
import gov.usgs.anss.edge.EdgeFileCannotAllocateException;
import gov.usgs.anss.edge.EdgeFileDuplicateCreationException;
import gov.usgs.anss.edge.EdgeFileReadOnlyException;
import gov.usgs.anss.edge.EdgeProperties;
import gov.usgs.anss.edge.FetchList;
import gov.usgs.anss.edge.HoldingSummary;
import gov.usgs.anss.edge.IllegalSeednameException;
import gov.usgs.anss.edge.IndexFile;
import gov.usgs.anss.edge.IndexFileReplicator;
import gov.usgs.anss.edge.IndexKey;
import gov.usgs.anss.edge.MasterBlock;
import gov.usgs.anss.edge.RawDisk;
import gov.usgs.anss.edge.RunList;
import gov.usgs.edge.config.Channel;
import gov.usgs.anss.edgemom.EdgeChannelServer;
import gov.usgs.anss.edgemom.HoldingSender;
import gov.usgs.anss.edgethread.MemoryChecker;
import gov.usgs.anss.seed.MiniSeed;
import gov.usgs.anss.seed.Steim1;
import gov.usgs.anss.seed.Steim2;
import gov.usgs.anss.seed.SteimException;
import gov.usgs.anss.util.Util;
import gov.usgs.anss.util.SeedUtil;
import java.io.IOException;
import java.io.FileNotFoundException;
import java.nio.ByteBuffer;
import java.sql.Timestamp;
import java.util.TreeMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.BitSet;
import java.util.Set;

/**
 *
 * @author davidketchum
 */
public class AnalyzeIndexFile {

  private static final TreeMap<String, RunList> chanruns = new TreeMap<>();
  static final TreeMap<String, Station> stations = new TreeMap<String, Station>();
  static final ArrayList<FetchList> gaps = new ArrayList<FetchList>(1000);        // List of gaps for run
  static final TreeMap<String, HoldingSummary> holdings = new TreeMap<String, HoldingSummary>(); // Treemap of channels and amount of data today
  static final TreeMap<String, IndexBlockAIF> channels = new TreeMap<String, IndexBlockAIF>();

  static int lastJulian;
  static String lastSeedName;
  static int lastOff;
  static StringBuilder mail = new StringBuilder(10000);
  private static EdgeChannelServer echn = new EdgeChannelServer("-empty >echnAIF","ECHN");
  ArrayList<IndexBlockAIF> threads;
  IndexFileReplicator ifl;
  HoldingSender hs;
  RawDisk rw;
  RawDiskBigBuffer dataFile; // Access to the data file so zeroer can use it!
  String filename;
  //ArrayList<IndexBlock> idx;    // The list of index blocks allocated in this file
  byte[] ctlbuf;   // The buffer wrapping the ctl ByteBuffer
  ByteBuffer ctl;  // The header portion of this file
  int julian;       // The julian day represented by this file
  boolean terminate;

  // These form the ctl block portion of the file
  int length;       // length of the controlled data file in blocks
  int next_extent;  // The next extent to allocate in controlled data file
  int next_index; // next index (or master block) to allocate from this file
  int mBlocks[];  // array of blocks currently master_blocks, 0=unallocated
  int maxIndex;

  // These are the master blocks as master Block objects
  MasterBlock mb[];

  // track number of each error
  int nstartTimeOOR;
  int nendTimeOOR;
  int indexOOR;
  int nextIndexOOR;
  int njulianOOR;
  int nextentContig;
  int nextentMultAlloc;
  int nextentDiv64;
  int nearliestOOR;
  int nlatestOOR;
  int nzeroBlock;
  int nchanMisMatch;
  int njulianMisMatch;
  int ndataTimeOOR;
  int nseedNameBad;
  int nrunsbad;
  int ngapsExcessive;
  int nmultAlloc;
  int steimErrors;
  boolean hsNoSend;
  StringBuffer disconChannels;
  AIFShutdown shutdown;
  int ndisconChannels = 0;

  public void close() throws IOException {
    ifl.close();
  }

  public static StringBuilder getMail() {
    return mail;
  }

  public static ArrayList<FetchList> getFetchList() {
    return gaps;
  }

  public static TreeMap<String, HoldingSummary> getHoldings() {
    return holdings;
  }

  public static int getJulian() {
    return lastJulian;
  }

  public String errorSummary() {
    String s = Util.ascdate() + " " + Util.asctime() + " # Error and Warning Summary for " + filename + " :!\n";
    if (nmultAlloc > 0) {
      s += nmultAlloc + " # of extents multiply allocated.!\n";
    }
    if (nchanMisMatch > 0) {
      s += nchanMisMatch + " # channel in extent does not match index channel!\n";
    }
    if (njulianMisMatch > 0) {
      s += njulianMisMatch + " # julian date in extent does not match file julian!\n";
    }
    if (nseedNameBad > 0) {
      s += nseedNameBad + " # times a bad seed name detected in data!\n";
    }
    if (njulianOOR > 0) {
      s += njulianOOR + " # time a julian day is found out-of-range!\n";
    }
    if (nzeroBlock > 0) {
      s += nzeroBlock + " # times a zero block shows up in allocated blocks!\n";
    }
    if (nextentDiv64 > 0) {
      s += nextentDiv64 + " # times an extent starting blocks is not a multiple of 64!\n";
    }
    if (indexOOR > 0) {
      s += indexOOR + " # of index blocks that are out of reasonable range!\n";
    }
    if (nextIndexOOR > 0) {
      s += nextIndexOOR + " # of times a next index points to out of reasonable range!\n";
    }
    if (nearliestOOR > 0) {
      s += nearliestOOR + " # warning times a extent earliest time is out of day range!\n";
    }
    if (nlatestOOR > 0) {
      s += nlatestOOR + " # warning times an extent ending time is out of day range!\n";
    }
    if (ndataTimeOOR > 0) {
      s += ndataTimeOOR + " # times a data block time is out of the extents time range!\n";
    }
    if (nrunsbad > 0) {
      s += nrunsbad + " # index blocks with excessive fragmentation of holdings(>10 )!\n";
    }
    if (ngapsExcessive > 0) {
      s += ngapsExcessive + " # index blocks with excessive number of gaps (>10)!\n";
    }
    if (steimErrors > 0) {
      s += steimErrors + " # data blocks with steim compression errors!\n";
    }
    mail.append(s).append("\n\n");
    Collections.sort(threads);
    String title = 
            "Seed Name   #runs #cons #disc#negdis#blks %Runs %Use MinGap MaxGap   AvgGap  1stGap   lastGap %1stlst Flags "
            + filename + "\n";
    s += title;
    mail.append(title);
    for (IndexBlockAIF thread : threads) {
      if (thread.getSeedName().substring(0, 2).equals("XX")) {
        continue;
      }
      if (!thread.getSeedName().substring(7, 9).equals("HN") && 
              !thread.getSeedName().substring(7, 9).equals("HL") && 
              !thread.getSeedName().substring(0, 2).equals("FB") && 
              !thread.getSeedName().substring(7,8).equals("A") &&
              !thread.getSeedName().substring(7,8).equals("O") &&
              !thread.getSeedName().substring(7,8).equals("V")
              ) {      
        String sum = thread.summary();
        if (sum.contains("***")) {
          s += sum + "\n";
          mail.append(sum).append("\n");
        }
      }
    }
    s += "Seed name    gap ovrl #blks Seed name    gap ovrl #blks Seed name    gap ovrl #blks Seed name    gap ovrl #blks  !\n";
    s += disconChannels.toString();
    Iterator itr = stations.values().iterator();
    Util.prt("#Stations=" + stations.size());
    Util.prt("Station    blks( baud)  Station    blks( baud)  Station    blks( baud)  Station    blks( baud)  Station    blks( baud)");
    StringBuilder sb2 = new StringBuilder(200);
    while (itr.hasNext()) {
      sb2.append(itr.next().toString()).append(" ");
      if (sb2.length() > 110) {
        Util.prt(sb2.toString());
        sb2.delete(0, sb2.length());
      }
    }
    if (sb2.length() > 0) {
      Util.prt(sb2.toString());
    }
    return s;
  }

  /**
   * Creates a new instance of AnalyzeIndexFile
   *
   * @param file THe file being read for analysis
   * @param h The holding sender to send a run summary to
   * @param doNotSendHold If true, do not send holdings.
   */
  public AnalyzeIndexFile(String file, HoldingSender h, boolean doNotSendHold) {
    filename = file;
    hs = h;
    //Util.prtProperties();
    hsNoSend = doNotSendHold;
    ctlbuf = new byte[512];
    ctl = ByteBuffer.wrap(ctlbuf);
    mBlocks = new int[IndexFile.MAX_MASTER_BLOCKS];   // space for master block numbers
    mb = new MasterBlock[IndexFile.MAX_MASTER_BLOCKS];  // space for master block objects
    disconChannels = new StringBuffer(10000);
    shutdown = new AIFShutdown();
    Runtime.getRuntime().addShutdownHook(shutdown);
    Util.setLogPadsb(false);
    //Run.setDebug(true, Util.getOutput());

    try {
      ifl = new IndexFileReplicator(filename, "", 0, false, true);
      julian = ifl.getJulian();
      lastJulian = julian;
      rw = ifl.getRawDisk();
      dataFile = new RawDiskBigBuffer(filename, "r", 128, 128);

      int nchan = reReadMasterBlocks();
      Util.prt(filename + " has " + nchan + " channels");
    } catch (EdgeFileDuplicateCreationException e) {
      Util.prt(" **** IDX analyze duplicate creation! e=" + e);
      e.printStackTrace();
      System.exit(0);
    } catch (FileNotFoundException e) {
      Util.prt(" **** IDX analyze cannot open file=" + filename + " " + e.getMessage());
      SendEvent.edgeSMEEvent("AIXPanic", "Cannot open file=" + filename + " e=" + e, "AnalyzeIndexFile");
      System.exit(0);
    } catch (EdgeFileReadOnlyException e) {
      Util.prt("ReadOnlyException getting indexFile=" + filename + e.getMessage());
      SendEvent.edgeSMEEvent("AIXPanic", "ROExcept file=" + filename + " e=" + e, "AnalyzeIndexFile");
      e.printStackTrace();
      System.exit(0);
    } catch (IOException e) {
      Util.prt("IOException getting index file=" + filename + " " + e.getMessage());
    }
    Util.prta("Wait for EdgeChannelServer to be valid");
    while (!EdgeChannelServer.isValid()) {
    try {
      Thread.sleep(100);
    } catch (InterruptedException expected) {
    }
  }
  }

  /**
   * from the raw disk block, populate the ctl variables
   */
  private void fromCtlbuf() {
    ctl.clear();
    length = ctl.getInt();
    next_extent = ctl.getInt();
    next_index = ((int) ctl.getShort()) & 0xffff;
    Util.prt("FromCtlBuf next_extent=" + next_extent + " next_index=" + next_index + " len=" + length);
    for (int i = 0; i < IndexFile.MAX_MASTER_BLOCKS; i++) {
      mBlocks[i] = ((int) ctl.getShort()) & 0xffff;
      //Util.prt("FromCtlBuf i="+i+" mb="+mBlocks[i]);
    }
  }

  /**
   * Cause the master blocks to be re-read. Should only be done on readonly files.
   *
   * @throws EdgeFileReadOnlyException If one is generated by the reads
   * @throws IOException If one is generated by the reads of the master blocks.
   */

  /**
   * Cause the master blocks to be re-read.Should only be done on readonly files.
   *
   * @return Number of channels
   * @throws EdgeFileReadOnlyException If one is generated by the reads
   * @throws IOException If one is generated by the reads of the master blocks.
   */
  public final int reReadMasterBlocks() throws EdgeFileReadOnlyException, IOException {
    int nchan = 0;
    rw.readBlock(ctlbuf, 0, 512);      // get ctrl region
    fromCtlbuf();                       // Unpack the ctlbuf
    for (int i = 0; i < IndexFile.MAX_MASTER_BLOCKS; i++) {
      if (mBlocks[i] != 0) {
        mb[i] = new MasterBlock((int) mBlocks[i], rw, false);
      } else {
        break;                       // exit when we are out of allocated blocks
      }
      nchan += mb[i].getNChan();
    }
    return nchan;
  }

  /**
   * Find the block number of the first index block for the given channel
   *
   * @param name2 A hopefully legal seed channel name
   * @return the first index block
   * @throws IllegalSeednameException If give channel name does not pass MasterBlock.chkSeedName()
   */
  public synchronized int getFirstIndexBlock(String name2)
          throws IllegalSeednameException {
    int ok;
    StringBuilder name = new StringBuilder();
    name.append(name2);
    MasterBlock.checkSeedName(name);      // insure the name is not crap, throws exception

    // Try to add to all of the master blocks, set ok if it is added
    for (int i = 0; i < IndexFile.MAX_MASTER_BLOCKS; i++) {
      if (mBlocks[i] == 0) {
        break;      // out of master blocks
      }
      ok = mb[i].getFirstIndexBlock(name);// this will also write out the masterblock
      if (ok != -1) {
        return ok;
      }
    }

    return -1;                // It must be a new channel
  }

  /**
   * Find the block number of the first index block for the given channel
   *
   * @param name2 A hopefully legal seed channel name
   * @return the first index block
   * @throws IllegalSeednameException If give channel name does not pass MasterBlock.chkSeedName()
   */
  public synchronized int getLastIndexBlock(String name2)
          throws IllegalSeednameException {
    int ok;
    StringBuilder name = new StringBuilder();
    name.append(name2);
    MasterBlock.checkSeedName(name);      // insure the name is not crap, throws exception

    // Try to add to all of the master blocks, set ok if it is added
    for (int i = 0; i < IndexFile.MAX_MASTER_BLOCKS; i++) {
      if (mBlocks[i] == 0) {
        break;      // out of master blocks
      }
      ok = mb[i].getLastIndexBlock(name);// this will also write out the masterblock 
      if (ok != -1) {
        return ok;
      }
    }

    return -1;                // It must be a new channel
  }

  public RawDisk getRawDisk() {
    return rw;
  }

  public RawDiskBigBuffer getDataRawDisk() {
    return dataFile;
  }

  public IndexFileReplicator getIndexFileReplicator() {
    return ifl;
  }

  /**
   * Analyze the given index file based on parameters given. This is used by msread to "dump"
   * index/data about the file
   *
   * @param indexDetail If true, dump details about the index at the extent level
   * @param errOnly Do everything, but only do output when variances are found
   * @param readData Analysis includes reading the data blocks and checking them as well
   * @param seedMask Do this only for seednames which match this regular expression
   * @param fixIndexLinks if true, if a broken index link is found it is fixed
   * @return A StringBuffer with the analysis (might be pretty big).
   */
  public StringBuilder analyze(boolean indexDetail, int readData, boolean errOnly,
          String seedMask, boolean fixIndexLinks) {
    TreeMap<IndexKey, IndexBlockAIF> id = new TreeMap<IndexKey, IndexBlockAIF>();
    IndexBlockAIF idx;
    StringBuilder sb = new StringBuilder(20000000);
    long freeMemory = Runtime.getRuntime().freeMemory();
    long maxMemory = Runtime.getRuntime().maxMemory();
    long totalMemory = Runtime.getRuntime().totalMemory();

    BitSet blockMap = new BitSet(next_extent / 64);
    BitSet indexBlockMap;
    try {
      indexBlockMap = new BitSet((int) (ifl.getRawDisk().length() / 512));
    } catch (IOException e) {
      Util.prt("***** could not get index file length! e=" + e);
      sb.append("***** could not get index file length! e=").append(e);
      e.printStackTrace();
      return sb;
    }
    mail.append(Util.ascdate()).append(" ").append(Util.asctime()).append(" Start analyze() on ").
            append(filename).append(" mem left=").append((maxMemory - totalMemory) + freeMemory).
            append(" max=").append(maxMemory).append(" tot=").append(totalMemory).append(" free=").
            append(freeMemory).append(" ").append(Util.getNode()).append("/").
            append(Util.getSystemName()).append("/").append(Util.getLocalHostIP()).append("\n");
    int nchan = 0;
    // Check that the seedname in the master block, matches the seedname in first index block and that it is valid!
    for (int i = 0; i < IndexFile.MAX_MASTER_BLOCKS; i++) {
      if (mBlocks[i] == 0) {
        break;
      }
      indexBlockMap.set(mBlocks[i]);    // mark off the master blocks
      for (int j = 0; j < MasterBlock.MAX_CHANNELS; j++) {
        nchan++;
        if (terminate) {
          break;
        }
        StringBuilder seedName = mb[i].getSeedName(j);
        if (seedName == null || Util.stringBuilderEqual(seedName, "            ")) {
          break;
        }
        //Util.prt("i="+i+" j="+j+" "+mb[i].getSeedNameString(j)+" "+mBlocks[i]);
        if (!Util.isValidSeedName(seedName)) {
          Util.prt("mb[" + i + "][" + j + "] blk=" + mBlocks[i] + j + " BAD seed=" + Util.toAllPrintable(seedName) + "|");
        } else if (seedName.toString().matches(seedMask) || seedMask.equals("")) {
          try {
            idx = new IndexBlockAIF(this, seedName.toString(), julian, true,
                    errOnly, indexDetail, readData, blockMap, indexBlockMap);
            if (!idx.getSeedName().equals(seedName.toString())) {
              Util.prt("mb[" + i + "][" + j + "] blk=" + mBlocks[i] + " 1stibl="
                      + mb[i].getFirstIndexBlock(seedName) + " lstidx=" + mb[i].getLastIndexBlock(seedName)
                      + " MBseed=" + Util.toAllPrintable(seedName) + " does not match seedname in idx=" + idx.toString());
            }
            id.put(idx.getKey(), idx);
          } catch (FileNotFoundException e) {
            Util.prt(sb.toString());
            Util.prt("creating block for " + seedName + "/" + SeedUtil.fileStub(julian) + e.getMessage());
            System.exit(0);
          } catch (IllegalSeednameException e) {
            Util.prt("creating first index block seed name illegal=" + seedName + "/" + julian + " i=" + i
                    + " mb#=" + mb[i].getBlockNumber() + " ch=" + j + " iblk=" + mb[i].getFirstIndexBlock(j) + "\n" + e.getMessage());
          } catch (IOException e) {
            Util.prt(sb.toString());
            Util.prt("IOException getting indexblock=" + seedName + "/" + SeedUtil.fileStub(julian)
                    + " mb=" + i + "/" + mBlocks[i] + " off=" + j + " " + e.getMessage() + e);
            e.printStackTrace();
            System.exit(0);
          } catch (EdgeFileReadOnlyException | DuplicateIndexBlockCreatedException e) {
            Util.prt("ReadONlyException getting indexBlock=" + seedName + "/" + julian + e.getMessage());
          }
        }
      }   // For each channel
    }     // for each masterBlock

    Util.prt("File=" + filename + " nextExtent=" + next_extent + " len=" + length
            + " nextIndex=" + next_index + " nchan=" + nchan);
    try {
      sb.append("File=").append(filename).append(" nextExtent=").append(next_extent).append(" len=").append(length).append(" nextIndex=").append(next_index).append(" nchan=").append(nchan).append(" datfile.len=").append(dataFile.length() / 512L).append("\n");
    } catch (IOException e) {
    }
    // Got an alpha list of all of the indexblocks list them out here.
    Iterator it = id.values().iterator();
    long lastTickle = System.currentTimeMillis();
    threads = new ArrayList<IndexBlockAIF>(id.size());
    Util.prt("There are " + id.size() + " threads to start");
    int count = 0;
    try {
      while (it.hasNext()) {
        idx = (IndexBlockAIF) it.next();
        if (seedMask.length() > 0 && !idx.getSeedName().matches(seedMask)) {
          continue;
        }
        if (indexDetail || errOnly || readData != 0 || hs != null) {
          threads.add(idx);
          //Thread t = new Thread(idx);
          idx.start();      // start run on string detail for this channel
          count++;
          //if(threadCount > 0) break;    //DEBUG: only start one for clarity!
        } else {
          idx.setDone(true);
        }
      }
    } catch (OutOfMemoryError e) {
      Util.prt(" ***** Got an OutOfMemory error we need to abort and notify dave count=" + count + " of " + id.size());
      SendEvent.edgeSMEEvent("OutOfMem", "In UpdateHoldings run for " + filename + " " + count + "/" + id.size(), this);
      MemoryChecker.checkMemory();
      e.printStackTrace();
      System.exit(1);
    }
    Util.prta(threads.size() + " threads have been started.");

    // now we wait for all of the Threads to be done!
    boolean done = false;
    int loop = 0;
    long eofTime = 0;
    while (!done) {
      if (terminate) {
        break;
      }
      Util.sleep(2000);
      long elapse = System.currentTimeMillis();
      int minblock = 2147000000;
      done = true;
      int ndone = 0;
      int blk;
      IndexBlockAIF minthr = null;
      int lastBlock = dataFile.getLastBlock();
      int waiting = 0;
      for (IndexBlockAIF thread : threads) {
        if (!thread.isDone()) {
          done = false;
        } else {
          ndone++;
        }
        blk = thread.getReadingBlock();
        if (blk >= lastBlock) {
          waiting++;
        }
        if (blk < minblock) {
          minthr = thread;
          minblock = blk;
        }
      }
      if (minblock >= dataFile.getLastBlock()) {
        if (dataFile.getNextBuffer()) {
          Util.prta("Get nextbuffer says EOF.  wait for threads to end");
          if (eofTime == 0) {
            eofTime = System.currentTimeMillis();
          } else if (System.currentTimeMillis() - eofTime > 120000) {
            Util.prta("EOF present for 2 minutes assume threads will never exit!");
            done = true;
            loop = 29;      // fource an output
          }
        }
      }
      loop++;
      if (loop % 30 == 0) {
        Util.prta("minblock=" + minblock + " lastblock=" + lastBlock + " #wait=" + waiting
                + " done=" + ndone + " of " + threads.size()
                + " thr=" + (minthr == null ? "null" : minthr.getSeedName()) + " elapse=" + (System.currentTimeMillis() - elapse));
        freeMemory = Runtime.getRuntime().freeMemory();
        maxMemory = Runtime.getRuntime().maxMemory();
        totalMemory = Runtime.getRuntime().totalMemory();
        Util.prta(" memory left=" + ((maxMemory - totalMemory + freeMemory) / 1000000) + " max=" + (maxMemory / 1000000)
                + " tot=" + (totalMemory / 1000000)
                + " free=" + (freeMemory / 1000000) + "mB #gaps=" + gaps.size() + " #holdings=" + holdings.size() + " #stat=" + stations.size()
                + " #runs=" + (hs != null ? hs.getRuns().size() : "null") + " sb=" + (sb.length() / 1000000) + " mB");
      }
    }

    // Now we need to build up the String for return
    int discons = 0;
    int negdiscons = 0;
    int nblks = 0;
    for (IndexBlockAIF thread : threads) {
      idx = thread;
      if (idx.isAlive()) {
        idx.terminate();
      }
      //sb.append(idx.toStringDetail(errOnly, readData, blockMap, runs));
      discons += idx.getDiscons();
      negdiscons += idx.getNegDiscons();
      nblks += idx.getNblocks();
      if (idx.getDetailString().length() > 1) {
        sb.append(idx.getDetailString()).append("\n");
      }
      if (sb.length() > 100000000) {
        Util.prt("Sbsize=" + sb.length() + sb);
        break;
      }
      idx.close();      // free up any space allocated-
    }
    //threads.clear();    // free up all references to inde blocks
    dataFile.close();     // Do not need this big buffer any more

    // Use the bit map on extents to make sure they are all in use
    Util.prta("Start scan for extent in use but not expected");
    sb.append("Start scan for extent in use but not expected #extents=").append(next_extent / 64).append("\n");
    for (int i = next_extent / 64; i < blockMap.size(); i++) {
      if (blockMap.get(i)) {
        sb.append("    ** more extents in use than expected=").append(next_extent).
                append(" in use=").append(i * 64).append(" size=").append(blockMap.size()).append("\n");
      }
    }
    if ((indexDetail || errOnly) && seedMask.equals("............")) {
      for (int blk = 0; blk < next_extent / 64; blk++) {
        if (!blockMap.get(blk)) {
          sb.append("      **** extent ").append(blk * 64).append(" is not in use!\n");
        }
      }
    }
    byte[] buf = new byte[512];
    gov.usgs.anss.edge.IndexBlock tmp = null;
    Util.prta("Start scan for unlinked index blocks size=" + indexBlockMap.size());
    sb.append("Start scan for unlinked index blocks size=").append(indexBlockMap.size()).append("next_index=").append(next_index).append("\n");
    for (int i = 1; i < indexBlockMap.size(); i++) {
      if (i >= next_index) {
        break;
      }
      if (!indexBlockMap.get(i)) {
        try {
          ifl.getRawDisk().readBlock(buf, i, 512);
          if (buf[0] == 0 && buf[1] == 0 && buf[2] == 0 && buf[3] == 0 && buf[4] == 0) {
            sb.append(" *** index block not on line but is zero o.k???? blk=").append(i).append(" next_index=").append(next_index).append("\n");
          } else {
            if (tmp == null) {
              tmp = new gov.usgs.anss.edge.IndexBlock(buf, i, julian, rw);
            } else {
              tmp.reload(buf, i);
            }
            sb.append(" *** index block not on link ").append(tmp.getSeedName()).append(" blk=").
                    append(tmp.getIndexBlockNumber()).append(" nextind=").append(tmp.getNextIndex()).append("\n");
            sb.append(tmp.toStringDetail(errOnly, readData, blockMap));
          }
        } catch (IOException | EdgeFileCannotAllocateException e) {
          if (i <= next_index) {
            Util.prt("Failed to read index block iblk=" + i + " next_index=" + next_index + " e=" + e);
          }
        }

      }
    }
    return sb;
  }

  public static int doFetchList(StringBuilder sb) {
    int ntotgaps = 0;
    Set<String> keys = chanruns.keySet();
    Iterator<String> itr = keys.iterator();
    while (itr.hasNext()) {
      String chan = itr.next();
      RunList rns = chanruns.get(chan);
      int ngaps = doFetchListDetail(rns, sb);
      //if(ngaps > 0) {
      //  for(int i=0; i<runs.size(); i++) sb.append(i+" "+runs.get(0).getSeedname()+" "+runs.get(i)+"\n");
      //}      if(ngaps > 10) {
      sb.append("      ").append(chan).append(" lots of gaps ").append(ngaps).
              append(" gaps in ").append(rns.size()).append(" runs\n");
      ntotgaps += ngaps;
    }
    return ntotgaps;
  }

  private static int doFetchListDetail(RunList runs, StringBuilder sb) {
    synchronized (gaps) {
      boolean dbg = false;
      if (runs.size() == 0) {
        return 0;
      }
      Collections.sort(runs.getRuns());
      int ngaps = 0;
      double rate = -1.;
      for (int i = 0; i < runs.size(); i++) {
        if (rate <= 0.) {
          rate = runs.get(i).getRate();
        }
      }
      if (runs.get(0).getSeedname().substring(0, 6).equals("CUMTDJ")) {
        Util.prt(" found " + runs.get(0).getSeedname() + " rate=" + rate + " old=" + runs.get(0).getRate());
        Util.prt("Runs=" + runs.toString());
      }
      if (rate <= 0.) {
        return 0;
      }
      long mssamp = (long) (2000. / rate + 0.5); // two sample widths, was 1 sample width
      long midnight = (runs.get(0).getStart() / 2 + runs.get(runs.size() - 1).getEnd() / 2) // avg time of day
              / 86400000L * 86400000L;
      //GregorianCalendar gtmp = new GregorianCalendar();
      long gtmp;
      if (runs.get(0).getSeedname().substring(0, 2).equals("XX")
              || runs.get(0).getSeedname().substring(0, 2).equals("FB")
              || runs.get(0).getSeedname().substring(10).equals("FB")) {
        return 0;
      }
      // is there a starting gap
      //if(runs.get(0).getSeedname().trim().equals("CUANWB LHE"))
      //  Util.prt("Got CUANWB LHE");
      if (runs.get(0).getStart() - midnight >= mssamp) {// inning of day gap
        gtmp = midnight;
        double duration = (runs.get(0).getStart() - midnight - 1) / 1000.;
        sb.append("Beg of day Gap : ").append(runs.get(0).getSeedname()).append(" ").
                append(Util.toDOYString(gtmp)).append(" dur=").append(duration).append(" #=").
                append(gaps.size()).append(" ").append(Util.asctime2(runs.get(0).getStart())).append("\n");
        if (dbg) {
          Util.prt("Beg of day Gap : " + runs.get(0).getSeedname() + " " + Util.toDOYString(gtmp)
                  + " dur=" + duration + " #=" + gaps.size() + " " + Util.asctime2(runs.get(0).getStart()));
        }
        gaps.add(new FetchList(runs.get(0).getSeedname().toString(), new Timestamp(gtmp), (int) (gtmp % 1000),
                duration, "", "open"));
        ngaps++;
      }
      // curr is always the time of the next expected sample (exactly) so gaps should be from one mssamp before just after the last
      // actually good sample
      long curr = runs.get(0).getStart();
      if (dbg) {
        for (int i = 0; i < runs.size(); i++) {
          Util.prt(i + " " + runs.get(0).getSeedname() + " " + runs.get(i));
        }
      }
      for (int i = 0; i < runs.size(); i++) {
        if (runs.get(0).getSeedname().substring(0, 6).equals("CUMTDJ")) {
          Util.prt(i + " diff=" + (runs.get(i).getStart() - curr));
        }
        if (runs.get(i).getStart() - curr <= mssamp) {  // we start before pointer to no gap
          if (runs.get(i).getEnd() >= curr) {
            curr = runs.get(i).getEnd(); // move current to end
          }
        } else {  //we have a gap list it
          gtmp = curr - mssamp + 1;        // Start of gap
          double duration = (runs.get(i).getStart() - curr + mssamp - 2) / 1000.;
          sb.append("Mid of day Gap : ").append(runs.get(0).getSeedname()).append(" ").
                  append(Util.toDOYString(gtmp)).append(" " + " dur=").append(duration).
                  append(" #=").append(gaps.size()).append("\n");
          if (dbg) {
            Util.prt("Mid of day Gap : " + runs.get(0).getSeedname() + " " + Util.toDOYString(gtmp) + " dur=" + duration + " #=" + gaps.size());
          }
          gaps.add(new FetchList(runs.get(0).getSeedname().toString(),
                  new Timestamp(gtmp - (gtmp % 1000)), (int) (gtmp % 1000),
                  duration, "", "open"));
          curr = runs.get(i).getEnd();
          ngaps++;
        }
        for (int j = i + 1; j < runs.size(); j++) {                    //  Check other runs to see if they extend it, if so move it
          if (runs.get(j).getStart() - curr <= mssamp
                  && runs.get(j).getEnd() >= curr) {
            curr = runs.get(j).getEnd();
          }
        }
        if (curr > midnight + 86400000L - mssamp) {
          break;
        }
      }
      // Is there an end-of-day gap
      if (midnight + 86400000L - curr >= mssamp) {    // Is
        gtmp = curr - mssamp + 1;
        double duration = (midnight + 86400000L - curr + mssamp - 1) / 1000.;
        if (dbg) {
          Util.prt("End of day Gap : " + runs.get(0).getSeedname() + " " + Util.toDOYString(gtmp) + " dur=" + duration + " #=" + gaps.size());
        }
        sb.append("End of day Gap : ").append(runs.get(0).getSeedname()).append(" ").
                append(Util.toDOYString(gtmp)).append(" dur=").append(duration).append(" #=").
                append(gaps.size()).append("\n");
        gaps.add(new FetchList(runs.get(0).getSeedname().toString(),
                new Timestamp(gtmp - (gtmp % 1000)), (int) (gtmp % 1000),duration, "", "open"));
        ngaps++;
      }

      return ngaps;
    }
  }

  /*
 * IndexBlock.java
 *
 * Created on May 24, 2005, 9:52 AM
 *
 *
   */

  /**
   * This class handles index blocks for EdgeMom. The physical form is : SeedName (12 char)
   * nextIndext (int) updateTime (int) startingBlock (int), bitmap (long), earliestTime (short),
   * latestTime (short) Repeat last MAX_EXTENTS times.
   *
   * The "real time" (that is EdgeMom usage) creates blocks for writing and the actual disk writes
   * are done through this class insuring the integrity of the index blocks as the data blocks are
   * written to disk. The file names for writing are derived from the "julian" day of the data and
   * the looked up "edge node" to make unique file names.
   *
   * There are also "readonly" constructors and methods which allow an exisiting file and index to
   * be read back and manipuated for queries and such. The read backs require full file names rather
   * that the julian dates and looked up edge node since these methods might be used on other
   * computers to read data (such as the CWB).
   *
   *
   * @author davidketchum
   */
  private final class IndexBlockAIF extends Thread implements Comparable<IndexBlockAIF> {

    /**
     * this is the number of extents in one index block (block#, bit maps, earliest/latest times)
   *
     */
    public static final int MAX_EXTENTS = 30;
    private RawDisk rw;       // access to the index file
    private RawDiskBigBuffer datout;   //
    private final AnalyzeIndexFile indexFile;  // Access to the controlling index file
    private MiniSeed ms;                  // Scratch Miniseed buffer
    private final int julian;
    private int iblk;

    private final byte[] buf = new byte[512];   // Note: wrapped by bb later
    private final byte[] extentbuf = new byte[4096];;
    private final byte[] inbuf = new byte[32768];
    private ByteBuffer bb;
    private final IndexKey key;
    //private boolean isModified;     // Set true by changes, false by updating to disk

    // Data for the raw block
    private String seedName;        // Exactly 12 characters long!
    private String band;
    private int nextIndex;          // If -1, no next index (this is the last one!)
    private long updateTime;         // seconds since midnight 1979
    private final int startingBlock[];
    private final long bitMap[];
    private final short earliestTime[];
    private final short latestTime[];
    private final boolean errOnly;
    private final int readData;
    private final BitSet blockMap;
    private final BitSet indexBlockMap;
    //ArrayList<msread.Run> runs=new ArrayList<msread.Run>(10);
    private RunList runs;
    private int lastRunsSize;
    private StringBuilder sb = new StringBuilder(2000);
    private final StringBuilder zeros = new StringBuilder(100);
    private boolean done;
    private final boolean indexDetail;
    //private DecimalFormat df2;   // used to convert ints to 2 character fixed length ascii
    //private DecimalFormat df22;
    //private final DecimalFormat df8 = new DecimalFormat("00000000");
    private byte[] frames;           // scratch space for data dumps

    // Local variables
    private int currentExtentIndex;  // the index in startingBlock[] of the current extent
    private int nextBlock;        // Within the last extent, where to write next(I don't this this is used)
    private long lastDate;
    private long lastlength;
    private double lastRate;
    private String lastString;
    private int lastLevel;
    private int ndiscons;                   // Number of discontinuities found, read must be on
    private int negdiscons;
    private int nblks;
    private int readingBlock;
    private boolean lowPriority;
    private boolean terminate;
    private int noverlaps;

    //public ArrayList<Run> getRuns() {return runs;}
    public String summary() {
      double totalRun = 0.;
      double trimRun = 0.;
      double minGap = 86500.;
      double maxGap = -1000;
      double totalGap = 0.;
      long minTimeGap = 0;
      long maxTimeGap = 0;
      Channel ch = EdgeChannelServer.getChannel(seedName, false);
      String flag = "";
      if (noverlaps > 200 && runs.size() > 3) {
        flag += "Cons ";
      }
      int ngap = runs.size() - 1;
      for (int i = 0; i < runs.size(); i++) {

        if (i < runs.size() - 1) {
          double gap = (runs.get(i + 1).getStart() - runs.get(i).getEnd()) / 1000.; // Length of gap in seconds
          if (minGap > gap) {
            minGap = gap;
          }
          if (maxGap < gap) {
            maxGap = gap;
          }
          totalGap += gap;
          if (runs.get(i).getEnd() < minTimeGap || minTimeGap == 0) {
            minTimeGap = runs.get(i).getEnd();
          }
          if (runs.get(i + 1).getStart() > maxTimeGap || maxTimeGap == 0) {
            maxTimeGap = runs.get(i).getStart();
          }
        }

        // Examine the last run of the day, its beginning is either the last gap end time
        // or if the end of the holding is near the mignight boundary, then there is an EOD gap
        if (i == runs.size() - 1) {
          if (runs.get(i).getStart() > maxTimeGap || maxTimeGap == 0) {
            maxTimeGap = runs.get(i).getStart();
          }
          if (minTimeGap == 0) {
            minTimeGap = runs.get(i).getStart();
          }

          long millis = runs.get(i).getEnd() % 86400000;
          if (seedName.equals("USWMOK VMW10")) {
            Util.prta(seedName + " size=" + runs.size() + " ms=" + millis + " Run=" + runs.get(i));
            for (int j = 0; j < runs.size(); j++) {
              Util.prt(j + " " + runs.get(j).toString());
            }
          }
          if (!(millis >= 86390000 || millis <= 10000)) {    // There is an end of day gap
            maxTimeGap = (runs.get(i).getStart() / 86400000 + 1) * 86400000 - 1; // Not use start to be on same day
            totalGap += (maxTimeGap + 1 - runs.get(i).getEnd()) / 1000.;
            ngap++;
          }
        }
        // Examine first run of the day for beginning of day gap
        if (i == 0) {    // check for gap at the begining of day
          long millis = runs.get(i).getStart() % 86400000;
          if (millis > 10000) {      // yes, there is a beginning of day gap
            if (minGap > millis / 1000.) {
              minGap = millis / 1000.;
            }
            if (maxGap < millis / 1000.) {
              maxGap = millis / 1000.;
            }
            totalGap += millis / 1000.;
            minTimeGap = runs.get(i).getStart() / 86400000 * 86400000;  // beginning of day is a gap
            ngap++;
          }
        }
        totalRun += runs.get(i).getLength();
        if (runs.get(i).getLength() > 300.) {
          trimRun += runs.get(i).getLength() - 300.;
        }
      }
      if (minGap == 86600.) {
        minGap = 0.;
      }
      if (maxGap == -1000.) {
        maxGap = 0.;
      }
      double avgGap = totalGap / Math.max(1, ngap);
      boolean expected = false;
      if (ch != null) {
        expected = ch.getExpected();
      }
      if (runs.size() >= 1000) {
        flag += "Hack";
      } else if ((nblks > 3600 || expected)
              && //  likely continuous
              (band.equals("BH") || band.equals("SH"))) {
        if (runs.size() > 50) {
          flag += "Gap ";
        }
        if (trimRun < 80.) {
          flag += "%Use ";
        }
        if (Math.abs((totalRun + totalGap) - 86400.) > 10.) {
          Util.prta("**** total gap + total run does not equal a full day " + seedName + " " 
             + " gap=" + Util.df23(totalGap) + " run=" + Util.df23(totalRun) + " #run="+runs.size());
          if (runs.size() < 200) {
            for (int j = 0; j < runs.size(); j++) {
              Util.prta(runs.get(j).toString());
            }
          }
        }
      } else if ((nblks > 12000 || expected)
              && // likely continuous
              (band.equals("HH") || band.equals("EH") || band.equals("HN"))) {
        if (runs.size() > 50) {
          flag += "Gap ";
        }
        if (trimRun < 80.) {
          flag += "%Use ";
        }
        if (Math.abs((totalRun + totalGap) - 86400.) > 10.) {
          Util.prta("**** total gap + total run does not equal a full day " + seedName 
                  + " gap=" + Util.df23(totalGap) + " run=" + Util.df23(totalRun) + " #run="+runs.size());
          if (runs.size() < 200) {
            for (int j = 0; j < runs.size(); j++) {
              Util.prta(runs.get(j).toString());
            }
          }
        }
      } else if ((nblks > 120 || expected)
              && // likely continuous
              (band.equals("LH") || band.equals("LN"))) {
        if (runs.size() > 20) {
          flag += "Gap ";
        }
        if (trimRun < 80.) {
          flag += "%Use ";
        }
        if (Math.abs((totalRun + totalGap) - 86400.) > 10.) {
          Util.prta("**** total gap + total run does not equal a full day " + seedName + " " 
              + " gap=" + Util.df23(totalGap) + " run=" + Util.df23(totalRun) + " #run="+runs.size());
        }
      }
      totalRun = totalRun / 86400. * 100.;
      trimRun = trimRun / 86400. * 100.;
      return seedName
              + Util.leftPad(runs.size() + "", 6)
              + Util.leftPad("" + noverlaps, 5)
              + Util.leftPad("" + ndiscons, 5)
              + Util.leftPad("" + negdiscons, 5)
              + Util.leftPad("" + nblks, 6)
              + Util.leftPad(Util.df21(totalRun), 6)
              + Util.leftPad(Util.df21(trimRun), 6)
              + Util.leftPad(Util.df21(minGap), 8)
              + Util.leftPad(Util.df21(maxGap), 8)
              + Util.leftPad(Util.df21(avgGap), 8)
              + " " + Util.asctime(minTimeGap)
              + " " + Util.asctime(maxTimeGap)
              + Util.leftPad(Util.df21((maxTimeGap - minTimeGap) / 864000.), 6)
              + (flag.equals("") ? "" : " *** " + flag);
    }

    public int getNOverlaps() {
      return noverlaps;
    }

    public RunList getRuns() {
      return runs;
    }

    public int getReadingBlock() {
      return readingBlock;
    }

    public int getDiscons() {
      return ndiscons;
    }

    public int getNegDiscons() {
      return negdiscons;
    }

    public int getNblocks() {
      return nblks;
    }

    public boolean isDone() {
      return done;
    }

    public void setDone(boolean t) {
      done = t;
    }

    public StringBuilder getDetailString() {
      return sb;
    }

    public void terminate() {
      terminate = true;
      interrupt();
    }

    public void close() {
      sb = null;
      frames = null;
      rw = null;
      datout = null;
      //indexFile=null;
      bb = null;
      //runs.clear();
      /*if(runs != null)
      for(int i=0; i<runs.getRuns().)size(); i++)
        if(runs.get(i) != null) runs.get(i).clear();
    runs = null;*/
 /* try {
      rw.close();

      datout.close();
    } catch(IOException e) {Util.prt("IOException closing iblk="+toString()+" "+e);}*/
    }
    // static data needed to implement the write all mini-seed through the writeMiniSeed static
    // function

    /**
     * create a string representing this index block
     *
     * @return The block number, seedname, julian date, # extents, and next index annotated
     */
    @Override
    public String toString() {
      int i;
      for (i = 0; i < MAX_EXTENTS; i++) {
        if (startingBlock[i] == -1) {
          break;
        }
      }
      return "Index blk=" + (Util.leftPad("" + iblk, 6) + "/" + currentExtentIndex + "       ").substring(0, 9) + " " + seedName
              + " julian=" + julian + " #extent=" + i + " next=" + nextIndex + " readblk=" + readingBlock;
    }

    @Override
    public void run() {
      Station stat;
      try {
        synchronized (stations) {
          stat = stations.get(seedName.substring(0, 7).trim());
          if (stat == null) {
            stat = new Station(seedName.substring(0, 7).trim());
            stations.put(seedName.substring(0, 7).trim(), stat);
          }
        }

        synchronized (channels) {
          IndexBlockAIF idx = channels.get(seedName);
          if (idx == null) {
            //Util.prt ("create chan="+seedName+" "+this);
            channels.put(seedName, this);
          } else {
            Util.prt(" **** Duplicate Channel : " + seedName + " " + (indexFile == null ? "null" : indexFile.filename) + " " + (idx.indexFile == null ? "null" : idx.indexFile.filename));
            sb.append(" **** Duplicate Channel possible ").append(seedName).append(" ").
                    append(indexFile == null ? "null" : indexFile.filename).append(" ").
                    append(idx.indexFile == null ? "null" : idx.indexFile.filename).append("\n");
            mail.append(" **** Duplicate Channel : ").append(seedName).append(" ").
                    append(indexFile == null ? "null" : indexFile.filename).append(" ").
                    append(idx.indexFile == null ? "null" : idx.indexFile.filename).append("\n");
          }
        }
        
        boolean ok = true;
        negdiscons = 0;
        nblks = 0;
        ndiscons = 0;
        negdiscons = 0;
        StringBuilder sblk = new StringBuilder(2000);
        while (ok) {
          //Util.prta("idx="+toString());  
          String last = "  " + toString() + "\n";
          String line = null;
          //nblks=0;
          if (!errOnly) {
            sb.append(last);
          }
          // nextIndex should be -1 or positive 
          if (nextIndex < -1 || nextIndex > 64000) {
            if (errOnly) {
              sb.append("   ").append(toString()).append("\n");
              sb.append(last);
            }
            sb.append("\n      **** next index is out of range").append(nextIndex).append("\n");
            nextIndexOOR++;
          }
          if (iblk < 2 || iblk > 50000) {
            if (errOnly) {
              sb.append("   ").append(toString()).append("\n");
              sb.append(last);
            }
            sb.append("\n      **** index block number is out of range\n");
            indexOOR++;
          }
          if (julian < SeedUtil.toJulian(2004, 320) || julian > SeedUtil.toJulian(2040, 365)) {
            if (errOnly) {
              sb.append("   ").append(toString()).append("\n");
              sb.append(last);
            }
            sb.append("      **** warning: Julian date out of range 2005-001 to 2040-365!");
            njulianOOR++;
          }
          int i;
          int off;        // Is there any carry over offset from last block
          if (seedName.equals(lastSeedName)) {
            off = lastOff;
          } else {
            off = 0;
          }
          lastSeedName = seedName;
          int threshold = 1000;

          for (i = 0; i < MAX_EXTENTS; i++) {
            if (startingBlock[i] == -1) {
              for (int j = i; j < MAX_EXTENTS; j++) {
                if (startingBlock[j] != -1) {
                  if (errOnly) {
                    sb.append(last);
                  }
                  sb.append("      **** all startingBlocks after first unused are not unused!!!");
                  nextentContig++;
                }
              }
              break;
            }
            if (blockMap.get(startingBlock[i] / 64)) {
              if (errOnly) {
                sb.append("   ").append(toString()).append("\n");
                sb.append(last);
              }
              sb.append("\n    ****** extent ").append(startingBlock[i]).append(" is multiply allocated!\n");
              nextentMultAlloc++;
            }
            blockMap.set(startingBlock[i] / 64);      // Set this extent as in use
            int secs = earliestTime[i] * 3;
            int hr = secs / 3600;
            secs = secs % 3600;
            int min = secs / 60;
            secs = secs % 60;
            String start = Util.df2(hr) + ":" + Util.df2(min) + ":" + Util.df2(secs);
            secs = latestTime[i] * 3;
            hr = secs / 3600;
            secs = secs % 3600;
            min = secs / 60;
            secs = secs % 60;
            String end = Util.df2(hr) + ":" + Util.df2(min) + ":" + Util.df2(secs);
            if (errOnly) {
              line = "    " + (i + "   ").substring(0, 4) + " start=" + (startingBlock[i] + "        ").substring(0, 8) + " "
                      + (Long.toHexString(bitMap[i]) + "                           ").substring(0, 16)
                      + " time range=" + start + " " + end + "\n";
            } else {
              sb.append("    ").append((i + "   ").substring(0, 4)).append(" start=").
                      append((startingBlock[i] + "        ").substring(0, 8)).append(" ").
                      append((Long.toHexString(bitMap[i]) + "                          ").substring(0, 16)).
                      append(" time range=").append(start).append(" ").append(end).append("\n");
            }

            // If this is not a full bitMap, check for it being the "last" 
            // and that they are contiguously assigned
            int nb = 64;
            if (bitMap[i] != -1) {       // Is bit map not full
              boolean chk = true;
              if ((i + 1) >= MAX_EXTENTS) {
                if (nextIndex == -1) {
                  chk = false; // its in last possible extent
                }
              } else if (chk && startingBlock[i + 1] == -1) {
                chk = false;        // its is in the last  used extent
              }
              if (chk) {
                if (errOnly) {
                  sb.append("   ").append(toString()).append("\n");
                  sb.append(line);
                }
                sb.append("       ***** ").append(seedName).append(" bitMap not full and extent is not last one\n");
              }
              nb = 0;
              long mask = bitMap[i] & 0x7fffffffffffffffL;
              while ((mask & 1) == 1) {
                mask = mask >> 1;
                nb++;
              }
              if (mask != 0) {
                if (errOnly) {
                  sb.append("   ").append(toString()).append("\n");
                  sb.append(line);
                }
                sb.append("      **** ").append(seedName).append(" bitMap has non-contiguous used blocks! nb2=").append(nb).append(" ").append(Util.toHex(bitMap[i])).append("\n");

              }

            }
            stat.addNblk(nb, SeedUtil.doy_from_ymd(SeedUtil.fromJulian(julian)));

            // Starting block must be divisible by 64 evenly
            if ((startingBlock[i] % 64) != 0) {
              if (errOnly) {
                sb.append("   ").append(toString()).append("\n");
                sb.append(line);
              }
              sb.append("      ***** ").append(seedName).append(" starting block not divisible by 64!\n");
              nextentDiv64++;
            }

            // earliest and latest times should be today!
            if (earliestTime[i] < 0 || (earliestTime[i] > 28800 && earliestTime[i] != 32700)) {
              if (errOnly) {
                sb.append("   ").append(toString()).append("\n");
                sb.append(line);
              }
              sb.append("      **** ").append(seedName).append(" warning: earliest time is not on this day\n");
              nearliestOOR++;
            }
            if ((latestTime[i] < 0 && latestTime[i] != -32700)
                    || (latestTime[i] > 29100 && !seedName.substring(7, 8).equals("V"))) {  // allow 15 minutes into next day except some V are SteimI
              if (errOnly) {
                sb.append("   ").append(toString()).append("\n");
                sb.append(line);
              }
              sb.append("      **** ").append(seedName).append(" warning: latest time is not on this day ").append(latestTime[i]).append("\n");
              nlatestOOR++;
            }

            // If set, read in the data in the extent and verify its in the right place
            if (sblk.length() > 0) {
              sblk.delete(0, sblk.length());
            }
            if (errOnly) {
              sblk.append("   ").append(toString()).append("\n").append(line);
            }
            if (readData > 0 || hs != null) {
              off = readit(i, nb, off, sblk, line);
              if(seedName.equals("USWMOK VMW10")) 
                Util.prt(seedName+" "+startingBlock[i]+" nb="+nb+" off="+off);
            }
          }   // for each extent 
          try {
            if (terminate) {
              break;
            }
            ok = nextIndexBlock();      // If done, transform to next one
          } catch (IOException e) {
            Util.prt("nextIndexBlock IOexception e=" + e.getMessage());
            Util.prt("idx=" + toString());
            ok = false;
            //e.printStackTrace();
          }
        }///
        noverlaps += runs.consolidate();
        if (noverlaps > 10) {
          sb.append("      ").append(toString()).append(" #overlaps ").append(noverlaps).append("\n");
        }
        //Util.prt(seedName+" run of blocks complete nruns="+runs.size());
        if (!terminate) {
          // All lof the threads have run to completion - we need

          if (errOnly && runs.size() > 10) {
            nrunsbad++;
            double shortest = 10000000.;
            double longest = 0.;
            double avg = 0.;
            for (int i = 0; i < runs.size(); i++) {
              shortest = Math.min(shortest, runs.getRuns().get(i).getLength());
              longest = Math.max(longest, runs.getRuns().get(i).getLength());
              avg += runs.getRuns().get(i).getLength();
            }
            sb.append("     ").append(toString()).append(" fragmented nruns=").
                    append(runs.size()).append(" #overlaps=").append(noverlaps).
                    append(" short=").append(Util.df22(shortest)).append(" long=").
                    append(Util.df22(longest)).append(" avg=").append(Util.df22(avg / runs.size())).append("\n");
          }
          if (runs.size() > 0) {
            if (runs.get(0).getSeedname().substring(0, 6).equals("CUMTDJ")) {
              Util.prt("MTDJ : runs.size=" + runs.size());
              Util.prt("MTDJ :" + runs.toString());
            }
          }
          if (runs.size() < 1500) {
            noverlaps += runs.consolidate();
            //int ngaps = doFetchList(runs,  sb);  // This needs to be done from 
            // Display the runs that generated those gaps - DEBUG mainly
            //if(ngaps > 0) {
            //  for(int i=0; i<runs.size(); i++) sb.append(i+" "+runs.get(0).getSeedname()+" "+runs.get(i)+"\n");
            //}
            //if(ngaps > 10) {
            //  ngapsExcessive++;
            //    sb.append("      ").append(toString()).append(" lots of gaps ").append(ngaps).
            //            append(" gaps in ").append(runs.size()).append(" runs\n");
            //}
            if (ndiscons + negdiscons > 100) {
              disconChannels.append(seedName).append(Util.leftPad("" + ndiscons, 5)).
                      append(Util.leftPad("" + negdiscons, 5)).append(Util.leftPad("" + nblks, 5)).
                      append(ndiscons > nblks / 10 || negdiscons > nblks / 10 ? "*" : " ");
              ndisconChannels++;
              if (ndisconChannels % 4 == 0) {
                disconChannels.append("!\n");
              }
            }
          } else {
            mail.append(seedName).append(" has way too many runs. No fetchlist or holdings generated.\n");
            sb.append(seedName).append(" has way too many runs.  No fetchlist of holdings generated.\n");
          }

          // If we have holdings sender open, send them out
          if (hs != null && runs.size() < 1000) {
            //ArrayList<Run> runs = getRuns();
            //if(seedName.indexOf("IMMJA0 HHZ") >= 0)
            //  Util.prt("Doe runs runs="+runs);
            for (int i = 0; i < runs.size(); i++) {
              if (seedName.contains("IMIL06 SHZ") || seedName.contains("IMKS31 BHZ")) {
                Util.prt(seedName + " run[" + i + "]=" + runs.get(i));
              }
              int loop = 1;
              while (hs.getNleft() < 100) {
                Util.sleep(200);
                if (loop++ % 1000 == 0) {
                  Util.prta("Waiting for HoldingSender space nleft=" + hs.getNleft() + " " + seedName + " loop=" + loop);
                }
              }
              try {
                if (!hsNoSend) {
                  hs.send(seedName, runs.get(i).getStart(), runs.get(i).getLength());
                }
                synchronized (holdings) {
                  HoldingSummary h = holdings.get(seedName);
                  if (h == null) {
                    h = new HoldingSummary(seedName, (int) (runs.get(i).getLength() * 1000.));
                    holdings.put(seedName, h);
                  } else {
                    h.add((int) (runs.get(i).getLength() * 1000.));
                  }
                }
              } catch (IOException e) {
                Util.prt("IOException updating holdings!" + e);
                Util.sleep(10000);
                i--;
              }
            }
            // Does this component need a fetchlist
          } else if (runs.size() >= 1000) {
            Util.prta(seedName + "No holdings created runs.size()=" + runs.size() + " HS:=" + hs);
          }
        }
      } catch (RuntimeException e) {
        Util.prt("RuntimeException in AnalyzeIndexFile.run() exiting! e=" + e);
        e.printStackTrace();
        System.exit(2);
      }
      noverlaps += runs.consolidate();
      done = true;
      readingBlock = 2147000000;
      if (seedName.substring(0, 6).equals("CUMTDJ")) {
        Util.prta(seedName + " thread is completed");
        Util.prt(runs.toString());
      }
      //Util.prta(seedName+" thread is completed.");
      //close();
    }

    private int readit(int i, int nb, int off, StringBuilder sblk, String line) {
      boolean dumpExtent = false;
      MiniSeed msin = null;

      try {
        readingBlock = startingBlock[i];
        if (readingBlock >= datout.getLastBlock()) {
          setPriority(2);
          lowPriority = true;
        }
        while (readingBlock >= datout.getLastBlock()) {
          if (terminate) {
            return 0;
          }
          //Thread.yield();
          try {
            sleep(500);
          } catch (InterruptedException e) {
          }
        }
        try {
          sleep(5);
        } catch (InterruptedException e) {
        }   // slow things down a bit
        if (lowPriority) {
          setPriority(5);
          lowPriority = false;
        }
        datout.readBlockFromBuffer(inbuf, startingBlock[i], 32768, seedName);
        int errChanDisagree = 0;
        int errTimeOOR = 0;
        if (zeros.length() > 0) {
          zeros.delete(0, zeros.length());
        }
        //try {sleep(10);} catch(InterruptedException e) {}
        int nzeroblks = 0;
        long zeromap = 0;
        int nbadblocks = 0;
        while (off < nb * 512) {

          if (terminate) {
            return 0;
          }
          System.arraycopy(inbuf, off, extentbuf, 0, 512);
          boolean allzeros = true;
          for (int j = 0; j < 512; j++) {
            if (extentbuf[j] != 0) {
              allzeros = false;
              break;
            }
          }
          if (allzeros) {
            nzeroblks++;
            zeromap |= 1 << (off / 512);
            //sb.append("   "+toString()+"\n");
            //sb.append("      ***** all zeros at extent="+startingBlock[i]+" blk="+off/512+
            //    " "+seedName+"/"+julian+"\n"); 
            //if(readData != 0) sblk.append("      ***** all zeros at extent="+startingBlock[i]+" blk="+off/512+
            //    " "+seedName+"/"+julian+"\n");
            nzeroBlock++;
            off += 512;
          } else if ( (extentbuf[0] < '0' || extentbuf[0] > '9' || extentbuf[1] < '0' || extentbuf[1] > '9'
                  || extentbuf[2] < '0' || extentbuf[2] > '9' || extentbuf[3] < '0' || extentbuf[3] > '9'
                  || extentbuf[4] < '0' || extentbuf[4] > '9' || extentbuf[5] < '0' || extentbuf[5] > '9'
                  || (extentbuf[6] != 'D' && extentbuf[6] != 'R' && extentbuf[6] != 'Q' &&
                  extentbuf[6] != 'M') || extentbuf[7] != ' ')) {
            Util.prt(seedName + "Not miniseed (end of 4k?) off=" + off + " sblock=" + startingBlock[i]/*+" "+MiniSeed.toStringRaw(extentbuf)*/);
            off += 512;
            nbadblocks++;
          } else {
            try {
              if (msin == null) {
                msin = new MiniSeed(extentbuf);  // create first time
              } else {
                msin.load(extentbuf);                      // reuse there after
              }            // Try to add this block to a run, if not create a new one and add it if it looks to be time series
              if (hs != null &&  msin.getNsamp() > 0 && msin.getRate() > 0.00001) {
                //if(msin.getSeedNameString().trim().equals("CUANWB LHE"))
                // Util.prt(" "+msin);
                boolean found = false;
                runs.add(msin);
                if (runs.size() > 900 && runs.size() > lastRunsSize+900) {
                  noverlaps += runs.consolidate();
                  lastRunsSize = runs.size();
                }
                //if (runs.size() >= 1000) {
                  //Util.prta("  ***** " + seedName + " is very hacked up. Do not process more runs>1000=" + runs.size() + "");
                  //sb.append("    ***** ").append(seedName).
                  //        append(" is very hacked up.  Do not process more runs>1000=").append(runs.size()).append("\n");
                  //mail.append("  ***** ").append(seedName).
                  //        append(" is very hacked up. Do not process more runs>1000 size()=").append(runs.size()).append("\n");
                //}
              }
              //Util.prt(seedName+" runs.size()="+runs.size()+" nsamp="+msin.getNsamp()+" rt="+msin.getRate()+" hs="+(hs != null));

              // Is block bigger
              if (msin.getBlockSize() > 512) {
                for (int j = 0; j < 4096; j++) {
                  extentbuf[j] = 0;
                }
                System.arraycopy(inbuf, off, extentbuf, 0, Math.min(32768 - off, msin.getBlockSize()));
                msin = new MiniSeed(extentbuf);
              }
              if ((readData & 2) != 0) {
                sb.append("off=").append(Util.rightPad("" + off, 5)).
                        append(" ").append(msin.toString()).append("\n");
              }
              if (readData != 0) {
                sblk.append("          off=").append(Util.rightPad("" + off, 5)).
                        append(" ").append(msin.toString()).append("\n");
              }
              if (32678 - off > msin.getBlockSize() && readData != 0) {
                try {
                  if (frames == null || frames.length < msin.getBlockSize() - 64) {
                    frames = new byte[msin.getBlockSize() - msin.getDataOffset()];
                  }

                  System.arraycopy(extentbuf, msin.getDataOffset(), frames, 0, msin.getBlockSize() - msin.getDataOffset());
                  int[] samples = null;
                  if (msin.getEncoding() == 11) {
                    samples = Steim2.decode(frames, msin.getNsamp(), msin.isSwapBytes());
                    if (Steim2.hadReverseError()) {
                      Util.prt("   ***** off=" + off + " nb=" + msin.getBlockSize() + " " + Steim2.getReverseError() + msin.getSeedNameString() + "\n");
                      sb.append("   ***** off=").append(off).append(" nb=").
                              append(msin.getBlockSize()).append(" ").append(Steim2.getReverseError()).
                              append(msin.getSeedNameString()).append("\n");
                      sblk.append("   ***** off=").append(off).append(" nb=").
                              append(msin.getBlockSize()).append(" ").append(Steim2.getReverseError()).
                              append(msin.getSeedNameString()).append("\n");
                      dumpExtent = true;
                      steimErrors++;
                    }
                    if (Steim2.hadSampleCountError()) {
                      Util.prt("   ***** off=" + off + " nb=" + msin.getBlockSize() + " " + Steim2.getSampleCountError() + msin.getSeedNameString() + "\n");
                      sb.append("   ***** off=").append(off).append(" nb=").append(msin.getBlockSize()).
                              append(" ").append(Steim2.getSampleCountError()).append(msin.getSeedNameString()).append("\n");
                      sblk.append("   ***** off=").append(off).append(" nb=").append(msin.getBlockSize()).
                              append(" ").append(Steim2.getSampleCountError()).append(msin.getSeedNameString()).append("\n");
                      dumpExtent = true;
                      steimErrors++;
                    }
                  }
                  if (msin.getEncoding() == 10) {
                    samples = Steim1.decode(frames, msin.getNsamp(), msin.isSwapBytes());
                  }

                } catch (SteimException e) {
                  sb.append("   *** steim2 err=").append(e.getMessage());
                  sblk.append("   *** steim2 err=").append(e.getMessage());
                  dumpExtent = true;
                  steimErrors++;
                }
              }
              // Mini-seed channel name must agree
              if (!msin.getSeedNameString().equals(seedName)) {
                errChanDisagree++;
                if (errOnly) {
                  sb.append("   ").append(toString()).append("\n");
                  sb.append(line);
                }
                sb.append("      **** extent=").append(startingBlock[i]).append(" blk=").
                        append(off / 512).append("  ").
                        append(msin.getSeedNameString()).append(" ").append(msin.getTimeString()).
                        append(" ").append(msin.getJulian()).append("\n");
                if (readData != 0) {
                  sblk.append("      **** extent=").append(startingBlock[i]).
                          append(" blk=").append(off / 512).append(" channel is not same=").
                          append(msin.getSeedNameString()).append(" ").append(msin.getTimeString()).
                          append(" ").append(msin.getJulian()).append("\n");
                }
                nchanMisMatch++;
                dumpExtent = true;
              }

              // It must be from the right julian day
              if (msin.getJulian() != julian) {
                if (errOnly) {
                  sb.append("   ").append(toString()).append("\n");
                  sb.append(line);
                }
                sb.append("      **** Julian date does not agree data=").append(msin.getJulian()).
                        append(" vs ").append(julian).append(" iblk=").append(off / 512).append("\n");
                if (readData != 0) {
                  sblk.append("      **** Julian date does not agree data=").
                          append(msin.getJulian()).append(" vs ").append(julian).append(" iblk=").
                          append(off / 512).append("\n");
                }
                njulianMisMatch++;
                dumpExtent = true;
              }

              // check that times are in the range!
              short st = (short) ((msin.getHour() * 3600 + msin.getMinute() * 60 + msin.getSeconds()) / 3);
              short en = (short) (st + msin.getNsamp() / msin.getRate() / 3.);
              if (earliestTime[i] < 31000 && latestTime[i] > -32000) {
                if ((st < earliestTime[i] || st > latestTime[i]) && latestTime[i] != 0) {
                  if (errOnly) {
                    sb.append("   ").append(toString()).append("\n");
                    sb.append(line);
                  }
                  sb.append("      ***** start time of data too out of extent range iblk=").
                          append(off / 512).append(" ").append(Util.df2(msin.getHour())).
                          append(":").append(Util.df2(msin.getMinute())).append(":").
                          append(Util.df2(msin.getSeconds())).append("\n");
                  if (readData != 0) {
                    sblk.append("      ***** start time of data too out of extent range iblk=").
                            append(off / 512).append(" ").append(Util.df2(msin.getHour())).append(":").
                            append(Util.df2(msin.getMinute())).append(":").
                            append(Util.df2(msin.getSeconds())).append("\n");
                  }
                  ndataTimeOOR++;
                  dumpExtent = true;
                }
              }

              // List a line about the record unless we are in errors only mode
              long time = msin.getTimeInMillis();
              long diff;

              // if this is a trigger or other record it will have no samples (but a time!) so
              // do not use it for discontinuity decisions
              if (msin.getNsamp() > 0 && msin.getRate() > 0.) {
                nblks++;
                if (lastDate > 0) {
                  diff = time - lastDate - lastlength;
                  if (Math.abs(diff) > 500. / msin.getRate()) {      // if there is a discontinuity of at least 1/2 sample

                    //Util.prt(seedName+" ** discon dbg diff="+diff+" rate="+msin.getRate()+"llen= "+lastlength+" lasttime="+Util.asctime(lastDate)+" ms="+msin);
                    //mail.append(seedName+" ** discon dbg diff="+diff+" rate="+msin.getRate()+" lastlen="+lastlength+"\n");
                    if (diff > 0) {
                      ndiscons++;
                    } else {
                      negdiscons++;
                    }
                    if ((readData & 4) != 0) {
                      sb.append("=").append(lastString).append("\n");
                      sb.append("   *** discontinuity = ").append(diff).append(" ms! ends at ").
                              append(msin.getTimeString()).append(" blk=").
                              append(startingBlock[i] + off / 512).append("\n");
                      sb.append("/").append(msin.toString()).append(" iblk=").
                              append(startingBlock[i] + off / 512).append("/").
                              append(msin.getBlockSize()).append("\n");
                    }
                    //Util.prt("-"+lastString); 
                    //Util.prt(")"+ms.getSeedNameString()+"   *** discontinuity = "+diff+" ms? ends at "+ms.getTimeString()+
                    //    " blk="+(startingBlock[i]+off/512));
                    //Util.prt("{"+ms.toString()+" iblk="+(startingBlock[i]+off/512)+"/"+ms.getBlockSize());

                  }
                }
                lastlength = (long) (msin.getNsamp() * 1000. / msin.getRate());
                lastLevel = (int) (500 / msin.getRate());
                lastRate = msin.getRate();
                lastDate = time;
                lastString = msin.toString() + " iblk=" + (startingBlock[i] + off / 512) + "/" + msin.getBlockSize();
              }

              off += msin.getBlockSize();
            } catch (IllegalSeednameException e) {
              //Util.prt("toStringDetail() IllegalSeednameException ="+e.getMessage()+
              //    "extent="+startingBlock[i]+" blk="+off/512);
              //for(int j=0; j<32; j++) Util.prt(j+" Dump "+extentbuf[j]+" "+(char) extentbuf[j]);
              sb.append("      **** extent=").append(startingBlock[i]).append(" blk=").append(off / 512).append(" IllegalSeednameException ").append(e.getMessage()).append("\n");
              off += 512;
              dumpExtent = true;
              nseedNameBad++;
            } catch (RuntimeException e) {
              Util.prt("Got runtime during parse in read it e=" + e);
              e.printStackTrace();
            }
          }
        }         // end while(off <65536) 
        off = off - nb * 512;         // calculate how much should be left over!
        lastOff = off;
        if (nzeroblks > 0) {
          sb.append("  ***").append(seedName).append(Util.leftPad("" + nzeroblks, 3)).
                  append(" Zero blks ext=").append(startingBlock[i]).append(" Iblk=").
                  append((Util.leftPad("" + iblk, 6) + "/" + currentExtentIndex + "       ").substring(0, 9)).
                  append(" ").append(timeToString(earliestTime[i])).append(" ").
                  append(timeToString(latestTime[i])).append(" #ext=").append(i).
                  append(" next=").append(nextIndex).append(" ").append(Util.toHex(zeromap)).
                  append(" bmap=").append(Util.toHex(bitMap[i])).append("\n");
        }

      } catch (IOException e) {
        Util.prt("IOException trying to read data from extent "
                + toString() + " blk=" + startingBlock[i]);
      }
      if (dumpExtent && readData != 0 && sblk.toString().length() > 1) {
        sb.append(sblk.toString());
      }
      noverlaps += runs.consolidate();
      return off;
    }

    /**
     * creates a new instance of Index block. Used by writeMiniSeed() when the index has not yet
     * been created on an Edge. This call requires the IndexFile for this julian day to have been
     * created and be open. If not an I/O error is likely.
     *
     * If the block already exists, it is read and the last extent determined. This extend is read
     * and the bit map made to agree with the number of blocks used in the extent (Uses non-zero
     * filled to allow membership). (Possible TODO: make checking of blocks read more stringent-
     * seedname match, etc.).
     *
     * @param ifl The associated AnalyzeInedFile object
     * @param sdname The 12 character seedName (must pass MasterBlock.checkSeedName()
     * @param jul The julian day to open for this component
     * @param first If true, return the first index block in the chain for the record if not, return
     * the last index block in chain ready for writing!
     * @param err The error only reporting flag
     * @param indDet If true, due detail reporting on index
     * @param read If true, a read data pass is also done
     * @param blkMap THis is bit map used to insure all blocks are accounted for in entire file
     * @param indexBlkMap Used to mark off all of the index blocks that were used
     * @throws IllegalSeednameException If name does not pass MasterBlock.checkSeedName()
     * @throws IOException If this block cannot be created.
     * @throws EdgeFileCannotAllocateException if this block cannot be allocated
     * @throws EdgeFileReadOnlyException Cannot create writable IndexBlock on readonly filez
     * @throws DuplicateIndexBlockCreatedException If first is set and this seedname index block has
     * already been created
     */
    public IndexBlockAIF(AnalyzeIndexFile ifl, String sdname, int jul, boolean first,
            boolean err, boolean indDet, int read, BitSet blkMap, BitSet indexBlkMap)
            throws IllegalSeednameException, IOException,
            EdgeFileReadOnlyException, DuplicateIndexBlockCreatedException {
      //if(indexBlocks == null) indexBlocks = Collections.synchronizedMap(new TreeMap());
      bb = ByteBuffer.wrap(buf);
      blockMap = blkMap;
      indexBlockMap = indexBlkMap;
      indexDetail = indDet;
      errOnly = err;
      readData = read;
      // These three are used across getNextIndex() calls so must be initialized when created
      lastDate = 0;
      lastlength = 0;
      lastString = "First Block";
      lastLevel = 25;

      // set the internal data
      julian = jul;
      seedName = sdname;
      band = sdname.substring(7, 9);

      // See if there is already an run list, if so it was created in some other fill and we just need to add to it
      runs = chanruns.get(sdname);
      if (runs == null) {
        runs = new RunList(1000, true);
        chanruns.put(sdname, runs);
      }
      chanruns.put(sdname, runs);         // Put this on global run list
      key = new IndexKey(sdname, julian);
      MasterBlock.checkSeedName(sdname);

      // Link up to file channels via the IndexFile object
      indexFile = ifl;
      rw = indexFile.getRawDisk();
      datout = indexFile.getDataRawDisk();

      // Allocate space for arrays in this object
      startingBlock = new int[MAX_EXTENTS];
      bitMap = new long[MAX_EXTENTS];
      earliestTime = new short[MAX_EXTENTS];
      latestTime = new short[MAX_EXTENTS];

      // If this is a "read type" or "first" get the first block
      if (first) {
        iblk = indexFile.getFirstIndexBlock(sdname);
      } else {          // Get the last block, suitable for writing
        iblk = indexFile.getLastIndexBlock(sdname);
        /*if(indexBlocks.get(key) != null) 
        throw new DuplicateIndexBlockCreatedException("Seedname:"+sdname+" jul="+jul);
      synchronized(indexBlocksLock) {
        indexBlocks.put(key, this);
      }*/
      }

      // If iblk < 1, this IndexBlock was not found, create it
      if (iblk < 0) {
        Util.prt("   **** Index block < 0 in AnalyzeIndexFile.IndexBlock getFirst/LastIndexBlock " + sdname + " first=" + first + " iblk=" + iblk);
      } else {        // The block was found, read it and  populate data
        rw.readBlock(buf, iblk, 512);
        indexBlockMap.set(iblk);
        fromBuf();
        updateTime = System.currentTimeMillis();

        buildLastExtent();
      }
      //Util.prt(seedName+" create IndexBlock hs="+(hs != null)+" readData="+readData);
    }

    /**
     * The latest extent is pointed to by currentExtentIndex but since it might be lazily written we
     * need to read in the extent and find the zero filled one to set the last block and recreate
     * the bit mask
     */
    private void buildLastExtent() throws IOException, IllegalSeednameException {
      //Util.prt("build last exent weird curr="+currentExtentIndex+" value="+startingBlock[currentExtentIndex]+" seedname="+seedName);
      if (startingBlock[currentExtentIndex] < 0) {
        return;
      }
      long mask = 1;
      byte[] data = datout.readBlockFromRawDisk(startingBlock[currentExtentIndex], 64 * 512); // read the whole extent
      byte[] bf = new byte[512];
      //  Util.prta(seedName+" Rebuildindex/extent="+iblk+"/"+currentExtentIndex+" Start  earliest="+
      //      timeToString(earliestTime[currentExtentIndex])+" latest="+timeToString(latestTime[currentExtentIndex]));
      for (nextBlock = 0; nextBlock < 64; nextBlock++) {
        System.arraycopy(data, nextBlock * 512, bf, 0, 64);
        try {
          if (Util.isValidSeedName(MiniSeed.crackSeedname(bf))) {

            int[] tm = MiniSeed.crackTime(bf);
            short time = (short) ((tm[0] * 3600 + tm[1] * 60 + tm[2]) / 3);
            short endtime = ((short) (MiniSeed.crackNsamp(bf) / MiniSeed.crackRate(bf) / 3.));
            endtime += time;
            if (time < earliestTime[currentExtentIndex]) {
              earliestTime[currentExtentIndex] = time;
            }
            if (endtime > latestTime[currentExtentIndex]) {
              latestTime[currentExtentIndex] = endtime;
            }
          }
        } catch (IllegalSeednameException e) {
          Util.prt("buildLastExtent got illegal cracking a buffer! next=" + nextBlock + " " + toString());
          e.printStackTrace();
          throw e;
        }

        if (data[nextBlock * 512] == 0) {
          break;
        }
        bitMap[currentExtentIndex] = bitMap[currentExtentIndex] | mask;      // Or in this bit, block is in use
        mask = mask << 1;
      }
      //  Util.prta(seedName+" Rebuild index/extent="+iblk+"/"+currentExtentIndex+" nextblk="+nextBlock+" earliest="+
      //      timeToString(earliestTime[currentExtentIndex])+" latest="+timeToString(latestTime[currentExtentIndex]));

    }

    /**
     * convert a earliest or lattest time to a string.
     *
     * @param time The time encoded as 3s of seconds since midnight
     * @return The formated string
     */
    public String timeToString(int time) {
      int secs = time * 3;
      int hr = secs / 3600;
      secs = secs % 3600;
      int min = secs / 60;
      secs = secs % 60;
      return Util.df2(hr) + ":" + Util.df2(min) + ":" + Util.df2(secs);

    }

    /**
     * recover the data from a binary buffer (like after its read in!)
     */
    private void fromBuf() {
      bb.clear();
      byte[] namebuf = new byte[12];
      bb.get(namebuf);
      seedName = new String(namebuf);
      band = seedName.substring(7, 9);
      nextIndex = bb.getInt();
      updateTime = bb.getInt() * 1000;
      currentExtentIndex = -1;
      for (int i = 0; i < MAX_EXTENTS; i++) {
        startingBlock[i] = bb.getInt();
        if (startingBlock[i] >= 0) {
          currentExtentIndex = i;
        }
        bitMap[i] = bb.getLong();
        earliestTime[i] = bb.getShort();
        latestTime[i] = bb.getShort();
      }
    }

    /**
     * Return the next block in the extent to use
     *
     * @return The next block to use in the current extent
     */
    public int getNextBlock() {
      return nextBlock;
    }

    /**
     * Return the current extents block.
     *
     * @return The current extents starting block
     */
    public int getCurrentExtent() {
      return startingBlock[currentExtentIndex];
    }

    /**
     * Get the number of extents referred to by this IndexBlock.
     *
     * @return the number of extents
     */
    public int getNumExtents() {
      int i;

      for (i = 0; i < MAX_EXTENTS; i++) {
        if (startingBlock[i] == -1) {
          break;
        }
      }

      return i;
    }

    /**
     * Get the bitmap of the extent with index {@code i}.
     *
     * @param i the index
     * @return a long bitmap indicating which blocks are used in the extent
     */
    public long getBitMap(int i) {
      return bitMap[i];
    }

    /**
     * Get the earliest time of the extent with index {@code i}.
     *
     * @param i the index
     * @return the earliest time represented by the extent
     */
    public int getEarliestTime(int i) {
      return earliestTime[i];
    }

    /**
     * Get the latest time of the extent with index {@code i}.
     *
     * @param i the index
     * @return the latest time represented by the extent
     */
    public int getLatestTime(int i) {
      return latestTime[i];
    }

    /**
     * Advance this index block to the next one in the chain, return true if it change false if this
     * is the last block of the chain
     *
     * @return True if a new block is positioned, false if it is already last block in chain
     * @throws IOException if the nextIndexBLock cannot be read
     */
    public synchronized boolean nextIndexBlock() throws IOException {
      if (nextIndex <= 0) {
        return false;
      }
      iblk = nextIndex;
      if (iblk <= 0) {
        return false;
      }
      rw.readBlock(buf, iblk, 512);
      indexBlockMap.set(iblk);      // mark off the index block
      fromBuf();
      return true;
    }

    /**
     * return the index key of this block
     *
     * @return The index key
     */
    public IndexKey getKey() {
      return key;
    }

    /**
     * return the array of starting blocks for the extents allocated to this index block
     *
     * @return Integer array of block numbers of the starts of 64 block extents allocated
     */
    public int[] getExtents() {
      return startingBlock;
    }

    /**
     * return the array with bit maps representing usage of the extents
     *
     * @return array of bit maps, lowest number block is represented with 1 bit
     */
    public long[] getBitMaps() {
      return bitMap;
    }

    /**
     * return the 12 character seed channel name
     *
     * @return the 12 character seed channel name
     */
    public String getSeedName() {
      return seedName;
    }

    /**
     * get the julian day of this file
     *
     * @return the julian day of this file (since long ago)
     */
    public int getJulian() {
      return julian;
    }

    /**
     * get the key used for comparing these thing
     *
     * @return the IndexKey for this indexblock
     */
    public IndexKey getIndexKey() {
      return key;
    }

    /**
     * return the update time of this block
     *
     * @return The update time a Date().getTime()
     */
    public long getUpdateTime() {
      return updateTime;
    }

    /**
     * return the next index block number (-1 means there is none)
     *
     * @return the next index block number in this seed channels chain
     */
    public int getNextIndex() {
      return nextIndex;
    }

    /**
     * compares two index blocks for ordering
     *
     * @param o The other object to compare to
     * @return -1 if this is < param, 0 if equal, 1 if this > param
     */
    @Override
    public int compareTo(IndexBlockAIF idx) {
      //IndexBlockAIF idx = (IndexBlockAIF) o;
      int idiff = seedName.compareTo(idx.getSeedName());
      if (idiff != 0) {
        return idiff;
      }

      //Break the tie with julian dates
      if (julian < idx.getJulian()) {
        return -1;
      } else if (julian > idx.getJulian()) {
        return 1;
      }
      return 0;               // They are equal in every way
    }

  }

  public class Station {

    int[] nblks;
    int[] doys;
    int ndays;
    String station;

    public Station(String name) {
      nblks = new int[366];
      doys = new int[366];
      station = name;
    }

    public void addNblk(int n, int doy) {
      for (int i = 0; i < ndays; i++) {
        if (doys[i] == doy) {
          nblks[i] += n;
          return;
        }
      }
      doys[ndays] = doy;
      nblks[ndays] += n;
      ndays++;
    }

    @Override
    public String toString() {
      StringBuilder sb = new StringBuilder(1000);
      sb.append((station + "         ").substring(0, 8));
      for (int i = 0; i < ndays; i++) {
        sb.append(Util.leftPad("" + nblks[i], 7)).append("(").
                append(Util.leftPad("" + (nblks[i] * 4096 / 86400), 5)).append(") ");
      }
      return sb.toString();
    }
  }

  class AIFShutdown extends Thread {

    public AIFShutdown() {

    }

    @Override
    public void run() {
      terminate = true;
      Util.prta("AIFShutdown");
    }
  }

  public static void main(String[] args) {
    Util.setModeGMT();
    EdgeProperties.init();
    //for(int i=0; i<args.length; i++) {
    AnalyzeIndexFile aif = new AnalyzeIndexFile("/Users/data/2006_204_4.idx", null, true);
    Util.prt(aif.analyze(true, 0, false, "", false).toString());
    //}
  }
}
