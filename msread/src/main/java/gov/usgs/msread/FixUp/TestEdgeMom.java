/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */


/*
 * TestEdgeMom.java
 *
 * Created on November 3, 2007, 10:46 AM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package gov.usgs.msread.FixUp;
import java.io.*;
import java.nio.*;
import java.net.*;
import gov.usgs.anss.util.*;
import gov.usgs.anss.edge.*;
/**
 *
 * @author davidketchum
 */
public class TestEdgeMom extends Thread  {
  String filename;
  boolean running;
  RawDisk rw = null;
  long length;
  Socket s;
  byte [] buf;
  int ioff;
  boolean isRunning() {return running;}
  public String toString() {return filename+" run="+running+" l="+(length/512)+" ioff="+(ioff/512)+" closed="+s.isBound();}
  /** Creates a new instance of TestEdgeMom */
  public TestEdgeMom(String file) {
    filename=file;
    running=true;
    String edgeIP="localhost";
    int edgePort=7974;
    try {
      rw = new RawDisk(filename,"r");
      length = rw.length();
      s = new Socket(edgeIP, edgePort);
      buf = new byte[(int) length];
      rw.readBlock(buf, 0, (int) length);
    }
    catch(IOException e) {
      Util.prt("   ****** TestEdgeMom did not start IO exception opening file"+e);
    }
  }
  public void run() {
    int iblk=0;
    try {
      
      OutputStream out = null;
      //Util.prt("Open edge at "+edgeIP+"/"+edgePort);
      out = s.getOutputStream();
      try {
        Util.prta(" Open file : "+filename+" start block="+iblk+" end="+(length/512L));
        int inrow=0;
        for(ioff=0; ioff<buf.length; ioff=ioff+512) {
          boolean zero=true;
          //mslist.clear();

          // Bust the first 512 bytes to get all the header and data blockettes - data
          // may not be complete!  If the block length is longer, read it in complete and
          // bust it appart again.              
          out.write(buf, ioff, 512);
          iblk++;
        }      // end of read  for(;;)
        Util.prta(filename+" "+iblk+" blks sent ");
      }
      catch(IOException e) {
        if(e.getMessage() != null) Util.prt("IOException "+e.getMessage());
        else {
          Util.prta(filename+" "+iblk+" blks sent ");
        }
        try {
          if(rw != null) rw.close();
        }
        catch(IOException e2) {}

      }
    }
    catch(IOException e) {
      Util.prt("cannot open a socket to send data! e="+e.getMessage());
    }
    try{
      s.close();
    }
    catch(IOException e) {}
    running=false;
    Util.prta(filename+" "+(buf.length/512)+" bytes sent");
  }
  static public void  main(String [] args) {
    Util.init();
    Util.setModeGMT();
    TestEdgeMom [] thr = new TestEdgeMom[args.length];
    for(int i=0; i<args.length; i++) thr[i] = new TestEdgeMom(args[i]);
    for(int i=0; i<args.length; i++) thr[i].start();
      try {Thread.sleep(1000);} catch(InterruptedException e) {}
    for(;;) {
      boolean anyrunning=false;
      for(int i=0; i<args.length; i++) {anyrunning |= thr[i].isRunning(); if(thr[i].isRunning()) Util.prta(thr[i].toString());}
      if(!anyrunning) break;
      try {Thread.sleep(1000);} catch(InterruptedException e) {}
    }
    Util.prt("All threads have exited");
  }
}
