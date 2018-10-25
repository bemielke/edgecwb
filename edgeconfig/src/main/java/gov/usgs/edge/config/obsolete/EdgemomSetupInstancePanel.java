/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */

package gov.usgs.edge.config.obsolete;

//package gov.usgs.edge.template;
import gov.usgs.edge.config.*;
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
 * JComboBox variable this variable is called "edgemomsetup" in the initial form
 * The table name and key name should match and start lower case (edgemomsetup).
 * The Class here will be that same name but with upper case at beginning(EdgemomSetupInstance).
 <br> 1)  Rename the location JComboBox to the "key" (edgemomsetup) value.  This should start Lower
 *      case.
 *<br>  2)  The table name should match this key name and be all lower case.
 *<br>  3)  Change all EdgemomSetupInstance to ClassName of underlying data (case sensitive!)
<br>  4)  Change all edgemomsetup key value (the JComboBox above)
 *<br>  5)  clearScreen should update swing components to new defaults
 *<br>  6)  chkForm should check all field for validity.  The errorTrack variable is
 *      used to track multiple errors.  In FUtil.chk* handle checks of the various
 *      variable types.  Date, double, and IP etc return the "canonical" form for 
 *      the variable.  These should be stored in a class variable so the latest one
 *      from "chkForm()" are available to the update writer.
 *<br>  7) newone() should be updated to create a new instance of the underlying data class.
 *<br>  8) oldone() get data from database and update all swing elements to correct values
 *<br>  9) addUpdateAction() add data base set*() operations updating all fields in the DB
 *<br> 10) EdgemomSetupInstancePanel() constructor - good place to change backgrounds using UC.Look() any
    other weird startup stuff.

<br>  local variable error - Must be a JTextField for posting error communications
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
public class EdgemomSetupInstancePanel extends javax.swing.JPanel {
  
  // NOTE : here define all variables general.  "Vector v" is used for main Comboboz
  static ArrayList<EdgemomSetupInstance> v;             // Vector containing objects of this EdgemomSetupInstance Type
  DBObject obj;
  ErrorTrack err=new ErrorTrack();
  boolean initiating=false;
  boolean building=false;
  PopupForm popup = new PopupForm(this);
   //USER: Here are the local variables
  int intpriority;
  

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
   Util.prta("chkForm Edgemomsetup");

   intpriority = FUtil.chkInt(priority,err,true);

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
    edgemomsetup.setSelectedIndex(-1);
    tag.setText("");
    priority.setText("");
    edgethreadID.setSelectedIndex(-1);
    args.setText("");
    logfile.setText("");
    comment.setText("");
    UC.Look(error);
    file2.setText("");
    file.setSelectedIndex(-1);
    error.setText("");
    disable.setSelected(false);
    addUpdate.setEnabled(false);
    addUpdate.setText("Enter a Edgemomsetup");
    //USER: Clear all fields for this form, this sets "defaults" for new screen
    // description.setText("");
  }
 
  
  private EdgemomSetupInstance newOne() {
      
    return new EdgemomSetupInstance(0, "DUMMY", "", //, more
            0, "", 0, 0, "", "", "", "", 0
            );
  }
  
  private void oldOne(String p) throws SQLException {
    obj = new DBObject(DBConnectionThread.getThread(DBConnectionThread.getDBSchema()), DBConnectionThread.getDBSchema(), 
            "edgemomsetup","ID",p);

    if(obj.isNew()) {
      Util.prt("object is new"+p);
    }
    else {
      //USER: Here set all of the form fields to data from the DBObject
      //      edgemomsetup.setText(obj.getString("EdgemomSetupInstance"));
      // Example : description.setText(obj.getString("description"));
      edgemomsetup.setSelectedItem(tag);
      tag.setText(obj.getString("tag"));
//      ID.setText(""+obj.getInt("ID"));
      int r = obj.getInt("roleID");
      if (r==0) roleID.setSelectedIndex(-1);
      else RoleInstancePanel.setJComboBoxToID(roleID, r);
      priority.setText(""+obj.getInt("priority"));
      int e = obj.getInt("edgethreadID");
      if (e==0) edgethreadID.setSelectedIndex(-1);
      else EdgethreadPanel.setJComboBoxToID(edgethreadID, e);
      args.setText(obj.getString("args"));
      disable.setSelected(obj.getInt("disabled") != 0);
      logfile.setText(obj.getString("logfile"));
      comment.setText(obj.getString("comment"));
      file2.setText(obj.getString("filename"));
      if(obj.getInt("edgefileid") >= 0) EdgeFilePanel.setJComboBoxToID(file, obj.getInt("edgefileid"));
      else file.setSelectedIndex(-1);
      RoleInstancePanel.setJComboBoxToID(changeRole, obj.getInt("roleid"));
      changeAccount.setSelectedItem(obj.getString("account"));
        
        
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
        lblTag = new javax.swing.JLabel();
        addUpdate = new javax.swing.JButton();
        ID = new javax.swing.JTextField();
        jLabel9 = new javax.swing.JLabel();
        error = new javax.swing.JTextField();
        lblPriority = new javax.swing.JLabel();
        priority = new javax.swing.JTextField();
        lblRoleID = new javax.swing.JLabel();
        roleID = RoleInstancePanel.getJComboBox();
        lblEdgeThreadID = new javax.swing.JLabel();
        edgethreadID = gov.usgs.edge.config.EdgethreadPanel.getJComboBox();
        lblArgs = new javax.swing.JLabel();
        lblLogfile = new javax.swing.JLabel();
        logfile = new javax.swing.JTextField();
        lblComment = new javax.swing.JLabel();
        comment = new javax.swing.JTextField();
        lblEdgemomsetup = new javax.swing.JLabel();
        edgemomsetup = new javax.swing.JComboBox();
        tag = new javax.swing.JTextField();
        jLabel1 = new javax.swing.JLabel();
        account = new javax.swing.JComboBox();
        changeRole = RoleInstancePanel.getJComboBox();
        changeAccount = new javax.swing.JComboBox();
        labChangeAccount = new javax.swing.JLabel();
        labChangeRole = new javax.swing.JLabel();
        labFile = new javax.swing.JLabel();
        file2 = new javax.swing.JTextField();
        jScrollPane1 = new javax.swing.JScrollPane();
        args = new javax.swing.JTextArea();
        cloneFrom = cloneFrom = EdgemomSetupInstancePanel.getJComboBox();
        for(int i=cloneFrom.getItemCount()-1; i>0; i--) {
          EdgemomSetupInstance t = (EdgemomSetupInstance) cloneFrom.getItemAt(i);
          for(int j = i-1; j>=0; j--) {
            EdgemomSetupInstance t2 = (EdgemomSetupInstance) cloneFrom.getItemAt(j);
            if(t2.getTag().equalsIgnoreCase(t.getTag()) && t2.getEdgethreadID() == t.getEdgethreadID() && t2.getArgs().equals(t.getArgs()) ) {
              //Util.prt(" Clone remove item="+i+" size="+cloneFrom.getItemCount());
              cloneFrom.removeItemAt(i);
              break;
            }
          }
        }
        clone = new javax.swing.JButton();
        displayHelp = new javax.swing.JRadioButton();
        disable = new javax.swing.JRadioButton();
        delete = new javax.swing.JButton();
        file = EdgeFilePanel.getJComboBox();
        EdgeFilePanel.setJComboConfigOnly(file);

        setLayout(new java.awt.GridBagLayout());

        lblTag.setText("Tag :");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 12;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
        add(lblTag, gridBagConstraints);

        addUpdate.setText("Add/Update");
        addUpdate.setMaximumSize(new java.awt.Dimension(300, 29));
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
        ID.setMinimumSize(new java.awt.Dimension(50, 28));
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
        gridBagConstraints.gridy = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        add(error, gridBagConstraints);

        lblPriority.setText("Priority : ");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 5;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
        add(lblPriority, gridBagConstraints);

        priority.setMinimumSize(new java.awt.Dimension(150, 27));
        priority.setPreferredSize(new java.awt.Dimension(150, 27));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 5;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        add(priority, gridBagConstraints);

        lblRoleID.setText("Select Role : ");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
        add(lblRoleID, gridBagConstraints);

        roleID.setMinimumSize(new java.awt.Dimension(150, 27));
        roleID.setPreferredSize(new java.awt.Dimension(150, 27));
        roleID.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                roleIDActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        add(roleID, gridBagConstraints);

        lblEdgeThreadID.setText("EdgeThread : ");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 6;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
        add(lblEdgeThreadID, gridBagConstraints);

        edgethreadID.setMinimumSize(new java.awt.Dimension(300, 27));
        edgethreadID.setPreferredSize(new java.awt.Dimension(300, 27));
        edgethreadID.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                edgethreadIDActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 6;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        add(edgethreadID, gridBagConstraints);

        lblArgs.setText("Arguments : ");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 7;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
        add(lblArgs, gridBagConstraints);

        lblLogfile.setText("Log File : ");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 8;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
        add(lblLogfile, gridBagConstraints);

        logfile.setMinimumSize(new java.awt.Dimension(250, 27));
        logfile.setPreferredSize(new java.awt.Dimension(250, 27));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 8;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        add(logfile, gridBagConstraints);

        lblComment.setText("Comment : ");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 9;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
        add(lblComment, gridBagConstraints);

        comment.setColumns(200);
        comment.setMinimumSize(new java.awt.Dimension(400, 27));
        comment.setPreferredSize(new java.awt.Dimension(400, 27));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 9;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        add(comment, gridBagConstraints);

        lblEdgemomsetup.setText("Select tag or enter new tag : ");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
        add(lblEdgemomsetup, gridBagConstraints);

        edgemomsetup.setEditable(true);
        edgemomsetup.setMinimumSize(new java.awt.Dimension(500, 27));
        edgemomsetup.setPreferredSize(new java.awt.Dimension(500, 27));
        edgemomsetup.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                edgemomsetupActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        add(edgemomsetup, gridBagConstraints);

        tag.setMinimumSize(new java.awt.Dimension(150, 27));
        tag.setPreferredSize(new java.awt.Dimension(150, 27));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 12;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        add(tag, gridBagConstraints);

        jLabel1.setText("Select Account :");
        jLabel1.setPreferredSize(new java.awt.Dimension(218, 22));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
        add(jLabel1, gridBagConstraints);

        account.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "vdl", "vdl1", "vdl2", "vdl3", "vdl4", "reftek" }));
        account.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                accountActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        add(account, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 14;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        add(changeRole, gridBagConstraints);

        changeAccount.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "vdl", "vdl1", "vdl2", "vdl3", "vdl4", "reftek" }));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 13;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        add(changeAccount, gridBagConstraints);

        labChangeAccount.setText("Move to Acccount:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 13;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
        add(labChangeAccount, gridBagConstraints);

        labChangeRole.setText("Move to Role :");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 14;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
        add(labChangeRole, gridBagConstraints);

        labFile.setText("External Config Filename :");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 10;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
        add(labFile, gridBagConstraints);

        file2.setColumns(40);
        file2.setMinimumSize(new java.awt.Dimension(494, 28));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 20;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        add(file2, gridBagConstraints);

        jScrollPane1.setMinimumSize(new java.awt.Dimension(500, 75));

        args.setColumns(80);
        args.setLineWrap(true);
        args.setRows(5);
        args.setToolTipText("Enter the arguments from the javadocs");
        jScrollPane1.setViewportView(args);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 7;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        add(jScrollPane1, gridBagConstraints);

        cloneFrom.setMinimumSize(new java.awt.Dimension(500, 27));
        cloneFrom.setPreferredSize(new java.awt.Dimension(500, 27));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 16;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        add(cloneFrom, gridBagConstraints);

        clone.setText("Clone From ");
        clone.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                cloneActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 16;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
        add(clone, gridBagConstraints);

        displayHelp.setText("Display Help");
        displayHelp.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                displayHelpActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 18;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        add(displayHelp, gridBagConstraints);

        disable.setText("Disable Thread");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 11;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        add(disable, gridBagConstraints);

        delete.setText("Delete Item");
        delete.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                deleteActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 19;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        add(delete, gridBagConstraints);

        file.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusGained(java.awt.event.FocusEvent evt) {
                fileFocusGained(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 10;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        add(file, gridBagConstraints);
    }// </editor-fold>//GEN-END:initComponents

  private void addUpdateActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_addUpdateActionPerformed
    // Add your handling code here:
    
    // Do not change
    if(initiating) return;
    if(chkForm()) return;
    try {
      String p = tag.getText().toString();
      //p = p.toUpperCase();                  // USER : drop this to allow Mixed case
      if(!obj.isNew()) obj.refreshRecord();        // Refresh the result set so the sets will not fail on stale one
      obj.setString("tag",p);

      
      // USER : Set all of the fields in data base.  obj.setInt("thefield", ivalue);
      obj.setInt("roleID",  ((RoleInstance) roleID.getSelectedItem()).getID());
      obj.setInt("priority",  intpriority);
      obj.setString("args", args.getText());
      obj.setInt("disabled", (disable.isSelected()? 1 : 0));
      obj.setString("logfile", logfile.getText());
      obj.setString("comment", comment.getText());
//      obj.setString("ipadr", stripadr);
      obj.setInt("edgethreadID",  ((Edgethread) edgethreadID.getSelectedItem()).getID());
      obj.setString("filename", file2.getText());
      obj.setInt("edgefileid", (file.getSelectedIndex() == -1? 0: ((EdgeFile) file.getSelectedItem()).getID()));
      obj.setString("account", changeAccount.getSelectedItem().toString());
      obj.setInt("roleid", ((RoleInstance)changeRole.getSelectedItem()).getID());
  
      
      // Do not change
      obj.updateRecord();
      
      // If we update something, we need to mark role as having changes so reconfiguration takes place
      Util.prt("edge.prop update role="+roleID.getSelectedItem()+" account="+account.getSelectedItem());
      try {
        DBConnectionThread.getThread("edge").executeUpdate("UPDATE edge.role set hasdata=1 where ID="+((RoleInstance) roleID.getSelectedItem()).getID());
      }
      catch(SQLException e) {
        Util.prt("Could not set hasdata for role="+roleID.getSelectedItem()+" e="+e);
      }
      v=null;       // force reload of combo box
      EdgemomSetupInstancePanel.getJComboBox(cloneFrom);
      buildEdgemomSetupCombo("Add/Update");
      clearScreen();
      tag.setText("");
    }  catch  (SQLException  E)
    { Util.SQLErrorPrint(E,"edgemomsetup: update failed partno="+edgemomsetup.getSelectedItem());
      error.setText("Could Not Update File!");
      error.setBackground(UC.red);
      addUpdate.setEnabled(true);
    }      
  }//GEN-LAST:event_addUpdateActionPerformed

  private void roleIDActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_roleIDActionPerformed
    // TODO add your handling code here:

    buildEdgemomSetupCombo("role");
    if(edgemomsetup.getItemCount() >= 1) edgemomsetup.setSelectedIndex(0); 
  }//GEN-LAST:event_roleIDActionPerformed

  private void edgemomsetupActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_edgemomsetupActionPerformed
    // TODO add your handling code here:
    if(!building) find(false);
}//GEN-LAST:event_edgemomsetupActionPerformed

private void accountActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_accountActionPerformed
// TODO add your handling code here:
  buildEdgemomSetupCombo("account");
  if(edgemomsetup.getItemCount() >= 1) edgemomsetup.setSelectedIndex(0);
}//GEN-LAST:event_accountActionPerformed

private void cloneActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_cloneActionPerformed
// TODO add your handling code here:
  if(cloneFrom.getSelectedIndex() != -1) {
    edgemomsetup.setSelectedIndex(-1);      // force a new one
    find(true);
    EdgemomSetupInstance setup = (EdgemomSetupInstance) cloneFrom.getSelectedItem();
    tag.setText(setup.getTag());
    args.setText(setup.getArgs());
    disable.setSelected(false);
    priority.setText(""+setup.getPriority());
    EdgethreadPanel.setJComboBoxToID(edgethreadID, setup.getEdgethreadID());
    logfile.setText(setup.getLogfile());
    comment.setText(setup.getComment());
    file2.setText(setup.getFilename());
    file.setSelectedIndex(-1);
    changeAccount.setSelectedIndex(account.getSelectedIndex());
    RoleInstancePanel.setJComboBoxToID(changeRole, ((RoleInstance)roleID.getSelectedItem()).getID());
  }
}//GEN-LAST:event_cloneActionPerformed

private void displayHelpActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_displayHelpActionPerformed
    if (displayHelp.isSelected()) popup.displayForm(true);
    else popup.displayForm(false);
}//GEN-LAST:event_displayHelpActionPerformed

private void edgethreadIDActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_edgethreadIDActionPerformed
// TODO add your handling code here:
  if(edgethreadID.getSelectedIndex() != -1) {
    popup.setText( ((Edgethread) edgethreadID.getSelectedItem()).getHelp());
  }
}//GEN-LAST:event_edgethreadIDActionPerformed

private void deleteActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_deleteActionPerformed
  // Delete the currently selected item.
  try {
    obj.deleteRecord();
    buildEdgemomSetupCombo("delete");
    Util.prt("Objected deleted obj="+obj);
  }
  catch(SQLException e) {
    Util.prt("Attempt to delete record failed!"+e);
  }
}//GEN-LAST:event_deleteActionPerformed

private void fileFocusGained(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_fileFocusGained
  EdgeFilePanel.getJComboBox(file);
  EdgeFilePanel.setJComboConfigOnly(file);
}//GEN-LAST:event_fileFocusGained
private synchronized void  buildEdgemomSetupCombo(String where) {
  v = null;
  building=true;
  makeEdgemomsetups();
  edgemomsetup.removeAllItems();
  if(roleID.getSelectedIndex() == -1 || account.getSelectedIndex() == -1) return; // must have an account and role
  int count=0;
  int start = edgemomsetup.getItemCount();
  for(int i=0; i<v.size(); i++) {
    EdgemomSetupInstance setup = (EdgemomSetupInstance) v.get(i);
    if(setup.getRoleID() == ((RoleInstance) roleID.getSelectedItem()).getID() &&
            setup.getAccount().equalsIgnoreCase( (String) account.getSelectedItem())) {
      edgemomsetup.addItem(setup);
      count++;
    }
  }
  building=false;
}
  
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JTextField ID;
    private javax.swing.JComboBox account;
    private javax.swing.JButton addUpdate;
    private javax.swing.JTextArea args;
    private javax.swing.JComboBox changeAccount;
    private javax.swing.JComboBox changeRole;
    private javax.swing.JButton clone;
    private javax.swing.JComboBox cloneFrom;
    private javax.swing.JTextField comment;
    private javax.swing.JButton delete;
    private javax.swing.JRadioButton disable;
    private javax.swing.JRadioButton displayHelp;
    private javax.swing.JComboBox edgemomsetup;
    private javax.swing.JComboBox edgethreadID;
    private javax.swing.JTextField error;
    private javax.swing.JComboBox file;
    private javax.swing.JTextField file2;
    private java.awt.GridBagLayout gridBagLayout1;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel9;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JLabel labChangeAccount;
    private javax.swing.JLabel labChangeRole;
    private javax.swing.JLabel labFile;
    private javax.swing.JLabel lblArgs;
    private javax.swing.JLabel lblComment;
    private javax.swing.JLabel lblEdgeThreadID;
    private javax.swing.JLabel lblEdgemomsetup;
    private javax.swing.JLabel lblLogfile;
    private javax.swing.JLabel lblPriority;
    private javax.swing.JLabel lblRoleID;
    private javax.swing.JLabel lblTag;
    private javax.swing.JTextField logfile;
    private javax.swing.JTextField priority;
    private javax.swing.JComboBox roleID;
    private javax.swing.JTextField tag;
    // End of variables declaration//GEN-END:variables
  /** Creates new form EdgemomsetupPanel */
  public EdgemomSetupInstancePanel() {
    initiating=true;
    initComponents();
    getJComboBox(edgemomsetup);                // set up the key JComboBox
    edgemomsetup.setSelectedIndex(-1);    // Set selected type
    Look();                    // Set color background
    initiating=false;
    Util.addDefaultProperty("popupX","10");
    Util.addDefaultProperty("popupY","50");
    popup.setContentHtml(true);
    popup.setLocation((int) Double.parseDouble(Util.getProperty("popupX")),
                      (int) Double.parseDouble(Util.getProperty("popupY")));
    if(roleID.getItemCount() > 0) roleID.setSelectedIndex(0);

  
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
    makeEdgemomsetups();
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
    //Util.prt("EdgemomSetupInstancePanel.setJComboBoxToID id="+ID);
    for(int i=0; i<b.getItemCount(); i++) { 
      if(((EdgemomSetupInstance) b.getItemAt(i)).getID() == ID) {
          b.setSelectedIndex(i);
          //Util.prt("EdgemomSetupInstancePanel.setJComboBoxToID id="+ID+" found at "+i);
      }
    }
  }  
  
  /** Given a database ID find the index into a JComboBox consistent with this panel
   *@param ID The row ID from the database
   *@return The index in the JComboBox with this ID
   */
  public static int getJComboBoxIndex(int ID) {
    if(v == null) makeEdgemomsetups();
    for(int i=0; i<v.size(); i++) {
      if( ID == ((EdgemomSetupInstance) v.get(i)).getID()) return i;
    }
    return -1;
  }
  /** Cause the main JComboBox to be reloaded*/
  public static void reload() {
    v = null;
    makeEdgemomsetups();
  }
    /* return a vector with all of the EdgemomSetupInstance
   * @return The vector with the edgemomsetup
   */
  public static ArrayList getEdgemomsetupVector() {
    if(v == null) makeEdgemomsetups();
    return v;
  }
  /** Get the item corresponding to database ID
   *@param ID the database Row ID
   *@return The EdgemomSetupInstance row with this ID
   */
  public static EdgemomSetupInstance getEdgemomsetupWithID(int ID) {
    if(v == null) makeEdgemomsetups();
    int i=getJComboBoxIndex(ID);
    if(i >= 0) return (EdgemomSetupInstance) v.get(i);
    else return null;
  }
  
  // This routine should only need tweeking if key field is not same as table name
  private static void makeEdgemomsetups() {
    if (v != null) return;
    v=new ArrayList<EdgemomSetupInstance>(100);
    try {
      Statement stmt = DBConnectionThread.getConnection(DBConnectionThread.getDBSchema()).createStatement();   // used for query
      String s = "SELECT * FROM edge.edgemomsetup,role where role.id=edgemomsetup.roleid ORDER BY tag,role,account;";
      ResultSet rs = stmt.executeQuery(s);
      while (rs.next()) {
        EdgemomSetupInstance loc = new EdgemomSetupInstance(rs);
//        Util.prt("MakeEdgemomsetup() i="+v.size()+" is "+loc.getEdgemomsetup());
        v.add(loc);
      }
      rs.close();
      stmt.close();
    }
    catch (SQLException e) {
      Util.SQLErrorPrint(e,"makeEdgemomsetups() on table SQL failed");
    }    
  }
  
  // No changes needed
  private void find(boolean forceNew) {
    if(edgemomsetup == null) return;
    if(initiating) return;
    String tagtemp="";
    EdgemomSetupInstance l;
    if(edgemomsetup.getSelectedIndex() == -1) {
      if(edgemomsetup.getSelectedItem() == null && !forceNew) return;
      l = newOne();
      tagtemp = ""+edgemomsetup.getSelectedItem();
      
    } 
    else {
      l = (EdgemomSetupInstance) edgemomsetup.getSelectedItem();
    }
      
    if(l == null) return;
    String p = l.getEdgemomsetup();
    if(p == null) return;
    if(p.equals("")) {
      addUpdate.setEnabled(false);
      addUpdate.setText("Enter a Edgemomsetup!");
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
          changeRole.setSelectedItem(roleID.getSelectedItem());
          changeAccount.setSelectedItem(account.getSelectedItem());
          priority.requestFocus();
          tag.setText(tagtemp);
          addUpdate.setText("Add "+changeAccount.getSelectedItem()+"-"+changeRole.getSelectedItem()+"-"+tag.getText());
          addUpdate.setEnabled(true);
      }
      else {
        setJComboBoxToID(edgemomsetup, obj.getInt("ID"));
       
        addUpdate.setText("Update "+((EdgemomSetupInstance) edgemomsetup.getSelectedItem()).getTag());
        addUpdate.setEnabled(true);
        ID.setText(""+obj.getInt("ID"));    // Info only, show ID
      }
    }  
    // This is only thrown if something happens in oldone() new DBOject
    // Other than it being a new record.
    catch  (SQLException  E)                                 // 
    { Util.SQLErrorPrint(E,"Edgemomsetup: SQL error getting Edgemomsetup="+p);
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
      Show.inFrame(new EdgemomSetupInstancePanel(), UC.XSIZE, UC.YSIZE);
    }
    catch(InstantiationException e) {}
  }
}
