/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.cwbqueryclient;

import gov.usgs.anss.edge.RawDisk;
import gov.usgs.anss.seed.MiniSeed;
import gov.usgs.anss.util.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;

/**
 * Create a simple miniSEED file from the CWBQuery list of miniSEED blocks. This allows options for
 * setting the filename with "-o", eliminating dups in the data, forcing the network code.  Notice
 * dups are normally not present in the list as CWBQuery normally would suppress them.
 *
 * @author davidketchum
 */
public class MSOutputer extends Outputer {

  boolean dbg;
  boolean nosort;
  String outfile;
  RawDisk out = null;
  String concatname;
  private long msSortDups;
  private long msWrite;  
  boolean concat;

  /**
   * Creates a new instance of MSOutput
   */
  public void setNosort() {
    nosort = true;
  }

  public String getFilename() {
    return outfile;
  }

  public long getLength() throws IOException {
    return out.length();
  }
  
  public long getMSSortDups() {
    return msSortDups;
  }

  public long getMSWrite() {
    return msWrite;
  }
  public void setConcat(boolean t, String concatname) throws IOException {
    concat = t;
    this.concatname = concatname;
    outfile = concatname.trim() + ".wfms";
    out = new RawDisk(outfile, "rw");
  }

  public MSOutputer(boolean nos) {
    nosort = nos;
  }

  @Override
  public void makeFile(String comp, String filename, String filemask, ArrayList<MiniSeed> blks,
          java.util.Date beg, double duration, String[] args) throws IOException {
    MiniSeed ms2;
    boolean nodups = false;
    boolean perf = false;
    String forceNetwork = null;
    for (int i = 0; i < args.length; i++) {
      if (args[i].equals("-nodups")) {
        nodups = true;
      }
      if (args[i].equals("-fn")) {
        forceNetwork = args[i + 1];
      }
      if(args[i].equals("-perf")) perf=true;
    }
    //if(concat) Util.prt("Start write of blocks at len="+out.length());
    if (out == null) {    // If we have not opened a file or if we need to concatenate
      if (filemask.endsWith("%N")) {
        filename += ".ms";
      }
      filename = filename.replaceAll("[__]", "_");
      outfile = filename;
      if (filename.contains("/")) {
        Util.chkFilePath(filename);
      }
      try {
        out = new RawDisk(outfile, "rw");
      } catch (IOException e) {
        Util.prt("COuld not create file e=" + e);
      }
    }
    msSortDups = System.currentTimeMillis();
    if (!nosort) {
      Collections.sort(blks);
    }
    if (nodups) {
      for (int i = blks.size() - 1; i > 0; i--) {
        if (blks.get(i).isDuplicate(blks.get(i - 1))) {
          blks.remove(i);
        }
      }
    }
    if (forceNetwork != null) {
      byte byte1 = (byte) forceNetwork.charAt(0);
      byte byte2 = (byte) forceNetwork.charAt(1);
      for (MiniSeed blk : blks) {
        ms2 = (MiniSeed) blk;
        ms2.getBuf()[18] = byte1;
        ms2.getBuf()[19] = byte2;
      }
    }
    msWrite = System.currentTimeMillis();
    msSortDups = msWrite - msSortDups;
    for (int i = 0; i < blks.size(); i++) {
      ms2 = (MiniSeed) blks.get(i);

      // The GSN would prefer not blockette 1001 if it contains invalid data
      if (ms2.getBlockette1001() != null) {
        if (ms2.getTimingQuality() < 0 || ms2.getTimingQuality() > 100) {
          //Util.prt("Starting block for delete 1001="+ms2);
          boolean worked = ms2.deleteBlockette(1001);
          //Util.prt("AFter delete 1001 worked="+worked+" "+ms2);
        }
      }
      boolean skip = false;
      for (int j = 0; j < ms2.getNBlockettes(); j++) {
        if (ms2.getBlocketteType(j) == -14080 || ms2.getBlocketteType(j) == -3071) {
          skip = true;
        }
      }
      if (dbg) {
        Util.prt("Out:" + ms2.getSeedNameString() + " " + ms2.getTimeString()
                + " ns=" + ms2.getNsamp() + " rt=" + ms2.getRate());
      }
      if (!skip) {
        out.write(ms2.getBuf(), 0, ms2.getBlockSize());
      } else {
        blks.set(i, null);
      }
    }
    out.setLength(out.getFilePointer());
    if (!concat) {
      out.close();
      out = null;
    }
    msWrite = System.currentTimeMillis() - msWrite;
    if(perf) {
      Util.prta("MSOutput : msSortDup="+msSortDups+" msWrite="+msWrite);
    }
    //else Util.prt("Done write offset="+out.length());
  }
}
