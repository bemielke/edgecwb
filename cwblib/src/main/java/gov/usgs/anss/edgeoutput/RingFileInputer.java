/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
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
 * Read data ring data file. This uses a RawDisk to a data file to read in the next data. The user
 * must be tracking the last used block as this class does not persistently store it.
 *
 * @author davidketchum
 */
public class RingFileInputer extends DataSource {

  private final String filename;
  private final RawDisk in;
  static public final int MAX_SEQ = 2000000000;
  private final int BIG_BUF_SIZE = 128;     // size of input buffer
  private final byte[] bigBuf;     // Buffer size for input
  private int offsetBigBuf;   // Last point processed from big buf
  private int nbytesBigBuf;   // number of bytes last read into big buf

  private final byte[] master;     // Place to put master block (1st block of file) 
  private final ByteBuffer mbb;     // A ByteBuffer wrapping master
  private int size;           // size of this file in blocks
  private int next;           // The next block per the index
  private int nextout;
  private long lastMasterRead;

  /**
   * a string representing this
   *
   * @return The string
   */
  @Override
  public String toString() {
    return "RFI: " + filename + " nxt(w)=" + next + " nxtout(r)=" + nextout + " szK=" + (size / 1000) + " diff=" + (next - nextout);
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
   * @return true if the reset was successful, if false, nextout was reset to next block put in the
   * file!
   * @throws java.io.IOException
   */
  public boolean setNextout(int n) throws IOException {
    readMaster();
    int diff = next - n;       // how far back is being requested
    if (diff < -MAX_SEQ / 2) {
      diff += MAX_SEQ;
    }
    if (diff < 0) {
      Util.prta("RFI: got small negative treat it like it is zero diff=" + diff);
      diff = 0;
    }
    if (diff < 0 || diff > size) {
      Util.prta("Attempt to set nextout OOR next=" + next + " requested=" + n + " override to maximum " + (next - size + 1000));
      SendEvent.pageSMEEvent("RFMaxReset", "Ring file " + filename + " reset to resend max!", "RingFileInput");
      nextout = next - size + 1000;
      if (nextout < 0) {
        nextout += MAX_SEQ;
      }
      return false;
    }
    nextout = n;
    if (nextout >= MAX_SEQ) {
      nextout -= MAX_SEQ;
    }
    return true;
  }

  /**
   * Creates a new instance of RingFileInputer
   *
   * @param fname The file name to open
   * @param startBlock if -1, as far back in ring as allowed, if >0, the starting block number
   * @throws FileNotFoundException if fname does not exist
   */
  public RingFileInputer(String fname, int startBlock) throws FileNotFoundException, IOException {
    filename = fname;
    in = new RawDisk(fname, "r");
    master = new byte[512];
    bigBuf = new byte[BIG_BUF_SIZE * 512];
    mbb = ByteBuffer.wrap(master);
    size = -1;
    readMaster();

    if (startBlock > 0) {
      int nblks = next - startBlock;
      if (nblks < 0) {
        nblks = nblks + MAX_SEQ;
      }
      if (nblks >= size) {         // invalidate all blocks in this circumstance.
        if (next < size) {         // it is likely the file has just been reset, do not send old data
          nextout = 0;
          Util.prt("    ***** RFI: Start block is out of range and file appears to be new.  Start at nextout=0 " + fname);
        } else {
          nextout = next - size + 1000;
          if (nextout < 0) {
            nextout += MAX_SEQ;
          }
          Util.prt("   ***** RFI: Start block is out or range - set oldest (deliver as much as possible)="
                  + startBlock + " next=" + next + " size=" + size + " file=" + fname);
        }
      } else {
        Util.prt("RFI: start is normal with nexout=" + startBlock + " " + fname);
        nextout = startBlock;       // What does user want
      }
    } else if (startBlock == -1) {
      if (next < size) {
        nextout = 0;
      } else {
        nextout = next - size + 1000;
      }
      if (nextout < 0) {
        nextout += size;
      }
    } else {
      nextout = next;
    }
  }

  private void readMaster() throws IOException {
    try {
      in.readBlock(master, 0, 8);
      mbb.position(0);
      next = mbb.getInt();
      int sz = mbb.getInt();
      if (size == -1) {
        size = sz;
        Util.prt("Size=" + size + " length=" + in.length() + " inblocks=" + (in.length() / 512));
      } else if (size != sz) {
        Util.prt("RFI: size changed on file is " + sz + " was " + size);
      }
      lastMasterRead = System.currentTimeMillis();
    } catch (IOException e) {
      Util.prta("RFI : IOException reading master block int RingFileInput file=" + filename);
      Util.IOErrorPrint(e, "RFI : Reading master block in RingFileInput file=" + filename);
      throw e;
    }
  }

  @Override
  public int getNextData(byte[] buf) throws IOException {
    return getNextData(buf, false);
  }
  /** Read the next block from this ring file.
   * 
   * @param buf Buffer to put the data block
   * @param noBlock If true, then do not block on read but return -1 instead
   * @return Number of bytes read, or -1 if noBlock is set and no data is available.
   * @throws IOException 
   */
  public int getNextData(byte [] buf, boolean noBlock) throws IOException {
    if (!in.getChannel().isOpen()) {
      return -1;
    }
    if (System.currentTimeMillis() - lastMasterRead > 200) {
      readMaster(); // Always check so lapping is known
    }    
    //Util.prta("Get next() has nextout="+nextout+" next="+next+" "+filename);
    if (next <= 0) {
      Util.prta("Next <= 0 set it to nextout !!!!");
      next = nextout;
    }
    while (next == nextout) {     // nothing to return
      if (System.currentTimeMillis() - lastMasterRead > 200) {
        readMaster();
        if(noBlock && next == nextout) return -1;
      } else {
        try {
          Thread.sleep(50);
        } catch (InterruptedException e) {
        }
      }
    }

    // Check to make sure the nextout is in the current window, if not reset it to oldest
    int diff = next - nextout;
    if (diff < 0) {
      diff += MAX_SEQ;
    }
    if (diff > size || diff < 0) {
      Util.prta("RFI: nextout is out of the window.  Reset to oldest nxt=" + next + " nout=" + nextout + " size=" + size);
      nextout = next - size + 100;
      if (nextout < 0) {
        nextout += MAX_SEQ;
      }
    }

    // There is data to return, read in the next block
    int nblk = (nextout % size) + 1;    // block in file for this offset
    int msize = 0;
    try {
      in.readBlock(buf, nblk, 512);      // get next block of MiniSeed
      msize = MiniSeed.crackBlockSize(buf);
      if (msize <= 0) {
        msize = 512;
      }
      if (msize > 512) {
        if (msize > 4096) {
          Util.prt("Read block size >4096=" + msize + "nblk=" + nblk + " nextout=" + nextout + " next=" + next);
          msize = 512;
        } else {
          if (size < nblk + msize / 512) {  // Need to read in two pieces!
            Util.prta("Two piece read at wrap msize=" + msize + " size=" + size + " nblk=" + nblk + " nextout=" + nextout);
            in.readBlock(buf, nblk, (size - nblk) * 512);
            in.readBlock(bigBuf, 1, (msize - (size - nblk) * 512));
            System.arraycopy(bigBuf, 0, buf, (size - nblk) * 512, (msize - (size - nblk) * 512));
          } else {
            in.readBlock(buf, nblk, msize);
          }
        }
      }
      if (msize < 512) {
        msize = 512;      // force it to always advance!
      }
      nextout += msize / 512;
      if (nextout >= MAX_SEQ) {
        Util.prta("RFI : sequence roll over to zero " + nextout + " file=" + filename);
        nextout -= MAX_SEQ;
      }
    } catch (IOException e) {
      Util.IOErrorPrint(e, "RFI: IO error file=" + filename + " next=" + next + " nextout=" + nextout + " nblk=" + nblk + " msize=" + msize);
      if (msize < 512) {
        msize = 512;
      }
      nextout += msize / 512;
      for (int i = 0; i < 512; i++) {
        buf[i] = 0;    // return a zero block
      }
      throw e;
    } catch (IllegalSeednameException e) {
      Util.prta("The data received is not a miniseed! =" + MiniSeed.toStringRaw(buf));
      nextout++;
    }
    return msize;
  }

  public void close() {
    try {
      in.close();
    } catch (IOException e) {
    }
  }

  public int getNextout() {
    return nextout;
  }

  public int getLastSeq() {
    if (System.currentTimeMillis() - lastMasterRead > 200) {
      try {
        readMaster(); // Always check so lapping is known
      }
      catch(IOException e) {
        e.printStackTrace();
      }
    }  
    return next;
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
        startBlock = rfi.nextout - rfi.size + 2000;
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
        System.exit(0);
      }
      if (resetLast) {
        bb.position(0);
        bb.putInt(newlast);
        last.writeBlock(0, buf, 0, 4);
        System.exit(0);
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
            Util.prta(rfi.nextout + " ms=" + ms.toString().substring(0, 64));
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
