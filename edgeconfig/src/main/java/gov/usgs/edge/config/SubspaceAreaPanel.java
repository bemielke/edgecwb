/*
 *  This software is in the public domain because it contains materials 
 *  that originally came from the United States Geological Survey, 
 *  an agency of the United States Department of Interior. For more 
 *  information, see the official USGS copyright policy at 
 *  http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.edge.config;
import gov.usgs.anss.db.DBConnectionThread;
import gov.usgs.anss.db.DBObject;
import gov.usgs.anss.util.ErrorTrack;
import gov.usgs.anss.util.FUtil;
import gov.usgs.anss.util.Show;
import gov.usgs.anss.edge.StaSrvEdge;
import gov.usgs.adslserverdb.MDSChannel;
//import gov.usgs.anss.picker.CWBSubspaceDetector;
import gov.usgs.anss.util.Util;
import java.io.File;
import java.awt.Color;
import java.io.IOException;
import java.io.BufferedReader;
import java.io.StringReader;
import java.io.FileNotFoundException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Statement;
import java.util.ArrayList;
import javax.swing.JComboBox;
import javax.xml.bind.JAXBException;
import org.quakeml_1_2.Event;
import gov.usgs.comcat.ComCatEvent;
import gov.usgs.comcat.SimplePick;
import subspacedetector.Config;
import subspacedetector.StationCollection;
import subspacepreprocess.CWBSubspacePreprocess;


/**
 *
 * requirements : The key for the file must be the name of the table and of the JComboBox variable
 * this variable is called "subspaceArea" in the initial form The table name and key name should
 * match and start lower case (subspaceArea). The Class here will be that same name but with upper
 * case at beginning(SubspaceArea).
 * <br> 1) Rename the location JComboBox to the "key" (subspaceArea) value. This should start Lower
 * case.
 * <br> 2) The table name should match this key name and be all lower case.
 * <br> 3) Change all SubspaceArea to ClassName of underlying data (case sensitive!)
 * <br> 4) Change all subspaceArea key value (the JComboBox above)
 * <br> 5) clearScreen should update swing components to new defaults
 * <br> 6) chkForm should check all field for validity. The errorTrack variable is used to track
 * multiple errors. In FUtil.chk* handle checks of the various variable types. Date, double, and IP
 * etc return the "canonical" form for the variable. These should be stored in a class variable so
 * the latest one from "chkForm()" are available to the update writer.
 * <br> 7) newone() should be updated to create a new instance of the underlying data class.
 * <br> 8) oldone() get data from database and update all swing elements to correct values
 * <br> 9) addUpdateAction() add data base set*() operations updating all fields in the DB
 * <br> 10) SubspaceAreaPanel() constructor - good place to change backgrounds using UC.Look() any
 * other weird startup stuff.
 *
 * <br> local variable error - Must be a JTextField for posting error communications
 * <br> local variable updateAdd - Must be the JButton for user clicks to try to post the data
 * <br> ID - JTextField which must be non-editable for posting Database IDs
 * <br>
 * <br>
 * ENUM notes : the ENUM are read from the database as type String. So the constructor should us the
 * strings to set the indexes. However, the writes to the database are done as ints representing the
 * value of the Enum. The JComboBox with the Enum has the strings as Items in the list with indexes
 * equal to the database value of the int. Such JComboBoxes are usually initialized with
 * <br>
 * <br> FUtil.getEnumJComboBox(DBConnectionThread.getConnection("DATABASE"),
 * "tcpstation","rtsport1");
 * <br> Put this in "CodeGeneration" "CustomCreateCode" of the form editor for each Enum.
 * <br>
 * <br> In oldone() get Enum fields with something like :
 * <br> fieldJcomboBox.setSelectedItem(obj.getString("fieldName"));
 * <br> (This sets the JComboBox to the Item matching the string)
 * <br>
 * data class should have code like :
 * <br><br> import java.util.ArrayList; /// Import for Enum manipulation
 * <br> .
 * <br> static ArrayList fieldEnum; // This list will have Strings corresponding to enum
 * <br> .
 * <br> .
 * <br> // To convert an enum we need to create the fieldEnum ArrayList. Do only once(static)
 * <br> if(fieldEnum == null) fieldEnum =
 * FUtil.getEnum(DBConnectionThread.getConnection("DATABASE"),"table","fieldName");
 * <br>
 * <br> // Get the int corresponding to an Enum String for storage in the data base and
 * <br> // the index into the JComboBox representing this Enum
 * <br> localVariable = FUtil.enumStringToInt(fieldEnum, enumString);
 * <br>
 *
 * @author D.C. Ketchum
 */
public final class SubspaceAreaPanel extends javax.swing.JPanel {
  private static String statusValue="NONE";
  private static String originalStatusValue;
  //USER: set this for the upper case conversion of keys.
  private final boolean keyUpperCase = false;    // set this true if all keys must be upper case
  //USER: Here are the local variables
  // NOTE : here define all variables general.  "ArrayList v" is used for main Comboboz
  private final StringBuilder sbtmp = new StringBuilder(100);     // scratch sb, must not be used beyond single routine.
  private final Timestamp start = new Timestamp(100000L);
  private final Timestamp end = new Timestamp(100000L);
  private double latitudeOut, longitudeOut, radiusOut, stationRadiusOut, minMagOut, maxMagOut;
  private boolean buildingEvents, buildingChannels;
  private long eventStartOut, eventEndOut;
  private int cwbPortOut;
  //private final SubspaceChannelPanel channelPanel;
  private SSDEventTypePanel eventTypesPanel;
  private StaSrvEdge stasrv;

  public static boolean isEditable() {
    if(originalStatusValue == null) return true;
    if(statusValue == null) return false;
    if(!originalStatusValue.equals("Operational")) return true;   // It was not in a operational state.
    if(!statusValue.equals("Operational")) return true;// got to non-operation from operational
    return false;
    //return !statusValue.equals("Operational");
  }
  private void doInit() {
    if (EdgeConfig.hasTable("edge", "ssdeventtype")) {
      eventTypesPanel = new SSDEventTypePanel();
      tabs.addTab("EventTypes", eventTypesPanel);
    }
    
    // Good place to add UC.Looks()
    deleteItem.setVisible(true);
    reload.setVisible(false);
    idLab.setVisible(true);
    ID.setVisible(true);
    changeTo.setVisible(true);
    labChange.setVisible(true);
    UC.Look(this);                    // Set color background
    UC.Look(areaStuff);
    UC.Look(channelDeletePanel);
    UC.Look(chPanel);
    UC.Look(eventPanel);
    UC.Look(enterPicks);
    UC.Look(fullWidthStuff);
    UC.Look(overalPanel);

  }

  private void doAddUpdate() throws SQLException {
    // USER: Set all of the fields in data base.  obj.setInt("thefield", ivalue);
    boolean anyerror = false;
    // scan all of the channel panels to make sure they are postable, update our error if not

    error.setBackground(UC.look);
    err.reset();
    // For each channel, make sure its updated
    /*for(int i=0; i<eventChannelPanel.getComponentCount(); i++) {
      Object obj1 = eventChannelPanel.getComponent(i);
      if(obj1 instanceof SubspaceChannelPanelOrg) {
        ((SubspaceChannelPanelOrg) eventChannelPanel.getComponent(i)).
                updateRecord(stmt);
      }
      else Util.prt("Channel panel contains non-panel obj="+obj1);
    }*/
    obj.setDouble("latitude", latitudeOut);
    obj.setDouble("longitude", longitudeOut);
    obj.setDouble("radius", radiusOut);
    obj.setInt("status", status.getSelectedIndex());
    obj.setDouble("stationradius", stationRadiusOut);
    obj.setString("phasesallowed", phasesAllowed.getText());
    obj.setString("cwbip", cwbip.getText());
    obj.setInt("cwbport", cwbPortOut);
    obj.setString("opsargs", opsArgs.getText());
    obj.setString("researchargs", researchArgs.getText());
    obj.setInt("eventtypeid", ((SSDEventType)eventTypeComboBox.getSelectedItem()).getID());

    if (start.getTime() > 100000000) {
      obj.setTimestamp("starttime", start);
    }
    if (end.getTime() > 100000000) {
      obj.setTimestamp("endtime", end);
    }
  }

  protected JComboBox getEventJComboBox() {
    return event;
  }

  private void doOldOne() throws SQLException {
    showStations.setText("Find Stations in Radius");
    showStations.setEnabled(true);
    //USER: Here set all of the form fields to data from the DBObject
    //      subspaceArea.setText(obj.getString("SubspaceArea"));
    // Example : description.setText(obj.getString("description"));
    latitude.setText(obj.getDouble("latitude") + "");
    longitude.setText("" + obj.getDouble("longitude"));
    radius.setText("" + obj.getDouble("radius"));
    status.setSelectedIndex(obj.getInt("status"));
    originalStatusValue = (String) status.getSelectedItem();
    stationRadius.setText("" + obj.getDouble("stationradius"));
    start.setTime(obj.getTimestamp("starttime").getTime());
    end.setTime(obj.getTimestamp("endtime").getTime());
    phasesAllowed.setText(obj.getString("phasesallowed"));
    cwbip.setText(obj.getString("cwbip"));
    cwbPort.setText(obj.getInt("cwbport") == 0 ? "" : obj.getInt("cwbport") + "");
    if (start.getTime() > 100000000l) {
      starttime.setText(Util.ascdatetime(start.getTime()).toString());
    } else {
      starttime.setText("");
    }
    if (end.getTime() > 100000000L) {
      endtime.setText(Util.ascdatetime(end.getTime()).toString());
    } else {
      endtime.setText("");
    }
    opsArgs.setText(obj.getString("opsargs"));
    researchArgs.setText(obj.getString("researchargs"));
    SSDEventTypePanel.getJComboBox(eventTypeComboBox);
    if (obj.getInt("eventtypeid") < 1) {
      eventTypeComboBox.setSelectedIndex(0);
    } else {
      SSDEventTypePanel.setJComboBoxToID(eventTypeComboBox, obj.getInt("eventtypeid"));
    }
    buildEventJComboBox();
  }
  public SubspaceArea getSelectedSubspaceArea() {
    return (SubspaceArea) subspaceArea.getSelectedItem();
  }
  private void buildEventJComboBox() {
    if (subspaceArea.getSelectedIndex() < 0) {
      return;     // Nothing selected
    }
    buildingEvents = true;
    event.removeAllItems();
    try {
      try (Statement stmt = DBConnectionThread.getConnection(DBConnectionThread.getDBSchema()).createStatement() // used for query
              ) {
        String s = "SELECT * FROM edge.subspaceevent WHERE areaid = "
                + ((SubspaceArea) subspaceArea.getSelectedItem()).getID() + " ORDER BY subspaceevent"; //USER: if DBSchema is not right DB, explict here
        try (ResultSet rs = stmt.executeQuery(s)) {
          while (rs.next()) {
            SubspaceEvent eventItem = new SubspaceEvent(rs);
            event.addItem(eventItem);
          }
        }
      }
    } catch (SQLException e) {
      Util.SQLErrorPrint(e, "checking subspaceevents) on table SQL failed for area=" + subspaceArea.getSelectedItem());
    }
    buildingEvents = false;
    if (event.getItemCount() > 0) {
      event.setSelectedIndex(0);
    }
  }

  // This routine must validate all fields on the form.  The err (ErrorTrack) variable
  // is used by the FUtil to verify fields.  Use FUTil or custom code here for verifications
  private boolean chkForm() {
    // Do not change
    err.reset();
    error.setText("");
    UC.Look(error);
    if(subspaceArea.getSelectedIndex() < 0 && subspaceArea.getSelectedItem() == null) return false;
    if(!isEditable()) {
      err.set(true);
      err.appendText("Status does not allow editing - change it from operational");
      error.setText(err.getText());
      error.setBackground(UC.red);
      return true;
    }
    
    //USER:  Your error checking code goes here setting the local variables to valid settings
    // note : Use FUtil.chk* to check dates, timestamps, IP addresses, etc and put them in
    // canonical form.  store in a class variable.  For instance :
    //   sdate = FUtil.chkDate(dateTextField, err);
    // Any errors found should err.set(true);  and err.append("error message");
    if (starttime.getText().trim().length() > 0) {
      start.setTime(FUtil.chkDatetime(starttime, err).getTime());
    } else {
      start.setTime(10000L);
    }
    if (endtime.getText().trim().length() > 0) {
      end.setTime(FUtil.chkDatetime(endtime, err).getTime());
    } else {
      end.setTime(10000L);
    }

    latitudeOut = FUtil.chkDouble(latitude, err, -90., 90.);
    longitudeOut = FUtil.chkDouble(longitude, err, -180., 180.);
    radiusOut = FUtil.chkDouble(radius, err, 0., 20.);
    stationRadiusOut = FUtil.chkDouble(stationRadius, err, 0., 2000.);
    Timestamp ts = FUtil.chkTimestamp(eventStart, err);
    Timestamp tsend = FUtil.chkTimestamp(endEvent, err);
    eventStartOut = ts.getTime();
    eventEndOut = tsend.getTime();
    minMagOut = FUtil.chkDouble(minMag, err, 0., 10., true);
    maxMagOut = FUtil.chkDouble(maxMag, err, 0., 10., true);
    cwbPortOut = FUtil.chkInt(cwbPort, err, 0, 32767, true);
   
    // No CHANGES : If we found an error, color up error box
    if (err.isSet()) {
      error.setText(err.getText());
      error.setBackground(UC.red);
    } else {
      addUpdate.setEnabled(true);
    }
    return err.isSet();
  }

  /**
   * This is the big work routine. Get the QuakeML because and evt is new or the users has said so,
   * and them make sure the subspaceevent tables reflects this latest one and that the subspace
   * channels also reflect the current settings. This includes removing channels that are no long
   * part of the set.
   *
   * 1) get and parse the QuakeML using ComCat and the ComCatEvent class 2) Update the subspaceevent
   * table with the latest evt origin times, coordinates, magnitude, mag type if evt is no present
   * in subspace evt add it. Save the table ID for this row. 3) Make the channel list anew from the
   * list of channel regexp (makeChannelList()) 4) For each existing subspacechannel associated with
   * this evt, update the arrival time and station coordinates, drop each existing channel from
   * channelList so that the new channels will be the only ones on it 5) For each non-null left on
   * the channelist, create a subspacechannel record with pick and coordinates and populate one to
   * three channels in a single subspacechannel record. 6) Rebuild the channel scrolling region
   * showing all of the channels
   *
   * @param qmleventID The string for ComCat for this id (something like 'us20008vhl'
   */
  private void loadEventInfo(String qmleventID, int areaID) {
    String filename = "quakeml/" + qmleventID + ".qml";
    Util.chkFilePath(filename);
    File f = new File(filename);

    ComCatEvent web = new ComCatEvent(null);
    // If we have cached this file, read it in and put it int the ComCatEvent
    try {
      if (f.exists()) {
        Util.clear(sbtmp);
        Util.readFileToSB(filename, sbtmp);
        web.loadQuakeML(sbtmp);
      } else {    // File does not exist, go to the web
        web.setDebug(true);
        web.setEvent(qmleventID);
        long len = web.executeURL();
        Util.writeFileFromSB(filename, web.getSB());
      }

    } catch (IOException e) {
      Util.prta("IOException reading existing quakeml file =" + filename + " e=" + e);
      e.printStackTrace();
      return;             // Cannot load this information
    }

    String nscl1, nscl2, nscl3;
    Timestamp tstamp = new Timestamp(1000000L);
    StringBuilder sql = new StringBuilder(100);
    int sseventID = -1;
    int neventIns = 0;
    int neventUpd = 0;
    int nsschIns = 0;
    int nsschUpd = 0;
    int nssevchIns = 0;
    int nssevchUpd = 0;
    //try {
    //Util.prt(e.getSB(null));
    try {
      int nevents = web.parseQuakeML();         // update the evt information in the database
      web.getEvent(0);            // get the evt
      // Now look up this ssdevent and if it does not exist, create it, else update it.
      try {
        try (Statement stmt = DBConnectionThread.getConnection(DBConnectionThread.getDBSchema()).
                createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE); // used for query
                Statement stmt2 = DBConnectionThread.getConnection(DBConnectionThread.getDBSchema()).
                        createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE);) {
          Util.clear(sql).append("SELECT * FROM edge.subspaceevent WHERE subspaceevent='").append(qmleventID).
                  append("' AND areaid =").append(areaID); //USER: if DBSchema is not right DB, explict here
          try (ResultSet rs = stmt.executeQuery(sql.toString())) {
            if (rs.next()) {
              sseventID = rs.getInt("id");
              rs.updateDouble("latitude", web.getLatitude());
              rs.updateDouble("longitude", web.getLongitude());
              rs.updateDouble("depth", web.getDepth());
              rs.updateDouble("magnitude", web.getPreferredMagnitude());
              rs.updateString("magmethod", web.getPreferredMagMethod());
              Util.clear(sql).append(
                      "UPDATE edge.subspaceevent SET origintime='").append(Util.ascdatetime2(web.getPreferredOriginTime())).
                      append("',updated=now()").append(" WHERE id=").append(rs.getInt("id"));  // again, rs.updateTimestamp() drops milliseconds.
              rs.updateRow();
              stmt2.executeUpdate(sql.toString());
              neventUpd++;
            } else {      // If thre is no row for this evt, create one
              Util.clear(sql).append(
                      "INSERT INTO edge.subspaceevent (subspaceevent, areaid, latitude, longitude, depth, magnitude, "
                      + "magmethod, origintime,updated, created_by, created) VALUE ('").append(qmleventID).
                      append("',").append(areaID).append(",").append(web.getLatitude()).
                      append(",").append(web.getLongitude()).append(",").append(web.getDepth()).
                      append(",").append(web.getPreferredMagnitude()).append(",'").
                      append(web.getPreferredMagMethod()).append("','").
                      append(Util.ascdatetime2(web.getPreferredOriginTime())).append("',now(),0,now())");
              Util.prt(sql);
              neventIns++;
              stmt.executeUpdate(sql.toString());
              sseventID = DBConnectionThread.getThread(DBConnectionThread.getDBSchema()).getLastInsertID("subspaceevent");
            }
            eventSummary.setText(web.getEventID() + " " + Util.ascdatetime2(web.getPreferredOriginTime()) + " "
                    + web.getLatitude() + " " + web.getLongitude() + " " + web.getDepth() + " "
                    + web.getPreferredMagnitude() + " " + web.getPreferredMagMethod());
          }
        }
      } catch (SQLException e) {
        Util.SQLErrorPrint(e, "*** looking for events matching " + qmleventID + " SQL failed");
        e.printStackTrace();
      }
    } catch (JAXBException e) {
      Util.prta("Got a JAXB error e=" + e);
    }

    // Now sort through all of the events and channels in this area and create or update SubspaceEventChannel records
    try (Statement stmt = DBConnectionThread.getConnection(DBConnectionThread.getDBSchema()).
            createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE)) {

      SubspaceEvent.events = null;
      SubspaceEvent.makeEvents();
      // Now makeup the channel list of channels for this area 
      SubspaceChannel.subspaceChannels = null;
      SubspaceChannel.makeSubspaceChannels();
      SubspaceEventChannel.channelEvents = null;
      SubspaceEventChannel.makeSubspaceEventChannels();

      // For all events, and channels in this area, make sure there is a corresponding SubspaceChannelEvent record (unless no pick is present)
      for (SubspaceEvent evt : SubspaceEvent.events) {     // All events
        if (evt.getAreaID() != areaID || !evt.getSubspaceEvent().equalsIgnoreCase(qmleventID)) {
          continue;          // This eventt is not in this area
        }
        for (SubspaceChannel sschan : SubspaceChannel.subspaceChannels) {// For all channels
          if (sschan.getAreaID() != areaID) {
            continue;      // channel is not in the area either.     
          }
          // See if there is a pick in the QML record
          SimplePick pick = null;
          try {
            String comp = sschan.getNSCL1();
            pick = web.getPick(comp);
            if (pick == null) {
              Util.prt("* No pick for " + qmleventID + " chn=" + sschan + " skipping SSEventChannel creation");
              continue;
            }
            if (!phasesAllowed.getText().equals("")) {
              if (!pick.getPhase().matches(phasesAllowed.getText())) {
                Util.prt("** " + qmleventID + " chn=" + sschan + " ph=" + pick.getPhase() + " does not match " + phasesAllowed.getText() + " skipping SSEventChannel creation");
                continue;
              }
            }
          } catch (JAXBException e) {
            Util.prt("*** JAXB error gettint pick for " + sschan.getSubspaceChannel() + " evt=" + evt.getSubspaceEvent() + " areaID=" + areaID);
          }

          // Look through all of the SubspaceEventChannel records, and update it if found, or create it if not found.
          boolean found = false;
          for (SubspaceEventChannel evch : SubspaceEventChannel.channelEvents) {
            if (evch.getChannelID() == sschan.getID() && evch.getEventID() == evt.getID()) {
              found = true;
              if (pick != null) {
                if (pick.getPhase() != null) {
                  Util.clear(sql).append("UPDATE edge.subspaceeventchannel SET phase='").append(pick.getPhase()).
                          append("',arrivaltime='").append(Util.ascdatetime2(pick.getPickTimeInMillis())).
                          append("',updated=now() WHERE id=").append(evch.getID());
                  stmt.executeUpdate(sql.toString());
                  nssevchUpd++;
                }
              }
              break;
            }
          }
          // do we have this, if so do nothingn, if not, create the SubspaceEventChannel to match.
          if (!found) {
            if (pick != null) {
              if (pick.getPhase() != null) {
                Util.clear(sql).append(
                        "INSERT INTO edge.subspaceeventchannel (channelID, eventID, phase, arrivaltime, disable, updated,created_by,created) VALUES (").
                        append(sschan.getID()).append(",").append(evt.getID()).append(",'").append(pick.getPhase()).append("','").
                        append(Util.ascdatetime2(pick.getPickTimeInMillis())).append("',0,now(), 0, now())");
                stmt.executeUpdate(sql.toString());
                nssevchIns++;
              }
            }
          }
        }
      }
    } catch (SQLException e) {
      Util.SQLErrorPrint(e, "*** Trying to build SubspaceEventChannel table SQL failed");
      e.printStackTrace();
    }

    // Update every channel in this area coordinates
    try (Statement stmt = DBConnectionThread.getConnection(DBConnectionThread.getDBSchema()).
            createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE)) { // used for query    
      // For each subspace channel assigned in this area, update the coordinates if they have changed
      for (int i = 0; i < SubspaceChannel.subspaceChannels.size(); i++) {
        if (SubspaceChannel.subspaceChannels.get(i).getAreaID() == areaID) {
          SubspaceChannel chn = SubspaceChannel.subspaceChannels.get(i);
          if (stasrv != null) {
            try {
              int ii = stasrv.getSACResponse(chn.getNSCL1(),
                      Util.ascdatetime(web.getPreferredOriginTime()).toString().replaceAll(" ", "-"),
                      "um", Util.clear(sbtmp));
            }
            catch(IOException e) {
              Util.prt("*** stasrv edge reported an IOException e="+e);
              
            }
            if (sbtmp.indexOf("no channels found") < 0) {  // Epoch is probably not open on this date.
              MDSChannel mds = new MDSChannel(sbtmp.toString());
              if (mds.getLatitude() != 0. || mds.getLongitude() != 0.) {
                if (chn.getLatitude() != mds.getLatitude() || chn.getLongitude() != mds.getLongitude()
                        || chn.getElevation() != mds.getElevation() || chn.getRate() != mds.getRate()) {
                  Util.clear(sql).append("UPDATE edge.subspacechannel SET latitude=").append(mds.getLatitude()).
                          append(",longitude=").append(mds.getLongitude()).
                          append(",elevation=").append(mds.getElevation()).
                          append(",rate=").append(mds.getRate()).
                          append(",updated=now()").
                          append(" WHERE id=").append(chn.getID());
                  stmt.executeUpdate(sql.toString());
                  nsschUpd++;
                }
              }
            }
          }
        }
      }
    } catch (SQLException e) {
      Util.SQLErrorPrint(e, "*** Updating SubspaceChannel coordinates SQL failed");
      e.printStackTrace();
    }
    Util.prt(Util.clear(sql).append("loadEventInfo for ").append(qmleventID).
            append(" #evtIns=").append(neventIns).append(" #evtUpd=").append(neventUpd).
            append(" #chnIns=").append(nsschIns).append(" #chnUpd=").append(nsschUpd).
            append(" #evtchIns=").append(nssevchIns).append(" #evtchUpd=").append(nssevchUpd));
  }

  // This routine must set the form to initial state.  It does not update the JCombobox
  private void clearScreen() {
    Subspace config = ((SubspacePanel)configPanel).getConfig();
    // Do not change
    ID.setText("");
    UC.Look(error);
    UC.Look(bottomStuff);
    error.setText("");
    addUpdate.setEnabled(false);
    addUpdate.setText("Enter a SubspaceArea");
    deleteItem.setEnabled(false);
    changeTo.setText("");

    //USER: Clear all fields for this form, this sets "defaults" for new screen
    // description.setText("");
    // These are on the AreaParams table.
    latitude.setText("0.");
    longitude.setText("0.");
    radius.setText("1.");
    stationRadius.setText("100.");
    starttime.setText("");
    endtime.setText("");
    eventSummary.setText("");
    cwbPort.setText("");
    cwbip.setText("");
    eventStart.setText("");
    if(config != null) {
      if(config.getResearchArgs() != null) {
        researchArgs.setText(config.getResearchArgs());
      }
      if(config.getOpsArgs() != null) 
        opsArgs.setText(config.getOpsArgs());
    }
    svd.setSelected(false);
    status.setSelectedIndex(0);
    
    // eventPanel fields
    minMag.setText("2.0");
    maxMag.setText("");
    phasesAllowed.setText("");

    endEvent.setText("");
    showStations.setText("Fetch Stations - Area not yet created!");
    showStations.setEnabled(false);

    // Channel panel
    ((SubspaceChannelPanel) chPanel).clearScreen();
    ((SubspaceChannelDeletePanel) channelDeletePanel).clearScreen();

  }

  private SubspaceArea newOne() {

    return new SubspaceArea(0, ((String) subspaceArea.getSelectedItem()).toUpperCase() //, more
            ,
             0., 0., 0., 1, new Timestamp(10000L), new Timestamp(10000L), 0, 10000L, 100000L, "", "",0
    );
  }

  /**
   * ******************** NO USER MODIFICATION BELOW HERE EXCEPT FOR ACTIONS *************
   */
  DBObject obj;
  ErrorTrack err = new ErrorTrack();
  boolean initiating = false;

  private void oldOne(String p) throws SQLException {
    obj = new DBObject(DBConnectionThread.getThread(DBConnectionThread.getDBSchema()),
            DBConnectionThread.getDBSchema(), // USER: Schema, if this is not right in URL load, it must be explicit here
            "subspacearea", "subspacearea", p);                     // table name and field name are usually the same

    if (obj.isNew()) {
      //Util.prt("object is new"+p);
    } else {
      //USER: Here set all of the form fields to data from the DBObject
      //      subspaceArea.setText(obj.getString("Subspacearea"));
      // Example : description.setText(obj.getString("description"));
      doOldOne();

    }           // End else isNew() - processing to form
  }

  /**
   * This method is called from within the constructor to initialize the form. WARNING: Do NOT
   * modify this code. The content of this method is always regenerated by the Form Editor.
   */
  // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
  private void initComponents() {
    java.awt.GridBagConstraints gridBagConstraints;

    labMain = new javax.swing.JLabel();
    subspaceArea = getJComboBox();
    error = new javax.swing.JTextField();
    tabs = new javax.swing.JTabbedPane();
    overalPanel = new javax.swing.JPanel();
    areaStuff = new javax.swing.JPanel();
    labLat = new javax.swing.JLabel();
    latitude = new javax.swing.JTextField();
    latLong = new javax.swing.JLabel();
    longitude = new javax.swing.JTextField();
    labRadius = new javax.swing.JLabel();
    radius = new javax.swing.JTextField();
    labStatRadus = new javax.swing.JLabel();
    stationRadius = new javax.swing.JTextField();
    showStations = new javax.swing.JButton();
    svd = new javax.swing.JCheckBox();
    doPreprocess = new javax.swing.JButton();
    status = new javax.swing.JComboBox<>();
    eventTypeComboBox = new javax.swing.JComboBox<>();
    fullWidthStuff = new javax.swing.JPanel();
    labStart = new javax.swing.JLabel();
    labEnd = new javax.swing.JLabel();
    starttime = new javax.swing.JTextField();
    endtime = new javax.swing.JTextField();
    labCWBIP = new javax.swing.JLabel();
    cwbip = new javax.swing.JTextField();
    labCWBport = new javax.swing.JLabel();
    cwbPort = new javax.swing.JTextField();
    labResearch = new javax.swing.JLabel();
    opsLab = new javax.swing.JLabel();
    jSeparator3 = new javax.swing.JSeparator();
    opsArgs = new javax.swing.JTextField();
    argsLab = new javax.swing.JLabel();
    researchArgs = new javax.swing.JTextField();
    channelDeletePanel = new gov.usgs.edge.config.SubspaceChannelDeletePanel(this);
    chPanel = new SubspaceChannelPanel(this);
    eventPanel = new javax.swing.JPanel();
    labEvent = new javax.swing.JLabel();
    event = new javax.swing.JComboBox();
    eventSummary = new javax.swing.JTextField();
    deleteEvent = new javax.swing.JButton();
    loadEvent = new javax.swing.JButton();
    loadAllEvents = new javax.swing.JButton();
    jSeparator1 = new javax.swing.JSeparator();
    labStartEvent = new javax.swing.JLabel();
    eventStart = new javax.swing.JTextField();
    labEndEvent = new javax.swing.JLabel();
    endEvent = new javax.swing.JTextField();
    labMinMag = new javax.swing.JLabel();
    minMag = new javax.swing.JTextField();
    labMaxMag = new javax.swing.JLabel();
    maxMag = new javax.swing.JTextField();
    labPhasesAllowed = new javax.swing.JLabel();
    phasesAllowed = new javax.swing.JTextField();
    findEvents = new javax.swing.JButton();
    eventsTextScrool = new javax.swing.JScrollPane();
    eventsText = new javax.swing.JTextArea();
    enterPicks = new javax.swing.JPanel();
    jScrollPane1 = new javax.swing.JScrollPane();
    pickArea = new javax.swing.JTextArea();
    parsePicks = new javax.swing.JButton();
    configPanel = new SubspacePanel();
    bottomStuff = new javax.swing.JPanel();
    addUpdate = new javax.swing.JButton();
    labChange = new javax.swing.JLabel();
    changeTo = new javax.swing.JTextField();
    deleteItem = new javax.swing.JButton();
    reload = new javax.swing.JButton();
    idLab = new javax.swing.JLabel();
    ID = new javax.swing.JTextField();

    setLayout(new java.awt.GridBagLayout());

    labMain.setText("Area");
    labMain.setToolTipText("This button commits either a new item or the changes for an updating item.  Its label with change to reflect that.");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_END;
    add(labMain, gridBagConstraints);

    subspaceArea.setEditable(true);
    subspaceArea.setToolTipText("<html>\nSelect an item to edit in the JComboBox or type in the name of an item.   \n<br>If the typed name does not match an existing item, an new item is assumed.  \n<br>\nNames should be chosen so that a human being will know which earthquake or area is being configured.  Example:  Montana20170706 referring to the main shock on that date.\n<br>\n</html>");
    subspaceArea.setMinimumSize(new java.awt.Dimension(326, 26));
    subspaceArea.setPreferredSize(new java.awt.Dimension(326, 26));
    subspaceArea.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        subspaceAreaActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(subspaceArea, gridBagConstraints);

    error.setEditable(false);
    error.setColumns(40);
    error.setMinimumSize(new java.awt.Dimension(488, 22));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(error, gridBagConstraints);

    tabs.setMaximumSize(new java.awt.Dimension(710, 670));
    tabs.setMinimumSize(new java.awt.Dimension(710, 670));
    tabs.setPreferredSize(new java.awt.Dimension(710, 670));

    areaStuff.setBorder(javax.swing.BorderFactory.createEtchedBorder());
    areaStuff.setCursor(new java.awt.Cursor(java.awt.Cursor.DEFAULT_CURSOR));
    areaStuff.setMinimumSize(new java.awt.Dimension(700, 250));
    areaStuff.setPreferredSize(new java.awt.Dimension(700, 250));
    areaStuff.setLayout(new java.awt.GridBagLayout());

    labLat.setText("Latitude:");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_END;
    areaStuff.add(labLat, gridBagConstraints);

    latitude.setToolTipText("The latitude of the center of the area.  Normally the epicenter of the original quake of interest.");
    latitude.setMinimumSize(new java.awt.Dimension(90, 26));
    latitude.setPreferredSize(new java.awt.Dimension(90, 26));
    latitude.addFocusListener(new java.awt.event.FocusAdapter() {
      public void focusLost(java.awt.event.FocusEvent evt) {
        latitudeFocusLost(evt);
      }
    });
    latitude.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        latitudeActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
    areaStuff.add(latitude, gridBagConstraints);

    latLong.setText("Longitude:");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_END;
    areaStuff.add(latLong, gridBagConstraints);

    longitude.setToolTipText("The longitude of the center of the area.  Normally the epicenter of the original quake of interest.");
    longitude.setActionCommand("<Not Set>");
    longitude.setMinimumSize(new java.awt.Dimension(90, 26));
    longitude.setPreferredSize(new java.awt.Dimension(90, 26));
    longitude.addFocusListener(new java.awt.event.FocusAdapter() {
      public void focusLost(java.awt.event.FocusEvent evt) {
        longitudeFocusLost(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
    areaStuff.add(longitude, gridBagConstraints);

    labRadius.setText("Event Radius (km):");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 3;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_END;
    areaStuff.add(labRadius, gridBagConstraints);

    radius.setToolTipText("A radius about the latitude and longitude of interest in kilometers.");
    radius.setMinimumSize(new java.awt.Dimension(50, 26));
    radius.setPreferredSize(new java.awt.Dimension(50, 26));
    radius.addFocusListener(new java.awt.event.FocusAdapter() {
      public void focusLost(java.awt.event.FocusEvent evt) {
        radiusFocusLost(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 3;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
    areaStuff.add(radius, gridBagConstraints);

    labStatRadus.setText("Station radius (km):");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 5;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_END;
    areaStuff.add(labStatRadus, gridBagConstraints);

    stationRadius.setMinimumSize(new java.awt.Dimension(50, 26));
    stationRadius.setPreferredSize(new java.awt.Dimension(50, 26));
    stationRadius.addFocusListener(new java.awt.event.FocusAdapter() {
      public void focusLost(java.awt.event.FocusEvent evt) {
        stationRadiusFocusLost(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 5;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
    areaStuff.add(stationRadius, gridBagConstraints);

    showStations.setText("Find Stations in Radius");
    showStations.setToolTipText("Click this to make a list of channels based on stations inside the radius that are active.");
    showStations.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        showStationsActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 2;
    gridBagConstraints.gridy = 5;
    areaStuff.add(showStations, gridBagConstraints);

    svd.setText("Preprocess using SVD");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 6;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
    areaStuff.add(svd, gridBagConstraints);

    doPreprocess.setText("Do Preprocess");
    doPreprocess.setToolTipText("Run the SubspacePreprocess routines on this configured area.  This will create all of the files needed to actually run the subspace detector.");
    doPreprocess.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        doPreprocessActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 2;
    gridBagConstraints.gridy = 6;
    areaStuff.add(doPreprocess, gridBagConstraints);

    status.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "Initial Design", "Operational", "Review", "Disabled", "Research/Offline" }));
    status.setToolTipText("<html>\nThis is the status of this configuration.  Choose from the following :\n<PRE>\nInitial Design     -  This is a new area and its design is not complete, it is not to be run anywhere.\nOperational        - This is a design which is currently running, do not edit the design in this mode.\nResearch            - This is a researcher design and is being run off line on the researchers workstation.\nReview               - This is a operation design taken to review mode so changes can be made, the new \n                             design does not take effect until the status is changed to \"Operational\".  Unchanged \n                             design from last operational mode is still running.\nDisabled            -  This was an operational design, but it is disabled from running until this status is changed.\n</pre>\n</html>");
    status.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        statusActionPerformed(evt);
      }
    });
    areaStuff.add(status, new java.awt.GridBagConstraints());

    eventTypeComboBox.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        eventTypeComboBoxActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 2;
    gridBagConstraints.gridy = 1;
    areaStuff.add(eventTypeComboBox, gridBagConstraints);

    overalPanel.add(areaStuff);

    fullWidthStuff.setLayout(new java.awt.GridBagLayout());

    labStart.setText("StartTime (batch) :");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 10;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_END;
    fullWidthStuff.add(labStart, gridBagConstraints);

    labEnd.setText("EndTime (batch) :");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 11;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_END;
    fullWidthStuff.add(labEnd, gridBagConstraints);

    starttime.setColumns(20);
    starttime.setToolTipText("<html>\nWhen an optional  start and stop time are set, a separate run of the Subspace detector is run between those times and a real time detector is started as well.  \n<br>\nWhen the processing of the interval is completed, these fields become blank again.\n<br>\nFormat: yyyy-mm-dd hh:mm:ss\n</html>");
    starttime.setMinimumSize(new java.awt.Dimension(200, 26));
    starttime.setPreferredSize(new java.awt.Dimension(200, 26));
    starttime.addFocusListener(new java.awt.event.FocusAdapter() {
      public void focusLost(java.awt.event.FocusEvent evt) {
        starttimeFocusLost(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 10;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
    fullWidthStuff.add(starttime, gridBagConstraints);

    endtime.setToolTipText("Start time in the form yyyy-mm-dd hh:mm:ss");
    endtime.setMinimumSize(new java.awt.Dimension(200, 26));
    endtime.setPreferredSize(new java.awt.Dimension(200, 26));
    endtime.addFocusListener(new java.awt.event.FocusAdapter() {
      public void focusLost(java.awt.event.FocusEvent evt) {
        endtimeFocusLost(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 11;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
    fullWidthStuff.add(endtime, gridBagConstraints);

    labCWBIP.setText("CWBIP (\"\" for OPS):");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 13;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_END;
    fullWidthStuff.add(labCWBIP, gridBagConstraints);

    cwbip.setToolTipText("<html>\nWhen in research mode (not running real time in OPS), this is the CWB DNS name or IP address which contains the data.\n</html>");
    cwbip.setMinimumSize(new java.awt.Dimension(200, 26));
    cwbip.setPreferredSize(new java.awt.Dimension(200, 26));
    cwbip.addFocusListener(new java.awt.event.FocusAdapter() {
      public void focusLost(java.awt.event.FocusEvent evt) {
        cwbipFocusLost(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 13;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
    fullWidthStuff.add(cwbip, gridBagConstraints);

    labCWBport.setText("CWBPort (\"\" for OPS):");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 14;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_END;
    fullWidthStuff.add(labCWBport, gridBagConstraints);

    cwbPort.setToolTipText("<html>\nWhen in research mode (not running in OPS), this is the port of the CWB to supply the data.  \n</html>\n");
    cwbPort.setMinimumSize(new java.awt.Dimension(60, 26));
    cwbPort.setPreferredSize(new java.awt.Dimension(60, 26));
    cwbPort.addFocusListener(new java.awt.event.FocusAdapter() {
      public void focusLost(java.awt.event.FocusEvent evt) {
        cwbPortFocusLost(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 14;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
    fullWidthStuff.add(cwbPort, gridBagConstraints);

    labResearch.setText("This section for offline/research runs");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 9;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
    fullWidthStuff.add(labResearch, gridBagConstraints);

    opsLab.setText("Ops Args:");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 7;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_END;
    fullWidthStuff.add(opsLab, gridBagConstraints);

    jSeparator3.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(0, 0, 0)));
    jSeparator3.setMinimumSize(new java.awt.Dimension(500, 10));
    jSeparator3.setPreferredSize(new java.awt.Dimension(500, 10));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 8;
    fullWidthStuff.add(jSeparator3, gridBagConstraints);

    opsArgs.setFont(new java.awt.Font("Monospaced", 0, 10)); // NOI18N
    opsArgs.setToolTipText("<html>\n* <pre>\n * switch   Value         Description\n * -config  filename     Configuration file name for the SSD config file (from Preprocessor)\n * -ssddbg               If present, turn on more detailed debugging\n * \n * \n * The switchs to the super class CWBPicker are documented in this class.   Below is a copy:\n *  * switch   args    description\n  * For properties that come from a template file (separator for key#pairs is '#':\n * Property      switch args      description\n * NSCL         -c NNSSSSSCCCLL  The channel to process, comes from the -c mostly but can be in preconfigured template\n * Blocksize    -blocksize       The processing block size in seconds\n * PickDBServer -db   dbURL      Set the DB where the status.globalpicks is located (used to generate ID numbers) 'NoDB' is legal\n * Author       -auth author     Set the author (def=FP6)\n * Agency       -agency agency   Set the agency\n * Title        -t     title     A descriptive title for this instance (example FP6-RP)\n * CWBIPAdrdess -h     cwb.ip    Set source of time series data (def cwbpub)\n * CWBPort      -p     port      Set the CWB port (def=2061)\n * MakeFiles    -mf              If present, this instance will make files in MiniSeed from parameters.\n * PickerTag    -f     pickerTag Some tag associated with the picker (def=NONE)\n * JSONArgs     -json  1;2&3;4   @see gov.usgs.picker.JsonDetectionSender for details\n *\n * For properties kept in the state file :\n * StartTime    -b     begintime Starting time, not valid on a segment picker\n * EndTime      -e     endTIme   Ending Time\n * Rate         -rt    Hz        The digit rate, else one from the time series will be used\n</pre>\n</html>\n");
    opsArgs.setMinimumSize(new java.awt.Dimension(530, 26));
    opsArgs.setPreferredSize(new java.awt.Dimension(530, 26));
    opsArgs.setRequestFocusEnabled(false);
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 7;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
    fullWidthStuff.add(opsArgs, gridBagConstraints);

    argsLab.setText("Research Args:");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 16;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_END;
    fullWidthStuff.add(argsLab, gridBagConstraints);

    researchArgs.setFont(new java.awt.Font("Monospaced", 0, 10)); // NOI18N
    researchArgs.setToolTipText("<html>\n* <pre>\n * switch   Value         Description\n * -config  filename     Configuration file name for the SSD config file (from Preprocessor)\n * -ssddbg               If present, turn on more detailed debugging\n * \n * \n * The switchs to the super class CWBPicker are documented in this class.   Below is a copy:\n *  * switch   args    description\n  * For properties that come from a template file (separator for key#pairs is '#':\n * Property      switch args      description\n * NSCL         -c NNSSSSSCCCLL  The channel to process, comes from the -c mostly but can be in preconfigured template\n * Blocksize    -blocksize       The processing block size in seconds\n * PickDBServer -db   dbURL      Set the DB where the status.globalpicks is located (used to generate ID numbers) 'NoDB' is legal\n * Author       -auth author     Set the author (def=FP6)\n * Agency       -agency agency   Set the agency\n * Title        -t     title     A descriptive title for this instance (example FP6-RP)\n * CWBIPAdrdess -h     cwb.ip    Set source of time series data (def cwbpub)\n * CWBPort      -p     port      Set the CWB port (def=2061)\n * MakeFiles    -mf              If present, this instance will make files in MiniSeed from parameters.\n * PickerTag    -f     pickerTag Some tag associated with the picker (def=NONE)\n * JSONArgs     -json  1;2&3;4   @see gov.usgs.picker.JsonDetectionSender for details\n *\n * For properties kept in the state file :\n * StartTime    -b     begintime Starting time, not valid on a segment picker\n * EndTime      -e     endTIme   Ending Time\n * Rate         -rt    Hz        The digit rate, else one from the time series will be used\n</pre>\n</html>\n");
    researchArgs.setMinimumSize(new java.awt.Dimension(530, 26));
    researchArgs.setPreferredSize(new java.awt.Dimension(530, 26));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 16;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
    fullWidthStuff.add(researchArgs, gridBagConstraints);

    overalPanel.add(fullWidthStuff);

    tabs.addTab("Area", overalPanel);

    channelDeletePanel.setMinimumSize(new java.awt.Dimension(575, 250));
    channelDeletePanel.setPreferredSize(new java.awt.Dimension(575, 200));
    tabs.addTab("Remove Channels", channelDeletePanel);

    chPanel.setBorder(javax.swing.BorderFactory.createEtchedBorder());
    chPanel.setMinimumSize(new java.awt.Dimension(575, 250));
    chPanel.setPreferredSize(new java.awt.Dimension(575, 200));
    tabs.addTab("Channels", chPanel);

    eventPanel.setRequestFocusEnabled(false);
    eventPanel.addFocusListener(new java.awt.event.FocusAdapter() {
      public void focusGained(java.awt.event.FocusEvent evt) {
        eventPanelFocusGained(evt);
      }
    });

    labEvent.setText("Select or Add Event(s):");
    eventPanel.add(labEvent);

    event.setEditable(true);
    event.setMaximumRowCount(20);
    event.setToolTipText("<html>\nSelect an event from the JCombo box \n<br>\nOR\n<br>\nType in a single or multiple event IDs separated by spaces, commas or newlines.  When one or more events IDs are entered here new SubspaceEvent records are create and new SubspaceChannel records are created for all channels listed above which have a pick in the QuakeML for the event IDs from the FDSN server at earthquake.usgs.gov comcat server.  \n<br>\nExamples : \n<br>\nus12345M7\n<br>\nus12345M7 US23456Q2 mb201454T7\n</html>\n");
    event.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        eventActionPerformed(evt);
      }
    });
    eventPanel.add(event);

    eventSummary.setEditable(false);
    eventSummary.setBackground(new java.awt.Color(204, 204, 204));
    eventSummary.setFont(new java.awt.Font("Monospaced", 0, 10)); // NOI18N
    eventSummary.setMinimumSize(new java.awt.Dimension(575, 26));
    eventSummary.setPreferredSize(new java.awt.Dimension(575, 26));
    eventPanel.add(eventSummary);

    deleteEvent.setText("Delete Event");
    deleteEvent.setToolTipText("The currently selected event is deleted from this list configured for this area.");
    deleteEvent.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        deleteEventActionPerformed(evt);
      }
    });
    eventPanel.add(deleteEvent);

    loadEvent.setText("Load Event From ComCat");
    loadEvent.setToolTipText("Manually force the selected event to be updated and update the SubspaceEvent and SubspaceChannels associated with this event.");
    loadEvent.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        loadEventActionPerformed(evt);
      }
    });
    eventPanel.add(loadEvent);

    loadAllEvents.setText("Reload All Events");
    loadAllEvents.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        loadAllEventsActionPerformed(evt);
      }
    });
    eventPanel.add(loadAllEvents);

    jSeparator1.setBorder(javax.swing.BorderFactory.createEtchedBorder());
    jSeparator1.setMinimumSize(new java.awt.Dimension(600, 5));
    jSeparator1.setPreferredSize(new java.awt.Dimension(500, 5));
    eventPanel.add(jSeparator1);

    labStartEvent.setText("Earliest Event (yyyy-mm-dd hh:mm def=none):");
    labStartEvent.setToolTipText("yyyy-mm-dd hh:mm The earliest origin time to allow when finding events.  Default is no time limit.");
    eventPanel.add(labStartEvent);

    eventStart.setToolTipText("yyyy-mm-dd hh:mm The earliest origin time to allow when finding events.");
    eventStart.setMinimumSize(new java.awt.Dimension(230, 26));
    eventStart.setPreferredSize(new java.awt.Dimension(230, 26));
    eventStart.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        eventStartActionPerformed(evt);
      }
    });
    eventPanel.add(eventStart);

    labEndEvent.setText("Latest Event (yyyy-mm-dd hh:mm def=none) :");
    eventPanel.add(labEndEvent);

    endEvent.setToolTipText("The latest origin time to allow when finding events.");
    endEvent.setMinimumSize(new java.awt.Dimension(230, 26));
    endEvent.setPreferredSize(new java.awt.Dimension(230, 26));
    eventPanel.add(endEvent);

    labMinMag.setText("Minimum Magitude (def=none) :");
    eventPanel.add(labMinMag);

    minMag.setText("2.0");
    minMag.setToolTipText("If not blink, the minimum magnitude to find for events.");
    minMag.setMinimumSize(new java.awt.Dimension(50, 26));
    minMag.setPreferredSize(new java.awt.Dimension(50, 26));
    eventPanel.add(minMag);

    labMaxMag.setText("Maximum Magnitude (def=none) :");
    eventPanel.add(labMaxMag);

    maxMag.setToolTipText("If not blank, the maximum magnitude to search for when finding events.");
    maxMag.setMinimumSize(new java.awt.Dimension(50, 26));
    maxMag.setPreferredSize(new java.awt.Dimension(50, 26));
    eventPanel.add(maxMag);

    labPhasesAllowed.setText("Phase Regex:");
    eventPanel.add(labPhasesAllowed);

    phasesAllowed.setToolTipText("<html>\nWhen getting events, limit the return to only phases that match this regular expression\n<p>\nExamples:\n<br>\n<PRE>\nP\nP|Pn|Pg|pP\nP*.\n</pre>\n</html>");
    phasesAllowed.setMinimumSize(new java.awt.Dimension(200, 26));
    phasesAllowed.setPreferredSize(new java.awt.Dimension(200, 26));
    phasesAllowed.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        phasesAllowedActionPerformed(evt);
      }
    });
    eventPanel.add(phasesAllowed);

    findEvents.setText("Find events based on Area Lat/Long and radius");
    findEvents.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        findEventsActionPerformed(evt);
      }
    });
    eventPanel.add(findEvents);

    eventsTextScrool.setMinimumSize(new java.awt.Dimension(550, 400));
    eventsTextScrool.setPreferredSize(new java.awt.Dimension(550, 400));

    eventsText.setColumns(20);
    eventsText.setRows(5);
    eventsTextScrool.setViewportView(eventsText);

    eventPanel.add(eventsTextScrool);

    tabs.addTab("Events", eventPanel);

    enterPicks.setMinimumSize(new java.awt.Dimension(575, 400));
    enterPicks.setPreferredSize(new java.awt.Dimension(575, 400));

    jScrollPane1.setMinimumSize(new java.awt.Dimension(685, 580));
    jScrollPane1.setPreferredSize(new java.awt.Dimension(685, 580));

    pickArea.setColumns(90);
    pickArea.setFont(new java.awt.Font("Monospaced", 0, 10)); // NOI18N
    pickArea.setRows(100);
    pickArea.setToolTipText("<html>\nEnter picks like for an SSD configuration file.  The order is :\n<p>\n<PRE>\nEventID orgDate orgTime Latitude Longitude Depth mag magType NN SSSS CCC LL Phase arrDate arrTime\nUS100097X6 2017-07-06 21:10:46.660 46.8551 -112.5766 11.02 2.5 ML MB ELMT EHZ .. Pg 2017-07-06 21:10:53.990\nUS100097HQ 2017-07-06 22:24:14.070 46.8695 -112.582 8.69 3.8 ML MB ELMT EHZ .. Pg 2017-07-06 22:24:22.090\n</pre>\n</html>");
    jScrollPane1.setViewportView(pickArea);

    enterPicks.add(jScrollPane1);

    parsePicks.setText("Parse Picks Text");
    parsePicks.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        parsePicksActionPerformed(evt);
      }
    });
    enterPicks.add(parsePicks);

    tabs.addTab("EnterPicks", enterPicks);
    tabs.addTab("ConfigDefaults", configPanel);

    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 4;
    add(tabs, gridBagConstraints);

    bottomStuff.setMinimumSize(new java.awt.Dimension(550, 60));

    addUpdate.setText("Add/Update");
    addUpdate.setToolTipText("Add a new Item or update and edited item.");
    addUpdate.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        addUpdateActionPerformed(evt);
      }
    });
    bottomStuff.add(addUpdate);

    labChange.setFont(new java.awt.Font("Monospaced", 0, 10)); // NOI18N
    labChange.setText("Change To:");
    bottomStuff.add(labChange);

    changeTo.setColumns(10);
    changeTo.setFont(new java.awt.Font("Monospaced", 0, 10)); // NOI18N
    changeTo.setToolTipText("To change the name of this item, put the new name here.  ");
    bottomStuff.add(changeTo);

    deleteItem.setText("Delete Item");
    deleteItem.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        deleteItemActionPerformed(evt);
      }
    });
    bottomStuff.add(deleteItem);

    reload.setText("Reload");
    reload.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        reloadActionPerformed(evt);
      }
    });
    bottomStuff.add(reload);

    idLab.setText("ID :");
    bottomStuff.add(idLab);

    ID.setBackground(new java.awt.Color(204, 204, 204));
    ID.setColumns(5);
    ID.setEditable(false);
    ID.setToolTipText("This is the ID of the displayed item in the underlying database.  Of much use to programmers, but not many others.");
    bottomStuff.add(ID);

    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 12;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(bottomStuff, gridBagConstraints);
  }// </editor-fold>//GEN-END:initComponents

  private void addUpdateActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_addUpdateActionPerformed
    // Add your handling code here:

    // Do not change
    if (initiating) {
      return;
    }
    if (chkForm()) {
      return;
    }
    try {
      String p = subspaceArea.getSelectedItem().toString();
      if (keyUpperCase) {
        p = p.toUpperCase();                  // USER: drop this to allow Mixed case
      }
      if (!obj.isNew()) {
        obj.refreshRecord();        // Refresh the result set so the sets will not fail on stale one
      }
      if (!changeTo.getText().equals("")) {
        p = changeTo.getText();
      }
      obj.setString("subspacearea", p);

      // USER: Set all of the fields in data base.  obj.setInt("thefield", ivalue);
      doAddUpdate();

      // Do not change
      obj.updateRecord();
      SubspaceArea.subspaceAreas = null;       // force reload of combo box
      getJComboBox(subspaceArea);
      clearScreen();
      subspaceArea.setSelectedIndex(-1);
    } catch (SQLException E) {
      Util.SQLErrorPrint(E, "subspaceArea: update failed partno=" + subspaceArea.getSelectedItem());
      error.setText("Could Not Update File!");
      E.printStackTrace();
      error.setBackground(UC.red);
      addUpdate.setEnabled(true);
    }
  }//GEN-LAST:event_addUpdateActionPerformed

  private void subspaceAreaActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_subspaceAreaActionPerformed
    // Add your handling code here:
    if (initiating) {
      return;
    }
    find();
    if (subspaceArea.getSelectedItem() != null) {
      if (subspaceArea.getSelectedItem() instanceof String) {
        // User is creating a new Area, force them to save it by disabling the most used options
        ((SubspaceChannelPanel) chPanel).setAreaID(0);
        ((SubspaceChannelDeletePanel) channelDeletePanel).setAreaID(0);
        findEvents.setText("Find Events disabled - save the SSD Area first");
        findEvents.setEnabled(false);
        parsePicks.setEnabled(false);
        eventTypeComboBox.setSelectedIndex(0);
        return;

      }
      // Setting the area id will allow these panels to work
      ((SubspaceChannelPanel) chPanel).setAreaID(((SubspaceArea) subspaceArea.getSelectedItem()).getID());
      ((SubspaceChannelDeletePanel) channelDeletePanel).setAreaID(((SubspaceArea) subspaceArea.getSelectedItem()).getID());
    }
    else {
      return;       // Nothing selected
    }
    buildEventJComboBox();
    chkForm();
    parsePicks.setEnabled(true);
    findEvents.setEnabled(true);
    findEvents.setText("Find Events Near " + latitudeOut + " " + longitudeOut + " within " + radiusOut + " km");
    if(stasrv == null) {
      String metaServer = Util.getProperty("metadataserver");
      try {
        stasrv = new StaSrvEdge(metaServer, 2052, null);
      }
      catch(IOException e) {
        Util.prt("IOException opening StaSrv to MDS at "+metaServer+":2052 e="+e);
      }
    }
  }//GEN-LAST:event_subspaceAreaActionPerformed

  /*
  *  Call this method to update panels with channels.
  *  It does so by calling the setAreaID for each, in the proper order.
  */
  public void updateChannelPanels() {    
    ((SubspaceChannelPanel) chPanel).setAreaID(((SubspaceArea) subspaceArea.getSelectedItem()).getID());
    ((SubspaceChannelDeletePanel) channelDeletePanel).setAreaID(((SubspaceArea) subspaceArea.getSelectedItem()).getID());    
  }
  
  public void updateChannelPanelsExceptChPanel() {
    ((SubspaceChannelDeletePanel) channelDeletePanel).setAreaID(((SubspaceArea) subspaceArea.getSelectedItem()).getID());    
  }
  
private void deleteItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_deleteItemActionPerformed
  try {
    SubspaceArea area = (SubspaceArea) subspaceArea.getSelectedItem();
    if (area == null) {
      return;  // nothing to do
    }
    int areaID = area.getID();
    Statement stmt = DBConnectionThread.getThread(DBConnectionThread.getDBSchema()).getNewStatement(true);
    String s = "DELETE FROM edge.subspaceeventchannel WHERE eventid in (SELECT id FROM edge.subspaceevent WHERE areaid =" + areaID+")";
    stmt.executeUpdate(s);
    Util.prt("SAP.delete() "+s);
    s = "DELETE FROM edge.subspaceevent where areaid=" + areaID;
    Util.prt("SAP.delete() "+s);
    stmt.executeUpdate(s);;
    s = "DELETE FROM edge.subspacechannel where areaid="+areaID;
    Util.prt("SAP.delete() "+s);
    stmt.executeUpdate(s);

    obj.deleteRecord();   // remove the area ID the normal way
    subspaceArea.removeItem(subspaceArea.getSelectedItem());
    clearScreen();
  } catch (SQLException e) {
    Util.prta("Delete record failed SQL error=" + e);
  }
}//GEN-LAST:event_deleteItemActionPerformed

private void reloadActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_reloadActionPerformed
  // put whatever jcombobox need reloading here
  //Class.getJComboBox(boxVariable);

  clearScreen();
}//GEN-LAST:event_reloadActionPerformed

  private void eventActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_eventActionPerformed
    if (event == null) {
      return;
    }
    if (initiating || buildingEvents | buildingChannels) {
      return;
    }
    SubspaceEvent ssevent = null;
    SubspaceArea area = (SubspaceArea) subspaceArea.getSelectedItem();
    if (area == null) {
      return;
    }
    int areaID = area.getID();
    if (event.getSelectedIndex() == -1) {
      if (subspaceArea.getSelectedItem() == null) {
        return;
      }
      String[] newEventIDs = ((String) event.getSelectedItem()).split("[,:\n\\s]");
      for (String newEventID : newEventIDs) {
        if (newEventID != null) {
          newEventID = newEventID.toLowerCase();
        }
        loadEventInfo(newEventID, areaID);    // This will update or create a new SubspaceEvent for the evt ID
        buildEventJComboBox();
        for (int i = 0; i < event.getItemCount(); i++) {
          ssevent = (SubspaceEvent) event.getItemAt(i);
          if (ssevent.getSubspaceEvent().equalsIgnoreCase(newEventID)) {
            event.setSelectedIndex(i);

            break;
          }
        }
      }
    } else {
      ssevent = (SubspaceEvent) event.getSelectedItem();
    }
    if (ssevent != null) {
      eventSummary.setText(ssevent.toString2());
    }
  }//GEN-LAST:event_eventActionPerformed

  private void loadEventActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_loadEventActionPerformed
    // need to use WebServices to get this evt from ComCat 
    if (event.getSelectedIndex() < 0) {
      return;      // nothing selected
    }
    SubspaceEvent ssevt = (SubspaceEvent) event.getSelectedItem();
    SubspaceArea area = (SubspaceArea) subspaceArea.getSelectedItem();
    if (area == null) {
      return;
    }
    int areaID = area.getID();
    loadEventInfo(ssevt.getSubspaceEvent().toLowerCase(), areaID);

  }//GEN-LAST:event_loadEventActionPerformed

  private void deleteEventActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_deleteEventActionPerformed
    // Need to delete this evt from the database
    if (event.getSelectedIndex() < 0) {
      return;      // No evt selected
    }
    try (Statement stmt = DBConnectionThread.getConnection(DBConnectionThread.getDBSchema()).createStatement() // used for query
            ) {
      String s = "DELETE FROM edge.subspaceeventchannel WHERE eventid=" + ((SubspaceEvent) event.getSelectedItem()).getID(); //USER: if DBSchema is not right DB, explict here
      stmt.executeUpdate(s);
      s = "DELETE FROM edge.subspaceevent where id=" + ((SubspaceEvent) event.getSelectedItem()).getID();
      stmt.executeUpdate(s);
      event.removeItemAt(event.getSelectedIndex());
    } catch (SQLException e) {
      Util.SQLErrorPrint(e, "deleting event=" + event.getSelectedItem() + "SQL failed");
    }
  }//GEN-LAST:event_deleteEventActionPerformed

  private void doPreprocessActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_doPreprocessActionPerformed
    // for the selected area write out files like SSSS.cfg with the information from the database tables
    if(chkForm()) return;
    if (subspaceArea.getSelectedIndex() < 0) {
      return;     // no area selected
    }
    StringBuilder sb = new StringBuilder(1000);
    StringBuilder state = new StringBuilder(50);
    StringBuilder bash = new StringBuilder(1000);
    SubspaceArea area = (SubspaceArea) subspaceArea.getSelectedItem();
    int areaID = area.getID();
    String s;
    String nnsssss="";
    bash.append("#!/bin/bash\n# For ").append(area.getSubspaceArea()).append(" Created by preprocessor on ").
            append(Util.ascdatetime(System.currentTimeMillis())).append("\n");
    try {     // catch Runtimes
      for (SubspaceChannel sschan : SubspaceChannel.subspaceChannels) {
        if (sschan.getAreaID() != areaID) {
          continue;
        }
        String configFile=null;
        String chan = sschan.getSubspaceChannel();
        nnsssss = chan.substring(0,7);
        try (Statement stmt = DBConnectionThread.getConnection(DBConnectionThread.getDBSchema()).createStatement()) {
          // Use the static routine to make the config, this same routine is used in the operating code for the same purpose.
          String preprocessConfigPath= area.getSubspaceArea().replaceAll(" ", "_") 
                  + Util.FS + chan.replaceAll(" ", "_").trim();
          Subspace config = ((SubspacePanel)configPanel).getConfig();
          Util.clear(sb);
          SubspaceArea.createPreprocessorConfigSB(stmt, area, sschan, svd.isSelected(), 
                  preprocessConfigPath, cwbip.getText(), cwbPortOut, config, sb, state);
          boolean ok = sb.indexOf("Skip it") < 0;
          String currentFile = preprocessConfigPath + Util.FS + chan.replaceAll(" ", "_").trim() + ".cfg";
          Util.chkFilePath(currentFile);
          File dir = new File(preprocessConfigPath);
          // Clean out all of the files in the directory
          if(dir.isDirectory()) {
            File [] files = dir.listFiles();
            for(File f : files) {
              if(f.getName().endsWith(".cfg") || f.getName().endsWith(".sac") || f.getName().endsWith(".state") || f.getName().endsWith("INVALID")) {
                f.delete();
              }
            }
          }
          
          
          if(ok) {
            Util.writeFileFromSB(currentFile, sb);
            CWBSubspacePreprocess preproc = new CWBSubspacePreprocess(currentFile, true, null);
          
            if(preproc.setConfiguration(currentFile)) {     // If it parses badly we need to flag it an continue.
              preproc.cleanUpFiles();
              preproc.buildTemplates();

            // add to the bash file
              String path = "." + Util.FS+area.getSubspaceArea().replaceAll(" ", "_") + Util.FS 
                      + chan.replaceAll(" ","_") + Util.FS;
              configFile = path  + chan.substring(0,7).replaceAll(" ","_") + ".cfg";

              // Save the state file if the user had any start/end times
              if(state.length() > 0) {
                currentFile = currentFile.replaceAll(".cfg", ".state");
                Util.writeFileFromSB(currentFile, state);
              }
              bash.append("java -cp ~/Jars/CWBPicker.jar gov.usgs.anss.picker.CWBSubspaceDetector ");
              bash.append("-path ").append(path).append(" -ID ").append(area.getID()).
                      append(" ").append(area.getResearchArgs().trim().replaceAll(";","\\\\;")).
                      append(" -c ");
              Config cfg = new Config(configFile, null);
              StationCollection station = cfg.getStatInfo();
              String nnssss = chan.substring(0,7).replaceAll(" ", "_");
              for(int i=0; i<cfg.getNumberofChannels(); i++) {
                String ch = station.getChannels()[i];
                station.getLocation();
                bash.append(nnssss).append(ch).append(station.getLocation().trim());
                if(i < cfg.getNumberofChannels()-1) {
                  bash.append(",");
                }
              } 
              //if(cfg.getCWBIP() != null) bash.append(" -h ").append(config.getCWBIP());
              bash.append(" -config ").append(configFile);
              bash.append(" >>").append(path).append(chan.replaceAll(" ","_")).append(".log &\n");

              // Now the .cfg file from the preprocessors is pointed at the SSD IP and Port for inputcwb, change to live one
              Util.clear(sb);
              Util.readFileToSB(configFile, sb);    // Read in the .cfg file and convert to string
              s = sb.toString();      
              Util.clear(sb);                       // Use this to rebuild the modified contents

              try {
                try (BufferedReader in = new BufferedReader(new StringReader(s))) {
                  String line;
                  while( (line = in.readLine()) != null) {
                    if(line.contains("inputcwb:")) {          // Is it the line needed modification
                      // yes output the modified line
                      sb.append("inputcwb: ").append(config.getCWBIP()).append(" ").append(config.getCWBPort()).append("\n");
                    }
                    else {
                      // no, just add the line
                      sb.append(line).append("\n");
                    }
                  }
                }
                // Write out the modified .cfg file
                Util.writeFileFromSB(configFile, sb);
              }
              catch(FileNotFoundException e) {
                e.printStackTrace();
              }
            }
            else {
              err.set();
              err.appendText("The Preprocessor could not parse the config file ="+currentFile+" configsize="+sb.length());
              return;
            }
          }     // Configuration file for preprocessor was ok
          else {  // the preprocessor did not configure a file
            Util.chkFilePath(currentFile);
            Util.writeFileFromSB(currentFile+"_IS_INVALID", sb);
          }
        } catch(FileNotFoundException e) {
          Util.prta("**** config file not found ="+configFile+" skipping");
        } catch (SQLException | IOException |InterruptedException e) {
          e.printStackTrace();
        }
      } // For each specified channel
      Util.writeFileFromSB(
              "." + Util.FS + area.getSubspaceArea().replaceAll(" ", "_") + Util.FS  + "run.bash", bash);
    } catch (RuntimeException | IOException e) {
      e.printStackTrace();
    }
  }//GEN-LAST:event_doPreprocessActionPerformed
  
  private void latitudeActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_latitudeActionPerformed
    chkForm();
  }//GEN-LAST:event_latitudeActionPerformed

  private void showStationsActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_showStationsActionPerformed
    chkForm();
    if (stasrv == null || latitudeOut == 0.) {
      return;   // cannot do this
    }
    ArrayList<String> edgechans = new ArrayList<>(8000);
    try {
      ResultSet rs = DBConnectionThread.getThread(DBConnectionThread.getDBSchema()).getNewStatement(false).
              executeQuery("SELECT channel FROM edge.channel WHERE lastdata > DATE_SUB(NOW(), INTERVAL 60 DAY) ORDER BY channel");
      while (rs.next()) {
        edgechans.add(rs.getString(1));
      }
    } catch (SQLException e) {
      e.printStackTrace();
    }
    try {
      String s = stasrv.getDelazc(0., stationRadiusOut / 110.567, latitudeOut, longitudeOut);
      String line;
      String lastChan = "";
      String remains="";
      StringBuilder sb = new StringBuilder(100);
      int nchan = 0;
      BufferedReader in = new BufferedReader(new StringReader(s));
      while ((line = in.readLine()) != null) {
        if (line.startsWith("* <EOR>")) {
          sb.append(lastChan);
          break;
        }     // end of the response
        if (line.startsWith(" Channel")) {
          continue;
        }
        if (line.length() < 12) {
          continue;  // Protect against short lines.
        }
        if (line.charAt(7) != 'H' && line.charAt(7) != 'B' && line.charAt(7) != 'C'
                && line.charAt(7) != 'D' && line.charAt(7) != 'S' && line.charAt(7) != 'E') {
          continue;    // not a high rate channel
        }
        String ch = line.substring(0, 12).trim();
        boolean found = false;
        for (int i = 0; i < edgechans.size(); i++) {
          if (edgechans.get(i).equals(ch)) {
            found = true;
            break;
          }
        }
        if (!found) {
          continue;
        }
        if (lastChan.equals("")) {
          lastChan = line.substring(0, 12).trim();        // First channel
          remains = line.substring(7,12);
        } else {
          if (lastChan.substring(0, 9).equals(line.substring(0, 9))) { // Same station, channel group
            remains += line.substring(7,12);
            lastChan = lastChan.substring(0, 9) + "." + lastChan.substring(10);  // put a wild card in
          } else {      // New station or channel group
            ((SubspaceChannelPanel) chPanel).createNewChannel(lastChan, remains);
            lastChan = line.substring(0,12);
            remains = line.substring(7,12);
          }
        }
      }
      if( !lastChan.equals("")) {
        ((SubspaceChannelPanel) chPanel).createNewChannel(lastChan, remains);
        lastChan = "";
        updateChannelPanelsExceptChPanel();
      }
    } catch (IOException e) {
      e.printStackTrace();
    }
  }//GEN-LAST:event_showStationsActionPerformed

  private void eventPanelFocusGained(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_eventPanelFocusGained
    chkForm();
    findEvents.setText("Find Events Near " + latitudeOut + " " + longitudeOut + " within " + radiusOut + " km");
  }//GEN-LAST:event_eventPanelFocusGained

  private void findEventsActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_findEventsActionPerformed
    ComCatEvent comcat = new ComCatEvent(null);
    if (subspaceArea.getSelectedIndex() < 0) {
      return;     // no area selected
    }
    SubspaceArea area = (SubspaceArea) subspaceArea.getSelectedItem();
    int areaID = area.getID();
    chkForm();
    comcat.setParams(null, eventStartOut, eventEndOut, latitudeOut, longitudeOut, radiusOut, minMagOut, maxMagOut);
    int nev = 0;
    try {
      comcat.executeURL();      // Get the events
      int nevents = comcat.parseQuakeML();
      for (int nn = 0; nn < nevents; nn++) {
        Event e = comcat.getEvent(nn);
        loadEventInfo((e.getEventsource() + e.getEventid()).toLowerCase(), areaID);
        Util.prta(nev + " Load event " + e.getEventsource() + e.getEventid());
        nev++;
      }

    } catch (IOException | JAXBException e) {
      e.printStackTrace();
    }
    buildEventJComboBox();
  }//GEN-LAST:event_findEventsActionPerformed

  private void eventStartActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_eventStartActionPerformed
    // TODO add your handling code here:
  }//GEN-LAST:event_eventStartActionPerformed

  private void loadAllEventsActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_loadAllEventsActionPerformed
    if (subspaceArea.getSelectedIndex() < 0) {
      return;     // no area selected
    }
    SubspaceArea area = (SubspaceArea) subspaceArea.getSelectedItem();
    int areaID = area.getID();
    for (int i = 0; i < event.getItemCount(); i++) {
      SubspaceEvent ev = (SubspaceEvent) event.getItemAt(i);
      loadEventInfo(ev.getSubspaceEvent(), areaID);
    }
  }//GEN-LAST:event_loadAllEventsActionPerformed

  private void statusActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_statusActionPerformed
    statusValue = (String) status.getSelectedItem();
    chkForm();
  }//GEN-LAST:event_statusActionPerformed

  private void phasesAllowedActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_phasesAllowedActionPerformed


  }//GEN-LAST:event_phasesAllowedActionPerformed

  private void parsePicksActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_parsePicksActionPerformed
    String text = pickArea.getText().replaceAll("  ", " ");
    text = text.replaceAll("  ", " ");
    StringBuilder channelsAdded = new StringBuilder(100);
    StringBuilder eventsAdded = new StringBuilder(100);
    StringBuilder errors = new StringBuilder();
    if(subspaceArea.getSelectedItem() == null) {
      return;
    }
    if(subspaceArea.getSelectedItem() instanceof String) {
      return;
    }
    SubspaceArea area = (SubspaceArea) subspaceArea.getSelectedItem();
    String sql="";
    if (area == null) {
      return;  // nothing to do
    }
    DBConnectionThread dbconn = DBConnectionThread.getThread(DBConnectionThread.getDBSchema());
    int areaID = area.getID();
    try {
      Statement stmt = DBConnectionThread.getConnection(DBConnectionThread.getDBSchema()).
              createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE);
      ArrayList<PickLine> picks = new ArrayList<>(20);
      try {
        try (BufferedReader in = new BufferedReader(new StringReader(text))) {
          String line;
          int nline = 0;
          while ((line = in.readLine()) != null) {
            nline++;
            try {
              if (line.length() < 50) {
                continue;                   // Short lines 
              }
              if (line.charAt(0) == '#' || line.charAt(0) == '!') {
                continue; // Commented lines
              }
              picks.add(new PickLine(line));
            } catch (RuntimeException e) {
              Util.prta(nline + " parse err=" + e + " line=" + line);
              errors.append("** ").append(nline).append(" did not parse err=").append(e).append(" line=").append(line).append("\n");
            }
          }
        }
      } catch (IOException e) {
        Util.prta("What does this mean? e=" + e);
        e.printStackTrace();
      }

      // For each pick, find an ssdevent and if none found, then create the ssdevent record
      // then create the pick record.
      SubspaceEventChannel.channelEvents = null;
      SubspaceEventChannel.makeSubspaceEventChannels();
      SubspaceEvent.events = null;
      SubspaceEvent.makeEvents();
      SubspaceChannel.subspaceChannels = null;
      SubspaceChannel.makeSubspaceChannels();
      for (PickLine pick : picks) {
        // Find this ssdevent in the SubspaceEvents or create one
        SubspaceEvent ssdevent = null;
        while (ssdevent == null) {          // we either find it, or make it up and then find it
          for (SubspaceEvent ev : SubspaceEvent.events) {
            if (pick.eventID.equalsIgnoreCase(ev.getSubspaceEvent())) {
              ssdevent = ev;
              break;
            }
            if (Math.abs(pick.originTime - ev.getOriginTime()) < 500
                    && // Does this look like the same ssdevent?
                    Math.abs(pick.latitude - ev.getLatitude()) < 0.001
                    && Math.abs(pick.longitude - ev.getLongitude()) < 0.001
                    && Math.abs(pick.depth - ev.getDepth()) < 1.) {
              if (ev.getSubspaceEvent().startsWith("zz")) {    // yes, its eventID does not match
                ssdevent = ev;
                break;
              } else {
                Util.prta("EventID " + ev.getSubspaceEvent() + " is at the same time and place as " + pick.toString() + " but IDs do not match!");
                errors.append("** EventID ").append(ev.getSubspaceEvent()).append(" is at the same time and place as ").
                        append(pick.toString()).append(" but IDs do not match!\n");
              }
            }
          }
          // If we did not identify an ssdevent, then we need to create one
          if (ssdevent == null) {
            sql = "INSERT INTO edge.subspaceevent (subspaceevent, areaID, latitude, longitude, depth, "
                    + "magnitude, magmethod, origintime,updated,created_by, created) VALUES ('"
                    + pick.eventID + "'," + areaID + "," + pick.latitude + "," + pick.longitude + "," + pick.depth + ","
                    + pick.magnitude + ",'" + pick.magType + "','" + Util.ascdatetime2(pick.originTime) + "',now(),0, now())";
            stmt.executeUpdate(sql);
            eventsAdded.append(pick.eventID).append("\n");
            if (pick.eventID.equalsIgnoreCase("?") || pick.eventID.length() <= 2) {
              int id = dbconn.getLastInsertID("subspaceevent");
              pick.eventID = "zz" + id;                     // Put it in the pick
              sql = "UPDATE edge.subspaceevent SET subspaceevent = '" + pick.eventID + "'";
              stmt.executeUpdate(sql);    // Manufacture an ssdevent ID
            }
            SubspaceEvent.events = null;
            SubspaceEvent.makeEvents();
          }
        }
        // Now add this to the subspaceeventchannel table if it is not already in there
        // Find this channel in the subspace channel table, or create it
        String channel = Util.makeSeedname(pick.net, pick.station, pick.channel, pick.location);
        SubspaceChannel ch = null;
        while (ch == null) {
          for (SubspaceChannel c : SubspaceChannel.subspaceChannels) {
            if (c.getAreaID() == areaID && c.getSubspaceChannel().equals(channel)) {
              ch = c;
              break;
            }
          }
          if (ch == null) {
            if (stasrv != null) {
              try {
                int ii = stasrv.getSACResponse(channel,
                        Util.ascdatetime(System.currentTimeMillis()).toString().replaceAll(" ", "-"),
                        "um", Util.clear(sbtmp));
              }
              catch(IOException e) {
                errors.append("** StaSrv to MDS threw and exception e=").append(e).append("\n");
                try {
                  stasrv.reopen();
                }
                catch(IOException e2) {
                  errors.append("*** StaSrv reopen failed e=").append(e).append("\n");
                }
              }
              MDSChannel mds = new MDSChannel(sbtmp.toString());
              if (mds.getLatitude() != 0. || mds.getLongitude() != 0.) {
                sql = "INSERT INTO edge.subspacechannel (subspacechannel, areaid, nscl1,nscl2,nscl3, "
                        + "rate,latitude,longitude,elevation,updated,created_by,created)"
                        + " VALUES ('" + channel + "'," + areaID + ",'" + channel + "','',''," + mds.getRate() + ","
                        + mds.getLatitude() + "," + mds.getLongitude() + "," + mds.getElevation() + ",now(),0,now())";
                stmt.executeUpdate(sql);
                channelsAdded.append(pick.net).append(" ").append(pick.station).append(" ").
                        append(pick.channel).append(" ").append(pick.location).append("\n");
              } else {
                Util.prt("**** Did not find channel coordinates! " + channel);
                errors.append("**** Did not find channel coordinates! You will have to hand enter them").append(channel).append("\n");
                sql = "INSERT INTO edge.subspacechannel (subspacechannel, areaid, nscl1,nscl2,nscl3, "
                        + "rate,latitude,longitude,elevation,updated,created_by,created)"
                        + " VALUES ('" + channel + "'," + areaID + ",'" + channel + "','','',0.,0.,0.,0.,now(),0,now())";
                stmt.executeUpdate(sql);

              }
            } else {
              Util.prt("**** No stasrv so cannot lookup channel coordinates! " + channel);
              errors.append("**** No MetaDataServer access so cannot lookup channel coordinates! Hand enter them").
                      append(channel).append("\n");
              sql =  "INSERT INTO edge.subspacechannel (subspacechannel, areaid, nscl1,nscl2,nscl3, "
                      + "rate,latitude,longitude,elevation,updated,created_by,created)"
                      + " VALUES ('" + channel + "'," + areaID + ",'" + channel + "','','',0.,0.,0.,0.,now(),0,now())";
              stmt.executeUpdate(sql);
            }
            SubspaceChannel.subspaceChannels = null;    // Get a new set of channels
            SubspaceChannel.makeSubspaceChannels();
          }
        }   // while there is no ch 
        // Now add each pick to the eventchannel table if it does not already exist!
        SubspaceEventChannel ssdevch = null;
        for (SubspaceEventChannel pk : SubspaceEventChannel.channelEvents) {
          if (pk.getChannelID() == ch.getID() && ssdevent.getID() == pk.getEventID()
                  && pk.getPhase().equalsIgnoreCase(pick.phase)
                  && Math.abs(pk.getArrivalTime().getTime() - pick.arrivalTime) < 500) {
            // This is the same pick, skip translating this one
            ssdevch = pk;
            break;
          }
        }
        if (ssdevch == null) {
          sql =  "INSERT INTO edge.subspaceeventchannel (channelid, eventid, phase, arrivaltime, disable,updated, created_by, created) VALUES ("
                  + ch.getID() + "," + ssdevent.getID() + ",'" + pick.phase + "','" + Util.ascdatetime2(pick.arrivalTime) + "',0,now(),0,now())";
          stmt.executeUpdate(sql);
        } else {
          if (Math.abs(ssdevch.getArrivalTime().getTime() - pick.arrivalTime) > 0) {
            sql = "UPDATE edge.subspaceeventchannel SET arrivaltime='" + Util.ascdatetime2(pick.arrivalTime)
                    + "' WHERE id = " + ssdevch.getID();
            stmt.executeUpdate(sql);
          }
        }
      }   // for each pick
    } catch (SQLException e) {
      Util.prt("SQL error creating picks from text e=" + e+"\nsql="+sql);
      errors.append("SQL error e=").append(e).append("\nsql=").append(sql);
      e.printStackTrace();
    }
    if (channelsAdded.length() > 0 || eventsAdded.length() > 0 || errors.length() > 0) {

      pickArea.setText(" ******************** Added items ******************\n"
              + (errors.length() > 0 ? "Errors follow :" + errors : "")
              + (channelsAdded.length() > 0 ? "These Channels were added, you might want to lock at the SSD parameters for these:\n" + channelsAdded : "")
              + (eventsAdded.length() > 0 ? "These events have been added to this SSD :\n" + eventsAdded : ""));

    } else {
      pickArea.setText("All picks converted");
    }
  }//GEN-LAST:event_parsePicksActionPerformed

  private void latitudeFocusLost(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_latitudeFocusLost
    chkForm();
  }//GEN-LAST:event_latitudeFocusLost

  private void longitudeFocusLost(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_longitudeFocusLost
    chkForm();
  }//GEN-LAST:event_longitudeFocusLost

  private void radiusFocusLost(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_radiusFocusLost
    chkForm();
  }//GEN-LAST:event_radiusFocusLost

  private void stationRadiusFocusLost(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_stationRadiusFocusLost
    chkForm();
  }//GEN-LAST:event_stationRadiusFocusLost

  private void starttimeFocusLost(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_starttimeFocusLost
    chkForm();
  }//GEN-LAST:event_starttimeFocusLost

  private void endtimeFocusLost(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_endtimeFocusLost
    chkForm();
  }//GEN-LAST:event_endtimeFocusLost

  private void cwbipFocusLost(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_cwbipFocusLost
    chkForm();
  }//GEN-LAST:event_cwbipFocusLost

  private void cwbPortFocusLost(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_cwbPortFocusLost
    chkForm();
  }//GEN-LAST:event_cwbPortFocusLost

  private void eventTypeComboBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_eventTypeComboBoxActionPerformed
    // TODO add your handling code here:
  }//GEN-LAST:event_eventTypeComboBoxActionPerformed


  // Variables declaration - do not modify//GEN-BEGIN:variables
  private javax.swing.JTextField ID;
  private javax.swing.JButton addUpdate;
  private javax.swing.JPanel areaStuff;
  private javax.swing.JLabel argsLab;
  private javax.swing.JPanel bottomStuff;
  private javax.swing.JPanel chPanel;
  private javax.swing.JTextField changeTo;
  private javax.swing.JPanel channelDeletePanel;
  private javax.swing.JPanel configPanel;
  private javax.swing.JTextField cwbPort;
  private javax.swing.JTextField cwbip;
  private javax.swing.JButton deleteEvent;
  private javax.swing.JButton deleteItem;
  private javax.swing.JButton doPreprocess;
  private javax.swing.JTextField endEvent;
  private javax.swing.JTextField endtime;
  private javax.swing.JPanel enterPicks;
  private javax.swing.JTextField error;
  private javax.swing.JComboBox event;
  private javax.swing.JPanel eventPanel;
  private javax.swing.JTextField eventStart;
  private javax.swing.JTextField eventSummary;
  private javax.swing.JComboBox<String> eventTypeComboBox;
  private javax.swing.JTextArea eventsText;
  private javax.swing.JScrollPane eventsTextScrool;
  private javax.swing.JButton findEvents;
  private javax.swing.JPanel fullWidthStuff;
  private javax.swing.JLabel idLab;
  private javax.swing.JScrollPane jScrollPane1;
  private javax.swing.JSeparator jSeparator1;
  private javax.swing.JSeparator jSeparator3;
  private javax.swing.JLabel labCWBIP;
  private javax.swing.JLabel labCWBport;
  private javax.swing.JLabel labChange;
  private javax.swing.JLabel labEnd;
  private javax.swing.JLabel labEndEvent;
  private javax.swing.JLabel labEvent;
  private javax.swing.JLabel labLat;
  private javax.swing.JLabel labMain;
  private javax.swing.JLabel labMaxMag;
  private javax.swing.JLabel labMinMag;
  private javax.swing.JLabel labPhasesAllowed;
  private javax.swing.JLabel labRadius;
  private javax.swing.JLabel labResearch;
  private javax.swing.JLabel labStart;
  private javax.swing.JLabel labStartEvent;
  private javax.swing.JLabel labStatRadus;
  private javax.swing.JLabel latLong;
  private javax.swing.JTextField latitude;
  private javax.swing.JButton loadAllEvents;
  private javax.swing.JButton loadEvent;
  private javax.swing.JTextField longitude;
  private javax.swing.JTextField maxMag;
  private javax.swing.JTextField minMag;
  private javax.swing.JTextField opsArgs;
  private javax.swing.JLabel opsLab;
  private javax.swing.JPanel overalPanel;
  private javax.swing.JButton parsePicks;
  private javax.swing.JTextField phasesAllowed;
  private javax.swing.JTextArea pickArea;
  private javax.swing.JTextField radius;
  private javax.swing.JButton reload;
  private javax.swing.JTextField researchArgs;
  private javax.swing.JButton showStations;
  private javax.swing.JTextField starttime;
  private javax.swing.JTextField stationRadius;
  private javax.swing.JComboBox<String> status;
  private javax.swing.JComboBox subspaceArea;
  private javax.swing.JCheckBox svd;
  private javax.swing.JTabbedPane tabs;
  // End of variables declaration//GEN-END:variables
  /**
   * Creates new form SubspaceAreaPanel
   */
  public SubspaceAreaPanel() {
    initiating = true;
    initComponents();
    getJComboBox(subspaceArea);                // set up the key JComboBox
    subspaceArea.setSelectedIndex(-1);    // Set selected type
    SSDEventTypePanel.getJComboBox(eventTypeComboBox);
    eventTypeComboBox.setSelectedIndex(0);
    initiating = false;

    // USER:This is a good place for UC.Look(components) to get rid of the ugly Grey
    doInit();
    clearScreen();                    // Start with empty screen
    String metaServer = Util.getProperty("metadataserver");
    if(metaServer == null) metaServer="cwbpub.cr.usgs.gov";
    try {
      stasrv = new StaSrvEdge(metaServer, 2052, null);
    }
    catch(IOException e) {
      Util.prt("IOException opening StaSrv to MDS at "+metaServer+":2052 e="+e);
    }
    //channelPanel = new SubspaceChannelPanel();
    //channelPanelScroll.add(channelPanel);
    //channelPanelScroll.setViewportView(channelPanel);  
  }

  //////////////////////////////////////////////////////////////////////////////
  // No USER: changes needed below here!
  //////////////////////////////////////////////////////////////////////////////
  /**
   * Create a JComboBox of all of the items in the table represented by this panel
   *
   * @return A New JComboBox filled with all row keys from the table
   */
  public static JComboBox getJComboBox() {
    JComboBox b = new JComboBox();
    getJComboBox(b);
    return b;
  }

  /**
   * Update a JComboBox to represent the rows represented by this panel
   *
   * @param b The JComboBox to Update
   */
  public static void getJComboBox(JComboBox b) {
    b.removeAllItems();
    makeSubspaceAreas();
    for (SubspaceArea area : SubspaceArea.subspaceAreas) {
      b.addItem(area);
    }

    b.setMaximumRowCount(30);
  }

  /**
   * Given a JComboBox from getJComboBox, set selected item to match database ID
   *
   * @param b The JComboBox
   * @param ID the row ID from the database to select
   */
  public static void setJComboBoxToID(JComboBox b, int ID) {
    b.setSelectedIndex(-1);
    //Util.prt("SubspaceAreaPanel.setJComboBoxToID id="+ID);
    for (int i = 0; i < b.getItemCount(); i++) {
      if (((SubspaceArea) b.getItemAt(i)).getID() == ID) {
        b.setSelectedIndex(i);
        //Util.prt("SubspaceAreaPanel.setJComboBoxToID id="+ID+" found at "+i);
      }
    }
  }

  /**
   * Given a database ID find the index into a JComboBox consistent with this panel
   *
   * @param ID The row ID from the database
   * @return The index in the JComboBox with this ID
   */
  public static int getJComboBoxIndex(int ID) {
    if (SubspaceArea.subspaceAreas == null) {
      makeSubspaceAreas();
    }
    for (int i = 0; i < SubspaceArea.subspaceAreas.size(); i++) {
      if (ID == SubspaceArea.subspaceAreas.get(i).getID()) {
        return i;
      }
    }
    return -1;
  }

  /**
   * Cause the main JComboBox to be reloaded
   */
  public static void reload() {
    SubspaceArea.subspaceAreas = null;
    makeSubspaceAreas();
  }

  /* return a ArrayList with all of the SubspaceArea
   * @return The ArrayList with the subspaceArea
   */
  public static ArrayList<SubspaceArea> getSubspaceAreaVector() {
    if (SubspaceArea.subspaceAreas == null) {
      makeSubspaceAreas();
    }
    return SubspaceArea.subspaceAreas;
  }

  /* return a ArrayList with all of the SubspaceArea
   * @return The ArrayList with the subspaceArea
   */
  public static ArrayList<SubspaceArea> getSubspaceAreaArrayList() {
    if (SubspaceArea.subspaceAreas == null) {
      makeSubspaceAreas();
    }
    return SubspaceArea.subspaceAreas;
  }

  /**
   * Get the item corresponding to database ID
   *
   * @param ID the database Row ID
   * @return The SubspaceArea row with this ID
   */
  public static SubspaceArea getSubspaceAreaWithID(int ID) {
    if (SubspaceArea.subspaceAreas == null) {
      makeSubspaceAreas();
    }
    int i = getJComboBoxIndex(ID);
    if (i >= 0) {
      return SubspaceArea.subspaceAreas.get(i);
    } else {
      return null;
    }
  }

  // This routine should only need tweeking if key field is not same as table name
  private static void makeSubspaceAreas() {
    if (SubspaceArea.subspaceAreas != null) {
      return;
    }
    SubspaceArea.makeSubspaceAreas();

  }

  // No changes needed
  private void find() {
    if (subspaceArea == null) {
      return;
    }
    if (initiating) {
      return;
    }
    SubspaceArea l;
    if (subspaceArea.getSelectedIndex() == -1) {
      if (subspaceArea.getSelectedItem() == null) {
        return;
      }
      l = newOne();
    } else {
      l = (SubspaceArea) subspaceArea.getSelectedItem();
    }

    if (l == null) {
      return;
    }
    String p = l.getSubspaceArea();
    if (p == null) {
      return;
    }
    if (p.equals("")) {
      addUpdate.setEnabled(false);
      deleteItem.setEnabled(false);
      addUpdate.setText("Enter a SubspaceArea!");
    }
    p = p.toUpperCase();
    error.setBackground(Color.lightGray);
    error.setText("");
    try {
      clearScreen();          // set screen to known state
      oldOne(p);

      // set add/Update button to indicate an update will happen
      if (obj.isNew()) {
        clearScreen();
        ID.setText("NEW!");

        // Set up for "NEW" - clear fields etc.
        error.setText("NOT found - assume NEW");
        error.setBackground(UC.yellow);
        addUpdate.setText("Add " + p);
        addUpdate.setEnabled(true);
        deleteItem.setEnabled(false);
      } else {
        addUpdate.setText("Update " + p);
        addUpdate.setEnabled(true);
        addUpdate.setEnabled(true);
        deleteItem.setEnabled(true);
        ID.setText("" + obj.getInt("ID"));    // Info only, show ID
      }
    } // This is only thrown if something happens in oldone() new DBOject
    // Other than it being a new record.
    catch (SQLException E) // 
    {
      Util.SQLErrorPrint(E, "SubspaceArea: SQL error getting SubspaceArea=" + p);
    }

  }

  /**
   * This main displays the form Pane by itself
   *
   * @param args command line args ignored
   */
  public static void main(String args[]) {
    DBConnectionThread jcjbl;
    Util.init(UC.getPropertyFilename());
    UC.init();
    Util.setModeGMT();
    try {
      // Make test DBconnection for form
      jcjbl = new DBConnectionThread(DBConnectionThread.getDBServer(), DBConnectionThread.getDBCatalog(),
              UC.defaultUser(), UC.defaultPassword(), true, false, DBConnectionThread.getDBSchema(), DBConnectionThread.getDBVendor());
      if (!DBConnectionThread.waitForConnection(DBConnectionThread.getDBSchema())) {
        if (!DBConnectionThread.waitForConnection(DBConnectionThread.getDBSchema())) {
          if (!DBConnectionThread.waitForConnection(DBConnectionThread.getDBSchema())) {
            Util.prt("COuld not connect to DB " + jcjbl);
            System.exit(1);
          }
        }
      }
      Show.inFrame(new SubspaceAreaPanel(), UC.XSIZE, UC.YSIZE);
    } catch (InstantiationException e) {
    }

  }

  private final class PickLine {

    String eventID, net, station, channel, location, phase, magType;
    double latitude, longitude, depth, magnitude;
    long originTime, arrivalTime;

    @Override
    public String toString() {
      return eventID + " " + Util.ascdatetime2(originTime) + " "
              + latitude + " " + longitude + " " + depth + " " + magnitude + " " + magType + " "
              + net + " " + station + " " + channel + " " + location + " " + phase + " " + 
              Util.ascdatetime2(arrivalTime) + " df=" + (arrivalTime - originTime);
    }

    public PickLine(String line) {
      String[] parts = line.split("\\s");
      if (parts.length < 15) {
        throw new IllegalArgumentException("Line does not have 15 parts line=" + line);
      }
      eventID = parts[0].toLowerCase();
      originTime = Util.stringToDate2(parts[1] + " " + parts[2]).getTime();
      latitude = Double.parseDouble(parts[3]);
      longitude = Double.parseDouble(parts[4]);
      depth = Double.parseDouble(parts[5]);
      magnitude = Double.parseDouble(parts[6]);
      magType = parts[7];
      net = parts[8];
      station = parts[9];
      channel = parts[10];
      location = parts[11];
      phase = parts[12];
      arrivalTime = Util.stringToDate2(parts[13] + " " + parts[14]).getTime();
    }
  }
}
