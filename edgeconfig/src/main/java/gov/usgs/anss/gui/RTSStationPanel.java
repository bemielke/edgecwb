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
import gov.usgs.anss.guidb.CommLink;
import gov.usgs.anss.db.DBConnectionThread;
import gov.usgs.anss.db.DBObject;
import gov.usgs.edge.snw.SNWStationPanel;
import java.awt.Color;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import javax.swing.JComboBox;
import javax.swing.JRadioButton;
import gov.usgs.anss.util.ErrorTrack;
import gov.usgs.anss.util.FUtil;
import gov.usgs.anss.util.Util;
import gov.usgs.anss.util.Show;

/**

 * @author  ketchum
 * requirements : 
 * The key for the file must be the name of the table and of the
 * JComboBox variable this variable is called "location" in the initial form.  It
 * is pre
 * 1)  Rename the location JComboBox to the "key" ($name) value.  This should start Lower
 *      case.
 * 2)  The table name should match this key name and be all lower case.
 * 3)  Change all $Name to ClassName of underlying data (case sensitive!)
 * 4)  Change all $name key value (the JComboBox above)
 * 5)  clearScreen should update swing components to new defaults
 * 6)  chkForm should check all field for validity.  The errorTrack variable is
 *      used to track multiple errors.  In FUtil.chk* handle checks of the various
 *      variable types.  Date, double, and IP etc return the "canonical" form for 
 *      the variable.  These should be stored in a class variable so the latest one
 *      from "chkForm()" are available to the update writer.
 * 7) newone() should be updated to create a new instance of the underlying data class.
 * 8) oldone() get data from database and update all swing elements to correct values
 * 9) addUpdateAction() add data base set*() operations updating all fields in the DB
 *10) $NamePanel() constructor - good place to change backgrounds using UC.Look() any
 *    other weird startup stuff.
 *
 * local variable error - Must be a JTextField for posting error communications
 * local variable updateAdd - Must be the JButton for user clicks to try to post the data
 * ID - JTextField which must be non-editable for posting Database IDs
 * 
 *ENUM notes :  the ENUM are read from the database as type String.  So the constructor
 * should use the strings to set the indexes.  However, the writes to the database are
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


public final class RTSStationPanel extends javax.swing.JPanel {
  final public static int RTS=1,CONSOLE=2,DATA=3,GPS=4,MSHEAR=5, NONE=6;
  // NOTE : here define all variables general.  "ArrayList tcpstations" is used for main Comboboz
  private DBObject obj;
  private final ErrorTrack err=new ErrorTrack();
  private int rtsport,consoleport,gpsport,mshearport,dataport;
  private String ip;
  private String mask;
  private boolean initiating;
  
  // Here are the local variables
  private java.sql.Date sdate;              // suppress date after verification
  

  // This routine must validate all fields on the form.  The err (ErrorTrack) variable
  // is used by the FUtil to verify fields.  Use FUTil or custom code here for verifications
  private boolean chkForm() {
    // Do not change
    if(initiating || RTSStation == null) return true;
   err.reset();
   UC.Look(error);
   error.setText("");
   
   // Your error checking code goes here setting the local variables to valid settings

   //Util.prt("chkForm RTSStation");
   FUtil.chkJComboBox(commlink,"Comm Link",err);
   if(commlink.getSelectedIndex() != -1) 
     AnssRules.chkIPMatchesCommlink(ipadr.getText(), (CommLink) commlink.getSelectedItem(),err);
   FUtil.chkJComboBox(commlinkOverride,"Comm Link Override",err);
   if(commlinkOverride.getSelectedIndex() != -1) 
     AnssRules.chkIPMatchesCommlink(ipadr.getText(), (CommLink) commlinkOverride.getSelectedItem(),err);
   
   FUtil.chkJComboBox(port1, "Port1 Connection", err);
   FUtil.chkJComboBox(port2, "Port2 Connection", err);
   FUtil.chkJComboBox(port3, "Port3 Connection", err);
   FUtil.chkJComboBox(port4, "Port4 Connection", err);
   Cpu cpu = (Cpu) cpuData.getSelectedItem();
   if(q330Enable.isSelected()) {
      if(cpuData.getSelectedIndex() == -1) 
      { err.set(true);
        err.appendText("A Q330 CPU should be chosen.");
      }
      else if( cpu.getCpu().startsWith("gacq") || cpu.getCpu().equalsIgnoreCase("glab") || cpu.getCpu().equalsIgnoreCase("vdldfc9")) {
        Util.prt("Q330 CPU is allowed ="+cpu.getCpu());
      }
      else 
      { err.set(true);
        err.appendText("Q330 CPU is not allowed");
      }
   }
   else if(q330.getText().length() > 0 && cpuData.getSelectedIndex() == -1) {
     err.set(true);
     err.appendText("A Q330 CPU must be set.");
   }
   if(port1.getSelectedIndex() == MSHEAR || port2.getSelectedIndex() == MSHEAR ||
      port3.getSelectedIndex() == MSHEAR ||port4.getSelectedIndex() == MSHEAR 
       ) {
      mshearport = FUtil.chkInt(mshearPort, err,2000, 22000);
      if(cpuData.getSelectedIndex() == -1) 
      { err.set(true);
        err.appendText("A MShear CPU should be chosen.");
      }
      else if( !((Cpu) cpuData.getSelectedItem()).isMShearAllowed())
      { err.set(true);
        err.appendText("MShear CPU is not allowed");
      }
   } else mshearport=-1;
   if( port1.getSelectedIndex() == DATA  || 
      port2.getSelectedIndex() == DATA ||
      port3.getSelectedIndex() == DATA ||
      port4.getSelectedIndex() == DATA ) {
      if(cpuData.getSelectedIndex() == -1) 
      { err.set(true);
        err.appendText("A data CPU should be chosen.");
      }
      else if(!((Cpu) cpuData.getSelectedItem()).isDataAllowed())
      { err.set(true);
        err.appendText("Data CPU is not allowed");
      }
   }
   ip = FUtil.chkIP(ipadr, err);
   mask = FUtil.chkIP(ipmask, err);
   sdate = FUtil.chkDate(suppressDate,err);
   //Util.prt("chkform ip="+ip);
   
   // For this form either all RTS are set to know states or none of them is
   if(port1.getSelectedIndex() == 1) {
     
   }
   else if( RTSStation.getSelectedIndex() != -1 &&
    RTSStation.getSelectedItem().toString().substring(0,3).equalsIgnoreCase("RTS")) 
   {      // so all should be selected
       
   } else if(port1.getSelectedIndex() == 6) {
       if(port2.getSelectedIndex() != 6 ||port3.getSelectedIndex() != 6 ||
          port4.getSelectedIndex() != 6 ) {
              err.set(true);
              err.appendText("An non-RTS must have all 4 ports set to 'None'");
       }
   } else {
      err.set(true);
      err.appendText("Port 1 must be 'RTS' or 'None'"); 
   }
    // No CHANGES : If we found an error, color up error box
   //Util.prt("chkform err="+err.isSet()+" text="+err.getText());
    if(err.isSet() ) {
      if(!ID.getText().equals("")) {
        error.setText(err.getText());
        error.setBackground(UC.red);
      }
      addUpdate.setEnabled(false);
      programQ330.setEnabled(false);
      programRTS.setEnabled(false);
      programRTSnoreboot.setEnabled(false);
      programCommand.setEnabled(false);
    } else {
      programQ330.setEnabled(true);
      programRTS.setEnabled(true);
      programRTSnoreboot.setEnabled(true);
      addUpdate.setEnabled(true);
      programCommand.setEnabled(true);
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
    programRTS.setEnabled(false);
    programQ330.setEnabled(false);
    programRTSnoreboot.setEnabled(false);
    programCommand.setEnabled(false);
    addUpdate.setText("Enter a RTSStation");
    
    // Clear all fields for this form, This should set the defaults for each field
    port1Text.setText("");
    port2Text.setText("");
    port3Text.setText("");
    port4Text.setText("");
    tunnelPorts.setText("");
    q330.setText("");
    suppressDate.setText("");
    commlink.setSelectedIndex(-1);
    commlinkOverride.setSelectedIndex(-1);
    ipadr.setText("");
    ipmask.setText("");
    port1.setSelectedIndex(NONE);
    port2.setSelectedIndex(NONE);
    port3.setSelectedIndex(NONE);
    port4.setSelectedIndex(NONE);
    port1Client.setSelected(false);
    port2Client.setSelected(false);
    port3Client.setSelected(false);
    port4Client.setSelected(false);    
    labMshearPort.setVisible(false);
    mshearPort.setVisible(false);
    labNode.setVisible(false);
    cpuData.setVisible(true);
    mshearPort.setText("");
    q330Enable.setSelected(false);
    tunnelPorts.setVisible(false);
    labTunnelPorts.setVisible(false);
    labHelp.setVisible(false);
    portHelp.setVisible(false);
    //q330.setVisible(false);
    //labQ330.setVisible(false);
    //routerConfig.setVisible(false);
    routerConfig.setSelected(false);
    useBH.setSelected(false);
  }
 
   private TCPStation newOne() {
    
    return new TCPStation(0, ((String) RTSStation.getSelectedItem()).toUpperCase(),
   0,0,"000.000.000.000",  0,  600, 2004,  "Unknown", 4, new java.sql.Date((long) 1000),
       1,1,1,                          // gomberg, rollback, ctrlq
       "000.000.000.000",  0,  600, // Console parameters
       "RTS-PCB", "000.000.000.000",  0,            // power control parameters
       "RTS","Console","GPS","Data",  // RTS ports types
       0,1,0,1                              // RTS clients
        ,"","",0,0, 0                   // q330, tunnel port and allow poc
    );
 
  }
   
  
  private void oldOne(String p) throws SQLException {
    Util.prta("RTSStationPanel oldone() try to find station="+p);
    obj = new DBObject(DBConnectionThread.getThread(DBConnectionThread.getDBSchema()), DBConnectionThread.getDBSchema(), "tcpstation","TCPStation",p);
    
    if(obj.isNew()) {
      Util.prta("object is new="+p);   
    }
    else {
      Util.prta("object is old="+p);
      // Here set all of the form fields to data from the DBObject
      //      RTSStation.setText(obj.getString("RTSStation"));
      //description.setText(obj.getString("description"));
      ipadr.setText(obj.getString("ipadr"));
      ipmask.setText(obj.getString("ipmask"));
      if(obj.getInt("commlinkID") <= 0) commlink.setSelectedIndex(-1);
      else CommLinkPanel.setJComboBoxToID(commlink, obj.getInt("commlinkID"));
      if(obj.getInt("commlinkOverrideID") <= 0) commlinkOverride.setSelectedIndex(-1);
      else CommLinkPanel.setJComboBoxToID(commlinkOverride,obj.getInt("commlinkOverrideID"));
      Util.prt("set commlinkid"+obj.getInt("commlinkID")+" over="+obj.getInt("commlinkOverrideID"));
      if(obj.getInt("cpuID") == 0) cpuData.setSelectedIndex(-1);
      else CpuPanel.setJComboBoxToID(cpuData,obj.getInt("cpuID"));
      port1.setSelectedItem(obj.getString("rtsport1"));
      port2.setSelectedItem(obj.getString("rtsport2"));
      port3.setSelectedItem(obj.getString("rtsport3"));
      port4.setSelectedItem(obj.getString("rtsport4"));
      
      Util.prt("1="+port1.getSelectedIndex()+"1="+port1.getSelectedIndex()+" 2="+port2.getSelectedIndex()+
              " 3="+port3.getSelectedIndex()+" 4="+port4.getSelectedIndex());

      //suppressDate.setText(Util.dateToString(obj.getDate("suppressDate")));
      suppressDate.setText(""+obj.getDate("suppressDate"));
      port1Client.setSelected( obj.getInt("rtsclient1") != 0);
      port2Client.setSelected( obj.getInt("rtsclient2") != 0);
      port3Client.setSelected( obj.getInt("rtsclient3") != 0);
      port4Client.setSelected( obj.getInt("rtsclient4") != 0);
      q330Enable.setSelected( obj.getString("q330Tunnel").equals("True") );   // 2 is true, 1 is false
      useBH.setSelected( (obj.getInt("usebh") != 0));
    }
     cpuData.setVisible(true);   // Assume no CPU visible, changed to allow Q330 CPU usage
     labNode.setVisible(false);
     q330Enable.setVisible(true);
     tunnelPorts.setText(obj.getString("tunnelports"));
     q330.setText(obj.getString("q330"));
     if(obj.getInt("allowpoc") != 0) routerConfig.setSelected(true);
     else routerConfig.setSelected(false);
     
     if(q330Enable.isSelected()) {
       //labTunnelPorts.setVisible(true);
       //tunnelPorts.setVisible(true);
       tunnelPorts.setText(obj.getString("tunnelports"));
       //labHelp.setVisible(true);
       //portHelp.setVisible(true);
       labQ330.setVisible(true);
       q330.setVisible(true);
       routerConfig.setVisible(true);
     }
     if(port1.getSelectedIndex() == MSHEAR || port2.getSelectedIndex() == MSHEAR ||
        port3.getSelectedIndex() == MSHEAR ||port4.getSelectedIndex() == MSHEAR) {
        mshearPort.setText(""+obj.getInt("localport"));
        labMshearPort.setVisible(true);
        mshearPort.setVisible(true);
        cpuData.setVisible(true);
        labNode.setVisible(true);
     } else {
       mshearPort.setText("");
       labMshearPort.setVisible(false);
       mshearPort.setVisible(false);
     }
     if( port1.getSelectedIndex() == DATA  || 
        port2.getSelectedIndex() == DATA  ||
        port3.getSelectedIndex() == DATA  ||
        port4.getSelectedIndex() == DATA ) {

        cpuData.setVisible(true);
        labNode.setVisible(true);

     }
    doPort(1,port1, port1Client,  port1Text);
   doPort(2,port2, port2Client,  port2Text);
   doPort(3,port3, port3Client,  port3Text);
   doPort(4,port4, port4Client,  port4Text);
   chkForm();
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
    jTextField1 = new javax.swing.JTextField();
    jScrollPane2 = new javax.swing.JScrollPane();
    jList1 = new javax.swing.JList();
    jLabel1 = new javax.swing.JLabel();
    RTSStation = getJComboBox();
    addUpdate = new javax.swing.JButton();
    ID = new javax.swing.JTextField();
    jLabel9 = new javax.swing.JLabel();
    error = new javax.swing.JTextField();
    port1 = FUtil.getEnumJComboBox(DBConnectionThread.getConnection("anss"), "tcpstation","rtsport1");
    port2 = FUtil.getEnumJComboBox(DBConnectionThread.getConnection("anss"), "tcpstation","rtsport2");
    port3 = FUtil.getEnumJComboBox(DBConnectionThread.getConnection("anss"), "tcpstation","rtsport3");
    port4 = FUtil.getEnumJComboBox(DBConnectionThread.getConnection("anss"), "tcpstation","rtsport4");
    labPort1 = new javax.swing.JLabel();
    labPort2 = new javax.swing.JLabel();
    labPort3 = new javax.swing.JLabel();
    labPort4 = new javax.swing.JLabel();
    port1Client = new javax.swing.JRadioButton();
    port2Client = new javax.swing.JRadioButton();
    port3Client = new javax.swing.JRadioButton();
    port4Client = new javax.swing.JRadioButton();
    port1Text = new javax.swing.JTextField();
    port2Text = new javax.swing.JTextField();
    port3Text = new javax.swing.JTextField();
    port4Text = new javax.swing.JTextField();
    labSuppress = new javax.swing.JLabel();
    suppressDate = new javax.swing.JTextField();
    labIPadr = new javax.swing.JLabel();
    ipadr = new javax.swing.JTextField();
    labNode = new javax.swing.JLabel();
    mshearPort = new javax.swing.JTextField();
    labMshearPort = new javax.swing.JLabel();
    programRTS = new javax.swing.JButton();
    jScrollPane1 = new javax.swing.JScrollPane();
    status = new javax.swing.JTextArea();
    cpuData = CpuPanel.getJComboBox();
    comlinkLab = new javax.swing.JLabel();
    commlink = CommLinkPanel.getJComboBox();
    programCommand = new javax.swing.JButton();
    cpuSuffix = new javax.swing.JTextField();
    commlinkOverride = CommLinkPanel.getJComboBox();
    programRTSnoreboot = new javax.swing.JButton();
    labCommlinkOverride = new javax.swing.JLabel();
    programQ330 = new javax.swing.JButton();
    q330Enable = new javax.swing.JCheckBox();
    labTunnelPorts = new javax.swing.JLabel();
    tunnelPorts = new javax.swing.JTextField();
    labQ330 = new javax.swing.JLabel();
    q330 = new javax.swing.JTextField();
    routerConfig = new javax.swing.JRadioButton();
    portHelp = new javax.swing.JTextField();
    labHelp = new javax.swing.JLabel();
    laIIPMask = new javax.swing.JLabel();
    ipmask = new javax.swing.JTextField();
    useBH = new javax.swing.JRadioButton();

    jTextField1.setText("jTextField1");

    jList1.setModel(new javax.swing.AbstractListModel() {
      String[] strings = { "Item 1", "Item 2", "Item 3", "Item 4", "Item 5" };
      public int getSize() { return strings.length; }
      public Object getElementAt(int i) { return strings[i]; }
    });
    jScrollPane2.setViewportView(jList1);

    setLayout(new java.awt.GridBagLayout());

    jLabel1.setText("Station :");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 0;
    add(jLabel1, gridBagConstraints);

    RTSStation.setEditable(true);
    RTSStation.setToolTipText("<html>\nIP Address of the RTS. \n<p>\nIf router option is used, the IP address of the router or only equipment at site.  \n<p>\nIf 1.1.1.1 this station is no longer active.\n</html>");
    RTSStation.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        RTSStationActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(RTSStation, gridBagConstraints);

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
    ID.setMinimumSize(new java.awt.Dimension(80, 22));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 15;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(ID, gridBagConstraints);

    jLabel9.setText("ID :");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 15;
    add(jLabel9, gridBagConstraints);

    error.setColumns(40);
    error.setEditable(false);
    error.setMinimumSize(new java.awt.Dimension(275, 22));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 3;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(error, gridBagConstraints);

    port1.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        port1ActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 3;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(port1, gridBagConstraints);

    port2.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        port2ActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 4;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(port2, gridBagConstraints);

    port3.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        port3ActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 5;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(port3, gridBagConstraints);

    port4.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        port4ActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 6;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(port4, gridBagConstraints);

    labPort1.setText("RTSPort1:");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 3;
    add(labPort1, gridBagConstraints);

    labPort2.setText("RTSPort2:");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 4;
    add(labPort2, gridBagConstraints);

    labPort3.setText("RTSPort3:");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 5;
    add(labPort3, gridBagConstraints);

    labPort4.setText("RTSPort4 :");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 6;
    add(labPort4, gridBagConstraints);

    port1Client.setText("Client ?");
    port1Client.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        port1ClientActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 2;
    gridBagConstraints.gridy = 3;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(port1Client, gridBagConstraints);

    port2Client.setText("Client ?");
    port2Client.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        port2ClientActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 2;
    gridBagConstraints.gridy = 4;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(port2Client, gridBagConstraints);

    port3Client.setText("Client ?");
    port3Client.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        port3ClientActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 2;
    gridBagConstraints.gridy = 5;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(port3Client, gridBagConstraints);

    port4Client.setText("Client ?");
    port4Client.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        port4ClientActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 2;
    gridBagConstraints.gridy = 6;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(port4Client, gridBagConstraints);

    port1Text.setColumns(40);
    port1Text.setEditable(false);
    port1Text.setFont(new java.awt.Font("Courier New", 0, 12)); // NOI18N
    port1Text.setMinimumSize(new java.awt.Dimension(275, 18));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 3;
    gridBagConstraints.gridy = 3;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(port1Text, gridBagConstraints);

    port2Text.setColumns(40);
    port2Text.setEditable(false);
    port2Text.setFont(new java.awt.Font("Courier New", 0, 12)); // NOI18N
    port2Text.setMinimumSize(new java.awt.Dimension(275, 18));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 3;
    gridBagConstraints.gridy = 4;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(port2Text, gridBagConstraints);

    port3Text.setColumns(40);
    port3Text.setEditable(false);
    port3Text.setFont(new java.awt.Font("Courier New", 0, 12)); // NOI18N
    port3Text.setMinimumSize(new java.awt.Dimension(275, 18));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 3;
    gridBagConstraints.gridy = 5;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(port3Text, gridBagConstraints);

    port4Text.setEditable(false);
    port4Text.setFont(new java.awt.Font("Courier New", 0, 12)); // NOI18N
    port4Text.setMinimumSize(new java.awt.Dimension(275, 18));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 3;
    gridBagConstraints.gridy = 6;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(port4Text, gridBagConstraints);

    labSuppress.setText("SuppressDate:");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 2;
    gridBagConstraints.gridy = 13;
    add(labSuppress, gridBagConstraints);

    suppressDate.setColumns(20);
    suppressDate.setMinimumSize(new java.awt.Dimension(110, 22));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 3;
    gridBagConstraints.gridy = 13;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(suppressDate, gridBagConstraints);

    labIPadr.setText("IP Addr :");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    add(labIPadr, gridBagConstraints);

    ipadr.setColumns(19);
    ipadr.setMinimumSize(new java.awt.Dimension(140, 22));
    ipadr.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        ipadrActionPerformed(evt);
      }
    });
    ipadr.addFocusListener(new java.awt.event.FocusAdapter() {
      public void focusLost(java.awt.event.FocusEvent evt) {
        ipadrFocusLost(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(ipadr, gridBagConstraints);

    labNode.setText("CPU Node :");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 2;
    gridBagConstraints.gridy = 7;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    add(labNode, gridBagConstraints);

    mshearPort.setColumns(8);
    mshearPort.setMinimumSize(new java.awt.Dimension(80, 22));
    mshearPort.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        mshearPortActionPerformed(evt);
      }
    });
    mshearPort.addFocusListener(new java.awt.event.FocusAdapter() {
      public void focusLost(java.awt.event.FocusEvent evt) {
        mshearPortFocusLost(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 3;
    gridBagConstraints.gridy = 11;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(mshearPort, gridBagConstraints);

    labMshearPort.setText("MShear Port:");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 2;
    gridBagConstraints.gridy = 11;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    add(labMshearPort, gridBagConstraints);

    programRTS.setText("Program RTS and Reboot- takes 20 seconds");
    programRTS.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        programRTSActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 3;
    gridBagConstraints.gridy = 14;
    add(programRTS, gridBagConstraints);

    jScrollPane1.setMinimumSize(new java.awt.Dimension(375, 100));
    jScrollPane1.setPreferredSize(new java.awt.Dimension(375, 150));

    status.setColumns(30);
    status.setEditable(false);
    status.setFont(new java.awt.Font("Monospaced", 0, 12)); // NOI18N
    status.setRows(8);
    status.setBorder(javax.swing.BorderFactory.createEtchedBorder());
    status.setMinimumSize(new java.awt.Dimension(375, 125));
    jScrollPane1.setViewportView(status);

    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 3;
    gridBagConstraints.gridy = 15;
    add(jScrollPane1, gridBagConstraints);

    cpuData.setToolTipText("Select the computer to do acquistion on this site.");
    cpuData.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        cpuDataActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 3;
    gridBagConstraints.gridy = 7;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(cpuData, gridBagConstraints);

    comlinkLab.setText("Satellite/Net:");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 2;
    gridBagConstraints.gridy = 2;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    add(comlinkLab, gridBagConstraints);

    commlink.setToolTipText("The Satellite or primary backhaul to use for this data.");
    commlink.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        commlinkActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 3;
    gridBagConstraints.gridy = 2;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(commlink, gridBagConstraints);

    programCommand.setText("Program RTS Cmnd/Status");
    programCommand.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        programCommandActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 16;
    add(programCommand, gridBagConstraints);

    cpuSuffix.setMinimumSize(new java.awt.Dimension(20, 22));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 3;
    gridBagConstraints.gridy = 16;
    add(cpuSuffix, gridBagConstraints);

    commlinkOverride.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        commlinkOverrideActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 3;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(commlinkOverride, gridBagConstraints);

    programRTSnoreboot.setText("Program RTS ports-No Reboot");
    programRTSnoreboot.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        programRTSnorebootActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 3;
    gridBagConstraints.gridy = 10;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(programRTSnoreboot, gridBagConstraints);

    labCommlinkOverride.setText("Alt BackHaul:");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 2;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    add(labCommlinkOverride, gridBagConstraints);

    programQ330.setText("Program Tunnel to RTS");
    programQ330.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        programQ330ActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 10;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(programQ330, gridBagConstraints);

    q330Enable.setText("RTS/Q330 Tunnel Enabled");
    q330Enable.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        q330EnableActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 7;
    add(q330Enable, gridBagConstraints);

    labTunnelPorts.setText("Port:IP/qp,..:");
    labTunnelPorts.setMinimumSize(new java.awt.Dimension(84, 16));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 2;
    gridBagConstraints.gridy = 8;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    add(labTunnelPorts, gridBagConstraints);

    tunnelPorts.setColumns(40);
    tunnelPorts.setFont(new java.awt.Font("Courier New", 0, 12)); // NOI18N
    tunnelPorts.setMinimumSize(new java.awt.Dimension(330, 22));
    tunnelPorts.setName(""); // NOI18N
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 3;
    gridBagConstraints.gridy = 8;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(tunnelPorts, gridBagConstraints);

    labQ330.setText("Q330 s/n:");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 8;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    add(labQ330, gridBagConstraints);

    q330.setColumns(15);
    q330.setFont(new java.awt.Font("Courier New", 0, 12)); // NOI18N
    q330.setToolTipText("Q330 tag numbers and optional data ports separated by spaces. Dataport 1 is used by default.  Example : 3892:1,3390.");
    q330.setMinimumSize(new java.awt.Dimension(188, 22));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 8;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(q330, gridBagConstraints);

    routerConfig.setText("Router at Site (1 IP adr used)");
    routerConfig.setToolTipText("If selected, this station will only have one IP address associated with it and a Firewall must either NAT separate devices or there is actually only one device like a sole Q330 station.");
    routerConfig.setMargin(new java.awt.Insets(0, 0, 0, 0));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 9;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(routerConfig, gridBagConstraints);

    portHelp.setColumns(40);
    portHelp.setEditable(false);
    portHelp.setFont(new java.awt.Font("Courier New", 0, 10)); // NOI18N
    portHelp.setText("gport[:[.n|IPadr|+n][/qport] +n to RTS, 192.168.1.n");
    portHelp.setMinimumSize(new java.awt.Dimension(330, 18));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 3;
    gridBagConstraints.gridy = 9;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(portHelp, gridBagConstraints);

    labHelp.setText("Port Help:");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 2;
    gridBagConstraints.gridy = 9;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    add(labHelp, gridBagConstraints);

    laIIPMask.setText("IP Mask:");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 2;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    add(laIIPMask, gridBagConstraints);

    ipmask.setColumns(19);
    ipmask.setMinimumSize(new java.awt.Dimension(140, 22));
    ipmask.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        ipmaskActionPerformed(evt);
      }
    });
    ipmask.addFocusListener(new java.awt.event.FocusAdapter() {
      public void focusLost(java.awt.event.FocusEvent evt) {
        ipmaskFocusLost(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 2;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(ipmask, gridBagConstraints);

    useBH.setText("Use Alt BH");
    useBH.setToolTipText("If selected, the alternate back haul is used to send data.  This usually means a new routing tables has to be generated.");
    useBH.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        useBHActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 2;
    gridBagConstraints.gridy = 0;
    add(useBH, gridBagConstraints);
  }// </editor-fold>//GEN-END:initComponents

  private void ipmaskFocusLost(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_ipmaskFocusLost
// TODO add your handling code here:
    chkForm();
  }//GEN-LAST:event_ipmaskFocusLost

  private void ipmaskActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_ipmaskActionPerformed
// TODO add your handling code here:
    chkForm();
  }//GEN-LAST:event_ipmaskActionPerformed

  private void q330EnableActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_q330EnableActionPerformed
// TODO add your handling code here:
    if(q330Enable.isSelected()) {
      cpuData.setVisible(true);
      //tunnelPorts.setVisible(true);
      //labTunnelPorts.setVisible(true);
      labQ330.setVisible(true);
      q330.setVisible(true);
      //routerConfig.setVisible(true);
      //labHelp.setVisible(true);
      //portHelp.setVisible(true);
    } else {
      //tunnelPorts.setVisible(false);
      //labTunnelPorts.setVisible(false);
      //labQ330.setVisible(false);
      //q330.setVisible(false);
      //routerConfig.setVisible(false);
      labHelp.setVisible(false);
      portHelp.setVisible(false);
    }
    chkForm();
  }//GEN-LAST:event_q330EnableActionPerformed
  private void programUseBH() {
    String p = RTSStation.getSelectedItem().toString();
    p = p.toUpperCase();
    boolean enable=useBH.isSelected();   // save because addUpdate changes this field
    try {
      Statement stmt = DBConnectionThread.getConnection(DBConnectionThread.getDBSchema()).createStatement();   // used for query
      String s = "SELECT * FROM tcpstation where tcpstation = "
              + Util.sqlEscape(p);
      ResultSet rs = stmt.executeQuery(s);
      rs.next();
      TCPStation loc = new TCPStation(rs);    // read back what we just updated
      Util.prt("program q330 selected="+enable);
      loc.programeRTSUseBackupIP(enable);     // program the RTS
      status.setText(p+" program Use backhaul mode to "+enable);

    }  
    catch  (SQLException  E) {
      Util.SQLErrorPrint(E,"RTSStation: update RTS tunnel  partno="+RTSStation.getSelectedItem());
      error.setText("Could Not Update RTS tunnel!");
      error.setBackground(UC.red);
      addUpdate.setEnabled(true);
      programCommand.setEnabled(true);
    }

  }
  private void programQ330ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_programQ330ActionPerformed
// TODO add your handling code here:
        // Add your handling code here:
    // Special case - we updated the database so now update the FOrm stuff on VMS
    String p = RTSStation.getSelectedItem().toString();
    p = p.toUpperCase();
    boolean enable=q330Enable.isSelected();   // save because addUpdate changes this field
    addUpdateActionPerformed(evt);
    try {
      Statement stmt = DBConnectionThread.getConnection(DBConnectionThread.getDBSchema()).createStatement();   // used for query
      String s = "SELECT * FROM tcpstation where tcpstation = "
              + Util.sqlEscape(p);
      ResultSet rs = stmt.executeQuery(s);
      rs.next();
      TCPStation loc = new TCPStation(rs);    // read back what we just updated 
      StringBuffer sb;
      Util.prt("program q330 selected="+enable);
      sb = loc.programRTSUdpTunnel(enable, cpuSuffix.getText());     // program the RTS
      Util.prta("String buffer returned="+sb.toString());
      status.setText(sb.toString());
      
    }  catch  (SQLException  E)
    { Util.SQLErrorPrint(E,"RTSStation: update RTS tunnel  partno="+RTSStation.getSelectedItem());
      error.setText("Could Not Update RTS tunnel!");
      error.setBackground(UC.red);
      addUpdate.setEnabled(true);
      programCommand.setEnabled(true);
    } 

  }//GEN-LAST:event_programQ330ActionPerformed

  private void programRTSnorebootActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_programRTSnorebootActionPerformed
    // TODO add your handling code here:
    // Special case - we updated the database so now update the FOrm stuff on VMS
    programRTS(false, evt);

  }//GEN-LAST:event_programRTSnorebootActionPerformed
  private void programRTS(boolean reboot,java.awt.event.ActionEvent evt) {
    String p = RTSStation.getSelectedItem().toString();
    p = p.toUpperCase();
    addUpdateActionPerformed(evt);
    try {
      Statement stmt = DBConnectionThread.getConnection(DBConnectionThread.getDBSchema()).createStatement();   // used for query
      String s = "SELECT * FROM tcpstation where tcpstation = "
              + Util.sqlEscape(p);
      ResultSet rs = stmt.executeQuery(s);
      rs.next();
      TCPStation loc = new TCPStation(rs);    // read back what we just updated 
      StringBuffer sb;
      if(reboot) sb = loc.programRTS(cpuSuffix.getText());     // program the RTS
      else sb = loc.programRTSnoreboot(cpuSuffix.getText());     // program the RTS
      Util.prta("String buffer returned="+sb.toString());
      status.setText(sb.toString());
      
    }  catch  (SQLException  E)
    { Util.SQLErrorPrint(E,"RTSStation: update RTS partno="+RTSStation.getSelectedItem());
      error.setText("Could Not Update RTS!");
      error.setBackground(UC.red);
      addUpdate.setEnabled(true);
      programRTS.setEnabled(true);
    } 
  }

  private void commlinkOverrideActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_commlinkOverrideActionPerformed
    // TODO add your handling code here:
    chkForm();
  }//GEN-LAST:event_commlinkOverrideActionPerformed

  private void programCommandActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_programCommandActionPerformed
  {//GEN-HEADEREND:event_programCommandActionPerformed
    // Add your handling code here:
    // Special case - we updated the database so now update the FOrm stuff on VMS
    String p = RTSStation.getSelectedItem().toString();
    p = p.toUpperCase();
    addUpdateActionPerformed(evt);
    try {
      Statement stmt = DBConnectionThread.getConnection(DBConnectionThread.getDBSchema()).createStatement();   // used for query
      String s = "SELECT * FROM tcpstation where tcpstation = "
              + Util.sqlEscape(p);
      ResultSet rs = stmt.executeQuery(s);
      rs.next();
      TCPStation loc = new TCPStation(rs);    // read back what we just updated 
      StringBuffer sb;
      sb = loc.programRTSCommandStatus(cpuSuffix.getText());     // program the RTS
      Util.prta("String buffer returned="+sb.toString());
      status.setText(sb.toString());
      
    }  catch  (SQLException  E)
    { Util.SQLErrorPrint(E,"RTSStation: update RTS command  partno="+RTSStation.getSelectedItem());
      error.setText("Could Not Update RTS Command/status!");
      error.setBackground(UC.red);
      addUpdate.setEnabled(true);
      programCommand.setEnabled(true);
    } 

  }//GEN-LAST:event_programCommandActionPerformed

  private void commlinkActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_commlinkActionPerformed
  {//GEN-HEADEREND:event_commlinkActionPerformed
    // Add your handling code here:
    chkForm();
  }//GEN-LAST:event_commlinkActionPerformed

  private void ipadrFocusLost(java.awt.event.FocusEvent evt)//GEN-FIRST:event_ipadrFocusLost
  {//GEN-HEADEREND:event_ipadrFocusLost
    // Add your handling code here:
    chkForm();
  }//GEN-LAST:event_ipadrFocusLost

  private void cpuDataActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_cpuDataActionPerformed
  {//GEN-HEADEREND:event_cpuDataActionPerformed
    // Add your handling code here:
    if(cpuData != null && cpuData.getSelectedIndex() != -1) mshearPortActionPerformed(evt);
  }//GEN-LAST:event_cpuDataActionPerformed

  private void programRTSActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_programRTSActionPerformed
  {//GEN-HEADEREND:event_programRTSActionPerformed
    // Add your handling code here:
      // Special case - we updated the database so now update the FOrm stuff on VMS
    programRTS(true, evt);

  }//GEN-LAST:event_programRTSActionPerformed

  private void mshearPortFocusLost(java.awt.event.FocusEvent evt)//GEN-FIRST:event_mshearPortFocusLost
  {//GEN-HEADEREND:event_mshearPortFocusLost
    // Add your handling code here:
    chkForm();
  }//GEN-LAST:event_mshearPortFocusLost

  private void mshearPortActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_mshearPortActionPerformed
  {//GEN-HEADEREND:event_mshearPortActionPerformed
    // Add your handling code here:
    port1ClientActionPerformed(evt);
    port2ClientActionPerformed(evt);
    port3ClientActionPerformed(evt);
    port4ClientActionPerformed(evt);
  }//GEN-LAST:event_mshearPortActionPerformed

  private void port4ClientActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_port4ClientActionPerformed
  {//GEN-HEADEREND:event_port4ClientActionPerformed
    // Add your handling code here:
        doPort(4,port4, port4Client,  port4Text);
  }//GEN-LAST:event_port4ClientActionPerformed

  private void port3ClientActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_port3ClientActionPerformed
  {//GEN-HEADEREND:event_port3ClientActionPerformed
    // Add your handling code here:
        doPort(3,port3, port3Client,  port3Text);
  }//GEN-LAST:event_port3ClientActionPerformed

  private void port2ClientActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_port2ClientActionPerformed
  {//GEN-HEADEREND:event_port2ClientActionPerformed
    // Add your handling code here:
        doPort(2,port2, port2Client,  port2Text);
  }//GEN-LAST:event_port2ClientActionPerformed

  private void port1ClientActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_port1ClientActionPerformed
  {//GEN-HEADEREND:event_port1ClientActionPerformed
    // Add your handling code here:
    doPort(1,port1, port1Client,  port1Text);
  }//GEN-LAST:event_port1ClientActionPerformed

private void ipadrActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_ipadrActionPerformed
// Add your handling code here:
  chkForm();
}//GEN-LAST:event_ipadrActionPerformed

private void port4ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_port4ActionPerformed
// Add your handling code here:
    doPort(4, port4, port4Client, port4Text);
}//GEN-LAST:event_port4ActionPerformed

private void port3ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_port3ActionPerformed
// Add your handling code here:
    doPort(3,port3,port3Client, port3Text);
}//GEN-LAST:event_port3ActionPerformed

private void port2ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_port2ActionPerformed
// Add your handling code here:
    doPort(2,port2, port2Client, port2Text);
}//GEN-LAST:event_port2ActionPerformed

private void port1ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_port1ActionPerformed
// Add your handling code here:
    doPort(1,port1, port1Client,  port1Text);
}//GEN-LAST:event_port1ActionPerformed
  private void doPort(int p, JComboBox b, JRadioButton client, javax.swing.JTextField text) {
   chkForm();
   //cpuData.setVisible(false);
   labNode.setVisible(false);
   if(port1.getSelectedIndex() == MSHEAR || port2.getSelectedIndex() == MSHEAR ||
      port3.getSelectedIndex() == MSHEAR || port4.getSelectedIndex() == MSHEAR ||
       q330Enable.isSelected()) {
      if(!q330Enable.isSelected()) {
        labMshearPort.setVisible(true);
        mshearPort.setVisible(true);
      }
      cpuData.setVisible(true);
      labNode.setVisible(true);
   } else {
     labMshearPort.setVisible(false);
     mshearPort.setVisible(false);
   }
   if( port1.getSelectedIndex() == DATA  || 
        port2.getSelectedIndex() == DATA  ||
        port3.getSelectedIndex() == DATA ||
        port4.getSelectedIndex() == DATA ) {
        cpuData.setVisible(true);
        labNode.setVisible(true);
   } 
    switch (b.getSelectedIndex()) {
      case RTS :
        if(client.isSelected()) {
          rtsport=2006;
          text.setText(UC.DEFAULT_RTS_VMS_NODE+"/"+rtsport+" as RTS debug");
        }
        else {
          rtsport=8000+p-1;
          text.setText(ipadr.getText()+"/"+rtsport+" as RTS debug");
        }
        break;
      case CONSOLE:
        if(client.isSelected()) {
          consoleport=2007;
          text.setText(UC.DEFAULT_CONSOLE_VMS_NODE+"/"+consoleport+" as Console");
        }
        else {
          consoleport=8000+p-1;
          text.setText(ipadr.getText()+"/"+consoleport+" as Console");
        }
        break;
      case NONE:
        if(client.isSelected()) {
          consoleport=0;
          text.setText(UC.DEFAULT_CONSOLE_VMS_NODE+"/"+consoleport+" as Console");
        }
        else {
          consoleport=8000+p-1;
          text.setText(ipadr.getText()+"/"+consoleport+" as Console");
        }
        
        break;
      case DATA:
        if(client.isSelected()) {
          dataport=2004;
          if(cpuData.getSelectedIndex() == -1) {
            text.setText("Node needs to be selected!");
          }
          else {
            String node = ((Cpu) cpuData.getSelectedItem()).getCpu();
            if(node.equals("nsn") || node.equals("nsn0") || node.equals("nsn4")) node=UC.DEFAULT_DATA_VMS_NODE;
            text.setText(node+"/"+dataport+" as ANSS Data");
          }
        }
        else {
          dataport=8000+p-1;
          text.setText(ipadr.getText()+"/"+dataport+" as ANSS Data");
        }
        break;
      case MSHEAR:

        client.setSelected(true);
        if( cpuData.getSelectedIndex() == -1) text.setText("MShear node needs to be selected!");
        else {
          
          String node = ((Cpu) cpuData.getSelectedItem()).getCpu();
          if( !((Cpu) cpuData.getSelectedItem()).isMShearAllowed()) text.setText(node+" - Not a MShear CPU!");
          else text.setText(node+"/"+mshearport+" as MShear Data");

        //Util.prt("**** mshear set to cpu gldmt="+cpuData.getSelectedIndex());
        }
        break;
      case GPS:
        if(client.isSelected()) {
          gpsport=2009;
          text.setText(UC.DEFAULT_GPS_VMS_NODE+"/"+gpsport+" as GPS  Data");
        }
        else {
          gpsport=8000+p-1;
          text.setText(ipadr.getText()+"/"+gpsport+" as GPS Data");
        }
        break;
    }    
  }
  private void addUpdateActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_addUpdateActionPerformed
    // Add your handling code here:
    
    // Do not change
    if(initiating) return;
    if(chkForm()) return;
    try {
      String p = RTSStation.getSelectedItem().toString();
      p = p.toUpperCase();
      if(!obj.isNew()) obj.refreshRecord();        // Refresh the result set so the sets will not fail on stale one
      // Set all of the fields
      obj.setString("TCPStation",p);
      obj.setString("ipadr", ip);
      obj.setString("ipmask", mask);
      Util.prt("Update : "+p+" ip="+ip+" cpu index="+cpuData.getSelectedIndex());
      obj.setInt("cpuID",0);
      if(commlink.getSelectedIndex() == -1) obj.setInt("commlinkID",0);
      else obj.setInt("commlinkID", ((CommLink) commlink.getSelectedItem()).getID());
      if(commlinkOverride.getSelectedIndex() == -1) obj.setInt("commlinkOverrideID",0);
      else obj.setInt("commlinkOverrideID", ((CommLink) commlinkOverride.getSelectedItem()).getID());
      obj.setInt("rtsport1", port1.getSelectedIndex());
      obj.setInt("rtsport2", port2.getSelectedIndex());
      obj.setInt("rtsport3", port3.getSelectedIndex());
      obj.setInt("rtsport4", port4.getSelectedIndex());
      Util.prt("suppressdate="+sdate);
      obj.setInt("rtsclient1", port1Client.isSelected() ? 1 : 0);
      obj.setInt("rtsclient2", port2Client.isSelected() ? 1 : 0);
      obj.setInt("rtsclient3", port3Client.isSelected() ? 1 : 0);
      obj.setInt("rtsclient4", port4Client.isSelected() ? 1 : 0);
      obj.setInt("usebh", (useBH.isSelected() ? 1 : 0));
      obj.setInt("powertype",0);
      obj.setInt("ctrlq",1);
      obj.setInt("rollback",1);
       obj.setInt("state",2);
       obj.setInt("gomberg",1);    // remember 1= false, 2= true!!
       obj.setInt("port",0);
       obj.setInt("localPort", 0);
       obj.setInt("q330Tunnel", q330Enable.isSelected() ? 2 : 1);
       obj.setString("tunnelports", tunnelPorts.getText());
       obj.setInt("allowpoc", routerConfig.isSelected() ? 1 : 0);
       obj.setString("q330", q330.getText());
       if(q330.getText().length() > 0)
         if(cpuData.getSelectedIndex() != -1) 
            obj.setInt("cpuID",((Cpu) cpuData.getSelectedItem()).getID());

      // If this is an rts port, set the TCPStation side 
      if(port1.getSelectedIndex() == RTS || port2.getSelectedIndex() == RTS ||
         port3.getSelectedIndex() == RTS || port4.getSelectedIndex() == RTS) {
        Util.prt("Setting RTS for RTS="+rtsport+" Console="+consoleport+" GPS="+gpsport+
            " data="+dataport+" mshearport="+mshearport);
        obj.setString("consoleIP",ip);
        obj.setString("powerip", ip);
        

        obj.setInt("state",3);
        obj.setInt("port", dataport);
        obj.setInt("localport", 2004);
  

        // We set the node zero on the VMS side if this is a client site
        if(port1.getSelectedIndex() == DATA && port1Client.isSelected() ||
          port2.getSelectedIndex() == DATA && port2Client.isSelected() ||
          port3.getSelectedIndex() == DATA && port3Client.isSelected() ||
          port4.getSelectedIndex() == DATA && port4Client.isSelected() 
        ) { 
            if(cpuData.getSelectedIndex() != -1) 
              obj.setInt("cpuID",((Cpu) cpuData.getSelectedItem()).getID());
            obj.setInt("state",2);
            obj.setInt("gomberg",2);    // remember 1= false, 2= true!!
            obj.setInt("rollback",2);
            obj.setInt("ctrlq",2);
            obj.setInt("port",0);
            obj.setInt("localPort", 2004);
        }
        
        // MShears are always clients so only a local port
        if(port1.getSelectedIndex() == MSHEAR && port1Client.isSelected() ||
          port2.getSelectedIndex() == MSHEAR && port2Client.isSelected() ||
          port3.getSelectedIndex() == MSHEAR && port3Client.isSelected() ||
          port4.getSelectedIndex() == MSHEAR && port4Client.isSelected() ||
          q330Enable.isSelected()
        ) { 
          if(cpuData.getSelectedIndex() != -1) 
            obj.setInt("cpuID",((Cpu) cpuData.getSelectedItem()).getID());
          obj.setInt("state",2); 
          obj.setInt("gomberg",1);
          obj.setInt("rollback",1);
          obj.setInt("ctrlq",1);
          obj.setInt("port",0);
          obj.setInt("localport", mshearport);  
        }

      
        // If we have an MShear port, set the configuration of data port
        Util.prta("dataport="+dataport+" mshear="+mshearport);
        
        // Set the RTS console port and power port, timeouts etc
        obj.setInt("consoleport",consoleport);
        obj.setInt("powertype",4);
        obj.setInt("timeout",1202);
        obj.setInt("consoletimeout", 602);
        obj.setInt("powerPort", 0);
      }
        
      // Do not change
      obj.updateRecord();
      programUseBH();     // Send command to set correct backhaul mode on this RTS
      
      
      // Special case - we updated the database so now update the FOrm stuff on VMS
      Statement stmt = DBConnectionThread.getConnection(DBConnectionThread.getDBSchema()).createStatement();   // used for query
      String s = "SELECT * FROM tcpstation where tcpstation = "
              + Util.sqlEscape(p);
      ResultSet rs = stmt.executeQuery(s);
      rs.next();
      TCPStation loc = new TCPStation(rs);    // read back what we just updated 
      loc.sendForm();                         // SEnd it to VMS
      
      SNWStationPanel.setSNWGroupsFromANSS(p);
      
      getJComboBox(RTSStation);
      RTSStation.setSelectedIndex(-1);
      clearScreen();
      
    }  catch  (SQLException  E)
    { Util.SQLErrorPrint(E,"RTSStation: update failed partno="+RTSStation.getSelectedItem());
      error.setText("Could Not Update File!");
      error.setBackground(UC.red);
      addUpdate.setEnabled(true);
      programRTS.setEnabled(true);
      programCommand.setEnabled(true);
      
    }      
  }//GEN-LAST:event_addUpdateActionPerformed

private void RTSStationActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_RTSStationActionPerformed
    // Add your handling code here:
  FUtil.searchJComboBox(RTSStation, true);
   find();
 
 
}//GEN-LAST:event_RTSStationActionPerformed

private void useBHActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_useBHActionPerformed
  // TODO add your handling code here:

}//GEN-LAST:event_useBHActionPerformed
  
  
  // Variables declaration - do not modify//GEN-BEGIN:variables
  private javax.swing.JTextField ID;
  private javax.swing.JComboBox RTSStation;
  private javax.swing.JButton addUpdate;
  private javax.swing.JLabel comlinkLab;
  private javax.swing.JComboBox commlink;
  private javax.swing.JComboBox commlinkOverride;
  private javax.swing.JComboBox cpuData;
  private javax.swing.JTextField cpuSuffix;
  private javax.swing.JTextField error;
  private java.awt.GridBagLayout gridBagLayout1;
  private javax.swing.JTextField ipadr;
  private javax.swing.JTextField ipmask;
  private javax.swing.JLabel jLabel1;
  private javax.swing.JLabel jLabel9;
  private javax.swing.JList jList1;
  private javax.swing.JScrollPane jScrollPane1;
  private javax.swing.JScrollPane jScrollPane2;
  private javax.swing.JTextField jTextField1;
  private javax.swing.JLabel laIIPMask;
  private javax.swing.JLabel labCommlinkOverride;
  private javax.swing.JLabel labHelp;
  private javax.swing.JLabel labIPadr;
  private javax.swing.JLabel labMshearPort;
  private javax.swing.JLabel labNode;
  private javax.swing.JLabel labPort1;
  private javax.swing.JLabel labPort2;
  private javax.swing.JLabel labPort3;
  private javax.swing.JLabel labPort4;
  private javax.swing.JLabel labQ330;
  private javax.swing.JLabel labSuppress;
  private javax.swing.JLabel labTunnelPorts;
  private javax.swing.JTextField mshearPort;
  private javax.swing.JComboBox port1;
  private javax.swing.JRadioButton port1Client;
  private javax.swing.JTextField port1Text;
  private javax.swing.JComboBox port2;
  private javax.swing.JRadioButton port2Client;
  private javax.swing.JTextField port2Text;
  private javax.swing.JComboBox port3;
  private javax.swing.JRadioButton port3Client;
  private javax.swing.JTextField port3Text;
  private javax.swing.JComboBox port4;
  private javax.swing.JRadioButton port4Client;
  private javax.swing.JTextField port4Text;
  private javax.swing.JTextField portHelp;
  private javax.swing.JButton programCommand;
  private javax.swing.JButton programQ330;
  private javax.swing.JButton programRTS;
  private javax.swing.JButton programRTSnoreboot;
  private javax.swing.JTextField q330;
  private javax.swing.JCheckBox q330Enable;
  private javax.swing.JRadioButton routerConfig;
  private javax.swing.JTextArea status;
  private javax.swing.JTextField suppressDate;
  private javax.swing.JTextField tunnelPorts;
  private javax.swing.JRadioButton useBH;
  // End of variables declaration//GEN-END:variables
  /** Creates new form RTSStationPanel */
  public RTSStationPanel() {
    initiating=true;
    initComponents();
    getJComboBox(RTSStation);
    RTSStation.setSelectedIndex(-1);    // Set selected type
    UC.Look(this);                    // Set color background
    UC.Look(port1Client);
    UC.Look(port2Client);
    UC.Look(port3Client);
    UC.Look(port4Client);
    UC.Look(port1Text);
    UC.Look(port2Text);
    UC.Look(port3Text);
    UC.Look(port4Text);
    UC.Look(q330Enable);
    UC.Look(portHelp);
    UC.Look(routerConfig);
    initiating=false;
    
    clearScreen();                    // Start with empty screen
  }
  
   // No changes needed.
 public static JComboBox getJComboBox() { JComboBox b = new JComboBox(); getJComboBox(b); return b;}
  
  public static void getJComboBox(JComboBox b) {
    b.removeAllItems();
    TCPStation.tcpstations=null;
    makeRTSStations();
    for (int i=0; i< TCPStation.tcpstations.size(); i++) {
      b.addItem( TCPStation.tcpstations.get(i));
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
    for(int i=0; i<TCPStation.tcpstations.size(); i++) if( ID == TCPStation.tcpstations.get(i).getID()) return i;
    return -1;
  }
  public static TCPStation getTCPStation(String s) {
    if(TCPStation.tcpstations == null) makeRTSStations();
    for(int i=0; i<TCPStation.tcpstations.size(); i++) {
      TCPStation a = TCPStation.tcpstations.get(i);
      //Util.prt("getTCPStation found="+s+" "+a);
      if( s.equalsIgnoreCase(a.getTCPStation())) return a;
    }
    return null;
  }
    
  // This routine should only need tweeking if key field is not same as table name
  private static void makeRTSStations() {
    TCPStation.makeTCPStations();   
  }
  
  // No changes needed
  private void find() {
    if(RTSStation == null) return;
    if(initiating) return;
    TCPStation l;
    if(RTSStation.getSelectedIndex() == -1) {
      if(RTSStation.getSelectedItem() == null) return;
      Util.prta("about to new one with +"+RTSStation.getSelectedItem()+"|");
      if(((String) RTSStation.getSelectedItem()).equals("")) return;
      l = newOne();
    } 
    else {
      l = (TCPStation) RTSStation.getSelectedItem();
    }
      
    if(l == null) return;
    String p = l.getTCPStation();
    if(p == null) return;
    if(p.equals("")) {
      addUpdate.setEnabled(false);
      programRTS.setEnabled(false);
      programCommand.setEnabled(false);
      addUpdate.setText("Enter a RTSStation!");
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
          // set any defaults here
          port1.setSelectedIndex(RTS);
          port2.setSelectedIndex(CONSOLE);
          port3.setSelectedIndex(NONE);
          port4.setSelectedIndex(DATA);
          port1Client.setSelected(false);
          port2Client.setSelected(true);
          port3Client.setSelected(false);
          port4Client.setSelected(true);    
          labMshearPort.setVisible(false);
          mshearPort.setVisible(false);
          labNode.setVisible(true);
          CpuPanel.setJComboBoxToID(cpuData, CpuPanel.getIDForCpu(UC.DEFAULT_DATA_VMS_NODE));
          cpuData.setVisible(true);
          mshearPort.setText("");    
          error.setText("NOT found - assume NEW");
          error.setBackground(UC.yellow);
          addUpdate.setText("Add "+p);
          addUpdate.setEnabled(true);
          programRTS.setEnabled(true);
          programCommand.setEnabled(true);
          q330Enable.setEnabled(true);
          useBH.setSelected(false);
      }
      else {
        addUpdate.setText("Update "+p);
        addUpdate.setEnabled(true);
        programRTS.setEnabled(true);
        programCommand.setEnabled(true);
        ID.setText(""+obj.getInt("ID"));    // Info only, show ID
      }
    }  catch  (SQLException  E)     // I don't think this can be thrown any more
    { Util.SQLErrorPrint(E,"RTSStation: SQL error getting RTSStation="+p);
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
      Show.inFrame(new RTSStationPanel(), UC.XSIZE, UC.YSIZE);
    }
    catch(InstantiationException e) {}
  }
}
