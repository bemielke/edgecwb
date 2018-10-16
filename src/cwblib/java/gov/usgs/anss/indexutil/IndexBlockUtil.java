/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */

/*
 * IndexBlock.java
 *
 * Created on March 28, 2006, 1:12 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package gov.usgs.anss.indexutil;
import gov.usgs.anss.edge.MasterBlock;
import gov.usgs.anss.edge.IndexBlock;
import java.nio.ByteBuffer;
import java.util.BitSet;
import gov.usgs.anss.util.*;
import gov.usgs.anss.edge.*;

/** 
 * This class wraps a IndexBlock or IndexBlockCheck.  These blocks are identical except
 * for byte 12-20 which contain different things in the two types.  This class alway 
 * treats these 8 bytes like they came from an index block (nextIndex, updateTime).
 * The fromIdxBlock() translates these two items on intput, but the toBuf leave the
 * bytes in the input data buffer undisturbed.  So Index and IndexChecks can both be
 * gotten from toBuf safely.
 *
 * @author davidketchum
 */
public class IndexBlockUtil{
 
  // The ctl buf is the first block and contains pointers to the master blocks

  // Index block storage
  private final byte [] idxbuf = new byte[512];     // array for index block reads
  private final ByteBuffer idxbb;
  public StringBuilder seedName = new StringBuilder(12);        // Exactly 12 characters long!
  public int nextIndex;          // If -1, no next index (this is the last one!)
  public int updateTime;         // seconds since midnight 1979
  public final int startingBlock[] = new int[IndexBlock.MAX_EXTENTS];
  public final long bitMap[] = new long[IndexBlock.MAX_EXTENTS];
  public final short earliestTime[] = new short[IndexBlock.MAX_EXTENTS];
  public final short latestTime[] = new short[IndexBlock.MAX_EXTENTS];
  public int iblk;
  boolean dbg;
  private final byte [] namebuf = new byte[12];

  /** set debug state
   *@param t The new debug state */
  public void setDebug(boolean t) {dbg=t;}
  public void clear() { // this looks to be left over from when GC was not releasing objects.
    //startingBlock=null; bitMap=null; earliestTime=null; latestTime=null; idxbb=null; idxbuf=null;
  }
  /** return the status string for this thread 
   *@return A string representing status for this thread */
  public String getStatusString() {
    return "IndexBlockUtil Status N/A";
  }
  @Override
  public String toString() {
    return toStringBuilder(null).toString();
  }
  public  StringBuilder toStringBuilder(StringBuilder tmp) {
    StringBuilder sbt = tmp;
    if(sbt == null) sbt = Util.clear(sb);
    synchronized(sbt) {
      if(nextIndex > 1000000) sbt.append(seedName).append(" chk format iblk=").append(iblk).append("\n");
      else sbt.append(seedName).append(" next=").append(nextIndex).append(" iblk=").append(iblk).append("\n");
      for(int i=0; i<IndexBlock.MAX_EXTENTS; i++) sbt.append(Util.leftPad(i,2)).append(" ").
              append(Util.leftPad(startingBlock[i],8)).append(" ").
              append(Util.leftPad(Util.toHex(bitMap[i]),10)).append("\n");
    }
    return sbt;
  }
  
   /** creates an new instance of IndexBlockUtil which is used to compare index blocks and check blocks - which
   * host/port source of data.  This one gets its arguments from a command line
   * @param bf Buffer with an index block
   * @param blk The block number
   * @throws gov.usgs.anss.edge.IllegalSeednameException
   */
  public IndexBlockUtil(byte [] bf, int blk) throws IllegalSeednameException {
    idxbb = ByteBuffer.wrap(idxbuf);
    reload(bf, blk);

  }
  public final synchronized void reload(byte [] bf, int blk) throws IllegalSeednameException {
    System.arraycopy(bf,0, idxbuf, 0, 512);
    fromIdxbuf();
    iblk=blk;
    MasterBlock.checkSeedName(seedName);
    
  }
  public boolean isOK(int max) {
    try {
      MasterBlock.checkSeedName(seedName);
    }
    catch(IllegalSeednameException e) {return false;}
    return iblk >= 0 && iblk <= max;
  }
  private final StringBuilder sb = new StringBuilder(500);
  
  public synchronized StringBuilder dumpBufs(IndexBlockUtil chk) {
    Util.clear(sb);
    if(chk == null) return sb.append("Chk buf is null in dumpBufs()");
    sb.append(seedName).append("|").append(chk.seedName).append("| iblk=").append(iblk).
            append(" nxt=").append(nextIndex).append(" chkiblk=").append(chk.iblk).
            append(Util.stringBuilderEqual(seedName,chk.seedName) ? "rqst\n" : "rqst *****\n");
    for(int i=0; i<IndexBlock.MAX_EXTENTS; i++ ) {
      if(startingBlock[i] != -1 || chk.startingBlock[i] != -1)
        if((startingBlock[i] != chk.startingBlock[i] || bitMap[i] != chk.bitMap[i]))
          sb.append(Util.rightPad(i,4)).append(" ").append(Util.leftPad(startingBlock[i], 8)).
                  append(" ").append(Util.leftPad(chk.startingBlock[i],8)).append(" ").
                  append(Util.leftPad(Util.toHex(bitMap[i]),18)).append(" ").
                  append(Util.leftPad(Util.toHex(chk.bitMap[i]),18)).append(" ").
                  append(Util.rightPad(Util.toHex(bitMap[i] ^ chk.bitMap[i]), 18)).
                  append((startingBlock[i] == chk.startingBlock[i] && bitMap[i] == chk.bitMap[i]) ? "rqst\n" : " rqst ***\n");
    }
    return sb;
  }
  /* a check block and a index block are equivalent, return list of blocks missing in
   * the first extent which does not matched. This is always performed on blocks which are not
   * the very last extent for the channel (the one that should be being updated currently).  The
   * strict flag can be turned on to run the last extent also through this process.  Normally the
   * the user should set strict when the file is expected to be complete (say after the day has been
   * over for some time).  Used by IndexChecker thread to build requests
   * of missing blocks.
   *@param chk The check block against which to run this check
   *@param missing array of at least 67 ints [0] returns with index blcok, 
   *    [1] with extent index, [2] with # blocks missing, [3-n] the list of missing blocks
   *@param strict The blocks should compare exactly (no last extent differences tolerated)
   *@return true if the block checks out, false if missing has been filled out
   */
  public void setDataBits(BitSet b) {
    for(int i=0; i<IndexBlock.MAX_EXTENTS; i++) {
      if(startingBlock[i] < 0) break;
      for(int j=0; j<64; j++) 
        if( (bitMap[i] & (1L << j)) != 0) b.set(startingBlock[i]+j);
    }
  }
  public synchronized boolean compareBufsChecker(IndexBlockUtil chk, int [] missing, boolean strict,
      StringBuilder sb) {
    //boolean dbg=false;
    boolean ok=true;
    if(chk == null) 
      return false;
    if(!Util.stringBuilderEqual(seedName,chk.seedName)) 
      return false;
    try {
      //if(strict) Util.prta("Strict is on");
      for(int i=0; i<IndexBlock.MAX_EXTENTS; i++) {
        boolean veryLastExtent = nextIndex == -1 &&                    // Is this the last index block for this component
                (i == (IndexBlock.MAX_EXTENTS -1) || // Is i the last extent in this block
                startingBlock[Math.min(IndexBlock.MAX_EXTENTS-1,i+1)] == -1);

        if(strict || !veryLastExtent) {   // It must match exactly
          boolean doall =false;
          if(startingBlock[i] >= 0) {       // this extent has data
            long mask = bitMap[i] ^ chk.bitMap[i];   // this is all the bits up and not matching
            mask = mask & bitMap[i];                 // It has to be in the index to be counted
            if(mask != 0) {       // There are no bits left to count
              boolean nextLast = startingBlock[Math.min(IndexBlock.MAX_EXTENTS-1,i+2)] == -1;
              sb.append(iblk).append("/").append(i).append(" ").append(seedName).append(" strict=").
                      append(strict?"t":"f").append(" lstExt=").append(veryLastExtent?"t":"f").
                      append(" ").append(nextLast?"t":"f").append(" ").
                      append(IndexBlock.timeToString(earliestTime[i],null)).append(" ").
                      append(IndexBlock.timeToString(latestTime[i],null)).append(" ").
                      append(iblk).append("/").append(nextIndex).append(" ").append(i).append(" ").
                      append(Util.toHex(bitMap[i])).append(" ").append(Util.toHex(chk.bitMap[i])).
                      append(" rqst msk=").append(Util.toHex(mask)).append("\n");
              long bit=1;
              missing[0]=iblk;
              missing[1]=i;
              int n = 3;
              for(int blk=0; blk<64; blk++) {
                // if it is on the xor list and the bit is set on the index(i.e. not on the chk)
                if( (mask & bit) != 0) missing[n++]= startingBlock[i] + blk;
                bit = bit << 1;
              }
              missing[2] = n - 3;
              //if(dbg) Util.prt("BitMap do not match at "+i+"\n"+toString()+chk.toString());
              return false;
            }
          }
          if(doall) {
            sb.append(iblk).append(" ").append(seedName).append(" strict=").append(strict).
                    append(" lstExt=").append(veryLastExtent).append(" ").
                    append(IndexBlock.timeToString(earliestTime[i],null)).append(" ").
                    append(IndexBlock.timeToString(latestTime[i],null)).append(" " + " starting block missmatch do 64 ").
                    append(startingBlock[i]).append(" ").append(chk.startingBlock[i]).append("\n");
            if(startingBlock[i] <= -1) continue;// This should never happen, index=-1 but
            for(int blk=0; blk<64; blk++) missing[blk+3]=startingBlock[i]+blk;
            missing[0]=iblk;
            missing[1]=i;
            missing[2]=64;
            return false;
          }
        }
      }
    }
    catch(RuntimeException e) {
      e.printStackTrace();
    }
    return true;
  }
  // a check block and a index block are equivalent if the names and bitmaps are the same!
  public boolean compareBufs(IndexBlockUtil chk) {
    //boolean dbg=false;
    if(chk == null) 
      return false;
    if(!Util.stringBuilderEqual(seedName, chk.seedName)) 
      return false;
    for(int i=0; i<IndexBlock.MAX_EXTENTS; i++) {
      // Does the extent match up, if not we will need all of the extent!
      if(bitMap[i] != chk.bitMap[i]) {
        // If this is the extent currently in use, do not check it - wait for it to fill
        if(nextIndex == -1 && i == IndexBlock.MAX_EXTENTS-1) return true;// last extent current
        return nextIndex == -1 && startingBlock[i+1] == -1;
      }
      if(startingBlock[i] != chk.startingBlock[i]) {
        //Util.prt("Starting block do not match at "+i+"\n"+toString()+chk.toString());
        return false;
      }
    }
    return true;
  }
  public StringBuilder getReason() {return compareReason;}
  private final StringBuilder compareReason = new StringBuilder(100);
  
  /**
   * 
   * @param chk The block to compare to this one
   * @return  ture if the bitmaps are the same and all extents are full
   */
  public boolean compareBufsComplete(IndexBlockUtil chk) {
    synchronized(compareReason) {
      while(true) {
        try {
          Util.clear(compareReason);
          if(chk == null) {compareReason.append("chk is null");return false;}
          if(!Util.stringBuilderEqual(seedName, chk.seedName)) {
            compareReason.append("seed names not same ").append(seedName).append("|").append(chk.seedName).append("|");
            return false;
          }
          for(int i=0; i<IndexBlock.MAX_EXTENTS; i++) {
            if(bitMap[i] != chk.bitMap[i] || bitMap[i] != -1) {
              compareReason.append("Bit map at i=").append(i).append(" not same or -1 ").
                      append(Util.toHex(bitMap[i])).append(" ").append(Util.toHex(chk.bitMap[i]));
              return false;
            }
          }
          break;
        }
        catch(RuntimeException e) {
          Util.prt("compareBufsComplete runtime() continue e="+e);
          e.printStackTrace();
        }
      }   // until we break out knowing the answer
    }
    return true;
  }
  /** recover the data from a binary buffer (like after its read in!)*/
  private synchronized void fromIdxbuf() {
    idxbb.clear();
    idxbb.get(namebuf);
    Util.clear(seedName);
    for(int i=0; i<12; i++) seedName.append((char) namebuf[i]);
    //seedName=new String(namebuf);
    nextIndex=idxbb.getInt();
    updateTime=idxbb.getInt();
    for(int i=0; i<IndexBlock.MAX_EXTENTS; i++) {
      startingBlock[i]=idxbb.getInt();
      bitMap[i]= idxbb.getLong();
      earliestTime[i]=idxbb.getShort();
      latestTime[i]=idxbb.getShort();
    }
  }
  /** convert the internal data to the byte buffer for writing 
   *<br> SeedName (12 char)
   *<br> node (4 bytes)
   *<br> julian (int)
   *<br> startingBlock (int), bitmap (long), earliestTime (short), latestTime (short)
   *<br> Repeat last MAX_EXTENTS times. 
   * @return The buffer representing this IndexBlock
   */
  public synchronized byte [] toBuf() {
    idxbb.clear();
    idxbb.position(0);
    for(int i=0; i<12; i++) idxbb.put((byte) seedName.charAt(i));
    //idxbb.put(seedName.getBytes());
    idxbb.position(20);
    for(int i=0; i<IndexBlock.MAX_EXTENTS; i++) 
      idxbb.putInt(startingBlock[i]).putLong(bitMap[i]).putShort(earliestTime[i]).putShort(latestTime[i]);
    return idxbuf;
  }
 
}
