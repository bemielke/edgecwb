/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.seed;

import gov.usgs.anss.edge.RawToMiniSeed;
import gov.usgs.anss.edge.MasterBlock;
import gov.usgs.anss.edge.IndexFile;
import gov.usgs.alarm.SendEvent;
import gov.usgs.anss.edge.TimeSeriesBlock;
import gov.usgs.anss.edge.IllegalSeednameException;
import gov.usgs.anss.edge.MiniSeedOutputHandler;
import gov.usgs.anss.edge.RawDisk;

//import gov.usgs.anss.edge.*;
import gov.usgs.anss.edgethread.EdgeThread;
import gov.usgs.anss.util.SeedUtil;
import gov.usgs.anss.util.Util;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Calendar;
import java.util.GregorianCalendar;

/**
 * This class represents a mini-seed packet. It can translate binary data in a
 * byte array and break apart the fixed data header and other data blockettes
 * and represent them as separate internal structures.
 *
 * @author davidketchum
 */
public class MiniSeed extends TimeSeriesBlock implements MiniSeedOutputHandler {

  public final static int ACTIVITY_CAL_ON = 1;
  public final static int ACTIVITY_TIME_CORRECTION_APPLIED = 2;
  public final static int ACTIVITY_BEGIN_EVENT = 4;
  public final static int ACTIVITY_END_EVENT = 8;
  public final static int ACTIVITY_POSITIVE_LEAP = 16;
  public final static int ACTIVITY_NEGATIVE_LEAP = 32;
  public final static int ACTIVITY_EVENT_IN_PROGRESS = 64;
  public final static int IOCLOCK_PARITY_ERROR = 1;
  public final static int IOCLOCK_LONG_RECORD = 2;
  public final static int IOCLOCK_SHORT_RECORD = 4;
  public final static int IOCLOCK_START_SERIES = 8;
  public final static int IOCLOCK_END_SERIES = 16;
  public final static int IOCLOCK_LOCKED = 32;
  public final static int QUALITY_AMP_SATURATED = 1;
  public final static int QUALITY_CLIPPED = 2;
  public final static int QUALITY_SPIKES = 4;
  public final static int QUALITY_GLITCHES = 8;
  public final static int QUALITY_MISSING_DATA = 16;
  public final static int QUALITY_TELEMETRY_ERROR = 32;
  public final static int QUALITY_CHARGING = 64;
  public final static int QUALITY_QUESTIONABLE_TIME = 128;
  public final static int julian1970 = SeedUtil.toJulian(1970,1);
  public static boolean strict = true;
  private static boolean dbgCreate = false;
  private static final byte[] btmp = staticInitBTMP(); // This is synchronized by all who wish to use it crack*()
  private static ByteBuffer bbtmp;

  static byte[] staticInitBTMP() {
    byte[] tmp = new byte[4096];
    bbtmp = ByteBuffer.wrap(tmp);
    return tmp;
  }   // static play space
  private ByteBuffer ms;
  private byte[] buf;              // our copy of the input data wrapped by ms
  private boolean cracked;
  private boolean cleared;          // This one was last cleared
  private int length;
  //private static DecimalFormat int5;
  private static int recordCount; // COunter as MiniSeed records are created.
  private static long nmsload;
  private static long nbufload;
  private static long nnew;
  private static long startUp;
  private final int recordNumber;       // The serial number assigned this record at creation (is unique for all MiniSeed on one process)
  private EdgeThread parent;

  // components filled out by the crack() routine
  private byte[] seed;             // The 12 charname in fixed header order SSSSSLLCCCNN
  StringBuilder seedname = new StringBuilder(12);           // The 12 character seedname in NNSSSSSCCLLL
  private String seednameString;
  private byte[] seq;              // 6 character with ascii of sequence
  private byte[] indicator;        // two character indicator normally "D " or "Q "
  private byte[] startTime;        // Bytes with raw fixed header time
  private ByteBuffer timebuf;
  private short nsamp;              // Number of samples
  private short rateFactor;         // Rate factor from fixed header
  private short rateMultiplier;     // Rate multiplier from fixed header
  private double rate = -1.;              // This is derived from the rateFactor and rateMultiplier
  private double b100Rate = -1.;
  private byte activityFlags;       // activity flags byte from fixed header
  private byte ioClockFlags;        // iod flags from fixed header
  private byte dataQualityFlags;    // Data quality flags from fixed header
  private byte nblockettes;         // number of "data blockettes" in this record
  private int timeCorrection;       // Time Correction from fixed header
  private short dataOffset;         // Offset in buffer of first byte of data
  private short blocketteOffset;    // Offset in bytes to first byte of first data blockette
  private boolean hasBlk1000;       // derived flag on whether a blockette 1000 was decoded
  private boolean hasBlk1001;       // derived flag on whether a blockette 1001 was decoded
  private short year, day, husec;     // Portions of time broken out from fixed header
  private byte hour, minute, sec;     // The byte portions of the time from fixed header
  //private GregorianCalendar time;   // This is the Java Gregorian representation of the time
  //private GregorianCalendar timeTruncated;// This does not round the ms
  private long time;                // This is the Java time representation of the time
  private long timeTruncated;       // This does not round the millis
  private static final GregorianCalendar globalg = new GregorianCalendar(); // Everything needs to synchronize on this if using it
  private static final GregorianCalendar globalg2 = new GregorianCalendar(); // Everything needs to synchronize on this if using it
  private static int[] ymd = new int[3];// synchronized with globalg 
  private int julian;               // julian day from year and doy
  private int forward;              // forward integration constart (from first frame)
  private int reverse;              // reverse or ending integeration constant from end

  // These contain information about the "data blockettes" really meta-data
  private ByteBuffer[] blockettes;   // these wrap the bufnnn below for each blockette found
  // in same order as the blocketteList
  private int[] blocketteOffsets;
  private short[] blocketteList;     // List of the blockett types found
  private byte[] buf100;             // These bufnnn contain data from the various
  private byte[] buf200;             // possible "data blockettes" found. They are
  private byte[] buf201;             // never all defined.
  private byte[] buf300;
  private byte[] buf310;
  private byte[] buf320;
  private byte[] buf390;
  private byte[] buf395;
  private byte[] buf400;
  private byte[] buf405;
  private byte[] buf500;
  private byte[] buf1000;
  private byte[] buf1001;
  private ByteBuffer bb100;             // These bufnnn contain data from the various
  private ByteBuffer bb200;             // possible "data blockettes" found. They are
  private ByteBuffer bb201;             // never all defined.
  private ByteBuffer bb300;
  private ByteBuffer bb310;
  private ByteBuffer bb320;
  private ByteBuffer bb390;
  private ByteBuffer bb395;
  private ByteBuffer bb400;
  private ByteBuffer bb405;
  private ByteBuffer bb500;
  private ByteBuffer bb1000;
  private ByteBuffer bb1001;
  private Blockette1000 b1000;
  private Blockette1001 b1001;
  //private Blockette2000 b2000;

  // Decomp stuff
  byte[] frames;

  // Data we need from the type 1000 and 1001
  private byte order;         // 0=little endian, 1 = big endian
  private boolean swap;            // If set, this MiniSeed needs to be swapped.
  private int recLength;      // in bytes
  private byte encoding;      // 10=Steim1, 11=Steim2, 15=NSN
  private byte timingQuality;   // 1001 - from 0 to 100 %
  private byte microSecOffset;  // offset from the 100 of USecond in time code
  private byte nframes;         // in compressed data (Steim method only)

  private static boolean dbg = false;

  public static void setDebugCreate(boolean b) {
    dbgCreate = b;
  }

  public static void setStrict(boolean b) {
    strict = b;
  }

  /**
   * If this mini-seed block is a duplicate of the one passed, return true
   *
   * @param ms The mini-seed block for comparison
   * @return true if the blocks have the same time, data rate, number of
   * samples, and compression payload
   *
   */
  public boolean isDuplicate(MiniSeed ms) {
    //crack();
    if (ms == null) {
      Util.prt("Null");
      return false;
    }

    if (Math.abs(time - ms.getTimeInMillis()) < 500. / getRate()
            && getNsamp() == ms.getNsamp() && buf.length == ms.getBuf().length) {
      byte[] obuf = ms.getBuf();
      for (int i = 64; i < buf.length; i++) {
        if (buf[i] != obuf[i]) {
          return false;
        }
      }
      return true;
    }
    return false;
  }

  @Override
  public void clear() {
    cracked = false;
    for (int i = 0; i < buf.length; i++) {
      buf[i] = 0;
    }
    for (int i = 0; i < 12; i++) {
      seed[i] = 'Z';
    }

    cleared = true;
  }

  public boolean hasBlk1000() {
    crack();
    return hasBlk1000;
  }

  /**
   * if true, this MiniSeed object is cleared and presumably available for reuse
   *
   * @return true if this is a cleared object - no data in it
   */
  @Override
  public boolean isClear() {
    return cleared;
  }

  /**
   * set the debug output flag
   *
   * @param t True for lots of output
   */
  public static void setDebug(boolean t) {
    dbg = t;
  }
  /** Given a julian day and time, get the milliseconds since 1970
   * 
   * @param julian
   * @param hr
   * @param min
   * @param sec
   * @param husec
   * @return 
   */
  public static long getTimeFromJulian(int julian, int hr, int min, int sec, int husec) {
    return (julian - julian1970)*86400000L + hr * 3600000L + min * 60000L + sec * 1000L + (husec + 5)/10;
  }
  /**
   * set time to given millis since 1970
   *
   * @param millis The time to set
   * @param hund The hundreds of microseconds to set (adds to Millis in )
   */
  public final void setTime(long millis, int hund) {
    synchronized (globalg) {
      globalg.setTimeInMillis(millis);
      setTime(globalg, hund);
    }
  }

  /**
   * set time to given GregorianCalendar
   *
   * @param g The time to set
   * @param hund The hundreds of microseconds to set (adds to Millis in
   * Gregorian Calendar)
   */
  public final void setTime(GregorianCalendar g, int hund) {
    year = (short) g.get(Calendar.YEAR);
    day = (short) g.get(Calendar.DAY_OF_YEAR);
    hour = (byte) g.get(Calendar.HOUR_OF_DAY);
    minute = (byte) g.get(Calendar.MINUTE);
    sec = (byte) g.get(Calendar.SECOND);
    husec = (short) (g.get(Calendar.MILLISECOND) * 10 + hund);
    julian = SeedUtil.toJulian(year, day);
    ms.position(20);
    ms.putShort(year);
    ms.putShort(day);
    ms.put(hour);
    ms.put(minute);
    ms.put(sec);
    ms.put((byte) 0);
    ms.putShort(husec);
    ms.position(20);
    ms.get(startTime);
    // Note : jan 1, 1970 is time zero so 1 must be subtracted from the day
    long millis = getTimeFromJulian(julian, hour, minute, sec, 0);
    time = millis + (husec + 5) / 10;
    timeTruncated = millis + husec / 10;
    chkTime();

  }

  /**
   * ms set the number of samples. Normally used to truncate a time series say
   * to make it just long enough to fill the day!
   *
   * @param ns The number of samples
   */
  public void setNsamp(int ns) {
    nsamp = (short) ns;
    ms.position(30);
    ms.putShort(nsamp);

  }

  public void setLocationCode(String newCode) {
    seed[5] = (byte) newCode.charAt(0);
    seed[6] = (byte) newCode.charAt(1);
    buf[13] = (byte) newCode.charAt(0);
    buf[14] = (byte) newCode.charAt(1);
  }

  public void fixLocationCode() {
    if (seed[5] != ' ' && !Character.isUpperCase(seed[5]) && !Character.isDigit(seed[5])) {
      seed[5] = ' ';
    }
    if (seed[6] != ' ' && !Character.isUpperCase(seed[6]) && !Character.isDigit(seed[6])) {
      seed[6] = ' ';
    }
  }

  public void fixHusecondsQ330() {
    if (husec % 10 == 0) {
      if ((husec / 10) % 10 == 4 || (husec / 10) % 10 == 9) {
        //Util.prt("Fix ms="+toString());
        husec += 9;
        //ByteBuffer timebuf = ByteBuffer.wrap(startTime);
        if (swap) {
          timebuf.order(ByteOrder.LITTLE_ENDIAN);
        }
        timebuf.position(8);    // fix it in the startTime buffer
        timebuf.putShort(husec);
        ms.position(28);
        ms.putShort(husec);        // fix it in the main buffer
        //Util.prt("aft ms="+toString());
        //time.add(Calendar.MILLISECOND, 1);  // add the extra millisecond to the gregorian timeCorrection
      }
    }
    if (husec == 9 && sec == 0 && minute == 0 && hour == 0) {
      husec = 0;
    }
  }

  public void prt(String s) {
    if (parent == null) {
      Util.prt(s);
    } else {
      parent.prt(s);
    }
  }

  public void prta(String s) {
    if (parent == null) {
      Util.prta(s);
    } else {
      parent.prta(s);
    }
  }

  public void setEdgeThreadParent(EdgeThread e) {
    parent = e;
  }

  public int getRecordNumber() {
    return recordNumber;
  }

  /**
   * Creates a new instance of MiniSeed
   *
   * @param inbuf An array of binary miniseed data
   * @throws IllegalSeednameException if the name does not pass muster
   */
  public MiniSeed(byte[] inbuf) throws IllegalSeednameException {
    buf = new byte[inbuf.length];
    System.arraycopy(inbuf, 0, buf, 0, inbuf.length);
    ms = ByteBuffer.wrap(buf);
    blockettes = new ByteBuffer[4];
    blocketteList = new short[4];
    blocketteOffsets = new int[4];
    nnew++;
    init();   // init will set swapping of ms
    recordNumber = recordCount++;
    // Debug code :
    if (System.currentTimeMillis() - startUp > 900000 && nnew % 500 == 0 && nnew > 100000 && dbgCreate) {
      new RuntimeException(Util.asctime() + " INFO : MiniSeed new1 nnew=" + nnew + " " + seedname).printStackTrace();
    }
  }
  public MiniSeed(String seedname, double rate, int nsamp, long time, int hund) throws IllegalSeednameException {
    buf = new byte[512];
    blockettes = new ByteBuffer[4];
    blocketteList = new short[4];
    blocketteOffsets = new int[4];
    ms = ByteBuffer.wrap(buf);
    ms.put("000000D ".getBytes());
    ms.put(seedname.substring(2,7).getBytes());
    ms.put(seedname.substring(10,12).getBytes());
    ms.put(seedname.substring(7,10).getBytes());
    ms.put(seedname.substring(0,2).getBytes());
    ms.putShort((short) 2017);
    ms.putShort((short) 1);
    ms.position(30);
    ms.putShort((short) nsamp);
    
    init();
    setTime(time, hund);
    
    setRate(rate);
    nnew++;
    recordNumber = recordCount++;
  }
  /**
   * Creates a new instance of MiniSeed
   *
   * @param inbuf An array of binary miniseed data
   * @param off the offset into inbuf to start
   * @param len The length of the inbuf to convert (the payload length)
   * @throws IllegalSeednameException if the name does not pass muster
   */
  public MiniSeed(byte[] inbuf, int off, int len) throws IllegalSeednameException {
    buf = new byte[len];
    System.arraycopy(inbuf, off, buf, 0, len);
    ms = ByteBuffer.wrap(buf);
    blockettes = new ByteBuffer[4];
    blocketteList = new short[4];
    blocketteOffsets = new int[4];
    nnew++;
    init();   // init will set swapping of ms
    recordNumber = recordCount++;
    // Debug code :
    if (System.currentTimeMillis() - startUp > 900000 && nnew % 500 == 0 && nnew > 100000 && dbgCreate) {
      new RuntimeException(Util.asctime() + " INFO : MiniSeed new2 nnew=" + nnew + " " + seedname).printStackTrace();
    }
  }

  @Override
  public void load(byte[] inbuf) throws IllegalSeednameException {
    nbufload++;
    if (inbuf.length != buf.length) {
      //Util.prt("MiniSeed.load() change buffer length from "+buf.length+" to "+inbuf.length);
      buf = new byte[inbuf.length];
      ms = ByteBuffer.wrap(buf);      // order will be set by init()
    }
    System.arraycopy(inbuf, 0, buf, 0, inbuf.length);
    init();
  }

  public void load(byte[] inbuf, int off, int len) throws IllegalSeednameException {
    nbufload++;
    if (buf.length != len) {
      //Util.prt("MiniSeed.load : change buffer length from "+buf.length+" to "+len);
      buf = new byte[len];
      ms = ByteBuffer.wrap(buf);      // order will be set by init()
    }
    System.arraycopy(inbuf, off, buf, 0, len);
    init();
  }

  /**
   * load a MiniSEED object from another one
   *
   * @param msin An existing MiniSeed object
   * @throws IllegalSeednameException Should not happen since original MiniSeed
   * object must have passed!
   */
  public void load(MiniSeed msin) throws IllegalSeednameException {
    if (msin.getBuf().length == buf.length) {
      nmsload++;
      System.arraycopy(msin.getBuf(), 0, buf, 0, buf.length); // copy the array
      cracked = false;
      cleared = false;
      encoding = 0;
      recLength = buf.length;
      order = -100;
      nframes = 0;
      microSecOffset = 0;
      timingQuality = 101;
      if (isHeartBeat()) {
        return;
      }
      swap = swapNeeded(buf, ms);
      if (swap) {
        ms.order(ByteOrder.LITTLE_ENDIAN);
      }
      if (seedname.length() > 0) {
        seedname.delete(0, seedname.length());
      }
      seedname.append(msin.seedname);
      System.arraycopy(msin.seed, 0, seed, 0, seed.length);
      System.arraycopy(msin.indicator, 0, indicator, 0, indicator.length);
      System.arraycopy(msin.startTime, 0, startTime, 0, startTime.length);
      System.arraycopy(msin.seed, 0, seed, 0, seed.length);
      seednameString = null;
      nsamp = msin.nsamp;
      rateFactor = msin.rateFactor;
      rateMultiplier = msin.rateMultiplier;
      rate = msin.rate;
      activityFlags = msin.activityFlags;
      ioClockFlags = msin.ioClockFlags;
      dataQualityFlags = msin.dataQualityFlags;
      nblockettes = msin.nblockettes;
      timeCorrection = msin.timeCorrection;
      dataOffset = msin.dataOffset;
      blocketteOffset = msin.blocketteOffset;
      ms.position(68);
      forward = msin.forward;
      reverse = msin.reverse;
      if (swap) {
        timebuf.order(ByteOrder.LITTLE_ENDIAN);
      }
      year = msin.year;
      day = msin.day;
      hour = msin.hour;
      minute = msin.minute;
      sec = msin.sec;
      husec = msin.husec;
      julian = msin.julian;
      time = msin.time;
      timeTruncated = msin.timeTruncated;
      chkTime();
      if(time < 0) {
        Util.prt("*** Neg time to load(MS) "+getSeedNameString()+" ="+time+" "+year+","+day+" "+hour+":"+minute+":"+sec+"."+husec);
      }    
    } else {
      load(msin.getBuf(), 0, msin.getBlockSize());
    }   
  }

  private void init() throws IllegalSeednameException {
    length = buf.length;
    cracked = false;
    cleared = false;
    encoding = 0;
    recLength = buf.length;   // this will be overridden by blockette 1000 if present
    order = -100;
    nframes = 0;
    microSecOffset = 0;
    timingQuality = 101;
    //if(int5 == null) int5 = new DecimalFormat("00000");
    if (seed == null) {
      seed = new byte[12];
      if (startUp == 0) {
        startUp = System.currentTimeMillis();
      }
    }
    if (seq == null) {
      seq = new byte[6];
    }
    if (indicator == null) {
      indicator = new byte[2];
    }
    if (startTime == null) {
      startTime = new byte[10];
      timebuf = ByteBuffer.wrap(startTime);
    }
    if (isHeartBeat()) {
      return;
    }
    swap = swapNeeded(buf, ms);
    if (swap) {
      ms.order(ByteOrder.LITTLE_ENDIAN);
    }

    // crack the seed name so we can check its legality
    ms.clear();
    ms.get(seq).get(indicator).get(seed).get(startTime);

    // s.substring(10,12)+s.substring(0,5)+s.substring(7,10)+s.substring(5,7);
    Util.clear(seedname).append((char) seed[10]).append((char) seed[11]).
            append((char) seed[0]).append((char) seed[1]).append((char) seed[2]).append((char) seed[3]).append((char) seed[4]).
            append((char) seed[7]).append((char) seed[8]).append((char) seed[9]).append((char) seed[5]).append((char) seed[6]);
    seednameString = null;
    try {
      MasterBlock.checkSeedName(seedname);
    } catch (IllegalSeednameException e) {
      if (strict) {
        throw e;
      }
    }
    nsamp = ms.getShort();
    rateFactor = ms.getShort();
    rate = -1.;
    b100Rate = -1.;
    // Since MiniSeed blocks are reused via Load, clear any of the blockettes from previous loads
    if (buf100 != null) {
      Arrays.fill(buf100, (byte) 0);
    }
    if (buf200 != null) {
      Arrays.fill(buf200, (byte) 0);             // possible "data blockettes" found. They are
    }
    if (buf201 != null) {
      Arrays.fill(buf201, (byte) 0);             // never all defined.
    }
    if (buf300 != null) {
      Arrays.fill(buf300, (byte) 0);
    }
    if (buf310 != null) {
      Arrays.fill(buf310, (byte) 0);
    }
    if (buf320 != null) {
      Arrays.fill(buf320, (byte) 0);
    }
    if (buf390 != null) {
      Arrays.fill(buf390, (byte) 0);
    }
    if (buf395 != null) {
      Arrays.fill(buf395, (byte) 0);
    }
    if (buf400 != null) {
      Arrays.fill(buf400, (byte) 0);
    }
    if (buf500 != null) {
      Arrays.fill(buf500, (byte) 0);
    }
    if (buf1000 != null) {
      Arrays.fill(buf1000, (byte) 0);
      b1000.reload(buf1000);
    }
    if (buf1001 != null) {
      Arrays.fill(buf1001, (byte) 0);
      b1001.reload(buf1001);
    }
    rateMultiplier = ms.getShort();
    activityFlags = ms.get();
    ioClockFlags = ms.get();
    dataQualityFlags = ms.get();
    nblockettes = ms.get();
    timeCorrection = ms.getInt();
    dataOffset = ms.getShort();
    blocketteOffset = ms.getShort();
    ms.position(68);
    forward = ms.getInt();
    reverse = ms.getInt();
    fixLocationCode();

    // Get the time code
    if (swap) {
      timebuf.order(ByteOrder.LITTLE_ENDIAN);
    }
    timebuf.position(0);
    year = timebuf.getShort();
    day = timebuf.getShort();
    hour = timebuf.get();
    minute = timebuf.get();
    sec = timebuf.get();
    timebuf.get();
    husec = timebuf.getShort();
    julian = SeedUtil.toJulian(year, day);

    // Note : jan 1, 1970 is time zero so 1 must be subtracted from the day
    long millis;
    long millis2;
    synchronized (globalg) {
      SeedUtil.ymd_from_doy(year, day, ymd);    // Note this is static so it needs to be synchronized
      globalg.set(ymd[0], ymd[1] - 1, ymd[2], hour, minute, sec);
      millis = globalg.getTimeInMillis() / 1000L * 1000L;
      if(millis < 0) {
        globalg2.set(ymd[0], ymd[1] - 1, ymd[2], hour, minute, sec);
        millis2 = globalg2.getTimeInMillis() /1000l * 1000L;
        Util.prt("*** Neg time to init() gc "+getSeedNameString()+" ="+millis+" "+year+","+day+" "+hour+":"+minute+":"+sec+"."+husec+
              " g="+Util.ascdatetime(globalg)+" g2="+globalg2.getTimeInMillis()+" "+Util.ascdatetime(globalg2));
      }
    }
    millis = getTimeFromJulian(julian, hour, minute,sec, 0);    // compute epoch time from julian day
    time = millis + (husec + 5) / 10;
    timeTruncated = millis + husec / 10;
    if(time < 0) {
      Util.prt("*** Neg time to init() "+getSeedNameString()+" ="+millis+" jul=:"+julian+" "
              +year+","+day+" "+hour+":"+minute+":"+sec+"."+husec+
              " jultim="+Util.ascdatetime(time)+" "+time);
    }
    chkTime();
    if (swap && dbg) {
      Util.prt("   *************Swap is needed! ************* " + getSeedNameString());
    }
  }

  public static boolean crackIsHeartBeat(byte[] buf) {
    boolean is = true;
    for (int i = 0; i < 6; i++) {
      if (buf[i] != 48 || buf[i + 6] != 32 || buf[i + 12] != 32) {
        is = false;
        break;
      }
    }
    return is;
  }

  private void chkTime() {
    if (year < 2000 || year > 2030 || day <= 0 || day > 366 || hour < 0 || hour > 23 || minute < 0 || minute > 59
            || sec < 0 || sec > 59 || husec < 0 || husec > 9999 || time < 0 ) {
      String s="MiniSeed.chkTime() bad time code +"+ seedname+" yr=" + year +
             " day=" + day + " " + hour + ":" + minute + ":" + sec + "." + Util.df4(husec)+" ms="+time;
      Util.prt(s);
      SendEvent.debugEvent("MSBadDate",s, "Util");
    }
  }
  /**
   * Is this mini-seed a heart beat. These packets have all zero sequence # and
   * all spaces in the net/station/location/channel
   *
   * @return true if sequences is all zero and first 12 chars are blanks
   */
  public boolean isHeartBeat() {
    //boolean is = true;
    for (int i = 0; i < 6; i++) {
      if (buf[i] != 48 || buf[i + 6] != 32 || buf[i + 12] != 32) {
        return false;
      }
    }
    return true;
  }

  /**
   * This returns the Julian data from a raw miniseed buffer in buf. This
   * routine would be used to extract a bit of data from a raw buffer without
   * going to the full effort of creating a MiniSeed object from it.
   *
   * @param buf A array with a miniseed block in raw form
   * @return The Julian day
   * @throws IllegalSeednameException if this is clearly not a miniseed buffer
   */
  public static int crackJulian(byte[] buf) throws IllegalSeednameException {
    short year;
    short day;
    synchronized (btmp) {
      System.arraycopy(buf, 0, btmp, 0, 128);
      if (swapNeeded(buf)) {
        bbtmp.order(ByteOrder.LITTLE_ENDIAN);
      }
      bbtmp.position(20);
      year = bbtmp.getShort();
      day = bbtmp.getShort();
      if (day <= 0 || day > 366) {
        Util.prt("MiniSeed.crackJulian bad day " + MiniSeed.crackSeedname(buf)
                + " yr=" + year + " day=" + day);
      }
      if (year <= 1970 || year > 2030) {
        Util.prt("MiniSeed.crackJulian bad year " + MiniSeed.crackSeedname(buf)
                + " yr=" + year + " day=" + day);
      }
      //short hour = bb.get();
      //short minute = bb.get();
      //short sec = bb.get();
      //bb.get();
      //husec=bb.getShort();
    }
    return SeedUtil.toJulian(year, day);
  }

  /**
   * get the time in Millis of the first sample from a miniseed header
   *
   * @param buf4096
   * @return
   * @throws IllegalSeednameException
   */
  public static long crackTimeInMillis(byte[] buf4096) throws IllegalSeednameException {
    int[] time = MiniSeed.crackTime(buf4096);  // get hour, minute, second, hsec
    if (time[0] >= 24 || time[1] >= 60 || time[2] >= 60) {
      new RuntimeException("Bad Time=" + MiniSeed.toStringRaw(buf4096)).printStackTrace();
    }
    int[] ymdtmp = SeedUtil.fromJulian(MiniSeed.crackJulian(buf4096));
    int day = SeedUtil.doy_from_ymd(ymdtmp);
    // Note : jan 1, 1970 is time zero so 1 must be subtracted from the day
    long millis = (ymdtmp[0] - 1970) * 365L * 86400000L + (day - 1) * 86400000L + ((long) time[0]) * 3600000L
            + ((long) time[1]) * 60000L + ((long) time[2]) * 1000L;
    millis += ((ymdtmp[0] - 1969) / 4) * 86400000L;      // Leap years past but not this one!
    return millis;
  }

  /**
   * This returns the time data as a 4 element array with hour,minute, sec, and
   * hsec from a raw miniseed buffer in buf. This routine would be used to
   * extract a bit of data from a raw buffer without going to the full effort of
   * creating a MiniSeed object from it.
   *
   * @param buf A array with a miniseed block in raw form
   * @return The time in a 4 integer array
   * @throws IllegalSeednameException if the buf is clearly not miniseed
   */
  public static int[] crackTime(byte[] buf) throws IllegalSeednameException {
    int[] time = new int[4];
    synchronized (btmp) {
      System.arraycopy(buf, 0, btmp, 0, 128);
      if (swapNeeded(buf, bbtmp)) {
        bbtmp.order(ByteOrder.LITTLE_ENDIAN);
      }
      bbtmp.position(20);
      short year = bbtmp.getShort();
      short day = bbtmp.getShort();
      time[0] = bbtmp.get();   // hour
      time[1] = bbtmp.get();   // minute
      time[2] = bbtmp.get();
      bbtmp.get();
      time[3] = bbtmp.getShort();
    }
    return time;
  }

  /**
   * This returns the time data as a 4 element array with hour,minute, sec, and
   * hsec from a raw miniseed buffer in buf. This routine would be used to
   * extract a bit of data from a raw buffer without going to the full effort of
   * creating a MiniSeed object from it.
   *
   * @param buf A array with a miniseed block in raw form
   * @param time The 4 element array to return the time in
   * @return The time in a 4 integer array
   * @throws IllegalSeednameException if the buf is clearly not miniseed
   */
  public static int[] crackTime(byte[] buf, int[] time) throws IllegalSeednameException {
    synchronized (btmp) {
      System.arraycopy(buf, 0, btmp, 0, 128);
      if (swapNeeded(buf, bbtmp)) {
        bbtmp.order(ByteOrder.LITTLE_ENDIAN);
      }
      bbtmp.position(20);
      short year = bbtmp.getShort();
      short day = bbtmp.getShort();
      time[0] = bbtmp.get();   // hour
      time[1] = bbtmp.get();   // minute
      time[2] = bbtmp.get();
      bbtmp.get();
      time[3] = bbtmp.getShort();
    }
    return time;
  }

  /**
   * Return the year from an uncracked miniseedbuf
   *
   * @param buf Buffer with miniseed header
   * @return The year
   * @throws IllegalSeednameException if the buffer clearly is not mini-seed
   */
  public static int crackYear(byte[] buf) throws IllegalSeednameException {
    synchronized (btmp) {
      System.arraycopy(buf, 0, btmp, 0, 128);
      if (swapNeeded(buf, bbtmp)) {
        bbtmp.order(ByteOrder.LITTLE_ENDIAN);
      }
      bbtmp.position(20);
      return (int) bbtmp.getShort();
    }
  }

  /**
   * Return the day of year from an uncracked miniseedbuf
   *
   * @param buf Buffer with miniseed header
   * @return The day of year
   * @throws IllegalSeednameException if the buffer clearly is not mini-seed
   *
   */
  public static int crackDOY(byte[] buf) throws IllegalSeednameException {
    int doy;
    synchronized (btmp) {
      System.arraycopy(buf, 0, btmp, 0, 128);
      if (swapNeeded(buf, bbtmp)) {
        bbtmp.order(ByteOrder.LITTLE_ENDIAN);
      }
      bbtmp.position(22);
      doy = (int) bbtmp.getShort();
    }
    return doy;
  }

  /**
   * This returns the number of samples of data from a raw miniseed buffer in
   * buf. This routine would be used to extract a bit of data from a raw buffer
   * without going to the full effort of creating a MiniSeed object from it.
   *
   * @param buf A array with a miniseed block in raw form
   * @return The number of samples
   * @throws IllegalSeednameException If the buffer is not miniseed
   */
  public static int crackNsamp(byte[] buf) throws IllegalSeednameException {
    int nsamp;
    synchronized (btmp) {
      System.arraycopy(buf, 0, btmp, 0, 128);
      if (swapNeeded(buf, bbtmp)) {
        bbtmp.order(ByteOrder.LITTLE_ENDIAN);
      }
      bbtmp.position(30);
      nsamp = (int) bbtmp.getShort();
    }
    return nsamp;
  }

  /**
   * This returns the digitizing rate from a raw miniseed buffer in buf. This
   * routine would be used to extract a bit of data from a raw buffer without
   * going to the full effort of creating a MiniSeed object from it.
   *
   * @param buf A array with a miniseed block in raw form
   * @return The digitizing rate as a double. 0. if the block factor and
   * multipler are invalid.
   * @throws IllegalSeednameException if the buffer clearly is not mini-seed
   */
  public static double crackRate(byte[] buf) throws IllegalSeednameException {
    double rate;
    synchronized (btmp) {
      System.arraycopy(buf, 0, btmp, 0, 128);
      if (swapNeeded(buf, bbtmp)) {
        bbtmp.order(ByteOrder.LITTLE_ENDIAN);
      }

      bbtmp.position(32);
      short rateFactor = bbtmp.getShort();
      short rateMultiplier = bbtmp.getShort();
      rate = rateFactor;
      // if rate > 0 its in hz, < 0 its period.
      // if multiplier > 0 it multiplies, if < 0 it divides.
      if (rateFactor == 0 || rateMultiplier == 0) {
        return 0;
      }
      if (rate >= 0) {
        if (rateMultiplier > 0) {
          rate *= rateMultiplier;
        } else {
          rate /= -rateMultiplier;
        }
      } else {
        if (rateMultiplier > 0) {
          rate = -rateMultiplier / rate;
        } else {
          rate = -1. / (-rateMultiplier) / rate;
        }
      }
    }
    return rate;
  }

  /**
   * This returns the seedname in NSCL order from a raw miniseed buffer in buf.
   * This routine would be used to extract a bit of data from a raw buffer
   * without going to the full effort of creating a MiniSeed object from it.
   *
   * @param buf A array with a miniseed block in raw form
   * @return The seedname in NSCL order
   */
  public static synchronized String crackSeedname(byte[] buf) {
    return crackSeedname(buf, null).toString();
    //ByteBuffer bb = ByteBuffer.wrap(buf);
    //bb.position(8);
    //byte [] seed = new byte[12];
    //bb.get(seed);
    /*String s=new String(buf,8,12);
    try {
      //String s = new String(seed)+"             ";
      //Util.prt("len="+s.length()+" s="+s+"|");
      if(s.length() < 12) Util.prt("len < 12 "+s);
      return s.substring(10,12)+s.substring(0,5)+s.substring(7,10)+s.substring(5,7);
    }
    catch(Exception e) {return "%%DUMMY!!!!";}*/
  }
  private static StringBuilder seedTempSB = new StringBuilder(12);

  //private static final byte [] seedtmp = new byte[12];
  /**
   * This returns the seedname in NSCL order from a raw miniseed buffer in buf.
   * This routine would be used to extract a bit of data from a raw buffer
   * without going to the full effort of creating a MiniSeed object from it.
   *
   * @param seedtmp A array with a miniseed block in raw form
   * @param sb StringBUilder to return the 12 character NSCL, if null the static
   * final StringBuilder is returned so beware of others using it
   * @return The input string builder for convenience.
   */
  public static synchronized StringBuilder crackSeedname(byte[] seedtmp, StringBuilder sb) {
    if (sb == null) {
      sb = seedTempSB;
    }
    //System.arraycopy(buf, 8, seedtmp, 0, 12);
    //ByteBuffer bb = ByteBuffer.wrap(buf);
    //bb.position(8);
    //bb.get(seedtmp);
    Util.clear(sb).append((char) seedtmp[18]).append((char) seedtmp[19]).
            append((char) seedtmp[8]).append((char) seedtmp[9]).append((char) seedtmp[10]).append((char) seedtmp[11]).append((char) seedtmp[12]).
            append((char) seedtmp[15]).append((char) seedtmp[16]).append((char) seedtmp[17]).append((char) seedtmp[13]).append((char) seedtmp[14]);
    return sb;
  }

  public static String safeLetter(byte b) {
    char c = (char) b;
    return Character.isLetterOrDigit(c) || c == ' ' ? "" + c : Util.toHex((byte) c).toString();
  }

  public static String toStringRaw(byte[] buf) {
    return toStringRawSB(buf).toString();
  }

  public static StringBuilder toStringRawSB(byte[] buf) {
    StringBuilder tmp = new StringBuilder(100);
    synchronized (btmp) {
      System.arraycopy(buf, 0, btmp, 0, 128);
      ByteBuffer bb = bbtmp;
      bb.position(0);
      for (int i = 0; i < 8; i++) {
        tmp.append(safeLetter(bb.get()));
      }
      tmp.append(" ");
      bb.position(18);
      for (int i = 0; i < 2; i++) {
        tmp.append(safeLetter(bb.get()));
      }
      bb.position(8);
      for (int i = 0; i < 5; i++) {
        tmp.append(safeLetter(bb.get()));
      }
      bb.position(15);
      for (int i = 0; i < 3; i++) {
        tmp.append(safeLetter(bb.get()));
      }
      bb.position(13);
      for (int i = 0; i < 2; i++) {
        tmp.append(safeLetter(bb.get()));
      }
      bb.position(20);
      short i2 = bb.getShort();
      tmp.append(" ").append(i2).append(" ").append(Util.toHex(i2));
      i2 = bb.getShort(); //22
      tmp.append(" ").append(i2).append(" ").append(Util.toHex(i2));
      tmp.append(" ").append(bb.get()).append(":").append(bb.get()).append(":").append(bb.get());
      bb.get();
      i2 = bb.getShort();//2
      tmp.append(".").append(i2).append(" ").append(Util.toHex(i2));
      i2 = bb.getShort();//30
      tmp.append(" ns=").append(i2);
      i2 = bb.getShort();//32
      tmp.append(" rt=").append(i2);
      i2 = bb.getShort();//34
      tmp.append("*").append(i2);
      bb.position(39);
      tmp.append(" nb=").append(bb.get());
      bb.position(44);
      i2 = bb.getShort();
      tmp.append(" d=").append(i2);
      i2 = bb.getShort();
      tmp.append(" b=").append(i2);
    }
    return tmp;
  }

  public static boolean swapNeeded(byte[] buf) throws IllegalSeednameException {
    ByteBuffer bb = ByteBuffer.wrap(buf);
    return swapNeeded(buf, bb);
  }

  public static boolean swapNeeded(byte[] buf, ByteBuffer bb) throws IllegalSeednameException {
    boolean swap = false;
    if (buf[0] < '0' || buf[0] > '9' || buf[1] < '0' || buf[1] > '9'
            || buf[2] < '0' || buf[2] > '9' || buf[3] < '0' || buf[3] > '9'
            || buf[4] < '0' || buf[4] > '9' || buf[5] < '0' || buf[5] > '9'
            || (buf[6] != 'D' && buf[6] != 'R' && buf[6] != 'Q') && buf[6] != 'M' || (buf[7] != ' ' && buf[7] != '~')) {
      if (strict) {
        throw new IllegalSeednameException("*** Bad seq # or [DQRM] [6]=" + buf[6] + " [7]=" + buf[7] + " " + toStringRaw(buf));
      }
    }
    bb.order(ByteOrder.BIG_ENDIAN); // Assume its big endian
    bb.position(39);          // position # of blockettes that follow
    int nblks = bb.get();       // get it
    int offset;
    if (nblks > 0) {
      bb.position(46);         // position offset to first blockette
      offset = bb.getShort();
      if (offset > 64 || offset < 48) {       // This looks like swap is needed
        bb.order(ByteOrder.LITTLE_ENDIAN);
        bb.position(46);
        offset = bb.getShort(); // get byte swapped version
        if (offset > 200 || offset < 0) {
          Util.prt("MiniSEED: cannot figure out if this is swapped or not!!! Assume not. offset=" + offset + " " + toStringRaw(buf));
          new RuntimeException("Cannot figure swap from offset ").printStackTrace();
        } else {
          swap = true;
        }
      }
      for (int i = 0; i < nblks; i++) {
        if (offset < 48 || offset > 256) {
          Util.prta("Illegal offset trying to figure swapping off=" + Util.toHex(offset) + " nblks=" + nblks + " seedname=" + Util.toAllPrintable(crackSeedname(buf)) + " " + toStringRaw(buf));
          new RuntimeException("Illegal offset=" + offset + " nblks=" + nblks + " " + Util.toAllPrintable(crackSeedname(buf))).printStackTrace();
          break;
        }
        bb.position(offset);
        int type = bb.getShort();
        int oldoffset = offset;
        offset = bb.getShort();
        //ByteOrder order=null;
        if (type == 1000) {
          bb.position(oldoffset + 5);   // this should be word order
          if (bb.get() == 0) {
            if (swap) {
              return swap;
            }
            Util.prt("Offset said swap but order byte in b1000 said not to! " + toStringRaw(buf));
            return false;
          } else {
            return false;
          }
        }
      }
    } else {    // This block does not have blockette 1000, so make decision based on where the data starts!
      bb.position(44);
      offset = bb.getShort();
      return offset < 0 || offset > 512;
    }
    return swap;
  }

  public static int crackBlockSize(byte[] buf) throws IllegalSeednameException {
    synchronized (btmp) {
      System.arraycopy(buf, 0, btmp, 0, 128);
      if (swapNeeded(buf, bbtmp)) {
        bbtmp.order(ByteOrder.LITTLE_ENDIAN);
      }
      bbtmp.position(39);          // position # of blockettes that follow
      int nblks = bbtmp.get();       // get it
      bbtmp.position(46);         // position offset to first blockette
      int offset = bbtmp.getShort();
      for (int i = 0; i < nblks; i++) {
        if (offset < 48 || offset >= 256) {
          Util.prta("Illegal offset trying to crackBlockSize() off=" + offset + " nblks=" + nblks + " seedname=" + crackSeedname(buf));
          return 512;   // Make a guess
        }
        bbtmp.position(offset);
        int type = bbtmp.getShort();
        int oldoffset = offset;
        offset = bbtmp.getShort();
        if (type == 1000) {
          bbtmp.position(oldoffset + 6);
          return 1 << bbtmp.get();
        }
      }
    }
    return 0;

  }

  /**
   * Given a time in millis and a Calendar.FIELD, return the field value
   *
   * @param time Take this time in Millis, and return
   * @param what A Calendar.FIELD like Calendar.YEAR
   * @return The portion field from a gregorian Calendar
   */
  private static int getGregorian(long time, int what) {
    globalg.setTimeInMillis(time);
    return globalg.get(what);
  }

  /**
   * this routine takes a binary buf and breaks it into the fixed data header
   * and any other data blockettes that are present. Note : creating a miniseed
   * object does nothing but store the data. Anything that needs to use the
   * "cracked" data structures should call crack first. If the record has been
   * previously cracked, no processing is done.
   */
  private void crack() {
    if (cracked) {
      return;
    }
    cracked = true;
    try {
      synchronized (this) {

        //timeCorrection=new byte[4];
        if (isHeartBeat()) {
          return;
        }

        // Bust up the time and convert to parts and to a GregorianCalendar
        //ByteBuffer timebuf=ByteBuffer.wrap(startTime);
        // This was obsoleted when use of globalg for time calculations
        //long millis = (year -1970)*365L*86400000L+(day-1)*86400000L+((long) hour)*3600000L+
        //    ((long) minute)*60000L+((long)sec)*1000L;
        //millis += ((year - 1969)/4)*86400000L;      // Leap years past but not this one!
        //if(millis != globalg.getTimeInMillis()) Util.prt("**** Problem with cracking time "+Util.ascdatetime2(globalg)+" "+Util.ascdatetime2(millis));
        //if(time == null) time = new GregorianCalendar();
        //if(timeTruncated == null) timeTruncated = new GregorianCalendar();
        hasBlk1000 = false;
        hasBlk1001 = false;
        /*prt("sq="+new String(seq)+" ind="+new String(indicator)+
            " name="+new String(seed)+" ns="+nsamp+" nblk="+nblockettes+
            " blkoff="+blocketteOffset+" dataOff="+dataOffset);*/
        // This is the "terminator" blocks for rerequests for the GSN, most LOGS have
        // nsamp set to number of characters in buffer!
        if (seed[7] == 'L' && seed[8] == 'O' && seed[9] == 'G' && nsamp == 0) {
          nblockettes = 0;
        }

        // If data blockettes are present, process them
        // blockettes is array with ByteBuffer of each type of blockette.  BlocketteList is
        // the blockette type of each in the same order
        if (nblockettes > 0) {
          if (blockettes.length < nblockettes) {     // if number of blockettes is bigger than reserved.
            if (nsamp > 0) {
              Util.prt("Unusual expansion of blockette space in MiniSeed nblks="
                      + nblockettes + " length=" + blockettes.length + " " + getSeedNameString() + " "
                      + "" + year + " " + Util.df3(day) + ":"
                      + Util.df2(hour) + ":" + Util.df2(minute) + ":" + Util.df2(sec) + "." + Util.df4(husec));
            }
            blockettes = new ByteBuffer[nblockettes];
            blocketteList = new short[nblockettes];
            blocketteOffsets = new int[nblockettes];
          }
          ms.position(blocketteOffset);
          short next = blocketteOffset;
          for (int blk = 0; blk < nblockettes; blk++) {
            blocketteOffsets[blk] = next;
            short type = ms.getShort();
            // This is the problem when blockette 1001 was not swapped for a shor ttime 2009,128-133
            if (type == -5885) {
              if (getGregorian(time, Calendar.YEAR) == 2009
                      && getGregorian(time, Calendar.DAY_OF_YEAR) >= 128 && getGregorian(time, Calendar.DAY_OF_YEAR) <= 133) {
                ms.position(ms.position() - 2);
                ms.putShort((short) 1001);
                type = 1001;
              }
            }

            if (dbg) {
              prt(blk + "                                           **** MS: Blockette type=" + type + " off=" + next);
            }
            blocketteList[blk] = type;
            if (next < 48 || next >= recLength) {
              if (nsamp > 0) {
                Util.prt("Bad position in blockettes next2=" + next);
              }
              nblockettes = (byte) blk;
              break;
            }
            ms.position((int) next);
            switch (type) {
              case 100:     // Sample Rate Blockette
                if (buf100 == null) {
                  buf100 = new byte[12];
                  bb100 = ByteBuffer.wrap(buf100);
                }
                blockettes[blk] = bb100;
                ms.get(buf100);
                if (swap) {
                  blockettes[blk].order(ByteOrder.LITTLE_ENDIAN);
                }
                blockettes[blk].clear();
                blockettes[blk].getShort();    // type = 100
                next = blockettes[blk].getShort();
                b100Rate = bb100.getFloat();
                break;
              case 200:     // Generic Event Detection
                if (buf200 == null) {
                  buf200 = new byte[28];
                  bb200 = ByteBuffer.wrap(buf200);
                }
                blockettes[blk] = bb200;
                ms.get(buf200);
                if (swap) {
                  blockettes[blk].order(ByteOrder.LITTLE_ENDIAN);
                }
                blockettes[blk].clear();
                blockettes[blk].getShort();    // type = 200
                next = blockettes[blk].getShort();
                break;
              case 201:     // Murdock Event Detection
                if (buf201 == null) {
                  buf201 = new byte[60];
                  bb201 = ByteBuffer.wrap(buf201);
                }
                blockettes[blk] = bb201;
                ms.get(buf201);
                if (swap) {
                  blockettes[blk].order(ByteOrder.LITTLE_ENDIAN);
                }
                blockettes[blk].clear();
                blockettes[blk].getShort();    // type = 201
                next = blockettes[blk].getShort();
                break;
              case 300:     // Step Calibration
                if (buf300 == null) {
                  buf300 = new byte[32];
                  bb300 = ByteBuffer.wrap(buf300);
                }
                blockettes[blk] = bb300;
                ms.get(buf300);
                if (swap) {
                  blockettes[blk].order(ByteOrder.LITTLE_ENDIAN);
                }
                blockettes[blk].clear();
                blockettes[blk].getShort();    // type = 300
                next = blockettes[blk].getShort();
                break;
              case 310:     // Sine Calbration
                if (buf310 == null) {
                  buf310 = new byte[32];
                  bb310 = ByteBuffer.wrap(buf310);
                }
                ms.get(buf310);
                blockettes[blk] = bb310;
                if (swap) {
                  blockettes[blk].order(ByteOrder.LITTLE_ENDIAN);
                }
                blockettes[blk].clear();
                blockettes[blk].getShort();    // type = 310
                next = blockettes[blk].getShort();
                break;
              case 320:     // Pseudo-random calibration
                if (buf320 == null) {
                  buf320 = new byte[32];
                  bb320 = ByteBuffer.wrap(buf320);
                }
                ms.get(buf320);
                blockettes[blk] = bb320;
                if (swap) {
                  blockettes[blk].order(ByteOrder.LITTLE_ENDIAN);
                }
                blockettes[blk].clear();
                blockettes[blk].getShort();    // type = 320
                next = blockettes[blk].getShort();
                break;
              case 390:     // Generic Calibration
                if (buf390 == null) {
                  buf390 = new byte[28];
                  bb390 = ByteBuffer.wrap(buf390);
                }
                ms.get(buf390);
                blockettes[blk] = bb390;
                if (swap) {
                  blockettes[blk].order(ByteOrder.LITTLE_ENDIAN);
                }
                blockettes[blk].clear();
                blockettes[blk].getShort();    // type = 330
                next = blockettes[blk].getShort();
                break;
              case 395:     // Calibration abort
                if (buf395 == null) {
                  buf395 = new byte[16];
                  bb395 = ByteBuffer.wrap(buf395);
                }
                blockettes[blk] = bb395;
                ms.get(buf395);
                if (swap) {
                  blockettes[blk].order(ByteOrder.LITTLE_ENDIAN);
                }
                blockettes[blk].clear();
                blockettes[blk].getShort();    // type = 395
                next = blockettes[blk].getShort();
                break;
              case 400:     // Beam blockette
                if (buf400 == null) {
                  buf400 = new byte[16];
                  bb400 = ByteBuffer.wrap(buf400);
                }
                ms.get(buf400);
                blockettes[blk] = bb400;
                if (swap) {
                  blockettes[blk].order(ByteOrder.LITTLE_ENDIAN);
                }
                blockettes[blk].clear();
                blockettes[blk].getShort();    // type = 400
                next = blockettes[blk].getShort();
                break;
              case 405:     // Beam Delay 
                if (buf405 == null) {
                  buf405 = new byte[6];
                  bb405 = ByteBuffer.wrap(buf405);
                }
                ms.get(buf405);
                blockettes[blk] = bb405;
                if (swap) {
                  blockettes[blk].order(ByteOrder.LITTLE_ENDIAN);
                }
                blockettes[blk].clear();
                blockettes[blk].getShort();    // type = 405
                next = blockettes[blk].getShort();
                break;
              case 500:     // Timing BLockette
                if (buf500 == null) {
                  buf500 = new byte[200];
                  bb500 = ByteBuffer.wrap(buf500);
                }
                ms.get(buf500);
                blockettes[blk] = bb500;
                if (swap) {
                  blockettes[blk].order(ByteOrder.LITTLE_ENDIAN);
                }
                blockettes[blk].clear();
                blockettes[blk].getShort();    // type = 500
                next = blockettes[blk].getShort();
                break;
              case 1000:
                if (buf1000 == null) {
                  buf1000 = new byte[8];
                  bb1000 = ByteBuffer.wrap(buf1000);
                  b1000 = new Blockette1000(buf1000);
                }
                ms.get(buf1000);
                blockettes[blk] = bb1000;
                b1000.reload(buf1000);
                if (swap) {
                  blockettes[blk].order(ByteOrder.LITTLE_ENDIAN);
                }
                blockettes[blk].clear();
                blockettes[blk].getShort();    // type = 1000
                next = blockettes[blk].getShort();
                encoding = blockettes[blk].get();
                order = blockettes[blk].get();
                recLength = 1;
                recLength = recLength << blockettes[blk].get();
                if (dbg) {
                  prt("MS: Blk 1000 length**2=" + recLength + " order=" + order
                          + " next=" + next + " encoding=" + encoding);
                }
                hasBlk1000 = true;
                if (b1000.getRecordLength() > ms.capacity()) {
                  byte[] tmp = new byte[b1000.getRecordLength()];
                  System.arraycopy(buf, 0, tmp, 0, buf.length);
                  buf = tmp;
                  int pos = ms.position();
                  ms = ByteBuffer.wrap(buf);
                  ms.position(pos);
                }
                break;
              case 1001:      // data extension (Quanterra only?) 
                if (buf1001 == null) {
                  buf1001 = new byte[8];
                  bb1001 = ByteBuffer.wrap(buf1001);
                  b1001 = new Blockette1001(buf1001);
                }
                ms.get(buf1001);
                blockettes[blk] = bb1001;
                b1001.reload(buf1001);
                if (swap) {
                  blockettes[blk].order(ByteOrder.LITTLE_ENDIAN);
                }
                blockettes[blk].clear();
                blockettes[blk].getShort();    // type = 1001
                next = blockettes[blk].getShort();
                timingQuality = blockettes[blk].get();
                microSecOffset = blockettes[blk].get();
                blockettes[blk].get();        // reserved
                nframes = blockettes[blk].get();

                // For a period in 2014, 120 to 142 II network was generating b1001 with frames=0, fix them.
                if (nframes == 0) {
                  if (year == 2014 && day >= 120 && day <= 142 && seedname.substring(0, 2).equals("II")) {
                    setB1001FrameCount(this.getUsedFrameCount());
                  }
                }
                if (dbg) {
                  prt("MS: Blk 1001 next=" + next + " timing=" + timingQuality + " uSecOff=" + microSecOffset
                          + " nframes=" + nframes);
                }
                hasBlk1001 = true;
                break;
              /*case 2000:
                int pos=ms.position();
                next = ms.getShort();
                next = ms.getShort();
                ms.position(pos);
                b2000 = new Blockette2000(ms);
                ms.position(next);
                break;*/
              default:
                if (dbg && type != 2000) {
                  prt("MS: - unknown blockette type=" + type);
                }
                short ty = ms.getShort();
                next = ms.getShort();
                int len = (next == 0 ? recLength : next) - blocketteOffsets[blk];
                if (len <= 0) {
                  prta("Blockette offsets make no sense next=" + next + " recl=" + recLength + " offset=" + blocketteOffsets[blk]);
                  break;
                }
                byte[] tmp = new byte[len];
                if (blocketteOffsets[blk] + len > ms.capacity() || blocketteOffsets[blk] <= 0) {
                  prt("BLockette has bad offsets - probably bad off=" + blocketteOffsets[blk] + " len=" + len + " size=" + ms.capacity());
                  break;
                } else {
                  ms.position(blocketteOffsets[blk]);

                  ms.get(tmp);
                  blockettes[blk] = ByteBuffer.wrap(tmp);
                  if (swap) {
                    blockettes[blk].order(ByteOrder.LITTLE_ENDIAN);
                  }
                  blockettes[blk].clear();
                  blockettes[blk].getShort();    // type = 1001
                  ms.position(next);
                }
                break;
            }
          }
        }

        cracked = true;
      }     // end of synchronized on this!
    } catch (RuntimeException e) {
      Util.prt("Runtim in crack() e=" + e);
      e.printStackTrace();
      throw e;
    }
  }

  public boolean deleteBlockette(int type) {
    for (int i = 0; i < nblockettes; i++) {
      if (blocketteList[i] == type) {
        // found it, take it out of the list and adjust the offsets
        if (i == 0) {
          ms.position(46);
          if (nblockettes == 1) {
            ms.putShort((short) 0);
          } else {
            ms.putShort((short) blocketteOffsets[1]); // make 2nd one the first one
          }
          ms.putInt(0);            // Wipe out the offset and type bytes
        } else {
          ms.position(blocketteOffsets[i - 1] + 2);
          if (i + 1 < nblockettes) {
            ms.putShort((short) blocketteOffsets[i + 1]);
          } else {
            ms.putShort((short) 0);      // End of chain
          }
        }
        ms.position(39);
        ms.put((byte) (nblockettes - 1));
        nblockettes--;
        cracked = false;
        crack();          // recrack it with the block missing
        return true;
      }
    }
    return false;
  }

  public static void getPerfString(StringBuilder sb) {
    sb.append("MiniSeed : nnew=").append(nnew).append(" nmsload=").append(nmsload).append(" nbufload=").append(nbufload);
  }

  /**
   * Get a string builder trimmed to a certain length
   *
   * @param tmp A stringBuider or if null the common one will be provided
   * @param len Trim length
   * @return
   */
  public StringBuilder toStringBuilder(StringBuilder tmp, int len) {
    StringBuilder sb = toStringBuilder(tmp);
    synchronized (sb) {
      if (sb.length() > len) {
        sb.delete(len, sb.length());
      }
    }
    return sb;
  }
  StringBuilder strms = new StringBuilder(120);

  @Override
  public StringBuilder toStringBuilder(StringBuilder tmp) {
    StringBuilder sb = tmp;
    if (sb == null) {
      sb = Util.clear(strms);
    }
    synchronized (sb) {
      sb.append(getSeedNameSB()).append(" ").append(new String(seq)).append(" ").
              append(new String(indicator)).append(getTimeString()).
              append(" n=").append(nsamp).
              append(" rt=").append(getRate()).
              append(" dt=").append(dataOffset).
              append(" off=").append(blocketteOffset).
              append(" #f=").append(getUsedFrameCount()).append(" nb=").
              append(nblockettes).append(swap ? "S" : "");
      for (int i = 0; i < nblockettes; i++) {
        switch (blocketteList[i]) {
          case 1000:
            sb.append(" ");
            b1000.toStringBuilder(sb);
            break;
          case 1001:
            b1001.toStringBuilderShort(sb);
            break;
          case 100:
            blockettes[i].position(4);
            sb.append(" (100=").append(blockettes[i].getFloat()).append(")");
            break;
          case 2000:
            blockettes[i].position(0);
            sb.append(new Blockette2000(blockettes[i]).toString());
            break;
          default:
            sb.append(" (").append(blocketteList[i]).append(")");
            break;
        }
      }
      if (nblockettes < 2) {
        sb.append(" bsiz=").append(recLength);
      }
      String f = "";
      if (order == 0) {
        sb.append("S");
      }
      if (swap) {
        sb.append("S");
      }
      if ((activityFlags & ACTIVITY_CAL_ON) != 0) {
        sb.append("C");
      }
      if ((activityFlags & ACTIVITY_TIME_CORRECTION_APPLIED) != 0) {
        sb.append("*");
      }
      if ((activityFlags & ACTIVITY_BEGIN_EVENT) != 0) {
        sb.append("T");
      }
      if ((activityFlags & ACTIVITY_END_EVENT) != 0) {
        sb.append("t");
      }
      if ((activityFlags & ACTIVITY_POSITIVE_LEAP) != 0) {
        sb.append("+");
      }
      if ((activityFlags & ACTIVITY_NEGATIVE_LEAP) != 0) {
        sb.append("-");
      }
      if ((activityFlags & ACTIVITY_EVENT_IN_PROGRESS) != 0) {
        sb.append("E");
      }
      if ((ioClockFlags & IOCLOCK_PARITY_ERROR) != 0) {
        sb.append("P");
      }
      if ((ioClockFlags & IOCLOCK_LONG_RECORD) != 0) {
        sb.append("L");
      }
      if ((ioClockFlags & IOCLOCK_SHORT_RECORD) != 0) {
        sb.append("s");
      }
      if ((ioClockFlags & IOCLOCK_START_SERIES) != 0) {
        sb.append("O");
      }
      if ((ioClockFlags & IOCLOCK_END_SERIES) != 0) {
        sb.append("F");
      }
      if ((ioClockFlags & IOCLOCK_LOCKED) == 0) {
        sb.append("U");
      }
      if ((dataQualityFlags & QUALITY_AMP_SATURATED) != 0) {
        sb.append("A");
      }
      if ((dataQualityFlags & QUALITY_CLIPPED) != 0) {
        sb.append("c");
      }
      if ((dataQualityFlags & QUALITY_SPIKES) != 0) {
        sb.append("G");
      }
      if ((dataQualityFlags & QUALITY_GLITCHES) != 0) {
        sb.append("g");
      }
      if ((dataQualityFlags & QUALITY_MISSING_DATA) != 0) {
        sb.append("M");
      }
      if ((dataQualityFlags & QUALITY_TELEMETRY_ERROR) != 0) {
        sb.append("e");
      }
      if ((dataQualityFlags & QUALITY_CHARGING) != 0) {
        sb.append("d");
      }
      if ((dataQualityFlags & QUALITY_QUESTIONABLE_TIME) != 0) {
        sb.append("q");
      }
    }
    return sb;
  }

  /**
   * create a basic string from the fixed data header representing this packet.
   * any other data blockettes are added at the end to indicate their presence
   *
   * @return A representative string
   */
  @Override
  public String toString() {
    crack();
    Util.clear(strms);
    strms.append(getSeedNameSB()).append(" ").append(new String(seq)).append(" ").
            append(new String(indicator)).append(getTimeString()).
            append(" n=").append((nsamp + "    ").substring(0, 5)).
            append("rt=").append(Util.rightPad(Util.df23(getRate()), 5)).
            append(" dt=").append((dataOffset + "  ").substring(0, 3)).
            append(" off=").append((blocketteOffset + "   ").substring(0, 3)).
            append(" #f=").append(getUsedFrameCount()).append(" nb=").
            append(nblockettes).append(swap ? "S" : "");
    for (int i = 0; i < nblockettes; i++) {
      switch (blocketteList[i]) {
        case 1000:
          strms.append(" ").append(b1000.toString());
          break;
        case 1001:
          strms.append(b1001.toStringShort());
          break;
        case 100:
          blockettes[i].position(4);
          strms.append(" (100=").append((blockettes[i].getFloat() + "     ").substring(0, 5)).append(")");
          break;
        case 2000:
          blockettes[i].position(0);
          strms.append(new Blockette2000(blockettes[i]).toString());
          break;
        default:
          strms.append(" (").append(blocketteList[i]).append(")");
          break;
      }
    }
    if (nblockettes < 2) {
      strms.append(" bsiz=").append(recLength);
    }
    String f = "";
    if (order == 0) {
      strms.append("S");
    }
    if (swap) {
      strms.append("S");
    }
    if ((activityFlags & ACTIVITY_CAL_ON) != 0) {
      strms.append("C");
    }
    if ((activityFlags & ACTIVITY_TIME_CORRECTION_APPLIED) != 0) {
      strms.append("*");
    }
    if ((activityFlags & ACTIVITY_BEGIN_EVENT) != 0) {
      strms.append("T");
    }
    if ((activityFlags & ACTIVITY_END_EVENT) != 0) {
      strms.append("t");
    }
    if ((activityFlags & ACTIVITY_POSITIVE_LEAP) != 0) {
      strms.append("+");
    }
    if ((activityFlags & ACTIVITY_NEGATIVE_LEAP) != 0) {
      strms.append("-");
    }
    if ((activityFlags & ACTIVITY_EVENT_IN_PROGRESS) != 0) {
      strms.append("E");
    }
    if ((ioClockFlags & IOCLOCK_PARITY_ERROR) != 0) {
      strms.append("P");
    }
    if ((ioClockFlags & IOCLOCK_LONG_RECORD) != 0) {
      strms.append("L");
    }
    if ((ioClockFlags & IOCLOCK_SHORT_RECORD) != 0) {
      strms.append("s");
    }
    if ((ioClockFlags & IOCLOCK_START_SERIES) != 0) {
      strms.append("O");
    }
    if ((ioClockFlags & IOCLOCK_END_SERIES) != 0) {
      strms.append("F");
    }
    if ((ioClockFlags & IOCLOCK_LOCKED) == 0) {
      strms.append("U");
    }
    if ((dataQualityFlags & QUALITY_AMP_SATURATED) != 0) {
      strms.append("A");
    }
    if ((dataQualityFlags & QUALITY_CLIPPED) != 0) {
      strms.append("c");
    }
    if ((dataQualityFlags & QUALITY_SPIKES) != 0) {
      strms.append("G");
    }
    if ((dataQualityFlags & QUALITY_GLITCHES) != 0) {
      strms.append("g");
    }
    if ((dataQualityFlags & QUALITY_MISSING_DATA) != 0) {
      strms.append("M");
    }
    if ((dataQualityFlags & QUALITY_TELEMETRY_ERROR) != 0) {
      strms.append("e");
    }
    if ((dataQualityFlags & QUALITY_CHARGING) != 0) {
      strms.append("d");
    }
    if ((dataQualityFlags & QUALITY_QUESTIONABLE_TIME) != 0) {
      strms.append("q");
    }

    return strms.toString();
  }

  /**
   * Compare this MiniSeed object to another. First the two objects' SEED names
   * are compared, and if they are equal, their starting dates are compared.
   *
   * @param o the other MiniSeed object
   * @return -1 if this object is less than other, 0 if the two objects are
   * equal, and +1 if this object is greater than other.
   */
  public int compareTo(MiniSeed o) {
    MiniSeed other;
    int cmp;
    if (o.isClear()) {
      return -1;    // Cleared MiniSeeds are always at end
    }
    if (isClear()) {
      return 1;
    }
    other = o;
    //crack();
    //other.crack();
    //cmp = getSeedNameString().compareTo(other.getSeedNameString());
    cmp = Util.compareTo(getSeedNameSB(), other.getSeedNameSB());
    if (cmp != 0) {
      return cmp;
    }
    if (time < other.getTimeInMillis()) {
      return -1;
    } else if (time > other.getTimeInMillis()) {
      return 1;
    } else {
      return 0;
    }
  }

  /**
   * return number of used data frames. We figure this by looking for all zero
   * frames from the end!
   *
   * @return Number of used frames based on examining for zeroed out frames
   * rather than b1001
   */
  public int getUsedFrameCount() {
    crack();
    int i;
    for (i = Math.min(buf.length, recLength) - 1; i >= dataOffset; i--) {
      if (buf[i] != 0) {
        break;
      }
    }
    return (i - dataOffset + 64) / 64;      // This is the data frame # used
  }

  /**
   * return state of swap as required by the Steim decompression routines and
   * our internal convention. We both assume bytes are in BIG_ENDIAN order and
   * swap bytes if the records is not. Stated another way, we swap whenever the
   * compressed data indicates it is is little endian order.
   *
   * @return True if bytes need to be swapped
   */
  public boolean isSwapBytes() {
    return swap;
  }

  /**
   * return the blocksize or record length of this mini-seed
   *
   * @return the blocksize of record length
   */
  @Override
  public int getBlockSize() {
    crack();
    return recLength;
  }

  /**
   * return number of samples in packet
   *
   * @return # of samples
   */
  @Override
  public int getNsamp() {
    return (int) nsamp;
  }

  /**
   * return number of data blockettes
   *
   * @return # of data blockettes
   */
  public int getNBlockettes() {
    return (int) nblockettes;
  }

  /**
   * return the offset of the ith blockette
   *
   * @param i The index of the block (should be less than return of
   * getNBlockettes
   * @return The offset of the ith blockette or -1 if i is out or range
   */
  public int getBlocketteOffset(int i) {
    crack();
    if (i < blocketteList.length) {
      return blocketteOffsets[i];
    }
    return -1;
  }

  /**
   * return the type of the ith blockette
   *
   * @param i The index of the block (should be less than return of
   * getNBlockettes
   * @return The type of the ith blockette or -1 if i is out or range
   */
  public int getBlocketteType(int i) {
    crack();
    if (i < blocketteList.length) {
      return (int) blocketteList[i];
    }
    return -1;
  }

  /**
   * return a ByteBuffer to the ith blockett
   *
   * @param i The index of the blockette desired
   * @return A byte buffer with the data for this blockett
   */
  public ByteBuffer getBlockette(int i) {
    return blockettes[i];
  }

  /**
   * return the seed name of this component in nnssssscccll order
   *
   * @return the nnssssscccll
   */
  @Override
  public String getSeedNameString() {
    if (seednameString == null) {
      seednameString = seedname.toString();
    }
    return seednameString;
    //String s = new String(seed);
    //return s.substring(10,12)+s.substring(0,5)+s.substring(7,10)+s.substring(5,7);
  }

  /**
   * return the seed name of this component in nnssssscccll order
   *
   * @return the nnssssscccll
   */
  @Override
  public StringBuilder getSeedNameSB() {

    return seedname;
  }

  /**
   * return the two char data block type, normally "D ", "Q ", etc
   *
   * @return the indicator
   */
  public String getIndicator() {
    return new String(indicator);
  }

  /**
   * return the encoding
   *
   * @return the encoding
   */
  public boolean isBigEndian() {
    return (order != 0);
  }

  /**
   * return the encoding
   *
   * @return the encoding
   */
  public int getEncoding() {
    crack();
    return encoding;
  }

  /**
   * return year from the first sample time
   *
   * @return the year
   */
  public int getYear() {
    return year;
  }

  /**
   * return the day-of-year from the first sample time
   *
   * @return the day-of-year of first sample
   */
  public int getDay() {
    return day;
  }

  /**
   * return the day-of-year from the first sample time
   *
   * @return the day-of-year of first sample
   */
  public int getDoy() {
    return day;
  }

  /**
   * return the hours
   *
   * @return the hours of first sample
   */
  public int getHour() {
    return hour;
  }

  /**
   * return the minutes
   *
   * @return the minutes of first sample
   */
  public int getMinute() {
    return minute;
  }

  /**
   * return the seconds
   *
   * @return the seconds of first sample
   */
  public int getSeconds() {
    return sec;
  }

  /**
   * return the 100s of uSeconds
   *
   * @return 100s of uSeconds of first sample
   */
  /*public int getHuseconds() {
    return husec;
  }*/
  /** return the number of microseconds.  This is the hundreds of usec from the MiniSeed header
   * plus the ones and 10s of usecs from the B1001 if present
   * 
   * @return microseconds of the time
   */
  public int getUseconds() {
    if (b1001 != null) {
      return husec * 100 + getB1001USec();
    }
    return husec * 100;
  }
  /**
   * return Julian day (a big integer) of the first sample's year and day
   *
   * @return The 1st sample time
   */
  public int getJulian() {
    return julian;
  }

  /**
   * return the activity flags from field 12 of the SEED fixed header
   * <p>
   * Bit Description
   * <p>
   * 0 Calibration signals present
   * <p>
   * 1 Time Correction applied.
   * <p>
   * 2 Beginning of an event, station trigger
   * <p>
   * 3 End of an event, station de-triggers
   * <p>
   * 4 Positive leap second occurred in packet
   * <p>
   * 5 Negative leap second occurred in packet
   * <p>
   * 6 Event in Progress
   * <p>
   * @return The activity flags
   */
  public byte getActivityFlags() {
    return activityFlags;
  }

  /**
   * return the data quality flags from field 13 of the Seed fixed header
   * <p>
   * bit Description
   * <p>
   * 0 Amplifier saturation detected
   * <p>
   * 1 Digitizer clipping detected
   * <p>
   * 2 Spikes detected
   * <p>
   * 3 Glitches detected
   * <p>
   * 4 Missing/padded data present
   * <p>
   * 5 Telemetry synchronization error
   * <p>
   * 6 A digital filter may be "charging"
   * <p>
   * 7 Time tag is questionable
   *
   * @return The data quality flags
   */
  public byte getDataQualityFlags() {
    return dataQualityFlags;
  }

  /**
   * Return the IO and clock flags from field 13 of fixed header
   * <p>
   * Bit Description
   * <p>
   * 0 Station volume parity error possibly present
   * <p>
   * 1 Long record read( possibly no problem)
   * <p>
   * 2 Short record read(record padded)
   * <p>
   * 3 Start of time series
   * <p>
   * 4 End of time series
   * <p>
   * 5 Clock locked
   *
   * @return The IO and clock flags
   */
  public byte getIOClockFlags() {
    return ioClockFlags;
  }

  /**
   * return the raw time bytes in an array
   *
   * @return The raw time bytes in byte array
   */
  public byte[] getRawTimeBuf() {
    byte[] b = new byte[startTime.length];
    System.arraycopy(startTime, 0, b, 0, startTime.length);
    return b;   // return copy of time bytes.
  }

  /**
   * return the raw time bytes in an array
   *
   * @param b The user array to get the time
   * @return The raw time bytes in byte array
   */
  public byte[] getRawTimeBuf(byte[] b) {
    System.arraycopy(startTime, 0, b, 0, startTime.length);
    return b;   // return copy of time bytes.
  }

  /**
   * return the time in gregorian calendar millis
   *
   * @return GregorianCalendar millis for start time
   */
  @Override
  public long getTimeInMillis() {
    return time;
  }

  /**
   * return a GregorianCalendar set to the 1st sample time, it is a copy just so
   * users do not do calculations with it and end up changing the mini-seed
   * record time. This time is rounded in millis
   *
   * @return The 1st sample time
   */
  @Override
  public GregorianCalendar getGregorianCalendar() {
    GregorianCalendar e = new GregorianCalendar();
    e.setTimeInMillis(time);
    return e;
  }

  /**
   * return the time in gregorian calendar millis, this time is truncated
   *
   * @return GregorianCalendar millis for start time
   */
  public long getTimeInMillisTruncated() {
    return timeTruncated;
  }

  /**
   * return a GregorianCalendar set to the 1st sample time, it is a copy just so
   * users do not do calculations with it and end up changing the mini-seed
   * record time. This time is truncated in millis NOTE: since this creates new
   * GregorianCalendars it can be inefficient.
   *
   * @return The 1st sample time
   */
  public GregorianCalendar getGregorianCalendarTruncated() {
    GregorianCalendar e = new GregorianCalendar();
    e.setTimeInMillis(timeTruncated);
    return e;
  }

  /**
   * give a standard time string for the 1st sample time. NOTE: since this
   * creates new GregorianCalendars it can be inefficient.
   *
   * @return the time string yyyy ddd:hh:mm:ss.hhhh
   */
  public String getTimeString() {
    return "" + year + " " + Util.df3(day) + ":" + Util.df2(hour) + ":" + Util.df2(minute) 
            + ":" + Util.df2(sec) + "." + Util.df6(getUseconds()) ;
  }

  public GregorianCalendar getGregorianCalendarEndTime() {
    GregorianCalendar endtmp = new GregorianCalendar();
    endtmp.setTimeInMillis(time + (long) ((nsamp - 1) / getRate() * 1000. + 0.5));
    return endtmp;
  }

  @Override
  public long getNextExpectedTimeInMillis() {
    return time + (long) ((nsamp) / getRate() * 1000. + 0.5);
  }

  @Override
  public long getLastTimeInMillis() {
    return time + (long) ((nsamp - 1) / getRate() * 1000. + 0.5);
  }
  //GregorianCalendar end = new GregorianCalendar();
  long end;

  public String getEndTimeString() {
    String s;
    synchronized (globalg) {

      if (getRate() < 0.0001) {
        globalg.setTimeInMillis(time);
      } else {
        globalg.setTimeInMillis(time + (long) ((nsamp - 1) / getRate() * 1000. + 0.5));
      }
      int iy = globalg.get(Calendar.YEAR);
      int doy = globalg.get(Calendar.DAY_OF_YEAR);
      int hr = globalg.get(Calendar.HOUR_OF_DAY);
      int min = globalg.get(Calendar.MINUTE);
      int sc = globalg.get(Calendar.SECOND);
      int msec = globalg.get(Calendar.MILLISECOND);
      s = "" + iy + " " + Util.df3(doy) + ":" + Util.df2(hr) + ":" + Util.df2(min) + ":" + Util.df2(sc) + "." + Util.df3(msec);
    }
    return s;
  }

  /**
   * return the rate factor from the seed header
   *
   * @return The factor
   */
  public short getRateFactor() {
    return rateFactor;
  }

  /**
   * return the rate multiplier from the seed header
   *
   * @return The multiplier
   */
  public short getRateMultiplier() {
    return rateMultiplier;
  }

  /**
   * return the sample rate
   *
   * @return the sample rate
   */
  @Override
  public double getRate() {
    if (rate > -0.5) {
      return rate;
    }
    if (b100Rate > 0.) {
      rate = b100Rate;
      return rate;
    }
    rate = rateFactor;
    // if rate > 0 its in hz, < 0 its period.
    // if multiplier > 0 it multiplies, if < 0 it divides.
    if (rateFactor == 0 || rateMultiplier == 0) {
      return 0;
    }
    if (rate >= 0) {
      if (rateMultiplier > 0) {
        rate *= rateMultiplier;
      } else {
        rate /= -rateMultiplier;
      }
    } else {
      if (rateMultiplier > 0) {
        rate = -rateMultiplier / rate;
      } else {
        rate = 1. / rateMultiplier / rate;     // note two minus signs cancel
      }
    }
    return rate;
  }

  public final void setRate(double r) {
    rate = r;
    if (rate > 0.9999) {
      rateFactor = (short) (rate * 100. + 0.001);
      rateMultiplier = -100;
    } else if (rate * 60. - 1.0 < 0.00000001) {    // Is it one minute data
      rateFactor = -60;
      rateMultiplier = 1;
    } // Its less than 1 hz
    else {
      r = 1. / r;                                       // convert to period
      if (Math.abs(r - Math.round(r)) < 0.0001) {      // is it an even period in seconds
        rateFactor = (short) Math.round(-r);          // yes, set it neg for period mult 1
        rateMultiplier = (short) 1;
      } else {
        rateMultiplier = 1;             /// rate factor is a period so negative, so multiplier is in numerator.
        while (r < 3275) {
          r = r * 10.;
          rateMultiplier = (short) (rateMultiplier * 10);
        }
        rateFactor = (short) Math.round(-r);
      }
    }
    ms.position(32);
    ms.putShort(rateFactor);
    ms.putShort(rateMultiplier);

  }

  /**
   * return the rate in the B100 blockette if present, if not -1.
   *
   * @return the B100 rate
   */
  public double getB100Rate() {
    crack();
    return b100Rate;
  }

  /**
   * Return the duration of this packet.
   *
   * @return the duration in seconds.
   */
  public double getDuration() {
    return nsamp / getRate();
  }

  /**
   * return the buffer with the data portion of the packet of length.
   *
   * @param len The number of bytes to return
   * @return An array with the bytes
   */
  public byte[] getData(int len) {
    ms.position(dataOffset);
    byte[] tmp = new byte[len];
    ms.get(tmp);
    return tmp;
  }

  /**
   * get the raw buffer representing this Miniseed-record. Beware you are
   * getting the actual buffer so changing it will change the underlying data of
   * this MiniSeed object. If this is done after crack, some of the internals
   * will not reflect it.
   *
   * @return the raw data byte buffer
   */
  @Override
  public byte[] getBuf() {
    return buf;
  }

  /**
   * return the record length from the blockette 1000. Note the length might
   * also be determined by getBuf().length as to how this mini-seed was created
   * (buf is a copy of the input buffer).
   *
   * @return the length if blockette 1000 present, zero if it is not
   */
  public int getRecLength() {
    crack();
    return recLength;
  }

  /**
   * return a blockette 100 - if this is a loaded block make sure the block is
   * actuall in last load
   *
   * @return the blockette or null if this blockette is not in mini-seed record
   */
  public byte[] getBlockette100() {
    crack();
    if (buf100 != null) {
      if (buf100[0] == 0 && buf100[1] == 0) {
        return null;
      }
    }
    return buf100;
  }             // These getBlockettennn contain data from the various

  /**
   * return a blockette 200
   *
   * @return the blockette or null if this blockette is not in mini-seed record
   */
  public byte[] getBlockette200() {
    crack();
    if (buf200 != null) {
      if (buf200[0] == 0 && buf200[1] == 0) {
        return null;
      }
    }
    return buf200;
  }             // possible "data blockettes" found. They are

  /**
   * return a blockette 201
   *
   * @return the blockette or null if this blockette is not in mini-seed record
   */
  public byte[] getBlockette201() {
    crack();
    if (buf201 != null) {
      if (buf201[0] == 0 && buf201[1] == 0) {
        return null;
      }
    }
    return buf201;
  }             // never all defined.

  /**
   * return a blockette 300
   *
   * @return the blockette or null if this blockette is not in mini-seed record
   */
  public byte[] getBlockette300() {
    crack();
    if (buf300 != null) {
      if (buf300[0] == 0 && buf300[1] == 0) {
        return null;
      }
    }
    return buf300;
  }

  /**
   * return a blockette 310
   *
   * @return the blockette or null if this blockette is not in mini-seed record
   */
  public byte[] getBlockette310() {
    crack();
    if (buf310 != null) {
      if (buf310[0] == 0 && buf310[1] == 0) {
        return null;
      }
    }
    return buf310;
  }

  /**
   * return a blockette 320
   *
   * @return the blockette or null if this blockette is not in mini-seed record
   */
  public byte[] getBlockette320() {
    crack();
    if (buf320 != null) {
      if (buf320[0] == 0 && buf320[1] == 0) {
        return null;
      }
    }
    return buf320;
  }

  /**
   * return a blockette 390
   *
   * @return the blockette or null if this blockette is not in mini-seed record
   */
  public byte[] getBlockette390() {
    crack();
    if (buf390 != null) {
      if (buf390[0] == 0 && buf390[1] == 0) {
        return null;
      }
    }
    return buf390;
  }

  /**
   * return a blockette 395
   *
   * @return the blockette or null if this blockette is not in mini-seed record
   */
  public byte[] getBlockette395() {
    crack();
    if (buf395 != null) {
      if (buf395[0] == 0 && buf395[1] == 0) {
        return null;
      }
    }
    return buf395;
  }

  /**
   * return a blockette 400
   *
   * @return the blockette or null if this blockette is not in mini-seed record
   */
  public byte[] getBlockette400() {
    crack();
    if (buf400 != null) {
      if (buf400[0] == 0 && buf400[1] == 0) {
        return null;
      }
    }
    return buf400;
  }

  /**
   * return a blockette 405
   *
   * @return the blockette or null if this blockette is not in mini-seed record
   */
  public byte[] getBlockette405() {
    crack();
    if (buf405 != null) {
      if (buf405[0] == 0 && buf405[1] == 0) {
        return null;
      }
    }
    return buf405;
  }

  /**
   * return a blockette 500
   *
   * @return the blockette or null if this blockette is not in mini-seed record
   */
  public byte[] getBlockette500() {
    crack();
    if (buf500 != null) {
      if (buf500[0] == 0 && buf500[1] == 0) {
        return null;
      }
    }
    return buf500;
  }

  /**
   * return a blockette 1000
   *
   * @return the blockette or null if this blockette is not in mini-seed record
   */
  public byte[] getBlockette1000() {
    crack();
    if (buf1000 != null) {
      if (buf1000[0] == 0 && buf1000[1] == 0) {
        return null;
      }
    }
    return buf1000;
  }

  /**
   * return a blockette 1001
   *
   * @return the blockette or null if this blockette is not in mini-seed record
   */
  public byte[] getBlockette1001() {
    crack();
    if (buf1001 != null) {
      if (buf1001[0] == 0 && buf1001[1] == 0) {
        return null;
      }
    }
    return buf1001;
  }

  /**
   * return the forward integration constant
   *
   * @return the forward integration constant
   */
  public int getForward() {
    return forward;
  }

  /**
   * return the offset to the data
   *
   * @return the offset to the data in bytes
   */
  public int getDataOffset() {
    return (int) dataOffset;
  }

  /**
   * return reverse integration constant
   *
   * @return The reverse integration constant
   */
  public int getReverse() {
    return reverse;
  }

  /**
   * get the sequence number as an int!
   *
   * @return the sequence number
   */
  public int getSequence() {
    return Integer.parseInt(new String(seq));
  }

  /**
   * return the timing quality byte from blockette 1000
   *
   * @return the timing quality byte from blockette 1001 or -1 if it does not
   * exist
   */
  public int getTimingQuality() {
    crack();
    if (b1001 == null) {
      return -1;
    }
    return b1001.getTimingQuality();
  }

  /**
   * return the number of used frames from from blockette 1000
   *
   * @return the # used frames from blockette 1001 or -1 if it does not exist
   */
  public int getB1001FrameCount() {
    crack();
    if (b1001 == null) {
      return -1;
    }
    return b1001.getFrameCount();
  }

  /**
   * return the number of used frames from from blockette 1000
   *
   * @return the # used frames from blockette 1001 or -1 if it does not exist
   */
  public int getB1001USec() {
    crack();
    if (b1001 == null) {
      return -1;
    }
    return b1001.getUSecs();
  }

  /**
   * the unit test main
   *
   * @param args COmmand line arguments
   */
  /**
   * Set the length of the packet to n in the Blockette 1000
   *
   * @param n the record length, anything but 512 or 4096 will generate some
   * warning output
   */
  public void setB1000Length(int n) {
    crack();
    if (n != 512 && n != 4096) {
      Util.prt("Unusual B1000 len=" + n + " " + toString());
    }
    // Change length of  record in Blockette1000 and change it in buf1000 and in data buffer.
    if (b1000 != null) {
      b1000.setRecordLength(n);
      System.arraycopy(b1000.getBytes(), 0, buf1000, 0, 8);
      for (int j = 0; j < blocketteList.length; j++) {
        if (blocketteList[j] == 1000) {
          System.arraycopy(b1000.getBytes(), 0, buf, blocketteOffsets[j], 8);
          break;
        }
      }
      cracked = false;
    }
  }

  public void setB1001FrameCount(int n) {
    // Change #frames in framce count in B1ockette 1001 and store in buf1001 and data buffer
    crack();
    if (b1001 != null) {
      b1001.setFrameCount(n);
      System.arraycopy(b1001.getBytes(), 0, buf1001, 0, 8);
      for (int j = 0; j < blocketteList.length; j++) {
        if (blocketteList[j] == 1001) {
          System.arraycopy(b1001.getBytes(), 0, buf, blocketteOffsets[j], 8);
          break;
        }
      }
      cracked = false;
    }
  }

  public void setB1001Usec(int n) {
    // Change #frames in framce count in B1ockette 1001 and store in buf1001 and data buffer
    crack();
    if (b1001 != null) {
      b1001.setUSecs(n);
      System.arraycopy(b1001.getBytes(), 0, buf1001, 0, 8);
      for (int j = 0; j < blocketteList.length; j++) {
        if (blocketteList[j] == 1001) {
          System.arraycopy(b1001.getBytes(), 0, buf, blocketteOffsets[j], 8);
          break;
        }
      }
      cracked = false;
    }
  }

  public int[] decomp() throws SteimException {
    int[] samples = new int[getNsamp()];
    return decomp(samples, 1.);
  }

  public int[] decomp(int[] samples) throws SteimException {
    return decomp(samples, 1.);
  }

  /**
   * This needs to be kept in accordance with whether decomp() can handle the
   * encoding
   *
   * @param encoding An encoding code
   * @param fscale The suggested scale factor for floating values (set zero if
   * it is not expecting floats)
   * @return True, if the data can be decoded with decomp()
   */
  public static boolean isSupportedEncoding(int encoding, double fscale) {
    switch (encoding) {
      case 1:     // 2 byte int
      case 2:     // 3 byte int
      case 3:     // 4 byte int
      case 11:    // Steim2
      case 10:    // Steim1
      case 16:    // CDSN gain ranged
      case 30:    // SRO
      case 32:    // two byte DWWSN
        return true;
      case 4:
      case 5:
        return (fscale != 0.);
      default:
        //Util.prt("** encoding unsupported : encoding="+encoding);
        return false;
    }
  }

  /**
   * return the decompressed bytes
   *
   * @param samples User provided scratch space for data, must be big enough to
   * hold samples
   * @param fscale If the data type is floating point, scale by this before
   * returning as ints
   * @return The users array with the samples, or null if an error occurred
   * @throws SteimException
   */
  public int[] decomp(int[] samples, double fscale) throws SteimException {
    if (fscale == 0.) {
      fscale = 1.;
    }
    int rev = 0;
    if (frames == null || frames.length < getBlockSize() - dataOffset) {
      frames = new byte[getBlockSize() - dataOffset];
    }
    System.arraycopy(buf, dataOffset, frames, 0, getBlockSize() - dataOffset);
    if (getEncoding() == 10) {
      samples = Steim1.decode(frames, getNsamp(), swap, rev);
    } else if (getEncoding() == 11) {
      Steim2.decode(frames, getNsamp(), samples, swap, rev);

      // Would adding this block "as is" cause a reverse constant error (or steim error)?  If so, restore block
      // to state before adding this one, write it out, and make this block the beginning of next output block
      if (Steim2.hadReverseError() || Steim2.hadSampleCountError()) {
        if (Steim2.hadReverseError()) {
          Util.prta("Decomp  " + Steim2.getReverseError() + " " + toString());
        }
        if (Steim2.hadSampleCountError()) {
          Util.prta("decomp " + Steim2.getSampleCountError() + " " + toString());
        }
        return null;
      }
    } else if (getEncoding() == 30) {
      ms.position(getDataOffset());
      for (int ii = 0; ii < getNsamp(); ii++) {
        int word = ((int) ms.getShort());
        int mantissa = word & 0xfff;
        if ((mantissa & 0x800) == 0x800) {
          mantissa = mantissa | 0xFFFFF000;  // Do sign
        }
        int exp = (word >> 12) & 0xf;
        //Util.prt(ii+" m="+mantissa+" exp="+exp+" exp2="+(-exp + 10)+" val="+(mantissa << (-exp +10)));
        exp = -exp + 10;                   // The sign is apparently biased by 10
        if (exp > 0) {
          samples[ii] = mantissa << exp;
        } else if (exp < 0) {
          Util.prt("Illegal exponent in SRO exp=" + exp);
          samples[ii] = mantissa >> exp;
        } else {
          samples[ii] = mantissa;
        }
      }
    } else if ((encoding >= 1 && encoding <= 5) || encoding == 32) {  // 32 is DWWSSN which is two bytes twos complement as well
      ms.position(getDataOffset());                // Note: DWWSSN says its gain ranged in SEED manual, but it is not
      for (int ii = 0; ii < getNsamp(); ii++) {
        switch (encoding) {
          case 5:
            //  IEEE doubles
            double d = ms.getDouble();
            Util.prt(ii + " d=" + d);
            samples[ii] = (int) (d * fscale + 0.5);
            break;
          case 1:
          case 32:
            samples[ii] = ms.getShort(); // 2 byte ints
            break;
          case 2:
            // 3 byte ints
            int i2 = ms.getInt();
            ms.position(ms.position() - 1);
            i2 = i2 & 0xffffff;
            if ((i2 & 0x800000) != 0) {
              i2 = i2 | 0xff000000;
            }
            samples[ii] = i2;
            break;
          case 3:
            samples[ii] = ms.getInt(); // 4 byte ints
            break;
          case 4:
            samples[ii] = (int) (ms.getFloat() * fscale + 0.5); // IEEE floats
            break;
          default:
            break;
        }
      }
    } /* Encoding 16 CDSN :
      B030F06          Number of Keys:    4        
      B030F07             Key  1: M0                # non multiplexed
      B030F07             Key  2: W2 D0-13 A-8191   # get two bytes, take 0-13 mantissa, bias -8191 (taken from data
      B030F07             Key  3: D14-15            # exponent in bits 14-15
      B030F07             Key  4: P0:#0,1:#2,2:#4,3:#7 # Multiplier table gaincode:multiplyer 0=1, 1=2^2=4, 2=2^4=16, 3=2^7=128
     */ else if (encoding == 16) { // CDSN gain range encoding
      ms.position(getDataOffset());
      for (int ii = 0; ii < getNsamp(); ii++) {
        int word = ((int) ms.getShort());   // get two bytes
        int mantissa = (word & 0x3fff) - 8191;       // Mantissa is 14 bits - then biased by 8191
        int exp = (word >> 14) & 0x3;           // this is a code
        //Util.prt(ii+" m="+mantissa+" exp="+exp+" mult="+cdsnMultiplier[exp]+" val="+(mantissa * cdsnMultiplier));
        samples[ii] = mantissa * cdsnMultipliers[exp];
      }
    }
    return samples;
  }
  private static int[] cdsnMultipliers = {1, 4, 16, 128};

  public void fixReverseIntegration() {
    try {
      if (frames == null || frames.length < getBlockSize() - dataOffset) {
        frames = new byte[getBlockSize() - dataOffset];
      }
      System.arraycopy(buf, dataOffset, frames, 0, getBlockSize() - dataOffset);
      int[] samples = null;
      int rev = 0;
      if (getEncoding() == 10) {
        samples = Steim1.decode(frames, getNsamp(), swap, rev);
      }
      if (getEncoding() == 11) {
        samples = Steim2.decode(frames, getNsamp(), swap, rev);
      }
      if (samples == null) {
        return;
      }

      // Would adding this block "as is" cause a reverse constant error (or steim error)?  If so, set reverse
      // integration constant from the decompressed dta.
      if (Steim2.hadReverseError()) {

        ms.position(dataOffset + 4);            // position forward integration constant
        //Util.prt("FixReverseIntegration: fwd="+forward+" "+samples[0]+" rev="+reverse+" "+samples[samples.length-1]);
        if (reverse != samples[samples.length - 1]) {
          ms.position(dataOffset + 8);
          ms.putInt(samples[samples.length - 1]);
          reverse = samples[samples.length - 1];      // set new reverse in data section
          samples = decomp();
          if (samples == null) {
            Util.prt("MS.fixReverseIntegration() failed decomp returned null");
          }
          //else Util.prt("FixReverseIntegration: new rev="+samples[samples.length-1]);
        }
      }
    } catch (SteimException e) {
    }
  }

  /**
   * this routine splits a bigger miniseed block into multiple 512. It has two
   * algorithms depending on whether the input block is a aggregate of 512s or
   * not. If it is, the blocks are just disaggregated. If not, the block is
   * decompressed and re-compressed into a series of blocks. In general the
   * block number of the output blocks is 500000+(orig sequ *10) + 512 block
   * number. So block number 1234 has 9 output sequences from 512340 to 512348.
   *
   * @throws IllegalSeednameException if the seedname does not pass muster
   * @return An array with the 512 byte blocks.
   */
  public MiniSeed[] toMiniSeed512() throws IllegalSeednameException {
    crack();
    if (this.getBlockSize() == 512) {
      MiniSeed[] mss = new MiniSeed[1];
      mss[0] = this;
      Util.prt("Attempt to put 512 byte miniseed into 512 byte miniseed!");
      return mss;
    }
    int nout = 0;
    MiniSeed mshdr = null;
    // Build a separate block with only the blockettes in them if the exceed the 16 bytes between 48 and 64
    if (dataOffset > 64) {
      boolean preserve = false;
      for (int i = 0; i < this.nblockettes; i++) {
        if (blocketteList[i] == 1000) {
          continue;    // we preseve these anyway
        }
        if (blocketteList[i] == 1001) {
          continue;
        }
        if (blocketteList[i] == 100) {
          continue;     // This blockette does not seem useful to us, so ditch it.
        }
        preserve = true;
      }
      if (preserve) {
        byte[] bf = new byte[512];
        System.arraycopy(buf, 0, bf, 0, 512);
        ByteBuffer bb = ByteBuffer.wrap(bf);
        bb.order(ms.order());
        bb.position(30);
        bb.putShort((short) 0);   // zap the number of samples
        bb.putShort((short) 0);   // set the data rate to zero
        for (int i = dataOffset; i < 512; i++) {
          bf[i] = 0;    // zero remainder of buffer
        }
        bb.position(44);
        bb.putShort((short) 256);
        mshdr = new MiniSeed(bf);
        mshdr.setB1000Length(512);
        mshdr.setB1001FrameCount(mshdr.getUsedFrameCount());
      }
    }

    // Now prepare the new header that will fit in 64 bytes with B1000 and B1001
    byte[] hdr = new byte[64];
    System.arraycopy(buf, 0, hdr, 0, 64);     // Save the header!
    hdr[0] = '9';
    for (int j = 1; j < 5; j++) {
      hdr[j] = hdr[j + 1];    // new seq = 900000+(oldseq*10)+blk#
    }
    hdr[5] = '0';

    // We only want a B1000 and B1001 (if present), fix up this header
    ByteBuffer bh = ByteBuffer.wrap(hdr);
    bh.position(39);
    bh.put((byte) 1);             // Assume only B1000 will be put back
    bh.position(44);
    bh.putShort((short) 64);      // data offset must be 64
    bh.putShort((short) 48);      // Blockettes now must start at 48
    if (hasBlk1000) {
      bh.put(buf1000);
      bh.position(50);
      bh.putShort((short) 0);   // assume no b1001
    } else {
      Util.prta("Attempt to break up a block without a B1000 " + toString());
      SendEvent.debugSMEEvent("MS512Err", toString(), "MiniSeed");
      return null;
    }
    if (hasBlk1001) {
      bh.position(39);
      bh.put((byte) 2);            // now 2 blockettes B1000 and B1001
      bh.position(50);
      bh.putShort((short) 56);     // link this to the B1000
      bh.position(56);
      bh.put(buf1001);
      bh.position(58);
      bh.putShort((short) 0);
    }

    // There are two types of 4096 point miniseed in the world so far, those built up from 512
    // and those compressed whole as 4096.  We can save the 512s by spliting them up if we find this
    boolean ok = true;
    for (int i = dataOffset; i < getBlockSize(); i = i + 7 * 64) {   // for each key in each 7th frame
      ms.position(i);
      int key = ms.getInt();      // this is a Steim frame key for 1st block of 512
      int v = key & 0xF0000000;   // if this is the start of an original frame, then 1st 2 keys are zero for ICs
      //Util.prt(i+" "+Util.toHex(v));
      if ((key & 0xF0000000) != 0) {
        ok = false;
        break;                    // no need to check more
      }
    }
    if (ok && seedname.charAt(0) == 'I' && seedname.charAt(1) == 'U') {                      // This can be broken up into groups of 7 frames modifying the headers
      //Util.prta("toMS512 DO IU ms="+toString());
      int nfrs = 100;
      if (b1001 != null) {
        nfrs = b1001.getFrameCount();
      }

      MiniSeed[] mss = new MiniSeed[(getBlockSize() / (512 - dataOffset)) + 1];
      if (mshdr != null) {
        mss[nout++] = mshdr;              // Add the hdr only block
      }
      int totsamp = 0;

      // Now build up blocks 7 frames long blocks long
      int hund = husec % 10;
      int i = dataOffset;
      int nleft = 512 - 64;
      int cntFrames = 0;
      // how many bytes are left for data frames
      while (i < getBlockSize()) {
        //for(int i=64; i<getBlockSize(); i=i+7*64) {
        byte[] bf = new byte[512];
        //ByteBuffer bb = ByteBuffer.wrap(bf);
        if (getBlockSize() - i < nleft) {
          for (int j = 0; j < 512; j++) {
            bf[j] = 0;   // on the last short block add prezero
          }
        }
        System.arraycopy(buf, i, bf, 64, Math.min(nleft, getBlockSize() - i));   // Put the 7 or less frames in position
        System.arraycopy(hdr, 0, bf, 0, 64);      // Add the incorrect header
        int nsampbuf = MiniSeed.getNsampFromBuf(bf, getEncoding());          // get number of samples in this compression frame
        if (nsampbuf > 0) {                  // sometimes there really is no more data, do not build zero blocks
          if (nfrs <= cntFrames) {
            Util.prt(" ****** nfrs=" + nfrs + " count=" + cntFrames + " but more data=" + nsampbuf);
          }
          MiniSeed msrec = new MiniSeed(bf);           // create a 512 miniseed for this
          msrec.setNsamp(nsampbuf);
          //GregorianCalendar g = new GregorianCalendar();
          long g = time + ((long) (totsamp / getRate() * 1000. + 0.0001));
          //Util.prt("nsb="+nsampbuf+" totsamp="+totsamp+" rate="+getRate()+" totms="+(totsamp/getRate()*1000.)+
          //        " time="+Util.asctime2(g)+" nout="+nout+" i="+i+" nleft="+nleft+" frms="+cntFrames+" of "+nfrs);
          msrec.setTime(g, hund);
          totsamp += nsampbuf;
          msrec.setB1000Length(512);
          msrec.setB1001FrameCount(msrec.getUsedFrameCount());
          msrec.fixReverseIntegration();
          mss[nout++] = msrec;
          hdr[5]++;
        }
        cntFrames += 7;
        i += nleft;
      }
      if (totsamp != getNsamp()) {
        Util.prt("Suspect break of 4096 miniseed to 512 nsamps do not agree. Use recompress method. nsamp=" + getNsamp() + " found " + totsamp);
        Util.prt(toString());
      } else {
        if (nout < mss.length) {
          MiniSeed[] tmp = new MiniSeed[nout];
          for (i = 0; i < nout; i++) {
            tmp[i] = mss[i];
          }
          //for(i=0; i<tmp.length; i++) Util.prt("ms["+i+"]="+tmp[i].toString());
          return tmp;
        }
        //for(i=0; i<mss.length; i++) Util.prt("ms["+i+"]="+mss[i].toString());
        return mss;
      }
    }

    // Decompress record to make new blocks.
    try {
      //Util.prt("toMS512 decomp/comp method input="+toString().substring(0,60));
      int rev = 0;
      byte[] frames2 = new byte[getBlockSize() - dataOffset];
      System.arraycopy(buf, dataOffset, frames2, 0, getBlockSize() - dataOffset);
      int[] samples = null;
      if (getEncoding() == 10) {
        samples = Steim1.decode(frames2, getNsamp(), swap, rev);
      }
      if (getEncoding() == 11) {
        samples = Steim2.decode(frames2, getNsamp(), swap, rev);
      }

      if (Steim2.hadReverseError() || Steim2.hadSampleCountError()) {
        if (Steim2.hadReverseError()) {
          Util.prta("make512()  " + Steim2.getReverseError());
        }
        if (Steim2.hadSampleCountError()) {
          Util.prta("make512() " + Steim2.getSampleCountError());
        }
      }

      // we now need to recompress the samples, we need to use a putbuf of our own.
      synchronized (globalg) {
        globalg.setTimeInMillis(getTimeInMillis()/1000*1000);   // Time to the nearest second
        int usecs = this.getUseconds();                         // Microseconds of the start time
        int sq = 0;
        for (int i = 1; i < 6; i++) {
          sq = sq * 10 + (buf[i] - '0');
        }
        sq = (sq * 10) % 100000;
        sq += 910000;
        RawToMiniSeed rtms = new RawToMiniSeed(getSeedNameSB(), getRate(), 7,
                globalg.get(Calendar.YEAR), globalg.get(Calendar.DAY_OF_YEAR),
                globalg.get(Calendar.HOUR_OF_DAY) * 3600 + globalg.get(Calendar.MINUTE) * 60 + globalg.get(Calendar.SECOND),
                globalg.get(Calendar.MILLISECOND) * 1000 + (husec % 10) * 100,
                sq, null);
        rtms.setOutputHandler(this);        // This registers our putbuf

        // if a blockette 1001 is present, update the useconds portion and clock quality.
        //int usec = 0;
        int tq = 0;
        if (b1001 != null) {
          tq = b1001.getTimingQuality();
          //usec = b1001.getUSecs();
        }
        ms512 = null;       // Insure this call to process makes a new array of 512s
        if (samples != null) {
          rtms.process(samples, samples.length, globalg.get(Calendar.YEAR), globalg.get(Calendar.DAY_OF_YEAR),
                  globalg.get(Calendar.HOUR_OF_DAY) * 3600 + globalg.get(Calendar.MINUTE) * 60 + globalg.get(Calendar.SECOND),
                  usecs, this.getActivityFlags(), this.getIOClockFlags(), this.getDataQualityFlags(), tq, 0, false);
        }
        rtms.forceOut();
        int totsamp = 0;
        if (ms512 != null) {
          for (MiniSeed ms5121 : ms512) {
            //ms5121.setB1001Usec(usec);    // This is the wrong way now
            totsamp += ms5121.getNsamp();
            //Util.prt("ms512["+i+"]="+ms512[i].toString());
          }
          if (totsamp != getNsamp()) {
            Util.prt("Suspicious 512 break up nsamp mismatch orig=" + getNsamp() + " 512=" + totsamp);
          }

          // If there was a blockette only hdr record created, add it to the beginning.
          if (mshdr != null) {
            MiniSeed[] tmp = new MiniSeed[ms512.length + 1];
            tmp[0] = mshdr;
            System.arraycopy(ms512, 0, tmp, 1, ms512.length);
            //for(int i=0; i<ms512.length; i++) tmp[i+1]=ms512[i];
            ms512 = tmp;
          }
        } else {
          Util.prt(" **** MiniSeed failed to split ms=" + toStringBuilder(null));
        }
      }
      //for(int i=0; i<ms512.length; i++)  Util.prt("ms512["+(i+1)+"] "+ms512[i].toString());
      return ms512;
    } catch (SteimException e) {
      Util.prt("**** block gave steim decode error. " + e.getMessage());
      return null;
    }

  }

  @Override
  public void close() {
  }   // needed to implement a miniseedoutputhandler
  private MiniSeed[] ms512;

  @Override
  public void putbuf(byte[] b, int size) {
    try {
      MiniSeed msin = new MiniSeed(b);
      if (ms512 == null) {
        ms512 = new MiniSeed[1];
      } else {
        MiniSeed[] tmp = new MiniSeed[ms512.length + 1];
        System.arraycopy(ms512, 0, tmp, 0, ms512.length);
        //for(int i=0; i<ms512.length; i++) tmp[i] = ms512[i];
        ms512 = tmp;
      }
      ms512[ms512.length - 1] = msin;
    } catch (IllegalSeednameException e) {
      Util.prt("putbuf building ms512 has IllegalSeednameException " + e.getMessage());
    }

  }

  public static int getNsampFromZeros(byte[] bf, int datoff, int size) throws IllegalSeednameException {
    for (int i = bf.length - 1; i >= datoff; i = i - 2) {
      if (bf[i] != 0 || bf[i - 1] != 0) {
        int nsampEst = (i + 1 - datoff) / 2;
        int ns = MiniSeed.crackNsamp(bf);
        if (ns != (bf.length - datoff) / 2) {
          Util.prt("Short i2 block ns=" + ns + " nsampest=" + nsampEst);
        }
        if (nsampEst == ns) {
          return ns;
        }
        if (Math.abs(nsampEst - ns) < 3) {
          return ns;    // If its close, assume its just some ending zeros
        }
      }
    }
    return (bf.length - datoff) / 2;
  }

  public static int getNsampFromBuf(byte[] bf, int encoding) throws IllegalSeednameException {
    int nsamp = 0;
    synchronized (btmp) {
      System.arraycopy(bf, 0, btmp, 0, bf.length);
      if (swapNeeded(bf, bbtmp)) {
        bbtmp.order(ByteOrder.LITTLE_ENDIAN);
      }
      ByteBuffer bb = bbtmp;
      bb.position(30);
      byte b1 = bb.get();
      byte b2 = bb.get();
      if (b1 == 0 && b2 == 0) {
        return 0;     // no samples in this - must be ACE or LOG etc
      }
      b1 = bb.get();
      b2 = bb.get();
      if (b1 == 0 && b2 == 0) {
        return 0;     // The rate is zero - must be ACE or LOG
      }
      if (swapNeeded(bf)) {
        bb.order(ByteOrder.LITTLE_ENDIAN);
      }
      bb.position(44);
      int datoff = bb.getShort();     // start at the data offset length
      // For fixed field, look for zeros at the end to estimate the number of samples.
      if (encoding == 2) {
        return getNsampFromZeros(bf, datoff, 3);    // 24 bit samples
      }
      if (encoding == 3 || encoding == 4) {
        return getNsampFromZeros(bf, datoff, 4); // ints or floats
      }
      if (encoding == 5) {
        return getNsampFromZeros(bf, datoff, 8);     // doubles
      }
      if (encoding == 30 || encoding == 32 || encoding == 16 || encoding == 1) {
        return getNsampFromZeros(bf, datoff, 2);
      }

      // Steim II, decode the keys to get the number of samples
      switch (encoding) {
        // end of encoding 11
        case 11:
          for (int offset = datoff; offset < bf.length; offset += 64) {
            bb.position(offset);
            int keys = bb.getInt();         // Get "nibbles" or key word
            for (int word = 1; word < 16; word++) {
              int type = (keys >> (15 - word) * 2) & 3;     // put the 2 bit nibble for 1st working word at bottom
              int diffwork = bb.getInt();
              int dnib;                   // This is the upper two bits of working word for certain "type" values
              // The number of bits in the difference is a determinted by the "nibble" in the key
              // for this work (the type here) and possible the "dnib" from top two bits of differences
              // word.  Figure out how many bits from these two data and how much to left shift the working
              // word to put the first difference sign bit in the 32 bit word sign bit.
              switch (type) {
                case 0:
                  if (dbg) {
                    Util.prt("non data!");    // This should never happen'
                  }
                  continue;
                case 1:     // 4 one byte differences
                  nsamp += 4;
                  continue;
                case 2:
                  dnib = diffwork >> 30 & 3;
                  switch (dnib) {
                    case 1:
                      nsamp++;
                      continue;
                    case 2:
                      nsamp += 2;
                      continue;
                    case 3:
                      nsamp += 3;
                      continue;
                  }               // end of case on dnib for type=2
                  continue;
                case 3:
                  dnib = diffwork >> 30 & 3;
                  switch (dnib) {
                    case 0:
                      nsamp += 5;
                      continue;
                    case 1:
                      nsamp += 6;
                      continue;
                    case 2:
                      nsamp += 7;

                  }   // end of case on dnib for type=3;
              }       // end of case on type
            }     // end loop on each word in frame

          }       // end loop on ach frame
          break;
        case 10:
          // Steim I
          for (int offset = datoff; offset < bf.length; offset += 64) {
            bb.position(offset);
            int keys = bb.getInt();         // Get "nibbles" or key word
            for (int word = 1; word < 16; word++) {
              int type = (keys >> (15 - word) * 2) & 3;     // put the 2 bit nibble for 1st working word at bottom
              int diffwork = bb.getInt();
              int dnib = 0;                   // This is the upper two bits of working word for certain "type" values
              switch (type) {
                case 0:
                  continue;                 // this is overhead words like integration constants
                case 1:
                  nsamp += 4;
                  continue;
                case 2:
                  nsamp += 2;
                  continue;
                case 3:
                  nsamp++;
              }
            }
            //Util.prt("at offset="+offset+" nsamp="+nsamp);

          }
          break;
        default:
          Util.prt("Encoding is unknown in getNsampFromBuf() =" + encoding + " " + toStringRaw(bf));
          break;
      }
    }
    return nsamp;
  }

  public static void simpleFixes(byte[] buf) throws IllegalSeednameException {
    // If any nulls are fouind in the seedname, change them to spaces
    if (buf[15] == 'L' && buf[16] == 'O' && buf[17] == 'G') {
      return;
    }
    for (int i = 8; i < 20; i++) {
      if (buf[i] == 0 || buf[i] == '-') {
        buf[i] = ' ';
        //Util.prt("* Replace 0 with space in seedname at pos="+i+" "+(new String(buf, 8,12)));
      }
    }
    for (int i = 0; i < 6; i++) {
      if (buf[i] == ' ') {
        buf[i] = '0';
      }
    }

    // Look for a blockette 1000 and see if the swap agrees.
    ByteBuffer bb = ByteBuffer.wrap(buf);
    boolean swap = swapNeeded(buf);
    if (swap) {
      bb.order(ByteOrder.LITTLE_ENDIAN);
    }
    bb.position(39);          // position # of blockettes that follow
    int nblks = bb.get();       // get it
    if (nblks > 0) {
      bb.position(46);         // position offset to first blockette
      int offset = bb.getShort();
      for (int i = 0; i < nblks; i++) {
        if (offset <= 0 || offset > buf.length) {
          Util.prt("Problem with blockette " + i + " of " + nblks + " offset bad=" + offset + " skip rest ");
          break;
        }
        bb.position(offset);
        int type = bb.getShort();
        int oldoffset = offset;
        offset = bb.getShort();
        if (type == 1000) {
          bb.position(oldoffset + 5);   // this should be word order
          if (bb.get() == 0) {
            if (!swap) {
              bb.position(oldoffset + 5);
              bb.put((byte) 1);
              Util.prt("B1000 say little endian, but swapNeeded() say its big endian. fixed");
            }
          } else {      // blocket 1000 says big endian
            if (swap) {
              bb.position(oldoffset + 5);
              bb.put((byte) 0);
              Util.prt("B1000 say big endian, but swapNeeded() say its little endian.fixed");
            }
          }
        }
      }
    }
  }

  public static boolean isQ680Header(byte[] buf) {
    // Some Q680 "mini-seed" have a header.  From 31-75 this is a time-span in ascii, look for this and skip it if found

    boolean hdr = true;
    for (int ii = 31; ii < 75; ii++) {
      if (!((buf[ii] >= 48 && buf[ii] <= 57) || buf[ii] == '.' || buf[ii] == ',' || buf[ii] == ':' || buf[ii] == 126)) {
        hdr = false;
        break;
      }
    }
    return hdr;
  }

  /**
   * given a rate in Hz return the Band code per SEED
   *
   * @param rate In HZ
   * @param shortPeriod If true, use short period band code instead of BroadBand
   * @return the band code
   */
  public static String getBandFromRate(double rate, boolean shortPeriod) {
    if (rate >= 10 && rate < 80.) {
      if (shortPeriod) {
        return "S";
      }
      return "B";
    } else if (rate == 1.) {
      return "L";
    } else if (rate >= 80.) {
      if (shortPeriod) {
        return "E";
      }
      return "H";
    } else if (rate >= 250. && rate < 500) {
      if (shortPeriod) {
        return "D";
      }
      return "C";
    } else if (rate >= 250. && rate < 1000) {
      return "D";
    } else if (rate >= 1000. && rate < 5000.) {
      if (shortPeriod) {
        return "G";
      }
      return "F";
    } else if (rate == 0.1) {
      return "V";
    } else if (rate <= 0.001) {
      return "R";
    } else if (rate < 0.1) {
      return "U";
    }
    return "X";
  }

  public static void main(String[] args) {
    Util.setModeGMT();

    StringBuilder sb = new StringBuilder(12);
    StringBuilder sb2 = new StringBuilder(12);
    sb.append("UUCWU  EHZ01");
    byte[] buf = new byte[512];
    buf[8] = (byte) sb.charAt(2);
    buf[9] = (byte) sb.charAt(3);
    buf[10] = (byte) sb.charAt(4);
    buf[11] = (byte) sb.charAt(5);
    buf[12] = (byte) sb.charAt(6);
    buf[13] = (byte) sb.charAt(10);
    buf[14] = (byte) sb.charAt(11);
    buf[15] = (byte) sb.charAt(7);
    buf[16] = (byte) sb.charAt(8);
    buf[17] = (byte) sb.charAt(9);
    buf[18] = (byte) sb.charAt(0);
    buf[19] = (byte) sb.charAt(1);

    //long val = MiniSeed.getLongFromSeedname(sb);
    long val2 = Util.getHashFromBuf(buf, 8);
    //Util.prt("val=" + val + " val2=" + val2 + " " + (val == val2));
    //MiniSeed.getSeednameFromLong(val, sb2);
    Util.prt(sb + "|" + sb2 + "|" + Util.compareTo(sb, sb2));
    Util.clear(sb).append("UUDWU  EHZ01");
    //long val3 = MiniSeed.getLongFromSeedname(sb);
    long val4 = Util.getHashFromSeedname(sb);
    //Util.prt("val3=" + val3 + " val-val3=" + (val - val3) + " val4=" + val4 + " val-val4=" + (val - val4));
    int blocklen = 512;
    Util.init("edge.prop");
    IndexFile.init();
    long last;
    int start = 0;
    boolean errorsOnly = false;
    boolean testCrack = false;
    boolean test512 = false;
    Util.setModeGMT();
    if (args.length == 0) {
      Util.prt("MiniSeed [-b nnn] [-dbg] [-err] [-512]  [-i yyyy doy] filenames");
      Util.prt(" -b nnnnn Set block size");
      Util.prt(" -dbg     Set voluminous output");
      Util.prt(" -err     Set errors only must appear before -i");
      Util.prt(" -d n     Read data when doing a -i must appear before -i");
      Util.prt(" -c       Test the static crack routine on the files");
      Util.prt("          n=0 no read,  1= read print errors, 2=read and display");
      Util.prt(" -i yyyy doy  Dump the index");

    }
    int readData = 0;
    String seedMask = "";
    for (int i = 0; i < args.length; i++) {
      //Util.prt(" arg="+args[i]+" i="+i);
      if (args[i].equals("-b")) {
        blocklen = Integer.parseInt(args[i + 1]);
        start = i + 2;
      }
      if (args[i].equals("-dbg")) {
        setDebug(true);
        start = i + 1;
      }
      if (args[i].equals("-err")) {
        errorsOnly = true;
        start = i + 1;
      }
      if (args[i].equals("-s")) {
        seedMask = args[i + 1];
      }
      if (args[i].equals("-d")) {
        readData = Integer.parseInt(args[i + 1]);
      }
      if (args[i].equals("-c")) {
        testCrack = true;
        start = i + 1;
      }
      if (args[i].equals("-512")) {
        test512 = true;
        start = i + 1;
      }
      if (args[i].equals("-i")) {
        int year = Integer.parseInt(args[i + 1]);
        int doy = Integer.parseInt(args[i + 2]);
        int julian = SeedUtil.toJulian(year, doy);
        Util.prt("analyze errorsOnly=" + errorsOnly + " readData=" + readData);
        Util.prt(IndexFile.analyze(julian, true, errorsOnly, readData, seedMask, false).toString());
        System.exit(0);

      }
    }
    int iblk = 0;
    //Util.prt("block len="+blocklen+" start="+start+" end="+(args.length-1));

    RawDisk rw = null;
    long lastlength = 0;
    String lastString = "";
    long diff;
    long time;
    MiniSeed ms;
    for (int i = start; i < args.length; i++) {
      Util.prt("Open file : " + args[i]);
      last = 0;
      RawDisk raw512 = null;
      int blk512 = 0;
      if (test512) {
        try {
          raw512 = new RawDisk(args[i] + ".512", "rw");
        } catch (FileNotFoundException e) {
          Util.prt("Cannot open .512 output file=" + e.getMessage());
          System.exit(1);
        }
      }
      try {
        rw = new RawDisk(args[i], "r");
        iblk = 0;
        for (;;) {
          rw.readBlock(buf, iblk, 512);
          //Util.prt("read bloc len="+buf.length+" iblk="+iblk);
          if (buf[0] == 0 && buf[1] == 0) {
            iblk++;
            continue;
          }

          // Bust the first 512 bytes to get all the header and data blockettes - data
          // may not be complete!  If the block length is longer, read it in complete and
          // bust it appart again.
          ms = new MiniSeed(buf);
          if (ms.getBlockSize() > 512) {
            if(buf.length < ms.getBlockSize()) buf = new byte[ms.getBlockSize()];
            rw.readBlock(buf, iblk, ms.getBlockSize());
            ms = new MiniSeed(buf);
          }

          // List a line about the record unless we are in errors only mode
          if (!errorsOnly) {
            Util.prt(ms.toString() + " iblk=" + iblk + "/" + ms.getBlockSize());
          }
          time = ms.getTimeInMillis();

          // if this is a trigger or other record it will have no samples (but a time!) so
          // do not use it for discontinuity decisions
          if (ms.getNsamp() > 0) {
            if (last != 0) {
              diff = time - last - lastlength;
              if (diff > 1) {
                if (errorsOnly) {
                  Util.prt(lastString);
                  Util.prt(ms.toString() + " iblk=" + iblk + "/" + ms.getBlockSize());
                }
                Util.prt("   ***** discontinuity = " + diff + " ms. ends at " + ms.getTimeString()
                        + " blk=" + iblk);
              }
            }
            lastlength = (long) (ms.getNsamp() * 1000 / ms.getRate());
            last = time;
            lastString = ms.toString() + " iblk=" + iblk + "/" + ms.getBlockSize();
          }
          if (testCrack) {

            int[] tm = MiniSeed.crackTime(buf);
            Util.prt("ms=" + ms.toString());
            Util.prt("Crack " + MiniSeed.crackSeedname(buf) + " rt=" + MiniSeed.crackRate(buf)
                    + " jul=" + MiniSeed.crackJulian(buf) + " ns=" + MiniSeed.crackNsamp(buf) + " time="
                    + tm[0] + ":" + tm[1] + ":" + tm[2] + "." + tm[3]);
          }
          if (test512) {
            MiniSeed[] ms512 = ms.toMiniSeed512();
            for (i = 0; i < ms512.length; i++) {
              Util.prt("   512[" + i + "]=" + ms512[i]);
              if (raw512 != null) {
                raw512.writeBlock(blk512++, ms512[i].getBuf(), 0, 512);
              }
              try {
                ms512[i].decomp();
              } catch (SteimException e) {
                Util.prt("   *** steim exception thrown e=" + e);
              }
              if (Steim2.hadReverseError()) {
                Util.prt("    ***** had reverse error=" + Steim2.getReverseError());
              }
            }
          }
          // Print out the console long if any is received.
          if (ms.getSeedNameString().substring(7, 10).equals("LOG") && ms.getNsamp() > 0 && !errorsOnly) {
            Util.prt("data=" + new String(ms.getData(ms.getNsamp())));
          }

          // if reported Blck 1000 length is zero, use the last known block len
          if (ms.getBlockSize() <= 0) {
            iblk += blocklen / 512;
          } else {
            iblk += ms.getBlockSize() / 512;
            blocklen = ms.getBlockSize();
          }
        }       // end of read new header for(;;)
      } catch (IllegalSeednameException e) {
        Util.prt("Main() got illegalseednameexcepion" + e.getMessage());
      } catch (IOException e) {
        if (e.getMessage() != null) {
          Util.prt("IOException " + e.getMessage());
        } else {
          Util.prt("EOF Nblocks=" + iblk);
        }
        try {
          if (rw != null) {
            rw.close();
          }
        } catch (IOException e2) {
        }

      }
    }
  }

  /**
   * This converts a SEEDname in the order of a MiniSeed packet to its long
   * representation
   *
   * @param s An array with the miniseed raw bytes
   * @param off The offset into the array to start (normally 8), if off <0, 8 is
   * @return thhe resulting long.
   */
  /*public static long getLongFromBuf(byte[] s, int off) {
    int j;
    int c;
    long val = 0;
    long mult = 1;
    if (off < 0) {
      off = 8;
    }
    for (int i = 0; i < 12; i++) {
      c = s[off + i];
      if (c == 0) {
        c = ' ';
      }
      if (c == ' ') {
        j = 36;
      } else if (c >= 'A') {
        j = c - 'A' + 10;
      } else {
        j = c - '0';
      }
      if (i != 7) {
        val = val * 37 + j;
      } else {
        if (c >= 'a') {
          mult = -1;
          j = c - 'a';    // convert to uppercase
        }
        val = val * 37 + j;
      }   // correct for lower case in position 10
    }
    return mult * val;
  }*/
  //private static int[] seedswap = {2, 3, 4, 5, 6, 10, 11, 7, 8, 9, 0, 1};  // This is the order to make miniseed header to NNSSSSSCCCLL
  //private static int[] swapseed = {1, 0, 11, 10, 9, 8, 7, 4, 3, 2, 6, 5}; // When it comes off in reverse order, positions to load into the string

  /*public static long getLongFromSeedname(String s) {
    int j;
    char c;
    int len = s.length();
    long val = 0;
    long mult = 1;
    for (int i = 0; i < 12; i++) {
      if (seedswap[i] < len) {
        c = s.charAt(seedswap[i]);
      } else {
        c = ' ';
      }
      if (c == ' ' || c == '-' || c == 0) {
        j = 36;
      } else if (c >= 'A') {
        j = c - 'A' + 10;
      } else {
        j = c - '0';
      }
      if (i != 7) {
        val = val * 37 + j;
      } else {
        if (c >= 'a') {
          mult = -1;
          j = c - 'a';    // convert to uppercase
        }
        val = val * 37 + j;
      }   // correct for lower case in position 10
    }
    return mult * val;
  }*/

 /* public static long getLongFromSeedname(CharSequence s) {
    int j;
    char c;
    int len = s.length();
    long val = 0;
    long mult = 1;
    for (int i = 0; i < 12; i++) {
      if (seedswap[i] < len) {
        c = s.charAt(seedswap[i]);
      } else {
        c = ' ';
      }
      if (c == ' ' || c == '-' || c == 0) {
        j = 36;
      } else if (c >= 'A') {
        j = c - 'A' + 10;
      } else {
        j = c - '0';
      }
      if (i != 7) {
        val = val * 37 + j;
      } else {
        if (c >= 'a') {
          mult = -1;
          j = c - 'a';    // convert to uppercase
        }
        val = val * 37 + j;
      }   // correct  for lower case in position 10
    }
    return mult * val;
  }
  public static char[] sfl = new char[12];

  public static synchronized void getSeednameFromLong(long val, StringBuilder s) {
    long j;
    char c;
    int mult = 1;
    if (val < 0) {
      mult = -1;
      val = -val;
    }
    if (s.length() > 0) {
      s.delete(0, s.length());
    }
    for (int i = 11; i >= 0; i--) {
      j = val % 37;
      val = val / 37;
      if (i == 7) {
        if (mult < 0) {
          j = j + 37;
        }
      }
      if (j == 36) {
        c = ' ';
      } else if (j < 10) {
        c = (char) (j + '0');
      } else if (j < 36) {
        c = (char) (j - 10 + 'A');
      } else {
        c = (char) (j - 37 + 'a');
      }
      sfl[11 - i] = c;
    }
    for (int i = 0; i < 12; i++) {
      s.append(sfl[swapseed[i]]);
    }
  }*/
}
