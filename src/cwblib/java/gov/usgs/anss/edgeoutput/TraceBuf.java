/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.edgeoutput;

import gov.usgs.anss.util.Util;
import gov.usgs.anss.edge.TimeSeriesBlock;
import gov.usgs.anss.edge.RawDisk;
import gov.usgs.anss.ew.*;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.GregorianCalendar;

/**
 * represents a Earthworm Trace Buf packet. It has a binary buffer of byes and ByteBuffer wrapper
 * for manipulation and byte swapping. It implements the TimeSeriesBlock interface so it can be used
 * anywhere generalized time series objects are desired - in particular they can be stored in
 * ChannelHolders.
 * <pre>
 * The header on the wire is :
 * 0   msgInst    Message institution
 * 1   msgType    A type of message
 * 2   modId      A module ID
 * 3   fragNum    The packet number of the packet, 0=first
 * 4   msgSeqNum  The message sequence number
 * 5   lastOfMsg  Flag indicating this is the last packet in the message
 * Trace buffers
 * 0 int    pinno
 * 4 int    nsamp
 * 8 double starttime
 * 16 double endtime
 * 24 double samprate
 * 32 char[7] station
 * 39 char[9] network
 * 48 char[5] chan      // Non-version 2.0
 * 52 char[3] loc
 * 55 char[2] version   // '2' and '0' if version 2.0, otherwise 1.0 assumed
 * 57 char[3] data type
 * 60 char[2] quality
 * 62 char[2] pad
 * </pre>
 *
 * @author davidketchum
 */
public final class TraceBuf extends TimeSeriesBlock {

  /**
   * Institution code assigned to the NSN
   */
  public static final int INST_USNSN = 13;      // NSN institution code
  /**
   * The module code for data from this
   */
  public static final int MODULE_EDGE = 1;      // module code (at least for testing) 
  /**
   * Length of the header portion of the UDP packet which is a TraceBuf
   */
  public final static int TRACE_HDR_LENGTH = 70;
  /**
   * The theoretical longest trace buf- to be this long it would have to be fragmented and
   * reassembled
   */
  public final static int TRACE_DATA_LENGTH = 4096;
  /**
   * the maximum trace buf length - to be this long it would have to be fragmented and reassembled
   */
  public final static int TRACE_LENGTH = TRACE_HDR_LENGTH + TRACE_DATA_LENGTH;
  /**
   * For edge to coax the maximum samples in a TraceBuf - this guarantees to fragmentation packets
   */
  public final static int TRACE_MAX_NSAMPS = 320;
  /**
   * maximum length of a edge-to-coax reflecting its design to never use fragmentation
   */
  public final static int TRACE_ONE_BUF_LENGTH = TRACE_HDR_LENGTH + TRACE_MAX_NSAMPS * 4;
  /**
   * The is the earthworm assigned "type" of a trace buf packet
   */
  public final static int TRACEBUF_TYPE = 19;  // tracebuf 2, tracebuf 1 is 20
  public final static int TRACEBUF1_TYPE = 20;
  public final static int GLOBAL_PICK_TYPE = 228;

  /**
   * these are the offsets in the 70 byte header (6 bytes general header and 64 bytes TraceBuf header)
   */
  private final static int OFF_INST = 0;
  private final static int OFF_TYPE = 1;
  private final static int OFF_MOD = 2;
  private final static int OFF_FRAGNO = 3;
  private final static int OFF_SEQNO = 4;
  private final static int OFF_LAST = 5;

  private final static int OFF_PINNO = 6;
  private final static int OFF_NSAMP = 10;
  private final static int OFF_STARTTIME = 14;
  private final static int OFF_ENDTIME = 22;
  private final static int OFF_RATE = 30;
  private final static int OFF_STATION = 38;
  private final static int OFF_NETWORK = 45;
  private final static int OFF_CHAN = 54;
  private final static int OFF_LOCATION = 58;
  private final static int OFF_VERSION = 61;
  private final static int OFF_DATATYPE = 63;
  private final static int OFF_QUALITY = 66;
  private final static int OFF_PAD = 68;
  private final static int OFF_DATA = 70;
  /**
   * this is the earthworm published maximum UDP packet size before fragmention is done
   */
  public final static int UDP_SIZ = 1472;
  private final byte[] s4 = "s4".getBytes();
  StringBuilder seedname = new StringBuilder(12); // working space for seedname
  String seednameString;

  boolean cleared;          // if true, this packet has been last cleared (is available)
  byte[] buf;              // Buffer space for this trace buf
  ByteBuffer bb;            // Wraps the buf
  boolean scn;              // if true, format data for SCN 1.0 and not SCNL
  //long begin;               // Time in ms of start of data
  //long end;                 // Time in ms of end of buffer
  //GregorianCalendar begin;  // Start time of buffer per TraceBuf
  //GregorianCalendar end;    // last sample time per TraceBuf
  ByteOrder normal;         // The byte order on this system
  int msglength;            // Length of non-tracebuf messages

  public boolean getSCNMode() {
    return scn;
  }

  /**
   * set the override to SCN mode (default is SCNL)
   *
   * @param t If true, set SCN mode in encoding routines.
   */
  public void setSCNMode(boolean t) {
    scn = t;
  }

  /**
   * return the time of the next sample after this tracebuf
   *
   * @return The time in millis of the next sample
   */
  @Override
  public long getNextExpectedTimeInMillis() {
    bb.position(OFF_ENDTIME);
    double endd = bb.getDouble();
    bb.position(OFF_RATE);
    double rate = bb.getDouble();
    return (long) ((endd + 1. / rate) * 1000 + 0.5);
  }

  /**
   * return the time of the next sample after this tracebuf
   *
   * @return The time in millis of the next sample
   */
  @Override
  public long getLastTimeInMillis() {
    bb.position(OFF_ENDTIME);
    double endd = bb.getDouble();
    bb.position(OFF_RATE);
    return (long) (endd * 1000 + 0.5);
  }

  /**
   * return the time of first sample in millis
   *
   * @return The time of the first sample in millis
   */
  @Override
  public long getTimeInMillis() {
    bb.position(OFF_STARTTIME);
    double start = bb.getDouble();
    return (long) (start * 1000. + 0.5);
  }

  /**
   * return start time as Gregorian
   *
   * @return a new gregorian with the time
   */
  @Override
  public GregorianCalendar getGregorianCalendar() {
    GregorianCalendar g = new GregorianCalendar();
    g.setTimeInMillis(getTimeInMillis());
    return g;
  }

  /**
   * return the block size of this tracebuf (70*nsamp*4)
   *
   * @return The blocksize in bytes.
   */
  @Override
  public int getBlockSize() {
    bb.position(OFF_NSAMP);
    return 70 + bb.getInt() * 4;
  }

  /**
   * return the seedname NSCL 12 bytes
   *
   * @return the Seed name
   */
  @Override
  public String getSeedNameString() {
    return getSeedname();
  }

  /**
   * clear the buf to zeros
   */
  @Override
  public void clear() {
    for (int i = 0; i < buf.length; i++) {
      buf[i] = 0;
    }
    cleared = true;
  }

  /**
   * return true if data is cleared
   *
   * @return If buffer is cleared
   */
  @Override
  public boolean isClear() {
    return cleared;
  }

  /**
   * set a module number in the buffer
   *
   * @param module The module number to set (stored as 8 bit byte)
   */
  public void setModule(int module) {
    bb.position(OFF_MOD);
    bb.put((byte) module);
  }

  /**
   * set data in this tracebuf - this is used so a tracebuf can be reused by updating its contents
   * and not allocating new memory. The trace buf type is set based on the scn flag (see
   * setSCNMode). If the flag is set the trace buf will be SCN type, if the flag is not set it will
   * be a type 2 SNCL packet.
   *
   * @param seedname The 12 character seed name
   * @param start the milliseconds since 1970 of the start time
   * @param nsamp the number of data samples.
   * @param rate The data rate in Hz
   * @param samples array of 32 bit ints with the time series
   * @param off The offset into the samples for the start of data
   * @param institution The institution code to set
   * @param module The module number
   * @param seqno The sequence number of the sample (will be 8 bit when done)
   * @param pinno The PINNO for this channel
   * @throws IndexOutOfBoundsException if the array in is bigger than the tracebuf size
   */
  public void setData(String seedname, long start, int nsamp, double rate, int[] samples, int off,
          int institution, int module, int seqno, int pinno) {
    Util.clear(tmpseed).append(seedname);
    while (tmpseed.length() < 12) {
      tmpseed.append(" ");
    }
    setData(tmpseed, start, nsamp, rate, samples, off, institution, module, seqno, pinno);
  }
  StringBuilder tmpseed = new StringBuilder(12);

  /**
   * set data in this tracebuf - this is used so a tracebuf can be reused by updating its contents
   * and not allocating new memory. The trace buf type is set based on the scn flag (see
   * setSCNMode). If the flag is set the trace buf will be SCN type, if the flag is not set it will
   * be a type 2 SNCL packet.
   *
   * @param seedname The 12 character seed name
   * @param start the milliseconds since 1970 of the start time
   * @param nsamp the number of data samples.
   * @param rate The data rate in Hz
   * @param samples array of 32 bit ints with the time series
   * @param off The offset into the samples for the start of data
   * @param institution The institution code to set
   * @param module The module number
   * @param seqno The sequence number of the sample (will be 8 bit when done)
   * @param pinno The PINNO for this channel
   * @throws IndexOutOfBoundsException if the array in is bigger than the tracebuf size
   */
  public void setData(StringBuilder seedname, long start, int nsamp, double rate, int[] samples, int off,
          int institution, int module, int seqno, int pinno) {
    for (int i = 0; i < TRACE_HDR_LENGTH; i++) {
      buf[i] = 0;
    }
    bb.position(0);
    bb.put((byte) institution);
    if (scn) {
      bb.put((byte) (TRACEBUF_TYPE + 1));
    } else {
      bb.put((byte) TRACEBUF_TYPE);
    }
    bb.put((byte) module);
    bb.put((byte) 0);       // fragment
    bb.put((byte) seqno);
    bb.put((byte) 1);         /// last is always true
    bb.putInt(pinno);
    bb.putInt(nsamp);
    cleared = false;
    double startTime = start / 1000.;
    bb.putDouble(startTime);
    bb.putDouble(startTime + (nsamp - 1) / rate);
    bb.putDouble(rate);
    bb.put((byte) sp2chr(seedname.charAt(2), 0));
    bb.put((byte) sp2chr(seedname.charAt(3), 0));
    bb.put((byte) sp2chr(seedname.charAt(4), 0));
    bb.put((byte) sp2chr(seedname.charAt(5), 0));
    bb.put((byte) sp2chr(seedname.charAt(6), 0));
    //bb.put(seedname.substring(2,7).trim().getBytes());
    bb.position(OFF_NETWORK);
    bb.put((byte) sp2chr(seedname.charAt(0), 0));
    bb.put((byte) sp2chr(seedname.charAt(1), 0));
    //bb.put(seedname.substring(0,2).trim().getBytes());
    bb.position(OFF_CHAN);
    bb.put((byte) sp2chr(seedname.charAt(7), 0));
    bb.put((byte) sp2chr(seedname.charAt(8), 0));
    bb.put((byte) sp2chr(seedname.charAt(9), 0));
    //bb.put(seedname.substring(7,10).trim().getBytes());
    Util.clear(this.seedname).append(seedname);
    seednameString = null;
    if (!scn) {
      bb.position(OFF_LOCATION);
      fixLocationCode();
      bb.put((byte) sp2chr(seedname.charAt(10), '-'));
      bb.put((byte) sp2chr(seedname.charAt(11), '-'));
      //bb.put(seedname.substring(10,12).trim().getBytes());
      bb.position(OFF_VERSION);
      bb.put((byte) 50);
      bb.put((byte) 48);    // '2' and then '0'
    }
    bb.position(OFF_DATATYPE);
    bb.put(s4);
    bb.position(OFF_DATA);
    for (int i = 0; i < nsamp; i++) {
      bb.putInt(samples[off + i]);
    }

  }

  public void makeSCNFromSCNL() {
    bb.position(1);
    bb.put((byte) (TRACEBUF_TYPE + 1));// set tracebug type to normal SCN tracebuf
    bb.position(OFF_VERSION);   // Set to version 10
    bb.put((byte) 49);
    bb.put((byte) 48);
    bb.position(OFF_LOCATION);  // Set location bytes to zero
    bb.put((byte) 0);
    bb.put((byte) 0);

  }

  /**
   * Creates a new instance of TraceBuf
   *
   * @param b This is the buffer to build a tracebuf from. It is copied into the tracebuf.
   */
  public TraceBuf(byte[] b) {
    buf = new byte[Math.max(TRACE_LENGTH, b.length)];
    System.arraycopy(b, 0, buf, 0, b.length);
    bb = ByteBuffer.wrap(buf);
    normal = bb.order();
    //begin= new GregorianCalendar();
    //end = new GregorianCalendar();
    cleared = false;
    fixLocationCode();
  }

  /**
   * create a new and empty trace buf
   */
  public TraceBuf() {
    buf = new byte[TRACE_LENGTH];
    bb = ByteBuffer.wrap(buf);
    normal = bb.order();
    //begin= new GregorianCalendar();
    //end = new GregorianCalendar();
    cleared = false;
    fixLocationCode();
  }

  /**
   * create a new and empty trace buf
   *
   * @param nsamp Number of samples to reserve space for
   */
  public TraceBuf(int nsamp) {
    buf = new byte[TRACE_HDR_LENGTH + nsamp * 4];
    bb = ByteBuffer.wrap(buf);
    normal = bb.order();
    //begin= new GregorianCalendar();
    //end = new GregorianCalendar();
    cleared = false;
    fixLocationCode();
  }

  /**
   * set data in this tracebuf - this is used so a tracebuf can be reused by updating its contents
   * and not allocating new memory. If the data buffer has version '2' (dec 50) set, then the scn
   * flag for this TraceBuf is set. Note : This can lead to inadvertant changes of SCN flag if this
   * packet is reused for other set data. Only use this routine for packets whose type is known to
   * be right To convert between formats it is better to use the "setData(seedname, etc) which will
   * retain the SCN setting.
   *
   * The length is assumed to be the length of the buffer.
   *
   * @param inbuf The byte buffer to use to replace data in this TraceBuf
   * @throws IndexOutOfBoundsException if the array in is bigger than the tracebuf size
   */
  public void setData(byte[] inbuf) throws IndexOutOfBoundsException {
    setData(inbuf, 0, inbuf.length);
  }

  /**
   * set the location code. If the code is not set in the buffer (is not a digit or upper case
   * character) use dash instead
   */
  private void fixLocationCode() {
    if (!Character.isDigit(buf[OFF_LOCATION]) && !Character.isUpperCase(buf[OFF_LOCATION])) {
      buf[OFF_LOCATION] = '-';
    }
    if (!Character.isDigit(buf[OFF_LOCATION + 1]) && !Character.isUpperCase(buf[OFF_LOCATION + 1])) {
      buf[OFF_LOCATION + 1] = '-';
    }

  }

  /**
   * set data in this UDP tracebuf - this is used so a tracebuf can be reused by updating its
   * contents and not allocating new memory. If the data buffer has version '2' (dec 50) set, then
   * the scn flag for this TraceBuf is set. Note : This can lead to inadvertant changes of SCN flag
   * if this packet is reused for other set data. Only use this routine for packets whose type is
   * known to be right To convert between formats it is better to use the "setData(seedname, etc)
   * which will retain the SCN setting.
   *
   * @param inbuf The byte buffer to use to replace data in this TraceBuf
   * @throws IndexOutOfBoundsException if the array in is bigger than the tracebuf size
   * @param len The length of inbuf in bytes.
   */
  public void setData(byte[] inbuf, int len) throws IndexOutOfBoundsException {
    setData(inbuf, 0, len);
  }

  /**
   * set data in this UDP tracebuf - this is used so a tracebuf can be reused by updating its
   * contents and not allocating new memory. If the data buffer has version '2' (dec 50) set, then
   * the scn flag for this TraceBuf is set. Note : This can lead to inadvertant changes of SCN flag
   * if this packet is reused for other set data. Only use this routine for packets whose type is
   * known to be right To convert between formats it is better to use the "setData(seedname, etc)
   * which will retain the SCN setting.
   *
   * @param inbuf The byte buffer to use to replace data in this TraceBuf
   * @param off The offset in tracebuf where this copy is to go
   * @throws IndexOutOfBoundsException if the array in is bigger than the tracebuf size
   * @param len The length of inbuf in bytes.
   */
  public void setData(byte[] inbuf, int off, int len) throws IndexOutOfBoundsException {
    setData(inbuf, 0, off, len);
  }
  /**
   * set data in this UDP tracebuf - this is used so a tracebuf can be reused by updating its
   * contents and not allocating new memory. If the data buffer has version '2' (dec 50) set, then
   * the scn flag for this TraceBuf is set. Note : This can lead to inadvertant changes of SCN flag
   * if this packet is reused for other set data. Only use this routine for packets whose type is
   * known to be right To convert between formats it is better to use the "setData(seedname, etc)
   * which will retain the SCN setting.
   *
   * @param inbuf The byte buffer to use to replace data in this TraceBuf
   * @param inbufoff The offset in Inbuf of the start of the copy
   * @param off The offset in tracebuf where this copy is to go
   * @throws IndexOutOfBoundsException if the array in is bigger than the tracebuf size
   * @param len The length of inbuf in bytes.
   */
  int nbigger;

  public void setData(byte[] inbuf, int inbufoff, int off, int len) throws IndexOutOfBoundsException {
    if (buf.length < off + len) {
      byte[] tmp = new byte[(off + len) * 2];   // build a bigger buffer
      System.arraycopy(buf, 0, tmp, 0, buf.length);
      Util.prt("   *** Making a tracebuf bigger was " + buf.length + " off=" + off + " len=" + len);
      nbigger++;
      if (nbigger % 1000 == 2) {
        new RuntimeException("Make tracebuf bigger! ").printStackTrace(Util.getOutput());
      }
      buf = tmp;
      bb = ByteBuffer.wrap(buf);
    }
    System.arraycopy(inbuf, inbufoff, buf, off, len);
    if (buf[OFF_TYPE] != EWMessage.TYPE_TRACEBUF && buf[OFF_TYPE] != EWMessage.TYPE_TRACEBUF2) {
      msglength = off + len;
      return;
    }
    ByteOrder shouldBe;
    String type = getDataType();
    if (type.equals("i4") || type.equals("i2") || type.equals("f4") || type.equals("f8")) {
      shouldBe = ByteOrder.LITTLE_ENDIAN;
    } else {
      shouldBe = ByteOrder.BIG_ENDIAN;
    }
    bb.order(shouldBe);
    cleared = false;
    //Util.prt("order is " +normal+" set to "+shouldBe+" for "+type);
    if (len > buf.length) {
      throw new IndexOutOfBoundsException("len=" + len + " in len=" + inbuf.length + " out len=" + buf.length);
    }
    fixLocationCode();
    bb.position(OFF_VERSION);
    byte vers = bb.get();
    scn = vers != 50;
    Util.clear(seedname);
    seednameString = null;
  }

  /**
   * set data in this tracebuf - this is used so a tracebuf can be reused by updating its contents
   * and not allocating new memory. If the data buffer has version '2' (dec 50) set, then the scn
   * flag for this TraceBuf is set. Note : This can lead to inadvertant changes of SCN flag if this
   * packet is reused for other set data. Only use this routine for packets whose type is known to
   * be right To convert between formats it is better to use the "setData(seedname, etc) which will
   * retain the SCN setting.
   *
   * @param inbuf The byte buffer to use to replace data in this TraceBuf
   * @param inbufoff The offset in Inbuf of the start of the copy
   * @throws IndexOutOfBoundsException if the array in is bigger than the tracebuf size
   * @param len The length of inbuf in bytes.
   */
  public void setDataMessageFormat(byte[] inbuf, int inbufoff, int len) throws IndexOutOfBoundsException {
    if (buf.length < len) {
      byte[] tmp = new byte[len * 2];   // build a bigger buffer
      System.arraycopy(buf, 0, tmp, 0, buf.length);
      Util.prt("   *** Making a tracebuf bigger was " + buf.length + " len=" + len);
      buf = tmp;
      bb = ByteBuffer.wrap(buf);
    }
    // The logo is different on a message and the frag stuff does not exist
    buf[OFF_INST] = inbuf[EWMessage.LOGO_INSTITUTION_OFFSET + inbufoff];
    buf[OFF_MOD] = inbuf[EWMessage.LOGO_MODULE_OFFSET + inbufoff];
    buf[OFF_TYPE] = inbuf[EWMessage.LOGO_TYPE_OFFSET + inbufoff];
    buf[OFF_FRAGNO] = 0;
    buf[OFF_LAST] = 0;
    buf[OFF_SEQNO] = 0;
    System.arraycopy(inbuf, 3 + inbufoff, buf, 6, len - 3);
    ByteOrder shouldBe;
    String type = getDataType();
    if (type.equals("i4") || type.equals("i2") || type.equals("f4") || type.equals("f8")) {
      shouldBe = ByteOrder.LITTLE_ENDIAN;
    } else {
      shouldBe = ByteOrder.BIG_ENDIAN;
    }
    bb.order(shouldBe);
    cleared = false;
    //Util.prt("order is " +normal+" set to "+shouldBe+" for "+type);
    if (len + 3 > buf.length) {
      throw new IndexOutOfBoundsException("len=" + len + " in len=" + inbuf.length + " out len=" + buf.length);
    }
    fixLocationCode();
    bb.position(OFF_VERSION);
    byte vers = bb.get();
    scn = vers != 50;
    Util.clear(seedname);
    seednameString = null;
  }

  /**
   * set data in this UDP tracebuf - this is used so a tracebuf can be reused by updating its
   * contents and not allocating new memory. If the data buffer has version '2' (dec 50) set, then
   * the scn flag for this TraceBuf is set. Note : This can lead to inadvertant changes of SCN flag
   * if this packet is reused for other set data. Only use this routine for packets whose type is
   * known to be right To convert between formats it is better to use the "setData(seedname, etc)
   * which will retain the SCN setting.
   *
   * The length is assumed to be the length of the buffer.
   *
   * @param inbuf The byte buffer to use to replace data in this TraceBuf
   * @throws IndexOutOfBoundsException if the array in is bigger than the tracebuf size
   */
  @Override
  public void load(byte[] inbuf) {
    setData(inbuf, inbuf.length);
  }

  /**
   * set data in this UDP tracebuf - this is used so a tracebuf can be reused by updating its
   * contents and not allocating new memory. If the data buffer has version '2' (dec 50) set, then
   * the scn flag for this TraceBuf is set. Note : This can lead to inadvertant changes of SCN flag
   * if this packet is reused for other set data. Only use this routine for packets whose type is
   * known to be right To convert between formats it is better to use the "setData(seedname, etc)
   * which will retain the SCN setting.
   *
   * The length is assumed to be the length of the buffer.
   *
   * @param inbuf The byte buffer to use to replace data in this TraceBuf
   * @param nbytes Number of bytes that are valid in inbuf
   * @throws IndexOutOfBoundsException if the array in is bigger than the tracebuf size
   */
  public void load(byte[] inbuf, int nbytes) {
    setData(inbuf, nbytes);
  }

  /**
   * get pinno
   *
   * @return The Pinno
   */
  public int getPinno() {
    bb.position(OFF_PINNO);
    return bb.getInt();
  }

  /**
   * get pinno
   *
   * @return The Pinno
   */
  @Override
  public int getNsamp() {
    bb.position(OFF_NSAMP);
    return bb.getInt();
  }

  @Override
  public String toString() {
    return toStringBuilder(null).toString();
  }
  StringBuilder tmpsb = new StringBuilder(120);

  @Override
  public StringBuilder toStringBuilder(StringBuilder tmp) {
    StringBuilder sb = tmp;
    if (sb == null) {
      sb = Util.clear(tmpsb);
    }
    long begin = getStarttime();
    long end = getEndtime();
    synchronized (sb) {
      sb.append("In=").append(buf[OFF_INST]).append(" ty=").append(buf[OFF_TYPE]).append(" md=").append(buf[OFF_MOD]).
              append(" #=").append(buf[OFF_FRAGNO]).append(" sq=").append(buf[OFF_SEQNO]).
              append(" nofrg=").append(buf[OFF_LAST]).append(" scn=").append(scn).append(" v=").
              append(buf[OFF_VERSION]).append(" ").append(buf[OFF_VERSION + 1]);
      if (buf[OFF_FRAGNO] > 0) {
        return sb;
      }
      if (buf[OFF_TYPE] == EWMessage.TYPE_TRACEBUF || buf[OFF_TYPE] == EWMessage.TYPE_TRACEBUF2) {
        sb.append(" ").append(getNetwork()).append(getStation()).append(getChannel()).
                append(getLocation()).append(" ").append(Util.ascdate(begin).substring(2)).append(" ").
                append(Util.asctime2(begin)).append(" ").append(Util.asctime2(end)).append(" n=").
                append(getNsamp()).append(" rt=");
        if (("" + getRate()).length() > 5) {
          sb.append(("" + getRate()).substring(0, 5)).append("*");
        } else {
          sb.append(getRate()).append(" ").append(getDataType()).append(" pn=").append(getPinno());
        }
        return sb;
      }
      Util.clear(sb).append("Not a traceBuf ***** type=").append(buf[OFF_TYPE]).append(" ");
      if (msglength > 6) {
        sb.append(Util.toAllPrintable(new String(buf, 6, msglength - 6)));
      }
    }
    return sb;
  }

  /**
   * get pinno
   *
   * @return The Pinno
   */
  public long getStarttime() {
    bb.position(OFF_STARTTIME);
    double time = bb.getDouble();
    return (long) (time * 1000. + 0.5);
  }

  /**
   * get pinno
   *
   * @return The Pinno
   */
  public long getEndtime() {
    bb.position(OFF_ENDTIME);
    double time = bb.getDouble();
    return (long) (time * 1000. + 0.5);
  }

  /**
   * get pinno
   *
   * @return The Pinno
   */
  @Override
  public double getRate() {
    bb.position(OFF_RATE);
    return bb.getDouble();
  }

  /**
   * get Rate statically from byte buffer
   *
   * @param b The buffer with a tracebuf.
   * @return The Pinno
   */
  public static double getRate(byte[] b) {
    ByteBuffer bb2 = ByteBuffer.wrap(b);
    bb2.position(OFF_RATE);
    return bb2.getDouble();
  }

  /**
   * set the rate in this packet (usually used in TraceBufListener when an obviously wrong rate has
   * come in.
   *
   * @param r The rate to force into this tracebuf
   */
  public void setRate(double r) {
    bb.position(OFF_RATE);
    bb.putDouble(r);
  }

  /**
   * return the seedname in edge order NSCL
   *
   * @return the 12 character seedname - change '-' to spaces
   */
  public String getSeedname() {
    if (seedname.length() == 0) {
      getSeedNameSB();
    }
    if (seednameString == null) {  // Only construct the seedname once as a StringBuilder
      seednameString = seedname.toString();
    }
    return seednameString;
  }

  @Override
  public StringBuilder getSeedNameSB() {
    if (seedname.length() == 0) {
      seedname.append(getNetwork()).append(getStation()).append(getChannel()).append(getLocation());
      Util.stringBuilderReplaceAll(seedname, "-", " ");
      while (seedname.length() < 12) {
        seedname.append(" ");
      }
    }
    return seedname;
  }

  /**
   * get pinno
   *
   * @return The Pinno
   */
  public String getStation() {
    bb.position(OFF_STATION);
    return (fromCString(bb, 5) + "     ").substring(0, 5);

  }

  /**
   * convert a zero terminated C string to a Java string
   *
   * @param bb The byte buffer positioned to the beginning of the C string
   * @param max The maximum length of the string (field length)
   * @return The resulting String
   */
  private String fromCString(ByteBuffer bb, int max) {
    int pos = bb.position();
    while (bb.get() != 0) {
    }
    int length = bb.position() - pos - 1;
    if (length > max) {
      length = max;
    }
    bb.position(pos);
    byte[] b = new byte[length];
    bb.get(b);
    return new String(b);
  }

  /**
   * Given a 12 character SEED name, set the various portions in the trace buf. If this trace buf is
   * type 2 (SCNL), then also set the location code
   *
   * @param s The 12 character SEED name NSCL order
   */
  public void setSeedname(String s) {
    bb.position(OFF_NETWORK);
    bb.put(s.substring(0, 2).getBytes());
    bb.position(OFF_CHAN);
    bb.put(s.substring(7, 10).getBytes());
    bb.position(OFF_STATION);
    bb.put(s.substring(2, 7).trim().getBytes());
    bb.position(OFF_VERSION);
    if (bb.get() >= '2') {
      bb.position(OFF_LOCATION);
      bb.put(s.substring(10, 12).getBytes());
    }
    seednameString = s;
  }

  /**
   * Given a 12 character SEED name, set the various portions in the trace buf. If this trace buf is
   * type 2 (SCNL), then also set the location code
   *
   * @param s The 12 character SEED name NSCL order
   */
  public void setSeedname(StringBuilder s) {
    bb.position(OFF_NETWORK);
    bb.put((byte) sp2chr(s.charAt(0), 0)).put((byte) sp2chr(s.charAt(1), 0));
    bb.position(OFF_CHAN);
    bb.put((byte) sp2chr(s.charAt(7), 0)).put((byte) sp2chr(s.charAt(8), 0)).put((byte) sp2chr(s.charAt(9), 0));
    bb.position(OFF_STATION);
    bb.put((byte) sp2chr(s.charAt(2), 0)).put((byte) sp2chr(s.charAt(3), 0)).put((byte) sp2chr(s.charAt(4), 0)).put((byte) sp2chr(s.charAt(5), 0)).put((byte) sp2chr(s.charAt(6), 0));
    bb.position(OFF_VERSION);
    if (bb.get() >= '2') {
      bb.position(OFF_LOCATION);
      bb.put((byte) sp2chr(s.charAt(10), '-')).put((byte) sp2chr(s.charAt(11), ' '));
    }
    seednameString = null;
  }

  private static char sp2chr(char c, int with) {
    if (c == ' ') {
      return (char) with;
    }
    return c;
  }

  /**
   * get pinno
   *
   * @return The Pinno
   */
  public String getNetwork() {
    bb.position(OFF_NETWORK);
    return (fromCString(bb, 2) + "  ").substring(0, 2);
  }

  /**
   * get pinno
   *
   * @return The Pinno
   */
  public String getChannel() {
    bb.position(OFF_CHAN);
    return (fromCString(bb, 3) + "   ").substring(0, 3);
  }

  /**
   * get pinno
   *
   * @return The Pinno
   */
  public String getDataType() {
    bb.position(OFF_DATATYPE);
    return (fromCString(bb, 2) + "  ").substring(0, 2);
  }
  private static StringBuilder tmpseed2 = new StringBuilder(12);    // For static getSeedname(byte [])
  private static byte[] outtmp = new byte[12];                     // For static getSeedname(byte [])

  /**
   * from a binary buffer containing a TraceBuf, extract the 12 character seedname. Note : this name
   * is not checked for validity.
   *
   * @param b The binary buffer with a tracebuf
   * @return The 12 character seedname
   */
  public static String getSeedname(byte[] b) {
    return getSeednameSB(b, null).toString();
  }

  /**
   * from a binary buffer containing a TraceBuf, extract the 12 character seedname. Note : this name
   * is not checked for validity.
   *
   * @param b The binary buffer with a tracebuf
   * @param tmp User string builder to use or null to use an internal one
   * @return The 12 character seedname as a StringBuilder
   */
  public synchronized static StringBuilder getSeednameSB(byte[] b, StringBuilder tmp) {
    if (b.length < 60) {
      return null;
    }
    boolean done = false;
    for (int i = OFF_NETWORK; i < OFF_NETWORK + 2; i++) {
      if (b[i] == 0 || done) {
        outtmp[i - OFF_NETWORK] = ' ';
        done = true;
      } else {
        outtmp[i - OFF_NETWORK] = b[i];
      }
    }
    done = false;
    for (int i = OFF_STATION; i < OFF_STATION + 5; i++) {
      if (b[i] == 0 || done) {
        outtmp[i - OFF_STATION + 2] = ' ';
        done = true;
      } else {
        outtmp[i - OFF_STATION + 2] = b[i];
      }
    }
    done = false;
    for (int i = OFF_CHAN; i < OFF_CHAN + 3; i++) {
      if (b[i] == 0 || done) {
        outtmp[i - OFF_CHAN + 7] = ' ';
        done = true;
      } else {
        outtmp[i - OFF_CHAN + 7] = b[i];
      }
    }
    done = false;
    if (b[OFF_VERSION] >= '2') {
      for (int i = OFF_LOCATION; i < OFF_LOCATION + 2; i++) {
        if (b[i] == 0 || done) {
          outtmp[i - OFF_LOCATION + 10] = ' ';
          done = true;
        } else {
          outtmp[i - OFF_LOCATION + 10] = b[i];
        }
      }
    } else {
      outtmp[10] = ' ';
      outtmp[11] = ' ';
    }
    StringBuilder sb = tmp;
    if (sb == null) {
      sb = tmpseed2;
    }
    Util.clear(sb);
    for (int i = 0; i < 12; i++) {
      sb.append((char) outtmp[i]);
    }
    return sb;
  }

  /**
   * return the location code (" ") if not a type 2.0- version
   *
   * @return two character location code or " " if not a SNCL type
   */
  public String getLocation() {
    bb.position(OFF_VERSION);
    int version = bb.get();
    int subversion = bb.get();
    if (version == '2') {
      bb.position(OFF_LOCATION);
      return (fromCString(bb, 2) + "  ").substring(0, 2);
    } //Util.prt("version="+version+"."+subversion);
    return "  ";
  }

  /**
   * get quality short
   *
   * @return The Quality
   */
  public int getQuality() {
    bb.position(OFF_QUALITY);
    return (int) bb.getShort();
  }

  /**
   * return the data as an array of its. The data type of the packet determined decoding.
   *
   * @return The array of ints with the data
   */
  public int[] getData() {
    switch (getDataType()) {
      case "s4":
        return getDataS4();
      case "i2":
        return getDataI2();
      case "i4":
        return getDataI4();
      case "s2":
        return getDataS2();
    }
    Util.prt("TraceBuf: Unknown type =" + getDataType());
    return null;
  }

  /**
   * get the data as s4 encoding
   *
   * @return An array of ints with samples
   */
  public int[] getDataS4() {
    if (!getDataType().equals("s4")) {
      return null;
    }
    int nsamp = getNsamp();
    if (nsamp < 0 || nsamp > 60000) {
      Util.prt("TraceBuf: Bad nsamp in getDataS4=" + nsamp + " hex=" + Util.toHex(nsamp) + " " + toString());
      return new int[1];
    }
    int[] data = new int[Math.max(1, nsamp)];
    bb.position(OFF_DATA);
    for (int i = 0; i < nsamp; i++) {
      data[i] = bb.getInt();
    }
    return data;
  }

  /**
   * get the data as I4 (little endian 4 byte)
   *
   * @return An array of ints with samples
   */
  public int[] getDataI4() {
    if (!getDataType().equals("i4")) {
      return null;
    }
    int[] data = new int[getNsamp()];
    int nsamp = getNsamp();
    bb.position(OFF_DATA);
    for (int i = 0; i < nsamp; i++) {
      data[i] = bb.getInt();
    }
    return data;
  }

  /**
   * get the data as i2 (little endian 2 byte)
   *
   * @return An array of ints with samples
   */
  public int[] getDataI2() {
    if (!getDataType().equals("i2")) {
      return null;
    }
    int[] data = new int[getNsamp()];
    int nsamp = getNsamp();
    bb.position(OFF_DATA);
    for (int i = 0; i < nsamp; i++) {
      data[i] = bb.getShort();
    }
    return data;
  }

  /**
   * get the data as s2 (big endian shorts)
   *
   * @return An array of ints with samples
   */
  public int[] getDataS2() {
    if (!getDataType().equals("s2")) {
      return null;
    }
    int[] data = new int[getNsamp()];
    int nsamp = getNsamp();
    bb.position(OFF_DATA);
    for (int i = 0; i < nsamp; i++) {
      data[i] = bb.getShort();
    }
    return data;
  }

  /**
   * return our internal buffer for users direct use
   *
   * @return The bytes making up this tracebuf
   */
  @Override
  public byte[] getBuf() {
    return buf;
  }

  /**
   * get our internal ByteBuffer
   *
   * @return the internal byte buffer for this trace buf
   */
  public ByteBuffer getByteBuffer() {
    return bb;
  }

  /**
   * Compare this TraceBuf object to another. First the two objects' SEED names are compared, and if
   * they are equal, their starting dates are compared. If the object is "cleared", then it always
   * sorts later.
   *
   * @param o the other MiniSeed object
   * @return -1 if this object is less than other, 0 if the two objects are equal, and +1 if this
   * object is greater than other.
   */
  public int compareTo(TraceBuf o) {
    TraceBuf other;
    int cmp;
    if (o.isClear()) {
      return -1;    // Cleared MiniSeeds are always at end
    }
    if (isClear()) {
      return 1;
    }
    other = o;
    cmp = getSeedNameString().compareTo(other.getSeedNameString());
    if (cmp != 0) {
      return cmp;
    }
    if (getTimeInMillis() < other.getTimeInMillis()) {
      return -1;
    } else if (getTimeInMillis() > other.getTimeInMillis()) {
      return 1;
    } else {
      return 0;
    }
  }

  public static void main(String[] args) {
    for (String file : args) {
      //"20130103_120000.00_MAN";
      Util.prt("Do file=" + file);
      try {
        byte[] buf = new byte[4096];
        RawDisk rw = new RawDisk(file, "rw");
        byte[] b = new byte[(int) rw.length()];
        ByteBuffer bb = ByteBuffer.wrap(b);
        buf[TraceBuf.OFF_TYPE] = EWMessage.TYPE_TRACEBUF2;
        bb.order(ByteOrder.LITTLE_ENDIAN);
        rw.readBlock(b, 0, (int) rw.length());
        int pos = 0;
        int iblk = 0;
        while (pos < rw.length()) {
          int pinno = bb.getInt();
          int nsamp = bb.getInt();
          double start = bb.getDouble();
          double end = bb.getDouble();
          double rate = bb.getDouble();
          int len = TraceBuf.TRACE_HDR_LENGTH + nsamp * 2 - 6;
          if (nsamp != 200) {
            Util.prt(" *********** ns=" + nsamp + " len=" + len + " rt=" + rate);
          }
          System.arraycopy(b, pos, buf, 6, len);
          pos = pos + len;
          bb.position(pos);
          TraceBuf tb = new TraceBuf();
          tb.setData(buf, 0, 0, len + 6);
          Util.prta(tb.toString() + " " + iblk + " % " + (iblk % 61));
          iblk++;
        }
      } catch (IOException e) {

      }
      //}
    }
  }
}
