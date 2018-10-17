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
import java.util.GregorianCalendar;
import java.text.DecimalFormat;

/**
 * ProcessStatus.java - contains messages in both raw and decoded form for the UDP status packets.
 * This information is used by the UdpProcess and served to any application needing such data. The
 * information recorded includes the cpu, process name, time of report, cputime, up time, state,
 * paging, memory usage,etc. The static variable MAX_LENGTH is the "on the wire" size of the packet
 * and must be adjusted by all programs simultaneously if new information needs to be included.
 *
 * @author D.C.Ketchum
 */
public final class ProcessStatus implements Comparable, StatusInfo {

  public static final String[] states = {"UNKN", "COLPG", "MWAIT", "CEF", "PFW", "LEF", "LEFO", "HIB",
    "HIBO", "SUSP", "SUSPO", "FPG", "COM", "COMO", "CUR"};
  static int MAX_LENGTH = 46;
  private GregorianCalendar time = new GregorianCalendar();
  private String key;                   // cpu(6)-sort(2)-proc_name(12)
  private int cputime;                // ticks in ms
  private int uptime;               // in secs
  private short state;
  private double pct;
  private int memory;                 // in k bytes
  private int paging;
  private int lastpaging;
  private byte[] b;
  private static final DecimalFormat df = new DecimalFormat("##0.0");
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
   * Creates a new instance of ProcessStatus
   */
  public ProcessStatus() {
    key = "ZZZZZZZZZZZZZZZZZZZZZ";
    time = new GregorianCalendar();
    b = new byte[MAX_LENGTH];
  }

  public ProcessStatus(String s) {
    key = s;
    time = new GregorianCalendar();
    b = new byte[MAX_LENGTH];
  }

  /**
   * create instance from raw on the wire bytes
   *
   * @param b the raw MAX_LENGTH bytes
   */
  @Override
  public void reload(byte[] b) {
    doProcess(b);
  }

  /**
   * create instance from raw on the wire bytes
   *
   * @param b the raw MAX_LENGTH bytes
   */
  public ProcessStatus(byte[] b) {
    doProcess(b);
  }

  /**
   * new instance from a full DatagramPacket containing MAX_LENGTH data
   *
   * @param p the DatagramPacket
   */
  public ProcessStatus(DatagramPacket p) {
    //prt("Processstatus(DatagramPacket) len="+p.getLength()+" "+p.getData().length);
    doProcess(p.getData());
  }

  private void doProcess(byte[] buf) {
    boolean dbg = false;
    int len = buf.length;
    b = new byte[MAX_LENGTH];
    System.arraycopy(buf, 0, b, 0, MAX_LENGTH);
    if (len < MAX_LENGTH) {
      prt("ProcessStatus too short=" + len);
    }
    StringBuilder sb = new StringBuilder(200);
    int yymmdd = FUtil.intFromArray(b, 0);
    int msecs = FUtil.intFromArray(b, 4);
    long now = Util.toGregorian2(yymmdd, msecs);
    if (dbg) {
      prt("yymmdd=" + yymmdd + " " + Util.yymmddFromGregorian(now) + " msec=" + msecs + " " + Util.msFromGregorian(now));
    }
    key = FUtil.stringFromArray(b, 8, 20);
    if (dbg) {
      prt("Key=" + key);
    }
    int cpu = FUtil.intFromArray(b, 28);
    memory = FUtil.intFromArray(b, 32);
    paging = FUtil.intFromArray(b, 36);
    uptime = FUtil.intFromArray(b, 40);
    state = FUtil.shortFromArray(b, 44);
    if (dbg) {
      prt("state=" + state + " mem=" + memory + " pag=" + paging);
    }
    if (state < 0 || state > 14) {
      state = 0;
    }
    if (time == null) {
      pct = 0.;
    } else {
      pct = (cpu - cputime) * 0.001 / (now - time.getTimeInMillis()) * 100.;
    }
    time.setTimeInMillis(now);
    cputime = cpu;
  }

  /**
   * when a new packet is recovered the main packets are updated with the latest info and certain
   * parameters calculated here.
   *
   * @param o The packet of ProcessStatus information
   */
  @Override
  public void update(Object o) {
    ProcessStatus ps = (ProcessStatus) o;
    double ms = (double) (ps.getTime() - time.getTimeInMillis());
    if (ms > 0) {
      pct = 100. * ((double) (ps.getCputime() - cputime)) / ms;
    } else {
      pct = 0.;
    }
    b = ps.getData();                // Insure our buffer is the latest one!
    if (!ps.getKey().equals(key)) {
      prt("Try to update key =" + key + " with " + ps.getKey());
    }
    cputime = ps.getCputime();
    lastpaging = paging;
    paging = ps.getPaging();
    memory = ps.getMemory();
    state = ps.getState();
    time.setTimeInMillis(ps.getTime());
  }

  /**
   * return the raw bytes for this process status
   *
   * @return the raw bytes
   */
  @Override
  public byte[] getData() {
    return b;
  }

  /**
   * return the key
   *
   * @return String with key
   */
  @Override
  public String getKey() {
    return key;
  }

  /**
   * return the cpu
   *
   * @return cpu name
   */
  public String getCpu() {
    return key.substring(0, 6);
  }

  /**
   * return the process name
   *
   * @return String with process name
   */
  public String getProcessName() {
    return key.substring(8, 20);
  }

  /**
   * return the Sort hint
   *
   * @return String with sort hint
   */
  public String getSort() {
    return key.substring(6, 8);
  }

  /**
   * return the cpu time in millisecond ticks
   *
   * @return String with millisecond ticks
   */
  public int getCputime() {
    return cputime;
  }

  /**
   * return the cpu Percentage estimate (cputime-lastcputime)/(time-lasttime)/10.
   *
   * @return cpuPercent estimate
   */
  public double getCpuPercent() {
    return pct;
  }

  /**
   * return the last value of paging variable
   *
   * @return last paging
   */
  public int getLastPaging() {
    return lastpaging;
  }

  /**
   * return the current paging
   *
   * @return current paging
   */
  public int getPaging() {
    return paging;
  }

  /**
   * return the memory usage
   *
   * @return memory usage
   */
  public int getMemory() {
    return memory;
  }

  /**
   * return the state of the CPU - see getStateString() for more info
   *
   * @return state encoding
   */
  public short getState() {
    return state;
  }

  /**
   * return the string corresponding to the state
   *
   * @return String with State
   */
  public String getStateString() {
    return states[state];
  }

  /**
   * return the length of on-the-wire raw packets
   *
   * @return msg length
   */
  public static int getMaxLength() {
    return MAX_LENGTH;
  }

  /**
   * return the time of process information
   *
   * @return time of packet
   */
  @Override
  public long getTime() {
    return time.getTimeInMillis();
  }

  /**
   * return uptime of process
   *
   * @return the uptime in seconds
   */
  public int getUptime() {
    return uptime;
  }

  /**
   * get uptime as a string padded out with zeros
   *
   * @return the uptime as ddd-hr:mm
   */
  public String getUptimeString() {

    int s = uptime;
    int days = s / 86400;
    s %= 86400;
    int hr = s / 3600;
    s %= 3600;
    int min = s / 60;
    s %= 60;
    return "" + Util.df3(days) + "-" + Util.df2(hr) + ":" + Util.df2(min);  //+":"+df.format(s);
  }

  /**
   * dump string with most of the known data
   *
   * @return long string with dump of data
   */
  @Override
  public String toString() {
    return "" + new Date(time.getTime().getTime()) + " " + new Time(time.getTime().getTime()) + " key="
            + key.trim() + " cpu=" + cputime + " state=" + states[state] + " pct=" + df.format(pct)
            + " mem=" + memory + " page=" + paging + " " + (paging - lastpaging);
  }

  /**
   * basic comparison for ordering by cpu-process
   *
   * @param o Object to compare to
   * @return -1, 0, -1 for <, = or >
   */
  @Override
  public int compareTo(Object o) {
    String s = ((ProcessStatus) o).getKey().toLowerCase();
    return key.toLowerCase().compareTo(s);
  }

  @Override
  public void createRecord() {
  }

  @Override
  public byte[] makeBuf() {
    return b;
  }

}
