/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.edgemom;

/*
 * ISILink.java
 *
 * Created on June 15, 2005, 12:18 PM
 *
 * To change this template, choose Tools | Options and locate the template under
 * the Source Creation and Management node. Right-click the template and choose
 * Open. You can then make changes to the template in the Source Editor.
 */
import gov.usgs.anss.edge.RawToMiniSeed;
import gov.usgs.anss.edge.IndexFile;
import gov.usgs.anss.edge.IndexBlock;
import gov.usgs.anss.edge.EdgeProperties;
import gov.usgs.anss.edge.ChannelSender;
import gov.usgs.alarm.SendEvent;
import gov.usgs.anss.edge.IllegalSeednameException;
import gov.usgs.anss.edge.RawDisk;
import gov.usgs.anss.edgeoutput.TraceBuf;
import gov.usgs.anss.edgethread.EdgeThread;
import gov.usgs.anss.edgethread.ManagerInterface;
import gov.usgs.anss.isi.IACP;
import gov.usgs.anss.isi.ISIGapFiller;
import gov.usgs.anss.isi.ISI;
import gov.usgs.anss.isi.ISICallBack;
import gov.usgs.anss.seed.MiniSeed;
import gov.usgs.anss.seed.Steim2Object;
import gov.usgs.anss.seed.SteimException;
import gov.usgs.anss.util.Util;
import gov.usgs.anss.util.SeedUtil;
import gov.usgs.edge.config.Channel;
import java.io.FileReader;
import java.io.IOException;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.nio.ByteBuffer;
import java.text.DecimalFormat;
import java.util.GregorianCalendar;
import gnu.trove.map.hash.TLongObjectHashMap;

/**
 * This client connects to a ISI server providing data in miniseed format,
 * receives the data from that port and puts it into and edge database. It has
 * the ability to expand larger than 512 packets. It will break 4k packets into
 * 512 packets by default. This classes uses the IACP and ISI classes witch
 * implement the ISI protocol.
 *
 * Notes on throttling : The primary feed (real time feed) has throttle set to
 * zero always (no throttle). Only gaps get throttled. So this command line
 * parameter is actually only passed to any gap fills that get started. The
 * throttle value is a maximum allowed bit rate for the gap fill. The actual
 * allowed fill rate is dynamic and is adjusted every two minutes by looking to
 * see if the latency is greater than 30 seconds on the real time feed and is
 * trending up (the rate is adjusted down by 0.75). If the latency is less than
 * 30 seconds and is trending down the rate is adjusted up by a factor of 1.25
 * with a maximum value of the throttle rate max set on the command line. The
 * rate can never be adjust below 100 bps. Such a link is essentially saying it
 * cannot keep up with real time.
 *
 * <PRE>
 *Switch   arg        Description *= Item is automatic in GSNStationPanel GUI configurations
 *-h       host    *The host ip address or DNS translatable name of the ISI server
 *-p       pppp    *The port to connect to on the ISI server.
 *-s       filename*The configuration file to use .gap means this is a range of sequence request, .seq means this
 *                 is a open ended request for data (normally from most recent on).  Other extensions might be used
 *                 someday for doing non-sequence oriented requests.  The file name can contain a path ISI/IUANMO.seq
 *                 but the station portion must always be of the for NNSSS[SS].
 *-dbg             Debug output is desired
 *-lc              Convert disk loop name to lower case for ISI (FSUHUB)
 *-dbgisi          Debug the ISI decoding in detail
 *-to      nnms    Set ISI timeout interval to nnms milliseconds for the ISI connection (2 x this is the MonitorISI length)
 *-throttle bit/s  Throttle the return of data to this number of bits/seconds for gap fills (default=30000)
 *                 if throttle less than 100 but >0 , then gap thread do not try to take data, they just measure latency.
 *                 This allows the ISI to just run real time for diagnosing link related problems
 *-noudpchan       Do not send results to the UdpChannel server for display in things like channel display
 *-latency         Record latency of data in log file
 *-nohydra         Do not send the resulting data to Hydra (usually used on test systems to avoid Hydra duplicates)
 * -bind ip.adr    Bind port to this IP address
 *-allow4k         Allow 4k records to go straight to database, default is to break 4k packets up into 512 packets
 *-noseqchk        Do not check for contiguous sequences (mainly for filtered ISI disk loops)
 *-l        lll    The initial  length of the data expected in bytes, this object will allow bigger buffers if B1000 indicate its needed.
 *-on       NN     Override seed network to this network.  This is done to all MiniSeed packets received.
 *-os       SSSSS  Override SEED station code.  Done to all MiniSeed packets received.
 * </PRE>
 *
 * @author davidketchum
 */
public final class ISILink extends EdgeThread implements ISICallBack, ManagerInterface {

  private static final DecimalFormat df2 = new DecimalFormat("00");
  private static final DecimalFormat df6 = new DecimalFormat("000000");
  TLongObjectHashMap<Integer> pinnos = new TLongObjectHashMap<>();
  ByteBuffer seq_no = ByteBuffer.wrap(new byte[12]); // 32 bit signature and 64 bit counter
  private byte[] frames = new byte[4096];
  private static int threadCount;
  private RawDisk seqFileWriter;
  private BufferedReader seqFileReader;
  //private String dirStub;
  private String filename;            // The configuration file name
  private ISIGapFiller isiGapFiller;
  private boolean gapMode;
  private boolean sequenceMode;
  private boolean newSequenceFile;    // There was not sequence file at start up
  private boolean checkSequence;
  private String bind;
  private final int port;
  private final String host;
  private ChannelSender csend;
  private ISIMonitor monitor;
  private boolean recordLatency;
  //PrintStream loglat;
  //String latFilename;
  long minLatency, maxLatency, avgLatency;
  int nlatency;
  private int heartBeat;        // Number of MiniSeed heartbeats (should be zero!)
  private int nheartbeat;       // Number of heartbeats received
  //private long heartBeat;       // System time of last heartbeat received
  //private long lastData;        // System time of last data
  private long inbytes;         // Total bytes in
  private long outbytes;        // Total bytes out
  private long countMsgs;        // Count data messages received
  private long lastStatus;      // Status last time
  private long startStatus;
  private boolean dbg;
  private boolean hydra;        // If true, send data to hydra
  private boolean allow4k;      // If true, 4k Miniseed is allowed through (should never happen)
  private int timeout;          // ISI timeout in milliseconds
  private final int threadNumber;     // This is used to make PIDs for ISI
  private String station;       // Station this is connected to
  private final StringBuilder seednameSB = new StringBuilder(12);
  //private IACP iacp;            
  private ISI isi;
  private int nminiseed;
  private String overrideNetwork; // If not null, set  the network in each miniseed block
  private String overrideStation; // If not null, set this name in each miniseed block
  private MiniSeed ms;
  private int signature;      // Starting or current sequenc
  private long sequence;      // Starting or current sequenc
  private int signature2;     // Set if in a gap file
  private long sequenceEnd;   // Set non-zero if in a gap file
  private int seqSignature;   // If current mode, the signature of the packet in the .seq file
  private long seqSequence;   // If current mode, the sequence of the packet in the .seq file
  private boolean startupDoGap;    // True only the first time the link is started
  private long expectedFirstGapSeq;
  private boolean firstSeqDone;
  private ShutdownISILinkThread shutdown;
  private String argsin;
  private boolean check;
  private boolean startIsBad;
  private Steim2Object steim2;
  private int bps;
  private final StringBuilder tmpsb = new StringBuilder(100);

  @Override
  public boolean getCheck() {
    return check;
  }

  @Override
  public void setCheck(boolean t) {
    check = t;
  }

  @Override
  public String getArgs() {
    return argsin;
  }

  public long getGapSize() {
    return compareSeq(signature2, sequenceEnd, signature, sequence);
  }

  @Override
  public long getBytesIn() {
    return inbytes;
  }

  @Override
  public long getBytesOut() {
    return outbytes;
  }

  /**
   * Callback to handle the first sequence number to arrive. Basically if this
   * is a sequence mode, generate a gap file.
   *
   * @param sig The signature of the first returned data block.
   * @param seq The sequence number of the first returned data block.
   */
  @Override
  public void isiInitialSequence(int sig, long seq) {
    lastStatus = System.currentTimeMillis();
    prta(Util.clear(tmpsb).append(tag).append(" Got initial sequence of ").
            append(sig).append("/").append(seq).append(" last was ").append(signature).
            append("/").append(sequence).append(" startup ").
            append(seqSignature).append("/").append(seqSequence));
    if (sequenceMode) {
      if (gapMode) {
        // If we are not checking for full sequences, this is likely just a missing packet
        if (sig != signature || seq != (expectedFirstGapSeq + 1) && !firstSeqDone) {
          prta(Util.clear(tmpsb).append(tag).append("On gap refill first block returned is not same as request got ").
                  append(sig).append(" ").append(seq).
                  append(" expected ").append(signature).append(" ").append(expectedFirstGapSeq));
          prta(Util.clear(tmpsb).append(tag).append(" compare ").append(sig).append("/").append(seq).
                  append(" to end ").append(signature2).append("/").append(sequenceEnd).
                  append("=").append(compareSeq(sig, seq, signature2, sequenceEnd)));
          if (compareSeq(sig, seq, signature2, sequenceEnd) >= 0) {
            prta(Util.clear(tmpsb).append(tag).append(" gap refill after end, this is a gap with no return checkSequence=").append(checkSequence));
            if (!checkSequence) {
              isiAlert(IACP.IACP_ALERT_REQUEST_COMPLETE, "This is a unfillable gap.");// force completion
            }
          }
          if (!checkSequence) {
            startIsBad = true;
          }
        }
        firstSeqDone = true;      // We only need to check the first sequence after the very first data block, but not reconnects
      } else {
        // This is the first block of the 'current' sequence, see if this makes a gap!
        // Only the first connection (startupGap == true) after this object is created can be a gap.  Lost connections
        // always get a proper sequence number to be complete.   If seqSignature=0, then we do not have real seq information.
        // So we override the sig and sequence. If this appears to be a "new" station (seq <100000), always page!  Either
        // this is a new station with a big sequence first, or this was started on a new node and we do not know the real gap.
        prta(Util.clear(tmpsb).append(tag).append(" Init seq check gap ").append(sig).append("/").append(seq).
                append(" to ").append(seqSignature).append("/").append(seqSequence).
                append(" compare=").append(compareSeq(seqSignature, seqSequence, sig, (seq > 0 ? seq - 1 : seq))).
                append(" startup=").append(startupDoGap));
        if (startupDoGap && (compareSeq(seqSignature, seqSequence, sig, (seq > 0 ? seq - 1 : seq)) < 0)) {   // and we have a sequence file or sequence is low without one
          try {
            boolean done = false;
            int version = 0;
            while (!done) {
              File f = new File(filename.trim() + ".gap" + version);
              if (!f.exists()) {
                done = true;
              } else {
                version++;
              }

            }
            if (compareSeq(seqSignature, seqSequence, sig, (seq > 0 ? seq - 1 : seq)) < 0) {
              if (seqSignature == 0 && seqSequence == 0) { // Usually means the station is new, or at least new to this node
                seqSignature = sig;
                if (seq < 100000) {
                  seqSequence = 1;
                } else {
                  seqSequence = seq - 1;            // If this does not appear to be a low sequence, then assume new to this node and notify SME
                }
                prta(Util.clear(tmpsb).append(tag).append("Change gap start to sequence ").append(seqSequence).
                        append(" if this is not one, then manual intervention is required"));
                SendEvent.edgeSMEEvent("ISIInitSeq", station + " init sequence detect set seq=" + seq + "->" + seqSequence + " if not 1 investigate!", this);
              }
              try (RawDisk rw = new RawDisk(filename.trim() + ".gap" + version, "rw")) {
                rw.seek(0L);
                String s = station + "\n" + seqSignature + "\n" + seqSequence + "\n" + sig + "\n" + (seq > 0 ? seq - 1 : seq) + "\n";
                rw.write(s.getBytes());
                rw.setLength(s.length());
                prta(Util.clear(tmpsb).append(tag).append("Make gap file for ").
                        append(seqSignature).append("/").append(seqSequence).append(" to ").
                        append(sig).append("/").append(seq > 0 ? seq - 1 : seq).append(" ").
                        append(filename.trim()).append(".gap").append(version).
                        append(" new station=").append(newSequenceFile));
              }
            } else {
              prta(Util.clear(tmpsb).append(tag).append("Start up has no gap ").
                      append(seqSignature).append("/").append(seqSequence).append(" to ").
                      append(sig).append("/").append(seq > 0 ? seq - 1 : seq));
            }
          } catch (IOException e) {
            Util.IOErrorPrint(e, tag + "writing gap file", getPrintStream());
          }
        }
        startupDoGap = false;
      }
    }
  }

  /**
   * Callback to Handle a heartbeat from ISI.
   */
  @Override
  public void isiHeartbeat() {
    nheartbeat++;
    //heartBeat=System.currentTimeMillis();
  }

  /**
   * Callback to handle ISI alerts - usually request done, or socket error
   * needing to be reopened.
   *
   * @param code An IACP_ALERT type code
   * @param msg IACP class msg related to the code
   */
  @Override
  public void isiAlert(int code, String msg) {
    if (gapMode && code == IACP.IACP_ALERT_REQUEST_COMPLETE) {
      if (startIsBad && checkSequence) {
        prta(Util.clear(tmpsb).append(tag).append(" Gap request is not complete - first block was bad!"));
        SendEvent.debugSMEEvent("ISIGapFail", station + " Gap was out of window - hand fill will be required", this);
        File f = new File(filename);
        File n = new File(filename.substring(0, filename.length() - 4) + Util.toDOYString().substring(0, 14).replaceAll(",", "-"));
        f.renameTo(n);
      } else {
        prta(Util.clear(tmpsb).append(tag).append(" Gap request is completed - delete ").
                append(filename).append(" and terminate #rcv=").append(countMsgs).
                append(" msg=").append(msg).append(" startBad=").append(startIsBad).
                append(" seqChk=").append(checkSequence));
        File f = new File(filename);
        if (f.exists()) {
          f.delete();
        }
      }
      terminate();    // We are done trying
    } else {
      prta(Util.clear(tmpsb).append(tag).append(" Got an alert code=").append(code).
              append(" msg=").append(msg).append(" wait for IACP to reconnect and configure"));
    }

  }

  /**
   * This callback routine handles IDA 10.4 data.
   *
   * @param buf The byte buffer with the packet.
   * @param sig The signature int (series) on this packet.
   * @param seq The sequence of this packet.
   * @param len The length of this packet (normally 1024).
   *
   */
  @Override
  public void isiIDA10(byte[] buf, int sig, long seq, int len) {
    ByteBuffer bb = ByteBuffer.wrap(buf);
    doSequenceChecking(sig, seq);
    bb.position(0);
    byte t = bb.get();        // Common header
    byte s = bb.get();
    if (t != 'T' || s != 'S') {
      prta(Util.clear(tmpsb).append(tag).append(" Got IDA10 packet but its not TS - no implemented ").append((char) t).append((char) s));
      return;
    }
    byte version = bb.get();
    byte subvers = bb.get();
    if (subvers == 4) {
      isiIDA10_4(bb, sig, seq, len);
    } else {
      prta(Util.clear(tmpsb).append(tag).append(" Got IDA10 packet of unimplemented version=").append(version).append(".").append(subvers));
    }
  }
  private GregorianCalendar gnow = new GregorianCalendar();
  private TraceBuf tb;

  private void isiIDA10_4(ByteBuffer bb, int sig, long seq, int len) {
    countMsgs++;
    // Decode the Header
    long unitserial = bb.getLong();
    int seqnumber330 = bb.getInt();        //Q330 time 24 bytes
    int secoffset = bb.getInt();
    int usecoffset = bb.getInt();
    int nanoIndexOffset = bb.getInt();    // Offset in NANOs to the first sample in IDA packet
    int filterMicros = bb.getInt();
    short lockTime = bb.getShort();
    byte clockBitMap = bb.get();
    byte clockQual = bb.get();
    // Rest of common header
    int idaseq = bb.getInt();
    int hostTime = bb.getInt();
    short reserved = bb.getShort();
    short lcq = bb.getShort();
    short nbytes = bb.getShort();

    // This is the data area
    byte[] streamChar = new byte[6];
    bb.get(streamChar);
    for (int i = 0; i < 6; i++) {
      if (streamChar[i] < 32) {
        streamChar[i] = 32;
      }
    }
    String stream = new String(streamChar).toUpperCase();
    byte format = bb.get();
    byte conversionGain = bb.get();
    short nsamp = bb.getShort();
    short rateFactor = bb.getShort();
    short rateMultiplier = bb.getShort();

    int julian = (secoffset + seqnumber330) / 86400 + SeedUtil.toJulian(2000, 1);
    int[] ymd = SeedUtil.fromJulian(julian);
    int doy = SeedUtil.doy_from_ymd(ymd);
    int secs = (secoffset + seqnumber330) % 86400;
    int sec = secs;       // save time for RTMS in seconds.
    int usecs = usecoffset + nanoIndexOffset / 1000 - filterMicros;
    // If the usecs is negative, make it positive by borrowing seconds
    while (usecs < 0) {
      sec--;
      usecs += 1000000;
    }
    // If the sec is now zero, then need to go to the previous day
    if (sec < 0) {
      julian--;
      ymd = SeedUtil.fromJulian(julian);
      doy = SeedUtil.doy_from_ymd(ymd);
      sec += 86400;
    }

    // Convert to displays variables for human readable time
    int hr = secs / 3600;
    secs = secs % 3600;
    int min = secs / 60;
    secs = secs % 60;
    int ioClock = 0;
    if (clockQual >= 80) {
      ioClock |= 32;
    }
    int quality = 0;
    int activity = 0;
    double rate = rateFactor;
    // If rate > 0, its in hz.If rate < 0 it is it's period.
    // if multiplier > 0 it multiplies. If multiplier < 0 it divides.
    if (rateFactor == 0 || rateMultiplier == 0) {
      rate = 0;
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
        rate = 1. / rateMultiplier / rate;     // Note: both are negative, cancelling out
      }
    }
    char first = stream.charAt(0);
    if (first != 'L' && first != 'B' && first != 'V' && first != 'A') {
      prt(Util.clear(tmpsb).append(tag).append("IDA10.4 reject ").append(stream).append(" ").
              append(ymd[0]).append("/").append(df2.format(ymd[1])).append("/").append(df2.format(ymd[2])).
              append(" ").append(df2.format(hr)).append(":").append(df2.format(min)).append(":").
              append(df2.format(secs)).append(".").append(df6.format(usecs).substring(0, 4)).
              append(" tQ=").append(clockQual).append(" sq=").append(seq).append(" nb=").append(nbytes).
              append(" ns=").append(nsamp).append(" rt=" + " rt=").append(rate));
      return;
    } else {
      prt(Util.clear(tmpsb).append(tag).append("IDA10.4 ").append(stream).append(" ").
              append(ymd[0]).append("/").append(df2.format(ymd[1])).append("/").append(df2.format(ymd[2])).
              append(" ").append(df2.format(hr)).append(":").append(df2.format(min)).append(":").
              append(df2.format(secs)).append(".").append(df6.format(usecs).substring(0, 4)).
              append(" tQ=").append(clockQual).append(/*" iseq="+idaseq+*/" sq=").append(seq).
              append(" nb=").append(nbytes).append(" ns=").append(nsamp).append(" rt=").append(rate));
    }

    if (format == 0) {
      int[] data = new int[nsamp];
      for (int i = 0; i < nsamp; i++) {
        data[i] = bb.getInt();
      }
      String seedname = (station + "      ").substring(0, 7) + stream.substring(0, 5);
      Util.clear(seednameSB).append(seedname);
      Util.rightPad(seednameSB, 12);
      RawToMiniSeed.addTimeseries(data, (int) nsamp, seednameSB, ymd[0], doy, sec, usecs,
              rate, activity, ioClock, quality, (int) clockQual, this);

      if (csend != null) {
        try {
          csend.send(julian, sec * 1000 + usecs / 1000, // Milliseconds
                  seednameSB, nsamp, rate, len);  // key,nsamp,rate and nbytes
        } catch (IOException e) {
          prt(Util.clear(tmpsb).append(tag).append(" IOError trying to send to UdpChannel"));
        }
      }
      if (hydra) {
        if (tb == null) {
          tb = new TraceBuf(new byte[TraceBuf.TRACE_LENGTH]);        // Trace buf for sending data to HydraOutputer
        }
        Integer pinno = pinnos.get(Util.getHashFromSeedname(seednameSB));
        if (pinno == null) {
          Channel chn = EdgeChannelServer.getChannel(seednameSB);
          if (chn != null) {
            pinno = chn.getID();
          } else {
            pinno = 0;
          }
          pinnos.put(Util.getHashFromSeedname(seednameSB), pinno);
        }
        // If the data rate is less than 10 samples per second, aggregate before sending
        gnow.set(ymd[0], ymd[1] - 1, ymd[2]);
        // This calculation of time is not right, even if husec is < 0
        gnow.setTimeInMillis(gnow.getTimeInMillis() / 86400000L * 86400000L + sec * 1000 + usecs / 1000);         // inst, module, etc set by the HydraOutputer
        tb.setData(seednameSB, gnow.getTimeInMillis(), nsamp, rate, data, 0, 0, 0, 0, pinno);
        Hydra.sendNoChannelInfrastructure(tb);
      }
      //prta(seednameSB+" "+Util.asctime2(gnow)+" rt="+nsamp+tb);
    } else {
      prt(Util.clear(tmpsb).append(tag).append(" ****** IDA10.4 format not implemented=").append(format));
    }
  }

  private void doSequenceChecking(int sig, long seq) {
    // Check for sequences out of order, or a gap in sequences.
    if (gapMode) {   // Check for sequence in the range
      if (!(compareSeq(sig, seq, signature, sequence) >= 0 && compareSeq(sig, seq, signature2, sequenceEnd) <= 0)) {
        prta(Util.clear(tmpsb).append(tag).append(" in Gapmode got sequence out of requested!  got ").
                append(sig).append("/").append(seq).append(" low=").append(signature).append("/").append(sequence).
                append(" hi=").append(signature2).append("/").append(sequenceEnd));
        SendEvent.edgeSMEEvent("ISIGapRcvOOR", station + " got " + sig + "/" + seq
                + " low=" + signature + "/" + sequence + " hi=" + signature2 + "/" + sequenceEnd, this);
        try {
          seqFileWriter.seek(0L);
          String s = station + "\n" + signature + "\n" + sequence + "\n" + (gapMode ? signature2 + "\n" + sequenceEnd + "\n" : "");
          seqFileWriter.write(s.getBytes());
          seqFileWriter.setLength(s.length());
        } catch (IOException expected) {
        }
        terminate();
      }
    }
    if (checkSequence && signature != 0 && (signature != sig || seq != sequence + 1)) {
      prta(Util.clear(tmpsb).append(tag).append(" got unexpected sequence OOR got ").
              append(sig).append("/").append(seq).append(" expect ").append(signature).append("/").append(sequence + 1));
    }

    if (compareSeq(sig, seq, signature, sequence) < -1) {
      prta(Util.clear(tmpsb).append(tag).append(" sequence received is less than sequence expected! ").
              append(sig).append("/").append(seq).append(" to ").append(signature).append("/").append(sequence));
      SendEvent.edgeSMEEvent("ISIRcvOOR", station + " got " + sig + "/" + seq
              + " exp=" + signature + "/" + sequence + " is before expected.", this);
    } else {
      signature = sig;      // Signatures and sequence must always increase, else its a corrupt buffer
      sequence = seq;
    }
  }

  /**
   * This callback routine handles the receipt of miniseed data from the ISI
   * link (the MiniSeed Callback).
   *
   * @param buf A 512 byte buffer with a miniseed packet.
   * @param sig The signature int (series? int) on this packet.
   * @param seq The long sequence number from ISI on this packet.
   */
  @Override
  public void isiMiniSeed(byte[] buf, int sig, long seq) {
    doSequenceChecking(sig, seq);
    //lastData=System.currentTimeMillis();
    long lastIsSlow = System.currentTimeMillis();
    boolean ok = true;
    try {
      if (overrideNetwork != null) {
        System.arraycopy(overrideNetwork.getBytes(), 0, buf, 18, 2);
      }
      if (overrideStation != null) {
        System.arraycopy(overrideStation.getBytes(), 0, buf, 8, 5);
      }
      try {
        if (ms == null) {
          ms = new MiniSeed(buf);
        } else {
          ms.load(buf);
        }
      } catch (RuntimeException e) {
        ok = false;
        e.printStackTrace(getPrintStream());
      }
      nminiseed++;    // Count the packets
      inbytes += 512;
      countMsgs++;
      if (ok) {
        if (!ms.isHeartBeat()) {
          if (ms.getBlockSize() > 512) {     // bigger mini-seed read it in
            prta(Util.clear(tmpsb).append(tag).append(" BLocksize is not 512.  This is not handled"));
            SendEvent.debugEvent("ISIBadSize", station + " a block was received not 512 bytes long", "ISILink");
          }

          // Check the packet for nsamp, Reverse Integration errors, and do not write it if it is bad
          if (ms.getNsamp() > 0 && ms.getRate() > 0.) {  // Check decompression if it passes
            try {
              System.arraycopy(buf, ms.getDataOffset(), frames, 0, ms.getBlockSize() - ms.getDataOffset());

              if (steim2.decode(frames, ms.getNsamp(), ms.isSwapBytes())) {
                if (steim2.hadSampleCountError() || steim2.hadReverseError()) {
                  ok = false;
                }
              } else {
                ok = false;
              }
            } catch (SteimException e) {
              prta(Util.clear(tmpsb).append("Got Steim exception e=").append(e).append(" ").append(ms.toStringBuilder(null)));
              ok = false;
            }
          }
          if (ok) {    // The pack passed the NSAMP/RIC test
            // The following measures latency of sends and writes them in latency files
            int[] t = MiniSeed.crackTime(buf);
            long lat = t[0] * 3600000L + t[1] * 60000L + t[2] * 1000L + t[3] / 10 + ((long) (MiniSeed.crackNsamp(buf) * 1000. / MiniSeed.crackRate(buf)));
            lat = (System.currentTimeMillis() % 86400000L) - lat;
            if (lat < 0) {
              lat += 86400000;
            }
            if (MiniSeed.crackSeedname(buf).substring(7, 10).equals("BHZ")) {
              if (lat < minLatency) {
                minLatency = lat;
              }
              if (lat > maxLatency) {
                maxLatency = lat;
              }
              avgLatency += lat;
              nlatency++;
            }

            // Is it time for status yet
            long latency = System.currentTimeMillis() - ms.getNextExpectedTimeInMillis();
            if (recordLatency) {
              prta(Util.clear(tmpsb).append(latency).append(" ").append(ms.getSeedNameSB()).append(" ").
                      append(ms.getTimeString()).append(" ns=").append(ms.getNsamp()).append(" ").
                      append(ms.getRate()).append(" latR"));
            }
            if (!gapMode && latency > 3600000 && ms.getSeedNameSB().substring(7, 10).equals("BHZ")
                    && (System.currentTimeMillis() - lastIsSlow) > 600000) {
              prta(Util.clear(tmpsb).append("ISIBehind : ").append(ms.getSeedNameSB()).append(" more than 1 hours ms=").
                      append(ms.toStringBuilder(null)).append(" lat=").append(latency));
              SendEvent.edgeSMEEvent("ISIBehind", ms.getSeedNameSB() + " is behind more than 1 hours", this);
              //terminate();
            }
            if (dbg) {
              prta(Util.clear(tmpsb).append("sq=").append(seq).append(" cnt=").append(countMsgs).append(" ").
                      append(ms.toStringBuilder(null)));
            }
            Channel c = EdgeChannelServer.getChannel(ms.getSeedNameSB());
            //if(seedname.indexOf("REPV15")>= 0)  par.prta(seedname+": "+c.toString()+" flags="+c.getFlags()+" mask="+mask+" hydra="+hydra);
            if (c == null) {
              prta(Util.clear(tmpsb).append(tag).append("ISI: ***** new channel found=").append(ms.getSeedNameSB()));
              SendEvent.edgeSMEEvent("ChanNotFnd", "ISI: MiniSEED Channel not found=" + ms.getSeedNameSB() + " new?", this);
              EdgeChannelServer.createNewChannel(ms.getSeedNameSB(), ms.getRate(), this);
            }
            IndexBlock.writeMiniSeedCheckMidnight(ms, false, allow4k, tag, this); // do not use this to send to hydra
            if (hydra) {
              Hydra.sendNoChannelInfrastructure(ms);
            }

            // Send to UdpChannel server
            if (csend != null) {
              csend.send(ms.getJulian(),
                      (int) (ms.getTimeInMillis() % 86400000), // Milliseconds
                      ms.getSeedNameSB(), ms.getNsamp(), ms.getRate(), ms.getBlockSize());  // key,nsamp,rate and nbytes
            }
          } else {    // Block did not pass RIC/Nsamp decompression test, make a gap file
            SendEvent.debugSMEEvent("ISIBadMS", "ISILink seq=" + sig + "/" + seq + " " + steim2.getReverseError() + " " + steim2.getSampleCountError(), this);
            prta(Util.clear(tmpsb).append("*** ISI bad MS. gap it").append(sig).append("/").append(seq).append(" ").
                    append(steim2.getReverseError()).append(" ").append(steim2.getSampleCountError()).
                    append(" ms=").append(ms.toStringBuilder(null)));
            try {
              if (!gapMode) {
                boolean done = false;
                int version = 0;
                while (!done) {
                  File f = new File(filename.trim() + ".gap" + version);
                  if (!f.exists()) {
                    done = true;
                  } else {
                    version++;
                  }
                }
                RawDisk rw = new RawDisk(filename.trim() + ".gap" + version, "rw");    // Write new gap file into ISI dire
                rw.seek(0L);
                String s = station + "\n" + sig + "\n" + (seq - 1) + "\n" + sig + "\n" + seq + "\n";
                rw.write(s.getBytes());
                rw.setLength(s.length());
                prta(Util.clear(tmpsb).append(tag).append("* Make inline gap file for RIC ").
                        append(sig).append("/").append(seq - 1).append(" to ").append(sig).
                        append("/").append(seq).append(" ").append(filename.trim()).
                        append(".gap").append(version));
                rw.close();
                rw = new RawDisk("ISI/" + station + "_" + sig + "_" + seq + ".badms", "rw");
                rw.seek(0);
                rw.write(buf, 0, 512);
                rw.setLength(512);
                rw.close();
              } else {
                prta(Util.clear(tmpsb).append(tag).append("* This RIC occured in a gap file, skip creating a new inline gap file"));
              }

            } catch (IOException e) {
              Util.IOErrorPrint(e, tag + "writing gap file", getPrintStream());
            }
          }
          if ((countMsgs % 100) == 0) {
            int insbps = (int) (100 * 595 * 8 * 1000L / Math.max(System.currentTimeMillis() - lastStatus, 5000));
            bps = (int) (insbps + 9 * bps) / 10;
            if (countMsgs == 100) {
              bps = insbps;
            }
            prta(Util.clear(tmpsb).append("cnt=").append(countMsgs).append(" in ").
                    append((System.currentTimeMillis() - lastStatus) / 1000).append(" s ").
                    append(insbps).append(" bps ").append(bps).append(" bps avg ").
                    append(ms.toStringBuilder(null)).append(" sq=").append(seq));
            lastStatus = System.currentTimeMillis();
          }
        } else {
          if (dbg) {
            Util.prta(Util.clear(tmpsb).append(tag).append("Heart beat"));
          }
          heartBeat++;
        }
      } else {
        Util.prta("*** MiniSEED did not load.  skip packet!");
      }
    } catch (IOException e) {
      Util.SocketIOErrorPrint(e, tag + "Sending data to holdings via UDP");
    } catch (IllegalSeednameException e) {
      prta(Util.clear(tmpsb).append(tag).append(" Got ").append(e.toString()));
    }
  }

  /**
   * This callback handles when the IACP makes a new connection. It should do
   * the request setup.
   */
  @Override
  public void isiConnection() {
    try {
      prta(Util.clear(tmpsb).append(tag).append(" New connection found - do setup request"));
      if (sequenceMode) {
        if ((sequenceEnd - sequence) > 10000) {
          SendEvent.debugSMEEvent("ISILargeGap", station + " Large gap req " + sequence + " to " + sequenceEnd, this);
          prta(Util.clear(tmpsb).append(tag).append(" ***** large gap request to fill ").
                  append(sequence).append(" to ").append(sequenceEnd));
        }
        isi.sendSeqRequest(signature, (sequence > 0 ? sequence + 1 : sequence), signature2, sequenceEnd);
      } else {
        prta(Util.clear(tmpsb).append(tag).append(" Non-sequence mode not implemented"));
      }
    } catch (IOException e) {
      Util.SocketIOErrorPrint(e, tag + "Sending SOH request", getPrintStream());
    }
  }

  /**
   * Set the debug variable.
   *
   * @param t True, turn on debug output.
   */
  public void setDebug(boolean t) {
    dbg = t;
  }

  /**
   * Creates an new instance of ISICLient - which will try to stay connected to
   * the host/port source of data. This one gets its arguments from a command
   * line.
   *
   * @param argline EdgeThread args.
   * @param tg The EdgeThread tag.
   */
  public ISILink(String argline, String tg) {
    super(argline, tg);
    ISILink foolThis = this;
    argsin = argline;
//initThread(argline,tg);
    String[] args = argline.split("\\s");
    dbg = false;
    int len = 512;
    String h = null;
    int pt = 0;
    boolean nocsend = false;
    allow4k = false;
    //latFilename=tg;
    startStatus = System.currentTimeMillis();
    countMsgs = 0;
    hydra = true;
    timeout = 30000;
    steim2 = new Steim2Object();
    //dirStub=System.getProperty("user.home")+"/ISI";
    //File f = new File(dirStub);
    int throttle = 30000;
    //if(!f.isDirectory()) dirStub = System.getProperty("user.home");
    boolean dbgisi = false;
    int rcvsiz = 0;
    prta(tg + "ISILink starting argline=" + argline);
    boolean lowerCase = false;
    checkSequence = true;
    for (int i = 0; i < args.length; i++) {
      //prt(i+" arg="+args[i]);
      if (args[i].length() <= 0) {
        continue;
      }
      if (args[i].equals("-h")) {
        h = args[i + 1];
        i++;
      } else if (args[i].equals("-p")) {
        pt = Integer.parseInt(args[i + 1]);
        i++;
      } //else if(args[i].equals("-l")) {len = Integer.parseInt(args[i+1]); i++;} // ISI only supports 512 currently
      else if (args[i].equals("-dbg")) {
        dbg = true;
      } else if (args[i].equals("-noudpchan")) {
        nocsend = true;
      } else if (args[i].equals("-nohydra")) {
        hydra = false;
      } else if (args[i].equals("-latency")) {
        recordLatency = true;
      } else if (args[i].equals("-allow4k")) {
        allow4k = true;
      } else if (args[i].equals("-noseqchk")) {
        checkSequence = false;
      } else if (args[i].equals("-rcvsiz")) {
        rcvsiz = Integer.parseInt(args[i + 1]);
        i++;
      } else if (args[i].equals("-s")) {
        filename = args[i + 1];
        i++;
      } else if (args[i].equals("-lc")) {
        lowerCase = true;
      } else if (args[i].equals("-to")) {
        timeout = Integer.parseInt(args[i + 1]);
        i++;
      } else if (args[i].equals("-bind")) {
        bind = args[i + 1];
        i++;
      } else if (args[i].equals("-on")) {
        overrideNetwork = (args[i + 1] + "  ").substring(0, 2);
        i++;
      } else if (args[i].equals("-os")) {
        overrideStation = (args[i + 1] + "     ").substring(0, 5);
        i++;
      } else if (args[i].equals("-dbgrtms")) {
        RawToMiniSeed.setDebugChannel(args[i + 1].replaceAll("-", " ").replaceAll("_", " "));
        RawToMiniSeed.setDebug(true);
      } //else if(args[i].equals("-d")) {dirStub=args[i+1];i++;}
      else if (args[i].equals("-throttle")) {
        throttle = Integer.parseInt(args[i + 1]);
        i++;
      } else if (args[i].equals("-dbgisi")) {
        dbgisi = true;
      } else if (args[i].substring(0, 1).equals(">")) {
        break;
      } else {
        prt("ISI client unknown switch=" + args[i] + " ln=" + argline);
      }
    }
    prt(Util.clear(tmpsb).append(tag).append(" ").append(Util.ascdate()).append(" ISILink: new line parsed to host=").
            append(h).append(" port=").append(pt).append(" station=").append(station).
            append(" to=").append(timeout).append(" noudpch=").append(nocsend).
            append(" dbg=").append(dbg).append(" rcvsiz=").append(rcvsiz).append(" bind=").append(bind));
    if (!nocsend) {
      csend = new ChannelSender("  ", "ISILink", "ISI-" + tg);
    }
    port = pt;
    host = h;
    IndexFile.init();
    threadNumber = threadCount++;
    String nd = IndexFile.getNode();
    int userNumber = 0;
    int nodeNumber;
    prta("node=" + nd);
    if (nd.contains("#")) {
      userNumber = nd.charAt(nd.indexOf("#") + 1) - '0';
      nodeNumber = Integer.parseInt(nd.substring(0, nd.indexOf("#")));
    } else if (nd.equals("")) {
      nodeNumber = 0;
    } else {
      nodeNumber = Integer.parseInt(nd);
    }
    prta(Util.clear(tmpsb).append("userNumber=").append(userNumber).append(" nodeNumber=").append(nodeNumber));
    signature = 0;
    sequence = 0;
    signature2 = 0;
    sequenceEnd = 0;
    startupDoGap = true;
    gapMode = false;
    sequenceMode = false;
    if (filename.indexOf(".gap") > 0) {
      gapMode = true;
      sequenceMode = true;
      timeout = 150000;
    } else if (filename.indexOf(".seq") > 0) {
      gapMode = false;
      sequenceMode = true;
      throttle = 0; // No throttle on main receiver
    }
    try {
      if (sequenceMode) {
        seqFileReader = new BufferedReader(new FileReader(filename));
        station = seqFileReader.readLine();
        if (gapMode) {
          signature = Integer.parseInt(seqFileReader.readLine().trim());
          sequence = Long.parseLong(seqFileReader.readLine().trim());
          signature2 = Integer.parseInt(seqFileReader.readLine().trim());
          sequenceEnd = Long.parseLong(seqFileReader.readLine().trim());
          if (compareSeq(signature, sequence, signature2, sequenceEnd) >= 0) {
            prta(Util.clear(tmpsb).append(tag).append("Attempt to start Gap file with sequences OOR ").
                    append(signature).append("/").append(sequence).append(" to ").
                    append(signature2).append("/").append(sequenceEnd).append(" throttle=").append(throttle));
            SendEvent.debugEvent("ISIGapOOR", station + " Attempt to start Gap file with sequences OOR "
                    + signature + "/" + sequence + " to " + signature2 + "/" + sequenceEnd + " " + throttle + "bps", foolThis);

            running = false;
            File f = new File(filename);
            if (f.exists()) {
              f.delete();
            }
            isi = null;
            shutdown = null;
            return;
          }
          expectedFirstGapSeq = sequence;
          prta(Util.clear(tmpsb).append(Util.ascdate()).append(" ISILink: start gap file from ").
                  append(signature).append("/").append(sequence).append(" to ").
                  append(signature2).append("/").append(sequenceEnd));
        } else {
          seqSignature = Integer.parseInt(seqFileReader.readLine().trim());
          seqSequence = Long.parseLong(seqFileReader.readLine().trim());
          prta("ISILink: read seq file = " + seqSignature + "/" + seqSequence);
        }
      } else {
        prta(" Non-sequence mode not yet implmented");
        isi = null;
        shutdown = null;
        return;
      }
    } catch (FileNotFoundException e) {
      prta(tag + "Could not find sequence file - assume new");
      newSequenceFile = true;
      station = filename.substring(0, filename.indexOf("."));
      if (station.lastIndexOf("/") >= 0) {
        station = station.substring(station.lastIndexOf("/") + 1);
      }
      try {
        seqFileWriter = new RawDisk(filename, "rw");
        seqFileWriter.seek(0L);
        String s = station + "\n" + 0 + "\n" + 0 + "\n" + (gapMode ? 0 + "\n" + 0 + "\n" : "");
        seqFileWriter.write(s.getBytes());
        seqFileWriter.setLength(s.length());
        seqFileWriter.close();
      } catch (IOException e2) {
        prta(tag + "IOException opening gap or sequence file.  Probably permission problem.  e2=" + e2);
        SendEvent.edgeSMEEvent("ISIGapBadOpn", "Could not open " + filename + " permissions?2 ", foolThis);
        isi = null;
        shutdown = null;
        return;
      }
    } catch (IOException e) {
      prta("IOException reading config file at line=");
    }
    tag += "ISIL" + ":";  // With String and type
    while (seqFileWriter == null) {
      try {
        seqFileWriter = new RawDisk(filename, "rw");
      } catch (FileNotFoundException e) {
        prta(tag + "IOException opening gap or sequence file.  Probably permission problem.  e=" + e);
        SendEvent.edgeSMEEvent("ISIGapBadOpn", "Could not open " + filename + " permissions? ", foolThis);
        try {
          sleep(120000);
        } catch (InterruptedException e2) {
        }
      }
    }
    prta(Util.clear(tmpsb).append(tag).append(" seqmod=").append(sequenceMode).
            append(" gapMode=").append(gapMode).append(" isiGapFiller=").append(isiGapFiller).
            append(" ").append(throttle).append(" bps file=").append(filename));
    if (sequenceMode && !gapMode && isiGapFiller == null) {
      isiGapFiller = new ISIGapFiller(argline.trim() + "gap", tag, this);
    }
    if (throttle > 100 || sequenceMode) {
      monitor = new ISIMonitor(this);
    }
    isi = new ISI(host, port, (lowerCase ? station.toLowerCase() : station), userNumber * 10000 + nodeNumber * 1000 + threadNumber,
            timeout, 0, rcvsiz, throttle, tag, bind, this);
    if (dbgisi) {
      isi.setDebug(true);
    }
    isi.setCallBack(foolThis);
    shutdown = new ShutdownISILinkThread(this);
    Runtime.getRuntime().addShutdownHook(shutdown);
    start();
  }

  @Override
  public void terminate() {
    isi.terminate();
    terminate = true;
    monitor.terminate();
    if (isiGapFiller != null) {
      isiGapFiller.terminate();
    }
    interrupt();
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
      sb.append(tag).append(" Open Port=").append(host).append("/").append(port).
              append(" gapMode=").append(gapMode).append(" seqMode=").append(sequenceMode).
              append(" #hb=").append(nheartbeat).append(" nbIn=").append(inbytes).
              append(" #msg=").append(countMsgs).append(" #ms=").append(nminiseed);
    }
    return sb;
  }

  /**
   * This Thread keeps a socket open to the host and port, reads in any
   * information on that socket, keeps a list of unique StatusInfo, and updates
   * that list when new data for the unique key comes in.
   */
  @Override
  public void run() {
    int nillegal = 0;
    running = true;
    long lastSequence = 0;
    StringBuilder runsb = new StringBuilder(50);
    while (true) {
      try {
        try {
          sleep(10000);
        } catch (InterruptedException e) {
        }
        if (monitor == null) {
          continue;  // not an active thread
        }
        if (monitor.didInterrupt()) {
          if (isi.isConnected()) {
            prta(Util.clear(runsb).append(tag).append("ISIL: monitor went off - reset Link!"));
            monitor.reset();
            isi.resetLink();
          } else {
            prta(Util.clear(runsb).append(tag).append("ISIL: monitor went off - not connected"));
            monitor.reset();
          }
        }
        if (sequenceMode && isi.getSequence() > 0) {
          signature = isi.getSignature();
          if (isi.getSequence() > 0) {
            sequence = isi.getSequence();
          } else {
            prta(Util.clear(runsb).append("ISILink: in run isi.getSequence was zero.  Do not reset it."));
          }
          if (terminate) {     // Trap the terminate happening with a bad seq/sig - looking for restart bug
            if (signature <= 0 || sequence <= 0) {
              prta(Util.clear(runsb).append("On terminate the signature and sequence are non-sensical sig=").append(signature).
                      append(" seq=").append(sequence));
              SendEvent.debugEvent("ISIBadSigSeq", station + " sig=" + signature + " seq=" + sequence, this);
            }
          }
          if ((terminate && !startIsBad && signature > 0 && sequence > 0)
                  || (!startIsBad && signature > 0 && Math.abs(sequence - lastSequence) > 10)) {
            try {
              seqFileWriter.seek(0L);
              String s = station + "\n" + signature + "\n" + sequence + "\n" + (gapMode ? signature2 + "\n" + sequenceEnd + "\n" : "");
              seqFileWriter.write(s.getBytes());
              seqFileWriter.setLength(s.length());
              if (terminate) {
                seqFileWriter.close();
              }
            } catch (IOException e) {
              Util.IOErrorPrint(e, tag + "Writing out " + filename + " term=" + terminate);
            }
            lastSequence = sequence;
          }
        }
        if (terminate) {
          break;
        }
      } catch (RuntimeException e) {
        prta(Util.clear(runsb).append(tag).append(" RuntimeException in ").append(this.getClass().getName()).
                append(" e=").append(e.getMessage()));
        if (getPrintStream() != null) {
          e.printStackTrace(getPrintStream());
        } else {
          e.printStackTrace();
        }
        if (e.getMessage() != null) {
          if (e.getMessage().contains("OutOfMemory")) {
            SendEvent.doOutOfMemory("", this);
            throw e;
          }
        } else {
          SendEvent.debugEvent("RuntimeExcp", station + " In ISILink e=" + e, this);
        }
        if (terminate) {
          break;
        }
      }
    }       // while(true) do socket open
    //monitor.terminate();
    prta(Util.clear(runsb).append(tag).append(" is terminated."));
    if (csend != null) {
      csend.close();
    }
    running = false;
    terminate = false;
    try {
      Runtime.getRuntime().removeShutdownHook(shutdown);
    } catch (Exception expected) {
    }
  }

  /**
   * Return an estimate of the number of sequences between two
   * signature-sequence combinations. If the signature are different, the
   * estimate is set at about 2 billion.
   */
  private long compareSeq(int sig, long seq, int sig2, long seq2) {
    if (sig != sig2) {
      if (sig < sig2) {
        return -2000000000L;
      } else {
        return 2000000000L;
      }
    }
    if (seq < seq2) {
      return (seq - seq2);
    }
    if (seq > seq2) {
      return (seq - seq2);
    }
    return 0L;
  }
  /**
   * Return the monitor string for Icinga.
   *
   * @return A String representing the monitor key value pairs for this
   * EdgeThread.
   */
  long lastMonCount;
  long lastMonInbytes;

  @Override
  public StringBuilder getMonitorString() {
    Util.clear(monitorsb);
    long np = countMsgs - lastMonCount;
    long nb = inbytes - lastMonInbytes;
    lastMonCount = countMsgs;
    lastMonInbytes = inbytes;
    monitorsb.append(tag).append("SLBytesIn=").append(nb).append("\n").append(tag).append("SLNpacket=").append(np).append("\n");
    return monitorsb;
  }

  @Override
  public StringBuilder getStatusString() {
    if (statussb.length() > 0) {
      statussb.delete(0, statussb.length());
    }
    return statussb.append("#rcv=").append(countMsgs).append(" hb=").append(heartBeat);
  }

  @Override
  public StringBuilder getConsoleOutput() {
    return consolesb;
  } //  we use prt directly

  /**
   * Monitor the ISICLient and stop it if it does not receive heartBeats or
   * data. This is essentially the ISI wait two times timeout and close if no
   * activity sensed.
   */
  public final class ISIMonitor extends Thread {

    boolean terminate;
    long lastHeartbeat;

    long lastCount;
    int msWait;
    boolean interruptDone;
    ISILink thr;
    private final StringBuilder runsb = new StringBuilder(100);

    public boolean didInterrupt() {
      return interruptDone;
    }

    public void terminate() {
      terminate = true;
      interrupt();
    }

    public void reset() {
      interruptDone = false;
    }

    public ISIMonitor(ISILink t) {
      thr = t;
      msWait = timeout * 2;
      gov.usgs.anss.util.Util.prta("new ThreadMonitor " + getName() + " " + getClass().getSimpleName() + " t=" + t);
      start();
    }

    @Override
    public void run() {
      //try{sleep(msWait);} catch(InterruptedException expected) {}
      long lastStatus = 0;
      int loop = 0;
      boolean lastConnected = false;
      while (!terminate) {
        long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < msWait) {
          try {
            sleep(Math.max(200, msWait - (System.currentTimeMillis() - start)));
          } catch (InterruptedException expected) {
          }
          if (terminate) {
            break;
          }
        }
        if (terminate) {
          break;
        }
        if (start - lastStatus > 900000) {
          lastStatus = start;
          prta(Util.clear(runsb).append("ISIM: status connected=").append(isi.isConnected()).
                  append(" lastHB=").append(lastHeartbeat).append(" HB=").append(nheartbeat).
                  append(" lastCount=").append(lastCount).append(" count=").append(countMsgs).
                  append("loop=").append(loop));
        }
        loop++;
        if (!isi.isConnected()) {
          lastConnected = false;
          if (loop % 20 != 19) {
            continue;
          }
        }
        if (isi.isConnected() && !lastConnected) { // connection just came up
          lastConnected = true;
          prta(" ISIM: monitor detects a new connection! ");
          try {
            sleep(10000);
          } catch (InterruptedException expected) {
          }
          continue;
        }

        if (lastHeartbeat == nheartbeat && countMsgs == lastCount) {
          isi.resetLink();
          thr.interrupt();      // Interrupt in case it is in a wait
          interruptDone = true;     // So interrupter can know it was us
          prta(Util.clear(runsb).append(" ISIM: monitor has gone off HB=").append(nheartbeat).
                  append(" lHB=").append(lastHeartbeat).append(" in =").append(countMsgs).
                  append(" lin=").append(lastCount).append(" to=").append(timeout).
                  append(" connected=").append(isi.isConnected()).append(" loop=").append(loop));
          try {
            sleep(msWait);
          } catch (InterruptedException expected) {
          }
          if (terminate) {
            break;
          }
          interruptDone = false;
        }
        lastCount = countMsgs;
        lastHeartbeat = nheartbeat;
      }
      prta(" ISIMon: has been terminated");
    }

  }

  /**
   * Inner class to be the thread activated on shutdown() by the process.
   */
  class ShutdownISILinkThread extends Thread {

    ISILink thr;

    /**
     * Default constructor does nothing. The shutdown hook starts the run()
     * thread.
     *
     * @param t The thread we will shutdown.
     */
    public ShutdownISILinkThread(ISILink t) {
      thr = t;
      gov.usgs.anss.util.Util.prta("new ThreadShutdown " + getName() + " " + getClass().getSimpleName());
    }

    // This is called by the RunTime.shutdown at any termination 
    @Override
    public void run() {
      System.err.println(tag + " ISIL: shutdown() started...");
      int loop = 0;
      // We want the edgeblock clients down before we shut down the replicator
      thr.terminate();          // Send terminate to main thread and cause interrupt
      RawToMiniSeed.forceStale(-1);
      while (running) {
        Util.sleep(100);
        loop++;
        if (loop % 100 == 0) {
          break;
        }
      }
      System.err.println(tag + " ISIL: shutdown() of ISILink Thread is complete.");
    }
  }

  /**
   * This is the test code for ISILink.
   *
   * @param args The command line arguments.
   */
  public static void main(String[] args) {
    IndexFile.init();
    EdgeProperties.init();
    Util.setModeGMT();
    ISILink[] liss = new ISILink[10];
    Util.setNoInteractive(true);
    IndexFile.setDebug(true);
    int i = 0;
    liss[i++] = new ISILink("-h fsuhub.gsras.ru -p 39136 -to 60000 -dbg -s ISI/IUYSS.seq -lc", "IUYSS");
    Util.prt("" + liss[0]);
  }

}
