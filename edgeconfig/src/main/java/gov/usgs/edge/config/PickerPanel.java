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
import gov.usgs.anss.db.DBConnectionThread;
import gov.usgs.anss.db.DBObject;
import gov.usgs.anss.util.ErrorTrack;
import gov.usgs.anss.util.Show;
import gov.usgs.anss.util.UC;
import gov.usgs.anss.util.Util;
import java.awt.Color;
import java.awt.Dimension;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.BatchUpdateException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Set;
import java.util.HashSet;
import java.util.Map;
import java.util.TreeMap;
import javax.swing.JComboBox;
/**
 *
 * requirements : 
 * The key for the file must be the name of the table and of the
 * JComboBox variable this variable is called "picker" in the initial form
 * The table name and key name should match and start lower case (picker).
 * The Class here will be that same name but with upper case at beginning(Picker).
 * <br> 1)  Rename the location JComboBox to the "key" (picker) value.  This should start Lower
 *      case.
 *<br>  2)  The table name should match this key name and be all lower case.
 *<br>  3)  Change all Picker to ClassName of underlying data (case sensitive!)
 *<br>  4)  Change all picker key value (the JComboBox above)
 *<br>  5)  clearScreen should update swing components to new defaults
 *<br>  6)  chkForm should check all field for validity.  The errorTrack variable is
 *      used to track multiple errors.  In FUtil.chk* handle checks of the various
 *      variable types.  Date, double, and IP etc return the "canonical" form for 
 *      the variable.  These should be stored in a class variable so the latest one
 *      from "chkForm()" are available to the update writer.
 *<br>  7) newone() should be updated to create a new instance of the underlying data class.
 *<br>  8) oldone() get data from database and update all swing elements to correct values
 *<br>  9) addUpdateAction() add data base set*() operations updating all fields in the DB
 *<br> 10) PickerPanel() constructor - good place to change backgrounds using UC.Look() any
 *    other weird startup stuff.
 *
 *<br>  local variable error - Must be a JTextField for posting error communications
 * <br> local variable updateAdd - Must be the JButton for user clicks to try to post the data
 * <br> ID - JTextField which must be non-editable for posting Database IDs
 * <br>
 *<br>
 *ENUM notes :  the ENUM are read from the database as type String.  So the constructor
 * should us the strings to set the indexes.  However, the writes to the database are
 * done as ints representing the value of the Enum.  The JComboBox with the Enum has 
 * the strings as Items in the list with indexes equal to the database value of the int.
 * Such JComboBoxes are usually initialized with
 *<br>
 *<br> FUtil.getEnumJComboBox(DBConnectionThread.getConnection("DATABASE"), "tcpstation","rtsport1");
 *<br> Put this in "CodeGeneration"  "CustomCreateCode" of the form editor for each Enum.
 *<br>
 *<br> In  oldone() get Enum fields with something like :
 * <br>  fieldJcomboBox.setSelectedItem(obj.getString("fieldName"));
 *<br>  (This sets the JComboBox to the Item matching the string)
 *<br>
 * data class should have code like :
 *<br><br>  import java.util.ArrayList;          /// Import for Enum manipulation
 *<br>    .
 * <br>  static ArrayList fieldEnum;         // This list will have Strings corresponding to enum
 *  <br>      .
 * <br>       .
 * <br>    // To convert an enum we need to create the fieldEnum ArrayList.  Do only once(static)
 * <br>    if(fieldEnum == null) fieldEnum = FUtil.getEnum(DBConnectionThread.getConnection("DATABASE"),"table","fieldName");
 * <br>    
 * <br>   // Get the int corresponding to an Enum String for storage in the data base and 
 * <br>   // the index into the JComboBox representing this Enum 
 * <br>   localVariable = FUtil.enumStringToInt(fieldEnum, enumString);
  * <br>
 * @author  D.C. Ketchum
 */
public class PickerPanel extends javax.swing.JPanel {
  //USER: set this for the upper case conversion of keys.
  private final boolean keyUpperCase=false;    // set this true if all keys must be upper case
  //USER: Here are the local variables
  // NOTE : here define all variables general.  "ArrayList pickers" is used for main Comboboz
  private final String defaultCWBPickerTemplate = 
          "!Key=value                   ! for CWB Picker\n"+
          "!Channel=NNSSSSSCCLL         ! The channel to process\n" +
          "TemplateFile=template       ! this refers to the template stored in the picker DB table\n" +
          "Blocksize=10                ! The processing block size in seconds\n" +
          "PickDBServer=dbURL          ! Set the DB where the status.globalpicks and edge.picker configuration are\n" +
          "Author=author               ! Set the author (def=FP6)\n" +
          "Agency=agency               ! Set the agency\n" +
          "!StartTime=yyyy/mm/dd hh:mm ! begintime Starting time, not valid on a segment picker\n" +
          "!EndTime=yyyy/mm/dd hh:mm   !  Ending Time\n" +
          "Title=title                 ! A descriptive title for this instance\n" +
          "Rate=100.00                 ! The digit rate, else one from the time series will be used\n" +
          "CWBIPAddress=localhost      ! Set source of time series data\n" +
          "CWBPort=2061                ! 2061 always we hope\n" +
          "Makefiles=true              ! If true, this instance will make files in MiniSeed from parameters.\n" +
          "PickerTag=TEXT              ! Some tag associated with the picker\n" +
          "JSONArgs=1;2&3;4            ! see gov.usgs.picker.JsonDetectionSender for details\n";
  private final String defaultFilterPickerTemplate =
          "!key#value                   ! for FP6 Prog Def  Comment\n" +
          "bands#0.5,1.:1.,1.:2.,1.:4.,1.:8.,1.:16.,1.:32.,1.  ! fr,fact:fr,fact... where fr is center freq in Hz and fact is the scaling factor for thresholds\n"+
          "filterWindow#0.8            ! 0.8 \n" +
          "longTermWindow#300.         ! 6.0\n" +
          "Threshold1#6.00             ! 9.36\n" +
          "Threshold2#5.00             ! 9.21\n" +
          "TupEvent#4.                 ! 0.388\n" +
          "TupEventMin#0.25            ! 1.\n";
  private void doInit() {
  // Good place to add UC.Looks()
    deleteItem.setVisible(true);
    reload.setVisible(false);
    idLab.setVisible(true);
    ID.setVisible(true);
    changeTo.setVisible(true);
    labChange.setVisible(true);
    if (EdgeConfig.hasTable("edge","pickerchannels")) {
      gov.usgs.edge.config.UC.Look(currentChannelsPanel);
      gov.usgs.edge.config.UC.Look(channelsPanel);
      gov.usgs.edge.config.UC.Look(channelsSelectionPanel);
      UC.Look(addChannelsPanel);
      UC.Look(addSelectChannelsPanel);
      UC.Look(singlePickersSelectPanel);
      UC.Look(singleChannelPanel);
    } else {
      channelsPanel.setVisible(false);
    }
    UC.Look(this);                    // Set color background

  }
  private void doAddUpdate() throws SQLException {
    // USER : Set all of the fields in data base.  obj.setInt("thefield", ivalue);
    obj.setString("classname",classname.getText());
    obj.setString("template",template.getText());
    obj.setString("args", args.getText());
  }
  private void doOldOne() throws SQLException {
      //USER: Here set all of the form fields to data from the DBObject
      //      picker.setText(obj.getString("Picker"));
      // Example : description.setText(obj.getString("description"));
    classname.setText(obj.getString("classname"));
    template.setText(obj.getString("template"));
    args.setText(obj.getString("args"));

  }
  

  // This routine must validate all fields on the form.  The err (ErrorTrack) variable
  // is used by the FUtil to verify fields.  Use FUTil or custom code here for verifications
  private boolean chkForm() {
    // Do not change
    err.reset();
    UC.Look(error);
   
    //USER:  Your error checking code goes here setting the local variables to valid settings
    // note : Use FUtil.chk* to check dates, timestamps, IP addresses, etc and put them in
    // canonical form.  store in a class variable.  For instance :
    //   sdate = FUtil.chkDate(dateTextField, err);
    // Any errors found should err.set(true);  and err.append("error message");
    if(template.getText().length() > 0 && args.getText().length() > 0) {
      err.appendText("Both args and template cannot be used");
      err.set();
    }

    // No CHANGES : If we found an error, color up error box
    if(err.isSet()) {
      error.setText(err.getText());
      error.setBackground(UC.red);
    }
    return err.isSet();
  }
  
  // This routine must set the form to initial state.  It does not update the JCombobox
  private void clearScreen() {
    
    // Do not change
    ID.setText("");
    UC.Look(error);
    UC.Look(bottomStuff);
    error.setText("");
    addUpdate.setEnabled(false);
    addUpdate.setText("Enter a Picker");
    deleteItem.setEnabled(false);
    changeTo.setText("");
    
    //USER: Clear all fields for this form, this sets "defaults" for new screen
    // description.setText("");
    classname.setText("");
    template.setText("");
    args.setText("");

  }


  /**********************  NO USER MODIFICATION BELOW HERE EXCEPT FOR ACTIONS **************/
  DBObject obj;
  ErrorTrack err=new ErrorTrack();
  boolean initiating=false;

  
  private Picker newOne() {
      
    return new Picker(0, ((String) picker.getSelectedItem()).toUpperCase() //, more
            ,"","",""
    );
  }
  
  private void oldOne(String p) throws SQLException {
    obj = new DBObject(DBConnectionThread.getThread(DBConnectionThread.getDBSchema()), 
            DBConnectionThread.getDBSchema(),       // Schema, if this is not right in URL load, it must be explicit here
            "picker","picker",p);                     // table name and field name are usually the same

    if(obj.isNew()) {
      //Util.prt("object is new"+p);
    }
    else {
      //USER: Here set all of the form fields to data from the DBObject
      //      picker.setText(obj.getString("Picker"));
      // Example : description.setText(obj.getString("description"));
      doOldOne();
      
        
    }           // End else isNew() - processing to form
  }
  
  
  /** This method is called from within the constructor to
   * initialize the form.
   * WARNING: Do NOT modify this code. The content of this method is
   * always regenerated by the Form Editor.
   */
  // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
  private void initComponents() {
    java.awt.GridBagConstraints gridBagConstraints;

    gridBagLayout1 = new java.awt.GridBagLayout();
    channelsPanel = new javax.swing.JPanel();
    channelsTabbedPane = new javax.swing.JTabbedPane();
    channelsSelectionPanel = new javax.swing.JPanel();
    currentChannelsScrollPane = new javax.swing.JScrollPane();
    currentChannelsPanel = new javax.swing.JPanel();
    selectAllCurrentChannelsButton = new javax.swing.JButton();
    deleselectAllCurrentChannelsButton = new javax.swing.JButton();
    refreshCurrenChannelsButton = new javax.swing.JButton();
    updateCurrentChannels = new javax.swing.JButton();
    currentTotalChannels = new javax.swing.JLabel();
    addChannelsPanel = new javax.swing.JPanel();
    addChannelsScrollPane = new javax.swing.JScrollPane();
    addSelectChannelsPanel = new javax.swing.JPanel();
    addChannelsRegex = new javax.swing.JTextField();
    jLabel1 = new javax.swing.JLabel();
    addRegexChannelsButton = new javax.swing.JButton();
    persistRegexChannels = new javax.swing.JButton();
    resetAddChannelsButton = new javax.swing.JButton();
    addTotalChannels = new javax.swing.JLabel();
    singleChannelPanel = new javax.swing.JPanel();
    singlePickersScrollPane = new javax.swing.JScrollPane();
    singlePickersSelectPanel = new javax.swing.JPanel();
    jLabel2 = new javax.swing.JLabel();
    singleChannelRegex = new javax.swing.JTextField();
    singleChannelComboBox = new javax.swing.JComboBox<>();
    singleChannelResetButton = new javax.swing.JButton();
    singleChannelUpdateButton = new javax.swing.JButton();
    labMain = new javax.swing.JLabel();
    picker = getJComboBox();
    error = new javax.swing.JTextField();
    bottomStuff = new javax.swing.JPanel();
    addUpdate = new javax.swing.JButton();
    labChange = new javax.swing.JLabel();
    changeTo = new javax.swing.JTextField();
    deleteItem = new javax.swing.JButton();
    reload = new javax.swing.JButton();
    idLab = new javax.swing.JLabel();
    ID = new javax.swing.JTextField();
    labClass = new javax.swing.JLabel();
    classname = new javax.swing.JTextField();
    labArgs = new javax.swing.JLabel();
    jScrollPane1 = new javax.swing.JScrollPane();
    template = new javax.swing.JTextArea();
    loadCWBPickerDefault = new javax.swing.JButton();
    loadFPDefault = new javax.swing.JButton();
    jScrollPane2 = new javax.swing.JScrollPane();
    args = new javax.swing.JTextArea();
    labTemplate = new javax.swing.JLabel();

    setLayout(new java.awt.GridBagLayout());

    channelsTabbedPane.setToolTipText("");

    currentChannelsScrollPane.setViewportView(currentChannelsPanel);

    selectAllCurrentChannelsButton.setText("Select All");
    selectAllCurrentChannelsButton.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        selectAllCurrentChannelsButtonActionPerformed(evt);
      }
    });

    deleselectAllCurrentChannelsButton.setText("Deselect All");
    deleselectAllCurrentChannelsButton.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        deleselectAllCurrentChannelsButtonActionPerformed(evt);
      }
    });

    refreshCurrenChannelsButton.setText("Refresh List");
    refreshCurrenChannelsButton.setToolTipText("Reload channels using this picker from the database.  This will also reselect any channels that have been deselected.");
    refreshCurrenChannelsButton.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        refreshCurrenChannelsButtonActionPerformed(evt);
      }
    });

    updateCurrentChannels.setText("Update Selections");
    updateCurrentChannels.setToolTipText("Commit channels selected status permanently to the database.");
    updateCurrentChannels.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        updateCurrentChannelsActionPerformed(evt);
      }
    });

    currentTotalChannels.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
    currentTotalChannels.setText("Total Channels: 0");

    javax.swing.GroupLayout channelsSelectionPanelLayout = new javax.swing.GroupLayout(channelsSelectionPanel);
    channelsSelectionPanel.setLayout(channelsSelectionPanelLayout);
    channelsSelectionPanelLayout.setHorizontalGroup(
      channelsSelectionPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
      .addGroup(channelsSelectionPanelLayout.createSequentialGroup()
        .addGap(20, 20, 20)
        .addComponent(currentChannelsScrollPane, javax.swing.GroupLayout.PREFERRED_SIZE, 368, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGroup(channelsSelectionPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
          .addGroup(channelsSelectionPanelLayout.createSequentialGroup()
            .addGroup(channelsSelectionPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
              .addGroup(channelsSelectionPanelLayout.createSequentialGroup()
                .addGap(18, 18, 18)
                .addComponent(selectAllCurrentChannelsButton)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(deleselectAllCurrentChannelsButton))
              .addGroup(channelsSelectionPanelLayout.createSequentialGroup()
                .addGap(49, 49, 49)
                .addComponent(updateCurrentChannels)))
            .addContainerGap(8, Short.MAX_VALUE))
          .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, channelsSelectionPanelLayout.createSequentialGroup()
            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addComponent(refreshCurrenChannelsButton)
            .addGap(59, 59, 59))
          .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, channelsSelectionPanelLayout.createSequentialGroup()
            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
            .addComponent(currentTotalChannels, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addGap(16, 16, 16))))
    );
    channelsSelectionPanelLayout.setVerticalGroup(
      channelsSelectionPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
      .addGroup(channelsSelectionPanelLayout.createSequentialGroup()
        .addGap(5, 5, 5)
        .addComponent(currentChannelsScrollPane)
        .addContainerGap())
      .addGroup(channelsSelectionPanelLayout.createSequentialGroup()
        .addGap(20, 20, 20)
        .addGroup(channelsSelectionPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
          .addComponent(selectAllCurrentChannelsButton)
          .addComponent(deleselectAllCurrentChannelsButton))
        .addGap(18, 18, 18)
        .addComponent(currentTotalChannels)
        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 31, Short.MAX_VALUE)
        .addComponent(refreshCurrenChannelsButton)
        .addGap(52, 52, 52)
        .addComponent(updateCurrentChannels)
        .addGap(34, 34, 34))
    );

    channelsTabbedPane.addTab("Current Channels", channelsSelectionPanel);

    addChannelsScrollPane.setViewportView(addSelectChannelsPanel);

    addChannelsRegex.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        addChannelsRegexActionPerformed(evt);
      }
    });

    jLabel1.setText("Channel Regex:");

    addRegexChannelsButton.setText("Add Channels By Regex");
    addRegexChannelsButton.setToolTipText("Use the above Regular Expression to select channels.");
    addRegexChannelsButton.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        addRegexChannelsButtonActionPerformed(evt);
      }
    });

    persistRegexChannels.setText("Update Channels");
    persistRegexChannels.setToolTipText("Add the listed channels that are selected to the picker and persist to the database.");
    persistRegexChannels.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        persistRegexChannelsActionPerformed(evt);
      }
    });

    resetAddChannelsButton.setText("Clear All/Start Over");
    resetAddChannelsButton.setToolTipText("Remove all channels that have been added so far.");
    resetAddChannelsButton.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        resetAddChannelsButtonActionPerformed(evt);
      }
    });

    addTotalChannels.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
    addTotalChannels.setText("Total Channels: 0");

    javax.swing.GroupLayout addChannelsPanelLayout = new javax.swing.GroupLayout(addChannelsPanel);
    addChannelsPanel.setLayout(addChannelsPanelLayout);
    addChannelsPanelLayout.setHorizontalGroup(
      addChannelsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
      .addGroup(addChannelsPanelLayout.createSequentialGroup()
        .addGap(20, 20, 20)
        .addComponent(addChannelsScrollPane, javax.swing.GroupLayout.PREFERRED_SIZE, 368, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGroup(addChannelsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
          .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, addChannelsPanelLayout.createSequentialGroup()
            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addGroup(addChannelsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
              .addComponent(resetAddChannelsButton)
              .addComponent(addRegexChannelsButton))
            .addGap(30, 30, 30))
          .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, addChannelsPanelLayout.createSequentialGroup()
            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
            .addComponent(addChannelsRegex)
            .addContainerGap())
          .addGroup(addChannelsPanelLayout.createSequentialGroup()
            .addGroup(addChannelsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
              .addGroup(addChannelsPanelLayout.createSequentialGroup()
                .addGap(68, 68, 68)
                .addComponent(jLabel1))
              .addGroup(addChannelsPanelLayout.createSequentialGroup()
                .addGap(46, 46, 46)
                .addComponent(persistRegexChannels))
              .addGroup(addChannelsPanelLayout.createSequentialGroup()
                .addGap(20, 20, 20)
                .addComponent(addTotalChannels, javax.swing.GroupLayout.PREFERRED_SIZE, 213, javax.swing.GroupLayout.PREFERRED_SIZE)))
            .addContainerGap(20, Short.MAX_VALUE))))
    );
    addChannelsPanelLayout.setVerticalGroup(
      addChannelsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
      .addComponent(addChannelsScrollPane, javax.swing.GroupLayout.Alignment.TRAILING)
      .addGroup(addChannelsPanelLayout.createSequentialGroup()
        .addGap(15, 15, 15)
        .addComponent(jLabel1)
        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
        .addComponent(addChannelsRegex, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
        .addComponent(addRegexChannelsButton)
        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
        .addComponent(addTotalChannels)
        .addGap(12, 12, 12)
        .addComponent(resetAddChannelsButton)
        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 33, Short.MAX_VALUE)
        .addComponent(persistRegexChannels)
        .addGap(29, 29, 29))
    );

    channelsTabbedPane.addTab("Add Channels", addChannelsPanel);

    singlePickersScrollPane.setViewportView(singlePickersSelectPanel);

    jLabel2.setText("Channel Regex:");

    singleChannelRegex.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        singleChannelRegexActionPerformed(evt);
      }
    });

    singleChannelComboBox.setToolTipText("This list will be populated from channels that match above regular expression.");
    singleChannelComboBox.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        singleChannelComboBoxActionPerformed(evt);
      }
    });

    singleChannelResetButton.setText("Reset Values");
    singleChannelResetButton.setToolTipText("Reload values from the database and undo any changes made that haven't been persisted to the database.");
    singleChannelResetButton.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        singleChannelResetButtonActionPerformed(evt);
      }
    });

    singleChannelUpdateButton.setText("Update Selections");
    singleChannelUpdateButton.setToolTipText("Persist selections to the database.");
    singleChannelUpdateButton.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        singleChannelUpdateButtonActionPerformed(evt);
      }
    });

    javax.swing.GroupLayout singleChannelPanelLayout = new javax.swing.GroupLayout(singleChannelPanel);
    singleChannelPanel.setLayout(singleChannelPanelLayout);
    singleChannelPanelLayout.setHorizontalGroup(
      singleChannelPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
      .addGroup(singleChannelPanelLayout.createSequentialGroup()
        .addContainerGap(9, Short.MAX_VALUE)
        .addComponent(singlePickersScrollPane, javax.swing.GroupLayout.PREFERRED_SIZE, 349, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(18, 18, 18)
        .addGroup(singleChannelPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
          .addGroup(singleChannelPanelLayout.createSequentialGroup()
            .addGroup(singleChannelPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
              .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, singleChannelPanelLayout.createSequentialGroup()
                .addComponent(jLabel2, javax.swing.GroupLayout.PREFERRED_SIZE, 122, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(58, 58, 58))
              .addComponent(singleChannelComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, 218, javax.swing.GroupLayout.PREFERRED_SIZE)
              .addComponent(singleChannelUpdateButton, javax.swing.GroupLayout.Alignment.TRAILING)
              .addComponent(singleChannelRegex, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.PREFERRED_SIZE, 224, javax.swing.GroupLayout.PREFERRED_SIZE))
            .addGap(41, 41, 41))
          .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, singleChannelPanelLayout.createSequentialGroup()
            .addComponent(singleChannelResetButton)
            .addGap(60, 60, 60))))
    );
    singleChannelPanelLayout.setVerticalGroup(
      singleChannelPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
      .addGroup(singleChannelPanelLayout.createSequentialGroup()
        .addGap(19, 19, 19)
        .addComponent(jLabel2)
        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
        .addComponent(singleChannelRegex, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
        .addComponent(singleChannelComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
        .addComponent(singleChannelResetButton)
        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 57, Short.MAX_VALUE)
        .addComponent(singleChannelUpdateButton)
        .addGap(31, 31, 31))
      .addGroup(singleChannelPanelLayout.createSequentialGroup()
        .addContainerGap()
        .addComponent(singlePickersScrollPane)
        .addContainerGap())
    );

    channelsTabbedPane.addTab("SingleChannel", singleChannelPanel);

    javax.swing.GroupLayout channelsPanelLayout = new javax.swing.GroupLayout(channelsPanel);
    channelsPanel.setLayout(channelsPanelLayout);
    channelsPanelLayout.setHorizontalGroup(
      channelsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
      .addGroup(channelsPanelLayout.createSequentialGroup()
        .addContainerGap()
        .addComponent(channelsTabbedPane, javax.swing.GroupLayout.PREFERRED_SIZE, 662, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
    );
    channelsPanelLayout.setVerticalGroup(
      channelsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
      .addGroup(channelsPanelLayout.createSequentialGroup()
        .addComponent(channelsTabbedPane, javax.swing.GroupLayout.PREFERRED_SIZE, 304, javax.swing.GroupLayout.PREFERRED_SIZE)
        .addGap(0, 0, Short.MAX_VALUE))
    );

    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 0;
    add(channelsPanel, gridBagConstraints);

    labMain.setText("PickerTag:");
    labMain.setToolTipText("This button commits either a new item or the changes for an updating item.  Its label with change to reflect that.");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 2;
    add(labMain, gridBagConstraints);

    picker.setEditable(true);
    picker.setToolTipText("Select an item to edit in the JComboBox or type in the name of an item.   If the typed name does not match an existing item, an new item is assumed.");
    picker.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        pickerActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 2;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(picker, gridBagConstraints);

    error.setEditable(false);
    error.setColumns(40);
    error.setMinimumSize(new java.awt.Dimension(488, 22));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(error, gridBagConstraints);

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

    labClass.setText("Classname:");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 3;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    add(labClass, gridBagConstraints);

    classname.setColumns(20);
    classname.setMinimumSize(new java.awt.Dimension(254, 28));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 3;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(classname, gridBagConstraints);

    labArgs.setText("Args:");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 7;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    add(labArgs, gridBagConstraints);

    jScrollPane1.setMinimumSize(new java.awt.Dimension(550, 250));
    jScrollPane1.setPreferredSize(new java.awt.Dimension(550, 250));

    template.setColumns(50);
    template.setFont(new java.awt.Font("Monospaced", 0, 12)); // NOI18N
    template.setRows(20);
    template.setTabSize(2);
    jScrollPane1.setViewportView(template);

    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 6;
    add(jScrollPane1, gridBagConstraints);

    loadCWBPickerDefault.setText("Add Default CWBPicker Key-Values");
    loadCWBPickerDefault.setToolTipText("");
    loadCWBPickerDefault.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        loadCWBPickerDefaultActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 8;
    add(loadCWBPickerDefault, gridBagConstraints);

    loadFPDefault.setText("Add Default Filterpick Key-Values");
    loadFPDefault.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        loadFPDefaultActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 9;
    add(loadFPDefault, gridBagConstraints);

    jScrollPane2.setMinimumSize(new java.awt.Dimension(550, 40));
    jScrollPane2.setPreferredSize(new java.awt.Dimension(550, 40));

    args.setColumns(20);
    args.setFont(new java.awt.Font("Monospaced", 0, 12)); // NOI18N
    args.setRows(5);
    jScrollPane2.setViewportView(args);

    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 7;
    add(jScrollPane2, gridBagConstraints);

    labTemplate.setText("Key-Values:");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 6;
    add(labTemplate, gridBagConstraints);
  }// </editor-fold>//GEN-END:initComponents

  private void addUpdateActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_addUpdateActionPerformed
    // Add your handling code here:
    
    // Do not change
    if(initiating) return;
    if(chkForm()) return;
    try {
      String p = picker.getSelectedItem().toString();
      if(keyUpperCase) p = p.toUpperCase();                  // USER : drop this to allow Mixed case
      if(!obj.isNew()) obj.refreshRecord();        // Refresh the result set so the sets will not fail on stale one
      if(!changeTo.getText().equals("")) p = changeTo.getText();
      obj.setString("picker",p);
      
      // USER : Set all of the fields in data base.  obj.setInt("thefield", ivalue);
      doAddUpdate();
      
      // Do not change
      obj.updateRecord();
      Picker.pickers=null;       // force reload of combo box
      getJComboBox(picker);
      clearScreen();
      picker.setSelectedIndex(-1);
    }  catch  (SQLException  E)
    { Util.SQLErrorPrint(E,"picker: update failed partno="+picker.getSelectedItem());
      error.setText("Could Not Update File!");
      error.setBackground(UC.red);
      addUpdate.setEnabled(true);
    }      
  }//GEN-LAST:event_addUpdateActionPerformed

  private void pickerActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_pickerActionPerformed
    // Add your handling code here:
   find();
 
 
  }//GEN-LAST:event_pickerActionPerformed

private void deleteItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_deleteItemActionPerformed
  try {
    obj.deleteRecord();
    picker.removeItem(picker.getSelectedItem());
    clearScreen();
  }
  catch(SQLException e) {
    Util.prta("Delete record failed SQL error="+e);
  }
}//GEN-LAST:event_deleteItemActionPerformed

private void reloadActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_reloadActionPerformed
  // put whatever jcombobox need reloading here
  //Class.getJComboBox(boxVariable);

  clearScreen();
}//GEN-LAST:event_reloadActionPerformed

  private void loadCWBPickerDefaultActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_loadCWBPickerDefaultActionPerformed
    template.setText(template.getText()+defaultCWBPickerTemplate);
  }//GEN-LAST:event_loadCWBPickerDefaultActionPerformed

  private void loadFPDefaultActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_loadFPDefaultActionPerformed
    template.setText(template.getText()+defaultFilterPickerTemplate);
  }//GEN-LAST:event_loadFPDefaultActionPerformed

  private void selectAllCurrentChannelsButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_selectAllCurrentChannelsButtonActionPerformed
    for (java.awt.Component csp : currentChannelsPanel.getComponents()) {
      ((PickerChannelsSubPanel) csp).setSelected(true);     
    }
    currentChannelsPanel.repaint();
  }//GEN-LAST:event_selectAllCurrentChannelsButtonActionPerformed

  private void refreshCurrenChannelsButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_refreshCurrenChannelsButtonActionPerformed
    try {
      updateChannelPanels(obj.getID(),obj.getString("picker"));
    }  
    // This is only thrown if something happens in oldone() new DBOject
    // Other than it being a new record.
    catch  (SQLException  E)                                 // 
    { Util.SQLErrorPrint(E,"Picker: SQL error refreshing currentChannels.");
    }
  }//GEN-LAST:event_refreshCurrenChannelsButtonActionPerformed

  private void deleselectAllCurrentChannelsButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_deleselectAllCurrentChannelsButtonActionPerformed
    for (java.awt.Component csp : currentChannelsPanel.getComponents()) {
      ((PickerChannelsSubPanel) csp).setSelected(false);     
    }    
    currentChannelsPanel.repaint();
  }//GEN-LAST:event_deleselectAllCurrentChannelsButtonActionPerformed

  private void updateCurrentChannelsActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_updateCurrentChannelsActionPerformed
    updateCurrentChannels();    
    try {
      updateChannelPanels(obj.getID(),obj.getString("picker"));
    }  
    // This is only thrown if something happens in oldone() new DBOject
    // Other than it being a new record.
    catch  (SQLException  E)                                 // 
    { Util.SQLErrorPrint(E,"Picker: SQL error refreshing currentChannels.");
    }
  }//GEN-LAST:event_updateCurrentChannelsActionPerformed

  private void addRegexChannelsButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_addRegexChannelsButtonActionPerformed
    if (obj == null || obj.isNew()) return;
    if (addChannelsRegex.getText().equals("")) return;
    ArrayList<java.awt.Component> existingComps = new ArrayList<>();
    Set<String> existingChanNames = new HashSet<>();
    for (java.awt.Component csp : addSelectChannelsPanel.getComponents()) {
      existingComps.add(csp);
      existingChanNames.add(((PickerChannelsSubPanel)csp).getChannelName());
    }
    int height=0;
    int width = 0;
    try (Statement stmt = DBConnectionThread.getThread("edge").getNewStatement(false)) {
        String sql = "SELECT * FROM edge.channel WHERE channel REGEXP '"
                + addChannelsRegex.getText() + "' ORDER BY channel";
        if (addChannelsRegex.getText().length() >= 5 && addChannelsRegex.getText().substring(0, 5).equalsIgnoreCase("WHERE")) {
          sql = "SELECT * FROM edge.channel " + addChannelsRegex.getText() + " ORDER BY channel";
        }        
        try (ResultSet rs = stmt.executeQuery(sql)) { 
          while (rs.next()) {
            if (existingChanNames.contains(rs.getString("channel"))) continue;
            PickerChannelsSubPanel cp = new PickerChannelsSubPanel(this, rs.getInt("id"), rs.getString("channel"),
                    obj.getID(), obj.getString("picker"));
            height += cp.getPreferredSize().getHeight() + 5;
            width = (int) cp.getPreferredSize().getWidth();
            cp.setText(rs.getString("channel"));
            cp.setSelected(true);
            addSelectChannelsPanel.add(cp);          
          }
        }
    } catch (SQLException e) {
      e.printStackTrace();
      Util.prt("SQL error getting channels\n" + e.getMessage() + "\n" + e.getErrorCode() + "\n" + e.getSQLState());
    }
    for (java.awt.Component csp : existingComps) {
      height += csp.getPreferredSize().getHeight() + 5;
      width = (int) csp.getPreferredSize().getWidth();
      addSelectChannelsPanel.add(csp);
    }
    addTotalChannels.setText("Total Channels: " + Integer.toString(addSelectChannelsPanel.getComponentCount()));
    addSelectChannelsPanel.invalidate();
    addSelectChannelsPanel.setPreferredSize(new Dimension((int) width, (int) height / 2 + 30));
    addSelectChannelsPanel.setMinimumSize(new Dimension((int) width, (int) height / 2 + 30));
    addSelectChannelsPanel.validate();
    addSelectChannelsPanel.repaint();
    validate();
  }//GEN-LAST:event_addRegexChannelsButtonActionPerformed

  private void resetAddChannelsButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_resetAddChannelsButtonActionPerformed
    addSelectChannelsPanel.removeAll();
    addTotalChannels.setText("Total Channels: 0");
    addSelectChannelsPanel.revalidate();
    addSelectChannelsPanel.repaint();    
  }//GEN-LAST:event_resetAddChannelsButtonActionPerformed

  private void persistRegexChannelsActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_persistRegexChannelsActionPerformed
    if (obj == null || obj.isNew()) return;
    try {
      int count = 0;
      //The pickerchannels table does not have a key based on pickerid and channelid so
      //this SQL trick prevents adding duplicates.
      PreparedStatement prepStmt = DBConnectionThread.getConnection(DBConnectionThread.getDBSchema())
              .prepareStatement("INSERT INTO edge.pickerchannels (pickerID,channelID) VALUES (?,?)");
      for (java.awt.Component csp : addSelectChannelsPanel.getComponents()) {
        if (((PickerChannelsSubPanel)csp).getSelected()) {
          prepStmt.setInt(1,obj.getID());
          prepStmt.setInt(2,((PickerChannelsSubPanel)csp).getChannelId());          
          prepStmt.addBatch();
          count++;
        }
      }
      if (count > 0) {
        try {
        prepStmt.executeBatch(); //BatchUpdateException
        } catch (BatchUpdateException e) {
          //Assuming it was a duplicate record so nothing to do
        }        
      }
    }  catch (SQLException e) {
      Util.SQLErrorPrint(e, "Adding channels SQL failed.");          
    }  
    resetAddChannelsButtonActionPerformed(null);
    try {
    updateChannelPanels(obj.getID(),obj.getString("picker"));
    } catch (SQLException e) {
      Util.SQLErrorPrint(e, "Adding channels SQL failed.");          
    }  
    channelsTabbedPane.setSelectedIndex(0);
    
  }//GEN-LAST:event_persistRegexChannelsActionPerformed

  private void addChannelsRegexActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_addChannelsRegexActionPerformed
    addRegexChannelsButtonActionPerformed(evt);
  }//GEN-LAST:event_addChannelsRegexActionPerformed

  private void singleChannelRegexActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_singleChannelRegexActionPerformed
    if (singleChannelRegex.getText().equals("")) return;    
    int size = 0;
    singleChannelComboBox.removeAllItems();
    ArrayList<Channel> channels = new ArrayList<>();
    try (Statement stmt = DBConnectionThread.getThread("edge").getNewStatement(false)) {
        String sql = "SELECT * FROM edge.channel WHERE channel REGEXP '"
                + singleChannelRegex.getText() + "' ORDER BY channel";
        if (singleChannelRegex.getText().length() >= 5 && singleChannelRegex.getText().substring(0, 5).equalsIgnoreCase("WHERE")) {
          sql = "SELECT * FROM edge.channel " + singleChannelRegex.getText() + " ORDER BY channel";
        }        
        try (ResultSet rs = stmt.executeQuery(sql)) { 
          while (rs.next()) {
            channels.add(new Channel(rs));
            size++;
          }
        }
    } catch (SQLException e) {
      e.printStackTrace();
      Util.prt("SQL error getting channels\n" + e.getMessage() + "\n" + e.getErrorCode() + "\n" + e.getSQLState());
    }  
    if (size != 0) {
      for (Channel channel : channels) {
        singleChannelComboBox.addItem(channel);
      }           
    }
//    singleChannelComboBox.revalidate();
//    singleChannelComboBox.repaint();
//    singleChannelPanel.revalidate();
//    singleChannelPanel.repaint();
    
  }//GEN-LAST:event_singleChannelRegexActionPerformed

  private void singleChannelComboBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_singleChannelComboBoxActionPerformed
    Channel channel = singleChannelComboBox.getItemAt(singleChannelComboBox.getSelectedIndex());
    singlePickersSelectPanel.removeAll();
    int height=0;
    int width=0;  
    if (channel != null) {
      for (Map.Entry<String,Integer> lPicker : (loadPickersForChannel(channel.getID())).entrySet()) {      
        PickerChannelsSubPanel cp = new PickerChannelsSubPanel(this,channel.getID(),channel.getChannel(),
                                        lPicker.getValue(),lPicker.getKey());
        height += cp.getPreferredSize().getHeight()+ 5;      
        width = (int) cp.getPreferredSize().getWidth();
        cp.setText(lPicker.getKey());
        cp.setSelected(true);
        singlePickersSelectPanel.add(cp);      
      }
    }
    singlePickersSelectPanel.invalidate();
    if (height ==  0) {
      singlePickersSelectPanel.setPreferredSize(singlePickersScrollPane.getPreferredSize());      
    } else { 
      singlePickersSelectPanel.setPreferredSize(new Dimension((int) width, (int) height / 2 + 30 ));
      singlePickersSelectPanel.setMinimumSize(new Dimension((int) width, (int) height / 2 + 30 ));
    }
    singlePickersSelectPanel.validate();
    singlePickersSelectPanel.repaint();        
    validate();
    
  }//GEN-LAST:event_singleChannelComboBoxActionPerformed

  private void singleChannelUpdateButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_singleChannelUpdateButtonActionPerformed
    try {
      int count = 0;
      PreparedStatement prepStmt = DBConnectionThread.getConnection(DBConnectionThread.getDBSchema())
            .prepareStatement("DELETE FROM pickerchannels WHERE pickerID = ? and channelID = ?");
      for (java.awt.Component csp : singlePickersSelectPanel.getComponents()) {
        if (!((PickerChannelsSubPanel) csp).getSelected()) {
          prepStmt.setInt(1, ((PickerChannelsSubPanel)csp).getPickerId());
          prepStmt.setInt(2, ((PickerChannelsSubPanel)csp).getChannelId());
          prepStmt.addBatch();
          count++;
        }
      }
      if (count > 0) {
        prepStmt.executeBatch();
      }
    }  catch (SQLException e) {
      Util.SQLErrorPrint(e, "Updating Current Channels SQL failed.");          
    }  
    singleChannelComboBoxActionPerformed(null);       
  }//GEN-LAST:event_singleChannelUpdateButtonActionPerformed

  private void singleChannelResetButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_singleChannelResetButtonActionPerformed
    singleChannelComboBoxActionPerformed(null);
  }//GEN-LAST:event_singleChannelResetButtonActionPerformed
  
  
  // Variables declaration - do not modify//GEN-BEGIN:variables
  private javax.swing.JTextField ID;
  private javax.swing.JPanel addChannelsPanel;
  private javax.swing.JTextField addChannelsRegex;
  private javax.swing.JScrollPane addChannelsScrollPane;
  private javax.swing.JButton addRegexChannelsButton;
  private javax.swing.JPanel addSelectChannelsPanel;
  private javax.swing.JLabel addTotalChannels;
  private javax.swing.JButton addUpdate;
  private javax.swing.JTextArea args;
  private javax.swing.JPanel bottomStuff;
  private javax.swing.JTextField changeTo;
  private javax.swing.JPanel channelsPanel;
  private javax.swing.JPanel channelsSelectionPanel;
  private javax.swing.JTabbedPane channelsTabbedPane;
  private javax.swing.JTextField classname;
  private javax.swing.JPanel currentChannelsPanel;
  private javax.swing.JScrollPane currentChannelsScrollPane;
  private javax.swing.JLabel currentTotalChannels;
  private javax.swing.JButton deleselectAllCurrentChannelsButton;
  private javax.swing.JButton deleteItem;
  private javax.swing.JTextField error;
  private java.awt.GridBagLayout gridBagLayout1;
  private javax.swing.JLabel idLab;
  private javax.swing.JLabel jLabel1;
  private javax.swing.JLabel jLabel2;
  private javax.swing.JScrollPane jScrollPane1;
  private javax.swing.JScrollPane jScrollPane2;
  private javax.swing.JLabel labArgs;
  private javax.swing.JLabel labChange;
  private javax.swing.JLabel labClass;
  private javax.swing.JLabel labMain;
  private javax.swing.JLabel labTemplate;
  private javax.swing.JButton loadCWBPickerDefault;
  private javax.swing.JButton loadFPDefault;
  private javax.swing.JButton persistRegexChannels;
  private javax.swing.JComboBox picker;
  private javax.swing.JButton refreshCurrenChannelsButton;
  private javax.swing.JButton reload;
  private javax.swing.JButton resetAddChannelsButton;
  private javax.swing.JButton selectAllCurrentChannelsButton;
  private javax.swing.JComboBox<Channel> singleChannelComboBox;
  private javax.swing.JPanel singleChannelPanel;
  private javax.swing.JTextField singleChannelRegex;
  private javax.swing.JButton singleChannelResetButton;
  private javax.swing.JButton singleChannelUpdateButton;
  private javax.swing.JScrollPane singlePickersScrollPane;
  private javax.swing.JPanel singlePickersSelectPanel;
  private javax.swing.JTextArea template;
  private javax.swing.JButton updateCurrentChannels;
  // End of variables declaration//GEN-END:variables
  /** Creates new form PickerPanel */
  public PickerPanel() {
    initiating=true;
    initComponents();
    getJComboBox(picker);                // set up the key JComboBox
    picker.setSelectedIndex(-1);    // Set selected type
    initiating=false;
    //Setting the UnitIncrement on the scroll bar helps prevent CPU usage when scrolling with mouse wheel.
    this.currentChannelsScrollPane.getVerticalScrollBar().setUnitIncrement(30);
    this.addChannelsScrollPane.getVerticalScrollBar().setUnitIncrement(30);
    // USER:This is a good place for UC.Look(components) to get rid of the ugly Grey
    doInit();
    clearScreen();                    // Start with empty screen
  }
  
  //////////////////////////////////////////////////////////////////////////////
  // No USER: changes needed below here!
  //////////////////////////////////////////////////////////////////////////////
  /** Create a JComboBox of all of the items in the table represented by this panel
   *@return A New JComboBox filled with all row keys from the table
   */
 public static JComboBox getJComboBox() { JComboBox b = new JComboBox(); getJComboBox(b); return b;}
  /** Update a JComboBox to represent the rows represented by this panel
   *@param b The JComboBox to Update
   */
  public static void getJComboBox(JComboBox b) {
    b.removeAllItems();
    makePickers();
    for (int i=0; i< Picker.pickers.size(); i++) {
      b.addItem( Picker.pickers.get(i));
    }
    b.setMaximumRowCount(30);
  }
  
  /** Given a JComboBox from getJComboBox, set selected item to match database ID
   *@param b The JComboBox
   *@param ID the row ID from the database to select
   */
  public static void setJComboBoxToID(JComboBox b, int ID) {
    b.setSelectedIndex(-1);
    //Util.prt("PickerPanel.setJComboBoxToID id="+ID);
    for(int i=0; i<b.getItemCount(); i++) 
      if(((Picker) b.getItemAt(i)).getID() == ID) {
          b.setSelectedIndex(i);
          //Util.prt("PickerPanel.setJComboBoxToID id="+ID+" found at "+i);
      }
  }  
  
  /** Given a database ID find the index into a JComboBox consistent with this panel
   *@param ID The row ID from the database
   *@return The index in the JComboBox with this ID
   */
  public static int getJComboBoxIndex(int ID) {
    if(Picker.pickers == null) makePickers();
    for(int i=0; i<Picker.pickers.size(); i++) if( ID == Picker.pickers.get(i).getID()) return i;
    return -1;
  }
  /** Cause the main JComboBox to be reloaded. */
  public static void reload() {
    Picker.pickers = null;
    makePickers();
  }
  /** return a ArrayList with all of the Picker
   * @return The ArrayList with the picker
   */
  public static ArrayList<Picker> getPickerVector() {
    if(Picker.pickers == null) makePickers();
    return Picker.pickers;
  }    /* return a ArrayList with all of the Picker
   * @return The ArrayList with the picker
   */
  public static ArrayList<Picker> getPickerArrayList() {
    if(Picker.pickers == null) makePickers();
    return Picker.pickers;
  }
  /** Get the item corresponding to database ID
   *@param ID the database Row ID
   *@return The Picker row with this ID
   */
  public static Picker getPickerWithID(int ID) {
    if(Picker.pickers == null) makePickers();
    int i=getJComboBoxIndex(ID);
    if(i >= 0) return  Picker.pickers.get(i);
    else return null;
  }
  
  // This routine should only need tweeking if key field is not same as table name
  private static void makePickers() {
    if (Picker.pickers != null) return;
    Picker.makePickers();
  }
  
  // No changes needed
  private void find() {
    if(picker == null) return;
    if(initiating) return;
    Picker l;
    if(picker.getSelectedIndex() == -1) {
      if(picker.getSelectedItem() == null) return;
      l = newOne();
    } 
    else {
      l = (Picker) picker.getSelectedItem();
    }
      
    if(l == null) return;
    String p = l.getPicker();
    if(p == null) return;
    if(p.equals("")) {
      addUpdate.setEnabled(false);
      deleteItem.setEnabled(false);
      addUpdate.setText("Enter a Picker!");
    }
    p = p.toUpperCase();
    error.setBackground(Color.lightGray);
    error.setText("");
    try {
      clearScreen();          // set screen to known state
      oldOne(p);
      
      // set add/Update button to indicate an update will happen
      if(obj.isNew()) {
          clearScreen();
          ID.setText("NEW!");

          // Set up for "NEW" - clear fields etc.
          error.setText("NOT found - assume NEW");
          error.setBackground(UC.yellow);
          addUpdate.setText("Add "+p);
          addUpdate.setEnabled(true);
          deleteItem.setEnabled(false);
          clearChannelPanel();
      }
      else {
        addUpdate.setText("Update "+p);
        addUpdate.setEnabled(true);
        addUpdate.setEnabled(true);
        ID.setText(""+obj.getInt("ID"));    // Info only, show ID
        updateChannelPanels(obj.getID(),obj.getString("picker"));
      }
    }  
    // This is only thrown if something happens in oldone() new DBOject
    // Other than it being a new record.
    catch  (SQLException  E)                                 // 
    { Util.SQLErrorPrint(E,"Picker: SQL error getting Picker="+p);
    }
    
  }
  
  private void clearChannelPanel() {
    currentChannelsPanel.removeAll();
    currentChannelsPanel.revalidate();
    currentChannelsPanel.repaint();
  }
  
  private void updateChannelPanels(int pickerId,String pickerName) {
    currentChannelsPanel.removeAll();
    int height=0;
    int width=0;
    for (Map.Entry<String,Integer> channel : (loadChannelsForPicker(pickerId)).entrySet()) {      
      PickerChannelsSubPanel cp = new PickerChannelsSubPanel(this,channel.getValue(),channel.getKey(),
                                      pickerId,pickerName);
      height += cp.getPreferredSize().getHeight()+ 5;      
      width = (int) cp.getPreferredSize().getWidth();
      cp.setText(channel.getKey());
      cp.setSelected(true);
      currentChannelsPanel.add(cp);      
    }
    currentTotalChannels.setText("Total Channels: " + Integer.toString(currentChannelsPanel.getComponentCount()));
    currentChannelsPanel.invalidate();
    currentChannelsPanel.setPreferredSize(new Dimension((int) width, (int) height / 2 + 30 ));
    currentChannelsPanel.setMinimumSize(new Dimension((int) width, (int) height / 2 + 30 ));
    currentChannelsPanel.validate();
    currentChannelsPanel.repaint();        
    validate();
  }
  
  private Map<String,Integer> loadChannelsForPicker(int pickerId) {
    Map<String,Integer> channels = new TreeMap();
    try (Statement stmt = DBConnectionThread.getConnection(DBConnectionThread.getDBSchema()).createStatement() // used for query
                ) {
      String sql = "SELECT ch.channel channel, ch.ID id FROM edge.pickerchannels pc, channel ch where pc.pickerID = " + pickerId + 
              " and ch.ID = pc.channelID";    
      try (ResultSet rs = stmt.executeQuery(sql)) {
        while (rs.next()) {
          channels.put(rs.getString("channel"), rs.getInt("id"));          
        }
      }
    }  catch (SQLException e) {
      Util.SQLErrorPrint(e, "Loading Channels for picker=" + pickerId + "SQL failed");          
    }    
    return channels;
  }
  
  private Map<String,Integer> loadPickersForChannel(int channelId) {
    Map<String,Integer> pickers = new TreeMap();
    try (Statement stmt = DBConnectionThread.getConnection(DBConnectionThread.getDBSchema()).createStatement()) {
      String sql = "SELECT pk.picker picker, pk.ID id FROM edge.pickerchannels pc, picker pk where pc.channelID = " + channelId +
                   " and pk.ID = pc.pickerID";
      try (ResultSet rs = stmt.executeQuery(sql)) {
        while (rs.next()) {
          pickers.put(rs.getString("picker"), rs.getInt("id"));
        }
      }
    } catch (SQLException e) {
      Util.SQLErrorPrint(e, "Loading Pickers for channel=" + channelId + "SQL failed");          
    }    
    return pickers;
  }
  
  private void updateCurrentChannels() {
    try {
      int count = 0;
      PreparedStatement prepStmt = DBConnectionThread.getConnection(DBConnectionThread.getDBSchema())
            .prepareStatement("DELETE FROM pickerchannels WHERE pickerID = ? and channelID = ?");
      for (java.awt.Component csp : currentChannelsPanel.getComponents()) {
        if (!((PickerChannelsSubPanel) csp).getSelected()) {
          prepStmt.setInt(1, ((PickerChannelsSubPanel)csp).getPickerId());
          prepStmt.setInt(2, ((PickerChannelsSubPanel)csp).getChannelId());
          prepStmt.addBatch();
          count++;
        }
      }
      if (count > 0) {
        prepStmt.executeBatch();
      }
    }  catch (SQLException e) {
      Util.SQLErrorPrint(e, "Updating Current Channels SQL failed.");          
    }   
  }

  /** This main displays the form Pane by itself
   *@param template command line template ignored*/
  public static void main(String template[]) {
    DBConnectionThread jcjbl;
    Util.init(UC.getPropertyFilename());
    UC.init();
    try {
        // Make test DBconnection for form
      jcjbl = new DBConnectionThread(DBConnectionThread.getDBServer(),DBConnectionThread.getDBCatalog(),
              UC.defaultUser(),UC.defaultPassword(), true, false, DBConnectionThread.getDBSchema(), DBConnectionThread.getDBVendor());
      if(!DBConnectionThread.waitForConnection(DBConnectionThread.getDBSchema()))
        if(!DBConnectionThread.waitForConnection(DBConnectionThread.getDBSchema()))
          if(!DBConnectionThread.waitForConnection(DBConnectionThread.getDBSchema()))
          {Util.prt("COuld not connect to DB "+jcjbl); System.exit(1);}
      Show.inFrame(new PickerPanel(), UC.XSIZE, UC.YSIZE);
    }
    catch(InstantiationException e) {}


  }
}
