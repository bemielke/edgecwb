/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */


/*
 * RawDisk.java
 *
 * Created on May 20, 2005, 4:02 PM
 * Author : D.C. Ketchum 
 *
 *This extends the random disk file an basically creates some 512 byte block
 *oriented extensions useful for the edge stuff.
 */

package gov.usgs.anss.net;
import gov.usgs.anss.RRPUtil.Util;
import java.io.*;
import java.util.Random;          // used in test main only


/** This class present a block oriented read/write for the RandomAccessFile class.
 * It basically adds methods which insure read/write on 512 byte boundaries
 *
 * @author davidketchum
 */
public class RawDisk extends RandomAccessFile {

  private String filename;
  private String mode;
  private long lastUsed;
  private int nwrite;
  private int nread;
  /** get number of read requests on this file
   *@return the number of read requests */
  public int getNread() {return nread;}
  /** get number of write requests on this file
   *@return The number of writes to this file*/
  public int getNwrite() {return nwrite;}
  
  /** get the time this RawDisk last did any I/O
   *@return The System.getCurrentMillis() of the last I/O*/
  public long getLastUsed() {return lastUsed;}
  /** Creates a new instance of RawDisk 
   *@param file - the filename or full path name to the file
   *@param md Mode of open "r", "rw", "rws" or "rwd" - rwd is recommended 
   *@throws FileNotFoundException If now a "w", then file was not found for reading.
   */
  public RawDisk(String file, String md) throws FileNotFoundException {
    super(file,md);
    filename=file;
    mode=md;
    nread=0;
    nwrite=0;
    lastUsed=System.currentTimeMillis();
  }
  /** return the file name of this file 
   *@return String with file name 
   */
  public String getFilename() {return filename;}
  /** position the file to blk (basically a seek to blk*512
   *@param blk Block to position to
   *@throws IOException If the seek fails.
   */
  public synchronized void position(int blk) throws IOException {
    seek((long) blk*512L);
    lastUsed=System.currentTimeMillis();
    
  }
  
  /** Write len bytes from b at offset off at block iblk
   *@param iblk Block to start write at
   *@param b data buffer
   *@param off offset in data buffer
   *@param len length of write
   *@throws IOException If seek or write fails.
   */
  public synchronized void writeBlock(int iblk, byte [] b, int off, int len) throws IOException {
    //long start = System.currentTimeMillis();
    lastUsed=System.currentTimeMillis();
    if(iblk < 0) Util.prta("RawDisk.writeBlock() negative offset iblk="+iblk);
    seek((long) iblk*512L);
    nwrite++;
    //long seek = System.currentTimeMillis();
    write(b, off, len);
    //long now = System.currentTimeMillis();
    //Util.prta("writebloc seek="+(seek-start)+" write="+(now-seek));
    //return start;
  }
  
  /** read starting at blk for len and return as a buffer.  Uses readFully so
   *the entire length must be returned. 
   *@param iblk the block to start the read
   *@param len the length of the read
   *@throws IOException If seek or read fails.
   *@return byte array with data - length is fully read so its always len long.
   */
  public synchronized byte [] readBlock(int iblk, int len) throws IOException {
    byte[] b = new byte[len];
    lastUsed=System.currentTimeMillis();
    nread++;
    seek((long) iblk*512L);
    readFully(b, 0, len);
    return b;
  }
  /** read starting at blk for len and return as a buffer.  Uses readFully so
   *the entire length must be returned. 
   *@param buf A buffer to put the data in.
   *@param iblk the block to start the read
   *@param len the length of the read
   *@throws IOException If seek or read fails.
   *@return len if data were read, should block if not
   */
  public synchronized int readBlock(byte [] b,int iblk, int len) throws IOException {
    if(b.length < len) 
      throw new IOException("readBlock buf length less than request "+b.length+"<"+len);
    lastUsed=System.currentTimeMillis();
    seek((long) iblk*512L);
    nread++;
    readFully(b,0, len);
    return len;
  }
  /** Main unit test
   *@param args command line args
   */
  public static void main(String [] args) {
    byte [] b = new byte[512];
    byte [] a = new byte[512];
    byte [] z = new byte[102400];
    byte [] c;
    Random ran = new Random();
    for(int i=0; i<102400;i++) {z[i]='z'; if(i%64 == 63) z[i]='\n';}
    for(int i=0; i<512; i++) {
      a[i] = 'a';
      if(i % 64 == 63) a[i]='\n';
      b[i]='b';
      if(i %64 == 63) b[i]='\n';
    }
    Util.prta("open");
    int iblk=0;
    try {
      RawDisk rw = new RawDisk("rawdsk.temp","rwd");
      // this test writes 10000 blocks very quickly and reads back to ensure buffering o.k.
      rw.setLength(512*10000);
      Util.prt("Start 10000 block write");
      for(int i=0; i<10000; i++) {
        a[0]=(byte) (i%128);
        rw.writeBlock(i, a, 0, 512);
      }
      Util.prta("Write done");
      for(int i=0; i<10000;i++) {
        rw.readBlock(b, i, 512);
        if(b[0] != (i%128)) Util.prta("blk="+i+" wrong is "+b[0]+" should be "+(i%128));
      }
      Util.prta("ReadBack done");
      Util.prta("set length");
      int i=0;
      // examine file and insure it is in a known state. Normally run this with
      // two instances writing a or b blocks at same time, then use this to examine
      // that all blocks are a or b or unwritten z blocks
      /*for(iblk=0; iblk<200; iblk++) {
        c = rw.readBlock(iblk,512);
        boolean isa=true;
        boolean isb=true;
        boolean isz=true;
        for(i=0; i<512; i++) {
          if(c[i] != a[i]) isa=false;
          if(c[i] != b[i]) isb=false;
          if(c[i] != z[i]) isz=false;
        }
        Util.prta("is a="+isa+" is b="+isb+" is z="+isz);
        
      }
      System.exit(0);*/
      
      // Set file size an write z blocks in entire length
      rw.setLength(1000000);
      Util.prta("i="+(i++));
      for(i=0; i<100; i++) rw.writeBlock(i*200, z, 0,  102400);

      // write first few blocks to know states
      Util.prta("i="+(i++));
      rw.writeBlock(0, a, 0, 512);
      Util.prta("i="+(i++));
      rw.writeBlock(1, b, 0, 512);
      Util.prta("i="+(i++));
      rw.writeBlock(3, b, 0, 512);
      Util.prta("i="+(i++));
      rw.writeBlock(5, b, 0, 512);
      Util.prta("i="+(i++));
      rw.writeBlock(3, a, 0, 512);      
      Util.prta("i="+(i++));
      
      // This loop randomly writes a block type.  Normally run this in two instances
      // one with a blocks and one with "b" blocks to watch action
      for(;;) 
      { iblk=(int)(ran.nextDouble()*20000.);
        rw.writeBlock(iblk,a, 0, 512);
      }
      //Util.prta("i="+(i++));
    }
    catch(FileNotFoundException e) {
      Util.IOErrorPrint(e,"File open err on temp"); 
    }
    catch(IOException e) {
      Util.IOErrorPrint(e,"IOerror ");
    }
  }
}
