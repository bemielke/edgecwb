/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */

package gov.usgs.msread.FixUp;
import gov.usgs.anss.edge.*;
import gov.usgs.anss.seed.MiniSeed;
import java.io.IOException;
import gov.usgs.anss.util.*;
//import gov.usgs.anss.edge.*;

/** This is a template for an EdgeThread (one that can be run by EdgeMom.  It includes
 * a timeout on the run() and a registered shutdown handler.  
 *
 *This main class rebuilds a index file from the data file by reading all the blocks
 * and writing them into a "faked" index.  (The file is opened using IndexFileReplicator
 * but the index RawDisk is overridden to be the new one).  So if you put in -f 2012_054_1.ms
 * it will build a new index in 2012_054_1.ms.idx_new which must be renamed to be used.
 *
 * @author davidketchum
 */
public class RebuildIndex {

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
    int julian = 0;
    for(int i=0; i<args.length; i++) {
      if(args[i].equals("-f")) {
        filename=args[i+1];
        int lastSlash=filename.lastIndexOf("/");
        if(lastSlash < 0) lastSlash=-1;
        Util.prt("IndexFile open="+filename+" yr="+
           filename.substring(lastSlash+1,lastSlash+5)+
           " doy="+filename.substring(lastSlash+6, lastSlash+9));
        int year;
        int doy;
        try {
          year = Integer.parseInt(filename.substring(lastSlash+1,lastSlash+5));
          doy = Integer.parseInt(filename.substring(lastSlash+6, lastSlash+9));
        }
        catch(NumberFormatException e) {
          Util.prt("IndexFileOpen probably bad year or date="+filename);
          return;
        }
        julian = SeedUtil.toJulian(year,doy);
      }
    }
    try {
      EdgeBlockQueue ebq = new EdgeBlockQueue(10000);     // this is going nowhere
      IndexFile idx = null;

      try {
        idx = new IndexFile("1971_001_", true, false, julian);    // This is a faked up empty IndexFile
      }
      catch(RuntimeException e) {Util.prt("e="+e);}
      catch(IOException e) {Util.prt("ioe="+e);}
      // create our new idx file which needs to be built
      RawDisk chk = new RawDisk(filename.trim()+".idx_new","rw");
      Util.prt("Open output file "+filename.trim()+".idx_new");
      // override it in the indexfile
      idx.setRawDisk(chk);
      idx.setReadonly(false);       // note data node was still opened read only
      IndexBlock.init();
      idx.clearMasterBlocks();          // force idx_new to be a "new" file
      RawDisk data = new RawDisk(filename,"r");
      byte [] zero = new byte[512];
      byte [] databuf = new byte[512*64];
      for(int i=0; i<512;i++) zero[i]=0;
      for(int iblk=0; iblk<chk.length()/512; iblk++) {
        chk.writeBlock(iblk, zero,  0, 512);
      }
      int nextstatus=500000;
      int allzero=0;
      int lastBlk=(int) (data.length()/512);
      Util.prt("LastBlock="+lastBlk);
      for(int iblk=0; iblk<lastBlk; iblk=iblk+64) {
        if(allzero > 10) break;

        data.readBlock(databuf, iblk, 512*(iblk + 64 < lastBlk? 64 : lastBlk-iblk)); // read in an extent
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
                Util.prt("** Seednames in an extent do not agree!"+seedname+"!="+ms.toString()+" iblk="+(iblk+i/512));
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
                if(ib == null) {

                  ib = new IndexBlock( ms.getSeedNameSB(),ms.getJulian(),false);
                  Util.prt("new index for "+ib);
                  ib.newIndexOnlyStart();
                }
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
      //Util.sleep(10000);
      Util.prta(filename.trim()+".idx_new has been created");
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
