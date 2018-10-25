/*
 *  This software is in the public domain because it contains materials 
 *  that originally came from the United States Geological Survey, 
 *  an agency of the United States Department of Interior. For more 
 *  information, see the official USGS copyright policy at 
 *  http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.msread.FixUp;

import gov.usgs.anss.edge.EdgeFileDuplicateCreationException;
import gov.usgs.anss.edge.EdgeFileReadOnlyException;
import gov.usgs.anss.edge.IllegalSeednameException;
import gov.usgs.anss.edge.IndexBlock;
import gov.usgs.anss.edge.IndexFile;
import static gov.usgs.anss.edge.IndexFile.MAX_MASTER_BLOCKS;
import gov.usgs.anss.edge.MasterBlock;
import gov.usgs.anss.edge.RawDisk;
import gov.usgs.anss.util.Util;
import gov.usgs.anss.edge.EdgeFileCannotAllocateException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.io.FileNotFoundException;

/**
 *
 * @author U.S. Geological Survey  <ketchum at usgs.gov>
 */
public class FixLocationDashes {
  public static void main(String [] args) {
    if(args.length == 0) {
      args = new String[1];
      args[0]="/data2/2007_180_3#2.idx";
    }
    IndexFile.setNoZeroer(true);
    for(int i=0; i<args.length; i++) {
      String filename=args[i];
      try {
        IndexFile idx = new IndexFile(filename, false, false);
        fixDashesLocation(idx, true);
        idx.crashClose();
      }
      catch(FileNotFoundException e) {
        Util.prt("Did not find file="+filename);
      }
      catch(EdgeFileReadOnlyException e) {
        e.printStackTrace();
      } catch (IOException e) {
        e.printStackTrace();
      } catch (EdgeFileDuplicateCreationException e) {
        e.printStackTrace();
      } catch (IllegalSeednameException e) {
        e.printStackTrace();
      }
    }
  }
  public static void fixDashesLocation(IndexFile file, boolean update) throws IOException,IllegalSeednameException {
    RawDisk dataFile=file.getDataRawDisk();
    RawDisk rw = file.getRawDisk();
    byte [] ctlbuf = new byte[512];
    MasterBlock [] mb = new MasterBlock[IndexFile.MAX_MASTER_BLOCKS];
    int [] mBlocks = new int[IndexFile.MAX_MASTER_BLOCKS];
    rw.readBlock(ctlbuf, 0, 512);      // get ctrl region
    ByteBuffer ctl = ByteBuffer.wrap(ctlbuf);
    int nindex=0;
    int ndata=0;
    int nchan=0;
    // Unpack the ctlbuf
    ctl.clear();
    int length2 = ctl.getInt();
    int next_extent = ctl.getInt();
    int next_index = ((int) ctl.getShort()) & 0xffff;
    StringBuilder newseedname = new StringBuilder(12);
    for(int i=0; i<IndexFile.MAX_MASTER_BLOCKS; i++) {
      mBlocks[i]= ((int) ctl.getShort()) & 0xffff;
    }                      
    for(int i=0; i<MAX_MASTER_BLOCKS; i++) 
      if(mBlocks[i] != 0) mb[i] = new MasterBlock((int) mBlocks[i], rw, false);
      else break;                       // exit when we are out of allocated blocks
    // Look through mast blocks for ones with location code ='--'
    for(int i=0; i<IndexFile.MAX_MASTER_BLOCKS;i++) {
      if(mBlocks[i] <= 0) break;      // no more use master blocks
      for(int j=0; j<MasterBlock.MAX_CHANNELS; j++) {
        if(mb[i].getSeedName(j).substring(10).equals("--")) {
          nchan++;
          StringBuilder oldseedname=mb[i].getSeedName(j);
          Util.clear(newseedname).append(oldseedname.substring(0,10)).append("  ");
          Util.prt(oldseedname+" -> "+newseedname+" in mb["+i+"]["+j+"]");
          // Need to follow chain of index blocks and fix stuff
          
          IndexBlock idx;
          try {
            idx = new IndexBlock(file, oldseedname);
          }
          catch(EdgeFileCannotAllocateException e) {
            e.printStackTrace();
            continue;
          }
          boolean goodBlock=true;
          // loop over all good index blocks
          while(goodBlock) {
            nindex++;
            if(update) idx.setSeedname(newseedname);   // note this sets the name and write out the index block
            int [] extent = idx.getExtents();
            for(int k=0; k<IndexBlock.MAX_EXTENTS; k++) {
              if(extent[k] < 0) continue;
              for(int iblk=extent[k]; iblk<extent[k]+64; iblk++) {
                dataFile.readBlock(ctlbuf, iblk, 512);   // read data block
                if(ctlbuf[13] == '-' && ctlbuf[14] == '-') {
                  ctlbuf[13] = ' '; ctlbuf[14]=' '; 
                  ndata++;
                  if(update) dataFile.writeBlock(iblk, ctlbuf, 0, 512);
                }
                else if(ctlbuf[13] == 0 && ctlbuf[14] == 0) {
                  //Util.prta("zero block skipped iblk="+iblk);
                }
                else 
                  Util.prt("data block iblk="+iblk+" is not -- or zero zero! "+ctlbuf[13]+" "+ctlbuf[14]);
              }
            }
            goodBlock = idx.nextIndexBlock();

          }
          
          if(update) {
              mb[i].setSeedname(j, newseedname);  // this updates the block too
          }
        }   // if this has a dash dash
      }     // for each channel in the master block
    }
    Util.prt("File="+file.getFilename()+" #ch="+nchan+" #indexBlk="+nindex+" #dataBlk="+ndata);
  }
}
