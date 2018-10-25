/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */

package gov.usgs.anss.edgemom;

/*
 * TraceBufListener.java
 *
 * Created on June 15, 2005, 12:18 PM
 *
 * To change this template, choose Tools | Options and locate the template under
 * the Source Creation and Management node. Right-click the template and choose
 * Open. You can then make changes to the template in the Source Editor.
 */

import gov.usgs.anss.edgeoutput.TraceBufQueuedListener;
import gov.usgs.anss.edge.RawToMiniSeed;
import gov.usgs.anss.edge.IndexFile;
import gov.usgs.anss.edge.EdgeProperties;
import gov.usgs.anss.edge.ChannelSender;
import gov.usgs.alarm.SendEvent;
import gov.usgs.anss.edgeoutput.Module;
import gov.usgs.anss.edgeoutput.TraceBuf;
import gov.usgs.anss.edgeoutput.Chan;

import gov.usgs.anss.edgethread.EdgeThread;
import gov.usgs.anss.util.*;
import gov.usgs.edge.config.Channel;
import java.io.IOException;
import java.net.UnknownHostException;
import java.util.Calendar;
import java.util.GregorianCalendar;
import gnu.trove.map.hash.TLongObjectHashMap;
import gnu.trove.iterator.TLongObjectIterator;


/**
 * TraceBufListener - is an EdgeThread which listens for tracebuf traffic and
 * submits it to an RawToMiniSeed for insertion into the Edge. Note that this
 * implies the trace wire is in order. This class is used to listen for UDP
 * traffic on and Earthworm Trace wire and put all of the data into the edge
 * unless this is forbidden by the ChannelConfig TraceInputDisabled flag.
 *
 * This addresses fragmented packets on the wire as well by processing each
 * packet through the module class which assembles any fragments and only
 * returns when the fragmented packet is complete.
 *
 * <br>
 * <PRE>
 *-dbg               Turn on debug logging
 *-h         nn.nn.nn     The ip address of the broadcast to listen too (192.168.8.255)
 *-p         pppp         Port to listen for UDP on
 * -maxqueue nn       The number of queued packets to buffer in the queue (def=100)
 * -qsize    nn       The maximum size of each buffered packet in bytes (default=1500)
 * -allowoor          If present, out-of-order data is permitted and are process, if false they are discarded
 * -exclude  regexp   If the channel matches the regular expression, exclude it from processing
 * -noudpchan          Do not update UdpChannel with input
 * -nohydra            Do not Send data received in this thread to Hydra
 * -dbgch    ChanString   do detailed debugging on this channel only (use _ for spaces)
 * -dbgmod             Turn on Module receiver detail debug output
 * </PRE>
 *
 * @author davidketchum
 */
public final class TraceBufListener extends EdgeThread {

  private long lastStatus;     // Time last status was received

  private final TraceBufMonitor monitor; // Watcher for hung conditions on this thread
  private long inbytes;         // Number of input bytes processed
  private long outbytes;        // Number of output bytes processed
// These are used in the TraceBufQueued Listener
  private int port;             // Trace Wire port
  private String host;
  private int maxqueue;
  private int qsize;
  private boolean allowoor;
  private boolean dbg;
  private String dbgchan;
  //private TraceBuf tb;
  private boolean hydra;
  private StringBuilder tmpsb = new StringBuilder(100);
  private String excludes;
  //private TreeMap<String, Integer> pinnos = new TreeMap<String, Integer>();

  private TraceBufQueuedListener listen;
  private ChannelSender csend;
  private TLongObjectHashMap<Chan> chans = new TLongObjectHashMap<Chan>();
  //private TreeMap<String, Chan> chans = new TreeMap<String, Chan>();
  int[] lastseq = new int[256];
  long[] npack = new long[256];
  int[] seqerr = new int[256];

  /**
   * Return number of bytes processed as input.
   *
   * @return Number of bytes processed as input.
   */
  public long getInbytes() {
    return inbytes;
  }

  /**
   * Return number of bytes processed as input.
   *
   * @return Number of bytes processed as input.
   */
  public long getOutbytes() {
    return outbytes;
  }

  /**
   * Set debug flat.
   *
   * @param t What to set debug to.
   */
  public void setDebug(boolean t) {
    dbg = t;
  }

  /**
   * Creates a new instance of TraceBufListener - This one gets its arguments
   * from a command line.
   *
   * @param argline The argument line.
   * @param tg Logging tag.
   */
  public TraceBufListener(String argline, String tg) {
    super(argline, tg);
    String[] args = argline.split("\\s");
    dbg = false;
    boolean nocsend = false;
    hydra = true;
    host = "";
    dbgchan = "          ";
    qsize = 1500;
    maxqueue = 100;
    for (int i = 0; i < 128; i++) {
      lastseq[i] = -1;
    }
    for (int i = 0; i < args.length; i++) {
      //prt(i+" arg="+args[i]);
      if (args[i].equals("-dbg")) {
        dbg = true;
      } else if (args[i].equals("-p")) {
        port = Integer.parseInt(args[i + 1]);
        i++;
      } else if (args[i].equals("-h")) {
        host = args[i + 1];
        i++;
      } else if (args[i].equals("-empty")) {
      } // Allow this for totally empty command lines
      else if (args[i].equals("-noudpchan")) {
        nocsend = true;
      } else if (args[i].equals("-nohydra")) {
        hydra = false;
      } else if (args[i].equals("-exclude")) {
        excludes = args[i + 1];
        i++;
      } else if (args[i].equals("-dbgmod")) {
        Module.setDebug(true);
      } else if (args[i].equals("-allowoor")) {
        allowoor = true;
      } else if (args[i].equals("-maxq")) {
        maxqueue = Integer.parseInt(args[i + 1]);
        i++;
      } else if (args[i].equals("-qsize")) {
        qsize = Integer.parseInt(args[i + 1]);
        i++;
      } else if (args[i].equals("-dbgch")) {
        dbgchan = args[i + 1].replaceAll("_", " ");
        i++;
      } else if (args[i].substring(0, 1).equals(">")) {
        break;
      } else {
        prt(Util.clear(tmpsb).append("TraceBufListener unknown switch=").
                append(args[i]).append(" ln=").append(argline));
      }
    }
    prt(Util.clear(tmpsb).append("TraceBufListener: new line parsed dbg=").append(dbg).
            append(" hydra=").append(hydra).append(" allowoor=").append(allowoor).
            append(" maxq=").append(maxqueue).append(" qsize=").append(qsize));
    tag = tg;
    tag += getName();  //with String and type
    monitor = new TraceBufMonitor(this);
    //tb = new TraceBuf(new byte[TraceBuf.TRACE_LENGTH*4]);
    try {
      listen = new TraceBufQueuedListener(host, port,maxqueue, qsize, this);
    } catch (UnknownHostException e) {
      prt("Could not find hosting opening TraceBufQueuedListener");

    }
    listen.setDebug(dbg);
    if (!nocsend) {
      csend = new ChannelSender("  ", "TBLis-" + port, "TBL-" + tg);
    }

    start();
  }

  @Override
  public void terminate() {
    // Set terminate do interupt.  If IO might be blocking, it should be closed here.
    terminate = true;
    interrupt();
    listen.terminate();

  }

  /**
   * This Thread keeps a socket open to the host and port, reads in any
   * information on that socket, keeps a list of unique StatusInfo, and updates
   * that list when new data for the unique key comes in.
   */
  @Override
  public void run() {
    long mask = 1 << (8 - 1);
    running = true;               // Mark we are running
    byte[] b = new byte[4100];
    //ByteBuffer bb = ByteBuffer.wrap(b);
    long lastChanStatus = System.currentTimeMillis();
    lastStatus = lastChanStatus - 570000;
    int loops = 0;
    long lastrecs = 0;
    StringBuilder seedname = new StringBuilder(12);

    boolean orgdbg = dbg;
    int nchanges = 0;
    while (true) {
      try {                     // Runtime exception catcher
        if (terminate) {
          break;
        }
        /* Keep trying until a connection is made */
        int loop = 0;
        if (terminate) {
          break;
        }
        prta(Util.clear(tmpsb).append(tag).append(" TBL: Unit is opened.  Start reads."));
        // Read data from the socket and update/create the list of records 
        while (true) {
          if (terminate) {
            break;
          }
          //try {
          int nout = listen.getNextout();
          int len = listen.dequeue(b);
          if (len > 0) {
            // Check for sequence error from the modules
            TraceBuf tb = Module.process(b, len, port, this);      // Check the module sequences, assemble tb from frags
            if (tb == null) {
              continue;        // If building a frag or certain errors, there is no tracebuf to process
            }
            Util.clear(seedname).append(tb.getSeedNameSB());
            if (seedname.indexOf(dbgchan) >= 0) {
              dbg = true;
            } else {
              dbg = orgdbg;
            }
            if (seedname.substring(7, 10).trim().length() < 3) {
              //continue;
              String chn = seedname.substring(7, 10).trim().toUpperCase();
              switch (chn) {
                case "SZ":
                  seedname.replace(7, 10, "SHZ");
                  break;
                case "BZ":
                  seedname.replace(7, 10, "BHZ");
                  break;
                case "SE":
                  seedname.replace(7, 10, "SHE");
                  break;
                case "BE":
                  seedname.replace(7, 10, "BHE");
                  break;
                case "SN":
                  seedname.replace(7, 10, "SHN");
                  break;
                case "BN":
                  seedname.replace(7, 10, "BHN");
                  break;
                case "LZ":
                  seedname.replace(7, 10, "LHZ");
                  break;
                case "LN":
                  seedname.replace(7, 10, "LHN");
                  break;
                case "LE":
                  seedname.replace(7, 10, "LHE");
                  break;
                default:
                  prta(Util.clear(tmpsb).append("Unknown channel type in TraceBufListener ").append(seedname));
                  continue;
              }
              if ((nchanges++ % 1000) == 0) {
                prta("Change " + tb.getSeedNameString() + " to " + seedname);
              }
              tb.setSeedname(seedname);
            }
            if (excludes != null) {
              if (seedname.toString().trim().matches(excludes)) {
                continue;
              }
            }
            if (dbg) {
              prta(Util.clear(tmpsb).append("TBL:  nxt=").append(nout).append(" l=").append(len).
                      append(" ").append(tb.toStringBuilder(null)));
            }
            //int [] data = tb.getData();
            Channel c = EdgeChannelServer.getChannel(seedname);
            //if(seedname.indexOf("UUSRU")>= 0)  prta(seedname+": "+c.toString()+" flags="+c.getFlags()+" mask="+mask+" hydra="+hydra);
            if (c != null) {
              if ((c.getFlags() & mask) != 0) {
                continue;      // Channel is disable from trace wire input
              }
              if (Math.abs(c.getRate() - tb.getRate()) / c.getRate() > 0.01) {
                prta(Util.clear(tmpsb).append("TBL: ***** rates mismatch ").append(seedname).
                        append(" chan=").append(c.getRate()).append(" trace=").append(tb.getRate()).
                        append(" buf rate=").append(tb.getRate()));
                SendEvent.debugSMEEvent("TBLBadRate", Util.clear(tmpsb).append("rates mismatch ").append(seedname).
                        append(" chan=").append(c.getRate()).append(" trace=").append(tb.getRate()).toString(), this);
                tb.setRate(c.getRate());
              }
            } else {
              prta(Util.clear(tmpsb).append("TBL: ***** new channel found=").append(seedname));
              SendEvent.edgeSMEEvent("ChanNotFnd", "TraceBuf Channel not found=" + seedname + " new?", this);
            }

            // The Chan process is looking for gaps and gathering statistics
            Chan ch = chans.get(Util.getHashFromSeedname(seedname));
            boolean oorSkip = false;
            if (ch == null) {
              ch = new Chan(seedname, tb, this);
              chans.put(Util.getHashFromSeedname(seedname), ch);
            } else {
              oorSkip = ch.process(tb);
              if (oorSkip) {
                prta(Util.clear(tmpsb).append("DEBUG: skip oor ").append(tb));
              }
              if (allowoor) {
                oorSkip = false;
              }
            }

            // Process the data via RawToMiniSeed, send to channel digest, etc.
            if (!oorSkip) {
              GregorianCalendar start = tb.getGregorianCalendar();
              if (dbg) {
                prta(Util.clear(tmpsb).append("TBL: addTimeseries ns=").append(tb.getNsamp()).
                        append(" ").append(seedname).append(" rate=").append(tb.getRate()).
                        append(" ").append(tb.toStringBuilder(null)));
              }
              if (dbg) {
                RawToMiniSeed.setDebugChannel(dbgchan);
              }
              RawToMiniSeed.addTimeseries(tb.getData(), tb.getNsamp(), seedname,
                      start.get(Calendar.YEAR), start.get(Calendar.DAY_OF_YEAR),
                      ((int) (start.getTimeInMillis() % 86400000) / 1000), ((int) (start.getTimeInMillis() % 1000) * 1000), tb.getRate(),
                      0, 0, 0, 0, this);
              try {
                if (csend != null) {
                  csend.send(SeedUtil.toJulian(start.get(Calendar.YEAR), start.get(Calendar.DAY_OF_YEAR)),
                          (int) (start.getTimeInMillis() % 86400000L), seedname,
                          tb.getNsamp(), tb.getRate(), tb.getNsamp() * 4);
                }
              } catch (IOException e) {
                if (e.getMessage().contains("operation interrupted")) {
                  prta(Util.clear(tmpsb).append("TBS: got Interrupted sending channel data"));
                } else {
                  prta(Util.clear(tmpsb).append("TBS: got IOException sending channel ").append(e.getMessage()));
                }
              }

              loops++;

              if (hydra) {
                Hydra.sendNoChannelInfrastructure(tb);    // Send data straight to Hydra
              }
            }
            // Time for status?
            if (loops % 200 == 0) {
              /*if(System.currentTimeMillis() - lastStatus > 60000) {
                for(int i=129; i<list.length(); i=i+130) list.replace(i,i+1,"\n");
                prta(loops+" #="+(list.length()/13)+" "+tb.toString()+"              "+
                    (list.indexOf("*") >= 0?"True":"")+"\n"+list.toString());
                list.delete(0,list.length());
                lastStatus = System.currentTimeMillis();
              }*/
              if (System.currentTimeMillis() - lastChanStatus > 600000) {
                //Iterator<Chan> itr = chans.values().iterator();
                TLongObjectIterator<Chan> itr = chans.iterator();
                while (itr.hasNext()) {
                  itr.advance();
                  prt(itr.value().toStringBuilder(null));
                }
                prta(Util.clear(tmpsb).append("TBL: # chans=").append(chans.size()).
                        append(" #pkt=").append(listen.getNpackets() - lastrecs).append(" ").append(listen));
                lastChanStatus = System.currentTimeMillis();
                lastrecs = listen.getNpackets();
                prta(Module.getStatusSB());
              }
            }
          } else {
            try {
              Thread.sleep(10);
            } catch (InterruptedException expected) {
            }
          }
          inbytes += 1;   // Count the bytes

          // Is it time for status yet
          if (System.currentTimeMillis() - lastStatus > 600000) {
            prta(Util.clear(tmpsb).append(tag).append(" Status Message").append(getStatusString()));
            lastStatus = System.currentTimeMillis();
          }
        }     // while(true) Get data

      } catch (RuntimeException e) {
        prta(Util.clear(tmpsb).append(tag).append(" RuntimeException in ").append(this.getClass().getName()).append(" e=").append(e.getMessage()));
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
    monitor.terminate();
    prt(tag + " is terminated.");
    if (csend != null) {
      csend.close();
    }
    running = false;
    terminate = false;
  }
  /**
   * return the monitor string for Icinga.
   *
   * @return A String representing the monitor key value pairs for this
   * EdgeThread.
   */
  long lastMonInbytes;

  @Override
  public StringBuilder getMonitorString() {
    Util.clear(monitorsb);
    long nb = inbytes - lastMonInbytes;
    lastMonInbytes = inbytes;
    return monitorsb.append("TraceBufListenerInbytes=").append(nb).append("\n");
  }

  @Override
  public StringBuilder getStatusString() {
    Util.clear(statussb);
    statussb.append("TBL: ").append(host).append("/").append(port).append(" in=").append(inbytes).append(" b\n");
    for (int i = 0; i < 127; i++) {
      if (npack[i] != 0) {
        statussb.append("  mod=").append(i).
                append(" #p=").append(npack[i]).append(" #seqerr=").append(seqerr[i]).append("\n");
      }
    }
    return statussb;
  }

  @Override
  public StringBuilder getConsoleOutput() {
    return consolesb;
  } //  We use prt directly

  /**
   * Monitor the TraceBufListener and stop it if it does not receive heartBeats
   * or data!
   */
  final class TraceBufMonitor extends Thread {

    boolean terminate;        // If true, this thread needs to exit
    int msWait;               // User specified period between checks
    boolean interruptDone;    // Flag that we have gone off
    TraceBufListener thr;      // The thread being monitored
    long lastInbytes;         // Count of last to look for stalls

    public boolean didInterrupt() {
      return interruptDone;
    }

    public void terminate() {
      terminate = true;
      interrupt();
    }

    public TraceBufMonitor(TraceBufListener t) {
      thr = t;
      msWait = 360000;      // Set the ms between checks
      gov.usgs.anss.util.Util.prta("new ThreadShutdown " + getName() + " " + getClass().getSimpleName() + " t=" + t);
      start();
    }

    @Override
    public void run() {
      long lastinbytes = inbytes;
      //try{sleep(msWait);} catch(InterruptedException e) {}
      while (!terminate) {
        try {
          sleep(msWait);
        } catch (InterruptedException expected) {
        }
        //prta(tag+" LCM: HB="+heartBeat+" lHB="+lastHeartbeat+" in ="+inbytes+" lin="+lastInbytes);
        if (inbytes == lastinbytes) {
          thr.interrupt();      // Interrupt in case its in a wait
          interruptDone = true;     // So interrupter can know it was us!
          prta(tag + " monitor has gone off ");
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
   * @param args the command line arguments
   */
  public static void main(String[] args) {

    IndexFile.init();
    EdgeProperties.init();
    TraceBufListener[] liss = new TraceBufListener[10];
    IndexFile.setDebug(true);

  }

}
