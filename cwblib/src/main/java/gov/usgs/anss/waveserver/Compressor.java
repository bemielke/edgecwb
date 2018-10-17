/*
 *  This software is in the public domain because it contains materials 
 *  that originally came from the United States Geological Survey, 
 *  an agency of the United States Department of Interior. For more 
 *  information, see the official USGS copyright policy at 
 *  http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.waveserver;

import java.util.zip.Deflater;
import java.util.zip.Inflater;
import java.util.zip.DataFormatException;

/**
 * Compresses an array of bytes using the JDK <code>Inflator</code> and <code>Deflator</code>
 * classes.
 *
 * @author U.S. Geological Survey <ketchum at usgs.gov>
 */
public class Compressor {

  /**
   * Compresses an array of bytes using the JDK <code>Inflator</code>/ <code>Deflator</code>
   * implementation. Compression ratios comparable to gzip are attained.
   *
   * @param bytes the array of bytes
   * @param level the compression level (1[least]-9[most])
   * @param ofs number of first byte in array to process
   * @param len length of processed array zone
   * @param output Array to receive the compressed data
   * @return the compressed array of bytes
   */
  public static int compress(byte[] bytes, int level, int ofs, int len, byte[] output) {
    Deflater deflater = new Deflater(level);
    deflater.setInput(bytes, ofs, len);
    deflater.finish();
    int ncompressed = deflater.deflate(output);
    return ncompressed;
  }

  /**
   * @param bytes Input bytes
   * @param bufferSize of compressed bytes
   * @param output The byte buffer to get the uncompressed data
   * @return the decompressed array of bytes
   * @throws java.util.zip.DataFormatException
   */
  public static int decompress(byte[] bytes, int bufferSize, byte[] output) throws DataFormatException {
    //try
    //{
    Inflater inflater = new Inflater();
    inflater.setInput(bytes, 0, bufferSize);
    int nbytes = inflater.inflate(output, 0, output.length);
    if (!inflater.finished()) {
      inflater.end();
      throw new RuntimeException("decompress output buffer too small in.len=" + bytes.length + " out.len=" + output.length + " remaining=" + inflater.getRemaining());
    }
    inflater.end();
    return nbytes;
    //}
    //catch (Exception e)
    //{
    //    e.printStackTrace();
    //}
    //return 0;
  }
}
