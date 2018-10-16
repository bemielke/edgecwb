/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.cwbquery;

import gov.usgs.anss.edge.IndexFile;
import gov.usgs.anss.edge.IndexFileReplicator;
import gov.usgs.anss.edge.EdgeProperties;
import gov.usgs.alarm.SendEvent;
import gov.usgs.anss.edgemom.EdgeChannelServer;
import gov.usgs.anss.edgethread.EdgeThread;
import gov.usgs.anss.edgethread.MemoryChecker;
import gov.usgs.anss.util.Util;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
//import java.util.GregorianCalendar;

/**
 * This class listens to a port for socket connections as a thread under QueryMom. For each incoming
 * socket connection found. A pool of up to some limit of "EdgeQueryThread" objects are managed and
 * one of the pool gets the accepted socket assigned to it. This thread handle the session with a
 * CWBQuery (or such) client. When the client closes the connection or error occurs, the
 * EdgeQueryThread will indicate its back on the pool by setting its socket assigned variable to
 * null.
 * <br>
 * The pool of threads is also managed looking for dead ones which are replaced with new ones and a
 * error logged as EdgeQueryThread should never exit.
 * <br>
 * The pool of threads is also examined for threads that are alive but not doing anything for a long
 * period. Such threads have their connections forced to close so they are available for more work.
 * This should only happen when a socket goes down in a bad manner and the thread is not notified
 * that is socket has gone away.
 *
 * <br>
 * <PRE>
 * Tag   arg          Description
 * -p    pppppp       The port number to run the server on (def = 2061)
 * -rw                If present, IndexFileReplicators to the files will be
 *                    opened read/write.  This is not a good idea unless delete/editing is permitted on this node.
 * -allowrestricted   Indicate this server can server restricted data
 * -mdsip ip.adr      Indicate a different MetaDataServer host, used by delaz (def=137.227.224.97 cwbpub)
 * -mdsport pppp      Indicate a different MeatDataServer port, used by delaz (def=2052)
 * -maxthreads  nnn   Maximum number of allowed connections simultaneously (def=200)
 * -maxopen nnn       Maximum number of files to allow to be opened before trimming back to 80% of this amount (def=1200)
 * -dbg               Turn on debugging of server portion only (not the requests)
 * -noautonnice       Do not allow ANY queries to set the nice mode unless it is expressly on the command line
 * -dbgall            Turn on debugging of all requests
 * </PRE> * EdgeQueryServer.java
 *
 * @author ketchum
 */
public final class EdgeQueryServer extends EdgeThread {

  public static boolean allowrestricted;
  public static boolean readonly = true;       // set true if IndexFileReplicators are to be ro
  public static boolean readonlyTimedout;
  public static String mdsServer = "137.227.224.97";  // I think its o.k. to put our public MDS in the source - it is public!
  public static int mdsPort = 2052;
  public static int state;
  public static boolean quiet;
  public static boolean noAutoNice;
  private static EdgeQueryServer thisServer;
  private String propertiesFile;
  private ServerSocket ss;        // the socket we are "listening" for new connections
  // set false for servers running on a EdgeMom w/Replicator as
  // The replicator also ahs IFRs open, but for read/write.
  private int maxThreads = 200;
  private int usedThreads;
  private ArrayList<EdgeQueryThread> handlers = new ArrayList<>(maxThreads);
  private int port;
  private long lastStatus;
  private boolean dbg, dbgall;
  private QueryFilesThread fileThread;
  private int nconnect;
  private int maxOpenFiles = 1200;
  private final ShutdownEdgeQueryThread shutdown;
  private CloseFiles closer;

  public static String getString() {
    return (thisServer == null ? "Null" : thisServer.toString());
  }

  @Override
  public String toString() {
    return tag + " state=" + state + (ss != null ? " ss=" + ss + " isClosed()=" + ss.isClosed() : " ss=null:") + " "
            + Util.ascdatetime2(lastStatus, null) + " closestate=" + (closer == null ? "null" : closer.state());
  }

  public static boolean isReadOnly() {
    if (readonlyTimedout) {
      return readonly;
    }
    for (int i = 0; i < 100; i++) {
      if (thisServer != null) {
        if (thisServer.isRunning()) {
          readonlyTimedout = true;
          return readonly;
        }
      }
      Util.sleep(100);
    }
    EdgeThread.staticprta("EdgeQueryServer : **** isReadOnly() while not running!!");
    SendEvent.edgeSMEEvent("EQS_ROBad", "Readonly setting in EdgeQueryServer timed out!", "EdgeQueryServer");
    readonlyTimedout = true;
    return true;

  }

  public static EdgeQueryServer getServer() {
    return thisServer;
  }

  //int deadLock;
  /**
   * return the monitor string for ICINGA
   *
   * @return A String representing the monitor key value pairs for this EdgeThread
   */
  @Override
  public StringBuilder getMonitorString() {
    int totblks = 0;
    int nquery = 0;
    int nconn = 0;
    for (EdgeQueryThread eq : handlers) {
      if (eq != null) {
        nconn += eq.isConnected() ? 1 : 0;
        totblks += eq.getTotalBlocks();
        nquery += eq.getNQuery();
      }
    }
    Util.clear(monitorsb).append("Nconn=").append(nconn).append("\nthr=").
            append(handlers.size()).append("\nmaxthr=").append(maxThreads).
            append("\nNquery=").append(nquery).append("\ntotblks=").append(totblks).append("\nnconnect=")
            .append(nconn).append("\n");
    return monitorsb;
  }

  @Override
  public StringBuilder getStatusString() {
    int totblks = 0;
    int nquery = 0;
    int nconn = 0;
    for (EdgeQueryThread eq : handlers) {
      if (eq != null) {
        nconn += eq.isConnected() ? 1 : 0;
        totblks += eq.getTotalBlocks();
        nquery += eq.getNQuery();
      }
    }
    if (statussb.length() > 0) {
      statussb.delete(0, statussb.length());
    }
    statussb.append("#conn=").append(nconn).append(" thr=").
            append(handlers.size()).append("/").append(maxThreads).
            append(" #query=").append(nquery).append(" totblks=").append(totblks).append(" nconnect=")
            .append(nconn);
    return statussb;
  }

  @Override
  public StringBuilder getConsoleOutput() {
    return consolesb;
  }

  @Override
  public void terminate() {
    terminate = true;
    fileThread.terminate();
  }

  /**
   * Creates a new instance of EQS
   *
   * @param argline The argument line
   * @param tg The login tag
   */
  @SuppressWarnings("empty-statement")
  public EdgeQueryServer(String argline, String tg) {
    super(argline, tg);
    String[] args = argline.split("\\s");
    port = 2061;
    readonly = true;
    propertiesFile = "queryserver.prop";
    File qs = new File("queryserver.prop");
    if (qs.exists()) {
      prta("load queryserver.prop");
      Util.loadProperties("queryserver.prop");
      Util.prtProperties(getPrintStream());
      IndexFile.forceinit();
      IndexFileReplicator.forceinit();
    } else {
      prta("No queryserver.prop found");
    }

    for (int i = 0; i < args.length; i++) {
      args[i] = args[i].toLowerCase();
      if (args[i].contains(">")) {
        break;
      }
      switch (args[i]) {
        case "-p":
          port = Integer.parseInt(args[i + 1]);
          i++;
          break;
        case "-rw":
          readonly = false;
          break;
        case "-maxthreads":
          maxThreads = Integer.parseInt(args[i + 1]);
          i++;
          break;
        case "-maxopen":
          maxOpenFiles = Integer.parseInt(args[i + 1]);
          i++;
          break;
        case "-mdsip":
          mdsServer = args[i + 1];
          i++;
          break;
        case "-mdsport":
          mdsPort = Integer.parseInt(args[i + 1]);
          i++;
          break;
        case "-noautonice":
          noAutoNice = true;
          break;
        case "-allowrestricted":
          allowrestricted = true;
          break;
        case "-dbg":
          dbg = true;
          break;
        case "-quiet":
          quiet = true;
          break;
        case "-dbgall":
          dbgall = true;
          EdgeQueryThread.setDebugAll(true);
          break;
        case "-new":
          ;
          break;
        case "-prop":
          Util.loadProperties(args[i + 1]);
          prta("Load properties=" + args[i + 1]);
          propertiesFile = args[i + 1];
          IndexFile.forceinit();
          IndexFileReplicator.forceinit();
          prt("npath=" + IndexFileReplicator.getNPaths());
          for (int j = 0; j < IndexFileReplicator.getNPaths(); j++) {
            prt("datapath" + j + "=" + IndexFileReplicator.getPath(j));
          }
          i++;
          break;
        case "-empty":
          ;
          break;
        default:
          prta("Unknown argument i=" + i + " is " + args[i]);
          break;
      }
    }
    readonlyTimedout = true;    // Lock in the ro reporter
    tag = tag + ":";
    try {
      prta(tag+"EQS:Startup port=" + port + " ro=" + readonly + " mds=" + mdsServer + "/" + mdsPort
              + " maxThreads=" + maxThreads + " allowrestricted=" + allowrestricted + " argline=" + argline + " srvdbg=" + dbg + " dbgall=" + dbgall + " quiet=" + quiet);
      ss = new ServerSocket(port, 256);            // Create the listener
      start();
    } catch (IOException e) {
      Util.IOErrorPrint(e, "EQS: Cannot set up socket server on port " + port);
      Util.exit(10);
    }
    shutdown = new ShutdownEdgeQueryThread();
    Runtime.getRuntime().addShutdownHook(shutdown);
  }

  /**
   * This Thread does accepts() on the port an assigns EdgeQueryThreads
   *
   */
  @Override
  public void run() {
    thisServer = this;
    fileThread = new QueryFilesThread(this);
    lastStatus = System.currentTimeMillis();
    setPriority(getPriority() + 2);
    prt(Util.asctime() + " EQS: start accept loop.  I am " + ss + " ro=" + readonly);
    int nclosed;
    long now;
    long lastMemoryCheck = System.currentTimeMillis();
    long lastPurge = lastMemoryCheck;
    running = true;
    closer = new CloseFiles();
    state = 1;
    Socket s = null;
    while (!terminate) {
      now = System.currentTimeMillis();
      state = 2;
      try {
        if (now - lastStatus > 900000 || IndexFileReplicator.getNOpenFiles() > maxOpenFiles) {
          state = 3;
          //prta(Util.getThreadsString());
          int nopen = IndexFileReplicator.getNOpenFiles();
          lastStatus = now;
          closer.doitNow();         // Do a close files cycle right now
          state = 4;
          now = System.currentTimeMillis();
          prta("EQS: used=" + usedThreads + " size=" + handlers.size() + " max=" + maxThreads
                  + " #connects=" + nconnect + " nopen=" + nopen + " now " + IndexFileReplicator.getNOpenFiles());
          for (int i = 0; i < handlers.size(); i++) {
            prta(i + " "
                    + " last=" + Util.leftPad("" + (now - handlers.get(i).getLastActionMillis()), 6) + " "
                    + (handlers.get(i) == null ? "null" : handlers.get(i).getStatus()));
          }
          try {
            if (!quiet) {
              state = 5;
              String line;
              try (BufferedReader stats = new BufferedReader(new StringReader(Util.getThreadsString().toString()))) {
                while ((line = stats.readLine()) != null) {
                  if (!line.contains("ZeroDataFile")  && !line.contains("QuerySpanThread") &&
                      !line.contains("Picker")) {
                    prt(line);
                  }
                }
              }
              prta("EQS: Time: " + EdgeQuery.getStatus());
            }
          } catch (IOException expected) {
          }
          File qs = new File(propertiesFile);
          if (qs.exists()) {
            Util.loadProperties(propertiesFile);
            IndexFile.forceinit();
            IndexFileReplicator.forceinit();
            prta("EQS: Npaths=" + IndexFile.getNPaths());
          }
        }
        state = 6;
        if (now - lastMemoryCheck > 120000) {
          if (MemoryChecker.checkMemory()) {
            Util.exit(3);
          }
          lastMemoryCheck = now;
        }
        state = 7;

        if (dbg) {
          prta(tag + "EQS: Calling accept");
        }
        s = accept();
        if (dbg) {
          prta(tag + "EQS: Accept done s=" + s);
        }
        if (terminate) {
          break;
        }
        nconnect++;
        state = 8;

        // find a thread to assign this to
        boolean assigned = false;
        int loop = 0;
        if (s == null) {
          continue;
        }
        while (!assigned) {
          // look through pool of connections and assign the first null one found
          for (int i = 0; i < handlers.size(); i++) {
            if (handlers.get(i).getSocket() == null) {
              if (!handlers.get(i).isAlive()) {
                state = 9;
                prta(tag + "[" + i + "] EdgeQueryThread is not alive ***** , replace it! running="
                        + handlers.get(i).isRunning() + " " + handlers.get(i).getStatus());
                SendEvent.edgeSMEEvent("EQThrDown", tag + "[" + i + "] is not alive ***** , replace it! ", this);
                handlers.set(i, new EdgeQueryThread(null, readonly, this));
              }
              state = 10;
              handlers.get(i).assignSocket(s);
              if (dbg) {
                prta(tag + "EQS:[" + i + "] Assign socket to " + i + "/" + handlers.size() + "/" + usedThreads + " "
                        + Util.ascdate() + " " + handlers.get(i).getTag() + " " + handlers.get(i).toString());
              }
              assigned = true;
              state = 11;
              break;
            }
          }

          // If we did not assign a connection, time out the list, create a new one, and try again
          state = 12;
          if (!assigned || System.currentTimeMillis() - lastPurge > 60000) {
            state = 13;
            if (!quiet) {
              prta(tag + "EQS: Start looking for connections to free");
            }
            long nw = System.currentTimeMillis();
            lastPurge = nw;
            int nfreed = 0;
            long maxLast = 0;
            usedThreads = 0;
            for (int i = 0; i < handlers.size(); i++) {
              //prta(tag+" check "+i+" "+handlers.get(i));
              EdgeQueryThread thr = handlers.get(i);
              if (thr.getSocket() != null) {
                usedThreads++;
              }
              if (nw - thr.getLastActionMillis() > maxLast) {
                maxLast = nw - thr.getLastActionMillis();
              }
              if (nw - thr.getLastActionMillis() > 600000 && thr.getSocket() != null) {
                thr.closeConnection();
                usedThreads--;
                prta(tag + "EQS: Free connection " + thr.getStatus() + " " + i + " #freed=" + nfreed);
                nfreed++;
              }
            }
            state = 14;
            if (nfreed <= 0) {
              state = 15;
              // If we are under the max limit, create a new one to handle this connection, else, have to wait!
              if (handlers.size() < maxThreads) {
                if (!assigned) { // do not add one if we are only doing a purge pass
                  state = 16;
                  if (!quiet) {
                    prta(tag + "EQS: add a new thread size=" + handlers.size() + " < " + maxThreads + " used=" + usedThreads);
                  }
                  handlers.add(new EdgeQueryThread(null, readonly, this));
                  state = 17;
                }
                state = 18;
              } else {
                state = 19;
                if (loop++ % 20 == 0 && !assigned) {
                  state = 20;
                  prta(tag + "EQS:  ** There is no room for more threads. Size=" + handlers.size() + " maxLast=" + maxLast);
                  SendEvent.edgeSMEEvent("EQSMaxThrds", "No of threads exceeds max=" + maxThreads, this);
                }
                try {
                  sleep(1000);
                } catch (InterruptedException expected) {
                }
              }
            }
            state = 21;
            prta(tag + "EQS: after handler free pass used=" + usedThreads + "/" + handlers.size() + " nfreed=" + nfreed + " maxlast=" + maxLast);
          }
          state = 22;
        } // Until something is assigned
      } catch (IOException e) {
        Util.SocketIOErrorPrint(e, "in CWS setup - aborting", getPrintStream());
      } catch (java.lang.OutOfMemoryError e) {
        state = 23;
        prta(Util.ascdate() + "EQS: ***** out of memory detected for socket = " + ss + " close it up");
        try {
          if (!ss.isClosed()) {
            ss.close();
          }
        } catch (IOException expected) {
        }
        Util.exit(0);
      } catch (RuntimeException e) {
        state = 24;
        prta(tag + "EQS: Runtime passed to EdgeQueryServer2 e=" + e);
        e.printStackTrace(getPrintStream());
        if (s != null) {
          try {
            s.close();
          } catch (IOException expected) {
          }
        }
        SendEvent.edgeSMEEvent("EQSRuntime", "Runtime error in EdgeQueryServer e=" + e, this);
      }
      state = 30;
    }
    state = 31;
    prta(tag + "EQS: try to exit - terminate all handlers");
    for (EdgeQueryThread handler : handlers) {
      handler.terminate();
    }
    state = 32;
    prta(tag + "EQS: run exit..");
    running = false;
  }

  private Socket accept() throws IOException {
    return ss.accept();
  }

  private class ShutdownEdgeQueryThread extends Thread {

    public ShutdownEdgeQueryThread() {
      Util.prta("new EdgeQuery Shutdown " + getName() + " " + getClass().getSimpleName());

    }

    /**
     * this is called by the Runtime.shutdown during the shutdown sequence cause all cleanup actions
     * to occur.
     */
    @Override
    public void run() {
      terminate = true;
      if (ss != null) {
        if (!ss.isClosed()) {
          try {
            ss.close();
          } catch (IOException expected) {
          }
        }
      }

      prta("EQS: EdgeQuery Shutdown Done.");

    }
  }

  public final class CloseFiles extends Thread {

    boolean doitNow;
    int state;

    public int state() {
      return state;
    }

    public void doitNow() {
      doitNow = true;
      interrupt();
      long loop = 0;
      while (doitNow) {
        try {
          sleep(1);
        } catch (InterruptedException expected) {
        }
        if (loop++ > 20000) {
          prta("EQS-CF: doitNow() **** is stuck - this is likely serious as the CloseFiles thread is not running!");
          break;
        }
      }
    }

    public CloseFiles() {
      setDaemon(true);
      start();
    }

    @Override
    public void run() {
      int nclosed;
      while (!terminate) {
        try {
          state = 1;
          prta("EQS-CF: files status=" + IndexFileReplicator.getStateLock()
                  + " #open=" + IndexFileReplicator.getNOpenFiles() + " #thr=" + Thread.activeCount());
          if ((nclosed = IndexFileReplicator.closeJulianLimit(45, 1800000)) > 0) {
            prta("EQS-CF: files " + nclosed + " files closed by julianLimit() #open=" + IndexFileReplicator.getNOpenFiles());
          }
          // close older open files not recently used
          if (IndexFileReplicator.getNOpenFiles() > maxOpenFiles) {
            state = 2;
            prta("EQS-CF: files trim #open=" + IndexFileReplicator.getNOpenFiles());
            IndexFileReplicator.trimFiles(maxOpenFiles * 8 / 10);
            prta("EQS-CF: files trim done #open=" + IndexFileReplicator.getNOpenFiles());
          }
          state = 3;
          IndexFileReplicator.closeStale(86400000);    // very occassionally close unused files
          state = 4;
          prta("EQS-CF: files closeStale() done  #open=" + IndexFileReplicator.getNOpenFiles());
          prta("EQS-CF: files QueryFileThread : " + fileThread.toString());
          doitNow = false;
          state = 5;
          for (int i = 0; i < 12000; i++) {
            try {
              sleep(50);
            } catch (InterruptedException expected) {
            }
            if (doitNow) {
              break;
            }
          }
          state = 6;
        } catch (RuntimeException e) {
          prta("EQS-CF: *** runtime continue e=" + e);
        }
      }
      state = 10;
    }
  }

  /**
   * Unit test main()
   *
   * @param args Unused command line args
   */
  public static void main(String[] args) {
    Util.setModeGMT();
    Util.setNoconsole(false);      // No user dialogs if errors foundrm
    Util.setNoInteractive(true);
    EdgeProperties.init();
    EdgeThread.setMainLogname("queryserver");   // This sets the default log name in edge thread (def=edgemom!)
    Util.debug(false);
    Util.setProcess("QueryServer");
    Util.suppressFile();
    boolean ro = true;
    allowrestricted = false;
    int port = 2061;
    String argline = "";
    for (int i = 0; i < args.length; i++) {
      if (args[i].equals("-p")) {
        port = Integer.parseInt(args[i + 1]);
      }
      if (args[i].equals("-rw")) {
        ro = false;
      }
      if (args[i].equals("-allowrestricted")) {
        allowrestricted = true;
      }
      if (args[i].equals("-mdsip")) {
        mdsServer = args[i + 1];
      }
      if (args[i].equals("-mdsport")) {
        mdsPort = Integer.parseInt(args[i + 1]);
      }
      argline += args[i] + " ";
    }

    IndexFileReplicator.init();
    IndexFile.init();
    EdgeThread.staticprt("Index.path=" + IndexFile.getPathString() + "IndexRep.path=" + IndexFileReplicator.getPathString());
    EdgeChannelServer ecs = null;
    EdgeThread.staticprt("port=" + port + " readonly=" + ro + " allowrestricted=" + allowrestricted + " mds=" + mdsServer + "/" + mdsPort);

    // If we do not allow restricted access, we ned to start a EdgeChannelServer to track restricted stations
    if (!allowrestricted) {
      ecs = new EdgeChannelServer("-empty", "ECS");
      while (!EdgeChannelServer.isValid()) {
        Util.sleep(100);
      }
    }
    EdgeQueryServer server = new EdgeQueryServer(argline.trim(), "EQS");

    for (;;) {
      Util.sleep(10000);
    }
  }
}
