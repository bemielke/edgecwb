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
import gov.usgs.anss.edge.EdgeProperties;
import gov.usgs.anss.edge.ChannelSender;
import gov.usgs.alarm.SendEvent;
import gov.usgs.anss.edge.IllegalSeednameException;
import gov.usgs.anss.edgethread.EdgeThread;
import gov.usgs.anss.edgethread.ManagerInterface;
import gov.usgs.anss.seed.MiniSeed;
import gov.usgs.anss.util.*;
import java.io.OutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.net.UnknownHostException;
import java.io.PrintStream;
import java.net.Socket;
import java.util.Calendar;
import java.util.GregorianCalendar;

/**
 * This client connects to a Netserv server, receives the data from that port
 * and puts it into and edge database. It has the ability to expand if mixed
 * sized packets are found. It will break 4k packets into 512 packets by
 * default. This client is a descendant of LISSClient since Netserv is so close
 * to being LISS. If the GSNManager is used and configuration is from the GUI,
 * then the host and port come from the GUI elements for those data. Only the
 * additional options go in the Options field of the GUI.
 *
 * <PRE>
 *Switch   arg        Description
 * Options configured automatically by GUI:
 *-h       host    (auto from GUI) The host ip address or DNS translatable name of the Netserv server.
 *-p       pppp    (auto from GUI) The port to connect to on the Netserv server.
 * Additional for 'Options' section of GUI:
 *-dbg             Debug output is desired.
 *-noudpchan       Do not send results to the UdpChannel server for display in things like channel display.
 *-latency         Record latency of data in log file.
 *-nohydra         Do not send the resulting data to Hydra (usually used on test systems to avoid Hydra duplicates).
 * </PRE>
 *
 * @author davidketchum
 */
public final class NetservClient extends EdgeThread implements ManagerInterface {

  private int port;
  private String host;
  private String bind;
  private int countMsgs;
  private int heartBeat;
  private int msgLength;
  private Socket d;           // Socket to the server
  private GregorianCalendar lastStatus;     // Time last status was received
  private InputStream in;
  private OutputStream outsock;
  private ChannelSender csend;
  private NetservMonitor monitor;
  private boolean recordLatency;
  private PrintStream loglat;
  private String latFilename;
  //private String station="";
  private long minLatency, maxLatency, avgLatency;
  private int nlatency;
  private long inbytes;
  private long outbytes;
  private boolean dbg;
  private boolean hydra;
  private boolean allow4k;
  private boolean check;
  private String argsin;
  //private int pt;
  private String h;
  private boolean nocsend;
  private int packetLength;

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

  @Override
  public long getBytesIn() {
    return inbytes;
  }

  @Override
  public long getBytesOut() {
    return outbytes;
  }

  /**
   * Creates an new instance of NetservCLient - which will try to stay connected
   * to the host/port source of data. This one gets its arguments from a command
   * line
   *
   * @param argline EdgeThread args.
   * @param tg The EdgeThread tag.
   */
  public NetservClient(String argline, String tg) {
    super(argline, tg);
    argsin = argline;
//initThread(argline,tg);
    restart(argline);
    create(h, port, packetLength);

  }

  public final void restart(String argline) {
    String[] args = argline.split("\\s");
    dbg = false;
    packetLength = 512;
    h = null;
    port = 0;
    nocsend = false;
    allow4k = false;
    latFilename = tag;
    hydra = true;

    for (int i = 0; i < args.length; i++) {
      //prt(i+" arg="+args[i]);
      if (args[i].equals("-h")) {
        h = args[i + 1];
        i++;
      } else if (args[i].equals("-p")) {
        port = Integer.parseInt(args[i + 1]);
        i++;
      } else if (args[i].equals("-dbg")) {
        dbg = true;
      } else if (args[i].equals("-noudpchan")) {
        nocsend = true;
      } else if (args[i].equals("-nohydra")) {
        hydra = false;
      } else if (args[i].equals("-latency")) {
        recordLatency = true;
      } else if (args[i].equals("-bind")) {
        bind = args[i + 1];
        i++;
      } else if (args[i].substring(0, 1).equals(">")) {
        break;
      } else {
        prt("Netserv client unknown switch=" + args[i] + " ln=" + argline);
      }
    }
    prt("NetservClient: new line parsed to host=" + h + " port=" + port + " len=" + packetLength + " dbg=" + dbg);

    if (!nocsend && csend == null) {
      csend = new ChannelSender("  ", "NetservClient", "LC-" + tag);
    }

    // If the socket is open, reopen it with the new parameters
    if (d != null) {
      if (!d.isClosed()) {
        try {
          d.close();
        } catch (IOException expected) {
        }
      }
    }
  }

  /**
   * Creates a new instance of NetservClient - tries to stay connected to the
   * given ip address and port and forwards any received data via
   * IndexBlock.miniSeedWrite().
   *
   * @param h The host IP or DNS convertible address of the netserv server.
   * @param pt The port on the netserv server to contact.
   * @param len The initial size of the input buffer (can grow if B1000
   * indicates it should).
   */
  public NetservClient(String h, int pt, int len) {
    super("", h + "/" + pt);
    create(h, pt, len);
  }

  private void create(String h, int pt, int len) {
    port = pt;
    host = h;
    countMsgs = 0;
    heartBeat = 0;
    msgLength = len;
    IndexFile.init();
    tag += "NSrv:";  // With String and type
    monitor = new NetservMonitor(this);
    start();
  }

  @Override
  public void terminate() {
    new RuntimeException("Tracking terminate in netserver").printStackTrace(getPrintStream());
    terminate = true;
    if (in != null) {
      prta(tag + " Terminate started. Close input unit.");
      try {
        in.close();
      } catch (IOException expected) {
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
   * This Thread keeps a socket open to the host and port, reads in any
   * information on that socket, keeps a list of unique StatusInfo, and updates
   * that list when new data for the unique key comes in.
   */
  @Override
  public void run() {
    byte[] buf = new byte[msgLength];
    MiniSeed ms = null;
    int nillegal = 0;
    running = true;
    int iday = 0;       // Mark we are running
    long isleep = 30000;
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
              } catch (IOException expected) {
              }
            }
            prta(tag + " Open Port=" + host + "/" + port + " bind=" + bind + " msgLen=" + msgLength);
            d = makeBoundSocket(d, host, port, bind);
            //d = new Socket(host, port);
            in = d.getInputStream();        // Get input and output streams
            outsock = d.getOutputStream();

            // Build first 100 StatusInfo objects and fill with empty data
            lastStatus = new GregorianCalendar();
            break;
          } catch (UnknownHostException e) {
            prt(tag + " Host is unknown=" + host + "/" + port + " loop=" + loop);
            if (loop % 30 == 0) {
              SendEvent.edgeSMEEvent("NetSrvNoHost", "Netserv no host host=" + host + " " + Util.getRoles(null)[0], this);
            }
            if (loop % 30 == 1) {
              SendEvent.edgeSMEEvent("HostUnknown", "Netserv host unknown=" + host, this);
            }
            loop++;
            try {
              sleep(120000L);
            } catch (InterruptedException expected) {
            }
          } catch (IOException e) {
            if (e.getMessage().equalsIgnoreCase("Connection refused")) {
              prta(tag + " Connection refused.  wait " + (isleep / 1000) + " secs ....");
              try {
                sleep(isleep);
              } catch (InterruptedException e2) {
              }
              isleep = isleep * 2;
              if (isleep > 900000) {
                isleep = 900000;
              }
              if (isleep >= 900000) {
                SendEvent.debugSMEEvent("NetSrvConn", "Netserv " + host + "/" + port + ":" + Util.getRoles(null)[0] + " refused " + tag, this);
              }
              try {
                sleep(isleep);
              } catch (InterruptedException expected) {
              }
            } else if (e.getMessage().equalsIgnoreCase("Connection timed out")
                    || e.getMessage().equalsIgnoreCase("Operation timed out")) {
              prta(tag + " Connection timed out.  wait " + isleep / 1000 + " secs ....");
              try {
                sleep(isleep);
              } catch (InterruptedException expected) {
              }
              isleep = isleep * 2;
              if (isleep > 1800000) {
                isleep = 1800000;
              }
              try {
                sleep(isleep);
              } catch (InterruptedException expected) {
              }
            } else {
              Util.IOErrorPrint(e, tag + " IO error opening socket=" + host + "/" + port);
              try {
                sleep(120000L);
              } catch (InterruptedException expected) {
              }
            }

          }

          //try {sleep(10000L);} catch (InterruptedException expected) {}
        }   // While true on opening the socket
        if (terminate) {
          break;
        }
        prta(tag + " local=" + d.getLocalPort() + " Socket is opened.  Start reads.");
        prta(tag + " is now changed!");
        isleep = 60000;
        // Read data from the socket and update/create the list of records 
        int len;
        int l;
        int nchar = 0;
        int blockSize = 0;
        int[] data;      // Need to decompress the data
        GregorianCalendar now = new GregorianCalendar();
        int goodYear = now.get(Calendar.YEAR) - 2;
        while (true) {
          if (terminate) {
            break;
          }
          try {
            len = 512;                // Desired block length may be larger
            l = 0;                    // Offset into buf for next read
            nchar = -99;                // Number of characters returned
            int loops = 0;
            while (len > 0) {            // 
              while (in.available() <= 0) {
                try {
                  sleep(10);
                } catch (InterruptedException expected) {
                }
              }
              nchar = in.read(buf, l, len);// Get nchar
              if (nchar <= 0) {
                prta(tag + " 512 read nchar=" + nchar + " len=" + len + " in.avail=" + in.available());
                break;
              }     // EOF - close up
              l += nchar;               // Update the offset
              if (blockSize == 0) {
                if (l > 64) {
                  if (!MiniSeed.crackIsHeartBeat(buf)) {
                    blockSize = MiniSeed.crackBlockSize(buf);
                    if (blockSize != msgLength) {
                      prta(tag + "  **** Found packet of different len=" + blockSize + "!=" + msgLength + " expected. abort....");
                      break;
                    }
                  }
                }
              }
              len -= nchar;             // Reduce the number left to read
              loops++;
              if (dbg) {
                prta(tag + " 512 read loop " + loops + " len=" + len + " ncha=" + nchar + " l=" + l + " blkSize=" + blockSize);
              }
              if (loops % 100 == 10) {
                prt(tag + " loops = " + loops + " len=" + len + " ncha=" + nchar + " l=" + l);
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
                tag = tag.substring(0, tag.indexOf(":") + 1) + "[" + d.getLocalPort() + "]";
              } else {
                ms.load(buf);
              }
              countMsgs++;
              // The following measure latency of sends and write them in latency files.
              int[] t = MiniSeed.crackTime(buf);
              if (ms.getYear() < goodYear) {
                prta(tag + "  *** year is out of range.  discard packet year=" + ms);
                continue;
              }
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
              /*if(System.currentTimeMillis() / 86400000L != iday) {
                if(loglat != null) loglat.close();
                iday =(int) (System.currentTimeMillis() / 86400000L);
                try {
                    loglat = new PrintStream(
                        new FileOutputStream(
                          Util.getProperty("logfilepath")+latFilename.trim()+".lclat"+EdgeThread.EdgeThreadDigit(), true));
                }
                catch(FileNotFoundException e) {}
              }
              if(out != null && buf[17] == 'Z') loglat.println(
                  Util.leftPad(""+(System.currentTimeMillis()%86400000L),9)+Util.leftPad(""+lat,10)+" "+
                  MiniSeed.crackSeedname(buf));
               */

              // Is it time for status yet
              nillegal = 0;                 // Must be a legal seed name
              if (recordLatency) {
                long latency = System.currentTimeMillis() - ms.getTimeInMillis() - ((long) (ms.getNsamp() / ms.getRate() * 1000.));
                prta(tag + " " + latency + " " + ms.getSeedNameSB() + " " + ms.getTimeString() + " ns=" + ms.getNsamp() + " " + ms.getRate() + " latR");
              }
              if (dbg) {
                prta(ms.toString());
              }
              IndexBlock.writeMiniSeedCheckMidnight(ms, hydra, false, tag, this);

              // Send to UdpChannel server
              if (csend != null) {
                csend.send(ms.getJulian(),
                        (int) (ms.getTimeInMillis() % 86400000), // Milliseconds
                        ms.getSeedNameSB(), ms.getNsamp(), ms.getRate(), ms.getBlockSize());  // Key, nsamp, rate and nbytes
              }
            } else {
              if (dbg) {
                Util.prta(tag + " Heart beat");
              }
              heartBeat++;
            }
            if ((now.getTimeInMillis() - lastStatus.getTimeInMillis()) > 600000 && dbg) {
              prta(tag + " # Rcv=" + countMsgs + " hbt=" + heartBeat);
              countMsgs = 0;
              lastStatus = now;
            }
          } catch (IOException e) {
            if (e.getMessage().contains("Socket closed") || e.getMessage().contains("Stream closed")) {
              if (terminate) {
                prta(tag + " Doing termination via Socket close.");
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
              prta(tag + " NetservClient: receive through IO exception e=" + e.getMessage());
              e.printStackTrace(getPrintStream());
            }
            break;      // Drop out of read loop to connect loop
          } catch (IllegalSeednameException e) {
            if (ms != null) {
              if (ms.getSeedNameSB().substring(7, 10).equals("LOG")) {
                try {
                  prta("Handle odd log record ");
                  for (int i = 0; i < 48; i++) {
                    prt(i + " = " + ms.getBuf()[i] + " " + (char) ms.getBuf()[i]);
                  }

                  IndexBlock.writeMiniSeedCheckMidnight(ms, hydra, false, tag, this);
                } catch (IllegalSeednameException expected) {
                }
                continue;
              }
            }
            nillegal++;
            if (ms != null) {
              prta(tag + " IllegalSeedName =" + nillegal + " "
                      + ms.getSeedNameSB() + " " + e.getMessage());
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
          /*catch (EdgeFileCannotAllocateException e) {
            prt("EdgeFileCannotAllocate "+e.getMessage());
          }
          catch (EdgeFileReadOnlyException e) {
            prt(" EdgeFileReadOnly "+e.getMessage());
          }*/

        }     // while(true) Get data
        try {
          if (nchar <= 0 & nchar != -99 && !monitor.didInterrupt()) {
            prta(tag + " exited due to EOF char=" + nchar + " terminate=" + terminate);
          } else if (terminate) {
            prt(tag + " terminate found.");
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
        prta(tag + " RuntimeException in " + this.getClass().getName() + " e=" + e.getMessage());
        if (getPrintStream() != null) {
          e.printStackTrace(getPrintStream());
        } else {
          e.printStackTrace();
        }
        if (e.getMessage() != null) {
          if (e.getMessage().contains("OutOfMemory")) {
            SendEvent.doOutOfMemory("", this);
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
   * Return the monitor string for Icinga.
   *
   * @return A String representing the monitor key value pairs for this
   * EdgeThread.
   */
  long lastMonInBytes;

  @Override
  public StringBuilder getMonitorString() {
    if (monitorsb.length() > 0) {
      monitorsb.delete(0, monitorsb.length());
    }
    long nb = inbytes - lastMonInBytes;
    lastMonInBytes = inbytes;
    return monitorsb.append(tag.replaceAll("NSrv:", "")).append("BytesIn").append(nb).append("\n");
  }

  @Override
  public StringBuilder getStatusString() {
    if (statussb.length() > 0) {
      statussb.delete(0, statussb.length());
    }
    return statussb.append("#rcv=").append(countMsgs).append(" hb=").append(heartBeat);
  }

  @Override
  public StringBuilder getConsoleOutput() {
    return consolesb;
  } //  we use prt directly

  /**
   * Monitor the NetservCLient and stop it if it does not receive heartBeats or
   * data.
   */
  final class NetservMonitor extends Thread {

    boolean terminate;
    int lastHeartbeat;
    long lastInbytes;
    int msWait;
    boolean interruptDone;
    NetservClient thr;

    public boolean didInterrupt() {
      return interruptDone;
    }

    public void terminate() {
      terminate = true;
      interrupt();
    }

    public NetservMonitor(NetservClient t) {
      thr = t;
      msWait = 360000;
      gov.usgs.anss.util.Util.prta("new ThreadMonitor " + getName() + " " + getClass().getSimpleName() + " t=" + t);
      start();
    }

    @Override
    public void run() {
      //try{sleep(msWait);} catch(InterruptedException expected) {}
      while (!terminate) {
        long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < msWait) {
          try {
            sleep(msWait - (System.currentTimeMillis() - start));
          } catch (InterruptedException expected) {
          }
          if (terminate) {
            break;
          }
        }
        if (terminate) {
          break;
        }
        //prta(tag+" NSM: HB="+heartBeat+" lHB="+lastHeartbeat+" in ="+inbytes+" lin="+lastInbytes);
        if (lastHeartbeat == heartBeat && inbytes - lastInbytes < 2000) {
          thr.interrupt();      // Interrupt in case its in a wait
          interruptDone = true;     // So interrupter can know it was us!
          if (d != null) {
            try {
              if (!d.isClosed()) {
                d.close();  // Force IO abort by closing the socket
              }
            } catch (IOException e) {
              prta(tag + " NSM: close socket IOException=" + e.getMessage());
            }
          }
          prta(tag + " NSM: monitor has gone off HB=" + heartBeat + " lHB=" + lastHeartbeat + " in =" + inbytes + " lin=" + lastInbytes);
          try {
            sleep(msWait);
          } catch (InterruptedException expected) {
          }
          if (terminate) {
            break;
          }
          interruptDone = false;
        }
        lastInbytes = inbytes;
        lastHeartbeat = heartBeat;
      }
      prta(tag + " NSM: has been terminated");
    }

  }

  /**
   * This is the test code for NetservClient.
   *
   * @param args The command line arguments.
   */
  public static void main(String[] args) {
    IndexFile.init();
    EdgeProperties.init();
    NetservClient[] Netserv = new NetservClient[10];
    IndexFile.setDebug(true);
    int i = 0;
    Netserv[i++] = new NetservClient("tuc.iu.Netserv.org", 4000, 512);
    Netserv[i++] = new NetservClient("poha.iu.Netserv.org", 4000, 512);
    Netserv[0].toString();
  }

}
