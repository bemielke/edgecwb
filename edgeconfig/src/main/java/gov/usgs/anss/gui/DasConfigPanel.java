/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */

package gov.usgs.anss.gui;
import gov.usgs.anss.guidb.DasConfig;
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

/**
 *
 * requirements : 
 * The key for the file must be the name of the table and of the
 * JComboBox variable this variable is called "dasconfig" in the initial form
 * The table name and key name should match and start lower case (dasconfig).
 * The Class here will be that same name but with upper case at beginning(DasConfig).
 * <br> 1)  Rename the location JComboBox to the "key" (dasconfig) value.  This should start Lower
 *      case.
 *<br>  2)  The table name should match this key name and be all lower case.
 *<br>  3)  Change all DasConfig to ClassName of underlying data (case sensitive!)
 *<br>  4)  Change all dasconfig key value (the JComboBox above)
 *<br>  5)  clearScreen should update swing components to new defaults
 *<br>  6)  chkForm should check all field for validity.  The errorTrack variable is
 *      used to track multiple errors.  In FUtil.chk* handle checks of the various
 *      variable types.  Date, double, and IP etc return the "canonical" form for 
 *      the variable.  These should be stored in a class variable so the latest one
 *      from "chkForm()" are available to the update writer.
 *<br>  7) newone() should be updated to create a new instance of the underlying data class.
 *<br>  8) oldone() get data from database and update all swing elements to correct values
 *<br>  9) addUpdateAction() add data base set*() operations updating all fields in the DB
 *<br> 10) DasConfigPanel() constructor - good place to change backgrounds using UC.Look() any
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
public final class DasConfigPanel extends javax.swing.JPanel {
  
  // NOTE : here define all variables general.  "ArrayList v" is used for main Comboboz
  static ArrayList<DasConfig> v;             // ArrayList containing objects of this DasConfig Type
  private DBObject obj;
  private final ErrorTrack err=new ErrorTrack();
  private boolean initiating=false;
  
  //USER: Here are the local variables
  
  

  // This routine must validate all fields on the form.  The err (ErrorTrack) variable
  // is used by the FUtil to verify fields.  Use FUTil or custom code here for verifications
  private boolean chkForm() {
    // Do not change
   err.reset();
   error.setText("");
   UC.Look(error);
   
   //USER:  Your error checking code goes here setting the local variables to valid settings
   // note : Use FUtil.chk* to check dates, timestamps, IP addresses, etc and put them in
   // canonical form.  store in a class variable.  For instance :
   //   sdate = FUtil.chkDate(dateTextField, err);
   // Any errors found should err.set(true);  and err.append("error message");
   Util.prt("chkForm DasConfig");


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
    addUpdate.setText("Enter a DasConfig");
    
    //USER: Clear all fields for this form, this sets "defaults" for new screen
    // description.setText("");
    text.setText("");
    fillGroup.setText("");
    expected.setText("");
  }
 
  
  private DasConfig newOne() {
      
    return new DasConfig(0, ((String) dasconfig.getSelectedItem()).toUpperCase(), "","","" //, more
       );
  }
  
  private void oldOne(String p) throws SQLException {
    obj = new DBObject(DBConnectionThread.getThread(DBConnectionThread.getDBSchema()), DBConnectionThread.getDBSchema(), "dasconfig","dasconfig",p);


    if(obj.isNew()) {
      Util.prt("object is new"+p);
    }
    else {
      //USER: Here set all of the form fields to data from the DBObject
      //      dasconfig.setText(obj.getString("DasConfig"));
      // Example : description.setText(obj.getString("description"));
      text.setText(obj.getString("text"));
      fillGroup.setText(obj.getString("filltype"));
      expected.setText(obj.getString("expectedchannels"));
      
      
        
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
    dasconfig = getJComboBox();
    addUpdate = new javax.swing.JButton();
    ID = new javax.swing.JTextField();
    jLabel9 = new javax.swing.JLabel();
    error = new javax.swing.JTextField();
    jLabel2 = new javax.swing.JLabel();
    jScrollPane1 = new javax.swing.JScrollPane();
    text = new javax.swing.JTextArea();
    labFillGroup = new javax.swing.JLabel();
    labChans = new javax.swing.JLabel();
    fillGroup = new javax.swing.JTextField();
    expected = new javax.swing.JTextField();

    setLayout(new java.awt.GridBagLayout());

    jLabel1.setText("DasConfig:");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 1;
    add(jLabel1, gridBagConstraints);

    dasconfig.setEditable(true);
    dasconfig.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        dasconfigActionPerformed(evt);
      }
    });

    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(dasconfig, gridBagConstraints);

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

    jLabel2.setText("Comment:");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 6;
    add(jLabel2, gridBagConstraints);

    jScrollPane1.setMinimumSize(new java.awt.Dimension(550, 164));
    text.setColumns(60);
    text.setFont(new java.awt.Font("Courier New", 0, 12));
    text.setRows(10);
    jScrollPane1.setViewportView(text);

    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 6;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(jScrollPane1, gridBagConstraints);

    labFillGroup.setText("FillGroup GP,GN,etc):");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 3;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    add(labFillGroup, gridBagConstraints);

    labChans.setText("Expected Channels:");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 4;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    add(labChans, gridBagConstraints);

    fillGroup.setColumns(3);
    fillGroup.setMinimumSize(new java.awt.Dimension(44, 22));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 3;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(fillGroup, gridBagConstraints);

    expected.setColumns(50);
    expected.setMinimumSize(new java.awt.Dimension(550, 22));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 4;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(expected, gridBagConstraints);

  }// </editor-fold>//GEN-END:initComponents

  private void addUpdateActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_addUpdateActionPerformed
    // Add your handling code here:
    
    // Do not change
    if(initiating) return;
    if(chkForm()) return;
    try {
      String p = dasconfig.getSelectedItem().toString();
      if(!obj.isNew()) obj.refreshRecord();        // Refresh the result set so the sets will not fail on stale one
      p = p.toUpperCase();                  // USER : drop this to allow Mixed case
      obj.setString("dasconfig",p);
      
      // USER : Set all of the fields in data base.  obj.setInt("thefield", ivalue);
      obj.setString("text",text.getText());
      obj.setString("filltype",fillGroup.getText());
      obj.setString("expectedchannels", expected.getText());
      // Do not change
      obj.updateRecord();
      v=null;       // force reload of combo box
      getJComboBox(dasconfig);
      clearScreen();
      dasconfig.setSelectedIndex(-1);
    }  catch  (SQLException  E)
    { Util.SQLErrorPrint(E,"dasconfig: update failed partno="+dasconfig.getSelectedItem());
      error.setText("Could Not Update File!");
      error.setBackground(UC.red);
      addUpdate.setEnabled(true);
    }      
  }//GEN-LAST:event_addUpdateActionPerformed

  private void dasconfigActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_dasconfigActionPerformed
    // Add your handling code here:
   find();
 
 
  }//GEN-LAST:event_dasconfigActionPerformed
  
  
  // Variables declaration - do not modify//GEN-BEGIN:variables
  private javax.swing.JTextField ID;
  private javax.swing.JButton addUpdate;
  private javax.swing.JComboBox dasconfig;
  private javax.swing.JTextField error;
  private javax.swing.JTextField expected;
  private javax.swing.JTextField fillGroup;
  private java.awt.GridBagLayout gridBagLayout1;
  private javax.swing.JLabel jLabel1;
  private javax.swing.JLabel jLabel2;
  private javax.swing.JLabel jLabel9;
  private javax.swing.JScrollPane jScrollPane1;
  private javax.swing.JLabel labChans;
  private javax.swing.JLabel labFillGroup;
  private javax.swing.JTextArea text;
  // End of variables declaration//GEN-END:variables
  /** Creates new form DasConfigPanel */
  public DasConfigPanel() {
    initiating=true;
    initComponents();
    getJComboBox(dasconfig);                // set up the key JComboBox
    dasconfig.setSelectedIndex(-1);    // Set selected type
    UC.Look(this);                    // Set color background
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
    makeDasConfigs();
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
    //Util.prt("DasConfigPanel.setJComboBoxToID id="+ID);
    for(int i=0; i<b.getItemCount(); i++) 
      if(((DasConfig) b.getItemAt(i)).getID() == ID) {
          b.setSelectedIndex(i);
          //Util.prt("DasConfigPanel.setJComboBoxToID id="+ID+" found at "+i);
      }

  }  
  
  /** Given a database ID find the index into a JComboBox consistent with this panel
   *@param ID The row ID from the database
   *@return The index in the JComboBox with this ID
   */
  public static int getJComboBoxIndex(int ID) {
    for(int i=0; i<v.size(); i++) if( ID == ((DasConfig) v.get(i)).getID()) return i;
    return -1;
  }
  /** Cause the main JComboBox to be reloaded*/
  public static void reload() {
    v = null;
    makeDasConfigs();
  }
  
  /** Get the item corresponding to database ID
   *@param ID the database Row ID
   *@return The DasConfig row with this ID
   */
  public static DasConfig getItemWithID(int ID) {
    int i=getJComboBoxIndex(ID);
    if(i >= 0) return (DasConfig) v.get(i);
    else return null;
  }
  
  // This routine should only need tweeking if key field is not same as table name
  private static void makeDasConfigs() {
    if (v != null) return;
    v=new ArrayList<DasConfig>(100);
    try {
      Statement stmt = DBConnectionThread.getConnection(DBConnectionThread.getDBSchema()).createStatement();   // used for query
      String s = "SELECT * FROM anss.dasconfig ORDER BY dasconfig;";
      ResultSet rs = stmt.executeQuery(s);
      while (rs.next()) {
        DasConfig loc = new DasConfig(rs);
//        Util.prt("MakeDasConfig() i="+v.size()+" is "+loc.getDasConfig());
        v.add(loc);
      }
      rs.close();
      stmt.close();
    }
    catch (SQLException e) {
      Util.SQLErrorPrint(e,"makeDasConfigs() on table SQL failed");

    }    
  }
  
  // No changes needed
  private void find() {
    if(dasconfig == null) return;
    if(initiating) return;
    DasConfig l;
    if(dasconfig.getSelectedIndex() == -1) {
      if(dasconfig.getSelectedItem() == null) return;
      l = newOne();
    } 
    else {
      l = (DasConfig) dasconfig.getSelectedItem();
    }
      
    if(l == null) return;
    String p = l.getDasConfig();
    if(p == null) return;
    if(p.equals("")) {
      addUpdate.setEnabled(false);
      addUpdate.setText("Enter a DasConfig!");
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
    { Util.SQLErrorPrint(E,"DasConfig: SQL error getting DasConfig="+p);
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
      Show.inFrame(new DasConfigPanel(), UC.XSIZE, UC.YSIZE);
    }
    catch(InstantiationException e) {}
  }
}
