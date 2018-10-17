/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.utility;

/**
 * This is a test of the MakeRawToMiniseed class. It reads in miniSEED files, breaks them into the
 * clear and then re-compresses them through both the one time and incremental methods.
 *
 * It writes out the resulting file for comparison
 *
 * @author ketchum
 */
import java.io.IOException;
import java.util.GregorianCalendar;
import java.util.Calendar;
import java.util.ArrayList;
import gov.usgs.anss.cwbqueryclient.ZeroFilledSpan;
import gov.usgs.anss.util.Util;
import gov.usgs.anss.edge.IllegalSeednameException;
import gov.usgs.anss.seed.MiniSeed;
import gov.usgs.anss.edge.RawDisk;

public final class TestMakeRawToMiniseed {

  /**
   * This is the test code for ReftekClient
   *
   * @param args the command line arguments
   */
  public static void main(String[] args) {
    byte[] buf = new byte[512];
    ArrayList<MiniSeed> blocks = new ArrayList<>(1000); // this will get the miniseed blocks created for this 
    MakeRawToMiniseed maker = new MakeRawToMiniseed();      // Create the maker object
    // to test read in a series of dataless files and write them back out with different names
    for (String arg : args) {
      try {
        RawDisk in = new RawDisk(arg, "rw"); // For each file on the argument line, read in all of
        for (int iblk = 0; iblk < in.length() / 512; iblk++) {   // the blocks to make a ZeroFilled span
          in.readBlock(buf, iblk, 512);
          blocks.add(new MiniSeed(buf, 0, 512));
        }
        // create a bit in the clear array with the data values in the test MiniSEED
        ZeroFilledSpan span = new ZeroFilledSpan(blocks);
        GregorianCalendar start = span.getGregorianCalendarAt(0);// Get start time of first sample
        // The maker wants the times as year, day of year, seconds since midnight on that day and microsecs
        int year = start.get(Calendar.YEAR);
        int doy = start.get(Calendar.DAY_OF_YEAR);
        int sec = (int) ((start.getTimeInMillis() % 86400000L) / 1000);
        int usec = (int) ((start.getTimeInMillis() % 1000L) * 1000);
        // This call make everything from one array of ings for the given NNSSSSSCCCLL and time
        // and returns the list of blocks
        String seedname = blocks.get(0).getSeedNameString();
        seedname = seedname.substring(0, 10) + "11";  // change the location code for clarity when plotting
        ArrayList<MiniSeed> ret = maker.loadTS(seedname, span.getNsamp(),
                span.getData(), year, doy, sec, usec, blocks.get(0).getRate(), 0, 0, 0, 50);
        for (int ii = 0; ii < ret.size(); ii++) {
          Util.prt(ii + " " + ret.get(ii));// dump out the output blocks
        }        // Optionall here call :
        maker.writeToDisk(arg + ".out");
        // You can also call this with little bits of time series by calling multiple times
        // THis just takes the bigger array and makes miniseed no more than 1000 samples at a time.
        seedname = seedname.substring(0, 10) + "22";     // change location code
        MakeRawToMiniseed maker2 = new MakeRawToMiniseed();
        double rate = blocks.get(0).getRate();
        int data[] = span.getData();
        int[] d = new int[1000];
        int nsamp = span.getNsamp();
        int off = 0;
        GregorianCalendar time = new GregorianCalendar();
        while (off < nsamp) {
          // calculate the time of the off sample
          time.setTimeInMillis(start.getTimeInMillis() + (long) (off / rate * 1000. + 0.5));
          // break it into the required components
          year = time.get(Calendar.YEAR);
          doy = time.get(Calendar.DAY_OF_YEAR);
          sec = (int) ((time.getTimeInMillis() % 86400000L) / 1000);
          usec = (int) ((time.getTimeInMillis() % 1000L) * 1000);
          int ns = Math.min(nsamp - off, 1000);         // how many samples to compress this time, limit 1000
          System.arraycopy(data, off, d, 0, ns);      // Put the data at the begging of d array
          maker2.loadTSIncrement(seedname, ns, d, year, doy, sec, usec, rate, 0, 0, 0, 50);// add it
          off += ns;                    // new offset into data of next sample to compress
        }
        ret = maker2.getBlocks();
        for (int ii = 0; ii < ret.size(); ii++) {
          Util.prt(ii + " " + ret.get(ii));
        }
        maker2.writeToDisk(arg + ".out2");
      } catch (IllegalSeednameException e) {
        Util.prt("" + e);
        e.printStackTrace();
      } catch (IOException e) {
        Util.prt("IOERR=" + e);
        e.printStackTrace();
      }
    }
    System.exit(0);
  }
}
