/*
 *  This software is in the public domain because it contains materials 
 *  that originally came from the United States Geological Survey, 
 *  an agency of the United States Department of Interior. For more 
 *  information, see the official USGS copyright policy at 
 *  http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.edgemom;

import gov.usgs.alarm.SendEvent;
import gov.usgs.anss.edge.IndexBlock;
import gov.usgs.anss.edge.RawDisk;
import gov.usgs.anss.util.Util;
import gov.usgs.anss.edge.IllegalSeednameException;
import java.io.IOException;
import gov.usgs.anss.edgethread.EdgeThread;
import gov.usgs.anss.seed.MiniSeed;
import gov.usgs.anss.edge.ChannelSender;
import gov.usgs.edge.config.Channel;
import static java.lang.Thread.sleep;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.net.UnknownHostException;

/**
 * object of this class handle input from a single client to a raw disk file
 * This thread is created by the RRPServer thread and its configuration is
 * described there.
 */
public final class RRPServerSocket extends Thread {

  private Socket s;
  private String tag;
  private String shortTag;
  // configuration parameters
  private String ipadr;
  private int wait;               // Millis between each 256 blocks throttle time (256*520*8)=1.065 m/bits
  private int size = 10;
  private String filename;
  private int updateFileMS;         // # of millis between file updates with latest block
  private int updateFileModulus;    // A modulus for updating the file (say every 10  or 100 packets);
  private int ackModulus;           // Modulus of ack times
  private int ackMS;                // Time between acks
  private String arglineorg;
  private String clientAddress;
  private boolean hydra;
  private ChannelSender csend;

  //Gap filling related 
  private int gapStart = -1;
  private int gapEnd = -1;

  // Working arrays
  private final byte[] buf;
  private final ByteBuffer bbuf;      // data buffer for blocks read from inet
  private final byte[] ackbuf;
  private final ByteBuffer ack;
  private final byte[] gapackbuf;
  private final ByteBuffer gapack;
  private final byte[] b;              // Data for control block in ring file
  private final ByteBuffer bb;          // Bitye for for control block in ring file
  private boolean terminate;
  private boolean firstAck;
  private RawDisk dsk;
  private final byte[] ctlbuf;
  private final ByteBuffer ctlbb;       // Wrap the ctrl file buffer
  private int nextout;            // next block we expect to write to disk
  private int lastAckNextout;     // Last block in file acked in protocol
  // Status related variables;
  private long lastAckUpdate;     // Time of last ack update.
  private long lastControlUpdate; // Last system time the control block was updated.
  private long lastBlockCheck;
  private long lastStatusUpdate;
  private long npackets;
  private boolean dbg, dbgbin;
  private boolean writeDiskFile;
  private final String orgtag;
  private int state;
  private final EdgeThread par;
  private final StringBuilder tmpsb = new StringBuilder(100);
  private final StringBuilder runsb = new StringBuilder(100);

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
      sb.append(tag).append(filename).append(" nextout=").append(nextout).append(" lastack=").append(lastAckNextout).
              append(" gap=").append(gapStart).append("-").append(gapEnd).append(" state=").append(state);
    }
    return sb;
  }

  public void terminate() {
    terminate = true;
    interrupt();
  }

  public final void prt(String s) {
    if (par == null) {
      Util.prt(s);
    } else {
      par.prt(s);
    }
  }

  public final void prta(String s) {
    if (par == null) {
      Util.prta(s);
    } else {
      par.prta(s);
    }
  }

  public final void prt(StringBuilder s) {
    if (par == null) {
      Util.prt(s);
    } else {
      par.prt(s);
    }
  }

  public final void prta(StringBuilder s) {
    if (par == null) {
      Util.prta(s);
    } else {
      par.prta(s);
    }
  }

  public int getGapStart() {
    return gapStart;
  }

  public int getGapEnd() {
    return gapEnd;
  }

  public String getTag() {
    return shortTag;
  }

  public String getFilename() {
    return filename;
  }

  public String getIP() {
    return ipadr;
  }

  public String getClientIP() {
    return clientAddress;
  }

  public String getArgline() {
    return arglineorg;
  }

  public void setDebug(boolean t) {
    dbg = t;
  }

  public synchronized void closeSocket() {
    //if(dbg) 

    if (s != null) {
      prta(Util.clear(runsb).append(tag).append("    *** Closing socket s=").
              append(s == null ? "null" : s.toString() + " isclosed() =" + s.isClosed()));
      updateControl();
      if (!s.isClosed()) {
        try {
          if (dbg) {
            prta(Util.clear(runsb).append(tag).append("    *** actual close s=").append(s));
          }
          s.close();
        } catch (IOException e) {
          prta(Util.clear(runsb).append(tag).append(" IOError closing connection"));
        }
      }
    }
    s = null;
  }

  /**
   * send and ack for current position
   */
  private void sendAck() {
    if (s == null) {
      return;
    }
    if (s.isClosed()) {
      return;
    }
    state = 10;
    if (firstAck) {                  // First acks need to respond with special command or desired 
      firstAck = false;

      if (lastAckNextout == -1) {      // New file, we do not know where
        prta(Util.clear(runsb).append(tag).append(" Ack Startup files must be new, ask for all data"));
        ack.position(4);
        ack.putInt(-2);
        ack.putInt(0);
      } else {
        prta(Util.clear(runsb).append(tag).append(" Ack Startup : old file, send first desired sequence=").append(nextout));
        if (nextout >= RRPServer.MODULO_SEQ) {
          nextout -= RRPServer.MODULO_SEQ;
        }
        prta(Util.clear(runsb).append(tag).append(" Ack startup : old file nextout=").append(nextout));
        ack.position(4);
        ack.putInt(nextout);
        ack.putInt(nextout);
        lastAckNextout = nextout;
      }

    } // normal running ack
    else {
      ack.position(4);
      ack.putInt(lastAckNextout);
      int last = nextout - 1;
      if (last < 0) {
        last += RRPServer.MODULO_SEQ;
      }
      ack.putInt(last);
      if (last == lastAckNextout) {
        if (dbg) {
          prta(Util.clear(runsb).append(tag).append(" Ack suppressed same sequence =").append(last));
        }
        return;       // do not send same unless first time!
      }
      if(dbg || lastAckNextout % 10 == 0) 
        prta(Util.clear(runsb).append(tag).append("    * Ack for ").append(lastAckNextout).append("-").append(last));
    }
    try {
      s.getOutputStream().write(ackbuf, 0, 12);
      lastAckUpdate = System.currentTimeMillis();

    } catch (IOException e) {
      prt(Util.clear(runsb).append(tag).append("RRPH: sendAck() gave IOException.  Close connection! e=").append(e.getMessage()));
      closeSocket();
    }
    lastAckNextout = nextout;
    state = 11;
  }

  /**
   * Send a gap declaration packet. The user needs to insure the whole gap is
   * processed before this is called again!
   *
   * @param startSeq The starting sequence of the gap
   * @param endSeq The ending sequence of the gap
   */
  public void sendGap(int startSeq, int endSeq) {
    if (gapStart != -1) {
      throw new RuntimeException("Attempt to start a gap when one is running!");
    }
    state = 30;
    gapStart = startSeq;
    gapEnd = endSeq;
    gapack.position(4);
    gapack.putInt(startSeq);   // Put the starting and end sequences in place
    gapack.putInt(endSeq);
    prta(tag + " Send gap " + startSeq + "-" + endSeq);
    try {
      s.getOutputStream().write(gapackbuf, 0, 12);
    } catch (IOException e) {
      prt(Util.clear(runsb).append(tag).append(" sendGap() gave IOException.  Close connection! e=").append(e.getMessage()));
      closeSocket();
    }
    state = 31;
  }

  /**
   *
   * @param argline The argline for starting this service
   * @param tag The tag to use for logging
   * @param parent A parent for logging.
   */
  public RRPServerSocket(String argline, String tag, EdgeThread parent) {
    par = parent;
    orgtag = tag;
    buf = new byte[512];
    bbuf = ByteBuffer.wrap(buf);
    ackbuf = new byte[12];
    ack = ByteBuffer.wrap(ackbuf);
    ack.position(0);
    ack.putShort((short) 0xa1b2);
    ack.putShort((short) 0);

    // Build the gap ack buffer
    gapackbuf = new byte[12];
    gapack = ByteBuffer.wrap(gapackbuf);
    gapack.position(0);
    gapack.putShort((short) 0xa1b3);
    gapack.putShort((short) 0);

    // control buf 
    lastControlUpdate = System.currentTimeMillis();
    ctlbuf = new byte[512];
    ctlbb = ByteBuffer.wrap(ctlbuf);
    for (int i = 0; i < 512; i++) {
      ctlbuf[i] = 0;
    }
    b = new byte[512];
    for (int i = 0; i < 512; i++) {
      b[i] = 0;
    }
    bb = ByteBuffer.wrap(b);
    setConfigLine(argline);

    prta(Util.clear(runsb).append(tag).append("New RRPHandler ").append(filename).
            append(" size=").append(size).append(" waitms=").append(wait));
    start();
  }

  /**
   *
   * @param ss The socket to assign to this handler
   */
  public synchronized void assignSocket(Socket ss) {
    s = ss;
    tag = orgtag + shortTag + " RRPH:" + s.getInetAddress().getHostAddress() + "/" + s.getPort()
            + getName().substring(getName().indexOf("-")) + ":";
    firstAck = true;
    prta(Util.clear(runsb).append(tag).append(" Assigning new socket ").append(s));
    sendAck();
  }

  /**
   * Set the configuration for this handlers.
   *
   * @param argline The configuration line for this handler
   */
  public final synchronized void setConfigLine(String argline) {
    if (argline.trim().equals(arglineorg)) {
      return;
    }
    arglineorg = argline;
    String[] args = argline.split("\\s");
    hydra = true;
    wait = 1000;           // So wait 1000 ms for each mBits inbound - 1 mbit/sec
    boolean nocsend = false;
    updateFileMS = 120000;
    updateFileModulus = 2100000000;     // Never do a update file on the modulus
    ackMS = 30000;
    ackModulus = 1000;
    shortTag = "";
    for (int i = 0; i < args.length; i++) {
      if (args[i].charAt(0) == '>') {
        break;
      }
      switch (args[i]) {
        case "-gsn":
          wait = 1000;
          updateFileMS = 120000;
          updateFileModulus = 2100000000; // never udpate file based on sequence
          size = 10;
          hydra = true;
          nocsend = false;
          ackMS = 30000;
          ackModulus = 100;
          break;
        case "-ip":
          ipadr = args[i + 1].trim();
          i++;
          break;
        case "-t":
          shortTag = args[i + 1];
          tag = shortTag;
          i++;
          break;
        case "":
          break;
        case "-s":
          size = Integer.parseInt(args[i + 1]);
          i++;
          break;
        case "-wait":
          wait = Integer.parseInt(args[i + 1]);
          i++;
          break;
        case "-f":
          filename = args[i + 1];
          i++;
          break;
        case "-dbgbin":
          dbgbin = true;
          break;
        case "-dbg":
          dbg = true;
          break;
        case "-nohydra":
          hydra = false;
          break;
        case "-noudpchan":
          nocsend = true;
          break;
        case "-u":
          updateFileMS = Integer.parseInt(args[i + 1]);
          i++;
          break;
        case "-up":
          updateFileModulus = Integer.parseInt(args[i + 1]);
          i++;
          break;
        case "-a":
          ackMS = Integer.parseInt(args[i + 1]);
          i++;
          break;
        case "-ap":
          ackModulus = Integer.parseInt(args[i + 1]);
          i++;
          break;
        case "-writering":
          writeDiskFile = true;
          break;
        default:
          Util.prt("Unknown argument in config file line=" + i + " arg=" + args[i] + "|");
      }
    }
    prta(Util.clear(runsb).append(tag).append(" Set RRPSS config ip=").append(ipadr).
            append(" file=").append(filename).append(" write=").append(writeDiskFile).
            append(" size=").append(size).append(" waitms=").append(wait).
            append(" hydra=").append(hydra).append(" noudpchan=").append(nocsend).
            append(" ack=").append(ackMS).append("/").append(ackModulus).
            append(" fileupd=").append(updateFileMS).append("/").append(updateFileModulus));

    if (nocsend) {
      if (csend != null) {
        csend = null;
      }
    } else {
      if (csend == null) {
        csend = new ChannelSender("  ", "RRPSrv", "R2E-" + ipadr);
      }
    }
    firstAck = true;
    try {
      prta(Util.clear(runsb).append(tag).append(" create new RawDisk - close the socket?"));
      if (dsk != null) {
        closeSocket();    // Note this closes dsk and s and sets s=null
      }
      dsk = new RawDisk(filename, "rw");
      //ctldsk = new RawDisk(filename+".ctl","rw");
    } catch (IOException e) {
      prt(Util.clear(runsb).append(tag).append(" Could not open the ring file or its control file e=").append(e.getMessage()));
      SendEvent.edgeSMEEvent("RRPSNoRing", tag + "Ring file does not exist", this);
    }
    try {
      clientAddress = InetAddress.getByName(ipadr).getHostAddress();
    } catch (UnknownHostException e) {
      clientAddress = ipadr;
    }

    try {
      boolean doInit = false;
      if (dsk.length() == 0) {   // is it a new file
        doInit = true;
        lastAckNextout = -1;
        nextout = -1;
      } else {                    // existing file, check its length and get nextout and size
        dsk.readBlock(b, 0, 512);
        bb.clear();
        nextout = bb.getInt();       // get the first sequence from header
        int sizenow = bb.getInt();
        lastAckNextout = bb.getInt();
        //if(size != sizenow) doInit=true;
        if (size > sizenow) {
          prt(Util.clear(runsb).append(tag).append(" make file bigger size was ").append(sizenow).append(" now ").append(size));
          if (writeDiskFile) {
            dsk.writeBlock(size, b, 0, 512); // make it bigger
          }
          lastAckNextout = nextout;           // we are going to start at last block processed
        }
        if (size < sizenow) {
          size = sizenow;  // if its bigger, use the full allocated size
          if (writeDiskFile) {
            dsk.setLength(((long) size + 1) * 512);
          }
          lastAckNextout = nextout;
        }
      }
      // the file is new or changed size, clear it out since the blocks no long align with sequence
      if (doInit) {
        bb.clear();
        bb.putInt(nextout);
        bb.putInt(size);      // in blocks
        bb.putInt(lastAckNextout);
        dsk.writeBlock(0, b, 0, 512);      // write out the control block
        for (int i = 0; i < 512; i++) {
          b[i] = 0;
        }
        prta(Util.clear(runsb).append(tag).append(" New Ring file - zero it. size=").append(size).
                append(" writeDisk=").append(writeDiskFile));
        byte[] zerobuf = new byte[51200];
        if (writeDiskFile) {     // zero out the file
          int blk = 1;
          while (blk < size + 1) {
            int nblk = size + 1 - blk;
            if (nblk > 100) {
              nblk = 100;
            }
            dsk.writeBlock(blk, zerobuf, 0, zerobuf.length);
            blk += nblk;
          }
        }
        prt(Util.asctime() + " " + tag + " New Ring initialized. writeDiskFile=" + writeDiskFile + " length=" + dsk.length());
      }
    } catch (IOException e) {
      Util.IOErrorPrint(e, tag + " getting size or reading data from ring file e=" + e.getMessage());
    }
  }

  @Override
  public void run() {
    int len;
    int nchar = 0;
    long waitfor;
    int l;
    short chksum;
    int seq;
    lastStatusUpdate = System.currentTimeMillis();
    lastBlockCheck = lastStatusUpdate;
    int lastNextout = nextout;
    short leadin;
    boolean isGapSeq;
    MiniSeed ms = null;
    long kbps=0;
    long packetDenom = 100;

    OUTER:
    while (!terminate) {
      state = 1;
      try {
        if (s == null) {
          prta(Util.clear(runsb).append(tag).append("Socket is null.  Wait for an assignment"));
          while (s == null && !terminate) {
            try {
              sleep(100);
            } catch (InterruptedException expected) {
            }
          }
          prta(Util.clear(runsb).append(tag).append("Socket is assigned"));
          if (terminate) {
            break;
          }
        }
        len = 8;
        l = 0;
        state = 2;
        while (len > 0) {            //
          nchar = s.getInputStream().read(buf, l, len);// get nchar
          if (nchar <= 0) {
            prta(Util.clear(runsb).append(tag).append(" EOF on socket. close and wait for new connection"));
            closeSocket();
            break;
          }      // EOF - close up
          l += nchar;               // update the offset
          len -= nchar;             // reduce the number left to read
        }
        if (nchar <= 0) {
          continue;      // EOF go around
        }
        if (terminate) {
          break;
        }
        bbuf.position(0);
        leadin = bbuf.getShort();
        state = 3;
        isGapSeq = false;
        if (leadin != (short) 0xa1b2 && leadin != (short) 0xa1b3) {
          state = 4;
          bbuf.position(0);
          prt(Util.clear(runsb).append(tag).append(" Leadins not right. close up.").
                  append(Util.toHex(bbuf.getShort())));
          closeSocket();
          continue;
        }
        chksum = bbuf.getShort();
        seq = bbuf.getInt();
        state = 5;
        short chk = 0;
        for (int i = 4; i < 8; i++) {
          chk += buf[i];
        }
        // Is this a gap filling packet
        switch (leadin) {
          case (short) 0xa1b3:
            // yes
            state = 6;
            isGapSeq = true;
            seq = -seq;
            if (seq != gapStart + 1) {
              prta(Util.clear(runsb).append(tag).append(" gap fill is not right seq=").append(seq).
                      append(" expecting=").append(gapStart + 1));
            }
            if (seq == gapEnd) {
              prta(Util.clear(runsb).append(tag).append(" gap fill complete").append(seq).
                      append(" gapEnd=").append(gapEnd));
              gapStart = -1;
              gapEnd = -1;
            }
            len = 512;
            l = 0;
            state = 7;
            while (len > 0) {            //
              nchar = s.getInputStream().read(buf, l, len);// get nchar
              if (nchar <= 0) {
                prta(Util.clear(runsb).append(tag).append(" EOF on socket. close and wait for new connection"));
                closeSocket();
                break;
              }     // EOF - close up
              l += nchar;               // update the offset
              len -= nchar;             // reduce the number left to read
              if (terminate) {
                break;
              }
            }
            if (nchar <= 0) {
              continue;
            }
            if (terminate) {
              break OUTER;
            }
            state = 8;
            if (writeDiskFile) {
              dsk.writeBlock((seq % size) + 1, buf, 0, 512);
            }
            try {
              if (ms == null) {
                ms = new MiniSeed(buf, 0, len);
              } else {
                ms.load(buf, 0, len);
              }
              if (dbg || seq % 1000 == 2) {
                prta(Util.clear(runsb).append(tag).append(" Gap fill  sq=").append(seq).append(" ").
                        append(ms.toStringBuilder(null, 80)));
              }
              if (csend != null) {
                csend.send(ms.getJulian(),
                        (int) (ms.getTimeInMillis() % 86400000), // Milliseconds
                        ms.getSeedNameSB(), ms.getNsamp(), ms.getRate(), ms.getBlockSize());  // key,nsamp,rate and nbytes
              }
              try {
                Channel c = EdgeChannelServer.getChannel(ms.getSeedNameSB());
                //if(seedname.indexOf("REPV15")>= 0)  par.prta(seedname+": "+c.toString()+" flags="+c.getFlags()+" mask="+mask+" hydra="+hydra);
                if (c == null) {
                  prta(Util.clear(tmpsb).append(tag).append("RRP2Edge: ***** new channel found=").append(ms.getSeedNameSB()));
                  SendEvent.edgeSMEEvent("ChanNotFnd", "RRP2Edge: MiniSEED Channel not found=" + ms.getSeedNameSB() + " new?", this);
                  /*Util.clear(dbmsg).append("edge^channel^channel=").append(seedname).
                  append(";commgroupid=0;operatorid=0;protocolid=0;sendto=0;flags=0;links=0;delay=0;sort1=new;sort2=;rate=").
                  append(rate).append(";nsnnet=0;nsnnode=0;nsnchan=0;created_by=0;created=current_timestamp();");*/
                  EdgeChannelServer.createNewChannel(ms.getSeedNameSB(), ms.getRate(), this);
                }
                state = 9;
                IndexBlock.writeMiniSeedCheckMidnight(ms, false, false, tag, par); // Do not use hydra via this method
                //if(hydra) Hydra.sendNoChannelInfrastructure(ms);        // Use this for Hydra
              } catch (RuntimeException e) {
                prta(Util.clear(runsb).append(tag).append("RRP2Edge: *** RuntimeError in writeMiniSeedMidnight - ignore ms=").append(ms));
                e.printStackTrace(par.getPrintStream());
              }
            } catch (IllegalSeednameException e) {
              prta(Util.clear(runsb).append(tag).append("Illegal seedname! skip and continue. e=").append(e));
            } // check whether we need to throttle.
            state = 12;
            break;
          // if this is a normal packet 0xa1b2
          case (short) 0xa1b2:
            state = 14;
            if (nextout >= RRPServer.MODULO_SEQ) {
              nextout -= RRPServer.MODULO_SEQ;
            }
            if (seq != nextout && seq >= 0) {
              prt(Util.clear(runsb).append(tag).append(" Sequence out of order got ").append(seq).
                      append(" expecting ").append(nextout));
              closeSocket();
              continue;
            }
            state = 15;
            // If sequence is negative, this is a "null" ack and we need to reset our expectations
            if (seq < 0) {
              nchar = s.getInputStream().read(buf, 0, 4);
              //l = 4;
              if (nchar == 4) {
                bbuf.position(0);
                nextout = bbuf.getInt();
                lastAckNextout = nextout - 1;
                if (seq != Integer.MIN_VALUE) {
                  nextout=2;
                  lastAckNextout = nextout - 1;
                  prta(Util.clear(runsb).append(tag).append(" Got Null packet set seq to ").append(nextout));
                  sendAck();
                } else {
                  prta(Util.clear(runsb).append(tag).append(" Got Seq OK packet set seq to ").append(nextout));
                }
                continue;
              } else {
                prta(Util.clear(runsb).append(tag).append(" Got Null seq but could not read rest!"));
                continue;
              }
            }
            break;
          default:
            prta(Util.clear(runsb).append(tag).append("Impossible: lead in not known value ").append(leadin));
            closeSocket();
            continue;
        }
        len = 512;
        state = 16;
        l = 0;
        while (len > 0) {            // 
          nchar = s.getInputStream().read(buf, l, len);// get nchar
          if (nchar <= 0) {
            prta(Util.clear(runsb).append(tag).append(" EOF on socket. close and wait for new connection"));
            closeSocket();
            break;
          }     // EOF - close up
          l += nchar;               // update the offset
          len -= nchar;             // reduce the number left to read
        }
        if (nchar <= 0) {
          continue;    // EOF go around again
        }
        state = 17;
        for (int i = 0; i < l; i++) {
          chk += buf[i];
        }
        if (chk != chksum) {
          prta(Util.clear(runsb).append(tag).append(" Checksum do not agree nextout=").append(nextout).
                  append(" chk=").append(Util.toHex(chk)).append(" != ").append(Util.toHex(chksum)));
          closeSocket();
        } else {
          if (dbgbin) {        // check that the sequence agrees with the seq in header
            bbuf.position(0);
            int seqhdr = bbuf.getInt();
            if (seqhdr != seq) {
              prta(Util.clear(runsb).append(tag).append(" Seq in data block does not agree with seq delivered! hdr=").
                      append(seqhdr).append(" != ").append(seq));
            }
          }
          //if(dbg) prta(Util.clear(runsb).append(tag).append(" rcv ").append(toStringRaw(buf)));
          // The data packet is good, write it out
          state = 18;
          npackets++;
          if (writeDiskFile) {
            dsk.writeBlock((seq % size) + 1, buf, 0, 512);
          }
          try {
            if (ms == null) {
              ms = new MiniSeed(buf, 0, 512);
            } else {
              ms.load(buf, 0, 512);
            }
            if (dbg || seq % packetDenom == 2) {
              if(kbps > 0) {
                packetDenom = 100;
                if(kbps > 10000) packetDenom=1000;
                if(kbps > 100000) packetDenom = 10000;
                if(kbps > 1000000) packetDenom = 100000;
              }
              prta(Util.clear(runsb).append(tag).append(isGapSeq ? " GAP sq=" : " RRP sq=").append(seq).
                      append(" ").append(ms.toStringBuilder(null, 80)));
            }
            if (csend != null) {
              csend.send(ms.getJulian(),
                      (int) (ms.getTimeInMillis() % 86400000), // Milliseconds
                      ms.getSeedNameSB(), ms.getNsamp(), ms.getRate(), ms.getBlockSize());  // key,nsamp,rate and nbytes
            }
            try {
              state = 19;
              Channel c = EdgeChannelServer.getChannel(ms.getSeedNameSB());
              //if(seedname.indexOf("REPV15")>= 0)  par.prta(seedname+": "+c.toString()+" flags="+c.getFlags()+
              //" mask="+mask+" hydra="+hydra);
              if (c == null) {
                prta(Util.clear(tmpsb).append(tag).append(tag).append(": ***** new channel found=").append(ms.getSeedNameSB()));
                SendEvent.edgeSMEEvent("ChanNotFnd", tag + ": MiniSEED Channel not found=" + ms.getSeedNameSB() + " new?", this);
                EdgeChannelServer.createNewChannel(ms.getSeedNameSB(), ms.getRate(), this);
              }
              IndexBlock.writeMiniSeedCheckMidnight(ms, false, false, tag, par); // Do not use hydra via this method
              if (hydra && !isGapSeq) {
                Hydra.sendNoChannelInfrastructure(ms);        // Use this for Hydra
              }
            } catch (RuntimeException e) {
              prta(Util.clear(runsb).append(tag).append(" *** RuntimeError in writeMiniSeedMidnight - ignore ms=").append(ms));
              e.printStackTrace(par.getPrintStream());
            }
          } catch (IllegalSeednameException e) {
            prta(Util.clear(runsb).append(tag).append(" Illegal seed? skip and continue"));
          }
          state = 21;
          if (nextout >= RRPServer.MODULO_SEQ) {
            nextout = 0;
          }
          if (seq >= 0 && !isGapSeq) {
            nextout = seq + 1;
          }
          if (nextout >= RRPServer.MODULO_SEQ) {
            nextout -= RRPServer.MODULO_SEQ;
          }

          // If it is time, write out the ctl block
          long now = System.currentTimeMillis();
          if (now - lastControlUpdate > updateFileMS || seq % updateFileModulus == 0) {
            updateControl();
          }
          if (now - lastAckUpdate > ackMS || seq % ackModulus == 0) {
            sendAck();      // send acks every so often
          }
          if (now - lastStatusUpdate > 600000) {
            prta(Util.clear(runsb).append(tag).append("Status10: nextout=").append(nextout).
                    append(" lastAck=").append(lastAckNextout).
                    append(" nb=").append((nextout - lastNextout) * 520 / 1000).append(" kB"));
            lastNextout = nextout;
            lastStatusUpdate = now;
          }
          state = 22;
          // check whether we need to throttle.
          if (npackets % 256 == 200) {
            waitfor = now - lastBlockCheck;
            if ((npackets % 25600) == 200 || waitfor > 120000) {
              long bps = 256 * 520 * 8 * 1000 / waitfor;
              kbps = (bps + 500) / 1000;
              Util.clear(runsb).append(tag).append("Status seq=").append(seq).append(" ");
              if(kbps < 10) runsb.append(bps).append(" bps elapsed=");
              else runsb.append(kbps).append(" kbps elapse=");
              runsb.append(waitfor).append(" waitms=").append(wait).append(" ").
                      append(waitfor < wait ? " * " + (wait - waitfor) + "ms" : "");
              prta(runsb);
            }
            lastBlockCheck = now;
            if (waitfor < wait) {
              try {
                sleep(Math.max(1, wait - waitfor));
              } catch (InterruptedException expected) {
              }
              lastBlockCheck = System.currentTimeMillis();
            }
          }
        }
        state = 23;
      } catch (IOException e) {
        Util.IOErrorPrint(e, tag + " RRPH: IOError on socket. close and wait for connection");
        prta(Util.clear(runsb).append(tag).append(" RRPH: IOError on socket. close and wait for connections e=").append(e));
        closeSocket();
      }
    }
    prt(Util.clear(runsb).append(tag).append(" exiting terminate=").append(terminate));
    if (nchar < 0) {
      prt(Util.clear(runsb).append(tag).append(" RRPH: EOF found.  close up socket - should not happen"));
    } else {
      prt(Util.clear(runsb).append(tag).append(" RRPH: Exiting loop!  close up socket terminate=").append(terminate));
    }

    if (s != null) {
      closeSocket();    // Note : this updates control block also
    }
    try {
      dsk.close();
    } catch (IOException e) {
      prta(Util.clear(runsb).append(tag).append("IOError closing disk ").append(filename));
    }
    prta(Util.clear(runsb).append(tag).append(" RRPH: RRPServerHandler has exit on s=").append(s));
    if (csend != null) {
      csend.close();
    }
  }

  /**
   * write out the control block with current values
   */
  protected void updateControl() {
    if (dbg) {
      prta(Util.clear(runsb).append(tag).append("    * Update control block nextout=").append(nextout).
              append(" size=").append(size).append(" lastAck=").append(lastAckNextout).append(" state=").append(state));
    }
    ctlbb.position(0);
    ctlbb.putInt(nextout);
    ctlbb.putInt(size);
    ctlbb.putInt(lastAckNextout);
    try {
      dsk.writeBlock(0, ctlbuf, 0, 512);
    } catch (IOException e) {
      prta(Util.clear(runsb).append(tag).append(" IOException updating control block =").append(e.getMessage()));
      e.printStackTrace(par.getPrintStream());
    }
    lastControlUpdate = System.currentTimeMillis();
  }

}         // End of RRPHandler class

