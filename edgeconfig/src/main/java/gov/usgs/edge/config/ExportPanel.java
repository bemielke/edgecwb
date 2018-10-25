package gov.usgs.edge.config;

/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */

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
 * JComboBox variable this variable is called "export" in the initial form
 * The table name and key name should match and start lower case (export).
 * The Class here will be that same name but with upper case at beginning(Export).
 * <br> 1)  Rename the location JComboBox to the "key" (export) value.  This should start Lower
 *      case.
 *<br>  2)  The table name should match this key name and be all lower case.
 *<br>  3)  Change all Export to ClassName of underlying data (case sensitive!)
 *<br>  4)  Change all export key value (the JComboBox above)
 *<br>  5)  clearScreen should update swing components to new defaults
 *<br>  6)  chkForm should check all field for validity.  The errorTrack variable is
 *      used to track multiple errors.  In FUtil.chk* handle checks of the various
 *      variable types.  Date, double, and IP etc return the "canonical" form for 
 *      the variable.  These should be stored in a class variable so the latest one
 *      from "chkForm()" are available to the update writer.
 *<br>  7) newone() should be updated to create a new instance of the underlying data class.
 *<br>  8) oldone() get data from database and update all swing elements to correct values
 *<br>  9) addUpdateAction() add data base set*() operations updating all fields in the DB
 *<br> 10) ExportPanel() constructor - good place to change backgrounds using UC.Look() any
 *    other weird startup stuff.
 *
 *<br>  local variable error - Must be a JTextField for posting error communications
 * <br> local variable updateAdd - Must be the JButton for user clicks to try to post the data
 * <br> ID - JTextField which must be non-editable for posting Database IDs
 * <br>
 *<br>
 *ENUM notes :  the ENUM are read from the database as type String.  So the constructor
 * should use the strings to set the indexes.  However, the writes to the database are
 * done as ints representing the value of the Enum.  The JComboBox with the Enum has 
 * the strings as Items in the list with indexes equal to the database value of the int.
 * Such JComboBoxes are usually initialized with 
 *<br>
 *<br> FUtil.getEnumJComboBox(DBConnectionThread.getConnection("DATABASE"), "tcpstation","rtsport1");
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
 * <br>    if(fieldEnum == null) fieldEnum = FUtil.getEnum(DBConnectionThread.getConnection("DATABASE"),"table","fieldName");
 * <br>    
 * <br>   // Get the int corresponding to an Enum String for storage in the data base and 
 * <br>   // the index into the JComboBox representing this Enum 
 * <br>   localVariable = FUtil.enumStringToInt(fieldEnum, enumString);
  * <br>
 * @author  D.C. Ketchum
 */
public class ExportPanel extends javax.swing.JPanel {
  DBObject obj;
  ErrorTrack err=new ErrorTrack();
  boolean initiating=false;
  
  //USER: Here are the local variables
  String exportIP;
  int exportPort;
  private int HBint;
  private int HBto;
  private int institutionVal;
  private int queueSizeVal;
  private int moduleVal;
  private int maxlatency;
  private int bandwidth; // kbits/sec
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
   Util.prt("chkForm Export");
   exportIP = FUtil.chkIP(ipadr, err);
   if(port.getText().equals("")) exportPort=0;
   else exportPort = FUtil.chkInt(port, err, 1024, 32767, true);
   institutionVal = FUtil.chkInt(institution, err, 0, 255, true);
   queueSizeVal = FUtil.chkInt(queueSize, err, 0, 50000, true);
   maxlatency = FUtil.chkInt(maxLatency, err, 0, 18000, true);
   moduleVal = FUtil.chkInt(module, err, 0, 255, true);
   HBint = FUtil.chkInt(hbint, err, 0, 240, true);
   HBto = FUtil.chkInt(hbto, err, 0, 300, true);
   bandwidth = FUtil.chkInt(nooutput, err, 0, 80000, true);

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
    addUpdate.setText("Enter a Export");
    
    //USER: Clear all fields for this form, this sets "defaults" for new screen
    // description.setText("");
    
    ipadr.setText("");
    port.setText("");
    type.setSelectedIndex(0);
    convertToSCN.setSelected(false);
    allowRestricted.setSelected(false);
    sniffLog.setSelected(false);
    ringfile.setText("/data2/^Export.ring");
    institution.setText("");
    maxLatency.setText("");
    queueSize.setText("");
    module.setText("");
    hbint.setText("");
    hbto.setText("");
    hbin.setText("");
    hbout.setText("");
    nooutput.setText("");
    chans.setText("");
    channelList.setText("");
    newRole.setSelectedIndex(-1);

  }
 
  
  private Export newOne() {
      
    return new Export(0, (""+export.getSelectedItem()) //, more,
       , "", "", 0, "Generic", 0, "", 0, 0);
  }
  
  private void oldOne(String p) throws SQLException {
    obj = new DBObject(DBConnectionThread.getThread(DBConnectionThread.getDBSchema()), 
            DBConnectionThread.getDBSchema(),"export","export",p);

    if(obj.isNew()) {
      Util.prt("object is new"+p);
    }
    else {
      //USER: Here set all of the form fields to data from the DBObject
      //      export.setText(obj.getString("Export"));
      // Example : description.setText(obj.getString("description"));
      ipadr.setText(obj.getString("exportipadr"));
      port.setText(""+(obj.getInt("exportport") == 0?"":obj.getInt("exportport")));
      channelList.setText(obj.getString("chanlist"));
      convertToSCN.setSelected(obj.getInt("scn") != 0);
      allowRestricted.setSelected( (obj.getInt("allowrestricted") & Export.MASK_ALLOWRESTRICTED) != 0);
      sniffLog.setSelected((obj.getInt("allowrestricted") & Export.MASK_SNIFFLOG) != 0);
      
      ringfile.setText(obj.getString("ringfile"));
      type.setSelectedItem(obj.getString("type"));
      institution.setText(obj.getInt("institution")+"");
      maxLatency.setText(obj.getInt("maxlatency")+"");
      queueSize.setText(obj.getInt("queuesize")+"");
      module.setText(obj.getInt("module")+"");
      hbint.setText(obj.getInt("hbint")+"");
      hbto.setText(obj.getInt("hbto")+"");
      hbin.setText(obj.getString("hbin"));
      hbout.setText(obj.getString("hbout"));
      nooutput.setText(obj.getInt("nooutput")+"" );
      calculateChannelList(false);
      newRole.setSelectedIndex(-1);

        
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
    export = getJComboBox();
    addUpdate = new javax.swing.JButton();
    ID = new javax.swing.JTextField();
    jLabel9 = new javax.swing.JLabel();
    error = new javax.swing.JTextField();
    labIP = new javax.swing.JLabel();
    ipadr = new javax.swing.JTextField();
    type = FUtil.getEnumJComboBox(DBConnectionThread.getConnection(DBConnectionThread.getDBSchema()), "export","type");
    delete = new javax.swing.JButton();
    jLabel3 = new javax.swing.JLabel();
    role = RolePanel.getJComboBox();
    subpanel = new javax.swing.JPanel();
    labQueueSize = new javax.swing.JLabel();
    queueSize = new javax.swing.JTextField();
    allowRestricted = new javax.swing.JRadioButton();
    convertToSCN = new javax.swing.JRadioButton();
    labHbout = new javax.swing.JLabel();
    hbout = new javax.swing.JTextField();
    labHbin = new javax.swing.JLabel();
    hbin = new javax.swing.JTextField();
    labHbto = new javax.swing.JLabel();
    hbto = new javax.swing.JTextField();
    labHbint = new javax.swing.JLabel();
    hbint = new javax.swing.JTextField();
    labModule = new javax.swing.JLabel();
    module = new javax.swing.JTextField();
    labInst = new javax.swing.JLabel();
    institution = new javax.swing.JTextField();
    sniffLog = new javax.swing.JRadioButton();
    labMax = new javax.swing.JLabel();
    maxLatency = new javax.swing.JTextField();
    labRole = new javax.swing.JLabel();
    labNewRole = new javax.swing.JLabel();
    newRole = RolePanel.getJComboBox();
    labType = new javax.swing.JLabel();
    labRingfile = new javax.swing.JLabel();
    ringfile = new javax.swing.JTextField();
    labNoOutput = new javax.swing.JLabel();
    nooutput = new javax.swing.JTextField();
    labPort = new javax.swing.JLabel();
    port = new javax.swing.JTextField();
    labPort2 = new javax.swing.JLabel();
    channelListScroll = new javax.swing.JScrollPane();
    channelList = new javax.swing.JTextArea();
    chanResultScroll = new javax.swing.JScrollPane();
    chans = new javax.swing.JTextArea();
    rename = new javax.swing.JTextField();

    setLayout(new java.awt.GridBagLayout());

    jLabel1.setText("Export : ");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 2;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_END;
    add(jLabel1, gridBagConstraints);

    export.setEditable(true);
    export.setToolTipText("This is a name to give this export - something like \"NEIC\".\n");
    export.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        exportActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 2;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(export, gridBagConstraints);

    addUpdate.setText("Add/Update");
    addUpdate.setToolTipText("Click here to add a new station or save changes to the database.");
    addUpdate.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        addUpdateActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 16;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(addUpdate, gridBagConstraints);

    ID.setColumns(8);
    ID.setEditable(false);
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 19;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(ID, gridBagConstraints);

    jLabel9.setText("ID :");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 19;
    add(jLabel9, gridBagConstraints);

    error.setColumns(40);
    error.setEditable(false);
    error.setMinimumSize(new java.awt.Dimension(488, 22));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(error, gridBagConstraints);

    labIP.setText("IP Addr of Import:");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 3;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    add(labIP, gridBagConstraints);

    ipadr.setColumns(17);
    ipadr.setToolTipText("<html>\nThe IP address of the far end of this connection.  \n<br>Normally, EXPORT is a client and this address is used to screen connections \n<br>so that only known hosts can connect to the EXPORT service and to set the \n<br>right channel set for this one.  If multiple connection from a single host is expected, \n<br>they must connect to different EWExport ports.\n\nFor the rare EXPORT as client, this is the address to contact and the port is the address to contact.");
    ipadr.setMinimumSize(new java.awt.Dimension(218, 28));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 3;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(ipadr, gridBagConstraints);

    type.setToolTipText("This is the list of EXPORT types.  These correspond to the earthworm software types generic, ack, and actv.");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 4;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(type, gridBagConstraints);

    delete.setText("Delete Export");
    delete.setToolTipText("Click here to delete this export configuration in its entirety.");
    delete.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        deleteActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 17;
    add(delete, gridBagConstraints);

    jLabel3.setText("Chan 12 char REs to send (or $EXPORT_TAG) - No location code use '--':");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 11;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
    add(jLabel3, gridBagConstraints);

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

    subpanel.setMinimumSize(new java.awt.Dimension(500, 220));
    subpanel.setPreferredSize(new java.awt.Dimension(500, 200));
    subpanel.setRequestFocusEnabled(false);

    labQueueSize.setText("QueueSize (def=1000):");
    subpanel.add(labQueueSize);

    queueSize.setColumns(4);
    queueSize.setToolTipText("This is the queue of size for realtime data.  It should be set so that the \"catchup\" mode is not over used.");
    queueSize.setMinimumSize(new java.awt.Dimension(74, 28));
    subpanel.add(queueSize);

    allowRestricted.setText("AllowRestricted");
    allowRestricted.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        allowRestrictedActionPerformed(evt);
      }
    });
    subpanel.add(allowRestricted);

    convertToSCN.setText("Convert to SCN ");
    convertToSCN.setToolTipText("If this is selected, location codes are stripped from the sent channels, and the TraceBufs are converted to type 1.0.\n");
    convertToSCN.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        convertToSCNActionPerformed(evt);
      }
    });
    subpanel.add(convertToSCN);

    labHbout.setText("HeartBeatOutMsg(def=exportalive):");
    subpanel.add(labHbout);

    hbout.setColumns(20);
    hbout.setToolTipText("Set the text this EXPORT will send with each heart beat.");
    hbout.setMinimumSize(new java.awt.Dimension(200, 28));
    subpanel.add(hbout);

    labHbin.setText("HeartBeatInMsg(def=importalive):");
    subpanel.add(labHbin);

    hbin.setColumns(20);
    hbin.setToolTipText("Set the heartbeat message text expected to be received from the foreign IMPORT.");
    subpanel.add(hbin);

    labHbto.setText("HeartBeatTimeout (def=120):");
    subpanel.add(labHbto);

    hbto.setColumns(4);
    hbto.setToolTipText("Set the timeout interval for no heartbeats to cause a disconnection in seconds.");
    subpanel.add(hbto);

    labHbint.setText("HeartBeatInterval (def=30):");
    subpanel.add(labHbint);

    hbint.setColumns(4);
    hbint.setToolTipText("Set the interval at which this export will beat its heart in seconds.");
    subpanel.add(hbint);

    labModule.setText("Module(def=30):");
    subpanel.add(labModule);

    module.setColumns(4);
    module.setToolTipText("The earthworm module number to use to send this data.");
    subpanel.add(module);

    labInst.setText("Inst(def=13):");
    subpanel.add(labInst);

    institution.setColumns(4);
    institution.setToolTipText("Institution code per the earthworm globals file.");
    subpanel.add(institution);

    sniffLog.setText("SniffLog");
    sniffLog.setToolTipText("If set, this exports log will include one line for each sent TraceBuf.  This is like \"sniff wave\".");
    sniffLog.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        sniffLogActionPerformed(evt);
      }
    });
    subpanel.add(sniffLog);

    labMax.setText("MaxLatency (sec 0=5 hours):");
    subpanel.add(labMax);

    maxLatency.setColumns(6);
    maxLatency.setToolTipText("Set the limit on the oldest data that can be sent out to this number of seconds.  Zero = 5 hours.  This parameter is in seconds.  So on reconnections any data older than this that is in the ring, will be skipped and a gap generated at the receiver.");
    maxLatency.setMinimumSize(new java.awt.Dimension(70, 28));
    subpanel.add(maxLatency);

    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 8;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
    add(subpanel, gridBagConstraints);

    labRole.setText("Role:");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_END;
    add(labRole, gridBagConstraints);

    labNewRole.setText("Move To Role:");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 18;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    add(labNewRole, gridBagConstraints);

    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 18;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
    add(newRole, gridBagConstraints);

    labType.setText("Export Type :");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 4;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_END;
    add(labType, gridBagConstraints);

    labRingfile.setText("Ring File:");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 5;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_END;
    add(labRingfile, gridBagConstraints);

    ringfile.setColumns(30);
    ringfile.setToolTipText("This is the file with the data for this export.  Normally, /data2/^Export.ring.");
    ringfile.setMinimumSize(new java.awt.Dimension(300, 28));
    ringfile.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        ringfileActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 5;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
    add(ringfile, gridBagConstraints);

    labNoOutput.setText("BandWidth (kbits/s)[0=nooutput]:");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 6;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_END;
    add(labNoOutput, gridBagConstraints);

    nooutput.setColumns(5);
    nooutput.setToolTipText("<html>\nData output from this export will be limited to this number of k-bits/s.  <br>Data will be buffered and sent in order to enforce this limit.  <br>A zero means, do not send any data on this port - essentially turn off this export.");
    nooutput.setMinimumSize(new java.awt.Dimension(74, 28));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 6;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
    add(nooutput, gridBagConstraints);

    labPort.setText("Port (if ACTV destination, ");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 9;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_END;
    add(labPort, gridBagConstraints);

    port.setColumns(7);
    port.setToolTipText("<html>\nIf this is a ACTV (client export), then this is the port on the destination to make the connection.\n<br>\n<br>\nIf this is a normal server export, this is blank unless multiple connections from the same source <br>\nare expected.  If multiple connections, this must be the port on the export for this connection and <br>\nthe export must serve multiple ports via -p port1:port2 in the EWExport configuration.<br>");
    port.setMinimumSize(new java.awt.Dimension(75, 28));
    port.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        portActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 9;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
    add(port, gridBagConstraints);

    labPort2.setText("if server, optional port, norm=blank):");
    labPort2.setMaximumSize(new java.awt.Dimension(149, 12));
    labPort2.setMinimumSize(new java.awt.Dimension(149, 12));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 10;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_END;
    add(labPort2, gridBagConstraints);

    channelListScroll.setToolTipText("One regular expression per line to be expanded into a channel list;\n\nExamples:\n\nUSDUG  BHZ00\nUSDUG  BH.00\nUSDUG  [LH]BH.00\nUSDUG  .....\nUS.....BH.00\n");
    channelListScroll.setMinimumSize(new java.awt.Dimension(244, 164));
    channelListScroll.setPreferredSize(new java.awt.Dimension(244, 164));

    channelList.setColumns(20);
    channelList.setFont(new java.awt.Font("Monospaced", 0, 12)); // NOI18N
    channelList.setRows(100);
    channelList.setToolTipText("<html>\nOne regular expression per line to be expanded into a channel list.  To indicate no location code use -- in the location code place ('USDUG  BH.--').\n<br><br>\nExamples:\n<br>\nUSACSO BH.--\n<br>\nUSDUG  BHZ00\n<br>\nUSDUG  BH.00\n<br>\nUSDUG  [LH]BH.00\n<br>\nUSDUG  .....\n<br>\nUS.....BH.00\n");
    channelList.setMinimumSize(new java.awt.Dimension(500, 200));
    channelList.addFocusListener(new java.awt.event.FocusAdapter() {
      public void focusLost(java.awt.event.FocusEvent evt) {
        channelListFocusLost(evt);
      }
    });
    channelList.addKeyListener(new java.awt.event.KeyAdapter() {
      public void keyReleased(java.awt.event.KeyEvent evt) {
        channelListKeyReleased(evt);
      }
    });
    channelListScroll.setViewportView(channelList);

    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 12;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(channelListScroll, gridBagConstraints);

    chanResultScroll.setMinimumSize(new java.awt.Dimension(160, 160));
    chanResultScroll.setPreferredSize(new java.awt.Dimension(160, 160));

    chans.setBackground(new java.awt.Color(204, 204, 204));
    chans.setColumns(20);
    chans.setEditable(false);
    chans.setFont(new java.awt.Font("Monospaced", 0, 12)); // NOI18N
    chans.setRows(500);
    chans.setToolTipText("This area shows the detailed channel list which match the regular expressions in the \"List of REs to Send\" window.  There will be one channel per line.");
    chans.setMinimumSize(new java.awt.Dimension(200, 200));
    chans.setPreferredSize(new java.awt.Dimension(200, 6000));
    chanResultScroll.setViewportView(chans);

    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 12;
    add(chanResultScroll, gridBagConstraints);

    rename.setColumns(40);
    rename.setMinimumSize(new java.awt.Dimension(200, 28));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 17;
    add(rename, gridBagConstraints);
  }// </editor-fold>//GEN-END:initComponents

  private void addUpdateActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_addUpdateActionPerformed
    // Add your handling code here:
    
    // Do not change
    if(initiating) return;
    if(chkForm()) return;
    try {
      String p = export.getSelectedItem().toString();
      //p = p.toUpperCase();                  // USER : drop this to allow Mixed case
      if(!obj.isNew()) obj.refreshRecord();        // Refresh the result set so the sets will not fail on stale one
      if(rename.getText().equals("")) obj.setString("export",p);
      else obj.setString("export", rename.getText());
      rename.setText("");

      // USER : Set all of the fields in data base.  obj.setInt("thefield", ivalue);
      obj.setString("exportipadr", exportIP);
      obj.setInt("exportport", exportPort);
      obj.setInt("scn", (convertToSCN.isSelected()? 1: 0));
      int mask=0;
      if(allowRestricted.isSelected()) mask = mask | Export.MASK_ALLOWRESTRICTED;
      if(sniffLog.isSelected()) mask = mask | Export.MASK_SNIFFLOG;
      
      obj.setInt("allowrestricted", mask);
      
      obj.setString("ringfile", ringfile.getText());
      obj.setString("chanlist", Util.chkTrailingNewLine(channelList.getText()));
      obj.setInt("type", type.getSelectedIndex());
      obj.setInt("hbint",HBint);
      obj.setInt("institution", institutionVal);
      obj.setInt("maxlatency", maxlatency);
      obj.setInt("queuesize", queueSizeVal);
      obj.setInt("module", moduleVal);
      obj.setInt("hbto", HBto);
      obj.setString("hbin", hbin.getText());
      obj.setString("hbout", hbout.getText());
      obj.setInt("nooutput", (nooutput.getText().equals("")? 500:bandwidth));
      if(newRole.getSelectedIndex() != -1) 
        obj.setInt("roleid", ((Role) newRole.getSelectedItem()).getID());
      else obj.setInt("roleid",((Role) role.getSelectedItem()).getID());


      // Do not change
      obj.updateRecord();
      Export.v=null;       // force reload of combo box
      int idout = obj.getID();
      getJComboBox(export);
      setJComboBoxToID(export, idout);
      calculateChannelList(true);
      buildExportJCombo();
      clearScreen();
      export.setSelectedIndex(-1);
    }  catch  (SQLException  E)
    { Util.SQLErrorPrint(E,"export: update failed partno="+export.getSelectedItem());
      error.setText("Could Not Update File!");
      error.setBackground(UC.red);
      addUpdate.setEnabled(true);
    }      
  }//GEN-LAST:event_addUpdateActionPerformed

  private void exportActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_exportActionPerformed
   if(initiating) return;
   find();
   if(export.getSelectedIndex() < 0) return;
   calculateChannelList(false);
  /* Export  e= ((Export) export.getSelectedItem());
   try {
    e.getExportList(DBConnectionThread.getThread("edge"));
   }
   catch  (SQLException  E)
    { Util.prt("SQL e="+E);
      E.printStackTrace();
      Util.SQLErrorPrint(E,"export: update failed partno="+export.getSelectedItem());
   }
   Iterator itr = e.getChannels().keySet().iterator();
   StringBuilder sb = new StringBuilder(1000);
   int nstat=0;
   while(itr.hasNext()) {
      sb.append(itr.next()).append("\n");
     nstat++;
   }
    sb.append("#Channels=").append(nstat).append("\n");
   chans.setText(sb.toString());*/

  }//GEN-LAST:event_exportActionPerformed

private void portActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_portActionPerformed

}//GEN-LAST:event_portActionPerformed

private void convertToSCNActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_convertToSCNActionPerformed

}//GEN-LAST:event_convertToSCNActionPerformed

private void channelListFocusLost(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_channelListFocusLost
  calculateChannelList(false);
}//GEN-LAST:event_channelListFocusLost

private void deleteActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_deleteActionPerformed
  // USer wants to delete this export, do this record and all matching records in the exportchan file
   try {
    int exID = ((Export) export.getSelectedItem()).getID();
     try (Statement stmt = DBConnectionThread.getConnection("edge").createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE)) {
       int i = stmt.executeUpdate("DELETE FROM exportchan where exportid="+exID);
       Util.prt("Deleted "+i+" channels for export="+((Export) export.getSelectedItem()).toString());
       i = stmt.executeUpdate("DELETE FROM export WHERE id="+exID);
       Util.prt("Deleted "+i+" export="+((Export) export.getSelectedItem()).toString());
     }
    export.removeItemAt(export.getSelectedIndex());
  }
  catch(SQLException e) {
    Util.prt("Error deleting EXPORT or CHANNELS"+e);
  }
  clearScreen();
}//GEN-LAST:event_deleteActionPerformed

  private void roleActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_roleActionPerformed
    buildExportJCombo();
  }//GEN-LAST:event_roleActionPerformed

  private void channelListKeyReleased(java.awt.event.KeyEvent evt) {//GEN-FIRST:event_channelListKeyReleased
    int chr = evt.getKeyChar();
    if(chr == '\n') 
      calculateChannelList(false);
  }//GEN-LAST:event_channelListKeyReleased

  private void allowRestrictedActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_allowRestrictedActionPerformed
    // TODO add your handling code here:
  }//GEN-LAST:event_allowRestrictedActionPerformed

  private void ringfileActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_ringfileActionPerformed
   
  }//GEN-LAST:event_ringfileActionPerformed

  private void sniffLogActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_sniffLogActionPerformed
    
  }//GEN-LAST:event_sniffLogActionPerformed
  private void buildExportJCombo() {
    if(initiating) return;
    if(role.getSelectedIndex() == -1 ) return;
    export.removeAllItems();
    int roleID = ((Role) role.getSelectedItem()).getID();
    for(int i=0; i<Export.v.size();i++) {
      if(Export.v.get(i).getRoleID() == roleID) export.addItem(Export.v.get(i));
    }
  }
  
  private void calculateChannelList(boolean doUpdate) {
     try {
       StringBuilder sb;
       int nc;
       try (Statement stmt = DBConnectionThread.getConnection("edge").createStatement()) {
         sb = new StringBuilder(1000);
         String list = channelList.getText().replaceAll("\n","|");
         while( list.charAt(list.length()-1) == '|') list = list.substring(0,list.length()-1);
         nc = 0;
         try (ResultSet rs = stmt.executeQuery("select channel from channel where channel regexp '"+list+"'")) {
           while(rs.next()) {
             sb.append(rs.getString(1)).append("\n");
             nc++;
           }
         }
       }
      sb.append(nc).append(" channels\n");
      chans.setText(sb.toString());

    }
    catch(SQLException e) {
      error.setText("SQLerror trying to get channel list e="+e);
    }   

  /*String [] regexp = channelList.getText().split("\\n");
  if(channelList.getText().length() <2) return;
  if(channelList.getText().substring(0,1).equals("$")) {
    try {
      Statement stmt = DBConnectionThread.getConnection("edge").createStatement();
      ResultSet rs = stmt.executeQuery("select chanlist from export where export='"+regexp[0].substring(1)+"'");
      if(rs.next()) {
        regexp = rs.getString(1).split("\\n");
      }  
      rs.close();
      stmt.close();
    }
    catch(SQLException e) {
      error.setText("Could not find key="+regexp[0]);
    }
    
  }
  StringBuilder sb = new StringBuilder(1000);
  ArrayList<String> chs = new ArrayList<String>(1000);
  try {
    Statement stmt = DBConnectionThread.getConnection("edge").createStatement();
    int nstat=0;
    for(int i=0; i< regexp.length; i++) {
      if(regexp[i].replaceAll(" ","").equals("")) continue;
      regexp[i] = regexp[i].replaceAll("-"," ").trim();
      ResultSet rs = stmt.executeQuery("SELECT id,channel FROM channel WHERE channel regexp '"+regexp[i]+
              "' order by channel");
      while(rs.next()) {
        sb.append(Util.leftPad(""+rs.getInt(1)+",",8)).append(rs.getString(2)).append("\n");
        chs.add(rs.getInt(1)+","+rs.getString(2));
        nstat++;
      }
      rs.close();
    }
    sb.append("#channels=").append(nstat).append("\n");
    stmt.close();
  }
  catch(SQLException e) {

  }
  chans.setText(sb.toString());
  try {
    if(doUpdate)
      ((Export) export.getSelectedItem()).reconcileDB(DBConnectionThread.getThread("edge"), chs);
  }
  catch(SQLException e) {
    Util.prt("SQLException reconciling list e="+e);
    e.printStackTrace();
    Util.SQLErrorPrint(e," Problem updating channel list");
  }*/
}
  
  // Variables declaration - do not modify//GEN-BEGIN:variables
  private javax.swing.JTextField ID;
  private javax.swing.JButton addUpdate;
  private javax.swing.JRadioButton allowRestricted;
  private javax.swing.JScrollPane chanResultScroll;
  private javax.swing.JTextArea channelList;
  private javax.swing.JScrollPane channelListScroll;
  private javax.swing.JTextArea chans;
  private javax.swing.JRadioButton convertToSCN;
  private javax.swing.JButton delete;
  private javax.swing.JTextField error;
  private javax.swing.JComboBox export;
  private java.awt.GridBagLayout gridBagLayout1;
  private javax.swing.JTextField hbin;
  private javax.swing.JTextField hbint;
  private javax.swing.JTextField hbout;
  private javax.swing.JTextField hbto;
  private javax.swing.JTextField institution;
  private javax.swing.JTextField ipadr;
  private javax.swing.JLabel jLabel1;
  private javax.swing.JLabel jLabel3;
  private javax.swing.JLabel jLabel9;
  private javax.swing.JLabel labHbin;
  private javax.swing.JLabel labHbint;
  private javax.swing.JLabel labHbout;
  private javax.swing.JLabel labHbto;
  private javax.swing.JLabel labIP;
  private javax.swing.JLabel labInst;
  private javax.swing.JLabel labMax;
  private javax.swing.JLabel labModule;
  private javax.swing.JLabel labNewRole;
  private javax.swing.JLabel labNoOutput;
  private javax.swing.JLabel labPort;
  private javax.swing.JLabel labPort2;
  private javax.swing.JLabel labQueueSize;
  private javax.swing.JLabel labRingfile;
  private javax.swing.JLabel labRole;
  private javax.swing.JLabel labType;
  private javax.swing.JTextField maxLatency;
  private javax.swing.JTextField module;
  private javax.swing.JComboBox newRole;
  private javax.swing.JTextField nooutput;
  private javax.swing.JTextField port;
  private javax.swing.JTextField queueSize;
  private javax.swing.JTextField rename;
  private javax.swing.JTextField ringfile;
  private javax.swing.JComboBox role;
  private javax.swing.JRadioButton sniffLog;
  private javax.swing.JPanel subpanel;
  private javax.swing.JComboBox type;
  // End of variables declaration//GEN-END:variables
  /** Creates new form ExportPanel. */
  public ExportPanel() {
    initiating=true;
    if(Export.typeEnum == null) Export.typeEnum = FUtil.getEnum(DBConnectionThread.getConnection(DBConnectionThread.getDBSchema()),"export","type");
    initComponents();
    getJComboBox(export);                // set up the key JComboBox
    export.setSelectedIndex(-1);    // Set selected type
    Look();                    // Set color background
    UC.Look(subpanel);
    UC.Look(sniffLog);
    UC.Look(allowRestricted);
    UC.Look(convertToSCN);
    initiating=false;
    // USER:This is a good place for UC.Look(components) to get rid of the ugly Grey
    role.setSelectedIndex(-1);
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
    makeExports();
    for (int i=0; i< Export.v.size(); i++) {
      b.addItem( Export.v.get(i));
    }
    b.setMaximumRowCount(30);
  }
  
  /** Given a JComboBox from getJComboBox, set selected item to match database ID
   *@param b The JComboBox
   *@param ID the row ID from the database to select
   */
  public static void setJComboBoxToID(JComboBox b, int ID) {
    b.setSelectedIndex(-1);
    //Util.prt("ExportPanel.setJComboBoxToID id="+ID);
    for(int i=0; i<b.getItemCount(); i++) 
      if(((Export) b.getItemAt(i)).getID() == ID) {
          b.setSelectedIndex(i);
          //Util.prt("ExportPanel.setJComboBoxToID id="+ID+" found at "+i);
      }
  }  
  
  /** Given a database ID find the index into a JComboBox consistent with this panel
   *@param ID The row ID from the database
   *@return The index in the JComboBox with this ID
   */
  public static int getJComboBoxIndex(int ID) {
    if(Export.v == null) makeExports();
    for(int i=0; i<Export.v.size(); i++) if( ID == Export.v.get(i).getID()) return i;
    return -1;
  }
  /** Cause the main JComboBox to be reloaded*/
  public static void reload() {
    Export.v = null;
    makeExports();
  }
  /** return a vector with all of the Export
   * @return The vector with the export
   */
  public static ArrayList getExportVector() {
    if(Export.v == null) makeExports();
    return Export.v;
  }
  /** Get the item corresponding to database ID
   *@param ID the database Row ID
   *@return The Export row with this ID
   */
  public static Export getExportWithID(int ID) {
    if(Export.v == null) makeExports();
    int i=getJComboBoxIndex(ID);
    if(i >= 0) return (Export) Export.v.get(i);
    else return null;
  }
  
  // This routine should only need tweeking if key field is not same as table name
  private static void makeExports() {
    Export.makeExports();   
  }
  
  // No changes needed
  private void find() {
    if(export == null) return;
    if(initiating) return;
    Export l;
    if(export.getSelectedIndex() == -1) {
      if(export.getSelectedItem() == null) return;
      l = newOne();
    } 
    else {
      l = (Export) export.getSelectedItem();
    }
      
    if(l == null) return;
    String p = l.getExport();
    if(p == null) return;
    if(p.equals("")) {
      addUpdate.setEnabled(false);
      addUpdate.setText("Enter a Export!");
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
    { Util.SQLErrorPrint(E,"Export: SQL error getting Export="+p);
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
      Show.inFrame(new ExportPanel(), UC.XSIZE, UC.YSIZE);
    }
    catch(InstantiationException e) {}
  }
  
  
}
