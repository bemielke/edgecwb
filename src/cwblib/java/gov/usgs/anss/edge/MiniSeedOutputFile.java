/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.edge;

import java.io.IOException;
import java.io.FileOutputStream;
import gov.usgs.anss.util.Util;

/**
 * This class implements the MiniSeedOutputHandler and puts the data in a file. Generally you make
 * one of these and then pass it to RawToMiniSeed method overrideOutput() which gets RTMS putbuf()
 * to call this object to handle output. Each time the mini-seed blockette is put in the file.
 *
 * @author davidketchum
 */
public final class MiniSeedOutputFile implements MiniSeedOutputHandler {

  FileOutputStream out;

  /**
   * create a file for outputing miniseed
   *
   * @param filename The filename into which to put the file
   */
  public MiniSeedOutputFile(String filename) {
    try {
      out = new FileOutputStream(filename.replaceAll(" ", "_"));
    } catch (IOException e) {
      Util.IOErrorPrint(e, "MSOF: Error opening file");
    }
  }

  /**
   * this putbuf is called by the Mini-seed compressor (usually in RawToMiniSeed) if its output has
   * been overriden to this object. Write the data ini the file.
   *
   * @param b The buffer of mini-seed data
   * @param size The size of miniseed
   */
  @Override
  public void putbuf(byte[] b, int size) {
    try {
      //MiniSeed ms = RawToMiniSeed.getMiniSeed(b, 0, size); //, size, size)new MiniSeed(b);
      //Util.prt("Out:"+ms.getSeedName()+" "+ms.getTimeString()+
      //        " ns="+ms.getNsamp()+" rt="+ms.getRate()+" sz="+size+" "+ms.getBlockSize());
      out.write(b, 0, size);
    } catch (IOException e) {
      Util.IOErrorPrint(e, "MSOF: Error writing data");
    }
    /*catch(IllegalSeednameException e) {
      Util.prt("MSOF: illegal seedname exception ");
    }*/
  }

  /**
   * Close the file
   */
  @Override
  public void close() {
    try {
      if (out != null) {
        out.close();
      }
    } catch (IOException e) {
      Util.IOErrorPrint(e, "MSOF: Error closing data");
    }
  }
}
