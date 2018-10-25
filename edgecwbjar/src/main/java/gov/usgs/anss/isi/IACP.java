/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.isi;

import gov.usgs.alarm.SendEvent;
import gov.usgs.anss.edgethread.EdgeThread;
import gov.usgs.anss.channelstatus.LatencyClient;
import gov.usgs.anss.seed.MiniSeed;
import gov.usgs.anss.channelstatus.ChannelStatus;
import gov.usgs.anss.util.Util;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;

/**
 * This class manages an ISI style connection. It does the Raw IO and returns
 * packets on the link. Normally this class is used by the ISI class to
 * implement an ISI connection (like to the slate at GSN stations).
 *
 *
 * @author davidketchum
 */
public final class IACP extends Thread {

  // 
  public static final int IACP_TYPE_NULL = 0;
  // Handshake related sub-frames
  public static final int IACP_TYPE_HANDSHAKE = 1;
  public static final int IACP_TYPE_PID = 2;
  public static final int IACP_TYPE_TO = 3;
  public static final int IACP_TYPE_SNDSIZ = 4;
  public static final int IACP_TYPE_RCVSIZ = 5;
  // general IACP I/O (post-handshake) related sub-frames
  public static final int IACP_TYPE_ALERT = 100;
  public static final int IACP_TYPE_NOP = 101;
  public static final int IACP_TYPE_ENOSUCH = 102;

  // Min/maxes of various code types.
  public static final int IACP_TYPE_IACP_MAX = 999;
  public static final int IACP_TYPE_ISI_MIN = 1000;     // Smallest posible ISI code
  public static final int IACP_TYPE_ISI_MAX = 1999;     // Smallest posible ISI code
  // Alert codes used for as IASCP_TYPE_ALERT payloads
  public static final int IACP_EINVAL_UINT32 = 0xffffffff;     // Used to flag an invalid UINT32
  public static final int IACP_ALERT_NONE = 0;              // Never sent
  public static final int IACP_ALERT_DISCONNECT = 1;        // normal disconnect
  public static final int IACP_ALERT_REQUEST_COMPLETE = 2;  //request complted 
  public static final int IACP_ALERT_IO_ERROR = 3;          // i/o error
  public static final int IACP_ALERT_SERVER_FAULT = 4;      // server error
  public static final int IACP_ALERT_SERVER_BUSY = 5;       // server has too may active connections
  public static final int IACP_ALERT_FAILED_AUTH = 6;       // frame signature failed to verify
  public static final int IACP_ALERT_ACCESS_DENIED = 7;     // access to server refused
  public static final int IACP_ALERT_REQUEST_DENIED = 8;    //client request refused
  public static final int IACP_ALERT_SHUTDOWN = 9;          // shutdown in progress
  public static final int IACP_ALERT_PROTOCOL_ERROR = 10;   // illegal frame received
  public static final int IACP_ALERT_ILLEGAL_DATA = 11;     // unexpeced frame data
  public static final int IACP_ALERT_UNSUPPORTED = 12;      // unsupported IACP frame type
  public static final int IACP_ALERT_OTHER_ERROR = 99;      // some other error
  public static final String[] alerts = {"NONE", "NORMAL DISCONNECT", "REQUEST COMPLETE", "IO ERROR ALERT", "SERVER FAULT",
    "SERVER BUSY", "FAILED AUTHORIZATION", "ACCESS DENIED", "REQUEST DENIED", "SHUTDOWN ALERT", "PROTOCOL ERROR",
    "ILLEGAL DATA", "UNSUPPORTED IACP FRAME"};
  // error level associated with I/O failures
  public static final int IACP_ERR_NONE = 0;
  public static final int IACP_ERR_TRANSIENT = 1;
  public static final int IACP_ERR_NON_FATAL = 2;
  public static final int IACP_ERR_FATAL = 3;
  // Limits 
  public static final int IACP_DATA_PREAMBLE_LEN = 16;      // IACP+seqno+type+len
  public static final int IACP_SIG_PREAMBLE_LEN = 8;        // keyid + len

  public static final int IACP_MAX_PREAMBLE_LEN = 16;
  public static final int IACP_MINTIMEO = 30000;            // 30 seconds minitimum io timeout with server
  public static final int IACP_MAXSIGLEN = 40;              // max DSS signature length
  public static final int IACP_ = 0;

  // Data associated with this connection
  private final ByteBuffer buf;       // input buffer
  private final byte[] b;            // Storage for input buffer
  private final ByteBuffer cmd;       // Output command wrapper
  private final byte[] cmdbuf = new byte[1024];
  private Socket d;             // The socket connection
  private final String host;          // The server as IP address or dotted address
  private final int port;             // Server port number 
  private int frameSeq;
  private final int pid;
  private int authKeyID;
  private int authSize;
  private int timeout;
  private final String bind;
  private byte[] authBytes;
  private byte[] isiPayload;
  private final String station;
  private int sndsiz;
  private int rcvsiz;
  private final EdgeThread par;
  private String tag;
  private ISI isiObject;        // Something to call back with ISI payloads
  private boolean terminate;
  private boolean running;
  private boolean connected;
  private long lastStatus;
  private int throttleRate;
  private final int maxThrottleRate;
  private long inbytes;
  private long lastLatencyCheck;
  private int lastLatency;
  private LatencyClient latencyClient;

  private InputStream in;
  private OutputStream out;

  public void terminate() {
    terminate = true;
    interrupt();
  }

  public void setISI(ISI obj) {
    if (isiPayload == null) {
      isiPayload = new byte[4096];
    }
    isiObject = obj;
  }

  public boolean isConnected() {
    return connected;
  }

  /**
   * Creates a new instance of IACP
   *
   * @param h host string to contact
   * @param p Port on that host with a ISI server
   * @param stat The station or more practically the loop name
   * @param procid The process id to use to set up the IACP
   * @param to The time out per ISI manual for reconnects/heartbeats for the
   * IACP
   * @param sndsize The buffer send size for the IACP
   * @param rcvsize The buffer rcv size for the IACP
   * @param throttle The throttle b/s to be enforced by the IACP
   * @param bind Bind IP address (or null for none)
   * @param parent The parent to log through.
   */
  public IACP(String h, int p, String stat, int procid, int to, int sndsize,
          int rcvsize, int throttle, String bind, EdgeThread parent) {
    host = h;
    port = p;
    pid = procid;
    timeout = to;
    par = parent;
    station = stat;
    sndsiz = sndsize;
    rcvsiz = rcvsize;
    this.bind = bind;
    throttleRate = throttle;
    maxThrottleRate = throttleRate;
    b = new byte[4096];
    frameSeq = 2;
    buf = ByteBuffer.wrap(b);                 // a byte buffer to build stuff in
    cmd = ByteBuffer.wrap(cmdbuf);
    tag = "[" + station + "]:";
    par.prta(tag + "create IACP to " + h + "/" + p + " pid=" + pid + " to=" + to + " throttle=" + throttle
            + " max=" + maxThrottleRate + " rcvsiz=" + rcvsiz + " bind=" + bind);

    gov.usgs.anss.util.Util.prta("new ThreadStation " + getName() + " " + getClass().getSimpleName() + " stat=" + stat + " " + h + "/" + p);
    start();
  }

  public void resetLink() {
    if (d != null) {
      if (!d.isClosed()) {
        try {
          par.prta(tag + "IACP: resetLink() close socket");
          if (in != null) {
            in.close();
          }
          if (out != null) {
            out.close();
          }
          d.close();
        } catch (IOException e) {
          par.prta(tag + "IACP: resetLink() IOError closing socket=" + e);
        }
      } else {
        par.prt(tag + "IACP: resetLink() socket was already closed.");
      }
    } else {
      par.prt(tag + "IACP: resetLink() do nothing - no socket or it is not connected");
    }

    d = null;
    try {
      sleep(1000);
    } catch (InterruptedException expected) {
    }
    //connected=false;
  }

  public void sendNull() throws IOException {
    sendCommand(IACP_TYPE_NULL, cmdbuf, 0);
  }

  public void sendCommand(int payloadID, byte[] pay, int len) throws IOException {
    setHeader(payloadID, len);
    if (len > 0) {
      cmd.put(pay, 0, len);
    }
    setSignature();
    if (d != null) {
      d.getOutputStream().write(cmdbuf, 0, cmd.position());
    }
  }

  private void setHeader(int type, int len) {
    cmd.position(0);
    cmd.put("IACP".getBytes());
    cmd.putInt(frameSeq++);
    cmd.putInt(type);
    cmd.putInt(len);
  }

  private void setSignature() {
    cmd.putInt(authKeyID);
    cmd.putInt(authSize);
    if (authSize > 0) {
      cmd.put(authBytes);
    }
  }

  @Override
  public void run() {
    MiniSeed ms = null;
    int nillegal = 0;
    running = true;
    int iday = 0;       // mark we are running
    long isleep = 15000;
    while (true) {
      try {
        connected = false;
        if (terminate) {
          break;
        }
        /* keep trying until a connection is made */
        int loop = 0;
        while (true) {
          if (maxThrottleRate < 100 && maxThrottleRate > 0) {
            double latency = -1.;
            try {
              if (latencyClient == null) {
                par.prta("Create Latency Client to " + Util.getProperty("StatusServer") + "/" + 7956);
                latencyClient = new LatencyClient(Util.getProperty("StatusServer"), 7956);
              }
              ChannelStatus cs = latencyClient.getStation((station + "   ").substring(0, 7));
              if (cs != null) {
                latency = cs.getLatency();
              }
            } catch (IOException e) {
              latencyClient = null;
            }
            par.prta(tag + " Gap socket disabled throttle < 100 = " + maxThrottleRate + " real time latency=" + latency);
            try {
              sleep(120000);
            } catch (InterruptedException expected) {
            }
            continue;
          }
          if (terminate) {
            break;
          }
          try {
            // Make sure anything we have open can be let go
            if (d != null) {
              try {
                if (!d.isClosed()) {
                  d.close();
                }
                if (in != null) {
                  in.close();
                }
                if (out != null) {
                  out.close();
                }
              } catch (IOException expected) {
              }
            }
            par.prta(tag + " Open Port=" + host + "/" + port + " rcvsize=" + rcvsiz + " throttle=" + throttleRate + " max=" + maxThrottleRate + " bind=" + bind);

            InetSocketAddress adr = new InetSocketAddress(host, port);
            if (d != null) {
              if (!d.isClosed()) {
                try {
                  par.prta(tag + "NBS:close existing socket s=" + d);
                  d.close();
                } catch (IOException expected) {
                }
              }
            }
            d = new Socket();
            if (bind != null) {
              if (!bind.equals("")) {
                d.bind(new InetSocketAddress(bind, 0));  // User specified a bind address so bind it to this local ip/ephemeral port
              }
            }
            if (rcvsiz > 0) {
              d.setReceiveBufferSize(rcvsiz);    // small recsize make throttling work better
            }
            par.prta(tag + " Do connect to " + adr);
            if (d == null) {
              try {
                sleep(15000);
              } catch (InterruptedException expected) {
              }
              continue;
            }  // sometimes the resetLink gets called on open and this is null
            d.connect(adr);
            par.prta(tag + " Socket is now open rcv=" + d.getReceiveBufferSize() + " local port=" + d.getLocalPort() + " localip=" + d.getLocalAddress());
            in = d.getInputStream();        // Get input and output streams
            out = d.getOutputStream();

            // Build first 100 StatusInfo objects and fill with empty data
            lastStatus = System.currentTimeMillis();
            connected = true;
            break;
          } catch (UnknownHostException e) {
            par.prt(tag + " Host is unknown=" + host + "/" + port + " loop=" + loop);
            if (loop % 30 == 1) {
              SendEvent.edgeSMEEvent("HostUnknown", "ISI host unknown=" + host, this);
            }
            loop++;
            try {
              sleep(120000L);
            } catch (InterruptedException expected) {
            }
          } catch (IOException e) {
            if (e.getMessage().equalsIgnoreCase("Connection refused")) {
              par.prta(tag + " Connection refused.  wait " + isleep / 1000 + " secs ....");
              try {
                sleep(isleep);
              } catch (InterruptedException expected) {
              }
              loop++;
              isleep = isleep * 2;
              if (isleep > 300000) {
                isleep = 300000;
              }
              if (isleep >= 300000) {
                SendEvent.debugSMEEvent("ISIConnect", tag + " ISI " + host + "/" + port + " repeatedly refused", this);
              }
            } else if (e.getMessage().equalsIgnoreCase("Connection timed out")) {
              isleep = isleep * 2;
              if (isleep > 900000) {
                isleep = 900000;
                SendEvent.debugSMEEvent("ISITimeOut", tag + " ISI " + host + "/" + port + " has timed out.", this);
              }
              par.prta(tag + " Connection timed out.  wait " + isleep / 1000 + " secs ....");
              try {
                sleep(isleep);
              } catch (InterruptedException expected) {
              }
            } else if (e.getMessage().contains("ocket close") || e.getMessage().contains("roken pipe")) {
              par.prta(tag + " Socket has closed or broken pipe.   Try to reopen after 15 ..." + e.getMessage());
              try {
                sleep(15000L);
              } catch (InterruptedException expected) {
              }
            } else {
              par.prta(tag + " IO error opening socket.  sleep 2 min." + host + "/" + port + " e=" + e);
              if (e.toString().contains("No route")) {
                SendEvent.debugSMEEvent("ISINoRoute", tag + "No route to host " + host + "/" + port, this);
              } else {
                Util.IOErrorPrint(e, tag + " IO error opening socket=" + host + "/" + port);
                if (!tag.contains("XX")) {
                  SendEvent.debugSMEEvent("ISIOpenErr", tag + " to " + host + "/" + port + " e=" + e, this);
                }
              }
              try {
                sleep(120000L);
              } catch (InterruptedException expected) {
              }
            }

          }

          //try {sleep(10000L);} catch (InterruptedException expected) {}
        }   // While True on opening the socket
        // Send the IACP Handshake packet
        buf.position(0);
        buf.putInt(4);
        buf.putInt(IACP_TYPE_PID);
        buf.putInt(4);
        buf.putInt(pid);
        buf.putInt(IACP_TYPE_TO);
        buf.putInt(4);
        buf.putInt(timeout);
        buf.putInt(IACP_TYPE_SNDSIZ);
        buf.putInt(4);
        buf.putInt(sndsiz);
        buf.putInt(IACP_TYPE_RCVSIZ);
        buf.putInt(4);
        buf.putInt(rcvsiz);
        par.prta(tag + "Send Handshake connected=" + connected);
        try {
          sendCommand(IACP_TYPE_HANDSHAKE, b, buf.position());
        } catch (IOException e) {    // IO exception setting up socket
          Util.SocketIOErrorPrint(e, "Setting up socket/sending handshake", par.getPrintStream());
          continue;               // This will start up the socket again
        }
        /*try {
          int lhdr = readFully(b,0, IACP_DATA_PREAMBLE_LEN);
          buf.position(12);
          int hdrlen=buf.getInt();
          int hdrbody = readFully(b,IACP_DATA_PREAMBLE_LEN, hdrlen+8);
        }
        catch(IOException e) {
          par.prta("Got IOError reading back Handshake");
          continue;
        }*/

        isiObject.isiConnection();
        if (terminate) {
          break;
        }
        par.prta(tag + " local=" + d.getLocalPort() + " Socket is opened.  Start reads. throttle=" + throttleRate);
        tag = tag.substring(0, tag.indexOf(":") + 1) + d.getLocalPort() + " ";
        par.prta(tag + " is now changed!");
        // Read data from the socket and update/create the list of records 
        int len;
        int alen;
        int aID = 0;
        int payloadID;
        int iacp;
        int seq;
        int l;
        //int nchar=0;
        //int nsamp=0;
        //int nsleft=0;
        //int blockSize=0;
        //int [] data;      // Need to decompress the data
        long lastInbytes = inbytes;
        long lastThrottleMS = System.currentTimeMillis();
        lastLatencyCheck = lastThrottleMS;
        int maxavailable = 1024;
        long now;
        while (true) {
          if (throttleRate > 100 && inbytes - lastInbytes >= 2048) {    // If throttle rate == 0, no throttle
            now = System.currentTimeMillis();
            l = (int) ((inbytes - lastInbytes) * 8000 / throttleRate);    // Milliseconds it should take to send these bytes
            if (l > (now - lastThrottleMS)) {
              par.prta(tag + " *Wait " + (l - now + lastThrottleMS) + " ms throttle=" + throttleRate + " #b=" + (inbytes - lastInbytes));
              try {
                sleep(l - now + lastThrottleMS);
              } catch (InterruptedException expected) {
              }
            }
            lastThrottleMS = System.currentTimeMillis();
            lastInbytes = inbytes;
            // Now check on the Latency.  If it is above the minimum and trending up, we need to back down the throttle
            if (lastThrottleMS - lastLatencyCheck > 120000) {
              lastLatencyCheck = lastThrottleMS;
              if (latencyClient == null) {
                par.prta("Create Latency Client to " + Util.getProperty("StatusServer") + "/" + 7956);
                latencyClient = new LatencyClient(Util.getProperty("StatusServer"), 7956);
              }
              if (latencyClient != null) {
                try {
                  int old = throttleRate;
                  ChannelStatus cs = latencyClient.getStation((station + "   ").substring(0, 7));
                  if (cs != null) {
                    par.prta("Latency=" + cs.getLatency() + " old latency=" + lastLatency);

                    if (cs.getLatency() > 30.) {
                      if (cs.getLatency() > lastLatency) {      // Need to slow down
                        throttleRate = throttleRate * 3 / 4;
                        if (throttleRate < maxThrottleRate / 2) {    // This code cause a disconnection of the link until real time data catches up
                          SendEvent.debugSMEEvent("ISIThrotDCON", "Throttle at disconn for " + station + " lat=" + cs.getLatency(), this);
                          par.prta("Throttle at disconnenct for " + station + " lat=" + cs.getLatency());
                          try {
                            connected = false;
                            d.close();      // Disconnect
                          } catch (IOException e) {
                            par.prta("Disconnect from Gap socket at low rate has caused io error=" + e);
                          }
                          for (;;) {
                            try {
                              sleep(120000);
                            } catch (InterruptedException expected) {
                            }
                            double latency = 10.;
                            cs = latencyClient.getStation((station + "   ").substring(0, 7));
                            if (cs != null) {
                              latency = cs.getLatency();
                            }
                            par.prta(tag + " Gap socket in loop waiting for realtime to clear " + latency + " throt=" + throttleRate + " max=" + maxThrottleRate);
                            if (latency < 15 && (latency < lastLatency || latency < 5)) {    // We did not reestablish the connection
                              break;
                            }
                            lastLatency = (int) latency;
                          }
                          par.prta(tag + " Gap socket code realtime has caught up, make new connection");
                          throttleRate = maxThrottleRate / 2;
                          break;
                        }
                        if (throttleRate < 100) {
                          throttleRate = 100;
                          SendEvent.debugSMEEvent("ISIThrotMin", "Throttle at min for " + station + " lat=" + cs.getLatency(), this);
                        }
                      }
                    } else {
                      if (cs.getLatency() < 15. && (cs.getLatency() < lastLatency || cs.getLatency() < 5)) {
                        throttleRate = throttleRate * 5 / 4;  // It o.k. and decreasing
                      }
                      if (throttleRate > maxThrottleRate) {
                        throttleRate = maxThrottleRate;
                      }
                    }
                    if (throttleRate != old) {
                      par.prta(tag + " throttle adjust to " + throttleRate + " from " + old
                              + " lat=" + cs.getLatency() + " lastLat=" + lastLatency + " max=" + maxThrottleRate);
                    }
                    lastLatency = (int) cs.getLatency();
                  }
                } catch (IOException e) {
                  par.prta(tag + "Could not get latency for station=" + station);
                }
              } else {
                par.prta("NO latency available from latency Client.");
              }
            }
          }
          try {
            if (in.available() > maxavailable) {
              par.prta("Max available=" + in.available() + " thr=" + throttleRate);
              maxavailable = in.available();
            }
            l = readFully(b, 0, IACP_DATA_PREAMBLE_LEN);  // Get the IACP header
            if (l == 0) {
              par.prta("EOF from preamble read.  Try to remake connection");
              break;
            }
            if (terminate) {
              break;
            }
            buf.position(0);
            iacp = buf.getInt();      // This should be 'IACP'
            seq = buf.getInt();       // This is the frame sequence
            payloadID = buf.getInt();   // This is the payload ID
            len = buf.getInt();       // This is the length
            //par.prt("iacp="+Util.toHex(iacp)+" seq="+seq+" payid="+payloadID+" len="+len);
            l = readFully(b, IACP_DATA_PREAMBLE_LEN, len + 8);  // Get the payload+ auth code
            if (l == 0) {
              par.prta("EOF from data read.  Try to remake connection");
              break;
            }
            if (terminate) {
              break;
            }
            buf.position(16 + len);
            aID = buf.getInt();
            alen = buf.getInt();
            if (alen > 0) {
              l = readFully(b, len + 24, alen);
              par.prta("Got some authentication payload - Not implemented len=" + alen);
            }
            if (terminate) {
              break;
            }
          } catch (IOException e) {
            if (terminate) {
              break;
            }
            if (e.toString().contains("Bad file desc")) {
              par.prt("IACP: Error got bad file descriptor - monitor probably went off");
            } else if (e.toString().contains("closed")) {
              par.prt("IACP: Error got closed on socket read - monitor may have gone off");
            } else {
              Util.SocketIOErrorPrint(e, "Getting data from IACP socket", par.getPrintStream());
            }
            break;                    // leave the read loop, try to reestablish
          }
          if (terminate) {
            break;
          }
          switch (payloadID) {
            case IACP_TYPE_HANDSHAKE:
              buf.position(IACP_DATA_PREAMBLE_LEN);
              int nparam = buf.getInt();
              for (int i = 0; i < nparam; i++) {
                int val = buf.getInt();
                int paylen = buf.getInt();
                if (paylen != 4) {
                  par.prta("Got unusual Payload Length in handshake=" + paylen);
                }
                switch (val) {
                  case IACP_TYPE_PID:
                    par.prta(tag + " HANDSHAKE PID is " + buf.getInt());
                    break;
                  case IACP_TYPE_TO:
                    timeout = buf.getInt();
                    par.prta(tag + " HANDSHAKE Timeout is " + timeout);
                    break;
                  case IACP_TYPE_SNDSIZ:
                    sndsiz = buf.getInt();
                    par.prta(tag + " HANDSHAKE SEND SIZE=" + sndsiz);
                    break;
                  case IACP_TYPE_RCVSIZ:
                    rcvsiz = buf.getInt();
                    par.prta(tag + " HANDSHAKE RCV SIZE=" + sndsiz);
                    break;
                  default:
                    par.prta(" ***** UNKNOWN handshake arg " + val);
                    break;
                }
              }
              break;
            case IACP_TYPE_NULL:
              par.prta(tag + " Got a NULL frame! l=" + l + " len=" + (len + 16) + " " + Util.toHex(iacp) + " sq=" + seq);
              break;
            case IACP_TYPE_ALERT:
              par.prt("iacp=" + Util.toHex(iacp) + " seq=" + seq + " payid=" + payloadID + " len=" + len + " alen=" + alen);
              if (len >= 4) {
                buf.position(IACP.IACP_DATA_PREAMBLE_LEN);
                int code = buf.getInt();
                par.prta(tag + " GOT ALERT of type =" + code + " " + (code < alerts.length ? alerts[code] : "UNSPECIFIED"));
                isiObject.isiAlert(code, (code < alerts.length ? alerts[code] : "UNSPECIFIED"));
              } else {
                par.prta(tag + " GOT ALERT with no payload! len=" + (len + 16));
              }
              break;
            case IACP_TYPE_NOP:
              par.prta(tag + " Got heartbeat len=" + (len + 16));
              isiObject.isiHeartbeat();
              break;
            case IACP_TYPE_ENOSUCH:
              par.prt(tag + " Got a NOSUCH IACP frame len=" + (len + 16));
              break;
            default:              // THis is probably an ISI frame
              if (payloadID >= IACP_TYPE_ISI_MIN && payloadID <= IACP_TYPE_ISI_MAX) {
                if (isiObject != null) {
                  //System.arraycopy(b, IACP_DATA_PREAMBLE_LEN, isiPayload, 0, len);
                  isiObject.doISI(payloadID, buf, len);
                  break;
                } else {
                  par.prta("Got an unknown payload ID of " + payloadID);
                  break;
                }
              }
          }
        }           // While socket is still connected, read loop
      } catch (RuntimeException e) {
        par.prta(tag + "IACP: RuntimeException go to socket open.  in " + this.getClass().getName() + " e=" + e.getMessage());
        if (par.getPrintStream() != null) {
          e.printStackTrace(par.getPrintStream());
        } else {
          e.printStackTrace();
        }
        if (e.getMessage() != null) {
          if (e.getMessage().contains("OutOfMemory")) {
            SendEvent.doOutOfMemory(tag, this);
            throw e;
          }
        }
      }
      par.prta("Out of read loop.  connected=" + connected + " iClosed=" + (d == null ? "Null" : "" + d.isClosed()));

      try {
        sleep(1000);
      } catch (InterruptedException expected) {
      } // wait a bit before trying to reopen the socket.
      isleep = 15000;
    }       // while(true) do socket open
    if (!d.isClosed()) {
      try {
        d.close();
      } catch (IOException expected) {
      }
    }
    d = null;
    par.prt(tag + " is terminated.");
    running = false;
    terminate = false;
  }
  static int dbgloop;

  private int readFully(byte[] buf, int off, int len) throws IOException {
    int nchar;
    int l = off;
    while (len > 0) {

      //while(in.available() <= 0) {try{sleep(10);} catch(InterruptedException e) {if(terminate) return 0;}}
      nchar = in.read(buf, l, len);// get nchar
      if (nchar <= 0) {
        par.prta("512 read nchar=" + nchar + " len=" + len + " in.avail=" + in.available());
        if (l > 0) {
          return l;   // If ther are bytes to process, return them
        }
        return 0;             // EOF - User should close up
      }
      l += nchar;               // update the offset
      len -= nchar;             // reduce the number left to read
    }
    dbgloop++;
    //if(dbgloop%100 == 0) throw new IOException("Debug exception");
    inbytes += l;
    return l;
  }

}
