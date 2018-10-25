/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */

package gov.usgs.anss.gui;
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
 * JComboBox variable this variable is called "remoteCommand" in the initial form
 * The table name and key name should match and start lower case (remoteCommand).
 * The Class here will be that same name but with upper case at beginning(RemoteCommand).
 * <br> 1)  Rename the location JComboBox to the "key" (remoteCommand) value.  This should start Lower
 *      case.
 *<br>  2)  The table name should match this key name and be all lower case.
 *<br>  3)  Change all RemoteCommand to ClassName of underlying data (case sensitive!)
 *<br>  4)  Change all remoteCommand key value (the JComboBox above)
 *<br>  5)  clearScreen should update swing components to new defaults
 *<br>  6)  chkForm should check all field for validity.  The errorTrack variable is
 *      used to track multiple errors.  In FUtil.chk* handle checks of the various
 *      variable types.  Date, double, and IP etc return the "canonical" form for 
 *      the variable.  These should be stored in a class variable so the latest one
 *      from "chkForm()" are available to the update writer.
 *<br>  7) newone() should be updated to create a new instance of the underlying data class.
 *<br>  8) oldone() get data from database and update all swing elements to correct values
 *<br>  9) addUpdateAction() add data base set*() operations updating all fields in the DB
 *<br> 10) RemoteCommandPanel() constructor - good place to change backgrounds using UC.Look() any
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
public final class RemoteCommandPanel extends javax.swing.JPanel {
  private static String db="anss";
  // NOTE : here define all variables general.  "ArrayList v" is used for main Comboboz
  static ArrayList<RemoteCommand> v;             // ArrayList containing objects of this RemoteCommand Type
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
   Util.prt("chkForm RemoteCommand");


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
    addUpdate.setText("Enter a RemoteCommand");
    
    //USER: Clear all fields for this form, this sets "defaults" for new screen
    // description.setText("");
    nodes.setText("");
    command.setText("");
    dialog.setText("");
  }
 
  
  private RemoteCommand newOne() {
      
    return new RemoteCommand(0, ((String) remoteCommand.getSelectedItem()).toUpperCase() //, more
       , "","",""
       );
  }
  public static void setDB(String dbin) {db = dbin;}
  private void oldOne(String p) throws SQLException {
    obj = new DBObject(DBConnectionThread.getThread(DBConnectionThread.getDBSchema()), db, "remotecommand","remotecommand",p);

    if(obj.isNew()) {
      Util.prt("object is new"+p);
    }
    else {
      //USER: Here set all of the form fields to data from the DBObject
      //      remoteCommand.setText(obj.getString("RemoteCommand"));
      // Example : description.setText(obj.getString("description"));
      nodes.setText(obj.getString("nodes"));
      command.setText(obj.getString("command"));
      dialog.setText(obj.getString("dialogtext"));
        
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
    jScrollPane1 = new javax.swing.JScrollPane();
    help = new javax.swing.JTextArea();
    jLabel1 = new javax.swing.JLabel();
    remoteCommand = getJComboBox();
    addUpdate = new javax.swing.JButton();
    ID = new javax.swing.JTextField();
    jLabel9 = new javax.swing.JLabel();
    error = new javax.swing.JTextField();
    nodesLab = new javax.swing.JLabel();
    nodes = new javax.swing.JTextField();
    commandLab = new javax.swing.JLabel();
    command = new javax.swing.JTextField();
    dialogLab = new javax.swing.JLabel();
    dialog = new javax.swing.JTextField();
    delete = new javax.swing.JButton();

    setLayout(new java.awt.GridBagLayout());

    jScrollPane1.setMinimumSize(new java.awt.Dimension(580, 200));

    help.setEditable(false);
    help.setColumns(60);
    help.setFont(new java.awt.Font("Courier New", 0, 12)); // NOI18N
    help.setRows(5);
    help.setText("Nodes should be a space separated list of roles (gacqn) or \"*\" for all nodes.\n\nThe command possible are \"Execute\", \"getfile\", \"QDPing\", \"Pings\", \"EdgeMom Status\".\nA command command is \"Execute bash doBash.bash ....\" to allow file expansion.\nCommands not needing this can be execute directly like \"Execute traceroute ....\".\nPut positional parameters on with $1, $2, etc or $@ for all positionals.\n\nThe dialog text format is [$n:Field=def] where $n is the positional parameter.\nAll occurances of $n in the command are substituted with the user input \nat that position.  Only the \"Field\" is put in user prompt in dialog.\nThe defaults, if present, are in the dialog box for user starting string.\n\n%N NodeNumber, %j version numbers for today, %J Day-of-Year,\n%Y Year, %M Month, %D Day of month, %L Last Dialog User input");
    help.setMinimumSize(new java.awt.Dimension(600, 80));
    jScrollPane1.setViewportView(help);

    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 11;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(jScrollPane1, gridBagConstraints);

    jLabel1.setText("Remote Cmd:");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 1;
    add(jLabel1, gridBagConstraints);

    remoteCommand.setEditable(true);
    remoteCommand.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        remoteCommandActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(remoteCommand, gridBagConstraints);

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

    nodesLab.setText("Roles (sp sep):");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 3;
    add(nodesLab, gridBagConstraints);

    nodes.setColumns(60);
    nodes.setMinimumSize(new java.awt.Dimension(500, 28));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 3;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(nodes, gridBagConstraints);

    commandLab.setText("Command :");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 4;
    add(commandLab, gridBagConstraints);

    command.setColumns(60);
    command.setMinimumSize(new java.awt.Dimension(500, 28));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 4;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(command, gridBagConstraints);

    dialogLab.setText("Dialog Text:");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 5;
    add(dialogLab, gridBagConstraints);

    dialog.setColumns(60);
    dialog.setMinimumSize(new java.awt.Dimension(500, 28));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 5;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(dialog, gridBagConstraints);

    delete.setText("Delete item");
    delete.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        deleteActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 16;
    add(delete, gridBagConstraints);
  }// </editor-fold>//GEN-END:initComponents

  private void addUpdateActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_addUpdateActionPerformed
    // Add your handling code here:
    
    // Do not change
    if(initiating) return;
    if(chkForm()) return;
    try {
      String p = remoteCommand.getSelectedItem().toString();
      if(!obj.isNew()) obj.refreshRecord();        // Refresh the result set so the sets will not fail on stale one
      obj.setString("remoteCommand",p);
      
      // USER : Set all of the fields in data base.  obj.setInt("thefield", ivalue);
      obj.setString("nodes", nodes.getText());
      obj.setString("command", command.getText());
      obj.setString("dialogtext", dialog.getText());
      
      // Do not change
      obj.updateRecord();
      v=null;       // force reload of combo box
      getJComboBox(remoteCommand);
      clearScreen();
      remoteCommand.setSelectedIndex(-1);
    }  catch  (SQLException  E)
    { Util.SQLErrorPrint(E,"remoteCommand: update failed partno="+remoteCommand.getSelectedItem());
      error.setText("Could Not Update File!");
      error.setBackground(UC.red);
      addUpdate.setEnabled(true);
    }      
  }//GEN-LAST:event_addUpdateActionPerformed

  private void remoteCommandActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_remoteCommandActionPerformed
    // Add your handling code here:
   find();
 
 
}//GEN-LAST:event_remoteCommandActionPerformed

  private void deleteActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_deleteActionPerformed
    if(remoteCommand.getSelectedIndex() < 0) return;
    try {
      obj.deleteRecord();
       remoteCommand.removeItem(remoteCommand.getSelectedItem());
       clearScreen();
   }
    catch(SQLException e) {
      Util.SQLErrorPrint(e,"Error deleting record "+remoteCommand.getSelectedItem()+" e="+e);
    }
  }//GEN-LAST:event_deleteActionPerformed
  
  
  // Variables declaration - do not modify//GEN-BEGIN:variables
  private javax.swing.JTextField ID;
  private javax.swing.JButton addUpdate;
  private javax.swing.JTextField command;
  private javax.swing.JLabel commandLab;
  private javax.swing.JButton delete;
  private javax.swing.JTextField dialog;
  private javax.swing.JLabel dialogLab;
  private javax.swing.JTextField error;
  private java.awt.GridBagLayout gridBagLayout1;
  private javax.swing.JTextArea help;
  private javax.swing.JLabel jLabel1;
  private javax.swing.JLabel jLabel9;
  private javax.swing.JScrollPane jScrollPane1;
  private javax.swing.JTextField nodes;
  private javax.swing.JLabel nodesLab;
  private javax.swing.JComboBox remoteCommand;
  // End of variables declaration//GEN-END:variables
  /** Creates new form RemoteCommandPanel
   * @param dbin Set the database with the reports definitition if null or empty use anss
   */
  public RemoteCommandPanel(String dbin) {
    initiating=true;
    if(dbin != null) 
      if(!dbin.equals("")) db = dbin;
    initComponents();
    getJComboBox(remoteCommand);                // set up the key JComboBox
    remoteCommand.setSelectedIndex(-1);    // Set selected type
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
    makeRemoteCommands();
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
    //Util.prt("RemoteCommandPanel.setJComboBoxToID id="+ID);
    for(int i=0; i<b.getItemCount(); i++) 
      if(((RemoteCommand) b.getItemAt(i)).getID() == ID) {
          b.setSelectedIndex(i);
          //Util.prt("RemoteCommandPanel.setJComboBoxToID id="+ID+" found at "+i);
      }
  }  
  
  /** Given a database ID find the index into a JComboBox consistent with this panel
   *@param ID The row ID from the database
   *@return The index in the JComboBox with this ID
   */
  public static int getJComboBoxIndex(int ID) {
    for(int i=0; i<v.size(); i++) if( ID == ((RemoteCommand) v.get(i)).getID()) return i;
    return -1;
  }
  /** Cause the main JComboBox to be reloaded*/
  public static void reload() {
    v = null;
    makeRemoteCommands();
  }
  
  /** Get the item corresponding to database ID
   *@param ID the database Row ID
   *@return The RemoteCommand row with this ID
   */
  public static RemoteCommand getItemWithID(int ID) {
    int i=getJComboBoxIndex(ID);
    if(i >= 0) return (RemoteCommand) v.get(i);
    else return null;
  }
  
  // This routine should only need tweeking if key field is not same as table name
  private static void makeRemoteCommands() {
    if (v != null) return;
    v=new ArrayList<RemoteCommand>(100);
    try {
      try (Statement stmt = DBConnectionThread.getConnection(DBConnectionThread.getDBSchema()).createStatement() // used for query
      ) {
        String s = "SELECT * FROM "+db+".remotecommand ORDER BY remoteCommand;";
        try (ResultSet rs = stmt.executeQuery(s)) {
          while (rs.next()) {
            RemoteCommand loc = new RemoteCommand(rs);
//        Util.prt("MakeRemoteCommand() i="+v.size()+" is "+loc.getRemoteCommand());
v.add(loc);
          }
        }
      }
    }
    catch (SQLException e) {
      Util.SQLErrorPrint(e,"makeRemoteCommands() on table SQL failed");
    }    
  }
  
  // No changes needed
  private void find() {
    if(remoteCommand == null) return;
    if(initiating) return;
    RemoteCommand l;
    if(remoteCommand.getSelectedIndex() == -1) {
      if(remoteCommand.getSelectedItem() == null) return;
      l = newOne();
    } 
    else {
      l = (RemoteCommand) remoteCommand.getSelectedItem();
    }
      
    if(l == null) return;
    String p = l.getRemoteCommand();
    if(p == null) return;
    if(p.equals("")) {
      addUpdate.setEnabled(false);
      addUpdate.setText("Enter a RemoteCommand!");
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
    { Util.SQLErrorPrint(E,"RemoteCommand: SQL error getting RemoteCommand="+p);
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
      Show.inFrame(new RemoteCommandPanel("anss"), UC.XSIZE, UC.YSIZE);
    }
    catch(InstantiationException e) {}
  }

}
