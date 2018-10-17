/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */

package gov.usgs.anss.seed;

import gov.usgs.anss.edge.IllegalSeednameException;
import gov.usgs.anss.edgethread.EdgeThread;
import gov.usgs.anss.util.Util;
import java.util.ArrayList;

/** This class forms a pool of miniseed blocks which can be allocated and freed, but
 * not run through the garbage collector.  Used mainly in CWBQuery so blocks are not
 * constantly built into new MiniSeed objects and them reaped.
 *
 * @author U.S. Geological Survey <ketchum at usgs.gov>
 */
public class MiniSeedPool {
  private final ArrayList<MiniSeed> used = new ArrayList<>(10);
  private final ArrayList<MiniSeed> freeList = new ArrayList<>(10);
  private boolean dbg;
  private int highwater;
  private EdgeThread par;
  private long nfree;
  private long nfreeOps;
  private long nget;
  public void prt(String s) {if(par == null) Util.prt(s); else par.prt(s);}
  public void prta(String s) {if(par == null) Util.prta(s); else par.prta(s);}
  public void setDebug(boolean t) {dbg=t;}
  public int getUsed() {return used.size();}
  public int getFree() {return freeList.size();}
  /** return highest number of blocks on the list
   * @return  The high water mark
   */
  public int getHighwater() {return highwater;}
  public synchronized void trimList(int size) {
    for(int i=freeList.size()-1; i>size; i--) {
      MiniSeed ms = freeList.get(i);
      ms.clear();
      freeList.remove(i);
    }
  }
  /** return the array of Used blocks - not normally used by user
   * @return The used list - user should not modify!
   */
  public ArrayList<MiniSeed> getUsedList() {return used;}
  /** return the array of free blocks - no normally needed by users
   * @return  the free list - user should not modify
   */
  public ArrayList<MiniSeed> getFreeList() {return freeList;}
  /**
   *  Mark all blocks as free
   */
  public synchronized void freeAll() {
    for(int i=used.size()-1; i>=0; i--) {
      nfreeOps++;
      freeList.add(used.get(i));
      used.remove(i);
      nfree++;
    }
  }
  public MiniSeedPool() {

  }
  public MiniSeedPool(EdgeThread parent) {
    par = parent;
  }
  /** return a pool block loaded with the buf 
   * @param buf Buffer with miniseed data
   * @param off Offset of first byte to convert
   * @param len Length of block
   * @return The MiniSeed block
   */
  public synchronized MiniSeed get(byte [] buf, int off, int len) {
    if(freeList.isEmpty()) {
      try {
        freeList.add(new MiniSeed(buf, off,len));
      }
      catch(IllegalSeednameException e) {
        prta("MSP: get() Illegal seedname? "+e);
        e.printStackTrace();
      }
      if(dbg)
        prta("MSP: need another MiniSeed "+toString());
    }
    
    MiniSeed bf = freeList.get(freeList.size()-1);
    if(bf.getBlockSize() < len) {
      try {
        bf = new MiniSeed(buf, off, len);
      }
      catch(IllegalSeednameException e) {
        prta("MSP: get()2 Illegal seedname? "+e);
        e.printStackTrace();
        return null;     // Cannot add an illegal seedname
      }
      freeList.set(freeList.size()-1, bf);
    }
    try{
      bf.load(buf, off, len);
    }
    catch(IllegalSeednameException e) {
        prta("MSP: get()3 Illegal seedname? "+e);
        e.printStackTrace();
        return null;
    } 
    freeList.remove(freeList.size()-1);
    used.add(bf);
    if(dbg) prta("get "+bf);
    nget++;
    return bf;
  }
    /** return a pool block loaded with the buf 
   * @param ms The miniseed block to load into the miniseed list
   * @return The MiniSeed block
   */
  public synchronized MiniSeed get(MiniSeed ms) {
    if(freeList.isEmpty()) {    // No free blocks, then create one
      try {
        freeList.add(new MiniSeed(ms.getBuf(), 0, ms.getBuf().length));
      }
      catch(IllegalSeednameException e) {
        prta("MSP: get()4 Illegal seedname? "+e);
        e.printStackTrace();
      }
      if(dbg)
        prta("MSP: need another MiniSeed "+toString());
    }
    
    MiniSeed bf = freeList.get(freeList.size()-1);  // Get the last block from the freeList
    if(bf.getBuf().length < ms.getBuf().length) { // is the size o.k.?
      try {
        bf = new MiniSeed(ms.getBuf(), 0, ms.getBuf().length);  // No make a bigger one
      }
      catch(IllegalSeednameException e) {
        prta("MSP: get()5 Illegal seedname? "+e);
        e.printStackTrace();
      }
      
      freeList.set(freeList.size()-1, bf);  // Put the bigger one on the freelist
    }
    try{
      bf.load(ms);
    }
    catch(IllegalSeednameException e) {
        prta("MSP: get( )6Illegal seedname? "+e);
        e.printStackTrace();
        return null;
    } 
    freeList.remove(freeList.size()-1);
    used.add(bf);
    if(dbg) prta("get "+bf);
    nget++;
    return bf;
  }
  /** Free this miniseed block - put it on the free list
   *  
   * @param tb The MiniSeed block to free
   * @return  true if it was on used list and now free
   */
  public synchronized boolean free(MiniSeed tb) {
    for(int i=used.size()-1; i>=0; i--) {   // work in reverse since that is likely where the block is
      nfreeOps++;
      if(tb == used.get(i)) {
        if(used.size() % 10 == 1 && dbg) prta("MSP: free block i="+i+" "+tb+" "+toString());
        freeList.add(tb);
        used.remove(i);
        nfree++;
        if(freeList.size() > highwater) highwater=freeList.size();
        return true;
      }
    }
    prta("MSP: **** attempt to free a block that is not in use!");
    new RuntimeException("MSP *** attempt to free a block that is not in use!").
            printStackTrace(par == null?Util.getOutput():par.getPrintStream());
    return false;
  }
  @Override
  public String toString() {return "MiniSeedPool: nget="+nget+" nfree="+nfree+"/"+nfreeOps+" used="+used.size()+" free="+freeList.size()+
          " "+((used.size()+freeList.size())*1024L/1024)+"kB";}
  private StringBuilder sbtmp;
  public StringBuilder toStringBuilder(StringBuilder sbin) {
    StringBuilder sb = sbin;
    if(sb == null) {
      if(sbtmp == null) sbtmp = new StringBuilder(100);
      sb=sbtmp;
    }
    sb.append("MiniSeedPool: nget=").append(nget).append(" nfree=").append(nfree).append("/").
            append(nfreeOps).append(" used=").append(used.size()).append(" free=").
            append(freeList.size()).append(" ").append((used.size()+freeList.size())*1024L/1024).append("kB");
    return sb;
  }
}
