/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.edgeoutput;

import gov.usgs.anss.edge.MiniSeedLog;
import gov.usgs.alarm.SendEvent;
import gov.usgs.anss.edge.RawDisk;
import gov.usgs.anss.edge.IllegalSeednameException;
import gov.usgs.anss.edgethread.ManagerInterface;
import gov.usgs.anss.seed.MiniSeed;
import gov.usgs.anss.util.Util;
import gov.usgs.edge.config.Channel;
import gov.usgs.anss.edgethread.EdgeThread;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.TreeMap;

/**
 * This block writes a ring file with a physical of sizeMB+1 blocks. The blocks Are written into the
 * file at (seq % sizeMB) +1 block (blocks 1-sizeMB+1). The first block is used to update where we
 * are by writing out the value of the next sequence to write(nextout) and the sizeMB of the file.
 * The sequence numbers within a ring increase until 2,000,000,000 is reached. This sequence the
 * maps to sequence zero again. Because of this architecture the sizeMB of the file blocks must
 * divide evenly into 2,000,000,000. We specify the file in MB which is really 2000 blocks to insure
 * this is true.
 * <p>
 * Cooperates with OutputInfrastructure calls for each packet. Has a bit of the data to include in
 * this RingFile and a bit of the individual disable so each block can be evaluated for inclusion
 * and temporary override. Also enforces the restricted data flag if the file is not setup to allow
 * restricted data on this ring. This flag usually comes from the sendTo.allowrestricted which allow
 * restricted data to things like VMS and other internal processing, but not to other SendTos.
 * <p>
 * For use on static ram for file systems, set the updateMS to be really big - then the last block
 * written will be done only on termination. For RRPClient use the starting sequence is normally set
 * in this file from the RRPclient last ack file so the last block written here is not really very
 * important when the RRPClient is started by this RingFile RRPClient is really keeping track of
 * last data to the file and the last acked packet.
 *
 * @author davidketchum
 */
public class RingFile extends EdgeOutputer implements ManagerInterface {

  public static final int MAX_SEQ = 2000000000;
  protected static final TreeMap<String, RingFile> rings = new TreeMap<>();
  protected static byte[] zerobuf;

  //private RRPClient rrp;
  protected RawDisk dsk;
  protected int size;         // in 512 byte blocks
  protected int nextout;
  protected int updateMS;
  protected long lastUpdate;    // Time control block was last updated
  protected String filename;
  protected final byte[] b = new byte[512];          // 512 bytes of scratch space
  protected ByteBuffer bb;      // This wraps the b[]
  protected boolean terminate;
  protected boolean sendLog;
  protected long mask;                  // This is the sendto bit bit for this output type
  protected long disableMask;
  UpdaterThread updater;
  protected boolean dbg;
  protected boolean isNew;
  protected EdgeThread par;
  protected String args;      // kept mostly so extenders can have access to raw argument
  protected String name;      //  ditto
  private boolean check;
  private long bytesOut;
  private final StringBuilder tmpsb = new StringBuilder(100);
  protected long nrestricted;

  @Override
  public long getBytesOut() {
    return bytesOut;
  }

  @Override
  public long getBytesIn() {
    return 0;
  }

  @Override
  public boolean isRunning() {
    return !terminate;
  } // not a thread

  @Override
  public boolean isAlive() {
    return !terminate;
  } // not a thread

  @Override
  public String getArgs() {
    return args;
  }

  @Override
  public void setCheck(boolean t) {
    check = t;
  }

  @Override
  public boolean getCheck() {
    return check;
  }

  public int getSize() {
    return size;
  }

  public static void zapFile(RawDisk dsk, int size) throws IOException {
    if (zerobuf == null) {
      zerobuf = new byte[51200];    // 100 blocks of data
    }
    int blk = 1;
    while (blk < size + 1) {
      int nblks = size + 1 - blk;
      if (nblks > 100) {
        nblks = 100;
      }
      dsk.writeBlock(blk, zerobuf, 0, nblks * 512);
      blk += nblks;
    }
    zerobuf = null;
  }

  /**
   *
   * @param s A string to print to the log
   */
  protected final void prt(String s) {
    if (par == null) {
      Util.prt(s);
    } else {
      par.prt(s);
    }
  }

  protected final void prt(StringBuilder s) {
    if (par == null) {
      Util.prt(s);
    } else {
      par.prt(s);
    }
  }

  /**
   *
   * @param s A String to print in the log preceded by the current time
   */
  protected final void prta(String s) {
    if (par == null) {
      Util.prta(s);
    } else {
      par.prta(s);
    }
  }

  protected final void prta(StringBuilder s) {
    if (par == null) {
      Util.prta(s);
    } else {
      par.prta(s);
    }
  }

  public int getNextout() {
    return nextout;
  }

  @Override
  public String toString() {
    return toStringBuilder(null).toString();
  }

  @Override
  public StringBuilder toStringBuilder(StringBuilder tmp) {
    StringBuilder sb = tmp;
    if (sb == null) {
      sb = Util.clear(tmpsb);
    }
    synchronized (sb) {
      sb.append("RF: ").append(filename).append(" sz=").append(size).append(" nxt=").append(nextout)
              .append(" msk=").append(Util.toHex(mask)).append(" allowRestricted=").append(allowRestricted);
    }
    return sb;
  }

  @Override
  public String getFilename() {
    return filename;
  }

  public void setDebug(boolean b) {
    dbg = b;
  }

  @Override
  public void terminate() {
    prta(toString() + " call terminate().");
    terminate = true;
    if (updater != null) {
      updater.interrupt();
    }
  }

  public long getSendtoMask() {
    return mask;
  }

  public boolean isNew() {
    return isNew;
  }

  /**
   * This should never be called for a ring file. This is how RAW data fills the memory buffers in
   * RingServerSeedLink
   *
   * @param c
   * @param seedname
   * @param time
   * @param rate
   * @param nsamp
   * @param data
   */
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
  public RingFile(String argline, EdgeThread parent) throws FileNotFoundException, IOException {
    super(false);
    String[] argsin = argline.split("\\s");
    String path = Util.fs + "data2";
    String ringname = "unknown.ring";
    int updMS = 500;
    int sizeMB = 500;
    long bit = 0;
    long disable = 0;
    String argsleft = "";
    for (int i = 0; i < argsin.length; i++) {
      if (argsin[i].equalsIgnoreCase("-p")) {
        path = argsin[i + 1];
        i++;
      } else if (argsin[i].equalsIgnoreCase("-f")) {
        ringname = argsin[i + 1];
        i++;
      } else if (argsin[i].equalsIgnoreCase("-u")) {
        updMS = Integer.parseInt(argsin[i + 1]);
        i++;
      } else if (argsin[i].equalsIgnoreCase("-s")) {
        sizeMB = Integer.parseInt(argsin[i + 1]);
        i++;
      } else if (argsin[i].equalsIgnoreCase("-m")) {
        bit = Long.parseLong(argsin[i + 1]);
        i++;
      } else if (argsin[i].equalsIgnoreCase("-dm")) {
        disable = Long.parseLong(argsin[i + 1]);
        i++;
      } else if (argsin[i].equalsIgnoreCase("-allowrestricted")) {
        allowRestricted = argsin[i + 1].equals("true");
        i++;
      } else if (argsin[i].equalsIgnoreCase("-args")) {
        argsleft = argline.substring(argline.indexOf("-args") + 5).trim();
        break;
      }
    }
    init(path, ringname, updMS, sizeMB, bit, disable, allowRestricted, argsleft, parent);
    this.args = argline;
  }

  /**
   * Creates a new instance of RingFile (note : this might have been easier if we had a constructor
   * which takes the sendto object directly).
   *
   * @param path The file path on which to make the RingFile or open it
   * @param name The filename of the ring (.ring will be added)
   * @param updMS The ms between updates of the header block (if zero, no updates)
   * @param sizeMB The sizeMB of the file in mB
   * @param bit The bit of the bit associated with this ringfile 1<<(sendto.id-1) 
   * @param disable The disable bit
   * @param allow If true, allow restricted output to ring file
   * @param args Arguments to this (only -allowlog is currently supported)
   * @param parent The EdgeThread for logging, if null use Util.prt()
   * @throws FileNotFoundException if the file cannot be opened
   * @throws IOException If one occurs.
   */
  public RingFile(String path, String name, int updMS, int sizeMB, long bit, long disable, boolean allow, String args, EdgeThread parent)
          throws FileNotFoundException, IOException {
    super(allow);
    init(path, name, updMS, sizeMB, bit, disable, allow, args, parent);
  }

  /**
   * this is the shared code between the two types of constructors
   *
   * @param path The file path on which to make the RingFile or open it
   * @param name The filename of the ring (.ring will be added)
   * @param updMS The ms between updates of the header block (if zero, no updates)
   * @param sizeMB The sizeMB of the file in mB
   * @param bit The bit of the bit associated with this ringfile 1<<(sendto.id-1) @param d isable
   * The disable bit @param allow If true, allow restricted output
   * to ring file
   * @param args Arguments to this (only -allowlog is currently supported)
   * @param parent The EdgeThread for logging, if null use Util.prt()
   * @throws FileNotFoundException if the file cannot be opened
   * @throws IOException If one occurs.
   */
  private void init(String path, String name, int updMS, int sizeMB, long bit, long disable, boolean allow, String args, EdgeThread parent)
          throws FileNotFoundException, IOException {
    size = sizeMB * 2000;
    par = parent;
    disableMask = disable;
    if (MAX_SEQ % size != 0) {      // User has picked a bad file sizeMB - override it
      size = size - (MAX_SEQ % size);
      prta("User specified file size is not legal at " + (sizeMB * 2000) + " blks. change to " + size);
    }
    updateMS = updMS;
    mask = bit;
    this.name = name;
    this.args = args;
    filename = path + Util.fs + name.trim() + ".ring";
    filename = filename.replace("//", Util.fs);
    dsk = new RawDisk(filename, "rw");
    for (int i = 0; i < 512; i++) {
      b[i] = 0;
    }
    bb = ByteBuffer.wrap(b);
    if (dsk.length() == 0) {   // is it a new file
      isNew = true;
      nextout = 0;
      bb.clear();
      bb.putInt(nextout);
      bb.putInt(size);
      dsk.writeBlock(size, b, 0, 512);   // Make the file the right sizeMB
      dsk.writeBlock(0, b, 0, 512);      // write first index block
      prta(Util.clear(tmpsb).append("Start new ringfile=").append(filename).
              append(" nextout=0 size=").append(size).append(" allowrestricted=").append(allowRestricted));
      zapFile(dsk, size);
      doUpdate();
    } else {                    // existing file, check its length and get nextout and sizeMB
      dsk.readBlock(b, 0, 512);
      bb.clear();
      nextout = bb.getInt();
      int sizenow = bb.getInt();
      prta(Util.clear(tmpsb).append("Pickup old ringfile=").append(filename).append(" nextout=").append(nextout).
              append(" size=").append(sizenow).append(" sizein=").append(size).
              append(" allowrestricted=").append(allowRestricted));
      if (size > sizenow) {
        isNew = true;
        dsk.writeBlock(size, b, 0, 512); // make it bigger
        dsk.writeBlock(size, b, 0, 512); // make it bigger
        nextout = 0;
        prta(Util.clear(tmpsb).append("Ring file is bigger.  Initialize it. file=").append(filename).append(" size=").append(size));
        zapFile(dsk, size);
        doUpdate();
      }
      if (size < sizenow) {
        //size=sizenow;  // if its bigger, use the full allocated sizels 
        dsk.setLength((size + 1) * 512);
        nextout = 0;
        isNew = true;
        prta(Util.clear(tmpsb).append("Ring file is smaller.  Initialize it. file=").append(filename).append(" size=").append(size));
        zapFile(dsk, size);
        doUpdate();
      }
    }
    if (args.contains("-dbg")) {
      dbg = true;
    }

    // Add this to static list of 
    synchronized (rings) {
      rings.put(name, (RingFile) this);
    }
    if (filename.contains("RLISS") || filename.contains("NEIC") || args.contains("-allowlog")) {
      sendLog = true;
      MiniSeedLog.setRLISSOutputer((RingFile) this);
    }
    prt(Util.clear(tmpsb).append("RingFile started file=").append(filename).append(" mask=").append(Util.toHex(mask)).
            append(" disable=").append(Util.toHex(disableMask)).append(" ms=").append(updateMS).append(" size=").append(size));

    if (updateMS > 0) {
      updater = new UpdaterThread(this);        // this insures time based updates and has shutdown code, but not if a child has it
    }
  }

  /**
   * process a block through the EdgeOutputer system
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
    if ((c.getSendtoMask() & mask) != 0) {       // is this going out this output method
      if ((c.getFlags() & disableMask) == 0) // is it not disabled from this output method
      {
        if ((c.getFlags() & 16) != 0) {        // is this public restricted
          if (!allowRestricted) {                // Is this outputer allowed restricted data
            if (nrestricted++ % 100 == 0) {
              prta(Util.clear(tmpsb).append("RF: Got a restricted channel to RingFile reject it.").append(filename).
                      append(" c=").append(c).append(" SEED=").append(MiniSeed.crackSeedname(msbuf)).
                      append(" allowRestricted=").append(allowRestricted));
              SendEvent.debugEvent("ChanRestrict", "Restricted channel to " + filename + " " + MiniSeed.crackSeedname(msbuf), "RingFile");
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
        prta(Util.clear(tmpsb).append(filename).append(" break up to 512 ").append(org.toStringBuilder(null)));
        MiniSeed[] mss = org.toMiniSeed512();
        for (MiniSeed ms : mss) {
          int[] time = MiniSeed.crackTime(ms.getBuf());
          //prt(i+" "+time[0]+":"+time[1]+":"+time[2]+" ms="+mss[i]);
          if (time[0] >= 24 || time[1] >= 60 || time[2] >= 60) {
            prt("***** bad time code in broken up packet!");
          }
          writeNext(ms.getBuf(), 0, 512, c.getSendtoMask(), c.getFlags());
        }
      } catch (IllegalSeednameException e) {
        prta(Util.clear(tmpsb).append("Got illegal seedname in ringfile - discard ").append(e.getMessage()));
      }
    } else {
      writeNext(msbuf, 0, msbuf.length, c.getSendtoMask(), c.getFlags());
    }
  }

  public static void writeBlock(byte[] b, int off, int len, long sendto, long flags) {
    synchronized (rings) {
      Iterator<RingFile> itr = rings.values().iterator();
      while (itr.hasNext()) {
        itr.next().writeNext(b, off, len, sendto, flags);
      }
    }
  }

  /**
   * Write to this file if the sendto bit indicates this is the right file
   *
   * @param b The data buffer to write
   * @param sendto The raw sendto bit bit for this block
   * @param flags The channel flags, (which might be used to disable this temporarily)
   *
   */
  public synchronized void writeNext(byte[] b, long sendto, int flags) {
    writeNext(b, 0, b.length, sendto, flags);
  }

  /**
   * Write to this file if the sendto bit indicates this is the right file
   *
   * @param b The data buffer to write
   * @param off The offset in buffer to start write
   * @param len The number of bytes to write
   * @param sendto The raw sendto bit bit for this block
   * @param flags The channel flags, (which might be used to disable this temporarily)
   */
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
  public synchronized void writeNext(byte[] b, int off, int len) {

    try {
      int nblks = (len + 511) / 512;
      if (terminate) {
        prta("RF: write to ringfile aborted..  File is closed.");
        return;
      }
      if ((nextout % size) + nblks > size) {
        int nblks2 = size - (nextout % size);
        if (dbg) {
          prt(Util.clear(tmpsb).append("RF: ").append(filename).append(" Write spans len=").append(len).
                  append(" nxt=").append(nextout).append(" size=").append(size).append(" nblks=").append(nblks2));
        }
        dsk.writeBlock((nextout % size) + 1, b, off, nblks2 * 512);
        nextout += nblks2;
        dsk.writeBlock((nextout % size) + 1, b, off + nblks2 * 512, len - nblks2 * 512);
        nextout += (len - nblks2 * 512 + 511) / 512;
        if (nextout >= MAX_SEQ) {
          prta(Util.clear(tmpsb).append(filename).append(" Ring buffer sequence roll over to zero ").append(nextout));
          nextout -= MAX_SEQ;
        }
        if (dbg) {
          prt(Util.clear(tmpsb).append("RF: ").append(filename).append(" Write spans nxt after=").append(nextout));
        }
      } else {
        //if(dbg) prt("RW: "+filename+" Write normal len="+len+" nxt="+nextout+" sizeMB="+sizeMB);
        dsk.writeBlock((nextout % size) + 1, b, off, len);
        nextout += (len + 511) / 512;
        if (nextout >= MAX_SEQ) {
          prta(Util.clear(tmpsb).append(filename).append(" Ring buffer sequence roll over to zero ").append(nextout));
          nextout -= MAX_SEQ;
        }
        bytesOut += len;
        //if(dbg) prt("RF: "+filename+" Write normal nxt after = "+nextout);
      }
      if (nextout % 1000 == 0 || dbg) {
        prta("RF: " + filename + " progress to " + nextout);
      }
    } catch (IOException e) {
      prta(Util.clear(tmpsb).append("RF: ").append(filename).append(" Got IOException writing to RingFile=").
              append(toString()).append(" msg=").append(e.getMessage()));
      e.printStackTrace(par.getPrintStream());
      Util.IOErrorPrint(e, "RF: writing to RingFile=" + toString());
      SendEvent.edgeEvent("ErrWrDisk", "RingFile wr err " + filename, this);
      nextout++;
    }

  }

  @Override
  public synchronized void close() {
    terminate();
  }

  static public void closeAll() {
    Iterator<RingFile> itr = rings.values().iterator();
    while (itr.hasNext()) {
      RingFile r = itr.next();
      synchronized (r) {
        r.terminate();
      }
    }
  }

  protected void clearUpdater() {
    if (updater != null) {
      updater.clear();
    }
  }

  /**
   * This tread periodically updates the first block with the last sequence written to the file. If
   * the terminate is set, then it writes the final time and closes the file.
   */
  public final class UpdaterThread extends Thread {

    RingFile ring;
    boolean clear;

    @Override
    public String toString() {
      return filename;
    }

    public UpdaterThread(RingFile r) {
      ring = r;
//      Runtime.getRuntime().addShutdownHook(new UpdaterThreadShutdown(this));
      gov.usgs.anss.util.Util.prta("new ThreadShutdown " + getName() + " " + getClass().getSimpleName() + " r=" + r);
      start();
    }

    public void clear() {
      clear = true;
    }

    @Override
    public void run() {
      int lastNextout = -2;
      long times = 0;
      int looptime;
      try {
        sleep(10000);
      } catch (InterruptedException e) {
      }
      if (clear) {
        return;
      }
      while (!terminate) {
        try {
          looptime = 0;
          while (looptime < updateMS) {
            try {
              sleep(100);
            } catch (InterruptedException e) {
            }
            if (terminate) {
              break;      // Terminate leave loop and write last block to file
            }
            if (clear) {
              break;
            }
            looptime += 100;
          }
          if (clear) {
            break;
          }
          if (nextout != lastNextout) {
            if (System.currentTimeMillis() - times > 600000) {
              prta("RF: doUpdate " + nextout);
            }
            times = System.currentTimeMillis();
            doUpdate();
            lastNextout = nextout;
          }
        } catch (RuntimeException e) {
          prta("RF: updaterThread() runtime continue e=" + e);
        }
      }
      doUpdate();
      try {
        if (!clear) {
          prta(Util.clear(tmpsb).append("RF: ").append(filename).append(" is closed."));
          dsk.close();
        }
      } catch (IOException e) {
      }
      prt(Util.clear(tmpsb).append("RF: ").append(filename).append(" Updater thread for ").append(ring.toString()).append(" has terminated."));
    }
  }

  private void doUpdate() {
    bb.clear();
    bb.putInt(nextout);
    bb.putInt(size);
    //if(dbg) prta("RF: "+filename+" Update control for "+ring.toString());
    try {
      dsk.writeBlock(0, b, 0, 8);
    } catch (IOException e) {
      prt(Util.clear(tmpsb).append("RF: ").append(filename).append(" Got IO exception updating ring block").append(e.getMessage()));
    }
  }

  /*  class UpdaterThreadShutdown extends Thread {
    UpdaterThread thr;
    public UpdaterThreadShutdown(UpdaterThread t) {thr=t;}
    public void run() {
      terminate=true;
      thr.interrupt();
    }
  }
   */
  public static void main(String[] args) {
    try {
      RingFile r = new RingFile(Util.fs + "Users" + Util.fs + "ata", "RINGTEST", 2000, 2, 1, 2, false, "", null);
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
      Util.prta("Exception in RingFile main() e=" + e.getMessage());
    }
  }
}
