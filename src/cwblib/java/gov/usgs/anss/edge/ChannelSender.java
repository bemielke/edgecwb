/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.edge;

import gov.usgs.alarm.SendEvent;
import gov.usgs.anss.edgemom.EdgeChannelServer;
import gov.usgs.anss.channelstatus.ChannelStatus;
import gov.usgs.anss.util.FUtil;
import gov.usgs.anss.util.SeedUtil;
import gov.usgs.anss.util.Util;
import java.io.IOException;
import java.net.*;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;

/**
 * This class creates a UDP end point and implements methods for sending data holdings to a
 * UdpHolding server in some alarm process. On creation it sets up the end point and stores the
 * target IP and p
 * <pre>
 * If property StatusServer is :
 * null - This is considered a configuration error and a message is sent to Alarm, no sender is setup
 * "" - Then there is no sender setup as a designed configuration.
 * ip.adr or remote - the host name to send the Channels to a UdpChannel server.
 * </pre>
 *
 * @author davidketchum
 */
public final class ChannelSender {

  public static final int MAX_LENGTH = ChannelStatus.getMaxLength();
  public static final int JULIAN_1970 = SeedUtil.toJulian(1970, 1, 1);
  public static final int JULIAN_MIN = SeedUtil.toJulian(System.currentTimeMillis() - 4*365*86400000L);
  private static final ArrayList<ChannelSender> senders = new ArrayList<>(2);
  private DatagramSocket s;  // A Datagram socket for sending the UDP packets
  private final String host;
  private final int port;
  private final byte[] b;
  //private final ByteBuffer bb;
  private DatagramPacket pkt;
  private long totalrecs;
  private static int nerr;
  private final String cpu;     // char*6
  private final String sort;    // char*2
  private final String proc_name;//Char*12
  private final String source;    // char*10
  private final GregorianCalendar packet_time;

  // status and debug
  private long lastStatus;
  private boolean dbg;
  private static long lastTotalRecords = 0;

  public long getTotalRecords() {
    return totalrecs;
  }

  public void close() {
    if (s != null) {
      s.close();
    }
  }

  public static ChannelSender getChannelSender() {
    if (senders.size() >= 1) {
      return senders.get(0);
    } else {
      return null;
    }
  }

  /**
   * Creates a new instance of ChannelSender
   *
   * @param srt A at most 2 character sort name
   * @param proc A at most 12 character process name
   * @param src A at most 10 character source name
   */
  public ChannelSender(String srt, String proc, String src) {
    host = Util.getProperty("StatusServer");
    port = 7993;
    sort = (srt + "  ").substring(0, 2);
    proc_name = (proc + "            ").substring(0, 12);
    source = (src + "          ").substring(0, 10);
    //cpu = (Util.getNode()+"     ").substring(0,6);
    cpu = (Util.getProperty("Instance") + "     ").substring(0, 6);
    packet_time = new GregorianCalendar();      // a time to use
    b = new byte[MAX_LENGTH];
    if (host == null) {
      SendEvent.edgeSMEEvent("CSBadHost", "StatusServer and MySQLServer did not yield a host in ChannelSender", "ChannelSender");
      return;
    }
    if (host.trim().equals("")) {
      Util.prta("CS: no server given - so do not setup a ChannelSender for this node");
      return;
    }
    try {
      InetAddress address = InetAddress.getByName(host);
      s = new DatagramSocket();
      pkt = new DatagramPacket(b, MAX_LENGTH, address, port);
      Util.prta("CS: set up for " + host + ":" + port + " proc=" + proc_name + " src=" + source + " cpu=" + cpu
              + " srt=" + sort + " mysql=" + Util.getProperty("MySQLServer") + " Status=" + Util.getProperty("StatusServer") + " #send=" + senders.size());
    } catch (UnknownHostException e) {
      Util.prta("CS: UnknownHostException : *** Failed to create a Channel sender e=" + e.getMessage());
    } catch (SocketException e) {
      Util.prta(" CS: *** could not create a ChannelSender UDP socket end" + e.getMessage());
    }
    synchronized (senders) {
      senders.add((ChannelSender) this);
    }
  }

  public synchronized void send(int julian, int millis, StringBuilder key, int nsamp, 
          double rate, int nbytes) throws IOException {
    //Util.prt("CS: "+key+" ns="+nsamp+" rt="+rate+" "+(pkt==null?"null":""));
    if (pkt == null) {
      return;       // Not set up
    }
    if (nsamp == 0) {
      return;        // trigger or other admin packets.
    }    // If its not a normal time series channel [BHLS][HL] then do not forward it.
    
    if(millis < 0) {
      new RuntimeException("ChannelSender with neg millis="+millis).printStackTrace();
    }
    if (!(key.charAt(0) == 'N' && key.charAt(1) == 'T')) {
      //if(!key.substring(0,2).equals("NT")) {    // Allow all Geomag channels
      char ch = key.charAt(7);
      if (ch != 'B' && ch != 'S' && ch != 'H' && ch != 'L' && ch != 'V' && ch != 'M' && ch != 'E' && ch != 'D' && ch != 'C') {
        //Util.prt("CS: bad comp="+key);
        return;
      }
      ch = key.charAt(8);
      if (ch != 'L' && ch != 'H' && ch != 'N' && ch != 'D' && ch != '1') {
        //Util.prt("CS: bad2 comp="+key);
        return;
      }
    }
    //else Util.prt("CS: send "+key+" ns="+nsamp+" rt="+rate+" "+Util.ascdatetime2((julian - JULIAN_1970)*86400000L+millis)+" pkt="+(pkt == null?"Null":pkt.getAddress()+" "+pkt.getPort()));

    if(julian < JULIAN_MIN) {
      int[] ymd = SeedUtil.fromJulian(julian);
      Util.prt("*** CS: date too old key="+key+" jd="+julian+" "+ymd[0]+"/"+ymd[1]+"/"+ymd[2]+"<"+JULIAN_MIN);
      return;
    }
    if (EdgeChannelServer.isValid()) {
      EdgeChannelServer.setLastData(key, (julian - JULIAN_1970) * 86400000L + millis);
    }
    if (pkt == null) {
      return;   // No UDP setup, so it cannot be sent
    }
    packet_time.setTimeInMillis(System.currentTimeMillis());
    int yymmdd = packet_time.get(Calendar.YEAR) * 10000 + (packet_time.get(Calendar.MONTH) + 1) * 100
            + packet_time.get(Calendar.DAY_OF_MONTH);
    FUtil.intToArray(yymmdd, b, 0);           // Packet arrival time ymd
    int mss = (int) (packet_time.getTimeInMillis() % 86400000L);
    FUtil.intToArray(mss, b, 4);              // Packet arrival time in millis
    FUtil.stringToArray(cpu, b, 8, 6);
    FUtil.stringToArray(sort, b, 14, 2);
    FUtil.stringToArray(proc_name, b, 16, 12);
    FUtil.stringToArray(source, b, 28, 10);
    FUtil.stringToArray(key, b, 38, 12);       // network/station/component/location
    FUtil.intToArray(nsamp, b, 50);
    int irate;
    if (rate >= 0.99) {
      irate = (int) (rate * 1000. + 0.5);
    } else if (rate > 0) {
      irate = (int) (-1. / rate * 1000 - 0.5);
    } else {
      Util.prt("CS: Illegal rate =" + rate + " " + key + " " + proc_name);
      return;
    }

    FUtil.intToArray(irate, b, 54);
    int[] ymd = SeedUtil.fromJulian(julian);
    yymmdd = ymd[0] * 10000 + ymd[1] * 100 + ymd[2];
    FUtil.intToArray(yymmdd, b, 58);  // Packet yymmdd
    FUtil.intToArray(millis, b, 62);     // packet MS
    FUtil.intToArray(nbytes, b, 66);// Packet Nbytes
    pkt.setData(b);
    totalrecs++;
    if (totalrecs % 10000 == 0) {
      Util.prta("CS: #recs=" + totalrecs);
    }
    try {
      s.send(pkt);
    } catch (IOException e) {
      Util.prt("CS: IOErr=" + e);
      nerr++;
    }
  }

  public String getStatusString() {
    return host + "/" + port + " nblks=" + totalrecs;
  }

  /**
   * return the monitor string for Icinga
   *
   * @return A String representing the monitor key value pairs for this EdgeThread
   */
  public static String getMonitorString() {
    long total = 0;
    synchronized (senders) {
      for (int i = 0; i < senders.size(); i++) {
        ChannelSender sender = senders.get(i);
        if (sender != null) {
          total += sender.getTotalRecords();
        } else {
          Util.prta("CS: ** has a null sender!  How does this happen! i=" + i + " sender=" + sender);
        }
      }
    }
    long nrec = total - lastTotalRecords;
    lastTotalRecords = total;
    return "ChannelSenderNrecs=" + nrec + "\nnerr=" + nerr + "\n";
  }

  @Override
  public String toString() {
    return getStatusString();
  }

  public String getConsoleOutput() {
    return "";
  }

}
