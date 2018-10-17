/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */

/*
 * RebuildCheck.java
 *
 * Created on March 4, 2008, 11:20 AM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package gov.usgs.anss.indexutil;
import gov.usgs.anss.edge.MasterBlock;
import gov.usgs.anss.edge.IndexFile;
import gov.usgs.anss.edge.IndexFileReplicator;
import gov.usgs.anss.edge.IndexBlock;
import java.io.*;
import gov.usgs.anss.edge.*;
import gov.usgs.anss.util.*;
import gov.usgs.anss.seed.MiniSeed;
import java.nio.*;
import java.util.*;

/** This class takes a .ms, .idx and .chk and insures that the .chk is up-to-date by
 * comparing it against its index, and if not in agreement, reading the data blocks
 * and setting any .chk bits that need to be set.  It also looks for bits in idx
 * that data indicate should be set in cases where chk and idx bit masks disagree.
 * I also sets the seednames to agree.  This largely happened before Mar 14, 2008 do
 * to some bugs in the index block creator/Replicator and should not happen in the
 * future.  
 *
 * @author davidketchum
 */
public class RebuildCheck {
  private RawDisk rw;
  private RawDisk chk;
  private RawDisk dataFile;
  private IndexFileReplicator idx;
  BitSet blkset;
 
  // The ctl buf is the first block and contains pointers to the master blocks
  byte [] ctlbuf;   // The buffer wrapping the ctl ByteBuffer
  ByteBuffer ctl;  // The header portion of this file
  int length;       // first bytes of ctl buf
  int next_extent;  // next place to write data from the ctl buf
  short next_index; // next index block to use from the ctl buf
  short mBlocks[];  // array of blocks currently master_blocks, 0=unallocated
  int blocksRequested;// Total count of blocks requested.
  int lastBlocksRequested;// for status, get differention counter
  long lastStatus;  // time of last status
  
  // These are the master blocks as master Block objects built by reading them in
  MasterBlock mb[];

  // Index block storage
  byte [] idxbuf ;     // array for index block reads
  byte [] chkbuf; 
  boolean detail;
  public RebuildCheck(String filestub, boolean det, boolean fix) {
    if(filestub.contains(".")) filestub = filestub.substring(0, filestub.indexOf("."));
    int expectedDOY;
    if(filestub.contains("#")) expectedDOY=Integer.parseInt(filestub.substring(filestub.length() -7, filestub.length() -4));
    else expectedDOY=Integer.parseInt(filestub.substring(filestub.length() -5, filestub.length() -2));
    ctlbuf = new byte[512];
    detail=det;
    ctl = ByteBuffer.wrap(ctlbuf);
    idxbuf = new byte[512];
    chkbuf = new byte[512];
    mBlocks = new short[IndexFile.MAX_MASTER_BLOCKS];   // space for master block numbers
    mb = new MasterBlock[IndexFile.MAX_MASTER_BLOCKS];  // space for master block objects
    for(int i=0; i<IndexFile.MAX_MASTER_BLOCKS; i++) {
      mb[i]=null;
      mBlocks[i]=0;  
    }
    try {
      rw = new RawDisk(filestub+".idx","rw");
      chk = new RawDisk(filestub+".chk","rw");
      dataFile = new RawDisk(filestub+".ms","r");
    }
    catch(FileNotFoundException e) {
      Util.prt("File not found for filestub="+filestub);
      System.exit(1);
    }    
    try {
      blkset = new BitSet(Math.min((int) (rw.length()/512), 4000));
    }
    catch(IOException e) {
      Util.IOErrorPrint(e," Building a bitset in IndexChecker");
    }
    readMasterBlocks();
    int nbad=0;
    IndexBlockUtil idxblk=null;
    IndexBlockUtil chkblk=null;
    int nseednames=0;
    int nstartingblocks=0;
    int nsets=0;
    int nunsets=0;
    int nidxset=0;
    int nindexbad=0;
    int n80=0;
    byte [] data = new byte[64*512];
    for(int imast=0; imast<IndexFile.MAX_MASTER_BLOCKS; imast++) {
      if(mBlocks[imast] == 0) break;          // done, no more master blocks to check
      for(int ii=0; ii<MasterBlock.MAX_CHANNELS; ii++) {
        if(mb[imast].getSeedName(ii).substring(0,1).equals(" ")) break;   // no more names
        int iblk = mb[imast].getFirstIndexBlock(ii);
        if(mb[imast].getSeedName(ii).substring(7,10).equals("ACE") || 
           mb[imast].getSeedName(ii).substring(7,10).equals("OCF")) continue;
        //prt("Check "+mb[imast].getSeedNameString(i)+" First index="+iblk);
        boolean idxModified=false;
        boolean chkModified=false;
        while(iblk > 0) {
          try {
            rw.readBlock(idxbuf, iblk, 512);
            idxblk = new IndexBlockUtil(idxbuf,iblk);
            chk.readBlock(chkbuf, iblk, 512);
            // check for % in the name, if present fix them to AAAAAAAAAAA
            if(chkbuf[8] == '%' && chkbuf[13] == '%' && chkbuf[18] == '%' && chkbuf[15] == '%') {
              Util.prt("Found a %%%%%%%%%%%% change to AAAAAAAAAAAAA");
              for(int i=0; i<2; i++) chkbuf[i+18]='A';
              for(int i=0; i<2; i++) chkbuf[i+13]='A';
              for(int i=0; i<5; i++) chkbuf[i+8]='A';
              for(int i=0; i<3; i++) chkbuf[i+15]='A';
            }
            chkblk= new IndexBlockUtil(chkbuf,iblk);
            if(idxblk.compareBufsComplete(chkblk)) {
              blkset.set(iblk);
            } 
            // the block is not complete,
            else {
              if(!idxblk.seedName.equals(chkblk.seedName)) {
                //if(detail) 
                  Util.prt(iblk+" Seednames do not agree "+idxblk.seedName+"|"+chkblk.seedName+"|");
                if(Util.stringBuilderEqual(chkblk.seedName,"REQUESTEDBLK")) chkblk.seedName = idxblk.seedName;
                else {
                  int nused=0;
                  int nmatch=0;
                 // Util.prt(idxblk.toString()+"\n"+chkblk.toString());
                  for(int i=0; i<IndexBlock.MAX_EXTENTS; i++) {
                    if(idxblk.startingBlock[i] < 0 ) {nused=i; break;}
                    if(idxblk.startingBlock[i] == chkblk.startingBlock[i]) nmatch++;
                  }
                  if(nmatch*100/Math.max(1,nused) > 80) {
                    chkblk.seedName = idxblk.seedName;
                    chkModified=true;
                    n80++;
                  }
                }
                nseednames++;
              }
              // Now look for differences in the bitmaps and pointers
              for(int i=0; i<IndexBlock.MAX_EXTENTS; i++) {
                if(idxblk.startingBlock[i] != chkblk.startingBlock[i]) {
                  if(i < IndexBlock.MAX_EXTENTS-1) {
                    if(idxblk.startingBlock[i+1] == -1) 
                      continue;     // This is a last extent for the blk
                  }
                  if(idxblk.nextIndex < 0) 
                    continue;        // This is the last extent and there is no next block
                  if(detail) Util.prt(iblk+"/"+i+" starting blocks disagree "+idxblk.seedName+" "+idxblk.startingBlock[i]+"!="+chkblk.startingBlock[i]);
                  chkblk.startingBlock[i] = idxblk.startingBlock[i];
                  chkModified=true;
                  nstartingblocks++;
                }
                if(idxblk.bitMap[i] != chkblk.bitMap[i]) {
                  if(detail) Util.prt(iblk+"/"+i+" bit maps disagree "+idxblk.seedName+" "+Util.toHex(idxblk.bitMap[i])+"!="+Util.toHex(chkblk.bitMap[i]));
                  int l = dataFile.readBlock(data, idxblk.startingBlock[i], 64*512);
                  if(l != 64*512) Util.prt("   Did not return all of the data l="+l);
                  for(int j=0; j<64; j++) {
                    boolean isZero=true;
                    if(data[j*512] != 0 || data[j*512+1] != 0 || data[j*512+2] != 0 || data[j*512+3] != 0 ||
                      data[j*512+4] != 0 || data[j*512+5] != 0 ) isZero=false;
                    MiniSeed ms = null;
                    try {
                      ms = new MiniSeed(data, j*512, 512);
                    }
                    catch(IllegalSeednameException e) {}
                    if( (idxblk.bitMap[i] & 1L<<j) != 0) {    // The index thinks the block is here
                      if(isZero) {
                        if( (chkblk.bitMap[i] & 1L<<j) != 0) {
                          long before=chkblk.bitMap[i];
                          if(detail) Util.prt("   ***** "+iblk+"/"+i+"/"+j+" "+idxblk.seedName+" index&chk has data bit but data is zero ms="+ms);
                          chkblk.bitMap[i] &= ~(1L<<j);
                          chkModified=true;
                          nunsets++;
                          if(detail) Util.prt("   bef="+Util.toHex(before)+" aft="+Util.toHex(chkblk.bitMap[i]));
                        }
                      }
                      else {
                        if( (chkblk.bitMap[i] & 1L <<j) != 0) { // Check block says data
                          //Util.prt("   "+iblk+"/"+i+"/"+j+" "+idxblk.seedName+" is o.k.");
                        }
                        else {
                          if(detail) Util.prt("    "+iblk+"/"+i+"/"+j+" "+idxblk.seedName+" need to set chk blk bit");
                          chkblk.bitMap[i] |= 1L<<j;
                          chkModified=true;
                          nsets++;
                        }
                      }
                    }
                    else {  // The idx bit is not set
                      if(!isZero && detail) {
                        if(ms != null) {
                          if(Util.stringBuilderEqual(ms.getSeedNameSB(),idxblk.seedName) && ms.getDoy() == expectedDOY) {
                            Util.prt("   ***** "+iblk+"/"+i+"/"+j+" "+idxblk.seedName+
                            " index off - data is Good Chk "+((chkblk.bitMap[i] & 1L << j) != 0? "ON":"OFF")+" ms="+ms);
                            nidxset++;
                            idxblk.bitMap[i] |= 1L << j;    // set the bit in the index block
                            idxModified=true;
                          }
                        }
                        else if((chkblk.bitMap[i] & 1L << j) != 0) {
                          Util.prt("   ***** "+iblk+"/"+i+"/"+j+" "+idxblk.seedName+
                            " index off -  Chk ON.  Data is garbled or zero="+isZero);
                        }
                      }
                      nindexbad++;

                    }
                  } // for each block in the extent
                }   // if bit maps to not agree
              }   // For each extent   
            }     // else blocks are not complete
          }
          catch(EOFException e) { // The index file has not been written to full size yet
            Util.IOErrorPrint(e,"Reading in index or chk blkd");
            continue;
          }
          /** this occurs mainly when zero filled blocks are in the index (the blocks got built in the file
           * but never were updated with any data.  It is not a serious error as they will get build eventually*/
          catch(IllegalSeednameException e ) {
            Util.prt(" Illegal seedname on init chk blk="+iblk+" of "+filestub+" "+e.getMessage());
            break;      // no idxblk created, so nothing to do
            //if(getPrintStream() == null) e.printStackTrace();
            //else e.printStackTrace(this.getPrintStream());
          }
          catch(IOException e) {
            Util.IOErrorPrint(e," Reading idx or chk block");
            if(idx.getRawDisk() == null) {
              Util.prta(" IFR has closed.  Terminating....");
            }
          }
          try {
            if(fix && chkModified) chk.writeBlock(iblk, chkblk.toBuf(), 0, 512);
            if(fix && idxModified) rw.writeBlock(iblk, idxblk.toBuf(), 0, 512);
          }
          catch(IOException e) {
            Util.IOErrorPrint(e,"Trying to write back check blocks");
          }
          if(iblk == idxblk.nextIndex) break;   // avoid an invalid infinite loop
          iblk = idxblk.nextIndex;
          //prt("Check "+mb[imast].getSeedNameString(i)+" next index="+iblk);
        } // while iblk > 0
      }   // for next channel in master block
    }     // For each master block
    Util.prt(Util.rightPad(filestub,20)+" #sets="+Util.rightPad(nsets+"",4)+" #unsets="+Util.rightPad(""+nunsets,4)+
        " nseedname="+Util.rightPad(""+nseednames,3)+"/"+Util.rightPad(""+n80,3)+" #startBlk="+Util.rightPad(""+nstartingblocks,4)+
        " #badidxbit="+Util.rightPad(""+nindexbad, 4)+"/"+nidxset+" fix="+fix);
  }
  private boolean readMasterBlocks() {
    try {
      rw.readBlock(ctlbuf, 0, 512);
      fromCtlbuf();
            // for each allocated MasterBlock create it as an object
      blkset.set(0);            // we do not check the control block!
      for(int i=0; i<IndexFile.MAX_MASTER_BLOCKS; i++) 
        if(mBlocks[i] != 0) {
          try {
            mb[i] = new MasterBlock((int) mBlocks[i], rw, false);
            blkset.set((int) mBlocks[i]);        // we do not check index blocks
          }
          catch(IOException e) {
            Util.IOErrorPrint(e," IOError reading MB at "+mBlocks[i]+" i="+i+" len="+(rw.length()/512L)+
                " in ichk idx="+idx.toStringFull());  
            return false;
          }
        }
        else break;                       // exit when we are out of allocated blocks
    }
    catch(IOException e) {
      Util.IOErrorPrint(e," IO error MB control block in IndexChecker idx="+idx.toStringFull());
      return false;
    }
    return true;
  }

  /** from the raw disk block, populate the ctl variables */
  private void fromCtlbuf() {
    ctl.clear();
    length = ctl.getInt();
    next_extent = ctl.getInt();
    next_index = ctl.getShort();
    for(int i=0; i<IndexFile.MAX_MASTER_BLOCKS; i++) {
      mBlocks[i]= ctl.getShort();
    } 
  }
   
  /** Creates a new instance of RebuildCheck */
  public RebuildCheck() {
  }
  public static void main(String [] args) {
    boolean detail=false;
    boolean fix=false;
    if(args.length < 1) {
      Util.prt("Usage : rebuildCheck list of files");
    }
    Util.prt("Start "+args[0]);
    Util.setNoInteractive(true);
    Util.setModeGMT();
    for (String arg : args) {
      if (arg.equals("-dbg")) {
        detail=true; continue;
      }
      if (arg.equals("-fix")) {
        fix=true; continue;
      }
      RebuildCheck chker = new RebuildCheck(arg, detail, fix);
    }
  }
}
