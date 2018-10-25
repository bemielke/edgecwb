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
import gov.usgs.anss.util.FUtil;
import gov.usgs.anss.util.UC;
import gov.usgs.anss.util.Util;
import java.awt.Color;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import javax.swing.JComboBox;
import javax.swing.SwingUtilities;
/**
 *
 * requirements : 
 * The key for the file must be the name of the table and of the
 * JComboBox variable this variable is called "edgeMomInstance" in the initial form
 * The table name and key name should match and start lower case (edgeMomInstance).
 * The Class here will be that same name but with upper case at beginning(EdgeMomInstanceSetup).
 * <br> 1)  Rename the location JComboBox to the "key" (edgeMomInstance) value.  This should start Lower
 *      case.
 *<br>  2)  The table name should match this key name and be all lower case.
 *<br>  3)  Change all EdgeMomInstanceSetup to ClassName of underlying data (case sensitive!)
 *<br>  4)  Change all edgeMomInstance key value (the JComboBox above)
 *<br>  5)  clearScreen should update swing components to new defaults
 *<br>  6)  chkForm should check all field for validity.  The errorTrack variable is
 *      used to track multiple errors.  In FUtil.chk* handle checks of the various
 *      variable types.  Date, double, and IP etc return the "canonical" form for 
 *      the variable.  These should be stored in a class variable so the latest one
 *      from "chkForm()" are available to the update writer.
 *<br>  7) newone() should be updated to create a new instance of the underlying data class.
 *<br>  8) oldone() get data from database and update all swing elements to correct values
 *<br>  9) addUpdateAction() add data base set*() operations updating all fields in the DB
 *<br> 10) EdgeMomInstanceSetupPanel() constructor - good place to change backgrounds using UC.Look() any
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
 * <br>  fieldJcomboBox.setSelectedItem(obj1.getString("fieldName"));
<br>  (This sets the JComboBox to the Item matching the string)
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
public class EdgeMomInstanceSetupPanel extends javax.swing.JPanel {
  //USER: set this for the upper case conversion of keys.
  private final boolean keyUpperCase=false;    // set this true if all keys must be upper case
  //USER: Here are the local variables
  // NOTE : here define all variables general.  "ArrayList edgemomInstanceSetups" is used for main Comboboz
  boolean building=false;
  PopupForm popup = new PopupForm(this);
   //USER: Here are the local variables
  int intpriority;
    private void doInit() {
  // Good place to add UC.Looks()
    deleteItem.setVisible(true);
    reload.setVisible(true);
    idLab.setVisible(true);
    ID.setVisible(true);
    changeTo.setVisible(false);
    labChange.setVisible(false);
    UC.Look(this);                    // Set color background
    UC.Look(configPanel);
    UC.Look(threadPanel);
    UC.Look(disable);
    UC.Look(displayAll);
    UC.Look(displayHelp);
    UC.Look(threadPanel);
    UC.Look(configPanel);
    UC.Look(tabPane);
    setEdgeThreadJCombo();
  }
  private void doAddUpdate() throws SQLException {
    // USER : Set all of the fields in data base.  obj1.setInt("thefield", ivalue);
  }
  private void doOldOne() throws SQLException {
      //USER: Here set all of the form fields to data from the DBObject
      //      edgeMomInstance.setText(obj1.getString("EdgeMomInstanceSetup"));
      // Example : description.setText(obj1.getString("description"));

  }
  private void setEdgeThreadJCombo() {
     edgethreadID.removeAllItems();
    for(Object obj1:EdgethreadPanel.getEdgethreadVector()) {
      Edgethread item = (Edgethread) obj1;
      if(displayAll.isSelected()) {
        edgethreadID.addItem(item);
      }
      else {
        if(item.getEdgeMom() != 0) edgethreadID.addItem(item);
      }
    }
  }   
  
  public void externalReload(int instanceId) {
    EdgemomInstanceSetup.edgemomInstanceSetups = null;
    buildEdgeMomInstanceSetupCombo("Add/Update");
    EdgeMomInstancePanel.getJComboBox(instanceID);     
    if (instanceId < 0 ) {
      clearScreen();
    } else {     
      EdgeMomInstancePanel.setJComboBoxToID(instanceID,instanceId);
    }    
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
   Util.prta("chkForm EdgemomInstanceSetup item="+edgemominstancesetup.getSelectedIndex());

   intpriority = FUtil.chkInt(priority,err,true);
    if(!configfile.getText().isEmpty() || !config.getText().isEmpty()) {
      if(edgefile.getSelectedIndex() >= 0) {
        err.set();
        err.appendText("IllegalLExternal Configuration file and ConfigFile/Config content!");
        edgefile.setSelectedIndex(-1);
      }
    }
    if(edgemominstancesetup.getSelectedIndex() >= 0) {
      EdgemomInstanceSetup th = (EdgemomInstanceSetup) edgemominstancesetup.getSelectedItem();
      if(th != null) {
        for(EdgemomInstanceSetup setup: EdgemomInstanceSetup.edgemomInstanceSetups) {
          if(configfile.getText().length() > 0 && 
                  configfile.getText().equals(setup.getConfigFilename()) && 
                  !configfile.getText().equalsIgnoreCase("/NONE") &&
                  th.getConfigFilename().equalsIgnoreCase(setup.getConfigFilename()) &&
                  ((EdgemomInstanceSetup) edgemominstancesetup.getSelectedItem()).getID() != setup.getID() &&
                  setup.getConfigContent().length() > 1 && 
                  !config.getText().startsWith("<<<<<<")) {
            err.set();
            err.appendText("Multi config for config file "+setup.toString());
          }
        }
      }
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
    addUpdate.setText("Enter a EdgeMomInstanceSetup");
    deleteItem.setEnabled(false);
    changeTo.setText("");
    // Do not change
    ID.setText("");
    edgemominstancesetup.setSelectedIndex(-1);
    tag.setText("");
    priority.setText("");
    edgethreadID.setSelectedIndex(-1);
    args.setText("");
    logfile.setText("");
    comment.setText("");
    edgefile.setSelectedIndex(-1);
    configfile.setText("");
    config.setText("");
    config.setEditable(true);
    config.setBackground(Color.WHITE);
    error.setText("");
    disable.setSelected(false);
    lblFile.setVisible(false);
    edgefile.setVisible(false);

    //USER: Clear all fields for this form, this sets "defaults" for new screen
    // description.setText("");    
    //USER: Clear all fields for this form, this sets "defaults" for new screen
    // description.setText("");

  }


  /**********************  NO USER MODIFICATION BELOW HERE EXCEPT FOR ACTIONS **************/
  DBObject obj;
  ErrorTrack err=new ErrorTrack();
  boolean initiating=false;

  
  private EdgemomInstanceSetup newOne() {
      setConfigfileTextArea();
      
     return new EdgemomInstanceSetup(0, "DUMMY", "", //, more
            0,  0, 0, "", "", "",0, "", "", 0
            );   
  }
  
  private void oldOne(String p) throws SQLException {
    obj = new DBObject(DBConnectionThread.getThread(DBConnectionThread.getDBSchema()), 
            DBConnectionThread.getDBSchema(), 
            "instancesetup","ID",p);

    if(obj.isNew()) {
      deleteItem.setEnabled(true);
      Util.prt("object is new"+p);
    }
    else {
      //USER: Here set all of the form fields to data from the DBObject
      //      edgemominstancesetup.setText(obj1.getString("EdgemomInstanceSetup"));
      // Example : description.setText(obj1.getString("description"));
      edgemominstancesetup.setSelectedItem(tag);
      tag.setText(obj.getString("tag"));
//      ID.setText(""+obj1.getInt("ID"));
      int r = obj.getInt("instanceID");
      if (r==0) instanceID.setSelectedIndex(-1);
      else EdgeMomInstancePanel.setJComboBoxToID(instanceID, r);
      priority.setText(""+obj.getInt("priority"));
      int e = obj.getInt("edgethreadID");
      if (e==0) edgethreadID.setSelectedIndex(-1);
      else EdgethreadPanel.setJComboBoxToID(edgethreadID, e);
      args.setText(obj.getString("args"));
      disable.setSelected(obj.getInt("disabled") != 0);
      logfile.setText(obj.getString("logfile"));
      comment.setText(obj.getString("comment"));
      //file2.setText(obj1.getString("filename"));
      if(obj.getInt("edgefileid") >= 0) EdgeFilePanel.setJComboBoxToID(edgefile, obj.getInt("edgefileid"));
      else edgefile.setSelectedIndex(-1);
      configfile.setText(obj.getString("configfile"));
      config.setText(obj.getString("config"));
      // If the config is empty, look to see if it is duplicated from somewhere else and warn the user
      deleteItem.setEnabled(true);
      
      //changeInstance.setSelectedIndex(-1);
      EdgeMomInstancePanel.setJComboBoxToID(changeInstance, obj.getInt("instanceid"));
        
        
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
    labMain = new javax.swing.JLabel();
    edgemominstancesetup = getJComboBox();
    error = new javax.swing.JTextField();
    bottomStuff = new javax.swing.JPanel();
    addUpdate = new javax.swing.JButton();
    labChange = new javax.swing.JLabel();
    changeTo = new javax.swing.JTextField();
    deleteItem = new javax.swing.JButton();
    reload = new javax.swing.JButton();
    idLab = new javax.swing.JLabel();
    ID = new javax.swing.JTextField();
    labInstanceID = new javax.swing.JLabel();
    instanceID = gov.usgs.edge.config.EdgeMomInstancePanel.getJComboBox();
    tabPane = new javax.swing.JTabbedPane();
    threadPanel = new javax.swing.JPanel();
    lblPriority = new javax.swing.JLabel();
    priority = new javax.swing.JTextField();
    lblEdgeThreadID = new javax.swing.JLabel();
    displayAll = new javax.swing.JRadioButton();
    edgethreadID = gov.usgs.edge.config.EdgethreadPanel.getJComboBox();
    lblArgs = new javax.swing.JLabel();
    jScrollPane1 = new javax.swing.JScrollPane();
    args = new javax.swing.JTextArea();
    lblLogfile = new javax.swing.JLabel();
    logfile = new javax.swing.JTextField();
    lblComment = new javax.swing.JLabel();
    comment = new javax.swing.JTextField();
    disable = new javax.swing.JCheckBox();
    labTag = new javax.swing.JLabel();
    tag = new javax.swing.JTextField();
    lblChangeRole = new javax.swing.JLabel();
    changeInstance = gov.usgs.edge.config.EdgeMomInstancePanel.getJComboBox();
    displayHelp = new javax.swing.JCheckBox();
    configPanel = new javax.swing.JPanel();
    labConfigFile = new javax.swing.JLabel();
    configfile = new javax.swing.JTextField();
    labConfig = new javax.swing.JLabel();
    scrollConfig = new javax.swing.JScrollPane();
    config = new javax.swing.JTextArea();
    clone = new javax.swing.JToggleButton();
    lblFile = new javax.swing.JLabel();
    edgefile = EdgeFilePanel.getJComboBox();
    cloneFrom = EdgeMomInstanceSetupPanel.getJComboBox();

    setLayout(new java.awt.GridBagLayout());

    labMain.setText("Tag (old or new) :\n");
    labMain.setToolTipText("This button commits either a new item or the changes for an updating item.  Its label with change to reflect that.");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 3;
    add(labMain, gridBagConstraints);

    edgemominstancesetup.setEditable(true);
    edgemominstancesetup.setToolTipText("Select an item to edit in the JComboBox or type in the name of an item.   If the typed name does not match an existing item, an new item is assumed.");
    edgemominstancesetup.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        edgemominstancesetupActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 3;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(edgemominstancesetup, gridBagConstraints);

    error.setEditable(false);
    error.setColumns(40);
    error.setMinimumSize(new java.awt.Dimension(488, 22));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 2;
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
    gridBagConstraints.gridy = 13;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(bottomStuff, gridBagConstraints);

    labInstanceID.setText("Select Instance-role:");
    add(labInstanceID, new java.awt.GridBagConstraints());

    instanceID.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        instanceIDActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(instanceID, gridBagConstraints);

    tabPane.setMinimumSize(new java.awt.Dimension(600, 515));
    tabPane.setPreferredSize(new java.awt.Dimension(600, 515));

    threadPanel.setName("Thread Config"); // NOI18N
    threadPanel.setLayout(new java.awt.GridBagLayout());

    lblPriority.setText("Priority:");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 4;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_END;
    threadPanel.add(lblPriority, gridBagConstraints);

    priority.setColumns(3);
    priority.setToolTipText("Set the priority of this thread.   If blank or zero, \"NORM_PRIORITY\" of 5 is selected.  Lowest priority is 1 and highest is 10.");
    priority.setMinimumSize(new java.awt.Dimension(100, 28));
    priority.setPreferredSize(new java.awt.Dimension(100, 28));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 4;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
    threadPanel.add(priority, gridBagConstraints);

    lblEdgeThreadID.setText("EdgeThread:");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 5;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_END;
    threadPanel.add(lblEdgeThreadID, gridBagConstraints);

    displayAll.setText("Show non-EdgeMom threads");
    displayAll.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        displayAllActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 5;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_END;
    threadPanel.add(displayAll, gridBagConstraints);

    edgethreadID.setToolTipText("<html>\n<pre>\nSelect an EdgeThread type to run.  All of the normal threads started in an EdgeMom are of type EdgeThread.\n\nIf you need help with configuring a particular type of thread, select it here and then click the \"Display Help\"\nbutton below.\n</pre>\n</html>");
    edgethreadID.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        edgethreadIDActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 5;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
    threadPanel.add(edgethreadID, gridBagConstraints);

    lblArgs.setText("Arguments:");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 6;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_END;
    threadPanel.add(lblArgs, gridBagConstraints);

    jScrollPane1.setMinimumSize(new java.awt.Dimension(475, 80));
    jScrollPane1.setPreferredSize(new java.awt.Dimension(475, 80));

    args.setColumns(75);
    args.setFont(new java.awt.Font("Monospaced", 0, 10)); // NOI18N
    args.setLineWrap(true);
    args.setRows(3);
    args.setTabSize(2);
    args.setToolTipText("<html>\nArguments for this EdgeThread type.  \n<br>These are documented in the javadoc and can be view in this GUI by selecting \"Display Help\".\n<br>The argument line can contain the various symbols for substitution which varies by EdgeThread :\n&,_,-  : often replace spaces as spaces cannot appear in any argument (quotes are not support)\n/ : often replaces colon as colon is used in the EdgeMom setup files and hence cannot be in the arguments.\n\n<br>\n<PRE>\nSymbols that can be present and substituted :\n$INSTANCE  replaced with the instance setting.  Useful in ring file names.\n$ROLE replaced with the role or primary role name where this instance is normally run.\n$ROLEIP replace with the role or primary role IP address where this instance is normally run.\n$SERVER replaced with the physical node/cpu system name (not the role, the real server name).\n$INSTANCEFAILOVERIP replace with the IP address of the instance failover,if any.\n$INSTANCEFAILOVER replace with role name of the instance failover, if any.\n$INSTANCEIP-NN#I replace with IP for the normal role for instance NN#I (EdgeBlockClients!)          \n$INSTANCEFAILOVERIP-NN#I replace with failover IP for instance NN#I.\n$INSTANCEFAILOVER-NN#I replace with role name for failover of instance NN#I.\n</PRE>\n</html>\n");
    jScrollPane1.setViewportView(args);

    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 6;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
    threadPanel.add(jScrollPane1, gridBagConstraints);

    lblLogfile.setText("Log File:");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 7;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_END;
    threadPanel.add(lblLogfile, gridBagConstraints);

    logfile.setMinimumSize(new java.awt.Dimension(300, 28));
    logfile.setPreferredSize(new java.awt.Dimension(300, 28));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 7;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
    threadPanel.add(logfile, gridBagConstraints);

    lblComment.setText("Comment:");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 8;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_END;
    threadPanel.add(lblComment, gridBagConstraints);

    comment.setMinimumSize(new java.awt.Dimension(500, 28));
    comment.setPreferredSize(new java.awt.Dimension(500, 28));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 8;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LAST_LINE_START;
    threadPanel.add(comment, gridBagConstraints);

    disable.setText("Disable Thread");
    disable.setToolTipText("If selected, this thread is disabled - a comment \"#\" will be on its line in any edgemom*.setup file.");
    disable.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        disableActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 14;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
    threadPanel.add(disable, gridBagConstraints);

    labTag.setText("Tag:");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 15;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_END;
    threadPanel.add(labTag, gridBagConstraints);

    tag.setToolTipText("<html>\nThe TAG is used to order the lines in the edgemom*.setup files.  \n<br>It should be brief but descriptive of the threads purpose.  \n<br>If no output log file is set, this tab will appear in all log lines from this thread in the EdgeMom default log.\n</html>");
    tag.setMinimumSize(new java.awt.Dimension(104, 28));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 15;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
    threadPanel.add(tag, gridBagConstraints);

    lblChangeRole.setText("Move to Instance:");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 16;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_END;
    threadPanel.add(lblChangeRole, gridBagConstraints);

    changeInstance.setToolTipText("<html>\nIf another instances (nn#i) is selected here, the thread is moved to run as part of that instance and removed from its current instance.  \n<br>This is a permanent move.\n</html>");
    changeInstance.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        changeInstanceActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 16;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
    threadPanel.add(changeInstance, gridBagConstraints);

    displayHelp.setText("Display Help");
    displayHelp.setToolTipText("If selected, a dialog box will pop up with the javadoc for this EdgeThread type.  This is useful for setting the arguments or reviewing the purpose of a thread.");
    displayHelp.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        displayHelpActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 20;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
    threadPanel.add(displayHelp, gridBagConstraints);

    tabPane.addTab("Thread Config", threadPanel);

    configPanel.setName("Config File"); // NOI18N

    labConfigFile.setText("Config Filename:");
    configPanel.add(labConfigFile);

    configfile.setColumns(30);
    configfile.setToolTipText("<html>\n<pre>\nPut the path/file name of a configuration file used by this thread.  The contents of the file are from \"Config Contents\". \nThe path/filename will be relative to the account directory so, 'SEEDLINK/my_config.setup' is the right form. \n\nThis is the preferred method for creating a setup file associated with this Edge Thread.\n\nIf either of the Config File or Config Content is not empty, it is not possible to use the 'External Config File' option.\n</pre>\n</html>\n");
    configfile.setMinimumSize(new java.awt.Dimension(300, 28));
    configfile.addFocusListener(new java.awt.event.FocusAdapter() {
      public void focusLost(java.awt.event.FocusEvent evt) {
        configfileFocusLost(evt);
      }
    });
    configPanel.add(configfile);

    labConfig.setText("Config Contents:");
    configPanel.add(labConfig);

    scrollConfig.setMinimumSize(new java.awt.Dimension(575, 400));
    scrollConfig.setPreferredSize(new java.awt.Dimension(575, 400));

    config.setColumns(100);
    config.setFont(new java.awt.Font("Monaco", 0, 10)); // NOI18N
    config.setRows(300);
    config.setToolTipText("<html>\n<PRE>\nThis is the contents of the configuration file \"Config Filename\".    Examples: 'SEEDLINK/my_config.setup'.\n\nIf this field and the 'Config Filename' field are not empty, then the 'External Config File' field is disabled.\n\n\nSymbols that can be present and substituted :\n$INSTANCE  replaced with the instance setting.  Useful in ring file names.\n$ROLE replaced with the role name or primary role name\n$SERVER replaced with the physical node/cpu system name.\n</PRE>\n</html>\n");
    scrollConfig.setViewportView(config);

    configPanel.add(scrollConfig);

    tabPane.addTab("Setup/Config File", configPanel);

    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 10;
    add(tabPane, gridBagConstraints);

    clone.setText("Clone From");
    clone.setToolTipText("<html>\nWhen pushed, the thread selected at the right is copied to this instance.  \n<br>The screen is updated for the new thread in this instance for editing.\n<br>Useful to use prior configured threads to be a starting point for a new configuration.\n</html>");
    clone.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        cloneActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 17;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_END;
    add(clone, gridBagConstraints);

    lblFile.setText("External Config File:");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 9;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_END;
    add(lblFile, gridBagConstraints);

    edgefile.setToolTipText("<html>\n<pre>\nSelect an external file maintained through the \"EdgeFile\" tab.   This should only be used for files\nthat need to be created, but are not in any EdgeMom configuration thread.  An example of this\nis querymom.setup files which will be in the External Config File/table using the \nquerymom.setup.$ROLE.$ACCOUNT filename so the file ends up in the right place.  This convention\nfor naming files determines if the file will be copied, and where it will be copied.\n\nThis should NEVER be used for setup files that are for one (or possible more - see below), \nEdgeMom configured threads.\n\nIt is much preferable to create any setup file for a thread using the \"Config Filename\" and \"ConfigContent\".\nIf you want to \"share\" a setup file between multiple threads (like for a contingency site), then\non the \"sharing\" side enter only the \"ConfigFilename\", and make sure the content is EMPTY.\nAt the contingency site the file will be created from the other role when all of the configuration\nfiles for all roles are built.  The contingency site will not create a file because the content is EMPTY.\nThe correct file will be moved from the configuration area to the desired diretory on BOTH sites.\n</pre>\n</html>  ");
    edgefile.addFocusListener(new java.awt.event.FocusAdapter() {
      public void focusGained(java.awt.event.FocusEvent evt) {
        edgefileFocusGained(evt);
      }
    });
    edgefile.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        edgefileActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 9;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
    add(edgefile, gridBagConstraints);

    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 17;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
    add(cloneFrom, gridBagConstraints);
  }// </editor-fold>//GEN-END:initComponents

  private void addUpdateActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_addUpdateActionPerformed
    // Add your handling code here:
    
    // Do not change
    if(initiating) return;
    if(chkForm()) return;
    try {
      String p = tag.getText();
      //p = p.toUpperCase();                  // USER : drop this to allow Mixed case
      if(!obj.isNew()) obj.refreshRecord();        // Refresh the result set so the sets will not fail on stale one
      obj.setString("tag",p);

      
      // USER : Set all of the fields in data base.  obj1.setInt("thefield", ivalue);
      obj.setInt("instanceID",  ((EdgeMomInstance) instanceID.getSelectedItem()).getID());
      obj.setInt("priority",  intpriority);
      obj.setString("args", args.getText());
      obj.setInt("disabled", (disable.isSelected()? 1 : 0));
      obj.setString("logfile", logfile.getText());
      obj.setString("comment", comment.getText());
//      obj1.setString("ipadr", stripadr);
      obj.setInt("edgethreadID",  ((Edgethread) edgethreadID.getSelectedItem()).getID());
      //obj.setString("filename", file2.getText());
      obj.setInt("edgefileid", (edgefile.getSelectedIndex() == -1? 0: ((EdgeFile) edgefile.getSelectedItem()).getID()));
      if(changeInstance.getSelectedIndex() >=0) obj.setInt("instanceid", ((EdgeMomInstance) changeInstance.getSelectedItem()).getID());
      obj.setString("configfile", configfile.getText());
      String [] s = config.getText().split("\n");
      if(s.length == 1 && s[0].contains("<<<<<")) obj.setString("config","");
      else obj.setString("config", Util.chkTrailingNewLine(config.getText()));
      config.setCaretPosition(0);
      // Do not change
      obj.updateRecord();
      
      // If we update something, we need to mark role as having changes so reconfiguration takes place
      Util.prt("edge.prop update instance="+instanceID.getSelectedItem());
      try {
        DBConnectionThread.getThread("edge").executeUpdate("UPDATE edge.role set hasdata=1 where ID="+((EdgeMomInstance) instanceID.getSelectedItem()).getRoleID());
      }
      catch(SQLException e) {
        Util.prt("Could not set hasdata for role="+instanceID.getSelectedItem()+" e="+e);
      }
      EdgemomInstanceSetup.edgemomInstanceSetups=null;       // force reload of combo box
      EdgeMomInstanceSetupPanel.getJComboBox(cloneFrom);
      cleanCloneFrom(cloneFrom);
      buildEdgeMomInstanceSetupCombo("Add/Update");
      clearScreen();
      tag.setText("");
    }  catch  (SQLException  E)
    { Util.SQLErrorPrint(E,"edgemominstancesetup: update failed partno="+edgemominstancesetup.getSelectedItem());
      error.setText("Could Not Update File!");
      error.setBackground(UC.red);
      addUpdate.setEnabled(true);
    }      
  }//GEN-LAST:event_addUpdateActionPerformed

  private void edgemominstancesetupActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_edgemominstancesetupActionPerformed
    // Add your handling code here:
   if(!building) find(false);
 
    setConfigfileTextArea();
  }//GEN-LAST:event_edgemominstancesetupActionPerformed

private void deleteItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_deleteItemActionPerformed
  // Delete the currently selected item.
  try {
    obj.deleteRecord();
    buildEdgeMomInstanceSetupCombo("delete");
    Util.prt("Objected deleted obj="+obj);
    if(edgemominstancesetup.getItemCount() > 0) edgemominstancesetup.setSelectedIndex(0);
    else edgemominstancesetup.setSelectedIndex(-1);
  }
  catch(SQLException e) {
    Util.prt("Attempt to delete record failed!"+e);
  }
}//GEN-LAST:event_deleteItemActionPerformed

private void reloadActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_reloadActionPerformed
  EdgeMomInstancePanel.getJComboBox(instanceID);
  clearScreen();
}//GEN-LAST:event_reloadActionPerformed

  private void edgefileActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_edgefileActionPerformed
    EdgeFile f = (EdgeFile) edgefile.getSelectedItem();
    if(f != null)
      if(f.getEdgeFile().startsWith("/NONE")) edgefile.setSelectedIndex(-1);
    chkForm();
  }//GEN-LAST:event_edgefileActionPerformed

  private void instanceIDActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_instanceIDActionPerformed
    buildEdgeMomInstanceSetupCombo("role");
    
    if(edgemominstancesetup.getItemCount() >= 1) 
      edgemominstancesetup.setSelectedIndex(0); 
    else edgemominstancesetup.setSelectedIndex(-1);

  }//GEN-LAST:event_instanceIDActionPerformed

  private void cloneActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_cloneActionPerformed
    if(cloneFrom.getSelectedIndex() != -1) {
      edgemominstancesetup.setSelectedIndex(-1);      // force a new one
      find(true);
      EdgemomInstanceSetup setup = (EdgemomInstanceSetup) cloneFrom.getSelectedItem();
      tag.setText(setup.getTag());
      args.setText(setup.getArgs().replaceAll("\n",""));
      disable.setSelected(false);
      priority.setText(""+setup.getPriority());
      EdgethreadPanel.setJComboBoxToID(edgethreadID, setup.getEdgethreadID());
      logfile.setText(setup.getLogfile());
      comment.setText(setup.getComment());
      //file2.setText(setup.getFilename());
      edgefile.setSelectedIndex(-1);
      configfile.setText(setup.getConfigFilename());
      config.setText("");
      EdgeMomInstancePanel.setJComboBoxToID(changeInstance, ((EdgeMomInstance)instanceID.getSelectedItem()).getID());
      config.setText("<<<<<<<< Filename is maintained in "+setup.getTag()+" "+setup.getInstance()+" >>>>>>>\n");
      config.setEditable(false);
      config.setBackground(Color.LIGHT_GRAY);    chkForm();
    }
    
  }//GEN-LAST:event_cloneActionPerformed

  private void displayHelpActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_displayHelpActionPerformed
    if (displayHelp.isSelected()) popup.displayForm(true); 
    else popup.displayForm(false);
  }//GEN-LAST:event_displayHelpActionPerformed

  private void edgethreadIDActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_edgethreadIDActionPerformed
    if(popup.getText().equals("") && !initiating) {
      java.awt.Frame f1 = (java.awt.Frame) SwingUtilities.windowForComponent((javax.swing.JPanel) this);
      if(f1 != null) {
        Util.prt("Set location of popup to "+Math.max(0, f1.getX() - 400)+","+f1.getY());
        popup.setLocation(Math.max(0, f1.getX() - 400), f1.getY());
      }
    }
    if(edgethreadID.getSelectedIndex() != -1) {
      popup.setContentHtml(true);
      popup.setText( "<html>\n"+((Edgethread) edgethreadID.getSelectedItem()).getHelp()+"</html>\n");
    }
  }//GEN-LAST:event_edgethreadIDActionPerformed

  private void edgefileFocusGained(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_edgefileFocusGained
    EdgeFilePanel.getJComboBox(edgefile);
    EdgeFilePanel.setJComboConfigOnly(edgefile);
  }//GEN-LAST:event_edgefileFocusGained

  private void changeInstanceActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_changeInstanceActionPerformed
    // TODO add your handling code here:
  }//GEN-LAST:event_changeInstanceActionPerformed

  private void disableActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_disableActionPerformed
    // TODO add your handling code here:
  }//GEN-LAST:event_disableActionPerformed

  private void displayAllActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_displayAllActionPerformed
    setEdgeThreadJCombo();
  }//GEN-LAST:event_displayAllActionPerformed

  private void configfileFocusLost(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_configfileFocusLost
    // if the user put in a filename that already exists, then show that he has done that and make the contents no editable.
    setConfigfileTextArea();
    chkForm();
  }//GEN-LAST:event_configfileFocusLost
  private void setConfigfileTextArea() {
    config.setEditable(true);
    config.setBackground(Color.WHITE);
    if(edgemominstancesetup.getSelectedIndex() < 0) return;
    EdgemomInstanceSetup th = (EdgemomInstanceSetup) edgemominstancesetup.getSelectedItem();
    if(th == null) return;
    for(EdgemomInstanceSetup setup: EdgemomInstanceSetup.edgemomInstanceSetups) {
      if(configfile.getText().length() > 0 && !configfile.getText().equalsIgnoreCase("/NONE") &&
              configfile.getText().equals(setup.getConfigFilename()) && 
              th.getConfigFilename().equalsIgnoreCase(setup.getConfigFilename()) &&
              th.getID() != setup.getID() &&
              setup.getConfigContent().length() > 1) {
        config.setText("<<<<<<<< Filename is maintained in "+setup.getTag()+" "+setup.getInstance()+" >>>>>>>\n");
        config.setEditable(false);
        config.setBackground(Color.LIGHT_GRAY);
      }
    }
    //For SeedLinkClients that use SeedLinkConfigBuilder with the "-cfg" option their setup files
    //will be automatically generated and should not be editted. The setup files are loaded back
    //into the database using the GetSeedLinkAutoConfig process.
    if (config.getText().contains("#Automatically created")){
      config.setEditable(false);
      config.setBackground(Color.LIGHT_GRAY);
    }
  }

  private synchronized void  buildEdgeMomInstanceSetupCombo(String where) {
  EdgemomInstanceSetup.edgemomInstanceSetups = null;
  building=true;
  makeEdgeMomInstanceSetups();
  edgemominstancesetup.removeAllItems();
  if(instanceID.getSelectedIndex() == -1 ) return; // must have an account and role
  for (EdgemomInstanceSetup setup : EdgemomInstanceSetup.edgemomInstanceSetups) {
    if(setup.getInstanceID() == ((EdgeMomInstance) instanceID.getSelectedItem()).getID()) {
      edgemominstancesetup.addItem(setup);
    }
  }
  if(edgemominstancesetup.getItemCount() > 0) 
    edgemominstancesetup.setSelectedIndex(0);

  building=false;
}
private void cleanCloneFrom(JComboBox cloneFrom) {
  for(int i=cloneFrom.getItemCount()-1; i>=0; i--) {
    for(int j=i-1; j>=0; j--) {
      EdgemomInstanceSetup item1 = (EdgemomInstanceSetup) cloneFrom.getItemAt(i);
      EdgemomInstanceSetup item2 = (EdgemomInstanceSetup) cloneFrom.getItemAt(j);
      if(item1.getCommandLine().equals(item2.getCommandLine())) {
        cloneFrom.removeItemAt(i);    // Its an exact match!
        break;      // nothing left to compare!
      }
    }
  }
}
  
  // Variables declaration - do not modify//GEN-BEGIN:variables
  private javax.swing.JTextField ID;
  private javax.swing.JButton addUpdate;
  private javax.swing.JTextArea args;
  private javax.swing.JPanel bottomStuff;
  private javax.swing.JComboBox changeInstance;
  private javax.swing.JTextField changeTo;
  private javax.swing.JToggleButton clone;
  private javax.swing.JComboBox cloneFrom;
  private javax.swing.JTextField comment;
  private javax.swing.JTextArea config;
  private javax.swing.JPanel configPanel;
  private javax.swing.JTextField configfile;
  private javax.swing.JButton deleteItem;
  private javax.swing.JCheckBox disable;
  private javax.swing.JRadioButton displayAll;
  private javax.swing.JCheckBox displayHelp;
  private javax.swing.JComboBox edgefile;
  private javax.swing.JComboBox edgemominstancesetup;
  private javax.swing.JComboBox edgethreadID;
  private javax.swing.JTextField error;
  private java.awt.GridBagLayout gridBagLayout1;
  private javax.swing.JLabel idLab;
  private javax.swing.JComboBox instanceID;
  private javax.swing.JScrollPane jScrollPane1;
  private javax.swing.JLabel labChange;
  private javax.swing.JLabel labConfig;
  private javax.swing.JLabel labConfigFile;
  private javax.swing.JLabel labInstanceID;
  private javax.swing.JLabel labMain;
  private javax.swing.JLabel labTag;
  private javax.swing.JLabel lblArgs;
  private javax.swing.JLabel lblChangeRole;
  private javax.swing.JLabel lblComment;
  private javax.swing.JLabel lblEdgeThreadID;
  private javax.swing.JLabel lblFile;
  private javax.swing.JLabel lblLogfile;
  private javax.swing.JLabel lblPriority;
  private javax.swing.JTextField logfile;
  private javax.swing.JTextField priority;
  private javax.swing.JButton reload;
  private javax.swing.JScrollPane scrollConfig;
  private javax.swing.JTabbedPane tabPane;
  private javax.swing.JTextField tag;
  private javax.swing.JPanel threadPanel;
  // End of variables declaration//GEN-END:variables
  /** Creates new form EdgeMomInstanceSetupPanel */
  public EdgeMomInstanceSetupPanel() {
    initiating=true;
    initComponents();
    cleanCloneFrom(cloneFrom);
    getJComboBox(edgemominstancesetup);                // set up the key JComboBox
    edgemominstancesetup.setSelectedIndex(-1);    // Set selected type
    initiating=false;
   
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
    makeEdgeMomInstanceSetups();
    for (EdgemomInstanceSetup v1 : EdgemomInstanceSetup.edgemomInstanceSetups) {
      b.addItem(v1);
    }
    b.setMaximumRowCount(30);
  }
  
  /** Given a JComboBox from getJComboBox, set selected item to match database ID
   *@param b The JComboBox
   *@param ID the row ID from the database to select
   */
  public static void setJComboBoxToID(JComboBox b, int ID) {
    b.setSelectedIndex(-1);
    //Util.prt("EdgeMomInstanceSetupPanel.setJComboBoxToID id="+ID);
    for(int i=0; i<b.getItemCount(); i++) 
      if(((EdgemomInstanceSetup) b.getItemAt(i)).getID() == ID) {
          b.setSelectedIndex(i);
          //Util.prt("EdgeMomInstanceSetupPanel.setJComboBoxToID id="+ID+" found at "+i);
      }
  }  
  
  /** Given a database ID find the index into a JComboBox consistent with this panel
   *@param ID The row ID from the database
   *@return The index in the JComboBox with this ID
   */
  public static int getJComboBoxIndex(int ID) {
    if(EdgemomInstanceSetup.edgemomInstanceSetups == null) makeEdgeMomInstanceSetups();
    for(int i=0; i<EdgemomInstanceSetup.edgemomInstanceSetups.size(); i++) if( ID == EdgemomInstanceSetup.edgemomInstanceSetups.get(i).getID()) return i;
    return -1;
  }
  /** Cause the main JComboBox to be reloaded*/
  public static void reload() {
    EdgemomInstanceSetup.edgemomInstanceSetups = null;
    makeEdgeMomInstanceSetups();
  }
    /* return a ArrayList with all of the EdgeMomInstanceSetup
   * @return The ArrayList with the edgeMomInstance
   */
  public static ArrayList<EdgemomInstanceSetup> getEdgeMomInstanceSetupVector() {
    if(EdgemomInstanceSetup.edgemomInstanceSetups == null) makeEdgeMomInstanceSetups();
    return EdgemomInstanceSetup.edgemomInstanceSetups;
  }    /* return a ArrayList with all of the EdgeMomInstanceSetup
   * @return The ArrayList with the edgeMomInstance
   */
  public static ArrayList<EdgemomInstanceSetup> getEdgeMomInstanceSetupArrayList() {
    if(EdgemomInstanceSetup.edgemomInstanceSetups == null) makeEdgeMomInstanceSetups();
    return EdgemomInstanceSetup.edgemomInstanceSetups;
  }
  /** Get the item corresponding to database ID
   *@param ID the database Row ID
   *@return The EdgeMomInstanceSetup row with this ID
   */
  public static EdgemomInstanceSetup getEdgeMomInstanceSetupWithID(int ID) {
    if(EdgemomInstanceSetup.edgemomInstanceSetups == null) makeEdgeMomInstanceSetups();
    int i=getJComboBoxIndex(ID);
    if(i >= 0) return  EdgemomInstanceSetup.edgemomInstanceSetups.get(i);
    else return null;
  }
  
  // This routine should only need tweeking if key field is not same as table name
  private static void makeEdgeMomInstanceSetups() {
    EdgeMomInstance.makeEdgeMomInstances();
    EdgemomInstanceSetup.makeEdgeMomInstanceSetups();
  
  }
  
  // No changes needed
  private void find(boolean forceNew) {
    if(edgemominstancesetup == null) return;
    if(initiating) return;
    String tagtemp="";
    EdgemomInstanceSetup l;
    if(edgemominstancesetup.getSelectedIndex() == -1) {
      if(edgemominstancesetup.getSelectedItem() == null && !forceNew) return;
      l = newOne();
      tagtemp = ""+edgemominstancesetup.getSelectedItem();
      
    } 
    else {
      l = (EdgemomInstanceSetup) edgemominstancesetup.getSelectedItem();
    }
      
    if(l == null) return;
    String p = l.getEdgemomsetup();
    if(p == null) return;
    if(p.equals("")) {
      addUpdate.setEnabled(false);
      addUpdate.setText("Enter a EdgemomInstanceSetup!");
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
          changeInstance.setSelectedItem(instanceID.getSelectedItem());
          priority.requestFocus();
          tag.setText(tagtemp);
          addUpdate.setText("Add "+changeInstance.getSelectedItem()+"-"+tag.getText());
          addUpdate.setEnabled(true);
      }
      else {
        setJComboBoxToID(edgemominstancesetup, obj.getInt("ID"));
       
        addUpdate.setText("Update "+((EdgemomInstanceSetup) edgemominstancesetup.getSelectedItem()).getTag());
        addUpdate.setEnabled(true);
        ID.setText(""+obj.getInt("ID"));    // Info only, show ID
      }
    }  
    // This is only thrown if something happens in oldone() new DBOject
    // Other than it being a new record.
    catch  (SQLException  E)                                 // 
    { Util.SQLErrorPrint(E,"EdgemomInstanceSetup: SQL error getting EdgemomInstanceSetup="+p);
    E.printStackTrace();
    }
    
  }

  /** This main displays the form Pane by itself
   *@param args command line args ignored*/
  public static void main(String args[]) {
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
      Show.inFrame(new EdgeMomInstanceSetupPanel(), UC.XSIZE, UC.YSIZE);
    }
    catch(InstantiationException e) {}


  }
}
