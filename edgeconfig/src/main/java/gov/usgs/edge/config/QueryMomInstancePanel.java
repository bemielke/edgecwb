/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */

package gov.usgs.edge.config;
import gov.usgs.anss.db.DBConnectionThread;
import gov.usgs.anss.db.DBObject;
import gov.usgs.anss.util.ErrorTrack;
import gov.usgs.anss.util.FUtil;
import gov.usgs.anss.util.Show;
import gov.usgs.anss.util.UC;
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
 * JComboBox variable this variable is called "instance" in the initial form
 * The table name and key name should match and start lower case (instance).
 * The Class here will be that same name but with upper case at beginning(QueryMomInstance).
 <br> 1)  Rename the location JComboBox to the "key" (instance) value.  This should start Lower
 *      case.
 *<br>  2)  The table name should match this key name and be all lower case.
 *<br>  3)  Change all QueryMomInstance to ClassName of underlying data (case sensitive!)
<br>  4)  Change all instance key value (the JComboBox above)
 *<br>  5)  clearScreen should update swing components to new defaults
 *<br>  6)  chkForm should check all field for validity.  The errorTrack variable is
 *      used to track multiple errors.  In FUtil.chk* handle checks of the various
 *      variable types.  Date, double, and IP etc return the "canonical" form for 
 *      the variable.  These should be stored in a class variable so the latest one
 *      from "chkForm()" are available to the update writer.
 *<br>  7) newone() should be updated to create a new instance of the underlying data class.
 *<br>  8) oldone() get data from database and update all swing elements to correct values
 *<br>  9) addUpdateAction() add data base set*() operations updating all fields in the DB
 *<br> 10) QueryMomInstancePanel() constructor - good place to change backgrounds using UC.Look() any
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
public class QueryMomInstancePanel extends javax.swing.JPanel {
  //USER: set this for the upper case conversion of keys.
  public static String defaultEdgeProp = "#"+Util.fs+"home"+Util.fs+"vdl"+Util.fs+"edge_$INSTANCE.prop\n"+
    "emailTo=anss.alarm@usgs.gov\n"+
    "daysize=3001\n"+
    "extendsize=2000\n"+
    "ebqsize=20000\n"+
    "SMTPFrom=$ACCOUNT-$INSTANCE@usgs.gov\n";
  
  public static String defaultCrontab=  "# Start an RRPClient created by this EdgeMom instance\n" +
          "#chkCWB rrpclient $INSTANCE 30 -ring /data2/$SENDTO_$INSTANCE.ring -tag ? -log ?? -ext ? -ip ?.?.?.? -p ?\n" +
          "# Start a RRPServer to get data for insertion in this edgemom instance, create a RRP2Edge for each ring\n" +
          "#chkCWB rrpserver $INSTANCE 20 -config config/?.setup -s 500 -p ?\n";

  private final boolean keyUpperCase=false;    // set this true if all keys must be upper case
  //USER: Here are the local variables
  // NOTE : here define all variables general.  "ArrayList v" is used for main Comboboz
  int roleIndexStart=-1;
  String instanceStart="";
  int heapValue;
  ArrayList ethr;
  private String setToolTip(javax.swing.JTextField args, String className) {
    for(Object thr: ethr) {
      Edgethread t = (Edgethread) thr;
      if(className.equalsIgnoreCase(t.getEdgethread())) {
        args.setToolTipText("<html><p style=\"450px\">\n"+t.getHelp());
      }
    } 
    return "";
  }
  private void doInit() {
  // Good place to add UC.Looks()
    deleteItem.setVisible(true);
    reload.setVisible(false);
    idLab.setVisible(true);
    ID.setVisible(true);
    changeTo.setVisible(true);
    labChange.setVisible(true);
    UC.Look(this);                    // Set color background
    ethr = EdgethreadPanel.getEdgethreadVector();
    setToolTip(qsArgs,"EdgeQueryServer");
    setToolTip(cwbwsArgs,"CWBWaveServer");
    setToolTip(dlqsArgs,"DataLinkToQueryServer");
    setToolTip(fkArgs,"FKManager");
    setToolTip(qscArgs,"QuerySpanCollection");
    setToolTip(mscArgs,"MiniSeedCollection");
    setToolTip(fpArgs,"PickerManager");
    setToolTip(twsArgs,"TrinetServer");
    setToolTip(ssdArgs,"CWBSubspaceManager");

    
   

  }
  private void doAddUpdate() throws SQLException {
    // USER : Set all of the fields in data base.  obj.setInt("thefield", ivalue);
    obj.setString("description", description.getText());
    if(role.getSelectedIndex() >= 0) obj.setInt("roleid", ((RoleInstance) role.getSelectedItem()).getID());
    else obj.setInt("roleid", 0);
    if(failoverRole.getSelectedIndex() >= 0) obj.setInt("failoverid", ((RoleInstance) failoverRole.getSelectedItem()).getID());
    else obj.setInt("failoverid", 0);
    obj.setInt("failover", failedOver.isSelected()?1:0);
    obj.setInt("disabled", disabled.isSelected()?1:0);
    obj.setInt("heap", heapValue);
    obj.setString("args", args.getText());
    obj.setString("account", account.getText());
    obj.setInt("qsenable", qsEnable.isSelected()?1:0);
    obj.setInt("cwbwsenable", cwbwsEnable.isSelected()?1:0);
    obj.setInt("dlqsenable", dlqsEnable.isSelected()?1:0);
    obj.setInt("qscenable", qscEnable.isSelected()?1:0);
    obj.setInt("fkenable", fkEnable.isSelected()?1:0);
    obj.setInt("mscenable", mscEnable.isSelected()?1:0);
    obj.setInt("twsenable", twsEnable.isSelected()?1:0);
    obj.setInt("fpenable", pmEnable.isSelected()?1:0);
    obj.setInt("ssdenable", ssdEnable.isSelected()?1:0);
    obj.setString("qsargs", qsArgs.getText());
    obj.setString("cwbwsargs", cwbwsArgs.getText());
    obj.setString("dlqsargs", dlqsArgs.getText());
    obj.setString("qscargs", qscArgs.getText());
    obj.setString("fkargs", fkArgs.getText());
    obj.setString("mscargs", mscArgs.getText());
    obj.setString("twsargs", twsArgs.getText());
    obj.setString("fpargs", fpArgs.getText());
    obj.setString("ssdargs", ssdArgs.getText());

    Statement stmt = DBConnectionThread.getConnection(DBConnectionThread.getDBSchema()).createStatement();   // used for query
    stmt.executeUpdate("UPDATE edge.role SET hasdata=1 WHERE role.id="+((RoleInstance) role.getSelectedItem()).getID());
    
  }
  private void doOldOne() throws SQLException {
      //USER: Here set all of the form fields to data from the DBObject
      //      instance.setText(obj.getString("QueryMomInstance"));
      // Example : description.setText(obj.getString("description"));
    description.setText(obj.getString("description"));
    args.setText(obj.getString("args"));
    heap.setText(obj.getInt("heap")+"");
    account.setText(obj.getString("account"));
    role.setSelectedIndex(RoleInstancePanel.getJComboBoxIndex(obj.getInt("roleid")));
    failoverRole.setSelectedIndex(RoleInstancePanel.getJComboBoxIndex(obj.getInt("failoverid")));
    disabled.setSelected(obj.getInt("disabled") != 0);
    failedOver.setSelected(obj.getInt("failover") != 0);
    roleIndexStart = role.getSelectedIndex();
    qsEnable.setSelected(obj.getInt("qsenable") != 0);
    cwbwsEnable.setSelected(obj.getInt("cwbwsenable") != 0);
    dlqsEnable.setSelected(obj.getInt("dlqsenable") != 0);
    qscEnable.setSelected(obj.getInt("qscenable") != 0);
    fkEnable.setSelected(obj.getInt("fkenable") != 0);
    mscEnable.setSelected(obj.getInt("mscenable") != 0);
    twsEnable.setSelected(obj.getInt("twsenable") != 0);
    pmEnable.setSelected(obj.getInt("fpenable") != 0);
    ssdEnable.setSelected(obj.getInt("ssdenable") != 0);
    qsArgs.setText(obj.getString("qsargs"));
    cwbwsArgs.setText(obj.getString("cwbwsargs"));
    dlqsArgs.setText(obj.getString("dlqsargs"));
    qscArgs.setText(obj.getString("qscargs"));
    fkArgs.setText(obj.getString("fkargs"));
    mscArgs.setText(obj.getString("mscargs"));
    twsArgs.setText(obj.getString("twsargs"));
    fpArgs.setText(obj.getString("fpargs"));
    ssdArgs.setText(obj.getString("ssdargs"));
  }
  

  // This routine must validate all fields on the form.  The err (ErrorTrack) variable
  // is used by the FUtil to verify fields. Use FUTil or custom code here for verifications
  private boolean chkForm() {
    // Do not change
   err.reset();
   UC.Look(error);
   
   //USER: Your error checking code goes here setting the local variables to valid settings
   // note : Use FUtil.chk* to check dates, timestamps, IP addresses, etc and put them in
   // canonical form.  store in a class variable.  For instance :
   //   sdate = FUtil.chkDate(dateTextField, err);
   // Any errors found should err.set(true);  and err.append("error message");
    if(role.getSelectedIndex() != roleIndexStart && roleIndexStart>=0) {
      if(changeTo.getText().equals("") ||
              changeTo.getText().equalsIgnoreCase(((QueryMomInstance)(instance.getSelectedItem())).getQueryMomInstance())) {
        err.set();
        err.appendText("Remember : When changing assigned role the instance name must change!");
      }
    }
    heapValue = FUtil.chkInt(heap, err, 0, 30000);
    // No CHANGES : If we found an error, color up error box
    if(err.isSet()) {
      error.setText(err.getText());
      error.setBackground(UC.red);
      addUpdate.setEnabled(false);
    } else addUpdate.setEnabled(true);
    return err.isSet();
  }
  
  // This routine must set the form to initial state.  It does not update the JCombobox
  private void clearScreen() {
    
    // Do not change
    ID.setText("");
    UC.Look(error);
    UC.Look(bottomStuff);
    UC.Look(threadPanel);
    error.setText("");
    addUpdate.setEnabled(false);
    addUpdate.setText("Enter a EdgeMomInstance");
    deleteItem.setEnabled(false);
    changeTo.setText("");
    roleIndexStart=-2;
    
    //USER: Clear all fields for this form, this sets "defaults" for new screen
    // description.setText("");
    args.setText("");
    description.setText("");
    role.setSelectedIndex(-1);
    failoverRole.setSelectedIndex(-1);
    disabled.setSelected(false);
    failedOver.setSelected(false);
    heap.setText("");
    account.setText("vdl");
    qsEnable.setSelected(false);
    cwbwsEnable.setSelected(false);
    dlqsEnable.setSelected(false);
    qscEnable.setSelected(false);
    fkEnable.setSelected(false);
    mscEnable.setSelected(false);
    twsEnable.setSelected(false);
    pmEnable.setSelected(false);
    ssdEnable.setSelected(false);
    qsArgs.setText("");
    cwbwsArgs.setText("");
    dlqsArgs.setText("");
    qscArgs.setText("");
    fkArgs.setText("");
    mscArgs.setText("");
    twsArgs.setText("");
    fpArgs.setText("");
    ssdArgs.setText("");
  }


  /**********************  NO USER MODIFICATION BELOW HERE EXCEPT FOR ACTIONS **************/
  DBObject obj;
  ErrorTrack err=new ErrorTrack();
  boolean initiating=false;

  
  private QueryMomInstance newOne() {
    
    return new QueryMomInstance(0, ((String) instance.getSelectedItem()).toUpperCase() //, more
            ,"",0,"",0,"", 0, 0, 0,0, "",0, "",0, "",0, "",0, "",0, "",0, "",0, "",0, ""
       );
  }
  
  private void oldOne(String p) throws SQLException {
    obj = new DBObject(DBConnectionThread.getThread(DBConnectionThread.getDBSchema()), 
            DBConnectionThread.getDBSchema(),       // Schema, if this is not right in URL load, it must be explicit here
            "querymominstance","instance",p);                     // table name and field name are usually the same

    if(obj.isNew()) {
      //Util.prt("object is new"+p);
    }
    else {
      //USER: Here set all of the form fields to data from the DBObject
      //      instance.setText(obj.getString("QueryMomInstance"));
      // Example : description.setText(obj.getString("description"));
      doOldOne();
        
        
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
    labMain = new javax.swing.JLabel();
    instance = getJComboBox();
    error = new javax.swing.JTextField();
    bottomStuff = new javax.swing.JPanel();
    addUpdate = new javax.swing.JButton();
    labChange = new javax.swing.JLabel();
    changeTo = new javax.swing.JTextField();
    deleteItem = new javax.swing.JButton();
    reload = new javax.swing.JButton();
    idLab = new javax.swing.JLabel();
    ID = new javax.swing.JTextField();
    labDesc = new javax.swing.JLabel();
    labAcct = new javax.swing.JLabel();
    labArgs = new javax.swing.JLabel();
    description = new javax.swing.JTextField();
    account = new javax.swing.JTextField();
    args = new javax.swing.JTextField();
    topPanel = new javax.swing.JPanel();
    labHeap = new javax.swing.JLabel();
    heap = new javax.swing.JTextField();
    labRole = new javax.swing.JLabel();
    role = gov.usgs.edge.config.RoleInstancePanel.getJComboBox();
    labFailRole = new javax.swing.JLabel();
    failoverRole = gov.usgs.edge.config.RoleInstancePanel.getJComboBox();
    failedOver = new javax.swing.JRadioButton();
    disabled = new javax.swing.JRadioButton();
    threadPanel = new javax.swing.JPanel();
    qsEnable = new javax.swing.JRadioButton();
    labqs = new javax.swing.JLabel();
    qsArgs = new javax.swing.JTextField();
    cwbwsArgs = new javax.swing.JTextField();
    dlqsArgs = new javax.swing.JTextField();
    qscArgs = new javax.swing.JTextField();
    fkArgs = new javax.swing.JTextField();
    mscArgs = new javax.swing.JTextField();
    twsArgs = new javax.swing.JTextField();
    fpArgs = new javax.swing.JTextField();
    ssdArgs = new javax.swing.JTextField();
    splitterEnable = new javax.swing.JRadioButton();
    splitterArgs = new javax.swing.JTextField();
    help = new javax.swing.JTextArea();
    cwbwsEnable = new javax.swing.JRadioButton();
    dlqsEnable = new javax.swing.JRadioButton();
    qscEnable = new javax.swing.JRadioButton();
    fkEnable = new javax.swing.JRadioButton();
    mscEnable = new javax.swing.JRadioButton();
    twsEnable = new javax.swing.JRadioButton();
    pmEnable = new javax.swing.JRadioButton();
    ssdEnable = new javax.swing.JRadioButton();
    labcwbws = new javax.swing.JLabel();
    labdlqs = new javax.swing.JLabel();
    labqsc = new javax.swing.JLabel();
    labfk = new javax.swing.JLabel();
    labmsc = new javax.swing.JLabel();
    labtws = new javax.swing.JLabel();
    labfp = new javax.swing.JLabel();
    labssd = new javax.swing.JLabel();
    labDataSplit = new javax.swing.JLabel();

    setLayout(new java.awt.GridBagLayout());

    labMain.setText("Instance (nn#n):");
    labMain.setToolTipText("This button commits either a new item or the changes for an updating item.  Its label with change to reflect that.");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    add(labMain, gridBagConstraints);

    instance.setEditable(true);
    instance.setToolTipText("Select an item to edit in the JComboBox or type in the name of an item.   If the typed name does not match an existing item, an new item is assumed.");
    instance.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        instanceActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(instance, gridBagConstraints);

    error.setColumns(40);
    error.setEditable(false);
    error.setMinimumSize(new java.awt.Dimension(488, 22));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(error, gridBagConstraints);

    bottomStuff.setMinimumSize(new java.awt.Dimension(550, 68));
    bottomStuff.setPreferredSize(new java.awt.Dimension(550, 68));

    addUpdate.setText("Add/Update");
    addUpdate.setToolTipText("Add a new Item or update and edited item.");
    addUpdate.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        addUpdateActionPerformed(evt);
      }
    });
    bottomStuff.add(addUpdate);

    labChange.setFont(new java.awt.Font("Monospaced", 0, 10)); // NOI18N
    labChange.setText("Change To:");
    bottomStuff.add(labChange);

    changeTo.setColumns(10);
    changeTo.setFont(new java.awt.Font("Monospaced", 0, 10)); // NOI18N
    changeTo.setToolTipText("To change the name of this item, put the new name here.  ");
    bottomStuff.add(changeTo);

    deleteItem.setText("Delete Item");
    deleteItem.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        deleteItemActionPerformed(evt);
      }
    });
    bottomStuff.add(deleteItem);

    reload.setText("Reload");
    reload.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        reloadActionPerformed(evt);
      }
    });
    bottomStuff.add(reload);

    idLab.setText("ID :");
    bottomStuff.add(idLab);

    ID.setBackground(new java.awt.Color(204, 204, 204));
    ID.setColumns(5);
    ID.setEditable(false);
    ID.setToolTipText("This is the ID of the displayed item in the underlying database.  Of much use to programmers, but not many others.");
    bottomStuff.add(ID);

    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 18;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(bottomStuff, gridBagConstraints);

    labDesc.setText("Description:");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 3;
    add(labDesc, gridBagConstraints);

    labAcct.setText("Account:");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 5;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    add(labAcct, gridBagConstraints);

    labArgs.setText("Arguments:");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 6;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    add(labArgs, gridBagConstraints);

    description.setColumns(80);
    description.setToolTipText("<html>\nEnter some descriptive texts about this instance - mostly what data comes in, or what this instance does.\n</html>");
    description.setFocusTraversalKeysEnabled(false);
    description.setMinimumSize(new java.awt.Dimension(550, 28));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 3;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(description, gridBagConstraints);

    account.setColumns(10);
    account.setText("vdl");
    account.setToolTipText("Set the account this EdgeMom is to run in.  Normally this  is 'vdl'.");
    account.setMinimumSize(new java.awt.Dimension(134, 28));
    account.addFocusListener(new java.awt.event.FocusAdapter() {
      public void focusLost(java.awt.event.FocusEvent evt) {
        accountFocusLost(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 5;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(account, gridBagConstraints);

    args.setColumns(80);
    args.setToolTipText("<html>\nexample : bash chkCWB edgemom N#I 100 [-test][-f filename][-dbg][-dbgblk][.....]\n<br>\n<p>\n<pre>\nThese switches are processed by the chkCWB script used to start processes in the crontab:\nSwitch        Description\n-max         This process should use the java switches -XX:NewSize and -XX:MaxNewSize to cause more frequenc GC runs\n-test          This switch tells the chkCWB to use the Jars files in ~vdl/Jars instead of ~vdl/bin to start this process.  Used for testing new code.\n-nice          If present, then this thread is started with a nice to lower its processing priority\n\nThese switches go to the EdgeMom thread.  Normally none of them are needed.  The -i will automatically be set by chkCWB script.\nTag   arg          Description\n-max                 If present, then use the max method of heap control (normally forcing GC cycles ever 200 mB or so)\n-f        filename file name of setup file def=edgemom.setup\n-nosnw             Turnoff any output to the SNW server via SNWSender from any thread within this EdgeMom\n-stalesec nn       Set the stale setting for closing files to this number of seconds (def=43200)\n-logname  file     Set the log file name to something other thant 'edgemom'\n-i        nn#nn    Set the instance to this value, this overrides edge.prop property Node!\n-ebq    nnnn       Set size of the EdgeBLock Queue default=\"+ebqsize);\n-logname  name     Use name as the bases for log file (def=edgemom)\n-stalesec nnnn     Number of seconds before a file is considered stale \n-nozeroer          If present to no zero ahead, use this only on filesystems that return zero on unwritten blocks in a file\n-zeroahead         The zeroer is to try to be this many megabytes ahead of the last data block in megabytes (def=1)\n\nDebugging related switches:\n-dbgblk            Turn on logging for each data block written  \n -dbgebq            Turn on logging for each data block to replication  \n-dbgrtms           Turn on debugging in RawToMiniSeed\n-dbgifr            Turn on debugging in IndexFileReplicator\n-eqbsave filename  Turn on saving of EdgeBlockQueue to journal filename\n-console           Set use console flag on - cause console output\n -notimeout         Turn off thread time outs - good for useing debugger\n-traceSteim        Turn on logging of Steim2.traceBackErrors()\n-notimeout         Set the EdgeThreadTimeout to no timeout mode\n</pre>\n</html>");
    args.setMinimumSize(new java.awt.Dimension(550, 28));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 6;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(args, gridBagConstraints);

    topPanel.setMinimumSize(new java.awt.Dimension(600, 80));

    labHeap.setText("Heap(mB):");
    topPanel.add(labHeap);

    heap.setColumns(5);
    heap.setToolTipText("Heap space to assign to this instance of EdgeMom in megabytes (mB).");
    topPanel.add(heap);

    labRole.setText("Normal Role: ");
    topPanel.add(labRole);

    role.setToolTipText("Select the role on which this instance is normally run.  The instance node number should agree with the cpu node number assign this role.");
    role.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        roleActionPerformed(evt);
      }
    });
    topPanel.add(role);

    labFailRole.setText("Failover Role:");
    topPanel.add(labFailRole);

    failoverRole.setToolTipText("Select the Role that would normally be the role assigned on a failover of the normal role.");
    failoverRole.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        failoverRoleActionPerformed(evt);
      }
    });
    topPanel.add(failoverRole);

    failedOver.setText("Fail Over this Instance");
    failedOver.setToolTipText("If selected, the instance is run on its failover role and removed from its normal role.");
    failedOver.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        failedOverActionPerformed(evt);
      }
    });
    topPanel.add(failedOver);

    disabled.setText("Disabled");
    disabled.setToolTipText("If selected, this instance is not run on any role.");
    topPanel.add(disabled);

    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 2;
    add(topPanel, gridBagConstraints);

    threadPanel.setMinimumSize(new java.awt.Dimension(626, 430));
    threadPanel.setPreferredSize(new java.awt.Dimension(600, 400));
    threadPanel.setLayout(new java.awt.GridBagLayout());

    qsEnable.setFont(new java.awt.Font("Monospaced", 0, 10)); // NOI18N
    qsEnable.setText("QueryServer");
    qsEnable.setToolTipText("<html>\n<pre>\nQueryServer provide MiniSeed data from the Edge/CWB to clients like CWBQuery.\n</pre>\n</html>");
    qsEnable.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        qsEnableActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
    threadPanel.add(qsEnable, gridBagConstraints);

    labqs.setFont(new java.awt.Font("Monospaced", 0, 10)); // NOI18N
    labqs.setText("Args:");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 1;
    threadPanel.add(labqs, gridBagConstraints);

    qsArgs.setColumns(40);
    qsArgs.setFont(new java.awt.Font("Monospaced", 0, 10)); // NOI18N
    qsArgs.setMaximumSize(new java.awt.Dimension(450, 28));
    qsArgs.setMinimumSize(new java.awt.Dimension(450, 28));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 2;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
    threadPanel.add(qsArgs, gridBagConstraints);

    cwbwsArgs.setColumns(40);
    cwbwsArgs.setFont(new java.awt.Font("Monospaced", 0, 10)); // NOI18N
    cwbwsArgs.setMaximumSize(new java.awt.Dimension(450, 28));
    cwbwsArgs.setMinimumSize(new java.awt.Dimension(450, 28));
    cwbwsArgs.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        cwbwsArgsActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 2;
    gridBagConstraints.gridy = 3;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
    threadPanel.add(cwbwsArgs, gridBagConstraints);

    dlqsArgs.setColumns(40);
    dlqsArgs.setFont(new java.awt.Font("Monospaced", 0, 10)); // NOI18N
    dlqsArgs.setMaximumSize(new java.awt.Dimension(450, 28));
    dlqsArgs.setMinimumSize(new java.awt.Dimension(450, 28));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 2;
    gridBagConstraints.gridy = 4;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
    threadPanel.add(dlqsArgs, gridBagConstraints);

    qscArgs.setColumns(40);
    qscArgs.setFont(new java.awt.Font("Monospaced", 0, 10)); // NOI18N
    qscArgs.setMaximumSize(new java.awt.Dimension(450, 28));
    qscArgs.setMinimumSize(new java.awt.Dimension(450, 28));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 2;
    gridBagConstraints.gridy = 6;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
    threadPanel.add(qscArgs, gridBagConstraints);

    fkArgs.setColumns(40);
    fkArgs.setFont(new java.awt.Font("Monospaced", 0, 10)); // NOI18N
    fkArgs.setMaximumSize(new java.awt.Dimension(450, 28));
    fkArgs.setMinimumSize(new java.awt.Dimension(450, 28));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 2;
    gridBagConstraints.gridy = 13;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
    threadPanel.add(fkArgs, gridBagConstraints);

    mscArgs.setColumns(40);
    mscArgs.setFont(new java.awt.Font("Monospaced", 0, 10)); // NOI18N
    mscArgs.setMaximumSize(new java.awt.Dimension(450, 28));
    mscArgs.setMinimumSize(new java.awt.Dimension(450, 28));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 2;
    gridBagConstraints.gridy = 8;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
    threadPanel.add(mscArgs, gridBagConstraints);

    twsArgs.setColumns(40);
    twsArgs.setFont(new java.awt.Font("Monospaced", 0, 10)); // NOI18N
    twsArgs.setMaximumSize(new java.awt.Dimension(450, 28));
    twsArgs.setMinimumSize(new java.awt.Dimension(450, 28));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 2;
    gridBagConstraints.gridy = 9;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
    threadPanel.add(twsArgs, gridBagConstraints);

    fpArgs.setColumns(40);
    fpArgs.setFont(new java.awt.Font("Monospaced", 0, 10)); // NOI18N
    fpArgs.setMaximumSize(new java.awt.Dimension(450, 28));
    fpArgs.setMinimumSize(new java.awt.Dimension(450, 28));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 2;
    gridBagConstraints.gridy = 12;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
    threadPanel.add(fpArgs, gridBagConstraints);

    ssdArgs.setColumns(40);
    ssdArgs.setFont(new java.awt.Font("Monospaced", 0, 10)); // NOI18N
    ssdArgs.setToolTipText("");
    ssdArgs.setMaximumSize(new java.awt.Dimension(450, 28));
    ssdArgs.setMinimumSize(new java.awt.Dimension(450, 28));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 2;
    gridBagConstraints.gridy = 15;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
    threadPanel.add(ssdArgs, gridBagConstraints);

    splitterEnable.setFont(new java.awt.Font("Monospaced", 0, 10)); // NOI18N
    splitterEnable.setText("DataLinkSplitter");
    splitterEnable.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        splitterEnableActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 16;
    threadPanel.add(splitterEnable, gridBagConstraints);

    splitterArgs.setColumns(40);
    splitterArgs.setFont(new java.awt.Font("Monospaced", 0, 10)); // NOI18N
    splitterArgs.setMaximumSize(new java.awt.Dimension(450, 2147483647));
    splitterArgs.setMinimumSize(new java.awt.Dimension(450, 28));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 2;
    gridBagConstraints.gridy = 16;
    threadPanel.add(splitterArgs, gridBagConstraints);

    help.setEditable(false);
    help.setColumns(70);
    help.setFont(new java.awt.Font("Monospaced", 0, 10)); // NOI18N
    help.setRows(16);
    help.setText("QueryServer is required for all configurations and with it alone the \n         QueryMom will provide MIniSEED data to clients like CWBQuery.\n\nEarthworm protocol - enable a CWBWaveServer to handle these requests.\n\nMemory Cache - Add a DataLink2QM and a QuerySpanCollection to provide \n          in-the-clear data to CWBWaveServer or for processing (FK, FP).\n\nTrinet protocol - enable a TrinetWaveServer to handle the requests.\n\nMiniSeed Memory cache - add the MiniSeedCollection to make miniSEED \n          data cache for a TrinetWaveServer.");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 2;
    gridBagConstraints.gridy = 0;
    threadPanel.add(help, gridBagConstraints);

    cwbwsEnable.setFont(new java.awt.Font("Lucida Grande", 0, 10)); // NOI18N
    cwbwsEnable.setText("CWBWaveServer");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 3;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
    threadPanel.add(cwbwsEnable, gridBagConstraints);

    dlqsEnable.setFont(new java.awt.Font("Lucida Grande", 0, 10)); // NOI18N
    dlqsEnable.setText("DataLink2QM");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 4;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
    threadPanel.add(dlqsEnable, gridBagConstraints);

    qscEnable.setFont(new java.awt.Font("Lucida Grande", 0, 10)); // NOI18N
    qscEnable.setText("QuerySpanCollection");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 6;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
    threadPanel.add(qscEnable, gridBagConstraints);

    fkEnable.setFont(new java.awt.Font("Lucida Grande", 0, 10)); // NOI18N
    fkEnable.setText("FKManager");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 13;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
    threadPanel.add(fkEnable, gridBagConstraints);

    mscEnable.setFont(new java.awt.Font("Lucida Grande", 0, 10)); // NOI18N
    mscEnable.setText("MiniSeedCollection");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 8;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
    threadPanel.add(mscEnable, gridBagConstraints);

    twsEnable.setFont(new java.awt.Font("Lucida Grande", 0, 10)); // NOI18N
    twsEnable.setText("TrinetWaveServer");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 9;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
    threadPanel.add(twsEnable, gridBagConstraints);

    pmEnable.setFont(new java.awt.Font("Lucida Grande", 0, 10)); // NOI18N
    pmEnable.setText("PickerManager");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 12;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
    threadPanel.add(pmEnable, gridBagConstraints);

    ssdEnable.setFont(new java.awt.Font("Lucida Grande", 0, 10)); // NOI18N
    ssdEnable.setText("Subspace");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 15;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
    threadPanel.add(ssdEnable, gridBagConstraints);

    labcwbws.setFont(new java.awt.Font("Monospaced", 0, 10)); // NOI18N
    labcwbws.setText("Args:");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 3;
    threadPanel.add(labcwbws, gridBagConstraints);

    labdlqs.setFont(new java.awt.Font("Monospaced", 0, 10)); // NOI18N
    labdlqs.setText("Args:");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 4;
    threadPanel.add(labdlqs, gridBagConstraints);

    labqsc.setFont(new java.awt.Font("Monospaced", 0, 10)); // NOI18N
    labqsc.setText("Args:");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 6;
    threadPanel.add(labqsc, gridBagConstraints);

    labfk.setFont(new java.awt.Font("Monospaced", 0, 10)); // NOI18N
    labfk.setText("Args:");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 13;
    threadPanel.add(labfk, gridBagConstraints);

    labmsc.setFont(new java.awt.Font("Monospaced", 0, 10)); // NOI18N
    labmsc.setText("Args:");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 8;
    threadPanel.add(labmsc, gridBagConstraints);

    labtws.setFont(new java.awt.Font("Monospaced", 0, 10)); // NOI18N
    labtws.setText("Args:");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 9;
    threadPanel.add(labtws, gridBagConstraints);

    labfp.setFont(new java.awt.Font("Monospaced", 0, 10)); // NOI18N
    labfp.setText("Args:");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 12;
    threadPanel.add(labfp, gridBagConstraints);

    labssd.setFont(new java.awt.Font("Monospaced", 0, 10)); // NOI18N
    labssd.setText("Args:");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 15;
    threadPanel.add(labssd, gridBagConstraints);

    labDataSplit.setFont(new java.awt.Font("Monospaced", 0, 10)); // NOI18N
    labDataSplit.setText("Args:");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 16;
    threadPanel.add(labDataSplit, gridBagConstraints);

    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 12;
    add(threadPanel, gridBagConstraints);
  }// </editor-fold>//GEN-END:initComponents

  private void addUpdateActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_addUpdateActionPerformed
    // Add your handling code here:
    
    // Do not change
    if(initiating) return;
    if(chkForm()) return;
    try {
      String p = instance.getSelectedItem().toString();
      if(p.contains("-")) p = p.substring(0, p.indexOf("-")).trim();
      if(keyUpperCase) p = p.toUpperCase();                  // USER : drop this to allow Mixed case
      if(!obj.isNew()) obj.refreshRecord();        // Refresh the result set so the sets will not fail on stale one
      if(!changeTo.getText().equals("")) p = changeTo.getText();
      obj.setString("instance",p);
      
      // USER : Set all of the fields in data base.  obj.setInt("thefield", ivalue);
      doAddUpdate();
      
      // Do not change
      obj.updateRecord();
      QueryMomInstance.v=null;       // force reload of combo box
      getJComboBox(instance);
      clearScreen();
      instance.setSelectedIndex(-1);
    }  catch  (SQLException  E)
    { Util.SQLErrorPrint(E,"instance: update failed partno="+instance.getSelectedItem());
      error.setText("Could Not Update File!");
      error.setBackground(UC.red);
      addUpdate.setEnabled(true);
    }      
  }//GEN-LAST:event_addUpdateActionPerformed

  private void instanceActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_instanceActionPerformed
    // Add your handling code here:
   find();
 
 
  }//GEN-LAST:event_instanceActionPerformed

private void deleteItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_deleteItemActionPerformed
  try {
    obj.deleteRecord();
    instance.removeItem(instance.getSelectedItem());
    clearScreen();
  }
  catch(SQLException e) {
    Util.prta("Delete record failed SQL error="+e);
  }
}//GEN-LAST:event_deleteItemActionPerformed

private void reloadActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_reloadActionPerformed
  // put whatever jcombobox need reloading here
  //Class.getJComboBox(boxVariable);

  clearScreen();
}//GEN-LAST:event_reloadActionPerformed

  private void roleActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_roleActionPerformed
    if(roleIndexStart >= 0 && role.getSelectedIndex() >= 0) chkForm();
  }//GEN-LAST:event_roleActionPerformed

  private void failoverRoleActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_failoverRoleActionPerformed
    chkForm();
  }//GEN-LAST:event_failoverRoleActionPerformed

  private void failedOverActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_failedOverActionPerformed
    chkForm();
  }//GEN-LAST:event_failedOverActionPerformed

  private void accountFocusLost(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_accountFocusLost
    chkForm();
  }//GEN-LAST:event_accountFocusLost

  private void qsEnableActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_qsEnableActionPerformed
    // TODO add your handling code here:
  }//GEN-LAST:event_qsEnableActionPerformed

  private void cwbwsArgsActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_cwbwsArgsActionPerformed
    // TODO add your handling code here:
  }//GEN-LAST:event_cwbwsArgsActionPerformed

  private void splitterEnableActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_splitterEnableActionPerformed
    // TODO add your handling code here:
  }//GEN-LAST:event_splitterEnableActionPerformed
  
  
  // Variables declaration - do not modify//GEN-BEGIN:variables
  private javax.swing.JTextField ID;
  private javax.swing.JTextField account;
  private javax.swing.JButton addUpdate;
  private javax.swing.JTextField args;
  private javax.swing.JPanel bottomStuff;
  private javax.swing.JTextField changeTo;
  private javax.swing.JTextField cwbwsArgs;
  private javax.swing.JRadioButton cwbwsEnable;
  private javax.swing.JButton deleteItem;
  private javax.swing.JTextField description;
  private javax.swing.JRadioButton disabled;
  private javax.swing.JTextField dlqsArgs;
  private javax.swing.JRadioButton dlqsEnable;
  private javax.swing.JTextField error;
  private javax.swing.JRadioButton failedOver;
  private javax.swing.JComboBox failoverRole;
  private javax.swing.JTextField fkArgs;
  private javax.swing.JRadioButton fkEnable;
  private javax.swing.JTextField fpArgs;
  private java.awt.GridBagLayout gridBagLayout1;
  private javax.swing.JTextField heap;
  private javax.swing.JTextArea help;
  private javax.swing.JLabel idLab;
  private javax.swing.JComboBox instance;
  private javax.swing.JLabel labAcct;
  private javax.swing.JLabel labArgs;
  private javax.swing.JLabel labChange;
  private javax.swing.JLabel labDataSplit;
  private javax.swing.JLabel labDesc;
  private javax.swing.JLabel labFailRole;
  private javax.swing.JLabel labHeap;
  private javax.swing.JLabel labMain;
  private javax.swing.JLabel labRole;
  private javax.swing.JLabel labcwbws;
  private javax.swing.JLabel labdlqs;
  private javax.swing.JLabel labfk;
  private javax.swing.JLabel labfp;
  private javax.swing.JLabel labmsc;
  private javax.swing.JLabel labqs;
  private javax.swing.JLabel labqsc;
  private javax.swing.JLabel labssd;
  private javax.swing.JLabel labtws;
  private javax.swing.JTextField mscArgs;
  private javax.swing.JRadioButton mscEnable;
  private javax.swing.JRadioButton pmEnable;
  private javax.swing.JTextField qsArgs;
  private javax.swing.JRadioButton qsEnable;
  private javax.swing.JTextField qscArgs;
  private javax.swing.JRadioButton qscEnable;
  private javax.swing.JButton reload;
  private javax.swing.JComboBox role;
  private javax.swing.JTextField splitterArgs;
  private javax.swing.JRadioButton splitterEnable;
  private javax.swing.JTextField ssdArgs;
  private javax.swing.JRadioButton ssdEnable;
  private javax.swing.JPanel threadPanel;
  private javax.swing.JPanel topPanel;
  private javax.swing.JTextField twsArgs;
  private javax.swing.JRadioButton twsEnable;
  // End of variables declaration//GEN-END:variables
  /** Creates new form EdgeMomInstancePanel */
  public QueryMomInstancePanel() {
    initiating=true;
    initComponents();
    getJComboBox(instance);                // set up the key JComboBox
    instance.setSelectedIndex(-1);    // Set selected type
    initiating=false;
   
    // USER:This is a good place for UC.Look(components) to get rid of the ugly Grey
    doInit();
    UC.Look(topPanel);
    UC.Look(cwbwsEnable);
    UC.Look(disabled);
    UC.Look(dlqsEnable);
    UC.Look(failedOver);
    UC.Look(fkEnable);
    UC.Look(mscEnable);
    UC.Look(qscEnable);
    UC.Look(splitterEnable);
    UC.Look(ssdEnable);
    UC.Look(twsEnable);
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
    makeEdgeMomInstances();
    for (QueryMomInstance v1 : QueryMomInstance.v) {
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
    //Util.prt("QueryMomInstancePanel.setJComboBoxToID id="+ID);
    for(int i=0; i<b.getItemCount(); i++) 
      if(((QueryMomInstance) b.getItemAt(i)).getID() == ID) {
          b.setSelectedIndex(i);
          //Util.prt("QueryMomInstancePanel.setJComboBoxToID id="+ID+" found at "+i);
      }
  }  
  
  /** Given a database ID find the index into a JComboBox consistent with this panel
   *@param ID The row ID from the database
   *@return The index in the JComboBox with this ID
   */
  public static int getJComboBoxIndex(int ID) {
    if(QueryMomInstance.v == null) makeEdgeMomInstances();
    for(int i=0; i<QueryMomInstance.v.size(); i++) if( ID ==  QueryMomInstance.v.get(i).getID()) return i;
    return -1;
  }
  /** Cause the main JComboBox to be reloaded. */
  public static void reload() {
    QueryMomInstance.v = null;
    makeEdgeMomInstances();
  }
  /** return a ArrayList with all of the QueryMomInstance
   * @return The ArrayList with the instance
   */
  public static ArrayList<QueryMomInstance> getEdgeMomInstanceVector() {
    if(QueryMomInstance.v == null) makeEdgeMomInstances();
    return QueryMomInstance.v;
  }    
	/** return a ArrayList with all of the QueryMomInstance
   * @return The ArrayList with the instance
   */
  public static ArrayList<QueryMomInstance> getEdgeMomInstanceArrayList() {
    if(QueryMomInstance.v == null) makeEdgeMomInstances();
    return QueryMomInstance.v;
  }
  /** Get the item corresponding to database ID
   *@param ID the database Row ID
   *@return The QueryMomInstance row with this ID
   */
  public static QueryMomInstance getEdgeMomInstanceWithID(int ID) {
    if(QueryMomInstance.v == null) makeEdgeMomInstances();
    int i=getJComboBoxIndex(ID);
    if(i >= 0) return QueryMomInstance.v.get(i);
    else return null;
  }
  
  // This routine should only need tweeking if key field is not same as table name
  private static void makeEdgeMomInstances() {
    QueryMomInstance.makeEdgeMomInstances();
   
  }
  
  // No changes needed
  private void find() {
    if(instance == null) return;
    if(initiating) return;
    QueryMomInstance l;
    if(instance.getSelectedIndex() == -1) {
      if(instance.getSelectedItem() == null) return;
      l = newOne();
    } 
    else {
      l = (QueryMomInstance) instance.getSelectedItem();
    }
      
    if(l == null) return;
    String p = l.getQueryMomInstance();
    if(p == null) return;
    if(p.equals("")) {
      addUpdate.setEnabled(false);
      deleteItem.setEnabled(false);
      addUpdate.setText("Enter a EdgeMomInstance!");
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
          deleteItem.setEnabled(false);
      }
      else {
        addUpdate.setText("Update "+p);
        addUpdate.setEnabled(true);
        addUpdate.setEnabled(true);
        ID.setText(""+obj.getInt("ID"));    // Info only, show ID
      }
    }  
    // This is only thrown if something happens in oldone() new DBOject
    // Other than it being a new record.
    catch  (SQLException  E)                                 // 
    { Util.SQLErrorPrint(E,"EdgeMomInstance: SQL error getting EdgeMomInstance="+p);
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
          {Util.prt("COuld not connect to DB "+jcjbl); System.exit(1);}
      Show.inFrame(new QueryMomInstancePanel(), UC.XSIZE, UC.YSIZE);
    }
    catch(InstantiationException e) {}


  }
}
