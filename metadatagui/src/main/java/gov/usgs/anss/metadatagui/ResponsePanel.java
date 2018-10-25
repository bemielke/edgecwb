/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */

package gov.usgs.anss.metadatagui;
import gov.usgs.anss.db.DBConnectionThread;
import gov.usgs.anss.db.DBObject;
import gov.usgs.anss.gui.FlagPanel2;
import gov.usgs.anss.util.ErrorTrack;
import gov.usgs.anss.util.Show;
import gov.usgs.anss.util.Util;
import java.awt.Color;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.DecimalFormat;
import java.util.ArrayList;
import javax.swing.JComboBox;

/**
 *
 * requirements : 
 * The key for the file must be the name of the table and of the
 * JComboBox variable this variable is called "response" in the initial form
 * The table name and key name should match and start lower case (response).
 * The Class here will be that same name but with upper case at beginning(Response).
 * <br> 1)  Rename the location JComboBox to the "key" (response) value.  This should start Lower
 *      case.
 *<br>  2)  The table name should match this key name and be all lower case.
 *<br>  3)  Change all Response to ClassName of underlying data (case sensitive!)
 *<br>  4)  Change all response key value (the JComboBox above)
 *<br>  5)  clearScreen should update swing components to new defaults
 *<br>  6)  chkForm should check all field for validity.  The errorTrack variable is
 *      used to track multiple errors.  In FUtil.chk* handle checks of the various
 *      variable types.  Date, double, and IP etc return the "canonical" form for 
 *      the variable.  These should be stored in a class variable so the latest one
 *      from "chkForm()" are available to the update writer.
 *<br>  7) newone() should be updated to create a new instance of the underlying data class.
 *<br>  8) oldone() get data from database and update all swing elements to correct values
 *<br>  9) addUpdateAction() add data base set*() operations updating all fields in the DB
 *<br> 10) ResponsePanel() constructor - good place to change backgrounds using UC.Look() any
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
 *<br> FUtil.getEnumJComboBox(DBConnectionThread.getConnection("anss"), "tcpstation","rtsport1");
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
 * <br>    if(fieldEnum == null) fieldEnum = FUtil.getEnum(DBConnectionThread.getConnection("anss"),"table","fieldName");
 * <br>    
 * <br>   // Get the int corresponding to an Enum String for storage in the data base and 
 * <br>   // the index into the JComboBox representing this Enum 
 * <br>   localVariable = FUtil.enumStringToInt(fieldEnum, enumString);
  * <br>
 * @author  D.C. Ketchum
 */
public class ResponsePanel extends javax.swing.JPanel {
  
  // NOTE : here define all variables general.  "ArrayList v" is used for main Comboboz
  static ArrayList<Response> v;             // ArrayList containing objects of this Response Type
  DBObject obj;
  ErrorTrack err=new ErrorTrack();
  boolean initiating=false;
  
  //USER: Here are the local variables
  static DecimalFormat e6 = new DecimalFormat("0.0000E00");
  static DecimalFormat deg = new DecimalFormat("0.00000");  
  long mask = 0l;
  long intFlags =0l;
  
    public class LoadResponseComboBox extends Thread {
    public boolean stopFlag= false;
    public String str;
//    private Set kset;
//    public DBConnectionThread mysql;
//    private boolean debug=false;
//    private SNWGrouprule myrule;



    public void run(LoadResponseComboBox resp)                       
    {              
      stopFlag= false;
  //    SNWGroup Group = ns.getGroup();

      while (!stopFlag){
  //      kset = Group.getGroupList();
  //      Group.getSNWGroup("Unknown");
        try {sleep(1000);} catch (InterruptedException e){}

      }           
    }  
  }

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
   Util.prt("chkForm Response");

   intFlags = fpGroupMask.getCurrentMask();

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
    addUpdate.setText("Enter a Response");
    
    //USER: Clear all fields for this form, this sets "defaults" for new screen
    // description.setText("");
    response.setSelectedIndex(-1);
    epoch.setSelectedIndex(-1);
    jTextArea1.setText("");
    fpGroupMask.setAllClear();   
    
  }
 
  
//  private Response newOne() {
//      
//    return new Response(0, ((String) response.getSelectedItem()).toUpperCase() //, more
//       );
//  }
  
  private void oldOne(String p) throws SQLException {
    
     obj = new DBObject(DBConnectionThread.getThread("metadata"), 
        "metadata", "response","ID",p);
   
    long flags=obj.getLong("flags");
    fpGroupMask.setAllClear();
    ArrayList fpList=fpGroupMask.getList();
    ArrayList radioList = fpGroupMask.getRadio();

    if(obj.isNew()) {
      Util.prt("object is new"+p);
    }
    else {
      int seli = epoch.getSelectedIndex();
      if (seli == -1) {
//          clearScreen();
          jTextArea1.setText("");
//          for(int i=0; i<radioList.size(); i++) {
//            JRadioButton rad = (JRadioButton) radioList.get(i);
//            ChannelFlags grp = (ChannelFlags) fpList.get(i);
//            rad.setSelected( false );
//             //if(rad.isSelected() ) groupmask |= 1L << (grp.getID()-1);
//          }
          return;
      }
      //USER: Here set all of the form fields to data from the DBObject
      //      response.setText(obj.getString("Response"));
      // Example : description.setText(obj.getString("description"));
      // Load group flags

      
      fpGroupMask.setMask(obj.getLong("flags"));
      StringBuilder sbx = new StringBuilder(1000);
      
        sbx.append(Util.rightPad(obj.getString("channel"),12)).append(" - ");
        sbx.append(" From ").append(obj.getString("effective"));
        sbx.append(" To ").append(obj.getString("endingDate"));
        sbx.append("                      [").append(Util.leftPad(obj.getString("ID"),6)).append("]\n");
        sbx.append("Dip : ").append(obj.getString("dip")).append("\n");
        sbx.append("Depth : ").append(deg.format(obj.getDouble("depth"))).append("  ");
        sbx.append("Azimuth : ").append(deg.format(obj.getDouble("azimuth"))).append("  ");
        sbx.append("Rate : ").append(deg.format(obj.getDouble("rate"))).append("  ");
        sbx.append("Units : ").append(obj.getString("units")).append("\n");
        sbx.append("SeedFlags : ").append(obj.getString("seedflags")).append("\n");
        sbx.append("InstrumentGain : ").append(deg.format(obj.getDouble("instrumentGain"))).append("  ");
        sbx.append("InstrumentType : ").append(obj.getString("instrumentType")).append("\n");
        sbx.append("a0 : ").append(e6.format(obj.getDouble("a0"))).append("  ");
        sbx.append("a0calc : ").append(e6.format(obj.getDouble("a0calc"))).append("  ");
        sbx.append("a0freq : ").append(deg.format(obj.getDouble("a0freq"))).append("\n");
        sbx.append("Sensitivity : ").append(e6.format(obj.getDouble("sensitivity"))).append("  ");
        sbx.append("SensitivityCalc : ").append(e6.format(obj.getDouble("sensitivityCalc"))).append("\n");
        sbx.append("latitude : ").append(deg.format(obj.getDouble("latitude"))).append("  ");
        sbx.append("longitude : ").append(deg.format(obj.getDouble("longitude"))).append("  ");
        sbx.append("elevation : ").append(deg.format(obj.getDouble("elevation"))).append("\n");
        sbx.append("CookedResponse : ").append(obj.getString("cookedresponse"));
//      sbx.append("Dip : "+obj.getString("dip")+"\n");
//      sbx.append("Azimuth : "+obj.getDouble("azimuth")+"\n");
//      sbx.append("Depth : "+obj.getDouble("depth")+"\n");
//      sbx.append("CookedResponse : "+obj.getString("cookedresponse")+"\n");
//      sbx.append("Rate : "+obj.getDouble("rate")+"\n");
//      sbx.append("SeedFlags : "+obj.getString("seedflags")+"\n");
//      sbx.append("InstrumentType : "+obj.getString("instrumentType")+"\n");
//      sbx.append("InstrumentGain : "+obj.getDouble("instrumentGain")+"\n");
//      sbx.append("Units : "+obj.getString("units")+"\n");
//      sbx.append("a0 : "+obj.getDouble("a0")+"\n");
//      sbx.append("a0calc : "+obj.getDouble("a0calc")+"\n");
//      sbx.append("a0freq : "+obj.getDouble("a0freq")+"\n");
//      sbx.append("Sensitivity : "+obj.getDouble("sensitivity")+"\n");
//      sbx.append("SensitivityCalc : "+obj.getDouble("sensitivityCalc")+"\n");
//      sbx.append("latitude : "+obj.getDouble("latitude")+"\n");
//      sbx.append("longitude : "+obj.getDouble("longitude")+"\n");
//      sbx.append("elevation : "+obj.getDouble("elevation")+"\n");
//      sbx.append("Effective : "+obj.getTimestamp("effective").toString()+"\n");
//      sbx.append("EndingDate : "+obj.getTimestamp("endingDate").toString()+"\n");
      jTextArea1.setText(sbx.toString());
      


        
        
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
    lblChannel = new javax.swing.JLabel();
    response = new javax.swing.JComboBox();
    addUpdate = new javax.swing.JButton();
    ID = new javax.swing.JTextField();
    jLabel9 = new javax.swing.JLabel();
    error = new javax.swing.JTextField();
    jLabel1 = new javax.swing.JLabel();
    lblEpoch = new javax.swing.JLabel();
    epoch = new javax.swing.JComboBox();
    flagPanel = new javax.swing.JPanel();
    jScrollPane1 = new javax.swing.JScrollPane();
    jTextArea1 = new javax.swing.JTextArea();
    btnLoadResponse = new javax.swing.JButton();

    setLayout(new java.awt.GridBagLayout());

    lblChannel.setText("Channel : ");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 1;
    add(lblChannel, gridBagConstraints);

    response.setEditable(true);
    response.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        responseActionPerformed(evt);
      }
    });
    response.addKeyListener(new java.awt.event.KeyAdapter() {
      public void keyTyped(java.awt.event.KeyEvent evt) {
        responseKeyTyped(evt);
      }
      public void keyReleased(java.awt.event.KeyEvent evt) {
        responseKeyReleased(evt);
      }
    });

    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(response, gridBagConstraints);

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

    jLabel1.setText("Flags : ");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 6;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    add(jLabel1, gridBagConstraints);

    lblEpoch.setText("Epochs : ");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 2;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    add(lblEpoch, gridBagConstraints);

    epoch.setEditable(true);
    epoch.setMinimumSize(new java.awt.Dimension(200, 25));
    epoch.setPreferredSize(new java.awt.Dimension(200, 25));
    epoch.addItemListener(new java.awt.event.ItemListener() {
      public void itemStateChanged(java.awt.event.ItemEvent evt) {
        epochItemStateChanged(evt);
      }
    });

    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 2;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(epoch, gridBagConstraints);

    flagPanel.setBackground(new java.awt.Color(204, 255, 204));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 6;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(flagPanel, gridBagConstraints);

    jScrollPane1.setPreferredSize(new java.awt.Dimension(488, 280));
    jTextArea1.setColumns(20);
    jTextArea1.setRows(5);
    jScrollPane1.setViewportView(jTextArea1);

    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 8;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(jScrollPane1, gridBagConstraints);

    btnLoadResponse.setText("Load Channels!");
    btnLoadResponse.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        btnLoadResponseActionPerformed(evt);
      }
    });

    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    add(btnLoadResponse, gridBagConstraints);

  }// </editor-fold>//GEN-END:initComponents

  private void btnLoadResponseActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnLoadResponseActionPerformed
    btnLoadResponse.setText("Loading . . . ");
    btnLoadResponse.repaint(1l);
    try {
      Thread.sleep(1000);
    }
    catch (InterruptedException e){
      Util.prt("Oops - nodding off"+e.toString());
    }
    MetaChannelPanel.getJComboBox(response);
    response.setSelectedIndex(-1);
    btnLoadResponse.setText("Channels Loaded");
    btnLoadResponse.repaint(1l);

  }//GEN-LAST:event_btnLoadResponseActionPerformed

  private void responseKeyReleased(java.awt.event.KeyEvent evt) {//GEN-FIRST:event_responseKeyReleased
// TODO add your handling code here:
//    String r = ((Response) response.getSelectedItem()).toString();
//    if (r==null) {
//      ;
//    }
//    else {
//      response.setSelectedItem(r);
//    }
  }//GEN-LAST:event_responseKeyReleased

  private void responseKeyTyped(java.awt.event.KeyEvent evt) {//GEN-FIRST:event_responseKeyTyped
// TODO add your handling code here:
//    String r = ((Response) response.getSelectedItem()).toString();
//    if (r==null) {
//      ;
//    }
//    else {
//      response.setSelectedItem(r);
//    }
  }//GEN-LAST:event_responseKeyTyped

  private void epochItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_epochItemStateChanged
// TODO add your handling code here:
    Response l = (Response) epoch.getSelectedItem();
      
    if(l == null) return;
    String p = ""+l.getID();
    if(p == null) return;
    if(p.equals("")) {
      addUpdate.setEnabled(false);
      addUpdate.setText("Choose an Epoch!");
    }
//    p = p.toUpperCase();
    error.setBackground(Color.lightGray);
    error.setText("");
    try {
//      clearScreen();          // set screen to known state
      oldOne(p);
      
      // set add/Update button to indicate an update will happen
      if(obj.isNew()) {
          addUpdate.setText("Add "+p);
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
      { Util.SQLErrorPrint(E,"Response: SQL error getting Response="+p);
    }
    
    
    
  }//GEN-LAST:event_epochItemStateChanged

  private void addUpdateActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_addUpdateActionPerformed
    // Add your handling code here:
    
    // Do not change
    if(initiating) return;
    if(chkForm()) return;
    try {
      String p = response.getSelectedItem().toString();
//      p = p.toUpperCase();                  // USER : drop this to allow Mixed case
      if(!obj.isNew()) obj.refreshRecord();        // Refresh the result set so the sets will not fail on stale one
//      obj.setString("response",p);
      
      // USER : Set all of the fields in data base.  obj.setInt("thefield", ivalue);
      // GroupMask
 
      obj.setLong("flags",intFlags);
      
      // Do not change
      obj.updateRecord();
      v=null;       // force reload of combo box
      MetaChannelPanel.getJComboBox(response);
      clearScreen();
      response.setSelectedIndex(-1);
    }  catch  (SQLException  E)
    { Util.SQLErrorPrint(E,"response: update failed partno="+response.getSelectedItem());
      error.setText("Could Not Update File!");
      error.setBackground(UC.red);
      addUpdate.setEnabled(true);
    }      
  }//GEN-LAST:event_addUpdateActionPerformed

  private void responseActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_responseActionPerformed
    // Add your handling code here:
    if (response.getSelectedIndex()==-1){
      epoch.removeAllItems();
      epoch.setSelectedIndex(-1);// Do nothing;
    }
    else
      ResponsePanel.getJComboBox(epoch,response.getSelectedItem().toString());   
    if (epoch.getItemCount()==1 ){
      epoch.setSelectedIndex(0);
    }
    else {
      epoch.setSelectedIndex(-1);
      jTextArea1.setText("");
      fpGroupMask.setAllClear();
    }
 
 
  }//GEN-LAST:event_responseActionPerformed

  
  // Variables declaration - do not modify//GEN-BEGIN:variables
  private javax.swing.JTextField ID;
  private javax.swing.JButton addUpdate;
  private javax.swing.JButton btnLoadResponse;
  private javax.swing.JComboBox epoch;
  private javax.swing.JTextField error;
  private javax.swing.JPanel flagPanel;
  private java.awt.GridBagLayout gridBagLayout1;
  private javax.swing.JLabel jLabel1;
  private javax.swing.JLabel jLabel9;
  private javax.swing.JScrollPane jScrollPane1;
  private javax.swing.JTextArea jTextArea1;
  private javax.swing.JLabel lblChannel;
  private javax.swing.JLabel lblEpoch;
  private javax.swing.JComboBox response;
  // End of variables declaration//GEN-END:variables
   private FlagPanel2 fpGroupMask;
   
  /** Creates new form ResponsePanel */
  public ResponsePanel() {
    initiating=true;
    initComponents();
    fpGroupMask = new FlagPanel2(ResponseFlagsPanel.getJComboBox(), 600,3);
    UC.Look(fpGroupMask);
    UC.Look(flagPanel);
    flagPanel.add(fpGroupMask);   
//    MetaChannelPanel.getJComboBox(response);                // set up the key JComboBox
    response.setSelectedIndex(-1);    // Set selected type
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
    makeResponses();
    for (int i=0; i< v.size(); i++) {
      b.addItem( v.get(i));
    }
    b.setMaximumRowCount(30);
  }
  public static void getJComboBox(JComboBox b, String str) {
    b.removeAllItems();
    if (str==null) ; // Don't do anything
    else makeResponses(str);
    if (v.isEmpty()) return;
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
    //Util.prt("ResponsePanel.setJComboBoxToID id="+ID);
    for(int i=0; i<b.getItemCount(); i++) 
      if(((Response) b.getItemAt(i)).getID() == ID) {
          b.setSelectedIndex(i);
          //Util.prt("ResponsePanel.setJComboBoxToID id="+ID+" found at "+i);
      }
  }  
  
  /** Given a database ID find the index into a JComboBox consistent with this panel
   *@param ID The row ID from the database
   *@return The index in the JComboBox with this ID
   */
  public static int getJComboBoxIndex(int ID) {
    if(v == null) makeResponses();
    for(int i=0; i<v.size(); i++) if( ID == ((Response) v.get(i)).getID()) return i;
    return -1;
  }
  /** Cause the main JComboBox to be reloaded*/
  public static void reload() {
    v = null;
    makeResponses();
  }
    /* return a ArrayList with all of the Response
   * @return The ArrayList with the response
   */
  public static ArrayList getResponseArrayList() {
    if(v == null) makeResponses();
    return v;
  }
  /** Get the item corresponding to database ID
   *@param ID the database Row ID
   *@return The Response row with this ID
   */
  public static Response getResponseWithID(int ID) {
    if(v == null) makeResponses();
    int i=getJComboBoxIndex(ID);
    if(i >= 0) return (Response) v.get(i);
    else return null;
  }
  
  // This routine should only need tweeking if key field is not same as table name
  private static void makeResponses() {
    if (v != null) return;
    v=new ArrayList<Response>(100);
    try {
      Statement stmt = DBConnectionThread.getConnection("metadata").createStatement();   // used for query
      String s = "SELECT * FROM metadata.response ORDER BY channel;";
      ResultSet rs = stmt.executeQuery(s);
      while (rs.next()) {
        Response loc = new Response(rs);
//        Util.prt("MakeResponse() i="+v.size()+" is "+loc.getResponse());
        v.add(loc);
      }
      rs.close();
      stmt.close();
    }
    catch (SQLException e) {
      Util.SQLErrorPrint(e,"makeResponses() on table SQL failed");
    }    
  }
  private static void makeResponses(String str) {
//    if (v != null) return;
    v=new ArrayList<Response>(100);
    try {
      Statement stmt = DBConnectionThread.getConnection("metadata").createStatement();   // used for query
      String s = "SELECT * FROM metadata.response WHERE channel ='"+str+"' ORDER BY effective;";
      ResultSet rs = stmt.executeQuery(s);
      while (rs.next()) {
        Response loc = new Response(rs);
//        Util.prt("MakeEvent() i="+v.size()+" is "+loc.getEvent());
        v.add(loc);
      }
      rs.close();
      stmt.close();
    }
    catch (SQLException e) {
      Util.SQLErrorPrint(e,"makeResponse() on table SQL failed");
    }    
  }   
  private static void makeResponses(String str, String str2) {
//    if (v != null) return;
    v=new ArrayList<Response>(100);
    try {
      Statement stmt = DBConnectionThread.getConnection("metadata").createStatement();   // used for query
      String s = "SELECT * FROM metadata.response WHERE channel ='"+str+"' AND effective = '"+str2+"';";
      ResultSet rs = stmt.executeQuery(s);
      while (rs.next()) {
        Response loc = new Response(rs);
//        Util.prt("MakeEvent() i="+v.size()+" is "+loc.getEvent());
        v.add(loc);
      }
    }
    catch (SQLException e) {
      Util.SQLErrorPrint(e,"makeResponse() on table SQL failed");
    }    
  }  
  
  // No changes needed
  private void find() {
    if(response == null) return;
    if(initiating) return;
    Response l;
    if(response.getSelectedIndex() == -1) {
      if(response.getSelectedItem() == null) return;
      return;
//      l = newOne();
    } 
    else {
      l = (Response) response.getSelectedItem();
    }
      
    if(l == null) return;
    String p = l.getResponse();
    if(p == null) return;
    if(p.equals("")) {
      addUpdate.setEnabled(false);
      addUpdate.setText("Enter a Response!");
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
    { Util.SQLErrorPrint(E,"Response: SQL error getting Response="+p);
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
      Show.inFrame(new ResponsePanel(), UC.XSIZE, UC.YSIZE);
    }
    catch(InstantiationException e) {}
  }
}
