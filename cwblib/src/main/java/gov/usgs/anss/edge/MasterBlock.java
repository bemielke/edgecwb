/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */

 /*
 * MasterBlock.java
 *
 * Created on May 23, 2005, 10:20 AM
 *
 * This class is used to maintain and write a master block.  The master block 
 * basically is a guide to which channels are in a Edge data file and where the 
 * first and last index block for this file are located.  The translation between
 * this block and its persistent storage on disk are made in this file.
 *
 */
package gov.usgs.anss.edge;

import gov.usgs.anss.util.*;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * This class represents a master block from the .idx file. It maintains the data and can convert
 * to/from the on disk structure. This class is mostly used by the IndexFile class to manipulate and
 * keep the MasterBlocks allocated within an .idx file. The translation between this block and its
 * persistent storage on disk are made in this file.
 *
 * <PRE>
 * The physical form is :
 * 12 Character seedname
 * Short starting index block
 * Short last index block
 * ...
 * repeat 32 times.
 * </PRE>
 *
 * @author davidketchum
 */
public final class MasterBlock {

  public static int MAX_CHANNELS = 32;
  public static final StringBuilder MASTERBLOCK = new StringBuilder(12).append("MASTERBLOCK ");
  private final int iblk;         // The disk block within the RawDisk object where this block
  private final RawDisk rw;       // The raw disk object used to write this block
  private final byte[] buf = new byte[512];      // Manage the raw bytes here 
  private final ByteBuffer bb;    // This is the byte buffer wrapping the buf
  private final StringBuilder[] seedNames;
  private final IndexFile indexFile;  // The index file this block is in
  private final short[] firstIndex;
  private final short[] lastIndex;
  private static boolean dbg;

  public int getNChan() {
    for (int i = 0; i < MAX_CHANNELS; i++) {
      if (Util.stringBuilderEqual(seedNames[i], "            ")) {
        return i;
      }
    }
    return MAX_CHANNELS;
  }

  /**
   * This should be used very carefully as it rename a channel!!!
   *
   * @param index The index seedname to change names
   * @param s The 12 character seedname to change to
   * @throws java.io.IOException
   */
  public void setSeedname(int index, StringBuilder s) throws IOException {
    Util.clear(seedNames[index]).append(s);
    updateBlock2();
  }

  public int getBlockNumber() {
    return iblk;
  }

  public static void setDebug(boolean b) {
    dbg = b;
  }

  /**
   * Creates a new instance of MasterBlock for the give block, IndexFile
   *
   * @param ib Block number in raw file for this master block
   * @param ifl the IndexFile object in which this master block exists
   * @param isNew if true, this is a newly allocated master block, if false, pre-existing
   * @throws IOException if read or write fails while creating the block
   */
  public MasterBlock(int ib, IndexFile ifl, boolean isNew) throws IOException {
    iblk = ib;
    indexFile = ifl;
    rw = ifl.getRawDisk();
    seedNames = new StringBuilder[MAX_CHANNELS];
    firstIndex = new short[MAX_CHANNELS];
    lastIndex = new short[MAX_CHANNELS];
    if (dbg) {
      Util.prta(" cons MasterBlock at " + iblk + " new=" + isNew);
    }
    if (iblk <= 0) {
      new RuntimeException("Exception: Attempt to create a MasterBlock OOR=" + iblk).printStackTrace();
    }
    bb = ByteBuffer.wrap(buf);  // buf is now the storage for the byte buffer
    if (isNew) {
      for (int i = 0; i < MAX_CHANNELS; i++) {
        seedNames[i] = new StringBuilder(12).append("            ");
        firstIndex[i] = 0;
        lastIndex[i] = 0;
      }
      updateBlock();
    } else {
      for (int i = 0; i < MAX_CHANNELS; i++) {
        seedNames[i] = new StringBuilder(12).append("            ");
      }
      rw.readBlock(buf, iblk, 512);
      fromBuf();
    }
  }

  /**
   * Creates a new instance of MasterBlock for a read only type since it cannot do any of the
   * EdgeMom updating without the IndexFile.
   *
   * @param ib Block number in raw file for this master block
   * @param r the RawDisk object in which this master block exists
   * @param isNew if true, this is a newly allocated master block, if false, pre-existing
   * @throws IOException if read or write fails while creating the block
   */
  public MasterBlock(int ib, RawDisk r, boolean isNew) throws IOException {
    iblk = ib;
    rw = r;
    seedNames = new StringBuilder[MAX_CHANNELS];
    firstIndex = new short[MAX_CHANNELS];
    lastIndex = new short[MAX_CHANNELS];
    if (dbg) {
      Util.prta(" cons MasterBlock at " + iblk + " new=" + isNew);
    }
    if (iblk <= 0) {
      new RuntimeException("Attempt to create a MasterBlock2 OOR=" + iblk + " r=" + r).printStackTrace();
    }
    bb = ByteBuffer.wrap(buf);  // buf is now the storage for the byte buffer
    if (isNew) {
      for (int i = 0; i < MAX_CHANNELS; i++) {
        seedNames[i] = new StringBuilder(12).append("            ");
        firstIndex[i] = 0;
        lastIndex[i] = 0;
      }
      updateBlock();
    } else {
      for (int i = 0; i < MAX_CHANNELS; i++) {
        seedNames[i] = new StringBuilder(12).append("            ");
      }
      rw.readBlock(buf, iblk, 512);
      fromBuf();
    }
    indexFile = null;
  }

  public void reload(int ib, RawDisk r) throws IOException {
    rw.readBlock(buf, iblk, 512);
    fromBuf();
  }

  private synchronized void toBuf() {
    synchronized (bb) {
      bb.clear();
      for (int i = 0; i < MAX_CHANNELS; i++) {
        for (int j = 0; j < 12; j++) {
          bb.put((byte) seedNames[i].charAt(j));
        }
        bb.putShort(firstIndex[i]).putShort(lastIndex[i]);
      }
    }
  }
  private final byte[] namebuf = new byte[12];

  private synchronized void fromBuf() {
    synchronized (bb) {
      bb.clear();
      for (int i = 0; i < MAX_CHANNELS; i++) {
        bb.get(namebuf);
        Util.clear(seedNames[i]);
        for (int j = 0; j < 12; j++) {
          seedNames[i].append((char) namebuf[j]);
        }
        firstIndex[i] = bb.getShort();
        lastIndex[i] = bb.getShort();
      }
    }
  }

  /**
   * get the ith first index block
   *
   * @param i the desired index in the block
   * @return the first index block number
   */
  public int getFirstIndexBlock(int i) {
    return ((int) firstIndex[i]) & 0xffff;
  }

  /**
   * this is only used by utilities to "fix" bad seed name or to make seednames invisible!
   *
   * @param zapname The channel name to zap
   * @param name The new name
   * @return The Master block as a raw buf
   */
  public byte[] jamSeedname(StringBuilder zapname, StringBuilder name) {
    for (int i = 0; i < MAX_CHANNELS; i++) {
      if (Util.stringBuilderEqual(seedNames[i], zapname)) {
        Util.clear(seedNames[i]).append(name);
        Util.prt("Zaping master block =" + iblk + " index=" + i + " from " + Util.toAllPrintable(zapname) + " to " + Util.toAllPrintable(name));
      }
    }
    toBuf();
    return buf;
  }

  /**
   * Find this channel in the block, if found return its index block number,
   *
   * @param name Name to look for
   * @throws IllegalSeednameException if the name input does not pass MasterBlock.checkSeedName()
   * @return -1 if cannot be added (master block is full) or index to this channel
   */
  public int getFirstIndexBlock(StringBuilder name)
          throws IllegalSeednameException {
    checkSeedName(name);
    for (int i = 0; i < MAX_CHANNELS; i++) {
      /*Util.prt(" name="+name+"["+i+"]="+seedNames[i]+
              " len="+name.length()+" "+seedNames[i].length());*/

      // If this is an existing channel, update its last index
      if (Util.stringBuilderEqual(seedNames[i], name)) {
        return ((int) firstIndex[i]) & 0xffff;
      }
    }
    return -1;
  }

  /**
   * Find this channel in the block, if found return its index block number,
   *
   * @param name Name to look for
   * @throws IllegalSeednameException if the name input does not pass MasterBlock.checkSeedName()
   * @return -1 if cannot be added (master block is full) or index to this channel
   */
  public int getLastIndexBlock(StringBuilder name)
          throws IllegalSeednameException {
    checkSeedName(name);
    for (int i = 0; i < MAX_CHANNELS; i++) {
      /*Util.prt(" name="+name+"["+i+"]="+seedNames[i]+
              " len="+name.length()+" "+seedNames[i].length());*/

      // If this is an existing channel, update its last index
      if (Util.stringBuilderEqual(seedNames[i], name)) {
        return ((int) lastIndex[i]) & 0xffff;
      }
    }
    return -1;
  }

  /**
   * Try to add this name to this master block. If successful, last index block is updated and disk
   * persistent storage is updated as well. If it is not found, -1 is returned indicating the
   * channel is not in this master block and the end of the channels were not found. If the end of
   * the masterblocks is reached (a blank name indicates this), add this as a new channel to that
   * master block.
   *
   * @param name Name to add to add
   * @param index index block
   * @throws IOException if write of master block fails.
   * @throws IllegalSeednameException if the name input does not pass MasterBlock.checkSeedName()
   * @return -1 if cannot be added (master block is full) or index to this channel
   */
  public synchronized int addIndexBlock(StringBuilder name, int index)
          throws IOException, IllegalSeednameException {
    checkSeedName(name);
    for (int i = 0; i < MAX_CHANNELS; i++) {
      /*Util.prt(" name="+name+"["+i+"]="+seedNames[i]+
              " len="+name.length()+" "+seedNames[i].length());*/

      // If this is an existing channel, update its last Q  index
      if (Util.stringBuilderEqual(seedNames[i], name)) {
        lastIndex[i] = (short) index;
        updateBlock();
        return i;
      }

      // If the MB seedname is blanks, we are out of channels, add this channel
      // to this block
      if (Util.stringBuilderEqual(seedNames[i], "            ")) {
        Util.clear(seedNames[i]).append(name);
        firstIndex[i] = (short) index;
        lastIndex[i] = firstIndex[i];
        updateBlock();
        return i;
      }
    }
    return -1;        // Channel is not in this MB and end of channels not reached
  }

  /**
   * Find this channel in this master block
   *
   * @param name The seed channel name (12 character)
   * @return -1 if not found. The channel number in this MasterBlock, if found
   */
  public int findChannel(StringBuilder name) {
    for (int i = 0; i < MAX_CHANNELS; i++) {
      if (Util.stringBuilderEqual(seedNames[i], name)) {
        return i;
      }
    }
    return -1;
  }

  /**
   * set a new last index block number for this named channel
   *
   * @param name The seed name of the channel
   * @param blk The block number of the last index.
   * @throws IOException if write of block is not successful
   * @throws IllegalSeednameException if name does not pass checkSeedName()
   * @return -1 if channel not in this iblk, else offset in this block of channel
   */
  public synchronized int setLastIndex(StringBuilder name, int blk)
          throws IOException, IllegalSeednameException {
    checkSeedName(name);
    for (int i = 0; i < MAX_CHANNELS; i++) {
      if (Util.stringBuilderEqual(seedNames[i], name)) {
        lastIndex[i] = (short) blk;
        updateBlock();
        return i;
      }
    }
    return -1;
  }

  /**
   * write out the master block represented by this object.
   *
   * @throws IOException if write fails
   */
  public final synchronized void updateBlock() throws IOException {
    toBuf();
    rw.writeBlock(iblk, buf, 0, 512);
    // NOTE: extentIndex = -1 is a flag that this is a MasterBlock to the EdgeBlockQueue
    //Util.prta("Mast blk update blk="+iblk+"/-1.-1"+" "+indexFile.getJulian()+"_"+IndexFile.getNode());
    if (iblk <= 0) {
      new RuntimeException("Exception:Updating a MasterBlock OOR=" + iblk).printStackTrace();
    }
    EdgeBlockQueue.queue(indexFile.getJulian(), IndexFile.getNode(), MASTERBLOCK, -1, iblk, -1, buf, 0);
  }

  /**
   * write out the master block represented by this object. Do not do any memory block inserts
   *
   * @throws IOException if write fails
   */

  public final synchronized void updateBlock2() throws IOException {
    toBuf();
    rw.writeBlock(iblk, buf, 0, 512);
    // NOTE: extentIndex = -1 is a flag that this is a MasterBlock to the EdgeBlockQueue
    //Util.prta("Mast blk update blk="+iblk+"/-1.-1"+" "+indexFile.getJulian()+"_"+IndexFile.getNode());
    if (iblk <= 0) {
      new RuntimeException("Exception:Updating a MasterBlock OOR=" + iblk).printStackTrace();
    }
  }
  /**
   * g et the channel string for a index block #
   *
   * @param index The channel within this block
   * @return String with representation of this index in the master block
   */
  private final StringBuilder gcsb = new StringBuilder(100);

  public StringBuilder getChannelString(int index) {
    Util.clear(gcsb);
    if (index < 0 || index > MAX_CHANNELS) {
      return gcsb.append("Index out of range=").append(index);
    }
    return gcsb.append(index).append(" ").append(seedNames[index]).append(" first=").
            append(firstIndex[index]).append(" last=").append(lastIndex[index]);
  }

  /**
   * get the seed name of the given index
   *
   * @param index The index to get
   * @return the seedName for the index
   */
  public StringBuilder getSeedName(int index) {
    return seedNames[index];
  }

  /**
   * static method that insures a seedname makes some sense. 1) Name is 12 characters long
   * nnssssscccll. 2) All characters are characters, digits, spaces, question marks or dashes 3)
   * Network code contain blanks 4) Station code must be at least 3 characters long 5) Channel codes
   * must be characters in first two places
   *
   * @param name A seed string to check
   * @throws IllegalSeednameException if any of the above rules are violated.
   */
  public static void checkSeedName(String name) throws IllegalSeednameException {
    if (name.length() != 12) {
      throw new IllegalSeednameException("Length not 12 is " + name.length()
              + " in [" + Util.toAllPrintable(name) + "]");
    }

    char ch;
    //char [] ch = name.toCharArray();
    for (int i = 0; i < 12; i++) {
      ch = name.charAt(i);
      if (!(Character.isLetterOrDigit(ch) || ch == ' ' || ch == '?' || ch == '_'
              || ch == '-')) {
        throw new IllegalSeednameException(
                "A seedname character is not letter, digit, space or [?-] ("
                + Util.toAllPrintable(name) + ") at " + i);
      }
    }
    if (name.charAt(0) == ' ' /*|| name.charAt(1) == ' '*/) // GEOS is network 'G'
    {
      throw new IllegalSeednameException(" network code blank (" + name + ")");
    }
    if (name.charAt(2) == ' ' || name.charAt(3) == ' ') {
      throw new IllegalSeednameException("Station code too short (" + name + ")");
    }
    if (!(Character.isLetterOrDigit(name.charAt(7)) && Character.isLetterOrDigit(name.charAt(8))
            && Character.isLetterOrDigit(name.charAt(9)))) {
      throw new IllegalSeednameException("Channel code not Letter, Letter, LetterOrDigit (" + name + ")");
    }

  }

  /**
   * static method that insures a seedname makes some sense. 1) Name is 12 characters long
   * nnssssscccll. 2) All characters are characters, digits, spaces, question marks or dashes 3)
   * Network code contain blanks 4) Station code must be at least 3 characters long 5) Channel codes
   * must be characters in first two places
   *
   * @param name A seed string to check
   * @throws IllegalSeednameException if any of the above rules are violated.
   */

  public static synchronized void checkSeedName(StringBuilder name) throws IllegalSeednameException {
    if (name.length() != 12) {
      throw new IllegalSeednameException("Length not 12 is " + name.length()
              + " in [" + Util.toAllPrintable(name) + "]");
    }

    char ch;
    //char [] ch = name.toCharArray();
    for (int i = 0; i < 12; i++) {
      ch = name.charAt(i);
      if (!(Character.isLetterOrDigit(ch) || ch == ' ' || ch == '?' || ch == '_'
              || ch == '-')) {
        throw new IllegalSeednameException(
                "A seedname character is not letter, digit, space or [?-] ("
                + Util.toAllPrintable(name) + ") at " + i);
      }
    }
    if (name.charAt(0) == ' ' /*|| name.charAt(1) == ' '*/) // GEOS is network 'G'
    {
      throw new IllegalSeednameException(" network code blank (" + name + ")");
    }
    if (name.charAt(2) == ' ' || name.charAt(3) == ' ') {
      throw new IllegalSeednameException("Station code too short (" + name + ")");
    }
    if (!(Character.isLetterOrDigit(name.charAt(7)) && Character.isLetterOrDigit(name.charAt(8))
            && Character.isLetterOrDigit(name.charAt(9)))) {
      throw new IllegalSeednameException("Channel code not Letter, Letter, LetterOrDigit (" + name + ")");
    }

  }

  /**
   * main unit test
   *
   * @param args command line
   */
  public static void main(String[] args) {
    try {
      checkSeedName("USMIAR BHE");
    } catch (IllegalSeednameException e) {
      Util.prt("(too short) " + e.getMessage());
    }
    try {
      checkSeedName("  MIAR BHE--");
    } catch (IllegalSeednameException e) {
      Util.prt("(no net) " + e.getMessage());
    }
    try {
      checkSeedName("USMIAR BH?  ");
    } catch (IllegalSeednameException e) {
      Util.prt("(bad comp) " + e.getMessage());
    }
    try {
      checkSeedName("USMI   BHE  ");
    } catch (IllegalSeednameException e) {
      Util.prt("(bad station) " + e.getMessage());
    }
    try {
      checkSeedName("USMIA/ BHE  ");
    } catch (IllegalSeednameException e) {
      Util.prt("(bad char) " + e.getMessage());
    }
  }
}
