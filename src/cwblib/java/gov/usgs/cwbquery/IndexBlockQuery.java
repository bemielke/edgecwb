/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.cwbquery;

import gov.usgs.anss.edge.MasterBlock;
import gov.usgs.anss.edge.IndexKey;
import gov.usgs.anss.edge.IndexFileReplicator;
import gov.usgs.anss.edge.RawDisk;
import gov.usgs.anss.edge.IllegalSeednameException;
import gov.usgs.anss.seed.MiniSeed;
import gov.usgs.anss.util.Util;
import gov.usgs.anss.util.LoggingStream;
import gov.usgs.anss.util.SeedUtil;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.BitSet;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

/**
 * This class handles index blocks the query tool. The physical form is : SeedName (12 char)
 * nextIndext (int) updateTime (int) startingBlock (int), bitmap (long), earliestTime (short),
 * latestTime (short) Repeat last MAX_EXTENTS times.
 *
 * This class is a lighter copy of the full IndexBlock which can basically be used to read an index
 * block, read its contents via get* methods, and get the next index block in the chain with
 * nextIndexBlock().
 *
 * @author davidketchum
 */
public final class IndexBlockQuery implements Comparable {

  /**
   * this is the number of extents in one index block (block#, bit maps, earliest/latest times)
   *
   */
  public static final int MAX_EXTENTS = 30;
  //static DecimalFormat df2;   // used to convert ints to 2 character fixed length ascii
  private RawDisk rw;       // access to the index file
  private RawDisk datout;   //
  private IndexFileReplicator IndexFileReplicator;  // Access to the controlling index file
  private int julian;
  private int iblk;
  private byte[] buf;
  private byte[] extentbuf;
  private ByteBuffer bb;
  private IndexKey key;
  private boolean isModified;     // Set true by changes, false by updating to disk

  // Data for the raw block
  private String seedName;        // Exactly 12 characters long!
  private int nextIndex;          // If -1, no next index (this is the last one!)
  private long updateTime;        // seconds since midnight 1979
  private int startingBlock[];
  private long bitMap[];
  private short earliestTime[];
  private short latestTime[];

  // Local variables
  private int currentExtentIndex;  // the index in startingBlock[] of the current extent
  private int nextBlock;        // Within the last extent, where to write next(I don't this this is used)
  private long lastDate;
  long lastlength;
  String lastString;

  // static data needed to implement the write all mini-seed through the writeMiniSeed static
  // function
  static private Map IndexBlockQuerys; // sorted list of open index blocks
  static private final Integer IndexBlockQuerysLock = Util.nextMutex();
  static private String whoHas;
  private static int today;           // The julian day for today
  private static RawDisk future, past;
  private static boolean dbg;
  private final StringBuilder sbdetail = new StringBuilder(10000);

  /**
   * set debug state
   *
   * @param t if true, turn on debugging
   */
  static public void setDebug(boolean t) {
    dbg = t;
  }

  public boolean isModified() {
    return isModified;
  }

  /**
   * create a string representing this index block
   *
   * @return The block number, seedname, julian date, # extents, and next index annotated
   */
  @Override
  public String toString() {
    int i;
    for (i = 0; i < MAX_EXTENTS; i++) {
      if (startingBlock[i] != -1) {
        break;
      }
    }
    return "Index blk=" + (iblk + "/" + currentExtentIndex + "       ").substring(0, 9) + " " + seedName
            + " julian=" + julian + "+" + IndexFileReplicator.getNode() + " #extent=" + (i + 1) + " next=" + nextIndex;
  }

  /**
   * set the debug string for who has allocated the IndexBlockQueryList
   *
   * @param s A string representing who has the IndexBlockQueryList mutex
   */
  public static void setIBL(String s) {
    whoHas = s;
  }

  /**
   * get the index of IndexBlockQuerys (mainly used to synchronize on this object
   *
   * @return The IndexBlockQuerys treeMap
   */
  public static Map getIndexBlockQuerys() {
    return IndexBlockQuerys;
  }
  static long nio;

  /**
   * reset the IndexBlockQuerys collection and number of ios if it has not yet been done
   */
  public static void init() {
    if (IndexBlockQuerys == null) {
      IndexBlockQuerys = Collections.synchronizedMap(new TreeMap());
      nio = 0;
    }
  }

  /**
   * return the number of IOs performed (incremented once for each mini-seed write)
   *
   * @return Number of IOs performed as a long
   */
  public static long getNio() {
    return nio;
  }

  /**
   * given a seed name and julian day, return the already existing index block
   *
   * @param sn The 12 character seedname
   * @param jul The julian day
   * @return The index block, null if it has not previously been created.
   */
  public static IndexBlockQuery getIndexBlockQuery(String sn, int jul) {
    IndexKey ky = new IndexKey(sn, jul);
    IndexBlockQuery idx;
    synchronized (IndexBlockQuerysLock) {
      setIBL("getIndex BLock " + sn + "/" + jul);
      idx = (IndexBlockQuery) IndexBlockQuerys.get(ky);
    }
    return idx;
  }

  /**
   * creates a new instance of IndexBlockQuery for the given seedname in the give Index. This method
   * is used by the Query tool and other "read only" uses of the files. The IndexFileReplicator
   * must, of course, be open and can (or should) be read only.
   *
   * @param name The 12 character seedName (must pass MasterBlock.checkSeedName()
   * @param ifile The previously opened IndexFileReplicator to use to read the blocks
   * @param ib the block number of the index to build.
   * @throws IllegalSeednameException if name does not pass MasterBlock.checkSeedName()
   * @throws IOException If this block cannot be created.
   */
  public IndexBlockQuery(IndexFileReplicator ifile, String name, int ib)
          throws IllegalSeednameException, IOException {
    iblk = ib;
    lastDate = 0;
    lastlength = 0;
    lastString = "First Block";
    IndexFileReplicator = ifile;
    julian = ifile.getJulian();
    seedName = name;
    key = new IndexKey(name, julian);
    MasterBlock.checkSeedName(name);
    rw = IndexFileReplicator.getRawDisk();
    datout = IndexFileReplicator.getDataRawDisk();
    // Allocate space for arrays in this object
    startingBlock = new int[MAX_EXTENTS];
    bitMap = new long[MAX_EXTENTS];
    earliestTime = new short[MAX_EXTENTS];
    latestTime = new short[MAX_EXTENTS];

    // If this is a "read type" or "first" get the first block
    // The block was found, read it and  populate data
    buf = rw.readBlock(iblk, 512);
    bb = ByteBuffer.wrap(buf);
    fromBuf();
    updateTime = System.currentTimeMillis();
    nextBlock = -1;

    // Find the starting block of the last extent
    int j;
    for (j = 0; j < MAX_EXTENTS; j++) {
      if (startingBlock[j] == -1) {
        break;
      }
    }
    j--;

    // "The latest extent is pointed to by j
    // but since it might be lazily written we need to read in the extent
    // and find the zero filled one to set the last block
    long mask = 1;
    byte[] data = datout.readBlock(startingBlock[j], 64 * 512); // read the whole extent
    for (nextBlock = 0; nextBlock < 64; nextBlock++) {
      if (data[nextBlock * 512] == 0) {
        break;
      }
      bitMap[j] = bitMap[j] | mask;      // Or in this bit, block is in use
      mask = mask << 1;
    }
  }

  /**
   * convert the internal data to the byte buffer for writing SeedName (12 char) nextIndext (int)
   * updateTime (int) startingBlock (int), bitmap (long), earliestTime (short), latestTime (short)
   * Repeat last MAX_EXTENTS times.
   */
  private void toBuf() {
    bb.clear();
    bb.put(seedName.getBytes()).putInt(nextIndex).putInt((int) (updateTime / 1000L));
    for (int i = 0; i < MAX_EXTENTS; i++) {
      bb.putInt(startingBlock[i]).putLong(bitMap[i]).putShort(earliestTime[i]).putShort(latestTime[i]);
    }
  }

  /**
   * recover the data from a binary buffer (like after its read in!)
   */
  private void fromBuf() {
    bb.clear();
    byte[] namebuf = new byte[12];
    bb.get(namebuf);
    seedName = new String(namebuf);
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
   * set the next index pointer for this block, normally called when a new index is needed for a
   * channel. This effectively closes out the use of this index file for writing
   *
   * @param idx The next Index block value.
   * @throws IOException If this index block cannot be written out for some reason
   */
  public synchronized void setNextIndex(int idx) throws IOException {
    nextIndex = idx;
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
   * Get the number of extents referred to by this IndexBlockQuery.
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

  static boolean dbgBlocks;
  static LoggingStream blkout;
  static long lastBlockDay;

  /**
   * set the debug blocks state
   *
   * @param t The state to set it to
   */
  public static void setDebugBlocks(boolean t) {
    dbgBlocks = t;
  }

  /**
   * Advance this index block to the next one in the chain, return true if it change false if this
   * is the last block of the chain
   *
   * @return True if a new block is positioned, false if it is already last block in chain
   * @throws IOException if the nextIndexBlockQuery cannot be read
   */
  public synchronized boolean nextIndexBlock() throws IOException {
    if (nextIndex <= 0) {
      return false;
    }
    iblk = nextIndex;
    buf = rw.readBlock(iblk, 512);
    bb = ByteBuffer.wrap(buf);
    fromBuf();
    return true;
  }

  /**
   * The the physical block number of this index block
   *
   * @return The block number of this block
   */
  public synchronized int getIndexBlockNumber() {
    return iblk;
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
   * @return the IndexKey for this IndexBlockQuery
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
  public int compareTo(Object o) {
    IndexBlockQuery idx = (IndexBlockQuery) o;
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

  /**
   * return a detailed string about this index block
   *
   * @param errOnly Only list errors
   * @param readData 0=don't, 1=read and list errors only, 2=read display and list errors
   * @param blockMap A bit set return with list of extents in use (an extent is 64 blocks long)
   * @return String representing this block in detail including errors/inconsistencies found
   */
  public String toStringDetail(boolean errOnly, int readData, BitSet blockMap) {
    if (sbdetail.length() > 0) {
      sbdetail.delete(0, sbdetail.length());
    }
    String last = "  " + toString() + "\n";
    String line = null;
    if (!errOnly) {
      sbdetail.append(last);
    }
    // nextIndex should be -1 or positive 
    if (nextIndex < -1 || nextIndex > 20000) {
      if (errOnly) {
        sbdetail.append("   ").append(toString()).append("\n");
        sbdetail.append(last);
      }
      sbdetail.append("\n      **** next index is out of range\n");
    }
    if (iblk < 2 || iblk > 50000) {
      if (errOnly) {
        sbdetail.append("   ").append(toString()).append("\n");
        sbdetail.append(last);
      }
      sbdetail.append("\n      **** index block number is out of range\n");
    }
    if (julian < SeedUtil.toJulian(2005, 1) || julian > SeedUtil.toJulian(2040, 365)) {
      if (errOnly) {
        sbdetail.append("   ").append(toString()).append("\n");
        sbdetail.append(last);
      }
      sbdetail.append("      **** warning: Julian date out of range 2005-001 to 2040-365!");
    }
    int i;
    for (i = 0; i < MAX_EXTENTS; i++) {
      if (startingBlock[i] == -1) {
        for (int j = i; j < MAX_EXTENTS; j++) {
          if (startingBlock[j] != -1) {
            if (errOnly) {
              sbdetail.append(last);
            }
            sbdetail.append("      **** all startingBlocks after first unused are not unused!!!");
          }
        }
        break;
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
                + (Long.toHexString(bitMap[i]) + "        ").substring(0, 8)
                + " time range=" + start + " " + end + "\n";
      } else {
        sbdetail.append("    ").append((i + "   ").substring(0, 4)).append(" start=").
                append((startingBlock[i] + "        ").substring(0, 8)).append(" ").
                append((Long.toHexString(bitMap[i]) + "                ").substring(0, 16)).
                append(" time range=").append(start).append(" ").append(end).append("\n");
      }

      // If this is not a full bitMap, check for it being the "last" 
      // and that they are contiguously assigned
      int nb = 64;
      if (bitMap[i] != -1) {       // Is bit map not full
        boolean chk = true;
        if ((i + 1) >= MAX_EXTENTS && nextIndex == -1) {
          chk = false; // its in last possible extent
        }
        if (chk && startingBlock[i + 1] == -1 && chk) {
          chk = false;        // its is in the last  used extent
        }
        if (chk) {
          if (errOnly) {
            sbdetail.append("   ").append(toString()).append("\n");
            sbdetail.append(line);
          }
          sbdetail.append("       ******* ").append(seedName).append(" bitMap not full and extent is not last one\n");
        }
        nb = 0;
        long mask = bitMap[i] & 0x7fffffffffffffffL;
        while ((mask & 1) == 1) {
          mask = mask >> 1;
          nb++;
        }
        if (mask != 0) {
          if (errOnly) {
            sbdetail.append("   ").append(toString()).append("\n");
            sbdetail.append(line);
          }
          sbdetail.append("      ****** ").append(seedName).append(" bitMap has non-contiguous used blocks! nb=").append(nb).append(" ").append(Util.toHex(bitMap[i])).append("\n");
        }

      }

      // Starting block must be divisible by 64 evenly
      if ((startingBlock[i] % 64) != 0) {
        if (errOnly) {
          sbdetail.append("   ").append(toString()).append("\n");
          sbdetail.append(line);
        }
        sbdetail.append("      ***** starting block not divisible by 64!");
      }

      // earliest and latest times should be today!
      if (earliestTime[i] < 0 || (earliestTime[i] > 28800 && earliestTime[i] != 32700)) {
        if (errOnly) {
          sbdetail.append("   ").append(toString()).append("\n");
          sbdetail.append(line);
        }
        sbdetail.append("      **** warning: earliest time is not on this day");
      }
      if ((latestTime[i] < 0 && latestTime[i] != -32700) || latestTime[i] > 28800) {
        if (errOnly) {
          sbdetail.append("   ").append(toString()).append("\n");
          sbdetail.append(line);
        }
        sbdetail.append("      **** warning: latest time is not on this day");
      }

      // If set, read in the data in the extent and verify its in the right place
      if (readData > 0) {
        if (extentbuf == null) {
          extentbuf = new byte[512];
        }
        MiniSeed ms;
        try {
          byte[] inbuf = datout.readBlock(startingBlock[i], 65536);
          int off = 0;
          while (off < nb * 512) {
            System.arraycopy(inbuf, off, extentbuf, 0, 512);
            boolean allzeros = true;
            for (int j = 0; j < 512; j++) {
              if (extentbuf[j] != 0) {
                allzeros = false;
                break;
              }
            }
            if (allzeros) {
              sbdetail.append("   ").append(toString()).append("\n");
              sbdetail.append("      ***** all zeros at extent=").append(startingBlock[i]).append(" blk=").
                      append(off / 512).append(" ").append(seedName).append("/").append(julian).append("\n");
              off += 512;
            } else {
              try {
                ms = new MiniSeed(extentbuf);
                if (readData >= 2) {
                  sbdetail.append(ms.toString()).append("\n");
                }
                // Mini-seed channel name must agree
                if (!ms.getSeedNameString().equals(seedName)) {
                  if (errOnly) {
                    sbdetail.append("   ").append(toString()).append("\n");
                    sbdetail.append(line);
                  }
                  sbdetail.append("      **** extent=").append(startingBlock[i]).append(" blk=").
                          append(off / 512).append(" channel is not same=").append(ms.getSeedNameSB()).
                          append(" ").append(ms.getTimeString()).append(" ").append(ms.getJulian()).append("\n");
                }

                // It must be from the right julian day
                if (ms.getJulian() != julian) {
                  if (errOnly) {
                    sbdetail.append("   ").append(toString()).append("\n");
                    sbdetail.append(line);
                  }
                  sbdetail.append("      **** Julian date does not agree data=").append(ms.getJulian()).
                          append(" vs ").append(julian).append("\n");
                }

                // check that times are in the range!
                short st = (short) ((ms.getHour() * 3600 + ms.getMinute() * 60 + ms.getSeconds()) / 3);
                short en = (short) (st + ms.getNsamp() / ms.getRate() / 3.);
                if (st < earliestTime[i] || st > latestTime[i]) {
                  if (errOnly) {
                    sbdetail.append("   ").append(toString()).append("\n");
                    sbdetail.append(line);
                  }
                  sbdetail.append("      ***** start time of data too out of range ").
                          append(Util.df2(ms.getHour())).append(":").append(Util.df2(ms.getMinute())).
                          append(":").append(Util.df2(ms.getSeconds())).append("\n");
                }

                // List a line about the record unless we are in errors only mode
                long time = ms.getTimeInMillis();
                long diff;

                // if this is a trigger or other record it will have no samples (but a time!) so
                // do not use it for discontinuity decisions
                if (ms.getNsamp() > 0) {
                  if (lastDate != 0) {
                    diff = time - lastDate - lastlength;
                    if (diff > 1) {
                      sbdetail.append("=").append(lastString).append("\n");
                      sbdetail.append("   *** discontinuity = ").append(diff).append(" ms! ends at ").
                              append(ms.getTimeString()).append(" blk=").
                              append(startingBlock[i] + off / 512).append("\n");
                      sbdetail.append("/").append(ms.toString()).append(" iblk=").append(startingBlock[i] + off / 512).
                              append("/").append(ms.getBlockSize()).append("\n");
                      Util.prt("-" + lastString);
                      Util.prt(")" + ms.getSeedNameString() + "   *** discontinuity = " + diff + " ms? ends at " + ms.getTimeString()
                              + " blk=" + (startingBlock[i] + off / 512));
                      Util.prt("{" + ms.toString() + " iblk=" + (startingBlock[i] + off / 512) + "/" + ms.getBlockSize());
                    }
                  }
                  lastlength = (long) (ms.getNsamp() * 1000 / ms.getRate());
                  lastDate = time;
                  lastString = ms.toString() + " iblk=" + (startingBlock[i] + off / 512) + "/" + ms.getBlockSize();
                }

                off += ms.getBlockSize();
              } catch (IllegalSeednameException e) {
                Util.prt("toStringDetail() IllegalSeednameException =" + e.getMessage()
                        + "extent=" + startingBlock[i] + " blk=" + off / 512);
                for (int j = 0; j < 32; j++) {
                  Util.prt(j + " Dump " + extentbuf[j] + " " + (char) extentbuf[j]);
                }
                sbdetail.append("      **** extent=").append(startingBlock[i]).append(" blk=").
                        append(off / 512).append(" IllegalSeednameException ").append(e.getMessage());
                off += 512;
              }
            }
          }         // end while(off <65536) 
        } catch (IOException e) {
          Util.prt("IOException trying to read data from extent "
                  + toString() + " blk=" + startingBlock[i]);
        }
      }
    }
    return sbdetail.toString();

  }

}
