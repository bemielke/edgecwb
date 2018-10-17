/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */

package gov.usgs.alarm;

import gov.usgs.anss.util.SimpleSMTPThread;
import gov.usgs.anss.util.Util;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;

/**
 * This class allows and event to get sent to the alarm system. It generally is
 * called statically.
 * *<br>
 * <br> offset Description
 * <br> 0,1,2,3 33,3,0,201 A header for event types
 * <br> 4 process - The local system process(20 char) normally last 4 of the
 * account, a space, and up to 15 of process.
 * <br> 24 Source - The source of the message (12 char)
 * <br> 36 code - the event code for this source (12 char)
 * <br> 48 Phrase or payload (80 Char)
 * <br> 128 Node (char 12) encodes as role:servername and truncated to 12.
 * <br> 140 End of data
 *
 * @author davidketch
 *
 * @author davidketchum
 */
public class SendEvent {

  static DatagramSocket out;
  static DatagramPacket dp;
  static byte[] outbuf;
  static ByteBuffer bf;
  static String eventHandlerIP = (Util.getProperty("AlarmIP") == null ? "localhost" : Util.getProperty("AlarmIP"));
  static String[] ips;
  static int eventHandlerPort = (Util.getProperty("AlarmPort") == null ? 7964 : ((Util.getProperty("AlarmPort").equals("") ? 7964 : Integer.parseInt(Util.getProperty("AlarmPort")))));

  /**
   * override the IP address to which Events are sent
   *
   * @param ip The IP address to use for events , if null do not change
   * @param port The port to use (if <= 0, do not change)
   */
  public static void setEventHandler(String ip, int port) {
    Util.prta("Bef setEventHandler ip=" + eventHandlerIP + "/" + eventHandlerPort);
    if (ip != null) {
      eventHandlerIP = ip;
    }
    if (port > 0) {
      eventHandlerPort = port;
    }
    Util.prta("Aft setEventHandler ip=" + eventHandlerIP + "/" + eventHandlerPort);
    ips = eventHandlerIP.split(",");
  }

  /**
   * Creates a new instance of SendEvent
   */
  public SendEvent() {
  }

  private static String getNodeRole() {
    String[] roles = Util.getRoles(Util.getSystemName());
    if (roles != null) {
      return roles[0] + ":" + Util.getSystemName();
    }
    return "Null";
  }

  /**
   * send a UDP packet with an event the current eventHandler IP and port with
   * Edge source and obj class process
   *
   * @param cd The 12 character error code
   * @param payload Up to 80 characters of message payload
   * @param obj An object whose class name will be used as the process
   */
  public static void edgeEvent(CharSequence cd, CharSequence payload, Object obj) {
    sendEvent("Edge", cd, payload, getNodeRole(), obj.getClass().getSimpleName());
  }

  /**
   * send a UDP packet with an event the current eventHandler IP and port with
   * Edge source and obj class process
   *
   * @param cd The 12 character error code
   * @param payload Up to 80 characters of message payload
   * @param str An CharSequence to be used as the process
   */
  public static void edgeEvent(CharSequence cd, CharSequence payload, CharSequence str) {
    sendEvent("Edge", cd, payload, getNodeRole(), str);
  }

  /**
   * send a UDP packet with an event the current eventHandler IP and port with
   * Edge source and obj class process
   *
   * @param cd The 12 character error code
   * @param payload Up to 80 characters of message payload
   * @param str An CharSequence to be used as the process
   */
  /*public static void edgeEvent(StringBuilder cd, CharSequence payload, CharSequence str) {
    sendEvent("Edge", cd, payload, getNodeRole(), str);
  }*/

  /**
   * send a UDP packet with an event the current eventHandler IP and port with
   * Edge source and obj class process
   *
   * @param cd The 12 character error code
   * @param payload Up to 80 characters of message payload
   * @param obj An object whose class name will be used as the process
   */
  public static void debugEvent(CharSequence cd, CharSequence payload, Object obj) {
    debugEvent(cd, payload, obj.getClass().getSimpleName());
  }

  /**
   * send a UDP packet with an event the current eventHandler IP and port with
   * Edge source and obj class process
   *
   * @param cd The 12 character error code
   * @param payload Up to 80 characters of message payload
   * @param process An CharSequence to be used as the process
   */
  public static synchronized void debugEvent(CharSequence cd, CharSequence payload, CharSequence process) {
    if (out == null) {
      setup();
    }
    String src = "Dbg";
    String node = getNodeRole();
    Util.clear(sendsb).append("SendEvent: ").append(src.trim()).append("-").append(cd).append(" ").
            append(node).append("/").append(process).append(" ").append(payload).append(" to ").
            append(ips[0].trim()).append("/").append(dp.getPort());
    Util.prta(sendsb);
    for (int i = 4; i < 140; i++) {
      outbuf[i] = 0;
    }
    bf.position(4);
    charSeqToByteBuffer(process, 0, Math.min(20, process.length()), bf);
    //bf.put(process.getBytes(),0, Math.min(20, process.length()));
    bf.position(24);
    charSeqToByteBuffer(src, 0, Math.min(12, src.length()), bf);
    //bf.put(src.getBytes(),0, Math.min(12, src.length()));
    bf.position(36);
    charSeqToByteBuffer(cd, 0, Math.min(12, cd.length()), bf);
    //bf.put(cd.getBytes(),0, Math.min(12, cd.length()));
    bf.position(48);
    charSeqToByteBuffer(payload, 0, Math.min(80, payload.length()), bf);
    //bf.put(payload.getBytes(),0, Math.min(80, payload.length()));
    bf.position(128);
    charSeqToByteBuffer(node, 0, Math.min(12, node.length()), bf);
    //bf.put(node.getBytes(),0, Math.min(12, node.length()));
    try {
      dp.setAddress(InetAddress.getByName(ips[0].trim()));
      out.send(dp);
    } catch (IOException e) {
      Util.prt("UDP error sending a message");
    }
  }

  /**
   * send a UDP packet with an event the current eventHandler IP and port with
   * Edge source and obj class process
   *
   * @param cd The 12 character error code
   * @param payload Up to 80 characters of message payload
   * @param obj An object whose class name will be used as the process
   */
  public static void edgeSMEEvent(CharSequence cd, CharSequence payload, Object obj) {
    sendEvent("EdgeSME", cd, payload, getNodeRole(), obj.getClass().getSimpleName());
  }

  /**
   * send a UDP packet with an event the current eventHandler IP and port with
   * Edge source and obj class process
   *
   * @param cd The 12 character error code
   * @param payload Up to 80 characters of message payload
   * @param str An CharSequence to be used as the process
   */
  public static void edgeSMEEvent(CharSequence cd, CharSequence payload, CharSequence str) {
    sendEvent("EdgeSME", cd, payload, getNodeRole(), str);
  }

  /**
   * send a UDP packet with an event the current eventHandler IP and port with
   * Edge source and obj class process
   *
   * @param cd The 12 character error code
   * @param payload Up to 80 characters of message payload
   * @param obj An object whose class name will be used as the process
   */
  public static void pageSMEEvent(CharSequence cd, CharSequence payload, Object obj) {
    sendEvent("PageSME", cd, payload, getNodeRole(), obj.getClass().getSimpleName());
  }

  /**
   * send a UDP packet with an event the current eventHandler IP and port with
   * Edge source and obj class process
   *
   * @param cd The 12 character error code
   * @param payload Up to 80 characters of message payload
   * @param str An CharSequence to be used as the process
   */
  public static void pageSMEEvent(CharSequence cd, CharSequence payload, CharSequence str) {
    sendEvent("PageSME", cd, payload, getNodeRole(), str);
  }

  /**
   * send a UDP packet with an event the current eventHandler IP and port with
   * Edge source and obj class process
   *
   * @param cd The 12 character error code
   * @param payload Up to 80 characters of message payload
   * @param obj An object whose class name will be used as the process
   */
  public static void debugSMEEvent(CharSequence cd, CharSequence payload, Object obj) {
    debugSMEEvent(cd, payload, obj.getClass().getSimpleName());
  }

  /**
   * send a UDP packet with an event the current eventHandler IP and port with
   * Edge source and obj class process
   *
   * @param cd The 12 character error code
   * @param payload Up to 80 characters of message payload
   * @param process An CharSequence to be used as the process
   */
  public static synchronized void debugSMEEvent(CharSequence cd, CharSequence payload, CharSequence process) {
    if (out == null) {
      setup();
    }
    String src = "DbgSME";
    String node = getNodeRole();
    Util.clear(sendsb).append("SendEvent: ").append(src.trim()).append("-").append(cd).append(" ").
            append(node.trim()).append("/").append(process).append(" ").append(payload).append(" to ").
            append(ips[0].trim()).append("/").append(dp.getPort());
    Util.prta(sendsb);
    for (int i = 4; i < 140; i++) {
      outbuf[i] = 0;
    }
    bf.position(4);
    charSeqToByteBuffer(process, 0, Math.min(20, process.length()), bf);
    //bf.put(process.getBytes(),0, Math.min(20, process.length()));
    bf.position(24);
    charSeqToByteBuffer(src, 0, Math.min(12, src.length()), bf);
    //bf.put(src.getBytes(),0, Math.min(12, src.length()));
    bf.position(36);
    charSeqToByteBuffer(cd, 0, Math.min(12, cd.length()), bf);
    //bf.put(cd.getBytes(),0, Math.min(12, cd.length()));
    bf.position(48);
    charSeqToByteBuffer(payload, 0, Math.min(80, payload.length()), bf);
    //bf.put(payload.getBytes(),0, Math.min(80, payload.length()));
    bf.position(128);
    charSeqToByteBuffer(node, 0, Math.min(12, node.length()), bf);
    //bf.put(node.getBytes(),0, Math.min(12, node.length()));
    try {
      dp.setAddress(InetAddress.getByName(ips[0].trim()));
      out.send(dp);
    } catch (IOException e) {
      Util.prt("UDP error sending a message");
    }
  }

  private synchronized static void setup() {
    try {
      Util.prta("SendEvent: Setup to  " + eventHandlerIP + "/" + eventHandlerPort + "  properties " + Util.getProperty("AlarmIP") + "/" + Util.getProperty("AlarmPort"));
      setEventHandler(eventHandlerIP, eventHandlerPort);
      out = new DatagramSocket();
      outbuf = new byte[140];
      bf = ByteBuffer.wrap(outbuf);
      if (ips == null) {
        ips = new String[1];
        ips[0] = eventHandlerIP;
      }
      dp = new DatagramPacket(outbuf, 0, 140, InetAddress.getByName(ips[0].trim()), eventHandlerPort);
      outbuf[0] = (byte) 33;
      outbuf[1] = (byte) 3;
      outbuf[2] = (byte) 0;
      outbuf[3] = (byte) -55;
    } catch (UnknownHostException e) {
      Util.prt("SendEvent: Unknown host =" + e);
    } catch (IOException e) {
      Util.prt("SendEvent: cannot open datagram socket on this computer! e=" + e);
    }
  }
  private static final StringBuilder sendsb = new StringBuilder(100);

  /**
   * send a UDP packet with an event the current eventHandler IP and port
   *
   * @param src The 12 character source
   * @param cd The 12 character error code
   * @param payload Up to 80 characters of message payload
   * @param node Up to 12 character computer node name
   * @param process Up to 12 character process name
   */
  public synchronized static void sendEvent(CharSequence src, CharSequence cd, CharSequence payload, 
          CharSequence node, CharSequence process) {
    if (out == null) {
      setup();
    }
    //Util.prta("SendEvent: "+src.trim()+"-"+cd.trim()+" "+node.trim()+"/"+process.trim()+" "+payload.trim());
    // Put the data in the packet
    for (int i = 4; i < 140; i++) {
      outbuf[i] = 0;
    }
    String acct = Util.getAccount();
    if (acct.length() > 4) {
      acct = acct.substring(acct.length() - 4);
    }
    Util.clear(sendsb).append("SendEvent: ").append(src).append("-").append(cd).append(" ").
            append(node).append("/").append(acct).append(" ").append(process).append(" ").
            append(payload).append(" to ").append(ips == null ? "null" : ips[0].trim()).
            append("/").append(dp == null ? "null" : dp.getPort());
    Util.prta(sendsb);

    bf.position(4);
    bf.put(acct.getBytes());
    bf.put((byte) 32);
    charSeqToByteBuffer(process, 0, Math.min(15, process.length()), bf);
    //bf.put(process.getBytes(),0, Math.min(15,process.length()));
    bf.position(24);
    charSeqToByteBuffer(src, 0, Math.min(12, src.length()), bf);
    //bf.put(src.getBytes(),0, Math.min(12, src.length()));
    bf.position(36);
    charSeqToByteBuffer(cd, 0, Math.min(12, cd.length()), bf);
    //bf.put(cd.getBytes(),0, Math.min(12, cd.length()));
    bf.position(48);
    charSeqToByteBuffer(payload, 0, Math.min(80, payload.length()), bf);
    //bf.put(payload.getBytes(),0, Math.min(80, payload.length()));
    bf.position(128);
    charSeqToByteBuffer(node, 0, Math.min(12, node.length()), bf);
    //bf.put(node.getBytes(),0, Math.min(12, node.length()));
    try {
      for (String ip : ips) {
        dp.setAddress(InetAddress.getByName(ip.trim()));
        out.send(dp);
      }
    } catch (IOException e) {
      Util.prt("UDP error sending a message");
    }
  }

  private static void charSeqToByteBuffer(CharSequence seq, int off, int len, ByteBuffer bb) {
    for (int i = off; i < Math.min(len, seq.length()); i++) {
      bb.put((byte) seq.charAt(i));
    }
  }

  public static void doOutOfMemory(CharSequence msg, Object ths) {
    SendEvent.edgeEvent("OutOfMemory", msg + " Out of Memory in "
            + (ths != null ? ths.getClass().getName() : "null") + " on " + Util.getNode() + "/" + Util.getAccount() + " " + Util.getProcess(), ths);
    SimpleSMTPThread.email(Util.getProperty("emailTo"), "Out of memory " + Util.getNode() + "/" + Util.getAccount() + " " + Util.getProcess(),
            Util.asctime() + " " + Util.ascdate() + " Out of Memory in " + (ths != null ? ths.getClass().getName() : "null") + " on " + Util.getNode() + "/" + Util.getAccount() + " " + Util.getProcess() + "\n"
            + msg);
  }

  public static void main(String[] args) {
    Util.init("edge.prop");
    SendEvent.edgeSMEEvent("HydraOovfl", "This is a test message", "DaveTest");
  }
}
