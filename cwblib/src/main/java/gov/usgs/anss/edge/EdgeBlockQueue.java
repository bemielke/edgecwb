/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.edge;

import java.io.IOException;
import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.io.FileOutputStream;
import gov.usgs.anss.edgethread.EdgeThread;
import gov.usgs.anss.util.Util;

/**
 * This class represents a ring buffer of the data blocks as they are written into disk blocks. It
 * is entirely static as their can only be one such queue global to all channels of data within an
 * edge node (or at least a single EdgeMom).
 *
 * The methods are synchronized to insure consistent access. The data are tracked by their sequence
 * number which varies from 1 to MAX_SEQUENCE. (zero has been reserved to indicate unknown
 * sequence). The data are physically stored in sequence % maxqueue which is stipulated at
 * construction (maxqueue must divide evenly into MAX_SEQUENCE).
 *
 * @author davidketchum
 */
public final class EdgeBlockQueue {

  /**
   * this is the max sequence. They go from 1 to this value
   */
  public static int MAX_SEQUENCE = 2000000000;
  public static EdgeBlock[] q;          // Blocks of buffers for data to ring buffer queue into
  private static int maxqueue;        // number of elements in q
  private static int sequence;        // sequence assigned to each block
  private static boolean ebqdbg;   // is debug on
  private static boolean rawsave;// create a debugging file with raw inputs
  private static RawDisk saveFile;    // This will be created by a save rawSaveOn
  private static long lastLap;        // Time of last lapping of ring buffer
  private static final String logname = "ebq";
  private static PrintStream out;
  private static long lastday;

  /**
   * print the string on 1) My local output file if defined, 2) The EdgeMom log with tag privix if
   * not
   *
   * @param s The string to print
   */
  public static void dbgprt(String s) {

    // If we have a log name, but it is not open, or its a new day - open it
    if (logname != null && (out == null || lastday != System.currentTimeMillis() / 86400000L)
            && out != System.out) {
      if (out != null) {
        out.close();
      }
      lastday = System.currentTimeMillis() / 86400000L;
      try {
        out = new PrintStream(
                new FileOutputStream(
                        Util.getProperty("logfilepath") + logname + ".log" + EdgeThread.EdgeThreadDigit()));
      } catch (FileNotFoundException e) {
        Util.IOErrorPrint(e, "Cannot open log file "
                + Util.getProperty("logfilepath") + logname + ".log" + EdgeThread.EdgeThreadDigit());
        out = System.out;           // emergency, send it to standard out
      }
    }
    if (out != null) {
      out.println(s);
    } else {
      Util.prt("EBQ: " + s);
    }
  }

  public static void dbgprta(String s) {
    dbgprt(Util.asctime() + " " + s);
  }

  /**
   * set rawsave one, open up the file - used to create debug streams of bytes for testing
   *
   * @param filename The filename where the stream of bytes through this routine are to be saved
   */
  public void rawSaveOn(String filename) {
    rawsave = true;
    try {
      saveFile = new RawDisk(filename, "rw");   // was always "rwd"
    } catch (FileNotFoundException e) {
      Util.prt("save file failed File not found=" + e.getMessage());
      System.exit(0);
    }
    //catch(IOException e) {Util.prt("IOerror opening save file"+e.getMessage()); System.exit(0);}

  }

  /**
   * return sequence of next buffer to have data put in it
   *
   * @return The sequence of the next place data will be queued
   */
  static public int getNextSequence() {
    return sequence;
  }

  /**
   * return the sequence following the given one with roll overs etc.
   *
   * @param i The sequence that needs to be incremented and possibly rolled
   * @return The next sequence
   */
  static public int incSequence(int i) {
    i++;
    if (i > MAX_SEQUENCE) {
      i = 1;
    }
    return i;
  }

  /**
   * return size of queue
   *
   * @return Size of the queue
   */
  static public int getMaxQueue() {
    return maxqueue;
  }

  /**
   * get the data represented by the given sequence to this ring buffer
   *
   * @param index The sequence of the desired buffer from the ring
   * @return Buffer with the data (this is the array so it will be overwritten eventually)
   */
  static synchronized public byte[] get(int index) {
    return q[index % maxqueue].get();
  }

  /**
   * return true if the data buffer has lapped once
   *
   * @return true if data buffers have lapped onec
   */
  public static boolean getLapped() {
    return (sequence > maxqueue);
  }

  /**
   * set debug state
   *
   * @param t The new debug state
   */
  public static void setDebug(boolean t) {
    ebqdbg = t;
    Util.prt("EBQ: debug set=" + ebqdbg);
  }

  /**
   * return debug state
   *
   * @return the flag
   */
  public static boolean getDebug() {
    return ebqdbg;
  }

  /**
   * Creates a new instance of EdgeBlockServer. The given maxq must divide evenly into the
   * MAX_SEQUENCE (2,000,000,000 at this time) or it is an error
   *
   * @param maxq The size of the queue to create (this times BUFFER_LENGTH is memory consumed
   * @throws IllegalArgumentException if MAX_SEQUENCE % maxq != 0
   */
  public EdgeBlockQueue(int maxq) throws IllegalArgumentException {
    maxqueue = maxq;
    sequence = 1;
    lastLap = System.currentTimeMillis();
    q = new EdgeBlock[maxq];
    if (MAX_SEQUENCE % maxq != 0) {
      Util.prt(" ***** EdgeBlockQueue: maxq=" + maxq + " does not divide evenly into 2,000,000,000");
      throw new IllegalArgumentException("EdgeBlockQueue construct with maxq=" + maxq
              + " is not divisible into MAX_SEQUENCE=" + MAX_SEQUENCE);
    }
    for (int i = 0; i < maxq; i++) {
      q[i] = new EdgeBlock(); // create the buffers for data blocks
    }
    Util.prta("EBQ: created size=" + maxqueue + " nextseq=" + getNextSequence());
  }

  /**
   * queue in data in raw buffer for (with julian, nblk prepended to data
   *
   * @param rawbuf The raw data buffer
   */
  public synchronized static void queue(byte[] rawbuf) {
    q[sequence % maxqueue].set(rawbuf, sequence);
    if (ebqdbg) {
      dbgprt(q[sequence % maxqueue].toString() + " QRAW");
    }
    sequence = incSequence(sequence);
    if (sequence % maxqueue == 0) {
      long delta = System.currentTimeMillis() - lastLap;
      Util.prta("EBQ: EdgeBlockQueue raw laps maxq=" + maxqueue + " " + (delta / 1000) + " secs " + (maxqueue * 1000 / delta) + " pkt/sec");
      lastLap = System.currentTimeMillis();
    }

  }
  private static final StringBuilder nodesb = new StringBuilder(4);

  /**
   * queue in data given the component julian day, block number and 512 mini-seed bytes
   *
   * @param julian The julian day
   * @param node The node of the edge computer (this + julian gives the filename!).
   * @param nblk Physical block number in file
   * @param seedName The seedname of the block
   * @param indexBlock The indexBlock number of the index block associated with this block
   * @param index The index into the index block for the extent so modified.
   * @param buf 512 bytes of Mini-seed data
   * @param offset Offset in buf at which to start transfer
   * @return The sequence number assigned this block
   */
  public synchronized static int queue(int julian, String node, StringBuilder seedName, int nblk, int indexBlock,
          int index, byte[] buf, int offset) {
    if (offset > 0) {
      julian = -julian;
    }
    int ret = sequence;
    if (buf[0] == 0 && buf[1] == 0 && buf[2] == 0 && buf[3] == 0) {
      Util.prta("EBQ: queue() a block of zeros!  nblk=" + nblk + " indexBlock=" + indexBlock + " ind=" + index);
    }
    if (nblk == 0 && indexBlock < 0) {
      new RuntimeException("Queue with nblk=0 and indexBlock< 0=" + indexBlock + " " + seedName + " " + julian + " " + node).printStackTrace();
    }
    Util.clear(nodesb).append(node);
    Util.rightPad(nodesb, 4);
    q[sequence % maxqueue].set(julian, nodesb, seedName, nblk, indexBlock, index, buf, offset, sequence);
    if (ebqdbg || seedName.indexOf("FORCELOAD") == 0) {
      dbgprt(q[sequence % maxqueue].toString() + " QJUL " + nodesb + "|" + node + "|");
    }
    if (rawsave) {
      try {
        saveFile.write(q[sequence % maxqueue].get(), 0, EdgeBlock.BUFFER_LENGTH);
      } catch (IOException e) {
        Util.prt("IOError writing ebqsavefile" + e.getMessage());
      }
    }
    sequence = incSequence(sequence);
    if (sequence % maxqueue == 0) {
      long delta = System.currentTimeMillis() - lastLap;
      Util.prta("EBQ: EdgeBlockQueue raw laps maxq=" + maxqueue + " " + (delta / 1000) + " secs " + (maxqueue * 1000 / delta) + " pkt/sec");
      lastLap = System.currentTimeMillis();
    }
    return ret;
  }

  /**
   * return the number of blocks left in the queue
   *
   * @param i The sequence number the inquirer is at
   * @return The number of blocks left in the queue
   */
  public static int nleft(int i) {
    int nleft = sequence - i;     // Number of blocks used
    if (nleft < 0) {
      nleft += MAX_SEQUENCE;// Number of blocks adjust for roll in sequences
    }
    nleft = maxqueue - nleft;
    return nleft;
  }

  /**
   * get the EdgeBlock with the given sequence, this is an object in the ring buffer so it must be
   * used before it is over written by what ever is filling the ring buffer. If this is not save use
   * the other getEdgeBlock which copies the data out.
   *
   * @param i the sequence of the desired block
   * @return The EdgeBlock at this sequence, null if not available
   */
  public synchronized static EdgeBlock getEdgeBlock(int i) {
    if (q[i % maxqueue].getSequence() != i) {
      return null;
    } else {
      return q[i % maxqueue];
    }
  }

  /**
   * get the EdgeBlock with the given sequence, return is to user allocated buffer in an edgeblock
   * (it is copied into the user objection
   *
   * @param eb The user object to copy the block into (note it needs to be created or the copy
   * fails)
   * @param i the sequence of the desired block
   * @return The EdgeBlock at this sequence, null if not available
   */
  public synchronized static boolean getEdgeBlock(EdgeBlock eb, int i) {
    if (q[i % maxqueue].getSequence() != i) {
      return false;
    }
    q[i % maxqueue].copyEdgeBlock(eb);      // copy our contents into used
    if (ebqdbg) {
      dbgprt(eb.toString() + " GEB");
    }
    return true;
  }

  /**
   * return whether the block is a continuation block (julian < 0)
   *
   * @param i The index in the queue of the block to check
   * @return true if Julian is < 0 indicating a continuation @throws IllegalArgumentE
   * xception if the value of i is not within the queue range
   */
  public synchronized static boolean isContinuation(int i) throws IllegalArgumentException {
    if (q[i % maxqueue].getSequence() != i) {
      throw new IllegalArgumentException("sequence " + i + " is not in buffer");
    }
    return q[i % maxqueue].isContinuation();

  }

  /**
   * return julian day in stored block with given sequence
   *
   * @param i The desired sequence
   * @throws IllegalArgumentException if the sequence is not in the ring buffer.
   * @return The julian day stored in the block
   */
  public synchronized static int getJulian(int i) throws IllegalArgumentException {
    if (q[i % maxqueue].getSequence() != i) {
      throw new IllegalArgumentException("sequence " + i + " is not in buffer");
    }
    return q[i % maxqueue].getJulian();
  }

  /**
   * return the node represented by this block with given sequence
   *
   * @param i The desired sequence
   * @throws IllegalArgumentException if the sequence is not in the ring buffer.
   * @return the node as a String
   */
  public synchronized static String getNode(int i) throws IllegalArgumentException {
    if (q[i % maxqueue].getSequence() != i) {
      throw new IllegalArgumentException("sequence " + i + " is not in buffer");
    }
    return q[i % maxqueue].getNode();
  }

  /**
   * return the block number in the block with given sequence
   *
   * @param i The desired sequence
   * @throws IllegalArgumentException if the sequence is not in the ring buffer.
   * @return the block number
   */
  public synchronized static int getBlockNumber(int i) throws IllegalArgumentException {
    if (q[i % maxqueue].getSequence() != i) {
      throw new IllegalArgumentException("sequence " + i + " is not in buffer");
    }
    return q[i % maxqueue].getBlock();
  }

  /**
   * return the index block number in the block with given sequence
   *
   * @param i The desired sequence
   * @throws IllegalArgumentException if the sequence is not in the ring buffer.
   * @return the index block number
   */
  public synchronized static int getIndexBlock(int i) throws IllegalArgumentException {
    if (q[i % maxqueue].getSequence() != i) {
      throw new IllegalArgumentException("sequence " + i + " is not in buffer");
    }
    return q[i % maxqueue].getIndexBlock();
  }

  /**
   * return the index block extent index number in the block with given sequence
   *
   * @param i The desired sequence
   * @throws IllegalArgumentException if the sequence is not in the ring buffer.
   * @return the extent index
   */
  public synchronized static int getExtentIndex(int i) throws IllegalArgumentException {
    if (q[i % maxqueue].getSequence() != i) {
      throw new IllegalArgumentException("sequence " + i + " is not in buffer");
    }
    return q[i % maxqueue].getExtentIndex();
  }

  /**
   * return the index block extent index number in the block with given sequence
   *
   * @param i The desired sequence
   * @throws IllegalArgumentException if the sequence is not in the ring buffer.
   * @return the extent index
   */
  public synchronized static int getSequence(int i) throws IllegalArgumentException {
    if (q[i % maxqueue].getSequence() != i) {
      throw new IllegalArgumentException("sequence " + i + " is not in buffer");
    }
    return q[i % maxqueue].getSequence();
  }

  /**
   * return the mini-seed payload of the block of sequence given. This is returned in the users
   * buffer and is a copy of the data block not subject to change by this thread
   *
   * @param i The sequence desired
   * @param buf Buffer of 512 bytes (at least) to get the data
   * @param offset The offset in the byte buffer to place the data.
   * @throws IllegalArgumentException if the sequence is not in the ring buffer.
   */
  public synchronized static void getData(int i, byte[] buf, int offset) throws IllegalArgumentException {
    if (q[i % maxqueue].getSequence() != i) {
      throw new IllegalArgumentException("sequence " + i + " is not in buffer");
    }
    System.arraycopy(q[i % maxqueue].get(), 24, buf, offset, 512);
  }

}
