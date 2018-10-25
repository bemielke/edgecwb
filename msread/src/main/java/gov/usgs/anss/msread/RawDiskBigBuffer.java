/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.msread;

import java.io.*;
import java.util.ArrayList;
import gov.usgs.anss.edge.RawDisk;
import gov.usgs.anss.edgethread.EdgeThread;
import gov.usgs.anss.util.*;
//import java.util.Random;          // used in test main only

/**
 * This class present a block oriented read/write for the RandomAccessFile class. It basically adds
 * methods which insure read/write on 512 byte boundaries. It does so by using a 'big' prefetched
 * buffer that moves through the file at the users command and allows block orient read requests
 * against that buffer. Read request outside of the buffer wait until the user advances the buffer
 * to include the blocked request. This is mainly used by AnalyzeDiskFile to look at Edge files
 * efficiently (see that process for more details on the implementation).
 * <p>
 * 1) Create one of these setting a buffer size 2) Create threads which are reading blocks and
 * moving up through a file 3) Have a method to determine all of the threads are blocked waiting for
 * more data from this file 4) When that happens, cause the next section to be read using nextBuffer
 * 5) continue until end of disk is reached with steps 3, 4, and 5.
 * <p>
 * Buffering details - the big buffer is really a ArrayList of smaller byte arrays of bufSizeMB 
 * parameter megabytes in size up to the length of
 * desired buffer (sizemb in the constructor). This is limited only by the systems ability to provide 
 * these smaller buffers. A java
 * array is address by int (32 bits ) so only 2 gB can be addressed in a single array so this is the maximum
 * size of the bufSizeMB parameter (2048 mB). The ArrayList
 * of many bufSizeMB byte arrays simulates a really big buffer of any size.
 *
 * @author davidketchum
 */
public final class RawDiskBigBuffer {

  private static long oneBufSize;
  private static int nbufs;
  private static final ArrayList<byte[]> bufs = new ArrayList<>(16);
  //private static byte[] buf;
  private static long bufsize;

  // Disck connection and information
  private static RawDisk rw;
  private String mode;
  private String filename;
  private long lastUsed;
  private int firstBlock;     // first block represented in the buffer
  private int lastBlock;      // last block represented in the buffer (last block readable +1)
  private int maxBlock;       // Size of the input file in blocks

  @Override
  public String toString() {
    return filename + " blks=" + firstBlock + "-" + lastBlock + " max=" + maxBlock
            + " size=" + (bufsize / 1024 / 1024) + "mB";
  }

  /**
   * Close the file, do not release the buffer space - it can be reused
   *
   */
  public void close() {
    try {
      rw.close();
    } catch (IOException e) {
      Util.IOErrorPrint(e, "closing RawDiskBuffer");
    }
    rw = null;
  }

  /**
   *
   * @return The maximum block in the file (file length/512)
   */
  public int getMaxBlock() {
    try {
      return (int) (rw.length() / 512L);
    } catch (IOException e) {
    }
    return -1;
  }

  /**
   *
   * @return first block represented by the current memory buffer, starts at zero
   */
  public int getFirstBlock() {
    return firstBlock;
  }

  /**
   *
   * @return The block number of the last block in the file (last block readable + 1)
   */
  public int getLastBlock() {
    return lastBlock;
  }

  /**
   * Size of the input file in bytes
   *
   * @return Length in bytes
   * @throws IOException
   */
  public long length() throws IOException {
    return rw.length();
  }

  /**
   * get the time this RawDisk last did any I/O
   *
   * @return The System.getCurrentMillis() of the last I/O
   */
  public long getLastUsed() {
    return lastUsed;
  }

  /**
   * Creates a new instance of RawDiskBigBuffer.  This creates a huge buffer of size (sizemb) megabytes made
   * up of many buffers of size bufSizeMB.  Example: bufSizeMB = 1024 mB and sizemb = 12000 mB would be 
   * created by allocating 12 arrays of 1024 mB. This class then reads in 12 of the 1024 MB arrays from disk
   * until the user says to go on to the next section.  The readBlock*() methods convert the block offset
   * from the begining of the file into the correct array of 1024 mB data and its offset in that array and returns
   * the 512 bytes from that location.  
   * <p>
   * The idea is to read the data from some large file into memory in big sections (1 gB in the example), and then
   * cache many of these sections so that the data can be randomly accessed by the user program.  When the user
   * program is done with the data in the sizemb section, it calls getNextBuffer() to read in data starting at
   * offset sizemb MB into the file. 
   *
   * @param file - the filename or full path name to the file
   * @param md Mode of open "r", "rw", "rws" or "rwd" - rwd is recommended
   * @param bufSizeMB The size in megabytes of each buffer allocation to make up the full size (max 2048)
   * @param sizemb The size of the big buffer to prefetch to in mB (made up of many bufsizeMB arrays (limit memory of system)
   * @throws FileNotFoundException If md does not include a "w", then file was not found for reading.
   */
  public RawDiskBigBuffer(String file, String md, int bufSizeMB, int sizemb) throws FileNotFoundException {
    filename = file;
    mode = md;
    if(oneBufSize == 0) {
      oneBufSize = bufSizeMB * 1024 * 1024;
    }
    else {
      if (oneBufSize != bufSizeMB * 1024 * 1024) {
        Util.prt("Attempt to change bufsize!");
      }
    }
    if (bufsize == 0) {
      allocateBufs(sizemb);
    } else if (bufsize < sizemb * 1024 * 1024 || oneBufSize != bufSizeMB * 1024 * 1024) {
        bufs.clear();
        System.gc();
        allocateBufs(sizemb);
        Util.prta("RawDiskBigBuffer: resize from " + bufsize / 1024 / 1024 + " to " + sizemb);
    }
    else {
      Util.prta("RawDiskBigBuffer: keep existing buffers " + bufsize / 1024 / 1024 + " to " + sizemb);
    }
    if (rw != null) {
      Util.prta("RDBB: trying to open another RawDisk while one is still open! rw=" + rw + " new file=" + file);
      Util.exit(1);
    }

    rw = new RawDisk(file, md);
    maxBlock = getMaxBlock();
    try {
      Util.prta("RDBB: open file=" + file + " mode=" + md + " bufsize=" + sizemb + " file is "
              + (rw.length() / 512L) + " blks");
    } catch (IOException e) {
      Util.IOErrorPrint(e, "Opening file=" + file);
      System.exit(0);
    }
    firstBlock = 0;
    lastBlock = 0;
    getNextBuffer();

    //nread=0;
    //nwrite=0;
    lastUsed = System.currentTimeMillis();
  }

  private void allocateBufs(long sizemb) {
    bufsize = sizemb * 1024 * 1024;

    nbufs = (int) ((bufsize + oneBufSize - 1) / oneBufSize);    // number of 1 gB buffers

    for (int i = 0; i < nbufs - 1; i++) {
      bufs.add(new byte[(int) oneBufSize]);
    }
    int remain = (int) (bufsize - (nbufs - 1) * oneBufSize);
    bufs.add(new byte[remain]);
  }

  /**
   * read in the next buffer
   *
   * @return true if the EOF has been reached or other IO error), otherwise return false
   */
  public synchronized boolean getNextBuffer() {
    int newfirst = lastBlock;
    lastBlock = firstBlock;
    try {
      long start = System.currentTimeMillis();
      long readlen = (maxBlock - newfirst) * 512L;
      if (readlen > bufsize) {
        readlen = bufsize;
      }
      if (readlen <= 0) {
        return true;
      }
      int iblk = newfirst;
      long nread = 0;
      int i = 0;
      while (nread < readlen) {
        rw.seek(iblk * 512L);
        long len = readlen - nread;       // assume read from hear to end of buffers
        if (len > oneBufSize) {
          len = oneBufSize;
        }
        rw.readFully(bufs.get(i), 0, (int) len);
        nread += len;
        i++;
        iblk += len / 512;
      }
      firstBlock = newfirst;
      lastBlock = firstBlock + (int) (nread / 512L);
      if (lastBlock % 64 != 0) {
        lastBlock = lastBlock / 64 * 64 + 64;
      }
      Util.prta("RDBB: get next new first=" + firstBlock + " last=" + lastBlock + " max=" + maxBlock
              + " elapse=" + (System.currentTimeMillis() - start));
      return false;
    } catch (IOException e) {
      Util.IOErrorPrint(e, "RawDiskBigBuffer IOERror reading buffer");
      System.exit(0);
    }
    return true;
  }

  /**
   * return the file name of this file
   *
   * @return String with file name
   */
  public String getFilename() {
    return filename;
  }

  /**
   * read starting at blk for len and return as a buffer. Uses readFully so the entire length must
   * be returned. This bypasses the big buffer and does physical I/O to get the data.
   *
   * @param iblk the block to start the read
   * @param len the length of the read
   * @throws IOException If seek or read fails.
   * @return byte array with data - length is fully read so its always len long.
   */
  public synchronized byte[] readBlockFromRawDisk(int iblk, int len) throws IOException {
    return rw.readBlock(iblk, len);
  }

  /**
   * read starting at blk for len and return as a buffer, this does not return until the full
   * requested length is read. If the read is past the end of the big buffer, it sleeps until a new
   * buffer is read and the request can be satisfied.
   *
   * @param b A buffer to put the data in.
   * @param iblk the block to start the read
   * @param len the length of the read
   * @param tag Some text to include in putput to identify the caller that is blocked
   * @throws IOException If seek or read fails.
   * @return len if data were read, should block if not
   */
  public synchronized int readBlockFromBuffer(byte[] b, int iblk, int len, String tag) throws IOException {
    return readBlockFromBuffer(b, iblk, len, true, tag);
  }

  /**
   * read starting at blk for len and return as a buffer, this does not return until the full
   * requested length is read. If the read is past the end of the big buffer, it sleeps until a new
   * buffer is read and the request can be satisfied if block is true, or returns immediately with
   * and return of -1 indicating that the end of the buffer has been reached..
   *
   * @param b A buffer to put the data in.
   * @param iblk the block to start the read
   * @param len the length of the read
   * @param block If true, then block and wait for next buffer to load, else return -1 indicating
   * not successful
   * @param tag Some text to include in putput to identify the caller that is blocked
   * @throws IOException If seek or read fails.
   * @return len if data were read, should block if not
   */
  public synchronized int readBlockFromBuffer(byte[] b, int iblk, int len, boolean block, String tag) throws IOException {
    if (b.length < len) {
      throw new IOException("readBlock buf length less than request " + b.length + "<" + len);
    }
    lastUsed = System.currentTimeMillis();
    if (iblk < firstBlock) {
      throw new IOException("Attempt to read before first block RDBG iblk=" + iblk + " first=" + firstBlock);
    }
    int outoff = 0;
    while (outoff < len) {
      int loop = 0;
      // If this block is out of the input range, wait for it to be read
      while (iblk >= lastBlock) {
        if (!block) {
          return -1;
        }
        Util.sleep(100);
        if (loop++ % 100 == 1) {
          Util.prt("waiting on block " + iblk + " " + tag);
        }
      }
      if (iblk < firstBlock) {
        throw new IOException("Attempt to read before first block RDBG iblk=" + iblk + " first=" + firstBlock);
      }
      long off = (iblk - firstBlock) * 512L;
      int i = (int) (off / oneBufSize);
      off = off % oneBufSize;
      if(off < 0 || outoff < 0 || i < 0) {
        Util.prt("iblk="+iblk+" len="+len+" off="+off +" i="+i+" outoff="+outoff+" "+toString());
      }
      System.arraycopy(bufs.get(i), (int) off, b, outoff, 512);
      outoff += 512;
      iblk++;
    }
    return len;
  }

  /**
   * Main unit test
   *
   * @param args command line args
   */
  public static void main(String[] args) {
    EdgeThread.setMainLogname(null);
    Util.init("edge.prop");
    byte[] b = new byte[512];
    byte[] a = new byte[512];
    byte[] z = new byte[102400];
    byte[] c;
    Util.prta("Start 10 mBlk read");
    try {
      RawDiskBigBuffer rwtst = new RawDiskBigBuffer("/data2/2017_180_6#0.ms", "r", 246, 2048);
      for (int i = 0; i < 40000000; i++) {
        int len = rwtst.readBlockFromBuffer(b, i, 512, false, "testing");
        if (len == -1) {
          boolean fmpt = rwtst.getNextBuffer();
          Util.prta("Get next buffer " + rwtst);
          if (fmpt) {
            Util.prta("End of file found at " + rw);
            rw.close();
            break;
          }
        }
        if (i % 1000000 == 0) {
          Util.prta("i=" + i);
        }
      }
      //Util.prta("i="+(i++));
    } catch (FileNotFoundException e) {
      Util.IOErrorPrint(e, "File open err on temp");
    } catch (IOException e) {
      Util.IOErrorPrint(e, "IOerror ");
    }
    Util.prta("End of execution");
  }
}
