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
import gov.usgs.anss.edgemom.RRPClient;
import gov.usgs.anss.util.Util;
import gov.usgs.edge.config.Channel;
import gov.usgs.anss.edgethread.EdgeThread;
import gov.usgs.anss.seed.MiniSeed;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import static java.lang.Thread.sleep;

/**
 * This block writes a ring file with a physical of size+1 blocks. The blocks Are written into the
 * file at (seq % size) +1 block (blocks 1-size+1).
 * <p>
 * The main difference with the RingFile class is that the first block is not used to update where
 * the last known block was written. This is kept in a state file (RRPStateFile) so that static RAM
 * systems to not get the first block written so often. The StateFile used to update where we are by
 * writing out the value of the next sequence to write(nextout). The first block still contains the
 * size of the ring. The other difference is this class also configures the RRPClient which will
 * write out the file. The other main difference is that this will create a RRPClient thread to send
 * out this ring file based on the parameters given and will provide to the RRPClient a queue of
 * recent data written to the ring file so that an extra read of the ring is not needed to forward
 * the data.
 * <p>
 * On an RTS2/Slate2 configuration this object is create the ring file from the OutputInfrastructure
 * thread. It creates a RRPStateFile with the extension .rf. Such a OI line will have the -rrp
 * switch which will start a RRPClient to send the file to the NEIC. This will create a RRPStateFile
 * witht the extension .rrpc which controls reading back the file and sending it out.
 * <p>
 * The sequence numbers within a ring increase until 2,000,000,000 is reached. This sequence the
 * maps to sequence zero again. Because of this architecture the size of the file blocks must divide
 * evenly into 2,000,000,000. We specify the file in MB which is really 2000 blocks to insure this
 * is true.
 * <p>
 * Cooperates with OutputInfrastructure calls for each packet. Has a mask of the data to include in
 * this RingFile and a mask of the individual disable so each block can be evaluated for inclusion
 * and temporary override. Also enforces the restricted data flag if the file is not setup to allow
 * restricted data on this ring. This flag usually comes from the sendTo.allow restricted which
 * allow restricted data to things like VMS and other internal processing, but not to other SendTos.
 * <p>
 * For use on static ram for file systems, set the updateMS to be really big - then the last block
 * written will be done only on termination. For RRPClient use the starting sequence is normally set
 * in this file from the RRPclient last ack file so the last block written here is not really very
 * important when the RRPClient is started by this RingFile RRPClient is really keeping track of
 * last data to the file and the last acked packet.
 *
 * @author davidketchum
 */
public final class RRPRingFile extends RingFile {

  //private RRPClient rrp;
  private RRPStateFile stateFileOut;      // This is the statefile with where writer is
  private RRPClient rrp;
  private RRPUpdaterThread rrpupdater;
  private final ArrayList<RRPBlock> queue = new ArrayList<>(20);
  private int qin;
  private ShutdownRRPRingFile shutdown;
  private boolean hasWrapped;   // ;f true, the file has wrapped around and the last part should not be zeros
  private String tag;

  @Override
  public void close() {
    doUpdate();
    rrpupdater.interrupt();
    super.close();
  }

  public long length() {
    try {
      return dsk.length();
    } catch (IOException e) {
    }
    return -1;
  }

  public byte[] getQueueForSeq(int sq) {
    int i = 0;
    for (RRPBlock q : queue) {
      //prta((i++)+" search queue "+q.getSeq()+" to "+sq);
      if (q.getSeq() == sq) {
        return q.getBuf();
      }
    }
    //prta("getqueueForSeq() seq not found");
    return null;
  }

  @Override
  public void terminate() {
    rrp.terminate();
    if (!Util.isShuttingDown()) {
      Runtime.getRuntime().removeShutdownHook(shutdown);
    }
    super.terminate();
  }

  public boolean hasWrapped() {
    return hasWrapped;
  }

  @Override
  public int getNextout() {
    if (nextout != stateFileOut.getLastIndex()) {
      prta("**** nextout not up-todate to state file nextout=" + nextout + " state=" + stateFileOut.getLastIndex());
    }
    return nextout; // This should be the same as nextout!
  }

  @Override
  public String toString() {
    return "RF: " + filename + " sz=" + size + " nxt=" + nextout + " msk=" + Util.toHex(mask) + " allowRestricted=" + allowRestricted;
  }

  @Override
  public void processRawBlock(Channel c, StringBuilder seedname, long time, double rate, int nsamp, int[] data) {
  }

  /**
   * This constructor is used by the OutputInfrastructureManager. The other for the old
   * OutputInfrastructure
   *
   * @param argline An argument line like from the output infrastructure command file
   * @param parent A parent to use for logging
   * @throws FileNotFoundException
   * @throws IOException
   */
  public RRPRingFile(String argline, EdgeThread parent) throws FileNotFoundException, IOException {
    super(argline, parent);
    init(parent);
  }

  /**
   * Creates a new instance of RingFile (note : this might have been easier if we had a constructor
   * which takes the sendto object directly).
   *
   * @param path The file path on which to make the RingFile or open it
   * @param name The filename of the ring (.ring will be added)
   * @param updMS The ms between updates of the header block
   * @param sizeMB The size of the file in mB
   * @param bit The mask of the bit associated with this ringfile 1<<(sendto.id-1) @param d isable
   * The disable mask @param allow If true, allow restricted output
   * to ring file
   * @param args Arguments to this (-allowlog) and the arguments for the RRPClient
   * @param parent An EdgeThread for logging, or null to use default
   * @throws FileNotFoundException if the file cannot be opened
   * @throws IOException If one occurs.
   */
  public RRPRingFile(String path, String name, int updMS, int sizeMB, long bit,
          long disable, boolean allow, String args, EdgeThread parent)
          throws FileNotFoundException, IOException {
    super(path, name, 0, sizeMB, bit, disable, allow, args, parent);   // updMS is zero to shutdown updates of header block in RingFile
    init(parent);
  }

  private void init(EdgeThread parent)
          throws FileNotFoundException, IOException {
    if (updateMS < 60000) {
      updateMS = 60000;
      prt("Update is too fast, set to 60000  size=" + size);
    }
    par = parent;
    tag = "RRPRF:[" + name + "]";
    stateFileOut = new RRPStateFile(filename + ".rf", size / 2000, par);
    stateFileOut.setDebug(dbg);

    for (int i = 0; i < 20; i++) {
      queue.add(new RRPBlock());
    }
    if (isNew) {
      stateFileOut.clearFile();
      nextout = 0;
      doUpdate();
    } else {
      nextout = stateFileOut.getLastIndex();
      int len = dsk.readBlock(queue.get(0).getBuf(), size - 2, 512);
      if (queue.get(0).getBuf()[0] != 0 && queue.get(0).getBuf()[20] != 0) {
        hasWrapped = true;
      }
    }
    if (args.contains("-rrp")) {
      prt(tag + "  start RRP client " + "-ring " + filename + " -state " + (size / 2000) + " " + args);
      rrp = new RRPClient("-ring " + filename + " -state " + (size / 2000) + " " + args + " >>" + name, name, this);
    }
    prta(tag + hashCode() + "  started file=" + filename + " mask=" + Util.toHex(mask) + " disable=" + Util.toHex(disableMask) + " ms=" + updateMS + " size=" + size);

    // Start up the updater and shutdown threads.
    rrpupdater = new RRPUpdaterThread(this);
    updater.clear();        // shutdown the updateer thread from the basic RingFile.
    shutdown = new ShutdownRRPRingFile(this);
    Runtime.getRuntime().addShutdownHook(shutdown);
  }

  /**
   * Test if sequence is in this current file
   *
   * @param seq The sequence to test
   * @return true if the sequence is in the current file
   */
  public boolean isSeqActive(int seq) {
    int diff = nextout - seq;      // How far ahead is the last block written to the file
    if (diff > size * 9 / 10) {
      diff -= size; // next out near the end of the file but this sequence near the beginning, adjust
    }
    if (diff > -size / 100 && diff <= 0) {  // the receiver has more recent data than our next out - likely next out is unwritten
      return true;
    }
    boolean inWrap = false;
    if (seq > MAX_SEQ - size && nextout < size) {  // The odd case where nextout has wrapped back to zero and seq is near end
      diff += MAX_SEQ;
      inWrap = true;
    }
    //prta(tag+" isSeqActive test seq="+seq+" nextout="+nextout+" diff="+diff+" inwrap="+inWrap+" hasWrap="+hasWrapped);
    // If this is more than the size of the file in either direction, the answer is no
    if (Math.abs(diff) > size - 10 && inWrap) {
      prta(tag + " Failed within size");
      return false;
    }
    if (seq > nextout && !inWrap) {
      prta(tag + " Failed less nextout not wrapped");
      return false;
    } // the sequence is ahead of the writer, it cannot be valid
    // The tricky case is the sequence is in the zero filled part of the file between nextout and size and the file has not wrapped
    if (hasWrapped) {
      prta(tag + " O.K. hasWrapped seq=" + seq);
      return true;
    }
    return seq % size <= nextout % size;    // Its ok unless the desired sequence is after nextout

  }

  /**
   * process a block through the OutputInfrastructure system
   *
   * @param msbuf buffer of bytes containing miniseed data
   * @param c A channel for the seedname in the edgeblock
   */
  @Override
  public void processBlock(byte[] msbuf, Channel c) {
    if (c == null) {
      // prta("Write LOG rec to "+MiniSeed.crackSeedname(msbuf)+" file="+filename);
      if (msbuf[15] == 'L' && msbuf[16] == 'O' && msbuf[17] == 'G' && sendLog) {
        writeNext(msbuf, 0, 512);
      }
      return;
    }
    if (dbg) {
      prta(tag + " processBlock() mask=" + Util.toHex(mask)
              + " c.sendToMask()=" + Util.toHex(c.getSendtoMask()) + " c.flags=" + c.getFlags());
    }
    if ((c.getSendtoMask() & mask) != 0) {       // is this going out this output method
      if ((c.getFlags() & disableMask) == 0) // is it not disabled from this output method
      {
        if ((c.getFlags() & 16) != 0) {        // is this public restricted
          if (!allowRestricted) {                // Is this outputer allowed restricted data
            if (nrestricted++ % 100 == 1) {
              prta(tag + "  Got a restricted channel to RingFile reject it." + filename
                      + " c=" + c + " SEED=" + MiniSeed.crackSeedname(msbuf) + " allowRestricted=" + allowRestricted);
              SendEvent.debugEvent("ChanRestrict", "Restricted channel to " + filename
                      + " " + MiniSeed.crackSeedname(msbuf), "RingFile");
            }
            return;
          }
        }
      }
    } else {
      return;  // This block is not going this way
    }
    if (msbuf.length > 512) {      // This must be miniseed block that needs to be broken up
      try {
        MiniSeed org = new MiniSeed(msbuf);
        prta(tag + "  break up to 512 " + org);
        MiniSeed[] mss = org.toMiniSeed512();
        for (MiniSeed ms : mss) {
          int[] time = MiniSeed.crackTime(ms.getBuf());
          //prt(i+" "+time[0]+":"+time[1]+":"+time[2]+" ms="+mss[i]);
          if (time[0] >= 24 || time[1] >= 60 || time[2] >= 60) {
            prt(tag + " ***** bad time code in broken up packet!");
          }
          writeNext(ms.getBuf(), 0, 512, c.getSendtoMask(), c.getFlags());
        }
      } catch (IllegalSeednameException e) {
        prta(tag + " Got illegal seedname in ringfile - discard " + e.getMessage());
      }
    } else {
      writeNext(msbuf, 0, msbuf.length, c.getSendtoMask(), c.getFlags());
    }
  }

  /**
   * Write to this file if the sendto mask indicates this is the right file
   *
   * @param b The data buffer to write
   * @param sendto The raw sendto bit mask for this block
   * @param flags The channel flags, (which might be used to disable this temporarily)
   *
   */
  @Override
  public synchronized void writeNext(byte[] b, long sendto, int flags) {
    writeNext(b, 0, b.length, sendto, flags);
  }

  /**
   * Write to this file if the sento mask indicates this is the right file
   *
   * @param b The data buffer to write
   * @param off The offset in buffer to start write
   * @param len The number of bytes to write
   * @param sendto The raw sendto bit mask for this block
   * @param flags The channel flags, (which might be used to disable this temporarily)
   */
  @Override
  public synchronized void writeNext(byte[] b, int off, int len, long sendto, long flags) {
    if ((sendto & mask) != 0) {
      if ((flags & disableMask) == 0) {
        writeNext(b, off, len);
      }
    }
    //else 
    //  prt("RingFile did not write data is disabled");
  }

  /**
   * Write to this file the given data unconditionally
   *
   * @param b The data buffer to write, length is the length of the buffer
   *
   */
  @Override
  public synchronized void writeNext(byte[] b) {
    writeNext(b, 0, b.length);
  }

  /**
   * Write to this file the given data unconditionally
   *
   * @param b The data buffer to write
   * @param off The offset in buffer to start write
   * @param len The number of bytes to write
   */
  @Override
  public synchronized void writeNext(byte[] b, int off, int len) {
    int oldNextout = nextout;
    try {
      int nblks = (len + 511) / 512;
      if (terminate) {
        prta(tag + "  write to ringfile aborted..  File is closed.");
        return;
      }
      if ((nextout % size) + nblks > size) {
        int nblks2 = size - (nextout % size);
        if (dbg) {
          prt(tag + "  Write spans len=" + len + " nxt=" + nextout + " size=" + size + " nblks=" + nblks2);
        }
        dsk.writeBlock((nextout % size) + 1, b, off, nblks2 * 512);
        nextout = nextout + nblks2;
        dsk.writeBlock((nextout % size) + 1, b, off + nblks2 * 512, len - nblks2 * 512);
        nextout = nextout + (len - nblks2 * 512 + 511) / 512;
        if (nextout >= MAX_SEQ) {
          prta(tag + " Ring buffer sequence roll over to zero " + nextout);
          nextout -= MAX_SEQ;
        }
        if (nextout < 10 && oldNextout > size - 10) {
          hasWrapped = true;
        }
        stateFileOut.updateSequence(nextout, nextout, false);
        if (dbg) {
          prt(tag + "  Write spans nxt after=" + nextout);
        }
      } else {
        //if(dbg) prt("RW: "+filename+" Write normal len="+len+" nxt="+nextout+" size="+size);
        queue.get(qin).load(b, nextout);
        //prta(tag+" Queue qin="+qin);
        qin = (++qin) % queue.size();
        dsk.writeBlock((nextout % size) + 1, b, off, len);
        nextout = nextout + (len + 511) / 512;
        if (nextout >= MAX_SEQ) {
          prta(tag + " Ring buffer sequence roll over to zero " + nextout);
          nextout -= MAX_SEQ;
        }
        stateFileOut.updateSequence(nextout, nextout, false);
        //if(dbg) prt("RF: "+filename+" Write normal nxt after = "+nextout);
      }
      if (nextout % 1000 == 0 || dbg) {
        prta(tag + "  progress to " + nextout + " qin=" + qin);
      }
    } catch (IOException e) {
      prta(tag + "  Got IOException writing to RingFile=" + toString() + " msg=" + e.getMessage());
      e.printStackTrace(par.getPrintStream());
      Util.IOErrorPrint(e, "RF: writing to RingFile=" + toString());
      SendEvent.edgeEvent("ErrWrDisk", "RingFile wr err " + filename, this);
      nextout++;
      try {
        stateFileOut.updateSequence(nextout, nextout, false);
      } catch (IOException e2) {
      }
    }

  }

  /**
   * Update nextout setting to disk. Normally called by RRPUpdaterThread and if file is closing.
   *
   */
  public final void doUpdate() {
    try {
      stateFileOut.updateSequence(nextout, nextout, true);
      if (dbg) {
        prta(tag + " nextout updated to " + nextout);
      }
    } catch (IOException e) {
      prt(tag + " IOError updating file e=" + e);
      SendEvent.edgeEvent("ErrWrDisk", "RRPRingFile wr err " + filename + ".rf", this);
    }
  }

  /**
   * This thread periodically updates the statefile with the last sequence written to the file. If
   * the terminate is set, then it writes the final time and closes the file and the state file.
   */
  public final class RRPUpdaterThread extends Thread {

    RRPRingFile ring;

    public RRPUpdaterThread(RRPRingFile r) {
      ring = r;
//      Runtime.getRuntime().addShutdownHook(new UpdaterThreadShutdown(this));
      gov.usgs.anss.util.Util.prta("new ThreadShutdown " + getName() + " " + getClass().getSimpleName() + " r=" + r);
      setDaemon(true);
      start();
    }

    @Override
    public void run() {
      int lastNextout = -2;
      int looptime;
      while (!terminate) {
        looptime = 0;
        while (looptime < updateMS) {
          try {
            sleep(100);
          } catch (InterruptedException e) {
          }
          if (terminate) {
            break;      // Terminate leave loop and write last block to file
          }
          looptime += 100;
        }
        if (nextout != lastNextout) {
          doUpdate();
          lastNextout = nextout;
        }
      }
      try {
        stateFileOut.updateSequence(nextout, nextout, false);// insure latest index is in state file
        prta(ring.tag + "  is closing");
        stateFileOut.close();   // This updates the file
        dsk.close();
      } catch (IOException e) {
      }
      prt(ring.tag + "  Updater thread for " + ring.toString() + " has terminated.");
    }
  }

  public class RRPBlock {

    int seq = -1;
    byte[] b = new byte[512];

    public RRPBlock() {
    }

    public void load(byte[] buf, int sq) {
      seq = sq;
      System.arraycopy(buf, 0, b, 0, 512);
    }

    public int getSeq() {
      return seq;
    }

    public byte[] getBuf() {
      return b;
    }

    @Override
    public String toString() {
      return "RRPBLock: seq=" + seq;
    }
  }

  public static void main(String[] args) {
    try {
      RRPRingFile r = new RRPRingFile(Util.fs + "Users" + Util.fs + "data", "RINGTEST", 2000, 2, 1, 2, false, "", null);
      byte[] b = new byte[4096];
      r.setDebug(true);
      r.nextout = 1999998002;
      for (int i = 0; i < 2003; i++) {
        r.writeNext(b, 0, 4096, 1, (i % 100 == 0 ? 2L : 0L));
      }
      for (int i = 0; i < 10000; i++) {
        for (int j = 0; j < 4096; j++) {
          b[j] = (byte) (i % 3);
        }
        int len = (i % 8 + 1) * 512;
        r.writeNext(b, 0, len, (long) (i % 3), (i % 100 == 0 ? 2L : 0L));
      }
      RingFile.closeAll();
    } catch (IOException e) {
      Util.prt("Exception in RingFile main() e=" + e.getMessage());
    }

  }

  class ShutdownRRPRingFile extends Thread {

    /**
     * default constructor does nothing the shutdown hook starts the run() thread
     *
     * @param t The RRPRingFile that this object is in charge of shutting down
     */
    RRPRingFile thr;

    public ShutdownRRPRingFile(RRPRingFile t) {
      thr = t;
      gov.usgs.anss.util.Util.prta("new ThreadShutdown " + getName() + " " + getClass().getSimpleName());
    }

    // This is called by the RunTime.shutdown at any termination 
    @Override
    public void run() {
      prta(" Shutdown Close RRPRF: " + filename);
      if (thr != null) {
        thr.close();
      }
    }
  }
}
