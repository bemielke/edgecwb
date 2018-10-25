package gov.usgs.edge.config;

/*
 * Copyright 2012, Incorporated Research Institutions for Seismology (IRIS) or
 * third-party contributors as indicated by the @author tags.
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */
import gov.usgs.adslserverdb.MDSChannel;
import gov.usgs.anss.db.DBConnectionThread;
import gov.usgs.anss.db.DBObject;
import gov.usgs.anss.util.ErrorTrack;
import gov.usgs.anss.util.FUtil;
import gov.usgs.anss.util.Show;
import gov.usgs.anss.util.UC;
import gov.usgs.anss.util.Util;
import gov.usgs.anss.util.StaSrv;
import subspacedetector.Config;
import subspacedetector.StationCollection;
import gov.usgs.anss.picker.CWBSubspaceDetector;
import java.awt.Color;
import java.awt.Dimension;
import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import javax.swing.JComboBox;
import javax.swing.JLabel;

/**
 *
 * requirements : The key for the file must be the name of the table and of the JComboBox variable
 * this variable is called "subspaceChannel" in the initial form The table name and key name should
 * match and start lower case (subspaceChannel). The Class here will be that same name but with
 * upper case at beginning(SubspaceChannel).
 * <br> 1) Rename the location JComboBox to the "key" (subspaceChannel) value. This should start
 * Lower case.
 * <br> 2) The table name should match this key name and be all lower case.
 * <br> 3) Change all SubspaceChannel to ClassName of underlying data (case sensitive!)
 * <br> 4) Change all subspaceChannel key value (the JComboBox above)
 * <br> 5) clearScreen should update swing components to new defaults
 * <br> 6) chkForm should check all field for validity. The errorTrack variable is used to track
 * multiple errors. In FUtil.chk* handle checks of the various variable types. Date, double, and IP
 * etc return the "canonical" form for the variable. These should be stored in a class variable so
 * the latest one from "chkForm()" are available to the update writer.
 * <br> 7) newone() should be updated to create a new instance of the underlying data class.
 * <br> 8) oldone() get data from database and update all swing elements to correct values
 * <br> 9) addUpdateAction() add data base set*() operations updating all fields in the DB
 * <br> 10) SubspaceChannelPanel() constructor - good place to change backgrounds using UC.Look()
 * any other weird startup stuff.
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
public class SubspaceChannelPanel extends javax.swing.JPanel {

  //USER: set this for the upper case conversion of keys.
  private final boolean keyUpperCase = false;    // set this true if all keys must be upper case
  //USER: Here are the local variables
  double hpcorner, lpcorner, detThresh, pre, templateDur, averageDur;
  double completenessVal;
  int np;
  StringBuilder sbtmp = new StringBuilder(1000);
  StaSrv stasrv = null;
  int areaID;
  SubspaceAreaPanel areaPanel;
  String remains;

  // NOTE : here define all variables general.  "ArrayList v" is used for main Comboboz
  private void doInit() {
    // Good place to add UC.Looks()
    deleteItem.setVisible(true);
    reload.setVisible(false);
    idLab.setVisible(true);
    ID.setVisible(true);
    changeTo.setVisible(true);
    labChange.setVisible(true);
    UC.Look(topPanel);
    UC.Look(this);                    // Set color background
    UC.Look(eventChannelPanel);

  }

  private void doAddUpdate(String p) throws SQLException {
    // USER: Set all of the fields in data base.  obj.setInt("thefield", ivalue);
    obj.setInt("areaid", areaID);
    String nscl1 = null, nscl2 = null, nscl3 = null;
    try {
      try (Statement stmt = DBConnectionThread.getConnection(DBConnectionThread.getDBSchema()).
              createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE)// used for query
              ) {
        if (remains != null) {
          for (int i = 0; i < remains.length(); i = i + 5) {
            String ch = remains.substring(i, i + 5);
            switch (ch.charAt(2)) {
              case 'Z':
                nscl1 = p.substring(0, 7) + ch;
                break;
              case 'N':
              case '1':
                nscl2 = p.substring(0, 7) + ch;
                break;
              case 'E':
              case '2':
                nscl3 = p.substring(0, 7) + ch;
                break;
              default:
                Util.prta("*** Did not find channel component for chan=" + ch + " for " + p);
                break;
            }
          }
          remains = null;
        } else {
          String s = "SELECT * FROM edge.channel where channel regexp '" + p + "' ORDER BY channel desc;";
          try (ResultSet rs = stmt.executeQuery(s)) {
            while (rs.next()) {
              if (nscl1 == null) {
                nscl1 = rs.getString("channel");
              } else if (nscl2 == null) {
                nscl2 = rs.getString("channel");
              } else if (nscl3 == null) {
                nscl3 = rs.getString("channel");
              } else {
                String c = rs.getString("channel");
                if (nscl2.charAt(9) == 'N' && c.charAt(9) == '1') {
                  nscl2 = c;
                }
                if (nscl3.charAt(9) == 'E' && c.charAt(9) == '2') {
                  nscl3 = c;
                }
              }
            }
          }
        }
        if (nscl1 == null) {
          Util.prta("***** channel " + p + " matches no channels in the edge.channel table!");
          nscl1 = p.substring(0, 9) + "Z" + p.substring(10).trim();
        }
      }
    } catch (SQLException e) {
      Util.SQLErrorPrint(e, "SubspaceChannelPanel doAddUpdate() on table SQL failed");
    }
    obj.setString("nscl1", nscl1);
    if (nscl1.contains(".")) {
      Util.prt("NSCL1 contains period " + nscl1);
    }
    if (nscl2 != null) {
      obj.setString("nscl2", nscl2);
      if (nscl2.contains(".")) {
        Util.prt("NSCL2 contains period " + nscl2);
      }
    }
    if (nscl3 != null) {
      obj.setString("nscl3", nscl3);
      if (nscl1.contains(".")) {
        Util.prt("NSCL3 contains period " + nscl3);
      }
    }
    if (nscl1.contains(".")) {
      Util.prta("NSCL1 has period");
    }
    if (stasrv != null) {
      int ii = stasrv.getSACResponse(nscl1,
              Util.ascdatetime(System.currentTimeMillis()).toString().replaceAll(" ", "-"),
              "um", Util.clear(sbtmp));
      MDSChannel mds = new MDSChannel(sbtmp.toString());
      if (mds.getLatitude() != 0. || mds.getLongitude() != 0.) {
        obj.setDouble("latitude", mds.getLatitude());
        obj.setDouble("longitude", mds.getLongitude());
        obj.setDouble("elevation", mds.getElevation());
        obj.setDouble("rate", mds.getRate());
      }
    }
    obj.setDouble("hpcorner", hpcorner);
    obj.setDouble("lpcorner", lpcorner);
    obj.setInt("npoles", np);
    obj.setDouble("detectionthreshold", detThresh);
    obj.setDouble("templateduration", templateDur);
    obj.setString("detectionthresholdtype", "" + detectionThresholdType.getSelectedItem());
    obj.setDouble("averagingduration", averageDur);
    obj.setDouble("completeness", completenessVal);
    obj.setDouble("preevent", pre);

    // Update the disable in SubspaceEventChannelPanel
    Statement stmt = DBConnectionThread.getThread(DBConnectionThread.getDBSchema()).getNewStatement(true);
    for (int i = 0; i < eventChannelPanel.getComponentCount(); i++) {
      Object obj1 = eventChannelPanel.getComponent(i);
      if (obj1 instanceof SubspaceEventChannelPanel) {
        SubspaceEventChannelPanel cp = (SubspaceEventChannelPanel) eventChannelPanel.getComponent(i);
        cp.updateRecord(stmt);
      } else {
        Util.prt("Channel pannel contains a non-panel object obj=" + obj1);
      }
    }
  }

  private void doOldOne() throws SQLException {
    //USER: Here set all of the form fields to data from the DBObject
    //      subspaceChannel.setText(obj.getString("SubspaceChannel"));
    // Example : description.setText(obj.getString("description"));

  }

  public ErrorTrack getErrorTrack() {
    return err;
  }

  /**
   *
   * @return The currently selected SubspaceChannel
   */
  public SubspaceChannel getSelectedSubspaceChannel() {
    return (SubspaceChannel) subspaceChannel.getSelectedItem();
  }

  public void setAreaID(int areaID) {
    this.areaID = areaID;
    clearScreen();
    getJComboBox(subspaceChannel, areaID);
  }

  // This routine must validate all fields on the form.  The err (ErrorTrack) variable
  // is used by the FUtil to verify fields.  Use FUTil or custom code here for verifications
  protected boolean chkForm() {
    // Do not change
    err.reset();
    UC.Look(error);
    if (!SubspaceAreaPanel.isEditable()) {
      err.set();
      err.appendText("Area is not editable status");
    }

    //USER:  Your error checking code goes here setting the local variables to valid settings
    // note : Use FUtil.chk* to check dates, timestamps, IP addresses, etc and put them in
    // canonical form.  store in a class variable.  For instance :
    //   sdate = FUtil.chkDate(dateTextField, err);
    // Any errors found should err.set(true);  and err.append("error message");
    hpcorner = FUtil.chkDouble(hpcorn, err, 0.1, 10.);
    lpcorner = FUtil.chkDouble(lpcorn, err, 0.1, 10.);
    np = FUtil.chkInt(npoles, err, 0, 5);
    detThresh = FUtil.chkDouble(detectionThreshold, err, 0., 1.);
    pre = FUtil.chkDouble(preevent, err, -0.5, 30.);
    templateDur = FUtil.chkDouble(templateDuration, err, 3., 600.);
    averageDur = FUtil.chkDouble(averagingDuration, err, 0.5, 1000.);
    completenessVal = FUtil.chkDouble(completeness, err);
    if (completenessVal < 0.5) {
      completeness.setText("0.9");
    }
    completenessVal = FUtil.chkDouble(completeness, err, 0.5, 0.96, true);
    int nref = 0;
    for (int i = 0; i < eventChannelPanel.getComponentCount(); i++) {
      SubspaceEventChannel sp = ((SubspaceEventChannelPanel) eventChannelPanel.getComponent(i)).getSubspaceEventChannel();
      if (sp.isReference()) {
        nref++;
      }
    }
    if (nref > 1) {
      err.set();
      err.appendText("Mult ref events");
    }

    // No CHANGES : If we found an error, color up error box
    if (err.isSet()) {
      error.setText(err.getText());
      error.setBackground(UC.red);
    } else {
      addUpdate.setEnabled(true);
    }
    return err.isSet();
  }

  // This routine must set the form to initial state.  It does not update the JCombobox
  protected final void clearScreen() {

    // Do not change
    ID.setText("");
    UC.Look(error);
    UC.Look(bottomStuff);
    error.setText("");
    addUpdate.setEnabled(false);
    addUpdate.setText("Enter a SubspaceChannel");
    deleteItem.setEnabled(false);
    changeTo.setText("");
    //USER: Clear all fields for this form, this sets "defaults" for new screen
    // description.setText("");    
    hpcorn.setText("1.");
    lpcorn.setText("4.");
    npoles.setText("3");
    filterType.setSelectedIndex(0);
    preevent.setText("0.");
    detectionThreshold.setText(".6");
    detectionThresholdType.setSelectedIndex(0);
    templateDuration.setText("30.");
    averagingDuration.setText("5.");
    completeness.setText("0.9");
    eventChannelPanel.removeAll();
    eventChannelPanel.invalidate();
    eventChannelPanel.validate();
    eventChannelPanel.repaint();
    if(areaID <= 0) {
      subspaceChannel.setEnabled(false);
    }
    else {
      subspaceChannel.setEnabled(true);
    }
  }

  private SubspaceChannel newOne() {

    return new SubspaceChannel(0, ((String) subspaceChannel.getSelectedItem()).toUpperCase(),
             0, "", "", "", 0., 0., 0., 0, "", 0., "", 0., 0., 0., 0., 0., 0., 0.);

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
            "subspacechannel", "subspaceChannel", p, "areaID",areaID);                     // table name and field name are usually the same

    if (obj.isNew()) {
      //Util.prt("object is new"+p);
    } else {
      //USER: Here set all of the form fields to data from the DBObject
      //      subspaceChannel.setText(obj.getString("SubspaceChannel"));
      // Example : description.setText(obj.getString("description"));
      hpcorn.setText(obj.getDouble("hpcorner") + "");
      lpcorn.setText(obj.getDouble("lpcorner") + "");
      npoles.setText(obj.getInt("npoles") + "");
      for (int i = 0; i < filterType.getItemCount(); i++) {
        if (obj.getString("filtertype").equalsIgnoreCase(filterType.getItemAt(i))) {
          filterType.setSelectedItem(i);
          break;
        }
      }
      preevent.setText(obj.getDouble("preevent") + "");
      detectionThreshold.setText(obj.getDouble("detectionthreshold") + "");
      for (int i = 0; i < detectionThresholdType.getItemCount(); i++) {
        if (obj.getString("filtertype").equalsIgnoreCase(detectionThresholdType.getItemAt(i))) {
          filterType.setSelectedItem(i);
          break;
        }
      }
      templateDuration.setText(obj.getDouble("templateDuration") + "");
      averagingDuration.setText(obj.getDouble("averagingDuration") + "");
      completeness.setText(obj.getDouble("completeness") + "");
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

    gridBagLayout1 = new java.awt.GridBagLayout();
    error = new javax.swing.JTextField();
    labMain = new javax.swing.JLabel();
    subspaceChannel = getJComboBox(0);
    topPanel = new javax.swing.JPanel();
    labHP = new javax.swing.JLabel();
    hpcorn = new javax.swing.JTextField();
    lsbLP = new javax.swing.JLabel();
    lpcorn = new javax.swing.JTextField();
    labNPoles = new javax.swing.JLabel();
    npoles = new javax.swing.JTextField();
    labFilterType = new javax.swing.JLabel();
    filterType = new javax.swing.JComboBox<>();
    labPreevent = new javax.swing.JLabel();
    preevent = new javax.swing.JTextField();
    labDThres = new javax.swing.JLabel();
    detectionThreshold = new javax.swing.JTextField();
    labDetThresType = new javax.swing.JLabel();
    detectionThresholdType = new javax.swing.JComboBox<>();
    labTemplateDuration = new javax.swing.JLabel();
    templateDuration = new javax.swing.JTextField();
    labAverageDur = new javax.swing.JLabel();
    averagingDuration = new javax.swing.JTextField();
    completeness = new javax.swing.JTextField();
    labCompleteness = new javax.swing.JLabel();
    eventChannelScroll = new javax.swing.JScrollPane();
    eventChannelPanel = new javax.swing.JPanel();
    bottomStuff = new javax.swing.JPanel();
    addUpdate = new javax.swing.JButton();
    labChange = new javax.swing.JLabel();
    changeTo = new javax.swing.JTextField();
    deleteItem = new javax.swing.JButton();
    reload = new javax.swing.JButton();
    idLab = new javax.swing.JLabel();
    ID = new javax.swing.JTextField();

    setMinimumSize(new java.awt.Dimension(500, 300));
    setPreferredSize(new java.awt.Dimension(500, 300));

    error.setEditable(false);
    error.setColumns(40);
    error.setMinimumSize(new java.awt.Dimension(650, 22));
    error.setPreferredSize(new java.awt.Dimension(650, 26));
    add(error);

    labMain.setText("Select or New NNSSSSSCCLL  :");
    labMain.setToolTipText("This button commits either a new item or the changes for an updating item.  Its label with change to reflect that.");
    add(labMain);

    subspaceChannel.setEditable(true);
    subspaceChannel.setFont(new java.awt.Font("Monospaced", 0, 13)); // NOI18N
    subspaceChannel.setMaximumRowCount(40);
    subspaceChannel.setToolTipText("Select an item to edit in the JComboBox or type in the name of an item.   If the typed name does not match an existing item, an new item is assumed.");
    subspaceChannel.setMinimumSize(new java.awt.Dimension(175, 26));
    subspaceChannel.setPreferredSize(new java.awt.Dimension(175, 26));
    subspaceChannel.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        subspaceChannelActionPerformed(evt);
      }
    });
    add(subspaceChannel);

    topPanel.setMinimumSize(new java.awt.Dimension(600, 150));
    topPanel.setPreferredSize(new java.awt.Dimension(600, 150));
    topPanel.setLayout(new java.awt.GridBagLayout());

    labHP.setText("HighPass(Hz):");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_END;
    topPanel.add(labHP, gridBagConstraints);

    hpcorn.setMinimumSize(new java.awt.Dimension(50, 26));
    hpcorn.setPreferredSize(new java.awt.Dimension(50, 26));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
    topPanel.add(hpcorn, gridBagConstraints);

    lsbLP.setText("LowPass(Hz):");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_END;
    topPanel.add(lsbLP, gridBagConstraints);

    lpcorn.setMinimumSize(new java.awt.Dimension(50, 26));
    lpcorn.setPreferredSize(new java.awt.Dimension(50, 26));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
    topPanel.add(lpcorn, gridBagConstraints);

    labNPoles.setText("Npoles:");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 2;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_END;
    topPanel.add(labNPoles, gridBagConstraints);

    npoles.setMinimumSize(new java.awt.Dimension(20, 26));
    npoles.setPreferredSize(new java.awt.Dimension(20, 26));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 2;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
    topPanel.add(npoles, gridBagConstraints);

    labFilterType.setText("FilterType:");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 3;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_END;
    topPanel.add(labFilterType, gridBagConstraints);

    filterType.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "Bandpass", "Highpass", "Lowpass" }));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 3;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
    topPanel.add(filterType, gridBagConstraints);

    labPreevent.setText("PreEvent(sec):");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 2;
    gridBagConstraints.gridy = 3;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_END;
    topPanel.add(labPreevent, gridBagConstraints);

    preevent.setToolTipText("");
    preevent.setMinimumSize(new java.awt.Dimension(50, 26));
    preevent.setPreferredSize(new java.awt.Dimension(50, 26));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 3;
    gridBagConstraints.gridy = 3;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
    topPanel.add(preevent, gridBagConstraints);

    labDThres.setText("DetectionThreshold:");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 2;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_END;
    topPanel.add(labDThres, gridBagConstraints);

    detectionThreshold.setMinimumSize(new java.awt.Dimension(50, 26));
    detectionThreshold.setPreferredSize(new java.awt.Dimension(50, 26));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 3;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
    topPanel.add(detectionThreshold, gridBagConstraints);

    labDetThresType.setText("DetectionThresholdType:");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 2;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_END;
    topPanel.add(labDetThresType, gridBagConstraints);

    detectionThresholdType.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "constant","empirical" }));
    detectionThresholdType.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        detectionThresholdTypeActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 3;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
    topPanel.add(detectionThresholdType, gridBagConstraints);

    labTemplateDuration.setText("TemplateDuration(sec):");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 2;
    gridBagConstraints.gridy = 2;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_END;
    topPanel.add(labTemplateDuration, gridBagConstraints);

    templateDuration.setMinimumSize(new java.awt.Dimension(50, 26));
    templateDuration.setPreferredSize(new java.awt.Dimension(50, 26));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 3;
    gridBagConstraints.gridy = 2;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
    topPanel.add(templateDuration, gridBagConstraints);

    labAverageDur.setText("AveragingDuration:");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 2;
    gridBagConstraints.gridy = 4;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_END;
    topPanel.add(labAverageDur, gridBagConstraints);

    averagingDuration.setMinimumSize(new java.awt.Dimension(50, 26));
    averagingDuration.setPreferredSize(new java.awt.Dimension(50, 26));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 3;
    gridBagConstraints.gridy = 4;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
    topPanel.add(averagingDuration, gridBagConstraints);

    completeness.setPreferredSize(new java.awt.Dimension(100, 26));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 4;
    topPanel.add(completeness, gridBagConstraints);

    labCompleteness.setText("Completeness:");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 4;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_END;
    topPanel.add(labCompleteness, gridBagConstraints);

    add(topPanel);

    eventChannelScroll.setBorder(javax.swing.BorderFactory.createBevelBorder(javax.swing.border.BevelBorder.RAISED));
    eventChannelScroll.setMinimumSize(new java.awt.Dimension(675, 350));
    eventChannelScroll.setPreferredSize(new java.awt.Dimension(675, 350));
    eventChannelScroll.setViewportView(eventChannelPanel);

    add(eventChannelScroll);

    bottomStuff.setMinimumSize(new java.awt.Dimension(550, 60));

    addUpdate.setText("Add/Update Channel");
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

    ID.setEditable(false);
    ID.setBackground(new java.awt.Color(204, 204, 204));
    ID.setColumns(5);
    ID.setToolTipText("This is the ID of the displayed item in the underlying database.  Of much use to programmers, but not many others.");
    bottomStuff.add(ID);

    add(bottomStuff);
  }// </editor-fold>//GEN-END:initComponents

  private void addUpdateActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_addUpdateActionPerformed
    // Do not change
    if (initiating) {
      return;
    }
    if (chkForm()) {
      return;
    }
    try {
      String p = subspaceChannel.getSelectedItem().toString();
      if (p.equals("")) {
        return;
      }
      if (keyUpperCase) {
        p = p.toUpperCase();                  // USER: drop this to allow Mixed case
      }
      if (!obj.isNew()) {
        obj.refreshRecord();        // Refresh the result set so the sets will not fail on stale one
      }
      if (!changeTo.getText().equals("")) {
        p = changeTo.getText();
      }
      obj.setString("subspaceChannel", (p + "            ").substring(0, 12).trim());

      // USER: Set all of the fields in data base.  obj.setInt("thefield", ivalue);
      doAddUpdate((p + "            ").substring(0, 12).trim());

      // Do not change
      obj.updateRecord();
      SubspaceChannel.subspaceChannels = null;       // force reload of combo box
      getJComboBox(subspaceChannel, areaID);
      clearScreen();
      subspaceChannel.setSelectedIndex(-1);
    } catch (SQLException E) {
      Util.SQLErrorPrint(E, "subspaceChannel: update failed partno=" + subspaceChannel.getSelectedItem());
      error.setText("Could Not Update File!");
      error.setBackground(UC.red);
      addUpdate.setEnabled(true);
    }
  }//GEN-LAST:event_addUpdateActionPerformed

  private void subspaceChannelActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_subspaceChannelActionPerformed
    if (initiating) {
      return;
    }
    SubspaceChannel sschannel = null;
    if (areaID <= 0) {
      return;
    }
    if (subspaceChannel.getSelectedIndex() == -1) {
      if (subspaceChannel.getSelectedItem() == null) {
        return;
      }
      String[] newChannels = ((String) subspaceChannel.getSelectedItem()).split("[,:;\t\n]");

      for (String newChannel : newChannels) {
        subspaceChannel.setSelectedItem(newChannel);
        find();
        addUpdate.doClick();
      }
    } else {
      sschannel = (SubspaceChannel) subspaceChannel.getSelectedItem();
    }
    if (sschannel != null) {

    }
    find();

    if (areaPanel.getEventJComboBox().getSelectedIndex() < 0) {
      return;      // No evt so no channels
    }    //SubspaceEvent ssevent = (SubspaceEvent) areaPanel.getEventJComboBox().getSelectedItem();
    eventChannelPanel.removeAll();
    if (obj.isNew()) {
      return;
    }
    SubspaceChannel sschan = (SubspaceChannel) subspaceChannel.getSelectedItem();
    if (sschan == null) {
      return;
    }
    int sschanID = sschan.getID();

    int height = 0;
    int width = 0;
    try {
      try (Statement stmt = DBConnectionThread.getConnection(DBConnectionThread.getDBSchema()).createStatement() // used for query
              ) {

        String s = "SELECT * FROM edge.subspaceeventchannel,edge.subspacechannel,edge.subspaceevent "
                + "WHERE subspacechannel.id=subspaceeventchannel.channelid AND subspaceevent.id=subspaceeventchannel.eventid AND "
                + "subspaceevent.areaid = " + sschan.getAreaID() + " AND "
                + "subspacechannel.id = " + sschanID + " ORDER BY arrivaltime DESC,disable,reference DESC, nscl1"; //USER: if DBSchema is not right DB, explict here
        try (ResultSet rs = stmt.executeQuery(s)) {
          while (rs.next()) {
            SubspaceEventChannel item = new SubspaceEventChannel(rs);
            SubspaceEvent ssevent = new SubspaceEvent(rs);
            Util.prt("Channel built item=" + item.toString2());
            SubspaceEventChannelPanel cp = new SubspaceEventChannelPanel(item, ssevent);
            //cp.reload(rs);
            height += cp.getPreferredSize().getHeight() + 10;
            width = (int) cp.getPreferredSize().getWidth();
            eventChannelPanel.add(cp);
          }
        }
      }
    } catch (SQLException e) {
      Util.SQLErrorPrint(e, "checking subspaceeventschannels) on table SQL failed for channel=" + sschan);
    }
    Util.prt("Set size to " + width + " " + height);
    if (height == 0.) {
      eventChannelPanel.add(new JLabel("No channels returned."));
      height = 400;
      width = 500;
    }
    eventChannelPanel.invalidate();
    eventChannelPanel.setPreferredSize(new Dimension((int) width, (int) height));
    eventChannelPanel.setMinimumSize(new Dimension((int) width, (int) height));
    eventChannelPanel.validate();
    validate();

  }//GEN-LAST:event_subspaceChannelActionPerformed

  public void createNewChannel(String channel, String remains) {
    if(areaID == 0) {
      return;
    }
    SubspaceChannel chn = SubspaceChannel.getSubspaceChannelWithAreaChannel(areaID, channel);
    if (chn != null) {
      return;
    }         // channel already exists, skip it
    this.remains = remains;
    subspaceChannel.setSelectedItem(channel);
    this.addUpdate.doClick();

  }
private void deleteItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_deleteItemActionPerformed
  try {
    SubspaceChannel chan = (SubspaceChannel) subspaceChannel.getSelectedItem();
    if (chan == null) {
      return;
    }
    Statement stmt = DBConnectionThread.getThread(DBConnectionThread.getDBSchema()).getNewStatement(true);
    String s = "DELETE FROM edge.subspaceeventchannel WHERE channelid = " + chan.getID();
    Util.prt("SCP.delete() " + s);
    stmt.executeUpdate(s);

    obj.deleteRecord();
    subspaceChannel.removeItem(subspaceChannel.getSelectedItem());
//    SubspaceChannel
    clearScreen();
//    areaPanel.updateChannelPanelsExceptChPanel();
    areaPanel.updateChannelPanels();
  } catch (SQLException e) {
    Util.prta("Delete record failed SQL error=" + e);
  }
}//GEN-LAST:event_deleteItemActionPerformed

private void reloadActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_reloadActionPerformed
  // put whatever jcombobox need reloading here
  //Class.getJComboBox(boxVariable);

  clearScreen();
}//GEN-LAST:event_reloadActionPerformed

  private void detectionThresholdTypeActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_detectionThresholdTypeActionPerformed
    // TODO add your handling code here:
  }//GEN-LAST:event_detectionThresholdTypeActionPerformed


  // Variables declaration - do not modify//GEN-BEGIN:variables
  private javax.swing.JTextField ID;
  private javax.swing.JButton addUpdate;
  private javax.swing.JTextField averagingDuration;
  private javax.swing.JPanel bottomStuff;
  private javax.swing.JTextField changeTo;
  private javax.swing.JTextField completeness;
  private javax.swing.JButton deleteItem;
  private javax.swing.JTextField detectionThreshold;
  private javax.swing.JComboBox<String> detectionThresholdType;
  private javax.swing.JTextField error;
  private javax.swing.JPanel eventChannelPanel;
  private javax.swing.JScrollPane eventChannelScroll;
  private javax.swing.JComboBox<String> filterType;
  private java.awt.GridBagLayout gridBagLayout1;
  private javax.swing.JTextField hpcorn;
  private javax.swing.JLabel idLab;
  private javax.swing.JLabel labAverageDur;
  private javax.swing.JLabel labChange;
  private javax.swing.JLabel labCompleteness;
  private javax.swing.JLabel labDThres;
  private javax.swing.JLabel labDetThresType;
  private javax.swing.JLabel labFilterType;
  private javax.swing.JLabel labHP;
  private javax.swing.JLabel labMain;
  private javax.swing.JLabel labNPoles;
  private javax.swing.JLabel labPreevent;
  private javax.swing.JLabel labTemplateDuration;
  private javax.swing.JTextField lpcorn;
  private javax.swing.JLabel lsbLP;
  private javax.swing.JTextField npoles;
  private javax.swing.JTextField preevent;
  private javax.swing.JButton reload;
  private javax.swing.JComboBox subspaceChannel;
  private javax.swing.JTextField templateDuration;
  private javax.swing.JPanel topPanel;
  // End of variables declaration//GEN-END:variables
  /**
   * Creates new form SubspaceChannelPanel
   *
   * @param area The area panel this is part of. THis is needed to trigger events up there
   */
  public SubspaceChannelPanel(SubspaceAreaPanel area) {
    areaPanel = area;
    initiating = true;
    initComponents();
    getJComboBox(subspaceChannel, 0);                // set up the key JComboBox
    subspaceChannel.setSelectedIndex(-1);    // Set selected type
    initiating = false;
    String metaServer = Util.getProperty("metadataserver");
    stasrv = new StaSrv(metaServer, 2052);
    areaID = 0;
    // USER:This is a good place for UC.Look(components) to get rid of the ugly Grey
    doInit();
    clearScreen();                    // Start with empty screen
  }

  //////////////////////////////////////////////////////////////////////////////
  // No USER: changes needed below here!
  //////////////////////////////////////////////////////////////////////////////
  /**
   * Create a JComboBox of all of the items in the table represented by this panel
   *
   * @param areaID The area id for the channels in this JComboBox
   * @return A New JComboBox filled with all row keys from the table
   */
  public static JComboBox getJComboBox(int areaID) {
    JComboBox b = new JComboBox();
    getJComboBox(b, areaID);
    return b;
  }

  /**
   * Update a JComboBox to represent the rows represented by this panel
   *
   * @param b The JComboBox to Update
   * @param areaID Populate a JCOmbo with the channels in the given areaID
   */
  public static void getJComboBox(JComboBox b, int areaID) {
    b.removeAllItems();
    SubspaceChannel.subspaceChannels = null;
    makeSubspaceChannels();
    SubspaceChannel.sortListByDistance();
    for (SubspaceChannel v1 : SubspaceChannel.subspaceChannels) {
      if (v1.getAreaID() == areaID) {
        b.addItem(v1);
      }
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
    //Util.prt("SubspaceChannelPanel.setJComboBoxToID id="+ID);
    for (int i = 0; i < b.getItemCount(); i++) {
      if (((SubspaceChannel) b.getItemAt(i)).getID() == ID) {
        b.setSelectedIndex(i);
        //Util.prt("SubspaceChannelPanel.setJComboBoxToID id="+ID+" found at "+i);
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
    if (SubspaceChannel.subspaceChannels == null) {
      makeSubspaceChannels();
    }
    for (int i = 0; i < SubspaceChannel.subspaceChannels.size(); i++) {
      if (ID == SubspaceChannel.subspaceChannels.get(i).getID()) {
        return i;
      }
    }
    return -1;
  }

  /**
   * Cause the main JComboBox to be reloaded
   */
  public static void reload() {
    SubspaceChannel.subspaceChannels = null;
    makeSubspaceChannels();
  }

  /* return a ArrayList with all of the SubspaceChannel
   * @return The ArrayList with the subspaceChannel
   */
  public static ArrayList<SubspaceChannel> getSubspaceChannelVector() {
    if (SubspaceChannel.subspaceChannels == null) {
      makeSubspaceChannels();
    }
    return SubspaceChannel.subspaceChannels;
  }

  /* return a ArrayList with all of the SubspaceChannel
   * @return The ArrayList with the subspaceChannel
   */
  public static ArrayList<SubspaceChannel> getSubspaceChannelArrayList() {
    if (SubspaceChannel.subspaceChannels == null) {
      makeSubspaceChannels();
    }
    return SubspaceChannel.subspaceChannels;
  }

  /**
   * Get the item corresponding to database ID
   *
   * @param ID the database Row ID
   * @return The SubspaceChannel row with this ID
   */
  public static SubspaceChannel getSubspaceChannelWithID(int ID) {
    if (SubspaceChannel.subspaceChannels == null) {
      makeSubspaceChannels();
    }
    int i = getJComboBoxIndex(ID);
    if (i >= 0) {
      return SubspaceChannel.subspaceChannels.get(i);
    } else {
      return null;
    }
  }

  // This routine should only need tweeking if key field is not same as table name
  private static void makeSubspaceChannels() {
    if (SubspaceChannel.subspaceChannels != null) {
      return;
    }
    SubspaceChannel.makeSubspaceChannels();
  }

  // No changes needed
  private void find() {
    if (subspaceChannel == null) {
      return;
    }
    if (initiating) {
      return;
    }
    SubspaceChannel l;
    if (subspaceChannel.getSelectedIndex() == -1) {
      if (subspaceChannel.getSelectedItem() == null) {
        return;
      }
      l = newOne();
    } else {
      l = (SubspaceChannel) subspaceChannel.getSelectedItem();
    }

    if (l == null) {
      return;
    }
    String p = l.getSubspaceChannel();
    if (p == null) {
      return;
    }
    if (p.equals("")) {
      addUpdate.setEnabled(false);
      deleteItem.setEnabled(false);
      addUpdate.setText("Enter a SubspaceChannel!");
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
      Util.SQLErrorPrint(E, "SubspaceChannel: SQL error getting SubspaceChannel=" + p);
      E.printStackTrace();
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
      Show.inFrame(new SubspaceChannelPanel(null), UC.XSIZE, UC.YSIZE);
    } catch (InstantiationException e) {
    }

  }
}
