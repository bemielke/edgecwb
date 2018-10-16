/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.cwbqueryclient;

import java.util.ArrayList;
import java.io.IOException;
import gov.usgs.anss.seed.MiniSeed;

/**
 * This abstract class is extended by every output class that accepts a list of MiniSEED blocks from
 * CWBQuery and then creates the new output file. The form of the makefile() methods is shared
 * across all outputers.
 *
 * @author davidketchum
 */
abstract public class Outputer {

  /**
   * Creates a new instance of Outputer
   */
  public Outputer() {
  }

  /**
   * the main routine gives an UNSORTED list in blks. If it needs to be sorted call
   * Collections.sort(blks);
   *
   * @param comp NNSSSSSCCCLL channel name
   * @param filename Filename for output
   * @param mask The file mask with % operators for constructing a name (filename was derived from
   * this)
   * @param blks The list of MiniSeed blocks to put into the output file
   * @param beg The beginning time of the time series
   * @param duration The duration of time series desired
   * @param args The original arguments to EdgeQueryClient so that additional args can be used by
   * each outputer
   * @throws java.io.IOException
   */
  abstract public void makeFile(String comp, String filename, String mask, ArrayList<MiniSeed> blks,
          java.util.Date beg, double duration, String[] args) throws IOException;
}
