/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.alarm;

import gov.usgs.anss.edgethread.EdgeThread;
import gov.usgs.anss.db.DBConnectionThread;
import gov.usgs.anss.util.AnssPorts;
import gov.usgs.anss.util.Subprocess;
import gov.usgs.anss.util.User;
import gov.usgs.anss.util.Util;
import java.io.IOException;
import java.io.FileNotFoundException;
import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;

import java.io.FileWriter;
import java.io.InputStream;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.GregorianCalendar;

/**
 * This server accepts connections and commands "Pings", "QDPings", "EdgeMom
 * Status" and "Execute" and more returns the output from their respective
 * clients. This supports the user GUI for Maintenance where user based commands
 * etc, can be implemented on remote nodes without the user having to have
 * authorized, but limited to the commands available for that node.
 *
 * @author davidketchum
 */
public final class CommandStatusServer extends EdgeThread {

  private int port;
  private ServerSocket d;
  private int totmsgs;
  private PrintStream logger;
  ArrayList<TextStatusHandler> thrs = new ArrayList<>(100);

  @Override
  public void terminate() {
    terminate = true;
  }

  @Override
  public StringBuilder getMonitorString() {
    Util.clear(monitorsb);
    return monitorsb;
  }

  @Override
  public StringBuilder getStatusString() {
    Util.clear(statussb);
    return statussb;
  }

  @Override
  public StringBuilder getConsoleOutput() {
    return consolesb;
  } //  we use prt directly

  public int getNumberOfMessages() {
    return totmsgs;
  }

  /**
   * Creates a new instance of TextStatusServers
   *
   * @param argline the command line arguments if any
   * @param tag The tag to use
   */
  public CommandStatusServer(String argline, String tag) {
    super(argline, tag);
    port = 7984;
    terminate = false;
    String[] args = argline.split("\\s");
    for (int i = 0; i < args.length; i++) {
      if (args[i].equals("-p")) {
        port = Integer.parseInt(args[i + 1]);
      }
    }
    try {
      logger = new PrintStream(
              new FileOutputStream("LOG" + Util.fs + "cmdstatus.log", true));   // Register our shutdown thread with the Runtime system.
    } catch (IOException e) {
      prta("IOException opening cmdstatus.log e=" + e);
      e.printStackTrace(getPrintStream());
    }
    Runtime.getRuntime().addShutdownHook(new ShutdownTextStatus());
    setDaemon(true);
    start();
  }

  @Override
  public void run() {
    boolean dbg = false;
    GregorianCalendar now;
    StringBuilder sb = new StringBuilder(10000);

    // OPen up a port to listen for new connections.
    prta(Util.asctime() + " TSS: Open Port=" + port);
    int loop = 0;
    while (true) {
      try {
        //server = s;
        if (terminate) {
          break;
        }
        d = new ServerSocket(port);
        break;
      } catch (SocketException e) {
        if (e.getMessage().equals("TSS:Address already in use")) {
          try {
            loop++;
            if (loop % 120 == 0) {
              prta("TSS: Address in use - try again. loop=" + loop + " " + EdgeThread.getInstance());
            }
            Thread.sleep(60000);
          } catch (InterruptedException Expected) {

          }
        } else {
          prta("TSS:Error opening TCP listen port =" + port + " " + EdgeThread.getInstance() + "-" + e.getMessage());
          try {
            Thread.sleep(2000);
          } catch (InterruptedException Expected) {

          }
        }
      } catch (IOException e) {
        prta("TSS:ERror opening socket server=" + e.getMessage());
        try {
          Thread.sleep(2000);
        } catch (InterruptedException Expected) {

        }
      }
    }

    while (true) {
      if (terminate) {
        break;
      }
      try {
        Socket s = d.accept();
        prta("TSS: from " + s);
        thrs.add(new TextStatusHandler(s));
        long l = System.currentTimeMillis();
        for (int i = thrs.size() - 1; i >= 0; i--) {
          if (thrs.get(i) != null) {
            prta(i + " " + thrs.get(i).toString());
            if (!thrs.get(i).isAlive()) {
              thrs.remove(i);
            } else if (l - thrs.get(i).getLastUpdate() > 7200000) {
              thrs.get(i).terminate();
              prta("**** Stop thread ****");
            }
          }
        }
      } catch (IOException e) {
        prta("TSS:receive through IO exception");
      }
    }       // end of infinite loop (while(true))
    //prta("Exiting TextStatusServers run()!! should never happen!****\n");
    prta("TSS:read loop terminated");
  }

  private final class ShutdownTextStatus extends Thread {

    public ShutdownTextStatus() {

    }

    /**
     * this is called by the Runtime.shutdown during the shutdown sequence cause
     * all cleanup actions to occur
     */
    @Override
    public void run() {
      terminate = true;
      prta("TSS: Shutdown started");
      int nloop = 0;
      prta("TSS:Shutdown Done. CLient c");
      // The connection threads are actually in QDPing and PingListeners, but they are
      // mnay but they can be shutdown without the whole program shutthing down
      DBConnectionThread.shutdown();     // Shutdown all of the connection threads

    }
  }

  private final class TextStatusHandler extends Thread {

    Socket s;
    long lastUpdate;
    String command;
    boolean terminate;

    public long getLastUpdate() {
      return lastUpdate;
    }

    public void terminate() {
      terminate = true;
    }

    public TextStatusHandler(Socket ss) {
      s = ss;
      lastUpdate = System.currentTimeMillis();
      start();
    }

    @Override
    public String toString() {
      return this.getName() + " cmd=" + command + " lastUpdate=" + (System.currentTimeMillis() - lastUpdate) / 1000 + " alive=" + isAlive();
    }

    @Override
    public void run() {
      try {
        OutputStream out = s.getOutputStream();
        BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream()));
        String line = in.readLine();
        String user = in.readLine();
        command = line + ":" + user;
        prta(Util.ascdate() + " TSH cmd=" + line + "|" + user + "|");
        logger.println(Util.ascdatetime2() + "|" + line + "|" + user);
        if (line == null) {    // this is usually a probe or scan - ignore it
          s.close();
          return;
        }
        if (line.equals("QDPing")) {
          prta("Command QDPING status");
          out.write(QDPing.getStatusAll().getBytes());
        } else if (line.equals("Pings")) {
          prta("Doing a ping report");
          out.write(PingListener.pingReport().getBytes());
        } else if (line.equals("EdgeMom Status")) {
          try {
            byte[] buf = new byte[1000];
            prta("EdgeMom Status");
            try (Socket s2 = new Socket("localhost", AnssPorts.MONITOR_EDGEMOM_PORT); // 7800
                    ) {
              InputStream input = s2.getInputStream();
              int nchar;
              out.write((Util.getNode() + "\n").getBytes());
              while ((nchar = input.read(buf)) > 0) {
                out.write(buf, 0, nchar);
              }
              out.write((Util.getNode() + "\n").getBytes());
            }
          } catch (UnknownHostException e) {
            prta("got Unknown host setting up socket for EdgeMom Status " + e.getMessage());
          } catch (IOException e) {
            prta("Got IOException opening socket or readint it for EdgeMom Status=" + e.getMessage());
          }
        } else if (line.substring(0, 7).equals("getfile")) {
          String[] args = line.split(" ");
          try {
            FileInputStream input = new FileInputStream(args[1]);
            byte[] buf = new byte[input.available()];
            int ret = input.read(buf, 0, input.available());
            out.write(buf, 0, buf.length);
            s.close();
            return;

          } catch (FileNotFoundException e) {
            out.write(("File was not found " + args[1] + "\n").getBytes());
          }

        } else if (line.substring(0, 7).equals("putfile")) {
          String[] args = line.split(" ");
          try {
            try (FileWriter output = new FileWriter(args[1])) {
              char[] buf = new char[1000];
              int loop = 0;
              int nchar = 0;
              for (;;) {
                if (!in.ready()) {
                  try {
                    sleep(100);
                  } catch (InterruptedException expected) {
                  }
                  loop++;
                  if (loop > 5) {
                    break;
                  }
                } else {
                  int len = in.read(buf, 0, 1000);
                  loop = 0;
                  if (len == -1) {
                    break;
                  }
                  nchar += len;
                  output.write(buf, 0, len);
                }
              }
              prta("nchar=" + nchar + " loop=" + loop);
              out.write((args[1] + " written " + nchar + " bytes.").getBytes());
            }
            s.close();
            return;
          } catch (IOException e) {
            if (e.toString().contains("Permission denied")) {
              out.write("You do not have permission to write this file!\n".getBytes());
              out.write((e.getMessage() + "\n").getBytes());
            } else {
              out.write(("IOError: e=" + e.getMessage()).getBytes());
            }
            Util.IOErrorPrint(e, "Error read or writing putfile");
          }
          s.close();
          return;

        } else if (line.substring(0, 7).equalsIgnoreCase("Execute")) {
          prta(line);
          line = line.replaceAll("~", Util.homedir);
          prta("Start command line=" + line);
          Subprocess sp = new Subprocess(line.substring(8));
          int lastLength = 0;
          int lastErrLength = 0;
          int strerrlast = 0;
          boolean done = false;
          int errlen = 0;
          int stdlen = 0;
          int loop = 0;
          while (!done && !terminate && loop < 600) {
            String str = sp.getOutput();
            String strerr = sp.getErrorOutput();
            if (strerr.length() > strerrlast) {
              out.write(strerr.getBytes(), strerrlast, strerr.length() - strerrlast);
              strerr += strerr.length() - strerrlast;
              strerrlast = strerr.length();
            }
            if (str.length() > lastLength) {
              //prta("Write new output len="+lastLength+" s.len="+str.length()+" len="+(str.length() - lastLength));
              out.write(str.getBytes(), lastLength, str.length() - lastLength);
              stdlen += str.length() - lastLength;
              lastLength = str.length();
              lastUpdate = System.currentTimeMillis();
            }
            if (sp.exitValue() != -99 && !sp.isAlive()) {
              break;
            }
            try {
              sleep(100);
            } catch (InterruptedException expected) {
            }
            loop++;
          }
          prta("Command has exited terminate=" + terminate + " done=" + done
                  + " errlen=" + errlen + " stdlen=" + stdlen + " exit=" + sp.exitValue() + " sp.alive()=" + sp.isAlive() + " loop=" + loop);
          out.write(("Command exited term=" + terminate + " exit=" + sp.exitValue() + " alive=" + sp.isAlive() + " loop=" + loop + "\n").getBytes());
          if (loop >= 600) {
            out.write("WARNING: command timed out - it may not have fully executed\n".getBytes());
          }
          if (sp.isAlive()) {
            out.write("WARNING: subprocess still running at exit - it may not have fully executed\n".getBytes());
          }
          String str = sp.getOutput();
          if (str.length() > lastLength) {
            out.write(str.getBytes(), lastLength, str.length() - lastLength);
            stdlen += str.length() - lastLength;
            //lastLength = str.length();
            prta("Command final stdlen=" + stdlen + " str.len=" + str.length());
          }
          if (sp.getErrorOutput().length() > 0) {
            out.write(("\nerr output *****\n" + sp.getErrorOutput()).getBytes());
          }
          out.write(("\n" + line + " is done.\n").getBytes());
        } else {
          prta("Unknown command ignored");
          s.close();
          return;
        }
        out.write("Execution completed".getBytes());
        s.close();
      } catch (IOException e) {
        if (e.toString().contains("Broken pipe")) {
          prta("Connect dropped s=" + s);
        } else {
          prta("TSS:receive through IO exception");
          Util.IOErrorPrint(e, "TSS:receive IOException");
          if (e.getMessage().contains("Too many open")) {
            Util.exit(0);
          }
        }
      }
      if (!s.isClosed()) {
        try {
          s.close();
        } catch (IOException expected) {
        }
      }
    }
  }

  /**
   * @param args the command line arguments
   */
  public static void main(String[] args) {
    Util.setProcess("CommandStatusServer");
    Util.loadProperties("edge.prop");
    Util.setNoInteractive(true);
    Util.setNoconsole(false);
    //ServerThread server = new ServerThread(AnssPorts.PROCESS_SERVER_PORT, false);
    User user = new User("dkt");
    boolean qdping = false;
    boolean checkBH = false;
    HughesNOCChecker bhChecker = null;
    //try  {
    String argline = "";
    for (String arg : args) {
      argline += arg + " ";
      if (arg.equals("-qdping")) {
        qdping = true;
      } else if (arg.equals("-chkbh")) {
        checkBH = true;
      } else if (arg.equals("-?") || arg.indexOf("help") > 0) {
        Util.prt("CommandStatusServer [-p pppp][-qdping][-s NNN][-nooutput]");
        Util.prt("-p nnnn Set port name to something other than 7984");
        Util.prt("-qdping  Run QDPing from this server as well as Commands/status (def=off unless gacq1)");
        Util.prt("-?            Print this message");
        Util.prt("QDPing (effective if -qdping is set or run on gacq1) :");
        Util.prt("     -s nnn Set delay to nnn seconds (def=10)");
        Util.prt("     -nooutput turn off all output mysql updates, Ping checkers in QDPing.");
        Util.prt("     -baler  Send QDPings to Balers instead of Q330s");
        Util.prt("     -chkbh  Send pings to check the Hughes NOC backhauls (def on gacq1)");
        Util.exit(0);
      }
    }
    EdgeThread.setMainLogname("cmdstatus");
    Util.setModeGMT();
    Util.prta(Util.asctime() + " starting roles=" + System.getenv("VDL_ROLES"));
    String roles = System.getenv("VDL_ROLES");
    if (roles != null) {
      if (roles.contains("gacq1")) {
        qdping = true;
        checkBH = true;
      }
    }
    if (checkBH) {
      bhChecker = new HughesNOCChecker();
      Util.prta("HughesNOC mode enabled.");
    }
    if (qdping) {
      Util.prta("Running in QDPing mode");
    }
    Util.prta("starting");
    CommandStatusServer t = new CommandStatusServer(argline, "CSS");
    if (qdping) {
      QDPing.main(args);
    }
    for (;;) {
      try {
        Thread.sleep(3600000);
      } catch (InterruptedException expected) {
      }
      Util.loadProperties("edge.prop");
      Util.prta(Util.ascdate() + " CommandStatusServer.main() " + (bhChecker == null ? "" : "\n" + checkBH));
    }
  }

}
