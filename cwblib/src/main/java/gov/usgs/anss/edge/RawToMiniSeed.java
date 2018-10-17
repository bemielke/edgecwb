/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.edge;

import gov.usgs.alarm.SendEvent;
import gov.usgs.anss.edgethread.EdgeThread;
import gov.usgs.anss.seed.MiniSeed;
import gov.usgs.anss.util.SeedUtil;
import gov.usgs.anss.util.Util;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import gov.usgs.anss.seed.MiniSeedPool;
import gnu.trove.map.hash.TLongObjectHashMap;
import gnu.trove.iterator.TLongObjectIterator;

/**
 *
 * This class allows a static entry where a the user submits a seed name, time and an array of
 * 32-bit "in-the-clear" ints with a chunk of time series and this routine will output mini-seed
 * blockettes ready for writing to the data system. (In fact they are written using
 * "IndexBlock.writeMiniSeed()"). This keeps a static list (via TreeMap) of the channels known to
 * this routine which it then uses to keep the context of the mini-seed data going.
 * <p>
 * Notes on Steim2 compression : The Steim 2 contains 0-N data differences the value of data[0] and
 * data[n-1]. The first difference always takes data[0] to the last value of the previous frame. So,
 * the only time you need outside information is for the first buffer if you are trying to get the
 * first difference to match. If not, the first difference can be zero and the prior reverse
 * constant is unknowable. To support making this first difference be right during testing, the
 * ability to pass the last value is supported in the call to addTimeseries. Practically there is no
 * reason to pass such a value other than for trying to exactly match some known series.
 *<p>
 * 1)  Each channel being compressed has three different compression streams.  If everything is 
 * received exactly in order and complete only one stream is need the “current” stream.  If there is
 * a gap, then another stream is created.  Up to 3 streams can be going at one time.  
 * <p>2)  Each new addition of time series is checked to see if it can be added exactly to the end 
 * of one of the open streams, if so, the time series is added.  
 * <p>3)  When a stream gets to a full miniSEED packet, the packet is sent to the 
 * writeMiniSeedCheckMidnight() method to be written into the buffer.  
 * <p>4)  If all 3 streams have been created, and the new time series does not fit at the end of 
 * any of them, the oldest one is forced out using writeMiniSeedCheckMidnight() and the force out 
 * compression stream gets the new time series.
 * <p>5)  Each of the open compression streams is checked periodically for no new data being added 
 * and if it has been long enough, the stream is declared as “stale” and the partially full 
 * miniSeed block is force out to disk via writeMiniSeedCheckMidnight(). The stale interval can be 
 * set in each edge mom instance, and it defaults to 1800 seconds.  This happens when the caller (normally
 * and Edgemom, calles forceStale(ms).  Any other using process that wants to force stale out periodically
 * needs to call forceStale(int ms) periodically.
 * <p>
 * By default the putbuf routines calls IndexBlock.writeMiniSeed().  The user can create a MiniSeedHandler
 * of their own and register it with RawToMiniSEED and then that routine will be invoked every time a 
 * full MiniSEED block is read to be dealt with.

 * @author davidketchum
 */
public final class RawToMiniSeed {

  static MiniSeedOutputHandler staticOverride;
  // Static variables
  static private final TLongObjectHashMap<RawToMiniSeed> chans = new TLongObjectHashMap<>();
  static private final TLongObjectHashMap<RawToMiniSeed> secondChans = new TLongObjectHashMap<>();
  static private final TLongObjectHashMap<RawToMiniSeed> oob = new TLongObjectHashMap<>();
  private static int tolerancePerSample = 2;          // so 1/2 sample by default, use setTolerance/sample to set higher value (less of a sample)

  static private String debugChannel = "IUMA2  LHZ10";
  private static final MiniSeedPool msp = new MiniSeedPool();
  static boolean dbg;
  private static StringBuffer sb;
  //static private boolean nohydra;       // If true, nothing will generate hydra output
  private static final StringBuffer tmpsb = new StringBuffer(100);    // This is used in nonsynchronized routings like addTimeSeries()
  private final StringBuilder tmpsbo = new StringBuilder(100); // Used in the object so allin synchronized methods.
  private static final StringBuilder tmpsbats = new StringBuilder(100);    // Used only in addTimeSeries static 
  private EdgeThread parent;

  // Steim2 compressor working variables
  private double rate;
  private double avgSec = 0.;                 // avg packet size in seconds.
  private final long periodUSec;
  private final int integration;
  // the next 3 must be correct before each return to maintain context
  private int reverse;                        // last sample of compression to date
  private final int[] data = new int[8];           // 8 is the Most differences which can be leftover
  private int ndata;                          // number of left over samples
  private long earliestTime;                  // Time of first sample in this RTMS in micros
  private long difftime;                      // time of first difference in d in micros
  private int julian;                         // julian day of diff time
  private int currentFrame;                   // Current frame where data is going
  private int currentWord;                    // Current word in the frame where data goes
  private final int toleranceUsec;

  // Space for the mini-seed records
  private final int numFrames;                      // number of Steim frames for one record
  private final StringBuilder seedname = new StringBuilder(12);                    // The seed name for this channel
  private FixedHeader hdr;                    // storage for fixed header
  private int startSequence;
  private SteimFrames[] frames;              // storage for Steim 64 byte frames
  private int ns;                             // Number of samples compress in this group of frames
  private int nextDiff;                       // next difference to compress (index to data/diff)
  // Internal variables
  private RawDisk back;                       // if not null, this will receive all put bufs
  private int backblk;                        // block number to write next
  private byte[] lastputbuf;
  private int[] x = new int[400];
  private int[] d = new int[400];
  private byte[] outbuf;
  private ByteBuffer bf;
  private MiniSeedOutputHandler overrideOutput;// if Not null, use this object to output compression

  public static void setTolerancePerSample(int t) {
    tolerancePerSample = t;
  }

  static synchronized public StringBuffer getStatus() {
    Util.clear(tmpsb).append("msp=").append(msp).append(" #chan=").append(chans.size()).
            append(" #2nd=").append(secondChans.size()).append(" #oob=").append(oob.size());
    return tmpsb;
  }

  /**
   * Provide external access to the MiniSeedPool
   *
   * @param b Byte array with raw miniseed
   * @param off offset into b for start of miniseed
   * @param len Length of the miniseed block
   * @return
   */
  static public MiniSeed getMiniSeed(byte[] b, int off, int len) {
    return msp.get(b, off, len);
  }

  /**
   * Free a miniseed block gotten from getMiniSeed()
   *
   * @param ms The block to fee
   */
  static public void freeMiniSeed(MiniSeed ms) {
    msp.free(ms);
  }

  private void close() {        // clean up object so it can be reapped
    parent = null;
    if (back != null) {
      try {
        back.close();
      } catch (IOException e) {
      }
    }
    back = null;
    frames = null;
    overrideOutput = null;

  }
  // Dead timer
  long lastUpdate;

  //public static void setNoHydra(boolean t) {nohydra=t;}
  @Override
  public String toString() {
    return toStringBuilder(null).toString();
  }

  public StringBuilder toStringBuilder(StringBuilder tmp) {
    StringBuilder sbtmp = tmp;
    if (sbtmp == null) {
      sbtmp = Util.clear(tmpsbo);
    }
    synchronized (sbtmp) {
      sbtmp.append(seedname).append(" ").append(timeFromUSec(earliestTime)).append(" to ").append(timeFromUSec(difftime)).
              append(" ns=").append(ns).append(" nxtDiff=").append(nextDiff).append(" ndata=").
              append(ndata).append(" rev=").append(reverse);
    }
    return sbtmp;
  }

  public double getAvgSec() {
    return avgSec;
  }

  public String toStringObject() {
    String s = super.toString();
    int beg = s.length() - 12;
    if (beg < 0) {
      beg = 0;
    }
    return s.substring(beg);
  }

  public String lastTime() {
    return stringFromUSec((long) (difftime + ndata * periodUSec + 0.01));
  }

  public String earliestTime() {
    return stringFromUSec(earliestTime);
  }

  private String stringFromUSec(long usec) {
    int[] ymd = SeedUtil.fromJulian(julian);
    int ms = (int) (usec / 1000);
    int hr = ms / 3600000;
    ms = ms % 3600000;
    int min = ms / 60000;
    ms = ms % 60000;
    int sec = ms / 1000;
    ms = ms % 1000;
    return "" + Util.df6(ymd[0]).substring(2, 6) + "-" + Util.df6(ymd[1]).substring(4, 6) + "-"
            + Util.df6(ymd[2]).substring(4, 6) + " " + Util.df6(hr).substring(4, 6) + ":"
            + Util.df6(min).substring(4, 6) + ":" + Util.df6(sec).substring(4, 6) + "."
            + Util.df6(ms).substring(3, 6);
  }

  public static void setDebugChannel(String s) {
    debugChannel = s;
  }

  public void setStartSequence(int i) {
    startSequence = i;
  }

  public static String timeFromUSec(long usec) {
    int ms = (int) (usec / 1000);
    int hr = ms / 3600000;
    ms = ms % 3600000;
    int min = ms / 60000;
    ms = ms % 60000;
    int sec = ms / 1000;
    ms = ms % 1000;
    return "" + Util.df6(hr).substring(4, 6) + ":"
            + Util.df6(min).substring(4, 6) + ":" + Util.df6(sec).substring(4, 6) + "."
            + Util.df6(ms).substring(3, 6);
  }

  /**
   * return the useconds since the julian fiducial time as a long
   *
   * @return the absolute microseconds of last data sample in this compression rec
   */
  public long getJulianUSec() {
    return (long) (((long) julian * 86400000000L) + difftime + ndata * periodUSec);
  }

  /**
   * return the parent thread (normally used for printing to logs)
   *
   * @return The parent thread for access to prt() generally
   */
  public EdgeThread getParent() {
    return parent;
  }

  /**
   * get seedname of channel
   *
   * @return the seedname of the channel
   */
  public StringBuilder getSeedname() {
    return seedname;
  }

  /**
   * return time since last thing happened on this channel
   *
   * @return the time in millis since the last modification or creation of this channel
   */
  public long lastAge() {
    return (System.currentTimeMillis() - lastUpdate);
  }

  /**
   * return digitizing rate
   *
   * @return The digitizing rate
   */
  public double getRate() {
    return rate;
  }

  /**
   * set a new digit rate (overrides rate used at creation
   *
   * @param rt The new rate to use
   */
  public void setRate(double rt) {
    rate = rt;
    hdr.setRate(rt);
  }

  /**
   * for debuging a string which represents the building of the RawToMiniSeed details
   *
   * @return The string representing the debugging detail
   */
  static public String getDebugString() {
    if (sb == null) {
      return "";
    } else {
      return sb.toString();
    }
  }

  /**
   * set the debug flag
   *
   * @param t The boolean value of the debug flag
   */
  public static void setDebug(boolean t) {
    dbg = t;
  }
  private static final StringBuilder stalesb = new StringBuilder(100);

  /**
   * check all RTMS on all 3 compression streams and force out if last update older than ms
   *
   * @param ms The number of milliseconds of age needed to cause forceout
   */
  public static void forceStale(int ms) {
    if (chans == null && secondChans == null && oob == null) {
      Util.prt("ForceStale RTMS lists null chan=" + chans + " 2nd=" + secondChans + " oob=" + oob);
      return;
    }
    TLongObjectIterator<RawToMiniSeed> itr;
    int count = 0;
    if (chans != null) {
      synchronized (chans) {
        itr = chans.iterator();
        while (itr.hasNext()) {
          itr.advance();
          RawToMiniSeed rm = itr.value();
          if (rm.lastAge() > ms) {
            EdgeThread par = rm.getParent();
            if (par != null) {
              par.prta(Util.clear(stalesb).append("RTMS: timeout stale primary age=").
                      append(rm.lastAge()).append(" ").append(rm.toStringBuilder(null)));
            }
            rm.forceOut();
            if (par != null) {
              par.prta(Util.clear(stalesb).append("RTMS: timeout stale forceOut done"));
            }
            rm.close();
            if (par != null) {
              par.prta(Util.clear(stalesb).append("RTMS: timeout stale close done"));
            }
            itr.remove();
            if (par != null) {
              par.prta(Util.clear(stalesb).append("RTMS: timeout stale remove done cnt=").append(count));
            }
            count++;
          }
        }
      }
    } else {
      Util.prta("ForceStale chans is null");
    }
    int count2 = 0;
    if (secondChans != null) {
      synchronized (secondChans) {
        itr = secondChans.iterator();
        while (itr.hasNext()) {
          itr.advance();
          RawToMiniSeed rm = itr.value();
          if (rm.lastAge() > ms) {
            EdgeThread par = rm.getParent();
            if (par != null) {
              par.prta(Util.clear(stalesb).append("RTMS: timeout stale 2nd chans  age=").
                      append(rm.lastAge()).append(" ").append(rm.toStringBuilder(null)));
            }
            rm.forceOut();
            rm.close();
            itr.remove();
            count2++;
          }
        }
      }
    } else {
      Util.prta("ForceStale 2nd is null");
    }
    int count3 = 0;
    if (oob != null) {
      synchronized (oob) {
        itr = oob.iterator();
        while (itr.hasNext()) {
          itr.advance();
          RawToMiniSeed rm = itr.value();
          if (rm.lastAge() > ms) {
            EdgeThread par = rm.getParent();
            if (par != null) {
              par.prta(Util.clear(stalesb).append("RTMS: timeout stale OOB chans  age=").
                      append(rm.lastAge()).append(" ").append(rm.toStringBuilder(null)));
            }
            rm.forceOut();
            rm.close();
            itr.remove();
            count3++;
          }
        }
      }
    } else {
      Util.prta("ForceState oob is null");
    }
    if (ms < 0) {
      Util.prta("ForceStale ms<0 closed chans=" + count + " 2nd=" + count2 + " oob=" + count3);
    }
  }

  /**
   * Write out a time series for the given seed name, time series, time and optional flags. This
   * presumes no knowledge of the prior reverse integration constant (the last data value of the
   * previously compressed block).
   *
   * @param x Array of ints with time series
   * @param nsamp Number of samples in x
   * @param seedname Internal seedname as an ascii (NNSSSSSCCCLL)
   * @param year The year of the 1st sample
   * @param doy The day-of-year of first sample
   * @param sec Seconds since midnight of the first sample
   * @param micros Microseconds (fraction of a second) to add to seconds for first sample
   * @param rt The nominal digitizing rate in Hertz
   * @param activity The activity flags per SEED volume (ch 8 pg 93)
   * @param IOClock The I/O and clock flags per SEED volume
   * @param quality The clock quality as defined in SEED volume
   * @param timingQuality The clock quality as defined in SEED volume blockette1001
   * @param par The parent thread to use for logging
   */
  public static void addTimeseries(int[] x, int nsamp, StringBuilder seedname,
          int year, int doy, int sec, int micros,
          double rt, int activity, int IOClock, int quality, int timingQuality, EdgeThread par) {
    addTimeseries(x, nsamp, seedname, year, doy, sec, micros, rt, activity, IOClock, quality,
            timingQuality, 0, par);
  }

  /**
   * Write out a time series for the given seed name, time series, time and optional flags. This
   * presumes no knowledge of the prior reverse integration constant (the last data value of the
   * previously compressed block).
   *
   * @param x Array of ints with time series
   * @param nsamp Number of samples in x
   * @param seedname Internal seedname as an ascii (NNSSSSSCCCLL)
   * @param year The year of the 1st sample
   * @param doy The day-of-year of first sample
   * @param sec Seconds since midnight of the first sample
   * @param micros Microseconds (fraction of a second) to add to seconds for first sample
   * @param rt The nominal digitizing rate in Hertz
   * @param activity The activity flags per SEED volume (ch 8 pg 93)
   * @param IOClock The I/O and clock flags per SEED volume
   * @param quality The clock quality as defined in SEED volume
   * @param timingQuality The timing quality (0-100)
   * @param par The parent thread to use for logging.
   * @param lastValue Only needed if exact match to existing seed is needed.
   */
  // DEBUG: 7/26/2007 try this whole routine synchronized
  public static void addTimeseries(int[] x, int nsamp, StringBuilder seedname,
          int year, int doy, int sec, int micros,
          double rt, int activity, int IOClock, int quality, int timingQuality,
          int lastValue, EdgeThread par) {
    if (nsamp == 0) {
      return;            // nothing to do!
    }
    dbg = seedname.indexOf("ZZIMGEA0 SHZ") > 0 || Util.stringBuilderEqual(seedname, debugChannel);
    if (dbg) {
      if (sb == null) {
        sb = new StringBuffer(10000);
      } else if (sb.length() > 0) {
        Util.clear(sb);
      }
      synchronized (tmpsbats) {
        if (par != null) {
          par.prt(Util.clear(tmpsbats).append("RTMS: Add TS ").append(seedname).
                  append(" ").append(timeFromUSec(sec * 1000000L + micros)).append(" rt=").append(rt).
                  append(" ns=").append(nsamp).append(" lst=").append(lastValue));
        }
      }
    }
    // Some input day used year doy and the offset may lap into next day, if so fix it here
    while (micros > 1000000) {
      micros -= 1000000;
      sec++;
    }
    while (sec >= 86400) {      // Do seconds say its a new day?
      synchronized (tmpsbats) {
        if (par != null) {
          par.prta(Util.clear(tmpsbats).append("RTMS: day adjust new yr=").append(year).
                  append(" doy=").append(doy).append(" sec=").append(sec).append(" usec=").append(micros).
                  append(" seedname=").append(seedname));
        }
      }
      int jul = SeedUtil.toJulian(year, doy);
      sec -= 86400;
      jul++;
      int[] ymd = SeedUtil.fromJulian(jul);
      year = ymd[0];
      doy = SeedUtil.doy_from_ymd(ymd);
      synchronized (tmpsbats) {
        if (par != null) {
          par.prta(Util.clear(tmpsbats).append("RTMS: day adjust new yr=").append(year).
                  append(" doy=").append(doy).append(" sec=").append(sec).append(" seedname=").append(seedname));
        }
      }
    }

    // find this channel in tree map or create it, check for out-of-band or 2nd
    // fill data as well.
    RawToMiniSeed rm;
    synchronized (chans) {
      rm = chans.get(Util.getHashFromSeedname(seedname));
    }
    RawToMiniSeed rm2;
    synchronized (secondChans) {
      rm2 = secondChans.get(Util.getHashFromSeedname(seedname));
    }
    RawToMiniSeed rm3;
    synchronized (oob) {
      rm3 = oob.get(Util.getHashFromSeedname(seedname));
    }
    if (dbg) {
      if (par != null) {
        synchronized (tmpsbats) {
          par.prta(Util.clear(tmpsbats).append("RTMS: rm =").append(rm == null ? "null" : rm.toStringBuilder(null)).
                  append("\nrm2=").append(rm2 == null ? "null" : rm2.toStringBuilder(null)).
                  append("\nrm3=").append(rm3 == null ? "null" : rm3.toStringBuilder(null)));
        }
      }
    }

    // If the oob or secondChans list has not been used lately, force out the data
    if (rm != null) {
      if (rm.lastAge() > Math.max(600000., rm.getAvgSec() * 4000.)) {
        synchronized (tmpsbats) {
          if (dbg) {
            if (par != null) {
              par.prta(Util.clear(tmpsbats).append("RTMS: timeout primary chans ").
                      append(seedname).append(" age=").append(rm.lastAge()).append(" ").append(rm.getAvgSec()).
                      append(" now=").append(System.currentTimeMillis()).append(" ").append(rm.toStringBuilder(null)));
            }
          }
        }
        rm.forceOut();
        rm.close();
        synchronized (chans) {
          chans.remove(Util.getHashFromSeedname(seedname));
        }
        rm = null;
        if (rm2 != null) {
          synchronized (tmpsbats) {
            if (dbg) {
              if (par != null) {
                par.prta(Util.clear(tmpsbats).append("RTMS: promote rm2 to rm for ").append(seedname));
              }
            }
          }
          synchronized(chans) {
            chans.put(Util.getHashFromSeedname(seedname), rm2);
          }
          synchronized (secondChans) {
            secondChans.remove(Util.getHashFromSeedname(seedname));
          }
          rm = rm2;
          rm2 = null;
          if (rm3 != null) {
            synchronized (tmpsbats) {
              if (dbg) {
                if (par != null) {
                  par.prta(Util.clear(tmpsbats).append("RTMS: promote rm3 to rm2 for ").append(seedname));
                }
              }
            }
            synchronized (secondChans) {
              secondChans.put(Util.getHashFromSeedname(seedname), rm3);
            }
            synchronized (oob) {
              oob.remove(Util.getHashFromSeedname(seedname));
            }
            rm2 = rm3;
            rm3 = null;
          }
        }
      }
    }

    // If the oob or secondChans list has not been used lately, force out the data
    if (rm2 != null) {
      if (rm2.lastAge() > Math.max(600000., rm2.getAvgSec() * 4000.)) {
        synchronized (tmpsbats) {
          if (dbg) {
            if (par != null) {
              par.prta(Util.clear(tmpsbats).append("RTMS: timeout 2nd chans ").
                      append(seedname).append(" age=").append(rm2.lastAge()).append(" ").
                      append(rm2.getAvgSec()).append(" now=").append(System.currentTimeMillis()).
                      append(" ").append(rm2.toStringBuilder(null)));
            }
          }
        }
        rm2.forceOut();
        rm2.close();
        synchronized (secondChans) {
          secondChans.remove(Util.getHashFromSeedname(seedname));
        }
        rm2 = null;
        if (rm3 != null) {
          synchronized (tmpsbats) {
            if (dbg) {
              if (par != null) {
                par.prta(Util.clear(tmpsbats).append("RTMS: promote rm3 to rm2 for ").append(seedname));
              }
            }
          }
          synchronized (secondChans) {
            secondChans.put(Util.getHashFromSeedname(seedname), rm3);
          }
          synchronized (oob) {
            oob.remove(Util.getHashFromSeedname(seedname));
          }
          rm2 = rm3;
          rm3 = null;
        }
      }
    }
    if (rm3 != null) {
      if (rm3.lastAge() > Math.max(600000., rm3.getAvgSec() * 4000.)) {
        synchronized (tmpsbats) {
          if (dbg) {
            if (par != null) {
              par.prta(Util.clear(tmpsbats).append("RTMS: timeout OOB chan ").
                      append(seedname).append(" lastage=").append(rm3.lastAge()).append(" ").
                      append(rm3.getAvgSec()).append(" now=").append(System.currentTimeMillis()).
                      append(" ").append(rm3.toStringBuilder(null)));
            }
          }
        }
        rm3.forceOut();
        rm3.close();
        synchronized (oob) {
          oob.remove(Util.getHashFromSeedname(seedname));
        }
        rm3 = null;
      }
    }

    // order the rms by getJulianUSec() (last sample time), done by 3 comparison and then swaps
    if (rm != null && rm2 != null) {
      if (rm.getJulianUSec() < rm2.getJulianUSec()) { //switch these two
        synchronized (tmpsbats) {
          if (dbg) {
            if (par != null) {
              par.prta(Util.clear(tmpsbats).append("RTMS: ").append(seedname).
                      append(" Swap rm and rm2 ").append(seedname).append(" ").append(rm.lastTime()).append(" ").
                      append(rm2.lastTime()).append(" df=").append((rm2.getJulianUSec() - rm.getJulianUSec()) / 1000000));
            }
          }
        }
        synchronized (chans) {
          chans.remove(Util.getHashFromSeedname(seedname));
        }
        synchronized (secondChans) {
          secondChans.remove(Util.getHashFromSeedname(seedname));
        }
        synchronized (chans) {
          chans.put(Util.getHashFromSeedname(seedname), rm2);
        }
        synchronized (secondChans) {
          secondChans.put(Util.getHashFromSeedname(seedname), rm);
        }
        RawToMiniSeed rmtmp = rm2;
        rm2 = rm;
        rm = rmtmp;
      }
    }
    if (rm != null && rm3 != null) {
      if (rm.getJulianUSec() < rm3.getJulianUSec()) { //switch these two
        synchronized (tmpsbats) {
          if (dbg) {
            if (par != null) {
              par.prta(Util.clear(tmpsbats).append("RTMS: ").append(seedname).
                      append(" Swap rm and rm3 ").append(seedname).append(" ").append(rm.lastTime()).append(" ").
                      append(rm3.lastTime()).append(" df=").append((rm3.getJulianUSec() - rm.getJulianUSec()) / 1000000));
            }
          }
        }
        synchronized (chans) {
          chans.remove(Util.getHashFromSeedname(seedname));
        }
        synchronized (oob) {
          oob.remove(Util.getHashFromSeedname(seedname));
        }
        synchronized (chans) {
          chans.put(Util.getHashFromSeedname(seedname), rm3);
        }
        synchronized (oob) {
          oob.put(Util.getHashFromSeedname(seedname), rm);
        }
        RawToMiniSeed rmtmp = rm3;
        rm3 = rm;
        rm = rmtmp;
      }
    }
    if (rm2 != null && rm3 != null) {
      if (rm2.getJulianUSec() < rm3.getJulianUSec()) { //switch these two
        synchronized (tmpsbats) {
          if (dbg) {
            if (par != null) {
              par.prta(Util.clear(tmpsbats).append("RTMS: ").append(seedname).
                      append(" Swap rm2 and rm3 ").append(seedname).append(" ").append(rm2.lastTime()).append(" ").
                      append(rm3.lastTime()).append(" df=").append((rm3.getJulianUSec() - rm2.getJulianUSec()) / 1000000));
            }
          }
        }
        synchronized (secondChans) {
          secondChans.remove(Util.getHashFromSeedname(seedname));
        }
        synchronized (oob) {
          oob.remove(Util.getHashFromSeedname(seedname));
        }
        synchronized (secondChans) {
          secondChans.put(Util.getHashFromSeedname(seedname), rm3);
        }
        synchronized (oob) {
          oob.put(Util.getHashFromSeedname(seedname), rm2);
        }
        RawToMiniSeed rmtmp = rm3;
        rm3 = rm2;
        rm2 = rmtmp;
      }
    }

    //if(dbg) prt("rm="+rm+" rm2="+rm2+" rm3="+rm3);
    if (rm == null) {
      synchronized (tmpsbats) {
        if (par != null) {
          par.prta(Util.clear(tmpsbats).append("RTMS: create new channel for ").append(seedname).append(" rate=").append(rt).
                  append(" rm=").append(rm).append(" rm2=").append(rm2).append(" rm3=").append(rm3));
        }
      }
      rm = new RawToMiniSeed(seedname, rt, 7, year, doy, sec, micros, 0, par);
      if (dbg) {
        RawToMiniSeed.setDebug(dbg);
      }
      synchronized (chans) {
        chans.put(Util.getHashFromSeedname(seedname), rm);
      }
    } else {            // check to see if a OOB would be better
      long gap = rm.gapCalc(year, doy, sec, micros);
      //long gapb = rm.gapCalcBegin(year,doy,sec,micros,nsamp);
      synchronized (tmpsbats) {
        if (dbg) {
          if (par != null) {
            par.prt(Util.clear(tmpsbats).append("RTMS: ").append(seedname).
                    append(" ns=").append(nsamp).append(" sc=").append(sec).append(" gp=").append(gap).
                    append("\nRTMS: rm =").append(rm).append("\nRTMS: rm2=").append(rm2).
                    append("\nRTMS: rm3=").append(rm3));
          }
        }
      }

      // check gap with main compression, if it continuous at end
      // or later in time, stick with it (i.e. this is inverse, check others!)
      if (Math.abs(gap) > 1000000. / rm.getRate() / tolerancePerSample /*&& gap < 0*/) {
        if (rm2 != null) {
          long gap2 = rm2.gapCalc(year, doy, sec, micros);
          //long gap2b = rm2.gapCalcBegin(year,doy,sec,micros,nsamp);
          if (Math.abs(gap2) < 1000000. / rm.getRate() / tolerancePerSample) {
            rm = rm2;
            //gap = gap2;
            //if(gap2b < gap) gap=gap2b;
          } else if (rm3 != null) {
            long gap3 = rm3.gapCalc(year, doy, sec, micros);
            rm = rm3;
            //gap = gap3;
          } else {            // rm3 is null use it for this one unless its close
            // to rm2 or rm
            synchronized (tmpsbats) {
              if (dbg) {
                if (par != null) {
                  par.prta(Util.clear(tmpsbats).append("RTMS: create new OOB (3rd) channel for ").
                          append(seedname).append(" rate=").append(rt).append(" ").
                          append(RawToMiniSeed.timeFromUSec(sec * 1000000L + micros)));
                }
              }
            }
            rm3 = new RawToMiniSeed(seedname, rt, 7, year, doy, sec, micros, 800000, par);
            if (dbg) {
              RawToMiniSeed.setDebug(dbg);
            }
            synchronized (oob) {
              oob.put(Util.getHashFromSeedname(seedname), rm3);
            }
            rm = rm3;
            //gap = 0;
            // end rm3 is null
          }
        } else {            // rm2 is null
          synchronized (tmpsbats) {
            if (dbg) {
              if (par != null) {
                par.prta(Util.clear(tmpsbats).append("RTMS: create new second channel for ").
                        append(seedname).append(" rate=").append(rt).append(" ").append(RawToMiniSeed.timeFromUSec(sec * 1000000L + micros)));
              }
            }
          }
          //new RuntimeException("Create new second! ").printStackTrace(par.getPrintStream()); //DEBUG: get stack trace!
          rm2 = new RawToMiniSeed(seedname, rt, 7, year, doy, sec, micros, 900000, par);
          if (dbg) {
            RawToMiniSeed.setDebug(dbg);
          }
          synchronized (secondChans) {
            secondChans.put(Util.getHashFromSeedname(seedname), rm2);
          }
          rm = rm2;
          //gap = 0;
        }

      }                     // end if gap is continuous or in future
    }                       // end else this is not a new channel
    synchronized (tmpsbats) {
      if (dbg) {
        if (par != null) {
          par.prt(Util.clear(tmpsbats).append("RTMS: rm chosen is ").append(rm).
                  append(" adding ").append(timeFromUSec(sec * 1000000L + micros)).append(" ns=").append(nsamp));
        }
      }
    }

    synchronized (tmpsbats) {
      if (dbg && rm == rm2) {
        if (par != null) {
          par.prta(Util.clear(tmpsbats).append("RTMS: use 2ndChans ").append(seedname).append(" sec=").append(sec));
        }
      }
      if (dbg && rm == rm3) {
        if (par != null) {
          par.prta(Util.clear(tmpsbats).append("RTMS: use OOB chan ").append(seedname).append(" sec=").append(sec));
        }
      }

      if (Math.abs(rm.getRate() - rt) / rt > 0.001) {
        if (par != null) {
          par.prta(Util.clear(tmpsbats).append("  *** ??? rate change on ").append(seedname).
                  append(" from ").append(rm.getRate()).append(" to ").append(rt));
        }
        rm.setRate(rt);
      }
      if (dbg) {
        if (par != null) {
          par.prta(Util.clear(tmpsbats).append("RTMS: enter process rm=").append(rm).append(" ns=").append(nsamp).
                  append(" rt=").append(rm.getRate()).append(" ").append(rm.getSeedname()));
        }
      }
    }
    // processes the time series, time and flags.
    rm.process(x, nsamp, year, doy, sec, micros, activity, IOClock, quality, timingQuality, lastValue, (rm == rm3));
    synchronized (tmpsbats) {
      if (dbg) {
        if (par != null) {
          par.prta(Util.clear(tmpsbats).append("RTMS: exit process ").append(rm.getSeedname()));
        }
      }
    }
  }

  /**
   * Write out a time series for the given seed name, time series, time and optional flags, etc.
   *
   * @param seedname The Seed name of the channel that needs to be forced out
   * @return a copy of the buffer just sent through putbuf
   */
  public static byte[] forceout(StringBuilder seedname) {
    byte[] buf = null;
    // find this channel in tree map or create it
    for (int i = 0; i < 3; i++) {
      RawToMiniSeed rm = null;
      if (i == 0) {
        synchronized (chans) {
          rm = chans.get(Util.getHashFromSeedname(seedname));
        }
      }
      if (i == 1) {
        synchronized (secondChans) {
          rm = secondChans.get(Util.getHashFromSeedname(seedname));
        }
      }
      if (i == 2) {
        synchronized (oob) {
          rm = oob.get(Util.getHashFromSeedname(seedname));
        }
      }
      if (rm != null) {
        // processes the time series, time and flags. 
        rm.forceOut();
        if (buf == null) {
          buf = rm.getLastPutbuf();
        }
        rm.clear();
      }
    }

    return buf;
  }

  /**
   * Write out a time series for the given seed name, time series, time and optional flags, etc.
   *
   * @param seedname The Seed name of the channel that needs to be forced out
   */
  public static void forceoutAll(StringBuilder seedname) {
    // find this channel in tree map or create it
    RawToMiniSeed rm;
    synchronized (chans) {
      rm = chans.get(Util.getHashFromSeedname(seedname));
    }
    if (rm == null) {
      if (dbg) {
        sb.append("RTMS: call to forceoutAll on non-existing channel!! = ").append(seedname).append("\n");
      }
    } else {
      // processes the time series, time and flags. 
      //Util.prta("RTMS: ForceOutAll on "+seedname+" did primary channel");
      rm.forceOut();
      rm.clear();
    }
    synchronized (secondChans) {
      rm = secondChans.get(Util.getHashFromSeedname(seedname));
    }
    if (rm == null) {
      if (dbg) {
        sb.append("RTMS: call to forceoutALL on non-existing 2nd channel!! = ").append(seedname).append("\n");
      }
    } else {
      // processes the time series, time and flags. 
      //Util.prta("RMTS: ForceOutAll on "+seedname+" did second channel");
      rm.forceOut();
      rm.clear();
    }
    synchronized (oob) {
      rm = oob.get(Util.getHashFromSeedname(seedname));
    }
    if (rm == null) {
      if (dbg) {
        sb.append("RTMS: call to forceoutAll on non-existing oob channel!! = ").append(seedname).append("\n");
      }
    } else {
      // processes the time series, time and flags. 
      //Util.prta("RTMS: ForceOutAll on "+seedname+" did OOB channel");
      rm.forceOut();
      rm.clear();
    }

  }

  /**
   * set a MiniSeedOutputHandler object to output the data
   *
   * @param obj An object which implements MiniSeedOutputHander to use for putbuf()
   */
  public void setOutputHandler(MiniSeedOutputHandler obj) {
    overrideOutput = obj;
  }

  /**
   * set a MiniSeedOutputHandler object to output the data for all created RTMS
   *
   * @param obj An object which implements MiniSeedOutputHander to use for putbuf()
   */
  public static void setStaticOutputHandler(MiniSeedOutputHandler obj) {
    staticOverride = obj;
  }

  /**
   * write stuff to the parents log file
   */
  private void prt(String s) {
    if (parent == null) {
      Util.prt(s);
    } else {
      parent.prt(s);
    }
  }

  private void prta(String s) {
    if (parent == null) {
      Util.prta(s);
    } else {
      parent.prta(s);
    }
  }

  private void prt(StringBuilder s) {
    if (parent == null) {
      Util.prt(s);
    } else {
      parent.prt(s);
    }
  }

  private void prta(StringBuilder s) {
    if (parent == null) {
      Util.prta(s);
    } else {
      parent.prta(s);
    }
  }

  /**
   * Creates a new instance of RawToMiniSeed
   *
   * @param name The seedname of the channel to create
   * @param rt nominal data rate of the channel
   * @param nFrames Number of frames to put in the output Mini-seed (7 for 512, 63 for 4096)
   * @param year Year of the first sample for this
   * @param doy Day-of-year of the first sample
   * @param sec integer number of seconds since midnight for 1st sample
   * @param micros Fractional Microseconds of first sample
   * @param startSeq Start any compression with this sequence number
   * @param par The parent thread for logging.
   */
  public RawToMiniSeed(StringBuilder name, double rt, int nFrames,
          int year, int doy, int sec, int micros, int startSeq, EdgeThread par) {
    parent = par;
    Util.clear(seedname).append(name);
    while (seedname.length() < 12) {
      seedname.append(" ");
    }
    numFrames = nFrames;
    outbuf = new byte[64 + numFrames * 64];
    bf = ByteBuffer.wrap(outbuf);
    overrideOutput = null;
    if (staticOverride != null) {
      overrideOutput = staticOverride;
    }
    frames = new SteimFrames[numFrames];
    startSequence = startSeq;
    for (int i = 0; i < numFrames; i++) {
      frames[i] = new SteimFrames();
    }
    hdr = new FixedHeader(seedname, rt, numFrames, year, doy, sec, micros, startSequence);
    difftime = sec * 1000000L + micros;         // no data in time buffer, set it to current
    earliestTime = difftime;
    // This is a runtime exception catch for impossible Julian day conversion to try to 
    // see them more clearly
    try {
      julian = SeedUtil.toJulian(year, doy);
    } catch (RuntimeException e) {
      prta(Util.clear(tmpsbo).append("RuntimeException special catch in RTMS: seed=").append(name).
              append(" yr=").append(year).append(" doy=").append(doy).append(" sec=").append(sec));
      e.printStackTrace();
      julian = SeedUtil.toJulian(1972, 1);   // give it some Julian day just in case
    }
    ndata = 0;
    rate = rt;
    lastUpdate = System.currentTimeMillis();

    reverse = 2147000000;
    integration = 0;
    currentWord = 0;
    currentFrame = 0;
    ns = 0;
    periodUSec = (long) Math.round(1000000. / rate);
    toleranceUsec = (int) (1000000 / rate / tolerancePerSample);
  }

  /**
   * calculate the gap from the last sample in this RawToMiniSeed stream and the time represented by
   * sec and micros
   *
   * @param year The year
   * @param doy The day of year
   * @param sec Time since midnight of first sample
   * @param micros Micros to add to seconds of first sample
   * @return The time difference in micros
   */
  public long gapCalc(int year, int doy, int sec, int micros) {
    long time = SeedUtil.toJulian(year, doy) * 86400000000L + sec * 1000000L + micros;
    long gap = (long) (time - difftime - ndata * periodUSec - julian * 86400000000L );
    return gap;
  }

  /**
   * calculate the gap from the first sample in this RawToMiniSeed stream and the time represented
   * by sec and micros
   *
   * @param year The year
   * @param doy The day of year
   * @param sec Time since midnight of first sample
   * @param micros Micros to add to seconds of first sample
   * @param n Number of samples in packet
   * @return The time difference in micros
   */
  public long gapCalcBegin(int year, int doy, int sec, int micros, int n) {
    long time = SeedUtil.toJulian(year, doy) * 86400000000L + sec * 1000000L + micros;
    long gap = (long) (julian * 86400000000L + earliestTime - (time + n / rate * 1000000. + 0.001));

    // if the gap is on the order of days, check to make sure it is not a day problem
    /*if(Math.abs(gap) >= 86399999999L) {
      int jul = SeedUtil.toJulian(year,doy);
      if(jul != julian) {
        gap = gap + (jul - julian)*86400000000L;
      }
    }*/
    return gap;
  }

  /**
   * process a time series into this RawToMiniSeed - used by static functions addTimeSeries to
   * actually modify the channel synchronized so this cannot have two sets in process at same time.
   * If this channel is configured as a hydra bound channel, it is put in the HydraQueue for
   * processing (if it is latter in time than the last one!)
   *
   * @param ts Array of ints with time series
   * @param nsamp Number of samples in x
   * @param year The year of the 1st sample
   * @param doy The day-of-year of first sample
   * @param sec Seconds since midnight of the first sample
   * @param micros Microseconds (fraction of a second) to add to seconds for first sample
   * @param activity The activity flags per SEED volume (ch 8 pg 93)
   * @param IOClock The I/O and clock flags per SEED volume
   * @param quality The data quality as defined in SEED volume
   * @param timingQuality The timing quality for blockette 1001
   * @param lastValue Only needed if exact match to existing seed is needed.
   *
   */
  public synchronized void process(int[] ts, int nsamp, int year, int doy, int sec, int micros,
          int activity, int IOClock, int quality, int timingQuality, int lastValue) {
    process(ts, nsamp, year, doy, sec, micros, activity, IOClock, quality, timingQuality, lastValue, false);
  }

  /**
   * process a time series into this RawToMiniSeed - used by static functions addTimeSeries to
   * actually modify the channel synchronized so this cannot have two sets in process at same time.
   * If this channel is configured as a hydra bound channel, it is put in the HydraQueue for
   * processing (if it is latter in time than the last one!)
   *
   * @param ts Array of ints with time series
   * @param nsamp Number of samples in x
   * @param year The year of the 1st sample
   * @param doy The day-of-year of first sample
   * @param sec Seconds since midnight of the first sample
   * @param micros Microseconds (fraction of a second) to add to seconds for first sample
   * @param activity The activity flags per SEED volume (ch 8 pg 93)
   * @param IOClock The I/O and clock flags per SEED volume
   * @param quality The data quality as defined in SEED volume
   * @param timingQuality The timing quality for blockette 1001
   * @param isRM3 If true, this is expected to be hacked up so we do not put out any SendEvents.
   * @param lastValue Only needed if exact match to existing seed is needed.
   *
   */
  public synchronized void process(int[] ts, int nsamp, int year, int doy, int sec, int micros,
          int activity, int IOClock, int quality, int timingQuality, int lastValue, boolean isRM3) {
    // The time internally is of the first difference.  We need to check that there is
    // not a time gap which should cause us to close out this one
    if (nsamp == 0) {
      return;
    }
    if (avgSec == 0.) {
      avgSec = nsamp / rate;
    } else {
      avgSec = (avgSec * 9. + nsamp / rate) / 10.;
    }
    avgSec = ((int) avgSec * 10.) / 10.;

    long gap = gapCalc(year, doy, sec, micros);
    boolean midnightProcess = false;
    lastUpdate = System.currentTimeMillis();

    // Check time for formal correctness and report if not
    if ((!isRM3 && julian != SeedUtil.toJulian(year, doy)
            && !(julian + 1 == SeedUtil.toJulian(year, doy) && sec < 100))
            || // its just a packet on the next day early
            sec < 0 || sec >= 86400 || micros > 1000000 || micros < 0) {
      prta("RTMS: this  =" + toString());
      prta("RTMS: odd rm=" + chans.get(Util.getHashFromSeedname(seedname)));
      prta("RTMS: odd rm2=" + secondChans.get(Util.getHashFromSeedname(seedname)));
      prta("RTMS: odd rm3=" + oob.get(Util.getHashFromSeedname(seedname)));
      SendEvent.debugSMEEvent("RTMSOddTime", "RTMS: " + seedname + " julian=" + julian + " " + SeedUtil.toJulian(year, doy) + " yr=" + year + " doy=" + doy + " sec=" + sec + " us=" + micros, this);
      new RuntimeException("RTMS: " + seedname + " julian=" + julian + " " + SeedUtil.toJulian(year, doy) + "  time not right! yr=" + year + " doy=" + doy + " sec=" + sec + " micros=" + micros + " ns" + nsamp + " rt=" + rate).printStackTrace();
      return;
    }

    // If this data is from the next day, cut off the old day
    if (SeedUtil.toJulian(year, doy) != julian) {
      prta(Util.clear(tmpsbo).append("RTMS: ").append(seedname).append(" New day on input (EOD?) :").
              append(julian).append(" != ").append(SeedUtil.toJulian(year, doy)).
              append(" sec=").append(sec).append(" usec=").append(micros));
      forceOut(reverse);
      clear();
      julian = SeedUtil.toJulian(year, doy);
      hdr.setStartTime(year, doy, sec, micros);
      earliestTime = sec * 1000000L + micros;
      difftime = earliestTime;
    }
    // If this is going to span midnight, and the time phase has shifted, need to close old block and open a new one with this rec
    if (sec * 1000000L + micros + nsamp * periodUSec >= 86400000000L) { // This data will span to tomorrow
      if (((sec * 1000000L + micros) % periodUSec) != (earliestTime % periodUSec)) {
        prta(Util.clear(tmpsbo).append("RTMS: ").append(seedname).append(" Day Span packet different time phase :" + " sec=").
                append(sec).append(" usec=").append(micros).append(" old phase=").append(earliestTime).
                append(" periodUSec=").append(periodUSec).append(" phases=").append((sec * 1000000L + micros) % periodUSec).
                append("/").append(earliestTime % periodUSec));
        forceOut(reverse);
        clear();
        julian = SeedUtil.toJulian(year, doy);
        hdr.setStartTime(year, doy, sec, micros);
        earliestTime = sec * 1000000L + micros;
        difftime = earliestTime;
      }
    }

    // iIf all  of these are true a force out has occurred, this is new data, do not check for gaps
    if (currentWord == 0 && reverse == 2147000000 && ns == 0 && currentFrame == 0) {
      hdr.setStartTime(year, doy, sec, micros);
      earliestTime = sec * 1000000L + micros;
    } // If this is earlier than this first, check for continuity the other way!
    else {
      if ((sec * 1000000L + micros) < earliestTime) {
        gap = gapCalcBegin(year, doy, sec, micros, nsamp);
        if (dbg) {
          sb.append("RTMS: ").append(seedname).append(" ").append(earliestTime()).
                  append(" discont2 force out gap=").append(gap / 1000000.).append(" sec\n");
        }
        if (dbg) {
          prta(Util.clear(tmpsbo).append("RTMS: ").append(seedname).append(" ").
                  append(earliestTime()).append(" discont2 frc out gap=").append(gap / 1000000.));
        }
        forceOut(reverse);
        clear();
        // Since this is not continuous, need to reset header time
        hdr.setStartTime(year, doy, sec, micros);
        //else prt("RTMS: "+seedname+" "+earliestTime()+" add begin continuous");
        earliestTime = sec * 1000000L + micros;
        difftime = earliestTime;

      } // Its later than first time, see if it is a gap at the end
      else {
        // If this gap is bigger than 1/2 sample (1/2 sample is 500000./rate in micros)
        if (Math.abs(gap) > 1000000. / rate / tolerancePerSample) {
          if (dbg) {
            sb.append("RTMS: ").append(seedname).append(" ").append(lastTime()).
                    append(" discont force out gap=").append(gap / 1000000.).append(" sec\n");
          }
          if (dbg) {
            prta(Util.clear(tmpsbo).append("RTMS: ").append(seedname).append(" ").append(lastTime()).
                    append(" discont frc out gap=").append(gap / 1000000.).append(" Sec fr=").
                    append(currentFrame).append(" wd=").append(currentWord).append(" ns=").append(ns).
                    append(" rt=").append(rate).append(" rev=").append(reverse).
                    append(" lst=").append(lastValue).append(" sb.len").append(sb.length()));
          }
          forceOut(reverse);
          clear();
          earliestTime = sec * 1000000L + micros;     // set initial time to this buffer
          difftime = earliestTime;
          hdr.setStartTime(year, doy, sec, micros);
        }
      }
    }

    // Set the flags for all putbufs from this call to process according to the flags
    hdr.setActivity(activity);
    hdr.setIOClock(IOClock);
    hdr.setQuality(quality);
    hdr.setTimingQuality(timingQuality);

    // while we have enough differences, prepare to move them
    int xlen = Math.max(0, ndata) + nsamp;     // need the actual length of stuff in x and d for later
    if (x.length < Math.max(0, ndata) + nsamp) {
      x = new int[Math.max(0, ndata) + nsamp * 120 / 100];
    }
    //int [] x = new int[Math.max(0,ndata)+nsamp];        // space for the time series
    if (d.length < Math.max(0, ndata) + nsamp) {
      d = new int[Math.max(0, ndata) + nsamp * 120 / 100];       // space for the differences
    }    //int [] d = new int[Math.max(0,ndata)+nsamp];
    if (d.length == 0) {
      prta("***** there are no samples in d!" + toString());
    }
    System.arraycopy(data, 0, x, 0, ndata);
    //for(int i=0; i<ndata; i++) x[i]=data[i];// put left over data into data array
    System.arraycopy(ts, 0, x, ndata, nsamp);
    for (int i = ndata + nsamp; i < x.length; i++) {
      x[i] = 0;  // zero fill remainder
    }    //for(int i=0; i<nsamp; i++) x[i+ndata] = ts[i];
    for (int i = ndata + nsamp; i < d.length; i++) {
      d[i] = 0;  // Zero fill remainder
    }
    if (reverse == 2147000000) {
      d[0]
              = x[0] - lastValue;       // starting up you do not know the reverse
    } else {
      d[0]
              = x[0] - reverse;                 // else use it to get first difference
      if (lastValue != reverse && dbg) {
        sb.append(seedname).append(" Last != reverse ").append(lastValue).append(" != ").
                append(reverse).append("\n");
      }
    }
    for (int i = 1; i < nsamp + ndata; i++) {
      d[i] = x[i] - x[i - 1];  // compute the differences
    }
    nextDiff = 0;
    boolean somePacked = true;

    // we need to get the current key 
    int key;
    if (currentWord == 0) {
      key = 0;
      currentWord = 1;
    } else {
      key = frames[currentFrame].get(0);  // Get the current key
    }
    int dnib;         // per manual coding of 2nd key (stored in data word high order)
    int bits;         // number of bits in the packing
    int npack;        // Number of samples to pack
    int ck;           // coding of key in main key word
    int loops = 0;
    while (somePacked) {
      loops++;
      if (loops > nsamp * 10) {
        prta(Util.clear(tmpsbo).append("  ***** ception Infinite loop process seed=").append(seedname).
                append(" nextdiff=").append(nextDiff).append(" nsamp=").append(nsamp));
        SendEvent.edgeSMEEvent("RTMSInfLoop", seedname + " compression to infinite loop", "RawToMiniSeed");
        return;
      }
      // check for startup conditions, start of new frame
      if (currentFrame == 0 && currentWord == 1 && nextDiff < xlen) {
        frames[0].put(x[nextDiff], 1);        // forward integration constant
        if (dbg && sb == null) {
          prt(Util.clear(tmpsbo).append("RTMS: null sb and dbg is on ").append(seedname));
        }
        if (dbg) {
          sb.append("Word 1 is hdr ").append(Integer.toHexString(x[nextDiff])).append("\n");
        }
        currentWord = 3;                  // Skip the key, ia0 and ian
        key = 0;
        frames[currentFrame].put(key, 0);            // mark key as zero
      }

      if ((somePacked = packOK(d, nextDiff, 7, 8))) {            // 7x4 bit diffs
        ck = 3;
        dnib = 2;
        npack = 7;
        bits = 4;
      } else if ((somePacked = packOK(d, nextDiff, 6, 16))) {    // 6x5 bit diffs
        ck = 3;
        dnib = 1;
        npack = 6;
        bits = 5;
      } else if ((somePacked = packOK(d, nextDiff, 5, 32))) {    // 5x6 bit diffs
        ck = 3;
        dnib = 0;
        npack = 5;
        bits = 6;
      } else if ((somePacked = packOK(d, nextDiff, 4, 128))) {   // 4x8 bit diffs
        ck = 1;
        dnib = -1;
        npack = 4;
        bits = 8;
      } else if ((somePacked = packOK(d, nextDiff, 3, 512))) {   // 3x10 bit diffs
        ck = 2;
        dnib = 3;
        npack = 3;
        bits = 10;
      } else if ((somePacked = packOK(d, nextDiff, 2, 16384))) { // 2x15 bit diffs
        ck = 2;
        dnib = 2;
        npack = 2;
        bits = 15;
      } else {                                                 // 1x30 bit diff
        if (Math.abs(d[nextDiff]) > 536870912) {
          prta(Util.clear(tmpsbo).append("RTMS: ").append(seedname).append(" **** diff bigger than 30 bits ").
                  append(d[nextDiff]).append(" packet will be broken").
                  append(year).append(":").append(doy).append(" sec=").append(sec));
        }
        ck = 2;
        dnib = 1;
        npack = 1;
        bits = 30;
        somePacked = true;
      }
      if (somePacked) {
        // If this packing crosses the day boundary, adjust it so it does not and cut off
        // midnightProcess insures this is only dones once in a process buffer
        long begbuf = ((long) sec * 1000000L + micros + (nextDiff + npack - ndata) * periodUSec);
        //if(dbg) prta("RTMS: "+seedname+" test midnight "+begbuf+" mid="+midnightProcess);
        if (!midnightProcess
                && (begbuf > (86400000000L + periodUSec - 1L)
                || // This is on the next day
                begbuf <= 0)) { // packet on new day, ndata on other side
          //               The next day + 1 sample less a bit
          midnightProcess = true;
          int ndataold = ndata;
          int nleft;
          if (begbuf < 0) {
            nleft = (int) ((-begbuf + periodUSec - 1L) / periodUSec);
          } else {
            nleft = (int) (((86400000000L + periodUSec - 1L)
                    - // The current time of the next samples                       usecs/samples
                    ((long) sec * 1000000L + micros + ((long) (nextDiff - ndata)) * periodUSec)) / periodUSec);
          }
          //if(dbg)
          prta(Util.clear(tmpsbo).append("RTMS: ").append(seedname).append(" EOD cut off ndata begbuf=").
                  append(begbuf).append(" needed= ").append(nleft).append(" ndataold=").append(ndataold).
                  append(" nextDiff=").append(nextDiff).append(" nsamp=").append(nsamp).
                  append(" ns=").append(ns).append(" sec=").append(sec).append(" usec=").append(micros).
                  append(" period=").append(periodUSec).append(" data.len=").append(data.length));
          if (nleft <= (nsamp + ndata - nextDiff)) {  // Do we have enough data for a midnight cutoff

            // There are ndata that need to be forced into this frame,  move the desired samples
            // to the begining of the data buffer to fake forceOut() to use these samples.
            for (int i = 0; i < nleft; i++) {
              data[i] = x[nextDiff + i];
            }
            ndata = nleft;
            frames[currentFrame].put(key, 0);          // save any mods made to key so forceOut gets it
            if (nextDiff > 0) {
              forceOut(x[nextDiff - 1]); // used faked data to force it out
            } else {
              forceOut(reverse);           // note : no call to clear() as we are just faking
            }            // update time to next sample in header
            int julian2 = SeedUtil.toJulian(year, doy);
            julian2++;
            int[] ymd = SeedUtil.fromJulian(julian2);
            year = ymd[0];
            int newdoy = SeedUtil.doy_from_ymd(ymd);
            // we added a day so adjust micros by that amount.
            doy = newdoy;
            sec = sec - 86400;
            julian = julian2;   // Do not force out again on day change
            //          hdr.setStartTime(ymd[0], doy, sec, micros+(nextDiff-ndataold+ndata)*periodUSec);
            //          earliestTime = sec*1000000L+micros;
            nextDiff += nleft;                // adjust nextDiff for the number of samps just out
            hdr.setStartTime(ymd[0], doy, sec, micros + (nextDiff - ndataold) * periodUSec);
            earliestTime = sec * 1000000L + micros + (nextDiff - ndataold) * periodUSec;
            ndata = ndataold;             // finished with fakeout, restore ndata
            prta(Util.clear(tmpsbo).append("RTMS: ").append(seedname).append(" EOD cut done ndata=").append(ndata).
                    append(" nextDiff=").append(nextDiff).append(" ns=").append(ns).
                    append(" sec=").append(sec).append(" usec=").append(micros));
            //difftime = (long) sec * 1000000L + (long) micros + (long) ((nextDiff-ndata)/rate*1000000.);
            currentWord = 1;
            key = 0;
            continue;                         // go to bottom of loop to start new packing
          }
        }

        // Check to see if there is enough data remaining to do this compression
        if (npack > (nsamp + ndata - nextDiff)) {
          for (int i = nextDiff; i < nsamp + ndata; i++) {
            data[i - nextDiff] = x[i];
          }
          frames[currentFrame].put(key, 0);        // save the key for next time

          // The time of the first data sample in the difference array (for gap detection)
          // time of ndata + samples into the new data (nextDiff-ndata) at rate
          difftime = (long) sec * 1000000L + (long) micros + (long) ((nextDiff - ndata) * periodUSec);
          julian = SeedUtil.toJulian(year, doy);
          if (difftime > 86400000000L) {
            //julian++; // Mar 5, 2008 - if data is compressed that already does not cross the boundary, we commented this
            // so the check at the top of the routine would cause the data to be cut off on julian day skip.
            difftime -= 86400000000L;
          }
          ndata = nsamp + ndata - nextDiff;
          // reverse is the data value just proceeding the difference array
          if (nextDiff > 0) {
            reverse = x[nextDiff - 1];
          } else {
            //Util.prt("Process: set reverse first sample minus diff="+data[0]+" "+d[0]+
            //    " nsamp="+nsamp+" "+seedname+" "+stringFromUSec(difftime));
            reverse = data[0] - d[0];    // 1/08 cut the BS - reverse has to be data minus the difference (to get same diff!)
          }
          //else reverse=lastValue;   // if we dont have the value, use the last one!
          return;                     // There is not enough data to compress yet
        }

        // Pack up npack samples of bits using the ck and dnib
        int w = 0;
        int mask = 0x3FFFFFFF;
        if (bits < 30) {
          mask = mask >> (30 - bits);
        }
        for (int i = 0; i < npack; i++) {
          w = (w << bits) | (d[nextDiff++] & mask);
        }
        ns += npack;
        if (dnib != -1) {
          w = w | (dnib << 30);
        }
        key = (key << 2) | ck;
        if (dbg) {
          sb.append("Word ").append(currentWord).append(" is ").append(ck).append(" dnib=").append(dnib).
                  append(" ns=").append(ns).append(" diff(").append(bits).append(" bit)=");
          for (int i = 0; i < npack; i++) {
            sb.append(d[nextDiff + i - npack]).append(" ");
          }
          sb.append("\n");
        }
        frames[currentFrame].put(w, currentWord);

        // Advance to next word, if this is end-of-frame, do the book keeping
        currentWord++;
        if (currentWord >= 16) {
          frames[currentFrame].put(key, 0);
          if (dbg) {
            sb.append("key=").append(Integer.toHexString(key)).append("\n");
          }
          key = 0;                              // clear key for next run
          if (currentFrame == numFrames - 1) {
            if (dbg) {
              sb.append("Word 2 of zero is hdr ").append(Integer.toHexString(x[nextDiff - 1])).append("\n");
            }
            frames[0].put(x[nextDiff - 1], 2);
            reverse = x[nextDiff - 1];
          }
          currentFrame++;
          currentWord = 1;           // point to first data word, word 0 will get the key

          // Is this new frame beyond the last frame (record is full?)
          if (currentFrame == numFrames) {    // yes, output the record and do book keeping
            currentFrame = 0;
            //byte [] buf = new byte[64+numFrames*64];
            //ByteBuffer bf = ByteBuffer.wrap(buf);
            bf.clear();
            Arrays.fill(outbuf, (byte) 0);    // zero the buffer because some partial frames might not be filled.
            hdr.setNSamp(ns);                 // set the number of samples in fixed hdr
            hdr.setActualNFrames(numFrames);
            ns = 0;

            bf.put(hdr.getBytes());     // get the hdr 9
            // put all of the frames into the buffer
            for (int i = 0; i < numFrames; i++) {
              bf.put(frames[i].getBytes());
            }
            // call routine to dispose of this frame
            if (dbg) {
              sb.append("Call putbuf with len=").append((numFrames + 1) * 64).
                      append(" override=").append(overrideOutput).append("\n");
            }
            putbuf(bf.array(), (numFrames + 1) * 64);
            /*try {
              byte [] fr = new byte[numFrames*64];
              System.arraycopy(bf.array(), 64, fr, 0, numFrames*64);
              int [] samples = Steim2.decode(fr, hdr.getNSamp(), false);
              prt("NSamples returned="+samples.length);
            }
            catch(SteimException e) {prt("Put outbuf Steim Exception");}*/
            // calculate based on latest time available the start time of next record
            hdr.setStartTime(year, doy, sec, ((long) micros) + ((long) ((nextDiff - ndata) / rate * 1000000.)));
            // increment the sequence number for the fixed header
            hdr.incrementSequence();
          }
        }     // if(currentWord < 16)
      }       // if somePacked
    }         // while (somePacked)
    prt("****** Unusual exit from process!");
    Util.exit(0);
  }

  // These variables are used by the putbuf related routines
  static byte[] lastbuf;
  static boolean newPutbuf;

  /**
   * for debugging returns state of "newPutbuf" boolean which is set by putbuf() whenever new output
   * is written. User could monitor this for change to "true" a and then use getLastBuf to retrieve
   * the output.
   *
   * @return Current value of newPutbuf boolean
   */
  static public boolean newPutbuf() {
    return newPutbuf;
  }

  /**
   * reset the newPutbuf flag and zero the lastBuf for debugging use
   *
   * @param t Value to set newPutbuf to
   */
  static public void setNewPutbuf(boolean t) {
    if (!newPutbuf && lastbuf != null) {
      Arrays.fill(lastbuf, (byte) 0);
    }
    newPutbuf = t;
  }

  public byte[] getLastPutbuf() {
    return lastputbuf;
  }

  /**
   * this routine puts a complete mini-seed blockette in outbuf out to the correct file using the
   * EdgeFile related routines. For debug purposes it stores the "newPutBuf" to indicate this was
   * called by internal processing since the last time that flag was reset and puts a copy of the
   * last written buffer in lastbuf.
   */
  private void putbuf(byte[] buf, int size) {
    if (overrideOutput != null) {
      overrideOutput.putbuf(buf, size);
      return;
    }
    newPutbuf = true;
    if (lastbuf == null) {
      lastbuf = new byte[size];
    }
    if (lastputbuf == null) {
      lastputbuf = new byte[size];
    }
    System.arraycopy(buf, 0, lastbuf, 0, size);
    System.arraycopy(buf, 0, lastputbuf, 0, size);
    //prt("Put outbuf size="+size);

    try {
      MiniSeed ms = msp.get(buf, 0, size);
      if (ms.getTimeInMillis() % 86400000L + (ms.getNsamp() - 1) / ms.getRate() * 1000. > 86400000) {
        prt("*** RTMS: created block that spans midnight!=" + ms);
      }
      //prt("RTMS: Putbuf rw="+(backblk+"    ").substring(0,5)+" ms="+ms.toString());
      // Write to the data files 
      if (dbg) {
        sb.append("putbuf(): writeMiniSeed for ms=").append(ms);
      }
      IndexBlock.writeMiniSeed(ms, "RTMS");
      msp.free(ms);
      // This is debug writes of data from putbuf
      if (back != null) {
        back.writeBlock(backblk, buf, 0, size);
        backblk += (size + 511) / 512;
      }
    } /*catch (EdgeFileReadOnlyException e) {
      prt("RTMS: could not write using writeMiniSeed - file is read only");
    }
    catch (EdgeFileCannotAllocateException e) {
      prt("RTMS: could not write using writeMiniSeed");
    }*/ catch (IllegalSeednameException e) {
      prt("Putbuf: illegal seedname=" + e.getMessage());
    } catch (IOException e) {
      Util.IOErrorPrint(e, "Error writing backing file from RawToMiniSeed");
    }
  }

  /**
   * for debug purposes, get the last data buffer written out by putbuf
   *
   * @return The last output of putbuf
   */
  public static byte[] getLastbuf() {
    return lastbuf;
  }

  /**
   * forceOut takes any remaining data in the data (ndata) and adds it to the end of the current
   * frame and then calls putbuf. If this is actually the end of all of the data, the user must call
   * clear() afterward to reset all of the counters. This is because day roll overs just fake up a
   * data[ndata] and call this routine but do not want other data about the current context to be
   * changed The reverse integration constant used is the one stored in the object
   */
  public void forceOut() {
    forceOut(reverse);
  }

  /**
   * forceOut takes any remaining data in the data (ndata) and adds it to the end of the current
   * frame and then calls putbuf. If this is actually the end of all of the data, the user must call
   * clear() afterward to reset all of the counters. This is because day roll overs just fake up a
   * data[ndata] and call this routine but do not want other data about the current context to be
   * changed
   *
   * @param reverse The data point before the beginning of this set of data frames
   */
  public synchronized void forceOut(int reverse) {
    // We must be done so position key to top of word before forcing out
    if (currentFrame == 0 && currentWord <= 1) {
      currentWord = 0;
      return;     // nothing to force out!
    }
    if (dbg) {
      sb.append("Force out called reverse=").append(reverse).append("\n");
    }
    if (currentWord >= 16) {  //DEBUG: this should nebvver happen, but it does!!!!
      prta(Util.clear(tmpsbo).append(seedname).append("Exception: forceoutCalled with currentWord>=16=").
              append(currentWord).append(" ndata=").append(ndata));
      // clear the output related variables (things into the frames).
      // Set up for new compression buffer
      ns = 0;
      currentFrame = 0;
      currentWord = 0;
      reverse = 2147000000;         // mark reverse is unknown, we forced it out
    }

    if (dbg) {
      prta(Util.clear(tmpsbo).append(seedname).append(" forceOut() called with rev=").
              append(reverse).append(" ndata=").append(ndata));
    }
    int key = frames[currentFrame].get(0);
    // is there any data sitting in ndata, we must force it out as well
    if (ndata > 0) {
      int dnib;         // per manual coding of 2nd key (stored in data word high order)
      int bits;         // number of bits in the packing
      int npack;        // Number of samples to pack
      int ck;           // coding of key in main key word
      if (d.length < ndata) {
        d = new int[ndata * 120 / 100];
      }
      //int [] d = new int [ndata];       // space for the differences 

      if (reverse == 2147000000) {
        d[0] = 0;         // no prior data, so difference is unknown
      } else {
        d[0] = data[0] - reverse;                 // else use it to get first difference
      }
      for (int i = 1; i < ndata; i++) {
        d[i] = data[i] - data[i - 1];  // compute the differences
      }
      switch (ndata) {
        case 7:
          // 7x4 bit diffs
          ck = 3;
          dnib = 2;
          npack = 7;
          bits = 4;
          break;
        case 6:
          // 6x5 bit diffs
          ck = 3;
          dnib = 1;
          npack = 6;
          bits = 5;
          break;
        case 5:
          // 5x6 bit diffs
          ck = 3;
          dnib = 0;
          npack = 5;
          bits = 6;
          break;
        case 4:
          // 4x8 bit diffs
          ck = 1;
          dnib = -1;
          npack = 4;
          bits = 8;
          break;
        case 3:
          // 3x10 bit diffs
          ck = 2;
          dnib = 3;
          npack = 3;
          bits = 10;
          break;
        case 2:
          // 2x15 bit diffs
          ck = 2;
          dnib = 2;
          npack = 2;
          bits = 15;
          break;
        default:
          // 1x30 bit diff
          ck = 2;
          dnib = 1;
          npack = 1;
          bits = 30;
          break;
      }
      if (!packOK(d, 0, npack, 1 << (bits - 1))) {
        prt(Util.clear(tmpsbo).append(seedname).append(" Steim2 Exception: force out does not work!  bits=").
                append(bits).append(" npack=").append(npack).append(" ndata=").append(ndata).
                append(" reverse=").append(reverse).append(" limit=").append(1 << (bits - 1)));
        prt(Util.clear(tmpsbo).append(seedname).append(" ns=").append(ns).append(" curWord=").append(currentWord).
                append(" curFrm=").append(currentFrame).append(" nextdiff=").append(nextDiff));
        for (int i = 0; i < ndata; i++) {
          prt(Util.clear(tmpsbo).append("d[").append(i).append("]=").append(d[i]).append(" data[").append(i).append("]=").append(data[i]));
        }
        RuntimeException e = new RuntimeException("force out does not work");
        if (parent == null) {
          e.printStackTrace(Util.getOutput());
        } else {
          e.printStackTrace(parent.getPrintStream());
        }
        //Util.exit(0);     // extreme action!
      }

      // Pack up npack samples of bits using the ck and dnib
      int w = 0;
      int mask = 0x3FFFFFFF;
      if (bits < 30) {
        mask = mask >> (30 - bits);
      }
      int nd = 0;
      for (int i = 0; i < npack; i++) {
        w = (w << bits) | (d[nd++] & mask);
      }
      ns += npack;
      if (dnib != -1) {
        w = w | (dnib << 30);
      }
      key = (key << 2) | ck;
      if (dbg) {
        String s = "Force out Word " + currentWord + " is " + ck + " dnib=" + dnib + " ns=" + ns + " diff(" + bits + " bit)=";
        for (int i = 0; i < npack; i++) {
          s = s + d[nd + i - npack] + " ";
        }
        sb.append(s).append("\n");
        //prt("ForceOut outbuf="+sbtmp.toString());
      }
      if (currentWord >= 16) {
        prta(Util.clear(tmpsbo).append("ERR:Forceout current word=").append(currentWord).
                append(" bits=").append(bits).append(" ndata=").append(ndata));
      }
      frames[currentFrame].put(w, currentWord);
      currentWord++;
    }
    while (currentWord < 16) {
      key = key << 2;                               // move key up
      frames[currentFrame].put(0, currentWord);   // zero out the unused words in the frame
      currentWord++;
    }
    frames[currentFrame].put(key, 0);
    // put all zeros in any remaining frames
    for (int i = currentFrame + 1; i < numFrames; i++) {
      for (int j = 0; j < 16; j++) {
        frames[i].put(0, j);
      }
    }
    if (ndata <= 0) {
      frames[0].put(reverse, 2);   // not clear it can be < 0????
    } else {
      frames[0].put(data[ndata - 1], 2);      // reverse integration is last data sample
    }
    // clean up and force the output buffer
    bf.clear();
    Arrays.fill(outbuf, (byte) 0);    // Make sure output buffer is zeros since frames are not complete!
    hdr.setNSamp(ns);                 // set the number of samples in fixed hdr
    hdr.setActualNFrames(currentFrame + 1);

    bf.put(hdr.getBytes());     // get the hdr 
    // put all of the frames into the buffer
    for (int i = 0; i < numFrames; i++) {
      bf.put(frames[i].getBytes());
    }
    // call routine to dispose of this frame
    putbuf(bf.array(), (numFrames + 1) * 64);

    // clear the output related variables (things into the frames).
    // Set up for new compression buffer
    ns = 0;
    currentFrame = 0;
    currentWord = 0;
    //reverse=2147000000;         // mark reverse is unknown, we forced it out

    // calculate based on latest time available the start time of next record
    //hdr.setStartTime(year,doy,sec,((long)micros)+((long)((nextDiff-ndata)/rate*1000000.)));
    // increment the sequence number for the fixed header
    hdr.incrementSequence();

  }

  /**
   * Get the RawToMiniSeed record for the given channel name from the static tree of same
   *
   * @param seedname The 12 character seed name for the channel
   * @return A RawToMiniSeed for the channel or null if one is not yet defined
   */
  public static RawToMiniSeed getRawToMiniSeed(String seedname) {
    if (chans == null) {
      return null;
    }
    synchronized(chans) {
      return (RawToMiniSeed) chans.get(Util.getHashFromSeedname(seedname));
    }
  }

  /**
   * Get the RawToMiniSeed record for the given channel name from the static tree of same
   *
   * @param seedname The 12 character seed name for the channel
   * @return A RawToMiniSeed for the channel or null if one is not yet defined
   */
  public static RawToMiniSeed getRawToMiniSeed(StringBuilder seedname) {
    if (chans == null) {
      return null;
    }
    synchronized(chans) {
      return (RawToMiniSeed) chans.get(Util.getHashFromSeedname(seedname));
    }
  }

  /**
   * create a backing file for this RawToMiniSeed with the given name. Used for debug only to create
   * files which only contain a concatenation of blocks for a single channel
   *
   * @param filename The file name to put in the file
   * @throws FileNotFoundException If the filename cannot be opened for "rw"
   */
  public void openFile(String filename) throws java.io.FileNotFoundException {
    //if(back == null) {
    back = new RawDisk(filename, "rw");
    backblk = 0;
    try {
      back.setLength(0L);
    } catch (IOException e) {
    }
    //}
  }

  /**
   * reset the backing file length to zero. For debugging purposes - not for normal use.
   */
  public void resetFile() {
    backblk = 0;
    try {
      back.setLength(0L);
    } catch (IOException e) {
    }
  }

  /**
   * write the contents of the last call to putbuf to the backing store at end of file
   *
   * @throws IOException if the file cannot be written to.
   */
  public void writeLastBuf() throws IOException {
    if (back != null) {
      back.writeBlock(backblk, lastbuf, 0, lastbuf.length);
      backblk += (lastbuf.length + 511) / 512;
    }
  }

  /**
   * cause this channel to be cleared for a new compression buffer. ANy data in progress is
   * discarded.
   */
  public void clear() {

    // Set up for new compression buffer
    ns = 0;
    nextDiff = 0;
    ndata = 0;
    currentFrame = 0;
    currentWord = 0;
    reverse = 2147000000;         // mark reverse is unknown, we forced it out

  }

  /**
   * return true of the given data differences will bit in to ns samples of size limit by limit
   */
  private boolean packOK(int[] d, int i, int ns, int limit) {
    int low = -limit;
    boolean ok = true;
    for (int j = i; j < i + ns; j++) {
      if (j < d.length) {
        if (d[j] < low || d[j] >= limit) {  // <= is not needed < should be o.k.
          ok = false;
          break;
        }
      } else {
        break;         // not enough data break out
      }
    }
    return ok;
  }

  /**
   * This internal class represents and implements translation to/from the fixed 48 byte header of
   * mini-seed blockettes.
   */
  class FixedHeader {

    int sequence;
    int startSequence;
    double rate;          // Digitizing rate in hertz 
    StringBuilder seedname = new StringBuilder(12);      // 12 character seed name
    int numFrames;        // Number of 64 byte Steim Frames in a whole record (for blockette1000)
    int actualFrames;     // Number of frames in this particular one
    short year;
    short doy;
    long time;              // In usecs since midnight
    short rateFactor;       // Rate converted to integer
    short rateMultiplier;   // Divisor if needed for rate
    short nsamp;
    byte activity, IOClock, quality, timingQuality;
    //byte usecs;

    /**
     * increment the sequence number of this header
     */
    public void incrementSequence() {
      sequence++;
      if (sequence < 0 || sequence >= 1000000) {
        sequence = 0;
      }
    }

    /**
     * instantiate a Fixed header
     *
     * @param name The Seed name
     * @param rt Nominal data rate in Hz
     * @param nFrames Number of steim 64 byte frames in the attached data portion
     * @param yr Year of first sample in this Mini-seed blockette
     * @param dy Day-of-year of 1st sample
     * @param sec integral # of seconds since midnight
     * @param micros Fractional Microseconds of 1st sample
     */
    public FixedHeader(StringBuilder name, double rt, int nFrames, int yr, int dy, int sec, int micros,
            int startSeq) {
      // rearrange the seed name to internal format
      Util.clear(seedname).append(name);
      while (seedname.length() < 12) {
        seedname.append(" ");
      }
      rate = rt;
      activity = 0;
      IOClock = 0;
      quality = 0;
      timingQuality = 0;
      startSequence = startSeq;
      setRate(rate);

      if (name.substring(0, 5).equals("XXMPR")) {
        Util.prt("set rate rate=" + rate + " fact=" + rateFactor + " mult=" + rateMultiplier);
      }
      numFrames = nFrames;
      sequence = startSequence;
      year = (short) yr;
      doy = (short) dy;
      time = sec * 1000000L + micros;
    }

    /**
     * return the bytes of this Fixed header in a 64 byte array. Since a ByteBuffer is used to put
     * data into the buffer, the data are in BIG ENDIAN order.
     *
     * @return The 64 bytes with data for the 48 byte header
     */
    public byte[] getBytes() {
      byte[] b = new byte[64];
      Arrays.fill(b, (byte) 0);
      ByteBuffer buf = ByteBuffer.wrap(b);
      buf.clear();
      buf.put(Util.df6(sequence).substring(0, 6).getBytes());
      buf.put((byte) 'D');
      buf.put((byte) ' ');
      // store name in seed manual order (pg 92 Chap 8 on fixed header)
      buf.put((seedname.substring(2, 7) + seedname.substring(10, 12)
              + seedname.substring(7, 10) + seedname.substring(0, 2)).getBytes());
      buf.putShort(year).putShort(doy);
      long t = time;
      buf.put((byte) (t / 3600000000L));
      t = time % 3600000000L;
      buf.put((byte) (t / 60000000L));
      t = t % 60000000L;
      buf.put((byte) (t / 1000000L));
      t = t % 1000000L;
      buf.put((byte) 0).putShort((short) (t / 100));
      buf.putShort(nsamp);
      buf.putShort(rateFactor);
      buf.putShort(rateMultiplier);
      buf.put(activity).put(IOClock).put(quality).put((byte) 2);// one block follows
      buf.putInt(0);
      buf.putShort((short) 64).putShort((short) 48);

      // This is the blockette 1000 (next block) encoding Steim2==11, Word order 1 (big endian)
      buf.putShort((short) 1000).putShort((short) 56).put((byte) 11).put((byte) 1);
      if (numFrames == 7) {
        buf.put((byte) 9);
      } else if (numFrames == 63) {
        buf.put((byte) 12);
      }
      buf.put((byte) 0);    // reserved 1000
      // blockette 1001 ,  next blockette (none),  timing quality, usecs
      buf.putShort((short) 1001).putShort((short) 0).put((byte) timingQuality).put((byte) (time % 100));
      // reserved, number of blockettes
      buf.put((byte) 0).put((byte) actualFrames);

      return buf.array();
    }

    /**
     * return # of samples in data represented by this header
     *
     * @return the # of samples in data represented by this header
     */
    public int getNSamp() {
      return (int) nsamp;
    }

    /**
     * set number of samples represented by this header
     *
     * @param n The number of samples to which will be represented by this header
     */
    public void setNSamp(int n) {
      nsamp = (short) n;
    }

    /**
     * set the digitizing rate
     *
     * @param rate The rate in Hz
     */
    public final void setRate(double rate) {
      // Convert Rate to integer scheme used by seed
      if (rate < 1.) {
        double r = 1. / rate;                           // period in seconds
        if (Math.abs(r - Math.round(r)) < 0.0001) {    // is it an even period in seconds
          rateFactor = (short) Math.round(-r);            // yes, set it neg for period mult 1
          rateMultiplier = (short) 1;
        } else {
          rateMultiplier = 1;                    // scale period to  fit short, set mult to multiplier
          while (r < 3275) {
            r = r * 10;
            rateMultiplier = (short) (rateMultiplier * 10);
          }
          rateFactor = (short) Math.round(-r);    // Rate is in period so its in denominator
        }
      } // Its > 1, so do hz
      else {
        // check to see if this is an integer, if so, the GSN prefers multipliers of 1
        if (Math.abs(rate - Math.round(rate)) < 0.00001) {
          rateFactor = (short) Math.round(rate);
          rateMultiplier = (short) 1;
        } else {
          double r = rate;   // it has a fractional part so scale it up and add divisor
          rateMultiplier = -1;  // Its a divisor so its negative
          while (r < 3275) {
            r = r * 10.;
            rateMultiplier = (short) (rateMultiplier * 10);
          }
          rateFactor = (short) Math.round(r);
        }
      }
      //Util.prta("rate="+rate+" rateFact="+rateFactor+" rateMult="+rateMultiplier);
    }

    /**
     * set the activity flags
     *
     * @param a The activity flags per the SEED documentation
     */
    public void setActivity(int a) {
      activity = (byte) a;
    }

    /**
     * set the quality flags
     *
     * @param a The Quality flags as per the SEED documentation
     */
    public void setQuality(int a) {
      quality = (byte) a;
    }

    /**
     * set the I/O and Clock flags
     *
     * @param a The I/O and clock flags per the SEED docs
     */
    public void setIOClock(int a) {
      IOClock = (byte) a;
    }

    /**
     * set timing quality
     *
     * @param the timing quality
     */
    public void setTimingQuality(int i) {
      timingQuality = (byte) i;
    }

    /**
     * public void set the number of frames in this header
     *
     * @param the actual number of frames *
     */
    public void setActualNFrames(int nf) {
      actualFrames = nf;
    }

    /**
     * set the start time of the first sample
     *
     * @param yr Year of first sample in this Mini-seed blockette
     * @param dy Day-of-year of 1st sample
     * @param sec integral # of seconds since midnight
     * @param micros Fractional Microseconds of 1st sample
     */
    public void setStartTime(int yr, int dy, int sec, long micros) {
      year = (short) yr;
      doy = (short) dy;
      time = sec * 1000000L + micros;
    }
  }

  /**
   * This class makes it easier to represent Steim Data frames of 64 bytes each for implementing the
   * compressor. It basically allows putting and getting 4 bytes at a time to the encapsulated
   * arrays. No matter what kind of computer we are on, this data is written in high endian order
   */
  class SteimFrames {

    byte[] w;

    public SteimFrames() {
      w = new byte[64];
    }

    /**
     * put the int i in the steim frame at index
     */
    public void put(int i, int index) {
      if (index >= 16) {
        prta("Index is out of range in steimFrame PUT" + index);
      }
      for (int j = 0; j < 4; j++) {
        w[index * 4 + 3 - j] = (byte) (i & 0xFF);
        i = i >> 8;
      }
    }

    /**
     * get the int from the index offset of the Steim Frame
     */
    public int get(int index) {
      int i = 0;
      for (int j = 0; j < 4; j++) {
        i = i << 8;
        i = i | (((int) w[index * 4 + j]) & 0xff);
      }
      return i;
    }

    /**
     * return all 64 bytes represented by this Steim Frame
     */
    public byte[] getBytes() {
      return w;
    }
  }

}
