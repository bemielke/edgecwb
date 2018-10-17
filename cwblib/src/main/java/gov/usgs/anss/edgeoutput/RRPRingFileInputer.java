/*
 *  This software is in the public domain because it contains materials 
 *  that originally came from the United States Geological Survey, 
 *  an agency of the United States Department of Interior. For more 
 *  information, see the official USGS copyright policy at 
 *  http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.edgeoutput;

import gov.usgs.alarm.SendEvent;
import gov.usgs.anss.edge.IllegalSeednameException;
import gov.usgs.anss.edge.RawDisk;
import gov.usgs.anss.edgethread.EdgeThread;
import gov.usgs.anss.seed.MiniSeed;
import gov.usgs.anss.util.Util;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * RRPRingFileInputer.java - This read data from an input ring file where the status is kept in a
 * static ram disk safer RRPState file rather than the first block of the file. These changes was
 * made due to the known problem of to many write cycles on static RAM disks causing failures of the
 * media. Since this works with an RRPRingFile which keeps a memory queue for recently written
 * blocks, this thread checks that memory queue first when looking for the next sequence to send and
 * sends it from there if available. This was to reduce I/O where this is used on an RTS2 or Slate2
 * - or when an OI outputer uses this option within and EdgeMom rather than running external
 * RRPClients.
 *
 * This is a descendant of the RingFileInputer which used the first block method.
 *
 * Created on March 3, 2007, 1:48 PM
 *
 */
public class RRPRingFileInputer extends DataSource {

  private final String filename;
  private final RRPRingFile in;
  static public final int MAX_SEQ = RingFile.MAX_SEQ;
  private final int BIG_BUF_SIZE = 8;     // size of input buffer, for this only 4096 bytes
  private final byte[] bigBuf = new byte[BIG_BUF_SIZE * 512];     // Buffer size for input
  //private final RRPStateFile stateFile;
  byte[] master = new byte[10];     // Place to put master block (1st block of file) 
  private final ByteBuffer mbb;     // A ByteBuffer wrapping master
  private int size;           // size of this file in blocks
  // int next;           // The next block per the index
  private int nextout;    // out tracking of the last thing returned to the user via getNextData()
  boolean newFile;    // If true, the current ring file is empty and waiting for first block
  private long lastMasterRead;
  private final String tag;
  private final EdgeThread par;

  public boolean isNew() {
    return newFile;
  }

  public final int getNext() {
    return in.getNextout();
  }    // next one is from the writer always

  public final void prt(String s) {
    if (par != null) {
      par.prt(s);
    } else {
      EdgeThread.staticprt(s);
    }
  }

  public final void prta(String s) {
    if (par != null) {
      par.prta(s);
    } else {
      EdgeThread.staticprta(s);
    }
  }

  /**
   * a string representing this
   *
   * @return The string
   */
  @Override
  public String toString() {
    return "RFI: " + filename + " nxt(w)=" + getNext() + " nxtout(r)=" + nextout + " szK=" + (size / 1000) + " diff=" + (getNext() - nextout);
  }

  /**
   * return size of this ring
   *
   * @return the size in blocks of the ring
   */
  public int getSize() {
    return size;
  }

  /**
   * set the next sequence to get
   *
   * @param n The next sequence to set
   * @return true if the reset was successful, if false, nexout was reset to next block put in the
   * file!
   * @throws java.io.IOException
   */
  public final boolean setNextout(int n) throws IOException {
    int norg = n;
    int diff = nextout - n;     // Number of packets between where receiver thinks we are and we do
    if(diff > size * 9 / 10) diff -= size;    // It has wrapped, 
    
    // On rare occasions nextout is old in the state file.  Probably processed died or computer died so 
    //state is not up-to-date.  If the proposed sequence is slightly in the future assume it is right.
    if(diff < 0 && diff > -size / 100) {    // It it within 1% of the future?
      prta(tag+" Attempt to set sequence="+n+" in near future.  Assume nextout is not up to date.  Likely crash.  nextout="+nextout);
      nextout = n;
      return true;
    }
    if (in.isSeqActive(n)) {
      nextout = n;
      return true;
    }

    // Try oldest block 
    n = getNext() - size + 1000;
    if (n < 0) {
      n += MAX_SEQ;
    }
    if (in.isSeqActive(n)) {
      nextout = n;
      prta(tag+" Attempt to set nextout OOR next=" + getNext() + " requested=" + norg + " override to maximum " + n);
      SendEvent.pageSMEEvent("RFMaxReset", "Ring file " + filename + " reset to resend max!", "RingFileInput");
      return false;
    }
    // We must have a file that has zeros at the end so we cannot go back so far
    n = getNext() / size * size;
    if (in.isSeqActive(n)) {
      nextout = 1;
      prta(tag+" Attempt to set nextout to n=" + norg + " size=" + size + " wrap=" + in.hasWrapped() + " OOR next=" + getNext() + " override to 1");
      return false;
    }
    n = norg;
    diff = getNext() - n;       // how far back is being requested
    prta(tag+" Atttempt to set failed in all cases - impossible! next=" + getNext() + " n=" + norg + " diff=" + diff);
    SendEvent.pageSMEEvent("RFMaxReset", "Ring file " + filename + " failed to reset to resend max", "RingFileInput");
    nextout = n;
    return false;
  }

  /**
   * Creates a new instance of RingFileInputer
   *
   * @param fname The file name to open
   * @param startBlock if -1, as far back in ring as allowed, if >0, the starting block number
   * @param ring The RRPRingFile to use
   * @param parent The edgethread to use for logging (if null, Util.prt is used)
   * @throws FileNotFoundException if fname does not exist
   */
  public RRPRingFileInputer(String fname, int startBlock, RRPRingFile ring, EdgeThread parent) throws FileNotFoundException, IOException {
    filename = fname;
    in = ring;                // Use the ringfile being written 
    //stateFile = new RRPStateFile(fname+".rf", 0, par);
    par = parent;
    mbb = ByteBuffer.wrap(master);
    size = -1;
    readMasterFile();       // This gets the size
    //readMaster();           // This gets the last index written by the writer.
    int nblks = 0;
    tag = "RRPRFI:[" + fname.trim() + "]";
    if (startBlock > 0) {
      if (setNextout(startBlock)) {
        prta(tag + " start is normal with nexout=" + startBlock + " " + fname);
        nextout = startBlock;       // What does user want
      } else {
        prta(tag + "    ***** RFI: Start block is out of range. nextout=" + nextout + " " + fname);
      }
    } else if (startBlock == -1) {
      if (getNext() < size) {
        nextout = 0;
      } // set to start at beginning
      else {
        nextout = getNext() - size + 1000;   // set to start at max
      }
      if (nextout < 0) {
        nextout += size;         // adjust if happens to wrap
      }
      nblks = size - nextout;
    } else {
      nextout = getNext();
    }
    prta(tag + " startBlock=" + startBlock + " next=" + getNext() + " nextout=" + nextout + " nblks=" + nblks);
  }

  /**
   * Read the last block written to the file by the writer - store in next
   *
   */
  /*private void readMaster() {
    next = stateFile.getLastIndex();
  }*/
  /**
   * Read the first block of the big data file to get the size, the state of writing is not in this
   * block as it has been put in the state file.
   *
   * @throws IOException
   */
  private void readMasterFile() throws IOException {
    //byte [] master = new byte[8];
    int sz = in.size;
    if (size == -1) {
      size = sz;
      prt(tag + "  Size=" + size + " length=" + in.length() + " inblocks=" + (in.length() / 512));
    } else if (size != sz) {
      prt(tag + "  size changed on file is " + sz + " was " + size);
    }
    lastMasterRead = System.currentTimeMillis();

  }

  /**
   * Read in a sequence - this will always return the sequence if it is in the currently active set
   * of sequences.
   *
   * @param seq Sequence to get
   * @param buf 4096 byte block to receive the data
   * @return -1 if the block is not in the current sequence window, length of returned bytes if > 0
   * @throws IOException If one is thrown trying to read the file.
   */
  public int getGapBlock(int seq, byte[] buf) throws IOException {
    if (!in.dsk.getChannel().isOpen()) {
      return -2;
    }

    // Check to make sure the seq is in the current window, if not reset it to oldest
    int diff = getNext() - seq;
    if (diff < 0) {
      diff += MAX_SEQ;
    }
    if (diff > size || diff < 0) {
      prta(tag + " getGapBlock() nextout is out of the window.  Reset to oldest nxt=" + getNext() + " nout=" + seq + " size=" + size);
      return -1;
    }

    // There is data to return, read in the next block
    int nblk = (seq % size) + 1;    // block in file for this offset
    int msize = 0;
    try {
      in.dsk.readBlock(buf, nblk, 512);      // get next block of MiniSeed
      msize = MiniSeed.crackBlockSize(buf);
      if (msize > 512) {
        if (msize > 4096) {
          prt(tag + " Read block size >4096=" + msize + "nblk=" + nblk + " nextout=" + seq + " next=" + getNext());
          msize = 512;
        } else {
          if (size < nblk + msize / 512) {  // Need to read in two pieces!
            prta(tag + " Two piece read at wrap msize=" + msize + " size=" + size + " nblk=" + nblk + " nextout=" + seq);
            in.dsk.readBlock(buf, nblk, (size - nblk) * 512);
            in.dsk.readBlock(bigBuf, 1, (msize - (size - nblk) * 512));
            System.arraycopy(bigBuf, 0, buf, (size - nblk) * 512, (msize - (size - nblk) * 512));
          } else {
            in.dsk.readBlock(buf, nblk, msize);
          }
        }
      }
    } catch (IOException e) {
      Util.IOErrorPrint(e, "RFI: IO error file=" + filename + " next=" + getNext() + " nextout=" + seq + " nblk=" + nblk + " msize=" + msize);
      throw e;
    } catch (IllegalSeednameException e) {
      prta(tag + " The data received is not a miniseed! =" + MiniSeed.toStringRaw(buf));
    }
    return msize;
  }

  /**
   * Get the next data block, this returns -1 if there is no next block so caller can take other
   * actions
   *
   * @param buf buffer to receive the data bloc,
   * @return -1 if no data is available, -2 if file is closed, else the length of the MiniSEED block
   * returned.
   * @throws IOException
   */
  @Override
  public int getNextData(byte[] buf) throws IOException {
    if (!in.dsk.getChannel().isOpen()) {
      return -2;
    }
    //if(System.currentTimeMillis() - lastMasterRead > 200) readMaster(); // Always check so lapping is known
    if (getNext() == nextout) {
      return -1;     // nothing to return
    }
    // Check to make sure the nextout is in the current window, if not reset it to oldest
    int diff = getNext() - nextout;
    if (diff < 0) {
      diff += MAX_SEQ;
    }
    if(diff == -1 && getNext() == 0 && nextout == 1) {
      prta(tag + "getNextData() is on first block of new file - do nothing!");
      return -1;
    }
    
    // Is it out of the window?
    if (diff > size || diff < 0) {
      prta(tag + " getNextData() nextout is out of the window.  Reset to oldest nxt=" + getNext() + " nout=" + nextout + " size=" + size);
      nextout = getNext() - size + 100;
      if (nextout < 0) {
        nextout += MAX_SEQ;
      }
    }

    // There is data to return, read in the next block
    int nblk = (nextout % size) + 1;    // block in file for this offset
    int msize = 0;
    try {
      in.dsk.readBlock(buf, nblk, 512);      // get next block of MiniSeed
      msize = MiniSeed.crackBlockSize(buf);
      //prta(tag+" read single block ms="+msize+" nblk="+nblk+" nextout="+nextout+" "+MiniSeed.toStringRaw(buf));//DEBUG: every block
      if (msize > 512) {
        if (msize > 4096) {
          prt(tag + " Read block size >4096=" + msize + "nblk=" + nblk + " nextout=" + nextout + " next=" + getNext());
          msize = 512;
        } else {
          if (size < nblk + msize / 512) {  // Need to read in two pieces!
            prta(tag + " Two piece read at wrap msize=" + msize + " size=" + size + " nblk=" + nblk + " nextout=" + nextout);
            in.dsk.readBlock(buf, nblk, (size - nblk) * 512);
            in.dsk.readBlock(bigBuf, 1, (msize - (size - nblk) * 512));
            System.arraycopy(bigBuf, 0, buf, (size - nblk) * 512, (msize - (size - nblk) * 512));
          } else {
            in.dsk.readBlock(buf, nblk, msize);
          }
        }
      }
      if (msize < 512) {
        msize = 512;      // force it to always advance!
      }
      nextout += msize / 512;
      if (nextout >= MAX_SEQ) {
        prta(tag + "  sequence roll over to zero " + nextout + " file=" + filename);
        nextout -= MAX_SEQ;
      }
    } catch (IOException e) {
      Util.IOErrorPrint(e, "RFI: IO error file=" + filename + " next=" + getNext() + " nextout=" + nextout + " nblk=" + nblk + " msize=" + msize);
      if (msize < 512) {
        msize = 512;
      }
      nextout += msize / 512;
      for (int i = 0; i < 512; i++) {
        buf[i] = 0;    // return a zero block
      }
      throw e;
    } catch (IllegalSeednameException e) {
      prta(tag + " The data received is not a miniseed! =" + MiniSeed.toStringRaw(buf));
      nextout++;
    }
    return msize;
  }

  /**
   * Get the next data block, this returns -1 if there is no next block so caller can take other
   * actions. It checks the RRPRingFile memory queue for the block first so most times the file is
   * not actually read for current data
   *
   * @param buf buffer to receive the data bloc,
   * @param rf If not null, this is the RRPRingFile that is writing the ring file (called from
   * within the same EdgeMom)
   * @return -1 if no data is available, -2 if file is closed, else the length of the MiniSEED block
   * returned.
   * @throws IOException
   */
  public int getNextData(byte[] buf, RRPRingFile rf) throws IOException {
    // If there is a ring file, get next from it (its more up to date than the state file), and see if the block is in the memory queue
    if (rf != null) {
      int next = rf.getNextout();
      byte[] b = rf.getQueueForSeq(nextout);
      if (b != null) {       // Is it in the memory queue
        System.arraycopy(b, 0, buf, 0, 512);
        nextout = (nextout + 1) % MAX_SEQ;
        //prta(tag+" read data from queue nextout="+nextout+" next="+getNext());
        return 512;
      }
    }
    // nextout is not in the memory queue, get the block from disk or return -1 or -2 if its not there yet
    return getNextData(buf);
  }

  public void close() {
    in.close();
  }

  /**
   * return the last block written into file by the RRPRingFile
   *
   * @return The last block written
   */
  public int getNextout() {
    return nextout;
  }

  public int getLastSeq() {
    return in.getNextout();
  }

  public boolean wouldBlock() {
    return nextout == in.getNextout();
  }

  public static void main(String[] args) {
    Util.setModeGMT();
    Util.setNoInteractive(true);
    String logname = Util.fs + "data2" + Util.fs + "LOG" + Util.fs + "dmcdump.log";
    String ring = Util.fs + "data2" + Util.fs + "IRIS_DMC.ring";
    String station = "USAAM";
    boolean resetLast = false;
    boolean dumpRing = false;
    int newlast = -1;
    int startBlock = -1;
    boolean dumpLast = false;
    for (int i = 0; i < args.length; i++) {
      switch (args[i]) {
        case "-ring":
          ring = args[i + 1];
          break;
        case "-dumplast":
          dumpLast = true;
          break;
        case "-resetlast":
          newlast = Integer.parseInt(args[i + 1]);
          resetLast = true;
          i++;
          break;
        case "-dumpring":
          dumpRing = true;
          i++;
          break;
        case "-start":
          startBlock = Integer.parseInt(args[i + 1]);
          i++;
          break;
      }
    }
    long lastday = System.currentTimeMillis();
    try {
      RingFileInputer rfi = new RingFileInputer(ring, startBlock);
      Util.prt("Ring size=" + rfi.getSize() + " lastseq=" + rfi.getLastSeq() + " nextout=" + rfi.getNextout());
      if (startBlock == 0) {
        startBlock = rfi.getNextout() - rfi.getSize() + 2000;
        Util.prt("Set start to max at " + startBlock);
      }
      boolean done = false;
      byte[] buf = new byte[4096];
      ByteBuffer bb = ByteBuffer.wrap(buf);
      RawDisk last = new RawDisk(ring + ".last", "rw");
      last.readBlock(buf, 0, 4);
      bb.position(0);
      int lastBlk = bb.getInt();
      if (dumpLast) {
        Util.prt("lastblock =" + lastBlk);
        Util.exit(0);
      }
      if (resetLast) {
        bb.position(0);
        bb.putInt(newlast);
        last.writeBlock(0, buf, 0, 4);
        Util.exit(0);
      }
      if (dumpRing) {
        while (!done) {
          if (lastday != System.currentTimeMillis() / 86400000L) {
            System.out.println("Open new log file " + logname + EdgeThread.EdgeThreadDigit());
            lastday = System.currentTimeMillis() / 86400000L;
            Util.setTestStream(logname + EdgeThread.EdgeThreadDigit());

          }
          int size = rfi.getNextData(buf);
          MiniSeed ms = new MiniSeed(buf);
          String seedname = ms.getSeedNameString();
          if (seedname.contains("USAAM") || seedname.contains("USDUG") || seedname.contains("USDGMT")
                  || seedname.contains("USNEW")) {
            Util.prta(rfi.getNextout() + " ms=" + ms.toString().substring(0, 64));
          }
        }
      }
    } catch (IllegalSeednameException e) {
      Util.prt("Got illegal seedname e=" + e.getMessage());
    } catch (IOException e) {
      Util.prt("Got IOExceptio nin main e=" + e);
      e.printStackTrace();
    }
  }
}
