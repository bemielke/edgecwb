/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.edge;

import gov.usgs.anss.util.*;
import gov.usgs.anss.edgethread.EdgeThread;
import java.io.*;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.Calendar;
import java.util.GregorianCalendar;

/**
 * This class is a client to a RawInputServer which submits in the clear data to a edge or cwb
 * combination. Which is chosen depends on whether the age of the data exceeds 10 days.
 *
 * @author davidketchum
 */
public final class RawInputClient {

  private String host;    // client of a RawInputServer (edge node)
  private int port;       // port of a RawInputServer (edge node)
  private int cwbport;    // port of a CWB server
  private String cwbhost; // port of a CWB host
  private Socket scwb;
  private Socket s;
  private byte[] buf;
  private ByteBuffer b;
  private byte[] tmp;
  private ByteBuffer tb;
  private int seq;
  private final String tag;
  private final GregorianCalendar dummy = new GregorianCalendar();
  private final StringBuilder seednameSB = new StringBuilder(12);
  private final GregorianCalendar gtmp = new GregorianCalendar();
  private EdgeThread par;

  @Override
  public String toString() {
    return cwbhost + "/" + cwbport + " " + host + "/" + port + " " + s + " " + scwb + " seq=" + seq;
  }

  /**
   *
   * @param s A string to print to the log
   */
  protected void prt(String s) {
    if (par == null) {
      Util.prt(s);
    } else {
      par.prt(s);
    }
  }

  /**
   *
   * @param s A String to print in the log preceded by the current time
   */
  protected final void prta(String s) {
    if (par == null) {
      Util.prta(s);
    } else {
      par.prta(s);
    }
  }

  /**
   *
   * @param s A string to print to the log
   */
  protected void prt(StringBuilder s) {
    if (par == null) {
      Util.prt(s);
    } else {
      par.prt(s);
    }
  }

  /**
   *
   * @param s A String to print in the log preceded by the current time
   */
  protected final void prta(StringBuilder s) {
    if (par == null) {
      Util.prta(s);
    } else {
      par.prta(s);
    }
  }

  /**
   *
   * @param p EdgeThead to use for logging
   */
  public void setLogger(EdgeThread p) {
    par = p;
  }

  /**
   * Creates a new instance of RawInputClient
   *
   * @param tg The logging tag for this client
   * @param h THe ip address of the target host RawInputServer
   * @param p The port on the ip of the RawInputServer
   * @param ch The CWB host to use for data out of the 10 day window (if no CWB set '')
   * @param cp The CWB port to use for data out of the 10 day window (if no CWB set 0)
   * @param parent A parent EdgeThread to use for logging
   */
  public RawInputClient(String tg, String h, int p, String ch, int cp, EdgeThread parent) {
    tag = tg;
    par = parent;
    create(tg, h, p, ch, cp);
  }

  /**
   * Creates a new instance of RawInputClient
   *
   * @param tg The logging tag for this client
   * @param h THe ip address of the target host RawInputServer
   * @param p The port on the ip of the RawInputServer
   * @param ch The CWB host to use for data out of the 10 day window (if no CWB set '')
   * @param cp The CWB port to use for data out of the 10 day window (if no CWB set 0)
   */
  public RawInputClient(String tg, String h, int p, String ch, int cp) {
    tag = tg;
    create(tg, h, p, ch, cp);
  }

  private void create(String tg, String h, int p, String ch, int cp) {
    if (!h.equals("")) {
      host = h;
    }
    if (p > 0) {
      port = p;
    }
    if (ch == null) {
      ch = "";
    }
    if (!ch.equals("")) {
      cwbhost = ch;
    }
    if (cp > 0) {
      cwbport = cp;
    }
    buf = new byte[24000];
    b = ByteBuffer.wrap(buf);
    tmp = new byte[40];
    tb = ByteBuffer.wrap(tmp);
    seq = 1;
    prt("RawInputClient for " + h + "/" + p + " cwb=" + ch + "/" + p);
  }

  public void close() {
    if (s != null) {
      if (!s.isClosed()) {
        try {
          s.close();
        } catch (IOException e) {
        }
      }
    }
    if (scwb != null) {
      if (!scwb.isClosed()) {
        try {
          scwb.close();
        } catch (IOException e) {
        }
      }
    }
  }

  /**
   * Cause any data fro the given seedname to be force out (last partial MiniSEED block force to
   * disk).
   *
   * @param seedname The SSNNNNNCCCLL channel name
   * @throws UnknownHostException
   */
  public void forceout(String seedname) throws UnknownHostException {
    send(seedname, -2, null, dummy, 0., 0, 0, 0, 0);
  }

  /**
   * Send a block of data to the Edge/CWB combination. If an IOException is thrown, this routine
   * will loop and try to reopen the socket every second to send the data.
   *
   * @param seedname The 12 character seedname of the channel NNSSSSSCCCLL fixed format
   * @param nsamp The number of data samples
   * @param samples An int array with the samples
   * @param time With the time of the first sample
   * @param rate The data rate in Hertz
   * @param activity The activity flags per the SEED manual
   * @param ioClock The IO/CLOCK flags per the SEED manual
   * @param quality The data Quality flags per the SEED manual
   * @param timingQuality The overall timing quality (must be 0-100)
   * @throws UnknownHostException - if the socket will not open
   */
  public void send(String seedname, int nsamp, int[] samples, GregorianCalendar time, double rate,
          int activity, int ioClock, int quality, int timingQuality) throws UnknownHostException {
    Util.clear(seednameSB).append(seedname);
    send(seednameSB, nsamp, samples, time, rate, activity, ioClock, quality, timingQuality);
  }

  /**
   * Send a block of data to the Edge/CWB combination. If an IOException is thrown, this routine
   * will loop and try to reopen the socket every second to send the data.
   *
   * @param seedname The 12 character seedname of the channel NNSSSSSCCCLL fixed format
   * @param nsamp The number of data samples
   * @param samples An int array with the samples
   * @param time With the time of the first sample
   * @param rate The data rate in Hertz
   * @param activity The activity flags per the SEED manual
   * @param ioClock The IO/CLOCK flags per the SEED manual
   * @param quality The data Quality flags per the SEED manual
   * @param timingQuality The overall timing quality (must be 0-100)
   * @throws UnknownHostException - if the socket will not open
   */
  public void send(StringBuilder seedname, int nsamp, int[] samples, GregorianCalendar time, double rate,
          int activity, int ioClock, int quality, int timingQuality) throws UnknownHostException {
    if (buf.length < nsamp * 4 + 40) {  // I do not thing this happens very often
      buf = new byte[(nsamp * 8 + 40) * 2];     // Make it twice this size.
      b = ByteBuffer.wrap(buf);
    }
    int yr = time.get(Calendar.YEAR);
    int doy = time.get(Calendar.DAY_OF_YEAR);
    int secs = (int) (time.getTimeInMillis() % 86400000L);
    int usec = (secs % 1000) * 1000;
    secs = secs / 1000;

    b.position(0);
    b.putShort((short) 0xa1b2);
    b.putShort((short) nsamp);
    for (int i = 0; i < 12; i++) {
      if (i >= seedname.length()) {
        b.put((byte) ' ');
      } else {
        b.put((byte) seedname.charAt(i));
      }
    }
    //b.put((seedname+"       ").substring(0,12).getBytes());
    b.putShort((short) yr);
    b.putShort((short) doy);
    int rateMantissa;
    int rateDivisor;
    if (rate > 0.9999) {
      rateMantissa = (int) (rate * 100. + 0.001);
      rateDivisor = -100;
    } else if (Math.abs(rate - 1.0 / 60.0) < 0.00000001) {    // Is it one minute data
      rateMantissa = -60;
      rateDivisor = 1;
    } else {
      double period = 1. / rate;
      rateDivisor = 1;        // this is in numerator since the period is in mantissa
      while (period < 3276.) {
        period = period * 10.;
        rateDivisor *= 10;
      }
      rateMantissa = -(int) Math.round(period);
      //rateMantissa = (int) (rate * 10000. +0.001);
      //rateDivisor = -10000;
    }
    //prta(seedname+" rate="+rate+" rateMan="+rateMantissa+" rateDiv="+rateDivisor);

    b.putShort((short) rateMantissa);
    b.putShort((short) rateDivisor);
    b.put((byte) activity);
    b.put((byte) ioClock);
    b.put((byte) quality);
    b.put((byte) timingQuality);
    b.putInt(secs);
    b.putInt(usec);
    b.putInt(seq++);
    if (nsamp > 0) {
      for (int i = 0; i < nsamp; i++) {
        b.putInt(samples[i]);
      }
    }
    int nbytes = b.position();

    // Now decide where this is written to, open sockets if necessary
    gtmp.setTimeInMillis(System.currentTimeMillis());
    int today = SeedUtil.toJulian(gtmp);
    int julian = SeedUtil.toJulian(yr, doy);
    boolean done = false;
    while (!done) {
      if (today - julian > 10 && cwbport > 0) {   // Use the CWB
        try {
          if (scwb == null) {
            prta("Need to open CWB socket to " + cwbhost + "/" + cwbport);
            scwb = new Socket(cwbhost, cwbport);
            prt("CWB port open! localport=" + scwb.getLocalPort());
            tb.position(0);
            tb.putShort((short) 0xa1b2);
            tb.putShort((short) -1);
            tb.put((tag + "            ").substring(0, 12).getBytes());
            scwb.getOutputStream().write(tmp, 0, 40);
          }
          scwb.getOutputStream().write(buf, 0, nbytes);
          done = true;
        } catch (IOException e) {
          prt("IOException opening cwb socket=" + e.getMessage());
          scwb = null;
          Util.sleep(1000);
        }
      } else {
        try {
          if (s == null) {
            prta("Need to open Edge socket to " + host + "/" + port);
            s = new Socket(host, port);
            prt("Edge port open! localport " + s.getLocalPort());
            tb.position(0);
            tb.putShort((short) 0xa1b2);
            tb.putShort((short) -1);
            tb.put((tag + "            ").substring(0, 12).getBytes());
            s.getOutputStream().write(tmp, 0, 40);
          }
          s.getOutputStream().write(buf, 0, nbytes);
          done = true;
        } catch (IOException e) {
          prt("IOException opening edge socket=" + e.getMessage());
          s = null;
          Util.sleep(1000);
        }
      }
    }
  }

  public static void main(String[] args) {
    Util.setModeGMT();
    String seedname = "XXDAVE MHZZ2";
    if (args.length == 1) {
      seedname = args[0];
    }
    RawInputClient ric = new RawInputClient("Dave", "localhost", 7981, null, 0);
    int[] data = new int[1440];
    int[] d = new int[20];
    GregorianCalendar g = new GregorianCalendar();
    g.set(2016, 8, 14, 18, 0, 0);
    g.setTimeInMillis(g.getTimeInMillis() / 86400000L * 86400000L);
    for (int i = 0; i < 1440; i++) {
      data[i] = i;
    }

    try {
      for (int i = 0; i < 1440; i = i + 20) {
        System.arraycopy(data, i, d, 0, 20);
        ric.send(seedname, 20, d, g, 10., 0, 0, 0, 0);
        g.setTimeInMillis(g.getTimeInMillis() + 20000 / 10);
      }
      ric.forceout(seedname);
    } catch (UnknownHostException e) {
      Util.prta("Exception=" + e);
      e.printStackTrace();
    }
  }
}
