/*
 *  This software is in the public domain because it contains materials 
 *  that originally came from the United States Geological Survey, 
 *  an agency of the United States Department of Interior. For more 
 *  information, see the official USGS copyright policy at 
 *  http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.msread.FixUp;

import gov.usgs.alarm.SendEvent;
import gov.usgs.anss.edge.IllegalSeednameException;
import gov.usgs.anss.edge.RawDisk;
import gov.usgs.anss.edgethread.EdgeThread;
import gov.usgs.anss.seed.MiniSeedPool;
import gov.usgs.anss.seed.MiniSeed;
import gov.usgs.anss.util.SeedUtil;
import gov.usgs.anss.util.Util;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.GregorianCalendar;
import java.util.Random;

/**
 * The class is used to write out channel day files. Generally the caller has
 * determined this is the right day and then uses the add() method to add blocks
 * to the file. If the blocks arrive out-of-order, then this is marked and the
 * files are periodically sorted into the correct order.
 *
 * <br> Because the sorting can be (and should be) serialized to prevent lots of
 * sorts at the same time, the storage for the sort is static and the sort
 * method synchronized so that only sort can occur at a time.
 *
 * @author U.S. Geological Survey <ketchum at usgs.gov>
 */
public class DailyFile {

  private static final ArrayList<MiniSeed> blks = new ArrayList<>(20000); // used by sort so only one to share
  private static byte[] sortBuf = new byte[10000000];                          // used by sort, so share it
  private static final Random random = new Random();
  private static final MiniSeedPool msp = new MiniSeedPool();           // Used by sort, so share it
  private static final GregorianCalendar thisTime = new GregorianCalendar();
  private static long lastSortAny;
  private long lastSort;
  private boolean inOrder;
  private boolean firstSort;
  private int nsort;
  private int sortTime;
  private final String filename;
  private int iblk;
  private RawDisk rw;
  private EdgeThread par;
  private long lastTime;
  private long lastWrite;
  private int nsamp;
  private MiniSeed ms;        // A scratch miniseed from the MiniSeedPool, released on close
  private final int[] ymd = new int[3];
  int[] time = new int[4];       // for decoding miniseed time

  public String getFilename() {
    return filename;
  }

  public boolean isOrdered() {
    return inOrder;
  }

  public long lastWrite() {
    return lastWrite;
  }

  public static MiniSeedPool getMiniSeedPool() {
    return msp;
  }

  public void prt(String a) {
    if (par == null) {
      Util.prt(a);
    } else {
      par.prt(a);
    }
  }

  public void prta(String a) {
    if (par == null) {
      Util.prta(a);
    } else {
      par.prta(a);
    }
  }

  @Override
  public String toString() {
    try {
      return filename + " len=" + rw.length() + " iblk=" + iblk + " ordered=" + inOrder
              + " lastwrite=" + Util.asctime2(lastWrite) + " lastTime=" + Util.asctime2(lastTime) + " msp=" + msp + " srt=" + sortBuf.length;
    } catch (IOException e) {
      return filename + " len=** not open ** iblk=" + iblk + " ordered=" + inOrder
              + " lastwrite=" + Util.asctime2(lastWrite) + " lastTime=" + Util.asctime2(lastTime) + " msp=" + msp + " srt=" + sortBuf.length;
    }
  }

  /**
   *
   * @param filename The filename to open, any need directories will be created.
   * @param parent The parent EdgeThread to use for logging.
   * @throws IOException if one occurs
   */
  public DailyFile(String filename, EdgeThread parent) throws IOException {
    this.filename = filename;
    par = parent;
    Util.chkFilePath(filename);
    lastSort = System.currentTimeMillis() + (long) (120000 * random.nextDouble());
    lastWrite = lastSort;
    sortTime = 300000;
    rw = new RawDisk(filename, "rw");
    if (rw.length() > 0) {
      inOrder = false;
      lastSort = System.currentTimeMillis() - (Math.abs(filename.hashCode()) % 300000);
      rw.position((int) rw.length() / 512);
      iblk = (int) (rw.length() / 512);
      firstSort = true;
    } else {
      iblk = 0;
      inOrder = true;
      lastSort = System.currentTimeMillis();
      rw.position(0);
      firstSort = false;
    }
  }

  public void close() {
    if (rw != null) {
      prta("Closing " + toString());
      try {
        if (!inOrder) {
          sortFile();
        }
        rw.close();
      } catch (IOException e) {
        par.prta("error closing Dailyfile " + filename + " e=" + e);
      }
    }
    if (ms != null) {
      msp.free(ms);     // Free the miniSEED block
    }
    ms = null;
    rw = null;
    par = null;
  }

  /**
   * Add a block to this file. If data comes in out-of-order, and its been 300
   * seconds since it was put in order, the file would be sorted as part of this
   * call
   *
   * @param buf A buffer with a 512 byte miniseed block
   * @param off An offset to the beginning of the block
   * @param len The len to process (512)
   * @return true if block was added to the file
   * @throws IOException
   * @throws IllegalSeednameException
   */
  public boolean add(byte[] buf, int off, int len) throws IOException, IllegalSeednameException {
    if (len == 512) {
      rw.write(buf, 0, len);
      iblk++;
      long thisTimeMillis;
      lastWrite = System.currentTimeMillis();
      if (MiniSeed.crackNsamp(buf) <= 0 || (buf[15] == 'L' && buf[16] == 'O' && buf[17] == 'G')) {
        return true;     // trigger or some other non-data block
      }
      if (buf[0] == 0 && buf[6] == 0 && buf[8] == 0 && buf[15] == 0) {
        prta("DF: " + filename + " *** got a zero block in add- skip it");
        return true;
      }

      MiniSeed.crackTime(buf, time);
      SeedUtil.ymd_from_doy(MiniSeed.crackYear(buf), MiniSeed.crackDOY(buf), ymd);
      synchronized (thisTime) {
        thisTime.set(ymd[0], ymd[1] - 1, ymd[2], time[0], time[1], time[2]);
        thisTime.setTimeInMillis(thisTime.getTimeInMillis() / 1000 * 1000 + time[3] / 10);
        thisTimeMillis = thisTime.getTimeInMillis();
      }
      // if this block is out of order and not some trigger block
      if (thisTimeMillis < lastTime) {
        if (ms == null) {
          ms = msp.get(buf, 0, len);
        } else {
          ms.load(buf, 0, len);
        }

        if (inOrder) {
          prta(filename + " is now out of order is " + Util.ascdate(thisTimeMillis) + " " + Util.asctime2(thisTimeMillis)
                  + " vs " + Util.ascdate(lastTime) + " " + Util.asctime2(lastTime) + " ms=" + ms.toString().substring(0, 50));
          lastSort = lastWrite;     // start up the wait for a sort
        }
        inOrder = false;
      } else {
        lastTime = thisTimeMillis;// if its not a trigger or something
      }
      // If out of order, see if the file sort timeout has expired.
      /*if (!inOrder) {
        long now = System.currentTimeMillis();
        if (now - lastSort > sortTime ) {  // is it time to sort it
          long since = now - lastSort;
          sortFile();
          lastSort = now;
          lastSortAny = now;
          prta("Sort of " + filename + " is complete since=" + since + " 1st=" + firstSort + " "
                  + (System.currentTimeMillis() - now) / 1000. + " s msp=" + msp + " srt=" + sortBuf.length + " sortTime=" + sortTime + " nsort=" + nsort);
          firstSort = false;
        }
      }*/
      return true;
    } else {
      MiniSeed ms2 = msp.get(buf, off, len);
      prta(filename + " **** illegal blocksize len=" + len + " ms=" + ms2);
      msp.free(ms2);
      return false;
    }
  }

  /**
   * sort the file
   */
  private void sortFile() throws IOException {
    synchronized (blks) {
      nsort++;
      if (nsort % 3 == 2) {
        prta("Sorting of file is excessive **** double interval");
        if (nsort > 10) {
          SendEvent.edgeSMEEvent("DFWriteSort", "Excess sort " + filename, this);
        }
        sortTime = sortTime + 600000;
      }
      if (rw.length() > sortBuf.length) {
        sortBuf = new byte[(int) rw.length() * 2];
      }
      prta("Sorting " + toString());
      // Read all of the blocks into one big buffer, process it to MiniSeed blocks, 
      rw.readBlock(sortBuf, 0, (int) rw.length());
      int ioff = 0;
      blks.clear();
      while (ioff < rw.length()) {
        blks.add(msp.get(sortBuf, ioff, 512));
        ioff = ioff + 512;
      }
      // Sort the blocks and write the file back out
      try {
        if (!blks.isEmpty()) {
          Collections.sort(blks);
        }
      } catch (RuntimeException e) {
        prta("*** Runtime error sorting " + filename + " blks size=" + blks.size());
        SendEvent.edgeSMEEvent("DFWSortErr", filename + " did not sort!", this);
        if (par == null) {
          e.printStackTrace();
        } else {
          e.printStackTrace(par.getPrintStream());
        }
        for (int i = 0; i < blks.size(); i++) {
          prt(i + " " + blks.get(i));
        }
        return;
      }
      rw.position(0);
      int n = 0;
      int ndup = 0;
      for (int i = 0; i < blks.size(); i++) {
        if (i > 0) {
          // If the block is not a duplicate of prior one
          if (blks.get(i).isDuplicate(blks.get(i - 1))) {
            ndup++;
          } else {
            rw.writeBlock(n++, blks.get(i).getBuf(), 0, 512);
          }
        } else {
          rw.writeBlock(n++, blks.get(i).getBuf(), 0, 512);  // Write out the first block
        }
        msp.free(blks.get(i));
      }
      if (ndup > 0) {
        prta(ndup + " ** duplicate blocks found when sorting " + filename + " len was=" + (rw.length() / 512) + " blk.size=" + blks.size() + " n=" + n);
        if ((rw.length() / 512) - n != ndup) {
          prta("******** this does not make sense.");
        }
        rw.setLength(n * 512);
        rw.position(n);
      }
      blks.clear();
      inOrder = true;
    }
  }

  /**
   *
   * @param args This is a list of filenames to reading, sort, and eliminate
   * duplicate blocks
   */
  public static void main(String[] args) {
    for (String arg : args) {
      try {
        DailyFile df = new DailyFile(arg, null);
        df.sortFile();
      } catch (IOException e) {
        Util.prt("IOException  e=" + e);
        e.printStackTrace();
      }
    }
  }

}
