/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */

package gov.usgs.anss.gui;

import gov.usgs.anss.guidb.TCPStation;
import gov.usgs.anss.db.DBConnectionThread;
import gov.usgs.anss.db.DBObject;
import gov.usgs.anss.util.ErrorTrack;
import gov.usgs.anss.util.FUtil;
import gov.usgs.anss.util.Show;
import gov.usgs.anss.util.Util;
import java.awt.Color;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import javax.swing.JComboBox;
/**
 *
 * requirements : 
 * The key for the file must be the name of the table and of the
 * JComboBox variable this variable is called "location" in the initial form
 * The table name and key name should match and start lower case (station).
 * The Class here will be that same name but with upper case at beginning(Vsat).
 * <br> 1)  Rename the location JComboBox to the "key" (station) value.  This should start Lower
 *      case.
 *<br>  2)  The table name should match this key name and be all lower case.
 *<br>  3)  Change all Vsat to ClassName of underlying data (case sensitive!)
 *<br>  4)  Change all station key value (the JComboBox above)
 *<br>  5)  clearScreen should update swing components to new defaults
 *<br>  6)  chkForm should check all field for validity.  The errorTrack variable is
 *      used to track multiple errors.  In FUtil.chk* handle checks of the various
 *      variable types.  Date, double, and IP etc return the "canonical" form for 
 *      the variable.  These should be stored in a class variable so the latest one
 *      from "chkForm()" are available to the update writer.
 *<br>  7) newone() should be updated to create a new instance of the underlying data class.
 *<br>  8) oldone() get data from database and update all swing elements to correct values
 *<br>  9) addUpdateAction() add data base set*() operations updating all fields in the DB
 *<br> 10) VsatPanel() constructor - good place to change backgrounds using UC.Look() any
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
 *<br> FUtil.getEnumJComboBox(UC.getConnection(), "tcpstation","rtsport1");
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
 * <br>    if(fieldEnum == null) fieldEnum = FUtil.getEnum(UC.getConnection(),"table","fieldName");
 * <br>    
 * <br>   // Get the int corresponding to an Enum String for storage in the data base and 
 * <br>   // the index into the JComboBox representing this Enum 
 * <br>   localVariable = FUtil.enumStringToInt(fieldEnum, enumString);
  * <br>
 * @author  D.C. Ketchum
 */
public final class VsatPanel extends javax.swing.JPanel {
  
  // NOTE : here define all variables general.  "ArrayList v" is used for main Comboboz
  static ArrayList<Vsat> v;             // ArrayList containing objects of this Vsat Type
  private DBObject obj;
  private final ErrorTrack err=new ErrorTrack();
  private boolean initiating=false;
  
  // Here are the local variables
  
  

  // This routine must validate all fields on the form.  The err (ErrorTrack) variable
  // is used by the FUtil to verify fields.  Use FUTil or custom code here for verifications
  private boolean chkForm() {
    // Do not change
   err.reset();
   gov.usgs.anss.util.UC.Look(error);
   
   // Your error checking code goes here setting the local variables to valid settings
   // note : Use FUtil.chk* to check dates, timestamps, IP addresses, etc and put them in
   // canonical form.  store in a class variable.  For instance :
   //   sdate = FUtil.chkDate(dateTextField, err);
   // Any errors found should err.set(true);  and err.append("error message");
   Util.prt("chkForm Vsat");
   String ip = FUtil.chkIP(ipadr, err, true);

    // No CHANGES : If we found an error, color up error box
    if(err.isSet()) {
      error.setText(err.getText());
      error.setBackground(gov.usgs.anss.util.UC.red);
    }
    return err.isSet();
  }
  
  // This routine must set the form to initial state.  It does not update the JCombobox
  private void clearScreen() {
    
    // Do not change
    ID.setText("");
    gov.usgs.anss.util.UC.Look(error);
    error.setText("");
    addUpdate.setEnabled(false);
    addUpdate.setText("Enter a Vsat location");
    
    // Clear all fields for this form, this sets "defaults" for new screen
    ipadr.setText("");
    comment.setText("");
    siteid.setText("");
    serialnumber.setText("");
    san.setText("");
    pin.setText("");
    account.setText("");
    macaddr.setText("");
    dynamic.setSelected(false);
    rtsip.setText("");
  }
 
  
  private Vsat newOne() {
      
    return new Vsat(0, ((String) station.getSelectedItem()).toUpperCase(), 
      "", "", "", "", "", "", "", "", 0
       );
  }
  
  private void oldOne(String p) throws SQLException {
        obj = new DBObject(DBConnectionThread.getThread(DBConnectionThread.getDBSchema()), DBConnectionThread.getDBSchema(), "vsat","station",p);
    if(obj.isNew()) {
      Util.prt("object is new"+p);
    }
    else {
      // Here set all of the form fields to data from the DBObject
      //      station.setText(obj.getString("Vsat"));
      // Example : description.setText(obj.getString("description"));
      ipadr.setText(obj.getString("ipadr"));
      comment.setText(obj.getString("comment"));
      siteid.setText(obj.getString("siteid"));
      serialnumber.setText(obj.getString("serialnumber"));
      pin.setText(obj.getString("pin"));
      san.setText(obj.getString("san"));
      account.setText(obj.getString("account"));
      macaddr.setText(obj.getString("macaddr"));
      dynamic.setSelected( (obj.getInt("dynamic") != 0));
      TCPStation t = RTSStationPanel.getTCPStation(obj.getString("station"));
      if(t != null) rtsip.setText(t.getIP());
    }           // End else isNew() - processing to form
  }
  
  
  /** This method is called from within the constructor to
   * initialize the form.
   * WARNING: Do NOT modify this code. The content of this method is
   * always regenerated by the Form Editor.
   */
  // <editor-fold defaultstate="collapsed" desc=" Generated Code ">//GEN-BEGIN:initComponents
  private void initComponents() {
    java.awt.GridBagConstraints gridBagConstraints;

    gridBagLayout1 = new java.awt.GridBagLayout();
    jLabel1 = new javax.swing.JLabel();
    station = getJComboBox();
    addUpdate = new javax.swing.JButton();
    ID = new javax.swing.JTextField();
    jLabel9 = new javax.swing.JLabel();
    error = new javax.swing.JTextField();
    serialnumber = new javax.swing.JTextField();
    siteid = new javax.swing.JTextField();
    ipadr = new javax.swing.JTextField();
    jLabel2 = new javax.swing.JLabel();
    jLabel3 = new javax.swing.JLabel();
    jLabel4 = new javax.swing.JLabel();
    jLabel5 = new javax.swing.JLabel();
    labSan = new javax.swing.JLabel();
    san = new javax.swing.JTextField();
    labPin = new javax.swing.JLabel();
    pin = new javax.swing.JTextField();
    labAcct = new javax.swing.JLabel();
    account = new javax.swing.JTextField();
    labMac = new javax.swing.JLabel();
    macaddr = new javax.swing.JTextField();
    dynamic = new javax.swing.JRadioButton();
    jScrollPane1 = new javax.swing.JScrollPane();
    comment = new javax.swing.JTextArea();
    rtsip = new javax.swing.JTextField();
    labRTSIP = new javax.swing.JLabel();

    setLayout(new java.awt.GridBagLayout());

    jLabel1.setText("Station:");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 1;
    add(jLabel1, gridBagConstraints);

    station.setEditable(true);
    station.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        stationActionPerformed(evt);
      }
    });

    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(station, gridBagConstraints);

    addUpdate.setText("Add/Update");
    addUpdate.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        addUpdateActionPerformed(evt);
      }
    });

    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 15;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(addUpdate, gridBagConstraints);

    ID.setColumns(8);
    ID.setEditable(false);
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 17;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(ID, gridBagConstraints);

    jLabel9.setText("ID :");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 17;
    add(jLabel9, gridBagConstraints);

    error.setColumns(40);
    error.setEditable(false);
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(error, gridBagConstraints);

    serialnumber.setColumns(20);
    serialnumber.setText("\n");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 3;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(serialnumber, gridBagConstraints);

    siteid.setColumns(10);
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 4;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(siteid, gridBagConstraints);

    ipadr.setColumns(18);
    ipadr.setText("000.000.000.000");
    ipadr.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        ipadrActionPerformed(evt);
      }
    });
    ipadr.addFocusListener(new java.awt.event.FocusAdapter() {
      public void focusLost(java.awt.event.FocusEvent evt) {
        ipadrFocusLost(evt);
      }
    });

    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 11;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(ipadr, gridBagConstraints);

    jLabel2.setText("Serial Number:");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 3;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    add(jLabel2, gridBagConstraints);

    jLabel3.setText("Site ID:");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 4;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    add(jLabel3, gridBagConstraints);

    jLabel4.setText("IP Address:");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 11;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    add(jLabel4, gridBagConstraints);

    jLabel5.setText("Comment:");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 14;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    add(jLabel5, gridBagConstraints);

    labSan.setText("SAN (consumer only):");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 6;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    add(labSan, gridBagConstraints);

    san.setColumns(15);
    san.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        sanActionPerformed(evt);
      }
    });

    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 6;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(san, gridBagConstraints);

    labPin.setText("PIN (consumer only:");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 7;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    add(labPin, gridBagConstraints);

    pin.setColumns(15);
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 7;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(pin, gridBagConstraints);

    labAcct.setText("Account (WB only):");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 8;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    add(labAcct, gridBagConstraints);

    account.setColumns(15);
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 8;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(account, gridBagConstraints);

    labMac.setText("MAC Addr (WB only) :");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 9;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    add(labMac, gridBagConstraints);

    macaddr.setColumns(25);
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 9;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(macaddr, gridBagConstraints);

    dynamic.setText("Dynamic IP");
    dynamic.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 0, 0, 0));
    dynamic.setMargin(new java.awt.Insets(0, 0, 0, 0));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 13;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(dynamic, gridBagConstraints);

    jScrollPane1.setMinimumSize(new java.awt.Dimension(500, 200));
    jScrollPane1.setPreferredSize(new java.awt.Dimension(500, 200));
    comment.setColumns(60);
    comment.setFont(new java.awt.Font("Courier New", 0, 12));
    comment.setRows(50);
    jScrollPane1.setViewportView(comment);

    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 14;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(jScrollPane1, gridBagConstraints);

    rtsip.setBackground(new java.awt.Color(192, 192, 192));
    rtsip.setColumns(15);
    rtsip.setEditable(false);
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 12;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(rtsip, gridBagConstraints);

    labRTSIP.setText("RTS IPadr :");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 12;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    add(labRTSIP, gridBagConstraints);

  }// </editor-fold>//GEN-END:initComponents

  private void ipadrFocusLost(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_ipadrFocusLost
// TODO add your handling code here:
    chkForm();
  }//GEN-LAST:event_ipadrFocusLost

  private void ipadrActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_ipadrActionPerformed
// TODO add your handling code here:
    chkForm();
  }//GEN-LAST:event_ipadrActionPerformed

  private void sanActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_sanActionPerformed
// TODO add your handling code here:
  }//GEN-LAST:event_sanActionPerformed

  private void addUpdateActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_addUpdateActionPerformed
    // Add your handling code here:
    
    // Do not change
    if(initiating) return;
    if(chkForm()) return;
    try {
      String p = station.getSelectedItem().toString();
      p = p.toUpperCase();                  // USER : drop this to allow Mixed case
      if(!obj.isNew()) obj.refreshRecord();        // Refresh the result set so the sets will not fail on stale one
      obj.setString("station",p);
      
      // USER : Set all of the fields in data base.  obj.setInt("thefield", ivalue);
      obj.setString("serialnumber",serialnumber.getText());
      obj.setString("ipadr",ipadr.getText());
      obj.setString("siteid", siteid.getText());
      obj.setString("comment", comment.getText());
      obj.setString("san",san.getText());
      obj.setString("pin",pin.getText());
      obj.setString("account",account.getText());
      obj.setString("macaddr",macaddr.getText());
      obj.setInt("dynamic", (dynamic.isSelected() ? 1:0)) ;
      
      // Do not change
      obj.updateRecord();
      v=null;       // force reload of combo box
      getJComboBox(station);
      clearScreen();
      station.setSelectedIndex(-1);
    }  catch  (SQLException  E)
    { Util.SQLErrorPrint(E,"vsat: update failed station="+station.getSelectedItem());
      error.setText("Could Not Update File!");
      error.setBackground(gov.usgs.anss.util.UC.red);
      addUpdate.setEnabled(true); 
    }      
  }//GEN-LAST:event_addUpdateActionPerformed

  private void stationActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_stationActionPerformed
    // Add your handling code here:
   find();
 
 
  }//GEN-LAST:event_stationActionPerformed
  
  
  // Variables declaration - do not modify//GEN-BEGIN:variables
  private javax.swing.JTextField ID;
  private javax.swing.JTextField account;
  private javax.swing.JButton addUpdate;
  private javax.swing.JTextArea comment;
  private javax.swing.JRadioButton dynamic;
  private javax.swing.JTextField error;
  private java.awt.GridBagLayout gridBagLayout1;
  private javax.swing.JTextField ipadr;
  private javax.swing.JLabel jLabel1;
  private javax.swing.JLabel jLabel2;
  private javax.swing.JLabel jLabel3;
  private javax.swing.JLabel jLabel4;
  private javax.swing.JLabel jLabel5;
  private javax.swing.JLabel jLabel9;
  private javax.swing.JScrollPane jScrollPane1;
  private javax.swing.JLabel labAcct;
  private javax.swing.JLabel labMac;
  private javax.swing.JLabel labPin;
  private javax.swing.JLabel labRTSIP;
  private javax.swing.JLabel labSan;
  private javax.swing.JTextField macaddr;
  private javax.swing.JTextField pin;
  private javax.swing.JTextField rtsip;
  private javax.swing.JTextField san;
  private javax.swing.JTextField serialnumber;
  private javax.swing.JTextField siteid;
  private javax.swing.JComboBox station;
  // End of variables declaration//GEN-END:variables
  /** Creates new form VsatPanel */
  public VsatPanel() {
    initiating=true;
    initComponents();
    getJComboBox(station);                // set up the key JComboBox
    station.setSelectedIndex(-1);    // Set selected type
    gov.usgs.anss.util.UC.Look((VsatPanel) this);                    // Set color background
    initiating=false;
   
    // USER:This is a good place for UC.Look(components) to get rid of the ugly Grey
    
    clearScreen();                    // Start with empty screen
  }
  
  //////////////////////////////////////////////////////////////////////////////
  // No USER: changes needed below here!
  //////////////////////////////////////////////////////////////////////////////
  /** Create a JComboBox of all of the items in the table represented by this panel
   *@return A New JComboBox filled with all row keys from the table
   */
 public static JComboBox getJComboBox() { JComboBox b = new JComboBox(); getJComboBox(b); return b;}
  /** Udate a JComboBox to represent the rows represented by this panel
   *@param b The JComboBox to Update
   */
  public static void getJComboBox(JComboBox b) {
    b.removeAllItems();
    makeVsats();
    for (Vsat v1 : v) {
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
    //Util.prt("VsatPanel.setJComboBoxToID id="+ID);
    for(int i=0; i<b.getItemCount(); i++) 
      if(((Vsat) b.getItemAt(i)).getID() == ID) {
          b.setSelectedIndex(i);
          //Util.prt("VsatPanel.setJComboBoxToID id="+ID+" found at "+i);
      }
  }  
  
  /** Given a database ID find the index into a JComboBox consistent with this panel
   *@param ID The row ID from the database
   *@return The index in the JComboBox with this ID
   */
  public static int getJComboBoxIndex(int ID) {
    for(int i=0; i<v.size(); i++) if( ID == ((Vsat) v.get(i)).getID()) return i;
    return -1;
  }
  /** Cause the main JComboBox to be reloaded*/
  public static void reload() {
    v = null;
    makeVsats();
  }
  
  /** Get the item corresponding to database ID
   *@param ID the database Row ID
   *@return The Vsat row with this ID
   */
  public static Vsat getItemWithID(int ID) {
    int i=getJComboBoxIndex(ID);
    if(i >= 0) return (Vsat) v.get(i);
    else return null;
  }
  
  // This routine should only need tweeking if key field is not same as table name
  private static void makeVsats() {
    if (v != null) return;
    v=new ArrayList<>(100);
    try {
      try (Statement stmt = DBConnectionThread.getConnection(DBConnectionThread.getDBSchema()).createStatement() // used for query
      ) {
        String s = "SELECT * FROM anss.vsat ORDER BY station;";
        try (ResultSet rs = stmt.executeQuery(s)) {
          while (rs.next()) {
            Vsat loc = new Vsat(rs);
//        Util.prt("MakeVsat() i="+v.size()+" is "+loc.getVsat());
            v.add(loc);
          }
        }
      }
    }
    catch (SQLException e) {
      Util.SQLErrorPrint(e,"makeVsats() on table SQL failed");
    }    
  }
  
  // No changes needed
  private void find() {
    if(station == null) return;
    if(initiating) return;
    Vsat l;
    if(station.getSelectedIndex() == -1) {
      if(station.getSelectedItem() == null) return;
      l = newOne();
    } 
    else {
      l = (Vsat) station.getSelectedItem();
    }
      
    if(l == null) return;
    String p = l.getStation();
    if(p == null) return;
    if(p.equals("")) {
      addUpdate.setEnabled(false);
      addUpdate.setText("Enter a Vsat!");
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
          error.setBackground(gov.usgs.anss.util.UC.yellow);
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
    { Util.SQLErrorPrint(E,"Vsat: SQL error getting Vsat="+p);
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
          {Util.prt("Could not connect to DB "+jcjbl); System.exit(1);}
      Show.inFrame(new VsatPanel(), UC.XSIZE, UC.YSIZE);
    }
    catch(InstantiationException e) {}
  }
}
