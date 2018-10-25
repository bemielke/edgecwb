/*
 *  This software is in the public domain because it contains materials 
 *  that originally came from the United States Geological Survey, 
 *  an agency of the United States Department of Interior. For more 
 *  information, see the official USGS copyright policy at 
 *  http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */

package gov.usgs.anss.net;
import gov.usgs.anss.edgethread.EdgeThread;
import gov.usgs.anss.util.Util;
import java.io.*;
import java.nio.ByteBuffer;
//import gov.usgs.anss.util2.Util;
/** This class manages an RRPServer or RRPclient state file.  It is designed to put the
 * last sequence in a file of many blocks in size where the file contains each successively 
 * updated last sequence in the file where sequences of -1 indicate this position does not
 * yet maintain a sequence.  As each block is consumed, the next block is set and the offset 
 * int the block cycles back to zero.  When the file is full (that is we want to write into
 * the nblk block), it cycles back to the first block, offset =0, and puts the first sequence in
 * offset zero.  
 * 
 * When opening an existing file, the blocks are scanned until the first -1 is found, the lastIndex
 * is set to the one just before the -1 and the iblk and offset set to the first -1.
 * 
 * This elaborate method of keeping track of the last sequence state is done so that on static
 * ram disk files, the writting of the blocks cycles over many blocks so the write to one block
 * does not occure in one place.
 *
 * @author U.S. Geological Survey <ketchum at usgs.gov>
 */
public class RRPStateFile {
  RandomAccessFile out ;
  private int iblk;
  private int offset;
  private byte [] buf = new byte[512];
  private  int lastIndex;
  private ByteBuffer bb;
  private int nblk;
  private String filename;
  private EdgeThread par;
  public void prt(String s) {if(par != null) par.prt(s); else EdgeThread.staticprt(s);}
  public void prta(String s) {if(par != null) par.prta(s); else EdgeThread.staticprta(s);}  
  public RRPStateFile(String filename, int nblk, EdgeThread parent) throws IOException {
    this.filename=filename;
    this.nblk=nblk;
    par = parent;
    if(nblk <= 0) nblk=100;
    bb = ByteBuffer.wrap(buf);
    try {
      out = new RandomAccessFile(filename,"rw");
      if(out.length() == 0) {   // Is this a new file
        clearFile();
      }
      
      // Look through the file looking for -1
      int index=0;
      lastIndex=1;
      for(iblk=0; iblk<nblk; iblk++) {
        out.seek(iblk*512);        
        out.read(buf, 0, 512);
        bb.position(0);
        for(offset=0; offset<128; offset++) {
          if( (index = bb.getInt()) == -1) break;
          lastIndex=index;
        }
        if(index == -1) {
          prta("Found new index at "+iblk+"/"+offset);
          break;
        }
      }
    }
    catch(FileNotFoundException e ) {
      prta("File not found"); 
      throw e;
    }
    catch(IOException e) {
      prta("IOErr="+e);
      throw e;
      
    }
  }
  public int getLastIndex() {return lastIndex;}
  /** write a sequence into the next position in the file. If this fills the block, setup next block
   * If it fills the file, put it in the first position and clear the file.
   * @param seq The sequence to record as the last one
   * @throws IOException 
   */
  public void updateSequence(int seq) throws IOException {
    // Put the sequence in the position
    bb.position(offset);
    bb.putInt(seq);
    offset += 4;
    lastIndex = seq;
    out.seek(iblk*512);
    out.write(buf, 0, 512);    
    // Is the next position in the next block, if so reset offset and block number
    if(offset >= 512) {
      iblk++;
      offset=0;
      for(int i=0; i<512; i++) buf[i]=-1;   // clear the current block buffer
      // Are we now past the last block for the file size, if so create a new file and move on
      if(iblk >= nblk) {
        prta("Index is wrapping to beginning of state file iblk="+iblk+" offset="+offset);
        iblk=0;
        offset=0;
        out.close();
        File file = new File(filename);           // We are going to rename the old file
        File rename = new File(filename+"_DEL");  // to this and then open a new one
        file.renameTo(rename);                    // out file is now renamed.
        out = new RandomAccessFile(filename,"rw");
        clearFile();
        bb.position(0);
        bb.putInt(seq);
        out.seek(0L);
        out.write(buf, 0, 512);
      }
    }

  }
  /** cearl the current out file to all -1
   * 
   * @throws IOException 
   */
  private void clearFile() throws IOException {
    out.seek(0L);
    prt("** Creating/Clear RRPState file="+filename);
    for (int i=0; i<512; i++) buf[i]=-1;
    for(int i=0; i<nblk;i++) out.write(buf, 0, 512);
  }
  public static void main(String [] args) {
    Util.init("edge.prop");
    Util.setModeGMT();
    String logfile="rrpstate";
    EdgeThread.setMainLogname(logfile);
    RRPStateFile file =null;
    try {
      file = new RRPStateFile("rrp.last", 20, null);
      for(int i=0;i<10000; i++) file.updateSequence(i);
    }
    catch(IOException e) {
      e.printStackTrace();
    }
    if(file != null) file.prt("Done");
  }
}
