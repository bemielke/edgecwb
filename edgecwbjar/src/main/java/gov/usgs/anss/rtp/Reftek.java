/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.rtp;

import gov.usgs.alarm.SendEvent;
import gov.usgs.anss.edgemom.ReftekClient;
import gov.usgs.anss.edgethread.EdgeThread;
import gov.usgs.anss.util.SeedUtil;
import gov.usgs.anss.util.Util;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.GregorianCalendar;
import java.util.Calendar;

/**
 * This class encapsulates all of the status and configuration data for a single
 * Reftek Unit. The class is normally created by the configuration pass. The
 * ReftekClient then uses object of this class to update the status information
 * from AD, AQ, DK, and US status query blocks. This run() method monitors for
 * the status blocks to have been all updated since the last update and writes
 * the DASSTATUS and SNW with the results.
 *
 * @author davidketchum
 */
public final class Reftek extends Thread {

  private static final GregorianCalendar gg = new GregorianCalendar();
  private final DecimalFormat df = new DecimalFormat("00");
  private static int nreftek;
  private final int unit;
  private final String ipadr;
  private final String network;
  private final String station;

  private final ArrayList<DataStreamInfo> dataStream = new ArrayList<>(5);// From configuration
  private StationChannel sc;
  private OperatingMode om;
  private final ArrayList<Sensor> sensors = new ArrayList<>(4);     // derived from AD records

  // These are the fields from the SOH records
  private String version;
  private String gpsPosition;
  private double batteryVoltage;
  private double temperature;
  private double backupVoltage;
  private int memoryUsed, memoryAvailable, memoryTotal;
  private String sensor1, sensor2;
  private int clockPhaseError;
  private String clockType;
  private String[] ipadrs;
  private String[] netmasks;
  private String[] gateways;
  private String[] hosts;
  private final byte[] scratch = new byte[300];

  private XC xc;    // external clock
  private DK dk;    // disk Status
  private US us;    // Unit Status
  private AQ aq;    // Acquisition status

  private final StringBuilder unknown = new StringBuilder(200);
  private final StringBuilder snw = new StringBuilder(400);
  private final StringBuilder tmpsb = new StringBuilder(400);

  private EdgeThread par;
  private boolean terminate;

  public void terminate() {
    terminate = true;
    dataStream.clear();
    sensors.clear();
    Util.clear(unknown);
    unknown.trimToSize();
  }
  int npackets;
  /** The unknown collects status messages that are not known to this software in loadSH.
   * These messages are already logged, but user software can get the entire StringBuilder of them
   * here.  Best to call clearUnknown just after it.  The size of the Unknown String builder is
   * limited to 100000 character, it is then trimmed by 50,000.
   * 
   * @return 
   */
  public StringBuilder getUnknown() {
    return unknown;
  }
  public void clearUnknown() {
    Util.clear(unknown);
  }

  public double getRate(int stream) {
    try {
      DataStreamInfo s = dataStream.get(stream);
      if (s == null) {
        return 0.;
      }
      return s.getRate();
    } catch (RuntimeException e) {
      par.prt(Util.clear(tmpsb).append(" *** Runtime stream OOR rate stream=").append(stream).append(" ").append(toStringLong()));
      e.printStackTrace(par.getPrintStream());
      if (e.getMessage() != null) {
        if (e.getMessage().contains("OutOfMem")) {
          SendEvent.doOutOfMemory("In load ET out of memory", this);
          throw e;
        }
      }
      return 0.;
    }

  }
  private final StringBuilder seedname = new StringBuilder(12);

  public StringBuilder getSeedname(int stream, int chan) {
    DataStreamInfo s;
    try {
      s = dataStream.get(stream);
    } catch (RuntimeException e) {
      par.prt(Util.clear(tmpsb).append(" *** Runtime stream OOR seedname for stream=").append(stream).
              append(" chan=").append(chan).append(" ").append(toStringLong()));
      e.printStackTrace(par.getPrintStream());
      if (e.getMessage() != null) {
        if (e.getMessage().contains("OutOfMem")) {
          SendEvent.doOutOfMemory("In load ET out of memory", this);
          throw e;
        }
      }
      return Util.clear(seedname).append("XX").append((Util.toHex(unit).substring(2) + "       ").substring(0, 5)).append("BH").append('X' + chan).append("  ");
    }
    if (s == null) {
      for (int i = 0; i < dataStream.size(); i++) {
        if (dataStream.get(i) != null) {
          par.prt(Util.clear(tmpsb).append(" No translation i=").append(i).append(" ds=").append(dataStream.get(i)).
                  append(" str=").append(stream).append(" ch=").append(chan).append(" ").append(network).append(station));
        }
      }
      par.prt(Util.clear(tmpsb).append("*** Got untranslatable stream=").append(stream).
              append(" chan=").append(chan).append(" ").append(toStringLong()));
      return Util.clear(seedname).append("XX").append((Util.toHex(unit).substring(2) + "     ").substring(0, 5)).
              append("BH").append('X' + chan).append("  ");
    }
    try {
      String comp = s.getSeedComponent(chan);
      if (comp == null) {
        return null;
      }
      //if(comp.indexOf(" ") >=0) par.prta("**** bad seedname "+network+station+" comp="+comp+"| chan="+chan+" DSinfo="+s.toString());
      return Util.clear(seedname).append(network).append((station + "     ").substring(0, 5)).append(comp).append(s.getLocation(chan));
    } catch (RuntimeException e) {
      par.prt(Util.clear(tmpsb).append("*** Runtime trying to do seedname for stream=").append(stream).
              append(" chan=").append(chan).append(" ").append(toStringLong()).append(" e=").append(e));
      e.printStackTrace(par.getPrintStream());
      if (e.getMessage() != null) {
        if (e.getMessage().contains("OutOfMem")) {
          SendEvent.doOutOfMemory("In load ET out of memory", this);
          throw e;
        }
      }
      return Util.clear(seedname).append("XX").append((Util.toHex(unit).substring(2) + "     ").substring(0, 5)).append("BH").append('X' + chan).append("  ");

    }
  }

  public void incrementPacketCount() {
    npackets++;
  }

  public int getNpackets() {
    return npackets;
  }
  /**
   * get a long millis for the given Reftek time yyyy:ddd:hh:mm:ss
   *
   * @param time The Time string to decode from yyyy:ddd:hh:mm:ss to millis
   * @return the millis since 1970
   */
  private static final int[] ymd1 = new int[3];
  private final int[] ymd2 = new int[3];

  public static long getTime(String time) {
    int year = Integer.parseInt(time.substring(0, 4));
    int doy = Integer.parseInt(time.substring(5, 8));
    int hr = Integer.parseInt(time.substring(9, 11));
    int min = Integer.parseInt(time.substring(12, 14));
    int sec = Integer.parseInt(time.substring(15, 17));
    SeedUtil.ymd_from_doy(year, doy, ymd1);
    gg.set(year, ymd1[1] - 1, ymd1[2], hr, min, sec);
    return gg.getTimeInMillis();
  }

  /**
   * build the DASStatus string. Note: Massposition and temperatures fixed from
   * (int) masspos*10+0.5 to (int) Math.round(masspos*10.) on Mar 3 2014 about
   * 1800 UTC At that time only the first mass position was recorded.
   *
   * @return A dasstatus string
   */
  public StringBuilder doDASStatusString() {
    try {
    Util.clear(snw);

    if (us == null) {
      return null;
    }
    int doy = Integer.parseInt(us.getTime().substring(5, 8));
    int year = Integer.parseInt(us.getTime().substring(0, 4));
    SeedUtil.ymd_from_doy(year, doy, ymd2);

    snw.append("status^dasstatus^time=").append(ymd2[0]).append("-").append(df.format(ymd2[1])).
            append("-").append(df.format(ymd2[2])).append(" ").append(us.getTime().substring(9));
    snw.append(";station=").append((network + station + "     ").substring(0, 7));
    snw.append(";dastype=13");
    int isens = 0;
    for (Sensor sensor : sensors) {
      if (sensor != null) {
        switch (isens) {
          case 0:
            snw.append(";masspos1=").append((int) Math.round(sensor.masspos[0] * 10.)).append(";masspos2=").
                    append((int) Math.round(sensor.masspos[1] * 10.)).append(";masspos3=").append((int) Math.round(sensor.masspos[2] * 10.));
            break;
          case 1:
            snw.append(";masspos4=").append((int) Math.round(sensor.masspos[0] * 10.)).append(";masspos5=").
                    append((int) Math.round(sensor.masspos[1] * 10.)).append(";masspos6=").append((int) Math.round(sensor.masspos[2] * 10.));
            break;
          default:
            par.prta(" *** Got weird sensor number=" + sensor + " idx=" + isens + " skipping snw at " + network + " " + station + " #sensor=" + sensors.size());
            break;
        }
      }
      isens++;
    }
    snw.append(";clockQuality=").append(xc.timeQuality()).append(";gpsbits=").
            append(xc.getGPSBits()).append(";pllbits=").append(xc.getPLLBits()).
            append(";pllint=").append(xc.getPLLInt()).append(";volt1=").
            append((int) Math.round(us.getInputVolts() * 10.)).append(";volt2=").
            append((int) Math.round(us.getBackupVolts() * 10.)).append(";temp1=").
            append(us.getTemperature()).append(";timebase=").append(xc.getTimeBase());
    if (!snw.toString().contains("null") && ReftekClient.getStatusSender() != null) {
      ReftekClient.getStatusSender().queueDBMsg(snw);
    }
    return snw;
    }
    catch(RuntimeException e) {
      par.prt("Error getting DASString from SNW e="+e);
      e.printStackTrace(par.getPrintStream());
    }
    return null;
  }
  private final StringBuilder senditsb = new StringBuilder(10);
  private int senditCount;

  private void sendit(StringBuilder snw) {
    synchronized (senditsb) {
      if (snw.charAt(snw.length() - 1) == ';') {
        snw.delete(snw.length() - 1, snw.length());
      }
      Util.clear(senditsb).append(network).append("-").append(station);
      for (int i = senditsb.length() - 1; i >= 0; i--) {
        if (senditsb.charAt(i) == ' ') {
          senditsb.delete(i, i + 1);
        }
      }
      senditsb.append(snw).append('\n');
      ReftekClient.getSNWSender().queue(senditsb);
      senditsb.delete(senditsb.length() - 1, senditsb.length());    // remove the newline
      if (senditCount++ % 50 == 0) {
        senditsb.append(" #snw=").append(senditCount);
        par.prta(senditsb);
      }
    }
  }
  StringBuilder doSNWsb = new StringBuilder(10);

  public void doSNWSenderString() {
    try {
      if (ReftekClient.getSNWSender() != null) {
        Util.clear(doSNWsb).append(":1:Reftek Unit Number=").append(Util.toHex(unit).delete(0, 2));
        sendit(doSNWsb);
        //ReftekClient.getSNWSender().queue(network+"-"+station.trim()+":1:Reftek Unit Number="+Util.toHex(unit).substring(2));
      } else {
        return;        // cannot send if none is defined
      }
      if (xc != null) {
        sendit(xc.getSNWSenderString());
      }
      if (dk != null) {
        sendit(dk.getSNWSenderString());
      }
      if (us != null) {
        sendit(us.getSNWSenderString());
      }
      if (aq != null) {
        sendit(aq.getSNWSenderString());
      }
      for (Sensor sensor : sensors) {
        if (sensor != null) {
          sendit(sensor.getSNWSenderString());
        }
      }
    }
    catch(RuntimeException e) {
      par.prt("Error doSNWSenderString() e="+e);
      e.printStackTrace(par.getPrintStream());
    }
  }

  @Override
  public String toString() {
    return toStringBuilder(null).toString();
  }

  public final StringBuilder toStringBuilder(StringBuilder tmp) {
    StringBuilder sb = tmp;

    if (sb == null) {
      sb = Util.clear(snw);
    }
    sb.append("Config:").append(Util.toHex((short) unit).substring(2)).append(" #pck=").append(npackets).
            append(" NNSS=").append(network).append(station).
            append(" ip=").append((ipadr + "                ").substring(0, 15)).append(" ").append(nreftek);
    return sb;
  }

  public String getStatusString() {
    String s = toStringLong().toString();
    npackets = 0;
    return s;
  }

  public XC getXC() {
    return xc;
  }

  public int getTimingQuality() {
    if (xc == null) {
      return getTimeQuality();
    } else {
      return xc.timeQuality();
    }
  }

  public StringBuilder toStringLong() {
    Util.clear(snw).append(toStringBuilder(null));
    for (int i = 0; i < dataStream.size(); i++) {
      if (dataStream.get(i) != null) {
        snw.append("DS:").append(i).append(" ").append(dataStream.get(i)).append("  ");
      }
    }
    if (xc != null) {
      snw.append("\n ").append(xc.toStringBuilder(null));
    }
    if (us != null) {
      snw.append("\n  US: ").append(us.toStringBuilder(null));
    }
    if (aq != null) {
      snw.append("\n  AQ: ").append(aq.toStringBuilder(null));
    }
    if (sensors != null) {
      for (Sensor sensor : sensors) {
        if (sensor != null) {
          snw.append("\n  AD: ").append(sensor.toStringBuilder(null));
        }
      }
    }
    return snw;
  }

  public StringBuilder getSOH() {
    Util.clear(snw);
    snw.append("    Status: ").append(batteryVoltage).append("V" + " temp=").append(temperature).
            append("C Bvolt=").append(backupVoltage).append(" Mem: used=").append(memoryUsed).
            append(" Free=").append(memoryAvailable).append(" ctype=").append(clockType).append(" PhaseErr=").append(clockPhaseError).append("uS\n");
    snw.append("    Version:").append(version).append(" Position:").append(gpsPosition).append(" Sens1=").append(sensor1).
            append(" sensor2=").append(sensor2).append("\n");
    if (ipadrs != null) {
      snw.append("    IPaddr:");
      for (String ipadr1 : ipadrs) {
        snw.append(ipadr1).append(" ");
      }
      snw.append("\n");
    }
    if (netmasks != null) {
      snw.append("    Masks :");
      for (String netmask : netmasks) {
        snw.append(netmask).append(" ");
      }
      snw.append("\n");
    }
    if (gateways != null) {
      snw.append("    Gatewy:");
      for (String gateway : gateways) {
        snw.append(gateway).append(" ");
      }
      snw.append("\n");
    }
    if (hosts != null) {
      snw.append("    Hosts :");
      for (String host : hosts) {
        snw.append(host).append(" ");
      }
      snw.append("\n");
    }
    return snw;
  }

  /**
   * Construct a new Reftek holder
   *
   * @param u The unit number
   * @param ip IP address of unit
   * @param net Seed network (2 characters)
   * @param stat Station name
   * @param parent The parent for logging
   */
  public Reftek(int u, String ip, String net, String stat, EdgeThread parent) {
    unit = u;
    ipadr = ip.trim();
    network = net;
    station = (stat + "     ").substring(0, 5);
    par = parent;
    for (int i = 0; i < 16; i++) {
      dataStream.add(i, null);
    }
    for (int i = 0; i < 4; i++) {
      sensors.add(i, null);
    }
    par.prta(Util.clear(tmpsb).append("Create reftek=").append(toStringBuilder(null)));
    nreftek++;
    //gov.usgs.anss.util.Util.prta("new Reftek "+getName()+" "+getClass().getSimpleName()+" u="+u+" "+ip+" "+stat);
    start();
  }

  public boolean addStream(int stream, String ch, String co, String loc, double rate, String bnd) {
    if (dataStream.get(stream) == null) {
      dataStream.set(stream, new DataStreamInfo(stream, loc, ch, co, rate, bnd));
      return true;
    } else {
      dataStream.get(stream).set(loc, ch, co, bnd, rate);
    }
    return false;
  }

  public void loadAttn(ByteBuffer bb, EdgeThread par) {
    int commandPosition = bb.position();
    try {
      bb.get(scratch, 0, 4);
      //int byteCount = (short) Integer.parseInt(new String(scratch, 0, 4));
      bb.get(scratch, 0, 2);
      String commandCode = new String(scratch, 0, 2);
      //if(!commandCode.equals("PR")) 
      //  par.prta("loatATTN cmd="+commandCode+" "+((char) bb.get())+" "+((char) bb.get())+" cnt="+byteCount);
      //bb.position(bb.position()-2);
      switch (commandCode) {
        case "SS":
          bb.get(scratch, 0, 2);
          String statusType = new String(scratch, 0, 2);
          switch (statusType) {
            case "AD":
              bb.get(scratch, 0, 18);
              String time = new String(scratch, 0, 18).trim();
              int sensorCount = bb.get() - 48;
              bb.get();     // skip space
              for (int i = 0; i < sensorCount; i++) {
                int pos = bb.position();
                int sensorNumber = bb.get() - 48;
                bb.position(pos);
                if (sensors.get(sensorNumber) == null) {
                  sensors.add(sensorNumber, new Sensor());
                }
                sensors.get(sensorNumber).load(bb, time);
                par.prta(Util.clear(tmpsb).append(network).append(station).append(" ").
                        append(Util.toHex(unit)).append("/").append(sensorNumber).
                        append(" PARSE AD: ").append(sensors.get(sensorNumber)));
              }
              break;
            case "XC":
              if (xc == null) {
                xc = new XC();
              }
              xc.load(bb);
              par.prta(Util.clear(tmpsb).append(network).append(station).append(" ").append(Util.toHex(unit)).
                      append(" PARSE XC: ").append(xc.toStringBuilder(null)));
              // If the clock is locked, records its reading
              if (xc.getLockStatus().equals("L")) {
                try {
                  Util.clear(tmpsb);
                  tmpsb.append("status^gps^latitude=").append(Util.df25(xc.getLatitude())).
                          append(";longitude=").append(Util.df25(xc.getLongitude())).
                          append(";nsat=").append(xc.getNsats()).
                          append(";height=").append(Util.df21(xc.getElevation())).
                          append(";station=").append(network).append(station).
                          append(";time=").append(Util.ascdatetime(xc.getTimeInMillis())).append("\n");
                  if (ReftekClient.getStatusSender() != null) {
                    ReftekClient.getStatusSender().queueDBMsg(tmpsb);
                  }
                } catch (Exception e) {
                  e.printStackTrace(par.getPrintStream());
                }
              }
              break;
            case "US":
              if (us == null) {
                us = new US();
              }
              us.load(bb);
              par.prta(Util.clear(tmpsb).append(network).append(station).append(" ").append(Util.toHex(unit)).
                      append(" PARSE US: ").append(us.toStringBuilder(null)));
              break;
            case "DK":
              if (dk == null) {
                dk = new DK();
              }
              dk.load(bb);
              par.prta(Util.clear(tmpsb).append(network).append(station).append(" ").append(Util.toHex(unit)).
                      append(" PARSE DK: ").append(dk.toStringBuilder(null)));
              break;
            case "VS":
              ;   // This is a no operation
              break;
            case "AQ":
              if (aq == null) {
                aq = new AQ();
              }
              aq.load(bb);
              par.prta(Util.clear(tmpsb).append(network).append(station).append(" ").append(Util.toHex(unit)).
                      append(" PARSE AQ: ").append(aq.toStringBuilder(null)));
              break;
            default:
              par.prt(Util.clear(tmpsb).append(network).append(station).append(" Unknown status type =").append(statusType));
              break;
          }
          break;
        //par.prta("Got a PR command code!"+toString());
        case "PR":
          break;
        case "ID":
          break;
        default:
          Util.clear(tmpsb).append(" *** Unknown commandCode=").append(commandCode).append(" ");
          break;
      }
    }
    catch(RuntimeException e) {
      bb.position(commandPosition);
      byte [] scr = new byte[bb.limit()];
      int size=bb.limit();

      bb.get(scr, 0, bb.limit()-commandPosition);
      for(int i=0; i< scr.length; i++) {
        if(scr[i] == 0 ) size=i;
      }      
      String bbstr = new String(scr, 0, size);
      par.prta(Util.clear(tmpsb).append(network).append(station).append("** PARSE exception loadAttn(). continue").append(e.toString()).
              append(" bbstr=").append(bbstr));
      e.printStackTrace(par.getPrintStream());
    }
  }

  /**
   * Load a ET record from a byte buffer of the raw data.
   *
   * @param bb Byte buffer pointed to the beginning of the ET
   */
  public void loadET(ByteBuffer bb) {
    try {
      int pos = bb.position();
      bb.get();
      bb.get();
      int stream = bb.get();
      stream = (stream / 16) * 10 + (stream & 0xf);
      DataStreamInfo ds = null;
      if (dataStream.size() <= stream) {
        par.prt(Util.clear(tmpsb).append("*** increase datastream size in loadET size=").append(dataStream.size()).
                append(" stream=").append(stream).append(" ").append(toStringLong()));
        int top = Math.max(dataStream.size() + 10, stream + 1);
        for (int i = dataStream.size(); i < top; i++) {
          par.prta(Util.clear(tmpsb).append(i).append(" add null for loadET"));
          dataStream.add(i, null);
        }
      } else {
        ds = dataStream.get(stream);
      }
      if (ds == null) {
        par.prt(Util.clear(tmpsb).append("**** data stream from ET is not in configuration ET.stream=").
                append(stream).append(" ").append(toString()));
        // I do not know what the stream 8 is from some units, but its not worth an alarm
        if (stream < 8) {
          SendEvent.edgeSMEEvent("ReftekChnCnf", "Unconfigured ET stream=" + stream + " " + toStringLong(), this);
        }
        ds = new DataStreamInfo(1.);
        dataStream.set(stream, ds);
      }
      bb.position(pos);
      ds.loadET(bb);
    } catch (RuntimeException e) {
      par.prta("Got runtime in LoadET e=" + e);
      e.printStackTrace(par.getPrintStream());
      if (e.getMessage() != null) {
        if (e.getMessage().contains("OutOfMem")) {
          SendEvent.doOutOfMemory("In load ET out of memory", this);
          throw e;
        }
      }
    }
  }

  /**
   * Load a EH record from a byte buffer of the raw data.
   *
   * @param bb Byte buffer pointed to the beginning of the EH
   * @return The parsed DataStreamInfo from this EH
   */
  public DataStreamInfo loadEH(ByteBuffer bb) {
    try {
      int pos = bb.position();
      bb.get();
      bb.get();
      int stream = bb.get();
      stream = (stream / 16) * 10 + (stream & 0xf);
      DataStreamInfo ds = dataStream.get(stream);
      if (ds == null) {
        par.prt(Util.clear(tmpsb).append("**** data stream from EH is not in configuration EH.stream=").
                append(stream).append(" ").append(toStringBuilder(null)));
        SendEvent.edgeSMEEvent("ReftekChnCnf", "Unconfigured EH stream=" + stream + " " + toStringLong(), this);
        ds = new DataStreamInfo(1.);
        dataStream.set(stream, ds);
      }
      bb.position(pos);
      ds.loadEH(bb);
      //par.prt("LoadEH = "+ds.toString());
      return ds;
    } catch (RuntimeException e) {
      System.out.println("" + e);
      e.printStackTrace();
      if (e.getMessage() != null) {
        if (e.getMessage().contains("OutOfMem")) {
          SendEvent.doOutOfMemory("In load ET out of memory", this);
          throw e;
        }
      }
    }
    return null;
  }

  /**
   * Load a SC record from a byte buffer of the raw data.
   *
   * @param bb Byte buffer pointed to the beginning of the SC
   */
  public void loadSC(ByteBuffer bb) {
    sc = new StationChannel();
    sc.load(bb);
  }

  /**
   * Load a OM record from a byte buffer of the raw data.
   *
   * @param bb Byte buffer pointed to the beginning of the OM
   */
  public void loadOM(ByteBuffer bb) {
    om = new OperatingMode();
    om.load(bb);
  }

  /**
   * Load a DS record from a byte buffer of the raw data.
   *
   * @param bb Byte buffer pointed to the beginning of the DS
   */
  public void loadDS(ByteBuffer bb) {
    try {
      boolean isNew = false;
      int pos = bb.position();
      byte[] c = new byte[2];
      bb.get(c, 0, 2);
      int stream = Integer.parseInt(new String(c, 0, 2).trim()) - 1;// these use external stream number apparently
      DataStreamInfo ds = dataStream.get(stream);
      if (ds == null) {
        par.prt(Util.clear(tmpsb).append("**** data stream from DS is not in configuration DS.stream=").
                append(stream).append(" ").append(toStringBuilder(null)));
        SendEvent.edgeSMEEvent("ReftekChnCnf", "Unconfigured DS stream=" + stream + " " + toStringLong(), this);
        ds = new DataStreamInfo(1.);
        dataStream.set(stream, ds);
        isNew = true;
      }
      bb.position(pos);
      ds.load(bb);
      if (isNew) {
        par.prt(Util.clear(tmpsb).append("***** created ds=").append(ds.toStringBuilder(null)));
      }
    } catch (NumberFormatException e) {
      e.printStackTrace();
      if (e.getMessage() != null) {
        if (e.getMessage().contains("OutOfMem")) {
          SendEvent.doOutOfMemory("In load ET out of memory", this);
          throw e;
        }
      }
    }
  }
  // Variable related to getting the time quality
  String lastLockTime;
  GregorianCalendar loadTime = new GregorianCalendar();
  long lastLock = -1;
  int[] ymd = new int[3];

  /**
   * The time quality is a SEED value 0-100 - return it based on the last lock
   * time. The time quality is 100 - (current time - lastLockTime) in minutes(was seconds until 2017/11),
   * if there is not last locked time, the return is constantly 90.
   *
   * @return Time quality based on last lock
   */
  public int getTimeQuality() {
    if (lastLockTime != null) {
      String[] parts = lastLockTime.split(":");
      lastLockTime = null;
      if (parts.length >= 4) {
        int doy = Integer.parseInt(parts[0]);
        int hr = Integer.parseInt(parts[1]);
        int min = Integer.parseInt(parts[2]);
        int sec = Integer.parseInt(parts[3]);
        loadTime.setTimeInMillis(System.currentTimeMillis());
        int year = loadTime.get(Calendar.YEAR);
        SeedUtil.ymd_from_doy(year, doy, ymd);
        loadTime.set(year, ymd[1] - 1, ymd[2], hr, min, sec);
        lastLock = loadTime.getTimeInMillis();
        //DCK changes from seconds to minutes 2017/11/09 if lost lock
        return Math.max(0, 100 - (int) ((System.currentTimeMillis() - lastLock) / 60000));
      }
    } else if (lastLock >= 0) {
      return Math.max(0, 100 - (int) ((System.currentTimeMillis() - lastLock) / 60000));
    }
    return 90;
  }

  /**
   * Load a DS record from a byte buffer of the raw data.
   *
   * @param soh String with the state of health from the Reftek 130.
   * @param par A place to log any problems/status
   */
  public void loadSH(String soh, EdgeThread par) {
    String line = "";
    int dummy;
    if (soh == null) {
      return;
    }
    if (soh.length() <= 10) {
      return;
    }
    try {
      BufferedReader in = new BufferedReader(new StringReader(soh));
      par.prta("In LoadSH " + station + " "/*+soh*/);
      while ((line = in.readLine()) != null) {
        try {
          if (line.contains("CPU SOFTWARE")) {
            version = line.substring(line.indexOf("V")).trim();
          } else if (line.contains("EXTERNAL CLOCK ERROR") || line.contains("BATTERY BACKUP") || line.contains("RECENTER")
                  || line.contains("A/D TIMETAG ERR") || line.contains("MQCHK:") || line.contains("SYSTEM POWERUP")
                  || line.contains("ACQUISITION STARTED")) {
            par.prta(station + " * LOG MSG: " + line);
          } else if (line.contains("CLOCK POWER") || line.contains("CLOCK WAKE") || line.contains("CLOCK IS UNLOCK")
                  || line.contains("Serial Link") || line.contains("Ethernet") || line.contains("FPGA")
                  || line.contains("SERIAL LINK") || line.contains("ETHERNET")
                  || line.contains("Ref Tek 130") || line.contains("REF TEK 130")
                  || line.contains("BOARD") || line.contains("Channel:Offset") || line.contains("SC1 addr") || line.contains("SC1 ADDR")
                  || line.contains("BVTC") || line.contains("AUTO DUMP") || line.contains("CLOCK FAILED")
                  || line.trim().equals("") || line.contains("LINK PARAMETER") || line.contains("MAKER")
                  || line.contains("CLOCK SLEEP") || line.contains("PARAMETER PACK")
                  || line.contains("CLOCK SLEEP") || line.contains("PARAMETER PACK")
                  || line.contains("CPY_RAM2NET")
                  || line.contains("GPS FIRMWARE") || line.contains("DSP CLOCK")
                  || line.contains("DSP:") || line.contains("INTERNAL CLOCK TIME JERK")
                  || line.contains("ACQUISITION ENABLED") || line.contains("RTP STATUS:")
                  || line.contains("Network layer") || line.contains("Net layer") || line.contains("Stack Status")
                  || line.contains("NETWORK LAYER") || line.contains("NET LAYER") || line.contains("STACK STATUS")
                  || line.contains("RTP Status") || line.contains("RTP: Stop") || line.contains("RTP: Open")
                  || line.contains("RTP: STUCK") || line.contains("RTP APP") || line.contains("RTP App")
                  || line.contains("RTP: DISCOVERY") || line.contains("RTP APP") || line.contains("RTP App")
                  || line.contains("RTP1 LINK") || line.contains("INTERNAL CLOCK POSSIBLE")
                  || line.contains("RTP: STORAGE") || line.contains("RTP: FORCING") || line.contains("MC:")
                  || line.contains("DISK") || line.contains("CHANNEL:") || line.contains("WARN") || line.contains("SCK:")
                  || line.contains("CLK:") || line.contains("PARAMETERS IMPLE") || line.contains("POWERUP COMP") // This is a noop to supress warnings.
                  || line.contains("RTP Storage") || line.contains("RTP STORAGE") || line.contains("RTP: OPENED") 
                  || line.contains("RTP: STOPPED") || line.contains(" CPY_RAM2DSK:") || line.contains("INTERNAL CLOCK")) {
            par.prta("MsgNop: " + station + ":" +line);   // This is a noop to supress warnings.
          } else if (line.contains("GPS: POSITION")) {
            gpsPosition = line.substring(line.indexOf("ION:") + 4).trim();
          } else if (line.contains("CPU SOFTWARE")) {
            version = line.substring(line.indexOf("V")).trim();
          } else if (line.contains("CLOCK IS LOCKED")) {
            lastLockTime = line.substring(0, 12);
            par.prta(Util.clear(tmpsb).append("Last lock = ").append(lastLockTime).append(Util.toHex((short) unit)).append(" ").append(station));
          } else if (line.contains("BATTERY VOLT")) {
            String[] parts = line.split("=");
            parts[1] = parts[1].trim();
            batteryVoltage = Double.parseDouble(parts[1].trim().substring(0, parts[1].indexOf("V")));
            parts[2] = parts[2].trim();
            temperature = Double.parseDouble(parts[2].substring(0, parts[2].indexOf("C")));
            parts[3] = parts[3].trim();
            backupVoltage = Double.parseDouble(parts[3].substring(0, parts[3].indexOf("V")));

          } else if (line.contains("MEMORY USED=")) {
            String[] parts = line.split("=");
            memoryUsed = Integer.parseInt(parts[1].substring(0, 5));
            memoryAvailable = Integer.parseInt(parts[2].substring(0, 5));
            memoryTotal = Integer.parseInt(parts[3].substring(0, 5));
          } else if (line.contains("EXTERNAL CLOCK TYPE")) {
            clockType = line.substring(line.indexOf("TYPE:") + 5).trim();
          } else if (line.contains("INTERNAL CLOCK PHASE ERROR OF ")) {
            int start = line.indexOf("ERROR OF") + 8;
            int end = line.indexOf("USEC");
            clockPhaseError = Integer.parseInt(line.substring(start, end).trim());
          } else if (line.contains("IP:")) {
            ipadrs = parseIP(line, "IP:");
          } else if (line.contains("Host:") || line.contains("HOST:")) {
            hosts = parseIP(line, "Host:");
          } else if (line.contains("Mask:") || line.contains("MASK:")) {
            netmasks = parseIP(line, "Mask:");
          } else if (line.contains("Gate:") || line.contains("GATE:")) {
            gateways = parseIP(line, "Gate:");
          } else if (line.contains("SENSOR 01")) {
            sensor1 = line.substring(line.indexOf("SENSOR 01"));
          } else if (line.contains("SENSOR 02")) {
            sensor2 = line.substring(line.indexOf("SENSOR 02"));
          } else {
            unknown.append(line).append("\n");
            if(unknown.length() > 100000) unknown.delete(0, 50000); // Limit the size of this 
            par.prta(Util.clear(tmpsb).append(station).append(" ** Unknown status line=").append(line).append("|"));
          }
        } catch (NumberFormatException e) {
          par.prt(e.toString());
          e.printStackTrace(par.getPrintStream());
          if (e.getMessage() != null) {
            if (e.getMessage().contains("OutOfMem")) {
              SendEvent.doOutOfMemory("In load ET out of memory", this);
              throw e;
            }
          }
        } catch (RuntimeException e) {
          par.prt("Run time - continue " + e.toString());
          e.printStackTrace(par.getPrintStream());

        }
      }
    } catch (IOException e) {
      par.prta(Util.clear(tmpsb).append("IO error reading SOH line=").append(line));
      e.printStackTrace();
    } catch (NumberFormatException e) {
      par.prt(e.toString());
      e.printStackTrace(par.getPrintStream());
      if (e.getMessage() != null) {
        if (e.getMessage().contains("OutOfMem")) {
          SendEvent.doOutOfMemory("In load ET out of memory", this);
          throw e;
        }
      }
    }
  }

  private String[] parseIP(String line, String key) {
    line = line.replaceAll("\\. ", ".");   // They have spaces in their IP addresses and masks!
    line = line.replaceAll("\\. ", ".");
    String[] parts = line.substring(line.indexOf(key) + key.length()).split("\\s");
    int count = 0;
    for (String part : parts) {
      if (!part.trim().equals("")) {
        count++;
      }
    }
    String[] val = new String[count];
    int j = 0;
    for (String part : parts) {
      if (!part.trim().equals("")) {
        val[j++] = part;
      }
    }
    return val;
  }

  @Override
  public void run() {
    long lastSent = 0;
    while (!terminate) {
      if (xc != null && dk != null && aq != null && us != null) {
        if (xc.getTimeInMillis() > lastSent && dk.getTimeInMillis() > lastSent
                && aq.getTimeInMillis() > lastSent && us.getTimeInMillis() > lastSent) {
          StringBuilder s = doDASStatusString();
          //par.prt("DASSTATUS: "+s);
          doSNWSenderString();
          lastSent = xc.getTimeInMillis() + 60000;
        }
      }
      try {
        sleep(1000);
      } catch (InterruptedException expected) {
      }
    }
    par.prta(station + " " + Util.toHex(unit) + " has terminated");
    par = null;
  }

  /**
   * Return a DS record based on its index
   *
   * @param i the ith data stream is returned
   * @return the ith DataStreamInfo record
   */
  public DataStreamInfo getDataStreamInfo(int i) {
    try {
      return dataStream.get(i);
    } catch (RuntimeException e) {
      par.prt(Util.clear(tmpsb).append(" *** Runtime stream OOR get DataStream stream=").append(i).append(" ").append(toStringLong()));
      e.printStackTrace(par.getPrintStream());
      return null;
    }
  }

  public int getUnit() {
    return unit;
  }

  public String getIP() {
    return ipadr;
  }

  public String getNetwork() {
    return network;
  }

  public String getStation() {
    return station;
  }

  public StationChannel getStationChannel() {
    return sc;
  }

  public OperatingMode getOperatingMode() {
    return om;
  }

  /**
   * Convert a short to a BCD integer
   *
   * @param i2 The short
   * @return BCD integer
   */
  public static short BCDtoInt(short i2) {
    return (short) (BCDtoInt((int) i2) & 0xffff);
  }

  /**
   * Convert a shortint to a BCD string.
   *
   * @param i4 The int
   * @return BCD string;
   */
  public static int BCDtoInt(int i4) {
    int mult = 1;
    int ans = 0;
    for (int i = 0; i < 8; i++) {
      ans += (i4 & 0xF) * mult;
      mult *= 10;
      i4 = i4 >> 4;
    }
    return ans;
  }

  /**
   * Convert a short to a BCD string.
   *
   * @param i2 The short
   * @return BCD string;
   */
  public static String BCDtoString(short i2) {
    return BCDtoString((int) i2).substring(4);
  }

  /**
   * Convert a short to a BCD string.
   *
   * @param i4 The short
   * @return BCD string;
   */
  public static String BCDtoString(int i4) {
    String s = "";
    for (int i = 0; i < 8; i++) {
      s = (char) ((i4 & 0xf) + 32) + s;
    }
    return s;
  }
  // StringBuilder to use
  private static final StringBuilder timesb = new StringBuilder(12);

  public static StringBuilder timeToString(byte[] time, StringBuilder sb) {
    if (sb == null) {
      sb = timesb;
    }
    Util.clear(sb);
    int t;
    for (int i = 0; i < 6; i++) {
      t = time[i];
      t &= 0xff;
      sb.append((char) (((t >> 4) & 0xf) + '0'));
      sb.append((char) ((t & 0xf) + '0'));
    }
    if (sb.indexOf(":") >= 0) {
      Util.prta("Got illegal character in time string " + sb + " " + Util.toHex(time[0]) + " "
              + Util.toHex(time[1]) + " " + Util.toHex(time[2]) + " " + Util.toHex(time[3]) + " "
              + Util.toHex(time[4]) + " " + Util.toHex(time[5]));
    }
    return sb;
  }

}
