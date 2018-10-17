/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.edgemom;

import gov.usgs.anss.edge.IndexBlock;
import gov.usgs.anss.edge.EdgeProperties;
import gov.usgs.alarm.SendEvent;
import gov.usgs.anss.edge.*;
import gov.usgs.anss.edgethread.*;
import gov.usgs.anss.seed.MiniSeed;
import gov.usgs.anss.util.*;
import java.io.EOFException;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import gnu.trove.map.hash.TLongObjectHashMap;
import gnu.trove.iterator.TLongObjectIterator;

/**
 * This class is used to send summaries of data packets received by an EdgeMom
 * to a TcpHoldings process. Each instance sends data of one two character type.
 * For instance if there are multiple Edge nodes sending data to a CWB node via
 * replication, the edge nodes might send holdings of type 'E1','E2', etc. and
 * the CWB node would record its holdings of type 'CW'. In this way a separate
 * recording of real time holdings is made at different stages.
 * <br>
 * UDP Mode - creates a UDP end point and implements methods for sending data
 * holdings to a UdpHolding server. On creation it sets up the end point and
 * stores the target IP and port. Each call to queue() causes a UDP packet with
 * a summary of to be sent via UDP to the target. Since it is UDP is is possible
 * that the packet will not be received or be processed by sender. Normally,
 * this is not much of a problem as holdings are remade from the data files by
 * updateHoldings.bash. This is the most common method of using this class.
 * <br>
 * TCP Mode - This module can be run in TCP mode. In this case a socket is
 * created to the TcpHoldings process and the data sent down the socket. Since
 * this might block, space is made to buffer the data (-q gives its size). If
 * the queue is full, then new calls to queue() will just discard the data.
 *
 * <PRE>
 * switch         Value               Description
 * ( -h        ip.adr      This is the host to receive the Holdings (def=gacqdb.cr.usgs.gov)
 * -p        port        The destination port number for the holdings (def=7996)
 * -t        NN          NN is the type of holding recorded.  There is no default type.
 * -quiet                If set, minimize output to log file
 * -dbg                  If set, crank up the output to the log
 *
 *   --- TCP mode switches ---
 * -tcp                  If present, use TCP/IP to send holdings instead of UDP (no normally used)
 * -q                    If in TCP mode, how many queue entries to buffer before blocking (def=20000)
 * -noeto                If present, do not timeout.  This is only effective if the rare -tcp mode is used.
 * </PRE>
 *
 * @author davidketchum
 */
public final class HoldingSender extends EdgeThread {

  private static final int MAX_LENGTH = 26;
  private static HoldingSender aHoldingSender;
  private static String lastms;
  private static final TLongObjectHashMap<Run> runs = new TLongObjectHashMap<Run>();
  //private static final TreeMap<String, Run> runs = new TreeMap<String, Run>();
  private DatagramSocket s;  // A Datagram socket for sending the UDP packets
  private String host;
  private int port;
  private byte[] buf;
  private byte[] type;
  private ByteBuffer bb;
  private DatagramPacket pkt;
  private boolean useTCP;
  // These are eneeded if TCP/IP is used
  private Socket ss;          // The TCP/IP socket (if used) 
  private ArrayList<byte[]> bufs;
  private int bufsize;
  private int nextin;
  private int nextout;
  private int ndiscard;
  private long totalrecs;
  private boolean shutdown;     // set true by our shutdown routine!
  private boolean inClose;      // Stop multiple calls to close()
  private boolean quiet;
  private boolean noeto;
  private long nblksin;
  private long nrunsout;
  // status and debug
  private long lastStatus;
  private boolean dbg;
  private int state, sstate, qstate, qqstate;
  private ShutdownHoldingSender shut;
  private StringBuilder tmpsb = new StringBuilder(100);

  public static HoldingSender getHoldingSender() {
    return aHoldingSender;
  }

  public TLongObjectHashMap<Run> getRuns() {
    return runs;
  }

  //public TreeMap<String, Run> getRuns() {return runs;}
  /**
   * Creates a new instance of HoldingSender
   *
   * @param argline EdgeThread arguments
   * @param tag The EdgeThead tag
   * @throws UnknownHostException If the host cannot be found
   */
  public HoldingSender(String argline, String tag) throws UnknownHostException {
    super(argline, tag);
    String[] args = argline.split("\\s");
    host = "gacqdb.cr.usgs.gov";
    port = 7997;
    bufsize = 20000;
    String typ = "";
    for (int i = 0; i < args.length; i++) {
      //prt(i+" arg="+args[i]);
      if (args[i].equals("-h")) {
        host = args[i + 1];
        i++;
      } else if (args[i].equals("-p")) {
        port = Integer.parseInt(args[i + 1]);
        i++;
      } else if (args[i].equals("-dbg")) {
        dbg = true;
      } else if (args[i].equals("-tcp")) {
        useTCP = true;
      } else if (args[i].equals("-t")) {
        typ = args[i + 1];
        type = (args[i + 1] + "  ").substring(0, 2).getBytes();
        i++;
      } else if (args[i].equals("-q")) {
        bufsize = Integer.parseInt(args[i + 1]);
        i++;
      } else if (args[i].equals("-quiet")) {
        quiet = true;
      } else if (args[i].equals("-noeto")) {
        noeto = true;
      } else if (args[i].substring(0, 1).equals(">")) {
        break;
      } else {
        prt("HS:  unknown switch=" + args[i] + " ln=" + argline);
      }
    }
    buf = new byte[MAX_LENGTH];
    IndexBlock.registerHoldingSender((HoldingSender) this);
    aHoldingSender = (HoldingSender) this;

    // Register our shutdown thread with the Runtime system.
    shut = new ShutdownHoldingSender();
    Runtime.getRuntime().addShutdownHook(shut);
    Run.par = (HoldingSender) this;
    bb = ByteBuffer.wrap(buf);
    if (useTCP) {
      if (!quiet) {
        prta(Util.clear(tmpsb).append(tag).append(" HS: new Holding sender with TCP for bufsize=").
                append(bufsize).append(" to ").append(host).append("/").append(port));
      }
      bufs = new ArrayList<byte[]>(bufsize);
      nextin = 0;
      nextout = 0;
      for (int i = 0; i < bufsize; i++) {
        bufs.add(i, new byte[MAX_LENGTH]);
      }
      start();
    } else {
      InetAddress address = InetAddress.getByName(host);
      pkt = new DatagramPacket(buf, MAX_LENGTH, address, port);
      if (!quiet) {
        prta(Util.clear(tmpsb).append(tag).append("HoldingSender set up for ").
                append(host).append(":").append(port).append("/").append(typ));
      }
      try {
        s = new DatagramSocket();
      } catch (SocketException e) {
        prta(Util.clear(tmpsb).append(" *** could not create a HoldingSender UDP socket end").append(e.getMessage()));
      }
      start();
    }
  }

  /**
   * send a holdings as a GregorianCalendar start time and seconds of duration
   *
   * @param seedname The 12 character seedname
   * @param start A gregoriancalendar set to the start time
   * @param duration The time in seconds of this span or holding
   * @return true if holding was posted.
   * @throws IOException if one is thrown trying to write to the server
   */
  public boolean send(String seedname, long start, double duration) throws IOException {
    try {
      //if(seedname.equals("NTBOU"))
      //  prt("HS: got "+Util.ascdate(start)+" "+Util.asctime2(start)+" dur="+duration);
      Run run;
      sstate = 1;
      synchronized (runs) {
        state = 2;
        run = runs.get(Util.getHashFromSeedname(seedname));
        nblksin++;
        if (run == null) {
          run = new Run(seedname, start, duration);
          runs.put(Util.getHashFromSeedname(seedname), run);
          return true;
        }
      }
      sstate = 3;
      if (run.add(seedname, start, duration)) {    // did we add 
        if (run.getLength() < 2000000.) {    // is this too long for a holdings packet
          if (System.currentTimeMillis() - run.getLastUpdate() > 180000) {
            return queue(run);
          }
          sstate = 5;
          return true;
        } else {
          // Its too long, send it and then start a new run for the channel
          prt(Util.clear(tmpsb).append(tag).append("HS: holding has reach length limit run=").append(run));
        }
      }
      sstate = 4;
      boolean wasSent = queue(run);
      run.reset(seedname, start, duration);
      return wasSent;
    } catch (RuntimeException e) {
      prt(Util.clear(tmpsb).append(tag).append("Run.send() got Runtime exception "));
      e.printStackTrace();
    }
    return false;
  }

  /**
   * send holdings held in a TimeSeriesBlock
   *
   * @param ms The TimeSeriesBlock
   * @return true if block was posted.
   * @throws IOException if one is thrown writing to the server
   */
  public boolean send(TimeSeriesBlock ms) throws IOException {
    sstate = 20;
    return send(ms.getSeedNameString(), ms.getTimeInMillis(), ms.getNsamp() / ms.getRate());
  }
  private final GregorianCalendar start = new GregorianCalendar();
  private final int[] ymd = new int[3];
  private final int[] time = new int[4];

  /**
   * send holdings from miniseed in uncracked form
   *
   * @param bf The miniseed data packet
   * @throws IOException if one is thrown writing to the server
   * @return true if handled o.k.
   */

  public boolean send(byte[] bf) throws IOException {

    try {
      state = 10;
      synchronized (start) {
        sstate = 11;
        SeedUtil.fromJulian(MiniSeed.crackJulian(bf), ymd);
        MiniSeed.crackTime(bf, time);
        //GregorianCalendar start = new GregorianCalendar(ymd[0], ymd[1]-1, ymd[2]);
        start.set(ymd[0], ymd[1] - 1, ymd[2]);
        start.setTimeInMillis(start.getTimeInMillis() / 86400000L * 86400000L); // remove any fractional part
        start.setTimeInMillis(start.getTimeInMillis() + ((long) time[0] * 3600000 + time[1] * 60000 + time[2] * 1000 + time[3] / 10));
        sstate = 12;
        return send(MiniSeed.crackSeedname(bf), start.getTimeInMillis(), MiniSeed.crackNsamp(bf) / MiniSeed.crackRate(bf));
      }
    } catch (RuntimeException e) {
      sstate = 13;
      if (e.getMessage().contains("ymd_from")) {
        prta(Util.clear(tmpsb).append(tag).append("Weird year :").append(e.getMessage()));
      }
      return true;
    } catch (IllegalSeednameException e) {
      sstate = 14;
      return true;
    }
  }
  private final GregorianCalendar gtmp = new GregorianCalendar();

  /**
   * Queue up a run
   *
   * @param run The run to queue up to send to holdings server
   * @return true If there was room in the queue
   * @throws IOException if one is thrown writing to the server
   */
  public boolean queue(Run run) throws IOException {
    //if(run.getSeedname().indexOf("GTBDFB BH1") >= 0) prta("Queue run "+run);
    run.setLastUpdate();
    nrunsout++;
    qstate = 40;
    synchronized (gtmp) {    // only one queue can use gtmp at a time
      qstate = 41;
      gtmp.setTimeInMillis(run.getStart());
      FUtil.intToArray(gtmp.get(Calendar.YEAR) * 10000 + (gtmp.get(Calendar.MONTH) + 1) * 100
              + gtmp.get(Calendar.DAY_OF_MONTH), buf, 0);
      FUtil.intToArray((int) (gtmp.getTimeInMillis() % 86400000L), buf, 4);
      int lenms = (int) (run.getLength() * 1000. + 0.5);
      FUtil.intToArray(lenms, buf, 8);
      FUtil.stringToArray(run.getSeedname(), buf, 12, 12);
      buf[24] = type[0];
      buf[25] = type[1];
      if (useTCP) {
        qstate = 42;
        return queue(buf);
      } else {
        qstate = 43;
        pkt.setData(buf);
        s.send(pkt);
        return true;
      }
    }
  }

  /*
 * HoldingOutputQueue.java
 *
 * Created on August 5, 2006, 5:20 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
   */
  @Override
  public StringBuilder getStatusString() {
    Util.clear(statussb);
    synchronized (statussb) {
      statussb.append(host).append("/").append(port).append(" nblks=").append(totalrecs).
              append(" discards=").append(ndiscard).append(" in=").append(nextin).append(" out=").
              append(nextout).append(" #in=").append(nblksin).append(" #out=").append(nrunsout).
              append(" qsize=").append(bufsize).append(" #runs=").append(runs.size()).
              append(" tcp=").append(useTCP).append(" state=").
              append(state).append("/").append(sstate).append("/").append(qstate).append("/").append(qqstate);
    }
    return statussb;
  }

  @Override
  public String toString() {
    return getStatusString().toString();
  }

  public StringBuilder toStringBuilder(StringBuilder tmp) {
    StringBuilder sb = tmp;
    if (sb == null) {
      sb = Util.clear(tmpsb);
    }
    synchronized (sb) {
      sb.append(getStatusString());
    }
    return sb;
  }
  /**
   * return the monitor string for ICINGA
   *
   * @return A String representing the monitor key value pairs for this
   * EdgeThread
   */
  long lastMonRecs;
  long lastMonDiscards;

  @Override
  public StringBuilder getMonitorString() {
    if (monitorsb.length() > 0) {
      monitorsb.delete(0, monitorsb.length());
    }
    long nb = totalrecs - lastMonRecs;
    long nd = ndiscard - lastMonDiscards;
    lastMonRecs = totalrecs;
    lastMonDiscards = ndiscard;
    return monitorsb.append("HoldingSenderNrecs=").append(nb).append("\nHoldingSenderDiscards=").append(nd).append("\n");
  }

  @Override
  public StringBuilder getConsoleOutput() {
    return consolesb;
  }

  @Override
  public synchronized void terminate() {
    prta(Util.clear(tmpsb).append(tag).append("HS: terminate() called!"));
    terminate = true;
  }

  /**
   * Close really forces all of the holdings into the queue and waits for the
   * queue to be empty or timed out.
   *
   */
  public void close() {
    if (inClose) {
      return;      // Close() has already been called
    }
    inClose = true;
    forceOutHoldings();       // this forces all holdings to queue and waits for queue to be emptied or timed out
    shutdown = true;
    terminate = true;
    prta(Util.clear(tmpsb).append(tag).append("HS: closing slocket nextin=").append(nextin).append(" nextout=").append(nextout));
  }

  public int getNleft() {
    int nleft = nextin - nextout;
    if (nleft < 0) {
      nleft += bufsize;
    }
    return bufsize - nleft;
  }

  private synchronized boolean queue(byte[] buf) {
    qqstate = 30;
    int next = nextin + 1;
    if (next >= bufsize) {
      next = 0;
    }
    if (next == nextout) {
      qqstate = 31;
      if (ndiscard % 1000 == 0) {
        prta(Util.clear(tmpsb).append(tag).append("HS: discarding holding - queue is full next=").
                append(next).append(" #dis=").append(ndiscard + 1));
        SendEvent.edgeSMEEvent("HoldSendDisc", Util.getProcess() + " discarding holdings next=" + next + " #dis=" + ndiscard, this);
      }
      ndiscard++;
      qqstate = 32;
      return false;
    }
    qqstate = 33;
    System.arraycopy(buf, 0, (byte[]) bufs.get(nextin), 0, 26);
    nextin++;
    if (nextin >= bufsize) {
      nextin = 0;
    }
    return true;
  }

  /**
   * write packets from queue
   */
  @Override
  public void run() {
    OutputStream outtcp = null;
    running = true;
    int err = 0;
    StringBuilder runsb = new StringBuilder(50);

    // In UDP case data is sent by the send() methods, we do nothing until exit
    if (!useTCP) {
      while (!terminate) {
        try {
          sleep(1000);
        } catch (InterruptedException expected) {
        }

      }
      s.close();
      running = false;
      return;
    }
    EdgeThreadTimeout eto = null;
    state = 1;
    int lastShutdown = 0;
    // Create a timeout thread to terminate this guy if it quits getting data
    if (!noeto) {
      eto = new EdgeThreadTimeout(getTag() + "HS:", this, 300000, 300000);
    }
    while (!shutdown) {               // We do not terminate exception by shutdown
      // This loop establishes a socket
      while (!shutdown && !terminate) {
        try {
          state = 2;
          if (shutdown) {
            break;
          }
          if (eto != null) {
            eto.resetTimeout();
          }
          if (!quiet) {
            prta(Util.clear(runsb).append(tag).append("HS: Try to create a socket to ").
                    append(host).append("/").append(port).append("nin=").append(nextin).
                    append(" nout=").append(nextout));
          }
          boolean connected = false;
          ss = new Socket(host, port);
          if (!quiet) {
            prta(Util.clear(runsb).append(tag).append("HS: Created new socket to TcpHoldings server at ").
                    append(host).append("/").append(port).append(" dbg=").append(dbg).
                    append(" LocPort=").append(ss.getLocalPort()).append(" nin=").append(nextin).
                    append(" nout=").append(nextout));
          }
          //ss.setSendBufferSize(256000);
          if (shutdown) {
            break;
          }
          outtcp = ss.getOutputStream();
          state = 3;
          connected = true;
          if (eto != null) {
            if (terminate && eto.hasSentInterrupt()) {
              terminate = false;
            }// timer went off getting socket
          }
          break;
        } catch (UnknownHostException e) {
          prta(Util.clear(runsb).append(tag).append("HS: Unknown host for socket=").
                  append(host).append("/").append(port));
          if (eto != null) {
            eto.resetTimeout();
          }
          try {
            sleep(300000);
          } catch (InterruptedException expected) {
          }
        } catch (IOException e) {
          if (shutdown) {
            break;
          }
          if (e.getMessage().contains("Connection refused")) {
            prta(Util.clear(runsb).append(tag).append("HS: ** Connection refused to ").
                    append(host).append("/").append(port).append(" try again in 30"));
          } else {
            Util.IOErrorPrint(e, "HS: *** IOError opening client " + host + "/" + port + " try again in 30 e=" + e.getMessage());
            e.printStackTrace();
          }
          try {
            sleep(30000);
          } catch (InterruptedException expected) {
          }
        }
        if (terminate && (eto != null ? eto.hasSentInterrupt() : false)) {
          terminate = false;
        }
      }
      state = 5;
      if (shutdown) {
        break;
      }
      state = 4;

      // WRite out data to the TcpHoldings server
      if (eto != null) {
        eto.setIntervals(1200000, 3000000);   // Set shorter timeout intervals
      }
      lastStatus = System.currentTimeMillis() - 240000;
      int nblks = 0;
      int nused;
      if (!quiet) {
        prta(Util.clear(runsb).append(tag).append("HS: start infinite loop in=").append(nextin).
                append(" out=").append(nextout).append(" discards=").append(ndiscard).
                append(" nblks=").append(nblks).append(" ").append(ss.getLocalPort()));
      }
      if (outtcp == null) {
        continue;
      }
      while (!(terminate && nextin == nextout)) {
        if (terminate) {
          break;
        }
        try {
          // read until the full length is in 
          //int l=0;
          state = 6;
          while (nextin != nextout) {
            state = 7;

            outtcp.write((byte[]) bufs.get(nextout), 0, 26);
            state = 8;
            if (eto != null) {
              eto.resetTimeout();
            }
            nblks++;
            totalrecs++;
            //if(nblks % 100 == 0) prta("Done block="+nblks);
            nextout++;
            if (nextout >= bufsize) {
              nextout = 0;
            }
            // put the block in the queue
            state = 9;
            if (System.currentTimeMillis() - lastStatus > 300000 || terminate) {
              state = 10;
              nused = nextin - nextout;
              if (nused < 0) {
                nused += bufsize;
              }
              if (System.currentTimeMillis() - lastStatus > 10000) {
                prta(Util.clear(runsb).append(tag).append("HS : via TCP nblks=").append(nblks).
                        append(" nxtin=").append(nextin).append(" nxtout=").append(nextout).
                        append(" used=").append(nused).append(" discards=").append(ndiscard).
                        append("/").append(ss.getLocalPort()).append(" #in=").append(nblksin).
                        append(" #out=").append(nrunsout).append(" #ch=").append(runs.size()));
              }
              lastStatus = System.currentTimeMillis();
              nblks = 0;
              // Check for any runs that are really stale
              try {
                Object[] r;
                state = 11;
                synchronized (runs) {
                  long now = System.currentTimeMillis();
                  TLongObjectIterator<Run> itr = runs.iterator();
                  while (itr.hasNext()) {
                    itr.advance();
                    Run run = itr.value();
                    if ((now - run.getLastUpdate() > 12000000 && run.isModified())) {
                      prta(Util.clear(runsb).append("HS : Q stale ").append(run.toStringBuilder(null)));
                      state = 13;
                      queue(run);
                      state = 14;
                    }
                  }
                }
              } catch (RuntimeException e) {
                prta(Util.clear(runsb).append(tag).append("HS : Got Exception checking for stale runs e=").append(e));
                e.printStackTrace();
              }

            }
            state = 15;
          }   // while nextin != nextout
          if (shutdown && lastShutdown != nextin) {
            prta(Util.clear(runsb).append(tag).append("HS: terminate or shutdown exit loop. term=").
                    append(terminate).append(" shut=").append(shutdown).
                    append(" nextin=").append(nextin).append(" nextout=").append(nextout));
            lastShutdown = nextin;
          }          //try{
          state = 16;
          sleep(2);
          state = 17;
          //} catch(InterruptedException expected) {}
        } catch (InterruptedException e) {
          state = 18;
          if (e.getMessage().contains("Operation interrupted") || e.getMessage().contains("sleep interrupt")) {
            prta(Util.clear(runsb).append(tag).append("HS -ETO: ** has sent interrupt2, shutdown socket:").
                    append(e.getMessage()));
          } else {
            prta(Util.clear(runsb).append(tag).append("Weird interrupted exception e=").append(e));
          }
          if (ss != null && !ss.isClosed()) {
            try {
              ss.close();
            } catch (IOException expected) {
            }
          }

        } catch (InterruptedIOException e) {
          state = 19;
          prta(Util.clear(runsb).append(tag).append("HS: InterruptedIOException - close the socket ").
                  append(eto == null ? "no eto" : eto.getTag()));
          if (ss != null && !ss.isClosed()) {
            try {
              ss.close();
            } catch (IOException expected) {
            }
          }

        } catch (IOException e) {
          state = 20;
          if (e.getMessage() != null) {
            if (e.getMessage().contains("Operation interrupted")) {
              prta(Util.clear(runsb).append(tag).append("HS -ETO: ** has sent interrupt, shutdown socket:"));
            } else {
              Util.SocketIOErrorPrint(e, "HS: Writing data to TcpHoldings", getPrintStream());
            }
          } else {
            Util.SocketIOErrorPrint(e, tag + " HS: Writing data to TcpHoldings", getPrintStream());
          }
          if (ss != null && !ss.isClosed()) {
            try {
              ss.close();
            } catch (IOException expected) {
            }
          }
        }
        //prta("HS:timer");
        if (ss.isClosed()) {
          break;       // if its closed, open it again
        }
      }             // end of while(!terminate) on writing data

      // IF the eto went off and set terminate, do not terminate but force the socket closed
      // and go back to reopen loop.  This insures that full exits only occur when the program
      // is shutting down.
      try {
        sleep(10000L);
      } catch (InterruptedException expected) {
      }
      state = 21;
      if (noeto && terminate) {
        break;
      } else if (eto != null) {
        if ((eto.hasSentInterrupt() && terminate)) {
          state = 22;
          terminate = false;
          prta(Util.clear(runsb).append(tag).append("HS: HoldingSender no output ETO did interrupt- reestablish in=")
                  .append(nextin).append(" out=").append(nextout).append(" discards=").append(ndiscard).
                  append(" nblks=").append(nblks).append(" ").append(ss.getLocalPort()));
          eto.resetTimeout();      // Reset ETO, should detect in destroy loop and restart the ETO
          if (!ss.isClosed()) {
            state = 23;
            try {
              ss.close();
              sleep(1000);
            } catch (IOException | InterruptedException expected) {
            }
          }
        }
      } else {
        state = 24;
        prta(Util.clear(runsb).append(tag).append("HS: Weird: got out of send loop but terminate is not set! terminate=").
                append(terminate).append(" eto.sent=").append(eto == null ? "no eto" : eto.hasSentInterrupt()));
        terminate = false;
      }

    }               // Outside loop that opens a socket
    state = 25;
    prta(Util.clear(runsb).append(tag).append("HS: ** HoldingSender shutdown TO.int=").
            append(eto == null ? "null" : eto.hasSentInterrupt()).append(" shutdown=").append(shutdown));
    if (eto != null) {
      eto.shutdown();           // shutdown the timeout thread
    }
    if (!ss.isClosed()) {       // close our socket with predjudice (no mercy!)
      try {
        ss.close();
      } catch (IOException expected) {
      }
    }
    running = false;
    if (!shutdown) {
      Runtime.getRuntime().removeShutdownHook(shut);
    }
    prta("HS:  ** exiting shutdown=" + shutdown);
    state = 30;
  }

  public void forceOutHoldings() {
    try {
      synchronized (runs) {
        prta(Util.clear(tmpsb).append(tag).append("HS: Holdings Forceout start runs.size()=").append(runs.size()));
        TLongObjectIterator<Run> itr = runs.iterator();
        int i = 0;
        while (itr.hasNext()) {
          itr.advance();
          Run r = itr.value();
          int loop = 0;
          while (getNleft() < 100) {
            try {
              sleep(200);
            } catch (InterruptedException expected) {
            }
            prta(Util.clear(tmpsb).append(tag).append("HS: force out runs -> queuing nleft=").append(getNleft()));
            loop++;
            if (loop > 150) {
              prta(Util.clear(tmpsb).append(tag).append("HS: ** forceout runs -> queue stuck - abandon queuing"));
              break;
            }
          }
          if (loop > 150) {
            break;
          }
          queue(r);
          i++;
          itr.remove();
        }
      }
    } catch (IOException e) {
      prta(Util.clear(tmpsb).append(tag).append("HS : ** ForceOutHOldings() IOException queuing all"));
    } catch (RuntimeException e) {
      prta(Util.clear(tmpsb).append(tag).append("HS : ** ForceOutHoldings() Runtime sending all e=").append(e));
    }
    int nloop = 0;
    prta(Util.clear(tmpsb).append(tag).append("HS: holding forceOut start wait for all out nextin=").
            append(nextin).append(" nextout=").append(nextout));
    while (nextin != nextout && nloop < 30) {
      Util.sleep(1000);
      nloop++;
      if (nloop % 10 == 0) {
        prta("HS: * forceOut() nxtin=" + nextin + " nextout=" + nextout
                + " state=" + state + " alive=" + isAlive() + " term=" + terminate + " shut=" + shutdown);
      }
    }
    if (nextin != nextout) {
      prta(Util.clear(tmpsb).append("HS: ForceOutHoldings() *** not all out after 30 seconds in=").
              append(nextin).append(" out=").append(nextout).append(" state=").append(state).
              append(" running=").append(isAlive()));
    } else {
      prta(Util.clear(tmpsb).append(tag).append("HS: ForceOutHoldings successful! nextin=").append(nextin).
              append(" nextout=").append(nextout).append(" state=").append(state).append(" alive=").append(isAlive()));
    }
  }

  private class ShutdownHoldingSender extends Thread {

    int state = 0;

    public ShutdownHoldingSender() {
      gov.usgs.anss.util.Util.prta("new ThreadShutdown " + getName() + " " + getClass().getSimpleName());

    }

    /**
     * this is called by the Runtime.shutdown during the shutdown sequence cause
     * all cleanup actions to occur
     */
    @Override
    public void run() {
      state = 1;
      Util.prta("HS: ShudownHoldingsSender start");
      close();      // This runs forceOutHoldings() to clear things out
      state = 2;
      prta("HS: ShutdownHoldingSender forceOutHoldings() done nextint=" + nextin + " nextout=" + nextout);
      terminate = true;
      prta("HS: Shutdown Done. ");
      state = 3;
    }

    @Override
    public String toString() {
      return "ShutdownHoldingSender state=" + state;
    }
  }

  public static void main(String[] args) {
    byte[] b = new byte[512];
    Util.setModeGMT();
    EdgeProperties.init();
    HoldingSender hs;
    try {
      hs = new HoldingSender("-h localhost -p 7996 -t TT -tcp -q 50000", "HS");
      for (int i = 0; i < args.length; i++) {
        try {
          RawDisk in = new RawDisk(args[i], "r");
          int iblk = 0;
          for (;;) {

            in.readBlock(b, iblk, 512);
            MiniSeed ms = new MiniSeed(b);
            if (iblk % 2 == 0) {
              hs.send(ms);
            } else {
              hs.send(b);
            }
            if (i > 0) {
              Util.sleep(5000);
            }
            iblk++;
          }
        } catch (EOFException e) {
          Util.prta("EOF found for " + args[i]);
        } catch (IllegalSeednameException e) {
          Util.prt("Got IllegalSeedname=" + e.toString());
        } catch (IOException e) {
          Util.IOErrorPrint(e, "Getting data from file or sending to Holdings");
        }
      }
    } catch (UnknownHostException e) {
      Util.prt("Got unknown host exception e=" + e);
      System.exit(0);
    }
    for (;;) {
      Util.sleep(1000);
    }
  }
}
