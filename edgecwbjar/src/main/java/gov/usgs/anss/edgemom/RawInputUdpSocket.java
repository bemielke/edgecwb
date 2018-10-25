/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.edgemom;

import gov.usgs.anss.edge.RawToMiniSeed;
import gov.usgs.anss.edge.IndexFile;
import gov.usgs.anss.edge.EdgeProperties;
import gov.usgs.anss.edgethread.EdgeThread;
import gov.usgs.anss.util.Util;
import java.io.*;
import java.net.*;
import java.nio.*;

/**
 *
 * @author davidketchum
 */
public final class RawInputUdpSocket extends EdgeThread {

  DatagramSocket d;               // Socket we will read forever (or until misalignment)
  int port;                       // Udp port we accept data from
  boolean dbg;
  long bytesin;                  // Count input bytes
  long bytesout;                 // Count output bytes (should be none)
  int totmsgs;                   // Count input messages
  long lastRead;                 // CurrentTimeMillis() of last I/O
  int msgLength = 576;

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
   * @param t If true, the run() method will terminate at the next change and
   * exit.
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
   * @param s2 A socket end which will be this socket's end.
   */
  //public void setSocket(Socket s2) {s = s2; start();}
  /**
   * Return a descriptive tag of this socket (basically "RIS:" plus the tag of
   * creation).
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
   * Return a measure of input volume.
   *
   * @return The number of bytes read from this socket since this object's creation.
   */
  @Override
  public long getBytesIn() {
    return bytesin;
  }

  /**
   * Return measure of output volume.
   *
   * @return The number of bytes written to this socket since this object was
   * created.
   */
  @Override
  public long getBytesOut() {
    return bytesout;
  }

  /**
   * Return ms of last I/O operation on this channel (from
   * System.currentTimeMillis()).
   *
   * @return ms value at last read or I/O operation.
   */
  public long getLastRead() {
    return lastRead;
  }

  /**
   * Creates a new instance of RawInputUdpSocket.
   *
   * @param argline The argument line.
   * @param tg Logging tag.
   */
  public RawInputUdpSocket(String argline, String tg) {
    super(argline, tg);
    String[] args = argline.split("\\s");
    dbg = false;
    int len = 512;
    String h = null;
    port = 7981;
    for (int i = 0; i < args.length; i++) {
      //prt(i+" arg="+args[i]);
      if (args[i].equals("-p")) {
        port = Integer.parseInt(args[i + 1]);
        i++;
      } else if (args[i].equals("-dbg")) {
        dbg = true;
      } else if (args[i].substring(0, 1).equals(">")) {
        break;
      } else {
        prt("RawInputUdpServer unknown switch=" + args[i] + " ln=" + argline);
      }
    }
    bytesin = 0;
    bytesout = 0;
    totmsgs = 0;
    terminate = false;
    running = false;
    start();
  }

  @Override
  public void run() {
    ByteBuffer b;
    byte[] buf = new byte[msgLength];
    int[] ts = new int[msgLength / 4];
    running = true;

    // Variables from each read header
    StringBuilder seedname = new StringBuilder(12);        // 12 character seed name
    double rate;
    short leadin;
    short nsamp;
    byte[] seedArray = new byte[12];
    short year;
    short doy;
    short rateMantissa;
    short rateMultiplier;
    byte activity;
    byte ioClock;
    byte quality;
    byte timingQuality;
    int sec;
    int usec;
    int seq;
    while (!terminate) {
      try {
        if (terminate) {
          break;
        }
        prt(Util.asctime() + " RawInputUdpSocket: Open Port=" + port);
        d = new DatagramSocket(port);
        lastRead = System.currentTimeMillis();
        break;
      } catch (SocketException e) {
        if (e.getMessage().equals("Address already in use")) {
          try {
            prt("RawInputUdpSocket: Address in use - try again.");
            Thread.sleep(2000);
          } catch (InterruptedException E) {

          }
        } else {
          prt("Error opening UDP port =" + port + "-" + e.getMessage());

          try {
            Thread.sleep(2000);
          } catch (InterruptedException E) {

          }
        }
      }
    }       // while(true) on opening UdpSocket
    DatagramPacket p = new DatagramPacket(buf, msgLength);

    // This loop receive UDP packets 
    while (!terminate) {
      try {
        d.receive(p);
        totmsgs++;
        if (dbg) {
          prt(Util.asctime() + " RawInputUdpSocket: Rcv adr=" + p.getSocketAddress()
                  + " len=" + p.getLength() + " totmsg=" + totmsgs);
        }
        if (p.getLength() > msgLength) {
          prta("RawInputUdpSocket: Datagram wrong size=" + msgLength
                  + "!=" + p.getLength() + " rcv adr=" + p.getSocketAddress());
        }
        buf = p.getData();
        b = ByteBuffer.wrap(buf);
        leadin = b.getShort();
        nsamp = b.getShort();
        b.get(seedArray, 0, 12);
        Util.clear(seedname);
        for (int i = 0; i < 12; i++) {
          seedname.append((char) seedArray[i]);
        }
        //seedname = new String(seedArray, 0, 12);
        year = b.getShort();
        doy = b.getShort();
        rateMantissa = b.getShort();
        rateMultiplier = b.getShort();
        activity = b.get();
        ioClock = b.get();
        quality = b.get();
        timingQuality = b.get();
        sec = b.getInt();
        usec = b.getInt();
        seq = b.getInt();

        if (leadin != (short) 0xa1b2) {
          prt(tag + " RIUS: Lead in not right.  Ignore data " + Integer.toHexString(leadin));
          continue;
        }
        bytesin += p.getLength();
        for (int i = 0; i < nsamp; i++) {
          ts[i] = b.getInt();
        }
        if (rateMultiplier < 0) {
          rate = (double) rateMantissa / (double) (-rateMultiplier);
        } else {
          rate = (double) rateMantissa * (double) rateMultiplier;
        }
        if (rateMantissa < 0) {
          rate = -1. / rate;
        }
        if (dbg) {
          prta("RIS: rcv=" + seedname + " ns=" + nsamp + " rt=" + rate
                  + " date=" + year + " " + doy + " " + sec + "." + usec
                  + " act=" + Integer.toHexString((int) activity)
                  + " ioclk=" + Integer.toHexString((int) ioClock)
                  + " qual=" + Integer.toHexString((int) quality));
        }

        RawToMiniSeed.addTimeseries(ts, (int) nsamp, seedname, (int) year, (int) doy, sec,
                usec, rate, (int) activity, (int) ioClock, (int) quality, (int) timingQuality,
                (EdgeThread) this);
        lastRead = System.currentTimeMillis();
      } catch (IOException e) {
        prt("RawInputUdpSocket:receive through IO exception");
      }
    }         // End of while(!terminate)
    prta("RawInputUdpSocket has been terminated.");
    if (!d.isClosed()) {
      d.close();    // Clean up UdpSocket if it is open
    }
    running = false;              // Let edgeThread know we are done
    terminate = false;
  }

  /*@param args Unused command line args*/
  public static void main(String[] args) {
    Util.setModeGMT();
    IndexFile.init();
    EdgeProperties.init();

    RawInputUdpSocket server = new RawInputUdpSocket("-p 7982 -dbg", "RIUS");
  }

}
