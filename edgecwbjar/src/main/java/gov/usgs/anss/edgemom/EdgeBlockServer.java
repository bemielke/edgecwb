/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.edgemom;

import gov.usgs.anss.edge.IndexBlockCheck;
import gov.usgs.anss.edge.IndexFile;
import gov.usgs.anss.edge.IndexBlock;
import gov.usgs.alarm.SendEvent;
import gov.usgs.anss.edge.EdgeBlockQueue;
import gov.usgs.anss.edgethread.EdgeThread;
import gov.usgs.anss.util.Util;
import gov.usgs.anss.util.SimpleSMTPThread;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Iterator;
import java.util.List;
import java.util.Collections;
import java.util.LinkedList;

/**
 * This server provides all the blocks being written to the files in this
 * EdgeMom to any connected clients. The blocks have a header that indicates
 * which file the block is intended for (julian day and node), and the physical
 * block number in the data file. In addition, any writes to the index file are
 * also present. Using this information, make a Replicator object in conjunction
 * with a EdgeBlockClient and replicate the data file on this server on the
 * client system. All EDGE nodes run this server and the CWB nodes connect to
 * them to cause the replication of the data files. Normally, a IndexFileServer
 * is run on every node with an EdgeBlockServer to provide dumps of the
 * IndexFile and the startup of a Replicator connection.
 *
 * <PRE>
 *It is EdgeThread with the following arguments :
 * Tag   arg          Description
 * -p    ppppp        Run the server on this port, or if its instance format the instance for this port
 * -poff    nnnnn     Change the port offset for this to nnnnn (def=7500)
 * -dbgibc            Run debug mode on the IndexBlockCheck objects.
 * -dbgebq            Run debug output in the EdgeBlockQueue feeding this server.
 * -dbg               Run debug output on this server.
 * -cwbcwb            This EdgeBlockServer is running on a CWB node and should respond to all requests
 * >[>]  filename     You can redirect output from this thread to filename in the current logging directory
 * </PRE>
 *
 * @author davidketchum
 */
public final class EdgeBlockServer extends EdgeThread {

  ServerSocket ss;                // The socket we are "listening" to for new connections
  private final List<EdgeBlockSocket> clients = Collections.synchronizedList(new LinkedList<EdgeBlockSocket>());// Create the list of clients attached
  // Note: this is in a Collections.synchronizedList() to support
  // Synchronized iterators
  //GregorianCalendar lastStatus;
  private int port;
  private ShutdownEBServ shutdown;
  //Status status;
  private String cmdline;
  private boolean cwbcwb;        // passed to EdgeBlockSocket constructor via cmdline)
  private boolean dbg;
  private int portOffset = 7500;
  private StringBuilder runsb = new StringBuilder(100);

  @Override
  public String toString() {
    return "p=" + port + " #clients=" + clients.size();
  }

  public StringBuilder toStringBuilder(StringBuilder sb) {
    if (sb == null) {
      sb = Util.getPoolSB();
    }
    Util.clear(sb).append("p=").append(port).append(" #clients=").append(clients.size());
    return sb;
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
      } catch (IOException e) {
      }
    }
  }

  public void setDebug(boolean t) {
    dbg = t;
  }

  /**
   * Creates a new instance of ServerThread.
   *
   * @param argline The argument line described in the class section.
   * @param tg The Edgethread tag. This will appear on all line output from this
   * modules.
   */
  public EdgeBlockServer(String argline, String tg) {
    super(argline, tg);
    cmdline = argline;
    if (cmdline.contains(">")) {
      cmdline = cmdline.substring(0, cmdline.indexOf(">")).trim();  // do not reuse the log file in children
    }
    String[] args = argline.split("\\s");
    dbg = false;
    int len = 512;
    String h = null;
    port = 7983;
    boolean dbgibc = false;
    boolean dbgebq = false;
    for (int i = 0; i < args.length; i++) {
      //prt(i+" arg="+args[i]);
      if (args[i].substring(0, 1).equals(">")) {
        break;
      }
      switch (args[i]) {
        case "-p":
          if (args[i + 1].contains("#")) {
            port = IndexFile.getNodeNumber(args[i + 1]) * 10 + IndexFile.getInstanceNumber(args[i + 1]) + portOffset;
          } else {
            port = Integer.parseInt(args[i + 1]);
          }
          i++;
          break;
        case "-dbg":
          dbg = true;
          break;
        case "-dbgibc":
          dbgibc = true;
          break;
        case "-poff":
          portOffset = Integer.parseInt(args[i + 1]);
          i++;
          break;
        case "-dbgebq":
          dbgebq = true;
          break;
        case "-cwbcwb":
          cwbcwb = true;
          break;
        case "-idxblk":
          IndexBlock.setDebugBlocks(true);
          break;
        default:
          prt("EdgeBlockServer unknown switch=" + args[i] + " ln=" + argline);
          break;
      }
    }
    prt(Util.clear(runsb).append("EdgeBlockServer starting on port=").append(port).append(" cwbcwb=").append(cwbcwb).append(" poff=").append(portOffset));
    shutdown = new ShutdownEBServ();
    Runtime.getRuntime().addShutdownHook(shutdown);

    // Set up debugs
    if (dbgebq) {
      EdgeBlockQueue.setDebug(dbgebq);
    }
    IndexBlockCheck.setDebug(dbgibc);
    try {
      ss = new ServerSocket(port);            // Create the listener

      //lastStatus = new GregorianCalendar();   // Set initial time
      start();
    } catch (IOException e) {
      Util.IOErrorPrint(e, "EdgeBlockServer : Cannot set up socket server on port " + port);
      Util.exit(10);
    }
    //status=new Status();              // 5 minute status output
  }

  /**
   * This Thread does accepts() on the port and creates a new ClientSocket inner
   * class object to track its progress. This basically holds information about
   * the client and detects writes that are hung via a timeout Thread.
   */
  @Override
  public void run() {
    running = true;
    prta(" EBServ:" + tag + " " + port + " start accept loop.  I am "
            + ss.getInetAddress() + "-" + ss.getLocalSocketAddress() + "|" + ss);
    try {
      while (!terminate) {
        if (dbg) {
          prta(Util.clear(runsb).append(" EBServ:").append(tag).append(" ").append(port).append("  call accept()"));
        }
        try {
          Socket s = accept();          // Use the accept method for performance analyzer
          if (terminate) {
            break;          // If we are terminating kill for this socket, do not start a new one!
          }
          EdgeBlockSocket q = new EdgeBlockSocket(cmdline,
                  s.getInetAddress().toString().substring(1) + "/" + s.getPort() + ":", s, this);
          q.setDebug(dbg);
          prta(Util.clear(runsb).append(" EBServ:").append(tag).append(" ").append(port).
                  append(" new connection from ").append(s).append(" clients=").append(clients.size()).
                  append(" scktag=").append(q.getTag()));
          synchronized (clients) {
            clients.add(q);
            Iterator<EdgeBlockSocket> itr = clients.iterator();
            int n = 0;
            EdgeBlockSocket u;
            while (itr.hasNext()) {
              u = itr.next();
              prta(Util.clear(runsb).append("       ").append(n++).append(" ").append(u.getTag()).
                      append(" alive=").append(u.isAlive()).append(u.isRunning()).append(" closed=").append(u.isClosed()));
              if (!u.isAlive()) {
                prta(Util.clear(runsb).append("     ***** removing ").append(u.getTag()));
                itr.remove();
              }
            }
          }
        } catch (IOException e) {
          if (!e.getMessage().equals("Socket closed")) {
            Util.IOErrorPrint(e, "EBServ: UdpEdgeBlockServerThread: accept gave IOException!");
            Util.exit(0);
          } else {
            prta(Util.clear(runsb).append(" EBServ:").append(tag).append(" ").append(port).append(" server socket closed e=").append(e));
          }
        }
      }
    } catch (RuntimeException e) {
      prta(Util.clear(runsb).append(tag).append("EBServ: RuntimeException in ").append(this.getClass().getName()).append(" e=").append(e.getMessage()));
      if (getPrintStream() != null) {
        e.printStackTrace(getPrintStream());
      } else {
        e.printStackTrace();
      }
      if (e.getMessage().contains("OutOfMemory")) {
        SimpleSMTPThread.email(Util.getProperty("emailTo"),
                "Out of Memory in " + Util.getProcess() + "/" + this.getClass().getName() + " on " + IndexFile.getNode() + "/" + Util.getAccount(),
                Util.asctime() + " " + Util.ascdate() + " Body");
        SendEvent.edgeEvent("OutOfMemory", "Out of Memory in " + Util.getProcess() + "/" + this.getClass().getName() + " on " + IndexFile.getNode() + "/" + Util.getAccount(), this);
        throw e;
      }
      terminate();
    }
    synchronized (clients) {
      Iterator itr = clients.iterator();
      int n = 0;
      EdgeBlockSocket u;
      while (itr.hasNext()) {
        u = (EdgeBlockSocket) itr.next();
        u.terminate();
      }
    }
    running = false;
    terminate = false;
  }

  /**
   * This is to isolate accept() calls for the performance analyzer.
   *
   * @return The socket from accept.
   */
  private Socket accept() throws IOException {
    return ss.accept();
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
  long lastMonpublic;

  @Override
  public StringBuilder getMonitorString() {
    if (monitorsb.length() > 0) {
      monitorsb.delete(0, monitorsb.length());
    }
    long nb = 0;
    Iterator<EdgeBlockSocket> itr = clients.iterator();
    while (itr.hasNext()) {
      EdgeBlockSocket ebs = itr.next();
      nb += ebs.getBytesOut();
      monitorsb.append(ebs.getMonitorString());
    }
    long tmp = nb;
    nb -= lastMonBytes;
    lastMonBytes = tmp;
    monitorsb.insert(0, "EBSvTotalBytesOut=" + nb + "\nNThreads=" + clients.size() + "\n");
    return monitorsb;
  }

  /**
   * Return the status String.
   *
   * @return
   */
  @Override
  public StringBuilder getStatusString() {
    if (statussb.length() > 0) {
      statussb.delete(0, statussb.length());
    }
    long current = System.currentTimeMillis();
    EdgeBlockSocket u;
    statussb.append("EdgeBlockServer has ").append(clients.size()).append(" subthreads port=").append(port).append("\n");
    synchronized (clients) {
      Iterator itr = clients.iterator();
      int n = 0;
      while (itr.hasNext()) {
        u = (EdgeBlockSocket) itr.next();
        statussb.append("      ").append(u.getStatusString().substring(4)).append("\n");
        n++;
        //if( n % 2 == 0 && itr.hasNext()) statussb.append("\n");
        if ((current - u.getlastWrite()) > 3600000 || !u.isAlive()) {
          prta(Util.clear(runsb).append("Close stale connection ").append(u.getTag()));
          u.setTerminate(true);
          itr.remove();
        }
      }
    }
    return statussb;
  }

  /*class Status extends Thread {
    public Status() {
      start();
    }
    public void run() {
      while(!terminate) {
        prt(Util.asctime()+" "+Util.ascdate()+"\n"+getStatusString());
        try{sleep(60000L);} catch (InterruptedException e) {}
      }
    }
  }*/
  class ShutdownEBServ extends Thread {

    public ShutdownEBServ() {
      gov.usgs.anss.util.Util.prta("new ThreadShutdown " + getName() + " " + getClass().getSimpleName());
    }

    // This is called by the RunTime.shutdown at any termination 
    @Override
    public void run() {
      System.err.println(Util.asctime2() + " EBServ:  Shutdown() started...");
      terminate = true;

      int nrunning = 1;
      int loops = 0;
      // Close down the accept() socket
      try {
        if (!ss.isClosed()) {
          ss.close();
        }
        System.err.println("EBServ: Shutdown() close accept socket");
      } catch (IOException e) {
      }
      try {
        sleep(15000);
      } catch (InterruptedException e) {
      } // give some time for the sockets to send out anyting in the EBQueue.
      // Close down all of the EdgeBlockSocket threads we know about
      synchronized (clients) {
        for (EdgeBlockSocket q : clients) {
          System.err.println(Util.asctime2() + " EBServ: shutdown() send terminate() to " + q.getTag());
          q.setTerminate(true);
        }
      }

      try {
        sleep(2000L);
      } catch (InterruptedException e) {
      }
      System.err.println("EBServ: In Shutdown() wait for all threads to exit");

      // Wait for the threads to all exit
      while (nrunning > 0) {
        loops++;
        if (loops > 5) {
          break;
        }
        nrunning = 0;
        String list = "Threads still up : ";
        synchronized (clients) {
          for (EdgeBlockSocket q : clients) {
            if (q.isRunning() && q.isAlive()) {
              nrunning++;
              list += " " + q.getTag();
              if (nrunning % 5 == 0) {
                list += "\n";
              }
            }
          }
        }

        System.err.println("EBServ: Shutdown() waiting for " + nrunning + " threads. " + list);
        if (nrunning == 0) {
          break;        // speed up the exit!
        }
        try {
          sleep(4000L);
        } catch (InterruptedException e) {
        }
      }
      System.err.println("EBServ: Shutdown of EdgeBlockServer is complete.");
    }
  }

}
