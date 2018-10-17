
/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.edgeoutput;

import gov.usgs.alarm.SendEvent;
import gov.usgs.anss.edge.IllegalSeednameException;
import gov.usgs.anss.edge.RawDisk;
import gov.usgs.anss.edgethread.EdgeThread;
import gov.usgs.anss.edgethread.ManagerInterface;
import gov.usgs.anss.seed.MiniSeed;
import gov.usgs.anss.util.SeedUtil;
import gov.usgs.anss.util.Util;
import gov.usgs.edge.config.Channel;
import gov.usgs.edge.config.Sendto;
import java.io.IOException;
import java.io.FileNotFoundException;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.GregorianCalendar;

/**
 * This class writes data scheduled to go to a IRIS ringserver (thanks Chad T) and has extended to
 * allow sending raw data to a QueryMom based DataLinkToQueryServer. It opens a port to a DataLink
 * port on a ringserver and writes each qualified block as described by the edge/cwb sendto system
 * to this port.
 * <br>
 * Basic design : The each time a block come in it is put in a short memory queue to buffer data on
 * the TCP/IP link. If the queue is full, the blocks are written to a buffer file.
 * <p>
 * The sender thread is in either file or queue mode. If the file is exhausted it is zapped, and
 * data is sent from the queue. If neither is available, sleep and wait for some.
 * <p>
 * This thread is created by the OutputInfrastructire to instantiate feeds to the ring servers or
 * memory buffers in QueryMom.
 * <PRE>
 * NOTE: for feeding a QueryMom memory cache the normal arguments are :
 * -allowram -h 127.0.0.1 -p 16099 -qsize 2000 -file /data2/ram.queue
 * switch  argsOrg     Description
 * -h     ipadr    The address of the ringserver datalink  (def=localhost)
 * -p     port     The datalink port number on the ringserver (def=18002)
 * -allowlog       If set LOG records will go out (normally not a good idea)
 * -qsize nnnn     The number of miniseed blocks to buffer through the in memory queue (512 bytes each, def=1000)
 * -file  filenam  The filename of the file to use for backup buffering (def=ringserver.queued)
 * -maxdisk nnn    Maximum amount of disk space to use in mB (def=1000 mB).
 * -allowrestricted true  If argument is 'true', then allow restricted channels
 * -allowraw       This must be set if this RSSL is being used to send raw data to a QueryMom DLTQS.
 * -xinst          A regular expression of instances that are blocked output blocked
 * -dbg            Set more voluminous log output
 * -dbgchan        Channel tag to match with contains
 * </PRE>
 *
 * @author U.S. Geological Survey <ketchum at usgs.gov>
 */
public final class RingServerSeedLink extends EdgeOutputer implements ManagerInterface {

  private RawDisk dsk;        // this is the backup queue if the link to ring server is down
  private int disknextout;        // number of blocks in the output file, if zero, it is not in use
  private int disknextin;
  private String filename;    // backup filename
  private int maxdisk;        // maximumd disk size in MB
  private int discardDisk;
  //private byte [] b;          // 512 bytes of scratch space
  //private ByteBuffer bb;      // This wraps the b[]
  private boolean terminate;
  private boolean sendLog;
  private String tag;
  private Socket s;
  private long mask;                  // This is the sendto bit mask for this output type
  private long disableMask;
  private boolean noOutput;
  private RingServerOutputter outputter;
  private boolean dbg;
  private String dbgchan = "ZZZZZZZZZ";
  private final String orgArgs;
  // memory queue related 
  private boolean acking;
  private int qsize;
  private final ArrayList<byte[]> queue = new ArrayList<>(100);
  private final Integer mutex = Util.nextMutex();
  private int nextin;           // The last place filled by input data (next block goes in nextin+1)
  private int nextout;          // next block to be written by output queue (so if nextin==nextout queue is full)
  private Sendto sendto;        // Use this sendto object to control behavior
  private MiniSeed org;
  private final EdgeThread par;     // parent for logging
  // Raw buffer variables
  private boolean allowraw;
  private byte[] rawbuf;
  private ByteBuffer raw;
  private final byte[] seednamebuf = new byte[12];
  private final StringBuilder tmpsb = new StringBuilder(100);
  private final StringBuilder thrsb = new StringBuilder(100);
  private final int[] timescr = new int[4];
  private int used;
  private int maxused;
  private long lastOutputCheck;
  private int lastNextoutCheck;
  private long nrestricted;
  private String host;
  private int port;
  private String argsOrg;
  private final StringBuilder dbgseed = new StringBuilder(12);
  private boolean check;

  @Override
  public long getBytesIn() {
    return 0;
  }

  @Override
  public boolean isRunning() {
    return !terminate;
  } // not a thread

  @Override
  public boolean isAlive() {
    return !terminate;
  } // not a thread

  @Override
  public void setCheck(boolean t) {
    check = t;
  }

  @Override
  public boolean getCheck() {
    return check;
  }

  public int getPort() {
    return port;
  }

  public String getHost() {
    return host;
  }

  @Override
  public long getBytesOut() {
    return outputter.getNBytesOut();
  }

  @Override
  public String getArgs() {
    return orgArgs;
  }

  @Override
  public String toString() {
    return toStringBuilder(null).toString();
  }

  @Override
  public StringBuilder toStringBuilder(StringBuilder tmp) {
    StringBuilder sb = tmp;
    if (sb == null) {
      sb = Util.clear(tmpsb);
    }
    synchronized (sb) {
      sb.append("RSSL: ").append(host).append("/").append(port).append(" ").append(" ").
              append(filename).append(" dnxt=").append(disknextout).
              append(" msk=").append(Util.toHex(mask)).append(" allowRestricted=").append(allowRestricted).
              append(" ").append(outputter.getStatusSB());
    }
    return sb;
  }

  @Override
  public String getFilename() {
    return filename;
  }

  public void setDebug(boolean b) {
    prta("RSSL: Set debug to " + b);
    dbg = b;
  }

  @Override
  public void terminate() {
    prta(toString() + " call terminate().");
    //new RuntimeException("Terminate of RSSL").printStackTrace(par.getPrintStream());
    terminate = true;
    outputter.interrupt();
    if (s != null) {
      if (!s.isClosed()) {
        try {
          s.close();
        } catch (IOException e) {
        }
      }
    }
  }

  public long getSendtoMask() {
    return mask;
  }

  public final void setSendto(Sendto sendto) {
    this.sendto = sendto;
    this.mask = 1 << (sendto.getID() - 1);
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

  /**
   * Creates a new instance of RingServerSeedLink. This is a EdgeOutputer created and called by the
   * OutputInfrastructure class. It takes the MiniSeed blocks and sends them on a DataLink to a
   * ringserver instances. The data normally just go into a memory based Queue for buffering.
   * However, if this queue is full, a file will be created to take the blocks. This file will only
   * be read back when the queue is empty until the file is empty and then everything goes back to
   * normal. This constructor was used by the original OutputInfrastructure class where
   * configuration file was not used.
   *
   * @param sendto This sendto object contains the allowrestricted and arguments strings
   * @param tag A tag for logging
   * @param parent EdgeThread for logging
   * @throws FileNotFoundException if the file cannot be opened
   * @throws IOException If one occurs.
   */
  public RingServerSeedLink(Sendto sendto, String tag, EdgeThread parent)
          throws FileNotFoundException, IOException {
    super(sendto.allowRestricted());
    par = parent;
    String[] args = sendto.getArgs().split("\\s");
    orgArgs = sendto.getArgs();
    this.tag = tag;
    setSendto(sendto);
    host = "localhost";
    port = 18002;
    filename = "ringserver.queued";
    sendLog = false;
    qsize = 1000;
    maxdisk = 2000000;      // default 1 GB of disk space
    for (int i = 0; i < args.length; i++) {
      if (args[i].equals("-h")) {
        host = args[i + 1];
        i++;
      } else if (args[i].equals("-p")) {
        port = Integer.parseInt(args[i + 1]);
        i++;
      } else if (args[i].equals("-file")) {
        filename = args[i + 1];
        i++;
      } else if (args[i].equals("-allowlog")) {
        sendLog = true;
      } else if (args[i].equals("-qsize")) {
        qsize = Integer.parseInt(args[i + 1]);
        i++;
      } else if (args[i].equals("-allowraw")) {
        allowraw = true;
      } else if (args[i].equals("-dbg")) {
        dbg = true;
      } else if (args[i].equals("-ack")) {
        acking = true;
      } else if (args[i].equalsIgnoreCase("-xinst")) {
        if (EdgeThread.getInstance().matches(args[i + 1])) {
          noOutput = true;
        }
        i++;
      } else if (args[i].equalsIgnoreCase("-allowrestricted")) {
        allowRestricted = args[i + 1].equalsIgnoreCase("true");
        i++;
      } else if (args[i].equals("-maxdisk")) {
        maxdisk = Integer.parseInt(args[i + 1]) * 2000;
        i++;
      } else if (args[i].trim().equals("")) {
        prta(Util.clear(tmpsb).append(tag).append(" *** unknown switch i=").append(i).append(" ").append(args[i]));
      }
    }
    init();
  }

  private void init() throws FileNotFoundException, IOException {
    // allocate the queue
    for (int i = 0; i < qsize; i++) {
      queue.add(new byte[512]);
    }
    this.tag = tag + ":" + host + "/" + port;
    nextin = qsize - 1;
    nextout = qsize - 1;    // There is nothing int the queue
    filename = filename + Util.getNode();
    dsk = new RawDisk(filename, "rw");
    disknextin = 0;
    if (dsk.length() == 0) {   // is it a new file
      disknextout = 0;
      prta(Util.clear(tmpsb).append(tag).append("Start new RingServerSeedLink=").append(filename).
              append(" nextout=0 allowrestricted=").append(allowRestricted).append(" noutput=").append(noOutput));
    } else {                    // existing file, check its length and get disknextout and size
      disknextout = (int) (dsk.length() / 512);
      if (disknextout > 10000) {
        prta(Util.clear(tmpsb).append(tag).append(" **** Start new RingServer has huge ").append(filename).
                append(" diskout=").append(disknextout).append(" len=").append(dsk.length()).append(" Zeroing file"));
        SendEvent.edgeSMEEvent("RSSLBigStart", "Zeroing " + filename + " is big nxtout=" + disknextout + " len=" + dsk.length(), "RingServerSeedLink");
        dsk.setLength(0);
        disknextout = 0;
      }

      prta(Util.clear(tmpsb).append(tag).append("Pickup old ring.queue=").append(filename).
              append(" nextout=").append(disknextout).append(" allowrestricted=").append(allowRestricted).append(" noutput=").append(noOutput));

    }

    // Add this to static list of 
    prt(Util.clear(tmpsb).append(tag).append("RingServerSeedLink started file=").append(filename).
            append(" mask=").append(Util.toHex(mask)).append(" disable=").append(Util.toHex(disableMask)).
            append(" qsize=").append(qsize).append(" ").append(host).append("/").append(port).
            append(" allowraw=").append(allowraw).append(" maxdisk=").append(maxdisk).
            append(" allowrestrict=").append(allowRestricted).append(" dbgch=").append(dbgchan).append(" noutput=").append(noOutput));
    //if(sendLog) MiniSeedLog.setRLISSOutputer(this);
    outputter = new RingServerOutputter();
  }

  public RingServerSeedLink(String argline, String tag, EdgeThread parent)
          throws FileNotFoundException, IOException {
    this(argline, tag, true, parent);
  }

  /**
   * Creates a new instance of RingServerSeedLink. This is a EdgeOutputer created and called by the
   * OutputInfrastructure class. It takes the MiniSeed blocks and sends them on a DataLink to a
   * ringserver instances. The data normally just go into a memory based Queue for buffering.
   * However, if this queue is full, a file will be created to take the blocks. This file will only
   * be read back when the queue is empty until the file is empty and then everything goes back to
   * normal.
   * <p>
   * <PRE>
   * The possible arguments are :
   *
   * switch argsOrg Description -h ipadr The address of the ringserver datalink -p port The datalink
   * port number on the ringserver -allowlog If set LOG records will go out (normally not a good
   * idea) -qsize nnnn The number of miniseed blocks to buffer through the in memory queue -file
   * filenam The filename of the file to use for backup buffering (def=ringserver.queued) -maxdisk
   * nnn Maximum amount of disk space to use in mB. -allowrestricted true If argument is word "true"
   * then restricted is allowed -allowlog If set LOG records will go out (normally not a good idea)
   * -dbg Set more voluminous log output
   * </PRE>
   *
   * @param argline The arguments to here
   * @param tag A logging tag
   * @param allowrestricted Whether restricted output is allowed, this can be overriden with command
   * line argument "-allowrestricted true"
   * @param parent A logging EdgeThread.
   * @throws FileNotFoundException
   * @throws IOException
   */
  public RingServerSeedLink(String argline, String tag, boolean allowrestricted, EdgeThread parent)
          throws FileNotFoundException, IOException {
    super(allowrestricted);
    par = parent;
    String[] args = argline.split("\\s");
    orgArgs = argline;
    host = "localhost";
    port = 16099;
    filename = "ringserver.queued";
    sendLog = false;
    qsize = 1000;
    maxdisk = 20000;      // default 10 Mb of disk space
    allowraw = true;
    sendLog = false;
    for (int i = 0; i < args.length; i++) {
      if (args[i].equals("-h")) {
        host = args[i + 1];
        i++;
      } else if (args[i].equals("-p")) {
        port = Integer.parseInt(args[i + 1]);
        i++;
      } else if (args[i].equals("-file")) {
        filename = args[i + 1];
        i++;
      } else if (args[i].equals("-qsize")) {
        qsize = Integer.parseInt(args[i + 1]);
        i++;
      } else if (args[i].equals("-dbg")) {
        dbg = true;
      } else if (args[i].equals("-m")) {
        mask = Long.parseLong(args[i + 1]);
        i++;
      } else if (args[i].equals("-dm")) {
        disableMask = Long.parseLong(args[i + 1]);
        i++;
      } else if (args[i].equals("-ack")) {
        acking = true;
      } else if (args[i].equals("-maxdisk")) {
        maxdisk = Integer.parseInt(args[i + 1]) * 2000;
        i++;
      } else if (args[i].equals("-allowlog")) {
        sendLog = true;
      } else if (args[i].equalsIgnoreCase("-xinst")) {
        if (EdgeThread.getInstance() != null) {
          if (EdgeThread.getInstance().matches(args[i + 1])) {
            noOutput = true;
          }
        }
        i++;
      } else if (args[i].equalsIgnoreCase("-allowrestricted")) {
        allowRestricted = args[i + 1].equalsIgnoreCase("true");
        i++;
      } else if (args[i].equals("-dbgch")) {
        dbgchan = args[i + 1].replaceAll("_", " ").replaceAll("-", " ");
        i++;
      } else if (args[i].trim().equals("")) {
        prta(Util.clear(tmpsb).append(tag).append(" *** unknown switch i=").append(i).append(" ").append(args[i]));
      }
    }
    init();     // open the files etc.
  }

  /**
   * process a block through the EdgeOutputer system
   *
   * @param msbuf buffer of bytes containing miniseed data
   * @param c A channel for the seedname in the edgeblock
   */
  @Override
  public synchronized void processBlock(byte[] msbuf, Channel c) {
    if (noOutput) {
      return;
    }
    MiniSeed.crackSeedname(msbuf, dbgseed);
    if (mask == 0) {
      prta(Util.clear(tmpsb).append(tag).append("Call to processBlock in QueryMom mode - illegal"));
      return;
    }
    if (c == null) {
      // prta("Write LOG rec to "+MiniSeed.crackSeedname(msbuf)+" file="+filename);
      if (msbuf[15] == 'L' && msbuf[16] == 'O' && msbuf[17] == 'G' && sendLog) {
        prta(Util.clear(tmpsb).append(tag).append(" log sends not implemented! ").append(MiniSeed.crackSeedname(msbuf))); // writeNext(msbuf, 0 , 512, 0L, 0L);
      }
      return;
    }
    if ((c.getSendtoMask() & mask) == 0L) {      // This is not on this output method! 
      if (dbgseed.indexOf(dbgchan) >= 0) {
        Util.clear(tmpsb).append(tag).append(dbgseed).append(" channel not enabled").append(Util.toHex(c.getSendtoMask())).append(" ").append(Util.toHex(mask));
      }
      return;
    } else {
      if ((c.getFlags() & disableMask) == 0) {   // is it not disabled from this output method
        if ((c.getFlags() & 16) != 0) {        // is this public restricted
          //prt("Got a restricted channel to "+filename+" c="+c);

          if (!allowRestricted) {                // Is this outputer allowed restricted data
            if (nrestricted++ % 100 == 1) {
              prta(Util.clear(tmpsb).append(tag).append(" Got a restricted channel to RingServerSeedlink reject it.").append(filename).
                      append(" c=").append(c).append(" SEED=").append(MiniSeed.crackSeedname(msbuf)).
                      append(" allowRestricted=").append(allowRestricted).append(" #res=").append(nrestricted));
              SendEvent.debugEvent("ChanRestrict", "Restricted channel to " + filename + " " + MiniSeed.crackSeedname(msbuf), "RingServerSeedLink");
            }
            return;
          }
        }
      }
    }
    try {
      if (msbuf.length > 512) {      // This must be miniseed block that needs to be broken up
        try {
          if (org == null) {
            org = new MiniSeed(msbuf);
          }
          org.load(msbuf);
          prta(Util.clear(tmpsb).append(tag).append(filename).append(" break up to 512 ").append(org));
          MiniSeed[] mss = org.toMiniSeed512();
          for (MiniSeed ms : mss) {
            int[] time = MiniSeed.crackTime(ms.getBuf());
            //prt(i+" "+time[0]+":"+time[1]+":"+time[2]+" ms="+mss[i]);
            if (time[0] >= 24 || time[1] >= 60 || time[2] >= 60) {
              prt(tag + "***** bad time code in broken up packet!");
            }
            writeNext(ms.getBuf(), 0, 512, c.getSendtoMask(), c.getFlags());
          }
        } catch (IllegalSeednameException e) {
          prta(Util.clear(tmpsb).append(tag).append("Got illegal seedname in SeedLinkRingServer - discard ").append(e.getMessage()));
        }
      } else {
        if (dbgseed.indexOf(dbgchan) >= 0) {
          Util.clear(tmpsb).append(tag).append(dbgseed).append(" channel sent ").append(Util.toHex(c.getSendtoMask())).append(" ").append(mask);
        }
        writeNext(msbuf, 0, msbuf.length, c.getSendtoMask(), c.getFlags());
      }
    } catch (IOException e) {
      prta(Util.clear(tmpsb).append(tag).append(" Got IO error writing disk file e=").append(e));
      Util.IOErrorPrint(e, tag + "RF: writing to ring.queue=" + toString());
      SendEvent.edgeEvent("ErrWrDisk", tag + "RingServerSeedlink Queue wr err " + filename, this);
      nextout++;
    }
  }

  /**
   * Process a raw data block to a DataLink - this is only useful for populating the RAM QuerySpan
   * portion of a QueryMom. This RingServerSeedLink must be specifically enabled to send raw data
   * (-allowraw). If this is not true, any calls to this routine are ignored. This capability of
   * sending raw data is an extension of the DataLink Protocol added by the Edge/CWB project, so
   * this kind of data should never be sent to a IRIS ringserver.
   * <p>
   * Since the source of raw data might have more than 100 samples, this routine will break any
   * larger number of samples into a series of WRITE /RAW blocks to the DataLink. The data are
   * queued with the MiniSeed blocks so the total size limit of a RAW packet is 512 bytes.
   *
   * @param c A channel object so configuration info like sendto masks etc are available for this
   * channel
   * @param seedname The 12 character NNSSSSSCCCLL of the time series bit being inserted raw.
   * @param time The millis since 1970 with the start time of the first sample
   * @param rate Digitizing rate in Hz.
   * @param nsamp The number of data samples in data[]
   * @param data The data samples.
   */
  @Override
  public synchronized void processRawBlock(Channel c, StringBuilder seedname, long time, double rate, int nsamp, int[] data) {
    if (noOutput) {
      return;
    }
    if (dbg || seedname.indexOf(dbgchan) >= 0) {
      prta(Util.clear(tmpsb).append(tag).append("ProcessRawBlock ").append(seedname).append(" ").
              append(Util.ascdatetime2(time, null)).append(" rt=").append(rate).
              append(" ns=").append(nsamp).append(" c.mask=").
              append(c == null ? "null" : c.getSendtoMask()).append(" mask=").append(mask).append(" allowraw=").append(allowraw));
    }
    if (c == null || !allowraw) {
      return;
    }
    if (mask == 0) {
      prta(Util.clear(tmpsb).append(tag).append("Call to processRawBlock in QueryMom mode - illegal"));
      return;
    }
    if ((c.getSendtoMask() & mask) == 0L) {      // This is not on this output method! 
      return;
    } else {
      if ((c.getFlags() & disableMask) == 0) {   // is it not disabled from this output method
        if ((c.getFlags() & 16) != 0) {        // is this public restricted
          //prt("Got a restricted channel to "+filename+" c="+c);
          if (!allowRestricted) {                // Is this outputer allowed restricted data
            if (nrestricted++ % 100 == 1) {
              prta(Util.clear(tmpsb).append(tag).append(" Got a restricted channel to RingServerSeedLink reject it.").
                      append(filename).append(" c=").append(c).append(" SEED=").append(seedname).
                      append(" allowRestricted=").append(allowRestricted).append(" #res=").append(nrestricted));
              SendEvent.debugEvent("ChanRestrict", "Restricted channel to " + filename + " " + seedname, "RingServerSeedLink");
            }
            return;
          }
        }
      }
    }
    processRawBlockQM(seedname, time, rate, nsamp, data);   // Write the raw data to the QueryMom
  }

  /**
   * Process a raw data block to a DataLink - this is only useful for populating the RAM QuerySpan
   * portion of a QueryMom when not using the OutputInfrastructure system. This is mainly used by
   * the LHDerivedToQueryMom. This capability of sending raw data is an extension of the DataLink
   * Protocol added by the Edge/CWB project, so this kind of data should never be sent to a IRIS
   * ringserver.
   * <p>
   * Since the source of raw data might have more than 100 samples, this routine will break any
   * larger number of samples into a series of WRITE /RAW blocks to the DataLink. The data are
   * queued with the MiniSeed blocks so the total size limit of a RAW packet is 512 bytes.
   *
   * @param seedname The 12 character NNSSSSSCCCLL of the time series bit being inserted raw.
   * @param time The millis since 1970 with the start time of the first sample
   * @param rate Digitizing rate in Hz.
   * @param nsamp The number of data samples in data[]
   * @param data The data samples.
   */
  public void processRawBlockQM(StringBuilder seedname, long time, double rate, int nsamp, int[] data) {
    if (noOutput) {
      return;
    }
    if (terminate) {
      return;
    }
    if (raw == null) {
      rawbuf = new byte[512];
      raw = ByteBuffer.wrap(rawbuf);
    }
    //if(seedname.charAt(7) == 'b') 
    //    prta("processRawBlockQM "+seedname+" "+Util.ascdatetime2(time, null)+" rt="+rate+" ns="+nsamp+" to "+host+"/"+port+"/"+s.getLocalPort());
    /* the buffer is 
     * off   type      description
     * 0    byte      27 - first lead in
     * 1    byte      3 - the 2nd lead in
     * 2    short     Number of samples
     * 4    String12  The NNSSSSSCCCLL 
     * 16   long      String time in millis
     * 24    double   rate
     * 32    int[]    Data samples
     */
    int start = 0;
    int ns;
    for (int i = 0; i < seednamebuf.length; i++) {
      seednamebuf[i] = 32;    // assume spaces
    }
    for (int i = 0; i < seedname.length(); i++) {
      seednamebuf[i] = (byte) seedname.charAt(i);// copy the string to the bytes
    }
    while (start < nsamp) {    // until all samples are sent.
      raw.position(0);
      raw.put((byte) 27);       // leadin
      raw.put((byte) 3);
      ns = nsamp - start;
      if (ns > 100) {
        ns = 100;
      }
      raw.putShort((short) (ns * (acking ? -1 : 1)));  // 2 - number of data samples
      raw.put(seednamebuf);  // 4  Tje NNSSSSSCCCLL
      long now = time + (long) (start / rate * 1000. + 0.001);  // Time of first sample in this packet
      raw.putLong(now);            // 16
      raw.putDouble(rate);          // 24 - digit rate
      for (int i = 0; i < ns; i++) {
        raw.putInt(data[i + start]);// starting at 32 the data samples.
      }
      start += ns;
      synchronized (queue) { // Insure disknextout does not get zapped while we are working on it, queue to memory or disk
        int next = (nextin + 1) % qsize;
        if (next == nextout || disknextout > 0) {  // if memory just got full or disk is being written
          // did not queue to memory, so write it to disk
          if (dbg) {
            prta(Util.clear(tmpsb).append(tag).append("Queue to disk blk=").append(disknextout).
                    append(" ").append(seedname).append(" ns=").append(ns).append(" ").append(Util.ascdatetime2(now)));
          }
          try {
            if (disknextout < maxdisk) {
              dsk.writeBlock(disknextout++, rawbuf, 0, raw.position());
              if (disknextout % 1000 == 999) {
                prta(Util.clear(tmpsb).append(tag).append(" * MS disk file getting bigger.  ").
                        append(filename).append(" size=").append(disknextout).append(" blks"));
              }
              if (disknextout % 100000 == 99999) {
                prta(Util.clear(tmpsb).append(tag).append(" ****** RAW disk file looks to big file=").
                        append(filename).append(" blks=").append(disknextout));
                SendEvent.edgeSMEEvent("RSSLBigDisk", filename + " RAW  is bigger than 50 mB=" + disknextout
                        + " blks", "RingServerSeedLink");
              }
            } else {
              discardDisk += disknextout;
              disknextout = 0;        // Start the disk nextout again
              disknextin = 0;
              SendEvent.edgeSMEEvent("RSSLDiskFull", tag + "Total disk discards=" + discardDisk
                      + " " + filename, this);
              prta(Util.clear(tmpsb).append(tag).append(" ***** RAM DISK reset out file=").
                      append(filename).append(" max=").append(maxdisk).
                      append("  discards=").append(discardDisk));
            }
          } catch (IOException e) {
            SendEvent.edgeSMEEvent("IOErrRSSL", "IOError writing to " + dsk.getFilename() + " from RingServerSeedLink", "RingServerSeedLink");
            disknextout = 0;
            disknextin = 0;
            try {
              dsk.setLength(0L);
            } catch (IOException e2) {
            }
          }
        } else {
          // Put it in Queue
          if (dbg) {
            prta(Util.clear(tmpsb).append(tag).append("Queue to memory next=").append(next).append(" ").
                    append(seedname).append(" ns=").append(ns).append(" ").
                    append(Util.ascdatetime2(now, null)));
          }
          System.arraycopy(rawbuf, 0, queue.get(next), 0, raw.position());
          synchronized (mutex) {
            nextin = next;
          }
        }
      }
    }   // While more samples
  }

  /**
   * Write to this file if the sendto mask indicates this is the right file
   *
   * @param b The data buffer to write
   * @param off The offset in buffer to start write
   * @param len The number of bytes to write
   * @param sendto The raw sendto bit mask for this block
   * @param flags The channel flags, (which might be used to disable this temporarily)
   * @throws java.io.IOException
   */
  public synchronized void writeNext(byte[] b, int off, int len, long sendto, long flags) throws IOException {
    if (used > qsize / 4) {
      if ((System.currentTimeMillis() - lastOutputCheck) > 60000) {
        lastOutputCheck = System.currentTimeMillis();
        if (lastNextoutCheck == nextout) {
          prta(Util.clear(tmpsb).append(tag).append(" used=").append(used).append("/").append(qsize).
                  append(" *** nextout not making progress.  reopen socket connection nextout=").append(nextout));
          outputter.reopen();
        }
        lastNextoutCheck = nextout;
      }
    }

    if ((sendto & mask) != 0) {
      if ((flags & disableMask) == 0) {
        synchronized (queue) {
          int next = (nextin + 1) % qsize;
          if (next == nextout || disknextout > 0) {  // if memory just got full or disk is being written
            // did not queue to memory, so write it to disk
            if (dbg) {
              try {
                MiniSeed.crackTime(b, timescr);
                prta(Util.clear(tmpsb).append(tag).append("Queue to disk MS blk=").append(disknextout).
                        append(" ").append(MiniSeed.crackSeedname(b)).append(" ").append(MiniSeed.crackDOY(b)).
                        append(" ").append(Util.df2(timescr[0])).append(":").append(Util.df2(timescr[1])).append(":").
                        append(Util.df2(timescr[2])).append(".").append(Util.df4(timescr[3])));
              } catch (IllegalSeednameException e) {
              }
            }
            if (disknextout < maxdisk) {
              dsk.writeBlock(disknextout++, b, 0, len);
              if (disknextout % 1000 == 999) {
                prta(Util.clear(tmpsb).append(tag).append(" * disk file getting bigger.  ").
                        append(filename).append(" size=").append(disknextout).append(" blks"));
              }
              if (disknextout % 100000 == 99999) {
                prta(Util.clear(tmpsb).append(tag).append(" ***** disk file looks to big blks=").append(disknextout));
                SendEvent.edgeSMEEvent("RSSLBigDisk", filename + " is bigger than 50 mB=" + disknextout + " blks", "RingServerSeedLink");
              }
            } else {
              discardDisk += disknextout;
              disknextout = 0;
              disknextin = 0;
              SendEvent.edgeSMEEvent("RSSLDiskFull", tag + "Total disk file=" + filename + " discards=" + discardDisk, this);
              prta(Util.clear(tmpsb).append(tag).append(" *** file=").append(filename).
                      append(" reset.  discards=").append(discardDisk));
            }
          } else {
            // Put it in Queue
            if (dbg) {
              try {
                MiniSeed.crackTime(b, timescr);
                prta(Util.clear(tmpsb).append(tag).append("Queue to memory MS blk=").append(next).append(" ").
                        append(MiniSeed.crackSeedname(b)).append(" ").append(MiniSeed.crackDOY(b)).
                        append(" ").append(Util.df2(timescr[0])).append(":").append(Util.df2(timescr[1])).
                        append(":").append(Util.df2(timescr[2])).append(".").append(Util.df4(timescr[3])));
              } catch (IllegalSeednameException e) {
              }
            }
            System.arraycopy(b, off, queue.get(next), 0, len);
            synchronized (mutex) {
              nextin = next;
            }
            used = nextin - nextout;
            if (used < 0) {
              used += qsize;
            }
            if (used > maxused) {
              maxused = used;
            }
            if (nextin % 10000 == 0) {
              prta(Util.clear(tmpsb).append(tag).append(" Queue to memory nextin=").append(nextin).
                      append(" nextout=").append(nextout).append(" used=").append(used).append(" max=").append(maxused));
            }
          }

        }
      }
    }
  }

  @Override
  public synchronized void close() {
    terminate();
  }

  /**
   * This class is internat to the RingServerSeedLink and takes data from the Memory or disk file
   * and sends it to a DataLink receiver. This routine implements parts of the /RAW datalink
   * extension used by the Edge/CWB to QueryMoms.
   */
  public final class RingServerOutputter extends Thread {

    private final byte[] buf = new byte[512];
    private final byte[] dlbuf = new byte[100];
    private final ByteBuffer dl;
    private byte[] sendbuf;
    private long npack;
    private long nbytesOut;
    private long lastOpen;
    private final StringBuilder runsb = new StringBuilder(100);
    private final StringBuilder statsb = new StringBuilder(100);
    AckReader ackReader;

    public long getNBytesOut() {
      return nbytesOut;
    }

    public void reopen() {
      if (s != null) {
        try {
          if (!s.isClosed()) {
            s.close();
          }
        } catch (IOException e) {
        }
      }
    }

    public StringBuilder getStatusSB() {
      Util.clear(statsb).append("RSO[").append(s == null ? "Null" : s.getLocalPort()).
              append("]: #pkt=").append(npack).append(" #out=").append(nbytesOut).
              append(" nextin=").append(nextin).append(" nextout=").append(nextout).
              append(" used/max/qsz=").append(used).append("/").append(maxused).append("/").append(qsize).append(" dskin=").append(disknextin).
              append(" dskout=").append(disknextout).append(" ack=").append(acking);
      nbytesOut = 0;
      return statsb;
    }

    public String getStatus() {
      return getStatusSB().toString();
    }

    @Override
    public String toString() {
      return toStringBuilder(null).toString();
    }

    public StringBuilder toStringBuilder(StringBuilder tmp) {
      StringBuilder sb = tmp;
      if (sb == null) {
        sb = Util.clear(statsb);
      }
      sb.append(tag).append(" ").append(host).append("/").append(port);
      return sb;
    }

    public long getLastOpen() {
      return lastOpen;
    }

    public RingServerOutputter() {
      dl = ByteBuffer.wrap(dlbuf);
      dlbuf[0] = 'D';
      dlbuf[1] = 'L';
      setDaemon(true);
      start();
    }

    @Override
    public void run() {
      byte ul = (byte) '_';
      byte space = (byte) ' ';
      byte[] write = "WRITE ".getBytes();
      byte[] mseed = "/MSEED ".getBytes();
      byte[] rawdata = "/RAW".getBytes();
      byte[] spasp = " A ".getBytes();
      byte[] spnsp = " N ".getBytes();
      byte[] s512 = "512".getBytes();
      byte[] seed = new byte[12];
      int year, doy, len = 0;
      long dltime, dlend;
      MiniSeed ms = null;
      int[] time;
      GregorianCalendar gc = new GregorianCalendar();
      while (!terminate) {
        openConnection();
        while (!terminate) {
          if (s == null) {
            break;
          }
          if (s.isClosed()) {
            break;
          }
          while (disknextout == 0 && nextout == nextin && !terminate) {
            try {
              sleep(200);
            } catch (InterruptedException e) {
            }
          }
          if (s == null) {
            break;
          }
          if (s.isClosed()) {
            break;
          }
          if (terminate) {
            break;
          }
          //synchronized (queue) {
          // Is there data in the disk queue
          if (disknextout != 0) {   // If memory is 
            // If we have memory blocks do them first until they are empty
            if (nextout != nextin && s != null) {
              sendbuf = queue.get((nextout + 1) % qsize);
              if (dbg) {
                prta(Util.clear(runsb).append(tag).append("RSO: Disk Active set to memory nexout=").append((nextout + 1) % qsize));
              }
            } else {
              // read the block
              try {
                dsk.readBlock(buf, disknextin, 512);
                if (dbg) {
                  prta(Util.clear(runsb).append(tag).append("RSO: Set disk block in=").append(disknextin));
                }
                sendbuf = buf;
                if (sendbuf[0] == 0 && sendbuf[1] == 0 && sendbuf[2] == 0 && sendbuf[3] == 0) {
                  prta(Util.clear(runsb).append(tag).append(" RSO: Got a zero block from disk at ").
                          append(disknextin).append(" try again"));
                  disknextin++;
                  if (disknextin >= disknextout) {
                    prta(Util.clear(runsb).append(tag).append(" found zero block and eof."));
                    disknextin = 0;
                    disknextout = 0;
                    dsk.setLength(0L);
                  }
                  continue;   // nothing to send!
                }
              } catch (IOException e) {
                if (e.toString().contains("EOFException")) {
                  prta(Util.clear(runsb).append(tag).append(" EOF reading disk, shutdown disk reads"));
                } else {
                  prta(Util.clear(runsb).append(tag).append(" Read disk error and not EOF!! e=").append(e));
                  e.printStackTrace(par.getPrintStream());
                }
                try {
                  dsk.setLength(0);
                } catch (IOException e2) {
                }
                disknextout = 0;
                disknextin = 0;
                continue;
              }
            }
          } else {
            if (nextout != nextin && s != null) {
              sendbuf = queue.get((nextout + 1) % qsize);
              if (dbg) {
                prta(Util.clear(runsb).append(tag).append("RSO: Set block to memory nexout=").append((nextout + 1) % qsize));
              }
            } else {
              continue;      // Nothing to send
            }
          }

          ackReader.reset();            // mark no ack received for this block
          dl.position(3);               // The first 3 bytes are 'D','L', <size>
          dl.put(write);                // command 'WRITE '
          int outlen = 512;
          // setup dlbuf and sendbuf to contain data to send, set len and outlen appropriately
          // If this is a raw buffer, process it
          if (sendbuf[0] == 27 && sendbuf[1] == 3) { // Is this RAW data
            dl.put(rawdata);
            len = dl.position() - 3;
            dl.position(2);
            dl.put((byte) len);
            int nsamp = (((int) sendbuf[2]) & 255) * 256 + (((int) sendbuf[3]) & 255);
            outlen = 32 + nsamp * 4;
            /* the buffer is 
             * off   type      description
             * 0    byte      27 - first lead in
             * 1    byte      3 - the 2nd lead in
             * 2    short     Number of samples
             * 4    String12  The NNSSSSSCCCLL 
             * 16   long      String time in millis
             * 24    double   rate
             * 32    int[]    Data samples
             */
            if (dbg) {
              ByteBuffer sbb = ByteBuffer.wrap(sendbuf);
              sbb.position(2);
              short ns = sbb.getShort();
              sbb.get(seed);
              long now = sbb.getLong();
              double rt = sbb.getDouble();
              prta(Util.clear(runsb).append(tag).append("RSO:ProcessSend ").append(new String(seed)).
                      append(" ns=").append(ns).append("/").append(nsamp).
                      append(" rt=").append(rt).append(" ").append(Util.ascdatetime2(now, null)));
            }
          } // Its a miniSEED block
          else {
            // build up the header section of the DataLink protocol for a MiniSEED block, per IRIS/ringserver documentation
            int off = 18;                   // offset in the MiniSeed of the network byte
            if (sendbuf[off] != ' ') {
              dl.put(sendbuf[off]);  // Network
            }
            off++;
            if (sendbuf[off] != ' ') {
              dl.put(sendbuf[off]);  // Networ
            }
            off++;

            dl.put(ul);                                 // add underline
            off = 8;                                       // offset in Miniseed to SSSSLLCCC
            if (sendbuf[off] != ' ') {
              dl.put(sendbuf[off]);  // Station
            }
            off++;
            if (sendbuf[off] != ' ') {
              dl.put(sendbuf[off]);
            }
            off++;
            if (sendbuf[off] != ' ') {
              dl.put(sendbuf[off]);
            }
            off++;
            if (sendbuf[off] != ' ') {
              dl.put(sendbuf[off]);
            }
            off++;
            if (sendbuf[off] != ' ') {
              dl.put(sendbuf[off]);
            }
            off++;
            dl.put(ul);                                 // add underline
            if (sendbuf[off] != ' ') {
              dl.put(sendbuf[off]);
            }
            off++; // Location code 
            if (sendbuf[off] != ' ') {
              dl.put(sendbuf[off]);
            }
            off++;
            dl.put(ul);                                 // add underline
            if (sendbuf[off] != ' ') {
              dl.put(sendbuf[off]);
            }
            off++; // Channel
            if (sendbuf[off] != ' ') {
              dl.put(sendbuf[off]);
            }
            off++;
            if (sendbuf[off] != ' ') {
              dl.put(sendbuf[off]);
            }
            off++;
            dl.put(mseed);                              // add '/MSEED'

            try {
              // Calculate the start time of the packet and the end time in Microseconds
              if (dbg) {
                if (ms == null) {
                  ms = new MiniSeed(sendbuf);
                } else {
                  ms.load(sendbuf);
                }
                prta(Util.clear(runsb).append(tag).append("Process ").append(ms.toStringBuilder(null)));
              }
              year = MiniSeed.crackYear(sendbuf);
              doy = MiniSeed.crackDOY(sendbuf);
              time = MiniSeed.crackTime(sendbuf);
              int[] ymd = SeedUtil.ymd_from_doy(year, doy);
              gc.set(year, ymd[1] - 1, ymd[2], time[0], time[1], time[2]); // Gregorian set to nearest second
              dltime = gc.getTimeInMillis() / 1000 * 1000000 + time[3] * 100;   // drop any stray millis and add try husec in mics
              dlend = dltime + (long) (MiniSeed.crackNsamp(sendbuf) / MiniSeed.crackRate(sendbuf) * 1000000.);// time of expected next
              long2bb(dltime, dl);
              dl.put(space);
              long2bb(dlend, dl);
              if (acking) {
                dl.put(spasp);
              } else {
                dl.put(spnsp);
              }
              dl.put(s512);
              len = dl.position() - 3;    // calculate length of the line without start
              dl.position(2);
              dl.put((byte) len);
              if (dbg) {
                prta(Util.clear(runsb).append(tag).append("DLBUF : ").append(new String(dlbuf, 0, len + 3)));
              }
            } catch (IllegalSeednameException e) {
              prta(Util.clear(runsb).append(tag).append(" e=").append(e));
              e.printStackTrace(par.getPrintStream());
            }
          }   // endif on MiniSEED block
          // The header is in the dlbuf, and the data (miniseed or in the clear) is in the sendbuf
          // len is set to the header length (minius the "DL"<nbytes> which start the dlbuf, outlen is length of
          // binary data bytes.
          try {
            s.getOutputStream().write(dlbuf, 0, len + 3);   // send the header
            s.getOutputStream().write(sendbuf, 0, outlen);// send the binary data
            npack++;
            nbytesOut += len + 3 + 512;
            int loop = 0;
            // if acking is enabled, check for receipt of the ACK.
            if (acking) {
              while (!ackReader.getAckReceived()) {
                try {
                  sleep(2);
                } catch (InterruptedException e) {
                }
                loop++;
                if (loop % 1000 == 999) {    // No ack in 20 seconds, restart the socket
                  prta(Util.clear(runsb).append(tag).append(" *** Did not get an ack in 2 seconds.  Restart"));
                  if (s != null) {
                    try {
                      s.close();
                    } catch (IOException e) {
                    }
                    s = null;
                    break;
                  }
                } else if (loop % 100 == 0) {
                  prta(Util.clear(runsb).append(tag).append(" * slow OK response! ").append(loop));
                }
              }
            }

            if (npack % 1000 == 0) {
              prta(Util.clear(runsb).append(tag).append(" ").append(getStatusSB()));
            }
            // If there was no error, s will still be defined, increment the block needed.
            if (s != null) {
              if (sendbuf == buf) {
                if (dbg) {
                  prta(Util.clear(runsb).append(tag).append(" write disk block=").append(disknextin));
                }
                disknextin++;
                if (disknextin >= disknextout) {
                  prta(Util.clear(runsb).append(tag).append(" Disk writes complete - go back to memory queue mode"));
                  disknextout = 0;
                  disknextin = 0;

                  dsk.setLength(0L);
                } else if (disknextin % 1000 == 999) {
                  prta(Util.clear(runsb).append(tag).
                          append(" Disk writes disknextin=").append(disknextin).append(" out=").append(disknextout));
                }
              } else {
                synchronized (mutex) {
                  nextout = (nextout + 1) % qsize;
                }
                used = nextin - nextout;
                if (used < 0) {
                  used += qsize;
                }
                if (dbg) {
                  prta(Util.clear(runsb).append(tag).append(" Queue out complete for nextout=").append(nextout));
                }
              }
            }
          } catch (IOException e) {
            if (e.toString().contains("roken pipe")) {
              prta(Util.clear(runsb).append(tag).append(" * Broken pipe - clear connection"));
            } else if (e.toString().contains("onnection reset")) {
              prta(Util.clear(runsb).append(tag).append(" * Connnection reset - clear connection"));
            } else if (e.toString().contains("ocket close")) {
              prta(Util.clear(runsb).append(tag).append(" * Socket closed - probably by watchdog - wait for reopen"));
            } else {
              prta(Util.clear(runsb).append(tag).append(" Got IO error trying to write to ringserver e=").append(e));
              e.printStackTrace(par == null ? EdgeThread.getStaticPrintStream() : par.getPrintStream());
            }
            if (s != null) {
              try {
                s.close();
              } catch (IOException e2) {
              }
            }
            s = null;
            try {
              sleep(2000);
            } catch (InterruptedException e2) {
            } // do not do this too quickly
            break;
          }

          if (s == null) {
            break;
          }
          if (s.isClosed()) {
            break;
          }
        }   // while(!terminate) read a block or queue and process
      } // while socket not open
      prta(tag + " is exiting!  Terminate=" + terminate);
      if (s != null) {
        try {
          s.close();
        } catch (IOException e2) {
        }
      }
      s = null;
    }
    byte[] work = new byte[20];

    private void long2bb(long l, ByteBuffer bb) {
      boolean minus = false;
      if (l < 0) {
        minus = true;
        l = -l;
      }
      int iw = 0;
      while (l > 0) {
        work[iw++] = (byte) ((l % 10) + '0');
        l = l / 10;
      }
      if (minus) {
        bb.put((byte) '-');
      }
      for (int i = iw - 1; i >= 0; i--) {
        bb.put(work[i]);
      }
    }

    private void openConnection() {
      long lastEmail = 0;
      if (s == null || s.isClosed()) {
        int loop = 0;
        int isleep = 7500;
        while (true) {
          if (terminate) {
            break;
          }
          try {
            prta(Util.clear(runsb).append(tag).append("OC: Open Port=").append(host).append("/").append(port));
            s = new Socket(host, port);
            prta(Util.clear(runsb).append(tag).append("OC: new socket is ").append(s));
            String response = "ID DataLink 2012.126 :: DLPROTO:1.0 PACKETSIZE:512 WRITE";
            buf[0] = 'D';
            buf[1] = 'L';
            buf[2] = (byte) response.length();
            System.arraycopy(response.getBytes(), 0, buf, 3, response.length());
            s.getOutputStream().write(buf, 0, response.length() + 3);
            if (ackReader == null) {
              ackReader = new AckReader();
            }
            loop = 0;
            if (s == null) {
              continue;
            }
            if (s.isClosed()) {
              continue;      // No open socket, try again
            }
            lastOpen = System.currentTimeMillis();
            break;
          } catch (UnknownHostException e) {
            prt(Util.clear(runsb).append(tag).append("OC: Host is unknown=").append(host).
                    append("/").append(port).append(" loop=").append(loop));
            if (loop % 120 == 0) {
              SendEvent.edgeSMEEvent("SLRingNoHost", "RingServerSL host unknown=" + host + " DNS Up?", this);

            }
            loop++;
            try {
              sleep(30000L);
            } catch (InterruptedException e2) {
            }
          } catch (IOException e) {
            if (e.getMessage().equalsIgnoreCase("Connection refused")) {
              isleep = isleep * 2;
              if (isleep >= 5000) {
                isleep = 5000;      // limit wait to 6 minutes
              }
              prta(Util.clear(runsb).append(tag).append("OC: Connection refused.  wait ").append(isleep / 1000).append(" secs ...."));
              if (isleep >= 30000 && System.currentTimeMillis() - lastEmail > 3600000) {
                SendEvent.edgeSMEEvent("RngSrvSLConn", "Connection refused to " + host + "/" + port, this);
                lastEmail = System.currentTimeMillis();
              }
              try {
                sleep(isleep);
              } catch (InterruptedException e2) {
              }
            } else if (e.getMessage().equalsIgnoreCase("Connection timed out")) {
              prta(Util.clear(runsb).append(tag).append("OC: Connection timed out.  wait ").append(isleep / 1000).append(" secs ...."));
              if (isleep >= 15000 && System.currentTimeMillis() - lastEmail > 3600000) {
                lastEmail = System.currentTimeMillis();
              }
              try {
                sleep(isleep);
              } catch (InterruptedException e2) {
              }
              isleep = isleep * 2;
              if (isleep >= 15000) {
                isleep = 15000;
              }
            } else {
              prta(Util.clear(runsb).append(tag).append("OC: connection bad e=").append(e));

              Util.IOErrorPrint(e, tag + "OC: IO error opening socket=" + host + "/" + port);
              try {
                sleep(30000L);
              } catch (InterruptedException e2) {
              }
            }

          }
        }   // While True on opening the socket
      }     // if s is null or closed  
      if (dbg) {
        prta(Util.clear(runsb).append(tag).append("OC: OpenConnection() is completed."));
      }
    }

  }

  public final class AckReader extends Thread {

    boolean ackReceived;
    int totalIn;
    StringBuilder tmpsb = new StringBuilder(50);

    public void reset() {
      ackReceived = false;
    }

    public boolean getAckReceived() {
      return ackReceived;
    }

    @Override
    public String toString() {
      return tag + " " + host + "/" + port;
    }

    public AckReader() {
      ackReceived = false;
      setDaemon(true);
      start();
    }

    @Override
    public void run() {
      byte[] b = new byte[512];
      ByteBuffer bb = ByteBuffer.wrap(b);

      while (!terminate) {
        try {
          // if the socket is closed, wait for it to open again
          while (s == null && !terminate) {
            try {
              sleep(100);
            } catch (InterruptedException e) {
            }
          }
          while (s.isClosed() && !terminate) {
            try {
              sleep(100);
            } catch (InterruptedException e) {
            }
          }
          if (terminate) {
            break;
          }
          int len;
          int l = 0;
          int nchar;
          try {
            // Wait for 3 char "DL<Length>"
            while (s.getInputStream().available() < 3 && !terminate) {
              try {
                sleep(10);
              } catch (InterruptedException e) {
              }
            }
            if (terminate) {
              break;
            }
            nchar = s.getInputStream().read(b, l, 3);
            if (nchar == 0) {
              try {
                s.close();
              } catch (IOException e) {
              }
              continue;
            }
            totalIn += nchar;
            if (dbg) {
              par.prt(Util.clear(tmpsb).append(tag).append(" ACKR: read hdr ").append(b[0]).append(" ").
                      append(b[1]).append(" ").append(b[2]).append(" nc=").append(nchar).
                      append(" avail=").append(s.getInputStream().available()));
            }
            // figure number of bytes to com
            len = b[2];
            // Get the length of the thing and test if its a OK
            Util.readFully(s.getInputStream(), b, 0, len);
            totalIn += len;
            if (dbg) {
              par.prt(Util.clear(tmpsb).append(tag).append(" ACKR: read len=").append(len).append(" ").
                      append(b[0]).append(" ").append(b[1]).append(" avail=").
                      append(s.getInputStream().available()).append(" |").append(new String(b, 0, len)).append("|"));
            }
            if (b[0] == 'O' && b[1] == 'K') {
              ackReceived = true;
            } else {
              par.prt(Util.clear(tmpsb).append(tag).append(" ACKR: read " + " |").append(new String(b, 0, len)).append("|"));
            }
          } catch (IOException e) {
            if (e.toString().contains("Socket is closed")) {
              prta(Util.clear(tmpsb).append(tag).append("ACKR: Socket closed by server! - wait for reconnect."));
            } else {
              Util.SocketIOErrorPrint(e, tag + "ACKR: IOError reading ack socket. close socket", par.getPrintStream());
              prta(Util.clear(tmpsb).append(tag).append("IOError reading from ack socket."));
              e.printStackTrace(par.getPrintStream());
            }
          }

          if (s == null) {
            continue;
          }
          if (s.isClosed()) {
            continue;      // socket is now closed, wait for a new one
          }
          if (terminate) {
            break;
          }
        } catch (RuntimeException e) {
          prta(Util.clear(tmpsb).append(tag).append("ACKR: runtime exception.  Probably socket closed.  Continue. e=").append(e));
          e.printStackTrace(par.getPrintStream());
        }
      }   // While not terminate
    }
  }
}
