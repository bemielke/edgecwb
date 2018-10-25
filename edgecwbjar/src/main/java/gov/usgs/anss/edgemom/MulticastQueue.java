/*
 *  This software is in the public domain because it contains materials 
 *  that originally came from the United States Geological Survey, 
 *  an agency of the United States Department of Interior. For more 
 *  information, see the official USGS copyright policy at 
 *  http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.edgemom;

import gov.usgs.anss.util.Util;
import gov.usgs.anss.edgethread.EdgeThread;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.SocketAddress;
import java.util.ArrayList;

/**
 * This class is a generalized Client which queues and dequeues messages as byte
 * arrays normally for the MulticastListeners (queue) and the
 * MulticastProcessor(dequeue). The MultiCastProcessor creates one of these and
 * gives this object to all actually listeners. If the queue overflows the data
 * are discarded. Status includes number of discarded packets, packets
 * processed, queue highwater.
 * <p>
 * In addition to the binary bytes the length of each packet and the internet
 * socket of queuer is tracked for use by the processing routine.
 * <p>
 * <PRE>
 * switch     args     Description
 * -qsize    nn        Number of buffers of qlen to reserve for the queue
 * -qlen     nn        The length of the buffering in the queue(def=512)
 * -dbg                Create more output
 * </PRE>
 *
 * @author davidketchum
 */
public final class MulticastQueue extends Thread {

  private int MAX_LENGTH = 512;         // Maximum length of received data
  private boolean terminate;

  // Queue related parameters
  private int qsize;
  private final ArrayList<byte[]> queue;    // The array list of qsize buffers
  private final int[] qLengths;           // The actual length put in those buffers
  private final SocketAddress[] qFroms;   // The socket addresses the packet came from
  private final int portFrom[] ;
  private int nextin;               // Next place to put a rcvd packet
  private int nextout;              // next place a user would dequeue a packet

  // status and debug
  private long bytesout;
  private long bytesin;
  private long lastBytesin;
  private long lastStatus;
  private int ndiscard;             // # packets discarded on queue full
  private long totalrecs;           // total recs processed
  private long lasttotalrecs;
  private int maxused;              // High water mark of used packets in the queue.
  private SocketAddress lastSock;
  private int lastPort;

  // Other
  private EdgeThread par;
  private boolean dbg;
  private String tag;
  private boolean running;
  private StringBuilder tmpsb = new StringBuilder();    // used in all synchronized methods
  private StringBuilder tmpsb2 = new StringBuilder();   // onluse used by toStringBuilder()

  public long getLastTotalRecs() {return lasttotalrecs;}
  public boolean isRunning() {
    return running;
  }

  @Override
  public String toString() {
    return toStringBuilder(null).toString();
  }

  public StringBuilder toStringBuilder(StringBuilder tmp) {
    StringBuilder sb = tmp;
    if (sb == null) {
      sb = Util.clear(tmpsb2);
    }
    synchronized (sb) {
      lasttotalrecs = totalrecs;
      sb.append(tag).append("MCQ: out=").append(bytesout / 1000).append(" kB in=").append(bytesin / 1000).
              append(" kB #rec=").append(totalrecs).append(" used=").append(getUsed()).
              append(" maxused=").append(maxused).append("/").append(qsize).
              append(" #discard=").append(ndiscard);
    }
    return sb;
  }

  public int getMaxLength() {
    return MAX_LENGTH;
  }

  public void setDebug(boolean t) {
    dbg = t;
  }

  /**
   * returns true if the memory queue is full
   *
   * @return true if the memory queue is full
   */
  public synchronized boolean isQueueFull() {
    int next;
    next = nextin + 1;
    if (next >= qsize) {
      next = 0;
    }
    return next == nextout;
  }

  /**
   * returns the queue slot number of the next element which would be returned
   * to user
   *
   * @return The slot of the next element to the user
   */
  public int getNextout() {
    return nextout;
  }

  /**
   * return number of packets used in the memory queue
   *
   * @return The number of packets used
   */
  public synchronized int getUsed() {
    int nused = nextin - nextout;
    if (nused < 0) {
      nused += qsize;
    }
    return nused;
  }

  public long getBytesOut() {
    return bytesout;
  }

  public long getBytesIn() {
    return bytesin;
  }

  /**
   *
   * @return Maximum number of memory elements ever used
   */
  public int getMaxUsed() {
    return maxused;
  }

  /**
   *
   * @return The size of the memory queue
   */
  public int getQueueSize() {
    return qsize;
  }

  /**
   *
   * @return Number of packets discard because the queue is full
   */
  public int getNDiscard() {
    return ndiscard;
  }

  public void terminate() {
    terminate = true;
  }

  /**
   * set terminate variable
   *
   * @param t If true, the run() method will terminate at next change and exit
   */
  public void setTerminate(boolean t) {
    terminate = t;
    if (terminate) {
      terminate();
    }
  }

  /**
   * return the SocketAddress the last dequeued packet came from
   *
   * @return the SocketAddress of the last dequeued packet
   */
  public SocketAddress getSockAddress() {
    return lastSock;
  }
  
  /**
   * 
   * @return The port of the last element dequeued from this queue
   */
  public int getPort() {
    return lastPort;
  }
  /**
   * return number of packets received
   *
   * @return Number of packets returned.
   */
  public long getNpackets() {
    return totalrecs;
  }

  public final void prt(String s) {
    if (par == null) {
      Util.prt(s);
    } else {
      par.prt(s);
    }
  }

  public final void prta(String s) {
    if (par == null) {
      Util.prta(s);
    } else {
      par.prta(s);
    }
  }

  public final void prt(StringBuilder s) {
    if (par == null) {
      Util.prt(s);
    } else {
      par.prt(s);
    }
  }

  public final void prta(StringBuilder s) {
    if (par == null) {
      Util.prta(s);
    } else {
      par.prta(s);
    }
  }

  /**
   * Creates a new instance of MulticastQueuedListener
   *
   * @param argline An EdgeThread style command line of options
   * @param tag Tag for logging
   * @param parent A EdgeThread to use for logging - otherwise logging is done
   * per the argline
   * @throws java.io.IOException If the MulticastSocket or DatagramPacket cannot
   * be setup on the configured address and port
   */
  public MulticastQueue(String argline, String tag, EdgeThread parent) throws IOException {
    par = parent;
    qsize = 100;
    this.tag = tag;
    String[] args = argline.split("\\s");
    for (int i = 0; i < args.length; i++) {
      //prt(i+" arg="+args[i]);
      if (args[i].equals("-dbg")) {
        dbg = true;
      } else if (args[i].equalsIgnoreCase("-qsize")) {
        qsize = Integer.parseInt(args[i + 1]);
        i++;
      } else if (args[i].equalsIgnoreCase("-qlen")) {
        MAX_LENGTH = Integer.parseInt(args[i + 1]);
        i++;
      } else if (args[i].substring(0, 1).equals(">")) {
        break;
      }
    }
    // Allocat the qszied arrays for byte buffers, lengths, and from addresses
    queue = new ArrayList<>(qsize);
    qFroms = new SocketAddress[qsize];
    portFrom = new int[qsize];
    nextin = 0;
    terminate = false;
    nextout = 0;
    qLengths = new int[qsize];
    for (int i = 0; i < qsize; i++) {
      queue.add(i, new byte[MAX_LENGTH]);
    }
    setDaemon(true);
    prta(Util.clear(tmpsb).append(tag).append("MCQ: start MulticastQueue qsize=").append(qsize).
            append(" qlen=").append(MAX_LENGTH).append(" dbg=").append(dbg));
    start();

  }

  /**
   * Dequeue one buffer if it is available, the length of the buffer is returned
   * and the socket the data came from is available by calling getSockAddress
   * immediately after this returns something.
   *
   * @param buf User buffer to return the input packet
   * @return Number of bytes returned or zero if nothing was returned, or -1 if
   * there is nothing to return
   */
  public synchronized int dequeue(byte[] buf) {
    if (nextin == nextout) {
      return -1;
    }
    int ret = 0;
    try {
      System.arraycopy(queue.get(nextout), 0, buf, 0, qLengths[nextout]);
      ret = qLengths[nextout];
      lastSock = qFroms[nextout];
      lastPort = portFrom[nextout];
    } catch (ArrayIndexOutOfBoundsException e) {// This is to look for a bug!
      prta(Util.clear(tmpsb).append(tag).append("MSQ: OOB nextout=").append(nextout).
              append(" len=").append(buf.length).append(" ").append(queue.get(nextin).length));
      prta(Util.clear(tmpsb).append(tag).append("MCQ: arraycopy OOB exception e=").append(e.getMessage()));
      e.printStackTrace();
    }
    nextout++;
    if (nextout >= qsize) {
      nextout = 0;
    }
    return ret;
  }

  /**
   * Queue up the contents of one DatagramPacket - save the data play load as a
   * byte array, the length and the sender of the packet
   *
   * @param d The datagram packet to queue.
   * @param port the port the packet was received on
   * @return true, if the packet was queue, false if there was no room.
   */
  public synchronized boolean queue(DatagramPacket d, int port) {
    if ((nextin + 1) % qsize == nextout) {
      ndiscard++;
      if (ndiscard % 100 == 99) {
        prta(Util.clear(tmpsb).append(tag).append("MCQ:  **** Have to discard a buffer nextout=").append(nextout).
                append(" nextin=").append(nextin).append(" ").append(d.getSocketAddress()).append(" ").
                append(d.getPort()).append(" len=").append(d.getLength()).append(" #discard=").append(ndiscard));
      }

      return false;
    }
    qLengths[nextin] = d.getLength();
    qFroms[nextin] = d.getSocketAddress();
    portFrom[nextin] = port;
    bytesin += d.getLength();
    if (d.getLength() > MAX_LENGTH) {
      prta(Util.clear(tmpsb).append(tag).
              append("MCQ:  received a packet bigger than the max len=").append(d.getLength()).
              append(" max=").append(MAX_LENGTH));
    }
    System.arraycopy(d.getData(), 0, queue.get(nextin), 0, d.getLength());
    nextin = (++nextin) % qsize;

    maxused = Math.max(maxused, getUsed());
    totalrecs++;
    return true;
  }

  /**
   * periodically print a status message about the queue
   *
   */
  @Override
  public void run() {
    StringBuilder sb = new StringBuilder(100);
    running = true;
    // This loop establishes a socket
    while (!terminate) {
      try {
        sleep(1000);
      } catch (InterruptedException expected) {
      }
      if (System.currentTimeMillis() - lastStatus > 300000) {
        prta(toStringBuilder(Util.clear(sb)));
        lastStatus = System.currentTimeMillis();
        totalrecs = 0;
        lastBytesin = bytesin;
      }
    }
    // Outside loop that opens a socket 
    prta(Util.clear(tmpsb).append(tag).append("MCQ: ** MulticastQueue terminated ").append(nextin).append(" ").append(nextout));
    prta(Util.clear(tmpsb).append(tag).append("MCQ:  ** exiting"));
    running = false;
  }
}
