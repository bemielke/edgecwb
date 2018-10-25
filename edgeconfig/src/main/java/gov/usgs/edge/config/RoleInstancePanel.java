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
//import java.sql.Statement;
import java.util.ArrayList;
import javax.swing.JComboBox;

/**
 *
 * requirements : 
 * The key for the file must be the name of the table and of the
 * JComboBox variable this variable is called "role" in the initial form
 * The table name and key name should match and start lower case (role).
 * The Class here will be that same name but with upper case at beginning(RoleInstance).
 <br> 1)  Rename the location JComboBox to the "key" (role) value.  This should start Lower
 *      case.
 *<br>  2)  The table name should match this key name and be all lower case.
 *<br>  3)  Change all RoleInstance to ClassName of underlying data (case sensitive!)
<br>  4)  Change all role key value (the JComboBox above)
 *<br>  5)  clearScreen should update swing components to new defaults
 *<br>  6)  chkForm should check all field for validity.  The errorTrack variable is
 *      used to track multiple errors.  In FUtil.chk* handle checks of the various
 *      variable types.  Date, double, and IP etc return the "canonical" form for 
 *      the variable.  These should be stored in a class variable so the latest one
 *      from "chkForm()" are available to the update writer.
 *<br>  7) newone() should be updated to create a new instance of the underlying data class.
 *<br>  8) oldone() get data from database and update all swing elements to correct values
 *<br>  9) addUpdateAction() add data base set*() operations updating all fields in the DB
 *<br> 10) RoleInstancePanel() constructor - good place to change backgrounds using UC.Look() any
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
public class RoleInstancePanel extends javax.swing.JPanel {
  
  // NOTE : here define all variables general.  "Vector v" is used for main Comboboz
  DBObject obj;
  ErrorTrack err=new ErrorTrack();
  boolean initiating=false;
  String stripadr = null;
  
  //USER: Here are the local variables
  int origCpuID;
  boolean origFailedOver;
  
  public static String defaultCrontab=  
          "# Make a pass through the day-before-yesterdays data files and put in holdings and generat fetchlists\n"+
          "#09 05 * * * nice bash scripts"+Util.fs+"updateHoldings.bash >>LOG"+Util.fs+"updateHoldings.log 2>&1\n"+
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
   //Util.prt("chkForm Role");
   addUpdate.setEnabled(true);
   if(ipadr.getText().trim().equals("")) stripadr="";
   else stripadr = FUtil.chkIP(ipadr,err,true);
   //String [] oldAccounts = accounts.getText().replaceAll("  ", " ").replaceAll("  ", " ").split(" ");

   // check to see if this cpu selection results in a conflict in accounts on this node
   //try {
     if(role.getSelectedIndex() != -1 && cpuID.getSelectedIndex()!=-1) {
       // NONE cpu or NULL is always OK
       if(!(""+cpuID.getSelectedItem()).equals("null") && !"NONE".equals(""+cpuID.getSelectedItem())) {
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

   //}
   //catch(SQLException e) {}
   alarmInt = FUtil.chkInt(alarm,err,-1000, 1000);
   aftacInt = FUtil.chkInt(aftac,err,-1000, 1000);
   consolesInt = FUtil.chkInt(console,err,-1000, 1000);
   metadataInt = FUtil.chkInt(mds,err,-3000, 3000);
   //quakemlInt = FUtil.chkInt(quakeml,err,-2500, 2500);
   //querymomInt = FUtil.chkInt(querymom,err,-40000, 40000);
   rtsInt = FUtil.chkInt(rts,err,-1000, 1000);
   tcpholdingsInt = FUtil.chkInt(tcpholdings,err,-1000, 1000);
   smgetterInt = FUtil.chkInt(smgetter,err,-1000, 1000);
   if(snwButton.isSelected() && !alarmButton.isSelected()) {
     err.set(true);
     err.appendText("SNW selected without alarm");
   }
    // No CHANGES : If we found an error, color up error box
   error.setText("");
   if(err.isSet()) {
      error.setText(err.getText());
      error.setBackground(UC.red);
      addUpdate.setEnabled(false);
    }
    return err.isSet();
  }
  int alarmInt, aftacInt, consolesInt, metadataInt,quakemlInt, querymomInt, rtsInt, tcpholdingsInt, smgetterInt;
  
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
    cpuID.setSelectedIndex(-1);
    hasdata.setSelected(false);
    config.setText("");
    config.setVisible(false);
    labConfig.setVisible(false);
    changeRole.setText("");
    neicOptions.setSelected(false);
    crontab.setText("");
    alarmButton.setSelected(false);
    alarm.setText("0");
    udpchannelButton.setSelected(false);
    cmdStatusButton.setSelected(false);
    dbmsgButton.setSelected(false);
    configButton.setSelected(false);
    latencyServer.setSelected(false);
    snwButton.setSelected(false); 
    snwSenderButton.setSelected(false);
    bhcheckButton.setSelected(false);
    etchostsButton.setSelected(false);
    routesButton.setSelected(false);
    webButton.setSelected(false);
    tcpholdings.setText("0");
    smgetter.setText("0");
    console.setText("0");
    rts.setText("0");
    aftac.setText("0");
    mds.setText("0");
    quakeml.setText("0");
    quakeml.setVisible(false);
    labQML.setVisible(false);
    neicPanel.setVisible(false);
    bhcheckButton.setVisible(false);
    etchostsButton.setVisible(false);
    routesButton.setVisible(false);
    webButton.setVisible(false);
    
    querymom.setVisible(false);     // this field is now in querymominstance rather than role
    labQuerymom.setVisible(false);  // ditto
    
  }
 
  
  private RoleInstance newOne() {
      
    return new RoleInstance(0, ((String) role.getSelectedItem()).toUpperCase(), //, more
            "", "", 0, 0, "", 0,0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,0,0,0, "",
            0,0,0,0,0,0,0,0,"",0L,0,0,0,0,0,0,0,0,0
       );
  }
  
  private void oldOne(String p) throws SQLException {
    obj = new DBObject(DBConnectionThread.getThread(DBConnectionThread.getDBSchema()), DBConnectionThread.getDBSchema(), "role","role",p);

    if(obj.isNew()) {
      Util.prt("object is new"+p);
    }
    else {
      //USER: Here set all of the form fields to data from the DBObject
      //      role.setText(obj.getString("RoleInstance"));
      // Example : description.setText(obj.getString("description"));
      description.setText(obj.getString("description"));
      ipadr.setText(obj.getString("ipadr"));
      accounts.setText(obj.getString("accounts"));
      CpuPanel.setJComboBoxToID(cpuID,obj.getInt("cpuID"));
//      cpuID.setSelectedItem(obj.getInt("cpuID"));
      if(obj.getInt("hasdata")==1 ) hasdata.setSelected(true);
      else hasdata.setSelected(false);
      if(obj.getInt("failover") == 1) failover.setSelected(true);
      else failover.setSelected(false);
      origFailedOver = failover.isSelected();
      CpuPanel.setJComboBoxToID(failoverCpuID, obj.getInt("failovercpuid"));
      origCpuID=obj.getInt("cpuID");
      alarmButton.setSelected(obj.getInt("alarm") != 0);
      crontab.setText(obj.getString("crontab"));
      bhcheckButton.setSelected(obj.getInt("alarmbhchecker") != 0);
      cmdStatusButton.setSelected(obj.getInt("alarmcmdstatus") != 0);
      configButton.setSelected(obj.getInt("alarmconfig") != 0);
      dbmsgButton.setSelected(obj.getInt("alarmdbmsgsrv") != 0);
      etchostsButton.setSelected(obj.getInt("alarmetchosts") != 0);
      latencyServer.setSelected(obj.getInt("alarmlatency") != 0);
      routesButton.setSelected(obj.getInt("alarmroutes") != 0);
      snwButton.setSelected(obj.getInt("alarmsnw") != 0);
      snwSenderButton.setSelected(obj.getInt("alarmsnwsender") != 0);
      udpchannelButton.setSelected(obj.getInt("alarmudpchannel") != 0);
      webButton.setSelected(obj.getInt("alarmwebserver") != 0);
      alarmArgs.setText(obj.getString("alarmargs"));
      alarmArgs.setCaretPosition(0);
      alarm.setText(""+obj.getInt("alarmmem"));
      aftac.setText(""+obj.getInt("aftac"));
      console.setText(""+obj.getInt("consoles"));
      mds.setText(""+obj.getInt("metadata"));
      quakeml.setText(""+obj.getInt("quakeml"));
      querymom.setText(""+obj.getInt("querymom"));
      rts.setText(""+obj.getInt("rts"));
      tcpholdings.setText(""+obj.getInt("tcpholdings"));
      smgetter.setText(""+obj.getInt("smgetter"));
//      CpuPanel.setJComboBoxToID(cpuID, obj.getInt("cpuID"));  
      chkForm();
    }           // End else isNew () - processing to form
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
    topPanel = new javax.swing.JPanel();
    lblIpadr = new javax.swing.JLabel();
    ipadr = new javax.swing.JTextField();
    lblCPU = new javax.swing.JLabel();
    cpuID = CpuPanel.getJComboBox();
    hasdata = new javax.swing.JRadioButton();
    labAccounts = new javax.swing.JLabel();
    accounts = new javax.swing.JTextField();
    labFailCpu = new javax.swing.JLabel();
    failoverCpuID = CpuPanel.getJComboBox();
    failover = new javax.swing.JRadioButton();
    mainTabs = new javax.swing.JTabbedPane();
    alarmPanel = new javax.swing.JPanel();
    labQuerymom = new javax.swing.JLabel();
    querymom = new javax.swing.JTextField();
    labAlarm = new javax.swing.JLabel();
    alarm = new javax.swing.JTextField();
    labTcpH = new javax.swing.JLabel();
    tcpholdings = new javax.swing.JTextField();
    minButton = new javax.swing.JButton();
    normButton = new javax.swing.JButton();
    jSeparator2 = new javax.swing.JSeparator();
    labThreads = new javax.swing.JLabel();
    alarmButton = new javax.swing.JRadioButton();
    udpchannelButton = new javax.swing.JRadioButton();
    snwButton = new javax.swing.JRadioButton();
    snwSenderButton = new javax.swing.JRadioButton();
    cmdStatusButton = new javax.swing.JRadioButton();
    dbmsgButton = new javax.swing.JRadioButton();
    configButton = new javax.swing.JRadioButton();
    latencyServer = new javax.swing.JRadioButton();
    routesButton = new javax.swing.JRadioButton();
    bhcheckButton = new javax.swing.JRadioButton();
    webButton = new javax.swing.JRadioButton();
    etchostsButton = new javax.swing.JRadioButton();
    jSeparator1 = new javax.swing.JSeparator();
    argsLabel = new javax.swing.JLabel();
    alarmArgs = new javax.swing.JTextField();
    neicOptions = new javax.swing.JRadioButton();
    neicPanel = new javax.swing.JPanel();
    labMDS = new javax.swing.JLabel();
    mds = new javax.swing.JTextField();
    labQML = new javax.swing.JLabel();
    quakeml = new javax.swing.JTextField();
    labConsole = new javax.swing.JLabel();
    console = new javax.swing.JTextField();
    labAftac = new javax.swing.JLabel();
    aftac = new javax.swing.JTextField();
    labRTS = new javax.swing.JLabel();
    rts = new javax.swing.JTextField();
    labSMG = new javax.swing.JLabel();
    smgetter = new javax.swing.JTextField();
    labNeic = new javax.swing.JLabel();
    crontabPanel = new javax.swing.JPanel();
    labCrontab = new javax.swing.JLabel();
    crontabScroll = new javax.swing.JScrollPane();
    crontab = new javax.swing.JTextArea();
    setDefaultCrontab = new javax.swing.JButton();
    configPanel = new javax.swing.JPanel();
    lblDesc = new javax.swing.JLabel();
    descScroll = new javax.swing.JScrollPane();
    description = new javax.swing.JTextArea();
    labConfig = new javax.swing.JLabel();
    configScrollPane = new javax.swing.JScrollPane();
    config = new javax.swing.JTextArea();
    addUpdate = new javax.swing.JButton();
    ID = new javax.swing.JTextField();
    labID = new javax.swing.JLabel();
    error = new javax.swing.JTextField();
    delete = new javax.swing.JButton();
    rebuild = new javax.swing.JButton();
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

    topPanel.setBorder(javax.swing.BorderFactory.createEtchedBorder());
    topPanel.setMinimumSize(new java.awt.Dimension(550, 105));
    topPanel.setPreferredSize(new java.awt.Dimension(550, 105));

    lblIpadr.setText("IP Address : ");
    topPanel.add(lblIpadr);

    ipadr.setToolTipText("The IP address of this role.");
    ipadr.setMinimumSize(new java.awt.Dimension(130, 28));
    ipadr.setPreferredSize(new java.awt.Dimension(130, 28));
    topPanel.add(ipadr);

    lblCPU.setText("Node:");
    topPanel.add(lblCPU);

    cpuID.setEditable(true);
    cpuID.setToolTipText("Select the physical node this role is to run on.  For sites not using dynamic roles to cpus on a private network, this is the same as the role generally.");
    cpuID.setMinimumSize(new java.awt.Dimension(150, 28));
    cpuID.setPreferredSize(new java.awt.Dimension(150, 28));
    cpuID.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        cpuIDActionPerformed(evt);
      }
    });
    topPanel.add(cpuID);

    hasdata.setText("Data ?");
    hasdata.setEnabled(false);
    topPanel.add(hasdata);

    labAccounts.setText("Enable Accts:");
    topPanel.add(labAccounts);

    accounts.setToolTipText("This role has responsibilty to configure all of the listed accounts via the database and GUI.");
    accounts.setMinimumSize(new java.awt.Dimension(350, 28));
    accounts.setPreferredSize(new java.awt.Dimension(350, 28));
    topPanel.add(accounts);

    labFailCpu.setText("FailOverNode:");
    topPanel.add(labFailCpu);

    failoverCpuID.setToolTipText("Set the node/cpu that this role would normally failover to.  In an N+1 failover configuration this would be the idle N+1 server.");
    failoverCpuID.setMinimumSize(new java.awt.Dimension(150, 27));
    failoverCpuID.setPreferredSize(new java.awt.Dimension(150, 27));
    topPanel.add(failoverCpuID);

    failover.setText("Failed Over");
    failover.setToolTipText("If selected, this role is marked to run on its failover node/cpu and not on the configured one.");
    topPanel.add(failover);

    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 3;
    add(topPanel, gridBagConstraints);

    mainTabs.setMinimumSize(new java.awt.Dimension(600, 400));
    mainTabs.setPreferredSize(new java.awt.Dimension(600, 400));

    alarmPanel.setBorder(javax.swing.BorderFactory.createEtchedBorder());
    alarmPanel.setMinimumSize(new java.awt.Dimension(550, 200));
    alarmPanel.setPreferredSize(new java.awt.Dimension(550, 200));

    labQuerymom.setText("QueryMom (mB):");
    alarmPanel.add(labQuerymom);

    querymom.setToolTipText("The heap memory for this process in megabytes (mB).  Zero means the process is not run. Negative means run in with -test.");
    querymom.setMinimumSize(new java.awt.Dimension(50, 28));
    querymom.setPreferredSize(new java.awt.Dimension(50, 28));
    querymom.addFocusListener(new java.awt.event.FocusAdapter() {
      public void focusLost(java.awt.event.FocusEvent evt) {
        querymomFocusLost(evt);
      }
    });
    alarmPanel.add(querymom);

    labAlarm.setText("Alarm(mB):");
    alarmPanel.add(labAlarm);

    alarm.setToolTipText("The heap memory for this process in megabytes (mB - try 350).    Zero means the process is not run.");
    alarm.setMinimumSize(new java.awt.Dimension(40, 28));
    alarm.setPreferredSize(new java.awt.Dimension(40, 28));
    alarm.addFocusListener(new java.awt.event.FocusAdapter() {
      public void focusLost(java.awt.event.FocusEvent evt) {
        alarmFocusLost(evt);
      }
    });
    alarmPanel.add(alarm);

    labTcpH.setText("TcpHoldings(mB):");
    alarmPanel.add(labTcpH);

    tcpholdings.setColumns(4);
    tcpholdings.setToolTipText("The heap memory for this process in megabytes (mB - try 50).  Zero means the process is not run. Negative means run in with -test.");
    tcpholdings.setMinimumSize(new java.awt.Dimension(40, 28));
    tcpholdings.setPreferredSize(new java.awt.Dimension(40, 28));
    tcpholdings.addFocusListener(new java.awt.event.FocusAdapter() {
      public void focusLost(java.awt.event.FocusEvent evt) {
        tcpholdingsFocusLost(evt);
      }
    });
    alarmPanel.add(tcpholdings);

    minButton.setText("Set Minimum");
    minButton.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        minButtonActionPerformed(evt);
      }
    });
    alarmPanel.add(minButton);

    normButton.setText("Set Normal");
    normButton.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        normButtonActionPerformed(evt);
      }
    });
    alarmPanel.add(normButton);

    jSeparator2.setMinimumSize(new java.awt.Dimension(500, 10));
    jSeparator2.setPreferredSize(new java.awt.Dimension(500, 10));
    alarmPanel.add(jSeparator2);

    labThreads.setText("Select Threads within Alarm :");
    alarmPanel.add(labThreads);

    alarmButton.setText("ProcessAlarms");
    alarmButton.setToolTipText("<html>\n<pre>Enable running of the Alarm thread which can generate pages and emails after receiving UDP events from any Edge/CWB process. \n\nNote this is distinct from the alarm process which runs on all nodes.  If this is selected, this alarm process will actually handle alarming.\n\nOnly one of these is run per installation (that is many servers send Alarm UDP event packets to a single server).  \n\nThe server the property 'StatusServer' must point on all nodes to the role running the alarm thread.\n</pre>\n</html>");
    alarmButton.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        alarmButtonActionPerformed(evt);
      }
    });
    alarmPanel.add(alarmButton);

    udpchannelButton.setText("UdpChannel");
    udpchannelButton.setToolTipText("<html>\n<pre>\nEnable a UdpChannel thread which gathers summaries of every packet received and \nmaintains a list of channels and their last time data was received and the latency of that data.   \nThere is only one UdpChannel thread for an installation and all computer doing input \nsend the data summaries via a ChannelSender to this single node.\nAll summaries are send to the node set in property 'StatusServer' which is normally set in the edge_$SERVER.prop file.\nThis thread is the source of data on latency and for ChannelDisplay and many other clients.\n</pre>\n</html>");
    udpchannelButton.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        udpchannelButtonActionPerformed(evt);
      }
    });
    alarmPanel.add(udpchannelButton);

    snwButton.setText("SNW");
    snwButton.setToolTipText("<html>\n<PRE>\nEnable the SNW tracking thread which uses the channel information in UdpChannel \nand the SeisnetWatch groups to make alarms and status for those SNW groups and all networks. \nIt must be run in the alarm where the UdpChannel is running and the Alarm processing must be selected as well.\nNote: this thread must be selected for network and SNW group alarms to be generated.\nThis thread must also be run if latency and last minute received data is to be sent to a real SNW server.\n</PRE>\n</html>");
    alarmPanel.add(snwButton);

    snwSenderButton.setText("SNW Sender");
    snwSenderButton.setToolTipText("<html>\n<PRE>\nEnable this Thread (SNWSender) which sends information from the SNW thread to a SNW server.  \nWithout this the groups are maintained and alarmed, but the information is not forwarded to a SNW server.\nThe SNW, UdpChannel, thread must also be selected for this to work.\n</PRE>\n</html>");
    alarmPanel.add(snwSenderButton);

    cmdStatusButton.setText("CmdServer");
    cmdStatusButton.setToolTipText("<html>\n<PRE>\nEnable the CommandStatusServer thread which listens for connections from a \nEdgeConfig GUI and executes commands in the EdgeMaint panel.\nThis thread must be selected on every node that needs to have GUI based command enabled.\n</PRE>\n</html>");
    alarmPanel.add(cmdStatusButton);

    dbmsgButton.setText("DBMsgSrv");
    dbmsgButton.setToolTipText("<html>\n<pre>\nStart a DBMessageServer thread which takes text lines from various processes and creates MySQL rows.\n\nOnly one of these per installation is needed and is run on the server pointed to by 'StatusServer' property.\n</pre>\n</html>");
    alarmPanel.add(dbmsgButton);

    configButton.setText("Config ");
    configButton.setToolTipText("<html>\n<pre>\nStart a EdgeMomConfigThread which does all of the configuration of a node based \non the configuration in the database.  It converts information from the cpu, role, instance, and edge setup\nGUI tabs into the  crontab and configuration files for the node.\n\nThis must be enabled on all nodes that want configuration from the database.\n</pre>\n</html>");
    alarmPanel.add(configButton);

    latencyServer.setText("LatencySrv");
    latencyServer.setToolTipText("<html>\n<pre>\nStart a LatencyServer thread which provides latency information to clients like Fetcher.  \n\nFetcher uses this to throttle fetches if the realtime data is showing increased latency.\n\nThis is normally run on the same alarm instance as the UdpChannel thread \nwhich is pointed to by the 'StatusServer' property.\n</pre>\n</html>");
    alarmPanel.add(latencyServer);

    routesButton.setText("Routes");
    routesButton.setToolTipText("<html>\n<pre>\nStart a MakeVsatRoutes thread to control alternate back haul routing at the NEIC over the VSAT bypass.  \n\nThis thread updates the routing table on the the server in order to change the access route from \nVPNs through the NOCs to using the Golden VSAT.\n\nThis must be run on all roles which have stations on the VSAT and hence can be bypassed.\n</pre>\n</html>");
    alarmPanel.add(routesButton);

    bhcheckButton.setText("BH Check");
    bhcheckButton.setToolTipText("<html>\n<pre>\nStart a HughesNOCChecker thread to ping the Hughes NOC through both the VPN and VSATs \nand insure both of these backhauls are operating.  Sends UDP events to Alarm. \n\nNormally this is only run on one node and that node must have access to the NOC VPNs and the Golden VSAT.\n</pre>\n</html?");
    alarmPanel.add(bhcheckButton);

    webButton.setText("web");
    webButton.setToolTipText("<html>\n<pre>\nStart a WebStation thread to update a MySQL database with flow information based on the UdpChannel.  \nThis database drives the GSN Operations status pages (http://earthquake.usgs.gov/monitoring/operations/).\n</pre>\n</html>");
    alarmPanel.add(webButton);

    etchostsButton.setText("/etc/hosts");
    etchostsButton.setToolTipText("<html>\n<pre>\nStart a MakeEtcHosts thread which writes out the /etc/hosts table after adding entries for all RTS, Q330s, VSAT gateways etc.  \n\nMakes \"ping isco\", \"ping isco-gw\", ping isco-q330,  and \"ping flwy-rt\" possible.\n</html>\n</pre>");
    alarmPanel.add(etchostsButton);

    jSeparator1.setMinimumSize(new java.awt.Dimension(550, 10));
    jSeparator1.setPreferredSize(new java.awt.Dimension(550, 10));
    alarmPanel.add(jSeparator1);

    argsLabel.setText("Alarm args:");
    argsLabel.addFocusListener(new java.awt.event.FocusAdapter() {
      public void focusLost(java.awt.event.FocusEvent evt) {
        argsLabelFocusLost(evt);
      }
    });
    alarmPanel.add(argsLabel);

    alarmArgs.setColumns(90);
    alarmArgs.setFont(new java.awt.Font("Monospaced", 0, 10)); // NOI18N
    alarmArgs.setToolTipText("<html>\nMost of the alarm configuration comes from selecting the switchs for the threads to run.  However, additional switches to change thread behavior can be added here.\n<PRE>\n Arg    Description\n -noalarm    Disable processing of UDPEvent packets and alarming by this node (no SNW work is possible)\n -notrap     Do not set up a SNMP trap handler (this is now the default)\n -trap       Set up a SNMP trap handler\n -replicate  Forward all UDP events to the given IP and port\n -repIP      The replication target IP address (NAGIOS)\n -repPort    The port n the repIP to send the replication packets\n -nosnwevent      Do not do SNW group monitoring and generate events on those groups (SNWGroupEventProcess)\n -dbgmail    If present turn on debug output when s SimpleSMTPTHread tries to send mail\n -bind IP1:IP2:port1:port2 Use these two ips are ports to bind the local end of the connection, emailAlt will use 2nd binding\n -dbg        Turn on more debug output\n -qmom file  File with lines like ip:cwbport:qport:CH1:CH2... Set up a test monitor of a QueryMom on the given ip and ports and chans (channels have - for space)\n -qport file Read in the config file and setup MonitorPorts for each.  Form of file is \"ipadr:port:[process[:account[:action]]]\n -noDB       This Alarm is only for relaying or logging messages, it has no DB so it cannot take any action\n -udphb         Send heartbeat UDP packets to configured edge based field stations (no normally used outside NEIC)\n -nodbmsg    Do not start a DBMessageServer on port 7985 - this would be very unusual as it does not harm\n -noudpchan  Do not start a UdpChannel server - this would be very unusual as it does no harm\n  \n Switches for the UdpChannel thread which feeds channel data to ChannelDisplay and other clients\n -web       Start a WebStationCLient to keep USGS operational web page status up-to-date (NEIC only)\n -snw       Start a SNWChannelClient to check for rate changes, new stations and channels etc\n -snwsender Send information to SNW Server from the SNWChannelClient (i.e. you are running a SNW server and need flow data)\n -mod50     Set the modulus for lastdata updates to 50 (def=20) - the lastdata will be updated about every modulus*3 minutes\n \n Switches for DBMessageServer :\n -p    port   Set the port number (default=7985)\n</PRE>\n</html>");
    alarmArgs.setMaximumSize(new java.awt.Dimension(500, 28));
    alarmArgs.setMinimumSize(new java.awt.Dimension(450, 28));
    alarmArgs.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        alarmArgsActionPerformed(evt);
      }
    });
    alarmPanel.add(alarmArgs);

    neicOptions.setText("Show NEIC Only Options");
    neicOptions.setToolTipText("If selected, certain alarm and special processes will be shown on this form.  These processes are used at the NEIC and are not generally useful outside of there.");
    neicOptions.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        neicOptionsActionPerformed(evt);
      }
    });
    alarmPanel.add(neicOptions);

    neicPanel.setBorder(javax.swing.BorderFactory.createEtchedBorder());
    neicPanel.setMinimumSize(new java.awt.Dimension(550, 75));
    neicPanel.setPreferredSize(new java.awt.Dimension(550, 75));

    labMDS.setText("MetaDataSrv:");
    neicPanel.add(labMDS);

    mds.setColumns(4);
    mds.setToolTipText("The heap memory for this process in megabytes (mB).    Zero means the process is not run. Negative means run in with -test.");
    mds.setMinimumSize(new java.awt.Dimension(40, 28));
    mds.setPreferredSize(new java.awt.Dimension(40, 28));
    mds.addFocusListener(new java.awt.event.FocusAdapter() {
      public void focusLost(java.awt.event.FocusEvent evt) {
        mdsFocusLost(evt);
      }
    });
    neicPanel.add(mds);

    labQML.setText("QuakeML:");
    neicPanel.add(labQML);

    quakeml.setColumns(4);
    quakeml.setToolTipText("The heap memory for this process in megabytes (mB).    Zero means the process is not run.");
    quakeml.setMinimumSize(new java.awt.Dimension(40, 28));
    quakeml.setPreferredSize(new java.awt.Dimension(40, 28));
    quakeml.addFocusListener(new java.awt.event.FocusAdapter() {
      public void focusLost(java.awt.event.FocusEvent evt) {
        quakemlFocusLost(evt);
      }
    });
    neicPanel.add(quakeml);

    labConsole.setText("Console (mB):");
    neicPanel.add(labConsole);

    console.setColumns(4);
    console.setToolTipText("The heap memory for this process in megabytes (mB).  Zero do not run.  Negative means run in with -test.");
    console.setFocusTraversalKeysEnabled(false);
    console.setMinimumSize(new java.awt.Dimension(40, 28));
    console.setPreferredSize(new java.awt.Dimension(40, 28));
    console.addFocusListener(new java.awt.event.FocusAdapter() {
      public void focusLost(java.awt.event.FocusEvent evt) {
        consoleFocusLost(evt);
      }
    });
    neicPanel.add(console);

    labAftac.setText("AftacHold:");
    neicPanel.add(labAftac);

    aftac.setColumns(4);
    aftac.setToolTipText("The heap memory for this process in megabytes (mB).    Zero means the process is not run. Negative means run in with -test.");
    aftac.setMinimumSize(new java.awt.Dimension(40, 28));
    aftac.setPreferredSize(new java.awt.Dimension(40, 28));
    aftac.addFocusListener(new java.awt.event.FocusAdapter() {
      public void focusLost(java.awt.event.FocusEvent evt) {
        aftacFocusLost(evt);
      }
    });
    neicPanel.add(aftac);

    labRTS.setText("RTS:");
    neicPanel.add(labRTS);

    rts.setColumns(4);
    rts.setToolTipText("The heap memory for this process in megabytes (mB).    Zero means the process is not run. Negative means run in with -test.");
    rts.setMinimumSize(new java.awt.Dimension(40, 28));
    rts.setPreferredSize(new java.awt.Dimension(40, 28));
    rts.addFocusListener(new java.awt.event.FocusAdapter() {
      public void focusLost(java.awt.event.FocusEvent evt) {
        rtsFocusLost(evt);
      }
    });
    neicPanel.add(rts);

    labSMG.setText("SMGetter:");
    neicPanel.add(labSMG);

    smgetter.setColumns(4);
    smgetter.setToolTipText("The heap memory for this process in megabytes (mB).    Zero means the process is not run. Negative means run in with -test.");
    smgetter.setMinimumSize(new java.awt.Dimension(40, 28));
    smgetter.setPreferredSize(new java.awt.Dimension(40, 28));
    smgetter.addFocusListener(new java.awt.event.FocusAdapter() {
      public void focusLost(java.awt.event.FocusEvent evt) {
        smgetterFocusLost(evt);
      }
    });
    neicPanel.add(smgetter);

    labNeic.setText("Special Processes:");
    neicPanel.add(labNeic);

    alarmPanel.add(neicPanel);

    mainTabs.addTab("Alarm & Other Process Config", alarmPanel);

    labCrontab.setText("Custom Crontab:");
    crontabPanel.add(labCrontab);

    crontabScroll.setMinimumSize(new java.awt.Dimension(550, 290));
    crontabScroll.setPreferredSize(new java.awt.Dimension(550, 290));

    crontab.setColumns(80);
    crontab.setFont(new java.awt.Font("Monospaced", 0, 10)); // NOI18N
    crontab.setRows(30);
    crontab.setToolTipText("<html>\nEnter crontab entries for custom processing.   \n<br>This normal is once a day processes and scripts such as updateHoldings, database clean up, or other special scripts that are related to the role.  \n<br>Most of the one time scripts can be in the \"cpu\" panel and few are known to be best put here.\n<PRE>\n\nNote : the chkCWB and chkNEIC scripts can be invoked starting with these tags and omitting the output log. \nThe configuration software will make a crontab line which will be run every minute and \nthe log will go to '>>LOG/chkcwb.log 2>&1'.\n\nSymbols that can be present and substituted :\n$INSTANCE  replaced with the instance setting.  Useful in ring file names.\n$ROLE replaced with the role name or primary role name\n$SERVER replaced with the physical node/cpu system name.\n</PRE>\n</html>\n");
    crontabScroll.setViewportView(crontab);

    crontabPanel.add(crontabScroll);

    setDefaultCrontab.setText("Set Default Crontab");
    setDefaultCrontab.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        setDefaultCrontabActionPerformed(evt);
      }
    });
    crontabPanel.add(setDefaultCrontab);

    mainTabs.addTab("Manual Crontab", crontabPanel);

    lblDesc.setText("Description : ");
    configPanel.add(lblDesc);

    descScroll.setMinimumSize(new java.awt.Dimension(550, 150));
    descScroll.setPreferredSize(new java.awt.Dimension(550, 150));

    description.setColumns(40);
    description.setFont(new java.awt.Font("Monospaced", 0, 10)); // NOI18N
    description.setRows(10);
    description.setToolTipText("Enter a description that breifly describes what this role is used for.");
    descScroll.setViewportView(description);

    configPanel.add(descScroll);

    labConfig.setText("Current Config:");
    configPanel.add(labConfig);

    configScrollPane.setToolTipText("<html>\n<pre>\nThis area contains a summary of all of the configuration nodes, \nwhat accounts are active on those nodes.  \nIt is NOT editable.\n</pre>\n</html>");
    configScrollPane.setMinimumSize(new java.awt.Dimension(550, 140));
    configScrollPane.setPreferredSize(new java.awt.Dimension(550, 140));

    config.setEditable(false);
    config.setBackground(new java.awt.Color(204, 204, 204));
    config.setColumns(70);
    config.setFont(new java.awt.Font("Monospaced", 0, 12)); // NOI18N
    config.setRows(30);
    config.setMinimumSize(new java.awt.Dimension(490, 12));
    configScrollPane.setViewportView(config);

    configPanel.add(configScrollPane);

    mainTabs.addTab("ConfigSummary", configPanel);

    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 4;
    add(mainTabs, gridBagConstraints);

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
    ID.setColumns(8);
    ID.setMinimumSize(new java.awt.Dimension(80, 28));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 17;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(ID, gridBagConstraints);

    labID.setText("ID :");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 17;
    add(labID, gridBagConstraints);

    error.setEditable(false);
    error.setColumns(40);
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
      /*if(!accounts.getText().equals(changeAccounts.getText())) {
        obj.setString("accounts", changeAccounts.getText());
        // So we need to update the edgemomsetup so that this role changes accounts as documented
        // and that the names of the file also change.
        String [] oldAccounts = accounts.getText().replaceAll("  ", " ").replaceAll("  ", " ").split(" ");
        String [] newAccounts = changeAccounts.getText().replaceAll("  ", " ").replaceAll("  ", " ").split(" ");
        if(oldAccounts.length != newAccounts.length) return;
        String roleString = ((RoleInstance) role.getSelectedItem()).getRole();
        int roleID = ((RoleInstance) role.getSelectedItem()).getID();
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
      else */
      obj.setString("accounts", accounts.getText());
      int nowCpuID = (cpuID.getSelectedIndex() == -1) ? 0: ((Cpu) cpuID.getSelectedItem()).getID();
      obj.setInt("cpuID", nowCpuID);
      obj.setInt("failover", (failover.isSelected()?1:0));
      int nowFailoverCpuID = (failoverCpuID.getSelectedIndex() == -1) ? 0: ((Cpu) failoverCpuID.getSelectedItem()).getID();
      obj.setInt("failovercpuid", nowFailoverCpuID);
      obj.setInt("alarm", alarmButton.isSelected()?1:0);
      obj.setString("crontab", Util.chkTrailingNewLine(crontab.getText()));
      obj.setInt("alarmbhchecker", bhcheckButton.isSelected()?1:0);
      obj.setInt("alarmcmdstatus", cmdStatusButton.isSelected()?1:0);
      obj.setInt("alarmconfig", configButton.isSelected()?1:0);
      obj.setInt("alarmdbmsgsrv", dbmsgButton.isSelected()?1:0);
      obj.setInt("alarmetchosts", etchostsButton.isSelected()?1:0);
      obj.setInt("alarmlatency", latencyServer.isSelected()?1:0);
      obj.setInt("alarmroutes", routesButton.isSelected()?1:0);
      obj.setInt("alarmsnw", snwButton.isSelected()?1:0);
      obj.setInt("alarmsnwsender", snwSenderButton.isSelected()?1:0);
      obj.setInt("alarmudpchannel",udpchannelButton.isSelected()?1:0);
      obj.setInt("alarmwebserver", webButton.isSelected()?1:0);
      obj.setString("alarmargs",alarmArgs.getText());
      obj.setInt("alarmmem",alarmInt);
      obj.setInt("aftac",aftacInt);
      obj.setInt("consoles",consolesInt);
      obj.setInt("metadata",metadataInt);
      obj.setInt("quakeml",quakemlInt);
      obj.setInt("querymom",querymomInt);
      obj.setInt("rts",rtsInt);
      obj.setInt("tcpholdings",tcpholdingsInt);
      obj.setInt("smgetter",smgetterInt);
      

      // Do not change
      obj.updateRecord();
      //if(origCpuID != nowCpuID) {
        DBConnectionThread dbconn = DBConnectionThread.getThread("edge");
        if(origCpuID > 0) dbconn.executeUpdate("UPDATE edge.cpu set hasdata=1 where ID="+origCpuID);
        if(nowCpuID > 0) dbconn.executeUpdate("UPDATE edge.cpu set hasdata=1 where ID="+nowCpuID);
        if(origFailedOver != failover.isSelected()) {
          if(nowFailoverCpuID > 0) dbconn.executeUpdate("UPDATE edge.cpu set hasdata=1 where id="+nowFailoverCpuID);
          
        }
        if(role.getSelectedIndex() >= 0)
          if(!(role.getSelectedItem() instanceof String)) 
            dbconn.executeUpdate("UPDATE edge.role set hasdata=1 WHERE ID="+((RoleInstance) role.getSelectedItem()).getID());
      //}
      RoleInstance.roleInstances=null;       // force reload of combo box
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
  CpuPanelInstance.getJComboBox(cpuID);
  clearScreen();
}//GEN-LAST:event_rebuildActionPerformed

private void cpuIDActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_cpuIDActionPerformed
  chkForm();
}//GEN-LAST:event_cpuIDActionPerformed

  private void udpchannelButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_udpchannelButtonActionPerformed
    // TODO add your handling code here:
  }//GEN-LAST:event_udpchannelButtonActionPerformed

  private void alarmButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_alarmButtonActionPerformed
    // TODO add your handling code here:
  }//GEN-LAST:event_alarmButtonActionPerformed

  private void neicOptionsActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_neicOptionsActionPerformed
    neicPanel.setVisible(neicOptions.isSelected());
    bhcheckButton.setVisible(neicOptions.isSelected());
    etchostsButton.setVisible(neicOptions.isSelected());
    routesButton.setVisible(neicOptions.isSelected());
    webButton.setVisible(neicOptions.isSelected());
    labNeic.setVisible(neicOptions.isSelected());
    
    chkForm();
  }//GEN-LAST:event_neicOptionsActionPerformed

  private void minButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_minButtonActionPerformed
    alarmButton.setSelected(false);
    udpchannelButton.setSelected(false);
    cmdStatusButton.setSelected(true);
    dbmsgButton.setSelected(true);
    configButton.setSelected(true);
    latencyServer.setSelected(false);
    snwButton.setSelected(false); 
    snwSenderButton.setSelected(false);
    bhcheckButton.setSelected(false);
    etchostsButton.setSelected(false);
    routesButton.setSelected(false);
    webButton.setSelected(false);
    chkForm();
  }//GEN-LAST:event_minButtonActionPerformed

  private void normButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_normButtonActionPerformed
    alarmButton.setSelected(true);
    udpchannelButton.setSelected(true);
    cmdStatusButton.setSelected(true);
    dbmsgButton.setSelected(true);
    configButton.setSelected(true);
    latencyServer.setSelected(true);
    snwButton.setSelected(true); 
    snwSenderButton.setSelected(false);
    bhcheckButton.setSelected(false);
    etchostsButton.setSelected(false);
    routesButton.setSelected(false);
    webButton.setSelected(false);
    chkForm(); 
  }//GEN-LAST:event_normButtonActionPerformed

  private void querymomFocusLost(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_querymomFocusLost
    chkForm();
  }//GEN-LAST:event_querymomFocusLost

  private void alarmFocusLost(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_alarmFocusLost
    chkForm();
  }//GEN-LAST:event_alarmFocusLost

  private void argsLabelFocusLost(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_argsLabelFocusLost
    chkForm();
  }//GEN-LAST:event_argsLabelFocusLost

  private void tcpholdingsFocusLost(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_tcpholdingsFocusLost
    chkForm();
  }//GEN-LAST:event_tcpholdingsFocusLost

  private void mdsFocusLost(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_mdsFocusLost
    chkForm();
  }//GEN-LAST:event_mdsFocusLost

  private void quakemlFocusLost(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_quakemlFocusLost
    chkForm();
  }//GEN-LAST:event_quakemlFocusLost

  private void consoleFocusLost(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_consoleFocusLost
    chkForm();
  }//GEN-LAST:event_consoleFocusLost

  private void aftacFocusLost(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_aftacFocusLost
    chkForm();
  }//GEN-LAST:event_aftacFocusLost

  private void rtsFocusLost(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_rtsFocusLost
    chkForm();
  }//GEN-LAST:event_rtsFocusLost

  private void smgetterFocusLost(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_smgetterFocusLost
    chkForm();
  }//GEN-LAST:event_smgetterFocusLost

  private void alarmArgsActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_alarmArgsActionPerformed
    // TODO add your handling code here:
  }//GEN-LAST:event_alarmArgsActionPerformed

  private void setDefaultCrontabActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_setDefaultCrontabActionPerformed
    crontab.setText(defaultCrontab);
  }//GEN-LAST:event_setDefaultCrontabActionPerformed
  private final StringBuilder conf = new StringBuilder(100);
  private void updateConfig() {
    config.setText("");
    Util.clear(conf);
    conf.append("Role     Assigned     Accounts/Instances\n");
    try {
      ResultSet rs = DBConnectionThread.getThread(DBConnectionThread.getDBSchema()).executeQuery(
              "SELECT DISTINCT role,account,cpuid,instance FROM edge.instance,role WHERE role.id=roleid order by role,account,instance");
      String lastRole="";
      String lastAccount="";
      while(rs.next()) {
        if(!lastRole.equals(rs.getString("role"))) {
          Cpu cpu = CpuPanel.getCpuWithID(rs.getInt("cpuid"));
          conf.append(lastRole.equals("")? "":"\n").append(Util.rightPad(rs.getString("role"), 8)).append(" on ").append(Util.rightPad( (cpu == null? "null" : cpu.getCpu()), 8)).append(" - ");
          if(!lastRole.equals("")) lastAccount="";
        }
        lastRole = rs.getString("role");
        if(!lastAccount.equals(rs.getString("account"))) {
          conf.append(" ").append(rs.getString("account")).append(" : ");
          lastAccount=rs.getString("account");
        }
        conf.append(Util.rightPad(rs.getString("instance"), 4));
      }
      config.setText(conf.toString());
    }
    catch(SQLException e) {}
    config.setCaretPosition(0);
  }
  
  // Variables declaration - do not modify//GEN-BEGIN:variables
  private javax.swing.JTextField ID;
  private javax.swing.JTextField accounts;
  private javax.swing.JButton addUpdate;
  private javax.swing.JTextField aftac;
  private javax.swing.JTextField alarm;
  private javax.swing.JTextField alarmArgs;
  private javax.swing.JRadioButton alarmButton;
  private javax.swing.JPanel alarmPanel;
  private javax.swing.JLabel argsLabel;
  private javax.swing.JRadioButton bhcheckButton;
  private javax.swing.JTextField changeRole;
  private javax.swing.JRadioButton cmdStatusButton;
  private javax.swing.JTextArea config;
  private javax.swing.JRadioButton configButton;
  private javax.swing.JPanel configPanel;
  private javax.swing.JScrollPane configScrollPane;
  private javax.swing.JTextField console;
  private javax.swing.JComboBox cpuID;
  private javax.swing.JTextArea crontab;
  private javax.swing.JPanel crontabPanel;
  private javax.swing.JScrollPane crontabScroll;
  private javax.swing.JRadioButton dbmsgButton;
  private javax.swing.JButton delete;
  private javax.swing.JScrollPane descScroll;
  private javax.swing.JTextArea description;
  private javax.swing.JTextField error;
  private javax.swing.JRadioButton etchostsButton;
  private javax.swing.JRadioButton failover;
  private javax.swing.JComboBox failoverCpuID;
  private java.awt.GridBagLayout gridBagLayout1;
  private javax.swing.JRadioButton hasdata;
  private javax.swing.JTextField ipadr;
  private javax.swing.JLabel jLabel1;
  private javax.swing.JSeparator jSeparator1;
  private javax.swing.JSeparator jSeparator2;
  private javax.swing.JLabel labAccounts;
  private javax.swing.JLabel labAftac;
  private javax.swing.JLabel labAlarm;
  private javax.swing.JLabel labChangeRole;
  private javax.swing.JLabel labConfig;
  private javax.swing.JLabel labConsole;
  private javax.swing.JLabel labCrontab;
  private javax.swing.JLabel labFailCpu;
  private javax.swing.JLabel labID;
  private javax.swing.JLabel labMDS;
  private javax.swing.JLabel labNeic;
  private javax.swing.JLabel labQML;
  private javax.swing.JLabel labQuerymom;
  private javax.swing.JLabel labRTS;
  private javax.swing.JLabel labSMG;
  private javax.swing.JLabel labTcpH;
  private javax.swing.JLabel labThreads;
  private javax.swing.JRadioButton latencyServer;
  private javax.swing.JLabel lblCPU;
  private javax.swing.JLabel lblDesc;
  private javax.swing.JLabel lblIpadr;
  private javax.swing.JTabbedPane mainTabs;
  private javax.swing.JTextField mds;
  private javax.swing.JButton minButton;
  private javax.swing.JRadioButton neicOptions;
  private javax.swing.JPanel neicPanel;
  private javax.swing.JButton normButton;
  private javax.swing.JTextField quakeml;
  private javax.swing.JTextField querymom;
  private javax.swing.JButton rebuild;
  private javax.swing.JComboBox role;
  private javax.swing.JRadioButton routesButton;
  private javax.swing.JTextField rts;
  private javax.swing.JButton setDefaultCrontab;
  private javax.swing.JTextField smgetter;
  private javax.swing.JRadioButton snwButton;
  private javax.swing.JRadioButton snwSenderButton;
  private javax.swing.JTextField tcpholdings;
  private javax.swing.JPanel topPanel;
  private javax.swing.JRadioButton udpchannelButton;
  private javax.swing.JRadioButton webButton;
  // End of variables declaration//GEN-END:variables
  /** Creates new form RolePanel */
  public RoleInstancePanel() {
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
    UC.Look(neicPanel);
    UC.Look(topPanel);
    UC.Look(alarmPanel);
    UC.Look(mainTabs);
    UC.Look(crontabPanel);
    UC.Look(configPanel);
    UC.Look(alarmPanel);
    UC.Look(crontabPanel);
    UC.Look(configPanel);
    UC.Look(alarmButton);
    UC.Look(bhcheckButton);
    UC.Look(cmdStatusButton);
    UC.Look(configButton);
    UC.Look(dbmsgButton);
    UC.Look(etchostsButton);
    UC.Look(failover);
    UC.Look(hasdata);
    UC.Look(latencyServer);
    UC.Look(neicOptions);
    UC.Look(routesButton);
    UC.Look(snwButton);
    UC.Look(snwSenderButton);
    UC.Look(udpchannelButton);
    UC.Look(webButton);


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
    for (RoleInstance v1 : RoleInstance.roleInstances) {
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
    //Util.prt("RoleInstancePanel.setJComboBoxToID id="+ID);
    for(int i=0; i<b.getItemCount(); i++) 
      if(((RoleInstance) b.getItemAt(i)).getID() == ID) {
          b.setSelectedIndex(i);
          //Util.prt("RoleInstancePanel.setJComboBoxToID id="+ID+" found at "+i);
      }
  }  
  
  /** Given a database ID find the index into a JComboBox consistent with this panel
   *@param ID The row ID from the database
   *@return The index in the JComboBox with this ID
   */
  public static int getJComboBoxIndex(int ID) {
    if(RoleInstance.roleInstances == null) makeRoles();
    for(int i=0; i<RoleInstance.roleInstances.size(); i++) if( ID ==  RoleInstance.roleInstances.get(i).getID()) return i;
    return -1;
  }
  /** Cause the main JComboBox to be reloaded. */
  public static void reload() {
    RoleInstance.roleInstances = null;
    makeRoles();
  }
  /** return a vector with all of the RoleInstance
   * @return The vector with the role
   */
  public static ArrayList<RoleInstance> getRoleVector() {
    if(RoleInstance.roleInstances == null) makeRoles();
    return RoleInstance.roleInstances;
  }
  /** Get the item corresponding to database ID
   *@param ID the database Row ID
   *@return The RoleInstance row with this ID
   */
  public static RoleInstance getRoleWithID(int ID) {
    if(RoleInstance.roleInstances == null) makeRoles();
    int i=getJComboBoxIndex(ID);
    if(i >= 0) return RoleInstance.roleInstances.get(i);
    else return null;
  }
  
  // This routine should only need tweeking if key field is not same as table name
  private static void makeRoles() {
    RoleInstance.makeRoles();
    
  }
  
  // No changes needed
  private void find() {
    if(role == null) return;
    if(initiating) return;
    RoleInstance l;
    if(role.getSelectedIndex() == -1) {
      if(role.getSelectedItem() == null) return;
      l = newOne();
    } 
    else {
      l = (RoleInstance) role.getSelectedItem();
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
      Show.inFrame(new RoleInstancePanel(), UC.XSIZE, UC.YSIZE);
    }
    catch(InstantiationException e) {}
  }
}
