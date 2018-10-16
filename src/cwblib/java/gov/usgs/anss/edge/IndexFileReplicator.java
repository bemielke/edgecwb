/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.edge;

import gov.usgs.alarm.SendEvent;
import gov.usgs.anss.seed.MiniSeed;
import gov.usgs.anss.util.FreeSpace;
import gov.usgs.anss.util.SeedUtil;
import gov.usgs.anss.util.Util;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import gnu.trove.map.hash.TLongObjectHashMap;
import gnu.trove.iterator.TLongObjectIterator;

/**
 * This class is responsible for all file writes for a replicated file. This includes the .ms (data
 * replication), .idx(index replication), and .chk(index check file). It has static functions which
 * can be used to access and manipulate the list of all objects of this type (files is a list of all
 * open IndexFileReplicators).
 *
 * The object portions are used to open a set of files for replication, managing the list of check
 * blocks, writing data to the index and data replications (writeIndexBlock() and writeDataBlock()).
 * The class tracks last time the file was used so that stale or inactive files can be detected (see
 * closeOld()).
 *
 * When a new one is created the algorithm for deciding where to put the file is different than for
 * an "Edge" computer. This is the algorithm (only step 1 for readonly files) :
 * <p>
 * 1) The paths are scanned for the file and if it is found it is opened.
 * <p>
 * 2) The paths are scanned for a file labeled empty_node.ms where node is the edge node that needs
 * to be opened. If such a file is found it is renamed to the usual julian_node.ms and opened.
 * <p>
 * 3) The Paths are check for how much free space is available on each one. The path with the
 * largest amount of free space has the file opened on it.
 * <p>
 *
 * The data file is Zeroed by a Thread started when a new IFR is created for writing. This Thread
 * attempts to keep 2000 block ahead of the "high water" block written to the file. Because a new
 * block might come in way above the zero's progress, the writeDataBlock() method will queue the
 * data block to be written by the Zeroer after the zeroer has passed the block. In this way all
 * writeDataBlock() calls return immediately.
 *
 * The "check" blocks are images of the corresponding index blocks but its bit map is used to
 * indicate that the block has been confirmed as written into the data file. Differences in the bit
 * maps between the index and check files indicate blocks which might not have been received and
 * written in the data file. Each data block written sets the corresponding bit map for the index
 * block, extent index and bit within the extent.
 *
 * @author davidketchum
 */
public final class IndexFileReplicator implements Comparable {

  private final static TLongObjectHashMap<IndexFileReplicator> files = new TLongObjectHashMap<>();  // used in synch blocks
  private static String whoHas;     // for debuggin mutex problems

  private static int recordNumbers;
  private static int stateLock;
  private static final byte[] scratch = new byte[512];
  private static ByteBuffer bbscratch;

  private static boolean shuttingDown; // set true in shutdown routine - cause different actions in closeer
  private static boolean dbg;
  private static boolean initdone;

  private int recordNumber;
  private int extendSize;       // This will be a multiple of DEFAULT_EXTEND_SIZE so a extends occurs at least 10 minute apart
  private long lastExtendTime;  // Track the time of the last extend of the file
  private long lastGetMasterBlocks; // insure this operation does not happen very frequently

  private String node;         // The edge node which created the original file
  private StringBuilder nodesb = new StringBuilder(4);
  private String filename;
  private final String maskStub;
  private String instance;
  //private byte [] seedbuf;    // 12 bytes for building/converting seednames
  private boolean readOnly;   // If true, allow only read access to file
  private RawDisk rw;         // unit of the .idx file
  private RawDisk dataFile;   // Access to the data file .ms
  private RawDisk checkFile;  // Access to the check file .chk
  private final TLongObjectHashMap<IndexBlockCheck> indices = new TLongObjectHashMap<>();  // List of indices in the check file(one per block in .chk)    
  private final int julian;         // The julian day represented by this file
  private long lastUsed;      // Updated with System.getCurrentMillis() each time this object is used
  private ShutdownIndexFile shutdown;
  private Object indexChecker;
  private int maxIndex;       // DEBUG:
  // status monitoring
  int ctlUpdates;
  int indexUpdates;
  int masterUpdates;
  int dataUpdates;

  // These form the ctl block portion of the file
  private int length;                 // length of the controlled data file in blocks
  private int highwater;

  private ZeroDataFile zeroer;        // all writable files have a zeroer running in front of the highwater blk.
  private CloseThread closer;         // close() starts one of these to get the file closed.
  private boolean terminate = false;
  private StringBuilder tmpsb = new StringBuilder(50);  // Mostly for returning status, cons and synched logging

  public static int getStateLock() {
    return stateLock;
  }

  public boolean isClosing() {
    return (closer != null);
  }

  /**
   * The has upper bits is the julian and the lower order bytes are the node number
   *
   * @param node Up to 4 character node/instance
   * @param julian The julian date
   * @return This hashed together.
   */
  public static long getHash(String node, int julian) {
    long hash = julian;
    for (int i = 0; i < 4; i++) {
      if (node.length() <= i) {
        hash = hash * 256 + 32;
      } else {
        hash = hash * 256 + node.charAt(i);
      }
    }
    return hash;
  }

  /**
   * The has upper bits is the julian and the lower order bytes are the node number
   *
   * @param node Up to 4 character node/instance
   * @param julian The julian date
   * @return This hashed together.
   */
  public static long getHash(StringBuilder node, int julian) {
    long hash = julian;
    for (int i = 0; i < 4; i++) {
      if (node.length() <= i) {
        hash = hash * 256 + 32;
      } else {
        hash = hash * 256 + node.charAt(i);
      }
    }
    return hash;
  }

  /**
   * this will wait for a closing file to be closed. If it does not happen in 10 seconds the return
   * will be false.
   *
   * @return True if file is not closed and off the list of files.
   */
  public boolean waitForClose() {
    if (closer == null) {
      throw new RuntimeException("Calling waitForClose while file is not closing! " + toString());
    }
    //String key  = node.trim()+"_"+julian;
    int loop = 0;
    for (;;) {
      try {
        while (files.get(getHash(node, julian)) != null) {
          try {
            Thread.sleep(100);
          } catch (InterruptedException e) {
          }
          loop++;
          if (loop > 100) {
            break;
          }
        }
        return loop < 100;
      } catch (RuntimeException e) {
        e.printStackTrace();
      }
    }
  }

  public void setMaxIndex(int i) {
    maxIndex = i;
  }

  public void registerIndexChecker(Object chk) {
    indexChecker = chk;
  }

  public Object getIndexchecker() {
    return indexChecker;
  }

  public void updateLastUsed() {
    lastUsed = System.currentTimeMillis();
  }
  private static long dataMonUpdates;
  private static long indexMonUpdates;
  private static long masterMonUpdates;
  private static long ctlMonUpdates;

  public static String getMonitorString() {
    long ndt = 0;
    long nit = 0;
    long nmt = 0;
    long nct = 0;
    long nibc = 0;
    synchronized (files) {
      TLongObjectIterator<IndexFileReplicator> itr = files.iterator();

      while (itr.hasNext()) {
        itr.advance();
        IndexFileReplicator ifr = itr.value();
        ndt += ifr.dataUpdates;
        nit += ifr.indexUpdates;
        nmt += ifr.masterUpdates;
        nct += ifr.ctlUpdates;
        nibc += ifr.indices.size();
      }
    }
    long nd = ndt - dataMonUpdates;
    long ni = nit - indexMonUpdates;
    long nm = nmt - masterMonUpdates;
    long nc = nct - ctlMonUpdates;
    dataMonUpdates = ndt;
    indexMonUpdates = nit;
    masterMonUpdates = nmt;
    ctlMonUpdates = nct;

    return "IndexNFileRep=" + files.size() + "\nTotalIndexBlockCheck=" + nibc + "\n"
            + "IndexNBlockDataRep=" + nd + "\nIndexNBlockIndexRep=" + ni + "\nIndexNBlockMasterRep=" + nm + "\nIndexNBlockCtrlRep=" + nc + "\n";
  }

  public String getStatusString() {
    return filename + " data=" + dataUpdates + " mast=" + masterUpdates + " index="
            + indexUpdates + " ctl=" + ctlUpdates + " #idx=" + indices.size();
  }

  public int getMaxIndex() {
    return maxIndex;
  }

  /**
   * return time this file was last used to read or write anything
   *
   * @return The System.currentTimeMillis() of the last IO
   */
  public long getLastUsed() {
    return lastUsed;
  }

  /**
   * set debug level
   *
   * @param t The new debut state
   */
  public static void setDebug(boolean t) {
    dbg = t;
  }

  /**
   * set who has the file list mutex
   *
   * @param s a string identifying the holder of the mutex
   */
  public static void setFL(String s) {
    whoHas = s;
  }

  /**
   * return instance from the filename
   *
   * @return the instance
   */
  public final String getInstance() {
    if (instance == null) {
      int lastUnderLine = filename.lastIndexOf("_");
      instance = filename.substring(lastUnderLine + 1);
      if (instance.length() != 4) {
        instance = (instance + "    ").substring(0, 4);
      }
    }
    return instance;
  }

  /**
   * get the rawdisk variable for using the index file
   *
   * @return A RawDisk object for I/O to this index file
   */
  public RawDisk getRawDisk() {
    return rw;
  }

  /**
   * get raw disk object for data file
   *
   * @return A RawDisk object for I/O to the data file associated with this index file
   */
  public RawDisk getDataRawDisk() {
    return dataFile;
  }

  /**
   * get raw disk object for index check file
   *
   * @return A RawDisk object for I/O to the data file associated with this index file
   */
  public RawDisk getCheckRawDisk() {
    return checkFile;
  }

  /**
   * is this file readonly
   *
   * @return true if read only
   */
  public boolean isReadOnly() {
    return readOnly;
  }

  /**
   * a string representing this index file
   *
   * @return the filename/julian and ro=True/False
   */
  @Override
  public final String toString() {
    return toStringBuilder(null).toString();
  }

  public final StringBuilder toStringBuilder(StringBuilder tmp) {
    StringBuilder sb = tmp;
    if (sb == null) {
      sb = Util.clear(tmpsb);
    }
    synchronized (sb) {
      sb.append(filename).append(Util.fs).append(julian).append(" ro=").append(readOnly).
              append(" rec#=").append(recordNumber).append(" #idx=").append(indices.size()).append(" closing=").append(isClosing()).
              append(" nMBskip/load=").append(nMBSkip).append("/").append(nMBLoad).
              append(" lastused=").append((System.currentTimeMillis() - lastUsed) / 1000).
              append("s rw=").append(rw);
    }
    return sb;
  }

  /**
   * a string representing this index file
   *
   * @return the filename/julian and ro=True/False
   */
  public String toStringFull() {
    return toStringBuilderFull(null).toString();
  }

  public StringBuilder toStringBuilderFull(StringBuilder tmp) {
    StringBuilder sb = tmp;
    if (sb == null) {
      sb = Util.clear(tmpsb);
    }
    if (filename == null) {
      Util.prt("IFR: TSfull null file");
    }
    if (rw == null) {
      Util.prt("IFR: rw is null");
    }
    if (dataFile == null) {
      Util.prt("IFR: datafile is null");
    }
    synchronized (sb) {
      if (rw == null || dataFile == null) {
        return sb.append(filename).append(Util.fs).append(julian).append(" ro=").append(readOnly).append(" #idx=").append(indices.size()).
                append(" last used=").append((System.currentTimeMillis() - lastUsed) / 1000).append(" is closed!");
      }
      sb.append(filename).append("/").append(julian).append(" ro=").append(readOnly).append(" #idx=").append(indices.size()).
              append(" last used=").append((System.currentTimeMillis() - lastUsed) / 1000).
              append(" sec idx: #rd=").append(rw.getNread()).append(" wr=").append(rw.getNwrite()).
              append(" data: #rd=").append(dataFile.getNread()).append(" #wr=").
              append(dataFile.getNwrite()).append(" rec#=").append(recordNumber);
      if (zeroer == null) {
        sb.append(" #zwr=").append(zeroer.getNwriteBehind()).
                append(" isclosing=").append(isClosing()).append(" ro=").append(readOnly);
      }
    }
    return sb;
  }

  /**
   * return the ticks counter in the zeroer. This was used in unit test to slow down the main, but
   * may not have another use.
   *
   * @return the number of ticks counted by the zeroer (50 milli each).
   */
  public long getTick() {
    return zeroer.getTick();
  }

  /**
   * return the full julian day for this file
   *
   * @return the julian day since long ago
   */
  public int getJulian() {
    return julian;
  }

  /**
   * return the edge node string for this file
   *
   * @return The edge node for this file
   */
  public String getNode() {
    return node;
  }

  /**
   * return a check block if there is one for the given block
   *
   * @param blk The block number of the desired check block
   * @return a null if the check block is not in the active list
   */
  public IndexBlockCheck getIndexBlockCheck(int blk) {
    synchronized (indices) {
      return (IndexBlockCheck) indices.get(blk);
    }
  }

  /**
   * Creates a new instance of IndexFile managed as a continuous wave form buffer ( (that is it has
   * a number of roots managed for space but not for fixed days)
   *
   * @param jul The julian date of the desired file
   * @param node The node number of the desired file
   * @param hiwater The lowest block number if file its o.k. to zero!
   * @param init if true this is a new file
   * @param ro If true, this Index file can only read (disables all allocation functions)
   * @throws FileNotFoundException if init is false and file is not found!
   * @throws EdgeFileReadOnlyException If init is true and file is readonly, non-sense!
   * @throws IOException Generally related to reads and writes failing
   * @throws EdgeFileDuplicateCreationException if this file has already been opened!
   */
  public IndexFileReplicator(int jul, String node, int hiwater, boolean init, boolean ro)
          throws FileNotFoundException, EdgeFileReadOnlyException, IOException, EdgeFileDuplicateCreationException {
    synchronized (files) {
      int[] ymd;
      julian = jul;
      if (files.get(getHash(node, julian)) != null) {
        throw new EdgeFileDuplicateCreationException("Duplicate file for julian=" + SeedUtil.fileStub(julian));
      }
      highwater = hiwater;

      // protect ourselves from obviously illegal julan days and name
      if (julian < 2000000 || julian > 2600000) {
        filename = "Illegal_" + julian + "_" + node.trim();
        maskStub = "";
      } else {
        ymd = SeedUtil.fromJulian(julian);
        int doy = SeedUtil.doy_from_ymd(ymd[0], ymd[1], ymd[2]);
        filename = SeedUtil.fileStub(ymd[0], doy) + "_" + node.trim();
        instance = (node + "    ").substring(0, 4);
        maskStub = IndexFile.makeMaskStub(ymd[0], doy, IndexFile.DATA_MASK);
      }

      //if(dbg) 
      Util.prta(Util.clear(tmpsb).append("IndexFileRep:  cons open non-edge ").append(filename).
              append("/").append(julian).append(" hi=").append(hiwater).append(" init=").append(init).
              append(" ro=").append(ro).append(" node=").append(node));
      //new RuntimeException("IFR: opening file "+filename+"/"+julian+" init="+init+" nd="+node).printStackTrace();
      if (shuttingDown) {
        Util.prt("Shutting down - no new IndexFileReps");
        return;
      }
      openIndexFileNonEdge(filename, node.trim(), init, ro);
      if (dbg) {
        Util.clear(tmpsb).append("IndexFileRep: Cons set highwater nd=");
        toStringBuilder(tmpsb).append(" to ").append(highwater).append(" rec#=").append(recordNumber);
        Util.prta(tmpsb);
      }
    }
  }

  /**
   * Creates a new instance of IndexFile if the file name is known on an continuous waveform type
   * computer (that is disk roots managed for space and not # of days). The filename will be parsed
   * to create a julian and any extension will be stripped off before calling
   * openIndexFileNonEdge(). Fixed number of days per path root.
   *
   * @param file A absolute path to the index file to open
   * @param node The node number of the file (this should also be in the file name!)
   * @param hiwater The lowest block number if file its o.k. to zero!
   * @param init if true this is a new file
   * @param ro If true, this Index file can only read (disables all allocation functions)
   * @throws FileNotFoundException if init is false and file is not found!
   * @throws EdgeFileReadOnlyException If init is true and file is readonly, non-sense!
   * @throws IOException Generally related to reads and writes failing
   * @throws EdgeFileDuplicateCreationException if such is detected (should now be impossible!)
   */
  public IndexFileReplicator(String file, String node, int hiwater, boolean init, boolean ro)
          throws FileNotFoundException, EdgeFileReadOnlyException, IOException, EdgeFileDuplicateCreationException {
    synchronized (files) {
      filename = file;
      highwater = hiwater;
      readOnly = ro;
      int lastSlash = filename.lastIndexOf("/");
      if (lastSlash < 0) {
        lastSlash = -1;
      }
      if (dbg) {
        Util.prt(Util.clear(tmpsb).append("IndexFileRep: open=").append(filename).
                append(" yr=").append(filename.substring(lastSlash + 1, lastSlash + 5)).
                append(" doy=").append(filename.substring(lastSlash + 6, lastSlash + 9)).
                append(" ro=").append(ro).append(" init=").append(init));
      }
      int year = Integer.parseInt(filename.substring(lastSlash + 1, lastSlash + 5));
      int doy = Integer.parseInt(filename.substring(lastSlash + 6, lastSlash + 9));
      julian = SeedUtil.toJulian(year, doy);
      maskStub = IndexFile.makeMaskStub(year, doy, IndexFile.DATA_MASK);
      if (files.get(getHash(node, julian)) != null) {
        throw new EdgeFileDuplicateCreationException("Duplicate file for julian=" + SeedUtil.fileStub(julian));
      }
      if (lastSlash >= 0) {
        filename = filename.substring(lastSlash + 1);
      }
      int period = filename.indexOf(".");
      if (period >= 0) {
        filename = filename.substring(0, period);
      }
      getInstance();
      openIndexFileNonEdge(filename, node.trim(), init, ro);
      if (dbg) {
        Util.prta(Util.clear(tmpsb).append("IndexFileRep:  cons open non-edge2 ").
                append(filename).append("/").append(julian).append(" hi=").append(hiwater).
                append(" init=").append(init).append(" ro=").append(ro).append(" rec#=").append(recordNumber));
      }
      if (dbg) {
        Util.prta(Util.clear(tmpsb).append("IndexFileRep: set highwater nd=").
                append(toString()).append(" to ").append(highwater));
      }
    }
  }

  /**
   * check to see if a file exists on the paths for data. If so get its full path name
   *
   * @param name The filename portion like 2008_047_7.ms
   * @return null if it does not exist, else the filename string for the whole path is returned
   */
  public static String fileExists(String name) {
    if (!initdone) {
      init();
    }
    for (int i = 0; i < IndexFile.NPATHS; i++) {
      String path = IndexFile.DATA_PATH[i];
      String filename = path.trim() + name.trim();

      File f = new File(filename);
      if (f.exists()) {
        return filename;
      }
    }
    return null;
  }

  /**
   * check to see if a file exists on the paths for data. If so get its full path name
   *
   * @param limit The number of days back to purge to
   * @return number of bytes freed by this delete.
   */
  public static long deleteJulianLimit(int limit) {
    if (!initdone) {
      init();
    }
    int today = SeedUtil.toJulian(System.currentTimeMillis());
    long saved = 0;
    for (int j = 0; j < IndexFile.NPATHS; j++) {
      String path = IndexFile.DATA_PATH[j];
      File dir = new File(path);
      if (dir.isDirectory()) {
        String[] allFiles = dir.list();
        for (String allFile : allFiles) {
          if (allFile.length() < 9) {
            continue;
          }
          if (Character.isDigit(allFile.charAt(0)) && Character.isDigit(allFile.charAt(1))
                  && Character.isDigit(allFile.charAt(2)) && Character.isDigit(allFile.charAt(3))
                  && Character.isDigit(allFile.charAt(5)) && Character.isDigit(allFile.charAt(6))
                  && Character.isDigit(allFile.charAt(7)) && allFile.charAt(4) == '_' && allFile.charAt(8) == '_') {
            int jul = SeedUtil.toJulian(Integer.parseInt(allFile.substring(0, 4)), Integer.parseInt(allFile.substring(5, 8)));
            if (today - jul > limit) {
              File f = new File(path + allFile);
              Util.prta("Delete Julian Limit=" + limit + " " + f.toString());
              saved += f.length();
              f.delete();
            }
          }
        }
      }
    }
    return saved;
  }

  /**
   * This is only called from constructors so it does not need to be synchronized on (this) It uses
   * continuous waveform buffer assumptions.
   *
   * @param name The file name to use
   * @param nd The node (the edge node) of the file
   * @param init If true, file is created as an empty file
   * @param ro If true, file is read only.
   * @throws FileNotFoundException If so generated.
   * @throws EdgeFileReadOnlyException If read only file does not exist.
   * @throws IOException If one is found
   */
  private void openIndexFileNonEdge(String name, String nd, boolean init, boolean ro)
          throws FileNotFoundException, EdgeFileReadOnlyException, IOException {
    //Util.prta("IFR: calling init! state="+stateLock);
    if (!initdone) {
      init();
    }
    //Util.prta("IFR: done with init! state="+stateLock);
    recordNumber = recordNumbers++;
    extendSize = IndexFile.DEFAULT_EXTEND_BLOCKS;
    lastExtendTime = System.currentTimeMillis();
    node = nd.trim();
    Util.clear(nodesb);
    for (int i = 0; i < 4; i++) {
      if (i < node.length()) {
        nodesb.append(node.charAt(i));
      } else {
        nodesb.append(' ');
      }
    }
    Util.prta(Util.clear(tmpsb).append("IFR: open ").append(name).append(" nd=").append(node).
            append(" init=").append(init).append(" ro=").append(ro).append(" #rec=").append(recordNumber).
            append(" files.size=").append(files.size()).append(" NPATH=").append(IndexFile.NPATHS));
    //seedbuf = new byte[10];

    // Set up the variables
    lastUsed = System.currentTimeMillis();
    readOnly = ro;
    int ipath = -1;
    boolean done = false;
    boolean exists = false;
    String emptyFile = "";
    int iempty = -1;
    String fileFound = null;
    // look through the paths for this file or an empty.
    for (int i = 0; i < IndexFile.NPATHS; i++) {
      String path = IndexFile.DATA_PATH[i];
      File dir = new File(path.substring(0, path.length() - 1));
      if (dbg) {
        Util.prt(Util.clear(tmpsb).append("IFR: path=").append(path).append(" dir=").
                append(dir.toString()).append(" isDir=").append(dir.isDirectory()));
      }
      String[] filess = dir.list();
      if (filess == null) {
        SendEvent.debugEvent("IFRFileOpen", "Failed to open files on path=" + path, "IndexFileReplicator");
        Util.prta(Util.clear(tmpsb).append("Attempt to open files on path=").append(path).
                append(" dir=").append(dir.toString()).append(" failed.  Is VDL denied access to path? Too many Files open?"));
      } else {
        for (String files1 : filess) {
          // Are we using the DATA_MASK, if so check for directories that match
          if (files1.equals(maskStub)) {   // yes, we found the directory
            File dir2 = new File(path + maskStub);
            String[] filess2 = dir2.list();
            for (String files2 : filess2) {
              if (files2.contains(filename + ".ms")) {
                ipath = i;
                exists = true;
                fileFound = IndexFile.DATA_PATH[ipath] + maskStub + Util.FS + name;
                Util.prta(Util.clear(tmpsb).append("IFR: found maskStub ").append(name).
                        append(" on ").append(IndexFile.DATA_PATH[ipath]).append(maskStub).append("/"));
                break;
              }
            }
          } else if (files1.indexOf(".ms") > 0) {
            if (dbg) {
              Util.prt(Util.clear(tmpsb).append(files1).append(" to ").append(filename));
            }
            if (files1.contains(filename + ".ms")) {
              ipath = i;
              exists = true;
              fileFound = IndexFile.DATA_PATH[ipath] + name;
              break;
            }
            if (files1.contains("empty_" + node)) {
              // found an empty one
              iempty = i;
              emptyFile = files1;
              break;
            }
          }
        }
      }
    }

    // If it does not exist, then use an empty or create one
    if (!exists) {
      if (readOnly) {
        Util.prta("** does not exist and readonly. npaths="+IndexFile.NPATHS+" "+IndexFile.DATA_PATH[0]+ 
                (IndexFile.NPATHS > 1?" "+IndexFile.DATA_PATH[1]:""));
        throw new FileNotFoundException("IndexFileRep: file=" + filename + " does not exist and open is readonly");
      }
      if (iempty != -1) {        // Empty found, rename the files, create the index
        File newname = new File(name + ".ms");
        File ms = new File(IndexFile.DATA_PATH[iempty] + emptyFile);
        ipath = iempty;
        ms.renameTo(newname);
        init = true;
      } else {                // no file or empty, find biggest free space
        int maxfree = 0;
        init = true;
        for (int i = 0; i < IndexFile.NPATHS; i++) {
          String path = IndexFile.DATA_PATH[i];
          File dir = new File(path.substring(0, path.length() - 1));
          String[] filess = dir.list();
          if (filess != null) {
            if (filess.length >= 1) {
              if (filess[0].contains("lost") && filess.length > 1) {
                filess[0] = filess[1];// do not use lost and found
              }              // if its an empty directory use the directory
              if (filess.length == 0 || (filess.length == 1 && filess[0].contains("lost"))) {
                filess = new String[1];
                filess[0] = path.substring(0, path.length() - 1);
              }
              int free = FreeSpace.getFree(IndexFile.DATA_PATH[i] + filess[0]);
              Util.prta(Util.clear(tmpsb).append("IndexFileRep: Free space on ").
                      append(IndexFile.DATA_PATH[i]).append(filess[0]).append(" is ").append(free));
              if (free > maxfree) {
                ipath = i;
                maxfree = free;
              }
            }
          }
        }
        if (maxfree < 20000) {     // Less than 12 GB is available on best drive, wake someone up
          SendEvent.edgeSMEEvent("LowFreeSpace", "Max free space < 20 GB = " + (maxfree / 1000)
                  + " on " + IndexFile.getNode() + "/" + Util.getAccount() + " " + Util.getProcess() + " file=" + name, "IndexFileRep");
        }
      }
    }

    // If its a fully qualified name, then leave it alone
    if (ipath == -1) {
      Util.prt("WARNING: no paths returned space - cannot choose a path!!!!!");
      SendEvent.debugEvent("NoPaths", "No paths found for opening files in IFR", "IndexFileReplicator");
    }
    if (fileFound != null) {
      filename = fileFound;
    } else {
      filename = IndexFile.DATA_PATH[ipath] + name;
    }

    // Now open the file and index
    // Using the "s" or "d" mode  made things much slower on Solaris especially, IO/sec
    // went down to pathetic levels (data file writes 200 ms dropped to 1 or 2 ms)
    // Even the relatively infrequent .idx and .chk were seen as a problem.  Let the
    // system do it and recover dropped blocks later.
    String mode = "rw";
    if (readOnly) {
      mode = "r";
    }
    if (dbg) {
      Util.prta(Util.clear(tmpsb).append("IFR: open ").append(filename).append(".idx init=").append(init).
              append("/").append(julian).append(" mode=").append(mode));
    }
    rw = new RawDisk(filename + ".idx", mode);
    mode = "rw";
    try {
      if (readOnly) {
        mode = "r";
      }
      if (dbg) {
        Util.prta(Util.clear(tmpsb).append("IFR: open ").append(filename).append(".chk init=").append(init).
                append("/").append(julian).append(" mode=").append(mode));
      }
      checkFile = new RawDisk(filename + ".chk", mode);
    } catch (FileNotFoundException e) {  // if we are read only, do not insist on a .chk file
      if (!readOnly) {
        throw e;
      }
      checkFile = null;
    }
    mode = "rw";
    if (readOnly) {
      mode = "r";
    }
    if (dbg) {
      Util.prta(Util.clear(tmpsb).append("IFR: open data ").append(filename).append(".ms init=").append(init).
              append("/").append(julian).append(" mode=").append(mode));
    }
    dataFile = new RawDisk(filename + ".ms", mode);   // was always "rwd"

    if (init) {
      if (readOnly) {
        throw new EdgeFileReadOnlyException("Cannot init a readonly file");
      }
      if (exists) {
        length = (int) (dataFile.length() / 512L);
      } else {
        length = IndexFile.DEFAULT_LENGTH;
      }
      highwater = 0;
      dataFile.setLength(length * 512L);      // create the file with the right length
      //Util.prta("IndexFileRep:2 set highwater nd="+toString()+" to "+highwater);
      if (dbg) {
        Util.prta(Util.clear(tmpsb).append("IFR: new created ").append(filename).append("/").append(julian));
      }
      Util.prta(Util.clear(tmpsb).append("IFR: open data ").append(filename).append(".ms init=").append(init).
              append("/").append(julian).append(" mode=").append(mode).append(" highwater=0"));

      try {
        writeIndexBlock(IndexFile.zerobuf, 0, -1, "ZeroCtrlBlk");        // write out zeroed masterblock
        dataFile.seek(0L);
        Util.prta(Util.clear(tmpsb).append("IFR: open new data file=").append(filename).
                append(" to length=").append(length).append(" zerbufsize=").append(IndexFile.ZERO_BUF_SIZE));
        dataFile.write(IndexFile.zerobuf, 0, IndexFile.zerobuf.length);         // write one set of zeros at the beginning
      } catch (IllegalSeednameException e) {
        Util.prta("IFR: e=" + e);
      } // This is impossible
      catch (RuntimeException e) {
        Util.prta("IFR: write zero block e=" + e.getMessage());
        e.printStackTrace();
      }
    } else {
      if (dataFile.length() / 512 != length) {
        if (length != 0 && dbg) {
          Util.prt(Util.clear(tmpsb).append("IFR: Read length does not match file length! file.length=").
                  append(dataFile.length() / 512).append(" length=").append(length));
          SendEvent.debugEvent("IFRLenMismat", "Read len does not match file length! fil.len=" + dataFile.length() / 512
                  + " len=" + length, "IndexFileReplicator");
        }
        length = (int) (dataFile.length() / 512L);
      }
      // for an existing file, make sure next_extent is less than highwater.
      if (exists) {
        try {
          if (dbg) {
            Util.prt(Util.clear(tmpsb).append("IFR: exists rw=").append(rw.toString()).
                    append(" idx rw.length(B)=").append(rw.length()).append(" data.length(blk)=").append(length));
          }
          if (rw.length() > 512) {
            synchronized (scratch) {
              rw.readBlock(scratch, 0, 512);
              bbscratch.clear();
              int len = bbscratch.getInt();        // length of file in blocks
              int next_extent = bbscratch.getInt();  // next extent
              if (dbg) {
                Util.prta(Util.clear(tmpsb).append("IFR: existing file next_extent=").append(next_extent).
                        append(" idx.filelen=").append(len).append(" dat.len=").
                        append(dataFile.length() / 512L).append(" hi=").append(highwater));
              }
              if (next_extent > highwater) {
                highwater = next_extent;
                if (dbg) {
                  Util.prta(Util.clear(tmpsb).append("3 set highwater nd=").append(toString()).append(" to ").append(highwater));
                }
              }
            }
          }
        } catch (IOException e) {
          Util.IOErrorPrint(e, "IndexFileRep: reading 0 block of idx for next_extent");
        }
      }
      //if(dbg) 
      Util.prta(Util.clear(tmpsb).append("IFR: Old IndexFile opened =").append(filename).
              append(" data length=").append(length).append(" exists=").append(exists).
              append(dataFile == null ? "datafile=null" : "").append(rw == null ? "idx=null" : ""));
    }
    shutdown = new ShutdownIndexFile(this);
    Runtime.getRuntime().addShutdownHook(shutdown);

    // If writing is allowed, start up the zeroer to keep zeroed block in front of where they go
    if (!readOnly) {
      zeroer = new ZeroDataFile(dataFile);
      Util.sleep(50);
    }
    whoHas = "OIF: add " + filename + Util.fs + julian;
    String key = node.trim() + "_" + julian;
    synchronized (files) {
      files.put(getHash(node, julian), this);
    }
  }

  /**
   * trim the filesize of this file by setting the file length to the length of the last extent in
   * the index
   *
   * @return If a major reduction is file size occurred
   */
  public synchronized boolean trimFileSize() {
    try {
      if (rw.length() > 512) {
        synchronized (scratch) {
          rw.readBlock(scratch, 0, 512);
          bbscratch.clear();
          int len = bbscratch.getInt();
          int next_extent = bbscratch.getInt();
          long fileLength = dataFile.length() / 512L;

          if (fileLength > next_extent + 2000) {
            Util.prt(Util.clear(tmpsb).append("IndexFileRep: trimFileSize rw=").append(filename).
                    append(" next_extent=").append(next_extent).append(" data length blocks=").append(fileLength).
                    append("/idx=").append(len).append(" trim to next_extent+2000"));
            // check to be sure there is no dat just past the end of file
            for (int iblk = next_extent + 64; iblk < next_extent + 2000; iblk++) {
              dataFile.readBlock(scratch, iblk, 512);
              for (int i = 0; i < 512; i++) {
                if (scratch[i] != 0) {
                  Util.prt(Util.clear(tmpsb).append("trimFileSize() found non-zero block at iblk=").append(iblk));
                  return false;
                }
              }
            }
            dataFile.setLength((long) (next_extent + 2000L) * 512L);
            Util.prta(Util.clear(tmpsb).append(filename).append(" trimFileSize() to ").append((next_extent + 2000L) * 512L).
                    append(" b ").append(next_extent + 200).append(" blks saving ").append(fileLength - next_extent - 2000).append(" blks"));
            return true;
          } else {
            Util.prt(Util.clear(tmpsb).append(rw.toString()).append(" nothing to trim ").
                    append(next_extent).append(" ").append(fileLength));
            return true;
          }
        }
      }
      return true;
    } catch (IOException e) {
      Util.IOErrorPrint(e, "IndexFileRep,trimFileLength() : IOException=" + e.getMessage());
      return false;
    }
  }
  private final StringBuilder seedtmp = new StringBuilder(12);

  /**
   * write index block, if this index is the ctl block, the next_extent is checked to insure out
   * version is just as high and that the zero is past it and the ctl block is updated. If this is a
   * master block, then it is updated in the chk file. It will wait for the zeroer to get past the
   * new highwater.
   *
   * @param buf The 512 bytes of index block to write
   * @param indexBlock The block number in the index file
   * @param extentIndex The extentIndex within this index block (-1 if its a MasterBlock)
   * @param seedname The 12 character seedname (to allow checking for illegal updates)
   * @throws IOException If one occurs on the physical I/O.
   * @throws IllegalSeednameException if the index block contains an illegal seedname
   */
  public synchronized void writeIndexBlock(byte[] buf, int indexBlock, int extentIndex, String seedname)
          throws IOException, IllegalSeednameException {
    Util.clear(seedtmp).append(seedname);
    writeIndexBlock(buf, indexBlock, extentIndex, seedtmp);
  }

  /**
   * write index block, if this index is the ctl block, the next_extent is checked to insure its
   * version is just as high and that the zero is past it and the ctl block is updated. If this is a
   * master block, then it is updated in the chk file. It will wait for the zeroer to get past the
   * new highwater.
   *
   * @param buf The 512 bytes of index block to write
   * @param indexBlock The block number in the index file
   * @param extentIndex The extentIndex within this index block (-1 if its a MasterBlock)
   * @param seedname The 12 character seedname (to allow checking for illegal updates)
   * @throws IOException If one occurs on the physical I/O.
   * @throws IllegalSeednameException if the index block contains an illegal seedname
   */
  public synchronized void writeIndexBlock(byte[] buf, int indexBlock, int extentIndex, StringBuilder seedname)
          throws IOException, IllegalSeednameException {
    rw.writeBlock(indexBlock, buf, 0, 512);   // write block (where ever it is) in .idx
    //if(dbg) Util.prt("IFR writeIndexBlock() iblk="+indexBlock+"/"+extentIndex);
    // if this is a write of the master block, check that the zeroer is caught up
    if (indexBlock == 0) {   // if its the first block, write it into the check file.
      ByteBuffer bb = ByteBuffer.wrap(buf);
      bb.clear();
      bb.getInt();   // skip the length word 
      int next_extent = bb.getInt();
      int next_index = ((int) bb.getShort()) & 0xffff;
      if (dbg) {
        Util.prta(Util.clear(tmpsb).append("ctl upd next=").append(next_extent).
                append(" nidx=").append(next_index).append("_").append(node).append(" ").append(seedname));
      }

      checkFile.writeBlock(indexBlock, buf, 0, 512);  // Write in chk file
      if (highwater < next_extent) {
        if (next_extent - highwater > 30000) {
          Util.prta(Util.clear(tmpsb).append("WIB: set suspicious highwater ").append(filename).
                  append(" frm ").append(highwater).append(" to next=").append(next_extent).
                  append(" indblk=").append(indexBlock).append(" extentIndex=").
                  append(extentIndex).append(" ").append(seedname));
          SendEvent.debugEvent("IFRSusHiWatr",
                  "suspicious hiwater " + getFilename() + " frm " + highwater + " to next=" + next_extent + " idxblk=" + indexBlock + " extIdx=" + extentIndex + " " + seedname,
                  "IndexFileReplicator");
          //new RuntimeException("WIB: Highwater set suspicious="+highwater+"next="+next_extent+" idxblk="+indexBlock+" extentIndex="+extentIndex).printStackTrace();
        }
        highwater = next_extent;
      }
      ctlUpdates++;     // note, this might trigger the zero starting.
    } else {          // Its an index block do processing on check block
      if (extentIndex != -1) {   // Its a index  block (not a master), do checkBlock work
        indexUpdates++;
        boolean hasIt = indices.containsKey(indexBlock);
        IndexBlockCheck chk = getIndexBlockCheck(indexBlock);
        if (chk == null) {
          Util.clear(seedtmp);
          for (int i = 0; i < 12; i++) {
            seedtmp.append((char) (buf[i] == 0 ? ' ' : buf[i]));
          }
          chk = new IndexBlockCheck(seedtmp, nodesb, indexBlock, this);
          // if the check block is fully confirmed it is senseless to keep it!
          if (!chk.isFullyConfirmed()) {
            if (dbg) {
              Util.prta(Util.clear(tmpsb).append("chk blk add=").append(indexBlock).
                      append(" had=").append(hasIt).append(" ").append(chk.toStringBuilder(null)));
            }
            //synchronized (indices) {
            indices.put(indexBlock, chk);
            //}
          } else {
            chk.clear();      // Allow this to be reapped
          }
        }

      } // else its a master block, no checkblock work to do
      else {
        masterUpdates++;
        checkFile.writeBlock(indexBlock, buf, 0, 512);  // write in chk fileda
        if (dbg) {
          Util.prta(Util.clear(tmpsb).append("MB= update block=").append(indexBlock).
                  append(" ").append(filename).append(" ").append(buf[0]).append(" ").
                  append(buf[1]).append(" ").append(buf[2]).append(" ").append(buf[3]).
                  append(" ").append(buf[4]).append(" ").append(buf[5]).append(" ").
                  append(buf[6]).append(" ").append(buf[7]));
        }
      }
    }
    lastUsed = System.currentTimeMillis();
  }

  /**
   * write out a new data block, if this is new highwater in the file, it sets it so and makes sure
   * the zeroer has zeroed past this block before it is written.
   *
   * @param seedname The seedname for this data block (or continuation block) for check
   * @param buf The 512 bytes of data to write
   * @param iblk The block number to put it in
   * @param indexBlock The index block in which this data block reflected
   * @param index The extent index in the index block where this data block is reflected.
   * @param continuation If true, this is not a header block, but the continuation of a bigger MS
   * block
   * @return true, if the blocks is a remark of a received block
   * @throws IOException If one occurs during the physical I/O.
   * @throws IllegalSeednameException if the data block contains an illegal seedname
   */
  public synchronized boolean writeDataBlock(String seedname, byte[] buf, int iblk,
          int indexBlock, int index, boolean continuation)
          throws IOException, IllegalSeednameException {
    Util.clear(seedtmp).append(seedname);
    return writeDataBlock(seedtmp, buf, iblk, indexBlock, index, continuation);
  }

  /**
   * write out a new data block, if this is new highwater in the file, it sets it so and makes sure
   * the zeroer has zeroed past this block before it is written.
   *
   * @param seedname The seedname for this data block (or continuation block) for check
   * @param buf The 512 bytes of data to write
   * @param iblk The block number to put it in
   * @param indexBlock The index block in which this data block reflected
   * @param index The extent index in the index block where this data block is reflected.
   * @param continuation If true, this is not a header block, but the continuation of a bigger MS
   * block
   * @return true, if the blocks is a remark of a received block
   * @throws IOException If one occurs during the physical I/O.
   * @throws IllegalSeednameException if the data block contains an illegal seedname
   */
  public synchronized boolean writeDataBlock(StringBuilder seedname, byte[] buf, int iblk,
          int indexBlock, int index, boolean continuation)
          throws IOException, IllegalSeednameException {
    //long trip = System.currentTimeMillis();
    if (buf[0] == 0 && buf[1] == 0 && buf[2] == 0 && buf[5] == 0) {
      if (!continuation) {
        Util.prta(Util.clear(tmpsb).append("IFR: **** attempt to write zero block to blk=").append(iblk).
                append(" ").append(seedname).append(" ").append(indexBlock).append("/").append(index).append(" ").append(filename));
        SendEvent.debugEvent("IFRZeroWrite", "Attempt to write zero blk " + iblk + " " + seedname + " " + indexBlock + "/" + index + " " + filename, "IndexFileReplicator");
        return false; // do not write out zero blocks
      }
      // if it is a continuation, write zeros so check gets updated
    }
    if (iblk > highwater) {
      highwater = iblk;
      //Util.prta("WDB: set highwater nd="+toString()+" to "+highwater+" lastzero="+zeroer.getLastZero());
    }
    if (zeroer != null) {
      if (zeroer.getLastZero() <= iblk || iblk > length) {
        // defer this write for later
        zeroer.queue(seedname.toString(), buf, iblk, indexBlock, index);
        return false;
        //zeroer.waitForHighwater();    // obsolete, queue it instead
      }
    }
    //long start = System.currentTimeMillis();
    // Note: write the block, note that if it throws a IOException, the check block is not updated
    if (!readOnly) {
      dataFile.writeBlock(iblk, buf, 0, 512);
    }
    if (highwater < iblk) {
      highwater = iblk;
    }

    //long write = System.currentTimeMillis() -trip-(start-trip); 
    //if(dbg) Util.prta("IFR writeData() iblk="+iblk+"  idx="+indexBlock+"/"+index+"/"+(iblk%64));
    // If this is for an index block that has not yet been created, create it!
    boolean hasIt = indices.containsKey(indexBlock);
    IndexBlockCheck chk = getIndexBlockCheck(indexBlock);
    if (chk == null) {
      /*String seedname = new String(buf, 8, 12);
      seedname = seedname.substring(10,12)+seedname.substring(0,5)+
          seedname.substring(7,10)+seedname.substring(5,7);*/
      if (Util.stringBuilderEqual(seedname, "REQUESTEDBLK")) {
        MiniSeed.crackSeedname(buf, seedname);// use correct seedname
      }
      chk = new IndexBlockCheck(seedname, nodesb, indexBlock, this);
      // if the check block is fully confirmed it is senseless to keep it!
      if (!chk.isFullyConfirmed()) {
        //synchronized (indices) {
        indices.put(indexBlock, chk);
        //}
        if (dbg) {
          Util.prta(Util.clear(tmpsb).append("writeDataBlock() chk blk add ").
                  append(indexBlock).append(" had=").append(hasIt).append(" ").append(chk.toStringBuilder(null)));
        }
      } else {
        chk.clear();        // Block is completed, allow it to reap
        return false;
      }
    }
    dataUpdates++;
    lastUsed = System.currentTimeMillis();
    return chk.markBlockUsed(index, iblk);
    //Util.prt("writeDataBLock() st="+(start-trip)+" write="+write+" datastart="+(datastart-start)+
    //   " chk="+(lastUsed -trip - (start-trip) -write)+" tot="+(lastUsed-trip));
  }

  /**
   * return state of shutting down variable
   *
   * @return True if in a shutting down state
   */
  static public boolean isShuttingDown() {
    return shuttingDown;
  }

  /**
   * get IndexFile associated with a given day, This routine enforces the open days rules and
   * returns a pointer to "future.idx" or "past.idx" if the day is out of range
   *
   * @param julian The julian date of the file desired.
   * @param node The string edge node of the desired file
   * @return The index file or null if none found
   */
  static public IndexFileReplicator getIndexFileReplicator(int julian, String node) {
    stateLock = 1;
    if (!initdone) {
      init();
    }
    IndexFileReplicator idx;
    whoHas = "getIdxFile : " + julian;
    synchronized (files) {
      idx = files.get(getHash(node, julian));
    }
    // If there is an index in files, set its used time so it will not be closed, and if it is closing return null
    if (idx != null) {
      idx.updateLastUsed();

      if (idx.isClosing()) {   // If the file is closing, return null
        boolean isClosed = idx.waitForClose();
        if (!isClosed) {
          Util.prta("***** IFR did not promptly close! " + node + " " + julian);
          SendEvent.edgeSMEEvent("IFRNotClosed", "IFR not promptly closed " + node + " " + julian, "IndexFileReplicator");
        }
        synchronized (files) {
          files.remove(getHash(node, julian));  // Take this file off the static list
        }
        idx = null;
      }
    }
    stateLock = -1;
    return idx;
  }

  /**
   * get IndexFile associated with a given day, This routine enforces the open days rules and
   * returns a pointer to "future.idx" or "past.idx" if the day is out of range
   *
   * @param julian The julian date of the file desired.
   * @param node The string edge node of the desired file
   * @return The index file or null if none found
   */
  static public IndexFileReplicator getIndexFileReplicator(int julian, StringBuilder node) {
    stateLock = 1;
    if (!initdone) {
      init();
    }
    IndexFileReplicator idx;
    whoHas = "getIdxFile : " + julian;
    synchronized (files) {
      idx = files.get(getHash(node, julian));
    }
    // If there is an index in files, set its used time so it will not be closed, and if it is closing return null
    if (idx != null) {
      idx.updateLastUsed();

      if (idx.isClosing()) {   // If the file is closing, return null
        boolean isClosed = idx.waitForClose();
        if (!isClosed) {
          Util.prta("***** IFR did not promptly close! " + node + " " + julian);
          SendEvent.edgeSMEEvent("IFRNotClosed", "IFR not promptly closed " + node + " " + julian, "IndexFileReplicator");
        }
        synchronized (files) {
          files.remove(getHash(node, julian));  // Take this file off the static list
        }
        idx = null;
      }
    }
    stateLock = -1;
    return idx;
  }

  /**
   * get number of open files
   *
   * @return The number of open files
   */
  static public int getNOpenFiles() {
    stateLock = 2;
    return files.size();
  }

  /**
   * return the filename associated with this IndexFile
   *
   * @return The filename string
   */
  public String getFilename() {
    return filename;
  }

  /**
   * return the requested path
   *
   * @param i The index number of the path
   * @return String with path name
   */
  static public String getPath(int i) {
    if (!initdone) {
      init();
    }
    return IndexFile.DATA_PATH[i];
  }

  /**
   * return number of paths
   *
   * @return the number of paths
   */
  static public int getNPaths() {
    return IndexFile.NPATHS;
  }

  public static String getPathString() {
    return IndexFile.paths.toString();
  }

  static synchronized public void forceinit() {
    initdone = false;
    init();
  }

  /**
   * This does all of the one-time static set up, create tree, read properties. Most of it is done
   * in IndexFile.init().
   */
  static synchronized public void init() {
    //Util.prta("IFR: in init() done="+initdone);
    stateLock = 3;
    if (!initdone) {
      bbscratch = ByteBuffer.wrap(scratch);
      initdone = true;
      IndexFile.init();       // This sets the NDAYS, PATHS, etc and EXTEND size, and the zerobuf is made and cleared
      //if(dbg)
      Util.prt("IFR: Ndatapaths=" + Util.getProperty("ndatapath") + " DEFAULT_LENGTH=" + IndexFile.DEFAULT_LENGTH
              + " EXTEND_BLOCKS=" + IndexFile.DEFAULT_EXTEND_BLOCKS);
      Util.prta(0 + "IFR: path=" + IndexFile.DATA_PATH[0] + " npaths=" + IndexFile.NPATHS);
      if (dbg) {
        Util.prta("IndexFiles.init() files hash=" + files.hashCode());
      }

    }
  }

  /**
   * close this index file and clean up other resources. Synchronized so only one close thread can
   * be created
   *
   * @throws IOException If IOException while trying to close the file
   */
  public synchronized void close() throws IOException {
    if (closer != null) {
      return;    // This file has already been closed!
    }
    stateLock = 6;
    lastUsed = System.currentTimeMillis();
    closer = new CloseThread(this);
  }

  /**
   * closing can take quite some time, so put it in a thread to prevent slow downs
   */
  public final class CloseThread extends Thread {

    IndexFileReplicator ifl;
    private final StringBuilder cltmpsb = new StringBuilder(100);
    private String str;

    public CloseThread(IndexFileReplicator f) {
      ifl = f;
      start();
    }

    @Override
    public void run() {
      str = toString();
      // remove this IndexFile from the TreeMap of open files
      if (dbg) {
        Util.prta(Util.clear(cltmpsb).append("closeThread start on ").append(ifl.getFilename()));
      }
      synchronized (files) {      // block other users from finding this file while it is being closed
        //synchronized(indexBlocksLock) {
        whoHas = "IF closer(): " + filename + Util.fs + julian;
        if (dbg) {
          Util.prta(Util.clear(cltmpsb).append("IFR:closeThread: files.remove2 then closeJulian ").
                  append(filename).append(str).append("/").append(julian).append(" hiwater=").append(highwater));
        }
        files.remove(getHash(node, julian));  // Take this file off the static list
      }

      if (dbg) {
        Util.clear(cltmpsb).append("IFR:closeThread: files.remove ").append(filename);
        toStringBuilder(cltmpsb).append("/").append(julian).append(" hiwater=").append(highwater);
        Util.prta(cltmpsb);
      }

      if (indices != null) {
        // close all of the open IndexBlockChecks.
        synchronized (indices) {
          TLongObjectIterator<IndexBlockCheck> itr = indices.iterator();
          while (itr.hasNext()) {
            itr.advance();
            IndexBlockCheck chk = itr.value();
            try {
              if (chk != null) {
                chk.close();  //("IFR close");
              }
            } catch (IOException e) {
              Util.prta("*** closeThread : IOException writing chk while closing=" + chk);
            }
          }
          //Util.prta("chk blks clear");
          indices.clear();
        }
      }
      if (zeroer != null) {
        zeroer.close();
      }

      // Close down all related index blocks (ones open for this Julian day
      //IndexBlock.closeJulian(julian);   // Clear out any indexBlocks that point here
      // At this point there should be no references left to this file object, shut it down
      if (dbg) {
        Util.prta(Util.clear(cltmpsb).append("IndexFileRep: closeThread() idx updated size=").
                append(indices.size()).append(" on ").append(filename).append(str));
      }

      // close files 
      try {
        if (dbg) {
          Util.prta(Util.clear(cltmpsb).append("IFR:closeThread close() close ").append(filename).append(str));
        }
        whoHas = "IFR closer(): " + filename + Util.fs + julian;
        if (dbg) {
          Util.prta(Util.clear(cltmpsb).append("IFR:closeThread: files.remove then closeJulian ").
                  append(filename).append(str).append("/").append(julian).append("_").append(node));
        }
        if (rw != null) {
          rw.close();
          if (dbg) {
            Util.prta(Util.clear(cltmpsb).append("IFR:closeThread : datafile.close() ").
                    append(filename).append(str));
          }
        }
        if (dataFile != null) {
          dataFile.close();       // close access to data portion (this is why zeroer must be dead!)
          if (dbg) {
            Util.prta(Util.clear(cltmpsb).append("IFR:closeThread: files.remove then closeJulian ").
                    append(filename).append(str).append("/").append(julian).append("_").append(node).append("highwater=").append(highwater));
          }
        }
        if (checkFile != null) {
          checkFile.close();
          if (dbg) {
            Util.prta(Util.clear(cltmpsb).append("IFR:closeThread: check File closed ").
                    append(filename).append(str).append("/").append(julian).append("_").append(node).
                    append(" highwater=").append(highwater));
          }
        }

      } catch (IOException e) {
        Util.prta(Util.clear(cltmpsb).append("IFR:closeThread IOException Thrown ").append(filename).
                append(str).append("/").append(julian).append("highwater=").append(highwater));
      }
      rw = null;
      dataFile = null;
      if (!shuttingDown) {
        try {
          Runtime.getRuntime().removeShutdownHook(shutdown);
        } catch (Exception e) {
        }
      }
      shutdown = null;
      Util.prta(Util.clear(cltmpsb).append("IFR:closeThread: exitThread complete ").
              append(filename).append(" ").append(str));
      ifl = null;
    }
  }

  /**
   * set the default length of the data files in blocks
   *
   * @param l The length in blocks of the data files by default
   */
  static public void setDefaultLength(int l) {
    IndexFile.DEFAULT_LENGTH = l;
  }

  /**
   * Process the indices writing out if they are stale and write and eliminate if they are complete.
   */
  private synchronized void processIndexChecks() {
    synchronized (indices) {
      TLongObjectIterator<IndexBlockCheck> e = indices.iterator();
      long now = System.currentTimeMillis();
      IndexBlockCheck chk;
      int nupd = 0;
      int nconf = 0;
      while (e.hasNext()) {
        e.advance();
        chk = (IndexBlockCheck) e.value();
        if (chk == null) {
          continue;
        }
        try {
          if (chk.isFullyConfirmed()) {
            //Util.prta("IFR: check block fullConfirmed "+chk.toString());
            chk.updateBlock("IFR:PIConf");
            nconf++;
            //Util.prta("chk blk removed Pr  ocessIndexChecks() "+chk.toString());
            chk.clear();
            e.remove();
          } else if (chk.isModified() && (now - chk.getLastUpdate()) > 120000) {
            if (dbg) {
              Util.prta(Util.clear(tmpsb).append("IFR: check block timeout ").append(chk.toStringBuilder(null)));
            }
            nupd = 0;
            chk.updateBlock("IFR:PICto");
          }
        } catch (IOException e2) {
          Util.IOErrorPrint(e2, "IOException updating chk block " + chk.toString());
        }
      }
      Util.clear(tmpsb).append("IFR: PI size=").append(indices.size()).append(" nupd=").append(nupd).append(" nconfirmed=").append(nconf).append(" ");
      toStringBuilder(tmpsb);
      Util.prta(tmpsb);
    }

  }

  /**
   * Close open for write file using the list of open files.
   */
  public static void closeAll() {
    stateLock = 4;
    Util.prta("IFR: close All() called");
    synchronized (files) {
      try {
        TLongObjectIterator<IndexFileReplicator> itr = files.iterator();
        //Object [] all = files.values().toArray();
        //for (Object all1 : all) {
        while (itr.hasNext()) {
          itr.advance();
          IndexFileReplicator ifl = itr.value();
          if (ifl != null) {
            ifl.close();
          }
          //((IndexFileReplicator) all1).close();
        }
      } catch (IOException e) {
        Util.prt("CloseAll IOexception e=" + e.getMessage());
      }
    }

  }

  /**
   * Dump out info on all known open files.
   *
   * @return String with one file per line and its status
   */
  public static StringBuilder getFullStatusSB() {
    StringBuilder sb = new StringBuilder(10000);
    synchronized (files) {
      TLongObjectIterator<IndexFileReplicator> itr = files.iterator();
      //Object [] all = files.values().toArray();
      //for (Object all1 : all) {
      while (itr.hasNext()) {
        itr.advance();
        IndexFileReplicator ifl = itr.value();
        if (ifl != null) {
          sb.append(ifl.toStringFull()).append("\n");
        }
      }
    }
    return sb;
  }

  public static String getFullStatus() {
    return getFullStatusSB().toString();
  }

  /**
   * close any files until less that the target number of files are opened. The files are closed
   * based on the the longest times since they were last used.
   *
   * @param target The number of files that are allowed to be left open
   */
  public static void trimFiles(int target) {
    Object[] all;
    synchronized (files) {
      all = files.values();
    }
    IndexFileReplicator ifr;
    long now = System.currentTimeMillis();
    long oldest = 0;
    Util.prta("IFR: trimFiles() #openIFR=" + all.length + " max=" + target);
    while (getNOpenFiles() > target) {
      int ind = -1;
      oldest = 0;
      for (int i = 0; i < all.length; i++) {
        ifr = (IndexFileReplicator) all[i];
        if (ifr != null) {
          if (now - ifr.getLastUsed() > oldest && now - ifr.getLastUsed() > 30000) {
            oldest = now - ifr.getLastUsed();
            ind = i;
          }
        }
      }
      if (ind == -1) {
        return;
      }
      try {
        ifr = (IndexFileReplicator) all[ind];
        //Util.prta("IFR: trimFiles() time out on file="+ifr.toString()+" #open="+getNOpenFiles()+" max="+target);
        if (ifr != null) {
          ifr.close();      // this removes the file from the files map.
        }
        all[ind] = null;
      } catch (IOException e) {
        Util.prta("IFR: trimFiles() IOException closing file" + e.getMessage());
      }
    }
    Util.prta("IFR: trimFiles() to " + target + " newest was last used " + oldest + " ms ago.");
  }

  /**
   * Close any files which have not been used for the given number of millis
   *
   * @param ms The number of millis that need to have expired without usage to cause close
   */
  public static void closeStale(int ms) {
    stateLock = 10;
    Object[] all;
    synchronized (files) {
      all = files.values();
    }
    stateLock = -10;
    IndexFileReplicator ifr;
    long now = System.currentTimeMillis();
    Util.prta("IFR: closeStale() #openIFR=" + all.length + " ms=" + ms);
    for (Object all1 : all) {
      ifr = (IndexFileReplicator) all1;
      try {
        if (ifr != null) {
          if ((now - ifr.getLastUsed()) > ms) {
            Util.prta("IFR: closeStale() time out on file=" + ifr.toString());
            ifr.close();      // This removes the file from files map
          }
        }
      } catch (IOException e) {
        Util.prta("IFR: closeStale() IOException closing file" + e.getMessage());
      } catch (RuntimeException e) {
        Util.prta("IFR: closeStale() Runtime closing file. continue! e=" + e);
      }
    }
    Util.prta("IFR: closeState() done");
  }

  /**
   * Close any files which are not within the limit number of days and not used in some amount of
   * time.
   *
   * @param limit The limit of the number of days that can be open
   * @param msused The number of milliseconds that must have passed since the file was last used
   * @return The number of files closed by this call
   */
  public static int closeJulianLimit(int limit, int msused) {
    stateLock = 11;
    int todayJulian = SeedUtil.toJulian(System.currentTimeMillis());
    Object[] all;
    synchronized (files) {
      all = files.values();
    }
    stateLock = -11;
    IndexFileReplicator ifr;
    long current = System.currentTimeMillis();
    Util.prta("IFR: closeStale() #openIFR=" + all.length + " limit=" + limit + " msused=" + msused);
    int count = 0;
    for (Object all1 : all) {
      ifr = (IndexFileReplicator) all1;
      try {
        if (ifr != null) {
          if ((todayJulian - ifr.getJulian()) > limit && (current - ifr.getLastUsed()) > msused) {
            Util.prta("IFR: closeJulianLimit()  on file=" + ifr.toString() + " ages=" + (current - ifr.getLastUsed()) + ">" + msused);
            ifr.close();      // This removes the file from the files map
            count++;
          }
        }
      } catch (IOException e) {
        Util.prta("IFR: closeJulianLimit() IOException closing file" + e.getMessage());
      }
    }
    return count;
  }

  /**
   * For every open IndexFileReplicator, process up any stale or completed blocks.
   */
  public static void writeStale() {
    stateLock = 12;
    Object[] all;
    synchronized (files) {
      all = files.values();
    }
    stateLock = -12;
    IndexFileReplicator ifr;
    for (Object all1 : all) {
      ifr = (IndexFileReplicator) all1;
      if (ifr != null) {
        ifr.processIndexChecks();
      }
    }
  }

  /**
   * For every open IndexFileReplicator, process up any stale or completed blocks.
   *
   * @return the status of all of the IndexFileReplicators on the files list
   */
  public static StringBuilder getStatusAll() {
    Object[] all;
    synchronized (files) {
      all = files.values();
    }
    IndexFileReplicator ifr;
    int totalIndex = 0;
    StringBuilder sb = new StringBuilder(300);
    for (Object all1 : all) {
      ifr = (IndexFileReplicator) all1;
      if (ifr != null) {
        sb.append(ifr.getStatusString()).append("\n");
        totalIndex += ifr.indices.size();
      }
    }
    sb.append("Total idx=").append(totalIndex).append("\n");
    return sb;
  }
  /**
   * Cause the master blocks to be re-read. Should only be done on readonly files.
   *
   * @return the array of Master blocks
   * @throws IOException If one is generated by the reads of the master blocks.
   */
  private final MasterBlock[] mb = new MasterBlock[IndexFile.MAX_MASTER_BLOCKS];
  private final int[] mBlocks = new int[IndexFile.MAX_MASTER_BLOCKS];
  private final byte[] ctlbuf = new byte[512];
  private ByteBuffer ctl;
  private long nMBSkip, nMBLoad;

  public MasterBlock[] getMasterBlocks() throws IOException {
    if (System.currentTimeMillis() - lastGetMasterBlocks < 20000) {
      nMBSkip++;
      return mb;
    }
    synchronized (mBlocks) {
      lastGetMasterBlocks = System.currentTimeMillis();
      nMBLoad++;
      rw.readBlock(ctlbuf, 0, 512);      // get ctrl region
      if (ctl == null) {
        ctl = ByteBuffer.wrap(ctlbuf);
      }
      // Unpack the ctlbuf
      ctl.clear();
      int length2 = ctl.getInt();
      int next_extent = ctl.getInt();
      int next_index = ((int) ctl.getShort()) & 0xffff;
      for (int i = 0; i < IndexFile.MAX_MASTER_BLOCKS; i++) {
        mBlocks[i] = ((int) ctl.getShort()) & 0xffff;
      }
      for (int i = 0; i < IndexFile.MAX_MASTER_BLOCKS; i++) {
        try {
          if (mBlocks[i] != 0) {
            if (mb[i] == null) {
              mb[i] = new MasterBlock((int) mBlocks[i], rw, false);
            } else {
              mb[i].reload((int) mBlocks[i], rw);
            }
          } else {
            break;         // exit when we are out of allocated blocks
          }
        } catch (EOFException e) {
          try {
            if (mb[i] == null) {
              mb[i] = new MasterBlock((int) mBlocks[i], rw, false);
            } else {
              mb[i].reload((int) mBlocks[i], rw);
            }
            Util.prta("IFR: * getMasterBlocks() return of read after EOF sucessful! rw=" + rw + " mblk[" + i + "]=" + mBlocks[i]
                    + " rw.len=" + rw.length() + " pos=" + rw.getFilePointer());
            continue;
          } catch (EOFException e2) {  // Is someone repositioning rw in another thread causing an EOF?
            Util.prta("IFR: *** getMasterBlocks() reading master block unsuccessful ");
            e2.printStackTrace();
          }
          throw new EOFException("reading master block from rw=" + rw + " mblk[" + i + "]=" + mBlocks[i]
                  + " rw.len=" + rw.length() + " pos=" + rw.getFilePointer());
        }
      }
    }
    return mb;
  }

  /**
   * Compares two file types orders them by julian day.
   *
   * @param other The IndexFile to compare against
   * @return -1 if this < other, 0 if equal, 1 if this is greater than
   */
  @Override
  public int compareTo(Object other) {
    int ojulian = ((IndexFileReplicator) other).getJulian();
    if (julian < ojulian) {
      return -1;
    } else if (julian > ojulian) {
      return 1;
    }
    String onode = ((IndexFileReplicator) other).getNode();
    return node.compareTo(onode);
  }

  /**
   * This class handles the shutdown chores for a shutdown hook.
   */
  class ShutdownIndexFile extends Thread {

    IndexFileReplicator file;

    public ShutdownIndexFile(IndexFileReplicator ifl) {
      file = ifl;
    }

    // This is called by the RunTime.shutdown at any termination 
    @Override
    public void run() {
      shuttingDown = true;
      try {
        if (dbg) {
          Util.prta("IFR: Shutdown IndexFileRep=" + file.toString() + " " + toString());
        }
        if (zeroer != null) {
          zeroer.shutdown();
        }
        //IndexBlock.closeJulian(file.getJulian());
        file.close();
      } catch (IOException e) {
        Util.prta("IFR:ShutdownIndexFile IOException file=" + file.toString() + " " + e.getMessage());
      }
    }
  }

  /**
   * this inner class is a thread that keeps zeroing data blocks ahead of the next_extent pointer.
   * This way the zeroing writes are spread out through the day. It also extends the file by the
   * DEFAULT_EXTEND_BLOCKS if the zeroing would go over the end of the file. It also implements a
   * short term queue so that data blocks that need to be written after the current zero and be
   * queued up for writing after the zeros has done its thing
   */
  public final class ZeroDataFile extends Thread {

    final RawDisk zero;         // local copy of the datafile RawDisk from out class
    long tick;            // Count the ticks of this Thread
    int lastZero;         // Last block group zeroed (actually its the next block to zero)
    long lastEmail;
    ArrayList<ZeroQueue> q;
    //boolean terminate;
    int nwrites;
    String localfilename;
    boolean shutdown;
    int state = -1;
    int zeroState = -1;
    private StringBuilder tmpsb = new StringBuilder(100);
    private StringBuilder runsb = new StringBuilder(100);

    public int getNwriteBehind() {
      return nwrites;
    }

    private void shutdown() {
      shutdown = true;
    }

    @Override
    public final String toString() {
      return filename + "/" + julian + " blk=" + lastZero + " st/cst/zst=" + state + "/" + closeState + "/" + zeroState;
    }

    //public void terminate() {terminate=true; interrupt();}
    public ZeroDataFile(RawDisk rw) throws FileNotFoundException {
      zero = rw;
      if (zero != null) {
        localfilename = zero.getFilename();
      }
      q = new ArrayList<>(100);
      tick = 0;
      if (dbg) {
        Util.prta(Util.clear(tmpsb).append("ZeroData: cons index ").append(filename).append(" ").
                append(toString()).append(" first hi=").append(highwater).
                append(" state=").append(state).append(" closeState=").append(closeState));
      }
      lastZero = highwater;
      if (lastZero < extendSize) {
        lastZero = extendSize;  // on very new files, prevent bogus warnings
      }
      // If the files is very short, make it a small minimum length and start the zeroer
      try {
        if (zero.length() / 512L < 5000) {
          zero.setLength(5000 * 512L);
        }
      } catch (IOException e) {
        Util.prta(Util.clear(tmpsb).append("ZeroData:zeroer cannot extend file length! ").
                append(e.getMessage()).append(" ").append(filename).append("/").append(julian));
      }
      // If the file is not the correct length, make it the right one
      try {
        if (zero.length() / 512L > length) {
          length = (int) (dataFile.length() / 512L);
        } else {
          zero.setLength(length * 512L);
        }
        if (dbg) {
          Util.prta(Util.clear(tmpsb).append("ZeroData: set correct length=").append(length).
                  append(" ").append(filename).append("/").append(julian));
        }
      } catch (IOException e) {
        Util.prta("ZeroData:zeroer cannot extent file length! " + e.getMessage() + " " + filename + "/" + julian);
        if (e.getMessage() != null) {
          if (e.getMessage().contains("No space")) {
            SendEvent.edgeEvent("NoSpaceLeft", "No space left " + dataFile.getFilename() + " on " + IndexFile.getNode(), (ZeroDataFile) this);
          } else {
            SendEvent.debugEvent("ZeroerErr", e.getMessage() + " on " + dataFile.getFilename() + " on " + IndexFile.getNode(), (ZeroDataFile) this);
          }
        }
      }
      state = 1;
      setDaemon(true);
      setPriority(1);
      if (dbg) {
        gov.usgs.anss.util.Util.prta("new Thread " + getName() + " " + getClass().getSimpleName() + " " + filename + " " + localfilename);
      }
      start();
    }

    /**
     * Used by outer class to insure new extents not issued before they are zeroed!
     *
     * @return The last block zero(actually the next block to zero)
     */
    public int getLastZero() {
      return lastZero;
    }

    /**
     * this thread sleeps 50 milliseconds and calls the doZero routine to keep the zeroer 2000
     * blocks ahead of the next_extent
     */
    @Override
    public void run() {
      long lastIndicesUpdate = System.currentTimeMillis();
      boolean done = false;
      int loop = 0;
      // When we start we do not know if the high water estimate is very good, do not start
      // zeroing until we get an update of the control block
      int initCtl = ctlUpdates;
      while (initCtl == ctlUpdates && !shutdown && !terminate) {
        try {
          sleep(20);
        } catch (InterruptedException e) {
        }
      }
      if (shutdown) {
        done = true;
      }
      lastZero = ((highwater + 128) / 64) * 64;
      Util.prta(Util.clear(runsb).append("ZeroData: start zero index ").append(filename).append(" ").
              append(toString()).append(" hi=").append(highwater).append(" init zeroer=").append(lastZero).
              append(" q.size()=").append(q.size()));
      state = 2;
      while (!done) {        // Loop until termination
        state = 3;
        try {
          doZero();      // if true, some work was done
          state = 4;
          try {
            sleep(50);
          } catch (InterruptedException e) {
          }
          state = 5;
          tick++;
          //Util.prt("zeroDataFile tick="+tick);
          if (terminate) {
            done = true;
          }
          if (shutdown) {
            done = true;
          }
          if (q.size() > 0) {
            setPriority(6);
          } else {
            setPriority(1);
          }
          loop++;
          if ((loop++ % 20) == 0) {
            if ((System.currentTimeMillis() - lastIndicesUpdate) > 120000) {
              processIndexChecks();
              lastIndicesUpdate = System.currentTimeMillis();
            }
          }
        } catch (IOException e) {
          Util.prt(Util.clear(runsb).append("IOException in zeroer close and exit file=").append(filename).append(" ").append(e.toString()));
          e.printStackTrace();
          break;
        }
        state = 6;
      }
      state = 7;
      close();
      state = 8;
      //if(dbg) 
      Util.prta(Util.clear(runsb).append("ZeroData: zero write thread on ").append(filename).
              append(" ").append(localfilename).append(" is terminated=").append(terminate).
              append(" shutdown=").append(shutdown));
      terminate = false;
    }
    private int closeState = -1;

    /**
     * close up this thread - insures all queued blocks are written
     */
    private void close() {
      if (closeState > 0) {
        return;      // close has been called already
      }
      closeState = 1;
      //if(rw != null && dbg) 
      Util.prta(Util.clear(tmpsb).append("ZeroData: close ").
              append(rw == null ? "null" : rw.getFilename()).append(" q.size=").append(q == null ? "null" : q.size()));
      int loop = 0;
      long elapse = System.currentTimeMillis();
      while (q.size() > 0 && loop < 100) {
        try {
          closeState = 2;
          doZero();
          closeState = 3;
        } catch (IOException e) {
          break;
        }
        if (q.size() > 0) {
          Util.prt(Util.clear(tmpsb).append("ZeroData: close more to do ").
                  append(rw == null ? "null" : rw.getFilename()).append(" q.size=").append(q == null ? "null" : q.size()));
        }
        loop++;
        closeState = 4;
      }
      closeState = 5;
      try {
        sleep(1000);
      } catch (InterruptedException e) {
      }  // wait for run() to exit 
      shutdown();
      Util.prta(Util.clear(tmpsb).append("ZeroData: close completed. loop=").append(loop).
              append(" elapse=").append(System.currentTimeMillis() - elapse).append(" ms ").
              append(rw == null ? "null" : rw.getFilename()).
              append(" q.size=").append(q == null ? "null" : q.size()).append(" alive=").
              append(isAlive()).append(" ").append(toString()));
      closeState = 6;
    }

    /**
     * put a data block in the queue to be written when the highwater has caught up
     *
     * @param seed NNSSSSSCCCLL
     * @param buf Then 512 bytes of data
     * @param iblk The block number in the file
     * @param indexBlock the index block number that this block is to be written on
     * @param index
     */
    public synchronized void queue(String seed, byte[] buf, int iblk, int indexBlock, int index) {
      q.add(new ZeroQueue(seed, buf, iblk, indexBlock, index));
      if (q.size() % 10000 == 0) {
        Util.prta(Util.clear(tmpsb).append("ZeroData ZQ: queue size=").append(q.size()).append(" iblk=").append(iblk).
                append(" hi=").append(highwater).append(" lastz=").append(lastZero));
      }
      if (q.size() % 50000 == 0) {    // This is bad!
        SendEvent.edgeEvent("ZeroBigQueue", "Zero has " + q.size() + " in write back for " + rw.getFilename(), "ZeroData");
      }
    }

    private boolean doZero() throws IOException {
      int newlength;
      boolean returnValue = false;
      zeroState = 1;
      if (highwater > 75000001) {
        if (System.currentTimeMillis() - lastEmail > 900000) {
          SendEvent.edgeEvent("HiWaterOOR", "Hiwater unreasonable for file=" + filename + " "
                  + Util.getNode() + "/" + Util.getAccount() + " " + Util.getProcess() + " " + highwater
                  + "zeror> 75 Mb blocks (37.5 GB) set limit to 37.5 GB\n",
                  "IndexFileRep");
          lastEmail = System.currentTimeMillis();
        }
        highwater = 75000000;
      }
      // Do we need to zero more
      boolean adjustOK = true;
      long now = System.currentTimeMillis();

      // If we are starting up late, then the file will not be long enough, set it to the new high water to get zeroer going
      if (highwater > length) {
        length = highwater + extendSize;
        dataFile.setLength(length * 512L);
        Util.prt(Util.clear(runsb).append("ZeroData: setting to higher length based on highwater=").
                append(highwater).append(" new length=").append(length));
        lastZero = (highwater + 128) / 64 * 64;
      }
      // Make sure lastZero is above high water
      if (lastZero < highwater + 128) {
        lastZero = (highwater + 128) / 64 * 64;
      }

      if (highwater > lastZero - extendSize) {  // the use of DEFAULT_EXTEND_BLOCKS is arbitrary, but scales
        zeroState = 2;
        // while more needs zeroing
        while (highwater > lastZero - extendSize) {  // Zero to allocated end of file
          zeroState = 3;
          try {

            // If the next zero will extend the file, extend it by DEFAULT_EXTEND_BLOCKS
            while (highwater + extendSize > length || highwater > length) { // does the file need to be extended?
              if (now - lastExtendTime < 600000 && adjustOK) {
                zeroState = 4;
                extendSize = extendSize * 2;
                adjustOK = false;
                Util.prta(Util.clear(runsb).append("ZeroData: Extend ").append(rw.getFilename()).
                        append(" size increased to ").append(extendSize).append(" elapsed=").
                        append(now - lastExtendTime));
                if (extendSize > 320000) {
                  extendSize = 320000;
                }
              }
              newlength = length + extendSize;
              synchronized (zero) {    // need to not interfere with other readers/writers
                //Util.prta("ZeroData: start Extend "+rw.getFilename());
                dataFile.setLength(newlength * 512L);
                length = (int) (dataFile.length() / 512L);
              }
              Util.prta(Util.clear(runsb).append("ZeroData: Extend ").append(rw.getFilename()).
                      append(" to ").append(newlength).append(" now=").append(length).
                      append(" hi=").append(highwater).append(" zero=").append(lastZero).
                      append(" elapsed=").append(now - lastExtendTime).
                      append(" q.size=").append(q.size()));
              lastExtendTime = now;
            }
            zeroState = 5;
            // Write out zeros for new  extents
            int nblks = Math.min(IndexFile.ZERO_BUF_SIZE / 512, length - lastZero);
            zero.writeBlock(lastZero, IndexFile.zerobuf, 0, nblks * 512);
            Util.prta(Util.clear(runsb).append("ZeroDataFile: ").append(rw.getFilename()).
                    append(" zero=").append(lastZero).append(" hiwater=").append(highwater).
                    append(" (").append(lastZero - highwater).append(") length=").append(length));
            //highwater=1000000;// force big zeroing to test speed */
            lastZero += nblks;
            zeroState = 6;
            try {
              sleep(20);
            } catch (InterruptedException e) {
            }
          } catch (IOException e) {
            if (e != null) {
              if (e.getMessage() != null) {
                if (e.getMessage().contains("No space left")) {
                  SendEvent.edgeEvent("NoSpaceLeft", "No space left in zero for file=" + zero.getFilename(), this);
                } else if (e.getMessage() != null) {
                  if (e.getMessage().contains("close")) {
                    Util.prta("ZeroerError file " + (zero == null ? "null" : zero.getFilename() + " closed"));
                  }
                } else {
                  SendEvent.debugEvent("ZeroerErr", "IFR zeroer IOError=" + (zero == null ? "null" : zero.getFilename()), this);
                  Util.IOErrorPrint(e, "ZeroData: file=" + (zero == null ? "null" : zero.getFilename()));
                }
              }
            }
            throw e;
          }
        }
        zeroState = -2;
        returnValue = true;
      }
      /**
       * we did not need to do any zeros. Now write out any back queued data that might have
       * accumulated while the zeroing was going on
       */
      int nblk = 0;
      zeroState = 7;
      while (q.size() > 0) {
        ZeroQueue zq = (ZeroQueue) q.get(0);    // get an element
        int block = zq.getBlock();
        zeroState = 8;
        if (block > highwater) {
          Util.prt(Util.clear(runsb).append("ZeroData: highwater=").append(highwater).
                  append(" less than write back block=").append(block).append(" set highwater length=").append(length));
          highwater = block;
          if (highwater > length) {
            length = highwater + extendSize;
            dataFile.setLength(length * 512);
            Util.prt(Util.clear(runsb).append("ZeroData: setting to higher length based on highwater=").
                    append(highwater).append(" new length=").append(length));
          }
        }
        if (block >= lastZero) {
          break;          // if it is not near the last zero, wait to write
        }
        try {
          writeDataBlock(zq.getSeedname(), zq.getData(), block, zq.getIndexBlock(), zq.getIndex(), false);
          nwrites++;
          //dataFile.writeBlock(block, zq.getData(), 0, 512);
          //Util.prta("ZQ: write queue iblk="+block+" "+lastZero+" sz="+q.size()+toString());
          nblk++;
          if (nblk > 50) {
            break;           // Limit how many to write in one cycle
          }
        } catch (IllegalSeednameException e) {
          Util.prt(Util.clear(runsb).append("ZeroData: write back gave Illegal seedname. skip zq.seed=").
                  append(zq.getSeedname()).append(" blk=").append(block).append(" indBlk=").
                  append(zq.getIndexBlock()).append(" ind=").append(zq.getIndex()));
        } catch (IOException e) {
          Util.prt(Util.clear(runsb).append("ZeroData: IOException writing queued elements").
                  append(toString()).append(" ").append(e.getMessage()));
        }
        q.remove(zq);
        zeroState = 9;
      }
      if (nblk > 0) {
        Util.prta(Util.clear(runsb).append("ZeroData: ").append(rw.getFilename()).
                append(" wrote ").append(nblk).append(" queued after blocks.  remaining ").append(q.size()));
      }
      zeroState = -1;
      return returnValue;
    }

    public long getTick() {
      return tick;
    }
  }     // end of class ZeroDataFile

  /**
   * this class stores one block of data for the queue in the zero for data that needs to be written
   * behind.
   */
  class ZeroQueue {

    int iblk;
    byte[] buf;
    String seedname;
    int indexBlock, index;

    public ZeroQueue(String seed, byte[] b, int blk, int indblk, int ind) {
      iblk = blk;
      seedname = seed;
      indexBlock = indblk;
      index = ind;
      buf = new byte[512];
      System.arraycopy(b, 0, buf, 0, 512);
    }

    public String getSeedname() {
      return seedname;
    }

    public int getIndexBlock() {
      return indexBlock;
    }

    public int getIndex() {
      return index;
    }

    public int getBlock() {
      return iblk;
    }

    public byte[] getData() {
      return buf;
    }

  }

  public static void main(String[] args) {
    String node = "8";
    int julian = 2454384;
    IndexFileReplicator idx = null;
    EdgeProperties.init();
    Util.prtProperties();
    Util.setModeGMT();
    Util.prt("saved=" + IndexFileReplicator.deleteJulianLimit(20));
    try {
      idx = new IndexFileReplicator(julian, node.trim(), 0, false, true);
    } catch (FileNotFoundException e) {
      Util.prta("EBS-EBRQST: rqst file not on server jul=" + julian + "_" + node);
    } catch (IOException e) {
      Util.prta("EBRQST:  error rqst opening IFR for file jul=" + julian + " node=" + node);
      e.printStackTrace();
    } catch (EdgeFileReadOnlyException e) {
      Util.prt("EBRQST: Impossible rqst edgefile read only when opening IFR jul=" + julian + " node=" + node + e.getMessage());
    } catch (EdgeFileDuplicateCreationException e) {
      Util.prta("EBRQST: got duplicate e=" + e);
    }
  }
}
