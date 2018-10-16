/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.util;

import java.io.OutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.net.UnknownHostException;
import java.net.Socket;

/**
 * RTSUtilityCommand.java - creates a thread which attempts to keep a socket connected to an IP
 * address passed at creation. This object then has entry points for issuing RTS commands down that
 * socket. This implements both RTS command to the USGS SocketServer and command to the port 4606
 * provided by RedBoot and the SocketServer
 *
 * @author ketchum
 */
public final class RTSUtilityCommand extends Thread {

  Socket s;
  InputStream in;
  OutputStream out;
  StringBuffer text;
  byte[] buf;
  RTSCommand rtscmd;
  String ipadr;
  boolean shutdown = false;

  /**
   * Creates a new instance of RTSUtilityCommand on the give IP address. This routine creates a
   * thread which constantly keeps a socket to the IP address open to the RTS Utility server command
   * port (RTSServer on gldmt). Various methods to this object then can issue commands on this
   * socket to the server which forwards them to the station.
   *
   * @param ip The IP address i
   */
  public RTSUtilityCommand(String ip) {
    ipadr = ip;
    text = new StringBuffer(2000);
    rtscmd = new RTSCommand();
    s = null;
    //connect();
    start();
  }

  /**
   * Close the socket associated with this object and shutdown the connect thread.
   */
  public void close() {
    Util.prta("RTSUC close()");
    shutdown = true;
    try {
      if (s != null) {
        s.close();
      }
    } catch (IOException e) {
    }

  }
  boolean noRTSServer = false;

  private void connect(String tag) {
    if (shutdown || noRTSServer) {
      return;
    }
    int isleep;
    int loop = 0;
    while (true) {
      isleep = 10000;
      if (ipadr.equalsIgnoreCase("") || ipadr.equalsIgnoreCase("none")) {
        Util.prta("RTSUC has no IP.  Wait ip=" + ipadr);

      } else {
        try {
          Util.prta("RTSUC connect try to " + ipadr + "/" + AnssPorts.RTS_UTILITY_COMMAND_PORT + " " + tag);
          text.append(Util.asctime()).append(" attempt connect(").append(ipadr).
                  append(") for RTSServer (port=").append(AnssPorts.RTS_UTILITY_COMMAND_PORT).
                  append(") ").append(tag).append("\n");
          s = new Socket(ipadr, AnssPorts.RTS_UTILITY_COMMAND_PORT);
          in = s.getInputStream();
          out = s.getOutputStream();
          //text.append(Util.asctime()+" connected.\n");
          Util.prta("RTSUC connected to " + ipadr + " " + tag);
          break;
        } catch (UnknownHostException e) {
          Util.prta("RTSUC : connect() no such host =" + ipadr + " " + tag);
          text.append("RTSUC : connect() no such host=").append(ipadr).append(" ").append(tag).append("\n");
          isleep = 300000;
        } catch (IOException e) {
          Util.prta("RTSUC: connect() try=" + loop + " again IOError " + e.getMessage() + " to "
                  + ipadr + "/" + AnssPorts.RTS_UTILITY_COMMAND_PORT + " " + tag);
          text.append(Util.asctime()).append(" try again connect() IOError " + " ").
                  append(tag).append(" ").append(e.getMessage()).append(" - Is RTSServer running?\n");
          loop++;
          isleep = 180000;
          if (loop > 5) {
            Util.prta("RTSUC: connect() failed - return null socket");
            s = null;
            noRTSServer = true;
            return;
          }
        }
      }
      /*      catch (SocketException e) {
        text.append(Util.asctime()+" connect() socket exception "+e.getMessage());
        Util.prta("RTSUC: Connect() socket exception="+e.getMessage());
      }*/
      synchronized (this) {
        try {
          wait(isleep);
        } catch (InterruptedException e) {
        }
      }
    }       // end infinite while
  }

  /**
   * Keeps a socket to the IP address open. If any errors occur, causes a new socket to be made. If
   * the close() routine is called, the shutdown variable is set and the next problem on the socket
   * causes this thread to exit. The target IP is the command server RTSServer normally
   */
  @Override
  public void run() {
    byte[] b = new byte[1024];
    while (true) {
      try {
        if (s == null) {
          connect("Run");
        }
        if (in == null) {
          connect("Run3");
        }
        if (s == null) {
          continue;
        }
        //while(in.available() <= 0) try{sleep(50);} catch(InterruptedException e) {}
        //int len = in.read(b);
        int len = Util.socketRead(in, b, 0, b.length);
        if (len <= 0) {
          if (shutdown) {
            if (s != null) {
              s.close();
            }
            s = null;
            if (in != null) {
              in.close();
            }
            in = null;
            break;
          }
          s = null;        // It must be closed, set to be reopened.
          synchronized (this) {
            try {
              wait(100);
            } catch (InterruptedException e) {
            }
          }
        }
        for (int i = 0; i < len; i++) {
          text.append((char) b[i]);
        }
      } catch (IOException e) {
        if (!e.getMessage().contains("Socket close") && !e.getMessage().contains("closed")) {
          Util.prta("RTSUC run() IOException getting response text=" + e.getMessage());
        }
        try {
          if (s != null) {
            s.close();
          }
          s = null;
          connect("Run");
        } catch (IOException e2) {
          Util.prta("RTSUC run() IOError on close");
          //break;
        }
        try {
          sleep(10000);
        } catch (InterruptedException e2) {
        }
      }
    }
    text.append("RTSUC: run is exiting\n");
  }

  /**
   * There is a StringBuffer which keeps appending information about the progress of command,
   * errors, or other information this routine needs to return to the users of its methods.
   *
   * @return The accumulated messages, errors and status as text
   */
  public StringBuffer getText() {
    return text;
  }

  /**
   * Execute a RTS reset on the specified IP via port 4606 in RedBoot or SocketServer
   *
   * @param rtsip The ip address of the target RTS
   */
  public void rtsReset(String rtsip) {
    command(2, rtsip);
  }

  /**
   * Execute a RTS Download on the specified IP via port 4606 in RedBoot or SocketServer
   *
   * @param rtsip The ip address of the target RTS
   * @param oldBurn
   */
  public void rtsDownload(String rtsip, boolean oldBurn) {
    if (oldBurn) {
      command(12, rtsip);
    } else {
      command(1, rtsip);
    }
  }

  /**
   * Execute a RTS Query on the specified IP via port 4606 in RedBoot or SocketServer
   *
   * @param rtsip The ip address of the target RTS
   */
  public void rtsQuery(String rtsip) {
    command(0, rtsip);
  }

  private void command(int cmd, String rtsip) {
    if (rtsip.isEmpty()) {
      return;
    }
    text.delete(0, text.length());
    if (s == null) {
      connect("command");
    }
    if (s == null) {
      text.append("Command() did not work.  Could not connect to RTSServer\n");
      return;
    }       // could not connect
    byte[] b = new byte[17];
    System.arraycopy((rtsip + "           ").getBytes(), 0, b, 0, 16);
    b[16] = (byte) (cmd & 0xff);
    try {
      out.write(b, 0, 17);
      Util.prta("RTSUC command() write 17");
    } catch (IOException e) {
      Util.prta("RTSUC: command() io error writing command" + e.getMessage());
      Util.prta("RTSUC: close socket and attempt to reopen");
      try {
        if (s != null) {
          s.close();
        }
        s = null;
      } catch (IOException e2) {
        Util.prta("IOexception closing socket");
      }
      text.append("Command() IOException closing socket()\n");
      Util.prta("Attempt to reconnect.");
      connect("command2");
    }
  }

  /**
   * Send a SocketServer reset command to the RTS at IP address given.
   *
   * @param ipadr The IP address of the target RTS
   * @return The string with error information (normally write error to socket)
   */
  public String reset(String ipadr) {
    Util.prta("RTSUC: RTS Reset " + ipadr);
    buf = rtscmd.reset(ipadr);
    return send();
  }

  /**
   * Send a SocketServer consoleBreak command to the RTS at IP address given.
   *
   * @param ipadr The IP address of the target RTS
   * @return The string with error information (normally write error to socket)
   */
  public String consoleBreak(String ipadr) {
    Util.prta("RTSUC: RTS Console Break " + ipadr);
    buf = rtscmd.consoleBreak(ipadr);
    return send();
  }

  /**
   * Send a SocketServer LoopTest command to the RTS at IP address given.
   *
   * @param ipadr The IP address of the target RTS
   * @return The string with error information (normally write error to socket)
   */
  public String loopTest(String ipadr) {
    Util.prta("RTSUC: RTS Loop Test " + ipadr);
    buf = rtscmd.loopTest(ipadr);
    return send();
  }

  /**
   * Send a SocketServer DAS power cycle command to the RTS at IP address given.
   *
   * @param ipadr The IP address of the target RTS
   * @return The string with error information (normally write error to socket)
   */
  public String dasPowerCycle(String ipadr) {
    Util.prta("RTSUC: RTS DAS power cycle " + ipadr);
    buf = rtscmd.dasPowerCycle(ipadr);
    return send();
  }

  /**
   * Send a SocketServer VSAT Power cycle command to the RTS at IP address given.
   *
   * @param ipadr The IP address of the target RTS
   * @return The string with error information (normally write error to socket)
   */
  public String vsatPowerCycle(String ipadr) {
    Util.prta("RTSUC: RTS VSAT power cycle " + ipadr);
    buf = rtscmd.vsatPowerCycle(ipadr);
    return send();
  }

  /**
   * Send a SocketServer Save Config command to the RTS at IP address given.
   *
   * @param ipadr The IP address of the target RTS
   * @return The string with error information (normally write error to socket)
   */
  public String saveConfig(String ipadr) {
    Util.prta("RTSUC: RTS Save Configuration " + ipadr);
    buf = rtscmd.saveConfig(ipadr);
    return send();
  }

  /**
   * Send a SocketServer send Override command to the RTS at IP address given.
   *
   * @param ipadr The IP address of the target RTS
   * @param rate A short with the new output rate limit in baud
   * @return The string with error information (normally write error to socket)
   */
  public String setOverride(String ipadr, short rate) {
    Util.prta("RTSUC: RTS Set Override Baud Rate=" + rate + " " + ipadr);
    buf = rtscmd.setOverride(ipadr, rate);
    return send();
  }

  /**
   * Send a SocketServer USGS Port Type command to the RTS at IP address given.
   *
   * @param ipadr The IP address of the target RTS
   * @param serial The serial port number
   * @param type The USGS type of port
   * @param ip The IP address associated with this serial port
   * @param ipalt The alternate IP address associated with this serial port
   * @param port THe IP port associated with this serial port
   * @param client True, RTS is client and will originate the socket
   * @return The string with error information (normally write error to socket)
   */
  public String setUsgsType(String ipadr, int serial, int type, String ip, String ipalt, int port, boolean client) {
    Util.prta("RTSUC: Set UsgsType " + ipadr + " on rts port=" + serial + " type=" + type + " " + ip + "/" + port + " client=" + client);
    buf = rtscmd.setUsgsType(ipadr, serial, type, ip, ipalt, port, client);
    return send();
  }

  /**
   * Send a SocketServer RTS Mode command to the RTS at IP address given.
   *
   * @param ipadr The IP address of the target RTS
   * @param mode Boolean, if true, the RTS port will start a TCP/IP connect (be a client)
   * @return The string with error information (normally write error to socket)
   */

  public String setRtsMode(String ipadr, boolean mode) {
    Util.prta("RTSUC: RTS RTS Mode=" + mode + " " + ipadr);
    buf = rtscmd.setRtsMode(ipadr, mode);
    return send();
  }

  public String setUseBackupIP(String ipadr, boolean mode) {
    Util.prta("RTSUC: RTS Backup IP =" + mode + " " + ipadr);
    buf = rtscmd.setUseBackupIP(ipadr, mode);
    return send();
  }

  public String setCommandIP(String ipadr, String newip, String newAlt, int port) {
    Util.prta("RTSUC: SetCommandIP " + ipadr + " to " + newip + " alt=" + newAlt + "/" + port);
    buf = rtscmd.setCommandIP(ipadr, newip, newAlt, port);
    return send();
  }

  public String setTunnelIP(String ipadr, String newip, String newAlt, int port) {
    Util.prta("RTSUC: SetTunnelIP " + ipadr + " to " + newip + ":" + newAlt + "/" + port);
    buf = rtscmd.setTunnelIP(ipadr, newip, newAlt, port);
    return send();
  }

  public String setStatusIP(String ipadr, String newip, int port, String station) {
    Util.prta("RTSUC: SetStatusIP " + ipadr + " to " + newip + "/" + port + " Station=" + station);
    buf = rtscmd.setStatusIP(ipadr, newip, port, station);
    return send();
  }

  private String send() {
    if (ipadr.equals("") || ipadr.equalsIgnoreCase("none")) {
      return "";
    }
    text.delete(0, text.length());
    if (s == null) {
      connect("send");
    }
    if (s == null) {
      text.append("Could not connect to RTSServer at ").append(ipadr).append(" skip RTS update.\n");
      return text.toString();
    }
    try {
      out.write(buf);
      Util.prta("RTSUC send() write len=" + buf.length);
      synchronized (this) {
        wait(200);
      }
    } catch (InterruptedException e) {
      Util.prta("RTSUC: send() Interrupted exception");
    } catch (IOException e) {
      text.append("RTSUC:send() Write error on  config command ").append(e.getMessage());
      Util.prta("RTSUC: send() io error writing command" + e.getMessage());
      Util.prta("RTSUC: close socket and attempt to reopen");
      try {
        if (s != null) {
          s.close();
        }
        s = null;
      } catch (IOException e2) {
        Util.prta("IOexception closing socket");
      }
      Util.prta("Attempt to reconnect.");
      connect("send2");
    }
    return text.toString();
  }

  /**
   * Unit test main
   *
   * @param args Command line args[0] should be the address of the RTS, args[1] should be IP to
   * receive serial data
   */
  public static void main(String[] args) {
    RTSUtilityCommand rts = new RTSUtilityCommand("gldketchum.cr.usgs.gov");
    while (true) {
      try {
        int loop = 0;
        while (true) {
          String ret = rts.setUsgsType("localhost", 3, RTSCommand.MSHEAR, args[1], args[1], 2004, true);
          Util.prt("setUSGStype return=" + ret);
          synchronized (rts) {
            rts.wait(3000);
            Util.prt(rts.getText().toString());
            Util.prta("cycle");
          }
          loop++;
          if (loop > 1000) {
            break;
          }
        }
        rts.rtsReset(args[0]);
        synchronized (rts) {
          rts.wait(10000);
          Util.prt(rts.getText().toString());
          Util.prta("Reset Done");
        }
        break;

      } catch (InterruptedException e) {
        Util.prt("Main: wait interruped");
      }
    }
  }
}
