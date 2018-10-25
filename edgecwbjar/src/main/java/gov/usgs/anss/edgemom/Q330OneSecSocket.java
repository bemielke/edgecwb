/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.edgemom;

import gov.usgs.anss.edge.IndexFile;
import gov.usgs.anss.edge.EdgeProperties;
import gov.usgs.anss.edge.*;
import gov.usgs.anss.edgeoutput.TraceBuf;
import gov.usgs.anss.edgethread.EdgeThread;
import gov.usgs.anss.seed.MiniSeed;
import gov.usgs.anss.util.Util;
import gov.usgs.edge.config.Channel;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.GregorianCalendar;
import gnu.trove.map.hash.TLongObjectHashMap;
//import gnu.trove.iterator.TLongObjectIterator;

/**
 * This class is created by a socket connection to Q330OneSecServer and reads
 * the one second packets and processes them to Hydra. It is configured entirely
 * via the server set up calls.
 * <br>
 * NOTE:  This code only works with the 32 bit version of ANSSQ330 as the 64 bit changes
 * the 1 second packet.
 *
 * @author davidketchum
 */
public final class Q330OneSecSocket extends EdgeThread {

  private int port;
  private String host;
  private int countMsgs;
  private final int msgLength;
  private final StringBuilder station = new StringBuilder(8);
  private Socket d;           // Socket to the server
  private long lastStatus;     // Time of last status was received
  private InputStream in;
  private OutputStream output;
  private EdgeThread par;
  private TraceBuf tb;         // Trace buf for hydra sender
  private final TLongObjectHashMap<Integer> pinnos = new TLongObjectHashMap<>();
  private final TLongObjectHashMap<LPBuffer> lpbuffers = new TLongObjectHashMap<>();

  // Performance measurements
  private long nbytesIn;
  private int npacket;

  private long lastNbytesIn;
  private int lastNpacket;
  private int last24Npacket;
  private long last24NbytesIn;
  private long lastStatus24;

  private int seq;
  private boolean hydra;
  private boolean dbg;
  private int minsamps;
  private final StringBuilder tmpsb = new StringBuilder(50);

  public void setDebug(boolean t) {
    dbg = t;
  }

  /**
   * Creates an new instance of Q330OneSecSocket - these are generally created
   * by the Q330OneSecondServer.
   *
   * @param s The socket for this connection.
   * @param parent The parent server for logging.
   * @param tg The tag to use for logging from this mode.
   * @param hydraFlag If true, send this data to hydra.
   */
  public Q330OneSecSocket(Socket s, Q330OneSecServer parent, String tg, boolean hydraFlag) {
    super("", tg);
    tag += "Q330Skt:[" + s.getInetAddress().getHostAddress() + "/" + s.getLocalPort() + "]";
    //this.setPrintStream(parent.getPrintStream());   // Use te same log as the server
    setEdgeThreadParent(parent);        // Set to log through the parent
    d = s;
    minsamps = 30;
    par = parent;
    hydra = hydraFlag;
    host = s.getInetAddress().getHostAddress();
    port = s.getPort();
    if (hydra) {
      tb = new TraceBuf(new byte[TraceBuf.TRACE_LENGTH]);        // Trace buf for sending data to HydraOutputer
    }
    msgLength = 2048;
    try {
      in = s.getInputStream();
      output = s.getOutputStream();
    } catch (IOException e) {
      Util.IOErrorPrint(e, "Error getting i/o streams from socket " + host + "/" + port);
      return;
    }
    countMsgs = 0;
    par.prt(Util.clear(tmpsb).append("Q330OneSecSocket: new line parsed to host=").append(host).
            append(" port=").append(port).append(" len=").append(msgLength).append(" dbg=").append(dbg).append(" hydra=").append(hydra));
    IndexFile.init();
    start();
  }

  @Override
  public void terminate() {
    terminate = true;
    if (in != null) {
      par.prta("Q330Skt: Terminate started. Close input unit.");
      try {
        in.close();
        output.close();
      } catch (IOException expected) {
      }
    } else {
      par.prt("Q330Skt: Terminate started. interrupt().");
      interrupt();
    }
  }

  /**
   * This Thread keeps a socket open to the host and port, reads in any
   * information on that socket, keeps a list of unique StatusInfo, and updates
   * that list when new data for the unique key comes in.
   */
  @Override
  public void run() {
    byte[] buf = new byte[msgLength];
    ByteBuffer bb = ByteBuffer.wrap(buf);
    int[] samples = new int[200];
    running = true;               // Mark that we are running

    // Read data from the socket and update/create the list of records 
    int len = msgLength;
    int l = 0;
    int nchar = 0;
    lastStatus = System.currentTimeMillis();
    lastStatus24 = lastStatus;
    long JAN_01_2000 = new GregorianCalendar(2000, 0, 1).getTimeInMillis();
    int nsamp = 0;
    int nsleft = 0;
    int[] data = null;      // Need to decompress the data
    try {
      sleep(500);
    } catch (InterruptedException expected) {
    }
    par.prta(Util.clear(tmpsb).append(tag).append(" Socket is opened.  Start reads. ").
            append(host).append("/").append(port).append(" dbg=").append(dbg));
    /* Per Q330LIB docs the header for a one second packet is :
     * 0   0  long   totalsize
     * 4   8  context context          A pointer to something opaque about the context
     * 8   16 string9 station_name
     * 18 string2 location
     * channel_number (according to tokens)
     * 22  30 string3 channel
     * word  padding
     * 28  40 int rate
     * 32  48 long c1_session
     * 36  56 long reserved
     * 40  64 double cl_offset   Closed loop time offset
     * 48  72 short filter_bits
     * 58  short qual_perc
     * 60    short activity_flags
     * 62  short io_flags
     * 64  short data_quality_flags
     * 66  byte src_channel
     * 67  byte src_subchannel
     * 68  longint samples
     *
     */
    byte[] stationb = new byte[8];
    byte[] chanb = new byte[3];
    byte[] locationb = new byte[2];
    double ts;
    short clock_perc;
    short activity_flags;
    short io_flags;
    byte ch;
    byte subch;
    short data_quality_flag;
    Integer pinno;
    int size = 0;
    Channel chn;
    long start;
    int rate;
    GregorianCalendar now = new GregorianCalendar();
    boolean swap = false;
    boolean first = true;
    StringBuilder location = new StringBuilder(2);
    StringBuilder channel = new StringBuilder(3);
    StringBuilder seedname = new StringBuilder(12);
    while (!EdgeChannelServer.isValid()) {
      try {
        sleep(50);
      } catch (InterruptedException expected) {
      }
    }
    while (true) {
      if (terminate) {
        break;
      }
      try {
        len = 4;          // Desired block length
        nchar = Util.socketReadFully(in, buf, 0, len);
        if (nchar <= 0) {
          break;      // EOF - close up - go to outer infinite loop
        }
        bb.position(0);
        size = bb.getInt();
        len = size - 4;          // First 4 bytes contains the length
        if (dbg) {
          par.prta(Util.clear(tmpsb).append("Init read len=").append(size).append(" ").
                  append(Util.toHex(size)).append(" char=").append(nchar));
        }
        if (first) {                 // This is the first one
          first = false;
          if (size < 0 || size > msgLength) {   // Probably swapped
            bb.position(0);
            bb.order(ByteOrder.LITTLE_ENDIAN);
            size = bb.getInt();
            par.prta(Util.clear(tmpsb).append("Byte order looks reversed len = ").append(len).
                    append(" revised=").append(size));
            len = size - 4;
            swap = true;
          }
        }
        if(len + 4 > buf.length) {
          par.prta(Util.clear(tmpsb).append(tag).append(" * Increase 1 second buf size to ").append(len*2+4).
                  append(" was ").append(buf.length).append(" size=").append(size));
          ByteOrder order = bb.order();
          buf = new byte[len*2+4];        
          bb = ByteBuffer.wrap(buf);
          bb.order(order);
        }
        // Read in the rest of the record
        nchar = Util.socketReadFully(in, buf, 4, len);
        nbytesIn += len;
        npacket++;

        //for(int i=0; i<size; i++) prt(i+" = "+buf[i]+" "+Util.toAllPrintable(Character.valueOf((char) buf[i]).toString()));
        if (nchar <= 0) {
          break;      // EOF - close up - go to outer infinite loop
        }
        bb.position(8);     // Position network and station
        zero(stationb);
        bb.get(stationb);
        fix(stationb);
        Util.clear(station);
        for (int i = 0; i < 8; i++) {
          if (i != 2) {
            station.append((char) stationb[i]);
          }
        }
        //station = new String(stationb);
        bb.position(18);
        zero(locationb);

        bb.get(locationb);
        fix(locationb);
        Util.clear(location);
        for (int i = 0; i < 2; i++) {
          location.append((char) locationb[i]);
        }
        //String location = new String(locationb);
        bb.position(22);
        zero(chanb);
        bb.get(chanb);
        fix(chanb);
        Util.clear(channel);
        for (int i = 0; i < 3; i++) {
          channel.append((char) chanb[i]);
        }
        //String channel = new String(chanb);
        bb.position(28);
        rate = bb.getInt();
        if(rate > samples.length) {
          samples = new int[rate*2];
        }
        bb.position(48);
        ts = bb.getDouble();
        bb.position(58);
        clock_perc = bb.getShort();
        activity_flags = bb.getShort();
        io_flags = bb.getShort();
        data_quality_flag = bb.getShort();
        ch = bb.get();
        subch = bb.get();
        if (dbg) {
          par.prta(Util.clear(tmpsb).append("1sec rt=").append(rate).append(" clk%=").append(clock_perc).
                  append(" act=").append(activity_flags).append(" io=").append(io_flags).
                  append(" dqual=").append(data_quality_flag).append(" ch=").append(ch).append(" subch=").append(subch));
        }
        for (int i = 0; i < rate; i++) {
          try {
            samples[i] = bb.getInt();
          }
          catch(RuntimeException e) {
            par.prta(Util.clear(tmpsb).append(tag).append(station).append(channel).
                    append(" ArrayIndex err 1sec rt=").append(rate).append(" i=").append(i).
                    append(" ").append(bb.toString()));
            break;
          }
        }
        countMsgs++;
        //String seedname = station.substring(0,2)+station.substring(3)+channel+location;
        Util.clear(seedname).append(station).append(channel).append(location);
        if (!Util.isValidSeedName(seedname)) {
          par.prt(Util.clear(tmpsb).append("Got an illegal seedname !").append(Util.toAllPrintable(seedname)));
        }
        start = JAN_01_2000 + (long) (ts * 1000. + 0.5);
        now.setTimeInMillis(start);
        if (dbg) {
          par.prta(Util.clear(tmpsb).append("1sec ").append(seedname).append(" ").
                  append(Util.ascdatetime2(now, null)).append(" #msg=").append(countMsgs));
        }
        if (hydra) {
          char ch2 = seedname.charAt(7);
          if (ch2 != 'B' && ch2 != 'S' && ch2 != 'H' && ch2 != 'L' && ch2 != 'M' && ch2 != 'E') {
            continue;
          }
          ch2 = seedname.charAt(8);
          if (ch2 != 'L' && ch2 != 'H') {
            continue;
          }
          synchronized (pinnos) {
            pinno = pinnos.get(Util.getHashFromSeedname(seedname));
            if (pinno == null) {
              chn = EdgeChannelServer.getChannel(seedname);
              if (chn != null) {
                pinno = chn.getID();
              } else {
                pinno = 0;
              }
              pinnos.put(Util.getHashFromSeedname(seedname), pinno);
            }
          }
          // If the data rate is less than 10 samples per second, aggregate before sending
          if (rate < 10) {
            synchronized (lpbuffers) {
              LPBuffer lp = lpbuffers.get(Util.getHashFromSeedname(seedname));
              if (lp == null) {
                lp = new LPBuffer(seedname, rate, minsamps, pinno);
                lpbuffers.put(Util.getHashFromSeedname(seedname), lp);
              }
              lp.process(now, rate, samples);
            }
          } else {
            tb.setData(seedname, now.getTimeInMillis(), rate, (double) rate, samples, 0, 0, 0, 0, pinno);  // inst, module, etc set by the HydraOutputer
            if (dbg) {
              par.prta(Util.clear(tmpsb).append(seedname).append(" ").append(" to Hydra rt=").
                      append(rate).append(" ").
                      append(Util.ascdatetime2(now, null)).append(" ").append(tb.toStringBuilder(null)));
            }
            Hydra.sendNoChannelInfrastructure(tb);
          }
          if (dbg) {
            par.prta(Util.clear(tmpsb).append(seedname).append(" ").append(Util.asctime2(now)).
                    append(" rt=").append(rate).append(tb.toStringBuilder(null)));
          }
        }

      } catch (IOException e) {
        Util.SocketIOErrorPrint(e, "Q330OneSec: rcv");
        break;      // Drop out of read loop to connect looop
      } catch (RuntimeException e) {
        par.prt(Util.clear(tmpsb).append(tag).append(" RuntimeException : ").append(e).
                append(" nc=").append(nchar).append(" len=").append(len).append(" l=").append(l).
                append(" ns=").append(nsamp).append(" nleft=").append(nsleft).append(" sz=").append(size));
        if (data == null) {
          par.prt(tag + "      data=null");
        } else {
          par.prt(Util.clear(tmpsb).append(tag).append("      data.length=").append(data.length));
        }
        for (int i = 0; i < 48; i++) {
          par.prt(i + " = " + buf[i] + " " + Util.toAllPrintable(Character.valueOf((char) buf[i]).toString()));
        }
        e.printStackTrace(par.getPrintStream());
        terminate = true;
        break;
      }

    }     // while(true) Get data
    try {
      if (nchar <= 0) {
        par.prta(tag + " exited due to EOF");
      } else if (terminate) {
        par.prt(tag + " terminate found.");
      } else {
        par.prta(tag + " exited while(true) for read");
      }
      if (d != null) {
        d.close();
      }
    } catch (IOException e) {
      Util.IOErrorPrint(e, tag + " socket- reopen");
    }
    par.prt(tag + " is terminated.");
    running = false;
    terminate = false;
  }

  private void fix(byte[] b) {
    for (int i = 0; i < b.length; i++) {
      if (b[i] < 32) {
        b[i] = 32;
      }
    }
  }

  private void zero(byte[] b) {
    for (int i = 0; i < b.length; i++) {
      b[i] = 0;
    }
  }

  /**
   * Return the monitor string for Icinga
   *
   * @return A String representing the monitor key value pairs for this
   * EdgeThread.
   */
  @Override
  public StringBuilder getMonitorString() {
    if (monitorsb.length() > 0) {
      monitorsb.delete(0, monitorsb.length());
    }
    return monitorsb;
  }

  public String getStatusString24() {
    long now = System.currentTimeMillis();
    String s = station + "1sec 24Hour  Summary : #bytes =" + (nbytesIn - last24NbytesIn) + " @ "
            + ((nbytesIn - last24NbytesIn) * 8000 / (now - lastStatus24)) + " bps #pkt=" + (npacket - last24Npacket)
            + " in " + (now - lastStatus24) / 1000 + " s";
    last24NbytesIn = nbytesIn;
    last24Npacket = npacket;
    lastStatus24 = now;
    return s;
  }

  @Override
  public StringBuilder getStatusString() {
    if (statussb.length() > 0) {
      statussb.delete(0, statussb.length());
    }
    long now = System.currentTimeMillis();
    long bps = (nbytesIn - lastNbytesIn) * 8000 / (now - lastStatus);
    statussb.append(station).append("1sec #bytes=").append(nbytesIn - lastNbytesIn).
            append(" @ ").append(bps).append(" bps #pkt=").append(npacket - lastNpacket);
    lastNbytesIn = nbytesIn;
    countMsgs = 0;
    lastNpacket = npacket;
    lastStatus = now;
    return statussb;
  }

  @Override
  public StringBuilder getConsoleOutput() {
    return consolesb;
  } //  We use prt directly

  /**
   * This internal class aggregates &lt 10 sps data into at least bigger chunks for sending to TraceWire
   */
  final class LPBuffer {

    int[] samps;               // Buffering for samples
    int nsamp;                  // The number of samples in samps 
    int rate;                   // The data rates in Hz
    StringBuilder seedname = new StringBuilder(12);            // The seedname of data
    int minsamps;               // The number of samples to cause output to Hydras
    int pinno;                  // Hydra Pin number
    GregorianCalendar start;    // The time of first sample in samps. if nsamps=0, the time of expected next data
    StringBuilder tmpsb = new StringBuilder(50);

    public LPBuffer(StringBuilder seed, int rt, int min, int pin) {
      minsamps = min;
      Util.clear(seedname).append(seed);
      rate = rt;
      nsamp = -1;                 // Indicates starting up.
      pinno = pin;
      samps = new int[minsamps + rt];// We ship whenever minsamps is exceeded, so buffer is minsamps + rate big
      start = new GregorianCalendar();// new gregorian, will be overriden with first buffer
      par.prta(Util.clear(tmpsb).append("NEW LPBUF for ").append(seedname).append(" min=").append(min).
              append(" rt=").append(rate).append(" pinno=").append(pinno));
    }

    public void process(GregorianCalendar now, int ns, int[] samples) {
      if (ns <= 0) {
        return; // Nothing to do
      }
      if (nsamp >= 0) {    // If not starting up
        // Check that this data is contiguous to prior buffering of data. If not, ship prior to buffering of data
        long diff = (now.getTimeInMillis() - start.getTimeInMillis() - nsamp * 1000 / rate);
        if (Math.abs(diff) > 500 / rate) {     // This data is out-of-order or overlapping
          if (diff < 0) {                    // Overlapping
            par.prta(Util.clear(tmpsb).append("Overlapping LPBUF=").append(seedname).append(" ").
                    append(Util.asctime2(now)).append(" ns=").append(ns).append(" rt=").append(rate).
                    append(" diff=").append(diff));
            return;              // Reject overlapping or past data
          }
          if (nsamp > 0) {       // Must be a gap, send any data bufferred to date, if any
            tb.setData(seedname, start.getTimeInMillis(), nsamp, (double) rate, samps, 0, 0, 0, 0, pinno);  // inst, module, etc set by the HydraOutputer
            par.prta(Util.clear(tmpsb).append("Gap LPBuf =").append(Util.asctime2(now)).append(" df=").append(diff).
                    append(" ns=").append(ns).append(" rt=").append(rate).append(" to ").append(tb.toStringBuilder(null)));
            Hydra.sendNoChannelInfrastructure(tb);
            par.prta("LPBuf send data3 " + seedname + " " + Util.ascdatetime2(start.getTimeInMillis(), null) + " ns=" + nsamp + " rt=" + rate);
            LHDerivedToQueryMom.sendDataQM(seedname, start.getTimeInMillis(), (double) rate, nsamp, samps);
            nsamp = 0;
          }
        }
      }
      if (nsamp <= 0) {
        start.setTimeInMillis(now.getTimeInMillis());
        if (nsamp == -1) {
          par.prta(Util.clear(tmpsb).append("LPBuf first data ").append(seedname).append(" ").append(Util.asctime2(now)));
        }
        nsamp = 0;
      }
      System.arraycopy(samples, 0, samps, nsamp, ns); // Put this data into the buffers
      nsamp += ns;                                    // Number of samples in the buffer now
      if (nsamp >= minsamps) {                        // Is it ship time?
        tb.setData(seedname, start.getTimeInMillis(), nsamp, (double) rate, samps, 0, 0, 0, 0, pinno);  // inst, module, etc set by the HydraOutputer
        //if(seedname.substring(0,10).equals("ZZDUG  LHZ")) 
        //  par.prta(Util.clear(tmpsb).append("Send LPBuf =").append(tb.toStringBuilder(null)));
        Hydra.sendNoChannelInfrastructure(tb);
        if (dbg) {
          par.prta("LPBuf send data2 " + seedname + " " + Util.ascdatetime2(start.getTimeInMillis(), null) + " ns=" + nsamp + " rt=" + rate);
        }
        LHDerivedToQueryMom.sendDataQM(seedname, start.getTimeInMillis(), (double) rate, nsamp, samps);
        nsamp = 0;
        start.setTimeInMillis(now.getTimeInMillis() + ns * 1000 / rate);  // Set the expected time to OOR/overlap check works
      }
    }
  }

  /**
   * @param args The command line arguments.
   */
  public static void main(String[] args) {
    Util.setModeGMT();
    IndexFile.init();
    EdgeProperties.init();
    RawDisk in;
    int len = 512;
    byte[] b = new byte[512];
    int iblk = 0;
    int l = 0;
    MiniSeed ms = null;
    Q330OneSecServer mss = new Q330OneSecServer("-p 2015", "msrv");
    EdgeBlockQueue ebq = new EdgeBlockQueue(10000);
    try {
      Socket s = new Socket("localhost", 2015);
      OutputStream out = s.getOutputStream();
      in = new RawDisk("/Users/ketchum/IUANMO_BHZ00.ms", "r");
      while ((l = in.readBlock(b, iblk, len)) > 0) {
        ms = new MiniSeed(b);
        //Util.prt("ms="+ms.toString());
        out.write(b, 0, len);
        iblk++;
      }
      Util.prt("Blks written=" + iblk);
      System.exit(0);
    } catch (IOException e) {
      Util.IOErrorPrint(e, "IOException caught in main iblk=" + iblk);
    } catch (IllegalSeednameException e) {
      Util.prt("Illegal seedname" + e.getMessage());
    }
    LISSClient[] liss = new LISSClient[10];
    IndexFile.setDebug(true);
    int i = 0;

  }

}
