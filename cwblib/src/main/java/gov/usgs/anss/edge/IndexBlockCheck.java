/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.edge;

import gov.usgs.anss.seed.MiniSeed;
import java.nio.ByteBuffer;
import java.io.IOException;
import java.io.EOFException;
import java.util.BitSet;
import gov.usgs.anss.util.Util;
import gov.usgs.anss.util.SeedUtil;

/**
 * This class handles index check blocks for Replicators. The physical form is :
 * <pre>
 * SeedName (12 char)
 *  node (4 bytes)
 * julian (int)
 * startingBlock (int), bitmap (long), earliestTime (short), latestTime (short)
 * Repeat last MAX_EXTENTS times.
 * </pre> This class is used to create confirmed index blocks which are compared to normal index
 * blocks to look for differences between the data confirmed and that which should be present. The
 * IndexFileReplicator maintains a list of these object for each block active in a .chk file. Each
 * block written to the data file causes a markBlockUsed() invocation here to mark block as
 * confirmed. This basically represents a particular check block in a .chk file.
 *
 * @author davidketchum
 */
public final class IndexBlockCheck implements Comparable {

  /**
   * this is the number of extents in one index block (block#, bit maps, earliest/latest times)
   *
   */
  public static final int MAX_EXTENTS = IndexBlock.MAX_EXTENTS;
  public static final StringBuilder REQUESTEDBLK = new StringBuilder(12).append("REQUESTEDBLK");
  public static final StringBuilder REQUESTEDBK = new StringBuilder(12).append("REQUESTEDBK");  // this was a typo in some EdgeBLockSockets.
  private static final byte[] extentdata = new byte[64 * 512];   // FOr reading the last extent
  private RawDisk rw;       // access to the index file
  private RawDisk datout;   //
  private RawDisk chkout;
  private IndexFileReplicator indexFile;  // Access to the controlling index file
  private int iblk;         // block number of this index check block
  private final byte[] buf = new byte[512];      // raw buffer backing this block
  private byte[] extentbuf;// for translating reading in an extent in the analysis
  private ByteBuffer bb;    // Byte buffer for manipulating buf
  //private byte [] chkbuf = new byte[512];   // Used in the constructure, looks like we could use buf instead
  //private ByteBuffer bbb ;

  // Data for the raw block 
  private final StringBuilder seedName = new StringBuilder(12);        // Exactly 12 characters long!
  private int julian;                                     // Julian day of this index block (from the IndexFileReplicator)
  private final byte[] nodebuf = new byte[4];                  // 4 character buffer for node
  //private int nextIndex;                                // not used in check index blocks
  //private int currentExtentIndex;                       // Not used in check index blocks
  private final int startingBlock[] = new int[MAX_EXTENTS];       // starting block for this 64 block extent
  private final long bitMap[] = new long[MAX_EXTENTS];           // bit map of confirmed bits in this extent
  private final short latestTime[] = new short[MAX_EXTENTS];     // not used, space for the latest time
  private final short earliestTime[] = new short[MAX_EXTENTS];   // not used, space for earliest time
  private final StringBuilder node = new StringBuilder(4);            // always 4 characters long right pad w/spaces
  private final StringBuilder chknode = new StringBuilder(4);
  private final StringBuilder chkseedname = new StringBuilder(12);
  private byte[] seedbuf = new byte[12];        // 12 character buffer for seednames
  // Local variables
  private int nupdates;                   // counts additions to this, causing periodic writes
  private long lastUpdate;                // to time blocks that are stale and need to be written
  private long lastIndexCheck;         // The last time this block was checked fully by an IndexCheck (used by IndexCheck only!)
  private long modifyTime;                // The time the last thing was changed in this block (block marked used)
  private boolean fullyConfirmed;       // if true, this block has all extent bits set
  private boolean isModified;

  /**
   * get the last time a IndexCheck was done in strict or last extent mode. This is set by the
   * IndexCheck
   *
   * @return The long time since the last IndexCheck
   */
  public long getLastModified() {
    return modifyTime;
  }

  /**
   * get the last time a IndexCheck was done in strict or last extent mode. This is set by the
   * IndexCheck
   *
   * @return The long time since the last IndexCheck
   */
  public long getLastIndexCheck() {
    return lastIndexCheck;
  }

  /**
   * Set the last index check time, this should only be done by the IndexCheck class
   *
   * @param l The time to set
   */
  public void setLastIndexCheck(long l) {
    lastIndexCheck = l;
  }
  // static data needed to implement the write all mini-seed through the writeMiniSeed static
  // function
  private static boolean dbg;

  /**
   * set debug state
   *
   * @param t if true, turn on debugging
   */
  public static void setDebug(boolean t) {
    dbg = t;
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
  private final StringBuilder tmpsb = new StringBuilder(100);

  public StringBuilder toStringBuilder(StringBuilder tmp) {
    StringBuilder sbt = tmp;
    if (sbt == null) {
      sbt = Util.clear(tmpsb);
    }
    synchronized (sbt) {
      int i;
      int j;
      for (j = MAX_EXTENTS - 1; j >= 0; j--) {
        if (bitMap[j] != 0) {
          break;
        }
      }
      for (i = MAX_EXTENTS - 1; i >= 0; i--) {
        if (startingBlock[i] != -1) {
          break;
        }
      }
      sbt.append("Check Index blk=").append(Util.rightPad(iblk, 6)).append(" ").
              append(seedName).append(" julian=").append(SeedUtil.fileStub(julian)).append("_").append(node).
              append(" #ext=").append(i + 1).append("/").append(j + 1).append(" ").append(j >= 0 ? Util.toHex(bitMap[j]) : "");
    }
    return sbt;
  }

  /**
   * Return whether this block has been modified since its last write
   *
   * @return true if it has been modified since its last write
   */
  public boolean isModified() {
    return isModified;
  }

  /**
   * return a byte representation of the buff. You are getting the internal buffer so do not modify
   * it!
   *
   * @return The 512 bytes of the buffer
   */
  public byte[] getBuf() {
    toBuf();
    return buf;
  }
  private static final StringBuilder sb = new StringBuilder(10000);

  /**
   * return a detailed string about this index block
   *
   * @param errOnly Only list errors
   * @param readData 0=don't, 1=read and list errors only, 2=read display and list errors
   * @param blockMap A bit set return with list of extents in use (an extent is 64 blocks long)
   * @return StringBuilder representing this block in detail including errors/inconsistencies found
   */
  public synchronized StringBuilder toStringDetail(boolean errOnly, int readData, BitSet blockMap) {
    Util.clear(sb);

    StringBuilder last = new StringBuilder(50).append("  ").append(toStringBuilder(null)).append("\n");
    StringBuilder line = new StringBuilder(50);
    if (!errOnly) {
      sb.append(last);
    }

    if (iblk < 2 || iblk > 50000) {
      if (errOnly) {
        sb.append("   ").append(toStringBuilder(null)).append("\n");
        sb.append(last);
      }
      sb.append("\n      **** index block number is getting large\n");
    }
    if (julian < SeedUtil.toJulian(2005, 1) || julian > SeedUtil.toJulian(2040, 365)) {
      if (errOnly) {
        sb.append("   ").append(toStringBuilder(null)).append("\n");
        sb.append(last);
      }
      sb.append("      **** warning: Julian date out of range 2005-001 to 2040-365!");
    }

    for (int i = 0; i < MAX_EXTENTS; i++) {
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
      Util.clear(line);
      if (errOnly) {
        line.append("    ").append(Util.rightPad(i, 4)).append(" start=").append(Util.rightPad(startingBlock[i], 8)).
                append(" ").append(Util.rightPad(Long.toHexString(bitMap[i]), 8)).append("\n");
      } else {
        sb.append("    ").append((i + "   ").substring(0, 4)).append(" start=").
                append((startingBlock[i] + "        ").substring(0, 8)).append(" ").
                append((Long.toHexString(bitMap[i]) + "                     ").substring(0, 16)).append("\n");
      }

      // If this is not a full bitMap, check for it being the "last" 
      // and that they are contiguously assigned
      int nb = 64;
      if (bitMap[i] != -1) {       // Is bit map not full
        boolean chk = true;
        if (chk && startingBlock[i + 1] == -1) {
          chk = false;        // its is in the last  used extent
        }
        if (chk) {
          if (errOnly) {
            sb.append("   ").append(toStringBuilder(null)).append("\n");
            sb.append(line);
          }
          sb.append("       ** ").append(seedName).append(" bitMap not full and extent is not last one\n");
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
          sb.append("      ** ").append(seedName).append(" bitMap has non-contiguous used blocks! nb1=").append(nb).append(" ").append(Util.toHex(bitMap[i])).append("\n");
        }

      }

      // Starting block must be divisible by 64 evenly
      if ((startingBlock[i] % 64) != 0) {
        if (errOnly) {
          sb.append("   ").append(toStringBuilder(null)).append("\n");
          sb.append(line);
        }
        sb.append("      ***** starting block not divisible by 64!");
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
              sb.append("   ").append(toStringBuilder(null)).append("\n");
              sb.append("      ***** all zeros at extent=").append(startingBlock[i]).
                      append(" blk=").append(off / 512).append(" ").append(seedName).
                      append("/").append(julian).append("\n");
              off += 512;
            } else {
              try {
                ms = new MiniSeed(extentbuf);
                if (readData >= 2) {
                  sb.append(ms.toStringBuilder(null)).append("\n");
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
                  sb.append("      **** Julian date does not agree data=").append(ms.getJulian()).
                          append(" vs ").append(julian).append("\n");
                }

                off += ms.getBlockSize();
              } catch (IllegalSeednameException e) {
                Util.prt("toStringDetail() IllegalSeednameException =" + e.getMessage()
                        + "extent=" + startingBlock[i] + " blk=" + off / 512);
                for (int j = 0; j < 32; j++) {
                  Util.prt(j + " Dump " + extentbuf[j] + " " + (char) extentbuf[j]);
                }
                sb.append("      **** extent=").append(startingBlock[i]).append(" blk=").append(off / 512).
                        append(" IllegalSeednameException ").append(e.getMessage());
                off += 512;
              }
            }
          }         // end while(off <65536) 
        } catch (IOException e) {
          Util.prt("IOException trying to read data from extent "
                  + toStringBuilder(null) + " blk=" + startingBlock[i]);
        }
      }
    }
    return sb;

  }

  /*public IndexBlockCheck(String name, String nd, int blk, IndexFileReplicator ifile)
  throws IllegalSeednameException, IOException {
    this(name, nd,blk,ifile);
  }*/
  /**
   * creates a new instance of IndexBlockCheck for the raw disk block given. The check file block is
   * also read and if the seedname, node, and julian date agree it is used to populate the rest of
   * the block, else all arrays start out empty (no blocks confirmed).
   *
   * @param name The seed name to be associated with the file.
   * @param ifile The previously opened IndexFile to use to read the blocks
   * @param nd The node of the edge computer originating this file
   * @param blk The block number in the check file for this index block check
   * @throws IllegalSeednameException if name does not pass MasterBlock.checkSeedName()
   * @throws IOException If this block cannot be created.
   */
  public IndexBlockCheck(StringBuilder name, StringBuilder nd, int blk, IndexFileReplicator ifile)
          throws IllegalSeednameException, IOException {
    for (int i = 0; i < MAX_EXTENTS; i++) {
      startingBlock[i] = -1;
      bitMap[i] = 0;
      earliestTime[i] = (short) -1;
      latestTime[i] = (short) -1;
    }
    //currentExtentIndex=0;
    lastUpdate = System.currentTimeMillis();
    fullyConfirmed = false;
    bb = ByteBuffer.wrap(buf);
    indexFile = ifile;
    julian = ifile.getJulian();
    MasterBlock.checkSeedName(name);
    Util.clear(seedName).append(name);
    Util.rightPad(seedName, 12);
    //seedName=(name+"            ").substring(0,12);
    Util.clear(node);
    for (int i = 0; i < 4; i++) {
      if (i < nd.length()) {
        node.append((char) (nd.charAt(i) == 0 ? ' ' : nd.charAt(i)));
      } else {
        node.append(' ');
      }
    }
    //node = (nd+"    ").substring(0,4);
    rw = indexFile.getRawDisk();
    datout = indexFile.getDataRawDisk();
    chkout = indexFile.getCheckRawDisk();
    // Allocate space for arrays in this object

    // Now check the check file block to see if this block is already there.  If so,
    // read in its contents to populate the arrays
    iblk = blk;
    try {
      boolean readOK = false;
      int len = chkout.readBlock(buf, blk, 512);
      for (int i = 0; i < 12; i++) {
        if (buf[i] != 0 && (buf[i] < 32 || buf[i] > 127)) {
          buf[i] = '%';//printable override
        }      //String chkseedname = new String(buf, 0, 12); 
      }
      Util.clear(chkseedname);
      for (int i = 0; i < 12; i++) {
        chkseedname.append((char) (buf[i] == 0 ? ' ' : buf[i]));
      }
      //Util.prta("chkbuf node ="+buf[12]+" "+buf[13]+" "+buf[14]+" "+buf[15]);
      for (int i = 12; i < 16; i++) {
        if (buf[i] != 0 && (buf[i] < 32 || buf[i] > 127)) {
          buf[i] = '%';// printable override
        }
      }
      Util.clear(chknode);
      for (int i = 0; i < 4; i++) {
        chknode.append((char) (buf[i + 12] == 0 ? ' ' : buf[i + 12]));
      }
      bb.clear();
      bb.position(16);
      int jul = bb.getInt();
      //Util.prta("New chk iblk="+iblk+" julian="+jul+" "+julian+" node "+chknode+" "+node+
      //    " seed="+chkseedname+" "+seedName);
      // In mar 2008 found some check blocks had 'requestedblk' in them, we need to overide this
      if (Util.stringBuilderEqual(seedName, REQUESTEDBLK) || Util.stringBuilderEqual(seedName, REQUESTEDBK)) {
        Util.prta(Util.clear(tmpsb).append("IBC: *** try to create a seedname=REQUESTEDBLK! iblk=").
                append(blk).append(" file=").append(ifile.getFilename()));
      }
      if (!Util.stringBuilderEqual(seedName, chkseedname) && !Util.stringBuilderEqual(chkseedname, REQUESTEDBLK)
              && !Util.stringBuilderEqual(seedName, REQUESTEDBK) && chkseedname.indexOf("%") < 0) {
        Util.prta(Util.clear(tmpsb).append("IBC: ***  Read of check block has wrong name! ").append(seedName).
                append(" chk=").append(chkseedname).append(" blk=").append(blk).append(" file=").append(ifile.getFilename()));
      }
      if (jul == julian && Util.stringBuilderEqual(chknode, node) && (Util.stringBuilderEqual(seedName, chkseedname)
              || Util.stringBuilderEqual(chkseedname, REQUESTEDBLK)
              || Util.stringBuilderEqual(chkseedname, "%%%%%%%%%%%%"))) {
        fromBuf();

        if (!isFullyConfirmed()) {   // No need to rebuild last extent in fully confirmed blocks
          // Find the starting block of the last extent
          int j;
          for (j = MAX_EXTENTS - 1; j >= 0; j--) {
            if (startingBlock[j] != -1) {
              break;
            }
          }

          // "The latest extent is pointed to by j
          // but since it might be lazily written we need to read in the extent
          // and find the zero filled one to set the last block
          //Util.prta("New chk passed ext="+j);
          if (j >= 0 && startingBlock[j] >= 0) {
            synchronized (extentdata) {
              int l = datout.readBlock(extentdata, startingBlock[j], 64 * 512); // read the whole extent
              for (int nextBlock = 0; nextBlock < 64; nextBlock++) {
                if (extentdata[nextBlock * 512] != 0) // zero block, do not mark as used.
                {
                  bitMap[j] = bitMap[j] | 1L << nextBlock;      // Or in this bit, block is in use
                }
              }
            }
          }
          //Util.prta("New chk Reconstructed bitmap="+bitMap[j]+" extind="+j+" starting="+startingBlock[j]);;
          readOK = true;
        } else {
          readOK = true;
        }
        // In Mar 2008, sometimes these names got into the check blocks, override them back to a valid seedname
        // This can be removed later when all of these are gone!
        if (Util.stringBuilderEqual(chkseedname, REQUESTEDBLK) || Util.stringBuilderEqual(chkseedname, "%%%%%%%%%%%%")) {
          Util.prta(Util.clear(tmpsb).append("IBC: *** fixing a REQUESTEDBLK or %%%%%%%%%%%% back to seedname=").
                  append(name).append(" blk=").append(blk).append(" ").append(ifile.getFilename()));
          Util.clear(seedName).append(name);
          Util.rightPad(seedName, 12);
          //seedName=(name+"       ").substring(0,12);
          updateBlock("Fix broken"); // make sure it gets written back
        }
      } else {
        Util.prta(Util.clear(tmpsb).append("IBC: ***** no match create empty blk=").append(blk).
                append(" Assume new jul=").append(SeedUtil.fileStub(julian)).append("/").
                append(SeedUtil.fileStub(julian)).append(" node=").append(chknode).append("/").
                append(node).append("| seed=").append(chkseedname).append("/").append(seedName).append("|"));
      }
    } catch (EOFException e) {
      Util.prta(Util.clear(tmpsb).append("IBC: EOF chk blk=").append(blk).
              append("  Assume new=").append(blk).append(" ").append(seedName).append(" ").
              append(SeedUtil.fileStub(julian)).append("_").append(node));
    } catch (IOException e) {
      Util.prta(Util.clear(tmpsb).append("IBC: IOE chk blk=").append(blk).
              append(" Assume new=").append(blk).append(" ").append(seedName).append(" ").
              append(SeedUtil.fileStub(julian)).append("_").append(node).append(" e=").append(e));
      e.printStackTrace();
    }
    //Util.prta("New chk blk="+blk+" "+seedName+" "+julian+"_"+node.trim()+
    //    " cfirm="+isFullyConfirmed()+" readok="+readOK);
    updateBlock("Init");            // Set initial configuration
  }

  /**
   * close up this block - write it out and reset all its file data so it cannot be use
   *
   * @throws java.io.IOException
   */
  public synchronized void close() throws IOException {
    try {
      if (dbg) {
        Util.prta(Util.clear(tmpsb).append("IndexBlockCheck close() ").
                append(seedName).append("/").append(julian));
      }
      updateBlock("close");
      clear();
      if (dbg) {
        Util.prta(Util.clear(tmpsb).append("IndexBlockCheck close() ").append(seedName).
                append("/").append(julian).append(" done."));
      }
    } catch (IOException e) {
      Util.prt(Util.clear(tmpsb).append("IOException when closing idx=").append(seedName).
              append(" jul=").append(julian).append(" ").append(e.getMessage()));
      throw e;
    }
  }

  public synchronized void clear() {
    if (dbg) {
      Util.prta(Util.clear(tmpsb).append("IndexBlockCheck clear() ").
              append(seedName).append("/").append(julian));
    }
    rw = null;
    datout = null;
    chkout = null;
    indexFile = null;
    //bb = null;
    if (dbg) {
      Util.prta(Util.clear(tmpsb).append("IndexBlockCheck close() ").
              append(seedName).append("/").append(julian).append(" done."));
    }
  }

  /**
   * check fullyConfirmed is set and reevaluate if it is not. A block is fully confirmed when all of
   * its bitmaps are all set (-1 in all MAX_EXTENTS) of them. Once a block is fully confirmed it is
   * generally useless.
   *
   * @return true if this block has all confirmed bits in all extents.
   */
  public final boolean isFullyConfirmed() {
    if (fullyConfirmed) {
      return true;
    }
    boolean done = true;          // use intermediate variable so other thread do not see
    // false positive when this is running.
    for (int i = MAX_EXTENTS - 1; i >= 0; i--) {
      if (bitMap[i] != -1) {
        done = false;
        break;
      }
    }
    fullyConfirmed = done;
    return fullyConfirmed;
  }

  /**
   * update this block on disk
   *
   * @param use A tag to identify who called update block to help with debug logging
   * @return true if this block is complete (all extent bit maps are -1) the fullyConfirmed state.
   * @throws IOException If one occurs writing the block
   */
  public final synchronized boolean updateBlock(String use) throws IOException {
    toBuf();
    //Util.prta("IBC: (DEBUG) write index="+iblk+" "+seedName+"_"+node);
    if (dbg) {
      Util.prta(Util.clear(tmpsb).append("IBC: ").append(Util.rightPad(use, 6)).
              append(" wr idx=").append(Util.leftPad(iblk, 5)).append(" ").append(seedName).
              append(" ").append(julian).append("_").append(node).append(" ").append(new String(buf, 0, 12)));
      //for(int i=0; i<MAX_EXTENTS; i++) if(bitmoreMap[i] != 0 && bitMap[i] != -1) 
      //  Util.prt("IBC: "+i+" "+Long.toHexString(bitMap[i]));
    }
    if (iblk > indexFile.getMaxIndex()) {
      if (dbg) {
        Util.clear(tmpsb).append("CHK: max idx=").append(iblk).append(" was ").append(indexFile.getMaxIndex()).append(" ");
        toStringBuilder(tmpsb);
        Util.prta(tmpsb);
      }
      indexFile.setMaxIndex(iblk);
    }
    //long temp= 
    chkout.writeBlock(iblk, buf, 0, 512);
    lastUpdate = System.currentTimeMillis();
    isModified = false;
    return isFullyConfirmed();
  }

  /**
   * return millis at which the last update occurred
   *
   * @return Millis time of last update
   */
  public long getLastUpdate() {
    return lastUpdate;
  }

  /**
   * convert the internal data to the byte buffer for writing
   * <pre>
   * SeedName (12 char)
   * node (4 bytes)
   * julian (int)
   * startingBlock (int), bitmap (long), earliestTime (short), latestTime (short)
   * Repeat last MAX_EXTENTS times.
   * </pre>
   */
  private synchronized void toBuf() {
    if (bb == null) {
      return;
    }
    bb.clear();
    if (seedName.length() != 12 || node.length() != 4) {
      Util.prta(Util.clear(tmpsb).append("IBC: to buf seed or node wrong len seed=").
              append(seedName.length()).append(seedName).append(" ").append(node.length()).append(node));
    }
    for (int i = 0; i < 12; i++) {
      bb.put((byte) seedName.charAt(i));
    }
    bb.position(12);
    //bb.put(node.getBytes());
    for (int i = 0; i < 4; i++) {
      bb.put((byte) (node.charAt(i) == ' ' ? 0 : node.charAt(i)));
    }
    bb.position(16);
    bb.putInt(julian);
    for (int i = 0; i < MAX_EXTENTS; i++) {
      bb.putInt(startingBlock[i]).putLong(bitMap[i]).putShort(earliestTime[i]).putShort(latestTime[i]);
    }
  }

  /**
   * recover the data from a binary buffer (like after its read in!)
   */
  private synchronized void fromBuf() {
    bb.clear();
    bb.position(0);
    bb.get(seedbuf);
    Util.clear(seedName);
    for (int i = 0; i < 12; i++) {
      seedName.append((char) (seedbuf[i] == 0 ? ' ' : seedbuf[i]));
    }
    //seedName=new String(seedbuf);
    bb.position(12);
    bb.get(nodebuf);
    Util.clear(node);
    for (int i = 0; i < 4; i++) {
      node.append((char) (nodebuf[i] == 0 ? ' ' : nodebuf[i]));
    }
    //node = new String(nodebuf);
    bb.position(16);
    julian = bb.getInt();
    //currentExtentIndex=-1;
    for (int i = 0; i < MAX_EXTENTS; i++) {
      startingBlock[i] = bb.getInt();
      bitMap[i] = bb.getLong();
      earliestTime[i] = bb.getShort();
      latestTime[i] = bb.getShort();
    }
  }

  /**
   * Get the number of extents referred to by this IndexBlockCheck.
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
   * mark a block of the current extent as in use, should be done as data are written into the
   * extent.
   *
   * @param block The block number used (0-64)
   * @param index The extent index in which to mark the block as confirmed.
   * @return true if the block was previously marked as received.
   */
  public synchronized boolean markBlockUsed(int index, int block) {
    long mask = 1;
    // if we have not seen the extent number for this yet, put it in the extent.
    if (startingBlock[index] < 0) {
      startingBlock[index] = block & ~0x3f;     // this has to be the extent!
      try {
        updateBlock("MBU1st");                       // first we have seen this extent
      } catch (IOException e) {
        Util.IOErrorPrint(e, "Trying to update a check block2 file=" + indexFile.getFilename() + "/" + iblk);
      }
    }
    block = block % 64;
    if (block > 0) {
      mask = mask << block;
    }
    boolean ret = (bitMap[index] & mask) != 0;
    bitMap[index] |= mask;
    isModified = true;
    modifyTime = System.currentTimeMillis();
    if (block == 63 || bitMap[index] == -1) {  // Last block in extent or a interior block that completes an extent
      try {
        //if(isFullyConfirmed()) Util.prt("IBC: is fully confirmed"+toString());
        updateBlock("MBU63");
      } catch (IOException e) {
        Util.IOErrorPrint(e, "Trying to update a check block file=" + indexFile.getFilename() + "/" + iblk);
      }
    }
    return ret;
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
   * compares two index blocks for ordering
   *
   * @param o The other object to compare to
   * @return -1 if this is < param, 0 if equal, 1 if this > param
   */
  @Override
  public int compareTo(Object o) {
    if (o == null) {
      Util.prta("*** IBC: compareTo() is null");
      return 1;
    }
    // If this is an integer, we are comparing block numbers. 
    if (o.getClass().getName().contains("java.lang.Integer")) {
      Integer oi = (Integer) o;
      if (iblk > oi) {
        return 1;
      } else if (iblk < oi) {
        return -1;
      }
      return 0;
    }
    if (!o.getClass().getName().contains("IndexBlockCheck")) {
      Util.prta(" *** IndexBlockCheck: compareTo() class not right=" + o.getClass().getName());
      return 1;
    }
    IndexBlockCheck idx = (IndexBlockCheck) o;
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
  }
}
