/*
 *  This software is in the public domain because it contains materials 
 *  that originally came from the United States Geological Survey, 
 *  an agency of the United States Department of Interior. For more 
 *  information, see the official USGS copyright policy at 
 *  http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.edgeoutput;

import gov.usgs.anss.edge.IndexBlock;
import java.nio.ByteBuffer;

/**
 * This information is about the location of a single block. The instance # and julian get a file
 * name, the indexBlock, extent, and block with he indexBlock itself give an individual block. These
 * objects are exchanged when the delete() or replace() features of the CWBEditor are exchanged from
 * the QueryServer.
 *
 * @author U.S. Geological Survey <ketchum at usgs.gov>
 */
public final class DeleteBlockInfo implements Comparable {

  public static final int DELETE_BLOCK_SIZE = 28;
  // These data are represented as a binary as bytes at the offsets given below in internet ordering
  // off  Description
  private String seedname;    // 0  12 byte seedname
  private String instance;    // 12 4 characters like 1#19
  private int julian;         // 16 int The julian day
  private int indexBlock;     // 20 short The index block number
  private int extent;         // 22 bytethe extent nubmer 1-30 (IndexBlock.MAX_EXTENTS
  private int block;          // 23 byte 0-63 block number
  private short earliest;     // 24 short secs since midnight/3
  private short latest;       // 26 short secs since midnight/3
  private final byte[] bytes = new byte[DELETE_BLOCK_SIZE];

  @Override
  public String toString() {
    return seedname + "/" + instance + " jul=" + julian + " idx/ext/blk="
            + indexBlock + "/" + extent + "/" + block + " early=" + IndexBlock.timeToString(earliest, null) + "-" + IndexBlock.timeToString(latest, null);
  }

  /**
   * COmpare two DeleteBlockInfo for ordering. Seedname, then instance, then julian day, the index
   * block number then extent index, then block
   *
   * @param b The other DeleteBLockInfo
   * @return -1 <than, 0 equals, 1 >than
   */
  @Override
  public int compareTo(Object b) {
    if (b instanceof DeleteBlockInfo) {
      DeleteBlockInfo a = (DeleteBlockInfo) b;
      int i = seedname.compareTo(a.getSeedname());
      if (i != 0) {
        return i;
      }
      i = instance.compareTo(a.getInstance());
      if (i != 0) {
        return i;
      }
      i = a.getJulian();
      int j = julian;
      if (j != i) {
        return (j > i ? 1 : (j < i ? -1 : 0));
      }
      i = a.getIndexBlock();
      j = indexBlock;
      if (j != i) {
        return (j > i ? 1 : (j < i ? -1 : 0));
      }
      i = a.getExtent();
      j = extent;
      if (j != i) {
        return (j > i ? 1 : (j < i ? -1 : 0));
      }
      i = a.getBlock();
      j = block;
      if (j != i) {
        return (j > i ? 1 : (j < i ? -1 : 0));
      }
    }
    return 0;
  }

  /**
   * create object from a ByteBuffer pointing to the beginning of its binary representation on the
   * network
   *
   * @param bb The ByteBuffer pointing to the binary data
   */
  public DeleteBlockInfo(ByteBuffer bb) {
    reload(bb);
  }

  /**
   * Make a new
   *
   * @param seedname The seedname NNSSSSSSCCCLL format
   * @param bb The byte buffer with the binary data
   */
  public DeleteBlockInfo(String seedname, ByteBuffer bb) {
    reload(seedname, bb);
  }

  /**
   * reload an existing DBI object from a binary ByteBuffer
   *
   * @param bb the buffer to load
   */
  public final void reload(ByteBuffer bb) {
    int pos = bb.position();
    bb.get(bytes);
    String s = new String(bytes, 0, 12);
    bb.position(pos);
    reload(s, bb);
  }

  /**
   * Reload a DBI from a seedname (NNSSSSSCCCLL) and a byte buffer
   *
   * @param seedname The NNSSSSSCCCLL for the channel
   * @param bb Byte buffer with binary data starting at the instance (after the seedname)
   */
  public final void reload(String seedname, ByteBuffer bb) {
    int pos = bb.position();
    bb.get(bytes);
    bb.position(pos + 12);
    this.seedname = seedname;
    byte[] b = new byte[4];
    bb.get(b);
    instance = new String(b, 0, 4);
    julian = bb.getInt();
    indexBlock = bb.getShort();
    extent = bb.get();
    block = bb.get();
    earliest = bb.getShort();
    latest = bb.getShort();
  }

  /**
   * Make a new DBI with the various elements
   *
   * @param seedname NNSSSSSCCCLL seedname format
   * @param instance An up to 4 character instance name of a CWB which created the data
   * @param julian The julian day this DBI is on.
   * @param indexBlock the index block in the IndexFile whose filename comes from julian and
   * instance.
   * @param extent The extent index number of the proper extent in the index block
   * @param block The block offset from the extent start of this block
   * @param earliest The time of day in seconds divided by 3 of the earliest time in the extent
   * @param latest The time of day in seconds divided by 3 which encloses the latest time in the
   * extent
   */
  public DeleteBlockInfo(String seedname, String instance, int julian, int indexBlock, int extent, int block, short earliest, short latest) {
    reload(seedname, instance, julian, indexBlock, extent, block, earliest, latest);
  }

  public final void reload(String seedname, String instance, int julian, int indexBlock,
          int extent, int block, short earliest, short latest) {
    this.seedname = seedname;
    this.instance = instance;
    this.julian = julian;
    this.indexBlock = indexBlock;
    this.extent = extent;
    this.block = block;
    this.earliest = earliest;
    this.latest = latest;
    ByteBuffer bb = ByteBuffer.wrap(bytes);
    bb.position(0);
    bb.put(seedname.getBytes());
    bb.put((instance + "    ").substring(0, 4).getBytes());
    bb.putInt(julian);
    bb.putShort((short) indexBlock);
    bb.put((byte) extent);
    bb.put((byte) block);
    bb.putShort(earliest);
    bb.putShort(latest);
  }

  /* Getters */
  public String getIndexString() {
    return seedname + "/" + instance + "/" + julian + "/" + indexBlock + "/" + extent + "/" + block;
  }

  public String getSeedname() {
    return seedname;
  }

  public String getInstance() {
    return instance;
  }

  public int getJulian() {
    return julian;
  }

  public int getIndexBlock() {
    return indexBlock;
  }

  public int getExtent() {
    return extent;
  }

  public int getBlock() {
    return block;
  }

  public short getEarliest() {
    return earliest;
  }

  public short getLatest() {
    return latest;
  }

  public byte[] getBytes() {
    return bytes;
  }
}
