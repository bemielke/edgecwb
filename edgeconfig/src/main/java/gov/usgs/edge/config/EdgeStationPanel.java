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
import gov.usgs.anss.gui.FlagPanel2;
import gov.usgs.anss.util.ErrorTrack;
import gov.usgs.anss.util.FUtil;
import gov.usgs.anss.util.Show;
import gov.usgs.anss.util.Util;
import java.awt.Color;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import javax.swing.JComboBox;
/**
 *
 * requirements : 
 * The key for the file must be the name of the table and of the
 * JComboBox variable this variable is called "gsnstation" in the initial form
 * The table name and key name should match and start lower case (gsnstation).
 * The Class here will be that same name but with upper case at beginning(EdgeStation).
 <br> 1)  Rename the location JComboBox to the "key" (gsnstation) value.  This should start Lower
 *      case.
 *<br>  2)  The table name should match this key name and be all lower case.
 *<br>  3)  Change all EdgeStation to ClassName of underlying data (case sensitive!)
<br>  4)  Change all gsnstation key value (the JComboBox above)
 *<br>  5)  clearScreen should update swing components to new defaults
 *<br>  6)  chkForm should check all field for validity.  The errorTrack variable is
 *      used to track multiple errors.  In FUtil.chk* handle checks of the various
 *      variable types.  Date, double, and IP etc return the "canonical" form for 
 *      the variable.  These should be stored in a class variable so the latest one
 *      from "chkForm()" are available to the update writer.
 *<br>  7) newone() should be updated to create a new instance of the underlying data class.
 *<br>  8) oldone() get data from database and update all swing elements to correct values
 *<br>  9) addUpdateAction() add data base set*() operations updating all fields in the DB
 *<br> 10) EdgeStationPanel() constructor - good place to change backgrounds using UC.Look() any
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
public class EdgeStationPanel extends javax.swing.JPanel {
  
  // NOTE : here define all variables general.  "Vector v" is used for main Comboboz
  static ArrayList<EdgeStation> v;             // Vector containing objects of this EdgeStation Type
  DBObject obj;
  ErrorTrack err=new ErrorTrack();
  boolean initiating=false;
  Timestamp ts = new Timestamp(86400000);
  String strlonghaulIP = null;
  String strrequestIP =null;
  int intlonghaulport;
  int intrequestport;
  Timestamp tsdisableRequestUntil = null;
  //USER: Here are the local variables
  
  

  // This routine must validate all fields on the form.  The err (ErrorTrack) variable
  // is used by the FUtil to verify fields.  Use FUTil or custom code here for verifications
  private boolean chkForm() {
    // Do not change
   err.reset();
   UC.Look(error);
   
   if(longhaulIP.getText().trim().equals("")) strlonghaulIP="";
   else strlonghaulIP = FUtil.chkIP(longhaulIP,err,true);
   if(longhaulport.getText().trim().equals("")) intlonghaulport=0;
   else intlonghaulport = FUtil.chkInt(longhaulport, err,1024,65535,true);
   if(requestIP.getText().trim().equals("")) strrequestIP="";
   else    strrequestIP = FUtil.chkIP(requestIP,err,true);
   if(requestport.getText().trim().equals("")) intrequestport=0;
   else    intrequestport = FUtil.chkInt(requestport, err,1024,65535,true);
   if(disableRequestUntil.getText().trim().equals("")) tsdisableRequestUntil=ts;
   else    tsdisableRequestUntil = FUtil.chkTimestamp(disableRequestUntil, err);
   
   
   
   //USER:  Your error checking code goes here setting the local variables to valid settings
   // note : Use FUtil.chk* to check dates, timestamps, IP addresses, etc and put them in
   // canonical form.  store in a class variable.  For instance :
   //   sdate = FUtil.chkDate(dateTextField, err);
   // Any errors found should err.set(true);  and err.append("error message");
   Util.prt("chkForm GSNStation");

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
    longhaulIP.setBackground(UC.white);
    longhaulport.setBackground(UC.white);
    requestIP.setBackground(UC.white);
    requestport.setBackground(UC.white);
    disableRequestUntil.setBackground(UC.white);
    error.setText("");
    addUpdate.setEnabled(false);
    addUpdate.setText("Enter a GSNStation");
    
    //USER: Clear all fields for this form, this sets "defaults" for new screen
    // description.setText("");
//    gsnstation.setSelectedIndex(-1);
    longhaulIP.setText("");
    longhaulport.setText("");
    longhaulprotocol.setSelectedItem(-1);
    role.setSelectedItem(-1);
    options.setText("");    
    requestIP.setText("");    
    requestport.setText("");
    disableRequestUntil.setText("");
    link.setVisible(false);       // We have not implemented anything here
    labLink.setVisible(false);
//    jTextArea1.setText("");
  }
  private void setDefaultOptions(String sorg) {
    Object gsn = gsnstation.getSelectedItem();
    if(gsn == null) return;
    String nnssss = null;
    if(gsn instanceof String) nnssss = (String) gsn;
    else if(gsn instanceof EdgeStation) nnssss= ((EdgeStation) gsn).getGSNStation();
    if(nnssss == null) return;
    nnssss = nnssss.trim();
    if(sorg.equalsIgnoreCase("SeedLink")) {
          options.setText("-noevent -S "+
          nnssss.substring(0,2)+"_"+nnssss.substring(2).trim()+"/??? -k 30 -nt 60 -x config/"+
              nnssss+".state #-etna2");
      longhaulport.setText("18000");
    }
    else if(sorg.equalsIgnoreCase("ISI")) {
      options.setText("-throttle 20000");
      requestIP.setText(longhaulIP.getText());
      requestport.setText("4003");
      longhaulport.setText("39136");
    }
    else if(sorg.equalsIgnoreCase("Edge")) {
      options.setText("-gsn");
      longhaulport.setText("2061");
      requestIP.setText(longhaulIP.getText());
      requestport.setText("2061");
    }
    else if(sorg.equalsIgnoreCase("netserv")) {
      options.setText("-dbg -allow4k");
      longhaulport.setText("4000");
      requestIP.setText(longhaulIP.getText());
      requestport.setText("4003");
    }
  }
  
  private EdgeStation newOne() {
    return new EdgeStation(0, ((String) gsnstation.getSelectedItem()).toUpperCase(), // more
            "",0,"NONE","",0,ts, "", 0
       );
  }
  
  private void oldOne(String p) throws SQLException {
    obj = new DBObject(DBConnectionThread.getThread(DBConnectionThread.getDBSchema()), DBConnectionThread.getDBSchema(), "gsnstation","gsnstation",p);
    
    
    if(obj.isNew()) {
      Util.prt("object is new"+p);
    }
    else {
      //USER: Here set all of the form fields to data from the DBObject
      //      gsnstation.setText(obj.getString("EdgeStation"));
      // Example : description.setText(obj.getString("description"));
      longhaulIP.setText(""+obj.getString("longhaulIP"));
      longhaulport.setText(""+obj.getInt("longhaulport"));
      if(longhaulport.getText().trim().equals("0")) longhaulport.setText("");
      longhaulprotocol.setSelectedItem(obj.getString("longhaulprotocol"));
      options.setText(""+obj.getString("options"));
      requestIP.setText(""+obj.getString("requestIP"));
      requestport.setText(""+obj.getInt("requestport"));
      if(requestport.getText().trim().equals("0")) requestport.setText("");
      disableRequestUntil.setText(""+obj.getTimestamp("disableRequestUntil").toString().substring(0,16));

      EdgeStationPanel.setJComboBoxToID(gsnstation,obj.getInt("ID"));
      RolePanel.setJComboBoxToID(role,obj.getInt("roleid"));
      
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
    lblGsnstation = new javax.swing.JLabel();
    gsnstation = getJComboBox();
    addUpdate = new javax.swing.JButton();
    ID = new javax.swing.JTextField();
    jLabel9 = new javax.swing.JLabel();
    error = new javax.swing.JTextField();
    lblLonghaulProtocol = new javax.swing.JLabel();
    lblRequestIP = new javax.swing.JLabel();
    lblLonghaulIP = new javax.swing.JLabel();
    lblLonghaulPort = new javax.swing.JLabel();
    longhaulIP = new javax.swing.JTextField();
    longhaulport = new javax.swing.JTextField();
    requestIP = new javax.swing.JTextField();
    requestport = new javax.swing.JTextField();
    disableRequestUntil = new javax.swing.JTextField();
    lblRequestPort = new javax.swing.JLabel();
    lblDisableRequestUntil = new javax.swing.JLabel();
    longhaulprotocol = FUtil.getEnumJComboBox(DBConnectionThread.getConnection(DBConnectionThread.getDBSchema()), "gsnstation","longhaulprotocol");
    labCPU = new javax.swing.JLabel();
    role = gov.usgs.edge.config.RolePanel.getJComboBox();
    labOpt = new javax.swing.JLabel();
    options = new javax.swing.JTextField();
    labLink = new javax.swing.JLabel();
    link = gov.usgs.anss.gui.CommLinkPanel.getJComboBox();
    jScrollPane2 = new javax.swing.JScrollPane();
    optionHelp = new javax.swing.JEditorPane();
    delete = new javax.swing.JButton();
    match = new javax.swing.JTextField();
    labMatch = new javax.swing.JLabel();
    setDefaults = new javax.swing.JButton();

    setLayout(new java.awt.GridBagLayout());

    lblGsnstation.setText("Net & Station(NNSSSS): ");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 2;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    add(lblGsnstation, gridBagConstraints);

    gsnstation.setEditable(true);
    gsnstation.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        gsnstationActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 2;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(gsnstation, gridBagConstraints);

    addUpdate.setText("Add/Update");
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

    ID.setEditable(false);
    ID.setColumns(8);
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

    lblLonghaulProtocol.setText("LongHaul Protocol : ");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 4;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    add(lblLonghaulProtocol, gridBagConstraints);

    lblRequestIP.setText("Request IP : ");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 11;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    add(lblRequestIP, gridBagConstraints);

    lblLonghaulIP.setText("LongHaul IP/DNS name: ");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 3;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    add(lblLonghaulIP, gridBagConstraints);

    lblLonghaulPort.setText("LongHaul Port : ");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 5;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    add(lblLonghaulPort, gridBagConstraints);

    longhaulIP.setMinimumSize(new java.awt.Dimension(308, 28));
    longhaulIP.setPreferredSize(new java.awt.Dimension(308, 28));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 3;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(longhaulIP, gridBagConstraints);

    longhaulport.setMinimumSize(new java.awt.Dimension(128, 28));
    longhaulport.setPreferredSize(new java.awt.Dimension(128, 28));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 5;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(longhaulport, gridBagConstraints);

    requestIP.setMinimumSize(new java.awt.Dimension(128, 28));
    requestIP.setPreferredSize(new java.awt.Dimension(128, 28));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 11;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(requestIP, gridBagConstraints);

    requestport.setMinimumSize(new java.awt.Dimension(128, 28));
    requestport.setPreferredSize(new java.awt.Dimension(128, 28));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 12;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(requestport, gridBagConstraints);

    disableRequestUntil.setMinimumSize(new java.awt.Dimension(200, 28));
    disableRequestUntil.setPreferredSize(new java.awt.Dimension(200, 28));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 14;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(disableRequestUntil, gridBagConstraints);

    lblRequestPort.setText("Request Port : ");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 12;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    add(lblRequestPort, gridBagConstraints);

    lblDisableRequestUntil.setText("Disable Request Until ?");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 14;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    add(lblDisableRequestUntil, gridBagConstraints);

    longhaulprotocol.setMinimumSize(new java.awt.Dimension(128, 28));
    longhaulprotocol.setPreferredSize(new java.awt.Dimension(128, 28));
    longhaulprotocol.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        longhaulprotocolActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 4;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(longhaulprotocol, gridBagConstraints);

    labCPU.setText("Role :");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 7;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    add(labCPU, gridBagConstraints);

    role.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        roleActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 7;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(role, gridBagConstraints);

    labOpt.setText("Options :");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 6;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    add(labOpt, gridBagConstraints);

    options.setFont(new java.awt.Font("Monospaced", 0, 10)); // NOI18N
    options.setToolTipText("<html>\nSeedLink -S NN_SSSS/CCC Channel examples:  ??? all channels, HH?&HN? , or H??&L??&VM?\n<p>\nISI - Use -throttle NNN if 30000 bps is not right, optionally use -bind nn.nn.nn.nn if multihome and firewalls prevent using some IPs.\n<p>\nNanometrics - SeedLink should NOT have -n -k or -x statefile configuration.  The Nanonmetrics SeedLink server blows up if these are set.\n<p>\nEtna2 - Seedlink always use -n -k and -x configuration.  The buffer is small but the statefile helps.\n<p>\nEdge/EdgeGeo - -gsn suitable for USGS station processors running Edge software (RRP options)\n<p>\nLISS/NetServ/Comserve- are no longer used - this is expert time!\n\n</html>");
    options.setMinimumSize(new java.awt.Dimension(575, 23));
    options.setPreferredSize(new java.awt.Dimension(575, 23));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 6;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(options, gridBagConstraints);

    labLink.setText("BackHaul :");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 8;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    add(labLink, gridBagConstraints);

    link.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        linkActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 8;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(link, gridBagConstraints);

    jScrollPane2.setMinimumSize(new java.awt.Dimension(500, 200));
    jScrollPane2.setPreferredSize(new java.awt.Dimension(600, 200));

    optionHelp.setEditable(false);
    optionHelp.setContentType("text/html"); // NOI18N
    optionHelp.setMinimumSize(new java.awt.Dimension(500, 80));
    optionHelp.setPreferredSize(new java.awt.Dimension(500, 200));
    jScrollPane2.setViewportView(optionHelp);

    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 9;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(jScrollPane2, gridBagConstraints);

    delete.setText("Delete Record");
    delete.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        deleteActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 18;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_END;
    add(delete, gridBagConstraints);

    match.setToolTipText("<html>\nEnter some text and all stations containing this string will be in the Station selection box.\n<p>\nNote that this is NOT a regular expression so \"US\" would match all US network stations and \"IUSAML\".\n</html>");
    match.setMinimumSize(new java.awt.Dimension(100, 26));
    match.setPreferredSize(new java.awt.Dimension(100, 26));
    match.addFocusListener(new java.awt.event.FocusAdapter() {
      public void focusLost(java.awt.event.FocusEvent evt) {
        matchFocusLost(evt);
      }
    });
    match.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        matchActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(match, gridBagConstraints);

    labMatch.setText("Matching(optional):");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    add(labMatch, gridBagConstraints);

    setDefaults.setText("Set Defaults based on Long Haul Protocol");
    setDefaults.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        setDefaultsActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 18;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
    add(setDefaults, gridBagConstraints);
  }// </editor-fold>//GEN-END:initComponents

  private void addUpdateActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_addUpdateActionPerformed
    // Add your handling code here:
    
    // Do not change
    if(initiating) return;
    if(chkForm()) return;
    try {
      String p = gsnstation.getSelectedItem().toString();
      p = p.toUpperCase();                  // USER : drop this to allow Mixed case
      if(!obj.isNew()) obj.refreshRecord();        // Refresh the result set so the sets will not fail on stale one
      obj.setString("gsnstation",p);
      
      // USER : Set all of the fields in data base.  obj.setInt("thefield", ivalue);
      
      obj.setString("longhaulIP",strlonghaulIP);
      obj.setInt("longhaulport", intlonghaulport);
      obj.setInt("longhaulprotocol", longhaulprotocol.getSelectedIndex());
//      obj.setInt("cpuID", ((Cpu) cpuID.getSelectedItem()).getID());
      obj.setInt("roleid", (role.getSelectedItem()== null)? 0 :((Role) role.getSelectedItem()).getID());
      obj.setString("options",options.getText());
      obj.setString("requestIP",strrequestIP);
      obj.setInt("requestport", intrequestport);
      obj.setTimestamp("disableRequestUntil", tsdisableRequestUntil);

      
      // Do not change
      obj.updateRecord();
      v=null;       // force reload of combo box
      getJComboBox(gsnstation);
      clearScreen();
      gsnstation.setSelectedIndex(-1);
    }  catch  (SQLException  E)
    { Util.SQLErrorPrint(E,"gsnstation: update failed partno="+gsnstation.getSelectedItem());
      error.setText("Could Not Update File!");
      error.setBackground(UC.red);
      addUpdate.setEnabled(true);
    }      
  }//GEN-LAST:event_addUpdateActionPerformed

  private void gsnstationActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_gsnstationActionPerformed
    // Add your handling code here:
   find();
 
 
  }//GEN-LAST:event_gsnstationActionPerformed

private void roleActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_roleActionPerformed
  // TODO add your handling code here:
  // Only list cpu's starting with "gacq"
  
}//GEN-LAST:event_roleActionPerformed

private void linkActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_linkActionPerformed
// TODO add your handling code here:
}//GEN-LAST:event_linkActionPerformed

private void longhaulprotocolActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_longhaulprotocolActionPerformed
// TODO add your handling code here:
  if(longhaulprotocol.getSelectedIndex() >= 0) {
    String s = longhaulprotocol.getSelectedItem().toString();
    String sorg = s;
    Object gsn = gsnstation.getSelectedItem();
    if(gsn == null) return;
    String gsnstring = null;
    if(gsn instanceof String) gsnstring = (String) gsn;
    else if(gsn instanceof EdgeStation) gsnstring= ((EdgeStation) gsn).getGSNStation();
    if(gsnstring == null) return;
    gsnstring=gsnstring.trim();
    lblLonghaulPort.setVisible(true);
    longhaulport.setVisible(true);
    lblRequestIP.setVisible(true);
    lblRequestPort.setVisible(true);
    requestIP.setVisible(true);
    requestport.setVisible(true);
    if(s.equals("ISI")) s = "ISILink";
    else if(s.equals("LISS")) s = "LISSClient";
    else if(s.equals("NetServ")) s = "NetservClient";
    else if(s.equals("Comserv")) s= "MiniSeedServer";
    else if(s.equals("SeedLink")) s= "SeedLinkClient";
    else if(s.contains("Edge")) {
      s = "RRPServer";
      lblLonghaulPort.setVisible(false);
      longhaulport.setVisible(false);    
      lblRequestIP.setVisible(false);
      lblRequestPort.setVisible(false);
      requestIP.setVisible(false);
      requestport.setVisible(false);    
    }
    else s="NONE";
    if(!s.equals("NONE")) {
      try {
        Statement stmt = DBConnectionThread.getConnection(DBConnectionThread.getDBSchema()).createStatement();   // used for query
        ResultSet rs = stmt.executeQuery("SELECT help FROM edge.edgethread WHERE classname='"+s+"'");
        if(rs.next()) {
          String help = rs.getString("help");
          //if(help.indexOf("<PRE>") >= 0) help = help.substring(help.indexOf("<PRE>")+5);
          //if(help.indexOf("</PRE>")>= 0) help = help.substring(help.indexOf("</PRE>"));
          if(longhaulprotocol.getSelectedItem().toString().equals("Comserv"))
            help +="<br>Comserv/cs2edge :<br>-csbuf nn   to set number of buffers in cs2edge (def=1000)<br>";
          optionHelp.setText(help);
        }
      }
      catch(SQLException e) {
        Util.SQLErrorPrint(e,"Trying to get help from EdgeThread table");
      }
      
    }
    else {
      if(longhaulprotocol.getSelectedItem().toString().equals("Comserv")) optionHelp.setText("Add -csbuf nn to set number of buffers (def=1000)");
      else optionHelp.setText("");
    }
    if(obj.isNew()) {
        setDefaultOptions(sorg);
    }

  }
}//GEN-LAST:event_longhaulprotocolActionPerformed

private void deleteActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_deleteActionPerformed
// TODO add your handling code here:
  try {
    obj.deleteRecord();
    gsnstation.removeItem(gsnstation.getSelectedItem());
    clearScreen();
  }
  catch(SQLException e) {
    Util.prta("Delete record failed SQL error="+e);
  }
}//GEN-LAST:event_deleteActionPerformed

  private void matchActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_matchActionPerformed
    rebuildStationJCombo();
  }//GEN-LAST:event_matchActionPerformed

  private void matchFocusLost(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_matchFocusLost
    rebuildStationJCombo();
  }//GEN-LAST:event_matchFocusLost

  private void setDefaultsActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_setDefaultsActionPerformed
    if(longhaulprotocol.getSelectedIndex() >= 0) {
      String s = longhaulprotocol.getSelectedItem().toString();   
      setDefaultOptions(s);
    }
  }//GEN-LAST:event_setDefaultsActionPerformed
  private void rebuildStationJCombo() {
    reload();
    getJComboBox(gsnstation);
    if(match.getText().equals("")) return;
    for(int i=gsnstation.getItemCount()-1; i>=0; i--) {
      if(!gsnstation.getItemAt(i).toString().contains(match.getText())) gsnstation.removeItemAt(i);
    }
    if(gsnstation.getItemCount() > 0) gsnstation.setSelectedIndex(0);
  }  
  
  // Variables declaration - do not modify//GEN-BEGIN:variables
  private javax.swing.JTextField ID;
  private javax.swing.JButton addUpdate;
  private javax.swing.JButton delete;
  private javax.swing.JTextField disableRequestUntil;
  private javax.swing.JTextField error;
  private java.awt.GridBagLayout gridBagLayout1;
  private javax.swing.JComboBox gsnstation;
  private javax.swing.JLabel jLabel9;
  private javax.swing.JScrollPane jScrollPane2;
  private javax.swing.JLabel labCPU;
  private javax.swing.JLabel labLink;
  private javax.swing.JLabel labMatch;
  private javax.swing.JLabel labOpt;
  private javax.swing.JLabel lblDisableRequestUntil;
  private javax.swing.JLabel lblGsnstation;
  private javax.swing.JLabel lblLonghaulIP;
  private javax.swing.JLabel lblLonghaulPort;
  private javax.swing.JLabel lblLonghaulProtocol;
  private javax.swing.JLabel lblRequestIP;
  private javax.swing.JLabel lblRequestPort;
  private javax.swing.JComboBox link;
  private javax.swing.JTextField longhaulIP;
  private javax.swing.JTextField longhaulport;
  private javax.swing.JComboBox longhaulprotocol;
  private javax.swing.JTextField match;
  private javax.swing.JEditorPane optionHelp;
  private javax.swing.JTextField options;
  private javax.swing.JTextField requestIP;
  private javax.swing.JTextField requestport;
  private javax.swing.JComboBox role;
  private javax.swing.JButton setDefaults;
  // End of variables declaration//GEN-END:variables
   private FlagPanel2 fpGroupMask;
   /** Creates new form GSNStationPanel */
  public EdgeStationPanel() {
    initiating=true;
    initComponents();
    getJComboBox(gsnstation);                // set up the key JComboBox
    gsnstation.setSelectedIndex(-1);    // Set selected type
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
    makeGSNStations();
    for (int i=0; i< v.size(); i++) {
      b.addItem( v.get(i));
    }
    b.setMaximumRowCount(30);
    b.setSelectedIndex(-1);
  }
  
  /** Given a JComboBox from getJComboBox, set selected item to match database ID
   *@param b The JComboBox
   *@param ID the row ID from the database to select
   */
  public static void setJComboBoxToID(JComboBox b, int ID) {
    b.setSelectedIndex(-1);
    //Util.prt("EdgeStationPanel.setJComboBoxToID id="+ID);
    for(int i=0; i<b.getItemCount(); i++) 
      if(((EdgeStation) b.getItemAt(i)).getID() == ID) {
          b.setSelectedIndex(i);
          //Util.prt("EdgeStationPanel.setJComboBoxToID id="+ID+" found at "+i);
      }
  }  
  
  /** Given a database ID find the index into a JComboBox consistent with this panel
   *@param ID The row ID from the database
   *@return The index in the JComboBox with this ID
   */
  public static int getJComboBoxIndex(int ID) {
    if(v == null) makeGSNStations();
    for(int i=0; i<v.size(); i++) if( ID == ((EdgeStation) v.get(i)).getID()) return i;
    return -1;
  }
  /** Cause the main JComboBox to be reloaded. */
  public static void reload() {
    v = null;
    makeGSNStations();
  }
    /* return a vector with all of the EdgeStation
   * @return The vector with the gsnstation
   */
  public static ArrayList getGSNStationVector() {
    if(v == null) makeGSNStations();
    return v;
  }
  /** Get the item corresponding to database ID
   *@param ID the database Row ID
   *@return The EdgeStation row with this ID
   */
  public static EdgeStation getGSNStationWithID(int ID) {
    if(v == null) makeGSNStations();
    int i=getJComboBoxIndex(ID);
    if(i >= 0) return (EdgeStation) v.get(i);
    else return null;
  }
  
  // This routine should only need tweeking if key field is not same as table name
  private static void makeGSNStations() {
    if (v != null) return;
    v=new ArrayList<EdgeStation>(100);
    try {
      Statement stmt = DBConnectionThread.getConnection(DBConnectionThread.getDBSchema()).createStatement();   // used for query
      String s = "SELECT * FROM edge.gsnstation ORDER BY gsnstation;";
      ResultSet rs = stmt.executeQuery(s);
      while (rs.next()) {
        EdgeStation loc = new EdgeStation(rs);
//        Util.prt("MakeGSNStation() i="+v.size()+" is "+loc.getGSNStation());
        v.add(loc);
      }
      rs.close();
      stmt.close();
    }
    catch (SQLException e) {
      Util.SQLErrorPrint(e,"makeGSNStations() on table SQL failed");
    }    
  }
  
  // No changes needed
  private void find() {
    if(gsnstation == null) return;
    if(initiating) return;
    EdgeStation l;
    if(gsnstation.getSelectedIndex() == -1) {
      if(gsnstation.getSelectedItem() == null) return;
      l = newOne();
    } 
    else {
      l = (EdgeStation) gsnstation.getSelectedItem();
    }
      
    if(l == null) return;
    String p = l.getGSNStation();
    if(p == null) return;
    if(p.equals("")) {
      addUpdate.setEnabled(false);
      addUpdate.setText("Enter a GSNStation!");
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
    { Util.SQLErrorPrint(E,"GSNStation: SQL error getting GSNStation="+p);
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
      Show.inFrame(new EdgeStationPanel(), UC.XSIZE, UC.YSIZE);
    }
    catch(InstantiationException e) {}
  }
}
