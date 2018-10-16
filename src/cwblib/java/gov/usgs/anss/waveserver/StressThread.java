/*
 *  This software is in the public domain because it contains materials 
 *  that originally came from the United States Geological Survey, 
 *  an agency of the United States Department of Interior. For more 
 *  information, see the official USGS copyright policy at 
 *  http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.waveserver;

import gov.usgs.anss.edgeoutput.TraceBuf;
import gov.usgs.anss.util.FUtil;
import gov.usgs.anss.util.Util;
import java.io.IOException;
import java.util.Random;
import static java.lang.Thread.sleep;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.GregorianCalendar;

/**
 * this is a stress test thread. Given a channel and time it will read data from the QueryMom
 * getSCNLRAW at various times over the next 3 hours. The c
 */
public final class StressThread extends Thread {

  private final String seedname;
  private final GregorianCalendar start = new GregorianCalendar();
  private final int npasses;
  private final int nsec;     // How long each request is
  private boolean go;
  private final String reqid;
  private final WaveServerClient wsc;
  private long totalBytes, totalTime;
  private boolean done;
  private long min, max;
  private final Random random = new Random();

  public String getSeedname() {
    return seedname;
  }

  public long getMin() {
    return min;
  }

  public long getMax() {
    return max;
  }

  public long getTotalSamp() {
    return totalBytes;
  }

  public long getTotalTime() {
    return totalTime;
  }

  public long getNpass() {
    return npasses;
  }

  @Override
  public String toString() {
    return seedname + " min=" + min + " max=" + max + " avg=" + (totalTime / npasses) + " totSamps=" + totalBytes + " elapse=" + totalTime;
  }

  public StressThread(String host, int port, String sname, GregorianCalendar st, int npass, int nsec, String reqid) {
    seedname = sname;
    start.setTimeInMillis(st.getTimeInMillis());
    npasses = npass;
    this.nsec = nsec;
    this.reqid = reqid;
    wsc = new WaveServerClient(host, port);
    min = Integer.MAX_VALUE;
    max = Integer.MIN_VALUE;

    start();
  }

  public void go() {
    go = true;
  }

  @Override
  public void run() {
    GregorianCalendar st = new GregorianCalendar();
    st.setTimeInMillis(start.getTimeInMillis());
    GregorianCalendar end = new GregorianCalendar();
    end.setTimeInMillis(st.getTimeInMillis() + nsec * 1000);
    ArrayList<TraceBuf> tbs = new ArrayList<TraceBuf>();
    while (!go) {
      try {
        sleep(100);
      } catch (InterruptedException e) {
      }
    } // wait for user start
    long begin = System.currentTimeMillis();
    for (int pass = 0; pass < npasses; pass++) {
      try {
        long beg = System.currentTimeMillis();
        Util.prta(seedname + " " + Util.asctime2(st) + " pass=" + pass + " start=" + Util.ascdatetime2(st) + " end=" + Util.ascdatetime2(end));
        wsc.getSCNLRAW(reqid, seedname, st, end, true, tbs);
        for (TraceBuf tb : tbs) {
          totalBytes += tb.getNsamp();
        }
        wsc.freeMemory(tbs);
        tbs.clear();
        st.setTimeInMillis(start.getTimeInMillis() + random.nextInt(npasses) * nsec * 1000);
        end.setTimeInMillis(st.getTimeInMillis() + nsec * 1000);
        long elapsed = System.currentTimeMillis() - beg;
        if (elapsed < min) {
          min = elapsed;
        }
        if (elapsed > max) {
          max = elapsed;
        }
      } catch (IOException e) {
        Util.prt(reqid + " IOErr=" + e);
        e.printStackTrace();
      }
    }
    totalTime = System.currentTimeMillis() - begin;
    done = true;
  }

  public static void main(String[] args) {
    Util.setModeGMT();
    GregorianCalendar start = new GregorianCalendar();
    start.setTimeInMillis(start.getTimeInMillis() - 3 * 3600000);
    int nsec = 120;
    String[] seedname = {"USDUG  BHZ00", "USISCO BHZ00", "IUANMO BHZ00", "NEWES  HHZ00", "USWRAK BHZ00", "CIISA  BHZ  ",
      "IWLOHW BHZ00", "GTCPUP BHZ00", "ICENH  BHZ00", "IUCOR  BHZ00", "USDGMT BHZ00", "IUHRV  BHZ", "USBOZ  BHZ00"};
    String host = "localhost";
    int port = 2060;
    boolean dbg = false;
    int npass = 100;
    int dummy;
    if (args.length == 1) {
      Util.prt("Stress test example : -stress -h localhost -p 2060 -n passes -d secs -s NSCL1:NSCL2:...NSCLN -b 2014/05/05-00:00");
    }
    for (int i = 0; i < args.length; i++) {
      switch (args[i]) {
        case "-s":
          seedname = args[i + 1].split(":");
          for (int j = 0; j < seedname.length; j++) {
            seedname[j] = (seedname[j].replaceAll("-", " ").trim() + "           ").substring(0, 12);
          }
          i++;
          break;
        case "-b":
          Timestamp st = FUtil.stringToTimestamp(args[i + 1].replaceAll("-", " ").replaceAll("/", "-"));
          start.setTimeInMillis(st.getTime());
          break;
        case "-d":
          nsec = Integer.parseInt(args[i + 1]);
          i++;
          break;
        case "-n":
          npass = Integer.parseInt(args[i + 1]);
          i++;
          break;
        case "-h":
          host = args[i + 1];
          i++;
          break;
        case "-p":
          port = Integer.parseInt(args[i + 1]);
          i++;
          break;
        case "-dbg":
          dbg = true;
          break;
        case "-stress":
        case "-wsc":
        case "-cwbws":
          dummy = 2;
          break;
        default:
          Util.prt("Unknown option " + args[i] + " at " + i);
          break;
      }
    }
    StressThread[] thr = new StressThread[seedname.length];
    Util.prta("nthr=" + seedname.length + " npss=" + npass + " nsec=" + nsec + " h=" + host + " p=" + port + " start=" + Util.ascdatetime2(start));
    for (int i = 0; i < seedname.length; i++) {
      seedname[i] = (seedname[i] + "      ").substring(0, 12);
      thr[i] = new StressThread(host, port, seedname[i], start, npass, nsec, seedname[i].substring(0, 7).trim());
    }
    long begin = System.currentTimeMillis();
    for (int i = 0; i < seedname.length; i++) {
      thr[i].go();
    }
    for (;;) {
      boolean done = true;
      for (int i = 0; i < seedname.length; i++) {
        if (!thr[i].done) {
          done = false;
        }
      }
      if (done) {
        break;
      }
    }
    long elapse = System.currentTimeMillis() - begin;
    long totalNsamp = 0;
    for (int i = 0; i < seedname.length; i++) {
      totalNsamp += thr[i].getTotalSamp();
      Util.prt(thr[i].toString());
    }
    Util.prt("avg samp/sec=" + (totalNsamp * 1000 / elapse) + " totalNsamp=" + totalNsamp + " full test elapsed=" + elapse);
    System.exit(0);
  }
}
