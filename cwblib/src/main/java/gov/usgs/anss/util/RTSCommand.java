/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.util;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.GregorianCalendar;
import java.util.Calendar;
//import java.io.*;

/**
 * RTSCommand.java contains the knowledge of how to build command buffers for the RTS SocketServer.
 * There is a long list of static variables starting with USGS_CMD which are the command integers
 * understood by the RTS. There is a separate method for each command which returns a raw byte
 * buffer with the command properly encoded from arguments to the method.
 *
 * Created on January 14, 2004, 10:05 AM
 *
 *
 * @author ketchum
 */
public final class RTSCommand {

  static int USGS_CMD_RESET = 3, USGS_CMD_CONSOLE_BREAK = 1, USGS_CMD_CONFIG_PORTS = 2;
  static int USGS_CMD_LOOP_TEST = 4, USGS_CMD_QUANTERRA_POWER_CYCLE = 5;
  static int USGS_CMD_VSAT_POWER_CYCLE = 6, USGS_CMD_MAX_RATE_OVERRIDE = 7;
  static int USGS_CMD_SET_STATUS_IP = 8, USGS_CMD_SET_SERIAL_CONFIG = 9;
  static int USGS_CMD_SET_USGS_TYPE = 10, USGS_CMD_SAVE_SERIAL_CONFIG = 11;
  static int USGS_CMD_EXECUTE_COMMAND = 12, USGS_CMD_SET_RTS_MODE = 13;
  static int USGS_CMD_SET_COMMAND_IP = 14;
  static int USGS_CMD_RESET_VSAT_WATCHDOG = 15;
  static int USGS_CMD_SET_TUNNEL_IP = 16;
  static int USGS_CMD_SET_RTS_IP_ADDRESS = 17;
  static int USGS_CMD_SET_USE_BACKUP = 18;

  /**
   * RTS usgs port type code for an RTS - status/command port
   */
  public static int RTS = 0;
  /**
   * USGS port type for the console port role
   */
  public static int CONSOLE = 1;
  /**
   * Usgs port type for the data port using Gomberg packets
   */
  public static int DATA = 2;
  /**
   * Usgs port type for a GPS port (binary transfer blocked about 256 bytes)
   */
  public static int GPS = 3;
  /**
   * Usgs port type for a MShear based data port - checks of acks
   */
  public static int MSHEAR = 4;
  GregorianCalendar now;

  byte[] buf;
  int offset;
  boolean dbg = false;

  /**
   * Creates a new instance of RTScommand and reserves buffer space for encoding
   */
  public RTSCommand() {
    buf = new byte[300];
    now = new GregorianCalendar();
  }

  public byte[] setCommandIP(String ipadr, String newip, String newAlt, int port) {
    command(USGS_CMD_SET_COMMAND_IP, ipadr);
    append((short) port);
    append(newip);
    append((byte) 0);
    append(newAlt);
    append((byte) 0);
    return getData();
  }

  public byte[] setTunnelIP(String ipadr, String newip, String newAlt, int port) {
    command(USGS_CMD_SET_TUNNEL_IP, ipadr);
    append((short) port);
    append(newip);
    append((byte) 0);
    append(newAlt);
    append((byte) 0);

    return getData();
  }

  public byte[] setStatusIP(String ipadr, String newip, int port, String station) {
    command(USGS_CMD_SET_STATUS_IP, ipadr);
    append((short) port);
    append(newip);
    append((byte) 0);
    append((station + "       ").substring(0, 7));
    append((byte) 0);
    return getData();
  }

  /**
   * Encode the command buffer for a RTS reset.
   *
   * @param ipadr The IP address as a nnn.nnn.nnn.nnn of an RTS to be reset.
   * @return A raw byte buffer with the bytes for a reset.
   */
  public byte[] reset(String ipadr) {
    command(USGS_CMD_RESET, ipadr);
    return getData();
  }

  /**
   * Encode an RTS command buffer for a break to console.
   *
   * @param ipadr The IP address as a nnn.nnn.nnn.nnn of an RTS to be sent a break.
   * @return A raw byte buffer with the bytes for a break command.
   */
  public byte[] consoleBreak(String ipadr) {
    command(USGS_CMD_CONSOLE_BREAK, ipadr);
    return getData();
  }

  /**
   * encode the RTS command buffer for a loop test
   *
   * @param ipadr The IP address as a nnn.nnn.nnn.nnn of an RTS to be sent the command
   * @return A raw byte buffer with the bytes for a loop test command.
   */
  public byte[] loopTest(String ipadr) {
    command(USGS_CMD_LOOP_TEST, ipadr);
    return getData();
  }

  /**
   * encode the RTS command buffer for a DAS (Quanterra) power cycle.
   *
   * @param ipadr The IP address as a nnn.nnn.nnn.nnn of an RTS to be sent the command
   * @return A raw byte buffer with the bytes for a DAS Power Cycle command.
   */
  public byte[] dasPowerCycle(String ipadr) {
    command(USGS_CMD_QUANTERRA_POWER_CYCLE, ipadr);
    return getData();
  }

  /**
   * encode the RTS command buffer for a reset VSAT WatchdogS.
   *
   * @param ipadr The IP address as a nnn.nnn.nnn.nnn of an RTS to be sent the command
   * @return A raw byte buffer with the bytes for a VSAT power cycle command.
   */
  public byte[] vsatResetWatchdog(String ipadr) {
    command(USGS_CMD_RESET_VSAT_WATCHDOG, ipadr);
    now.setTimeInMillis(System.currentTimeMillis());
    int doy = now.get(Calendar.DAY_OF_YEAR);
    int value = (int) (doy * 86400 + (now.getTimeInMillis() % 86400000L) / 1000 + 2);
    //Util.prt(ipadr+" val="+value+" doy="+doy+" sec="+(now.getTimeInMillis()%86400000L));
    append(value);
    return getData();
  }

  /**
   * encode the RTS command buffer for a VSAT power cycle.
   *
   * @param ipadr The IP address as a nnn.nnn.nnn.nnn of an RTS to be sent the command
   * @return A raw byte buffer with the bytes for a VSAT power cycle command.
   */
  public byte[] vsatPowerCycle(String ipadr) {
    command(USGS_CMD_VSAT_POWER_CYCLE, ipadr);
    return getData();
  }

  /**
   * encode the RTS command buffer for a Save Config.
   *
   * @param ipadr The IP address as a nnn.nnn.nnn.nnn of an RTS to be sent the command
   * @return A raw byte buffer with the bytes for a Save Config command.
   */
  public byte[] saveConfig(String ipadr) {
    command(USGS_CMD_SAVE_SERIAL_CONFIG, ipadr);
    return getData();
  }

  /**
   * encode the RTS command buffer for a Set Maximum Override of Baud Rate.
   *
   * @param ipadr The IP address as a nnn.nnn.nnn.nnn of an RTS to be sent the command
   * @param rate The new baud rate limit.
   * @return A raw byte buffer with the bytes for a loop test command.
   */
  public byte[] setOverride(String ipadr, short rate) {
    command(USGS_CMD_MAX_RATE_OVERRIDE, ipadr);
    append(rate);
    return getData();
  }

  /**
   * encode the RTS command buffer for a Set Maximum Override of Baud Rate.
   *
   * @param ipadr The IP address as a nnn.nnn.nnn.nnn of an RTS to be sent the command
   * @param flag if true, use Backup IP addressing on VSAT back hauls.
   * @return A raw byte buffer with the bytes for a loop test command.
   */
  public byte[] setUseBackupIP(String ipadr, boolean flag) {
    command(USGS_CMD_SET_USE_BACKUP, ipadr);
    append((short) (flag ? 1 : 0));
    return getData();
  }

  /**
   * encode the RTS command buffer for a Configure USGS port.
   *
   * @param ipadr The IP address as a nnn.nnn.nnn.nnn of an RTS to be sent the command
   * @param serial The target serial port on RTS (0 to 3) for ports 1 to 4.
   * @param type The type of port. See enum in tcpstation table for encoding.
   * @param ip The IP address to program this port to contact if it is a client
   * @param altip The alternate IP address to program this port to contact if it is a client
   * @param port The port number the RTS is to contact if this serial port will be a client
   * @param client Boolean indicating whether the RTS serial port is a client (originates sockets).
   * @return A raw byte buffer with the bytes for a configure USGS port command.
   */
  public byte[] setUsgsType(String ipadr, int serial, int type, String ip, String altip, int port, boolean client) {
    command(USGS_CMD_SET_USGS_TYPE, ipadr);
    append((short) serial);
    append((short) type);
    byte[] balt;
    try {
      InetAddress a = InetAddress.getByName(ip);
      byte[] b = a.getAddress();
      for (int i = 0; i < 4; i++) {
        append(b[i]);
      }
    } catch (UnknownHostException e) {
      Util.prta("unknown host exception ip=" + ip);
      append(0);
    }

    append((short) port);
    if (client) {
      append((short) 0);
    } else {
      append((short) 1);
    }
    try {
      InetAddress a = InetAddress.getByName(altip);
      byte[] b = a.getAddress();
      for (int i = 0; i < 4; i++) {
        append(b[i]);
      }
    } catch (UnknownHostException e) {
      Util.prta("unknown host exception ip=" + ip);
      append(0);
    }
    return getData();
  }

  /**
   * encode the RTS command buffer for a set Mode (whether RTS connects as client)
   *
   * @param ipadr The IP address as a nnn.nnn.nnn.nnn of an RTS to be sent the command
   * @param mode boolean - turn on the RTS port to connect to golden if true
   * @return A raw byte buffer with the bytes for a loop test command.
   */

  public byte[] setRtsMode(String ipadr, boolean mode) {
    command(USGS_CMD_SET_RTS_MODE, ipadr);
    if (mode) {
      append((byte) 1);
    } else {
      append((byte) 0);
    }
    return getData();
  }

  private void command(int cmd, String ip) {
    String ipadr = "000.000.000.000";
    offset = 0;
    try {
      InetAddress a = InetAddress.getByName(ip);
      ipadr = a.getHostAddress();
    } catch (UnknownHostException e) {
      Util.prta("RTSConfigUDP: command() = IP address bad" + ip);
    }
    append((ipadr + "                ").substring(0, 16));
    append((byte) (7999 & 0xff));
    append((byte) ((7999 >> 8) & 0xff));
    append((byte) 33);
    append((byte) 3);
    append((byte) cmd);
    offset++;                 // skip nbytes until end
  }

  private void append(String s) {
    byte[] v = s.getBytes();
    if (dbg) {
      Util.prt("append string l=" + s.length() + " s=" + s + " offset=" + offset);
    }
    moveb(s.length(), v, buf, offset);
    offset += s.length();
  }

  private void append(byte[] c) {
    moveb(c.length, c, buf, offset);
    offset += c.length;
  }

  private void append(byte c) {
    if (dbg) {
      Util.prt("append byte off=" + offset + " c=" + c);
    }
    buf[offset++] = c;
  }

  private void append(short c) {
    buf[offset++] = (byte) ((c >> 8) & 0xff);
    buf[offset++] = (byte) (c & 0xff);
  }

  private void append(int c) {
    if (dbg) {
      Util.prt("Append int off=" + offset + " c=" + c);
    }
    for (int i = 0; i < 4; i++) {
      buf[offset + 3 - i] = (byte) (c & 0xff);
      c = c >> 8;
    }
    offset += 4;
  }

  private void moveb(int l, byte[] from, byte[] to, int offset) {
    System.arraycopy(from, 0, to, offset, l);
    //for(int i=0; i< l; i++) to[offset+i] = from[i];
  }

  private byte[] getData() {
    buf[21] = (byte) (offset - 18);
    if (dbg) {
      Util.prt("Send len=" + buf[21]);
    }
    byte[] b = new byte[offset];
    System.arraycopy(buf, 0, b, 0, offset);
    return b;
  }

}
