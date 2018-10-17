/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.edgethread;

import gov.usgs.anss.util.Util;
import gov.usgs.anss.util.LoggingStream;
import gov.usgs.anss.util.LoggerInterface;
import gov.usgs.anss.util.SeedUtil;

import java.io.IOException;
import java.io.PrintStream;
import java.io.FileOutputStream;
import java.io.FileNotFoundException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.GregorianCalendar;
import java.util.StringTokenizer;

/**
 *
 * This sets up the required /common stuff for a EdgeMom controlled Thread class. It provides the
 * common stuff like output, annotation, and control needed by EdgeMom. Any subclass should set
 * logname if it wants individual logging files for this thread, if logfile is null, then its
 * logging will be merged with the EdgeMom log preceded the tag. Each thread should then use prt()
 * or prta() to record any output.
 * <p>
 * 1) Each Subclass of this must have a constructor(String line, String tg) where line is parsed for
 * command line arguments, and then call super(line,tg) 1st thing. This sets up the command elements
 * for the EdgeThread (sets the tag, opens log files after > or >>). Don't forget to strip the > or
 * >> section if your code will gag on it.
 * <p>
 * 2) Each subclass should set running=true when its run method is started and set it false if it
 * exits.
 * <p>
 * 3) Each subclass should terminate its main thread if terminate is found true, it should set
 * terminate false at completion of the thread.
 * <p>
 * 4) Create all abstract methods specified below : getConsoleOutput() (any output not written via
 * the prt() or prta() function. Normally this is stdout or stderr for real subprocesses created
 * using Subprocess class. This output will be sent to the appropriate log (thread log or edgemom
 * log) depending on the logname setting. By doConsoleLog().
 * <p>
 * terminate() this will start a termination of the thread. At a minimum the terminate variable in
 * the base class should be set true, and then set false when the thread exits. This gives a change
 * to the user to cause blocked threads to terminate quickly and gracefully especially by closing
 * I/O channels, calling "interrupt()" to get out of sleeps and waits, etc. Care should be taken
 * that a thread that has this called will quit (preferably soon, but certainly surely!).
 * <p>
 * getStatusString() return something to display on the status lines by process
 * <p>
 * 5) after calling super() the thread can add stuff to its "tag". InitThread() will make a tag like
 * "uniqueTag[Thread-nn]" typically add a "type" on as a two character string +":". So for LISS
 * client tag += "LC:"
 * <p>
 * 6) It is important that the thread catch RuntimeException and cause the thread to exit as if its
 * "terminate" flag was set. The EdgeMom thread should then be able to restart the thread and
 * processing will continue
 * <p>
 * Notes on logging: The main logging is done through a PrintStream that is synchronized here
 * through a separate "outmutex". The "staticout" is a TestStream which is synchronized itself, so
 * not synchronization is needed here. In the EdgeMom environment, EdgeMom logging and Util.prt()
 * logging is done via the staticout. The EdgeThreads under the EdgeMom thread log themselves via
 * the PrintStream out individually.
 *
 * @author davidketchum
 */
abstract public class EdgeThread extends Thread {

  public boolean terminate;
  public boolean running;
  public String tag;
  private final Integer outmutex = Util.nextMutex();   // The mutex variable out
  private PrintStream out;              // The currently open PrintStream for logging for this EdgeTHread.
  private StringBuilder timescr = new StringBuilder(14);   // Scratch space for time tags, syncrhonized by outmutex
  public String outfile;                // Name of last opened file on out (like log/echn_i#n.logN)
  protected static LoggingStream staticout; // For logging, this is registered with Util so any Util.prt go here as well
  static long staticlastday;
  static boolean useConsole;
  static String staticlogname ;
  static private String instance;
  static private String processTag;
  private final ArrayList<LoggerInterface> logs = new ArrayList<>(10);      // Keep track of objects using out so they can be updated
  private static final ArrayList<LoggerInterface> staticlogs = new ArrayList<>(10);// Keep track of objects using staticout
  protected final StringBuilder statussb = new StringBuilder(100);     // Every thread needs these scratch places
  protected final StringBuilder monitorsb = new StringBuilder(100);
  protected final StringBuilder consolesb = new StringBuilder(100);
  protected EdgeThread logpar;          // Multiple edgethreads can use a parent EdgeThread for logging - set parent her
  String logname;
  long lastday;                 // track days for log file opens

  /**
   * Do logging for this edge thread through some parent thread - this is often used by servers who
   * create connection EdgeThreads and all logging is desired to be in the server's log file.
   *
   * @param parent The parent thread
   */
  public final void setEdgeThreadParent(EdgeThread parent) {
    logpar = parent;
  }

  /**
   *
   * @return The instance name or null if it was enver set or set to '^'
   */
  public static String getInstance() {
    return instance;
  }

  public static String getProcessTag() {
    return processTag;
  }

  public static LoggingStream staticout() {
    return staticout;
  }

  public static void setUseConsole(boolean t) {
    useConsole = t;
    Util.prt("UseConsole is " + t);
  }

  public abstract void terminate();          // starts a termination on the thread

  /**
   * return of consolesb is the minimum
   *
   * @return
   */
  public abstract StringBuilder getConsoleOutput();        // must return any output from the thread

  /**
   * return of statusesb is the minimum
   *
   * @return
   */
  public abstract StringBuilder getStatusString();         // Some sort of status on the thread

  /**
   * return of monitorsb is the minimum
   *
   * @return
   */
  public abstract StringBuilder getMonitorString();          // Key value pairs to return to a monitor process like Nagios

  public boolean getTerminate() {
    return terminate;
  }    // returns the terminate variable

  public boolean isRunning() {
    return running;
  }

  /**
   * override the sleep method so that it terminates if the terminate variable is set. This means we
   * cannot sleep throught a shutdown!
   *
   * @param ms Number of millis to sleep
   * @throws InterruptedException If its thrown by Thread.sleep()
   */
  public void sleep(int ms) throws InterruptedException {
    int cnt = 0;
    while (cnt < ms) {
      Thread.sleep(Math.min(100, ms - cnt));
      cnt += 100;
      if (terminate) {
        return;
      }
    }
  }

  /**
   * when opening a DBConnection we want its output to go to this EdgeThread log file - register all
   * such here
   *
   * @param db
   */
  public final void addLog(LoggerInterface db) {
    logs.add(db);
    db.setLogPrintStream(out);
  }

  public static void addStaticLog(LoggerInterface db) {
    staticlogs.add(db);
    db.setLogPrintStream(staticout);
  }

  /**
   * reset previously created logs
   *
   * @param out
   */
  private void resetLogs(PrintStream out) {
    for (LoggerInterface log : logs) {
      log.setLogPrintStream(out);
    }
  }

  public String getTag() {
    return tag;
  }

  /**
   * sets the printstream that will be used.
   *
   * @param o
   */
  protected void setPrintStream(PrintStream o) {
    out = o;
  }

  public String getLogFilename() {
    return outfile;
  }

  public final PrintStream getPrintStream() {
    if (out == null) {
      return System.out;
    } else {
      return out;
    }
  }

  public long getBytesIn() {
    return -1;
  }

  public long getBytesOut() {
    return -1;
  }

  public void closeLog() {
    if (staticout != null) {
      staticout.close();
    }
    if (out != null) {
      out.close();
    }
  }

  public static void setInstance(String inst) {
    instance = inst;
  }

  public static void setMainLogname(String s) {
    staticlogname = s;
    if(Util.getProperty("logfilepath") != null) {
      staticlastday = 0;
      staticCheckDay();
      staticprt("Setting MainLogname to "+s);
//      new RuntimeException("Setting log main to "+s).printStackTrace(staticout);
    }
  }

  public final static void staticprt(StringBuilder s) {
    staticCheckDay();
    Util.prt(s);
  }

  public final static void staticprta(StringBuilder s) {
    staticCheckDay();
    Util.prta(s);
  }

  public final static void staticprt(StringBuffer s) {
    staticCheckDay();
    Util.prt(s);
  }

  public final static void staticprta(StringBuffer s) {
    staticCheckDay();
    Util.prta(s);
  }

  /**
   * print a string with the time prepended. Ends up in the file based on thread creation
   *
   * @param s The string to output.
   */
  public final static void staticprta(String s) {
    staticprt(Util.asctime2() + " " + s);
  }

  /**
   * Output a string. This also causes new log files to be created as days roll over based on the
   * julian day of the system time. The output unit is chosen based on the redirect .
   *
   * @param s The string to print
   */
  public final static void staticprt(String s) {
    staticCheckDay();
    Util.prt(s);
  }

  /**
   * Print something without a newline
   *
   * @param s
   */
  private static void staticprint(String s) {
    staticCheckDay();
    Util.print(s);
  }

  /**
   * Print something without a new line
   *
   * @param sb
   */
  public final static void staticprint(StringBuilder sb) {
    staticCheckDay();
    Util.print(sb);
  }

  /**
   * Print something without a new line
   *
   * @param sb
   */
  public final static void staticprint(StringBuffer sb) {
    staticCheckDay();
    Util.print(sb);
  }

  /**
   * Check for day roll over from the static printing methods.
   */
  public final static void staticCheckDay() {
    if (!useConsole) {
      if ((staticout == null || staticlastday != System.currentTimeMillis() / 86400000L)) {
        LoggingStream staticoutclose = staticout;

        if (instance != null) {
          if (instance.equals("^")) {
            instance = null;
          }
        }
        staticlastday = System.currentTimeMillis() / 86400000L;
        boolean append = false;
        if (staticout == null) {
          append = true;
        }
        if(staticlogname != null) {
          String outfile = (Util.getProperty("logfilepath") == null ? "" : 
                  (staticlogname.contains(Util.FS)?"":Util.getProperty("logfilepath")))
                  + staticlogname + (instance == null ? "" : "_" + instance) + (processTag == null ? "" : "_" + processTag)
                  + ".log" + EdgeThread.EdgeThreadDigit();
          staticout = new LoggingStream(outfile, append);// append if out is null
          LoggingStream.setNoConsole(!useConsole);
          LoggingStream.setNoInteractive(true);
          Util.setOutput(staticout);                     // this forces Util.prt and err/std exceptions to the file
          staticout.println("\n" + Util.ascdatetime2()
                  + " %%%% Opening day file :" + outfile + " append=" + append);
          // reset the static log for any registered ones
          for (LoggerInterface staticlog : staticlogs) {
            staticlog.setLogPrintStream(staticout);
          }
          if (staticoutclose != null) {
            staticoutclose.println(Util.ascdatetime2() + " Closing day file.");
            staticoutclose.close();
          }
        }
      }
    }
  }

  /**
   * Return the Printstream associated with static output (that is something now in Util.prt()
   *
   * @return
   */
  public static PrintStream getStaticPrintStream() {
    if (staticout == null) {
      staticprt("force staticout");
    }
    return staticout;
  }

  /**
   * process the initialization for the EdgeThread. Set the tag and open the log file if the command
   * line has ">" or ">>" on it.
   *
   * @param line The command line parameters (parse for > or >> to set log name)
   * @param tg The unique tag which will appear on any lines header for EdgeMom.prt()
   */
  public EdgeThread(String line, String tg) {
    if (tg.equals("") || useConsole) {
      out = System.out;
    }
    if (tg.length() > 4) {
      tag = tg;
    } else {
      tag = (tg + "    ").substring(0, 4);
    }
    StringTokenizer tk = new StringTokenizer(getName(), "-");
    if (tk.countTokens() >= 2) {
      tk.nextToken();
      tag = tag + "[" + tk.nextToken() + "]";
    } else {
      tag = tag + "[" + getName() + "]";
    }
    //prt("EdgeThread set tag to "+tag);
    line = line.replaceAll("  ", " ");
    line = line.replaceAll("  ", " ");
    String[] args = line.split("\\s");
    for (int i = 0; i < args.length; i++) {
      if (args[i].equalsIgnoreCase("-i") || args[i].equalsIgnoreCase("-instance")) {
        instance = args[i + 1];
      }
      if (args[i].equalsIgnoreCase("-tag")) {
        processTag = args[i + 1];
      }
    }
    if (instance != null) {
      if (instance.equals("^")) {
        instance = null;
      }
    }
    tk = new StringTokenizer(line, ">");
    if (tk.countTokens() >= 2) {
      tk.nextToken();         // skip the parameters
      logname = tk.nextToken();
      // if its >> then use append mode and get name
      boolean append = false;
      if (line.contains(">>")) {
        append = true;
      }
      logname = logname.trim();           // no leading or trailing whitespace please!
      try {
        lastday = System.currentTimeMillis() / 86400000L;
        outfile = (logname.contains(Util.FS)?"":Util.getProperty("logfilepath")) + logname + (instance == null ? "" : "_" + instance)
                + (processTag == null ? "" : "_" + processTag) + ".log" + EdgeThreadDigit();
        Util.chkFilePath(outfile);
        out = new PrintStream(
                new FileOutputStream(outfile, append));
        out.println(Util.ascdatetime(System.currentTimeMillis()) + " % Opening day file on cons out:" + outfile);
      } catch (FileNotFoundException e) {
        Util.IOErrorPrint(e, "Cannot open log file " + outfile);
        out = System.out;           // emergency, send it to standard out
      }
    }
    if (!tg.contains("TEST")) {
      Util.prta("new ThreadEdge " + getName() + " is " + tg + ":"
              + getClass().getSimpleName() + " log=" + outfile +  " args=" + line);
    }
  }

  public void doConsoleLog() {
    StringBuilder e = getConsoleOutput();
    if (e != null) {
      if (e.length() > 0) {
        prta(e);
        e.delete(0, e.length());
      }
    }
  }

  /**
   * Create a socket and optionally bind it
   *
   * @param s The socket which might already be open, null if this is the first create
   * @param ipadr The IP address or DNS name of the remote host
   * @param port The port on the remote host
   * @param bind If not null, then bind to this address on the local host
   * @return
   * @throws java.io.IOException
   */
  protected final Socket makeBoundSocket(Socket s, String ipadr, int port, String bind) throws IOException {
    InetSocketAddress adr = new InetSocketAddress(ipadr, port);
    if (s != null) {
      if (!s.isClosed()) {
        try {
          prta(tag + "NBS:close existing socket s=" + s);
          s.close();
        } catch (IOException e) {
        }
      }
    }
    prta(tag + "NBS: New Socket ip=" + ipadr + "/" + port + " bind=" + bind);
    s = new Socket();
    if (bind != null) {
      if (!bind.equals("")) {
        s.bind(new InetSocketAddress(bind, 0));  // User specified a bind address so bind it to this local ip/ephemeral port
      }
    }
    s.connect(adr);
    return s;
  }

  private void checkNewDay() {
    if (!useConsole) {
      // If we have a log name, but it is not open, or its a new day - open it
      if (logname != null && (out == null || lastday != System.currentTimeMillis() / 86400000L)
              && out != System.out) {
        PrintStream outclose = out;
        lastday = System.currentTimeMillis() / 86400000L;
        try {
          outfile = (logname.contains(Util.FS)?"":Util.getProperty("logfilepath")) + logname + (instance == null ? "" : "_" + instance)
                  + (processTag == null ? "" : "_" + processTag) + ".log" + EdgeThreadDigit();
          out = new PrintStream(
                  new FileOutputStream(outfile));
          resetLogs(out);
          out.println(Util.ascdatetime(System.currentTimeMillis()) + " %% Opening day file on chk out:" + outfile);
          if (outclose != null) {
            outclose.close();
          }
        } catch (FileNotFoundException e) {
          Util.IOErrorPrint(e, "Cannot open log file "+ outfile);
          out = System.out;           // emergency, send it to standard out
        }
      }
    }
  }

  /**
   * print the string on 1) My local output file if defined, 2) The EdgeMom log with tag if not
   *
   * @param s The string to print
   */
  public final void prt(String s) {
    if (logpar != null) {
      logpar.prt(s);
      return;
    }
    checkNewDay();
    if (out != null) {
      synchronized (outmutex) {
        out.println(s);
      }
    } else {
      staticprint(tag);
      staticprint(" ");
      staticprt(s);
    }
  }

  /**
   * print the string on 1) My local output file if defined, 2) The EdgeMom log with tag if not
   *
   * @param s The string to print
   */
  public final void prta(String s) {
    if (logpar != null) {
      logpar.prta(s);
      return;
    }
    checkNewDay();
    try {
      if (out != null) {
        synchronized (outmutex) {
          out.print(Util.asctime2(Util.clear(timescr)).append(" "));
          out.println(s);
        }
      } else {
        synchronized (outmutex) {      // synch timescr
          staticprint(Util.asctime2(Util.clear(timescr)).append(" "));
        }
        staticprt(s);
      }
    } catch (RuntimeException e) {
      e.printStackTrace();
    }
  }

  /**
   * print the string on 1) My local output file if defined, 2) The EdgeMom log with tag if not
   *
   * @param s The string to print
   */
  public final void prt(StringBuilder s) {
    if (logpar != null) {
      logpar.prt(s);
      return;
    }
    checkNewDay();
    try {
      if (out != null) {
        synchronized (outmutex) {
          out.println(s);
        }
      } else {
        staticprint(tag);
        staticprt(s);
      }
    } catch (RuntimeException e) {
      e.printStackTrace();
    }
  }

  /**
   * print the string on 1) My local output file if defined, 2) The EdgeMom log with tag if not
   *
   * @param s The string builder to print
   */

  public final void prta(StringBuilder s) {
    if (logpar != null) {
      logpar.prta(s);
      return;
    }
    checkNewDay();
    try {
      if (out != null) {
        synchronized (outmutex) {
          out.print(Util.asctime2(Util.clear(timescr)).append(" "));
          out.println(s);
        }
      } else {
        //staticprta(tag+s);
        synchronized (outmutex) {    // synch timescr
          staticprint(Util.asctime2(Util.clear(timescr)));
        }
        staticprint(" ");
        staticprt(s);
      }
    } catch (RuntimeException e) {
      e.printStackTrace();
    }
  }

  /**
   * print the string on 1) My local output file if defined, 2) The EdgeMom log with tag if not.
   * Synchronized to avoid interspersed output from multiple threads.
   *
   * @param s The string buffer to print
   */
  public final void prt(StringBuffer s) {
    if (logpar != null) {
      logpar.prt(s);
      return;
    }
    checkNewDay();
    try {
      if (out != null) {
        synchronized (outmutex) {
          out.println(s);
        }
      } else {
        //staticprt(tag+s);
        staticprint(tag);
        staticprt(s);
      }
    } catch (RuntimeException e) {
      e.printStackTrace();
    }
  }

  /**
   * print the string on 1) My local output file if defined, 2) The EdgeMom log with tag if not.
   * Synchronized to avoid interspersed output from multiple threads.
   *
   * @param s The string buffer to print
   */
  public final void prta(StringBuffer s) {
    if (logpar != null) {
      logpar.prta(s);
      return;
    }
    checkNewDay();
    try {
      if (out != null) {
        synchronized (outmutex) {
          out.print(Util.asctime2(Util.clear(timescr)).append(" "));
          out.println(s);
        }
      } else {
        //staticprta(tag+s);
        synchronized (outmutex) {    // synch timescr
          staticprint(Util.asctime2(Util.clear(timescr)));
        }
        staticprint(" ");
        staticprint(tag);
        staticprt(s);

      }
    } catch (RuntimeException e) {
      e.printStackTrace();
    }
  }

  /**
   * Set the output filename. This will have the property "logfilepath" prepended and ".log"
   * appended
   *
   * @param s The filename to use for logging
   */
  public final void setNewLogName(String s) {
    logname = s;
    if (!useConsole) {
      if (out != null) {
        out.close();
      }
      lastday = System.currentTimeMillis() / 86400000L;
      outfile = (logname.contains(Util.FS)?"":Util.getProperty("logfilepath")) + logname + (instance == null ? "" : "_" + instance)
              + (processTag == null ? "" : "_" + processTag) + ".log" + EdgeThreadDigit();
      staticprt("Changing log file to " + outfile);
      try {
        Util.chkFilePath(outfile);
        out = new PrintStream(
                new FileOutputStream(outfile), true);
        resetLogs(out);
      } catch (FileNotFoundException e) {
        Util.IOErrorPrint(e, "Cannot open log file " + outfile);
        out = System.out;           // emergency, send it to standard out
      }
    }
  }

  /**
   * Return the day of year last digit
   *
   * @return The Day of year last digit as a string
   */
  public final static String EdgeThreadDigit() {
    int doy = SeedUtil.doy_from_ymd(SeedUtil.fromJulian(SeedUtil.toJulian(new GregorianCalendar())));
    return Character.toString((char) ('0' + (doy % 10)));
  }

}
