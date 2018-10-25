/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.edgemom;

import gov.usgs.alarm.SendEvent;
import gov.usgs.anss.cd11.GapList;
import gov.usgs.anss.edge.ChannelSender;
import gov.usgs.anss.edge.EdgeProperties;
import gov.usgs.anss.edge.IndexFile;
import gov.usgs.anss.edge.RawToMiniSeed;
import gov.usgs.anss.edgethread.EdgeThread;
import gov.usgs.anss.net.TimeoutSocketThread;
import gov.usgs.anss.seed.MiniSeed;
import gov.usgs.anss.util.SeedUtil;
import gov.usgs.anss.util.Util;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.TreeMap;

/**
 * This client connects to a SCREAMS server and interprets the incoming GCF packets data and
 * converts them to miniSEED using RawToMiniSeed. The problem is generally deciding a method of
 * obtaining a SEED name from the channel. Two methods are supported: 1) A config file is used which
 * will map the first 4 characters of the guralp StreamID and set the station based on the config
 * file. 2) A configfile is used which will map the characters before the first dash in the source
 * name.
 *
 * <PRE>
 *Switch   arg        Description *= Item is automatic in GSNStationPanel GUI configurations
 * -cs                Use station seednames from the source field
 * -c      filename   Use configuration in filename to do stream-to-seed conversions
 * -p      pppp       Port number on the SCREAM server to use (def=1567)
 * -ipadr  ip.adr.scr The IP address of the target SCREAM server
 * -bind   ip.adr     Bind our end of the connection to this address (useful on multi-homed machines)
 * -state  statefile  Put state information for saving sequences in -state
 * -fn       NN       Force location code on all channels
 * -nohydra           Do not create Tracebufs and send them to Hydra
 * -nogapfill         Do not use the version 4.5 to fill gaps
 * -dbg               Higher log output
 * Format of config file:
 * UNIT:-s STATION -bands HN -n NN -l LL-TR
 * UNIT is the name of the station within the Guralp SCREAM (could be serial number or station like name)
 * -s STATION is the 5 character or LESS SEED station code
 * -bands gives instrument band codes (e.g. H or N) for two seismometer groups A and B, if lower case then seismometer is short period
 * -n NN is the SEED network code
 * -l LL-TR - The LL is the location code for the first seismometer group and TR is for the 2nd group.
 *            Use hyphen for space (so ------ is no location codes). The default is one group set to "00".
 * The frequency band is derived from the digitizing rate and whether the -bands letter for
 * the group is upper or lower case.
 *
 * The location code are for continuous and triggered data from the same Guralp "tap"
 * Example :
 * AKAS:-s AKAS -bands HN -n TU -l 00-01
 * C488:-s OKCFA -bands HH -n OK -l -----
 * </PRE> In this example if both seismometer groups are sampled at 100 hz, the band codes will be
 * "H", the seismometer code will be "H" for the 1st seismometer and "N for the 2nd. That is the
 * first seismometer will be "HHZ,HH1,HH2" location code 00, and the 2nd "HNZ,HN1,HN2" w/location
 * code -1
 *
 * @author davidketchum
 */
public final class ScreamClient extends EdgeThread {

  public final short GURALP_SCREAM_DEFAULT_PORT = 1567;
  public final int[] gains = {0, 1, 2, 4, 8, 16, 32, 64};
  private final TreeMap<String, ScreamUnitConfig> units = new TreeMap<>();
  DatagramSocket d;               // Socket we will read forever (or misalignment)
  int port;                       // Udp port we accept data from
  private String ip;              // IP address of target scream server
  private String bind;          // The IP address to bind
  boolean dbg;
  long bytesin;                  // Count input bytes
  long bytesout;                 // Count output bytes (should be none)
  int totmsgs;                   // Count input messages
  long lastRead;                 // CurrentTimeMillis() of last I/O
  private String statefile;
  private String configFile;
  private GapList gaplist;

  // GCF related stuff
  private byte version;
  private int systemID, streamID, dateCode, dataFormat;
  private String systemIDString, streamIDString, dataCodeString;
  private StringBuilder sbtmp = new StringBuilder(10);
  private GregorianCalendar start = new GregorianCalendar();
  private long fudicialDate;
  private byte[] scratch = new byte[1024];
  int startSeq, endSeq;
  int msgLength = 1100;
  // Unified status packets content
  private boolean locked;
  private int clockSource;
  private int clockUSecOff;
  private int clockLastSyncGCF;
  private boolean configSource;
  private int fix;
  private double latitude;
  private double longitude;
  private double elevation;
  // Seed channel related stuff
  private String overrideSeedNetwork;
  private long lastSME;
  private boolean nohydra;
  private boolean nogapfill;
  private GCFGapFiller gapfiller;
  private ChannelSender csend;
  boolean nocsend = false;

  @Override
  public void terminate() {
    terminate = true;
    if (!d.isClosed()) {
      d.close();
    }
  }

  /**
   * Set terminate variable.
   *
   * @param t If true, the run() method will terminate at next change and exit.
   */
  public void setTerminate(boolean t) {
    terminate = t;
    if (terminate) {
      terminate();
    }
  }

  /**
   * Set the socket end for this tunnel and start the Thread up.
   *
   * @param s2 A socket end which will be this socket end.
   */
  //public void setSocket(Socket s2) {s = s2; start();}*/
  /**
   * Return a descriptive tag of this socket (basically "RIS:" plus the tag of creation).
   *
   * @return A descriptive tag.
   */
  @Override
  public String getTag() {
    return "RIU:" + tag;
  }
  /**
   * Return the monitor string for Icinga.
   *
   * @return A String representing the monitor key value pairs for this EdgeThread.
   */
  long lastMonBytesin;

  @Override
  public StringBuilder getMonitorString() {
    if (monitorsb.length() > 0) {
      monitorsb.delete(0, monitorsb.length());
    }
    long nb = bytesin - lastMonBytesin;
    lastMonBytesin = bytesin;
    return monitorsb.append(tag).append("ScreamBytesIn=").append(nb).append("\n");
  }

  /**
   * Return a status string for this type of thread.
   *
   * @return A status string with identifier and output measures.
   */
  @Override
  public StringBuilder getStatusString() {
    if (statussb.length() > 0) {
      statussb.delete(0, statussb.length());
    }
    return statussb.append(getTag()).append(" in=").append((bytesin + "        ").substring(0, 9)).
            append("out=").append((bytesout + "        ").substring(0, 9)).append("last=").
            append(((System.currentTimeMillis() - lastRead) + "        ").substring(0, 9));
  }

  /**
   * Return console output. For this there is none.
   *
   * @return The console output which is always empty.
   */
  @Override
  public StringBuilder getConsoleOutput() {
    return consolesb;
  }

  /**
   * Set debug flag.
   *
   * @param t Value to set.
   */
  public void setDebug(boolean t) {
    dbg = t;
  }

  /**
   * Return the measure of input volume.
   *
   * @return The number of bytes read from this socket since this object's data creation.
   */
  @Override
  public long getBytesIn() {
    return bytesin;
  }

  /**
   * Return measure of output volume.
   *
   * @return The number of bytes written to this socket since this object was created.
   */
  @Override
  public long getBytesOut() {
    return bytesout;
  }

  /**
   * Return ms of last IO operation on this channel (from System.currentTimeMillis()).
   *
   * @return ms value at last read or I/O operation.
   */
  public long getLastRead() {
    return lastRead;
  }

  /**
   * Creates a new instance of ScreamClient.
   *
   * @param argline The argument line.
   * @param tg Logging tag.
   */
  public ScreamClient(String argline, String tg) {
    super(argline, tg);
    String[] args = argline.split("\\s");
    dbg = false;
    int len = 512;
    String h = null;
    port = 1567;
    bind = null;
    endSeq = -1;
    nohydra = false;
    nogapfill = false;
    startSeq = -1;
    for (int i = 0; i < args.length; i++) {
      //prt(i+" arg="+args[i]);
      if (args[i].equals("-p")) {
        port = Integer.parseInt(args[i + 1]);
        i++;
      } else if (args[i].equals("-ipadr")) {
        ip = args[i + 1];
        i++;
      } else if (args[i].equals("-bind")) {
        bind = args[i + 1];
        i++;
      } else if (args[i].equals("-state")) {
        statefile = args[i + 1];
        i++;
      } else if (args[i].equals("-c")) {
        configFile = args[i + 1];
        i++;
        configSource = false;
      } else if (args[i].equals("-cs")) {
        configFile = args[i + 1];
        i++;
        configSource = true;
      } else if (args[i].equals("-lowseq")) {
        startSeq = Integer.parseInt(args[i + 1]);
        i++;
      } else if (args[i].equals("-endseq")) {
        startSeq = Integer.parseInt(args[i + 1]);
        i++;
      } else if (args[i].equals("-noudpchan")) {
        nocsend = true;
      } else if (args[i].equals("-fn")) {
        overrideSeedNetwork = args[i + 1];
        i++;
      } else if (args[i].equals("-nohydra")) {
        nohydra = true;
      } else if (args[i].equals("-dbg")) {
        dbg = true;
      } else if (args[i].equals("-nogapfill")) {
        nogapfill = true;
      } else if (args[i].substring(0, 1).equals(">")) {
        break;
      } else {
        prt(tag + "ScreamClient:  unknown switch=" + args[i] + " ln=" + argline);
      }
    }
    start.set(1989, 10, 17, 1, 0);  // November 17, 1989 - I do not know why
    fudicialDate = start.getTimeInMillis() / 86400000L * 86400000L;
    if (!nocsend && csend == null) {
      csend = new ChannelSender("  ", "ScreamCl", "SC-" + tag);
    }

    bytesin = 0;
    bytesout = 0;
    totmsgs = 0;
    terminate = false;
    running = false;
    if (Util.isShuttingDown) {
      return;     // do not restart if its shutting down
    }
    try {
      prta(tag + "make scream state file=" + statefile);
      gaplist = new GapList(0, -1, statefile, this);
    } catch (IOException e) {
      prta(tag + "ScreamClient: error opening gap file/statefile");
    }
    readConfigFile();
    if (!nogapfill) {
      gapfiller = new GCFGapFiller();
    }
    start();
  }

  @Override
  public void run() {
    try {
      GCFSend gcfsender = null;
      byte[] buf = new byte[msgLength];
      ByteBuffer b = ByteBuffer.wrap(buf);
      long lastReadConfig = System.currentTimeMillis();
      running = true;

      // Variables from each read header
      int increment = 0;
      while (!terminate) {
        try {
          if (terminate) {
            break;
          }
          prta(tag + " ScreamClient: Open Port=" + (port + increment));
          if (bind == null) {
            d = new DatagramSocket(port + increment);
          } else {
            d = new DatagramSocket(port + increment, InetAddress.getByName(bind));
          }
          lastRead = System.currentTimeMillis();
          break;
        } catch (UnknownHostException e) {
          prta(tag + "***** Unknown host=" + bind);
          return;
        } catch (SocketException e) {
          if (e.getMessage().equals("Address already in use")) {
            try {
              prt(tag + "ScreamClient: Address in use - try again. port=" + (port + increment));
              increment++;
              Thread.sleep(2000);
            } catch (InterruptedException expected) {

            }
          } else {
            prt(tag + "Error opening UDP port =" + (port + increment) + "-" + e.getMessage());
            increment++;
            try {
              Thread.sleep(2000);
            } catch (InterruptedException expected) {

            }
          }
        }
      }       // while(true) on opening UdpSocket
      DatagramPacket p = new DatagramPacket(buf, msgLength);

      // If this is the main thread, start a GCFSend thread to start/keep the flow going
      // If this is sequence mode, we need to send request for first packet
      if (startSeq < 0 && endSeq < 0) {
        gcfsender = new GCFSend(this, d);
      }

      //try{sleep(600000);} catch(InterruptedException expected) {}
      // This loop receive UDP packets
      while (!terminate) {
        try {
          d.receive(p);
          processGCF(b, p.getLength());

          totmsgs++;
          if (dbg) {
            prta(tag + Util.ascdate() + " ScreamClient: Rcv adr=" + p.getSocketAddress()
                    + " len=" + p.getLength() + " totmsg=" + totmsgs);
          }
          if (p.getLength() > msgLength) {
            prta(tag + "ScreamClient: Datagram wrong size=" + msgLength
                    + "!=" + p.getLength() + " rcv adr=" + p.getSocketAddress());
          }

          bytesin += p.getLength();
          lastRead = System.currentTimeMillis();
        } catch (IOException e) {
          prt(tag + "ScreamClient:receive through IO exception");
        } catch (RuntimeException e) {
          e.printStackTrace(getPrintStream());
          prta(tag + "Runtime exception e=" + e);
        }
        if (System.currentTimeMillis() - lastReadConfig > 120000) {
          lastReadConfig = System.currentTimeMillis();
          prta(tag + "BytesIn=" + bytesin + " #msg=" + totmsgs);
          readConfigFile();
        }
      }         // endi of while(!terminate)
    } catch (RuntimeException e) {
      prta(tag + "Got runtime exception in Scream client e=" + e);
      e.printStackTrace(this.getPrintStream());
    }
    prta(tag + "ScreamClient has been terminated. wait for gapfiller to exit.");
    gaplist.writeGaps(true);
    if (gapfiller != null) {
      while (gapfiller.isAlive()) {
        try {
          sleep(1000);
        } catch (InterruptedException expected) {
        }
      }
    }
    prt(tag + "Gap filler is down");
    if (!d.isClosed()) {
      d.close();    // clean up UdpSocket if it is open
    }
    if (csend != null) {
      csend.close();
    }
    running = false;              // Let edgeThread know we are done
    terminate = false;
  }
  private int[] tsdata = new int[msgLength];
  private final StringBuilder seednameSB = new StringBuilder(12);

  private synchronized void processGCF(ByteBuffer b, int length) {
    String seedname;        // 12 character seed name
    double rate;
    short nsamp = 0;
    byte[] seedArray = new byte[12];
    int doy;
    int sec;
    int usec;
    int activity = 0;
    int ioClock = 0;
    int quality = 0;
    int timingQuality = 0;
    int compressionCode;
    int nrecords;
    int ttl;
    int year;
    int seq = 0;
    long seq64 = -1;
    int termRouting = -1;
    int gain = 0;
    int type = -1;
    b.position(0);
    if (length < 16) {
      b.get(scratch, 0, length);
      StringBuilder status = Util.toAllPrintable(new String(scratch));
      if (length >= 7) {
        if (status.substring(0, 7).equals("GCFACKN") && dbg) {
          prta(tag + "GCFACKN received");
        }
      }
      return;
    }
    systemID = b.getInt();
    streamID = b.getInt();
    dateCode = b.getInt();
    setDateFromDateCode(dateCode, start);
    dataFormat = b.getInt();
    ttl = (dataFormat >> 24) & 0xff;
    rate = (dataFormat >> 16) & 0xff;
    if (rate >= 151 && rate <= 199) {
      switch ((int) (rate + .0001)) {
        case 157:
          rate = 0.1;
          break;
        case 161:
          rate = 0.125;
          break;
        case 162:
          rate = 0.2;
          break;
        case 164:
          rate = 0.25;
          break;
        case 167:
          rate = 0.5;
          break;
        case 171:
          rate = 400.;
          break;
        case 174:
          rate = 500.;
          break;
        case 176:
          rate = 1000.;
          break;
        case 179:
          rate = 2000.;
          break;
        case 181:
          rate = 4000.;
          break;
        default:
          prta(tag + "Found unknown data rate=" + rate);
          break;
      }
    }
    compressionCode = (dataFormat >> 8) & 0x7F;
    nrecords = (dataFormat & 0xFF);
    if (systemID > 0) {
      systemIDString = extractBase36(systemID, 6);
    } else {
      systemIDString = extractBase36(systemID, 5);
      type = (systemID >> 26) & 1;
      gain = (systemID >> 27) & 0xf;
    }
    streamIDString = extractBase36(streamID, 6);
    if (gain >= 8) {
      prta(tag + "Got bad gain from systtemID string=" + systemID + " systemIDstring=" + systemIDString);
    }
    if (dbg) {
      prta(tag + "Got packet len=" + length + " sys/str=" + systemIDString + "/"
              + streamIDString + " sq=" + seq64 + " typ/gain=" + type + "/" + gain + "(" + gains[Math.min(gain, 8)] + ") ttl=" + ttl + " " + Util.ascdate(start) + " "
              + Util.asctime2(start) + " rt=" + rate + " cmp=" + compressionCode + " nrec=" + nrecords);
    }

    // Now the data portion is the Forward IC followed by nrecords of i*4 formated by compression code
    if (rate == 0) {
      int pos = b.position();
      b.position(1024);
      version = b.get();
      String source = "";
      if (version == 31) {
        byte len = b.get(0);
        b.get(scratch, 0, 32);
        seq = b.getShort();
        byte order = b.get();
        source = new String(scratch, 0, (((int) len) & 0xff));
      } else if (version == 40 || version == 45) {
        byte order = b.get();
        seq = b.getShort();
        byte len = b.get();
        b.get(scratch, 0, 48);
        source = new String(scratch, 0, (((int) len) & 0xff));
        if (version == 45) {
          termRouting = b.getInt();
          seq64 = b.getLong();
          gaplist.gotSeq(seq64);
          if (seq64 % 200 == 0) {
            gaplist.writeGaps(false);
          }
        }
      }
      b.position(pos);
      if (compressionCode != 4) {
        prta(tag + "Got status block with weird compression code!!!! comp=" + compressionCode + " rate=" + rate + " blocktype=" + (systemID % 1296));
      } else if ((streamID % 1296) == 0) {
        if (nrecords > 0) {
          b.get(scratch, 0, nrecords * 4);
          StringBuilder status = Util.toAllPrintable(new String(scratch, 0, nrecords * 4));
          if (dbg) {
            prta(tag + "Status=\n" + Util.stringBuilderReplaceAll(status, "0xd0xa", "\n"));
          }
        } else if (dbg) {
          prta(tag + "Status was zero or less length=" + nrecords);
        }
        return;
      } else if ((streamID % 1296) == 1) {
        // The manual Scream Unified status packets Guralp WSA-RFC-UNIS was used in this section
        prta(tag + "Unified status block found (ignore)  nrec=" + nrecords);
        int off = 0;      // Point to tag
        while (off < nrecords) {
          int tag2 = b.getInt();
          int nwords = tag2 & 0xff;
          tag2 = tag2 >> 8;
          off += nwords + 2;      // Point to next tag
          if (tag2 == 0) {        // Clock status section 2.1
            int word1 = b.getInt();
            clockLastSyncGCF = b.getInt();
            locked = (word1 < 0);
            clockSource = (word1 >> 24) & 7;
            clockUSecOff = word1 & 0xffffff;
          } else if (tag2 == 2) {  // GPS Receiver status section 2.2
            int word1 = b.getInt();
            int format = (word1 >> 4) & 0xf;
            fix = word1 & 0xf;
            if (format == 0) {  // DM24mk3 (?5121.6655,N,00109.8456,W,00113,M?, 32 bytes)
              b.get(scratch, 0, 32);
              String temp = new String(scratch, 0, 32);
              latitude = Double.parseDouble(temp.substring(0, 9)) / 100.;
              if (temp.charAt(10) == 'S') {
                latitude = -latitude;
              }
              longitude = Double.parseDouble(temp.substring(12, 22)) / 100.;
              if (temp.charAt(23) == 'W') {
                longitude = -longitude;
              }
              elevation = Integer.parseInt(temp.substring(25, 30));
              char unit = temp.charAt(31);
              switch (unit) {
                case 'F':
                case 'f':
                  elevation /= 0.3048;
                  break;
                case 'M':
                case 'm':
                  elevation = elevation * 1.0;
                  break;
                default:
                  prta(tag + " ***** GCF Unified Status Packet got unknown elevation unit=" + unit);
                  break;
              }

            }
            if (format == 1) { // Degrees/m (?+51.216655-001.098456+000113.000?, 32 bytes)
              b.get(scratch, 0, 32);
              String temp = new String(scratch, 0, 32);
              latitude = Double.parseDouble(temp.substring(0, 10));
              longitude = Double.parseDouble(temp.substring(10, 21));
              elevation = Integer.parseInt(temp.substring(21, 32));
            } else {
              prta(tag + " ****** GCF Unified status packet : got unknown format for clock decoding=" + format);
            }
          } else if (tag2 >= 256 && tag2 < 511) { // Channel quality flags section 2.2
            int instrument = (tag2 >> 15) & 1;
            int chan = (tag2 >> 8) & 0x7f;
            int flags = b.getInt();
            prta(tag + "Got channel flags inst=" + instrument + " chan=" + chan + " flags=" + Util.toHex(flags));
          } else {
            prta(" ****** GCF Unified Status Packet Unknown tag=" + tag2);
          }
        }
        return;

      } else if ((streamID % 1296) == 1030) {    // Strong motion block
        prta(tag + "Strong motion block (ignore) nrec=" + nrecords);
        return;
      } else if ((streamID % 1296) == 445) {    // Strong motion block
        prta(tag + "CD status block (ignore) nrec=" + nrecords);
        return;
      } else if ((streamID % 1296) == 421) {    // Byte pipe
        prta(tag + "Byte Pipe block (ignore) nrec=" + nrecords);
        return;
      }
    } else {
      /*The DM24 organizes the data it produces into streams. Each stream
      has a 6-character identifier. The first four characters are taken from the
      System ID of the digitizer. When you receive the instrument, the
      System ID is set to it's serial number, but you can change it in Scream!
      or with a terminal command.
      The next character denotes the component or output type:
      ? Z, N, and E denote the vertical, north/south, and east/west
      components respectively.
      ? X denotes the fourth full-rate data channel, which is provided
      for connection to your own monitoring equipment via the
      AUXILIARY connector (if present).
      ? C denotes the calibration input channel, which replaces the X
      streams whilst calibration is in progress.
      ? M denotes one of the 16 slow-rate Mux (multiplex) channels.
      Three of these (M8, M9 and MA) are used to report the sensor
      mass positions. Channels MC and MD are connected to the X and
      Y axes of the downhole inclinometer.
      ? For Z, N, E, X, and C streams, the last character represents the
      output tap. Taps correspond to stages in the decimation process
      within the digitizer, allowing the DM24 to output several
      different data rates simultaneously. There are four taps,
      numbered 0 to 3; 0 has the highest data rate and 3 the lowest.
      Data streams end in 0, 2, 4 and 6 for taps 0, 1, 2 and 3
      respectively.
      If you configure the DM24 to output triggered data, this will
      appear in separate streams ending with the letters G, I, K or M
      for taps 0, 1, 2 and 3 respectively.
      The DM24 also generates a stream ending 00. This is a status stream
      containing useful diagnostic information, in plain text form (see
      Section 6.1, page 89).
       */
      String name = streamIDString.substring(0, 4);    // What the instrument knows itself as STOPPED HERE!!!!!!!!!!

      int val = b.getInt();
      nsamp = 0;
      for (int i = 0; i < nrecords; i++) {
        int diffs = b.getInt();
        switch (compressionCode) {
          case 1:   // 32 bit differences
            val = val + diffs;
            tsdata[nsamp++] = val;
            break;
          case 2:    // 2 16 bit differences
            int df = diffs & 0xffff;
            if ((df & 0x8000) != 0) {
              df |= 0xffff0000;
            }
            val += df;
            tsdata[nsamp++] = val;
            df = diffs >> 16;
            val += df;
            tsdata[nsamp++] = val;
            break;
          case 4:  // 4 1 byte differences
            for (int j = 0; j < 4; j++) {
              df = diffs & 0xff;
              if ((df & 0x80) != 0) {
                df |= 0xffffff00;
              }
              val += df;
              tsdata[nsamp++] = val;
              diffs = diffs >> 8;
            }
            break;
          default:
            prta("(***** unknown compression to Scream=" + compressionCode + " name=" + name
                    + " vers=" + version + " " + Util.toHex(dataFormat) + " sys=" + systemIDString + " str=" + streamIDString);
        }
      }

      int ric = b.getInt();
      if (nsamp > 0) {
        if (tsdata[nsamp - 1] != ric) {
          prta(tag + "BAD RIC on packet = " + ric + "!=" + tsdata[nsamp]);
        } else if (dbg) {
          prta(tag + "RIC is O.K.=" + ric);
        }
      }
    }
    b.position(1024);
    version = b.get();
    String source;
    switch (version) {
      case 31: {
        byte len = b.get(0);
        b.get(scratch, 0, 32);
        seq = b.getShort();
        byte order = b.get();
        //source = new String(scratch, 0, (((int) len) & 0xff));
        break;
      }
      case 40:
      case 45: {
        byte order = b.get();
        seq = b.getShort();
        byte len = b.get();
        b.get(scratch, 0, 48);
        source = new String(scratch, 0, (((int) len) & 0xff));
        if (version == 45) {
          termRouting = b.getInt();
          seq64 = b.getLong();
          gaplist.gotSeq(seq64);
          if (seq64 % 200 == 0) {
            gaplist.writeGaps(false);
          }
        }
        year = start.get(Calendar.YEAR);
        doy = start.get(Calendar.DAY_OF_YEAR);
        sec = dateCode & 0x1ffff;
        usec = 0;
        // The seedname comes from the configuration and the
        ScreamUnitConfig unit
                = (configSource ? units.get(source.substring(0, source.indexOf("-")).toUpperCase())
                        : units.get(streamIDString.substring(0, 4).toUpperCase()));
        if (unit == null) {
          seedname = "XX" + streamIDString.substring(0, 4) + " " + "BH" + streamIDString.substring(5, 6) + "99";
          if (System.currentTimeMillis() - lastSME > 120000) {
            SendEvent.edgeSMEEvent("ScreamNotCnf", tag + " stream=" + streamIDString + " source=" + source + " is not configed", this);
            lastSME = System.currentTimeMillis();
          }
        } else {
          seedname = unit.getSeedName(streamIDString, rate);
        }
        if (dbg) {
          prta(tag + "Vers=" + version + " ord=" + order + " sq=" + seq64 + " ns=" + nsamp + " " + seedname + " "
                  + year + "," + doy + " " + Util.asctime2(start) + " rt=" + rate + " ttl=" + ttl + " mk2=" + type
                  + " source=" + source + " strID=" + streamIDString + " unit=" + unit);
        }
        if (!seedname.substring(0, 2).equals("XX") && seedname.charAt(7) != 'X') {
          Util.clear(seednameSB).append(seedname);    // HACK : not ready to make seednames StringBuilders everywhere
          Util.rightPad(seednameSB, 12);

          RawToMiniSeed.addTimeseries(tsdata, (int) nsamp, seednameSB, year, doy, sec,
                  usec, rate, activity, ioClock, quality, timingQuality,
                  (EdgeThread) this);
          if (!nohydra) {
            Hydra.send(seednameSB, year, doy, sec, usec, nsamp, tsdata, rate);
          }
          if (csend != null) {
            try {
              csend.send(SeedUtil.toJulian((int) year, (int) doy), sec * 1000 + usec / 1000, seednameSB,
                      (int) nsamp, rate, (int) nsamp);
            } catch (IOException e) {
              prta(tag + "GCF: got IOException sending channel " + e.getMessage());
            }
          }
        }
        break;
      }
      default:
        prta(tag + " Uknown GCF version=" + version);
        break;
    }
  }

  /**
   * extract a base 36 string from input * 32 and a number of letters
   *
   * @param input The input integer
   * @param nletter The number of letters to extract
   * @return
   */
  private String extractBase36(int input, int nletter) {
    if (sbtmp.length() > 0) {
      sbtmp.delete(0, sbtmp.length());
    }
    char ch;
    for (int i = 0; i < nletter; i++) {
      int code = Math.abs(input % 36);
      input = input / 36;
      if (code < 10) {
        ch = (char) ('0' + code);
      } else {
        ch = (char) ('A' + (code - 10));
      }
      if (ch < '0' || ch > 'Z') {
        prta(tag + "invalid character code=" + code + " ch=" + ch);
      }
      sbtmp.insert(0, ch);
    }
    return sbtmp.toString();
  }

  private void setDateFromDateCode(int dateCode, GregorianCalendar g) {
    int days = (dateCode >> 17) & 0x7fff;
    int secs = dateCode & 0x1ffff;
    g.setTimeInMillis(fudicialDate + days * 86400000L + secs * 1000);
  }

  /**
   * when called read in the configuration file
   *
   */
  private void readConfigFile() {
    try {
      try (BufferedReader in = new BufferedReader(new FileReader(configFile))) {
        String line;
        while ((line = in.readLine()) != null) {
          if (line.length() < 1) {
            continue;
          }
          if (line.substring(0, 1).equals("#") || line.substring(0, 1).equals("!")) {
            continue;
          }
          String[] parts = line.split(":");
          if (parts.length != 2) {
            prta(tag + "Illegal line in configfile =" + line + " skip....");
            continue;
          }
          ScreamUnitConfig u = units.get(parts[0].toUpperCase());
          if (u == null) {
            u = new ScreamUnitConfig(parts[0], parts[1]);
            units.put(parts[0].toUpperCase(), u);
          } else {
            u.load(parts[1]);
          }
        }
      }
    } catch (IOException expected) {

    }
  }

  public final class GCFGapFiller extends Thread {

    byte[] bbuf = new byte[msgLength];
    byte[] bout = new byte[10];
    ByteBuffer bf, bo;
    int totalBytes = 0;
    TimeoutSocketThread timeout;
    Socket sf = null;

    public void terminate() {
      if (sf != null) {
        try {
          sf.close();
        } catch (IOException expected) {
        }
      }
    }

    public GCFGapFiller() {
      bf = ByteBuffer.wrap(bbuf);
      bo = ByteBuffer.wrap(bout);
      timeout = new TimeoutSocketThread("FillTO", null, 60000);
      start();
    }

    @Override
    public void run() {
      prta(tag + "gaps start " + gaplist.toString());
      try {
        while (!terminate) {
          int ngaps = gaplist.getGapCount();
          if (ngaps > 0) {
            long low = gaplist.getLowSeq(0);
            long high = gaplist.getHighSeq(0);
            fillGap(low, high);
            prta(tag + "gaps aft " + gaplist.toString());
          }
          try {
            sleep(ngaps == 0 ? 15000 : 1000);
          } catch (InterruptedException expected) {
          }
        }
      } catch (RuntimeException e) {
        prta(tag + "GCFGapFiller runtime exception e=" + e);
        e.printStackTrace(getPrintStream());
      }
      prta(tag + "Fill has been terminated");
    }

    private boolean fillGap(long low, long high) {
      prta(tag + "Fill try gap " + low + "-" + high);
      boolean result = false;

      try {
        int sysID;
        int strID;
        int dtCode = 0;
        int dtFormat;
        int compCode = 0;
        int ttl = 0;
        double rate = 0;
        int nrecords;
        sf = new Socket(ip, port);
        timeout.setSocket(sf);
        timeout.enable(true);
        bout[0] = (byte) 0xF8;
        bout[1] = (byte) 0xFC;
        int loop = 0;
        sf.getOutputStream().write(bout, 0, 2);
        while (sf.getInputStream().available() < 10 && loop < 30) {
          if (terminate) {
            break;
          }
          loop++;
          try {
            sleep(1000);
          } catch (InterruptedException expected) {
          }
        }
        if (sf.getInputStream().available() < 10) {
          prta(tag + " **** get version failed.   Give up for now! avail=" + sf.getInputStream().available());
          sf.close();
          timeout.setSocket(null);
          timeout.enable(false);
          return false;
        }
        int len = sf.getInputStream().read(bbuf, 0, sf.getInputStream().available());
        totalBytes += len;
        String version = new String(bbuf, 0, len);
        prta(tag + "Version return =" + version);
        if (!version.contains("4.5") && !version.contains("4.6")) {
          prta(tag + "WARNING: server is not version 4.5 or 4.6! version=" + version);
          SendEvent.edgeSMEEvent("ScreamNot45", "Scream version not 4.5 or 4.6 on ip=" + ip + " version", this);
        }
        bout[1] = (byte) 0xfe;
        sf.getOutputStream().write(bout, 0, 2);   /// send request for oldest 64 byte sequence
        len = sf.getInputStream().read(bbuf, 0, 8);     // this should return 8 bytes
        totalBytes += len;
        bf.position(0);
        long oldest = bf.getLong();
        prta(tag + "Oldest returns as " + oldest);

        // If all of these blocks in the gap are before the oldest, just zap them and leave
        if (oldest >= high) {
          gaplist.trimList(oldest);
          sf.close();
          timeout.setSocket(null);
          timeout.enable(false);
          return false;
        }
        // Oldest is in the gap, zap below it
        if (oldest < high && oldest > low) {
          gaplist.trimList(oldest);
          low = oldest;
        }
        // For every one from low to high, to get it
        bout[1] = (byte) 0xFF;     // request a single block
        for (long blk = low; blk < high; blk++) {
          if (terminate) {
            break;
          }
          bo.position(2);
          bo.putLong(blk);
          sf.getOutputStream().write(bout, 0, 10);
          len = sf.getInputStream().read(bbuf, 0, 4);     // Get first 4 ints so we can figure out the record size
          totalBytes += len;
          if (bbuf[0] == -1 && bbuf[1] == -1 && bbuf[2] == -1 & bbuf[3] == -1) { // block does not exist, mark it as received
            if (dbg) {
              prta(tag + "Fill blk=" + blk + " **** is not available");
            }
            gaplist.gotSeq(blk);
            continue;
          }
          len = sf.getInputStream().read(bbuf, 4, 12);    // get first 4 ints so length can be decoded
          totalBytes += len;
          int n = sf.getInputStream().available();
          bf.position(0);
          sysID = bf.getInt();
          strID = bf.getInt();
          dtCode = bf.getInt();
          dtFormat = bf.getInt();
          ttl = (dtFormat >> 24) & 0xff;
          rate = (dtFormat >> 16) & 0xff;
          compCode = (dtFormat >> 8) & 0xFF;
          nrecords = (dtFormat & 0xFF);
          len = sf.getInputStream().read(bbuf, 16, 1073);
          totalBytes += len;
          if (n != 1073) {
            prta(tag + "Fill **** block is not 1089 bytes long! n=" + n);
          }
          prta(tag + "Fill blk=" + blk + " nrec=" + nrecords + " available=" + n + " systemid=" + Util.toHex(sysID) + " strID=" + Util.toHex(strID));
          processGCF(bf, 16 + n);
          timeout.resetTimeout();
        }
        result = true;
      } catch (IOException e) {
        prta(tag + "Fill got IOError e=" + e);
        e.printStackTrace(getPrintStream());
      }
      timeout.setSocket(null);
      timeout.enable(false);
      if (!sf.isClosed()) {
        try {
          sf.close();
        } catch (IOException expected) {
        }
      }
      return result;
    }
  }

  public final class GCFSend extends Thread {

    DatagramSocket gcfsender;
    DatagramPacket gcfsendpkt;
    byte[] gcfbuf = new byte[100];
    EdgeThread par;
    private int counter;

    public GCFSend(EdgeThread parent, DatagramSocket udp) {
      par = parent;
      gcfsender = udp;
      gcfsendpkt = new DatagramPacket(gcfbuf, 100);
      System.arraycopy("GCFSEND:B".getBytes(), 0, gcfbuf, 0, 9);
      gcfbuf[9] = 0;
      try {
        gcfsendpkt.setAddress(InetAddress.getByName(ip));
        gcfsendpkt.setPort(port);
        gcfsendpkt.setData(gcfbuf, 0, 10);
        gcfsendpkt.setLength(10);
      } catch (IOException e) {
        par.prta("GCFSEND: could not setup datagramm packet e=" + e);
      }
      start();
    }

    @Override
    public void run() {
      counter = 0;
      while (!terminate) {
        try {
          gcfsender.send(gcfsendpkt);
          if (dbg) {
            par.prta("Send len=" + gcfsendpkt.getLength() + " to " + gcfsendpkt.getSocketAddress() + "/"
                    + gcfsendpkt.getPort() + " " + Util.toAllPrintable(new String(gcfsendpkt.getData(), 0, gcfsendpkt.getLength())));
          }
          counter++;
        } catch (IOException e) {
          par.prta("IOError sending GCFSEND packet! e=" + e);
        }
        try {
          sleep(120000);
        } catch (InterruptedException expected) {
        }
      }
      par.prta("GFCSend: Exiting thread");

    }
  }

  public class ScreamUnitConfig {

    String unit;
    String station;
    String network;
    String location;
    String componentMask;
    String sensorABand;
    String sensorBBand;
    String lastLoadLine = "";

    @Override
    public String toString() {
      return "SUC: " + unit + " " + network + station + " mask=" + componentMask + " ABand=" + sensorABand + " BBand=" + sensorBBand;
    }
//

    public String getSeedName(String streamID, double rate) {
      String seedname;
      if (overrideSeedNetwork == null) {
        seedname = network;
      } else {
        seedname = overrideSeedNetwork;
      }
      if (station == null) {
        seedname += (streamID.substring(0, 4) + "    ").substring(0, 5);
      } else {
        seedname += station;
      }
      char ch5 = streamID.charAt(4);
      if (ch5 == 'Z' || ch5 == 'N' || ch5 == 'E') {
        seedname += MiniSeed.getBandFromRate(rate, (((int) streamID.charAt(5)) & 1) == 0
                ? Character.isLowerCase(sensorABand.charAt(0)) : Character.isLowerCase(sensorBBand.charAt(0)));
        if ((((int) streamID.charAt(5)) & 1) == 0) {
          seedname += sensorABand.toUpperCase();
        } else {
          seedname += sensorBBand.toUpperCase();
        }
        if (componentMask == null) {
          seedname += ch5;
        } else {
          switch (ch5) {
            case 'Z':
              seedname += componentMask.substring(0, 1);
              break;
            case 'N':
              seedname += componentMask.substring(1, 2);
              break;
            case 'E':
              seedname += componentMask.substring(2, 3);
              break;
            default:
              break;
          }
        }
        if (location == null) {
          seedname += "  ";
        } else {
          seedname += ((((int) streamID.charAt(5)) & 1) == 0 ? location.substring(0, 2) : location.substring(3, 5));
        }
        return seedname;
      }
      //if(ch5 == '')

      return (seedname + "X       ").substring(0, 12);
    }

    public ScreamUnitConfig(String unitin, String argline) {
      unit = unitin;
      load(argline);
      lastLoadLine = argline;
    }

    /**
     * load an existing unit with a new command line
     *
     * @param argline The line to load
     */
    public final void load(String argline) {
      if (argline.equals(lastLoadLine)) {
        return;
      }
      lastLoadLine = argline;
      argline = argline.replaceAll("  ", " ");
      argline = argline.replaceAll("  ", " ");
      argline = argline.replaceAll("  ", " ");
      String[] args = argline.split("\\s");
      componentMask = null;
      location = "00";
      network = "XX";
      for (int i = 0; i < args.length; i++) {
        if (args[i].equals("-s")) {
          station = (args[i + 1] + "     ").substring(0, 5);
          i++;
        }
        if (args[i].equals("-n")) {
          network = (args[i + 1] + "  ").substring(0, 2);
          i++;
        }
        if (args[i].equals("-l")) {
          location = (args[i + 1] + "     ").substring(0, 5).replaceAll("-", " ");
          i++;
        }
        if (args[i].equals("-c")) {
          componentMask = args[i + 1];
          i++;
        }
        if (args[i].equals("-bands")) {
          sensorABand = args[i + 1].substring(0, 1);
          if (args[i + 1].length() >= 2) {
            sensorBBand = args[i + 1].substring(1, 2);
          }
          i++;
        }
      }
    }
  }

  /*@param args Unused command line args*/
  public static void main(String[] args) {
    Util.setModeGMT();
    IndexFile.init();
    EdgeProperties.init();

    ScreamClient server = new ScreamClient("-p 7982 -dbg", "RIUS");
  }

}
