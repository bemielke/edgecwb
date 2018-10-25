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
 * JComboBox variable this variable is called "requestStation" in the initial form
 * The table name and key name should match and start lower case (requestStation).
 * The Class here will be that same name but with upper case at beginning(RequestStation).
 * <br> 1)  Rename the location JComboBox to the "key" (requestStation) value.  This should start Lower
 *      case.
 *<br>  2)  The table name should match this key name and be all lower case.
 *<br>  3)  Change all RequestStation to ClassName of underlying data (case sensitive!)
 *<br>  4)  Change all requestStation key value (the JComboBox above)
 *<br>  5)  clearScreen should update swing components to new defaults
 *<br>  6)  chkForm should check all field for validity.  The errorTrack variable is
 *      used to track multiple errors.  In FUtil.chk* handle checks of the various
 *      variable types.  Date, double, and IP etc return the "canonical" form for 
 *      the variable.  These should be stored in a class variable so the latest one
 *      from "chkForm()" are available to the update writer.
 *<br>  7) newone() should be updated to create a new instance of the underlying data class.
 *<br>  8) oldone() get data from edge and update all swing elements to correct values
 *<br>  9) addUpdateAction() add data base set*() operations updating all fields in the DB
 *<br> 10) RequestStationPanel() constructor - good place to change backgrounds using UC.Look() any
 *    other weird startup stuff.
 *
 *<br>  local variable error - Must be a JTextField for posting error communications
 * <br> local variable updateAdd - Must be the JButton for user clicks to try to post the data
 * <br> ID - JTextField which must be non-editable for posting edge IDs
 * <br>
 *<br>
 *ENUM notes :  the ENUM are read from the edge as type String.  So the constructor
 * should us the strings to set the indexes.  However, the writes to the edge are
 * done as ints representing the value of the Enum.  The JComboBox with the Enum has 
 * the strings as Items in the list with indexes equal to the edge value of the int.
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
public class RequestStationPanel extends javax.swing.JPanel {
  
  // NOTE : here define all variables general.  "Vector v" is used for main Comboboz
  static ArrayList<RequestStation> v;             // Vector containing objects of this RequestStation Type
  DBObject obj;
  ErrorTrack err=new ErrorTrack();
  boolean initiating=false;
  
  //USER: Here are the local variables
  int rport;    // station port
  Timestamp until;  // translation of until
  int throttleRate;
  

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
   Util.prt("chkForm RequestStation");
   until = FUtil.chkTimestamp(disableRequestUntil, err);
   rport = FUtil.chkInt(requestPort, err, 1, 32767);
   throttleRate = FUtil.chkInt(throttle, err, 1, 10000000);
   FUtil.chkJComboBox(type, "Request type", err);

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
    addUpdate.setText("Enter a RequestStation");
    
    //USER: Clear all fields for this form, this sets "defaults" for new screen
    // description.setText("");
    type.setSelectedIndex(-1);
    fetchType.setText("");
    throttle.setText("");
    requestIP.setText("");
    requestPort.setText("");
    chanRE.setText("");
    disableRequestUntil.setText("");
  }
 
  
  private RequestStation newOne() {
      
    return new RequestStation(0, ((String) requestStation.getSelectedItem()).toUpperCase() //, more
            , 0, "", 0, "",0, "", new Timestamp(100000)
       );
  }
  
  private void oldOne(String p) throws SQLException {
    obj = new DBObject(DBConnectionThread.getThread(DBConnectionThread.getDBSchema()), 
    DBConnectionThread.getDBSchema(), "requeststation","id",p);

    if(obj.isNew()) {
      Util.prt("object is new"+p);
    }
    else {
      //USER: Here set all of the form fields to data from the DBObject
      //      requestStation.setText(obj.getString("RequestStation"));
      // Example : description.setText(obj.getString("description"));
      RequestTypePanel.setJComboBoxToID(type, obj.getInt("requestid"));
      fetchType.setText(obj.getString("fetchtype"));
      throttle.setText(""+obj.getInt("throttle"));
      requestIP.setText(obj.getString("requestip"));
      requestPort.setText(""+obj.getInt("requestport"));
      chanRE.setText(obj.getString("channelre"));
      disableRequestUntil.setText(obj.getTimestamp("disablerequestuntil").toString().substring(0,16));
        
        
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
    requestStation = getJComboBox();
    addUpdate = new javax.swing.JButton();
    ID = new javax.swing.JTextField();
    jLabel9 = new javax.swing.JLabel();
    error = new javax.swing.JTextField();
    type = RequestTypePanel.getJComboBox();
    labtype = new javax.swing.JLabel();
    labFetchTpye = new javax.swing.JLabel();
    fetchType = new javax.swing.JTextField();
    labThottle = new javax.swing.JLabel();
    throttle = new javax.swing.JTextField();
    labrequestIP = new javax.swing.JLabel();
    requestIP = new javax.swing.JTextField();
    labrequestPort = new javax.swing.JLabel();
    requestPort = new javax.swing.JTextField();
    labChanre = new javax.swing.JLabel();
    chanRE = new javax.swing.JTextField();
    labDisable = new javax.swing.JLabel();
    disableRequestUntil = new javax.swing.JTextField();
    delete = new javax.swing.JButton();

    setLayout(new java.awt.GridBagLayout());

    jLabel1.setText("Station :");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 1;
    add(jLabel1, gridBagConstraints);

    requestStation.setEditable(true);
    requestStation.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        requestStationActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(requestStation, gridBagConstraints);

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

    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 3;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(type, gridBagConstraints);

    labtype.setText("Request Type:");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 3;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    add(labtype, gridBagConstraints);

    labFetchTpye.setText("Fetch Type:");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 4;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    add(labFetchTpye, gridBagConstraints);

    fetchType.setColumns(4);
    fetchType.setText("jTextField1");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 4;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(fetchType, gridBagConstraints);

    labThottle.setText("Throttle rate :");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 5;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    add(labThottle, gridBagConstraints);

    throttle.setColumns(8);
    throttle.setText("jTextField2");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 5;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(throttle, gridBagConstraints);

    labrequestIP.setText("Request IP :");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 6;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    add(labrequestIP, gridBagConstraints);

    requestIP.setColumns(20);
    requestIP.setText("jTextField3");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 6;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(requestIP, gridBagConstraints);

    labrequestPort.setText("Request Port :");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 7;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    add(labrequestPort, gridBagConstraints);

    requestPort.setColumns(8);
    requestPort.setText("jTextField4");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 7;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(requestPort, gridBagConstraints);

    labChanre.setText("Channel RegExp :");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 8;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    add(labChanre, gridBagConstraints);

    chanRE.setColumns(40);
    chanRE.setText("jTextField5");
    chanRE.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        chanREActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 8;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(chanRE, gridBagConstraints);

    labDisable.setText("Disable Requests Until :");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 9;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    add(labDisable, gridBagConstraints);

    disableRequestUntil.setColumns(20);
    disableRequestUntil.setText("jTextField6");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 9;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(disableRequestUntil, gridBagConstraints);

    delete.setText("Delete record");
    delete.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        deleteActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 18;
    add(delete, gridBagConstraints);
  }// </editor-fold>//GEN-END:initComponents

  private void addUpdateActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_addUpdateActionPerformed
    // Add your handling code here:
    
    // Do not change
    if(initiating) return;
    if(chkForm()) return;
    try {
      String p = requestStation.getSelectedItem().toString();
      p = p.toUpperCase();                  // USER : drop this to allow Mixed case
      if(p.contains("-")) p = p.substring(0, p.indexOf("-"));
      if(!obj.isNew() ) obj.refreshRecord();
      obj.setString("requestStation",p);
      
      // USER : Set all of the fields in data base.  obj.setInt("thefield", ivalue);
      obj.setInt("requestid",((RequestType) type.getSelectedItem()).getID());
      obj.setString("fetchtype", fetchType.getText());
      obj.setInt("throttle", throttleRate);
      obj.setString("requestip", requestIP.getText());
      obj.setInt("requestport", rport);
      obj.setString("channelre", chanRE.getText());
      obj.setTimestamp("disableRequestUntil", until);
      
      // Do not change
      obj.updateRecord();
      v=null;       // force reload of combo box
      getJComboBox(requestStation);
      clearScreen();
      requestStation.setSelectedIndex(-1);
    }  catch  (SQLException  E)
    { Util.SQLErrorPrint(E,"requestStation: update failed partno="+requestStation.getSelectedItem());
      error.setText("Could Not Update File!");
      error.setBackground(UC.red);
      addUpdate.setEnabled(true);
    }      
  }//GEN-LAST:event_addUpdateActionPerformed

  private void requestStationActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_requestStationActionPerformed
    // Add your handling code here:
   find();
 
 
}//GEN-LAST:event_requestStationActionPerformed

  private void chanREActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_chanREActionPerformed
    // TODO add your handling code here:
}//GEN-LAST:event_chanREActionPerformed

  private void deleteActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_deleteActionPerformed
    if(requestStation.getSelectedIndex() != -1) {
      try {
        obj.deleteRecord();
        Util.prt("DELETE item = "+requestStation.getSelectedItem());
        requestStation.removeItemAt(requestStation.getSelectedIndex());
      }
      catch(SQLException e) {
        Util.SQLErrorPrint(e, "Trying to delete a requeststation");

      }
    }
  }//GEN-LAST:event_deleteActionPerformed
  
  
  // Variables declaration - do not modify//GEN-BEGIN:variables
  private javax.swing.JTextField ID;
  private javax.swing.JButton addUpdate;
  private javax.swing.JTextField chanRE;
  private javax.swing.JButton delete;
  private javax.swing.JTextField disableRequestUntil;
  private javax.swing.JTextField error;
  private javax.swing.JTextField fetchType;
  private java.awt.GridBagLayout gridBagLayout1;
  private javax.swing.JLabel jLabel1;
  private javax.swing.JLabel jLabel9;
  private javax.swing.JLabel labChanre;
  private javax.swing.JLabel labDisable;
  private javax.swing.JLabel labFetchTpye;
  private javax.swing.JLabel labThottle;
  private javax.swing.JLabel labrequestIP;
  private javax.swing.JLabel labrequestPort;
  private javax.swing.JLabel labtype;
  private javax.swing.JTextField requestIP;
  private javax.swing.JTextField requestPort;
  private javax.swing.JComboBox requestStation;
  private javax.swing.JTextField throttle;
  private javax.swing.JComboBox type;
  // End of variables declaration//GEN-END:variables
  /** Creates new form RequestStationPanel */
  public RequestStationPanel() {
    initiating=true;
    initComponents();
    getJComboBox(requestStation);                // set up the key JComboBox
    requestStation.setSelectedIndex(-1);    // Set selected type
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
    makeRequestStations();
    for (int i=0; i< v.size(); i++) {
      b.addItem( v.get(i));
    }
    b.setMaximumRowCount(30);
  }
  
  /** Given a JComboBox from getJComboBox, set selected item to match edge ID
   *@param b The JComboBox
   *@param ID the row ID from the edge to select
   */
  public static void setJComboBoxToID(JComboBox b, int ID) {
    b.setSelectedIndex(-1);
    //Util.prt("RequestStationPanel.setJComboBoxToID id="+ID);
    for(int i=0; i<b.getItemCount(); i++) 
      if(((RequestStation) b.getItemAt(i)).getID() == ID) {
          b.setSelectedIndex(i);
          //Util.prt("RequestStationPanel.setJComboBoxToID id="+ID+" found at "+i);
      }
  }  
  
  /** Given a edge ID find the index into a JComboBox consistent with this panel
   *@param ID The row ID from the edge
   *@return The index in the JComboBox with this ID
   */
  public static int getJComboBoxIndex(int ID) {
    if(v == null) makeRequestStations();
    for(int i=0; i<v.size(); i++) if( ID == ((RequestStation) v.get(i)).getID()) return i;
    return -1;
  }
  /** Cause the main JComboBox to be reloaded*/
  public static void reload() {
    v = null;
    makeRequestStations();
  }
  /** return a vector with all of the RequestStation
   * @return The vector with the requestStation
   */
  public static ArrayList<RequestStation> getRequestStationVector() {
    if(v == null) makeRequestStations();
    return v;
  }
  /** Get the item corresponding to edge ID
   *@param ID the edge Row ID
   *@return The RequestStation row with this ID
   */
  public static RequestStation getRequestStationWithID(int ID) {
    if(v == null) makeRequestStations();
    int i=getJComboBoxIndex(ID);
    if(i >= 0) return (RequestStation) v.get(i);
    else return null;
  }
  
  // This routine should only need tweeking if key field is not same as table name
  private static void makeRequestStations() {
    if (v != null) return;
    v=new ArrayList<RequestStation>(10);
    try {
      Statement stmt = DBConnectionThread.getConnection(DBConnectionThread.getDBSchema()).createStatement();   // used for query
      String s = "SELECT * FROM edge.requeststation ORDER BY requestStation;";
      ResultSet rs = stmt.executeQuery(s);
      while (rs.next()) {
        RequestStation loc = new RequestStation(rs);
//        Util.prt("MakeRequestStation() i="+v.size()+" is "+loc.getRequestStation());
        v.add(loc);
      }
      rs.close();
      stmt.close();
    }
    catch (SQLException e) {
      Util.SQLErrorPrint(e,"makeRequestStations() on table SQL failed");
    }    
  }
  
  // No changes needed
  private void find() {
    if(requestStation == null) return;
    if(initiating) return;
    RequestStation l = null;
    if(requestStation.getSelectedIndex() == -1) {
      if(requestStation.getSelectedItem() instanceof String) {
        String userInput= (String) requestStation.getSelectedItem();
        for(int i=0; i<requestStation.getItemCount(); i++) {
          if(((RequestStation) requestStation.getItemAt(i)).toString().equalsIgnoreCase(userInput)) {
            l = (RequestStation) requestStation.getItemAt(i);
            break;
          }
        }
      }
      if(l == null) {
        if(requestStation.getSelectedItem() == null) return;
        l = newOne();
      }
    } 
    else {
      l = (RequestStation) requestStation.getSelectedItem();
    }
      
    if(l == null) return;
    String p = l.getID()+"";
    if(p == null) return;
    if(p.equals("")) {
      addUpdate.setEnabled(false);
      addUpdate.setText("Enter a RequestStation!");
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
    { Util.SQLErrorPrint(E,"RequestStation: SQL error getting RequestStation="+p);
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
      Show.inFrame(new RequestStationPanel(), UC.XSIZE, UC.YSIZE);
    }
    catch(InstantiationException e) {}
  }
  
}
