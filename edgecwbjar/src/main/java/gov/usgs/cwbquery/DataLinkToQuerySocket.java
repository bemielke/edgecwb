/*
 *  This software is in the public domain because it contains materials 
 *  that originally came from the United States Geological Survey, 
 *  an agency of the United States Department of Interior. For more 
 *  information, see the official USGS copyright policy at 
 *  http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.cwbquery;

import gov.usgs.alarm.SendEvent;
import gov.usgs.anss.edge.EdgeBlockQueue;
import gov.usgs.anss.edge.EdgeProperties;
import gov.usgs.anss.edge.IllegalSeednameException;
import gov.usgs.anss.edge.IndexFile;
import gov.usgs.anss.edge.RawDisk;
import gov.usgs.anss.edgemom.DataLinkServer;
import gov.usgs.anss.edgemom.LISSClient;
import gov.usgs.anss.edgethread.EdgeThread;
import gov.usgs.anss.seed.MiniSeed;
import gov.usgs.anss.util.Util;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;

/**
 * This class received data from a socket and process any raw or MiniSEED data
 * received into the QueryMom memory caches. This thread is normally created by
 * the DataLinkServerToQueryServer as each connection comes in. It reads both
 * RAW and MiniSeed data using the DataLink protocol. As it reads in packets it
 * can present the RAW packets to the QuerySpanCollection to make the
 * in-the-clear memory cache and for MiniSeed packets it presents the raw
 * MiniSeed buffer to both QuerySpanCollection and MiniSeedColletion which makes
 * in memory caches of the miniSeed for a TrinetServer.
 *
 * <p>
 * There are no command line arguments. This thread needs to know its parent,
 * the socket to use and the throttle wait time.
 * <pre>
 * The basic idea is read the first 3 bytes which should be "D","L" and the binary length of the
 * rest of the header.  Then read the rest of the header, and decode it.  If it contains
 * "/RAW" use the raw interpretation of the payload, if not use the MiniSEED parsing
 *
 * For Raw read in the next 4 bytes (27, 3, and (short) nsamps.  The rest of the raw packet
 * is 28+nsamp*4 bytes in length.  The parse the NNSSSSSCCCLL, start time, rate, and data[nsamps]
 *
 * For MiniSeed parse the header to get the NNSSSSSCCCLL, startms, endms and Ack flag as space delimited text
 * from the header.  The miniseed data packet is the next 512 bytes after the header.
 *
 *   For Raw Packets
 * Offset    type       Description
 * 0          "DL"      Lead in characters in ascii
 * 2         byte       Size of the header in bytes from "WRITE to /RAW" inclusive(9 for RAW)
 * 3          "WRITE "   5 bytes In ascii
 * 8          "/RAW"    if a Raw data packet
 * 12         27        The original raw lead in
 * 13          3        The original raw lead in 2
 * 14        short      Number of data samples (big endian order), if negative user ack protocol
 * 16        NNSSSSSCCCLL  12 character network,station,channel,location code
 * 28        long       Time of the first sample in millis since 1970 (big endian order)
 * 36        double     Rate in Hz (big endian order)
 * 44        int[nsamp] The data samples 4 bytes for each sample (big endian order)
 *
 *   For MiniSeed packets, the header is all ascii except for byte 2 which is the binary length
 * of the header in bytes from "WRITE" to the "A" or "N"
 * DL\<size\>WRITE NN_SSSS_LL_CC/MSEED StartMS EndMS [AN][Binary data packet]
 * Offset     type      Description
 * 0          "DL"      Lead in characters in ascii
 * 2          byte       Size of the header in bytes (Length from "WRITE" to the MiniSeed size inclusive) varies because of ASCII encodings
 * 3          "WRITE "   5 bytes In ascii
 * 8        NN_SSSS_LL_CCC  12 character network,station,channel,location code
 * ??       "/MSEED "   Characters indicating this is MiniSEED (note space)
 * ??       Start       String time in millis since 1970 in ascii
 *          space       Separates the start and end times
 *          End         String end time in millis since 1970 in ascii
 *          space
 *          A or N      Depending on Ack mode
 *          space
 *          MS_Size     Size of the miniseed packet in bytes encoded as ascii (normally 512)
 * 20       byte[512] Containing one MiniSEED packet
 *
 * </pre>
 *
 * @author U.S. Geological Survey <ketchum at usgs.gov>
 */
public final class DataLinkToQuerySocket extends EdgeThread {

  private final int port;
  private final String host;
  private int countMsgs;
  private int heartBeat;
  private final int msgLength;
  private final Socket d;           // Socket to the server
  private long lastStatus;     // Time last status was received
  private InputStream in;
  private OutputStream outsock;
  private long bytesIn;
  private long bytesOut;
  private boolean hydra;
  //private String seedname;        // The NNSSSSS for a station coming through this socket.
  private boolean dbg;
  private final EdgeThread parent;
  private final int waitms;
  private long terminateTime;
  private int state;

  //private String ID;            // This ID is the first command sent and IDs the sender
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

  //public String getSeedname() {return seedname;}
  /**
   * create a new instance of DataLinkSocket (usually for DataLinkServer)
   *
   * @param s The socket to read the data from
   * @param par the parent object. Used for logging to the same file
   * @param tg The EdgeThread tag to use
   * @param wait Wait time in millis between each block received - this
   * parameter implemented -no waits are done
   *
   */
  public DataLinkToQuerySocket(Socket s, DataLinkToQueryServer par, String tg, int wait) {
    super("", tg);

    tag += "DLSkt:[" + s.getInetAddress().getHostAddress() + "/" + s.getLocalPort() + "/" + s.getPort() + "]";
    parent = par;
    this.setPrintStream(parent.getPrintStream());   // use same log as the server.
    d = s;
    waitms = wait;
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
    parent.prt(tag + "DataLinkSocket: new line parsed to host=" + host + " port=" + port + " len="
            + msgLength + " dbg=" + dbg + " mswait=" + waitms);
    IndexFile.init();
    start();
  }

  @Override
  public void terminate() {
    terminate = true;
    terminateTime = System.currentTimeMillis();
    if (d != null) {
      parent.prta(tag + "DLSkt: Terminate started. Close input unit.");
      try {
        d.close();
      } catch (IOException expected) {
      }
      interrupt();
    } else {
      parent.prt(tag + "DLSkt: Terminate started. interrupt().");
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
    byte[] bufhdr = new byte[msgLength];
    ByteBuffer bbhdr = ByteBuffer.wrap(bufhdr);
    StringBuilder sb = new StringBuilder(255);      // staus for run()
    StringBuilder stringID = new StringBuilder(20); // build the ID in run()
    StringBuilder seedname = new StringBuilder(12);
    byte[] seednamebuf = new byte[12];
    int[] data = new int[100];
    MiniSeed ms = null;
    int nillegal = 0;
    int hdrlen;
    running = true;               // mark we are running
    int lastCount = countMsgs;
    parent.prta(tag + " Socket is opened.  Start reads. " + host + "/" + port + " " + getName());
    // Read data from the socket and update/create the list of records 
    int len = msgLength;
    int l = 0;
    int nchar = 0;
    lastStatus = System.currentTimeMillis();
    long openTime = lastStatus;
    // Insure the QuerySpanCollection is up
    if (!QuerySpanCollection.isUp() || MiniSeedCollection.isUp()) {
      for (int i = 0; i < 100; i++) {
        try {
          sleep(100);
        } catch (InterruptedException expected) {
        }
        if (QuerySpanCollection.isUp() || MiniSeedCollection.isUp()) {
          break;
        }
        if (i == 99) {
          SendEvent.edgeSMEEvent("NoQSCollection", "A QuerySpan collection has not been created", "DataLiniToQuerySocket");
        }
      }
    }
    parent.prta(tag + " Wait for QuerySpanCollection and MiniSeedCollection is done");
    int nsamp = 0;
    int ns;
    int nsleft = 0;
    //int [] data = null;      // Need to decompress the data
    long now;
    long start;
    double rate;
    String response = "ID DataLink 2012.126 :: DLPROTO:1.0 PACKETSIZE:512 WRITE";
    byte[] responseBytes = response.getBytes();
    long hpdatastart;
    long hpdataend;
    int size;
    int n;
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
        l = 0;                    // offset into buf for next read
        nchar = 0;                // Number of characters returned
        // packets are "DL<hdrlen><hdrlen bytes of header><data payload>
        state = 2;
        nchar = Util.readFully(in, bufhdr, 0, 3);  // Get the 'D','L' and hdr len
        if (nchar == 0) {
          if (d.isClosed()) {
            prta("**** socket has closed");
          }
          state = 20;
          break;
        }
        if (bufhdr[0] != 'D' || bufhdr[1] != 'L') {
          prta(tag + " **** Bad back header not DL [0]=" + bufhdr[0] + " [1]=" + bufhdr[1] + " [2]=" + bufhdr[2] + " "
                  + ((char) bufhdr[0]) + ((char) bufhdr[1]) + ((char) bufhdr[2]));
          try {
            sleep(1000);
          } catch (InterruptedException expected) {
          }
          while (in.available() > 0) {
            int j = in.read(bufhdr);
            for (int i = 0; i < j; i++) {
              prt("[" + i + "]=" + bufhdr[i] + " " + Character.toString((char) bufhdr[i]));
            }
          }
          if (d.isClosed()) {
            prta(tag + "**** socket has closed");
          }
          state = 20;
          break;
        }
        hdrlen = ((int) bufhdr[2]) & 0xff;
        state = 3;
        nchar = Util.readFully(in, bufhdr, 3, hdrlen);  // read in the header
        state = 4;
        if (nchar <= 0) {
          break;      // EOF - close up - go to outer infinite loop
        }        // put command in StringBuilder
        Util.clear(sb);
        for (int i = 3; i < 3 + hdrlen; i++) {
          sb.append((char) bufhdr[i]);
        }
        if (dbg) {
          prta(tag + "Got message len=" + hdrlen + " sb=" + sb.toString());
        }
        state = 5;
        // Is this a startup string protocol line?
        if (sb.indexOf("ID ") == 0) {
          state = 6;
          if (dbg) {
            prta(tag + "Got ID connect ID=" + sb.substring(3));
          }
          bufhdr[2] = (byte) response.length();
          System.arraycopy(responseBytes, 0, bufhdr, 3, response.length());
          outsock.write(bufhdr, 0, response.length() + 3);
        } else if (sb.indexOf("WRITE") == 0) {
          state = 6;
          /* the buffer is 
           * off   type      description
           * 0    byte      27 - first lead in
           * 1    byte      3 - the 2nd lead in
           * 2    short     Number of samples
           * 4    String12  The NNSSSSSCCCLL 
           * 16   long      String time in millis
           * 24    double   rate
           * 32    int[]    Data samples
           */
          char flags = 9;
          if (sb.indexOf("/RAW") >= 0) {    // Is this raw input, rather than miniseed
            state = 661;
            nchar = Util.readFully(in, buf, 0, 4);    // the lead in bytes plus the size of the data
            nsamp = (((int) buf[2]) & 255) * 256 + (((int) buf[3]) & 255);
            if (nsamp < 0) {
              flags = 'A';
              nsamp = -nsamp;
            }
            nchar = Util.readFully(in, buf, 4, nsamp * 4 + 28);
            bb.position(4);           // point it at the seedname
            bb.get(seednamebuf);
            start = bb.getLong();
            rate = bb.getDouble();
            for (int i = 0; i < nsamp; i++) {
              data[i] = bb.getInt();
            }
            if (dbg || countMsgs % 10000 == 0) {
              parent.prta(tag + " Raw data : " + new String(seednamebuf) + " "
                      + Util.ascdatetime2(start) + " rt=" + rate + " ns=" + nsamp + " #=" + countMsgs);
            }
            state = 662;
            Util.clear(seedname);
            for (int jj = 0; jj < 12; jj++) {
              seedname.append((char) seednamebuf[jj]);
            }
            QuerySpanCollection.add(seedname, start, data, nsamp, rate);
            bytesIn += bb.position();
            DataLinkSplitter.write(bufhdr, hdrlen, buf, bb.position());
            if (flags == 'A') {
              writeAck(buf);
            }
            state = 663;
          } else {
            Util.clear(stringID);
            hpdatastart = 0;
            hpdataend = 0;
            size = 0;
            n = 0;
            flags = 9;
            for (int i = 9; i < sb.length() + 3; i++) {
              if (bufhdr[i] == ' ') {
                n++;
              } else if (n == 0) {
                stringID.append((char) bufhdr[i]);
              } else if (n == 1) {
                hpdatastart = hpdatastart * 10 + (bufhdr[i] - '0');
              } else if (n == 2) {
                hpdataend = hpdataend * 10 + (bufhdr[i] - '0');
              } else if (n == 3) {
                flags = (char) bufhdr[i];
              } else if (n == 4) {
                size = size * 10 + (bufhdr[i] - '0');
              }
            }
            if (dbg) {
              prt(tag + "stringid+" + stringID + " st=" + hpdatastart + " end=" + hpdataend + " size=" + size);
            }
            if (size != 512) {
              prta(tag + " ***** Size of message for miniseed is not 512 - len=" + size);
            }
            state = 61;
            nchar = Util.readFully(in, buf, 0, size);  // get the miniSEED block 
            state = 62;
            DataLinkSplitter.write(bufhdr, hdrlen, buf, size);
            bytesIn += size;
            // break the data into MiniSeed and send it out, reuse our ms holder

            if (waitms > 0) {
              try {
                sleep(waitms);
              } catch (InterruptedException expected) {
              }
            }
            state = 65;
            nillegal = 0;                 // must be a legal seed name
            if (dbg || countMsgs % 10000 == 0) {
              if (ms == null) {
                ms = new MiniSeed(buf);
              } else {
                ms.load(buf, 0, size);
              }
              parent.prta(tag + " " + ms.toString().substring(0, 68) + " #=" + countMsgs);
            }
            state = 66;
            QuerySpanCollection.add(buf);
            state = 68;
            MiniSeedCollection.add(buf);
            state = 69;
          }
          if (flags == 'A') {
            writeAck(buf);
          }

          //try{sleep(1000);} catch(InterruptedException expected) {}  //DEBUG:
          state = 67;
        } else if (sb.indexOf("POSITION") == 0) {
          prta(tag + "*** Unimplemented command " + sb.toString() + " hdrlen=" + hdrlen);
        } else if (sb.indexOf("MATCH") == 0) {
          prta(tag + "*** Unimplemented command " + sb.toString() + " hdrlen=" + hdrlen);

        } else if (sb.indexOf("READ") == 0) {
          prta(tag + "*** Unimplemented command " + sb.toString() + " hdrlen=" + hdrlen);

        } else if (sb.indexOf("STREAM") == 0) {
          prta(tag + "*** Unimplemented command " + sb.toString() + " hdrlen=" + hdrlen);

        } else if (sb.indexOf("ENDSTREAM") == 0) {
          prta(tag + "*** Unimplemented command " + sb.toString() + " hdrlen=" + hdrlen);

        } else if (sb.indexOf("INFO") == 0) {
          prta(tag + "*** Unimplemented command " + sb.toString() + " hdrlen=" + hdrlen);

        } else {
          prta("*** Unimplemented command " + sb.toString() + " hdrlen=" + hdrlen);
        }

        countMsgs++;
        now = System.currentTimeMillis();

        // Is it time for status yet
        if ((now - lastStatus) > 600000) {
          prta(tag + "DLS: # Rcv=" + (countMsgs - lastCount) + " #tot=" + countMsgs + " hbt=" + heartBeat + " waitms=" + waitms);
          lastCount = countMsgs;
          lastStatus = now;
        }
        state = 8;
      } catch (IOException e) {
        if (e.getMessage().equalsIgnoreCase("Socket closed") && terminate) {
          parent.prta(tag + " Doing termination via Socket close.");
        } else {
          Util.IOErrorPrint(e, "DLS: receive through IO exception", parent.getPrintStream());
        }
        break;      // Drop out of read loop to connect looop
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
          terminate = true;    // if 3 in a row, then close connection
        }
      } catch (RuntimeException e) {
        parent.prta(tag + " RuntimeException : " + e + " nc=" + nchar + " len=" + len + " l=" + l + " ns=" + nsamp
                + " nleft=" + nsleft);
        Util.bytesToSB(buf, 48, runsb);
        prt(runsb);
        e.printStackTrace();
        e.printStackTrace(parent.getPrintStream());
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
        parent.prta(tag + "DLS: Runtime during exit e=" + e);
      }
      e.printStackTrace(parent.getPrintStream());
    }
    prta(tag + " is terminated msgs = " + countMsgs + " " + ((int) (countMsgs / ((System.currentTimeMillis() - openTime) / 1000.))) + " msg/s");
    running = false;
    terminate = false;
  }
  StringBuilder runsb = new StringBuilder(1000);

  private void writeAck(byte[] buf) throws IOException {
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

  /**
   * return the monitor string for Nagios
   *
   * @return A String representing the monitor key value pairs for this
   * EdgeThread
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
  } //  we use parent.prt directly

  /**
   * Test routine
   *
   * @param args the command line arguments
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
