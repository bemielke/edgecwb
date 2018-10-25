/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.alarm;

import gov.usgs.anss.util.Util;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;

/**
 * This class will relay UDP alarm packets to another alarm server. It is
 * entirely static.
 *
 * @author davidketchum
 */
public final class SendEventRelay {

  private static DatagramSocket out;
  private static DatagramPacket dp;
  private static byte[] outbuf;
  private static ByteBuffer bf;
  private static String eventHandlerIP;
  private static int eventHandlerPort = 7964;
  private static int bindPort;

  /**
   * override the IP address to which Events are sent
   *
   * @param ip The IP address to use for events , if null do not change
   * @param port The port to use (if <= 0, do not change) 
   * @param bindport If this end needs to bind its port, set the binding..
   */
  public static void setEventHandler(String ip, int port, int bindport) {
    Util.prta("Bef setEventRelayHandler ip=" + eventHandlerIP + "/" + eventHandlerPort);
    if (ip != null) {
      eventHandlerIP = ip;
    }
    if (port > 0) {
      eventHandlerPort = port;
    }
    bindPort = bindport;
    Util.prta("Aft setEventRelayHandler ip=" + eventHandlerIP + "/" + eventHandlerPort);
  }

  /**
   * Creates a new instance of SendEvent
   */
  public SendEventRelay() {
  }

  public static void edgeEvent(String cd, String payload, Object obj) {
    sendEvent("Edge", cd, payload, Util.getNode(), obj.getClass().getSimpleName());
  }

  /**
   * send a UDP packet with an event the current eventHandler IP and port
   *
   * @param src The 12 character source
   * @param cd The 12 character error code
   * @param payload Up to 80 characters of message payload
   * @param node Up to 12 character computer node name
   * @param process Up to 12 character process name
   */
  public synchronized static void sendEvent(String src, String cd, String payload, String node, String process) {
    if (eventHandlerIP == null) {
      return;
    }
    if (out == null) {
      try {
        Util.prt("Evt Relay to " + eventHandlerIP + "/" + eventHandlerPort + " bind=" + bindPort + " " + src + "-" + cd + " nd/prc=" + node + "/" + process + " pl=" + payload);
        if (bindPort > 0) {
          out = new DatagramSocket(bindPort);
        } else {
          out = new DatagramSocket();
        }
        outbuf = new byte[140];
        bf = ByteBuffer.wrap(outbuf);
        dp = new DatagramPacket(outbuf, 0, 140, InetAddress.getByName(eventHandlerIP), eventHandlerPort);
        outbuf[0] = (byte) 33;
        outbuf[1] = (byte) 3;
        outbuf[2] = (byte) 0;
        outbuf[3] = (byte) -55;
      } catch (UnknownHostException e) {
        Util.prt("Unknown host =" + e);
        return;
      } catch (IOException e) {
        Util.prt("cannot open datagram socket on this computer! e=" + e);
        return;
      }
    }

    // Put the data in the packet
    for (int i = 4; i < 140; i++) {
      outbuf[i] = 0;
    }
    bf.position(4);
    bf.put(process.getBytes(), 0, Math.min(20, process.length()));
    bf.position(24);
    bf.put(src.getBytes(), 0, Math.min(12, src.length()));
    bf.position(36);
    bf.put(cd.getBytes(), 0, Math.min(12, cd.length()));
    bf.position(48);
    bf.put(payload.getBytes(), 0, Math.min(80, payload.length()));
    bf.position(128);
    bf.put(node.getBytes(), 0, Math.min(12, node.length()));
    try {
      out.send(dp);
    } catch (IOException e) {
      Util.prt("Relay UDP error sending a message");
    }

  }
}
