/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package gov.usgs.anss.db;
import gov.usgs.anss.util.ErrorTrack;
import gov.usgs.anss.util.FUtil;
import gov.usgs.anss.util.Show;
import gov.usgs.anss.util.Util;
import java.awt.Color;
import java.io.*;
import java.sql.*;
import java.util.*;
import javax.swing.JOptionPane;

/** This GUI aids in setting up a new NODE for simple imputs and possible NEIC output.
 * 
 * You can also do a command line configuration as described below.  This GUI writes out
 * several .prop files, updates the EdgeMomSetup and EdgeFIle tables with a configuration 
 * based on the standard configuration.  The user normally has to run the full GUI to clean
 * up thinks like alive strings in IMPORTs.  You can set the path here with a file, but it 
 * is probably easier to use the GUIs.
 * 
 *<pre>
 *   * To do a manual mode configuration :");
  * dbsetup -gui -man [-h host][-ip ip.adr][-db ip.adr][-neic regexp][-import ip.adr:port][-trace ip.adr:port][-email e.adr][-path file][-smtp ip.adr][-alarm ip.adr][-status ip.adr][-nodenum nn][-snw ip.adr:port][-vendor mysql
  * -h  hostname  Like gldketchum
  * -ip ip.adr    The numeric IP address of the the host from -h
  * -db     ip.adr/port The IP address of the database server controlling this configuration (def=localhost)
  * -neic regexp  A regular expression of channels in NNSSSSSCCCLL to send to neic everything='.*',  seismic='......[BESH][HN].*'
  * -import ip.adr:port Get data from an Earthworm IMPORT at this IP and port (you must edit the EW/*.config for alive messages
  * -trace  ip.adr:port Get UDP trace data from the IP address (broadcast adr) and port
  * -email  email       Send emails of all errors to this person(def=ketchum@usgs.gov)
  * -path   filename    Filename contains a path setup for this node including npaths, nday?, and datapath? (def is 1 path on /data for 10 days)
  * -smtp   ip.adr      Set the SMTP server to this IP for e-mails sent from the node (def=136.177.7.24)
  * -alarm  ip.adr      IP address of Alarm server program (def=localhost)
  * -status ip.adr      IP address of the UdpChannel and TcpHoldings servers (def=localhost)
  * -nodenum nn         A number to add to this nodename (which does not end in a number) to keep files unique
  * -snw    ip.adr:port The IP and port of a SeisNetWatch SocketServer for contribuiting latency data to SNW(def=No SNW)
  * -vendor mysql|postgres The vendor of the database to use - this sets the port number to standard ports(def=mysql)

* </pre>
 * @author U.S. Geological Survey <ketchum at usgs.gov>
 */
public class DBSetupGUI extends javax.swing.JPanel {
  private TreeMap<String, DBConnectionThread> db = new TreeMap<String, DBConnectionThread>(); 
  private DBConnectionThread root;
  boolean newMode=false;
  private PreparedStatement create,select,grant,grantlocal,setpassword,setpasswordlocal;
  ErrorTrack err=new ErrorTrack();

  String [] databases  = {"anss","edge","alarm","fetcher","metadata", "status"};
  /**
   * Creates new form DBSetupGUI
   */
  public DBSetupGUI() {
    initComponents();
    newMode=true;
    newButtonActionPerformed(null);
    makeSymbolicLinks.setVisible(false);
    setAllInvisible();
    rootLoginPanel.setVisible(true);
    command.setVisible(false);
  }
  private void setAllInvisible() {
    makeSymbolicLinks.setVisible(false);
    accounts.setVisible(false);
    labAccounts.setVisible(false);
    newPanel.setVisible(false);
    keyPanel.setVisible(false);
    newUserPanel.setVisible(false);
    textPanel.setVisible(false);
    rootLoginPanel.setVisible(false);
    addUpdate.setVisible(false);
    servers.setVisible(false);
    delete.setVisible(false);
    newInstall.setVisible(false);
    importPort.setVisible(false);
    importIP.setVisible(false);
    labImportPort.setVisible(false);
    labImportIP.setVisible(false);
    labTracePort.setVisible(false);
    tracePort.setVisible(false);
    labTraceIP.setVisible(false);
    traceIP.setVisible(false);
    labNEICSend.setVisible(false);
    neicSendRE.setVisible(false);
    setDBs();
  }
  private void setDBs() {
    String s = "";
    for(int i=0; i<databases.length; i++) {
      s += databases[i]+" ";
    }
    dbs.setText(s);
  }
  public static String randomPassword() {
    Random ran = new Random(System.currentTimeMillis());  // pick a seed
    int l = (int) (ran.nextDouble()*3+10.);
    String s = "";
    for(int i=0; i<l; i++) {
      int j = (int) (ran.nextDouble()*79.);
      if(j < 26) s+= (char) ('A'+j);
      else if(j < 52) s += (char) ('a'+j-26);
      else if(j < 62) s += (char) ('0'+j-52);
      else if(j == 62) s+="$";
      else if(j == 63) s+="!";
      else if(j == 64) s+="%";
      else if(j == 65) s+="&";
      else if(j == 66) s+="/";
      else if(j == 67) s+="\\";
      else if(j == 68) s+="?";
      else if(j == 69) s+="*";
      else if(j == 70) s+=";";
      else if(j == 71) s+="<";
      else if(j == 72) s+="@";
      else if(j == 73) s+="#";
      else if(j == 74) s+="+";
      else if(j == 75) s+="]";
      else if(j == 76) s+="[";
      else if(j == 77) s+="}";
      else if(j == 78) s+="{";

    }
    return s;
  }
  /**
   * This method is called from within the constructor to initialize the form.
   * WARNING: Do NOT modify this code. The content of this method is always
   * regenerated by the Form Editor.
   */
  @SuppressWarnings("unchecked")
  // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
  private void initComponents() {
    java.awt.GridBagConstraints gridBagConstraints;

    error = new javax.swing.JTextField();
    rootLoginPanel = new javax.swing.JPanel();
    labRootIP = new javax.swing.JLabel();
    rootIP = new javax.swing.JTextField();
    labRootVendor = new javax.swing.JLabel();
    rootVendor = new javax.swing.JComboBox();
    jLabel2 = new javax.swing.JLabel();
    privUser = new javax.swing.JTextField();
    labRootPw = new javax.swing.JLabel();
    password = new javax.swing.JPasswordField();
    command = new javax.swing.JComboBox();
    newInstall = new javax.swing.JPanel();
    labInstallation = new javax.swing.JLabel();
    installation = new javax.swing.JTextField();
    labHostIP = new javax.swing.JLabel();
    hostIP = new javax.swing.JTextField();
    hostIP.setText(Util.getLocalHostIP());
    labDB = new javax.swing.JLabel();
    dbIP = new javax.swing.JTextField();
    labSMTP = new javax.swing.JLabel();
    smtpIP = new javax.swing.JTextField();
    labAlarm = new javax.swing.JLabel();
    alarmIP = new javax.swing.JTextField();
    labSNW = new javax.swing.JLabel();
    snwIP = new javax.swing.JTextField();
    labSNWP = new javax.swing.JLabel();
    snwPort = new javax.swing.JTextField();
    labStatus = new javax.swing.JLabel();
    statusIP = new javax.swing.JTextField();
    labEmail = new javax.swing.JLabel();
    emailTo = new javax.swing.JTextField();
    labPaths = new javax.swing.JLabel();
    jScrollPane1 = new javax.swing.JScrollPane();
    paths = new javax.swing.JTextArea();
    doConfig = new javax.swing.JButton();
    sendToNEIC = new javax.swing.JRadioButton();
    labNodeNum = new javax.swing.JLabel();
    nodeNumber = new javax.swing.JTextField();
    labImportIP = new javax.swing.JLabel();
    importIP = new javax.swing.JTextField();
    labImportPort = new javax.swing.JLabel();
    importPort = new javax.swing.JTextField();
    labTraceIP = new javax.swing.JLabel();
    traceIP = new javax.swing.JTextField();
    labTracePort = new javax.swing.JLabel();
    tracePort = new javax.swing.JTextField();
    enableImport = new javax.swing.JRadioButton();
    enableTrace = new javax.swing.JRadioButton();
    labNEICSend = new javax.swing.JLabel();
    neicSendRE = new javax.swing.JTextField();
    keyPanel = new javax.swing.JPanel();
    labPath = new javax.swing.JLabel();
    path = new javax.swing.JTextField();
    labKey = new javax.swing.JLabel();
    key = new javax.swing.JTextField();
    changeKey = new javax.swing.JButton();
    delete = new javax.swing.JButton();
    newButton = new javax.swing.JButton();
    labServers = new javax.swing.JLabel();
    servers = new javax.swing.JComboBox();
    newPanel = new javax.swing.JPanel();
    labIP = new javax.swing.JLabel();
    ipadr = new javax.swing.JTextField();
    jLabel1 = new javax.swing.JLabel();
    dbVendor = new javax.swing.JComboBox();
    labDBPort = new javax.swing.JLabel();
    dbPort = new javax.swing.JTextField();
    textPanel = new javax.swing.JScrollPane();
    text = new javax.swing.JTextArea();
    newUserPanel = new javax.swing.JPanel();
    labUser = new javax.swing.JLabel();
    username = new javax.swing.JTextField();
    labUserPW = new javax.swing.JLabel();
    userPassword = new javax.swing.JTextField();
    addUser = new javax.swing.JButton();
    labDbs = new javax.swing.JLabel();
    dbs = new javax.swing.JTextField();
    addUpdate = new javax.swing.JButton();
    makeSymbolicLinks = new javax.swing.JButton();
    labAccounts = new javax.swing.JLabel();
    accounts = new javax.swing.JTextField();

    setMinimumSize(new java.awt.Dimension(650, 450));

    error.setBackground(new java.awt.Color(204, 204, 204));
    error.setColumns(50);
    add(error);

    labRootIP.setText("IP of MySQL server:");
    rootLoginPanel.add(labRootIP);

    rootIP.setText("localhost");
    rootLoginPanel.add(rootIP);

    labRootVendor.setText("DB Vendor:");
    rootLoginPanel.add(labRootVendor);

    rootVendor.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "MySQL", "PostgreSQL" }));
    rootVendor.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        rootVendorActionPerformed(evt);
      }
    });
    rootLoginPanel.add(rootVendor);

    jLabel2.setText("PrivUser:");
    rootLoginPanel.add(jLabel2);

    privUser.setText("root");
    privUser.setToolTipText("User name with enough privilege to create user (root or other admin account)");
    privUser.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        privUserActionPerformed(evt);
      }
    });
    rootLoginPanel.add(privUser);

    labRootPw.setText("Root Password:");
    rootLoginPanel.add(labRootPw);

    password.setText("jPasswordField1");
    password.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        passwordActionPerformed(evt);
      }
    });
    rootLoginPanel.add(password);

    add(rootLoginPanel);

    command.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "Choose a Command", "Setup Update and Readonly Accts", "Create/Modify User", "Create NEW Install Configuration" }));
    command.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        commandActionPerformed(evt);
      }
    });
    add(command);

    newInstall.setMinimumSize(new java.awt.Dimension(650, 500));
    newInstall.setPreferredSize(new java.awt.Dimension(650, 500));
    newInstall.setSize(new java.awt.Dimension(100, 400));
    newInstall.setLayout(new java.awt.GridBagLayout());

    labInstallation.setText("Node:");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_END;
    newInstall.add(labInstallation, gridBagConstraints);

    installation.setColumns(6);
    installation.setText("gldketchum");
    installation.setToolTipText("This must match the host name of the computer being configured, that is the host returned by a 'uname -n'.");
    installation.setMinimumSize(new java.awt.Dimension(86, 28));
    installation.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        installationActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
    newInstall.add(installation, gridBagConstraints);

    labHostIP.setText("IPAdr:");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_END;
    newInstall.add(labHostIP, gridBagConstraints);

    hostIP.setColumns(12);
    hostIP.setToolTipText("This is the dotted form IP address of the node to configure.  DO NOT USE 'localhost'.");
    hostIP.setMinimumSize(new java.awt.Dimension(158, 28));
    hostIP.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        hostIPActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
    newInstall.add(hostIP, gridBagConstraints);

    labDB.setText("DB IP :");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 2;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_END;
    newInstall.add(labDB, gridBagConstraints);

    dbIP.setColumns(12);
    dbIP.setText("localhost");
    dbIP.setToolTipText("Enter the IP address of the MySQL database to use as a dotted numberic address (do not use DNS system names).");
    dbIP.setMinimumSize(new java.awt.Dimension(158, 28));
    dbIP.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        dbIPActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 2;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
    newInstall.add(dbIP, gridBagConstraints);

    labSMTP.setText("SMTP IP:");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 2;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_END;
    newInstall.add(labSMTP, gridBagConstraints);

    smtpIP.setColumns(12);
    smtpIP.setText("mailx");
    smtpIP.setMinimumSize(new java.awt.Dimension(158, 28));
    smtpIP.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        smtpIPActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 3;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
    newInstall.add(smtpIP, gridBagConstraints);

    labAlarm.setText("Alarm IP:");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 2;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_END;
    newInstall.add(labAlarm, gridBagConstraints);

    alarmIP.setColumns(12);
    alarmIP.setText("localhost");
    alarmIP.setMinimumSize(new java.awt.Dimension(158, 28));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 3;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
    newInstall.add(alarmIP, gridBagConstraints);

    labSNW.setText("SNW IP :");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 2;
    gridBagConstraints.gridy = 2;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_END;
    newInstall.add(labSNW, gridBagConstraints);

    snwIP.setColumns(12);
    snwIP.setText("None");
    snwIP.setMinimumSize(new java.awt.Dimension(158, 28));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 3;
    gridBagConstraints.gridy = 2;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
    newInstall.add(snwIP, gridBagConstraints);

    labSNWP.setText("SNW port :");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 2;
    gridBagConstraints.gridy = 3;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_END;
    newInstall.add(labSNWP, gridBagConstraints);

    snwPort.setColumns(4);
    snwPort.setText("0");
    snwPort.setMinimumSize(new java.awt.Dimension(85, 28));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 3;
    gridBagConstraints.gridy = 3;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
    newInstall.add(snwPort, gridBagConstraints);

    labStatus.setText("Status IP");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 2;
    gridBagConstraints.gridy = 4;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_END;
    newInstall.add(labStatus, gridBagConstraints);

    statusIP.setColumns(12);
    statusIP.setText("localhost");
    statusIP.setMinimumSize(new java.awt.Dimension(158, 28));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 3;
    gridBagConstraints.gridy = 4;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
    newInstall.add(statusIP, gridBagConstraints);

    labEmail.setText("EmailTo:");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 5;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_END;
    newInstall.add(labEmail, gridBagConstraints);

    emailTo.setColumns(20);
    emailTo.setText("ketchum@usgs.gov");
    emailTo.setMinimumSize(new java.awt.Dimension(254, 28));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 5;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
    newInstall.add(emailTo, gridBagConstraints);

    labPaths.setText("Paths :");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 10;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_END;
    newInstall.add(labPaths, gridBagConstraints);

    jScrollPane1.setMinimumSize(new java.awt.Dimension(244, 132));

    paths.setColumns(20);
    paths.setRows(8);
    paths.setText("nday=10\nndatapath=1\ndatapath=/data/\n#nday1=0\n#datapath1=/data3/\n#nday2=0\n#datapath2=/data4/");
    paths.setToolTipText("<html>\nThe ndatatpath variable must equal the number of data paths.  Each data path must have a nday? and datapath? defined.  The first one (the zeroth path) has no number following nday and  datapath.  The data paths must be valid existing directories owned by vdl and have group vdl and have group write permission.  Each data path must have a trailing \"/?\".  Example : datapath=/RAID/CWB/.\n<br>If this is a CWB node the nday should be 100000000 indicating a CWB with infinite storage and all of the other nday? should be zero.\n<br> For a Edge node the number of days of data is fixed and the number of days stored on each path is the value of nday.  So if you have 3 paths and you want to store 5 days on the first, two days on the 2nd and 3 days on the third the correct values are :\n<PRE>\nndatapath=3\nnday=5\nnday1=2\nnday2=3\ndatapath=/data/\ndatapath1=/data3/\ndatapath2=/data4/\n");
    jScrollPane1.setViewportView(paths);

    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 10;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
    newInstall.add(jScrollPane1, gridBagConstraints);

    doConfig.setText("Create single node");
    doConfig.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        doConfigActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 18;
    newInstall.add(doConfig, gridBagConstraints);

    sendToNEIC.setText("Send data to NEIC");
    sendToNEIC.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        sendToNEICActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 3;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
    newInstall.add(sendToNEIC, gridBagConstraints);

    labNodeNum.setText("Node Number (opt):");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 2;
    gridBagConstraints.gridy = 5;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_END;
    newInstall.add(labNodeNum, gridBagConstraints);

    nodeNumber.setColumns(2);
    nodeNumber.setToolTipText("Add a node number if your actual nodes do not have numbers at their end.  This number is used to allow files created on this node to have unique names using this number to prevent file clashes.   Example : if you node is names \"cwb\" you would put it in \"Node:\" above and perhaps put \"3\" in node number to give it a unique number.");
    nodeNumber.setMinimumSize(new java.awt.Dimension(38, 28));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 3;
    gridBagConstraints.gridy = 5;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
    newInstall.add(nodeNumber, gridBagConstraints);

    labImportIP.setText("ImportIP:");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 2;
    gridBagConstraints.gridy = 6;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_END;
    newInstall.add(labImportIP, gridBagConstraints);

    importIP.setColumns(12);
    importIP.setText("192.168.18.20");
    importIP.setToolTipText("Enter the IP address where an Earthworm Export is running with data for this Edge/CWB.");
    importIP.setMinimumSize(new java.awt.Dimension(158, 28));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 3;
    gridBagConstraints.gridy = 6;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
    newInstall.add(importIP, gridBagConstraints);

    labImportPort.setText("Import Port:");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 2;
    gridBagConstraints.gridy = 7;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_END;
    newInstall.add(labImportPort, gridBagConstraints);

    importPort.setColumns(4);
    importPort.setText("0");
    importPort.setToolTipText("Port where a Earthworm Export is running with data for this Edge/CWB.\n");
    importPort.setMinimumSize(new java.awt.Dimension(74, 28));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 3;
    gridBagConstraints.gridy = 7;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
    newInstall.add(importPort, gridBagConstraints);

    labTraceIP.setText("TraceIP:");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 2;
    gridBagConstraints.gridy = 8;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_END;
    newInstall.add(labTraceIP, gridBagConstraints);

    traceIP.setColumns(15);
    traceIP.setText("192.168.20.255");
    traceIP.setToolTipText("Set the trace UDP broadcast address for bringing in realtime data.");
    traceIP.setMinimumSize(new java.awt.Dimension(158, 28));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 3;
    gridBagConstraints.gridy = 8;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
    newInstall.add(traceIP, gridBagConstraints);

    labTracePort.setText("TracePort:");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 2;
    gridBagConstraints.gridy = 9;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_END;
    newInstall.add(labTracePort, gridBagConstraints);

    tracePort.setColumns(5);
    tracePort.setText("40010");
    tracePort.setMinimumSize(new java.awt.Dimension(74, 28));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 3;
    gridBagConstraints.gridy = 9;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
    newInstall.add(tracePort, gridBagConstraints);

    enableImport.setText("Setup Import with Data");
    enableImport.setToolTipText("Select this if you want to setup an EWImport from an Earthworm export to send realtime data to the Edge/CWB.\n");
    enableImport.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        enableImportActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 6;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
    newInstall.add(enableImport, gridBagConstraints);

    enableTrace.setText("Enable realtime UDP Trace Input");
    enableTrace.setToolTipText("Use this if you have a UDP trace wire you want to use to put realtime data into this Edge/CWB.");
    enableTrace.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        enableTraceActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 8;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
    newInstall.add(enableTrace, gridBagConstraints);

    labNEICSend.setText("NEIC Send RE:");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 4;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_END;
    newInstall.add(labNEICSend, gridBagConstraints);

    neicSendRE.setColumns(40);
    neicSendRE.setText(".*");
    neicSendRE.setToolTipText("<html>\nThis is a regular expression of the channels to send to the NEIC by default.  You can set the channels to not be sent after the fact on the EdgeConfig GUI>.  All channels are fixed 12 character like NNSSSSSCCCLL where NN is the 2 character network code, SSSSS is the 5 character station code, CCC is the 3 character channel code, and LL is the 2 character location code.  You regular expression must match 12 characters so have '.*' at the end is a good idea.  \".\" represents a single character wildcard, [ABC] means one character that is 'A' or 'B' or 'C'.  More complex settings can be made - see the Java documentation on regular expressions for more details.\n<br>\nUse '.*' to send all channels received by default.\n<br>\nFor something like all seismic channels use something like 'UU.....[BLHSE][HNL].*'\n<br>\n");
    neicSendRE.setMinimumSize(new java.awt.Dimension(254, 28));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 4;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
    newInstall.add(neicSendRE, gridBagConstraints);

    add(newInstall);

    keyPanel.setMaximumSize(new java.awt.Dimension(660, 200));
    keyPanel.setMinimumSize(new java.awt.Dimension(600, 120));
    keyPanel.setPreferredSize(new java.awt.Dimension(600, 120));

    labPath.setText("PathToAccount:");
    keyPanel.add(labPath);

    path.setColumns(20);
    path.setText("/home/vdl");
    path.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        pathActionPerformed(evt);
      }
    });
    path.addFocusListener(new java.awt.event.FocusAdapter() {
      public void focusLost(java.awt.event.FocusEvent evt) {
        pathFocusLost(evt);
      }
    });
    keyPanel.add(path);

    labKey.setText("Key:");
    keyPanel.add(labKey);

    key.setColumns(12);
    keyPanel.add(key);

    changeKey.setText("Change Key (rare option!)");
    changeKey.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        changeKeyActionPerformed(evt);
      }
    });
    keyPanel.add(changeKey);

    delete.setText("Delete Connection");
    delete.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        deleteActionPerformed(evt);
      }
    });
    keyPanel.add(delete);

    newButton.setText("Create New Connection ");
    newButton.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        newButtonActionPerformed(evt);
      }
    });
    keyPanel.add(newButton);

    labServers.setText("Edit Server:");
    keyPanel.add(labServers);

    servers.setMaximumRowCount(16);
    servers.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        serversActionPerformed(evt);
      }
    });
    keyPanel.add(servers);

    add(keyPanel);

    newPanel.setMaximumSize(new java.awt.Dimension(650, 100));
    newPanel.setMinimumSize(new java.awt.Dimension(650, 60));
    newPanel.setPreferredSize(new java.awt.Dimension(650, 100));

    labIP.setText("IPAdr (new):");
    newPanel.add(labIP);

    ipadr.setColumns(18);
    newPanel.add(ipadr);

    jLabel1.setText("DB Vendor:");
    newPanel.add(jLabel1);

    dbVendor.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "MySQL", "PostgreSQL", "Oracle" }));
    dbVendor.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        dbVendorActionPerformed(evt);
      }
    });
    newPanel.add(dbVendor);

    labDBPort.setText("DB Port:");
    newPanel.add(labDBPort);

    dbPort.setColumns(5);
    dbPort.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        dbPortActionPerformed(evt);
      }
    });
    newPanel.add(dbPort);

    add(newPanel);

    text.setColumns(40);
    text.setRows(10);
    text.setToolTipText("Edit the connections in this box of the form \"[update][readonly]:acct=acctPassword\".  This will set the password for these accounts when \"Add/Update\" is clicked.");
    textPanel.setViewportView(text);

    add(textPanel);

    newUserPanel.setMaximumSize(new java.awt.Dimension(650, 100));
    newUserPanel.setMinimumSize(new java.awt.Dimension(650, 100));
    newUserPanel.setPreferredSize(new java.awt.Dimension(650, 100));

    labUser.setText("UserName:");
    newUserPanel.add(labUser);

    username.setColumns(12);
    username.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        usernameActionPerformed(evt);
      }
    });
    newUserPanel.add(username);

    labUserPW.setText("Password:");
    newUserPanel.add(labUserPW);

    userPassword.setColumns(12);
    newUserPanel.add(userPassword);

    addUser.setText("Add/Update user");
    addUser.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        addUserActionPerformed(evt);
      }
    });
    newUserPanel.add(addUser);

    labDbs.setText("DBs:");
    newUserPanel.add(labDbs);

    dbs.setColumns(45);
    newUserPanel.add(dbs);

    add(newUserPanel);

    addUpdate.setText("Add/Update");
    addUpdate.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        addUpdateActionPerformed(evt);
      }
    });
    add(addUpdate);

    makeSymbolicLinks.setText("Make Links to Accounts");
    makeSymbolicLinks.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        makeSymbolicLinksActionPerformed(evt);
      }
    });
    add(makeSymbolicLinks);

    labAccounts.setText("Accounts:");
    add(labAccounts);

    accounts.setText("/home/vdl1 /home/vdl2 /home/vdl3 /home/vdl4 /home/reftek");
    add(accounts);
  }// </editor-fold>//GEN-END:initComponents

  private void pathActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_pathActionPerformed
    File dir = new File(path.getText()+System.getProperty("file.separator")+".dbconn");
    setup=true;
    servers.removeAllItems();
    servers.addItem("Select a Connection to Edit");
    if(!dir.exists()) {
      dir.mkdir();
      error.setText("No dbconn file found, create directory!");
      error.setBackground(Color.yellow);
      servers.setVisible(false);
      labServers.setVisible(false);
      delete.setVisible(false);
    }
    
    File dbconf = new File(path.getText()+System.getProperty("file.separator")+".dbconn"+
            System.getProperty("file.separator")+"dbconn.conf");
    if(!dbconf.exists()) {
      String ans = JOptionPane.showInputDialog(null, "Please enter a local encryption key.  You need not remember this so random is fine!");
      key.setText(ans);
      String saveEncrypt=ans;
      ans = DBContainer.encrypt(ans, DBContainer.getMask());
      File f = new File(path.getText()+System.getProperty("file.separator")+".dbconn");
      if(!f.exists()) f.mkdir();
      try {
        PrintStream keyout = new PrintStream(path.getText()+System.getProperty("file.separator")+".dbconn"+
                System.getProperty("file.separator")+"dbconn.conf");
        keyout.println(ans);
        keyout.close();  
      }
      catch(IOException e) {
        error.setText("Could not create .dbconn/dbconn.conf! e="+e);
        error.setBackground(Color.red);

      }
    }
    try {
      BufferedReader dbconn = new BufferedReader(new FileReader(path.getText()+System.getProperty("file.separator")+
              ".dbconn"+System.getProperty("file.separator")+"dbconn.conf"));
      String line = dbconn.readLine();
      key.setText(DBContainer.decrypt(line, randomSeed));
    }
    catch(IOException e) {
      Util.prt("Errror reading .dbconn/dbconn.conf e="+e);
    }
      File [] names = dir.listFiles();
      for(int i=0; i<names.length; i++) {
        if(names[i].getName().indexOf("dbconn") == 0 && names[i].getName().indexOf("dbconn.conf") < 0) 
          servers.addItem(names[i]);
      }
      if(servers.getItemCount() > 0) {
        servers.setVisible(true);
        labServers.setVisible(true);
        delete.setVisible(true);
      }
      dir.mkdir();

    setup=false;
  }//GEN-LAST:event_pathActionPerformed

  private void pathFocusLost(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_pathFocusLost
    pathActionPerformed(null);
  }//GEN-LAST:event_pathFocusLost
  String seed;
  String randomSeed=DBContainer.getMask();
  boolean setup;
  DBContainer container;
  private void serversActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_serversActionPerformed
    if(servers.getSelectedIndex() > 0 && !setup ) {
      DBConnectionThread d = db.get(((File) servers.getSelectedItem()).getName());
      if(d == null) {
        String [] parts = ((File) servers.getSelectedItem()).getName().replaceAll(".conf", "").split("_");
        String vendor = parts[1];
        String url=parts[2]+"/"+parts[3]+":mysql:"+vendor+":mysql";
        
        String rootpw=new String(password.getPassword());
        try {
          d = new DBConnectionThread(url, 
                  (vendor.toLowerCase().indexOf("postgr") >=0?"postgres":"edge"), 
                  privUser.getText(),
                  rootpw,  true, false, url, vendor, Util.getOutput());
          if(!d.waitForConnection())
                if(!d.waitForConnection()) {
                  error.setText("Did Not connect to privilege user.  Is is user or password wrong? "+((File) servers.getSelectedItem()).getName());
                  error.setBackground(Color.RED);
                  return;
                }
          error.setText("");
          error.setBackground(Color.white);
          db.put(((File) servers.getSelectedItem()).getName(), d);
        }
        catch(InstantiationException e) {
          Util.prt("Instantiation error e="+e);
          e.printStackTrace();
        }
      }
      textPanel.setVisible(true);
      addUpdate.setVisible(true);
      try {
        BufferedReader dbconn = new BufferedReader(new FileReader(path.getText()+
                System.getProperty("file.separator")+".dbconn"+System.getProperty("file.separator")+"dbconn.conf"));
        String line = dbconn.readLine();
        key.setText(DBContainer.decrypt(line, randomSeed));
      }
      catch(IOException e) {
        Util.prt("Errror reading .dbconn/dbconn.conf");
      }

      container = new DBContainer(path.getText()+System.getProperty("file.separator")+
              ".dbconn"+System.getProperty("file.separator")+((File) servers.getSelectedItem()).getName(), key.getText());
      try {
        text.setText(container.read());
      }
      catch(IOException e) {
        Util.prt("error reading "+path.getText()+"/.dbconn/"+((File) servers.getSelectedItem()).getName());
        e.printStackTrace();
      }
    }
  }//GEN-LAST:event_serversActionPerformed

  private void passwordActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_passwordActionPerformed
    String vendor = (rootVendor.getSelectedItem().toString().toLowerCase().indexOf("postgres")>=0?"postgres":"mysql");
    String ip = rootIP.getText().trim()+(rootIP.getText().indexOf("/") >0?"":(vendor.equals("postgres")?"/5432":"/3306"));
    String pw = new String(password.getPassword());
    if(pw.equals("jPasswordField1")) {
      pw="ketchum9";
      password.setText(pw);
    }
    try {
      Util.prt("serverSelect log into "+ip+" vend="+vendor+" url="+ip);
      root = new DBConnectionThread(ip, 
                  vendor, 
                  privUser.getText(),
              pw,  true, false, "ROOT", 
              vendor, Util.getOutput());
      pw="";
      if(!root.waitForConnection())
        if(!root.waitForConnection())
        if(!root.waitForConnection())
        if(!root.waitForConnection())
        if(!root.waitForConnection())
        if(!root.waitForConnection())
        if(!root.waitForConnection())
        if(!root.waitForConnection()) {
          error.setText("Did Not connect to root.  Is root password wrong? "+((File) servers.getSelectedItem()).getName());
          error.setBackground(Color.RED);
          return;
        }
      error.setText("Root login to "+ip+" as root is successful!");
      error.setBackground(Color.green);
      rootLoginPanel.setVisible(false);
      command.setVisible(true);      
    }
    catch(InstantiationException e) {
      error.setText("Instantiation exception trying to login as root on "+ip);
      error.setBackground(Color.red);
    }

  }//GEN-LAST:event_passwordActionPerformed

  private void dbPortActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_dbPortActionPerformed
    // TODO add your handling code here:
  }//GEN-LAST:event_dbPortActionPerformed

  private void newButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_newButtonActionPerformed
    newMode = !newMode;
    newPanel.setVisible(newMode);
    servers.setVisible(!newMode);
    labServers.setVisible(!newMode);
    delete.setVisible(!newMode);
    ipadr.setText("localhost");
    dbPort.setText("3306");
    if(newMode) text.setText("update:UPDATUSER=UPDATEPW\nreadonly:READONLYUSER=READONLYPASSWORD\n");
    textPanel.setVisible(true);
    addUpdate.setVisible(true);

  }//GEN-LAST:event_newButtonActionPerformed

  private void addUpdateActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_addUpdateActionPerformed
    try {
      if(newMode) {
        int port;
        if(dbPort.getText().equals("")) {
          if(dbVendor.getSelectedItem().toString().toLowerCase().equals("mysql")){dbPort.setText("3306"); port=3306;}
          else if(dbVendor.getSelectedItem().toString().toLowerCase().equals("mysql")) {dbPort.setText("5432");port=5432;}
        }
        else {
          try {
            port =  Integer.parseInt(dbPort.getText());
          }
          catch(NumberFormatException e) {
            error.setText("Port is illegal");
            error.setBackground(Color.red);
            return;
          }
        }
        File dbconndir = new File(path.getText()+System.getProperty("file.separator")+".dbconn");
        if(!dbconndir.exists()) {
          dbconndir.mkdir();
          Util.prt("Making directory ="+dbconndir);
        }

        container = new DBContainer(path.getText()+System.getProperty("file.separator")+".dbconn"+
                System.getProperty("file.separator")+"dbconn_"+dbVendor.getSelectedItem().toString().toLowerCase()+"_"+
                
                Util.cleanIP(ipadr.getText())+"_"+dbPort.getText()+".conf", key.getText());
      }
      else {
        String [] parts = servers.getSelectedItem().toString().split("_");
        dbVendor.setSelectedItem(parts[1]);
        dbPort.setText(parts[3].replaceAll(".conf", ""));
        ipadr.setText(parts[2]);

        
      }
      try {
        String rootpw = new String(password.getPassword());
        String url = Util.cleanIP(ipadr.getText())+"/"+dbPort.getText();
        DBConnectionThread d = DBConnectionThread.getThread(url);
        Util.prt("Add/Update log into "+url+" vend="+dbVendor.getSelectedItem()+" url="+url);
        if(d == null ) d =
                new DBConnectionThread(url, 
                (dbVendor.getSelectedItem().toString().toLowerCase().indexOf("postgr") >=0?"postgres":"mysql"), 
                privUser.getText(),
                rootpw,  true, false, url, 
                dbVendor.getSelectedItem().toString().toLowerCase().indexOf("postgres")>=0?"postgres":"mysql", 
                Util.getOutput());
        if(!d.waitForConnection())
              if(!d.waitForConnection()) {
                error.setText("Did Not connect to root.  Is root password wrong for this server? "+
                        (newMode?"":((File) servers.getSelectedItem()).getName()));
                error.setBackground(Color.RED);
                return;
              }
        error.setText("");
        error.setBackground(Color.white);
        db.put(path.getText()+System.getProperty("file.separator")+".dbconn"+System.getProperty("file.separator")+
                "dbconn_"+dbVendor.getSelectedItem().toString().toLowerCase()+"_"+
              Util.cleanIP(ipadr.getText())+"_"+dbPort.getText()+".conf", d);

      }
      catch(InstantiationException e) {
        Util.prt("Instantiation error e="+e);
        e.printStackTrace();
      }
      BufferedReader in = new BufferedReader(new StringReader(text.getText()));

      String line;
      DBConnectionThread d = db.get(path.getText()+System.getProperty("file.separator")+".dbconn"+
              System.getProperty("file.separator")+"dbconn_"+dbVendor.getSelectedItem().toString().toLowerCase()+"_"+
                Util.cleanIP(ipadr.getText())+"_"+dbPort.getText()+".conf");
      error.setText("");
      text.setText("");
      while ( (line = in.readLine()) != null) {
        if(line.trim().equals("")) continue;
        String [] parts = line.split(":");
        if(parts.length != 2) {
          error.setText("Bad line="+line);
          error.setBackground(Color.RED);
          return;
        }
        String [] userparts = parts[1].replace("=","= ").split("=");
        userparts[1]=userparts[1].trim();
        if(userparts[1].trim().equals("")) userparts[1]=randomPassword();
        boolean newUser=false;
        String s = userparts[0]+"="+userparts[1];
        text.setText(text.getText()+parts[0]+":"+s+"\n");
        container.add(parts[0],s);
        String privs=null;
        if(parts[0].equals("update")) privs="SELECT,DELETE,UPDATE,INSERT";
        if(parts[0].equals("readonly")) privs="SELECT";
        if(privs == null) {
          String ans = JOptionPane.showInputDialog(null, "Will user "+userparts[0]+" have only READONLY access?");
          privs= "DELETE,INSERT,SELECT,UPDATE";
          if((ans.toLowerCase()+" ").substring(0,1).equals("y")) privs="SELECT";
        }
        if(dbVendor.getSelectedItem().toString().toLowerCase().equals("mysql")) {
          try {
            if(select == null) select = d.prepareStatement("SELECT user FROM user WHERE user=?", true);
            select.setString(1, userparts[0]);
            ResultSet rs = select.executeQuery();
            //ResultSet rs = d.executeQuery("SELECT user FROM user WHERE user='"+userparts[0]+"'");
            if(!rs.next()) {
              String ans = JOptionPane.showInputDialog(null, "User "+userparts[0]+" not found.  Should I create it (Please Answer yes)?");
              rs.close();
              if((ans.toLowerCase()+" ").substring(0,1).equals("y")) {
                error.setText(error.getText()+" user "+userparts[0]+" created. ");
                newUser=true;
                //if(create == null) create = d.prepareStatement("CREATE USER ? IDENTIFIED BY ?", true);
                //create.setString(1, userparts[0]);
                //create.setString(2, userparts[1]);
                //create.executeUpdate();
                d.executeUpdate("CREATE USER "+userparts[0]+"@'%' IDENTIFIED BY '"+userparts[1]+"'");
                d.executeUpdate("CREATE USER "+userparts[0]+"@'localhost' IDENTIFIED BY '"+userparts[1]+"'");
                
                if(grant == null) {
                  grant = d.prepareStatement("GRANT ? on ?.* TO ?@'%'", true);
                  grantlocal = d.prepareStatement("GRANT ? on ?.* TO ?@'localhost'", true);
                }
                for(int i=0; i<databases.length; i++) {
                  /*grant.setString(1, privs);
                  grant.setString(2, databases[i]);
                  grant.setString(3, userparts[0]);
                  grant.executeUpdate();
                  grantlocal.setString(1, privs);
                  grantlocal.setString(2, databases[i]);
                  grantlocal.setString(3, userparts[0]);
                  grantlocal.executeUpdate();*/
                  d.executeUpdate("GRANT "+privs+" on "+databases[i]+".* TO "+userparts[0]+"@'%'");
                  d.executeUpdate("GRANT "+privs+" on "+databases[i]+".* TO "+userparts[0]+"@'localhost'");
                }
                if(setpassword == null) setpassword = d.prepareStatement("SET PASSWORD FOR ?@'%' = PASSWORD(?)", true);
                if(setpasswordlocal == null) setpasswordlocal = d.prepareStatement("SET PASSWORD FOR ?@'localhost' = PASSWORD(?)", true);
                setpassword.setString(1,userparts[0]);
                setpassword.setString(2, userparts[1]);
                setpassword.executeUpdate();
                setpasswordlocal.setString(1,userparts[0]);
                setpasswordlocal.setString(2, userparts[1]);
                setpasswordlocal.executeUpdate();
                //d.executeUpdate("SET PASSWORD FOR "+userparts[0]+"@'localhost' = PASSWORD('"+userparts[1]+"'");
                d.executeUpdate("FLUSH PRIVILEGES");
              }
            }
            else {
              error.setText(error.getText()+" User "+userparts[0]+" password updated. ");
              if(setpassword == null) setpassword = d.prepareStatement("SET PASSWORD FOR ?@'%' = PASSWORD(?)", true);
              if(setpasswordlocal == null) setpasswordlocal = d.prepareStatement("SET PASSWORD FOR ?@'localhost' = PASSWORD(?)", true);
              setpassword.setString(1,userparts[0]);
              setpassword.setString(2, userparts[1]);
              setpassword.executeUpdate();
              setpasswordlocal.setString(1,userparts[0]);
              setpasswordlocal.setString(2, userparts[1]);
              setpasswordlocal.executeUpdate();

              //d.executeUpdate("SET PASSWORD FOR "+userparts[0]+"@'%' = PASSWORD('"+userparts[1]+"')");
              //d.executeUpdate("SET PASSWORD FOR "+userparts[0]+"@'localhost' = PASSWORD('"+userparts[1]+"')");
              d.executeUpdate("FLUSH PRIVILEGES");
              rs.close();
            }
          }
          catch(SQLException e) {
            Util.prt("SQLException updating MySQL user and privileges="+e);
            e.printStackTrace();
            error.setText("SQL error adding or updating user="+e);
            error.setBackground(Color.red);
          }
        }
        else if(dbVendor.getSelectedItem().toString().toLowerCase().indexOf("postgres") >=0) {
          try {
            // TODO : this is untested code
            d.executeUpdate("CREATE ROLE "+userparts[0]+" PASSWORD '"+userparts[1]+"'");
            for(int i=0; i<databases.length; i++) {
              d.executeUpdate("GRANT "+privs+" ON SCHEMA "+databases[i]);
              d.executeUpdate("GRANT USAGE ON SCHEMA "+databases[i]+" TO "+userparts[0]);
              d.executeUpdate("GRANT "+privs+" ON ALL TABLES IN SCHEMA "+databases[i]+" TO "+userparts[0]);
              d.executeUpdate("GRANT USAGE ON ALL SEQUENCES IN SCHEMA "+databases[i]+" TO "+userparts[0]);
              d.executeUpdate("GRANT USAGE ON ALL FUNCTIONS IN SCHEMA "+databases[i]+" TO "+userparts[0]);
              d.executeUpdate("GRANT CONNECT ON SCHEMA "+databases[i]+" TO "+userparts[0]);
            }
          }
          catch(SQLException e) {
            Util.prt("SQLException updating POSTGRES user and privileges="+e);
            e.printStackTrace();
            error.setText("SQL error adding or updating user="+e);
            error.setBackground(Color.red);            
          }
          
        }
      }
      container.close();
      text.setText("");
      key.setText("");
      error.setBackground(Color.white);
    }
    catch(IOException e) {
      error.setText("Could not update file e="+e);
      error.setBackground(Color.RED);
      e.printStackTrace();
    }
    servers.setSelectedIndex(0);
    textPanel.setVisible(false);
    addUpdate.setVisible(false);
    newPanel.setVisible(false);
    servers.setVisible(true);
  }//GEN-LAST:event_addUpdateActionPerformed

  private void dbVendorActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_dbVendorActionPerformed
    if(dbVendor.getSelectedItem().toString().toLowerCase().equals("mysql")) dbPort.setText("3306");
    if(dbVendor.getSelectedItem().toString().toLowerCase().indexOf("postgres")>=0) dbPort.setText("5432");
    if(dbVendor.getSelectedItem().toString().toLowerCase().equals("oracle")) dbPort.setText("1111");
    
  }//GEN-LAST:event_dbVendorActionPerformed

  private void deleteActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_deleteActionPerformed
    ((File) servers.getSelectedItem()).delete();
    error.setText("Item deleted="+servers.getSelectedItem());
    error.setBackground(Color.white);
    servers.removeItemAt(servers.getSelectedIndex());
    servers.setSelectedIndex(0);
    text.setText("");
    
  }//GEN-LAST:event_deleteActionPerformed

  private void makeSymbolicLinksActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_makeSymbolicLinksActionPerformed
    //For each account try to make a symbolic link to .dbconndir in path given for vdl
    String [] accts = accounts.getText().split("\\s");
    for(int i=0; i<accts.length; i++) {
      File f = new File(accts[i]+System.getProperty("file.separator")+".dbconn");
      if(f.exists()) {
        error.setText(error.getText()+" "+accts[i]+".dbconn exists");
      }
      else {
        
      }
    }
  }//GEN-LAST:event_makeSymbolicLinksActionPerformed

  private void commandActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_commandActionPerformed
    if(command.getSelectedIndex() <0) return;
    if(command.getSelectedItem().toString().equals("Create/Modify User")) {
      setAllInvisible();
      newUserPanel.setVisible(true);
    }
    else if(command.getSelectedItem().toString().equalsIgnoreCase("Setup Update and Readonly Accts")) {
      setAllInvisible();
      keyPanel.setVisible(true);
      path.requestFocus();
      
    }
    else if(command.getSelectedItem().toString().equalsIgnoreCase("Create NEW Install Configuration")) {
      setAllInvisible();
      newInstall.setVisible(true);
      installation.requestFocus();
    }
    else {
      Util.prt("Unknown command="+command.getSelectedItem());
    }
  }//GEN-LAST:event_commandActionPerformed

  private void addUserActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_addUserActionPerformed
    try {
      if(username.getText().trim().equals("") || userPassword.getText().trim().equals("")) {
        error.setText("you must set a username and password!");
        error.setBackground(Color.yellow);
      }
      ResultSet rs = root.executeQuery("SELECT user FROM user WHERE user='"+username.getText()+"'");
      if(!rs.next()) {
        String ans = JOptionPane.showInputDialog(null, "User "+username.getText()+" not found.  Should I create it?");
        rs.close();
        if((ans.toLowerCase()+" ").substring(0,1).equals("y")) {
          root.executeUpdate("CREATE USER "+username.getText()+" IDENTIFIED BY '"+userPassword.getText()+"'");
        }
        else {
          error.setText("Create new user aborted!");
          error.setBackground(Color.yellow);
          return;
        }
      }
      String [] db = dbs.getText().trim().split("\\s");
              
      for(int i=0; i<db.length; i++) {
        if(db[i].trim().equals("")) continue;
        root.executeUpdate("GRANT DELETE,INSERT,SELECT,UPDATE on "+db[i]+".* TO "+username.getText()+"@'%'");
        root.executeUpdate("GRANT DELETE,INSERT,SELECT,UPDATE on "+db[i]+".* TO "+username.getText()+"@'localhost'");
      }
      root.executeUpdate("SET PASSWORD FOR "+username.getText()+"@'%' = PASSWORD('"+userPassword.getText()+"')");
      root.executeUpdate("SET PASSWORD FOR "+username.getText()+"@'localhost' = PASSWORD('"+userPassword.getText()+"')");
      root.executeUpdate("FLUSH PRIVILEGES");
      rs.close();
    }
    catch(SQLException e) {
      error.setText("Error updating user e="+e);
      error.setBackground(Color.red);
      Util.prt("SQLException updating user and privileges="+e);
      e.printStackTrace();
      return;
    }
    error.setText(username.getText()+" was added with permissions to "+dbs.getText());
    error.setBackground(Color.green);
    username.setText("");
    userPassword.setText("");
    
  }//GEN-LAST:event_addUserActionPerformed

  private void usernameActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_usernameActionPerformed

  }//GEN-LAST:event_usernameActionPerformed

  private void changeKeyActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_changeKeyActionPerformed
    // CHange key needs to loop through the paths servers and change the key and change it in .dbconn/dbconn.conf
    
  }//GEN-LAST:event_changeKeyActionPerformed

  private void dbIPActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_dbIPActionPerformed
    // TODO add your handling code here:
  }//GEN-LAST:event_dbIPActionPerformed

  private void smtpIPActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_smtpIPActionPerformed
    // TODO add your handling code here:
  }//GEN-LAST:event_smtpIPActionPerformed

  private void installationActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_installationActionPerformed
    // TODO add your handling code here:
  }//GEN-LAST:event_installationActionPerformed
  String dbip, alarmip,snwip,statusip,smtpip,importip,traceip, hostip;
  int importport, traceport, snwport;
  private void doConfigActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_doConfigActionPerformed
    err.reset();
    error.setBackground(Color.white);
    error.setText("");
    dbip = dbIP.getText().toLowerCase();
    if(!dbip.equals("localhost")) dbip = Util.cleanIP(FUtil.chkIP(dbIP, err));
    alarmip = alarmIP.getText().toLowerCase();
    if(!alarmip.equals("localhost")) alarmip = Util.cleanIP(FUtil.chkIP(alarmIP, err));
    snwip = snwIP.getText().toLowerCase();
    if(!snwip.equals("localhost") && !snwip.equals("none")) snwip = Util.cleanIP(FUtil.chkIP(snwIP, err));
    if(snwip.equals("none")) snwip="";
    statusip = statusIP.getText().toLowerCase();
    if(!statusip.equals("localhost")) statusip = Util.cleanIP(FUtil.chkIP(statusIP, err));
    smtpip = smtpIP.getText().toLowerCase();
    if(!smtpip.equals("localhost")) smtpip = Util.cleanIP(FUtil.chkIP(smtpIP, err));
    hostip = hostIP.getText().toLowerCase();
    if(!hostip.equals("localhost")) hostip = Util.cleanIP(FUtil.chkIP(hostIP, err));
    importip="";
    importport=0;
    if(enableImport.isSelected()) {
      importip = FUtil.chkIP(importIP,err);
      importip = Util.cleanIP(importip);
      importport=FUtil.chkInt(importPort, err, 0, 65533);
    }
    traceip="";
    traceport=0;
    if(enableTrace.isSelected()) {
      traceip = FUtil.chkIP(traceIP,err);
      traceip = Util.cleanIP(traceip);
      traceport=FUtil.chkInt(tracePort, err, 0, 65533);
    }
    
    if(emailTo.getText().indexOf("@") < 0) err.appendText("no @ in email address");
    
    snwport = FUtil.chkInt(snwPort, err, 0, 65533);
    
    
    try {
       BufferedReader in = new BufferedReader(new StringReader(paths.getText()));
      int npaths=0;
      ArrayList<String> datapaths = new ArrayList<String>(1);
      ArrayList<Integer> ndays = new ArrayList<Integer>(1);
      String line;
      int npathsfile=0;
      while ( (line = in.readLine()) != null) {
        try {
          if(line.length() == 0) continue;
          if(line.charAt(0) == '#') continue;
          String [] parts = line.split("=");
          if(parts[0].equals("ndatapath")) {
            npathsfile=Integer.parseInt(parts[1]);
          }
          else if(parts[0].indexOf("nday") == 0) {
            int n = Integer.parseInt(parts[1]);
            int i=0;
            if(parts[0].length() > 4) i = Integer.parseInt(parts[0].substring(4));
            ndays.add(i, new Integer(n));
          }
          else if(parts[0].indexOf("datapath") == 0) {
            String pth  = parts[1];
            int i=0;
            if(parts[0].length() > 8) i = Integer.parseInt(parts[0].substring(8));
            if(!pth.endsWith(System.getProperty("file.separator"))) {
              err.set();
              err.appendText(parts[0]+" does not end with "+System.getProperty("file.separator"));}
            datapaths.add(i, pth);
          }
        }
        catch(RuntimeException e) {
          err.appendText("Syntax error in line="+line);
        }
      }
      if(npathsfile != ndays.size() || datapaths.size() != npathsfile) {
        err.set(); 
        err.appendText("#paths="+npathsfile+" #ndays="+ndays.size()+" #datapaths="+datapaths.size()+" do no match");
        return;
      }
      else {
        for(int i=0; i<ndays.size();i++) {
          if(datapaths.get(i) == null || ndays.get(i) == null) {err.set(); err.appendText("path"+i+" is missing!");}
        }
      }
      error.setText(err.getText());
      if(err.isSet()) error.setBackground(Color.red);
      else {
        // Do the anss.prop and edgecon.prop, groups_$HOST, roles_$HOST
        try {
   
          doProp("anss.prop.example");
          doProp("edgecon.prop.example");
          doProp("chandisp.prop.example");
          doProp("query.prop.example");
          doProp("queryserver.prop.example");
          doProp("metagui.prop.example");
          doProp("msread.prop");
          RandomAccessFile rw = new RandomAccessFile("groups_"+installation.getText(), "rw");
          String groups="#!/bin/bash -f\n# for roles "+installation.getText()+"\nVDL_GROUPS=\"q330\"\nexport VDL_GROUPS\n";
          rw.write(groups.getBytes(),0, groups.length());
          rw.setLength(groups.length());
          rw.close();
          rw = new RandomAccessFile("roles_"+installation.getText(), "rw");
          String roles="#!/bash -f\n# for node "+installation.getText()+"\nVDL_ROLES=\""+installation.getText()+"\"\nexport VDL_ROLES\n";
          rw.write(roles.getBytes(),0, roles.length());
          rw.setLength(roles.length());
          rw.close();
          
        }
        catch(IOException e) {
          error.setText("Could not write file="+ e);
          error.setBackground(Color.red);
          return;
        }
        
        // Get the edge.prop file from edgefile table for local host and create one for the new host
        ResultSet rs = DBConnectionThread.getThread("ROOT").executeQuery("SELECT * FROM edge.edgefile WHERE edgefile='_edge.prop.localhost.vdl'");
        if(rs.next()) {
          String edgeProp = rs.getString("content");
          rs.close();
          edgeProp = doSubstitutions(edgeProp);

          if(nodeNumber.getText().trim().length() > 0) {
            if(!edgeProp.endsWith("\n")) edgeProp += "\n";
            edgeProp += "Node="+installation.getText().trim()+nodeNumber.getText().trim()+"\n";
          }
          rs = DBConnectionThread.getThread("ROOT").executeQuery(
                  "SELECT * FROM edge.edgefile WHERE edgefile='edge.prop."+installation.getText()+".vdl'");
          if(rs.next()) {
            DBConnectionThread.getThread("ROOT").executeUpdate("DELETE FROM edge.edgefile WHERE id="+rs.getInt("id"));
          }
          rs.close();
          DBConnectionThread.getThread("ROOT").executeUpdate("INSERT INTO edge.edgefile (edgefile,content,updated,created_by,created) VALUES ("+
                  "'edge.prop."+installation.getText()+".vdl','"+edgeProp+"',now(), 0, now())");
          RandomAccessFile rw = new RandomAccessFile("edge.prop", "rw");
          rw.write(edgeProp.getBytes(), 0, (int) edgeProp.length());
          rw.setLength(edgeProp.length());
          rw.close();
        }
        // Get the edge.prop file from edgefile table for local host and create one for the new host
        rs = DBConnectionThread.getThread("ROOT").executeQuery("SELECT * FROM edge.edgefile WHERE edgefile='_msread.prop.localhost.vdl'");
        if(rs.next()) {
          String msreadProp = rs.getString("content");
          rs.close();
          msreadProp = doSubstitutions(msreadProp);

          if(nodeNumber.getText().trim().length() > 0) {
            if(!msreadProp.endsWith("\n")) msreadProp += "\n";
            msreadProp += "Node="+installation.getText().trim()+nodeNumber.getText().trim()+"\n";
          }
          rs = DBConnectionThread.getThread("ROOT").executeQuery(
                  "SELECT * FROM edge.edgefile WHERE edgefile='msread.prop."+installation.getText()+".vdl'");
          if(rs.next()) {
            DBConnectionThread.getThread("ROOT").executeUpdate("DELETE FROM edge.edgefile WHERE id="+rs.getInt("id"));
          }
          rs.close();
          DBConnectionThread.getThread("ROOT").executeUpdate("INSERT INTO edge.edgefile (edgefile,content,updated,created_by,created) VALUES ("+
                  "'msread.prop."+installation.getText()+".vdl','"+msreadProp+"',now(), 0, now())");
          RandomAccessFile rw = new RandomAccessFile("msread.prop", "rw");
          rw.write(msreadProp.getBytes(), 0, (int) msreadProp.length());
          rw.setLength(msreadProp.length());
          rw.close();
        }       
        // if send all to NEIC is set, fix the sendto
        if(sendToNEIC.isSelected()) {
          DBConnectionThread.getThread("ROOT").executeUpdate("UPDATE edge.sendto SET autoset='"+neicSendRE.getText()+"' WHERE sendto='TO_NEIC'");
        }
        else {
          DBConnectionThread.getThread("ROOT").executeUpdate("UPDATE edge.sendto SET autoset='' WHERE sendto='TO_NEIC'");
        }

        // Do the crontab 
        rs = DBConnectionThread.getThread("ROOT").executeQuery("SELECT * FROM edge.edgefile where edgefile='_crontab.localhost.vdl'");
        if(rs.next()) {
          String crontab = rs.getString("content");
          rs = DBConnectionThread.getThread("ROOT").executeQuery(
                  "SELECT * FROM edge.edgefile WHERE edgefile='crontab."+installation.getText()+".vdl'");
          if(rs.next()) {
            DBConnectionThread.getThread("ROOT").executeUpdate("DELETE FROM edge.edgefile WHERE id="+rs.getInt("id"));
          }
          rs.close();
          crontab = crontab.replaceAll("\\'","\\\\'");
          DBConnectionThread.getThread("ROOT").executeUpdate(
                  "INSERT INTO edge.edgefile (edgefile,content,updated,created_by,created) VALUES ("+
                  "'crontab."+installation.getText()+".vdl','"+crontab+"',now(), 0, now())");
        }

        // setup the CPU and role
        DBConnectionThread.getThread("ROOT").executeUpdate("DELETE FROM edge.cpu WHERE cpu='"+installation.getText()+"'");
        DBConnectionThread.getThread("ROOT").executeUpdate(
                  "INSERT INTO edge.cpu (cpu,ipadr,os,hasdata,updated,created_by,created) VALUES ("+
                "'"+installation.getText()+"','"+hostip+"','Linux',0,now(), 0, now())");
        int cpuID=DBConnectionThread.getThread("ROOT").getLastInsertID("ROOT");

        DBConnectionThread.getThread("ROOT").executeUpdate("DELETE FROM edge.role WHERE role='"+installation.getText()+"'");
        DBConnectionThread.getThread("ROOT").executeUpdate(
                  "INSERT INTO edge.role (role,description,ipadr,cpuid,accounts,hasdata,updated,created_by,created) VALUES ("+
                "'"+installation.getText()+"','','"+hostip+"',"+cpuID+",'vdl',0,now(), 0, now())");
        int roleID = DBConnectionThread.getThread("ROOT").getLastInsertID("ROOT");
        Statement stmt = DBConnectionThread.getThread("ROOT").getNewStatement(false);
        rs =stmt.executeQuery("SELECT * FROM edge.edgemomsetup WHERE roleid=1");
        while(rs.next()) {
          int disabled=rs.getInt("disabled");
          int edgefileID=rs.getInt("edgefileid");
          String args = rs.getString("args");
          // If this is a import port
          if(enableImport.isSelected() && rs.getString("tag").equalsIgnoreCase("IMPORT")) {
            disabled=0;
            ResultSet rs2 = DBConnectionThread.getThread("ROOT").executeQuery("SELECT * FROM edge.edgefile WHERE edgefile='_EW/import_localhost.config'");
            String config=null;
            if(rs2.next()) {
             config = rs2.getString("content");
            }
            rs2.close();
            config = doSubstitutions(config);
            config = config.replaceAll("\\$IMPORTIP", importip);
            config = config.replaceAll("\\$IMPORTPORT", ""+importport);
            Util.prt("import config after subs="+config);
            rs2 = DBConnectionThread.getThread("ROOT").executeQuery(
                    "SELECT * FROM edge.edgefile WHERE edgefile='_EW/import_"+installation.getText().trim()+".config'");
            if(rs2.next()) {
              int ID = rs2.getInt("id");
              rs2.close();
              DBConnectionThread.getThread("ROOT").executeUpdate("DELETE FROM edge.edgefile WHERE id="+ID);
            }
            else rs2.close();
            DBConnectionThread.getThread("ROOT").executeUpdate(
                "INSERT INTO edge.edgefile (edgefile,content,updated,created_by,created) VALUES ("+
                "'EW/import_"+installation.getText()+".config','"+config+"',now(), 0, now())");
            edgefileID = DBConnectionThread.getThread("ROOT").getLastInsertID("edgefile");

          }
          // if this is an traceport
          if(enableTrace.isSelected() && rs.getString("tag").equalsIgnoreCase("trace1")) {
            args = args.replaceAll("\\$TRACEPORT", ""+traceport);
            args = args.replaceAll("\\$TRACEBROADCAST", traceip);
            disabled=0;
          }
          args = doSubstitutions(args);
          DBConnectionThread.getThread("ROOT").executeUpdate(
            "INSERT INTO edge.edgemomsetup (roleid,account,tag,priority,edgethreadid,args,logfile,comment,filename,edgefileID,disabled,"+
                  "updated,created_by,created) VALUES ("+roleID+",'"+rs.getString("account")+"','"+rs.getString("tag")+"',"+
                  rs.getInt("priority")+","+rs.getInt("edgethreadid")+",'"+args+"','"+
                  rs.getString("logfile")+"','"+rs.getString("comment")+"','"+rs.getString("filename")+"',"+
                  edgefileID+","+
                  disabled+",now(), 0, now())");
        }
      }
    }
    catch(SQLException e) {
      Util.prt("SQLError="+e);
      e.printStackTrace();
    }
    catch(IOException e) {
      Util.prt("IOError="+e);
      e.printStackTrace();
    }
    error.setText("Configuration written!");
  }//GEN-LAST:event_doConfigActionPerformed
  private void doConfigActionPerformedOldEdgeConfig(java.awt.event.ActionEvent evt) {                                         
    err.reset();
    error.setBackground(Color.white);
    error.setText("");
    dbip = dbIP.getText().toLowerCase();
    if(!dbip.equals("localhost")) dbip = Util.cleanIP(FUtil.chkIP(dbIP, err));
    alarmip = alarmIP.getText().toLowerCase();
    if(!alarmip.equals("localhost")) alarmip = Util.cleanIP(FUtil.chkIP(alarmIP, err));
    snwip = snwIP.getText().toLowerCase();
    if(!snwip.equals("localhost") && !snwip.equals("none")) snwip = Util.cleanIP(FUtil.chkIP(snwIP, err));
    if(snwip.equals("none")) snwip="";
    statusip = statusIP.getText().toLowerCase();
    if(!statusip.equals("localhost")) statusip = Util.cleanIP(FUtil.chkIP(statusIP, err));
    smtpip = smtpIP.getText().toLowerCase();
    if(!smtpip.equals("localhost")) smtpip = Util.cleanIP(FUtil.chkIP(smtpIP, err));
    hostip = hostIP.getText().toLowerCase();
    if(!hostip.equals("localhost")) hostip = Util.cleanIP(FUtil.chkIP(hostIP, err));
    importip="";
    importport=0;
    if(enableImport.isSelected()) {
      importip = FUtil.chkIP(importIP,err);
      importip = Util.cleanIP(importip);
      importport=FUtil.chkInt(importPort, err, 0, 65533);
    }
    traceip="";
    traceport=0;
    if(enableTrace.isSelected()) {
      traceip = FUtil.chkIP(traceIP,err);
      traceip = Util.cleanIP(traceip);
      traceport=FUtil.chkInt(tracePort, err, 0, 65533);
    }
    
    if(emailTo.getText().indexOf("@") < 0) err.appendText("no @ in email address");
    
    snwport = FUtil.chkInt(snwPort, err, 0, 65533);
    
    
    try {
       BufferedReader in = new BufferedReader(new StringReader(paths.getText()));
      int npaths=0;
      ArrayList<String> datapaths = new ArrayList<String>(1);
      ArrayList<Integer> ndays = new ArrayList<Integer>(1);
      String line;
      int npathsfile=0;
      while ( (line = in.readLine()) != null) {
        try {
          if(line.length() == 0) continue;
          if(line.charAt(0) == '#') continue;
          String [] parts = line.split("=");
          if(parts[0].equals("ndatapath")) {
            npathsfile=Integer.parseInt(parts[1]);
          }
          else if(parts[0].indexOf("nday") == 0) {
            int n = Integer.parseInt(parts[1]);
            int i=0;
            if(parts[0].length() > 4) i = Integer.parseInt(parts[0].substring(4));
            ndays.add(i, new Integer(n));
          }
          else if(parts[0].indexOf("datapath") == 0) {
            String pth  = parts[1];
            int i=0;
            if(parts[0].length() > 8) i = Integer.parseInt(parts[0].substring(8));
            if(!pth.endsWith(System.getProperty("file.separator"))) {
              err.set();
              err.appendText(parts[0]+" does not end with "+System.getProperty("file.separator"));}
            datapaths.add(i, pth);
          }
        }
        catch(RuntimeException e) {
          err.appendText("Syntax error in line="+line);
        }
      }
      if(npathsfile != ndays.size() || datapaths.size() != npathsfile) {
        err.set(); 
        err.appendText("#paths="+npathsfile+" #ndays="+ndays.size()+" #datapaths="+datapaths.size()+" do no match");
        return;
      }
      else {
        for(int i=0; i<ndays.size();i++) {
          if(datapaths.get(i) == null || ndays.get(i) == null) {err.set(); err.appendText("path"+i+" is missing!");}
        }
      }
      error.setText(err.getText());
      if(err.isSet()) error.setBackground(Color.red);
      else {
        // Do the anss.prop and edgecon.prop, groups_$HOST, roles_$HOST
        try {
   
          doProp("anss.prop.example");
          doProp("edgecon.prop.example");
          doProp("chandisp.prop.example");
          doProp("query.prop.example");
          doProp("queryserver.prop.example");
          doProp("metagui.prop.example");
          doProp("msread.prop");
          RandomAccessFile rw = new RandomAccessFile("groups_"+installation.getText(), "rw");
          String groups="#!/bin/bash -f\n# for roles "+installation.getText()+"\nVDL_GROUPS=\"q330\"\nexport VDL_GROUPS\n";
          rw.write(groups.getBytes(),0, groups.length());
          rw.setLength(groups.length());
          rw.close();
          rw = new RandomAccessFile("roles_"+installation.getText(), "rw");
          String roles="#!/bash -f\n# for node "+installation.getText()+"\nVDL_ROLES=\""+installation.getText()+"\"\nexport VDL_ROLES\n";
          rw.write(roles.getBytes(),0, roles.length());
          rw.setLength(roles.length());
          rw.close();
          
        }
        catch(IOException e) {
          error.setText("Could not write file="+ e);
          error.setBackground(Color.red);
          return;
        }
        
        // Get the edge.prop file from edgefile table for local host and create one for the new host
        ResultSet rs = DBConnectionThread.getThread("ROOT").executeQuery("SELECT * FROM edge.edgefile WHERE edgefile='_edge.prop.localhost.vdl'");
        if(rs.next()) {
          String edgeProp = rs.getString("content");
          rs.close();
          edgeProp = doSubstitutions(edgeProp);

          if(nodeNumber.getText().trim().length() > 0) {
            if(!edgeProp.endsWith("\n")) edgeProp += "\n";
            edgeProp += "Node="+installation.getText().trim()+nodeNumber.getText().trim()+"\n";
          }
          rs = DBConnectionThread.getThread("ROOT").executeQuery(
                  "SELECT * FROM edge.edgefile WHERE edgefile='edge.prop."+installation.getText()+".vdl'");
          if(rs.next()) {
            DBConnectionThread.getThread("ROOT").executeUpdate("DELETE FROM edge.edgefile WHERE id="+rs.getInt("id"));
          }
          rs.close();
          DBConnectionThread.getThread("ROOT").executeUpdate("INSERT INTO edge.edgefile (edgefile,content,updated,created_by,created) VALUES ("+
                  "'edge.prop."+installation.getText()+".vdl','"+edgeProp+"',now(), 0, now())");
          RandomAccessFile rw = new RandomAccessFile("edge.prop", "rw");
          rw.write(edgeProp.getBytes(), 0, (int) edgeProp.length());
          rw.setLength(edgeProp.length());
          rw.close();
        }
        // Get the edge.prop file from edgefile table for local host and create one for the new host
        rs = DBConnectionThread.getThread("ROOT").executeQuery("SELECT * FROM edge.edgefile WHERE edgefile='_msread.prop.localhost.vdl'");
        if(rs.next()) {
          String msreadProp = rs.getString("content");
          rs.close();
          msreadProp = doSubstitutions(msreadProp);

          if(nodeNumber.getText().trim().length() > 0) {
            if(!msreadProp.endsWith("\n")) msreadProp += "\n";
            msreadProp += "Node="+installation.getText().trim()+nodeNumber.getText().trim()+"\n";
          }
          rs = DBConnectionThread.getThread("ROOT").executeQuery(
                  "SELECT * FROM edge.edgefile WHERE edgefile='msread.prop."+installation.getText()+".vdl'");
          if(rs.next()) {
            DBConnectionThread.getThread("ROOT").executeUpdate("DELETE FROM edge.edgefile WHERE id="+rs.getInt("id"));
          }
          rs.close();
          DBConnectionThread.getThread("ROOT").executeUpdate("INSERT INTO edge.edgefile (edgefile,content,updated,created_by,created) VALUES ("+
                  "'msread.prop."+installation.getText()+".vdl','"+msreadProp+"',now(), 0, now())");
          RandomAccessFile rw = new RandomAccessFile("msread.prop", "rw");
          rw.write(msreadProp.getBytes(), 0, (int) msreadProp.length());
          rw.setLength(msreadProp.length());
          rw.close();
        }       
        // if send all to NEIC is set, fix the sendto
        if(sendToNEIC.isSelected()) {
          DBConnectionThread.getThread("ROOT").executeUpdate("UPDATE edge.sendto SET autoset='"+neicSendRE.getText()+"' WHERE sendto='TO_NEIC'");
        }
        else {
          DBConnectionThread.getThread("ROOT").executeUpdate("UPDATE edge.sendto SET autoset='' WHERE sendto='TO_NEIC'");
        }

        // Do the crontab 
        rs = DBConnectionThread.getThread("ROOT").executeQuery("SELECT * FROM edge.edgefile where edgefile='_crontab.localhost.vdl'");
        if(rs.next()) {
          String crontab = rs.getString("content");
          rs = DBConnectionThread.getThread("ROOT").executeQuery(
                  "SELECT * FROM edge.edgefile WHERE edgefile='crontab."+installation.getText()+".vdl'");
          if(rs.next()) {
            DBConnectionThread.getThread("ROOT").executeUpdate("DELETE FROM edge.edgefile WHERE id="+rs.getInt("id"));
          }
          rs.close();
          crontab = crontab.replaceAll("\\'","\\\\'");
          DBConnectionThread.getThread("ROOT").executeUpdate(
                  "INSERT INTO edge.edgefile (edgefile,content,updated,created_by,created) VALUES ("+
                  "'crontab."+installation.getText()+".vdl','"+crontab+"',now(), 0, now())");
        }

        // setup the CPU and role
        DBConnectionThread.getThread("ROOT").executeUpdate("DELETE FROM edge.cpu WHERE cpu='"+installation.getText()+"'");
        DBConnectionThread.getThread("ROOT").executeUpdate(
                  "INSERT INTO edge.cpu (cpu,ipadr,os,hasdata,updated,created_by,created) VALUES ("+
                "'"+installation.getText()+"','"+hostip+"','Linux',0,now(), 0, now())");
        int cpuID=DBConnectionThread.getThread("ROOT").getLastInsertID("ROOT");

        DBConnectionThread.getThread("ROOT").executeUpdate("DELETE FROM edge.role WHERE role='"+installation.getText()+"'");
        DBConnectionThread.getThread("ROOT").executeUpdate(
                  "INSERT INTO edge.role (role,description,ipadr,cpuid,accounts,hasdata,updated,created_by,created) VALUES ("+
                "'"+installation.getText()+"','','"+hostip+"',"+cpuID+",'vdl',0,now(), 0, now())");
        int roleID = DBConnectionThread.getThread("ROOT").getLastInsertID("ROOT");
        Statement stmt = DBConnectionThread.getThread("ROOT").getNewStatement(false);
        rs =stmt.executeQuery("SELECT * FROM edge.edgemomsetup WHERE roleid=1");
        while(rs.next()) {
          int disabled=rs.getInt("disabled");
          int edgefileID=rs.getInt("edgefileid");
          String args = rs.getString("args");
          // If this is a import port
          if(enableImport.isSelected() && rs.getString("tag").equalsIgnoreCase("IMPORT")) {
            disabled=0;
            ResultSet rs2 = DBConnectionThread.getThread("ROOT").executeQuery("SELECT * FROM edge.edgefile WHERE edgefile='_EW/import_localhost.config'");
            String config=null;
            if(rs2.next()) {
             config = rs2.getString("content");
            }
            rs2.close();
            config = doSubstitutions(config);
            config = config.replaceAll("\\$IMPORTIP", importip);
            config = config.replaceAll("\\$IMPORTPORT", ""+importport);
            Util.prt("import config after subs="+config);
            rs2 = DBConnectionThread.getThread("ROOT").executeQuery(
                    "SELECT * FROM edge.edgefile WHERE edgefile='_EW/import_"+installation.getText().trim()+".config'");
            if(rs2.next()) {
              int ID = rs2.getInt("id");
              rs2.close();
              DBConnectionThread.getThread("ROOT").executeUpdate("DELETE FROM edge.edgefile WHERE id="+ID);
            }
            else rs2.close();
            DBConnectionThread.getThread("ROOT").executeUpdate(
                "INSERT INTO edge.edgefile (edgefile,content,updated,created_by,created) VALUES ("+
                "'EW/import_"+installation.getText()+".config','"+config+"',now(), 0, now())");
            edgefileID = DBConnectionThread.getThread("ROOT").getLastInsertID("edgefile");

          }
          // if this is an traceport
          if(enableTrace.isSelected() && rs.getString("tag").equalsIgnoreCase("trace1")) {
            args = args.replaceAll("\\$TRACEPORT", ""+traceport);
            args = args.replaceAll("\\$TRACEBROADCAST", traceip);
            disabled=0;
          }
          args = doSubstitutions(args);
          DBConnectionThread.getThread("ROOT").executeUpdate(
            "INSERT INTO edge.edgemomsetup (roleid,account,tag,priority,edgethreadid,args,logfile,comment,filename,edgefileID,disabled,"+
                  "updated,created_by,created) VALUES ("+roleID+",'"+rs.getString("account")+"','"+rs.getString("tag")+"',"+
                  rs.getInt("priority")+","+rs.getInt("edgethreadid")+",'"+args+"','"+
                  rs.getString("logfile")+"','"+rs.getString("comment")+"','"+rs.getString("filename")+"',"+
                  edgefileID+","+
                  disabled+",now(), 0, now())");
        }
      }
    }
    catch(SQLException e) {
      Util.prt("SQLError="+e);
      e.printStackTrace();
    }
    catch(IOException e) {
      Util.prt("IOError="+e);
      e.printStackTrace();
    }
    error.setText("Configuration written!");
  }
  byte [] b = new byte[20000];
  private void doProp(String prop) throws IOException  {
    RandomAccessFile rw = new RandomAccessFile("scripts/INSTALL/"+prop, "rw");
    rw.read(b, 0, (int) rw.length());
    String anss= new String(b, 0, (int) rw.length());
    anss = doSubstitutions(anss);
    rw.close();
    rw = new RandomAccessFile(prop.replaceAll("\\.example", ""), "rw");
    rw.write(anss.getBytes(), 0, anss.length());
    rw.setLength(anss.length());
    rw.close();
  }
private String doSubstitutions( String edgeProp) {
    edgeProp = edgeProp.replaceAll("\\$MYSQLIP", dbip);
    edgeProp = edgeProp.replaceAll("\\$STATUSSERVER", statusip);
    edgeProp = edgeProp.replaceAll("\\$ALARMIP", alarmip);
    edgeProp = edgeProp.replaceAll("\\$SNWHOST", snwip);
    edgeProp = edgeProp.replaceAll("\\$SNWPORT", ""+snwport);
    edgeProp = edgeProp.replaceAll("\\$SMTPSERVER", smtpip);
    edgeProp = edgeProp.replaceAll("\\$EMAILTO", emailTo.getText());
    edgeProp = edgeProp.replaceAll("\\$INS", installation.getText());
    edgeProp = edgeProp.replaceAll("\\$DATAPATHS", paths.getText());
    edgeProp = edgeProp.replaceAll("\\$HOST",installation.getText());
    return edgeProp;
  }
  private void enableImportActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_enableImportActionPerformed
    importIP.setVisible(enableImport.isSelected());
    importPort.setVisible(enableImport.isSelected());
    labImportIP.setVisible(enableImport.isSelected());
    labImportPort.setVisible(enableImport.isSelected());
  }//GEN-LAST:event_enableImportActionPerformed

  private void enableTraceActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_enableTraceActionPerformed
    traceIP.setVisible(enableTrace.isSelected());
    tracePort.setVisible(enableTrace.isSelected());
    labTraceIP.setVisible(enableTrace.isSelected());
    labTracePort.setVisible(enableTrace.isSelected());
  }//GEN-LAST:event_enableTraceActionPerformed

  private void sendToNEICActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_sendToNEICActionPerformed
    labNEICSend.setVisible(sendToNEIC.isSelected());
    neicSendRE.setVisible(sendToNEIC.isSelected());
  }//GEN-LAST:event_sendToNEICActionPerformed

  private void privUserActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_privUserActionPerformed
    // TODO add your handling code here:
  }//GEN-LAST:event_privUserActionPerformed

  private void rootVendorActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_rootVendorActionPerformed
    if(rootVendor.getSelectedItem().toString().toLowerCase().indexOf("postgres") >=0) privUser.setText("postgres");
    else privUser.setText("root");
  }//GEN-LAST:event_rootVendorActionPerformed

  private void hostIPActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_hostIPActionPerformed
    // TODO add your handling code here:
  }//GEN-LAST:event_hostIPActionPerformed

  // Variables declaration - do not modify//GEN-BEGIN:variables
  private javax.swing.JTextField accounts;
  private javax.swing.JButton addUpdate;
  private javax.swing.JButton addUser;
  private javax.swing.JTextField alarmIP;
  private javax.swing.JButton changeKey;
  private javax.swing.JComboBox command;
  private javax.swing.JTextField dbIP;
  private javax.swing.JTextField dbPort;
  private javax.swing.JComboBox dbVendor;
  private javax.swing.JTextField dbs;
  private javax.swing.JButton delete;
  private javax.swing.JButton doConfig;
  private javax.swing.JTextField emailTo;
  private javax.swing.JRadioButton enableImport;
  private javax.swing.JRadioButton enableTrace;
  private javax.swing.JTextField error;
  private javax.swing.JTextField hostIP;
  private javax.swing.JTextField importIP;
  private javax.swing.JTextField importPort;
  private javax.swing.JTextField installation;
  private javax.swing.JTextField ipadr;
  private javax.swing.JLabel jLabel1;
  private javax.swing.JLabel jLabel2;
  private javax.swing.JScrollPane jScrollPane1;
  private javax.swing.JTextField key;
  private javax.swing.JPanel keyPanel;
  private javax.swing.JLabel labAccounts;
  private javax.swing.JLabel labAlarm;
  private javax.swing.JLabel labDB;
  private javax.swing.JLabel labDBPort;
  private javax.swing.JLabel labDbs;
  private javax.swing.JLabel labEmail;
  private javax.swing.JLabel labHostIP;
  private javax.swing.JLabel labIP;
  private javax.swing.JLabel labImportIP;
  private javax.swing.JLabel labImportPort;
  private javax.swing.JLabel labInstallation;
  private javax.swing.JLabel labKey;
  private javax.swing.JLabel labNEICSend;
  private javax.swing.JLabel labNodeNum;
  private javax.swing.JLabel labPath;
  private javax.swing.JLabel labPaths;
  private javax.swing.JLabel labRootIP;
  private javax.swing.JLabel labRootPw;
  private javax.swing.JLabel labRootVendor;
  private javax.swing.JLabel labSMTP;
  private javax.swing.JLabel labSNW;
  private javax.swing.JLabel labSNWP;
  private javax.swing.JLabel labServers;
  private javax.swing.JLabel labStatus;
  private javax.swing.JLabel labTraceIP;
  private javax.swing.JLabel labTracePort;
  private javax.swing.JLabel labUser;
  private javax.swing.JLabel labUserPW;
  private javax.swing.JButton makeSymbolicLinks;
  private javax.swing.JTextField neicSendRE;
  private javax.swing.JButton newButton;
  private javax.swing.JPanel newInstall;
  private javax.swing.JPanel newPanel;
  private javax.swing.JPanel newUserPanel;
  private javax.swing.JTextField nodeNumber;
  private javax.swing.JPasswordField password;
  private javax.swing.JTextField path;
  private javax.swing.JTextArea paths;
  private javax.swing.JTextField privUser;
  private javax.swing.JTextField rootIP;
  private javax.swing.JPanel rootLoginPanel;
  private javax.swing.JComboBox rootVendor;
  private javax.swing.JRadioButton sendToNEIC;
  private javax.swing.JComboBox servers;
  private javax.swing.JTextField smtpIP;
  private javax.swing.JTextField snwIP;
  private javax.swing.JTextField snwPort;
  private javax.swing.JTextField statusIP;
  private javax.swing.JTextArea text;
  private javax.swing.JScrollPane textPanel;
  private javax.swing.JTextField traceIP;
  private javax.swing.JTextField tracePort;
  private javax.swing.JTextField userPassword;
  private javax.swing.JTextField username;
  // End of variables declaration//GEN-END:variables
/** This main displays the form Pane by itself
   *@param args command line args ignored*/
  public static void main(String args[]) {
    DBConnectionThread jcjbl;
    Util.init("edge.prop");
    Util.setNoInteractive(true);
    Util.setNoconsole(false);
    boolean manual=false;
    boolean dump=false;
    DBSetupGUI setup =null;
    String vendor="mysql";
    BufferedReader in =null;
    for(int j=0; j<args.length; j++) {
      if(args[j].equals("-?") || args[j].equals("-help")) {
        Util.prt("To do a manual mode configuration :");
        Util.prt("dbsetup -gui -man [-h host][-ip ip.adr][-db ip.adr][-neic regexp][-import ip.adr:port][-trace ip.adr:port][-email e.adr][-path file][-smtp ip.adr][-alarm ip.adr][-status ip.adr][-nodenum nn][-snw ip.adr:port][-vendor mysql");
        Util.prt("-h  hostname  Like gldketchum");
        Util.prt("-ip ip.adr    The numeric IP address of the the host from -h");
        Util.prt("-db     ip.adr/port The IP address of the database server controlling this configuration (def=localhost)");
        Util.prt("-neic regexp  A regular expression of channels in NNSSSSSCCCLL to send to neic everything='.*',  seismic='......[BESH][HN].*'");
        Util.prt("-import ip.adr:port Get data from an Earthworm IMPORT at this IP and port (you must edit the EW/*.config for alive messages");
        Util.prt("-trace  ip.adr:port Get UDP trace data from the IP address (broadcast adr) and port");
        Util.prt("-email  email       Send emails of all errors to this person(def=ketchum@usgs.gov)");
        Util.prt("-path   filename    Filename contains a path setup for this node including npaths, nday?, and datapath? (def is 1 path on /data for 10 days)");
        Util.prt("-smtp   ip.adr      Set the SMTP server to this IP for e-mails sent from the node ");
        Util.prt("-alarm  ip.adr      IP address of Alarm server program (def=localhost)");
        Util.prt("-status ip.adr      IP address of the UdpChannel and TcpHoldings servers (def=localhost)");
        Util.prt("-nodenum nn         A number to add to this nodename (which does not end in a number) to keep files unique");
        Util.prt("-snw    ip.adr:port The IP and port of a SeisNetWatch SocketServer for contribuiting latency data to SNW(def=No SNW)");
        Util.prt("-vendor mysql|postgres The vendor of the database to use - this sets the port number to standard ports(def=mysql)");
      }
      if(args[j].equals("-man")) {
        in = new BufferedReader(new InputStreamReader(System.in));
        setup = new DBSetupGUI();
        setup.dbIP.setText("localhost");
        

        for(int i=0; i<args.length; i++) {
          if(args[i].equals("-man")) manual=true;
          else if(args[i].equals("-dump")) dump=true;
          else if(args[i].equals("-h")) {setup.installation.setText(args[i+1]); i++;}
          else if(args[i].equals("-ip")) {setup.hostIP.setText(args[i+1]); i++;}
          else if(args[i].equals("-db")) {setup.dbIP.setText(args[i+1]); i++;}
          else if(args[i].equals("-smtp")) {setup.smtpIP.setText(args[i+1]); i++;}
          else if(args[i].equals("-alarm")) {setup.smtpIP.setText(args[i+1]); i++;}
          else if(args[i].equals("-status")) {setup.statusIP.setText(args[i+1]); i++;}
          else if(args[i].equals("-nodenum")) {setup.nodeNumber.setText(args[i+1]); i++;}
          else if(args[i].equals("-email")) {setup.emailTo.setText(args[i+1]); i++;}
          else if(args[i].equals("-vendor")) {vendor = args[i+1]; i++;}
          else if(args[i].equals("-path")) {
            try {
              RandomAccessFile rw = new RandomAccessFile(args[i+1],"rw");
              byte [] b = new byte[(int) rw.length()];
              rw.read(b);
              String s = new String(b);
              setup.paths.setText(s);
            }
            catch(IOException e) {
              Util.prt("-path file error e="+e);
              System.exit(1);
            }
          }          
          else if(args[i].equals("-snw")) {
            String [] s = args[i+1].split("[:/]");
            setup.snwIP.setText(s[0]); 
            if(s.length >0) setup.snwPort.setText(s[1]);
            i++;
          }          
          else if(args[i].equals("-trace")) {
            setup.enableTrace.setSelected(true);
            String [] s = args[i+1].split("[:/]");
            setup.traceIP.setText(s[0]); 
            if(s.length >0) setup.tracePort.setText(s[1]);
            i++;
          }
         else if(args[i].equals("-import")) {
            setup.enableImport.setSelected(true);
            String [] s = args[i+1].split("[:/]");
            setup.importIP.setText(s[0]); 
            if(s.length >0) setup.importPort.setText(s[1]);
            i++;
          }
          else if(args[i].equals("-neic")) {setup.sendToNEIC.setSelected(true); setup.neicSendRE.setText(args[i+1]); i++;}
          else Util.prt(" **** Manual configuration unknown switch="+i+" "+args[i]);
        }
        if(dump) {
          Util.prt("Host : "+setup.installation.getText()+" ip="+setup.hostIP.getText()+" DBServer="+setup.dbIP.getText()+" add node number"+setup.nodeNumber.getText());
          if(setup.sendToNEIC.isSelected()) Util.prt("Send to NEIC is ON for channels matching '"+setup.neicSendRE.getText());
          else Util.prt("Send to NEIS OFF");
          if(setup.enableImport.isSelected()) Util.prt("IMPORT data from "+setup.importIP.getText()+"/"+setup.importPort.getText());
          if(setup.enableTrace.isSelected()) Util.prt("UDP trace data from "+setup.traceIP.getText()+"/"+setup.tracePort.getText());
          if(!setup.snwIP.getText().equalsIgnoreCase("none") && !setup.snwIP.getText().equals(""))
            Util.prt("Send data to SNW at "+setup.snwIP.getText()+"/"+setup.snwPort.getText());
          else Util.prt("No SNW");
          Util.prt("Emails go to "+setup.emailTo.getText()+"\n"+setup.paths.getText());
        }

        Util.prt("Enter username=password for privileged access to "+setup.dbIP.getText());
        String line = null;
        try {
          line = in.readLine();
        }
        catch(IOException e) {
          Util.prt("error getting line exit.");
          System.exit(1);
        }
        String [] s = line.split("=");
        String user="root";
        String pw="";
        if(s.length == 1) {
          pw=s[0];
        }
        else {
          user=s[0];
          pw= s[1];
        }
        try {
          Util.prt("serverSelect log into "+setup.dbIP.getText()+" vend="+vendor);
          DBConnectionThread root = new DBConnectionThread(setup.dbIP.getText(), vendor, 
                      (vendor.toLowerCase().indexOf("postgr") >=0?"postgres":user),
                  pw,  true, false, "ROOT", vendor, Util.getOutput());
          pw="";
          if(!root.waitForConnection())
            if(!root.waitForConnection()) {
              Util.prt("Did Not connect to root.  Is root password wrong? ");
              System.exit(1);
            }
          Util.prt("Root login root is successfull");  
        }
        catch(InstantiationException e) {
          Util.prt("Instantiation exception trying to login as root");
          System.exit(1);
        }
        Util.prt("Is this correct (y/n)");
        try {
          String ans = in.readLine();
          if((ans+" ").substring(0,1).toLowerCase().equalsIgnoreCase("y")) {
            
            setup.doConfigActionPerformed(null);
            if(setup.err.isSet()) 
              Util.prt("**** error processing = "+setup.err.getText()+" error="+setup.error.getText());
          }
        }
        catch(IOException e) {
          Util.prt("Error getting user input - exit");
          System.exit(2);
        }    
        System.exit(0);
      }
    }


        // Make test DBconnection for form
    Show.inFrame(new DBSetupGUI(), 650  , 575);
  }
}
