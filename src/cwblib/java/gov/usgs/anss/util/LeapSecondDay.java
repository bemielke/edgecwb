/*
 *  This software is in the public domain because it contains materials 
 *  that originally came from the United States Geological Survey, 
 *  an agency of the United States Department of Interior. For more 
 *  information, see the official USGS copyright policy at 
 *  http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.util;

import gov.usgs.alarm.SendEvent;
import gov.usgs.anss.net.Wget;
import java.io.File;
import java.io.RandomAccessFile;
import java.io.IOException;
import java.util.GregorianCalendar;

/**
 * This class keeps track of the julian days of the leap seconds and the total of the leap seconds.
 * It gets its base information from the U.S. Naval Observatory web site in a file where they keep
 * the TAI - UTC information (basically the two time standards differences). It writes this file
 * into .leapseconds when it has to be read from the web site. If the file .leapseconds does not
 * exist, the web site is read to create it. The thread will reread the file if it is newer than 6
 * hours (i.e. hand edited), and every 6 hours it checks to see if the .leapseconds file is older
 * than 10 days. If it is old, then the web site is queried and the leap seconds file rebuilt. The
 * URL for the leapsecond web site can be set by property 'LeapSecondURL'.
 * <p>
 * Basically there are four methods : isReady(), isLeap(julian), and getLeapSeconds(julian). If the
 * navy web site cannot be reached and there is no .leapseconds file, then all queries will return
 * nominal values.
 * <p>
 * Note: There has never been a negative leap second so this routine does not immediately show the
 * sign of the leap seconds. However, the getDayLength(jul) figures this out for a leap second day
 * by comparing the cumulative time correction for the leap day and the following day. For a
 * positive leap second there would be more cumulative time the following day.
 *
 * @author U.S. Geological Survey  <ketchum at usgs.gov>
 */
public final class LeapSecondDay extends Thread {

  public static String leapSecondURL = "http://maia.usno.navy.mil/ser7/tai-utc.dat";  // We know a leap seconds file is here
  /**
   * The file where the leap seconds are kept.
   */
  public static String leapSecondFilename = System.getProperty("user.home") + System.getProperty("file.separator") + ".leapseconds";
  public static int[] julianLeap;
  public static double[] leapSecs;
  public static long lastRead;
  private int julian;
  public final StringBuilder tmpsb = new StringBuilder(50);

  public static StringBuilder toStringSB(StringBuilder tmp) {
    return Util.getLeapSecondObject().toStringBuilder(tmp);
  }

  @Override
  public String toString() {
    return toStringBuilder(null).toString();
  }

  public StringBuilder toStringBuilder(StringBuilder tmp) {
    StringBuilder sb = tmp;
    if (sb == null) {
      sb = Util.clear(tmpsb);
    }
    synchronized (sb) {
      sb.append("LeapSecondDay: nlines=").append(julianLeap.length).append(" current day jul=").append(julian).
              append(" ").append(isLeap(julian) ? " IS leap second day " : " IS NOT leap second day").
              append(" secs=").append(getLeapSeconds(julian));
    }
    return sb;
  }

  public LeapSecondDay() {
    if (Util.getProperty("LeapSecondURL") != null) {
      leapSecondURL = Util.getProperty("LeapSecondURL");
    }
    setDaemon(true);
    start();
  }

  @Override
  public void run() {
    GregorianCalendar gc = new GregorianCalendar();
    boolean dbg = false;
    for (;;) {
      gc.setTimeInMillis(System.currentTimeMillis());
      julian = SeedUtil.toJulian(gc);
      init(dbg);
      if (dbg) {
        Util.prta("LeapSecondDay: nlines=" + julianLeap.length + " current day jul=" + julian + " "
                + (isLeap(julian) ? " IS leap second day " : " IS NOT leap second day") + " secs=" + getLeapSeconds(julian));
      }
      try {
        sleep(86400000L);
      } catch (InterruptedException e) {
      }
      dbg = true;
    }

  }

  /**
   * Return true if the leap seconds are valid. If not, then the day is 86400 seconds long in all
   * cases.
   *
   * @return True if the leap seconds are valid.
   */
  public static boolean isReady() {
    return julianLeap != null;
  }

  /**
   * Read the .leapseconds file or rebuild it from the USNO website and populate the data
   * structures.
   *
   */
  private synchronized void init(boolean dbg) {
    File leapseconds = new File(leapSecondFilename);
    int doy = SeedUtil.doy_from_ymd(SeedUtil.fromJulian(julian));
    boolean urlNeeded = (doy > 355 || (doy > 172 && doy < 184));
    // If the file does not exit, or if we are in the leap second change part of the year and the file is not within one day
    if (!leapseconds.exists() || (urlNeeded && leapseconds.lastModified() < System.currentTimeMillis() - 86400000L)) {
      try {
        Wget w2 = new Wget(leapSecondURL);
        if (dbg) {
          Util.prta(Util.ascdate() + "*** .leapsecond from " + leapSecondURL + " getLength=" + w2.getLength());
        }
        if (w2.getLength() < 1000) {
          Util.prt("****** Cannot fetch data from .leapsecond url=" + leapSecondURL);
          SendEvent.edgeSMEEvent("BadLeapFetch", "Could get .leapseconds from " + leapSecondURL, "LeapSecondDay");
          return;
        }
        try (RandomAccessFile rw = new RandomAccessFile(leapSecondFilename, "rw")) {
          rw.write(w2.getBuf(), 0, w2.getLength());
          rw.setLength(w2.getLength());
        }
      } catch (IOException e) {
        Util.prt("**** could not read/write .leapseconds file.  Permissions? " + leapSecondFilename);
      }
    } else if (leapseconds.exists() && julianLeap != null) {
      return;   // we have the file read
    }    // If the file exists and is less than 10 days old, read it
    try {
      try (RandomAccessFile rw = new RandomAccessFile(leapSecondFilename, "r")) {
        if (rw.length() < 1000) {
          Util.prt("****** Cannot fetch data from .leapsecond len=" + rw.length());
          SendEvent.edgeSMEEvent("BadLeapFetch", "Could get leap seconds from .leapseconds", "LeapSecondDay");
          leapseconds.delete();       // its bad force a new read
        } else {    // read in the leap seconds file and make it a string
          byte[] buf = new byte[(int) rw.length()];
          if (dbg) {
            Util.prt("Read .leapseconds file and parse");
          }
          rw.read(buf, 0, (int) rw.length());
          String ans = new String(buf, 0, (int) rw.length());
          String[] s = ans.split("\n");
          julianLeap = new int[s.length];
          leapSecs = new double[s.length];
          for (int i = 0; i < s.length; i++) {
            String[] parts = s[i].split("=");
            if (parts[0].substring(0, 6).trim().compareTo("1972") < 0) {
              continue;
            }
            julianLeap[s.length - i - 1] = Integer.parseInt(parts[1].substring(2, 12).trim().replaceAll("\\.5", ""));
            leapSecs[s.length - i - 1] = Double.parseDouble(parts[2].substring(1, 6).trim());
          }
        }
      }
    } catch (IOException e) {
      Util.prt("**** could not read .leapseconds file.  Permissions??");
    }

    if (dbg) {
      Util.prt(toStringBuilder(null));
    }
  }

  /**
   * Given a julian date, return if this is a leap second day (does it have a leap second on it)
   *
   * @param jul The julian date to evaluate.
   * @return true if this julian date is a leap second day (has an additional second or should be
   * missing a second)
   */
  public static synchronized boolean isLeap(int jul) {
    if (julianLeap == null) {
      return false;
    }
    for (int i = 0; i < julianLeap.length; i++) {
      if (jul == julianLeap[i]) {
        return true;
      }
      if (jul > julianLeap[i]) {
        break;
      }
    }
    return false;
  }

  /**
   * Given a julian date give the number of milliseconds for that date i.e. usually 86400000, but
   * for a positive leap second day 86401000.
   *
   * @param jul Julian date to evaluate
   * @return Number of milliseconds expected on that date.
   */
  public static synchronized long getDayLength(int jul) {
    if (!isLeap(jul)) {
      return 86400000L;
    }
    if (getLeapSeconds(jul) < getLeapSeconds(jul + 1)) {
      return 86401000L;   // its a positive leap second
    }
    return 86399000L;       // one second short
  }

  /**
   * given a julian date, return the cumulative time difference between TAI and UTC time (the sum of
   * all previous time adjustments).
   *
   * @param julian The julian date to evaluate.
   * @return return the cumulative time adjustment for this day.
   */
  public static synchronized double getLeapSeconds(int julian) {
    if (leapSecs == null) {
      return -1.;
    }
    double ret = leapSecs[0];
    for (int i = 0; i < julianLeap.length; i++) {
      if (julian >= julianLeap[i]) {
        ret = leapSecs[i];
      }
      if (julian > julianLeap[i]) {
        break;
      }
    }
    return ret;
  }

  public static void main(String[] args) {
    Util.init("edge.prop");
    Util.setModeGMT();
    while (!LeapSecondDay.isReady()) {
      Util.prta("Not yet ready");
      Util.sleep(100);
    }
    if (args.length == 0) {
      args = new String[3];
      args[0] = "2457203";
      args[1] = "2457204";    // this is leap second day 06/30/2015
      args[2] = "2457205";
    }

    for (String arg : args) {
      int julian = Integer.parseInt(arg);
      boolean isLeap = LeapSecondDay.isLeap(julian);
      Util.prt("Julian " + julian + (isLeap ? " IS a leap second day " : " IS NOT a leap second day ")
              + LeapSecondDay.getLeapSeconds(julian) + " " + LeapSecondDay.getDayLength(julian));
      Util.prt(LeapSecondDay.toStringSB(null));
    }
    Util.prt("End of execution");
  }
}
