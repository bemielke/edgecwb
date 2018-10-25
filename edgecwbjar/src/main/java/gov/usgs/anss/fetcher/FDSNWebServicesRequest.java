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
import gov.usgs.anss.seed.MiniSeed;
import gov.usgs.anss.util.Subprocess;
import gov.usgs.anss.util.Util;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.sql.SQLException;
import java.util.ArrayList;
import java.io.File;
import java.util.GregorianCalendar;

/**
 * This class gets a segment of time series from a FDSN waveserver for the Fetcher. It makes up a
 * FDSNWebService command line for the FDSN CWBFetchData script and executes it, waits for
 * completion, and processes any returned file.
 *
 * @author sharper dcketchum
 */
public final class FDSNWebServicesRequest extends Fetcher {
  //private int blocksize;

  private Subprocess sp;
  private final GregorianCalendar end = new GregorianCalendar();   // create a Gregorian to do end time calculation
  private final ArrayList<MiniSeed> mss = new ArrayList<>(1);

  public FDSNWebServicesRequest(String[] args) throws SQLException, IOException {
    super(args);
  }

  @Override
  public void closeCleanup() {
    if (sp != null) {
      sp.terminate();
    }
  }

  @Override
  public void startup() {
    if (localBaler) {
      return;    // local FDSNWebServicesRequest, do not try Baler stuff.
    }
    checkNewDay();
  }

  @Override
  public ArrayList<MiniSeed> getData(FetchList fetch) throws IOException {
    checkNewDay();
    mss.clear();
    String seed = fetch.getSeedname();                 // get the seedname for parsing
    String starttime = fetch.getStart().toString();// get start time from fetch object
    end.setTimeInMillis((fetch.getStart().getTime() / 1000 * 1000) + fetch.getStartMS() + (long) (fetch.getDuration() * 1000. + 2000));
    String endtime = Util.ascdate(end).toString() + "T" + Util.asctime(end).toString();
    // Convert starttime and endtime to FetchData yyyy-MM-DDTHH:MM:SS.ssss format)

    String fetchStarttime = starttime.replace(' ', 'T');
    String fetchEndtime = endtime.replace('/', '-');
    String outfile = "FDSNWS_" + fetch.getID() + ".ms";       // The ID gives us a unique filename

    File f = new File(outfile);
    if (f.exists()) {
      f.delete();
    }

    String s = "scripts/CWBFetchData " + getHost() + " -N " + seed.substring(0, 2) // Command line for CWBFetchData
            + " -S " + seed.substring(2, 7).trim()
            + " -C " + seed.substring(7, 10)
            + " -L " + (seed.substring(10) + "  ").substring(0, 2).replaceAll(" ", "-")
            + " -s " + fetchStarttime
            + " -e " + fetchEndtime
            + " -o " + outfile + "";
    sp = new Subprocess(s);       // Start the fetch data

    try {
      sp.waitFor();
      prta(s + " stdout=" + sp.getOutput());
      if (sp.getErrorOutput().contains("of time series data") || sp.getErrorOutput().contains("No data selections to req")) {
        
        if (sp.getErrorOutput().contains(" 0 Bytes of time series data") || 
            sp.getErrorOutput().contains("Unrecognized data") ||
            sp.getErrorOutput().contains("No data selections to req")) {
          //prta("NO data returned.  return empty mss s=" + s);  // This will return an empty mss indicating no data, but there is hope

          int ndays;
          String band = fetch.getSeedname().substring(7,9);
          switch(band) {
            case "HN":
            case "HL":
            case "BL":
            case "BN":
              ndays=3;
              break;
            case "HH":
            case "BH":
            case "EH":
            case "SH":
              ndays=3;
              break;
            default:
              ndays=14;
          }
          f = new File(outfile);
          if (f.exists()) {
            f.delete();
          }
          if( System.currentTimeMillis() - fetch.getStart().getTime() > ndays*86400000L) {
            prta("  ** NO data is over " + ndays + " days.  Mark as hopeless");
            return null;
          }         
          return mss;
        } else {      // Got some data
          try ( // Read back any input file and put the MiniSEED blocks into the ArrayList.
                  RandomAccessFile raw = new RandomAccessFile(outfile, "r")) { // Should "r" be "rw"? It is rw in other fetchers
            prta("ok " + raw.length() + " bytes returned : " + s);
            byte[] buf = new byte[512];
            for (int offset = 0; offset < raw.length(); offset = offset + 512) {   // For each offset in this file
              raw.seek(offset);
              raw.read(buf, 0, 512);
              MiniSeed ms = new MiniSeed(buf);
              mss.add(ms);
              prt(ms.toString());
            }
          } catch (IllegalSeednameException ex) {
            prta("IllegalSeedname : " + ex.toString());
          } catch (IOException e) {
            f = new File(outfile);
            if (f.exists()) {
              f.delete();
              throw e;
            }
          }
          f = new File(outfile);
          if (f.exists()) {
            f.delete();
          }
        }
        return mss;
      } else {
        prta(" NOT OK - stderr:" + s + sp.getErrorOutput());
        f = new File(outfile);
        if (f.exists()) {
          f.delete();
        }
        return mss;         // this is really bad, return and mark hopeless
      }
    } catch (InterruptedException expected) {
    }
    return mss;
  }

  /**
   * test routine
   *
   * @param args the args
   */
  public static void main(String[] args) {
    Util.setModeGMT();
    Util.init("edge.prop");
    boolean single = false;
    args = "-1 -s XXCNT1-HHZ00 -b 2018-09-30 00:00:00 -d 3600 -h 136.177.124.141".split("\\s");
    for (String arg : args) {
      if (arg.equals("-1")) {
        single = true;
      }
    }

    //Subprocess a = new Subprocess("Curl /Users/sharper/bin/FetchData -N _GSN -C BHZ -s 2016-06-28T10:00:00 -e 2016-06-28T11:00:00 -o GSN.mseed -m GSN.metadata");
    try {
      FDSNWebServicesRequest FDSNWSRequest = new FDSNWebServicesRequest(args);
      if (single) {
        ArrayList<MiniSeed> mss = FDSNWSRequest.getData(FDSNWSRequest.getFetchList().get(0));
        if (mss == null) {
          Util.prt("NO DATA was returned for fetch=" + FDSNWSRequest.getFetchList().get(0));
        } else if (mss.isEmpty()) {
          Util.prt("Empty data return - normally leave fetch open " + FDSNWSRequest.getFetchList().get(0));
        } else {
          for (MiniSeed ms : mss) {
            Util.prt(ms.toString());
          }
        }
        System.exit(0);
      } else {      // Do fetch from table mode 
        FDSNWSRequest.startit();
        while (FDSNWSRequest.isAlive()) {
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
