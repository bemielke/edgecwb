/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.edgemom;

import gov.usgs.anss.edge.EdgeProperties;
import gov.usgs.alarm.SendEvent;
import gov.usgs.anss.edge.*;
import gov.usgs.anss.edgeoutput.RRPRingFileInputer;
import gov.usgs.anss.edgeoutput.ChannelHolder;
import gov.usgs.anss.edgeoutput.RRPRingFile;
import gov.usgs.anss.edgeoutput.RRPStateFile;
import static gov.usgs.anss.edgeoutput.RRPRingFileInputer.MAX_SEQ;
import gov.usgs.anss.edgeoutput.RingFile;
import gov.usgs.anss.edgethread.EdgeThread;
import gov.usgs.anss.seed.MiniSeed;
import gov.usgs.anss.util.*;
import java.io.*;
import java.net.*;
import java.nio.*;

/**
 * RRPClient - is an EdgeThread which Opens a RingFileInputer to a RRPServer.
 * Optionally the data can be delayed. The RingFileInputer uses the memory queue
 * of recent blocks to see if the next needed sequence is in the memory and
 * sends that if available rather than reading the actual ring file. This is to
 * reduce I/O especially for RRPClients that are part of the RTS2/Slate2 project
 * to run RRP in the field.
 *
 * <PRE>
 * switch  data   Description
 * -ring  filename The filename of the ring to be forwarded via the RRP protocol
 * -ip    ip.adr    The IP address of the RRPServer
 * -p     nnnn      The port on the RRPServer which accepts sockets from this RRPClient
 * -l     kbps      Limit transfer rate for 10 packets to this kbps (def=25 kbps)
 * -delay  ssss     Number of seconds to delay data
 * -ext    ASCII    The extension to add to the filename.last to make the status file for this instance
 * -state  statelen THe number of blocks to use for the state file - defaults to 100
 * DEBUG SWITCHES:
 * -force           Force errors on the link for debugging
 * -hdrdump         Turn on logging of headers
 * -dbgbin          Turn on debugging of binary data
 * -dbgch seedname  A debug channel name
 * -alert nnnn      Send an alarm event if behind by more than this number of packets
 * -forceninit      Start this sender with the next packet - do not send the entire missing data.
 *
 * </PRE>
 *
 *
 * @author davidketchum
 */
public final class RRPClient extends EdgeThread {

  // local object attributes
  private String filename;
  private RRPRingFileInputer in;

  // File and buffers for keeping track of last output
  //RawDisk gotlast;            // Our place to record last seqence acked
  //private final byte [] lastbuf;
  //private final ByteBuffer lastbb;
  private int lastAckSeq;
  private int forceAckSeq = -1;

  // gap filling data
  private int gapStart = -1;
  private int gapEnd = -1;

  // Structure related to socket for sending blocks and reading acks 
  private AckReader ackReader;
  private String ipadr;               // Remote end ip
  private int port;                   // Remote end port
  private String bind;                // Local end IP address
  private Socket s;                   // Socket to remote end
  private OutputStream sockout;
  // General EdgeMomThread attributes
  private long lastStatus;     // Time last status was received
  private int behindAlertLimit;
  private boolean forceInit;
  private RRPCMonitor monitor; // Watcher for hung conditions on this thread
  private long inbytes;         // number of input bytes processed
  private long outbytes;        // Number of output bytes processed
  private RFTCIShutdown shutdown;
  private int delayMS;          // if non-zero, amount of time to delay all data
  private int throttleRate = 50000;       // def=25 kbps.  MS to wait between 10* 520 byte transfers kbps = 41600/throttlems
  private long lastInbytes;     // store value of inBytes every throttle cycle
  private long lastThrottle = System.currentTimeMillis();
  private long lastClose;
  private int stateModulus = 1000;

  boolean dbg;
  boolean dbgbin;               // if true, modify MiniSeed to include sequence in each
  boolean forceErrors;
  boolean hdrdump;
  //String logname="rrpc";
  private RRPRingFile ringfile;
  private RRPStateFile outStateFile, inStateFile;
  private int stateLength;
  // data used by run() and the doGap routine
  private final byte[] hdr = new byte[8];            // header buffer
  private final byte[] buf4096 = new byte[4096];    // mini seed block
  private final ByteBuffer hdrbb;                     // Byte buffer to MiniSeed block
  private final ByteBuffer bbout;                     // Byte buffer to MiniSeed block
  private int force = 0;
  private int npackets;
  private final StringBuilder runsb = new StringBuilder(100);
  // debug variables
  int state;

  @Override
  public String toString() {
    return tag + " RRPC: inb=" + inbytes + " outb=" + outbytes + " file=" + filename + " state=" + state;
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
   * creates an new instance of RRPClient - This one gets its arguments from a
   * command line
   *
   * @param argline The argument line to use to start this client
   * @param tg The tag to use for logging
   * @param ringfile The RingFile which spawned this RRPClient, used to get
   * current data from memory to save disk reads
   * @throws java.io.IOException
   */
  public RRPClient(String argline, String tg, RRPRingFile ringfile) throws IOException {
    super(argline, tg);
    //Util.setTestStream("rrpc.log"+EdgeThread.EdgeThreadDigit());
    //edgeDigit = EdgeThread.EdgeThreadDigit();
    prt(tag + "args=" + argline);
    String[] args = argline.split("\\s");
    dbg = false;
    dbgbin = false;
    hdrdump = false;
    forceInit = false;
    this.ringfile = ringfile;
    tag = tg + "-" + getName().substring(getName().indexOf("-")) + ":";
    String ext = "";
    int dummy = 0;
    stateLength = 100;
    for (int i = 0; i < args.length; i++) {
      //prt(i+" arg="+args[i]);
      if (args[i].length() == 0) {
        continue;
      }
      if (args[i].equals("-dbg")) {
        dbg = true;
      } else if (args[i].equals("-rrp")) {
        stateModulus = 20;             // Cause logging every 20 packets 
      } else if (args[i].equals("-empty")) {
        dummy = 1;         // supprss warningAllow this for totally empty command lines
      } else if (args[i].equals("-ring")) {
        filename = args[i + 1];
        i++;
      } else if (args[i].equals("-dbgch")) {
        prta(tag + "CH: dbg=" + args[i + 1]);
        ChannelHolder.setDebugSeedname(args[i + 1]);
        i++;
      } else if (args[i].equals("-dbgbin")) {
        dbgbin = true;
      } else if (args[i].equals("-ip")) {
        ipadr = args[i + 1];
        i++;
      } else if (args[i].equals("-port")) {
        port = Integer.parseInt(args[i + 1]);
        i++;
      } else if (args[i].equals("-forceseq")) {
        forceAckSeq = Integer.parseInt(args[i + 1]);
        i++;
        prta(" ******* Force seq set to " + forceAckSeq);
      } else if (args[i].equals("-force")) {
        forceErrors = true;
      } else if (args[i].equals("-hdrdump")) {
        hdrdump = true;
      } else if (args[i].equals("-forceinit")) {
        forceInit = true;
      } else if (args[i].equals("-ext")) {
        ext = args[i + 1];
        i++;
      } else if (args[i].equals("-bind")) {
        bind = args[i + 1];
        i++;
      } else if (args[i].equals("-state")) {
        stateLength = Integer.parseInt(args[i + 1]);
        i++;
      } else if (args[i].equals("-delay")) {
        delayMS = Integer.parseInt(args[i + 1]) * 1000;
        i++;
      } else if (args[i].equals("-alert")) {
        behindAlertLimit = Integer.parseInt(args[i + 1]);
        i++;
      } else if (args[i].equals("-l")) {
        throttleRate = 1000 * Integer.parseInt(args[i + 1]);
        i++;
      }// in kbps
      else if (args[i].equals("-i") || args[i].equals("-instance")) {
      } else if (args[i].substring(0, 1).equals(">")) {
        break;
      } 
      else if (args[i].equalsIgnoreCase("-f") ||args[i].equalsIgnoreCase("-p") || args[i].equalsIgnoreCase("-u") ||
              args[i].equalsIgnoreCase("-s") || args[i].equalsIgnoreCase("-m") || args[i].equalsIgnoreCase("-dm") ||
              args[i].equalsIgnoreCase("-allowrestricted")) {
        i++;
      }
      else if (args[i].equalsIgnoreCase("-args")) {
        
      }
      else if (i != 0) {
        prt("RRPClient unknown switch[" + i + "]=" + args[i] + " ln=" + argline);
      }
    }

    if(stateModulus < 2) stateModulus = 2;      // modulus == 1 causes output, so 2 is the minimum modulus
    prt(Util.clear(runsb).append(tag).append(" RRPClient: new line parsed dbg=").append(dbg).
            append(" ring=").append(filename).append(" ext=").append(ext).
            append(" ip=").append(ipadr).append("/").append(port).
            append(" forceInit=").append(forceInit).append(" bind=").append(bind).
            append(" thrRate=").append(throttleRate).append(" mod=").append(stateModulus));
    if (ringfile != null) {
      /*if (ringfile.isNew()) {
        prt(Util.clear(runsb).append(tag).append(" Set ForceInit on as this file is new"));
        forceInit = true;
      }*/
      filename = ringfile.getFilename();

    }          // its a new file, so any sequences from RRPServer are BS */
    tag += "RRPC:";  //with String and type
    // Open the RingFileInputer.
    int next = 0;
    //lastbuf = new byte[4];
    //lastbb = ByteBuffer.wrap(lastbuf);

    // Figure out where we last recorded our position in the file 
    while (outStateFile == null) {
      try {
        outStateFile = new RRPStateFile(filename.trim() + ".rrpc" + ext, stateLength, this);
        outStateFile.setDebug(dbg);
      } catch (IOException e) {
        prta(tag + "Could not open state file e=" + e);
        SendEvent.edgeSMEEvent("RRPC_NoRing", "RRPClient " + filename + ".rrpc" + ext + " ring state file err=" + e, (RRPClient) this);
        try {
          sleep(30000);
        } catch (InterruptedException e2) {
        }
      }
    }
    lastAckSeq = outStateFile.getLastAck();
    int lastseq = outStateFile.getLastIndex();

    // Open the ring file for input
    try {
      in = new RRPRingFileInputer(filename, 0, this.ringfile, this);
      //in = new RingFileInputer(filename, lastAckSeq);
      /*if(lastAckSeq >= RingFile.MAX_SEQ) lastAckSeq -= RingFile.MAX_SEQ;
      inStateFile = new RRPStateFile(filename.trim()+".rf", stateLength, this);
      inStateFile.setDebug(dbg);
      prta(tag+"RingFile: "+filename+" inputState="+inStateFile+" lastAckSeq="+lastAckSeq);*/
    } catch (IOException e) {
      prta(tag + " File not found=" + filename);
      SendEvent.edgeSMEEvent("RRPC_NoRing", "RRPClient " + filename + " ring file not found", (RRPClient) this);
      throw e;
    }
    hdrbb = ByteBuffer.wrap(hdr);
    bbout = ByteBuffer.wrap(buf4096);
    if (forceInit) {
      lastAckSeq = outStateFile.getLastIndex();
    }
    if (hdrdump) {
      //prta(tag+"RingFile: "+in+" .last lastAckSeq="+lastAckSeq);
      return;
    }

    monitor = new RRPCMonitor(this);
    shutdown = new RFTCIShutdown();
    Runtime.getRuntime().addShutdownHook(shutdown);

    start();
  }

  @Override
  public void terminate() {
    // Set terminate do interrupt.  If IO might be blocking, it should be closed here.
    terminate = true;
    interrupt();
    //in.close();
    close();
  }

  public void close() {
    prta(tag + "close() isClosed=" + (s == null ? "Null" : s.isClosed()) + " s=" + s + " Called from " + Util.getCallerLine());
    if (s != null) {
      if (!s.isClosed()) {
        try {
          s.close();
          lastClose = System.currentTimeMillis();
        } catch (IOException expected) {
        }
      }
    }
  }

  /**
   * If there is a open gap, write out one MiniSeed block in that Gap
   *
   * @return True if a block was written, false if not
   * @throws IOException If one is thrown trying to read the file.
   */
  private boolean doGap() throws IOException {
    if (gapStart < 0) {
      return false;
    }
    int len = in.getGapBlock(gapStart + 1, buf4096);    // Get the data from the file
    if (dbg) {
      try {
        if (gapms == null) {
          gapms = new MiniSeed(buf4096, 0, 512);
        } else {
          gapms.load(buf4096, 0, 512);
        }
      } catch (IllegalSeednameException e) {
        prta(Util.clear(runsb).append(tag).append(tag).append(" Gap illegal seedname=").append(e));
      }
      prta(Util.clear(runsb).append(tag).append(tag).append(" Gap read for gapStart=").
              append(gapStart).append("-").append(gapEnd).append(" ms=").append(gapms.toStringBuilder(null, 60)));
    }
    if (len > 0) {
      if (buf4096[0] != 0 && buf4096[8] != 0) {
        writeBlock(buf4096, -gapStart, len);
      } else {
        prta(Util.clear(runsb).append(tag).append(tag).append(" ** Gap seq ").append(gapStart).append(" skipped.  Its a zero block"));
      }
    } else {
      prta(Util.clear(runsb).append(tag).append(" ** Gap seq ").append(gapStart).
              append(" is not in the active sequences for file ").append(in));
      len = 512;
    }
    gapStart += len / 512;
    if (gapStart >= MAX_SEQ) {
      prta(Util.clear(runsb).append(tag).append("Gap sequence roll over to zero ").append(gapStart).
              append(" file=").append(filename));
      gapStart -= MAX_SEQ;
    }
    if (gapStart == gapEnd) {
      prta(Util.clear(runsb).append(tag).append("Gap fullfilled gapStart=").append(gapStart).
              append(" gapEnd=").append(gapEnd));
      gapStart = -1;
      gapEnd = -1;
    }
    return true;
  }
  private MiniSeed gapms;

  @Override
  public void run() {
    running = true;               // mark we are running
    hdrbb.position(0);
    hdrbb.putShort((short) 0xa1b2);

    // Open the corresponding Channel infrastructure.
    int seq;
    long lastNbytes = 0;
    lastStatus = System.currentTimeMillis();
    int len;
    //int julian2000 = SeedUtil.toJulian(2000, 1, 1);
    int timesBehind = 0;

    // Open the connection, this insures the next packet exchange has happend
    prta(Util.clear(runsb).append(tag).append(" Run() has started - open connection"));
    while (s == null) {
      //try {sleep(3600000);} catch(InterruptedException expected) {}    // DEBUG
      openConnection();
      prta(Util.clear(runsb).append(tag).append(" s is null OpenConnection completed"));
    }
    while (true) {

      try {     // catch runtime exceptions
        if (terminate) {
          break;
        }
        state = 1;
        if (s.isClosed() || !s.isConnected()) {
          openConnection();
          prta(Util.clear(runsb).append(tag).append(" s is closed/not connected OpenConnection completed"));
        }
        state = 2;
        try {
          // If no current data to get, do gap blocks
          int loop = 0;
          while (in.wouldBlock()) {
            if (!doGap()) {
              try {
                sleep(100);
              } catch (InterruptedException expected) {
              }
              loop++;
              /*if(loop % 30 == 0) {
                prta(Util.clear(runsb).append(tag).append(" in would block loop=").append(loop).
                    append(" ").append(in.wouldBlock()));
              }*/
            }
            if (s.isClosed() || !s.isConnected()) {
              break;
            }
            if (terminate) {
              break;
            }
          }
          state = 21;
          if(dbg)
            prta(Util.clear(runsb).append(tag).append(" exit would block loop=").append(loop).
                  append(" ").append(in.wouldBlock()));
          if (s.isClosed() || !s.isConnected()) {
            continue;
          }
          if (terminate) {
            break;
          }
          seq = in.getNextout();                 // What sequence would we get
          if (dbg) {
            prt(Util.clear(runsb).append(tag).append(" Looking for seq=").append(seq));
          }
          len = -1;
          state = 22;
          while (len == -1) {
            len = in.getNextData(buf4096, ringfile);  // Try to read a block
            if (terminate) {
              break;
            }
            if (len < 0) {
              try {
                sleep(100);
              } catch (InterruptedException expected) {
              }// No data, so wait a little
            }
          }
          state = 23;
          if (terminate) {
            break;
          }
          if (dbg) {
            try {
              if (gapms == null) {
                gapms = new MiniSeed(buf4096, 0, 512);
              } else {
                gapms.load(buf4096, 0, 512);
              }
            } catch (IllegalSeednameException e) {
              prta(Util.clear(runsb).append(tag).append(" Read seq=").append(seq).append(" Illegal ").append(e));
            }
            long latency = System.currentTimeMillis() - gapms.getNextExpectedTimeInMillis();
            prta(Util.clear(runsb).append(tag).append(" Read seq=").append(seq).
                    append(" ms=").append(gapms != null ? gapms.toStringBuilder(null, 60) : "").
                    append(" lat=").append(latency));
          }
          if (terminate || len == -2) {
            break; // File is closed
          }
          if (len != 512) {
            prta(Util.clear(runsb).append(tag).append(" **** got non-512 length=").append(len));
          }
          //if(len == 512 || len == 0) {System.arraycopy(buf4096, 0, buf512, 0, 512); buf=buf512;}
          //else buf = buf4096;
          state = 3;
          if (buf4096[0] != 0 && buf4096[20] != 0) {
            writeBlock(buf4096, seq, len);
          } else {
            prta(Util.clear(runsb).append(tag).append(" Read zero packet - not sent acking seq=").append(seq).append(" via null"));
            if(seq == 1) {  // We must be starting up and nothing is in the ring file yet, just wait
              lastAckSeq=seq;
              in.setNextout(seq);
              prta(Util.clear(runsb).append(tag).append(" Read zero on first sequence - assume file is new and wait for some data seq=").append(seq));
              try{sleep(30000);} catch(InterruptedException expected) {
              }
            }
            lastAckSeq = seq;
            ackReader.sendNull();               // Tell the other end we are skipping a packet
          }
          outStateFile.updateSequence(seq, lastAckSeq, false);    // Update sequence in outState, do not write out
          if (terminate) {
            break;
          }

          // timeout on no acks coming in
          if (System.currentTimeMillis() - ackReader.lastAckTime() > 300000) {
            prta(Util.clear(runsb).append(tag).append(" 300 seconds without an ack -close  and continue"));
            close();
            continue;
          }

          // Is it time for status yet
          if (System.currentTimeMillis() - lastStatus > 600000) {
            int nbehind = outStateFile.getLastIndex() - in.getNextout();
            prta(Util.clear(runsb).append(tag).append("#pkt=").append(npackets).
                    append("  nKB=").append((inbytes - lastNbytes) / 1000).
                    append(" kbps=").append(Util.df21((inbytes - lastNbytes) * 8. / 600000.)).
                    append(" diff=").append(nbehind).append(" Ring:").append(in));
            if (in.getLastSeq() - in.getNextout() > behindAlertLimit && behindAlertLimit > 0) {
              SendEvent.edgeSMEEvent("RRPBehind", filename + " behind " + ((nbehind + 500) / 1000) 
                      + " Kpkt /" + behindAlertLimit, this);

              /*if(in.getLastSeq() - in.getNextout() > 1000) {
                timesBehind++;
                if(timesBehind > 2) {
                  prta(" **** behind more than 1000 packets for more than 20 minutes. gap="+in.getNextout()+"-"+in.getLastSeq()+" Remake the connection...");
                  SendEvent.edgeSMEEvent("RRPBehindTO", filename+" behind "+(in.getLastSeq() - in.getNextout() + 500)/1000+" Kpkt", this);
                  s.close();
                }
              }*/
            } else {
              timesBehind = 0;
            }
            outStateFile.updateSequence(seq, lastAckSeq, true);  // update sequences on disk at least every 10 minutes
            lastStatus = System.currentTimeMillis();
            lastNbytes = inbytes;
            Util.loadProperties("edge.prop");
          }
        } catch (IOException e) {
          prta(Util.clear(runsb).append(tag).append(" IOException reading from ringfile =").append(e.getMessage()));
          e.printStackTrace(getPrintStream());
          if (s != null) {
            if (!s.isClosed()) {
              try {
                s.close();
              } catch (IOException e2) {
              }
            }
          }
        }
      } catch (RuntimeException e) {
        prta(Util.clear(runsb).append(tag).append(" RuntimeException in ringtochan ").
                append(this.getClass().getName()).append(" e=").append(e.getMessage()));
        if (getPrintStream() != null) {
          e.printStackTrace(getPrintStream());
        } else {
          e.printStackTrace(getPrintStream());
        }
        if (e.getMessage() != null) {
          if (e.getMessage().contains("OutOfMemory")) {
            SendEvent.edgeSMEEvent("OutOfMemory", "RRPClient out of memory", this);
            /*SimpleSMTPThread.email(Util.getProperty("emailTo"),
                "Out of Memory in "+this.getClass().getName()+" on "+IndexFile.getNode(),
                Util.asctime()+" "+Util.ascdate()+" Body");*/
          }
        }
      }
    }       // while(true) open on ring file
    //monitor.terminate();
    if (outStateFile != null) {
      outStateFile.close();  // Note this writes out the latest sequences
    }
    if (in != null) {
      in.close();
    }
    Runtime.getRuntime().removeShutdownHook(shutdown);
    prta(tag + " is terminated exiting!");
    running = false;
    terminate = false;
  }

  /**
   *
   * @param buf4096 A raw buffer with a miniseed packet
   * @param seq The sequence number to attach to this packet
   * @param len Length of the MiniSeed packet
   * @return True if block is written, if false, it is not written because the
   * time delay has not been reached or IO error occurred
   */
  private boolean writeBlock(byte[] buf4096, int seq, int len) {
    if (delayMS > 0) {
      state = 4;
      try {
        long millis = MiniSeed.crackTimeInMillis(buf4096);
        if (millis - System.currentTimeMillis() > 600000) {
          return false; // skip data from the future!
        }
        int loop = 0;
        while (System.currentTimeMillis() - millis < delayMS) {
          loop++;
          if ((loop % 100) == 10 || (npackets % 1000) == 1) {
            prta(Util.clear(runsb).append(tag).append("Waiting for delay now=").append(System.currentTimeMillis()).
                    append(" til ").append(millis).append(" ").append(System.currentTimeMillis() - millis).
                    append(" ").append(Util.ascdatetime2(millis)));
          }
          try {
            sleep(1000);
          } catch (InterruptedException e) {
          }
        }
      } catch (IllegalSeednameException e) {
        prta(Util.clear(runsb).append(tag).append("Got illegal miniseed block ").append(MiniSeed.toStringRaw(buf4096)));
        e.printStackTrace(getPrintStream());
      }
    }
    state = 6;

    // Send data in buffer to server, make sure its open and sending
    boolean sent = false;
    if (terminate) {
      return false;
    }
    while (!sent) {
      if (s.isClosed()) {
        break;
      }

      state = 7;
      // Send the data out breaking it into 512 byte blocks.
      for (int off = 0; off < len; off += 512) {
        short chksum = 0;
        hdrbb.position(4);      // position the sequence and stuff it
        hdrbb.putInt(seq);
        for (int i = 0; i < 4; i++) {
          chksum += hdr[i + 4];    // chksum of the sequence
        }
        // If we are in binary debug mode, put sequence in first part of sequence in MiniSeed
        if (dbgbin) {
          bbout.position(0);
          if (forceErrors && seq % 10000 == 6000) {
            if (force % 2 == 1) {
              bbout.putInt(seq + 1);
            }
            force++;
          } else {
            bbout.putInt(seq);
          }
        }
        for (int i = 0; i < 512; i++) {
          chksum += buf4096[i + off]; // chksum of the payload
        }
        hdrbb.position(2);
        if (forceErrors && seq % 10000 == 2000) {
          if (force % 2 == 0) {
            chksum++;
          }
          force++;
        }
        hdrbb.putShort(chksum);
        try {
          state = 9;
          sockout.write(hdr, 0, 8);
          state = 10;

          // sleep to limit overall rate every 2k bytes or so to throttleRate
          if (throttleRate > 100 && inbytes - lastInbytes >= 512) {    // If throttle rate == 0, no throttle
            long now = System.currentTimeMillis();
            int l = (int) ((inbytes - lastInbytes) * 8000 / throttleRate);    // Milliseconds it should take to send these bytes
            if (l > (now - lastThrottle)) {
              //prta(Util.clear(runsb).append(tag).append(" Wait ").append(l-now + lastThrottle).
              //        append(" ms throttle=").append(throttleRate).append(" #b=").append(inbytes - lastInbytes));
              try {
                sleep(l - now + lastThrottle);
              } catch (InterruptedException expected) {
              }
            }
            lastInbytes = inbytes;
            lastThrottle = System.currentTimeMillis();
          }
          sockout.write(buf4096, off, 512);
          state = 11;
          npackets++;
          inbytes += 520;
          if (dbg || npackets % stateModulus == 1) {
            prta(Util.clear(runsb).append(tag).append("write ").append(seq).append(" ").
                    append(in.getLastSeq()).append(" off=").append(off).append(" l=").append(len).
                    append(" #pkt=").append(npackets).append(" nb=").append(inbytes).append(" ").
                    append(MiniSeed.toStringRaw(buf4096)));
          }
        } catch (IOException e) {
          Util.SocketIOErrorPrint(e, tag + " RRPC: writing to socket ", getPrintStream());
          prta(Util.clear(runsb).append(tag).append("Closing socket to start again."));
          close();
          break;          // bail out of this loop, let connection open and seq exchange start again
        }
      }             // for each offset of 512 in the MiniSeed block
      state = 8;
      sent = true;
    }           // while not sent 
    return sent;
  }

  private void openConnection() {
    long lastEmail = 0;
    if (s == null || s.isClosed() || !s.isConnected()) {
      int loop = 0;
      int isleep = 7500;
      int nbad = 0;
      while (true) {
        if (terminate) {
          break;
        }
        try {
          prta(Util.clear(runsb).append(tag).append("OC: Open Port=").append(ipadr).append("/").append(port).
                  append(" bind=").append(bind).append(" closed=").append(s != null ? "" + s.isClosed() : "Null").
                  append(" s=").append(s));
          InetSocketAddress adr = new InetSocketAddress(ipadr, port);
          prta(Util.clear(runsb).append(tag).append("OC: call close"));
          if(s != null) {
            close();
          }
          long elapse = System.currentTimeMillis() - lastClose;
          if (elapse < 10000) {
            try {
              prta(Util.clear(runsb).append(tag).append(" open wait minimum 10 seconds ").append(10000 - elapse));
              sleep(10000 - elapse);
            } catch (InterruptedException expected) {
            }
          }
          prta(Util.clear(runsb).append(tag).append("OC: New Socket bind=").append(bind).
                  append(" adr=").append(adr));
          s = new Socket();
          if (bind != null) {
            if (!bind.equals("")) {
              s.bind(new InetSocketAddress(bind, 0));  // User specified a bind address so bind it to this local ip/ephemeral 
            }
          }
          s.setSendBufferSize(1024);
          s.setReceiveBufferSize(512);
          s.connect(adr);
          //s = new Socket(ipadr, port);
          prta(Util.clear(runsb).append(tag).append("OC: new socket is ").append(s).
                  append(" sndsiz=").append(s.getSendBufferSize()).
                  append(" rcvsize=").append(s.getReceiveBufferSize()));

          sockout = s.getOutputStream();
          if (ackReader == null) {
            ackReader = new AckReader();
          } else {
            ackReader.resetReadOne();
          }
          prta(Util.clear(runsb).append(tag).append("OC: Wait for response from server closed=").append(s.isClosed()).
                  append(" connectd=").append(s.isConnected()));
          loop = 0;
          while (!ackReader.getReadOne()) {
            if (terminate || s.isClosed()) {
              break;
            }
            try {
              sleep(100);
              loop++;
              if (loop > 1200) {
                prta(Util.clear(runsb).append(tag).append("OC: No response from server for 120 seconds.  Close and reopen"));
                s.close();
                break;
              }
            } catch (InterruptedException expected) {
            }
          }
          if (s.isClosed()) {
            prta(Util.clear(runsb).append("Socket is closed after return from getReadOne()"));
            continue;      // No open socket, try again
          }
          prta(Util.clear(runsb).append(tag).append("OC: Server responded lastAckSeq=").append(lastAckSeq));
          break;
        } catch (UnknownHostException e) {
          prt(Util.clear(runsb).append(tag).append("OC: Host is unknown=").append(ipadr).append("/").append(port).
                  append(" loop=").append(loop));
          if (loop % 120 == 0) {
            SendEvent.edgeSMEEvent("RRPC_HostBad", "RRPClient host unknown=" + ipadr, this);
            //SimpleSMTPThread.email(Util.getProperty("emailTo"),"RRPClient host unknown="+ipadr,
            //  "This message comes from the RRPClient when the host computer is unknown,\nIs DNS up?\n");

          }
          loop++;
          try {
            sleep(30000L);
          } catch (InterruptedException expected) {
          }
        } catch (IOException e) {
          String msg = e.getMessage();
          if (msg != null) {
            if (msg.equalsIgnoreCase("Connection refused")) {
              isleep = isleep * 2;
              if (isleep >= 120000) {
                isleep = 120000;      // limit wait to 2 minutes
              }
              prta(Util.clear(runsb).append(tag).append("OC: Connection refused.  wait ").
                      append(isleep / 1000).append(" secs ...."));
              if (isleep >= 360000 && System.currentTimeMillis() - lastEmail > 3600000) {
                SendEvent.edgeSMEEvent("RRPC_Refused", "RRPClient repeatedly refused=" + ipadr, this);
                /*SimpleSMTPThread.email(Util.getProperty("emailTo"),tag+" RPP "+ipadr+"/"+port+" repeatedly refused",
                    Util.ascdate()+" "+Util.asctime()+" from "+filename+"\n"+
                      "This message comes from the RPPClient when a connection is repeatedly refused,\n"+
                      "Is remote server up?  This message will repeat once per hour.\n");*/
                lastEmail = System.currentTimeMillis();
              }
              try {
                sleep(isleep);
              } catch (InterruptedException expected) {
              }
            } else if (msg.equalsIgnoreCase("Connection timed out")) {
              prta(Util.clear(runsb).append(tag).append("OC: Connection timed out.  wait ").
                      append(isleep / 1000).append(" secs ...."));
              if (isleep >= 120000 && System.currentTimeMillis() - lastEmail > 3600000) {
                SendEvent.edgeSMEEvent("RRPC_HostBad", "RRPClient timed out =" + ipadr + "/" + port + " " + filename, this);

                /*SimpleSMTPThread.email(Util.getProperty("emailTo"),tag+" RPP "+ipadr+"/"+port+" repeatedly timed out",
                    Util.ascdate()+" "+Util.asctime()+" from "+filename+"\n"+
                      "This message comes from the RPPClient when a connection is repeatedly timing out,\n"+
                      "Is remote server up?  This message will repeat once per hour.\n");*/
                lastEmail = System.currentTimeMillis();
              }
            }
            try {
              sleep(isleep);
            } catch (InterruptedException expected) {
            }
            isleep = isleep * 2;
            if (isleep >= 120000) {
              isleep = 120000;
            }
          } else {
            prta(Util.clear(runsb).append(tag).append("OC: connection nbad=").append(nbad).
                    append(" e=").append(e));
            e.printStackTrace(getPrintStream());
            Util.IOErrorPrint(e, tag + "OC: IO error opening socket=" + ipadr + "/" + port);
            try {
              sleep(120000L);
            } catch (InterruptedException expected) {
            }
            if (nbad++ % 10 == 9) {
              SendEvent.edgeSMEEvent("RRPCBadConn", "Bad connection repeatedly to " + ipadr + "/" + port, "RRPClient");
              //Util.exit(1);
            }
          }
        } catch (RuntimeException e) {
          e.printStackTrace(getPrintStream());
        }
      }   // While True on opening the socket
      //if(dbg) 
      prta(tag + "OC: OpenConnection() is completed.");
    }     // if s is null or closed  
  }

  /**
   * return the monitor string for Nagios
   *
   * @return A String representing the monitor key value pairs for this
   * EdgeThread
   */
  @Override
  public StringBuilder getMonitorString() {
    Util.clear(monitorsb).append(tag).append(" RRPC: inb=").append(inbytes).
            append(" outb=").append(outbytes).
            append(" file=").append(filename).
            append(" state=").append(state);
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

  private final class AckReader extends Thread {

    boolean readOne;
    long lastAckTime;
    StringBuilder acksb = new StringBuilder(100);

    public AckReader() {
      readOne = false;
      lastAckTime = System.currentTimeMillis();
      start();
    }

    public void resetReadOne() {
      readOne = false;
    }

    public boolean getReadOne() {
      return readOne;
    }

    public long lastAckTime() {
      return lastAckTime;
    }

    private void sendNull() {
      byte[] b = new byte[12];
      ByteBuffer bb = ByteBuffer.wrap(b);
      bb.position(4);
      bb.putInt(-1);
      bb.putInt(lastAckSeq);
      short chksum = 0;
      for (int i = 0; i < 8; i++) {
        chksum += b[4 + i];    // csum on seq and lastAckSeq only
      }
      bb.position(0);
      bb.putShort((short) 0xa1b2);
      bb.putShort(chksum);
      try {
        s.getOutputStream().write(b, 0, 12);
      } catch (IOException e) {
        prta(tag + "ACKR: IOError writing null.  Close socket.");
      }
      prta(tag + "ACKR:  Send Null for ack=" + lastAckSeq);
    }

    private void sendSetupOK() {
      byte[] b = new byte[12];
      ByteBuffer bb = ByteBuffer.wrap(b);
      bb.position(4);
      bb.putInt(Integer.MIN_VALUE);
      bb.putInt(lastAckSeq);
      short chksum = 0;
      for (int i = 0; i < 8; i++) {
        chksum += b[4 + i];    // csum on seq and lastAckSeq only
      }
      bb.position(0);
      bb.putShort((short) 0xa1b2);
      bb.putShort(chksum);
      try {
        s.getOutputStream().write(b, 0, 12);
      } catch (IOException e) {
        prta(tag + "ACKR: IOError writing null.  Close socket.");
      }
      prta(tag + "ACKR:  Send Null for ack=" + lastAckSeq);
    }

    @Override
    public void run() {
      byte[] b = new byte[12];
      ByteBuffer bb = ByteBuffer.wrap(b);

      while (!terminate) {
        // if the socket is closed, wait for it to open again
        while (s == null) {
          try {
            sleep(1000);
          } catch (InterruptedException expected) {
          }
        }
        while ((s.isClosed() || !s.isConnected()) && !terminate) {
          try {
            sleep(1000);
          } catch (InterruptedException expected) {
          }
        }
        if (dbg) {
          prta(Util.clear(acksb).append(tag).append(" top read s=").append(s));
        }

        int len = 12;
        int l = 0;
        int nchar;
        try {
          if (dbg) {
            prta(Util.clear(runsb).append(tag).append("ACKR:  calling read fully"));
          }

          nchar = Util.readFully(s.getInputStream(), b, 0, 12);
          if (nchar == 0) {      // is it an EOF
            prt(Util.clear(acksb).append(tag).append("ACKR: EOF reading from AckReader socket close socket"));
            close();
            continue;
          }
        } catch (IOException e) {
          if (e.toString().contains("Socket is closed") || e.toString().contains("not connect")) {
            prta(Util.clear(acksb).append(tag).append("Socket closed by server! - reconnect."));
            close();
          } else {
            //Util.SocketIOErrorPrint(e,tag+"ACKR: IOError reading ack socket. close socket", getPrintStream());
            prta(Util.clear(acksb).append(tag).append("IOError reading from ack socket. close and continue e=").append(e));
            e.printStackTrace(getPrintStream());
            close();
          }
          continue;           // go to top of loop and wait to read some more data
        }
        if(dbg) 
          prta(Util.clear(acksb).append(tag).append(" out of read loop l=").append(l).append(" nchar=").append(nchar));
        if (terminate) {
          break;
        }
        lastAckTime = System.currentTimeMillis();

        // 12 charactgers have been read, take action
        if (s.isClosed()) {
          try {
            sleep(10000);
          } catch (InterruptedException expected) {
          }
          continue;
        }      // socket is now closed, wait for a new one
        if (terminate) {
          break;
        }
        bb.position(0);
        short leads = bb.getShort();
        short flags = bb.getShort();
        if (leads == (short) 0xa1b3) {     // This is a gap request packet, set the range for the gap file
          gapStart = bb.getInt();
          gapEnd = bb.getInt();
          prta(Util.clear(acksb).append(tag).append("ACKR: got a request for a gap fill ").append(gapStart).
                  append(" to ").append(gapEnd));
          continue;
        }
        if (leads != (short) 0xa1b2) {
          prta(Util.clear(acksb).append(tag).append("ACKR: Leadin not right reading acks - close up socket leads=").
                  append(Util.toHex(leads)));
          close();
          continue;
        }

        int seqAck = bb.getInt();     // The previous last ack or a control code
        int last = bb.getInt();       // The latest sequence to ack, or a control code
        if (forceAckSeq >= 0) {
          seqAck = forceAckSeq;
          last = forceAckSeq;
          forceAckSeq = -2;
          prta(tag + " ***** force sequence to " + last);
        }
        // Check for far end wanting a resstart somewhere else
        if(dbg || seqAck % 10 == 0)  
          prta(Util.clear(acksb).append(tag).append("ACKR: rcv seq=").append(seqAck).
                append(" last=").append(last).append(" lastAckSeq=").append(lastAckSeq));
        try {
          if (lastAckSeq == -1 || (in.isNew() && in.getNextout() == 0)) {
            prta(Util.clear(acksb).append(tag).append("ACKR: *** lastAckSeq == -1 means new ring file.  Send null to force reset. acked=").
                    append(seqAck).append(" last=").append(last).append(" newfile=").append(in.isNew()).
                    append(" in.nextout=").append(in.getNextout()));
            sendNull();
            lastAckSeq = 0;  // IS THIS RIGHT????
            last = 0;
            in.setNextout(in.getLastSeq());
          } else if (seqAck == -1) {    // The user wants to start at an offset from our last known, less whatever is in last NOT else is new
            int next = lastAckSeq - last;
            if (next < 0) {
              next += RingFile.MAX_SEQ;
            }
            if (in.setNextout(next)) {
              prta(Util.clear(acksb).append(tag).append("ACKR:  Startup mode with offset=").append(last).
                      append(" sets nextout successfully to ").append(next).append(" in.nextout=").append(in.getNextout()));
            } else {
              prta(Util.clear(acksb).append(tag).append("ACKR: Startup mode with offset=").append(last).
                      append(" was NOT SUCCESSFUL.  Set to latest data=").append(in.getNextout()));
              lastAckSeq = in.getNextout();
              sendNull();
            }
            lastAckSeq = in.getNextout();
            readOne = true;
          } else if (seqAck == -2) {    // The user does not know, but wants all possible data
            if (in.getLastSeq() == 0) {
              prta(Util.clear(acksb).append(tag).
                      append("ACKR: Startup mode for Maximum block, but input files is likely empty last=").append(in.getLastSeq()).
                      append(" Set nextout to zero"));
              in.setNextout(in.getLastSeq());
              lastAckSeq = -1;
              last = 0;
            } else {
              int next = in.getNextout() - in.getSize() + in.getSize() / 100;
              if (in.getNextout() < in.getSize()) {
                next = 1;      // It must be a new file do not trust the old data
              }
              if (next < 0) {
                next += RingFile.MAX_SEQ;
              }
              prta(Util.clear(acksb).append(tag).append("ACKR: Startup mode maximum blocks set to ").append(next).
                      append(" old nextout=").append(in.getNextout()).append(" size=").append(in.getSize()));
              in.setNextout(next);
              lastAckSeq = in.getNextout();
            }
            sendNull();
            readOne = true;
          } else if (seqAck == last) {     // Initialize on this sequence
            lastAckSeq = seqAck;
            if (lastAckSeq >= RingFile.MAX_SEQ) {
              lastAckSeq -= RingFile.MAX_SEQ;
            }
            prta(Util.clear(acksb).append(tag).append(" attempt to set sequence =").append(lastAckSeq).
                    append(" in Ring.  next=").append(in.getLastSeq()).
                    append(" size=").append(in.getSize()).append(" nextout=").append(in.getNextout()));
            // force the sequence to return next in the RRPRingFile.
            if (forceInit) {
              lastAckSeq = in.getLastSeq();
              in.setNextout(in.getLastSeq());
              prta(tag + " ForceINIT on - set sequence to " + in.getNextout());
              sendNull();
            } else if (in.setNextout(lastAckSeq)) {
              prta(Util.clear(acksb).append(tag).append("ACKR: Startup set seq to ").append(seqAck).
                      append(" was successful nextout now ").append(in.getNextout()));
              if (forceAckSeq == -2) {   // When we are forcing an Ack we must be forceful and set lastAckSeq and do a sendNull
                lastAckSeq = in.getNextout();
                sendNull();
              } else {
                sendSetupOK();     // Tell other end everything is o.k
              }
            } else {    // could not set the lastAckSeq - do the best we can going backwards.
              int next = in.getNextout() - in.getSize() + in.getSize() / 100;
              if (in.getNextout() < in.getSize()) {
                next = 1;      // It must be a new file do not trust the old data
              }
              if (next < 0) {
                next += RingFile.MAX_SEQ;
              }
              in.setNextout(next);
              prta(Util.clear(acksb).append(tag).append("ACKR: Startup set seq OOR - set it to oldest nout=").append(in.getNextout()).
                      append(" size=").append(in.getSize()).append(" new=").append(next));
              lastAckSeq = in.getNextout();
              sendNull();
            }

            readOne = true;
          } else if (seqAck != lastAckSeq + 1) {       // There is a gap in the acknowlegements!
            prta(Util.clear(acksb).append(tag).append("ACKR: Got gap in acks lastAckSeq=").append(lastAckSeq).
                    append(" got ").append(seqAck).append(" new last=").append(last));  // Its an ack range, check to see if its end is right
          } else if (last / 10000 != seqAck / 10000) {
            prta(tag + "ACKR: status lastAckSeq=" + lastAckSeq + " got " + seqAck + " new last=" + last);
          }
          lastAckSeq = last;
          //sendSetupOK();
          outStateFile.updateSequence(in.getNextout(), lastAckSeq, false);// on receiving a ack,  sequence do not write it
        } catch (IOException e) {
          prta(Util.clear(acksb).append(tag).append("ACKR: Wow.  Error writing the lastBlock file!").append(e.getMessage()));
          //Util.exit(1);
        }

      }   // while(!terminate)
    }   // end of run()

    /**
     *
     * @param buf user buffer for data
     * @param off starting offset in buf for first byte of data
     * @param len length to read
     * @return 0 if EOF, else last byte in buf actually filled.
     * @throws IOException
     */
    private int readFully(byte[] buf, int off, int len) throws IOException {
      int nchar;
      int l = off;
      while (len > 0) {

        //while(in.available() <= 0) {try{sleep(10);} catch(InterruptedException e) {if(terminate) return 0;}}
        nchar = s.getInputStream().read(buf, l, len);// get nchar
        if (nchar <= 0) {
          prta("read nchar=" + nchar + " len=" + len + " in.avail=" + s.getInputStream().available());
          if (l > 0) {
            return l;   // If ther are bytes to process, return them
          }
          return 0;             // EOF - User should close up
        }
        l += nchar;               // update the offset
        len -= nchar;             // reduce the number left to read
      }
      return l;
    }
  }     // end of class AckReader

  /**
   * RRPCMonitor the RRPClient and stop it if it does not receive heartBeats or
   * data!
   */
  private final class RRPCMonitor extends Thread {

    boolean terminate;        // If true, this thread needs to exit
    int msWait;               // user specified period between checks
    boolean interruptDone;    // Flag that we have gone off
    RRPClient thr;      // The thread being RRPCMonitored
    long lastInbytes;         // count of last to look for stalls

    public boolean didInterrupt() {
      return interruptDone;
    }

    public void terminate() {
      terminate = true;
      interrupt();
    }

    public RRPCMonitor(RRPClient t) {
      thr = t;
      msWait = 120000;      // Set the ms between checks
      start();
    }

    @Override
    public void run() {
      long lastNbytes = 0;
      int panic = 0;
      prta(tag + "RRPCMonitor has started msWait=" + msWait);
      //try{sleep(msWait);} catch(InterruptedException expected) {}
      while (!terminate) {
        try {
          sleep(msWait);
        } catch (InterruptedException expected) {
        }
        //prta(tag+" LCM: HB="+heartBeat+" lHB="+lastHeartbeat+" in ="+inbytes+" lin="+lastInbytes);
        if (inbytes - lastNbytes < 1000) {
          thr.interrupt();      // Interrupt in case its in a wait
          thr.close();
          SendEvent.edgeSMEEvent("RRP_Stalled", "RRPClient " + filename + " has timed out", this);
          // Close the ring file
          lastNbytes = inbytes;
          interruptDone = true;     // So interrupter can know it was us!
          prta(tag + " RRPCMonitor has gone off panic=" + panic + " s=" + s + " isclosed=" + s.isClosed() + " isalive=" + thr.isAlive() + " state=" + state);
          if (!thr.isAlive()) {
            prta(tag + " RRPCMonitor no active thread.  exit()");
            //Util.exit(2);
          }
          try {
            sleep(msWait);
          } catch (InterruptedException expected) {
          }
          interruptDone = false;
          panic++;
          if (panic > (lastNbytes < 1000 ? 500 : 10)) {
            SendEvent.edgeSMEEvent("RRP_Panic", "RRPClient " + filename + " has paniced and is exiting.", this);
            //Util.exit(1);
          }
        } else {
          panic = 0;
        }
        lastInbytes = inbytes;
      }
    }
  }

  /**
   * this internal class gets registered with the Runtime shutdown system and is
   * invoked when the system is shutting down. This must cause the thread to
   * exit
   */
  private final class RFTCIShutdown extends Thread {

    /**
     * default constructor does nothing the shutdown hook starts the run()
     * thread
     */
    public RFTCIShutdown() {
    }

    // This is called by the RunTime.shutdown at any termination 
    @Override
    public void run() {
      prta(tag + "RFTCI Shutdown() started...");
      try {
        sleep(2000);
      } catch (InterruptedException expected) {
      }    // Let a squirt of data pass
      terminate();          // send terminate to main thread and cause interrupt
      int loop = 0;
      terminate();
      prta(tag + "RFTCI shutdown() is complete.");
    }
  }

  /**
   * @param args the command line arguments
   */
  public static void main(String[] args) {
    // TODO code application logic here
    Util.setProcess("RRPClient");
    EdgeProperties.init();
    boolean makeCheck = true;
    String logfile = "rrpc";
    EdgeThread.setMainLogname("rrpc");
    Util.setModeGMT();
    //EdgeMom.setNoConsole(false);
    //EdgeMom.prt("no console");
    //EdgeThread.setUseConsole(true);
    boolean dbg = false;
    Util.setNoInteractive(true);
    String argline = "";
    for (int i = 0; i < args.length; i++) {
      argline += args[i] + " ";
      if (args[i].equals("-dbg")) {
        dbg = true;
      }
      if (args[i].equals("-hdrdump")) {
        EdgeThread.setUseConsole(true);
      }
      if (args[i].equals("-log")) {
        logfile = args[i + 1];
      }
    }
    if (args.length == 0) {
      System.out.println("RRPClient -ring file [-hdrdump] -ip nn.nn.nn.nn [-p port][[-dbg][-dbgbin]");
      System.out.println("   -ring  filename the ring file to operate on.  defautl='ringbuffer'");
      System.out.println("   -hdrdump            Dump out the header information from the ring and the .last file");
      System.out.println("   -ip    nn.nn.nn.nn IP address of the server");
      System.out.println("   -p     port         User port instead of default of 22223");
      System.out.println("   -dbg                More verbose output");
      System.out.println("   -dbgbin             Replace bytes of miniseed with seq number for testing mode (DEBUG ONLY)");
      System.exit(0);
    }
    argline += " >>" + logfile;
    // -host n.n.n.n -port nn -mhost n.n.n.n -mport nn -msgmax max -qsize nnnn -wait nnn (secs) -module n -inst n
    //HydraOutputer.setCommandLine("");     // set all defaults  
    try {
      RRPClient infra = new RRPClient(argline, "RRPC", null);
    } catch (IOException expected) {
    }
  }

}
