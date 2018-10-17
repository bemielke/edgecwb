/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.util;

/*
 * RTSConfigUDP.java
 *
 * Created on DJanuary 13, 2004, 11:42 AM
 */

/**
 * This class implements UDP commands to the RTSServer which relays the command to an RTS. This
 * allows client programs to do the UDP commands when they might not route correctly over the VSAT
 * because of the private connections. Most of the RTS based udp commands are implemented.
 *
 * @author ketchum
 */
import java.net.DatagramSocket;
import java.net.DatagramPacket;
import java.net.InetAddress;

import java.io.*;

public final class RTSConfigUDP {

  static int USGS_CMD_RESET = 3, USGS_CMD_CONSOLE_BREAK = 1, USGS_CMD_CONFIG_PORTS = 2;
  static int USGS_CMD_LOOP_TEST = 4, USGS_CMD_QUANTERRA_POWER_CYCLE = 5;
  static int USGS_CMD_VSAT_POWER_CYCLE = 6, USGS_CMD_MAX_RATE_OVERRIDE = 7;
  static int USGS_CMD_SET_STATUS_IP = 8, USGS_CMD_SET_SERIAL_CONFIG = 9;
  static int USGS_CMD_SET_USGS_TYPE = 10, USGS_CMD_SAVE_SERIAL_CONFIG = 11;
  static int USGS_CMD_EXECUTE_COMMAND = 12, USGS_CMD_SET_RTS_MODE = 13;
  static int USGS_CMD_SET_COMMAND_IP = 14;
  static int USGS_CMD_RESET_VSAT_WATCHDOG = 15;
  public static int RTS = 0, CONSOLE = 1, DATA = 2, GPS = 3, MSHEAR = 4;

  DatagramSocket ss;
  DatagramPacket p;
  byte[] buf;
  int offset;
  RTSCommand rtscmd;

  /**
   * Creates a new instance of RTSConfigUDPr
   *
   * @param host The host to send the datagram
   */
  public RTSConfigUDP(String host) {
    try {
      ss = new DatagramSocket();
      buf = new byte[200];
      p = new DatagramPacket(buf, 200, InetAddress.getByName(host), 7999);
      rtscmd = new RTSCommand();

    } catch (IOException e) {
      Util.prt("Cannot set up socket server on port 7999");
      e.printStackTrace();
      System.exit(10);
    }
  }

  /**
   * reset(ipadr) the RTS
   *
   * @param ipadr The ip address of the RTS to reset
   */
  public void reset(String ipadr) {
    buf = rtscmd.reset(ipadr);
    send();
  }

  public void consoleBreak(String ipadr) {
    buf = rtscmd.consoleBreak(ipadr);
    send();
  }

  public void loopTest(String ipadr) {
    buf = rtscmd.loopTest(ipadr);
    send();
  }

  public void dasPowerCycle(String ipadr) {
    buf = rtscmd.dasPowerCycle(ipadr);
    send();
  }

  public void vsatResetWatchdog(String ipadr, int usebh) {
    buf = rtscmd.vsatResetWatchdog(ipadr);
    //Util.prta("set buf="+buf.length);
    send();
    Util.sleep(1000);
    buf = rtscmd.setUseBackupIP(ipadr, (usebh != 0));
    send();
  }

  public void vsatPowerCycle(String ipadr) {
    buf = rtscmd.vsatPowerCycle(ipadr);
    send();
  }

  public void saveConfig(String ipadr) {
    buf = rtscmd.saveConfig(ipadr);
    send();
  }

  public void setOverride(String ipadr, short rate) {
    buf = rtscmd.setOverride(ipadr, rate);
    send();
  }

  public void setUsgsType(String ipadr, int serial, int type, String ip, String altip, int port, boolean client) {
    buf = rtscmd.setUsgsType(ipadr, serial, type, ip, altip, port, client);
    send();
  }

  public void setRtsMode(String ipadr, boolean mode) {
    buf = rtscmd.setRtsMode(ipadr, mode);
    send();
  }

  private void send() {
    //Util.prt("Send len="+buf[21]+" offset="+offset);
    try {
      p.setData(buf, 0, buf.length);
      //p.setData(buf, 0, offset);
      ss.send(p);
    } catch (NullPointerException | IOException e) {
      Util.prt("RTSConfigUDP.send : " + e.getMessage());
    }
  }

  // This main displays the form Pane by itself
  public static void main(String args[]) {
    Util.init();
    RTSConfigUDP rts = new RTSConfigUDP("gacq1");
    Util.prta("Reset");
    String ipadr = "";
    boolean reset = false;
    if (args.length < 2) {
      Util.prt("Usage : RTSConfigUDP -r ip.adr # reset the given ip address");
      System.exit(0);
    }
    for (int i = 0; i < args.length; i++) {
      if (args[i].equals("-r")) {
        reset = true;
        ipadr = args[i + 1];
      }

    }
    if (reset) {
      rts.reset(ipadr);
    }
  }
}
