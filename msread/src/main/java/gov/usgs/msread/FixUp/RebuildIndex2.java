/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */


/*
 * IndexChecker.java
 *
 * Created on March 25, 2006, 1:32 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package gov.usgs.msread.FixUp;
import gov.usgs.anss.edge.*;
import gov.usgs.anss.seed.MiniSeed;
import java.io.IOException;
import gov.usgs.anss.util.*;

/** This is a template for an EdgeThread (one that can be run by EdgeMom.  It includes
 * a timeout on the run() and a registered shutdown handler.
 *
 *This main class rebuilds a index file from the data file by reading all the blocks
 * and writting them into a "faked" index.  (The file is opened using IndexFileReplicator
 * but the index RawDisk is overrided to be the new one).  This one requires the file and its
 * Index to be openable.  Use RebuildIndex when this is not true.
 *
 * @author davidketchum
 */
public class RebuildIndex2 {

  /** unit test main - reads in a captured stream of bytes from a server and plays
   *them back.
   *@param args command line args
   */
  public static void rebuildIndex(String [] args) {
    EdgeProperties.init();
    byte [] buf = new byte[EdgeBlock.BUFFER_LENGTH];
    String filename = "/Users/data/2007_030_7";
    boolean makeCheck=true;
    String node="";
    for(int i=0; i<args.length; i++) {
      if(args[i].equals("-f")) {
        filename=args[i+1];
        if(!filename.contains(".ms")) {
          Util.prt(filename+" is not a .ms file.  aborting");
          return;
        }
      }
    }
    try {
      EdgeBlockQueue ebq = new EdgeBlockQueue(10000);     // this is going nowhere
      IndexFile idx = null;
      try {
        idx = new IndexFile(filename, false, true);
      }
      catch(RuntimeException e) {Util.prt("e="+e);}
      catch(IOException e) {Util.prt("ioe="+e);}
      int julian = idx.getJulian();
      // create our new idx file which needs to be built
      RawDisk chk = new RawDisk(filename.trim()+".idx_new","rw");
      // override it in the indexfile
      idx.setRawDisk(chk);
      idx.setReadonly(false);       // note data node was still opened read only
      IndexBlock.init();
      idx.clearMasterBlocks();          // force idx_new to be a "new" file
      RawDisk data = idx.getDataRawDisk();
      byte [] zero = new byte[512];
      byte [] databuf = new byte[512*64];
      for(int i=0; i<512;i++) zero[i]=0;
      for(int iblk=0; iblk<chk.length()/512; iblk++) {
        chk.writeBlock(iblk, zero,  0, 512);
      }
      int nextstatus=500000;
      int allzero=0;
      for(int iblk=0; iblk<data.length()/512; iblk=iblk+64) {
        if(allzero > 10) break;
        data.readBlock(databuf, iblk, 512*64); // read in an extent
        String seedname=null;
        int foundZero=0;
        for(int i=0; i<64*512; i=i+512) {
          System.arraycopy(databuf, i, zero,0,512);
          if(zero[6] != 0 && zero[7] != 0) {
            allzero=0;
            try {
              MiniSeed ms = new MiniSeed(zero);

              // If this is the first seedname in extent, capture it, then check against all others
              if(seedname == null) seedname=ms.getSeedNameString();
              if(!seedname.equals(ms.getSeedNameString())) {
                Util.prt("** Seednames in an extent do not agree!"+seedname+"!="+ms.getSeedNameString()+" iblk="+iblk);
              }

              // Is it status time?
              if(iblk > nextstatus) {
                Util.prta("iblk="+iblk+" max="+data.length()/512+" "+ms.getTimeString()+" mxindx="+
                    idx.getMaxIndex());
                nextstatus+=500000;
              }
              // If we have found a zero block, we should not then have a data block, warn!
              if(foundZero > 0) {
                Util.prta("** Found data after zero in extent iblk="+iblk+" "+seedname+" "+ms.toString());
              }

              // If the julian day just moved, its probably old bogus data!
              if(ms.getJulian() != julian) Util.prta("** Not right julian "+julian+"!="+ms.getJulian()+ms.toString());
              else {
                IndexBlock ib = IndexBlock.getIndexBlock(ms.getSeedNameSB(), ms.getJulian());
                if(ib == null) ib = new IndexBlock( ms.getSeedNameSB(),ms.getJulian(),false);
                ib.writeMSIndexOnly(ms, iblk+i/512);
              }
            }
            catch(DuplicateIndexBlockCreatedException e) {
              Util.prta("Attempt to create duplicateBlock"+e.getMessage());
            }
            catch(IllegalSeednameException e) {
              Util.prta("Illegal seedname exception on iblk="+iblk+" offset="+i);
            }
            catch(EdgeFileCannotAllocateException e) {
              Util.prt("EdgeFileCannot allocate "+e.getMessage());
            }
          }
          else {
            foundZero++;
            if(foundZero == 64)
              {allzero++;
               Util.prta("All zero extent at iblk="+iblk+" allzero="+allzero);
               if(allzero > 10) {
                 Util.prta("10 zero filled blocks assume it end-of-file");
                 break;
               }
            }
          }
        }
      }
      idx.close();
      System.exit(0);
    }
    catch(IOException e) {
      Util.IOErrorPrint(e,"Error opening main test file");
    }
    catch(EdgeFileDuplicateCreationException e) {
      Util.prta("Attempt to create duplicateBlock"+e.getMessage());
    }
    catch(EdgeFileReadOnlyException e) {
      Util.prt("Edgefile read only exception opening file="+filename);
    }

  }
  public static void main(String [] args) {
    Util.setModeGMT();
    Util.setNoconsole(true);      // Mark no dialog boxes
    Util.setNoInteractive(false);
    Util.init("edge.prop");
    Util.setProcess("RebuildIndex");
    Util.prta("Starting "+args[1]);
    rebuildIndex(args);

  }
}
