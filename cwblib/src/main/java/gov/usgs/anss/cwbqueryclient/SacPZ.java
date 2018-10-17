/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.cwbqueryclient;

import java.io.IOException;
import java.io.PrintWriter;
import gov.usgs.anss.util.*;

/**
 * Create a SAC Poles and zeros file from the MDS given a channel name and unit. This is created as
 * an object to hold the connector to the MDS and then just uses that connector after that.
 *
 * @author davidketchum
 */
public class SacPZ {

  private final String pzunit;
  private final StaSrv stasrv;

  /**
   * Creates a new instance of SacPZ
   *
   * @param stahost The host to use for metadata, if null or "", it uses cwb-pub
   * @param unit The unit of the desired response 'nm' or 'um'
   */
  public SacPZ(String stahost, String unit) {
    pzunit = unit;
    stasrv = new StaSrv(stahost, 2052);
  }

  /**
   * get a response string - if the MDS is not yet up it will wait for it to get up
   *
   * @param lastComp The component name to get from the MetaDataServer
   * @param time String representation of the time
   * @return The response from the MDS
   */
  public String getSACResponse(String lastComp, String time) {
    String s = stasrv.getSACResponse(lastComp, time, pzunit);
    int loop = 0;
    while (s.contains("MetaDataServer not up")) {
      if (loop++ % 15 == 1) {
        Util.prta("MetaDataServer is not up - waiting for connection");
      }
      try {
        Thread.sleep(2000);
      } catch (InterruptedException expected) {
      }
      s = stasrv.getSACResponse(lastComp, time, pzunit);
    }
    return s;
  }

  /**
   * get a response string and write it to the filename stub
   *
   * @param lastComp The component name to get from the MetaDataServer
   * @param time String representation of the time
   * @param filename the filename stub to write to (.pz will be concatenated at the end)
   * @return The response from the MDS
   */
  public String getSACResponse(String lastComp, String time, String filename) {
    String s = getSACResponse(lastComp, time);
    writeSACPZ(filename, s);
    return s;
  }

  /**
   * write out the string as a PZ - not normally called by user
   *
   * @param filename The filename stub to write to (.pz will be added at end)
   * @param s The response string to write (normally gotten by )
   */
  private void writeSACPZ(String filename, String s) {
    try {
      try (PrintWriter fout = new PrintWriter(filename + ".pz")) {
        fout.write(s);
      }
    } catch (IOException e) {
      Util.prta("Output error writing sac response file " + filename + ".resp e=" + e.getMessage());
    }
  }
}
