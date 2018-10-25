/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */

package gov.usgs.anss.gui;
/*
 * CpuLinkIPPanel.java
 *
 * Created on July 16, 2002, 2:51 PM
 * Version as of November 30, 2003
 */

/**
 *
 * @author  ketchum
 * requirements : 
 * The key for the file must be the name of the table and of the
 * JComboBox variable this variable is called "location" in the initial form
 * The table name and key name should match and start lower case (cpuLinkIP).
 * The Class here will be that same name but with upper case at beginning(CpuLinkIP).
 * 1)  Rename the location JComboBox to the "key" (cpuLinkIP) value.  This should start Lower
 *      case.
 * 2)  The table name should match this key name and be all lower case.
 * 3)  Change all CpuLinkIP to ClassName of underlying data (case sensitive!)
 * 4)  Change all cpuLinkIP key value (the JComboBox above)
 * 5)  clearScreen should update swing components to new defaults
 * 6)  chkForm should check all field for validity.  The errorTrack variable is
 *      used to track multiple errors.  In FUtil.chk* handle checks of the various
 *      variable types.  Date, double, and IP etc return the "canonical" form for 
 *      the variable.  These should be stored in a class variable so the latest one
 *      from "chkForm()" are available to the update writer.
 * 7) newone() should be updated to create a new instance of the underlying data class.
 * 8) oldone() get data from database and update all swing elements to correct values
 * 9) addUpdateAction() add data base set*() operations updating all fields in the DB
 *10) CpuLinkIPPanel() constructor - good place to change backgrounds using UC.Look() any
 *    other weird startup stuff.
 *
 * local variable error - Must be a JTextField for posting error communications
 * local variable updateAdd - Must be the JButton for user clicks to try to post the data
 * ID - JTextField which must be non-editable for posting Database IDs
 * 
 *ENUM notes :  the ENUM are read from the database as type String.  So the constructor
 * should us the strings to set the indexes.  However, the writes to the database are
 * done as ints representing the value of the Enum.  The JComboBox with the Enum has 
 * the strings as Items in the list with indexes equal to the database value of the int.
 * Such JComboBoxes are usually initalized with 
 * FUtil.getEnumJComboBox(UC.getConnection(), "tcpstation","rtsport1");
 * Put this in "CodeGeneration"  "CustomCreateCode" of the form editor for each Enum.
 *
 * In  oldone() get Enum fields with something like :
 *  fieldJcomboBox.setSelectedItem(obj.getString("fieldName"));
 * (This sets the JComboBox to the Item matching the string)
 *
 * data class should have code like :
 * import java.util.ArrayList;          /// Import for Enum manipulation
 *   .
 *  static ArrayList fieldEnum;         // This list will have Strings corresponding to enum
 *       .
 *       .
 *    // To convert an enum we need to create the fieldEnum ArrayList.  Do only once(static)
 *    if(fieldEnum == null) fieldEnum = FUtil.getEnum(UC.getConnection(),"table","fieldName");
 *    
 *   // Get the int corresponding to an Enum String for storage in the data base and 
 *   // the index into the JComboBox representing this Enum 
 *   localVariable = FUtil.enumStringToInt(fieldEnum, enumString);
  
 */

import gov.usgs.anss.guidb.CpuLinkIP;
import gov.usgs.anss.guidb.Cpu;
import gov.usgs.anss.guidb.CommLink;
import gov.usgs.anss.db.DBConnectionThread;
import gov.usgs.anss.db.DBObject;
import gov.usgs.anss.util.ErrorTrack;
import gov.usgs.anss.util.Show;
import gov.usgs.anss.util.Util;
import gov.usgs.anss.guidb.TCPStation;
import java.awt.Color;
import java.sql.SQLException;
import javax.swing.JComboBox;

public final class CpuLinkIPPanel extends javax.swing.JPanel {
  
  // NOTE : here define all variables general.  "Vector v" is used for main Comboboz
  private DBObject obj;
  private final ErrorTrack err=new ErrorTrack();
  
  // Here are the local variables
  private String ip;
  
  

  // This routine must validate all fields on the form.  The err (ErrorTrack) variable
  // is used by the FUtil to verify fields.  Use FUTil or custom code here for verifications
  private boolean chkForm() {
    // Do not change
   err.reset();
   UC.Look(error);
   
   // Your error checking code goes here setting the local variables to valid settings
   // note : Use FUtil.chk* to check dates, timestamps, IP addresses, etc and put them in
   // canonical form.  store in a class variable.  For instance :
   //   sdate = FUtil.chkDate(dateTextField, err);
   // Any errors found should err.set(true);  and err.append("error message");
   Util.prt("chkForm CpuLinkIP");
   ip = TCPStation.chkIP(ipadr,err);
   


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
    addUpdate.setText("Enter a CpuLinkIP");
    
    // Clear all fields for this form, this sets "defaults" for new screen
    cpu.setSelectedIndex(-1);
    commlink.setSelectedIndex(-1);
    ipadr.setText("");
  }
 
  
  private CpuLinkIP newOne() {
      
    return new CpuLinkIP(0, "new", 0,0,"000.000.000.000"
       );
  }
  
  private void oldOne(String p) throws SQLException {
    obj = new DBObject(DBConnectionThread.getThread(DBConnectionThread.getDBSchema()), DBConnectionThread.getDBSchema(), "cpulinkip","cpulinkip",p);

    if(obj.isNew()) {
      Util.prt("object is new"+p);
    }
    else {
      // Here set all of the form fields to data from the DBObject
      //      cpuLinkIP.setText(obj.getString("CpuLinkIP"));
      // Example : description.setText(obj.getString("description"));
      ipadr.setText(obj.getString("ipadr"));
      CpuPanel.setJComboBoxToID(cpu,obj.getInt("cpuID"));
      CommLinkPanel.setJComboBoxToID(commlink, obj.getInt("commlinkID"));
      
        
        
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
        cpuLinkIP = getJComboBox();
        addUpdate = new javax.swing.JButton();
        ID = new javax.swing.JTextField();
        jLabel9 = new javax.swing.JLabel();
        error = new javax.swing.JTextField();
        labComm = new javax.swing.JLabel();
        commlink = CommLinkPanel.getJComboBox();
        labIPadr = new javax.swing.JLabel();
        ipadr = new javax.swing.JTextField();
        labCPU = new javax.swing.JLabel();
        cpu = CpuPanel.getJComboBox();

        setLayout(new java.awt.GridBagLayout());

        jLabel1.setText("Node-CommLink Key :");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        add(jLabel1, gridBagConstraints);

        cpuLinkIP.setEditable(true);
        cpuLinkIP.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                cpuLinkIPActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        add(cpuLinkIP, gridBagConstraints);

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

        labComm.setText("CommLink :");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
        add(labComm, gridBagConstraints);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        add(commlink, gridBagConstraints);

        labIPadr.setText("IP address :");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 5;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
        add(labIPadr, gridBagConstraints);

        ipadr.setColumns(20);
        ipadr.setText("000.000.000.000");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 5;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        add(ipadr, gridBagConstraints);

        labCPU.setText("Role:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
        add(labCPU, gridBagConstraints);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        add(cpu, gridBagConstraints);
    }// </editor-fold>//GEN-END:initComponents

  private void addUpdateActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_addUpdateActionPerformed
    // Add your handling code here:
    
    // Do not change
    if(chkForm()) return;
    try {
      String p = cpuLinkIP.getSelectedItem().toString();
      p = p.toUpperCase();                  // USER : drop this to allow Mixed case
      if(!obj.isNew()) obj.refreshRecord();
      obj.setString("cpuLinkIP",p);
      
      // USER : Set all of the fields in data base.  obj.setInt("thefield", ivalue);
      obj.setString("cpulinkip",((Cpu) cpu.getSelectedItem()).getCpu()+"-"+
        ((CommLink) commlink.getSelectedItem()).getCommLink());
      obj.setInt("cpuID",((Cpu) cpu.getSelectedItem()).getID());
      obj.setInt("commlinkID", ((CommLink) commlink.getSelectedItem()).getID());
      obj.setString("ipadr", ip);
      
      
      // Do not change
      obj.updateRecord();
      CpuLinkIP.v=null;
      getJComboBox(cpuLinkIP);
      clearScreen();
      cpuLinkIP.setSelectedIndex(-1);
    }  catch  (SQLException  E)
    { Util.SQLErrorPrint(E,"cpuLinkIP: update failed partno="+cpuLinkIP.getSelectedItem());
      error.setText("Could Not Update File!");
      error.setBackground(UC.red);
      addUpdate.setEnabled(true);
    }      
  }//GEN-LAST:event_addUpdateActionPerformed

  private void cpuLinkIPActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_cpuLinkIPActionPerformed
  {//GEN-HEADEREND:event_cpuLinkIPActionPerformed
    // Add your handling code here:
   find();
 
 
  }//GEN-LAST:event_cpuLinkIPActionPerformed
  
  
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JTextField ID;
    private javax.swing.JButton addUpdate;
    private javax.swing.JComboBox commlink;
    private javax.swing.JComboBox cpu;
    private javax.swing.JComboBox cpuLinkIP;
    private javax.swing.JTextField error;
    private java.awt.GridBagLayout gridBagLayout1;
    private javax.swing.JTextField ipadr;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel9;
    private javax.swing.JLabel labCPU;
    private javax.swing.JLabel labComm;
    private javax.swing.JLabel labIPadr;
    // End of variables declaration//GEN-END:variables
  /** Creates new form CpuLinkIPPanel */
  public CpuLinkIPPanel() {
    initComponents();
    getJComboBox(cpuLinkIP);                // set up the key JComboBox
    cpuLinkIP.setSelectedIndex(-1);    // Set selected type
    UC.Look(this);                    // Set color background
   
    // USER:This is a good place for UC.Look(components) to get rid of the ugly Grey
    
    clearScreen();                    // Start with empty screen
  }
  
  //////////////////////////////////////////////////////////////////////////////
  // No USER: changes needed below here!
  //////////////////////////////////////////////////////////////////////////////
 public static JComboBox getJComboBox() { JComboBox b = new JComboBox(); getJComboBox(b); return b;}
  
  public static void getJComboBox(JComboBox b) {
    b.removeAllItems();
    makeCpuLinkIPs();
    for (int i=0; i< CpuLinkIP.v.size(); i++) {
      b.addItem( CpuLinkIP.v.get(i));
    }
    b.setMaximumRowCount(30);
   
  }
  public static CpuLinkIP getCpuLinkIPfromIDs(int cpu, int commlink) {
    return CpuLinkIP.getCpuLinkIPfromIDs(cpu, commlink);

  }
  public static String getIPfromIDs(int cpu, int commlink) {
    return CpuLinkIP.getIPfromIDs(cpu, commlink);
  }
  // Given a JComboBox from getJComboBox, set selected item to match database ID
  public static void setJComboBoxToID(JComboBox b, int ID) {
    b.setSelectedIndex(-1);
    //Util.prt("CpuLinkIPPanel.setJComboBoxToID id="+ID);
    for(int i=0; i<b.getItemCount(); i++) 
      if(((CpuLinkIP) b.getItemAt(i)).getID() == ID) {
          b.setSelectedIndex(i);
          //Util.prt("CpuLinkIPPanel.setJComboBoxToID id="+ID+" found at "+i);
      }
    
  }  
  
  // IF you have the maintcodeID and need to know where it is in the combo box, call this!
  public static int getJComboBoxIndex(int ID) {
    for(int i=0; i<CpuLinkIP.v.size(); i++) if( ID == CpuLinkIP.v.get(i).getID()) return i;
    return -1;
  }
  
  // This routine should only need tweeking if key field is not same as table name
  private static void makeCpuLinkIPs() {
    if (CpuLinkIP.v != null) return;
    CpuLinkIP.makeCpuLinkIPs();
  }
  
  // No changes needed
  private void find() {
    if(cpuLinkIP == null) return;
    CpuLinkIP l;
    if(cpuLinkIP.getSelectedIndex() == -1) {
      if(cpuLinkIP.getSelectedItem() == null) return;
      l = newOne();
    } 
    else {
      l = (CpuLinkIP) cpuLinkIP.getSelectedItem();
    }
      
    if(l == null) return;
    String p = l.getCpuLinkIP();
    if(p == null) return;
    if(p.equals("")) {
      addUpdate.setEnabled(false);
      addUpdate.setText("Enter a CpuLinkIP!");
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
    { Util.SQLErrorPrint(E,"CpuLinkIP: SQL error getting CpuLinkIP="+p);
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
      Show.inFrame(new CpuLinkIPPanel(), UC.XSIZE, UC.YSIZE);
    }
    catch(InstantiationException e) {}
  }
}

