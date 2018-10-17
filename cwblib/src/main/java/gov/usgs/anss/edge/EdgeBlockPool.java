/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.edge;

import gov.usgs.anss.edgethread.EdgeThread;
import gov.usgs.anss.util.Util;
import java.util.ArrayList;

/**
 * This class forms a pool of EdgeBlocks which can be allocated and freed, but not run through the
 * garbage collector. Used mainly by EdgeBlockClient for making the IndexFile collection which must
 * be freed when completed.
 *
 * @author U.S. Geological Survey <ketchum at usgs.gov>
 */
public final class EdgeBlockPool {

  private final ArrayList<EdgeBlock> used = new ArrayList<>(10);
  private final ArrayList<EdgeBlock> freeList = new ArrayList<>(10);
  private boolean dbg;
  private int highwater;
  private EdgeThread par;
  private long nfree;
  private long nfreeOps;
  private long nget;

  public void prt(String s) {
    if (par == null) {
      Util.prt(s);
    } else {
      par.prt(s);
    }
  }

  public void prta(String s) {
    if (par == null) {
      Util.prta(s);
    } else {
      par.prta(s);
    }
  }

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
   * return highest number of blocks on the list
   *
   * @return The high water mark
   */
  public int getHighwater() {
    return highwater;
  }

  public synchronized void trimList(int size) {
    for (int i = freeList.size() - 1; i > size; i--) {
      EdgeBlock ms = freeList.get(i);
      freeList.remove(i);
    }
  }

  /**
   * return the array of Used blocks - not normally used by user
   *
   * @return The used list - user should not modify!
   */
  public ArrayList<EdgeBlock> getUsedList() {
    return used;
  }

  /**
   * return the array of free blocks - no normally needed by users
   *
   * @return the free list - user should not modify
   */
  public ArrayList<EdgeBlock> getFreeList() {
    return freeList;
  }

  /**
   * Mark all blocks as free
   */
  public synchronized void freeAll() {
    for (int i = used.size() - 1; i >= 0; i--) {
      nfreeOps++;
      freeList.add(used.get(i));
      used.remove(i);
      nfree++;
    }
  }

  public EdgeBlockPool() {

  }

  public EdgeBlockPool(EdgeThread parent) {
    par = parent;
  }

  /**
   * return a pool block loaded with the buf
   *
   * @param buf Buffer with miniseed data
   * @param seq The sequence to set in the block
   * @return The EdgeBlock block
   */
  public synchronized EdgeBlock get(byte[] buf, int seq) {
    if (freeList.isEmpty()) {
      freeList.add(new EdgeBlock());
      if (dbg) {
        prta("EBP: need another EdgeBlock " + toString());
      }
    }

    EdgeBlock bf = freeList.get(freeList.size() - 1);
    bf.set(buf, seq);

    freeList.remove(freeList.size() - 1);
    used.add(bf);
    if (dbg) {
      prta("get " + bf);
    }
    nget++;
    return bf;
  }

  /**
   * Free this miniseed block - put it on the free list
   *
   * @param tb The EdgeBlock block to free
   * @return true if it was on used list and now free
   */
  public synchronized boolean free(EdgeBlock tb) {
    for (int i = used.size() - 1; i >= 0; i--) {   // work in reverse since that is likely where the block is
      nfreeOps++;
      if (tb == used.get(i)) {
        if (used.size() % 10 == 1 && dbg) {
          prta("EBP: free block i=" + i + " " + tb + " " + toString());
        }
        freeList.add(tb);
        used.remove(i);
        nfree++;
        if (freeList.size() > highwater) {
          highwater = freeList.size();
        }
        return true;
      }
    }
    prta("EBP: **** attempt to free a block that is not in use!");
    new RuntimeException("EBP *** attempt to free a block that is not in use!").
            printStackTrace(par == null ? Util.getOutput() : par.getPrintStream());
    return false;
  }

  @Override
  public String toString() {
    return toStringBuilder(null).toString();
  }
  private final StringBuilder sbtmp = new StringBuilder(100);

  /**
   *
   * @param sbin The user string builder to append to. This is not cleared if it is not null!
   * @return
   */
  public StringBuilder toStringBuilder(StringBuilder sbin) {
    StringBuilder sb = sbin;
    if (sb == null) {
      sb = Util.clear(sbtmp);
    }
    sb.append("EdgeBlockPool: nget=").append(nget).append(" nfree=").append(nfree).append("/").
            append(nfreeOps).append(" used=").append(used.size()).append(" free=").
            append(freeList.size()).append(" ").append((used.size() + freeList.size()) * 1024L / 1024).append("kB");
    return sb;
  }
}
