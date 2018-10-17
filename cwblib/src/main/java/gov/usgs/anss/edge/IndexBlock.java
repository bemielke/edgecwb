/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.edge;
import gov.usgs.alarm.SendEvent;
import gov.usgs.anss.edgemom.HoldingSender;
import gov.usgs.anss.edgemom.Hydra;
import gov.usgs.anss.edgethread.EdgeThread;
import gov.usgs.anss.seed.MiniSeed;
import gov.usgs.anss.seed.Steim1;
import gov.usgs.anss.seed.Steim2;
import gov.usgs.anss.seed.SteimException;
import gov.usgs.anss.seed.SteimFrameBlock;
import gov.usgs.anss.util.SeedUtil;
import gov.usgs.anss.util.LoggingStream;
import gov.usgs.anss.util.Util;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.BitSet;
import gnu.trove.map.hash.TLongObjectHashMap;
import gnu.trove.iterator.TLongObjectIterator;
import gov.usgs.anss.edgemom.EdgeChannelServer;
import gov.usgs.edge.config.Channel;
import gov.usgs.anss.util.LeapSecondDay;

/**
 * This class handles index blocks for EdgeMom. The physical form is :
 * <PRE>
 * SeedName (12 char)
 * nextIndex(int)
 * updateTime (int)
 * startingBlock (int), bitmap (long), earliestTime (short), latestTime (short)
 * Repeat last MAX_EXTENTS times.
 * </PRE> 
 * The "real time" (that is EdgeMom usage) creates these blocks for writing and the actual disk
 * writes are done through this class insuring the integrity of the index blocks as the data blocks
 * are written to disk. The file names for writing are derived from the "julian" day of the data and
 * the looked up "edge node" to make unique file names.  It tracks the extents assigned to this index 
 * block assigning new ones when needed to complete a data block write.  When this IndexBlock has full
 * extents, its last state is written and it becomes a new index block and allocates space for itself 
 * in the index file.  It is lazy in writing out update of bits within an extent, but updates itself
 * when needed (when an new extent is added, or a long time passes, or the rundown of the IndexFile
 * causes all index blocks to be written.  
 * <p>
 * On reopening an index block at file start, the last extent is read and the blocks that are occupied
 * are marked in use to handle the case where the program was terminated abnormally and rundowns did not
 * occur.
 *
 *
 * <p>
 * There are also "readonly" constructors and methods which allow an existing file and index to be
 * read back and manipulated for queries and such. The read backs require full file names rather
 * that the julian dates and looked up edge node since these methods might be used on other
 * computers to read data (such as the CWB).
 *
 *
 * @author davidketchum
 */
public final class IndexBlock implements Comparable {

  /**
   * this is the number of extents in one index block (block#, bit maps, earliest/latest times)
   *
   */
  public static final int MAX_EXTENTS = 30;
  static HoldingSender udpHolding;
  private static final byte[] lastExtentScratch = new byte[64 * 512];
  //static DecimalFormat df2;   // used to convert ints to 2 character fixed length ascii
  private RawDisk rw;       // access to the index file
  private RawDisk datout;   //
  private IndexFile indexFile;  // Access to the controlling index file
  private final int julian;
  private int iblk;
  private final byte[] buf = new byte[512];
  private byte[] extentbuf;
  private final ByteBuffer bb;          // THis wraps the scratch buf for reading index blocks
  //private final IndexKey  key;      // This has been replaced with the hash 
  private boolean isModified;     // Set true by changes, false by updating to disk

  // Data for the raw block
  private final StringBuilder seedName = new StringBuilder(12);        // Exactly 12 characters long!
  private final long hash;
  private int nextIndex;          // If -1, no next index (this is the last one!)
  private long updateTime;         // seconds since midnight 1979
  private final int startingBlock[] = new int[MAX_EXTENTS];
  private final long bitMap[] = new long[MAX_EXTENTS];
  private final short earliestTime[] = new short[MAX_EXTENTS];
  private final short latestTime[] = new short[MAX_EXTENTS];

  // Local variables
  private int currentExtentIndex;  // the index in startingBlock[] of the current extent
  private int nextBlock;        // Within the last extent, where to write next(I don't this this is used)
  private long lastDate;        // Last date as a epoch millis
  long lastlength;
  private final StringBuilder lastString = new StringBuilder(100);
  private final StringBuilder tmpsb = new StringBuilder(100);         // only call toString* methods
  private final StringBuilder tmpsb2 = new StringBuilder(100);         // only call from synchronized methods.
  private static final StringBuffer tmpthr = new StringBuffer(100);   // Some calls are multithreaded so use thread safe style
  private static final StringBuffer tmpthr2 = new StringBuffer(100);   // Some calls are multithreaded so use thread safe style

  // static data needed to implement the write all mini-seed through the writeMiniSeed static
  // function
  private static final TLongObjectHashMap<IndexBlock> indexBlocks = new TLongObjectHashMap<>();
  //static private final Map<IndexKey, IndexBlock> indexBlocks= 
  //        Collections.synchronizedMap(new TreeMap<IndexKey, IndexBlock>());; // sorted list of open index blocks
  //static final private Integer indexBlocksLock = 0; 
  static private final StringBuffer whoHas = new StringBuffer(100);
  private static long lastErrorTime;  // Damp the rate of email an pages
  private static long numberNoSpace;
  private static final StringBuilder errMsg = new StringBuilder(100);       // Error message for damping
  private static int today;           // The julian day for today
  private static RawDisk future, past, overflow;
  private static boolean dbg;

  /**
   * Set the seedname, this should only be done very carefully!
   *
   * @param s New 12 character seedname
   * @throws java.io.IOException
   */
  public void setSeedname(StringBuilder s) throws IOException {
    Util.clear(seedName).append(s);
    Util.rightPad(seedName, 12);
    this.updateBlock2();
  }

  /**
   * set debug state
   *
   * @param t if true, turn on debugging
   */
  static public void setDebug(boolean t) {
    dbg = t;
  }

  /**
   * return a monitor string for this class
   *
   * @return The string
   */
  public static String getMonitorString() {
    long nb = nio - lastnio;
    lastnio = nio;
    return "NBlocksWrite=" + nb + "\nIndexNblocks=" + (indexBlocks == null ? "0\n"
            : indexBlocks.size() + "\n");
  }

  public boolean isModified() {
    return isModified;
  }

  /**
   * set up a UdpHoldings sender with this IndexBlock class
   *
   * @param hs Is the holding sender to register
   */
  public static void registerHoldingSender(HoldingSender hs) {
    udpHolding = hs;
  }

  /**
   * create a string representing this index block
   *
   * @return The block number, seedname, julian date, # extents, and next index annotated
   */
  @Override
  public String toString() {
    return toStringBuilder(null).toString();
  }

  public StringBuilder toStringBuilder(StringBuilder tmp) {
    StringBuilder sb = tmp;
    if (sb == null) {
      sb = Util.clear(tmpsb2);
    }
    synchronized (sb) {
      int i;
      for (i = 0; i < MAX_EXTENTS; i++) {
        if (startingBlock[i] != -1) {
          break;
        }
      }
      sb.append("Index blk=").append(iblk).append("/").append(currentExtentIndex);
      while (sb.length() < 20) {
        sb.append(" ");
      }
      sb.append(" ").append(seedName).append(" julian=").append(julian).append("+").
              append(IndexFile.getNode()).append(" #extent=").append(i + 1).append(" hash=").append(Util.toHex(hash)).append(" next=").append(nextIndex);
    }
    return sb;
  }

  /**
   * convert a earliest or latest time to a string.
   *
   * @param time The time encoded as 3s of seconds since midnight
   * @param tmp A string builder to append or if null an internal one is returned
   * @return The formated string
   */
  public static StringBuilder timeToString(int time, StringBuilder tmp) {
    StringBuilder sb = tmp;
    if (sb == null) {
      sb = Util.getPoolSB();
    }
    int secs = time * 3;
    int hr = secs / 3600;
    secs = secs % 3600;
    int min = secs / 60;
    secs = secs % 60;
    Util.append(hr, 2, '0', sb).append(":");
    Util.append(min, 2, '0', sb).append(":");
    Util.append(secs, 2, '0', sb);
    return sb;

  }

  public String toStringDetail(boolean errOnly, int readData, BitSet blockMap) {
    return toStringDetailSB(errOnly, readData, blockMap).toString();
  }

  /**
   * return a detailed string about this index block
   *
   * @param errOnly Only list errors
   * @param readData 0=don't, 1=read and list errors only, 2=read display and list errors
   * @param blockMap A bit set return with list of extents in use (an extent is 64 blocks long)
   * @return String representing this block in detail including errors/inconsistencies found
   */
  public StringBuilder toStringDetailSB(boolean errOnly, int readData, BitSet blockMap) {

    //if(df2 == null) df2 = new DecimalFormat("00");
    StringBuilder sb = new StringBuilder(10000);
    StringBuilder last = new StringBuilder(50).append("  ").append(toStringBuilder(null)).append("\n");
    //String last = "  "+toString()+"\n";
    //String line=null;
    StringBuilder start = new StringBuilder(8);
    StringBuilder end = new StringBuilder(8);
    StringBuilder line = new StringBuilder(50);
    if (!errOnly) {
      sb.append(last);
    }
    // nextIndex should be -1 or positive 
    if (nextIndex < -1 || nextIndex > 50000) {
      if (errOnly) {
        sb.append("   ").append(toStringBuilder(null)).append("\n");
        sb.append(last);
      }
      sb.append("\n      **** next index is out of range\n");
    }
    if (iblk < 2 || iblk > 50000) {
      if (errOnly) {
        sb.append("   ").append(toStringBuilder(null)).append("\n");
        sb.append(last);
      }
      sb.append("\n      **** index block number is out of range\n");
    }
    if (julian < SeedUtil.toJulian(2005, 1) || julian > SeedUtil.toJulian(2040, 365)) {
      if (errOnly) {
        sb.append("   ").append(toStringBuilder(null)).append("\n");
        sb.append(last);
      }
      sb.append("      **** warning: Julian date out of range 2005-001 to 2040-365!");
    }
    int i;
    for (i = 0; i < MAX_EXTENTS; i++) {
      if (startingBlock[i] == -1) {
        for (int j = i; j < MAX_EXTENTS; j++) {
          if (startingBlock[j] != -1) {
            if (errOnly) {
              sb.append(last);
            }
            sb.append("      **** all startingBlocks after first unused are not unused!!!");
          }
        }
        break;
      }
      blockMap.set(startingBlock[i] / 64);      // Set this extent as in use
      start = IndexBlock.timeToString(earliestTime[i], Util.clear(start));
      end = IndexBlock.timeToString(latestTime[i], Util.clear(end));

      if (errOnly) {
        line.append("    ").append(Util.rightPad(i, 4)).append(" start=").append(Util.rightPad(startingBlock[i], 8)).
                append(" ").append(Util.rightPad(Long.toHexString(bitMap[i]), 8)).append(" time range=").
                append(start).append(" ").append(end).append("\n");
      } else {
        sb.append("    ").append(Util.rightPad(i, 4)).append(" start=").
                append(Util.rightPad(startingBlock[i], 8)).append(" ").
                append(Util.rightPad(Long.toHexString(bitMap[i]), 8)).
                append(" time range=").append(start).append(" ").append(end).append("\n");
      }

      // If this is not a full bitMap, check for it being the "last" 
      // and that they are contiguously assigned
      int nb = 64;
      if (bitMap[i] != -1) {       // Is bit map not full
        boolean chk = true;
        if ((i + 1) >= MAX_EXTENTS && nextIndex == -1) {
          chk = false; // its in last possible extent
        } else if (i + 1 < MAX_EXTENTS) {
          if (chk && startingBlock[i + 1] == -1) {
            chk = false;        // its is in the last  used extent
          }
        }
        if (chk) {
          if (errOnly) {
            sb.append("   ").append(toStringBuilder(null)).append("\n");
            sb.append(line);
          }
          sb.append("       *** ").append(seedName).append(" bitMap not full and extent is not last one\n");
        }
        nb = 0;
        long mask = bitMap[i] & 0x7fffffffffffffffL;
        while ((mask & 1) == 1) {
          mask = mask >> 1;
          nb++;
        }
        if (mask != 0) {
          if (errOnly) {
            sb.append("   ").append(toStringBuilder(null)).append("\n");
            sb.append(line);
          }
          sb.append("      *** ").append(seedName).append(" bitMap has non-contiguous used blocks! nb=").append(nb).
                  append(" ").append(Util.toHex(bitMap[i])).append("\n");

        }

      }

      // Starting block must be divisible by 64 evenly
      if ((startingBlock[i] % 64) != 0) {
        if (errOnly) {
          sb.append("   ").append(toStringBuilder(null)).append("\n");
          sb.append(line);
        }
        sb.append("      ***** starting block not divisible by 64!\n");
      }

      // earliest and latest times should be today!
      if (earliestTime[i] < 0 || (earliestTime[i] > 29100 && earliestTime[i] != 32700)) {
        if (errOnly) {
          sb.append("   ").append(toStringBuilder(null)).append("\n");
          sb.append(line);
        }
        sb.append("      **** warning: earliest time is not on this day=").append(earliestTime[i]).append("\n");
      }
      if ((latestTime[i] < 0 && latestTime[i] != -32700) || latestTime[i] > 29100) {
        if (errOnly) {
          sb.append("   ").append(toStringBuilder(null)).append("\n");
          sb.append(line);
        }
        sb.append("      **** warning: latest time is not on this day=").append(latestTime[i]).append("\n");
      }

      // If set, read in the data in the extent and verify its in the right place
      if (readData > 0) {
        if (extentbuf == null) {
          extentbuf = new byte[512];
        }
        MiniSeed ms;
        try {
          synchronized (lastExtentScratch) {
            datout.readBlock(lastExtentScratch, startingBlock[i], 64 * 512);
            int off = 0;
            while (off < nb * 512) {
              System.arraycopy(lastExtentScratch, off, extentbuf, 0, 512);
              boolean allzeros = true;
              for (int j = 0; j < 512; j++) {
                if (extentbuf[j] != 0) {
                  allzeros = false;
                  break;
                }
              }
              if (allzeros) {
                sb.append("   ").append(toStringBuilder(null)).append("\n");
                sb.append("      ***** all zeros at extent=").append(startingBlock[i]).append(" blk=").
                        append(off / 512).append(" ").append(seedName).append("/").append(julian).append("\n");
                off += 512;
              } else {
                try {
                  ms = new MiniSeed(extentbuf);
                  if (readData >= 2) {
                    sb.append("off=").append(off).append(" ").append(ms.toStringBuilder(null)).append("\n");
                  }
                  // Mini-seed channel name must agree
                  if (!Util.stringBuilderEqual(ms.getSeedNameSB(), seedName)) {
                    if (errOnly) {
                      sb.append("   ").append(toStringBuilder(null)).append("\n");
                      sb.append(line);
                    }
                    sb.append("      **** extent=").append(startingBlock[i]).append(" blk=").append(off / 512).
                            append(" channel is not same=").append(ms.getSeedNameSB()).append(" ").
                            append(ms.getTimeString()).append(" ").append(ms.getJulian()).append("\n");
                  }

                  // It must be from the right julian day
                  if (ms.getJulian() != julian) {
                    if (errOnly) {
                      sb.append("   ").append(toStringBuilder(null)).append("\n");
                      sb.append(line);
                    }
                    sb.append("      **** Julian date does not agree data=").append(ms.getJulian()).append(" vs ").append(julian).append("\n");
                  }

                  // check that times are in the range!
                  short st = (short) ((ms.getHour() * 3600 + ms.getMinute() * 60 + ms.getSeconds()) / 3);
                  short en = (short) (st + ms.getNsamp() / ms.getRate() / 3.);
                  if (st < earliestTime[i] || st > latestTime[i]) {
                    if (errOnly) {
                      sb.append("   ").append(toStringBuilder(null)).append("\n");
                      sb.append(line);
                    }
                    sb.append("      ***** start time of data too out of range ").append(Util.asctime(ms.getTimeInMillis())).append("\n");
                  }

                  // List a line about the record unless we are in errors only mode
                  long time = ms.getTimeInMillis();
                  long diff;

                  // if this is a trigger or other record it will have no samples (but a time!) so
                  // do not use it for discontinuity decisions
                  if (ms.getNsamp() > 0) {
                    if (lastDate > 0) {
                      diff = time - lastDate - lastlength;
                      if (diff > 1) {
                        sb.append("=").append(lastString).append("\n");
                        sb.append("   *** discontinuity = ").append(diff).append(" ms! ends at ").
                                append(ms.getTimeString()).append(" blk=").append(startingBlock[i] + off / 512).append("\n");
                        sb.append("/").append(ms.toStringBuilder(null)).append(" iblk=").
                                append(startingBlock[i] + off / 512).append("/").
                                append(ms.getBlockSize()).append("\n");
                        Util.prt("-" + lastString);
                        Util.prt(")" + ms.getSeedNameSB() + "   *** discontinuity = " + diff + " ms? ends at " + ms.getTimeString()
                                + " blk=" + (startingBlock[i] + off / 512));
                        Util.prt("{" + ms.toStringBuilder(null) + " iblk=" + (startingBlock[i] + off / 512) + "/" + ms.getBlockSize());
                      }
                    }
                    lastlength = (long) (ms.getNsamp() * 1000 / ms.getRate());
                    lastDate = time;
                    Util.clear(lastString).append(ms.toStringBuilder(null)).append(" iblk=").
                            append(startingBlock[i] + off / 512).append("/").append(ms.getBlockSize());
                  }

                  off += ms.getBlockSize();
                } catch (IllegalSeednameException e) {
                  Util.prt("toStringDetail() IllegalSeednameException =" + e.getMessage()
                          + "extent=" + startingBlock[i] + " blk=" + off / 512);
                  //for(int j=0; j<32; j++) Util.prt(j+" Dump "+extentbuf[j]+" "+(char) extentbuf[j]);
                  sb.append("      **** extent=").append(startingBlock[i]).append(" blk=").
                          append(off / 512).append(" IllegalSeednameException ").append(e.getMessage());
                  off += 512;
                }
              }
            }         // end while(off <65536) 
          }     // synchronized(lastExtentScratch)
        } catch (IOException e) {
          Util.prt("IOException trying to read data from extent "
                  + toStringBuilder(null) + " blk=" + startingBlock[i]);
        }
      }
    }
    return sb;

  }
  /**
   * get the index of indexBlocks (mainly used to synchronize on this object
   *
   * @return The indexBlocks treeMap
   */
  //public static TLongObjectHashMap getIndexBlocks() {return indexBlocks;}
  static long nio;
  static long lastnio;

  /**
   * reset the indexBlocks collection and number of ios if it has not yet been done
   */
  public static void init() {
    if (indexBlocks == null) {
      //indexBlocks = Collections.synchronizedMap(new TreeMap<IndexKey, IndexBlock>());
      //indexBlocksLock=new Integer(0);
      nio = 0;
    }
  }

  /**
   * Main writer of miniseed data to disk
   *
   * @param ms The miniseed block to write
   * @param sendHydra - if true this block is forwarded to Hydra
   * @param allow4k If true, 4 k buffers will go into the edge instead of being converted to 512s
   * @param tag A descriptive tag for the submitter of this data (for debug and error feedback)
   * @param par The EdgeThread caller mainly to allow logging to go to right place
   * @return true if block was written
   * @throws IllegalSeednameException if same is found decoding the miniseed
   */
  public static boolean writeMiniSeedCheckMidnight(MiniSeed ms, boolean sendHydra, boolean allow4k, String tag, EdgeThread par)
          throws IllegalSeednameException//, IOException, EdgeFileCannotAllocateException,
  //     EdgeFileReadOnlyException 
  {
    if (!allow4k && ms.getBlockSize() > 512) {
      MiniSeed[] mss = ms.toMiniSeed512();
      boolean written = false;
      if (mss != null) {
        for (MiniSeed ms1 : mss) {
          //Util.prt(mss[i].toString());
          written |= writeMiniSeedCheckMidnight(ms1, sendHydra, tag, par);
        }
      }
      return written;
    } else {
      return writeMiniSeedCheckMidnight(ms, sendHydra, tag, par);
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
   * Main writer of miniseed data to disk
   *
   * @param ms The miniseed block to write
   * @param sendHydra - if true this block is forwarded to Hydra
   * @param tag A descriptive tag for the submitter of this data (for debug and error feedback)
   * @param par The EdgeThread caller mainly to allow logging to go to right place
   * @return true if block was written
   * @throws IllegalSeednameException if same is found decoding the miniseed
   */
  public static boolean writeMiniSeedCheckMidnight(MiniSeed ms, boolean sendHydra, String tag, EdgeThread par)
          throws IllegalSeednameException//, IOException, EdgeFileCannotAllocateException,
  //     EdgeFileReadOnlyException 
  {
    // check to see if this block spans the boundary, if it does, split it up and write
    if (sendHydra) {
      Hydra.send(ms);
    }
    //GregorianCalendar end = new GregorianCalendar();
    //end.setTimeInMillis(ms.getGregorianCalendarTruncated().getTimeInMillis());// set to begin of buf
    //end.add(Calendar.MILLISECOND, (int)(ms.getNsamp()/ms.getRate()*1000.));// end of buf
    // is this on another day?
    if ((ms.getEncoding() == 11 || ms.getEncoding() == 10)
            && /*end.get(Calendar.DAY_OF_MONTH) !=  
            ms.getGregorianCalendarTruncated().get(Calendar.DAY_OF_MONTH) */ 
            ms.getTimeInMillis() / 86400000L != ms.getNextExpectedTimeInMillis() / 86400000L
            && // spans a midnight boundary
            ms.getNsamp() > 0
            && !(ms.getSeedNameSB().charAt(7) == 'L' && ms.getSeedNameSB().charAt(8) == 'O' && 
            ms.getSeedNameSB().charAt(9) == 'G')) {
      par.prta(Util.clear(tmpthr2).append("MS EOD Start ").append(ms.getSeedNameSB()).append(" ms=").
              append(Util.asctime2(ms.getTimeInMillis(), null)).
              append(" end=").append(Util.ascdatetime2(ms.getNextExpectedTimeInMillis(), null)).
              append(" ns=").append(ms.getNsamp()).append(" rt=").append(ms.getRate()));
      int[] data = null;      // Need to decompress the data
      int nsamp = ms.getNsamp();             // need original number of samples
      long dayMillis = (LeapSecondDay.isLeap(ms.getJulian()) ? 86401000 : 86400000L);
      int nsleft = (int) // Time to midnight * rate
              ((dayMillis - ((ms.getHour() * 3600000L + ms.getMinute() * 60000 + ms.getSeconds() * 1000 + ms.getUseconds() / 1000) % dayMillis)
              + ((long) (1. / ms.getRate() * 1000 - 1.)))
              * // ms per sample -1.
              ms.getRate() / 1000. + 0.0000001);               // #sec left in day*rate 

      par.prta(Util.clear(tmpthr2).append("MS EOD cut off ").append(ms.getSeedNameSB()).append(" start=").
              append(Util.ascdatetime2(ms.getTimeInMillis(), null)).append(" ns=").append(nsamp).
              append(" nsleft=").append(nsleft).append(" lsec=").append(LeapSecondDay.isLeap(ms.getJulian())));
      if (nsleft >= nsamp) {
        return IndexBlock.writeMiniSeed(ms, tag);  // just write it out
      }      // Do compression to make a mini-seed from here to midnight
      try {
        byte[] frames = new byte[ms.getBlockSize() - 64];
        System.arraycopy(ms.getBuf(), 64, frames, 0, ms.getBlockSize() - 64);
        if (ms.getEncoding() == 11) {
          data = Steim2.decode(frames, ms.getNsamp(), false);// decompress it
        } else if (ms.getEncoding() == 10) {
          data = Steim1.decode(frames, ms.getNsamp(), ms.isSwapBytes());
          if (Steim1.getRIC() != data[ms.getNsamp() - 1]) {
            par.prt(Util.clear(tmpthr2).append("    ** Steim1 RIC error RIC=").append(Steim1.getRIC()).append("!=").append(data[ms.getNsamp() - 1]));
          }
        }
        //par.prt(ms.getSeedNameSB()+" ******* Steim 1 EOD encoding not supported."+ms.toString());//data = Steim.decode(frames, 10000, false);
      } catch (SteimException e) {
        par.prt(Util.clear(tmpthr2).append(ms.getSeedNameSB()).append("**** steim exception decompressing end of day packet.").append(ms.toStringBuilder(null)));
      }
      int[] ymd = SeedUtil.fromJulian(ms.getJulian());
      int doy = ms.getDay();
      int secs = ms.getHour() * 3600 + ms.getMinute() * 60 + ms.getSeconds(); // Get seconds from start time exactly from MS
      // This calculation was replace 9/16/2008 when LH data with .9999 seconds would not get correct start time 
      //int secs = (int) (ms.getGregorianCalendar().getTimeInMillis() % 86400000L)/1000;
      int micros = ms.getUseconds();
      par.prta(Util.clear(tmpthr2).append("MS EOD ").append(ms.getSeedNameSB()).append("start=").append(secs).
              append(" mics=").append(micros).append(" nsleft=").append(nsleft).
              append(" y=").append(ymd[0]).append(" doy=").append(doy));
      if (ms.getEncoding() == 11) {
        RawToMiniSeed.addTimeseries(data, nsleft, ms.getSeedNameSB(), ymd[0], doy,
                secs, micros, ms.getRate(), ms.getActivityFlags(), ms.getIOClockFlags(),
                ms.getDataQualityFlags(), ms.getTimingQuality(), 0, par);
        //prta("ORG last="+ms.toString());
        /*ms = new MiniSeed(*/
        RawToMiniSeed.forceout(ms.getSeedNameSB());//);
      } else if (ms.getEncoding() == 10) {
        try {
          // Make little arrays of data of exact length because IRIS Steim I compressor is pretty dumb
          int[] d = new int[nsleft];
          System.arraycopy(data, 0, d, 0, nsleft);
          SteimFrameBlock fb = Steim1.encode(d, 7); // Compress it
          byte[] buf = new byte[512];              // Use this buffer to make new MiniSeed blocks
          byte[] b = fb.getEncodedData();
          System.arraycopy(ms.getBuf(), 0, buf, 0, 64);// Copy the header for original block
          System.arraycopy(b, 0, buf, 64, b.length);// copy the compressed bytes after header
          MiniSeed ms1 = RawToMiniSeed.getMiniSeed(buf, 0, 512);         // Make a miniseed block
          ms1.setNsamp(nsleft);                     // set number of samples
          int ncomp = MiniSeed.getNsampFromBuf(buf, ms.getEncoding());// How many were really compressed
          par.prta(Util.clear(tmpthr2).append("MS EOD Steim1 Last block nsblock=").append(ncomp).
                  append(" nleft=").append(nsleft).append(" ns=").append(nsamp).append(" ms=").append(ms1.toStringBuilder(null)));
          // Sometimes the IRIS code shorts the compression that is 410 samples will only compress the first 408
          // When this happens we need to add one more little short block before the midnight boundary
          if (ncomp < nsleft) {      // did it short us
            ms1.setNsamp(ncomp);    // reset NSAMP to the number actually compressed
            IndexBlock.writeMiniSeed(ms1, tag);  // Write out shorted block
            d = new int[nsleft - ncomp];    // Number of samples still needed (ncomp to nsleft)
            System.arraycopy(data, ncomp, d, 0, nsleft - ncomp);// Now do remainder of samps
            par.prta(Util.clear(tmpthr2).append("MS EOD Steim1 Last small *** block ns=").append(nsleft - ncomp));
            fb = Steim1.encode(d, 7);
            b = fb.getEncodedData();
            System.arraycopy(b, 0, buf, 64, b.length);
            ms1.load(buf);
            ms1.setNsamp(nsleft - ncomp);   // set number of samples
            int ncomp2 = MiniSeed.getNsampFromBuf(buf, ms.getEncoding());// How many were really compressed
            long g = ms.getTimeInMillis();// Use trucated time and add back hundred of millis
            g = g + (int) (ncomp * 1000. / ms.getRate() + 0.4);// time is org time + ncomp samples
            ms1.setTime(g, ms.getUseconds() % 1000);       // Set time adding back the hundreds of millis
            par.prt(Util.clear(tmpthr2).append(" *** Got an unusual Steim 1 Last into 3 blocks ncomp2=").append(ncomp2).
                    append(" last ns=").append(nsleft - ncomp).append(" ms1=").append(ms1.toStringBuilder(null)));
          }
          IndexBlock.writeMiniSeed(ms1, tag);        // Write it out

          // There should be samples left from nsleft to nsamp, compress them to block on next day
          d = new int[nsamp - nsleft];
          System.arraycopy(data, nsleft, d, 0, nsamp - nsleft);// Now do remainder of samps
          fb = Steim1.encode(d, 7);
          b = fb.getEncodedData();
          System.arraycopy(b, 0, buf, 64, b.length);
          ms1.load(buf);
          ms1.setNsamp(nsamp - nsleft);
          long g = ms.getTimeInMillis();// Use trucated time and add back hundred of millis
          g += (int) (nsleft * 1000. / ms.getRate() + 0.4);// Original time + nsleft samples
          ms1.setTime(g, ms.getUseconds()/100 % 10);       // Set time adding back the hundreds of millis digit
          ncomp = MiniSeed.getNsampFromBuf(buf, ms.getEncoding());// How many were really compressed          
          par.prta(Util.clear(tmpthr2).append("MS EOD Steim1 1st block nsblock=").append(ncomp).
                  append(" ns=").append(nsamp).append(" nleft=").append(nsleft).append(" ms=").append(ms1.toStringBuilder(null)));
          // Sometimes the IRIS Steim 1 compression code shorts the sample compressed so 410 samples only compresses 408
          // If this happens, correct the block, write it and make a new short one after it
          if (ncomp < nsamp - nsleft) {  // Did the short happen
            ms1.setNsamp(ncomp);    // reset NSAMP to the number actually compressed
            IndexBlock.writeMiniSeed(ms1, tag);// Write the shorted block
            d = new int[nsamp - nsleft - ncomp];  // This many samples remain
            System.arraycopy(data, nsleft + ncomp, d, 0, nsamp - nsleft - ncomp);// Now do remainder of samps
            par.prta(Util.clear(tmpthr2).append("MS EOD Steim1 1st small block ns=").append(nsamp - nsleft - ncomp));
            fb = Steim1.encode(d, 7);
            b = fb.getEncodedData();
            System.arraycopy(b, 0, buf, 64, b.length);
            ms1.load(buf);
            ms1.setNsamp(nsamp - nsleft - ncomp);
            g = ms.getTimeInMillis();// Use trucated time and add back hundred of millis
            g += (int) ((nsleft + ncomp) * 1000. / ms.getRate() + 0.4);// Add on nsamps of tim
            ms1.setTime(g, ms.getUseconds() /100 % 10);       // Set time adding back the hundreds of millis
            int ncomp2 = MiniSeed.getNsampFromBuf(buf, ms.getEncoding());// How many were really compressed
            par.prt(Util.clear(tmpthr2).append(" *** Got an unusual Steim 1 1st into 3 blocks ncomp2=").append(ncomp2).
                    append(" last ns=").append(nsamp - nsleft - ncomp).append(" ms1=").append(ms1.toStringBuilder(null)));
          }
          IndexBlock.writeMiniSeed(ms1, tag);
          RawToMiniSeed.freeMiniSeed(ms1);    // Free the miniseed block
          return true;
        } catch (SteimException e) {
          par.prt(Util.clear(tmpthr2).append("Got steim I compression error trying to do midnight e=").append(e).
                  append(" ms=").append(ms.toStringBuilder(null)));
          return false;
        } catch (IOException e) {
          par.prt(Util.clear(tmpthr2).append("Got steim I IO error trying to do midnight e=").append(e).append(" ms=").append(ms.toStringBuilder(null)));
          return false;
        }
      }
      //prta("EOD last="+ms.toString());

      int startJulian = ms.getJulian() + 1;
      while (nsamp > nsleft) {
        ymd = SeedUtil.fromJulian(startJulian);
        doy = SeedUtil.doy_from_ymd(ymd);
        long start = ms.getHour() * 3600000000L + ms.getMinute() * 60000000L + ms.getSeconds() * 1000000L 
                + ms.getUseconds();

        // this is the start time in microseconds
        start += (long) (nsleft / ms.getRate() * 1000000. + 0.1);// Add the nleft to this start time
        start = (start % dayMillis);             // this will be on next day, so remove the whole day portion (unsecs into next day)
        secs = (int) (start / 1000000);               // convert micros onto next day to whole seconds
        micros = (int) (start % 1000000);           // and microseconds
        // drop the portion of the data already put in prior day
        // Compress the data out and force the short block out.

        int maxsamp = (int) (86400 * ms.getRate() + .5);
        if (maxsamp <= 0) {
          maxsamp = 86400;         // this prevents weird rate=0 packets from doing us in
        }
        int n = nsamp - nsleft;
        if (n > maxsamp) {
          n = maxsamp;
        }
        par.prta(Util.clear(tmpthr2).append("MS EOD Next day ").append(ms.getSeedNameSB()).
                append(" secs=").append(secs).append(" mics=").append(micros).append(" ns=").append(nsamp - nsleft).
                append(" n=").append(n).append(" y=").append(ymd[0]).append(" doy=").append(doy));
        System.arraycopy(data, nsleft, data, 0, n);
        RawToMiniSeed.addTimeseries(data, n, ms.getSeedNameSB(),
                ymd[0], doy, secs, micros, ms.getRate(), ms.getActivityFlags(),
                ms.getIOClockFlags(), ms.getDataQualityFlags(), ms.getTimingQuality(),
                0, par);
        RawToMiniSeed.forceout(ms.getSeedNameSB());
        startJulian++;
        nsleft += n;    // number compressed so far
      }
      return true;
    } else {
      return IndexBlock.writeMiniSeed(ms, tag);
    }

  }

  /**
   * write a miniseed block of length len into this file. We will find the Mini-seed Actual writing
   * to a day file occurs in the IndexFile.writeMiniSeed() but this class has to handle everything
   * that can happen before a currently open day file indexBlock can be used 1) Verify that the seed
   * name make sense, 2) If the julian date is out of the range, this sends data to the future or
   * old file 3) If the index is not found in the TreeMap of open indices, it is opened/create 4)
   * IndexBlock.writeMiniSeed() is called to put the data in the day file using the index block
   * created (this is a non-static call) - bit maps etc are updated there
   *
   * @param ms The mini-seed block to write out
   * @param tag A tag representing who wrote this block for debug purposes
   * @return false if image is shutting down and no new input is allowed.
   * @throws IllegalSeednameException if it the mini-seed name does not pass MasterBlock.chkSeedname
   */
  public static boolean writeMiniSeed(MiniSeed ms, String tag)
          throws IllegalSeednameException//, IOException, EdgeFileCannotAllocateException,
  //    EdgeFileReadOnlyException 
  {
    boolean error = false;
    boolean noPage = false;
    IndexBlock idx = null;
    IndexFile ifl = null;
    try {
      init();
      if (IndexFile.isShuttingDown()) {
        Util.prta("IF: shutting down : Did not write ms=" + ms.toStringBuilder(null));
        return false;
      }

      // If this is a "LOG" component, write it out into console logging area, if so we are done
      if (MiniSeedLog.write(ms)) {
        return true;
      }

      //synchronized(indexBlocksLock) {
      Util.clear(whoHas).append(tag);
      //long t1=System.currentTimeMillis();
      //Util.prt("synchronize nio="+nio+" blocks ="+indexBlocksLock.hashCode());
      int julian = ms.getJulian();
      MasterBlock.checkSeedName(ms.getSeedNameSB());  // throw exception if illegal
      synchronized (indexBlocks) {
        if (IndexFile.isShuttingDown()) {
          return false;      // catch thing waiting on the lock
        }
        nio++;
        if (today == 0) {
          today = SeedUtil.toJulian(System.currentTimeMillis());
        }
        if (julian > today) {       // reconsider today, it might be near midnight
          int soon = SeedUtil.toJulian(System.currentTimeMillis() + 1800000); // julian 30 minutes from now
          /*if(Util.getOS().indexOf("Mac") >=0)
            soon = SeedUtil.toJulian(2005,5,1);     // DEBUG: Force date*/
          if (soon != today) {
            Util.prta(tag + " WrMS: Day roll=" + today + " to " + soon + " nio=" + nio);
            today = soon;

            if (future != null) {
              if (future.length() == 0) {
                Util.prta("Dropping zero length future file=" + future.getFilename());
                File futf = new File(future.getFilename());
                if (futf.exists()) {
                  futf.delete();
                }
              }
              future.close();
            }
            future = new RawDisk(IndexFile.getPath(0) + "future_" + SeedUtil.fileStub(soon) + "_"
                    + IndexFile.getNode() + ".ms", "rw");

            if (past != null) {
              if (past.length() == 0) {
                Util.prta("Dropping zero length past file =" + past.getFilename());
                File pastf = new File(past.getFilename());
                if (pastf.exists()) {
                  pastf.delete();
                }
              }
              past.close();
            }
            past = new RawDisk(IndexFile.getPath(0) + "past_" + SeedUtil.fileStub(soon) + "_"
                    + IndexFile.getNode() + ".ms", "rw");

            if (overflow != null) {
              if (overflow.length() == 0) {
                Util.prta("Dropping zero length overflow file =" + overflow.getFilename());
                File pastf = new File(overflow.getFilename());
                if (pastf.exists()) {
                  pastf.delete();
                }
              }
              overflow.close();
            }
            overflow = new RawDisk(IndexFile.getPath(0) + "overflow_" + SeedUtil.fileStub(soon) + "_"
                    + IndexFile.getNode() + ".ms", "rw");

            Util.prta(tag + " WrMS: Day roll done!");
          }

          if (julian > today) {      // This data is from the future, Put it in the future file
            if (future == null) {
              future = new RawDisk(IndexFile.getPath(0) + "future_" + SeedUtil.fileStub(soon) + "_"
                      + IndexFile.getNode() + ".ms", "rw");
            }
            int blk = (int) (future.length() / 512L);
            future.writeBlock(blk, ms.getBuf(), 0, (ms.getBlockSize() + 511) / 512 * 512);
            return true;
          }
        }

        // Now check to see if the data is too old
        if (julian <= (today - IndexFile.getNdays()) && IndexFile.getNdays() > 1) {
          if (past == null) {
            int soon = SeedUtil.toJulian(System.currentTimeMillis());
            past = new RawDisk(IndexFile.getPath(0) + "past_" + SeedUtil.fileStub(soon) + "_"
                    + IndexFile.getNode() + ".ms", "rw");
          }
          int blk = (int) (past.length() / 512L);
          past.writeBlock(blk, ms.getBuf(), 0, (ms.getBlockSize() + 511) / 512 * 512);
          return true;
        }
      }
      //long t2=System.currentTimeMillis();

      // The date is in the range of the possible files, get an index block for it
      //Util.prta("getIndex block nio="+nio+" files="+IndexFile.getFilesLock().hashCode());
      //synchronized(IndexFile.getFilesLock()) {
      if (IndexFile.isShuttingDown()) {
        return false;      // catch thing waiting on the lock
      }
      ifl = IndexFile.getIndexFile(julian);
      // if it is not open, ifl is null.  Open it.
      if (ifl == null) {
        if (dbg) {
          Util.prta(tag + "Need to open julian=" + julian + " nio=" + nio);
        }
        try {
          ifl = new IndexFile(julian, false, false);
        } catch (IOException e) {
          if (e.getMessage() == null) {
            Util.prta("**** non message error in open writeMiniSeed=" + e + " " + SeedUtil.fileStub(julian));
            Util.prta("ms=" + ms);
            e.printStackTrace();
          } else if (e.getMessage().contains("Too many open files")) {
            int nclosed = 0;
            int msec = 42300000;
            while (nclosed == 0) {
              nclosed += IndexFile.closeStale(msec);
              Util.prta("Attempt to closeStale for " + msec + " closed " + nclosed + " files");
              msec /= 2;
              if (msec < 600000) {
                Util.prt("IB: could not free up a file on too many open files!");
                Util.exit(1);
                break;
              }
            }
          }
          // Retry open now
          try {
            ifl = new IndexFile(julian, false, false);
          } catch (EdgeFileDuplicateCreationException e2) {
            Util.prt("***** rare duplicate creation found.  Handle!");
            ifl = IndexFile.getIndexFile(julian);
            if (ifl == null) {
              Util.prt("This is impossible - no index file found for duplicate creation!");
              Util.exit(0);
            }
          }
        } catch (EdgeFileDuplicateCreationException e) {
          Util.prt("***** rare duplicate creation found.  Handle!");
          ifl = IndexFile.getIndexFile(julian);
          if (ifl == null) {
            Util.prt("This is impossible - no index file found for duplicate creation!");
            Util.exit(0);
          }
        }
      }
      //}
      //Util.prta("getIndex done nio="+nio);
      if (IndexFile.isShuttingDown()) {
        return false;      // catch thing waiting on the lock
      }
      idx = getIndexBlock(ms.getSeedNameSB(), julian);    // is it in the list?
      if (idx == null) {
        try {
          //Util.prta("IB: create new IndexBlock "+ms.getSeedNameSB()+" "+julian+"_"+tag);
          idx = new IndexBlock(ms.getSeedNameSB(), julian, false);
          //Util.prta("IB: created Index block="+idx);
        } catch (DuplicateIndexBlockCreatedException e) {
          SendEvent.debugEvent("DupIDXCreate", ms.getSeedNameSB() + " " + SeedUtil.fileStub(julian) + " " + tag, "IndexBlock");
          Util.prta(tag + "DupIDXCreateException:  this should never happen!!! I just checked it!!!!" + e.getMessage());
          e.printStackTrace();
          idx = getIndexBlock(ms.getSeedNameSB(), julian);  // It must be there now.
          if (idx == null) {
            return true;
          }
          Util.prta("IB: recovered from duplicate create idx=" + idx.toStringBuilder(null));
        }
      } //else Util.prta("Found the index block for "+ms.getSeedNameSB()+"="+julian);
      //}
      //  long t3=System.currentTimeMillis();
      try {
        if (udpHolding != null) {
          if(ms.getRate() > 0. && ms.getNsamp() > 0) udpHolding.send(ms);   // Send this block to the holdings server, if its timeseries
        }
      } catch (IOException e) {      // This is not a fatal error
        Util.prta("IB: writeMiniSeed() IOException sending holding e=" + e.getMessage());
      }
      int blkwritten = idx.writeMS(ms);   // Write out the block
    } //IOException, EdgeFileCannotAllocateException,EdgeFileReadOnlyException
    catch (EdgeFileCannotAllocateException e) {
      Util.prta("IB: writeMiniSeed() EdgeFileCannotAllocate : ms=" + ms + " e=" + e.getMessage());
      SendEvent.edgeSMEEvent("FileIdxAllc", "Alloc " + ms.getSeedNameString() + " " + Util.ascdatetime(ms.getTimeInMillis()) + " " + e.getMessage(), "IndexBlock");
      try {
        if (overflow == null) {
          int soon = SeedUtil.toJulian(System.currentTimeMillis());
          overflow = new RawDisk(IndexFile.getPath(0) + "overflow_" + SeedUtil.fileStub(soon) + "_"
                  + IndexFile.getNode() + ".ms", "rw");
        }
        int blk = (int) (overflow.length() / 512L);
        overflow.writeBlock(blk, ms.getBuf(), 0, (ms.getBlockSize() + 511) / 512 * 512);
        return true;
      } catch (IOException e2) {
        Util.prta("PANIC: failed to allocate write to overflow file had error e=" + e2);
        e2.printStackTrace();
      }
      e.printStackTrace();
      Util.clear(errMsg).append(e.getMessage());
      error = true;

    } catch (EdgeFileReadOnlyException e) {
      Util.prta("IB: writeMiniSeed() EdgeFileReadOnlyException : e=" + e.getMessage());
      e.printStackTrace();
      Util.clear(errMsg).append(e.getMessage());
      error = true;

    } catch (IOException e) {
      if (e.getMessage() == null) {
        Util.prta("IB: write mini-seed IOException but null message " + ifl + " " + idx);
        e.printStackTrace();
      } else if (e.getMessage().contains("Too many open files")) {
        int nclosed = 0;
        int msec = 42300000;
        while (nclosed == 0) {
          nclosed += IndexFile.closeStale(msec);
          msec /= 2;
          if (msec < 600000) {
            Util.prt("IB: could not free up a file on too many open files!");
            break;
          }
        }
      } else if (e.getMessage().contains("No space left on device")) {
        numberNoSpace++;
        if (System.currentTimeMillis() - lastErrorTime > 300000 || numberNoSpace % 1000 == 0) // limit output!
        {
          Util.prta("IB: write mini-seed no space left on device " + numberNoSpace + " " + e.getMessage());
        }
      } else if (e.getMessage().contains("Negative seek offset")) {
        Util.prta("IB: writeMiniSeed() IOException : e=" + e.getMessage());
        Util.prta("IB:=" + (idx == null ? "null" : idx.toStringBuilder(null)));
        e.printStackTrace();
        noPage = true;
      } else {
        Util.prta("IB: writeMiniSeed() IOException : e=" + e.getMessage());
        Util.prta("IB:=" + (idx == null ? "null" : idx.toStringBuilder(null)));
        e.printStackTrace();
      }
      Util.clear(errMsg).append(e.getMessage());
      error = true;

    }
    if (error) {
      if (System.currentTimeMillis() - lastErrorTime > 300000) {
        if (!noPage) {
          SendEvent.edgeEvent("ErrWrDisk",
                  "Error writing Edge disk on " + Util.getNode() + "/" + Util.getAccount() + " msg=" + errMsg, "IndexBlock");
        }
        Util.prt("page and emails out.");
        lastErrorTime = System.currentTimeMillis();
      }
    }
    // long t4=System.currentTimeMillis();
    // Util.prt("Seed() top="+(t2-t1)+" mid="+(t3-t2)+" write="+(t4-t3));
    // idx is the index block to use while writing this block out
    return true;
  }

  /**
   * return the hash code for a 12 character seedname and julian used for IndexBlocks. T
   * <br>
   * If run as part of a EdgeMom type process where there is an EdgeChannelServer object, the has
   * code is the ID from the channel shifted by 32 ORed with the julian date. This is unique for all
   * defined channels. An Exception is thrown if the channel is not in the EdgeChannelServer.
   * <br>
   * If there is no EdgeChannelServer object, this code is the Util.getHashSB(seedname) as a long,
   * left shifted by 24 bits. and ORed with the julian day *6. This puts the julian day in the lower
   * 3 bytes and the channel hash in the upper 5 bytes. If there are hash code conflicts then up to
   * 6 can be configured under the one code.
   *
   * @param s The 12 character seedname to hash using the ID from the EdgeChannelServer
   * @param julian The julian day for the lower portion
   * @return A hash code.
   * @throws gov.usgs.anss.edge.EdgeFileCannotAllocateException if channel is not available to
   * compute hash code
   */
  public static long getHash(StringBuilder s, int julian) throws EdgeFileCannotAllocateException {
    if (!EdgeChannelServer.exists()) {       // Not being run as part of an EdgeMom - use older hash code.
      return getHashSBNew(s) << 24 + julian * 6;
    }
    Channel c = EdgeChannelServer.getChannel(s);
    long hash;
    if (c != null && c.getID() > 0) {
      int id = c.getID();
      hash = id;
      hash = hash << 32 | julian;
      return hash;
    }
    Util.prta("IB: getHash() channel unknown! " + s + " " + julian);
    SendEvent.edgeSMEEvent("IBHashBad", "GetHash did not have channel! " + s, "IndexBlock");
    EdgeFileCannotAllocateException e = new EdgeFileCannotAllocateException("GetHash did not have channel! " + s);
    e.printStackTrace();
    throw e;
  }

  private static final int[] seedswap = {2, 3, 4, 5, 6, 0, 1, 11, 8, 7, 9, 10};  // This is the order to make miniseed header to NNSSSSSCCCLL

  public static long getHashOld(StringBuilder s, int julian) {
    int j;
    byte c;
    int len = s.length();
    long val = 0;
    long mult = 1;
    for (int i = 0; i < 12; i++) {
      if (seedswap[i] < len) {
        c = (byte) s.charAt(seedswap[i]);
      } else {
        c = ' ';
      }
      if (c == ' ' || c == '-' || c == 0) {
        j = 36;
      } else if (c >= 'A') {
        j = c - 'A' + 10;
      } else {
        j = c - '0';
      }
      if (i == 0) {
        j = j + (julian % 15);     // Top most bits gets up to 14
      } else if (seedswap[i] >= 7) {            // These are the location codes
        if (j == 36) {
          j -= julian % 26;          // If its s space, subtract up to 26 from it)
        } else if (j < 10) {
          j += julian % 26 - i;
          if (j < 0) {
            j = -j;
          }
        } // if its a number, Add up to 26 if the location code is a number
        else {
          j -= julian % 26 - i;
          if (j < 0) {
            j = -j;
          }
        }
        if (seedswap[i] != 7 && seedswap[i] != 8 && seedswap[i] != 11) {
          julian /= 26;                 // We put this in the location code
        }
      }

      if (seedswap[i] != 7) {
        val = val * 37 + j;
      } else {
        if (c >= 'a') {
          mult = -1;
          j = c - 'a';    // convert to uppercase
        }
        val = val * 37 + j;
      }   // correct  for lower case in position 10
    }
    return mult * val;
  }

  /**
   * This taks first and last character coded to 37 states adds them and shifts them by 74. This
   * works well for SeedNames because 74^6 =1.642e11 < 2<39 = 5.49e11 so it fits in 39 bits. When
   * shifted by 24 and then adding the julian this should give a unique hash code every time. The
   * chances of the two encodings being offset by equal amounts to some other set seems unlikely.
   * @param s @return
   */
  public static long getHashSBNew(StringBuilder s) {
    int j, j2;
    byte c, c2;
    long mult = 1;
    for (int i = 0; i < 12; i++) {
      // code the character to ' '=36, 0-9 = 0-9 and UC letters are 10-36
      c = (byte) s.charAt(i);
      c2 = ' ';
      if (s.length() >= 12 - i) {
        c2 = (byte) s.charAt(11 - i);
      }
      if (c == ' ' || c == '-' || c == 0) {
        j = 36;
      } else if (c >= 'A') {
        j = c - 'A' + 10;
      } else {
        j = c - '0';
      }
      if (c2 == ' ' || c2 == '-' || c2 == 0) {
        j2 = 36;
      } else if (c2 >= 'A') {
        j2 = c2 - 'A' + 10;
      } else {
        j2 = c2 - '0';
      }
      mult = mult * 74 + j + j2;        // put each 74^6 = 1.642e11 < 2<39 = 5.49e11 so it fits in 39 bits
    }
    return mult;
  }

  /**
   * /** given a seed name and julian day, return the already existing index block
   *
   * @param sn The 12 character seedname
   * @param jul The julian day
   * @return The index block, null if it has not previously been created. DO synchronize this code,
   * it always seems to lead to a dead lock I do not understand
   * @throws gov.usgs.anss.edge.EdgeFileCannotAllocateException if channel is not available to
   * compute hash code
   */
  public static IndexBlock getIndexBlock(StringBuilder sn, int jul) throws EdgeFileCannotAllocateException {
    long hash = getHash(sn, jul);
    IndexBlock idx;
    synchronized (indexBlocks) {
      Util.clear(whoHas).append("getIndexBlock").append(sn).append("/").append(jul);
      // Try the normal Hash
      idx = indexBlocks.get(hash);
      //Util.prta("Get Index block for "+sn+"/"+jul+" hash="+getHash(sn,jul));
      if (idx != null) {   // there is a block here, make sure its the right one
        if (!Util.stringBuilderEqual(idx.getSeedName(), sn) || idx.julian != jul) { // Is this not the right block
          // Its not the right block, call getIndexBlockHash to find the next hash code that is free
          long oldHash = hash;
          hash = getIndexBlockHashCode(sn, jul);     // call to get the actual usable hash
          //Util.prta("*** INFO: Hash codes of "+idx.getSeedName()+"/"+idx.getJulian()+"  = "+sn+"/"+jul+
          //        " collide - get new hash="+Util.toHex(hash)+" was "+Util.toHex(oldHash)+" "+(hash-oldHash));
          idx = indexBlocks.get(hash);              // return the block at the correct code
        }
      }
    }
    return idx;
  }

  /**
   * /** given a seed name and julian day, return the unique hash code for this block
   *
   * @param sn The 12 character seedname
   * @param jul The julian day
   * @return The index block hash code if the block exists, or the one which would be assigned to it
   * now if it does not. DO synchronize this code, it always seems to lead to a dead lock I do not
   * understand
   * @throws gov.usgs.anss.edge.EdgeFileCannotAllocateException if channel is not available to
   * compute hash code
   */
  public static long getIndexBlockHashCode(StringBuilder sn, int jul) throws EdgeFileCannotAllocateException {
    // This check is hear because we saw some zero seednames being created and looked up.  Report to figure this out.
    if (sn.charAt(0) == 0) {
      new RuntimeException("IndexBlock.getIndexBlock with zero seedname exception").printStackTrace();
    }
    IndexBlock idx;
    long hash = getHash(sn, jul);     // This is the normal hash code for the lbock
    int i = 0;
    synchronized (indexBlocks) {
      Util.clear(whoHas).append("getIndexBlock").append(sn).append("/").append(jul);
      boolean done = false;
      while (!done) {
        idx = indexBlocks.get(hash);    // get the block
        //Util.prta("Get Index block for "+sn+"/"+jul+" hash="+getHash(sn,jul));
        if (idx != null) { // a block exists at this hash, make sure its the right one!
          if (!Util.stringBuilderEqual(idx.getSeedName(), sn) || idx.julian != jul) { // Its not the right one, look for a free one
            if (i >= 1) {
              Util.prta("*** INFO: Hash codes of " + idx.getSeedName() + "/" + idx.getJulian() + "  = "
                      + sn + "/" + jul + " collide - try " + i + " increment! hash=" + Util.toHex(hash));
            }
            hash++;
          } else {
            done = true;   // Its the right hash code for this seedname and julian
          }
        } else {
          done = true;     // This hash code is free, there is no index block for this seedname and julian
        }
      }
      i++;
    }
    return hash;
  }

  /**
   * updateJulian causes all Index Block being held for a julian day to be written to disk, and
   * perhaps more importantly, to the EdgeBlockQueue so that the replicators get fresh copies! This
   * is triggered by the IndexFileServer after sending out a copy of the index file, it triggers one
   * of these so the index file is updated with any cached data for the index blocks.
   *
   * @param jul The julian day about to be closed.
   */
  public static void updateJulian(int jul) {
    Util.prta(Util.clear(tmpthr).append("IndexBlock: updateJulian()=").append(jul));
    if (indexBlocks.isEmpty()) {
      return;       // read only files do not build up these
    }
    synchronized (indexBlocks) {
      Util.clear(whoHas).append("updateJulian ").append(jul);
      IndexBlock idx;
      TLongObjectIterator<IndexBlock> it = indexBlocks.iterator();
      while (it.hasNext()) {
        it.advance();
        idx = it.value();
        if (idx.getJulian() == jul) {
          try {
            idx.updateBlock("UpdJul");
          } catch (IOException e) {
            Util.IOErrorPrint(e, "updateJulian() : IO error");
          }
        }
      }
    }
  }

  /**
   * close/write all index block that are open for a given julian day. Called by IndexFile when a
   * day file is about to be closed to insure no attempts to write through the index blocks will
   * occur
   *
   * @param jul The julian day about to be closed.
   * @return The number of index blocks removed from the indexBlocks hash
   */
  public static int closeJulian(int jul) {
    if (dbg) {
      Util.prta(Util.clear(tmpthr).append("IndexBlock: need IBL start closing julian=").append(jul));
    }
    int nfree = 0;
    if (indexBlocks.isEmpty()) {
      return 0;       // read only files do not build up these
    }
    synchronized (indexBlocks) {
      Util.clear(whoHas).append("closeJulian ").append(jul);
      IndexBlock idx;
      if (indexBlocks == null) {
        return 0;
      }
      TLongObjectIterator<IndexBlock> it = indexBlocks.iterator();
      while (it.hasNext()) {
        it.advance();
        idx = it.value();
        if (idx.getJulian() == jul) {
          it.remove();
          idx.close();
          nfree++;
        }
      }
    }
    if (dbg) {
      Util.prta(Util.clear(tmpthr).append("IndexBlock: drop IBL : end closing julian=").append(jul));
    }
    return nfree;
  }

  /**
   * write all index block that are a certain age without update and modified. Called by IndexFile
   * periodically to insure that end-of-day index blocks get written out.
   *
   * @param msec The age at which the block is considered stale and will be written in millis
   */
  public static void writeStale(long msec) {
    if (dbg) {
      Util.prta(Util.clear(tmpthr).append("IndexBlock: writeStale()"));
    }
    if (indexBlocks.isEmpty() || indexBlocks == null) {
      return;       // read only files do not build up these
    }
    long now = System.currentTimeMillis();
    int nstale = 0;
    synchronized (indexBlocks) {
      IndexBlock idx;
      TLongObjectIterator<IndexBlock> it = indexBlocks.iterator();
      while (it.hasNext()) {
        it.advance();
        idx = it.value();
        if (idx.isModified() && (now - idx.getUpdateTime()) > msec) {
          try {
            idx.updateBlock("stale");
            nstale++;
            if (dbg) {
              Util.prta(Util.clear(tmpthr).append("Update stale index ").append(idx.toStringBuilder(null)));
            }
          } catch (IOException e) {
            Util.IOErrorPrint(e, "Error writing stale indexblock" + idx.toString());
          }
        }
      }
      Util.prta(Util.clear(tmpthr).append("IB: writeStale #=").append(nstale));
    }

  }

  /**
   * creates a new instance of IndexBlock for the given seedname in the give Index. This method is
   * used by the Query tool and other "read only" uses of the files. The IndexFile must, of course,
   * be open and can (or should) be read only.
   *
   * @param name The 12 character seedName (must pass MasterBlock.checkSeedName()
   * @param ifile The previously opened IndexFile to use to read the blocks
   * @throws IllegalSeednameException if name does not pass MasterBlock.checkSeedName()
   * @throws IOException If this block cannot be created.
   * @throws gov.usgs.anss.edge.EdgeFileCannotAllocateException if channel is not available to
   * compute hash code
   */
  public IndexBlock(IndexFile ifile, StringBuilder name)
          throws IllegalSeednameException, IOException, EdgeFileCannotAllocateException {
    bb = ByteBuffer.wrap(buf);
    lastDate = 0;
    lastlength = 0;
    Util.clear(lastString).append("First Block");
    indexFile = ifile;
    julian = ifile.getJulian();
    Util.clear(seedName).append(name);
    Util.rightPad(seedName, 12);
    hash = getIndexBlockHashCode(seedName, julian);
    MasterBlock.checkSeedName(name);
    rw = indexFile.getRawDisk();
    datout = indexFile.getDataRawDisk();

    // If this is a "read type" or "first" get the first block
    // The block was found, read it and  populate data
    iblk = indexFile.getFirstIndexBlock(name);
    rw.readBlock(buf, iblk, 512);
    fromBuf();
    updateTime = System.currentTimeMillis();
    nextBlock = -1;

    // Find the starting block of the last extent, sets nextBlock, start & latest time, bit map 
    buildLastExtent();
  }

  /**
   * creates a new instance of Index block. Used by writeMiniSeed() when the index has not yet been
   * created on an Edge. This call requires the IndexFile for this julian day to have been created
   * and be open. If not an I/O error is likely.
   *
   * If the block already exists, it is read and the last extent determined. This extend is read and
   * the bit map made to agree with the number of blocks used in the extent (Uses non-zero filled to
   * allow membership).
   *
   * @param sdname The 12 character seedName (must pass MasterBlock.checkSeedName()
   * @param jul The julian day to open for this component
   * @param first If true, return the first index block in the chain for the record if not, return
   * the last index block in chain ready for writing!
   * @throws IllegalSeednameException If name does not pass MasterBlock.checkSeedName()
   * @throws IOException If this block cannot be created.
   * @throws EdgeFileCannotAllocateException if this block cannot be allocated
   * @throws EdgeFileReadOnlyException Cannot create writable IndexBlock on readonly file
   * @throws DuplicateIndexBlockCreatedException If first is set and this seedname index block has
   * already been created
   */
  public IndexBlock(StringBuilder sdname, int jul, boolean first)
          throws IllegalSeednameException, IOException, EdgeFileCannotAllocateException,
          EdgeFileReadOnlyException, DuplicateIndexBlockCreatedException {

    bb = ByteBuffer.wrap(buf);

    // These three are used across getNextIndex() calls so must be initialized when created
    lastDate = 0;
    lastlength = 0;
    Util.clear(lastString).append("First Block");
    // set the internal data
    julian = jul;
    Util.clear(seedName).append(sdname);
    Util.rightPad(seedName, 12);
    MasterBlock.checkSeedName(sdname);

    // Link up to file channels via the IndexFile object
    indexFile = IndexFile.getIndexFile(julian);
    rw = indexFile.getRawDisk();
    datout = indexFile.getDataRawDisk();

    // If this is a "read type" or "first" get the first block
    if (first) {
      iblk = indexFile.getFirstIndexBlock(sdname);
      hash = 0;
    } else {          // Get the last block, suitable for writing
      iblk = indexFile.getLastIndexBlock(sdname);
      synchronized (indexBlocks) {
        hash = getIndexBlockHashCode(seedName, julian);   // Get a free hash code for this seedname and julian
        indexBlocks.put(hash, (IndexBlock) this);
      }
      Util.clear(whoHas).append("createIDX: ").append(sdname).append("/").append(jul);
    }

    // If iblk < 1, this IndexBlock was not found, create it
    if (iblk < 0) {
      iblk = indexFile.newIndexBlock(sdname);
      nextIndex = -1;           // This is the end block
      currentExtentIndex = -1;
      for (int i = 0; i < MAX_EXTENTS; i++) {
        startingBlock[i] = -1;
        bitMap[i] = 0;
        earliestTime[i] = 32700;
        latestTime[i] = -32700;
      }
      nextBlock = -1;
      addExtent();
      //updateBlock("Create");    // addExtent() always does this
    } else {        // The block was found, read it and  populate data
      rw.readBlock(buf, iblk, 512);
      fromBuf();
      updateTime = System.currentTimeMillis();

      // The latest extent is pointed to by currentExtentIndex
      // but since it might be lazily written we need to read in the extent
      // and find the zero filled one to set the last block and recreate the bit mask
      buildLastExtent();
    }

  }

  /**
   * used by rebuildIndex to reset a newly created index chain so the extent allocated in
   * constructor is discarded
   */
  public synchronized void newIndexOnlyStart() {
    nextBlock = -1;
    currentExtentIndex = -1;
    startingBlock[0] = -1;    // wipe out the bogus extent
    bitMap[0] = 0;
  }
  //private final StringBuilder start = new StringBuilder(10);
  //private final StringBuilder end = new StringBuilder(10);

  /**
   * The latest extent is pointed to by currentExtentIndex but since it might be lazily written we
   * need to read in the extent and find the zero filled one to set the last block and recreate the
   * bit mask
   */
  private final byte[] scratch = new byte[512];

  private void buildLastExtent() throws IOException {
    long mask = 1;
    synchronized (lastExtentScratch) {   // Only one can use this buffer at a time
      datout.readBlock(lastExtentScratch, startingBlock[currentExtentIndex], 64 * 512); // read the whole extent
      //  Util.prta(seedName+" Rebuildindex/extent="+iblk+"/"+currentExtentIndex+" Start  earliest="+
      //      timeToString(earliestTime[currentExtentIndex])+" latest="+timeToString(latestTime[currentExtentIndex]));
      for (nextBlock = 0; nextBlock < 64; nextBlock++) {
        System.arraycopy(lastExtentScratch, nextBlock * 512, scratch, 0, 64);
        if (lastExtentScratch[nextBlock * 512] == 0) {
          break;
        }
        if (Util.isValidSeedName(MiniSeed.crackSeedname(scratch))) {
          try {
            int[] tm = MiniSeed.crackTime(scratch);
            short time = (short) ((tm[0] * 3600 + tm[1] * 60 + tm[2]) / 3);
            short endtime = ((short) (MiniSeed.crackNsamp(scratch) / MiniSeed.crackRate(scratch) / 3.));
            endtime += time;
            if (time < earliestTime[currentExtentIndex]) {
              earliestTime[currentExtentIndex] = time;
            }
            if (endtime > latestTime[currentExtentIndex]) {
              latestTime[currentExtentIndex] = endtime;
            }
          } catch (IllegalSeednameException e) {
            e.printStackTrace();
            return;
          }
        }
        bitMap[currentExtentIndex] = bitMap[currentExtentIndex] | mask;      // Or in this bit, block is in use
        mask = mask << 1;
      }
      if (nextBlock >= 64) {
        nextBlock = -1;     // mark ready to get next extent.
      }      //Util.prta(seedName+" Rebuild index/extent="+iblk+"/"+currentExtentIndex+" nextblk="+nextBlock+" earliest="+
      //    timeToString(earliestTime[currentExtentIndex], start)+" latest="+timeToString(latestTime[currentExtentIndex], end));
    }
  }

  /**
   * creates a new instance of Index block pointing to the iblk block. It appears that is method of
   * creating a block is out of favor (I found no usages in 3/2006).
   *
   * @param name The 12 character seedName (must pass MasterBlock.checkSeedName()
   * @param jul The julian day to open for this component
   * @param blk The block number in index file to read and open for writing.
   * @throws IllegalSeednameException If name does not pass MasterBlock.checkSeedName()
   * @throws IOException If this block cannot be created.
   * @throws gov.usgs.anss.edge.EdgeFileCannotAllocateException If channel is not available to
   * compute hash code.
   */
  public IndexBlock(StringBuilder name, int jul, int blk)
          throws IllegalSeednameException, IOException, EdgeFileCannotAllocateException {

    bb = ByteBuffer.wrap(buf);
    // These three are used across getNextIndex() calls so must be initialized when created
    lastDate = 0;
    lastlength = 0;
    Util.clear(lastString).append("First Block");

    julian = jul;
    Util.clear(seedName).append(name);
    Util.rightPad(seedName, 12);
    hash = getIndexBlockHashCode(seedName, julian);
    MasterBlock.checkSeedName(name);

    // Link up  to file channels via the IndexFile object
    indexFile = IndexFile.getIndexFile(julian);
    rw = indexFile.getRawDisk();
    datout = indexFile.getDataRawDisk();

    // If this is a "read type" or "first" get the first block
    // The block was found, read it and  populate data
    iblk = blk;
    rw.readBlock(buf, iblk, 512);
    fromBuf();
    updateTime = System.currentTimeMillis();
    if (!Util.stringBuilderEqual(seedName, name)) {
      Util.prta(" **** IndexBlock created for iblk=" + iblk
              + " does not match given name=" + name + " found=" + seedName);
      throw new IllegalSeednameException("iblk=" + iblk
              + " does not match given name=" + name + " found=" + seedName);
    }

    // Find the starting block of the last extent, set nextBlock, bitmask, start and latest time
    buildLastExtent();
  }

  /**
   * not used in real time, but allow a utility program to make an index block from 512 bytes a
   * julian and block number
   *
   * @param bf 512 bytes of index block
   * @param blk The block offset in the index file of this index block
   * @param jul The julian day for this file
   * @param rw
   * @throws gov.usgs.anss.edge.EdgeFileCannotAllocateException if hash code cannot be computed
   * because channel is not available
   */
  public IndexBlock(byte[] bf, int blk, int jul, RawDisk rw) throws EdgeFileCannotAllocateException {
    bb = ByteBuffer.wrap(buf);
    reload(bf, blk);
    iblk = blk;
    julian = jul;
    updateTime = System.currentTimeMillis();
    hash = getIndexBlockHashCode(seedName, julian);
    this.rw = rw;
  }

  /**
   * close up this block - write it out and reset all its file data so it cannot be used
   */
  private synchronized void close() {
    try {
      if (dbg) {
        Util.prta(Util.clear(tmpsb).append("IndexBlock close() ").append(seedName).append("/").append(julian));
      }
      updateBlock("close");
      rw = null;
      datout = null;
      indexFile = null;
      if (dbg) {
        Util.prta(Util.clear(tmpsb).append("IndexBlock close() ").append(seedName).append("/").append(julian).append(" done."));
      }
    } catch (IOException e) {
      Util.prt(Util.clear(tmpsb).append("IOException when closing idx=").append(seedName).append(" jul=").append(julian).append(" ").append(e.getMessage()));
    }
  }
  private final StringBuilder INDEXBLOCK = new StringBuilder().append("INDEXBLOCK  ");

  /**
   * update this block on disk
   *
   * @throws IOException If one occurs when writing the block
   */
  private synchronized void updateBlock(String txt) throws IOException {
    // Get current system time in millis and convert to seconds (truncating)
    updateTime = System.currentTimeMillis();
    toBuf();
    rw.writeBlock(iblk, buf, 0, 512);
    isModified = false;
    if (iblk > indexFile.getMaxIndex()) {
      if (dbg) {
        Util.prta(Util.clear(tmpsb).append("New Max index ").append(iblk).
                append(" was ").append(indexFile.getMaxIndex()).append(" ").append(toStringBuilder(null)).append(" ext=").append(currentExtentIndex));
      }
      indexFile.setMaxIndex(iblk);
    }
    //Util.prta(Util.clear(tmpsb).append("updateBlock ").append(txt).append(" ").append(iblk).append(" ").
    //    append(toStringBuilder(null)).append(" ext=").append(currentExtentIndex));

    if (dbg) {
      Util.prta(Util.clear(tmpsb).append("UpdIdx ").append(txt).append(" blk=").append(iblk).
              append("/").append(currentExtentIndex).append(" ").append(julian).append("_").
              append(IndexFile.getNode()).append(" ").append(new String(buf, 0, 12)));
    }
    // Update index block in replicated data as well!
    EdgeBlockQueue.queue(julian, IndexFile.getNode(), INDEXBLOCK,
            -1, // this is an index block!
            iblk, currentExtentIndex, buf, 0);
  }

  /**
   * update this block on disk but do not send on network
   *
   * @throws IOException If one occurs when writing the block
   */
  private synchronized void updateBlock2() throws IOException {
    // Get current system time in millis and convert to seconds (truncating)
    updateTime = System.currentTimeMillis();
    toBuf();
    rw.writeBlock(iblk, buf, 0, 512);
    Util.prta(Util.clear(tmpsb).append("updateBlock2 ").append(iblk).append(" ").
            append(toStringBuilder(null)).append(" ext=").append(currentExtentIndex));
    isModified = false;
    if (iblk > indexFile.getMaxIndex()) {
      //Util.prta("New Max2 index "+iblk+" was "+indexFile.getMaxIndex()+" "+toString()+" ext="+currentExtentIndex);
      indexFile.setMaxIndex(iblk);
    }

  }

  /**
   * update this block on disk but do not send on network
   *
   * @param rw The a rawdisk to the index files, used by fix routines in AnalyzeDisk or
   * IndexFile.analyze()
   * @throws IOException If one occurs when writing the block
   */
  private synchronized void updateBlockFix() throws IOException {
    // Get current system time in millis and convert to seconds (truncating)
    updateTime = System.currentTimeMillis();
    toBuf();
    rw.writeBlock(iblk, buf, 0, 512);
    isModified = false;

  }

  /**
   * convert the internal data to the byte buffer for writing
   * <PRE>
   * SeedName (12 char)
   * nextIndext (int)
   * updateTime (int)
   * startingBlock (int),
   * bitmap (long),
   * earliestTime (short),
   * latestTime (short)
   * Repeat last MAX_EXTENTS times.
   * </PRE>
   */
  private void toBuf() {
    if (bb == null) {
      throw new RuntimeException("toBuf bb=null " + toString());
    }
    bb.clear();
    //bb.put(seedName.getBytes())
    for (int i = 0; i < 12; i++) {
      bb.put((byte) seedName.charAt(i));
    }
    bb.putInt(nextIndex).putInt((int) (updateTime / 1000L));
    for (int i = 0; i < MAX_EXTENTS; i++) {
      bb.putInt(startingBlock[i]).putLong(bitMap[i]).putShort(earliestTime[i]).putShort(latestTime[i]);
    }
  }
  private final byte[] namebuf = new byte[12];

  /**
   * recover the data from a binary buffer (like after its read in!)
   */
  private void fromBuf() {
    bb.clear();
    bb.get(namebuf);
    //seedName=new String(namebuf);
    Util.clear(seedName);
    for (int i = 0; i < 12; i++) {
      seedName.append((char) namebuf[i]);
    }
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
   * this is not used in the realtime system. It is present so that an program can provide a
   * bytebuffer with an index block in it loading
   *
   * @param bf A byte array with a 512 byte index block in it
   * @param blk block number in the index file
   */
  public final void reload(byte[] bf, int blk) {
    System.arraycopy(bf, 0, buf, 0, 512);
    iblk = blk;
    bb.clear();
    bb.get(namebuf);
    //seedName=new String(namebuf);
    Util.clear(seedName);
    for (int i = 0; i < 12; i++) {
      seedName.append((char) namebuf[i]);
    }
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
    updateBlock("setNxt");
  }

  /**
   * set the next index pointer for this block, normally called when a new index is needed for a
   * channel. This effectively closes out the use of this index file for writing
   *
   * @param idx The next Index block value.
   * @throws IOException If this index block cannot be written out for some reason
   */
  public synchronized void setNextIndexFix(int idx) throws IOException {
    nextIndex = idx;
    try {
      updateBlockFix();
    } catch (IOException e) {
      e.printStackTrace();
      throw e;
    }
  }

  /**
   * add an extent to this index, if the extents are full this will allocate a new index block,
   * update forward index pointer to this block, make a final write of this block, and then set up
   * this object to use the newly allocated block with cleared parameters
   *
   * @throws IOException if the index block write throws one
   * @throws EdgeFileCannotAllocateException Really serious, a new index block cannot be obtained.
   * @throws EdgeFileReadOnlyException This file is read only so you cannot allocate new extents
   * @throws IllegalSeednameException Should never be thrown, but if seedname is corrupt it will be.
   */
  public final synchronized void addExtent()
          throws IOException, EdgeFileCannotAllocateException, EdgeFileReadOnlyException, IllegalSeednameException {
    for (int i = 0; i < MAX_EXTENTS; i++) // is the starting block free? if so, set it to the 
    {
      if (startingBlock[i] == -1) {
        //Util.prta(start+" addExtent() at "+i+" "+seedName+"/"+iblk+" rw="+rw.getChannel());
        startingBlock[i] = indexFile.getNewExtent();
        bitMap[i] = 0;
        currentExtentIndex = i;
        nextBlock = 0;
        updateBlock("AEold");
        return;
      }
    }

    // The index is full (no more extents can be added, get a new index block and
    // Finish up this one
    try {
      int blk = indexFile.newIndexBlock(seedName);
      nextIndex = blk;            // Set forward link from this block
      //Util.prta(start+" addExtent() NEW "+seedName+"/"+iblk+" rw="+rw.getChannel()+" nextindex="+nextIndex);
      updateBlock("AEfin");            // final write of this block, its full!

      // O.K. now we become a new index
      nextIndex = -1;
      iblk = blk;                 // set new place to record this block
      for (int i = 0; i < MAX_EXTENTS; i++) {
        startingBlock[i] = -1;
        bitMap[i] = 0;
        earliestTime[i] = 32700;
        latestTime[i] = -32700;
      }
      startingBlock[0] = indexFile.getNewExtent();   // Put the new extent in this new one
      currentExtentIndex = 0;
      nextBlock = 0;
      updateBlock("AEinit");            // first write of this new index block!
    } catch (IllegalSeednameException e) {
      Util.prt("Illegal Seedname is impossible here addExtent()! " + e.getMessage());
      Util.prta(toStringBuilder(null));
      e.printStackTrace();
      throw e;
    }
  }

  /**
   * add an extent to this index, this routine is for rebuilding index, so the iblk is used to
   * determine the extent, update forward index pointer to this block, make a final write of this
   * block, and then set up this object to use the newly allocated block with cleared parameters
   *
   * @param blck The data block number for which the extent needs to be added
   * @throws IOException if the index block write throws one
   * @throws EdgeFileCannotAllocateException Really serious, a new index block cannot be obtained.
   * @throws EdgeFileReadOnlyException This file is read only so you cannot allocate new extents
   *
   */
  public synchronized void addExtentIndexOnly(int blck)
          throws IOException, EdgeFileCannotAllocateException, EdgeFileReadOnlyException {
    int start = blck & ~0x3f;
    indexFile.setNextExtentOnly(blck);
    for (int i = 0; i < MAX_EXTENTS; i++) // is the starting block free? if so, set it to the 
    {
      if (startingBlock[i] == -1) {
        startingBlock[i] = start;
        bitMap[i] = 0;
        currentExtentIndex = i;
        nextBlock = 0;
        updateBlock2();
        return;
      }
    }

    // The index is full (no more extents can be added, get a new index block and
    // Finish up this one
    try {
      int blk = indexFile.newIndexBlock(seedName);
      nextIndex = blk;            // Set forward link from this block
      updateBlock2();            // final write of this block, its full!

      // O.K. now we become a new index
      nextIndex = -1;
      iblk = blk;                 // set new place to record this block
      for (int i = 0; i < MAX_EXTENTS; i++) {
        startingBlock[i] = -1;
        bitMap[i] = 0;
        earliestTime[i] = 32700;
        latestTime[i] = -32700;
      }
      startingBlock[0] = start;   // Put the new extent in this new one
      currentExtentIndex = 0;
      nextBlock = 0;
      updateBlock2();           // First write of this new index block
    } catch (IllegalSeednameException e) {
      Util.prta("Illegal Seedname is not possible here! addExtentIndexOnly() " + e.getMessage());
      Util.prta(toStringBuilder(null));
      e.printStackTrace();
    }
  }

  public int getIndexBlockNumber() {
    return iblk;
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
        return i;
      }
    }
    return MAX_EXTENTS;
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
   * mark a block of the current extent as in use, should be done as data are written into the
   * extent. If writer always getNextBlock() and then write, then call this routine, the bit maps
   * will be correct and nextBlock will work!
   *
   * @param block The block number used (0-64)
   * @param nblks The number of blocks used
   */
  public synchronized void markBlocksUsed(int block, int nblks) {
    long mask = 1;
    if (block > 0) {
      mask = mask << block;
    }
    isModified = true;
    if (nblks == 1) {
      bitMap[currentExtentIndex] |= mask;
    } else {
      for (int i = 0; i < nblks; i++) {
        bitMap[currentExtentIndex] |= mask;
        mask = mask << 1;
      }
    }
    nextBlock = block + nblks;
    if (nextBlock >= 64) {
      nextBlock = -1;
    }
  }
  static boolean dbgBlocks = false;
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
   * write a mini-seed block to disk for this IndexBlock (creates log for this index Blocks writes)
   *
   * @param ms The miniseed block to write
   * @return The block number written to in data file
   * @throws IOException if writing causes one
   * @throws EdgeFileCannotAllocateException if a new blocks needed be cannot be obtained
   * @throws EdgeFileReadOnlyException If this file is read only, it cannot be written!
   * @throws IllegalSeednameException Should not be possible, but if the seedname is bad, it will be
   */
  public synchronized int writeMS(MiniSeed ms)
          throws IOException, EdgeFileCannotAllocateException, EdgeFileReadOnlyException, IllegalSeednameException {

    // The time for earliestTime and latestTime is 3s of seconds since midnight of julian day
    //int [] ymd = SeedUtil.fromJulian(julian);
    short endtime;
    short time = (short) ((ms.getHour() * 3600 + ms.getMinute() * 60 + ms.getSeconds()) / 3);
    if (ms.getNsamp() <= 0 || ms.getRate() <= 0) {
      endtime = time;   // usually murdoch-hutt or other non-data blocks.
    } else {
      endtime = (short) ((ms.getHour() * 3600 + ms.getMinute() * 60 + ms.getSeconds() + ms.getNsamp() / ms.getRate()) / 3.);
    }

    int nblks = (ms.getBlockSize() + 511) / 512;

    // See if we have an extent under way
    if (nextBlock == -1 || currentExtentIndex == -1 || nextBlock >= 64) {
      addExtent();
    }

    // Is logging turned on, if so make sure file is open
    if (dbgBlocks) {
      if (blkout == null || System.currentTimeMillis() / 86400000L != lastBlockDay) {
        lastBlockDay = System.currentTimeMillis() / 86400000L;
        int doy = SeedUtil.doy_from_ymd(SeedUtil.fromJulian(SeedUtil.toJulian(System.currentTimeMillis())));
        // open block output file, append if this is a first opening
        blkout = new LoggingStream(Util.getProperty("logfilepath")
                + "block.log" + Character.toString((char) ('0' + (doy % 10))), (blkout == null));
        LoggingStream.setNoConsole(true);

      }
    }
    // Will this write go past the end of the extent?
    int retval;
    if ((nextBlock + nblks) <= 64) {
      if (time < earliestTime[currentExtentIndex]) {
        earliestTime[currentExtentIndex] = time;
      }
      if (endtime > latestTime[currentExtentIndex]) {
        latestTime[currentExtentIndex] = endtime;
      }
      if (dbgBlocks) {
        blkout.println("writeMS: bk=" + startingBlock[currentExtentIndex] + "-" + nextBlock + " nb=" + nblks + " " + ms);
      }
      // send data off to any clients wanting copies
      //synchronized(EdgeBlockQueue.class) {      // Insure blocks are grouped together!
      for (int i = 0; i < nblks; i++) {
        EdgeBlockQueue.queue(julian, IndexFile.getNode(),
                ms.getSeedNameSB(), startingBlock[currentExtentIndex] + nextBlock + i,
                iblk, currentExtentIndex, ms.getBuf(), i * 512);
      }
      //}
      datout.writeBlock(startingBlock[currentExtentIndex] + nextBlock, ms.getBuf(), 0, nblks * 512);
      /*if(nblks > 1) {
        byte [] bb2 = ms.getBuf();
        Util.prt(" big blk wr="+ms.toString()+" 513="+bb2[513]+" "+b2b[514]+" "+bb2[bb2.length-1]);
      }*/
      retval = startingBlock[currentExtentIndex] + nextBlock;
      markBlocksUsed(nextBlock, nblks);     // This updates nextBlock as well, if last 
      // nextBlock will come back -1
      //if(nextBlock == -1) updateBlock();    // If this is the last nextBLock, write it out!
    } // This write needs to be broken into two pieces with a new extent gathered
    else {
      int nblks2 = 64 - nextBlock;        // Number of blocks that can be written now
      if (time < earliestTime[currentExtentIndex]) {
        earliestTime[currentExtentIndex] = time;
      }
      if (endtime > latestTime[currentExtentIndex]) {
        latestTime[currentExtentIndex] = endtime;
      }
      if (dbgBlocks) {
        blkout.println("writeMS: bk=" + startingBlock[currentExtentIndex] + "-" + nextBlock + " nb2=" + nblks2 + " " + ms);
      }
      retval = startingBlock[currentExtentIndex] + nextBlock;

      // send data off to any clients wanting copies
      //synchronized(EdgeBlockQueue.class) {      // Insure blocks are grouped together!
      for (int i = 0; i < nblks2; i++) {
        EdgeBlockQueue.queue(julian, IndexFile.getNode(),
                ms.getSeedNameSB(), startingBlock[currentExtentIndex] + nextBlock + i,
                iblk, currentExtentIndex, ms.getBuf(), i * 512);
      }
      //}
      datout.writeBlock(startingBlock[currentExtentIndex] + nextBlock, ms.getBuf(), 0, nblks2 * 512);
      markBlocksUsed(nextBlock, nblks2);
      addExtent();                  // this will add more space

      // Note: we do not update earliest/latestTime here since beginning is in prior extent
      nblks = nblks - nblks2;         // how many remain
      if (dbgBlocks) {
        blkout.println("writeMS: " + ms.getSeedNameSB()
                + " bk=" + startingBlock[currentExtentIndex] + nextBlock + " nb=" + nblks2);
      }
      // send data off to any clients wanting copies
      //synchronized(EdgeBlockQueue.class) {      // Insure blocks are grouped together!
      for (int i = 0; i < nblks; i++) {
        EdgeBlockQueue.queue(julian, IndexFile.getNode(),
                ms.getSeedNameSB(), startingBlock[currentExtentIndex] + nextBlock + i,
                iblk, currentExtentIndex, ms.getBuf(), (i + nblks2) * 512);
      }
      //}
      datout.writeBlock(startingBlock[currentExtentIndex] + nextBlock, ms.getBuf(), nblks2 * 512, nblks * 512);
      markBlocksUsed(nextBlock, nblks);
    }
    return retval;
  }

  /**
   * write a mini-seed block to disk for this IndexBlock (creates log for this index Blocks
   * writes).This is used to rebuild an index usually by reading a series of miniseed blocks from
   * the data file.
   *
   * @param ms The miniseed block to write
   * @param blk The data block number to write this block to (gives mask and startingBlock)
   * @throws IOException if writing causes one
   * @throws EdgeFileCannotAllocateException if a new blocks needed be cannot be obtained
   * @throws EdgeFileReadOnlyException If this file is read only, it cannot be written!
   */
  public synchronized void writeMSIndexOnly(MiniSeed ms, int blk)
          throws IOException, EdgeFileCannotAllocateException, EdgeFileReadOnlyException {

    // The time for earliestTime and latestTime is 3s of seconds since midnight of julian day
    //int [] ymd = SeedUtil.fromJulian(julian);
    short time = (short) ((ms.getHour() * 3600 + ms.getMinute() * 60 + ms.getSeconds()) / 3);
    short endtime;
    if (ms.getNsamp() <= 0 || ms.getRate() <= 0) {
      endtime = time;   // usually murdoch-hutt or other non-data blocks.
    } else {
      endtime = (short) ((ms.getHour() * 3600 + ms.getMinute() * 60 + ms.getSeconds() + ms.getNsamp() / ms.getRate()) / 3.);
    }

    int nblks = (ms.getBuf().length + 511) / 512;

    // See if we have an extent under way
    if (nextBlock == -1 || currentExtentIndex == -1) {
      addExtentIndexOnly(blk);
    }
    // This must be an extent or we need to add another one
    if ((blk & ~0x3f) != startingBlock[currentExtentIndex]) {
      addExtentIndexOnly(blk);
    }
    nextBlock = blk & 0x3f;

    // Will this write go past the end of the extent?
    if (time < earliestTime[currentExtentIndex]) {
      earliestTime[currentExtentIndex] = time;
    }
    if (endtime > latestTime[currentExtentIndex]) {
      latestTime[currentExtentIndex] = endtime;
    }
    //if(dbgBlocks) blkout.println("writeMSIndexOnly: "+ms.getSeedNameSB()+
    //    " bk="+startingBlock[currentExtentIndex]+nextBlock+" nb="+nblks);
    // send data off to any clients wanting copies
    markBlocksUsed(nextBlock, nblks);     // This updates nextBlock as well, if last 
    // nextBlock will come back -1
    if (nextBlock == -1) {
      updateBlock2();
    }
    // This write needs to be broken into two pieces with a new extent gathered
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
    rw.readBlock(buf, iblk, 512);
    //if(bb == null) bb = ByteBuffer.wrap(buf);
    fromBuf();
    return true;
  }

  /**
   * return the index key of this block
   *
   * @return The index key
   */
  //public IndexKey getKey() {return key;}
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
  public StringBuilder getSeedName() {
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
  //public IndexKey getIndexKey() {return key;}
  /**
   *
   * @return Get the long hash key of channel name and part of the date
   */
  public long getHash() {
    return hash;
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
    IndexBlock idx = (IndexBlock) o;
    int idiff = Util.compareTo(seedName, idx.getSeedName());
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
   * main test unit
   *
   * @param args command line args
   */
  static public void main(String[] args) {
    int blocklen = 512;
    long last;
    long big = 1;
    for (int i = 0; i < 12; i++) {
      big *= 37;
    }
    Util.prt("big=" + Util.toHex(big));
    big = big / 37 * 51;
    Util.prt(" Big2=" + Util.toHex(big));
    StringBuilder sb = new StringBuilder(12);
    sb.append("UUDWU  EHZ01");
    long sbhash = sb.hashCode();
    long sthash = sb.toString().hashCode();
    int julian = 2457043;
    try {
      long hash = IndexBlock.getHashOld(sb, julian);
      long hashnew = IndexBlock.getHash(sb, julian);
      long sthash5 = Util.getHashSB(sb);
      Util.clear(sb).append("UUCWU  EHZ01");
      long sthash2 = sb.toString().hashCode();
      long sbhash2 = sb.hashCode();
      long hash2 = IndexBlock.getHashOld(sb, julian + 1);
      long hash2new = IndexBlock.getHash(sb, julian);
      long sthash4 = Util.getHashSB(sb);
      Util.prt("hast=" + hash + " hast2=" + hash2 + " sbhash=" + sbhash + " sb2hash=" + sbhash2
              + " sthash=" + sthash + " sthash2=" + sthash2 + " stutil=" + sthash5 + " 2=" + sthash4 + " stutil=" + (int) (sthash5 & 0xFFFFFFFFL) + " 2=" + (int) (sthash4 & 0xFFFFFFFFL));

      Util.prt(Util.toHex(sthash) + " new hash=" + Util.toHex(hashnew) + " jul=" + Util.toHex(julian));
      Util.prt(Util.toHex(sthash2) + " new hash=" + Util.toHex(hash2new) + " jul=" + Util.toHex(julian));

    } catch (EdgeFileCannotAllocateException e) {
      e.printStackTrace();
    }

    byte[] buf;
    int start = 0;
    EdgeProperties.init();
    boolean errorsOnly = false;
    Util.setModeGMT();
    if (args.length == 0) {
      Util.prt("MiniSeed [-b nnn] [-dbg] [-err] filenames");
      Util.prt(" -b nnnnn Set block size");
      Util.prt(" -dbg     Set voluminous output");
      Util.prt(" -err     Set errors only");

    }
    for (int i = 0; i < args.length; i++) {
      //Util.prt(" arg="+args[i]+" i="+i);
      if (args[i].equals("-b")) {
        blocklen = Integer.parseInt(args[i + 1]);
        start = i + 2;
      }
      if (args[i].equals("-dbg")) {
        setDebug(true);
        start = i + 1;
      }
      if (args[i].equals("-err")) {
        errorsOnly = true;
        start = i + 1;
      }
    }
    int iblk = 0;
    //Util.prt("block len="+blocklen+" start="+start+" end="+(args.length-1));

    RawDisk rw = null;
    long lastlength = 0;
    String lastString = "";
    long diff;
    long time;
    MiniSeed ms;
    for (int i = start; i < args.length; i++) {
      Util.prt("Open file : " + args[i]);
      last = 0;
      try {
        rw = new RawDisk(args[i], "r");
        iblk = 0;
        for (;;) {
          buf = rw.readBlock(iblk, 512);
          //Util.prt("read bloc len="+buf.length+" iblk="+iblk);

          // Bust the first 512 bytes to get all the header and data blockettes - data
          // may not be complete!  If the block length is longer, read it in complete and
          // bust it appart again.
          ms = new MiniSeed(buf);
          if (ms.getBlockSize() > 512) {
            rw.readBlock(iblk, ms.getBlockSize());
            ms = new MiniSeed(buf);
          }

          // List a line about the record unless we are in errors only mode
          if (!errorsOnly) {
            Util.prt(ms.toString() + " iblk=" + iblk + "/" + ms.getBlockSize());
          }
          time = ms.getTimeInMillis();

          // if this is a trigger or other record it will have no samples (but a time!) so
          // do not use it for discontinuity decisions
          if (ms.getNsamp() > 0) {
            if (last != 0) {
              diff = time - last - lastlength;
              if (diff > 1) {
                if (errorsOnly) {
                  Util.prt("+" + lastString);
                  Util.prt("|" + ms.toString() + " iblk=" + iblk + "/" + ms.getBlockSize());
                }
                Util.prt("   ***** discontinuity = " + diff + " ms. ends at " + ms.getTimeString()
                        + " blk=" + iblk);
              }
            }
            IndexBlock.writeMiniSeed(ms, "test");
            lastlength = (long) (ms.getNsamp() * 1000 / ms.getRate());
            last = time;
            lastString = ms.toString() + " iblk=" + iblk + "/" + ms.getBlockSize();
          }

          // Print out the console long if any is received.
          if (ms.getSeedNameSB().substring(7, 10).equals("LOG") && ms.getNsamp() > 0 && !errorsOnly) {
            Util.prt("data=" + new String(ms.getData(ms.getNsamp())));
          }

          // if reported Blck 1000 length is zero, use the last known block len
          if (ms.getBlockSize() <= 0) {
            iblk += blocklen / 512;
          } else {
            iblk += ms.getBlockSize() / 512;
            blocklen = ms.getBlockSize();
          }
        }       // end of read new header for(;;)
      } catch (IllegalSeednameException e) {
        Util.prt("IllegalSeednameException " + e.getMessage());
      } /*catch(EdgeFileCannotAllocateException e) {
        Util.prt("EdgeFileCannotAllocateException "+e.getMessage());
      }
      catch(EdgeFileReadOnlyException e) {
        Util.prt("EdgeFileReadOnlyException "+e.getMessage());
      }*/ catch (IOException e) {
        if (e.getMessage() != null) {
          Util.prt("IOException " + e.getMessage());
        } else {
          Util.prt("EOF Nblocks=" + iblk);
        }
        try {
          if (rw != null) {
            rw.close();
          }
        } catch (IOException e2) {
        }

      }
    }
  }
}
