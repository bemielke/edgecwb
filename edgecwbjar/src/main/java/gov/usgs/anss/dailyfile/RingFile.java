/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.dailyfile;

import gov.usgs.anss.edge.RawDisk;
import gov.usgs.anss.util.Util;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Read data from a ringfile created by the RRP or OutputInfrastructure classes.
 *
 * <br>
 * Changes :<br><br>
 * 28-May-2007 Initial version
 * <br><br>
 * This code allows a user's application to get data from a Ring Buffer created
 * by the RRP or ANSS OutputInfrastructure. It also creates a companion to the
 * ring file where status information about the using process's progress in
 * reading the ring.
 *
 * <br><br>
 * This object's run method insures the control file is updated at least every
 * 20 seconds. When way behind, the control file update logic in getNext() is
 * not exercised since the criteria for writing it out is to be caught up.
 * <br><br>
 * Overall concept - Each block in a ring file has a sequence number which can
 * be computed from its position in the ring file and the next sequence number
 * to be put in the file and the ring size. These sequence numbers run from 0 -
 * 1,999,999,999. The position of a sequence in the ring file is (seq %
 * ringsize)+1. The first block is reserved for the status information in the
 * file. The ringstruct below represents this ondisk status of the ringwriter
 * and of the user of this process (nextout and maxsize).
 * <br>
 *
 * @author davidketchum
 */
public final class RingFile extends Thread {

  private String filename;
  private RawDisk ringin;
  private RawDisk ctl;          // control raw disk
  private String ctlExtension;
  private byte[] ctlbuf;
  private ByteBuffer ctlbb;
  private int BIG_BUF_SIZE = 128;     // size of input buffer
  private byte[] bigBuf;     // Buffer size for input
  private int offsetBigBuf;   // Last point processed from big buf
  private int nbytesBigBuf;   // number of bytes last read into big buf

  private byte[] master;     // Place to put master block (1st block of ring file file) 
  private ByteBuffer mbb;     // A ByteBuffer wrapping master block
  private int size;           // size of this file in blocks
  private int next;           // The next block per the index
  private int nextout;
  private long lastMasterRead;
  private long lastControlWrite;
  private int nupdateCtl;
  private long lastUpdate;
  private boolean terminate;
  private boolean dbg = false;
  private final ShutdownRingFileInputer shutdown;

  /**
   * a string representing this RingFile object.
   *
   * @return The string
   */
  @Override
  public String toString() {
    return "RFI: " + filename + " next=" + next + " nextout=" + nextout + " size=" + size + " ctlUpd=" + nupdateCtl;
  }

  /**
   * return size of this ring in blocks. The file is one block larger for the
   * status block
   *
   * @return the size in data blocks of the ring
   */
  public int getSize() {
    return size;
  }

  /**
   * set the next sequence to get from this ring file. The value is validated to
   * be in the ring file, if it is not, the exitout is set to the current
   * location. Often when detecting this the user would probably recall this
   * routine with getLastSeq()-getSize()+1000. Please check for a negative and
   * add 2,000,000,000 if found.
   *
   * @param n The next sequence to get from this ring buffer.
   * @return true if the reset was successful, if false, nexout was reset to
   * next block put in the file!
   */
  public boolean setNextout(int n) {
    readMaster();
    int diff = next - n;       // how far back is being requested
    if (diff < 0) {
      diff += size;
    }
    if (diff < 0 || diff > size) {
      System.out.println(Util.asctime() + " " + "Attempt to set nextout OOR next=" + next + " requested=" + n + " override to minimum");
      nextout = next;
      return false;
    }
    nextout = n;
    return true;
  }

  /**
   * Creates a new instance of RingFileInputer - The initial starting block is
   * obtained from the control file if it is found. If the control file is new,
   * the oldest block +1000 is set as the starting block. The control file is
   * used to store the last data the user obtained from getNext().
   *
   * @param fname The file name to open
   * @param ctlext String extension to add to the ring file name to for the
   * control file name.
   * @throws FileNotFoundException - if the ring file does not exist or has
   * permissions problems.
   * @throws IOException This should not be thrown, but could be if a file
   * length() method throws it.
   */
  public RingFile(String fname, String ctlext) throws FileNotFoundException, IOException {
    filename = fname;
    ctlExtension = ctlext;
    if (ctlExtension == null) {
      ctlExtension = ".ctl";
    }
    if (ctlExtension.length() < 1) {
      ctlExtension = ".ctl";
    }
    ringin = new RawDisk(fname, "r");
    master = new byte[512];
    bigBuf = new byte[BIG_BUF_SIZE * 512];
    mbb = ByteBuffer.wrap(master);
    size = -1;
    readMaster();

    // set up and read the control file 
    ctlbuf = new byte[512];
    ctlbb = ByteBuffer.wrap(ctlbuf);
    ctl = new RawDisk(filename.trim() + ctlExtension, "rw");
    int startBlock = -2;
    if (ctl.length() == 0) {      // this file is new, we do not know where to start
      startBlock = -1;           // no control file, set to start at best guess
      System.out.println("RFI: Ctrl file is new, starting block is unknown");
    } else {
      ctl.readBlock(ctlbuf, 0, 4);
      ctlbb.position(0);
      int lastBlock = ctlbb.getInt();
      System.out.println("RFI: Starting up cmd line startblock=" + startBlock + " ctl file start=" + lastBlock);
      if (lastBlock > 0 && lastBlock < 2000000000) {
        startBlock = lastBlock;
      }
    }
    lastUpdate = System.currentTimeMillis();

    // Decide what to do based on control block start block
    if (startBlock > 0) {
      int nblks = next - startBlock;
      if (nblks < 0 && nblks > -100000) {
        nblks += 2000000000;
      }
      if (nblks > size) {      // if this is not in range,  start at oldest
        nextout = next - size + size / 100;
        if (nextout < 0) {
          nextout += 2000000000;
        }
        System.out.println("RFI: Start block is out of range -oldest=" + startBlock + " next=" + next + " size=" + size);
      } else {
        nextout = startBlock;       // The control assigned blcok
        System.out.println("RFI: start block ok=" + nextout);
        // If needed, wait for input ring to indicate this block is in memory.  This only happens if ring regresses as part of start up.
        if (nblks < 0) {
          while (nblks < 0) {
            try {
              Thread.sleep(100);
            } catch (InterruptedException expected) {
            }
            readMaster();
            nblks = next - startBlock;
          }
        }
      }
    } else if (startBlock == -1) {         // Start block not in control, set nextout to oldest
      System.out.println("RFI: Starting block is -1, next=" + next + " size=" + size);
      if (next < size) {
        nextout = 1;
      } else {
        nextout = next - size + size / 100;
      }
      if (nextout < 0) {
        nextout += size;
      }
      System.out.println("RFI: Initial starting block (NEW) = " + nextout);
    } else {
      System.out.println("RFI: Very unusual startup with bad startBlock=" + startBlock);
      nextout = next;
    }

    // This shutdown handler insures a last control file update occurs
    shutdown = new ShutdownRingFileInputer();
    Runtime.getRuntime().addShutdownHook(shutdown);
  }

  @Override
  public void run() {
    int loop = 0;
    while (!terminate) {
      try {
        sleep(2000);
      } catch (InterruptedException expected) {
      }
      if (System.currentTimeMillis() - lastUpdate > 20000) {
        try {
          if (dbg) {
            System.out.println("Update nextout (ctl) to " + nextout + " next=" + next);
          }
          updateControlFile(nextout);
        } catch (IOException e) {
          System.out.println("RFI: IOException updating control=" + e.getMessage());
        }
        lastUpdate = System.currentTimeMillis();
      }
    }
    try {
      updateControlFile(nextout);
      close();
    } catch (IOException e) {
      System.out.println("RFI: IOException updating control or closing=" + e.getMessage());
    }
    System.err.println("RFI: RingFileInputer terminated");
  }

  /**
   * update the control file and set last seq processed. Normally used
   * internally, but user might call this with a sequence (especially
   * getNextout()) if more frequent updates were desired.
   *
   * @param seq The last sequence confirmed processed
   * @throws IOException if writing to the control file fails (likely
   * permissions problem?)
   */
  public void updateControlFile(int seq) throws IOException {
    if (System.currentTimeMillis() - lastControlWrite < 1000) {
      return;
    }
    lastControlWrite = System.currentTimeMillis();
    ctlbb.position(0);
    ctlbb.putInt(seq);
    ctl.writeBlock(0, ctlbuf, 0, 8);
    nupdateCtl++;
  }

  /**
   * read in the master block data for this ring file.
   */
  private void readMaster() {
    try {
      ringin.readBlock(master, 0, 8);
      mbb.position(0);
      next = mbb.getInt();
      int sz = mbb.getInt();
      if (dbg) {
        System.out.println("RFI: read next=" + next + " nextout=" + nextout);
      }
      if (size == -1) {
        size = sz;
        System.out.println("Size=" + size + " length=" + ringin.length() + " inblocks=" + (ringin.length() / 512));
      } else if (size != sz) {
        System.out.println("RFI: size changed on file is " + sz + " was " + size);
      }
      lastMasterRead = System.currentTimeMillis();
    } catch (IOException e) {
      System.out.println(Util.asctime() + " " + "RFI : IOException reading master block int RingFileInput file=" + filename);
      Util.IOErrorPrint(e, "RFI : Reading master block in RingFileInput file=" + filename);
    }
  }

  public String dumpRingHeader() {
    readMaster();
    return "next=" + next + " size=" + size + " nextout=" + nextout;
  }

  /**
   * read the next block from this ring. This will block if the data are not yet
   * available. If the data returned is not mini-seed, the next block to read
   * will be one greater than this one. If the next block desired has fallen out
   * of the ring buffer window, the block returned is the oldest data in the
   * ring + a little safety margin. This condition means the user is not
   * processing data as fast as it is going into the ring.
   *
   * @param buf A buffer at least as long as the biggest expected miniseed block
   * (4096 is a good value)
   * @return The length of the returned miniseed in bytes from its blockette
   * 1000. If no blockette 100, return 0.
   * @throws IOException Should not be possible, but could be thrown by reading
   * the file or control file.
   */
  public int getNext(byte[] buf) throws IOException {
    while (next == nextout) {     // nothing to return
      if (terminate) {
        return -1;
      }
      updateControlFile(nextout);
      if (!ringin.getChannel().isOpen()) {
        return -1;
      }
      if (System.currentTimeMillis() - lastMasterRead > 500) {
        readMaster();
      } else {
        try {
          Thread.sleep(500);
        } catch (InterruptedException e) {
        }
      }
    }
    // Check to make sure the nextout is in the current window, if not reset it to oldest
    int diff = next - nextout;
    /* check to see we are in the window, be tolerant of restarts where diff < 0 because ring pointer went backwards */
    while (diff < 0 && diff > -10000) {
      try {
        Thread.sleep(100);
      } catch (InterruptedException e) {
      }
      readMaster();
      diff = next - nextout;
    }

    if (diff < 0) {
      diff += 2000000000;
    }
    if (diff >= size) {
      System.out.println("RFI: nextout is out of the window.  Reset to oldest nxt=" + next + " nout=" + nextout + " size=" + size);
      nextout = next - size + size / 100;
      if (nextout < 0) {
        nextout += 2000000000;
      }
      updateControlFile(nextout);
    }

    // There is data to return, read in the next block
    int nblk = (nextout % size) + 1;    // block in file for this offset
    int msize = 0;
    try {
      ringin.readBlock(buf, nblk, 512);      // get next block of MiniSeed
      msize = crackBlockSize(buf);
      if (msize > 512) {
        if (msize > 4096) {
          System.out.println("RFI: Read block size >4096=" + msize + "nblk=" + nblk + " nextout=" + nextout + " next=" + next);
          msize = 512;
        } else {
          if (size < nblk + msize / 512) {  // Need to read in two pieces!
            System.out.println(Util.asctime() + " " + "RFI: Two piece read at wrap msize=" + msize + " size=" + size + " nblk=" + nblk + " nextout=" + nextout);
            ringin.readBlock(buf, nblk, (size - nblk) * 512);
            ringin.readBlock(bigBuf, 1, (msize - (size - nblk) * 512));
            System.arraycopy(bigBuf, 0, buf, (size - nblk) * 512, (msize - (size - nblk) * 512));
          } else {
            ringin.readBlock(buf, nblk, msize);
          }
        }
      }

      nextout += msize / 512;
      if (msize < 512) {
        nextout++;        // we must move forward!
      }
      if (nextout >= 2000000000) {
        System.out.println(Util.asctime() + " " + "RFI : sequence roll over to zero " + nextout + " file=" + filename);
        nextout -= 2000000000;
      }
    } catch (IOException e) {
      Util.IOErrorPrint(e, "RFI: IO error file=" + filename + " next=" + next + " nextout=" + nextout + " nblk=" + nblk + " msize=" + msize);
      throw e;
    }
    if (nextout % 100 == 0) {
      updateControlFile(nextout);
    }
    return msize;
  }

  /**
   * close up this connection to a ring file. The prefered way for a user to
   * stop this object is a call to terminate()
   */
  private void close() {
    try {
      ringin.close();
      ctl.close();
    } catch (IOException e) {
    }
  }

  /**
   * return the last seq number in this file sequence number - the block we are
   * chasing or alternatively, the next block sequence number the writer of the
   * ring file will put in the file
   *
   * @return The sequence number of the newest block in this ring file.
   */
  public int getLastSeq() {
    return next;
  }

  /**
   * Return the sequence number the next call to getNext() will return. This is
   * the current read cursor for this object.
   *
   * @return The sequence number of the next block that should be return by a
   * call to getNext()
   */
  public int getNextout() {
    return nextout;
  }

  /**
   * Convenience method to extract the blockette 1000 size from the miniseed.
   *
   * @param buf The buffer containing mini-seed block.
   * @return The length from the blockette 1000, 0 if no blockette 1000 is found
   */
  public static int crackBlockSize(byte[] buf) {
    ByteBuffer bb = ByteBuffer.wrap(buf);
    bb.position(39);          // position # of blockettes that follow
    int nblks = bb.get();       // get it
    bb.position(46);         // position offset to first blockette
    int offset = bb.getShort();
    for (int i = 0; i < nblks; i++) {
      bb.position(offset);
      int type = bb.getShort();
      int oldoffset = offset;
      offset = bb.getShort();
      if (type == 1000) {
        bb.position(oldoffset + 6);
        return 1 << bb.get();
      }
    }
    return 0;

  }

  /**
   * This will cause the ring file to terminate gracefully including updating
   * the control file.
   */
  public void terminate() {
    terminate = true;
    interrupt();
  }

  /**
   * This class is called when the system is shutting down a java process. It is
   * registered with Runtime.getRuntime().addShutdownHook()
   */
  public class ShutdownRingFileInputer extends Thread {

    public ShutdownRingFileInputer() {
    }

    @Override
    public void run() {
      System.out.println("RFI: Shutdown executed");
      terminate = true;
      interrupt();
    }
  }

  /**
   * this is example of how to use a RingFileInputer. It creates a side control
   * file with the filename of the ring+".ctl" and keeps the last block
   * processed there. For the debugging phase it supports the dbgbin convention
   * where the first 4 bytes of the miniseed are replaced by the sequence number
   * in the sender. This allows us to check that the data sequence and expected
   * sequences agree.
   * <br>
   * <br> args Description
   * <br>-f filename Open ring file of this name
   * <br>-b nnn Set required blocksize to nnn.
   * <br>-dbgbin Turn on binary debug. This overwrites the 1st 4 bytes of
   * miniseed with seq for debug testing.
   *
   * @param args command line args
   */
  public static void main(String[] args) {
    String filename = "ringbuffer";
    byte[] ctlbuf = new byte[512];
    ByteBuffer ctlbb = ByteBuffer.wrap(ctlbuf);
    byte[] buf = new byte[4096];
    ByteBuffer bb = ByteBuffer.wrap(buf);
    int size;
    boolean dbgbin = false;
    boolean dump = false;
    boolean setCurrent = false;
    boolean hdrdump = false;
    int blksize = 0;
    if (args.length == 0) {
      System.out.println("Usage java -cp RRPServer.jar gov.usgs.anss.net.RingFile -f filenam [-dump][-current][-sblk bbbbb]");
      System.out.println(" -dump    dump miniseed header as read");
      System.out.println(" -current dump only from current head of data instead of last remembered");
      System.out.println(" -sblk    set the current control block number in data file to bbbbbb (very dangerous");
      System.out.println(" -dbgbin  Set to use debug mode on blocks (not normally useful");
      System.exit(0);
    }
    for (int i = 0; i < args.length; i++) {
      if (args[i].equals("-f")) {
        filename = args[i + 1];
      }
      if (args[i].equals("-dump")) {
        dump = true;
      }
      if (args[i].equals("-current")) {
        setCurrent = true;
      }
      if (args[i].equals("-hdrdump")) {
        int sleep = Integer.parseInt(args[i + 1]);
        try {
          RawDisk rw = new RawDisk(filename, "r");
          for (;;) {
            try {
              rw.readBlock(ctlbuf, 0, 8);
              ctlbb.position(0);
              int next = ctlbb.getInt();
              System.out.println("next = " + next);
              try {
                Thread.sleep(sleep);
              } catch (InterruptedException expected) {
              }
            } catch (IOException e) {
              System.out.println("Error reading ring file e=" + e);
              System.exit(0);
            }
          }
        } catch (FileNotFoundException e) {
          System.out.println("Did not find filename=" + filename + " e=" + e);
          System.exit(1);
        }
      }
      if (args[i].equals("-dbgbin")) {
        dbgbin = true;
      }
      if (args[i].equals("-b")) {
        blksize = Integer.parseInt(args[i + 1]);
      }
      if (args[i].equals("-sblk")) {
        int startBlock = Integer.parseInt(args[i + 1]);
        try {
          System.out.println("Set " + filename + "to next block " + startBlock);
          try (RawDisk rw = new RawDisk(filename, "rw")) {
            rw.readBlock(ctlbuf, 0, 8);
            ctlbb.position(0);
            int was = ctlbb.getInt();
            System.out.println("Block was " + was);
            ctlbb.position(0);
            ctlbb.putInt(startBlock);
            rw.writeBlock(0, ctlbuf, 0, 512);
          }
        } catch (IOException e) {
          System.out.println("IOException setting block in control file=" + e.getMessage());

        }
        System.exit(0);
      }
    }
    try {

      System.out.println("Opening file=" + filename + " dump=" + dump);
      RingFile in = new RingFile(filename, ".ctl");
      System.out.println("Startup : next=" + in.getLastSeq() + " nextout=" + in.getNextout());
      if (setCurrent) {
        in.setNextout(in.getLastSeq() - 10);
      }
      int expected = in.getNextout();
      for (;;) {
        // we want our expected seq to match nextout (the next seq to read!)
        if (in.getNextout() != expected) {
          System.out.println("Not getting expected block.  expected=" + expected + " nextout=" + in.getNextout() + " last=" + in.getLastSeq());
          expected = in.getNextout();
        }
        for (int i = 0; i < 512; i++) {
          buf[i] = -1;
        }
        size = in.getNext(buf);
        if (blksize > 0 && size != blksize) {
          System.out.println("Got block of different size=" + size + " at seq=" + expected);
        }
        if (dump) {
          try {
            byte[] b = new byte[12];
            bb.position(0);
            bb.get(b, 0, 8);
            String seq = new String(b, 0, 8);
            bb.get(b, 0, 12);
            String slcn = new String(b, 0, 12);
            short yr = bb.getShort();
            short doy = bb.getShort();
            byte hr = bb.get();
            byte mn = bb.get();
            byte sec = bb.get();
            bb.get();
            short hsec = bb.getShort();
            short nsamp = bb.getShort();
            short rateFact = bb.getShort();
            short rateMult = bb.getShort();
            System.out.println(expected + " sq=" + seq + " " + slcn.substring(10, 12) + slcn.substring(0, 5) + slcn.substring(7, 10) + slcn.substring(5, 7) + " ns=" + nsamp
                    + " " + yr + "," + doy + ":" + hr + ":" + mn + ":" + sec + "." + hsec + " rate=" + rateFact + "/" + rateMult);
          } catch (Exception e) {
            System.out.println("Got illegal seedname e=" + e);
          }
        }

        // With dbgbin on the seq # has been put in 1st 4 bytes of miniseed seq to add checking for this
        if (dbgbin) {
          bb.position(0);
          int seq = bb.getInt();
          if (seq != expected) {
            if (seq % in.getSize() != expected % in.getSize()) {
              System.out.println("Data sequence does not match expected in test mode expected="
                      + expected + " got " + seq + " nextout=" + in.getNextout());
            } else {
              System.out.println("Data sequence does not match expected but is right position expected="
                      + expected + " got " + seq + " nextout=" + in.getNextout());
            }
          }
        }
        expected++;
        if (expected == 2000000000) {
          expected = 0;
        }
        if (expected % 10000 == 0) {
          System.out.println(Util.asctime() + " Processing " + expected + " " + in.toString());
        }
        if (expected % 100 == 0) {
          in.updateControlFile(in.getNextout());
        }

      }
    } catch (FileNotFoundException e) {
      System.out.println("File not found=" + filename + " " + e.getMessage());
    } catch (IOException e) {
      System.out.println(e.getMessage() + "IO error in main()");
    }

  }
}
