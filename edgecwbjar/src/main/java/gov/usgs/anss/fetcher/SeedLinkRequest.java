/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright 
 */
package gov.usgs.anss.fetcher;

import gov.usgs.anss.edge.FetchList;
import gov.usgs.anss.edge.IllegalSeednameException;
import gov.usgs.anss.edge.RawDisk;
import gov.usgs.anss.seed.MiniSeed;
import gov.usgs.anss.util.Util;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.GregorianCalendar;
import java.util.Calendar;
import nl.knmi.orfeus.seedlink.SLLog;
import nl.knmi.orfeus.seedlink.SLPacket;
import nl.knmi.orfeus.seedlink.SeedLinkException;
import nl.knmi.orfeus.seedlink.client.SeedLinkConnection;

/**
 * This class gets a random section of data from a SeedLinkServer for use in the Fetcher. Since most
 * SeedLinkServers do not support this, its use is limited (no known SeedLinks where it might
 * work!).
 *
 * @author davidketchum
 */
public final class SeedLinkRequest extends Fetcher {

  private int blocksize;
  //private Subprocess sp;
  private final String station;
  private final int netTimeout = 120;
  private final int netDelay = 20;
  private final int keepAlive = 0;
  private String bTime;
  private String eTime;
  private String multiselect;
  private String selectors;
  
  @Override
  public void closeCleanup() {
    prta(station + " closeCleanup - terminate mysql and any subprocesses");
    //if(mysqlanss != null) mysqlanss.terminate();
    /*if (sp != null) {
      sp.terminate();
    }*/
  }

  public SeedLinkRequest(String[] args) throws SQLException {
    super(args);
    station = getSeedname().substring(2, 7).trim() + getSeedname().substring(10).trim();

    // Since this is a thread mysqanss might be in use by other threads.  Always
    // create
    //tag="CWB"+getName().substring(getName().indexOf("-"))+"-"+getSeedname().substring(2,7).trim()+":";
    prt(tag + " start " + orgSeedname + " " + host + "/" + port);

  }

  @Override
  public void startup() {

  }

  /**
   * retrieve the data in the fetch entry
   *
   * @param fetch A fetch list object containing the fetch details
   * @return The MiniSeed data resulting from the fetch
   */
  @Override
  public ArrayList<MiniSeed> getData(FetchList fetch) {
    checkNewDay();
    ArrayList<MiniSeed> mss = new ArrayList<>(1);
    //String starting = fetch.getStart().toString().substring(0,19).replaceAll("-", "/");
    GregorianCalendar timeTemp = new GregorianCalendar();
    timeTemp.setTimeInMillis(fetch.getStart().getTime());
    int bYear = timeTemp.get(Calendar.YEAR);
    int bMonth = timeTemp.get(Calendar.MONTH) + 1;
    int bDay = timeTemp.get(Calendar.DAY_OF_MONTH);
    int bHour = timeTemp.get(Calendar.HOUR_OF_DAY);
    int bMinute = timeTemp.get(Calendar.MINUTE);
    int bSecond = timeTemp.get(Calendar.SECOND);
    bTime = "" + bYear + "," + bMonth + "," + bDay + "," + bHour + "," + bMinute + "," + bSecond;

    timeTemp.setTimeInMillis(timeTemp.getTimeInMillis() + (long) (fetch.getDuration() * 1000. + fetch.getStartMS()));
    int eYear = timeTemp.get(Calendar.YEAR);
    int eMonth = timeTemp.get(Calendar.MONTH) + 1;
    int eDay = timeTemp.get(Calendar.DAY_OF_MONTH);
    int eHour = timeTemp.get(Calendar.HOUR_OF_DAY);
    int eMinute = timeTemp.get(Calendar.MINUTE);
    int eSecond = timeTemp.get(Calendar.SECOND);
    eTime = "" + eYear + "," + eMonth + "," + eDay + "," + eHour + "," + eMinute + "," + eSecond;

    //Timestamp end = new Timestamp(fetch.getStart().getTime()+(long) (fetch.getDuration()*1000.+fetch.getStartMS()));
    String sname = fetch.getSeedname();
    multiselect = sname.substring(0, 2) + "_" + sname.substring(2, 7).trim() + ":" + sname.substring(10).trim();
    if(sname.length() ==  12) multiselect += sname.substring(10,12) + sname.substring(7, 10);
    else multiselect += sname.substring(7, 10);
    SLLog sllog = new SLLog(3, getPrintStream(), "", getPrintStream(), "");
    prta("SLR: getData " + host + "/" + port + " " + multiselect + " " + bTime + "-" + eTime);
    SeedLinkConnection slconn = new SeedLinkConnection(sllog);
    slconn.setNetTimout(netTimeout);

    slconn.setNetDelay(netDelay);
    slconn.setKeepAlive(keepAlive);
    slconn.setSLAddress(host + ":" + port);

    slconn.setBeginTime(bTime);
    slconn.setEndTime(eTime);
    try {
      //slconn.setUniParams(multiselect, -1, null);
      slconn.parseStreamlist(multiselect, null);
    } catch (SeedLinkException e) {
      e.printStackTrace();
    }

    //
    String infolevel = null;
    try {
      if (infolevel != null) {
        slconn.requestInfo(infolevel);
      }

      // Loop with the connection manager
      SLPacket slpack;
      while ((slpack = slconn.collect(null)) != null) {
        if (slpack == SLPacket.SLTERMINATE) {
          prta("SLR: SLTERMINATE - request is done!");
          break;
        }
        if (slpack.getType() == SLPacket.TYPE_SLINF) {
          prta("SLR: got in TYPE_SLINF " + (new String(slpack.msrecord, 0, 20)));
        } else if (slpack.getType() == SLPacket.TYPE_SLINFT) {
          prta("SLR: got in TYPE_SLINFT " + slconn.getInfoString());
          continue;
        } else if (slpack == SLPacket.SLNOPACKET) {
          prta("SLR: got a SLNOPACKET");
          continue;
        } else if (slpack == SLPacket.SLERROR) {
          prta("SLR: got SLERROR");
          continue;
        }

        MiniSeed ms = new MiniSeed(slpack.msrecord, 0, 512);
        mss.add(ms);
        prta(" seq=" + slpack.getSequenceNumber() + " type=" + slpack.getType() + " ms=" + ms.toStringBuilder(null));
        // do something with packet
        //boolean terminate = packetHandler(count, slpack);
        if (terminate) {
          break;
        }
      }

    } catch (SeedLinkException sle) {
      System.out.print(fetch + ": " + sle);
    } catch (IllegalSeednameException ex) {
      Logger.getLogger(SeedLinkRequest.class.getName()).log(Level.SEVERE, null, ex);
    }
    // Close the SeedLinkConnection
    prta("SLR: close up socket mss=" + mss.size());
    slconn.close();
    if (mss.isEmpty()) {
      return null;
    }
    return mss;
  }

  /**
   * @param args the command line arguments
   */
  public static void main(String[] args) {
    try {

      Util.setModeGMT();
      Util.init("edge.prop");
      GregorianCalendar test = new GregorianCalendar();
      test.setTimeInMillis(test.getTime().getTime() - 900000);
      String date = Util.ascdatetime(test).toString();
      String yyyymmdd = date.substring(0, 10).trim();
      String hhmmss = date.substring(10).trim();
      //System.out.println(Util.today()+" "+Util.time());
      //args = "-s USAAM--BHZ00 -b 2015/8/13 22:00 -d 900 -1 -t GP -h gacq2 -p 16002".split("\\s");
      args = ("-s USDUG--BHZ00 -b " + yyyymmdd + " " + hhmmss + " -d 150 -1 -t BD -h cwbpub -p 18000").split("\\s");
      boolean single = false;
      //SLClient slc = null;
      for (String arg : args) {
        if (arg.equals("-1")) {
          single = true;
        }
      }

      //if(args.length == 0) args = "-s USDUG--BHZ00 -b 2015/08/10 12:00 -d 300 -1 -t GP -h cwbpub -p 2061".split("\\s");
      // set the single mode flag
      // Try to start up a new requestor
      SeedLinkRequest slrequest = new SeedLinkRequest(args);
      if (single) {
        ArrayList<MiniSeed> mss = slrequest.getData(slrequest.getFetchList().get(0));
        if (mss == null) {
          Util.prt("NO DATA was returned for fetch=" + slrequest.getFetchList().get(0));
        } else if (mss.isEmpty()) {
          Util.prt("Empty data return - normally leave fetch open " + slrequest.getFetchList().get(0));
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
        slrequest.startit();
        while (slrequest.isAlive()) {
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
