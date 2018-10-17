/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.edgeoutput;

import gov.usgs.anss.edgethread.EdgeThread;
import gov.usgs.anss.util.Util;
import java.util.ArrayList;

/**
 * This class manages a pool of miniseed blocks allowing them to be allocated (and created if the
 * pool is empty), and marked free. The CWBQuery uses this so that new MiniSeed blocks to not have
 * to be constantly created and destroyed which excites the garbage collector to no end.
 *
 * @author U.S. Geological Survey <ketchum at usgs.gov>
 */
public final class TraceBufPool {

  private final ArrayList<TraceBuf> used = new ArrayList<>(10);
  private final ArrayList<TraceBuf> freeList = new ArrayList<>(10);
  private int tbSize;
  private final byte[] b;
  private final EdgeThread par;
  private boolean dbg;

  public void setDebug(boolean t) {
    dbg = t;
  }

  public int getUsed() {
    return used.size();
  }

  public int getFree() {
    return freeList.size();
  }

  /**
   * Trim the freelist back to the given size
   *
   * @param size The number of blocks to leave on the free list
   */
  public synchronized void trimList(int size) {
    for (int i = freeList.size() - 1; i > size; i--) {
      TraceBuf ms = freeList.get(i);
      ms.clear();
      freeList.remove(i);
    }
  }

  public void prt(String s) {
    if (par == null) {
      Util.prt(s);
    } else {
      prt(s);
    }
  }

  public void prta(String s) {
    if (par == null) {
      Util.prta(s);
    } else {
      prta(s);
    }
  }

  /**
   * A TraceBuf pool creates TraceBufs and allows callers to allocate one or free one. All of the
   * TraceBufs are managed on the free list and used list
   *
   * @param initSize Number of trace bufs to initially allocate
   * @param tbSz The size in bytes of the tracebuf buffers
   * @param parent A logging thread if desired.
   */
  public TraceBufPool(int initSize, int tbSz, EdgeThread parent) {
    tbSize = tbSz;
    par = parent;
    if (tbSize <= 0) {
      tbSize = TraceBuf.UDP_SIZ;
    }
    b = new byte[tbSize];
    for (int i = 0; i < initSize; i++) {
      freeList.add(new TraceBuf(b));
    }
  }

  /**
   * get a tracebuf
   *
   * @return A trace buf allocated to user - do not forget to free() it.
   */
  public synchronized TraceBuf get() {
    if (freeList.isEmpty()) {
      freeList.add(new TraceBuf(b));
      if (dbg) {
        prta("TBP: need another tracebuf " + toString());
      }
    }
    TraceBuf bf = freeList.get(freeList.size() - 1);
    freeList.remove(freeList.size() - 1);
    used.add(bf);
    return bf;
  }

  /**
   * Free the given TraceBuf
   *
   * @param tb The TraceBuf to free
   */
  public synchronized void free(TraceBuf tb) {
    if (tb == null) {
      return;
    }
    for (int i = used.size() - 1; i >= 0; i--) {
      if (tb == used.get(i)) {
        if (used.size() % 10 == 1 && dbg) {
          prta("TBP: free block i=" + i + " " + toString());
        }
        freeList.add(tb);
        used.remove(i);
        return;
      }
    }
    prta("TBP: ***** attempt to free a block that is not in use!");
  }

  @Override
  public String toString() {
    return "TraceBufPool: used=" + used.size() + " free=" + freeList.size()
            + " " + ((used.size() + freeList.size()) * tbSize / 1000) + "kB";
  }
  private StringBuilder sbtmp;

  public StringBuilder toStringBuilder(StringBuilder sbin) {
    StringBuilder sb = sbin;
    if (sb == null) {
      if (sbtmp == null) {
        sbtmp = new StringBuilder(100);
      }
      sb = sbtmp;
    }
    synchronized (sb) {
      sb.append("MiniSeedPool: nused=").append(used.size()).append(" free=").
              append(freeList.size()).append(" ").append((used.size() + freeList.size()) * 1024L / 1024).append("kB");
    }
    return sb;
  }
}
