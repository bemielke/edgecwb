/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.edgemom;

import gov.usgs.alarm.SendEvent;
import gov.usgs.anss.edge.ChannelSender;
import gov.usgs.anss.edge.IllegalSeednameException;
import gov.usgs.anss.edge.IndexBlock;
import gov.usgs.anss.edgethread.EdgeThread;
import gov.usgs.anss.net.RingFile;
import gov.usgs.anss.seed.MiniSeed;
import gov.usgs.anss.util.Util;
import gov.usgs.edge.config.Channel;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.GregorianCalendar;

/**
 * The RRPToEdge will read in a RingFile created by an instance of RRPServer and
 * feed the mini-seed into this edge instance. This in generally used to insert
 * data moved via the RRP protocol reliably into an Edge Instance.
 * <p>
 * <PRE>
 *switch   arg       Description
 *-dbg               Turn debug output on
 *-file  filename    The ringfile name to use.
 *-nohydra           If present, data is not run to Hydra for processing to trace wire
 *-noudpchan         If present, do not send UDP summary packets to UdpChannel (No ChannelDisplay)
 * -blk  nnnnn       Force the RingFile to start reading at the given block, expert uses are obvious.
 * </PRE>
 *
 * @author davidketchum
 */
public final class RRPToEdge extends EdgeThread {

  private String filename;
  private RingFile in;
  private final ShutdownRRPToEdge shutdown;
  int nblocks;
  private ChannelSender csend;
  private boolean dbg;
  private boolean hydra;
  private String orgtag;
  private boolean add1024;        // if Q680s are off 1024 weeks, add this in on input
  private GregorianCalendar g1024;
  private int overrideBlock = -1;
  private boolean nocsend;
  private final StringBuilder tmpsb = new StringBuilder(100);

  /**
   * Set debug state.
   *
   * @param t The new debug state.
   */
  public void setDebug(boolean t) {
    dbg = t;
  }

  /**
   * Terminate thread (causes an interrupt to be sure). You may not need the
   * interrupt!
   */
  @Override
  public void terminate() {
    terminate = true;
    if (in != null) {
      in.terminate();
    }
    interrupt();
  }

  /**
   * Return the status string for this thread.
   *
   * @return A string representing status for this thread
   */
  @Override
  public StringBuilder getStatusString() {
    if (statussb.length() > 0) {
      statussb.delete(0, statussb.length());
    }
    return statussb.append("RRP2Edge: nblks=").append(nblocks);
  }
  /**
   * Return the monitor string for Icinga.
   *
   * @return A String representing the monitor key value pairs for this
   * EdgeThread.
   */
  long lastMonNblocks;

  @Override
  public StringBuilder getMonitorString() {
    if (monitorsb.length() > 0) {
      monitorsb.delete(0, monitorsb.length());
    }
    long nb = nblocks - lastMonNblocks;
    lastMonNblocks = nblocks;
    return monitorsb.append(orgtag).append("-RRP2EdgeNBlocks=").append(nb).append("\n");
  }

  /**
   * Return console output - this is fully integrated so it never returns
   * anything.
   *
   * @return "" Since this cannot get output outside of the prt() system.
   */
  @Override
  public StringBuilder getConsoleOutput() {
    return consolesb;
  }

  /**
   * Creates an new instance of LISSCLient - which will try to stay connected to
   * the host/port source of data. This one gets it's arguments from a command
   * line.
   *
   * @param argline The argument line to parse for running arguments.
   * @param tg The logging tag.
   */
  public RRPToEdge(String argline, String tg) {
    super(argline, tg);
//initThread(argline,tg);
    orgtag = tg;
    if (orgtag.contains("-")) {
      orgtag = orgtag.substring(0, orgtag.indexOf("-"));
    }
    String[] args = argline.split("\\s");
    dbg = false;
    nocsend = false;
    add1024 = false;
    hydra = true;
    for (int i = 0; i < args.length; i++) {
      //prt(i+" arg="+args[i]);
      if (args[i].length() == 0) {
        continue;
      }
      if (args[i].equals("-dbg")) {
        dbg = true;
      } else if (args[i].equals("-file")) {
        filename = args[i + 1];
        i++;
      } else if (args[i].equals("-nohydra")) {
        hydra = false;
      } else if (args[i].substring(0, 1).equals(">")) {
        break;
      } else if (args[i].equals("-noudpchan")) {
        nocsend = true;
      } else if (args[i].equals("-add1024")) {
        g1024 = new GregorianCalendar();
        add1024 = true;
      } else if (args[i].equals("-blk")) {
        overrideBlock = Integer.parseInt(args[i + 1]);
        i++;
      } else {
        prt(Util.clear(tmpsb).append("RRPToEdge: unknown switch=").append(args[i]).append(" ln=").append(argline));
      }

    }
    prt(Util.clear(tmpsb).append("Rep: created args=").append(argline).append(" tag=").append(tag));
    if (!nocsend) {
      csend = new ChannelSender("  ", "RRP2Edge", "R2E-" + orgtag);
    }
    shutdown = new ShutdownRRPToEdge();
    Runtime.getRuntime().addShutdownHook(shutdown);
    start();

  }

  /**
   * This Thread keeps a socket open to the host and port, reads in any
   * information on that socket, keeps a list of unique StatusInfo, and updates
   * that list when new data for the unique key comes in.
   */
  @Override
  public void run() {
    running = true;
    byte[] buf = new byte[4096];
    MiniSeed ms = null;
    int nzero = 0;
    long start = System.currentTimeMillis();
    long now, elapse;
    int hunds;
    // loop until the ring file exists
    while (!terminate) {
      try {
        in = new RingFile(filename, ".rrp2edge");
        if (overrideBlock > 0) {
          prt(Util.clear(tmpsb).append("RRP2Edge: overriding starting block=").append(overrideBlock));
          in.overrideStartBlock(overrideBlock);
          overrideBlock = -1;
        }
        break;      // time to read ringfile
      } catch (FileNotFoundException e) {
        prta(Util.clear(tmpsb).append("RRP2Edge: file was not found- cannot start! wait=120 file=").append(filename));
      } catch (IOException e) {
        prta(Util.clear(tmpsb).append("RRP2Edge: IO error while opening ring file! wait=120 file=").append(filename));
      }
      try {
        sleep(120000);
      } catch (InterruptedException expected) {
      }
    }
    prta(Util.clear(tmpsb).append("Starting at block ").append(in.getNextout()).append(" last=").append(in.getLastSeq()));
    // top of main loops
    try {     // This try catches any RuntimeExceptions
      while (!terminate) {
        if (terminate) {
          break;
        }
        int msize = in.getNext(buf);               // Note: this blocks if no data is available

        if (terminate) {
          break;
        }
        try {
          if (buf[0] == 0 && buf[6] == 0 && buf[8] == 0 && buf[15] == 0) {
            nzero++;
            if (nzero % 1000 == 0) {
              prta(Util.clear(tmpsb).append("RRP2Edge: Got zero block ").append(nzero).
                      append(" block=").append(in.getNextout()).append(" last=").append(in.getLastSeq()));
            }
          } else {
            if (ms == null) {
              ms = new MiniSeed(buf);
            } else {
              ms.load(buf);
            }
            if (dbg || nblocks % 1000 == 2) {
              prta(Util.clear(tmpsb).append(ms.toStringBuilder(null, 80)).append(" #blks=").append(nblocks).
                      append(" idx=").append(in.getNextout()));
              now = System.currentTimeMillis();
              elapse = now - start;        // number of millis to do 1000 blocks
              if (nblocks % 1000 == 2 && elapse < 1000) {
                try {
                  sleep(1000 - elapse);
                } catch (InterruptedException expected) {
                }// Limit to 512000 bytes/sec
              }
              start = now;
            }
            if (add1024 && ms.getTimeInMillis() < System.currentTimeMillis() - 7 * 1023 * 86400000) {
              g1024.setTimeInMillis((ms.getTimeInMillis() + 1024 * 7 * 86400000L) / 1000 * 1000);
              hunds = ms.getUseconds() % 1000 / 100;    // The single digit of h usec
              prta(Util.clear(tmpsb).append("Reset time to ").append(Util.ascdate(g1024)).append(" ").
                      append(Util.asctime2(g1024)).append(" ").append(ms.toStringBuilder(null, 60)));
              ms.setTime(g1024, hunds);
            }
            Channel c = EdgeChannelServer.getChannel(ms.getSeedNameSB());
            //if(seedname.indexOf("REPV15")>= 0)  par.prta(seedname+": "+c.toString()+" flags="+c.getFlags()+" mask="+mask+" hydra="+hydra);
            if (c == null) {
              prta(Util.clear(tmpsb).append(tag).append("RRP2Edge: ***** new channel found=").append(ms.getSeedNameSB()));
              SendEvent.edgeSMEEvent("ChanNotFnd", "RRP2Edge: MiniSEED Channel not found=" + ms.getSeedNameSB() + " new?", this);
              EdgeChannelServer.createNewChannel(ms.getSeedNameSB(), ms.getRate(), this);
            }
            if (csend != null) {
              csend.send(ms.getJulian(),
                      (int) (ms.getTimeInMillis() % 86400000), // Milliseconds
                      ms.getSeedNameSB(), ms.getNsamp(), ms.getRate(), ms.getBlockSize());  // key,nsamp,rate and nbytes
            }
            try {
              IndexBlock.writeMiniSeedCheckMidnight(ms, false, false, tag, this); // Do not use hydra via this method
              if (hydra) {
                Hydra.sendNoChannelInfrastructure(ms);        // Use this for Hydra
              }
            } catch (RuntimeException e) {
              prta(Util.clear(tmpsb).append("RRP2Edge: *** RuntimeError in writeMiniSeedMidnight - ignore ms=").append(ms));
              e.printStackTrace(getPrintStream());
            }
          }
          nblocks++;
        } catch (IllegalSeednameException e) {
          prta(Util.clear(tmpsb).append("RRP2Edge: Got illegal seedname=").append(e));
        } catch (RuntimeException e) {
          prta(Util.clear(tmpsb).append("RRP2Edge: got runtime error e=").append(e));
          e.printStackTrace(getPrintStream());
        }
      }
      // The main loop has exited so the thread needs to exit
    } catch (IOException e) {
      prta(Util.clear(tmpsb).append("Got IO error trying to read an RRP file e=").append(e));
      e.printStackTrace(this.getPrintStream());
    } catch (RuntimeException e) {
      prta(Util.clear(tmpsb).append("RRP2Edge: *** RuntimeException in ").
              append(this.getClass().getName()).append(" e=").append(e.getMessage()));
      if (getPrintStream() != null) {
        e.printStackTrace(getPrintStream());
      } else {
        e.printStackTrace(getPrintStream());
      }
      if (e.getMessage() != null) {
        if (e.getMessage().contains("OutOfMemory")) {
          SendEvent.doOutOfMemory(tag, this);
          throw e;
        }
      }
      terminate();
    }
    prta(Util.clear(tmpsb).append("RRP2Edge: *** terminating =").append(terminate));
    if (in != null) {
      in.terminate();
    }
    try {
      Runtime.getRuntime().removeShutdownHook(shutdown);
    } catch (Exception expected) {
    }
    if (csend != null) {
      csend.close();
    }
    running = false;            // Let all know we are not running
    terminate = false;          // Sign that a terminate is no longer in progress
  }

  /**
   * This internal class gets registered with the Runtime shutdown system and is
   * invoked when the system is shutting down. This must cause the thread to
   * exit.
   */
  class ShutdownRRPToEdge extends Thread {

    /**
     * Default constructor does nothing; the shutdown hook starts the run()
     * thread.
     */
    public ShutdownRRPToEdge() {
      gov.usgs.anss.util.Util.prta("new ThreadShutdown " + getName() + " " + getClass().getSimpleName());
    }

    // This is called by the RunTime.shutdown at any termination 
    @Override
    public void run() {
      System.err.println(Util.asctime() + " RRP2Edge: RRPToEdge Shutdown() started...");
      terminate();          // Send terminate to main thread and cause interrupt
      int loop = 0;
      while (running) {
        // Do stuff to aid the shutdown
        try {
          sleep(1000);
        } catch (InterruptedException expected) {
        }
        loop++;
      }
      System.err.println(Util.asctime() + " RRP2Edge: Shutdown() of RRPToEdge is complete.");
    }
  }

}
