/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.cwbquery;

import gov.usgs.anss.edge.IndexFileReplicator;
import gov.usgs.alarm.SendEvent;
import gov.usgs.anss.edge.*;
import gov.usgs.anss.seed.MiniSeed;
import gov.usgs.anss.edgethread.EdgeThread;
import gov.usgs.anss.util.SeedUtil;
import gov.usgs.anss.util.Util;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Date;

/**
 * Thread of this type are created by the EdgeQueryServer process and managed as a pool in that
 * class. A thread of this type is given a socket connection that came to the server, reads input
 * lines from that socket, and sends back data of listings or error codes. The socket remains open
 * unless an error occurs so this thread could be in use for a long period of time to a client that
 * keeps making requests. It creates one EdgeQuery object to actually do the requests. So this
 * thread is mainly parsing command lines and making calls to the EdgeQuery object to satisfy its
 * requests.
 * <p>
 * Interaction : The input is lines of the form used for CWBQuery with each argument include in
 * single quotes like :
 * <PRE>
 * '-s' 'USDUG  BHZ' '-b' '2014-01-20 12:23:23' '-d' '600' '-t' 'ms'\n
 * </pre> The command line can be terminated with newline or tab.
 * <p>
 * The returned data depends on the command:
 * <p>
 * Data commands - For data commands like the above 512 byte blocks of data are written back as
 * concatenated miniseed blocks - no wrappers (that is just the miniSEED bytes). If the terminator
 * is a tab, then an additional 512 byte block is written where the first 5 characters are
 * '\<EOR\>'. If the terminator is newline, then the socket is closed when the last of the data is
 * written and no extra block is written.
 * <p>
 * Text information commands - For commands returning Text like -lsc. The text is returned and the
 * socket is closed regardless of the terminator for the command line. The lines are unix newline
 * delimited (not the Windows CR/LF terminator).
 *
 * @author davidketchum
 */
public final class EdgeQueryThread extends Thread {

  private static byte[] eorbuf;
  private static byte[] errbuf;
  private static boolean dbgall;
  private static final byte[] VERSION_BYTES = "PROTOCOL_VERSION: CWB\n".getBytes();
  private Socket s;             // The socket currently assigned to this thread, read command and return results
  private EdgeQuery eq;            // this is the object which actually does data queries
  // These items are parsed from each command line
  private Date start, end;
  //private EdgeQuery query;
  private String seedname;
  private String exclude;
  private double duration;
  private boolean dbg;
  private static boolean quiet;
  private Date beg;
  private String begin;
  private String endDate;
  private final boolean readonly;        // set true if IndexFileReplicators are to be readonly
  // set false for servers running on a EdgeMom w/Replicator as
  // The replicator also ahs IFRs open, but for read/write.
  private boolean nice, nonice;            // Run thread a bit slower if this is set.
  private boolean lsoption;
  private boolean lschannels;
  private boolean gapsonly;
  private boolean delazc;
  private String delazargs;
  private boolean deleteBlock;     // If true, this query is to return deleted blocks
  private boolean allowDeleted;
  private boolean showIllegals;
  private long nquery, nlsc, totblks;
  private String tag;
  //String [] arglines;
  private final byte[] line;
  private boolean terminate;
  private long lastActionMillis;
  private boolean running;
  private String argline;
  private String lastArgline;
  private boolean idle;
  private int state;
  private final StringBuilder sb = new StringBuilder(1000);
  private final ArrayList<String> matches = new ArrayList<>(100);
  private final EdgeThread par;
  private boolean eofFound;
  private boolean tabFound;

  public boolean isIdle() {
    return idle;
  }

  public int state() {
    return state;
  }

  public static void setDebugAll(boolean t) {
    dbgall = t;
    Util.prta(" ***** DBGALL=" + t);
  }

  public long getNQuery() {
    return nquery;
  }

  public long getTotalBlocks() {
    return totblks;
  }

  public boolean isConnected() {
    return s != null;
  }

  public String getStatus() {
    return tag + " #query=" + nquery + " #lsc=" + nlsc + " #blks=" + totblks + " state=" + state + " idle=" + idle
            + " running=" + running + (s == null ? " not connected" : " connected") + " lastCmd=" + lastArgline;
  }

  public long getLastActionMillis() {
    return lastActionMillis;
  }

  public void terminate() {
    terminate = true;
    interrupt();
  }

  public boolean isRunning() {
    return running;
  }

  public String getTag() {
    return tag;
  }

  public void closeConnection() {
    prta(tag + " closing");
    try {
      if (!s.isClosed()) {
        s.close();
      }
    } catch (IOException expected) {

    }
    s = null;
  }

  public Socket getSocket() {
    return s;
  }

  private void prt(String s) {
    if (par == null) {
      Util.prt(s);
    } else {
      par.prt(s);
    }
  }

  private void prta(String s) {
    if (par == null) {
      Util.prta(s);
    } else {
      par.prta(s);
    }
  }

  /**
   * creates a new instance of EdgeQueryThread probably from a Server of some type
   *
   * @param sock Socket on which to get startup string and return response
   * @param ro If true, readonly mode is set
   */

  /**
   * creates a new instance of EdgeQueryThread probably from a Server of some type
   *
   * @param sock Socket on which to get startup string and return response
   * @param ro If true, readonly mode is set
   * @param parent A logging EdgeTHread
   */
  public EdgeQueryThread(Socket sock, boolean ro, EdgeThread parent) {
    s = sock;
    par = parent;
    readonly = ro;                // readonly state.
    line = new byte[10000];
    if (eorbuf == null) {
      eorbuf = new byte[512];
      System.arraycopy("<EOR>".getBytes(), 0, eorbuf, 0, 5);
      errbuf = new byte[5];
      System.arraycopy("<ERR>".getBytes(), 0, errbuf, 0, 5);
    }
    quiet = EdgeQueryServer.quiet;
    eq = new EdgeQuery(readonly, par);
    start();
  }

  public void assignSocket(Socket sock) {
    try {
      if (sock == null) {
        return;
      }
      try {
        sock.setSendBufferSize(512000);
        //s.setTcpNoDelay(true);
      } catch (SocketException e) {
        prt("EQT: setSendBuffer problem=" + e.getMessage());
      }
      String thread = this.toString().substring(this.toString().indexOf("-") + 1);
      if (thread.contains(",")) {
        thread = thread.substring(0, thread.indexOf(","));
      }
      lastActionMillis = System.currentTimeMillis();
      if (!quiet) {
        prta("Assign socket sock=" + sock + " to handler thread=" + thread);
      }
      tag = "EQT:[" + thread + "-" + (sock.getInetAddress() == null ? "null" : ("" + sock.getInetAddress()).substring(1)) + "/" + sock.getPort() + "]";
      lastArgline = "NEW";
      s = sock;
    } catch (RuntimeException e) {
      prta("Got runtime error e=" + e + " in assignSocket() s=" + s + " sock=" + sock + " EQT2=" + toString());
      s = null;
      throw e;
    }
  }

  @Override
  public void run() {
    int begjulian = -1;
    int endjulian = -1;
    running = true;
    int off;

    setPriority(getPriority() + 1);
    long st, msSet, msMatch, msQuery, now, msDelaz;
    TimeoutThread timeOut = new TimeoutThread(tag + " TO:", this, 6000000, 12000000);
    while (!terminate) {
      try {
        state = 1;
        idle = true;
        while (s == null) {
          try {
            sleep(100);
            timeOut.resetTimeout();
            if (terminate) {
              break;
            }
          } catch (InterruptedException expected) {
          }
        }
        idle = false;
        state = 2;
        if (terminate) {
          break;
        }
        if (!quiet) {
          prta(Util.ascdate() + " " + tag + " Starting EdgeQueryThread run from "
                  + (s != null ? s.getInetAddress() + "/" + s.getPort() : "null"));
        }
        eofFound = false;
        tabFound = false;
        lastActionMillis = System.currentTimeMillis();
        for (;;) {

          if (eofFound || terminate) {
            break;
          }
          // get a command line from the server
          boolean done = false;
          off = 0;
          argline = "";
          long elapse = System.currentTimeMillis();
          state = 3;
          while (!done) {
            tabFound = false;
            eofFound = false;
            dbg = false;
            try {
              //int l = s.getInputStream().read(line, off, 1000-off);
              if (s == null) {
                break;
              }
              int l = Util.socketRead(s.getInputStream(), line, off, line.length - off);
              if (l <= 0) {
                if (dbg) {
                  prta(tag + "  EOF getting command line abort s=" + s + " off=" + off);
                }
                if (s != null) {
                  if (!s.isClosed()) {
                    try {
                      s.close();
                    } catch (IOException expected) {
                    }
                  }
                }
                s = null;
                break;
              }
              off += l;
              for (int i = 0; i < off; i++) {

                if (line[i] == '\t' || line[i] == '\n') {
                  argline = new String(line, 0, off - 1);
                  if (line[i] == '\n') {
                    eofFound = true;
                  }
                  if (line[i] == '\t') {
                    tabFound = true;
                  }
                  if (off - i - 1 > 0) {
                    System.arraycopy(line, i + 1, line, 0, off - i - 1);
                  }
                  off = off - i - 1;
                  done = true;
                  break;
                }
              }
            } catch (InterruptedIOException e) {
              prta(tag + " Got InterruptedIO - thread time out must have gone off.");
              try {
                s.close();
              } catch (IOException expected) {
              }
              //timeOut.shutdown();
              if (eq != null) {
                eq.close();
              }
              s = null;
              //continue;
            } catch (IOException e) {
              Util.SocketIOErrorPrint(e, "Reading command line");
              if (s != null) {
                try {
                  s.close();
                } catch (IOException expected) {
                }
              }
              //timeOut.shutdown();
              if (eq != null) {
                eq.close();
              }
              s = null;
              break;
            }
          }
          state = 3;
          if (s == null) {
            break;
          }
          if (argline.equals("") && !done) {
            if (!quiet) {
              prta(tag + " argline empty and ! done - they must have closed the socket closed=" + (s != null ? s.isClosed() : "null"));
            }
            break;
          } // we got no command and EOF must have been set
          if (!quiet) {
            prta(tag + " tab=" + tabFound + " eofFound=" + eofFound + " argline=" + argline + "|");
          }
          if (argline.equals("\n") || argline.equals("")) {
            prta(tag + "Reject empty command line");
            continue;
          }
          lastArgline = argline;
          if (argline.startsWith("VERSION:")) {
            try {
              s.getOutputStream().write(VERSION_BYTES);
              prta(tag + " VERSION command");
            } catch (IOException e) {
              prta(tag + " IO Exception doing VERSION! e=" + e);
            }
            continue;
          }
          begin = "";
          endDate = "";
          // The new line is found, parse the line!
          argline = argline.replaceAll("''", "'");
          String[] args2 = argline.split("'");
          String[] args = new String[args2.length / 2];
          lsoption = false;
          lschannels = false;
          boolean eqdbg = false;
          duration = 300.;
          end = null;
          nonice = false;
          nice = false;
          int j = 0;
          for (int i = 1; i < args2.length; i = i + 2) // Trim off the empty args
          {
            args[j++] = args2[i];
          }
          for (int i = 0; i < args.length; i++) {
            if (args[i].length() > 0) {
              if (args[i].charAt(0) == '\'') {
                args[i] = args[i].substring(1);
              }
              if (args[i].charAt(args[i].length() - 1) == '\'') {
                args[i] = args[i].substring(0, args[i].length() - 1);
              }
            }
          }
          lastActionMillis = System.currentTimeMillis();
          exclude = Util.getProperty("exclude");
          if (exclude == null) {
            exclude = "exclude.txt";
          }
          if (exclude.equals("")) {
            exclude = "exclude.txt";
          }
          if (EdgeQueryServer.allowrestricted) {
            exclude = null;
          }
          seedname = "";
          gapsonly = false;
          delazc = false;
          delazargs = "";
          deleteBlock = false;
          allowDeleted = false;
          boolean version = false;
          MiniSeed.setDebug(false);
          IndexFileReplicator.setDebug(false);
          IndexBlockQuery.setDebug(false);
          Util.debug(false);
          eq.setDebug(false);

          for (int i = 0; i < args.length; i++) {
            //prt("EQT: arg="+args[i]+"| i="+i);
            switch (args[i]) {

              case "-b":
                begin = args[i + 1];
                if ((i + 2) < args.length) {
                  if (args[i + 2].indexOf(":") == 2) {
                    begin += " " + args[i + 2];
                  }
                }
                break;
              case "-dbgall":
                dbg = true;
                prta("**** DBGALL switch on");
                MiniSeed.setDebug(true);
                IndexFileReplicator.setDebug(true);
                IndexBlockQuery.setDebug(true);
                Util.debug(true);
                eq.setDebug(true);
                break;
              case "-dbg":
                dbg = true;
                eq.setDebug(true);
                break;
              case "-s":
                seedname = args[i + 1];
                break;
              case "-ed":
                endDate = args[i + 1];
                break;
              case "-d":
                if (args[i + 1].endsWith("D") || args[i + 1].endsWith("d")) {
                  duration = Double.parseDouble(args[i + 1].substring(0, args[i + 1].length() - 1)) * 86400. - 0.001;
                } else {
                  duration = Double.parseDouble(args[i + 1]);
                }
                break;
              case "-ls":
                lsoption = true;
                break;
              case "-lsc":
                lsoption = true;
                lschannels = true;
                break;
              case "-allowdeleted":
                allowDeleted = true;
                break;
              case "-eqdbg":
                eqdbg = true;
                break;
              case "-e":
                exclude = "exclude.txt";
                break;
              case "-gaps":
                gapsonly = true;
                break;
              case "-delazc":
                delazc = true;
                delazargs = args[i + 1];
                break;
              case "-delete":
                if (readonly) {
                  SendEvent.edgeSMEEvent("QueryMomRO", "A delete option attempted on readony QueryMom!", this);
                }
                deleteBlock = true;
                break;
              case "-si":
                showIllegals = true;
                break;
              case "-nice":
                nice = true;
                break;
              case "-nonice":
                nonice = true;
                break;
              case "-reset":
                prt("Reset.  Exitting....");
                System.exit(0);
            }
          }
          if (dbgall) {
            dbg = true;
            eq.setDebug(true);
          }
          if (dbg) {
            prta(tag + " parsed lsoption=" + lsoption + " dbg=" + dbg + " dbgall=" + dbgall + " eqdbg=" + eqdbg + " exclude=" + exclude
                    + " s=" + seedname + " dur=" + duration + " b=" + begin + " delaz=" + delazargs);
          }
          if (duration > 366 * 86400. + 1) {
            prta(tag + "****The duration is absurd.  Set it to one year dur=" + duration);
            duration = 367. * 86400.;
          }

          if (!lsoption) {
            state = 6;
            if (begin.equals("")) {
              prt(tag + " You must enter a beginning time");
              try {
                s.close();
              } catch (IOException expected) {
              }
              //timeOut.shutdown();
              if (eq != null) {
                eq.close();
              }
              prt(tag + " exiting.  No beginning time");
              break;
            } else {
              beg = Util.stringToDate2(begin);
              start = beg;

            }
            if (seedname.equals("")) {
              prt(tag + " -s SCNL is not optional.  Specify a seedname");
              try {
                if(s != null) s.close();
              } catch (IOException expected) {
              }
              //timeOut.shutdown();
              if (eq != null) {
                eq.close();
              }
              break;
            }
            end = new Date(beg.getTime() + ((long) (duration * 1000)));
          } else {      // this is an ls option
            if (duration <= 301.) {
              duration = 14. * 86400. - 0.001;   // lsoptions duration default
            }
            if (begin.equals("")) {                      // set begin if default to now - duration
              beg = new Date(System.currentTimeMillis() - ((long) (duration * 1000.)));
            } else {
              beg = Util.stringToDate2(begin);
            }
            if (endDate.equals("")) {                      // set begin if default to now - duration
              end = new Date(beg.getTime() + ((long) duration * 1000));
            } else {
              end = Util.stringToDate2(endDate);
              duration = (end.getTime() - beg.getTime()) / 1000.;
            }
            if (!quiet) {
              prta(tag + " lsoption beg=" + begin + "->" + beg + " dur=" + duration + " endDate=" + endDate + "->" + end + " scnl=" + seedname);
            }
          }
          seedname = seedname.replaceAll("-", " ");
          if (!seedname.contains("[") && seedname.indexOf("\\*") <= 0 && seedname.length() < 12) {
            seedname = (seedname + "............").substring(0, 12);
          }
          // Parameters are parsed, form a query for the date range specified, if the currently open query is
          // not for the same date range, open a new one.
          endjulian = SeedUtil.toJulian(end);
          begjulian = SeedUtil.toJulian(beg);
          st = System.currentTimeMillis();

          eq.setParameters(beg, end, eqdbg | dbg, (EdgeQueryServer.allowrestricted ? null : exclude), gapsonly, tag, lsoption, allowDeleted);
          now = System.currentTimeMillis();
          msSet = now - st;
          //eq = new EdgeQuery(beg, end, eqdbg | dbg, (EdgeQueryServer.allowrestricted?null:exclude), readonly, gapsonly,tag, lsoption);
          state = 7;
          st = now;
          if (delazc) {
            eq.setDelaz(delazargs, Util.ascdate(beg.getTime()).toString(), seedname);
            now = System.currentTimeMillis();
            msDelaz = now - st;
          } else {
            msDelaz = 0;
          }

          state = 8;
          if (lsoption) {
            st = System.currentTimeMillis();
            state = 9;
            nlsc++;
            if (dbg | eqdbg) {
              prta(tag + " Do a ls option showIllegals=" + showIllegals);
            }
            String dir = "";
            try {
              if (lschannels) {
                dir = eq.queryChannels(showIllegals, timeOut);
              } else {
                dir = eq.queryDirectory();
              }
            } catch (IOException e) {
              prta(tag + " error doing an queryDirectory" + e);
              Util.SocketIOErrorPrint(e, "Error doing queryDirectory()");
              e.printStackTrace(par.getPrintStream());
            }
            state = 10;
            eq.close();
            if (dbg) {
              prta(tag + " ls returned " + dir.length() + " bytes " + (System.currentTimeMillis() - st));
            }
            //Sprt("dir="+dir);
            try {
              s.getOutputStream().write(dir.getBytes());
              s.close();
              s = null;
              break;
            } catch (IOException e) {
              Util.SocketIOErrorPrint(e, "Error doing directory write back");
            }
          } // This is a query for data, 
          else {  // this is a query for data
            st = System.currentTimeMillis();
            state = 12;
            nquery++;
            try {
              if (nonice) {
                nice = false;
              }
              if (System.currentTimeMillis() - beg.getTime() > 31557600000L && !nonice && !EdgeQueryServer.noAutoNice) {
                nice = true;
              }

              if (nice) {
                try {
                  sleep(100);
                } catch (InterruptedException expected) {
                }
              }
              // Get a list of seednames that match
              matches.clear();
              int nret = eq.queryMatches(seedname, beg, duration, matches);
              // Did it return any matches
              if (matches.size() > 0) { //yes
                state = 13;
                if (dbg) {
                  prta(tag + " result: #matches = " + matches.size() + " seedmask=" + seedname
                          + " beg=" + beg + " match[0]=" + matches.get(0) + " nice=" + nice + " #files=" + eq.getIndices().size());
                  if (!quiet) {
                    if (sb.length() > 0) {
                      sb.delete(0, sb.length());
                    }
                    sb.append(tag);
                    for (int i = 0; i < matches.size(); i++) {
                      sb.append(matches.get(i)).append(" ");
                      if (i % 8 == 7) {
                        sb.append("\n").append(tag);
                      }
                    }

                    prt(sb.toString());
                  }
                }
              } else if (dbg) // No matches
              {
                prta(tag + " result: #matches = 0");
              }
              now = System.currentTimeMillis();
              msMatch = now - st;

              state = 14;
              st = now;
              eq.resetLastNblks();
              // For each matching seedname do a query
              for (String match : matches) {
                if (match.trim().equals("")) {
                  continue;
                }
                if (dbg) {
                  prta(tag + match + " beg=" + beg + " dur=" + duration);
                }
                state = 15;
                //ArrayList blks =
                lastActionMillis = System.currentTimeMillis();
                try {
                  eq.query(match, beg, duration, s.getOutputStream(), nice, deleteBlock);
                } catch (IOException e) {    // Is this a single block delete error
                  if (e.toString().contains("within a single block")) {
                    // Write out the error buffers
                    prta(tag + " * IOErr is within a single block type - write out err to client ");
                    s.getOutputStream().write(errbuf, 0, 5);  // Write out error indication
                    tabFound = false;   // Force close of socket
                    break;
                  }
                  else {
                    break;
                  }
                  //throw e;
                }
                state = 16;
                totblks += eq.getLastNblks();
                if (isInterrupted()) {
                  if (timeOut.hasSentInterrupt()) {
                    prt(tag + " timeout during query on " + match);
                    break;
                  }
                }
                ///prta("EQT: reset Timeout");
                timeOut.resetTimeout();
                if (nice) {
                  try {
                    sleep(100);
                  } catch (InterruptedException expected) {
                  }
                }
              }

              // Blocks are sent, now clean up
              now = System.currentTimeMillis();
              msQuery = now - st;
              state = 17;
              if (!quiet) {
                prta(tag + " query done. " + seedname + " " + beg + " " + duration + " nblks=" + eq.getLastNblks() + " #match=" + matches.size()
                        + " elapsed=" + (System.currentTimeMillis() - elapse) / 1000. + " s nice=" + nice
                        + " set=" + msSet + " match=" + msMatch + " query=" + msQuery + " delaz=" + msDelaz);
              }

              if (sendEOR()) {
                break;        // if sendEOR is a tab, we do not  break out
              }
            } catch (IllegalSeednameException e) {
              prt(tag + " Seedname in query is illegal=" + seedname);
              e.printStackTrace(par.getPrintStream());
              try {
                if (sendEOR()) {
                  break;
                }
              } // this request is now done.
              catch (IOException expected) {
              }
            } catch (InterruptedIOException e) {
              prt(tag + "InterruptedIOException in query.  Stop this thread it is hung!!" + timeOut.hasSentInterrupt());
              if (terminate) {
                break;          // drop this connection
              }
            } catch (IOException e) {
              Util.SocketIOErrorPrint(e, tag + "EQT: during query processing");
              prta(tag + " EQT: IOError processing stream e=" + e + " tab=" + tabFound);
              e.printStackTrace(par.getPrintStream());
              try {
                if (sendEOR()) {
                  break;
                }
              } catch (IOException expected) {
              }
            }
          }     // else on lsoption
        }       // for(;;) ever loop on argline, loop if terminate ori tab, else close the socket
      } catch (RuntimeException e) {
        prta(tag + " ** RuntimeError e=" + e);
        e.printStackTrace(par.getPrintStream());
        SendEvent.edgeSMEEvent("RuntimeExcp", "EQT run err=" + e, this);
        // The connection must or should be broken, close it and set null, wait for new connection
        if (s != null) {
          if (!s.isClosed()) {
            try {
              s.close();
            } catch (IOException expected) {
            }
          }
        }
        s = null;
        tag += "IDLE";
        //break;  // do not terminated loop and wait for new socket
      }
      state = 20;
      eq.close();
    } // while not terminated

    // Close up the socket and exit
    running = false;
    if (s != null) {
      prta(tag + " *** start close of socket " + s.isClosed() + " terminate=" + terminate);
      if (!s.isClosed()) {
        try {
          s.close();
        } catch (IOException expected) {
        }
      }
    }
    //prta(tag+" start timeout shutdown");
    eq = null;
    timeOut.shutdown();
    if (eq != null) {
      eq.close();
    }
    //if(dbg) 
    prta(tag + " exiting...");
  }

  private boolean sendEOR() throws IOException {
    // if the delimiter is tab, leave socket open
    if (tabFound) {
      s.getOutputStream().write(eorbuf, 0, (deleteBlock ? 5 : (gapsonly ? 64 : 512)));
      return false;/*prta(tag+" EORBUF written");*/
    } else {
      // newline termination, close the socket
      if (s != null) {
        if (!s.isClosed()) {
          try {
            s.close();
          } catch (IOException expected) {
          }
        }
      }
      s = null;
      return true;
    }
  }
  /**
   * Creates a new instance of EdgeQueryThread. It appears to not be used.
   *
   * @param sock Socket on which to send the resulting data
   * @param q EdgeQuery object on which to make query, if NULL then create one.
   * @param seedMask The regular expression mask of the desired channels
   * @param startDate The date at which o start the query
   * @param endDate The data at which to end the query (inclusive)
   */
  /*public EdgeQueryThread(Socket sock, EdgeQuery q, String seedMask, Date startDate, Date endDate) {
    seedName = seedMask;
    s = sock;           
    start = startDate;
    end = endDate;
    query = q;
    start();
  }*/
}
