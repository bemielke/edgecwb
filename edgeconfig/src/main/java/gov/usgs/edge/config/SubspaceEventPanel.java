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
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import javax.swing.JComboBox;
/**
 *
 * requirements : 
 * The key for the file must be the name of the table and of the
 * JComboBox variable this variable is called "subspaceEvent" in the initial form
 * The table name and key name should match and start lower case (subspaceEvent).
 * The Class here will be that same name but with upper case at beginning(SubspaceEvent).
 * <br> 1)  Rename the location JComboBox to the "key" (subspaceEvent) value.  This should start Lower
 *      case.
 *<br>  2)  The table name should match this key name and be all lower case.
 *<br>  3)  Change all SubspaceEvent to ClassName of underlying data (case sensitive!)
 *<br>  4)  Change all subspaceEvent key value (the JComboBox above)
 *<br>  5)  clearScreen should update swing components to new defaults
 *<br>  6)  chkForm should check all field for validity.  The errorTrack variable is
 *      used to track multiple errors.  In FUtil.chk* handle checks of the various
 *      variable types.  Date, double, and IP etc return the "canonical" form for 
 *      the variable.  These should be stored in a class variable so the latest one
 *      from "chkForm()" are available to the update writer.
 *<br>  7) newone() should be updated to create a new instance of the underlying data class.
 *<br>  8) oldone() get data from database and update all swing elements to correct values
 *<br>  9) addUpdateAction() add data base set*() operations updating all fields in the DB
 *<br> 10) SubspaceEventPanel() constructor - good place to change backgrounds using UC.Look() any
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
public class SubspaceEventPanel extends javax.swing.JPanel {
  //USER: set this for the upper case conversion of keys.
  private final boolean keyUpperCase=false;    // set this true if all keys must be upper case
  //USER: Here are the local variables
  // NOTE : here define all variables general.  "ArrayList v" is used for main Comboboz
  private void doInit() {
  // Good place to add UC.Looks()
    deleteItem.setVisible(true);
    reload.setVisible(false);
    idLab.setVisible(true);
    ID.setVisible(true);
    changeTo.setVisible(true);
    labChange.setVisible(true);
    UC.Look(this);                    // Set color background

  }
  private void doAddUpdate() throws SQLException {
    // USER: Set all of the fields in data base.  obj.setInt("thefield", ivalue);
  }
  private void doOldOne() throws SQLException {
      //USER: Here set all of the form fields to data from the DBObject
      //      subspaceEvent.setText(obj.getString("SubspaceEvent"));
      // Example : description.setText(obj.getString("description"));

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


    // No CHANGES : If we found an error, color up error box
    if(err.isSet()) {
      error.setText(err.getText());
      error.setBackground(UC.red);
    }
    else addUpdate.setEnabled(true);
    return err.isSet();
  }
  
  // This routine must set the form to initial state.  It does not update the JCombobox
  protected void clearScreen() {
    
    // Do not change
    ID.setText("");
    UC.Look(error);
    UC.Look(bottomStuff);
    error.setText("");
    addUpdate.setEnabled(false);
    addUpdate.setText("Enter a SubspaceEvent");
    deleteItem.setEnabled(false);
    changeTo.setText("");
    
    //USER: Clear all fields for this form, this sets "defaults" for new screen
    // description.setText("");

  }
  
  private SubspaceEvent newOne() {
      
    return new SubspaceEvent(0, ((String) subspaceevent.getSelectedItem()).toUpperCase(),0, 0.,0.,0., 0.,"",0 //, more
       );
  }
  

  /**********************  NO USER MODIFICATION BELOW HERE EXCEPT FOR ACTIONS **************/
  static ArrayList<SubspaceEvent> v;             // ArrayList containing objects of this SubspaceEvent Type
  DBObject obj;
  ErrorTrack err=new ErrorTrack();
  boolean initiating=false;


  private void oldOne(String p) throws SQLException {
    obj = new DBObject(DBConnectionThread.getThread(DBConnectionThread.getDBSchema()), 
            DBConnectionThread.getDBSchema(),       // USER: Schema, if this is not right in URL load, it must be explicit here
            "subspaceEvent","subspaceEvent",p);                     // table name and field name are usually the same

    if(obj.isNew()) {
      //Util.prt("object is new"+p);
    }
    else {
      //USER: Here set all of the form fields to data from the DBObject
      //      subspaceEvent.setText(obj.getString("SubspaceEvent"));
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
    labMain = new javax.swing.JLabel();
    subspaceevent = getJComboBox();
    error = new javax.swing.JTextField();
    bottomStuff = new javax.swing.JPanel();
    addUpdate = new javax.swing.JButton();
    labChange = new javax.swing.JLabel();
    changeTo = new javax.swing.JTextField();
    deleteItem = new javax.swing.JButton();
    reload = new javax.swing.JButton();
    idLab = new javax.swing.JLabel();
    ID = new javax.swing.JTextField();
    labText = new javax.swing.JLabel();
    jScrollPane1 = new javax.swing.JScrollPane();
    eventText = new javax.swing.JTextArea();
    deleteEvent = new javax.swing.JButton();

    setLayout(new java.awt.GridBagLayout());

    labMain.setText("ComCatEventID:");
    labMain.setToolTipText("This button commits either a new item or the changes for an updating item.  Its label with change to reflect that.");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 1;
    add(labMain, gridBagConstraints);

    subspaceevent.setEditable(true);
    subspaceevent.setToolTipText("Select an item to edit in the JComboBox or type in the name of an item.   If the typed name does not match an existing item, an new item is assumed.");
    subspaceevent.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        subspaceeventActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(subspaceevent, gridBagConstraints);

    error.setColumns(40);
    error.setEditable(false);
    error.setMinimumSize(new java.awt.Dimension(488, 22));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 0;
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
    gridBagConstraints.gridy = 8;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(bottomStuff, gridBagConstraints);

    labText.setText("jLabel1");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 4;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    add(labText, gridBagConstraints);

    jScrollPane1.setMinimumSize(new java.awt.Dimension(600, 48));

    eventText.setEditable(false);
    eventText.setBackground(new java.awt.Color(204, 204, 204));
    eventText.setColumns(60);
    eventText.setRows(3);
    eventText.setMinimumSize(new java.awt.Dimension(600, 48));
    jScrollPane1.setViewportView(eventText);

    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 4;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(jScrollPane1, gridBagConstraints);

    deleteEvent.setText("Delete Event");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 5;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
    add(deleteEvent, gridBagConstraints);
  }// </editor-fold>//GEN-END:initComponents

  private void addUpdateActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_addUpdateActionPerformed
    // Add your handling code here:
    
    // Do not change
    if(initiating) return;
    if(chkForm()) return;
    try {
      String p = subspaceevent.getSelectedItem().toString();
      if(keyUpperCase) p = p.toUpperCase();                  // USER: drop this to allow Mixed case
      if(!obj.isNew()) obj.refreshRecord();        // Refresh the result set so the sets will not fail on stale one
      if(!changeTo.getText().equals("")) p = changeTo.getText();
      obj.setString("subspaceEvent",p);
      
      // USER: Set all of the fields in data base.  obj.setInt("thefield", ivalue);
      doAddUpdate();
      
      // Do not change
      obj.updateRecord();
      v=null;       // force reload of combo box
      getJComboBox(subspaceevent);
      clearScreen();
      subspaceevent.setSelectedIndex(-1);
    }  catch  (SQLException  E)
    { Util.SQLErrorPrint(E,"subspaceEvent: update failed partno="+subspaceevent.getSelectedItem());
      error.setText("Could Not Update File!");
      error.setBackground(UC.red);
      addUpdate.setEnabled(true);
    }      
  }//GEN-LAST:event_addUpdateActionPerformed

  private void subspaceeventActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_subspaceeventActionPerformed
    // Add your handling code here:
   find();
 
 
  }//GEN-LAST:event_subspaceeventActionPerformed

private void deleteItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_deleteItemActionPerformed
  try {
    SubspaceEvent event = (SubspaceEvent) subspaceevent.getSelectedItem();
    if(event == null) return;
    Statement stmt = DBConnectionThread.getThread(DBConnectionThread.getDBSchema()).getNewStatement(true);
    String s = "DELETE FROM edge.subspaceeventchannel WHERE eventid =" + event.getID();
    Util.prt("SEP.delete() "+s);
    stmt.executeUpdate(s);
    obj.deleteRecord();
    subspaceevent.removeItem(subspaceevent.getSelectedItem());
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
  
  
  // Variables declaration - do not modify//GEN-BEGIN:variables
  private javax.swing.JTextField ID;
  private javax.swing.JButton addUpdate;
  private javax.swing.JPanel bottomStuff;
  private javax.swing.JTextField changeTo;
  private javax.swing.JButton deleteEvent;
  private javax.swing.JButton deleteItem;
  private javax.swing.JTextField error;
  private javax.swing.JTextArea eventText;
  private java.awt.GridBagLayout gridBagLayout1;
  private javax.swing.JLabel idLab;
  private javax.swing.JScrollPane jScrollPane1;
  private javax.swing.JLabel labChange;
  private javax.swing.JLabel labMain;
  private javax.swing.JLabel labText;
  private javax.swing.JButton reload;
  private javax.swing.JComboBox subspaceevent;
  // End of variables declaration//GEN-END:variables
  /** Creates new form SubspaceEventPanel */
  public SubspaceEventPanel() {
    initiating=true;
    initComponents();
    getJComboBox(subspaceevent);                // set up the key JComboBox
    subspaceevent.setSelectedIndex(-1);    // Set selected type
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
  /** Udate a JComboBox to represent the rows represented by this panel
   *@param b The JComboBox to Update
   */
  public static void getJComboBox(JComboBox b) {
    b.removeAllItems();
    makeSubspaceEvents();
    for (SubspaceEvent v1 : v) {
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
    //Util.prt("SubspaceEventPanel.setJComboBoxToID id="+ID);
    for(int i=0; i<b.getItemCount(); i++) 
      if(((SubspaceEvent) b.getItemAt(i)).getID() == ID) {
          b.setSelectedIndex(i);
          //Util.prt("SubspaceEventPanel.setJComboBoxToID id="+ID+" found at "+i);
      }
  }  
  
  /** Given a database ID find the index into a JComboBox consistent with this panel
   *@param ID The row ID from the database
   *@return The index in the JComboBox with this ID
   */
  public static int getJComboBoxIndex(int ID) {
    if(v == null) makeSubspaceEvents();
    for(int i=0; i<v.size(); i++) if( ID == ((SubspaceEvent) v.get(i)).getID()) return i;
    return -1;
  }
  /** Cause the main JComboBox to be reloaded*/
  public static void reload() {
    v = null;
    makeSubspaceEvents();
  }
    /* return a ArrayList with all of the SubspaceEvent
   * @return The ArrayList with the subspaceEvent
   */
  public static ArrayList<SubspaceEvent> getSubspaceEventVector() {
    if(v == null) makeSubspaceEvents();
    return v;
  }    /* return a ArrayList with all of the SubspaceEvent
   * @return The ArrayList with the subspaceEvent
   */
  public static ArrayList<SubspaceEvent> getSubspaceEventArrayList() {
    if(v == null) makeSubspaceEvents();
    return v;
  }
  /** Get the item corresponding to database ID
   *@param ID the database Row ID
   *@return The SubspaceEvent row with this ID
   */
  public static SubspaceEvent getSubspaceEventWithID(int ID) {
    if(v == null) makeSubspaceEvents();
    int i=getJComboBoxIndex(ID);
    if(i >= 0) return (SubspaceEvent) v.get(i);
    else return null;
  }
  
  // This routine should only need tweeking if key field is not same as table name
  private static void makeSubspaceEvents() {
    if (v != null) return;
    v=new ArrayList<SubspaceEvent>(100);
    try {
      try (Statement stmt = DBConnectionThread.getConnection(DBConnectionThread.getDBSchema()).createStatement() // used for query
      ) {
        String s = "SELECT * FROM "+DBConnectionThread.getDBSchema()+".subspaceEvent ORDER BY subspaceEvent;"; //USER: if DBSchema is not right DB, explict here
        try (ResultSet rs = stmt.executeQuery(s)) {
          while (rs.next()) {
            SubspaceEvent loc = new SubspaceEvent(rs);
//        Util.prt("MakeSubspaceEvent() i="+v.size()+" is "+loc.getSubspaceEvent());
            v.add(loc);
          }
        }
      }
    }
    catch (SQLException e) {
      Util.SQLErrorPrint(e,"makeSubspaceEvents() on table SQL failed");
    }    
  }
  
  // No changes needed
  private void find() {
    if(subspaceevent == null) return;
    if(initiating) return;
    SubspaceEvent l;
    if(subspaceevent.getSelectedIndex() == -1) {
      if(subspaceevent.getSelectedItem() == null) return;
      l = newOne();
    } 
    else {
      l = (SubspaceEvent) subspaceevent.getSelectedItem();
    }
      
    if(l == null) return;
    String p = l.getSubspaceEvent();
    if(p == null) return;
    if(p.equals("")) {
      addUpdate.setEnabled(false);
      deleteItem.setEnabled(false);
      addUpdate.setText("Enter a SubspaceEvent!");
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
      }
      else {
        addUpdate.setText("Update "+p);
        addUpdate.setEnabled(true);
        addUpdate.setEnabled(true);
        ID.setText(""+obj.getInt("ID"));    // Info only, show ID
      }
    }  
    // This is only thrown if something happens in oldone() new DBOject
    // Other than it being a new record.
    catch  (SQLException  E)                                 // 
    { Util.SQLErrorPrint(E,"SubspaceEvent: SQL error getting SubspaceEvent="+p);
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
      Show.inFrame(new SubspaceEventPanel(), UC.XSIZE, UC.YSIZE);
    }
    catch(InstantiationException e) {}


  }
}
