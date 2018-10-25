
/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.edgemom;
//import gov.usgs.alarm.SendEvent;

import gov.usgs.anss.edge.RTMSBuf;
import gov.usgs.anss.edge.IndexFile;
import gov.usgs.anss.edge.EdgeProperties;
import gov.usgs.anss.edge.ChannelSender;
import gov.usgs.alarm.*;
import gov.usgs.anss.edgethread.EdgeThread;
import gov.usgs.anss.util.Util;
import java.io.*;
import java.net.*;
import java.util.*;

/**
 * * RawInputServer.java
 *
 * Created on August 8, 2005, 3:11 PM This class accepts data in the 'raw' or
 * in-the-clear format and creates MiniSeed output for the edge database. Many
 * incoming formats are not in miniseed so some code is inserted that puts the
 * data in the clear and sends it to a port of this type. The work is actually
 * done in a RawInputSocket which is created for each connection to this server.
 * Most of the configuration arguments for a server port are actually passed to
 * the RawInputSocket for implementation. The code has the ability to handle
 * out-of-order data by using the -oor and -oorint. This puts a layer of
 * buffering and processing between the receipt of data and its processing to
 * mini-seed. This allows long enough runs of data to be collected and processed
 * to make good Miniseed.
 * <PRE>
 *switch    arg     Description
 *-p        pppp    Port to listen on
 *-oor              Set out-of-order processing on.  Used for AFTAC data primarily (OutOfOrderRTMS parameter)
 *-oorint   nnnn    Setting in milliseconds between processing checks on OOR data (OutOfOrderRTMS parameter)
 *-rsize    nnnn    Size of read buffer in bytes for the socket (RawInputSocket parameter)
 *-single           Set single mode.  Only one RawInputSocket can exist at a time on this server.  Close any on new connects
 *-socklog          Turn socket logging on - each socket gets its own log file whose name comes from input stream (RawInputSocket parameter)
 *-noudpchan        Do not send summary of data to UdpChannel for ChannelDisplay (RawInputSocket parameter)
 *-hydrainorder     Data is always in order - send data straight to Hydra(RawInputSocket parameter)
 *-nohydra          Do not send data to hydra (testing mainly)(RawInputSocket parameter)
 *-rawsave          Save raw received data in a file for debugging
 *-rawread          Read data from previously saved file (debugging)
 * -dbgch           A string to compare to first part of channel and turn on lines of received data for each match
 * -dbg             Generate lots of output over the input process.
 * -rsend  max      Send any data on this server via the OutputInfrastructure rawSend() with a max digit rate of max.
 *
 * The input stream looks like :
 *  0 short 0xa1b2          : this also sets the endianness of the stream, all other should follow same
 *  2 short nsamp           Number of samples that follow (-2 force out streams, -1 add to logging tag from seedname)
 *  4 string*12 seedname    NNSSSSSCCCLL seed channel name
 * 16 short year
 * 18 short doy             Day of year of starting sample
 * 20 short rateMantissa    Per the SEED definition
 * 22 short rateMultiplier  Per the SEED definition
 * 24 byte activity         "             "
 * 25 byte ioCLock          "             "
 * 26 byte quality          "             "
 * 27 byte timingQuality    "             "
 * 28 int sec               Since midnight for the starting sample
 * 32 int usec              Of the second
 * 36 int seq               Sequence number of the packet
 * 40 int[nsamp] data       integer data - if nsamp less than or equal to 0, then there is no data.
 * </PRE>
 *
 * @author davidketchum
 */
public final class RawInputServer extends EdgeThread {

  private ServerSocket ss;                // The socket we are "listening" to for new connections
  private final List<RawInputSocket> clients = Collections.synchronizedList(new LinkedList<RawInputSocket>());// Create the list of clients attached                   // list of clients which have attached (RawInputSockets)
  // Note: this is in a Collections.synchronizedList() to support
  // Synchronized iterators
  //private GregorianCalendar lastStatus;
  private int port;
  private final ShutdownRIServ shutdown;
  //private Status status;
  private boolean oor;                    // If true, set up to use the OOR algorithm
  private int oorInt;                     // Interval at which OOR processing is desired in ms
  private boolean dbg;
  private boolean rawsave;
  private boolean rawread;
  private String rawfile;
  private boolean singleMode;
  private boolean hydra;                  // Send data to hydra
  private boolean hydraInorder;            // Use the in order short cut for data to Hydra (by pass channel infra)
  private int readSize;                   // Passed to the sockets, the desired read size in kB
  private ChannelSender csend;
  private String dbgch = "ZZZZZZZZ";
  private boolean socketLog;
  private boolean rawSend;
  private double rawMaxRate;
  private final StringBuilder tmpsb = new StringBuilder(100);

  @Override
  public String toString() {
    return "port=" + port;
  }

  @Override
  public void terminate() {
    setTerminate(true);
  }

  public void setTerminate(boolean t) {
    terminate = t;
    if (terminate) {
      try {
        ss.close();
      } catch (IOException expected) {
      }
    }
  }

  public void setDebug(boolean t) {
    dbg = t;
  }

  /**
   * Creates a new instance of ServerThread.
   *
   * @param argline EdgeThread arguments.
   * @param tg EdgeThread tag.
   */
  public RawInputServer(String argline, String tg) {
    super(argline, tg);
    String[] args = argline.split("\\s");
    dbg = false;
    int len = 512;
    String h = null;
    port = 7981;
    oorInt = 60000;       // Default interval for checking oor data
    rawread = false;
    singleMode = false;
    readSize = 64000;
    hydra = true;
    boolean nocsend = false;
    hydraInorder = false;
    rawfile = "";
    socketLog = false;        // Individual logging per socket

    for (int i = 0; i < args.length; i++) {
      //prt(i+" arg="+args[i]);
      if (args[i].equals("-p")) {
        port = Integer.parseInt(args[i + 1]);
        i++;
      } else if (args[i].equals("-dbg")) {
        dbg = true;
      } else if (args[i].equals("-oor")) {
        oor = true;
      } else if (args[i].equals("-oorint")) {
        oorInt = Integer.parseInt(args[i + 1]);
        i++;
      } else if (args[i].equals("-rawsave")) {
        rawsave = true; // create raw output
      } else if (args[i].equals("-rawread")) {
        rawread = true;
        rawfile = args[i + 1];
        i++;
      } else if (args[i].equals("-rsize")) {
        readSize = Integer.parseInt(args[i + 1]) * 1000;
        i++;
      } else if (args[i].equals("-single")) {
        singleMode = true;
      } else if (args[i].equals("-socklog")) {
        socketLog = true;
      } else if (args[i].equals("-noudpchan")) {
        nocsend = true;
      } else if (args[i].equals("-hydrainorder")) {
        hydraInorder = true;
      } else if (args[i].equals("-nohydra")) {
        hydra = false;
      } else if (args[i].equals("-dbgch")) {
        dbgch = args[i + 1];
        i++;
      } else if (args[i].equals("-rsend")) {
        rawSend = true;
        rawMaxRate = Double.parseDouble(args[i + 1]);
        i++;
      } //{ RawToMiniSeed.setNoHydra(true);  prt("    WARNING: all RTMS is set no Hydra");}
      else if (args[i].substring(0, 1).equals(">")) {
        break;
      } else {
        prt("RawInputServer unknown switch=" + args[i] + " ln=" + argline);
      }
    }
    prt("RawInputServer starting on port=" + port + " singlemode=" + singleMode + " oor=" + oor
            + " oorint=" + oorInt + " rsize=" + readSize + " hydra=" + hydra + " inorder=" + hydraInorder);
    shutdown = new ShutdownRIServ();
    Runtime.getRuntime().addShutdownHook(shutdown);
    try {
      ss = new ServerSocket(port);            // Create the listener
      //lastStatus = new GregorianCalendar();   // Set initial time
      start();
    } catch (IOException e) {
      Util.IOErrorPrint(e, "RawInputServer : Cannot set up socket server on port " + port);
      Util.exit(10);
    }
    if (!nocsend) {
      csend = new ChannelSender("  ", "RawInputSrv", "RI-" + tg);
    }
    //status=new Status();              // 5 minute status output
  }

  /**
   * This Thread does accepts() on the port and creates a new ClientSocket inner
   * class object to track its progress (basically holds information about the
   * client and detect writes that are hung via a timeout Thread).
   */
  @Override
  public void run() {
    running = true;
    prt(Util.asctime() + " RIServ : start accept loop.  I am "
            + ss.getInetAddress() + "-" + ss.getLocalSocketAddress() + "|" + ss);
    try {
      while (!terminate) {
        RawInputSocket q = null;
        if (dbg) {
          prt(Util.asctime() + " RIServ:" + tag + " " + port + "  call accept()");
        }
        try {
          Socket s = accept();
          if (terminate) {
            break;        // Shuting down, do not start anything new!
          }
          if (singleMode && q != null) {
            q.terminate();
          }
          if (dbg) {
            prta(" RIServ:" + tag + " " + port + " new connection from " + s);
          }
          boolean found = false;
          for (int i = 0; i < clients.size(); i++) {
            if (clients.get(i).isAvailable()) {
              q = clients.get(i);
              q.setSocket(s);
              found = true;
              prt("    Reuse i=" + i + "/" + clients.size() + " " + q.getStatusString());
              break;
            }
          }
          if (!found) {
            q = new RawInputSocket(s, oor, oorInt, (EdgeThread) this, csend, readSize,
                    hydra, hydraInorder, socketLog, rawSend, rawMaxRate, dbgch);
            if (rawread) {
              q.setReadraw(rawfile);
            }
            if (rawsave) {
              q.setRawsave();
            }
            q.setDebug(dbg);
            if (singleMode && clients.size() > 0) {
              prta(tag + "RIServ: Single mode terminate RIS at " + clients.get(0).getTag());
              clients.get(0).terminate();
              clients.remove(0);       // Take the only allowed connection off the list
            }
            synchronized (clients) {
              Iterator itr = clients.iterator();
              int n = 0;
              RawInputSocket u;
              long now = System.currentTimeMillis();
              while (itr.hasNext()) {
                u = (RawInputSocket) itr.next();
                if ((!u.isRunning() && !u.isAlive())) {
                  prt(Util.clear(tmpsb).append("    REMOVE ").append(u.getStatusString()));
                  u.terminate();
                  itr.remove();
                } else {
                  prt("   Remaining Status : " + u.getStatusString());
                }
              }
            }
            clients.add(q);
            prt("    Added sz=" + clients.size() + " " + q.getStatusString());
          }
        } catch (IOException e) {
          if (!e.getMessage().equals("Socket closed")) {
            Util.IOErrorPrint(e, "RIServ:" + tag + " " + port + " RawInputServerThread: accept gave IOException!"
                    + e.getMessage());

            break;
          } // If its just a "Socket closed" be quiet and accept that it was closed
          else {
            prt(Util.asctime() + " RIServ:" + tag + " " + port + " socket is closed.");
          }
        }
      } // while(!terminate)
      // Shutdown all open clients
      synchronized (clients) {
        Iterator itr = clients.iterator();
        int n = 0;
        RawInputSocket u;
        while (itr.hasNext()) {
          u = (RawInputSocket) itr.next();
          u.terminate();
        }
      }
    } catch (RuntimeException e) {
      prta(tag + "RIS: RuntimeException in " + this.getClass().getName() + " e=" + e.getMessage());
      if (getPrintStream() != null) {
        e.printStackTrace(getPrintStream());
      } else {
        e.printStackTrace();
      }
      if (e.getMessage() != null) {
        if (e.getMessage().contains("OutOfMemory")) {
          SendEvent.doOutOfMemory(tag, this);
          throw e;
        }
      } else {
        prta(tag + "RIS: RuntimeException e=" + e);
      }
    }
    if (csend != null) {
      csend.close();
    }
    running = false;
    terminate = false;
  }

  public Socket accept() throws IOException {
    if (dbg) {
      prta("RIS: in accept()");
    }
    return ss.accept();
  }

  @Override
  public long getBytesIn() {
    long bytesin = 0;
    synchronized (clients) {
      Iterator<RawInputSocket> itr = clients.iterator();
      RawInputSocket u;
      while (itr.hasNext()) {
        bytesin += itr.next().getBytesIn();
      }
    }
    return bytesin;
  }

  @Override
  public long getBytesOut() {
    long bytesout = 0;
    synchronized (clients) {
      Iterator<RawInputSocket> itr = clients.iterator();
      int n = 0;
      RawInputSocket u;
      while (itr.hasNext()) {
        bytesout += itr.next().getBytesOut();
      }
    }
    return bytesout;
  }

  @Override
  public StringBuilder getConsoleOutput() {
    return consolesb;
  } // Use prt and prta directly
  /**
   * Return the monitor string for Icinga.
   *
   * @return A String representing the monitor key value pairs for this
   * EdgeThread.
   */
  long lastMonBytes;

  @Override
  public StringBuilder getMonitorString() {
    Util.clear(monitorsb);
    long bi = getBytesIn();
    long nb = bi - lastMonBytes;
    lastMonBytes = bi;
    /*Iterator<MiniSeedSocket> itr = clients.values().iterator();
    while(itr.hasNext()) {
      monitorsb.append(itr.next().getMonitorString()).append("\n");
    }*/
    monitorsb.insert(0, tag + "TotalBytesIn=" + nb + "\n" + tag + "NThreads=" + clients.size() + "\n");
    return monitorsb;
  }

  @Override
  public StringBuilder getStatusString() {
    Util.clear(statussb);
    long current = System.currentTimeMillis();
    statussb.append("RawInputServer has ").append(clients.size()).append(" subthreads port=").append(port).append(" ").append(RTMSBuf.getStatus()).append("\n");
    RawInputSocket u;
    synchronized (clients) {
      Iterator itr = clients.iterator();
      int n = 0;
      while (itr.hasNext()) {
        u = (RawInputSocket) itr.next();
        statussb.append("      ").append(u.getStatusString().substring(4)).append("\n");
        n++;
        //if( n % 2 == 0 && itr.hasNext()) sb.append("\n");
        if ((current - u.getLastRead()) > 3600000) {
          prta("RIServ:" + tag + " " + port + " Close stale connection " + u.getTag());
          u.setTerminate(true);
          itr.remove();
        }
      }
    }
    return statussb;
  }

  /**
   * This class sends out status information every minute.
   */
  final class Status extends Thread {

    public Status() {
      gov.usgs.anss.util.Util.prta("new ThreadStatuus " + getName() + " " + getClass().getSimpleName());
      start();
    }

    @Override
    public void run() {
      while (!terminate) {
        prt(Util.asctime() + " " + Util.ascdate() + "\n" + getStatusString());
        try {
          sleep(60000L);
        } catch (InterruptedException expected) {
        }
      }
    }
  }

  /**
   * This class shutdown this thread via the Rundown service.
   */
  final class ShutdownRIServ extends Thread {

    public ShutdownRIServ() {
      gov.usgs.anss.util.Util.prta("new ThreadShutdown " + getName() + " " + getClass().getSimpleName());
    }

    // This is called by the RunTime.shutdown at any termination 
    @Override
    public void run() {
      prta("RIServ:" + tag + " " + port + "  Shutdown() started...");
      terminate = true;

      int nrunning = 1;
      int loops = 0;
      // Close down the accept() socket
      try {
        if (!ss.isClosed()) {
          ss.close();
        }
        prta("RIServ:" + tag + " " + port + " Shutdown() close accept socket");
      } catch (IOException expected) {
      }

      // Close down all of the RawInputSocket threads we know about
      synchronized (clients) {
        for (RawInputSocket q : clients) {
          prta("RIServ:" + tag + " " + port + " shutdown() send terminate() to " + q.getTag());
          q.setTerminate(true);
        }
      }

      try {
        sleep(2000L);
      } catch (InterruptedException expected) {
      }
      prta("RIServ:" + tag + " " + port + " In Shutdown() wait for all threads to exit");

      // Wait for all of the threads to exit
      while (nrunning > 0) {
        loops++;
        if (loops > 5) {
          break;
        }
        nrunning = 0;
        String list = "Threads still up : ";
        synchronized (clients) {
          for (RawInputSocket q : clients) {
            if (q.isRunning() && q.isAlive()) {
              nrunning++;
              list += " " + q.getTag();
              if (nrunning % 5 == 0) {
                list += "\n";
              }
            }
          }
        }

        prta("RIServ:" + tag + " " + port + " Shutdown() waiting for " + nrunning + " threads. " + list);
        if (nrunning == 0) {
          break;        // Speed up the exit!
        }
        try {
          sleep(4000L);
        } catch (InterruptedException expected) {
        }
      }
      prta("RIServ:" + tag + " " + port + " Shutdown of RawInputServer is complete.");
    }
  }

  /*@param args Unused command line args*/
  public static void main(String[] args) {
    Util.setModeGMT();
    IndexFile.init();
    EdgeProperties.init();
    boolean dbg = true;
    for (String arg : args) {
      if (arg.equals("-dbg")) {
        dbg = true;
      }
    }

    RawInputServer server = new RawInputServer("-p 7981 -dbg", "RISv");
    server.setDebug(dbg);
  }

}
