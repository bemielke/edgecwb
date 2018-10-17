/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */


/*
 * IndexFileUtil.java
 *
 * Created on March 28, 2006, 1:30 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package gov.usgs.anss.indexutil;

import gov.usgs.anss.edge.MasterBlock;
import gov.usgs.anss.edge.IndexFile;
import gov.usgs.anss.edge.EdgeProperties;
import java.util.BitSet;
import java.nio.ByteBuffer;
import java.io.*;
import gov.usgs.anss.util.*;
import gov.usgs.anss.edge.*;
import gov.usgs.anss.seed.MiniSeed;

/** This is a template for an EdgeThread (one that can be run by EdgeMom.  It includes
 * a timeout on the run() and a registered shutdown handler.  
 *
 *At a minimum IndexBlockUtil should be replaced to the new class
 *And IChk replaces with a short hand for the class. (ETT??).
 *
 * @author davidketchum
 */
public class IndexFileUtil{
  private RawDisk rw;
  String filename;
 
  // The ctl buf is the first block and contains pointers to the master blocks
  byte [] ctlbuf;   // The buffer wrapping the ctl ByteBuffer
  ByteBuffer ctl;  // The header portion of this file
  int length;       // first bytes of ctl buf
  int next_extent;  // next place to write data from the ctl buf
  short next_index; // next index block to use from the ctl buf
  public short mBlocks[];  // array of blocks currently master_blocks, 0=unallocated
  int max_block;
  boolean isChkFormat;      // if true, the file is in chk format, not index (preamble is
                            // is different and date fields not present)
  
  // These are the master blocks as master Block objects built by reading them in
  public MasterBlock mb[];
   
  IndexBlockUtil index[];
  // Variables needed for debug and shutdown
  boolean dbg;
  /** set debug state
   *@param t The new debug state */
  public void setDebug(boolean t) {dbg=t;}

  /** return the status string for this thread 
   *@return A string representing status for this thread */
  public String getStatusString() {
    return "IndexFileUtil Status N/A";
  }
  /** is this file in "chk" file format( the preamble of the index is not same format
   * and the date fields are probably not filled out!)
   *@return True if the file is in chk file format
   */
  public boolean isChkFormat() {return isChkFormat;}
  /** set the chk format flag
   *@param t True if this file is to be interpreted in chk file format
   */
  public void setIsChkFormat(boolean t) {isChkFormat=t;}
   /** creates an new instance of LISSCLient - which will try to stay connected to the
   * host/port source of data.  This one gets its arguments from a command line
   * @param file Filename to open like yyyy_ddd_inst
   * @param ro Open read only
   * @throws java.io.FileNotFoundException
   */
  public IndexFileUtil(String file, boolean ro) throws FileNotFoundException, IOException {
    filename=file;
    rw = new RawDisk(filename, (ro ? "r": "rw"));
    isChkFormat=false;
    if(filename.contains(".chk")) isChkFormat=true;
    ctlbuf = new byte[512];
    ctl = ByteBuffer.wrap(ctlbuf);
    mBlocks = new short[IndexFile.MAX_MASTER_BLOCKS];   // space for master block numbers
    mb = new MasterBlock[IndexFile.MAX_MASTER_BLOCKS];  // space for master block objects
    for(int i=0; i<IndexFile.MAX_MASTER_BLOCKS; i++) {
      mb[i]=null;
      mBlocks[i]=0;  
    }
    readMasterBlocks();
    max_block = (int) rw.length()/512;
    index = new IndexBlockUtil[max_block];
    for(int i=0; i<index.length; i++) index[i]= null;
    byte [] buf = new byte[512];
    if(rw.length()/512 > next_index) 
      Util.prta("Index file is longer than next_index="+next_index+" Len="+rw.length()/512);
    for(int i=1; i<next_index; i++) {
      try {
        boolean skip=false;
        for(int j=0; j<IndexFile.MAX_MASTER_BLOCKS; j++) if(mBlocks[j] == i) {skip=true; break;}
        if(skip) continue;
        rw.readBlock(buf, i, 512);
        index[i] = new IndexBlockUtil(buf,i);
        if(!index[i].isOK(max_block)) {
          Util.prta("index at blk="+i+" is not o.k.");
          index[i] = null;
        }
      }
      catch(IOException e) {
        Util.prta("IOException reading in index blocks iblk="+i);
      }
      catch(IllegalSeednameException e) {
        Util.prta("Illegal seedname in block="+i+" "+filename+" "+e.getMessage());
        Util.prt(buf[8]+" "+buf[9]+" "+buf[10]+" "+buf[11]+" "+buf[12]+" "+buf[13]+" "+buf[14]+" "+
            buf[15]+" "+buf[16]+" "+buf[17]+" "+buf[18]+" "+buf[19]);
        index[i]=null;
      }
    }
  }
 
  private void readMasterBlocks() {
    try {
      rw.readBlock(ctlbuf, 0, 512);
      fromCtlbuf();
            // for each allocated MasterBlock create it as an object
      for(int i=0; i<IndexFile.MAX_MASTER_BLOCKS; i++) 
        if(mBlocks[i] != 0) {
          mb[i] = new MasterBlock((int) mBlocks[i], rw, false);
        }
        else break;                       // exit when we are out of allocated blocks
    }
    catch(IOException e) {
      Util.IOErrorPrint(e,"IO error reading Master blocks in IndexBlockUtil");
    }
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
  public BitSet getDataBitSet() {
    BitSet b = new BitSet(10000000);
    for(int i=0; i<IndexFile.MAX_MASTER_BLOCKS; i++) {
      if(mb[i] == null) break;
      for(int j=0; j<MasterBlock.MAX_CHANNELS; j++) {
      if(Util.stringBuilderEqual(mb[i].getSeedName(j),"            ")) break;
        int in = mb[i].getFirstIndexBlock(j);
        int nchain=0;
        while (in > 0) {
          index[in].setDataBits(b);
          in = index[in].nextIndex;
          nchain++;
        }
      }
    }     // for each master block i=0;   
    return b;
  }
  public String compareIndexBlocks(IndexFileUtil chk) {return compareIndexBlocks(chk,false);}
  public String compareIndexBlocks(IndexFileUtil chk, boolean detail) {
    int foundBlank=-1;
    StringBuilder sb = new StringBuilder(10000);
    int nchk=0;
    int nok=0;
    for(int i=0; i<IndexFile.MAX_MASTER_BLOCKS; i++) {
      if(mb[i] == null) break;
      for(int j=0; j<MasterBlock.MAX_CHANNELS; j++) {
        if(Util.stringBuilderEqual(mb[i].getSeedName(j),"            ")) foundBlank=i*100+j;
        else if(foundBlank > 0)  
          sb.append(" seedname=").append(mb[i].getSeedName(j)).append(" follows a blank one at ").append(foundBlank).append("\n");
        if(!Util.stringBuilderEqual(mb[i].getSeedName(j),"           ")) {
          int in = mb[i].getFirstIndexBlock(j);
          int chkblk = chk.mb[i].getFirstIndexBlock(j);
          if(chk.isChkFormat()) chkblk=in;
          int nchain=0;
          while (in > 0) {
            if(in != chkblk) 
              sb.append("Warning: index block number not same for ").append(mb[i].getSeedName(j)).
                      append(" ").append(in).append("!=").append(chkblk).append(" nchain=").append(nchain).append("\n");
            
            nchk++;
            if(index[in].compareBufs(chk.index[chkblk])) {
              if(detail) Util.prt("i="+Util.rightPad(""+i, 2)+" j="+Util.rightPad(""+j,2)+
                  " ch="+Util.rightPad(""+nchain,2)+" blk="+(in+"    ").substring(0,5)+
                  " nxt="+Util.rightPad(""+index[in].nextIndex,5)+mb[i].getSeedName(j)+" is OK");
              nok++;
            }
            else {
              sb.append("i=").append(Util.rightPad(""+i, 2)).append(" j=").append(Util.rightPad(""+j,2)).
                      append(" ch=").append(Util.rightPad(""+nchain,2)).append(" blk=").
                      append((in+"    ").substring(0,5)).append("  ").append(mb[i].getSeedName(j)).append("is different\n");
              sb.append(index[in].dumpBufs(chk.index[chkblk]));
            }
            in = index[in].nextIndex;
            if(chk.isChkFormat()) chkblk=in;
            else chkblk = chk.index[chkblk].nextIndex;
            nchain++;
          }
        }
      }
    }     // for each master block i=0;
    Util.prt("Nchk="+nchk+" nok="+nok);
    return sb.toString();    
  }
  public String compareMasterBlocks(IndexFileUtil chk) {
    StringBuilder sb = new StringBuilder(10000);
    
    // Insure list of master blocks are the same
    int nchan=0;
    for(int i=0; i<IndexFile.MAX_MASTER_BLOCKS; i++) {
      if(mb[i] == null) break;
      boolean found=false;
      for(int j=0; j<IndexFile.MAX_MASTER_BLOCKS; j++) 
        if(chk.mBlocks[j] == mBlocks[i]) {found=true; break;}
      if(!found) 
        sb.append(" MasterBlock ").append(i).append("/").append(mBlocks[i]).
                append(" appears in ").append(filename).append(" but is absent in ").append(chk.filename).append("\n");
    }
    for(int i=0; i<IndexFile.MAX_MASTER_BLOCKS; i++) {
      if(chk.mb[i] == null) break;
      boolean found=false;
      for(int j=0; j<IndexFile.MAX_MASTER_BLOCKS; j++) 
        if(mBlocks[j] == chk.mBlocks[i]) {found=true; break;}
      if(!found) 
        sb.append(" MasterBlock ").append(i).append("/").append(mBlocks[i]).
                append(" appears in ").append(filename).append(" but is absent in chk.filename\n");
    }
    
    // now compare contents of master blocks
    int foundBlank=-1;
    for(int i=0; i<IndexFile.MAX_MASTER_BLOCKS; i++) {
      if(mb[i] == null) break;
      for(int j=0; j<MasterBlock.MAX_CHANNELS; j++) {
        if(mb[i] != null && chk.mb[i] != null ) {
          nchan++;
          if(!mb[i].getSeedName(j).equals(chk.mb[i].getSeedName(j)))
          sb.append(filename).append(" master block ").append(i).append("/").append(mBlocks[i]).
                  append(" slot=").append(j).append(" Seednames do not match").
                  append(mb[i].getSeedName(j)).append("!=").append(chk.mb[i].getSeedName(j)).append("\n");
        }
      }
    }
    sb.append("Master blocks show ").append(nchan).append(" channels\n");
    return sb.toString();
  }
  public static void main(String [] args) {
    EdgeProperties.init();
    byte [] buf = new byte[EdgeBlock.BUFFER_LENGTH];
    String filename  = "/Users/data/2006_094_4.idx";
    String filename2 = "/Users/data/2006_094_4.chk";
    StringBuilder sb = new StringBuilder(100000);
    if(args.length < 2) {
      Util.prt("IndexUtil filename1 filename2");
      Util.prt("    filename1 or 2 is normally in index file(.idx) or index chk(.chk)");
      Util.prt("    This command will compare these indices and print out differences between them");
      Util.prt("    The master blocks and each index block are compared - output can be lengthy");
      System.exit(0);
    }
    boolean makeCheck=true;
    boolean dataCheck=false;
    boolean detail=false;
    String node="";
    OUTER:
    for (String arg : args) {
      switch (arg) {
        case "-empty":
          break OUTER;
        case "-d":
          dataCheck=true;
          break;
        case "-v":
          detail=true;
          break;
        default:
          filename=filename2;
          filename2 = arg;
          break;
      }
    }
    try {
      Util.prta("   Load file "+filename);
      IndexFileUtil file1=new IndexFileUtil(filename, true);
      Util.prta("   Load file "+filename2);
      IndexFileUtil file2 = new IndexFileUtil(filename2, true);
      Util.prta("    Start comparison");
      Util.prt(file1.compareMasterBlocks(file2));
      Util.prt(file1.compareIndexBlocks(file2,detail));
      Util.prt("file1="+file1.filename+" max="+file1.max_block+" file2="+file2.filename+" max="+file2.max_block);
      if(dataCheck) {
        BitSet b1 = file1.getDataBitSet();
        BitSet b2 = file2.getDataBitSet();
        int lastb1=-1;
        int lastb2=-1;
        int nb1=0;
        int nb2=0;
        int ndiff=0;
        for(int i=0; i<b1.length(); i++) {
          if(b1.get(i)) {lastb1=i; nb1++;}
          if(b2.get(i)) {lastb2=i; nb2++;}
          if(b1.get(i) != b2.get(i)) {ndiff++;Util.prt(i+" Bit set diff="+b1.get(i)+" "+b2.get(i));}
        }
        Util.prt(" b1 lst="+lastb1+" #="+nb1+" b2 lst="+lastb2+" #="+nb2+" # diff="+ndiff);
        int period = filename.indexOf(".");
        if(period < 0) {Util.prt("bad filename = "+filename+" for parsing to data file"); System.exit(0);}
        filename = filename.substring(0,period)+".ms";
        RawDisk r = new RawDisk(filename,"r");
        period = filename2.indexOf(".");
        if(period < 0) {Util.prt("bad filename = "+filename2+" for parsing to data file"); System.exit(0);}
        filename2 = filename2.substring(0,period)+".ms";
        RawDisk r2 = new RawDisk(filename2,"r");
        Util.prta("Start data compare pass for "+filename+" to "+filename2);
        byte [] bf = new byte[5120000];
        byte [] bf2 = new byte[5120000];
        byte [] tmp = new byte[512];
        ndiff=0;
        for(int i=0; i<lastb1; i=i+1000) {
          if(i % 500000 == 0) Util.prta(i+" of "+(r.length()/512L));
          r.readBlock(bf, i, 512000);
          r2.readBlock(bf2, i, 512000);
          for(int j=0; j<1000; j++) {
            if(b1.get(i+j)) {
              boolean isDiff=false;
              int nf=0;
              int nz=0;
              for(int k = 0; k<512; k++)  if(bf[j*512+k] != bf2[j*512+k]) {
                if(bf2[j*512+k] == 0) nz++;
                if(!isDiff) sb.delete(0,sb.length());
                isDiff=true;
                nf++;
                sb.append(i+j).append("/").append(k).append(" ").append(Util.leftPad(""+bf[j*512+k],4)).
                        append(" != ").append(Util.leftPad(""+bf2[j*512+k],4)).append("\n");
              }
              if(isDiff) {
                try {
                  ndiff++;
                  System.arraycopy(bf,512*j, tmp, 0, 512);
                  int [] time = MiniSeed.crackTime(tmp);
                  Util.prt((i+j)+" has "+nf+" differences #zero="+nz+" "+
                      Util.toAllPrintable(new String(bf, j*512,20))+" "+
                      Util.leftPad(""+time[0],2)+":"+Util.leftPad(""+time[1],2)+":"+
                      Util.leftPad(""+time[2],2));
                  if(detail) Util.prt(sb.toString());
                }
                catch(IllegalSeednameException e) {
                  e.printStackTrace();
                }
              }
            }
          }
        }
        Util.prta("data compare complete # diff="+ndiff);
      }
    }
    catch(FileNotFoundException e) {
      Util.prt("File not found for either "+filename+" or "+filename2+" "+e.getMessage());
    }
    catch(IOException e) {
      Util.prt("IOException "+filename+" or "+filename2+" "+e.getMessage());
    }
  }
}
