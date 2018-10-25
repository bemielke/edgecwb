/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.alarm;

import gov.usgs.anss.db.DBConnectionThread;
import gov.usgs.anss.db.DBMessageQueuedClient;
import gov.usgs.anss.util.Util;
import gov.usgs.anss.edgethread.EdgeThread;
import gov.usgs.anss.edgethread.MonitorStatus;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.TreeMap;
import java.nio.ByteBuffer;
import java.util.Iterator;

/**
 * The DBMessageServer takes messages of a particular form and creates an SQL statement to insert
 * data into the given database and table,setting the fields given. It used to be run from the
 * POCMySQLServer project, but is now started by alarm since only one of these is needed at any
 * installation. It creates DBMessageHandler for each connection which reads lines of ASCII text of
 * the form:
 * <p>
 * db^table^fields=val;field=val;\n
 * <p>
 * These messages are parsed to form a SQL insert statement thus :
 * <p>
 * INSERT INTO db.table (field1, field2,....) VALUES ("val1","val2",.....);
 * <p>
 * If the first fields=val is id=nnn, then the parsing is for an update :
 * <p>
 * UPDATE db.table SET fields=val,fields=val,fields=val WHERE id=nnn;
 * <p>
 * This class is used so than many Edge/CWB processing running on may nodes and instances can insert
 * data into databases without having a MySQL session open and all the the efforts it takes to keep
 * one open. The primary users of this class are EdgeChannelServer to create new channels, the
 * Q680Socket, and ReftekClient for the same reason. These classes all us the DBMessageQueuedClient
 * to submit data.\
 * <p>
 * The databases allowed are alarm, anss, edge, fetcher, portables, and vdldfc on property DBServer;
 * databases metadata, irserver, digit, inv, qml, webstation on property MetaDBServer database
 * status on StatusDBServer
 * <p>
 * This software was reworked in Jul 2016 and added a new DBConnThread to handle each of the
 * database connections and queue data from each of many possible DBMessageHandler in the
 * DBConnTHread. The queuing has an ArrayList of StringBuilders to hold the fast cache in memory for
 * up to a set limit to memory buffers. When the memory buffers are full, the RandomAccessFile is
 * used to store the incoming SQL. This file puts a two byte size followed by the ASCII SQL
 * statements concatenated together. Once the file is triggered the thread waits for the memory to
 * become empty and then sends out all of the disk based units until it is exhausted. It then sets
 * the files back to zero size and starts putting arriving SQL back into the memory.
 *
 * <PRE>
 * switch     args      Description
 *  -dbmport  port      Override the port to run (default=7985)
 * -dbmpath   path      Path for overflow of queue to disk file DBMsg[DBNAME] (def=/data2)
 * -dbmqsize  nnnn      Change the queue size in memory to nnn (def=1000)
 * </PRE>
 *
 * @author davidketchum
 */
public final class DBMessageServer extends EdgeThread {

  private static final TreeMap<String, DBConnThread> map = new TreeMap<String, DBConnThread>();
  public static boolean isShuttingDown;
  private final ArrayList<DBMessageHandler> handlers = new ArrayList<>(10);
  private int port = 7985;
  private String path;        // the path for the backup disk file (name is DBMsg_$DBNAM)
  private int memoryQsize = 1000;
  private ServerSocket d;
  private int totmsgs;
  private long lastDBStatus;
  private final MonitorStatus statusThread;

  //private DBConnThread dbserver, statusdbserver, metadbserver;
  @Override
  public StringBuilder getMonitorString() {
    Util.clear(monitorsb).append("DBMHandlers=").append(handlers.size()).append("\nDBMMsgs=").append(totmsgs).
            append("\n");
    return monitorsb;
  }

  @Override
  public StringBuilder getStatusString() {
    return Util.clear(statussb).append("#Handlers=").append(handlers.size()).
            append(" #Msg=").append(totmsgs);
  }

  @Override
  public StringBuilder getConsoleOutput() {
    return consolesb;
  }

  @Override
  public void terminate() {
    terminate = true;
    interrupt();
    try {
      if (!d.isClosed()) {
        d.close();
      }
    } catch (IOException expected) {
    }
  }   // cause the termination to begin

  public int getNumberOfMessages() {
    return totmsgs;
  }

  /**
   * Creates a new instance of DBMessageServers
   *
   * @param argline Parameters for starting gthis thread
   * @param tg The logging tag for this edge thread
   */
  public DBMessageServer(String argline, String tg) {
    super(argline, tg);
    String[] args = argline.split("\\s");
    port = 7985;
    path = "/data2";
    for (int i = 0; i < args.length; i++) {
      if (args[i].equalsIgnoreCase("-dbmport")) {
        port = Integer.parseInt(args[i + 1]);
        i++;
      } else if (args[i].equalsIgnoreCase("-dbmpath")) {
        path = args[i + 1];
        i++;
      } else if (args[i].equalsIgnoreCase("-dbmqsize")) {
        memoryQsize = Integer.parseInt(args[i + 1]);
        i++;
      }
    }
    terminate = false;
    statusThread = new MonitorStatus(600000, (DBMessageServer) this);
    // Register our shutdown thread with the Runtime system.
    Runtime.getRuntime().addShutdownHook(new ShutdownDBMessage());
    if (Alarm.isNoDB()) {
      prta(" ***** DBMessageServer will not start because this is a NoDB configuration!");
      SendEvent.edgeSMEEvent("DBMsgNoDB", "A DBMessageServer tried to start in NoDB mode!", (DBMessageServer) this);
      return;
    }
    //new RuntimeException("DBMessage server created!").printStackTrace(getPrintStream());
    running = true;
    start();
  }

  @Override
  public void run() {
    boolean dbg = false;
    long now;
    StringBuilder runsb = new StringBuilder(10000);

    // OPen up a port to listen for new connections.
    int loop = 0;
    while (true) {
      try {
        //server = s;
        if (terminate) {
          break;
        }
        prta(Util.clear(runsb).append("DBMsgSrv: Open Port=").append(port));
        d = new ServerSocket(port);
        break;
      } catch (SocketException e) {
        if (e.getMessage().equals("DBMsgSrv:Address already in use")) {
          try {
            prta("DBMsgSrv: Address in use - try again.");
            Thread.sleep(2000);
          } catch (InterruptedException Expected) {

          }
        } else {
          prta(Util.clear(runsb).append("DBMsgSrv:Error opening TCP listen port =").append(port).append("-").append(e.getMessage()));
          try {
            Thread.sleep(2000);
          } catch (InterruptedException Expected) {

          }
        }
      } catch (IOException e) {
        prta(Util.clear(runsb).append("DBMsgSrv:Error opening socket server=").append(e.getMessage()));
        try {
          Thread.sleep(2000);
        } catch (InterruptedException Expected) {
        }
        if (loop++ > 120) {
          Util.clear(runsb).append("DBMsgSrv: Panic - cannot open server port");
          SendEvent.edgeSMEEvent("DBMsgSrvBad", "Cannot open port=" + port + " in DBMsgSrv", this);
          Util.exit("Cannot open server");
        }
      }
    }
    while (true) {
      if (terminate) {
        break;
      }
      try {
        Socket s = d.accept();
        if (terminate) {
          break;
        }
        prta(Util.clear(runsb).append(Util.ascdate()).append(" DBMsgSrv: from ").append(s).
                append(" rcvbuf=").append(s.getReceiveBufferSize()).append(" size=").append(handlers.size()));
        if (s.getReceiveBufferSize() < 40960) {
          s.setReceiveBufferSize(40960);
        }
        DBMessageHandler tmp = new DBMessageHandler(s); // create a new handler
        now = System.currentTimeMillis();
        for (int i = 0; i < handlers.size(); i++) {        // either replace a dead one or add it to the list
          if (!handlers.get(i).isAlive() || now - handlers.get(i).getLastMessageTime() > 86400000L) {
            prta(Util.clear(runsb).append("DBMsgSrv: Replacing hander at i=").append(i).append(" ").
                    append(handlers.get(i).toStringBuilder(null)).append(" alive=").append(handlers.get(i).isAlive()));
            handlers.get(i).terminate();
            handlers.add(i, tmp);
            tmp = null;
          }
          break;
        }
        if (tmp != null) {
          handlers.add(tmp);    // its a new one to a full list
        }
        for (DBMessageHandler t : handlers) {
          if (t != null) {
            prta(Util.clear(runsb).append(t.toStringBuilder(null)).append(" alive=").append(t.isAlive()));
          }
        }
        synchronized (map) {
          Iterator itr = map.values().iterator();
          while (itr.hasNext()) {
            prta(itr.next().toString());
          }
        }
      } catch (IOException e) {
        prta("DBMsgSrv:receive through IO exception. continue; e=" + e);
      } catch (RuntimeException e) {
        prta("DBMsgSrv: Runtime e=" + e);
        e.printStackTrace(getPrintStream());
      }
    }       // end of infinite loop (while(true))
    //prta("Exiting DBMessageServers run()!! should never happen!****\n");
    prta("DBMsgSrv:read loop terminated.  Stopping handlers");
    for (DBMessageHandler handler : handlers) {
      if (handler != null) {
        handler.terminate();
      }
    }
    running = false;
    statusThread.terminate();
  }

  private class ShutdownDBMessage extends Thread {

    public ShutdownDBMessage() {

    }

    /**
     * this is called by the Runtime.shutdown during the shutdown sequence cause all cleanup actions
     * to occur
     */
    @Override
    public void run() {
      terminate = true;
      isShuttingDown = true;
      interrupt();
      if (d != null) {
        try {
          d.close();
        } catch (IOException expected) {
        }
      }
      prta("DBMsgSrv: Shutdown started");
      int nloop = 0;
      prta("DBMsgSrv:Shutdown Done.");
    }
  }

  /**
   * A new connection from a sender creates one of these when the connection is made and hands off
   * the socket. This thread creates DBConnThread inner class instance for each database connection
   * needed if it does not yet exist (TreeMap of DBConnThread is kept to look up the connection).
   * The original messages are parsed and become either a UPDATE or INSERT SQL statement depending
   * on whether and ID is given in the first field. These SQL statements are then queued to the
   * DBConnThread connect to the right Database.
   */
  private final class DBMessageHandler extends Thread {

    private final Socket s;
    private final String tag;
    private long lastMessage = System.currentTimeMillis();
    private long nmsgs;
    private int maxavail;
    private boolean closing;
//DBMessageServerWatchdog watchdog;
    private DBConnThread dbconn;        // The currently selected DBConn for this handler

    public void terminate() {
      closing = true;
      if (s != null) {
        if (!s.isClosed()) {
          try {
            s.close();
          } catch (IOException expected) {
          }
        }
      }
    }

    public long getLastMessageTime() {
      return lastMessage;
    }

    @Override
    public String toString() {
      return toStringBuilder(null).toString();
    }

    public StringBuilder toStringBuilder(StringBuilder sb) {
      if (sb == null) {
        sb = Util.getPoolSB();
      }
      Util.clear(sb).append(tag).append(" #msgs=").append(nmsgs).append(" maxavail=").append(maxavail).
              append(" ").append((System.currentTimeMillis() - lastMessage) / 1000).append(" s ago");
      return sb;
    }

    /**
     *
     * @param ss Socket
     */
    public DBMessageHandler(Socket ss) {
      s = ss;
      tag = "DBMH[" + ss.getInetAddress().toString() + "/" + ss.getPort() + "]";
      //watchdog = new DBMessageServerWatchdog(tag, s);
      setDaemon(true);
      start();
    }

    @Override
    public void run() {
      byte[] ack = new byte[3];
      ack[0] = 'O';
      ack[1] = 'K';
      ack[2] = '\n';
      long lastNmsg = 0;
      try {
        OutputStream out = s.getOutputStream();
        BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream()));
        StringBuilder attr = new StringBuilder(1000);
        StringBuilder vals = new StringBuilder(1000);
        long statusTime = System.currentTimeMillis();
        while (!terminate) {
          try {
            // body of Handler goes here
            //while(s.getInputStream().available() <= 0 && !terminate && !s.isClosed()) try{sleep(50); } catch(InterruptedException expected) {}
            if (s.getInputStream().available() > maxavail) {
              maxavail = s.getInputStream().available();
            }
            if (terminate) {
              break;
            }
            // read the line of the form database^tablename^field=value;field2=value;
            String line = in.readLine();
            lastMessage = System.currentTimeMillis();
            out.write(ack);             // send the ack
            if (line == null) {
              break;     // EOF found, exit
            }            // check for reasonable ASCII - if it does not have it, then them message is probably a probe - ignore it
            for (int i = 0; i < line.length(); i++) {
              if (line.charAt(i) < ' ' || line.charAt(i) > '~') {
                prta(tag + "DBMgsSrv: Input includes binary data - reject it and close socket. line=" + Util.toAllPrintable(line));
                SendEvent.edgeSMEEvent("POCBinData", "Binary data received from s=" + s, this);
                closing = true;     // This handler must end.
                break;
              }
            }
            if (terminate | closing) {
              break;
            }
            nmsgs++;
            long now = System.currentTimeMillis();
            if (now - statusTime > 900000) {
              statusTime = now;
              prta(Util.ascdate() + " " + tag + " DBMsgSrv: nmsg=" + nmsgs + "/" + (nmsgs - lastNmsg) + " maxavail=" + maxavail + " s=" + s.getInetAddress() + "/" + s.getPort());
              lastNmsg = nmsgs;
              if (now - lastDBStatus > 3600000) {
                prta(DBConnectionThread.getStatus());
                lastDBStatus = now;
              }
            }
            String[] fields;
            boolean isSQL = false;
            //prta(tag+nmsgs+"="+line);
            if (line.startsWith("INSERT INTO ")) {
              isSQL = true;
              fields = line.substring(12).split("\\.");
            } else if (line.startsWith("UPDATE ")) {
              isSQL = true;
              fields = line.substring(7).split("\\.");
            } else {
              if (line.contains("edge^channel^channel")) {
                prta(tag + "DBMsgSrv: ** create channel line = " + line);
              }
              fields = line.split("\\^");
              if (fields.length != 3) {
                prta(tag + "DBMsgSrv: line is not in db^table^fields=val;..... format.  skip=" + Util.toAllPrintable(line));
                SendEvent.edgeSMEEvent("POCBadData", "Line in wrong form not db^table^fields s=" + s, this);
                continue;
              }
            }

            // get the DBConnThread for this database.  If thisis null, then create a new one.
            synchronized (map) {
              dbconn = map.get(fields[0]);
            }
            if (dbconn == null) {
              // This support the division of the databases onto 3 servers.  Check for DBServer databases
              if (fields[0].equals("edge") || fields[0].equals("anss")
                      || fields[0].equals("alarm") || fields[0].equals("portables")
                      || fields[0].equals("fetcher") || fields[0].equals("vdldfc")
                      || fields[0].equals("fk")) {
                dbconn = new DBConnThread("DBServer", fields[0], tag, path);
              } // Check for MetaDBServer databases
              else if (fields[0].equals("metadata") || fields[0].equals("irserver")
                      || fields[0].equals("digit") || fields[0].startsWith("inv")
                      || fields[0].equals("qml") || fields[0].equals("webstation") || fields[0].equals("pgm")) {
                dbconn = new DBConnThread("MetaDBServer", fields[0], tag, path);
              } // Check for StatusDBServer databases
              else if (fields[0].equals("status")) {
                dbconn = new DBConnThread("StatusDBServer", fields[0], tag, path);
              } else {
                SendEvent.edgeSMEEvent("DBMsgBadDB", "Database " + fields[0] + " is not handled by DBMessageServer!", this);
                prta(tag + " **** Database not handled : " + fields[0] + " line=" + line);
              }
            }
            if (dbconn == null) {
              SendEvent.edgeSMEEvent("DBMsgsSrvBad", "Did not open connection to DBMsgSrv-" + tag + fields[0], this);
              continue;
            }
            Util.clear(attr);
            Util.clear(vals);
            if (isSQL) {
              attr.append(line);
              dbconn.queue(attr);
              continue;
            }
            // The database is open, write data into it

            if (fields[2].startsWith("id=")) {
              attr.append("UPDATE ").append(fields[0]).append(".").append(fields[1].trim()).append(" SET ");
              String[] pairs = fields[2].split("[;]");
              int id = -1;
              for (int i = 0; i < pairs.length; i++) {
                if (pairs[i].startsWith("id=")) {
                  id = Integer.parseInt(pairs[i].substring(3).trim());
                } else {
                  String[] vars = pairs[i].split("=");
                  attr.append(vars[0].trim()).append("=");
                  if (vars.length <= 1) {
                    attr.append("''");
                  } else {
                    if (vars[1].contains("current_timestamp()") || vars[1].contains("now()") ||
                            vars[1].contains("<<") || vars[1].contains(vars[0])) {
                      attr.append(vars[1].trim());
                    } else {
                      attr.append("'").append(vars[1].trim()).append("'");
                    }
                  }
                  attr.append(i < pairs.length - 1 ? "," : "");   // add a comma if not the last one.
                }
              }
              attr.append(" WHERE id=").append(id);
              dbconn.queue(attr);
            } else {
              attr.append("INSERT INTO ").append(fields[0]).append(".").append(fields[1].trim()).append(" (");
              vals.append("(");
              String[] pairs = fields[2].split("[;]");
              for (int i = 0; i < pairs.length; i++) {
                String[] vars = pairs[i].split("=");
                attr.append(vars[0].trim());
                if (vars.length <= 1) {
                  vals.append("''");
                } else {
                  if (vars[1].contains("current_timestamp()") || vars[1].contains("now()")) {
                    vals.append(vars[1].trim());
                  } else {
                    vals.append("'").append(vars[1].trim()).append("'");
                  }
                }
                if (i < pairs.length - 1) {
                  attr.append(",");
                  vals.append(",");
                }
              }
              attr.append(") VALUES ");
              vals.append(")");
              if (nmsgs % 100 == 0 || attr.indexOf("INTO channel") >= 0) {
                prta(tag + nmsgs + " " + attr.toString().substring(0, 26) + vals.toString());
              }
              dbconn.queue(attr.append(vals));
            }
          } catch (IOException e) {
            prta(tag + " IOError on socket2 closing=" + closing + " e=" + e);
            e.printStackTrace(getPrintStream());
            break;
            //Util.IOErrorPrint(e,"DBMsgSrv: IOError on socket");
          } catch (RuntimeException e) {
            prta(tag + " runtime error - continuing e=" + e);
            e.printStackTrace(getPrintStream());
            break;
          }
        }     // while(!terminate)
      } catch (IOException e) {    // These should never be executed because th in loop ones should keep it right
        prta(tag + " IOError on socket e=" + e);
        e.printStackTrace(getPrintStream());
        //Util.IOErrorPrint(e,"DBMsgSrv: IOError on socket");
      } catch (RuntimeException e) {
        prta(tag + " runtime error - exiting e=" + e);
        e.printStackTrace(getPrintStream());
      }
      prta(tag + " DBMessageHandler has exit after " + nmsgs + " maxavail=" + maxavail + " msgs on s=" + s);
      if (s != null) {
        if (!s.isClosed()) {
          try {
            s.close();
          } catch (IOException e) {
            prta(tag + " IOError closing socket");
          }
        }
      }
    }
  }

  private final class DBConnThread extends Thread {

    private DBConnectionThread dbconn;
    private String server;
    private final String tag;
    private final String db;
    private long lastSetLog = System.currentTimeMillis() / 86400000L;

    // Disk read back related
    private RandomAccessFile dsk;
    private boolean emptyingDisk = false;     // If true, the disk is being emptied.
    private final ByteBuffer bbsize;       // one size
    private byte[] size = new byte[2];
    private final ByteBuffer bbsizein;       // one size
    private byte[] sizein = new byte[2];
    private byte[] buf = new byte[512];    // scratch space
    private byte[] bufin = new byte[512];
    private final StringBuilder sb = new StringBuilder(100);

    // Queue related 
    private final ArrayList<StringBuilder> queue = new ArrayList<StringBuilder>(10);  // active in queue
    private final ArrayList<StringBuilder> free = new ArrayList<StringBuilder>(10);   // created but freed from queue
    private long diskOutPointer = 0;
    private long diskOutMB = 0;                 // track 100 kBytes into file for logging

    // status
    private long nsql;
    private long ndisk;
    private long nsqlerr;
    private int maxqueue;
    private boolean dbg = false;
    private StringBuilder runsb = new StringBuilder(100);

    @Override
    public String toString() {
      return server + "." + db + " #sql=" + nsql + " #disk=" + ndisk + " #sqlerr=" + nsqlerr
              + " #memqueue=" + queue.size() + " #free=" + free.size();
    }

    public DBConnThread(String server, String db, String tag, String path) {
      this.server = server.trim();
      this.db = db.trim();
      this.tag = "DBMDCT-" + tag.trim() + ":";
      synchronized (map) {
        map.put(this.db, (DBConnThread) this);
      }
      try {
        dsk = new RandomAccessFile(path.trim() + Util.fs + "DBMsg_" + db + ".sql", "rw");
        diskOutPointer = dsk.length();        // If the file is not zero length, some data may need to be inserted
        if (diskOutPointer > 0) {
          emptyingDisk = true;
        }
      } catch (IOException e) {
        prta(this.tag + " cannot open dsk file e=" + e);
      }
      bbsize = ByteBuffer.wrap(size);
      bbsizein = ByteBuffer.wrap(sizein);
      openDB();
      setDaemon(true);
      start();
    }

    @Override
    public void run() {
      long timer = 30000;
      long diskPointer = 0;
      int len;
      //try{sleep(60000);} catch(InterruptedException expected) {}
      prta(tag + " Starting run");
      long lastStatus = System.currentTimeMillis();
      long lastNdisk = 0;
      long lastNsql = 0;
      String dbname = Util.rightPad(db, 10).toString();
      while (!terminate) {
        // If in disk mode, read the next packet from the files (2 byte size then data)

        if (emptyingDisk) {
          try {
            synchronized (bbsize) {
              dsk.seek(diskPointer);
              dsk.read(size, 0, 2);
              bbsize.position(0);
              len = bbsize.getShort();
              if (buf.length < len) {
                buf = new byte[2 * len];
                prta(Util.clear(runsb).append(tag).append(" ** increase disk read buffer size to ").append(len * 2));
              }
              dsk.read(buf, 0, len);
            }

            // Convert the SQL into a StringBuilder from raw bytes.
            Util.clear(sb);
            for (int i = 0; i < len; i++) {
              sb.append((char) buf[i]);
            }
            try {
              dbconn.executeUpdate(sb.toString());        // execute database update
              // the SQL worked, advance the disk pointer
              if (dbg || ndisk % 1000 == 0 || sb.indexOf("INSERT INTO edge.channel") >= 0) {
                prta(tag + " * execute from disk q.size=" + queue.size() + " " + diskOutPointer + "/" + diskPointer
                        + " #sql=" + nsql + " #disk=" + ndisk + " " + sb);
              }
              nsql++;
              ndisk++;
              diskPointer += len + 2;
              synchronized (bbsize) {
                if (diskPointer >= diskOutPointer) { // Have we emptied the disk
                  prta(Util.clear(runsb).append(tag).append("** execute disk completed.  zero the file queue.size=").append(queue.size()).
                          append(" max=").append(diskOutPointer).append(" #sql=").append(nsql).append(" #disk=").append(ndisk));
                  // no longer in disk mode, zero the file and both pointers
                  emptyingDisk = false;
                  dsk.setLength(0L);
                  diskPointer = 0;
                  diskOutPointer = 0;
                }
              }
              continue;
            } catch (SQLException e) {
              diskPointer += len + 2;
              nsqlerr++;
              dbconn.close();
              try {
                sleep(timer);
              } catch (InterruptedException expected) {
              }
              timer = Math.min(600000, timer * 2);
              openDB();
            } catch (RuntimeException e) {
              e.printStackTrace(getPrintStream());
              diskPointer += len + 2;
            }
            continue;

          } catch (IOException e) {
            prta("DBMsgDBT: IOException on disk e=" + e);
            e.printStackTrace(getPrintStream());
          }
        }

        // If we get here, the disk is not in use
        if (queue.isEmpty()) {
          // If the queue is empty and there is stuff on the disk, switch to emptying the disk (any incoming data is going to disk)
          if (diskOutPointer > 0) {    // Its time to empty the disk
            diskPointer = 0;
            emptyingDisk = true;
            continue;
          }
          // Disk is not in use, take a break
          if (System.currentTimeMillis() - lastStatus > 60000) {
            prta(Util.clear(runsb).append(tag).append(dbname).append("MEM: qsize=").append(queue.size()).
                    append(" maxq=").append(maxqueue).
                    append(" #sql=").append(nsql).append("/").append(nsql - lastNsql).
                    append(" DISK: #disk=").append(ndisk).append("/").append(ndisk - lastNdisk).
                    append(" dpnt=").append(diskPointer).append("/").append(diskOutPointer));
            lastStatus = System.currentTimeMillis();
            lastNdisk = ndisk;
            lastNsql = nsql;
            maxqueue = 0;
          }
          try {
            sleep(500);
          } catch (InterruptedException expected) {
          }
          if (nsql % 100 == 0) {
            if (System.currentTimeMillis() / 86400000L != lastSetLog) {
              dbconn.setLogPrintStream(getPrintStream());
              prta(Util.clear(runsb).append(tag).append(" Setting the print log for ").append(dbname));
              lastSetLog = System.currentTimeMillis() / 86400000L;
            }
          }
        } // The queue is not empty so dequeue the next element
        else {
          if (dbconn.isOK()) {         // is the database connection up?
            for (int itry = 0; itry < 3; itry++) {
              try {
                if (dbg || nsql % 1000 == 0 || sb.indexOf("INSERT INTO edge.channel") >= 0) {
                  prta(Util.clear(runsb).append(tag).append(" * Execute from memory queue.size=").append(queue.size()).
                          append(" #sql=").append(nsql).append(" #disk=").append(ndisk).append(" ").append(queue.get(0)));
                }
                dbconn.executeUpdate(queue.get(0).toString());  // execute SQL, any error cause connect to be remade
                // the SQL excecuted so its safe to dequeu
                nsql++;
                synchronized (queue) {
                  //try{sleep(500);} catch(InterruptedException expected) {}  //debug, slow memory emptying
                  free.add(queue.get(0));           // add this to free list
                  queue.remove(0);                  // remove it from the memory queue
                }
                timer = 30000;
                break;     // Go get the next one
              } catch (SQLException e) {
                prta(Util.clear(tmpsb).append(tag).append(" SQL error occurred. reopen and continue e=").append(e));
              } catch (RuntimeException e) {
                e.printStackTrace();
              }
              if (itry == 2) {
                prta(Util.clear(tmpsb).append(tag).
                        append(" **** Some error cause queue entry not to execute 3 times.  Skip  it ").append(queue.get(0)));
                SendEvent.edgeSMEEvent("DBMsgSQLErr", "Some error hanging loop ", this);
                synchronized (queue) {
                  free.add(queue.get(0));           // add this to free list
                  queue.remove(0);                  // remove it from the memory queue
                  break;                            // leave loop to panic
                }
              }
            }
            continue;
          }
          // To get here either an SQLException was thrown or there was no connection.  In any case close and reopen the connection
          dbconn.close();
          if (dbg) {
            prta(Util.clear(tmpsb).append(tag).append(" ***DBConn has to be restarted.  time=").append(timer));
          }
          try {
            sleep(timer);
          } catch (InterruptedException expected) {
          }
          timer = Math.min(600000, timer * 2);
          openDB();
        }
      }
      prta(tag + " is exiting queue.size() = " + queue.size());
    }

    /**
     * Open up a new connection to the database. This uses the server thread, and db set at creation
     * of this object.
     *
     */
    private void openDB() {
      dbconn = DBConnectionThread.getThread("DBMsgSrv-" + db.trim());
      if (dbconn != null) {
        prta(tag + " ***** Why was reopened called with an existing connection? " + tag + db);
        dbconn.close();
      }
      // This support the division of the databases onto 3 servers
      //watchdog.arm(true, dbconn);
      try {
        prta(Util.clear(runsb).append(tag).append(" Create new DBConn opening to ").append(server).append(" ").append(db));
        dbconn = new DBConnectionThread(Util.getProperty(server), "update", db,
                true, false, "DBMsgSrv-" + db, getPrintStream());
        if (!DBConnectionThread.waitForConnection("DBMsgSrv-" + db)) {
          if (!DBConnectionThread.waitForConnection("DBMsgSrv-" + db)) {
            if (!DBConnectionThread.waitForConnection("DBMsgSrv-" + db)) {
              if (!DBConnectionThread.waitForConnection("DBMsgSrv-" + db)) {
                if (!DBConnectionThread.waitForConnection("DBMsgSrv-" + db)) {
                  prta(tag + "DBMsgDBT: did not promptly connect DBMsgSrv-" + db);
                }
              }
            }
          }
        }
      } catch (InstantiationException e) {
        prta(Util.clear(runsb).append(tag).append(" Instantiation error on db=").append(db).
                append(" tag=DBMsgSrv-").append(db).append(" impossible"));
        dbconn = DBConnectionThread.getThread("DBMsgSrv-" + db.trim());
      }

      //watchdog.arm(false, dbconn);
    }
    private StringBuilder tmpsb = new StringBuilder(10);

    public synchronized void queue(StringBuilder sql) {
      // check to see if we are in disk mode, if not, put SQL in memory 
      boolean dbgsave = dbg;
      if (sql.indexOf("INSERT INTO edge.channel") >= 0) {
        dbg = true;
        prta(Util.clear(tmpsb).append(tag).append(" * adding a channel! =").append(sql));
      }
      if (queue.size() < memoryQsize && diskOutPointer == 0) {
        synchronized (queue) {
          if (free.isEmpty()) {
            queue.add(new StringBuilder(sql.length()).append(sql));   // Create a new StringBuilder
          } // add a new string builder
          else {
            queue.add(Util.clear(free.get(free.size() - 1)).append(sql)); // reuse one from the free list
            free.remove(free.size() - 1);                                 // take it off the free list
          }
          maxqueue = Math.max(queue.size(), maxqueue);
          if (dbg) {
            prta(Util.clear(tmpsb).append(tag).append(" * Add to memory q.size=").append(queue.size()).
                    append(" ").append(sql));
          }
        }
      } else {      // Queue is full or we are in disk mode, need to put this on disk
        bbsizein.position(0);
        bbsizein.putShort((short) sql.length());
        if (bufin.length < sql.length()) {
          bufin = new byte[2 * sql.length()];
          prta(Util.clear(tmpsb).append(tag).append(" ** increase buffer size to ").append(sql.length() * 2).
                  append(" sql=").append(sql));
        }
        Util.stringBuilderToBuf(sql, bufin);
        if (dbg || diskOutMB != diskOutPointer / 100000) {
          prta(tag + " add to disk " + diskOutPointer + " " + sql);
          diskOutMB = diskOutPointer / 100000;
        }
        // put the 2 byte size and SQL at end of disk file
        synchronized (bbsize) {
          try {
            dsk.seek(diskOutPointer);
            dsk.write(sizein, 0, 2);
            dsk.write(bufin, 0, sql.length());
            diskOutPointer += sql.length() + 2;
          } catch (IOException e) {
            prta(Util.clear(tmpsb).append(tag).append(" IO error e=").append(e));
            e.printStackTrace(getPrintStream());
          }
        }
      }
      dbg = dbgsave;
    }
  }

  /**
   * @param args the command line arguments
   */
  public static void main(String[] args) {
    Util.prt(Util.asctime());
    Util.setModeGMT();
    Util.setNoInteractive(true);
    Util.prt(Util.asctime());
    //ServerThread server = new ServerThread(AnssPorts.PROCESS_SERVER_PORT, false);

    // This is now a test routine
    //String[] db = {"edge^channel", "status^latency", "metadata"};
    String seedname = "ZZDAVE HHZ10";
    double rate = 40.;
    StringBuilder ch = new StringBuilder(100);
    ch.append("edge^channel^channel=").append(seedname).
            append(";commgroupid=0;operatorid=0;protocolid=0;sendto=0;flags=0;links=0;delay=0;sort1=new;sort2=;rate=").
            append(rate).append(";nsnnet=0;nsnnode=0;nsnchan=0;created_by=0;created=current_timestamp();\n");
    StringBuilder chupdate = new StringBuilder(100);
    chupdate.append("edge^channel^id=1000;lastdata=2015-07-20 00:01:03;protocolid=1;updated=now();\n");
    DBMessageServer dbserver = new DBMessageServer("-path /data2 >>dbserver", "DBSRV");
    Util.sleep(500);
    DBMessageQueuedClient dbclient = new DBMessageQueuedClient("localhost", 7985, 500, 300, null);
    dbclient.queueDBMsg(ch);
    Util.sleep(3000);
    dbclient.queueDBMsg(chupdate);
    for (int i = 0; i < 10000; i++) {
      rate += 0.01;
      String num = Util.leftPad("" + i, 5).toString().replaceAll(" ", "0");
      if (num.length() > 5) {
        Util.prt("Num is too big =" + num + "|");
      }
      seedname = seedname.substring(0, 7) + num;
      Util.clear(ch).append("edge^channel^channel=").append(seedname).
              append(";commgroupid=0;operatorid=0;protocolid=0;sendto=0;flags=0;links=0;delay=0;sort1=new;sort2=;rate=").
              append(rate).append(";nsnnet=0;nsnnode=0;nsnchan=0;created_by=0;created=current_timestamp();\n");
      Util.clear(chupdate).append("edge^channel^id=1000;lastdata=2015-07-20 00:01:03;protocolid=").append(i % 128).append(";updated=now();\n");
      dbclient.queueDBMsg(ch);
      dbclient.queueDBMsg(chupdate);
      Util.sleep(5);
      if (i % 250 == 0) {
        Util.prta("i=" + i + " " + seedname + " " + dbclient.toString());
      }
    }
    Util.sleep(1000000);
    String argline = "";
    //try  {
    for (String arg : args) {
      argline += arg + " ";
      if (arg.equals("-?") || arg.indexOf("help") > 0) {
        Util.prt("-p nnnn Set port name to something other than 7984");
        Util.prt("-?            Print this message");
        System.exit(0);
      }
    }

    DBMessageServer t = new DBMessageServer(argline + " dbmsgsrv", "DBMS");
    int loop = 0;
    for (;;) {
      Util.sleep(10000);
      if (loop % 30 == 0) {
        Util.prta(DBConnectionThread.getStatus());
      }
    }

  }

}
