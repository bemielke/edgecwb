/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.edgemom;
import gov.usgs.anss.edge.IndexFile;
import gov.usgs.anss.edge.IndexBlock;
import gov.usgs.anss.edge.EdgeProperties;
import gov.usgs.anss.edge.ChannelSender;
import gov.usgs.alarm.SendEvent;
import gov.usgs.anss.edge.IllegalSeednameException;
import gov.usgs.anss.edgethread.EdgeThread;
import gov.usgs.anss.edgethread.ManagerInterface;
import gov.usgs.anss.seed.MiniSeed;
import gov.usgs.anss.util.Util;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.GregorianCalendar;

/**
 * This client connects to a LISS server, receives the data from that port and
 * puts it into and edge database. It has the ability to expand if mixed sized
 * packets are found. It will break 4k packets into 512 packets by default. It
 * includes two different ways to create an instance of a LISSClient. An inner
 * monitor class is included to monitor the LISSClient
 *
 * <PRE>
 *Switch			arg        Description
 *-h					host    The host ip address or DNS translatable name of the LISS server
 *-p					pppp    The port to connect to on the LISS server.
 *-dbg					      Debug output is desired
 *-noudpchan		      Do not send results to the UdpChannel server for display in things like channel display
 *-latency			      Record latency of data in log file
 * -bind							ip.adr  Bind to this local address
 *-nohydra						Do not send the resulting data to Hydra (usually used on test systems to avoid Hydra duplicates)
 *-allow4k						Allow 4k records to go straight to database, default is to break 4k packets up into 512 packets
 *-l					lll			The initial  length of the data expected in bytes, this object will allow bigger buffers if B1000 indicate its needed.
 * -sleepmax	nnnn		The maximum number of seconds to sleep
 * </PRE>
 *
 * @author davidketchum
 */
public class LISSClient extends EdgeThread implements ManagerInterface {

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
  private LISSMonitor monitor;
  private boolean recordLatency;
  private PrintStream loglat;
  private String latFilename;
  private long minLatency, maxLatency, avgLatency;
  private int nlatency;
  private long inbytes;
  private long outbytes;
  private boolean dbg;
  private boolean hydra;
  private boolean allow4k;
  private boolean check;
  private String argsin;
  private int pt;
  private String h;
  private boolean nocsend;
  private int packetLength;
  private int sleepMax;

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
   * creates an new instance of LISSCLient - which will try to stay connected to
   * the host/port source of data. This one gets its arguments from a command
   * line
   *
   * @param argline EdgeThread args
   * @param tg The EdgeThread tag
   */
  public LISSClient(String argline, String tg) {
    super(argline, tg);
    argsin = argline;
//initThread(argline,tg);
    restart(argline);
    create(h, pt, packetLength);

  }

  public final void restart(String argline) {
    String[] args = argline.split("\\s");
    dbg = false;
    packetLength = 512;
    h = null;
    pt = 0;
    nocsend = false;
    allow4k = false;
    latFilename = tag;
    hydra = true;
    sleepMax = 14400000;

    for (int i = 0; i < args.length; i++) {
      //prt(i+" arg="+args[i]);
      if (args[i].equals("-h")) {
        h = args[i + 1];
        i++;
      } else if (args[i].equals("-bind")) {
        bind = args[i + 1];
        i++;
      } else if (args[i].equals("-p")) {
        pt = Integer.parseInt(args[i + 1]);
        i++;
      } else if (args[i].equals("-l")) {
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
      } else if (args[i].equals("-sleepmax")) {
        sleepMax = Integer.parseInt(args[i + 1]) * 1000;
        i++;
      } else if (args[i].substring(0, 1).equals(">")) {
        break;
      } else {
        prt("LISS client unknown switch=" + args[i] + " ln=" + argline);
      }
    }
    prt("LISSClient: new line parsed to host=" + h + " port=" + pt + " len=" + packetLength + " dbg=" + dbg + " bind=" + bind);

    if (!nocsend && csend == null) {
      csend = new ChannelSender("  ", "LISSClient", "LC-" + tag);
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

  /**
   * Creates a new instance of LISSClient - tries to stay connected to the given
   * IP address and port and forwards any received data via
   * IndexBlock.miniSeedWrite()
   *
   * @param h The host IP or DNS convertible address of the liss server
   * @param pt The port on the LISS server to contact
   * @param len The initial size of the input buffer (can grow if B1000
   * indicates it should)
   */
  public LISSClient(String h, int pt, int len) {
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
    tag += "LC:";  //with String and type
    monitor = new LISSMonitor(this);
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
    int iday = 0;       // mark we are running
    long isleep = 30000;
    while (true) {
      try {
        if (terminate) {
          break;
        }
        /* keep trying until a connection is made */
        int loop = 0;
        while (true) {
          if (terminate) {
            break;
          }
          try { // Make sure anything we have open can be let go
            d = makeBoundSocket(d, host, port, bind);
            /*if(d != null) {
              try {
                if(!d.isClosed()) d.close();
                 if(in != null) in.close();
                if(outsock != null) outsock.close();
              }
              catch(IOException e) {}
            }
            prta(tag+" Open Port="+host+"/"+port+" msgLen="+msgLength+" isleep="+(isleep/1000));
            d = new Socket(host, port);*/
            in = d.getInputStream();        // Get input and output streams
            outsock = d.getOutputStream();

            // Build first 100 StatusInfo objects and fill with empty data
            lastStatus = new GregorianCalendar();
            break;
          } catch (UnknownHostException e) {
            prt(tag + " Host is unknown=" + host + "/" + port + " loop=" + loop);
            if (loop % 30 == 1) {
              SendEvent.edgeEvent("HostUnknown", "LISS host unknown=" + host, this);
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
              if (isleep > sleepMax) {
                isleep = sleepMax;
              }
              if (isleep >= 3600000) {
                SendEvent.edgeSMEEvent("LISSRefuse", "For " + host + "/" + port, this);
              }
              try {
                sleep(Util.isNEICHost(host) ? 30000 : isleep);
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
              if (isleep > sleepMax) {
                isleep = sleepMax;
              }
              try {
                sleep(Util.isNEICHost(host) ? 30000 : isleep);
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

          //try {sleep(10000L);} catch (InterruptedException e) {}
        }   // While True on opening the socket
        if (terminate) {
          break;
        }
        prta(tag + " local=" + d.getLocalPort() + " Socket is opened.  Start reads.");
        tag = tag.substring(0, tag.indexOf(":") + 1) + d.getLocalPort() + " ";
        prta(tag + " is now changed!");
        isleep = 30000;
        // Read data from the socket and update/create the list of records 
        int len;
        int l;
        int nchar = 0;
        int nsamp = 0;
        int nsleft = 0;
        int blockSize = 0;
        //int [] data = null;      // Need to decompress the data
        GregorianCalendar now = new GregorianCalendar();
        while (true) {
          if (terminate) {
            break;
          }
          try {
            len = 512;                // desired block length my be larger
            l = 0;                    // offset into buf for next read
            nchar = 0;                // Number of characters returned
            int loops = 0;
            while (len > 0) {            // 
              while (in.available() <= 0) {
                try {
                  sleep(10);
                } catch (InterruptedException e) {
                }
              }
              nchar = in.read(buf, l, len);// get nchar
              if (nchar <= 0) {
                prta("512 read nchar=" + nchar + " len=" + len + " in.avail=" + in.available());
                break;
              }     // EOF - close up
              l += nchar;               // update the offset
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
              len -= nchar;             // reduce the number left to read
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
              if (ms.getBlockSize() > 512) {     // bigger mini-seed read it in
                if (buf.length < ms.getBlockSize()) {
                  byte[] buf2 = new byte[buf.length];
                  System.arraycopy(buf, 0, buf2, 0, buf.length);
                  buf = new byte[ms.getBlockSize()];
                  System.arraycopy(buf2, 0, buf, 0, buf2.length);
                }
                len = ms.getBlockSize() - l;
                while (len > 0) {            // 
                  while (in.available() <= 0) {
                    try {
                      sleep(10);
                    } catch (InterruptedException e) {
                    }
                  }
                  nchar = in.read(buf, l, len);// get nchar
                  if (nchar <= 0) {
                    break;     // EOF - close up
                  }
                  l += nchar;               // update the offset
                  len -= nchar;             // reduce the number left to read
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
              // The following measure latency of sends and write them in latency files.
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
              nillegal = 0;                 // must be a legal seed name
              if (recordLatency) {
                long latency = System.currentTimeMillis() - ms.getTimeInMillis() - ((long) (ms.getNsamp() / ms.getRate() * 1000.));
                prta(latency + " " + ms.getSeedNameSB() + " " + ms.getTimeString() + " ns=" + ms.getNsamp() + " " + ms.getRate() + " latR");
              }
              if (dbg) {
                prta(ms.toString());
              }
              IndexBlock.writeMiniSeedCheckMidnight(ms, false, allow4k, tag, this); // Do not use ChanInfra for Hydra
              if (hydra) {
                Hydra.sendNoChannelInfrastructure(ms);        // Do Hydra via No Infrastructure
              }
              // Send to UdpChannel server
              if (csend != null) {
                csend.send(ms.getJulian(),
                        (int) (ms.getTimeInMillis() % 86400000), // Milliseconds
                        ms.getSeedNameSB(), ms.getNsamp(), ms.getRate(), ms.getBlockSize());  // key,nsamp,rate and nbytes
              }
            } else {
              if (dbg) {
                Util.prta("Heart beat");
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
              prta("LISSClient: receive through IO exception e=" + e.getMessage());
              e.printStackTrace(getPrintStream());
            }
            break;      // Drop out of read loop to connect looop
          } catch (IllegalSeednameException e) {
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
              terminate = true;    // if 3 in a row, then close connection
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
   * return the monitor string for Icinga
   *
   * @return A String representing the monitor key value pairs for this
   * EdgeThread
   */
  long lastMonInBytes;

  @Override
  public StringBuilder getMonitorString() {
    if (monitorsb.length() > 0) {
      monitorsb.delete(0, monitorsb.length());
    }
    long nb = inbytes - lastMonInBytes;
    lastMonInBytes = inbytes;
    return monitorsb.append(tag.replaceAll("LC:", "")).append("BytesIn").append(nb).append("\n");
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
   * monitor the LISSCLient and stop it if it does not receive heartBeats or
   * data!
   */
  public final class LISSMonitor extends Thread {

    boolean terminate;
    int lastHeartbeat;
    long lastInbytes;
    int msWait;
    boolean interruptDone;
    LISSClient thr;

    public boolean didInterrupt() {
      return interruptDone;
    }

    public void terminate() {
      terminate = true;
      interrupt();
    }

    public LISSMonitor(LISSClient t) {
      thr = t;
      msWait = 360000;
      Util.prta("new ThreadMonitor " + getName() + " is " + getClass().getSimpleName() + " LISS=" + t);
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
   * This is the test code for LISSClient
   *
   * @param args the command line arguments
   */
  public static void main(String[] args) {
    IndexFile.init();
    EdgeProperties.init();
    LISSClient[] liss = new LISSClient[10];
    IndexFile.setDebug(true);
    int i = 0;
    liss[i++] = new LISSClient("tuc.iu.liss.org", 4000, 512);
    liss[i++] = new LISSClient("poha.iu.liss.org", 4000, 512);
    liss[i++] = new LISSClient("saml.iu.liss.org", 4000, 512);
    liss[i++] = new LISSClient("trqa.iu.liss.org", 4000, 512);
    liss[i++] = new LISSClient("bill.iu.liss.org", 4000, 512);
    liss[i++] = new LISSClient("majo.iu.liss.org", 4000, 512);
    liss[i++] = new LISSClient("tsum.iu.liss.org", 4000, 512);
    liss[i++] = new LISSClient("nwao.iu.liss.org", 4000, 512);
    liss[i++] = new LISSClient("pmsa.iu.liss.org", 4000, 512);
    liss[i++] = new LISSClient("otav.iu.liss.org", 4000, 512);
    liss[0].toString();
  }

}
