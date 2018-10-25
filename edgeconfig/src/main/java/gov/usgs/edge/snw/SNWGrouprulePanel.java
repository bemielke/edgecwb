/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */

package gov.usgs.edge.snw;
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
import java.util.ArrayList;
import javax.swing.JComboBox;
/**
 *
 * requirements : 
 * The key for the file must be the name of the table and of the
 * JComboBox variable this variable is called "snwgrouprule" in the initial form
 * The table name and key name should match and start lower case (snwgrouprule).
 * The Class here will be that same name but with upper case at beginning(SNWGrouprule).
 * <br> 1)  Rename the location JComboBox to the "key" (snwgrouprule) value.  This should start Lower
 *      case.
 *<br>  2)  The table name should match this key name and be all lower case.
 *<br>  3)  Change all SNWGrouprule to ClassName of underlying data (case sensitive!)
 *<br>  4)  Change all snwgrouprule key value (the JComboBox above)
 *<br>  5)  clearScreen should update swing components to new defaults
 *<br>  6)  chkForm should check all field for validity.  The errorTrack variable is
 *      used to track multiple errors.  In FUtil.chk* handle checks of the various
 *      variable types.  Date, double, and IP etc return the "canonical" form for 
 *      the variable.  These should be stored in a class variable so the latest one
 *      from "chkForm()" are available to the update writer.
 *<br>  7) newone() should be updated to create a new instance of the underlying data class.
 *<br>  8) oldone() get data from database and update all swing elements to correct values
 *<br>  9) addUpdateAction() add data base set*() operations updating all fields in the DB
 *<br> 10) SNWGrouprulePanel() constructor - good place to change backgrounds using UC.Look() any
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
public final class SNWGrouprulePanel extends javax.swing.JPanel {
  
  // NOTE : here define all variables general.  "Vector groupRules" is used for main Comboboz
  static ArrayList<SNWGrouprule> groupRules;             // Vector containing objects of this SNWGrouprule Type
  private DBObject obj;
  private final ErrorTrack err=new ErrorTrack();
  private boolean initiating=false;
  
  //USER: Here are the local variables
      
  private int     typeidsaveint;
  private double  minsavedouble;
  private double  maxsavedouble;
  private int     snwgroupidsaveint;
  private double  damping1savedouble;
  private double  damping2savedouble;
  private int     eventIDsavelong;
    
  

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
   Util.prt("chkForm SNWGrouprule");
//   obj.setString("format", format.getText());
//   obj.setString("typeid", typeid.getText());
   
   typeidsaveint = FUtil.chkInt(typeid,err);
   minsavedouble = FUtil.chkDouble(min,err);
   maxsavedouble = FUtil.chkDouble(max,err);
   damping1savedouble = FUtil.chkDouble(damping1,err);
   damping2savedouble = FUtil.chkDouble(damping2,err);
   
//   obj.setDouble("min", min.getText());
//   obj.setDouble("max", max.getText());
//   obj.setString("keyname", keyname.getSelectedItem());
//   obj.setInt("snwgroupid", ((SNWGroup) snwgroupid.getSelectedItem()).getID());
//   obj.setString("bitmask", bitmask.getText());
//   obj.setDouble("damping1", damping1.getText());
//   obj.setDouble("damping2", damping2.getText());
//   obj.setInt("eventID", eventid.getSelectedItem());

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
    format.setText("");
    ID.setText("");
    min.setText("");
    min.setText("");
//    keyname.setText("");
    snwgroupid.setSelectedIndex(-1);
    keyname.setSelectedIndex(-1);
    bitmask.setText("");
    damping1.setText("");
    damping2.setText("");
    eventid.setSelectedIndex(-1);
    addUpdate.setEnabled(false);
    addUpdate.setText("Enter a SNWGrouprule");
    
    //USER: Clear all fields for this form, this sets "defaults" for new screen
    // description.setText("");
  }
 
  
  private SNWGrouprule newOne() {
      
    return new SNWGrouprule(0, ((String) snwgrouprule.getSelectedItem()),
          "",     //String format, 
          0,     //int typid, 
          0.0D,   //double min, 
          0.0D,   //max, 
          "",     //keyname, 
          0,     //snwgroupid, 
          "",     //bitmask, 
          0.0D,   //damping1, 
          0.0D,   //damping2, 
          0      //eventid//, more
       );
  }
  
  private void oldOne(String p) throws SQLException {
   obj = new DBObject(DBConnectionThread.getThread(DBConnectionThread.getDBSchema()), 
           DBConnectionThread.getDBSchema(), "snwgrouprule","snwgrouprule",p);

    if(obj.isNew()) {
      Util.prt("object is new"+p);
    }
    else {
      //USER: Here set all of the form fields to data from the DBObject
      //      snwgrouprule.setText(obj.getString("SNWGrouprule"));
      // Example : description.setText(obj.getString("description"));

          format.setText(obj.getString("format"));
//          SNWGroupPanel.setJComboBoxToID(typeid,obj.getInt("typeid"));          
          typeid.setText(""+obj.getInt("typeid"));
          min.setText(""+obj.getDouble("min"));
          max.setText(""+obj.getDouble("max"));

//          keyname.setText(obj.getString("keyname"));
//          SNWRulePanel.setJComboBoxToID(snwrule,obj.getInt("snwruleID"));
          SNWGroupPanel.setJComboBoxToID(keyname,obj.getInt("keyname")); 
          SNWGroupPanel.setJComboBoxToID(snwgroupid,obj.getInt("snwgroupid"));
          bitmask.setText(obj.getString("bitmask"));
          damping1.setText(""+obj.getDouble("damping1"));
          damping2.setText(""+obj.getDouble("damping2"));

          eventid.setSelectedItem(obj.getInt("eventid"));
        
        
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
    labeventID = new javax.swing.JLabel();
    snwgrouprule = getJComboBox();
    addUpdate = new javax.swing.JButton();
    ID = new javax.swing.JTextField();
    jLabel9 = new javax.swing.JLabel();
    error = new javax.swing.JTextField();
    labsnwgrouprule = new javax.swing.JLabel();
    labformat = new javax.swing.JLabel();
    labtypeid = new javax.swing.JLabel();
    labmin = new javax.swing.JLabel();
    labmax = new javax.swing.JLabel();
    labkeyname = new javax.swing.JLabel();
    labsnwgroupid = new javax.swing.JLabel();
    labbitmask = new javax.swing.JLabel();
    labdamping1 = new javax.swing.JLabel();
    labdamping2 = new javax.swing.JLabel();
    eventid = SNWEventidPanel.getJComboBox();
    format = new javax.swing.JTextField();
    min = new javax.swing.JTextField();
    max = new javax.swing.JTextField();
    damping1 = new javax.swing.JTextField();
    snwgroupid = SNWGroupPanel.getJComboBox();
    damping2 = new javax.swing.JTextField();
    bitmask = new javax.swing.JTextField();
    keyname = SNWKeynamePanel.getJComboBox();
    typeid = new javax.swing.JTextField();

    setLayout(new java.awt.GridBagLayout());

    labeventID.setHorizontalAlignment(javax.swing.SwingConstants.RIGHT);
    labeventID.setText("EventID :");
    labeventID.setHorizontalTextPosition(javax.swing.SwingConstants.RIGHT);
    labeventID.setName("lblEventID");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 14;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    add(labeventID, gridBagConstraints);

    snwgrouprule.setEditable(true);
    snwgrouprule.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        snwgroupruleActionPerformed(evt);
      }
    });

    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(snwgrouprule, gridBagConstraints);

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

    jLabel9.setHorizontalAlignment(javax.swing.SwingConstants.RIGHT);
    jLabel9.setText("ID :");
    jLabel9.setHorizontalTextPosition(javax.swing.SwingConstants.RIGHT);
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 17;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    add(jLabel9, gridBagConstraints);

    error.setColumns(40);
    error.setEditable(false);
    error.setMinimumSize(new java.awt.Dimension(488, 22));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(error, gridBagConstraints);

    labsnwgrouprule.setHorizontalAlignment(javax.swing.SwingConstants.RIGHT);
    labsnwgrouprule.setText("SNWGroupRule :");
    labsnwgrouprule.setHorizontalTextPosition(javax.swing.SwingConstants.RIGHT);
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    add(labsnwgrouprule, gridBagConstraints);

    labformat.setHorizontalAlignment(javax.swing.SwingConstants.RIGHT);
    labformat.setText("Format :");
    labformat.setHorizontalTextPosition(javax.swing.SwingConstants.RIGHT);
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 2;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    add(labformat, gridBagConstraints);

    labtypeid.setHorizontalAlignment(javax.swing.SwingConstants.RIGHT);
    labtypeid.setText("Type ID :");
    labtypeid.setHorizontalTextPosition(javax.swing.SwingConstants.RIGHT);
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 3;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    add(labtypeid, gridBagConstraints);

    labmin.setHorizontalAlignment(javax.swing.SwingConstants.RIGHT);
    labmin.setText("Minimum :");
    labmin.setHorizontalTextPosition(javax.swing.SwingConstants.RIGHT);
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 4;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    add(labmin, gridBagConstraints);

    labmax.setHorizontalAlignment(javax.swing.SwingConstants.RIGHT);
    labmax.setText("Maximum :");
    labmax.setHorizontalTextPosition(javax.swing.SwingConstants.RIGHT);
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 5;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    add(labmax, gridBagConstraints);

    labkeyname.setHorizontalAlignment(javax.swing.SwingConstants.RIGHT);
    labkeyname.setText("Key Name :");
    labkeyname.setHorizontalTextPosition(javax.swing.SwingConstants.RIGHT);
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 6;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    add(labkeyname, gridBagConstraints);

    labsnwgroupid.setHorizontalAlignment(javax.swing.SwingConstants.RIGHT);
    labsnwgroupid.setText("Group Name :");
    labsnwgroupid.setHorizontalTextPosition(javax.swing.SwingConstants.RIGHT);
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 7;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    add(labsnwgroupid, gridBagConstraints);

    labbitmask.setHorizontalAlignment(javax.swing.SwingConstants.RIGHT);
    labbitmask.setText("BitMask");
    labbitmask.setHorizontalTextPosition(javax.swing.SwingConstants.RIGHT);
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 8;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    add(labbitmask, gridBagConstraints);

    labdamping1.setHorizontalAlignment(javax.swing.SwingConstants.RIGHT);
    labdamping1.setText("Damping1 :");
    labdamping1.setHorizontalTextPosition(javax.swing.SwingConstants.RIGHT);
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 9;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    add(labdamping1, gridBagConstraints);

    labdamping2.setHorizontalAlignment(javax.swing.SwingConstants.RIGHT);
    labdamping2.setText("Damping2 :");
    labdamping2.setHorizontalTextPosition(javax.swing.SwingConstants.RIGHT);
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 10;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    add(labdamping2, gridBagConstraints);

    eventid.setMinimumSize(new java.awt.Dimension(150, 25));
    eventid.setPreferredSize(new java.awt.Dimension(130, 25));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 14;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(eventid, gridBagConstraints);

    format.setText("   ");
    format.setMinimumSize(new java.awt.Dimension(100, 20));
    format.setPreferredSize(new java.awt.Dimension(130, 20));
    format.setRequestFocusEnabled(false);
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 2;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(format, gridBagConstraints);

    min.setText("0.0");
    min.setPreferredSize(new java.awt.Dimension(130, 20));
    min.setRequestFocusEnabled(false);
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 4;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(min, gridBagConstraints);

    max.setText("1000.0");
    max.setPreferredSize(new java.awt.Dimension(130, 20));
    max.setRequestFocusEnabled(false);
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 5;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(max, gridBagConstraints);

    damping1.setText("damping1");
    damping1.setPreferredSize(new java.awt.Dimension(130, 20));
    damping1.setRequestFocusEnabled(false);
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 9;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(damping1, gridBagConstraints);

    snwgroupid.setPreferredSize(new java.awt.Dimension(130, 25));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 7;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(snwgroupid, gridBagConstraints);

    damping2.setText("damping2");
    damping2.setPreferredSize(new java.awt.Dimension(130, 20));
    damping2.setRequestFocusEnabled(false);
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 10;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(damping2, gridBagConstraints);

    bitmask.setText(" ");
    bitmask.setPreferredSize(new java.awt.Dimension(130, 20));
    bitmask.setRequestFocusEnabled(false);
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 8;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(bitmask, gridBagConstraints);

    keyname.setPreferredSize(new java.awt.Dimension(130, 25));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 6;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(keyname, gridBagConstraints);

    typeid.setText(" ");
    typeid.setMinimumSize(new java.awt.Dimension(100, 20));
    typeid.setPreferredSize(new java.awt.Dimension(130, 20));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 3;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(typeid, gridBagConstraints);

  }// </editor-fold>//GEN-END:initComponents

  private void addUpdateActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_addUpdateActionPerformed
    // Add your handling code here:
    
    // Do not change
    if(initiating) return;
    if(chkForm()) return;
    try {
      String p = snwgrouprule.getSelectedItem().toString();
      p = p.toUpperCase();                  // USER : drop this to allow Mixed case
      if(!obj.isNew()) obj.refreshRecord();        // Refresh the result set so the sets will not fail on stale one
      obj.setString("snwgrouprule",p);
      
      // USER : Set all of the fields in data base.  obj.setInt("thefield", ivalue);
      obj.setString("format", format.getText());
      obj.setInt("typeid", typeidsaveint);
      obj.setDouble("min", minsavedouble);
      obj.setDouble("max", maxsavedouble);
      obj.setString("keyname", ( keyname.getSelectedItem().toString()));
      obj.setInt("snwgroupid", ((SNWGroup) snwgroupid.getSelectedItem()).getID());
      obj.setString("bitmask", bitmask.getText());
      obj.setDouble("damping1", damping1savedouble);
      obj.setDouble("damping2", damping2savedouble);
      obj.setInt("eventID", ((SNWEventid) eventid.getSelectedItem()).getID());
      
//                  rs.getString("snwgrouprule"),
//            rs.getString("format"),
//            rs.getLong("typeid"),
//            rs.getDouble("min"),
//            rs.getDouble("max"),
//            rs.getString("keyname"),
//            rs.getInt("snwgroupid"),
//            rs.getString("bitmask"),
//            rs.getDouble("damping1"),
//            rs.getDouble("damping2"),
//            rs.getLong("eventID")
      
      // Do not change
      obj.updateRecord();
      groupRules=null;       // force reload of combo box
      getJComboBox(snwgrouprule);
      clearScreen();
      snwgrouprule.setSelectedIndex(-1);
    }  catch  (SQLException  E)
    { Util.SQLErrorPrint(E,"snwgrouprule: update failed partno="+snwgrouprule.getSelectedItem());
      error.setText("Could Not Update File!");
      error.setBackground(UC.red);
      addUpdate.setEnabled(true);
    }      
  }//GEN-LAST:event_addUpdateActionPerformed

  private void snwgroupruleActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_snwgroupruleActionPerformed
    // Add your handling code here:
   find();
 
 
  }//GEN-LAST:event_snwgroupruleActionPerformed
  
  
  // Variables declaration - do not modify//GEN-BEGIN:variables
  private javax.swing.JTextField ID;
  private javax.swing.JButton addUpdate;
  private javax.swing.JTextField bitmask;
  private javax.swing.JTextField damping1;
  private javax.swing.JTextField damping2;
  private javax.swing.JTextField error;
  private javax.swing.JComboBox eventid;
  private javax.swing.JTextField format;
  private java.awt.GridBagLayout gridBagLayout1;
  private javax.swing.JLabel jLabel9;
  private javax.swing.JComboBox keyname;
  private javax.swing.JLabel labbitmask;
  private javax.swing.JLabel labdamping1;
  private javax.swing.JLabel labdamping2;
  private javax.swing.JLabel labeventID;
  private javax.swing.JLabel labformat;
  private javax.swing.JLabel labkeyname;
  private javax.swing.JLabel labmax;
  private javax.swing.JLabel labmin;
  private javax.swing.JLabel labsnwgroupid;
  private javax.swing.JLabel labsnwgrouprule;
  private javax.swing.JLabel labtypeid;
  private javax.swing.JTextField max;
  private javax.swing.JTextField min;
  private javax.swing.JComboBox snwgroupid;
  private javax.swing.JComboBox snwgrouprule;
  private javax.swing.JTextField typeid;
  // End of variables declaration//GEN-END:variables
  /** Creates new form SNWGrouprulePanel. */
  public SNWGrouprulePanel() {

    initiating=true;
    initComponents();
    getJComboBox(snwgrouprule);                // set up the key JComboBox
    snwgrouprule.setSelectedIndex(-1);    // Set selected type
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
  /** Update a JComboBox to represent the rows represented by this panel
   *@param b The JComboBox to Update
   */
  public static void getJComboBox(JComboBox b) {
    b.removeAllItems();
    makeSNWGrouprules();
    for (int i=0; i< groupRules.size(); i++) {
      b.addItem(groupRules.get(i));
    }
    b.setMaximumRowCount(30);
  }
  
  /** Given a JComboBox from getJComboBox, set selected item to match database ID
   *@param b The JComboBox
   *@param ID the row ID from the database to select
   */
  public static void setJComboBoxToID(JComboBox b, int ID) {
    b.setSelectedIndex(-1);
    //Util.prt("SNWGrouprulePanel.setJComboBoxToID id="+ID);
    for(int i=0; i<b.getItemCount(); i++) 
      if(((SNWGrouprule) b.getItemAt(i)).getID() == ID) {
          b.setSelectedIndex(i);
          //Util.prt("SNWGrouprulePanel.setJComboBoxToID id="+ID+" found at "+i);
      }
  }  
  
  /** Given a database ID find the index into a JComboBox consistent with this panel
   *@param ID The row ID from the database
   *@return The index in the JComboBox with this ID
   */
  public static int getJComboBoxIndex(int ID) {
    if(groupRules == null) makeSNWGrouprules();
    for(int i=0; i<groupRules.size(); i++) if( ID == ((SNWGrouprule) groupRules.get(i)).getID()) return i;
    return -1;
  }
  /** Cause the main JComboBox to be reloaded*/
  public static void reload() {
    groupRules = null;
    makeSNWGrouprules();
  }
  /** return a vector with all of the SNWGrouprule
   * @return The vector with the snwgrouprule
   */
  public static ArrayList getSNWGroupruleVector() {
    if(groupRules == null) makeSNWGrouprules();
    return groupRules;
  }
  /** Get the item corresponding to database ID
   *@param ID the database Row ID
   *@return The SNWGrouprule row with this ID
   */
  public static SNWGrouprule getItemWithID(int ID) {
    if(groupRules == null) makeSNWGrouprules();
    int i=getJComboBoxIndex(ID);
    if(i >= 0) return (SNWGrouprule) groupRules.get(i);
    else return null;
  }
  
  // This routine should only need tweeking if key field is not same as table name
  private static void makeSNWGrouprules() {
    if (groupRules != null) return;
    groupRules=new ArrayList<SNWGrouprule>(100);
    try {
      try (Statement stmt = DBConnectionThread.getConnection(DBConnectionThread.getDBSchema()).createStatement() // used for query
      ) {
        String s = "SELECT * FROM snwgrouprule ORDER BY snwgrouprule;";
        try (ResultSet rs = stmt.executeQuery(s)) {
          while (rs.next()) {
            SNWGrouprule loc = new SNWGrouprule(rs);
            //        Util.prt("MakeSNWGrouprule() i="+groupRules.size()+" is "+loc.getSNWGrouprule());
            groupRules.add(loc);
          }
        }
      }
    }
    catch (SQLException e) {
      Util.SQLErrorPrint(e,"makeSNWGrouprules() on table SQL failed");
    }    
  }
  
  // No changes needed
  private void find() {
    if(snwgrouprule == null) return;
    if(initiating) return;
    SNWGrouprule l;
    if(snwgrouprule.getSelectedIndex() == -1) {
      if(snwgrouprule.getSelectedItem() == null) return;
      l = newOne();
    } 
    else {
      l = (SNWGrouprule) snwgrouprule.getSelectedItem();
    }
      
    if(l == null) return;
    String p = l.getSNWGrouprule().toString();
    if(p == null) return;
    if(p.equals("")) {
      addUpdate.setEnabled(false);
      addUpdate.setText("Enter a SNWGrouprule!");
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
    { Util.SQLErrorPrint(E,"SNWGrouprule: SQL error getting SNWGrouprule="+p);
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
      Show.inFrame(new SNWGrouprulePanel(), UC.XSIZE, UC.YSIZE);
    }
    catch(InstantiationException e) {}
  }
}
