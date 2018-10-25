/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */

package gov.usgs.anss.metadatagui;
//package gov.usgs.edge.template;
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
import java.sql.Timestamp;
import java.util.ArrayList;
import javax.swing.JComboBox;
/**
 *
 * requirements : 
 * The key for the file must be the name of the table and of the
 * JComboBox variable this variable is called "loadurl" in the initial form
 * The table name and key name should match and start lower case (loadurl).
 * The Class here will be that same name but with upper case at beginning(Flags).
 * <br> 1)  Rename the location JComboBox to the "key" (loadurl) value.  This should start Lower
 *      case.
 *<br>  2)  The table name should match this key name and be all lower case.
 *<br>  3)  Change all Flags to ClassName of underlying data (case sensitive!)
 *<br>  4)  Change all loadurl key value (the JComboBox above)
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
 * Such JComboBoxes are usually initalized with 
 *<br>
 *<br> FUtil.getEnumJComboBox(DBConnectionThread.getConnection("metadata"), "tcpstation","rtsport1");
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
 * <br>    if(fieldEnum == null) fieldEnum = FUtil.getEnum(DBConnectionThread.getConnection("metadata"),"table","fieldName");
 * <br>    
 * <br>   // Get the int corresponding to an Enum String for storage in the data base and 
 * <br>   // the index into the JComboBox representing this Enum 
 * <br>   localVariable = FUtil.enumStringToInt(fieldEnum, enumString);
  * <br>
 * @author  D.C. Ketchum
 */
public class LoadURLPanel extends javax.swing.JPanel {
  
  // NOTE : here define all variables general.  "ArrayList v" is used for main Comboboz
  static ArrayList<LoadURL> v;             // ArrayList containing objects of this Flags Type
  DBObject obj;
  ErrorTrack err=new ErrorTrack();
  boolean initiating=false;
  Timestamp ts = new Timestamp(10000l);
  
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
   
   ts = FUtil.chkTimestamp(lastload, err);

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
    lastload.setText("");
    seedurl.setText("");
    xmlurl.setText("");
    disable.setSelected(false);
    comment.setText("");
  }
 
  
  private LoadURL newOne() {
    // USER: add all fields needed for a Flags
    return new LoadURL(0, (String) loadurl.getSelectedItem(),
        "", ts, "", "", 0, ""//, more
       );
  }
  
  private void oldOne(String p) throws SQLException {
    obj = new DBObject(DBConnectionThread.getThread("metadata"), 
        "metadata", "loadurl","loadurl",p);

    if(obj.isNew()) {
      Util.prt("object is new"+p);
    }
    else {
      // Here set all of the form fields to data from the DBObject
      //      loadurl.setText(obj.getString("Flags"));
      // Example : description.setText(obj.getString("description"));
      outputfile.setText(obj.getString("outputfile"));
      lastload.setText(obj.getTimestamp("lastload").toString().substring(0,19));
      seedurl.setText(obj.getString("seedurl"));
      xmlurl.setText(obj.getString("xmlurl"));
      disable.setSelected(obj.getInt("disable") != 0);
      comment.setText(obj.getString("comment"));
        
        
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
    lblLoadurl = new javax.swing.JLabel();
    loadurl = getJComboBox();
    addUpdate = new javax.swing.JButton();
    ID = new javax.swing.JTextField();
    jLabel9 = new javax.swing.JLabel();
    error = new javax.swing.JTextField();
    labOutputfile = new javax.swing.JLabel();
    outputfile = new javax.swing.JTextField();
    lblSeedurl = new javax.swing.JLabel();
    lblXmlurl = new javax.swing.JLabel();
    jspSeedurl = new javax.swing.JScrollPane();
    seedurl = new javax.swing.JTextArea();
    jspXmlurl = new javax.swing.JScrollPane();
    xmlurl = new javax.swing.JTextArea();
    lblLastload = new javax.swing.JLabel();
    lastload = new javax.swing.JTextField();
    disable = new javax.swing.JRadioButton();
    delete = new javax.swing.JButton();
    jScrollPane1 = new javax.swing.JScrollPane();
    comment = new javax.swing.JTextArea();
    labComment = new javax.swing.JLabel();

    setLayout(new java.awt.GridBagLayout());

    lblLoadurl.setText("Load URL :");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 1;
    add(lblLoadurl, gridBagConstraints);

    loadurl.setEditable(true);
    loadurl.setMaximumSize(new java.awt.Dimension(200, 25));
    loadurl.setMinimumSize(new java.awt.Dimension(200, 25));
    loadurl.setPreferredSize(new java.awt.Dimension(200, 25));
    loadurl.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        loadurlActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(loadurl, gridBagConstraints);

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

    labOutputfile.setText("Output File : ");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 3;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    add(labOutputfile, gridBagConstraints);

    outputfile.setColumns(80);
    outputfile.setMinimumSize(new java.awt.Dimension(408, 22));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 3;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(outputfile, gridBagConstraints);

    lblSeedurl.setText("Seed URL : ");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 6;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    add(lblSeedurl, gridBagConstraints);

    lblXmlurl.setText("XML URL : ");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 8;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    add(lblXmlurl, gridBagConstraints);

    jspSeedurl.setMinimumSize(new java.awt.Dimension(408, 100));
    jspSeedurl.setPreferredSize(new java.awt.Dimension(408, 100));

    seedurl.setColumns(20);
    seedurl.setRows(5);
    seedurl.setToolTipText("<html>\nftp:[PASV]//url.to.ftp/dir1/dir2/*.dataless - User PASV on sites that require passive FTP (which is most of them).\n<p>\nhttps://some.url.somewhere/dir1/dir2/*.dataless - use http or https as needed\n<p>\narclink:webdc.eu:NN[,L1-L2[,L3-l4]....  - this become \"[L1-L2]*\" and \"[L3-L4]*\" in the station portion of the request.\n<p>\n</html>\n");
    jspSeedurl.setViewportView(seedurl);

    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 6;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(jspSeedurl, gridBagConstraints);

    jspXmlurl.setMinimumSize(new java.awt.Dimension(408, 100));
    jspXmlurl.setPreferredSize(new java.awt.Dimension(408, 100));

    xmlurl.setColumns(20);
    xmlurl.setRows(5);
    xmlurl.setToolTipText("<html>\nStation XML is not currently supported.\n</html>");
    jspXmlurl.setViewportView(xmlurl);

    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 8;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(jspXmlurl, gridBagConstraints);

    lblLastload.setText("Last Loaded : ");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 4;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    add(lblLastload, gridBagConstraints);

    lastload.setMinimumSize(new java.awt.Dimension(200, 22));
    lastload.setPreferredSize(new java.awt.Dimension(200, 22));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 4;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(lastload, gridBagConstraints);

    disable.setText("Disable");
    disable.setMargin(new java.awt.Insets(0, 0, 0, 0));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 10;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(disable, gridBagConstraints);

    delete.setText("Delete Record");
    delete.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        deleteActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 18;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(delete, gridBagConstraints);

    jScrollPane1.setMinimumSize(new java.awt.Dimension(408, 100));
    jScrollPane1.setPreferredSize(new java.awt.Dimension(408, 100));

    comment.setColumns(20);
    comment.setRows(5);
    comment.setToolTipText("User comment - please include dates with the comments especially when disabling a LoadURL!");
    jScrollPane1.setViewportView(comment);

    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 9;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
    add(jScrollPane1, gridBagConstraints);

    labComment.setText("Comments:");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 9;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_END;
    add(labComment, gridBagConstraints);
  }// </editor-fold>//GEN-END:initComponents

  private void deleteActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_deleteActionPerformed
// TODO add your handling code here:
    if(loadurl.getSelectedIndex() == -1) return;
    try {
      Util.prta("Delete record="+obj.toString()+" "+loadurl.getSelectedItem().toString());
      obj.deleteRecord();
      loadurl.removeItem(loadurl.getSelectedItem());
      loadurl.setSelectedIndex(-1);
      clearScreen();
    }
    catch(SQLException e) {
      Util.SQLErrorPrint(e,"Error deleting record="+obj.toString()+" "+loadurl.getSelectedItem().toString());
    }
  }//GEN-LAST:event_deleteActionPerformed

  private void addUpdateActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_addUpdateActionPerformed
    // Add your handling code here:
    
    // Do not change
    if(initiating) return;
    if(chkForm()) return;
    try {
      String p = loadurl.getSelectedItem().toString();
      if(!obj.isNew()) obj.refreshRecord();        // Refresh the result set so the sets will not fail on stale one
      if(p.startsWith("_")) p=p.substring(1);   // leading _ are indicators its disabled, but should not be in DB
      if(p.startsWith("_")) p=p.substring(1);
      obj.setString("loadurl",p);
      
      // USER : Set all of the fields in data base.  obj.setInt("thefield", ivalue);
      obj.setTimestamp("lastload", ts);
      obj.setString("outputfile", outputfile.getText());
      obj.setString("seedurl", seedurl.getText());
      obj.setString("xmlurl", xmlurl.getText());
      obj.setInt("disable",(disable.isSelected()? 1 : 0));
      obj.setString("comment",comment.getText());
      // Do not change
      obj.updateRecord();
      v=null;       // force reload of combo box
      getJComboBox(loadurl);
      clearScreen();
      loadurl.setSelectedIndex(-1);
    }  catch  (SQLException  E)
    { Util.SQLErrorPrint(E,"loadurl: update failed partno="+loadurl.getSelectedItem());
      error.setText("Could Not Update File!");
      error.setBackground(UC.red);
      addUpdate.setEnabled(true);
    }      
  }//GEN-LAST:event_addUpdateActionPerformed

  private void loadurlActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_loadurlActionPerformed
    // Add your handling code here:
   find();
 
 
  }//GEN-LAST:event_loadurlActionPerformed
  
  
  // Variables declaration - do not modify//GEN-BEGIN:variables
  private javax.swing.JTextField ID;
  private javax.swing.JButton addUpdate;
  private javax.swing.JTextArea comment;
  private javax.swing.JButton delete;
  private javax.swing.JRadioButton disable;
  private javax.swing.JTextField error;
  private java.awt.GridBagLayout gridBagLayout1;
  private javax.swing.JLabel jLabel9;
  private javax.swing.JScrollPane jScrollPane1;
  private javax.swing.JScrollPane jspSeedurl;
  private javax.swing.JScrollPane jspXmlurl;
  private javax.swing.JLabel labComment;
  private javax.swing.JLabel labOutputfile;
  private javax.swing.JTextField lastload;
  private javax.swing.JLabel lblLastload;
  private javax.swing.JLabel lblLoadurl;
  private javax.swing.JLabel lblSeedurl;
  private javax.swing.JLabel lblXmlurl;
  private javax.swing.JComboBox loadurl;
  private javax.swing.JTextField outputfile;
  private javax.swing.JTextArea seedurl;
  private javax.swing.JTextArea xmlurl;
  // End of variables declaration//GEN-END:variables
  /** Creates new form LoadURLPanel */
  public LoadURLPanel() {
    initiating=true;
    initComponents();
    getJComboBox(loadurl);                // set up the key JComboBox
    loadurl.setSelectedIndex(-1);    // Set selected type
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
  /** Udate a JComboBox to represent the rows represented by this panel
   *@param b The JComboBox to Update
   */
  public static void getJComboBox(JComboBox b) {
    b.removeAllItems();
    makeLoadURL();
    for (int i=0; i< v.size(); i++) {
      b.addItem( v.get(i));
    }
    b.setMaximumRowCount(25);
  }
  
  /** Given a JComboBox from getJComboBox, set selected item to match database ID
   *@param b The JComboBox
   *@param ID the row ID from the database to select
   */
  public static void setJComboBoxToID(JComboBox b, int ID) {
    b.setSelectedIndex(-1);
    //Util.prt("FlagsPanel.setJComboBoxToID id="+ID);
    for(int i=0; i<b.getItemCount(); i++) 
      if(((LoadURL) b.getItemAt(i)).getID() == ID) {
          b.setSelectedIndex(i);
          //Util.prt("FlagsPanel.setJComboBoxToID id="+ID+" found at "+i);
      }
  }  
  
  /** Given a database ID find the index into a JComboBox consistent with this panel
   *@param ID The row ID from the database
   *@return The index in the JComboBox with this ID
   */
  public static int getJComboBoxIndex(int ID) {
    for(int i=0; i<v.size(); i++) if( ID == ((LoadURL) v.get(i)).getID()) return i;
    return -1;
  }
  /** Cause the main JComboBox to be reloaded*/
  public static void reload() {
    v = null;
    makeLoadURL();
  }
  
  /** Get the item corresponding to database ID
   *@param ID the database Row ID
   *@return The Flags row with this ID
   */
  public static LoadURL getItemWithID(int ID) {
    int i=getJComboBoxIndex(ID);
    if(i >= 0) return (LoadURL) v.get(i);
    else return null;
  }
  
  // This routine should only need tweeking if key field is not same as table name
  private static void makeLoadURL() {
    if (v != null) return;
    v=new ArrayList<LoadURL>(100);
    try {
      Statement stmt = DBConnectionThread.getConnection("metadata").createStatement();   // used for query
      String s = "SELECT * FROM metadata.loadurl ORDER BY loadurl;";
      ResultSet rs = stmt.executeQuery(s);
      while (rs.next()) {
        LoadURL loc = new LoadURL(rs);
//        Util.prt("MakeFlags() i="+v.size()+" is "+loc.getFlags());
        v.add(loc);
      }
    }
    catch (SQLException e) {
      Util.SQLErrorPrint(e,"makLoadURLs() on table SQL failed");
    }    
  }
  
  // No changes needed
  private void find() {
    if(loadurl == null) return;
    if(initiating) return;
    LoadURL l;
    if(loadurl.getSelectedIndex() == -1) {
      if(loadurl.getSelectedItem() == null) return;
      l = newOne();
    } 
    else {
      l = (LoadURL) loadurl.getSelectedItem();
    }
      
    if(l == null) return;
    String p = l.getLoadURL();
    if (p.length()>=29) p = p.substring(0,29);
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
      Show.inFrame(new LoadURLPanel(), UC.XSIZE, UC.YSIZE);
    }
    catch(InstantiationException e) {}
  }
  
}
