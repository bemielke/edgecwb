/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */

package gov.usgs.edge.snw;
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
import java.util.ArrayList;
import javax.swing.JComboBox;

/**
 *
 * requirements : 
 * The key for the file must be the name of the table and of the
 * JComboBox variable this variable is called "snwkeyname" in the initial form
 * The table name and key name should match and start lower case (snwkeyname).
 * The Class here will be that same name but with upper case at beginning(SNWKeyname).
 * <br> 1)  Rename the location JComboBox to the "key" (snwkeyname) value.  This should start Lower
 *      case.
 *<br>  2)  The table name should match this key name and be all lower case.
 *<br>  3)  Change all SNWKeyname to ClassName of underlying data (case sensitive!)
 *<br>  4)  Change all snwkeyname key value (the JComboBox above)
 *<br>  5)  clearScreen should update swing components to new defaults
 *<br>  6)  chkForm should check all field for validity.  The errorTrack variable is
 *      used to track multiple errors.  In FUtil.chk* handle checks of the various
 *      variable types.  Date, double, and IP etc return the "canonical" form for 
 *      the variable.  These should be stored in a class variable so the latest one
 *      from "chkForm()" are available to the update writer.
 *<br>  7) newone() should be updated to create a new instance of the underlying data class.
 *<br>  8) oldone() get data from database and update all swing elements to correct values
 *<br>  9) addUpdateAction() add data base set*() operations updating all fields in the DB
 *<br> 10) SNWKeynamePanel() constructor - good place to change backgrounds using UC.Look() any
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
 * Such JComboBoxes are usually initalized with 
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
public final class SNWKeynamePanel extends javax.swing.JPanel {
  
  // NOTE : here define all variables general.  "Vector snwKeys" is used for main Comboboz
  static ArrayList<SNWKeyname> snwKeys;             // Vector containing objects of this SNWKeyname Type
  private DBObject obj;
  private final ErrorTrack err=new ErrorTrack();
  private boolean initiating=false;
  
  //USER: Here are the local variables
  
  

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
   Util.prt("chkForm SNWKeyname");


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
    addUpdate.setText("Enter a SNWKeyname");
    
    //USER: Clear all fields for this form, this sets "defaults" for new screen
    description.setText("");
  }
 
  
  private SNWKeyname newOne() {
      
    return new SNWKeyname(0, ((String) snwkeyname.getSelectedItem()), "" //, more
       );
  }
  
  private void oldOne(String p) throws SQLException {
    obj = new DBObject(DBConnectionThread.getThread(DBConnectionThread.getDBSchema()), DBConnectionThread.getDBSchema(), 
            "snwkeyname","snwkeyname",p);
    

    if(obj.isNew()) {
      Util.prt("object is new"+p);
    }
    else {
      //USER: Here set all of the form fields to data from the DBObject
      //      snwkeyname.setText(obj.getString("SNWKeyname"));
      description.setText(obj.getString("description"));

        
        
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
    snwkeyname = getJComboBox();
    addUpdate = new javax.swing.JButton();
    ID = new javax.swing.JTextField();
    jLabel9 = new javax.swing.JLabel();
    error = new javax.swing.JTextField();
    labDesc = new javax.swing.JLabel();
    description = new javax.swing.JTextField();

    setLayout(new java.awt.GridBagLayout());

    jLabel1.setText("Keyname:");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 1;
    add(jLabel1, gridBagConstraints);
    jLabel1.getAccessibleContext().setAccessibleName("Keyname:");

    snwkeyname.setEditable(true);
    snwkeyname.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        snwkeynameActionPerformed(evt);
      }
    });

    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(snwkeyname, gridBagConstraints);

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
    error.setMinimumSize(new java.awt.Dimension(488, 22));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(error, gridBagConstraints);

    labDesc.setText("Description:");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 2;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    add(labDesc, gridBagConstraints);

    description.setColumns(50);
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 2;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(description, gridBagConstraints);

  }// </editor-fold>//GEN-END:initComponents

  private void addUpdateActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_addUpdateActionPerformed
    // Add your handling code here:
    FUtil.searchJComboBox(snwkeyname, true);
    // Do not change
    if(initiating) return;
    if(chkForm()) return;
    try {
      String p = snwkeyname.getSelectedItem().toString();
      if(!obj.isNew()) obj.refreshRecord();        // Refresh the result set so the sets will not fail on stale one
      obj.setString("snwkeyname",p);
      
      // USER : Set all of the fields in data base.  obj.setInt("thefield", ivalue);
      obj.setString("description",description.getText());
      
      // Do not change
      obj.updateRecord();
      snwKeys=null;       // force reload of combo box
      getJComboBox(snwkeyname);
      clearScreen();
      snwkeyname.setSelectedIndex(-1);
    }  catch  (SQLException  E)
    { Util.SQLErrorPrint(E,"snwkeyname: update failed partno="+snwkeyname.getSelectedItem());
      error.setText("Could Not Update File!");
      error.setBackground(UC.red);
      addUpdate.setEnabled(true);
    }      
  }//GEN-LAST:event_addUpdateActionPerformed

  private void snwkeynameActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_snwkeynameActionPerformed
    // Add your handling code here:
    FUtil.searchJComboBox(snwkeyname, true);
   find();
 
 
  }//GEN-LAST:event_snwkeynameActionPerformed
  
  
  // Variables declaration - do not modify//GEN-BEGIN:variables
  private javax.swing.JTextField ID;
  private javax.swing.JButton addUpdate;
  private javax.swing.JTextField description;
  private javax.swing.JTextField error;
  private java.awt.GridBagLayout gridBagLayout1;
  private javax.swing.JLabel jLabel1;
  private javax.swing.JLabel jLabel9;
  private javax.swing.JLabel labDesc;
  private javax.swing.JComboBox snwkeyname;
  // End of variables declaration//GEN-END:variables
  /** Creates new form SNWKeynamePanel */
  public SNWKeynamePanel() {
    initiating=true;
    initComponents();
    getJComboBox(snwkeyname);                // set up the key JComboBox
    snwkeyname.setSelectedIndex(-1);    // Set selected type
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
 public static JComboBox getJComboBox() { 
   JComboBox b = new JComboBox(); 
   getJComboBox(b); 
   return b;}
  /** Update a JComboBox to represent the rows represented by this panel
   *@param b The JComboBox to Update
   */
  public static void getJComboBox(JComboBox b) {
    b.removeAllItems();
    makeSNWKeynames();
    for (int i=0; i< snwKeys.size(); i++) {
      b.addItem(snwKeys.get(i));
    }
    b.setMaximumRowCount(30);
  }
  
  /** Given a JComboBox from getJComboBox, set selected item to match database ID
   *@param b The JComboBox
   *@param ID the row ID from the database to select
   */
  public static void setJComboBoxToID(JComboBox b, int ID) {
    b.setSelectedIndex(-1);
    //Util.prt("SNWKeynamePanel.setJComboBoxToID id="+ID);
    for(int i=0; i<b.getItemCount(); i++) 
      if(((SNWKeyname) b.getItemAt(i)).getID() == ID) {
          b.setSelectedIndex(i);
          //Util.prt("SNWKeynamePanel.setJComboBoxToID id="+ID+" found at "+i);
      }
  }  
  
  /** Given a database ID find the index into a JComboBox consistent with this panel
   *@param ID The row ID from the database
   *@return The index in the JComboBox with this ID
   */
  public static int getJComboBoxIndex(int ID) {
    if(snwKeys == null) makeSNWKeynames();
    for(int i=0; i<snwKeys.size(); i++) if( ID == ((SNWKeyname) snwKeys.get(i)).getID()) return i;
    return -1;
  }
  /** Cause the main JComboBox to be reloaded*/
  public static void reload() {
    snwKeys = null;
    makeSNWKeynames();
  }
 /** return a vector with all of the Groups
  * @return The vector with the rules
  */
  public static ArrayList getGroupVector() {
    if(snwKeys == null) makeSNWKeynames();
    return snwKeys;
  }
  /** Get the item corresponding to database ID
   *@param ID the database Row ID
   *@return The SNWKeyname row with this ID
   */
  public static SNWKeyname getItemWithID(int ID) {
    if(snwKeys == null) makeSNWKeynames();
    int i=getJComboBoxIndex(ID);
    if(i >= 0) return (SNWKeyname) snwKeys.get(i);
    else return null;
  }
  
  // This routine should only need tweeking if key field is not same as table name
  private static void makeSNWKeynames() {
    if (snwKeys != null) return;
    snwKeys=new ArrayList<SNWKeyname>(100);
    try {
      Statement stmt = DBConnectionThread.getConnection(DBConnectionThread.getDBSchema()).createStatement();   // used for query
      String s = "SELECT * FROM edge.snwkeyname ORDER BY snwkeyname;";
      ResultSet rs = stmt.executeQuery(s);
      while (rs.next()) {
        SNWKeyname loc = new SNWKeyname(rs);
//        Util.prt("MakeSNWKeyname() i="+snwKeys.size()+" is "+loc.getSNWKeyname());
        snwKeys.add(loc);
      }
      rs.close();
      stmt.close();
    }
    catch (SQLException e) {
      Util.SQLErrorPrint(e,"makeSNWKeynames() on table SQL failed");
    }    
  }
  
  // No changes needed
  private void find() {
    if(snwkeyname == null) return;
    if(initiating) return;
    SNWKeyname l;
    if(snwkeyname.getSelectedIndex() == -1) {
      if(snwkeyname.getSelectedItem() == null) return;
      l = newOne();
    } 
    else {
      l = (SNWKeyname) snwkeyname.getSelectedItem();
    }
      
    if(l == null) return;
    String p = l.getSNWKeyname();
    if(p == null) return;
    if(p.equals("")) {
      addUpdate.setEnabled(false);
      addUpdate.setText("Enter a SNWKeyname!");
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
    { Util.SQLErrorPrint(E,"SNWKeyname: SQL error getting SNWKeyname="+p);
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
      Show.inFrame(new SNWKeynamePanel(), UC.XSIZE, UC.YSIZE);
    }
    catch(InstantiationException e) {}
  }
}
