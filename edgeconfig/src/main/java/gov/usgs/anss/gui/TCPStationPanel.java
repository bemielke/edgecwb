/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */

 package gov.usgs.anss.gui;
import gov.usgs.anss.guidb.TCPStation;
import gov.usgs.anss.guidb.Cpu;
import gov.usgs.anss.db.DBConnectionThread;
import gov.usgs.anss.db.DBObject;
import gov.usgs.anss.util.ErrorTrack;
import gov.usgs.anss.util.FUtil;
import gov.usgs.anss.util.Show;
import gov.usgs.anss.util.Util;
import gov.usgs.edge.snw.SNWStationPanel;
import java.awt.Color;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import javax.swing.JComboBox;
/**
 *
 * @author  ketchum
 * requirements : 
 * JComboBox variable name must be that of the table the data comes from
 *      The creation routine in form should be set to getJComboBox();
 * error - Must be a JTextField for posting error communications
 * updateAdd - Must be the JButton for user clicks to try to post the data
 * ID - JTextField which must be non-editable for posting Database ID
 * 
 */
public final class TCPStationPanel extends javax.swing.JPanel {
  
  // NOTE : here define all variables general.  "ArrayList v" is used for main Comboboz
  static ArrayList<TCPStation> v;             // ArrayList containing objects of this TCPStation Type
  private DBObject obj;
  private final ErrorTrack err=new ErrorTrack();
  
  // Here are the local variables
  private java.sql.Date sdate;
  private String rtsport1, rtsport2, rtsport3, rtsport4; // used to disable this screen if RTS selected
  //int nodeValue;
  

  // This routine must validate all fields on the form.  The err (ErrorTrack) variable
  // is used by the FUtil to verify fields.  Use FUTil or custom code here for verifications
  private boolean chkForm() {
    // Do not change
   err.reset();
   UC.Look(error);
   
   // Your error checking code goes here setting the local variables to valid settings
   Util.prt("chkForm TCPStation");
   FUtil.chkJComboBox(clientServer,"Type Connection", err);
   sdate = FUtil.chkDate(suppressDate, err);
    // No CHANGES : If we found an error, color up error box
    if(err.isSet()) {
      error.setText(err.getText());
      error.setBackground(UC.red);
    }
    return err.isSet();
  }
  
  // This routine must set the form to initial state.  It does not update the JCombobox
  private void clearScreen() {
    ipadr.setText("");
    port.setText("0");
    timeout.setText("600");
    clientServer.setSelectedIndex(-1);
    gomberg.setSelected(true);
    rollback.setSelected(true);
    ctrlq.setSelected(true);
    consoleIP.setText("");
    consolePort.setText("0");
    localPort.setText("0");
    suppressDate.setText("");
    consoleTimeout.setText("600");
    powerType.setSelectedIndex(4);
    powerIP.setText("");
    powerPort.setText("0");
    
    // Do not change
    ID.setText("");
    UC.Look(error);
    error.setText("");
    addUpdate.setEnabled(false);
    addUpdate.setText("Enter a TCPStation");
    
    // Clear all fields for this form, use UC.look to set background
   // UC.Look(isStation);
    //isStation.setSelected(true);
    //description.setText("");
    
  }
 
  private TCPStation newOne() {
    return new TCPStation(0, ((String) tcpstation.getSelectedItem()).toUpperCase(), 1, 0, 
       "000.000.000.000",  0,  600, 0, "Invalid", 0, new java.sql.Date(10000L),
       1,1,1,                          // gomberg, rollback, ctrlq
       "000.000.000.000",  0,  600, // Console parameters
       "RTS-PCB", "000.000.000.000",  0,             // power control parameters
       "Invalid", "Invalid", "Invalid", "Invalid",// rts port type
       0, 0, 0, 0                           // rts cllient type
        ,"","",0 ,0 ,0                 // Q330, tunnelport string, allowpoc, no tunnel
    );
  }
  
  private void oldOne(String p) throws SQLException {
    obj = new DBObject(DBConnectionThread.getThread(DBConnectionThread.getDBSchema()), DBConnectionThread.getDBSchema(), "tcpstation","tcpstation",p);

    // Here set all of the form fields to data from the DBObject
    //      TCPStation.setText(obj.getString("TCPStation"));
    //description.setText(obj.getString("description"));
    ipadr.setText(obj.getString("ipadr"));
    port.setText(obj.getInt("port")+"");
    timeout.setText(obj.getInt("timeout")+"");
    localPort.setText(obj.getInt("localPort")+"");
    clientServer.setSelectedItem(obj.getString("state"));
    CpuPanel.setJComboBoxToID(cpu, obj.getInt("cpuID"));
    sdate = obj.getDate("suppressDate");
    suppressDate.setText(Util.dateToString(sdate));
    if(FUtil.isTrue(obj.getString("gomberg")) == 0) gomberg.setSelected(false);
    else gomberg.setSelected(true);
    if(FUtil.isTrue(obj.getString("rollback")) == 0) rollback.setSelected(false);
    else rollback.setSelected(true);
    if(FUtil.isTrue(obj.getString("ctrlq")) == 0) ctrlq.setSelected(false);
    else ctrlq.setSelected(true);
    consoleIP.setText(obj.getString("consoleIP"));
    consolePort.setText(obj.getInt("consolePort")+"");
    consoleTimeout.setText(obj.getInt("consoleTimeout")+"");
    powerType.setSelectedItem(obj.getString("powerType"));
    powerIP.setText(obj.getString("powerIP"));
    powerPort.setText(obj.getInt("powerPort")+"");
    rtsport1=obj.getString("rtsport1");
    rtsport2=obj.getString("rtsport2");
    rtsport3=obj.getString("rtsport3");
    rtsport4=obj.getString("rtsport4");
    

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
    tcpstation = getJComboBox();
    addUpdate = new javax.swing.JButton();
    ID = new javax.swing.JTextField();
    jLabel9 = new javax.swing.JLabel();
    error = new javax.swing.JTextField();
    labIpadr = new javax.swing.JLabel();
    ipadr = new javax.swing.JTextField();
    labPort = new javax.swing.JLabel();
    port = new javax.swing.JTextField();
    labTimeout = new javax.swing.JLabel();
    timeout = new javax.swing.JTextField();
    labClientServer = new javax.swing.JLabel();
    labsuppress = new javax.swing.JLabel();
    suppressDate = new javax.swing.JTextField();
    clientServer = FUtil.getEnumJComboBox(DBConnectionThread.getConnection("anss"), "tcpstation", "state");
    labNode = new javax.swing.JLabel();
    labWarnClient = new javax.swing.JLabel();
    labconsoleIP = new javax.swing.JLabel();
    consoleIP = new javax.swing.JTextField();
    labConsolePort = new javax.swing.JLabel();
    consolePort = new javax.swing.JTextField();
    labConsoleTO = new javax.swing.JLabel();
    consoleTimeout = new javax.swing.JTextField();
    labPowerType = new javax.swing.JLabel();
    powerType = FUtil.getEnumJComboBox(DBConnectionThread.getConnection("anss"), "tcpstation","powertype");
    labPowerIP = new javax.swing.JLabel();
    powerIP = new javax.swing.JTextField();
    labPowerPort = new javax.swing.JLabel();
    powerPort = new javax.swing.JTextField();
    labConsole = new javax.swing.JLabel();
    jSeparator1 = new javax.swing.JSeparator();
    jSeparator2 = new javax.swing.JSeparator();
    jSeparator3 = new javax.swing.JSeparator();
    jSeparator4 = new javax.swing.JSeparator();
    jSeparator5 = new javax.swing.JSeparator();
    jSeparator6 = new javax.swing.JSeparator();
    jSeparator7 = new javax.swing.JSeparator();
    jSeparator8 = new javax.swing.JSeparator();
    gomberg = new javax.swing.JRadioButton();
    rollback = new javax.swing.JRadioButton();
    ctrlq = new javax.swing.JRadioButton();
    labLocalPort = new javax.swing.JLabel();
    localPort = new javax.swing.JTextField();
    cpu = CpuPanel.getJComboBox();

    setLayout(new java.awt.GridBagLayout());

    jLabel1.setText("Station :");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    add(jLabel1, gridBagConstraints);

    tcpstation.setEditable(true);
    tcpstation.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        tcpstationActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(tcpstation, gridBagConstraints);

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
    ID.setMinimumSize(new java.awt.Dimension(35, 22));
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
    error.setMinimumSize(new java.awt.Dimension(250, 22));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(error, gridBagConstraints);

    labIpadr.setText("IP Address :");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 2;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    add(labIpadr, gridBagConstraints);

    ipadr.setColumns(16);
    ipadr.setText("000.000.000.000");
    ipadr.setMinimumSize(new java.awt.Dimension(100, 22));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 2;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(ipadr, gridBagConstraints);

    labPort.setText("Port (0=server) :");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 2;
    gridBagConstraints.gridy = 2;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    add(labPort, gridBagConstraints);

    port.setColumns(6);
    port.setText("0");
    port.setMinimumSize(new java.awt.Dimension(35, 22));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 3;
    gridBagConstraints.gridy = 2;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(port, gridBagConstraints);

    labTimeout.setText("Timeout (secs) :");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 3;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    add(labTimeout, gridBagConstraints);

    timeout.setColumns(8);
    timeout.setMinimumSize(new java.awt.Dimension(35, 22));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 3;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(timeout, gridBagConstraints);

    labClientServer.setText("Type Connection:");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 2;
    gridBagConstraints.gridy = 3;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    add(labClientServer, gridBagConstraints);

    labsuppress.setText("Supress Until :");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 2;
    gridBagConstraints.gridy = 4;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    add(labsuppress, gridBagConstraints);

    suppressDate.setColumns(12);
    suppressDate.setMinimumSize(new java.awt.Dimension(100, 22));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 3;
    gridBagConstraints.gridy = 4;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(suppressDate, gridBagConstraints);
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 3;
    gridBagConstraints.gridy = 3;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(clientServer, gridBagConstraints);

    labNode.setText("NSN node (if client) :");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 4;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    add(labNode, gridBagConstraints);

    labWarnClient.setText("(if console server)");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 8;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    add(labWarnClient, gridBagConstraints);

    labconsoleIP.setText("Console IP :");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 9;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    add(labconsoleIP, gridBagConstraints);

    consoleIP.setColumns(16);
    consoleIP.setText("000.000.000.000");
    consoleIP.setMinimumSize(new java.awt.Dimension(100, 22));
    consoleIP.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        consoleIPActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 9;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(consoleIP, gridBagConstraints);

    labConsolePort.setText("Console Port :");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 2;
    gridBagConstraints.gridy = 9;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    add(labConsolePort, gridBagConstraints);

    consolePort.setColumns(5);
    consolePort.setMinimumSize(new java.awt.Dimension(35, 22));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 3;
    gridBagConstraints.gridy = 9;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(consolePort, gridBagConstraints);

    labConsoleTO.setText("Console Timeout (secs):");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 10;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    add(labConsoleTO, gridBagConstraints);

    consoleTimeout.setColumns(5);
    consoleTimeout.setText("600");
    consoleTimeout.setMinimumSize(new java.awt.Dimension(35, 22));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 10;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(consoleTimeout, gridBagConstraints);

    labPowerType.setText("Power Type :");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 13;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    add(labPowerType, gridBagConstraints);
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 13;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(powerType, gridBagConstraints);

    labPowerIP.setText("Power IP :");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 14;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    add(labPowerIP, gridBagConstraints);

    powerIP.setMinimumSize(new java.awt.Dimension(100, 22));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 14;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(powerIP, gridBagConstraints);

    labPowerPort.setText("Power Port :");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 2;
    gridBagConstraints.gridy = 13;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    add(labPowerPort, gridBagConstraints);

    powerPort.setColumns(6);
    powerPort.setMinimumSize(new java.awt.Dimension(35, 22));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 3;
    gridBagConstraints.gridy = 13;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(powerPort, gridBagConstraints);

    labConsole.setText("Power Control");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 12;
    add(labConsole, gridBagConstraints);

    jSeparator1.setMinimumSize(new java.awt.Dimension(200, 8));
    jSeparator1.setPreferredSize(new java.awt.Dimension(200, 2));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 11;
    add(jSeparator1, gridBagConstraints);

    jSeparator2.setMinimumSize(new java.awt.Dimension(200, 8));
    jSeparator2.setPreferredSize(new java.awt.Dimension(200, 2));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 7;
    add(jSeparator2, gridBagConstraints);

    jSeparator3.setMinimumSize(new java.awt.Dimension(100, 8));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 7;
    add(jSeparator3, gridBagConstraints);

    jSeparator4.setMinimumSize(new java.awt.Dimension(100, 8));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 2;
    gridBagConstraints.gridy = 7;
    add(jSeparator4, gridBagConstraints);

    jSeparator5.setMinimumSize(new java.awt.Dimension(100, 8));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 3;
    gridBagConstraints.gridy = 7;
    add(jSeparator5, gridBagConstraints);

    jSeparator6.setMinimumSize(new java.awt.Dimension(100, 8));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 11;
    add(jSeparator6, gridBagConstraints);

    jSeparator7.setMinimumSize(new java.awt.Dimension(100, 8));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 2;
    gridBagConstraints.gridy = 11;
    add(jSeparator7, gridBagConstraints);

    jSeparator8.setMinimumSize(new java.awt.Dimension(100, 8));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 3;
    gridBagConstraints.gridy = 11;
    add(jSeparator8, gridBagConstraints);

    gomberg.setText("Gomberg Pkts ?");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 5;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    add(gomberg, gridBagConstraints);

    rollback.setText("Rollback Support?");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 6;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    add(rollback, gridBagConstraints);

    ctrlq.setText("Ctrl Q on connect?");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 2;
    gridBagConstraints.gridy = 5;
    add(ctrlq, gridBagConstraints);

    labLocalPort.setText("Filter Port (200n: 4=ANS, 3=VDL): :");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 2;
    gridBagConstraints.gridy = 6;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    add(labLocalPort, gridBagConstraints);

    localPort.setColumns(6);
    localPort.setMinimumSize(new java.awt.Dimension(40, 22));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 3;
    gridBagConstraints.gridy = 6;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(localPort, gridBagConstraints);
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 4;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(cpu, gridBagConstraints);
  }// </editor-fold>//GEN-END:initComponents

private void consoleIPActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_consoleIPActionPerformed
// Add your handling code here:
}//GEN-LAST:event_consoleIPActionPerformed

private void port2ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_port2ActionPerformed
// Add your handling code here:
}//GEN-LAST:event_port2ActionPerformed

  private void addUpdateActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_addUpdateActionPerformed
    // Add your handling code here:
    
    // Do not change
    if(chkForm()) return;
    try {
      String p = tcpstation.getSelectedItem().toString();
      p = p.toUpperCase();
      
      // Set all of the fields using obj.set?????("fieldNamev", newValue);
      //obj.setInt("equiptypeID", ((EquipType) equipType.getSelectedItem()).getID());
      //      obj.setInt("input_power",power.getSelectedIndex());
      if(!obj.isNew()) obj.refreshRecord();        // Refresh the result set so the sets will not fail on stale one
     
      obj.setString("TCPStation",p);             // set primary key name
      obj.setString("ipadr", ipadr.getText()); 
      
      obj.setInt("port", Integer.parseInt(port.getText())); 
      obj.setInt("timeout", Integer.parseInt(timeout.getText())); 
      obj.setInt("localPort", Integer.parseInt(localPort.getText()));
      obj.setInt("cpuID", ((Cpu) cpu.getSelectedItem()).getID());
      obj.setInt("state", clientServer.getSelectedIndex()); 
      obj.setInt("gomberg", gomberg.isSelected() ? 2 : 1); 
      obj.setInt("rollback", rollback.isSelected() ? 2 : 1); 
      obj.setInt("ctrlq", ctrlq.isSelected() ? 2 : 1); 
      if(suppressDate.getText().equals("")) 
          obj.setDate("suppressDate",new java.sql.Date( (long) 1000));
      else obj.setDate("suppressDate", sdate);
      obj.setString("consoleIP", consoleIP.getText()); 
      obj.setInt("consolePort", Integer.parseInt(consolePort.getText()));
      obj.setInt("consoleTimeout", Integer.parseInt(consoleTimeout.getText()));
      obj.setInt("powerType", powerType.getSelectedIndex()); 
      obj.setString("powerIP", powerIP.getText());
      obj.setInt("powerPort", Integer.parseInt(powerPort.getText()));
      Util.prt("state="+clientServer.getSelectedIndex()+" gomber="+gomberg.isSelected()+
        " rollback="+rollback.isSelected());
      obj.setInt("rtsport1",RTSStationPanel.NONE); obj.setInt("rtsport2",RTSStationPanel.NONE); 
      obj.setInt("rtsport3",RTSStationPanel.NONE); obj.setInt("rtsport4",RTSStationPanel.NONE); 
      obj.setInt("rtsclient1",0); obj.setInt("rtsclient2",0); 
      obj.setInt("rtsclient3",0); obj.setInt("rtsclient4",0); 
      
      // Do not change
      obj.updateRecord();
      
      // Special case - we updated the DBConnectionTh so now update the FOrm stuff on VMS
      Statement stmt = DBConnectionThread.getConnection("anss").createStatement();   // used for query
      String s = "SELECT * FROM tcpstation where tcpstation = "
              + Util.sqlEscape(p);
      ResultSet rs = stmt.executeQuery(s);
      rs.next();
      TCPStation loc = new TCPStation(rs);    // read back what we just updated 
      loc.sendForm();                         // SEnd it to VMS
      
      SNWStationPanel.setSNWGroupsFromANSS(p);
      
      getJComboBox(tcpstation);
      clearScreen();
      tcpstation.setSelectedIndex(-1);
      
    }  catch  (SQLException  E)
    { Util.SQLErrorPrint(E,"TCPStation: update failed partno="+tcpstation.getSelectedItem());
      error.setText("Could Not Update File!");
      error.setBackground(UC.red);
      addUpdate.setEnabled(true);
    }      
  }//GEN-LAST:event_addUpdateActionPerformed

private void tcpstationActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_tcpstationActionPerformed
    // Add your handling code here:
   find();
 
 
}//GEN-LAST:event_tcpstationActionPerformed
  
  
  // Variables declaration - do not modify//GEN-BEGIN:variables
  private javax.swing.JTextField ID;
  private javax.swing.JButton addUpdate;
  private javax.swing.JComboBox clientServer;
  private javax.swing.JTextField consoleIP;
  private javax.swing.JTextField consolePort;
  private javax.swing.JTextField consoleTimeout;
  private javax.swing.JComboBox cpu;
  private javax.swing.JRadioButton ctrlq;
  private javax.swing.JTextField error;
  private javax.swing.JRadioButton gomberg;
  private java.awt.GridBagLayout gridBagLayout1;
  private javax.swing.JTextField ipadr;
  private javax.swing.JLabel jLabel1;
  private javax.swing.JLabel jLabel9;
  private javax.swing.JSeparator jSeparator1;
  private javax.swing.JSeparator jSeparator2;
  private javax.swing.JSeparator jSeparator3;
  private javax.swing.JSeparator jSeparator4;
  private javax.swing.JSeparator jSeparator5;
  private javax.swing.JSeparator jSeparator6;
  private javax.swing.JSeparator jSeparator7;
  private javax.swing.JSeparator jSeparator8;
  private javax.swing.JLabel labClientServer;
  private javax.swing.JLabel labConsole;
  private javax.swing.JLabel labConsolePort;
  private javax.swing.JLabel labConsoleTO;
  private javax.swing.JLabel labIpadr;
  private javax.swing.JLabel labLocalPort;
  private javax.swing.JLabel labNode;
  private javax.swing.JLabel labPort;
  private javax.swing.JLabel labPowerIP;
  private javax.swing.JLabel labPowerPort;
  private javax.swing.JLabel labPowerType;
  private javax.swing.JLabel labTimeout;
  private javax.swing.JLabel labWarnClient;
  private javax.swing.JLabel labconsoleIP;
  private javax.swing.JLabel labsuppress;
  private javax.swing.JTextField localPort;
  private javax.swing.JTextField port;
  private javax.swing.JTextField powerIP;
  private javax.swing.JTextField powerPort;
  private javax.swing.JComboBox powerType;
  private javax.swing.JRadioButton rollback;
  private javax.swing.JTextField suppressDate;
  private javax.swing.JComboBox tcpstation;
  private javax.swing.JTextField timeout;
  // End of variables declaration//GEN-END:variables
  /** Creates new form TCPStationPanel */
  public TCPStationPanel() {
    initComponents();
    tcpstation.setSelectedIndex(-1);    // Set selected type
    UC.Look(this);                    // Set color background
    clearScreen();                    // Start with empty screen
  }
  
  // No changes needed.
 public static JComboBox getJComboBox() { JComboBox b = new JComboBox(); getJComboBox(b); return b;}
  
  public static void getJComboBox(JComboBox b) {
    b.removeAllItems();
    makeTCPStations();
    for (int i=0; i< v.size(); i++) {
      b.addItem( v.get(i));
    }
    b.setMaximumRowCount(30);
  }
  
  // Given a JComboBox from getJComboBox, set selected item to match database ID
  public static void setJComboBoxToID(JComboBox b, int ID) {
    b.setSelectedIndex(-1);
    //Util.prt("TCPStationPanel.setJComboBoxToID id="+ID);
    for(int i=0; i<b.getItemCount(); i++) 
      if(((TCPStation) b.getItemAt(i)).getID() == ID) {
          b.setSelectedIndex(i);
          //Util.prt("TCPStationPanel.setJComboBoxToID id="+ID+" found at "+i);
      }
  }  
  
  // IF you have the maintcodeID and need to know where it is in the combo box, call this!
  public static int getJComboBoxIndex(int ID) {
    for(int i=0; i<v.size(); i++) if( ID == ((TCPStation) v.get(i)).getID()) return i;
    return -1;
  }
  public static ArrayList<TCPStation> getArrayList() {
    makeTCPStations();
    return v;
  }
  public static void reload() {
    if(v != null) v.clear();
    v = null;
    makeTCPStations();
  }
  public static TCPStation getTCPStationWithIP(String ip) {
    ip = Util.cleanIP(ip);
    makeTCPStations();
    for(int i=0; i<v.size(); i++) {
      if(Util.cleanIP(v.get(i).getIP()).equals(ip)) 
        return v.get(i);
    }
    return null;
  }
    
  // This routine should only need tweaking if key field is not same as table name
  private static void makeTCPStations() {
    if (v != null) return;
    v = new ArrayList<TCPStation>(100);
    try {
      if(DBConnectionThread.getConnection(DBConnectionThread.getDBSchema()) == null) {
        try {
               DBConnectionThread temp = new DBConnectionThread(Util.getProperty("DBServer"), "readonly","anss",
                false, false, DBConnectionThread.getDBSchema(), Util.getOutput());
         if(!temp.waitForConnection())
           if(!temp.waitForConnection()) {
             Util.prt("*** could not make backup in makeTCPStatoins() connection as "+DBConnectionThread.getDBSchema());
           }
        }
        catch(InstantiationException e) {

        }
      }
      try (Statement stmt = DBConnectionThread.getConnection(DBConnectionThread.getDBSchema()).createStatement()) {
        String s = "SELECT * FROM anss.tcpstation ORDER BY tcpstation";
        try (ResultSet rs = stmt.executeQuery(s)) {
          while (rs.next()) {
            TCPStation loc = new TCPStation(rs);
            //Util.prt("MakeTCPStation() i="+v.size()+" is "+loc.getTCPStation());
            v.add(loc);
          }
        }
      }
    }
    catch (SQLException e) {
      Util.SQLErrorPrint(e,"makeTCPStations() on table SQL failed");
    }    
  }
  
  // No changes needed
  private void find() {
    if(tcpstation == null) return;
    TCPStation l;
    if(tcpstation.getSelectedIndex() == -1) {
      if(tcpstation.getSelectedItem() == null) return;
      l = newOne();
    } 
    else {
      l = (TCPStation) tcpstation.getSelectedItem();
    }
      
    if(l == null) return;
    String p = l.getTCPStation();
    if(p == null) return;
    if(p.equals("")) {
      addUpdate.setEnabled(false);
      addUpdate.setText("Enter a TCPStation!");
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
          if( rtsport1.equals("None") && rtsport2.equals("None") &&
              rtsport2.equals("None") && rtsport4.equals("None")) {
            addUpdate.setText("Update "+p);
            addUpdate.setEnabled(true);
          } else {
            error.setText("In RTS mode, you cannot modify here.");
            error.setBackground(UC.red);
            addUpdate.setText("RTS Mode - No Mods");
          }
      }
      ID.setText(""+obj.getInt("ID"));    // Info only, show ID
    }  catch  (SQLException  E)
    { Util.SQLErrorPrint(E,"TCPStation: find failed TCPStation="+p);
      clearScreen();
      ID.setText("NEW!");
      
      // Set up for "NEW" - clear fields etc.
      error.setText("NOT found - assume NEW");
      error.setBackground(UC.yellow);
      addUpdate.setText("Add "+p);
      addUpdate.setEnabled(true);
//      TCPStation.setSelectedItem(((TCPStation) TCPStation.getSelectedItem()).toString().toUpperCase());
    }
    
  }
  /** This main displays the form Pane by itself
   *@param args command line args ignored*/
  public static void main(String args[]) {
    DBConnectionThread jcjbl;
    Util.init(gov.usgs.anss.util.UC.getPropertyFilename());
    gov.usgs.anss.util.UC.init();
    try {
        // Make test DBconnection for form
      jcjbl = new DBConnectionThread(DBConnectionThread.getDBServer(),DBConnectionThread.getDBCatalog(),
              gov.usgs.anss.util.UC.defaultUser(),gov.usgs.anss.util.UC.defaultPassword(), true, false, DBConnectionThread.getDBSchema(), DBConnectionThread.getDBVendor());
      if(!DBConnectionThread.waitForConnection(DBConnectionThread.getDBSchema()))
        if(!DBConnectionThread.waitForConnection(DBConnectionThread.getDBSchema()))
          if(!DBConnectionThread.waitForConnection(DBConnectionThread.getDBSchema()))
          {Util.prt("Could not connect to DB "+jcjbl); System.exit(1);}
      Show.inFrame(new TCPStationPanel(), gov.usgs.anss.util.UC.XSIZE, gov.usgs.anss.util.UC.YSIZE);
    }
    catch(InstantiationException e) {}


  }
}
