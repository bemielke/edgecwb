/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.edgemom;

import gov.usgs.anss.edge.OutOfOrderRTMS;
import gov.usgs.anss.edge.RawToMiniSeed;
import gov.usgs.anss.edge.MasterBlock;
import gov.usgs.anss.edge.EdgeProperties;
import gov.usgs.anss.edge.ChannelSender;
import gov.usgs.alarm.SendEvent;
import gov.usgs.anss.edge.EdgeBlockQueue;
import gov.usgs.anss.edge.IllegalSeednameException;
import gov.usgs.anss.edgeoutput.TraceBuf;
import gov.usgs.anss.edgethread.EdgeThread;
import gov.usgs.anss.util.Util;
import gov.usgs.anss.util.SeedUtil;
import gov.usgs.edge.config.Channel;
import java.io.*;
import java.net.*;
import java.nio.*;
import java.util.*;
import gnu.trove.map.hash.TLongObjectHashMap;
//import gnu.trove.iterator.TLongObjectIterator;

/**
 *
 * RawInputSocket.java - This reads from a socket data in "RawInput" format.
 * This is just a 36 byte header with leadin, seedname, timing, flags, number of
 * samples, etc. followed by "in-the-clear" data samples. The data thus obtained
 * are then written via the RawToMiniSeed.addTimeseries() to enter the data into
 * the edge files. Times of I/O are kept so that sockets and objects can be
 * freed if stale. If configured for out-of-order data the OutOfOrderRTMS class
 * is used to process the data to MiniSeed. All of the configuration of this
 * class is done by the RawInputServer that creates it. See that class for more
 * detail of the configuration options.
 * <p>
 * Note: this class is never configured directly into an EdgeMom instance. It is
 * always created by a RawInputServer.
 *
 * Created on August 8, 2005, 3:12 PM
 *
 * @author davidketchum
 */
public final class RawInputSocket extends EdgeThread {

  private final TLongObjectHashMap<Integer> pinnos = new TLongObjectHashMap<>();
  private Socket s;               // Socket we will read forever (or misalignment)
  private final boolean oor;                // If true, use out of order processing.
  private boolean dbg;
  private OutOfOrderRTMS oorProcessor;
  private long bytesin;
  private long bytesout;
  private long countIn;
  private long lastRead;
  private int readSize;
  private final boolean hydra;          // Send data to hydra
  private final boolean hydraInorder;   // Use shortcut send to hydra (no channel infrastructure)
  private TraceBuf tb;
  private static boolean rawsave;
  private EdgeThread parent;
  private static int nrawout;              // number of bytes of raw debug output
  private FileInputStream rawfile;
  private static FileOutputStream rawout;
  private static final Integer rawmutex = Util.nextMutex();
  private final boolean socketLog;
  private double rawMaxRate = 10.;
  private final boolean rawSend;
  private ChannelSender csend;
  private final String dbgch;
  private final String superTag;
  StringBuilder tg = new StringBuilder(50);
  private final StringBuilder tmpsb = new StringBuilder(20);
  private final StringBuilder runsb = new StringBuilder(50);
  private final StringBuilder statsb = new StringBuilder(20);
  private int state;

  @Override
  public String toString() {
    return s + " #bytesin=" + bytesin;
  }

  public StringBuilder toStringBuilder(StringBuilder tmp) {
    StringBuilder sb = tmp;
    if (sb == null) {
      sb = Util.clear(statsb);
    }
    sb.append(s.toString()).append(" #bytesin=").append(bytesin);
    return sb;
  }

  /**
   * Set up a read of a captured file for debugging purposes
   *
   * @param file The file name for the raw output
   */
  public void setReadraw(String file) {
    try {
      rawfile = new FileInputStream(file);
    } catch (FileNotFoundException e) {
      prt(Util.clear(tmpsb).append("File not found in setReadraw=").append(file));
    }
  }

  /**
   * Set this socket to create a raw file of bytes read for debugging purposes
   */
  public void setRawsave() {
    rawsave = true;
    if (rawout == null) {
      try {
        rawout = new FileOutputStream(getTag().substring(0, 4) + ".out");
      } catch (FileNotFoundException e) {
        prt(Util.clear(tmpsb).append("could not create rawsave file=").append(getTag()));
        rawout = null;
      }
    }
  }

  @Override
  public void terminate() {
    setTerminate(true);
  }

  /**
   * Set the terminate variable.
   *
   * @param t If true, the run() method will terminate at next change and exit
   */
  public void setTerminate(boolean t) {
    terminate = t;
    if (terminate) {
      interrupt();
      if (s != null) {
        try {
          if (!s.isClosed()) {
            s.close();
          }
        } catch (IOException expected) {
        }
      }
    }
  }

  /**
   * Return a descriptive tg of this socket (basically "RIS:" plus the tg of
   * creation).
   *
   * @return A descriptive tg.
   */
  @Override
  public final String getTag() {
    return "RIS:" + tg;
  }

  /**
   * return the monitor string for Nagios
   *
   * @return A String representing the monitor key value pairs for this
   * EdgeThread
   */
  @Override
  public StringBuilder getMonitorString() {
    Util.clear(monitorsb);
    return monitorsb;
  }

  /**
   * Return a status string for this type of thread.
   *
   * @return A status string with identifier and output measures.
   */
  @Override
  public StringBuilder getStatusString() {
    Util.clear(statussb);
    return statussb.append((tg + "                                           ").substring(0, 42)).
            append(" in=").append(Util.leftPad(bytesin, 9)).append(" out=").
            append(Util.leftPad(bytesout, 9)).append(" last=").
            append(Util.leftPad(System.currentTimeMillis() - lastRead, 9)).
            append(" alive/run=").append(isAlive() ? "t" : "f").append("/").append(isRunning() ? "t" : "f").append(" st=").append(state).
            append(" ").append(oorProcessor == null ? "" : oorProcessor.getStatusString()).append(" s=").append(s);
  }

  /**
   * Return the console output. In this case, this there is none.
   *
   * @return The console output, which is always empty.
   */
  @Override
  public StringBuilder getConsoleOutput() {
    return consolesb;
  }

  /**
   * Set the debug flag.
   *
   * @param t Value to set.
   */
  public void setDebug(boolean t) {
    dbg = t;
  }

  /**
   * Return InetAdddress of the remote end (usually an RTS).
   *
   * @return The InetAdddress of the remote end.
   */
  public InetAddress getRemoteInetAddress() {
    return s.getInetAddress();
  }

  /**
   * Return the measure of input volume.
   *
   * @return The number of bytes read from this socket since this objects creation.
   */
  @Override
  public long getBytesIn() {
    return bytesin;
  }

  /**
   * Return the measure of output volume.
   *
   * @return The number of bytes written to this socket since this object was
   * created.
   */
  @Override
  public long getBytesOut() {
    return bytesout;
  }

  /**
   * Return ms of last IO operation on this channel (from
   * System.currentTimeMillis()).
   *
   * @return ms value at last read or last I/O operation.
   */
  public long getLastRead() {
    return lastRead;
  }

  public final void setSocket(Socket ss) {
    if (s != null) {
      if (!s.isClosed()) {
        try {
          s.close();
        } catch (IOException expected) {
        }
      }
      for (int i = 0; i < 10; i++) {
        try {
          sleep(1000);
        } catch (InterruptedException expected) {
        }
        if (s == null) {
          prta(tg + "RIS: set socket was not null.  Closed and waited " + i + "seconds for cleanup");
          break;
        }
      }
    }
    s = ss;
    Util.clear(tg).append(ss.getInetAddress().toString().substring(1)).append("/").append(ss.getPort()).append(superTag);
  }

  public boolean isAvailable() {
    return s == null;
  }

  /**
   * Creates a new instance of RawInputSocket
   *
   * @param ss The socket to use for this object.
   * @param outOfOrder If true, data on this port is expected to be out of order
   * (Please set oorInterver>0).
   * @param oorInterval The expected out of order interval in seconds for the
   * OutOfOrder class.
   * @param par An edgethread "parent" to use for logging.
   * @param cs A channel sender for sending data to holdings.
   * @param rsize A desired minimum input buffer size.
   * @param hyd If true, data is sent to hydra.
   * @param inorder The data is always in order, so Hydra can be sent directly
   * rather than through an OutputInfraStructure.
   * @param sockLog Turn on logging of the socket read.
   * @param rawSend If true, send data to OutputInfrastructure as a raw packet.
   * @param rawMaxRate The maximum data rate to include in any Raw Sends.
   * @param dbgch String if present at beginning of seedname causes single line
   * of output per input packet
   */
  public RawInputSocket(Socket ss, boolean outOfOrder, int oorInterval, EdgeThread par,
          ChannelSender cs, int rsize, boolean hyd, boolean inorder, boolean sockLog,
          boolean rawSend, double rawMaxRate, String dbgch) {
    super("", "");
    superTag = super.getTag().trim();
    setSocket(ss);
    oor = outOfOrder;
    this.setEdgeThreadParent(par);
    parent = par;
    csend = cs;
    readSize = rsize;
    hydra = hyd;
    socketLog = sockLog;
    hydraInorder = inorder;
    this.rawSend = rawSend;
    this.rawMaxRate = rawMaxRate;
    this.setPrintStream(par.getPrintStream());
    this.dbgch = dbgch;
    if (readSize < 64000) {
      readSize = 64000;
    }
    if (oor) {
      oorProcessor = new OutOfOrderRTMS(oorInterval, getTag(), par);
    }
    terminate = false;
    running = true;
    lastRead = System.currentTimeMillis();
    if (hydraInorder) {
      tb = new TraceBuf(new byte[TraceBuf.TRACE_LENGTH]);  // make scratch tracebuf
    }
    prta(Util.clear(tmpsb).append(tg).append("Start RIS : ").append(tg).append("dbg=").append(dbg).
            append(" dbgch=").append(this.dbgch).append(" oor=").append(oor).append(" inorder=").append(hydraInorder));
    state = 1;
    start();
  }

  /*@Override
  public void prt(String s) {if(socketLog) {super.prt(s); return;}if(parent == null) Util.prt(s); else parent.prt(s);}
  @Override
  public final void prta(String s) {if(socketLog) {super.prta(s);return;}if(parent == null) Util.prta(s); else parent.prta(s);}*/
  /**
   * This reads from the socket to an RTS, aligns the packet, and forwards it to
   * the indicated IP/port as a UDP packet. Note that if the socket goes "bad"
   * this routine just waits for it to be made good through a call to reopen.
   * Termination only occurs if the connection is down forever or during
   * shutdown situations.
   */
  @Override
  public void run() {
    byte[] hdr = new byte[40];
    byte[] payload = new byte[4000];
    int[] ts = new int[8000];
    ByteBuffer b = ByteBuffer.wrap(hdr);
    ByteBuffer p = ByteBuffer.wrap(payload);
    int[] ymd = new int[3];
    GregorianCalendar start = new GregorianCalendar();    // Set to beginning of year
    boolean swap = false;
    long lastUpdate = System.currentTimeMillis();
    int nb;
    StringBuilder seedname = new StringBuilder(12);        // 12 character seed name
    double rate;
    short leadin;
    short nsamp = 0;
    byte[] seedArray = new byte[12];
    short year;
    short doy;
    short rateFactor;
    short rateMultiplier;
    byte activity;
    byte ioClock;
    byte quality;
    byte timingQuality;
    Integer pinno;
    int sec;
    int usec;
    int seq;
    while (!terminate) {   // Loop until we get a socket
      while (s == null) {
        try {
          sleep(50);
        } catch (InterruptedException expected) {
        }    // let caller set debug!
      }
      if (s == null) {
        continue;
      }
      try {
        //Util.prta(getTag()+" orig rec buf size="+s.getReceiveBufferSize());
        if (s.getReceiveBufferSize() < readSize) {
          s.setReceiveBufferSize(readSize);
        }
        prta(Util.clear(runsb).append(tg).append(" RIS: Starting rsize=").
                append(s.getReceiveBufferSize()).append(" ").append(readSize).
                append(" oor=").append(oor).append(" hydra=").append(hydra).append(" inord=").append(hydraInorder).
                append(" dbg=").append(dbg).append(" dbgch=").append(dbgch));

      } catch (SocketException e) {
        prta(Util.clear(runsb).append(tg).append("Got an SocketException trying to increase receive buffer size").
                append(e == null ? "null" : e.getMessage()));
      }

      // run util terminate flag is set
      try {
        while (!terminate) {
          if (dbg) {
            prta(tg + "read hdr");
          }

          nb = readin(hdr, 40);
          if (nb <= 0) {
            break;     // EOF found;
          }
          state = 22;
          if (nb == -1) {
            prta(Util.clear(runsb).append(tg).append(" EOF returned from readin hdr term=").append(terminate));
            continue;
          }       // This return means reader had error and socket is closed
          b.position(0);
          leadin = b.getShort();
          if (leadin != (short) 0xa1b2) {
            if (leadin == (short) 0xb2a1) {
              prta(Util.clear(runsb).append(tg).append("Lead ins indicate byte swap.  Set up native=").append(ByteOrder.nativeOrder()).
                      append(" b.order=").append(b.order()).append(" big=").append(ByteOrder.BIG_ENDIAN).
                      append(" little=").append(ByteOrder.LITTLE_ENDIAN));
              b.order(ByteOrder.LITTLE_ENDIAN);
              p.order(ByteOrder.LITTLE_ENDIAN);
              swap = true;
              b.position(0);
              leadin = b.getShort();
              prta(Util.clear(runsb).append(tg).append("Leadins after swap=").append(Util.toHex(leadin)));
            } else {
              prta(Util.clear(runsb).append(tg).append(" RIS: Bad leadin ").
                      append(Util.toHex(leadin)).append(" ").append(Util.toHex(hdr[2])).
                      append(" ").append(Util.toHex(hdr[3])).append(" lastnsamp=").append(nsamp));

              // If we are in debug mode, study the file a bit more to figure out why it is wrong
              if (rawfile != null) {
                nb = readin(hdr, 40);
                if (nb <= 0) {
                  break;      // EOF
                }
                b.position(0);
                leadin = b.getShort();
                if (leadin == (short) 0xa1b2) {
                  prt(Util.clear(runsb).append(tg).append(" RIS: Skip one header worked"));
                } else {
                  nb = readin(hdr, 40);
                  if (nb <= 0) {
                    break;   // EOF
                  }
                  b.position(0);
                  leadin = b.getShort();
                  if (leadin == (short) 0xa1b2) {
                    prt(Util.clear(runsb).append(tg).append(" RIS: Skip one header worked2"));
                  } else {
                    nb = readin(payload, 2048);
                    if (nb <= 0) {
                      break; //EOF
                    }
                    for (int i = 0; i < 2047; i++) {
                      if (payload[i] == -95 && payload[i + 1] == -78) {
                        prt(Util.clear(runsb).append("found at offset=").append(i));
                      }
                    }
                  }
                }
                prt(Util.clear(runsb).append(tg).append(" RIS: Lead in not right.  Close & terminate ").
                        append(Integer.toHexString(leadin)).append(" ").append(nb));
                try {
                  s.close();
                } catch (IOException expected) {
                }
                break;
              }
              continue;
            }
          }

          nsamp = b.getShort();
          if (nsamp > ts.length) {
            ts = new int[nsamp];
          }
          b.get(seedArray, 0, 12);
          Util.clear(seedname);
          state = 2;
          for (int i = 0; i < 12; i++) {
            seedname.append((seedArray[i] == 0 ? ' ' : (char) seedArray[i]));
          }
          if (nsamp > 0) {
            try {
              MasterBlock.checkSeedName(seedname);
            } catch (IllegalSeednameException e) {
              prta("The seedname " + seedname + "| nsamp=" + nsamp + " is illegal e=" + e);
              continue;
            }
          }

          year = b.getShort();
          doy = b.getShort();
          rateFactor = b.getShort();
          rateMultiplier = b.getShort();
          activity = b.get();
          ioClock = b.get();
          quality = b.get();
          timingQuality = b.get();
          sec = b.getInt();
          usec = b.getInt();
          seq = b.getInt();
          if (dbg) {
            prta(Util.clear(runsb).append(tg).append("RIS: ").append(Util.toAllPrintable(seedname)).
                    append(" ns=").append(nsamp).append(" yr/doy=").append(year).append("/").append(doy).
                    append(" sec=").append(sec).append(" usec=").append(usec).
                    append(" ").append(RawToMiniSeed.timeFromUSec(sec * 1000000L + usec)).
                    append(" rt=").append(rateFactor).append(" mult=").append(rateMultiplier).append(" sq=").append(seq).
                    append(" act=").append(activity).append(" io=").append(ioClock).
                    append(" dq=").append(quality).append(" tq=").append(timingQuality));
          }

          // special case, get an id tg from the other end
          if (nsamp < -1) {
            state = 3;
            RawToMiniSeed.forceoutAll(seedname);
            prta(Util.clear(runsb).append(tg).append(" RIS: nsamp < -2 is ").append(nsamp).
                    append(" force out ").append(seedname));
            continue;
          } else if (nsamp == -1 || nsamp == 0) {
            state = 4;
            if (dbg) {
              prta(Util.clear(runsb).append(tg).append("RIS: org tag=").append(tg));
            }

            if (tg.indexOf("]") > 0) {
              tg.delete(tg.indexOf("]") + 1, tg.length());  //tg = tg.substring(0,tg.indexOf("]")+1);
            } else if (tg.indexOf(":") > 0) {
              tg.delete(tg.indexOf(":"), tg.length()); //tg = tg.substring(0,tg.indexOf(":"));  
            } else {
              tg.append(":").append(seedname); //tg += ":"+seedname+"-";
            }
            if (dbg) {
              prta(Util.clear(runsb).append("RIS: add to tag nb=").append(nb).append(" nm=").append(seedname).
                      append(" tag=").append(tg).append(" oor=").append(oor).append(" socklog=").append(socketLog));
            }
            if (socketLog) {
              this.setNewLogName(seedname.substring(0, nb));
            }

            // Debugging: write output for header only!
            if (rawout != null && nrawout < 1000000000) {
              try {
                synchronized (rawmutex) {
                  rawout.write(hdr, 0, 40);
                }
              } catch (IOException e) {
                prt(Util.clear(runsb).append(tg).append("IOException writing raw out=").append(e.getMessage()));
              }
              nrawout += nb;
            }
            continue;
          } else if (nsamp > 0) {
            state = 5;
            if (nsamp * 4 > payload.length) {
              prta(Util.clear(runsb).append(tg).append(" RIS: read too long ns=").append(nsamp).
                      append(" len=").append(payload.length).append(" adjust buf len=").append(nsamp * 4 + 200));
              payload = new byte[nsamp * 4 + 200];
              p = ByteBuffer.wrap(payload);
              if (swap) {
                p.order(ByteOrder.LITTLE_ENDIAN);
              }
            }
            if (dbg) {
              prta("read payload");
            }
            nb = readin(payload, nsamp * 4);
            if (nb <= 0) {
              break;      // EOF
            }
            if (dbg) {
              prta(Util.clear(runsb).append(tg).append("Read payload returned nb=").append(nb).
                      append(" expected ").append(nsamp * 4));
            }
            if (nb == -1) {
              prta(Util.clear(runsb).append(tg).append(" EOF returned from readin data term=").append(terminate));
              break;
            }       // This return means reader had error and socket is closed
            // debugging write output
            if (rawout != null && nrawout < 1000000000) {
              try {
                synchronized (rawmutex) {
                  rawout.write(hdr, 0, 40);
                  rawout.write(payload, 0, nsamp * 4);
                }
              } catch (IOException e) {
                prt(Util.clear(runsb).append(tg).append("IOException writing raw out=").append(e.getMessage()));
              }
              nrawout += nb;
            }
            bytesin += nb;
            p.position(0);
            int nbad = 0;
            int lastbad = 0;
            for (int i = 0; i < nsamp; i++) {
              ts[i] = p.getInt();
              if (Math.abs(ts[i]) > 67108864) {   // big enough most geomag data will fit.
                lastbad = ts[i];
                nbad++;
              }
            }
            if (nbad > 0) {
              prta(Util.clear(runsb).append(tg).append(nbad).append(" OOR values in ").append(nsamp).append(" samps ").
                      append(seedname).append(" val=").append(lastbad).append(" ").append(" n>24 bit=").append(nbad).append(" ").
                      append(year).append(",").append(doy).append(" ").append((RawToMiniSeed.timeFromUSec(sec * 1000000L + usec))));
            }

            try {
              MasterBlock.checkSeedName(seedname);
            } catch (IllegalSeednameException e) {
              prt(Util.clear(runsb).append(tg).append(" RIS : ***** IllegalSeedName received:").append(Util.toAllPrintable(seedname)));
              prt(Util.clear(runsb).append(tg).append(" RIS : ns=").append(nsamp).
                      append(" nb=").append(bytesin).append(" hdr =").append(Util.toAllPrintable(new String(hdr))));
              break;      // close socket
            }
          }
          if (rateFactor > 0) {
            rate = rateFactor;
          } else {
            rate = -1. / rateFactor;
          }
          if (rateMultiplier < 0) {
            rate = rate / (double) (-rateMultiplier);
          } else {
            rate = rate * (double) rateMultiplier;
          }
          countIn++;

          state = 6;
          if (nsamp < -1 || nsamp > 20000 || year < 1970 || year > 2200 || doy < 1 || doy > 366
                  || rate < 0. || rate > 1000.) {
            prta(Util.clear(runsb).append(tg).append(" RIS: ***** packet params out of range yr=").append(year).
                    append(" doy=").append(doy).append(" ns=").append(nsamp).append(" rt=").append(rate));
            break;
          }
          start.set(year, 0, 1);    // Time first of January this year (first day of the year).
          start.setTimeInMillis((start.getTimeInMillis() / 86400000L * 86400000L) + (doy - 1) * 86400000L + sec * 1000L + (usec + 500) / 1000);
          if (dbg || seedname.indexOf(dbgch) == 0 || countIn % 100 == 0)  {//IMYKW3 HHZ") >=0) 
            prta(Util.clear(runsb).append(tg).append("RIS: rcv=").append(countIn).append(" ").
                    append(seedname).append(" ns=").append(nsamp).
                    append(" rt=").append(Util.df23(rate)).append(" date=").append(year).append(" ").append(doy).
                    append(" ").append(RawToMiniSeed.timeFromUSec(sec * 1000000L + usec)).
                    append(" sec/usec=").append(sec).append("/").append(usec).append(" time=").append(Util.ascdatetime2(start)).
                    append(" sq=").append(seq).
                    append(" act=").append(Integer.toHexString((int) activity)).
                    append(" ioclk=").append(Integer.toHexString((int) ioClock)).
                    append(" qual=").append(Integer.toHexString((int) quality)).
                    append(" Tqual=").append(Integer.toHexString((int) timingQuality))
                    .append(" oor=").append(oor ? "t" : "f").append(" ").append("raw=").append(rawSend ? "t" : "f").
                    append(" maxrt=").append(rawMaxRate).append(" ").
                    append(nsamp >= 1 ? ts[0] : "").append(" ").append(nsamp >= 2 ? ts[1] : "").append(" ").
                    append(nsamp >= 3 ? ts[2] : "").append(nsamp >= 4 ? ".." : "").append(nsamp >= 5 ? ts[nsamp - 2] : "??").append(" ").append(nsamp >= 4 ? ts[nsamp - 1] : "??"));
          }
          if (rawSend) {// Normally lower rate data to QueryMom so it can be in RAM spans earlier than MiniSeed.
            if (rate <= rawMaxRate) {
              Util.ymd_from_doy((int) year, (int) doy, ymd);
              Channel c = EdgeChannelServer.getChannel(seedname);
              if (dbg) {
                prta(Util.clear(runsb).append(tg).append(" RIS: read channel=").
                        append(seedname).append(" rtn=").
                        append((c == null ? "Null" : c.getSendtoMask() + " rt=" + c.getRate())));
              }

              if (c == null) {
                prta(Util.clear(runsb).append(tg).append(" RIS: need to create channel ").
                        append(seedname).append(" rt=").append(rate));
                EdgeChannelServer.createNewChannel(seedname, rate, this);
                c = EdgeChannelServer.getChannel(seedname);
              }
              OutputInfrastructure.sendRaw(c, seedname, start.getTimeInMillis(), rate, nsamp, ts);
            }
          }

          if (oor) {
            oorProcessor.addBuffer(ts, (int) nsamp, seedname, (int) year, (int) doy, sec,
                    usec, rate, (int) activity, (int) ioClock, (int) quality, (int) timingQuality);
          } else {
            // Send data to be compressed
            RawToMiniSeed.addTimeseries(ts, (int) nsamp, seedname, (int) year, (int) doy, sec,
                    usec, rate, (int) activity, (int) ioClock, (int) quality, (int) timingQuality, parent);
            if (hydra) {
              if (hydraInorder) {
                pinno = pinnos.get(Util.getHashFromSeedname(seedname));
                if (pinno == null) {
                  Channel chn = EdgeChannelServer.getChannel(seedname);
                  if (chn != null) {
                    pinno = chn.getID();
                  } else {
                    pinno = 0;
                  }
                  pinnos.put(Util.getHashFromSeedname(seedname), pinno);
                }
                tb.setData(seedname, start.getTimeInMillis(), nsamp, rate, ts, 0, 0, 0, 0, pinno);
                Hydra.sendNoChannelInfrastructure(tb);    // send data straight to Hydra
              } else {
                Hydra.send(seedname, year, doy, sec, usec, nsamp, ts, rate);
              }
            }
          }
          try {
            if (csend != null) {
              csend.send(SeedUtil.toJulian((int) year, (int) doy), sec * 1000 + usec / 1000, seedname,
                      (int) nsamp, rate, (int) nsamp);
            }
          } catch (IOException e) {
            prta(Util.clear(runsb).append(tg).append("RIS: got IOException sending channel ").append(e.getMessage()));
          }
          lastRead = System.currentTimeMillis();

        }         // End of while(!terminate) on reading packets
      } catch (RuntimeException e) {
        prta(Util.clear(runsb).append(tg).append("RIS: RuntimeException in ").
                append(this.getClass().getName()).append(" e=").append(e.getMessage()));
        if (getPrintStream() != null) {
          e.printStackTrace(getPrintStream());
        } else {
          e.printStackTrace();
        }
        if (e.getMessage() != null) {
          if (e.getMessage().contains("OutOfMemory")) {
            SendEvent.doOutOfMemory(tg.toString(), this);
            throw e;
          }
        }
      }
      // We have been asked close up the socket and cause children UTLs to close.  
      prta(Util.clear(runsb).append(tg).append("RIS: EOF or other need to close"));
      if (!s.isClosed()) {
        try {
          s.close();
        } catch (IOException expected) {
        }
      }
      s = null;
    }   // while(!terminate) wait for a socket

    if (dbg) {
      prta(Util.clear(runsb).append(getTag()).append(" RIS: ** leaving run() loop.  Terminate RawInputSocket. nbin=").
              append(bytesin));
    }

    if (oorProcessor != null) {
      oorProcessor.terminate();
    }
    s = null;
    csend = null;
    running = false;
    terminate = false;
    tb = null;
    oorProcessor = null;
    prta(Util.clear(runsb).append(tg).append(" RIS: has Terminated nbin=").append(bytesin));
    parent = null;
  }

  /**
   * Read in the given number of bytes from the socket. Do not return until they
   * are in. If error causes us to close the socket or it is not open on the
   * call, we wait until the socket is reopened and then read the data.
   *
   * @param buf Buffer to put data in.
   * @param len Length of data to read. This is the amount that will be read or
   * die trying.
   * @return If same len, success. If -1, error reading from socket (generally
   * it's closed now).
   */
  private int readin(byte[] buf, int len) {
    if (rawfile != null) {
      try {
        int l = rawfile.read(buf, 0, len);
        if (l == -1) {
          prt("EOF on input debug file. exit" + tg);
          System.exit(0);
        }
        return len;
      } catch (IOException e) {
        prt(Util.clear(runsb).append("IOException reading rawfile=").append(e.getMessage()));
      }
    }
    state = 10;
    while (true) {       // Wait until read is satisfied.
      int l = 0;
      int nb;
      if (dbg) {
        try {
          prta(Util.clear(runsb).append(tg).append("readin start avail=").append(s.getInputStream().available()));
        } catch (IOException e) {
          prta(Util.clear(runsb).append(tg).append("In dbg get avail got IOException e=").append(e.getMessage()));
        }
      }
      state = 11;
      while (l < len) {
        try {
          state = 12;
          state = 13;
          nb = s.getInputStream().read(buf, l, len - l);
          state = 14;

          if (dbg) {
            prta(Util.clear(runsb).append(tg).append("readin read rtn=").append(nb).append(" len=").append(len).
                    append(" l=").append(l).append(" avail=").append(s.getInputStream().available()));
          }
        } catch (IOException e) {
          state = 15;
          if (e.getMessage().equalsIgnoreCase("Connection reset")) {
            prta(Util.clear(runsb).append(tg).append(" RIS: socket has reset.  close and reestablish"));
          } else if (e.getMessage().contains("Socket is closed")
                  || e.getMessage().contains("Socket closed")) {
            prta(Util.clear(runsb).append(tg).append(" RIS: Socket is closed"));
          } else {
            Util.IOErrorPrint(e, tg + " RIS: error reading from socket", getPrintStream());
          }
          //break;
          return -1;
        }
        if (nb <= 0) {        // End of file
          state = 16;
          if (dbg) {
            prta(Util.clear(runsb).append(tg).append(" RIS: Socket must be closed for EOF"));
          }
          return -1;
          //break;
        }
        l += nb;
      }

      if (l >= len) {
        return len;
      }
      if (dbg) {
        prta(Util.clear(runsb).append(tg).append(" RIS: readin() not satisfied close and wait to try again. "));
      }
      if (!s.isClosed()) {
        try {
          s.close();
        } catch (IOException e) {
        }
      }
    }
  }

  public static void main(String[] args) {
    String file = "RIS.out";
    EdgeProperties.init();
    EdgeMom.prt("testing 123");
    Util.debug(false);

    Util.setModeGMT();
    EdgeBlockQueue ebq = new EdgeBlockQueue(1000);
    Util.prt("testing 123");
    Util.setNoconsole(true);
    Util.prt("testin 456");
    Socket s = null;
    try {
      RawInputServer serv = new RawInputServer("-p 7207 -dbg -oor -oorint 10000 -rawread " + file, "TAG");
      Util.prt("testing 8910");
      s = new Socket(Util.getLocalHostIP(), 7207);
    } catch (UnknownHostException e) {
      Util.prt("UknownHost=" + e.getMessage());
    } catch (IOException e) {
      Util.prt("IOExp=" + e.getMessage());
    }
    //RawInputSocket sock = new RawInputSocket(s, true, 60000 );
    //sock.setReadraw(file);

  }
}
