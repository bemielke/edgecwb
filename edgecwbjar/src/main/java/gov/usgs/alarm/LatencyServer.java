/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.alarm;

import gov.usgs.anss.channelstatus.ChannelStatus;
import gov.usgs.anss.util.Util;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.Iterator;

/**
 * This server sends a string channel information for a station or all stations
 * to any client. The input can be a 7 character station name (this software
 * will find the likely vertical for this station, a 12 character NNSSSSSCCCLL
 * for a single channel, or '*' to get a dump of all of the stations. The binary
 * data returned is in ChannelStatus order.
 *
 * @author davidketchum
 */
public final class LatencyServer extends Thread {

  private int port;
  private ServerSocket d;
  private int totmsgs;
  private boolean terminate;

  public void terminate() {
    terminate = true;
    interrupt();
    try {
      if (d != null && !d.isClosed()) {
        d.close();
      }
    } // cause the termination to begin
    catch (IOException expected) {
    }
  }

  public int getNumberOfMessages() {
    return totmsgs;
  }

  /**
   * Creates a new instance of EdgeStatusServers. Note this uses UdpChannel
   * static members to get to the SNWChannelClient and StatusSocketReader
   *
   * @param porti The port to listen to
   */
  public LatencyServer(int porti) {
    port = porti;
    terminate = false;
    // Register our shutdown thread with the Runtime system.
    Runtime.getRuntime().addShutdownHook(new ShutdownLatencyServer(this));

    start();
  }

  @Override
  public void run() {
    boolean dbg = false;
    StringBuilder sb = new StringBuilder(10000);

    // OPen up a port to listen for new connections.
    while (true) {
      try {
        //server = s;
        if (terminate) {
          break;
        }
        Util.prt(Util.asctime() + " LS: Open Port=" + port);
        d = new ServerSocket(port);
        break;
      } catch (SocketException e) {
        if (e.getMessage().equals("Address already in use")) {
          try {
            Util.prt("LS: Address in use - try again.");
            Thread.sleep(60000);
            port -= 100;
          } catch (InterruptedException Expected) {
          }
        } else {
          Util.prt("LS:Error opening TCP listen port =" + port + "-" + e.getMessage());
          try {
            Thread.sleep(60000);
          } catch (InterruptedException Expected) {
          }
        }
      } catch (IOException e) {
        Util.prt("LS:Error opening socket server=" + e.getMessage());
        try {
          Thread.sleep(2000);
        } catch (InterruptedException Expected) {
        }
      }
    }
    Socket s = null;
    //TreeMap<String, byte []> lastLatency = UdpChannel.snwcc.latencyMap();
    Object[] msgs;
    byte[] b = new byte[12];
    ChannelStatus cs = null;
    ChannelStatus csbhz;
    ChannelStatus csshz;
    ChannelStatus cshhz;
    ChannelStatus csehz;
    while (true) {
      if (terminate) {
        break;
      }
      try {
        // Each connection (accept) format up the key-value pairs and then close the socket
        s = accept();
        if (terminate) {
          if (!s.isClosed()) {
            s.close();
          }
          break;
        }
        int len = s.getInputStream().read(b, 0, 12);
        Util.prta("LS: len=" + len + " " + (len > 0 ? new String(b, 0, len) : "Empty") + "| " + s.getInetAddress());
        if (len == 12) {
          String seedname = new String(b);
          int nmsgs = UdpChannel.snwcc.getStatusSocketReader().length();
          msgs = UdpChannel.snwcc.getStatusSocketReader().getObjectArray();
          if (UdpChannel.snwcc.getStatusSocketReader().length() > 0) {
            for (int i = 0; i <= nmsgs; i++) {
              if (((ChannelStatus) msgs[i]).getKey().equals(seedname)) {
                cs = (ChannelStatus) msgs[i];
                Util.prt("LS:" + seedname + "->" + cs.toString());
                s.getOutputStream().write(cs.getData(), 0, ChannelStatus.getMaxLength());
                break;
              }
            }
          }
        } else if (len == 7) {
          String station = new String(b, 0, 7);
          int nmsgs = UdpChannel.snwcc.getStatusSocketReader().length();
          msgs = UdpChannel.snwcc.getStatusSocketReader().getObjectArray();
          cs = null;
          csbhz = null;
          cshhz = null;
          csshz = null;
          csehz = null;
          if (UdpChannel.snwcc.getStatusSocketReader().length() > 0) {
            for (int i = 0; i <= nmsgs; i++) {
              if (((ChannelStatus) msgs[i]).getKey().substring(0, 7).equals(station)) {
                cs = (ChannelStatus) msgs[i];
                if (cs.getKey().substring(7, 10).equals("BHZ")) {
                  csbhz = cs;
                  break;
                }
                if (cs.getKey().substring(7, 10).equals("HHZ")) {
                  cshhz = cs;
                }
                if (cs.getKey().substring(7, 10).equals("SHZ")) {
                  csshz = cs;
                }
                if (cs.getKey().substring(7, 10).equals("EHZ")) {
                  csehz = cs;
                }

              }
            }
            // Send out the highest ranking signal
            if (csbhz != null) {
              s.getOutputStream().write(csbhz.getData(), 0, ChannelStatus.getMaxLength());
              Util.prt("LS: cs=" + csbhz);
            } else if (csehz != null) {
              s.getOutputStream().write(csehz.getData(), 0, ChannelStatus.getMaxLength());
              Util.prt("LS: cs=" + csehz);
            } else if (csshz != null) {
              Util.prt("LS: cs=" + csshz);
              s.getOutputStream().write(csshz.getData(), 0, ChannelStatus.getMaxLength());
            } else if (cshhz != null) {
              Util.prt("LS: cs=" + cshhz);
              s.getOutputStream().write(cshhz.getData(), 0, ChannelStatus.getMaxLength());
            }
          }
          /*if(UdpChannel.snwcc != null)  {
            String station= new String(b,0,7);
            byte [] b2 = UdpChannel.snwcc.latencyMap().get(station);
            if(b2 != null) {
              ChannelStatus cs2 = new ChannelStatus(b2);
              Util.prt("LS:"+station+"->"+cs2);
              s.getOutputStream().write(b2, 0, ChannelStatus.getMaxLength());
            }
          }*/
        } else if (len == 1 && b[0] == '*') {
          Iterator itr = UdpChannel.snwcc.latencyMap().keySet().iterator();

          while (itr.hasNext()) {
            byte[] b2 = UdpChannel.snwcc.latencyMap().get((String) itr.next());
            if (cs == null) {
              cs = new ChannelStatus(b2);
            } else {
              cs.reload(b2);
            }
            s.getOutputStream().write((cs.toString() + "\n").getBytes());
          }
        } else {
          Util.prta("LS: input messages is not 7 or 12=" + len);
        }
        //prta("LatencyServer: wrote "+sb.length()+" #msg="+totmsgs);
        s.close();
      } catch (IOException e) {
        Util.SocketIOErrorPrint(e, "LS: during accept or dump back");
        if (s != null) {
          try {
            s.close();
          } catch (IOException expected) {
          }
        }
      }
    }       // end of infinite loop (while(true))
    //Util.prt("Exiting EdgeStatusServers run()!! should never happen!****\n");
    try {
      if (!d.isClosed()) {
        d.close();
      }
    } catch (IOException expected) {
    }
    Util.prt("LS:read loop terminated");
  }

  public Socket accept() throws IOException {
    return d.accept();
  }

  private class ShutdownLatencyServer extends Thread {

    LatencyServer thr;

    public ShutdownLatencyServer(LatencyServer t) {
      thr = t;

    }

    /**
     * this is called by the Runtime.shutdown during the shutdown sequence cause
     * all cleanup actions to occur
     */
    @Override
    public void run() {
      thr.terminate();
      Util.prta("LatencyServer: Shutdown started");
    }
  }
}
