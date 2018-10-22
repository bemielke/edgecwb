/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.edge.config;

import gov.usgs.anss.cwbqueryclient.EdgeQueryClient;
import gov.usgs.anss.db.DBConnectionThread;
import gov.usgs.anss.util.Util;
import gov.usgs.anss.seed.MiniSeed;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.GregorianCalendar;
import java.net.Socket;
//import java.net.UnknownHostException;
import java.io.IOException;
import java.util.Collections;

/**
 * SubspaceArea.java If the name of the class does not match the name of the field in the class you
 * may need to modify the rs.getSTring in the constructor.
 *
 * This SubspaceArea template for creating a database database object. It is not really needed by
 * the SubspaceAreaPanel which uses the DBObject system for changing a record in the database file.
 * However, you have to have this shell to create objects containing a database record for passing
 * around and manipulating. There are two constructors normally given:
 * <br>
 * 1) passed a result set positioned to the database record to decode
 * <br> 2) Passed all of the data arguments needed to build the same set of data.
 * <br>
 * The SubspaceArea should be replaced with capitalized version (class name) for the file.
 * subspaceArea should be replaced with the lower case version (for variables).
 *
 * Both the constructors use the "makeSubspaceArea(list of data args) to set the local variables to
 * the value. The result set just uses the rs.get???("fieldname") to get the data from the database
 * where the other passes the arguments from the caller.
 *
 * <br> Notes on Enums :
 * <br> * data class should have code like :
 * <br> import java.util.ArrayList; /// Import for Enum manipulation
 * <br> .
 * <br> static ArrayList fieldEnum; // This list will have Strings corresponding to enum
 * <br> .
 * <br> .
 * <br> // To convert an enum we need to create the fieldEnum ArrayList. Do only once(static)
 * <br> if(fieldEnum == null) fieldEnum =
 * FUtil.getEnum(JDBConnection.getConnection("DATABASE"),"table","fieldName");
 * <br>
 * <br> // Get the int corresponding to an Enum String for storage in the data base and
 * <br> // the index into the JComboBox representing this Enum
 * <br> localVariable = FUtil.enumStringToInt(fieldEnum, enumString);
 * <br> Created on July 8, 2002, 1:23 PM
 *
 * @author D.C. Ketchum
 */
public final class SubspaceArea {    //implements Comparator
  // static ArrayList enumName;   // this need to be populated in makeSubspaceArea()

  public static final String[] STATUS_ENUM = {"Initial Design", "Operational", "Review", "Disabled", "Research/Offline"};

  public static ArrayList<SubspaceArea> subspaceAreas;             // ArrayList containing objects of this SubspaceArea Type
  public static final GregorianCalendar date2099 = new GregorianCalendar(2099, 11, 31, 23, 59, 59);
  /**
   * Creates a new instance of SubspaceArea
   */
  private int ID;                   // This is the database ID (should alwas be named "ID"
  private String subspaceArea;             // This is the main key which should have same name
  private int status;
  // as the table which it is a key to.  That is
  // Table "category" should have key "category"

  // USER: All fields of file go here
  //  double longitude
  private double latitude, longitude, radius;
  private int independent;
  private long startTime, endTime;
  private long lastPreprocess, updated;
  private int eventTypeID;
  private String opsargs, researchargs;
  //Timestamp startTime = new Timestamp(10000L); 
          //Timestamp endTime = new Timestamp(10000L);
          // Put in correct detail constructor here.  Use makeSubspaceArea() by filling in all
          // the rs.get???("fieldName") for all of the arguments
  /**
   * Create a instance of this table row from a positioned result set
   *
   * @param rs The ResultSet already SELECTED
   * @exception SQLException SQLException If the ResultSet is Improper, network is down, or
   * database is down
   */

  public SubspaceArea(ResultSet rs) throws SQLException {  //USER: add all data field here
    reload(rs);
  }

  /**
   * reload the object data from a given result set
   *
   * @param rs The ResultSet to load the object from
   * @throws SQLException If one is thrown
   */
  public final void reload(ResultSet rs) throws SQLException {
    makeSubspaceArea(rs.getInt("ID"), rs.getString("subspaceArea"),
            rs.getDouble("latitude"), rs.getDouble("longitude"), rs.getDouble("radius"),
            rs.getInt("independent"), rs.getTimestamp("startTime").getTime(), rs.getTimestamp("endtime").getTime(),
            rs.getInt("status"), rs.getTimestamp("lastpreprocess").getTime(),
            rs.getTimestamp("updated").getTime(), rs.getString("opsargs"), rs.getString("researchargs"),rs.getInt("eventtypeid")
    // ,rs.getDouble(longitude)
    );
  }

  // Detail Constructor, match set up with makeSubspaceArea(), this argument list should be
  // the same as for the result set builder as both use makeSubspaceArea()
  /**
   * create a row record from individual variables
   *
   * @param inID The ID in the database, no
   * @param lat latitude in degrees
   * @param lng longitude in degrees
   * @param rad radius in km
   * @param indep Runt as independent or svd
   * @param start
   * @param end
   * @param loc The key value for the database (same a name of database table)
   * @param stat status value
   * @param lastPre Time of last preprocess run
   * @param upd Updated time from database
   * @param ops Operation args
   * @param research Research args
   * @param eventType is the ID number of the enum in the SSDEventType table
   */
  public SubspaceArea(int inID, String loc //USER: add fields, double lon
          ,
           double lat, double lng, double rad, int indep, Timestamp start, Timestamp end,
          int stat, long lastPre, long upd, String ops, String research, int eventType
  ) {
    makeSubspaceArea(inID, loc, lat, lng, rad, indep, start.getTime(), end.getTime(), stat,
            lastPre, upd, ops, research ,eventType//, lon
    );
  }

  // This routine transfers to the local variables of this object all of the data variables
  // associated with this file.
  /**
   * internally set all of the field in our data to the passed data
   *
   * @param inID The row ID in the database
   * @param loc The key (same as table name)
   *
   */
  private void makeSubspaceArea(int inID, String loc //USER: add fields, double lon
          ,
           double lat, double lng, double rad, int indep, long start, long end, int stat,
          long lastPre, long upd, String ops, String research, int eventtype
  ) {
    ID = inID;
    subspaceArea = loc;     // longitude = lon
    //USER:  Put asssignments to all fields from arguments here

    // ENUM example:
    //if(qtypeEnum == null) qtypeEnum=FUtil.getEnum(JDBConnection.getConnection("DATABASE"), "nsnstation","quanterraType");
    //qtype = FUtil.enumStringToInt(qtypeEnum, st); 
    // Boolean example where ENUM "True", "False
    // cont40 = FUtil.isTrue(c40);          // c40 is a string with "true" or false
    latitude = lat;
    longitude = lng;
    radius = rad;
    independent = indep;
    startTime = start;
    endTime = end;
    status = stat;
    lastPreprocess = lastPre;
    updated = upd;
    opsargs = ops;
    researchargs = research;
    eventTypeID = eventtype;
  }

  /**
   * return the key name as the string
   *
   * @return the key name
   */
  public String toString2() {
    return subspaceArea;
  }

  /**
   * return the key name as the string
   *
   * @return the key name
   */
  @Override
  public String toString() {
    return subspaceArea;
  }
  // getter

  // standard getters
  /**
   * get the database ID for the row
   *
   * @return The database ID for the row
   */
  public int getID() {
    return ID;
  }

  /**
   * get the key name for the row
   *
   * @return The key name string for the row
   */
  public String getSubspaceArea() {
    return subspaceArea;
  }

  public long getStartTime() {
    return startTime;
  }

  public long getEndTime() {
    return endTime;
  }

  public double getLatitude() {
    return latitude;
  }

  public double getLongitude() {
    return longitude;
  }

  public double getRadius() {
    return radius;
  }

  public long getLastPreprocess() {
    return lastPreprocess;
  }

  public long getUpdated() {
    return updated;
  }

  public int getStatusIndex() {
    return status;
  }

  public String getOpsArgs() {
    return opsargs;
  }

  public String getResearchArgs() {
    return researchargs;
  }
  
  public int getEventTypeID() {
    return eventTypeID;
  }
  
  public String getEventTypeValue() {
    SSDEventType ssdEt = SSDEventType.getSSDEventTypeWithID(eventTypeID);
    if (ssdEt != null) {
      return ssdEt.getSSDEventType();
    }
    return null;
  }

  public String getStatusString() {
    if (status >= 0 && status < STATUS_ENUM.length) {
      return STATUS_ENUM[status];
    }
    return "UNKNOWN";
  }

  //USER:  All field getters here
  //  public double getLongitude() { return longitude;}
  // This is needed if you need to implement Comparator
  /*  public class EquipCompare implements Comparator {
    public int compare(Object a, Object b) {
      return ((SubspaceArea) a).getSubspaceArea().compareTo(((SubspaceArea) b).getSubspaceArea());
    }
    public boolean equals(Object a, Object b) {
      if( ((SubspaceArea)a).getSubspaceArea().equals( ((SubspaceArea) b).getSubspaceArea())) return true;
      return false;
    }
//  }*/
  public static ArrayList<SubspaceArea> getSubspaceAreaArrayList() {
    makeSubspaceAreas();
    return subspaceAreas;
  }

  /**
   *
   * @param ID The ID of the SubspaceArea
   * @return
   */
  public static SubspaceArea getSubspaceAreaWithID(int ID) {
    if(subspaceAreas == null) {
      makeSubspaceAreas();
    }
    for (SubspaceArea instance : subspaceAreas) {
      if (instance.getID() == ID) {
        return instance;
      }
    }
    return null;
  }  // This routine should only need tweeking if key field is not same as table name

  public static void makeSubspaceAreas() {
    try {
      Statement stmt;
      if (DBConnectionThread.getConnection(DBConnectionThread.getDBSchema()) != null) {
        stmt = DBConnectionThread.getConnection(DBConnectionThread.getDBSchema()).createStatement();   // used for query
      } else {
        stmt = DBConnectionThread.getConnection("DBServer").createStatement();    // This is for call in the PickManager in QueryMom
      }
      makeSubspaceAreas(DBConnectionThread.getConnection(DBConnectionThread.getDBSchema()).createStatement());
    } catch (SQLException e) {
      Util.SQLErrorPrint(e, "makePickers() on table SQL failed");
    }
  }

  public static void makeSubspaceAreas(Statement stmt) {
    if (subspaceAreas != null) {
      return;
    }
    subspaceAreas = new ArrayList<SubspaceArea>(100);
    String s = "SELECT * FROM " + "edge.subspacearea ORDER BY subspacearea;"; //USER: if DBSchema is not right DB, explict here
    try (ResultSet rs = stmt.executeQuery(s)) {
      while (rs.next()) {
        SubspaceArea loc = new SubspaceArea(rs);
//        Util.prt("MakeSubspaceArea() i="+v.size()+" is "+loc.getSubspaceArea());
        subspaceAreas.add(loc);
      }
    } catch (SQLException e) {
      Util.SQLErrorPrint(e, "makeSubspaceAreas() on table SQL failed");
    }
  }

  /**
   * Given the area, subspacechannel, create an config file suitable for the SubspaceProcessor. If
   * called with a configuration object, it will also populate the SSD CWB if the timeseries is not
   * already there.
   *
   * @param stmt An MySQL statement to the Edge database.
   * @param area An SubspaceArea object for the area that contains this channel
   * @param sschan The SubspaceChannel for which the config is to be create
   * @param svd Use svd or independent method
   * @param configPath Path to put this configuration under
   * @param config This is the Subspace config record from the subspace table, if present, populate
   * the SSD CWB
   * @param cwbIP User provided CWBIP for research requests (i.e. not in the subspace configuration
   * table)
   * @param cwbPort User provide CWB port for research requests
   * @param sb The user supplied StringBuilder which will contain the configuration string when
   * done, user should clear before calling
   * @param state The StringBuilder to get the state file contents, NSCL, start and stop times.
   */
  public static void createPreprocessorConfigSB(Statement stmt, SubspaceArea area, SubspaceChannel sschan,
          boolean svd, String configPath, String cwbIP, int cwbPort, Subspace config, 
          StringBuilder sb, StringBuilder state) {
    ArrayList<SubspaceEvent> events;
    String chan = sschan.getSubspaceChannel();
    String loccode = "";
    boolean gotPhases = false;
    StringBuilder in = new StringBuilder(1000);
    try (ResultSet rsevent = stmt.executeQuery("SELECT * FROM edge.subspaceevent WHERE areaid="
            + area.getID() + " ORDER by subspaceevent")) {
      events = new ArrayList<>(100);
      while (rsevent.next()) {
        events.add(new SubspaceEvent(rsevent));
        in.append(rsevent.getInt("id")).append(",");
      }
    } catch (SQLException e) {
      e.printStackTrace();
    }
    in.deleteCharAt(in.length() - 1);
    String s = "SELECT eventid,phase,arrivaltime,disable FROM edge.subspaceeventchannel WHERE channelid = " + sschan.getID()
            + " AND eventid IN (" + in + ") ORDER BY reference DESC,eventid ASC";
    try (ResultSet rschan = stmt.executeQuery(s)) {
      while (rschan.next()) {
        // Find the evt for this channel record
        int eventID = rschan.getInt("eventid");
        SubspaceEvent ssevent = SubspaceEvent.getSubspaceEventWithID(eventID);
        String channel = sschan.getNSCL1();
        String loc = chan.substring(10).trim();
        if (loc.equals("")) {
          loc = "..";
        }
        loccode = loc;
        if (ssevent != null) {
          sb.append((rschan.getInt("disable") > 0 ? "#" : "")).append(ssevent.getSubspaceEvent()).
                  append(" ").append(Util.ascdatetime2(ssevent.getOriginTime())).
                  append(" ").append(ssevent.getLatitude()).append(" ").append(ssevent.getLongitude()).append(" ").
                  append(ssevent.getDepth()).append(" ").append(ssevent.getMagnitude()).append(" ").
                  append(ssevent.getMagMethod()).append(" ").append(channel.substring(0, 2)).append(" ").
                  append(channel.substring(2, 7).trim()).append(" ").append(channel.substring(7, 10)).append(" ").
                  append(loc).append(" ").append(rschan.getString("phase")).append(" ").
                  append(Util.ascdatetime2(rschan.getTimestamp("arrivaltime").getTime()));

          if (!gotPhases) {
            sb.append(svd ? " svd" : " independent");
          }
          sb.append("\n");

          // Get the time series from the SSDCWB
          if (config != null) {
            try {
              checkForData(config, sschan.getNSCL1(), rschan.getTimestamp("arrivaltime").getTime(), sschan.getTemplateDuration());
              if (sschan.getNSCL2() != null) {
                checkForData(config, sschan.getNSCL2(), rschan.getTimestamp("arrivaltime").getTime(), sschan.getTemplateDuration());
              }
              if (sschan.getNSCL2() != null) {
                checkForData(config, sschan.getNSCL3(), rschan.getTimestamp("arrivaltime").getTime(), sschan.getTemplateDuration());
              }
            }
            catch(RuntimeException e) {
              Util.prta("Runtime exception in check data. continue e="+e);
              e.printStackTrace();
            }
          }
          gotPhases = true;
        }
      }   // For each channel in this area

    } catch (SQLException e) {
      e.printStackTrace();
    }

    if (gotPhases) {     // There has to be at least on pick for a configuration to be valid
      sb.append("\n").append("bandpass: ").append(sschan.getHPCorner()).append(" ").
              append(sschan.getLPCorner()).append(" ").append(sschan.getNpoles()).append("\n");
      if (sschan.getLatitude() != 0. && sschan.getLongitude() != 0) {
        sb.append("station: ").append(sschan.getLatitude()).append(" ").append(sschan.getLongitude()).
                append(" ").append(sschan.getElevation()).append("\n");
      }

      state.append("NSCL=").append(sschan.getNSCL1().replaceAll(" ","_"));
      if(sschan.getNSCL2() != null) {
        if(!sschan.getNSCL2().equals("")) {
          state.append(",").append(sschan.getNSCL2().replaceAll(" ", "_"));
        }
      }
      if(sschan.getNSCL3() != null) {
        if(!sschan.getNSCL3().equals("")) {
          state.append(",").append(sschan.getNSCL3().replaceAll(" ", "_"));
        }
      }        
      state.append("\n");
      if (area.getStartTime() < area.getEndTime() && area.getStartTime() > 0) {
        sb.append("start_stop: ").append(Util.ascdate(area.getStartTime())).append(" ").
                append(Util.ascdate(area.getEndTime())).append("\n");
        state.append("Starttime=").append(Util.ascdatetime2(area.getStartTime())).append("\nEndtime=").
                append(Util.ascdatetime2(area.getEndTime()).append("\n"));
      }
      else {
        sb.append("start_stop: ").append(Util.ascdate(Math.max(area.getStartTime(), System.currentTimeMillis()))).
                append(" ").
                append(Util.ascdate(Math.max(area.getEndTime(),date2099.getTimeInMillis()))).append("\n");
        state.append("Starttime=").append(Util.ascdatetime2()).append("\nEndtime=").
                append(Util.ascdatetime2(date2099).append("\n"));   
      }

      String chans = sschan.getNSCL3().substring(7, 10) + " " + sschan.getNSCL2().substring(7, 10) + " " + sschan.getNSCL1().substring(7, 10);
      chans = chans.trim();
      sb.append("channels: ").append(chans).append("\n");
      //sb.append("template_parameters: 10. 20. 0.65 -0.1\n");
      if (configPath.endsWith(Util.FS)) {
        configPath = configPath.substring(0, configPath.length() - 1);
      }
      sb.append("output_path: ").append(configPath).append(Util.FS).append("\n");
      //sb.append("acquisition: 121 9 601\n");    // These are not used, but needed by Config class
      sb.append("template_parameters: ").append(sschan.getTemplateDuration()*2.).append(" ").
              append(sschan.getTemplateDuration()).append(" ").append(sschan.getAveragingDuration()).
              append(" ").append(sschan.getPreEvent()).append("\n");
      // the 1800. and 9. are for non-constant method.
      sb.append("detectionthreshold_parameters: ").append(sschan.getDetectionThreshold()).append(" 1800. 9.0 ").  
              append(sschan.getDetectionType()).append("\n");
      sb.append("completeness: ").append(Util.df22(sschan.getCompleteness())).append("\n");
      sb.append("sample: ").append(sschan.getRate()).append("\n");
      sb.append("location: ").append(loccode.replaceAll("\\.", "-")).append("\n");
      if (cwbIP != null) {
        if (!cwbIP.equals("")) {
          sb.append("inputcwb: ").append(cwbIP).append(" ").append(cwbPort).append("\n");
        }
        else {
          if(config != null) {
            sb.append("inputcwb: ").append(config.getSSDCWBIP()).append(" ").append(config.getSSDCWBPort()).append("\n");
          }
        }
      }
    } else {
      sb.append("#").append(chan).append(" * does not have any phases! Skip it!\n");
      Util.prt(chan + " * does not have any phases!  Skip it");
    }
  }

  /**
   * Look for a segment of data in the SSD CWB and if it does not exist, get it from the configured
   * data source and put it in the SSD one.
   *
   * @param config The subspace configuration object
   * @param nscl Seedname
   * @param arrival Arrival time
   * @param duration duration
   */
  private static void checkForData(Subspace config, String nscl, long arrival, double duration) {
    // Try to get the data from the SSD computer, if we find it, just return
    ArrayList<MiniSeed> ms2 = null;
    // See if the SSDCWB has data from two minute before arrival until one minute after the duration
    String line = "-h " + config.getSSDCWBIP() + " -p " + config.getSSDCWBPort() + " -t null -s " + nscl.replaceAll(" ", "-")
            + " -b '" + Util.ascdatetime2(arrival - 120000) + "' -d " + (duration + 240. ) + " -perf -uf";
    ArrayList<ArrayList<MiniSeed>> mss = EdgeQueryClient.query(line);
    if (!mss.isEmpty()) {
      ms2 = mss.get(0);
      Collections.sort(ms2);
      if (!ms2.isEmpty()) {
        if(ms2.get(0).getTimeInMillis() <= arrival - 120000 && 
          ms2.get(ms2.size()-1).getNextExpectedTimeInMillis() >= arrival + (duration  + 120.)*1000) { // complete?
          Util.prta("CFD: * SSDCWB is complete mss.size()="+ms2.size()+" "+line);
          Util.prt("CFD: ms(0)='"+ms2.get(0)+"\nCFD:ms(l)="+ms2.get(ms2.size()-1));
          EdgeQueryClient.freeQueryBlocks(mss, "Freems2", null);
          return;
        }
        Util.prta("CFD: * looks like data on SSDCWBIP is not complete "+
                Util.ascdatetime2(ms2.get(0).getTimeInMillis()) + "-"+
                Util.ascdatetime2(ms2.get(ms2.size()-1).getNextExpectedTimeInMillis())+
                " does not cover "+Util.ascdatetime(arrival - 120000)+"-"+ 
                Util.ascdatetime2(arrival + (long)((duration + 240.)*1000.)));
      }
    }
    try {
      // Try to get the data from the source CWB, if we get it, store it in the SSD CWB for 2 minutes before to two minute + an extra duration after
      // Reference needs two minutes before an two minutes after so make this a bit longer.
      line = "-h " + config.getCWBIP() + " -p " + config.getCWBPort() + " -t null -s " + nscl.replaceAll(" ", "-")
              + " -b '" + Util.ascdatetime2(arrival - 120000) + "' -d " + (120. + duration + 250.) + " -perf -uf";
      ArrayList<ArrayList<MiniSeed>> mss2 = EdgeQueryClient.query(line);
      if(ms2 != null) {
        if (ms2.isEmpty()) {
          if(!ms2.isEmpty()) {
            EdgeQueryClient.freeQueryBlocks(mss, "Freems2a",null);
          }
          return;
        }
      }
      
      if(mss2.isEmpty()) {
        if(ms2 != null) {
          if(!ms2.isEmpty()) {
            EdgeQueryClient.freeQueryBlocks(mss, "Freems2b",null);
          }
        }     
        Util.prta("CFD: * No data found on : "+line);
        return;
      }
      ArrayList<MiniSeed> ms3 = mss2.get(0);
      if (ms3.isEmpty()) {
        if(ms2 != null) {
          if(!ms2.isEmpty()) {
            EdgeQueryClient.freeQueryBlocks(mss, "Freems2b",null);
          }
        }
        Util.prta("CFD: * No data found on : "+line);
        return;
      }
      Collections.sort(ms3);
      int blksSent=0;
      try (Socket out = new Socket(config.getSSDCWBIP(), config.getSSDInsertPort())) {
        for (MiniSeed ms : ms3) {
          boolean dup=false;
          if(ms2 != null) {
            for(int i=0; i<ms2.size(); i++) {
              if(ms.isDuplicate(ms2.get(i))) {
                dup = true;
                //Util.prta("CFD: * Duplicate not sent to SSDCWBIP ms="+ms);
                break;
              } 
            }
          }
          if(!dup) {
            out.getOutputStream().write(ms.getBuf(), 0, 512);
            Util.prta("CFD: add more data to SSDCWBIP ms="+ms);
            blksSent++;
          }
        }
      }
      if(ms2 != null) {
        if(!ms2.isEmpty()) {
            EdgeQueryClient.freeQueryBlocks(mss, "Freems2c",null);
        }
      }
      if(!ms3.isEmpty()) {
        EdgeQueryClient.freeQueryBlocks(mss2, "Freems3a",null);
      }
      Util.prta("CFD: "+blksSent + " blocks sent to CWBSSD " + config.getSSDCWBIP() + "/" + config.getSSDInsertPort() + " for " + nscl);
    } catch (IOException e) {
      e.printStackTrace();
    }
  }
}
