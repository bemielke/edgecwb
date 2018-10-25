/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.edgemom;

import gov.usgs.anss.edge.RawToMiniSeed;
import gov.usgs.anss.edge.IndexFile;
import gov.usgs.anss.edge.IndexBlock;
import gov.usgs.anss.edge.EdgeProperties;
import gov.usgs.anss.edge.ChannelSender;
import gov.usgs.alarm.SendEvent;
//import gov.usgs.anss.edge.*;
import gov.usgs.anss.edge.IllegalSeednameException;
import gov.usgs.anss.edge.EdgeFileCannotAllocateException;
import gov.usgs.anss.edge.EdgeFileDuplicateCreationException;
import gov.usgs.anss.edge.EdgeFileReadOnlyException;
import gov.usgs.anss.edge.EdgeBlockQueue;
import gov.usgs.anss.edge.RawDisk;
import gov.usgs.anss.edgeoutput.DeleteBlockInfo;
import gov.usgs.anss.edgethread.EdgeThread;
import gov.usgs.anss.seed.MiniSeed;
import gov.usgs.anss.seed.SteimException;
import gov.usgs.anss.util.Util;
import gov.usgs.edge.config.Channel;
import java.util.GregorianCalendar;
import java.util.Calendar;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.ArrayList;

/**
 * This class receives MiniSeed data from a socket. It is normally created by
 * the MiniSeedServer as each connection comes in. It inherits its arguments
 * from the MiniSeedServer configuration (no hydra, no udp chan, etc.). As it
 * reads the MiniSeed, its buffer size can be adapted of B1000, indicating
 * blocks of bigger sizes. It also breaks 4k packets into 512 packets unless
 * configured otherwise.
 *
 * @author davidketchum
 */
public final class MiniSeedSocket extends EdgeThread {

  private final int port;
  private final String host;
  private int countMsgs;
  private int heartBeat;
  private final int msgLength;
  private final Socket d;           // Socket to the server
  private long lastStatus;     // Time the last status was received
  private InputStream in;
  private OutputStream outsock;
  private final ChannelSender csend;
  private final boolean allow4k;
  private long bytesIn;
  private long bytesOut;
  private final boolean hydra;
  private String seedname;        // The NNSSSSS for a station coming through this socket
  private boolean dbg;
  private final EdgeThread parent;
  private final boolean scn;
  private final int waitms;
  private final boolean q330check;
  private long terminateTime;
  private double fscale = 0.;
  private final StringBuilder seednameSB = new StringBuilder(12);
  private final StringBuilder runsb = new StringBuilder(50);
  private final StringBuilder tmpsb = new StringBuilder(100);

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
      sb.append(host).append("/").append(port).append(" par=").append(parent.getTag());
    }
    return sb;
  }

  public void setDebug(boolean t) {
    dbg = t;
  }

  @Override
  public long getBytesIn() {
    return bytesIn;
  }

  @Override
  public long getBytesOut() {
    return bytesOut;
  }

  public String getSeedname() {
    return seedname;
  }
  StringBuilder cmd = new StringBuilder(100);
  private DeleteBlockInfo dbi;    // Used in processCommand for replacing blocks
  private byte[] scr;            // Used in processCommand for replacing blocks
  private byte[] scr2;
  private ByteBuffer bbscr;       //   .    .     . to scr

  private boolean processCommand(ByteBuffer bb) throws IOException, IllegalSeednameException, RuntimeException {
    MiniSeed ms = null;
    IndexFile indexFile;
    ArrayList<IndexBlock> indexBlocks = new ArrayList<>(100);
    bb.position(0);
    byte byt;
    int j = 0;
    Util.clear(cmd);
    while ((byt = bb.get()) != '\n') {
      cmd.append((char) byt);
    }
    String[] parts = cmd.toString().replaceAll("  ", " ").replaceAll("  ", " ").split("\\s");
    if (!parts[0].equals("CMD")) {
      prta("Huh!  parts[0] should be 'CMD'");
    }
    if (parts[1].equalsIgnoreCase("REPLACE")) {
      // Make up our scratch space with the dbi binary data and build a DBI
      if (scr == null) {
        scr = new byte[512];
        scr2 = new byte[512];
        bbscr = ByteBuffer.wrap(scr);
      }
      bb.get(scr, 0, DeleteBlockInfo.DELETE_BLOCK_SIZE);  // Read in block to replace 
      bbscr.position(0);
      if (dbi == null) {
        dbi = new DeleteBlockInfo(bbscr);
      } else {
        dbi.reload(bbscr);
      }
      int len = bb.position();      // This is how many bytes were taken by the command and block and remain to be read for Miniseed
      bb.get(scr, 0, msgLength - bb.position());    // Put the first part of miniseed into scr buffer

      // The length of the command. DeleteBlockInfo binary is missing from the miniseed block, so add it on
      try {
        int l = msgLength - len;                    // Offset into buf for next read
        int nchar = 0;                // Number of characters returned
        while (len > 0) {            // 
          //nchar= in.read(buf, l, len);// get nchar
          nchar = Util.socketRead(in, scr, l, len);// Get nchar
          if (nchar <= 0) {
            return false;     // EOF - close up
          }
          l += nchar;               // Update the offset
          len -= nchar;             // Reduce the number left to read
        }
        if (nchar <= 0) {
          return false;     // EOF - close up - go to outer infinite loop
        }
      } catch (IOException e) {
        throw e;
      }
      if (ms == null) {
        ms = new MiniSeed(scr);
      } else {
        ms.load(scr);
      }
      int julian = ms.getJulian();
      try {
        indexFile = IndexFile.getIndexFile(julian);
        if (indexFile == null) {
          indexFile = new IndexFile(julian, false, false);
        }
        if (indexFile.getJulian() == dbi.getJulian()) {      // Need to build up the list of IndexBlocks
          if (IndexFile.getNode().equals(dbi.getInstance().trim())) {// If it is the wrong instance, then there is no sense in looking
            Util.clear(seednameSB).append(dbi.getSeedname());
            IndexBlock idx = new IndexBlock(indexFile, seednameSB);
            while (idx.getIndexBlockNumber() != dbi.getIndexBlock()) {
              boolean ok = idx.nextIndexBlock();
              if (!ok) {
                break;
              }
            }
            if (idx.getIndexBlockNumber() == dbi.getIndexBlock()) {
              // Found the index block which matches
              if (Util.stringBuilderEqual(idx.getSeedName(), dbi.getSeedname())) {
                int extent = idx.getExtents()[dbi.getExtent()];
                if (extent >= 0) {
                  int iblk = extent + dbi.getBlock();
                  indexFile.getDataRawDisk().readBlock(scr2, iblk, 512);
                  if (scr2[7] != '~') {
                    parent.prt(Util.clear(runsb).append("This is not a deleted block - abort!!!! iblk=").append(iblk));
                  } else {
                    short time = (short) ((ms.getTimeInMillis() % 86400000L) / 3000);
                    if (time >= idx.getEarliestTime(dbi.getExtent()) && time <= idx.getLatestTime(dbi.getExtent())) {
                      indexFile.getDataRawDisk().writeBlock(iblk, scr, 0, 512);
                      parent.prt(Util.clear(runsb).append("Success write of iblk=").append(iblk).append(" dbi=").append(dbi).append(" scr=").append(ms));
                      return true;
                    } else {
                      parent.prt(Util.clear(runsb).append("Failed time test ").append(time).
                              append(" early=").append(idx.getEarliestTime(dbi.getExtent())).
                              append(" late=").append(idx.getLatestTime(dbi.getExtent())));
                    }
                  }
                }
              } else {
                parent.prt(Util.clear(runsb).append("Block number disagree=").append(dbi.getIndexBlock()));
              }
            }
          } else {
            parent.prt(Util.clear(runsb).append("instances do not match - are you running on a Edge/CWB node"));
          }
        }          // Its not an available block
        parent.prt(Util.clear(runsb).append("Something does not match ! julian ").
                append(dbi.getJulian()).append(" ").append(indexFile.getJulian()).
                append(" instance=").append(dbi.getInstance()).append(" ").append(IndexFile.getNode()));
        bb.position(0);
        bb.put(scr, 0, 512);      // Put the data in
      } catch (EdgeFileReadOnlyException e) {
        parent.prt("Could not write to file readonly ");
        e.printStackTrace();
      } catch (EdgeFileDuplicateCreationException e) {
        parent.prt("Dup create - how does this happen");
        e.printStackTrace();
      } catch (EdgeFileCannotAllocateException e) {
        parent.prt("Cannot allocate - is channel not real?");
        e.printStackTrace();
      }

    } else if (parts[1].equalsIgnoreCase("FSCALE")) {
      fscale = Double.parseDouble(parts[2]);
      parent.prt("Setting FSCALE=" + fscale);
    } else {
      throw new RuntimeException("Command is not known cmd=" + cmd + "|");
    }
    return false;
  }

  /**
   * Create a new instance of MiniSeedSocket (usually for MiniSeedServer).
   *
   * @param s The socket to read the data from.
   * @param par The parent object. Used for logging to the same file.
   * @param tg The EdgeThread tag to use.
   * @param c The channel sender to use (null if sending is disabled).
   * @param hydraFlag If True, send all data to hydra on receipt.
   * @param allow4096 If true, 4k packets to go database rather than the default
   * of being split up into 512 packets.
   * @param wait Wait time in millis between each block received.
   * @param q330 If true, do Q330 checking.
   * @param scnmode if true, strip off locations.
   */
  public MiniSeedSocket(Socket s, MiniSeedServer par, String tg, ChannelSender c,
          boolean hydraFlag, boolean allow4096, int wait, boolean q330, boolean scnmode) {
    super("", tg);
    q330check = q330;
    tag += "MSSkt:[" + s.getInetAddress().getHostAddress() + "/" + s.getLocalPort() + "]";
    parent = par;
    this.setPrintStream(parent.getPrintStream());   // Use same log as the server
    d = s;
    allow4k = allow4096;
    csend = c;
    waitms = wait;
    scn = scnmode;
    hydra = hydraFlag;
    host = s.getInetAddress().getHostAddress();
    port = s.getPort();
    countMsgs = 0;
    msgLength = 512;
    heartBeat = 0;
    try {
      in = s.getInputStream();
      outsock = s.getOutputStream();
    } catch (IOException e) {
      Util.IOErrorPrint(e, "Error getting i/o streams from socket " + host + "/" + port, parent.getPrintStream());
      return;
    }

    parent.prt(tag + "MiniSeedSocket: new line parsed to host=" + host + " port=" + port + " len="
            + msgLength + " dbg=" + dbg + " allow4k=" + allow4k + " hydra=" + hydra + " mswait=" + waitms + " q330=" + q330check);
    IndexFile.init();
    start();
  }

  @Override
  public void terminate() {
    terminate = true;
    terminateTime = System.currentTimeMillis();
    if (d != null) {
      parent.prta(tag + "MSSkt: Terminate started. Close input unit.");
      try {
        d.close();
      } catch (IOException e) {
      }
      interrupt();
    } else {
      parent.prt(tag + "MSSkt: Terminate started. interrupt().");
      interrupt();
    }
  }

  /**
   * This Thread keeps a socket open to the host and port, reads in any
   * information on that socket, keeps a list of unique StatusInfo, and updates
   * that list when new data for the unique key comes in.
   */
  @Override
  public void run() {
    byte[] b100 = null;
    ByteBuffer bb100 = null;    // if blockette 100 decoding is needed
    byte[] buf = new byte[msgLength];
    ByteBuffer bbbuf = ByteBuffer.wrap(buf);
    MiniSeed ms = null;
    int[] samples = null;
    int nillegal = 0;
    running = true;               // Mark that we are running
    int lastCount = countMsgs;
    GregorianCalendar gc = new GregorianCalendar();

    parent.prta(tag + " Socket is opened.  Start reads. " + host + "/" + port + " " + getName());
    // Read data from the socket and update/create the list of records 
    int len = msgLength;
    int l = 0;
    int nchar = 0;
    lastStatus = System.currentTimeMillis();
    long openTime = lastStatus;

    int nsamp = 0;
    int ns;
    int nsleft = 0;
    int[] data = null;      // Need to decompress the data
    long now;
    while (true) {
      try {
        if (terminate && in.available() <= 0) {
          break;
        }
        if (terminate) {
          if (System.currentTimeMillis() - terminateTime > 10000) {
            break;
          }
        }
        len = msgLength;          // Desired block length
        l = 0;                    // Offset into buf for next read
        nchar = 0;                // Number of characters returned
        while (len > 0) {            // 
          //nchar= in.read(buf, l, len); // Get nchar
          nchar = Util.socketRead(in, buf, l, len); // Get nchar
          if (nchar <= 0) {
            break;     // EOF - close up
          }
          l += nchar;               // Update the offset
          len -= nchar;             // Reduce the number left to read
        }
        if (nchar <= 0) {
          break;      // EOF - close up - go to outer infinite loop
        }
        countMsgs++;
        now = System.currentTimeMillis();

        // Is it time for status yet?
        if ((now - lastStatus) > 600000 && dbg) {
          parent.prta(Util.clear(runsb).append(tag).append(" # Rcv=").
                  append(countMsgs - lastCount).append(" #tot=").append(countMsgs).
                  append(" hbt=").append(heartBeat));
          lastCount = countMsgs;
          lastStatus = now;
        }
        // Break the data into MiniSeed and send it out; reuse our ms holder
        if (buf[0] == 'C' && buf[1] == 'M' && buf[2] == 'D') {
          if (processCommand(bbbuf)) {
            bytesIn += 512;
            continue;
          }    // It was written, go on
        }
        if (ms == null) {
          ms = new MiniSeed(buf);
        } else {
          ms.load(buf);
        }
        if (tag.equals("MSSkt:")) {
          tag = tag + ms.getSeedNameSB().substring(0, 8).trim();
        }
        if (ms.getBlockSize() > 512) {
          // If buf is too small, make it bigger and put the first msgLength back into it!
          if (buf.length < ms.getBlockSize()) {
            byte[] buf2 = new byte[buf.length];
            System.arraycopy(buf, 0, buf2, 0, buf.length);
            buf = new byte[ms.getBlockSize()];
            System.arraycopy(buf2, 0, buf, 0, buf2.length);
            ms = new MiniSeed(buf);
          }
          len = ms.getBlockSize() - l;
          while (len > 0) {            // 
            //
            nchar = Util.socketRead(in, buf, l, len);// Get nchar
            if (nchar <= 0) {
              break;     // EOF - close up
            }
            l += nchar;               // Update the offset
            len -= nchar;             // Reduce the number left to read
          }
          if (nchar <= 0) {
            break;       // EOF exit loop
          }
          ms.load(buf);
        }
        bytesIn += ms.getBlockSize();
        if (seedname == null) {
          seedname = ms.getSeedNameSB().substring(0, 7);
        }
        if (waitms > 0) {
          try {
            sleep(waitms);
          } catch (InterruptedException e) {
          }
        }

        if (!ms.isHeartBeat()) {
          nillegal = 0;                 // Must be a legal seed name
          if (dbg || countMsgs % 1000 == 0) {
            //|| (q330check && ms.getSeedNameSB().substring(0,6).equals("USBRAL") && ms.getActivityFlags() > 0.0001 && ms.getNsamp() >0 ))
            parent.prta(Util.clear(runsb).append(tag).
                    append(" ").append(ms.toStringBuilder(null)).append(" ").append(countMsgs));
          }
          try {
            if (q330check && ms.getRate() > 0.00001 && ms.getNsamp() > 0) {
              ns = MiniSeed.getNsampFromBuf(buf, ms.getEncoding());
              if (ns < ms.getNsamp()) {
                parent.prta(Util.clear(runsb).append(tag).append(" *** Q330 ns fail=").append(ns).append(" ms=").append(ms.toStringBuilder(null)));
                SendEvent.debugEvent("Q330BadBlk", tag + "Bad ns " + ns + "!=" + ms.getNsamp() + " " + ms.toStringBuilder(null, 50), this);
                continue;       // Skip putting this data into databases
              }

            }
            if (scn) {
              ms.setLocationCode("  ");
            }

            if (ms.getBlockSize() > 512 && ms.getNsamp() <= 0) {
              parent.prt(Util.clear(runsb).append(tag).append("Skipping non time series block of ").
                      append(ms.getBlockSize()).append(" and no data ms=").append(ms.toStringBuilder(null)));
            } else if (ms.getEncoding() == 10 || ms.getEncoding() == 11) {
              Channel c = EdgeChannelServer.getChannel(ms.getSeedNameSB());
              //if(seedname.indexOf("REPV15")>= 0)  par.prta(seedname+": "+c.toString()+" flags="+c.getFlags()+" mask="+mask+" hydra="+hydra);
              if (c == null) {
                prta(Util.clear(tmpsb).append(tag).append("MSS: ***** new channel found=").append(ms.getSeedNameSB()));
                SendEvent.edgeSMEEvent("ChanNotFnd", "MSS: MiniSEED Channel not found=" + ms.getSeedNameSB() + " new?", this);
                EdgeChannelServer.createNewChannel(ms.getSeedNameSB(), ms.getRate(), this);
              }
              IndexBlock.writeMiniSeedCheckMidnight(ms, false, allow4k, tag, this);  // Do not use Chan Infra for Miniseed data
              if (hydra) {
                Hydra.sendNoChannelInfrastructure(ms);            // Send with no channel Infra
              }              // Send this channel information to the channel server for latency, etc.
              if (csend != null) {
                csend.send(ms.getJulian(),
                        (int) (ms.getTimeInMillis() % 86400000), // Milliseconds
                        ms.getSeedNameSB(), ms.getNsamp(), ms.getRate(), ms.getBlockSize());  // Key, nsamp, rate and nbytes
              }
            } else if (MiniSeed.isSupportedEncoding(ms.getEncoding(), fscale)) {   // SRO and 2, 3, and 4 bytes ints can be interpretted here
              if (samples == null) {
                samples = new int[ms.getNsamp() * 2];
              }
              if (ms.getNsamp() > samples.length) {
                samples = new int[ms.getNsamp() * 2];
              }
              double rate = ms.getRate();
              if (ms.getBlockette100() != null) {
                if (b100 == null) {
                  b100 = new byte[12];
                }
                if (bb100 == null) {
                  bb100 = ByteBuffer.wrap(b100);
                }
                System.arraycopy(ms.getBlockette100(), 0, b100, 0, 12); // copy the whole blockette 100
                bb100.position(4);      // put at the floating rate
                rate = bb100.getFloat();
                parent.prta("Setting rate to blockette100 rate=" + rate + " ms.rate()=" + ms.getRate());
              }
              try {
                data = ms.decomp(samples);
                gc.setTimeInMillis(ms.getTimeInMillis());
                RawToMiniSeed.addTimeseries(data, ms.getNsamp(), ms.getSeedNameSB(),
                        gc.get(Calendar.YEAR), gc.get(Calendar.DAY_OF_YEAR),
                        (int) ((ms.getTimeInMillis() % 86400000L) / 1000), 
                        ms.getUseconds(),
                        rate, ms.getActivityFlags(), ms.getIOClockFlags(),
                        ms.getDataQualityFlags(), ms.getTimingQuality(), parent);
              } catch (SteimException e) {
                parent.prt("*** SteimException - skip data ms=" + ms);
              }

            } else if (ms.getSeedNameSB().charAt(7) != 'L' || ms.getSeedNameSB().charAt(8) != 'O'
                    || ms.getSeedNameSB().charAt(10) != 'G') {
              parent.prta("*** Skip Unsupported data encoding=" + ms.getEncoding() + " ms=" + ms);
            }
          } catch (IllegalSeednameException e) {
            parent.prt(Util.clear(runsb).append(tag).append("Illegal seedname should be impossible here. ms.sn=").append(ms.getSeedNameSB()));
          }
        } else {
          heartBeat++;
        }
      } catch (IOException e) {
        if (e.getMessage().equalsIgnoreCase("Socket closed") && terminate) {
          parent.prta(Util.clear(runsb).append(tag).append(" Doing termination via Socket close."));
        } else {
          Util.IOErrorPrint(e, "MSS: receive through IO exception", parent.getPrintStream());
        }
        break;      // Drop out of read loop to connect looop
      } catch (IllegalSeednameException e) {
        nillegal++;
        if (ms != null) {
          parent.prta(Util.clear(runsb).append(tag).append(" IllegalSeedName =").
                  append(nillegal).append(" ").append(Util.toAllPrintable(ms.getSeedNameSB())).
                  append(" ").append(e.getMessage()));
        } else {
          parent.prta(tag + " IllegalSeedName =" + nillegal + " ms is null. "
                  + e.getMessage());
        }
        for (int i = 0; i < 48; i++) {
          parent.prt(i + " = " + buf[i] + " " + (char) buf[i]);
        }
        if (nillegal > 3) {
          terminate = true;    // If 3 in a row, then close connection
        }
      } catch (RuntimeException e) {
        parent.prt(Util.clear(runsb).append(tag).append(" RuntimeException : ").append(e.getMessage()).
                append(" nc=").append(nchar).append(" len=").append(len).append(" l=").append(l).
                append(" ns=").append(nsamp).append(" nleft=").append(nsleft));
        if (data == null) {
          parent.prt(Util.clear(runsb).append(tag).append("      data=null"));
        } else {
          parent.prt(Util.clear(runsb).append(tag).append("      data.length=").append(data.length));
        }
        parent.prt(Util.bytesToSB(buf, 48, runsb));
        e.printStackTrace(parent.getPrintStream());
        terminate = true;
        break;
      }

    }     // while(true) Get data
    try {
      if (nchar <= 0) {
        parent.prta(Util.clear(runsb).append(tag).append(" exited due to EOF after ").append(countMsgs).
                append(" pkts lastMS=").append(ms == null ? "null" : ms.toStringBuilder(null)));
      } else if (terminate) {
        parent.prt(Util.clear(runsb).append(tag).append(" terminate found."));
      } else {
        parent.prta(Util.clear(runsb).append(tag).append(" exited while(true) for read"));
      }
      if (d != null) {
        d.close();
      }
    } catch (IOException e) {
      Util.IOErrorPrint(e, tag + " socket- reopen", parent.getPrintStream());
    } catch (RuntimeException e) {
      if (parent != null) {
        parent.prta(Util.clear(runsb).append("MSS: Runtime during exit e=").append(e));
      }
      e.printStackTrace(parent.getPrintStream());
    }
    parent.prta(Util.clear(runsb).append(tag).append(" is terminated msgs = ").append(countMsgs).
            append(" ").append((int) (countMsgs / ((System.currentTimeMillis() - openTime) / 1000.))).append(" msg/s"));
    running = false;
    terminate = false;
  }

  /**
   * Return the monitor string for Icinga.
   *
   * @return A String representing the monitor key value pairs for this
   * EdgeThread.
   */
  long lastMonCountMsgs;
  long lastMonBytesIn;

  @Override
  public StringBuilder getMonitorString() {
    if (monitorsb.length() > 0) {
      monitorsb.delete(0, monitorsb.length());
    }
    long np = countMsgs - lastMonCountMsgs;
    long nb = bytesIn - lastMonBytesIn;
    lastMonCountMsgs = countMsgs;
    lastMonBytesIn = bytesIn;
    return monitorsb.append(tag).append("-NPacket=").append(np).append("\n").append(tag).
            append("-BytesIn=").append(nb).append("\n");
  }

  @Override
  public StringBuilder getStatusString() {
    if (statussb.length() > 0) {
      statussb.delete(0, statussb.length());
    }
    return statussb.append(tag).append(" #rcv=").append(countMsgs).append(" hb=").append(heartBeat);
  }

  @Override
  public StringBuilder getConsoleOutput() {
    return consolesb;
  } //  We use parent.prt directly

  /**
   * Test routine.
   *
   * @param args The command line arguments.
   */
  public static void main(String[] args) {
    Util.setModeGMT();
    IndexFile.init();
    EdgeProperties.init();
    RawDisk in;
    int len = 512;
    byte[] b = new byte[512];
    int iblk = 0;
    int l = 0;
    MiniSeed ms = null;
    MiniSeedServer mss = new MiniSeedServer("-p 2015", "msrv");
    EdgeBlockQueue ebq = new EdgeBlockQueue(10000);
    try {
      Socket s = new Socket("localhost", 2015);
      OutputStream out = s.getOutputStream();
      in = new RawDisk("/Users/ketchum/IUANMO_BHZ00.ms", "r");
      while ((l = in.readBlock(b, iblk, len)) > 0) {
        ms = new MiniSeed(b);
        //Util.parent.prt("ms="+ms.toString());
        out.write(b, 0, len);
        iblk++;
      }
      Util.prt("Blks written=" + iblk);
      System.exit(0);
    } catch (IOException e) {
      Util.IOErrorPrint(e, "IOException caught in main iblk=" + iblk);
    } catch (IllegalSeednameException e) {
      Util.prt("Illegal seedname" + e.getMessage());
    }
    LISSClient[] liss = new LISSClient[10];
    IndexFile.setDebug(true);
    int i = 0;

  }

}
