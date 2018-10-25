/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */

package gov.usgs.anss.gui;


/**
 *
 * @author  ketchum
 * requirements : 
 * The key for the file must be the name of the table and of the
 * JComboBox variable this variable is called "location" in the initial form
 * The table name and key name should match and start lower case (cpu).
 * The Class here will be that same name but with upper case at beginning(Cpu).
 * 1)  Rename the location JComboBox to the "key" (cpu) value.  This should start Lower
 *      case.
 * 2)  The table name should match this key name and be all lower case.
 * 3)  Change all Cpu to ClassName of underlying data (case sensitive!)
 * 4)  Change all cpu key value (the JComboBox above)
 * 5)  clearScreen should update swing components to new defaults
 * 6)  chkForm should check all field for validity.  The errorTrack variable is
 *      used to track multiple errors.  In FUtil.chk* handle checks of the various
 *      variable types.  Date, double, and IP etc return the "canonical" form for 
 *      the variable.  These should be stored in a class variable so the latest one
 *      from "chkForm()" are available to the update writer.
 * 7) newone() should be updated to create a new instance of the underlying data class.
 * 8) oldone() get data from database and update all swing elements to correct values
 * 9) addUpdateAction() add data base set*() operations updating all fields in the DB
 *10) CpuPanel() constructor - good place to change backgrounds using UC.Look() any
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
 * FUtil.getEnumJComboBox(DBConnectionThread.getConnection("anss"), "tcpstation","rtsport1");
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
 *    if(fieldEnum == null) fieldEnum = FUtil.getEnum(DBConnectionThread.getConnection("anss"),"table","fieldName");
 *    
 *   // Get the int corresponding to an Enum String for storage in the data base and 
 *   // the index into the JComboBox representing this Enum 
 *   localVariable = FUtil.enumStringToInt(fieldEnum, enumString);
  
 */
import gov.usgs.anss.guidb.Cpu;
import gov.usgs.anss.db.DBConnectionThread;
import gov.usgs.anss.db.DBObject;
import gov.usgs.anss.util.ErrorTrack;
import gov.usgs.anss.util.Show;
import gov.usgs.anss.util.Util;
import java.awt.Color;
import java.sql.SQLException;
import javax.swing.JComboBox;


public final class CpuPanel extends javax.swing.JPanel {
  
  // NOTE : here define all variables general.  "ArrayList v" is used for main Comboboz
  private DBObject obj;
  private final ErrorTrack err=new ErrorTrack();
  
  // Here are the local variables
  private int roles;
  
  

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
   Util.prt("chkForm Cpu");
   roles=0;
   if(isConsole.isSelected()) roles |=Cpu.ROLE_ANSS_CONSOLE;
   if(isRTS.isSelected()) roles |=Cpu.ROLE_ANSS_RTS;
   if(isData.isSelected()) roles |=Cpu.ROLE_ANSS_DATA;
   if(isMShear.isSelected()) roles |=Cpu.ROLE_ANSS_MSHEAR;
   if(isGPS.isSelected()) roles |=Cpu.ROLE_ANSS_GPS;


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
    addUpdate.setText("Enter a Cpu");
    
    // Clear all fields for this form, this sets "defaults" for new screen
    dottedName.setText("");
    os.setText("");
    roles=0;
    isConsole.setSelected(false);
    isRTS.setSelected(false);
    isData.setSelected(false);
    isMShear.setSelected(false);
    isGPS.setSelected(false);
    

  }
 
  
  private Cpu newOne() {
      
    return new Cpu(0, ((String) cpu.getSelectedItem()).toUpperCase(),
        "dot.ted.name","No OS", 0);
  }
  
  private void oldOne(String p) throws SQLException {
    obj = new DBObject(DBConnectionThread.getThread(DBConnectionThread.getDBSchema()), DBConnectionThread.getDBSchema(), "cpu","cpu",p);

    if(obj.isNew()) {
      Util.prt("object is new"+p);
    }
    else {
      // Here set all of the form fields to data from the DBObject
      //      cpu.setText(obj.getString("Cpu"));
      // Example : description.setText(obj.getString("description"));
      dottedName.setText(obj.getString("Dotted_name"));
      os.setText(obj.getString("os"));
      roles=obj.getInt("roles");
      if( (roles & Cpu.ROLE_ANSS_CONSOLE) != 0) isConsole.setSelected(true);
      if( (roles & Cpu.ROLE_ANSS_DATA) != 0) isData.setSelected(true);
      if( (roles & Cpu.ROLE_ANSS_RTS) != 0) isRTS.setSelected(true);
      if( (roles & Cpu.ROLE_ANSS_GPS) != 0) isGPS.setSelected(true);
      if( (roles & Cpu.ROLE_ANSS_MSHEAR) != 0) isMShear.setSelected(true);
        
        
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
        cpu = getJComboBox();
        addUpdate = new javax.swing.JButton();
        ID = new javax.swing.JTextField();
        jLabel9 = new javax.swing.JLabel();
        error = new javax.swing.JTextField();
        labDotted = new javax.swing.JLabel();
        labOs = new javax.swing.JLabel();
        os = new javax.swing.JTextField();
        isConsole = new javax.swing.JRadioButton();
        isRTS = new javax.swing.JRadioButton();
        isData = new javax.swing.JRadioButton();
        isMShear = new javax.swing.JRadioButton();
        isGPS = new javax.swing.JRadioButton();
        dottedName = new javax.swing.JTextField();
        delete = new javax.swing.JButton();

        setLayout(new java.awt.GridBagLayout());

        jLabel1.setText("Role Name :");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        add(jLabel1, gridBagConstraints);

        cpu.setEditable(true);
        cpu.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                cpuActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        add(cpu, gridBagConstraints);

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
        error.setMinimumSize(new java.awt.Dimension(20, 22));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        add(error, gridBagConstraints);

        labDotted.setText("Dotted IP USGS addr:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 3;
        add(labDotted, gridBagConstraints);

        labOs.setText("Operating System:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 4;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
        add(labOs, gridBagConstraints);

        os.setColumns(10);
        os.setMinimumSize(new java.awt.Dimension(100, 22));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 4;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        add(os, gridBagConstraints);

        isConsole.setText("Is Console Allowed?");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 5;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        add(isConsole, gridBagConstraints);

        isRTS.setText("Is RTS allowed?");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 6;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        add(isRTS, gridBagConstraints);

        isData.setText("Is Data Allowed?");
        isData.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                isDataActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 7;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        add(isData, gridBagConstraints);

        isMShear.setText("Is MShear Allowed?");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 8;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        add(isMShear, gridBagConstraints);

        isGPS.setText("Is GPS allowed?");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 9;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        add(isGPS, gridBagConstraints);

        dottedName.setColumns(50);
        dottedName.setText("this is the dotted address");
        dottedName.setMinimumSize(new java.awt.Dimension(200, 22));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        add(dottedName, gridBagConstraints);

        delete.setText("Delete");
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

  private void isDataActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_isDataActionPerformed
  {//GEN-HEADEREND:event_isDataActionPerformed
    // Add your handling code here:
  }//GEN-LAST:event_isDataActionPerformed

  private void addUpdateActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_addUpdateActionPerformed
    // Add your handling code here:
    
    // Do not change
    if(chkForm()) return;
    try {
      String p = cpu.getSelectedItem().toString();
      //p = p.toUpperCase();                  // USER : drop this to allow Mixed case
      if(!obj.isNew()) obj.refreshRecord();        // Refresh the result set so the sets will not fail on stale one
      obj.setString("cpu",p);
      
      // USER : Set all of the fields in data base.  obj.setInt("thefield", ivalue);
      obj.setString("dotted_name", dottedName.getText());
      obj.setString("os",os.getText());
      obj.setInt("roles",roles);
      
      // Do not change
      obj.updateRecord();
      Cpu.cpus=null;
      getJComboBox(cpu);
      clearScreen();
      cpu.setSelectedIndex(-1);
    }  catch  (SQLException  E)
    { Util.SQLErrorPrint(E,"cpu: update failed partno="+cpu.getSelectedItem());
      error.setText("Could Not Update File!");
      error.setBackground(UC.red);
      addUpdate.setEnabled(true);
    }      
  }//GEN-LAST:event_addUpdateActionPerformed

  private void cpuActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_cpuActionPerformed
  {//GEN-HEADEREND:event_cpuActionPerformed
    // Add your handling code here:
   find();
 
 
  }//GEN-LAST:event_cpuActionPerformed

private void deleteActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_deleteActionPerformed
  if(cpu.getSelectedIndex() != -1) {
    try {
      obj.deleteRecord();
      Util.prt("DELETE item = "+cpu.getSelectedItem());
      cpu.removeItemAt(cpu.getSelectedIndex());
    }
    catch(SQLException e) {
      Util.SQLErrorPrint(e, "Trying to delete a role");

    }
  }
}//GEN-LAST:event_deleteActionPerformed
  
  
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JTextField ID;
    private javax.swing.JButton addUpdate;
    private javax.swing.JComboBox cpu;
    private javax.swing.JButton delete;
    private javax.swing.JTextField dottedName;
    private javax.swing.JTextField error;
    private java.awt.GridBagLayout gridBagLayout1;
    private javax.swing.JRadioButton isConsole;
    private javax.swing.JRadioButton isData;
    private javax.swing.JRadioButton isGPS;
    private javax.swing.JRadioButton isMShear;
    private javax.swing.JRadioButton isRTS;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel9;
    private javax.swing.JLabel labDotted;
    private javax.swing.JLabel labOs;
    private javax.swing.JTextField os;
    // End of variables declaration//GEN-END:variables
  /** Creates new form CpuPanel */
  public CpuPanel() {
    initComponents();
    getJComboBox(cpu);                // set up the key JComboBox
    cpu.setSelectedIndex(-1);    // Set selected type
    init();
    // USER:This is a good place for UC.Look(components) to get rid of the ugly Grey
    
    clearScreen();                    // Start with empty screen
  }
  private void init() {
    UC.Look(this);                    // Set color background

  }
  //////////////////////////////////////////////////////////////////////////////
  // No USER: changes needed below here!
  //////////////////////////////////////////////////////////////////////////////
 public static JComboBox getJComboBox() { JComboBox b = new JComboBox(); getJComboBox(b); return b;}
  
  public static void getJComboBox(JComboBox b) {
    b.removeAllItems();
    makeCpus();
    for (int i=0; i< Cpu.cpus.size(); i++) {
      b.addItem( Cpu.cpus.get(i));
    }
    b.setMaximumRowCount(30);
  }
  
  // Given a JComboBox from getJComboBox, set selected item to match database ID
  public static void setJComboBoxToID(JComboBox b, int ID) {
    b.setSelectedIndex(-1);
    //Util.prt("CpuPanel.setJComboBoxToID id="+ID);
    for(int i=0; i<b.getItemCount(); i++) 
      if(((Cpu) b.getItemAt(i)).getID() == ID) {
          b.setSelectedIndex(i);
          //Util.prt("CommLinkPanel.setJComboBoxToID id="+ID+" found at "+i);
      }
  }  
  
  // IF you have the maintcodeID and need to know where it is in the combo box, call this!
  public static int getJComboBoxIndex(int ID) {
    makeCpus();
    for(int i=0; i<Cpu.cpus.size(); i++) if( ID ==  Cpu.cpus.get(i).getID()) return i;
    return -1;
  }
  
  public static int getIDForCpu(String cpu) {
    makeCpus();
    for(int i=0; i<Cpu.cpus.size(); i++)  
      if ( cpu.equalsIgnoreCase( Cpu.cpus.get(i).getCpu()) || cpu.equalsIgnoreCase( Cpu.cpus.get(i).getDottedName()))  
        return ((Cpu) Cpu.cpus.get(i)).getID();
    return 0;
  }
  public static String getIPForID(int ID) {
    makeCpus();
    for(int i=0; i<Cpu.cpus.size(); i++)  
      if ( ID == (Cpu.cpus.get(i).getID())) 
        return Cpu.cpus.get(i).getDottedName();
    return null;
  }    
  
  public static Cpu getCpuForID(int ID) {
    makeCpus();
    for(int i=0; i<Cpu.cpus.size(); i++)  
      if ( ID == (((Cpu) Cpu.cpus.get(i)).getID())) 
        return ((Cpu) Cpu.cpus.get(i));
    return null;
  }    
  // This routine should only need tweeking if key field is not same as table name
  private static void makeCpus() {
    Cpu.makeCpus();
  }
  
  // No changes needed
  private void find() {
    if(cpu == null) return;
    Cpu l;
    if(cpu.getSelectedIndex() == -1) {
      if(cpu.getSelectedItem() == null) return;
      l = newOne();
    } 
    else {
      l = (Cpu) cpu.getSelectedItem();
    }
      
    if(l == null) return;
    String p = l.getCpu();
    if(p == null) return;
    if(p.equals("")) {
      addUpdate.setEnabled(false);
      addUpdate.setText("Enter a Cpu!");
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
    { Util.SQLErrorPrint(E,"Cpu: SQL error getting Cpu="+p);
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
            if(!DBConnectionThread.waitForConnection(DBConnectionThread.getDBSchema()))
              if(!DBConnectionThread.waitForConnection(DBConnectionThread.getDBSchema()))
              {Util.prt("COuld not connect to DB "+jcjbl); 
                System.exit(1);
              }
      Show.inFrame(new CpuPanel(), UC.XSIZE, UC.YSIZE);
    }
    catch(InstantiationException e) {}


  }
}
