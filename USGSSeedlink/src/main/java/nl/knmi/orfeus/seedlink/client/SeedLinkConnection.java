/*
 * This file is part of the ORFEUS Java Library.
 *
 * Copyright (C) 2004 Anthony Lomax <anthony@alomax.net www.alomax.net>
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */

 /*
 * SeedLinkConnection.java - Parts of this have been modified by the USGS to support
 * their real time needs.  In particular the terminate() method was added to insure 
 * termination happens.
 *
 * Created on 05 April 2004, 09:55
 */
package nl.knmi.orfeus.seedlink.client;

/**
 * Note: modified by DCK to allow reuse of SLPacket rather the creating them for
 * each one. The heap reaping was out of hand.
 *
 * @author Anthony Lomax
 */
import gnu.trove.iterator.TLongObjectIterator;
import gnu.trove.map.hash.TLongObjectHashMap;
import gov.usgs.anss.util.Util;
import gov.usgs.anss.seed.MiniSeed;
import nl.knmi.orfeus.seedlink.*;

//import edu.iris.Fissures.seed.container.*;
//import edu.iris.Fissures.seed.exception.*;
import java.io.*;
import java.net.*;
import java.util.*;

//import static nl.knmi.orfeus.seedlink.SLPacket.SLHEADSIZE;
//import static nl.knmi.orfeus.seedlink.SLPacket.SLRECSIZE;
/**
 *
 * Class to manage a connection to a SeedLinkServer using a Socket.
 *
 * See nl.knmi.orfeus.SLClient for an example of how to create and use this a
 * SeedLinkServer object. A new SeedLink application can be created by
 * subclassing SLClient, or by creating a new class and invoking the methods of
 * SeedLinkConnection.
 *
 * @see nl.knmi.orfeus.SLClient
 * @see java.net.Socket
 *
 *
 *
 */
public class SeedLinkConnection {

  // constants
  /**
   * URI/URL prefix for seedlink servers ("seedlnk://")
   */
  public static final String SEEDLINK_PROTOCOL_PREFIX = "seedlink://";

  /**
   * The station code used for uni-station mode
   */
  protected static final String UNISTATION = "UNI";

  /**
   * The network code used for uni-station mode
   */
  protected static final String UNINETWORK = "XX";
  protected static final String UNINETSTATION = "XXUNI  ";

  /**
   * Default size for buffer to hold responses from server.
   */
  protected static final int DFT_READBUF_SIZE = 1024;
  protected byte[] readback = new byte[1024];    // read back buffers for all commands

  /**
   * Character used for delimiting timestamp strings in the statefile.
   */
  protected static char QUOTE_CHAR = '"';

  // publically accessable (get/set) parameters
  /**
   * The host:port of the SeedLink server.
   */
  protected String sladdr = null;

  /**
   * Interval to send keepalive/heartbeat (seconds).
   */
  protected int keepalive = 0;

  /**
   * Network timeout (seconds).
   */
  protected int netto = 120;

  /**
   * Network reconnect delay (seconds).
   */
  protected int netdly = 30;

  /**
   * Logging object.
   */
  protected SLLog sllog = null;

  /**
   * String containing concatination of contents of last terminated set of INFO
   * packets
   */
  protected String infoString = "";

  /**
   * File name for storing state information.
   */
  protected String statefile = null;

  /**
   * Flag to control last packet time usage.
   *
   * if true, begin_time is appended to DATA command
   *
   *
   */
  protected boolean lastpkttime = false;

  // protected parameters
  /**
   * Vector of SLNetStation objects.
   */
  //protected final ArrayList<SLNetStation>	streams = new ArrayList<>(100);
  protected final TLongObjectHashMap<SLNetStation> streams = new TLongObjectHashMap<>();

  /**
   * Beginning of time window.
   */
  // 20050415 AJL changed from Btime to String
  protected String begin_time = null;

  /**
   * End of time window.
   */
  // 20050415 AJL changed from Btime to String
  protected String end_time = null;

  /**
   * Flag to control resuming with sequence numbers.
   */
  protected boolean resume = true;

  /**
   * Flag to indicate multistation mode.
   */
  protected boolean multistation = false;

  /**
   * Flag to indicate dial-up mode.
   */
  protected boolean dialup = false;

  /**
   * Flag to control connection termination.
   */
  protected boolean terminateFlag = false;

  /**
   * ID of the remote SeedLink server.
   */
  protected String server_id = null;

  /**
   * Version of the remote SeedLink server
   */
  protected float server_version = 0.0f;

  /**
   * INFO level to request.
   */
  protected String infoRequestString = null;

  /**
   * The network socket.
   */
  protected java.net.Socket socket = null;

  /**
   * The network socket InputStream.
   */
  protected InputStream socketInputStream = null;

  /**
   * The network socket OutputStream.
   */
  protected OutputStream socketOutputStream = null;

  /**
   * Persistent state information.
   */
  protected SLState state = null;

  /**
   * String to store INFO packet contents.
   */
  protected final StringBuilder infoStrBuf = new StringBuilder();

  /**
   * logging string builder
   */
  private final StringBuilder tmpsb = new StringBuilder(200);

  private SLPacket slpacketReuse;        // DCK : reusable packet space for collect

  @Override
  public String toString() {
    return sladdr + " sock=" + (socket == null ? "null" : socket.getInetAddress() + ":" + socket.getPort()) + " #sreams=" + streams.size();
  }

  /**
   *
   * Creates a new instance of SeedLinkConnection.
   *
   * @param sllog an SLLoc object to control info and error message logging.
   *
   */
  public SeedLinkConnection(SLLog sllog) {

    this.state = new SLState();
    if (sllog != null) {
      this.sllog = sllog;
    } else {
      this.sllog = new SLLog();
    }
    // DCK: this does nothing! byte [] b = new byte[512];

  }

  /**
   *
   * Returns connection state of the connection socket.
   *
   * @return true if connected, false if not connected or socket is not
   * initialized
   *
   */
  public boolean isConnected() {
    if (socket != null && socket.isClosed()) {
      return false;
    }
    return (socket != null && socket.isConnected());

  }

  /**
   *
   * Returns the SLState state object.
   *
   * @return the SLState state object
   *
   */
  public SLState getState() {

    return (state);

  }

  /**
   *
   * Sets the SLLog logging object.
   *
   * @param sllog an SLLoc object to control info and error message logging.
   *
   */
  public void setLog(SLLog sllog) {

    if (sllog != null) {
      this.sllog = sllog;
    }

  }

  /**
   *
   * Returns the SLLog logging object.
   *
   * @return the SLLoc object to control info and error message logging.
   *
   */
  public SLLog getLog() {

    return (sllog);

  }

  /**
   *
   * Sets the network timeout (seconds).
   *
   * @param netto the network timeout in seconds.
   *
   */
  public void setNetTimout(int netto) {

    this.netto = netto;

  }

  /**
   *
   * Returns the network timeout (seconds).
   *
   * @return the network timeout in seconds.
   *
   */
  public int getNetTimout() {

    return (netto);

  }

  /**
   *
   * Sets interval to send keepalive/heartbeat (seconds).
   *
   * @param keepalive the interval to send keepalive/heartbeat in seconds.
   *
   */
  public void setKeepAlive(int keepalive) {

    this.keepalive = keepalive;

  }

  /**
   *
   * Returns the interval to send keepalive/heartbeat (seconds).
   *
   * @return the interval to send keepalive/heartbeat in seconds.
   *
   */
  public int getKeepAlive() {

    return (keepalive);

  }

  /**
   *
   * Sets the network reconnect delay (seconds).
   *
   * @param netdly the network reconnect delay in seconds.
   *
   */
  public void setNetDelay(int netdly) {

    this.netdly = netdly;

  }

  /**
   *
   * Returns the network reconnect delay (seconds).
   *
   * @return the network reconnect delay in seconds.
   *
   */
  public int getNetDelay() {

    return (netdly);

  }

  /**
   *
   * Sets the host:port of the SeedLink server.
   *
   * @param sladdr the host:port of the SeedLink server.
   *
   */
  public void setSLAddress(String sladdr) {

    if (sladdr.startsWith(SEEDLINK_PROTOCOL_PREFIX)) {
      sladdr = sladdr.substring(SEEDLINK_PROTOCOL_PREFIX.length());
    }

    this.sladdr = sladdr;

  }

  /**
   *
   * Sets a specified start time for beginning of data transmission .
   *
   * @param lastpkttime if true, beginning time of last packet recieved for each
   * station is appended to DATA command on resume.
   *
   */
  public void setLastpkttime(boolean lastpkttime) {

    this.lastpkttime = lastpkttime;

  }

  /**
   *
   * Sets begin_time for initiation of continuous data transmission.
   *
   * @param startTimeStr start time in in SeedLink string format:
   * "year,month,day,hour,minute,second".
   *
   */
  // 20050415 AJL added to support continuous data transfer from a time in the past
  public void setBeginTime(String startTimeStr) {

    if (startTimeStr != null) {
      this.begin_time = startTimeStr;
    } else {
      this.begin_time = null;
    }

  }

  /**
   *
   * Sets end_time for termitiation of data transmission.
   *
   * @param endTimeStr start time in in SeedLink string format:
   * "year,month,day,hour,minute,second".
   *
   */
  // 20071204 AJL added to support windowed data transfer
  public void setEndTime(String endTimeStr) {

    if (endTimeStr != null) {
      this.end_time = endTimeStr;
    } else {
      this.end_time = null;
    }

  }

  /**
   *
   * Sets terminate flag, closes connection and clears state as soon as possible
   *
   *
   */
  public void terminate() {

    terminateFlag = true;
    // In certain bad conditions, no packets are flowing and setting terminateFlag will not
    // ever terminate.  Wait up to 30 seconds and if it does not happen call doTerminate() ourselves
    if (shutdown == null) {
      shutdown = new SeedLinkConnectionTerminate(this);// DCK: USGS has many, so need to make thread
    }
  }
  SeedLinkConnectionTerminate shutdown = null;

  /**
   * Since it might take some time to stop a SeedLinkConnection, spawn a thread
   * with the details. This allows all of the shutdowns to run in parallel and
   * since thre might be many SeedLinkClients, we do not want to create a choke
   * point here.
   *
   */
  final class SeedLinkConnectionTerminate extends Thread {

    SeedLinkConnection t;

    SeedLinkConnectionTerminate(SeedLinkConnection thr) {
      t = thr;
      start();
    }

    @Override
    public void run() {
      // In certain bad conditions, no packets are flowing and setting terminateFlag will not
      // ever terminate.  Wait up to 30 seconds and if it does not happen call doTerminate() ourselves
      int loop;
      for (loop = 0; loop < 30; loop++) {
        if (t.socket == null) {
          break;
        }
        try {
          Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
      }
      if (loop >= 30) {

        t.sllog.log(true, 5, Util.clear(tmpsb).append("[").append(t.sladdr).append("] *** terminate() had to doTerminate() - no data is flowing"));//DCK was -1
        t.doTerminate();
      } else {
        t.sllog.log(true, 5, Util.clear(tmpsb).append("[").append(t.sladdr).append("] terminate() worked normally loop=").append(loop)); //DCK was -1
      }
    }
  }

  /**
   *
   * Returns the host:port of the SeedLink server.
   *
   * @return the host:port of the SeedLink server.
   *
   */
  public String getSLAddress() {

    return (sladdr);

  }

  /**
   *
   * Returns a copy of the Vector of SLNetStation objects.
   *
   * @return a copy of the Vector of SLNetStation objects.
   *
   */
  /*public ArrayList<SLNetStation> getStreams() {
        
        return (ArrayList<SLNetStation>) streams.clone();
        
    }*/
  /**
   *
   * Returns a copy of the Vector of SLNetStation objects.
   *
   * @return a copy of the Vector of SLNetStation objects.
   *
   */
  public TLongObjectHashMap<SLNetStation> getStreams() {

    return (TLongObjectHashMap<SLNetStation>) streams;

  }

  /**
   *
   * Returns the results of the last INFO request.
   *
   * @return concatination of contents of last terminated set of INFO packets
   *
   */
  public String getInfoString() {

    return (infoString);

  }

  /**
   *
   * Creates an info String from a String Buffer
   *
   * @param strBuf the buffer to convert to an INFO String.
   *
   * @return the INFO Sting.
   */
  protected String createInfoString(StringBuilder strBuf) {

    int start = 0;
    while ((start = strBuf.indexOf("><", start)) > 0) {
      strBuf.replace(start, start + 2, ">\n<");
    }

    return (strBuf.toString().trim());

  }

  /**
   *
   * Check this SeedLinkConnection description has valid parameters.
   *
   * @return true if pass and false if problems were identified.
   *
   */
  protected boolean checkslcd() {

    boolean retval = true;

    if (streams.size() < 1 && infoRequestString == null) {
      sllog.log(true, 0, Util.clear(tmpsb).append("[").append(sladdr).append("] stream chain AND info type are empty"));
      retval = false;
    }

    int ndx;
    if (sladdr == null) {
      sllog.log(false, 1, Util.clear(tmpsb).append("[").append(sladdr).append("] [").append(sladdr).append("] server address is empty"));
      retval = false;
    } else if ((ndx = sladdr.indexOf(':')) < 1 || sladdr.length() < ndx + 2) {
      sllog.log(true, 0, Util.clear(tmpsb).append("[").append(sladdr).append("] host address: [").append(sladdr).append("] is not in `[hostname]:port' format"));
      retval = false;
    }

    return (retval);
  }    // End of sl_checkslconn

  /**
   *
   * Read a list of streams and selectors from a file and add them to the stream
   * chain for configuring a multi-station connection.
   *
   * If 'defselect' is not null it will be used as the default selectors for
   * entries will no specific selectors indicated.
   *
   * The file is expected to be repeating lines of the form:
   * <PRE>
   *   NET STA [selectors]
   * </PRE> For example:
   * <PRE>
   * # Comment lines begin with a '#' or '*'
   * GE ISP  BH?.D
   * NL HGN
   * MN AQU  BH?  HH?
   * </PRE>
   *
   * @param streamfile name of file containing list of streams and selectors.
   * @param defselect default selectors.
   *
   * @return the number of streams configured.
   *
   * @exception SeedLinkException on error.
   *
   */
  public int readStreamList(String streamfile, String defselect) throws SeedLinkException {

    int addret;

    // Open the stream list file
    BufferedReader buffReader = null;
    StreamTokenizer streamTkz = null;
    try {
      buffReader = new BufferedReader(new FileReader(streamfile));
      streamTkz = new StreamTokenizer(buffReader);
    } catch (FileNotFoundException e) {
      sllog.log(true, 0, Util.clear(tmpsb).append("[").append(sladdr).append("] ").append(e).
              append(": opening stream list file: ").append(streamfile));
      throw (new SeedLinkException(tmpsb.toString()));
    }

    sllog.log(false, 1, Util.clear(tmpsb).append("reading stream list from ").append(streamfile));

    streamTkz.wordChars('.', '.');
    streamTkz.wordChars('?', '?');
    streamTkz.eolIsSignificant(true);
    streamTkz.commentChar('#');
    streamTkz.commentChar('*');

    // AJL 20080815 - Bug fixes to read stream tokens that contain numbers
    streamTkz.ordinaryChars('0', '9');
    streamTkz.wordChars('0', '9');

    int linecount = 0;
    int stacount = 0;

    try {

      while (streamTkz.ttype != StreamTokenizer.TT_EOF) {

        linecount++;

        String net = null;
        String station = null;
        String selectors = null;

        boolean dataline = false;

        streamTkz.nextToken();
        sllog.log(false, 1, Util.clear(tmpsb).append("readStreamList1Net: <").append(streamTkz.sval).append("><").
                append(streamTkz.nval).append(">"));
        if (streamTkz.ttype == StreamTokenizer.TT_EOF) {
          break;
        }
        if (streamTkz.ttype == StreamTokenizer.TT_WORD) {
          net = streamTkz.sval;
          dataline = true;
          streamTkz.nextToken();
          sllog.log(false, 1, Util.clear(tmpsb).append("readStreamList2Stat: <").append(streamTkz.sval).
                  append("><").append(streamTkz.nval).append(">"));
          if (streamTkz.ttype == StreamTokenizer.TT_WORD) {
            station = streamTkz.sval;
            streamTkz.nextToken();
            sllog.log(false, 1, Util.clear(tmpsb).append("readStreamList3Sel: <").append(streamTkz.sval).
                    append("><").append(streamTkz.nval).append(">"));
            if (streamTkz.ttype == StreamTokenizer.TT_WORD) {   // selectors present
              selectors = "";
              do {
                selectors += " " + streamTkz.sval;
                streamTkz.nextToken();
                sllog.log(false, 1, Util.clear(tmpsb).append("readStreamList4: <").append(streamTkz.sval).
                        append("><").append(streamTkz.nval).append(">"));
              } while (streamTkz.ttype == StreamTokenizer.TT_WORD);
            }
            sllog.log(false, 0, Util.clear(tmpsb).append("nt=").append(net).append(" stat=").append(station).
                    append(" selectors=").append(selectors));
          }
        }
        // skip to next line
        while (streamTkz.ttype != StreamTokenizer.TT_EOL && streamTkz.ttype != StreamTokenizer.TT_EOF) {
          streamTkz.nextToken();
          sllog.log(false, 1, Util.clear(tmpsb).append("readStreamList5: <").append(streamTkz.sval).
                  append("><").append(streamTkz.nval).append(">"));
        }
        if (!dataline) {
          continue;
        }

        if (net == null) {
          sllog.log(true, 0, Util.clear(tmpsb).append("invalid or missing network string at line ").append(linecount).
                  append(" of stream list file: ").append(streamfile));
          continue;
        }
        if (station == null) {
          sllog.log(true, 0, Util.clear(tmpsb).append("invalid or missing station string line ").append(linecount).
                  append(" of stream list file: ").append(streamfile));
          continue;
        }

        // Add this stream to the stream chain
        if (selectors != null) {
          Util.clear(netstat).append((net + "  ").substring(0, 2)).append((station + "    ").substring(0, 5));
          addret = addStream(netstat, selectors, -1, null);
          stacount++;
        } else {
          Util.clear(netstat).append((net + "  ").substring(0, 2)).append((station + "    ").substring(0, 5));
          try {
            addret = addStream(netstat, defselect, -1, null);
          } catch (RuntimeException e) {
            e.printStackTrace();
          }
          stacount++;
        }

      }
      if (stacount == 0) {
        sllog.log(true, -1, Util.clear(tmpsb).append("[").append(sladdr).append("] no streams defined in ").append(streamfile));
      } else {
        sllog.log(false, -1, Util.clear(tmpsb).append("[").append(sladdr).append("] Read ").append(stacount).
                append(" streams from ").append(streamfile));
      }

    } catch (IOException e) {
      sllog.log(true, 0, Util.clear(tmpsb).append("[").append(sladdr).append("] ").append(e).
              append(": reading stream list file: ").append(streamfile));
      throw (new SeedLinkException(tmpsb.toString()));
    } finally {
      try {
        buffReader.close();
      } catch (IOException e) {
      }
    }

    return (stacount);

  }              // End of read_streamlist()

  /**
   *
   * Parse a string of streams and selectors and add them to the stream chain
   * for configuring a multi-station connection.
   *
   * The string should be of the following form:
   * "stream1[:selectors1],stream2[:selectors2],..."
   *
   * For example:
   * <PRE>
   * "IU_KONO:BHE BHN,GE_WLF,MN_AQU:HH?.D"
   * </PRE>
   *
   * @param streamlist list of streams and slectors.
   * @param defselect default selectors.
   *
   * @return the number of streams configured.
   *
   * @exception SeedLinkException on error.
   *
   */
  public int parseStreamlist(String streamlist, String defselect) throws SeedLinkException {

    int stacount = 0;

    // Parse the streams and selectors
    StringTokenizer strTkz = new StringTokenizer(streamlist, ",");

    while (strTkz.hasMoreTokens()) {

      String net;
      String station = null;
      String staselect;

      boolean configure = true;

      try {

        String streamToken = strTkz.nextToken();
        StringTokenizer reqTkz = new StringTokenizer(streamToken, ":");
        String reqToken = reqTkz.nextToken();
        StringTokenizer netStaTkz = new StringTokenizer(reqToken, "_");

        // Fill in the NET and STA fields
        if (netStaTkz.countTokens() != 2) {
          sllog.log(true, 0, Util.clear(tmpsb).append("[").append(sladdr).
                  append("] not in NET_STA format: ").append(reqToken));
          //configure = false;
        } else {
          // First token, should be a network code
          net = netStaTkz.nextToken();
          if (net.length() < 1) {
            sllog.log(true, 0, Util.clear(tmpsb).append("[").append(sladdr).
                    append("] not in NET_STA format: ").append(reqToken));
            configure = false;
          } else {
            // Second token, should be a station code
            station = netStaTkz.nextToken();
            if (station.length() < 1) {
              sllog.log(true, 0, Util.clear(tmpsb).append("[").append(sladdr).
                      append("] not in NET_STA format: ").append(reqToken));
              configure = false;
            }
          }

          if (reqTkz.hasMoreTokens()) {   // Selectors were included
            // Second token of reqTkz, should be selectors
            staselect = reqTkz.nextToken();
            if (staselect.length() < 1) {
              sllog.log(true, 0, Util.clear(tmpsb).append("[").append(sladdr).
                      append("] empty selector: ").append(reqToken));
              configure = false;
            }
          } else {    // If no specific selectors, use the default
            staselect = defselect;
          }

          // Add this to the stream chain
          if (configure) {
            try {
              Util.clear(netstat).append((net + "  ").substring(0, 2)).append((station + "    ").substring(0, 5));
              addStream(netstat, staselect, -1, null);
              stacount++;
            } catch (SeedLinkException e) {
              throw (e);
            }
          }
        }

      } catch (NoSuchElementException e) {

      }

    }

    if (stacount == 0) {
      sllog.log(true, 0, Util.clear(tmpsb).append("[").append(sladdr).append("] no streams defined in stream list"));
    } else if (stacount > 0) {
      sllog.log(false, 2, Util.clear(tmpsb).append("parsed ").append(stacount).append(" streams from stream list"));
    }

    return (stacount);

  }              // End of sl_parse_streamlist()

  /**
   *
   * Add a new stream entry to the stream chain for the given net/station
   * parameters. If the stream entry already exists do nothing and return 1.
   * Also sets the multistation flag to true.
   *
   * @param nnsssss network code and station code padded two and 5
   * @param selectors selectors for this net/station, null if none.
   * @param seqnum SeedLink sequence number of last packet received, -1 to start
   * at the next data.
   * @param timestamp SeedLink time stamp in SEED
   * "year,day-of-year,hour,minute,second" format for last packet received, null
   * for none.
   *
   * @return 0 if successfully added, 1 if an entry for network and station
   * already exists.
   *
   * @exception SeedLinkException on error.
   *
   */
  protected int addStream(StringBuilder nnsssss, String selectors, int seqnum, String timestamp) throws SeedLinkException {

    // Sanity, check for a uni-station mode entry
    if (streams.size() > 0) {
      TLongObjectIterator<SLNetStation> itr = streams.iterator();
      itr.advance();

      SLNetStation stream = itr.value();
      if (Util.stringBuilderEqual(stream.getNetStation(), UNINETSTATION)) {
        //if (stream.net.equals(UNINETWORK) &&  stream.station.equals(UNISTATION)) {
        sllog.log(true, 0, Util.clear(tmpsb).append("[").append(sladdr).append("] addStream called, but uni-station mode already configured!"));
        throw (new SeedLinkException(tmpsb.toString()));
      }
    }

    // Search the stream chain to see if net/station/selector already present
    SLNetStation stream = streams.get(Util.getHashSB(nnsssss));
    if (stream != null) {
      if (Util.stringBuilderEqual(stream.getNetStation(), nnsssss) && stream.selectors.equals(selectors)) {
        return 1;   // We have this stream already
      } else if (Util.stringBuilderEqual(stream.getNetStation(), nnsssss)) {
        return stream.appendSelectors(selectors);
      } else {
        sllog.log(true, 0, Util.clear(tmpsb).append("[").append(sladdr).
                append("] Add stream impossible! stations do not match! ").
                append(stream.getNetStation()).append("!=").append(nnsssss));
      }
    }
    /*for (SLNetStation stream : streams) {

        if (stream.net.equals(net) && stream.station.equals(station) && stream.selectors.equals(selectors))
        	return(1);	// stream already exists in the chain
        if (stream.net.equals(net) && stream.station.equals(station))
          return (stream.appendSelectors(selectors)); // stream already exists in the chain, append selectors
        }*/

    // Add new stream
    SLNetStation newstream = new SLNetStation(nnsssss, selectors, seqnum, timestamp);
    streams.put(Util.getHashSB(nnsssss), newstream);
    sllog.log(false, 0, Util.clear(tmpsb).append("[").append(sladdr).append("] Add stream ").
            append(nnsssss).append(" ").append(selectors).append(" sq=").append(seqnum).append(" ").
            append(timestamp).append(" streams.size=").append(streams.size()));

    multistation = true;

    return (0);

  }   // End of addstream()
  private final StringBuilder netstat = new StringBuilder(7);

  /**
   *
   * Set the parameters for a uni-station mode connection for the given SLCD
   * struct. If the stream entry already exists, overwrite the previous
   * settings. Also sets the multistation flag to 0 (false).
   *
   * @param selectors selectors for this net/station, null if none.
   * @param seqnum SeedLink sequence number of last packet received, -1 to start
   * at the next data.
   * @param timestamp SeedLink time stamp in SEED
   * "year,day-of-year,hour,minute,second" format for last packet received, null
   * for none.
   *
   * @exception SeedLinkException on error.
   *
   */
  public void setUniParams(String selectors, int seqnum, String timestamp) throws SeedLinkException {

    // Sanity, check for a multi-station mode entry
    if (streams.size() > 0) {
      SLNetStation stream = (SLNetStation) streams.get(0);
      if (!Util.stringBuilderEqual(stream.getNetStation(), UNINETSTATION)) {
        //if (!stream.net.equals(UNINETWORK) ||  !stream.station.equals(UNISTATION)) {
        sllog.log(true, 0, Util.clear(tmpsb).append("[").append(sladdr).
                append("] setUniParams called, but multi-station mode already configured!"));
        throw (new SeedLinkException(tmpsb.toString()));
      }
    }

    // Add new stream
    //SLNetStation newstream = new SLNetStation(UNINETWORK, UNISTATION, selectors, seqnum, timestamp);
    Util.clear(netstat).append(UNINETSTATION);
    SLNetStation newstream = new SLNetStation(netstat, selectors, seqnum, timestamp);
    streams.put(1, newstream);

    multistation = false;

  }   // End of sl_setuniparams()

  /**
   *
   * Set the state file and recover state.
   *
   * @param statefile path and name of statefile.
   *
   * @return the number of stream chains recovered.
   *
   * @exception SeedLinkException on error.
   *
   */
  public int setStateFile(String statefile) throws SeedLinkException {

    this.statefile = statefile;
    return (recoverState(statefile));

  }

  /**
   *
   * Recover the state file and put the sequence numbers and time stamps into
   * the pre-existing stream chain entries.
   *
   * @param statefile path and name of statefile.
   *
   * @return the number of stream chains recovered.
   *
   * @exception SeedLinkException on error.
   *
   */
  public int recoverState(String statefile) throws SeedLinkException {

    // open the state file
    BufferedReader buffReader = null;
    StreamTokenizer streamTkz = null;
    try {
      buffReader = new BufferedReader(new FileReader(statefile));
      streamTkz = new StreamTokenizer(buffReader);
    } catch (FileNotFoundException e) {
      sllog.log(true, 0, Util.clear(tmpsb).append("[").append(sladdr).append("] ").append(e).append(": opening state file: ").append(statefile));
      throw (new SeedLinkException(tmpsb.toString()));
    }

    // recover the state
    sllog.log(false, 1, Util.clear(tmpsb).append("[").append(sladdr).
            append("] recovering connection state from state file: ").append(statefile));

    streamTkz.commentChar('#');
    streamTkz.commentChar('*');
    streamTkz.eolIsSignificant(true);
    streamTkz.quoteChar(QUOTE_CHAR);

    int linecount = 0;
    int stacount = 0;

    try {

      while (streamTkz.ttype != StreamTokenizer.TT_EOF) {

        linecount++;

        String net = null;
        String station = null;
        int seqnum = -1;
        String timeStr = "";

        streamTkz.nextToken();
        if (streamTkz.ttype == StreamTokenizer.TT_EOF) {
          break;
        }
        if (streamTkz.ttype == StreamTokenizer.TT_WORD) {
          net = streamTkz.sval;
          streamTkz.nextToken();
          //DCK: changed stations to quoted strings because stations starting with number failed 
          if (streamTkz.ttype == StreamTokenizer.TT_WORD || streamTkz.ttype == QUOTE_CHAR) {
            station = streamTkz.sval.trim();
            streamTkz.nextToken();
            if (streamTkz.ttype == StreamTokenizer.TT_NUMBER) {
              seqnum = (int) Math.round(streamTkz.nval);
              streamTkz.nextToken();
              if (streamTkz.ttype == QUOTE_CHAR) {
                timeStr = streamTkz.sval;
                streamTkz.nextToken();
              }
            }
          }
          sllog.log(false, 0, Util.clear(tmpsb).append("Recover State: ").append(net).append(" ").append(station).append(" ").append(seqnum).append(" ").append(timeStr));
        }
        while (streamTkz.ttype != StreamTokenizer.TT_EOL && streamTkz.ttype != StreamTokenizer.TT_EOF) {
          streamTkz.nextToken();
        }

        // check for completeness of read
        switch (timeStr) {
          case "":
            sllog.log(true, 0, Util.clear(tmpsb).append("error parsing line ").append(linecount).
                    append(" of ").append(statefile).append(" net=").append(net).append("/").
                    append(station).append("sq=").append(seqnum).append(" tm=").append(timeStr));
            continue;
          case "null":
            continue;
        }

        // Search for a matching net/station in the stream chain
        Util.clear(netstat).append((net + "  ").substring(0, 2)).append((station + "     ").substring(0, 5));
        SLNetStation stream = streams.get(Util.getHashSB(netstat));
        //for (SLNetStation stream1 : streams) {
        //  stream = (SLNetStation) stream1;
        //  if (stream.net.equals(net) && stream.station.equals(station))
        //    break;	// found
        //  stream = null;
        //}
        // update net/station entry in the stream chain
        if (stream != null) {
          stream.seqnum = seqnum;
          if (timeStr != null) {
            try {
              stream.ltime = Util.stringToDate2(timeStr).getTime();
              //String tmp = Util.ascdatetime2(stream.ltime)+" "+timeStr;
              //  stream.ltime = new Btime(timeStr).getEpochTime();
              stacount++;
            } catch (Exception e) {
              sllog.log(true, 0, Util.clear(tmpsb).append("parsing timestamp in line ").append(linecount).append(" of state file: ").append(e));
            }
          }
        }

      }

      if (stacount == 0) {
        sllog.log(true, 0, Util.clear(tmpsb).append("[").append(sladdr).append("] no matching streams found in ").append(statefile));
      } else {
        sllog.log(false, 0, Util.clear(tmpsb).append("[").append(sladdr).append("] recoverd state for  ").
                append(stacount).append(" streams in ").append(statefile).append(" size=").append(streams.size()));
      }

    } catch (IOException e) {
      sllog.log(true, 0, Util.clear(tmpsb).append("[").append(sladdr).append("] ").append(e).append(": reading state  file: ").append(statefile));
      throw (new SeedLinkException(tmpsb.toString()));
    } finally {
      try {
        buffReader.close();
      } catch (IOException e) {
      }
    }

    return (stacount);

  }

  private final StringBuilder sc = new StringBuilder(1000);
  private final byte[] scbuf = new byte[1000];
  private final ArrayList<SLNetStation> strs = new ArrayList<>(100);   // Sort these to order for saving??

  /**
   *
   * Save all current sequence numbers and time stamps into the given state
   * file.
   *
   * @param statefile path and name of statefile.
   *
   * @return the number of stream chains saved.
   *
   * @exception SeedLinkException on error.
   *
   */
  public int saveState(String statefile) throws SeedLinkException {
    Util.clear(sc);    // DCK: rewrite to use a StringBuilder and random access file.
    // Loop through the stream chain
    //sllog.log(false, 0, Util.clear(tmpsb).append("[").append(sladdr).
    //        append("] saving connection state to state file size=").append(streams.size()));//DCK: comment out concatenation to save strings
    TLongObjectIterator<SLNetStation> itr = streams.iterator();
    strs.clear();
    while (itr.hasNext()) {
      itr.advance();
      strs.add(itr.value());
    }
    Collections.sort(strs);
    for (SLNetStation curstream : strs) {
      //for (SLNetStation curstream : streams) {
      // get stream (should be only stream present)
      StringBuilder tmp = curstream.getNetStation();
      //sc.append(curstream.net).append(" ").append(QUOTE_CHAR).append(curstream.station).append(QUOTE_CHAR).
      sc.append(tmp.charAt(0)).append(tmp.charAt(1)).append(" ").append(QUOTE_CHAR).
              append(tmp.charAt(2)).append(tmp.charAt(3)).
              append(tmp.charAt(4)).append(tmp.charAt(5)).
              append(tmp.charAt(6)).append(QUOTE_CHAR).
              append(" ").append(curstream.seqnum).append(" ").
              append(QUOTE_CHAR);
      Util.toDOYString(curstream.ltime, sc);
      sc.append(QUOTE_CHAR).append("\n");
    }
    sllog.log(false, 2, sc);
    try {
      sllog.log(false, 0, Util.clear(tmpsb).append("[").append(sladdr).append("] write state file=").
              append(statefile).append(" lines=").append(strs.size()).append(" filesize=").append(sc.length()));
      Util.writeFileFromSB(statefile, sc);
    } catch (IOException e) {
      sllog.log(true, 0, Util.clear(tmpsb).append("[").append(sladdr).append("] ").append(e).
              append(": error writing state file: ").append(statefile));
      throw (new SeedLinkException(tmpsb.toString()));
    }
    return streams.size();
    /*// open the state file
        BufferedWriter buffWriter = null;
        try { 
            buffWriter = new BufferedWriter(new FileWriter(statefile));
        } catch (IOException ioe) {
            sllog.log(true, 0, "[" + sladdr + "] cannot open state file: " + ioe);
            String message = "[" + sladdr + "] " + ioe + ": opening state file: " + statefile;
            sllog.log(true, 0,  message);
            throw(new SeedLinkException(message));
        }
        
        sllog.log(false, 2, /*"[" + sladdr + "]" saving connection state to state file");//DCK: comment out concatenation to save strings
        
        int stacount = 0;
        try {
          // Loop through the stream chain
          for (SLNetStation curstream : streams) {
            // get stream (should be only stream present)
            buffWriter.write(curstream.net + " " + QUOTE_CHAR
                    + curstream.station + QUOTE_CHAR+ " " + curstream.seqnum
                    + " " + QUOTE_CHAR + Util.toDOYString(curstream.ltime) + QUOTE_CHAR + "\n");
            sllog.log(false, 2, curstream.net + " " + curstream.station + " " + curstream.seqnum
                    + " " + QUOTE_CHAR + Util.toDOYString(curstream.ltime) + QUOTE_CHAR );
          }
            
        } catch (IOException e) {
            String message = "[" + sladdr + "] " + e + ": writing state file: " + statefile;
            sllog.log(true, 0,  message);
            throw(new SeedLinkException(message));
        } finally {
            try {
                buffWriter.close();
            } catch (Exception e) {;}
        }
        
        return(stacount);*/

  }

  protected SLPacket doTerminate() {

    sllog.log(false, 0, Util.clear(tmpsb).append("[").append(sladdr).append("] terminating collect loop"));
    close();      // DCK: this seems better as its saves the state as well disconnect()
    //disconnect();
    //state.state = SLState.SL_DOWN;  // AJL added
    // state.state = SLState.SL_DATA; // libslink error?
    state = new SLState();  // AJL added AJL 20040526
    infoRequestString = null;   // AJL added AJL 20040526
    Util.clear(infoStrBuf);
    //infoStrBuf = new StringBuffer();   // AJL added AJL 20040526
    return (SLPacket.SLTERMINATE);  // AJL added AJL 20040526

  }

  /**
   *
   * Routine to manage a connection to a SeedLink server based on the values
   * given in this SeedLinkConnection, and to collect data.
   *
   * Designed to run in a tight loop at the heart of a client program, this
   * function will return every time a packet is received.
   *
   * @return an SLPacket when something is received.
   * @return null when the connection was closed by the server or the
   * termination sequence completed.
   *
   * @exception SeedLinkException on error.
   *
   */
  private long lastStateSave;
  private byte[] bytesread = new byte[8192];

  public SLPacket collect(SLPacket slpacket) throws SeedLinkException {

    //terminateFlag = false;   //DCK: if terminate flag is set by terminate, why would this every undo that!
    // Check if the infoRequestString was set
    if (infoRequestString != null) {
      state.query_mode = SLState.INFO_QUERY;
    }

    // If the connection is not up check this SeedLinkConnection and reset the timing variables */
    if (socket == null || !socket.isConnected()) {
      if (!checkslcd()) {
        sllog.log(true, 0, Util.clear(tmpsb).append("[").append(sladdr).append("] problems with the connection description"));
        throw (new SeedLinkException(tmpsb.toString()));
      }
      state.previous_time = UtilSL.getCurrentTime();  // Initialize timing base
      state.netto_trig = -1;	   // Init net timeout trigger to reset state
      state.keepalive_trig = -1;	   // Init keepalive trigger to reset state
    }

    // Start the primary loop
    int npass = 0;
    while (true) {

      // DCK this just creates lots of useless strings
      //DCK sllog.log(false, 5, "[" + sladdr + "] primary loop pass " + npass);
      npass++;

      //we are terminating (abnormally!)
      if (terminateFlag) {
        return (doTerminate());
      }

      // not terminating
      if (socket == null || !socket.isConnected()) {
        state.state = SLState.SL_DOWN;
      }

      // Check for network timeout
      if (state.state == SLState.SL_DATA && netto > 0 && state.netto_trig > 0) {
        sllog.log(false, 0, Util.clear(tmpsb).append("[").append(sladdr).append("] network timeout (").
                append(netto).append("s), reconnecting in ").append(netdly).append("s"));
        close();
        state.state = SLState.SL_DOWN;
        state.netto_trig = -1;
        state.netdly_trig = -1;
        return doTerminate();
      }

      // Check if a keepalive packet needs to be sent
      if (state.state == SLState.SL_DATA && !state.expect_info && keepalive > 0 && state.keepalive_trig > 0) {
        sllog.log(false, 2, Util.clear(tmpsb).append("[").append(sladdr).append("] sending: keepalive request"));
        try {
          sendInfoRequest("ID", 3);
          state.query_mode = SLState.KEEP_ALIVE_QUERY;
          state.expect_info = true;
          state.keepalive_trig = -1;
        } catch (SeedLinkException e) {	// SeedLink version does not support INFO requests

        } catch (IOException ioe) {	// I/O error, assume link is down
          sllog.log(false, 0, Util.clear(tmpsb).append("[").append(sladdr).
                  append("] I/O error, reconnecting in ").append(netdly).append("s"));
          disconnect();
          state.state = SLState.SL_DOWN;
        }
      }

      // Check if an in-stream INFO request needs to be sent
      if (state.state == SLState.SL_DATA && !state.expect_info && infoRequestString != null) {
        try {
          sendInfoRequest(infoRequestString, 1);
          state.query_mode = SLState.INFO_QUERY;
          state.expect_info = true;
        } catch (SeedLinkException e) {	// SeedLink version does not support INFO requests
          state.query_mode = SLState.NO_QUERY;
        } catch (IOException ioe) {	// I/O error, assume link is down
          sllog.log(false, 0, Util.clear(tmpsb).append("[").append(sladdr).
                  append("] I/O error, reconnecting in ").append(netdly).append("s"));
          disconnect();
          state.state = SLState.SL_DOWN;
        }
        infoRequestString = null;
      }

      // Throttle the loop while delaying
      if (state.state == SLState.SL_DOWN && state.netdly_trig > 0) {
        UtilSL.sleep(500);
      }

      // Connect to remote SeedLink
      if (state.state == SLState.SL_DOWN && state.netdly_trig == 0 && !terminateFlag) {//DCK: do not reconnect if terminate in progress
        try {
          connect();
          state.state = SLState.SL_UP;
        } catch (SeedLinkException | IOException e) {
          sllog.log(true, 0, e.toString());
        }
        state.netto_trig = -1;
        state.netdly_trig = -1;
      }

      // Negotiate/configure the connection
      if (state.state == SLState.SL_UP) {

        int slconfret = 0;

        // Send query if a query is set,
        //   stream configuration will be done only after query is fully returned
        if (infoRequestString != null /*&& streams.size() < 1*/) {
          try {
            sendInfoRequest(infoRequestString, 1);
            state.query_mode = SLState.INFO_QUERY;
            state.expect_info = true;
          } catch (SeedLinkException e) {	// SeedLink version does not support INFO requests
            sllog.log(false, 1, Util.clear(tmpsb).append("[").append(sladdr).
                    append("] SeedLink version does not support INFO requests"));
            state.query_mode = SLState.NO_QUERY;
            state.expect_info = false;
          } catch (IOException ioe) {	// I/O error, assume link is down
            sllog.log(true, 0, Util.clear(tmpsb).append("[").append(sladdr).
                    append("] I/O error, reconnecting in ").append(netdly).append("s"));
            disconnect();
            state.state = SLState.SL_DOWN;
          }
          infoRequestString = null;
        } else if (!state.expect_info) {
          try {
            configLink();
            state.recptr = 0;	// initialize the data buffer pointers
            state.sendptr = 0;
            state.state = SLState.SL_DATA;
          } catch (SeedLinkException | IOException e) {
            sllog.log(true, 0, Util.clear(tmpsb).append("[").append(sladdr).append("] negotiation with remote SeedLink failed: ").append(e));
            e.printStackTrace();
            disconnect();
            state.state = SLState.SL_DOWN;  // AJL added
            state.netdly_trig = -1;
          }
          state.expect_info = false;
        }

      }

      // Process data in our buffer and then read incoming data
      if (state.state == SLState.SL_DATA || (state.expect_info && !(state.state == SLState.SL_DOWN))) {

        // AJL 20040610 serious BUG in slibslink ???  moved into while loop
        //boolean sendpacket = true;
        // Process data in buffer
        while (state.packetAvailable()) {

          boolean sendpacket = true;

          // Check for an INFO packet
          if (state.packetIsInfo()) {

            boolean terminator = (state.databuf[state.sendptr + SLPacket.SLHEADSIZE - 1] != '*');
            if (!state.expect_info) {
              sllog.log(true, 0, Util.clear(tmpsb).append("[").append(sladdr).append("] unexpected INFO packet received, skipping"));
            } else {
              if (terminator) {
                state.expect_info = false;
              }
              // Keep alive packets are not returned
              if (state.query_mode == SLState.KEEP_ALIVE_QUERY) {
                //sllog.log(false,-1,"conn got KEEP: > ");
                sendpacket = false;
                if (!terminator) {
                  sllog.log(true, 0, Util.clear(tmpsb).append("[").append(sladdr).append("] non-terminated keep-alive packet received!?!"));
                } else {
                  sllog.log(false, 2, Util.clear(tmpsb).append("[").append(sladdr).append("] keepalive packet received"));
                }
              } else {
                slpacket = state.getPacket(slpacket);    // DCK: get packet into pre allocated packet
                //System.out.println("conn got INFO: > " + slpacket.getSequenceNumber());
                // construct info String
                int type = slpacket.getType();
                if (type == SLPacket.TYPE_SLINF) {
                  for (int i = 0; i < slpacket.msrecord.length - 64; i++) {
                    infoStrBuf.append((char) slpacket.msrecord[i + 64]);
                  }
                  //infoStrBuf.append(new String(slpacket.msrecord, 64, slpacket.msrecord.length - 64));
                } else if (type == SLPacket.TYPE_SLINFT) {
                  for (int i = 0; i < slpacket.msrecord.length - 64; i++) {
                    infoStrBuf.append((char) slpacket.msrecord[i + 64]);
                  }
                  //infoStrBuf.append(new String(slpacket.msrecord, 64, slpacket.msrecord.length - 64));
                  infoString = createInfoString(infoStrBuf);
                  Util.clear(infoStrBuf);
                  //infoStrBuf = new StringBuffer();
                }
              }
            }
            if (state.query_mode != SLState.NO_QUERY) {
              state.query_mode = SLState.NO_QUERY;
            }

          } else {   // Get packet and update the stream chain entry if not an INFO packet

            try {
              slpacket = state.getPacket(slpacket);// DCK: get packet into pre allocated packet
              //System.out.println("conn got DATA: > " + slpacket.getSequenceNumber());
              updateStream(slpacket);
              if (statefile != null && (System.currentTimeMillis() - lastStateSave) > 120000) { // dCK: was 20000 ms
                lastStateSave = System.currentTimeMillis();
                saveState(statefile);
              }
            } catch (SeedLinkException sle) {
              sllog.log(true, 0, Util.clear(tmpsb).append("[").append(sladdr).
                      append("] bad packet: ").append(sle).append(" ").append(new String(slpacket.msrecord, 0, 20)));
              //sle.printStackTrace();
              if (!sle.toString().contains("type 2000")) {
                sendpacket = false; // the packet is broken
              } else {
                sllog.log(true, 0, " allow blockette 2000");
              }
            }
          }

          // Increment the send pointer
          state.incrementSendPointer();
          // After processing the packet buffer shift the data
          state.packDataBuffer();
          // Return packet
          if (sendpacket) {
            //System.out.println("conn sending:  > " + slpacket.getSequenceNumber());
            return (slpacket);
          }

        }

        // A trap door for terminating, all complete data packets from the buffer
        //   have been sent to the caller
        /* AJL 20040526
                           if (terminateFlag) {
                                  return(SLPacket.SLTERMINATE);
                          }
         */
        //we are terminating (abnormally!)
        if (terminateFlag) {
          return (doTerminate());
        }

        // AJL 20040609 moved above
        // After processing the packet buffer shift the data
        //state.packDataBuffer();
        // Catch cases where the data stream stopped
        try {
          if (state.isError()) {
            sllog.log(true, 0, Util.clear(tmpsb).append("[").append(sladdr).append("] SeedLink reported an error with the last command"));
            disconnect();
            return (SLPacket.SLERROR);
          }
        } catch (SeedLinkException sle) {
        } //not enough bytes to determine packet type
        try {
          if (state.isEnd()) {
            sllog.log(false, 1, Util.clear(tmpsb).append("[").append(sladdr).append("] end of buffer or selected time window"));
            disconnect();
            return (SLPacket.SLTERMINATE);
          }
        } catch (SeedLinkException sle) {
        } //not enough bytes to determine packet type

        // Check for more available data from the socket
        //byte[] bytesread = null;
        int nbytesread;
        try {
          if (bytesread.length < state.bytesRemaining()) {
            sllog.log(false, 0, Util.clear(tmpsb).append("Had to increase receive buffer size from ").
                    append(bytesread.length).append(" to ").append(state.bytesRemaining() * 2));
            new RuntimeException(tmpsb.toString()).printStackTrace();
            bytesread = new byte[state.bytesRemaining() * 2];
          }
          nbytesread = receiveData(bytesread, state.bytesRemaining(), sladdr);
          if (nbytesread > 0) {   // Data is here, process it
            state.appendBytes(bytesread, nbytesread);
            // Reset the timeout and keepalive timers
            state.netto_trig = -1;
            state.keepalive_trig = -1;
          } else {
            UtilSL.sleep(500);	// AJL added to prevent use of all CPU
          }
        } catch (IOException ioe) {          // read() failed
          sllog.log(true, 0, Util.clear(tmpsb).append("[").append(sladdr).
                  append("] socket read error: ").append(ioe).append(", reconnecting in ").append(netdly).append("s"));
          disconnect();
          state.state = SLState.SL_DOWN;
          state.netto_trig = -1;
          state.netdly_trig = -1;
        }

      }

      // Update timing variables when more than a 1/4 second has passed
      double current_time = UtilSL.getCurrentTime();

      if ((current_time - state.previous_time) >= 0.25) {

        state.previous_time = current_time;

        // Network timeout timing logic
        if (netto > 0) {
          if (state.netto_trig == -1) {  // reset timer
            state.netto_time = current_time;
            state.netto_trig = 0;
          } else if (state.netto_trig == 0 && (current_time - state.netto_time) > netto) {
            state.netto_trig = 1;
          }
        }

        // Keepalive/heartbeat interval timing logic
        if (keepalive > 0) {
          if (state.keepalive_trig == -1) {	// reset timer
            state.keepalive_time = current_time;
            state.keepalive_trig = 0;
          } else if (state.keepalive_trig == 0 && (current_time - state.keepalive_time) > keepalive) {
            state.keepalive_trig = 1;
          }
        }

        // Network delay timing logic
        if (netdly > 0) {
          if (state.netdly_trig == -1) {	// reset timer
            state.netdly_time = current_time;
            state.netdly_trig = 1;
          } else if (state.netdly_trig == 1 && (current_time - state.netdly_time) > netdly) {
            state.netdly_trig = 0;
          }
        }

      }

    }    // End of primary loop

  }   // End of sl_collect()

  /**
   *
   * Open a network socket connection to a SeedLink server. Expects sladdr to be
   * in 'host:port' format.
   *
   * @exception SeedLinkException on error or no response or bad response from
   * server.
   * @exception IOException if an I/O error occurs.
   *
   */
  public void connect() throws SeedLinkException, IOException {
    if (keepalive > 0 && keepalive*2 > netto) {
      sllog.log(false, 0, "keepalive="+keepalive+" netto="+netto+" incompatible. Adjust keepalive");
      keepalive = netto / 2;
    }
    try {
      String[] parts = sladdr.split(":");
      String host_name = parts[0].trim();
      int nport = Integer.parseInt(parts[1]);
      //String host_name = sladdr.substring(0, sladdr.indexOf(':'));
      //int nport = Integer.parseInt(sladdr.substring(sladdr.indexOf(':') + 1));

      // create and connect Socket
      //Socket sock = new Socket(host_name, nport);
      Socket sock = new Socket(); // DCK convert for possible binding.
      sllog.log(false, 0, Util.clear(tmpsb).append("[").append(sladdr).append("] connecting socket to ").
              append(host_name).append("/").append(nport));

      /*try {System.out.println("sock.getReceiveBufferSize: " + sock.getReceiveBufferSize());} catch (Exception e) {System.out.println(e);}
try {System.out.println("sock.getReuseAddress: " + sock.getReuseAddress());} catch (Exception e) {System.out.println(e);}
try {System.out.println("sock.getKeepAlive: " + sock.getKeepAlive());} catch (Exception e) {System.out.println(e);}
       */
      if (parts.length >= 3) {
        sllog.log(false, 0, Util.clear(tmpsb).append("[").append(sladdr).append("] binding to ").append(parts[2]));

        sock.bind(new InetSocketAddress(parts[2], 0));  // User specified a bind address so bind it to this local ip/ephemeral port
      }
      sock.setReceiveBufferSize(65536);
      sock.setReuseAddress(true);
      sock.setKeepAlive(true);

      sock.connect(new InetSocketAddress(host_name, nport));

      /*try {System.out.println("sock.getReceiveBufferSize: " + sock.getReceiveBufferSize());} catch (Exception e) {System.out.println(e);}
try {System.out.println("sock.getReuseAddress: " + sock.getReuseAddress());} catch (Exception e) {System.out.println(e);}
try {System.out.println("sock.getKeepAlive: " + sock.getKeepAlive());} catch (Exception e) {System.out.println(e);}
       */
      // Wait up to 10 seconds for the socket to be connected
      int timeout = 10;
      int i = 0;
      while (i++ < timeout && !sock.isConnected()) {
        UtilSL.sleep(1000);
      }
      if (!sock.isConnected()) {
        Util.clear(tmpsb).append("[").append(sladdr).append("] socket connect time-out (").append(timeout).append("s)");
        //sllog.log(true, 0,  message);
        throw (new SeedLinkException(tmpsb.toString()));
      }

      // socket connected
      sllog.log(false, 0, Util.clear(tmpsb).append("[").append(sladdr).append("] network socket opened sock=").append(sock.getInetAddress()));

      // Set the KeepAlive socket option, not really useful in this case
      sock.setKeepAlive(true);

      this.socket = sock;
      this.socketInputStream = socket.getInputStream();
      this.socketOutputStream = socket.getOutputStream();

    } catch (NumberFormatException | IOException | SeedLinkException e) {
      //e.printStackTrace();
      throw (new SeedLinkException(Util.clear(tmpsb).append("[").append(sladdr).append("] cannot connect to SeedLink server: ").append(e).toString()));
    }

    // Everything should be connected, say hello
    try {
      sayHello();
    } catch (SeedLinkException | IOException sle) {
      sllog.log(false, 0, Util.clear(tmpsb).append("[").append(sladdr).append("] sayHello() failed! e=").append(sle));
      try {
        socket.close();
        socket = null;
      } catch (IOException e1) {
      }
      throw sle;
    }
  }	// End of connect()

  /**
   *
   * Close the network socket associated with this connection.
   *
   */
  public void disconnect() {

    if (socket != null) {
      try {
        socket.close();
      } catch (IOException ioe) {
        sllog.log(true, 1, Util.clear(tmpsb).append("[").append(sladdr).append("] network socket close failed: ").append(ioe));
      }
      socket = null;
      sllog.log(false, 0, Util.clear(tmpsb).append("[").append(sladdr).append("] network socket closed"));
    }

    // make sure previous state is cleaned up
    state = new SLState();  // AJL added AJL 20040610  
  }

  /* End of sl_disconnect() */


  /**
   *
   * Closes this SeedLinkConnection by closing the network socket and saving the
   * state to the statefile, if it exists.
   *
   */
  public void close() {

    if (socket != null) {
      sllog.log(false, 0, Util.clear(tmpsb).append("[").append(sladdr).append("] closing SeedLinkConnection()"));
      disconnect();
    }
    if (statefile != null) {
      try {
        saveState(statefile);
        try (RandomAccessFile ttmp = new RandomAccessFile(statefile, "r")) {
          byte[] b = new byte[(int) ttmp.length()];
          ttmp.read(b);
          sllog.log(false, 0, Util.clear(tmpsb).append("Closing save ").append(statefile).append("\n").append(new String(b)));
        }
      } catch (SeedLinkException | IOException sle) {
        sllog.log(false, -1, sle.toString());
      }
    }
  }

  /**
   *
   * Send bytes to the server. This is only designed for small pieces of data,
   * specifically for when the server responses to commands. It then reads back
   * resplen worth of response bytes into the user array.
   *
   * @param sendbytes bytes to send.
   * @param code a string to include in error messages for identification.
   * @param resplen if > 0 then read up to resplen response bytes after sending.
   * @param bytesread user buffer to receive readback bytes
   *
   * @return the response bytes or null if no response requested.
   *
   * @exception SeedLinkException on error or no response or bad response from
   * server.
   * @exception IOException if an I/O error occurs.
   *
   */
  public int sendData(byte[] sendbytes, String code, int resplen, byte[] bytesread) throws SeedLinkException, IOException {

    try {
      socketOutputStream.write(sendbytes);
    } catch (IOException ioe) {
      throw (ioe);
    }

    if (resplen <= 0) {
      return 0;   	// no response requested, just return
    }
    // If requested, wait up to 30 seconds for a response
    //byte[] bytesread = null;
    int ackcnt = 0;			// counter for the read loop
    int ackpoll = 50;		        // poll at 0.05 seconds for reading
    int nbytesread = 0;
    int ackcntmax = 30000 / ackpoll; 	// 30 second wait
    while ((nbytesread = receiveData(bytesread, resplen, code)) == 0) {
      if (ackcnt > ackcntmax) {
        throw (new SeedLinkException("[" + code + "] no response from SeedLink server to '" + (new String(sendbytes)) + "'"));
      }
      UtilSL.sleep(ackpoll);
      ackcnt++;
    }
    if (nbytesread <= 0) {
      throw (new SeedLinkException("[" + code + "] bad response to '"
              + Arrays.toString(sendbytes) + "'"));
    }

    return nbytesread;
  }    // End of sendData()

  /**
   *
   * Read bytes from the server.
   *
   * @param bytes User array to put up to maxbytes in
   * @param maxbytes maximum number of bytes to read.
   * @param code a string to include in error messages for identification.
   *
   * @return 0 if no bytes read, -1 if EOF found, if greater than zero, the
   * number of bytes read
   *
   * @exception IOException if an I/O error occurs.
   *
   */
  public int receiveData(byte[] bytes, int maxbytes, String code) throws IOException {

    //byte[] bytes = new byte[maxbytes];  // This used to create a lot of arrays!
    // read up to maxbytes
    int nbytesread = 0;
    try {
      if (socketInputStream.available() > 0) {
        nbytesread = socketInputStream.read(bytes, 0, maxbytes);
      } else {
        return 0;    // No bytes available, return no bytes
      }
    } catch (IOException ioe) {
      throw (ioe);
    }

    // check for end or no bytes read
    if (nbytesread == -1) { // should indicate TCP FIN or EOF
      sllog.log(true, 1, Util.clear(tmpsb).append("[").append(code).append("] socket.read(): ").
              append(nbytesread).append(": TCP FIN or EOF received"));
      return (nbytesread);
    } else if (nbytesread == 0) {
      return nbytesread;      // No data received
    }

    // copy bytes to array of length exactly nbytesread
    //byte[] bytesread = new byte[nbytesread];
    //System.arraycopy(bytes, 0, bytesread, 0, nbytesread);
    return nbytesread;
  }    // End of receiveData()

  /**
   *
   * Send the HELLO command and attempt to parse the server version number from
   * the returned string. The server version is set to 0.0 if it can not be
   * parsed from the returned string.
   *
   * @exception SeedLinkException on error.
   * @exception IOException if an I/O error occurs.
   *
   */
  public void sayHello() throws SeedLinkException, IOException {

    /* Send HELLO */
    String sendStr = "HELLO";
    sllog.log(false, 2, Util.clear(tmpsb).append("[").append(sladdr).append("] sending: ").append(sendStr));
    byte[] bytes = (sendStr + "\r").getBytes();
    //byte[] bytesread = null;
    int nbytesread = sendData(bytes, sladdr, DFT_READBUF_SIZE, readback);

    // Parse the server ID and version from the returned string
    String servstr = null;
    try {
      servstr = new String(readback, 0, nbytesread);
      int vndx = servstr.indexOf(" v");
      if (vndx < 0) {
        server_id = servstr;
        server_version = 0.0f;
      } else {
        server_id = servstr.substring(0, vndx);
        String tmpstr = servstr.substring(vndx + 2);
        int endndx = tmpstr.indexOf(" ");
        server_version = Float.valueOf(tmpstr.substring(0, endndx));
      }
    } catch (NumberFormatException e) {
      throw (new SeedLinkException(Util.clear(tmpsb).append("[").append(sladdr).append("] bad server ID/version string: '").
              append(servstr).append("'").toString()));
    }

    // Check the response to HELLO
    if (server_id.equalsIgnoreCase("SEEDLINK")) {
      sllog.log(false, 1, Util.clear(tmpsb).append("[").append(sladdr).append("] connected to: '").append(servstr.substring(0, servstr.indexOf('\r'))).append("'"));
    } else {
      throw (new SeedLinkException(Util.clear(tmpsb).append("[").append(sladdr).append("] incorrect response to HELLO: '").append(servstr).append("'").toString()));
    }

  }

  /* End of sl_sayhello() */


  /**
   *
   * Add an INFO request to the SeedLink Connection Description.
   *
   * @param infoLevel the INFO level (one of: ID, STATIONS, STREAMS, GAPS,
   * CONNECTIONS, ALL)
   *
   * @exception SeedLinkException if an INFO request is already pending.
   *
   */
  public void requestInfo(String infoLevel) throws SeedLinkException {

    if (infoRequestString != null || state.expect_info) {
      throw (new SeedLinkException(Util.clear(tmpsb).append("[").append(sladdr).append("] cannot make INFO request, one is already pending").toString()));
    } else {
      infoRequestString = infoLevel;
    }
  }                               // End of requestInfo()

  /**
   *
   * Sends a request for the specified INFO level. The verbosity level can be
   * specified, allowing control of when the request should be logged.
   *
   * @param infoLevel the INFO level (one of: ID, STATIONS, STREAMS, GAPS,
   * CONNECTIONS, ALL).
   * @param verb_level Verbosity level to use on info request log
   *
   * @exception SeedLinkException on error.
   * @exception IOException if an I/O error occurs.
   *
   */
  public void sendInfoRequest(String infoLevel, int verb_level) throws SeedLinkException, IOException {

    if (checkVersion(2.92f) >= 0) {
      byte[] bytes = ("INFO " + infoLevel + "\r").getBytes();
      sllog.log(false, verb_level, Util.clear(tmpsb).append("[").append(sladdr).append("] sending: requesting INFO level ").append(infoLevel));
      sendData(bytes, sladdr, 0, readback);
    } else {
      throw (new SeedLinkException("[" + sladdr
              + "] detected SeedLink version (" + server_version + ") does not support INFO requests"));
    }

  }    // End of requestInfo()

  /**
   *
   * Checks server version number against a given specified value.
   *
   * @param version specified version value to test.
   *
   * @return 1 if version is greater than or equal to value specified, 0 if no
   * server version is known, -1 if version is less than value specified.
   *
   */
  public int checkVersion(float version) {

    if (server_version == 0.0f) {
      return 0;
    } else if (server_version >= version) {
      return 1;
    } else {
      return -1;
    }

  }    // End of checkVersion()

  /**
   *
   * Configure/negotiate data stream(s) with the remote SeedLink server.
   * Negotiation will be either uni- or multi-station depending on the value of
   * 'multistation' in this SeedLinkConnection.
   *
   * @exception SeedLinkException if multi-station and SeedLink version does not
   * support multi-station protocol.
   * @throws java.io.IOException
   *
   */
  public void configLink() throws SeedLinkException, IOException {

    if (multistation) {
      if (checkVersion(2.5f) >= 0) {
        negotiateMultiStation();
      } else {
        throw (new SeedLinkException(Util.clear(tmpsb).append("[").append(sladdr).
                append("] detected SeedLink version (").append(server_version).
                append(") does not support multi-station protocol").toString()));
      }
    } else {
      negotiateUniStation();
    }

  }    // End of configLink()

  /**
   *
   * Negotiate a SeedLink connection for a single station and issue the DATA
   * command. If selectors are defined, then the string is parsed on space and
   * each selector is sent. If 'seqnum' != -1 and the SLCD 'resume' flag is true
   * then data is requested starting at seqnum.
   *
   * @param curstream the description of the station to negotiate.
   *
   * @exception SeedLinkException on error.
   * @exception IOException if an I/O error occurs.
   *
   */
  public void negotiateStation(SLNetStation curstream) throws SeedLinkException, IOException {

    // Send the selector(s) and check the response(s)
    String[] selectors = curstream.getSelectors();

    int acceptsel = 0;		// Count of accepted selectors
    for (String selector : selectors) {
      if (selector.length() > SLNetStation.MAX_SELECTOR_SIZE) {
        sllog.log(false, 0, Util.clear(tmpsb).append("[").append(sladdr).append("] invalid selector: ").append(selector));
      } else {

        // Build SELECT command, send it and receive response
        String sendStr = "SELECT " + selector;
        sllog.log(false, 2, Util.clear(tmpsb).append("[").append(sladdr).append("] sending: ").append(sendStr));
        byte[] bytes = (sendStr + "\r").getBytes();
        //byte[] bytesread = null;
        int nbytesread = sendData(bytes, sladdr, DFT_READBUF_SIZE, readback);

        // Check response to SELECT
        String readStr = new String(readback, 0, nbytesread);
        switch (readStr) {
          case "OK\r\n":
            sllog.log(false, 2, Util.clear(tmpsb).append("[").append(sladdr).append("] response: selector ").append(selector).append(" is OK"));
            acceptsel++;
            break;
          case "ERROR\r\n":
            sllog.log(true, 0, Util.clear(tmpsb).append("[").append(sladdr).
                    append("] response: selector ").append(selector).append(" not accepted"));
            break;
          default:
            throw (new SeedLinkException(Util.clear(tmpsb).append("[").append(sladdr).append("] response: invalid response to SELECT command: ").append(readStr).toString()));
        }
      }
    } // End of selector processing

    // Fail if none of the given selectors were accepted
    if (acceptsel < 1) {
      throw (new SeedLinkException(Util.clear(tmpsb).append("[").append(sladdr).append("] response: no data stream selector(s) accepted").toString()));
    } else {
      sllog.log(false, 2, Util.clear(tmpsb).append("[").append(sladdr).append("] response: ").append(acceptsel).append(" selector(s) accepted"));
    }

    // Issue the DATA, FETCH or TIME action commands.  A specified start (and
    //   optionally, stop time) takes precedence over the resumption from any
    //   previous sequence number.
    String sendStr = null;

    if (curstream.seqnum != -1 && resume) {
      // resuming

      if (dialup) {
        sendStr = "FETCH";
      } else {
        sendStr = "DATA";
      }

      /* Append the last packet time if the feature is enabled and server is >= 2.93 */
      if (lastpkttime && checkVersion(2.93f) >= 0 && curstream.ltime > 0) {
        // Increment sequence number by 1
        sendStr += " " + Integer.toHexString(curstream.seqnum + 1) + " " + curstream.getSLTimeStamp();
        sllog.log(false, 1, Util.clear(tmpsb).append("[").append(sladdr).
                append("] requesting resume data from ").append(curstream.getNetStation()).
                append(" 0x").append(Integer.toHexString(curstream.seqnum + 1).toUpperCase()).
                append(" (decimal: ").append(curstream.seqnum).append(1).append(") at ").append(curstream.getSLTimeStamp()));
      } else {
        // Increment sequence number by 1
        sendStr += " " + Integer.toHexString(curstream.seqnum + 1);
        sllog.log(false, 1, Util.clear(tmpsb).append("[").append(sladdr).
                append("] requesting resume data from ").append(curstream.getNetStation()).
                append(" 0x").append(Integer.toHexString(curstream.seqnum + 1).toUpperCase()).
                append(" (decimal: ").append(curstream.seqnum).append(1).append(")"));
      }

    } else if (begin_time != null) {
      // begin time specified (should only be at initial startup)

      if (checkVersion(2.92f) >= 0) {
        if (end_time == null) {
          sendStr = "TIME " + begin_time;
        } else {
          sendStr = "TIME " + begin_time + " " + end_time;
        }
        sllog.log(false, 1, Util.clear(tmpsb).append("[").append(sladdr).append("] requesting specified time window"));
      } else {
        throw (new SeedLinkException(Util.clear(tmpsb).append("[").append(sladdr).
                append("] detected SeedLink version (").append(server_version).
                append(") does not support TIME windows").toString()));
      }

    } else {
      // default

      if (dialup) {
        sendStr = "FETCH";
      } else {
        sendStr = "DATA";
      }
      sllog.log(false, 1, Util.clear(tmpsb).append("[").append(sladdr).
              append("] requesting next available data from ").append(curstream.getNetStation()));
    }

    // Send action command and receive response
    sllog.log(false, 2, Util.clear(tmpsb).append("[").append(sladdr).append("] sending: ").append(sendStr));
    byte[] bytes = (sendStr + "\r").getBytes();
    //byte[] bytesread = null;
    int nbytesread = sendData(bytes, sladdr, DFT_READBUF_SIZE, readback);

    // Check response to DATA/FETCH/TIME
    String readStr = new String(readback, 0, nbytesread);
    switch (readStr) {
      case "OK\r\n":
        sllog.log(false, 2, Util.clear(tmpsb).append("[").append(sladdr).append("] response: DATA/FETCH/TIME command is OK"));
        acceptsel++;
        break;
      case "ERROR\r\n":
        throw (new SeedLinkException(Util.clear(tmpsb).append("[").append(sladdr).append("] response: DATA/FETCH/TIME command is not accepted").toString()));
      default:
        throw (new SeedLinkException(Util.clear(tmpsb).append("[").append(sladdr).append("] response: invalid response to DATA/FETCH/TIME command: ").append(readStr).toString()));
    }

  }    // End of negotiateStation()

  /**
   *
   * Negotiate a SeedLink connection in uni-station mode and issue the DATA
   * command. This is compatible with SeedLink Protocol version 2 or greater. If
   * selectors are defined, then the string is parsed on space and each selector
   * is sent. If 'seqnum' != -1 and the SLCD 'resume' flag is true then data is
   * requested starting at seqnum.
   *
   * @exception SeedLinkException on error.
   * @exception IOException if an I/O error occurs.
   *
   */
  public void negotiateUniStation() throws SeedLinkException, IOException {

    // get stream (should be only stream present)
    SLNetStation curstream = null;
    try {
      curstream = (SLNetStation) streams.get(0);
    } catch (Exception e) {
      throw (new SeedLinkException(Util.clear(tmpsb).append("[").append(sladdr).
              append("] cannot negotiate uni-station, stream list does not have exactly one element").toString()));
    }
    //if (!(curstream.net.equals(UNINETWORK) &&  curstream.station.equals(UNISTATION)))
    if (!Util.stringBuilderEqual(curstream.getNetStation(), UNINETSTATION)) {
      throw (new SeedLinkException(Util.clear(tmpsb).append("[").append(sladdr).
              append("] cannot negotiate uni-station, mode not configured!").toString()));
    }

    // negotiate the station connection
    negotiateStation(curstream);
  }    // End of negotiateUniStation()

  /**
   *
   * Negotiate a SeedLink connection using multi-station mode and issue the END
   * action command. This is compatible with SeedLink Protocol version 3,
   * multi-station mode. If selectors are defined, then the string is parsed on
   * space and each selector is sent. If 'seqnum' != -1 and the SLCD 'resume'
   * flag is true then data is requested starting at seqnum.
   *
   * @exception SeedLinkException on error.
   * @exception IOException if an I/O error occurs.
   *
   */
  public void negotiateMultiStation() throws SeedLinkException, IOException {

    int acceptsta = 0;
    /* Count of accepted stations */


    if (streams.size() < 1) {
      throw (new SeedLinkException(Util.clear(tmpsb).append("[").append(sladdr).
              append("] cannot negotiate multi-station, stream list is empty").toString()));
    }

    sllog.log(false, 0, Util.clear(tmpsb).append(Util.asctime()).
            append(" Start negotiateMultiStation() for ").append(streams.size()).append(" stations ").append(sladdr));

    // Loop through the stream chain
    //for (int i = 0; i < streams.size(); i++ ) {
    TLongObjectIterator<SLNetStation> itr = streams.iterator();
    while (itr.hasNext()) {
      // get stream (should be only stream present)
      //SLNetStation curstream = (SLNetStation) streams.get(i);
      itr.advance();
      SLNetStation curstream = itr.value();

      // A ring identifier
      //String slring = curstream.net + curstream.station;
      // Build STATION command, send it and receive response
      //String sendStr = "STATION  " + curstream.station + " " + curstream.net;
      String sendStr = "STATION " + curstream.getNetStation().toString().substring(2).trim()
              + " " + curstream.getNetStation().toString().substring(0, 2);
      sllog.log(false, 2, Util.clear(tmpsb).append("[").append(sladdr).append("] sending: ").append(sendStr));
      byte[] bytes = (sendStr + "\r").getBytes();
      //byte[] bytesread = null;
      int nbytesread = sendData(bytes, sladdr, DFT_READBUF_SIZE, readback);

      // Check response to SELECT
      String readStr = new String(readback, 0, nbytesread);
      switch (readStr) {
        case "OK\r\n":
          sllog.log(false, 2, Util.clear(tmpsb).append("[").append(sladdr).append("] response: station is OK (selected)"));
          break;
        case "ERROR\r\n":
          sllog.log(true, 0, Util.clear(tmpsb).append("[").append(sladdr).append("] response: station not accepted, skipping"));
          continue;
        default:
          throw (new SeedLinkException(Util.clear(tmpsb).append("[").append(sladdr).
                  append("] response: invalid response to STATION command: ").append(readStr).toString()));
      }

      // negotiate the station connection
      try {
        negotiateStation(curstream);
      } catch (SeedLinkException | IOException e) {
        sllog.log(true, 0, e.toString());
        continue;
      }

      acceptsta++;
      if (acceptsta % 20 == 0) {
        sllog.log(false, 0, Util.clear(tmpsb).append(acceptsta).append(" of ").
                append(streams.size()).append(" negotiated and accepted so far"));
      }

    }	// End of stream and selector config (end of stream chain)

    // Fail if no stations were accepted
    if (acceptsta < 1) {
      throw (new SeedLinkException("[" + sladdr + "] no stations accepted"));
    } else {
      sllog.log(false, 1, Util.clear(tmpsb).append("[").append(sladdr).append("] ").append(acceptsta).append(" station(s) accepted"));
    }

    // Issue END action command
    String sendStr = "END";
    sllog.log(false, 2, Util.clear(tmpsb).append("[").append(sladdr).append("] sending: ").append(sendStr));
    byte[] bytes = (sendStr + "\r").getBytes();
    sendData(bytes, sladdr, 0, readback);
    sllog.log(false, 0, Util.clear(tmpsb).append(Util.asctime()).append(" Finish negotiateMultiStation()"));

  }    // End of configlink_multi()

  /**
   *
   * Update the appropriate stream chain entry given a Mini-SEED record.
   *
   * @param slpacket the packet conaining a Mini-SEED record.
   * 
   * @exception SeedLinkException on error.
   *
   */
  public void updateStream(SLPacket slpacket) throws SeedLinkException {

    int seqnum = slpacket.getSequenceNumber();
    if (seqnum == -1) {
      throw (new SeedLinkException(Util.clear(tmpsb).append("[").append(sladdr).append("] could not determine sequence number").toString()));
    }

    MiniSeed ms = slpacket.getMiniSeed();
    String net = null;
    String station = null;
    if (ms != null) {
      Util.clear(netstat);
      for (int i = 0; i < 7; i++) {
        netstat.append((char) ms.getSeedNameSB().charAt(i));
      }
    } else {
      sllog.log(false, 0, "*** Non miniseed reference in update stream>");
    }
    /*else {    // This should be obsolete - only using MiniSEED now.
        Blockette blockette = slpacket.getBlockette();
        
        if (blockette.getType() != 999)
          throw(new SeedLinkException("[" + sladdr + "] blockette not 999 (Fixed Section Data Header)"));
        
        // read some blockette fields
        String tmp = "Update "+slpacket.getMiniSeed();

        try {
          station = (String) blockette.getFieldVal(4);
          net = (String) blockette.getFieldVal(7);
          btime = (Btime) blockette.getFieldVal(8);
        } catch (SeedException se) {
            throw(new SeedLinkException("[" + sladdr + "] blockette read error: " + se));
        }
      }*/

    // For uni-station mode
    if (!multistation) {
      // get stream (should be only stream present)
      SLNetStation curstream = null;
      try {
        curstream = (SLNetStation) streams.get(1);
      } catch (Exception e) {
        throw (new SeedLinkException(Util.clear(tmpsb).append("[").append(sladdr).
                append("] cannot update uni-station stream, stream list does not have exactly one element").toString()));
      }
      curstream.seqnum = seqnum;
      if (ms != null) {
        curstream.ltime = ms.getTimeInMillis();
      } else {
        sllog.log(false, 0, "BTime reference in SeedLinkConnection.updateStream!");
        //curstream.ltime = btime.getEpochTime();
      }
      return;
    }

    // For multi-station mode, search the stream chain
    // Search for a matching net/station in the stream chain
    SLNetStation stream = streams.get(Util.getHashSB(netstat));
    /*SLNetStation stream = null;
      for (SLNetStation stream1 : streams) {
        stream = (SLNetStation) stream1;
        if (stream.net.equals(net) && stream.station.equals(station))
          break;	// found
        stream = null;
      }*/
    // update net/station entry in the stream chain
    //sllog.log(true, 5, "update stread "+stream+" to  sq="+seqnum+" btime="+btime);
    if (stream != null) {
      stream.seqnum = seqnum;
      if (ms != null) {
        stream.ltime = ms.getTimeInMillis();
      } else {
        stream.ltime = System.currentTimeMillis();
      }
    } else {
      // If we got here no match was found
      sllog.log(true, 0, Util.clear(tmpsb).append("unexpected data received: ").append(net).append(" ").append(station).append(" ").append(ms));
    }

  }    // End of updateStream()

}
