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
 * JComboBox variable this variable is called "suppression" in the initial form
 * The table name and key name should match and start lower case (suppression).
 * The Class here will be that same name but with upper case at beginning(Suppression).
 * <br> 1)  Rename the location JComboBox to the "key" (suppression) value.  This should start Lower
 *      case.
 * <br>  2)  The table name should match this key name and be all lower case.
 * <br>  3)  Change all Suppression to ClassName of underlying data (case sensitive!)
 * <br>  4)  Change all suppression key value (the JComboBox above)
 * <br>  5)  clearScreen should update swing components to new defaults
 * <br>  6)  chkForm should check all field for validity.  The errorTrack variable is
 *      used to track multiple errors.  In FUtil.chk* handle checks of the various
 *      variable types.  Date, double, and IP etc return the "canonical" form for 
 *      the variable.  These should be stored in a class variable so the latest one
 *      from "chkForm()" are available to the update writer.
 * <br>  7) newone() should be updated to create a new instance of the underlying data class.
 * <br>  8) oldone() get data from database and update all swing elements to correct values
 * <br>  9) addUpdateAction() add data base set*() operations updating all fields in the DB
 * <br> 10) SuppressionPanel() constructor - good place to change backgrounds using UC.Look() any
 *    other weird startup stuff.
 * 
 * <br>  local variable error - Must be a JTextField for posting error communications
 * <br> local variable updateAdd - Must be the JButton for user clicks to try to post the data
 * <br> ID - JTextField which must be non-editable for posting Database IDs
 * <br>
 * <br>
 * ENUM notes :  the ENUM are read from the database as type String.  So the constructor
 * should us the strings to set the indexes.  However, the writes to the database are
 * done as ints representing the value of the Enum.  The JComboBox with the Enum has 
 * the strings as Items in the list with indexes equal to the database value of the int.
 * Such JComboBoxes are usually initialized with 
 * <br>
 * <br> FUtil.getEnumJComboBox(DBConnectionThread.getConnection("alarm"), "tcpstation","rtsport1");
 * <br> Put this in "CodeGeneration"  "CustomCreateCode" of the form editor for each Enum.
 * <br>
 * <br> In  oldone() get Enum fields with something like :
 * <br>  fieldJcomboBox.setSelectedItem(obj.getString("fieldName"));
 * <br>  (This sets the JComboBox to the Item matching the string)
 * <br>
 * data class should have code like :
 * <br><br>  import java.util.ArrayList;          /// Import for Enum manipulation
 * <br>    .
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
 * 
 * @author D.C. Ketchum
 */
public final class SuppressionPanel extends javax.swing.JPanel {
  
  // NOTE : here define all variables general.  "ArrayList suppressions" is used for main Comboboz
  static ArrayList<Suppression> suppressions;             // ArrayList containing objects of this Suppression Type
  private DBObject obj;
  private final ErrorTrack err=new ErrorTrack();
  private boolean initiating=false;
  
  //USER: Here are the local variables
  private int eventIDInt;
  private int suppressionGroupIDInt;
  private int expireDateInt;
  private Timestamp exp;
  

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
   Util.prt("chkForm Suppression");
   
//   eventIDInt = FUtil.chkInt(eventID,err);
//   suppressionGroupIDInt = FUtil.chkInt(suppressionGroupID,err);
   exp = FUtil.chkTimestamp(expires,err);
   

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
    addUpdate.setText("Enter a Suppression");
    
    //USER: Clear all fields for this form, this sets "defaults" for new screen
    // description.setText("");
    eventID.setSelectedIndex(-1);
    suppressionGroupID.setSelectedIndex(-1);
    expires.setText("00:00:00");

    
  }
//   public Suppression(int inID, String loc   //USER: add fields, double lon
//      , int eID, int tID, int dact, int act, Timestamp start, Timestamp end, Timestamp start2, Timestamp end2,int act2
//    )
  
  private Suppression newOne() {
      Timestamp t = new Timestamp(0l);
    return new Suppression(0, ((String) suppression.getSelectedItem()).toUpperCase(), //, more
       0, // eventID
       0, // suppressionGroupID
       t // expires
       );
  }
  
  private void oldOne(String p) throws SQLException {
      obj = new DBObject(DBConnectionThread.getThread(DBConnectionThread.getDBSchema()), 
      "alarm", "suppression","suppression",p);

    if(obj.isNew()) {
      Util.prt("object is new"+p);
    }
    else {
      //USER: Here set all of the form fields to data from the DBObject
      //      suppression.setText(obj.getString("Suppression"));
      // Example : description.setText(obj.getString("description"));

      EventPanel.setJComboBoxToID(eventID, obj.getInt("eventID"));
      SuppressionGroupPanel.setJComboBoxToID(suppressionGroupID, obj.getInt("suppressionGroupID"));
      expires.setText(obj.getTimestamp("expires").toString().substring(0,19));        
        
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
    labSuppression = new javax.swing.JLabel();
    suppression = getJComboBox();
    addUpdate = new javax.swing.JButton();
    ID = new javax.swing.JTextField();
    labID = new javax.swing.JLabel();
    error = new javax.swing.JTextField();
    labEventID = new javax.swing.JLabel();
    labSuppressionGroupID = new javax.swing.JLabel();
    labExpires = new javax.swing.JLabel();
    eventID = EventPanel.getJComboBox();
    suppressionGroupID = SuppressionGroupPanel.getJComboBox();
    expires = new javax.swing.JTextField();
    delete = new javax.swing.JButton();
    reload = new javax.swing.JButton();

    setLayout(new java.awt.GridBagLayout());

    labSuppression.setText("Suppression : ");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    add(labSuppression, gridBagConstraints);

    suppression.setEditable(true);
    suppression.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        suppressionActionPerformed(evt);
      }
    });

    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(suppression, gridBagConstraints);

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
    ID.setMaximumSize(new java.awt.Dimension(92, 20));
    ID.setMinimumSize(new java.awt.Dimension(92, 20));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 17;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(ID, gridBagConstraints);

    labID.setText("ID :");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 17;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    add(labID, gridBagConstraints);

    error.setColumns(40);
    error.setEditable(false);
    error.setMinimumSize(new java.awt.Dimension(488, 22));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(error, gridBagConstraints);

    labEventID.setText("Event : ");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 2;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    add(labEventID, gridBagConstraints);

    labSuppressionGroupID.setText("SuppressionGroup : ");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 5;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    add(labSuppressionGroupID, gridBagConstraints);

    labExpires.setText("Expires (yyyy-mm-dd hh:mm) : ");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 7;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    add(labExpires, gridBagConstraints);

    eventID.setEditable(true);
    eventID.setMaximumSize(new java.awt.Dimension(129, 25));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 2;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(eventID, gridBagConstraints);

    suppressionGroupID.setEditable(true);
    suppressionGroupID.setMaximumSize(new java.awt.Dimension(129, 25));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 5;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(suppressionGroupID, gridBagConstraints);

    expires.setMaximumSize(new java.awt.Dimension(129, 25));
    expires.setMinimumSize(new java.awt.Dimension(129, 25));
    expires.setPreferredSize(new java.awt.Dimension(129, 25));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 7;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(expires, gridBagConstraints);

    delete.setText("Delete");
    delete.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        deleteActionPerformed(evt);
      }
    });

    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 19;
    add(delete, gridBagConstraints);

    reload.setText("Reload");
    reload.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        reloadActionPerformed(evt);
      }
    });

    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 19;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(reload, gridBagConstraints);

  }// </editor-fold>//GEN-END:initComponents

  private void deleteActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_deleteActionPerformed
// TODO add your handling code here:
    try {
        obj.deleteRecord();
        SuppressionPanel.reload();
        SuppressionPanel.getJComboBox(suppression);
        suppression.setSelectedIndex(-1);
        clearScreen();
      }
      catch(SQLException e) {Util.SQLErrorPrint(e,"Error deleting record");}
  }//GEN-LAST:event_deleteActionPerformed

  private void reloadActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_reloadActionPerformed
// TODO add your handling code here:
    EventPanel.getJComboBox();
    SuppressionGroupPanel.getJComboBox();
  }//GEN-LAST:event_reloadActionPerformed

  private void addUpdateActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_addUpdateActionPerformed
    // Add your handling code here:
    
    // Do not change
    if(initiating) return;
    if(chkForm()) return;
    try {
      String p = suppression.getSelectedItem().toString();
      p = p.toUpperCase();                  // USER : drop this to allow Mixed case
      obj.setString("suppression",p);
      
      // USER : Set all of the fields in data base.  obj.setInt("thefield", ivalue);
      if(eventID.getSelectedIndex() != -1) obj.setInt("eventID", ((Event) eventID.getSelectedItem()).getID());
      else obj.setInt("eventID",0);
      if(suppressionGroupID.getSelectedIndex() != -1) obj.setInt("suppressionGroupID", ((SuppressionGroup) suppressionGroupID.getSelectedItem()).getID());
      else obj.setInt("suppressionGroupID",0);
      obj.setTimestamp("expires",exp);  
      
      // Do not change
      obj.updateRecord();
      suppressions=null;       // force reload of combo box
      getJComboBox(suppression);
      clearScreen();
      suppression.setSelectedIndex(-1);
    }  catch  (SQLException  E)
    { Util.SQLErrorPrint(E,"suppression: update failed partno="+suppression.getSelectedItem());
      error.setText("Could Not Update File!");
      error.setBackground(UC.red);
      addUpdate.setEnabled(true);
    }      
  }//GEN-LAST:event_addUpdateActionPerformed

  private void suppressionActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_suppressionActionPerformed
    // Add your handling code here:
   find();
 
 
  }//GEN-LAST:event_suppressionActionPerformed
  
  
  // Variables declaration - do not modify//GEN-BEGIN:variables
  private javax.swing.JTextField ID;
  private javax.swing.JButton addUpdate;
  private javax.swing.JButton delete;
  private javax.swing.JTextField error;
  private javax.swing.JComboBox eventID;
  private javax.swing.JTextField expires;
  private java.awt.GridBagLayout gridBagLayout1;
  private javax.swing.JLabel labEventID;
  private javax.swing.JLabel labExpires;
  private javax.swing.JLabel labID;
  private javax.swing.JLabel labSuppression;
  private javax.swing.JLabel labSuppressionGroupID;
  private javax.swing.JButton reload;
  private javax.swing.JComboBox suppression;
  private javax.swing.JComboBox suppressionGroupID;
  // End of variables declaration//GEN-END:variables
  /**
   * Creates new form SuppressionPanel
   */
  public SuppressionPanel() {
    initiating=true;
    initComponents();
    getJComboBox(suppression);                // set up the key JComboBox
    suppression.setSelectedIndex(-1);    // Set selected type
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
    makeSuppression();
    for (int i=0; i< suppressions.size(); i++) {
      b.addItem(suppressions.get(i));
    }
    b.setMaximumRowCount(30);
  }
  
  /** Given a JComboBox from getJComboBox, set selected item to match database ID
   *@param b The JComboBox
   *@param ID the row ID from the database to select
   */
  public static void setJComboBoxToID(JComboBox b, int ID) {
    b.setSelectedIndex(-1);
    //Util.prt("SuppressionPanel.setJComboBoxToID id="+ID);
    for(int i=0; i<b.getItemCount(); i++) 
      if(((Suppression) b.getItemAt(i)).getID() == ID) {
          b.setSelectedIndex(i);
          //Util.prt("SuppressionPanel.setJComboBoxToID id="+ID+" found at "+i);
      }
  }  
  
  /** Given a database ID find the index into a JComboBox consistent with this panel
   *@param ID The row ID from the database
   *@return The index in the JComboBox with this ID
   */
  public static int getJComboBoxIndex(int ID) {
    if(suppressions == null) makeSuppression();
    for(int i=0; i<suppressions.size(); i++) if( ID == ((Suppression) suppressions.get(i)).getID()) return i;
    return -1;
  }
  /** Cause the main JComboBox to be reloaded. */
  public static void reload() {
    suppressions = null;
    makeSuppression();
  }
   /** return a ArrayList with all of the Suppression.
    *@return The ArrayList with the suppression
    */
  public static ArrayList<Suppression> getSuppressionArrayList() {
    if(suppressions == null) makeSuppression();
    return suppressions;
  }
  /** Get the item corresponding to database ID
   *@param ID the database Row ID
   *@return The Suppression row with this ID
   */
  public static Suppression getItemWithID(int ID) {
    if(suppressions == null) makeSuppression();
    int i=getJComboBoxIndex(ID);
    if(i >= 0) return (Suppression) suppressions.get(i);
    else return null;
  }
  
  // This routine should only need tweeking if key field is not same as table name
  private static void makeSuppression() {
    if (suppressions != null) return;
    suppressions=new ArrayList<Suppression>(100);
    try {
      Statement stmt = DBConnectionThread.getConnection(DBConnectionThread.getDBSchema()).createStatement();   // used for query
      String s = "SELECT * FROM alarm.suppression ORDER BY suppression;";
      ResultSet rs = stmt.executeQuery(s);
      while (rs.next()) {
        Suppression loc = new Suppression(rs);
//        Util.prt("MakeSuppression() i="+suppressions.size()+" is "+loc.getSuppression());
        suppressions.add(loc);
      }
    }
    catch (SQLException e) {
      Util.SQLErrorPrint(e,"makeSuppression() on table SQL failed");
    }    
  }
  
  // No changes needed
  private void find() {
    if(suppression == null) return;
    if(initiating) return;
    Suppression l;
    if(suppression.getSelectedIndex() == -1) {
      if(suppression.getSelectedItem() == null) return;
      l = newOne();
    } 
    else {
      l = (Suppression) suppression.getSelectedItem();
    }
      
    if(l == null) return;
    String p = l.getSuppression();
    if(p == null) return;
    if(p.equals("")) {
      addUpdate.setEnabled(false);
      addUpdate.setText("Enter a Suppression!");
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
          EventPanel.setJComboBoxToID(eventID,obj.getInt("eventID"));
          SuppressionGroupPanel.setJComboBoxToID(suppressionGroupID,obj.getInt("suppressionGroupID"));
          expires.setText(obj.getTimestamp("expires").toString().substring(0,19)); 
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
    { Util.SQLErrorPrint(E,"Suppression: SQL error getting Suppression="+p);
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
      Show.inFrame(new SuppressionPanel(), UC.XSIZE, UC.YSIZE);
    }
    catch(InstantiationException e) {}
  }
  
}
