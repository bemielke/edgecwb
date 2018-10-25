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
import javax.swing.JOptionPane;

/**
 *
 * requirements : 
 * The key for the file must be the name of the table and of the
 * JComboBox variable this variable is called "snwgroup" in the initial form
 * The table name and key name should match and start lower case (snwgroup).
 * The Class here will be that same name but with upper case at beginning(SNWGroup).
 * <br> 1)  Rename the location JComboBox to the "key" (snwgroup) value.  This should start Lower
 *      case.
 *<br>  2)  The table name should match this key name and be all lower case.
 *<br>  3)  Change all SNWGroup to ClassName of underlying data (case sensitive!)
 *<br>  4)  Change all snwgroup key value (the JComboBox above)
 *<br>  5)  clearScreen should update swing components to new defaults
 *<br>  6)  chkForm should check all field for validity.  The errorTrack variable is
 *      used to track multiple errors.  In FUtil.chk* handle checks of the various
 *      variable types.  Date, double, and IP etc return the "canonical" form for 
 *      the variable.  These should be stored in a class variable so the latest one
 *      from "chkForm()" are available to the update writer.
 *<br>  7) newone() should be updated to create a new instance of the underlying data class.
 *<br>  8) oldone() get data from database and update all swing elements to correct values
 *<br>  9) addUpdateAction() add data base set*() operations updating all fields in the DB
 *<br> 10) SNWGroupPanel() constructor - good place to change backgrounds using UC.Look() any
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
public final class SNWGroupPanel extends javax.swing.JPanel {
  
  // NOTE : here define all variables general.  "Vector v" is used for main Comboboz
  private DBObject obj;
  private final ErrorTrack err=new ErrorTrack();
  private boolean initiating=false;
  private String rawdoc;
  
  //USER: Here are the local variables
  private int pctbadInt;
  private int pctbadwarnInt;

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
   Util.prt("chkForm SNWGroup");
   pctbadInt = FUtil.chkInt(pctbad,err,  0,100);
   pctbadwarnInt = FUtil.chkInt(pctbadwarn,err,  0,100);
   if(pctbadInt > pctbadwarnInt) {
     err.set();
     err.appendText("Pct Bad is > Pct Bad + Warn!");
   }
    if( obj.getMaxID() > 64) {
      Util.prt("Too many for a bit map field !!!");
      err.appendText("Too many for a bit field"); err.set();
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
    addUpdate.setEnabled(false);
    addUpdate.setText("Enter a SNWGroup");
    
    //USER: Clear all fields for this form, this sets "defaults" for new screen
    description.setText("");
    documentation.setText("");
    documentation.setEditable(true);
    rawdoc="";
    pctbad.setText("");
    pctbadwarn.setText("");
    newGroup.setText("");
    
  }
 
  
  private SNWGroup newOne() {
      
    return new SNWGroup(0, ((String) snwgroup.getSelectedItem()), "", "", 0, 0 //, more
       );
  }
  
  private void oldOne(String p) throws SQLException {
    obj = new DBObject(DBConnectionThread.getThread(DBConnectionThread.getDBSchema()), DBConnectionThread.getDBSchema(), "snwgroup","snwgroup",p);
    
    if(obj.isNew()) {
      Util.prt("object is new"+p);
      chkForm();
    }
    else {
      //USER: Here set all of the form fields to data from the DBObject
      //      snwgroup.setText(obj.getString("SNWGroup"));
      description.setText(obj.getString("description"));
      documentation.setText(obj.getString("documentation"));
      rawdoc=documentation.getText();
      pctbad.setText(obj.getString("pctbad"));
      pctbadwarn.setText(obj.getString("pctbadwarn"));

        
        
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
    snwgroup = getJComboBox();
    addUpdate = new javax.swing.JButton();
    ID = new javax.swing.JTextField();
    jLabel9 = new javax.swing.JLabel();
    error = new javax.swing.JTextField();
    labDesc = new javax.swing.JLabel();
    description = new javax.swing.JTextField();
    updateAll = new javax.swing.JButton();
    jLabel2 = new javax.swing.JLabel();
    jLabel3 = new javax.swing.JLabel();
    pctbad = new javax.swing.JTextField();
    pctbadwarn = new javax.swing.JTextField();
    docScrollPane = new javax.swing.JScrollPane();
    documentation = new javax.swing.JTextArea();
    labDoc = new javax.swing.JLabel();
    labNewGrouip = new javax.swing.JLabel();
    newGroup = new javax.swing.JTextField();
    showDecoded = new javax.swing.JButton();
    help = new javax.swing.JTextField();
    clearBits = new javax.swing.JButton();

    setLayout(new java.awt.GridBagLayout());

    jLabel1.setText("Group : ");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    add(jLabel1, gridBagConstraints);

    snwgroup.setEditable(true);
    snwgroup.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        snwgroupActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(snwgroup, gridBagConstraints);

    addUpdate.setText("Add/Update");
    addUpdate.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        addUpdateActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 13;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(addUpdate, gridBagConstraints);

    ID.setColumns(8);
    ID.setEditable(false);
    ID.setMinimumSize(new java.awt.Dimension(100, 20));
    ID.setPreferredSize(new java.awt.Dimension(100, 20));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 14;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(ID, gridBagConstraints);

    jLabel9.setText("ID :");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 14;
    add(jLabel9, gridBagConstraints);

    error.setColumns(40);
    error.setEditable(false);
    error.setMinimumSize(new java.awt.Dimension(488, 22));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(error, gridBagConstraints);

    labDesc.setText("Description : ");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 2;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    add(labDesc, gridBagConstraints);

    description.setColumns(50);
    description.setToolTipText("If this description includes the string '<ANALYST>', then it will sort to the first and include a '+' sign in channel display.\n");
    description.setMinimumSize(new java.awt.Dimension(400, 20));
    description.setPreferredSize(new java.awt.Dimension(400, 20));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 2;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(description, gridBagConstraints);

    updateAll.setText("Set All ANSS station groups");
    updateAll.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        updateAllActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 16;
    add(updateAll, gridBagConstraints);

    jLabel2.setText("Percent Bad : ");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 3;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    add(jLabel2, gridBagConstraints);

    jLabel3.setText("Pct Bad+Warn : ");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 4;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    add(jLabel3, gridBagConstraints);

    pctbad.setMinimumSize(new java.awt.Dimension(50, 20));
    pctbad.setPreferredSize(new java.awt.Dimension(50, 20));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 3;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(pctbad, gridBagConstraints);

    pctbadwarn.setMinimumSize(new java.awt.Dimension(50, 20));
    pctbadwarn.setPreferredSize(new java.awt.Dimension(50, 20));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 4;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(pctbadwarn, gridBagConstraints);

    docScrollPane.setMinimumSize(new java.awt.Dimension(650, 300));

    documentation.setColumns(70);
    documentation.setRows(30);
    documentation.setTabSize(2);
    docScrollPane.setViewportView(documentation);

    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 6;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(docScrollPane, gridBagConstraints);

    labDoc.setText("Documentation:");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 6;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    add(labDoc, gridBagConstraints);

    labNewGrouip.setText("Change Name to:");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 9;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    add(labNewGrouip, gridBagConstraints);

    newGroup.setColumns(20);
    newGroup.setMinimumSize(new java.awt.Dimension(100, 28));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 9;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(newGroup, gridBagConstraints);

    showDecoded.setText("Format");
    showDecoded.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        showDecodedActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 7;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    add(showDecoded, gridBagConstraints);

    help.setBackground(new java.awt.Color(204, 204, 204));
    help.setEditable(false);
    help.setFont(new java.awt.Font("Monospaced", 0, 10)); // NOI18N
    help.setText("<Last&[First]|OrgTag[^format]> %f=First%l=Last %e=email %o=Office %c=Cell %h=Home %p=PCmnt %d=OrgCmnt");
    help.setMinimumSize(new java.awt.Dimension(650, 28));
    help.setPreferredSize(new java.awt.Dimension(650, 23));
    help.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        helpActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 7;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(help, gridBagConstraints);

    clearBits.setText("Clear this bits in all SNW stations");
    clearBits.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        clearBitsActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 17;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    add(clearBits, gridBagConstraints);
  }// </editor-fold>//GEN-END:initComponents

    private void updateAllActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_updateAllActionPerformed
// TODO add your handling code here:
      String ans = JOptionPane.showInputDialog(null, "Are you sure you want update all groups based on ANSS setting (Y/N)?");
      if( ans.equalsIgnoreCase("y")) {
        SNWStationPanel.setSNWGroupsFromANSS("");
      }
    }//GEN-LAST:event_updateAllActionPerformed

  private void addUpdateActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_addUpdateActionPerformed
    // Add your handling code here:
    FUtil.searchJComboBox(snwgroup, true);
    // Do not change
    if(initiating) return;
    if(chkForm()) return;
    try {
      String p = snwgroup.getSelectedItem().toString();
      if(!obj.isNew()) obj.refreshRecord();        // Refresh the result set so the sets will not fail on stale one
      if(!newGroup.getText().equals("")) p = newGroup.getText();
      obj.setString("snwgroup",p);
      
      // USER : Set all of the fields in data base.  obj.setInt("thefield", ivalue);
      obj.setString("description",description.getText());
      if(showDecoded.getText().equals("Format")) obj.setString("documentation", documentation.getText());
      else obj.setString("documentation",rawdoc);
      obj.setInt("pctbad",pctbadInt);
      obj.setInt("pctbadwarn",pctbadwarnInt);
      
      // Do not change
      obj.updateRecord();
      SNWGroup.snwgroups=null;       // force reload of combo box
      getJComboBox(snwgroup);
      clearScreen();
      snwgroup.setSelectedIndex(-1);
    }  catch  (SQLException  E)
    { Util.SQLErrorPrint(E,"snwgroup: update failed partno="+snwgroup.getSelectedItem());
      error.setText("Could Not Update File!");
      error.setBackground(UC.red);
      addUpdate.setEnabled(true);
    }      
  }//GEN-LAST:event_addUpdateActionPerformed

  private void snwgroupActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_snwgroupActionPerformed
    // Add your handling code here:
    FUtil.searchJComboBox(snwgroup, true);
   find();
 
 
  }//GEN-LAST:event_snwgroupActionPerformed
  public static String doTags(String input, Statement oracleght) {
    String [] tokens = input.split("[<>]");
    if(tokens.length == 1) return input;
    StringBuilder sb = new StringBuilder(input.length()+300);
    for(int i=0; i<tokens.length; i++) {
      if(i % 2 == 1) {
        if(tokens[i].contains("&")) {    // Its a name, look it up
          String [] name = tokens[i].split("[&^]");
          String s = "SELECT * FROM anss_poc_people_vw WHERE last_name ='"+name[0].trim()+"'";
          if(name.length > 1 && name[1].length() > 0) s += " AND first_name LIKE '%"+name[1].trim()+"%'";
          try {
            try (ResultSet rs = oracleght.executeQuery(s)) {
              if(rs.next()) {
                if(name.length <= 2) {
                  sb.append(" ").append(rs.getString("first_name")).append(" ").append(rs.getString("last_name")).append(" <").append(rs.getString("emp_email")).append("> Office Ph:").append(doPhone(rs.getString("emp_off_ph"))).append(" ");
                }
                if(name.length>= 3) {
                  sb.append(replaceAllFields(name[2],rs));
                }
              }
              else {
                sb.append(" <").append(tokens[i]).append("> ");      // if no name is found, leave tag
              }
            }
          }
          catch(SQLException e) {
            Util.prt("Could not find name matching tag=<"+tokens[i]);
            e.printStackTrace();
          }

        }
        else {        // Try an organization
          try {
            String [] name = tokens[i].split("\\^");
            String  s = "SELECT * FROM anss_poc_orgs_vw WHERE ANSS_TAG LIKE '%"+name[0]+"%'";
            ResultSet rs = oracleght.executeQuery(s);
            boolean found=false;
            if(rs.next()) {
              found = true;
              int org_id=rs.getInt("org_id");
              rs.close();
              rs = oracleght.executeQuery("SELECT * from anss_poc_people_vw WHERE org_id="+org_id);
              int count=0;
              String comment="";
              while(rs.next()) {
                comment=rs.getString("org_comment");
                if(name.length > 1) {
                  sb.append(count== 0?"":"\n").append(replaceAllFields(name[1],rs));
                }
                else {
                  sb.append(count == 0?"":"\n").append(rs.getString("first_name")).append(" ").append(rs.getString("last_name")).append(" <").append(rs.getString("emp_email")).append("> ").append(doPhone(rs.getString("emp_off_ph"))).append(" ").append(rs.getString("title"));
                }
                count++;
              }
                if(count > 1) sb.append("\n").append(comment).append("\n");
            }
            else {
              rs.close();
              s = "SELECT * FROM anss_poc_orgs_vw WHERE name LIKE '%"+name[0]+"%'";
              rs = oracleght.executeQuery(s);
              if(rs.next()) {
                found = true;
                int org_id=rs.getInt("org_id");
                rs.close();
                rs = oracleght.executeQuery("SELECT * from anss_poc_people_vw WHERE org_id="+org_id);
                int count=0;
                String comment="";
                while(rs.next()) {
                  if(name.length > 1) {
                    sb.append(count== 0?"":"\n").append(replaceAllFields(name[1],rs));
                    comment=rs.getString("org_comment");
                  }
                  else {
                    comment=rs.getString("org_comment");
                    sb.append(count == 0?"":"\n").append(rs.getString("first_name")).append(" ").append(rs.getString("last_name")).append(" <").append(rs.getString("emp_email")).append("> ").append(doPhone(rs.getString("emp_off_ph"))).append(" ").append(rs.getString("title"));
                  }
                  count++;
                }
                if(count > 1) sb.append("\n").append(comment).append("\n");
              }
            }
          }
          catch(SQLException e) {
            Util.prt("Could not find organization or people in it for org="+tokens[i]);
            e.printStackTrace();
          }
        }
      }
      else {
        sb.append(tokens[i]);
      }
    }
    return sb.toString();
  }

  private static String replaceAllFields(String line, ResultSet rs) throws SQLException {
    line = line.replaceAll("%e", rs.getString("emp_email") == null?"NotOnFile":rs.getString("emp_email"));
    line = line.replaceAll("%l", rs.getString("last_name")== null?"NotOnFile":rs.getString("last_name"));
    line = line.replaceAll("%f", rs.getString("first_name")== null?"NotOnFile":rs.getString("first_name"));
    line = line.replaceAll("%o", rs.getString("emp_off_ph")== null?"NotOnFile":doPhone(rs.getString("emp_off_ph")));
    line = line.replaceAll("%h", rs.getString("emp_home_ph")== null?"NotOnFile":doPhone(rs.getString("emp_home_ph")));
    line = line.replaceAll("%c", rs.getString("emp_cell_ph")== null?"NotOnFile":doPhone(rs.getString("emp_cell_ph")));
    line = line.replaceAll("%C", rs.getString("work_cell_ph")== null?"NotOnFile":doPhone(rs.getString("work_cell_ph")));
    line = line.replaceAll("%t", rs.getString("title")== null?"NotOnFile":rs.getString("title"));
    line = line.replaceAll("%a", rs.getString("anss_tag")== null?"NotOnFile":rs.getString("anss_tag"));
    line = line.replaceAll("%d", rs.getString("org_comment")== null?"NotOnFile":rs.getString("org_comment"));
    line = line.replaceAll("%p", rs.getString("person_comment")== null?"NotOnFile":rs.getString("person_comment"));
    return line;

  }
  private static String doPhone(String ph) {
    if(ph == null) return "null";
    if(ph.contains("-")) return ph;
   if(ph.contains(" ")) return ph;
   if(ph.length() == 7) return ph.substring(0,3)+"-"+ph.substring(3);
    if(ph.length() == 10) return ph.substring(0,3)+" "+ph.substring(3,6)+"-"+ph.substring(6);
    return ph;
  }
private void helpActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_helpActionPerformed
// TODO add your handling code here:
}//GEN-LAST:event_helpActionPerformed

private void showDecodedActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_showDecodedActionPerformed
  if(showDecoded.getText().equals("Format")) {
    rawdoc = documentation.getText();
    try {
      DBConnectionThread dbpoc = DBConnectionThread.getThread("poc");
      if(dbpoc == null) {
        try {
            dbpoc = new DBConnectionThread((Util.getProperty("DBServer").contains("127.0.0.1")?
              "127.0.0.1":"igskcicgvmdbint.cr.usgs.gov/5432"), // was gldintdb and TEAM2 port 1521
                    "ghsct1","adm_owner","ham23*ret", false, false, "poc", "postgres", Util.getOutput());
          if(!dbpoc.waitForConnection())
            if(!dbpoc.waitForConnection()) Util.prt("Did not make prompt postgres connection"+dbpoc);
        }
        catch(InstantiationException e) {
          documentation.setText("Unable to connect to USGS POC service ="+dbpoc);
        }
        
      }
      if(dbpoc != null) {
        try (Statement stmt = dbpoc.getConnection().createStatement()) {
          documentation.setText(doTags(rawdoc, stmt));
        }
      }
      else documentation.setText("Nothing to decode - not at USGS");
      showDecoded.setText("Show Raw");
      documentation.setEditable(false);
    }
    catch(SQLException e) {
      e.printStackTrace();
    }
  }
  else {
    documentation.setEditable(true);
    showDecoded.setText("Format");
    documentation.setText(rawdoc);
  }
}//GEN-LAST:event_showDecodedActionPerformed

  private void clearBitsActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_clearBitsActionPerformed
    if(obj.getID() >0 && obj.getID() < 65 && !obj.isNew() && snwgroup.getSelectedIndex() >=0) {
      SNWGroup t  = (SNWGroup) snwgroup.getSelectedItem();
      String ans = JOptionPane.showInputDialog(null, "Are you sure you want to clear the "+t.getSNWGroup()+" bit on all SNW stations (Y/N)?");
      if( ans.equalsIgnoreCase("y")) {      
        try {
          Statement stmt = DBConnectionThread.getConnection(DBConnectionThread.getDBSchema()).createStatement();   // used for query
          String s = "UPDATE edge.snwstation SET groupmask = groupmask & ~(1 << "+(obj.getID()-1)+")";
          int nrec = stmt.executeUpdate(s);
          Util.prt("clear bits nrec="+nrec);
        }
        catch(SQLException e) {
          e.printStackTrace();
        }
      }
    }
  }//GEN-LAST:event_clearBitsActionPerformed
  
  
  // Variables declaration - do not modify//GEN-BEGIN:variables
  private javax.swing.JTextField ID;
  private javax.swing.JButton addUpdate;
  private javax.swing.JButton clearBits;
  private javax.swing.JTextField description;
  private javax.swing.JScrollPane docScrollPane;
  private javax.swing.JTextArea documentation;
  private javax.swing.JTextField error;
  private java.awt.GridBagLayout gridBagLayout1;
  private javax.swing.JTextField help;
  private javax.swing.JLabel jLabel1;
  private javax.swing.JLabel jLabel2;
  private javax.swing.JLabel jLabel3;
  private javax.swing.JLabel jLabel9;
  private javax.swing.JLabel labDesc;
  private javax.swing.JLabel labDoc;
  private javax.swing.JLabel labNewGrouip;
  private javax.swing.JTextField newGroup;
  private javax.swing.JTextField pctbad;
  private javax.swing.JTextField pctbadwarn;
  private javax.swing.JButton showDecoded;
  private javax.swing.JComboBox snwgroup;
  private javax.swing.JButton updateAll;
  // End of variables declaration//GEN-END:variables
  /** Creates new form SNWGroupPanel. */
  public SNWGroupPanel() {
    initiating=true;
    initComponents();
    getJComboBox(snwgroup);                // set up the key JComboBox
    snwgroup.setSelectedIndex(-1);    // Set selected type
    UC.Look((javax.swing.JPanel) this);                    // Set color background
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
    makeSNWGroups();
    for (int i=0; i< SNWGroup.snwgroups.size(); i++) {
      b.addItem( SNWGroup.snwgroups.get(i));
    }
    b.setMaximumRowCount(30);
  }
  
  /** Given a JComboBox from getJComboBox, set selected item to match database ID
   *@param b The JComboBox
   *@param ID the row ID from the database to select
   */
  public static void setJComboBoxToID(JComboBox b, int ID) {
    b.setSelectedIndex(-1);
    //Util.prt("SNWGroupPanel.setJComboBoxToID id="+ID);
    for(int i=0; i<b.getItemCount(); i++) 
      if(((SNWGroup) b.getItemAt(i)).getID() == ID) {
          b.setSelectedIndex(i);
          //Util.prt("SNWGroupPanel.setJComboBoxToID id="+ID+" found at "+i);
      }
  }  
  
  /** Given a database ID find the index into a JComboBox consistent with this panel
   *@param ID The row ID from the database
   *@return The index in the JComboBox with this ID 
   */
  public static int getJComboBoxIndex(int ID) {
    if(SNWGroup.snwgroups == null) makeSNWGroups();
    for(int i=0; i<SNWGroup.snwgroups.size(); i++) if( ID == SNWGroup.snwgroups.get(i).getID()) return i;
    return -1;
  }
  /** Cause the main JComboBox to be reloaded. */
  public static void reload() {
    SNWGroup.snwgroups = null;
    makeSNWGroups();
  }
 /** return a vector with all of the Groups
  * @return The vector with the rules
  */
  public static ArrayList getGroupVector() {
    if(SNWGroup.snwgroups == null) makeSNWGroups();
    return SNWGroup.snwgroups;
  }
  /** Get the item corresponding to database ID
   *@param ID the database Row ID
   *@return The SNWGroup row with this ID
   */
  public static SNWGroup getItemWithID(int ID) {
    if(SNWGroup.snwgroups == null) makeSNWGroups();
    int i=getJComboBoxIndex(ID);
    if(i >= 0) return SNWGroup.snwgroups.get(i);
    else return null;
  }
  
  // This routine should only need tweeking if key field is not same as table name
  private static void makeSNWGroups() {
    SNWGroup.makeSNWGroups();

  }
  
  // No changes needed
  private void find() {
    if(snwgroup == null) return;
    if(initiating) return;
    SNWGroup l;
    if(snwgroup.getSelectedIndex() == -1) {
      if(snwgroup.getSelectedItem() == null) return;
      l = newOne();
    } 
    else {
      l = (SNWGroup) snwgroup.getSelectedItem();
    }
      
    if(l == null) return;
    String p = l.getSNWGroup();
    if(p == null) return;
    if(p.equals("")) {
      addUpdate.setEnabled(false);
      addUpdate.setText("Enter a SNWGroup!");
    }
    error.setBackground(Color.lightGray);
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
    { Util.SQLErrorPrint(E,"SNWGroup: SQL error getting SNWGroup="+p);
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
      Show.inFrame(new SNWGroupPanel(), UC.XSIZE, UC.YSIZE);
    }
    catch(InstantiationException e) {}
  }

}
