/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.util;

import gov.usgs.anss.edgethread.EdgeThread;
import java.io.IOException;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;

/**
 * ClientSocket.java This represent one end of a socket. The "Client" is because this object is
 * generally created by a "ServerSocket" when each new "Client" creates a connection. For instance,
 * ServerThread creates on of this class for each subscriber who connects up. In this case the
 * "Threading" and buffering of this class is needed to keep the ServerThread from blocking.
 *
 * Created on July 29, 2003, 5:50 PM
 *
 *
 * @author ketchum
 */
public final class ClientSocket extends Thread {

  private Socket s;
  private OutputStream out;
  private int pseq;
  private byte[] buf;
  private ArrayList<QBuffer> outq;
  private int outqHighWater;
  private long lastWrite;
  private long bytesOut;
  private boolean writeActive;
  private boolean gombergMode;
  private int maxBuffers;
  private boolean outside;
  private String tag;
  private EdgeThread par;

  public void setParent(EdgeThread parent) {
    par = parent;
  }

  private void prt(String a) {
    if (par == null) {
      Util.prt(a);
    } else {
      par.prt(a);
    }
  }

  private void prt(StringBuilder a) {
    if (par == null) {
      Util.prt(a);
    } else {
      par.prt(a);
    }
  }

  private void prta(String a) {
    if (par == null) {
      Util.prta(a);
    } else {
      par.prta(a);
    }
  }

  private void prta(StringBuilder a) {
    if (par == null) {
      Util.prta(a);
    } else {
      par.prta(a);
    }
  }

  public boolean isOutside() {
    return outside;
  }
  private StringBuilder sb = new StringBuilder(10);

  /**
   * Return a string representing this ClientSocket
   *
   * @return A string representation of this ClientSocket
   */
  @Override
  public String toString() {
    return toStringSB().toString();
  }

  public StringBuilder toStringSB() {
    Util.clear(sb);
    if (s == null) {
      return sb.append(tag).append("Socket already nulled");
    }
    if (gombergMode) {
      return sb.append(tag).append(s.getInetAddress().toString()).append(" sq=").append(pseq & 0xff);
    } else {
      return sb.append(tag).append(s.getInetAddress().toString()).append(" ").
              append(bytesOut / 1000).append(" kB ").append(outside ? "out" : "in ").
              append(" outq=").append(outq.size()).append("/").append(outqHighWater).append("/").append(maxBuffers);
    }
  }

  public boolean isClosed() {
    return (s == null);
  }

  /**
   * Construct a client socket with socket and gomberg flag
   *
   * @param st a socket already opened we are going to use
   * @param gb a boolean indicating if GombergPackets are expected on the socket
   * @param maxBuf
   */
  public ClientSocket(Socket st, boolean gb, int maxBuf) {
    tag = "CS:[";
    tag = tag + st.getInetAddress().toString().substring(1) + "/" + st.getPort() + "-"
            + this.getName().substring(getName().indexOf("-") + 1) + "] ";
    try {
      String local = InetAddress.getLocalHost().toString();
      prt(tag + " USGS install local=" + local);
      if (Util.isNEICHost(local) ) {
        if (st.getInetAddress().toString().substring(1).equals("127.0.0.1")) {
          outside = false;
        } else if (st.getInetAddress().toString().substring(1).contains("136.") && st.getInetAddress().toString().substring(1).contains(".177")) {
          outside = false;  // gldway1
        }
      }
    } catch (UnknownHostException e) {
    }
    s = st;
    maxBuffers = maxBuf;
    buf = new byte[2048];
    pseq = 1;
    gombergMode = gb;
    outq = new ArrayList<>(2000);
    try {
      out = s.getOutputStream();
    } catch (IOException e) {
      Util.IOErrorPrint(e, tag + " Create client failed to get OutputStream");
    }
    prta(tag + "created to " + s + " gb=" + gombergMode + " " + (outside ? "outside" : "inside"));
    lastWrite = System.currentTimeMillis();
    start();                           // Start up the writer threads
  }

  /**
   * This thread looks for more data in its output queue and sends it when some is found.
   */
  @Override
  public void run() {
    QBuffer qb;
    while (true) {
      if (s == null) {
        break;
      }
      try {
        while (outq.isEmpty()) {
          sleep((long) 100);
        }
        if (outq.isEmpty()) {
          continue;
        }
        if (out == null) {
          continue;
        }
        qb = outq.get(0);
        //if(qb == null) prta(tag+"err qb is null outq.size()="+outq.size());
        outq.remove(0);
        if (outq.size() % 5000 == 0 && outq.size() > 990) {
          prta(tag + " QBuffer down #=" + outq.size());
        }
        try {
          lastWrite = System.currentTimeMillis();
          writeActive = true;
          //prta("Write out "+qb.getData().length+" len="+qb.getLength());
          if (out == null) {
            prta(tag + "err out is null " + toString());
          }
          if (qb == null) {/*prta(tag+"err qb is null "+toString());*/ continue;
          }
          if (qb.getData() == null) {
            prta(tag + "CS: err qb.getData() is null " + toString());
          }
          out.write(qb.getData(), 0, qb.getLength());
          bytesOut += qb.getLength();
          writeActive = false;
        } catch (IOException e) {
          try {
            Util.SocketIOErrorPrint(e, tag + " Client " + toString() + " writing to socket");
            if (s != null) {
              s.close();
            }
            s = null;
          } catch (IOException e2) {
            Util.IOErrorPrint(e2, tag + "ClientSocket while closing bad socket");
          }
        }
      } catch (InterruptedException e) {
        prt(tag + "run() interrupted exception! loop! " + toString());
      }
    }             // While (true)
  }

  /**
   * close up the socket associated with this object
   */
  synchronized public void close() {
    try {
      prt(tag + "Closing client socket=" + toString());
      if (s == null) {
        return;
      }
      if (s.isClosed()) {
        return;
      }
      s.close();
      s = null;
    } catch (IOException e) {
      Util.SocketIOErrorPrint(e, tag + "closing " + s);
    }
    outq.clear();
  }

  /**
   * sendBufAsGomberg() sends the buffer but builds the gomberg header with the current time and
   * bogus route, node, and chan
   *
   * @param buf raw bytes to send
   * @param len number of bytes to send (should be less than buf.length
   * @exception IOException Thrown if error occurs writing the data
   */
  synchronized public void sendBufAsGomberg(byte[] buf, int len) throws IOException {
    GregorianCalendar d = new GregorianCalendar(); // Get system date/time
    NsnTime tc = new NsnTime(d.get(Calendar.YEAR), d.get(Calendar.DAY_OF_YEAR),
            d.get(Calendar.HOUR_OF_DAY), d.get(Calendar.MINUTE), d.get(Calendar.SECOND),
            d.get(Calendar.MILLISECOND), 0);
    GombergPacket gb = new GombergPacket((short) (len + 14), (byte) -2, (byte) 0,
            (byte) 126, (byte) (pseq++ & 0xff), tc, buf);
    byte[] b = gb.getAsBuf();
    write(b, len + 14);
  }

  /**
   * write() writes the raw buffer to this client. Use sendAsGomberg() to encapsulate
   *
   * @param p DatagramPacket with the data
   * @exception IOException Thrown if error occurs writing the data
   */
  synchronized public void write(DatagramPacket p) throws IOException {
    write(p.getData(), 0, p.getLength());
  }

  /**
   * Write out data via to this client
   *
   * @param b Raw data bytes to write
   * @param len number of bytes to write
   * @exception IOException Thrown if error occurs writing the data
   */
  synchronized public void write(byte[] b, int len) throws IOException {
    write(b, 0, len);
  }

  /**
   * Write out data via to this client
   *
   * @param b Raw data bytes to write
   * @param len number of bytes to write
   * @param offset Offset in b[] to start the write
   * @exception IOException Thrown if error occurs writing the data
   */
  synchronized public void write(byte[] b, int offset, int len) throws IOException {
    if (s == null) {
      throw new IOException("Socket is closed.");
    }
    if (outq.size() > maxBuffers) {
      throw new IOException("Exceeded max buffering in CLientSocket/ServerThread=" + maxBuffers);
    }
    QBuffer bf = new QBuffer(b, offset, len);
    //if(bf == null) prta(tag+"QBuffer returned a null?????"+toString());
    outq.add(bf);
    if (outq.size() % 5000 == 0) {
      prta(tag + " Write created QBufer #=" + outq.size());
    }
    if (outq.size() > outqHighWater) {
      outqHighWater = outq.size();
    }
  }

  /**
   * return true if a write is active on this client's socket
   *
   * @return true if a write is active
   */
  synchronized public boolean getWriteActive() {
    return writeActive;
  }

  /**
   * compute how long a write has been active in milliseconds
   *
   * @return the length of time a write has been active or zero if none is active
   */
  synchronized public int getWriteWaitMS() {
    if (!writeActive) {
      return 0;
    }
    return (int) (System.currentTimeMillis() - lastWrite);
  }

  /**
   * Return the OutputStream to this client;s socket
   *
   * @return the OutputStream for this socket
   */
  public Socket getSocket() {
    return s;
  }

  /**
   * Return the OutputStream to this client;s socket
   *
   * @return the OutputStream for this socket
   */
  public OutputStream getOut() {
    return out;
  }

  /**
   * Return number of write requests in the write Queue to this socket
   *
   * @return The number of writes which are pending in the Queue(one more might be active currently
   * but no longer in the que)
   */
  public int getNumberWriteQueue() {
    return outq.size();
  }

  /**
   * This internal class represent the data in one queued output. Basically it buffers the raw data
   * bytes and the number of bytes in the request
   */
  class QBuffer {

    byte[] b;
    int len;

    /**
     * create a QBuffer with data starting at in[offset] of length l
     *
     * @param in The raw data buffer
     * @param offset offset in in[offset] to start the buffer
     * @param l The number of bytes to store
     */
    public QBuffer(byte[] in, int offset, int l) {
      len = l;
      b = new byte[len];
      System.arraycopy(in, offset, b, 0, len);
    }

    /**
     * return the data as an array of raw bytes
     *
     * @return the array of bytes
     */
    public byte[] getData() {
      return b;
    }

    /**
     * return the length of the data in this buffer
     *
     * @return The length of data bytes
     */
    public int getLength() {
      return len;
    }

  }
} // end of class ClientSocket

