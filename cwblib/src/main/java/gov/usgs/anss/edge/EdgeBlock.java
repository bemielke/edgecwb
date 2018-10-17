/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.edge;

import java.nio.ByteBuffer;
import gov.usgs.anss.util.Util;
import gov.usgs.anss.util.SeedUtil;
import java.text.DecimalFormat;

/**
 * this Block class just encapsulates a block in a data file with the additional data of the julian
 * day and block number of the target, index block, extent index, node, and a sequence number. This
 * is the format used to store and communicate between EdgeBlockQueues using Replication and
 * EdgeBlockClients. The BUFFER_LENGTH is the size of the binay package:
 * <PRE>
 * Raw form is :
 *
 * off name  type  description
 *  0 julian int if negative this is a continuation (no seed header)
 *  4 node   char[4]
 *  8 blk    int (if -1, then this is an index block update).
 * 12 indexBlock int index block this is stored in
 * 16 index  int The extent index within the index block
 * 20 seq    int The sequence number assigned this block,
 * 24 seedname String*12 The seed name this block is associated with
 * 36 ms     byte[512] with data from mini-seed or index block.
 * </PRE>
 *
 * @author davidketchum
 */
public class EdgeBlock {

  /**
   * The length of the data buffers in this queue
   */
  static public int BUFFER_LENGTH = 548;// Length of the data blocks held by this class
  private static final DecimalFormat df8 = new DecimalFormat(" 00000000;-0000");
  ;
  private static final DecimalFormat df4 = new DecimalFormat(" 0000;-000");
  private final byte[] buf = new byte[BUFFER_LENGTH];
  private final ByteBuffer b;
  private final StringBuilder seedname = new StringBuilder(12);

  public EdgeBlock() {
    b = ByteBuffer.wrap(buf);
    for (int i = 0; i < BUFFER_LENGTH; i++) {
      buf[i] = 1;
    }
  }

  /**
   * to String - write out data block and other id data
   *
   * @return A string representation
   */
  @Override
  public String toString() {
    return toStringBuilder(null).toString();
  }
  StringBuilder tmpsb = new StringBuilder(100);

  public StringBuilder toStringBuilder(StringBuilder tmp) {
    StringBuilder sb = tmp;
    if (sb == null) {
      sb = Util.clear(tmpsb);
    }
    synchronized (sb) {
      boolean zeros = false;
      if (buf[36] == 0 && buf[37] == 0 && buf[38] == 0 && buf[39] == 0) {
        zeros = true;
      }
      sb.append("EBD: ").append(getNode()).append(" ").append(SeedUtil.fileStub(getJulian())).
              append(isContinuation() ? "C" : " ").append(df8.format(getBlock())).
              append(df4.format(getIndexBlock())).append("/").append(df4.format(getExtentIndex())).
              append(df8.format(getSequence())).append(" ").append(getSeedName()).append(" ").
              append(("" + zeros).substring(0, 1));
    }
    return sb;
  }

  /**
   * Make a copy of this edgeBlock into another
   *
   * @param eb The EdgeBlock to copy it into
   */
  public void copyEdgeBlock(EdgeBlock eb) {
    System.arraycopy(buf, 0, eb.get(), 0, BUFFER_LENGTH);

  }

  /**
   * set the block to the given values
   *
   * @param julian The julian day
   * @param nd String with node of originating computer. (no more than 4 characters)
   * @param seedName The seedname associated with this block
   * @param nblk BLock in this julian day file for this block
   * @param indexBlock the index block that this data is recorded in
   * @param index The extent index in the index block corresponding to this data
   * @param bf A byte data buffer containing the data
   * @param offset The offset of the block within bf
   * @param sequence The sequence number to assign to this block
   */
  public void set(int julian, StringBuilder nd, StringBuilder seedName, int nblk, int indexBlock, int index,
          byte[] bf, int offset, int sequence) {
    //byte [] node = nd.getBytes();
    if (nd.length() > 4) {
      Util.prta("EBQ: found node > 4 characters! node=" + nd);
      new RuntimeException("EB: found more than 4 character node=" + nd).printStackTrace();
      throw new RuntimeException("EB: found more than 4 character node=" + nd);
      //System.exit(1);     // Panic!
    }
    b.clear();
    b.putInt(julian);
    for (int i = 0; i < 4; i++) {
      if (i < nd.length()) {
        b.put((byte) (nd.charAt(i) == ' ' ? 0 : nd.charAt(i)));
      } else {
        b.put((byte) 0);
      }
    }
    b.putInt(nblk);
    b.putInt(indexBlock);
    b.putInt(index);
    b.putInt(sequence);
    Util.clear(seedname).append(seedName);
    Util.rightPad(seedname, 12);
    //if(seedName.length() != 12) seedName = (seedName+"            ").substring(0,12);
    //b.put(seedName.getBytes());
    for (int i = 0; i < 12; i++) {
      b.put((byte) seedname.charAt(i));
    }
    b.put(bf, offset, Math.min(BUFFER_LENGTH - 36, bf.length - offset));
  }

  /**
   * set based on a raw buffer
   *
   * @param bf The raw buffer with BUFFER_LENGTH data
   * @param offset The offset in bf to start with
   * @param sequence The sequence number to assign to this block (if zero, leave it alone
   */
  public void set(byte[] bf, int offset, int sequence) {
    System.arraycopy(bf, offset, buf, 0, BUFFER_LENGTH);
    if (sequence > 0) {
      setSequence(sequence);
    }
  }

  /**
   * set based on a raw buffer
   *
   * @param bf The raw buffer with BUFFER_LENGTH data
   * @param sequence The sequence number to assign to this block (if zero, leave it alone
   */
  public void set(byte[] bf, int sequence) {
    System.arraycopy(bf, 0, buf, 0, BUFFER_LENGTH);
    if (sequence > 0) {
      setSequence(sequence);
    }
  }

  /**
   * set the sequence number of this buffer
   *
   * @param sequence the sequence number to set
   */
  public void setSequence(int sequence) {
    b.clear();
    b.position(20);
    b.putInt(sequence);
  }

  /**
   * return the mini-seed (data) portion
   *
   * @param b A buffer to receive the data.
   * @param offset the data is returned at this offset in the array
   */
  public void getData(byte[] b, int offset) {
    if (buf != null && buf.length > 0) {
      System.arraycopy(buf, 36, b, offset, Math.min(buf.length, 512));
    }
  }

  /**
   * return the mini-seed (data) portion
   *
   * @param b A buffer to receive the data.
   */
  public void getData(byte[] b) {
    if (buf != null && buf.length > 0) {
      System.arraycopy(buf, 36, b, 0, Math.min(buf.length, 512));
    }
  }

  /**
   * return the mini-seed (data) portion
   *
   * @return A buffer with the data.
   */
  public byte[] getData() {
    if (buf != null && buf.length > 0) {
      byte[] b2 = new byte[512];
      System.arraycopy(buf, 36, b2, 0, Math.min(buf.length, 512));
      return b2;
    }
    return null;      // there is not the right abount of data
  }

  /**
   * return the ready to write buffer. Note his is the internal buffer and not a copy of it
   *
   * @return the BUFFER_LENGTH buffer containing data
   */
  public byte[] get() {
    return buf;
  }

  /**
   * return julian day in stored block
   *
   * @return The julian day stored in the block
   */
  public int getJulian() {
    b.clear();
    return Math.abs(b.getInt());
  }

  /**
   * is this a MiniSeed continuation of a >512 length block
   *
   * @return true if it is a continuation of a longer than 512 byte MS block
   */
  public boolean isContinuation() {
    b.clear();
    return (b.getInt() < 0);
  }

  /**
   * return the node represented by this block
   *
   * @return the node as a String
   */
  public String getNode() {
    byte[] bftmp = new byte[4];
    b.clear();
    b.getInt();
    b.get(bftmp);
    for (int i = 0; i < 4; i++) {
      if (bftmp[i] == 0) {
        bftmp[i] = ' ';
      }
    }
    return new String(bftmp);
  }
  private final StringBuilder nodesb = new StringBuilder(4);
  private final byte[] bf = new byte[4];

  public StringBuilder getNodeSB() {
    b.clear();
    b.getInt();
    b.get(bf);
    Util.clear(nodesb);
    for (int i = 0; i < 4; i++) {
      nodesb.append((char) (bf[i] == 0 ? ' ' : bf[i]));
    }
    return nodesb;
  }

  /**
   * return the block number in the block
   *
   * @return the block number
   */
  public int getBlock() {
    b.position(8);
    return b.getInt();
  }

  /**
   * return the index block number in the block
   *
   * @return the index block number
   */
  public int getIndexBlock() {
    b.position(12);
    return b.getInt();
  }

  /**
   * return the index block extent index number in the block
   *
   * @return the extent index
   */
  public int getExtentIndex() {
    b.position(16);
    return b.getInt();
  }

  /**
   * return the index block extent index number in the block
   *
   * @return the extent index
   */
  public int getSequence() {
    b.position(20);
    return b.getInt();
  }

  /**
   * get the SEED station name (hopefully this is a data record)
   *
   * @return The 10 character SEEDNAME in sssssllcccnn straight from the header
   */
  public StringBuilder getSeedNameSB() {
    b.position(24);
    Util.clear(seedname);
    //byte [] bf = new byte[12];
    for (int i = 0; i < 12; i++) {
      seedname.append((char) b.get());
    }
    return seedname;
  }

  public String getSeedName() {
    return getSeedNameSB().toString();
  }
}
