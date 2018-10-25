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
import static gov.usgs.anss.edgeoutput.RingFile.MAX_SEQ;
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
import javax.swing.JOptionPane;
/**
 *
 * requirements : 
 * The key for the file must be the name of the table and of the
 * JComboBox variable this variable is called "sendto" in the initial form
 * The table name and key name should match and start lower case (sendto).
 * The Class here will be that same name but with upper case at beginning(Sendto).
 * <br> 1)  Rename the location JComboBox to the "key" (sendto) value.  This should start Lower
 *      case.
 *<br>  2)  The table name should match this key name and be all lower case.
 *<br>  3)  Change all Sendto to ClassName of underlying data (case sensitive!)
 *<br>  4)  Change all sendto key value (the JComboBox above)
 *<br>  5)  clearScreen should update swing components to new defaults
 *<br>  6)  chkForm should check all field for validity.  The errorTrack variable is
 *      used to track multiple errors.  In FUtil.chk* handle checks of the various
 *      variable types.  Date, double, and IP etc return the "canonical" form for 
 *      the variable.  These should be stored in a class variable so the latest one
 *      from "chkForm()" are available to the update writer.
 *<br>  7) newone() should be updated to create a new instance of the underlying data class.
 *<br>  8) oldone() get data from database and update all swing elements to correct values
 *<br>  9) addUpdateAction() add data base set*() operations updating all fields in the DB
 *<br> 10) SendtoPanel() constructor - good place to change backgrounds using UC.Look() any
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
public class SendtoPanel extends javax.swing.JPanel {
  
  // NOTE : here define all variables general.  "Vector v" is used for main Comboboz
  static ArrayList<Sendto> v;             // Vector containing objects of this Sendto Type
  DBObject obj;
  ErrorTrack err=new ErrorTrack();
  boolean initiating=false;
  int filesizeMB;
  int updms;
  
  
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
   filesizeMB=FUtil.chkInt(filesize,err,0,100000);
   if(filesizeMB > 0) {
    if(MAX_SEQ % filesizeMB*1000 != 0) {      // User has picked a bad file sizeMB - override it
      err.set();
      err.appendText("Size in MB must divide evenly into 1,000,000");
    }
   }
   if(!filepath.getText().equals("") ) {
		 if(Util.getOS().contains("indow")) {
			 Util.prt("windows : accept all paths");
		 }
		 else {
			if(!filepath.getText().substring(0,1).equals("/")) {
				err.set(); err.appendText("Path must start with '/'");
				filepath.setBackground(UC.red);
			}
			else {filepath.setBackground(UC.white);}
		}
	 }
   updms = FUtil.chkInt(updateMS, err, 0, 99999999);
   
   // Insure class is on the acceptable list!
   if(!className.getText().equals("") &&
      className.getText().indexOf("gov.usgs.anss.edgeoutput.") != 0  &&
           !className.getText().equalsIgnoreCase("RingServerSeedLink")) {
     err.set(); err.appendText("Class Not an EdgeOutputer "); 
     className.setBackground(UC.red);
   }
   else {className.setBackground(UC.white);}
    if(obj != null) 
      if( obj.getMaxID() >= 64) {
        Util.prt("Too many for a bit map field !!!");
        err.appendText("Too many for a bit map"); err.set();
      } 

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
    UC.Look(allowRestricted);
    UC.Look(hasData);
    addUpdate.setEnabled(false);
    addUpdate.setText("Enter a Sendto");
    
    //USER: Clear all fields for this form, this sets "defaults" for new screen
    description.setText("");
    hasData.setSelected(false);
    allowRestricted.setSelected(false);
    filesize.setText("");
    nodes.setText("");
    autoset.setText("");
    filepath.setText("");
    className.setText("");
    updateMS.setText("");
    args.setText("");
    newSendto.setText("");
  }
 
  
  private Sendto newOne() {
    // USER: add all fields needed for a Sendto
    return new Sendto(0, ((String) sendto.getSelectedItem()),
        description.getText(), 0, 0, "", 0, "", "", 0,"",""//, more
       );
  }
  
  private void oldOne(String p) throws SQLException {
    obj = new DBObject(DBConnectionThread.getThread(DBConnectionThread.getDBSchema()), DBConnectionThread.getDBSchema(), "sendto","sendto",p);

    if(obj.isNew()) {
      Util.prt("object is new"+p);
    }
    else {
      // Here set all of the form fields to data from the DBObject
      //      sendto.setText(obj.getString("Sendto"));
      // Example : description.setText(obj.getString("description"));
      description.setText(obj.getString("description"));
      if(obj.getInt("hasdata") != 0) hasData.setSelected(true);
      else hasData.setSelected(false);
      if(obj.getInt("allowrestricted") != 0) allowRestricted.setSelected(true);
      else allowRestricted.setSelected(false);
      filesize.setText(""+obj.getInt("filesize"));
      nodes.setText(obj.getString("nodes"));
      autoset.setText(obj.getString("autoset"));
      filepath.setText(obj.getString("filepath"));
      updateMS.setText(""+obj.getInt("updatems"));
      className.setText(obj.getString("class"));
      args.setText(obj.getString("args"));
        
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
    sendto = getJComboBox();
    addUpdate = new javax.swing.JButton();
    ID = new javax.swing.JTextField();
    jLabel9 = new javax.swing.JLabel();
    error = new javax.swing.JTextField();
    labDesc = new javax.swing.JLabel();
    description = new javax.swing.JTextField();
    hasData = new javax.swing.JRadioButton();
    fileSizeLab = new javax.swing.JLabel();
    filesize = new javax.swing.JTextField();
    filepathLab = new javax.swing.JLabel();
    filepath = new javax.swing.JTextField();
    labMS = new javax.swing.JLabel();
    updateMS = new javax.swing.JTextField();
    labClass = new javax.swing.JLabel();
    className = new javax.swing.JTextField();
    labArgs = new javax.swing.JLabel();
    allowRestricted = new javax.swing.JRadioButton();
    labNodes = new javax.swing.JLabel();
    nodes = new javax.swing.JTextField();
    labAuto = new javax.swing.JLabel();
    labTest = new javax.swing.JLabel();
    testChannel = new javax.swing.JTextField();
    clearBit = new javax.swing.JButton();
    labNewSendto = new javax.swing.JLabel();
    newSendto = new javax.swing.JTextField();
    jScrollPane1 = new javax.swing.JScrollPane();
    args = new javax.swing.JTextArea();
    jScrollPane2 = new javax.swing.JScrollPane();
    autoset = new javax.swing.JTextArea();

    setLayout(new java.awt.GridBagLayout());

    jLabel1.setText("Send To:");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_END;
    add(jLabel1, gridBagConstraints);

    sendto.setEditable(true);
    sendto.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        sendtoActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(sendto, gridBagConstraints);

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

    ID.setEditable(false);
    ID.setColumns(6);
    ID.setToolTipText("This is the database ID for this sendto record.");
    ID.setMinimumSize(new java.awt.Dimension(40, 22));
    ID.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        IDActionPerformed(evt);
      }
    });
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

    labDesc.setText("Description:");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 3;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    add(labDesc, gridBagConstraints);

    description.setColumns(80);
    description.setMinimumSize(new java.awt.Dimension(408, 22));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 3;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(description, gridBagConstraints);

    hasData.setText("Has Side Data?");
    hasData.setMargin(new java.awt.Insets(0, 0, 0, 0));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 4;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(hasData, gridBagConstraints);

    fileSizeLab.setText("Filesize (mB) :");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 5;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    add(fileSizeLab, gridBagConstraints);

    filesize.setColumns(6);
    filesize.setMinimumSize(new java.awt.Dimension(80, 22));
    filesize.addFocusListener(new java.awt.event.FocusAdapter() {
      public void focusLost(java.awt.event.FocusEvent evt) {
        filesizeFocusLost(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 5;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(filesize, gridBagConstraints);

    filepathLab.setText("Path for Storage on Edge/CWB:");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 6;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    add(filepathLab, gridBagConstraints);

    filepath.setColumns(40);
    filepath.setMinimumSize(new java.awt.Dimension(488, 22));
    filepath.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        filepathActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 6;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(filepath, gridBagConstraints);

    labMS.setText("Update time (ms):");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 8;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    add(labMS, gridBagConstraints);

    updateMS.setColumns(10);
    updateMS.setMinimumSize(new java.awt.Dimension(128, 22));
    updateMS.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        updateMSActionPerformed(evt);
      }
    });
    updateMS.addFocusListener(new java.awt.event.FocusAdapter() {
      public void focusLost(java.awt.event.FocusEvent evt) {
        updateMSFocusLost(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 8;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(updateMS, gridBagConstraints);

    labClass.setText("ClassName : ");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 12;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    add(labClass, gridBagConstraints);

    className.setColumns(40);
    className.setMinimumSize(new java.awt.Dimension(488, 22));
    className.addFocusListener(new java.awt.event.FocusAdapter() {
      public void focusLost(java.awt.event.FocusEvent evt) {
        classNameFocusLost(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 12;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(className, gridBagConstraints);

    labArgs.setText("Arguments:");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 13;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    add(labArgs, gridBagConstraints);

    allowRestricted.setText("Allow restricted channels");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 7;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(allowRestricted, gridBagConstraints);

    labNodes.setText("Roles RE:");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 9;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    add(labNodes, gridBagConstraints);

    nodes.setColumns(60);
    nodes.setMinimumSize(new java.awt.Dimension(500, 28));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 9;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(nodes, gridBagConstraints);

    labAuto.setText("Channel set on Create RE:");
    labAuto.setToolTipText("Channels matching this regular expression should have this sendto set on creation.");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 10;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    add(labAuto, gridBagConstraints);

    labTest.setText("Test Chan (NNSSSSSCCCLL):");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 18;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_END;
    add(labTest, gridBagConstraints);

    testChannel.setColumns(20);
    testChannel.setText("NNSSSSSCCCLL");
    testChannel.setToolTipText("Enter a channel (all 12 characters) in NNSSSSSCCCLL format.  On \"return\" it will indicate whether this channel would match the \"Channel set on Creation\" regular expression.  Useful for testing complex ones.");
    testChannel.setMinimumSize(new java.awt.Dimension(180, 28));
    testChannel.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        testChannelActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 18;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
    add(testChannel, gridBagConstraints);

    clearBit.setText("Clear This bit in all Channels");
    clearBit.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        clearBitActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 19;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_END;
    add(clearBit, gridBagConstraints);

    labNewSendto.setText("RenameSendto:");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 20;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_END;
    add(labNewSendto, gridBagConstraints);

    newSendto.setColumns(50);
    newSendto.setMinimumSize(new java.awt.Dimension(414, 28));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 20;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
    add(newSendto, gridBagConstraints);

    jScrollPane1.setMinimumSize(new java.awt.Dimension(600, 23));
    jScrollPane1.setPreferredSize(new java.awt.Dimension(600, 23));

    args.setColumns(60);
    args.setLineWrap(true);
    args.setRows(5);
    args.setPreferredSize(new java.awt.Dimension(600, 23));
    args.addFocusListener(new java.awt.event.FocusAdapter() {
      public void focusGained(java.awt.event.FocusEvent evt) {
        argsFocusGained(evt);
      }
    });
    jScrollPane1.setViewportView(args);

    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 13;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(jScrollPane1, gridBagConstraints);

    jScrollPane2.setMinimumSize(new java.awt.Dimension(550, 100));
    jScrollPane2.setPreferredSize(new java.awt.Dimension(550, 100));

    autoset.setColumns(60);
    autoset.setFont(new java.awt.Font("Monospaced", 0, 12)); // NOI18N
    autoset.setLineWrap(true);
    autoset.setRows(10);
    autoset.setMinimumSize(new java.awt.Dimension(600, 60));
    autoset.addFocusListener(new java.awt.event.FocusAdapter() {
      public void focusGained(java.awt.event.FocusEvent evt) {
        autosetFocusGained(evt);
      }
    });
    jScrollPane2.setViewportView(autoset);

    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 10;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
    add(jScrollPane2, gridBagConstraints);
  }// </editor-fold>//GEN-END:initComponents

  private void classNameFocusLost(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_classNameFocusLost

    chkForm();
  }//GEN-LAST:event_classNameFocusLost

  private void updateMSFocusLost(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_updateMSFocusLost

    chkForm();
  }//GEN-LAST:event_updateMSFocusLost

  private void filesizeFocusLost(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_filesizeFocusLost

    chkForm();
  }//GEN-LAST:event_filesizeFocusLost

  private void updateMSActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_updateMSActionPerformed

    chkForm();
  }//GEN-LAST:event_updateMSActionPerformed

  private void filepathActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_filepathActionPerformed

    chkForm();
  }//GEN-LAST:event_filepathActionPerformed

  private void addUpdateActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_addUpdateActionPerformed
    
    
    // Do not change
    if(initiating) return;
    if(chkForm()) return;
    try {
      String p = sendto.getSelectedItem().toString();
      //p = p.toUpperCase();                  // USER : drop this to allow Mixed case
      if(!obj.isNew()) obj.refreshRecord();        // Refresh the result set so the sets will not fail on stale one
      if(!newSendto.getText().equals("")) obj.setString("sendto", newSendto.getText());
      else obj.setString("sendto",p);
      
      // USER : Set all of the fields in data base.  obj.setInt("thefield", ivalue);
      obj.setString("description", description.getText());
      if(hasData.isSelected()) obj.setInt("hasdata",1);
      else obj.setInt("hasdata",0);
      if(allowRestricted.isSelected()) obj.setInt("allowrestricted", 1);
      else obj.setInt("allowrestricted",0);
      obj.setInt("filesize",filesizeMB);
      obj.setString("filepath", filepath.getText());
      obj.setString("nodes", nodes.getText());
      obj.setString("autoset", autoset.getText().replaceAll("\n"," "));
      obj.setInt("updatems",updms);
      obj.setString("class", className.getText());
      obj.setString("args", args.getText().replaceAll("\n", " "));
      
      // Do not change
      obj.updateRecord();
      v=null;       // force reload of combo box
      getJComboBox(sendto);
      clearScreen();
      sendto.setSelectedIndex(-1);
    }  catch  (SQLException  E)
    { Util.SQLErrorPrint(E,"sendto: update failed partno="+sendto.getSelectedItem());
      error.setText("Could Not Update File!");
      error.setBackground(UC.red);
      addUpdate.setEnabled(true);
    }      
  }//GEN-LAST:event_addUpdateActionPerformed

  private void sendtoActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_sendtoActionPerformed
   find();
 
 
  }//GEN-LAST:event_sendtoActionPerformed

  private void IDActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_IDActionPerformed
    chkForm();
  }//GEN-LAST:event_IDActionPerformed

  private void testChannelActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_testChannelActionPerformed
    if(testChannel.getText().indexOf("=") > 0) testChannel.setText(testChannel.getText().substring(0,testChannel.getText().indexOf("=")));
    testChannel.setText(testChannel.getText()+"="+testChannel.getText().matches(autoset.getText()));
  }//GEN-LAST:event_testChannelActionPerformed

  private void clearBitActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_clearBitActionPerformed
    if(obj.getID() >0 && obj.getID() < 65 && !obj.isNew() && sendto.getSelectedIndex() >=0) {
      Sendto t  = (Sendto) sendto.getSelectedItem();
      String ans = JOptionPane.showInputDialog(null, "Are you sure you want to clear the "+t.getSendto()+" bit on all Channels (Y/N)?");
      if( ans.equalsIgnoreCase("y")) {
        try {
          Statement stmt = DBConnectionThread.getConnection(DBConnectionThread.getDBSchema()).createStatement();   // used for query
          String s = "UPDATE edge.channel SET sendto = sendto & ~(1<< "+(obj.getID()-1)+")";
          int nrec = stmt.executeUpdate(s);
          Util.prt("clear bits nrec="+nrec);
        }
        catch(SQLException e) {
          e.printStackTrace();
        }
      }
    }
  }//GEN-LAST:event_clearBitActionPerformed

  private void argsFocusGained(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_argsFocusGained
    chkForm();
  }//GEN-LAST:event_argsFocusGained

  private void autosetFocusGained(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_autosetFocusGained
    chkForm();
  }//GEN-LAST:event_autosetFocusGained
  
  
  // Variables declaration - do not modify//GEN-BEGIN:variables
  private javax.swing.JTextField ID;
  private javax.swing.JButton addUpdate;
  private javax.swing.JRadioButton allowRestricted;
  private javax.swing.JTextArea args;
  private javax.swing.JTextArea autoset;
  private javax.swing.JTextField className;
  private javax.swing.JButton clearBit;
  private javax.swing.JTextField description;
  private javax.swing.JTextField error;
  private javax.swing.JLabel fileSizeLab;
  private javax.swing.JTextField filepath;
  private javax.swing.JLabel filepathLab;
  private javax.swing.JTextField filesize;
  private java.awt.GridBagLayout gridBagLayout1;
  private javax.swing.JRadioButton hasData;
  private javax.swing.JLabel jLabel1;
  private javax.swing.JLabel jLabel9;
  private javax.swing.JScrollPane jScrollPane1;
  private javax.swing.JScrollPane jScrollPane2;
  private javax.swing.JLabel labArgs;
  private javax.swing.JLabel labAuto;
  private javax.swing.JLabel labClass;
  private javax.swing.JLabel labDesc;
  private javax.swing.JLabel labMS;
  private javax.swing.JLabel labNewSendto;
  private javax.swing.JLabel labNodes;
  private javax.swing.JLabel labTest;
  private javax.swing.JTextField newSendto;
  private javax.swing.JTextField nodes;
  private javax.swing.JComboBox sendto;
  private javax.swing.JTextField testChannel;
  private javax.swing.JTextField updateMS;
  // End of variables declaration//GEN-END:variables
  /** Creates new form SendtoPanel. */
  public SendtoPanel() {
    initiating=true;
    initComponents();
    getJComboBox(sendto);                // set up the key JComboBox
    sendto.setSelectedIndex(-1);    // Set selected type
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
    makeSendtos();
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
    //Util.prt("SendtoPanel.setJComboBoxToID id="+ID);
    for(int i=0; i<b.getItemCount(); i++) 
      if(((Sendto) b.getItemAt(i)).getID() == ID) {
          b.setSelectedIndex(i);
          //Util.prt("SendtoPanel.setJComboBoxToID id="+ID+" found at "+i);
      }
  }  
  
  /** Given a database ID find the index into a JComboBox consistent with this panel
   *@param ID The row ID from the database
   *@return The index in the JComboBox with this ID
   */
  public static int getJComboBoxIndex(int ID) {
    for(int i=0; i<v.size(); i++) if( ID == ((Sendto) v.get(i)).getID()) return i;
    return -1;
  }
  /** Cause the main JComboBox to be reloaded*/
  public static void reload() {
    v = null;
    makeSendtos();
  }
  
  /** Get the item corresponding to database ID
   *@param ID the database Row ID
   *@return The Sendto row with this ID
   */
  public static Sendto getItemWithID(int ID) {
    int i=getJComboBoxIndex(ID);
    if(i >= 0) return (Sendto) v.get(i);
    else return null;
  }
  
  // This routine should only need tweeking if key field is not same as table name
  private static void makeSendtos() {
    if (v != null) return;
    v=new ArrayList<Sendto>(100);
    try {
      Statement stmt = DBConnectionThread.getConnection(DBConnectionThread.getDBSchema()).createStatement();   // used for query
      String s = "SELECT * FROM edge.sendto ORDER BY sendto;";
      ResultSet rs = stmt.executeQuery(s);
      while (rs.next()) {
        Sendto loc = new Sendto(rs);
//        Util.prt("MakeSendto() i="+v.size()+" is "+loc.getSendto());
        v.add(loc);
      }
      rs.close();
      stmt.close();
    }
    catch (SQLException e) {
      Util.SQLErrorPrint(e,"makeSendtos() on table SQL failed");
    }    
  }
  
  public ArrayList<Sendto> getSendtoVector() {
    if(v == null) makeSendtos();
    return v;
  }
  
  // No changes needed
  private void find() {
    if(sendto == null) return;
    if(initiating) return;
    Sendto l;
    if(sendto.getSelectedIndex() == -1) {
      if(sendto.getSelectedItem() == null) return;
      l = newOne();
    } 
    else {
      l = (Sendto) sendto.getSelectedItem();
    }
      
    if(l == null) return;
    String p = l.getSendto();
    if(p == null) return;
    if(p.equals("")) {
      addUpdate.setEnabled(false);
      addUpdate.setText("Enter a Sendto!");
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
          if(obj.getMaxID() >= 64) chkForm();
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
    { Util.SQLErrorPrint(E,"Sendto: SQL error getting Sendto="+p);
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
      Show.inFrame(new SendtoPanel(), UC.XSIZE, UC.YSIZE);
    }
    catch(InstantiationException e) {}
  }
}
