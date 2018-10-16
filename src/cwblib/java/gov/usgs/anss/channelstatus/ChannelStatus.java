/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.channelstatus;

/**
 * This class wraps data in the channel UDP packets used by UdpChannel, ChannelDisplay etc to track
 * traffic and latency and last minute received on the network by channel. new instance give a raw
 * buffer of MAX_LENGTH size containing one UDP packet's data
 * <pre>
 * Off type
 * 0 int         The day encoded as yy*10000 + mm * 100 + day
 * 4 int         The milliseconds into the day (max 86400000)
 * 8 String*6    The CPU tag string
 * 14 String*2  The sort tag
 * 16 String*12 the process tag
 * 28 String 10 The source tag
 * 38 String 12 The seedname NNSSSSSCCCLL
 * 50 int       The number of samples delivered
 * 54 in        Data rate in Hz, if rate < 0, rate = 1.(-rate/1000)
 * 58 int       Packet date in yymmdd format (yy*10000+mm*100+day)
 * 62 int       Milliseconds into the day (max 86400000)
 * 66 int       Number of bytes in received packet (total packet length in native format)
 * @param b Array of raw data bytes from a UDP packet
 * </pre>
 *
 * @author ketchum
 */
import gov.usgs.anss.db.DBConnectionThread;
import java.awt.Color;
import java.net.DatagramPacket;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.DecimalFormat;
import java.util.Calendar;
import java.util.GregorianCalendar;
import gnu.trove.map.hash.TLongObjectHashMap;
import gov.usgs.anss.edgethread.EdgeThread;
import gov.usgs.anss.util.FUtil;
import gov.usgs.anss.util.StatusInfo;
import gov.usgs.anss.util.UC;
import gov.usgs.anss.util.Util;

/**
 * The ChannelStatus contains one group of information from the UDP channel system plus data derived
 * for successive updates of this data. Basically this is a SEED name, the cpu it came from, the
 * "source" (a hint as to how it got to ANSS), information about the packet (time, # samples,
 * digitizing rate), communications data (time of arrival, size of packet). The standard length of
 * the packets is in static int MAX_LENGTH and must be changed if the definition of the UDP packets
 * is changed.
 *
 * <pre>
 * Off type        Description
 * 0 int        The day encoded as yy*10000 + mm * 100 + day
 * 4 int        The milliseconds into the day (max 86400000)
 * 8 String*6   The CPU tag string
 * 14 String*2  The sort tag
 * 16 String*12 the process tag
 * 28 String 10 The source tag
 * 38 String 12 The seedname NNSSSSSCCCLL
 * 50 int       The number of samples delivered
 * 54 in        Data rate in Hz, if rate < 0, rate = 1.(-rate/1000)
 * 58 int       Packet date in yymmdd format (yy*10000+mm*100+day)
 * 62 int       Milliseconds into the day (max 86400000)
 * 66 int       Number of bytes in received packet (total packet length in native format)
 * </pre>
 *
 */
public class ChannelStatus implements Comparable, StatusInfo {

  private static final TLongObjectHashMap<Long> groupMasks = new TLongObjectHashMap<>();
  private static DBConnectionThread dbsnwchannel;
  public static final int MAX_LENGTH = 70;
  private long time;         // Time we processed message on foreign computer 
  private String key;                     // network/station/comp/location per SEED
  private byte[] bcpu = new byte[6];       // What computer the process was on
  private byte[] bsort = new byte[2];      // Sort hint, maybe not used???
  private byte[] bproc_name = new byte[12];// foreign process that sent this message
  private byte[] bsource = new byte[10];   // Some info on where the data came from  
  //String proc_name;               // foreign process that sent this message
  //String cpu;                     // What computer the process was on
  //String sort;                    // Sort hint, maybe not used???
  //String source;                  // Some info on where the data came from  
  private int ns;                         // Number of samples in packet
  private int nbytes;                     // number of bytes in the packet
  private int lastnbytes;                 // number of bytes when updateNbytes() was last called
  private int changeNbytes;               // DIfference in nbytes and lastnbytes when updateLastNbytes() last called
  private double rate;                    // Data rate in HZ
  private long packet_time;  // Time stamp in for data packet
  private int otherStream;                // if > 0, then a second stream is running this far behind in seconds
  private int otherTimeout;               // if > 0, then other stream is valid
  private long snwGroupMask = -1;              // SeisNetWatch group membership mask

  private static final DecimalFormat df = new DecimalFormat("##0.0");
  ;               // %%0.0 format
  
  byte b[];
  boolean dbg = false;
  Color background;
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

  public void setKey(String k) {
    key = k;
  }

  public long snwGroupMask() {
    return (snwGroupMask == -1 ? snwGroupMask = getSNWGroupMask(key) : snwGroupMask);
  }

  public ChannelStatus(String s) {
    key = s;
    time = System.currentTimeMillis();
    GregorianCalendar time2 = new GregorianCalendar();

    b = new byte[MAX_LENGTH];
    background = UC.purple;
    ns = -1;
    bproc_name = "PrcUnkwn     ".getBytes();
    bcpu = "CpuUnkwn".getBytes();
    bsource = "SrcUnkwn   ".getBytes();
    initPacketTime();
    time2.setTimeInMillis(time);
    packet_time = time;
    bsort = "  ".getBytes();
    packet_time -= 12 * 3600000;
    yymmdd = time2.get(Calendar.YEAR) * 10000 + (time2.get(Calendar.MONTH) + 1) * 100
            + time2.get(Calendar.DAY_OF_MONTH);
    FUtil.intToArray(yymmdd, b, 0);
    int ms = (int) (time2.getTimeInMillis() % 86400000L);
    FUtil.intToArray(ms, b, 4);
    System.arraycopy(bcpu, 0, b, 8, 6);
    System.arraycopy(bsort, 0, b, 14, 2);
    System.arraycopy(bproc_name, 0, b, 16, 12);
    System.arraycopy(bsource, 0, b, 28, 10);
    //FUtil.stringToArray(cpu,b, 8,6);
    //FUtil.stringToArray(sort, b, 14, 2);
    //FUtil.stringToArray(proc_name ,b,16,12);
    //FUtil.stringToArray(source, b,28, 10);
    FUtil.stringToArray(key, b, 38, 12);       // network/station/component/location
    FUtil.intToArray(-1, b, 50);
    FUtil.intToArray(0, b, 54);
    FUtil.intToArray(yymmdd, b, 58);  // Packet yymmdd
    FUtil.intToArray(ms, b, 62);     // packet MS
    FUtil.intToArray(0, b, 66);      // Nbytes
    snwGroupMask = getSNWGroupMask(key);

  }

  private void initPacketTime() {
    if (DBConnectionThread.noDB) {
      return;
    }
    DBConnectionThread C = DBConnectionThread.getThread("edge");
    if (C != null) {
      try {
        ResultSet rs = C.executeQuery("SELECT lastdata FROM edge."
                + "  channel WHERE channel='" + key + "'");
        rs.next();

        try {
          time = rs.getTimestamp("lastdata").getTime();
        } catch (RuntimeException e) {
          time = 0;
        }
        //if(time > 10*86400000) prt("Really old set up on "+key+" "+rs.getTimestamp("lastdata")+" age="+getAge()+" time="+time);
      } catch (SQLException e) {
        prta("SQLError ChannelStatus on key=" + key + " e=" + e);
      }
    } else {
      time = 0;
    }

  }

  private static synchronized long getSNWGroupMask(String key) {
    if (DBConnectionThread.noDB) {
      return 0l;
    }
    if (dbsnwchannel == null) {
      synchronized (groupMasks) {
        try {
          if (dbsnwchannel == null) {
            dbsnwchannel = new DBConnectionThread(Util.getProperty("DBServer"), "readonly", "edge",
                    false, false, "SNWGroupoMask", Util.getOutput());
            if (!dbsnwchannel.waitForConnection()) {
              if (!dbsnwchannel.waitForConnection()) {
                if (!dbsnwchannel.waitForConnection()) {
                  if (!dbsnwchannel.waitForConnection()) {
                    Util.prt("Failed to open " + Util.getProperty("DBServer") + " readonly as SNWGroupMask");
                  }
                }
              }
            }
          }
        } catch (InstantiationException e) {
          return 0L;
        }
        try {
          try (ResultSet rs = dbsnwchannel.executeQuery("SELECT network,snwstation,groupmask FROM edge.snwstation")) {
            while (rs.next()) {
              groupMasks.put(Util.getHashFromSeedname((rs.getString("network") + "  ").substring(0, 2) + rs.getString("snwstation")), rs.getLong("groupmask"));

              //if(rs.getString("snwstation").indexOf("RSSD") >=0)
              //  prt(groupMasks.size()+" Mask for "+rs.getString("snwstation")+" is "+Util.toHex(rs.getLong("groupmask")));
            }
          }
          Util.prt("SNWGroupmask size=" + groupMasks.size());
        } catch (SQLException e) {
          Util.SQLErrorPrint(e, "SQL error getting SNW station mask");
        }
      }
    }
    Long l = groupMasks.get(Util.getHashFromSeedname(key.substring(0, 7)));
    //if(key.substring(0,7).indexOf("RSSD") >=0) prt("Return for key="+key+" is "+(l == null?"null":Util.toHex(l.longValue())));
    if (l == null) {
      return 0L;
    } else {
      return l;
    }
  }

  /**
   * Creates a new instance of ChannelStatus with all Z's in the key
   */
  public ChannelStatus() {
    key = "ZZZZZZZZZZZZ";
    time = System.currentTimeMillis();
    b = new byte[MAX_LENGTH];
    background = UC.white;
    snwGroupMask = 0;
  }

  @Override
  public synchronized void reload(byte[] b) {
    doProcess(b);
  }

  /**
   * new instance give a raw buffer of MAX_LENGTH size containing one UDP packet's data
   *
   * @param b Array of raw data bytes from a UDP packet
   */
  public ChannelStatus(byte[] b) {
    doProcess(b);
  }

  /**
   * new instance give a raw buffer of MAX_LENGTH size containing one UDP packet's data
   *
   * @param p A UDP DatagramPacket containing the data
   */
  public ChannelStatus(DatagramPacket p) {
    //prt("ChannelStatus(DatagramPacket) len="+p.getLength()+" "+p.getData().length);
    doProcess(p.getData());
  }
  // class variables used by doProcess.
  private int len;
  private long now;
  private int yymmdd;
  private int msecs;
  private int irate;
  private int packet_date;
  private int packet_ms;
  private boolean newkey;

  private void doProcess(byte[] buf) {
    len = buf.length;
    if (b == null) {
      b = new byte[MAX_LENGTH];
    }
    System.arraycopy(buf, 0, b, 0, MAX_LENGTH);
    if (len < MAX_LENGTH) {
      prt("ChannelStatus too short=" + len);
    }
    //StringBuffer sb = new StringBuffer(200);
    yymmdd = FUtil.intFromArray(b, 0);
    msecs = FUtil.intFromArray(b, 4);
    try {
      now = Util.toGregorian2(yymmdd, msecs);
    } catch (RuntimeException e) {
      now = 1000000L;
      prta("bad tim to ChannelStatus ymmdd=" + yymmdd + " msec=" + msecs + " comp=" + FUtil.stringFromArray(b, 38, 12));
    }
    if(now < time) return;
    //if(dbg) prt("yymmdd="+yymmdd+" "+Util.yymmddFromGregorian(now)+" msec="+msecs+" "+Util.msFromGregorian(now));
    //cpu = FUtil.stringFromArray(b, 8,6);
    //sort = FUtil.stringFromArray(b, 14, 2);
    //proc_name = FUtil.stringFromArray(b,16,12);
    //source = FUtil.stringFromArray(b,28, 10);
    //key = FUtil.stringFromArray(b, 38,12).toUpperCase();       // network/station/component/location
    System.arraycopy(b, 8, bcpu, 0, 6);
    System.arraycopy(b, 14, bsort, 0, 2);
    System.arraycopy(b, 16, bproc_name, 0, 12);
    System.arraycopy(b, 28, bsource, 0, 10);
    //cpu = new String(b, 8,6);
    //sort = new String(b, 14, 2);
    //proc_name = new String(b,16,12);
    //source = new String(b,28, 10);
    newkey = false;
    if (key == null) {
      newkey = true;
    } else {
      for (int i = 0; i < 12; i++) {
        if (key.charAt(i) != (char) b[i + 38]) {
          newkey = true;
          break;
        }
      }
    }
    if (newkey) {
      key = new String(b, 38, 12).toUpperCase();
      snwGroupMask = -1;      // invalidate the mask
      // network/station/component/location
      if (!Util.isValidSeedName(key)) {
        throw new RuntimeException("Illegal seedname in Channel status=" + key);
      }
    }
    ns = FUtil.intFromArray(b, 50);
    irate = FUtil.intFromArray(b, 54);
    if (irate == 0) {
      rate = 0.5;
    } else if (irate > 0) {
      rate = (double) irate / 1000.;
    } else {
      rate = 1. / ((double) -irate / 1000);
    }
    //if(key != null)
    //  if(key.substring(0,10).equals("IUHKT  VHZ"))
    //    prt("GRFO yymmdd="+yymmdd+" msecs="+msecs);
    packet_date = FUtil.intFromArray(b, 58);
    packet_ms = FUtil.intFromArray(b, 62);
    if(packet_ms < 0) {
      prta("ChannelStatus: ** negative packet time="+packet_ms+" How does this happen? "+b[62]+" "+b[63]+" "+b[64]+" "+b[65]);
    }
    nbytes = FUtil.intFromArray(b, 66);
    try {
      packet_time = Util.toGregorian2(packet_date, packet_ms);
    } catch (RuntimeException e) { // if the date is way out of range, pick it off and force to earlier date
      if (e.getMessage().contains("data out of range")) {
        prta("ChannelStatus: ** " + key + " " + getProcess().trim() + " src=" + getSource().trim()
                + " cpu=" + getCpu().trim() + ":" + e.getMessage());
        packet_time = 1000000L;
      } else {
        throw e;
      }
    }
    if (dbg) {
      prt("key=" + key + " rate=" + rate + " ns=" + ns + " date=" + packet_date + " ms=" + packet_ms + " nbytes=" + nbytes);
    }
    time = now;                     // Time packet was received on host computer
    // Move this to getSNWMask()     snwGroupMask = getSNWGroupMask(key);
  }

  /**
   * Update a existing channel with information from a new packet. The input is a generic object
   * because the UDP software is generalized to handle many types of data for distribution. Various
   * statistics are computed from the existing data and the data in the new object - whether a
   * "other stream" is working, total bytes received, last times,etc.
   *
   * @param o An object which must downcast to a ChannelStatus
   */
  @Override
  public void update(Object o) {
    ChannelStatus ps = (ChannelStatus) o;

    if (!ps.getKey().equals(key)) {
      prt("   ************** Try to update key =" + key + " with " + ps.getKey());
    }
    long ms = (ps.getPacketTime() - packet_time);
    nbytes += ps.getNbytes();
    if (ms < 0) {
      otherStream = (int) -ms / 1000;
      if (dbg) {
        prta("Update found older by " + otherStream + ps.toString());
      }
      otherTimeout = 20;
      return;
    }
    if (otherStream > 0 && otherTimeout > 0) {
      otherTimeout--;
    }
    if (otherTimeout == 0) {
      otherStream = 0;
    }
    System.arraycopy(ps.getData(), 0, b, 0, MAX_LENGTH);
    if (dbg) {
      prta("Update " + key + " with " + ps.toString());
    }
    ns = ps.getNsamp();
    if (ps.getPacketTime() - System.currentTimeMillis() > 86400000) {
      packet_time = System.currentTimeMillis() + 86400000;
    } else {
      packet_time = ps.getPacketTime();
    }
    rate = ps.getRate();
    time = Math.max(time,ps.getTime());
    System.arraycopy(ps.bsource, 0, bsource, 0, 10);
    System.arraycopy(ps.bsort, 0, bsort, 0, 2);
    System.arraycopy(ps.bproc_name, 0, bproc_name, 0, 12);
    System.arraycopy(ps.bcpu, 0, bcpu, 0, 6);
    //if(!ps.getSource().equals(source)) source = ps.getSource();
    //if(!ps.getProcess().equals(proc_name)) proc_name=ps.getProcess();
    //if(!ps.getCpu().equals(cpu)) cpu = ps.getCpu();
    //if(!ps.getSort().equals(sort)) sort = ps.getSort();
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
   * return the originating processes name
   *
   * @return The process name
   */
  public String getProcess() {
    return new String(bproc_name, 0, 12);
  }

  /**
   * return the number of samples in packet
   *
   * @return number of sample
   */
  public int getNsamp() {
    return ns;
  }

  /**
   * return the sample rate
   *
   * @return The data sample rate
   */
  public double getRate() {
    return rate;
  }

  /**
   * Return the source. The source is some way of grouping data that makes sense when analyzing from
   * a communications point of view. Mostly a source matches up with a SEED network code, but
   * sometimes the same network comes on different paths and this gives a hint as to how to view
   * from a communication point of view
   *
   * @return The source of the data
   */
  public String getSource() {
    return new String(bsource, 0, 10);
  }

  /**
   * Return sort hint
   *
   * @return The Sorting criteria
   */
  public String getSort() {
    return new String(bsort, 0, 2);
  }

  /**
   * return the Network portion of seed name
   *
   * @return The network (1st two characters of key)
   */
  public String getNetwork() {
    return key.substring(0, 2);
  }

  /**
   * return the computer system that originated the UDP packet - edge collector
   *
   * @return The CPU string
   */
  public String getCpu() {
    return new String(bcpu, 0, 6);
  }

  /**
   * return the raw bytes for the cpu (6 bytes)
   *
   * @return The 6 cpu bytes
   */
  public byte[] getCpuBuf() {
    return bcpu;
  }

  /**
   * return the raw bytes for the sort (2 bytes)
   *
   * @return the 2 sort bytes
   */
  public byte[] getSortBuf() {
    return bsort;
  }

  /**
   * Return the raw source bytes (10 bytes)
   *
   * @return The 10 Source bytes
   */
  public byte[] getSourceBuf() {
    return bsource;
  }

  /**
   * Return the raw process byes (12 bytes)
   *
   * @return The raw process name bytes (12)
   */
  public byte[] getProcessBuf() {
    return bproc_name;
  }

  /**
   * Return total number of bytes received on this channel since the UDP server was started
   *
   * @return Number of bytes received since UPD server startup
   */
  public int getNbytes() {
    return nbytes;
  }

  /**
   * Return the number of bytes last updated via updateLastNbytes. This is used to compute numbers
   * of bytes over time by the Display Programs.
   *
   * @return The last nbytes
   */
  public int getLastNbytes() {
    return lastnbytes;
  }

  /**
   * returns number of bytes changed between calls to updateLastNbytes()
   *
   * @return The nbytes change
   */
  public int getNbytesChange() {
    return changeNbytes;
  }

  /**
   * cause the total number of bytes to be recorded and the number of bytes changed since last call
   * to this routine to be recorded. This is used by applications like the display routines to track
   * changes over time intervals.
   */
  public void updateLastNbytes() {
    changeNbytes = nbytes - lastnbytes;
    lastnbytes = nbytes;
  }

  /**
   * This is the time of the data in the packet
   *
   * @return The packet time
   */
  public long getPacketTime() {
    return packet_time;
  }

  /**
   * This is the time of the data in the packet
   *
   * @return The packet time
   */
  public long getNextPacketTime() {
    return packet_time + (int) (ns * 1000 / rate);
  }

  /**
   * this is how old the packet receive time is relative to the current system time in minutes
   *
   * @return The packet age in minutes
   */
  public double getAge() {
    return (System.currentTimeMillis() - time) / 60000. + .05;
  }

  /**
   * compute latency of the other stream (if any)
   *
   * @return The other stream latency in minutes
   */
  public int getOtherStreamLatency() {
    return (otherStream + 30) / 60;
  }

  /**
   * return the latency of the current stream, that is how old the last data in the last packet was
   * at the time it arrived
   *
   * @return The latency in seconds
   */
  public double getLatency() {
    return (time - packet_time - (ns - 1) * 1000 / rate) / 1000.;
  }

  /**
   * return the length of the UDP base packets
   *
   * @return The max length parameter
   */
  public static int getMaxLength() {
    return MAX_LENGTH;
  }

  /**
   * get the time of arrival of the packet
   */
  @Override
  public long getTime() {
    return time;
  }

  /**
   * get remoteIP NEEDS
   *
   * @return The remote IP
   */
  public String getRemoteIP() {
    return "";
  }

  /**
   * get remote process
   *
   * @return The remote process
   */
  public String getRemoteProcess() {
    return "";
  }
  StringBuilder sb = new StringBuilder(100);

  @Override
  public String toString() {
    return toStringSB().toString();
  }

  public StringBuilder toStringSB() {
    return Util.clear(sb).append("").append(Util.ascdatetime2(time)).append(" k=").append(key).
            append(" ").append(getSource()).append(" pk=").append(Util.ascdatetime2(packet_time)).
            append(" ns=").append(ns).append(" nb=").append(nbytes).
            append(" rt=").append(df.format(rate)).append(" age=").append(df.format(getAge())).
            append(" ltcy=").append(df.format(getLatency())).append(" cpu=").append(getCpu());
  }

  /**
   * Implements a sort order for channels in key order (network/station/channel/location).
   */
  @Override
  public int compareTo(Object o) {
    return key.compareTo(((ChannelStatus) o).getKey());
  }

  @Override
  public void createRecord() {
  }

  @Override
  public byte[] makeBuf() {
    return b;
  }

}
