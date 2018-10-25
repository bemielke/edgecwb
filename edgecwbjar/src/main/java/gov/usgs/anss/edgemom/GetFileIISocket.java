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
import gov.usgs.anss.edge.ChannelSender;
import gov.usgs.alarm.SendEvent;
import gov.usgs.anss.edge.RawDisk;
import gov.usgs.anss.edge.IllegalSeednameException;
import gov.usgs.anss.edgethread.EdgeThread;
import gov.usgs.anss.seed.MiniSeed;
import gov.usgs.anss.util.Util;
import gov.usgs.edge.config.Channel;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.util.Date;
import java.util.TreeMap;

/**
 * This class receives data from an EWSendFileII socket. It is normally created
 * by the GetFileIIServer as each connection comes in. It inherits its arguments
 * from the GetFileIIServer configuration (no hydra, no udp chan, etc.). This
 * routine only supports 512 byte miniseed.
 *
 * The NQ starts sending RBF (continuous) and TRG (Triggered) and USR (User
 * requested) data. When continuous is found recently, suppress the TRG and USR
 * inserts because they just duplicate data that will be in the continuous and
 * make the Hydra Picker not pick this data due to the in-order problem.
 *
 * @author davidketchum
 */
public final class GetFileIISocket extends EdgeThread {

  private int port;
  private String host;
  private TreeMap<String, Date> continuousList = new TreeMap<>();   // Track time of last continuous (RBF) file
  private int countMsgs;
  private final int heartBeat;
  private final int msgLength;
  private Socket d;           // Socket to the server
  private long lastStatus;     // Time last status was received
  private InputStream in;
  private ChannelSender csend;
  private boolean allow4k;      // 4k data allowed (unimplemented)
  private long bytesIn;
  private long bytesOut;
  private boolean hydra;
  private String seedname;        // The NNSSSSS for a station coming through this socket
  private boolean dbg;
  private EdgeThread parent;      // For logging
  private int waitms;
  private String path;

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

  /**
   * Create a new instance of GetFileIISocket (usually for GetFileIIServer)
   *
   * @param s The socket to read the data from.
   * @param par The parent object. Used for logging to the same file.
   * @param tg The EdgeThread tag to use.
   * @param c The channel sender to use (null if sending is disabled).
   * @param hydraFlag If True, send all data to hydra on receipt.
   * @param allow4096 If true, 4k packets go to database rather than the default
   * of being split up to 512s.
   * @param wait Wait time in milliseconds between each block received.
   * @param dir The directory path to put any files on.
   */
  public GetFileIISocket(Socket s, EdgeThread par, String tg, ChannelSender c,
          boolean hydraFlag, boolean allow4096, int wait, String dir) {
    super("", tg);
    tag += "GFSkt:[" + s.getInetAddress().getHostAddress() + "/" + s.getLocalPort() + "]";
    parent = par;
    this.setPrintStream(parent.getPrintStream());   // Use same log as the server.
    d = s;
    allow4k = allow4096;
    csend = c;
    waitms = wait;
    hydra = hydraFlag;
    path = dir;
    host = s.getInetAddress().getHostAddress();
    port = s.getPort();
    countMsgs = 0;
    msgLength = 512;
    heartBeat = 0;
    try {
      in = s.getInputStream();
    } catch (IOException e) {
      Util.IOErrorPrint(e, "Error getting i/o streams from socket " + host + "/" + port, parent.getPrintStream());
      return;
    }

    parent.prt(tag + "GetFileIISocket: new line parsed to host=" + host + " port=" + port + " len="
            + msgLength + " dbg=" + dbg + " allow4k=" + allow4k + " hydra=" + hydra + " mswait=" + waitms + " csend=" + csend);
    IndexFile.init();
    start();
  }

  @Override
  public void terminate() {
    terminate = true;
    if (in != null) {
      parent.prta(tag + "GFSkt: Terminate started. Close input unit.");
      try {
        in.close();
      } catch (IOException expected) {
      }
    } else {
      parent.prt(tag + "GFSkt: Terminate started. interrupt().");
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
    String filename;
    byte[] buf = new byte[400000];
    byte[] acks = new byte[3];
    acks[0] = 'A';
    acks[1] = 'C';
    acks[2] = 'K';
    MiniSeed ms = null;
    int nillegal = 0;
    running = true;               // Mark we are running

    parent.prta(tag + " Socket is opened.  Start reads. " + host + "/" + port + " " + getName());
    // Read data from the socket and update/create the list of records 
    int len = msgLength;
    int nchar = 0;
    lastStatus = System.currentTimeMillis();

    long now;
    // Try to read in the filename
    boolean eof = false;
    try {
      nchar = Util.socketReadFully(in, buf, 0, 6);
      if (nchar <= 0) {
        eof = true;
      }
      String size = new String(buf, 0, 6).trim();
      parent.prt("1st read size=" + size + " nc=" + nchar);
      int l = Integer.parseInt(size);
      nchar = Util.socketReadFully(in, buf, 0, l);
      parent.prt("2nd read nc=" + nchar);
      if (nchar <= 0) {
        eof = true;
      }
      filename = new String(buf, 0, l);
      parent.prta(tag + " get filename: " + filename);
    } catch (IOException e) {
      parent.prt(tag + " IOError=" + e);
      SendEvent.edgeSMEEvent("GFIIError", "IO Error in GetFileII e=" + e, this);
      eof = true;
      filename = "";
    } catch (RuntimeException e) {
      e.printStackTrace(getPrintStream());
      eof = true;
      filename = "";
    }
    int offset = 0;
    if (eof) {
      parent.prta(tag + " got EOF getting filename");
    }
    if (!eof && !filename.equals("")) {
      try {
        nchar = 0;                // Number of characters returned
        try {
          for (;;) {
            //nchar= in.read(buf, l, len);// get nchar
            nchar = Util.socketReadFully(in, buf, 0, 6);// Get nchar
            if (nchar <= 0) {
              eof = true;
              break;
            }     // EOF - close up - go to outer infinite loop
            String size = new String(buf, 0, 6).trim();
            if (dbg) {
              parent.prt("Read size for block=" + countMsgs + " read size=" + size + " nc=" + nchar + " offset=" + offset);
            }
            len = Integer.parseInt(size);
            // Its the EOF, process the file
            if (len == 0) {
              boolean insertIt = false;
              int off = 0;
              String[] parts = filename.split("_");
              Date lastCont = continuousList.get(parts[1].trim());
              if (parts[0].equals("RBF")) {
                if (lastCont == null) {
                  lastCont = new Date(System.currentTimeMillis());
                  continuousList.put(parts[1].trim(), lastCont);
                } else {
                  lastCont.setTime(System.currentTimeMillis());
                }
                insertIt = true;
              } else {
                if (parts[0].trim().equals("TRG") || parts[0].equals("USR")) {
                  if (lastCont == null) {
                    insertIt = true;
                  } else if (System.currentTimeMillis() - lastCont.getTime() > 14400000) {
                    insertIt = true;
                  }
                }
              }
              parent.prt("GF: file insert=" + insertIt + " " + parts[0] + " " + parts[1] + " last=" + lastCont + " file=" + filename);

              if (!filename.endsWith(".msd") || !insertIt) {
                Util.chkFilePath(path + filename);
                try (RawDisk rw = new RawDisk(path + filename, "rw")) {
                  rw.writeBlock(0, buf, 0, offset);
                  rw.setLength(offset);
                }
              } else {// Break the data into MiniSeed and send it out, reuse our ms holder
                File test = new File(path + filename);
                if (test.exists()) {
                  parent.prta("****Duplicate receipt of file =" + filename + " skip this");
                  break;
                }
                Util.chkFilePath(path + "l" + filename);
                try (RawDisk rw = new RawDisk(path + "l" + filename, "rw")) {
                  rw.writeBlock(0, buf, 0, offset);
                  rw.setLength(offset);
                }
                if (offset % 512 != 0) {
                  parent.prt(" ***** Filesize is not a multiple of 512 what does this mean! " + offset);
                }
                parent.prt(tag + "start processing loop offset=" + offset + " blks=" + offset / 512);
                while (off < offset) {
                  MiniSeed.simpleFixes(buf);
                  /*if(buf[8] == 'N' && buf[9] == 'Q' && buf[10] == '3' && buf[11] == '4' && buf[12] == '1') {
                    buf[18]='G'; buf[19]='S'; buf[8] = 'O'; buf[9]='K'; buf[10]='0'; buf[11]='0'; buf[12]='3';
                  }*/
                  if (ms == null) {
                    ms = new MiniSeed(buf, 0, 512);
                  } else {
                    ms.load(buf, off, 512);
                  }
                  if (dbg || off == 0 && off >= 512) {
                    parent.prt("offset=" + off + " ms=" + ms);
                  }
                  if (tag.equals("GFSkt:")) {
                    tag = tag + ms.getSeedNameString().substring(0, 8).trim();
                  }
                  if (ms.getBlockSize() > 512) {
                    parent.prt("Unexpected blocksize!  abort");
                    break;
                  }
                  off += ms.getBlockSize();
                  bytesIn += ms.getBlockSize();
                  if (seedname == null) {
                    seedname = ms.getSeedNameString().substring(0, 7);
                  }

                  nillegal = 0;                 // Must be a legal seed name
                  String sn = ms.getSeedNameString();
                  try {
                    Channel c = EdgeChannelServer.getChannel(ms.getSeedNameSB());
                    //if(seedname.indexOf("REPV15")>= 0)  par.prta(seedname+": "+c.toString()+" flags="+c.getFlags()+" mask="+mask+" hydra="+hydra);
                    if (c == null) {
                      parent.prta(tag + "GFII: ***** new channel found=" + ms.getSeedNameSB());
                      SendEvent.edgeSMEEvent("ChanNotFnd", "GFII: MiniSEED Channel not found=" + ms.getSeedNameSB() + " new?", this);
                      EdgeChannelServer.createNewChannel(ms.getSeedNameSB(), ms.getRate(), this);
                    }
                    IndexBlock.writeMiniSeedCheckMidnight(ms, hydra, allow4k, tag, this);
                    // Send this channel information to the channel server for latency, etc
                    if (csend != null) {
                      csend.send(ms.getJulian(),
                              (int) (ms.getTimeInMillis() % 86400000), // Milliseconds
                              ms.getSeedNameSB(), ms.getNsamp(), ms.getRate(), ms.getBlockSize());  // key,nsamp,rate and nbytes
                    }
                  } catch (IllegalSeednameException e) {
                    parent.prt("Illegal seedname should be impossible here. ms.sn=" + ms.getSeedNameString() + " was " + sn);
                  }
                }
              }
              d.getOutputStream().write(acks);
              parent.prta(tag + "Processing completed. Send acks. off=" + off + " offset=" + offset + " bytesin=" + bytesIn + " filename=" + filename);
              break;      // Break out of read loop
            }      // If done, get 000000 at the end

            // Read in the data block and add to buffer, expand buffer if needed
            if (offset + len > buf.length) {
              parent.prta(tag + " make buffer bigger len=" + buf.length);
              byte[] temp = new byte[buf.length * 2];
              System.arraycopy(buf, 0, temp, 0, buf.length);
              buf = temp;
            }
            nchar = Util.socketReadFully(in, buf, offset, len);
            if (nchar <= 0) {
              eof = true;
              break;
            }
            offset += nchar;
            if (waitms > 0) {
              try {
                sleep(waitms);
              } catch (InterruptedException e) {
              }
            }
            countMsgs++;
          }   // End infinite for(;;)
        } catch (IOException e) {
          if (e.getMessage().contains("Socket close")) {
            parent.prta(tag + " Socket is closed " + filename);
          } else {
            parent.prt(tag + " IOerror e=" + e + " " + filename);
            SendEvent.edgeSMEEvent("GFIIError", "IO Error in GetFileII e=" + e, this);

          }
        }
      } catch (IllegalSeednameException e) {
        nillegal++;
        if (ms != null) {
          parent.prta(tag + " IllegalSeedName =" + nillegal + " "
                  + Util.toAllPrintable(ms.getSeedNameString()) + " " + e.getMessage());
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
        parent.prt(tag + " RuntimeException : " + e.getMessage() + " nc=" + nchar + " len=" + len);
        for (int i = 0; i < 48; i++) {
          parent.prt(i + " = " + buf[i] + " " + (char) buf[i]);
        }
        e.printStackTrace(this.getPrintStream());
        terminate = true;
      }
    }     // If(!eof) on filename, read
    try {
      if (nchar <= 0) {
        parent.prta(tag + " exited due to EOF after " + countMsgs + " pkts lastMS=" + ms);
      } else if (terminate) {
        parent.prt(tag + " terminate found.");
      } else {
        parent.prta(tag + " exited while(true) for read");
      }
      if (d != null) {
        d.close();
      }
    } catch (IOException e) {
      Util.IOErrorPrint(e, tag + " socket - exit", parent.getPrintStream());
    }
    parent.prta(tag + " is terminated msgs = " + countMsgs + " offset=" + offset + " eof=" + eof + " bytesIn=" + bytesIn + " " + filename);
    running = false;
    terminate = false;
  }

  /**
   * Return the monitor string for Icinga.
   *
   * @return A String representing the monitor key value pairs for this
   * EdgeThread.
   */
  private long lastMonitorOut = System.currentTimeMillis();
  private long lastMonitorBytesOut;

  @Override
  public StringBuilder getMonitorString() {
    if (monitorsb.length() > 0) {
      monitorsb.delete(0, monitorsb.length());
    }
    double elapsed = (System.currentTimeMillis() - lastMonitorOut) / 1000.;
    lastMonitorOut = System.currentTimeMillis();
    long nb = bytesOut - lastMonitorBytesOut;
    lastMonitorBytesOut = bytesOut;
    String tg = tag.replaceAll("\\[", "").replaceAll("\\]", "").trim() + "-";
    return monitorsb.append(tg).append("BytesOut=").append(nb).append("\n").append(tg).
            append("RateKBPS=").append((int) (nb / elapsed)).append("\n");
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
  } //  We use parent.prt directly

}
