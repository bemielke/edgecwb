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
/**
 *
 * requirements : 
 * The key for the file must be the name of the table and of the
 * JComboBox variable this variable is called "edgefile" in the initial form
 * The table name and key name should match and start lower case (edgefile).
 * The Class here will be that same name but with upper case at beginning(EdgeFile).
 * <br> 1)  Rename the location JComboBox to the "key" (edgefile) value.  This should start Lower
 *      case.
 *<br>  2)  The table name should match this key name and be all lower case.
 *<br>  3)  Change all EdgeFile to ClassName of underlying data (case sensitive!)
 *<br>  4)  Change all edgefile key value (the JComboBox above)
 *<br>  5)  clearScreen should update swing components to new defaults
 *<br>  6)  chkForm should check all field for validity.  The errorTrack variable is
 *      used to track multiple errors.  In FUtil.chk* handle checks of the various
 *      variable types.  Date, double, and IP etc return the "canonical" form for 
 *      the variable.  These should be stored in a class variable so the latest one
 *      from "chkForm()" are available to the update writer.
 *<br>  7) newone() should be updated to create a new instance of the underlying data class.
 *<br>  8) oldone() get data from database and update all swing elements to correct values
 *<br>  9) addUpdateAction() add data base set*() operations updating all fields in the DB
 *<br> 10) EdgeFilePanel() constructor - good place to change backgrounds using UC.Look() any
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
public class EdgeFilePanel extends javax.swing.JPanel {
  
  // NOTE : here define all variables general.  "Vector v" is used for main Comboboz
  static ArrayList<EdgeFile> v;             // Vector containing objects of this EdgeFile Type
  DBObject obj;
  ErrorTrack err=new ErrorTrack();
  boolean initiating=false;
  
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
   Util.prt("chkForm EdgeFile");


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
    addUpdate.setText("Enter a EdgeFile");
    changeEdgefile.setText("");
    
    //USER: Clear all fields for this form, this sets "defaults" for new screen
    // description.setText("");
    content.setText("");
  }
 
  
  private EdgeFile newOne() {
      
    return new EdgeFile(0, ((String) edgefile.getSelectedItem()), content.getText() //, more
       );
  }
  
  private void oldOne(String p) throws SQLException {
    obj = new DBObject(DBConnectionThread.getThread(DBConnectionThread.getDBSchema()), DBConnectionThread.getDBSchema(), "edgefile","edgefile",p);

    if(obj.isNew()) {
      Util.prt("object is new"+p);
    }
    else {
      //USER: Here set all of the form fields to data from the DBObject
      //      edgefile.setText(obj.getString("EdgeFile"));
      // Example : description.setText(obj.getString("description"));
      content.setText(obj.getString("content"));
        
        
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
    edgefile = getJComboBox();
    addUpdate = new javax.swing.JButton();
    ID = new javax.swing.JTextField();
    jLabel9 = new javax.swing.JLabel();
    error = new javax.swing.JTextField();
    jLabel2 = new javax.swing.JLabel();
    jScrollPane1 = new javax.swing.JScrollPane();
    content = new javax.swing.JTextArea();
    labChange = new javax.swing.JLabel();
    changeEdgefile = new javax.swing.JTextField();
    delete = new javax.swing.JButton();
    reload = new javax.swing.JButton();
    fileRE = new javax.swing.JTextField();
    labRE = new javax.swing.JLabel();
    setDefaultQuerymom = new javax.swing.JButton();

    setLayout(new java.awt.GridBagLayout());

    jLabel1.setText("Filename:");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 2;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    add(jLabel1, gridBagConstraints);

    edgefile.setEditable(true);
    edgefile.setToolTipText("<html>\nSelect an existing edgefile by name, or enter the path/name of an external file to be created.\n<br>\nIf a external file is only used by one thread in one instance, it is better to create it on the EdgeMomSetup panel using the \"Config filename\" and \"Config Content\" fields.\n</html>\n");
    edgefile.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        edgefileActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 2;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(edgefile, gridBagConstraints);

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

    jLabel2.setText("Content:");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 3;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    add(jLabel2, gridBagConstraints);

    jScrollPane1.setMinimumSize(new java.awt.Dimension(600, 400));
    jScrollPane1.setPreferredSize(new java.awt.Dimension(564, 400));

    content.setColumns(80);
    content.setFont(new java.awt.Font("Monospaced", 0, 12)); // NOI18N
    content.setRows(200);
    content.setToolTipText("<html>\nEnter the contents of this external file (normally a configuration file)\n<br>\n<PRE>\nSymbols that can be present and substituted :\n$INSTANCE  replaced with the instance setting.  Useful in ring file names.\n$ROLE replaced with the role name or primary role name\n$SERVER replaced with the physical node/cpu system name.\n</PRE>\n</html>\n");
    jScrollPane1.setViewportView(content);

    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 3;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(jScrollPane1, gridBagConstraints);

    labChange.setText("ChangeName:");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 6;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    add(labChange, gridBagConstraints);

    changeEdgefile.setColumns(40);
    changeEdgefile.setToolTipText("This changes the path/name of this external file.  The old path and file are removed from the list.");
    changeEdgefile.setMinimumSize(new java.awt.Dimension(400, 28));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 6;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(changeEdgefile, gridBagConstraints);

    delete.setText("Delete File");
    delete.setToolTipText("Click and this external file and its contents are deleted.");
    delete.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        deleteActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 7;
    add(delete, gridBagConstraints);

    reload.setText("Reload");
    reload.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        reloadActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 8;
    add(reload, gridBagConstraints);

    fileRE.setColumns(10);
    fileRE.setToolTipText("<html>\nEnter some text (a regular expression) and limit the files displayed to match this string.\n<br>Particularly useful to look at things only on one node or of one type.\n</html>");
    fileRE.addFocusListener(new java.awt.event.FocusAdapter() {
      public void focusLost(java.awt.event.FocusEvent evt) {
        fileREFocusLost(evt);
      }
    });
    fileRE.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        fileREActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(fileRE, gridBagConstraints);

    labRE.setText("Matching (optional):");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    add(labRE, gridBagConstraints);

    setDefaultQuerymom.setText("set Default QueryMom.setup");
    setDefaultQuerymom.setToolTipText("<html>\nA common configuration file needed is a \"querymom.setup\" for a given role or cpu.  \n<br>Clicking this button gets the standard starting content for a querymom.setup ready for editing to the specific needs of a given QueryMom instance.\n</html>");
    setDefaultQuerymom.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        setDefaultQuerymomActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 4;
    add(setDefaultQuerymom, gridBagConstraints);
  }// </editor-fold>//GEN-END:initComponents

  private void addUpdateActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_addUpdateActionPerformed
    // Add your handling code here:
    
    // Do not change
    if(initiating) return;
    if(chkForm()) return;
    try {
      String p = edgefile.getSelectedItem().toString();
      //p = p.toUpperCase();                  // USER : drop this to allow Mixed case
      if(!obj.isNew()) obj.refreshRecord();        // Refresh the result set so the sets will not fail on stale one
      if(changeEdgefile.getText().equals("")) obj.setString("edgefile",p);
      else obj.setString("edgefile",changeEdgefile.getText());
      
      // USER : Set all of the fields in data base.  obj.setInt("thefield", ivalue);
      obj.setString("content", Util.chkTrailingNewLine(content.getText()));
      
      // Do not change
      obj.updateRecord();

       // If we are updating configuration files in reftek area, force gacq4 update
       if(p.contains("~reftek")) {
          try {
            DBConnectionThread.getThread("edge").executeUpdate("UPDATE edge.role set hasdata=1 where role ='gacq4'");
          }
          catch(SQLException e) {
            Util.prt("Could not set hasdata for role=gacq4 e="+e);
          }
       }
      if(p.contains("import_gacq")) {
        String [] parts = p.split("[_.]");
        for (String part : parts) {
          if (part.contains("gacq")) {
            try {
              DBConnectionThread.getThread("edge").executeUpdate("UPDATE edge.role set hasdata=1 where role='" + part + "'");
              break;
            } catch (SQLException e) {
              Util.prt("**** Could not set has data for role=" + part);
            }
          }
        }

      }
      // If this sidefile is mentioned on any line in andy edge config, we need to set hasdata for that role to cause change
      if(obj.getInt("id") != 0) {
        try {
          int[] roleids;
          int count;
          try (ResultSet rs = DBConnectionThread.getThread("edge").executeQuery("SELECT roleid FROM edge.edgemomsetup WHERE edgefileid="+obj.getInt("ID"))) {
            roleids = new int[20];
            count = 0;
            while(rs.next()) {
              roleids[count++] =rs.getInt("roleid");
            }
          }
          for(int i=0; i<count; i++) {
            Util.prt("Turn on hasdata for roleid="+roleids[i]);
            DBConnectionThread.getThread("edge").executeUpdate("UPDATE edge.role set hasdata=1 where ID="+roleids[i]);
          }

        }
        catch(SQLException e) {
          Util.prt("SQLerror updating role for changed file d="+e);
        }
      }
// If this is a edge.prop or crontab update, then mark the role for update
      if(p.contains("crontab") ) {
        String account = p.substring(p.lastIndexOf(".")+1);
        String tmp = p.substring(0,p.lastIndexOf("."));
        String role = tmp.substring(tmp.lastIndexOf(".")+1);
        Util.prt("edge.prop update role="+role+" account="+account);
        try {
          DBConnectionThread.getThread("edge").executeUpdate("UPDATE edge.role set hasdata=1 where role ='"+role+"'");
        }
        catch(SQLException e) {
          Util.prt("Could not set hasdata for role="+role+" e="+e);
        }
      }
      v=null;       // force reload of combo box
      getJComboBox(edgefile);
      clearScreen();
      rebuildFileJCombo();
      edgefile.setSelectedIndex(-1);
    }
     catch  (SQLException  E)
    { Util.SQLErrorPrint(E,"edgefile: update failed partno="+edgefile.getSelectedItem());
      error.setText("Could Not Update File!");
      error.setBackground(UC.red);
      addUpdate.setEnabled(true);
    }      
  }//GEN-LAST:event_addUpdateActionPerformed

  private void edgefileActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_edgefileActionPerformed
    // Add your handling code here:
   find();
  }//GEN-LAST:event_edgefileActionPerformed

private void deleteActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_deleteActionPerformed
// TODO add your handling code here:
  try {
    if(edgefile.getSelectedIndex() != -1 ) {
      obj.deleteRecord();
      edgefile.removeItemAt(edgefile.getSelectedIndex());
      clearScreen();
    }
  }
  catch(SQLException e) {
    Util.prt("Failed to delete e="+e);
  }
}//GEN-LAST:event_deleteActionPerformed

private void reloadActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_reloadActionPerformed
  reload();
  getJComboBox(edgefile);  
  rebuildFileJCombo();
  
}//GEN-LAST:event_reloadActionPerformed

private void fileREActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_fileREActionPerformed
  rebuildFileJCombo();
}//GEN-LAST:event_fileREActionPerformed

private void fileREFocusLost(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_fileREFocusLost
  rebuildFileJCombo();
}//GEN-LAST:event_fileREFocusLost

  private void setDefaultQuerymomActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_setDefaultQuerymomActionPerformed
    content.setText(defaultQueryMomSetup);
  }//GEN-LAST:event_setDefaultQuerymomActionPerformed
  
  private void rebuildFileJCombo() {
    reload();
    getJComboBox(edgefile);
    if(fileRE.getText().equals("")) return;
    for(int i=edgefile.getItemCount()-1; i>=0; i--) {
      if(!edgefile.getItemAt(i).toString().contains(fileRE.getText())) edgefile.removeItemAt(i);
    }
    if(edgefile.getItemCount() > 0) edgefile.setSelectedIndex(0);
  }
  // Variables declaration - do not modify//GEN-BEGIN:variables
  private javax.swing.JTextField ID;
  private javax.swing.JButton addUpdate;
  private javax.swing.JTextField changeEdgefile;
  private javax.swing.JTextArea content;
  private javax.swing.JButton delete;
  private javax.swing.JComboBox edgefile;
  private javax.swing.JTextField error;
  private javax.swing.JTextField fileRE;
  private java.awt.GridBagLayout gridBagLayout1;
  private javax.swing.JLabel jLabel1;
  private javax.swing.JLabel jLabel2;
  private javax.swing.JLabel jLabel9;
  private javax.swing.JScrollPane jScrollPane1;
  private javax.swing.JLabel labChange;
  private javax.swing.JLabel labRE;
  private javax.swing.JButton reload;
  private javax.swing.JButton setDefaultQuerymom;
  // End of variables declaration//GEN-END:variables
  /** Creates new form EdgeFilePanel */
  public EdgeFilePanel() {
    initiating=true;
    initComponents();
    getJComboBox(edgefile);                // set up the key JComboBox
    edgefile.setSelectedIndex(-1);    // Set selected type
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
    makeEdgeFiles();
    for (EdgeFile v1 : v) {
      b.addItem(v1);
    }
    b.setMaximumRowCount(30);
  }
  public static void setJComboConfigOnly(JComboBox b) {
    for(int i=b.getItemCount() -1; i>=0; i--) {
      if(!b.getItemAt(i).toString().contains("/")) b.removeItemAt(i);
    }
  }
  /** Given a JComboBox from getJComboBox, set selected item to match database ID
   *@param b The JComboBox
   *@param ID the row ID from the database to select
   */
  public static void setJComboBoxToID(JComboBox b, int ID) {
    b.setSelectedIndex(-1);
    //Util.prt("EdgeFilePanel.setJComboBoxToID id="+ID);
    for(int i=0; i<b.getItemCount(); i++) 
      if(((EdgeFile) b.getItemAt(i)).getID() == ID) {
          b.setSelectedIndex(i);
          //Util.prt("EdgeFilePanel.setJComboBoxToID id="+ID+" found at "+i);
      }
  }  
  
  /** Given a database ID find the index into a JComboBox consistent with this panel
   *@param ID The row ID from the database
   *@return The index in the JComboBox with this ID
   */
  public static int getJComboBoxIndex(int ID) {
    if(v == null) makeEdgeFiles();
    for(int i=0; i<v.size(); i++) if( ID == ((EdgeFile) v.get(i)).getID()) return i;
    return -1;
  }
  /** Cause the main JComboBox to be reloaded*/
  public static void reload() {
    v = null;
    makeEdgeFiles();
  }
    /* return a vector with all of the EdgeFile
   * @return The vector with the edgefile
   */
  public static ArrayList getEdgeFileVector() {
    if(v == null) makeEdgeFiles();
    return v;
  }
  /** Get the item corresponding to database ID
   *@param ID the database Row ID
   *@return The EdgeFile row with this ID
   */
  public static EdgeFile getEdgeFileWithID(int ID) {
    if(v == null) makeEdgeFiles();
    int i=getJComboBoxIndex(ID);
    if(i >= 0) return (EdgeFile) v.get(i);
    else return null;
  }
  
  // This routine should only need tweeking if key field is not same as table name
  private static void makeEdgeFiles() {
    if (v != null) return;
    v=new ArrayList<>(100);
    try {
      try (Statement stmt = DBConnectionThread.getConnection(DBConnectionThread.getDBSchema()).createStatement() // used for query
      ) {
        String s = "SELECT * FROM edge.edgefile ORDER BY edgefile;";
        try (ResultSet rs = stmt.executeQuery(s)) {
          while (rs.next()) {
            EdgeFile loc = new EdgeFile(rs);
//        Util.prt("MakeEdgeFile() i="+v.size()+" is "+loc.getEdgeFile());
            v.add(loc);
          }
        }
      }
    }
    catch (SQLException e) {
      Util.SQLErrorPrint(e,"makeEdgeFiles() on table SQL failed");
    }    
  }
  
  // No changes needed
  private void find() {
    if(edgefile == null) return;
    if(initiating) return;
    EdgeFile l;
    if(edgefile.getSelectedIndex() == -1) {
      if(edgefile.getSelectedItem() == null) return;
      l = newOne();
    } 
    else {
      l = (EdgeFile) edgefile.getSelectedItem();
    }
      
    if(l == null) return;
    String p = l.getEdgeFile();
    if(p == null) return;
    if(p.equals("")) {
      addUpdate.setEnabled(false);
      addUpdate.setText("Enter a EdgeFile!");
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
    { Util.SQLErrorPrint(E,"EdgeFile: SQL error getting EdgeFile="+p);
    }
    
  }
  public static final String defaultQueryMomSetup = 
          "# EdgeMom thread controls staring and logging from all of the other thread (mandatory)\n" +
          " Mom:EdgeMom:-empty\n" +
          "# The EdgeChannelServer is used for configuration parameters from channel table\n" +
          " Echn:EdgeChannelServer:-empty >>echnqm\n" +
          "# FilterPickerManager makes the configuration file for FilterPickers from filterpicker table of args and channel table\n" +
          "#FP:gov.usgs.anss.picker.FilterPickerManager:-dbpick localhost/3306;status;mysql;status -qsize 100 -host 192.168.18.80 -port 16099 -empty >>fpm\n" +
          "# EdgeQueryServer : [-p port][-rw][-allowrestricted][-mdsip ip][-mdsport port][-max nn][-dbg][-dbgall]\n" +
          "#                       2061                        137.227.224.97  2052        200  \n" +
          "QS:gov.usgs.cwbquery.EdgeQueryServer:-p 2061 -mdsip 137.227.224.97 -mdsport 2052 -allowrestricted  >>queryserver\n" +
          "# Start a CWBWaveServer:\n" +
          "# -noudpchan            Do not use a SocketServerReader to get status from a UdpChannel for the latest data\n" +
          "# -holdings             NOT PREFERRED.  Get MENULIST information by querying the holdings databases instead of channels \n" +
          "#                        (StatusDaBServer must be defined or -holdingsmysql must be used), \n" +
          "#                       but do recent end from channels or if StatusServer is set, from UdpChannel information.\n" +
          "#                       If this is not selected, early times are taken from channel creation time in channel table, and\n" +
          "#                       ending times from the UdpChannel information.  Not using holdings is recommended.\n" +
          "# -holdingsmysql dburl  Use this server for holdings instead of the StatusDBServer property or -dbconnedge switch\n" +
          "# -dbedge        dburl  Use this server to look at channel table in edge for last data or if no holdings channel creation (def=property DBServer)\n" +
          "# -dbstatus      dburl  Use this server to look at holdings in the status database (def=propery StatusDBServer)\n" +
          "# -dbmeta        dburl  Use this server to look at metadata (def=property MetadataDBServer)\n" +
          "# -maxthread     nn     Have a maximum of nn threads available to run requests simultaneously (def=1000)\n" +
          "# -minthread     nn     Have a minimum of nn threads available to run requests simultaneously at startup (def=20)\n" +
          "# -p             pppp   Run this service on port pppp (def=2060) \n" +
          "# -instance n       If this is not the only instance on a node, set this to a positive instance number (1-10), or a port to use for monitoring\n" +
          "# -cwbhost       ip.adr Run requests against this CWB (def=cwbpub.cr.usgs.gov) instead of this instance\n" +
          "# -allowrestricted      Allow requests for restricted channels\n" +
          "# -nofilter             Do not filter down the list of channels to just reasonable seismic channels [BSHLMECD][HND][ZNE12F]\n" +
          "# -daysback      nnn    Allow the MENU list to go nnn days into the past when looking for a channel that stop coming in.\n" +
          "# -mdsip         ip.adr Use this IP address for MetaDataServer requests instead for 137.227.230.1\n" +
          "# -mdsport       port   User this port for the MDS instead for 2052\n" +
          "# -mdsto         millis Milliseconds to timeout connections to the MDS (def is 5000)\n" +
          "# -quiet                Really cut down the amount of output.\n" +
          "# -cdelim       char    Use this character instead of space to delimit seednames names for METADATA: CHANNELS command ($ is most common)\n" +
          "# -dbg                  Run with more verbose output\n" +
          "# -nodb                 Run in \"no database\" mode - use if no databases are to be used\n" +
          "# -dbgmsp               Run with verbose output on MiniSeedPool\n" +
          "# -subnet     path/to/config This is a top level configuration directory for subnetogram (VHP).  It would normally contain on directory per observator with .config files for each subnet\n" +
          "##CWBWaveServer 1600 \"\" -instance 0 -mdsip cwbpub.cr.usgs.gov -p 2060 -cwbhost cwbpub.cr.usgs.gov -daysback 10000 -maxthreads 50  >>LOG/CWBWaveServer.log 2>&1\n" +
          "#CWBWS:gov.usgs.anss.waveserver.CWBWaveServer:-p 2060 -mdsip cwbpub.cr.usgs.gov -mdsport 2052 -daysback 10000 -maxthreads 50 >> cwbws\n" +
          "# Set up the receiver of data from one or more EdgeMom based outputer (RingServerSeedLink)\n" +
          "# -iplist filename      If present, the list of IP addresses (one per line) is used to validate incoming connections\n" +
          "#                       If not present, accept all connections\n" +
          "# [-p port][-bind ip][-iplist filename][-dbg]\n" +
          "#  16099     NONE        NONE     \n" +
          "##DLQS:gov.usgs.cwbquery.DataLinkToQueryServer:-empty >>dlqs\n" +
          "#DLQS:gov.usgs.cwbquery.DataLinkToQueryServer:-empty >>dlqs\n" +
          "# -h  host              Normally do not specify so host is null and internal access to files is used\n" +
          "# -d  secs              Depth of in-the-clear data in seconds\n" +
          "# -pre pct              % of memory to leave in memory on before the current time (def=75)\n" +
          "# -bands band-list      two character band codes separated by dashes to include in the memory cache (def=BH-LH-SH-EH-HH-)\n" +
          "# -load  Nthread        Set the number of threads to use to populate memory cache from disk (def=2), if 0, no population is done.\n" +
          "##QSC:QuerySpanCollection:[-h cwbip][-p cwbport][-d secs][-pre pct][-bands]s\n" +
          "##                     Internal     2061     3600       75      BH-LH-SH-EH-HH-\n" +
          "#QSC:gov.usgs.cwbquery.QuerySpanCollection:-d 3600 -bands BH-LH-SH-EH-HH-  >>qsc \n" +
          "## Memory structure for TrinetServer emulator\n" +
          "# -h  host              Normally do not specify so host is null and internal access to files is used\n" +
          "# -d  secs              Depth of in-the-clear data in seconds\n" +
          "# -pre pct              % of memory to leave in memory on before the current time (def=75)\n" +
          "# -bands band-list      two character band codes separated by dashes to include in the memory cache (def=BH-LH-SH-EH-HH-)\n" +
          "# -load  Nthread        Set the number of threads to use to populate memory cache from disk (def=2), if 0, no population is done.\n" +
          "# -dbgchan chanRD       A NSCL regular expression which will generate debug output\n" +
          "##MSC:gov.usgs.cwbquery.MiniSeedCollection:[-dbg][-d nsec][-load n][-dbgchan chanRE][-bands BN-BN-] >>msc\n" +
          "##                                                3600           5                      BH-LH-SH-EH-HH-\n" +
          "#MSC:gov.usgs.cwbquery.MiniSeedCollection:-d 3600 -load 5  -bands BH-EL-LH-HH-EH-HN-BN- >>msc\n" +
          "#\n" +
          "# Setup the trinet part of the server\n" +
          "# -p      Port         The server port (def=2063)\n" +
          "# -allowrestricted     If present, restricted channels can be served by this server\n" +
          "# -maxthreads  NN      Maximum number of threads to permit simultaneously (def 1000)\n" +
          "# -minthreads  NN      The number of TrinetHandler threads to start initially (def 20)\n" +
          "# -quiet               If present, reduce the verbosity of log output\n" +
          "# -dbg                 Increase logging for debugging purposes\n" +
          "# -dbgmsp              If present, set debugging output for the MiniSeedPool\n" +
          "#TWS:gov.usgs.anss.waveserver.TrinetServer:-p 9101 -maxthread 50 >>tws";
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
      Show.inFrame(new EdgeFilePanel(), UC.XSIZE, UC.YSIZE);
    }
    catch(InstantiationException e) {}
  }
}
