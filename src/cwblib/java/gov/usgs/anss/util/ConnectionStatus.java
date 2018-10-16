/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.util;

import gov.usgs.anss.edgethread.EdgeThread;
import java.sql.*;
import java.net.*;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.text.DecimalFormat;
import java.util.ArrayList;

/**
 * The ConnectionStatus contains one group of information from the UDP channel system plus data
 * derived for successive updates of this data. Basically it is the IP addresses and ports involved
 * and whether there was a connect or disconnect.
 *
 *
 */
public final class ConnectionStatus implements Comparable, StatusInfo {

  static ArrayList typeEnum;
  static int MAX_LENGTH = 66;
  static int DERIVED_LENGTH = 16;
  private GregorianCalendar time = new GregorianCalendar();         // Time of this message from Foreign computer
  private GregorianCalendar firstTime = new GregorianCalendar();    // This is not changed by update and gives time base for stats
  private String key;                     // ipadr/station per SEED
  private String proc_name;               // foreign process that sent this message
  private String cpu;                     // What computer the process was on
  private String sort;                    // Sort hint, maybe not used???
  private int msgType;                    // 1= connect, 2=disconnect
  private int localPort;
  private int remotePort;
  private int pid;                        // pid that was running

  // derived data
  private int ndisconnects;
  private int nconnects;
  private int secsConnected;
  private int secsDisconnected;

  //private DecimalFormat df = new DecimalFormat("##0.0");  // %%0.0 format

  byte b[];
  boolean dbg = false;
  private EdgeThread par;

  @Override
  public void setParent(EdgeThread parent) {
    par = parent;
  }

  private void prt(String a) {
    if (par == null) {
      Util.prt(a);
    } else {
      par.prt(a);
    }
  }

  private void prt(StringBuilder a) {
    if (par == null) {
      Util.prt(a);
    } else {
      par.prt(a);
    }
  }

  private void prta(String a) {
    if (par == null) {
      Util.prta(a);
    } else {
      par.prta(a);
    }
  }

  private void prta(StringBuilder a) {
    if (par == null) {
      Util.prta(a);
    } else {
      par.prta(a);
    }
  }

  /**
   * Creates a new instance of ConnectionStatus with all Z's in the ke
   *
   * @param k
   */
  public ConnectionStatus(String k) {
    key = k;
    time = new GregorianCalendar();
    msgType = -1;
    ndisconnects = 0;
    nconnects = 0;
    secsConnected = 0;
    secsDisconnected = 0;
    b = makeBuf();
  }

  /**
   * Creates a new instance of ConnectionStatus with all Z's in the key
   */
  public ConnectionStatus() {
    key = "ZZZZZZZZZZZZ";
    time = new GregorianCalendar();
    b = new byte[MAX_LENGTH];
    msgType = -1;
    ndisconnects = 0;
    nconnects = 0;
    secsConnected = 0;
    secsDisconnected = 0;
  }

  public ConnectionStatus(ResultSet rs) throws SQLException {
    if (typeEnum == null) {
      typeEnum = FUtil.getEnum(UC.getConnection(), "tcpconnection", "type");
    }
    Timestamp ts = Util.getTimestamp(rs, "time");
    time = new GregorianCalendar();
    time.setTimeInMillis(ts.getTime());
    firstTime = time;
    cpu = Util.getString(rs, "cpu");
    sort = Util.getString(rs, "sort");
    proc_name = (Util.getString(rs, "process") + "               ").substring(0, 12);
    String ip = (Util.getString(rs, "ip") + "     ").substring(0, 16);

    String station = Util.getString(rs, "station");
    key = (ip.substring(0, 16) + station + "         ").substring(0, 22);
    String t = Util.getString(rs, "type");

    msgType = FUtil.enumStringToInt(typeEnum, t);
    remotePort = Util.getShort(rs, "remoteport");
    localPort = Util.getShort(rs, "localPort");
    pid = Util.getInt(rs, "pid");
    b = makeBuf();
  }
  //This create the raw binary buffer from the data

  @Override
  public final byte[] makeBuf() {
    byte[] btmp = new byte[MAX_LENGTH + DERIVED_LENGTH];
    int yymmdd = time.get(Calendar.YEAR) * 10000 + (time.get(Calendar.MONTH) + 1) * 100
            + time.get(Calendar.DAY_OF_MONTH);
    FUtil.intToArray(yymmdd, btmp, 0);
    int ms = (int) (time.getTimeInMillis() % 86400000L);
    FUtil.intToArray(ms, btmp, 4);
    FUtil.stringToArray(cpu, btmp, 8, 6);
    FUtil.stringToArray(sort, btmp, 14, 2);
    FUtil.stringToArray(proc_name, btmp, 16, 12);
    FUtil.stringToArray(key, btmp, 28, 22);
    FUtil.intToArray(msgType, btmp, 50);
    FUtil.intToArray(remotePort, btmp, 54);
    FUtil.intToArray(localPort, btmp, 58);
    FUtil.intToArray(pid, btmp, 62);
    FUtil.intToArray(nconnects, btmp, 66);
    FUtil.intToArray(ndisconnects, btmp, 70);
    FUtil.intToArray(secsConnected, btmp, 74);
    FUtil.intToArray(secsDisconnected, btmp, 78);
    return btmp;
  }

  /**
   * new instance give a raw buffer of MAX_LENGTH size containing one UDP packet's data
   *
   * @param b Array of raw data bytes from a UDP packet
   */
  @Override
  public void reload(byte[] b) {
    doProcess(b);
  }

  /**
   * new instance give a raw buffer of MAX_LENGTH size containing one UDP packet's data
   *
   * @param b Array of raw data bytes from a UDP packet
   */
  public ConnectionStatus(byte[] b) {
    doProcess(b);
  }

  /**
   * new instance give a raw buffer of MAX_LENGTH size containing one UDP packet's data
   *
   * @param p A UDP DatagramPacket containing the data
   */
  public ConnectionStatus(DatagramPacket p) {
    //Util.prt("ConnectionStatus(DatagramPacket) len="+p.getLength()+" "+p.getData().length);
    doProcess(p.getData());
  }

  private void doProcess(byte[] buf) {
    int len = buf.length;
    b = new byte[MAX_LENGTH];
    System.arraycopy(buf, 0, b, 0, MAX_LENGTH);
    if (len < MAX_LENGTH) {
      Util.prt("ConnectionStatus too short=" + len);
    }
    StringBuilder sb = new StringBuilder(200);
    int yymmdd = FUtil.intFromArray(b, 0);
    int msecs = FUtil.intFromArray(b, 4);
    long now = Util.toGregorian2(yymmdd, msecs);
    firstTime.setTimeInMillis(now);
    //if(dbg) Util.prt("yymmdd="+yymmdd+" "+Util.yymmddFromGregorian(now)+" msec="+msecs+" "+Util.msFromGregorian(now));
    cpu = FUtil.stringFromArray(b, 8, 6);
    sort = FUtil.stringFromArray(b, 14, 2);
    proc_name = FUtil.stringFromArray(b, 16, 12);
    key = FUtil.stringFromArray(b, 28, 22);       // IP address and station
    msgType = FUtil.intFromArray(b, 50);
    remotePort = FUtil.intFromArray(b, 54);
    localPort = FUtil.intFromArray(b, 58);
    pid = FUtil.intFromArray(b, 62);
    if (dbg) {
      Util.prta("Connect build key=" + key + " rport=" + remotePort + " lport=" + localPort + " msg=" + msgType);
    }
    time.setTimeInMillis(now);                     // Time packet was received on host computer
    if (buf.length >= MAX_LENGTH + DERIVED_LENGTH) {
      nconnects = FUtil.intFromArray(buf, 66);
      ndisconnects = FUtil.intFromArray(buf, 70);
      secsConnected = FUtil.intFromArray(buf, 74);
      secsDisconnected = FUtil.intFromArray(buf, 78);
    }
  }

  @Override
  public void createRecord() {
    try {
      DBObjectOld obj = new DBObjectOld(UC.getConnection(), "tcpconnection");
      obj.setTimestamp("time", new Timestamp(time.getTimeInMillis()));
      obj.setString("cpu", cpu);
      obj.setString("sort", sort);
      obj.setString("process", proc_name);
      obj.setString("ip", getIP());
      obj.setString("station", getStation());
      obj.setInt("type", msgType);
      obj.setShort("remotePort", (short) remotePort);
      obj.setShort("localPort", (short) localPort);
      obj.setInt("pid", pid);
      obj.updateRecord(true);
      obj.close();            // we will not use this obj again, close it to free memory 
    } catch (SQLException e) {
      Util.SQLErrorPrint(e, "writing tcpconnection");
    }
  }

  /**
   * Update a existing channel with information from a new packet. The input is a generic object
   * because the UDP software is generalized to handle many types of data for distribution. Various
   * statistics are computed from the existing data and the data in the new object - whether a
   * "other stream" is working, total bytes received, last times,etc.
   *
   * @param o An object which must downcast to a ConnectionStatus
   */
  @Override
  public void update(Object o) {
    ConnectionStatus ps = (ConnectionStatus) o;

    b = ps.getData();                // Insure our buffer is the latest one!
    if (dbg) {
      Util.prta("Update " + key + " with " + toString() + " msg=" + ps.getMsgType() + " last=" + msgType
              + " from " + ps.getCpu() + ":" + ps.getProcess());
    }
    if (!ps.getKey().equals(key)) {
      Util.prt("   ************** Try to update key =" + key + " with " + ps.getKey());
    }

    // update nconnects/ndisconnects/secsDisconnect/secsCOnnect based on data
    if (ps.getMsgType() == 1) {
      nconnects++;
    }
    if (ps.getMsgType() == 2) {
      ndisconnects++;
    }
    // if this is a connect after a disconnect, compute time disconnected
    if (ps.getMsgType() == 1 && msgType == 2) {

      secsDisconnected += (ps.getTime() - time.getTimeInMillis() + 500) / 1000;
      if (dbg) {
        Util.prta("Calc secsDiscon=" + secsDisconnected + " key=" + key);
      }
    }
    // if this is a disconnect after a connect, compute the time connected
    if (ps.getMsgType() == 2 && msgType == 1) {
      secsConnected += (ps.getTime() - time.getTimeInMillis() + 500) / 1000;
      if (dbg) {
        Util.prta("Calc secsConnect=" + secsConnected + " key=" + key);
      }
    }

    // Update internal data to last values
    msgType = ps.getMsgType();
    localPort = ps.getLocalPort();
    remotePort = ps.getRemotePort();
    time.setTimeInMillis(ps.getTime());
    pid = ps.getPid();
  }

  /**
   * returns the raw data bytes that were used to create this record.  Debugging?
   */
  @Override
  public byte[] getData() {
    return b;
  }

  /**
   * The key is Network//station//channel/location
   *
   * @return the key
   */
  @Override
  public String getKey() {
    return key;
  }

  /**
   * get the station name
   *
   * @return e
   */
  public String getStation() {
    return key.substring(16, 22);
  }

  /**
   * get the IP address of the connection
   *
   * @return
   */
  public String getIP() {
    return key.substring(0, 15);
  }

  /**
   * return the originating processes name
   *
   * @return The process name
   */
  public String getProcess() {
    return proc_name;
  }

  /**
   * return the number of samples in packet
   *
   * @return message type (1=connect, 2=disconnect)
   */
  public int getMsgType() {
    return msgType;
  }

  /**
   * return the seconds that connection has been u
   *
   * @return p
   */
  public int getSecsConnected() {
    if (msgType == 1) {
      return secsConnected
              + (int) ((new GregorianCalendar().getTimeInMillis() - time.getTimeInMillis() + 500) / 1000);
    } else {
      return secsConnected;
    }
  }

  //* return the seconds disconnected()*/
  public int getSecsDisconnected() {
    if (msgType == 2) {
      return secsDisconnected
              + (int) ((new GregorianCalendar().getTimeInMillis() - time.getTimeInMillis() + 500) / 1000);
    } else {
      return secsDisconnected;
    }
  }

  //* return the seconds disconnected()*/
  public int getPid() {
    return pid;
  }

  /**
   * get number of disconnects
   *
   * @return
   */
  public int getNdisconnects() {
    return ndisconnects;
  }

  /**
   * get number of connections
   *
   * @return
   */
  public int getNconnects() {
    return nconnects;
  }

  /**
   * return the computer system that originated the UDP packet - edge collection
   *
   * @return r
   */
  public String getCpu() {
    return cpu;
  }

  /**
   * this is how old the packet receive time is relative to the current syste time
   *
   * @return
   */
  public int getAge() {
    return (int) (new GregorianCalendar().getTimeInMillis() - time.getTimeInMillis() + 500) / 1000;
  }

  /**
   * return local port connection
   *
   * @return
   */
  public int getLocalPort() {
    return localPort;
  }

  /**
   * return remote port connection
   *
   * @return
   */
  public int getRemotePort() {
    return remotePort;
  }

  /**
   * return the length of the UDP base packets
   *
   * @return
   */
  public static int getMaxLength() {
    return MAX_LENGTH + DERIVED_LENGTH;
  }

  /**
   * get the time of arrival of the packet
   */
  @Override
  public long getTime() {
    return time.getTimeInMillis();
  }

  @Override
  public String toString() {
    return "" + new Date(time.getTime().getTime()) + " " + new Time(time.getTime().getTime()) + " key="
            + key + "typ=" + msgType + " #con=" + nconnects + " #dcon=" + ndisconnects
            + " con=" + secsConnected + " dcon=" + secsDisconnected + " prc=" + proc_name + " age=" + getAge();
  }

  /**
   * Implements a sort order for channels in key order (network/station/channel/location).
   */
  @Override
  public int compareTo(Object o) {
    String s = ((ConnectionStatus) o).getKey().toLowerCase();
    return key.toLowerCase().compareTo(s);
  }
}
