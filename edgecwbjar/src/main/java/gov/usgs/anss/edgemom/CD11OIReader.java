
/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.edgemom;

import gov.usgs.alarm.SendEvent;
import gov.usgs.anss.cd11send.CD11StationProcessor;
import gov.usgs.anss.edge.EdgeProperties;
import gov.usgs.anss.edge.IllegalSeednameException;
import gov.usgs.anss.edge.IndexFile;
import gov.usgs.anss.edge.RawDisk;
import gov.usgs.anss.edgeoutput.RingFileInputer;
import gov.usgs.anss.edgethread.EdgeThread;
import gov.usgs.anss.seed.MiniSeed;
import gov.usgs.anss.util.Util;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.TreeMap;

/**
 * CD11OIReader - is an EdgeThread which Opens a RingFileInputer and supplies
 * its data to one or more CD11StationProcessors. These are built dynamically as
 * new stations are discovered in the OI file (that is the data must be in the
 * ring file for this thread to start the CD11StationProcessor for the data).
 * The CD11StationProcessor uses the config file to configure its operating
 * characteristics and that of the CD11SenderClient.
 *
 * <br><br>
 * Command line arguments :
 * <br>
 * <PRE>
 * -ring	 filename  The filename of the ring file (used to open a RingFileInputer)
 * -config filename  The config filename for the individual stations in a group
 * -dbg							 Turn on more debug output
 *
 * The config file consists of lines which are parsed by CD11RingProcessor and CD11SenderClient.  The
 * TAG portion must be the network and station as in "IUANMO". The station portion can be less than five
 * characters, but the network portion must be two characters.  For a single-character network code,
 * put a single white space between the network character and first station character.
 * An example line looks like :
 * IUANMO:-dbg -secdepth 300 -ringpath /data2 -recsize 4096 -maxrec 10000 -creator ANMO -destination 0 -ip 192.239.137.12 -p 8046 -b 1.2.3.4 >>anmo2
 * G PEL:-dbg -secdepth 300 -ringpath /data2 -recsize 4096 -maxrec 10000 -creator PEL -destination 0 -ip 192.239.137.12 -p 8046 -b 1.2.3.4 >>pel2
 *
 * Parameter used by CD11StationProcessor and CD11SenderCLient:
 * Tag					arg          Description
 * -recsize			nnnnn				 The size in bytes of records in the CD1.1 output ring file (the input to this thread).
 * -maxrec			nnnnn			 	 The number of records in the CD1.1 output ring file (the modulo to seq for rec position).
 * -ringpath		/path				 The path on which ring files are to be found, the filenames are dictated by the station.cd11out.
 * -creator			creator			 The creator name to use in CD1.1 for the source of this data (normally usgs).
 * -destination dest				 The destination name to use in the CD1.1 for the destination of this data (0 or ndc usually).
 * -dbg											 More output.
 * -dbgdet									 Decode all output packets and print a summary - this also sets the -dbg flag.
 *
 * Parameters used by CD11SenderClient:
 * -p    ppppp        Run the server on this port.
 * -ip   ip.adr       The computer IP to contact to initiate the CD1.1 exchange.
 * -b    ip.adr       The address of the local computer to bind to this end of the socket.
 *
 * Parameters handled by CD11StationProcessor only:
 * -secdepth secs     The number of seconds of depth in the processor in the clear buffers.
 *
 * >[>]  filename     You can redirect output from this thread to filename in the current logging directory.
 *
 * ConfigFile (/path/STATION.config):
 * Gap List size=5 series=0
 * LowSeq =27360959         # Lowest sequence still open for having gaps.
 * HighSeq=27361100         # Highest sequence acknowledged.
 * start1-end1              # Gap1 range.
 * start2- end2             # Gap2 range.
 * .
 * .
 * </PRE>
 *
 * @author davidketchum
 */
public final class CD11OIReader extends EdgeThread {

  // Local object attributes
  TreeMap<String, CD11StationProcessor> processors = new TreeMap<>();
  private String filename;      // The OI filename which is the cd11ring filename base
  private String configFile;    // The file with output socket configuration data
  private RingFileInputer in;   // Reader of data from OI

  // General EdgeMomThread attributes
  private long lastStatus;     // Time of last status was received

  private long inbytes;         // Number of input bytes processed
  private long outbytes;        // Number of output bytes processed
  private StringBuilder tmpsb = new StringBuilder(50);
  CD11OIReaderShutdown shutdown;
  boolean dbg;

  @Override
  public String toString() {
    return toStringBuilder(null).toString();
  }

  public StringBuilder toStringBuilder(StringBuilder tmp) {
    StringBuilder sb = tmp;
    if (sb == null) {
      sb = Util.clear(tmpsb);
    }
    synchronized (sb) {
      sb.append("CD11OI:").append(getName()).append(" inb=").append(inbytes).append(" outb=").append(outbytes);
    }
    return sb;
  }

  /**
   * Return the number of bytes processed as input.
   *
   * @return Number of bytes processed as input.
   */
  public long getInbytes() {
    return inbytes;
  }

  /**
   * Return the number of bytes processed as input.
   *
   * @return Number of bytes processed as input.
   */
  public long getOutbytes() {
    return outbytes;
  }

  /**
   * Set debug as flat.
   *
   * @param t What to set debug to!
   */
  public void setDebug(boolean t) {
    dbg = t;
  }

  /**
   * Creates an new instance of CD11OIReaderShutdown - This one gets its
   * arguments from a command line.
   *
   * @param argline The argument line for parameters.
   * @param tg The tag by which this thread will be known.
   */
  public CD11OIReader(String argline, String tg) {
    super(argline, tg);
    prta(Util.clear(tmpsb).append("args=").append(argline));
    String[] args = argline.split("\\s");
    dbg = false;
    tag = tg;

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
      } else if (args[i].equals("-config")) {
        configFile = args[i + 1];
        i++;
      } else if (args[i].substring(0, 1).equals(">")) {
        break;
      } else {
        prt(Util.clear(tmpsb).append("CD11OIReader unknown switch=").append(args[i]).append(" ln=").append(argline));
      }
    }
    prt(Util.clear(tmpsb).append("CD11OIReader: new line parsed dbg=").append(dbg).
            append(" ring=").append(filename).append(" config=").append(configFile));
    tag += "CD11OIRdr:";  //With String and type

    // Configure a shutdown handler for this thread
    shutdown = new CD11OIReaderShutdown();
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
   * This thread reads new data from the Input ring file and sends it onto the
   * ChannelInfrastructure if the channels are on the MiniSeed list.
   */
  @Override
  public void run() {
    running = true;               // Mark that we are running

    // Open the RingFileInputer
    int next = 0;
    byte[] nextbuf = new byte[4];
    ByteBuffer bb = ByteBuffer.wrap(nextbuf);
    RawDisk gotlast = null;
    StringBuilder runsb = new StringBuilder(50);
    while (in == null) {
      if (terminate) {
        break;
      }
      try {
        gotlast = new RawDisk(filename.trim() + ".last", "r");
        int n = gotlast.readBlock(nextbuf, 0, 4);
        bb.position(0);
        next = bb.getInt();
        gotlast.close();
      } catch (FileNotFoundException e) {
        next = -1;
      } catch (IOException e) {
        prta(Util.clear(runsb).append(tag).append("IOException reading .last file=").append(e.getMessage()));
        e.printStackTrace();
        Util.exit(1);
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
            prta(Util.clear(runsb).append(tag).append("IOException initializing .last file e=").append(e.getMessage()));
          }
        }

      } catch (FileNotFoundException e) {
        prta(Util.clear(runsb).append(tag).append("could not open .last file!!! e=").append(e.getMessage()));
        Util.exit(0);
      }
      try {
        in = new RingFileInputer(filename, next);
      } catch (FileNotFoundException e) {
        prta(Util.clear(runsb).append(tag).append(" File not found=").append(filename));
        SendEvent.edgeSMEEvent("CD11NoRing", "Ring file not found in CD11OIReaderShutdown " + filename, this);
        try {
          sleep(120000);
        } catch (InterruptedException expected) {
        }
        in = null;
      } catch (IOException e) {
        prta(Util.clear(runsb).append(tag).append(" IOException opening file e=").append(e));
      }
    }
    prta(Util.clear(runsb).append(tag).append(" next at startup=").append(next).append(" file=").append(filename));
    // Open the corresponding Channel infrastructure
    byte[] buf4096 = new byte[4096];
    byte[] buf512 = new byte[512];
    byte[] buf;
    MiniSeed ms = null;
    long lastNbytes = 0;
    int npackets = 0;
    lastStatus = System.currentTimeMillis();
    long lastNoConfig = 0;
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
          inbytes += len;   // Count the bytes
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
          CD11StationProcessor proc = processors.get(ms.getSeedNameSB().substring(0, 7));
          if (proc == null) {
            proc = new CD11StationProcessor(configFile, ms, this);
            processors.put(ms.getSeedNameSB().substring(0, 7), proc);
          }

          // Process the packet; if it returns that a new CD11 packet can be built, build it and write it to the disk
          if (dbg) {
            prta(Util.clear(runsb).append("Proc: ").append(ms.toStringBuilder(null)));
          }
          proc.queue(ms);

          // Periodically update the next out for pickup latter.
          if (npackets % 100 == 0) {
            next = in.getNextout();
            bb.position(0);
            bb.putInt(next);
            gotlast.writeBlock(0, nextbuf, 0, 4);
            proc.writeCD11Header();                // Update the CD11Ring header with current state
          }

          // Is it time for status yet
          if (System.currentTimeMillis() - lastStatus > 600000) {
            Iterator<CD11StationProcessor> itr = processors.values().iterator();
            int n = 0;
            int nconn = 0;
            while (itr.hasNext()) {
              n++;
              if (itr.next().isConnected()) {
                nconn++;
              }
            }
            if (nconn == 0 && n > 5) {
              SendEvent.edgeSMEEvent("CD11FWDNoCon", "Number of connections to AFTAC is small " + nconn + "/" + n, this);
            }
            prta(Util.clear(runsb).append(tag).append("#pkt=").append(npackets).
                    append(" nb=").append(inbytes - lastNbytes).
                    append(" nconn=").append(nconn).append("/").
                    append(n).append(" Ring:").append(in));
            lastStatus = System.currentTimeMillis();
            lastNbytes = inbytes;

          }
        } catch (FileNotFoundException e) {
          if (e.getMessage().contains("configfile")) {
            if (System.currentTimeMillis() - lastNoConfig > 600000) {
              prta(Util.clear(runsb).append(tag).
                      append(" * There is a station with data that is not in the config file station=").
                      append(ms == null ? "null" : ms.getSeedNameSB()));
              //SendEvent.debugEvent("CD11Config", "Output station not config "+ms.getSeedNameString().substring(0,7), this);
              lastNoConfig = System.currentTimeMillis();
            }
          } else {
            prta(Util.clear(runsb).append(tag).append(" Got file not found=").append(e));
          }
        } catch (IOException e) {
          prta(Util.clear(runsb).append(tag).append(" IOException reading from ringfile =").append(e.getMessage()));
          e.printStackTrace();
        } catch (IllegalSeednameException e) {
          prta(Util.clear(runsb).append(tag).append(" IllegalSeednameException! e=").append(e.getMessage()));
        }
      } catch (RuntimeException e) {
        prta(tag + " RuntimeException in CD11OIReader " + this.getClass().getName() + " e=" + e.getMessage() + " ms=" + ms);
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
    }       // While(true) do socket open
    //monitor.terminate();
    prta(Util.clear(runsb).append(tag).append(" out of main loop.  Do shutdown."));
    next = in.getNextout();
    bb.position(0);
    bb.putInt(next);
    try {
      gotlast.writeBlock(0, nextbuf, 0, 4);
      gotlast.close();
      Iterator<CD11StationProcessor> itr = processors.values().iterator();
      while (itr.hasNext()) {
        CD11StationProcessor ir = itr.next();
        ir.writeCD11Header();
        ir.terminate();
      }
      in.close();
    } catch (IOException e) {
      prta(Util.clear(runsb).append("Got IOError trying to update status file or closing rings ").append(e));
    } catch (RuntimeException e) {
      prta(Util.clear(runsb).append("Got Runtime trying to shutdown e=").append(e));
      e.printStackTrace(getPrintStream());
    }
    prta(Util.clear(runsb).append(tag).append(" is terminated."));
    running = false;
    terminate = false;
  }

  /**
   * Return the monitor string for Icinga.
   *
   * @return A String representing the monitor key value pairs for this
   * EdgeThread.
   */
  @Override
  public StringBuilder getMonitorString() {
    return monitorsb;
  }

  @Override
  public StringBuilder getStatusString() {
    Util.clear(statussb);
    statussb.append(" #inbytes=").append(inbytes / 1000).append(" kB #Station=").append(processors.size()).append("\n");
    Iterator<CD11StationProcessor> itr = processors.values().iterator();
    while (itr.hasNext()) {
      statussb.append("     ").append(itr.next().getStatus()).append("\n");
    }
    return statussb;
  }

  @Override
  public StringBuilder getConsoleOutput() {
    return consolesb;
  } //  we use prt directly

  /**
   * RF2CIMonitor the CD11OIReaderShutdown and stop it if it does not receive
   * heartBeats or data!
   */
  private final class RF2CIMonitor extends Thread {

    boolean terminate;        // If true, this thread needs to exit
    int msWait;               // User specified period between checks
    boolean interruptDone;    // Flag that we have gone off
    CD11OIReaderShutdown thr;      // The thread being RF2CIMonitored
    long lastInbytes;         // Count of last to look for stalls
    StringBuilder tmpsb = new StringBuilder(50);

    public boolean didInterrupt() {
      return interruptDone;
    }

    public void terminate() {
      terminate = true;
      interrupt();
    }

    public RF2CIMonitor(CD11OIReaderShutdown t) {
      thr = t;
      msWait = 360000;      // Set the ms between checks
      start();
    }

    @Override
    public void run() {
      long lastNbytes = 0;
      //try{sleep(msWait);} catch(InterruptedException expected) {}
      while (!terminate) {
        try {
          sleep(msWait);
        } catch (InterruptedException expected) {
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
              prta(tag+" LCM: close socket IOException="+e.getMessage());
            }
          }*/
          lastNbytes = inbytes;
          interruptDone = true;     // So interrupter can know it was us!
          prta(Util.clear(tmpsb).append(tag).append(" CD11OIMonitor has gone off "));
          try {
            sleep(msWait);
          } catch (InterruptedException expected) {
          }
          interruptDone = false;
        }
        lastInbytes = inbytes;
      }
    }
  }

  /**
   * This internal class gets registered with the Runtime shutdown system and is
   * invoked when the system is shutting down. This must cause the thread to
   * exit.
   */
  private final class CD11OIReaderShutdown extends Thread {

    /**
     * Default constructor does nothing; the shutdown hook starts the run()
     * thread.
     */
    public CD11OIReaderShutdown() {
    }

    // This is called by the RunTime.shutdown at any termination 
    @Override
    public void run() {
      prta("CD11OIR Shutdown() started...");
      terminate();          // Send terminate to main thread and cause interrupt
      int loop = 0;
      while (running) {
        // Do stuff to aid the shutdown
        try {
          sleep(4000);
        } catch (InterruptedException expected) {
        }
        if (loop % 20 == 0) {
          prta(Util.getThreadsString());
        }
        loop++;
        if (loop > 50) {
          break;
        }
      }
      prta(Util.getThreadsString());
      prta("CD11OIR shutdown() is complete.");
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
    EdgeChannelServer echn = new EdgeChannelServer("-empty", "ECHN");    // Setup so test works
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
      } catch (InterruptedException expected) {
      }
    }
    Util.prta("Start CD11OI");
    // -host n.n.n.n -port nn -mhost n.n.n.n -mport nn -msgmax max -qsize nnnn -wait nnn (secs) -module n -inst n
    //HydraOutputer.setCommandLine("");     // set all defaults  
    CD11OIReader infra = new CD11OIReader(
            argline, "CD11OI");

  }

}
