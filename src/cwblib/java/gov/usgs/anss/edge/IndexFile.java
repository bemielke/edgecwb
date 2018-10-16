/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */

package gov.usgs.anss.edge;

import gov.usgs.alarm.SendEvent;
import gov.usgs.anss.util.FreeSpace;
import gov.usgs.anss.util.SeedUtil;
import gov.usgs.anss.util.Subprocess;
import gov.usgs.anss.util.Util;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.*;
import gnu.trove.map.hash.TLongObjectHashMap;
import gnu.trove.iterator.TLongObjectIterator;

/**
 * This class is the exclusive write manager of the .idx portion of the edge file. It is responsible
 * for allocating master blocks and index blocks from its body and keeping the control portion and
 * the master blocks up to date (index blocks themselves are written by others). This class also
 * hands out extents in the "data" file and keeps the data file unused portion zeroed in from of the
 * allocated blocks. It also has static fields representing the parsing of the edge*.prop file
 * properties (npath, datapath?, nday?, extendsize, daysize)
 *
 * The control portion must be updated : 1) when a new extent is granted (so the next_extent
 * variable is updated) 2) When a new Master block is allocated (so the mBlocks array is complete
 * and the next_index is correct) 3) when a new index block is allocated (so the next index is
 * complete)
 *
 * The master blocks are updated through the master block objects in mb[]. Basically everything is
 * triggered by requests for more index blocks by the using routines.
 * <pre>
 * Control block :
 * int length;          // size of the file int 512 byte blocks
 * int nextextent;      // next block in .ms file to be allocated for a 64 block extent
 * short next_index;    // the block number of the next unallocated block in the index
 * short[] master_blocks;// The list of blocks in this file which are master blocks (250 max)
 *
 * Master Block:
 *  The physical form is :
 * 12 Character seedname
 * short starting_index   // block number with the first index for this channel
 * Short last_index block // block number of the last index for this channel
 * ...
 * repeat 32 times.
 *
 * Index Block :
 * seedname 12 characters NNSSSSSCCCLL format
 * int next_index;       Block number of index block chained after this one
 * int time              last update time in seconds since 1970
 * repeating part (30 times)
 * int  starting_block   Block number in .ms file for this extent
 * long bitmap           Each block in the extent is marked busy with a bit set here block 0 = bit 0, block1=bit 1
 * short earliest_time   Seconds since beginning of day / 3.
 * short latest_time     Seconds since beginiing of day / 3.
 * ...
 * repeat 30 times
 * </pre>
 *
 * @author davidketchum
 */
public class IndexFile implements Comparable {

  private static final StringBuilder CONTROLBLK = new StringBuilder().append("CONTROLBLK  ");
  private static final TLongObjectHashMap<IndexFile> files = new TLongObjectHashMap<>();
  private static int MAX_OPEN_FILES_BEFORE_PURGE = 500;
  /**
   * From edge*.prop the default starting size of new files(daysize)
   */
  protected static int DEFAULT_LENGTH = 20000;        // in blocks
  /**
   * From edge*.prop the default extend size in blks (extendsize)
   */
  protected static int DEFAULT_EXTEND_BLOCKS = 4000;  // in blocks
  /**
   * From edge_.prop the size of the zero buffer is the DEFAULT_EXTEND_BLOCKS in bytes
   */
  protected static int ZERO_BUF_SIZE;
  public static final int MAX_MASTER_BLOCKS = 250;
  /**
   * From edge*.prop the Number of data paths (npath)
   */
  protected static int NPATHS;
  /**
   * From edge*.prop the NPATHS sized array of strings to the paths (datapath?)
   */
  protected static String[] DATA_PATH;
  /**
   * From edge*.prop the Number of days for each path - for CWB set [0] to a large number and others
   * to zero (nday?)
   */
  protected static int[] NDAYS_PATH;
  /**
   * From edge*.prop the Total number of days represented by the paths - used in Edge Mode where
   * each path gets a fixed number of days
   */
  protected static int NDAYS = -1;
  /**
   * From edge*.prop the data directory submask YYYY_DDD shorted how ever wanted after the year
   */
  protected static String DATA_MASK;
  private static int fileRecordCount = 0;
  private static String node;                     // The computer node we are on
  protected static StringBuilder paths = new StringBuilder(100);
  private static boolean noZeroer;
  static String whoHas;

  /**
   * the default length of the data file in blocks
   */
  private String filename;
  private int recordNumber;     // This gives a small number to each instantiated IndexFile
  private int extendSize;       // Starts at DEFAULT_EXTEND_BLOCKS but gets bigger if file is growing rapidly
  private long lastExtendTime;  // Track the time of the last extension of the file
  private boolean readOnly; // If true, allow only read access to file
  private RawDisk rw;
  private RawDisk dataFile; // Access to the data file so zeroer can use it!
  private byte[] ctlbuf;   // The buffer wrapping the ctl ByteBuffer
  private ByteBuffer ctl;  // The header portion of this file
  private final int julian;       // The julian day represented by this file
  private final String maskStub;
  private ShutdownIndexFile shutdown;
  private static boolean shuttingDown; // set true in shutdown routine - cause different actions in closeer

  // These form the ctl block portion of the file
  private int length;       // length of the controlled data file in blocks
  private int next_extent;  // The next extent to allocate in controlled data file
  private int next_index;   // This is an unsigned representation of a short
  //private short next_index; // next index (or master block) to allocate from this file
  private int mBlocks[];  // array of blocks currently master_blocks, 0=unallocated (unsigned short)
  //private short mBlocks[];  // array of blocks currently master_blocks, 0=unallocated
  private int maxIndex;

  // These are the master blocks as master Block objects
  private MasterBlock mb[];

  public static byte[] zerobuf;
  private ZeroDataFile zeroer;
  private CloseThread closer;
  private boolean terminate = false;
  private static boolean dbg;

  public int getMaxIndex() {
    return maxIndex;
  }

  public void setMaxIndex(int i) {
    maxIndex = i;
  }

  public static void setNoZeroer(boolean t) {
    noZeroer = t;
  }

  //public static void setZeroerBlocksAhead(int i) {ZERO_BLOCKS_AHEAD=i;}
  public static String getMonitorString() {
    return "NumberIndexFiles=" + (files == null ? "0" : files.size()) + "\n";
  }

  public static void setMaxFilesBeforePurge(int n) {
    IndexFile.MAX_OPEN_FILES_BEFORE_PURGE = n;
  }

  /**
   * this is only used in RebuildIndex to force the next_extent to be set correctly
   *
   * @param i
   */
  protected void setNextExtentOnly(int i) {
    if (i > next_extent) {
      next_extent = (i & ~0x3f) + 64;
    }
    if (zeroer != null) {
      zeroer.shutdown();
      zeroer = null;
    }
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
   * get the rawdisk variable for using the index file
   *
   * @return A RawDisk object for I/O to this index file
   */
  public RawDisk getRawDisk() {
    return rw;
  }

  /**
   * set the rawdisk variable for using the index file (used by things rebuilding indices mainly
   *
   * @param r The raw disk to set
   */
  public void setRawDisk(RawDisk r) {
    rw = r;
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
   * return next_index block number to assign
   *
   * @return the next_index block number
   */
  public int getNextIndex() {
    return (int) next_index;
  }

  /**
   * a string representing this index file
   *
   * @return the filename/julian and ro=True/False
   */
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
      sb.append(filename).append("/").append(julian).append(" ro=").append(readOnly);
    }
    return sb;
  }
  private final StringBuilder tmpsb = new StringBuilder(50);

  /**
   * a string representing this index file
   *
   * @return the filename/julian and ro=True/False
   */
  public StringBuilder toStringFull() {
    long now = System.currentTimeMillis();
    long lastUsed = now - rw.getLastUsed();
    if ((now - dataFile.getLastUsed()) > lastUsed) {
      lastUsed = now - dataFile.getLastUsed();
    }
    synchronized (tmpsb) {
      Util.clear(tmpsb).append(filename).append("/").append(julian).append(" ro=").append(readOnly).
              append(" last used=").append(lastUsed / 1000).append(" sec idx: #rd=").append(rw.getNread()).
              append(" wr=").append(rw.getNwrite()).append(" data: #rd=").append(dataFile.getNread()).
              append(" #wr=").append(dataFile.getNwrite());
    }
    return tmpsb;
  }

  /**
   * get the array of master block objects for this index file
   *
   * @return Array of MasterBlock objects for this index file
   */
  public MasterBlock[] getMasterBlocks() {
    return mb;
  }

  /**
   * return the read only flag
   *
   * @return True if file was opened as readonly
   */
  public boolean getReadonly() {
    return readOnly;
  }

  public void setReadonly(boolean t) {
    readOnly = t;
  }

  /**
   * Creates a new instance of IndexFile
   *
   * @param jul The julian date of the desired file
   * @param init if true this is a new file
   * @param ro If true, this Index file can only read (disables all allocation functions)
   * @throws FileNotFoundException if init is false and file is not found!
   * @throws EdgeFileDuplicateCreationException If init is true and file is readonly, non-sense!
   * @throws EdgeFileReadOnlyException If init is true and file is readonly, non-sense!
   * @throws IOException Generally related to reads and writes failing
   */
  public IndexFile(int jul, boolean init, boolean ro)
          throws FileNotFoundException, EdgeFileReadOnlyException, IOException, EdgeFileDuplicateCreationException {
    synchronized (files) {
      int[] ymd;
      julian = jul;
      ymd = SeedUtil.fromJulian(julian);
      int doy = SeedUtil.doy_from_ymd(ymd[0], ymd[1], ymd[2]);
      maskStub = makeMaskStub(ymd[0], doy, DATA_MASK);
      filename = SeedUtil.fileStub(ymd[0], doy) + "_" + getNode();
      Util.prta(Util.clear(tmpsb).append("IndexFile:  cons ").append(filename).append("/").append(julian).
              append(" init=").append(init).append(" ro=").append(ro));
      if (shuttingDown) {
        Util.prt("Shutting down - no new Index files");
        return;
      }
      openIndexFile(filename, init, ro);
    }
  }

  public static String makeMaskStub(int year, int doy, String mask) {
    if (mask == null) {
      return "";
    }
    if (mask.length() < 4) {
      return "";
    }
    String tmp = Util.df4(year) + "_" + Util.df3(doy);
    return tmp.substring(0, mask.length());
  }

  /**
   * Creates a new instance of IndexFile (normal used for read only access on non edge) The filename
   * will be parsed to create a julian and any extension will be stripped off before calling
   * openIndexFile().
   *
   * @param file A absolute path to the index file to open
   * @param init if true this is a new file
   * @param ro If true, this Index file can only read (disables all allocation functions)
   * @param jul Force this file to be index as this julian data, this is a hack for use by
   * RebuildIndex
   * @throws FileNotFoundException if init is false and file is not found!
   * @throws EdgeFileReadOnlyException If init is true and file is readonly, non-sense!
   * @throws EdgeFileDuplicateCreationException If init is true and file is readonly, non-sense!
   * @throws IOException Generally related to reads and writes failing
   */

  public IndexFile(String file, boolean init, boolean ro, int jul)
          throws FileNotFoundException, EdgeFileReadOnlyException, IOException, EdgeFileDuplicateCreationException {
    synchronized (files) {   // prevents multiple creations of the same file!
      filename = file;
      julian = jul;
      int[] ymd = SeedUtil.fromJulian(julian);
      int doy = SeedUtil.doy_from_ymd(ymd[0], ymd[1], ymd[2]);
      maskStub = makeMaskStub(ymd[0], doy, DATA_MASK);
      int period = filename.indexOf(".");
      if (period >= 0) {
        filename = filename.substring(0, period);
      }
      openIndexFile(filename, init, ro);
    }
  }
  public static int getJulianFromFilename(String filename) throws FileNotFoundException {
     int lastSlash = filename.lastIndexOf("/");
      if (lastSlash < 0) {
        lastSlash = -1;
      }
      int year = 0;
      int doy = 0;
      try {
        year = Integer.parseInt(filename.substring(lastSlash + 1, lastSlash + 5));
        doy = Integer.parseInt(filename.substring(lastSlash + 6, lastSlash + 9));
      } catch (NumberFormatException e) {
        throw new FileNotFoundException(
                "IndexFile: NumberFormatException : filename is probably bad! " + filename);
      }
      return SeedUtil.toJulian(year, doy);
    
  }
  /**
   * Creates a new instance of IndexFile (normal used for read only access on non edge) The filename
   * will be parsed to create a julian and any extension will be stripped off before calling
   * openIndexFile().
   *
   * @param file A absolute path to the index file to open
   * @param init if true this is a new file
   * @param ro If true, this Index file can only read (disables all allocation functions)
   * @throws FileNotFoundException if init is false and file is not found!
   * @throws EdgeFileReadOnlyException If init is true and file is readonly, non-sense!
   * @throws EdgeFileDuplicateCreationException If init is true and file is readonly, non-sense!
   * @throws IOException Generally related to reads and writes failing
   */

  public IndexFile(String file, boolean init, boolean ro)
          throws FileNotFoundException, EdgeFileReadOnlyException, IOException, EdgeFileDuplicateCreationException {
    synchronized (files) {   // prevents multiple creations of the same file!
      filename = file;
      julian = getJulianFromFilename(filename);
      int [] ymd = SeedUtil.fromJulian(julian);
      int doy = SeedUtil.doy_from_ymd(ymd);
      maskStub = makeMaskStub(ymd[0], doy, DATA_MASK);
      //if(lastSlash >=0) filename=filename.substring(lastSlash+1);
      int period = filename.indexOf(".");
      if (period >= 0) {
        filename = filename.substring(0, period);
      }
      openIndexFile(filename, init, ro);
    }
  }

  
    /**
   * Creates a new instance of IndexFile (normal used for read only access on non edge) The filename
   * will be parsed to create a julian and any extension will be stripped off before calling
   * openIndexFile().
   *
   * @param file A absolute path to the index file to open
   * @param init if true this is a new file
   * @param ro If true, this Index file can only read (disables all allocation functions)
   * @param rawidx User supplied raw disk to the index
   * @param rawms User supplied raw disk to the miniSeed file
   * @throws FileNotFoundException if init is false and file is not found!
   * @throws EdgeFileReadOnlyException If init is true and file is readonly, non-sense!
   * @throws EdgeFileDuplicateCreationException If init is true and file is readonly, non-sense!
   * @throws IOException Generally related to reads and writes failing
   */

  public IndexFile(String file, boolean init, boolean ro, RawDisk rawidx, RawDisk rawms)
          throws FileNotFoundException, EdgeFileReadOnlyException, IOException, EdgeFileDuplicateCreationException {
    synchronized (files) {   // prevents multiple creations of the same file!
      filename = file;
      julian = getJulianFromFilename(filename);
      int [] ymd = SeedUtil.fromJulian(julian);
      int doy = SeedUtil.doy_from_ymd(ymd);
      maskStub = makeMaskStub(ymd[0], doy, DATA_MASK);
      int period = filename.indexOf(".");
      if (period >= 0) {
        filename = filename.substring(0, period);
      }
      openIndexFile(filename, init, ro, rawidx, rawms);
    }
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
   * get the files tree (normally used to synchronize file opens)
   *
   * @return the files index (users should not be allowed to modify this
   */
  //static public Integer getFilesLock(){return filesLock;}
  /**
   * get IndexFile associated with a given day, This routine enforces the open days rules and
   * returns a pointer to "future.idx" or "past.idx" if the day is out of range
   *
   * @param julian The julian date of the file desired.
   * @return The index file or null if none found
   */
  static public IndexFile getIndexFile(int julian) {
    if (NDAYS <= 0) {
      init();
    }
    IndexFile idx;
    synchronized (files) {      // do not allow multiple creates of the same file!
      whoHas = "getIdxFile : " + julian;
      idx = files.get(julian);
    }
    return idx;
  }

  public static String getPathString() {
    return paths.toString();
  }

  /**
   * get Filename for the given julian day on this node
   *
   * @param jul The julian day desired
   * @return filename for the given julian day on this node
   */
  static public String getFilename(int jul) {
    return getIndexFile(jul).getFilename();
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
    init();
    return DATA_PATH[i];
  }

  /**
   * return the number of days allowed in open day files
   *
   * @return THe number of days allowed to be open
   */
  static public int getNdays() {
    init();
    return NDAYS;
  }

  /**
   * return number of paths
   *
   * @return the number of paths
   */
  static public int getNPaths() {
    return NPATHS;
  }

  /**
   * get a number of days for the indexed path
   *
   * @param i The index of the desired path
   * @return the number of days on index path (zero if out of range)
   */
  static public int getNdays(int i) {
    return NDAYS_PATH[i];
  }

  /**
   * force a reload of the paths etch from the currrent properties
   *
   */
  static public void forceinit() {
    NDAYS = 0;
    init();
  }

  /**
   * This does all of the one-time static set up, create tree, read properties, create zerobuf
   */
  static public void init() {
    if (NDAYS <= 0) {
      //filesLock = new Integer(0);     // This is more stable to lock on than files.
      if (Util.getProperty("daysize") == null) {
        DEFAULT_LENGTH = 20000;
      } else {
        DEFAULT_LENGTH = Integer.parseInt(Util.getProperty("daysize").trim());
      }
      if (Util.getProperty("extendsize") == null) {
        DEFAULT_EXTEND_BLOCKS = 2000;
      } else {
        DEFAULT_EXTEND_BLOCKS = Integer.parseInt(Util.getProperty("extendsize").trim());
      }
      DATA_MASK = Util.getProperty("datamask");
      ZERO_BUF_SIZE = Math.min(DEFAULT_EXTEND_BLOCKS, 40000) * 512;
      zerobuf = new byte[ZERO_BUF_SIZE];
      Arrays.fill(zerobuf, (byte) 0);

      // The number of data paths
      if (Util.getProperty("ndatapath") == null) {
        Util.prta(" ****** ndatapath is null - is the prop file missing or bad? *****");
      }
      if (Util.getProperty("nday") == null) {
        Util.prta(" ****** nday is null - is the prop file missing or bad? *****");
      }
      if (Util.getProperty("datapath") == null) {
        Util.prta(" ****** datapath is null - is the prop file missing? *****");
      }
      NPATHS = Integer.parseInt(Util.getProperty("ndatapath").trim());
      //if(dbg)
      Util.prta("IF: Ndatapaths=" + Util.getProperty("ndatapath") + " DEFAULT_LENGTH=" + DEFAULT_LENGTH
              + " EXTEND_BLOCKS=" + DEFAULT_EXTEND_BLOCKS + " zero.len=" + zerobuf.length);

      // Now read in the number of days on each path and the paths, total #of days in NDAYS
      NDAYS = Integer.parseInt(Util.getProperty("nday").trim()); // First # of days (maybe only)
      NDAYS_PATH = new int[NPATHS];
      NDAYS_PATH[0] = NDAYS;
      DATA_PATH = new String[NPATHS];
      DATA_PATH[0] = Util.getProperty("datapath").trim();
      paths.append("Npath=").append(NPATHS).append(" 0 nday=").append(NDAYS_PATH[0]).append(" path=").append(DATA_PATH[0]).
              append(" datamask=").append(DATA_MASK);

      if (NPATHS > 0) {
        for (int i = 1; i < NPATHS; i++) {
          if (Util.getProperty("nday" + i) == null || Util.getProperty("datapath" + i) == null) {
            SendEvent.edgeEvent("BadEdgeProp", "for path=" + i + " ndays=" + Util.getProperty("nday" + i)
                    + " path=" + Util.getProperty("datapath" + i), "IndexFile");
            if (Util.getProperty("datapath" + i) == null) {
              DATA_PATH[i] = "/data/";
            }
            Util.prta(i + " IF: ***** either ndays=" + Util.getProperty("nday" + i)
                    + " or data path=" + Util.getProperty("datapath" + i) + " is NULL!");
            continue;
          }
          try {
            NDAYS_PATH[i] = Integer.parseInt(Util.getProperty("nday" + i).trim());
          } catch (NumberFormatException e) {
            SendEvent.edgeEvent("BadEdgeProp", "** The property for datapath"+i+" did not parse!", "IndexFile");
          }
          NDAYS += NDAYS_PATH[i];
          DATA_PATH[i] = Util.getProperty("datapath" + i);

          Util.prta(i + " IF: nday=" + NDAYS_PATH[i] + " path=" + DATA_PATH[i]);
          paths.append(i).append(" nday=").append(NDAYS_PATH[i]).append(" path=").append(DATA_PATH[i]);
        }
      }
      if (dbg) {
        Util.prta("IndexFiles.init() files hash=" + files.hashCode());
      }

    }
  }

  private void openIndexFileWithMask(String name, boolean init, boolean ro) {
    // open an existing file or new file
  }

    // This is only called from constructors so it does not need to be synchonized on (this)
  private void openIndexFile(String name, boolean init, boolean ro)
          throws FileNotFoundException, EdgeFileReadOnlyException, IOException,
          EdgeFileDuplicateCreationException {
    openIndexFile(name,init, ro, null, null);
    
  }
  // This is only called from constructors so it does not need to be synchonized on (this)
  private void openIndexFile(String name, boolean init, boolean ro, RawDisk rawidx, RawDisk rawms)
          throws FileNotFoundException, EdgeFileReadOnlyException, IOException,
          EdgeFileDuplicateCreationException {
    if (shuttingDown) {
      return;
    }
    init();
    /*if(DATA_MASK != null) {
      openIndexFileWithMask(name, init, ro);
      return;
    }*/
    extendSize = DEFAULT_EXTEND_BLOCKS;
    if (getIndexFile(julian) != null) {
      throw new EdgeFileDuplicateCreationException("Duplicate file for julian=" + julian);
    }
    recordNumber = fileRecordCount++;
    shutdown = new ShutdownIndexFile(this);
    Runtime.getRuntime().addShutdownHook(shutdown);

    // Set up the variables
    readOnly = ro;
    ctlbuf = new byte[512];
    ctl = ByteBuffer.wrap(ctlbuf);
    mBlocks = new int[MAX_MASTER_BLOCKS];   // space for master block numbers
    mb = new MasterBlock[MAX_MASTER_BLOCKS];  // space for master block objects

    int ipath = 0;
    // Check the NDAYS[0], if it is large, then use CWB type open to find path (max free space)
    // if < 1000, then open by the fixed days modulo and reuse files if found. ##B5DF784H TQH26JAT
    if (NDAYS_PATH[0] > 1000) {
      boolean exists = false;

      if (NPATHS > 1) {
        // look through the paths for this file or an empty.
        for (int i = 0; i < NPATHS; i++) {
          String path = DATA_PATH[i];
          File dir = new File(path.substring(0, path.length() - 1));
          if (dbg) {
            Util.prta(Util.clear(tmpsb).append("IndexFileRep: path=").append(path).
                    append(" dir=").append(dir.toString()).append(" isDir=").append(dir.isDirectory()));
          }
          String[] filess = dir.list();
          if (filess == null) {
            SendEvent.debugEvent("IFRFileOpen", "Failed to open files on path=" + path, "IndexFileReplicator");
            Util.prta(Util.clear(tmpsb).append("Attempt to open files on path=").append(path).
                    append(" dir=").append(dir.toString()).append(" failed.  Is VDL denied access to path? Too many Files open?"));
          } else {
            for (String files1 : filess) {
              // Are we using the DATA_MASK, if so check for directories that match
              if (files1.equals(maskStub) && !maskStub.equals("")) {   // yes, we found the directory
                File dir2 = new File(path + maskStub);
                String[] filess2 = dir2.list();
                for (String files2 : filess2) {
                  if (files2.contains(filename + ".ms")) {
                    ipath = i;
                    exists = true;
                    Util.prta(Util.clear(tmpsb).append("IndexFile: found maskStub ").append(name).
                            append(" on ").append(DATA_PATH[ipath]).append(maskStub).append("/"));
                    break;
                  }
                }
              }
              if (files1.indexOf(".ms") > 0) {
                if (dbg) {
                  Util.prta(Util.clear(tmpsb).append(files1).append(" to ").append(filename));
                }
                if (files1.contains(filename + ".ms")) {
                  ipath = i;
                  exists = true;
                  Util.prta(Util.clear(tmpsb).append("IndexFile: found ").append(name).append(" on ").append(DATA_PATH[ipath]));
                  break;
                }
              }
            }
          }
        }
        if (!exists) {
          int maxfree = 0;
          init = true;
          for (int i = 0; i < NPATHS; i++) {
            String path = DATA_PATH[i];
            File dir = new File(path.substring(0, path.length() - 1));
            String[] filess = dir.list();
            if (filess.length > 0) {
              if (filess[0].contains("lost") && filess.length > 1) {
                filess[0] = filess[1];// do not use lost and found
              }
            }
            // if its an empty directory use the directory
            if (filess.length == 0 || (filess.length == 1 && filess[0].contains("lost"))) {
              filess = new String[1];
              //filess[0]=path.substring(0,path.length()-1);
              filess[0] = "";
            }
            if (filess.length >= 1) {
              int free = FreeSpace.getFree(DATA_PATH[i] + filess[0]);
              Util.prta(Util.clear(tmpsb).append("IndexFile: Free space on ").
                      append(DATA_PATH[i]).append(filess[0]).append(" is ").append(free));
              if (free > maxfree) {
                ipath = i;
                maxfree = free;
              }
            }
          }
          Util.prta(Util.clear(tmpsb).append("IndexFile: new file on biggest freespace ").append(name).
                  append(" to ").append(DATA_PATH[ipath]).append(" free=").append(maxfree));
        }
      }
      if (name.contains("/")) {  // If the user gave an absolute path, just use it.
        filename = name;
      } else {  // For this julian day, figure out the path and add the file name
        filename = DATA_PATH[ipath] + (maskStub.equals("") ? "" : maskStub + "/") + name;
      }
      if (NPATHS <= 1) {     // Only one, need to see if it exists and set init
        File f = new File(filename + ".ms");
        exists = f.exists();
        init = !f.exists();
      }
      Util.prta(Util.clear(tmpsb).append("IndexFile: CWB open filename=").append(filename).
              append(" exists=").append(exists).append(" init=").append(init));
    } else {  // Not a CWB, open like an edge node
      // If its a fully qualified name, then leave it alone
      int cycle = julian % NDAYS;             // This is the day in the total cycle
      int sum = 0;
      for (ipath = 0; ipath < NPATHS; ipath++) {
        if (cycle >= sum && cycle < sum + NDAYS_PATH[ipath]) {
          break;
        }
        sum += NDAYS_PATH[ipath];
      }
      if (name.contains("/")) {   // If the user gave an absolute path, just use it.
        filename = name;// For this julian day, figure out the path and add the file name
      } else {
        filename = DATA_PATH[ipath] + name;
      }
      if (!readOnly) {

        // Compute which is the "previous" file in this cycle (NDAYS ago), if it is
        // found, rename it to this file so it will open using the preallocated file
        int[] ymd = SeedUtil.fromJulian(julian - NDAYS);
        int jday = SeedUtil.doy_from_ymd(ymd[0], ymd[1], ymd[2]);
        String previous = DATA_PATH[ipath] + SeedUtil.fileStub(ymd[0], jday) + "_" + getNode();
        try {

          // Look in files TreeMap for the one we are about to try to reuse, if found
          // Close it an take off TreeMap
          IndexFile old;
          synchronized (files) {
            //if(dbg)
            Util.prta(Util.clear(tmpsb).append("IndexFile: check on existing file ").append(filename).append("/").append(julian));
            whoHas = "OIF: " + filename + "/" + julian;
            old = files.get(julian - NDAYS);
          }

          if (old != null) {     // We found one
            //if(dbg)
            Util.prta(Util.clear(tmpsb).append("IndexFile: need to close existing ").append(filename).append("/").append(julian));
            old.close();        // we must be done with this file, note this removes it from tree map
          }

          // If the previous cycle file exists, rename it and then reuse it
          File prevFile = new File(previous + ".ms");
          File newFile = new File(filename + ".ms");
          //DEBUG: delete all files about to be opened
          /*if(Util.getOS().indexOf("Mac") >=0 ) {
            File newIDX = new File(filename+".idx");
            newFile.delete(); newIDX.delete();
          }*/
          if (prevFile.exists() && !readOnly && NDAYS_PATH[0] <= 1000) {
            //if(dbg)
            Util.prta(Util.clear(tmpsb).append("IndexFile: renaming ").append(previous).append(" to ").append(filename));
            prevFile.renameTo(newFile);           // rename file to new day
            prevFile = new File(previous + ".idx");
            newFile = new File(filename + ".idx");
            if (prevFile.exists()) {
              prevFile.renameTo(newFile);
            }
            init = true;                // This is a renamed fail so always init it
          } else {      // The previous does not exist, are we opening a brand new file
            if (dbg) {
              Util.prta(Util.clear(tmpsb).append("IndexFile: ").append(filename).append("/").
                      append(julian).append(" exists=").append(newFile.exists()).append(" init=").append(init));
            }
            if (!newFile.exists() && !readOnly) {
              init = true;        // its a new file
            }
          }
        } catch (SecurityException e) {
          Util.prta(Util.clear(tmpsb).append("Security exception renaming files=").append(filename).
                  append(" ").append(e.getMessage()));
        }
      }
    }

    // All of the above was to set the absolute path to the file in variable filename and whether to.
    // initialize it.   Now open the file and index based on this   
    String mode = "rw";
    if (readOnly) {
      mode = "r";
    }
    //if(dbg) 
    Util.prta(Util.clear(tmpsb).append("IndexFile: open ").append(filename).append(".idx init=").append(init).
            append("/").append(julian).append(" mode=").append(mode));
    if(rawidx == null) {
      rw = new RawDisk(filename + ".idx", mode);
    }
    else {
      rw = rawidx;
    }
    if (dbg) {
      Util.prta(Util.clear(tmpsb).append("IndexFile: open data ").append(filename).append(".ms init=").append(init).
              append("/").append(julian).append(" mode=").append(mode));
    }
    mode = "rw";
    if (readOnly) {
      mode = "r";
    }
    if(rawms == null) {
      dataFile = new RawDisk(filename + ".ms", mode);   // was always "rwd"
    }
    else {
      dataFile = rawms;
    }

    // This happened once - clearly a file must have been partway opened to have this happen!
    if (rw.length() == 0 && !init) {
      Util.prta(Util.clear(tmpsb).append("IndexFile try to open existing zero length file!  How can this happen! ").
              append(filename).append(" datalen=").append(dataFile.length()));
      SendEvent.debugEvent("IdxFileZero", "Index file " + filename + " was opened with zero length! Init it", this);
      if (dataFile.length() == 0) {
        init = true;
      } else {
        close();   // data file has been written to.  Just close up and try not to do any damage.
      }
    }
    if (init) {
      if (readOnly) {
        throw new EdgeFileReadOnlyException("Cannot init a readonly file");
      }
      for (int i = 0; i < MAX_MASTER_BLOCKS; i++) {
        mb[i] = null;
        mBlocks[i] = 0;

      }
      length = Math.max(DEFAULT_LENGTH, 1);

      next_index = 1;
      next_extent = 0;
      updateCtl(false);

      // Zero out the index file (especially if it being reused)
      int max = (int) (rw.length() / 512L);
      //if(dbg) 
      Util.prta(Util.clear(tmpsb).append("IndexFile: being inited ").append(filename).append("/").
              append(julian).append(" blks=").append(max));
      for (int ib = 1; ib < max; ib = ib + 64) {
        int nb = 64;
        if ((max - ib) < 64) {
          nb = max - ib;
        }
        rw.writeBlock(ib, zerobuf, 0, nb * 512);
      }
      updateCtl(true);      // Now that index is fully written, tell replicator about it.
      Util.prta("IndexFile: update ctrl 1st time");
      Util.prta(Util.clear(tmpsb).append("IndexFile: open new data file=").append(filename).
              append(" to length=").append(length).append(" zerbufsize=").append(ZERO_BUF_SIZE));
      dataFile.write(zerobuf, 0, zerobuf.length);

    } else {
      rw.readBlock(ctlbuf, 0, 512);      // get ctrl region
      fromCtlbuf();                       // Unpack the ctlbuf

      if (dataFile.length() / 512 != length) {
        if (dbg) {
          Util.prt(Util.clear(tmpsb).append("Read length does not match file length! file.length=").
                  append(dataFile.length() / 512).append(" length=").append(length));
        }
        length = (int) (dataFile.length() / 512L);
      }
      /*if(dbg)*/ Util.prta(Util.clear(tmpsb).append("Old IndexFile opened =").append(filename).
              append(" data length=").append(length).append(" blks. next_index=").append(next_index).
              append(" next_extent=").append(next_extent));

      // for each allocated MasterBlock create it as an object
      for (int i = 0; i < MAX_MASTER_BLOCKS; i++) {
        if (mBlocks[i] != 0) {
          mb[i] = new MasterBlock((int) mBlocks[i], this, false);
        } else {
          break;                       // exit when we are out of allocated blocks
        }
      }
    }

    // If writing is allowed, find the next_extent - remember it is lazily written
    // every 1000 blocks or so, so the end is somewhere in the next 1000
    if (!readOnly && !noZeroer) {
      zeroer = new ZeroDataFile(dataFile);
    }
    synchronized (files) {
      whoHas = "OIF: add " + filename + Util.fs + julian;
      files.put(julian, this);
      if (files.size() > MAX_OPEN_FILES_BEFORE_PURGE) {
        closeJulianLimit(15, 300000);
      }
    }
  }

  /**
   * Cause the master blocks to be re-read. Should only be done on readonly files.
   *
   * @throws EdgeFileReadOnlyException If one is generated by the reads
   * @throws IOException If one is generated by the reads of the master blocks.
   */
  public void reReadMasterBlocks() throws EdgeFileReadOnlyException, IOException {
    if (!readOnly) {
      throw new EdgeFileReadOnlyException("Should not re-read Master Blocks on writable file!:");
    }
    rw.readBlock(ctlbuf, 0, 512);      // get ctrl region
    fromCtlbuf();                       // Unpack the ctlbuf
    for (int i = 0; i < MAX_MASTER_BLOCKS; i++) {
      if (mBlocks[i] != 0) {
        mb[i] = new MasterBlock((int) mBlocks[i], this, false);
      } else {
        break;                       // exit when we are out of allocated blocks
      }
    }
  }

  /**
   * Return the Instance of this EdgeMom (this is the Property "Node") This should be of the form
   * n[#i] where n and i are small integers such that the length of n[#i] is no more than 4
   * characters
   *
   * @return The up to 4 character instance
   */
  public static String getInstance() {
    return getNode();
  }

  /**
   * Return the instance number, that is the part of the Node on Instance following the #. If no #,
   * then the instance number is zero (also if it cannot be decoded)
   *
   * @param nd The node string
   * @return The Instance number as a small integer value.
   */
  public static int getInstanceNumber(String nd) {
    //String nd=getNode();
    if (nd.contains("#")) {
      try {
        return Integer.parseInt(nd.substring(nd.indexOf("#") + 1));
      } catch (NumberFormatException e) {
        SendEvent.edgeSMEEvent("BadInstNum", "Bad instance number from node=" + nd, "IndexFile");
      }
    } else if (nd.contains("Q")) {
      try {
        return Integer.parseInt(nd.substring(nd.indexOf("Q") + 1));
      } catch (NumberFormatException e) {
        SendEvent.edgeSMEEvent("BadInstNum", "Bad instance number from node=" + nd, "IndexFile");
      }
    }

    return 0;
  }

  /**
   * Return the node number, that is the part of the Node on Instance prior to #. If no #, then the
   * instance number is zero (also if it cannot be decoded)
   *
   * @param nd THe node string
   * @return The Instance number as a small integer value.
   */
  public static int getNodeNumber(String nd) {
    //String nd=getNode();
    if (nd.contains("#")) {
      try {
        return Integer.parseInt(nd.substring(0, nd.indexOf("#")));
      } catch (NumberFormatException e) {
        SendEvent.edgeSMEEvent("BadNodeNum", "Bad node number from node=" + nd, "IndexFile");
      }
    } else if (nd.contains("Q")) {
      try {
        return Integer.parseInt(nd.substring(0, nd.indexOf("Q")));
      } catch (NumberFormatException e) {
        SendEvent.edgeSMEEvent("BadNodeNum", "Bad node number from node=" + nd, "IndexFile");
      }
    } else {
      try {
        int nodetmp = Integer.parseInt(nd);
        return nodetmp;
      } catch (NumberFormatException e) {
        SendEvent.edgeSMEEvent("BadNodeNum", "Bad Node number from node=" + nd, "IndexFile");
      }
    }
    return 0;
  }

  /**
   * This returns the computer node for adding to the file name, the first time it actually runs the
   * uname -n and parses, it sets the static variable node and that will be returned on all
   * successive calls.
   * <p>
   * If the property "Node" is known, this is used as the node unless the current systemname ends
   * with a number and this number does not match the number returned by the property. This case is
   * special at the USGS in that it indicates an "instance" has been moved from its original server
   * to a new one (hence the system number and node number no longer match). When this happens, the
   * names of files need to be modified so they do not clash and overwrite on any CWBs being
   * populated by Replication. When this situation occurs, the system number is increased by 50.
   * <p>
   * @return a tag with the edge node number (the string of digits a the end of the node)
   */
  public static String getNode() {
    if (node != null) {
      return node;
    }
    //new RuntimeException("Setting IndexFile: getNode()").printStackTrace();
    String s = null;
    if (Util.getProperty("Node") != null) {
      String instance = Util.getProperty("Instance");
      if (instance == null) {
        instance = Util.getProperty("Node"); // this makes it work the old way
      }
      node = Util.getProperty("Node");
      String originalNode = node;
      int instanceNodeNumber = getNodeNumber(instance);
      int instanceNumber = getInstanceNumber(instance);
      int systemNumber = getNodeNumber(node);
      String systemname = Util.getSystemName();

      Util.prta("IndexFile: getNode orignode=" + originalNode + " node=" + node + " instance=" + instance
              + " instanceNodeNumber=" + instanceNodeNumber + " instanceNumber=" + instanceNumber
              + " systemname=" + systemname + " system number=" + systemNumber);
      node = instance;
      if (systemNumber != instanceNodeNumber && systemNumber > 0) {        // Instance is running on a numbered system
        // If the system number does not equal the node number, this instance is running on a different node
        // Translate the node in IndexFile so any files created here are unique from original instance
        if (instanceNumber >= 10) {  // need two digits for instance, means system must be 1 digit
          Util.prta("IndexFile: getNode() *** Is wrapping with instance> 10 !  node=" + instanceNodeNumber + " system=" + systemNumber + " instance=" + instanceNumber);
          SendEvent.edgeSMEEvent("IdxNodeBadTag", "The instance " + node + " is not on right server, but node wrap might be bad", "IndexFile");
          node = (9 - instanceNodeNumber) + "#" + instanceNumber;
          Util.prta("IndexFile: getNode *** trying to protect itself with instanceNumber change " + node);

        } else {
          if (instanceNodeNumber + 50 > 99) {
            Util.prta("IndexFile: getNode() *** Is wrapping with node >69!  node=" + instanceNodeNumber + " system=" + systemNumber + " instanc=" + instanceNumber);
            SendEvent.edgeSMEEvent("IdxNodeBadTag", "The instance " + node + " is not on right server, but node wrap might be bad", "IndexFile");
            node = ((instanceNodeNumber + 50) % 100) + "#" + instanceNumber;
            Util.prta("IndexFile: *** getNode trying to protect itself with instanceNumber change " + node);
          }
          node = ((instanceNodeNumber + 50) % 100) + "#" + instanceNumber;
        }
        if (node.length() > 4) {
          Util.prta("IndexFile: getNode() ****  Is more than for digits node=" + node + " node=" + instanceNodeNumber + " system=" + systemNumber + " instance=" + instanceNumber);
          Util.exit("IndexFile");
        }
      }
      if (!node.equals(instance)) {
        Util.prta(" ***** Remapping node=" + instance + " to " + node);
      }
      Util.prta("IndexFile: getNode initialized=" + node);
      if (node.length() <= 4) {
        return node;
      }
      s = node;
      SendEvent.edgeSMEEvent("IdxFilBadNod", "IndexFile node is old style " + node
              + " Please convert to nn#n format", "IndexFile");
    }
    Util.prta("IndexFile:getNode() property=" + Util.getProperty("Node") + " node=" + node);
    try {
      if (s == null) {
        Subprocess sp = new Subprocess("uname -n");
        sp.waitFor();
        s = sp.getOutput();      // remember this might have stuff after the end
      }
      String orgs = s;
      s = s.trim();
      Util.prta("IndexFile: getNode() uname -n returned=" + s + " len=" + s.length());
      //new RuntimeException("IndexFile.getNode() using uname? " + node + " " + Util.getProperty("Node")).printStackTrace();
      int dot = s.indexOf(".");             // see if this is like edge3.cr.usgs.gov
      if (dot > 0) {
        s = s.substring(0, dot);    // Trim off to the first dot if any
      }
      int length;
      // find first non letter or digit and call this the length
      for (dot = 0; dot < s.length(); dot++) {
        if (!Character.isLetterOrDigit(s.charAt(dot)) && s.charAt(dot) != '-') {
          break;
        }
      }
      length = dot;
      // For all characters staring and length, look back for first non-digit
      for (dot = length - 1; dot >= 0; dot--) // From end, find first non-digit
      {
        if (!Character.isDigit(s.charAt(dot)) && s.charAt(dot) != 0) {
          break;
        }
      }

      if (dot > 0) {
        s = s.substring(dot + 1, length);     // return only the digit portion
      }
      node = s;
      if (node.equals("")) {
        if (Util.getProperty("Node") != null) {
          node = Util.getProperty("Node");
          int start = node.length() - 1;
          for (int i = node.length() - 1; i >= 0; i--) {
            if (Character.isDigit(node.charAt(i))) {
              start = i;
            } else {
              break;
            }
          }
          node = node.substring(start);
        } else {
          SendEvent.edgeSMEEvent("NoNodeNumber", "This node does not have a node number! " + s + " uname=" + orgs, "IndexFile");
          Util.prta("***** this node does not have a node number!!!! s=" + s + " uname=" + orgs);
        }
      }

      Util.prta("IndexFile: getNode() final=" + node);

      // Now see if there is more than one edgemom running!
      Subprocess sp = new Subprocess("whoami");
      sp.waitFor();
      String s2 = sp.getOutput().trim();
      if (s2.equals("reftek")) {
        node += "#19";
      }
      //s2="vdl1";
      for (dot = s2.length() - 1; dot >= 0; dot--) {
        if (!Character.isDigit(s2.charAt(dot)) && s2.charAt(dot) != 0) {
          break;
        }
      }
      if (dot < s2.length() - 1) {
        node += "#" + s2.substring(dot + 1, s2.length());
      }
      Util.prta("IndexFile: getNode() s2=" + s2 + " len=" + s2.length() + "dot=" + dot + " node=" + node + " s=" + s);
      return node;
    } catch (IOException e) {
      Util.prta("IndexFile: Cannot run uname -n or whoami e=" + e);
      e.printStackTrace();
      Util.exit(0);
    } catch (InterruptedException e) {
      Util.prta("IndexFile: uname -n interrupted!");
      e.printStackTrace();
      Util.exit(0);
    }
    return "---";
  }

  /**
   * allocate a new master block, auto updates the change ctl area
   *
   * @return the number of the newly created MasterBlock
   * @throws EdgeFileCannotAllocateException if the master block index is full (very rare as 250
   * master blocks must all ready have been allocated
   * @throws EdgeFileReadOnlyException If file was opend read only, you cannot get new blocks
   * @throws IOException Thown if an IO fails to the .idx file
   */
  public synchronized int newMasterBlock()
          throws EdgeFileCannotAllocateException, IOException, EdgeFileReadOnlyException {
    if (readOnly) {
      throw new EdgeFileReadOnlyException("Cannot ge new master block in readonly");
    }
    for (int i = 0; i < MAX_MASTER_BLOCKS; i++) {
      if (mBlocks[i] == 0) {
        mBlocks[i] = next_index;
        incrementNextIndex();
        //if(dbg) 
        Util.prta(Util.clear(tmpsb).append("newMasterBlock create at ").append(mBlocks[i]).append("/").
                append(julian).append(" i=").append(i).append(" filename=").append(filename));
        mb[i] = new MasterBlock((int) mBlocks[i], this, true);
        updateCtl(true);              // Write back changed block
        return (int) i;
      }
    }
    throw new EdgeFileCannotAllocateException("MasterBlock - out of MasterBlocks!");
  }

  /**
   * allocate a new index block. The caller is then free to update this block without any further
   * coordination. That is the caller is the sole writer of this block. All processing of the master
   * blocks occurs through this call and the user need not worry about master blocks.
   *
   * @param name A hopefully legal seed channel name
   * @return the new index block
   * @throws IOException - usually something amiss with read/writing the file
   * @throws EdgeFileReadOnlyException if opened readonly, new blocks cannot be allocated.
   * @throws EdgeFileCannotAllocateException - very rare, must be out of the 250 masterblock indices
   * @throws IllegalSeednameException If give channel name does not pass MasterBlock.chkSeedName()
   */
  public synchronized int newIndexBlock(StringBuilder name)
          throws IOException, EdgeFileCannotAllocateException, IllegalSeednameException,
          EdgeFileReadOnlyException {
    if (readOnly) {
      throw new EdgeFileReadOnlyException("Cannot add new index to read only");
    }
    int ok = -1;
    MasterBlock.checkSeedName(name);      // insure the name is not crap, throws exception

    // Try to add to all of the master blocks, set ok if it is added
    for (int i = 0; i < MAX_MASTER_BLOCKS; i++) {
      if (mBlocks[i] == 0) {
        break;      // out of master blocks
      }
      ok = mb[i].addIndexBlock(name, next_index);// this will also write out the masterblock
      if (ok != -1) {
        break;
      }
    }

    // This channel could not be added to the existing master blocks, add another MB
    if (ok == -1) {
      ok = newMasterBlock();
      //if(dbg) 
      Util.prta(Util.clear(tmpsb).append("Adding master block=").append(next_index).append(" ").append(name).
              append(" ok=").append(ok).append(" in ").append(filename));
      // It has to work this time!
      mb[ok].addIndexBlock(name, next_index);  // this will also write out the master block
    }

    ok = next_index;        // save the index block to return
    incrementNextIndex();
    if(dbg) 
      Util.prta(Util.clear(tmpsb).append("New Index Blk=").append(ok).append(" to ").append(name).
            append(" ").append(filename.substring(Math.max(0, filename.lastIndexOf("/")))).
            append(" nxtext=").append(next_extent));
    updateCtl(true);              // update increment index on disk
    return ok;                // return the index blocks
  }

  private void incrementNextIndex() {
    next_index++;
    if (next_index > 65500) {
      Util.prta(Util.clear(tmpsb).append("***** Index block is about to overflow unsigned short representation!!!!").
              append(next_index) + " " + filename);
      SendEvent.edgeEvent("IndexOvfl", "The index blocks for " + filename + " is overflowing!!!!", this);
      if (next_index > 65534) {
        Util.prta(Util.clear(tmpsb).append("IndexFile about to wrap index blocks - do not allow this!"));
        Util.exit(333);
      }
    }
  }

  /**
   * Find the block number of the first index block for the given channel
   *
   * @param name A hopefully legal seed channel name
   * @return the first index block
   * @throws IllegalSeednameException If give channel name does not pass MasterBlock.chkSeedName()
   */
  public synchronized int getFirstIndexBlock(StringBuilder name)
          throws IllegalSeednameException {
    int ok;
    MasterBlock.checkSeedName(name);      // insure the name is not crap, throws exception

    // Try to add to all of the master blocks, set ok if it is added
    for (int i = 0; i < MAX_MASTER_BLOCKS; i++) {
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
   * for rebuilding an index, we need to forget the original master blocks and allocate anew!
   *
   * @throws EdgeFileReadOnlyException If updctl() throws one
   * @throws IOException If updctl() throws one
   * @thros
   */
  public synchronized void clearMasterBlocks() throws IOException, EdgeFileReadOnlyException {
    for (int i = 0; i < MAX_MASTER_BLOCKS; i++) {
      mBlocks[i] = 0;
      mb[i] = null;
    }
    next_index = 1;
    updateCtl(true);

  }

  /**
   * Find the block number of the first index block for the given channel
   *
   * @param name A hopefully legal seed channel name
   * @return the first index block
   * @throws IllegalSeednameException If give channel name does not pass MasterBlock.chkSeedName()
   */
  public synchronized int getLastIndexBlock(StringBuilder name)
          throws IllegalSeednameException {
    int ok;
    MasterBlock.checkSeedName(name);      // insure the name is not crap, throws exception

    // Try to add to all of the master blocks, set ok if it is added
    for (int i = 0; i < MAX_MASTER_BLOCKS; i++) {
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

  /**
   * Grant the next extent out to the user. Allow zero fill to run if blocked.
   *
   * @throws IOException - IO errors on updating .idx file
   * @throws EdgeFileReadOnlyException If file was opened readonly, new blocks are not allowed.
   * @return the block number starting the 64 block extent. The user is now the sole writer of the
   * returned blocks.
   */
  public synchronized int getNewExtent()
          throws IOException, EdgeFileReadOnlyException {

    /* Since we do not want to zero extents issued to others, wait to insure that 
      * zeroer is ahead of this extent.  If not, wait until it is!  This should not
     * happen often!!!
     */
    if (readOnly) {
      throw new EdgeFileReadOnlyException("Cannot add new extent to read only");
    }
    if (zeroer != null) {
      int loop = 0;
      while (next_extent >= zeroer.getLastZero()) {
        zeroer.interrupt();
        try {
          wait(5L);
        } catch (InterruptedException e) {
        }
        loop++;
      }
      if (loop > 0) {
        Util.prta(Util.clear(tmpsb).append("****!!!! WARNING : zero is behind data! ").
                append(filename).append(" next=").append(next_extent).append(" zero=").append(zeroer.getLastZero()).
                append(" delay ").append(loop * 5).append(" ms.  Set zeroAhead bigger!"));
      }
    }
    int ret = next_extent;
    //Util.prta(next_extent+" is ne assigned "+recordNumber+" files="+files.size());
    next_extent += 64;
    updateCtl(false);
    return ret;
  }

  /**
   * Close this index file and clean up other resources.
   *
   * @throws IOException If IOException while trying to close the file
   */
  public synchronized void close() throws IOException {
    closer = new CloseThread(this);
  }

  public void crashClose() {
    synchronized (files) {      // block other users from finding this file while it is being closed
      files.remove(julian);  // Take this file off the static list
    }
    if (rw != null) {
      try {
        rw.close();
      } catch (IOException e) {
      }
    }
    if (dbg) {
      Util.prta("CloseThread : datafile.close() " + filename + toString());
    }
    if (dataFile != null) {
      try {
        dataFile.close();
      } catch (IOException e) {
      }      // close access to data portion (this is why zeroer must be dead!)
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
  }

  /**
   * closing can take quite some time, so put it in a thread to prevent slow downs
   */
  public final  class CloseThread extends Thread {

    IndexFile ifl;
    String str;
    private final StringBuilder tmpsb = new StringBuilder(100);

    public CloseThread(IndexFile f) {
      ifl = f;
      //setDaemon(true);
      gov.usgs.anss.util.Util.prta("new ThreadShutdown " + getName() + " " + getClass().getSimpleName());
      start();
    }

    @Override
    public void run() {
      str = toString();
      Util.prta(Util.clear(tmpsb).append("CloseThread: started on ").append(filename).append(" ").append(str));
      //boolean dbg=true;
      synchronized (files) {      // block other users from finding this file while it is being closed
        //synchronized(indexBlocksLock) {
        whoHas = "IF closer(): " + filename + Util.fs + julian;
        if (dbg) {
          Util.prta(Util.clear(tmpsb).append("CloseThread: files.remove2 then closeJulian ").
                  append(filename).append(toString()).append("/").append(julian).
                  append(" next_index=").append(next_index).append(" next_extent=").append(next_extent));
        }
        files.remove(julian);  // Take this file off the static list
      }
      Util.prta(Util.clear(tmpsb).append("CloseThread: call updateCtl ").append(filename).append("/").append(julian));
      // Close down all related index blocks (ones open for this Julian day
      try {
        updateCtl(true);              // update the control block (any extents added)
      } catch (EdgeFileReadOnlyException | IOException e) {
      }
      Util.prta(Util.clear(tmpsb).append("CloseThread: call closeJulian ").append(filename).append("/").append(julian));

      int nfree = IndexBlock.closeJulian(julian);   // Clear out any indexBlocks that point here
      // At this point there should be no references left to this file object, shut it down
      //if(dbg)
      Util.prta(Util.clear(tmpsb).append("IndexFile:CloseThread() ").append(filename).append(" #free=").append(nfree).append(" on ").append(str));
      if (!readOnly) {         // no zeroing thread if not able to write it
        terminate = true;       // This basically will shut down the zeroer thread
        while (terminate) {    // this changes when the zeroing thread exits
          try {
            sleep(200L);
          } catch (InterruptedException e) {
          }
          if (dbg) {
            Util.prta(Util.clear(tmpsb).append("CloseThread: Terminate wait on ").append(filename).append(str));
          }
        }
      }

      // close files and remove this IndexFile from the TreeMap of open files
      try {
        //if(dbg)
        Util.prta(Util.clear(tmpsb).append("CloseThread close() close ").append(filename).append(str));
        whoHas = "IF closer(): " + filename + Util.fs + julian;
        if (dbg) {
          Util.prta(Util.clear(tmpsb).append("CloseThread: files.remove then closeJulian ").
                  append(filename).append(toString()).append("/").append(julian));
        }
        if (rw != null) {
          rw.close();
        }
        if (dbg) {
          Util.prta(Util.clear(tmpsb).append("CloseThread : datafile.close() ").append(filename).append(str));
        }
        if (dataFile != null) {
          dataFile.close();       // close access to data portion (this is why zeroer must be dead!)
        }
        if (dbg) {
          Util.prta(Util.clear(tmpsb).append("CloseThread: done close on filename=").
                  append(filename).append(toString()));
        }
        whoHas = "IF closer(): " + filename + Util.fs + julian;
        if (dbg) {
          Util.prta(Util.clear(tmpsb).append("CloseThread: files.remove then closeJulian ").
                  append(filename).append(toString()).append("/").append(julian).append(" next_index=").
                  append(next_index).append(" next_extent=").append(next_extent));
        }
        whoHas = "IF closer(): " + filename + Util.fs + julian;
        if (dbg) {
          Util.prta(Util.clear(tmpsb).append("CloseThread: files.remove then closeJulian ").
                  append(filename).append(toString()).append("/").append(julian).append(" next_index=").
                  append(next_index).append(" next_extent=").append(next_extent));
        }
      } catch (IOException e) {
        Util.prta(Util.clear(tmpsb).append("CloseThread IOException Thrown ").append(filename).append(str));
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
      ifl = null;
      //if(dbg)
      Util.prta(Util.clear(tmpsb).append("CloseThread: exitThread ").append(filename).append(str));
    }
  }

  /**
   * update the first block of the index file
   *
   * @throws IOException if I/O error updating control section
   * @throws EdgeFileReadOnlyException if opened read only, updateCtl is an error
   */
  private synchronized void updateCtl(boolean updateReplicate)
          throws IOException, EdgeFileReadOnlyException {
    if (readOnly) {
      throw new EdgeFileReadOnlyException("cannot updateControl area in read only");
    }
    toCtlbuf();           // create raw byte array from data
    if (rw != null) {
      rw.writeBlock(0, ctlbuf, 0, 512);
    }
    // Update index block in replicated data as well!
    if (updateReplicate) {
      EdgeBlockQueue.queue(julian, IndexFile.getNode(), CONTROLBLK,
              -1, // this is an index block!
              0, -1, ctlbuf, 0);      // Pass it to the replication!
    }
  }

  /**
   * find the channel index in which this channel appears
   *
   * @param name The seed channel name
   * @return The channel index
   */
  public int getChannelIndex(StringBuilder name) {
    int chan;
    for (int i = 0; i < MAX_MASTER_BLOCKS; i++) {
      if (mBlocks[i] == 0) {
        return -1;
      }
      if ((chan = mb[i].findChannel(name)) != -1) {
        return i * MasterBlock.MAX_CHANNELS + chan;
      }
    }
    return -1;
  }

  /**
   * from the raw disk block, populate the ctl variables
   */
  private void fromCtlbuf() {
    ctl.clear();
    length = ctl.getInt();
    next_extent = ctl.getInt();
    next_index = (int) (ctl.getShort()) & 0xFFFF; // its a unsigned short
    for (int i = 0; i < MAX_MASTER_BLOCKS; i++) {
      mBlocks[i] = ((int) ctl.getShort()) & 0xFFFF; // its an unsigned short
    }
  }

  /**
   * from the ctl variables, construct the raw disk block
   */
  private void toCtlbuf() {
    ctl.clear();
    ctl.putInt(length).putInt(next_extent).putShort((short) next_index);
    for (int i = 0; i < MAX_MASTER_BLOCKS; i++) {
      ctl.putShort((short) mBlocks[i]);
    }
  }

  /**
   * set the default length of the data files in blocks
   *
   * @param l The length in blocks of the data files by default
   */
  static public void setDefaultLength(int l) {
    DEFAULT_LENGTH = l;
  }

  /**
   * create a one line per channel list
   *
   * @return a one line per channel listing of the master blocks
   */
  public String listChannels() {
    StringBuilder sb = new StringBuilder(10000);
    for (int i = 0; i < MAX_MASTER_BLOCKS; i++) {
      if (mBlocks[i] == 0) {
        break;
      }
      for (int j = 0; j < MasterBlock.MAX_CHANNELS; j++) {
        sb.append(i * MasterBlock.MAX_CHANNELS).append(j).append(" ").append(mb[i].getChannelString(j)).append("\n");
      }
    }
    return sb.toString();
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
   * get the raw disk unit open on this julian file
   *
   * @return the RawDisk file open for this file
   */
  class ShutdownIndexFile extends Thread {

    IndexFile file;

    public ShutdownIndexFile(IndexFile ifl) {
      file = ifl;
      gov.usgs.anss.util.Util.prta("new ThreadShutdown " + getName() + " " + getClass().getSimpleName());
    }

    // This is called by the RunTime.shutdown at any termination 
    @Override
    public void run() {
      try {
        sleep(10000);
      } catch (InterruptedException e) {
      } // allow some time for final writes!
      shuttingDown = true;
      try {
        if (dbg) {
          Util.prta("Shutdown Indexfile=" + file.toString() + " " + toString());
        }
        IndexBlock.closeJulian(file.getJulian());
        file.close();
      } catch (IOException e) {
        Util.prta("ShutdownIndexFile IOException file=" + file.toString() + " " + e.getMessage());
      }
    }
  }


  /**
   * dump out info on all known open files
   *
   * @return String with one file per line and its status
   */
  private static final StringBuilder fullsb = new StringBuilder(10);

  public static String getFullStatus() {
    Util.clear(fullsb);
    synchronized (files) {
      TLongObjectIterator<IndexFile> itr = files.iterator();
      while (itr.hasNext()) {
        itr.advance();
        fullsb.append(itr.value().toStringFull()).append("\n");
      }
      /*Object [] all = files.values().toArray();
       for (Object all1 : all) {
         fullsb.append(((IndexFile) all1).toStringFull()).append("\n");
       }*/
    }
    return fullsb.toString();
  }

  /**
   * close open for write file
   */
  public static void closeAll() {
    synchronized (files) {
      try {
        TLongObjectIterator<IndexFile> itr = files.iterator();
        while (itr.hasNext()) {
          itr.advance();
          itr.value().close();
        }
        /* Object [] all = files.values().toArray();
        for (Object all1 : all) {
          ((IndexFile) all1).close();
        }*/
      } catch (IOException e) {
        Util.prta("CloseAll IOexception e=" + e.getMessage());
      }
    }
  }

  /**
   * close open for write file
   *
   * @param msec number of milliseconds that need to have past since last use for file to be closed
   * @return The number of files closed
   */
  public static int closeStale(int msec) {
    if (NDAYS <= 0) {
      return 0;
    }
    int nclosed = 0;
    synchronized (files) {
      try {
        TLongObjectIterator<IndexFile> itr = files.iterator();
        long now = System.currentTimeMillis();
        while (itr.hasNext()) {
          itr.advance();
          IndexFile idx = itr.value();
          if ((now - idx.getRawDisk().getLastUsed()) > msec
                  && (now - idx.getDataRawDisk().getLastUsed()) > msec) {
            Util.prta("IndexFile: ms=" + msec + " close stale " + idx.toString());
            idx.close();
            nclosed++;
          }
          /*else {
            try {  // this is a bad idea as it cause every file to update whenever close stale is called!
              idx.updateCtl(true);     // do  an updateCtl to make sure its updated periodically             
            }
            catch(EdgeFileReadOnlyException e) {}
          }   */
        }
        /*Object [] all = files.values().toArray();
        IndexFile idx;
        long now = System.currentTimeMillis();
        Util.prta("IF: closeStale() #openIF="+all.length+" ms="+msec);
        for (Object all1 : all) {
          idx = (IndexFile) all1;
          if( (now - idx.getRawDisk().getLastUsed()) > msec &&
                  (now - idx.getDataRawDisk().getLastUsed()) > msec) {
            Util.prt("IndexFile: close stale "+idx.toString());
            idx.close();
            nclosed++;
          }
          else {
            try {
              idx.updateCtl(true);     // do  an updateCtl to make sure its updated periodically             
            }
            catch(EdgeFileReadOnlyException e) {}
          }
        }*/
      } catch (IOException e) {
        Util.prta("CloseAll IOexception e=" + e.getMessage());
      }
    }
    Util.prta("IF: closeStale() #closed=" + nclosed);
    return nclosed;
  }

  /**
   * close any files which are not within the limit number of days and not used in some amount of
   * time
   *
   * @param limit The limit of the number of days that can be open
   * @param msused The number of milliseconds that must have passed since the file was last used
   * @return The number of files closed by this call
   */
  public static int closeJulianLimit(int limit, int msused) {
    //synchronized(indexBlocksLock) {
    java.util.GregorianCalendar now = new java.util.GregorianCalendar();
    now.setTimeInMillis(System.currentTimeMillis());
    int todayJulian = SeedUtil.toJulian(now);
    IndexFile ifr;
    long current = System.currentTimeMillis();
    Util.prta("IFR: closeStale() #openIFR=" + files.size() + " limit=" + limit + " msused=" + msused);
    int count = 0;
    TLongObjectIterator<IndexFile> itr = files.iterator();
    while (itr.hasNext()) {
      itr.advance();
      ifr = itr.value();
      try {
        if ((todayJulian - ifr.getJulian()) > limit
                && (current - ifr.getRawDisk().getLastUsed()) > msused
                && (current - ifr.getDataRawDisk().getLastUsed()) > msused) {
          Util.prta("IFR: closeJulianLimit()  on file=" + ifr.toString() + " ages=" + (current - ifr.getRawDisk().getLastUsed()) + ">" + msused);
          ifr.close();
          count++;
        }
      } catch (IOException e) {
        Util.prta("IFR: closeJulianLimit() IOException closing file" + e.getMessage());
      }
    }
    /*Object [] all = files.values().toArray();
    for (Object all1 : all) {
      ifr = (IndexFile) all1;
      try {
        if( (todayJulian - ifr.getJulian()) > limit &&
                (current - ifr.getRawDisk().getLastUsed()) > msused &&
                (current - ifr.getDataRawDisk().getLastUsed()) > msused) {
          Util.prta("IFR: closeJulianLimit()  on file="+ifr.toString()+" ages="+(current - ifr.getRawDisk().getLastUsed())+">"+msused);
          ifr.close();
          count++;
        }
      }
      catch(IOException e) {
        Util.prta("IFR: closeJulianLimit() IOException closing file"+e.getMessage());
      }
    }*/
    return count;
  }

  /**
   * Analyze the given index file based on parameters given. This is used by msread to "dump"
   * index/data about the file
   *
   * @param filename Filename of index file to read
   * @param indexDetail If true, dump details about the index at the extent level
   * @param errOnly Do everything, but only do output when variances are found
   * @param readData Analysis includes reading the data blocks and checking them as well
   * @param seedMask Do this only for seednames which match this regular expression
   * @param fixIndexLinks If true, fix any broken links in the index chain
   * @return A StringBuffer with the analysis (might be pretty big).
   */
  public static StringBuffer analyze(String filename, boolean indexDetail,
          boolean errOnly, int readData, String seedMask, boolean fixIndexLinks) {
    try {
      IndexFile ifl = new IndexFile(filename, false, !fixIndexLinks);
      return ifl.analyze(indexDetail, readData, errOnly, seedMask, fixIndexLinks);
    } catch (FileNotFoundException e) {
      Util.prta("IDX analyze cannot open file=" + filename + " " + e.getMessage());
      Util.exit(0);
    } catch (EdgeFileReadOnlyException e) {
      Util.prta("ReadOnlyException getting indexFile=" + filename + e.getMessage());
      e.printStackTrace();
      Util.exit(0);
    } catch (EdgeFileDuplicateCreationException e) {
      Util.prta("DuplicateCreationException getting indexFile=" + filename + e.getMessage());
      e.printStackTrace();
      Util.exit(0);
    } catch (IOException e) {
      Util.prta("IOException getting index file=" + filename + " " + e.getMessage());
      e.printStackTrace();
    }
    return new StringBuffer("Proably a file error!");        // should not happen!
  }

  /**
   * Analyze the given index file based on parameters given. This is used by msread to "dump"
   * index/data about the file
   *
   * @param julian Julian day to use to build Filename of index file to read
   * @param indexDetail If true, dump details about the index at the extent level
   * @param errOnly Do everything, but only do output when variances are found
   * @param readData Analysis includes reading the data blocks and checking them as well
   * @param seedMask Do this only for seednames which match this regular expression
   * @param fixIndexLinks If true, fix an broken links in the index chain
   * @return A StringBuffer with the analysis (might be pretty big).
   */
  public static StringBuffer analyze(int julian, boolean indexDetail,
          boolean errOnly, int readData, String seedMask, boolean fixIndexLinks) {
    IndexFile ifl = getIndexFile(julian);
    try {
      if (ifl == null) {
        ifl = new IndexFile(julian, false, true);
      }
      return ifl.analyze(indexDetail, readData, errOnly, seedMask, fixIndexLinks);
    } catch (FileNotFoundException e) {
      Util.prta("IDX analyze cannot open file=" + julian + " " + e.getMessage());
      Util.exit(0);
    } catch (EdgeFileReadOnlyException e) {
      Util.prta("ReadOnlyException getting indexFile=" + julian + e.getMessage());
      e.printStackTrace();
      Util.exit(0);
    } catch (EdgeFileDuplicateCreationException e) {
      Util.prta("DuplicateCreationsException getting indexFile=" + julian + e.getMessage());
      e.printStackTrace();
      Util.exit(0);
    } catch (IOException e) {
      Util.prta("IOException getinng index file=" + julian + " " + e.getMessage());
    }
    return null;
  }

  /**
   * Analyze the given index file based on parameters given. This is used by msread to "dump"
   * index/data about the file
   *
   * @param indexDetail If true, dump details about the index at the extent level
   * @param errOnly Do everything, but only do output when variances are found
   * @param readData Analysis includes reading the data blocks and checking them as well
   * @param seedMask Do this only for seednames which match this regular expression
   * @param fixBrokenLinks
   * @return A StringBuffer with the analysis (might be pretty big).
   */
  public StringBuffer analyze(boolean indexDetail, int readData, boolean errOnly,
          String seedMask, boolean fixBrokenLinks) {
    Util.setLogPadsb(false);
    TreeMap<IndexKey, IndexBlock> id = new TreeMap<>();
    IndexBlock idx2;
    StringBuffer sb = new StringBuffer(20000);
    BitSet blockMap = new BitSet(next_extent / 64);
    BitSet indexBlockMap;
    int dataBlocks;
    try {
      indexBlockMap = new BitSet((int) (rw.length() / 512));
      dataBlocks = (int) (dataFile.length() / 512);
    } catch (IOException e) {
      Util.prta("***** could not get index file length! e=" + e);
      sb.append("***** could not get index file length! e=").append(e);
      e.printStackTrace();
      return sb;
    }
    int nchan = 0;
    for (int i = 0; i < MAX_MASTER_BLOCKS; i++) {
      if (mBlocks[i] == 0) {
        break;
      }
      indexBlockMap.set(mBlocks[i]);
      for (int j = 0; j < MasterBlock.MAX_CHANNELS; j++) {
        nchan++;
        StringBuilder seedName = mb[i].getSeedName(j);
        //Util.prt("i="+i+" j="+j+" "+mb[i].getSeedName(j));
        if (seedName == null || Util.stringBuilderEqual(seedName, "            ")) {
          break;
        }
        try {
          idx2 = new IndexBlock(seedName, julian, true);
          IndexKey key = new IndexKey(seedName.toString(), julian);
          id.put(key, idx2);
        } catch (FileNotFoundException e) {
          Util.prta("creating block for " + seedName + "/" + julian + e.getMessage());
          Util.exit(0);
        } catch (IllegalSeednameException e) {
          Util.prta("creating first index block seed name illegal=" + seedName + "/" + julian + e.getMessage());
        } catch (IOException e) {
          Util.prt("IOException getting indexblock=" + seedName + "/" + julian + e.getMessage());
          e.printStackTrace();
          Util.exit(0);
        } catch (EdgeFileCannotAllocateException e) {
          Util.prta("CannotAllocate getting indexblock=" + seedName + "/" + julian + e.getMessage());
          Util.exit(0);
        } catch (EdgeFileReadOnlyException e) {
          Util.prta("ReadONlyException getting indexBlock=" + seedName + "/" + julian + e.getMessage());
        } catch (DuplicateIndexBlockCreatedException e) {
          Util.prta("DuplicateCreationException getting indexBlock=" + seedName + "/" + julian + e.getMessage());
        }
      }   // For each channel
    }     // for each masterBlock

    Util.prta("File=" + filename + " nextExtent=" + next_extent + " len=" + length
            + " nextIndex=" + next_index + " nchan=" + nchan);
    sb.append("File=").append(filename).append(" nextExtent=").append(next_extent).
            append(" len=").append(length).append(" nextIndex=").append(next_index).
            append(" nchan=").append(nchan).append("\n");
    // Got an alpha list of all of the indexblocks list them out here, use list to scan all of the datat blocks.
    Iterator it = id.values().iterator();

    long lastTickle = System.currentTimeMillis();
    int ndone = 0;
    while (it.hasNext()) {
      idx2 = (IndexBlock) it.next();
      boolean ok = true;
      if (seedMask.length() > 0 && !idx2.getSeedName().toString().matches(seedMask)) {
        continue;
      }
      while (ok) {
        //Util.prta("idx="+idx.toString());
        indexBlockMap.set(idx2.getIndexBlockNumber());    // Set the IndexBlock as having been seen on the links
        if (indexDetail || errOnly || readData != 0) {
          sb.append(idx2.toStringDetail(errOnly, readData, blockMap));
        } else {
          sb.append(idx2.toString()).append("\n");
        }
        try {
          ok = idx2.nextIndexBlock();      // If done, transform to next one
        } catch (IOException e) {
          Util.prta("nextIndexBlock IOexception e=" + e.getMessage());
        }
        String seedName = idx2.getSeedName().toString();
        if (System.currentTimeMillis() - lastTickle > 30000) {
          Util.prta("Working on " + seedName + " size=" + sb.length() + " ndone=" + ndone + " please wait....");
          lastTickle = System.currentTimeMillis();
        }

      }
      ndone++;
    }
    Util.prta("Analyze() done=" + ndone + " fixLinks=" + fixBrokenLinks);
    sb.append("Start scan for extents used but not on map #extents=").append(next_extent / 64).append("\n");
    for (int i = next_extent / 64; i < blockMap.size(); i++) {
      if (blockMap.get(i)) {
        sb.append("    *** more extents in use than expected=").append(next_extent).
                append(" in use=").append(i * 64).append(" size=").append(blockMap.size()).append("\n");
      }
    }
    if (indexDetail || errOnly) {
      for (int blk = 0; blk < next_extent / 64; blk++) {
        if (!blockMap.get(blk)) {
          sb.append("      **** extent ").append(blk * 64).
                  append(" next_extent=").append(next_extent * 64).append(" fsz=").append(dataBlocks).append(" is not in use!\n");
        }
      }
    }
    byte[] buf = new byte[512];
    gov.usgs.anss.edge.IndexBlock tmp;
    IndexBlock[] idxs = new IndexBlock[indexBlockMap.size()];
    for (int iblk = 1; iblk < indexBlockMap.size(); iblk++) {
      boolean master = false;
      for (int j = 0; j < MAX_MASTER_BLOCKS; j++) {
        if (mBlocks[j] == iblk) {
          master = true;
          break;
        }
      }
      try {
        rw.readBlock(buf, iblk, 512);
        boolean ok = false;
        for (int j = 0; j < 512; j++) {
          if (buf[j] != 0) {
            ok = true;
            break;
          }
        }
        if (!ok) {
          continue;
        }
        idxs[iblk] = new gov.usgs.anss.edge.IndexBlock(buf, iblk, julian, rw);
      } catch (IOException | EdgeFileCannotAllocateException e) {
        if (iblk <= next_index) {
          Util.prta("***** Failed to read in index block for array i=" + iblk + " next_index=" + next_index + " e=" + e);
        }
      }
    }
    sb.append("Start scan for unlinked index blocks #blocks=").append(indexBlockMap.size()).append(" next_index=").append(next_index).append("\n");
    for (int iblk = 1; iblk < indexBlockMap.size(); iblk++) {
      if (!indexBlockMap.get(iblk)) {
        tmp = idxs[iblk];
        if (tmp == null) {
          if (iblk < next_index) {
            sb.append("*** index block is likely zeros at iblk=").append(iblk).append(" nextindx=").append(next_index).append("\n");
          }
          continue;
        }
        IndexBlock last = null;
        for (int j = iblk - 1; j >= 1; j--) {
          if (idxs[j] != null) {
            //Util.prt(j+" "+iblk+" looking for "+idxs[j].getSeedName()+" to "+tmp.getSeedName());

            if (Util.stringBuilderEqual(idxs[j].getSeedName(), tmp.getSeedName())) {
              last = idxs[j];
              //sb.append("**** Found prior index block for ").append(tmp.getSeedName()).append(" ").append(last).append("\n");
              break;
            }
          }
        }
        sb.append(" *** index block not on link ").append(tmp.getSeedName()).append(" blk=").
                append(tmp.getIndexBlockNumber()).append(" nextind=").append(tmp.getNextIndex());
        if (last == null) {
          sb.append(" ** No last block found for this channel");
        } else {
          sb.append(" last index ").append(last.getSeedName()).append(" blkd=").append(last.getIndexBlockNumber()).
                  append(" nxtblk=").append(last.getNextIndex()).append(" last bitmap=").
                  append(Util.toHex(last.getBitMap(IndexBlock.MAX_EXTENTS - 1)));
          if (last.getNextIndex() != tmp.getIndexBlockNumber()
                  && last.getNextIndex() == -1) {
            if (fixBrokenLinks) {
              try {
                for (int i = 0; i < IndexBlock.MAX_EXTENTS; i++) {
                  last.getBitMaps()[i] = -1L;
                }
                last.setNextIndexFix(tmp.getIndexBlockNumber());
                sb.append("*** Fixing forward line for last=").append(last).append(" to ").append(tmp.getIndexBlockNumber()).append("\n");
              } catch (IOException e) {
                Util.prta("IOException updating broken link packet e=" + e);
                sb.append("***** IOException updating broken link packet e=").append(e).append("\n");
              }
            }
          } else {
            sb.append(" * No need to fix - index link list o.k.");
          }
        }

        sb.append("\n");
        //sb.append(tmp.toStringDetail(errOnly, readData, blockMap));
      }
    }
    return sb;
  }

  /**
   * compares two file types orders them by julian day.
   *
   * @param other The IndexFile to compare against
   * @return -1 if this < other, 0 if equal, 1 if this is greater than
   */
  @Override
  public int compareTo(Object other) {
    int ojulian = ((IndexFile) other).getJulian();
    if (julian < ojulian) {
      return -1;
    } else if (julian > ojulian) {
      return 1;
    }
    return 0;
  }

  /**
   * This routine reads in the edge.prop and runs it through the IndexFile.init() to insure that the
   * datapaths, datamask, etc are setup. It then scan each data path for data files and tries to put
   * them in the datamask directory - that is - if it finds a $PATH/yyyy_ddd_n#i.ms file it tries to
   * move it to $PATH/$DATAMASK/yyyy_ddd_n#i.ms along with the .idx file. It will create the
   * datamask subdirectory on the path if it does not exist.
   * <br>
   * It will not move the file if a similarly named data or index file is already in the DATAMASK
   * directory or if there is a permissions or other problem moving the file.
   */
  public static void rearrangeDataMask() {
    IndexFile.init();     // make sure the properties have been read
    if (DATA_MASK == null) {
      return;
    }
    if (DATA_MASK.equals("")) {
      return;
    }
    StringBuilder tmpsb = new StringBuilder(100);

    // For each data path look file files like yyyy_ddd_n#i.ms and move it and the index if found
    for (int i = 0; i < NPATHS; i++) {
      String path = DATA_PATH[i];
      File dir = new File(path.substring(0, path.length() - 1));
      //if(dbg) 
      Util.prta(Util.clear(tmpsb).append("IndexFile: path=").append(path).
              append(" dir=").append(dir.toString()).append(" isDir=").append(dir.isDirectory()));
      String[] filess = dir.list();      // List of data filenames on the path
      if (filess == null) {    // There are no files in the directory.
        SendEvent.debugEvent("IFDataMask", "Failed to open files on path=" + path, "IndexFile");
        Util.prta(Util.clear(tmpsb).append("Attempt to open files on path=").append(path).
                append(" dir=").append(dir.toString()).append(" failed.  Is VDL denied access to path? Too many Files open?"));
      } else {
        for (String files1 : filess) {
          // Are we using the DATA_MASK, if so check for directories that match
          if (files1.endsWith(".ms") && Character.isDigit(files1.charAt(0)) && files1.charAt(4) == '_') {
            String maskStub = files1.substring(0, IndexFile.DATA_MASK.length());   // the YYYY_D? portion

            // check that the subdirectoy exists and if not create it
            File subDir = new File(path + maskStub);
            if (!subDir.exists()) {
              subDir.mkdir();    // If this one does not exist, make it
            }
            // Create File object for the files in the subdirectory - they should not exists
            File theFile = new File(path + maskStub + Util.FS + files1);
            File theIndexFile = new File(path + maskStub + Util.FS + files1.replace(".ms", ".idx"));
            if (theFile.exists()) {      // This files exists in both places, warn the user an do nothing
              Util.prta(Util.clear(tmpsb).append("rearrangeDataMask() *** file exists in path=").append(path).
                      append(" and ").append(path).append(maskStub).append(" ").append(files1));
              if (theIndexFile.exists()) {
                Util.prta(Util.clear(tmpsb).append("rearrangeDataMask() ** index file exists on path=").
                        append(path).append(" and ").append(path).append(maskStub).append(" ").
                        append(files1.replace(".ms", ".idx")));
              }
              continue;
            }
            if (theIndexFile.exists()) {     // only the index file exists in subdirectory? how does this happen!
              Util.prta(Util.clear(tmpsb).append("rearrangeDataMask() *** index file exists with out data file on path=").
                      append(path).append(" and ").append(path).append(maskStub).append(" ").
                      append(files1.replace(".ms", ".idx")));
              continue;
            }

            // The files are as expected with no matching files in subdirectory, move them there now
            if (!theFile.exists() && !theIndexFile.exists()) {
              // create file objects for the original files on the datapath
              File orgIndexFile = new File(path + files1.replaceAll(".ms", ".idx"));
              File orgFile = new File(path + files1);
              if (orgIndexFile.exists() && orgFile.exists()) {   // If they exists - they should, do the move
                Util.prta(Util.clear(tmpsb).append("rearrangeDataMask() Moving ").
                        append(orgFile).append(" to ").append(theFile).append(" and ").
                        append(orgIndexFile).append(" to ").append(theIndexFile));
                try {
                  boolean fileOK = orgFile.renameTo(theFile);
                  boolean indexOK = orgIndexFile.renameTo(theIndexFile);
                  if (!fileOK || !indexOK) {
                    Util.prta(Util.clear(tmpsb).append("rearrangeDataMask() **** Moving failed ").
                            append(orgFile).append(" to ").append(theFile).append(" ok=").append(fileOK).append(" and ").
                            append(orgIndexFile).append(" to ").append(theIndexFile).append(" ok=").append(indexOK));
                  }
                } catch (Exception e) {
                  Util.prta(Util.clear(tmpsb).append(" error moving data files **** e=").append(e));
                }
              } else {
                Util.prta(Util.clear(tmpsb).append(
                        "rearrangeDataMask() ** file or index does not exists file=").
                        append(files1).append(" org=").append(orgFile.exists()).
                        append(" org idx=").append(orgFile.exists()));
              }
            }
          } // it matches the profile for a .ms data file
        }   // for each filename
      }     // else on directory exists
    }       // for each data path
  }
  /**
   * this inner class is a thread that keeps zeroing data blocks ahead of the next_extent pointer.
   * This way the zeroing writes are spread out through the day. It also extends the file by the
   * DEFAULT_EXTEND_BLOCKS if the zeroing would go over the end of the file.  It has a dynmic portion
   * in that the extend size is double if the extensions are happening in less that 600 seconds.  The
   * max extendsize is 320000 blocks (160 mB).  The zeroer is typically writting the lesser of 
   * 20000 blocks (10 mB) and the value of the 'extendsize' property.  So the file might get extended
   * by 320,000 blocks, but the zeroing will generally occur 20,000 blocks at a time
   */
  public final class ZeroDataFile extends Thread {

    final RawDisk zero;         // local copy of the datafile RawDisk from out class
    long tick;            // Count the ticks of this Thread
    int lastZero;         // Last block group zeroed (actually its the next block to zero)
    boolean shutdown;

    public void shutdown() {
      shutdown = true;
    }
    private StringBuilder runsb = new StringBuilder(100);

    @Override
    public final String toString() {
      return filename + Util.fs + julian + " blk=" + lastZero;
    }
    /** 
     * 
     * @param rw The Data file rawdisk
     * @throws FileNotFoundException 
     */
    public ZeroDataFile(RawDisk rw) throws FileNotFoundException {
      zero = rw;
      tick = 0;
      if (lastZero < extendSize) {
        lastZero = extendSize;       // on very new files, prevent bogus warnings
      }
      if (dbg) {
        Util.prta("ZeroData: start zero index " + filename + " " + toString());
      }
      // If the files is very short, make it a small minimum length and start the zeroer
      try {
        if (zero.length() / 512L < 5000) {
          zero.setLength(5000 * 512L);
        }
      } catch (IOException e) {
        Util.prta("zeroer cannot extent file length! " + e.getMessage() + " " + filename + "/" + julian);
      }
      //doZero();             // Insure the minimum # of blocks are zeroed
      // If the file is not the correct length, make it the right one
      try {
        if (zero.length() / 512L > length) {
          length = (int) (dataFile.length() / 512L);
        } else {
          zero.setLength(length * 512L);
        }
        if (dbg) {
          Util.prta("ZeroData: set correct length=" + length + " " + filename + "/" + julian);
        }
      } catch (IOException e) {
        Util.prta("ZeroData: zeroer cannot extent file length! " + e.getMessage() + " " + filename + "/" + julian);
        if (e.getMessage() != null) {
          if (e.getMessage().contains("No space")) {
            SendEvent.edgeEvent("NoSpaceLeft", "No space left " + dataFile.getFilename() + " on " + IndexFile.getNode(), (ZeroDataFile) this);
          } else {
            SendEvent.debugEvent("ZeroerErr", e.getMessage() + " on " + dataFile.getFilename() + " on " + IndexFile.getNode(), (ZeroDataFile) this);
          }
        }

      }
      gov.usgs.anss.util.Util.prta("new Thread " + getName() + " " + getClass().getSimpleName());

      start();
    }

    /**
     * used by outer class to insure new extents not issued before they are zeroed!
     *
     * @return The last block zero(actually the next block to zero)
     */
    public int getLastZero() {
      return lastZero;
    }

    /**
     * this thread sleeps 50 milliseconds and calls the doZero routine to keep the zeroer
     * ZERO_BLOCKS_AHEAD (Def=2000) blocks ahead of the next_extent
     */
    @Override
    public void run() {
      boolean done = false;
      lastZero = next_extent + 128;
      while (!done && !shutdown) {        // Loop until termination
        try {
          doZero();      // if true, some work was done
          try {
            sleep(50);
          } catch (InterruptedException e) {
          }
          tick++;
          //Util.prt("zeroDataFile tick="+tick);
          if (terminate) {
            done = true;
          }
        } catch (IOException e) {
          Util.prta(Util.clear(runsb).append("IOException in zeroer close and exit file=").append(filename).append(" ").append(e.toString()));
          e.printStackTrace();
          break;
        }
      }
      if (shutdown) {
        Util.prta("IndexFile: zero shutdown (normally by RebuildIndex");
      } else {
        Util.prta("IndexFile: zero write thread on " + rw.getFilename()
                + " is terminated. " + toString());
      }
      close();        // close the file
      terminate = false;
    }
    private int closeState;

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
              append(rw == null ? "null" : rw.getFilename()));
      int loop = 0;
      long elapse = System.currentTimeMillis();
      closeState = 5;
      try {
        sleep(1000);
      } catch (InterruptedException e) {
      }  // wait for run() to exit 
      shutdown();
      Util.prta(Util.clear(tmpsb).append("ZeroData: close completed. loop=").append(loop).
              append(" elapse=").append(System.currentTimeMillis() - elapse).append(" ms ").
              append(rw == null ? "null" : rw.getFilename()).
              append(" alive=").
              append(isAlive()).append(" ").append(toString()));
      closeState = 6;
    }
    private long lastEmail;

    private boolean doZero() throws IOException {
      int newlength;
      if (next_extent + 128 > lastZero) {
        Util.prta(Util.clear(runsb).append("ZeroDataFile: ***** Zero is behind last extent!  How does this happend lastZero=").
                append(lastZero).append(" next_extent=").append(next_extent));
        SendEvent.edgeSMEEvent("ZeroBehind", "Zeroer is behind last extent " + lastZero + "<" + next_extent, this);
        lastZero = next_extent + 128;     // do not ever allow zeroer behind data written to file
      }
      if (next_extent > 75000001) {
        if (System.currentTimeMillis() - lastEmail > 900000) {
          SendEvent.edgeEvent("HiWaterOOR", "Hiwater unreasonable for file=" + filename + " "
                  + Util.getNode() + "/" + Util.getAccount() + " " + Util.getProcess() + " " + next_extent
                  + "zeror> 75 Mb blocks (37.5 GB) set limit to 37.5 GB\n",
                  "IndexFileRep");
          lastEmail = System.currentTimeMillis();
        }
      }
      boolean adjustOK = true;
      long now = System.currentTimeMillis();
      // If we are starting up late, then the file will not be long enough, set it to the new high water to get zeroer going
      if (next_extent > length) {
        length = next_extent + extendSize;
        dataFile.setLength(length * 512L);
        Util.prta(Util.clear(runsb).append("ZeroData: setting to higher length based on highwater=").
                append(next_extent).append(" new length=").append(length));
        lastZero = (next_extent + 128) / 64 * 64;
      }
      // Make sure lastZero is above high water
      if (lastZero < next_extent + 128) {
        lastZero = (next_extent + 128) / 64 * 64;
      }
      // Do we need to zero more
      if (next_extent > lastZero - extendSize) {
        // while more needs zeroing
        while (lastZero < next_extent + extendSize) {
          try {
            if (terminate) {
              return false;     // no more!
            }            // If the next zero will extend the file, extend it by DEFAULT_EXTEND_BLOCKS
            // If the next zero will extend the file, extend it by DEFAULT_EXTEND_BLOCKS
            while (next_extent + extendSize > length || next_extent > length) { // does the file need to be extended?
              if (now - lastExtendTime < 600000 && adjustOK) {
                extendSize = extendSize * 2;
                adjustOK = false;
                Util.prta(Util.clear(runsb).append("ZeroData: Extend ").append(rw.getFilename()).
                        append(" size increased to ").append(extendSize).append(" elapsed=").
                        append(System.currentTimeMillis() - lastExtendTime));
                if (extendSize > 320000) {
                  extendSize = 320000;
                }
              }
              newlength = length + extendSize;
              synchronized (zero) {    // need to not interfere with other readers/writers
                dataFile.setLength(newlength * 512L);
                length = (int) (dataFile.length() / 512L);
              }
              Util.prta(Util.clear(runsb).append("Extend ").append(rw.getFilename()).
                      append(" to ").append(newlength).append(" new length=").append(length).
                      append(" next=").append(next_extent).append(" zero=").append(lastZero).
                      append(" elapsed=").append(now - lastExtendTime));
              lastExtendTime = now;
            }

            // Write out zeros for one extent
            int nblks = Math.min(ZERO_BUF_SIZE / 512, length - lastZero);
            zero.writeBlock(lastZero, zerobuf, 0, nblks * 512);
            Util.prta(Util.clear(runsb).append("ZeroDataFile: ").append(rw.getFilename()).
                    append(" zero=").append(lastZero).append(" hiwater=").append(next_extent).
                    append(" (").append(lastZero - next_extent).append(") length=").append(length));
            //highwater=1000000;// force big zeroing to test speed */
            lastZero += nblks;

          } catch (IOException e) {
            if (e.getMessage() != null) {
              if (e.getMessage().contains("No space left on device")) {
                SendEvent.edgeEvent("NoSpaceLeft", "No space left " + rw.getFilename() + " on " + IndexFile.getNode(), this);
              } else {
                SendEvent.debugEvent("ZeroerError", e.getMessage() + " on " + rw.getFilename() + " on " + IndexFile.getNode(), this);
              }
            } else {
              SendEvent.debugEvent("ZeroerError", e.toString() + " on " + rw.getFilename() + " on " + IndexFile.getNode(), this);
            }
            Util.IOErrorPrint(e, "zeroing data file=" + rw.getFilename());
          }
        }
        return true;
      }
      return false;
    }

    public long getTick() {
      return tick;
    }
  }     // end of class ZeroDataFile

  /**
   * unit test main
   *
   * @param args command line args
   */
  public static void main(String[] args) {
    EdgeProperties.init();
    rearrangeDataMask();
    String nd = "1";
    String inst = "90#1";
    Util.setProperty("Node", nd);
    Util.setProperty("Instance", inst);
    String nd2 = IndexFile.getNode();
    Util.prt("prop.node=" + nd + " inst=" + inst + " result node=" + node + "/" + nd2);
    node = null;
    nd = "1";
    inst = "1#9";
    Util.setProperty("Node", nd);
    Util.setProperty("Instance", inst);
    nd2 = IndexFile.getNode();
    Util.prt("prop.node=" + nd + " inst=" + inst + " result node=" + node + "/" + nd2);
    node = null;
    nd = "9";
    inst = "1#90";
    Util.setProperty("Node", nd);
    Util.setProperty("Instance", inst);
    nd2 = IndexFile.getNode();
    Util.prt("prop.node=" + nd + " inst=" + inst + " result node=" + node + "/" + nd2);
    node = null;
    nd = "90";
    inst = "1#9";
    Util.setProperty("Node", nd);
    Util.setProperty("Instance", inst);
    nd2 = IndexFile.getNode();
    Util.prt("prop.node=" + nd + " inst=" + inst + " result node=" + node + "/" + nd2);
    node = null;
    nd = "1";
    inst = "2#99";
    Util.setProperty("Node", nd);
    Util.setProperty("Instance", inst);
    nd2 = IndexFile.getNode();
    Util.prt("prop.node=" + nd + " inst=" + inst + " result node=" + node + "/" + nd2);
    String[] stations = new String[8];
    stations[0] = "MIAR";
    stations[1] = "WMOK";
    stations[2] = "ISCO";
    stations[3] = "ACSO";
    stations[4] = "AAM";
    stations[5] = "BLA";
    stations[6] = "DUG";
    stations[7] = "SDCO";
    String[] comps = new String[6];
    comps[0] = "BHZ";
    comps[1] = "BHN";
    comps[2] = "BHE";
    comps[3] = "LHZ";
    comps[4] = "LHN";
    comps[5] = "LHE";

    byte[] b = new byte[512];
    byte[] a = new byte[512];
    byte[] z = new byte[102400];
    byte[] c;
    int julian = SeedUtil.toJulian(4, 364);
    StringBuilder seedname = new StringBuilder(12);
    try {
      IndexFile f = null;
      for (int i = 0; i < 16; i++) {
        f = new IndexFile(julian++, true, false);
      }
      if (f != null) {
        int i = f.getChannelIndex(Util.clear(seedname).append("USMIAR BHZ00"));

        Util.prt("getChannelIndex=" + i);
        int idx = -1;
        if (i < 0) {
          idx = f.newIndexBlock(Util.clear(seedname).append("USMIAR BHZ00"));
        }
        i = f.getChannelIndex(Util.clear(seedname).append("USMIAR BHZ00"));
        int idx2 = f.newIndexBlock(Util.clear(seedname).append("USMIAR BHN00"));
        int idx3 = f.newIndexBlock(Util.clear(seedname).append("USMIAR BHE00"));
        Util.prt("getChannelIndex=" + i + " in idx=" + idx + " " + idx2 + " " + idx3);

        Util.prt(f.listChannels());

        Util.prt("\n");

        for (i = 0; i < 8; i++) {
          for (int j = 0; j < 6; j++) {
            f.newIndexBlock(Util.clear(seedname).append("US").append((stations[i] + "    ").substring(0, 5)).append(comps[j]).append("00"));
          }
        }
        Util.prt(f.listChannels());
        f.close();
        IndexFile f2 = new IndexFile(julian, false, false);
        Util.prt(f2.listChannels());
        for (i = (int) f2.getTick(); i < 1000; i++) {
          while (i >= f2.getTick()) {

          }
          f2.getNewExtent();
          if (i % 100 == 0) {
            for (int j = 0; j < 40; j++) {
              f2.getNewExtent();
            }
          }
        }
        f2.close();
        IndexFile f3 = new IndexFile(julian, false, true);
        Util.prt(f3.listChannels());
      }
    } catch (FileNotFoundException e) {
      Util.IOErrorPrint(e, "File open err on temp");
    } catch (IOException e) {
      Util.IOErrorPrint(e, "IOerror ");
    } catch (EdgeFileCannotAllocateException e) {
      Util.prt("Cannot allocate exception =" + e.getMessage());
    } catch (EdgeFileDuplicateCreationException e) {
      Util.prt("DuplicateCreation exception =" + e.getMessage());
    } catch (IllegalSeednameException e) {
      Util.prt("IllegalSeednameException=" + e.getMessage());
    } catch (EdgeFileReadOnlyException e) {
      Util.prt("EdgeFileReadOnlyException =" + e.getMessage());
    }
  }

}
