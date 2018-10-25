/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.edgemom;
//import gov.usgs.alarm.SendEvent;

import gov.usgs.anss.edge.IndexFile;
import gov.usgs.anss.edge.IndexBlock;
import gov.usgs.anss.edge.ChannelSender;
import gov.usgs.alarm.SendEvent;
import gov.usgs.anss.edge.IllegalSeednameException;
import gov.usgs.anss.edgeoutput.RingFile;
import gov.usgs.anss.edgethread.EdgeThread;
import gov.usgs.anss.edgethread.ManagerInterface;
import gov.usgs.anss.seed.MiniSeed;
import gov.usgs.anss.util.Util;
import gov.usgs.anss.util.SimpleSMTPThread;
//import java.io.*;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.GregorianCalendar;

/**
 * This client connects to a datasock server, receives the data from that port,
 * and puts it into and edge database (optional) or directly to a RingFile for
 * use by a RRPClient(optional).
 * <p>
 * RingFile only : In many cases (like UCB), the edgemom is running on a remote
 * computer and edge data files are not desired. In this case, set -noedge and
 * turn on the ring mode only. At such sites, a companion RRPClient is run
 * against the RingFile and the data is forwarded to a Golden based RRPServer.
 * Notes: normally such sites have -noudpchan -nohydra and -noedge set. Only one
 * of the datasocks in this mode needs to configure the RingFile. The RingFile
 * is shared by all datasocks (its variables are static). The DatasockClients
 * that do not set up the ringfile need to have the -ringmode switch to tell
 * them they are using a RingFile and to wait for the the RingFile to be setup
 * by the one DatasockClient that configures it. Below is the original snip for
 * UCB as an example (note YBH sets up the ring and the others do not, these
 * datasocks use passwords but not station/channel selectors) :
 *
 * <PRE>
 * # set up the data socks
 * CMB:DatasockClient:-h athos.geo.berkeley.edu -p 5011 -pw bk2vdl -noudpchan -nohydra -noedge -ringmode >>cmb
 * HOPS:DatasockClient:-h athos.geo.berkeley.edu -p 5014 -pw bk2vdl -noudpchan -nohydra -noedge -ringmode >>hops
 * HUMO:DatasockClient:-h porthos.geo.berkeley.edu -p 5017 -pw bk2vdl -noudpchan -nohydra -noedge -ringmode >>humo
 * MCCM:DatasockClient:-h athos.geo.berkeley.edu -p 5018 -pw bk2vdl -noudpchan -nohydra -noedge -ringmode >>mccm
 * MOD:DatasockClient:-h porthos.geo.berkeley.edu -p 5016 -pw bk2vdl -noudpchan -nohydra -noedge -ringmode >>mod
 * SAO:DatasockClient:-h athos.geo.berkeley.edu -p 5012 -pw bk2vdl -noudpchan -nohydra -noedge -ringmode >>sao
 * WDC:DatasockClient:-h athos.geo.berkeley.edu -p 5013 -pw bk2vdl -noudpchan -nohydra -noedge -ringmode >>wdc
 * YBH:DatasockClient:-h athos.geo.berkeley.edu -p 5015 -pw bk2vdl  -noudpchan -nohydra -noedge -ringmode \
 *    -ring ucb -ringpath ./data -ringsize 50 -updatems 5000 >>ybh
 *
 * </PRE> Edge site : If this is a true edge site, do not set ringmode and do
 * not set -noedge and the data will go into edge files as normal.
 * <p>
 * It has the ability to expand if mixed sized packets are found. It will break
 * 4k packets into 512 packets by default This is not expected to happen for
 * datasocks but the code was inherited from LISSClient which already had this
 * feature.
 * <p>
 * <PRE>
 *
 *Switch			arg					Description
 * Connection to the datasock switches:
 * -h					host				The host ip address or DNS translatable name of the datasock server.
 * -p					pppp				The port to connect to on the datasock server.
 * -s					stations		If the datasock allows station, channel selection (most don't), then this is the station list.
 * -c					channels		If the datasock allows station, channel selection, then this is the list of channels (e.g. BHZ,BHN,BHE,LHZ,LHN,LHE).
 *	NOTE: If the data sock does not allow station, channel selection it will reject connections that have them!
 * -pw			  password		Datasock password (do not use this if the datasock does not have a password set up).
 *
 * Switches related to Edge behavior of the edge thread:
 * -dbg										Debug output is desired.
 * -noudpchan							Do not send results to the UdpChannel server for display in things like channel display.
 * -latency								Record latency of data in log file.
 * -nohydra								Do not send the resulting data to Hydra (usually used on test systems to avoid Hydra duplicates).
 * -allow4k								Allow 4k records to go straight to database, default is to break 4k packets up into 512 packets.
 * -l					lll					The initial  length of the data expected in bytes, this object will allow bigger buffers if B1000 indicate its needed.
 *  Ringfile output switches:
 * -noedge								Use ring only, do not put data into the edge.
 * -ringmode							Use a ring file for output and do not put data into edge files (all datasocks using the ring should set this).
 * -ring		  filename		Set the ring filename .ring; this will be appended to this name.
 *                  (only one datasock client should have this set, the others will wait for it to open)
 * -ringpath	path				Set the path to the ring file (def=.).
 * -ringsize	nnn					Size of ring file in MB (def=100).
 * -updatems	nnn					Length of time to wait before updating the header block (def=5000).
 *
 *
 * </PRE>
 *
 * @author davidketchum  <ketchum at usgs.gov>
 */
public final class DatasockClient extends EdgeThread implements ManagerInterface {

  private static RingFile ring;
  private static String ringFilename;
  private static String ringPath = ".";
  private static int updateMS = 5000;
  private static int ringsize = 100;
  private static final long mask = 1;
  private static boolean ringMode;
  boolean edgeMode;   // If true, put this station in the edge (overriden by -noedge)
  int port;
  String host;
  int countMsgs;
  int totMsgs;
  int heartBeat;
  int msgLength;
  Socket d;           // Socket to the server
  long lastStatus;     // Time of last status was received
  InputStream in;
  OutputStream outsock;
  ChannelSender csend;
  DatasockMonitor monitor;
  boolean recordLatency;
  PrintStream loglat;
  String latFilename;
  long minLatency, maxLatency, avgLatency;
  int nlatency;
  long inbytes;
  long outbytes;
  boolean dbg;
  boolean hydra;
  boolean allow4k;
  boolean check;
  String argsin;
  int pt;
  String h;
  boolean nocsend;
  int packetLength;
  String password;
  String stations;
  String channels;

  @Override
  public void setCheck(boolean t) {
    check = t;
  }

  @Override
  public boolean getCheck() {
    return check;
  }

  @Override
  public String getArgs() {
    return argsin;
  }

  public void setDebug(boolean t) {
    dbg = t;
  }

  /**
   * Creates an new instance of DatasockClient - which will try to stay
   * connected to the host/port source of data. This one gets its arguments from
   * a command line.
   *
   * @param argline EdgeThread args.
   * @param tg The EdgeThread tag.
   */
  public DatasockClient(String argline, String tg) {
    super(argline, tg);
    argsin = argline;
//initThread(argline,tg);
    restart(argline);
    create(h, pt, packetLength);

  }

  public void restart(String argline) {
    String[] args = argline.split("\\s");
    dbg = false;
    packetLength = 512;
    h = null;
    pt = 0;
    nocsend = false;
    allow4k = false;
    latFilename = tag;
    hydra = true;
    edgeMode = true;
    for (int i = 0; i < args.length; i++) {
      //prt(i+" arg="+args[i]);
      // These are parameters for the data sock
      if (args[i].equals("-h")) {
        h = args[i + 1];
        i++;
      } else if (args[i].equals("-p")) {
        pt = Integer.parseInt(args[i + 1]);
        i++;
      } else if (args[i].equals("-s")) {
        stations = args[i + 1];
        i++;
      } else if (args[i].equals("-c")) {
        channels = args[i + 1];
        i++;
      } else if (args[i].equals("-pw")) {
        password = args[i + 1];
        i++;
      } // These are parameters that control the operation of this data sock on the edge
      else if (args[i].equals("-l")) {
        packetLength = Integer.parseInt(args[i + 1]);
        i++;
      } else if (args[i].equals("-dbg")) {
        dbg = true;
      } else if (args[i].equals("-noudpchan")) {
        nocsend = true;
      } else if (args[i].equals("-nohydra")) {
        hydra = false;
      } else if (args[i].equals("-latency")) {
        recordLatency = true;
      } else if (args[i].equals("-allow4k")) {
        allow4k = true;
      } // These are for the ring static setup
      else if (args[i].equals("-noedge")) {
        edgeMode = false;
      } else if (args[i].equals("-ringmode")) {
        ringMode = true;
      } else if (args[i].equals("-ring")) {
        ringFilename = args[i + 1];
        i++;
        ringMode = true;
      } else if (args[i].equals("-ringpath")) {
        ringPath = args[i + 1];
        i++;
      } else if (args[i].equals("-updatems")) {
        updateMS = Integer.parseInt(args[i + 1]);
        i++;
      } else if (args[i].equals("-ringsize")) {
        ringsize = Integer.parseInt(args[i + 1]);
        i++;
      } else if (args[i].substring(0, 1).equals(">")) {
        break;
      } else {
        prt("DSOCK client unknown switch=" + args[i] + " ln=" + argline);
      }
    }
    prt("DatasockClient: new line parsed to host=" + h + " port=" + pt + " len=" + packetLength + " dbg=" + dbg);
    if (!nocsend && csend == null) {
      csend = new ChannelSender("  ", "DatasockClient", "LC-" + tag);
    }

    // If the socket is open, reopen it with the new parameters
    if (d != null) {
      if (!d.isClosed()) {
        try {
          d.close();
        } catch (IOException e) {
        }
      }
    }
  }

  private void create(String h, int pt, int len) {
    port = pt;
    host = h;
    countMsgs = 0;
    heartBeat = 0;
    msgLength = len;
    IndexFile.init();
    tag += "DS:";  // With String and type
    monitor = new DatasockMonitor(this);
    start();
  }

  @Override
  public void terminate() {
    terminate = true;
    if (in != null) {
      prta(tag + " Terminate started. Close input unit.");
      try {
        in.close();
      } catch (IOException e) {
      }
      interrupt();
    } else {
      prt(tag + " Terminate started. interrupt().");
      interrupt();
    }
    monitor.terminate();
  }

  @Override
  public String toString() {
    return tag + " " + host + "/" + port + " msgLen=" + msgLength + " nbIn=" + inbytes + " #hb=" + heartBeat;
  }

  /**
   * This thread keeps a socket open to the host and port, reads in any
   * information on that socket, keeps a list of unique StatusInfo, and updates
   * that list when new data for the unique key comes in.
   */
  @Override
  public void run() {
    byte[] buf = new byte[msgLength];
    MiniSeed ms = null;
    int nillegal = 0;
    running = true;
    int iday = 0;       // Mark that we are running
    long isleep = 30000;
    if (ringMode) {
      if (ringFilename != null) {
        while (ring == null) {
          try {
            ring = new RingFile(ringPath, ringFilename, updateMS, ringsize, 1L, 4L, true, "-allowlog", this);
          } catch (IOException e) {
            if (nillegal % 30 == 0) {
              SimpleSMTPThread.email(Util.getProperty("emailTo"), "DataSockClient ring file error",
                      Util.ascdate() + " " + Util.asctime() + " " + Util.getNode() + "\n"
                      + "e=" + e + "\nThis email comes from DataSockRing with tag=" + tag + " " + host + "/" + port + "\n");
              SendEvent.edgeSMEEvent("DataSockRing", "DataSockClient ring file error " + Util.getNode() + "/" + Util.getAccount() + " " + Util.getProcess() + " "
                      + "e=" + e, this);
            }
            Util.prt(tag + " could not open ring file e=" + e);
            nillegal++;
            try {
              sleep(120000);
            } catch (InterruptedException e2) {
            }
          }
        }
      } else {
        while (ring == null) {
          try {
            sleep(1000);
          } catch (InterruptedException e) {
          }
        }
      }
    }     // End if this is a ring mode setup
    while (true) {
      try {
        if (terminate) {
          break;
        }
        /* Keep trying until a connection is made */
        int loop = 0;
        while (true) {
          if (terminate) {
            break;
          }
          try { // Make sure anything we have open can be let go
            if (d != null) {
              try {
                if (!d.isClosed()) {
                  d.close();
                }
                if (in != null) {
                  in.close();
                }
                if (outsock != null) {
                  outsock.close();
                }
              } catch (IOException e) {
              }
            }
            prta(tag + " Open Port=" + host + "/" + port + " msgLen=" + msgLength);
            d = new Socket(host, port);
            in = d.getInputStream();        // Get input and output streams
            outsock = d.getOutputStream();
            StringBuilder rq = new StringBuilder(1000);
            String s = "100 PASSWD " + password + "\n";
            if (password != null) {
              prt("SEND: " + s);
              outsock.write(s.getBytes());
            }
            if (channels != null) {
              s = "110 " + stations.trim() + " " + channels.trim() + "\n";
              prt("SEND: " + s);
            }
            //outsock.write(s.getBytes());
            s = "199 EOT\n";
            prt("SEND: " + s);
            outsock.write(s.getBytes());
            // Build first 100 StatusInfo objects and fill with empty data
            lastStatus = System.currentTimeMillis();
            break;
          } catch (UnknownHostException e) {
            prt(tag + " Host is unknown=" + host + "/" + port + " loop=" + loop);
            if (loop % 30 == 0) {
              SimpleSMTPThread.email(Util.getProperty("emailTo"), "DSOCK host unknown=" + host + " " + Util.getNode(),
                      Util.ascdate() + " " + Util.asctime() + " " + Util.getNode() + " " + Util.getProcess() + "\n"
                      + "This message comes from the DatasockClient when the host computer is unknown,\nIs DNS up?  Loop % 30 is 0\n"
                      + Util.ascdate() + " " + Util.asctime() + " loop=" + loop);
            }
            if (loop % 30 == 1) {
              SendEvent.edgeSMEEvent("HostUnknown", "DSOCK host unknown=" + host, this);
            }
            loop++;
            try {
              sleep(120000L);
            } catch (InterruptedException e2) {
            }
          } catch (IOException e) {
            if (e.getMessage().equalsIgnoreCase("Connection refused")) {
              prta(tag + " Connection refused.  wait " + (isleep / 1000) + " secs ....");
              try {
                sleep(isleep);
              } catch (InterruptedException e2) {
              }
              isleep = isleep * 2;
              if (isleep > 600000) {
                isleep = 600000;
              }
              if (isleep >= 3600000) {
                SimpleSMTPThread.email(Util.getProperty("emailTo"), "'" + tag + " DSOCK " + host + "/" + port + ":" + Util.getNode() + " repeatedly refused'",
                        Util.ascdate() + " " + Util.asctime() + " " + Util.getNode() + " " + Util.getProcess() + "\n"
                        + "This message comes from the DatasockClient when a connection is repeatedly refused,\n"
                        + "Is DSOCK server up?  This message will repeat on escalting scale. next=" + (isleep / 1000) + "\n");
              }
              try {
                sleep(isleep);
              } catch (InterruptedException e2) {
              }
            } else if (e.getMessage().equalsIgnoreCase("Connection timed out")
                    || e.getMessage().equalsIgnoreCase("Operation timed out")) {
              prta(tag + " Connection timed out.  wait " + isleep / 1000 + " secs ....");
              try {
                sleep(isleep);
              } catch (InterruptedException e2) {
              }
              isleep = isleep * 2;
              if (isleep > 600000) {
                isleep = 600000;
              }
              try {
                sleep(isleep);
              } catch (InterruptedException e2) {
              }
            } else {
              Util.IOErrorPrint(e, tag + " IO error opening socket=" + host + "/" + port);
              try {
                sleep(120000L);
              } catch (InterruptedException e2) {
              }
            }

          }

        }   // While True on opening the socket
        if (terminate) {
          break;
        }
        prta(tag + " local=" + d.getLocalPort() + " Socket is opened.  Start reads.");
        tag = tag.substring(0, tag.indexOf(":") + 1) + d.getLocalPort() + " ";
        prta(tag + " is now changed!");
        isleep = 60000;
        // Read data from the socket and update/create the list of records 
        int len;
        int l;
        int nchar = 0;
        int blockSize = 0;
        GregorianCalendar now = new GregorianCalendar();
        while (true) {
          if (terminate) {
            break;
          }
          try {
            len = 512;                // Desired block length may be larger
            l = 0;                    // Offset into buf for next read
            nchar = 0;                // Number of characters returned
            int loops = 0;
            while (len > 0) {            // 
              while (in.available() <= 0) {
                try {
                  sleep(10);
                } catch (InterruptedException e) {
                }
              }
              nchar = Util.socketRead(in, buf, l, len);// get nchar
              if (nchar <= 0) {
                prta("512 EOF read nchar=" + nchar + " len=" + len + " in.avail=" + in.available());
                break;
              }     // EOF - close up
              if (inbytes == 0 && buf[0] > '9') {
                for (int i = 0; i < 512; i++) {
                  if (buf[i] == 0) {
                    prta("Err:" + new String(buf, 0, i - 1));
                    break;
                  }
                }
                terminate();
              }
              l += nchar;               // Update the offset
              if (blockSize == 0) {
                if (l > 64) {
                  if (!MiniSeed.crackIsHeartBeat(buf)) {
                    blockSize = MiniSeed.crackBlockSize(buf);
                    if (blockSize != msgLength) {
                      prta("Found packet of different len=" + blockSize + "!=" + msgLength + " expected");
                    }
                  }
                }
              }
              len -= nchar;             // Reduce the number left to read
              loops++;
              if (dbg) {
                prta("512 read loop " + loops + " len=" + len + " ncha=" + nchar + " l=" + l + " blkSize=" + blockSize);
              }
              if (loops % 100 == 10) {
                prt("loops = " + loops + " len=" + len + " ncha=" + nchar + " l=" + l);
              }
            }
            inbytes += l;
            if (nchar <= 0) {
              break;      // EOF - close up - go to outer infinite loop
            }
            now.setTimeInMillis(System.currentTimeMillis());
            if (!MiniSeed.crackIsHeartBeat(buf)) {
              if (ms == null) {
                ms = new MiniSeed(buf);
              } else if (MiniSeed.crackBlockSize(buf) < buf.length) {
                ms = new MiniSeed(buf);
              } else {
                ms.load(buf);
              }
              if (ms.getBlockSize() > 512) {     // Bigger mini-seed: read it in
                if (buf.length < ms.getBlockSize()) {
                  byte[] buf2 = new byte[buf.length];
                  System.arraycopy(buf, 0, buf2, 0, buf.length);
                  buf = new byte[ms.getBlockSize()];
                  System.arraycopy(buf2, 0, buf, 0, buf2.length);
                }
                len = ms.getBlockSize() - l;
                while (len > 0) {            // 
                  nchar = Util.socketRead(in, buf, l, len);// Get nchar
                  if (nchar <= 0) {
                    break;     // EOF - close up
                  }
                  l += nchar;               // Update the offset
                  len -= nchar;             // Reduce the number left to read
                  if (dbg) {
                    prta("rest read read loop " + loops + " len=" + len + " ncha=" + nchar + " l=" + l);
                  }
                }
                if (nchar <= 0) {
                  break;       // EOF exit loop
                }
                ms.load(buf);
                inbytes += l;
              }
              countMsgs++;
              totMsgs++;
              // The following lines measure the latency of sends and write them into latency files
              int[] t = MiniSeed.crackTime(buf);
              long lat = t[0] * 3600000L + t[1] * 60000L + t[2] * 1000L + t[3] / 10 + ((long) (MiniSeed.crackNsamp(buf) * 1000. / MiniSeed.crackRate(buf)));
              lat = (System.currentTimeMillis() % 86400000L) - lat;
              if (lat < 0) {
                lat += 86400000;
              }
              if (MiniSeed.crackSeedname(buf).substring(7, 10).equals("BHZ")) {
                if (lat < minLatency) {
                  minLatency = lat;
                }
                if (lat > maxLatency) {
                  maxLatency = lat;
                }
                avgLatency += lat;
                nlatency++;
              }

              // Is it time for status yet?
              nillegal = 0;                 // Must be a legal seed name
              if (recordLatency) {
                long latency = System.currentTimeMillis() - ms.getTimeInMillis() - ((long) (ms.getNsamp() / ms.getRate() * 1000.));
                prta(latency + " " + ms.getSeedNameString() + " " + ms.getTimeString() + " ns=" + ms.getNsamp() + " " + ms.getRate() + " latR");
              }
              if (dbg) {
                prta(ms.toString());
              }
              if (ringMode) {
                ring.writeNext(buf, 0, MiniSeed.crackBlockSize(buf));
              }
              if (edgeMode) {
                IndexBlock.writeMiniSeedCheckMidnight(ms, hydra, allow4k, tag, this);
              }

              // Send to UdpChannel server
              if (csend != null) {
                csend.send(ms.getJulian(),
                        (int) (ms.getTimeInMillis() % 86400000), // Milliseconds
                        ms.getSeedNameSB(), ms.getNsamp(), ms.getRate(), ms.getBlockSize());  // Key,nsamp,rate and nbytes
              }
            } else {
              if (dbg) {
                Util.prta("Heart beat");
              }
              heartBeat++;
            }
            if ((System.currentTimeMillis() - lastStatus) > 600000) {
              prta(tag + " # Rcv=" + countMsgs + " " + toString());
              countMsgs = 0;
              lastStatus = System.currentTimeMillis();
            }
          } catch (IOException e) {
            if (e.getMessage().contains("Socket closed") || e.getMessage().contains("Stream closed")) {
              if (terminate) {
                prta("Doing termination via Socket close.");
                break;
              } else {
                if (monitor.didInterrupt()) {
                  prta(tag + " Socket closed by Monitor - reopen");
                  break;
                }
              }
              prta(tag + " Unexplained socket closed e=" + e.getMessage());
              break;
            } else {
              prta("DatasockClient: receive through IO exception e=" + e.getMessage());
              e.printStackTrace(getPrintStream());
            }
            break;      // Drop out of read loop. Enter connect loop
          } catch (IllegalSeednameException e) {
            nillegal++;
            if (ms != null) {
              prta(tag + " IllegalSeedName =" + nillegal + " "
                      + ms.getSeedNameString() + " " + e.getMessage());
            } else {
              prta(tag + " IllegalSeedName =" + nillegal + " ms is null. "
                      + e.getMessage());
            }
            for (int i = 0; i < 48; i++) {
              prt(i + " = " + buf[i] + " " + (char) buf[i]);
            }
            if (nillegal > 3) {
              terminate = true;    // If 3 in a row, then close connection
            }
          }

        }     // while(true) Get data
        try {
          if (nchar <= 0 && !monitor.didInterrupt()) {
            prta(" exited due to EOF char=" + nchar + " terminate=" + terminate);
          } else if (terminate) {
            prt(" terminate found.");
          } else {
            prta(tag + " exited while(true) for read");
          }
          if (d != null && !d.isClosed()) {
            d.close();
          }
        } catch (IOException e) {
          prta(tag + " socket- reopen e=" + (e == null ? "null" : e.getMessage()));
          if (e != null) {
            e.printStackTrace(this.getPrintStream());
          }
        }
      } catch (RuntimeException e) {
        prta(tag + "LC: RuntimeException in " + this.getClass().getName() + " e=" + e.getMessage());
        if (getPrintStream() != null) {
          e.printStackTrace(getPrintStream());
        } else {
          e.printStackTrace();
        }
        if (e.getMessage() != null) {
          if (e.getMessage().contains("OutOfMemory")) {
            SendEvent.edgeEvent("OutOfMemory", "Out of Memory in " + Util.getProcess() + "/" + this.getClass().getName() + " on " + IndexFile.getNode(), this);
            SimpleSMTPThread.email(Util.getProperty("emailTo"),
                    "Out of Memory in " + Util.getProcess() + "/" + this.getClass().getName() + " on " + IndexFile.getNode(),
                    Util.asctime() + " " + Util.ascdate() + " Body");
            throw e;
          }
        }
      }
    }       // While(true) do socket open
    monitor.terminate();
    prt(tag + " is terminated.");
    if (csend != null) {
      csend.close();
    }
    running = false;
    terminate = false;
  }

  /**
   * return the monitor string for Icinga
   *
   * @return A String representing the monitor key value pairs for this
   * EdgeThread
   */
  @Override
  public StringBuilder getMonitorString() {
    if (monitorsb.length() > 0) {
      monitorsb.delete(0, monitorsb.length());
    }
    return monitorsb;
  }

  @Override
  public StringBuilder getStatusString() {
    if (statussb.length() > 0) {
      statussb.delete(0, statussb.length());
    }
    statussb.append("#rcv=").append(totMsgs).append(" hb=").append(heartBeat);
    totMsgs = 0;
    return statussb;
  }

  @Override
  public StringBuilder getConsoleOutput() {
    return consolesb;
  } //  We use prt directly

  /**
   * Monitor the DatasockClient and stop it if it does not receive heartBeats or
   * data!
   */
  private final class DatasockMonitor extends Thread {

    boolean terminate;
    int lastHeartbeat;
    long lastInbytes;
    int msWait;
    boolean interruptDone;
    DatasockClient thr;

    public boolean didInterrupt() {
      return interruptDone;
    }

    public void terminate() {
      terminate = true;
      interrupt();
    }

    public DatasockMonitor(DatasockClient t) {
      thr = t;
      msWait = 360000;
      gov.usgs.anss.util.Util.prta("new ThreadMonitor " + getName() + " " + getClass().getSimpleName());
      start();
    }

    @Override
    public void run() {
      //try{sleep(msWait);} catch(InterruptedException e) {}
      while (!terminate) {
        long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < msWait) {
          try {
            sleep(msWait - (System.currentTimeMillis() - start));
          } catch (InterruptedException e) {
          }
          if (terminate) {
            break;
          }
        }
        if (terminate) {
          break;
        }
        //prta(tag+" LCM: HB="+heartBeat+" lHB="+lastHeartbeat+" in ="+inbytes+" lin="+lastInbytes);
        if (lastHeartbeat == heartBeat && inbytes - lastInbytes < 2000) {
          thr.interrupt();      // Interrupt in case its in a wait
          if (d != null) {
            try {
              if (!d.isClosed()) {
                d.close();  // Force IO abort by closing the socket
              }
            } catch (IOException e) {
              prta(tag + " LCM: close socket IOException=" + e.getMessage());
            }
          }
          interruptDone = true;     // So interrupter can know it was us!
          prta(tag + " LCM: monitor has gone off HB=" + heartBeat + " lHB=" + lastHeartbeat + " in =" + inbytes + " lin=" + lastInbytes);
          try {
            sleep(msWait);
          } catch (InterruptedException e) {
          }
          if (terminate) {
            break;
          }
          interruptDone = false;
        }
        lastInbytes = inbytes;
        lastHeartbeat = heartBeat;
      }
      prta(tag + " LCM: has been terminated");
    }

  }

  /**
   * This is the test code for DatasockClient.
   *
   * @param args The command line arguments.
   */
  public static void main(String[] args) {
    /*IndexFile.init();
    EdgeProperties.init();
    DatasockClient [] DSOCK=  new DatasockClient[10];
    IndexFile.setDebug(true);
    int i=0;
    DSOCK[i++] = new DatasockClient("-h poha.iu.LISS.org -p 4000 -noudpchan -nohydra -noedge -ringmode -dbg","POHA");
    DSOCK[i++] = new DatasockClient("-h saml.iu.LISS.org -p 4000 -noudpchan -nohydra -noedge -ringmode -dbg","SAML");
    DSOCK[i++] = new DatasockClient("-h trqa.iu.LISS.org -p 4000 -noudpchan -nohydra -noedge -ringmode -dbg","TRQA");
    DSOCK[i++] = new DatasockClient("-h bill.iu.LISS.org -p 4000 -noudpchan -nohydra -noedge -ringmode -dbg","BILL");
    DSOCK[i++] = new DatasockClient("-h majo.iu.LISS.org -p 4000 -noudpchan -nohydra -noedge -ringmode -dbg","MAJO");
    DSOCK[i++] = new DatasockClient("-h nwao.iu.LISS.org -p 4000 -noudpchan -nohydra -noedge -ringmode -dbg","NWAO");
    DSOCK[i++] = new DatasockClient("-h pmsa.iu.LISS.org -p 4000 -noudpchan -nohydra -noedge -ringmode -dbg","PMSA");
    DSOCK[i++] = new DatasockClient("-h otav.iu.LISS.org -p 4000 -noudpchan -nohydra -noedge -ringmode -dbg","OTAV");
    DSOCK[i++] = new DatasockClient("-h tuc.iu.LISS.org -p 4000 -noudpchan -nohydra -noedge -ringmode -dbg "+
            "-ring test -ringpath /data -ringsize 10 -updatems 10000","TUC");
     */
  }

}
