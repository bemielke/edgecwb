/*
 *  This software is in the public domain because it contains materials 
 *  that originally came from the United States Geological Survey, 
 *  an agency of the United States Department of Interior. For more 
 *  information, see the official USGS copyright policy at 
 *  http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.waveserver;

import gov.usgs.adslserverdb.InstrumentType;
import gov.usgs.alarm.SendEvent;
import gov.usgs.anss.edge.StaSrvEdge;
import gov.usgs.anss.util.Util;
import gov.usgs.edge.config.Channel;
import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;

/**
 * This class is for the CWBWS to handle information about a channel.
 *
 *
 * @author U.S. Geological Survey  <ketchum at usgs.gov>
 */
public final class ChannelList {

  private long created = Long.MAX_VALUE;    // daysbackms time of seedname
  private long lastData = 0;                // end time of seedname
  private long mdsflag;                   // MDS flag
  private String seedname;                 //NNSSSSSCCCLL form
  private final StringBuilder seednamesb = new StringBuilder(12);
  private final StringBuilder SCNLSpaced = new StringBuilder(40);              // SSSS CCC NN LL form
  private final StringBuilder instrument = new StringBuilder(10);              // An instrument name if known
  private double sensitivity;
  private final double intercept = Double.NaN;
  private double mdsrate;
  //private String wwsChannelString;
  private final StringBuilder wwsGetChannelString = new StringBuilder(20);
  //private String wwsGetChannelMetadataString;
  private final StringBuilder unit = new StringBuilder(4);
  private double latitude, longitude, elevation;
  private final StringBuilder longname = new StringBuilder(5);
  // The wwwChannelString has a beggining which only changes with metadata, and end which is the same
  // and a middle which is the time qscspan.  The strings for the MDS portion are made up when MDS changes
  // come in.
  private final StringBuilder wwsChannelStart = new StringBuilder(40);
  private final StringBuilder wwsChannelEnd = new StringBuilder(40);
  private final StringBuilder wwsMetadataEnd = new StringBuilder(20);
  // MDS related variables
  private final StringBuilder wwsString = new StringBuilder(100);

  public StringBuilder getChannelString() {
    return wwsGetChannelString;
  }

  public StringBuilder getMetadataEnd() {
    return wwsMetadataEnd;
  }
  //public String getChannelMetadataString() {return wwsGetChannelString.toString()+wwsMetadataEnd.toString();}

  public long getCreated() {
    return created;
  }

  public long getLastData() {
    return lastData;
  }

  public long getMDSFlag() {
    return mdsflag;
  }

  public double getMDSRate() {
    return mdsrate;
  }

  public String getChannel() {
    return seedname;
  }

  public StringBuilder getChannelSB() {
    return seednamesb;
  }

  public double getLatitude() {
    return latitude;
  }

  public double getLongitude() {
    return longitude;
  }

  public double getElevation() {
    return elevation;
  }

  public StringBuilder getInstrument() {
    return instrument;
  }

  protected void updateMetaTable() throws IOException {

  }

  /**
   * update the particulars for locatec etc, from the metadata table.
   *
   * @throws IOException
   */
  protected void updateMDS() throws IOException {
    //prta("updateMDS : mdsip="+mdsIP+" mdsport="+mdsPort+" "+toString());
    if (CWBWaveServer.thisThread.mdsIP == null) {
      return;
    }
    if (CWBWaveServer.thisThread.mdsIP.equals("") || CWBWaveServer.thisThread.mdsPort == 0) {
      return;
    }
    boolean done = false;
    //String longname="";
    while (!done) {
      if (CWBWaveServer.stasrv == null) {
        try {
          CWBWaveServer.thisThread.prta("create new MDS socket to " + CWBWaveServer.thisThread.mdsIP + "/" + CWBWaveServer.thisThread.mdsPort);
          CWBWaveServer.stasrv = new StaSrvEdge(CWBWaveServer.thisThread.mdsIP, CWBWaveServer.thisThread.mdsPort, CWBWaveServer.thisThread.mdsto, CWBWaveServer.thisThread);
          CWBWaveServer.thisThread.prta("MDS socket is created sucessfully stasrv=" + CWBWaveServer.stasrv);
        } catch (IOException e) {
          CWBWaveServer.thisThread.prta("updateMDS: cannot open MDS/StaSrv connection e=" + e);
          SendEvent.edgeSMEEvent("CWBWvSrNoMDS", "Cannot open MDS at " + CWBWaveServer.thisThread.mdsIP + "/" + CWBWaveServer.thisThread.mdsPort, CWBWaveServer.thisThread);
          throw e;
        }
      }

      try {
        String[] lines = CWBWaveServer.stasrv.getSACResponse(seedname, null, null).split("\n");
        for (String line : lines) {
          //Util.prt(line);
          if (line.contains("<EOR>")) {
            break;    // end of response
          } else if (line.contains("LAT-SEED")) {
            latitude = Double.parseDouble(line.substring(14).trim());
          } else if (line.contains("LONG-SEED")) {
            longitude = Double.parseDouble(line.substring(14).trim());
          } else if (line.contains("ELEV-SEED")) {
            elevation = Double.parseDouble(line.substring(14).trim());
          } else if (line.contains("SENS-SEED")) {
            sensitivity = Double.parseDouble(line.substring(14).trim());
          } else if (line.contains("INSTRMNTUNIT")) {
            Util.clear(unit).append(line.substring(14).trim().toUpperCase());
            if (Util.stringBuilderEqual(unit, "V")) {
              Util.clear(unit).append("nm/s");
            } else if (Util.stringBuilderEqual(unit, "A")) {
              Util.clear(unit).append("nm/s/s");
            } else if (Util.stringBuilderEqual(unit, "D")) {
              Util.clear(unit).append("nm");
            }
          } else if (line.contains("RATE")) {
            mdsrate = Double.parseDouble(line.substring(14).trim());
          } else if (line.contains("INSTRMNTTYP")) {
            Util.clear(instrument).append(InstrumentType.decodeSeismometerString(seedname, line.substring(14).trim()));
          } else if (line.contains("DESCRIPTION")) {
            Util.clear(longname).append(line.substring(13).trim());
          }
        }
        doMDS();
        done = true;
      } catch (IOException e) {
        CWBWaveServer.thisThread.prta("Could not open MDS socket or IOError to " + CWBWaveServer.thisThread.mdsIP + "/" + CWBWaveServer.thisThread.mdsPort + " e=" + e);
        e.printStackTrace(CWBWaveServer.thisThread.getPrintStream());
        SendEvent.edgeSMEEvent("CWBWSNoMDS", "Cannot connect to MDS at " + CWBWaveServer.thisThread.mdsIP + "/" + CWBWaveServer.thisThread.mdsPort, this);
        try {
          CWBWaveServer.thisThread.sleep(5000);
        } catch (InterruptedException expected) {
        }

      }
    }   // while(!done)
  }
  StringBuilder sbgroup = new StringBuilder(100);

  private void doMDS() {
    //prta("doMDS: "+seedname+" "+latitude+" "+longitude+" "+elevation+" "+longname);
    CWBWaveServer.updateChannel(seedname, latitude, longitude, elevation, longname);
    ArrayList<String> groups = null;
    Util.clear(wwsChannelStart);
    wwsChannelStart.append("channel=").append(seedname.substring(2, 7).trim()).append(" ").
            append(seedname.substring(7, 10)).append(" ").append(seedname.substring(0, 2).trim()).
            append(seedname.trim().length() == 10 ? "" : " " + seedname.substring(10).trim()).
            append(",instrument=").append(instrument);
    Util.clear(wwsChannelEnd);
    wwsChannelEnd.append(",alias=,unit=").append(unit).append(",linearA=").
            append(sensitivity > 0. ? Util.df26(1. / sensitivity * 1.e9) : "NaN").append(",linearB=").
            append(intercept).append(",groups=");

    //groups=Networks!^Shishaldin!\cNetworks!^Shishaldin!\c, or groups=Networks!^Akutan\c,
    if (CWBWaveServer.thisThread.subnetConfig == null) {
      wwsChannelEnd.append("Networks^").append(seedname.substring(0, 2)).append("\\c,");
    } else {
      groups = CWBWaveServer.thisThread.subnetConfig.getGroups(seedname);
      if (groups == null) {
        wwsChannelEnd.append(",");
      } else if (groups.isEmpty()) {
        wwsChannelEnd.append(",");
      } else {
        for (String group : groups) {
          wwsChannelEnd.append("Networks!^").append(group).append("\\c,");
        }
      }
    }
    wwsChannelEnd.append("\n");
    // Networks!^Shishaldin!|Networks!^Shishaldin! or Networks!^Shishaldin!
    Util.clear(sbgroup);
    if (CWBWaveServer.thisThread.subnetConfig == null) {
      sbgroup.append("Networks^").append(seedname.substring(0, 2));
    } else {
      if (groups == null) {
        sbgroup.append("~");
      } else {
        for (int i = 0; i < groups.size(); i++) {
          sbgroup.append("Network!^").append(groups.get(i));
          if (i < groups.size() - 1) {
            sbgroup.append("|");
          }
        }
      }
    }
    int l = sbgroup.length();
    Util.stringBuilderReplaceAll(sbgroup, "\n", "");
    if(sbgroup.length() != l) 
      CWBWaveServer.thisThread.prta("ChannelList: ** newlines removed from group! l="+l+" "+sbgroup.length());
    Util.clear(wwsMetadataEnd).append(String.format("%s:%s:%s:%f:%f:%s:",
            ":UTC", "", unit, (sensitivity > 0. ? 1. / sensitivity * 1.e9 : Double.NaN), 
            Double.NaN, sbgroup.toString()));
    Util.stringBuilderReplaceAll(wwsMetadataEnd, "\n", "");
    if (seedname.contains("AKRB")) {
      CWBWaveServer.thisThread.prta("ARKB: seedname=" + seedname + "|");
    }
    Util.clear(SCNLSpaced).append(seedname.substring(2, 7).trim()).
            append(CWBWaveServer.thisThread.winstonChannelDelimiter).
            append(seedname.substring(7, 10)).append(CWBWaveServer.thisThread.winstonChannelDelimiter).
            append(seedname.substring(0, 2).trim()).
            append(seedname.substring(10).trim().equals("") ? "" : CWBWaveServer.thisThread.winstonChannelDelimiter + seedname.substring(10));
    if (seedname.contains("AKRB")) {
      CWBWaveServer.thisThread.prta("ARKB: SCNLSpaced=" + SCNLSpaced);
    }

  }

  /*public void updateMetadata(String instrument, double sensitivity, String unit) {
    this.instrument=instrument;
    this.sensitivity=sensitivity;
    this.unit=unit;
  }*/
  //public String getWWSChannelString() {return wwsChannelString;}
  public StringBuilder getWWSChannelString(double end) {
    Util.clear(wwsString);
    wwsString.append(wwsChannelStart).
            append(",startTime=").append(Util.df23((created - CWBWaveServer.year2000MS) / 1000.)).
            // End comes from menuThread which might use lastData or SSR from UdpChannel
            append(",endTime=").append(Util.df23(end - CWBWaveServer.year2000sec)).
            append(wwsChannelEnd);
    Util.clear(wwsGetChannelString).append(String.format("%d:%s:%f:%f:%f:%f", -1, SCNLSpaced,
            (created - CWBWaveServer.year2000MS) / 1000., (end - CWBWaveServer.year2000sec), longitude, latitude));
    if (SCNLSpaced.indexOf("AKRB") >= 0) {
      CWBWaveServer.thisThread.prt("AKRB: space=" + SCNLSpaced + " wwsget=" + wwsGetChannelString);
    }
    return wwsString;
  }

  @Override
  public String toString() {
    return seedname + " " + mdsflag + " " + Util.ascdate(created) + " " + Util.asctime2(created)
            + " " + Util.ascdate(lastData) + " " + Util.asctime2(lastData);
  }
  public StringBuilder tmpsb = new StringBuilder();

  public StringBuilder toStringBuilder(StringBuilder sb) {
    StringBuilder tmp = sb;
    if (tmp == null) {
      tmp = Util.clear(tmpsb);
    }
    tmp.append(seedname).append(" ").append(mdsflag).append(" ").append(Util.ascdate(created)).
            append(" ").append(Util.asctime2(created)).append(" ").append(Util.ascdate(lastData)).
            append(" ").append(Util.asctime2(lastData));
    return tmp;
  }

  public ChannelList(ResultSet rs, boolean isHoldings) throws SQLException {
    update(rs, isHoldings);
    doMDS();
  }

  public ChannelList(String seed, long start, long last) {
    seedname = seed;
    Util.clear(seednamesb).append(seedname);
    Util.stringBuilderRightPad(seednamesb, 12);
    lastData = last;
    created = start;
    doMDS();
  }

  public ChannelList(StringBuilder seed, long start, long last) {
    seedname = seed.toString().trim();
    Util.clear(seednamesb).append(seedname);
    Util.stringBuilderRightPad(seednamesb, 12);
    lastData = last;
    created = start;
    doMDS();
  }

  protected final void update(ResultSet rs, boolean isHoldings) throws SQLException {
    if (isHoldings) {
      seedname = rs.getString("seedname");
      Util.clear(seednamesb).append(seedname);
      Util.stringBuilderRightPad(seednamesb, 12);
      if (rs.getTimestamp(3).getTime() > lastData) {
        lastData = rs.getTimestamp(3).getTime();
      }
      mdsflag = 0;
      if (rs.getTimestamp(2).getTime() < created) {
        created = rs.getTimestamp(2).getTime();
      }
      if (seedname.substring(0, 2).trim().length() == 0) {
        CWBWaveServer.thisThread.prta("ChannelList update from isHoldings=true bad network " + seedname);
      }
    } else {
      seedname = rs.getString("channel");
      Util.clear(seednamesb).append(seedname);
      Util.stringBuilderRightPad(seednamesb, 12);
      if (seedname.substring(0, 2).trim().length() == 0) {
        CWBWaveServer.thisThread.prta("ChannelList update from isHoldings=false bad network " + seedname);
      }
      if (rs.getTimestamp("lastdata").getTime() > lastData) {
        lastData = rs.getTimestamp("lastdata").getTime();
      }
      mdsflag = rs.getLong("mdsflags");
      if (rs.getTimestamp("created").getTime() < created) {
        created = rs.getTimestamp("created").getTime();
      }
    }
    Util.clear(SCNLSpaced).append(seedname.substring(2, 7).trim()).append(CWBWaveServer.thisThread.winstonChannelDelimiter).
            append(seedname.substring(7, 10)).append(CWBWaveServer.thisThread.winstonChannelDelimiter).
            append(seedname.substring(0, 2).trim()).
            append(seedname.substring(10).trim().equals("") ? "" : CWBWaveServer.thisThread.winstonChannelDelimiter + seedname.substring(10));

  }

  protected final void update(Channel chan) {

    seedname = chan.getChannel();
    Util.clear(seednamesb).append(seedname);
    Util.stringBuilderRightPad(seednamesb, 12);
    if (seedname.substring(0, 2).trim().length() == 0) {
      CWBWaveServer.thisThread.prta("ChannelList update from isHoldings=false bad network " + seedname);
    }
    if (chan.getLastData().getTime() > lastData) {
      lastData = chan.getLastData().getTime();
    }
    mdsflag = chan.getMDSFlags();
    if (chan.getCreated().getTime() < created) {
      created = chan.getCreated().getTime();
    }
    Util.clear(SCNLSpaced).append(seedname.substring(2, 7).trim()).append(CWBWaveServer.thisThread.winstonChannelDelimiter).
            append(seedname.substring(7, 10)).append(CWBWaveServer.thisThread.winstonChannelDelimiter).
            append(seedname.substring(0, 2).trim()).
            append(seedname.substring(10).trim().equals("") ? "" : CWBWaveServer.thisThread.winstonChannelDelimiter + seedname.substring(10));

  }
}
