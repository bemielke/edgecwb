/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */

package gov.usgs.edge.alarm;

import gov.usgs.anss.db.DBConnectionThread;
import gov.usgs.anss.db.DBObject;
import gov.usgs.anss.util.ErrorTrack;
import gov.usgs.anss.util.FUtil;
import gov.usgs.anss.util.Show;
import gov.usgs.anss.util.Util;
import gov.usgs.edge.config.UC;
import java.awt.Color;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import javax.swing.JComboBox;
/**
 *
 * requirements : 
 * The key for the file must be the name of the table and of the
 * JComboBox variable this variable is called "event" in the initial form
 * The table name and key name should match and start lower case (event).
 * The Class here will be that same name but with upper case at beginning(Event).
 * <br> 1)  Rename the location JComboBox to the "key" (event) value.  This should start Lower
 *      case.
 *<br>  2)  The table name should match this key name and be all lower case.
 *<br>  3)  Change all Event to ClassName of underlying data (case sensitive!)
 *<br>  4)  Change all event key value (the JComboBox above)
 *<br>  5)  clearScreen should update swing components to new defaults
 *<br>  6)  chkForm should check all field for validity.  The errorTrack variable is
 *      used to track multiple errors.  In FUtil.chk* handle checks of the various
 *      variable types.  Date, double, and IP etc return the "canonical" form for 
 *      the variable.  These should be stored in a class variable so the latest one
 *      from "chkForm()" are available to the update writer.
 *<br>  7) newone() should be updated to create a new instance of the underlying data class.
 *<br>  8) oldone() get data from database and update all swing elements to correct values
 *<br>  9) addUpdateAction() add data base set*() operations updating all fields in the DB
 *<br> 10) EventPanel() constructor - good place to change backgrounds using UC.Look() any
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
 *<br> FUtil.getEnumJComboBox(DBConnectionThread.getConnection("alarm"), "tcpstation","rtsport1");
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
 * <br>    if(fieldEnum == null) fieldEnum = FUtil.getEnum(DBConnectionThread.getConnection("alarm"),"table","fieldName");
 * <br>    
 * <br>   // Get the int corresponding to an Enum String for storage in the data base and 
 * <br>   // the index into the JComboBox representing this Enum 
 * <br>   localVariable = FUtil.enumStringToInt(fieldEnum, enumString);
  * <br>
 * @author  D.C. Ketchum
 */
public final class EventPanel extends javax.swing.JPanel {
  
  // NOTE : here define all variables general.  "Vector v" is used for main Comboboz
  private DBObject obj;
  private final ErrorTrack err=new ErrorTrack();
  private boolean initiating=false;
  
  //USER: Here are the local variables
  private int dampingInt;        // 1st damping variable
  private int damping2Int;       // 2nd damping variable
  private int flagsInt;
  private int escalationMethodID; // The first escalation method if any
  private int escalationMethodID1;// Secondary escalation method, if any
  private int escalationMethodID2;// Tertiarary escalation method, if any
  private Timestamp tsSupp;
  
  

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
   Util.prt("chkForm Event");
   dampingInt = FUtil.chkInt(damp, err, 0, 10000000);
   damping2Int = FUtil.chkInt(damp2, err, 0, 10000000);
   flagsInt = FUtil.chkInt(flags, err, 0, 10000000);
   tsSupp = FUtil.chkTimestamp(suppressUntil,err);
   //String tmp[] = tsSupp.toString().split("-");
   
   
   
//   if(source.getText().length() > 12) {

   if(code.getText().length() > 12) {
     err.set(true); err.appendText("Code too long");
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
    error.setText("");
    addUpdate.setEnabled(false);
    addUpdate.setText("Enter a Event");
    
    //USER: Clear all fields for this form, this sets "defaults" for new screen
    // description.setText("");
    //source.setSelectedIndex(-1);
    code.setText("");
    phrase.setText("");
    regexp.setText("");
    damp.setText("0"); 
    damp2.setText("0");
    suppressReason.setText("");
    updated.setText("");
    
    // We do not yet support escalation methods so blank them out for now
    lblEsc.setVisible(false);
    lblEsc1.setVisible(false);
    lblEsc2.setVisible(false);
    escalationMethod.setVisible(false);
    escalationMethod1.setVisible(false);
    escalationMethod2.setVisible(false);
    labCode.setVisible(false);
    code.setVisible(false);
    labRegexp.setVisible(false);
    regexp.setVisible(false);
    
  }
 
  
  private Event newOne() {
    Timestamp t = new Timestamp(10000l);
    return new Event(0, ((String) event.getSelectedItem()).toUpperCase() //, more
      , "", 0,"","", "", 0, 0, 0, 0, 0, t, "", ""
       );
  }
  
  private void oldOne(String p) throws SQLException {
      obj = new DBObject(DBConnectionThread.getThread(DBConnectionThread.getDBSchema()), 
      "alarm", "event","event",p);

    if(obj.isNew()) {
      Util.prt("object is new"+p);
    }
    else {
      //USER: Here set all of the form fields to data from the DBObject
      //      event.setText(obj.getString("Event"));
      // Example : description.setText(obj.getString("description"));
      //source.setSelectedItem(obj.getString("source"));
//      setJComboBoxToID(source,obj.getInt("source"));
      code.setText(obj.getString("code"));
      phrase.setText(obj.getString("phrase"));
      updated.setText(obj.getTimestamp("updated").toString());
      regexp.setText(obj.getString("regularexpression"));
      damp.setText(""+obj.getInt("damping"));
      damp2.setText(""+obj.getInt("damping2"));
      flags.setText(""+obj.getInt("flags"));
//      escalationMethodID.setText(""+obj.getInt("escalationMethodID"));
//      escalationMethodID1.setText(""+obj.getInt("escalationMethodID1"));
//      escalationMethodID2.setText(""+obj.getInt("escalationMethodID2"));
      suppressUntil.setText(""+obj.getTimestamp("suppressUntil").toString().substring(0,19));
      suppressReason.setText(""+obj.getString("suppressReason"));
      suppressNodeRegExp.setText(""+obj.getString("suppressNodeRegExp"));
      permSuppressNodeRE.setText(""+obj.getString("suppressNodeRegExpPerm")); 
        
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
    label1 = new java.awt.Label();
    labEventSC = new javax.swing.JLabel();
    event = getJComboBox();
    addUpdate = new javax.swing.JButton();
    ID = new javax.swing.JTextField();
    labID = new javax.swing.JLabel();
    error = new javax.swing.JTextField();
    labSource = new javax.swing.JLabel();
    labCode = new javax.swing.JLabel();
    code = new javax.swing.JTextField();
    labPhrase = new javax.swing.JLabel();
    phrase = new javax.swing.JTextField();
    labRegexp = new javax.swing.JLabel();
    regexp = new javax.swing.JTextField();
    labDamping = new javax.swing.JLabel();
    damp = new javax.swing.JTextField();
    labDamp2 = new javax.swing.JLabel();
    damp2 = new javax.swing.JTextField();
    jScrollPane1 = new javax.swing.JScrollPane();
    help = new javax.swing.JTextArea();
    labHelp = new javax.swing.JLabel();
    source = SourcePanel.getJComboBox();
    source.setSelectedIndex(-1);
    lblEsc = new javax.swing.JLabel();
    escalationMethod = new javax.swing.JComboBox();
    lblEsc1 = new javax.swing.JLabel();
    escalationMethod1 = new javax.swing.JComboBox();
    lblEsc2 = new javax.swing.JLabel();
    escalationMethod2 = new javax.swing.JComboBox();
    lblSuppUntil = new javax.swing.JLabel();
    suppressReason = new javax.swing.JTextField();
    lblSuppReason = new javax.swing.JLabel();
    suppressUntil = new javax.swing.JTextField();
    labSuppressNodeRegExp = new javax.swing.JLabel();
    suppressNodeRegExp = new javax.swing.JTextField();
    lblFlags = new javax.swing.JLabel();
    flags = new javax.swing.JTextField();
    labPermSuppress = new javax.swing.JLabel();
    permSuppressNodeRE = new javax.swing.JTextField();
    updated = new javax.swing.JTextField();
    labUpdated = new javax.swing.JLabel();

    label1.setText("label1");

    setLayout(new java.awt.GridBagLayout());

    labEventSC.setText("Event (Source-Code) : ");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 3;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    add(labEventSC, gridBagConstraints);

    event.setEditable(true);
    event.setToolTipText("<html>\n<pre>\n\nSelect the combined source and code here (source-code is an event). \n\nYou cannot create a new event or code here.\n</pre>\n</html>\n");
    event.setMaximumSize(new java.awt.Dimension(129, 25));
    event.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        eventActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 3;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(event, gridBagConstraints);

    addUpdate.setText("Add/Update");
    addUpdate.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        addUpdateActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 19;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(addUpdate, gridBagConstraints);

    ID.setEditable(false);
    ID.setColumns(8);
    ID.setMaximumSize(new java.awt.Dimension(92, 20));
    ID.setMinimumSize(new java.awt.Dimension(92, 20));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 20;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(ID, gridBagConstraints);

    labID.setText("ID :");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 20;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    add(labID, gridBagConstraints);

    error.setColumns(40);
    error.setEditable(false);
    error.setMinimumSize(new java.awt.Dimension(488, 22));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(error, gridBagConstraints);

    labSource.setText("Source : ");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 2;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    add(labSource, gridBagConstraints);

    labCode.setText("Code : ");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 21;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    add(labCode, gridBagConstraints);

    code.setColumns(14);
    code.setMaximumSize(new java.awt.Dimension(129, 20));
    code.setMinimumSize(new java.awt.Dimension(129, 20));
    code.setPreferredSize(new java.awt.Dimension(129, 20));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 21;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(code, gridBagConstraints);

    labPhrase.setText("Phrase : ");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 18;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    add(labPhrase, gridBagConstraints);

    phrase.setEditable(false);
    phrase.setBackground(java.awt.Color.lightGray);
    phrase.setColumns(70);
    phrase.setFont(new java.awt.Font("Monospaced", 0, 10)); // NOI18N
    phrase.setToolTipText("This is the phrase from the last time this event occured, so it might change over time.");
    phrase.setMaximumSize(new java.awt.Dimension(129, 20));
    phrase.setMinimumSize(new java.awt.Dimension(500, 20));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 18;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(phrase, gridBagConstraints);

    labRegexp.setText("RegularExpression : ");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 7;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    add(labRegexp, gridBagConstraints);

    regexp.setColumns(40);
    regexp.setMaximumSize(new java.awt.Dimension(129, 20));
    regexp.setMinimumSize(new java.awt.Dimension(129, 20));
    regexp.setPreferredSize(new java.awt.Dimension(129, 20));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 7;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(regexp, gridBagConstraints);

    labDamping.setText("Damping (action repeat interval):");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 8;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    add(labDamping, gridBagConstraints);

    damp.setColumns(6);
    damp.setToolTipText("Set the minimum time in minutes between email or page actions for this event.  ");
    damp.setMaximumSize(new java.awt.Dimension(129, 20));
    damp.setMinimumSize(new java.awt.Dimension(129, 20));
    damp.setPreferredSize(new java.awt.Dimension(129, 20));
    damp.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        dampActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 8;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(damp, gridBagConstraints);

    labDamp2.setText("Damping2 (minimum persist) : ");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 9;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    add(labDamp2, gridBagConstraints);

    damp2.setColumns(6);
    damp2.setToolTipText("Set the minimum time this condition much persist before any action can be taken.");
    damp2.setMaximumSize(new java.awt.Dimension(129, 20));
    damp2.setMinimumSize(new java.awt.Dimension(129, 20));
    damp2.setPreferredSize(new java.awt.Dimension(129, 20));
    damp2.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        damp2ActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 9;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(damp2, gridBagConstraints);

    jScrollPane1.setMaximumSize(new java.awt.Dimension(500, 200));
    jScrollPane1.setMinimumSize(new java.awt.Dimension(500, 200));
    jScrollPane1.setPreferredSize(new java.awt.Dimension(500, 200));

    help.setEditable(false);
    help.setColumns(40);
    help.setFont(new java.awt.Font("Monospaced", 0, 10)); // NOI18N
    help.setRows(10);
    help.setText(" Choose a source, and then choose an code forming an Event (Source-Code).\n\n You cannot create a new event using this form!\n \n This form allows users to change damping and other fields related to the event.\n\n\n Damping (action repeat interval): Minutes between pages or emails for a given event.\n Damping2(minimum persist):\tMinutes an event must persist before FIRST action.\n\nPermNodeSuppressRE:  matching nodes are permanently suppressedion\n\nSuppress until : Set a date on which this suppress expires - for temporarily stopping actions\nSuppress - Why? : a reminder of why this is temporarily suppressed\nSuppress node regexp : The temporary suppress is only for nodes matching this regular express.\n");
    jScrollPane1.setViewportView(help);

    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 0;
    add(jScrollPane1, gridBagConstraints);

    labHelp.setText("Help : ");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    add(labHelp, gridBagConstraints);

    source.setEditable(true);
    source.setToolTipText("<html>\n<pre>\nThe sources are determined by the software and are not under user control.  Select a source of the event\nyou want to edit.  You cannot create a new source here.\n</pre>\n</html>\n");
    source.setMaximumSize(new java.awt.Dimension(129, 25));
    source.addItemListener(new java.awt.event.ItemListener() {
      public void itemStateChanged(java.awt.event.ItemEvent evt) {
        sourceItemStateChanged(evt);
      }
    });
    source.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        sourceActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 2;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(source, gridBagConstraints);

    lblEsc.setText("Escalation Method : ");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 10;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    add(lblEsc, gridBagConstraints);

    escalationMethod.setEditable(true);
    escalationMethod.setMaximumSize(new java.awt.Dimension(129, 25));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 10;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(escalationMethod, gridBagConstraints);

    lblEsc1.setText("Escalation Method 1 : ");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 11;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    add(lblEsc1, gridBagConstraints);

    escalationMethod1.setEditable(true);
    escalationMethod1.setMaximumSize(new java.awt.Dimension(129, 25));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 11;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(escalationMethod1, gridBagConstraints);

    lblEsc2.setText("Escalation Method 2 : ");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 12;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    add(lblEsc2, gridBagConstraints);

    escalationMethod2.setEditable(true);
    escalationMethod2.setMaximumSize(new java.awt.Dimension(129, 25));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 12;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(escalationMethod2, gridBagConstraints);

    lblSuppUntil.setText("Suppress Until (yyyy/mm/dd) : ");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 14;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    add(lblSuppUntil, gridBagConstraints);

    suppressReason.setToolTipText("Give a reason/hint for why this temporary suppression was created.");
    suppressReason.setMaximumSize(new java.awt.Dimension(129, 20));
    suppressReason.setMinimumSize(new java.awt.Dimension(350, 20));
    suppressReason.setPreferredSize(new java.awt.Dimension(129, 20));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 15;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(suppressReason, gridBagConstraints);

    lblSuppReason.setText("Suppress - Why? : ");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 15;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    add(lblSuppReason, gridBagConstraints);

    suppressUntil.setColumns(25);
    suppressUntil.setToolTipText("Any temporary suppression expires on this date.");
    suppressUntil.setMaximumSize(new java.awt.Dimension(129, 20));
    suppressUntil.setMinimumSize(new java.awt.Dimension(200, 20));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 14;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(suppressUntil, gridBagConstraints);

    labSuppressNodeRegExp.setText("Suppress Node Reg Exp : ");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 16;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    add(labSuppressNodeRegExp, gridBagConstraints);

    suppressNodeRegExp.setColumns(80);
    suppressNodeRegExp.setToolTipText("Only node matching this regular expression are subject to a temporary suppression of this event.");
    suppressNodeRegExp.setMaximumSize(new java.awt.Dimension(500, 20));
    suppressNodeRegExp.setMinimumSize(new java.awt.Dimension(500, 20));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 16;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(suppressNodeRegExp, gridBagConstraints);

    lblFlags.setText("Flags (1=Email only, 2=test) : ");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 4;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    add(lblFlags, gridBagConstraints);

    flags.setToolTipText("This overrides the actions for this event to be only testing or only emails regardless of subscriptions.");
    flags.setMaximumSize(new java.awt.Dimension(129, 20));
    flags.setMinimumSize(new java.awt.Dimension(129, 20));
    flags.setPreferredSize(new java.awt.Dimension(129, 20));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 4;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(flags, gridBagConstraints);

    labPermSuppress.setText("Perm Node Suppress RE:");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 13;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    add(labPermSuppress, gridBagConstraints);

    permSuppressNodeRE.setColumns(80);
    permSuppressNodeRE.setToolTipText("Nodes matching this regular expression are to permanently suppress this event.");
    permSuppressNodeRE.setMinimumSize(new java.awt.Dimension(500, 22));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 13;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(permSuppressNodeRE, gridBagConstraints);

    updated.setEditable(false);
    updated.setBackground(java.awt.Color.lightGray);
    updated.setColumns(22);
    updated.setToolTipText("This is the last time this event happened.");
    updated.setMinimumSize(new java.awt.Dimension(200, 28));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 17;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
    add(updated, gridBagConstraints);

    labUpdated.setText("Last Occurred:");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 17;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_END;
    add(labUpdated, gridBagConstraints);
  }// </editor-fold>//GEN-END:initComponents

  private void eventActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_eventActionPerformed
    find();
  }//GEN-LAST:event_eventActionPerformed

  private void sourceItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_sourceItemStateChanged
    if (source.getSelectedItem()==null)
      EventPanel.getJComboBox(event);
    else
      EventPanel.getJComboBox(event,source.getSelectedItem().toString());
    event.setSelectedIndex(-1);
  }//GEN-LAST:event_sourceItemStateChanged

  private void sourceActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_sourceActionPerformed
    if (source.getSelectedItem()==null)
      EventPanel.getJComboBox(event);
    else
      EventPanel.getJComboBox(event,source.getSelectedItem().toString()); 
    event.setSelectedIndex(-1);
  }//GEN-LAST:event_sourceActionPerformed

  private void damp2ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_damp2ActionPerformed
    chkForm();
  }//GEN-LAST:event_damp2ActionPerformed

  private void dampActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_dampActionPerformed
    chkForm();
  }//GEN-LAST:event_dampActionPerformed

  private void addUpdateActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_addUpdateActionPerformed
    
    // Do not change
    if(initiating) return;
    if(chkForm()) return;
    try {
      String p = source.getSelectedItem().toString()+"-"+code.getText();
      //p = p.toUpperCase();                  // USER : drop this to allow Mixed case
      obj.setString("event",p);
      if(!obj.isNew()) obj.refreshRecord();        // Refresh the result set so the sets will not fail on stale one
      
      // USER : Set all of the fields in data base.  obj.setInt("thefield", ivalue);

      obj.setString("source", source.getSelectedItem().toString());
      obj.setInt("flags", flagsInt);
      obj.setString("code", code.getText());
      obj.setString("phrase", phrase.getText());
      obj.setString("regularexpression", regexp.getText());
      obj.setInt("damping", dampingInt);
      obj.setInt("damping2", damping2Int);
      obj.setInt("escalationmethodid", escalationMethodID);
      obj.setInt("escalationmethodid1", escalationMethodID1);
      obj.setInt("escalationmethodid2", escalationMethodID2);
      obj.setTimestamp("suppressUntil", tsSupp);
      obj.setString("suppressReason", suppressReason.getText());
      obj.setString("suppressNodeRegExp", suppressNodeRegExp.getText());
      obj.setString("suppressNodeRegExpPerm", permSuppressNodeRE.getText());
      // Do not change
      obj.updateRecord();
      Event.events=null;       // force reload of combo box
      getJComboBox(event);
      clearScreen();
      event.setSelectedIndex(-1);
      source.setSelectedIndex(source.getSelectedIndex()); // Select same source for convenience
    }  catch  (SQLException  E)
    { Util.SQLErrorPrint(E,"event: update failed partno="+event.getSelectedItem());
      error.setText("Could Not Update File!");
      error.setBackground(UC.red);
      addUpdate.setEnabled(true);
    }      
  }//GEN-LAST:event_addUpdateActionPerformed
  
  
  // Variables declaration - do not modify//GEN-BEGIN:variables
  private javax.swing.JTextField ID;
  private javax.swing.JButton addUpdate;
  private javax.swing.JTextField code;
  private javax.swing.JTextField damp;
  private javax.swing.JTextField damp2;
  private javax.swing.JTextField error;
  private javax.swing.JComboBox escalationMethod;
  private javax.swing.JComboBox escalationMethod1;
  private javax.swing.JComboBox escalationMethod2;
  private javax.swing.JComboBox event;
  private javax.swing.JTextField flags;
  private java.awt.GridBagLayout gridBagLayout1;
  private javax.swing.JTextArea help;
  private javax.swing.JScrollPane jScrollPane1;
  private javax.swing.JLabel labCode;
  private javax.swing.JLabel labDamp2;
  private javax.swing.JLabel labDamping;
  private javax.swing.JLabel labEventSC;
  private javax.swing.JLabel labHelp;
  private javax.swing.JLabel labID;
  private javax.swing.JLabel labPermSuppress;
  private javax.swing.JLabel labPhrase;
  private javax.swing.JLabel labRegexp;
  private javax.swing.JLabel labSource;
  private javax.swing.JLabel labSuppressNodeRegExp;
  private javax.swing.JLabel labUpdated;
  private java.awt.Label label1;
  private javax.swing.JLabel lblEsc;
  private javax.swing.JLabel lblEsc1;
  private javax.swing.JLabel lblEsc2;
  private javax.swing.JLabel lblFlags;
  private javax.swing.JLabel lblSuppReason;
  private javax.swing.JLabel lblSuppUntil;
  private javax.swing.JTextField permSuppressNodeRE;
  private javax.swing.JTextField phrase;
  private javax.swing.JTextField regexp;
  private javax.swing.JComboBox source;
  private javax.swing.JTextField suppressNodeRegExp;
  private javax.swing.JTextField suppressReason;
  private javax.swing.JTextField suppressUntil;
  private javax.swing.JTextField updated;
  // End of variables declaration//GEN-END:variables
  /** Creates new form EventPanel */
  public EventPanel() {
    initiating=true;
    initComponents();
    getJComboBox(event);                // set up the key JComboBox
    event.setSelectedIndex(-1);    // Set selected type
    Look();                    // Set color background
    initiating=false;
   
    // USER:This is a good place for UC.Look(components) to get rid of the ugly Grey
    UC.Look(help);
    clearScreen();                    // Start with empty screen
  }
  
  private void Look() {
    UC.Look(this);                    // Set color background
  }
  /** Find an event given the source, code, and payload
   *<br> 1)  If source and code are set, use them to look up the event
   *<br> 2)  If source or code are blank, use regular expression
   *@param src The source code to match
   *@param cd  The code to match
   *@param payload The payload of the message
   *@return null if no match is found, else a Event object matching these parameters.
   */
  public static Event findEvent(String src, String cd, String payload ) {
    if(src.length() > 0 && cd.length() > 0) {
      for(int i=0; i<Event.events.size(); i++) {
        if(Event.events.get(i).getSource().equalsIgnoreCase(src.trim()) &&Event. events.get(i).getCode().equalsIgnoreCase(cd.trim())) return Event.events.get(i);
      }
      return null;
    }
    else {
      for(int i=0; i<Event.events.size(); i++) {
        String re = Event.events.get(i).getRegexp();
        if(re.length() > 0) {
          if(payload.matches(re)) return Event.events.get(i);
        }
      }
      return null;
    }
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
    makeEvents();
    for (int i=0; i< Event.events.size(); i++) {
      b.addItem( Event.events.get(i));
    }
    b.setMaximumRowCount(30);
  }
  
  public static void getJComboBox(JComboBox b, String str) {
    b.removeAllItems();
    if (str==null) makeEvents();
    else makeEvents(str);
    for (int i=0; i< Event.events.size(); i++) {
      b.addItem( Event.events.get(i));
    }
    b.setMaximumRowCount(30);
  }
  
  /** Given a JComboBox from getJComboBox, set selected item to match database ID
   *@param b The JComboBox
   *@param ID the row ID from the database to select
   */
  public static void setJComboBoxToID(JComboBox b, int ID) {
    b.setSelectedIndex(-1);
    //Util.prt("EventPanel.setJComboBoxToID id="+ID);
    for(int i=0; i<b.getItemCount(); i++) 
      if(((Event) b.getItemAt(i)).getID() == ID) {
          b.setSelectedIndex(i);
          //Util.prt("EventPanel.setJComboBoxToID id="+ID+" found at "+i);
      }

  }  
  
  /** Given a database ID find the index into a JComboBox consistent with this panel
   *@param ID The row ID from the database
   *@return The index in the JComboBox with this ID
   */
  public static int getJComboBoxIndex(int ID) {
    for(int i=0; i<Event.events.size(); i++) {
      if( ID == Event.events.get(i).getID()) 
        return i;
    }
    return -1;
  }
  /** Cause the main JComboBox to be reloaded*/
  public static void reload() {
    Event.events = null;
    makeEvents();
  }
  
  /** Get the item corresponding to database ID
   *@param ID the database Row ID
   *@return The Event row with this ID
   */
  public static Event getItemWithID(int ID) {
    makeEvents();
    int i= getJComboBoxIndex(ID);
    if(i >= 0) return Event.events.get(i);
    else 
      return null;
  }
  
  // This routine should only need tweeking if key field is not same as table name
  private static void makeEvents() {
    Event.makeEvents();
   
  }
  private static void makeEvents(String str) {
//    if (v != null) return;
    Event.events=new ArrayList<Event>(100);
    try {
      Statement stmt = DBConnectionThread.getConnection(DBConnectionThread.getDBSchema()).createStatement();   // used for query
      String s = "SELECT * FROM alarm.event WHERE source ='"+str+"' ORDER BY event;";
      ResultSet rs = stmt.executeQuery(s);
      while (rs.next()) {
        Event loc = new Event(rs);
//        Util.prt("MakeEvent() i="+v.size()+" is "+loc.getEvent());
        Event.events.add(loc);
      }
    }
    catch (SQLException e) {
      Util.SQLErrorPrint(e,"makeEvents() on table SQL failed");
    }    
  }
  
  // No changes needed
  private void find() {
    clearScreen();
    if(event == null) return;
    if(initiating) return;
    Event l;
    if(event.getSelectedIndex() == -1) {
      if(event.getSelectedItem() == null) return;
      l = newOne();
    } 
    else {
      l = (Event) event.getSelectedItem();
    }
      
    if(l == null) return;
    String p = l.getEvent();
    if(p == null) return;
    if(p.equals("")) {
      addUpdate.setEnabled(false);
      addUpdate.setText("Enter a Event!");
    }
    //p = p.toUpperCase();
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
      }
      else {
        addUpdate.setText("Update "+p);
        addUpdate.setEnabled(true);
        ID.setText(""+obj.getInt("ID"));    // Info only, show ID
      }
    }  
    // This is only thrown if something happens in oldone() new DBOject
    // Other than it being a new record.
    catch  (SQLException  E)                                 // 
    { Util.SQLErrorPrint(E,"Event: SQL error getting Event="+p);
    }
    
  }
  public static ArrayList<Event> getEventVector() {
    makeEvents();
    return Event.events;
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
          {Util.prt("Could not connect to DB "+jcjbl); System.exit(1);}
      Show.inFrame(new EventPanel(), UC.XSIZE, UC.YSIZE);
    }
    catch(InstantiationException e) {}
  }
}
