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
import java.util.ArrayList;
import javax.swing.JComboBox;

/**
 *
 * requirements : 
 * The key for the file must be the name of the table and of the
 * JComboBox variable this variable is called "role" in the initial form
 * The table name and key name should match and start lower case (role).
 * The Class here will be that same name but with upper case at beginning(Role).
 * <br> 1)  Rename the location JComboBox to the "key" (role) value.  This should start Lower
 *      case.
 *<br>  2)  The table name should match this key name and be all lower case.
 *<br>  3)  Change all Role to ClassName of underlying data (case sensitive!)
 *<br>  4)  Change all role key value (the JComboBox above)
 *<br>  5)  clearScreen should update swing components to new defaults
 *<br>  6)  chkForm should check all field for validity.  The errorTrack variable is
 *      used to track multiple errors.  In FUtil.chk* handle checks of the various
 *      variable types.  Date, double, and IP etc return the "canonical" form for 
 *      the variable.  These should be stored in a class variable so the latest one
 *      from "chkForm()" are available to the update writer.
 *<br>  7) newone() should be updated to create a new instance of the underlying data class.
 *<br>  8) oldone() get data from database and update all swing elements to correct values
 *<br>  9) addUpdateAction() add data base set*() operations updating all fields in the DB
 *<br> 10) RolePanel() constructor - good place to change backgrounds using UC.Look() any
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
public class RolePanel extends javax.swing.JPanel {
  
  // NOTE : here define all variables general.  "Vector roles" is used for main Comboboz
  DBObject obj;
  ErrorTrack err=new ErrorTrack();
  boolean initiating=false;
  String stripadr = null;
  
  //USER: Here are the local variables
  int origCpuID;
  

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
   Util.prt("chkForm Role");
   addUpdate.setEnabled(true);
   if(ipadr.getText().trim().equals("")) stripadr="";
   else stripadr = FUtil.chkIP(ipadr,err,true);
    String [] oldAccounts = accounts.getText().replaceAll("  ", " ").replaceAll("  ", " ").split(" ");
    String [] newAccounts = changeAccounts.getText().replaceAll("  ", " ").replaceAll("  ", " ").split(" ");
    if(oldAccounts.length != newAccounts.length) {
      err.set(true); err.appendText("#new accounts != #old accounts");
    }

   // check to see if this cpu selection results in a conflict in accounts on this node
   try {
     if(role.getSelectedIndex() != -1 && cpuID.getSelectedIndex()!=-1) {
       // NONE cpu or NULL is always OK
       if(!(""+cpuID.getSelectedItem()).equals("null") && !"NONE".equals(""+cpuID.getSelectedItem())) {
         ArrayList<Integer> rolesOnCpu = new ArrayList<Integer>();
         ArrayList<String> toAccounts = new ArrayList<String>(10);
         String [] accts = changeAccounts.getText().split(" ");
         for(int i=0; i<accts.length; i++) if(!accts[i].trim().equals("")) toAccounts.add(accts[i].trim());

         ResultSet rs = DBConnectionThread.getThread("edge").executeQuery(
                 "SELECT id,accounts FROM edge.role WHERE cpuid="+((Cpu) cpuID.getSelectedItem()).getID());
         while(rs.next()) {
           if(rs.getInt("ID") == ((Role) role.getSelectedItem()).getID()) continue; // If this is the current role, skip it
           accts = rs.getString("accounts").split(" ");
           for(int i=0; i<accts.length; i++)
             if(!accts[i].trim().equals("")  ) toAccounts.add(accts[i].trim());
           rolesOnCpu.add(new Integer(rs.getInt("id")));
         }
         rs.close();
         for(int i=0; i<toAccounts.size()-1;i++) {
           for(int j=i+1; j<toAccounts.size(); j++) {
             if(toAccounts.get(i).equals(toAccounts.get(j))) {
               err.set(true);
               err.appendText("'"+toAccounts.get(i)+"' is a duplicate enabled account on this cpu.  Move an account");
               break;
             }
           }
           if(err.isSet()) break;
         }
       }
     }
     else {
       if(role != null && obj != null) {
         if(role.getSelectedIndex() == -1 && obj.isNew()) Util.prt("its a new one!");
       }
       else {
         err.set(true);
         err.appendText("role or cpu not valid");
         addUpdate.setEnabled(false);
       }
     }

   }
   catch(SQLException e) {}

    // No CHANGES : If we found an error, color up error box
   error.setText("");
   if(err.isSet()) {
      error.setText(err.getText());
      error.setBackground(UC.red);
      addUpdate.setEnabled(false);
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
    addUpdate.setText("Enter a Role");
    
    //USER: Clear all fields for this form, this sets "defaults" for new screen
    // description.setText("");
    description.setText("");
    ipadr.setText("");
    accounts.setText("");
    changeAccounts.setText("");
    cpuID.setSelectedIndex(-1);
    hasdata.setSelected(false);
    config.setText("");
    changeRole.setText("");
  }
 
  
  private Role newOne() {
      
    return new Role(0, ((String) role.getSelectedItem()).toUpperCase(), //, more
            "", "", 0, 0, ""
       );
  }
  
  private void oldOne(String p) throws SQLException {
    obj = new DBObject(DBConnectionThread.getThread(DBConnectionThread.getDBSchema()), DBConnectionThread.getDBSchema(), "role","role",p);

    if(obj.isNew()) {
      Util.prt("object is new"+p);
    }
    else {
      //USER: Here set all of the form fields to data from the DBObject
      //      role.setText(obj.getString("Role"));
      // Example : description.setText(obj.getString("description"));
      description.setText(obj.getString("description"));
      ipadr.setText(obj.getString("ipadr"));
      accounts.setText(obj.getString("accounts"));
      changeAccounts.setText(obj.getString("accounts"));
      CpuPanel.setJComboBoxToID(cpuID,obj.getInt("cpuID"));
//      cpuID.setSelectedItem(obj.getInt("cpuID"));
      if(obj.getInt("hasdata")==1 ) hasdata.setSelected(true);
      else hasdata.setSelected(false);
      origCpuID=obj.getInt("cpuID");
      
//      CpuPanel.setJComboBoxToID(cpuID, obj.getInt("cpuID"));  
      chkForm();
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
    role = getJComboBox();
    addUpdate = new javax.swing.JButton();
    ID = new javax.swing.JTextField();
    jLabel9 = new javax.swing.JLabel();
    error = new javax.swing.JTextField();
    lblDesc = new javax.swing.JLabel();
    jScrollPane1 = new javax.swing.JScrollPane();
    description = new javax.swing.JTextArea();
    lblIpadr = new javax.swing.JLabel();
    lblCPU = new javax.swing.JLabel();
    cpuID = CpuPanel.getJComboBox();
    hasdata = new javax.swing.JRadioButton();
    ipadr = new javax.swing.JTextField();
    labAccounts = new javax.swing.JLabel();
    accounts = new javax.swing.JTextField();
    delete = new javax.swing.JButton();
    rebuild = new javax.swing.JButton();
    jScrollPane2 = new javax.swing.JScrollPane();
    config = new javax.swing.JTextArea();
    labChange = new javax.swing.JLabel();
    changeAccounts = new javax.swing.JTextField();
    labConfig = new javax.swing.JLabel();
    labChangeRole = new javax.swing.JLabel();
    changeRole = new javax.swing.JTextField();

    setLayout(new java.awt.GridBagLayout());

    jLabel1.setText("Role : ");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    add(jLabel1, gridBagConstraints);

    role.setEditable(true);
    role.setMinimumSize(new java.awt.Dimension(150, 28));
    role.setPreferredSize(new java.awt.Dimension(150, 28));
    role.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        roleActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(role, gridBagConstraints);

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

    lblDesc.setText("Description : ");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 5;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    add(lblDesc, gridBagConstraints);

    jScrollPane1.setMinimumSize(new java.awt.Dimension(484, 120));
    jScrollPane1.setPreferredSize(new java.awt.Dimension(484, 120));

    description.setColumns(40);
    description.setRows(5);
    description.setPreferredSize(new java.awt.Dimension(480, 40));
    jScrollPane1.setViewportView(description);

    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 5;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(jScrollPane1, gridBagConstraints);

    lblIpadr.setText("IP Address : ");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 6;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    add(lblIpadr, gridBagConstraints);

    lblCPU.setText("Node:");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 7;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    add(lblCPU, gridBagConstraints);

    cpuID.setEditable(true);
    cpuID.setMinimumSize(new java.awt.Dimension(150, 28));
    cpuID.setPreferredSize(new java.awt.Dimension(150, 28));
    cpuID.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        cpuIDActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 7;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(cpuID, gridBagConstraints);

    hasdata.setText("Has Side Data ?");
    hasdata.setEnabled(false);
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 8;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(hasdata, gridBagConstraints);

    ipadr.setMinimumSize(new java.awt.Dimension(150, 28));
    ipadr.setPreferredSize(new java.awt.Dimension(150, 28));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 6;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(ipadr, gridBagConstraints);

    labAccounts.setText("Enable Accts (sp separated):");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 2;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    add(labAccounts, gridBagConstraints);

    accounts.setColumns(40);
    accounts.setMinimumSize(new java.awt.Dimension(150, 28));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 2;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(accounts, gridBagConstraints);

    delete.setText("Delete");
    delete.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        deleteActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 16;
    add(delete, gridBagConstraints);

    rebuild.setText("Rebuild");
    rebuild.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        rebuildActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 19;
    add(rebuild, gridBagConstraints);

    jScrollPane2.setMinimumSize(new java.awt.Dimension(550, 200));

    config.setColumns(70);
    config.setEditable(false);
    config.setFont(new java.awt.Font("Monospaced", 0, 12)); // NOI18N
    config.setRows(15);
    config.setMinimumSize(new java.awt.Dimension(490, 12));
    jScrollPane2.setViewportView(config);

    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 12;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(jScrollPane2, gridBagConstraints);

    labChange.setText("Change accounts to:");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 3;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    add(labChange, gridBagConstraints);

    changeAccounts.setColumns(40);
    changeAccounts.setMinimumSize(new java.awt.Dimension(150, 28));
    changeAccounts.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        changeAccountsActionPerformed(evt);
      }
    });
    changeAccounts.addFocusListener(new java.awt.event.FocusAdapter() {
      public void focusLost(java.awt.event.FocusEvent evt) {
        changeAccountsFocusLost(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 3;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(changeAccounts, gridBagConstraints);

    labConfig.setText("Current Config:");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 12;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    add(labConfig, gridBagConstraints);

    labChangeRole.setText("Change Role Name:");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 18;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_END;
    add(labChangeRole, gridBagConstraints);

    changeRole.setColumns(15);
    changeRole.setMinimumSize(new java.awt.Dimension(150, 28));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 18;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
    add(changeRole, gridBagConstraints);
  }// </editor-fold>//GEN-END:initComponents

  private void addUpdateActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_addUpdateActionPerformed
    // Add your handling code here:
    
    // Do not change
    if(initiating) return;
    if(chkForm()) return;
    try {
      String p = role.getSelectedItem().toString();
      //p = p.toUpperCase();                  // USER : drop this to allow Mixed case
      if(!obj.isNew()) obj.refreshRecord();        // Refresh the result set so the sets will not fail on stale one
      if(changeRole.getText().length() > 0) obj.setString("role", changeRole.getText());
      else obj.setString("role", p);
      
      // USER : Set all of the fields in data base.  obj.setInt("thefield", ivalue);
      obj.setString("description", description.getText());
      obj.setString("ipadr", stripadr);
      if(!accounts.getText().equals(changeAccounts.getText())) {
        obj.setString("accounts", changeAccounts.getText());
        // So we need to update the edgemomsetup so that this role changes accounts as documented
        // and that the names of the file also change.
        String [] oldAccounts = accounts.getText().replaceAll("  ", " ").replaceAll("  ", " ").split(" ");
        String [] newAccounts = changeAccounts.getText().replaceAll("  ", " ").replaceAll("  ", " ").split(" ");
        if(oldAccounts.length != newAccounts.length) return;
        String roleString = ((Role) role.getSelectedItem()).getRole();
        int roleID = ((Role) role.getSelectedItem()).getID();
        for(int i=0; i<oldAccounts.length; i++) {
          if(oldAccounts[i].equals(newAccounts[i])) continue;
          // change filename in edgefile to the new account
          int nfiles = DBConnectionThread.getThread("edge").executeUpdate(
                  "UPDATE edge.edgefile SET edge.edgefile.edgefile =REPLACE(edgefile,'"+
                          roleString+"."+oldAccounts[i]+"','"+roleString+"."+newAccounts[i]+"')" +
                  " WHERE edgefile regexp '"+roleString+"."+oldAccounts[i]+"$'");
          // change the account in all of edgemomsetup for this role from old account to new account
          int nlines= DBConnectionThread.getThread("edge").executeUpdate(
                  "UPDATE edge.edgemomsetup set account='"+newAccounts[i]+"' WHERE roleid="+roleID+" AND account='"+oldAccounts[i]+"'");
          Util.prt("Change of account for "+roleString+" from "+oldAccounts[i]+
                  " to "+newAccounts[i]+" changed "+nfiles+" edgefile records and "+nlines+" edgemom.setup records");
        }
      }
      else obj.setString("accounts", accounts.getText());
      int nowCpuID = (cpuID.getSelectedIndex() == -1) ? 0: ((Cpu) cpuID.getSelectedItem()).getID();
      obj.setInt("cpuID", nowCpuID);
      //if(hasdata.isSelected()) obj.setInt("hasdata",1);
      //else obj.setInt("hasdata",0);
      if(nowCpuID != origCpuID || !accounts.getText().equals(changeAccounts.getText())) obj.setInt("hasdata", 1);
      else obj.setInt("hasdata", 0);

      
      // Do not change
      obj.updateRecord();
      if(origCpuID != nowCpuID) {
        DBConnectionThread dbconn = DBConnectionThread.getThread("edge");
        if(origCpuID > 0) dbconn.executeUpdate("UPDATE edge.cpu set hasdata=1 where ID="+origCpuID);
        if(nowCpuID > 0) dbconn.executeUpdate("UPDATE edge.cpu set hasdata=1 where ID="+nowCpuID);
      }
      Role.roles=null;       // force reload of combo box
      getJComboBox(role);
      clearScreen();
      role.setSelectedIndex(-1);
    }  catch  (SQLException  E)
    { Util.SQLErrorPrint(E,"role: update failed partno="+role.getSelectedItem());
      error.setText("Could Not Update File!");
      error.setBackground(UC.red);
      addUpdate.setEnabled(true);
    }      
  }//GEN-LAST:event_addUpdateActionPerformed

  private void roleActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_roleActionPerformed
    // Add your handling code here:
   find();
   updateConfig();
 
}//GEN-LAST:event_roleActionPerformed

private void deleteActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_deleteActionPerformed
// TODO add your handling code here:
  if(role.getSelectedIndex() != -1) {
    try {
      obj.deleteRecord();
      Util.prt("DELETE item = "+role.getSelectedItem());
      role.removeItemAt(role.getSelectedIndex());
    }
    catch(SQLException e) {
      Util.SQLErrorPrint(e, "Trying to delete a role");

    }
  }
}//GEN-LAST:event_deleteActionPerformed

private void rebuildActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_rebuildActionPerformed
  CpuPanel.getJComboBox(cpuID);
  clearScreen();
}//GEN-LAST:event_rebuildActionPerformed

private void cpuIDActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_cpuIDActionPerformed
  chkForm();
}//GEN-LAST:event_cpuIDActionPerformed

private void changeAccountsActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_changeAccountsActionPerformed
  chkForm();
}//GEN-LAST:event_changeAccountsActionPerformed

private void changeAccountsFocusLost(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_changeAccountsFocusLost
  chkForm();
}//GEN-LAST:event_changeAccountsFocusLost
  private StringBuilder conf = new StringBuilder(100);
  private void updateConfig() {
    config.setText("");
    if(conf.length() >0) conf.delete(0,conf.length());
    conf.append("Role     Assigned     Configurations exist in these accounts\n");
    try {
      ResultSet rs = DBConnectionThread.getThread(DBConnectionThread.getDBSchema()).executeQuery(
              "SELECT DISTINCT role,account,cpuid FROM edge.edgemomsetup,role WHERE role.id=roleid order by role,account");
      String lastRole="";
      while(rs.next()) {
        if(!lastRole.equals(rs.getString("role"))) {
          Cpu cpu = CpuPanel.getCpuWithID(rs.getInt("cpuid"));
          conf.append(lastRole.equals("")? "":"\n").append(Util.rightPad(rs.getString("role"), 8)).append(" on ").append(Util.rightPad( (cpu == null? "null" : cpu.getCpu()), 8)).append(" : ");
        }
        lastRole = rs.getString("role");
        conf.append(Util.rightPad(rs.getString("account"), 6));
      }
      config.setText(conf.toString());
    }
    catch(SQLException e) {}

  }
  
  // Variables declaration - do not modify//GEN-BEGIN:variables
  private javax.swing.JTextField ID;
  private javax.swing.JTextField accounts;
  private javax.swing.JButton addUpdate;
  private javax.swing.JTextField changeAccounts;
  private javax.swing.JTextField changeRole;
  private javax.swing.JTextArea config;
  private javax.swing.JComboBox cpuID;
  private javax.swing.JButton delete;
  private javax.swing.JTextArea description;
  private javax.swing.JTextField error;
  private java.awt.GridBagLayout gridBagLayout1;
  private javax.swing.JRadioButton hasdata;
  private javax.swing.JTextField ipadr;
  private javax.swing.JLabel jLabel1;
  private javax.swing.JLabel jLabel9;
  private javax.swing.JScrollPane jScrollPane1;
  private javax.swing.JScrollPane jScrollPane2;
  private javax.swing.JLabel labAccounts;
  private javax.swing.JLabel labChange;
  private javax.swing.JLabel labChangeRole;
  private javax.swing.JLabel labConfig;
  private javax.swing.JLabel lblCPU;
  private javax.swing.JLabel lblDesc;
  private javax.swing.JLabel lblIpadr;
  private javax.swing.JButton rebuild;
  private javax.swing.JComboBox role;
  // End of variables declaration//GEN-END:variables
  /** Creates new form RolePanel. */
  public RolePanel() {
    initiating=true;
    initComponents();
    getJComboBox(role);                // set up the key JComboBox
    role.setSelectedIndex(-1);    // Set selected type
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
    makeRoles();
    for (int i=0; i< Role.roles.size(); i++) {
      b.addItem( Role.roles.get(i));
    }
    b.setMaximumRowCount(30);
  }
  
  /** Given a JComboBox from getJComboBox, set selected item to match database ID
   *@param b The JComboBox
   *@param ID the row ID from the database to select
   */
  public static void setJComboBoxToID(JComboBox b, int ID) {
    b.setSelectedIndex(-1);
    //Util.prt("RolePanel.setJComboBoxToID id="+ID);
    for(int i=0; i<b.getItemCount(); i++) 
      if(((Role) b.getItemAt(i)).getID() == ID) {
          b.setSelectedIndex(i);
          //Util.prt("RolePanel.setJComboBoxToID id="+ID+" found at "+i);
      }
  }  
  
  /** Given a database ID find the index into a JComboBox consistent with this panel
   *@param ID The row ID from the database
   *@return The index in the JComboBox with this ID
   */
  public static int getJComboBoxIndex(int ID) {
    if(Role.roles == null) makeRoles();
    for(int i=0; i<Role.roles.size(); i++) if( ID == Role.roles.get(i).getID()) return i;
    return -1;
  }
  /** Cause the main JComboBox to be reloaded. */
  public static void reload() {
    Role.roles = null;
    makeRoles();
  }
    /** return a vector with all of the Role
   * @return The vector with the role
   */
  public static ArrayList<Role> getRoleVector() {
    if(Role.roles == null) makeRoles();
    return Role.roles;
  }
  /** Get the item corresponding to database ID
   *@param ID the database Row ID
   *@return The Role row with this ID
   */
  public static Role getRoleWithID(int ID) {
    if(Role.roles == null) makeRoles();
    int i=getJComboBoxIndex(ID);
    if(i >= 0) return  Role.roles.get(i);
    else return null;
  }
  
  // This routine should only need tweeking if key field is not same as table name
  private static void makeRoles() {
    Role.makeRoles();
 
  }
  
  // No changes needed
  private void find() {
    if(role == null) return;
    if(initiating) return;
    Role l;
    if(role.getSelectedIndex() == -1) {
      if(role.getSelectedItem() == null) return;
      l = newOne();
    } 
    else {
      l = (Role) role.getSelectedItem();
    }
      
    if(l == null) return;
    String p = l.getRole();
    if(p == null) return;
    if(p.equals("")) {
      addUpdate.setEnabled(false);
      addUpdate.setText("Enter a Role!");
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
        if(err.isSet()) addUpdate.setEnabled(false);
        else addUpdate.setEnabled(true);
        ID.setText(""+obj.getInt("ID"));    // Info only, show ID
      }
    }  
    // This is only thrown if something happens in oldone() new DBOject
    // Other than it being a new record.
    catch  (SQLException  E)                                 // 
    { Util.SQLErrorPrint(E,"Role: SQL error getting Role="+p);
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
      Show.inFrame(new RolePanel(), UC.XSIZE, UC.YSIZE);
    }
    catch(InstantiationException e) {}
  }
}
