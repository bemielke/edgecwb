/*
 *  This software is in the public domain because it contains materials 
 *  that originally came from the United States Geological Survey, 
 *  an agency of the United States Department of Interior. For more 
 *  information, see the official USGS copyright policy at 
 *  http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.cwbquery;

import gov.usgs.anss.seed.MiniSeedPool;
import gov.usgs.anss.edgethread.EdgeThread;
import gov.usgs.anss.seed.MiniSeed;
import gov.usgs.anss.util.Util;
import gov.usgs.anss.edge.IllegalSeednameException;
import gov.usgs.anss.edge.RunList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.GregorianCalendar;
import java.util.ArrayList;

/**
 * This class is for the memory buffering in QueryMom. New miniseed are added to
 * the array.
 *
 * 1) The array contains allocated miniSeed blocks from the MiniSeed pool. 2)
 * size is the number of elements with current data, 3) maxsize is the number of
 * elements which contains allocated miniSeed, but ones past size should be
 * marked NSAMP_NULL
 * <p>
 * This method allows the arrays to high water with allocated MiniSeed blocks
 * and for some of them to be available for adding without having to free them
 * each time a trim() operation occurs. That is trimmed blocks from the
 * beginning of the array are marked NSAMP_NULL and moved to the end. Thus there
 * are always maxsize blocks allocated and the once between size and maxsize
 * (exclusive) are available to be loaded.
 * <p>
 * The query() method is used by the TriNetServer to get blocks from the RAM
 * directly.
 */
public final class MiniSeedArray {

  private static final int NSAMP_NULL = -1000;
  private static final MiniSeedPool msp = new MiniSeedPool();
  private boolean dbg = false;
  private boolean dbgsuppress = false;
  private MiniSeed[] msblks;
  private int whoHas = 0;
  private final Integer mutex = Util.nextMutex();
  private int size;     // This is the size of the used blocks
  private int maxsize;  // this is the size of the blocks that have been allocated from the MiniSeedPool, some at end exist but marked NSAMP_NULL
  private double rate;
  private String seedname;
  private String tag;
  private static String debugChannel;
  private int msover2;
  private final double duration;
  private final long durationMS;
  private final StringBuilder sb = new StringBuilder(100);
  private final StringBuilder tmpsb = new StringBuilder(100);
  private final EdgeThread par;
  private final RunList runs = new RunList(10, false);

  public static MiniSeedPool getMiniSeedPool() {
    return msp;
  }

  public static String getDebugChan() {
    return debugChannel;
  }

  public int getMemoryUsage() {
    return maxsize * 1024 + sb.capacity() + 40;
  }

  public void setDebug(boolean t) {
    dbg = t;
  }

  public void setDebugSuppress(boolean t) {
    dbgsuppress = t;
  }

  public String getSeedname() {
    return seedname;
  }

  public int getSize() {
    return size;
  }

  public int getMaxSize() {
    return maxsize;
  }

  public double getRate() {
    return rate;
  }

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
  private final MiniSeedTimeComparator comparator = new MiniSeedTimeComparator();

  @Override
  public String toString() {
    return toStringBuilder(null).toString();
  }

  public StringBuilder toStringBuilder(StringBuilder tmp2) {
    StringBuilder tmp = tmp2;
    if (tmp == null) {
      tmp = Util.clear(tmpsb);
    }
    synchronized (tmp) {
      tmp.append(tag).append(" sz=").append(size).append("/").append(maxsize).append("/").
              append(msblks == null ? "null" : msblks.length).append((maxsize - size) > (size / 5 + 1) ? "***" : "").
              append(" rt=").append(rate);
      if (size > 0 && msblks != null) {
        tmp.append(" ").append(Util.ascdatetime2(msblks[0].getTimeInMillis())).append("-").
                append(Util.ascdatetime2(msblks[size - 1].getTimeInMillis()));
      }
    }
    return tmp;
  }

  /**
   * Return info about this object and the list of runs covered.
   *
   * @return
   */
  public StringBuilder toStringDetail() {
    Util.clear(sb);
    sb.append(toStringBuilder(null)).append("\n");
    sb.append(runs.toStringFull());

    //new RuntimeException("Debug").printStackTrace(par.getPrintStream());
    return sb;
  }

  /**
   * REturn summary of packet and the full list of blocks and runs - i.e. lots
   * of output
   *
   * @return
   */
  public StringBuilder toStringFull() {
    Util.clear(sb);
    sb.append(toStringDetail());
    for (int i = 0; i < size; i++) {
      boolean t = false;
      if (i > 0) {
        if (Math.abs(msblks[i - 1].getNextExpectedTimeInMillis() - msblks[i].getTimeInMillis()) > msover2) {
          t = true;
        }
      }
      sb.append("  i=").append(Util.leftPad("" + i, 4)).append(" ").append(msblks[i].toString().substring(0, 50)).
              append(t ? " ***" : "").append("\n");
    }
    return sb.append(toString());
  }

  /**
   * By calling this collection with fixed timestamps and offsets, the user can
   * have a reasonable sparse collection of known time stamps.
   *
   * @param ms A first MiniSeed block to add to the array
   * @param duration To track the msblks
   * @param parent Logging parent.
   * @throws java.lang.InstantiationException Normally if the ms block is badly
   * formed!
   */
  public MiniSeedArray(MiniSeed ms, double duration, EdgeThread parent) throws InstantiationException {
    if (ms.getRate() <= 0. || ms.getNsamp() <= 0) {
      throw new InstantiationException("Cannot create a MiniSeed collection with non-timeseries block ms=" + ms);
    }
    this.seedname = ms.getSeedNameString();
    par = parent;
    this.duration = duration;
    durationMS = (long) (duration * 1000. + 0.5);
    setRate(ms.getRate());
    tag = "MSA:[" + seedname + "]";
    msblks = new MiniSeed[(int) (duration * rate / Math.min(200, ms.getNsamp()) * 2.)];
    prta(Util.clear(tmpsb).append(tag).append(" init size=").append(msblks.length).
            append(" dur=").append(duration).append(" rt=").append(rate).append(" ms=").append(ms.toStringBuilder(null)));
    size = 0;
    add(ms);
  }

  /**
   * Clear the history of time and add a new starting point for the given offset
   *
   * @param ms The single block to remain after it clear
   */
  public void clear(MiniSeed ms) {

    synchronized (mutex) {
      whoHas = 1;
      for (int i = size - 1; i >= 0; i--) {
        msp.free(msblks[i]);
        msblks[i] = null;
      }
      this.seedname = ms.getSeedNameString();
      size = 0;
      if (ms.getRate() > 0 && ms.getNsamp() > 0) {
        setRate(ms.getRate());
      }
    }
    whoHas = 0;
    add(ms);
    if (dbg) {
      prta(Util.clear(tmpsb).append(tag).append(" clear() ").append(seedname).append(" ").append(toStringDetail()));
    }
  }

  public void close() {
    synchronized (mutex) {
      whoHas = 2;
      for (int i = size - 1; i >= 0; i--) {
        msp.free(msblks[i]);
        msblks[i] = null;
      }
      size = 0;
      if (sb.length() > 0) {
        sb.delete(0, sb.length());
      }
    }
    whoHas = 0;
  }

  /**
   * Set the data rate. This checks for change of rate
   *
   * @param r The rate to set in Hz
   */
  public final void setRate(double r) {
    if (rate != 0.) {
      if (Math.abs(rate - r) / rate > 0.1) {// Is this a significant rate change (not just a better estimate)
        if (dbg) {
          Util.clear(tmpsb).append(tag).append(" **** Rate changed in MiniSeedCollection rnow=").
                  append(r).append(" vs ").append(rate).append(" ");
          toStringBuilder(tmpsb);
          prta(tmpsb);
        }
        synchronized (mutex) {
          whoHas = 3;
          size = 0;
          for (int i = 0; i < size; i++) {
            free(i);
          }
        }
        whoHas = 0;
      }
    }
    if (r > 0.) {
      rate = r;
      msover2 = (int) (1. / rate * 1000. / 2.);
    }

  }

  /**
   * Free the block at index I from the MiniSeed pool, also sets this msblk to
   * null;
   *
   * @param i The index to free
   */
  private void free(int i) {
    msp.free(msblks[i]);
    msblks[i] = null;
  }

  /**
   * discard any time point older than time and adjust all offsets by the given
   * amount. Used primarily by the shift() routine to time and update for the
   * data moving in the buffer.
   *
   * @param time The earliest time to keep in millis
   * @return Number of blocks trimmed
   */
  public int trim(GregorianCalendar time) {
    return trim(time.getTimeInMillis());
  }

  /**
   * discard any time point older than time and adjust all offsets by the given
   * amount. Used primarily by the shift() routine to time and update for the
   * data moving in the buffer.
   *
   * @param time The earliest time to keep in millis
   * @return Number of blocks trimmed.
   */
  public int trim(long time) {
    synchronized (mutex) {
      whoHas = 5;
      for (int i = 0; i < size; i++) {    // look for first block following the time
        if (msblks[i].getTimeInMillis() >= time) {
          i--;
          if (i == size && size > 0) {
            i--;    // If this will drop ALL of the data, always keep one!
          }
          if (i > 0) {
            if (msblks.length < maxsize + i) {
              increaseMax(maxsize * 2);
            }
            runs.trim(msblks[i].getTimeInMillis());   // This should be the new beginning time in the RunsList
            if (dbg) {
              prt(Util.clear(tmpsb).append(tag).append(" trim from beginning i=").append(i).
                      append(" ").append((time - msblks[0].getTimeInMillis()) / 1000.).append(" secs"));
            }
            for (int j = 0; j < i; j++) {
              msblks[j].setNsamp(NSAMP_NULL);
            }
            System.arraycopy(msblks, 0, msblks, maxsize, i);    // put the first i blks at the end
            System.arraycopy(msblks, i, msblks, 0, maxsize); // Move all down including just nulled ones
            for (int j = 0; j < i; j++) {
              msblks[maxsize + j] = null; // clear up the moved ones
            }
            size -= i;
            return i;
          } else {
            return 0;
          }
        }
      }
    }
    whoHas = 0;
    return 0;
  }

  /**
   * Trims back by the total duration allowed from the last data time found in
   * the array
   *
   * @return Number of blocks trimmed
   */
  public int trim() {
    if (size < 5) {
      return 0;
    }
    if (dbg) {
      prta(Util.clear(tmpsb).append(tag).append(" trim() starting at ").
              append(Util.ascdatetime2(msblks[size - 1].getTimeInMillis() - durationMS, null)).
              append(" durMS=").append(durationMS));
    }
    int n = trim(msblks[size - 1].getTimeInMillis() - durationMS);
    if (dbg) {
      prta(Util.clear(tmpsb).append(tag).append(" trim() returned ").append(n));
    }
    return n;
  }

  /**
   * Increase size of array of MiniSeed to the new size
   *
   * @param newsize The number of array elements in the resized array
   */
  private void increaseMax(int newsize) {
    whoHas = 6;
    synchronized (mutex) {
      MiniSeed[] tmp = new MiniSeed[newsize];
      System.arraycopy(msblks, 0, tmp, 0, maxsize);
      msblks = tmp;
      //if(dbg) 
      prta(Util.clear(tmpsb).append(tag).append(" increase size to ").append(newsize).append(" ").append(toString()));
    }
    whoHas = 0;
  }

  private void addAtEnd(MiniSeed ms) {
    if (size == msblks.length) {
      increaseMax(size * 2);
    }
    if (msblks[size] == null) {
      msblks[size] = msp.get(ms);
      size++;
    } else if (msblks[size].getNsamp() == NSAMP_NULL) {   // Is the next one a null packet - it should be!
      try {
        msblks[size].load(ms);
        size++;
      } catch (IllegalSeednameException expected) {
      }  // this should be impossible
    } else {
      prta(Util.clear(tmpsb).append(" **** Impossible addAtEnd is not NULL or nsamp=-1000 type null! size=").
              append(size).append("" + " ms=").append(msblks[size]));
    }
    if (size > maxsize) {
      maxsize = size;
      if (maxsize > 400) {
        if (msblks[size - 1].getTimeInMillis() - msblks[0].getTimeInMillis() > durationMS + 300000) {
          int n = trim(msblks[size - 1].getTimeInMillis() - durationMS);
          prta(Util.clear(tmpsb).append(tag).append(" Trim on size larger than duration + 5 minutes n=").append(n).
                  append(" size=").append(size).append("/").append(maxsize).append("/").append(msblks.length));
        }
      }
    }
  }
  private final StringBuilder addsb = new StringBuilder(100);

  /**
   * submit a time for consideration to be added to these structures. If this
   * time is is not within the minTimeDifference of the closest time, it will be
   * added
   *
   * @param ms The miniseed block to add
   */
  public final void add(MiniSeed ms) {
    if (ms == null) {
      prta(Util.clear(tmpsb).append(tag).append(" *** add block is null!"));
      new RuntimeException("add block ms is null").printStackTrace(par.getPrintStream());
      return;
    }
    try {
      synchronized (mutex) {

        whoHas = 10;
        if (size == 0) {
          addAtEnd(ms);
          runs.add(ms);
          if (dbg) {
            prta(Util.clear(tmpsb).append(tag).append(" add block at beginning - first block size=0"));
          }
          return;
        }
        if (ms.getTimeInMillis() >= msblks[size - 1].getTimeInMillis()) {
          if (ms.getTimeInMillis() == msblks[size - 1].getTimeInMillis()) {
            whoHas = 11;
            //if(dbg) 
            if (ms.getSeedNameSB().indexOf("NNMCA  EHZ") < 0) { // Debug: these are allways in duplication
              if (!dbgsuppress) {
                Util.clear(addsb).append(tag).append(" * attempt to add dup block at end size=").append(size).
                        append("\n*dupms=")
                        .append(ms.toStringBuilder(null, 62)).append("\n*dup ").append(size).append(" ").append(msblks[size - 1].toStringBuilder(null, 62));
                prta(addsb);
              }
            }
            return;
          }
          addAtEnd(ms);
          runs.add(ms);
          if (dbg) {
            Util.clear(addsb).append(tag).append(" add block at end size=").append(size).append("/").append(msblks.length).append(" ").
                    append(ms.toStringBuilder(null, 50)).append(" ").append((ms.getTimeInMillis() - msblks[0].getTimeInMillis()) / 1000.);
            prta(addsb);
          }
        } else if (ms.getTimeInMillis() < msblks[0].getTimeInMillis()) {
          if (msblks[size - 1].getTimeInMillis() - duration * 1000. > ms.getTimeInMillis()) {
            return;     // the block is too early
          }
          whoHas = 12;
          if (maxsize == msblks.length) {
            increaseMax(size * 2);
          }
          // Put this block in place zero
          System.arraycopy(msblks, 0, msblks, 1, maxsize);  // move everything down 1
          if (msblks[size + 1] == null) {
            msblks[0] = msp.get(ms); // Is the block past the end null, add a new one
          } else if (msblks[maxsize].getNsamp() == NSAMP_NULL) { // Its not null, so very last block must be NSAMP_NULL
            msblks[0] = msblks[maxsize];    // move the unused one into position
            msblks[maxsize] = null;           // mark it used
            try {
              msblks[0].load(ms);
            } catch (IllegalSeednameException expected) {
            }
          } else {
            prta(Util.clear(tmpsb).append(tag).append(" ****** index end is not null. Impossible size=").append(size).append(" maxsize=").append(maxsize));
            msblks[0] = msp.get(ms);
          }
          runs.add(ms);
          //if(dbg) 
          prta(Util.clear(addsb).append(tag).append(" add block at beginning size=").append(size).append(" ").
                  append(ms.toStringBuilder(null, 50)));
          size++;
          if (size > maxsize) {
            maxsize = size;
          }
        } else {
          whoHas = 13;
          long now = System.currentTimeMillis();
          // Need to find correct position
          int index = Arrays.binarySearch(msblks, 0, size, ms, comparator);
          if (index >= 0) {
            if (!dbgsuppress) {
              prta(Util.clear(addsb).append(tag).append(" * attempt to add dup block at ").append(index).
                      append("/").append(size).append("\n*dupms=  ").append(ms.toStringBuilder(null, 62)).
                      append("\n*dup ").append(index).append(" ").append(msblks[index].toStringBuilder(null, 62)));
            }
            return;
          }    // This says this block is already here!
          index = -(index + 1);    // place to put this one
          long bs = System.currentTimeMillis();
          // All of the following is checking that binarysearch gave the right answer - if it did not then
          // Something is really badly wrong;
          boolean ok = false;
          int loop = 0;
          while (!ok) {
            if (index >= size || index == 0) {
              prta(Util.clear(tmpsb).append(tag).append(" ** add block at index is out of range! ").
                      append(index).append(" size=").append(size).append(" \n").append(toStringFull()));
              ok = false;
            }
            if (index > 0 && index < size) {
              if (msblks[index - 1].getTimeInMillis() > ms.getTimeInMillis()
                      || msblks[index].getTimeInMillis() < ms.getTimeInMillis()) {
                prta(Util.clear(tmpsb).append(tag).append(" *** insert is in wrong place! index=").append(index).
                        append("\nms=").append(ms.toStringBuilder(null, 50)).
                        append("\n-1=").append(msblks[index - 1].toStringBuilder(null, 50)).
                        append("\nin=").append(msblks[index].toStringBuilder(null, 50)).
                        append("\nDump=").append(toStringFull()));
                int retry_index = Arrays.binarySearch(msblks, 0, size, ms, comparator);
                retry_index = -(retry_index + 1);
                prta(Util.clear(tmpsb).append(tag).append(" * retry index ").append(index).append(" ").append(retry_index));
                if (index == retry_index) {
                  prta(tag + " index and retry are same");
                }
                index = retry_index;
              } else {
                ok = true;
              }
            }
            loop++;
            if (loop > 5) {
              prta(Util.clear(tmpsb).append(tag).append(" *** stuck in find index loop! loop=").append(loop).
                      append(" ind=").append(index).append(" size=").append(size).append(" ").
                      append(ms.toStringBuilder(null, 50)).append("\n").append(toStringFull()));
              Arrays.sort(msblks, 0, size, comparator);
            }
            if (loop > 8) {
              prta(Util.clear(tmpsb).append(tag).append(" **** stuck in index loop - abandon hope and discard packet!"));
              return;
            }
            if (loop > 2) {
              Util.sleep(100);
            }
          }
          long bs2 = System.currentTimeMillis();
          if (maxsize + 1 == msblks.length) {
            increaseMax(size * 2);
          }
          System.arraycopy(msblks, index, msblks, index + 1, maxsize - index);
          if (msblks[size + 1] == null) {
            msblks[index] = msp.get(ms);
          } else if (msblks[maxsize].getNsamp() == NSAMP_NULL) {
            msblks[index] = msblks[maxsize];    // move the unused one into position
            msblks[maxsize] = null;           // mark it used
            try {
              msblks[index].load(ms);
            } catch (IllegalSeednameException expected) {
            }
          } else {
            prta(Util.clear(tmpsb).append(tag).append(" ****** index end is not null. Impossible size=").
                    append(size).append(" maxsize=").append(maxsize));
            msblks[index] = msp.get(ms);
          }
          long bs3 = System.currentTimeMillis();
          runs.add(ms);
          long bs4 = System.currentTimeMillis();
          //if(dbg) 
          if (!dbgsuppress) {
            prta(Util.clear(addsb).append(tag).append(" add block at index=").append(index).
                    append(" size=").append(size).append(" ").append(ms.toStringBuilder(null, 50)));
          }
          if (bs - now > 1 || bs2 - bs > 1 || bs3 - bs2 > 1 || bs4 - bs3 > 1) {
            prta(Util.clear(addsb).append(tag).append(" add block at index perf bs=").append(bs - now).
                    append(" chk=").append(bs2 - bs).append(" copy=").append(bs3 - bs2).append(" run=").append(bs4 - bs3));
          }
          //prta("After add at index="+index+" "+toStringDetail().toString());
          size++;
          if (size > maxsize) {
            maxsize = size;
          }
          /*if(!checkOrder(tmp)) {
            prta(tag+"After add at index="+index+" failed test ms="+ms+" \n"+tmp+"\n"+toStringFull());
            runs.add(ms);
            Arrays.sort(msblks, 0, size, comparator);
          }*/
        }
      } // mutex
      whoHas = 0;
    } catch (RuntimeException e) {
      prta(Util.clear(tmpsb).append(tag).append(" Add() runtime e=").append(e).
              append(" size=").append(size).append(" ms=").append(ms).append(" ").append(toString()));
      e.printStackTrace(par.getPrintStream());
      prta(Util.clear(tmpsb).append(tag).append("Add runtime e=").append(e).append(toStringFull()));
    }
  }

  /**
   * Return the index into array which includes the given time, or if the time
   * is in a gap, the block coming just after the gap. If
   *
   * @param time
   * @return    *
   */
  /**
   * Retrieve any blocks between the start and end time onto a user supplied
   * ArrayList and use a user supplied MiniSeedPool to allocate the blocks
   *
   * @param start Start time in millis
   * @param end End time in mills
   * @param mss ArrayList to add any matching blocks to
   * @param msp The MiniSeedPool to allocate the returned blocks from
   * @return
   */
  StringBuilder querysb = new StringBuilder(1000);

  public int query(long start, long end, ArrayList<MiniSeed> mss, MiniSeedPool msp) {
    int n = 0;
    int index;
    long now = System.currentTimeMillis();
    Util.clear(querysb);
    //if(dbg) 
    querysb.append(Util.asctime(System.currentTimeMillis())).append(tag).append(" Query ").
            append(Util.ascdatetime2(start)).append(" to ").
            append(Util.ascdatetime2(end)).append(" mss.size=").append(mss.size()).
            append(" size=").append(size).append("\n");
    if (size > 0) {
      if (runs.getLatest() < start) {
        return 0;    // asking for time way after end
      }
      if (runs.getEarliest() > end) {
        return 0;    // asking for data before this channels time
      }
    } else {
      return 0;        // If size is zero, we have no data
    }
    int whoHad = whoHas;
    synchronized (mutex) {
      whoHas = 40;
      querysb.append(Util.asctime(System.currentTimeMillis())).append(tag).append(" got mutex=").
              append(System.currentTimeMillis() - now).append(" whoHad=").append(whoHad).append("\n");
      if (msblks[0].getTimeInMillis() >= start) {
        index = 0;          // asking for time before, start with this one
      } else {
        querysb.append(Util.asctime(System.currentTimeMillis())).append(tag).
                append(" start binary find").append("\n");
        int low = 0;
        int high = size - 1;
        int steps = 0;
        while ((high - low > 5)) {
          int ind = (low + high) / 2;
          if (dbg) {
            prta(Util.clear(tmpsb).append(tag).append(" Query binary search low=").append(low).
                    append(" ").append(Util.ascdatetime2(msblks[low].getTimeInMillis(), null)).
                    append(" high=").append(high).append(" ").
                    append(Util.ascdatetime2(msblks[high].getTimeInMillis(), null)).
                    append(" ind=").append(ind).append(" ").
                    append(Util.ascdatetime2(msblks[ind].getTimeInMillis(), null)));
          }
          if (msblks[ind].getTimeInMillis() <= start) {
            if (dbg) {
              prta(Util.clear(tmpsb).append(tag).append(" Query binary search set new low=").append(ind));
            }
            low = ind;
          } else {
            if (dbg) {
              prta(Util.clear(tmpsb).append(tag).append(" Query binary search set new high=").append(ind));
            }
            high = ind;
          }
          steps++;
        }
        querysb.append(Util.asctime(System.currentTimeMillis())).append(tag).append(" within 5 ").
                append(low).append(" ").append(high).append(" steps=").append(steps).
                append(" ms=").append(System.currentTimeMillis() - now).append("\n");
        index = low;
        for (int i = low; i <= high; i++) {
          if (msblks[i].getTimeInMillis() <= start && msblks[i].getNextExpectedTimeInMillis() > start) {
            index = i;
            break;
          } else if (msblks[i].getTimeInMillis() > start) {
            index = i;
            break;
          }      // Time must be in a gap, return next block
        }
      }
      querysb.append(Util.asctime(System.currentTimeMillis())).append(tag).
              append(" found index=").append(index).append("\n");
      if (index < 0) {
        return 0;
      }
      if (dbg) {
        prta(Util.clear(tmpsb).append(tag).append(" Query ").append(index).append(" =")
                .append(msblks[index].toStringBuilder(null, 50)).append(" is first block"));
      }
      while (index < size) {
        if (msblks[index].getTimeInMillis() <= end) {
          if (dbg) {
            prta(Util.clear(tmpsb).append(tag).append(" Query add block ind=").
                    append(index).append(" n=").append(n).append(" ").
                    append(msblks[index].toStringBuilder(null, 50)));
          }
          mss.add(msp.get(msblks[index]));
          n++;
        } else {
          if (dbg) {
            prta(Util.clear(tmpsb).append(tag).append(" Query block past end. ind=").
                    append(index).append(" ").append(msblks[index].toStringBuilder(null, 50)).
                    append(" ").append(toStringBuilder(null)));
          }
          break;
        }     // past the end, do not need any more
        index++;
      }
      querysb.append(Util.asctime(System.currentTimeMillis())).append(tag).append(" ready to return ind=").
              append(index).append(" n=").append(n).append(" ms=").
              append(System.currentTimeMillis() - now).append("\n");
    }
    whoHas = 0;
    if ((System.currentTimeMillis() - now) > 2) {
      prt(querysb);
    }
    return n;
  }
  private final RunList chkrunlist = new RunList(runs.getRuns().size(), false);

  public void forceSort() {

    synchronized (mutex) {
      whoHas = 20;
      Arrays.sort(msblks, 0, size, comparator);
    }
    whoHas = 0;
  }

  /**
   * force these runs to match the ones in the chkrunlist
   *
   */
  public void reconcileRuns() {
    for (int i = 0; i < chkrunlist.getRuns().size(); i++) {
      runs.add(chkrunlist.get(i).getSeedname(), chkrunlist.get(i).getStart(), chkrunlist.get(i).getLength());
    }
    runs.consolidate();
  }

  /**
   * Check the data structures for various problems. 1) All blocks after size to
   * maxsize are in the NSAMP_NULL state 2) Build a RunList from the blocks in
   * the array and compare them to the incremental runslist 3) check ordering of
   * blocks in the array 4) Insure no null blocks are in the blocks through size
   *
   * Build up a StringBUilder with a list of the variances
   *
   * @param sb StringBuilder to put the variances in
   * @return true if all looks correct.
   */
  public boolean checkOrder(StringBuilder sb) {
    Util.clear(sb);
    boolean ok = true;
    chkrunlist.clear();
    synchronized (mutex) {
      whoHas = 21;
      for (int i = size; i < msblks.length; i++) {
        if (msblks[i] != null) {
          if (msblks[i].getNsamp() != NSAMP_NULL) {
            sb.append(" *** Off end block not null! size=").append(size).append(" i=").append(i).append(" ").append(msblks[i]);
            ok = false;
          }
        }
      }
      for (int i = 0; i < size; i++) {
        chkrunlist.add(msblks[i]);
        if (msblks[i] == null) {
          ok = false;
          sb.append("*** Block at index=").append(i).append(" is null! size=").append(size);
        }
        if (i < size - 1) {
          if (msblks[i].getTimeInMillis() > msblks[i + 1].getTimeInMillis()) {
            sb.append(" *** blocks not in order at i=").append(i).append("\n").append(msblks[i]).append("\n").append(msblks[i + 1]);
            ok = false;
            //Arrays.sort(msblks, 0, size, comparator);
          } else if (msblks[i].getTimeInMillis() == msblks[i + 1].getTimeInMillis()) {
            sb.append(" ** Blocks seems duplicated at index=").append(i).append(" ").append(Util.ascdatetime2(msblks[i].getTimeInMillis()));
            ok = false;
          }
        }
      }
      runs.consolidate();
      chkrunlist.consolidate();

      if (runs.getRuns().size() != chkrunlist.getRuns().size()) {
        if (runs.getRuns().size() > 10) {
          sb.append(" ### unequal number of runs, but more than 10 ignore runs=").
                  append(runs.getRuns().size()).append(" ").append(chkrunlist.getRuns().size()).append("\n");
        } else {
          sb.append(" *** unequal number of runs on run list\n");
          sb.append(runs.toStringFull()).append("\n").append(chkrunlist.toStringFull());
        }
        ok = false;
      } else {
        for (int i = 0; i < runs.getRuns().size(); i++) {
          if (runs.get(i).compareTo(chkrunlist.get(i)) != 0) {
            if (chkrunlist.size() > 10) {
              sb.append("  ### Data in more than 10 runs ").append(i).append(" run does not match. run.size=").
                      append(runs.size()).append(" ").append(chkrunlist.size());
              ok = false;
              break;
            } else {
              sb.append(i).append(" *** run not equal size=").append(runs.getRuns().size()).append("\n").
                      append(runs.get(i).toString()).append(" to ").append(chkrunlist.get(i).toString());
              ok = false;
            }
          }
        }
      }
    }     // mutex
    whoHas = 0;
    if (!ok) {
      forceSort();
      reconcileRuns();
    }
    return ok;
  }

  class MiniSeedTimeComparator implements Comparator<MiniSeed> {

    @Override
    public synchronized int compare(MiniSeed o1, MiniSeed o2) {
      if (o1 != null && o2 != null) {
        if (Util.compareTo(o1.getSeedNameSB(), o2.getSeedNameSB()) != 0) {
          prta(" ****Seedname not same o1=" + o1.getSeedNameSB() + "|" + o2.getSeedNameSB() + "|");
          new RuntimeException("Seedname not same o1=" + o1.getSeedNameSB() + "|" + o2.getSeedNameSB() + "|").printStackTrace(par.getPrintStream());
          return Util.compareTo(o1.getSeedNameSB(), o2.getSeedNameSB());
        }
        if (o1.getTimeInMillis() > o2.getTimeInMillis()) {
          return 1;
        } else if (o1.getTimeInMillis() < o2.getTimeInMillis()) {
          return -1;
        } else {
          return 0;
        }
      } else {
        prta("***** Comparator had a null! ");
        if (o1 == null && o2 == null) {
          return 0;
        } else if (o1 != null && o2 == null) {
          return -1;
        } else if (o1 == null && o2 != null) {
          return 1;
        }
      }
      return 0;
    }

    @Override
    public boolean equals(Object obj) {
      if (obj instanceof MiniSeed) {
        if (obj == this) {
          return true;
        }
      }
      return false;
    }

    @Override
    public int hashCode() {
      int hash = 3;
      return hash;
    }

  }
}
