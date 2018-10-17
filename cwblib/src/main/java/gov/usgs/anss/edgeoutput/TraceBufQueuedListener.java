/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.edgeoutput;

import gov.usgs.alarm.SendEvent;
import gov.usgs.anss.edge.EdgeProperties;
import gov.usgs.anss.edge.IndexFile;
import gov.usgs.anss.edgemom.EdgeChannelServer;
import gov.usgs.anss.edgemom.Hydra;
import gov.usgs.anss.edgethread.EdgeThread;
import gov.usgs.anss.ew.EWMessage;
import gov.usgs.anss.util.Util;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.TreeMap;

/**
 * This class listens to a UDP port and broadcast address for TraceBufs queues them into a raw byte
 * queue established at startup. This is a subthread to the TraceBufListener class which is the
 * EdgeThread. That class dequeues the data from this one and processes it into the Edgemom
 * environment.
 *
 * The Solaris Java allowed connection directly to the broadcast address and ensured data came from
 * the right subnet. Linux would not allow this. So we connect to the port wide open from all
 * interfaces and subnet, and check each packet to make sure it comes from the right subnet or
 * discard it. May 2008. DCK
 *
 * <PRE>
 * These parameters actually come from the TraceBufListener class.  They are arguments to
 * the constructor
 * -host ip.add.ress Use this broadcast address to listen def="" all ports (edgewire)
 * -port nnnn        Listen for traffic on port nnnn def=40010 (edgewire)
 * -dbg              Print details of each packet received.
 * Note : a log file will be created in ~/log/tbNNNNN.log?
 * </PRE>
 *
 * @author davidketchum
 */
public final class TraceBufQueuedListener extends Thread {

  private final int MAX_LENGTH;
  private final String localIP;
  private final int port;
  private final byte[] buf;
  private byte[] type;

  // These are eneeded if TCP/IP is used
  private DatagramSocket ss;          // The datagram socket
  private final ArrayList<byte[]> bufs;
  private final int[] lengths;
  private final int bufsize;
  private int nextin;
  private int nextout;
  private int ndiscard;
  private long totalrecs;
  private long npackets;
  private int nontracebuf;
  private int maxused;

  // status and debug
  private long lastStatus;
  private boolean terminate;
  private boolean dbg;
  private final EdgeThread par;
  private final StringBuilder tmpsb = new StringBuilder(100);

  /**
   * return number of packets received that were not tracebufs
   *
   * @return The number of non-tracebufs received
   */
  public int getNontracebuf() {
    return nontracebuf;
  }

  /**
   * return number of packets received that were not tracebufs
   *
   * @return The number of non-tracebufs received
   */
  public long getNpackets() {
    return npackets;
  }

  /**
   * Creates a new instance of TraceBufQueuedClient
   *
   * @param locip The IP address (broadcast address) to listen on
   * @param pt The port number to listen for UDP packets
   * @param maxQueue Number of messages to reserve space for in queue
   * @param maxLength Maximum record length for these messages
   * @param parent The parent EdgeThread to use for logging.
   * @throws UnknownHostException if the locip does not make sense
   */
  public TraceBufQueuedListener(String locip, int pt, int maxQueue, int maxLength, EdgeThread parent) throws UnknownHostException {
    par = parent;
    bufsize = maxQueue;
    port = pt;
    localIP = locip;
    MAX_LENGTH = maxLength;
    buf = new byte[MAX_LENGTH];
    bufs = new ArrayList<>(bufsize);
    nextin = 0;
    terminate = false;
    nextout = 0;
    lengths = new int[bufsize];
    for (int i = 0; i < bufsize; i++) {
      bufs.add(i, new byte[MAX_LENGTH]);
    }
    gov.usgs.anss.util.Util.prta("new ThreadOutput " + getName() + " " + getClass().getSimpleName() + " " + locip + "/" + pt);
    start();

  }

  public StringBuilder getStatusString() {
    return toStringBuilder(null);
  }

  @Override
  public String toString() {
    return toStringBuilder(null).toString();
  }

  public StringBuilder toStringBuilder(StringBuilder tmp) {
    StringBuilder sb = tmp;
    if (sb == null) {
      sb = Util.clear(tmpsb);
    }
    synchronized (sb) {
      sb.append("pt=").append(port).append(" nblks=").append(totalrecs).append(" discards=").append(ndiscard).
              append(" in=").append(nextin).append(" out=").append(nextout).append(" qsize=").append(bufsize).
              append(" maxused=").append(maxused).append(" #nontrace=").append(nontracebuf);
    }
    return sb;
  }

  public void terminate() {
    par.prta("TBQL: terminate set!");
    terminate = true;
    ss.close();
  }

  public void setDebug(boolean t) {
    dbg = t;
  }

  public boolean isQueueFull() {
    int next = nextin + 1;
    if (next >= bufsize) {
      next = 0;
    }
    return next == nextout;
  }

  /**
   * Get the dequeing pointer
   *
   * @return The pointer to the next buffer to be sent out (but not processed)
   */
  public synchronized int getNextout() {
    return nextout;
  }

  /**
   *
   * @param buf get a buffer from the queue
   * @return The number of bytes in the buffer
   */
  public synchronized int dequeue(byte[] buf) {
    if (nextin == nextout) {
      return -1;
    }
    try {
      System.arraycopy(bufs.get(nextout), 0, buf, 0, lengths[nextout]);
      int ret = lengths[nextout];
      nextout++;
      if (nextout >= bufsize) {
        nextout = 0;
      }
      return ret;
    } catch (ArrayIndexOutOfBoundsException e) {// This is to look for a bug!
      par.prta(Util.clear(tmpsb).append("TBQL: OOB nextout=").append(nextout).
              append(" len=").append(buf.length).append(" ").append(bufs.get(nextin).length));
      par.prta(Util.clear(tmpsb).append("TBQL: arraycopy OOB exception e=").append(e.getMessage()));
      e.printStackTrace();
      Util.exit(0);
    }
    return -1;    // Its impossible to get here!
  }

  /**
   * write packets from queue
   */
  @Override
  public void run() {
    StringBuilder sb = new StringBuilder(1000);
    InetAddress inet = null;
    InetAddress bindinet = null;
    InetSocketAddress bindsa = null;
    //int err=0;
    DatagramPacket d = new DatagramPacket(new byte[TraceBuf.TRACE_LENGTH], TraceBuf.TRACE_LENGTH);
    StringBuilder runsb = new StringBuilder(100);
    byte[] okays = new byte[3];
    byte[] subnet = null;
    boolean linux = Util.getOS().equals("Linux");
    try {
      sleep(500);
    } catch (InterruptedException e) {
    }

    // In UDP case data is sent by the send() methods, we do nothing until exit
    // Solaris allows binding the Broadcast Address directly and Linux does not, if
    // this is Linux always open with port only (danger is other interface could send to
    // this port and not just the Tracewire interface).
    while (ss == null) {
      try {
        par.prta(Util.clear(runsb).append("TBQL: Listen for UDP packets on ").append(localIP).append("/").append(port).append(" dbg=").append(dbg));
        if (!localIP.equals("")) {
          inet = InetAddress.getByName(localIP);
          subnet = inet.getAddress();
        }
        if (!localIP.equals("")) {
          bindinet = InetAddress.getByName(localIP);
          bindsa = new InetSocketAddress(bindinet, port);
        }
        if (localIP.equals("") || linux)  {
          /*ss = new DatagramSocket();
          ss.setReuseAddress(true);
          inet = InetAddress.getByName("0.0.0.0");
          InetSocketAddress sa = new InetSocketAddress(inet, port);
          par.prt("TBQL: try to bind "+sa+" on ss="+ss);
          ss.bind(sa);*/
          if(bindinet != null) {
            par.prta("TBQL: bind to "+bindinet+" "+port+" bindsa="+bindsa);
            ss = new DatagramSocket(null);
            ss.bind(bindsa);
          }
          else {
            ss = new DatagramSocket(port);      // listen for packets on this port    

          }
        } else {
          par.prta(Util.clear(runsb).append("TBQL: Listen for UDP packets on inet2 ").append(inet).append("/").append(port));
          //ss = new DatagramSocket(port, inet);      // listen for packets on this port  
          ss = new DatagramSocket(port, bindinet);      // listen for packets on this port  
          if(bindsa != null) {
            par.prta("TBQL: bind2 to "+bindsa);
            ss.bind(bindsa);
          }
          //ss.setBroadcast(true);       // enable sending of broadcast packets from this socket
        }
        par.prta(Util.clear(runsb).append("TBQL: Socket is at ").append(ss.getLocalSocketAddress()).
                append(" bind=").append(ss.isBound()).append(" linux=").append(linux).append(" dbg=").append(dbg));
        par.prt(Util.clear(runsb).append("TBQL: Rcv buf size def=").append(ss.getReceiveBufferSize()));
        ss.setReceiveBufferSize(250000);
        par.prt(Util.clear(runsb).append("TBQL: Rcv buf size def=").append(ss.getReceiveBufferSize()));
      } catch (IOException e) {
        par.prta(Util.clear(runsb).append("TBQL: could not set up datagram socket e=").append(e).append(" msg=").append(e.getMessage()));
        e.printStackTrace(par.getPrintStream());
        try {
          sleep(30000);
        } catch (InterruptedException e2) {
        }
      }
    }

    int nused;
    //int frag=0;
    // This loop establishes a socket
    while (!terminate) {
      try {
        try {
          ss.receive(d);          // if this is a Linux system, insure data is from the right subnet
          if (terminate) {
            break;
          }
          if (linux && subnet != null) {
            byte[] from = d.getAddress().getAddress();
            if (!(subnet[0] == from[0] && subnet[1] == from[1] && subnet[2] == from[2])) {
              par.prta(Util.clear(runsb).append("TBQL: Got a UDP not from the right broadcast net! ").append(inet).append(" ").append(d.getAddress()));
              continue;
            }
            //else par.prta("Pack from right subnet "+inet+" "+d.getAddress());
          }
          byte[] data = d.getData();
          if (dbg) {
            par.prta(Util.clear(runsb).append("TBQL: Got UDP lent=").append(d.getLength()).append(" ty=").append(data[1]).
                    append(" In=").append(data[0]).append("md=").append(data[2]).append(" frg=").append(data[3]).
                    append(" sq=").append(data[4]).append(" nxt=").append(nextin).append(" lst=").append(data[5]).
                    append(d.getAddress()).append("/").append(d.getPort()).append(d.getSocketAddress().toString()));
          }
          /*if(sb.length() > 0) sb.delete(0,sb.length());
          for(int i=0; i<d.getLength(); i++) {
            sb.append(Util.leftPad(""+data[i+6],8));
            if(i % 10 == 9) sb.append("\n");
          }
          par.prt("DATA="+sb.toString());*/
          if (data[1] != 19 && data[1] != 20) {
            par.prta(Util.clear(runsb).append("    ****** TBQL: Non-tracebuf NSN found l=").append(d.getLength()).
                    append(" ").append(d.getAddress()).append("/").append(d.getPort()).
                    append(" inst=").append(data[0]).append(" type=").append(data[1]).
                    append(" mod=").append(data[2]).append(" Frg=").append(data[3]).
                    append(" sq=").append(data[4]).append(" lst=").append(data[5]));
            nontracebuf++;
            //continue;
          }
          if ((nextin + 1) % bufsize == nextout) {
            ndiscard++;
            if (ndiscard % 100 == 1) {
              par.prta(Util.clear(runsb).append("  **** TBQL: Have to discard a tracebuf nextout=").append(nextout).
                      append(" nextin=").append(nextin).append(" #discard=").append(ndiscard));
              Util.prta(Util.clear(runsb).append("  **** TBQL: Have to discard a tracebuf nextout=").append(nextout).
                      append(" nextin=").append(nextin).append(" #discard=").append(ndiscard));
            }
            if (ndiscard % 10000 == 1) {
              SendEvent.edgeSMEEvent("TBDiscard",
                      "TraceBuf has discarded " + ndiscard + " pkt next=" + nextout, this);
            }
            continue;
          }
          lengths[nextin] = d.getLength();
          System.arraycopy(d.getData(), 0, bufs.get(nextin), 0, d.getLength());
          nextin = (++nextin) % bufsize;

          nused = nextin - nextout;
          if (nused < 0) {
            nused += bufsize;
          }
          if (nused > maxused) {
            maxused = nused;
          }
          totalrecs++;
          npackets++;
          if (totalrecs % 1000 == 0) {
            if (System.currentTimeMillis() - lastStatus > 300000) {
              par.prta(this.getStatusString());
              lastStatus = System.currentTimeMillis();
              totalrecs = 0;
            }
          }

        } catch (IOException e) {
          if (e.toString().contains("Socket close")) {
            par.prta(Util.clear(runsb).append("TBLQ: socket closed.  Probably shutdown term=").append(terminate));
          } else {
            Util.IOErrorPrint(e, "TBQL: *** IOError getting UDP " + port + " " + e.getMessage());
          }
        }
      } catch (RuntimeException e) {
        par.prta(Util.clear(runsb).append("TBQL: RuntimeException in ").append(this.getClass().getName()).
                append(" e=").append(e.getMessage()));
        if (par.getPrintStream() != null) {
          e.printStackTrace(par.getPrintStream());
        } else {
          e.printStackTrace();
        }
        if (e.getMessage() != null) {
          if (e.getMessage().contains("OutOfMemory")) {
            SendEvent.edgeEvent("OutOfMemory", "Out of Memory in " + Util.getProcess() + "/"
                    + this.getClass().getName() + " on " + IndexFile.getNode(),
                    "TraceBufQueuedListener");
            throw e;
          }
          SendEvent.debugEvent("RuntimeExc", "Unexpected runtime exception e=" + e + " "
                  + (e.getMessage() == null ? "" : e.getMessage()), this);
        }
      }
    }
    // Outside loop that opens a socket 
    par.prta("TBQL: ** TraceBufQueuedClient terminated ");
    if (!ss.isClosed()) {
      ss.close();
    }
    par.prta("TBQL:  ** exiting");
  }

  public static void main(String[] args) {
    Util.setModeGMT();
    Util.setProcess("TraceBufQueuedListener");
    EdgeProperties.init();
    Util.loadProperties("~vdl/edge.prop");
    long lastChanStatus = System.currentTimeMillis();
    long lastStatus = System.currentTimeMillis();
    long lastrecs = 0;
    StringBuilder sb = new StringBuilder(1000);
    Util.setNoInteractive(true);
    boolean dbg = false;
    String host = "";
    String bindhost = "";
    int port = 40010;
    DecimalFormat df1 = new DecimalFormat("0.0");

    TreeMap<String, Chan> chans = new TreeMap<>();
    for (int i = 0; i < args.length; i++) {
      switch (args[i]) {
        case "-dbg":
          dbg = true;
          break;
        case "-host":
        case "-h":
          host = args[i + 1];
          i++;
          break;
        case "-port":
        case "-p":
          port = Integer.parseInt(args[i + 1]);
          i++;
          break;
        case "-?":
          Util.prt("-host ip.add.ress Use this broadcast address to listen def=all interfaces ");
          Util.prt("-port nnnn        Listen for traffic on port nnnn def=40010 (edgewire)");
          Util.prt("-dbg              Print details of each packet received.");
          Util.prt("Note : a log file will be created in ~/log/tbNNNNN.log?");
          Util.prt("Default is " + host + "/" + port);
          System.exit(0);
      }
    }
    EdgeChannelServer echn = new EdgeChannelServer("-empty >>tb" + port, "ECS");   // Setup so main test works
    Util.prta("Start host=" + host + "/" + port + " debug=" + dbg);
    while (!EdgeChannelServer.isValid()) {
      Util.prt("Wait for EdgeChannelServer");
      Util.sleep(1000);
    }
    Hydra hydra = new Hydra("-empty >>tb" + port, "TB1");
    try {
      byte[] b = new byte[4100];
      //TraceBuf tb = new TraceBuf(new byte[TraceBuf.TRACE_LENGTH]);
      hydra.prta("Start listener " + host + "/" + port);
      TraceBufQueuedListener listen = new TraceBufQueuedListener(host, port, 10000, 4000, hydra);
      //TraceBufQueuedListener listen = new TraceBufQueuedListener("localhost",40010, 100, 4000);
      listen.setDebug(dbg);
      StringBuilder list = new StringBuilder(10000);
      int loops = 0;
      for (;;) {
        int nout = listen.getNextout();
        int len = listen.dequeue(b);
        if (len > 0) {
          //tb.setData(b, len);
          TraceBuf tb = Module.process(b, len, port, hydra);
          if (dbg) {
            Util.prta("Frag : nxt=" + nout + " inst=" + b[0] + " ty=" + b[1] + " md=" + b[2] + " frg=" + b[3] + " sq=" + b[4] + " lst=" + b[5]);
          }
          hydra.prta("Frag : nxt=" + nout + " inst=" + b[0] + " ty=" + b[1] + " md=" + b[2] + " frg=" + b[3] + " sq=" + b[4] + " lst=" + b[5]);
          if (tb == null || (tb.getBuf()[1] != EWMessage.TYPE_TRACEBUF && tb.getBuf()[1] != EWMessage.TYPE_TRACEBUF2)) {
            if (dbg && tb != null) {
              Util.prt("Msg=" + tb);
              hydra.prta("Msg=" + tb);
            }
            continue;
          }
          Chan c = chans.get(tb.getSeedNameString());
          if (c == null) {
            c = new Chan(tb.getSeedNameSB(), tb, hydra);
            Util.prt("Create chan=" + c.toString());
            chans.put(tb.getSeedNameString(), c);
          } else {
            c.process(tb);
          }
          double lat = (System.currentTimeMillis() - tb.getNextExpectedTimeInMillis()) / 1000.;
          if (dbg) {
            Util.prta("nxt=" + nout + " l=" + len + " lat=" + (lat > 20. ? "*" : "") + df1.format(lat) + " " + tb.toString());
          }
          hydra.prta("nxt=" + nout + " l=" + len + " lat=" + (lat > 20. ? "*" : "") + df1.format(lat) + " " + tb.toString());
          if (dbg && tb.getBuf()[3] == 0) {   // as long as this is fragment 0
            int[] data = tb.getData();
            int min = 2147000000;
            int max = -2147000000;
            if (data != null) {
              if (sb.length() > 0) {
                sb.delete(0, sb.length());
              }
              for (int i = 0; i < Math.min(Math.min(40, tb.getNsamp()), data.length); i++) {
                sb.append(Util.leftPad("" + data[i], 8)).append(" ");
                if (i % 10 == 9) {
                  sb.append("\n");
                }
                if (data[i] < min) {
                  min = data[i];
                }
                if (data[i] > max) {
                  max = data[i];
                }
              }
              //if(max - min > 6000) 
              if (dbg) {
                Util.prt(tb.getSeedname() + "nxt=" + nout + " min=" + min + " max=" + max + " df=" + (max - min) + "\n" + sb.toString());
              }
            }
          }
          String comp = tb.getSeedNameString().substring(7, 10);
          String add = "*";
          if (comp.substring(0, 2).equals("LH") || comp.substring(0, 2).equals("BH")
                  || comp.substring(0, 2).equals("HH") || comp.substring(0, 2).equals("SH")
                  || comp.substring(0, 2).equals("EH") || comp.substring(0, 2).equals("LL")
                  || comp.substring(0, 2).equals("BL")) {
            add = " ";
          }
          if (list.indexOf(tb.getSeedNameString()) < 0) {
            list.append(tb.getSeedNameString()).append(add);
          }
          loops++;
          if (loops % 200 == 0) {
            /*if(System.currentTimeMillis() - lastStatus > 60000) {
              for(int i=129; i<list.length(); i=i+130) list.replace(i,i+1,"\n");
              par.prta(loops+" #="+(list.length()/13)+" "+tb.toString()+"              "+
                  (list.indexOf("*") >= 0?"True":"")+"\n"+list.toString());
              list.delete(0,list.length());
              lastStatus = System.currentTimeMillis();
            }*/
            if (System.currentTimeMillis() - lastChanStatus > 300000) {
              Iterator<Chan> itr = chans.values().iterator();
              while (itr.hasNext()) {
                Chan ch = itr.next();
                Util.prt(ch.toString());
                ch.clearStatistics();
              }
              Util.prta("# chans=" + chans.size() + " #pkt=" + (listen.getNpackets() - lastrecs) + " " + listen);
              lastChanStatus = System.currentTimeMillis();
              lastrecs = listen.getNpackets();
              Util.prta("Modules: ");
              Util.prt(Module.getStatus());
              hydra.prt(Module.getStatus());

            }
          }
        } else {
          try {
            Thread.sleep(10);
          } catch (InterruptedException e) {
          }
        }
      }
    } catch (UnknownHostException e) {
      Util.prt("Could not open a TraceBufQueued Listener");
    }
  }
}
