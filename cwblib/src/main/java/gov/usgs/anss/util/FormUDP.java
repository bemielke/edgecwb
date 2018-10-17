/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.util;

import java.net.DatagramSocket;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.io.IOException;

/**
 * FormUDP.java - This class allows communications of tag-value pairs over a TCP/IP socket to the
 * VMS systems. This system is used to update the VMS Form file to reflect the database settings on
 * the Anss system. This allows automatic syncing of the Anss database to the form file. The "port"
 * of the created object indicates the form file type on VMS (a separate listener for each file type
 * is created on the VMS side).
 *
 * Created on December 5, 2003, 11:42 AM
 *
 * @author ketchum
 */
public final class FormUDP {

  DatagramSocket ss;
  DatagramPacket p;
  byte[] buf;
  int offset;

  /**
   * Creates a new instance of UdpServer
   *
   * @param port The integer port number of the VMS system to contact- determines which file or
   * system will be update
   * @param host The host name of the VMS system which must be resolvable by DNS to IP adr
   * @param station The key of the record in the Form file on VMS (which record will be updated)
   */
  public FormUDP(int port, String host, String station) {       // note port decides which file on VMS side
    try {
      ss = new DatagramSocket();
      buf = new byte[1500];
      p = new DatagramPacket(buf, 1500, InetAddress.getByName(host), port);
      Util.prt("Create form UDP to " + host + "/" + port);
      offset = 0;
      buf[offset++] = 27;
      buf[offset++] = 3;
      byte[] r = (station + "        ").getBytes();
      moveb(8, r, buf, offset);
      offset += 8;
    } catch (IOException e) {
      Util.prt("Cannot set up socket server on port " + port);
      e.printStackTrace();
      System.exit(10);
    }
  }

  /**
   * append this key-value pair - usually a variable in the form file.
   *
   * @param key The string with the key name (maps to a variable on VMS)
   * @param s The value to assign the key
   */
  public void append(String key, String s) {
    byte[] b = (key + "           ").getBytes();
    moveb(12, b, buf, offset);
    offset += 12;
    byte[] v = (s + "                ").getBytes();
    moveb(16, v, buf, offset);
    offset += 16;
  }

  /**
   * append this key-value pair - usually a variable in the form file.
   *
   * @param key The string with the key name (maps to a variable on VMS)
   * @param s The value to assign the key
   */
  public void append(String key, double s) {
    byte[] b = (key + "           ").getBytes();
    moveb(12, b, buf, offset);
    offset += 12;
    byte[] v = (s + "                ").getBytes();
    moveb(16, v, buf, offset);
    offset += 16;
  }

  /**
   * append this key-value pair - usually a variable in the form file.
   *
   * @param key The string with the key name (maps to a variable on VMS)
   * @param s The value to assign the key
   */
  public void append(String key, int s) {
    byte[] b = (key + "           ").getBytes();
    moveb(12, b, buf, offset);
    offset += 12;
    byte[] v = (s + "                ").getBytes();
    moveb(16, v, buf, offset);
    offset += 16;
  }

  private void moveb(int l, byte[] from, byte[] to, int offset) {
    System.arraycopy(from, 0, to, offset, l);
  }

  /**
   * reset the object to start a new
   */
  public void reset() {
    offset = 2;
  }

  /**
   * Send the key-value pairs to the host system
   */
  public void send() {
    try {
      p.setData(buf, 0, offset + 1);
      ss.send(p);
    } catch (NullPointerException | IOException e) {
      Util.prt("FormUDP.send : " + e.getMessage());
    }
  }

  /**
   * This main displays the form Pane by itself
   *
   * @param args Command line args
   */
  public static void main(String args[]) {
    FormUDP rts = new FormUDP(2099, "NEISA.CR.USGS.GOV", "TST");
    rts.append("ipadr", "111.222.333.444");
    rts.append("gomberg", 1);
    rts.append("timeout", 601);
    rts.send();
  }
}
