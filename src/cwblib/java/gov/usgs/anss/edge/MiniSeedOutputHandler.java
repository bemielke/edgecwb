/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.edge;

/**
 * This provides the interface that needs to be implemented for a class which can get Mini-Seed
 * output (primarily from the compressor in RawToMiniSeed).
 *
 * @author davidketchum
 */
public interface MiniSeedOutputHandler {

  public void putbuf(byte[] b, int size);

  public void close();
}
