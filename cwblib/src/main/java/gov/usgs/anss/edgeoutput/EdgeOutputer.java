/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.edgeoutput;

import gov.usgs.edge.config.Channel;

/**
 * This class describes the methods that must be implemented by an output class from the EdgeCWB
 * system. The data are pused into the implementers so they can handle their data in a output
 * protocol specific ways. Classes like RingFile, RingServerSeedLink, etc must implement this class.
 * Most outputer use RingFIle output for their input to directly decouple. Any direct implementers
 * must never block.
 *
 * @author davidketchum
 */
abstract public class EdgeOutputer {

  /**
   * creates a null instance of this *'
   */
  boolean allowRestricted;

  public EdgeOutputer(boolean allow) {
    allowRestricted = allow;
  }

  /**
   * Creates a new instance of Outputers
   *
   * @param ms a buffer with MiniSeed data
   * @param chn The channel record
   */
  abstract public void processBlock(byte[] ms, Channel chn);

  abstract public void close();

  abstract public String getFilename();

  abstract public StringBuilder toStringBuilder(StringBuilder sb);

  /**
   * This processes a Raw block of data, do not use this with EdgeOutputers which only deal with
   * MiniSeed like the RingFile, but it is supported for things like RingServerSeedLink which can
   * send Raw data to a QueryMom DataLinkToQueryMom port
   *
   * @param c The channel record for this channel
   * @param seedname NNSSSSSCCCLL form
   * @param time The time since the 1970 epoch in millis
   * @param rate The digitizing rate for the packet
   * @param nsamp The number of samples in the data array
   * @param data Ints with raw data
   */
  abstract public void processRawBlock(Channel c, StringBuilder seedname, long time, double rate, int nsamp, int[] data);

}
