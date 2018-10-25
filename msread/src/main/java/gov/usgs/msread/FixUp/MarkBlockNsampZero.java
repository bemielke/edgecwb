/*
 *  This software is in the public domain because it contains materials 
 *  that originally came from the United States Geological Survey, 
 *  an agency of the United States Department of Interior. For more 
 *  information, see the official USGS copyright policy at 
 *  http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.msread.FixUp;
import gov.usgs.anss.edge.*;
import gov.usgs.anss.seed.MiniSeed;
import gov.usgs.anss.util.*;
import java.io.*;
/**
 *
 * @author U.S. Geological Survey <ketchum at usgs.gov>
 */
public class MarkBlockNsampZero {
  public static void main(String [] args) {
    String re = "GSOK025H[HN]...";
    if(args.length == 0) {
      args = new String[1];
      args[0]="/data/2014_031_4#9.ms";
    }
    int start=0;
    MiniSeed ms = null;
    byte [] buf = new byte[512];
    for(int i=0; i<args.length;i++) {
      if(args[i].equals("chan")) {re = args[i+1]; i++; start=i;}
    }
    for(int i=start; i<args.length; i++) {
      try {
        RawDisk rw = new RawDisk(args[i], "rw");
        Util.prt("Open file : "+args[i]);
        for(int iblk=0; iblk<rw.length()/512; iblk++) {
          try {
            rw.readBlock(buf, iblk,512);
            if(iblk % 1000000 == 0) Util.prta("progress "+iblk+"/"+(rw.length()/512));
            if(MiniSeed.crackSeedname(buf).matches(re)) {
              if(ms == null) ms = new MiniSeed(buf, 0, 512);
              else ms.load(buf, 0, 512);
              //Util.prt("nd="+ms);
              if(ms.getRate() == 1.0 || ms.getRate() == 0.) {
                buf[7]='~';
                buf[30]=0;
                buf[31]=0;
                buf[32]=0;
                buf[33]=0;
                buf[34]=0;
                buf[35]=0;
                ms.load(buf, 0, 512);
                Util.prt("ms="+ms);
                rw.writeBlock(iblk, buf, 0, 512);
              }
            }
          }
          catch(IllegalSeednameException e) {
           Util.prt("illigal seedname="+e);
          }
          catch(IOException e) {
            Util.prt("IOError e2="+e);
          }
        }
        rw.close();
      }
      catch(IOException e) {
        Util.prt("IOError e="+e);
      }
    }
  }
}
