/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.edgemom;

import gov.usgs.anss.edge.OutOfOrderRTMS;
import gov.usgs.anss.edge.MasterBlock;
import gov.usgs.anss.edge.ChannelSender;
import gov.usgs.alarm.SendEvent;
import gov.usgs.anss.cd11.Canada;
import gov.usgs.anss.cd11.CanadaException;
import gov.usgs.anss.cd11.GapList;
import gov.usgs.anss.edge.*;
import gov.usgs.anss.edgeoutput.TraceBuf;
import gov.usgs.anss.edgethread.EdgeThread;
import gov.usgs.anss.db.DBMessageQueuedClient;
import gov.usgs.anss.net.SNWSender;
import gov.usgs.anss.util.NsnTime;
import gov.usgs.anss.util.SeedUtil;
import gov.usgs.anss.util.Util;
import gov.usgs.edge.config.Channel;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.text.DecimalFormat;
import java.util.Calendar;
import java.util.GregorianCalendar;
import gnu.trove.map.hash.TLongObjectHashMap;
//import gnu.trove.iterator.TLongObjectIterator;

/**
 * This class contins information on the Q680 and Q730 sockets.  Since these are no longer in use, 
 * it is likely obsolete.
 * 
 * @author davidketchum
 */
public final class Q680Socket extends Thread {

  private static final int GOMBERG_HDR_SIZE = 12;
  private static final int STATUS_HDR_SIZE = 40;
  private static final int CHANNEL_HDR_SIZE = 4;
  private static final int TIMESERIES_HDR_SIZE = 12;
  private static String dbgdataStation = "";

  private static final DecimalFormat df61 = new DecimalFormat("###0.0");
  private static final DecimalFormat df91 = new DecimalFormat("####.0");
  private static DBMessageQueuedClient messageServer;
  private static SNWSender snwsender;

  private static final String[] chans = {
    "HHN00", "HHE00", "HHZ00", "BHN00", "BHE00", "BHZ00", "BHN00", "BHE00", "BHZ00", // codes CH_X80 to CH_Z20
    "MHN00", "MHE00", "MHZ00", "LHN00", "LHE00", "LHZ00", // CH_X10 - CH_Z1
    "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", // Unused codes from 15 to 31
    "HNN20", "HNE20", "HNZ20", "BNN20", "BNE20", "BNZ20", "BNN20", "BNE20", "BNZ20", // codes CH_X80 to CH_Z20
    "MNN20", "MNE20", "MNZ20", "LNN20", "LNE20", "LNZ20"};                            // CH_X10 - CH_Z1

  //Group delays by channel number in husec
  private static final int[] q730delay = {
    240, 240, 240, 3850, 3850, 3850, 15480, 15480, 15480,
    38730, 38730, 38730, 159930, 159930, 159930,
    0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
    240, 240, 240, 3850, 3850, 3850, 15480, 15480, 15480,
    38730, 38730, 38730, 159930, 159930, 159930
  };
  private static final int[] q680delay = {
    890, 890, 890, 4690, 4690, 4690, 12340, 12340, 12340,
    27570, 27570, 27570, 148070, 148070, 148070,
    0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
    890, 890, 890, 4690, 4690, 4690, 12340, 12340, 12340,
    27570, 27570, 27570, 148070, 148070, 148070

  };
  private TLongObjectHashMap<LPBuffer> lpbuffers = new TLongObjectHashMap<>();
  private TLongObjectHashMap<Integer> pinnos = new TLongObjectHashMap<>();
  private StringBuilder sb = new StringBuilder(200);
  private String orgline;
  private Socket s;
  private int oorint;     // The OOR interval in seconds
  private OutOfOrderRTMS oorProcessor;
  private RawDisk ring;
  private boolean dbg;
  private boolean dbgdata;

  private int npackets;
  private ChannelSender csend;
  private boolean nocsend;
  private String oordbg;
  private long lastStatus;
  private int lastBytesIn;
  private int bytesIn;
  private int bytesOut;
  private long lastStatusOut;
  private EdgeThread par;
  private GapList gaps;
  private boolean hydra;

  private byte[] buf;
  private ByteBuffer bb;
  private byte[] ackbuf;
  private ByteBuffer ackbb;
  private byte[] cmdbuf;
  private ByteBuffer cmdbb;
  private GregorianCalendar acktim = new GregorianCalendar();
  private GregorianCalendar gnow = new GregorianCalendar();
  private byte[] b;
  private String tag;
  private boolean terminate;
  private boolean running;
  private int minsamps;
  private TraceBuf tb;         // trace buf for hydra sender
  byte timeQual;                // Time quality 0-100
  byte clock;                   // clock bits status from DAS
  byte ioClock;                 // SEED IO and clock flags
  byte activity;                // SEED Activity flags
  byte quality;                 // SEED Quality flags

  // Q680/q730 das parameters (from nsnstation table)
  private String ip;
  private int ID;
  private int tbmax;
  private int tbase;
  private boolean cont40;
  private boolean cont20;
  private int expbias;
  private boolean highgain;
  private boolean lowgain;
  private int smtriglevel;
  private int dac480;
  private int attn480;
  private int seistype;
  private int seistype2;
  private int qtype;
  private String network;
  private String station;
  private boolean nosnw;
  private final StringBuilder seednameSB = new StringBuilder(12);

  public static void setDbgDataStation(String s) {
    dbgdataStation = s.trim();
  }

  public boolean isRunning() {
    return running;
  }

  public String getStatusString() {
    double elapsed = (System.currentTimeMillis() - lastStatusOut) / 1000.;
    lastStatusOut = System.currentTimeMillis();
    int nb = bytesIn - lastBytesIn;
    lastBytesIn = bytesIn;
    return station + " #pkt=" + npackets + " #bytesin=" + bytesIn + " rate=" + ((int) (nb * 8 / elapsed));
  }

  public int getBytesIn() {
    return bytesIn;
  }

  public int getBytesOut() {
    return bytesOut;
  }

  public String getMonitorString() {
    return "";
  }

  public String getConsoleOutput() {
    return "";
  }

  public void terminate() {
    terminate = true;
    if (par != null) {
      par.prt(tag + " Q680Socket terminate called");
    } else {
      Util.prt(tag + " Q680Socket terminate called.");
    }
    if (s != null) {
      try {
        s.close();
      } catch (IOException expected) {
      }
    }
  }

  @Override
  public String toString() {
    return tag + " s=" + (s == null ? "null" : s.getInetAddress().toString() + "/" + s.getPort())
            + " hydra=" + hydra;
  }

  public void setDebug(boolean t) {
    dbg = t;
  }

  public void newSocket(Socket ss) {
    if (s != null) {
      if (!s.isClosed()) {
        try {
          s.close();
        } catch (IOException expected) {
        }
      }
    }
    try {
      sleep(1000);
    } catch (InterruptedException expected) {
    }
    dbgdata = false;
    if (dbgdataStation.equals(station.trim())) {
      dbgdata = true;
    }
    par.prt(tag + " " + station + " New connection set to " + ss + " dbgdata=" + dbgdata + " " + dbgdataStation);
    s = ss;
    try {
      sendAcks();
    } catch (IOException e) {
      par.prta(tag + " newSocket failed to send Ack! e=" + e);
    }
  }

  public final void setArgs(String argline) {
    String[] args = argline.split("\\s");
    orgline = argline;
    oordbg = "";
    oorint = 30000;
    nocsend = false;
    hydra = true;
    ID = 0;
    ip = "";
    tbmax = 4096;
    tbase = 1200;
    cont40 = true;
    cont20 = false;
    expbias = 16;
    highgain = false;
    lowgain = false;
    smtriglevel = 2000;
    dac480 = 0;
    attn480 = 0;
    seistype = 0;
    qtype = 0;
    network = "US";
    for (int i = 0; i < args.length; i++) {
      if (args[i].equals("-oorint")) {
        oorint = Integer.parseInt(args[i + 1]);
        i++;
      } else if (args[i].equals("-oordbg")) {
        oordbg = args[i + 1].replaceAll("_", " ");
        i++;
      } else if (args[i].equals("-dbg")) {
        dbg = true;
      } else if (args[i].equals("-noudpchan")) {
        nocsend = true;
        csend = null;
      } else if (args[i].equals("-nohydra")) {
        hydra = false;
      } else if (args[i].equals("-ip")) {
        ip = args[i + 1];
        i++;
      } else if (args[i].equals("-id")) {
        ID = Integer.parseInt(args[i + 1]);
        i++;
      } else if (args[i].equals("-tbmax")) {
        tbmax = Integer.parseInt(args[i + 1]);
        i++;
      } else if (args[i].equals("-tb")) {
        tbase = Integer.parseInt(args[i + 1]);
        i++;
      } else if (args[i].equals("-c40")) {
        if (args[i + 1].equals("True")) {
          cont40 = true;
        }
        i++;
      } else if (args[i].equals("-c20")) {
        if (args[i + 1].equals("True")) {
          cont20 = true;
        }
        i++;
      } else if (args[i].equals("-exp")) {
        expbias = Integer.parseInt(args[i + 1]);
        i++;
      } else if (args[i].equals("-hg")) {
        if (args[i + 1].equals("True")) {
          highgain = true;
        }
        i++;
      } else if (args[i].equals("-sm")) {
        if (args[i + 1].equals("True")) {
          lowgain = true;
        }
        i++;
      } else if (args[i].equals("-smlev")) {
        smtriglevel = Integer.parseInt(args[i + 1]);
        i++;
      } else if (args[i].equals("-dac480")) {
        dac480 = Integer.parseInt(args[i + 1]);
        i++;
      } else if (args[i].equals("-attn480")) {
        attn480 = Integer.parseInt(args[i + 1]);
        i++;
      } else if (args[i].equals("-nosnw")) {
        nosnw = true;
      } else if (args[i].equals("-dbgdata")) {
        i++;
      } else if (args[i].equals("-rtmsdbg")) {
        i++;
      } else if (args[i].equals("-net")) {
        if (i < args.length - 1) {
          network = args[i + 1];
        } else {
          network = "US";
        }
        i++;
      } else if (args[i].equals("-seistype")) {
        switch (args[i + 1]) {
          case "NSN":
            seistype = 0;
            break;
          case "STS-2":
            seistype = 1;
            break;
          case "NSN-3T":
            seistype = 3;
            break;
          case "CMG3T":
            seistype = 4;
            break;
          case "NSNCMG5":
            seistype = 5;
            break;
          case "EPISENSOR":
            seistype = 6;
            break;
          case "CMG5TD":
            seistype = 7;
            break;
          case "MEMS":
            seistype = 8;
            break;
          case "CMG3ESP":
            seistype = 9;
            break;
        }
        i++;
      } else if (args[i].equals("-seistype2")) {
        switch (args[i + 1]) {
          case "NSN":
            seistype2 = 0;
            break;
          case "STS-2":
            seistype2 = 1;
            break;
          case "NSN-3T":
            seistype2 = 3;
            break;
          case "CMG3T":
            seistype2 = 4;
            break;
          case "NSNCMG5":
            seistype2 = 5;
            break;
          case "EPISENSOR":
            seistype2 = 6;
            break;
          case "CMG5TD":
            seistype2 = 7;
            break;
          case "MEMS":
            seistype2 = 8;
            break;
          case "CMG3ESP":
            seistype2 = 9;
            break;
        }
        i++;
      } else if (args[i].equals("-qtype")) {
        switch (args[i + 1]) {
          case "Q680":
            qtype = 0;
            break;
          case "Q730":
            qtype = 1;
            break;
          case "Q330SR":
            qtype = 2;
            break;
          case "Q330HR":
            qtype = 3;
            break;
        }
        i++;
      } else if (args[i].charAt(0) == '>') {
        continue;
      } else {
        par.prta(tag + "Unknown argument to Q680Socket=" + args[i]);
      }
      if (!nocsend && csend == null) {
        csend = new ChannelSender("  ", "Q680", "Q680-" + tag);
      }
      if (hydra && tb == null) {
        tb = new TraceBuf(new byte[TraceBuf.TRACE_LENGTH]);        // Trace buf for sending data to HydraOutputer
      }
    }
    station = (network + tag + "   ").substring(0, 7).toUpperCase();
    par.prta("CFG:" + network + station + " hg=" + highgain + " sm=" + lowgain + " exp=" + expbias + " smlev=" + smtriglevel + " 20=" + cont20
            + " 40=" + cont40 + " seistype=" + seistype + " 2=" + seistype2 + " tb=" + tbase);
  }

  public Q680Socket(String argline, String tg, EdgeThread parent) {
    //super(argline, tg);
    tag = tg;
    par = parent;
    buf = new byte[4096];
    bb = ByteBuffer.wrap(buf);
    b = new byte[buf.length];
    minsamps = 30;
    ackbuf = new byte[2000];
    ackbb = ByteBuffer.wrap(ackbuf);
    cmdbuf = new byte[250];
    cmdbb = ByteBuffer.wrap(cmdbuf);

    setArgs(argline);     // Set arguments per the input command line
    try {
      gaps = new GapList(0, 0L, "Q680/" + tag + ".gaps", par);
    } catch (FileNotFoundException e) {
      par.prta(tag + " Could not open gap list file.  ");
    }
    oorProcessor = new OutOfOrderRTMS(oorint, tag, par);
    try {
      if (snwsender == null && !nosnw) {
        snwsender = new SNWSender(200, 300);
      }
    } catch (UnknownHostException e) {
      snwsender = null;
      par.prt("  **** Q680Socket did not open a SNWSender!");
      SendEvent.edgeSMEEvent("Q680SNWSendErr", "Q680Socket did not set up SNWSender e=" + e, (Q680Socket) this);
    }
    gov.usgs.anss.util.Util.prta("new ThreadStation " + getName() + " " + getClass().getSimpleName() + " tag=" + tag + " args=" + argline);
    start();
  }

  @Override
  public void run() {
    running = true;
    byte[] statb = new byte[7];
    int countMsgs = 0;
    int lastCount = 0;
    int crc;
    int crccomp;
    short nbytes;
    byte type;
    byte lead1, lead2;
    int lastAckMsg = 0;
    int lastSeq = 0;
    int lastBytesInRun = 0;
    long lastDaily = System.currentTimeMillis();
    int dailyBytesIn = 0;
    int dailyCount = 0;
    int l;
    GregorianCalendar tmptime = new GregorianCalendar();
    int yr;
    int doy;
    int husecNominal;
    try {
      while (!terminate) {
        while (s == null) {
          try {
            sleep(1000);
          } catch (InterruptedException expected) {
          }
        }
        if (terminate) {
          break;
        }
        int nsleft = 0;
        long now;
        while (true) {
          if (terminate) {
            break;
          }
          try {
            // Get the header
            l = Util.readFully(s.getInputStream(), buf, 0, GOMBERG_HDR_SIZE);
            if (l == 0) {
              break;
            }
            bb.position(0);
            lead1 = bb.get();
            lead2 = bb.get();
            if (lead1 != 27 || lead2 != 3) {
              par.prta(tag + " Lead ins not right.  close socket " + lead1 + " " + lead2);
              SendEvent.debugEvent("Q680BadLd", tag + "Bad leadins", this);
              s.close();
              s = null;
              break;
            }
            nbytes = bb.getShort();
            int seq = bb.getInt();
            if ((seq > gaps.getHighestSeq() && seq % 100 == 1)
                    || (countMsgs - lastAckMsg) > 300
                    || System.currentTimeMillis() - acktim.getTimeInMillis() > 180000) {    // its been a long time
              if (seq % 3600 == 1) {
                gaps.trimList(seq - 3 * 86400); // kill off gaps that are older than 3 days
              }
              par.prta(tag + " " + station + " send ack seq=" + seq + " cnt=" + countMsgs + " last=" + lastAckMsg);
              sendAcks();
              lastAckMsg = countMsgs;
            }
            crc = bb.getShort();
            crc = crc & 0xffff;
            type = bb.get();
            npackets++;
            if (type == 3) {
              par.prt(station + " got a keepalive!!!");
              continue;
            }      // This is a null keepalive packet

            // The header is decoded.  Now get the rest of the bytes
            l = Util.readFully(s.getInputStream(), buf, GOMBERG_HDR_SIZE, nbytes - GOMBERG_HDR_SIZE);
            if (l == 0) {
              break;       // Got an EOF
            }
            crccomp = 0;
            if (nbytes > 10) {
              crccomp = CRC16.crcbuf(buf, 10, nbytes - 10);
            }
            if (crc != crccomp) {
              par.prta(tag + "Q680Skt: CRC mismatch " + crc + "!=" + crccomp);
              SendEvent.debugEvent("Q680ChkSm", tag + "Bad checksum", this);
              continue;
            }
            countMsgs++;
            dailyCount++;
            bb.position(GOMBERG_HDR_SIZE + 6);
            short yrdoy = bb.getShort();
            yr = yrdoy / 367 + 2000;
            doy = yrdoy % 367;
            husecNominal = bb.getInt();
            tmptime.setTimeInMillis(86400000L * 1000 + (husecNominal + 5) / 10);

            if (dbg) {
              par.prt(station + " " + yr + "," + doy + " " + Util.asctime2(tmptime) + " nb=" + nbytes
                      + (crc != crccomp ? " crc=" + Util.toHex(crc)
                              + " crcomp=" + Util.toHex(crccomp) : "") + " ty=" + type + " sq=" + (seq % 1000000));
            }
            if (seq > 0) {
              gaps.gotSeq(seq);
            }
            if (seq > 0 && seq - 1 != lastSeq && dbg) {
              par.prt(station + " Out of seq=" + seq + " expect=" + (lastSeq + 1));
              // Its a duplicate packet, do not reprocess.  Happens when ack from RTS to Q680 times out and data sent twice
              if (lastSeq == seq) {
                par.prta(" **** Duplicate packet skip " + seq);
                continue;
              }
            }
            if (seq > 0) {
              lastSeq = seq;
            }

            // Now process the data according to type
            switch (type) {
              case 0:
                doData();
                break;
              case 1:   // Status from the field
                doStatus();
                break;
              case 2:
                doPower();
                break;
              // Command returns look like this :
              // esc, stx, nbyte(short), 4 bytes of zero(net, node, chan, seq)
              // nsntime code (6 bytes) (offset 8)
              // cmdcode - The ANS code (offset 14)
              // cmddata - depends on code (offset 15)
              case 42:    // ASKTIME - get a time mark from here, ANSTIME=45
                byte[] nsntime = new NsnTime(new GregorianCalendar()).getAsBuf();
                cmdbb.position(15);     // position to payload portion
                cmdbb.put((byte) qtype);
                cmdbb.putShort((short) tbase);
                cmdbb.putShort((short) (tbmax <= 0 ? 4096 : tbmax));
                cmdbb.putShort((short) ID);
                cmdbb.put(((network + "  ").substring(0, 2) + (tag + "     ").substring(0, 5)).getBytes());
                ansCommand(45, nsntime, cmdbb.position() - 15, cmdbb);
                s.getOutputStream().write(cmdbuf, 0, cmdbb.position());
                par.prta(tag + " ANSTIME len=" + cmdbb.position());
                break;
              case 43:    // ASKCONFIG - get the configuration data, ANSCONFIG=46
                nsntime = new NsnTime(new GregorianCalendar()).getAsBuf();
                cmdbb.position(15);
                cmdbb.put((byte) (cont40 ? 1 : 0));
                cmdbb.put((byte) 0);      // This is nheli
                cmdbb.putInt(0);
                cmdbb.putShort((short) 0);  // these are 6 bytes of heli gains
                cmdbb.put((byte) (lowgain ? 2 : 0));  // Low gain is encoded as two
                cmdbb.put((byte) (highgain ? 3 : 0)); // High bain is encoded are 3
                cmdbb.put((byte) (cont20 ? 4 : 0));   // continuouse 40 is encoded as 4
                cmdbb.put((byte) 0);                // no local loops allowed now
                cmdbb.put((byte) dac480);           // The dac480 selection byte
                cmdbb.putShort((short) attn480);    // gain of the dac480
                cmdbb.putShort((short) 0);          // gain of 2nd dac480 chan
                cmdbb.putShort((short) 0);          // gain of 3rd dac480 chan
                cmdbb.put((byte) seistype);         // seismometer type
                cmdbb.put((byte) qtype);            // quanterra type
                cmdbb.put((byte) seistype2);        // Strong motion seismometer type

                ansCommand(46, nsntime, cmdbb.position() - 15, cmdbb);
                par.prta(tag + " ANSCONFIG len=" + cmdbb.position() + " hg=" + highgain + " lg=" + lowgain + " 40=" + cont40);
                s.getOutputStream().write(cmdbuf, 0, cmdbb.position());
                break;
              case 44:    // ASKTRIGGER - get the trigger parameters, ANSTRIGGER=47
                nsntime = new NsnTime(new GregorianCalendar()).getAsBuf();
                cmdbb.position(15);
                String sn = "   3.5";
                cmdbb.put("   3.5".getBytes());   // 6 bytes of encoded sn
                cmdbb.put(" 999930.0".getBytes()); // 9 bytes of high frequency s/n
                cmdbb.put((byte) 1);              // ncoin
                cmdbb.put((byte) 4);              // number of trigger freqs
                cmdbb.put((byte) 0);              //
                cmdbb.put((byte) 0x1E);           // The 4 frequencies to use
                cmdbb.put((byte) expbias);        // exponent bias for powers
                cmdbb.put(Util.leftPad("" + smtriglevel, 8).toString().getBytes());
                ansCommand(47, nsntime, cmdbb.position() - 15, cmdbb);
                par.prta(tag + " ANSTRIG: len=" + cmdbb.position());
                s.getOutputStream().write(cmdbuf, 0, cmdbb.position());
                break;

              default:
                par.prta("Found unknown type on Q680 link type=" + type);
            }
            now = System.currentTimeMillis();

            // Do a once daily output with daily average bit rate, bytes, etc.
            if (now - lastDaily > 600000 && (now % 86400000L) < 300000) {
              par.prta(tag + " Daily summary #pkts=" + dailyCount + " " + (bytesIn - dailyBytesIn) + " bytes " + (now - lastDaily) / 1000 + " s "
                      + (bytesIn - dailyBytesIn) * 8000L / (now - lastDaily) + " b/s");
              lastDaily = now;
              dailyCount = 0;
              dailyBytesIn = bytesIn;
            }

            // Is it time for status yet
            if ((now - lastStatus) > 600000 && dbg) {
              par.prta(tag + " # Rcv=" + (countMsgs - lastCount) + " #tot=" + countMsgs + " #bytesin=" + bytesIn + " "
                      + ((long) bytesIn - lastBytesInRun) * 8000L / (now - lastStatus) + " b/s");
              lastBytesInRun = bytesIn;
              par.prta(tag + "Gaps=" + gaps.toString());
              lastCount = countMsgs;
              lastStatus = now;
            }

            bytesIn += nbytes;

          } catch (IOException e) {
            if (e.toString().contains("Socket closed") || e.toString().contains("onnection reset")) {
              par.prta(tag + " Doing termination via Socket close.");
            } else {
              Util.IOErrorPrint(e, tag + " receive through IOError", par.getPrintStream());
            }
            break;      // Drop out of read loop to connect looop
          }
        }
        par.prta(tag + "Socket must be closed.");
        if (s != null) {
          if (!s.isClosed()) {
            try {
              s.close();
            } catch (IOException expected) {
            }
          }
        }
        s = null;
      }
    } catch (RuntimeException e) {
      par.prta(tag + " Got runtime exception e=" + e);
      e.printStackTrace(par.getPrintStream());
    }
    gaps.writeGaps(true);
    oorProcessor.terminate();
    par.prta(tag + " has exited");
    running = false;
    if (csend != null) {
      csend.close();
    }
  }

  private byte cnvMpos(byte aux) {
    return (byte) (((((int) aux) & 0xff) - 128));
  }
  /**
   * handle a status packet
   *
   */
  byte[] aux = new byte[24];

  private void doStatus() {
    bb.position(GOMBERG_HDR_SIZE);
    double volt1, volt2;
    int temp1, temp2;
    byte dastype = bb.get();
    byte cqual = bb.get();
    short pllint = bb.getShort();
    short tb2 = bb.getShort();
    short yrdoy = bb.getShort();
    int husec = bb.getInt();
    byte gpsb = bb.get();
    byte pllb = bb.get();
    byte restartcd = bb.get();
    byte timequal = bb.get();
    bb.get(aux);

    temp1 = cvttmpf(aux[11]);
    temp2 = cvttmpf(aux[19]);
    if (qtype == 0) {
      volt1 = (((int) aux[2]) & 0xff) * 0.143;
      volt2 = (((int) aux[3]) & 0xff) * 0.143;
    } else {
      volt1 = (((int) aux[2]) & 0xff) * 0.1;
      volt2 = (((int) aux[3]) & 0xff) * 0.1;
    }
    if (sb.length() > 0) {
      sb.delete(0, sb.length());
    }

    // This time is MS seems to be the only way to tell the time of the sample, decode it
    int[] ymd = SeedUtil.ymd_from_doy((yrdoy / 367) + 2000, yrdoy % 367);
    sb.append("status^dasstatus^time=").append(ymd[0]).append("-").append(Util.df2.format(ymd[1])).
            append("-").append(Util.df2.format(ymd[2])).append(" ").append(toTimeString(yrdoy, husec).substring(9));
    sb.append(";station=").append((station + "     ").substring(0, 7));
    sb.append(";dastype=").append(qtype).append(";masspos1=").append(cnvMpos(aux[8])).
            append(";masspos2=").append(cnvMpos(aux[9])).append(";masspos3=").append(cnvMpos(aux[10])).
            append(";masspos4=").append(cnvMpos(aux[16])).append(";masspos5=").append(cnvMpos(aux[17])).
            append(";masspos6=").append(cnvMpos(aux[18]));
    sb.append(";clockQuality=").append(timequal).append(";gpsbits=").append(gpsb).
            append(";pllbits=").append(pllb).append(";pllint=").append(pllint).append(";volt1=").
            append((int) (volt1 * 10 + 0.5)).append(";volt2=").append((int) (volt2 * 10 + 0.5)).append(";temp1=").
            append(temp1).append(";timebase=").append(tb2);
    par.prt(sb.toString());
    // Create a queued client socket to the DBMessageServer, this is shared by all instances
    if (messageServer == null) {
      par.prta("Staring a DBMessageQueuedClient to " + Util.getProperty("StatusServer") + "/7985");
      messageServer = new DBMessageQueuedClient(Util.getProperty("StatusServer"), 7985, 100, 300, par);  // 100 message queue, 300 in length

    }
    if (!sb.toString().contains("null") && messageServer != null) {
      messageServer.queueDBMsg2(sb.toString());
    }
    par.prt("Status " + toTimeString(yrdoy, husec) + " das=" + dastype + " cqual=" + cqual + " pllint=" + pllint
            + " tbase=" + tb2 + " gpsb=" + gpsb + " pllb=" + pllb + " restar=" + restartcd + " timeq=" + timequal);
    par.prt("aux=" + aux[0] + " " + aux[1] + " " + aux[2] + " " + aux[3] + " " + aux[4] + " " + aux[5] + " " + aux[6] + " " + aux[7] + " [2]: "
            + aux[8] + " " + aux[9] + " " + aux[10] + " " + aux[11] + " " + aux[12] + " " + aux[13] + " " + aux[14] + " " + aux[15] + " [3]: "
            + aux[16] + " " + aux[17] + " " + aux[18] + " " + aux[19] + " " + aux[20] + " " + aux[21] + " " + aux[22] + " " + aux[23]);
    if (!nosnw) {
      Util.clear(sb);
      sb.append(station.substring(0, 2)).append("-").append(station.substring(2).trim()).append(":8:");
      sb.append("posz=").append(df61.format(cnvMpos(aux[8]) * 0.0976).trim()).append(";posn=").
              append(df61.format(cnvMpos(aux[9]) * 0.0976).trim()).append(";pose=").
              append(df61.format(cnvMpos(aux[10]) * 0.0976)).append(";tempq1=").append(temp1).
              append(".;tempq2=").append(temp2).append(".");
      sb.append(";24Vr=").append(df61.format(volt1)).append(";12Vr=").append(df61.format(volt2)).
              append(";time=").append(toTimeString(yrdoy, husec).substring(0, 17)).append("\n");
      snwsender.queue(sb);
      par.prt("SNW:" + sb);
    }
  }

  /**
   * for the Q680 and Q730 convet the AUX adc reading for temperatures to
   * degrees celsius
   *
   * @param tmp The byte from the aux ADC with the reading
   * @return degrees celsius
   */
  private int cvttmpf(byte tmp) {
    /*  double b=4103.53;           /* For Panasonic PMT-119 */
 /*  double t0=298.16;           /* 0 deg centigrade is this Kelvin */
    double work, ratio;
    /* working floats */
    ratio = (double) (((int) tmp) & 0xff) / 256.;
    /* convert to scale of 0. - 1. */
    if (ratio == 0) {
      ratio = 1 / 256.;  // Zero is illegal since we have to take its log
    }
    work = 255.;
    /* very hot incase tmp is 0 */
    if (ratio > 0) {
      work = (4103.53 / (4103.53 / 298.16 + Math.log(ratio / (1.0 - ratio)))) - 273.16;
    }
    work = work * 9. / 5. + 32.;
    return (int) ((work - 32.) * 5. / 9. + 0.5);  // Convert to celsius
  }

  /**
   * handle a power packet
   *
   */
  private void doPower() {
    bb.position(GOMBERG_HDR_SIZE);
    byte[] tc = new byte[6];
    for (int i = 0; i < 6; i++) {
      bb.get(tc, 0, 6);
      /*bb.position(bb.position()+2);       //DEBUG:  this is because the NSN time has an extra two bytes
      bb.get(tc, 2,4);*/
      times[i] = new NsnTime(tc);
    }
    /*if(dbg) {
      par.prt("Exp="+bb.getShort()+" "+bb.getShort()+" "+bb.getShort()+" "+bb.getShort()+" "+bb.getShort()+" "+bb.getShort()+" ");
      for(int i=0; i<6; i++) par.prt(i+" "+times[i].toString()+" Pow="+bb.getInt()+" "+bb.getInt()+" "+bb.getInt()+" "+bb.getInt()+" "+
            bb.getInt()+" "+bb.getInt()+" "+bb.getInt()+" "+bb.getInt());
    }*/
  }
  private final NsnTime[] times = new NsnTime[6];

  /**
   * Handle a data packet int buf and bb
   *
   * @param station The station name NNSSSSS
   */
  private void doData() {
    int nillegal = 0;
    int nbytes;
    Integer pinno;
    Channel chn;
    int chancode;
    int[] data = new int[1000];
    // Variables needed to process a time series packet
    int nsamp;
    int husec = 0;
    int husecNominal;
    //String seedname;
    bb.position(GOMBERG_HDR_SIZE);
    int nchan = bb.get();
    timeQual = bb.get();
    clock = bb.get();
    ioClock = bb.get();
    activity = bb.get();
    quality = bb.get();
    short yrdoy = bb.getShort();
    int yr = yrdoy / 367 + 2000;
    int doy = yrdoy % 367;
    husecNominal = bb.getInt();
    if (dbgdata) {
      par.prt("  nch=" + nchan + " tqual=" + timeQual + " clk=" + clock + " ioCl=" + ioClock
              + " act=" + activity + ((activity & 4) != 0 ? " ** Trig **" : "")
              + ((activity & 8) != 0 ? " ** TrigOff **" : "")
              + ((activity & 1) != 0 ? " ** CAL ON **" : "")
              + " dqual=" + quality + " yr=" + yr + " doy=" + doy + " time=" + toTimeString(yrdoy, husecNominal));
    }

    for (int ch = 0; ch < nchan; ch++) {
      nbytes = bb.getShort();   // number of bytes in this channel segment\
      chancode = bb.get();
      //String chan = chans[chancode];
      nsamp = bb.get();
      Util.clear(seednameSB).append(station).append(chans[chancode]);
      //seedname = station+chan;
      //seedname = seedname.replaceAll("%","Z");    // DEBUG: allow % in going to Z

      // The time code needs to be adjusted by group delay, and then set to correct day/time
      if (qtype == 0) {
        husec = husecNominal - q680delay[chancode];
      } else if (qtype == 1) {
        husec = husecNominal - q730delay[chancode];
      }
      int year = yr;
      int day = doy;
      if (husec < 0) {     // correction went to prior day
        int julian = SeedUtil.toJulian(year, day);
        julian--;
        int[] ymd = SeedUtil.fromJulian(julian);
        year = ymd[0];
        day = SeedUtil.doy_from_ymd(ymd);
        husec += 864000000;
      }
      //get the canadian compressed data
      if (dbgdata) {
        par.prt("    nbcan=" + nbytes + " chan=" + chans[chancode] + " ns=" + nsamp + " time=" + toTimeString(yrdoy, husec) + " "
                + (qtype == 0 ? q680delay[chancode] : q730delay[chancode]));
      }
      if (nbytes == 0) {     // This is LH in the clear data
        data[0] = bb.getInt();// Note LP buffers handle compression to oorProcessor
      } else {
        bb.get(b, 0, nbytes);     // Get the canadian compressed data

        try {
          MasterBlock.checkSeedName(seednameSB);
          Canada.canada_uncompress(b, data, nbytes, nsamp, 0);
          /*if(dbgdata && chan.substring(0,2).equals("BH")) {
            par.prt("Decomp:"+data[0]+" "+data[1]+" "+data[2]+" "+data[3]+" "+data[4]+" "+data[5]+" "+data[6]+" "+
                   data[7]+" "+data[8]+" "+data[9]+" "+data[10]+" "+data[11]+" "+data[12]+" "+data[13]+" "+
                   data[14]+" "+data[15]+" "+data[16]+" "+data[17]+" "+data[18]+" "+data[19]+"\n       "+
                   data[20]+" "+data[21]+" "+data[22]+" "+data[23]+" "+data[24]+" "+data[25]+" "+data[26]+" "+
                   data[27]+" "+data[28]+" "+data[29]+" "+data[30]+" "+data[31]+" "+data[32]+" "+data[33]+" "+
                   data[34]+" "+data[35]+" "+data[36]+" "+data[37]+" "+data[38]+" "+data[39]
                   );
          }*/
          oorProcessor.addBuffer(data, nsamp, seednameSB, year, day,
                  husec / 10000, (husec % 10000) * 100, (double) nsamp,
                  activity, ioClock, quality, timeQual, false);
          if (csend != null) {
            csend.send(SeedUtil.toJulian(year, day), husec / 10, seednameSB,
                    nsamp, (double) nsamp, nsamp * 4);
          }
        } catch (IOException e) {
          par.prt(tag + " IOEerror from csend e=" + e);
        } catch (CanadaException e) {
          par.prt(tag + " CanadaException: Q680S trying to do packet from " + seednameSB + " e=" + e);
        } catch (IllegalSeednameException e) {
          nillegal++;
          par.prta(tag + " IllegalSeedName =" + nillegal + " "
                  + Util.toAllPrintable(seednameSB) + " " + e.getMessage());
          for (int i = 0; i < 48; i++) {
            par.prt(i + " = " + buf[i] + " " + (char) buf[i]);
          }
          if (nillegal > 3) {
            terminate = true;    // if 3 in a row, then close connection
          }
        } catch (RuntimeException e) {
          par.prt(tag + " Got Runtime  e=" + e);
          e.printStackTrace(par.getPrintStream());
        }
      }
      // Send the data to out of order processing, notice, hydra is handled separately 

      // Send the data to hydra if selected
      char ch2 = seednameSB.charAt(7);
      if (ch2 != 'B' && ch2 != 'S' && ch2 != 'H' && ch2 != 'L' && ch2 != 'M' && ch2 != 'E') {
        continue;
      }
      ch2 = seednameSB.charAt(8);
      if (ch2 != 'H') {
        continue;
      }
      pinno = pinnos.get(Util.getHashFromSeedname(seednameSB));
      if (pinno == null) {
        chn = EdgeChannelServer.getChannel(seednameSB);
        if (chn != null) {
          pinno = chn.getID();
        } else {
          pinno = 0;
        }
        pinnos.put(Util.getHashFromSeedname(seednameSB), pinno);
      }
      // If the data rate is less than 10 samples per second, aggregate before sending
      int[] ymd = SeedUtil.ymd_from_doy(year, day);
      gnow.set(ymd[0], ymd[1] - 1, ymd[2]);
      // Not this calculation of time is right even if husec is < 0
      gnow.setTimeInMillis(gnow.getTimeInMillis() / 86400000L * 86400000L + (husec + (husec > 0 ? 5 : -5)) / 10);
      if (nsamp < 10) {
        LPBuffer lp = lpbuffers.get(Util.getHashFromSeedname(seednameSB));
        if (lp == null) {
          lp = new LPBuffer(seednameSB, nsamp, minsamps, pinno);
          lpbuffers.put(Util.getHashFromSeedname(seednameSB), lp);
        }
        lp.process(gnow, nsamp, data);
      } else {
        if (hydra) {
          // inst, module, etc set by the HydraOutputer
          tb.setData(seednameSB, gnow.getTimeInMillis(), nsamp, (double) nsamp, data, 0, 0, 0, 0, pinno);
          Hydra.sendNoChannelInfrastructure(tb);
          if (dbgdata) {
            par.prta(seednameSB + " " + Util.asctime2(gnow) + " rt=" + nsamp + tb);
          }
        }
      }
    }
    if (dbgdata) {
      par.prta("doData completed.");
    }
  }

  /**
   * return a channel code and location given a Q680 channel number
   *
   * @param code The channel code
   * @return The 5 digit channel and code
   */
  /**
   * Build a command packet in cmdbb.
   *
   * @param anstype The answer type (command type)
   * @param time The NSNtime to put on the packet
   * @param nbytes The number of bytes in the payload (cmdbb(15) and on
   * @param cmdbb The command buf to build and to take the payload from
   */
  private void ansCommand(int anstype, byte[] time, int nbytes, ByteBuffer cmdbb) {
    cmdbb.position(0);
    cmdbb.put((byte) 27);
    cmdbb.put((byte) 3);
    cmdbb.putShort((short) (nbytes + 15));
    cmdbb.putInt(0);        // place holder for the net,node, chan, and seq not used anymore
    cmdbb.put(time, 0, 6);
    cmdbb.put((byte) anstype);
    //for(int i=0; i<nbytes+15; i++) par.prta("i="+i+" "+cmdbuf[i]);
    // The thing is done, now encode it for the wire
    encodeCommand(cmdbb);
  }

  /**
   * this encodes a Q680 command response which is in cmdbb for transmission
   * using the printable only scheme. The number of bytes to encode are suppose
   * to be in the nbytes position of the command packet (short at position 0).
   * The number of bytes in the returned cmdbb is (nb -2)*2 +2 and will be
   * positions there on exit
   *
   * @param cmdbb The ByteBuffer with an in-the-clear command packet to encode
   */
  private void encodeCommand(ByteBuffer cmdbb) {
    int nb = cmdbb.position();
    cmdbb.position(2);
    short nbytes = cmdbb.getShort();
    cmdbb.position(2);
    cmdbb.putShort((short) ((nbytes - 2) * 2 + 2)); // Put the encoded # of bytes in right place
    byte[] out = new byte[(nbytes - 2) * 2];
    ByteBuffer bb2 = ByteBuffer.wrap(out);
    bb2.position(0);
    cmdbb.position(2);
    for (int i = 2; i < nbytes; i++) {
      byte b2 = cmdbb.get();
      bb2.put((byte) (((b2 >> 4) & 0xf) + 32));
      bb2.put((byte) ((b2 & 0xf) + 32));
    }
    cmdbb.position(2);
    cmdbb.put(out);

  }

  private void sendAcks() throws IOException {
    try {
      ackbb.position(0);
      ackbb.put((byte) 27);
      ackbb.put((byte) 3);
      ackbb.putShort((short) 0);
      ackbb.putInt(0);      // route, node stat_chan and sec
      acktim.setTimeInMillis(System.currentTimeMillis());
      int yr = acktim.get(Calendar.YEAR);
      int doy = acktim.get(Calendar.DAY_OF_YEAR);
      int ms = (int) (acktim.getTimeInMillis() % 86400000L);
      ackbb.put((byte) ((yr - 1970) * 2 + doy / 256 * 256));
      ackbb.put((byte) (doy % 256));
      ackbb.putInt(ms * 16);
      ackbb.put((byte) 11);      // this is a ack
      ackbb.putInt((int) gaps.getLowestSeq());
      ackbb.putInt((int) gaps.getHighestSeq());
      ackbb.putInt(gaps.getGapCount());
      for (int i = 0; i < gaps.getGapCount(); i++) {
        ackbb.putInt((int) gaps.getLowSeq(i));
        ackbb.putInt((int) gaps.getHighSeq(i));
        if (ackbb.position() > 1500) {
          par.prta("***** too many gaps.  shorten acks #gaps=" + gaps.getGapCount());
          break;
        }
      }
      int len = ackbb.position();
      ackbb.position(2);
      ackbb.putShort((short) len);
      encodeCommand(ackbb);
      if (dbg) {
        par.prta(station + " SendGaps size=" + ackbb.position() + " len=" + len + " " + gaps.toString());
      }
      s.getOutputStream().write(ackbuf, 0, ackbb.position());
    } catch (RuntimeException e) {
      par.prta(" **** got a runtime exceptoin trying to sendAck from Q680Socket " + toString());
      e.printStackTrace(par.getPrintStream());
    }
  }

  public static String toTimeString(short yrdoy, int husecs) {
    int yr = (yrdoy / 367) + 2000;
    int doy = yrdoy % 367;
    if (husecs < 0) {
      int julian = SeedUtil.toJulian(yr, doy);
      julian--;
      int[] ymd = SeedUtil.fromJulian(julian);
      yr = ymd[0];
      doy = SeedUtil.doy_from_ymd(ymd);
      husecs += 864000000;
    }
    int hr = husecs / 36000000;
    husecs -= hr * 36000000;
    int min = husecs / 600000;
    husecs -= min * 600000;
    int sec = husecs / 10000;
    husecs -= sec * 10000;
    return "" + yr + " " + Util.df3.format(doy) + ":" + Util.df2.format(hr) + ":" + Util.df2.format(min) + ":" + Util.df2.format(sec) + "." + Util.df4.format(husecs);
  }

  /**
   * this internal class aggregates < 10 sps data into at least bigger chunks
   * for sending to TraceWire
   */
  class LPBuffer {

    int[] samps;               // buffering for samples
    int nsamp;                  // number of samples in samps 
    int rate;                   // data rates in Hz
    StringBuilder seedname = new StringBuilder(12);            // seedname of data
    int minsamps;               // The number of samples to cause output to Hydras
    int pinno;                  // Hydra Pin no.
    GregorianCalendar start;    // time of first sample in samps, if nsamps=0 the time of expected next data

    public LPBuffer(StringBuilder seed, int rt, int min, int pin) {
      minsamps = min;
      Util.clear(seedname).append(seed);
      Util.rightPad(seedname, 12);
      rate = rt;
      nsamp = -1;                 // indicates starting up.
      pinno = pin;
      samps = new int[minsamps + rt];// we ship whenever minsamps is exceeded, so buffer is minsamps + rate big
      start = new GregorianCalendar();// new gregorian, will be overriden with first buffer
      start.setTimeInMillis(1000000L);
      par.prta("LPB: NEW LPBUF for " + seedname + " min=" + min + " rt=" + rate + " pinno=" + pinno);
    }

    public void process(GregorianCalendar now, int ns, int[] samples) {
      //if(dbg) par.prta("LPB: new "+seednameSB+" ns="+ns+" "+Util.asctime2(now)+" start="+Util.asctime2(start)+" nsamp="+nsamp+" min="+minsamps);
      if (ns <= 0) {
        return;
      }
      if (now.getTimeInMillis() < start.getTimeInMillis()) {
        if (dbg) {
          par.prt("LPB: OOR add buffer " + seedname + " now=" + Util.ascdate(now) + " " + Util.asctime2(now)
                  + " start=" + Util.ascdate(start) + " " + Util.asctime2(start));
        }
        oorProcessor.addBuffer(samples, ns, seedname, now.get(Calendar.YEAR), now.get(Calendar.DAY_OF_YEAR),
                (int) ((now.getTimeInMillis() % 86400000L) / 1000), (int) ((now.getTimeInMillis() % 86400000L) % 1000) * 1000, (double) rate,
                activity, ioClock, quality, timeQual, false);
        return;
        // Nothing to do or its a old 2nd stream
      }
      if (nsamp >= 0) {    // if not starting up
        // check that this data is contiguous to prior buffering of data, if not ship prior.
        long diff = (now.getTimeInMillis() - start.getTimeInMillis() - nsamp * 1000 / rate);
        if (Math.abs(diff) > 500 / rate) {     // This data is out-of-order or overlapping
          if (diff < 0) {                    // overlapping
            par.prta("LPB:Overlapping LPBUF=" + seedname + " " + Util.asctime2(now) + " ns=" + ns + " rt=" + rate + " diff=" + diff);
            return;              // reject overlapping or past data
          }
          if (nsamp > 0) {       // Must be a gap, send any data bufferred to date, if any
            // Send 30 seconds of LH data to compressor
            if (dbg) {
              par.prt("LPB:Send LH data Gap " + seedname + " nsamp=" + nsamp + " "
                      + Util.ascdate(now) + " " + Util.asctime2(now) + " start=" + Util.ascdate(start) + " " + Util.asctime2(start));
            }
            oorProcessor.addBuffer(samps, nsamp, seedname, start.get(Calendar.YEAR), start.get(Calendar.DAY_OF_YEAR),
                    (int) ((start.getTimeInMillis() % 86400000L) / 1000), (int) ((start.getTimeInMillis() % 86400000L) % 1000) * 1000, (double) rate,
                    activity, ioClock, quality, timeQual, false);
            if (hydra) {
              tb.setData(seedname, start.getTimeInMillis(), nsamp, (double) rate, samps, 0, 0, 0, 0, pinno);  // inst, module, etc set by the HydraOutputer
              par.prta("LPB:Gap LPBuf =" + Util.asctime2(now) + " df=" + diff + " ns=" + ns + " rt=" + rate + " to " + tb.toString());
              Hydra.sendNoChannelInfrastructure(tb);
            }
            nsamp = 0;
          }
        }
      }
      if (nsamp <= 0) {
        start.setTimeInMillis(now.getTimeInMillis());
        if (nsamp == -1) {
          par.prta("LPB:LPBuf first data " + seedname + " " + Util.asctime2(now));
        }
        nsamp = 0;
      }
      System.arraycopy(samples, 0, samps, nsamp, ns); // put this data into the buffers
      nsamp += ns;                                    // Number of samples in buffer now.
      if (nsamp >= minsamps) {                        // is it ship time?
        if (dbg) {
          par.prt("LPB:Send LH data " + seedname + " nsamp=" + nsamp + " " + Util.asctime2(start));
        }
        oorProcessor.addBuffer(samps, nsamp, seedname, start.get(Calendar.YEAR), start.get(Calendar.DAY_OF_YEAR),
                (int) ((start.getTimeInMillis() % 86400000L) / 1000), (int) ((start.getTimeInMillis() % 86400000L) % 1000) * 1000, (double) rate,
                activity, ioClock, quality, timeQual, false);
        if (hydra) {
          tb.setData(seedname, start.getTimeInMillis(), nsamp, (double) rate, samps, 0, 0, 0, 0, pinno);  // inst, module, etc set by the HydraOutputer
          if (seedname.substring(0, 10).equals("USDUG  LHZ")) {
            par.prta("Send LPBuf =" + tb.toString());
          }
          Hydra.sendNoChannelInfrastructure(tb);
        }
        nsamp = 0;
        start.setTimeInMillis(now.getTimeInMillis() + ns * 1000 / rate);  // set expected time to OOR/overlap check works
      }
    }
  }

}
