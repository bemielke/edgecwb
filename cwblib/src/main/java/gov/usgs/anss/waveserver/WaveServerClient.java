/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.waveserver;

import gov.usgs.anss.edgeoutput.TraceBuf;
import gov.usgs.anss.edge.TimeSeriesBlock;
import gov.usgs.anss.ew.EWMessage;
import gov.usgs.anss.util.Util;
import gov.usgs.anss.util.FUtil;
import gov.usgs.anss.utility.MakeRawToMiniseed;
import gov.usgs.anss.net.TimeoutSocketThread;
import gov.usgs.anss.edgeoutput.TraceBufPool;

import java.io.IOException;
import java.net.Socket;
import java.net.UnknownHostException;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.sql.Timestamp;
//import java.text.DecimalFormat;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.ArrayList;
import java.util.TreeMap;
import java.util.zip.DataFormatException;
//import java.util.zip.Deflater;

/**
 * This class is a client to WaveServer for most of the common commands. It maintains a structure
 * will MenuItem objects for each channel in the MENU and can retrieve the information from the last
 * MENU by calling getMenuChannel().
 *
 * <PRE>
 * Supported WaveServer commands :
 * getMENU()     populates the menu structure with MenuItems for each channel
 * getMENUSCNL() ask server for menu from one channel.
 * getSCNLRAW()  trace bufs in an ArrayList
 * getSCNLRAWAsTimeSeriesBlocks()  trace bufs in an ArrayList of TimeSeriesBlocks
 * getSCNL()     Get data returned as ASCII, but converted to ints by this routine.
 * Winston Command additions:
 * getSCNLHELIRAW() Returned mins an maxes filtered and sampled at 1 Hz suitable for Swarm Helicorders
 * getWAVERAW()   Get data in raw wave format (basically a single array of data)
 * getCHANNELS()  Get channel related metadata
 * getMETADATAINSTRUMENT Get the instrument metadata - requires server have connection to a MetaDataServer
 * getMETADATACHANNEL() Get the channel metadata ala Winston
 * getSTATUS()    Return a status stream from the server
 * getVERSION()   REturn the version string.
 *
 * </PRE> For all trace commands (getSCNL(), getSCNLRAW, getWAVERAW(), the returned rate, start
 * time, gap string (like 'FG', 'FL','FR' or 'F') are available by methods after each call and
 * before the next call via getGapString().
 *
 * <PRE>
 * Testing a single command can be done with :
 * java -cp $PATH/QueryMom.jar gov.usgs.anss.waveserver.WaveServerClient ARGS
 * -c MENU|MENUSCNL|GETSCNL|GETSCNLRAW|GETSCNLHELIRAW|GETWAVERAW|GMDC|GMDI|STATUS|GETCHANNELS -s NSCL -b date -e date -f fill -h HOST -p port(2060) -pin PIN -r requestid -ms
 *    GETSCNL requires -s -b -e
 *    MENUSCNL requires -s
 *    GETSCNLRAW requires -s -b -e
 *    GETSCNLRAW with -ms will create MiniSeed files from the resulting TraceBufs
 *    GMDC is GETMETADATAA: CHANNEL and GMDI is GETMETADATA: INSTRUMENT
 *    NSCL must be in exact NNSSSSSCCCLL form (spaces count!).  Spaces or hyphens can be used
 *    -h  IP.OF.Server  Set the host (def=localhost)
 *    -p  port          Sets the port (def=2060
 *    -b or -e Date     beginning and ending data in yyyy/mm/dd hh:mm:ss.mmm format (slashes or dashes in date supported - dash to separate time from date allowed)
 *    -test omit the -c but include -s -b and -e will run ALL of the commands and compare the waveforms returned by GETSCNL,GETSCNLRAW, and GETWAVERAW
 *    -ms LL with GETSCNLRAW will convert the returned trace bufs to MiniSEED with location code LL - useful for debugging
 *    -fill FILL changes the fill value from 2147000000
 *    -r RID changes the request ID from GS to RID
 *   -pin PID sets the PIN replacing the default 0000
 * </PRE>
 *
 * There is the argument -test which for the given host and port will run all of the command and do
 * some testing of the results - the result of GETSCNL, GETSCNLRAW, and
 *
 * @author davidketchum
 */
public class WaveServerClient {

  /**
   * The weird fiducial time for the Winston Wave server times - 2001-01-01 12:00:00 UTC in millis
   */
  public static long year2000MSWinston;
  /**
   * The default standard fill value
   */
  public static int FILL_VALUE = 2147000000;
  private final GregorianCalendar g = new GregorianCalendar(2000, 0, 1);
  private final TreeMap<String, MenuItem> menu = new TreeMap<String, MenuItem>();
  private byte[] b = new byte[40000];
  private Socket s;
  private final GregorianCalendar gc = new GregorianCalendar();
  private String host = "137.227.224.97";
  private String fillString = "2147000000";
  private int port = 2060;
  private boolean dbg;
  private String currentCommand;
  private long startreq;
  private final TraceBufPool tbp = new TraceBufPool(1000, TraceBuf.UDP_SIZ, null);
  private TimeoutSocketThread timeout;
  // Response stuff
  private String ans;      // the full response line throught the newline
  private String[] parts;
  private String returnedRequestID;
  private String returnedPin;
  private String returnedSeedname;
  private String returnedF;
  private String returnedType;
  private double returnedRate;
  private double returnedStart;
  private double returnedEnd;
  private long timeoutMS = 60000;
  private int returnedLen;
  private int nfill;
  private int newl;
  private ByteBuffer bb;
  private final StringBuilder sb = new StringBuilder(1000); // Used by readNumberLines to assemble the longer message 

  public boolean timedOut() {
    if (timeout == null) {
      return false;
    }
    return timeout.hasSentInterrupt();
  }

  public void setTimeoutInterval(long ms) {
    if (timeout != null) {
      timeout.setInterval(ms);
    }
    timeoutMS = ms;
  }

  public void setFillString(String s) {
    fillString = s;
  }

  /**
   * For the last command this is the ascii string returned either by a response to the first new
   * line, or for some commands (GMDI, GMDC, GETCHANNELS)the entire response including multiple
   * newlines as a string. AFter a MENU this contains the entire menu response but no newlines are
   * present.
   *
   * @return The response through the first newline or the entire ASCII response with all newlines
   * depending on command
   */
  private String getResponseString() {
    return ans;
  }

  /**
   * For the last command this is the requestID
   *
   * @return The users requestID
   */
  public String getRequestID() {
    return returnedRequestID;
  }

  /**
   * For the last command (GETSCNL, GETWAVERAW and GETSCNLRAW only) this is the returned rate - some
   * commands do not return a rate
   *
   * @return The The rate in Hz
   */
  public double getRate() {
    return returnedRate;
  }

  /**
   * For the last command (GETSCNL, GETWAVERAW and GETSCNLRAW only) this is the returned PIN - some
   * commands do not return a PIN
   *
   * @return The The rate in Hz
   */
  public String getPIN() {
    return returnedPin;
  }

  /**
   * For the last command (GETSCNL, GETWAVERAW and GETSCNLRAW only) this is the returned start time
   * - some commands do not return a start time
   *
   * @return The time since 1970 as decimal seconds (per normal wave servers, NOT the time for
   * Winston WaveServers)
   */
  public double getStart() {
    return returnedStart;
  }

  /**
   * For the last command (GETSCNL, GETWAVERAW and GETSCNLRAW only) this is the returned start time
   * in millis - some commands do not return a start time
   *
   * @return The time since 1970 as milliseconds (per normal wave servers, NOT the time for Winston
   * WaveServers)
   */
  public long getStartInMillis() {
    return (long) (returnedStart * 1000. + 0.01);
  }

  /**
   * For the last command length of the returned data in bytes - not normally useful to users
   *
   * @return The length of the returned data in bytes
   */
  public int getLength() {
    return returnedLen;
  }

  /* The gap string returned by last command (GETSCNL, GETSCNLRAW) 
   * @return The gap string normally "F", "FG","FL" or "FR"
   */
  public String getGapString() {
    return returnedF;
  }

  /**
   * The seedname returned, normally the same as the one put in!
   *
   * @return Seedname as a NNSSSSSCCCLL with spaces to fill out fields - no delimiters
   */
  public String getSeedname() {
    return returnedSeedname;
  }

  /**
   * The returned data type - normally only GETSCNLRAW returns this
   *
   * @return The data type 's4' is the normal, but 'i4', 's2', and 'i2' are possible
   */
  public String getType() {
    return returnedType;
  }

  /**
   * for GETSCNL the number of fill values in the buffer
   *
   * @return The number of fill values
   */
  public int getNFill() {
    return nfill;
  }

  /**
   * if true, turn on lots of output - this is used the main() to use this as a quick test of
   * WaveServer compatability
   *
   * @param t If true, turn on loggin
   */
  public void setDebug(boolean t) {
    dbg = t;
  }

  /**
   * Make a new WaveServerClient object for the given IP and port
   *
   * @param host IP address or dns name of server
   * @param port Port to use (def=2060)
   */
  public WaveServerClient(String host, int port) {
    if (host != null) {
      this.host = host;
    }
    if (port > 0) {
      this.port = port;
    }
    Util.setModeGMT();
    g.set(2000, 00, 1, 0, 0, 0);
    g.setTimeInMillis(g.getTimeInMillis() / 86400000L * 86400000L + 86400000L / 2);  //this is the weird time used by Winston for 2000
    year2000MSWinston = g.getTimeInMillis();
    bb = ByteBuffer.wrap(b);
  }

  /**
   * Free memory of trace bufs returned by GETSNCLRAW(). This should be called after the tracebufs
   * from a GETSCNLRAW() are no longer needed. These trace bufs are maintained in a pool for reuse
   * rather than letting them be reaped constantly by the garbage collector. Failure to call this
   * will eventually create so many TraceBufs in the pool that heap space will run out!
   *
   * @param tbs The ArrayList of TimeSeriesBlocks with the returned data to free
   */
  public void freeMemory(ArrayList<TraceBuf> tbs) {
    for (int i = tbs.size() - 1; i >= 0; i--) {
      if (tbs.get(i) instanceof TraceBuf) {
        tbp.free((TraceBuf) tbs.get(i));
      }
    }
  }

  /**
   * Free memory of trace bufs returned by GETSNCLRAW(). This should be called after the tracebufs
   * from a GETSCNLRAW() are no longer needed. These trace bufs are maintained in a pool for reuse
   * rather than letting them be reaped constantly by the garbage collector. Failure to call this
   * will eventually create so many TraceBufs in the pool that heap space will run out!
   *
   * @param tbs The ArrayList of TimeSeriesBlocks with the returned data to free
   */
  public void freeMemoryTSB(ArrayList<TimeSeriesBlock> tbs) {
    for (int i = tbs.size() - 1; i >= 0; i--) {
      if (tbs.get(i) instanceof TraceBuf) {
        tbp.free((TraceBuf) tbs.get(i));
      }
    }
  }

  /**
   * Open the socket to the server
   */
  private void openSocket() {
    if (s != null) {
      if (!s.isClosed()) {
        closeSocket();
      }
    }
    s = null;
    startreq = System.currentTimeMillis();

    while (s == null) {
      try {
        if (dbg) {
          Util.prta("open " + host + ":" + port);
        }
        s = new Socket(host, port);
        if (timeout == null) {
          timeout = new TimeoutSocketThread("WSC", s, (int) timeoutMS);
        }
        timeout.setSocket(s);
        timeout.enable(true);
        s.setSoTimeout(120000);
        break;
      } catch (IOException e) {
        Util.prta("Could not connect to " + host + ":" + port + " e=" + e);
        Util.sleep(10000);
      }
    }
  }

  /* Close up the socket
   *
   */
  public void closeSocket() {
    try {
      if (s != null) {
        if (!s.isClosed()) {
          s.close();
        }
      }
    } catch (IOException e) {
      Util.prt("Close threw IO error e=" + e);
    }
    timeout.enable(false);
  }

  /**
   * Commands like MENU, MENUSCNL, GMDC, GMDI, STATUS version only require simple text
   *
   * @param command One of the simple commands
   * @param requestid The request ID
   * @param seedname The NNSSSSSCCCLL (some command do not use this)
   * @throws IOException
   */
  private void doTextCommand(String command, String requestid, String seedname) throws IOException {
    String line;
    if (command.equals("MENU")) {
      line = "MENU: " + requestid.trim() + " SCNL\n";
    } else if (command.equals("MENUSCNL")) {
      line = "MENUSCNL: " + requestid.trim() + " " + seedname.substring(2, 7).trim()
              + " " + seedname.substring(7, 10).trim() + " " + seedname.substring(0, 2).trim() + " "
              + (seedname.length() >= 12 ? seedname.substring(10, 12).replaceAll(" ", "-").trim() : "--") + "\n";
    } else if (command.equals("GMDC")) {
      line = "GETMETADATA: " + requestid.trim() + " CHANNEL " + "\n";
    } else if (command.equals("GMDI")) {
      line = "GETMETADATA: " + requestid.trim() + " INSTRUMENT " + "\n";
    } else if (command.equals("STATUS")) {
      line = command + ": " + requestid.trim() + " 0 \n";
    } else if (command.equals("GETCHANNELS") && seedname.equals("METADATA")) {
      line = command + ": " + requestid.trim() + " METADATA\n";
    } else {
      line = command + ": " + requestid.trim() + "\n";
    }
    openSocket();         // Open socket to server
    sendCommand(line);    // Send this command line
    getResponse();        // Read back data until first newline
    if (command.equals("GMDC") || command.equals("GMDI") || command.equals("GETCHANNELS")) {
      readNumberLines();
    }
    closeSocket();
  }

  private void readNumberLines() throws IOException {
    String[] p = ans.trim().split(" ");
    int nlines = Integer.parseInt(p[1]);
    Util.clear(sb);
    Util.prta("read number of lines=" + nlines);
    sb.append(ans).append("\n");
    for (int i = 0; i < nlines; i++) {
      getResponse();
      sb.append(ans).append("\n");
    }
    ans = sb.toString();
    Util.clear(sb);
  }

  private void sendCommand(String line) throws IOException {
    if (dbg) {
      Util.prta("Send command:" + line.replaceAll("\n", "|"));
    }
    s.getOutputStream().write(line.getBytes(), 0, line.length());
  }

  /**
   * Get the response from the server through the first newline.
   *
   * @return The length of the string to the newline
   * @throws IOException
   */
  public int getResponse() throws IOException {
    int chr;
    int offset = 0;
    // Read in until first newline is read
    while ((chr = s.getInputStream().read()) >= 0) {
      if (offset >= b.length - 1) {
        byte[] temp = new byte[offset * 2];
        System.arraycopy(b, 0, temp, 0, offset);
        b = temp;
        timeout.resetTimeout();
      }
      b[offset++] = (byte) chr;
      if (offset % 10000 == 1 && dbg && offset > 10000) {
        Util.prta("GetResponse():offset=" + offset);
      }
      if (chr == '\n') {
        break;
      }
    }
    newl = offset - 1;      // Set newline spot in array 
    ans = new String(b, 0, Math.max(newl, 0));
    timeout.enable(false);    // do not timeout now
    //if(dbg)
    //  Util.prt("ans="+ans);
    return newl;
  }

  /**
   * Trying to isolate the response for time series data to one place. The responses seem
   * inconsistent!
   *
   * @throws IOException if number of parameters returned is short, this normally is a socket closed
   * or broken.
   */
  private void parseResponse() throws IOException {
    returnedRequestID = null;
    returnedPin = null;
    returnedSeedname = null;
    returnedF = null;
    returnedType = null;
    returnedRate = 0.;
    returnedStart = 0.;
    returnedEnd = 0.;
    returnedLen = 0;
    parts = ans.split("\\s");
    if (parts[0].equals("ERROR") && parts[1].equals("REQUEST")) {
      Util.prt("Return ERROR REQUEST - aborting");
      Util.sleep(500);
      int navail = s.getInputStream().available();
      if (navail > 0) {
        int n = s.getInputStream().read(b, 0, navail);
        String err = new String(b, 0, n);
        Util.prt("Additional input: " + err);
      }
    } else {
      switch (currentCommand) {
        case "GETSCNLRAW":
          returnedRequestID = parts[0];
          if (parts.length < 7) {
            throw new IOException("GETSCNLRAW did not return enough parameters.  Likely a socket problem");
          }
          returnedPin = parts[1];
          returnedSeedname = "";
          returnedSeedname = Util.makeSeedname(parts[4], parts[2], parts[3], parts[5]);
          if (parts.length >= 7) {
            returnedF = parts[6];
          }
          if (parts.length >= 8) {
            returnedType = parts[7];
          }
          if (parts.length >= 9) {
            returnedStart = Double.parseDouble(parts[8]);
          }
          if (parts.length >= 10) {
            returnedRate = Double.parseDouble(parts[9]);
          }
          if (parts.length >= 11) {
            returnedLen = Integer.parseInt(parts[10]);
          }
          if (dbg) {
            Util.prt("req=" + parts[0] + " pin=" + parts[1] + " scnl=" + parts[2] + " " + parts[3] + " " + parts[4] + " " + parts[5] + " f=" + parts[6]
                    + (parts.length >= 8 ? " type=" + parts[7] : "")
                    + (parts.length >= 9 ? " start=" + parts[8] : "")
                    + (parts.length >= 10 ? " rate=" + parts[9] : "")
                    + (parts.length >= 11 ? " len=" + parts[10] : ""));
          }
          break;
        case "GETSCNRAW":
          returnedRequestID = parts[0];
          if (parts.length < 7) {
            throw new IOException("GETSCNLRAW did not return enough parameters.  Likely a socket problem");
          }
          returnedPin = parts[1];
          returnedSeedname = "";
          returnedSeedname = Util.makeSeedname(parts[4], parts[2], parts[3], "  ");
          if (parts.length >= 6) {
            returnedF = parts[5];
          }
          if (parts.length >= 7) {
            returnedType = parts[6];
          }
          if (parts.length >= 8) {
            returnedStart = Double.parseDouble(parts[7]);
          }
          if (parts.length >= 9) {
            returnedRate = Double.parseDouble(parts[8]);
          }
          if (parts.length >= 10) {
            returnedLen = Integer.parseInt(parts[9]);
          }
          if (dbg) {
            Util.prt("req=" + parts[0] + " pin=" + parts[1] + " scnl=" + parts[2] + " " + parts[3] + " " + parts[4] + " " + parts[5] + " f=" + parts[6]
                    + (parts.length >= 8 ? " type=" + parts[7] : "")
                    + (parts.length >= 9 ? " start=" + parts[8] : "")
                    + (parts.length >= 10 ? " rate=" + parts[9] : "")
                    + (parts.length >= 11 ? " len=" + parts[10] : ""));
          }
          break;
        case "GETSCNL":
          returnedRequestID = parts[0];
          returnedPin = parts[1];
          returnedSeedname = Util.rightPad(parts[4], 2).substring(0, 2) + Util.rightPad(parts[2], 5) + Util.rightPad(parts[3], 3)
                  + Util.rightPad(parts[5].replaceAll("-", " "), 2);
          if (parts.length < 7) {
            throw new IOException("GETSCNL did not return enough parameters.  Likely a socket problem");
          }
          returnedF = parts[6];
          if (parts.length >= 8) {
            returnedType = parts[7];
          }
          if (parts.length >= 9) {
            returnedStart = Double.parseDouble(parts[8]);
          }
          if (parts.length >= 10) {
            returnedRate = Double.parseDouble(parts[9]);
          }
          if (dbg) {
            Util.prt("sz=" + parts.length + " req=" + parts[0] + " pin=" + parts[1] + " scnl=" + parts[2] + " " + parts[3] + " " + parts[4] + " " + parts[5] + " f=" + parts[6]
                    + (parts.length >= 8 ? " type=" + parts[7] : "")
                    + (parts.length >= 9 ? " start/end=" + parts[8] : "") + (parts.length >= 10 ? " rate=" + parts[9] : ""));
          }
          break;
      }
      if (returnedF.contains("FR") || returnedF.contains("FG") || returnedF.contains("FL") || returnedF.contains("FO") || returnedF.contains("FU")) {
        if (returnedF.contains("FR") || returnedF.contains("FL")) {
          gc.setTimeInMillis((long) (returnedStart * 1000.));
          if (dbg) {
            Util.prt("Time for FL (endtime < begin) or FR (beg time > end)" + Util.ascdate(gc) + " " + Util.asctime2(gc) + " ");
          }
        }
        if (dbg) {
          Util.prt("Gap found skip data");
        }
      } else {
        gc.setTimeInMillis((long) (returnedStart * 1000.));
        if (dbg) {
          Util.prt("Data returned from " + Util.ascdatetime2(gc) + " len=" + returnedLen);
        }
      }
    }
  }

  /**
   * Read the bytes from the socket - the length came from the response line like for getSCNLRAW
   *
   * @throws IOException
   */
  private void readLen() throws IOException {
    int len = returnedLen;
    if (b.length < returnedLen) {
      b = new byte[returnedLen * 2];
      bb = ByteBuffer.wrap(b);
    }
    int offset = 0;
    int cnt = 0;
    int nchar;
    while (cnt < len) {
      nchar = s.getInputStream().read(b, offset, len - offset);
      if (len < 0) {
        break;
      }
      offset += nchar;
      cnt += nchar;
    }
    long endreq = System.currentTimeMillis();
    if (cnt != len) {
      Util.prt("Read for RAWSCNL is short need=" + len + " got " + cnt);
    } else if (dbg) {
      Util.prt("Length returned is o.k.");
    }
    bb.position(0);
  }

  /**
   * This gets data suitable for a helicorder (times, minimum, maximum) at one sample per second.
   * The arrays must be big enough to hold the requested duration at 1 sps, or a Array bounds
   * exception will happen
   *
   * @param reqid
   * @param seedname A NNSSSSSCCCLL
   * @param start Start time
   * @param end End Time
   * @param compress if true, the connections should use the compression
   * @param time Returned with times of each sample
   * @param min Returned with minimum value of each sample
   * @param max Returned with maximum value of each sample
   * @return The number of data points returned.
   * @throws IOException
   */
  public int getSCNLHELIRAW(String reqid, String seedname, GregorianCalendar start, GregorianCalendar end,
          boolean compress, long[] time, double[] min, double[] max) throws IOException {
    currentCommand = "GETSCNLHELIRAW";
    GregorianCalendar st = new GregorianCalendar();
    GregorianCalendar en = new GregorianCalendar();

    long stms = (start.getTimeInMillis() - year2000MSWinston);
    long endms = (end.getTimeInMillis() - year2000MSWinston);
    String line = "GETSCNLHELIRAW: " + reqid + " " + seedname.substring(2, 7).trim()
            + " " + seedname.substring(7, 10).trim() + " " + seedname.substring(0, 2).trim() + " "
            + seedname.substring(10, 12).replaceAll(" ", "-").trim() + " "
            + Util.df23(stms / 1000.) + " " + Util.df23(endms / 1000.) + " " + (compress ? "1" : "0") + "\n";
    if (dbg) {
      Util.prt(line);
    }
    openSocket();         // Open socket to server
    sendCommand(line);    // Send this command line
    getResponse();        // Read back data until first newline
    parts = ans.trim().split("\\s");
    returnedLen = Integer.parseInt(parts[1]);
    int ns = 0;
    if (returnedLen > 0) {
      readLen();
      byte[] uncompressed = gov.usgs.util.Util.decompress(b, returnedLen);
      ByteBuffer bbcomp = ByteBuffer.wrap(uncompressed);
      bbcomp.position(0);

      // Decode the bytes
      ns = bbcomp.getInt();
      if (dbg) {
        Util.prt("SCNLHELIRAW returnes " + ns + " data points");
      }
      for (int i = 0; i < ns; i++) {
        time[i] = (long) (year2000MSWinston + bb.getDouble() * 1000);
        min[i] = bbcomp.getDouble();
        max[i] = bbcomp.getDouble();
        if (dbg) {
          Util.prt(Util.ascdatetime2(time[i]) + " " + min[i] + " " + max[i]);
        }
      }
    }
    return ns;
  }

  /**
   * This gets data suitable for a helicorder (times, minimum, maximum) at one sample per second.
   * The arrays must be big enough to hold the requested duration at 1 sps, or a Array bounds
   * exception will happen. Note: Right after getting the data the user might want to get the header
   * values for the :
   * <pre>
   * start time (getRawWaveMillis(),
   * data rate (getRawWaveRate()),
   * registrationOffset (getRawWaveRegistrationOffset())
   * </pre>
   *
   * @param reqid Some request ID
   * @param seedname A NNSSSSSCCCLL
   * @param start Start time
   * @param end End Time
   * @param compress if true, the connections should use the compression
   * @param data The array of ints to get the data points
   * @return The number of data points returned.
   * @throws IOException
   */
  public int getWAVERAW(String reqid, String seedname, GregorianCalendar start, GregorianCalendar end,
          boolean compress, int[] data) throws IOException {
    currentCommand = "GETWAVERAW";
    GregorianCalendar st = new GregorianCalendar();
    GregorianCalendar en = new GregorianCalendar();
    returnedRequestID = reqid;
    long stms = (start.getTimeInMillis() - year2000MSWinston);
    long endms = (end.getTimeInMillis() - year2000MSWinston);
    String line = "GETWAVERAW: " + reqid + " " + seedname.substring(2, 7).trim()
            + " " + seedname.substring(7, 10).trim() + " " + seedname.substring(0, 2).trim() + " "
            + seedname.substring(10, 12).replaceAll(" ", "-").trim() + " "
            + Util.df23(stms / 1000.) + " " + Util.df23(endms / 1000.) + " " + (compress ? "1" : "0") + "\n";
    if (dbg) {
      Util.prt(line);
    }
    openSocket();         // Open socket to server
    sendCommand(line);    // Send this command line
    getResponse();        // Read back data until first newline
    parts = ans.trim().split("\\s");
    returnedLen = Integer.parseInt(parts[1]);
    int ns = 0;
    ByteBuffer bbtmp;
    if (returnedLen > 0) {
      readLen();
      int nret;
      if (compress) {
        //byte [] uncompressed = gov.usgs.util.Util.decompress(b, returnedLen);
        byte[] uncompressed = new byte[returnedLen * 6];
        try {
          nret = Compressor.decompress(b, returnedLen, uncompressed);
          /*byte [] b2 = new byte[b.length];
          nret =    Compressor.compress(uncompressed,  Deflater.BEST_SPEED, 0, nret, b2);
          int nbad=0;
          for(int i=0; i<nret; i++) {
            if(b[i] != b2[i]) {Util.prt(i+" "+b[i]+"!="+b2[i]);nbad++;}
          }
          if(nbad > 0) 
            Util.prt("**** compressor test failed!");*/
        } catch (DataFormatException e) {
          Util.prt("Exception on decompress e=" + e);
          e.printStackTrace();
        } catch (RuntimeException e) {
          Util.prt("Runtime on decompress e=" + e);
          e.printStackTrace();
        }
        bbtmp = ByteBuffer.wrap(uncompressed);
        bbtmp.position(0);
      } else {
        bbtmp = this.bb;
        bbtmp.position(0);
      }

      // Decode the bytes
      double rawwavetime = bbtmp.getDouble();
      long rawwaveMillis = (long) (rawwavetime * 1000. + year2000MSWinston + 0.5);
      returnedRate = bbtmp.getDouble();
      registrationOffset = bbtmp.getDouble();
      returnedSeedname = seedname;
      returnedStart = rawwaveMillis / 1000.;
      returnedType = "";
      returnedF = "";
      ns = bbtmp.getInt();
      returnedEnd = returnedStart + (ns - 1) / returnedRate;
      if (dbg) {
        Util.prt("WAVERAW returnes " + ns + " data points");
      }
      for (int i = 0; i < ns; i++) {
        data[i] = bbtmp.getInt();
      }
    }

    return ns;
  }

  /**
   * This is the registration offset returned by the last call to getRAWWAVE
   *
   * @return The registration offset (what ever that is!)
   */
  public double getRawWaveRegistrationOffset() {
    return registrationOffset;
  }
  //private long rawwaveMillis;
  //private GregorianCalendar rawWaveTime = new GregorianCalendar();
  //private double rawwaverate;
  //private double rawwavetime;
  private double registrationOffset;

  /**
   * Request data in the ASCII format - generally used by GUIs
   *
   * @param reqid The request ID
   * @param scnl A NNSSSSSCCCLL in normal form. It can contain "hyphens"
   * @param start Start time
   * @param end End time
   * @param fill Fill value for missing data
   * @param data Returned with the data (this must be big enough when this routine is called)
   * @return the number of data points in data
   * @throws IOException
   */
  public int getSCNL(String reqid, String scnl, GregorianCalendar start, GregorianCalendar end,
          String fill, int[] data) throws IOException {
    gc.setTimeInMillis((long) (returnedStart * 1000.));
    currentCommand = "GETSCNL";
    double duration = (end.getTimeInMillis() - start.getTimeInMillis()) / 1000.;
    String line = "GETSCNL:" + reqid + " " + scnl.substring(2, 7).replaceAll("-", " ").trim()
            + " " + scnl.substring(7, 10).trim() + " " + scnl.substring(0, 2).replaceAll("-", " ").trim() + " "
            + scnl.substring(10, 12).replaceAll(" ", "-").trim() + " "
            + Util.df23(start.getTimeInMillis() / 1000.) + " " + Util.df23(end.getTimeInMillis() / 1000.) + " " + fill + "\n";
    if (dbg) {
      Util.prt(line);
    }
    openSocket();         // Open socket to server
    sendCommand(line);    // Send this command line
    getResponse();        // Read back data until first newline
    parseResponse();      // Take the first few paramaters and put in the returned* variables
    gc.setTimeInMillis((long) (returnedStart * 1000.));
    if (dbg) {
      Util.prt("GETSCNL: Data returned from " + Util.ascdatetime2(gc) + " rate=" + getRate());
    }
    //if(dbg) Util.prt(ans);
    double rate = getRate();
    if (parts.length - 10 != (int) (duration * rate)) {
      Util.prt("*** GETSCNL: number of samples does not agree got " + (duration * rate) + " vs got " + (parts.length - 10));
    } else if (dbg) {
      Util.prt("GETSCNL: Number or samples o.k");
    }

    if (dbg) {
      StringBuilder dsb = new StringBuilder();
      for (int i = 0; i < Math.min(parts.length, 20); i++) {
        dsb.append(i).append("=").append(parts[i].equals(fillString) ? "Fill" : parts[i]).append((i % 10 == 9 ? "\n" : " "));
      }
      dsb.append("\n");
      for (int i = parts.length - 10; i < parts.length; i++) {
        dsb.append(i).append("=").append(parts[i].equals(fillString) ? "Fill" : parts[i]).append((i % 10 == 9 ? "\n" : " "));
      }
      Util.prt(dsb.toString());
    }

    nfill = 0;
    int fillValue = FILL_VALUE;
    for (int i = 10; i < parts.length; i++) {
      try {
        if (parts[i].equals(fillString)) {/*Util.prt("fill at :"+i);*/
          data[i - 10] = FILL_VALUE;
        } else {
          data[i - 10] = Integer.parseInt(parts[i]);
        }
      } catch (NumberFormatException e) {
        Util.prt("bad intput a i=" + i + " parts=" + parts[i]);
      }
      if (data[i - 10] == fillValue) {
        nfill++;
      }
    }

    if (nfill > 0 && dbg) {
      Util.prt(" *** number of fill values=" + nfill);
    }
    return parts.length - 10;
  }

  /**
   * This returns a MenuItem for the given seed name, by going to the server and asking for this one
   * channel Note: This is different than using getMENUChannel() which returns the same information,
   * but at the time of the last getMENU()
   *
   * @param reqid The request ID
   * @param seedname The seedname
   * @return Null if no seedname is found, else a menu item for that seedname
   * @throws IOException
   */
  public MenuItem getMENUSCNL(String reqid, String seedname) throws IOException {
    doTextCommand("MENUSCNL", reqid, seedname);
    //Util.prt(ans);
    if (!ans.contains(" ")) {
      Util.prt("**** bad getMENUSCNL return ans=" + ans);
      return null;
    }
    MenuItem item = new MenuItem(ans.substring(ans.indexOf(" ")));
    return item;
  }

  /**
   * This returns a MenuItem for the given seed name, by going to the server and asking for this one
   * channel Note: This is different than using getMENUChannel() which returns the same information,
   * but at the time of the last getMENU()
   *
   * @param reqid The request ID
   * @param seedname The seedname
   * @param item A user supplied MenuItem to load with the result
   * @return
   * @throws IOException
   */
  public MenuItem getMENUSCNL(String reqid, String seedname, MenuItem item) throws IOException {
    doTextCommand("MENUSCNL", reqid, seedname);
    item.load(ans.substring(ans.indexOf(" ")));
    return item;
  }

  /**
   * This returns a MenuSummary for the given seed name, by going to the server and asking for this
   * one channel Note: This is different than using getMENUChannel() which returns the same
   * information, but at the time of the last getMENU()
   *
   * @param reqid The request ID
   * @return A summary string
   * @throws IOException
   */
  public String getMENUSUM(String reqid) throws IOException {
    doTextCommand("MENUSUM", reqid, "--");

    return ans;
  }

  /**
   * Get the MENU tree map. Be careful as this is not synchronized
   *
   * @return The tree map
   */
  public TreeMap<String, MenuItem> getMenuTreeMap() {
    return menu;
  }

  /**
   * The infamous getMENU from a wave server - populate the tree map with menu items representing
   * this menu. Update or create new MenuItems as needed.
   *
   * Once the menu is gotten normally individual rows would be obtained with getMenuChannel()
   *
   * @param reqid The request ID
   * @throws IOException
   */
  public void getMENU(String reqid) throws IOException {
    doTextCommand("MENU", reqid, "ZZSSSSSCCC--");
    String str = getResponseString();
    str = str.replaceAll("s4", "s4\n").replaceAll("i4", "i4\n").replaceAll("s2", "s2\n").replaceAll("i2", "i2\n");
    Util.prta("MENU returned " + str.length() + " bytes");
    //if(dbg) Util.prt(str);      
    // Create an array with one line per array element.
    String[] lines = str.split("\n");
    long now = System.currentTimeMillis();
    for (int i = 0; i < lines.length; i++) {
      if (lines[i].trim().length() == 0) {
        continue;    // Too short
      }
      if (i == 0) {
        lines[i] = lines[i].substring(lines[i].indexOf(" "));  // trim request ID off of first line
      }
      try {
        lines[i] = lines[i].trim().replaceAll("  ", " ").replaceAll("  ", " ").replaceAll("  ", " ");
        String[] p = lines[i].split("\\s");
        if (p.length >= 8) {
          String seedname = Util.makeSeedname(p[3], p[1], p[2], p[4]);
          MenuItem item = menu.get(seedname);
          if (item == null) {
            item = new MenuItem(lines[i]);
            menu.put(seedname, item);
          } else {
            item.load(lines[i]);
          }
          if (dbg) {
            Util.prt(Util.leftPad("" + i, 5) + " " + lines[i] + " " + item
                    + " " + Util.df21((now - item.getEndInMillis()) / 1000.) + ((now - item.getEndInMillis() > 300000) ? "*" : ""));
          }
        } else {
          Util.prt("Bad menu line=" + lines[i]);
        }
      } catch (NumberFormatException e) {
        Util.prt("Got runtime exception parsing line i=" + i + " line=" + lines[i]);
        e.printStackTrace();
      }

    }
  }

  /**
   * Return the Menu Item (or null) for the menu for the given net, station, chan, location
   *
   * @param network up to 2 character network code (empty network codes are not allowed)
   * @param station up to 5 character station code
   * @param channel up to 3 character (normally exactly 3 character) channel code
   * @param location up to 2 character location code
   * @return
   */
  public MenuItem getMenuChannel(String network, String station, String channel, String location) {
    return menu.get(Util.makeSeedname(network, station, channel, location));
  }

  /**
   * Get the MenuItem (or null) for the given seedname
   *
   * @param seedname A NNSSSSSCCCLL seedname - spaces to pad the fixed fields - no delimiters
   * @return
   */
  public MenuItem getMenuChannel(String seedname) {
    return menu.get(seedname);
  }

  /**
   * Request raw data as TimeSerisBlock packets. The ArrayList will only contain the subclass
   * TraceBufs, but this will return the super class for convenience. Please remember to call
   * freeMemory() with the return with the return if tbPool use is specified true to free the pool
   * space!
   *
   * @param reqid The request ID
   * @param scnl A NNSSSSSCCCLL in normal form. It can contain "hyphens"
   * @param start Start time
   * @param end End time
   * @param tbPool If true, TraceBufs will come from a TraceBufPool. The user must call freeMemory
   * with returned values
   * @param tbtt The ArrayList of TraceBufs as their superclass TimeSeriesBlocks that will have all
   * responsive data added to this structure
   * @throws IOException
   */
  public void getSCNLRAWAsTimeSeriesBlocks(String reqid, String scnl, GregorianCalendar start, GregorianCalendar end, boolean tbPool,
          ArrayList<TimeSeriesBlock> tbtt) throws IOException {
    ArrayList<TraceBuf> tbs = new ArrayList<TraceBuf>(tbtt.size());
    getSCNLRAW(reqid, scnl, start, end, tbPool, tbs);
    for (TraceBuf tb : tbs) {
      tbtt.add(tb);
    }
  }

  /**
   * Request raw data as TimeSerisBlock packets. The ArrayList will only contain the subclass
   * TraceBufs, but this will return the super class for convenience. Please remember to call
   * freeMemory() with the return with the return if tbPool use is specified true to free the pool
   * space!
   *
   * @param reqid The request ID
   * @param scnl A NNSSSSSCCCLL in normal form. It can contain "hyphens"
   * @param start Start time
   * @param end End time
   * @param tbPool If true, TraceBufs will come from a TraceBufPool. The user must call freeMemory
   * with returned values
   * @param tbtt The ArrayList of TraceBufs as their superclass TimeSeriesBlocks that will have all
   * responsive data added to this structure
   * @throws IOException
   */
  public void getSCNRAWAsTimeSeriesBlocks(String reqid, String scnl, GregorianCalendar start, GregorianCalendar end, boolean tbPool,
          ArrayList<TimeSeriesBlock> tbtt) throws IOException {
    ArrayList<TraceBuf> tbs = new ArrayList<TraceBuf>(tbtt.size());
    getSCNRAW(reqid, scnl, start, end, tbPool, tbs);
    for (TraceBuf tb : tbs) {
      tbtt.add(tb);
    }
  }

  /**
   * Request raw data as TraceBuf packets. Please remember to call freeMemory() with the return if
   * tbPool use is specified true to clear the pool space
   *
   * @param reqid The request ID
   * @param scnl A NNSSSSSCCCLL in normal form. It can contain "hyphens"
   * @param start Start time
   * @param end End time
   * @param tbPool If true, TraceBufs will come from a TraceBufPool. The user must call freeMemory
   * with returned values
   * @param tbs The ArrayList of TraceBufs that will have all responsive data added to this
   * structure
   * @throws IOException
   */
  public void getSCNLRAW(String reqid, String scnl, GregorianCalendar start, GregorianCalendar end, boolean tbPool,
          ArrayList<TraceBuf> tbs) throws IOException {
    double duration = (end.getTimeInMillis() - start.getTimeInMillis()) / 1000.;
    currentCommand = "GETSCNLRAW";
    String line = "GETSCNLRAW: " + reqid + " " + scnl.substring(2, 7).trim()
            + " " + scnl.substring(7, 10).trim() + " " + scnl.substring(0, 2).trim() + " "
            + (scnl.length() >= 12 ? scnl.substring(10, 12).replaceAll(" ", "-").trim() : "--") + " "
            + Util.df23(start.getTimeInMillis() / 1000.) + " " + Util.df23(end.getTimeInMillis() / 1000.) + "\n";
    if (dbg) {
      Util.prt(line);
    }
    openSocket();         // Open socket to server
    sendCommand(line);    // Send this command line
    getResponse();        // Read back data until first newline
    parseResponse();      // Take the first few paramaters and put in the returned* variables
    readLen();            // This is a command where the length is in the response, read the bytes after the newline
    byte[] s2 = new byte[2];
    byte[] work = new byte[4096];
    work[1] = EWMessage.TYPE_TRACEBUF2;
    work[0] = 13; // inst
    work[2] = 18; // module
    work[3] = 0;  // frag
    work[4] = 0;  // seq
    work[5] = 1;  // last

    //int save=bb.position();
    while (bb.position() < returnedLen) {
      int save = bb.position();
      bb.position(save + 57);
      bb.get(s2);
      String type = new String(s2);
      if (type.equals("i4")) {
        bb.order(ByteOrder.LITTLE_ENDIAN);
      }
      bb.position(save);
      bb.position(bb.position() + 4);
      int ns = bb.getInt();
      if (type.equals("i4")) {
        bb.order(ByteOrder.BIG_ENDIAN);
      }
      if (dbg) {
        Util.prt(type + " ns=" + ns + " save=" + save + " len=" + returnedLen);
      }
      bb.position(save);
      bb.get(work, 6, TraceBuf.TRACE_HDR_LENGTH + 4 * ns);    // We do not have the 6 byte EW header
      TraceBuf tb;
      if (tbPool) {
        tb = tbp.get();
      } else {
        tb = new TraceBuf();
      }
      tb.setSCNMode(false);
      tb.load(work, TraceBuf.TRACE_HDR_LENGTH + 4 * ns + 6);
      tbs.add(tb);
      bb.position(save + TraceBuf.TRACE_HDR_LENGTH + 4 * ns - 6);

    }
    long endreq = System.currentTimeMillis();
    if (dbg) {
      Util.prt("elapsed=" + (endreq - startreq) + " bb=" + bb.position() + " lenTB=" + returnedLen + " newlinePos=" + newl + " size=" + tbs.size());
    }
  }

  /**
   * Request raw data as TraceBuf packets. Please remember to call freeMemory() with the return if
   * tbPool use is specified true to clear the pool space
   *
   * @param reqid The request ID
   * @param scnl A NNSSSSSCCCLL in normal form. It can contain "hyphens", the location code is
   * ignored.
   * @param start Start time
   * @param end End time
   * @param tbPool If true, TraceBufs will come from a TraceBufPool. The user must call freeMemory
   * with returned values
   * @param tbs The ArrayList of TraceBufs that will have all responsive data added to this
   * structure
   * @throws IOException
   */
  public void getSCNRAW(String reqid, String scnl, GregorianCalendar start, GregorianCalendar end, boolean tbPool,
          ArrayList<TraceBuf> tbs) throws IOException {
    double duration = (end.getTimeInMillis() - start.getTimeInMillis()) / 1000.;
    currentCommand = "GETSCNRAW";
    String line = "GETSCNRAW: " + reqid + " " + scnl.substring(2, 7).trim()
            + " " + scnl.substring(7, 10).trim() + " " + scnl.substring(0, 2).trim() + " "
            + //scnl.substring(10,12).replaceAll(" ","-").trim()+" "+
            Util.df23(start.getTimeInMillis() / 1000.) + " " + Util.df23(end.getTimeInMillis() / 1000.) + "\n";
    if (dbg) {
      Util.prt(line);
    }
    openSocket();         // Open socket to server
    sendCommand(line);    // Send this command line
    getResponse();        // Read back data until first newline
    parseResponse();      // Take the first few paramaters and put in the returned* variables
    readLen();            // This is a command where the length is in the response, read the bytes after the newline
    byte[] s2 = new byte[2];
    byte[] work = new byte[4096];
    work[1] = EWMessage.TYPE_TRACEBUF2;
    work[0] = 13; // inst
    work[2] = 18; // module
    work[3] = 0;  // frag
    work[4] = 0;  // seq
    work[5] = 1;  // last

    //int save=bb.position();
    while (bb.position() < returnedLen) {
      int save = bb.position();
      bb.position(save + 57);
      bb.get(s2);
      String type = new String(s2);
      if (type.equals("i4")) {
        bb.order(ByteOrder.LITTLE_ENDIAN);
      }
      bb.position(save);
      bb.position(bb.position() + 4);
      int ns = bb.getInt();
      if (type.equals("i4")) {
        bb.order(ByteOrder.BIG_ENDIAN);
      }
      if (dbg) {
        Util.prt(type + " ns=" + ns + " save=" + save + " len=" + returnedLen);
      }
      bb.position(save);
      bb.get(work, 6, TraceBuf.TRACE_HDR_LENGTH + 4 * ns);    // We do not have the 6 byte EW header
      TraceBuf tb;
      if (tbPool) {
        tb = tbp.get();
      } else {
        tb = new TraceBuf();
      }
      tb.setSCNMode(false);
      tb.load(work, TraceBuf.TRACE_HDR_LENGTH + 4 * ns + 6);
      tbs.add(tb);
      bb.position(save + TraceBuf.TRACE_HDR_LENGTH + 4 * ns - 6);

    }
    long endreq = System.currentTimeMillis();
    if (dbg) {
      Util.prt("elapsed=" + (endreq - startreq) + " bb=" + bb.position() + " lenTB=" + returnedLen + " newlinePos=" + newl + " size=" + tbs.size());
    }
  }

  /**
   * Get the instrument metadata for all channels - this is a big string
   * name=113A,description=Mohawk Valley, Roll, AZ,
   * USA,longitude=-113.766700,latitude=32.768300,height=118.0,timezone=GMT
   *
   * @param requestid request ID
   * @return The instrument metadata string per Winston
   * @throws IOException
   */
  public String getMETADATAINSTRUMENT(String requestid) throws IOException {
    doTextCommand("GMDI", requestid, "");
    return ans;
  }

  /**
   * Get the channel metadata for all channels - this is a big string name=113A,description=Mohawk
   * Valley, Roll, AZ, USA,longitude=-113.766700,latitude=32.768300,height=118.0,timezone=GMT
   *
   * @param requestid request ID
   * @return The channel metadata string per Winston
   * @throws IOException
   */
  public String getMETADATACHANNEL(String requestid) throws IOException {
    doTextCommand("GMDC", requestid, "");
    return ans;
  }

  /**
   * Get the server status
   *
   * @param requestid
   * @return Normally "GC: 2"
   * @throws IOException
   */
  public String getSTATUS(String requestid) throws IOException {
    doTextCommand("STATUS", requestid, "");
    return ans;
  }

  /**
   * Return GETCHANNELS string - this has times and coordinates -1:113A BHE AE
   * :397483200.000000:448177664.000000:-113.766700:32.768300
   *
   * @param requestid
   * @param word "" for all, or "METADATA"
   * @return The strings
   * @throws IOException if one occurs
   */
  public String getCHANNELS(String requestid, String word) throws IOException {
    doTextCommand("GETCHANNELS", requestid, word);
    return ans;
  }

  /**
   * return protocol version
   *
   * @param requestid
   * @return version as a string
   * @throws IOException
   */
  public String getVERSION(String requestid) throws IOException {
    doTextCommand("VERSION", requestid, "");
    return ans;
  }

  /**
   * this is the test routine for all of these functions. Supports a command line interface to test
   * most WaveServer functions.
   *
   * @param args
   */
  public static void main(String[] args) {
    Util.setModeGMT();
    Util.setNoconsole(true);
    Util.setNoInteractive(true);
    Util.init();
    GregorianCalendar g = new GregorianCalendar(2000, 0, 1);

    if (args.length == 0) {
      Util.prt("-c MENU|MENUSCNL|GETSCNL|GETSCNLRAW|GETSCNRAW|GETSCNLHELIRAW|GETWAVERAW|GMDC|GMDI|STATUS|GETCHANNELS -s NSCL -b date -e date -f fill -h HOST -p port(2060) -pin PIN -r requestid -ms");
      Util.prt("   GETSCNL requires -s -b -e");
      Util.prt("   MENUSCNL requires -s");
      Util.prt("   GETSCNLRAW and GETSCNRAW requires -s -b -e ");
      Util.prt("   GETSCNLRAW and GETSCNRAW with -ms will create MiniSeed files from the resulting TraceBufs");
      Util.prt("   GMDC is GETMETADATA: CHANNEL and GMDI is GETMETADATA: INSTRUMENT");
      Util.prt("   GETCHANNNELSMD does GETCHANNELS:  METADATA (used by swarm!)");
      Util.prt("   NSCL must be in exact NNSSSSSCCCLL form (spaces count!).  Spaces or hyphens can be used");
      Util.prt("   -h  IP.OF.Server  Set the host (def=localhost)");
      Util.prt("   -p  port          Sets the port (def=2060");
      Util.prt("   -b or -e Date     beginning and ending data in yyyy/mm/dd hh:mm:ss format (slashes or dashes in date supported - dash to separate time from date allowed)");
      Util.prt("   -test omit the -c but include -s -b and -e will run ALL of the commands and compare the waveforms returned by GETSCNL,GETSCNLRAW, and GETWAVERAW");
      Util.prt("   -ms LL with GETSCNLRAW or GETSCNRAW will convert the returned trace bufs to MiniSEED with location code LL - useful for debugging");
      Util.prt("   -fill FILL changes the fill value from 2147000000");
      Util.prt("   -r RID changes the request ID from GS to RID");
      Util.prt("   -pin PID sets the PIN replacing the default 0000");
      //System.exit(0);
    }
    //int port=16022;
    int dummy = 0;
    boolean makeMiniSeed = false;
    String makeMiniSeedLocation = "";
    String filename = "temp.ms";
    String pin = "0000";
    String seedname = "USDUG  BHZ00";
    String requestid = "GS";
    String start = "2014/03/24-12:10:00.00000000";
    String ending = "2014/03/24-12:20:00.00000000";
    String fill = "2147000000";
    String command = "GETSCNL";
    String host = "localhost";
    int port = 2060;
    GregorianCalendar begin = new GregorianCalendar();
    GregorianCalendar end = new GregorianCalendar();
    GregorianCalendar gc = new GregorianCalendar();
    ArrayList<TimeSeriesBlock> tbs = new ArrayList<TimeSeriesBlock>(100);
    StringBuilder sb = new StringBuilder(2000);
    int[] datagetscnl = null;
    // fully automatic test - do one comman each
    String[] test = {"",
      "GETSCNLRAW",
      "GETSCNRAW",
      "GETSCNL",
      "GETSCNLHELIRAW",
      "GETWAVERAW",
      "VERSION",
      "MENU",
      "MENUSCNL",
      "GMDC",
      "GMDI",
      "GETCHANNELS",
      "STATUS",
      "MENUSUM"};
    boolean testmode = false;
    boolean stressTest = false;
    long timeoutSecs = 60;
    boolean dbg = false;
    for (int i = 0; i < args.length; i++) {
      switch (args[i]) {
        case "-pin":
          pin = args[i + 1];
          i++;
          break;
        case "-s":
          seedname = (args[i + 1].replaceAll("-", " ").trim() + "           ").substring(0, 12);
          i++;
          break;
        case "-r":
          requestid = args[i + 1];
          i++;
          break;
        case "-b":
          start = args[i + 1];
          i++;
          break;
        case "-e":
          ending = args[i + 1];
          i++;
          break;
        case "-f":
          fill = args[i + 1];
          i++;
          break;
        case "-c":
          command = args[i + 1];
          test[0] = command;
          i++;
          break;
        case "-h":
          host = args[i + 1];
          i++;
          break;
        case "-ms":
          makeMiniSeed = true;
          makeMiniSeedLocation = args[i + 1];
          i++;
          break;
        case "-p":
          port = Integer.parseInt(args[i + 1]);
          i++;
          break;
        case "-to":
          timeoutSecs = Integer.parseInt(args[i + 1]);
          i++;
          break;
        case "-dbg":
          dbg = true;
          break;
        case "-wsc":
          dummy = 1;    // do nothing but suppress NB warnings.
          break;
        case "-test":
          testmode = true;
          break;
        case "-stress":
          StressThread.main(args);
          System.exit(1);
        default:
          Util.prt("Unknown option " + args[i] + " at " + i);
          break;
      }
    }
    Util.prta("cmd=" + command + " to " + host + "/" + port + " scnl=" + seedname + " start=" + start + " end=" + ending + " fill=" + fill);
    MakeRawToMiniseed maker = null;
    if (makeMiniSeed) {
      maker = new MakeRawToMiniseed();
    }

    // create Gregorians with start and end times 
    Timestamp st = FUtil.stringToTimestamp(start.replaceAll("-", " ").replaceAll("/", "-"));
    Timestamp en = FUtil.stringToTimestamp(ending.replaceAll("-", " ").replaceAll("/", "-"));
    begin.setTimeInMillis(st.getTime());
    end.setTimeInMillis(en.getTime());
    WaveServerClient wsc = new WaveServerClient(host, port); // Set up object to do command
    wsc.setFillString(fill);
    if (dbg) {
      wsc.setDebug(true);
    }
    wsc.setTimeoutInterval(timeoutSecs * 1000);
    for (String test1 : test) {
      command = test1;
      Util.prt("cmd=" + command);
      if (command.equals("")) {
        continue;
      }
      try {
        long expected = 0;
        boolean found = false;
        // Have to read response for GETSCNLRAW after figuring out how long it will be.
        switch (command) {
          case "GETSCNLRAW":
          case "GETSCNRAW":
            try {
              tbs.clear();
              if (command.equals("GETSCNLRAW")) {
                wsc.getSCNLRAWAsTimeSeriesBlocks(requestid, seedname, begin, end, true, tbs);
              } else {
                wsc.getSCNRAWAsTimeSeriesBlocks(requestid, seedname, begin, end, true, tbs);
              }
            } catch (IOException e) {
              Util.prt("Error in GETSCNLRAW call e=" + e);
              e.printStackTrace();
            }
            Util.prta(command + " returned " + tbs.size() + " trace bufs");
            for (TimeSeriesBlock tb1 : tbs) {
              TraceBuf tb = (TraceBuf) tb1;
              Util.prt("tb=" + tb.toString()
                      + (tb.getData().length >= 1 ? " [0]=" + tb.getData()[0] : " [0]=null")
                      + (tb.getData().length >= 2 ? " [1]=" + tb.getData()[1] : " [1]=null")
                      + (tb.getData().length >= 3 ? " [2]=" + tb.getData()[2] : " [2]=null")
                      + (tb.getData().length >= 4 ? " [3]=" + tb.getData()[3] : " [3]=null"));
              for (int i = 0; i < tb.getNsamp(); i++) {
                if (tb.getData()[i] == FILL_VALUE) {
                  Util.prt(" **** fill at i=" + i);
                }
              }
              if (dbg) {
                if (sb.length() > 0) {
                  sb.delete(0, sb.length());
                }
                for (int i = 0; i < tb.getNsamp(); i++) {
                  if (i % 20 == 0) {
                    sb.append("\n").append(Util.leftPad(i + ":", 8));
                  }
                  sb.append(Util.leftPad(" " + tb.getData()[i], 8));
                  if (tb.getData()[i] == FILL_VALUE) {
                    sb.append("*");
                  }
                }
                Util.prt(sb.toString());
              }
              if (makeMiniSeed && maker != null) {
                if (expected > 0 && Math.abs(expected - tb.getTimeInMillis()) > 1000. / tb.getRate()) {
                  maker.flush();
                }
                gc.setTimeInMillis(tb.getTimeInMillis());
                long usec = tb.getTimeInMillis() * 1000;
                int year = gc.get(Calendar.YEAR);
                int doy = gc.get(Calendar.DAY_OF_YEAR);
                int sec = (int) (usec % 86400000000L / 1000000);  // milliseconds into this day
                if (expected <= 0) {
                  filename = tb.getSeedname().substring(0, 10).replaceAll(" ", "_")
                          + makeMiniSeedLocation + "_" + year + "_" + doy + "_"
                          + Util.df2(gc.get(Calendar.HOUR_OF_DAY)) + Util.df2(gc.get(Calendar.MINUTE)) + ".ms";
                }
                expected = tb.getNextExpectedTimeInMillis();
                usec = usec % 1000000;                      // microseconds left over
                maker.loadTSIncrement(tb.getSeedNameString().substring(0, 10) + makeMiniSeedLocation,
                        tb.getNsamp(), tb.getData(), year, doy, sec, (int) usec, tb.getRate(),
                        0, 0, 0, 1);

              }
            }// ending of data clean up
            if (maker != null) {
              maker.flush();
              maker.writeToDisk(filename);
            }
            break;
          case "GETWAVERAW": {
            int[] data = new int[8640000];
            int npts = wsc.getWAVERAW(requestid, seedname, begin, end, true, data);
            Util.prt(" RAWWAVE return " + Util.ascdatetime2(wsc.getStartInMillis()) + " " + npts + " samps "
                    + wsc.getRate() + " Hz offset=" + wsc.getRawWaveRegistrationOffset());
            if (tbs.size() > 0 && testmode) {   // Check the GETSCNLRAW, GETSCNL, and GETRAWWAVE data against each other
              int i = 0;
              int nok = 0;
              for (TimeSeriesBlock tb1 : tbs) {
                TraceBuf tb = (TraceBuf) tb1;
                //Util.prt(""+tb);
                for (int j = 0; j < tb.getNsamp(); j++) {
                  if (i >= npts) {
                    break;
                  }
                  if (tb.getData()[j] != data[i] || tb.getData()[j] != datagetscnl[i]) {
                    Util.prt("Data disagrees " + i + " wraw="
                            + data[i] + " != tb=" + tb.getData()[j] + (datagetscnl != null ? " getscnl=" + datagetscnl[i] : ""));
                  } else {
                    nok++;
                  }
                  i++;
                }
              }
              if (nok == npts) {
                Util.prt("* Congratulations :  data from GETSCNLRAW, GETSCNL, and GETWAVERAW agree!");
              }
            }
            break;
          }
          case "GETSCNLHELIRAW":
            long[] time = new long[864000];
            double[] min = new double[864000];
            double[] max = new double[864000];
            int ns = wsc.getSCNLHELIRAW(requestid, seedname, begin, end, true, time, min, max);
            Util.prt(" SCNLHELIRAW returns " + ns + " points");
            break;
          case "GETSCNL": {
            datagetscnl = new int[8640000];
            int npts = wsc.getSCNL(requestid, seedname, begin, end, fill, datagetscnl);
            int cfill = 0;
            for (int i = 0; i < npts; i++) {
              if (datagetscnl[i] == FILL_VALUE) {
                cfill++;
              }
            }
            Util.prt(" GETSCNL returns " + npts + " points " + cfill + " are fills");
            break;
          }
          case "MENU":
            wsc.getMENU(requestid);
            Util.prt("" + wsc.getMenuChannel(seedname));    // show we can now get an item frmo the tree map
            TreeMap<String, MenuItem> menu = wsc.getMenuTreeMap();//  get the treemap
            Util.prt("TreeMap size=" + menu.size());
            break;
          case "MENUSCNL":
            MenuItem item = wsc.getMENUSCNL(requestid, seedname);
            Util.prt("getMENUSCNL for " + seedname + " returned " + item);
            Util.prt("Compare to getMenuChannel() for same item " + wsc.getMenuChannel(seedname));
            break;
          case "MENUSUM":
            Util.prt(wsc.getMENUSUM(requestid));
            break;
          case "GMDC": {
            String s = wsc.getMETADATACHANNEL(requestid);
            if (dbg) {
              Util.prt("Full response=" + s);
            } else {
              Util.prta("GMDC returns (first 400)\n" + s.substring(0, Math.min(400, s.length())) + " ....");
            }
            break;
          }
          case "GMDI": {
            String s = wsc.getMETADATAINSTRUMENT(requestid);
            if (dbg) {
              Util.prt("Full response=" + s);
            } else {
              Util.prta("GMDI returns (first 400)\n" + s.substring(0, Math.min(400, s.length())) + " ....");
            }
            break;
          }
          case "STATUS":
            Util.prta("STATUS returns\n" + wsc.getSTATUS(requestid));
            break;
          case "GETCHANNELS": {
            String s = wsc.getCHANNELS(requestid, "");
            if (dbg) {
              Util.prt("Full response=" + s);
            } else {
              Util.prta("GETCHANNELS returns (first 400)\n" + s.substring(0, Math.min(400, s.length())) + " ....");
            }
            break;
          }
          case "GETCHANNELSMD": {
            String s = wsc.getCHANNELS(requestid, "METADATA");
            if (dbg) {
              Util.prt("Full response=" + s);
            } else {
              Util.prta("GETCHANNELS returns (first 400)\n" + s.substring(0, Math.min(400, s.length())) + " ....");
            }
            break;
          }
          case "VERSION": {
            wsc.doTextCommand(command, requestid, seedname);
            String ans = wsc.getResponseString();
            Util.prt(ans);
            break;
          }
          default: {
            Util.prt("**** we should not get here - command=" + command);
            wsc.doTextCommand(command, requestid, seedname);
            String ans = wsc.getResponseString();
            ans = ans.replaceAll("s4", "s4\n");
            Util.prt(ans);
            break;
          }
        }
      } catch (SocketException e) {
        Util.SocketIOErrorPrint(e, "Trying to open CWBWaveServer");
        e.printStackTrace();
      } catch (UnknownHostException e) {
        Util.SocketIOErrorPrint(e, "Trying to open CWBWaveServer");

      } catch (IOException e) {
        Util.IOErrorPrint(e, "Trying to open CWBWaveServer");

      }
      if (!testmode) {
        break;
      }
    } // for loop on test
    Util.prt("End of execution");
    System.exit(0);
  }

}
