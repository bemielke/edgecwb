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
import gov.usgs.anss.db.JDBConnection;
import gov.usgs.anss.util.ErrorTrack;
import gov.usgs.anss.util.ErrorTrack;
import gov.usgs.anss.util.Report;
import gov.usgs.anss.util.Report;
import gov.usgs.anss.util.Show;
import gov.usgs.anss.util.Show;
import gov.usgs.anss.util.UC;
import gov.usgs.anss.util.UC;
import gov.usgs.anss.util.Util;
import gov.usgs.anss.util.Util;
import java.awt.Color;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import javax.swing.JComboBox;



/** Implements the simple screen for getting report titles and the SQL which generates them
 
 requirements : 
 * The key for the file must be the name of the table and of the
 * JComboBox variable this variable is called "location" in the initial form
 * The table name and key name should match and start lower case (nname).
 * The Class here will be that same name but with upper case at beginning(NName).
 * <br> 1)  Rename the location JComboBox to the "key" (nname) value.  This should start Lower
 *      case.
 *<br>  2)  The table name should match this key name and be all lower case.
 *<br>  3)  Change all NName to ClassName of underlying data (case sensitive!)
 *<br>  4)  Change all nname key value (the JComboBox above)
 *<br>  5)  clearScreen should update swing components to new defaults
 *<br>  6)  chkForm should check all field for validity.  The errorTrack variable is
 *      used to track multiple errors.  In FUtil.chk* handle checks of the various
 *      variable types.  Date, double, and IP etc return the "canonical" form for 
 *      the variable.  These should be stored in a class variable so the latest one
 *      from "chkForm()" are available to the update writer.
 *<br>  7) newone() should be updated to create a new instance of the underlying data class.
 *<br>  8) oldone() get data from database and update all swing elements to correct values
 *<br>  9) addUpdateAction() add data base set*() operations updating all fields in the DB
 *<br> 10) NNamePanel() constructor - good place to change backgrounds using UC.Look() any
 *    other weird startup stuff.
 *
 *<br><br>  local variable error - Must be a JTextField for posting error communications
 * <br><br> local variable updateAdd - Must be the JButton for user clicks to try to post the data
 * <br> <br>ID - JTextField which must be non-editable for posting Database IDs
 * <br>
 *ENUM notes :  the ENUM are read from the database as type String.  So the constructor
 * should us the strings to set the indexes.  However, the writes to the database are
 * done as ints representing the value of the Enum.  The JComboBox with the Enum has 
 * the strings as Items in the list with indexes equal to the database value of the int.
 * Such JComboBoxes are usually initialized with 
 *<br>  FUtil.getEnumJComboBox(UC.getConnection(), "tcpstation","rtsport1");
 * <br>Put this in "CodeGeneration"  "CustomCreateCode" of the form editor for each Enum.
 *<br>
 * In  oldone() get Enum fields with something like :
 * <br>  fieldJcomboBox.setSelectedItem(obj.getString("fieldName"));
 *<br>  (This sets the JComboBox to the Item matching the string)
 *<br>
 * data class should have code like :
 *<br>  import java.util.ArrayList;          /// Import for Enum manipulation
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
 * ReportPanel.java
 *<br>
 *<br> requirements :
 * <br> report assumptions :
 *<br>  This implements the simple user interface for gather report titles and
 *the SQL code association with it.  
 *
 * Created on July 16, 2002, 2:51 PM
 *
 * @author  D.C. Ketchum
 */
public class ReportPanel extends javax.swing.JPanel {
  
  // NOTE : here define all variables general.  "Vector v" is used for main Comboboz
  static ArrayList<Report> v;             // Vector containing objects of this Report Type
  DBObject obj;
  static String threadName;
  ErrorTrack err=new ErrorTrack();
  
  // Here are the local variables.  These are the variables set by chkForm to insure
  // that the text fields parse correctly to numeric and date types.  Text data in the
  // DB will not necessary need a local variable.
  
  
  
  // This routine must validate all fields on the form.  The err (ErrorTrack) variable
  // is used by the FUtil to verify fields.  Use FUTil or custom code here for verifications
  private boolean chkForm() {
    // Do not change
    err.reset();
    UC.Look(error);
    
    // Your error checking code goes here setting the local variables to valid settings
    // like : delev = FUtil.chkDouble(elevation,err,-200.,4000.);
    //        date = FUtil.chkDate(dateTextField, err);
    //         FUtil.chkJComboBox(comboBoxVar,"Text", err);     // Combo box must have a selection
    //                                                       // Text will appear "No Text selected"
    //
    // or
    //  if(textboxvar.getText().length() <=1) {
    //     err.set(true);
    //     err.appendText("Blank variable not allowed!");
    // }
    //   Util.prt("chkFOrm Loction");
    
    
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
    addUpdate.setText("Enter a Report");
    
    // Clear all fields for this form, set background colors with UC.Look(Component)
    sql.setText("");
    table.setText("");
  }
  
  
  private Report newOne() {
    return new Report(0, ((String) report.getSelectedItem()),""
    );
  }
  
  private void oldOne(String p) throws SQLException {
    obj = new DBObject(DBConnectionThread.getThread(DBConnectionThread.getDBSchema()), 
            DBConnectionThread.getDBSchema(), (threadName.equals("IADSR")?"irsanreport":"report"),"report",p);
    
    // Here set all of the form fields to data from the DBObject
    //      report.setText(obj.getString("report"));
    sql.setText(obj.getString("sql"));
    table.setText(obj.getString("catalog"));
    
  }
  
  
  /** This method is called from within the constructor to
   * initialize the form.
   * WARNING: Do NOT modify this code. The content of this method is
   * always regenerated by the Form Editor.
   */
  // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
  private void initComponents() {

    gridBagLayout1 = new java.awt.GridBagLayout();
    jComboBox1 = new javax.swing.JComboBox();
    error = new javax.swing.JTextField();
    jSeparator2 = new javax.swing.JSeparator();
    templateLabel = new javax.swing.JLabel();
    report = getJComboBox();
    labTable = new javax.swing.JLabel();
    table = new javax.swing.JTextField();
    addUpdate = new javax.swing.JButton();
    jSeparator1 = new javax.swing.JSeparator();
    jLabel9 = new javax.swing.JLabel();
    ID = new javax.swing.JTextField();
    helpArea = new javax.swing.JTextArea();
    jScrollPane1 = new javax.swing.JScrollPane();
    sql = new javax.swing.JTextArea();
    newName = new javax.swing.JTextField();
    deleteButton = new javax.swing.JButton();

    error.setColumns(40);
    error.setEditable(false);
    add(error);

    jSeparator2.setPreferredSize(new java.awt.Dimension(850, 1));
    add(jSeparator2);

    templateLabel.setText("Report :");
    add(templateLabel);

    report.setEditable(true);
    report.setPreferredSize(new java.awt.Dimension(300, 28));
    report.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        reportActionPerformed(evt);
      }
    });
    add(report);

    labTable.setText("Database:");
    add(labTable);

    table.setColumns(10);
    add(table);

    addUpdate.setText("Add/Update");
    addUpdate.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        addUpdateActionPerformed(evt);
      }
    });
    add(addUpdate);

    jSeparator1.setPreferredSize(new java.awt.Dimension(850, 1));
    add(jSeparator1);

    jLabel9.setText("ID :");
    add(jLabel9);

    ID.setColumns(8);
    ID.setEditable(false);
    add(ID);

    helpArea.setColumns(80);
    helpArea.setEditable(false);
    helpArea.setFont(new java.awt.Font("Monospaced", 0, 13)); // NOI18N
    helpArea.setText("Enter either a SQL statement without the ending semi colon  in WHERE\n[field > $name{tag=def} AND/OR]  !tag will be display before textbox to gather data  \n!def will be the default for the tag field. If field is needed more than once use %name\nin the other locations\n                                       OR \nObjectName|methodName(Class tag1, Class tag2,....)  Each \"tag\" will be \nused in a dialog box to prompt for a \"Enter <tag>\". A static method in \nObjectName of methodName must exist with correct signature. Currently\nonly Signatures with \"String\" are supported.");
    helpArea.setBorder(javax.swing.BorderFactory.createEtchedBorder());
    add(helpArea);

    jScrollPane1.setPreferredSize(new java.awt.Dimension(650, 200));

    sql.setColumns(80);
    sql.setFont(new java.awt.Font("Monospaced", 0, 12)); // NOI18N
    sql.setRows(20);
    jScrollPane1.setViewportView(sql);

    add(jScrollPane1);

    newName.setColumns(40);
    newName.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        newNameActionPerformed(evt);
      }
    });
    add(newName);

    deleteButton.setText("DeleteReport");
    deleteButton.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        deleteButtonActionPerformed(evt);
      }
    });
    add(deleteButton);
  }// </editor-fold>//GEN-END:initComponents

  private void deleteButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_deleteButtonActionPerformed
    try {
      obj.deleteRecord();
      report.removeItem(report.getSelectedItem());
      ReportPanel.reload();
    }
    catch(SQLException e) {
      Util.SQLErrorPrint(e,"Deleting report record");
    }
    clearScreen();
  }//GEN-LAST:event_deleteButtonActionPerformed

  private void newNameActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_newNameActionPerformed
  }//GEN-LAST:event_newNameActionPerformed
  
  private void addUpdateActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_addUpdateActionPerformed
    // Add your handling code here:
    
    // Do not change
    if(chkForm()) return;
    try {
      String p = report.getSelectedItem().toString();
      //p = p.toUpperCase();
      
      // Set all of the fields
      if(newName.getText().length() > 2) p = newName.getText();
      if(!obj.isNew()) obj.refreshRecord();        // Refresh the result set so the sets will not fail on stale one
      obj.setString("report",p);
      Util.prt("report="+p);
      
      // Set all fields using obj.set?????("FieldName", value);
      obj.setString("sql",sql.getText());
      obj.setString("catalog",table.getText());
      
      // Do not change
      obj.updateRecord();
      v=null;
      int ind = report.getSelectedIndex();
      getJComboBox(report);
      //clearScreen();
      report.setSelectedIndex(ind);
      //report.setSelectedIndex(-1);
    }  catch  (SQLException  E)
    { Util.SQLErrorPrint(E,"report: update failed partno="+report.getSelectedItem());
      error.setText("Could Not Update File!");
      error.setBackground(UC.red);
      addUpdate.setEnabled(true);
    }
  }//GEN-LAST:event_addUpdateActionPerformed
  
  private void reportActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_reportActionPerformed
    // Add your handling code here:
    find();
    
    
  }//GEN-LAST:event_reportActionPerformed
  
  
  // Variables declaration - do not modify//GEN-BEGIN:variables
  private javax.swing.JTextField ID;
  private javax.swing.JButton addUpdate;
  private javax.swing.JButton deleteButton;
  private javax.swing.JTextField error;
  private java.awt.GridBagLayout gridBagLayout1;
  private javax.swing.JTextArea helpArea;
  private javax.swing.JComboBox jComboBox1;
  private javax.swing.JLabel jLabel9;
  private javax.swing.JScrollPane jScrollPane1;
  private javax.swing.JSeparator jSeparator1;
  private javax.swing.JSeparator jSeparator2;
  private javax.swing.JLabel labTable;
  private javax.swing.JTextField newName;
  private javax.swing.JComboBox report;
  private javax.swing.JTextArea sql;
  private javax.swing.JTextField table;
  private javax.swing.JLabel templateLabel;
  // End of variables declaration//GEN-END:variables
  /** Creates new form ReportPanel
   * @param database */
  public ReportPanel(String database) {
    threadName=database;
    initComponents();
    report.setSelectedIndex(-1);    // Set selected type
    Look();                    // Set color background
    clearScreen();                    // Start with empty screen
  }
  private void Look() {
    UC.Look(this);
  }
  
  /** Create a JComboBox of all of the items in the table represented by this panel
   *@return A New JComboBox filled with all row keys from the table
   */
 public static JComboBox getJComboBox() { JComboBox b = new JComboBox(); getJComboBox(b); return b;}
  /** Update a JComboBox to represent the rows represented by this panel
   *@param b The JComboBox to Update
   */
  public static void getJComboBox(JComboBox b) {
    b.removeAllItems();
    makeReports();
    for (Report v1 : v) {
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
    //Util.prt("NNamePanel.setJComboBoxToID id="+ID);
    for(int i=0; i<b.getItemCount(); i++) 
      if(((Report) b.getItemAt(i)).getID() == ID) {
          b.setSelectedIndex(i);
          //Util.prt("NNamePanel.setJComboBoxToID id="+ID+" found at "+i);
      }
  }  
  
  /** Given a database ID find the index into a JComboBox consistent with this panel
   *@param ID The row ID from the database
   *@return The index in the JComboBox with this ID
   */
  public static int getJComboBoxIndex(int ID) {
    for(int i=0; i<v.size(); i++) if( ID == ((Report) v.get(i)).getID()) return i;
    return -1;
  }
  /** Cause the main JComboBox to be reloaded*/
  public static void reload() {
    v = null;
    makeReports();
  }
  
  // This routine should only need tweeking if key field is not same as table name
  private static void makeReports() {
    if (v != null) return;
    v=new ArrayList<>(100);
    try {
      Statement stmt;
      if(DBConnectionThread.getConnection(DBConnectionThread.getDBSchema()) != null) {
        stmt = DBConnectionThread.getConnection(DBConnectionThread.getDBSchema()).createStatement();   // used for query

      }
      else {
        stmt = JDBConnection.getConnection().createStatement();
      }
      String s = "SELECT * FROM report ORDER BY report";
      if(threadName.equalsIgnoreCase("IADSR")) s = "SELECT * FROM irsanreport ORDER BY report";
      ResultSet rs = stmt.executeQuery(s);
      while (rs.next()) {
        Report loc = new Report(rs);
        v.add(loc);
      }
    }
    catch (SQLException e) {
      Util.SQLErrorPrint(e,"makeReports() on table SQL failed");
    }
  }
  
  // No changes needed
  private void find() {
    if(report == null) return;
    Report l;
    if(report.getSelectedIndex() == -1) {
      if(report.getSelectedItem() == null) return;
      l = newOne();
    }
    else {
      l = (Report) report.getSelectedItem();
    }
    
    if(l == null) return;
    String p = l.getReport();
    if(p == null) return;
    if(p.equals("")) {
      addUpdate.setEnabled(false);
      addUpdate.setText("Enter a Report!");
    }
    //p = p.toUpperCase();
    error.setBackground(Color.lightGray);
    error.setText("");
    try {
      clearScreen();          // set screen to known state
      oldOne(p);
      
      // set add/Update button to indicate an update will happen
      addUpdate.setText("Update "+p);
      addUpdate.setEnabled(true);
      ID.setText(""+obj.getInt("ID"));    // Info only, show ID
    }  catch  (SQLException  E)
    { if( !E.getMessage().equals("Before start of result set"))
        Util.SQLErrorPrint(E,"report: find faild report="+p);
      clearScreen();
      ID.setText("NEW!");
      
      // Set up for "NEW" - clear fields etc.
      error.setText("NOT found - assume NEW");
      error.setBackground(UC.yellow);
      addUpdate.setText("Add "+p);
      addUpdate.setEnabled(true);
      //      report.setSelectedItem(((Report) report.getSelectedItem()).toString().toUpperCase());
    }
    
  }
/** This main displays the form Pane by itself
   *@param args command line args ignored*/
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
            if(!DBConnectionThread.waitForConnection(DBConnectionThread.getDBSchema()))
              if(!DBConnectionThread.waitForConnection(DBConnectionThread.getDBSchema()))
              {Util.prt("COuld not connect to DB "+jcjbl); 
                System.exit(1);
              }
      Show.inFrame(new ReportPanel(DBConnectionThread.getDBSchema()), UC.XSIZE, UC.YSIZE);

    }
    catch(InstantiationException e) {}


  }

}
