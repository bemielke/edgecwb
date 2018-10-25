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
 * JComboBox variable this variable is called "cpu" in the initial form
 * The table name and key name should match and start lower case (cpu).
 * The Class here will be that same name but with upper case at beginning(Cpu).
 * <br> 1)  Rename the location JComboBox to the "key" (cpu) value.  This should start Lower
 *      case.
 *<br>  2)  The table name should match this key name and be all lower case.
 *<br>  3)  Change all Cpu to ClassName of underlying data (case sensitive!)
 *<br>  4)  Change all cpu key value (the JComboBox above)
 *<br>  5)  clearScreen should update swing components to new defaults
 *<br>  6)  chkForm should check all field for validity.  The errorTrack variable is
 *      used to track multiple errors.  In FUtil.chk* handle checks of the various
 *      variable types.  Date, double, and IP etc return the "canonical" form for 
 *      the variable.  These should be stored in a class variable so the latest one
 *      from "chkForm()" are available to the update writer.
 *<br>  7) newone() should be updated to create a new instance of the underlying data class.
 *<br>  8) oldone() get data from database and update all swing elements to correct values
 *<br>  9) addUpdateAction() add data base set*() operations updating all fields in the DB
 *<br> 10) CpuPanel() constructor - good place to change backgrounds using UC.Look() any
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
public class CpuPanelInstance extends javax.swing.JPanel {
  
  // NOTE : here define all variables general.  "Vector v" is used for main Comboboz
  static ArrayList<Cpu> v;             // Vector containing objects of this Cpu Type
  DBObject obj;
  ErrorTrack err=new ErrorTrack();
  int nodeNum;
  boolean initiating=false;
  
  //USER: Here are the local variables
  String stripadr=null;
  public static String defaultEdgeprop = 
    "#"+Util.fs+"home"+Util.fs+"vdl"+Util.fs+"edge_$SERVER.prop\n"+
    "Node=?\n" +
    "nday=10\n" +
    "ndatapath=1\n" +
    "datapath="+Util.fs+"data"+Util.fs+"\n" +
    "datamask=YYYY_D\n" +
    "DBServer=localhost/3306:edge:mysql:edge\n" +
    "MetaDBServer=localhost/3306:metadata:mysql:metadata\n" +
    "StatusDBServer=localhost/3306:status:mysql:status\n" +
    "StatusServer=localhost\n" +
    "logfilepath=log"+Util.fs+"\n" +
    "SMTPServer=mailx\n" +
    "emailTO=anss.alarm@usgs.gov\n" +
    "SMTPFrom=$SERVER-vdl@usgs.gov\n"+
    "daysize=10000\n" +
    "extendsize=2000\n" +
    "ebqsize=2000\n" +
    "AlarmIP=localhost\n" +
    "instanceconfig=true\n";

  
  public static String defaultCrontab=  
          "# Read in SQL database from master and load it into our local DB\n"+
          "#* * * * * bash scripts"+Util.fs+"chkEdgeAnssSQL >> LOG"+Util.fs+"chkEdgeAnssSQL.log 2>&1\n"+
          "# Make a pass through the day-before-yesterdays data files and put in holdings and generat fetchlists\n"+
          "#09 05 * * * nice bash scripts"+Util.fs+"updateHoldings.bash >>LOG"+Util.fs+"updateHoldings.log 2>&1\n"+
          "# check the free space on the various file systems,emails to ketchum and events to Alarm\n"+
          "#*/5 * * * * bash scripts"+Util.fs+"chkMinfreegen /home:8000 /data:160000 /var:4000 >>LOG"+Util.fs+"minfreegen.log2 2>&1\n"+
          "# check the process status for hunting hard to find computer seizing (NEIC only!)\n"+
          "#*/5 * * * * bash scripts"+Util.fs+"chkMonitorProcess >>LOG"+Util.fs+"chkMonitorProcess.log 2>&1\n"+
          "# move older oldings to holdingshist,holdinghist2, same for other status DB like latency\n"+
          "#57 09 * * * bash scripts"+Util.fs+"moveHoldings >>LOG"+Util.fs+"MoveHoldings.log 2>&1\n"+
          "# maintain a ringserver on this system.  The configuration is in the ringserver directory\n"+
          "#* * * * * bash "+Util.fs+"home"+Util.fs+"vdl"+Util.fs+"scripts"+Util.fs+"chkRingServer >>LOG"+Util.fs+"ringserver.log 2>&1\n"+
          "# process any data that arrived outside of the nday window into the CWB.  Set IP and port for CWB\n"+
          "#55 07 * * * bash scripts"+Util.fs+"processPastFile.bash localhost 2062 ketchum@usgs.gov >>LOG"+Util.fs+"processPastFile.log 2>&1\n"+
          "# this is the NEIC backup script for the Edge computers.  This should be changed to the local backup script\n"+
          "##48 20 * * * nice bash scripts"+Util.fs+"bkedge >>LOG"+Util.fs+"bkedge.log 2>&1\n";

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
   Util.prt("chkForm Cpu");
   
   if(ipadr.getText().trim().equals("")) stripadr="";
   else stripadr = FUtil.chkIP(ipadr,err,true);   
   nodeNum = FUtil.chkInt(nodeNumber, err,-100, 100);
   if(cpu.getSelectedItem() instanceof String) {
     
   }
   else {
     Cpu thisCpu = (Cpu) cpu.getSelectedItem();
     for(int i=0; i<cpu.getItemCount(); i++) {
       Cpu c = (Cpu) cpu.getItemAt(i);
       if(c.getID() != thisCpu.getID()) {    // do not flunk on self
         if(c.getNodeNumber() == nodeNum) {
           err.set();
           err.appendText("Node="+nodeNum+" not unique in cpu="+c.getCpu());
         }
       }
     }
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
    UC.Look(topPanel);
    error.setText("");
    addUpdate.setEnabled(false);
    addUpdate.setText("Enter a Cpu");
    
    //USER: Clear all fields for this form, this sets "defaults" for new screen
    // description.setText("");
    ipadr.setText("");
    nodeNumber.setText("");
    os.setText("");
    change.setText("");
    hasdata.setSelected(false);
    edgeprop.setText("");
    crontab.setText("");
  }
 
  
  private Cpu newOne() {
      
    return new Cpu(0, ((String) cpu.getSelectedItem()).toUpperCase(), //, more
            "", "", 0, "", "", 0
       );
  }
  
  private void oldOne(String p) throws SQLException {
    obj = new DBObject(DBConnectionThread.getThread(DBConnectionThread.getDBSchema()), DBConnectionThread.getDBSchema(), "cpu","cpu",p);

    if(obj.isNew()) {
      Util.prt("object is new"+p);
    }
    else {
      //USER: Here set all of the form fields to data from the DBObject
      //      cpu.setText(obj.getString("Cpu"));
      // Example : description.setText(obj.getString("description"));
      //cpu.setSelectedItem(obj.getString("cpu"));
      ipadr.setText(obj.getString("ipadr"));
      os.setText(obj.getString("os"));
      nodeNumber.setText(obj.getInt("nodenumber")+"");
      
      if(obj.getInt("hasdata")==1 ) hasdata.setSelected(true);
      else hasdata.setSelected(false);     
      edgeprop.setText(obj.getString("edgeprop"));
      crontab.setText(obj.getString("crontab"));
        
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
    lblCpu = new javax.swing.JLabel();
    cpu = getJComboBox();
    topPanel = new javax.swing.JPanel();
    labNodenumber = new javax.swing.JLabel();
    nodeNumber = new javax.swing.JTextField();
    lblIPadr = new javax.swing.JLabel();
    ipadr = new javax.swing.JTextField();
    lblOS = new javax.swing.JLabel();
    os = new javax.swing.JTextField();
    hasdata = new javax.swing.JRadioButton();
    setDefaultCrontab = new javax.swing.JButton();
    setDefaultEdgeprop = new javax.swing.JButton();
    addUpdate = new javax.swing.JButton();
    ID = new javax.swing.JTextField();
    jLabel9 = new javax.swing.JLabel();
    error = new javax.swing.JTextField();
    delete = new javax.swing.JButton();
    labChange = new javax.swing.JLabel();
    change = new javax.swing.JTextField();
    labEdgeprop = new javax.swing.JLabel();
    jScrollPane1 = new javax.swing.JScrollPane();
    edgeprop = new javax.swing.JTextArea();
    labCrontab = new javax.swing.JLabel();
    jScrollPane2 = new javax.swing.JScrollPane();
    crontab = new javax.swing.JTextArea();

    setLayout(new java.awt.GridBagLayout());

    lblCpu.setText("Node : ");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    add(lblCpu, gridBagConstraints);

    cpu.setEditable(true);
    cpu.setMinimumSize(new java.awt.Dimension(150, 28));
    cpu.setPreferredSize(new java.awt.Dimension(150, 28));
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

    topPanel.setMinimumSize(new java.awt.Dimension(600, 75));
    topPanel.setPreferredSize(new java.awt.Dimension(600, 75));

    labNodenumber.setText("NodeNumber:");
    topPanel.add(labNodenumber);

    nodeNumber.setColumns(4);
    nodeNumber.setMinimumSize(new java.awt.Dimension(62, 28));
    nodeNumber.addFocusListener(new java.awt.event.FocusAdapter() {
      public void focusLost(java.awt.event.FocusEvent evt) {
        nodeNumberFocusLost(evt);
      }
    });
    nodeNumber.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        nodeNumberActionPerformed(evt);
      }
    });
    topPanel.add(nodeNumber);

    lblIPadr.setText("IP Address : ");
    topPanel.add(lblIPadr);

    ipadr.setMinimumSize(new java.awt.Dimension(150, 28));
    ipadr.setPreferredSize(new java.awt.Dimension(150, 28));
    topPanel.add(ipadr);

    lblOS.setText("OS : ");
    topPanel.add(lblOS);

    os.setMinimumSize(new java.awt.Dimension(150, 28));
    os.setPreferredSize(new java.awt.Dimension(150, 28));
    topPanel.add(os);

    hasdata.setText("Data?");
    hasdata.setEnabled(false);
    topPanel.add(hasdata);

    setDefaultCrontab.setText("Set Default Crontab");
    setDefaultCrontab.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        setDefaultCrontabActionPerformed(evt);
      }
    });
    topPanel.add(setDefaultCrontab);

    setDefaultEdgeprop.setText("Set Default edge.prop");
    setDefaultEdgeprop.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        setDefaultEdgepropActionPerformed(evt);
      }
    });
    topPanel.add(setDefaultEdgeprop);

    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 2;
    add(topPanel, gridBagConstraints);

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
    ID.setColumns(20);
    ID.setMinimumSize(new java.awt.Dimension(204, 28));
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

    labChange.setText("ChangeNodeTo:");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 18;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_END;
    add(labChange, gridBagConstraints);

    change.setColumns(20);
    change.setMinimumSize(new java.awt.Dimension(254, 28));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 18;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
    add(change, gridBagConstraints);

    labEdgeprop.setText("edge.prop");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 4;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    add(labEdgeprop, gridBagConstraints);

    jScrollPane1.setMinimumSize(new java.awt.Dimension(600, 160));
    jScrollPane1.setPreferredSize(new java.awt.Dimension(600, 160));

    edgeprop.setColumns(80);
    edgeprop.setFont(new java.awt.Font("Monospaced", 0, 10)); // NOI18N
    edgeprop.setRows(50);
    edgeprop.setToolTipText("<html>\n<p>\nThis would be edge.prop that is defined for this server.  Any of its properties might be overriden by the instance based, or role based properties.  \n<p>\nGenerally this would be things that only depend on this server.  Examples are the Node property setting a system number for this node, \n<p>\nproperties related to storing files (nday?, datapath?, ndatapath?), how this node is configured (DBServer, StatusDBServer, MetaDBServer), \n<p>\nplace for logging (logfilepath), mail configuration (SMTPSerfver, SMTPFrom), where the Alarm process is running (AlarmIP), etc.\n\n<PRE>\nNode=7\nnday=10\nndatapath=1\ndatapath=/data/\nDBServer=localhost/3306:edge:mysql:edge\nMetaDBServer=localhost/3306:metadata:mysql:metadata\nStatusDBServer=localhost/3306:status:mysql:status\nStatusServer=localhost\nlogfilepath=log/\nAlarmIP=localhost\nSMTPServer=mailx\nStatusServer=localhost\nSNWHost=localhost\nSNWPort=10008\n\nSymbols that can be present and substituted :\n$INSTANCE  replaced with the instance setting.  Useful in ring file names.\n$ROLE replaced with the role name or primary role name\n$SERVER replaced with the physical node/cpu system name.\n</PRE>\n</html>\n");
    edgeprop.setMinimumSize(new java.awt.Dimension(600, 80));
    jScrollPane1.setViewportView(edgeprop);

    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 4;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(jScrollPane1, gridBagConstraints);

    labCrontab.setText("Crontab:");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 7;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_END;
    add(labCrontab, gridBagConstraints);

    jScrollPane2.setMinimumSize(new java.awt.Dimension(600, 160));
    jScrollPane2.setPreferredSize(new java.awt.Dimension(600, 160));

    crontab.setColumns(80);
    crontab.setFont(new java.awt.Font("Monospaced", 0, 10)); // NOI18N
    crontab.setRows(50);
    crontab.setToolTipText("<html>\nThese are crontab lines that will always run on this server whether it is assigned a node or not.  So the \nlines here should be independent of any role or instance.  Examples are :\n<br>\nmoveHoldings - this moves the holdings table records that are old enough to holdingshist, and holdingshist2\n<br>\nchkEdgeAnssSQL - checks for a econ2vdldfc.sql, anss2vdldfc.sql, or metadata.sql.gz  file and loads it into the local database\n<br>\nupdateHoldings.bash - does the daily processing for holdings, makes up fetchlist entries, etc for day before yesterday\n<br>\nbkedge - This is the backup process run every day.  This is specific to the NEIC but any other backup script would go here\n<br>\nchkMinfreegen - this checks freespace on various mount points and sends alerts when space is short.\n<PRE>\n#57 09 * * * bash scripts/moveHoldings >>LOG/MoveHoldings.log 2>&1\n#* * * * * bash scripts/chkEdgeAnssSQL >> LOG/chkEdgeAnssSQL.log 2>&1\n#09 05 * * * nice bash scripts/updateHoldings.bash >>LOG/updateHoldings.log 2>&1\n#48 20 * * * nice bash scripts/bkedge >>LOG/bkedge.log 2>&1 \n#*/5 * * * * bash scripts/chkMonitorProcess >>LOG/chkMonitorProcess.log 2>&1\n#*/5 * * * * bash scripts/chkMinfreegen /home:8000 /data:160000 /var:4000 >>LOG/minfreegen.log2 2>&1\n\nNote : the chkCWB and chkNEIC scripts can be invoked starting with these tags and omitting the output log. \nThe crontab will add to be run every minute and the log will go to '>>LOG/chkcwb.log 2>&1'.\n\nSymbols that can be present and substituted :\n$INSTANCE  replaced with the instance setting.  Useful in ring file names.\n$ROLE replaced with the role name or primary role name\n$SERVER replaced with the physical node/cpu system name.\n</PRE>\n</html>");
    crontab.setMinimumSize(new java.awt.Dimension(600, 160));
    jScrollPane2.setViewportView(crontab);

    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 7;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
    add(jScrollPane2, gridBagConstraints);
  }// </editor-fold>//GEN-END:initComponents

  private void addUpdateActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_addUpdateActionPerformed
    // Add your handling code here:
    
    // Do not change
    if(initiating) return;
    if(chkForm()) return;
    try {
      String p = cpu.getSelectedItem().toString();
      //p = p.toUpperCase();                  // USER : drop this to allow Mixed case
      if(!obj.isNew()) obj.refreshRecord();        // Refresh the result set so the sets will not fail on stale one
      if(change.getText().length() > 0) obj.setString("cpu", change.getText());
      else obj.setString("cpu", p);
      
      // USER : Set all of the fields in data base.  obj.setInt("thefield", ivalue);
      obj.setString("ipadr", stripadr);
      obj.setInt("nodenumber", nodeNum);
      obj.setString("os", os.getText());
      obj.setInt("hasdata",1);
      //else obj.setInt("hasdata",0);
      obj.setString("edgeprop", Util.chkTrailingNewLine(edgeprop.getText()));
      obj.setString("crontab", Util.chkTrailingNewLine(crontab.getText()));
      // Do not change
      obj.updateRecord();
      DBConnectionThread dbconn = DBConnectionThread.getThread("edge");
      if(cpu.getSelectedIndex() >= 0)
        if(!(cpu.getSelectedItem() instanceof String)) 
          dbconn.executeUpdate("UPDATE edge.role set hasdata=1 WHERE cpuID="+((Cpu) cpu.getSelectedItem()).getID());
      v=null;       // force reload of combo box
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

  private void cpuActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_cpuActionPerformed
    // Add your handling code here:
   find();
 
 
}//GEN-LAST:event_cpuActionPerformed

private void deleteActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_deleteActionPerformed
// TODO add your handling code here:
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

  private void setDefaultCrontabActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_setDefaultCrontabActionPerformed
    crontab.setText(defaultCrontab);
  }//GEN-LAST:event_setDefaultCrontabActionPerformed

  private void setDefaultEdgepropActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_setDefaultEdgepropActionPerformed
    edgeprop.setText(defaultEdgeprop);
  }//GEN-LAST:event_setDefaultEdgepropActionPerformed

  private void nodeNumberActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_nodeNumberActionPerformed
    chkForm();
  }//GEN-LAST:event_nodeNumberActionPerformed

  private void nodeNumberFocusLost(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_nodeNumberFocusLost
    chkForm();
  }//GEN-LAST:event_nodeNumberFocusLost
  
  
  // Variables declaration - do not modify//GEN-BEGIN:variables
  private javax.swing.JTextField ID;
  private javax.swing.JButton addUpdate;
  private javax.swing.JTextField change;
  private javax.swing.JComboBox cpu;
  private javax.swing.JTextArea crontab;
  private javax.swing.JButton delete;
  private javax.swing.JTextArea edgeprop;
  private javax.swing.JTextField error;
  private java.awt.GridBagLayout gridBagLayout1;
  private javax.swing.JRadioButton hasdata;
  private javax.swing.JTextField ipadr;
  private javax.swing.JLabel jLabel9;
  private javax.swing.JScrollPane jScrollPane1;
  private javax.swing.JScrollPane jScrollPane2;
  private javax.swing.JLabel labChange;
  private javax.swing.JLabel labCrontab;
  private javax.swing.JLabel labEdgeprop;
  private javax.swing.JLabel labNodenumber;
  private javax.swing.JLabel lblCpu;
  private javax.swing.JLabel lblIPadr;
  private javax.swing.JLabel lblOS;
  private javax.swing.JTextField nodeNumber;
  private javax.swing.JTextField os;
  private javax.swing.JButton setDefaultCrontab;
  private javax.swing.JButton setDefaultEdgeprop;
  private javax.swing.JPanel topPanel;
  // End of variables declaration//GEN-END:variables
  /** Creates new form CpuPanel. */
  public CpuPanelInstance() {
    initiating=true;
    initComponents();
    getJComboBox(cpu);                // set up the key JComboBox
    cpu.setSelectedIndex(-1);    // Set selected type
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
    makeCpus();
    for (Cpu v1 : v) {
      b.addItem(v1);
    }
    b.setMaximumRowCount(30);
  }
  
  /** Given a JComboBox from getJComboBox, set selected item to match database ID
   *@param b The JComboBox
   *@param ID the row ID from the database to select
   */
  public static void setJComboBoxToID(JComboBox b, int ID) {
    b.setSelectedIndex(-1);
    //Util.prt("CpuPanel.setJComboBoxToID id="+ID);
    for(int i=0; i<b.getItemCount(); i++) 
      if(((Cpu) b.getItemAt(i)).getID() == ID) {
          b.setSelectedIndex(i);
          //Util.prt("CpuPanel.setJComboBoxToID id="+ID+" found at "+i);
      }
  }  
  
  /** Given a database ID find the index into a JComboBox consistent with this panel
   *@param ID The row ID from the database
   *@return The index in the JComboBox with this ID
   */
  public static int getJComboBoxIndex(int ID) {
    if(v == null) makeCpus();
    for(int i=0; i<v.size(); i++) if( ID == ((Cpu) v.get(i)).getID()) return i;
    return -1;
  }
  /** Cause the main JComboBox to be reloaded. */
  public static void reload() {
    v = null;
    makeCpus();
  }
  /** return a vector with all of the Cpu
   * @return The vector with the cpu
   */
  public static ArrayList getCpuVector() {
    if(v == null) makeCpus();
    return v;
  }
  /** Get the item corresponding to database ID
   *@param ID the database Row ID
   *@return The Cpu row with this ID
   */
  public static Cpu getCpuWithID(int ID) {
    if(v == null) makeCpus();
    int i=getJComboBoxIndex(ID);
    if(i >= 0) return (Cpu) v.get(i);
    else return null;
  }
  
  // This routine should only need tweeking if key field is not same as table name
  private static void makeCpus() {
    if (v != null) return;
    v=new ArrayList<>(100);
    try {
      try (Statement stmt = DBConnectionThread.getConnection(DBConnectionThread.getDBSchema()).createStatement() // used for query
      ) {
        String s = "SELECT * FROM edge.cpu ORDER BY cpu;";
        try (ResultSet rs = stmt.executeQuery(s)) {
          while (rs.next()) {
            Cpu loc = new Cpu(rs);
//        Util.prt("MakeCpu() i="+v.size()+" is "+loc.getCpu());
            v.add(loc);
          }
        }
      }
    }
    catch (SQLException e) {
      Util.SQLErrorPrint(e,"makeCpus() on table SQL failed");
    }    
  }
  
  // No changes needed
  private void find() {
    if(cpu == null) return;
    if(initiating) return;
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
          {Util.prt("Could not connect to DB "+jcjbl); System.exit(1);}
      Show.inFrame(new CpuPanelInstance(), UC.XSIZE, UC.YSIZE);
    }
    catch(InstantiationException e) {}
  }
}
