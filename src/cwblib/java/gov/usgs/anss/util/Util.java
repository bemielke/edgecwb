/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.util;

import gov.usgs.anss.edgethread.EdgeThread;
import gov.usgs.anss.db.DBConnectionThread;
import gov.usgs.alarm.SendEvent;
import java.io.*;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.sql.*;
import java.text.DecimalFormat;
import java.util.*;
import java.nio.ByteBuffer;

/**
 * Util.java contains various static methods needed routinely in many places. Its purpose is to hold
 * all of these little helper functions in one place rather that creating a lot of little classes
 * whose names would have to be remembered.
 *
 * Created on March 14, 2000, 3:58 PMt
 */
public class Util extends Object {

  public static final String ps = System.getProperty("path.separator");
  public static final String fs = System.getProperty("file.separator");
  public static final String PS = System.getProperty("path.separator");
  public static final String FS = System.getProperty("file.separator");
  public static final String homedir
          = System.getProperty("user.home").substring(0, System.getProperty("user.home").
                  lastIndexOf(System.getProperty("file.separator")) + 1);
  public static final String userdir = System.getProperty("user.dir");
  public static final String userhome = System.getProperty("user.home");
  private static final String username = System.getProperty("user.name");
  private static final String OS = System.getProperty("os.name");
  public static String propfilename;
  public static final Properties defprops = new Properties();
  public static final Properties prop = new Properties(defprops);
  public static boolean isExiting;    // set true by the exit routine
  static String process = "UNSET";
  static PrintStream stdout = System.out; // This is were logging goes, normally reset to a LoggingStream
  static PrintStream stderr = System.err; 

  // Use of Trust store is largely obsolete and now certs are managed through Java
  private static final String TRUSTSTORE_FILENAME
          = new File(System.getProperty("user.home"), ".keystore").getPath();
  private static LeapSecondDay leapSecondDay;   // Start the leap second thread
  private static boolean debug_flag = false;
  private static boolean traceOn = false;
  public static boolean isShuttingDown;
  public static int mutexCount;
  private static final ArrayList<LoggerInterface> logs = new ArrayList<>(10);
  private static LoggingStream out;                    // This is how the console our log files is written, it is thread safe
  private static PrintStream lpt = null;
  private static String pid;
  private static String localHostIP;
  private static byte[] localIP;
  private static String device;
  private static String node;           // this is the system name, but override by Node property
  private static String systemName;  // This is always the physical system of the system
  private static int systemNumber = -1;
  private static String instance = "###";
  private static String propertyFile;

  // Variables for the shared StringBuilder (padsb and datesb) for limiting scratch StringBuilders
  private static final int INITIAL_PADSB_SIZE = 8000;
  private static final ArrayList<StringBuilder> datesb = new ArrayList(INITIAL_PADSB_SIZE);
  private static final ArrayList<StringBuilder> padsb = new ArrayList(INITIAL_PADSB_SIZE);
  private static int nextDateSBIndex;        // This is a round robin array of temporary String builders to try to
  private static long nextDatesbCount;
  private static int nextPadSBIndex;         // avoid use by more than one thread in their short lives.
  private static long nextPadsbCount;
  private static boolean logPadsb = true;
  public static long loopDatesbMS;            // Tracks the last time to use get to the last DateSB 
  private static long lastLoopPadsbMS = System.currentTimeMillis(); // Time of the last use of the last PadSB
  private static long lastLoopDatesbMS = System.currentTimeMillis();// Time of the last use of the last DateSB
  public static long loopPadsbMS;             // tracks the time to make the loop to the end of the PadSB last time

  // couple of Gregorians for doing date calculations - synchronize on these when using them
  private static final GregorianCalendar gstat2 = new GregorianCalendar();
  private static final GregorianCalendar gstat = new GregorianCalendar();

  static {
    for (int i = 0; i < INITIAL_PADSB_SIZE; i++) {
      datesb.add(new StringBuilder(20));
    }
    for (int i = 0; i < INITIAL_PADSB_SIZE; i++) {
      padsb.add(new StringBuilder(20));
    }
    gstat.setTimeZone(TimeZone.getTimeZone("UTC"));   // These may get created before default timezones are set, set them to UTC
    gstat2.setTimeZone(TimeZone.getTimeZone("UTC"));  // ditto
  }

  // Decimal format to shair across code - methods synchronize these while in use
  public static final DecimalFormat df1 = new DecimalFormat("0");
  public static final DecimalFormat df2 = new DecimalFormat("00");
  public static final DecimalFormat df3 = new DecimalFormat("000");
  public static final DecimalFormat df4 = new DecimalFormat("0000");
  public static final DecimalFormat df5 = new DecimalFormat("00000");
  public static final DecimalFormat df6 = new DecimalFormat("000000");
  public static final DecimalFormat df21 = new DecimalFormat("0.0");
  public static final DecimalFormat df22 = new DecimalFormat("0.00");
  public static final DecimalFormat df23 = new DecimalFormat("0.000");
  public static final DecimalFormat df24 = new DecimalFormat("0.0000");
  public static final DecimalFormat df25 = new DecimalFormat("0.00000");
  public static final DecimalFormat df26 = new DecimalFormat("0.000000");
  public static final DecimalFormat ef1 = new DecimalFormat("0.0E00");
  public static final DecimalFormat ef2 = new DecimalFormat("0.00E00");
  public static final DecimalFormat ef3 = new DecimalFormat("0.000E00");
  public static final DecimalFormat ef4 = new DecimalFormat("0.0000E00");
  public static final DecimalFormat ef5 = new DecimalFormat("0.00000E00");
  public static final DecimalFormat ef6 = new DecimalFormat("0.000000E00");
  public static final StringBuilder df = new StringBuilder(12);

  public static final StringBuilder df1z(long i) {
    return append(i, 1, '0', Util.clear(nextPadsb()));
  }

  public static final StringBuilder df2z(long i) {
    return append(i, 2, '0', Util.clear(nextPadsb()));
  }

  public static final StringBuilder df3z(long i) {
    return append(i, 3, '0', Util.clear(nextPadsb()));
  }

  public static final StringBuilder df4z(long i) {
    return append(i, 4, '0', Util.clear(nextPadsb()));
  }

  public static final StringBuilder df5z(long i) {
    return append(i, 5, '0', Util.clear(nextPadsb()));
  }

  public static final StringBuilder df6z(long i) {
    return append(i, 6, '0', Util.clear(nextPadsb()));
  }

  public static final StringBuilder df7z(long i) {
    return append(i, 7, '0', Util.clear(nextPadsb()));
  }

  /**
   *
   * @param i long to convert
   * @return String formated with 1 space leading zeros
   */
  public static final String df1(long i) {
    synchronized (df1) {
      return df1.format(i);
    }
  }

  public static final String df1(int i) {
    synchronized (df1) {
      return df1.format(i);
    }
  }

  public static final String df2(long i) {
    synchronized (df2) {
      return df2.format(i);
    }
  }

  public static final String df2(int i) {
    synchronized (df2) {
      return df2.format(i);
    }
  }

  public static final String df3(long i) {
    synchronized (df3) {
      return df3.format(i);
    }
  }

  public static final String df3(int i) {
    synchronized (df3) {
      return df3.format(i);
    }
  }

  public static final String df4(long i) {
    synchronized (df4) {
      return df4.format(i);
    }
  }

  public static final String df4(int i) {
    synchronized (df4) {
      return df4.format(i);
    }
  }

  public static final String df5(long i) {
    synchronized (df5) {
      return df5.format(i);
    }
  }

  public static final String df5(int i) {
    synchronized (df5) {
      return df5.format(i);
    }
  }

  public static final String df6(long i) {
    synchronized (df6) {
      return df6.format(i);
    }
  }

  public static final String df6(int i) {
    synchronized (df6) {
      return df6.format(i);
    }
  }

  public static final String df21(double i) {
    synchronized (df21) {
      return df21.format(i);
    }
  }

  public static final String df22(double i) {
    synchronized (df22) {
      return df22.format(i);
    }
  }

  public static final String df23(double i) {
    synchronized (df23) {
      return df23.format(i);
    }
  }

  public static final String df24(double i) {
    synchronized (df24) {
      return df24.format(i);
    }
  }

  public static final String df25(double i) {
    synchronized (df25) {
      return df25.format(i);
    }
  }

  public static final String df26(double i) {
    synchronized (df26) {
      return df26.format(i);
    }
  }

  public static final String ef1(double i) {
    synchronized (ef1) {
      return ef1.format(i);
    }
  }

  public static final String ef2(double i) {
    synchronized (ef2) {
      return ef2.format(i);
    }
  }

  public static final String ef3(double i) {
    synchronized (ef3) {
      return ef3.format(i);
    }
  }

  public static final String ef4(double i) {
    synchronized (ef4) {
      return ef4.format(i);
    }
  }

  public static final String ef5(double i) {
    synchronized (ef5) {
      return ef5.format(i);
    }
  }

  public static final String ef6(double i) {
    synchronized (ef6) {
      return ef6.format(i);
    }
  }

  public static final LeapSecondDay getLeapSecondObject() {
    return leapSecondDay;
  }

  public static synchronized Integer nextMutex() {
    return mutexCount++;
  }

  public static void addLog(LoggerInterface log) {
    logs.add(log);
    log.setLogPrintStream(out);
  }
  private static boolean isApplet = true;

  public static void setOutput(LoggingStream o) {
    if (out != null) {
      out.close();
    }
    out = o;
  }

  public static LoggingStream getOutput() {
    return out;
  }

  public static void suppressFile() {
    out.suppressFile();
  }

  public static void setProcess(String s) {
    process = s;
  }

  public static void setInstance(String s) {
    instance = s;
  }

  public static String getProcess() {
    return process;
  }

  public static String getPID() {
    return pid;
  }

  /**
   * get the last loaded property filename. Always include user.home + FS + filename
   *
   * @return the last loaded property filename with full path
   */
  public static String getPropertyFilename() {
    return propfilename;
  }

  public static boolean isShuttingDown() {
    return isShuttingDown;
  }

  /**
   * return the string representing the OS
   *
   * @return A String with OS in it
   */
  public static String getOS() {
    return OS;
  }

  /**
   * this initializes the Util package using the UC.getPropertyFilename()
   */
  public static void init() {
    init(UC.getPropertyFilename());
  }

  public static boolean getNoInteractive() {
    if (out != null) {
      return LoggingStream.noInteractive;
    }
    return false;
  }

  /**
   * set value of nointeractive flag (eliminates output dialogs on error if true)
   *
   * @param t If true, kill interactive dialogs on error
   */
  public static void setNoInteractive(boolean t) {
    if (out != null) {
      LoggingStream.setNoInteractive(t);
    }
  }

  /**
   * Set the noConsoleFlag for output via TestStream. Also disables all dialog boxes
   *
   * @param t If true, set noConsoleflag in TestStream
   */
  public static void setNoconsole(boolean t) {
    if (out != null) {
      LoggingStream.setNoConsole(t);
    }
  }

  /**
   * Print out a summary of running threads. Use tag for identification of caller
   *
   * @param tag The tag to use in output to identify caller
   */
  public static void showShutdownThreads(String tag) {
    System.err.println(Util.asctime2() + " " + tag + " Shutdown is complete.  Thread.activeCount="
            + Thread.activeCount());

    // If there are any threads alive, we need more data on how to kill them!
    if (Thread.activeCount() > 0) {
      Thread[] thrs = new Thread[Thread.activeCount()];
      Thread.enumerate(thrs);       // GEt the list of threads.
      for (int i = 0; i < thrs.length; i++) {
        if (thrs[i] != null) {
          System.err.println(i + " nm=" + thrs[i].getName() + " toStr=" + thrs[i].toString()
                  + " alive=" + thrs[i].isAlive() + " class=" + thrs[i].getClass().toString());
        }
      }
      for (Thread thr : thrs) {
        if (thr != null) {
          thr.interrupt();
        }
      }

      try {
        Thread.sleep(5000L);
      } catch (InterruptedException e) {
      }
      System.err.println(Util.asctime2() + " " + tag + " Shutdown is complete.  Thread.activeCount="
              + Thread.activeCount());
    }
  }

  /**
   * returns a String with on line per active thread (no limit)
   *
   * @return The list of current Threads
   */
  public static StringBuilder getThreadsString() {
    return getThreadsString(100000);
  }
  private static final StringBuilder thrsb = new StringBuilder(100);

  /**
   * returns a String with on line per active thread
   *
   * @param limit The most threads to list
   * @return The list of current Threads
   */
  public static StringBuilder getThreadsString(int limit) {
    StringBuilder sb;
    synchronized (thrsb) {
      sb = Util.clear(thrsb);
      sb.append(Util.asctime2()).append(" Thread.activeCount=").append(Thread.activeCount()).append("\n");

      // If there are any threads alive, we need more data on how to kill them!
      if (Thread.activeCount() > 0) {
        Thread[] thrs = new Thread[Thread.activeCount()];
        if (thrs != null) {
          Thread.enumerate(thrs);       // GEt the list of threads.
          int nline = 0;
          for (int i = 0; i < thrs.length; i++) {
            try {
              if (thrs[i] != null) {
                sb.append(i).append(" nm=").append(thrs[i].getId()).
                        append("/").append(thrs[i].getPriority()).append("/").append(thrs[i].isDaemon() ? "T" : "N").
                        append(" toStr=").append(thrs[i].toString())
                        .append(" alive/d=").append(thrs[i].isAlive() ? "Y" : "N").append(thrs[i].isDaemon() ? "Y" : "N").
                        append(" cl=").
                        append(thrs[i].getClass().getSimpleName()).append("\n");
                nline++;
                if (nline > limit) {
                  break;
                }
              }
            } catch (RuntimeException e) {
              Util.prt("Runtime err in getThreadsString() continue. e=" + e);
              e.printStackTrace();
            }
          }
        }
      }
    }
    return sb;
  }

  /**
   * Insure string ends with a single new line!
   *
   * @param s Input string
   * @return revised string.
   */
  public static String chkTrailingNewLine(String s) {
    if (s.length() == 0) {
      return s;
    }
    while (s.endsWith("\n\n")) {
      s = s.substring(0, s.length() - 1);
    }
    if (s.equals("\n")) {
      s = "";
      return s;
    }
    if (s.endsWith("\n")) {
      return s;
    }
    return s + "\n";
  }

  public static String tf(boolean t) {
    return t ? "t" : "f";
  }

  public static int getTrailingNumber(String s) {
    int in = 0;
    for (int i = s.length() - 1; i >= 0; i--) {
      if (!Character.isDigit(s.charAt(i))) {
        if (i + 1 < s.length()) {
          in = Integer.parseInt(s.substring(i + 1));
        }
        break;
      }
    }
    return in;
  }

  public static int getLeadingNumber(String s) {
    int in = 0;
    for (int i = 0; i < s.length(); i++) {
      if (Character.isDigit(s.charAt(i))) {
        in = in * 10 + (s.charAt(i) - '0');
      } else {
        break;
      }
    }
    return in;
  }

  /**
   * If the file contains a path, insure all of the path exist and if not create it
   *
   * @param filename File to create possibly on a path
   * @return true if directories were created
   */
  public static boolean chkFilePath(String filename) {
    if (filename.contains("/")) {
      String path = filename.substring(0, filename.lastIndexOf("/"));
      File p = new File(path);
      if (!p.exists()) {
        return p.mkdirs();
      }
    }
    return false;
  }

  /**
   * initialize the Util packages using the given property filename. If the propfile does not exist,
   * then Properties/propfile is checked for existance and used instead.
   *
   * @param propfile The string with file name in user account to use for properties.
   */
  public static void init(String propfile) {
    gstat.setTimeZone(TimeZone.getTimeZone("UTC"));

    gstat2.setTimeZone(TimeZone.getTimeZone("UTC"));

    if (propertyFile != null) {
      return;
    }
    if (propfile != null) {
      propertyFile = propfile;
    }
    try {
      localHostIP = InetAddress.getLocalHost().toString();
      localIP = InetAddress.getLocalHost().getAddress();
    } catch (UnknownHostException e) {
      localHostIP = "UnknownLocalIP";
    }
    if (debug_flag) {
      System.out.println("  *** init() " + OS + " " + userdir + " " + userhome);
    }
    //out = System.out;
    if (OS.contains("Windows")) {
      addDefaultProperty("PrinterCommand", "print /D:\\\\pilgrim\\shaky2");
    } else if (OS.contains("SunOS")) {
      addDefaultProperty("PrinterCommand", "lpr -P shaky2");
    } else if (OS.contains("Mac")) {
      addDefaultProperty("PrinterCommand", "lpr -P shaky2");
    } else {
      addDefaultProperty("PrinterCommand", "");
    }
    addDefaultProperty("PrinterFile", System.getProperty("user.home") + System.getProperty("file.separator") + "anss.lpt");
    addDefaultProperty("SessionFile", System.getProperty("user.home") + System.getProperty("file.separator") + "SESSION.OUT");

    if (!OS.contains("Windows")) {   // All systems but Windows!
      byte[] bo = new byte[10];
      try {
        String[] cmd = {"bash", "-c", "echo $PPID"};
        Process p = Runtime.getRuntime().exec(cmd);
        int len = p.getInputStream().read(bo);
        pid = "";
        for (int i = 0; i < len; i++) {
          if (bo[i] >= '0' && bo[i] <= '9') {
            pid += (char) bo[i];
          }
        }
        //System.out.println("Pid="+pid);
      } catch (IOException e) {
        System.out.println("Failed to get pid.");

      }
      try {
        String c = "/usr/sbin/ifconfig -a";
        String adrstring = "inet";
        String netmaskstring = "netmask";
        if (OS.contains("Linux")) {
          c = "/sbin/ifconfig -a";
          adrstring = "inet addr:";
          netmaskstring = "Bcast";
        }
        if (OS.contains("Mac")) {
          c = "/sbin/ifconfig -a";
        }
        Subprocess sp = new Subprocess(c);
        int val = sp.waitFor();
        String s = sp.getOutput();
        //System.out.println("ifconfi output= "+val+"\n"+s);
        BufferedReader in = new BufferedReader(new StringReader(s));
        String line;
        while ((line = in.readLine()) != null) {
          if (line.contains(adrstring) && line.indexOf(netmaskstring) > 0) {
            String addr = line.substring(line.indexOf(adrstring) + adrstring.length() + 1, line.indexOf(netmaskstring) - 1).trim();
            if (addr.length() > 7) {
              if (isNEICHost(addr)) {
                addDefaultProperty("HostIP", addr);
                break;
              }
            }
          }
        }
      } catch (IOException e) {
        System.out.println("IOException trying to get IP of local host to public internet" + e);
      } catch (InterruptedException e) {
        System.out.println("InterruptedException trying to get IP of local host to public internet" + e);
      }
    } else {    // For windows do this
      try {
        System.out.println("Windows IP=" + InetAddress.getLocalHost().getHostAddress());
        addDefaultProperty("HostIP", InetAddress.getLocalHost().getHostAddress());
      } catch (UnknownHostException e) {
        System.out.println("Could not load host IP for windows.");
      }
    }

    //if(out == null) defprops.list(System.out);
    //else defprops.list(out);
    //Util.prta("LoadProperties="+propfile);
    loadProperties(propfile);

    if (Util.getProperty("HostIP") != null) {
      if (Util.getProperty("HostIP").length() < 7) {
        addDefaultProperty("HostIP", "gacqdb");
      }
    }
    if (debug_flag) {
      System.out.println(" prop size=" + prop.size());
    }

    // Set up the TestStream object to control the output log file and console output
    if (out == null) {
      String filename = prop.getProperty("SessionFile");
      if (filename == null) {
        filename = "SESSION.OUT";
      }
      if (debug_flag) {
        System.out.println("  **** Setting up the TestStream for out file=" + filename);
      }
      if (debug_flag) {
        System.out.println("  **** Opening session file =" + filename);
      }
      out = new LoggingStream(filename);
    }
    if (debug_flag) {
      if (out == null) {
        prop.list(System.out);
      } else {
        prop.list(out);
      }
    }
    if (debug_flag) {
      prtinfo();
    }
    DBConnectionThread.init(getProperty("DBServer"));
    leapSecondDay = new LeapSecondDay();     // Start up leap second processing thread
  }

  public static void setDefaultLocalTrustStore() {
    /* Identify the truststore used for SSL. */
    System.setProperty("javax.net.ssl.trustStore", TRUSTSTORE_FILENAME);
    /* It seems we don't need a password when we're not getting private keys out
       of the truststore. If it later becomes necessary to provide a password,
       here's how to do it. */
    // System.setProperty("javax.net.ssl.trustStorePassword", TRUSTSTORE_PASSWORD);
  }

  private static void resetLog(PrintStream out) {
    for (LoggerInterface log : logs) {
      log.setLogPrintStream(out);
    }
  }

  public static void setTestStream(String filename) {
    if (out != null) {
      out.close();
    }
    out = new LoggingStream(filename);
    resetLog(out);
  }

  /**
   * return the static member indicating whether this is a application or applet. This static member
   * controls other Util about whether certain thing can be done. For instance, Util.prt will put
   * output in a SESSION.OUT file if its an application, but it cannot do this as an applet because
   * file access is not allowed by applets via the sandbox.
   *
   * @return The current state of isApplet variable;
   */
  public static boolean getIsApplet() {
    return isApplet;
  }

  /**
   * send txt to the locally attached printer. This only make sense in a application. The lpt unit
   * is opened automatically
   *
   * @param filename This file is read and formatted for output on a printer
   */
  public static void lptText(String filename) {
    String data;
    try {
      if (debug_flag) {
        Util.prt("lptText lpt=" + lpt);
      }
      if (lpt == null) {
        lptOpen();
      }
      if (debug_flag) {
        Util.prt("lptText lpt now=" + lpt);
      }
      BufferedReader r = new BufferedReader(
              new StringReader(filename));
      while ((data = r.readLine()) != null) {
        if (data.equals("")) {
          data = " ";
        }
        if (debug_flag) {
          Util.prt("Line=" + " " + data);
        }
        lpt.println("    | " + data);
      }
      lpt.print("\f");
      lpt.flush();
      lpt.close();
      lpt = null;
    } catch (IOException E) {
      Util.prt("IO exception caught!!!!");
    }
  }

  /**
   * send txt to the LPT output unit. It will be indented with spaces and the LPT will use lptOpen()
   * if it is not yet opened
   *
   * @param t A string buffer to format for output on lpt unit
   */
  public static void lpt(StringBuffer t) {
    lpt(t.toString());
  }

  /**
   * send txt to the LPT output unit. It will be indented with spaces and the LPT will use lptOpen()
   * if it is not yet opened
   *
   * @param txt A string buffer to format for output on lpt unit
   */
  public static void lpt(String txt) {
    if (lpt == null) {
      lptOpen();
    }
    //lpt.println(txt); // the following was needed so PCs would put in CRLF
    try {
      BufferedReader in = new BufferedReader(
              new StringReader(txt));
      String s;
      while ((s = in.readLine()) != null) {
        lpt.println(s);
      }
    } catch (IOException e) {
      Util.prt("lpt() threw IOError=" + e);
    }
  }

  static public String getAccount() {
    return username;
  }

  static public String getLocalHostIP() {
    return localHostIP;
  }

  static public boolean isNEICHost() {
    return isNEICHost(localIP);
  }

  static public boolean isNEICHost(byte[] ip) {
    if (ip[0] == -120 && ip[1] == -79) {
      return true;
    }
    if (ip[0] == -84 && ip[1] == 24) {
      return true;    // private 172
    }
    return ip[0] == -119 && ip[1] == -29;
  }

  static public boolean isNEICHost(String ip) {
    try {
      String iptmp = ip;
      if (iptmp.contains("/")) {
        iptmp = iptmp.substring(iptmp.indexOf("/") + 1).trim();
      }
      if (iptmp.contains(">")) {
        iptmp = iptmp.substring(iptmp.indexOf(">") + 1).trim();
      }
      byte[] ipbuf = InetAddress.getByName(iptmp).getAddress();
      //Util.prt("isNEICHost("+ip+") to ipbuf="+ipbuf[0]+"."+ipbuf[1]+"."+ipbuf[2]+"."+ipbuf[3]+" ret="+isNEICHost(ipbuf));
      return isNEICHost(ipbuf);
    } catch (UnknownHostException e) {
      e.printStackTrace();
    }
    return false;
  }

  static public String getIDText() {
    return node + "/" + username + " " + process + " " + localHostIP + " " + OS + " " + userhome + " " + userdir;
  }
  static String[] roles;
  static long lastRoles;

  /**
   * return the mroles currently running on this computer according to the user.home/roles_NODE file
   *
   * @param node If null or empty, use the current systemName returned by Util.getNode(), else the
   * systemName to get
   * @return Array of strings with each role currently on this systemName.
   */
  static public String[] getRoles(String node) {
    //Util.prta("Util.getRoles node="+node+" getNode() ="+getNode());
    if (node == null) {
      node = Util.getSystemName();
    }
    if (node.startsWith("evpn-")) {
      node = "gm073";
    }
    if (node.equals("")) {
      node = Util.getSystemName();
    }
    //Util.prta("Util.getRoles2 node="+node+" getNode() ="+getNode());

    // on non-edge/gaux systemName, just use the system systemName name
    if (!node.contains("edge") && !node.contains("gaux")) {  // Not an edge type systemName use system name
      if (roles == null) {
        roles = new String[1];
        roles[0] = node;
      }
      //return roles;   // Old behavior, do not try and read roles_systemname
    }
    if (System.currentTimeMillis() - lastRoles < 120000) {
      return roles;
    }
    lastRoles = System.currentTimeMillis();

    //Util.prt("call getroles returns="+System.getenv("VDL_ROLES"));
    String line;
    try {
      try (BufferedReader in = new BufferedReader(new FileReader(userhome + FS + "roles_" + node.trim()))) {
        while ((line = in.readLine()) != null) {
          if (line.length() > 9) {
            if (line.substring(0, 9).equals("VDL_ROLES")) {
              line = line.substring(11).replaceAll("\"", "");
              roles = line.split("[\\s,]");
              for (int i = 0; i < roles.length; i++) {
                roles[i] = roles[i].trim();
                //Util.prt("get mroles read files returns mroles "+i+" "+mroles[i]);
              }
              break;
            }
          }
        }
      }
    } catch (FileNotFoundException e) {
      Util.IOErrorPrint(e, "Trying to read " + userhome + FS + "roles_" + node.trim());
    } catch (IOException e) {
      Util.IOErrorPrint(e, "Trying to read " + userhome + FS + "roles_" + node.trim());
    }
    return roles;
  }

  /**
   * When you want the basic data base reports with "|" to have boxes around the fields. This takes
   * the string in txt (usually returned as a print from a result set and sends it to the printer
   *
   * @param t The string buffer to format in box mode
   */
  public static void lptBox(StringBuffer t) {
    lptBox(t.toString());
  }

  /**
   * When you want the basic data base reports with "|" to have boxes around the fields. This takes
   * the string in txt (usually returned as a print from a result set and sends it to the printer
   *
   * @param txt The string to format in box mode
   */
  public static void lptBox(String txt) {
    String s;
    String header;
    String line = "";
    int iline;
    if (lpt == null) {
      lptOpen();
    }
    try {
      // get "title" line and build the - and + box line
      try (BufferedReader in = new BufferedReader(
              new StringReader(txt))) {
        // get "title" line and build the - and + box line
        if (debug_flag) {
          Util.prt("Starting Print");
        }
        s = in.readLine();
        int lastcol = 0;
        for (int i = 0; i < s.length(); i++) {
          if (s.charAt(i) == '|') {
            if (i < 80) {
              lastcol = i;
            }
            line += "+";
          } else {
            line += "-";
          }
        }
        header = s;
        iline = 100;
        lastcol++;

        // Read in a database line, convert it
        while ((s = in.readLine()) != null) {
          if (debug_flag) {
            Util.prt("Readln=" + s);
          }
          if (iline > 55) {
            if (iline < 100) {
              lpt.print("\f");
            }
            if (lastcol <= 80) {
              lpt.println(" " + header);
              lpt.println(" " + line);
              iline = 2;
            } else {
              lpt.println(" " + header.substring(0, lastcol));
              lpt.println(" " + line.substring(0, lastcol));
              lpt.println("           |" + header.substring(lastcol));
              lpt.println("           |" + line.substring(lastcol));
              iline = 4;
            }
          }
          if (lastcol <= 80) {
            lpt.println(" " + s);
            lpt.println(" " + line);
            iline += 2;
          } else {
            lpt.println(" " + s.substring(0, lastcol));
            String tmp = s.substring(lastcol);
            tmp = tmp.replace('|', ' ');
            tmp = tmp.trim();
            if (!tmp.equals("")) {
              s = s.replace('|', ' ');
              lpt.println("            " + s.substring(lastcol));
              iline++;
            }
            lpt.println(" " + line.substring(0, lastcol));
//          lpt.println("           |"+line.substring(lastcol));
            iline += 2;
          }

        }
      }
      lpt.println("\f");
      lpt.flush();
      lpt.close();
      lpt = null;
    } catch (IOException E) {
      Util.prt("IO exception creating print job");
    }
  }

  /**
   * lptPrint() causes the currently open printer file to be printed based on the "PrintCommand"
   * property
   */
  public static void lptSpool() {
    if (lpt != null) {
      lpt.flush();
      lpt.close();
      lpt = null;
    }
    String cmd = prop.getProperty("PrinterCommand") + " " + prop.getProperty("PrinterFile");
    if (debug_flag) {
      Util.prt("Print command = " + cmd);
    }
    try {
      Process p = Runtime.getRuntime().exec(cmd);
      Util.prt("Runtime executed!");
      p.waitFor();
    } catch (IOException e) {
      IOErrorPrint(e, "Trying to print with cmd=" + cmd);
    } catch (InterruptedException e) {
      Util.prt("Interrupted exception doing runtime.exec()");
    }
  }

  /**
   * lptOpen opens the line printer. If the user wants to redirect this to some device/file other
   * than LPT1:, create a file C:\Ops\PRINTER.DAT and put the desired device or file on the first
   * line. This will open an outputStream to the device
   */
  public static void lptOpen() {

    device = prop.getProperty("PrinterFile");
    try {

      if (debug_flag) {
        Util.prt("Attempt Connect to |" + device + "| unit");
      }
      lpt = new PrintStream(
              device);
      /*lpt = new PrintStream(
                 new BufferedOutputStream(
                   new FileOutputStream(device)));*/
    } catch (FileNotFoundException E) {
      Util.prt("Printer did not open " + device);
      Util.prt("Message : " + E.toString());
      E.printStackTrace(out);
      Util.prt("Open LPT.OUT for print compatibility");
      exit("Printer file would not open.");
    }
    if (debug_flag) {
      Util.prt("LPT is now " + lpt);
    }
  }

  /**
   * set whether this entity should be treated as an applet or not
   *
   * @param b If true this should be treated as an applet
   */
  public static void setApplet(boolean b) {
    isApplet = b;
    init();
    if (debug_flag) {
      Util.prt("setApplet=" + b);
    }
  }

  /**
   * return a line of user input (from System.in)
   *
   * @return A String with input without the newline
   */
  public static String getLine() {
    StringBuilder sb = new StringBuilder(200);
    byte[] b = new byte[50];
    boolean done = false;
    while (!done) {
      try {
        int len = System.in.read(b);
        for (int i = 0; i < len; i++) {
          if (b[i] == '\n') {
            done = true;
            break;
          }
          sb.append((char) b[i]);
        }
      } catch (IOException e) {
        Util.IOErrorPrint(e, "Getting console input");
      }
    }

    return sb.toString();
  }

  /**
   * turn trace output on or off
   *
   * @param t If true, start printing trace output
   */
  public static void setTrace(boolean t) {
    traceOn = t;
  }

  /**
   * if Trace is on, print out information on object and the given string
   *
   * @param obj A object to print its class name
   * @param txt Some text to add to class information
   */
  public static void trace(Object obj, String txt) {
    if (traceOn) {
      Util.prt("TR:" + obj.getClass().getName() + ":" + txt);
    }
  }

  /**
   * Use this to override the target of the prt and prta() methods from the console or session.out
   */
  //public static void setOut(PrintStream o) { out = o;}
  /**
   * prt takes the input text and prints it out. It might go into the MSDOS window or to the
   * SESSION.OUT file depending on the state of the debug flag. The "main" should decide on debug or
   * not, set the flag and then all of the output will be available on the window or in the file.
   * The file is really useful when something does not work because the user can e-mail it to us and
   * a full debug listing is available for postmortem
   *
   * @param out The output PrintStream to send output,
   * @param txt The output text
   */
  public static void prt(PrintStream out, String txt) {
    if (userhome == null) {
      init();
    }
    out.println(txt);
  }

  /**
   * prta adds time stamp to output of prt(). takes the input text and prints it out. It might go
   * into the MSDOS window or to the SESSION.OUT file depending on the state of the debug flag. The
   * "main" should decide on debug or not, set the flag and then all of the output will be available
   * on the window or in the file. The file is really useful when something does not work because
   * the user can e-mail it to us and a full debug listing is available for postmortem
   *
   * @param out The output PrintStream to send output,
   * @param txt The output text
   */
  public static void prta(PrintStream out, String txt) {
    if (userhome == null) {
      init();
    }
    out.println(Util.asctime2() + " " + txt);
  }

  public static void prta(CharSequence sb) {
    try {
      if (userhome == null) {
        init();
      }
      if (out == EdgeThread.staticout() || out == null) {
        EdgeThread.staticCheckDay();
      }
      if (out == null) {
        System.out.print(Util.asctime2());
        System.out.print(" ");
        System.out.println(sb);
      } else {
        out.print(Util.asctime2());
        out.print(" ");
        out.println(sb);
      }
    } catch (RuntimeException e) {
      e.printStackTrace();
    }
  }

  public static void prt(CharSequence sb) {
    try {
      if (userhome == null) {
        init();
      }
      if (out == EdgeThread.staticout() || out == null) {
        EdgeThread.staticCheckDay();
      }
      if (out == null) {
        System.out.println(sb);
      } else {
        out.println(sb);
      }
    } catch (RuntimeException e) {
      e.printStackTrace();
    }
  }

  /*public static void prt(StringBuffer sb) {
    try {
      if (userhome == null) {
        init();
      }
      if (out == EdgeThread.staticout() ||out == null) {
        EdgeThread.staticCheckDay();
      }
      out.println(sb);
    } catch (RuntimeException e) {
      e.printStackTrace();
    }
  }

  public static void prta(StringBuffer sb) {
    try {
      if (userhome == null) {
        init();
      }
      if (out == EdgeThread.staticout() ||out == null) {
        EdgeThread.staticCheckDay();
      }
      out.print(Util.asctime2());
      out.print(" ");
      out.print(sb);
    } catch (RuntimeException e) {
      e.printStackTrace();
    }
  }*/
  public static void print(CharSequence s) {
    try {
      if (out == EdgeThread.staticout() || out == null) {
        EdgeThread.staticCheckDay();
      }
      if (out == null) {
        System.out.print(s);
      } else {
        out.print(s);
      }
    } catch (RuntimeException e) {
      e.printStackTrace();
    }
  }

  /*public static void print(StringBuilder sb) {
    if (out == EdgeThread.staticout()) {
      EdgeThread.staticCheckDay();
    }
    try {
      out.print(sb);
    } catch (RuntimeException e) {
      e.printStackTrace();
    }
  }

  public static void print(StringBuffer sb) {
    if (out == EdgeThread.staticout()) {
      EdgeThread.staticCheckDay();
    }
    try {
      out.print(sb);
    } catch (RuntimeException e) {
      e.printStackTrace();
    }
  }*/
  /**
   * dump a bunch of config info
   */
  public static void prtinfo() {
    Util.prta("Environment : OS=" + OS + " Arch=" + System.getProperty("os.arch") + " version=" + System.getProperty("os.version")
            + " user name=" + System.getProperty("user.name") + " hm=" + System.getProperty("user.home")
            + " current dir=" + System.getProperty("user.dir")
            + "Separators file=" + System.getProperty("file.separator")
            + " path=" + System.getProperty("path.separator") + " homedir=" + homedir);
    Util.prt("Java compiler=" + System.getProperty("java.compiler")
            + " JRE version=" + System.getProperty("java.version")
            + " JRE Manuf=" + System.getProperty("java.vendor")
            + " Install directory=" + System.getProperty("java.home")
            + " JRE URL=" + System.getProperty("java.url"));
    Util.prt("VM implementation version=" + System.getProperty("java.vm.version")
            + " vendor=" + System.getProperty("java.vm.vendor")
            + " name=" + System.getProperty("java.vm.name"));
    Util.prt("VM Specification version=" + System.getProperty("java.vm.specification.version")
            + " vendor=" + System.getProperty("java.vm.specification.vendor")
            + " name=" + System.getProperty("java.vm.specification.name"));
    Util.prt("Class version=" + System.getProperty("java.class.version")
            + "\nclass path=" + System.getProperty("java.class.path")
            + "\nlibrary path=" + System.getProperty("java.library.path"));
    Util.prt("SystemName=" + Util.getSystemName() + " system number=" + Util.getSystemNumber());
  }

  /**
   * set value of debug flag and hence whether Util.prt() generates output to string. If false,
   * output will go to SESSION.OUT unless an applet
   *
   * @param in if true, set debug flag on
   */
  public static void debug(boolean in) {
    debug_flag = in;
    if (debug_flag) {
      //new RuntimeException("Set Util.debug(true) continue").printStackTrace();
      prtinfo();
    }
  }

  /**
   * get state of debug flag
   *
   * @return Current setting of debug flag
   */
  public static boolean isDebug() {
    return debug_flag;
  }

  /**
   * This routine dumps the meta data and the current values from a ResultSet. Note: the values will
   * always return NULL if the RS is on the insertRow, even if the insertRow columns have been
   * updated
   *
   * @param rs The resultset to print
   */
  public static void printResultSetMetaData(ResultSet rs) {
    try {
      ResultSetMetaData md = rs.getMetaData();
      Util.prt("Insert row columns= " + md.getColumnCount());
      for (int i = 1; i <= md.getColumnCount(); i++) {
        String column = md.getColumnName(i);
        String type = md.getColumnTypeName(i);
        String txt = "" + i + " " + type + " nm: " + column + " NullOK :"
                + md.isNullable(i) + "value=";
        if (type.equals("CHAR")) {
          txt = txt + rs.getString(column);
        }
        if (type.equals("LONG")) {
          txt = txt + rs.getInt(column);
        }
        Util.prt(txt);
      }
    } catch (SQLException e) {
      Util.SQLErrorPrint(e, "MetaData access failed");
    }
  }

  /**
   * Clear all of the fields in a ResultSet. Used the by the New record objects to insure everything
   * is cleared for an InsertRow so the dumb thing will actually insert something! This uses the
   * Result set meta data to get the descriptions and types of the columns
   *
   * @param rs The resultset to clear.
   */
  public static void clearAllColumns(ResultSet rs) {
    try {
      ResultSetMetaData md = rs.getMetaData();
//      Util.prt("ClearAllColumns= " + md.getColumnCount());
      for (int i = 1; i <= md.getColumnCount(); i++) {
        String column = md.getColumnName(i);
        String type = md.getColumnTypeName(i);
        //Util.prt("" + i + " " + type + " nm: " + column + " NullOK :" +md.isNullable(i));
//        String txt = "" + i + " " + type + " nm: " + column + " NullOK :" +
//          md.isNullable(i) ;
        // For each data type add an ELSE here
        int j = type.indexOf(" UNSIGNED");
        if (j > 0) {
          type = type.substring(0, j);
          //Util.prta("handle unsigend="+type);
        }
        if (type.equalsIgnoreCase("CHAR")) {
          rs.updateString(column, "");
        } else if (type.equalsIgnoreCase("FLOAT")) {
          rs.updateFloat(column, (float) 0.);
        } else if (type.equalsIgnoreCase("DOUBLE")) {
          rs.updateDouble(column, (double) 0.);
        } else if (type.equalsIgnoreCase("LONGLONG")) {
          rs.updateLong(column, (long) 0);
        } else if (type.equalsIgnoreCase("INTEGER")) {
          rs.updateInt(column, (int) 0);
        } else if (type.equalsIgnoreCase("INT")) {
          rs.updateInt(column, (int) 0);
        } else if (type.equalsIgnoreCase("BIGINT")) {
          rs.updateInt(column, (int) 0);
        } else if (type.equalsIgnoreCase("LONG")) {
          rs.updateInt(column, 0);
        } else if (type.equalsIgnoreCase("SHORT")) {
          rs.updateShort(column, (short) 0);
        } else if (type.equalsIgnoreCase("SMALLINT")) {
          rs.updateShort(column, (short) 0);
        } else if (type.equalsIgnoreCase("TINY")) {
          rs.updateByte(column, (byte) 0);
        } else if (type.equalsIgnoreCase("TINYINT")) {
          rs.updateByte(column, (byte) 0);
        } else if (type.equalsIgnoreCase("BYTE")) {
          rs.updateByte(column, (byte) 0);
        } else if (type.equalsIgnoreCase("VARCHAR")) {
          rs.updateString(column, "");
        } else if (type.equalsIgnoreCase("DATE")) {
          rs.updateDate(column, new java.sql.Date((long) 0));
        } else if (type.equalsIgnoreCase("DATETIME")) {
          rs.updateDate(column, new java.sql.Date((long) 0));
        } else if (type.equalsIgnoreCase("TIME")) {
          rs.updateTime(column, new Time((long) 0));
        } else if (type.equalsIgnoreCase("TINYBLOB")) {
          rs.updateString(column, "");
        } else if (type.equalsIgnoreCase("BLOB")) {
          rs.updateString(column, "");
        } else if (type.equalsIgnoreCase("MEDIUMBLOB")) {
          rs.updateString(column, "");
        } else if (type.equalsIgnoreCase("LONGBLOB")) {
          rs.updateString(column, "");
        } else if (type.equalsIgnoreCase("TINYTEXT")) {
          rs.updateString(column, "");
        } else if (type.equalsIgnoreCase("TEXT")) {
          rs.updateString(column, "");
        } else if (type.equalsIgnoreCase("MEDIUMTEXT")) {
          rs.updateString(column, "");
        } else if (type.equalsIgnoreCase("LONGTEXT")) {
          rs.updateString(column, "");
        } else if (type.equalsIgnoreCase("int4")) {
          rs.updateLong(column, (int) 0);
        } else if (type.equalsIgnoreCase("int2")) {
          rs.updateShort(column, (short) 0);
        } else if (type.equalsIgnoreCase("int8")) {
          rs.updateLong(column, (long) 0);
        } else if (type.equalsIgnoreCase("int4")) {
          rs.updateLong(column, (int) 0);
        } else if (type.equalsIgnoreCase("TIMESTAMP")) {
          java.util.Date now = new java.util.Date();
          rs.updateTimestamp(column, new Timestamp(now.getTime()));
        } else if (type.equalsIgnoreCase("bpchar")) {
          rs.updateString(column, "");    // weird postgress column
        } else if (type.equalsIgnoreCase("serial")) {
          rs.updateInt(column, (int) 0);  //  "
        } else {
          System.err.println(" *** clearAllColumn type not handled!=" + type
                  + " Column=" + column);
          //Util.exit(0);
        }
//        Util.prt(txt);
      }
    } catch (SQLException e) {
      Util.SQLErrorPrint(e, "MetaData access failed");
    }
  }

  /**
   * given and SQLException and local message string, dump the exception and system state at the
   * time of the exception. This routine should be called by all "catch(SQLException) clauses to
   * implement standard reporting.
   *
   * @param E The exception.
   * @param msg The user supplied text to add
   */
  public static void SQLErrorPrint(SQLException E, String msg) {
    System.err.println(asctime2() + " " + msg);
    System.err.println("SQLException : " + E.getMessage());
    System.err.println("SQLState     : " + E.getSQLState());
    System.err.println("SQLVendorErr : " + E.getErrorCode());
  }

  /**
   * given and SQLException and local message string, dump the exception and system state at the
   * time of the exception. This routine should be called by all "catch(SQLException) clauses to
   * implement standard reporting.
   *
   * @param E The exception.
   * @param msg The user supplied text to add
   * @param out The printstream to use for outputing this exception
   */
  public static void SQLErrorPrint(SQLException E, String msg, PrintStream out) {
    if (out == null) {
      SQLErrorPrint(E, msg);
      return;
    }
    out.println(asctime2() + " " + msg);
    out.println("SQLException : " + E.getMessage());
    out.println("SQLState     : " + E.getSQLState());
    out.println("SQLVendorErr : " + E.getErrorCode());
  }

  /**
   * given and IOException from a Socket IO and local message string, dump the exception and system
   * state at the time of the exception. This routine should be called by all
   * "catch(SocketException) clauses to implement standard reporting.
   *
   * @param e The exception
   * @param msg The user supplied text to add
   */
  public static void SocketIOErrorPrint(IOException e, String msg) {
    SocketIOErrorPrint(e, msg, null);
  }

  /**
   * given and IOException from a Socket IO and local message string, dump the exception and system
   * state at the time of the exception. This routine should be called by all
   * "catch(SocketException) clauses to implement standard reporting.
   *
   * @param e The exception
   * @param msg The user supplied text to add
   * @param ps The PrintStream to use to output this exception
   */
  public static void SocketIOErrorPrint(IOException e, String msg, PrintStream ps) {
    if (ps == null) {
      ps = System.out;
    }
    if (e != null) {
      if (e.getMessage() != null) {
        if (e.getMessage().contains("Broken pipe")) {
          ps.println(asctime2() + " Broken pipe " + msg);
        } else if (e.getMessage().contains("Connection reset")) {
          ps.println(asctime2() + " Connection reset " + msg);
        } else if (e.getMessage().contains("Connection asctime2()d")) {
          ps.println(asctime2() + " Connection timed " + msg);
        } else if (e.getMessage().contains("Socket closed")) {
          ps.println(asctime2() + " Socket closed " + msg);
        } else if (e.getMessage().contains("Socket is closed")) {
          ps.println(asctime2() + " Socket closed " + msg);
        } else if (e.getMessage().contains("Stream closed")) {
          ps.println(asctime2() + " Socket Stream closed " + msg);
        } else if (e.getMessage().contains("Operation interrupt")) {
          ps.println(asctime2() + " Socket interrupted " + msg);
        } else {
          Util.IOErrorPrint(e, msg, ps);
        }
      }
    } else {
      Util.IOErrorPrint(e, msg, ps);
    }
  }

  /**
   * given and SocketException and local message string, dump the exception and system state at the
   * time of the exception. This routine should be called by all "catch(SocketException) clauses to
   * implement standard reporting.
   *
   * @param E The exception
   * @param msg The user supplied text to add
   */
  public static void SocketErrorPrint(SocketException E, String msg) {
    System.err.println(asctime2() + " " + msg + " e=" + E);
    System.err.println("SocketException : " + E.getMessage());
    E.printStackTrace();
  }

  /**
   * given and IOException and local message string, dump the exception and system state at the time
   * of the exception. This routine should be called by all "catch(IOException) clauses to implement
   * standard reporting.
   *
   * @param E The exception
   * @param msg The user supplied text to add
   */
  public static void IOErrorPrint(IOException E, String msg) {
    System.err.println(asctime2() + " " + msg + " e=" + E);
    System.err.println("IOException : " + E.getMessage());
    E.printStackTrace();
  }

  /**
   * given and IOException and local message string, dump the exception and system state at the time
   * of the exception. This routine should be called by all "catch(IOException) clauses to implement
   * standard reporting.
   *
   * @param E The exception
   * @param msg The user supplied text to add
   * @param out The PrintStream to use to output this exception
   */
  public static void IOErrorPrint(IOException E, String msg, PrintStream out) {
    if (out == null) {
      IOErrorPrint(E, msg);
      return;
    }
    out.println(asctime2() + " " + msg + " e=" + E);
    out.println("IOException : " + E.getMessage());
    E.printStackTrace(out);
  }

  /**
   * given and UnknownHostException and local message string, dump the exception and system state at
   * the time of the exception. This routine should be called by all "catch(UnknownHostException)
   * clauses to implement standard reporting.
   *
   * @param E The exception
   * @param msg The user supplied text to add
   */
  public static void UnknownHostErrorPrint(UnknownHostException E, String msg) {
    System.err.println(asctime2() + " " + msg);
    System.err.println("SocketException : " + E.getMessage());
    E.printStackTrace();
  }

  /**
   * sleep the give number of milliseconds
   *
   * @param ms THe number of millis to sleep
   */
  public static void sleep(int ms) {
    try {
      Thread.sleep(Math.max(1, ms));
    } catch (InterruptedException e) {
    }
  }

  /**
   * Escape a string for use in an SQL query. The string is returned enclosed in single quotes with
   * any dangerous characters escaped.
   *
   * This is modeled after escape_string_for_mysql from the MySQL API. Beware that the characters
   * '%' and '_' are not escaped. They do not have special meaning except in LIKE clauses.
   *
   * @param s The string to be escaped
   * @return The escaped string
   */
  public static StringBuilder sqlEscape(StringBuilder s) {
    StringBuilder result;
    int i;
    char c;

    result = new StringBuilder();
    result.append('\'');
    for (i = 0; i < s.length(); i++) {
      c = s.charAt(i);
      switch (c) {
        case '\0':
          result.append("\\0");
          break;
        case '\n':
          result.append("\\n");
          break;
        case '\r':
          result.append("\\r");
          break;
        case '\032':
          result.append("\\Z");
        case '\\':
        case '\'':
        case '"':
          result.append("\\").append(c);
          break;
        default:
          result.append(c);
          break;
      }
    }
    result.append('\'');

    return result;
  }

  public static StringBuilder sqlEscape(String s) {
    StringBuilder sb = new StringBuilder(10).append(s);
    return sqlEscape(sb);
  }

  /**
   * Escape an int for use in an SQL query.
   *
   * @param i The int to be escaped
   * @return The escaped string
   */
  public static String sqlEscape(int i) {
    return Integer.toString(i);
  }

  /**
   * Escape a long for use in an SQL query.
   *
   * @param l The long to be escaped
   * @return The escaped string
   */
  public static String sqlEscape(long l) {
    return Long.toString(l);
  }

  /**
   * Escape a Date for use in an SQL query. The string returned is in the form "{d 'yyyy-MM-dd'}".
   *
   * @param d The Date to be escaped
   * @return The escaped string
   * @see
   * <a href="http://incubator.apache.org/derby/docs/10.0/manuals/reference/sqlj230.html">http://incubator.apache.org/derby/docs/10.0/manuals/reference/sqlj230.html</a>
   */
  public static String sqlEscape(java.sql.Date d) {
    return "{d " + sqlEscape(d.toString()) + "}";
  }

  /**
   * Escape a Time for use in an SQL query. The string returned is in the form "{t 'hh:mm:ss'}".
   *
   * @param t The Time to be escaped
   * @return The escaped string
   * @see
   * <a href="http://incubator.apache.org/derby/docs/10.0/manuals/reference/sqlj230.html">http://incubator.apache.org/derby/docs/10.0/manuals/reference/sqlj230.html</a>
   */
  public static String sqlEscape(Time t) {
    return "{t " + sqlEscape(t.toString()) + "}";
  }

  /**
   * Escape a Timestamp for use in an SQL query. The string returned is in the form "{ts 'yyyy-MM-dd
   * hh:mm:ss.ffffffff'}".
   *
   * @param ts The Timestamp to be escaped
   * @return The escaped string
   * @see
   * <a href="http://incubator.apache.org/derby/docs/10.0/manuals/reference/sqlj230.html">http://incubator.apache.org/derby/docs/10.0/manuals/reference/sqlj230.html</a>
   */
  public static String sqlEscape(Timestamp ts) {
    return "{ts " + sqlEscape(ts.toString()) + "}";
  }

  /**
   * remove any backslashes and backslash any single quotes, semicolons become colons suitable for
   * writing to a string in SQL
   *
   * @param s Input string.
   * @return The modified string if any
   */
  public static String deq(String s) {
    // remove all backslashes and backslash any single quotes, make any semicolons into colons
    return s.replaceAll("\\\\", "").replaceAll("\\'", "\\\\'").replaceAll(";", ":");
  }

  /**
   * remove any backslashes and backslash any single quotes, semicolons become colons suitable for
   * writing to a string in SQL
   *
   * @param s Input string.
   * @return The modified string if any
   */
  public static StringBuilder deq(StringBuilder s) {
    // remove all backslashes and backslash any single quotes, make any semicolons into colons
    Util.stringBuilderReplaceAll(s, "\\", "");
    Util.stringBuilderReplaceAll(s, "\\'", "\\\\'");
    Util.stringBuilderReplaceAll(s, ";", ":");
    return s;
  }

  /**
   * ResultSets often come with "NULL" and return nulls, we prefer actual objects with appropriate
   * values. Return a "" if result is null.
   *
   * @param rs The ResultSet to get this String column from
   * @param column The name of the column to get
   * @return The target column value
   * @throws SQLException if column does not exist or other SQLExceptions
   */
  public static String getString(ResultSet rs, String column)
          throws SQLException {
//    try {
    String t = rs.getString(column);
//    Util.prt("Util.getString for " + column + "=" +t + "| wasnull=" +rs.wasNull());
    if (rs.wasNull()) {
      t = "";
    }
//    } catch (SQLException e) {throw e};
    return t;
  }

  /**
   * ResultSets often come with "NULL" and return nulls, we prefer actual objects with appropriate
   * values. Return a "" if result is null.
   *
   * @param rs The ResultSet to get this column from
   * @param column The column to get
   * @return The target column value
   * @throws SQLException if column does not exist or other SQLExceptions
   */
  public static String getString(ResultSet rs, int column)
          throws SQLException {
//    try {
    String t = rs.getString(column);
//    Util.prt("Util.getString for " + column + "=" +t + "| wasnull=" +rs.wasNull());
    if (rs.wasNull()) {
      t = "";
    }
//    } catch (SQLException e) {throw e};
    return t;
  }

  /**
   * get and integer from ResultSet rs with name 'column'
   *
   * @param rs The ResultSet to get this column from
   * @param column The column to get
   * @return The target column value
   * @throws SQLException if column does not exist or other SQLExceptions
   */
  public static int getInt(ResultSet rs, String column)
          throws SQLException {
//    try {
    int i = rs.getInt(column);
    if (rs.wasNull()) {
      i = 0;
    }
//    } catch (SQLException e) { throw e}
    return i;
  }

  /**
   * get and integer from ResultSet rs with name 'column'
   *
   * @param rs The ResultSet to get this column from
   * @param column The column to get
   * @return The target column value
   * @throws SQLException if column does not exist or other SQLExceptions
   */
  public static int getInt(ResultSet rs, int column)
          throws SQLException {
//    try {
    int i = rs.getInt(column);
    if (rs.wasNull()) {
      i = 0;
    }
//    } catch (SQLException e) { throw e}
    return i;
  }

  /**
   * get a long from ResultSet rs with name 'column'
   *
   * @param rs The ResultSet to get this column from
   * @param column The column to get
   * @return The target column value
   * @throws SQLException if column does not exist or other SQLExceptions
   */
  public static long getLong(ResultSet rs, String column)
          throws SQLException {
//    try {
    long i = rs.getLong(column);
    if (rs.wasNull()) {
      i = 0;
    }
//    } catch (SQLException e) { throw e}
    return i;
  }

  /**
   * get a long from ResultSet rs with name 'column'
   *
   * @param rs The ResultSet to get this column from
   * @param column The column to get
   * @return The target column value
   * @throws SQLException if column does not exist or other SQLExceptions
   */
  public static long getLong(ResultSet rs, int column)
          throws SQLException {
//    try {
    long i = rs.getLong(column);
    if (rs.wasNull()) {
      i = 0;
    }
//    } catch (SQLException e) { throw e}
    return i;
  }

  /**
   * get a short from ResultSet rs with name 'column'
   *
   * @param rs The ResultSet to get this column from
   * @param column The column to get
   * @return The target column value
   * @throws SQLException if column does not exist or other SQLExceptions
   */
  public static short getShort(ResultSet rs, String column)
          throws SQLException {
//    try {
    short i = rs.getShort(column);
    if (rs.wasNull()) {
      i = 0;
    }
//    } catch (SQLException e) { throw e}
    return i;
  }

  /**
   * get a short from ResultSet rs with name 'column'
   *
   * @param rs The ResultSet to get this column from
   * @param column The column to get
   * @return The target column value
   * @throws SQLException if column does not exist or other SQLExceptions
   */
  public static short getShort(ResultSet rs, int column)
          throws SQLException {
//    try {
    short i = rs.getShort(column);
    if (rs.wasNull()) {
      i = 0;
    }
//    } catch (SQLException e) { throw e}
    return i;
  }

  /**
   * get a byte from ResultSet rs with name 'column'
   *
   * @param rs The ResultSet to get this column from
   * @param column The column to get
   * @return The target column value
   * @throws SQLException if column does not exist or other SQLExceptions
   */
  public static byte getByte(ResultSet rs, int column)
          throws SQLException {
//    try {
    byte i = rs.getByte(column);
    if (rs.wasNull()) {
      i = 0;
    }
//    } catch (SQLException e) { throw e}
    return i;
  }

  /**
   * get a double from ResultSet rs with name 'column' /** get a short from ResultSet rs with name
   * 'column'
   *
   * @param rs The ResultSet to get this column from
   * @param column The column to get
   * @return The target column value
   * @throws SQLException if column does not exist or other SQL error
   */
  public static double getDouble(ResultSet rs, String column)
          throws SQLException {
//    try {
    double i = rs.getDouble(column);
    if (rs.wasNull()) {
      i = 0;
    }
//    } catch (SQLException e) { throw e}
    return i;
  }

  /**
   * get a double from ResultSet rs with name 'column'
   *
   * @param rs The ResultSet to get this column from
   * @param column The column to get
   * @return The target column value
   * @throws SQLException if column does not exist or other SQL error
   */
  public static double getDouble(ResultSet rs, int column)
          throws SQLException {
//    try {
    double i = rs.getDouble(column);
    if (rs.wasNull()) {
      i = 0;
    }
//    } catch (SQLException e) { throw e}
    return i;
  }

  /**
   * get a float from ResultSet rs with name 'column'
   *
   * @param rs The ResultSet to get this column from
   * @param column The column to get
   * @return The target column value
   * @throws SQLException if column does not exist or other SQL error
   */
  public static float getFloat(ResultSet rs, String column)
          throws SQLException {
//    try {
    float i = rs.getFloat(column);
    if (rs.wasNull()) {
      i = 0;
    }
//    } catch (SQLException e) { throw e}
    return i;
  }

  /**
   * get a float from ResultSet rs with name 'column'
   *
   * @param rs The ResultSet to get this column from
   * @param column The column to get
   * @return The target column value
   * @throws SQLException if column does not exist or other SQL error
   */
  public static float getFloat(ResultSet rs, int column)
          throws SQLException {
//    try {
    float i = rs.getFloat(column);
    if (rs.wasNull()) {
      i = 0;
    }
//    } catch (SQLException e) { throw e}
    return i;
  }

  /**
   * get a Timestamp from ResultSet rs with name 'column'
   *
   * @param rs The ResultSet to get this column from
   * @param column The column to get
   * @return The target column value
   */
  public static Timestamp getTimestamp(ResultSet rs, String column) {
    try {
      Timestamp i = rs.getTimestamp(column);
      if (rs.wasNull()) {
        i = new Timestamp(10000L);
      }
      return i;
    } catch (SQLException | RuntimeException e) {
      return new Timestamp(10000L);
    }
  }

  /**
   * get a Timestamp from ResultSet rs with name 'column', return 1970 if anything is wrong like
   * null or illegal date
   *
   * @param rs The ResultSet to get this column from
   * @param column The column to get
   * @return The target column value
   */
  public static Timestamp getTimestamp(ResultSet rs, int column) {
    try {
      Timestamp i = rs.getTimestamp(column);
      if (rs.wasNull()) {
        i = new Timestamp(10000L);
      }
      return i;
    } catch (SQLException | RuntimeException e) {
      return new Timestamp(10000L);
    }
  }

  /**
   * get a date from ResultSet rs with name 'column'
   *
   * @param rs The ResultSet to get this column from
   * @param column The column to get
   * @return The target column value
   * @throws SQLException if column does not exist or other SQL error
   */
  public static java.sql.Date getDate(ResultSet rs, String column)
          throws SQLException {
    java.sql.Date i = rs.getDate(column);
    if (rs.wasNull()) {
      i = new java.sql.Date((long) 0);
    }
    return i;
  }

  /**
   * get a date from ResultSet rs with name 'column'
   *
   * @param rs The ResultSet to get this column from
   * @param column The column to get
   * @return The target column value
   * @throws SQLException if column does not exist or other SQL error
   */
  public static java.sql.Date getDate(ResultSet rs, int column)
          throws SQLException {
    java.sql.Date i = rs.getDate(column);
    if (rs.wasNull()) {
      i = new java.sql.Date((long) 0);
    }
    return i;
  }

  /**
   * get a Time from ResultSet rs with name 'column'
   *
   * @param rs The ResultSet to get this column from
   * @param column The column to get
   * @return The target column value
   * @throws SQLException if column does not exist or other SQL error
   */
  public static Time getTime(ResultSet rs, String column)
          throws SQLException {
    Time i = rs.getTime(column);
    if (rs.wasNull()) {
      i = new Time(0);
    }
    return i;
  }

  /**
   * get a Time from ResultSet rs with name 'column'
   *
   * @param rs The ResultSet to get this column from
   * @param column The column to get
   * @return The target column value
   * @throws SQLException if column does not exist or other SQL error
   */
  public static Time getTime(ResultSet rs, int column)
          throws SQLException {
    Time i = rs.getTime(column);
    if (rs.wasNull()) {
      i = new Time(0);
    }
    return i;
  }

  /**
   * We will represent Times as hh:dd and trade them back and forth with sister routine
   * stringToTime()
   *
   * @param t The time to convert to a String
   * @return The String with time in hh:mm am/pm
   */
  public static String timeToString(Time t) {
    String s = t.toString();
    s = s.substring(0, 5);
    if (s.substring(0, 2).compareTo("12") > 0) {
      int ih = Integer.parseInt(s.substring(0, 2)) - 12;
      s = "" + ih + s.substring(2, 5);
      s = s + " pm";
    } else {
      if (s.substring(0, 2).equals("12")) {
        s = s + " pm";
      } else {
        s = s + " am";
      }
    }
    return s;
  }

  /**
   * convert string to a time in the normal form hh:mm [am/pm] to SQL Time type
   *
   * @param s The String to convert to a time
   * @return The Time
   */
  public static Time stringToTime(String s) {
    int ih, im;
    String ampm;
    StringTokenizer tk = new StringTokenizer(s, ": ");
    if (tk.countTokens() < 2) {
      Util.prt("stringToTime not enough tokens s=" + s + " cnt="
              + tk.countTokens());
      return new Time((long) 0);
    }
    String hr = tk.nextToken();
    String mn = tk.nextToken();
    if (debug_flag) {
      Util.prt("time to String hr=" + hr + " min=" + mn);
    }
    try {
      ih = Integer.parseInt(hr);
      im = Integer.parseInt(mn);
    } catch (NumberFormatException e) {
      Util.prt("Time: not a integers " + hr + ":" + mn + " string=" + s);
      return new Time((long) 0);
    }
    if (tk.hasMoreTokens()) {
      ampm = tk.nextToken();
      if (debug_flag) {
        Util.prt("timeToString ampm=" + ampm + " is pm=" + ampm.equalsIgnoreCase("pm"));
      }
      if (ampm.equalsIgnoreCase("pm") && ih != 12) {
        ih += 12;
      } else if (ampm.equalsIgnoreCase("am")) {
      } else {
        if (debug_flag) {
          Util.prt("Time add on not AM or PM =" + s);
        }
        if (ih < 8) {
          ih += 12;          // We do not play before 8
        }
      }
    } else {
      if (ih < 8) {
        ih += 12;          // We do not play before 8
      }
    }

    Time t = new Time((long) ih * 3600000 + im * 60000);
    return t;
  }

  // This sets the default time zone to GMT so that GregorianCalendar uses GMT 
  // as the local time zone!
  public static void setModeGMT() {
    TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    //GregorianCalendar gc = new GregorianCalendar();
    //System.out.println("def="+bef+" aft="+System.currentTimeMillis()+" diff="+(System.currentTimeMillis() - bef));
    //System.out.println(Util.ascdatetime2(System.currentTimeMillis())+" new Gregorian="+Util.ascdatetime2(gc));
    //System.out.println(" sys="+System.getProperty("user.timezone"));
    //System.out.println("Done");
  }

  /**
   * Create a SQL date from a year, month, day int. The SQL date comes from MS since 1970 but the
   * "gregorianCalendar" Class likes to use MONTH based on 0=January. This does the right Thing so I
   * wont forget later!
   *
   * @param year The year
   * @param month The month
   * @param day The day of month
   * @return Date in sql form
   */
  public static java.sql.Date date(int year, int month, int day) {
    GregorianCalendar d = new GregorianCalendar(year, month - 1, day);
    return new java.sql.Date(d.getTime().getTime());
  }

  /**
   * Create a Java date from a year, month, day, hr, min,sec . The SQL date comes from MS since 1970
   * but the "gregorianCalendar" Class likes to use MONTH based on 0=January. This does the right
   * Thing so I wont forget later!
   *
   * @param year The year
   * @param month The month
   * @param day The day of month
   * @param hr The hour of day
   * @param min The minute
   * @param sec The second
   * @return Date in sql form
   *
   */
  public static java.util.Date date(int year, int month, int day, int hr, int min, int sec) {
    GregorianCalendar d = new GregorianCalendar(year, month - 1, day, hr, min, sec);
    return new java.util.Date(d.getTime().getTime());
  }

  /**
   * return current date (based on system time) as an SQL Date
   *
   * @return The current date as an SQL date
   */
  public static java.sql.Date today() {
    GregorianCalendar d = new GregorianCalendar();
//		Util.prt("Year="+d.get(Calendar.YEAR)+" mon="+d.get(Calendar.MONTH));
//		Util.prt("d="+d.toString());
    return new java.sql.Date(d.getTime().getTime());
  }

  /**
   * get a gregorian calendar given a yymmdd encoded date and msecs
   *
   * @param yymmdd The encoded date
   * @param msecs Millis since midnight
   * @return The number of millis since 1970 per GregorianCalendar
   */
  public static long toGregorian2(int yymmdd, int msecs) {
    int msorg = msecs;
    if (msecs < 0) {
      Util.prt("toGregorian2 msecs<0 = " + msorg);
      msecs = Math.abs(msecs);
    }
    int yr = yymmdd / 10000;
    if (yr < 100) {
      yr = yr + 2000;
    }
    yymmdd = yymmdd - yr * 10000;
    int mon = yymmdd / 100;
    int day = yymmdd % 100;
    int hr = msecs / 3600000;
    msecs = msecs - hr * 3600000;
    int min = msecs / 60000;
    msecs = msecs - min * 60000;
    int secs = msecs / 1000;
    msecs = msecs - secs * 1000;
    if (hr == 24 && min == 0 && secs == 0 && msecs == 0) { // This happens if the last time is 23:59:59.999[5-9] it rounds up to next day
      hr = 23;
      min = 59;
      secs = 59;
      msecs = 999;
    }
    if (yr < 2000 || yr > 2030 || mon <= 0 || mon > 12 || hr < 0 || hr > 23 || min < 0 || min > 59
            || secs < 0 || secs > 59 || msecs < 0 || msecs > 999 || msorg < 0) {
      String s = "toGregorian2 data out of range yr=" + yr
              + " mon=" + mon + " day=" + day + " " + hr + ":" + min + ":" + secs + "." + msecs + " msorg=" + msorg;
      RuntimeException e = new RuntimeException(s);
      e.printStackTrace();
      SendEvent.debugEvent("ToGregNeg", s, "Util");
      throw e;
    }
    long ms;
    synchronized (gstat2) {
      gstat2.set(yr, mon - 1, day, hr, min, secs);
      gstat2.setTimeInMillis(gstat2.getTimeInMillis() / 1000 * 1000);
      gstat2.add(Calendar.MILLISECOND, msecs);
      ms = gstat2.getTimeInMillis();
    }
    return ms;
  }

  /**
   * Make a NNSSSSSCCCLL from the component network, station, channel and location codes Note: the
   * output is in the form with all Spaces and no hyphens.
   *
   * @param network Up to a 2 character network code
   * @param station Up to a 5 character station code
   * @param channel Up to a 3 character channel code
   * @param location Up to a 2 character location code
   * @return
   */
  public static String makeSeedname(String network, String station, String channel, String location) {
    return Util.rightPad(network.replaceAll("-", " "), 2).substring(0, 2)
            + Util.rightPad(station.replaceAll("-", " ").trim(), 5)
            + Util.rightPad(channel.replaceAll("-", " ").trim(), 3)
            + Util.rightPad(location.replaceAll("-", " "), 2);
  }

  /**
   * Make a NNSSSSSCCCLL from the component network, station, channel and location codes Note: the
   * output is in the form with all Spaces and no hyphens.
   *
   * @param network Up to a 2 character network code
   * @param station Up to a 5 character station code
   * @param channel Up to a 3 character channel code
   * @param location Up to a 2 character location code
   * @return
   */
  /**
   * Make a NNSSSSSCCCLL from the component network, station, channel and location codes Note: the
   * output is in the form with all Spaces and no hyphens.
   *
   * @param network Up to a 2 character network code
   * @param station Up to a 5 character station code
   * @param channel Up to a 3 character channel code
   * @param location Up to a 2 character location code
   * @param nscl The NSCL is returned here
   * @return
   */
  public static StringBuilder makeSeednameSB(String network, String station, String channel, String location, StringBuilder nscl) {
    if (nscl == null) {
      nscl = Util.nextPadsb();
    }
    Util.clear(nscl).append(Util.rightPad(network.replaceAll("-", " "), 2).substring(0, 2)).
            append(Util.rightPad(station.replaceAll("-", " ").trim(), 5)).
            append(Util.rightPad(channel.replaceAll("-", " ").trim(), 3)).
            append(Util.rightPad(location.replaceAll("-", " "), 2));
    return nscl;
  }

  /**
   * Convert a yymmdd type integer into a gregorian calendar
   *
   * @param yymmdd The yymmdd integer
   * @param msecs Millis to add
   * @return
   */
  public static GregorianCalendar toGregorian(int yymmdd, int msecs) {
    long ms = toGregorian2(yymmdd, msecs);
    GregorianCalendar now = new GregorianCalendar();
    now.setTimeZone(TimeZone.getTimeZone("GMT+0"));
    now.setTimeInMillis(ms);
    return now;
  }

  /**
   * given a gregorian calendar return a date encoded yymmdd
   *
   * @param d The gregoriancalendar to convert
   * @return The yymmdd encoded date
   */
  public static int yymmddFromGregorian(GregorianCalendar d) {
    return d.get(Calendar.YEAR) * 10000 + (d.get(Calendar.MONTH) + 1) * 100 + d.get(Calendar.DAY_OF_MONTH);
  }

  /**
   * given a gregorian calendar return a date encoded yymmdd
   *
   * @param d The gregoriancalendar to convert
   * @return The yymmdd encoded date
   */
  public static int yymmddFromGregorian(long d) {
    int yymmdd;
    synchronized (gstat) {
      gstat.setTimeInMillis(d);
      yymmdd = gstat.get(Calendar.YEAR) * 10000 + (gstat.get(Calendar.MONTH) + 1) * 100
              + gstat.get(Calendar.DAY_OF_MONTH);
    }
    return yymmdd;
  }

  /**
   * given a gregorian calendar return a millis since midnight
   *
   * @param d The gregoriancalendar to convert
   * @return The millis since midnight
   */
  public static int msFromGregorian(GregorianCalendar d) {
    //Util.prt("timeinms="+d.getTimeInMillis());
    return (int) (d.getTimeInMillis() % 86400000L);
  }

  /**
   * given a gregorian calendar return a millis since midnight
   *
   * @param d The gregoriancalendar to convert
   * @return The millis since midnight
   */
  public static int msFromGregorian(long d) {
    //Util.prt("timeinms="+d.getTimeInMillis());
    return (int) (d % 86400000L);
  }

  /**
   * return a time string to the hundredths of second for current time
   *
   * @return the time string hh:mm:ss.hh
   */
  public static StringBuilder asctime() {
    return asctime(System.currentTimeMillis(), null);
  }

  /**
   * return a time string to the hundredths of second from a GregorianCalendar
   *
   * @param d A gregorian calendar to translate to time hh:mm:ss.hh
   * @return the time string hh:mm:ss.hh
   */
  public static StringBuilder asctime(GregorianCalendar d) {
    return asctime(d.getTimeInMillis(), null);
  }

  /**
   * return a time string to the hundredths of second for current time
   *
   * @param sb The string builder to add to
   * @return the time string hh:mm:ss.hh
   */
  public static StringBuilder asctime(StringBuilder sb) {
    return asctime(System.currentTimeMillis(), sb);
  }

  /**
   * return a time string to the hundredths of second from a GregorianCalendar
   *
   * @param d A gregorian calendar to translate to time hh:mm:ss.hh
   * @param sb The string builder to add to
   * @return the time string hh:mm:ss.hh
   */
  public static StringBuilder asctime(GregorianCalendar d, StringBuilder sb) {
    return asctime(d.getTimeInMillis(), sb);
  }

  /**
   * Create a StringBuilder with status about the state of the date and pad StringBuilder pools.
   *
   * @return The status string
   */
  public static StringBuilder getSBPoolStatus() {
    long tot = 0;
    int max = 0;
    StringBuilder maxsb = null;
    for (StringBuilder padsb1 : padsb) {
      tot += padsb1.capacity();
      if (padsb1.capacity() > max) {
        max = padsb1.capacity();
        maxsb = padsb1;
      }

    }
    for (StringBuilder datesb1 : datesb) {
      tot += datesb1.capacity();
    }
    synchronized (thrsb) {
      Util.clear(thrsb).
              append("SBP: date ").append(loopDatesbMS).append(" ms idx/size/cnt ").append(nextDateSBIndex).
              append("/").append(datesb.size()).append("/").append(nextDatesbCount).
              append(" pad ").append(loopPadsbMS).append(" ms idx/size/cnt ").append(nextPadSBIndex).
              append("/").append(padsb.size()).append("/").append(nextPadsbCount).
              append(" ").append(" max=").append(max).append(" ").append(tot / 1024).append(" kB");
      if (max > 2000) {
        thrsb.append("Max content=").append(maxsb);
      }
    }
    return thrsb;
  }

  /**
   * Return a short StringBuilder from the pool - please do not use more than 20 places!
   *
   * @return A StringBuilder from the pool
   */
  public static StringBuilder getPoolSB() {
    return Util.clear(nextPadsb());
  }

  public static void setLogPadsb(boolean t) {
    logPadsb = t;
  }

  /**
   * This routine returns the next available StringBuilder from the date string build pool. If the
   * end of the current pool is reached, the routing looks to see if it has taken less than 500 ms
   * since the last time this happened, if so, it doubles the size of the pool up to 100000. NOTE:
   * this is used by all of the date/time translation routines in this class and is not available to
   * others other than through the public date methods.
   *
   * @return A StringBuilder which has been cleared.
   */
  private static StringBuilder nextDatesb() {
    StringBuilder ret;
    synchronized (datesb) {
      if (nextDateSBIndex >= datesb.size() - 1) {
        long now = System.currentTimeMillis();
        //prt(asctime2(Util.clear(thrsb))+" Loop Datesb now="+now+" last="+lastLoopDatesbMS+" diff="+(now - lastLoopDatesbMS)+
        //        " sz="+datesb.length+" next="+nextDateSBIndex+" "+(nextDateSBIndex % datesb.length));
        loopDatesbMS = now - lastLoopDatesbMS;
        lastLoopDatesbMS = now;
        if (loopDatesbMS < 500) {
          if (datesb.size() < 100000) {    // Do not let it grow absurdly
            if (logPadsb) {
              out.println(" ** SBP: nextDatesb() <500 ms=" + loopDatesbMS + " sz=" + datesb.size());
            }
            int newsize = Math.min(datesb.size() * 2, 100000);
            for (int i = datesb.size(); i < newsize; i++) {
              datesb.add(new StringBuilder(20));
            }
          }

        }
        //else prt(asctime2(Util.clear(thrsb))+" Loop Dates SB O.K. "+loopDatesbMS+" sz="+datesb.length);
      }
      nextDatesbCount++;
      nextDateSBIndex = (++nextDateSBIndex) % datesb.size();
      ret = Util.clear(datesb.get(nextDateSBIndex));
    }
    return ret;
  }
  private static long lastPadsbPrint;

  public static void setSuppressPadsbWarning(boolean t) {
    suppressPadsbWarning = t;
  }
  private static boolean suppressPadsbWarning;

  /**
   * This routine returns the next available StringBuilder from the 'pad' string builder pool. If
   * the end of the current pool is reached, the routing looks to see if it has taken less than 500
   * ms since the last time this happened, if so, it doubles the size of the pool up to 100000.
   * NOTE: This is private as it is only used with this class by methods formatting StringBuilers or
   * String and the actual padsb StringBuiler is only exposed by its return to the user of these
   * Strings. Hopefully the user will
   *
   * @return A StringBuilder which has been cleared.
   */
  private static StringBuilder nextPadsb() {
    StringBuilder ret;
    synchronized (padsb) {
      if (nextPadSBIndex >= padsb.size() - 1) {
        long now = System.currentTimeMillis();
        loopPadsbMS = now - lastLoopPadsbMS;
        lastLoopPadsbMS = now;
        if (loopPadsbMS < 500) {
          if (padsb.size() < 100000) {   // Do not let it grow absurdly
            if (logPadsb) {
              out.println(" ** SBP: nextPadsb() <500 ms=" + loopPadsbMS + " next=" + nextPadSBIndex + " sz=" + padsb.size());
            }
            int newsize = Math.min(padsb.size() * 2, 100000);
            for (int i = padsb.size(); i < newsize; i++) {
              padsb.add(new StringBuilder(20));
            }
          }

          if (now - lastPadsbPrint < 2000 && padsb.size() > 100000 && !suppressPadsbWarning) {
            new RuntimeException("Padsb frequent.  Trace to help track loops").printStackTrace();
          }
          lastPadsbPrint = now;
        }
        //else prt(asctime2(Util.clear(thrsb))+" Loop Pad SB o.k. "+loopPadsbMS+" sz="+padsb.length);
      }
      nextPadSBIndex = (++nextPadSBIndex) % padsb.size();
      nextPadsbCount++;
      ret = Util.clear(padsb.get(nextPadSBIndex));
    }
    return ret;
  }

  /**
   * return a time string to the hundredths of second from a GregorianCalendar
   *
   * @param ms A long with a ms from a GregorianCalendar etc
   * @param tmp The string builder to add to
   * @return the time string hh:mm:ss.hh
   */
  public static StringBuilder asctime(long ms, StringBuilder tmp) {
    StringBuilder sb = tmp;
    try {
      if (sb == null) {
        sb = Util.clear(nextDatesb());
      }
      synchronized (gstat) {
        gstat.setTimeInMillis(ms);
        Util.append((gstat.get(Calendar.HOUR_OF_DAY)), 2, '0', sb).append(":");
        Util.append((gstat.get(Calendar.MINUTE)), 2, '0', sb).append(":");
        Util.append((gstat.get(Calendar.SECOND)), 2, '0', sb);
      }
    } catch (RuntimeException e) {
      e.printStackTrace();
    }
    return sb;
  }

  /**
   * return a time string to the hundredths of second from a GregorianCalendar
   *
   * @param ms A long with a ms from a GregorianCalendar etc
   * @return the time string hh:mm:ss.hh
   */
  public static StringBuilder asctime(long ms) {
    return asctime(ms, null);
  }

  /**
   * return a time string for current time
   *
   * @return the time string hh:mm:ss.mmm
   */
  public static StringBuilder asctime2() {
    return asctime2(System.currentTimeMillis(), null);
  }

  /**
   * return a time string to the milliseconds for the current time in user StringBuilder
   *
   * @param tmp A stringBuilder to put the result
   * @return the time string hh:mm:ss.mmm
   */
  public static StringBuilder asctime2(StringBuilder tmp) {
    return asctime2(System.currentTimeMillis(), tmp);
  }

  /**
   * return a time string to the millisecond from a GregorianCalendar
   *
   * @param d A gregorian calendar to translate to time hh:mm:ss.mmm
   * @return the time string hh:mm:ss.mmm
   */
  public static StringBuilder asctime2(GregorianCalendar d) {
    return asctime2(d.getTimeInMillis(), null);
  }

  /**
   * return a time string to the millisecond from a GregorianCalendar
   *
   * @param s A gregorian calendar to translate to time hh:mm:ss.mmm
   * @param sb A stringBuilder to put the result
   * @return the time string hh:mm:ss.mmm
   */
  public static StringBuilder asctime2(GregorianCalendar s, StringBuilder sb) {
    return asctime2(s.getTimeInMillis(), sb);
  }

  /**
   * return a time string to the millisecond from a GregorianCalendar
   *
   * @param ms A milliseconds (1970 datum) to translate to time hh:mm:ss.mmm
   * @return the time string hh:mm:ss.mmm
   */
  public static StringBuilder asctime2(long ms) {
    return asctime2(ms, null);
  }

  /**
   * return a time string to the millisecond from a GregorianCalendar
   *
   * @param ms A milliseconds (1970 datum) to translate to time hh:mm:ss.mmm
   * @param tmp String builder for answer, if null an internal stringbuilder is used
   * @return the time string hh:mm:ss.mmm
   */
  public static StringBuilder asctime2(long ms, StringBuilder tmp) {
    StringBuilder sb = tmp;
    if (tmp == null) {
      sb = Util.clear(nextDatesb());
    }
    try {
      synchronized (gstat) {
        gstat.setTimeInMillis(ms);
        Util.append((gstat.get(Calendar.HOUR_OF_DAY)), 2, '0', sb).append(":");
        Util.append((gstat.get(Calendar.MINUTE)), 2, '0', sb).append(":");
        Util.append((gstat.get(Calendar.SECOND)), 2, '0', sb).append(".");
        Util.append((gstat.get(Calendar.MILLISECOND)), 3, '0', sb);
      }
    } catch (RuntimeException e) {
      e.printStackTrace();
    }
    return sb;
  }

  /**
   * give a ip address as 4 bytes convert it to a dotted string
   *
   * @param ip Four bytes with raw IP address
   * @param offset An offset in ip where the four raw bytes start
   * @return string of form nnn.nnn.nnn.nnn with leading zeros to fill out space
   */
  public static StringBuilder stringFromIP(byte[] ip, int offset) {
    StringBuilder sb = Util.clear(nextPadsb());
    try {
      Util.append(((int) ip[offset] & 0xff), 3, '0', sb).append(".");
      Util.append(((int) ip[offset + 1] & 0xff), 3, '0', sb).append(".");
      Util.append(((int) ip[offset + 2] & 0xff), 3, '0', sb).append(".");
      Util.append(((int) ip[offset + 3] & 0xff), 3, '0', sb).append(".");
    } catch (RuntimeException e) {
      e.printStackTrace();
    }
    return sb;
  }

  /**
   * return the current date as a yyyy_DDD string
   *
   * @return YYYY_DDD of the current date
   */
  public static StringBuilder toDOYString() {
    StringBuilder ret;
    synchronized (gstat) {
      gstat.setTimeInMillis(System.currentTimeMillis());
      ret = toDOYString(gstat);
    }
    return ret;
  }

  /**
   * return a DOY formated string from a GregoianCalendar
   *
   * @param gc The GregorianCalendar
   * @return string of form YYYY,DDD,HH:MM:SS
   */
  public static StringBuilder toDOYString(GregorianCalendar gc) {
    return toDOYString(gc, null);
  }

  /**
   * return a DOY formated string from a GregoianCalendar
   *
   * @param time Time in epoch millis
   * @return string of form YYYY,DDD,HH:MM:SS
   */
  public static StringBuilder toDOYString(long time) {
    StringBuilder ret;
    synchronized (gstat2) {
      gstat2.setTimeInMillis(time);
      ret = toDOYString(gstat2);
    }
    return ret;
  }

  /**
   * return a DOY formated string from a TimeStamp
   *
   * @param ts The time stamp
   * @return string of form YYYY,DDD,HH:MM:SS
   */
  public static StringBuilder toDOYString(Timestamp ts) {
    StringBuilder ret;
    synchronized (gstat) {
      gstat.setTimeInMillis(ts.getTime());
      ret = toDOYString(gstat);
    }
    return ret;
  }

  /**
   * return a DOY formated string from a TimeStamp
   *
   * @param gc The time gregorian
   * @param tmp StringBuilder to add this DOY string
   * @return string of form YYYY,DDD,HH:MM:SS
   */
  public static StringBuilder toDOYString(GregorianCalendar gc, StringBuilder tmp) {
    StringBuilder sb = tmp;
    try {
      if (sb == null) {
        sb = Util.clear(nextPadsb());
      }
      Util.append(gc.get(Calendar.YEAR), 4, ' ', sb).append(",");
      Util.append(gc.get(Calendar.DAY_OF_YEAR), 3, '0', sb).append(",");
      Util.append(gc.get(Calendar.HOUR_OF_DAY), 2, '0', sb).append(":");
      Util.append(gc.get(Calendar.MINUTE), 2, '0', sb).append(":");
      Util.append(gc.get(Calendar.SECOND), 2, '0', sb).append(".");
      Util.append(gc.get(Calendar.MILLISECOND), 3, '0', sb);
    } catch (RuntimeException e) {
      e.printStackTrace();
    }
    return sb;
  }

  /**
   * return a DOY formated string from a TimeStamp
   *
   * @param time The time in millis
   * @param tmp A string builder to put answer in, if null, use an internal string builder
   * @return string of form YYYY,DDD,HH:MM:SS
   */
  public static StringBuilder toDOYString(long time, StringBuilder tmp) {
    StringBuilder ret;
    synchronized (gstat2) {
      gstat2.setTimeInMillis(time);
      ret = toDOYString(gstat2, tmp);
    }
    return ret;
  }

  /**
   * * return the current date as yyyy/mm/dd
   *
   * @return The current data
   */
  public static StringBuilder ascdate() {
    return ascdate(System.currentTimeMillis(), null);
  }

  /**
   * * return the given GreogoianCalendar date as yyyy/mm/dd
   *
   * @param d A GregorianCalendar to translate
   * @return The current data
   */
  public static StringBuilder ascdate(GregorianCalendar d) {
    return ascdate(d.getTimeInMillis(), null);
  }

  /**
   * * return the given GreogoianCalendar date as yyyy/mm/dd
   *
   * @param ms epoch millis
   * @return The current data
   */
  public static StringBuilder ascdate(long ms) {
    return ascdate(ms, null);
  }

  /**
   * * return the given GreogoianCalendar date as yyyy/mm/dd
   *
   * @param ms A milliseconds value to translate (1970 or GregorianCalendar datum)
   * @param tmp A StringBuilder to add this date to or if null a temp string builder is returned.
   * @return The current data
   */
  public static StringBuilder ascdate(long ms, StringBuilder tmp) {
    StringBuilder sb = tmp;
    try {
      if (sb == null) {
        sb = Util.clear(nextDatesb());
      }
      synchronized (gstat) {
        gstat.setTimeInMillis(ms);
        sb.append(gstat.get(Calendar.YEAR)).append("/");
        Util.append(gstat.get(Calendar.MONTH) + 1, 2, '0', sb).append("/");
        Util.append(gstat.get(Calendar.DAY_OF_MONTH), 2, '0', sb);
      }
    } catch (RuntimeException e) {
      e.printStackTrace();
    }
    return sb;
  }

  /**
   * return the given long millisecond date as "yyyy/mm/dd hh:mm:ss"
   *
   * @param d The millisecond date
   * @return the date/time string
   */
  public static StringBuilder ascdatetime(double d) {
    return ascdatetime((long) d, null);
  }

  /**
   * return the given long millisecond date as "yyyy/mm/dd hh:mm:ss"
   *
   * @param d The millisecond
   * @param tmp A string builder to add to or if null an internal one is returned
   * @return the date/time string
   */
  public static StringBuilder ascdatetime(long d, StringBuilder tmp) {
    StringBuilder sb = ascdatetime2(d, tmp);
    try {
      sb.delete(19, sb.length());
    } catch (RuntimeException e) {
      Util.prt("Unusual runtime makeing ascdatetime(long,sb) continue " + e);
      e.printStackTrace();
    }
    return sb;
  }

  /**
   * return the given GregorianCalendar date as "yyyy/mm/dd hh:mm:ss"
   *
   * @param d The GregorianCalendar
   * @return the date/time string
   */
  public static StringBuilder ascdatetime(GregorianCalendar d) {
    if (d == null) {
      return ascdatetime(System.currentTimeMillis(), null);
    }
    return ascdatetime(d.getTimeInMillis(), null);
  }

  /**
   * return the given long millisecond date as "yyyy/mm/dd hh:mm:ss.mm"
   *
   * @param d The millisecond date
   * @return the date/time string
   */
  public static StringBuilder ascdatetime2(double d) {
    return ascdatetime2((long) d, null);
  }

  /**
   * return the given long millisecond date as "yyyy/mm/dd hh:mm:ss.mm"
   *
   * @param d The millisecond date
   * @return the date/time string
   */
  public static StringBuilder ascdatetime2(long d) {
    return ascdatetime2((long) d, null);
  }

  /**
   * return the given GregorianCalendar date as "yyyy/mm/dd hh:mm:ss.mm"
   *
   * @param d The GregorianCalendar
   * @return the date/time string
   */
  public static StringBuilder ascdatetime2(GregorianCalendar d) {
    if (d == null) {
      return Util.clear(Util.nextDatesb()).append("null");
    }
    return ascdatetime2(d.getTimeInMillis(), null);
  }

  /**
   * return the given long millisecond date as "yyyy/mm/dd hh:mm:ss.mm"
   *
   * @param sb The user string builder or null for an internal one
   * @return the date/time string
   */
  public static StringBuilder ascdatetime2(StringBuilder sb) {
    return ascdatetime2(System.currentTimeMillis(), sb);
  }

  /**
   * return the given long millisecond date as "yyyy/mm/dd hh:mm:ss.mm"
   *
   * @return the date/time string
   */
  public static StringBuilder ascdatetime2() {
    return ascdatetime2(System.currentTimeMillis(), null);
  }

  /**
   * return the given long millisecond date as "yyyy/mm/dd hh:mm:ss.mm"
   *
   * @param d The millisecond date
   * @param sb
   * @return the date/time string
   */
  public static StringBuilder ascdatetime2(GregorianCalendar d, StringBuilder sb) {
    if (d == null) {
      if (sb == null) {
        return Util.clear(Util.nextDatesb()).append("null");
      } else {
        return sb.append("null");
      }
    }
    return ascdatetime2(d.getTimeInMillis(), sb);
  }

  /**
   * return the given long millisecond date as "yyyy/mm/dd hh:mm:ss.mm"
   *
   * @param ms The millisecond date
   * @param tmp The stringBuilder to add this data to, if null, a temporary one is returned, but it
   * will be wiped soon - if null internal will be used
   * @return the date/time string
   */
  public static StringBuilder ascdatetime2(long ms, StringBuilder tmp) {
    StringBuilder sb = tmp;
    try {
      if (sb == null) {
        sb = Util.clear(nextDatesb());
      }
      synchronized (gstat) {
        gstat.setTimeInMillis(ms);
        Util.append(gstat.get(Calendar.YEAR), 4, ' ', sb).append("/");
        Util.append(gstat.get(Calendar.MONTH) + 1, 2, '0', sb).append("/");
        Util.append(gstat.get(Calendar.DAY_OF_MONTH), 2, '0', sb).append(" ");
        Util.append(gstat.get(Calendar.HOUR_OF_DAY), 2, '0', sb).append(":");
        Util.append(gstat.get(Calendar.MINUTE), 2, '0', sb).append(":");
        Util.append(gstat.get(Calendar.SECOND), 2, '0', sb).append(".");
        Util.append(gstat.get(Calendar.MILLISECOND), 3, '0', sb);
      }
    } catch (RuntimeException e) {
      e.printStackTrace();
    }
    return sb;
  }

  /**
   * return current date (based on system time) as an SQL Date
   *
   * @return the current time in SQL Time form
   */
  public static Time time() {
    return new Time(System.currentTimeMillis());
  }

  /**
   * return current date (based on system time) as an SQL Date
   *
   * @return The curent time/date as a Timestamp
   */
  public static Timestamp now() {
    return new Timestamp(System.currentTimeMillis());
  }

  /**
   * get time in millis (this is the same as System.currentTimeInMillis()
   *
   * @return THe current time in millis
   */
  public static long getTimeInMillis() {
    return System.currentTimeMillis();
  }

  /**
   * dateToString takes a JDBC/SQL date and makes it a mm/dd/yyyy string
   *
   * @param d ate to translate to string
   * @return The ascii string in yyyy/mm/dd
   */
  public static String dateToString(java.sql.Date d) {
    if (d == null) {
      return "";
    }
    String s = d.toString();    // returns yyyy-mm-dd
    if (s == null) {
      return "";
    }
    if (s.equals("null")) {
      return "";
    }
//    Util.prt("datetostring="+s);
    StringTokenizer tk = new StringTokenizer(s, "-");

    int yr = Integer.parseInt(tk.nextToken());
    int mon = Integer.parseInt(tk.nextToken());
    int day = Integer.parseInt(tk.nextToken());
    return "" + mon + "/" + day + "/" + yr;

  }

  /**
   * dateToString takes a JDBC date and makes it a mm/dd/yyyy string
   *
   * @param d ate to translate to string
   * @return The ascii string in yyyy/mm/dd
   */
  public static StringBuilder dateToString(java.util.Date d) {
    if (d == null) {
      return Util.clear(nextDatesb());
    }
    return Util.ascdate(d.getTime(), null);

    /*if(s == null) return "";
    if(s.equals("null")) return "";
//    Util.prt("datetostring="+s);
    StringTokenizer tk = new StringTokenizer(s,"-/");

    int yr = Integer.parseInt(tk.nextToken());
    int mon= Integer.parseInt(tk.nextToken());
    int day  = Integer.parseInt(tk.nextToken());
    return ""+mon+"/"+day+"/"+yr;*/
  }

  /**
   * return the current system time as an SQL Timestamp.
   *
   * @return the current time as a Timestamp
   */
  public static Timestamp TimestampNow() {
    return new Timestamp(System.currentTimeMillis());
  }

  /**
   * convert a zero terminated C string to a Java string
   *
   * @param bb The byte buffer positioned to the beginning of the C string
   * @param max The maximum length of the string (field length)
   * @return The resulting String
   */
  public static String stringFromCString(ByteBuffer bb, int max) {
    int pos = bb.position();
    while (bb.get() != 0) {
    }
    int length = bb.position() - pos - 1;
    if (length > max) {
      length = max;
    }
    bb.position(pos);
    byte[] b = new byte[length];
    bb.get(b);
    bb.position(pos + max);
    return new String(b);
  }

  /**
   * Convert a yyyy/mm/dd string to a full java.sql.Date. No times are permitted. If the yy is two
   * digits it is converted by the rule of 80 (80-99 > 1900+yy), yy < 80, year=2000+yy. For more
   * flexible conversions including ones with times seed seedToDate2. Ether slash or hyphen can be
   * used to separate the fields.
   *
   * @param s string to decode to a sql date yyyy/mm/dd
   * @return The sql date from the string
   */
  public static java.sql.Date stringToDate(String s) {
    StringTokenizer tk = new StringTokenizer(s, "/-");
    if (tk.countTokens() < 2) {
      Util.prt("stringToDate not enough Tokens s=" + s + " cnt="
              + tk.countTokens());
      return Util.date(1970, 1, 1);
    }
    String mon = tk.nextToken();
    String day = tk.nextToken();
    String yr;
    int m, d, y;
    if (tk.hasMoreTokens()) {
      yr = tk.nextToken();
    } else {
      yr = "" + Util.year();
    }
    try {
      m = Integer.parseInt(mon);
      d = Integer.parseInt(day);
      y = Integer.parseInt(yr);
    } catch (NumberFormatException e) {
      Util.prt("dateToString() Month or day not a int mon=" + mon + " day=" + day);
      return Util.date(Util.year(), 1, 1);
    }
    if (m <= 0 || m > 12) {
      Util.prt("stringToDate : bad month = " + mon + " s=" + s);
      return Util.date(1970, 1, 1);
    }
    if (d <= 0 || d > 31) {
      Util.prt("stringToDate : bad day = " + day + " s=" + s);
      return Util.date(1970, 1, 1);
    }
    if (y < 100) {
      if (y > 80) {
        y += 1900;
      } else {
        y += 2000;
      }
    }

    return Util.date(y, m, d);
  }

  /**
   * convert a year and day of year to an array in yr,mon,day order
   *
   * @param yr The year
   * @param doy The day of the year
   * @return an array in yr, mon, day
   * @throws RuntimeException if its mis formatted
   */
  public static int[] ymd_from_doy(int yr, int doy) throws RuntimeException {
    int[] ymd = new int[3];
    ymd_from_doy(yr, doy, ymd);
    return ymd;
  }

  /**
   * convert a year and day of year to an array in yr,mon,day order
   *
   * @param yr The year
   * @param doy The day of the year
   * @param ymd The array of 3 ints to return the answer
   * @throws RuntimeException if its mis formatted
   */
  public static void ymd_from_doy(int yr, int doy, int[] ymd) throws RuntimeException {
    int[] daytab = new int[]{0, 31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31};
    int[] dayleap = new int[]{0, 31, 29, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31};
    int j;
    int sum;
    if (yr >= 60 && yr < 100) {
      yr = yr + 1900;
    } else if (yr < 60 && yr >= 0) {
      yr = yr + 2000;
    }
    boolean leap = yr % 4 == 0 && yr % 100 != 0 || yr % 400 == 0;
    /* is it a leap year */
    sum = 0;
    ymd[0] = yr;
    if (leap) {
      for (j = 1; j <= 12; j++) {
        if (sum < doy && sum + dayleap[j] >= doy) {
          ymd[1] = j;
          ymd[2] = doy - sum;
          return;
        }
        sum += dayleap[j];
      }
    } else {
      for (j = 1; j <= 12; j++) {
        if (sum < doy && sum + daytab[j] >= doy) {
          ymd[1] = j;
          ymd[2] = doy - sum;
          return;
        }
        sum += daytab[j];
      }
    }
    System.out.println("ymd_from_doy: impossible drop through!   yr=" + yr + " doy=" + doy);
    throw new RuntimeException("ymd_from_DOY : impossible yr=" + yr + " doy=" + doy);

  }

  /**
   * Convert a yyyy/mm/dd string to a full Date Of the form. This allows both / and - date
   * separators. Any parts of the time may be omitted. The position between the date and time can be
   * a Space, colon, slash, hyphen, period or Capital T. Examples:
   * <br>yyyy/mm/dd/yyyy hh:mm:ss, or
   * <br>yyyy-mm/dd hh:mm:ss or
   * <br>yyyy,doy hh:mm:ss
   * <br>yyyy-mm-ddThh:mm
   * <br>yyyy-mm-dd hh:mm
   *
   * @param s The string to encode
   * @return The java.util.Date representing the string or a date in 1970 if the string is bad.
   */
  public static java.util.Date stringToDate2(String s) {
    StringTokenizer tk;
    String yr;
    String mon;
    String day;
    if (s.indexOf(",") > 0) {  // must be yyyy,doy format
      tk = new StringTokenizer(s, ", -:.");
      if (tk.countTokens() == 2) {

      } else if (tk.countTokens() < 4) {
        Util.prt("StringToDate2 not enough tokens for doy form s=" + s + " cnt=" + tk.countTokens());
        return Util.date(1970, 1, 1);
      }
      yr = tk.nextToken();
      int doy = Integer.parseInt(tk.nextToken());
      int[] ymd = ymd_from_doy(Integer.parseInt(yr), doy);
      yr = "" + ymd[0];
      mon = "" + ymd[1];
      day = "" + ymd[2];
    } else {
      tk = new StringTokenizer(s, "/ -:.T");
      if (tk.countTokens() < 5 && tk.countTokens() < 3) {
        Util.prt("stringToDate no enough Tokens s=" + s + " cnt="
                + tk.countTokens());
        return Util.date(1970, 1, 1);
      }
      yr = tk.nextToken();
      mon = tk.nextToken();
      day = tk.nextToken();
    }
    String hr = "00";
    String min = "00";
    String sec = "00";
    String frac = "0";
    int m, d, y, h, mn;
    int sc, ms;
    if (tk.hasMoreTokens()) {
      hr = tk.nextToken();
    }
    if (tk.hasMoreTokens()) {
      min = tk.nextToken();
    }
    if (tk.hasMoreTokens()) {
      sec = tk.nextToken();
    }
    if (tk.hasMoreTokens()) {
      frac = tk.nextToken();

    }
    try {
      m = Integer.parseInt(mon);
      d = Integer.parseInt(day);
      y = Integer.parseInt(yr);
      h = Integer.parseInt(hr);
      mn = Integer.parseInt(min);
      sc = Integer.parseInt(sec);
      ms = Integer.parseInt(frac);
      if (frac.length() == 1) {
        ms = ms * 100;
      }
      if (frac.length() == 2) {
        ms = ms * 10;
      }
      if (frac.length() == 4) {
        ms = ms / 10;
      }
      if (frac.length() == 5) {
        ms = ms / 100;
      }
      if (frac.length() == 6) {
        ms = ms / 1000;
      }
    } catch (NumberFormatException e) {
      Util.prt("dateToString2() fail to decode ints s=" + s);
      return Util.date(1970, 1, 1, 0, 0, 0);
    }
    if (m <= 0 || m > 12) {
      Util.prt("stringToDate : bad month = " + mon + " s=" + s);
      return Util.date(1970, 1, 1, 0, 0, 0);
    }
    if (d <= 0 || d > 31) {
      Util.prt("stringToDate : bad day = " + day + " s=" + s);
      return Util.date(1970, 1, 1, 0, 0, 0);
    }
    if (y < 100) {
      if (y > 80) {
        y += 1900;
      } else {
        y += 2000;
      }
    }
    if (h < 0 || h > 23) {
      Util.prt("stringToDate2 : bad hour = " + hr + " s=" + s);
      return Util.date(1970, 1, 1, 0, 0, 0);
    }
    if (mn < 0 || mn > 59) {
      Util.prt("stringToDate2 : bad min = " + mn + " s=" + s);
      return Util.date(1970, 1, 1, 0, 0, 0);
    }
    if (sc < 0 || sc > 59) {
      Util.prt("stringToDate2 : bad sec = " + sc + " s=" + s);
      return Util.date(1970, 1, 1, 0, 0, 0);
    }

    java.util.Date dd = Util.date(y, m, d, h, mn, sc);
    if (ms != 0) {
      dd.setTime(dd.getTime() + ms);
    }
    return dd;
  }

  /**
   * a quick hack to return the current year
   *
   * @return the current year
   */
  public static int year() {
    gstat.setTimeInMillis(System.currentTimeMillis());
    return gstat.get(Calendar.YEAR);
  }

  /**
   * Left pad a string s to Width.
   *
   * @param s The string to pad
   * @param width The desired width
   * @return The padded string to width
   */
  public static StringBuilder leftPad(String s, int width) {
    StringBuilder sb = nextPadsb();
    try {
      Util.clear(sb).append(s);
    } catch (RuntimeException e) {
      e.printStackTrace();
    }
    return leftPad(sb, width);

    /* String tmp="";
    int npad = width - s.length();
    if( npad < 0) tmp = s.substring(0 ,width);
    else if( npad == 0) tmp = s;
    else {
      for (int i = 0; i < npad; i++) tmp += " ";
      tmp += s;
    }
    return tmp;*/
  }

  public static StringBuilder leftPad(int s, int width) {
    StringBuilder sb = nextPadsb();
    try {
      Util.clear(sb).append(s);
    } catch (RuntimeException e) {
      e.printStackTrace();
    }
    return leftPad(sb, width);
  }

  public static StringBuilder leftPad(long s, int width) {
    StringBuilder sb = nextPadsb();
    try {
      Util.clear(sb).append(s);
      return leftPad(sb, width);
    } catch (RuntimeException e) {
      e.printStackTrace();
    }
    return sb;
  }

  public static StringBuilder rightPad(int s, int width) {
    StringBuilder sb = nextPadsb();
    try {
      Util.clear(sb).append(s);
      return rightPad(sb, width);
    } catch (RuntimeException e) {
      e.printStackTrace();
    }
    return sb;
  }

  public static StringBuilder rightPad(long s, int width) {
    StringBuilder sb = nextPadsb();
    try {
      Util.clear(sb).append(s);
      return rightPad(sb, width);
    } catch (RuntimeException e) {
      e.printStackTrace();
    }
    return sb;
  }

  /**
   * Left pad a string s to Width.
   *
   * @param s The string to pad
   * @param width The desired width
   * @return The padded string to width
   */
  public static StringBuilder leftPad(StringBuilder s, int width) {
    try {
      while (s.length() < width) {
        s.insert(0, ' ');
      }
    } catch (RuntimeException e) {
      e.printStackTrace();
    }
    return s;
  }

  /**
   * pad on right side of string to width. Used to create "fixed field" lines
   *
   * @param s The string to pad
   * @param width The desired width
   * @return The padded string to width
   */
  public static StringBuilder rightPad(String s, int width) {
    StringBuilder sb = nextPadsb();
    try {
      Util.clear(sb).append(s);
      return rightPad(sb, width);
    } catch (RuntimeException e) {
      e.printStackTrace();
    }
    return sb;
  }

  /**
   * pad on right side of string to width. Used to create "fixed field" lines
   *
   * @param s The string to pad
   * @param width The desired width
   * @return The padded string to width
   */
  public static StringBuilder rightPad(StringBuilder s, int width) {
    try {
      while (s.length() < width) {
        s.append(" ");
      }
    } catch (RuntimeException e) {
      e.printStackTrace();
    }
    return s;
  }

  /**
   * Pad both sides of a string to width Width so String is "centered" in field
   *
   * @param s The string to pad
   * @param width The desired width
   * @return The padded string to width
   */
  public static StringBuilder centerPad(String s, int width) {
    return centerPad(Util.clear(nextPadsb()).append(s), width);
  }

  /**
   * Pad both sides of a string to width Width so String is "centered" in field
   *
   * @param s The string to pad
   * @param width The desired width
   * @return The padded string to width
   */
  public static StringBuilder centerPad(StringBuilder s, int width) {
    try {
      while (s.length() < width) {
        if (s.length() < width) {
          s.insert(0, ' ');
        }
        if (s.length() < width) {
          s.append(' ');
        }
      }
    } catch (RuntimeException e) {
      e.printStackTrace();
    }
    return s;
  }

  /**
   * Exit using System.exit() after printing the "in" string. Use so its easier on Post-mortem to
   * see where exit occured.
   *
   * @param in a string to print before exiting.
   */
  public static void exit(String in) {
    exit(in, 0);
  }

  /**
   * Exit using System.exit() using then in exit code. Use so its easier on Post-mortem to see where
   * exit occurred.
   *
   * @param in an exit code to pass to System.exit()
   */
  public static void exit(int in) {
    exit("" + in, in);
  }

  /**
   * Exit using System.exit() after printing the "in" string. Use so its easier on Post-mortem to
   * see where exit occurred.
   *
   * @param in a string to print before exiting.
   * @param code The exit code to pass to system.exit
   */
  public static void exit(String in, int code) {
    Util.prt(in);
    isShuttingDown = true;
    new RuntimeException("Util.exit(" + in + "," + code + ") called.  Dump stack to identify caller in post mortem pid=" + pid).printStackTrace();
    if (!OS.contains("Window")) {
      try {
        Subprocess sp = new Subprocess("bash -l kill9.bash " + pid + " util-" + username);
        Util.sleep(500);
        Util.prt("kill9.bash stdout=" + sp.getOutput() + " stderr=" + sp.getErrorOutput());
        Subprocess sp2;
        if (Util.getProperty("stackdump") != null) {
          if (OS.contains("Mac")) {
            sp2 = new Subprocess("/usr/bin/jstack -l -F " + Util.getPID());
          } else {
            sp2 = new Subprocess("/usr/java/bin/jstack -l -F " + Util.getPID());
          }
          try {
            sp2.waitFor(10000);
          } catch (InterruptedException e) {
          }
          String s = sp2.getOutput();
          StringBuilder tmp = new StringBuilder(s.length());
          tmp.append(s);
          Util.writeFileFromSB("stack_" + Util.getPID(), tmp);
        }
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
    System.exit(code);
  }

  /**
   * return the properties
   *
   * @return The Properties
   */
  public static Properties getProperties() {
    return prop;
  }

  /**
   * set a property pere key value pair
   *
   * @param tag The key in the property
   * @param val The value to set it to
   */
  public static void setProperty(String tag, String val) {
    if (debug_flag) {
      Util.prt("set prop " + tag + " to " + val);
    }
    prop.setProperty(tag, val);
  }

  /**
   * Because this is run before the Util.prt out variable is set, it cannot use prt(). Load the
   * properties from a file. First the file is check to see if it exists in user.home and then in
   * user.home/Properties.
   *
   * @param filename The file to load from
   */
  public static void loadProperties(String filename) {

    if (filename.equals("")) {
      return;
    }
    if (propertyFile == null) {
      init(filename);
      return;
    }    // note: init will call this routine so we need to end recursion here
    if (debug_flag) {
      System.out.println(" # default props=" + defprops.size());
    }
    if (debug_flag) {
      System.out.println(" prop after=" + prop.size());
    }
    if(propfilename != null) {
      if(propfilename.equals(userhome + FS + filename) || 
              propfilename.equals(userhome + FS + "Properties" + FS + filename)) return;
    }
    //new RuntimeException ("LoadProperties file="+userhome+FS+filename+"/"+propfilename+" process="+Util.getProcess()+" continue").printStackTrace();
    if(filename.contains(FS)) {
      propfilename = filename;
    }
      else {
      propfilename = userhome + FS + filename;
      File f = new File(propfilename);
      String propfilename2 = userhome + FS + "Properties" + FS + filename;
      File f2 = new File(propfilename2);
      if (f.exists()) {
        if (f2.exists()) {
          System.out.println("*** Both " + propfilename + " and " + propfilename2 + " exist.  Using " + propfilename);
        }
        //loadProperties(propfilename);
      } else {
        if (f2.exists()) {
          propfilename = propfilename2;     // The /User/.prop does not exist, use /user/Properties/prop
        } else {
          System.out.println("*** Property file " + filename + " does not exist in $HOME or $HOME/Properties");
          return;
        }
      }
    }

    if (debug_flag) {
      System.out.println("Load properties from " + propfilename + " procname=" + getProcess());
    }
    //prta("Load properties from "+propfilename + " procname=" + getProcess());
    //new RuntimeException(asctime()+" Loading prop="+propfilename+" continue").printStackTrace(out != null?out:stdout);
    try {
      try (FileInputStream i = new FileInputStream(propfilename)) {
        prop.load(i);
        //prtProperties();
      }
    } catch (FileNotFoundException e) {
      System.out.println(" ** Properties file not found=" + propfilename + " userhome=" + userhome + " " + System.getProperty("user.home"));
      //saveProperties();
    } catch (IOException e) {
      System.out.println("IOException reading properties file=" + propfilename);
      exit("Cannot load properties");
    }
    //File f = new File(propfilename);
    //Util.prt(" file="+propfilename+" DBServer="+Util.getProperty("DBServer")+" f="+f.getAbsolutePath());
  }

  /**
   * print out the current Property pairs
   */
  public static void prtProperties() {
    if (out == null) {
      prop.list(System.out);
    } else {
      prop.list(out);
    }
  }

  /**
   * print out the current Property pairs
   *
   * @param out2 A print stream to print it on
   */
  public static void prtProperties(PrintStream out2) {
    if (out2 != null) {
      prop.list(out2);
    } else {
      prop.list(out);
    }
  }

  /**
   * return the value of a given property
   *
   * @param key The name/key of the property
   * @return the value associated with the key
   */
  public static String getProperty(String key) {
    if (prop == null) {
      return null;
    }
    return prop.getProperty(key);
  }

  /**
   * save the properties to a file
   */
  public static void saveProperties() {
    //if(debug_flag) 
    System.out.println(Util.asctime() + " Saving properties to " + propfilename + " nkeys=" + prop.size());
    try {
      Util.chkFilePath(propfilename);
      try (FileOutputStream o = new FileOutputStream(propfilename)) {
        prop.store(o, propfilename + " by " + getProcess() + " via Util.saveProperties() cp="
                + System.getProperties().getProperty("java.class.path"));
      }
    } catch (FileNotFoundException e) {
      System.out.println("Could not write properties to " + propfilename);
      exit("Cannot write Properties");
    } catch (IOException e) {
      System.out.println("Write error on properties to " + propfilename);
      exit("Cannot write properties");
    }
  }

  /**
   * conveniently get the value associated with the MySQLServer key
   *
   * @return The value of the MySQLServer key
   */
  public static String getMySQLServer() {
    return prop.getProperty("MySQLServer");
  }

  /**
   * conveniently get the value associated with the MySQLServer key
   *
   * @return The value of the MySQLServer key
   */
  public static String getDBServer() {
    return prop.getProperty("DBServer");
  }

  /**
   * conveniently get the value of the StatusServer
   *
   * @return The StatusServer property
   */
  public static String getStatusServer() {
    return prop.getProperty("StatusServer");
  }

  /**
   * Return just the IP from a URL for database access
   *
   * @param url The DB URL string
   * @return The IP portion
   */
  public static String DBURL2IP(String url) {
    if (url == null) {
      return null;
    }
    String[] parts = url.split("[/:]");
    if (parts.length >= 1) {
      return parts[0];
    }
    return null;
  }

  /**
   * add a default property
   *
   * @param tag The key of the property
   * @param value The value of the default property
   */
  public static void addDefaultProperty(String tag, String value) {
    defprops.setProperty(tag, value);

  }

  /**
   * a simple assert routine - prints if string are not equal
   *
   * @param tag Some text to print if assert fails
   * @param s1 A string which is tested against s2
   * @param s2 A string to test
   */
  public static void assertEquals(String tag, String s1, String s2) {
    if (s1.equals(s2)) {
      Util.prt("ASSERT: " + tag + " is o.k.");
    } else {
      Util.prt(tag + "ASSERT: fails " + s1);
      Util.prt(tag + "Assert  !=    " + s2);
    }
  }

  /**
   * Creates a new instance of Subprocess
   *
   * @param cmd A command string to process
   * @return An array of strings of parsed tokens.
   * @throws IOException If reading stderr or stdout gives an error
   */
  public static String[] parseCommand(String cmd) throws IOException {

    // Start using bash , -c means use next command as start of line
    String[] cmdline = new String[100];
    int pos = cmd.indexOf(">");
    if (pos >= 0) {
      cmd = cmd.substring(0, pos);
    }

    // Break this up into the command elements in quotes
    StringTokenizer tk = new StringTokenizer(cmd, "\"\'");
    int i = 0;
    int narg = 0;
    while (tk.hasMoreTokens()) {
      if (i % 2 == 0) {
        String[] args = tk.nextToken().split("\\s");
        for (String arg : args) {
          cmdline[narg++] = arg;
        }
      } else {
        cmdline[narg++] = tk.nextToken();     // this is a quoted string
      }
      i++;
    }
    String[] finalcmd = new String[narg];
    for (i = 0; i < narg; i++) {
      //prt(i+"="+cmdline[i]);
      finalcmd[i] = cmdline[i];
    }
    return finalcmd;
  }

  /**
   * Take some bytes from an byte array and make a printable StringBuilder
   *
   * @param buf A byte buffer
   * @param len Number of bytes to dump
   * @param tmpsb A StringBuilder to receive the output - this will be cleared before any processing
   * @return The input StringBuilder
   */
  public static StringBuilder bytesToSB(byte[] buf, int len, StringBuilder tmpsb) {
    Util.clear(tmpsb);
    for (int i = 0; i < Math.min(len, buf.length); i++) {
      tmpsb.append(Util.leftPad(i, 3)).append(" ").append(Util.leftPad(buf[i], 4)).append(" ch=").append(Util.leftPad(Util.toPrintable(buf[i]), 5)).append(" | ");
      if (i % 8 == 7) {
        Util.prt(tmpsb);
        Util.clear(tmpsb);
      }
    }
    return tmpsb;
  }

  public static StringBuilder bufToSB(byte[] buf, int off, int len, StringBuilder tmpsb) {
    Util.clear(tmpsb);
    for (int i = off; i < Math.min(off + len, buf.length); i++) {
      tmpsb.append((char) buf[i]);
    }
    return tmpsb;
  }

  public static StringBuilder toPrintable(byte b) {
    StringBuilder sb = Util.clear(nextPadsb());
    try {
      if (b < 32 || b == 127) {
        sb.append(Util.toHex(b));
      } else {
        sb.append((char) b);
      }
    } catch (RuntimeException e) {
      e.printStackTrace();
    }
    return sb;
  }

  /**
   * Print a string in all printable characers, take non-printable to their hex vales
   *
   * @param s The string to print after conversion
   * @return The String with non-printables converted
   */
  public static StringBuilder toAllPrintable(String s) {
    byte[] b = s.getBytes();
    StringBuilder sb = Util.clear(nextPadsb());
    try {
      for (int i = 0; i < s.length(); i++) {
        if (b[i] < 32 || b[i] == 127) {
          sb.append(Util.toHex(b[i]));
        } else {
          sb.append(s.charAt(i));
        }
      }
    } catch (RuntimeException e) {
      e.printStackTrace();
    }
    return sb;
  }

  /**
   * Print a string in all printable characters, take non-printable to their hex vales
   *
   * @param s The string to print after conversion
   * @return The String with non-printables converted
   */
  public static StringBuilder toAllPrintable(CharSequence s) {
    StringBuilder sb = Util.clear(nextPadsb());
    try {
      for (int i = 0; i < s.length(); i++) {
        if (s.charAt(i) < 32 || s.charAt(i) == 127) {
          sb.append(Util.toHex(s.charAt(i)));
        } else {
          sb.append(s.charAt(i));
        }
      }
    } catch (RuntimeException e) {
      e.printStackTrace();
    }
    return sb;
  }

  /* Test the latest things added.  Many test functions were removed as the
	 *debugging was completed.
	 
  public  static  void  main3  (String  []  args)  {
    JDBConnection  jcjbl;
    Connection  C;
    User user=new User("dkt");
    try  {
      jcjbl  =  new  JDBConnection(UC.JDBCDriver(),  UC.JDBCDatabase());
      C  =  jcjbl.getConnection();
      UC.setConnection(C);
      
      Util.setApplet(true);
      user=new User(C,"dkt","karen");
    } catch (JCJBLBadPassword e) {
      System.err.println("Password must be wrong on User construction");
    } catch  (SQLException  e)  {
      Util.SQLErrorPrint(e," Main SQL unhandled=");
      System.err.println("SQLException  on  getting test $DBObject");
    }

    GregorianCalendar g = new GregorianCalendar();
    Util.prt("asctime="+asctime(g)+" ascdate="+ascdate(g));
    Time t = new Time((long) 12*3600000+11*60000+13000);
    String s = Util.timeToString(t);
    Util.prt(t.toString()+" timeToString returned=" + s);
    t = new Time((long) 11*3600000+11*60000+13000);
    s = Util.timeToString(t);
    Util.prt(t.toString()+" timeToString returned=" + s);
    t = new Time(15*3600000+11*60000+13000);
    s = Util.timeToString(t);
    Util.prt(t.toString()+" timeToString returned=" + s);
   
    s="10:31 am";
    t = Util.stringToTime(s);
    Util.prt(s+" from string returned " + t.toString());
    s="10:32 pm";
    t = Util.stringToTime(s);
    Util.prt(s+ " from string returned " + t.toString());
    s="10:31";
    t = Util.stringToTime(s);
    Util.prt(s+" from string returned " + t.toString());
    s="3:30";
    t = Util.stringToTime(s);
    Util.prt(s+" from string returned " + t.toString());
    s="a3:30";
    t = Util.stringToTime(s);
    Util.prt(s+" from string returned " + t.toString());
    s="3:3d";
    t = Util.stringToTime(s);
    Util.prt(s+" from string returned " + t.toString());
    s="3:30 ap";
    t = Util.stringToTime(s);
    Util.prt(s+" from string returned " + t.toString());
    
    Date d = Util.date(2000,3,1);
    Util.prt("2000,3,1 gave "+ Util.dateToString(d));
    d=Util.date(2000,12,30);
    Util.prt("2000,12,30 gave "+ Util.dateToString(d));

    s = "1/1";
    d=Util.stringToDate(s);
    Util.prt(s + " returned " + d.toString());
     s = "12/31";
    d=Util.stringToDate(s);
    Util.prt(s + " returned " + d.toString());
    s = "1/1/2000";
    d=Util.stringToDate(s);
    Util.prt(s + " returned " + d.toString());
    s = "09/1/00";
    d=Util.stringToDate(s);
    Util.prt(s + " returned " + d.toString());
    s = "01/12/99";
    d=Util.stringToDate(s);
    Util.prt(s + " returned " + d.toString());
    s = "13/1";
    d=Util.stringToDate(s);
    Util.prt(s + " returned " + d.toString());
    
    s = "12/32";
    d=Util.stringToDate(s);
    Util.prt(s + " returned " + d.toString());
    Util.prt(""+stringToDate2("2006/1/1 12:00"));
    Util.prt(""+stringToDate2("6/1/1-12:01"));
    Util.prt(""+stringToDate2("2006,104 12:00"));
    Util.prt(""+stringToDate2("6,104-12:01:02"));
    
  }*/
  /**
   * convert to hex string
   *
   * @param b The item to convert to hex
   * @return The hex string
   */
  public static StringBuilder toHex(byte b) {
    return toHex(((long) b) & 0xFFL);
  }

  /**
   * convert to hex string
   *
   * @param b The item to convert to hex
   * @return The hex string
   */
  public static StringBuilder toHex(short b) {
    return toHex(((long) b) & 0xFFFFL);
  }

  /**
   * convert to hex string
   *
   * @param b The item to convert to hex
   * @return The hex string
   */
  public static StringBuilder toHex(int b) {
    return toHex(((long) b) & 0xFFFFFFFFL);
  }

  /**
   * convert to hex string
   *
   * @param i The item to convert to hex
   * @return The hex string
   */
  public static StringBuilder toHex(long i) {
    StringBuilder s = Util.clear(nextPadsb());
    try {
      int j = 60;
      int k;
      long val;
      char c;
      boolean flag = false;
      s.append("0x");

      for (k = 0; k < 16; k++) {
        val = (i >> j) & 0xf;
        //prt(i+" i >> j="+j+" 0xF="+val);
        if (val < 10) {
          c = (char) (val + '0');
        } else {
          c = (char) (val - 10 + 'a');
        }
        if (c != '0') {
          flag = true;
        }
        if (flag) {
          s.append(c);
        }
        j = j - 4;
      }
      if (!flag) {
        s.append("0");
      }
      return s;
    } catch (RuntimeException e) {
      e.printStackTrace();
    }
    return s;
  }

  /**
   * static method that insures a seedname makes some sense. 1) Name is 12 characters long
   * nnssssscccll. 2) All characters are characters, digits, spaces, question marks or dashes 3)
   * Network code contain blanks 4) Station code must be at least 3 characters long 5) Channel codes
   * must be characters in first two places
   *
   * @param name A seed string to check
   * @return True if seename passes tests.
   */
  public static boolean isValidSeedName(String name) {
    if (name.length() != 12) {
      return false;
    }

    char ch;
    //char [] ch = name.toCharArray();
    for (int i = 0; i < 12; i++) {
      ch = name.charAt(i);
      if (!(Character.isLetterOrDigit(ch) || ch == ' ' || ch == '?' || ch == '_'
              || ch == '-')) {
        return false;
      }
    }
    if (name.charAt(0) == ' ' /*|| name.charAt(1) == ' '*/) {
      return false;
    }
    if (name.charAt(2) == ' ' || name.charAt(3) == ' ') {
      return false;
    }
    return Character.isLetter(name.charAt(7)) && Character.isLetterOrDigit(name.charAt(8))
            && Character.isLetterOrDigit(name.charAt(9));
  }

  /**
   * static method that insures a seedname makes some sense. 1) Name is 12 characters long
   * nnssssscccll. 2) All characters are characters, digits, spaces, question marks or dashes 3)
   * Network code contain blanks 4) Station code must be at least 3 characters long 5) Channel codes
   * must be characters in first two places
   *
   * @param name A seed string to check
   * @return True if seename passes tests.
   */
  public static boolean isValidSeedName(StringBuilder name) {
    if (name.length() != 12) {
      return false;
    }

    char ch;
    //char [] ch = name.toCharArray();
    for (int i = 0; i < 12; i++) {
      ch = name.charAt(i);
      if (!(Character.isLetterOrDigit(ch) || ch == ' ' || ch == '?' || ch == '_'
              || ch == '-')) {
        return false;
      }
    }
    if (name.charAt(0) == ' ' /*|| name.charAt(1) == ' '*/) {
      return false;
    }
    if (name.charAt(2) == ' ' || name.charAt(3) == ' ') {
      return false;
    }
    return Character.isLetterOrDigit(name.charAt(7)) && Character.isLetterOrDigit(name.charAt(8))
            && Character.isLetterOrDigit(name.charAt(9));
  }

  /* Test routine
    /* @param args the command line arguments
   */
  public static void main(String[] args) {
    init("edge.prop");
    Util.prtinfo();
    String sys = Util.getSystemName();
    try {
      Util.prt("isNEICHost=" + isNEICHost() + " " + isNEICHost("137.227.224.97") + isNEICHost(InetAddress.getByName("136.177.20.195").getAddress()));
    } catch (UnknownHostException e) {
      e.printStackTrace();
    }
    System.exit(0);
    Util.prt("index of 123=" + charSequenceIndexOf("1234567890", "123"));
    Util.prt("index of 789=" + charSequenceIndexOf("1234567890", "789"));
    Util.prt("index of 890=" + charSequenceIndexOf("1234567890", "890"));
    Util.prt("index of 901=" + charSequenceIndexOf("1234567890", "901"));

    getRoles(null);
    Util.prt("node=" + Util.getNode() + " system number=" + Util.getSystemNumber());
    try {
      Util.prt(InetAddress.getLocalHost().getAddress()[0] + "." + InetAddress.getLocalHost().getAddress()[1]);
    } catch (UnknownHostException e) {
      e.printStackTrace();
    }
    Util.exit("");
    Util.prt(Util.deq("UT:Beaver;City Maint Yd"));
    StringBuilder sb1 = new StringBuilder(10);
    StringBuilder sb2 = new StringBuilder(10);
    sb1.append("12345");
    sb2.append("123456");
    int ans = Util.sbCompareTo(sb1, sb2);
    Util.prt(sb1 + " " + sb2 + " ans=" + ans);
    ans = Util.sbCompareTo(Util.clear(sb1).append("01234"), sb2);
    Util.prt(sb1 + " " + sb2 + " ans=" + ans);
    ans = Util.sbCompareTo(Util.clear(sb1).append("123456"), sb2);
    Util.prt(sb1 + " " + sb2 + " ans=" + ans);
    ans = Util.sbCompareTo(Util.clear(sb1).append("123"), sb2);
    Util.prt(sb1 + " " + sb2 + " ans=" + ans);
    ans = Util.sbCompareTo(Util.clear(sb1).append("1234567"), sb2);
    Util.prt(sb1 + " " + sb2 + " ans=" + ans);
    ans = Util.sbCompareTo(null, sb2);
    Util.prt("null " + sb2 + " ans=" + ans);
    ans = Util.sbCompareTo(Util.clear(sb1).append("01234"), null);
    Util.prt(sb1 + " null ans=" + ans);
    long big = 1;
    for (int i = 0; i < 12; i++) {
      big *= 37;
    }
    long big2 = big / 37 * 51;
    Util.prt("big=" + Util.toHex(big) + " big2=" + Util.toHex(big2) + " max=" + Util.toHex(Long.MAX_VALUE));
    StringBuilder sb = new StringBuilder(100);
    sb.append("edge^channel^channel=XXTEST BHZ;sendto=0;rate=100.;");
    Util.stringBuilderReplaceAll(sb, "sendto=0", "sendto=0");
    Util.prt(sb);
    Util.stringBuilderReplaceAll2(sb, "sendto=0", "sendto=0");
    Util.prt(sb);
    Util.stringBuilderReplaceAll(sb, "sendto=0", "sendto=11");
    Util.prt(sb);

    Util.clear(sb);
    sb.append("2#0");
    Util.rightPad(sb, 4);
    Util.prt(sb + "|");
    sb.append((String) null);
    Util.append(123, 4, ' ', sb).append(" ");
    Util.append(23, 3, '0', sb).append(",");
    Util.append(7, 2, '0', sb).append(":");
    Util.append(23, 2, '0', sb).append(":");
    Util.append(59, 2, '0', sb).append(".");
    Util.append(10, 4, '0', sb).append(" ");
    Util.prt(sb);
    int i4 = 32769;
    short i2 = (short) i4;
    Util.prt("I4=" + i4 + " i2=" + i2);

    Properties props = System.getProperties();
    Enumeration keys = props.keys();
    while (keys.hasMoreElements()) {
      String name = keys.nextElement().toString();
      Util.prt(name + "=" + System.getProperty(name));
    }
    Util.prt("node=" + Util.getNode());
    Util.prt("systemname=" + Util.getSystemName());
    String[] mroles = Util.getRoles(null);
    for (int i = 0; i < mroles.length; i++) {
      Util.prt("Role[" + i + "] is " + mroles[i]);
    }
    mroles = Util.getRoles(Util.getNode());
    for (int i = 0; i < mroles.length; i++) {
      Util.prt("Role node[" + i + "] is " + mroles[i]);
    }
    Util.prt("FreeSpace on /Users/ketchum=" + FreeSpace.getFree("/Users/ketchum"));
    System.exit(1);
    java.util.Date dd = Util.stringToDate2("2006/10/10 12:34:56.789");
    Util.prt(dd.toString() + " ms=" + (dd.getTime() % 1000));
    byte[] b = new byte[12];
    String s;
    b[0] = 0;
    b[1] = 'a';
    b[2] = 2;
    b[3] = 'b';
    b[4] = 127;
    b[5] = -2;
    b[6] = 'c';
    b[7] = 'd';
    b[8] = 'A';
    b[9] = 'B';
    b[10] = 126;
    b[11] = 1;
    s = new String(b);
    Util.prt(Util.toAllPrintable(s) + " should be " + "0x0a0x2b0x7f0xfecdAB~0x1");
    for (int i = 0; i < 128; i = i + 12) {
      for (int j = i; j < i + 12; j++) {
        b[j - i] = (byte) j;
      }
      s = new String(b);
      Util.prt("i=" + i + " " + Util.toAllPrintable(s));
    }
    for (int i = -127; i < 0; i = i + 12) {
      for (int j = i; j < i + 12; j++) {
        b[j - i] = (byte) j;
      }
      s = new String(b);
      Util.prt("i=" + i + " " + Util.toAllPrintable(s));
    }

    Util.loadProperties("anss.prop");
    lpt("This is a test message for printing!\nIt is two lines of text\n");
    lpt("This is a third line");
    lptSpool();

  }

  /**
   * The returns the "Node" property if defined, and the physical server name if the "Node" is not
   * defined.
   *
   * @return a tag with the edge systemName number the systemName name up to the first "." like
   * "edge5"
   */
  public static String getNode() {
    init();
    if (node != null) {
      return node;
    }
    if (Util.getProperty("Node") != null) {
      node = Util.getProperty("Node");
      getSystemNumber();
      return node;
    }
    return "99";
  }

  /**
   *
   * @param s Given a system name, remove the igskcic stuff at the USGS so the names are shorter
   * @return the trimmed name
   */
  public static String trimSystemName(String s) {
    return s.trim().replaceAll("igsk..cgus", "").replaceAll("igsk..cgvm", "").
            replaceAll("igsk..cglt", "").replaceAll("igsk..cgws", "").
            replaceAll("igsk..c", "");
  }

  /**
   * Return the system name as a full node name like you would get from uname or the /etc/hostname
   * table However, for USGS system the name may be trimmed by trimSystemName to remove the ugly
   * igkcic....
   *
   * @return The name of this host as it thinks of itself.
   */
  public static String getSystemName() {

    if (systemName != null) {
      return systemName;
    }
    if (OS.contains("Windows")) {
      systemName = Util.getProperty("WindowsNode");
      if (systemName == null) {
        try {
          systemName = InetAddress.getLocalHost().getHostName();
          systemName = systemName.trim();
        } catch (UnknownHostException e) {
          Util.prt("Could not get IndetAddress.getLocalHost() e=" + e);
          systemName = "Windows0";
        }
        Util.prt("Use InetAddress.getLocalHost().getHostname() + " + systemName);

      }
      return systemName;
    }
    for (int i = 0; i < 10; i++) {     // sometimes the name is not know right after reboot.  Try several times!
      try {
        /*Map<String,String> env = System.getenv();
        Set<String> keys = env.keySet();
        Iterator<String> itr = keys.iterator();
        while(itr.hasNext()) {
          Sdstring key = itr.next();
          String val = env.get(key);
          Util.prt("env."+key+"="+val);
        }*/
        systemName = System.getenv("HOSTNAME");
        String s = systemName;
        Util.prt("getSystemName(): HOSTNAME=" + s);
        if (systemName == null) {
          try {
            InetAddress localhost = InetAddress.getLocalHost();
            Util.prt("getSystemName(): localhost=" + localhost);
          } catch (RuntimeException expected) {
          }
          Subprocess sp = new Subprocess("uname -n");
          sp.waitFor();
          s = sp.getOutput();      // remember this might have stuff after the end
        }
        //Util.prt("getNode uname -n returns ="+s);
        s = s.trim();
        //if(dbg) Util.prta("getNode() uname -n returned="+s+" len="+s.length());
        int dot = s.indexOf(".");             // see if this is like edge3.cr.usgs.gov
        if (dot > 0) {
          s = s.substring(0, dot);    // Trim off to the first dot if any
        }
        s = s.replaceAll("\n", "");
        //Util.prta("getSystemName() uname -n="+s+" Network: localhost="+localhost.getHostName()+" canonical="+localhost.getCanonicalHostName());
        systemName = trimSystemName(s.trim());
        Util.prta("getSystemName: trimSystemName=" + systemName);
        return systemName;

      } catch (IOException e) {
        systemName = System.getenv("HOSTNAME");
        Util.prt("Cannot run uname -n type=" + OS + " type2=" + System.getProperty("os.name") + " env.HOSTNAME=" + systemName + " e=" + e);
        try {
          systemName = InetAddress.getLocalHost().getHostName();
          systemName = trimSystemName(systemName.trim());
          Util.prt("Use InetAddress.getLocalHost().getHostname() + " + systemName);
          return systemName;
        } catch (UnknownHostException e2) {
          Util.prt("Could not get IndetAddress.getLocalHost() e=" + e);
        }
        e.printStackTrace();
        sleep(10000);
        if (i == 9) {
          Util.prt("Util.getSystemName() did not return one!  How can this happen! Abort!");
          Util.exit(0);
        }
      } catch (InterruptedException e) {
        Util.prt("Util.getNode():uname -n interrupted!");
        e.printStackTrace();
        Util.exit(0);
      }
    }
    return "---";
  }

  /**
   * Get the system number from the "Node" property or failing that from the end of the
   * SystemName(). If the systemName() does not end in digits, then the system number would be zero
   * which is only good on a one server Edge/CWB setup. Hence an warning message is printed here. If
   * # appears in the Node, then the system number is before the #.
   *
   * @return The system number or zero if the system does not end with an up to two digit number
   */
  public static int getSystemNumber() {
    if (systemNumber >= 0) {
      return systemNumber;
    }
    int sysNum = 0;
    if (getNode() != null) {    // The Node property normally gives the system number
      try {
        String nodeProperty = getNode();
        if (nodeProperty.contains("#")) {
          nodeProperty = systemName.substring(0, nodeProperty.indexOf("#"));
        }
        for (int i = nodeProperty.length() - 2; i < nodeProperty.length(); i++) {
          if (Character.isDigit(nodeProperty.charAt(i))) {
            sysNum = 10 * sysNum + (nodeProperty.charAt(i) - '0');
          }
        }
      } catch (RuntimeException e) {
        Util.prt("Util.getSystemNumber is null and there is no proper Node property=" + getNode());
      }
    }
    // If the "Node" property does not set a system number, then see if the system name gives one.
    if (sysNum == 0) {
      Util.getSystemName();   // This sets the systemName attribute
      for (int i = systemName.length() - 2; i < systemName.length(); i++) {
        if (Character.isDigit(systemName.charAt(i))) {
          sysNum = 10 * sysNum + (systemName.charAt(i) - '0');
        }
      }
    }
    systemNumber = sysNum;
    return systemNumber;
  }

  /**
   * Clean up an IP address to a common form, This removes leading zeros from any octets so
   * 140.090.000.015 becomes 140.90.0.15
   *
   * @param ip The unclean IP address
   * @return A cleaned up IP address
   */
  public static String cleanIP(String ip) {
    for (int i = 0; i < 2; i++) {
      if (ip.substring(0, 1).equals("0")) {
        ip = ip.substring(1);
      }
      ip = ip.replaceAll("\\.0", ".");
    }
    ip = ip.replaceAll("\\.\\.", ".0.");
    return ip;
  }
  /*public static void main2(String[] args) {
    int i = 0x12345678;
    prt(i+" is hex "+toHex(i));
    i = 0xFFFFFFFF;
    prt(i+" is hex "+toHex(i));
    i = 0;
    prt(i+" is hex "+toHex(i));
    i = 4096;
    prt(i+" is hex "+toHex(i));
     long l = 0xabcdef0123456789l;
    prt(l+" is hex "+toHex(l));
    System.exit(0);
 }*/
  private static byte[] readbuf;

  /**
   * read an entire file into a StringBuiler assuming each byte is an ASCII char
   *
   * @param filename The filename to read
   * @param rfsb The String builer to return the answer in
   * @return The users rfsb or it was null a new StringBuilder.
   * @throws java.io.IOException If one occurs
   */
  public static synchronized StringBuilder readFileToSB(String filename, StringBuilder rfsb) throws IOException {
    //StringBuilder statussb = new StringBuilder(100);
    StringBuilder sb = rfsb;
    int length;
    try (RandomAccessFile filein = new RandomAccessFile(filename, "r")) {
      if (sb == null) {
        sb = new StringBuilder((int) filein.length());
      }
      length = (int) filein.length();
      if (readbuf == null) {
        readbuf = new byte[length * 2];
      }
      if (readbuf.length < length) {
        readbuf = new byte[length * 2];
      }
      filein.readFully(readbuf, 0, length);
    }
    Util.clear(sb);
    for (int i = 0; i < length; i++) {
      sb.append((char) readbuf[i]);
    }
    return sb;
  }

  /**
   * Write the contents of a CharSequence (String, StringBuilder, etc) into a file. It writes to a
   * temporary file first, then moves the temp file to the eventual filename to insure the file is
   * created as an atomic operation.
   *
   * @param filename Filename of the file
   * @param wfsb CharSequence with contents.
   * @throws java.io.IOException If file cannot be written
   */
  public static synchronized void writeFileFromSB(String filename, CharSequence wfsb) throws IOException {
    File outfile = new File(filename);
    File temp = new File(filename + ".edgecwbtmp");
    RandomAccessFile fileout;
    fileout = new RandomAccessFile(temp, "rw");
    if (readbuf == null) {
      readbuf = new byte[wfsb.length() * 2];
    }
    if (readbuf.length < wfsb.length()) {
      readbuf = new byte[wfsb.length() * 2];
    }
    Util.stringBuilderToBuf(wfsb, readbuf);
    fileout.write(readbuf, 0, wfsb.length());
    fileout.setLength((long) wfsb.length());
    fileout.close();
    //Util.prta("writeFileFromSB : len="+wfsb.length()+" "+temp+" to "+outfile);
    temp.renameTo(outfile);
  }

  /**
   * read fully the number of bytes, or throw exception
   *
   * @param in The InputStream to read from
   * @param buf The byte buffer to receive the data
   * @param off The offset into the buffer to start the read
   * @param len Then desired # of bytes
   * @return The length of the read in bytes, zero if EOF is reached
   * @throws IOException if one is thrown by the InputStream
   */
  public static int readFully(InputStream in, byte[] buf, int off, int len) throws IOException {
    int nchar;
    int l = off;
    while (len > 0) {            //
      //while(in.available() <= 0) try{Thread.sleep(10);} catch(InterruptedException e) {}
      nchar = in.read(buf, l, len);// get nchar
      if (nchar <= 0) {
        //Util.prta(len+" RF read nchar="+nchar+" len="+len+" in.avail="+in.available());
        return 0;
      }     // EOF - close up
      l += nchar;               // update the offset
      len -= nchar;             // reduce the number left to read
    }
    return l;
  }

  /**
   * read fully the number of bytes, or throw exception. Suitable for sockets since the read method
   * uses a lot of CPU if you just call it. This checks to make sure there is data before attemping
   * the read.
   *
   * @param in The InputStream to read from
   * @param buf The byte buffer to receive the data
   * @param off The offset into the buffer to start the read
   * @param len Then desired # of bytes
   * @return The length of the read in bytes, zero if EOF is reached, - bytes read if EOF came
   * before all bytes were read
   * @throws IOException if one is thrown by the InputStream
   */
  static long countErr = 0;

  public static int socketReadFully(InputStream in, byte[] buf, int off, int len) throws IOException {
    int nchar;
    int l = off;
    while (len > 0) {            //
      nchar = in.read(buf, l, len);// get nchar
      if (nchar <= 0) {
        if (in.available() > 0) {
          Util.prta(len + " SRF read nchar=" + nchar + " len=" + len + " off=" + off + " in.avail=" + in.available());
        }
        if (countErr++ % 1000 == 999) {
          new RuntimeException("SRF EOF called 1000 times " + countErr).printStackTrace();
        }
        return (l == off ? 0 : off - l);  // negative bytes read
      }     // EOF - close up
      l += nchar;               // update the offset
      len -= nchar;             // reduce the number left to read
    }
    return l - off;
  }

  /**
   * read up to the number of bytes, or throw exception. Suitable for sockets since the read method
   * uses a lot of CPU if you just call it. This checks to make sure there is data before attemping
   * the read.
   *
   * @param in The InputStream to read from
   * @param buf The byte buffer to receive the data
   * @param off The offset into the buffer to start the read
   * @param len Then desired # of bytes
   * @return The length of the read in bytes, zero if EOF is reached
   * @throws IOException if one is thrown by the InputStream
   */
  public static int socketRead(InputStream in, byte[] buf, int off, int len) throws IOException {
    int nchar;
    nchar = in.read(buf, off, len);// get nchar
    if (nchar <= 0) {
      //Util.prta(len+" SR read nchar="+nchar+" len="+len+" in.avail="+in.available());
      return 0;
    }
    return nchar;
  }

  /**
   * read fully a single byte, or throw exception. Suitable for sockets since the read method uses a
   * lot of CPU if you just call it. This checks to make sure there is data before attemping the
   * read, and sleeps if no data is available
   *
   * @param in The InputStream to read from
   * @return the single byte read as a int
   * @throws IOException if one is found
   */
  public static int socketRead(InputStream in) throws IOException {
    return in.read();
  }

  public static StringBuilder stringBuilderRightPad(StringBuilder sb, int len) {
    if (sb.length() < len) {
      for (int i = sb.length(); i < len; i++) {
        sb.append(" ");
      }
    }
    return sb;
  }

  public static StringBuilder trim(StringBuilder sb) {
    int j = 0;
    for (int i = 0; i < sb.length(); i++) {
      if (sb.charAt(0) == ' ') {
        sb.delete(0, 1);
      } else {
        break;
      }
    }
    for (int i = sb.length() - 1; i >= 0; i--) {
      if (sb.charAt(i) == ' ') {
        sb.deleteCharAt(i);
      } else {
        break;
      }
    }
    return sb;
  }

  /**
   * Often we have a stringBuilder that we need to write out as raw ASCII bytes. This routine will
   * convert all of the chars in the StringBuilder into an simple array of bytes provided by the use
   *
   * @param sb The string builder to convert.
   * @param buf The user supplied buffer of bytes
   * @throws IndexOutOfBoundsException If user supplied buffer is smaller than the StringBuffer
   */
  public static void stringBuilderToBuf(CharSequence sb, byte[] buf) throws IndexOutOfBoundsException {
    if (sb.length() > buf.length) {
      throw new IndexOutOfBoundsException("stringBUilden len=" + sb.length() + " your buffer=" + buf.length);
    }
    for (int i = 0; i < sb.length(); i++) {
      buf[i] = (byte) sb.charAt(i);
    }
  }

  public static int sbToInt(StringBuilder sb) {
    int in = 0;
    for (int i = 0; i < sb.length(); i++) {
      if (Character.isDigit(sb.charAt(i))) {
        in = in * 10 + (sb.charAt(i) - '0');
      } else {
        break;
      }
    }
    return in;
  }

  public static long sbToLong(StringBuilder sb) {
    long in = 0;
    for (int i = 0; i < sb.length(); i++) {
      if (Character.isDigit(sb.charAt(i))) {
        in = in * 10 + (sb.charAt(i) - '0');
      } else {
        break;
      }
    }
    return in;
  }
  static final long powers = 31L * 31L * 31 * 31 * 31 * 31 * 31 * 31 * 31 * 31 * 31;    // 31^11 power perfect for 12 characters strings

  /**
   * Generate a java like hash code from a StringBuilder without making a string. This does create
   * the same hash code if it is converted to int, but I have left it as long for our purposes.
   * Normally this is used in IndexBlock to create the upper 40 bits of the hash code of seedname
   * and julian date (seed IndexBlock.getHash(seedname,julian) for the only known usage of this
   * routine. This will NOT generate the same hash code as String if the string length is more than
   * 12 characters
   *
   * @param sb The seedname (normally) to hash
   * @return a hash code s[0]*31^(n-1) + s[1]*31^(n-2) + ... + s[n-1] per String.hashCode()
   */
  public static long getHashSB(StringBuilder sb) {
    long hash = 0;
    long pow = powers;
    if (sb.length() < 12) {
      for (int i = 0; i < 12 - sb.length(); i++) {
        pow = pow / 31;    // fix power to n-1
      }
    }
    for (int i = 0; i < sb.length(); i++) {
      hash += ((int) sb.charAt(i)) * pow;
      pow = pow / 31;
      if (pow == 0) {
        pow = powers;
      }
    }
    return hash;
  }

  /**
   * Often we have a stringBuilder that we need to write out as raw ASCII bytes. This routine will
   * convert all of the chars in the StringBuilder into an simple array of bytes provided by the use
   *
   * @param sb The string buffer to convert.
   * @param buf The user supplied buffer of bytes
   * @return the byte array the user provided - useful for inline code
   * @throws IndexOutOfBoundsException If user supplied buffer is smaller than the StringBuffer
   */
  public static byte[] stringBufferToBuf(StringBuffer sb, byte[] buf) throws IndexOutOfBoundsException {
    if (sb.length() > buf.length) {
      throw new IndexOutOfBoundsException("stringBuffer len=" + sb.length() + " your buffer=" + buf.length);
    }
    for (int i = 0; i < sb.length(); i++) {
      buf[i] = (byte) sb.charAt(i);
    }
    return buf;
  }

  /**
   *
   * @param num The value to be rounded
   * @param sigfigs The number of sig figs
   * @return The double rounded to the right number of sig figs.
   */
  public static double roundToSig(double num, int sigfigs) {
    return new BigDecimal(num).round(new MathContext(sigfigs, RoundingMode.HALF_EVEN)).doubleValue();
  }

  /**
   * convert a StringBUilder to all lower case.
   *
   * @param sb The StringBuilder to convert
   * @param tmp A user StringBuilder for scratch work. If null, a Util.padsb is used.
   * @return the same stringBuilder after conversion.
   */
  public static StringBuilder sbToLowerCase(StringBuilder sb, StringBuilder tmp) {
    if (tmp == null) {
      tmp = nextPadsb();
    }
    Util.clear(tmp).append(sb);
    Util.clear(sb);
    for (int i = 0; i < tmp.length(); i++) {
      sb.append(Character.toLowerCase(tmp.charAt(i)));
    }
    return sb;
  }

  /**
   * convert a StringBUilder to all lower case.
   *
   * @param sb The StringBuilder to convert
   * @param tmp Optional user scratch buffer to use on conversion, null means use a Padsb()
   * @return the same stringBuilder after conversion.
   */
  public static StringBuilder sbToUpperCase(StringBuilder sb, StringBuilder tmp) {
    if (tmp == null) {
      tmp = nextPadsb();
    }
    Util.clear(tmp).append(sb);
    Util.clear(sb);
    for (int i = 0; i < tmp.length(); i++) {
      sb.append(Character.toUpperCase(tmp.charAt(i)));
    }
    return sb;
  }

  /**
   * Compare the contents of two stringBuilders
   *
   * @param sb First string builder
   * @param sb2 Second string builder
   * @return 0 if equal in length and content, -1 if sb less than sb2, 1 if sb greater than
   */
  public static int sbCompareTo(CharSequence sb, CharSequence sb2) {
    if (sb == null && sb2 == null) {
      return 0;
    }
    if (sb == null && sb2 != null) {
      return 1;
    }
    if (sb != null && sb2 == null) {
      return -1;
    }
    if (sb == null || sb2 == null) {
      return -2;  // this does nothing but suppress warning about nulls in next statement.
    }
    for (int i = 0; i < Math.min(sb.length(), sb2.length()); i++) {
      if (sb.charAt(i) < sb2.charAt(i)) {
        return -1;
      }
      if (sb.charAt(i) > sb2.charAt(i)) {
        return 1;
      }
    }
    if (sb.length() > sb2.length()) {
      return 1;
    }
    if (sb.length() < sb2.length()) {
      return -1;
    }
    return 0;
  }

  /**
   * Compare the contents of two stringBuilders
   *
   * @param sb First string builder
   * @param sb2 Second string builder
   * @param maxlen Maximum length to compare
   * @return 0 if equal in length and content, -1 if sb less than sb2, 1 if sb greater than
   */
  public static int sbCompareTo(CharSequence sb, CharSequence sb2, int maxlen) {
    if (sb == null && sb2 == null) {
      return 0;
    }
    if (sb == null && sb2 != null) {
      return 1;
    }
    if (sb != null && sb2 == null) {
      return -1;
    }
    if (sb == null || sb2 == null) {
      return -2;  // this does nothing but suppress warning about nulls in next statement.
    }
    for (int i = 0; i < Math.min(Math.min(sb.length(), sb2.length()), maxlen); i++) {
      if (sb.charAt(i) < sb2.charAt(i)) {
        return -1;
      }
      if (sb.charAt(i) > sb2.charAt(i)) {
        return 1;
      }
    }
    if (Math.min(maxlen, sb.length()) > Math.min(maxlen, sb2.length())) {
      return 1;
    }
    if (Math.min(maxlen, sb.length()) < Math.min(maxlen, sb2.length())) {
      return -1;
    }
    return 0;
  }

  /**
   * Do a readline from a BufferedReader but output to a StringBuilder provided by the user
   * <PRE>
   * Sample usage :
   *   StringBuilder line = new StringBuilder(100);
   *   BufferedReader in = new BufferedReader(new FileReader(filename));
   *   for(;;) {
   *     int len = Util.stringBuilderReadline(in, line);
   *     if(len < 0) break;
   *     // Do something with the line
   *   }
   *   in.close();
   *
   * </PRE>
   *
   * @param br A bufferred reader of characters
   * @param out The users supplied StringBuilder
   * @return -1 if EOF found, else the length of the line in bytes
   * @throws IOException
   */
  public static int stringBuilderReadline(BufferedReader br, StringBuilder out) throws IOException {
    Util.clear(out);
    for (;;) {
      int i = br.read();
      if (i < 0) {
        return i;            // EOF found
      }
      char c = (char) i;
      if (c == '\r') {
        continue;      //ignore all returns
      }
      if (c == '\n') {
        break;
      }
      out.append(c);
    }
    return out.length();
  }

  /**
   * Are two StringBuilders equal
   *
   * @param sb1 First StringBuilder
   * @param sb2 2nd
   * @return Ture if they are both not null, their lengths are the same, and the contents are the
   * same with char compares.
   *
   */
  public static boolean stringBuilderEqual(CharSequence sb1, CharSequence sb2) {
    return (sbCompareTo(sb1, sb2) == 0);

  }

  /**
   *
   * @param sb1 String to examine for the given substring
   * @param sb2 Substring to find in the main string
   * @return position where substring is found, else -1
   */
  public static int charSequenceIndexOf(CharSequence sb1, CharSequence sb2) {
    for (int i = 0; i < sb1.length() - sb2.length() + 1; i++) {
      boolean found = true;
      for (int j = 0; j < sb2.length(); j++) {
        if (sb1.charAt(i + j) != sb2.charAt(j)) {
          found = false;
          break;
        }
      }
      if (found) {
        return i;
      }
    }
    return -1;
  }

  /**
   * Are two StringBuilders equal
   *
   * @param sb1 First StringBuilder
   * @param sb2 2nd
   * @return Ture if they are both not null, their lengths are the same, and the contents are the
   * same with char compares.
   *
   */
  public static boolean stringBuilderEqualIgnoreCase(CharSequence sb1, CharSequence sb2) {
    if (sb1 == null && sb2 == null) {
      return true;
    }
    if (sb1 == null) {
      return false;
    }
    if (sb2 == null) {
      return false;
    }
    if (sb1.length() != sb2.length()) {
      return false;
    }
    for (int i = 0; i < Math.min(sb1.length(), sb2.length()); i++) {
      if (Character.toLowerCase(sb2.charAt(i)) != Character.toLowerCase(sb1.charAt(i))) {
        return false;
      }
    }
    return true;
  }

  /**
   * Are two StringBuilders equal
   *
   * @param sb1 First StringBuilder
   * @param sb2 2nd
   * @return Ture if they are both not null, their lengths are the same, and the contents are the
   * same with char compares.
   *
   */
  /*public static boolean stringBuilderEqual(StringBuilder sb1, String sb2) {
    if(sb1 == null && sb2 == null) return true;
    if(sb1 == null) return false;
    if(sb2 == null) return false;
    if(sb1.length() != sb2.length()) return false;
    for(int i=0; i<sb1.length();i++) if(sb2.charAt(i) != sb1.charAt(i)) return false;
    return true;
  }*/
  /**
   * Are two StringBuilders equal
   *
   * @param sb1 First StringBuilder
   * @param sb2 2nd
   * @return Ture if they are both not null, their lengths are the same, and the contents are the
   * same with char compares.
   *
   */
  /*public static boolean stringBuilderEqualIgnoreCase(StringBuilder sb1, String sb2) {
    if(sb1 == null && sb2 == null) return true;
    if(sb1 == null) return false;
    if(sb2 == null) return false;
    if(sb1.length() != sb2.length()) return false;
    for(int i=0; i<sb1.length();i++) 
      if(Character.toLowerCase(sb2.charAt(i)) != Character.toLowerCase(sb1.charAt(i))) return false;
    return true;
  }*/
  /**
   * String Builders do not have a "replaceAll()" so we fake one up
   *
   * @param sb The stringBuilder to do the replaceAll on
   * @param match The string to match
   * @param replace The string to replace it with
   * @return The number of substitutions done.
   */
  public static StringBuilder stringBuilderReplaceAll(StringBuilder sb, String match, String replace) {
    boolean done = false;
    int index = 0;
    int len = match.length();
    int matches = 0;
    while (!done) {
      index = sb.indexOf(match, index);
      if (index >= 0) {
        sb.replace(index, index + len, replace);
        matches++;
        if (matches > 1000) {// I think this test is no longer needed
          Util.prt("stringBuilderReplaceAll **** in infinite loop.  Probably a replacement with similar " + match + " replace=" + replace);
          done = true;
        }
        index++;
      } else {
        done = true;
      }
    }
    return sb;
  }

  /**
   * String Builders do not have a "replaceAll()" so we fake one up
   *
   * @param sb The stringBuilder to do the replaceAll on
   * @param match The string to match
   * @param replace The string to replace it with
   * @return The number of substitutions done.
   */
  public static int stringBuilderReplaceAll2(StringBuilder sb, String match, String replace) {
    boolean done = false;
    int index = 0;
    int len = match.length();
    int matches = 0;
    while (!done) {
      index = sb.indexOf(match, index);
      if (index >= 0) {
        sb.replace(index, index + len, replace);
        matches++;
        if (matches > 1000) {  // It think this test is no longer needed
          Util.prt("stringBuilderReplaceAll2 **** in infinite loop.  Probably a replacement with similar " + match + " replace=" + replace);
          done = true;
        }
        index++;
      } else {
        done = true;
      }
    }
    return matches;
  }

  /**
   * String Builders do not have a "replaceAll()" so we fake one up
   *
   * @param sb The stringBuilder to do the replaceAll on
   * @param match The string to match
   * @param replace The string to replace it with
   * @return The number of substitutions done.
   */
  public static StringBuilder stringBuilderReplaceAll(StringBuilder sb, char match, char replace) {
    for (int i = 0; i < sb.length(); i++) {
      if (sb.charAt(i) == match) {
        sb.delete(i, i + 1);
        sb.insert(i, replace);
      }
    }
    return sb;
  }

  public static StringBuffer clear(StringBuffer sb) {
    synchronized (sb) {
      if (sb.length() > 0) {
        sb.delete(0, sb.length());
      }
    }
    return sb;
  }

  public static StringBuilder clear(StringBuilder sb) {
    synchronized (sb) {
      if (sb.length() > 0) {
        sb.delete(0, sb.length());
      }
    }
    return sb;
  }

  /**
   * StringBuilder does not have a comparison operator, create one here
   *
   * @param sb1 String builder 1
   * @param sb2
   * @return -1 if sb1 < sb2, 1 if sb1 > sb2, if they are not of equal length, if the minimum length
   * matches, then the longer string is bigger
   */
  public static int compareTo(StringBuilder sb1, StringBuilder sb2) {
    return sbCompareTo(sb1, sb2);
  }

  /**
   *
   * @param i Append an int
   * @param len to width of field
   * @param fill Fill to the left with this character
   * @param sb The Stringbuilder to append it to
   * @return
   */
  public static StringBuilder append(long i, int len, char fill, StringBuilder sb) {
    try {
      synchronized (sb) {
        boolean lead = true;
        try {
          for (int j = 0; j < len; j++) {
            char digit = (char) (((i / ((int) (Math.pow(10., len - j - 1) + 0.0001))) % 10) + '0');
            if (digit == '0' && lead) {
              sb.append(fill);
            } else {
              sb.append(digit);
            }
            if (digit != '0') {
              lead = false;
            }
          }
        } catch (RuntimeException e) {
          e.printStackTrace();
        }
      }
    } catch (RuntimeException e) {
      e.printStackTrace();
    }
    return sb;
  }

  /**
   *
   * @param i Append an int
   * @param len to width of field
   * @param fill Fill to the left with this character
   * @param sb The Stringbuilder to append it to
   * @return
   */
  public static StringBuffer append(long i, int len, char fill, StringBuffer sb) {
    boolean lead = true;
    try {
      for (int j = 0; j < len; j++) {
        char digit = (char) (((i / ((int) (Math.pow(10., len - j - 1) + 0.0001))) % 10) + '0');
        if (digit == '0' && lead) {
          sb.append(fill);
        } else {
          sb.append(digit);
        }
        if (digit != '0') {
          lead = false;
        }
      }
    } catch (RuntimeException e) {
      e.printStackTrace();
    }
    return sb;
  }
  private static final int[] seedswap = {2, 3, 4, 5, 6, 10, 11, 7, 8, 9, 0, 1};  // This is the order to make miniseed header to NNSSSSSCCCLL
  private static final int[] swapseed = {1, 0, 11, 10, 9, 8, 7, 4, 3, 2, 6, 5}; // When it comes off in reverse order, positions to load into the string

  public static long getHashFromSeedname(CharSequence s) {
    int j;
    char c;
    int len = s.length();
    long val = 0;
    long mult = 1;
    for (int i = 0; i < 12; i++) {
      if (seedswap[i] < len) {
        c = s.charAt(seedswap[i]);
      } else {
        c = ' ';
      }
      if (c == ' ' || c == '-' || c == 0) {
        j = 36;
      } else if (c >= 'A') {
        j = c - 'A' + 10;
      } else {
        j = c - '0';
      }
      if (i != 7) {
        val = val * 37 + j;
      } else {
        if (c >= 'a') {
          mult = -1;
          j = c - 'a';    // convert to uppercase
        }
        val = val * 37 + j;
      }   // correct  for lower case in position 10
    }
    return mult * val;
  }

  /**
   * This converts a SEEDname in the order of a MiniSeed packet to its long representation
   *
   * @param s An array with the miniseed raw bytes
   * @param off The offset into the array to start (normally 8), if off <0, 8 is
   * @r
   * eturn thhe resulting long.
   */
  public static long getHashFromBuf(byte[] s, int off) {
    int j;
    int c;
    long val = 0;
    long mult = 1;
    if (off < 0) {
      off = 8;
    }
    for (int i = 0; i < 12; i++) {
      c = s[off + i];
      if (c == 0) {
        c = ' ';
      }
      if (c == ' ') {
        j = 36;
      } else if (c >= 'A') {
        j = c - 'A' + 10;
      } else {
        j = c - '0';
      }
      if (i != 7) {
        val = val * 37 + j;
      } else {
        if (c >= 'a') {
          mult = -1;
          j = c - 'a';    // convert to uppercase
        }
        val = val * 37 + j;
      }   // correct for lower case in position 10
    }
    return mult * val;
  }
  /* public static long getHash(StringBuilder s) {
    int j;
    char c;
    int len = s.length();
    long val = 0;
    long mult = 1;
    for (int i = 0; i < 12; i++) {
      if (seedswap[i] < len) {
        c = s.charAt(seedswap[i]);
      } else {
        c = ' ';
      }
      if (c == ' ' || c == '-' || c == 0) {
        j = 36;
      } else if (c >= 'A') {
        j = c - 'A' + 10;
      } else {
        j = c - '0';
      }
      if (i != 7) {
        val = val * 37 + j;
      } else {
        if (c >= 'a') {
          mult = -1;
          j = c - 'a';    // convert to uppercase
        }
        val = val * 37 + j;
      }   // correct  for lower case in position 10
    }
    return mult * val;
  }*/
  public static char[] sfl = new char[12];

  public static void ungetHash(long val, StringBuilder s) {
    long j;
    char c;
    int mult = 1;
    if (val < 0) {
      mult = -1;
      val = -val;
    }
    if (s.length() > 0) {
      s.delete(0, s.length());
    }
    for (int i = 11; i >= 0; i--) {
      j = val % 37;
      val = val / 37;
      if (i == 7) {
        if (mult < 0) {
          j = j + 37;
        }
      }
      if (j == 36) {
        c = ' ';
      } else if (j < 10) {
        c = (char) (j + '0');
      } else if (j < 36) {
        c = (char) (j - 10 + 'A');
      } else {
        c = (char) (j - 37 + 'a');
      }
      sfl[11 - i] = c;
    }
    for (int i = 0; i < 12; i++) {
      s.append(sfl[swapseed[i]]);
    }
  }

  public static void putString(String s, ByteBuffer bb) {
    for (int i = 0; i < s.length(); i++) {
      bb.put((byte) s.charAt(i));
    }
  }

  public static void putString(StringBuilder s, ByteBuffer bb) {
    for (int i = 0; i < s.length(); i++) {
      bb.put((byte) s.charAt(i));
    }
  }

  /**
   * CHeck a file (normally a configuration file) to see if its modify data is now newer than one
   * the caller is tracking.
   * <PRE>
   * long lastModified =0;    // class variable to track mod date of configuration file
   * .
   * .
   * Some loop which checks the modify date
   * long lastMod = Util.isModifyDateNewer(configFile, lastModified);
   * if(lastMod == 0)  {
   *   prta("config file modify date is the same!"+Util.ascdatetime2(lastMod));
   *   return;
   * }
   * else if(lastMod > 0) {
   *   lastModified = lastMod;
   * }
   * else {prta(" *** config file does not exist! "+configFile); return;}
   * </PRE>
   *
   * @param configFile The the modify date of the file against a last modified date
   * @param lastModified The last time the caller thinks the file was modified
   * @return The last modified date if the file has a modify data greater than lastModified, user
   * should store this for the next call, else zero
   */
  public static long isModifyDateNewer(String configFile, long lastModified) {
    File f = new File(configFile);
    if (!f.exists()) {
      return -1;          // File does not exist
    }
    long lastMod = f.lastModified();    //get modify time
    if (lastMod <= lastModified + 1000) {  // is it a least one second older than our old one
      return 0;                         // Its older
    }
    return lastMod;                     // its newer, return the new time
  }

  /**
   * Issue the given curl command and put the results in the file. If the file is below minlen, try
   * again.
   *
   * @param url The curl command
   * @param file The file to get the results
   * @param minlen The minimum length of a successful curl, if it is not this length it is reissued.
   * @param par A logging printStream
   * @return the length of the result
   * @throws IOException If one is thrown by the subprocess.
   */
  public static long curlit(String url, String file, int minlen, PrintStream par) throws IOException {
    long len = Integer.MIN_VALUE;
    if (par == null) {
      par = getOutput();
    }
    while (len < minlen) {
      //par.println("Curl line="+line);
      String line = "curl -# -sS -o " + file + " " + url;
      Subprocess curl = new Subprocess(line);
      int loop = 0;
      while (curl.exitValue() == -99) {
        Util.sleep(100);
        if (loop++ % 200 == 0) {
          if (curl.getOutput().length() > 1) {
            par.println(curl.getOutput());
          }
          if (curl.getErrorOutput().length() > 1) {
            par.println(curl.getErrorOutput());
          }
        }
      }
      if (curl.getErrorOutput().length() > 1) {
        par.println("curl line=" + line + "\ncurlit: err=" + curl.getErrorOutput());
      }
      if (curl.getOutput().length() > 1) {
        par.println("curl line=" + line + "\ncurlit: out=" + curl.getOutput());
      }
      if (curl.exitValue() != 0) {
        par.println("curlit: ***** exit=" + curl.exitValue());
        Util.sleep(30000);
        continue;
      }
      File f = new File(file);

      if (f.exists()) {    // Is the expected file there?
        len = f.length();
        if (len < minlen) { // Is it long enough
          par.println("BWFDIR: File is to short len=" + len + " try again");
          f.delete();
        }
      }
    }
    return len;
  }

  /**
   * Finds the filename and line number for an object in a stack trace.
   *
   *
   * @return the caller of this method
   */
  public static String getCallerLine() {
    StackTraceElement[] stack = new RuntimeException("getCallerline").getStackTrace();
    return stack[2].toString();
  }

  /**
   * given an Exception and local message string, dump the exception and system state at the time of
   * the exception. This routine should be called by all "catch(Exception) clauses to implement
   * standard reporting.
   *
   * @param E The Exception
   * @param msg The user supplied text to add
   */
  public static void errorPrint(Exception E, String msg) {
    System.err.println(asctime2() + " " + msg + " e=" + E);
    System.err.println("IOException : " + E.getMessage());
    E.printStackTrace();
  }

}
