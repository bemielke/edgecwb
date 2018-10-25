/*
 *  This software is in the public domain because it contains materials 
 *  that originally came from the United States Geological Survey, 
 *  an agency of the United States Department of Interior. For more 
 *  information, see the official USGS copyright policy at 
 *  http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.edgemom;

import gov.usgs.alarm.SendEvent;
import gov.usgs.anss.edge.ChannelSender;
import gov.usgs.anss.edge.EdgeBlockQueue;
import gov.usgs.anss.edge.EdgeProperties;
import gov.usgs.anss.edge.IllegalSeednameException;
import gov.usgs.anss.edge.IndexBlock;
import gov.usgs.anss.edge.IndexFile;
import gov.usgs.anss.edge.RawDisk;
import gov.usgs.anss.edgethread.EdgeThread;
import gov.usgs.anss.seed.MiniSeed;
import gov.usgs.anss.util.Util;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

/**
 * This class receives data from a socket. It is normally created by the
 * DataLinkServer as each connection comes in. It inherits its arguments from
 * the DataLinkServer configuration (no hydra, no udp chan, etc.). As it reads
 * the MiniSeed, its buffer size can be adapted if B1000 indicates blocks of
 * bigger sizes. It also breaks 4k packets into 512 sizes unless configured
 * otherwise.
 *
 *
 * @author U.S. Geological Survey <ketchum at usgs.gov>
 */
public final class DataLinkSocket extends EdgeThread {

  private int port;
  private String host;
  private int countMsgs;
  private int heartBeat;
  private final int msgLength;
  private Socket d;           // Socket to the server
  private long lastStatus;     // Time last status was received
  private InputStream in;
  private OutputStream outsock;
  private ChannelSender csend;
  private boolean allow4k;
  private long bytesIn;
  private long bytesOut;
  private boolean hydra;
  private String seedname;        // The NNSSSSS for a station coming through this socket.
  private boolean dbg;
  private EdgeThread parent;
  private boolean scn;
  private int waitms;
  private boolean q330check;
  private long terminateTime;
  private int state;
  private String ID;            // This ID is the first command sent. The sender is identified
  private boolean rawSend;
  private double maxRawRate;
  private final StringBuilder tmpsb = new StringBuilder(100);

  public void setDebug(boolean t) {
    dbg = t;
  }

  @Override
  public long getBytesIn() {
    return bytesIn;
  }

  @Override
  public long getBytesOut() {
    return bytesOut;
  }

  public String getSeedname() {
    return seedname;
  }

  /**
   * Create a new instance of DataLinkSocket (usually for DataLinkServer).
   *
   * @param s The socket to read the data from.
   * @param par The parent object. Used for logging to the same file.
   * @param tg The EdgeThread tag to use.
   * @param c The channel sender to use (null if sending is disabled).
   * @param hydraFlag If True, send all data to hydra on receipt.
   * @param allow4096 If true, 4k packets to go database rather than the default
   * of being split up into 512 packets.
   * @param wait Wait time in milliseconds between each block received.
   * @param q330 If true, do Q330 checking.
   * @param scnmode If true, strip off locations.
   * @param rawSend if true send raw receive packets with rates less than
   * maxRawRate to OutputInfrastructure
   * @param maxRawRate Max rate to include in raw send, rates above this will
   * only be sent as MiniSEED
   */
  public DataLinkSocket(Socket s, DataLinkServer par, String tg, ChannelSender c,
          boolean hydraFlag, boolean allow4096, int wait, boolean q330, boolean scnmode, boolean rawSend, double maxRawRate) {
    super("", tg);
    q330check = q330;
    tag += "DLSkt:[" + s.getInetAddress().getHostAddress() + "/" + s.getLocalPort() + "." + s.getPort() + "]";
    parent = par;
    this.setPrintStream(parent.getPrintStream());   // Use the same log as the server
    d = s;
    allow4k = allow4096;
    csend = c;
    waitms = wait;
    scn = scnmode;
    this.rawSend = rawSend;
    this.maxRawRate = maxRawRate;
    hydra = hydraFlag;
    host = s.getInetAddress().getHostAddress();
    port = s.getPort();
    countMsgs = 0;
    msgLength = 512;
    heartBeat = 0;
    try {
      in = s.getInputStream();
      outsock = s.getOutputStream();
    } catch (IOException e) {
      Util.IOErrorPrint(e, "Error getting i/o streams from socket " + host + "/" + port, parent.getPrintStream());
      return;
    }

    parent.prt(Util.clear(tmpsb).append(tag).append("DataLinkSocket: new line parsed to host=").append(host).
            append(" port=").append(port).append(" len=").append(msgLength).append(" dbg=").append(dbg).
            append(" allow4k=").append(allow4k).append(" hydra=").append(hydra).
            append(" mswait=").append(waitms).append(" q330=").append(q330check));
    IndexFile.init();
    start();
  }

  @Override
  public void terminate() {
    terminate = true;
    terminateTime = System.currentTimeMillis();
    if (d != null) {
      parent.prta(Util.clear(tmpsb).append(tag).append("DLSkt: Terminate started. Close input unit."));
      try {
        d.close();
      } catch (IOException expected) {
      }
      interrupt();
    } else {
      parent.prt(Util.clear(tmpsb).append(tag).append("DLSkt: Terminate started. interrupt()."));
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
    StringBuilder sb = new StringBuilder(255);
    StringBuilder stringID = new StringBuilder(20);
    String response = "ID DataLink 2012.126 :: DLPROTO:1.0 PACKETSIZE:512 WRITE";
    MiniSeed ms = null;
    int nillegal = 0;
    int hdrlen;
    running = true;               // Mark we are running
    int lastCount = countMsgs;
    parent.prta(Util.clear(tmpsb).append(tag).append(" Socket is opened.  Start reads. ").
            append(host).append("/").append(port).append(" ").append(getName()));
    // Read data from the socket and update/create the list of records 
    int len = msgLength;
    int l = 0;
    int nchar = 0;
    lastStatus = System.currentTimeMillis();
    long openTime = lastStatus;

    int nsamp = 0;
    int ns;
    int nsleft = 0;
    //int [] data = null;      // Need to decompress the data
    long now;
    state = 1;
    while (true) {
      try {
        if (terminate && in.available() <= 0) {
          break;
        }
        if (terminate) {
          if (System.currentTimeMillis() - terminateTime > 10000) {
            break;
          }
        }
        l = 0;                    // Offset into buffer for next read
        nchar = 0;                // Number of characters returned
        // Packets are "DL<hdrlen><hdrlen bytes of header><data payload>
        state = 2;
        Util.readFully(in, buf, 0, 3);  // Get the 'D','L' and hdr length
        if (buf[0] != 'D' || buf[1] != 'L') {
          prta(Util.clear(tmpsb).append(" **** Bad back header not DL [0]=").append(buf[0]).
                  append(" [1]=").append(buf[1]).append(" [2]=").append(buf[2]).append(" ").
                  append((char) buf[0]).append((char) buf[1]).append((char) buf[2]));
          try {
            sleep(1000);
          } catch (InterruptedException expected) {
          }
          while (in.available() > 0) {
            int j = in.read(buf);
            for (int i = 0; i < j; i++) {
              prt(Util.clear(tmpsb).append("[").append(i).append("]=").append(buf[i]).append(" ").append((char) buf[i]));
            }
          }
          if (d.isClosed()) {
            prta("**** socket has closed");
          }
          state = 20;
          break;
        }
        hdrlen = ((int) buf[2]) & 0xff;
        state = 3;
        nchar = Util.readFully(in, buf, 3, hdrlen);  // Read in the header
        state = 4;
        if (nchar <= 0) {
          break;      // EOF - close up - go to outer infinite loop
        }        // Put command in StringBuilder
        Util.clear(sb);
        for (int i = 3; i < 3 + hdrlen; i++) {
          sb.append((char) buf[i]);
        }
        if (dbg) {
          prta(Util.clear(tmpsb).append("Got message len=").append(hdrlen).append(" sb=").append(sb));
        }
        state = 5;
        if (sb.indexOf("ID ") == 0) {
          state = 6;
          ID = sb.substring(3);
          if (dbg) {
            prta(Util.clear(tmpsb).append("Got ID connect ID=").append(ID));
          }
          buf[2] = (byte) response.length();
          for (int i = 0; i < response.length(); i++) {
            buf[i + 3] = (byte) response.charAt(i);
          }
          //System.arraycopy(response.getBytes(),0, buf, 3, response.length());
          outsock.write(buf, 0, response.length() + 3);
        } else if (sb.indexOf("WRITE") == 0) {
          state = 6;
          Util.clear(stringID);
          long hpdatastart = 0;
          long hpdataend = 0;
          int size = 0;
          int n = 0;
          char flags = 9;
          for (int i = 9; i < sb.length() + 3; i++) {
            if (buf[i] == ' ') {
              n++;
            } else if (n == 0) {
              stringID.append((char) buf[i]);
            } else if (n == 1) {
              hpdatastart = hpdatastart * 10 + (buf[i] - '0');
            } else if (n == 2) {
              hpdataend = hpdataend * 10 + (buf[i] - '0');
            } else if (n == 3) {
              flags = (char) buf[i];
            } else if (n == 4) {
              size = size * 10 + (buf[i] - '0');
            }
          }
          if (dbg) {
            prt(Util.clear(tmpsb).append("stringid=").append(stringID).append(" st=").append(hpdatastart).
                    append(" end=").append(hpdataend).append(" size=").append(size));
          }
          state = 61;
          nchar = Util.readFully(in, buf, 0, size);
          state = 62;
          if (ms == null) {
            ms = new MiniSeed(buf);
          } else {
            ms.load(buf, 0, size);
          }
          bytesIn += ms.getBlockSize();
          if (flags == 'A') {
            state = 63;
            buf[2] = 6;
            buf[3] = 'O';
            buf[4] = 'K';
            buf[5] = ' ';
            buf[6] = '1';
            buf[7] = ' ';
            buf[8] = '0';
            outsock.write(buf, 0, 9);
          }
          // Break the data into MiniSeed and send it out; reuse our miniseed holder

          if (waitms > 0) {
            try {
              sleep(waitms);
            } catch (InterruptedException expected) {
            }
          }
          state = 64;
          if (!ms.isHeartBeat()) {
            state = 65;
            nillegal = 0;                 // Must be a legal seed name
            if (dbg || countMsgs % 1000 == 0) {
              //|| (q330check && ms.getSeedNameString().substring(0,6).equals("USBRAL") && ms.getActivityFlags() > 0.0001 && ms.getNsamp() >0 ))
              parent.prta(Util.clear(tmpsb).append(tag).append(" ").append(ms.toStringBuilder(null)).append(" #=").append(countMsgs));
            }
            try {
              if (q330check && ms.getRate() > 0.00001 && ms.getNsamp() > 0) {
                state = 66;
                ns = MiniSeed.getNsampFromBuf(buf, ms.getEncoding());
                if (ns < ms.getNsamp()) {
                  parent.prta(Util.clear(tmpsb).append(tag).append(" *** Q330 ns fail=").append(ns).
                          append(" ms=").append(ms.toStringBuilder(null)));
                  SendEvent.debugEvent("Q330BadBlk", tag + "Bad ns " + ns + "!=" + ms.getNsamp() + " " + ms.toString().substring(0, 50), this);
                  continue;       // Skip putting this data into databases
                }

              }
              if (scn) {
                ms.setLocationCode("  ");
              }

              IndexBlock.writeMiniSeedCheckMidnight(ms, false, allow4k, tag, this);  // Do not use Chan Infra for Miniseed data
              if (hydra) {
                Hydra.sendNoChannelInfrastructure(ms);            // Send with no channel Infra
              }              // Send this channel information to the channel server for latency etc.
              if (csend != null) {
                csend.send(ms.getJulian(),
                        (int) (ms.getTimeInMillis() % 86400000), // Milliseconds
                        ms.getSeedNameSB(), ms.getNsamp(), ms.getRate(), ms.getBlockSize());  // Key,nsamp,rate and nbytes
              }
            } catch (IllegalSeednameException e) {
              prt(tag + "Illegal seedname should be impossible here. " + ms.getSeedNameString());
            }
          } else {
            heartBeat++;
          }
          state = 67;
        } else if (sb.indexOf("POSITION") == 0) {
          prta("*** Unimplemented command " + sb.toString() + " hdrlen=" + hdrlen);
        } else if (sb.indexOf("MATCH") == 0) {
          prta("*** Unimplemented command " + sb.toString() + " hdrlen=" + hdrlen);

        } else if (sb.indexOf("READ") == 0) {
          prta("*** Unimplemented command " + sb.toString() + " hdrlen=" + hdrlen);

        } else if (sb.indexOf("STREAM") == 0) {
          prta("*** Unimplemented command " + sb.toString() + " hdrlen=" + hdrlen);

        } else if (sb.indexOf("ENDSTREAM") == 0) {
          prta("*** Unimplemented command " + sb.toString() + " hdrlen=" + hdrlen);

        } else if (sb.indexOf("INFO") == 0) {
          prta("*** Unimplemented command " + sb.toString() + " hdrlen=" + hdrlen);

        } else {
          prta("*** Unimplemented command " + sb.toString() + " hdrlen=" + hdrlen);
        }

        countMsgs++;
        now = System.currentTimeMillis();

        // Is it time for status yet
        if ((now - lastStatus) > 600000) {
          prta(Util.clear(tmpsb).append(tag).append("DLS: # Rcv=").append(countMsgs - lastCount).
                  append(" #tot=").append(countMsgs).append(" hbt=").append(heartBeat));
          lastCount = countMsgs;
          lastStatus = now;
        }
        state = 8;
      } catch (IOException e) {
        if (e.getMessage().equalsIgnoreCase("Socket closed") && terminate) {
          parent.prta(Util.clear(tmpsb).append(tag).append(" Doing termination via Socket close."));
        } else {
          Util.IOErrorPrint(e, "DLS: receive through IO exception", parent.getPrintStream());
        }
        break;      // Drop out of read loop to connect loop
      } catch (IllegalSeednameException e) {
        nillegal++;
        if (ms != null) {
          parent.prta(tag + " IllegalSeedName =" + nillegal + " "
                  + Util.toAllPrintable(ms.getSeedNameString()) + " " + e.getMessage());
        } else {
          parent.prta(tag + " IllegalSeedName =" + nillegal + " ms is null. "
                  + e.getMessage());
        }
        for (int i = 0; i < 48; i++) {
          parent.prt(i + " = " + buf[i] + " " + (char) buf[i]);
        }
        if (nillegal > 3) {
          terminate = true;    // If 3 in a row, then close connection
        }
      } catch (RuntimeException e) {
        parent.prt(tag + " RuntimeException : " + e.getMessage() + " nc=" + nchar + " len=" + len + " l=" + l + " ns=" + nsamp
                + " nleft=" + nsleft);
        for (int i = 0; i < 48; i++) {
          prt(i + " = " + buf[i] + " " + (char) buf[i]);
        }
        e.printStackTrace(this.getPrintStream());
        terminate = true;
        break;
      }

    }     // while(true) Get data
    try {
      state = 9;
      if (nchar <= 0) {
        parent.prta(tag + " exited due to EOF or error after " + countMsgs + " pkts lastMS=" + (ms == null ? "null" : ms.toString().substring(0, 62)));
      } else if (terminate) {
        parent.prt(tag + " terminate found.");
      } else {
        parent.prta(tag + " exited while(true) for read");
      }
      if (d != null) {
        d.close();
      }
    } catch (IOException e) {
      Util.IOErrorPrint(e, tag + " socket- reopen", parent.getPrintStream());
    } catch (RuntimeException e) {
      if (parent != null) {
        parent.prta("DLS: Runtime during exit e=" + e);
      }
      e.printStackTrace(parent.getPrintStream());
    }
    prta(tag + " is terminated msgs = " + countMsgs + " " + ((int) (countMsgs / ((System.currentTimeMillis() - openTime) / 1000.))) + " msg/s");
    running = false;
    terminate = false;
  }

  /**
   * Return the monitor string for Icinga.
   *
   * @return A String representing the monitor key value pairs for this
   * EdgeThread.
   */
  long lastMonCountMsgs;
  long lastMonBytesIn;

  @Override
  public StringBuilder getMonitorString() {
    if (monitorsb.length() > 0) {
      monitorsb.delete(0, monitorsb.length());
    }
    long np = countMsgs - lastMonCountMsgs;
    long nb = bytesIn - lastMonBytesIn;
    lastMonCountMsgs = countMsgs;
    lastMonBytesIn = bytesIn;
    return monitorsb.append(tag).append("-NPacket=").append(np).append("\n").append(tag).append("-BytesIn=").append(nb).append("\n");
  }

  @Override
  public StringBuilder getStatusString() {
    if (statussb.length() > 0) {
      statussb.delete(0, statussb.length());
    }
    return statussb.append(tag).append(" #rcv=").append(countMsgs).append(" hb=").append(heartBeat).append(" state=").append(state);
  }

  @Override
  public StringBuilder getConsoleOutput() {
    return consolesb;
  } //  We use parent.prt directly

  /**
   * Test routine.
   *
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
    DataLinkServer mss = new DataLinkServer("-p 2015", "msrv");
    EdgeBlockQueue ebq = new EdgeBlockQueue(10000);
    try {
      Socket s = new Socket("localhost", 2015);
      OutputStream out = s.getOutputStream();
      in = new RawDisk("/Users/ketchum/IUANMO_BHZ00.ms", "r");
      while ((l = in.readBlock(b, iblk, len)) > 0) {
        ms = new MiniSeed(b);
        //Util.parent.prt("ms="+ms.toString());
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
