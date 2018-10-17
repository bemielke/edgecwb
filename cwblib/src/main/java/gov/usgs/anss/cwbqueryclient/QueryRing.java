/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.cwbqueryclient;

import gov.usgs.anss.seed.MiniSeed;
import gov.usgs.anss.seed.Steim2Object;
import gov.usgs.anss.util.*;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;

/**
 * This class keeps a buffer of a certain length of in the clear data and satisfies requests from
 * it. When data is needed which is not in the buffer, a query is made and more data read in from a
 * preDuration for a duration in length. The class is best used to process data in chunks
 * continuously from some start time, or for processing data continuously near real time. This is a
 * subclass of ZeroFilledSpan which is used to hold and manage the data in the clear.
 *
 * You can think of this as a little window of data that you can slide around by asking for
 * different times. It hides all of the details of doing queries and converting to in the clear data
 * and handling gaps in the data.
 *
 * Its a good batch class for getting time series from the CWB.
 *
 * @author davidketchum
 */
public final class QueryRing extends ZeroFilledSpan {

  public static int FILL_VALUE = 2147000000;
  //EdgeQueryClient query;
  private final String seedname;      // The seed channel that is buffered and returne by this object
  //private DecimalFormat df2 = new DecimalFormat("00");
  private String host;
  private int port;
  private final double duration;
  private final double preDuration;
  private long lastNullQuery;
  private final String[] args;
  private final GregorianCalendar now = new GregorianCalendar();  // This is used to do time computations in chkData()
  private final Steim2Object steim2 = new Steim2Object();

  /**
   * Creates a new instance of QueryRing set the duration to make the reads not happen too often and
   * the preDuration to be how far in the past you might ask for data from the normal near-real time
   * processing time.
   *
   * @param h The host where the query server is running, if null or "", its gcwb.cr.usgs.gov
   * @param p The port where the cwb is running, if zero, defaults to 2061
   * @param st The starting time for the initialization
   * @param dur The length of the ring buffer in seconds.
   * @param preDur the length of time before the "real time" to make sure is available
   * @param name The seed name of the data channel.
   */
  public QueryRing(String h, int p, String name, GregorianCalendar st,
          double dur, double preDur) {
    super(new ArrayList<MiniSeed>(1), st, dur, FILL_VALUE);
    seedname = name;
    host = h;
    port = p;
    start.setTimeInMillis(st.getTimeInMillis());
    duration = dur;
    preDuration = preDur;
    if (h == null) {
      host = "cwbpub.cr.usgs.gov";
    } else if (h.equals("")) {
      host = "cwbpub.cr.usgs.gov";
    }
    if (port <= 0) {
      port = 2061;
    }
    args = new String[16];
    args[0] = "-s";
    args[1] = seedname;
    args[2] = "-t";
    args[3] = "null";
    args[4] = "-d";
    args[5] = "" + duration;
    args[6] = "-q";
    args[7] = "-b";
    args[8] = "" + start.get(Calendar.YEAR) + "," + start.get(Calendar.DAY_OF_YEAR) + "-"
            + Util.df2(start.get(Calendar.HOUR_OF_DAY)) + ":" + Util.df2(start.get(Calendar.MINUTE))
            + ":" + Util.df2(start.get(Calendar.SECOND));
    args[9] = "-h";
    args[10] = host;
    args[11] = "-to";
    args[12] = "60";
    args[13] = "-uf";
    args[14] = "-p";
    args[15] = "" + port;
    ArrayList<ArrayList<MiniSeed>> mslist = EdgeQueryClient.query(args);
    if (mslist == null) {
      Util.prta("Query returned null!");
    } else if (mslist.size() == 1) {
      refill(mslist.get(0), start, duration, FILL_VALUE);
    }
    EdgeQueryClient.freeQueryBlocks(mslist, "init", null);

  }

  /**
   * return a chunk of time series starting at a given time for a number of samples. The start time
   * is modified to be the time of the first sample returned.
   *
   * @param starting The starting time as a GregorianCalendar (at return the time of the first
   * sample)
   * @param nsamp The number of samples to return
   * @param d A data array which must be at least nsamp long where the data are returned.
   * @param allowGaps If true, then the data are returned for the whole interval with no-data
   * values, if false, only data available to the first gap is returned
   * @return The number of samples actually returned, will be less than nsamp if the data are not
   * yet available
   * @throws java.io.IOException
   */
  public int getDataAt(GregorianCalendar starting, int nsamp, double[] d, boolean allowGaps) throws IOException {
    if (chkData(starting, nsamp)) {
      return getData(starting, nsamp, d, allowGaps);
    } else {
      return 0;
    }
  }

  /**
   * return a chunk of time series starting at a given time for a number of samples. The start time
   * is modified to be the time of the first sample returned.
   *
   * @param starting The starting time as a GregorianCalendar (at return the time of the first
   * sample)
   * @param nsamp The number of samples to return
   * @param d A data array which must be at least nsamp long where the data are returned.
   * @param allowGaps If true, then the data are returned for the whole interval with no-data
   * values, if false, only data available to the first gap is returned
   * @return The number of samples actually returned, will be less than nsamp if the data are not
   * yet available.
   */

  /**
   * return a chunk of time series starting at a given time for a number of samples.The start time
   * is modified to be the time of the first sample returned.
   *
   * @param starting The starting time as a GregorianCalendar (at return the time of the first
   * sample)
   * @param nsamp The number of samples to return
   * @param d A data array which must be at least nsamp long where the data are returned.
   * @param allowGaps If true, then the data are returned for the whole interval with no-data
   * values, if false, only data available to the first gap is returned
   * @return The number of samples actually returned, will be less than nsamp if the data are not
   * yet available.
   * @throws java.io.IOException
   */
  public int getDataAt(GregorianCalendar starting, int nsamp, float[] d, boolean allowGaps) throws IOException {
    if (chkData(starting, nsamp)) {
      return getData(starting, nsamp, d, allowGaps);
    } else {
      return 0;
    }
  }

  /**
   * return a chunk of time series starting at a given time for a number of samples. The start time
   * is modified to be the time of the first sample returned.
   *
   * @param starting The starting time as a GregorianCalendar (at return the time of the first
   * sample)
   * @param nsamp The number of samples to return
   * @param d A data array which must be at least nsamp long where the data are returned.
   * @param allowGaps If true, then the data are returned for the whole interval with no-data
   * values, if false, only data available to the first gap is returned
   * @return The number of samples actually returned, will be less than nsamp if the data are not
   * yet available.
   */

  /**
   * return a chunk of time series starting at a given time for a number of samples.The start time
   * is modified to be the time of the first sample returned.
   *
   * @param starting The starting time as a GregorianCalendar (at return the time of the first
   * sample)
   * @param nsamp The number of samples to return
   * @param d A data array which must be at least nsamp long where the data are returned.
   * @param allowGaps If true, then the data are returned for the whole interval with no-data
   * values, if false, only data available to the first gap is returned
   * @return The number of samples actually returned, will be less than nsamp if the data are not
   * yet available.
   * @throws java.io.IOException
   */
  public int getDataAt(GregorianCalendar starting, int nsamp, int[] d, boolean allowGaps) throws IOException {
    if (chkData(starting, nsamp)) {
      return getData(starting, nsamp, d, allowGaps);
    } else {
      return 0;
    }
  }

  /**
   * Check whether the data is already in this object, if not, make a request to make it so
   *
   * @param starting The starting time as a GregorianCalendar (at return the time of the first
   * sample)
   * @param nsamp The number of samples to return
   *
   */
  private synchronized boolean chkData(GregorianCalendar starting, int nsamp) throws IOException {
    boolean done = false;
    for (int loop = 0; loop < 120; loop++) {
      if (dbg) {
        EdgeQueryClient.setDebugMiniSeedPool(dbg);
      }
      if (dbg) {
        Util.prta("Ask for data at " + Util.ascdate(starting) + " " + Util.asctime2(starting) + " ns=" + nsamp
                + " start=" + Util.ascdate(start) + " " + Util.asctime2(start) + " lastTime=" + Util.ascdate(lastTime) + " " + Util.asctime2(lastTime));
      }
      // If the data is time entirely in the buffer, return true, else false
      if (starting.getTimeInMillis() - start.getTimeInMillis() > -msover2 - 1
              && starting.getTimeInMillis() + nsamp / rate * 1000 < lastTime) {
        return true;  // its good
      }
      if (starting.getTimeInMillis() - start.getTimeInMillis() < -msover2 - 1) {
        if (dbg) {
          Util.prta("Data not in range buf start=" + Util.ascdate(start) + " " + Util.asctime2(start)
                  + " want " + Util.ascdate(starting) + " " + Util.asctime2(starting) + " dur=" + duration);
        }
        now.setTimeInMillis(starting.getTimeInMillis());
        now.add(Calendar.MILLISECOND, (int) (-preDuration * 1000.));
        if (dbg) {
          Util.prta("Query for data start=" + Util.ascdate(now) + " " + Util.asctime2(now) + " predur=" + preDuration);
        }
        args[8] = "" + now.get(Calendar.YEAR) + "," + now.get(Calendar.DAY_OF_YEAR) + "-"
                + Util.df2(now.get(Calendar.HOUR_OF_DAY)) + ":" + Util.df2(now.get(Calendar.MINUTE))
                + ":" + Util.df2(now.get(Calendar.SECOND));
        ArrayList<ArrayList<MiniSeed>> mslist = EdgeQueryClient.query(args);
        if (mslist == null) {
          prtError(args);
          Util.sleep(1000);
          continue;
        } else if (mslist.size() == 1) {
          refill(mslist.get(0), now, duration, FILL_VALUE);
        }
        EdgeQueryClient.freeQueryBlocks(mslist, "Start", null);
        if (dbg) {
          Util.prt(EdgeQueryClient.getMiniSeedPool().toString());
        }
      } else if (starting.getTimeInMillis() + nsamp / rate * 1000 >= lastTime - 1000) {// is this past the data in the buffer
        // We just need to fill in some data
        if (dbg) {
          Util.prta("Data ask is off end start=" + Util.ascdate(start) + " " + Util.asctime2(start) + " dur=" + duration);
        }
        // We should query from the lastTime (last data sample put in the buffer, or the beginning of the buffer which ever is later
        now.setTimeInMillis(Math.max(lastTime, start.getTimeInMillis()));     // set to last value put in
        args[8] = "" + now.get(Calendar.YEAR) + "," + now.get(Calendar.DAY_OF_YEAR) + "-"
                + Util.df2(now.get(Calendar.HOUR_OF_DAY)) + ":" + Util.df2(now.get(Calendar.MINUTE))
                + ":" + Util.df2(now.get(Calendar.SECOND));
        // Ask for data length that will fill the buffer rom last data we have to end of buffer (or start if last data is too old
        long endingTime = (long) (starting.getTimeInMillis() + nsamp / rate * 1000);
        double secs = (Math.max(endingTime, getMillisAt(data.length - 1)) - now.getTimeInMillis() + 1000) / 1000.;
        args[5] = "" + secs;
        if (dbg) {
          Util.prta("Query for data start=" + Util.ascdate(start) + " " + Util.asctime2(start)
                  + " predur=" + preDuration + " queryTime=" + args[8] + " dur=" + args[5]);
        }
        ArrayList<ArrayList<MiniSeed>> mslist = EdgeQueryClient.query(args);
        if (mslist == null) {
          prtError(args);
          Util.sleep(1000);
          continue;
        } else {
          if (dbg) {
            Util.prta("Query returned " + (mslist.isEmpty() ? "null" : mslist.get(0).size()));
          }
          shiftAdd((mslist.isEmpty() ? null : mslist.get(0)), starting.getTimeInMillis(),
                  (long) (starting.getTimeInMillis() + nsamp / rate * 1000), preDuration);
        }
        EdgeQueryClient.freeQueryBlocks(mslist, "End", null);
        if (dbg) {
          Util.prta(EdgeQueryClient.getMiniSeedPool().toString());
        }
      }
      // If the data is time entirely in the buffer, return true, else false
      if (starting.getTimeInMillis() - start.getTimeInMillis() > -msover2 - 1
              && starting.getTimeInMillis() + nsamp / rate * 1000 < lastTime) {
        return true;
      }
      if (loop > 120) {
        throw new IOException("Cannot get data from CWB");
      }
      return false;
    }
    return false;
  }
  StringBuilder errsb = new StringBuilder(100);

  private void prtError(String[] args) {
    if (errsb.length() > 0) {
      errsb.delete(0, errsb.length());
    }
    errsb.append("Query return null! args=");
    for (String arg : args) {
      errsb.append(arg).append(" ");
    }
    Util.prta(errsb.toString());
  }

  /**
   * test routine - This reads in a whole day of data from the CWB using the normal query method to
   * get an ArrayList of ArrayList of MiniSeed and puts the miniSEED into a ZeroFilledSpan.
   *
   * In then uses a QueryRing to open the beginning of the day an march through it comparing the
   * data by time as it goes.
   *
   * @param args The args
   */
  public static void main(String[] args) {
    DecimalFormat df2 = new DecimalFormat("00");
    Util.setModeGMT();
    // Make up a time in 2013
    GregorianCalendar time = new GregorianCalendar(2013, 07, 26, 0, 0, 0); // remember 03 is April in GregorianCalendars
    time.setTimeInMillis(time.getTimeInMillis() / 1000 * 1000);
    // Set up a query ring for the 
    QueryRing ring = new QueryRing("137.227.224.97", 2061, "IWDLMT BHZ00", time, 600., 30.);
    ring.setDebug(false);
    /*
    // Make up a string for this same time to get the whole day using a normal query
    String s = "-h localhost -s IWDLMT-BHZ00 -t null -d 1d -q -b "+time.get(Calendar.YEAR)+","+time.get(Calendar.DAY_OF_YEAR)+"-"+
            Util.df2(time.get(Calendar.HOUR_OF_DAY))+":"+
            Util.df2(time.get(Calendar.MINUTE))+":"+
            Util.df2(time.get(Calendar.SECOND));

    // Get the MiniSeed for the data from the query server
    ArrayList<ArrayList<MiniSeed>> mslist = EdgeQueryClient.query(s);
    // Normally the returned list will only contain one Arraylist<MiniSeed>>
    if(mslist.size() >= 1) {
      // create a Span with the full day of data and user FILL_VALUE=2147000000 instead of zero for the fill value
      ZeroFilledSpan span = new ZeroFilledSpan(mslist.get(0),FILL_VALUE);
      Util.prta("ring="+ring);      // Show the initial state of the QueryRing
      Util.prta("span="+span);      // Show the initial state of the ZeroFilledSpan
      int [] d = new int[400];
      int [] d2 = new int[400];
      // every 10 seconds for the entire day
      for(int i=0; i<86400; i=i+10) {
        boolean err=false;
        int ns = ring.getDataAt(time, 400, d, false);  // Get 4000 samples (10 seconds) of data at this time from the ring
        int ns2 = span.getData(time, 400, d2, false);  // Get the same samples from the full span
        // check that all of the data matches
        if(ns != ns2) 
          Util.prt("i="+i+" "+Util.ascdate(time)+" "+Util.asctime2(time)+
                " Did not get matching number of samples ring ns="+ns+" span ns="+ns2);
        for(int j=0; j<Math.min(ns2,ns); j++) {
          if(d[j] != d2[j]) {
            Util.prt(j+" "+d[j]+"!="+d2[j]);    // If results do not match print out offset and value
            err=true;
          }
        }
        if(i % 3600 == 0 || err) Util.prta("i="+i+" "+Util.ascdate(time)+" "+Util.asctime2(time));
        for(int j=0; j<400; j++) {d[j]=0; d2[j]=0;}    // clear the arrays
        time.add(Calendar.MILLISECOND, 10000);        // Update time by 10 seconds
      }
    }
    * */

    // Second test - loop near real time - and show data from ring
    time.setTimeInMillis((System.currentTimeMillis() - 1800000) / 10000L * 10000L);
    time = new GregorianCalendar(2011, 07, 23, 0, 0, 0); // remember 03 is April in GregorianCalendars
    time.setTimeInMillis(time.getTimeInMillis() / 1000 * 1000);
    Util.prta("Starting real time ring test at " + Util.ascdate(time) + " " + Util.asctime2(time));
    ring = new QueryRing("137.227.224.97", 0, "TAT25A BHZ", time, 200., 30.);  // remember 2 spaces to pad station name
    ring.setDebug(true);
    int npts;
    double[] d = new double[500];      // Use doubles fetcher this time
    for (;;) {
      try {
        while ((npts = ring.getDataAt(time, 400, d, false)) != 400) {
          Util.sleep(100);
          if (System.currentTimeMillis() - ring.getLastTime() > 300000) {
            time.setTimeInMillis(time.getTimeInMillis() + 10000);
            Util.prt("Stuck on gap for 300 secs - advance 10 secs " + Util.asctime2(time) + " npts=" + npts);
          }
        }
        // 10 seconds of data is in the buffer, do something with it here
        Util.prta("Processing data for " + Util.ascdate(time) + " " + Util.asctime2(time) + " npts=" + 400);
        time.setTimeInMillis(time.getTimeInMillis() + 10000);
      } catch (IOException e) {
        Util.prta("Query ring threw an exception ");
        e.printStackTrace();
      }
    }
  }
}
