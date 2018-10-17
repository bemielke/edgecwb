/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.utility;

import java.io.IOException;
import java.util.ArrayList;
import gnu.trove.map.hash.TLongObjectHashMap;
import gnu.trove.iterator.TLongObjectIterator;
import gov.usgs.anss.util.Util;
import gov.usgs.anss.edge.RawToMiniSeed;
import gov.usgs.anss.seed.MiniSeedPool;
import gov.usgs.anss.edgethread.FakeThread;
import gov.usgs.anss.edge.MiniSeedOutputHandler;
import gov.usgs.anss.seed.MiniSeed;
import gov.usgs.anss.edge.RawDisk;
import gov.usgs.anss.edgethread.EdgeThread;
import java.util.Collections;

/**
 * This class simplifies creating MiniSeed from a time series. When you create an object of this
 * type the time series is created and Built into an ArrayList of MiniSeed which can be gotten by
 * the user, or written to a file by the appropriate methods.
 * <p>
 * Data from many channels can be processed through this object. There is an array where all blocks
 * are concatenated and also an array list that is maintained with separate channels in each. Note
 * the access methods or write methods are setup to do the whole array of by channel depending on
 * the name.
 * <p>
 * @author davidketchum
 */
public final class MakeRawToMiniseed implements MiniSeedOutputHandler {

  private final TLongObjectHashMap<ArrayList<MiniSeed>> channels = new TLongObjectHashMap<>();
  private final MiniSeedPool msp;
  private final ArrayList<MiniSeed> blks = new ArrayList<>(1000);
  private final FakeThread fake = null; //new FakeThread("","MRTM");
  private int dayFilterYear = 0;
  private int dayFilterDoy = 0;
  private EdgeThread par;

  private void prt(String s) {
    if (par == null) {
      Util.prt(s);
    } else {
      par.prt(s);
    }
  }

  private void prta(String s) {
    if (par == null) {
      Util.prta(s);
    } else {
      par.prta(s);
    }
  }

  private void prt(StringBuilder s) {
    if (par == null) {
      Util.prt(s);
    } else {
      par.prt(s);
    }
  }

  private void prta(StringBuilder s) {
    if (par == null) {
      Util.prta(s);
    } else {
      par.prta(s);
    }
  }

  /**
   * Returns an iterator to all of the ArrayLists with a new channel each iteration. Since this is
   * unsynchronized on channels it is dangerous to us
   *
   * @return The iterator
   */
  public TLongObjectIterator<ArrayList<MiniSeed>> getIterator() {
    return channels.iterator();
  }

  @Override
  public String toString() {
    return "MakeRTMS blks=" + blks.size() + " " + (blks.size() > 0 ? blks.get(0).toString() : "");
  }

  public void setDayFilter(int yr, int doy) {
    dayFilterYear = yr;
    dayFilterDoy = doy;
  }

  /**
   * Flush all data into the array lists of miniseed blocks.
   *
   */
  public void flush() {
    RawToMiniSeed.forceStale(-1000);
  }

  /**
   * Return the MiniSeed blocks that resulted from creating this object.
   *
   * @return
   */
  public ArrayList<MiniSeed> getMiniSeedBlocks() {
    return blks;
  }

  /**
   * Return the MiniSeed blocks that resulted from creating this object.
   *
   * @param seedname The NNSSSSSCCCLL of the channel
   * @return The array list with the data blocks for this channel (unsorted) or null if the channel
   * does not exist
   */
  public ArrayList<MiniSeed> getMiniSeedBlocks(String seedname) {
    synchronized (channels) {
      return channels.get(Util.getHashFromSeedname(seedname));
    }
  }

  /**
   * use this to clear out the miniseed lists. If you do not do this and call many times you end up
   * with a concatenated set of blocks from each call. Call this when all of the data on the
   * miniseed lists has been processed and its time to free all of the space.
   *
   */
  @Override
  public void close() {
    flush();
    blks.clear();
    synchronized (channels) {
      TLongObjectIterator<ArrayList<MiniSeed>> itr = channels.iterator();
      while (itr.hasNext()) {
        itr.advance();
        ArrayList<MiniSeed> chan = itr.value();
        chan.clear();
      }
    }
    msp.freeAll();
    blks.clear();
  }

  /**
   * this is used by the RawToMiniSeed code to send each block, the user of this class should not
   * use this!
   *
   * @param buf Buffer of miniseed data
   * @param len length of buffer
   */
  @Override
  public void putbuf(byte[] buf, int len) {
    MiniSeed ms = msp.get(buf, 0, len);
    if (dayFilterYear > 0) {
      if (ms.getDoy() != dayFilterDoy) {
        return;
      }
      if (ms.getYear() != dayFilterYear) {
        return;
      }
    }
    blks.add(ms);   // Add to list of all blocks
    // Add to list of blocks by channels
    synchronized (channels) {
      ArrayList<MiniSeed> chan = channels.get(Util.getHashFromSeedname(ms.getSeedNameSB()));
      if (chan == null) {
        chan = new ArrayList<>(100);
        channels.put(Util.getHashFromSeedname(ms.getSeedNameSB()), chan);
      }
      chan.add(ms);   // add it to the channel one
    }

  }

  /**
   * Take the existing ArrayList of MiniSeed and write it to disk
   *
   * @param filename The full filename the user want for the file
   * @throws IOException If one occurs writing the file
   */
  public void writeToDisk(String filename) throws IOException {
    try (RawDisk msout = new RawDisk(filename, "rw")) {
      msout.setLength(0L);      // always a new file if opened during run.
      for (int i = 0; i < blks.size(); i++) {
        msout.writeBlock(i, blks.get(i).getBuf(), 0, 512);
      }
    } // always a new file if opened during run.
  }

  /**
   * Take the miniseed in a channel oriented structure and write out the file.
   *
   * @param seedname Write out only this seedname
   * @param filename The full filename the user want for the file
   * @throws IOException If one occurs writing the file
   */
  public void writeToDisk(String seedname, String filename) throws IOException {
    try (RawDisk msout = new RawDisk(filename, "rw")) {
      msout.setLength(0L);      // always a new file if opened during run.
      ArrayList<MiniSeed> chan;
      synchronized (channels) {
        chan = channels.get(Util.getHashFromSeedname(seedname));
      }
      if (chan != null) {
        Collections.sort(chan);
        for (int i = 0; i < chan.size(); i++) {
          msout.writeBlock(i, chan.get(i).getBuf(), 0, 512);
        }
      }
    } // always a new file if opened during run.
  }

  /**
   * use this to create an empty maker. Then use load to put miniseed onto the list and close if you
   * write them out each time or process them via getMiniSeed().
   *
   * @param parent For logging
   */
  public MakeRawToMiniseed(EdgeThread parent) {

    par = parent;
    msp = new MiniSeedPool(par);
    RawToMiniSeed.setStaticOutputHandler((MakeRawToMiniseed) this);

  }

  /**
   * use this to create an empty maker. Then use load to put miniseed onto the list and close if you
   * write them out each time or process them via getMiniSeed().
   *
   */
  public MakeRawToMiniseed() {
    msp = new MiniSeedPool(null);
    RawToMiniSeed.setStaticOutputHandler((MakeRawToMiniseed) this);

  }

  /**
   * create and process a time series in the creator
   *
   * @param seedname 12 character seedname
   * @param nsamp Number of samples in data[]
   * @param data The samples
   * @param year year of first sample
   * @param doy the day of the year
   * @param secs seconds of first sample
   * @param mics Microseconds of first sample
   * @param rate The data rate is Hertz
   * @param activity flag per SEED
   * @param IOClock flags per SEED
   * @param quality flags per SEED
   * @param timeQual
   * @param parent
   */
  public MakeRawToMiniseed(String seedname, int nsamp, int[] data, int year, int doy,
          int secs, int mics, double rate, int activity, int IOClock, int quality, int timeQual, EdgeThread parent) {
    par = parent;
    msp = new MiniSeedPool(parent);
    RawToMiniSeed.setStaticOutputHandler((MakeRawToMiniseed) this);
    StringBuilder seednameSB = new StringBuilder(12);
    Util.rightPad(seednameSB.append(seedname), 12);
    RawToMiniSeed.addTimeseries(data, nsamp, seednameSB, year, doy, secs, mics, rate, activity, IOClock, quality, timeQual, fake);
    RawToMiniSeed.forceStale(-10);

  }

  /**
   * process a time series in an existing instance. All within this call - that is you are not going
   * to incrementally add to this miniseed.
   *
   * @param seedname 12 character seedname
   * @param nsamp Number of samples in data[]
   * @param data The samples
   * @param year year of first sample
   * @param doy The day of year
   * @param secs seconds of first sample
   * @param mics Microseconds of first sample
   * @param rate the data rate in Hz
   * @param activity flag per SEED
   * @param IOClock flags per SEED
   * @param quality flags per SEED
   * @param timeQual The time quality 0-100
   * @return THe ArrayList of MiniSeed of the resulting compressed blocks.
   */
  public ArrayList<MiniSeed> loadTS(String seedname, int nsamp, int[] data, int year, int doy,
          int secs, int mics, double rate, int activity, int IOClock, int quality, int timeQual) {
    Util.clear(nscl);
    Util.rightPad(nscl.append(seedname), 12);
    RawToMiniSeed.addTimeseries(data, nsamp, nscl, year, doy, secs, mics, rate,
            activity, IOClock, quality, timeQual, fake);
    RawToMiniSeed.forceStale(-10);
    return blks;
  }

  /**
   * process a time series in an existing instance. All within this call - that is you are not going
   * to incrementally add to this miniseed.
   *
   * @param seedname 12 character seedname
   * @param nsamp Number of samples in data[]
   * @param data The samples
   * @param year year of first sample
   * @param doy The day of year
   * @param secs seconds of first sample
   * @param mics Microseconds of first sample
   * @param rate the data rate in Hz
   * @param activity flag per SEED
   * @param IOClock flags per SEED
   * @param quality flags per SEED
   * @param timeQual The time quality 0-100
   * @return THe ArrayList of MiniSeed of the resulting compressed blocks.
   */
  public ArrayList<MiniSeed> loadTS(StringBuilder seedname, int nsamp, int[] data, int year, int doy,
          int secs, int mics, double rate, int activity, int IOClock, int quality, int timeQual) {

    Util.rightPad(seedname, 12);
    RawToMiniSeed.addTimeseries(data, nsamp, seedname, year, doy, secs, mics, rate,
            activity, IOClock, quality, timeQual, fake);
    RawToMiniSeed.forceStale(-10);
    return blks;
  }

  /**
   * process a time series in an existing instance. Remember to call close() if each time series is
   * processed as you go (otherwise the blocks concatenate). This is used to incrementally add data
   * to a miniseed.
   *
   * @param seedname 12 character seedname
   * @param nsamp Number of samples in data[]
   * @param data The samples
   * @param year year of first sample
   * @param doy The day of year
   * @param secs seconds of first sample
   * @param mics Microseconds of first sample
   * @param rate the data rate in Hz
   * @param activity flag per SEED
   * @param IOClock flags per SEED
   * @param quality flags per SEED
   * @param timeQual The time quality 0-100
   */
  public void loadTSIncrement(StringBuilder seedname, int nsamp, int[] data, int year, int doy,
          int secs, int mics, double rate, int activity, int IOClock, int quality, int timeQual) {
    RawToMiniSeed.addTimeseries(data, nsamp, seedname, year, doy, secs, mics, rate,
            activity, IOClock, quality, timeQual, fake);
  }

  /**
   * process a time series in an existing instance. Remember to call close() if each time series is
   * processed as you go (otherwise the blocks concatenate). This is used to incrementally add data
   * to a miniseed.
   *
   * @param seedname 12 character seedname
   * @param nsamp Number of samples in data[]
   * @param data The samples
   * @param year year of first sample
   * @param doy The day of year
   * @param secs seconds of first sample
   * @param mics Microseconds of first sample
   * @param rate the data rate in Hz
   * @param activity flag per SEED
   * @param IOClock flags per SEED
   * @param quality flags per SEED
   * @param timeQual The time quality 0-100
   */
  public void loadTSIncrement(String seedname, int nsamp, int[] data, int year, int doy,
          int secs, int mics, double rate, int activity, int IOClock, int quality, int timeQual) {
    Util.clear(nscl).append(seedname);
    Util.rightPad(nscl, 12);
    RawToMiniSeed.addTimeseries(data, nsamp, nscl, year, doy, secs, mics, rate,
            activity, IOClock, quality, timeQual, fake);
  }
  StringBuilder nscl = new StringBuilder(12);

  public ArrayList<MiniSeed> getBlocks() {
    RawToMiniSeed.forceStale(-10);
    return blks;
  }

}
