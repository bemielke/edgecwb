/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */

package gov.usgs.anss.indexutil;

import gov.usgs.anss.edge.IllegalSeednameException;
import gov.usgs.anss.edgethread.EdgeThread;
import gov.usgs.anss.util.Util;
import java.util.ArrayList;

/** This class forms a pool of IndexBlockUtil blocks which can be allocated and freed, but
 * not run through the garbage collector.  Used mainly in EdgeMom so blocks are not
 * constantly built into new IndexBlockUtil objects and them reaped in the IndexCheckers.
 * 
 * Adapted from MiniSeedPool July 2014
 *
 * @author U.S. Geological Survey <ketchum at usgs.gov>
 */
public class IndexBlockUtilPool {
  private final ArrayList<IndexBlockUtil> used = new ArrayList<>(10);
  private final ArrayList<IndexBlockUtil> freeList = new ArrayList<>(10);
  private boolean dbg;
  private int highwater;
  private EdgeThread par;
  public void prt(String s) {if(par == null) Util.prt(s); else par.prt(s);}
  public void prta(String s) {if(par == null) Util.prta(s); else par.prta(s);}
  public void setDebug(boolean t) {dbg=t;}
  public int getUsed() {return used.size();}
  public int getFree() {return freeList.size();}
  /** return highest number of blocks on the list
   * @return  The highwater number of blocks
   */
  public int getHighwater() {return highwater;}
  public synchronized void trimList(int size) {
    for(int i=freeList.size()-1; i>size; i--) {
      IndexBlockUtil ms = freeList.get(i);
      ms.clear();
      freeList.remove(i);
    }
  }
  /** return the array of Used blocks - not normally used by user
   * @return  */
  public ArrayList<IndexBlockUtil> getUsedList() {return used;}
  /** return the array of free blocks - no normally needed by users
   * @return  */
  public ArrayList<IndexBlockUtil> getFreeList() {return freeList;}
  /**
   *  Mark all blocks as free
   */
  public synchronized void freeAll() {
    for(int i=used.size()-1; i>=0; i--) {
      freeList.add(used.get(i));
      used.remove(i);
    }
  }
  public IndexBlockUtilPool() {

  }
  public IndexBlockUtilPool(EdgeThread parent) {
    par = parent;
  }
  /** return a pool block loaded with the buf 
   * @param buf Buffer with IndexBlockUtil data
   * @param blk block number
   * @return The IndexBlockUtil block
   */
  public synchronized IndexBlockUtil get(byte [] buf, int blk)  {
    if(freeList.isEmpty()) {
      try {
        freeList.add(new IndexBlockUtil(buf, blk));
      }
      catch(IllegalSeednameException e) {
        prta("IBUP: get() Illegal seedname?"+e);
        e.printStackTrace();
      }
      if(dbg)
        prta("IBUP: need another IndexBlockUtil "+toString());
    }
    
    IndexBlockUtil bf = freeList.get(freeList.size()-1);

    freeList.set(freeList.size()-1, bf);
    try{
      bf.reload(buf, blk);
    }
    catch(IllegalSeednameException e) {
        prta("IBUP: get()2 Illegal seedname? "+e);
        e.printStackTrace();
        return null;
    } 
    freeList.remove(freeList.size()-1);
    used.add(bf);
    if(dbg) prta("get "+bf);
    return bf;
  }
  /** Free this IndexBlockUtil block - put it on the free list
   *  
   * @param tb The IndexBlockUtil block to free
   * @return  true if it was on used list and now free
   */
  public synchronized boolean free(IndexBlockUtil tb) {
    if(tb == null) { 
      new RuntimeException("IBUP *** free called with null block - continue"
              + "").
            printStackTrace(par == null?Util.getOutput():par.getPrintStream());
      return false;
    }
            
    for(int i=used.size()-1; i>=0; i--) {
      if(tb == used.get(i)) {
        if(used.size() % 10 == 1 && dbg) prta("IBUP: free block i="+i+" "+tb+" "+toString());
        freeList.add(tb);
        used.remove(i);
        if(freeList.size() > highwater) highwater=freeList.size();
        return true;
      }
    }
    prta("IBUP: **** attempt to free a block that is not in use! tb=");
    new RuntimeException("IBUP *** attempt to free a block that is not in use!"+tb.toString()).
            printStackTrace(par == null?Util.getOutput():par.getPrintStream());
    return false;
  }
  @Override
  public String toString() {return toStringBuilder(null).toString();}
  private final StringBuilder tmpsb = new StringBuilder(100);
  public StringBuilder toStringBuilder(StringBuilder tmp) {
    StringBuilder sb = tmp;
    if(sb == null) sb = Util.clear(tmpsb); 
    synchronized(sb) {
      sb.append("IndexBlockUtilPool: used=").append(used.size()).append(" free=").append(freeList.size()).
            append(" ").append((used.size()+freeList.size())*1024/1000).append("kB");
    }
    return sb;
  }
}
