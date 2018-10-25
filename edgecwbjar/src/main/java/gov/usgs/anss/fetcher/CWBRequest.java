/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.fetcher;

import gov.usgs.anss.edge.FetchList;
import gov.usgs.anss.cwbqueryclient.EdgeQueryClient;
import gov.usgs.anss.edge.RawDisk;
import gov.usgs.anss.seed.MiniSeed;
import gov.usgs.anss.util.Util;
import java.io.IOException;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;

/**
 * Get a time series segment from a CWB for use by the fetcher. Calls the EdgeQueryClient and
 * processes the returned blocks.
 *
 * @author davidketchum
 */
public final class CWBRequest extends Fetcher {

  private static final ArrayList<MiniSeed> mssempty = new ArrayList<>(1); // an empty for returning nothing
  private String station;

  @Override
  public void closeCleanup() {
    prta(station + " closeCleanup - terminate ");
    //if(mysqlanss != null) mysqlanss.terminate();
  }

  public CWBRequest(String[] args) throws SQLException {
    super(args);
    if (getSeedname().length() >= 10) {
      station = getSeedname().substring(2, 7).trim() + getSeedname().substring(10).trim();
      tag = "CWB" + getName().substring(getName().indexOf("-")) + "-" + getSeedname().substring(2, 7).trim() + ":";
    }
    prt(tag + " start " + orgSeedname + " " + host + "/" + port);

  }

  @Override
  public void startup() {
    if (localBaler) {
      return;    // local cwbrequest, do not try Baler stuff.
    }
    checkNewDay();
  }

  /**
   * retrieve the data in the fetch entry
   *
   * @param fetch A fetch list object containing the fetch details
   * @return The MiniSeed data resulting from the fetch
   */
  @Override
  public ArrayList<MiniSeed> getData(FetchList fetch) {
    String starting = fetch.getStart().toString().substring(0, 19).replaceAll("-", "/") + "." + Util.df3(fetch.getStartMS());
    Timestamp end = new Timestamp(fetch.getStart().getTime() + (long) (fetch.getDuration() * 1000. + fetch.getStartMS()));
    String sname = fetch.getSeedname();

    // If the check CWB first variable is true, then we should check our output CWB for this data first, if it does not have it then proceed
    ArrayList<ArrayList<MiniSeed>> msscwb = null;
    if (chkCWBFirst) {
      String command = "-s " + fetch.getSeedname().replaceAll(" ", "-")
              + " -t null -b " + starting.replaceAll(" ", "-") + " -d " + fetch.getDuration() + " -h " + cwbIP + " -p " + cwbPort;
      prt(tag + " try CWB first " + sname + " " + cwbIP + ":" + cwbPort + " " + starting + " to " + end.toString().substring(0, 19) + " "
              + ((end.getTime() - fetch.getStart().getTime()) / 1000.));
      prta(tag + " " + command);
      msscwb = EdgeQueryClient.query(command);
    }

    String command = "-s " + fetch.getSeedname().replaceAll(" ", "-")
            + " -t null -b " + starting.replaceAll(" ", "-") + " -d " + fetch.getDuration() + " -h " + host + " -p " + port;
    prt(tag + " " + sname + " " + host + ":" + port + " " + starting + " to " + end.toString().substring(0, 19) + " "
            + ((end.getTime() - fetch.getStart().getTime()) / 1000.));
    prta(tag + " " + command);
    ArrayList<ArrayList<MiniSeed>> mss2 = EdgeQueryClient.query(command);
    if (mss2 == null) {
      prt(tag + " return was null");
      return mssempty;
    }    // Never return null, just return no block, null means set request to no data
    prt(tag + " return size mss=" + mss2.size() + " size=" + (mss2.size() >= 1 ? mss2.get(0).size() : "null"));
    if (mss2.isEmpty()) {
      return null;
    }
    if (msscwb != null) {
      if (!msscwb.isEmpty()) {
        if (!msscwb.get(0).isEmpty()) {
          // We got some data from the output CWB, remove any duplicated blocks
          for (MiniSeed ms : mss2.get(0)) {
            for (MiniSeed msc : msscwb.get(0)) {
              if (ms.isDuplicate(msc)) {
                ms.getBuf()[7] = '~';    // Mark all duplicates as out
              }
            }
          }
        }
      }
    }
    return mss2.get(0).isEmpty() ? null : mss2.get(0);

  }

  /**
   * @param args the command line arguments
   */
  public static void main(String[] args) {

    Util.setModeGMT();
    Util.init("edge.prop");
    boolean single = false;
    if (args.length == 0) {
      args = "-s USDUG--BHZ00 -b 2015/08/10 12:00 -d 300 -1 -t GP -h cwbpub -p 2061".split("\\s");
    }
    // set the single mode flag
    for (String arg : args) {
      if (arg.equals("-1")) {
        single = true;
      }
    }

    // Try to start up a new requestor
    try {
      CWBRequest cwbrequest = new CWBRequest(args);
      if (single) {
        ArrayList<MiniSeed> mss = cwbrequest.getData(cwbrequest.getFetchList().get(0));
        if (mss == null) {
          Util.prt("NO DATA was returned for fetch=" + cwbrequest.getFetchList().get(0));
        } else if (mss.isEmpty()) {
          Util.prt("Empty data return - normally leave fetch open " + cwbrequest.getFetchList().get(0));
        } else {
          try (RawDisk rw = new RawDisk("tmp.ms", "rw")) {
            for (int i = 0; i < mss.size(); i++) {
              rw.writeBlock(i, mss.get(i).getBuf(), 0, 512);
              Util.prt(mss.get(i).toString());
            }
            rw.setLength(mss.size() * 512L);
          }
        }
        System.exit(0);
      } else {      // Do fetch from table mode 
        cwbrequest.startit();
        while (cwbrequest.isAlive()) {
          Util.sleep(1000);
        }
      }
    } catch (IOException e) {
      Util.prt("IOError=" + e);
      e.printStackTrace();
    } catch (SQLException e) {
      Util.SQLErrorPrint(e, "Impossible in test mode");
    }

  }
}
