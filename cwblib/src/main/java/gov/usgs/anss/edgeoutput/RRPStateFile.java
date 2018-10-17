/*
 *  This software is in the public domain because it contains materials 
 *  that originally came from the United States Geological Survey, 
 *  an agency of the United States Department of Interior. For more 
 *  information, see the official USGS copyright policy at 
 *  http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.edgeoutput;

import gov.usgs.anss.edgethread.EdgeThread;
import gov.usgs.anss.util.Util;
import java.io.*;
import java.nio.ByteBuffer;
//import gov.usgs.anss.util2.Util;

/**
 * This class manages an RRPServer or RRPclient state file. It is designed to store two sequences in
 * a file of many blocks in size where the file contains each successively updated last sequence2 in
 * the file where sequences of -1 indicate this position does not yet maintain a sequence. As each
 * block is consumed, the next block is set and the offset incremented until the block cycles back
 * to zero. When the file is full (that is we want to write into the nblk block), it cycles back to
 * the first block, offset =0, and puts the first sequence in offset zero.
 * <p>
 * When opening an existing file, the blocks are scanned until the first -1 is found, the lastIndex
 * is set to the one just before the -1 and the iblk and offset set to the first -1.
 * <p>
 * This elaborate method of keeping track of the last sequence state is done so that on static ram
 * disk files, the writing of the blocks cycles over many blocks so the write to one block does not
 * occur so often that the static ram write limit does not cause static ram disk failure.
 * <p>
 * The sequences were named from the point of view of a RRPClient writing data (nextout and
 * lastack), however, this class is used by RRPServers as well which are tracking two sequences for
 * different reasons.
 *
 * @author U.S. Geological Survey <ketchum at usgs.gov>
 */
public final class RRPStateFile {

  private RandomAccessFile out;
  private int iblk;
  private int offset;        // offset in ints to the current position of a -1 in file
  private byte[] buf = new byte[512];
  private int lastIndex;   // this is the last sequence which has been processed by this program
  private int lastAck;     // This is the last sequence the program has acked from RRPServer end
  private ByteBuffer bb;
  private int nblk;
  private String filename;
  private EdgeThread par;
  private boolean isNew;
  private boolean dbg = false;

  public boolean isNew() {
    return isNew;
  }

  public void setDebug(boolean t) {
    dbg = t;
  }

  /**
   * Return the 1st of the two indices tracked. For a writer this is the lastIndex
   *
   * @return The 1st index tracked, for a writer the lastIndex
   */
  public int getLastIndex() {
    return lastIndex;
  }

  /**
   * Return the 2nd of the indices tracked. For a writer, the last acked sequence from RRPServer
   *
   * @return The 2nd index tracked, for a writer the last acked sequence from RRPServer
   */
  public int getLastAck() {
    return lastAck;
  }

  private void prt(String s) {
    if (par != null) {
      par.prt(s);
    } else {
      EdgeThread.staticprt(s);
    }
  }

  private void prta(String s) {
    if (par != null) {
      par.prta(s);
    } else {
      EdgeThread.staticprta(s);
    }
  }

  public RRPStateFile(String filename, int nblk, EdgeThread parent) throws IOException {
    this.filename = filename;
    this.nblk = nblk;             // This works out to one block for every mB in the data file
    par = parent;
    if (nblk <= 0) {
      this.nblk = 100;
    }
    bb = ByteBuffer.wrap(buf);
    try {
      out = new RandomAccessFile(filename, "rw");
      if (out.length() == 0) {   // Is this a new file
        clearFile();
        iblk = 0;
        offset = 0;
      } else {    // This is a old file, find the first -1 to set the offset
        this.nblk = (int) (out.length() / 512);
        iblk = 0;
        offset = 0;
        // Look through the file looking for -1
        int index = 0;
        lastIndex = 1;
        for (iblk = 0; iblk < this.nblk; iblk++) {
          out.seek(iblk * 512);
          out.read(buf, 0, 512);
          bb.position(0);
          for (offset = 0; offset < 128; offset++) {
            if ((index = bb.getInt()) == -1) {
              break;
            }
            lastIndex = lastAck;      // put prior value as last index
            lastAck = index;          // this value as last ack when -1 occurs they are set correctly
          }
          if (index == -1) {
            break;    // We found the -1, no need to check for more blocks
          }
        }
        prta(filename + " Found new index at " + iblk + "/" + offset + " nblk=" + this.nblk + " lastAck=" + lastAck + " lastIndex=" + lastIndex);
      }
    } catch (FileNotFoundException e) {
      prta(filename + " File not found");
      throw e;
    } catch (IOException e) {
      prta("IOErr=" + e);
      throw e;

    }
  }

  /**
   * Read in the contents of the disk and reset the lastIndex and lastAck variables. Note: this
   * should not be done unless initial values are unknown. These variables are updated in memory all
   * of the time and might have newer values.
   *
   * @throws IOException
   */
  public final void readMaster() throws IOException {
    // Look through the file looking for -1
    int index = 0;
    for (;;) {
      out.seek(iblk * 512);
      out.read(buf, 0, 512);
      bb.position(offset * 4);
      for (; offset < 128; offset++) {
        if ((index = bb.getInt()) == -1) {
          break;
        }
        lastIndex = lastAck;
        lastAck = index;
      }
      if (index == -1) {
        return;   // It was in this block
      }
      iblk = iblk + 1;
      offset = 0;
      if (iblk >= nblk) {        // The last one has been used, there should be a new file

        boolean done = false;
        prta(filename + "readmaster is wrapping to new file");
        while (!done) {
          out.close();
          iblk = 0;
          try {
            out = new RandomAccessFile(filename, "r");   // Try to open the newly created index
          } catch (FileNotFoundException e) {    // It does not exist yet
            Util.sleep(1000);                 // sleep and continue
            continue;
          }
          try {
            out.seek((nblk - 1) * 512);     // read the last block
          } catch (IOException e) {       // Maybe the file is not long enough yet
            Util.sleep(1000);           // sleep and retry
            continue;
          }
          out.read(buf, 0, 512);     // read the last block, if it is a new file it will have all -1s
          bb.position(0);
          if (bb.getInt() == -1) {
            done = true;
          }
          Util.sleep(1000);
        }
        // Go to the beginning of the file
        iblk = 0;
        offset = 0;
      }
    }     // End for for(;;)
  }

  /**
   * write a sequence into the next position in the file. If this fills the block, setup next block
   * If it fills the file, create a new file and clear it after renaming the old one.
   *
   * @param seq The sequence to record as the last one
   * @param lastack The sequence number last ACKed
   * @param writeDisk If true, this set is updated onto disk
   * @throws IOException
   */
  public void updateSequence(int seq, int lastack, boolean writeDisk) throws IOException {
    lastIndex = seq;
    lastAck = lastack;
    if (dbg) {
      prta(filename + " update to seq=" + seq + " last=" + lastack + " write=" + writeDisk);
    }
    if (writeDisk) {
      // Put the sequence in the position
      bb.position(offset * 4);
      bb.putInt(seq);
      bb.putInt(lastack);
      offset += 2;          // just used two more values
      out.seek(iblk * 512);
      out.write(buf, 0, 512);
      // Is the next position in the next block, if so reset offset and block number
      if (offset >= 128) {
        iblk++;
        offset = 0;
        for (int i = 0; i < 512; i++) {
          buf[i] = -1;   // clear the current block buffer
        }        // Are we now past the last block for the file size, if so create a new file and move on
        if (iblk >= nblk) {
          prta("updateSequnce : Index is wrapping to beginning of state file iblk=" + iblk + " offset=" + offset);
          iblk = 0;
          offset = 0;
          out.close();
          File file = new File(filename);           // We are going to rename the old file
          File rename = new File(filename + "_DEL");  // to this and then open a new one
          file.renameTo(rename);                    // out file is now renamed.
          out = new RandomAccessFile(filename, "rw");
          clearFile();
          bb.position(0);
          bb.putInt(seq);
          bb.putInt(lastack);
          out.seek(0L);
          out.write(buf, 0, 512);
        }
      }
    }

  }

  /**
   * cearl the current out file to all -1
   *
   * @throws IOException
   */
  public final void clearFile() throws IOException {
    out.seek(0L);
    prt(filename + " ** Creating/Clear RRPState file=" + filename + " nblk=" + nblk);
    for (int i = 0; i < 512; i++) {
      buf[i] = -1;
    }
    for (int i = 0; i < nblk; i++) {
      out.write(buf, 0, 512);
    }
    lastAck = -1;
    lastIndex = -1;     // We know nothing about sequences.
    iblk = 0;
    isNew = true;
  }

  public void close() {
    if (out != null) {
      try {
        updateSequence(lastIndex, lastAck, true);     // force the values to disk
        out.close();
      } catch (IOException e) {
        prta(" IOError closing state file=" + filename + " e=" + e);
      }
    }
  }

  public static void main(String[] args) {
    Util.init("edge.prop");
    Util.setModeGMT();
    String logfile = "rrpstate";
    EdgeThread.setMainLogname(logfile);
    RRPStateFile file, file2;
    try {
      file = new RRPStateFile("rrp.last", 20, null);
      file2 = new RRPStateFile("rrp.last", 0, null);
      file.updateSequence(0, 0, true);
      for (int i = 0; i < 10000; i++) {
        file.updateSequence(i, i + 1000, i % 10 == 0);
        if (i == 1279) {
          Util.prt("2259");
        }
        if (i % 10 == 0) {
          //file2.readMaster();
          Util.prt(i + " ack=" + file2.getLastAck() + " index=" + file2.getLastIndex());
        }
      }
      file.prt("Done");
    } catch (IOException e) {
      e.printStackTrace();
    }
  }
}
