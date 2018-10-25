/*
 *  This software is in the public domain because it contains materials 
 *  that originally came from the United States Geological Survey, 
 *  an agency of the United States Department of Interior. For more 
 *  information, see the official USGS copyright policy at 
 *  http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.waveserver;

import gov.usgs.anss.edge.IllegalSeednameException;
import gov.usgs.anss.seed.MiniSeed;
import gov.usgs.anss.seed.SteimException;
import gov.usgs.anss.util.Util;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.GregorianCalendar;

/**
 * This class received data from a socket to a DataLinkSplitter on a QueryMom.
 * It reads both RAW and MiniSeed data using the DataLink protocol and returns
 * the data as simple time series by channel
 *
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
public class SimpleDataLinkClient {

  private final int port;
  private final String host;
  private final Socket d;           // Socket to the server
  private InputStream in;
  private OutputStream outsock;
  private String channelRE;
  //private String seedname;        // The NNSSSSS for a station coming through this socket.
  private boolean dbg;
  private byte[] buf = new byte[512];
  private final ByteBuffer bb;
  private byte[] bufhdr = new byte[100];
  private StringBuilder sb = new StringBuilder(255);      // staus for run()
  private StringBuilder stringID = new StringBuilder(20); // build the ID in run()
  private byte[] seednamebuf = new byte[12];
  private MiniSeed ms = null;

  //public String getSeedname() {return seedname;}
  /**
   * create a new instance of DataLinkSocket (usually for DataLinkServer)
   *
   * @param host Host to connect to IP or DNS name
   * @param port Port to connect to
   * @param regexp Channel regexp
   * @throws java.io.IOException Of one comes from the socket
   */
  public SimpleDataLinkClient(String host, int port, String regexp) throws IOException {
    this.port = port;
    this.host = host;
    channelRE = regexp;
    try {
      d = new Socket(host, port);
      in = d.getInputStream();
      outsock = d.getOutputStream();
    } catch (IOException e) {
      System.err.println("Error getting i/o streams from socket " + host + "/" + port);
      throw e;
    }
    System.out.println("DataLinkSocket: new line parsed to host=" + host + " port=" + port);
    bb = ByteBuffer.wrap(buf);
  }

  /**
   * Read a packet of time series from the link
   *
   * @param start On return contains the starting time of the first sample
   * @param seedname On return contains the channel returned
   * @param data On return contains the nsamps of data
   * @return if > 0, the number of samples, if zero, something went wrong
   * @throws IOException If the socket throws one
   */
  public int getPacket(GregorianCalendar start, StringBuilder seedname, int[] data)
          throws IOException {

    int nillegal = 0;
    int hdrlen;
    // Read data from the socket and update/create the list of records 
    //int [] data = null;      // Need to decompress the data
    long now;
    double rate;
    String response = "ID DataLink 2012.126 :: DLPROTO:1.0 PACKETSIZE:512 WRITE";
    byte[] responseBytes = response.getBytes();
    long hpdatastart;
    long hpdataend;
    int size;
    int n, nsamp;
    int nchar, l;
    while (true) {
      try {
        l = 0;                    // offset into buf for next read
        // packets are "DL<hdrlen><hdrlen bytes of header><data payload>
        nchar = Util.readFully(in, bufhdr, 0, 3);  // Get the 'D','L' and hdr len
        if (nchar == 0) {
          if (d.isClosed()) {
            System.out.println("**** socket has closed");
          }
          break;
        }
        if (bufhdr[0] != 'D' || bufhdr[1] != 'L') {
          System.err.println(" **** Bad back header not DL [0]=" + bufhdr[0] + " [1]=" + bufhdr[1] + " [2]=" + bufhdr[2] + " "
                  + ((char) bufhdr[0]) + ((char) bufhdr[1]) + ((char) bufhdr[2]));
          while (in.available() > 0) {
            int j = in.read(bufhdr);
            for (int i = 0; i < j; i++) {
              System.err.println("[" + i + "]=" + bufhdr[i] + " " + Character.toString((char) bufhdr[i]));
            }
          }
          if (d.isClosed()) {
            System.err.println("**** socket has closed");
          }
          break;
        }
        hdrlen = ((int) bufhdr[2]) & 0xff;
        nchar = Util.readFully(in, bufhdr, 3, hdrlen);  // read in the header
        if (nchar <= 0) {
          break;      // EOF - close up - go to outer infinite loop
        }        // put command in StringBuilder
        Util.clear(sb);
        for (int i = 3; i < 3 + hdrlen; i++) {
          sb.append((char) bufhdr[i]);
        }
        // Is this a startup string protocol line?
        if (sb.indexOf("ID ") == 0) {
          if (dbg) {
            System.out.println("Got ID connect ID=" + sb.substring(3));
          }
          bufhdr[2] = (byte) response.length();
          System.arraycopy(responseBytes, 0, bufhdr, 3, response.length());
          outsock.write(bufhdr, 0, response.length() + 3);
          return 0;
        } else if (sb.indexOf("WRITE") == 0) {
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
            //nchar = 
            Util.readFully(in, buf, 0, 4);    // the lead in bytes plus the size of the data
            nsamp = (((int) buf[2]) & 255) * 256 + (((int) buf[3]) & 255);
            if (nsamp < 0) {
              flags = 'A';
              nsamp = -nsamp;
            }
            //nchar = 
            Util.readFully(in, buf, 4, nsamp * 4 + 28);
            bb.position(4);           // point it at the seedname
            bb.get(seednamebuf);
            start.setTimeInMillis(bb.getLong());
            rate = bb.getDouble();
            for (int i = 0; i < nsamp; i++) {
              data[i] = bb.getInt();
            }
            if (dbg) {
              System.out.println("Raw data : " + new String(seednamebuf) + " "
                      + Util.ascdatetime2(start) + " rt=" + rate + " ns=" + nsamp);
            }
            Util.clear(seedname);
            for (int jj = 0; jj < 12; jj++) {
              seedname.append((char) seednamebuf[jj]);
            }
            if (flags == 'A') {
              writeAck(buf);
            }
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
              System.out.println("stringid+" + stringID + " st=" + hpdatastart + " end=" + hpdataend + " size=" + size);
            }
            if (size != 512) {
              System.out.println(" ***** Size of message for miniseed is not 512 - len=" + size);
            }
            //nchar = 
            Util.readFully(in, buf, 0, size);  // get the miniSEED block 
            if (ms == null) {
              ms = new MiniSeed(buf, 0, size);
            } else {
              ms.load(buf, 0, size);
            }
            if (dbg) {
              System.out.println("ms=" + ms);
            }
            start.setTimeInMillis(ms.getTimeInMillis());
            try {
              ms.decomp(data);
              nsamp = ms.getNsamp();
            } catch (SteimException expected) {
              nsamp = 0;
            }   // Not much we can do?
            if (flags == 'A') {
              writeAck(buf);
            }
          }
          if (seedname.toString().matches(channelRE)) {
            return nsamp;
          }
        } else if (sb.indexOf("POSITION") == 0) {
          System.out.println("*** Unimplemented command " + sb.toString() + " hdrlen=" + hdrlen);
        } else if (sb.indexOf("MATCH") == 0) {
          System.out.println("*** Unimplemented command " + sb.toString() + " hdrlen=" + hdrlen);

        } else if (sb.indexOf("READ") == 0) {
          System.out.println("*** Unimplemented command " + sb.toString() + " hdrlen=" + hdrlen);

        } else if (sb.indexOf("STREAM") == 0) {
          System.out.println("*** Unimplemented command " + sb.toString() + " hdrlen=" + hdrlen);

        } else if (sb.indexOf("ENDSTREAM") == 0) {
          System.out.println("*** Unimplemented command " + sb.toString() + " hdrlen=" + hdrlen);

        } else if (sb.indexOf("INFO") == 0) {
          System.out.println("*** Unimplemented command " + sb.toString() + " hdrlen=" + hdrlen);

        } else {
          System.out.println("*** Unimplemented command " + sb.toString() + " hdrlen=" + hdrlen);
        }
      } catch (IOException e) {
        throw e;
      } catch (IllegalSeednameException e) {
        nillegal++;
        if (ms != null) {
          System.out.println(" IllegalSeedName =" + nillegal + " "
                  + Util.toAllPrintable(ms.getSeedNameString()) + " " + e.getMessage());
        } else {
          System.out.println(" IllegalSeedName =" + nillegal + " ms is null. "
                  + e.getMessage());
        }
        for (int i = 0; i < 48; i++) {
          System.out.println(i + " = " + buf[i] + " " + (char) buf[i]);
        }
      }
    }
    return 0;
  }

  private void writeAck(byte[] buf) throws IOException {
    buf[2] = 6;
    buf[3] = 'O';
    buf[4] = 'K';
    buf[5] = ' ';
    buf[6] = '1';
    buf[7] = ' ';
    buf[8] = '0';
    outsock.write(buf, 0, 9);
  }

  public static void main(String[] args) {
    Util.setModeGMT();
    StringBuilder seedname = new StringBuilder(12);
    int nsamp;
    int[] data = new int[720];
    GregorianCalendar start = new GregorianCalendar();

    try {
      SimpleDataLinkClient mss = new SimpleDataLinkClient("localhost", 16098, "NT.....SVZ..");
      for (;;) {
        nsamp = mss.getPacket(start, seedname, data);
        System.out.println(Util.ascdatetime2(start) + " " + seedname + " nsamp=" + nsamp);
      }
    } catch (IOException e) {
      e.printStackTrace();
    }

  }

}
