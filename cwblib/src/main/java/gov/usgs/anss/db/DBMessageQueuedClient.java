/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.db;

import gov.usgs.alarm.SendEvent;
import gov.usgs.anss.util.Util;
import gov.usgs.anss.edgethread.EdgeThread;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * This class can send new inserts for status, etc to a DBMessageServer. The form of the messages is
 * specified in that class.This class is a generalized Client which keeps a connection to a given
 * port open, queues input messages to that port, and sends those messages in an asynchronous
 * fashion. If the queue is full, any attempts to queue more data simply causes it to be discarded.
 * This particular class is for communicating with a DBMessageServer. The messages should be in the
 * form : database^table^attr=value;attr=val;.....;\n
 *
 * @author davidketchum
 */

public final class DBMessageQueuedClient extends Thread {

  private final int MAX_LENGTH;
  private String host;
  private final int port;
  private final byte[] tmpbuf;
  //private  byte [] type;

  // These are needed if TCP/IP is used
  private Socket ss;          // The TCP/IP socket (if used) 
  private OutputStream out;
  private InputStream in;
  private final ArrayList<byte[]> bufs;
  private final int[] lengths;
  private final int bufsize;
  private int nextin;
  private int nextout;
  private int ndiscard;
  private long totalrecs;
  private int used;
  private int maxused;
  private String tag;

  // status and debug
  private long lastStatus;
  private boolean terminate;
  private boolean dbg;
  private final EdgeThread par;

  public int getQsize() {
    return bufsize;
  }

  public int getPctFull() {
    return used * 100 / bufsize;
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

  /**
   * Creates a new instance of DBMessageQueuedClient
   *
   * @param h The host of the POCMySQLServer
   * @param pt The port of the POCMySQLServer
   * @param maxQueue Number of messages to reserve space for in queue
   * @param maxLength Maximum record length for these messages
   * @param parent An EdgeThread to log through
   */
  public DBMessageQueuedClient(String h, int pt, int maxQueue, int maxLength, EdgeThread parent) {
    par = parent;
    tag = "DBQC-" + this.getName().substring(7) + "_" + (parent == null ? "" : parent.getTag()) + "[]";
    if(maxQueue < 10) {
      SendEvent.edgeSMEEvent("DBMsgBadSize", "The maxQueue is very small "+maxQueue, this);
      prta("DBMQC: **** The maxQueue is too small - increase to 10 from "+maxQueue);
    }
    bufsize = Math.max(maxQueue,20);
    host = h;
    if (host.indexOf(":") > 0) {
      host = host.substring(0, host.indexOf(":"));
    }
    if (host.indexOf("/") > 0) {
      host = host.substring(0, host.indexOf("/"));
    }
    port = pt;
    MAX_LENGTH = maxLength;
    tmpbuf = new byte[MAX_LENGTH];
    bufs = new ArrayList<>(bufsize);
    nextin = 0;
    terminate = false;
    nextout = 0;
    lengths = new int[bufsize];
    if (Util.isShuttingDown()) {
      return;     // do not start new thread if shutting down
    }
    for (int i = 0; i < bufsize; i++) {
      bufs.add(i, new byte[MAX_LENGTH]);
    }
    //new RuntimeException("DBMessageQueuedClient created ").printStackTrace(par.getPrintStream());
    // Register our shutdown thread with the Runtime system.
    //new RuntimeException("Who created me ").printStackTrace(parent.getPrintStream());
    Runtime.getRuntime().addShutdownHook(new ShutdownDBMessage());
    prta(getStatusString());
    start();

  }

  public String getStatusString() {
    return tag + host + "/" + port + " nblks=" + totalrecs + " discards=" + ndiscard
            + " in=" + nextin + " out=" + nextout + " qsize=" + bufsize;
  }

  @Override
  public String toString() {
    return getStatusString();
  }

  public void terminate() {
    terminate = true;
    interrupt();
  }

  public boolean isQueueEmpty() {
    return nextin == nextout;
  }

  public boolean isQueueFull() {
    int next = nextin + 1;
    if (next >= bufsize) {
      next = 0;
    }
    return next == nextout;
  }

  public synchronized boolean queueDBMsg(StringBuilder s) {
    if (s.length() <= 0) {
      return true;   // no input
    }
    if (s.charAt(s.length() - 1) != '\n') {
      s.append('\n');
    }
    for (int i = 0; i < s.length(); i++) {
      tmpbuf[i] = (byte) s.charAt(i);
    }
    return queueDBMsg(tmpbuf, 0, s.length());
  }

  public synchronized boolean queueDBMsg2(String s) {
    return DBMessageQueuedClient.this.queueDBMsg((s + "\n").getBytes());
  }

  /**
   *
   * @param buf A buffer to send out, it must end with a newline!
   * @return True if queued
   */
  public synchronized boolean queueDBMsg(byte[] buf) {
    return queueDBMsg(buf, 0, buf.length);
  }

  /**
   * Send this byte buffer out. If it does not end with a newline, add one.
   *
   * @param buf byte buffer with the message
   * @param off Offset in buf to start
   * @param len length of the message in bytes
   * @return true if queued.
   */
  public synchronized boolean queueDBMsg(byte[] buf, int off, int len) {
    if ((char) buf[off + len - 1] != '\n') {
      buf[off + len] = '\n';
      len++;      // add the newline 
    }
    if (len > MAX_LENGTH) {
      prt(tag + " called with buffer to big len=" + len + " max=" + MAX_LENGTH);
      prt(tag + " msg=" + new String(buf, off, len));
      return false;
    }
    if (isQueueFull()) {
      int next = nextin + 1;
      if (ndiscard % 100 == 0) {
        prt(tag + " discarding - queue is full " + host + "/" + port + " #discard=" + ndiscard);
      }
      ndiscard++;
      return false;
    }
    try {
      System.arraycopy(buf, off, (byte[]) bufs.get(nextin), 0, len);
    } catch (ArrayIndexOutOfBoundsException e) {// This is to look for a bug!
      prt(tag + host + "/" + port + " OOB nextin=" + nextin
              + " len=" + len + " " + ((byte[]) bufs.get(nextin)).length + " "
              + " buf=" + Arrays.toString(buf) + " " + Arrays.toString(((byte[]) bufs.get(nextin))));
      prt(tag + " arraycopy OOB exception e=" + e.getMessage());
      e.printStackTrace();
      Util.exit(0);
    }
    lengths[nextin] = len;
    nextin++;
    if (nextin >= bufsize) {
      nextin = 0;
    }
    used = nextin - nextout;
    if (used < 0) {
      used += bufsize;
    }
    if (used > maxused) {
      maxused = used;
    }
    return true;
  }

  /**
   * write packets from queue
   */
  @Override
  public void run() {
    boolean connected;
    out = null;
    int err = 0;
    byte[] okays = new byte[3];

    // In UDP case data is sent by the send() methods, we do nothing until exit
    // Create a timeout thread to terminate this guy if it quits getting data
    while (!terminate) {
      connected = false;
      // This loop establishes a socket
      while (!terminate) {
        try {
          prta(tag + " Create new socket to " + host + "/" + port + " dbg=" + dbg);
          ss = new Socket(host, port);
          tag = tag.substring(0, tag.indexOf("[") + 1) + ss.getLocalPort() + "]:";
          prta(tag + " Created new socket to " + host + "/" + port + " local=" + ss.getLocalPort());
          if (terminate) {
            break;
          }
          out = ss.getOutputStream();
          in = ss.getInputStream();
          connected = true;
          break;
        } catch (UnknownHostException e) {
          prt(tag + " Unknown host for socket=" + host + "/" + port + " " + par);
          if (terminate) {
            break;
          }
          SendEvent.edgeSMEEvent("MsgClient", "Message client could not connect to " + host + "/" + port + " e=" + e, "DBMessageQueuedClient");
          try {
            sleep(300000);
          } catch (InterruptedException expected) {
          }
        } catch (IOException e) {
          if (terminate) {
            break;
          }
          Util.SocketIOErrorPrint(e, tag + " opening socket to client.  Wait 30 and try again " + host + "/" + port + " " + par);
          try {
            sleep(30000);
          } catch (InterruptedException expected) {
          }
          SendEvent.edgeSMEEvent("MsgClient", "Message client could not connect to " + host + "/" + port + " e=" + e, "DBMessageQueuedClient");
        }
      }

      // WRite out data to the TcpHoldings server
      lastStatus = System.currentTimeMillis();
      int nblks = 0;
      int nused;
      while (!terminate) {
        try {
          // read until the full length is in 
          int l = 0;
          while (nextin != nextout) {
            out.write((byte[]) bufs.get(nextout), 0, lengths[nextout]);
            if (dbg || totalrecs % 100 == 1) {
              prta(tag + " " + totalrecs + " " + new String(bufs.get(nextout), 0, lengths[nextout] - 1));
            }
            int loop = 0;
            //try {sleep(30);} catch(InterruptedException e) {}
            while (loop++ < 5000 && in.available() <= 0) {
              try {
                sleep(1);
              } catch (InterruptedException expected) {
              } // 5 second time out
            }
            if (in.available() < 3) {
              prta(tag + " Ack readback timed out.  close loop and try again loop=" + loop + " avail=" + in.available() + " closed=" + ss.isClosed());
              try {
                ss.close();
              } catch (IOException expected) {
              }
              l = -1;    // break out of this loop
              break;
            } else {
              in.read(okays, 0, 3);
              if (okays[0] == 'O' && okays[1] == 'K' && okays[2] == '\n') {
                if (dbg) {
                  prta(tag + " Ack received loop=" + loop);
                }
              } else {
                prta(tag + " Ack is bad.  break connection loop=" + loop);
                try {
                  ss.close();
                } catch (IOException expected) {
                }
                l = -1;
                break;
              }
            }
            nblks++;
            totalrecs++;
            nextout++;
            if (nextout >= bufsize) {
              nextout = 0;
            }
            // put the block in the queue
            if (terminate) {
              break;
            }
          }
          if (l == -1) {
            break;
          }
          if (System.currentTimeMillis() - lastStatus > 300000) {
            nused = nextin - nextout;
            if (nused < 0) {
              nused += bufsize;
            }
            prta(tag + " via TCP nblks=" + nblks + " nxtin=" + nextin + " nxtout=" + nextout
                    + " used=" + nused + " discards=" + ndiscard + " maxused=" + maxused + " used=" + used);
            maxused = 0;
            lastStatus = System.currentTimeMillis();
            nblks = 0;
          }
          try {
            sleep(500);
          } catch (InterruptedException expected) {
          }
        } catch (IOException e) {
          if (e.getMessage().contains("Operation interrupted")) {
            prt(tag + " ** has gotten interrupt, shutdown socket:" + e.getMessage());
          } else {
            Util.SocketIOErrorPrint(e, Util.asctime() + " DBQC: Writing data to " + host + "/" + port);
          }
          if (!ss.isClosed()) {
            try {
              ss.close();
            } catch (IOException expected) {
            }
          }
        }
        //prt(tag+"timer");
        if (Util.isShuttingDown()) {
          break;
        }
        if (ss.isClosed()) {
          break;       // if its closed, open it again
        }
      }             // end of while(!terminate) on writing data
      try {
        sleep(1000L);
      } catch (InterruptedException expected) {
      }
    }               // Outside loop that opens a socket 
    prt(tag + " ** DBMessageQueuedClient terminated ");
    if (!ss.isClosed()) {       // close our socket with predjudice (no mercy!)
      try {
        ss.close();
      } catch (IOException expected) {
      }
    }
    prt(tag + "  ** exiting");
  }

  private final class ShutdownDBMessage extends Thread {

    public ShutdownDBMessage() {

    }

    /**
     * this is called by the Runtime.shutdown during the shutdown sequence cause all cleanup actions
     * to occur
     */
    @Override
    public void run() {
      int nloop = 0;
      prta(tag + " Shutdown started nloop=" + nloop);
      while (!isQueueEmpty()) {
        try {
          sleep(100);
        } catch (InterruptedException expected) {
        }
        if (nloop++ > 200) {
          prta(tag + " Shutdown - did not clear queue in 20 seconds " + nextin + " " + nextout);
        }
      }
      terminate();
      prta(tag + "Shutdown Done. CLient c");
    }
  }

}
