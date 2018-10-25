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
import java.net.InetSocketAddress;
import java.net.DatagramPacket;
import java.net.NetworkInterface;
import java.net.MulticastSocket;
import java.net.InetAddress;
import java.net.SocketAddress;

/**
 * This class is a generalized Client which listens to a Multicast IP (group)
 * and port and puts the messages in a MulticastQueue supplied at creation.
 * Every packet received is queued.
 *
 *
 * <PRE>
 * switch     args     Description
 * -p        nnnnn     The port to use for this connection, if omitted let system pick an ephemerial port
 * -mip      ip.adr    The Multicast address to listen for or in multicast speak the group.
 * -bind     ip.adr    Bind the receive port to this IP address of the possible multihomed addresses
 * -if       device    Use this interface
 * -dbgmcast           Create more output
 * </PRE>
 *
 * @author davidketchum
 */
public final class MulticastListener extends Thread {
  private byte [] zeros = new byte[512];
  private int MAX_LENGTH = 512;         // Maximum length of received data

  // Configuration of the MulticastSocket or Datagram
  private String groupIP;         // Local IP address (useful if multi-homed!)
  private int port;               // port to listen to
  private final byte[] buf;
  private int rcvBufSize;         // if user wants a bigger rcv buf size for the socket
  private MulticastSocket ss;          // The Multicast datagram socket
  private InetAddress group;          // Inet address of the Multicast Group
  private String eth;
  private String bindIP;          // if set, then this IP address is bound
  private DatagramPacket d;       // The databram packet for receiving each transmission
  private MulticastQueue queue;   // The queue to put each recevied packet.

  // status and debug
  private long bytesout;
  private long bytesin;
  private long lastStatus;
  private long totalrecs;           // total recs processed
  private int maxused;              // High water mark of used packets in the queue.
  private SocketAddress lastSock;   // On dequeue this is set so user who needs it can get it from getSockAddress()
  private boolean terminate;
  private boolean running;
  private String tag;
  // Other
  private EdgeThread par;
  private boolean dbg;
  private StringBuilder runsb = new StringBuilder(100);
  private StringBuilder tmpsb = new StringBuilder(100);   // toStringBuilder and constructor

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
      sb = Util.clear(tmpsb);
    }
    synchronized (sb) {
      sb.append(tag).append("MCL: ").append(" din=").append(bytesin).append(" out=").append(bytesout).append(" #recs=").append(totalrecs);
    }
    return sb;
  }

  public int getMaxLength() {
    return MAX_LENGTH;
  }

  public void setDebug(boolean t) {
    dbg = t;
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

  public void terminate() {
    terminate = true;
    if (!ss.isClosed()) {
      ss.close();
    }
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

  public void sendPacket(DatagramPacket p, int len) throws IOException {
    ss.send(p);
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
   * return number of packets received
   *
   * @return Number of packets returned.
   */
  public long getNpackets() {
    return totalrecs;
  }

  public int getPort() {
    return port;
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
   * @param q The shared MulticastQueue
   * @throws java.io.IOException If the MulticastSocket or DatagramPacket cannot
   * be setup on the configured address and port
   */
  public MulticastListener(String argline, String tag, EdgeThread parent, MulticastQueue q) throws IOException {
    par = parent;
    port = 8050;
    rcvBufSize = 0;
    queue = q;
    groupIP = "239.2.0.200";
    String[] args = argline.split("\\s");
    for (int i = 0; i < args.length; i++) {
      //prt(i+" arg="+args[i]);
      if (args[i].equals("-p")) {
        port = Integer.parseInt(args[i + 1]);
        i++;
      } else if (args[i].equals("-dbgmcast")) {
        dbg = true;
      } else if (args[i].equalsIgnoreCase("-qlen")) {
        MAX_LENGTH = Integer.parseInt(args[i + 1]);
        i++;
      } else if (args[i].equalsIgnoreCase("-mip")) {
        groupIP = args[i + 1];
        i++;
      } else if (args[i].equalsIgnoreCase("-bind")) {
        bindIP = args[i + 1];
        i++;
      } else if (args[i].equalsIgnoreCase("-if")) {
        eth = args[i + 1];
        i++;
      } else if (args[i].equalsIgnoreCase("-rsize")) {
        rcvBufSize = Integer.parseInt(args[i + 1]) * 1024;
        i++;
      } else if (args[i].equalsIgnoreCase("-qsize") || args[i].equalsIgnoreCase("-chanre")
              || args[i].equalsIgnoreCase("-msconfig") || args[i].equalsIgnoreCase("-msrange")
              || args[i].equalsIgnoreCase("-inorder") || args[i].equalsIgnoreCase("-l1zgapfill")) {
        i++;
      } // args with a params
      else if (args[i].equalsIgnoreCase("-q330") || args[i].equalsIgnoreCase("-nohydra")
              || // args with no params        
              args[i].equalsIgnoreCase("-noudpchan") || args[i].equalsIgnoreCase("-rsend")
              || args[i].equalsIgnoreCase("-dbg") || args[i].equalsIgnoreCase("-1sec")) {
      } else if (args[i].substring(0, 1).equals(">")) {
        break;
      } else {
        prt(Util.clear(tmpsb).append("MulticastListener unknown switch=").append(args[i]).append(" ln=").append(argline));
      }
    }
    this.tag = tag + "-MCL:[" + groupIP + "/" + port + "]";
    buf = new byte[MAX_LENGTH];
    terminate = false;

    // Setup the Multicast socket and Datagram for receiving messages.
    try {
      ss = new MulticastSocket(port);              // let system set this end port
      ss.setReuseAddress(true);
      if (rcvBufSize > 0) {
        //prt(Util.clear(tmpsb).append(this.tag).append("Rcv buf size def=").append(ss.getReceiveBufferSize()).
        //        append(" to ").append(rcvBufSize).append(" s=").append(ss));  // This tries to increas the system supplied buffer size

        ss.setReceiveBufferSize(rcvBufSize);
        //prt(Util.clear(tmpsb).append(this.tag).append("Rcv buf size aft=").append(ss.getReceiveBufferSize()).append(" s=").append(ss));
      }
    } catch (IOException e) {
      prta("MCL: Exception opening a multicast socket for port =" + port + " e=" + e);
      return;
    }
    try {
      prt(Util.clear(tmpsb).append(this.tag).append(" MCL: open port ").append(ss.getInetAddress()).
              append(" ladr=").append(ss.getLocalAddress()).append(" reuse=").append(ss.getReuseAddress()).
              append(" rcvbuf=").append(ss.getReceiveBufferSize()).append("/").append(rcvBufSize).
              append(" port=").append(ss.getPort()).append(" lport=").append(ss.getLocalPort()).
              append(" bindIP=").append(bindIP).append(" port=").append(port).
              append(" groupIP=" + " nic=").append(eth));
      group = InetAddress.getByName(groupIP);
      if (eth != null) {   // If a specific interface is needed.
        InetSocketAddress group2 = new InetSocketAddress(groupIP, port);
        NetworkInterface nic = NetworkInterface.getByName(eth);
        ss.joinGroup(group2, nic);
        prt(Util.clear(tmpsb).append("nic=").append(nic).append(" group2=").append(group2).append(" ss=").append(ss));
      } else {
        ss.joinGroup(group);
      }

      d = new DatagramPacket(buf, MAX_LENGTH);    // Setup the datagram to receive from the socket

      if (dbg) {
        prt(Util.clear(tmpsb).append(this.tag).append(" MCL: open port ").append(ss.getInetAddress()).
                append(" ladr=").append(ss.getLocalAddress()).append(" reuse=").append(ss.getReuseAddress()).
                append(" rcvbuf=").append(ss.getReceiveBufferSize()).append(" port=").append(ss.getPort()).
                append(" lport=").append(ss.getLocalPort()));
      }
    } catch (IOException e) {
      prt(Util.clear(tmpsb).append(this.tag).append("could not set up datagram socket or MulticastSocket e=").append(e.getMessage()));
      if (!e.toString().contains("already bound")) {
        if (par == null) {
          e.printStackTrace();
        } else {
          e.printStackTrace(par.getPrintStream());
        }
      }
      throw e;
    }
    setDaemon(true);
    start();

  }

  /**
   * write packets from queue
   */
  @Override
  public void run() {
    running = true;
    // This loop establishes a socket
    while (!terminate) {
      try {
        System.arraycopy(zeros, 0, d.getData(), 0, 512);    // prezero the array
        ss.receive(d);
        if (dbg) {
          byte[] data = d.getData();
          prta(Util.clear(runsb).append(tag).append("MCL: Got MCast lent=").
                  append(d.getLength()).append(" [0]=").append(data[0]).append(" [1]=").
                  append(data[1]).append(" [2]=").append(data[2]).append(" [3]").
                  append(data[3]).append(" ").append(d.getAddress()).append("/").
                  append(d.getPort()).append(d.getSocketAddress().toString()));
          /*iif(sb == null) sb = new StringBuilder(1000);
          if(sb.length() > 0) sb.delete(0,sb.length());
          for(int i=0; i<d.getLength(); i++) {
            sb.append(Util.leftPad(""+data[i+6],8));
            if(i % 10 == 9) sb.append("\n");
          }
          prt("DATA="+sb.toString());*/
        }
        boolean ok = false;
        int wait = 0;
        while (!ok) {
          ok = queue.queue(d, port);
          if (!ok) {
            wait++;
            if (wait % 20 == 19) {
              prta(Util.clear(runsb).append(tag).append("MCL: Waiting for queue ").append(wait));
            }
            try {
              sleep(10);
            } catch (InterruptedException expected) {
            }
          }
        }
        bytesin += d.getLength();
        if (d.getLength() > MAX_LENGTH) {
          prta(Util.clear(runsb).append(tag).append("MCL: packet too big ").append(groupIP).append("/").append(port));
        }
        totalrecs++;
        if (totalrecs % 10000 == 0) {
          if (System.currentTimeMillis() - lastStatus > 300000) {
            prta(toStringBuilder(Util.clear(runsb)));
            lastStatus = System.currentTimeMillis();
            totalrecs = 0;
          }
        }

      } catch (IOException e) {
        if (terminate) {
          break;
        }
        Util.IOErrorPrint(e, tag + " *** IOError getting Multicast " + groupIP + "/" + port + " " + e.getMessage());
        try {
          sleep(30000);
        } catch (InterruptedException expected) {
        }
      } catch (RuntimeException e) {
        if (terminate) {
          break;
        }
        prta(tag + "MCL: Runtime error in MulticastListener ");
        e.printStackTrace(par.getPrintStream());
        break;
      }
    }
    // Outside loop that opens a socket 
    prta(Util.clear(runsb).append(tag).append("MCL:  ** MulticastListener terminated"));
    close();
    prta(Util.clear(runsb).append(tag).append("MCL:  ** exiting"));
    running = false;
  }

  public void close() {
    if (ss != null) {
      if (!ss.isClosed()) {
        ss.close();
      }
    }
  }
}
