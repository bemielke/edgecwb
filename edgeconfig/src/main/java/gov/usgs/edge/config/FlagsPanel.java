/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */

package gov.usgs.edge.config;
//package gov.usgs.edge.template;
import gov.usgs.anss.db.DBConnectionThread;
import gov.usgs.anss.db.DBObject;
import gov.usgs.anss.util.ErrorTrack;
import gov.usgs.anss.util.Show;
import gov.usgs.anss.util.Util;
import java.awt.Color;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import javax.swing.JComboBox;
import javax.swing.JOptionPane;
/**
 *
 * requirements : 
 * The key for the file must be the name of the table and of the
 * JComboBox variable this variable is called "flags" in the initial form
 * The table name and key name should match and start lower case (flags).
 * The Class here will be that same name but with upper case at beginning(Flags).
 * <br> 1)  Rename the location JComboBox to the "key" (flags) value.  This should start Lower
 *      case.
 *<br>  2)  The table name should match this key name and be all lower case.
 *<br>  3)  Change all Flags to ClassName of underlying data (case sensitive!)
 *<br>  4)  Change all flags key value (the JComboBox above)
 *<br>  5)  clearScreen should update swing components to new defaults
 *<br>  6)  chkForm should check all field for validity.  The errorTrack variable is
 *      used to track multiple errors.  In FUtil.chk* handle checks of the various
 *      variable types.  Date, double, and IP etc return the "canonical" form for 
 *      the variable.  These should be stored in a class variable so the latest one
 *      from "chkForm()" are available to the update writer.
 *<br>  7) newone() should be updated to create a new instance of the underlying data class.
 *<br>  8) oldone() get data from database and update all swing elements to correct values
 *<br>  9) addUpdateAction() add data base set*() operations updating all fields in the DB
 *<br> 10) FlagsPanel() constructor - good place to change backgrounds using UC.Look() any
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
 *<br> FUtil.getEnumJComboBox(DBConnectionThread.getConnection("edge"), "tcpstation","rtsport1");
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
 * <br>    if(fieldEnum == null) fieldEnum = FUtil.getEnum(DBConnectionThread.getConnection("edge"),"table","fieldName");
 * <br>    
 * <br>   // Get the int corresponding to an Enum String for storage in the data base and 
 * <br>   // the index into the JComboBox representing this Enum 
 * <br>   localVariable = FUtil.enumStringToInt(fieldEnum, enumString);
  * <br>
 * @author  D.C. Ketchum
 */
public class FlagsPanel extends javax.swing.JPanel {
  
  // NOTE : here define all variables general.  "Vector v" is used for main Comboboz
  static ArrayList<Flags> v;             // Vector containing objects of this Flags Type
  DBObject obj;
  ErrorTrack err=new ErrorTrack();
  boolean initiating=false;
  
  // Here are the local variables
  
  

  // This routine must validate all fields on the form.  The err (ErrorTrack) variable
  // is used by the FUtil to verify fields.  Use FUTil or custom code here for verifications
  private boolean chkForm() {
    // Do not change
   err.reset();
   UC.Look(error);
   
   //USER: Your error checking code goes here setting the local variables to valid settings
   // note : Use FUtil.chk* to check dates, timestamps, IP addresses, etc and put them in
   // canonical form.  store in a class variable.  For instance :
   //   sdate = FUtil.chkDate(dateTextField, err);
   // Any errors found should err.set(true);  and err.append("error message");
   Util.prt("chkForm Flags");
    if( obj.getMaxID() >= 64) {
      Util.prt("Too many for a bit map field !!!");
      err.appendText("Too many for a bit map"); err.set();
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
    addUpdate.setText("Enter a Flags");
    
    //USER: Clear all fields for this form, this sets "defaults" for new screen
    description.setText("");
    hasData.setSelected(false);
    newFlag.setText("");
  }
 
  
  private Flags newOne() {
    // USER: add all fields needed for a Flags
    return new Flags(0, (String) flags.getSelectedItem(),
        description.getText(), 0//, more
       );
  }
  
  private void oldOne(String p) throws SQLException {
    obj = new DBObject(DBConnectionThread.getThread(DBConnectionThread.getDBSchema()), DBConnectionThread.getDBSchema(), "flags","flags",p);

    if(obj.isNew()) {
      Util.prt("object is new"+p);
    }
    else {
      // Here set all of the form fields to data from the DBObject
      //      flags.setText(obj.getString("Flags"));
      // Example : description.setText(obj.getString("description"));
      description.setText(obj.getString("description"));
      if(obj.getInt("hasdata") != 0) hasData.setSelected(true);
      else hasData.setSelected(false);
        
        
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
    jLabel1 = new javax.swing.JLabel();
    flags = getJComboBox();
    addUpdate = new javax.swing.JButton();
    ID = new javax.swing.JTextField();
    jLabel9 = new javax.swing.JLabel();
    error = new javax.swing.JTextField();
    labDesc = new javax.swing.JLabel();
    description = new javax.swing.JTextField();
    hasData = new javax.swing.JRadioButton();
    clearBits = new javax.swing.JButton();
    labNewFlag = new javax.swing.JLabel();
    newFlag = new javax.swing.JTextField();

    setLayout(new java.awt.GridBagLayout());

    jLabel1.setText("Flag Name :");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 1;
    add(jLabel1, gridBagConstraints);

    flags.setEditable(true);
    flags.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        flagsActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(flags, gridBagConstraints);

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
    ID.setMinimumSize(new java.awt.Dimension(104, 22));
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
    error.setMinimumSize(new java.awt.Dimension(488, 22));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(error, gridBagConstraints);

    labDesc.setText("Description:");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 3;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    add(labDesc, gridBagConstraints);

    description.setColumns(80);
    description.setMinimumSize(new java.awt.Dimension(408, 22));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 3;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(description, gridBagConstraints);

    hasData.setText("Has Side Data?");
    hasData.setMargin(new java.awt.Insets(0, 0, 0, 0));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 4;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(hasData, gridBagConstraints);

    clearBits.setText("Clear this bit in all channels");
    clearBits.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        clearBitsActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 16;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_END;
    add(clearBits, gridBagConstraints);

    labNewFlag.setText("RenameFlag:");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 19;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_END;
    add(labNewFlag, gridBagConstraints);

    newFlag.setColumns(30);
    newFlag.setMinimumSize(new java.awt.Dimension(374, 28));
    newFlag.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        newFlagActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 19;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
    add(newFlag, gridBagConstraints);
  }// </editor-fold>//GEN-END:initComponents

  private void addUpdateActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_addUpdateActionPerformed
    // Add your handling code here:
    
    // Do not change
    if(initiating) return;
    if(chkForm()) return;
    try {
      String p = flags.getSelectedItem().toString();
      if(!obj.isNew()) obj.refreshRecord();        // Refresh the result set so the sets will not fail on stale one
      obj.setString("flags",p);
      if(!newFlag.getText().equals("")) obj.setString("flags",newFlag.getText());
      // USER : Set all of the fields in data base.  obj.setInt("thefield", ivalue);
      obj.setString("description", description.getText());
      if(hasData.isSelected()) obj.setInt("hasdata",1);
      else obj.setInt("hasdata",0);
      
      // Do not change
      obj.updateRecord();
      v=null;       // force reload of combo box
      getJComboBox(flags);
      clearScreen();
      flags.setSelectedIndex(-1);
    }  catch  (SQLException  E)
    { Util.SQLErrorPrint(E,"flags: update failed partno="+flags.getSelectedItem());
      error.setText("Could Not Update File!");
      error.setBackground(UC.red);
      addUpdate.setEnabled(true);
    }      
  }//GEN-LAST:event_addUpdateActionPerformed

  private void flagsActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_flagsActionPerformed
    // Add your handling code here:
   find();
 
 
  }//GEN-LAST:event_flagsActionPerformed

  private void clearBitsActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_clearBitsActionPerformed
    if(obj.getID() >0 && obj.getID() < 65 && !obj.isNew() && flags.getSelectedIndex() >=0) {
      Flags t  = (Flags) flags.getSelectedItem();
      String ans = JOptionPane.showInputDialog(null, "Are you sure you want to clear the "+t.getFlags()+" bit on all Channels (Y/N)?");
      if( ans.equalsIgnoreCase("y")) {      
        try {
          Statement stmt = DBConnectionThread.getConnection(DBConnectionThread.getDBSchema()).createStatement();   // used for query
          String s = "UPDATE edge.channel SET flags = flags & ~(1<< "+(obj.getID()-1)+")";
          int nrec = stmt.executeUpdate(s);
          Util.prt("clear bits nrec="+nrec);
        }
        catch(SQLException e) {
          e.printStackTrace();
        }
      }
    }
  }//GEN-LAST:event_clearBitsActionPerformed

  private void newFlagActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_newFlagActionPerformed
    // TODO add your handling code here:
  }//GEN-LAST:event_newFlagActionPerformed
  
  
  // Variables declaration - do not modify//GEN-BEGIN:variables
  private javax.swing.JTextField ID;
  private javax.swing.JButton addUpdate;
  private javax.swing.JButton clearBits;
  private javax.swing.JTextField description;
  private javax.swing.JTextField error;
  private javax.swing.JComboBox flags;
  private java.awt.GridBagLayout gridBagLayout1;
  private javax.swing.JRadioButton hasData;
  private javax.swing.JLabel jLabel1;
  private javax.swing.JLabel jLabel9;
  private javax.swing.JLabel labDesc;
  private javax.swing.JLabel labNewFlag;
  private javax.swing.JTextField newFlag;
  // End of variables declaration//GEN-END:variables
  /** Creates new form FlagsPanel. */
  public FlagsPanel() {
    initiating=true;
    initComponents();
    getJComboBox(flags);                // set up the key JComboBox
    flags.setSelectedIndex(-1);    // Set selected type
    Look();                    // Set color background
    initiating=false;
   
    // USER:This is a good place for UC.Look(components) to get rid of the ugly Grey
    
    clearScreen();                    // Start with empty screen
  }
  private void Look() {
    UC.Look(this);                    // Set color background
    
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
    makeFlagss();
    for (int i=0; i< v.size(); i++) {
      b.addItem( v.get(i));
    }
    b.setMaximumRowCount(30);
  }
  
  /** Given a JComboBox from getJComboBox, set selected item to match database ID
   *@param b The JComboBox
   *@param ID the row ID from the database to select
   */
  public static void setJComboBoxToID(JComboBox b, int ID) {
    b.setSelectedIndex(-1);
    //Util.prt("FlagsPanel.setJComboBoxToID id="+ID);
    for(int i=0; i<b.getItemCount(); i++) 
      if(((Flags) b.getItemAt(i)).getID() == ID) {
          b.setSelectedIndex(i);
          //Util.prt("FlagsPanel.setJComboBoxToID id="+ID+" found at "+i);
      }
  }  
  
  /** Given a database ID find the index into a JComboBox consistent with this panel
   *@param ID The row ID from the database
   *@return The index in the JComboBox with this ID
   */
  public static int getJComboBoxIndex(int ID) {
    for(int i=0; i<v.size(); i++) if( ID == ((Flags) v.get(i)).getID()) return i;
    return -1;
  }
  /** Cause the main JComboBox to be reloaded*/
  public static void reload() {
    v = null;
    makeFlagss();
  }
  
  /** Get the item corresponding to database ID
   *@param ID the database Row ID
   *@return The Flags row with this ID
   */
  public static Flags getItemWithID(int ID) {
    int i=getJComboBoxIndex(ID);
    if(i >= 0) return (Flags) v.get(i);
    else return null;
  }
  
  // This routine should only need tweeking if key field is not same as table name
  private static void makeFlagss() {
    if (v != null) return;
    v=new ArrayList<Flags>(100);
    try {
      Statement stmt = DBConnectionThread.getConnection(DBConnectionThread.getDBSchema()).createStatement();   // used for query
      String s = "SELECT * FROM edge.flags ORDER BY flags;";
      ResultSet rs = stmt.executeQuery(s);
      while (rs.next()) {
        Flags loc = new Flags(rs);
//        Util.prt("MakeFlags() i="+v.size()+" is "+loc.getFlags());
        v.add(loc);
      }
      rs.close();
      stmt.close();
    }
    catch (SQLException e) {
      Util.SQLErrorPrint(e,"makeFlagss() on table SQL failed");
    }    
  }
  
  // No changes needed
  private void find() {
    if(flags == null) return;
    if(initiating) return;
    Flags l;
    if(flags.getSelectedIndex() == -1) {
      if(flags.getSelectedItem() == null) return;
      l = newOne();
    } 
    else {
      l = (Flags) flags.getSelectedItem();
    }
      
    if(l == null) return;
    String p = l.getFlags();
    if(p == null) return;
    if(p.equals("")) {
      addUpdate.setEnabled(false);
      addUpdate.setText("Enter a Flags!");
    }
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
          if(obj.getMaxID() >= 64) chkForm();
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
    { Util.SQLErrorPrint(E,"Flags: SQL error getting Flags="+p);
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
      Show.inFrame(new FlagsPanel(), UC.XSIZE, UC.YSIZE);
    }
    catch(InstantiationException e) {}
  }
}
