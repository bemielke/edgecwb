/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.cwbqueryclient;

import gov.usgs.anss.edgemom.HoldingSender;
import gov.usgs.anss.seed.MiniSeed;
import gov.usgs.anss.util.*;
import java.io.IOException;
import java.net.*;
import java.util.ArrayList;
import java.util.Collections;

/**
 * This outputer just sends the blocks to the TCP/IP based holdings server. The user can override
 * the gacqdb/7996:CW if necessary.
 *
 * <pre>
 * -hold   Ip.adres:port:type Use a HoldingsSender pointed at the given ip and port and using the gap type given
 * </pre>
 *
 * @author davidketchum
 */
public class HoldingOutputer extends Outputer {

  boolean dbg;
  HoldingSender hs;

  /**
   * Creates a new instance of HoldingsOutput
   */
  public HoldingOutputer() {

  }

  @Override
  public void makeFile(String comp, String filename, String filemask, ArrayList<MiniSeed> blks,
          java.util.Date beg, double duration, String[] args) throws IOException {
    MiniSeed ms2;
    String holdingIP = "gacqdb";
    String holdingType = "CW";
    int holdingPort = 7996;
    for (String arg : args) {
      if (arg.indexOf("-hold") == 0) {
        String[] a = arg.split(":");
        if (a.length == 4) {
          holdingIP = a[1];
          holdingPort = Integer.parseInt(a[2]);
          holdingType = a[3];
        }
      }
    }
    if (hs == null) {
      try {
        hs = new HoldingSender("-h " + holdingIP + " -p " + holdingPort + " -t " + holdingType + " -q 10000 -tcp", "");
      } catch (UnknownHostException e) {
        Util.prt("Unknown host exception host=" + holdingIP);
        System.exit(1);
      }
    }

    Collections.sort(blks);
    for (MiniSeed blk : blks) {
      ms2 = (MiniSeed) blk;
      while (hs.getNleft() < 100) {
        //Util.prta("Slept 1 left="+hs.getNleft()+" "+hs.getStatusString());
        try {
          Thread.sleep(100);
        } catch (InterruptedException expected) {
        }
      }
      hs.send(ms2);
    }
  }

}
