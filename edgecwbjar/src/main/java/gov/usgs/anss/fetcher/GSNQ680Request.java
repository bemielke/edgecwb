/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.fetcher;

import gov.usgs.alarm.SendEvent;
import gov.usgs.anss.db.DBConnectionThread;
import gov.usgs.anss.edge.FetchList;
import gov.usgs.anss.edge.IllegalSeednameException;
import gov.usgs.anss.edgethread.EdgeThread;
import gov.usgs.anss.seed.MiniSeed;
import gov.usgs.anss.util.SimpleSMTPThread;
import gov.usgs.anss.util.Util;
import gov.usgs.edge.config.RoleInstance;
import gov.usgs.edge.config.EdgeStation;
import gov.usgs.edge.config.RequestType;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.GregorianCalendar;

/**
 * retrieve a time series segment from a Q680 style request handler (on GSN equipment). This is
 * likely obsolete as all such equipment has been retired by the GSN.
 *
 * @author U.S. Geological Survey  <ketchum at usgs.gov>
 */
public final class GSNQ680Request extends Fetcher {

  private byte[] b;
  private ByteBuffer buf;
  private final ArrayList<MiniSeed> mss = new ArrayList<>(1000);
  private GSNReaderTimeout timeout;
  private boolean dbg;
  private String line;
  private String status;
  private boolean running;
  //private FetchList fetch;
  private int totalBytes;
  private boolean inReader;
  private boolean inThrottle;
  private boolean inAvail;
  static long lastMismatch;

  private Socket s;

  //private boolean terminate;
  @Override
  public void closeCleanup() {
  }

  public String toStringDetail() {
    return "GNSReq" + host + "/" + port + "  run=" + running + " " + status;
  }

  public String getStatusString() {
    return "LOG: " + status.replaceAll("\r", "\nLOG:") + "\n";
  }

  /**
   * return the array list of MiniSeed blocks returned as a result
   *
   *
   * @return The array list of miniseed. Some of the indices may contain null where duplicates were
   * eliminated)
   */
  public ArrayList<MiniSeed> getResults() {
    return mss;
  }

  /**
   * shutdown this connection to the server
   */
  @Override
  public void terminate() {
    prta("GSNQ680 terminate called.");
    terminate = true;
    interrupt();
    if (s != null) {
      if (!s.isClosed()) {
        try {
          s.close();
        } catch (IOException e) {
        }
      }
    }
  }

  public ArrayList<MiniSeed> waitFor() {
    while (running) {
      Util.sleep(100);
    }
    prt("returned mss =" + mss);
    return mss;
  }

  public GSNQ680Request(String[] args) throws UnknownHostException, IOException, SQLException {
    super(args);
    for (String arg : args) {
      if (arg.equals("-dbg")) {
        dbg = true;
      }
    }
    //if(dbg)
    if (!singleRequest) {
      synchronized (edgedbMutex) {
        ResultSet rs = dbconnedge.executeQuery("SELECT * FROM gsnstation WHERE gsnstation regexp '"
                + (getSeedname() + "       ").substring(0, 7).trim() + "'");
        if (rs.next()) {
          prta(getSeedname() + " gsnstation gives IP=" + rs.getString("requestIP"));
          if (rs.getString("requestIP").trim().equals("")) {
            prta("  ** No address for requests.  " + getSeedname() + " do not start.");
            host = "";
          } else {
            host = Util.cleanIP(rs.getString("requestip"));
            port = rs.getInt("requestport");
          }
        } else {
          throw new UnknownHostException("GSNQ680Request could not find host for " + getSeedname());
        }
        prta("Open socket to " + host + "/" + port + " dbg=" + dbg);
        timeout = new GSNReaderTimeout(this);
      }
    }
  }

  public boolean isRunning() {
    return running;
  }

  @Override
  public ArrayList<MiniSeed> getData(FetchList fetch) {
    checkNewDay();
    try {
      b = new byte[4096];
      running = true;
      mss.clear();
      GregorianCalendar g = new GregorianCalendar();
      g.setTimeInMillis(fetch.getStart().getTime() + fetch.getStartMS());
      boolean nodata = false;
      //MiniSeed ms = null;
      double dur = fetch.getDuration();
      int inbytes = 0;
      long startms = System.currentTimeMillis();
      boolean eof = false;
      boolean processNow = false;
      if (host.equals("")) {
        try {
          sleep(30000);
        } catch (InterruptedException expected) {
        }
        prta(" * No host for request " + fetch.getSeedname() + " start=" + fetch.getStart() + " dur=" + fetch.getDuration());
        return mss;
      }
      while (dur > 0.02 && mss.size() < 12000 && !terminate) {
        while (s == null || s.isClosed()) {
          readRequestStation();
          try {
            prta(Util.ascdate() + " Open socket to " + host + "/" + port + " for " + fetch.getSeedname()
                    + " start=" + fetch.getStart() + " dur=" + fetch.getDuration());
            s = new Socket();
            s.setReceiveBufferSize(4096);
            InetSocketAddress adr = new InetSocketAddress(host, port);
            s.setSoLinger(false, 10);
            s.connect(adr);
            //if(dbg) 
            //try{sleep(10000);} catch(InterruptedException e) {}
            prta(tag + "socket2 opened rcv=" + s.getReceiveBufferSize()
                    + " keep=" + s.getKeepAlive() + " nodelay=" + s.getTcpNoDelay() + " soTimeout=" + s.getSoTimeout()
                    + " linger=" + s.getSoLinger());
          } catch (UnknownHostException e) {
            prta("Got unknown host wait 120... " + host + "/" + port);
            try {
              sleep(120000);
            } catch (InterruptedException e2) {
            }
            s = null;
            return mss;
          } catch (IOException e) {
            prta("Got IOError opening socket. Wait 120... " + host + "/" + port + " e=" + e);
            s = null;
            try {
              sleep(120000);
            } catch (InterruptedException expected) {
            }
            return mss;
          }
        }
        String seed = fetch.getSeedname();

        line = "DATREQ " + seed.substring(2, 7).trim() + "." + (seed.length() < 12 ? "" : seed.substring(10, 12)) + "-" + seed.substring(7, 10) + " "
                + Util.ascdate(g) + " " + Util.asctime(g).substring(0, 8) + " " + ((int) (Math.min(10800., dur + 0.99))) + "\n"; //10801
        dur -= Math.min(10800., dur);//10800
        g.setTimeInMillis(g.getTimeInMillis() + (long) (Math.min(10800., dur) * 1000.));// 10800
        try {
          prta("dur=" + dur + " #bytes=" + inbytes + " mss.size=" + mss.size() + " line=" + line.substring(0, line.length() - 1));
          s.getOutputStream().write((line + "\n").getBytes());
        } catch (IOException e) {
          prta("Got IOEError writing command or reading socket e=" + e.toString());
        }
        eof = false;
        while (!eof && !terminate) {
          try {
            int l = readFully(s, b, 0, 512);    // Dynamic throttle is in readFully
            if (l <= 0) {
              prta("ReadFully returned EOF=" + l);
              eof = true;
              s.close();
              break;     // EOF found
            }
            status = "No Status EOF or Reset?";
            if (b[15] == 'L' && b[16] == 'O' && b[17] == 'G' && b[13] == 'R' && b[14] == 'Q') {
              status = new String(b, 64, 200).trim();
              nodata = status.contains("No data was available");
              if (dbg) {
                prt("\n" + status.replaceAll("\r", "\nLOG:") + "\n" + " size=" + mss.size() + "<EOL>");
              }
              status = status.replaceAll("\r", "\n");
              eof = true;
              inbytes += 4096;
              s.close();
              break;
            }
            inbytes += 512;
            int size = MiniSeed.crackBlockSize(b);
            status = status + " size=" + size;
            if (size > 512) {
              if (b.length < size) {
                byte[] tmp = b;
                b = new byte[size];
                System.arraycopy(tmp, 0, b, 0, tmp.length);
              }
              l = readFully(s, b, 512, size - 512);   // Note: dynamic throttle is done here every 512 bytes
              if (l <= 0) {
                prta("readFully2 returned EOF=" + l);
                eof = true;
                s.close();
                break;
              }
            }
            inbytes += (size - 512);
            // Build a new SAC time series and fill it with data
            MiniSeed ms = new MiniSeed(b);
            if (!ms.getSeedNameString().substring(0, 10).equals(seed.substring(0, 10))) {
              prta("Got a channel name mismatch for " + seed + " got " + ms.getSeedNameString() + " last=" + (System.currentTimeMillis() - lastMismatch));
              SendEvent.edgeSMEEvent("Q680RqMatch", "Q680 fetch channel mismatch " + ms.getSeedNameString(), "GSNQ680Request");
              if (System.currentTimeMillis() - lastMismatch > 3600000) {

                SimpleSMTPThread.email(Util.getProperty("emailTo"), "GSNQ680 channel mismatch " + ms.getSeedNameString(),
                        Util.ascdatetime(System.currentTimeMillis()) + "| " + line + " | Rec " + ms.getSeedNameString() + " "
                        + Util.ascdatetime2(ms.getTimeInMillis()) + "\n"
                        + "Message comes from Q680Request when a channel returned does not match the channel requested2. Node=" + Util.getRoles(null)[0]);
                lastMismatch = System.currentTimeMillis();
              }
              mss.clear();
              return mss;      // indicate we did not get data like the station is down.
            }
            mss.add(ms);
            if (dbg /*|| seed.substring(7,9).equals("AC")*/) {
              prt(mss.get(mss.size() - 1).toString());
            }
            //if(mss.size() % 50 == 49) readRequestStation();
            if (mss.size() % 1000 == 0) {
              prta("Progress mss.size=" + mss.size() + " dur=" + dur + " #bytes=" + inbytes + " line=" + line.substring(0, line.length() - 1));
            }
          } catch (IllegalSeednameException e) {
            prta("IllegalSeedname at blk=" + mss.size() + " e=" + e);
          } catch (SocketException e) {
            //e.printStackTrace(log);
            if (e.getMessage().contains("Connection reset")) {
              prta(tag + "GSN request : connection reset");
              eof = true;
              processNow = true;
            } else if (e.getMessage().contains("Broken pipe")) {
              prta(tag + "GSN request : broken pipe - exit");
              eof = true;
              processNow = true;
            } else if (e.getMessage().contains("Socket close")) {
              prta(tag + "GSN request : Socket closed - exit");
              eof = true;
              processNow = true;
            } else {
              prta(tag + "Unknown socket problem e=" + e);
              eof = true;
            }
            if (eof == true) {
              try {
                s.close();
              } catch (IOException e2) {
              }
            }
            nodata = false;
            IOErrorDuringFetch = true;
          } catch (IOException e) {
            prt(tag + "IOError trying to read from socket e=" + e);
            IOErrorDuringFetch = true;
          }
        }   // reads are not terminated by being completed
        // See if we are done
        /*long waitms = inbytes*8000/throttleUpper - (System.currentTimeMillis() - startms);
        if(waitms > 0) {
          try {prta("   * Throttle total wait="+waitms+" calc total elapse="+(inbytes*8000/throttleUpper)+
                  " inbytes="+inbytes+" throttle="+throttleUpper); sleep(waitms);}
          catch(InterruptedException expected) {}
        }*/
        if (dbg) {
          prta("Bottom of loop. mss.size=" + mss.size() + " dbg=" + dbg + " processNow=" + processNow);
        }
        if (processNow) {
          break;     // We want to process what we have and not try to get more
        }
      }   // Loop on remaining duration
      // check for blocks too soon
      int nremove = 0;
      long end = (long) (fetch.getStart().getTime() + fetch.getStartMS() + fetch.getDuration() * 1000.);
      for (int i = mss.size() - 1; i >= 0; i--) {
        if (mss.get(i).getNextExpectedTimeInMillis() < fetch.getStart().getTime() + fetch.getStartMS()) {
          prta("** MS block before begin time - remove it. " + mss.get(i).toStringBuilder(null));
          mss.remove(i);
          nremove++;
        } else if (mss.get(i).getTimeInMillis() >= end) {
          prta("** MS block after end of request time - remove it. " + mss.get(i).toStringBuilder(null));
          mss.remove(i);
          nremove++;
        }
      }
      if (mss.isEmpty() && (nodata || nremove > 0)) {
        prta("GSN request nodata exiting - " + status + " inbytes=" + inbytes + " nodata=" + nodata + " nremove=" + nremove);
        running = false;
        return null;
      } else {
        // THe request might have been broken into several pieces, if it was some blocks my be duplicated, drop them
        for (int i = 1; i < mss.size(); i++) {
          if (mss.get(i - 1) != null) {
            if (mss.get(i).isDuplicate(mss.get(i - 1))) {
              mss.set(i, null);    // convert it to a null block
            }
          }
        }
        if (s != null) {
          if (!s.isClosed()) {
            try {
              s.close();
            } catch (IOException expected) {
            }
          }
        }
        prta("GSN request exiting - " + status + " mss.size=" + mss.size() + " inbytes=" + inbytes + " eof=" + eof + " term=" + terminate);
      }
    } catch (RuntimeException e) {
      prta("GSN request Runtime e=" + e);
      if (log != null) {
        e.printStackTrace(log);
      }
    }
    running = false;
    return mss;
  }

  /**
   * read fully the number of bytes, or throw exception
   *
   * @param s The socket to read from
   * @param b The byte buffer to receive the data
   * @param off The offset into the buffer to start the read
   * @param len The length of the read in bytes
   */
  private int readFully(Socket s, byte[] buf, int off, int len) throws IOException {
    int nchar = 0;
    int l = off;
    try {
      InputStream in = s.getInputStream();
      inReader = true;
      while (len > 0) {            //
        int navailLoop = 0;
        inAvail = true;
        /*while(in.available() <=0 && navailLoop++ <500) {
          Util.sleep(10);
          if(s.isInputShutdown() || s.isClosed()) {
            Util.prta("readFully s.shutdown="+s.isInputShutdown()+" closed="+s.isClosed());
            break;
          }
        }*/
        inAvail = false;
        nchar = in.read(buf, l, Math.min(len, 512));// get nchar
        if (nchar <= 0) {
          prta(len + " read nchar=" + nchar + " len=" + len + " in.avail=" + in.available());
          inReader = false;
          return 0;
        }     // EOF - close up
        l += nchar;               // update the offset
        totalBytes += nchar;
        len -= nchar;             // reduce the number left to read
        inThrottle = true;
        doDynamicThrottle(nchar);
        inThrottle = false;
      }
      inReader = false;
      return l;
    } catch (IOException e) {      // We expect sockets to be closed by timeout.
      if (s.isClosed()) {
        prta("ReadFully leaving due to timeout detected.  Socket is closed");
      }
      prt("IOErr found len=" + len + " nchar=" + nchar + " l=" + l + " " + (char) buf[13] + " "
              + (char) buf[14] + " " + (char) buf[15] + " " + (char) buf[16] + " " + (char) buf[17] + " e=" + e);
      inReader = false;
      throw e;
    }

  }

  /**
   * this is for doing things at the start of the run which might take too long in the constructor.
   * For GSNQ680s I cannot think of a thing!
   */
  @Override
  public void startup() {
  }


  public final class GSNReaderTimeout extends Thread {

    GSNQ680Request thr;
    int lastBytes;
    int loop;

    GSNReaderTimeout(GSNQ680Request a) {
      thr = a;
      start();
    }

    @Override
    public String toString() {
      return " Timeout: alive=" + thr.isAlive()
              + " inRead=" + inReader + " inThrt=" + inThrottle + " inAvail=" + inAvail
              + " loop=" + loop + " lastBytes=" + lastBytes + " tot=" + totalBytes;
    }

    @Override
    public void run() {
      long lastStatus = System.currentTimeMillis();
      try {
        sleep(30000);
      } catch (InterruptedException expected) {
      }
      prta("  GSNReaderTimeout started for thr=" + thr.toString());
      while (thr.isAlive()) {
        if (System.currentTimeMillis() - lastStatus > 600000) {
          lastStatus = System.currentTimeMillis();
          prta(" GSNReaderTimeout: OK2 " + toString());
        }
        // We have to be in the reader to activate this code.
        if (inReader) {
          lastBytes = totalBytes;     // Save current total bytes
          loop = 0;                 // counter of seconds
          while (lastBytes == totalBytes && inReader && thr.isAlive()) {    // If no progress is being made and have not left reader
            try {
              sleep(1000);
            } catch (InterruptedException expected) {
            }
            loop++;                   // count up the seconds
            if (loop > 125) {
              prta(" *** GSNReaderTimeout on readFully.  Force socket closed " + seedname + " " + toString());
              try {
                if (s != null) {
                  if (!s.isClosed()) {
                    s.close();
                  }
                }
              } catch (IOException e) {
                prta("  *** GSNReaderTimeout " + seedname + " got IO exception trying to close socket e=" + e);
              }
              break;
            } // if over second limit
            else if (loop % 120 == 0) {
              prta(" * GSNReaderTimeout reader at " + loop + " seconds. last="
                      + lastBytes + " tot=" + totalBytes + " Alive=" + thr.isAlive() + " Wait for 120...");
            }
            if (System.currentTimeMillis() - lastStatus > 30000) {
              lastStatus = System.currentTimeMillis();
              if (loop > 15) {
                prta(" GSNReaderTimeout: BAD " + loop + " " + toString());
              }
            }
          }
        }
        try {
          sleep(10000);
        } catch (InterruptedException expected) {
        }
      }
      prta("GSNReaderTimeout is exiting " + seedname + " " + toString());
    }
  }
}
