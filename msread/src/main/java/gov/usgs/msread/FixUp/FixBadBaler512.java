/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */


 /*
 * FixBadBaler512.java
 *
 * Created on October 25, 2007, 12:00 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */
package gov.usgs.msread.FixUp;
import gov.usgs.anss.seed.*;
import gov.usgs.anss.util.*;
import gov.usgs.anss.edge.*;

import java.io.IOException;
import java.io.EOFException;
import java.io.FileNotFoundException;


/** This class was used to fix up a problem when the 4096 to 512 byte convertor was
 * broken from 2007,267 to 2007,294.  This made the 512 block potentially mistimed and
 * duplicated.  This changes the Indictor to "A " so that this data will be ignored by 
 * the query functions making it practically invisible.  The data was then refetched via the
 * fixed code.
 *
 * @author davidketchum
 */
public class FixBadBaler512 {
  
  /** Creates a new instance of FixBadBaler512 */
  public FixBadBaler512() {
  }
   public static void main( String [] args) {
    // To get here we are in "rawMode
    byte [] littlebuf = new byte[8192];
    RawDisk rw = null;
    long length=0;
    byte [] buf = new byte[2048*512];
    for(int i=0;i<args.length; i++) {
      try {
        
        rw= new RawDisk(args[i],"rw");
        length = rw.length();
      }
      catch(FileNotFoundException e) {
        Util.prt("File is not found = "+args[i]);
        continue;
      }
      catch(IOException e) {
        Util.prt("Cannot get length of file "+args[i]);
        continue;
      }
      int iblk=0;
      Util.prta(" Open file : "+args[i]+" start block="+iblk+" end="+(length/512L)+"\n");

      long lastTime = System.currentTimeMillis()-1000;
      int zeroInARow=0;
      int next=0;
      int len=0;
      int nsleep=0;
      int off=0;
      int nsend=0;
      int nzap=0;
      int negak=0;
      MiniSeed ms = null;
      MiniSeed msbig = null;
      MiniSeed ms3=null;
      int iblk90 = 0;
      for(;;) {
        try {
          if(iblk90 ==0)  {
            iblk90 = (int) (rw.length()/512/10*9);
          }
          try {Thread.sleep(50);} catch(InterruptedException e ){}
          len = rw.readBlock(buf, iblk, 2048*512);
          if(iblk >= next) {
            next += 500000;
            System.out.println(
              Util.asctime().substring(0,8)+" "+iblk+" "+(500000000/Math.max(System.currentTimeMillis() - lastTime,1))+
                " b/s siz="+rw.length()/512+" sleep="+nsleep+" "+args[i]+" #zap="+nzap+" #egak="+negak);
            lastTime = System.currentTimeMillis();
            nsleep=0;
          }

          while (off < len) {
            if(buf[off] == 0 && buf[off+1] == 0 && buf[off+2] == 0) {
              zeroInARow++;

              if(zeroInARow > 600 && iblk > iblk90) {System.out.println("\n"+Util.asctime()+" Zeros found for EOF "+(iblk+off/512));break;}
              off += 512;
              continue;
            } 
            zeroInARow=0;

            System.arraycopy(buf, off, littlebuf, 0, 512);
            try {
              if(iblk == 0 && off == 0) msbig = new MiniSeed(littlebuf);     // first buffer
              String seedname = MiniSeed.crackSeedname(littlebuf);
              if(!Util.isValidSeedName(seedname) || 
                  seedname.substring(7,10).equals("ACE") || seedname.substring(7,10).equals("OCF") || 
                  MiniSeed.crackNsamp(littlebuf) == 0) {
                off += 512;
                continue;
              }
              if(ms == null) ms = new MiniSeed(littlebuf);
              else ms.load(littlebuf);
              seedname = ms.getSeedNameString();
              
              // This should be a conditional on whether the block matches the criteria
              if(seedname.substring(0,2).equals("US") && ms.getSequence() > 900000 && ms.getSequence() < 901500) {
                String frag = seedname.substring(2,7).trim();
                if(!(frag.equals("AAM") ||frag.equals("ACSO") ||frag.equals("AGMN") ||frag.equals("BLA") ||frag.equals("COWI") ||
                    frag.equals("DUG") ||frag.equals("ECSD") ||frag.equals("EGAK") ||frag.equals("EGMT") ||frag.equals("EYMN") ||
                    frag.equals("GLMI") ||frag.equals("HAWA") ||frag.equals("HLID") ||frag.equals("ISCO") ||frag.equals("JCT") ||
                    frag.equals("LONY") ||frag.equals("MIAR") ||frag.equals("MVCO") ||frag.equals("NEW") ||frag.equals("WMOK") ||
                    frag.equals("WRAK") ||frag.equals("WUAZ") ||frag.equals("WVOR") || frag.equals("DGMT") || frag.equals("HDIL")
                    || frag.equals("NLWA"))) 
                  Util.prt("   **** Station not expected="+ms.toString());
                else {
                  //Util.prt(ms.toString());
                  if(frag.equals("EGAK")) negak++;
                  else {off+=512;continue;}
                  if(ms.getTimeInMillis() % 86400000L > 43200000L) {off+=512;continue; }
                  
                  // This block is due to be modified.  Modify it and write it back out.
                  int theBlock = iblk + off/512;
                  byte [] bf = rw.readBlock(theBlock,512);
                  if(ms3 == null) ms3 = new MiniSeed(bf);
                  else ms3.load(bf);
                  if(!ms.toString().equals(ms3.toString()))Util.prt("DIFF "+ms.toString()+"\nTO   "+ms3.toString());
                  bf[6]='A';
                  rw.writeBlock(theBlock, bf, 0, 512);
                  nzap++;
                }
              }
              if(
                  ms.getNsamp()> 0 && ms.getNsamp() < 8000 &&
                  ms.getJulian() > SeedUtil.toJulian(1970,1) && 
                  ms.getJulian() <SeedUtil.toJulian(2100,1) &&
                  (ms.getBlockSize() == 512 || ms.getBlockSize() == 4096) &&
                  ms.getRate() >0.0099 && ms.getRate() < 200.) {
                if(ms.getBlockSize() == 512) off += 512;
                else if(65536 - (off % 65536) >= ms.getBlockSize()) 
                  off += ms.getBlockSize();
                else 
                  off += 65536 - (off % 65536);
              }
              else {
                try {
                  Util.prta(iblk+"/"+off+" something is not right="+Util.toAllPrintable(seedname)+" ns="+ms.getNsamp()+
                    " bs="+ms.getBlockSize()+" rt="+ms.getRate()+" jul="+ms.getJulian());
                }
                catch(RuntimeException e) {
                  Util.prt("Runtime at iblk="+iblk+" off="+off+" is "+(e.getMessage() == null ? "null": e.getMessage()));
                  if(e.getMessage() != null) {
                    if(e.getMessage().indexOf("impossible yr") < 0) 
                      e.printStackTrace();
                  }
                }
                off+=512;
              }
            }
            catch(IllegalArgumentException e) {
              off += 512;
              Util.prta("Illegal agument exception caught "+( e == null ? "null" : e.getMessage()));
              e.printStackTrace();
            }
            catch(IllegalSeednameException e) {
              Util.prt("Illegal seedname caught iblk="+iblk+" off="+off+" e="+e.getMessage());
              off += 512;
            }
            catch(RuntimeException e) {
                Util.prt("Runtime2 at iblk="+iblk+" off="+off+" is "+(e.getMessage() == null ? "null": e.getMessage()));
                if(e.getMessage() != null) {
                  if(e.getMessage().indexOf("impossible yr") < 0) 
                    e.printStackTrace();
                }
                off+=512;
            }
          //Util.prt("off="+off+" "+new MiniSeed(littlebuf).toString());

          }
          if(zeroInARow > 600 && iblk > iblk90) break;
          off = off -len;
          iblk = iblk + len/512;
        }
       /* catch(IllegalSeednameException e ) {
          Util.prt("Illegal seedname found iblk="+iblk+" "+e.getMessage());
          iblk++;
        }*/
        catch(EOFException e) {
          Util.prt("EOF on file iblk="+iblk);      
          break;
        }
        catch(IOException e) {
          Util.prt("IOException = "+e.getMessage());
          System.exit(1);
        }
      }     // infinite loop on a file
      Util.prt("#blocks zapped="+nzap+" #egak="+negak);
    }       // for all files
    System.exit(1);   // We have done the holdings
  }
  
   
}
