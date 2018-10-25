/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.edgemom;

import gov.usgs.alarm.SendEvent;
import gov.usgs.anss.cd11.AckNackThread;
import gov.usgs.anss.cd11.CD11Frame;
import gov.usgs.anss.cd11.CanadaException;
import gov.usgs.anss.cd11.ChannelSubframe;
import gov.usgs.anss.cd11.GapList;
import gov.usgs.anss.cd11send.CD11SenderReader;
import gov.usgs.anss.cd11send.ZeroFilledSpan;
import gov.usgs.anss.edge.EdgeProperties;
import gov.usgs.anss.edge.IllegalSeednameException;
import gov.usgs.anss.edge.IndexFile;
import gov.usgs.anss.edge.MasterBlock;
import gov.usgs.anss.edge.RawDisk;
import gov.usgs.anss.edgethread.EdgeThread;
import gov.usgs.anss.net.TimeoutSocketThread;
import gov.usgs.anss.cwbqueryclient.EdgeQueryClient;
import gov.usgs.anss.seed.MiniSeed;
import gov.usgs.anss.util.Util;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.GregorianCalendar;
import java.util.Iterator;
import java.util.TreeMap;
import java.io.ByteArrayInputStream;

/**
 * This client sends data from one station to a CD1.1 receiver. It gets its data
 * from a ring file that has blocks of CD1.1 formatted records arranged so that
 * the sequence number determines their position in the file. This program
 * watches for updates in the ring position and sends all records from the last
 * transmission to the new index as the current data stream.
 *
 * This client also keeps track of gaps that are generated 1) In the current
 * stream when sequence numbers are skipped because the CD1.1 data does not have
 * the correct sequence, or 2) Based on the gap list in the ACK packet from the
 * CD1.1 receiver. This list of unsent sequences are used by the CD11GapBuiler
 * to generate the missing sequences from queries made to one of the CWBs. These
 * out-of-order packets are then sent via the out-of-band queue when current
 * data is not yet available.
 *
 * This client implements the ConnectionRequest/Response and opens the data
 * socket to the address and port provided by the CD1.1 server (data consumer -
 * in the USGS case AFTAC).
 *
 * Object of this type are generally launched by the CD11StationProcessor using
 * the same command line used to launch the processor. Those commands are
 * generally in a configuration file given to the CD11OIReader which specify how
 * to handle the actual forwarding of the data.
 *
 *
 * <PRE>
 *It is EdgeThread with the following arguments :
 * Tag					arg          Description
 * -s						station      The station name normally, but the name of the data flowing on this connection.
 * -p						ppppp        Run the server on this port.
 * -ip					ip.adr       The computer IP to contact to initiate the CD1.1 exchange.
 * -b						ip.adr       The address of the local computer to bind to this end of the socket.
 * -recsize			nnnnn				 The size in bytes of records in the CD1.1 output ring file (the input to this thread).
 * -maxrec			nnnnn				 The number of records in the CD1.1 output ring file (the modulo to seq for rec position).
 * -ringpath		/path				 The path on which ring file are to be found, the filenames are dictated by the station.cd11out.
 * -creator			creator			 The creator name to use in CD1.1 for the source of this data (normally usgs).
 * -destination dest				 The destination name to use in the CD1.1 for the destination of this data (0 or ndc usually).
 * -c						creator      The creator name, defaults to station if not set.
 * -d						destination  The destination of the data 0 is default(IDC, etc).
 *
 * -lmap FR=TO[/F2=T2..]  Change the location code from FR->TO and F2->T2 etc.  Spaces should be input as "-"
 *                    If this switch is not used, all location codes will be removed.  To preserve location codes
 *                    use -lmap NN=NN.
 * -cmap FRM=TO1[/fR2=TO2] Change channel names from FRM->TO11 etc.  So BHZ=HHZ/BH1=HH1.
 *
 *
 * Parameters handled by CD11StationProcessor only:
 * -secdepth		secs				 The number of seconds of depth in the processor in the clear buffers.
 *
 * >[>]  filename     You can redirect output from this thread to filename in the current logging directory
 *
 * ConfigFile (/path/STATION.config) :
 * Gap List size=5 series=0
 * LowSeq =27360959         # Lowest sequence still open for having gaps.
 * HighSeq=27361100         # Highest sequence acknowledged.
 * start1-end1              # gap1 range.
 * start2- end2             # gap2 range.
 * .
 * .
 * </PRE>
 *
 * @author davidketchum
 */
public final class CD11SenderClient extends EdgeThread {

  private Socket ss;                // The socket we are "listening" for new connections
  //GregorianCalendar lastStatus;
  private int port;                       // Theport we connecting to
  private String ip;                      // The IP address we will maintain a connection to
  private String station;                 // Set the station name to get (or array)
  private InetAddress bindAddress;        // The interface we are to listen on.
  private ShutdownCD11SenderClient shutdown;
  private AckNackThread ack;
  private final StringBuilder creator = new StringBuilder(8);
  private final StringBuilder destination = new StringBuilder(8);
  private CD11SenderReader reader;// Thread that reads from this data socket and decodes and handles ack/alerts
  private int series;             // The series being sent
  private long seq;               // The current sequence number
  private int ngap;
  private GapList outGaps;
  private boolean connected;      // True if a connection is up, false if a connection is in progress
  //Status status;
  private String cmdline;
  private String gapsFile;
  private boolean dbg;
  private boolean dbgdet;
  private byte[] buf;
  private ByteBuffer bb;

  // CD1.1 ring file variables
  private String dataFile;                // The data file name
  private RawDisk cd11ring;
  private byte[] cd11hdrbuf = new byte[20];
  private ByteBuffer cd11hdr;
  private int recsize;            // size of the records in blocks, note this comes from the Ring file header    
  private int maxrec;             // number of records in the ring (the modulo)
  private long lastSeqOut;        // The last sequence number written to the file
  private int state;

  // Converting location/channel codes (mapping new location codes)
  private String locFrom[];
  private String locTo[];
  private String chnFrom[];
  private String chnTo[];
  private int noobPackets;
  private int npackets;
  private CD11Frame frame;
  private CD11Frame inframe;
  private final StringBuilder tmpsb = new StringBuilder(100);

  public int inState() {
    return state;
  }

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
      sb.append("CD11SC: ").append(station).append(" ").append(ip).append("/").append(port).append(" ").
              append(connected ? "Conn" : "NOTConn").append(" #oob=").append(noobPackets).
              append(" st=").append(state).append(" gpst=").append(outGaps.inState()).
              append(" alive=").append(isAlive()).append(" #pkt=").append(npackets).
              append(" Rdr: #rcv=").append(reader == null ? "null" : reader.getNframes());
    }
    return sb;
  }

  @Override
  public void terminate() {
    setTerminate(true);
  }

  public boolean isConnected() {
    return connected;
  }

  public int getNPackets() {
    return npackets;
  }

  public int getReaderNFrames() {
    return (reader != null ? reader.getNframes() : 0);
  }

  public GapList getGapList() {
    return outGaps;
  }

  public String getGapFilename() {
    return gapsFile;
  }

  public void setTerminate(boolean t) {
    terminate = t;
    prta(Util.clear(tmpsb).append(tag).append(" terminate called=").append(t));
    if (terminate && ss != null) {
      try {
        ss.close();
      } catch (IOException expected) {
      }
    }
  }

  public void restart() {
    if (ss != null) {
      try {
        if (!ss.isClosed()) {
          ss.close();
        }
      } catch (IOException expected) {
      }
    }
    prta(Util.clear(tmpsb).append(tag).append(" restart executed closed=").
            append(ss == null ? "null" : ss.isClosed()).append(" st=").append(state).append(" alive=").append(isAlive()));
    connected = false;
  }
  byte[] queueOOB = null;

  public void queueOOB(byte[] b) {
    queueOOB = b;
    while (queueOOB != null) {
      try {
        sleep(300);
      } catch (InterruptedException expected) {
      }
    }
  }

  /**
   * Add a sequence number to the list of queued out-of-band output.
   *
   * @param t
   */
  /*public void addQueue(long t) {
    for(int i=0; i<queue.size(); i++) {
      if(t >= queue.get(i).longValue()) {
        if(t > queue.get(i).longValue()) queue.add(i, new Long(t)); // Only add if not equal to
        return;
      }
    }
    queue.add(new Long(t));       // Add it at the end
 }
  public int getQueueSize() {return queue.size();}*/
  public void setDebug(boolean t) {
    dbg = t;
  }

  public void setDebugDetail(boolean t) {
    dbgdet = t;
  }

  /**
   * Creates a new instance of ServerThread.
   *
   * @param argline The argument line described in the class section.
   * @param tg The Edgethread tag. This will appear on all line output from this
   * modules.
   * @param sps The TreeMap of spans being created for this channel by
   * CD11StationProcess (basically a channel list!).
   * @throws UnknownHostException If the bind name does not translate.
   */

  public CD11SenderClient(String argline, String tg, TreeMap<String, ZeroFilledSpan> sps) throws UnknownHostException {
    super(argline, tg);
    prta(Util.clear(tmpsb).append("New CD11SenderClient : argline=").append(argline).append(" tag=").append(tag));
    cmdline = argline;
    spans = sps;
    String[] args = argline.split("\\s");
    dbg = false;
    dbgdet = false;
    port = 29001;
    dataFile = tg + ".cd11out";
    ip = "localhost";
    station = tg;
    bindAddress = null;
    String ringPath = "";
    Util.clear(creator).append(station.substring(2).trim());
    Util.clear(destination).append("0");
    recsize = 4;
    for (int i = 0; i < args.length; i++) {
      //prt(i+" arg="+args[i]);
      if (args[i].length() == 0) {
        continue;
      }
      if (args[i].charAt(0) == '>') {
        break;
      }
      if (args[i].equals("-p")) {
        port = Integer.parseInt(args[i + 1]);
        i++;
      } else if (args[i].substring(0, 1).equals(">")) {
        break;
      } else if (args[i].equals("-ip")) {
        ip = args[i + 1];
        i++;
      } else if (args[i].equals("-b")) {
        bindAddress = InetAddress.getByName(args[i + 1]);
        i++;
      } else if (args[i].equals("-dbg")) {
        dbg = true;
      } else if (args[i].equals("-dbgdet")) {
        dbgdet = true;
        dbg = true;
      } else if (args[i].equals("-recsize")) {
        recsize = (Integer.parseInt(args[i + 1]) + 511) / 512;
        i++;
      } else if (args[i].equals("-maxrec")) {
        maxrec = Integer.parseInt(args[i + 1]);
        i++;
      } else if (args[i].equals("-ringpath")) {
        ringPath = args[i + 1];
        if (!ringPath.endsWith("/")) {
          ringPath += "/";
        }
        i++;
      } else if (args[i].equals("-creator")) {
        Util.clear(creator).append(args[i + 1]);
        i++;
      } else if (args[i].equals("-destination")) {
        Util.clear(destination).append(args[i + 1]);
        i++;
      } // Processed by the CD11StationProcessor
      else if (args[i].equals("-secdepth")) {
        i++;
      } else if (args[i].equals("-lmap")) {
        String[] parts = args[i + 1].split("/");
        if (parts.length > 0) {
          locFrom = new String[parts.length];
          locTo = new String[parts.length];
          for (int j = 0; j < parts.length; j++) {
            String[] maps = parts[j].split("=");
            if (maps.length != 2) {
              prt("CD11StationProcessor -lmap is not in FR-TO/FR-TO/FR-TO form");
            }
            locFrom[j] = maps[0].replaceAll("-", " ");
            locTo[j] = maps[1].replaceAll("-", " ");
            prt(Util.clear(tmpsb).append(tag).append(station).append(" Location convert ").
                    append(locFrom[j]).append("->").append(locTo[j]).append("|"));
          }
        }
      } else if (args[i].equals("-cmap")) {
        String[] parts = args[i + 1].split("/");
        if (parts.length > 0) {
          chnFrom = new String[parts.length];
          chnTo = new String[parts.length];
          for (int j = 0; j < parts.length; j++) {
            String[] maps = parts[j].split("=");
            if (maps.length != 2) {
              prt("CD11StationProcessor -cmap is not in CCCLL-TOCLL:CCCL2=toCLL form");
            }
            chnFrom[j] = maps[0].replaceAll("-", " ");
            chnTo[j] = maps[1].replaceAll("-", " ");
            prt(Util.clear(tmpsb).append(tag).append(" ").append(station).
                    append(" Channel convert ").append(chnFrom[j]).append("->").
                    append(chnTo[j]).append("|"));
          }
        }
      } else {
        prt(Util.clear(tmpsb).append(tag).append("CD11SenderClient unknown switch=").
                append(args[i]).append(" ln=").append(argline));
      }

    }
    tag += "[" + station.trim() + "]CDSC:";
    prt(Util.clear(tmpsb).append(tag).append("CD11Sender argline=").append(argline));
    if (bindAddress == null) {
      throw new UnknownHostException("No host has been bound");
    }
    prt(Util.clear(tmpsb).append(tag).append("CD11SenderClient starting on port=").append(port).append(" recsize=").append(recsize));

    cd11hdr = ByteBuffer.wrap(cd11hdrbuf);
    while (cd11ring == null) {
      try {
        cd11ring = new RawDisk(ringPath + station + ".cd11out", "r");
        // Is the file new?  
        readCD11Header();
        if (seq <= 1) {
          seq = lastSeqOut;      // Do not know where to pick up
        }
        prta(Util.clear(tmpsb).append(tag).append(" open cd11out file seq=").append(seq).
                append(" lastSeqOut=").append(lastSeqOut).append(" recsize=").append(recsize).append(" #bytesbuf=").append(recsize * 1024));
        buf = new byte[recsize * 1024];
        bb = ByteBuffer.wrap(buf);
        break;
      } catch (IOException e) {
        Util.IOErrorPrint(e, "Trying to open/read the " + station + ".cd11out file", getPrintStream());
        Util.prta(Util.clear(tmpsb).append(tag).append(" waiting to open the .cd11out file e=").append(e));
        try {
          sleep(30000);
        } catch (InterruptedException expected) {
        }
      }
    }

    // The "gapsFile" is real; a list of gaps we have seen in our output
    gapsFile = ringPath + station + ".gaps";
    try {
      outGaps = new GapList(0, seq, gapsFile, this);  // Default is the current sequence
      outGaps.setDebug(dbg);
      outGaps.trimList(seq - maxrec);
    } catch (FileNotFoundException e) {
      Util.prta(Util.clear(tmpsb).append(tag).append("FileNotFound config file=").
              append(gapsFile).append(" assume new"));
    }

    frame = new CD11Frame(10240, 100, creator, destination, seq, series, 0, this);
    inframe = new CD11Frame(10240, 100, this);     // suitable for reading data
    shutdown = new ShutdownCD11SenderClient();
    prta("new ThreadProcess " + getName() + " " + getClass().getSimpleName() + "@" + Util.toHex(hashCode()) + " tag=" + tag
            + " ogaps=" + Util.toHex(outGaps.hashCode()) + " frm@" + Util.toHex(frame.hashCode()) + " infr@" + Util.toHex(inframe.hashCode()) + " l=" + argline);
    Runtime.getRuntime().addShutdownHook(shutdown);
    start();
  }

  /**
   * This reads in the header.
   *
   * @return true if the file characteristics have changed from our
   * configuration.
   */
  private boolean readCD11Header() throws IOException {
    cd11ring.readBlock(cd11hdrbuf, 0, 20);
    cd11hdr.position(0);
    lastSeqOut = cd11hdr.getLong();
    maxrec = cd11hdr.getInt();
    recsize = cd11hdr.getInt();
    return false;

  }

  /**
   * This Thread does accepts() on the port and creates a new ClientSocket inner
   * class object to track its progress (basically hold information about the
   * client and detects writes that are hung via a timeout Thread).
   */
  @Override
  public void run() {
    int len;
    running = true;
    connected = false;
    int trailerOffset;
    long frseq;
    boolean exit;
    int authLen;
    long lastGapCheck = System.currentTimeMillis();
    long beginGap = -1;
    InetAddress ipServer;
    int portServer;
    boolean oobSent;
    GregorianCalendar gg = new GregorianCalendar();
    StringBuilder runsb = new StringBuilder(50);
    TimeoutSocketThread timeout = new TimeoutSocketThread(tag, ss, 30000);
    while (!terminate) {
      // This loop does the connection, and the ConnectionRequest and ConnectionResponse exchange
      while (!connected) {
        try {
          if (terminate) {
            break;
          }
          if (ack != null) {
            ack.close();
            ack = null;
          }
          state = 1;
          if (reader != null) {
            reader.newSocket(null);
          }
          prta(Util.clear(runsb).append(tag).append(" Open connection Request to ").append(ip).append("/").append(port));
          ss = new Socket(ip, port);            // Create the socket to the Dispather
          timeout.setSocket(ss);
          timeout.enable(true);
          frame.loadConnectionRequest(station.substring(2).trim(), "TCP", ss.getLocalAddress().toString().substring(1), ss.getLocalPort());
          prta(Util.clear(runsb).append(tag).append(" Open Connection port=").
                  append(ss.getLocalAddress()).append("/").append(ss.getLocalPort()).append(" frame=").append(frame.toStringBuilder(null)));
          len = frame.getOutputBytes(bb);

          ss.getOutputStream().write(buf, 0, len);  // send Connection request packet
          //lastStatus = new GregorianCalendar();   // Set initial time
          //CD11Frame.setDebug(true);
          int frmlen = inframe.readFrame(ss.getInputStream(), this);// Read what should be a Connection response
          timeout.enable(false);
          if (frmlen == 0) {
            prta(Util.clear(runsb).append(tag).
                    append("Got ** EOF reading connection response frame.  Connection not complete try again in 120 s.  type=").append(inframe.getType()));
            
            SendEvent.edgeSMEEvent("CD11FwdEOFCn", "EOF reading connection response frame - AFTAC restart needed?", this);
            connected = false;
            if (ss != null) {
              try {
                ss.close();
              } catch (IOException expected) {
              }
            }
            try {
              sleep(120000);
            } catch (InterruptedException expected) {
            }
            continue;
          }
          prta(Util.clear(runsb).append(tag).append(" read frame=").append(inframe.toString()));
          if (inframe.getType() == CD11Frame.TYPE_CONNECTION_RESPONSE) {
            ByteBuffer cr = ByteBuffer.wrap(inframe.getBody());
            cr.position(0);
            short major = cr.getShort();
            short minor = cr.getShort();
            byte[] responderb = new byte[8];
            cr.get(responderb);
            for (int i = 0; i < 8; i++) {
              if (responderb[i] == 0) {
                responderb[i] = 32;
              }
            }
            byte[] respondType = new byte[4];
            cr.get(respondType);
            byte[] serviceType = new byte[4];
            for (int i = 0; i < 4; i++) {
              if (respondType[i] == 0) {
                respondType[i] = 32;
              }
              if (serviceType[i] == 0) {
                serviceType[i] = 32;
              }
            }
            cr.get(serviceType);
            byte[] ipb = new byte[4];
            cr.get(ipb);
            ipServer = InetAddress.getByAddress(ipb);
            portServer = ((int) cr.getShort()) & 0xffff;
            prta(Util.clear(runsb).append(tag).append(" Connection_response : ip=").
                    append(ipServer).append("/").append(portServer).append(" ").append(major).append(".").append(minor).append(" responder=").append(new String(responderb).trim()).append(":").append(new String(respondType).trim()).append(":").append(new String(serviceType).trim()));
            ss.close();
            ss = new Socket(ipServer, portServer);    // connect the data port
            timeout.setSocket(ss);
            timeout.enable(true);
            prta(Util.clear(runsb).append(tag).append(" Data Connection to ").append(ipServer).
                    append("/").append(portServer).append(" made."));
          } else {
            state = 2;
            prta(Util.clear(runsb).append(tag).append("Got unexpected response to CONNECTION_REQUEST +").
                    append(frame.getType()));
            timeout.enable(false);
            SendEvent.edgeSMEEvent("CD11UnknResp", "Response frame unexpected - AFTAC restart needed?", this);
            ss.close();
            connected = false;
            try {
              sleep(10000);
            } catch (InterruptedException expected) {
            }
            continue;
          }

          // Send an Option Request, though why it is mandatory is not known.
          state = 3;
          frame.loadOptionRequest(station.substring(2).trim());
          len = frame.getOutputBytes(bb);
          prta(Util.clear(runsb).append(tag).append(" Send Option Request ").append(station).
                  append(" len=").append(len).append(" frame=").append(frame.toStringBuilder(null)));
          ss.getOutputStream().write(buf, 0, len);  // send Option request packet
          frmlen = inframe.readFrame(ss.getInputStream(), this);// Read what should be a Option response
          timeout.enable(false);
          if (frmlen == 0) {     // Got an EOF - need to start over
            prta(tag + " Got EOF trying to read OPtion frame");
            SendEvent.edgeSMEEvent("CD11FwdEOFCn", "EOF reading option response - AFTAC restart needed?", this);
            connected = false;
            if (ss != null) {
              try {
                ss.close();
              } catch (IOException expected) {
              }
            }
            try {
              sleep(10000);
            } catch (InterruptedException expected) {
            }
            continue;
          }
          prta(Util.clear(runsb).append(tag).append(" frm read =").append(inframe.toString()));
          state = 4;
          if (inframe.getType() == CD11Frame.TYPE_OPTION_RESPONSE) {
            ByteBuffer cr = ByteBuffer.wrap(inframe.getBody());
            cr.position(0);
            int nopt = cr.getInt();     // Get number of options
            int optlen = cr.getInt();      // Get Option 1 length
            if (nopt != 1) {
              prta(Util.clear(runsb).append(tag).
                      append(" *********** Option response does not have one option! nopt=").append(nopt));
            }
            prta(Util.clear(runsb).append(tag).append("Got Option Response as expected nopt=").
                    append(nopt).append(" opt 1 len=").append(optlen));
            byte[] resp = new byte[optlen];
            cr.get(resp);
            for (int i = 0; i < optlen; i++) {
              prt(Util.clear(runsb).append(i).append(" = ").append(resp[i]));
            }
            connected = true;
          } else {
            prta(Util.clear(runsb).append(tag).append(" ************ Did not get expected option response type=").
                    append(frame.getType()));
            SendEvent.edgeSMEEvent("CD11BadOpt", "Option response not received - AFTAC restart needed?", this);
            byte[] buf2 = frame.getBody();
            for (int i = 0; i < frame.getBodyLength(); i++) {
              prta(i + " = " + buf2[i]);
            }
          }
        } catch (ConnectException e) {
          timeout.enable(false);
          if (e.toString().contains("Connection timed out")) {
            prta(tag + "Connection timed out.  Try again");
          } else if (e.toString().contains("refused")) {
            prta(Util.clear(runsb).append(tag).append("Connection to ").append(ip).append("/").
                    append(port).append(" was refused.  Wait an try again"));
            try {
              sleep(60000);
            } catch (InterruptedException e2) {
            }
          } else {
            prta(Util.clear(runsb).append(tag).append("Unknown connection excep e=").append(e));
          }
        } catch (IOException e) {
          timeout.enable(false);
          prta(Util.clear(runsb).append(tag).append("IOException making connection to server=").append(e));
          e.printStackTrace(getPrintStream());
          Util.IOErrorPrint(e, tag + "CD11SenderClient : TCP/IP error on request or data connection " + ip + "/" + port);
          try {
            sleep(120000);
          } catch (InterruptedException expected) {
          }
        }
      }   // connect loop
      if (ack != null) {
        ack.close();
      }
      try {
        ack = new AckNackThread(tag + "Ack", ss, creator, destination, 0, outGaps, this);
        ack.setDebug(true);
        ack.setEnableAcks(true);
      } catch (IOException e) {
        prta(Util.clear(runsb).append(tag).append("got IOError opening acknack thread e=").append(e));
        ack = null;
      }
      state = 5;
      if (reader != null) {
        reader.newSocket(ss);
      } else {
        reader = new CD11SenderReader(ss, station, this);
      }
      CD11Frame.setDebug(false);
      prta(Util.clear(runsb).append(tag).append(" ").append(port).append(" start data Transfer ").
              append(ss.getInetAddress()).append("-").append(ss.getLocalSocketAddress()).append("|").append(ss));
      try {
        GregorianCalendar nominal = new GregorianCalendar(2000, 0, 1, 0, 0, 0);
        nominal.setTimeInMillis(nominal.getTimeInMillis() / 1000L * 1000L);   // no millis please
        exit = false;
        // This is the infinite loop sending data from ring or filling a gap
        while (!terminate) {
          try {
            state = 6;
            if (exit || !connected) {
              break;     // something bad has happened
            }
            readCD11Header();             // get the lastSeqOut, maxrec, etc.
            if (lastSeqOut <= 0) {
              prt(Util.clear(runsb).append(tag).append(" Waiting for a valid sequence"));
              try {
                sleep(10000);
              } catch (InterruptedException expected) {
              }
              continue;
            } else {
              if (lastSeqOut + 1 != seq) {
                prta(Util.clear(runsb).append(tag).append("Seq frm ring lastSeqOut=").append(lastSeqOut).append(" seq=").append(seq));
              }
              if (seq < 1) {
                seq = lastSeqOut;
              }
            }
          } catch (IOException e) {
            Util.IOErrorPrint(e, tag + " IOError reading header", getPrintStream());
            terminate = true;
            break;
          }
          while (lastSeqOut >= seq) {
            try {
              state = 7;
              cd11ring.readBlock(buf, (int) ((seq % maxrec) * recsize + 1), recsize * 512);
            } catch (IOException e) {
              prta(Util.clear(runsb).append(tag).append(" got IOerror reading cd11 ring file e=").append(e));
              seq++;
              continue;
            }
            bb.position(24);
            frseq = bb.getLong();    // Get the sequence from the frame
            if (frseq == seq) {            // If it is the right one, send it
              /*if(beginGap != -1 ) {
                if(dbg) prta("New Gap in output from "+beginGap+"-"+seq);
                outGaps.addGap(beginGap, seq);
                beginGap = -1;
              }*/
              state = 8;
              if (seq % 100 == 0) {
                outGaps.trimList(seq - maxrec);
                outGaps.writeGaps(false);
              }   // occasionally write out the gap tracker
              bb.position(4);             // po
              trailerOffset = bb.getInt();
              if (trailerOffset > buf.length) {
                prta(Util.clear(runsb).append(tag).append(" ***** got bad auth offset=").
                        append(trailerOffset).append(" skip packet!"));
                SendEvent.debugEvent("CD11BadOffst", tag + " got bad offset in CD11 packet", this);
                seq++;
                continue;
              }
              bb.position(trailerOffset + 4);
              authLen = bb.getInt();      // The auth leng

              try {
                if (dbgdet) {    // This loads the binary output into a CD11 frame and dumps out its content
                  prt(dumpFrame(bb, buf, trailerOffset + authLen + 16).insert(0, "DETDBG:"));   // Details of the channels
                }
                gg.setTimeInMillis(seq * 10000L + CD11Frame.FIDUCIAL_START_MS);
                prta(Util.clear(runsb).append(tag).append(" send packet len=").append(trailerOffset + authLen + 16).
                        append(" seq=").append(seq).append(" ").append(Util.ascdatetime2(gg, null)/*+" OOB Queue Size="+queue.size()*/));
                outGaps.gotSeq(frseq);
                npackets++;
                state = 9;
                ss.getOutputStream().write(buf, 0, trailerOffset + authLen + 16);  // The length is offset + authLen + the other 16 trailer bytes
                state = 10;
                try {
                  sleep(200);
                } catch (InterruptedException expected) {
                }
              } catch (IOException e) {
                prta(Util.clear(runsb).append(tag).append("IOerror writing to socket! e=").append(e));
                exit = true;
                break;    // Force a new connection
              }
            } else {        // If this is not a valid sequence, start a continuation of a gap
              if (beginGap == -1) {
                beginGap = seq;    // It is a new gap
              }
            }
            seq++;
            state = 11;
          }
          if (exit) {
            prta(Util.clear(runsb).append(tag).append("break out of transfer "));
            break;
          }      // IOexception occurred, need to remake the socket
          // Seee if there is an OOB ready to send
          oobSent = false;
          if (queueOOB != null) {
            //if(dbg) prta(Util.clear(runsb).append(tag).append("send OOB found packet - process").append(noobPackets));
            state = 12;
            System.arraycopy(queueOOB, 0, buf, 0, Math.min(recsize * 512, queueOOB.length));
            queueOOB = null;            // Tell the sender we have it and its gone
            oobSent = true;
            bb.position(4);             // Position trailer offset
            trailerOffset = bb.getInt();
            if (trailerOffset > buf.length) {
              prta(Util.clear(runsb).append(tag).append(" ***** got bad auth OOB offset=").
                      append(trailerOffset).append(" skip packet!"));
              SendEvent.debugEvent("CD11BadOffst", tag + " got bad offset in OOB CD11 packet", this);
              continue;
            }
            bb.position(trailerOffset + 4);
            authLen = bb.getInt();      // The auth length

            try {
              noobPackets++;
              bb.position(24);
              frseq = bb.getLong();         // get the seq from the frame
              //DBG: if(dbgdet) { // This loads the binary output into a CD11 frame and dumps out its content
              prt(dumpFrame(bb, buf, trailerOffset + authLen + 16).insert(0, "OOB:"));     // details
              //}
              gg.setTimeInMillis(frseq * 10000L + CD11Frame.FIDUCIAL_START_MS);
              prta(Util.clear(runsb).append(tag).append(" send OOB len=").append(trailerOffset + authLen + 16).
                      append(" seq=").append(frseq).append(" ").append(Util.ascdatetime2(gg, null)));
              if (frseq >= outGaps.getLowestSeq()) {
                outGaps.gotSeq(frseq);  // Do not add earlier taps on OOB
              }
              state = 13;
              ss.getOutputStream().write(buf, 0, trailerOffset + authLen + 16);  // length is offset + authLen + the other 16 trailer bytes
              state = 14;
              //if(dbg) prta(Util.clear(runsb).append(tag).append(" send OOB is away"));
            } catch (IOException e) {
              prta(Util.clear(runsb).append(tag).append("IOerror writing send OOB to socket! e=").append(e));
              //Exit=true;
              break;    // Force a new connection
            }
          }

          state = 15;
          // All of the current flow is out, check to see if its time to review the gap list, and do so
          if (System.currentTimeMillis() - lastGapCheck > 600000) {
            state = 151;
            lastGapCheck = System.currentTimeMillis();
            int nfree = outGaps.trimList(seq - maxrec);       // trim the gap list to be in the current file
            state = 153;
            Util.clear(runsb);
            state = 157;
            outGaps.toStringBuilder(runsb);
            state = 154;
            runsb.insert(0, "nfree=" + nfree + " ");
            state = 155;
            String tmp = runsb.toString();
            state = 158;
            prta(tmp);
            state = 156;
          }         // if its time to check on gap blocks
          //if(dbg) prta(tag+" npkt="+npackets+" noob="+noobPackets+" oobSent="+oobSent);
          try {
            sleep(oobSent ? 50 : 1000);
          } catch (InterruptedException expected) {
          }
          state = 16;
        }   // while(!terminate) is in data transfer mode
        // Data transfer phase has exit. Close up the socket.
        connected = false;
        prta(Util.clear(runsb).append(tag).append("CD11CS: connection must be closed and reopened connected=").
                append(connected).append(" exit=").append(exit).append(" state=").append(state).append(" s=").append(ss));
        state = 17;
        if (ss != null) {
          try {
            if (!ss.isClosed()) {
              ss.close();
            }
          } catch (IOException expected) {
          }
        }
        ack.close();
        ack = null;
      } catch (RuntimeException e) {
        state = 161;
        prta(Util.clear(runsb).append(tag).append("CD11CS: RuntimeException in ").
                append(this.getClass().getName()).append(" e=").append(e.getMessage()));
        state = 162;
        if (getPrintStream() != null) {
          e.printStackTrace(getPrintStream());
        } else {
          e.printStackTrace();
        }
        state = 163;
        if (e.getMessage() != null) {
          if (e.getMessage().contains("OutOfMemory")) {
            SendEvent.edgeEvent("OutOfMemory", "Out of Memory in " + Util.getProcess() + "/"
                    + this.getClass().getName() + " on " + IndexFile.getNode(), this);
          }
        }
      }
    }       // while(!terminate) 
    state = 18;
    if (ack != null) {
      ack.terminate();
    }
    reader.terminate();
    prta(Util.clear(runsb).append(tag).append(" is exiting."));
    outGaps.writeGaps(true);            // Save the perceived gaps in output
    running = false;
    terminate = false;
  }
  private final StringBuilder sbdump = new StringBuilder(1000);

  /**
   * This code dumps a packet from a buffer given the raw bytes and a ByteBuffer
   * to same. It was derived from the process CD11LinkServer.processCD11Frame()
   * routine.
   *
   * @param bb ByteBuffer wrapping buffer.
   * @param buf The raw frame bytes with network header (note decode starts at
   * FRAME_HDR_LEN).
   * @param frameLen The total length of the frame including the frame
   * FRAME_HDR_LEN.
   * @return A new StringBuilder with the details.
   */
  private synchronized StringBuilder dumpFrame(ByteBuffer bb, byte[] buf, int frameLen) {
    try {
      Util.clear(sbdump);
      int dataNChan;
      int dataFrameMS;
      String nominalTime;
      int channelStringCount;
      ChannelSubframe csf = null;
      String seedname;
      StringBuilder seednameSB = new StringBuilder(12);
      byte[] timeScratch = new byte[20];
      GregorianCalendar nominal = new GregorianCalendar();
      GregorianCalendar g2 = new GregorianCalendar();
      int dataQuality;
      int ioFlags;
      int clockQuality;
      int activity;
      int usec;
      int[] samples = new int[10240];
      GregorianCalendar gtmp2;
      ByteArrayInputStream in2 = new ByteArrayInputStream(buf, 0, frameLen);
      try {
        inframe.readFrame(in2, this); // read the frame
        in2.close();
      } catch (IOException | RuntimeException e) {
        e.printStackTrace(getPrintStream());
      }
      sbdump.append(inframe.toStringBuilder(null));  // basic header      // Got data to process

      //  Decode the packet
      // Decode parts of the data frame header
      bb.position(CD11Frame.FRAME_HDR_LENGTH);
      dataNChan = bb.getInt();
      dataFrameMS = bb.getInt();   // length of time represented by this data frame
      bb.get(timeScratch);         // get 20 bytes of time
      nominalTime = new String(timeScratch);
      CD11Frame.fromCDTimeString(nominalTime, nominal, this);
      channelStringCount = bb.getInt();
      int org = channelStringCount;
      if (channelStringCount % 4 != 0) {
        channelStringCount += 2;
      }
      boolean cd10 = (dataNChan & 0xff0000) != 0;
      dataNChan = dataNChan & 0xffff;
      //if(dbg) 
      prta("Fr: data nch=" + Util.toHex(dataNChan) + " len=" + dataFrameMS + " ms "
              + nominalTime + " " + Util.ascdatetime2(nominal)
              + " chStrCnt=" + channelStringCount + "/" + org + " cd10=" + (cd10 ? "t" : "f"));
      bb.position(bb.position() + channelStringCount);
      //if(!nominalTime.equals(lastNominal) ) {
      // Process all of the channels
      sbdump.append(nominalTime).append("@");
      for (int ich = 0; ich < dataNChan; ich++) {
        try {

          try {
            if (csf == null) {
              csf = new ChannelSubframe(bb, this);
            } else {
              csf.load(bb);
            }
          } catch (RuntimeException e) {
            if (e.toString().contains("BufferUnderflow")) {
              SendEvent.debugEvent("CD11RPUnderF", tag + " Buffer too small csf=" + csf, this);
              break;      // Do not process this one
            } else {

            }
            prta("dumpFrame: Error trying to decode frame.  Skip this sequence ich=" + ich + " of " + dataNChan 
                    + " frMS=" + dataFrameMS + " time=" + nominalTime);
            e.printStackTrace(getPrintStream());
            break;
          }
          //if(dbg) par.prt("ch="+ich+" "+csf.toString());
          //if(dbg) {
          sbdump.append(csf.getStation().substring(0, 8)).append(" ").append(csf.getNsamp()).append(" ");
          if (ich % 8 == 7 && ich != dataNChan - 1) {
            sbdump.append("\n").append(nominalTime).append("@");
          }
          //}
          if (csf.getNsamp() > samples.length) {
            samples = new int[csf.getNsamp()];// self adjust size of this array
          }
          boolean compressError = false;
          try {
            try {
              csf.getSamples(samples);
            } catch (CanadaException e) {
              if (e.toString().contains("not a mult")) {
                prta(tag + " CanadaException: not mult of 4 on allow through " + csf.toString() + " e=" + e);
              } else {
                prta(tag + " CanadaException: CD11L trying to do packet from " + csf.toString() + " e=" + e);
                compressError = true;
              }
            }
            if (!compressError) {
              // Look at status bytes for stuff we can pass on
              seedname = "IM" + csf.getStation();
              byte[] status = csf.getStatusBytes();
              dataQuality = 0;
              ioFlags = 0;
              clockQuality = 0;
              activity = 0;

              if (status != null) {
                //if(status.length < 5) par.prta("Wierd status not null an < 5 "+status.length);
                if (status[0] == 1) {    // status per 0.3 manual table 4.22
                  if (status.length >= 2) {
                    if ((status[1] & 2) != 0) {
                      dataQuality |= 16;     // mark channel as padded
                    }
                    if ((status[1] & 4) != 0) {
                      dataQuality |= 2;       // mark channel as clipped
                    }
                    if ((status[1] & 8) != 0) {
                      activity |= 1;         // calibration underway
                    }
                  }
                  if (status.length >= 4) {
                    if ((status[3] & 4) == 0) {
                      ioFlags |= 32;         // mark clock as locked
                    }
                    clockQuality = status[3] & 7;                    // put GPS status at bottom
                  }
                  String lastLock = null;
                  if (csf.getStatusSize() >= 28) {
                    for (int i = 8; i < 28; i++) {
                      if (status[i] == 0) {
                        status[i] = 32;
                      }
                    }
                    lastLock = new String(status, 8, 20).trim();     // get the last lock string
                  }
                  long cycles = 0;
                  if (lastLock == null) {
                    cycles = 0;
                  } else if (lastLock.length() < 19) {
                    cycles = 0;
                  } else {
                    try {
                      CD11Frame.fromCDTimeString(lastLock, g2, this);
                      long lockDiff = csf.getGregorianTime().getTimeInMillis() - g2.getTimeInMillis();
                      cycles = lockDiff / 3600000L;
                      if (cycles < 0 || cycles > 9) {
                        prt("Unlocked clock: lastLock.length=" + lastLock.length() + " " + lastLock + ": cyc=" + cycles);
                        cycles = 9;
                      }
                    } catch (RuntimeException e) {
                      if (e.toString().contains("impossible yr")) {
                        prt(tag + " Got bad year in last lock field.  Ignore...");
                      } else {
                        prt(tag + " Got Runtime CD11LinkSock()  e=" + e);
                        e.printStackTrace(getPrintStream());
                        StackTraceElement[] stack = e.getStackTrace();
                        for (StackTraceElement stack1 : stack) {
                          prt(stack1.toString());
                        }
                      }
                    }
                  }
                  clockQuality |= cycles * 10;
                  if (status.length >= 32) {
                    usec = status[28] << 24 | status[29] << 16 | status[30] << 8 | status[31];
                  } else {
                    usec = 0;
                  }
                  if (usec != 0) {
                    prt("Found usec not zero =" + usec + " " + csf);
                  }
                }
              }

              gtmp2 = csf.getGregorianTime();
              try {
                MasterBlock.checkSeedName(seedname);
              } catch (IllegalSeednameException e) {
                prt("Bad seed channel name =" + seedname + " e=" + e);
                continue;
              }
              seedname = seedname.toUpperCase();
              Util.clear(seednameSB).append(seedname);    // HACK : not ready to make seednames StringBuilders everywhere
              Util.rightPad(seednameSB, 12);
              //if(seednameSB.charAt(0) == 'A' && seednameSB.charAt(1) == 'Y') par.prt(seednameSB+" "+Util.ascdatetime2(g)+" ns="+csf.getNsamp());
            }     // If not a compression error
          } catch (RuntimeException e) {
            prt(tag + " Got Runtime2 CD11LinkSock()  e=" + e);
            e.printStackTrace(getPrintStream());
            StackTraceElement[] stack = e.getStackTrace();
            for (StackTraceElement stack1 : stack) {
              prt(stack1.toString());
            }
          }
        } catch (RuntimeException e) {
          prta("Got runtime exception doing subframe  ich=" + ich + " of " + dataNChan + " strcnt=" + channelStringCount + " sb=" + sbdump + " e=" + e);
          e.printStackTrace(getPrintStream());
          break;
        }
      }
      sbdump.append("trn=").append(csf == null ? "Null" : csf.getTransform()).append(" ucf=").
              append(csf == null ? "Null" : csf.getUncompressedFormat());
    } catch (RuntimeException e) {
      prta("DumpFrame: runtime error e=" + e);
      e.printStackTrace(getPrintStream());
    }
    return sbdump;
  }

  @Override
  public StringBuilder getConsoleOutput() {
    return consolesb;
  } // Use prt and prta directly

  /**
   * Return the monitor string for Icinga.
   *
   * @return A String representing the monitor key value pairs for this
   * EdgeThread.
   */
  @Override
  public StringBuilder getMonitorString() {
    return monitorsb;
  }

  @Override
  public StringBuilder getStatusString() {
    long current = System.currentTimeMillis();
    Util.clear(statussb);

    statussb.append("CD11SC: ").append(station).append(" ").append(ip).append("/").append(port).append(" ").
            append(connected ? "Conn" : "NOTConn").append(" st=").append(state).append(" gpst=").append(outGaps.inState()).
            append(" alive=").append(isAlive()).
            append(" #oob=").append(noobPackets).append(" #pkt=").
            append(npackets).append(" Rdr: #rcv=").append(reader == null ? "null" : reader.getNframes()).append("\n");
    return statussb;
  }

  class ShutdownCD11SenderClient extends Thread {

    public ShutdownCD11SenderClient() {
    }

    // This is called by the RunTime.shutdown at any termination 
    @Override
    public void run() {
      prta(tag + "CD11CS:  Shutdown() started...");
      terminate();
      // close down the accept() socket
      try {
        if (ss != null) {
          if (!ss.isClosed()) {
            ss.close();
          }
        }
        System.err.println("CD11CS: Shutdown() close accept socket");
      } catch (IOException expected) {
      }

      prta(tag + "CD11CS: Shutdown of CD11SenderClient is complete.");
    }
  }
  private GregorianCalendar g = new GregorianCalendar();
  private GregorianCalendar gtmp = new GregorianCalendar();
  private GregorianCalendar outTime = new GregorianCalendar();
  private CD11Frame frameoob;
  private TreeMap<String, ZeroFilledSpan> spansoob = new TreeMap<>();
  private int maxsecs = 600;        // maximum number of seconds per query
  private byte[] framebuf;
  private ByteBuffer bboob;
  private int maxLengthOut = 0;
  private int[] data = new int[1000];
  private TreeMap<String, ZeroFilledSpan> spans;
  byte[] zero = new byte[1];
  String zerobyte = new String(zero);

  /**
   * Try to get data from a CWB and fill a gap for the given sequences, mark any
   * sent off in gap set.
   *
   * @param lowSeq Low sequence to send.
   * @param highSeq High sequence + 1 to send.
   * @param gaps A GapList to mark any successfully sent one off.
   * @return The number of sequences sent.
   */
  public int doFetchForGap(long lowSeq, long highSeq, GapList gaps) {
    if (frameoob == null) {
      frameoob = new CD11Frame(recsize * 1024, 0, creator, destination, 0L, 0, 0, this);
      prta("Create doFetchForGap frame " + frameoob.getBufferRaw().length + " " + frameoob);
      framebuf = new byte[recsize * 1024];
      bboob = ByteBuffer.wrap(framebuf);
    }
    if (npackets < 3) {
      return -1;
    }
    String[] args = new String[9];
    // get the data from lowSeq to highSeq into the spansoosb (limit to maxsecs) for each channel
    args[0] = "-s";
    args[2] = "-b";
    args[4] = "-d";
    args[6] = "-t";
    args[7] = "null";            // We have a gap, process it
    args[8] = "-uf";
    long now = System.currentTimeMillis();
    g.setTimeInMillis(lowSeq * 10000L + CD11Frame.FIDUCIAL_START_MS);
    double dur = Math.min(highSeq * 10000L + CD11Frame.FIDUCIAL_START_MS - g.getTimeInMillis(), maxsecs * 1000L) * 0.001;
    args[3] = Util.ascdate(g) + "-" + Util.asctime(g);
    args[5] = "" + dur;
    if (dur > 1800.) {
      dur = 1800;
    }
    Iterator<ZeroFilledSpan> itr = spans.values().iterator();
    int nsuccess = 0;
    int nblks = 0;
    while (itr.hasNext()) {
      String name = (itr.next().getSeedname() + "          ").substring(0, 12);
      ZeroFilledSpan span = spansoob.get(name);
      if (span != null) {
        span.clear();
      }
      args[1] = name.trim();
      //if(dbg) prta("GapBuilder: Try gap "+args[1]+" "+args[3]+" for "+args[5]+" sq="+lowSeq+"-"+highSeq+" #pkt="+npackets);
      ArrayList<ArrayList<MiniSeed>> mss = EdgeQueryClient.query(args);
      if (mss == null) {
        if (dbg) {
          prta(Util.clear(tmpsb).append(" GapBuilder: Null returned ").
                  append(args[1]).append(" ").append(args[3]).append(" for ").append(args[5]));
        }
        continue;
      }
      if (mss.isEmpty() || mss.get(0).isEmpty()) {
        if (dbg) {
          prta(Util.clear(tmpsb).append(" GapBuilder: No data returned ").
                  append(args[1]).append(" ").append(args[3]).append(" for ").append(args[5]));
        }
        continue;
      }
      if (span == null) {
        gtmp.setTimeInMillis(g.getTimeInMillis());
        span = new ZeroFilledSpan(mss.get(0).get(0), gtmp, (double) (maxsecs + 100), 2147000000);
        spansoob.put(name, span);
        span.clear();
      }
      for (int i = 0; i < mss.get(0).size(); i++) {
        //prt("GapBuilder:"+mss.get(0).get(i).toString().substring(0,102));
        nblks++;
        try {
          if (mss.get(0).get(i).getRate() > 0.001 && mss.get(0).get(i).getNsamp() > 0) {
            span.addMiniSeed(mss.get(0).get(i));
          }
        } catch (ArrayIndexOutOfBoundsException e) {
          prt(Util.clear(tmpsb).append("GapBuilder: block goes off end of span ").append(e.toString()));
        }
      }
      EdgeQueryClient.freeQueryBlocks(mss, "GapBuilder:", getPrintStream());
      //if(dbg) prta("GapBuilder: rtn="+mss.get(0).size()+" span="+span);
    }
    // Now check each interval and put the data for each 10 seconds into the file
    if (nblks == 0) {
      return 0;      // we got nothing!
    }
    for (int i = 0; i < (int) ((dur + 20.) / 10.); i++) {
      try {
        //prt("GapBuiler: check each 10 second interval from "+Util.ascdate(g)+" "+Util.asctime(g)+" for "+(dur-i*10.));
        gtmp.setTimeInMillis(g.getTimeInMillis());
        if (windowOK(gtmp, spansoob)) {
          gtmp.setTimeInMillis(g.getTimeInMillis());
          buildFrame(frameoob, spansoob, gtmp);
          int l = frameoob.getOutputBytes(bboob);    // Get the data into bbframe around framebuf
          long seqout = frameoob.getOutSeq();
          //int iblk = (int) ((seqout % maxrec)*recsize+1);  // COmpute the block to put the data
          //if(dbg) prta("GapBuilder: success packet seq="+seqout+" "+Util.ascdate(g)+" "+Util.asctime(g)+" to "+iblk+" "+frameoob.toStringBuilder());
          if (dbg) {
            prta(Util.clear(tmpsb).append(tag).append("GapBuilder : out to queueOOB len=").append(l).append(" seqout=").append(seqout).
                    append(" ").append(frameoob.toStringBuilder(null)).append(" ").
                    append(frameoob.getChannelString().toString().replaceAll(zerobyte, "-")));
          }
          queueOOB(framebuf);     // Note this sets the buffer and then waits for it to be sent
          prta(Util.clear(tmpsb).append(tag).append("GapBuilder return from queueOOB! seqout=").append(seqout));
          if (seqout >= gaps.getLowestSeq()) {
            gaps.gotSeq(seqout);    // do not build low gaps from new sequences below current lowest.
          }
          nsuccess++;
          if (l > maxLengthOut) {
            maxLengthOut = l;
          }
          //if(System.currentTimeMillis() - now > 60000) {prta("GapBuilder: break out early request longer than 1 min runtime"); break;}

        }
        //else if(dbg) prta("GapBuilder: not spanned "+Util.ascdate(g)+" "+Util.asctime2(g));
      } catch (IndexOutOfBoundsException e) {
        prta(Util.clear(tmpsb).append("GapBuilder: *** windowOK() skip ").append(e.getMessage()));
      }
      g.setTimeInMillis(g.getTimeInMillis() + 10000);
    }
    return nsuccess;

  }

  private boolean windowOK(GregorianCalendar g, TreeMap<String, ZeroFilledSpan> sp) {
    GregorianCalendar nominal = null;
    Iterator<ZeroFilledSpan> itr = sp.values().iterator();
    boolean ok = true;
    //int nchan=0;
    //String chans="";
    while (itr.hasNext()) {
      //nchan++;
      ZeroFilledSpan span = itr.next();
      if (nominal == null) {
        nominal = span.getStart();
      }
      try {
        if (span.hasFill(g, 10.)) {
          ok = false;
          break;
        }
        //chans += span.getSeedname().substring(7);
      } catch (IndexOutOfBoundsException e) {
        //e.printStackTrace(getPrintStream());
        throw e;
      }
    }
    //prta("GapBUilder: windowOk nchan="+nchan+" chans="+chans);
    return ok;
  }
  private final StringBuilder nameString = new StringBuilder(60);

  private synchronized void buildFrame(CD11Frame cd11frame, TreeMap<String, ZeroFilledSpan> sp, GregorianCalendar next) {

    Iterator<ZeroFilledSpan> itr = sp.values().iterator();
    cd11frame.loadDataFrame(next, 10000);
    int nchan = 0;
    //String chans="";
    while (itr.hasNext()) {
      ZeroFilledSpan span = itr.next();
      int nsamp = (int) (10. * span.getRate() + .0001);
      span.getData(next, nsamp, data, outTime);

      Util.clear(nameString).append(span.getSeedname().substring(2, 10).trim());
      // do any channel substitutions
      if (chnFrom != null) {
        String chan = span.getSeedname().substring(7);
        for (int i = 0; i < chnFrom.length; i++) {
          if (chan.equals(chnFrom[i])) {
            chan = chnTo[i];
            //prt("Change gap chan "+span.getSeedname().substring(7)+" to "+chan);
            nameString.delete(5, nameString.length());
            nameString.append(chan);
            //nameString = nameString.substring(0,5)+chan;
          }
        }
      }
      // do any location code substitutions
      if (locFrom != null) {
        String loc = (span.getSeedname() + "      ").substring(10, 12);
        for (int i = 0; i < locFrom.length; i++) {
          loc = loc.replaceAll(locFrom[i], locTo[i]);
        }
        nameString.append(loc.trim());
        //nameString = nameString+loc.trim();
        //if(dbg) 
        //  par.prt("Would change name from "+span.getSeedname()+" to "+nameString+"|");
      }
      //chans += nameString.substring(5);
      nchan++;
      //prt("Add gap channel "+nameString+" "+Util.asctime2(outTime)+" rt="+span.getRate()+" ns="+nsamp+" span="+span);
      StringBuilder ans = cd11frame.addChannel(nameString, outTime, nsamp, span.getRate(), data, false, 1, 0); // Candadian before auth, seismic data

      //cd11frame.addChannel(span.getSeedname().substring(2,10).trim(), outTime, nsamp, span.getRate(), data, false, 1, 0); // Candadian before auth, seismic data
      span.removeFirst((next.getTimeInMillis() + 10000 - span.getStart().getTimeInMillis()) / 1000.);        // Shift data down by 10 seconds
    }
    next.setTimeInMillis(next.getTimeInMillis() + 10000);

  }

  public static void main(String[] args) {
    EdgeProperties.init();
    Util.setModeGMT();
    Util.setProcess("CD11SenderClient");
    EdgeThread.setMainLogname("aftac");

    EdgeThread.setUseConsole(false);
    boolean dbg = false;
    Util.setNoInteractive(true);

    for (;;) {
      try {
        Thread.sleep(60000);
      } catch (InterruptedException expected) {
      }
    }
  }
}
