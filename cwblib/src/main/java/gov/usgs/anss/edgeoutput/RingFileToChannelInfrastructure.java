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
import gov.usgs.anss.edge.IndexFile;
import gov.usgs.anss.edge.RawDisk;
import gov.usgs.anss.edgemom.EdgeChannelServer;
import gov.usgs.anss.edgethread.EdgeThread;
import gov.usgs.anss.seed.MiniSeed;
import gov.usgs.anss.util.Util;
import gov.usgs.edge.config.Channel;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * RingFileToChannelInfrastructure - is an EdgeThread which Opens a RingFileInputer and supplies its
 * data to a ChannelInfrastucture. The output selection bit selects which data will be processed to
 * the ChannelInfrastructure. The class starts a monitor and shutdown handler.
 *
 * <br><br>
 * Command line arguments :
 * <br>
 * <PRE>
 * -ring filename  The filename of the ring file (used to open a RingFileInputer)
 * -secdepth ss    Seconds of depth to keep in the ChannelInfrastructure
 * -minrecs  rr    Minimum number of records to keep in the ChannelInfrastructure
 * -bitid    id    The ID for this output type (the mask is 1<<(bitid-1), channels with this bit set are sent to the ChannelInfrastructure
 * -defclass class The gov.usgs.anss.edgeoutput.class that will handle the data (passed to ChannelInfrastructure)
 * </pre>
 *
 * @author davidketchum
 */
public final class RingFileToChannelInfrastructure extends EdgeThread {

  // local object attributes
  private String filename;
  private RingFileInputer in;
  private ChannelInfrastructure chanInfra;
  private int secsDepth;
  private int minRecs;
  private long bitmask;               // the sendto bit mask (1L << (BIT ID -1))
  private Class defClass;             // The default outputer class for thie ChannelInfrastructure

  // General EdgeMomThread attributes
  private long lastStatus;     // Time last status was received

  private RF2CIMonitor monitor; // Watcher for hung conditions on this thread
  private long inbytes;         // number of input bytes processed
  private long outbytes;        // Number of output bytes processed
  private RFTCIShutdown shutdown;
  private boolean dbg;

  @Override
  public String toString() {
    return "RF2CI:" + getName() + " inb=" + inbytes + " outb=" + outbytes + " secdepth=" + secsDepth + " minr=" + minRecs + " defclass=" + defClass;
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
   * creates an new instance of RingFileToChannelInfrastructure - This one gets its arguments from a
   * command line
   *
   * @param argline The argline to start with
   * @param tg The logging tag for this thread.
   */
  public RingFileToChannelInfrastructure(String argline, String tg) {
    super(argline, tg);
    prt("args=" + argline);
    String[] args = argline.split("\\s");
    dbg = false;
    secsDepth = 600;
    minRecs = 3;
    tag = tg;
    bitmask = 2;
    for (int i = 0; i < args.length; i++) {
      //prt(i+" arg="+args[i]);
      if (args[i].length() == 0) {
        continue;
      }
      if (args[i].equals("-dbg")) {
        dbg = true;
      } else if (args[i].equals("-empty")) ; // Allow this for totally empty command lines
      else if (args[i].equals("-ring")) {
        filename = args[i + 1];
        i++;
      } else if (args[i].equals("-secdepth")) {
        secsDepth = Integer.parseInt(args[i + 1]);
        i++;
      } else if (args[i].equals("-minrecs")) {
        minRecs = Integer.parseInt(args[i + 1]);
        i++;
      } else if (args[i].equals("-bitid")) {
        bitmask = 1L << (Integer.parseInt(args[i + 1]) - 1);
        i++;
      } else if (args[i].equals("-dbgch")) {
        prta("CH: dbg=" + args[i + 1]);
        ChannelHolder.setDebugSeedname(args[i + 1]);
        i++;
      } else if (args[i].equals("-defclass")) {
        String c = args[i + 1];       // This is a class name, normally in gov.usgs.anss.edgeoutput
        i++;
        if (!c.contains("gov.usgs")) {
          c = "gov.usgs.anss.edgeoutput." + c;
        }
        try {
          defClass = Class.forName(c);
        } catch (ClassNotFoundException e) {
          prt("Class not found for class=" + c + " e=" + e.getMessage());
          System.exit(0);
        }
      } else if (args[i].substring(0, 1).equals(">")) {
        break;
      } else {
        prt("RingFileToChannelInfrastructure unknown switch=" + args[i] + " ln=" + argline);
      }
    }
    if (defClass == null) {
      prt("RingFileToChannelInfrastructure: there is no -defclass on command line or the class does not exist!");
      System.exit(1);
    }
    prt("RingFileToChannelInfrastructure: new line parsed dbg=" + dbg + " ring=" + filename
            + " depth=" + secsDepth + " minrec=" + minRecs + " defclass=" + defClass + " bit=" + Util.toHex(bitmask));
    tag += "RF2CI:";  //with String and type
    //monitor = new RF2CIMonitor(this);
    shutdown = new RFTCIShutdown();
    Runtime.getRuntime().addShutdownHook(shutdown);
    chanInfra = new ChannelInfrastructure(filename, secsDepth, minRecs, defClass, 200, this);
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
        gotlast = new RawDisk(filename.trim() + ".last", "r");
        int n = gotlast.readBlock(nextbuf, 0, 4);
        bb.position(0);
        next = bb.getInt();
        gotlast.close();
      } catch (FileNotFoundException e) {
        next = -1;
      } catch (IOException e) {
        Util.prta("IOException reading .last file=" + e.getMessage());
        e.printStackTrace();
        System.exit(1);
      }

      // Now reopen it as a read write file
      try {
        gotlast = new RawDisk(filename.trim() + ".last", "rw");
        if (next == -1) {
          bb.position(0);
          bb.putInt(next);
          try {
            gotlast.writeBlock(0, nextbuf, 0, 4);
          } catch (IOException e) {
            Util.prta("IOExcpection initializing .last file e=" + e.getMessage());
          }
        }

      } catch (FileNotFoundException e) {
        Util.prta("could not open .last file!!! e=" + e.getMessage());
        System.exit(0);
      }
      try {
        in = new RingFileInputer(filename, next);
      } catch (IOException e) {
        prta(tag + " File not found=" + filename);
        SendEvent.edgeSMEEvent("RFTCINoFile", "Ring file not found in RingFileToChannelInfra " + filename, this);
        try {
          sleep(3600000);
        } catch (InterruptedException e2) {
        }
        in = null;
      }
    }

    // Open the corresponding Channel infrastructure.
    byte[] buf4096 = new byte[4096];
    byte[] buf512 = new byte[512];
    byte[] buf;
    MiniSeed ms = null;
    long lastNbytes = 0;
    int npackets = 0;
    lastStatus = System.currentTimeMillis();
    int len;
    while (true) {
      try {                     // Runtime exception catcher
        if (terminate) {
          break;
        }

        try {
          len = in.getNextData(buf4096);        // Note: this will block if no data available
          if (terminate || len == -1) {
            break; // File is closed
          }
          inbytes += len;   // count the bytes
          npackets++;
          if (len == 512) {
            System.arraycopy(buf4096, 0, buf512, 0, 512);
            buf = buf512;
          } else {
            buf = buf4096;
          }
          if (ms == null) {
            ms = new MiniSeed(buf);
          } else {
            ms.load(buf);
          }
          //prt("ms="+ms);
          Channel c = EdgeChannelServer.getChannel(ms.getSeedNameString());
          //if(ms.getSeedNameString().substring(0,6).equals("IUANMO")) prta(ms.getSeedNameString()+" c.getSendtoMask="+c.getSendtoMask()+" bitmask="+bitmask);
          if (c != null) {
            if ((c.getSendtoMask() & bitmask) != 0) {
              chanInfra.addData(ms);     // Add this data to the channel infrastructure
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
            prta(tag + "#pkt=" + npackets + "  nb =" + (inbytes - lastNbytes) + " cinfra:" + chanInfra + " Ring:" + in);
            prta(ChannelHolder.getSummary());
            prta(HydraOutputer.queueSummary());
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
            SendEvent.edgeEvent("OutOfMemory", "Out of Memory in " + Util.getProcess() + "/" + this.getClass().getName() + " on " + IndexFile.getNode(), this);
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
   * RF2CIMonitor the RingFileToChannelInfrastructure and stop it if it does not receive heartBeats
   * or data!
   */
  public final class RF2CIMonitor extends Thread {

    boolean terminate;        // If true, this thread needs to exit
    int msWait;               // user specified period between checks
    boolean interruptDone;    // Flag that we have gone off
    RingFileToChannelInfrastructure thr;      // The thread being RF2CIMonitored
    long lastInbytes;         // count of last to look for stalls

    public boolean didInterrupt() {
      return interruptDone;
    }

    public void terminate() {
      terminate = true;
      interrupt();
    }

    public RF2CIMonitor(RingFileToChannelInfrastructure t) {
      thr = t;
      msWait = 360000;      // Set the ms between checks
      gov.usgs.anss.util.Util.prta("new ThreadMonitor " + getName() + " " + getClass().getSimpleName() + " t=" + t);
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
          prta(tag + " RF2CIMonitor has gone off ");
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
      gov.usgs.anss.util.Util.prta("new ThreadShutdown " + getName() + " " + getClass().getSimpleName());
    }

    // This is called by the RunTime.shutdown at any termination 
    @Override
    public void run() {
      prta("RFTCI Shutdown() started...");
      terminate();          // send terminate to main thread and cause interrupt
      int loop = 0;
      chanInfra.shutdown();
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
      prta("RFTCI shutdown() is complete.");
    }
  }

  /**
   * @param args the command line arguments
   */
  public static void main(String[] args) {
    EdgeProperties.init();
    boolean makeCheck = true;
    Util.setModeGMT();
    //EdgeMom.setNoConsole(false);
    //EdgeMom.prt("no console");
    EdgeThread.setUseConsole(true);
    boolean dbg = false;
    Util.setNoInteractive(true);
    Util.prta("Starting");
    EdgeChannelServer echn = new EdgeChannelServer("-empty", "ECHN");    // creation in test routine
    String argline = "";
    for (String arg : args) {
      argline += arg + " ";
      if (arg.equals("-dbg")) {
        dbg = true;
      }
    }
    while (!EdgeChannelServer.isValid()) {
      try {
        Thread.sleep(100);
      } catch (InterruptedException e) {
      }
    }
    Util.prta("Start RFTCI");
    // -host n.n.n.n -port nn -mhost n.n.n.n -mport nn -msgmax max -qsize nnnn -wait nnn (secs) -module n -inst n
    //HydraOutputer.setCommandLine("");     // set all defaults  
    RingFileToChannelInfrastructure infra = new RingFileToChannelInfrastructure(
            argline, "RF2CItest");

  }

}
