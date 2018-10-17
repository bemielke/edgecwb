/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.edgeoutput;

import gov.usgs.alarm.SendEvent;
import gov.usgs.anss.edge.EdgeProperties;
import gov.usgs.anss.edge.IllegalSeednameException;
import gov.usgs.anss.edge.RawDisk;
import gov.usgs.anss.edgemom.EdgeChannelServer;
import gov.usgs.anss.edgethread.EdgeThread;
import gov.usgs.anss.seed.MiniSeed;
import gov.usgs.anss.util.SimpleSMTPThread;
import gov.usgs.anss.util.Util;
import gov.usgs.edge.config.Channel;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.TreeMap;

/**
 * RingFileToLISS - is an EdgeThread which Opens a RingFileInputer and supplies its data to a
 * ChannelInfrastucture. The output selection bit selects which data will be processed to the
 * ChannelInfrastructure. The class starts a monitor and shutdown handler.
 *
 * <br><br>
 * Command line arguments :
 * <br>
 * <PRE>
 * -ring filename  The filename of the ring file (used to open a RingFileInputer)
 * -secdepth ss    Seconds of depth to keep in the ChannelInfrastructure
 * -minrecs  rr    Minimum number of records to keep in the ChannelInfrastructure
 * -bitid    id    The ID for this output type (the mask is 1&lt&lt(bitid-1), channels with this bit set are sent to the ChannelInfrastructure
 * -defclass class The gov.usgs.anss.edgeoutput.class that will handle the data (passed to ChannelInfrastructure)
 * </PRE>
 *
 * @author davidketchum
 */
public final class RingFileToLISS extends EdgeThread {

  // local object attributes
  private TreeMap<String, LISSStationServer> stations = new TreeMap<String, LISSStationServer>();
  private String filename;
  private RingFileInputer in;
  private long bitmask;               // the sendto bit mask (1L << (BIT ID -1))

  // General EdgeMomThread attributes
  private long lastStatus;     // Time last status was received

  private RF2LISSMonitor monitor; // Watcher for hung conditions on this thread
  private long inbytes;         // number of input bytes processed
  private long outbytes;        // Number of output bytes processed
  private final RFTCIShutdown shutdown;
  boolean dbg;

  @Override
  public String toString() {
    return "RF2LISS:" + getName() + " inb=" + inbytes + " outb=" + outbytes;
  }

  /**
   * return number of bytes processed as input
   *
   * @return Number of bytes processed as input
   */
  public long getInbytes() {
    return inbytes;
  }

  /**
   * return number of bytes processed as input
   *
   * @return Number of bytes processed as input
   */
  public long getOutbytes() {
    return outbytes;
  }

  /**
   * set debug flat
   *
   * @param t What to set debug to!
   */
  public void setDebug(boolean t) {
    dbg = t;
  }

  /**
   * creates an new instance of RingFileToLISS - This one gets its arguments from a command line
   *
   * @param argline The command line to parse for input
   * @param tg The logging tag
   */
  public RingFileToLISS(String argline, String tg) {
    super(argline, tg);
    prt("args=" + argline);
    String[] args = argline.split("\\s");
    dbg = false;
    tag = tg;
    bitmask = 2;
    for (int i = 0; i < args.length; i++) {
      //prt(i+" arg="+args[i]);
      if (args[i].length() == 0) {
        continue;
      }
      switch (args[i]) {
        case "-dbg":
          dbg = true;
          break;
        case "-empty":
          ;         // Allow this for totally empty command lines
          break;
        case "-ring":
          filename = args[i + 1];
          i++;
          break;
        case "-bitid":
          bitmask = 1L << (Integer.parseInt(args[i + 1]) - 1);
          i++;
          break;
        case "-dbgch":
          prta("CH: dbg=" + args[i + 1]);
          ChannelHolder.setDebugSeedname(args[i + 1]);
          i++;
          break;
        default:
          break;
      }
    }
    prt("RingFileToLISS: new line parsed dbg=" + dbg + " ring=" + filename
            + " bit=" + Util.toHex(bitmask));
    tag += "RF2LISS:";  //with String and type
    //monitor = new RF2LISSMonitor(this);
    shutdown = new RFTCIShutdown();
    Runtime.getRuntime().addShutdownHook(shutdown);
    start();
  }

  @Override
  public void terminate() {
    // Set terminate do interupt.  If IO might be blocking, it should be closed here.
    terminate = true;
    interrupt();
    in.close();

  }

  /**
   * this thread reads new data from the Input ring file and sends it onto the ChannelInfrastructure
   * if the channels is on the MiniSeed list
   */
  @Override
  public void run() {
    running = true;               // mark we are running

    // Open the RingFileInputer.
    int next = 0;
    byte[] nextbuf = new byte[4];
    ByteBuffer bb = ByteBuffer.wrap(nextbuf);
    RawDisk gotlast = null;
    while (in == null) {
      try {
        gotlast = new RawDisk(filename.trim() + ".rliss", "r");
        int n = gotlast.readBlock(nextbuf, 0, 4);
        bb.position(0);
        next = bb.getInt();
        gotlast.close();
      } catch (FileNotFoundException e) {
        next = -1;
      } catch (IOException e) {
        Util.prta("IOException reading .rliss file=" + e.getMessage());
        e.printStackTrace();
        System.exit(1);
      }

      // Now reopen it as a read write file
      try {
        gotlast = new RawDisk(filename.trim() + ".rliss", "rw");
        if (next == -1) {
          bb.position(0);
          bb.putInt(next);
          try {
            gotlast.writeBlock(0, nextbuf, 0, 4);
          } catch (IOException e) {
            Util.prta("IOExcpection initializing .rliss file e=" + e.getMessage());
          }
        }

      } catch (FileNotFoundException e) {
        Util.prta("could not open .rliss file!!! e=" + e.getMessage());
        System.exit(0);
      }
      try {
        in = new RingFileInputer(filename, next);
      } catch (FileNotFoundException e) {
        prta(tag + " File not found=" + filename);
        SendEvent.edgeSMEEvent("BadRingLISS", "Ring file " + filename + " EOFError opening!", this);
        SimpleSMTPThread.email(Util.getProperty("emailTo"), "Ring file not found in RingFileToLISS " + filename, "BODY");
        try {
          sleep(3600000);
        } catch (InterruptedException expected) {
        }
        in = null;
      } catch (IOException e) {
        prta(tag + " ***** IOException reading Ringfile e=" + e);
        SendEvent.edgeSMEEvent("BadRingLISS", "Ring file " + filename + " IOError opening!", this);
        try {
          sleep(30000);
        } catch (InterruptedException expected) {
        }
      }
    }

    // Open the corresponding Channel infrastructure.
    byte[] buf512 = new byte[512];
    byte[] buf = buf512;
    MiniSeed ms = null;
    long lastNbytes = 0;
    int npackets = 0;
    lastStatus = System.currentTimeMillis();
    int len;
    String seedname;
    while (true) {
      try {                     // Runtime exception catcher
        if (terminate) {
          break;
        }

        try {
          len = in.getNextData(buf);        // Note: this will block if no data available
          if (terminate || len == -1) {
            break; // File is closed
          }
          inbytes += len;   // count the bytes
          npackets++;
          if (ms == null) {
            ms = new MiniSeed(buf);
          } else {
            ms.load(buf);
          }
          //prt("ms="+ms);
          seedname = ms.getSeedNameString();
          if (seedname.substring(7, 10).equals("LOG")) {
            prta("RF2LISS: LOG record=" + seedname);
            seedname = seedname.substring(0, 7) + "BHZ" + seedname.substring(10, 12);
            Channel c = EdgeChannelServer.getChannel(seedname, false);
            if (c == null) {
              seedname = seedname.substring(0, 7) + "BHZ00";
            }
          }
          Channel c = EdgeChannelServer.getChannel(seedname, false);
          //if(ms.getSeedNameString().substring(0,6).equals("IUANMO")) prta(ms.getSeedNameString()+" c.getSendtoMask="+c.getSendtoMask()+" bitmask="+bitmask);
          if (c != null) {
            if ((c.getSendtoMask() & bitmask) != 0) {
              LISSStationServer liss = LISSStationServer.getServer(ms.getSeedNameString());
              if (liss != null) {
                liss.queue(buf, 512);
              }
            }
          }

          // periodically update the next out for pickup latter.
          if (npackets % 20 == 0) {
            next = in.getNextout();
            bb.position(0);
            bb.putInt(next);
            gotlast.writeBlock(0, nextbuf, 0, 4);
          }

          // Is it time for status yet
          if (System.currentTimeMillis() - lastStatus > 600000) {
            prta(tag + "#pkt=" + npackets + "  nb =" + (inbytes - lastNbytes) + " Ring:" + in);
            lastStatus = System.currentTimeMillis();
            lastNbytes = inbytes;
          }
        } catch (IOException e) {
          prta(tag + " IOException reading from ringfile =" + e.getMessage());
          e.printStackTrace();
        } catch (IllegalSeednameException e) {
          prta(tag + " IllegalSeednameException! e=" + e.getMessage());
        }
      } catch (RuntimeException e) {
        prta(tag + " RuntimeException in ringtochan " + this.getClass().getName() + " e=" + e.getMessage());
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
        }
      }
    }       // while(true) do socket open
    //monitor.terminate();
    prt(tag + " is terminated.");
    running = false;
    terminate = false;
  }

  /**
   * return the monitor string for ICINGA
   *
   * @return A String representing the monitor key value pairs for this EdgeThread
   */
  @Override
  public StringBuilder getMonitorString() {
    return monitorsb;
  }

  @Override
  public StringBuilder getStatusString() {
    return statussb;
  }

  @Override
  public StringBuilder getConsoleOutput() {
    return consolesb;
  } //  we use prt directly

  /**
   * RF2LISSMonitor the RingFileToLISS and stop it if it does not receive heartBeats or data!
   */
  public final class RF2LISSMonitor extends Thread {

    boolean terminate;        // If true, this thread needs to exit
    int msWait;               // user specified period between checks
    boolean interruptDone;    // Flag that we have gone off
    RingFileToLISS thr;      // The thread being RF2LISSMonitored
    long lastInbytes;         // count of last to look for stalls

    public boolean didInterrupt() {
      return interruptDone;
    }

    public void terminate() {
      terminate = true;
      interrupt();
    }

    public RF2LISSMonitor(RingFileToLISS t) {
      thr = t;
      msWait = 360000;      // Set the ms between checks
      start();
    }

    @Override
    public void run() {
      long lastNbytes = 0;
      //try{sleep(msWait);} catch(InterruptedException e) {}
      while (!terminate) {
        try {
          sleep(msWait);
        } catch (InterruptedException e) {
        }
        //prta(tag+" LCM: HB="+heartBeat+" lHB="+lastHeartbeat+" in ="+inbytes+" lin="+lastInbytes);
        if (inbytes - lastNbytes < 1000) {
          thr.interrupt();      // Interrupt in case its in a wait
          // Close the ring file
          /*if(in != null) {
            try {
              if(!in.isClosed()) d.close();  // Force IO abort by closing the socket
            }
            catch(IOException e) {
              Util.prta(tag+" LCM: close socket IOException="+e.getMessage());
            }
          }*/
          lastNbytes = inbytes;
          interruptDone = true;     // So interrupter can know it was us!
          prta(tag + " RF2LISSMonitor has gone off ");
          try {
            sleep(msWait);
          } catch (InterruptedException e) {
          }
          interruptDone = false;
        }
        lastInbytes = inbytes;
      }
    }
  }

  /**
   * this internal class gets registered with the Runtime shutdown system and is invoked when the
   * system is shutting down. This must cause the thread to exit
   */
  class RFTCIShutdown extends Thread {

    /**
     * default constructor does nothing the shutdown hook starts the run() thread
     */
    public RFTCIShutdown() {
    }

    // This is called by the RunTime.shutdown at any termination 
    @Override
    public void run() {
      prta("RF2LISS Shutdown() started...");
      terminate();          // send terminate to main thread and cause interrupt
      int loop = 0;
      while (running) {
        // Do stuff to aid the shutdown
        try {
          sleep(1000);
        } catch (InterruptedException e) {
        }
        if (loop % 20 == 0) {
          prta(Util.getThreadsString());
        }
        loop++;
        if (loop > 200) {
          break;
        }
      }
      prta(Util.getThreadsString());
      prta("RF2LISS shutdown() is complete.");
    }
  }

  /**
   * @param args the command line arguments
   */
  public static void main(String[] args) {
    // TODO code application logic here
    EdgeProperties.init();
    boolean makeCheck = true;
    Util.setModeGMT();
    //EdgeMom.setNoConsole(false);
    //EdgeMom.prt("no console");
    EdgeThread.setUseConsole(true);
    boolean dbg = false;
    Util.setNoInteractive(true);
    Util.prta("Starting");
    EdgeChannelServer echn = new EdgeChannelServer("-empty", "ECHN");
    String argline = "";
    for (String arg : args) {
      argline += arg + " ";
      if (arg.equals("-dbg")) {
        dbg = true;
      }
    }
    while (!EdgeChannelServer.isValid()) {
      Util.sleep(100);
    }
    Util.prta("Start RF2LISS");
    // -host n.n.n.n -port nn -mhost n.n.n.n -mport nn -msgmax max -qsize nnnn -wait nnn (secs) -module n -inst n
    //HydraOutputer.setCommandLine("");     // set all defaults  
    RingFileToLISS infra = new RingFileToLISS(
            argline, "RF2LISStest");

  }

}
