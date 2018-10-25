/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */

package gov.usgs.anss.gui;
import gov.usgs.anss.guidb.Q330;
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
 * The Class here will be that same name but with upper case at beginning(Q330).
 * <br> 1)  Rename the location JComboBox to the "key" (station) value.  This should start Lower
 *      case.
 *<br>  2)  The table name should match this key name and be all lower case.
 *<br>  3)  Change all Q330 to ClassName of underlying data (case sensitive!)
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
 *<br> 10) Q330Panel() constructor - good place to change backgrounds using UC.Look() any
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
public final class Q330Panel extends javax.swing.JPanel {
  
  // NOTE : here define all variables general.  "ArrayList v" is used for main Comboboz
  static ArrayList<Q330> v;             // ArrayList containing objects of this Q330 Type
  private DBObject obj;
  private final ErrorTrack err=new ErrorTrack();
  private boolean initiating=false;
  
  // Here are the local variables
  private long authValue;
  

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
   Util.prt("chkForm Q330");
   if(q330.getSelectedIndex() != -1) {
     Q330 q = (Q330) q330.getSelectedItem();
     if(q.getq330().length() != 4) {
       err.set();
       err.appendText("Serial must be 4 characters!");
     }
   }
    if(hexserial.getText().length() < 5 || !hexserial.getText().substring(0,2).equals("0x")) {
      err.appendText("Must start with 0x");
      err.set();
    }
    else {
      try {
        long val = Long.parseLong(hexserial.getText().substring(2),16);
        Util.prt("val = "+Util.toHex(val));
        /*if( (val & 0xFFFFF00000000000L)  != 0x100000000000000L) {
          err.appendText("Must start 0x10000000000000 is "+Util.toHex(val)+" (wrong # digits?)");
          err.set();
        }*/
      }
      catch(NumberFormatException e) {
        err.appendText("Number format error (not hex digits)");
        err.set();
      }

    }
    authValue=FUtil.chkLongHex(authcode, err, true);
    if(authValue == 0) calcAuthPorts.setText("1=0x0 2=0x0 3=0x0");
    else {
      calcAuthPorts.setText("1="+Util.toHex(Q330.calcAuthcodeDataPort(hexserial.getText(), 1))+
              " 2="+Util.toHex(Q330.calcAuthcodeDataPort(hexserial.getText(), 2))+
              " 3="+Util.toHex(Q330.calcAuthcodeDataPort(hexserial.getText(), 3)));
    }

    // No CHANGES : If we found an error, color up error box
    if(err.isSet()) {
      addUpdate.setEnabled(false);
      error.setText(err.getText());
      error.setBackground(gov.usgs.anss.util.UC.red);
    } else {error.setText("");addUpdate.setEnabled(true);}
    return err.isSet();
  }
  
  // This routine must set the form to initial state.  It does not update the JCombobox
  private void clearScreen() {
    
    // Do not change
    ID.setText("");
    gov.usgs.anss.util.UC.Look(error);
    error.setText("");
    addUpdate.setEnabled(false);
    addUpdate.setText("Enter a Q330 location");
    
    // Clear all fields for this form, this sets "defaults" for new screen
    hexserial.setText("");
    authcode.setText("");
  }
 
  
  private Q330 newOne() {
      
    return new Q330(0, ((String) q330.getSelectedItem()).toUpperCase(), "",0
       );
  }
  
  private void oldOne(String p) throws SQLException {
    obj = new DBObject(DBConnectionThread.getThread(DBConnectionThread.getDBSchema()), DBConnectionThread.getDBSchema(), "q330","q330",p);

    if(obj.isNew()) {
      Util.prt("object is new"+p);
    }
    else {
      // Here set all of the form fields to data from the DBObject
      //      q330.setText(obj.getString("Q330"));
      // Example : description.setText(obj.getString("description"));
      hexserial.setText(obj.getString("hexserial"));
      authcode.setText(Util.toHex(obj.getLong("authcode")).toString());
      calcAuthcode.setText(Util.toHex(Q330.calcAuthcode(obj.getString("hexserial"))).toString());
        
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
    q330 = getJComboBox();
    addUpdate = new javax.swing.JButton();
    ID = new javax.swing.JTextField();
    jLabel9 = new javax.swing.JLabel();
    error = new javax.swing.JTextField();
    hexserial = new javax.swing.JTextField();
    jLabel3 = new javax.swing.JLabel();
    labAuthcode = new javax.swing.JLabel();
    authcode = new javax.swing.JTextField();
    calcAuthcode = new javax.swing.JTextField();
    labCalcAuth = new javax.swing.JLabel();
    labPortAuth = new javax.swing.JLabel();
    calcAuthPorts = new javax.swing.JTextField();

    setLayout(new java.awt.GridBagLayout());

    jLabel1.setText("Serial (nnnn):");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 1;
    add(jLabel1, gridBagConstraints);

    q330.setEditable(true);
    q330.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        q330ActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(q330, gridBagConstraints);

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

    hexserial.setColumns(20);
    hexserial.setMinimumSize(new java.awt.Dimension(248, 22));
    hexserial.addFocusListener(new java.awt.event.FocusAdapter() {
      public void focusLost(java.awt.event.FocusEvent evt) {
        hexserialFocusLost(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 4;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(hexserial, gridBagConstraints);

    jLabel3.setText("Hex Serial (0x...)");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 4;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    add(jLabel3, gridBagConstraints);

    labAuthcode.setText("AuthCode (in Hex):");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 5;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    add(labAuthcode, gridBagConstraints);

    authcode.setColumns(10);
    authcode.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        authcodeActionPerformed(evt);
      }
    });
    authcode.addFocusListener(new java.awt.event.FocusAdapter() {
      public void focusLost(java.awt.event.FocusEvent evt) {
        authcodeFocusLost(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 5;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(authcode, gridBagConstraints);

    calcAuthcode.setColumns(10);
    calcAuthcode.setEditable(false);
    calcAuthcode.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        calcAuthcodeActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 6;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(calcAuthcode, gridBagConstraints);

    labCalcAuth.setText("AuthCode (calc in Hex):");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 6;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    add(labCalcAuth, gridBagConstraints);

    labPortAuth.setText("Port authcodes:");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 7;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    add(labPortAuth, gridBagConstraints);

    calcAuthPorts.setColumns(40);
    calcAuthPorts.setEditable(false);
    calcAuthPorts.setMinimumSize(new java.awt.Dimension(450, 28));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 7;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(calcAuthPorts, gridBagConstraints);
  }// </editor-fold>//GEN-END:initComponents

  private void calcAuthcodeActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_calcAuthcodeActionPerformed
// TODO add your handling code here:
  }//GEN-LAST:event_calcAuthcodeActionPerformed

  private void hexserialFocusLost(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_hexserialFocusLost
// TODO add your handling code here:
    if(!chkForm()) 
      calcAuthcode.setText(Util.toHex(Q330.calcAuthcode(hexserial.getText())).toString());
    
  }//GEN-LAST:event_hexserialFocusLost

  private void addUpdateActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_addUpdateActionPerformed
    // Add your handling code here:
    
    // Do not change
    if(initiating) return;
    if(chkForm()) return;
    try {
      String p = q330.getSelectedItem().toString();
      p = p.toUpperCase();                  // USER : drop this to allow Mixed case
      if(!obj.isNew()) obj.refreshRecord(); // Refresh the result set so the sets will not fail on stale one
      obj.setString("q330",p);
      
      // USER : Set all of the fields in data base.  obj.setInt("thefield", ivalue);
      obj.setString("hexserial",hexserial.getText());
      obj.setLong("authcode", authValue);
      
      // Do not change
      obj.updateRecord();
      v=null;       // force reload of combo box
      getJComboBox(q330);
      clearScreen();
      q330.setSelectedIndex(-1);
    }  catch  (SQLException  E)
    { Util.SQLErrorPrint(E,"vsat: update failed q330="+q330.getSelectedItem());
      error.setText("Could Not Update File!");
      error.setBackground(gov.usgs.anss.util.UC.red);
      addUpdate.setEnabled(true);
    }      
  }//GEN-LAST:event_addUpdateActionPerformed

  private void q330ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_q330ActionPerformed
    // Add your handling code here:
   find();
 
 
  }//GEN-LAST:event_q330ActionPerformed

  private void authcodeFocusLost(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_authcodeFocusLost
    if(authcode.getText().equalsIgnoreCase("c")) authcode.setText(calcAuthcode.getText());
    chkForm();
  }//GEN-LAST:event_authcodeFocusLost

  private void authcodeActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_authcodeActionPerformed
    if(authcode.getText().equalsIgnoreCase("c")) authcode.setText(calcAuthcode.getText());
    chkForm();

  }//GEN-LAST:event_authcodeActionPerformed
  
  
  // Variables declaration - do not modify//GEN-BEGIN:variables
  private javax.swing.JTextField ID;
  private javax.swing.JButton addUpdate;
  private javax.swing.JTextField authcode;
  private javax.swing.JTextField calcAuthPorts;
  private javax.swing.JTextField calcAuthcode;
  private javax.swing.JTextField error;
  private java.awt.GridBagLayout gridBagLayout1;
  private javax.swing.JTextField hexserial;
  private javax.swing.JLabel jLabel1;
  private javax.swing.JLabel jLabel3;
  private javax.swing.JLabel jLabel9;
  private javax.swing.JLabel labAuthcode;
  private javax.swing.JLabel labCalcAuth;
  private javax.swing.JLabel labPortAuth;
  private javax.swing.JComboBox q330;
  // End of variables declaration//GEN-END:variables
  /** Creates new form Q330Panel */
  public Q330Panel() {
    initiating=true;
    initComponents();
    getJComboBox(q330);                // set up the key JComboBox
    q330.setSelectedIndex(-1);    // Set selected type
    gov.usgs.anss.util.UC.Look(this);                    // Set color background
    UC.Look(calcAuthcode);
    UC.Look(calcAuthPorts);
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
    makeQ330s();
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
    //Util.prt("Q330Panel.setJComboBoxToID id="+ID);
    for(int i=0; i<b.getItemCount(); i++) 
      if(((Q330) b.getItemAt(i)).getID() == ID) {
          b.setSelectedIndex(i);
          //Util.prt("Q330Panel.setJComboBoxToID id="+ID+" found at "+i);
      }
  }  
  
  /** Given a database ID find the index into a JComboBox consistent with this panel
   *@param ID The row ID from the database
   *@return The index in the JComboBox with this ID
   */
  public static int getJComboBoxIndex(int ID) {
    for(int i=0; i<v.size(); i++) if( ID == ((Q330) v.get(i)).getID()) return i;
    return -1;
  }
  /** Cause the main JComboBox to be reloaded*/
  public static void reload() {
    v = null;
    makeQ330s();
  }
  
  /** Get the item corresponding to database ID
   *@param ID the database Row ID
   *@return The Q330 row with this ID
   */
  public static Q330 getItemWithID(int ID) {
    int i=getJComboBoxIndex(ID);
    if(i >= 0) return (Q330) v.get(i);
    else return null;
  }
  
  // This routine should only need tweeking if key field is not same as table name
  private static void makeQ330s() {
    if (v != null) return;
    v=new ArrayList<Q330>(100);
    try {
      try (Statement stmt = DBConnectionThread.getConnection(DBConnectionThread.getDBSchema()).createStatement() // used for query
      ) {
        String s = "SELECT * FROM anss.q330 ORDER BY q330;";
        try (ResultSet rs = stmt.executeQuery(s)) {
          while (rs.next()) {
            Q330 loc = new Q330(rs);
//        Util.prt("MakeQ330() i="+v.size()+" is "+loc.getQ330());
            v.add(loc);
          }
        }
      }
    }
    catch (SQLException e) {
      Util.SQLErrorPrint(e,"makeQ330s() on table SQL failed");
    }    
  }
  
  // No changes needed
  private void find() {
    if(q330 == null) return;
    if(initiating) return;
    Q330 l;
    if(q330.getSelectedIndex() == -1) {
      if(q330.getSelectedItem() == null) return;
      if( ((String) q330.getSelectedItem()).length() != 4) {
        error.setText("Serials must be 4 characters!");
        error.setBackground(UC.red);
        return;
      }
      l = newOne();
    } 
    else {
      l = (Q330) q330.getSelectedItem();
    }
      
    if(l == null) return;
    String p = l.getq330();
    if(p == null) return;
    if(p.equals("")) {
      addUpdate.setEnabled(false);
      addUpdate.setText("Enter a Q330!");
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
        chkForm();
        addUpdate.setText("Update "+p);
        addUpdate.setEnabled(true);
        ID.setText(""+obj.getInt("ID"));    // Info only, show ID
      }
    }  
    // This is only thrown if something happens in oldone() new DBOject
    // Other than it being a new record.
    catch  (SQLException  E)                                 // 
    { Util.SQLErrorPrint(E,"Q330: SQL error getting Q330="+p);
      E.printStackTrace();
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
      Show.inFrame(new Q330Panel(), UC.XSIZE, UC.YSIZE);
    }
    catch(InstantiationException e) {}
  }
}
