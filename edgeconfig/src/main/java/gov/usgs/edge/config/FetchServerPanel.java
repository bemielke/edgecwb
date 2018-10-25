package gov.usgs.edge.config;

/*
 * Copyright 2012, Incorporated Research Institutions for Seismology (IRIS) or
 * third-party contributors as indicated by the @author tags.
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */
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
 * JComboBox variable this variable is called "fetchServer" in the initial form
 * The table name and key name should match and start lower case (fetchServer).
 * The Class here will be that same name but with upper case at beginning(FetchServer).
 <br> 1)  Rename the location JComboBox to the "key" (fetchServer) value.  This should start Lower
 *      case.
 *<br>  2)  The table name should match this key name and be all lower case.
 *<br>  3)  Change all FetchServer to ClassName of underlying data (case sensitive!)
<br>  4)  Change all fetchServer key value (the JComboBox above)
 *<br>  5)  clearScreen should update swing components to new defaults
 *<br>  6)  chkForm should check all field for validity.  The errorTrack variable is
 *      used to track multiple errors.  In FUtil.chk* handle checks of the various
 *      variable types.  Date, double, and IP etc return the "canonical" form for 
 *      the variable.  These should be stored in a class variable so the latest one
 *      from "chkForm()" are available to the update writer.
 *<br>  7) newone() should be updated to create a new instance of the underlying data class.
 *<br>  8) oldone() get data from database and update all swing elements to correct values
 *<br>  9) addUpdateAction() add data base set*() operations updating all fields in the DB
 *<br> 10) FetchServerPanel() constructor - good place to change backgrounds using UC.Look() any
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
public class FetchServerPanel extends javax.swing.JPanel {
  static ArrayList<String> discoveryMethods = FUtil.getEnum(DBConnectionThread.getConnection("edge"), "fetchserver", "discoverymethod");
  //USER: set this for the upper case conversion of keys.
  private boolean keyUpperCase=false;    // set this true if all keys must be upper case
  //USER: Here are the local variables
  // NOTE : here define all variables general.  "ArrayList v" is used for main Comboboz
  int intPort;
  int valueProcOrder,valueFetchPort, valueThrottle;
  int cwbPortValue, edgePortValue;
  private void doInit() {
  // Good place to add UC.Looks()
    deleteItem.setVisible(true);
    reload.setVisible(true);
    idLab.setVisible(true);
    ID.setVisible(true);
    changeTo.setVisible(true);
    labChange.setVisible(true);
    UC.Look(this);                    // Set color background

  }
  private void doAddUpdate() throws SQLException {
    // USER : Set all of the fields in data base.  obj.setInt("thefield", ivalue);
    obj.setString("ipadr", ipadr.getText());
    obj.setString("gaptype", gaptype.getText());
    obj.setInt("port", intPort);
    obj.setString("chanre",chanRE.getText());
    obj.setInt("procorder", valueProcOrder);
    obj.setString("fetchipadr", fetchIP.getText());
    obj.setInt("fetchport", valueFetchPort);
    obj.setInt("throttle", valueThrottle);
    if(role.getSelectedIndex() >=0 )
      obj.setInt("roleid", ((RoleInstance) role.getSelectedItem()).getID());
    if(discoveryType.getSelectedIndex() >= 0) 
      obj.setInt("discoverymethod", FUtil.enumStringToInt(discoveryMethods, (String) discoveryType.getSelectedItem()));
    if(fetchRequestType.getSelectedIndex() >= 0) 
      obj.setInt("fetchrequesttypeid",((RequestType) fetchRequestType.getSelectedItem()).getID());  }
  private void doOldOne() throws SQLException {
      //USER: Here set all of the form fields to data from the DBObject
      //      fetchServer.setText(obj.getString("FetchServer"));
      // Example : description.setText(obj.getString("description"));
    ipadr.setText(obj.getString("ipadr"));
    port.setText(obj.getInt("port")+"");
    gaptype.setText(obj.getString("gaptype"));
    chanRE.setText(obj.getString("chanre"));
    procorder.setText(obj.getInt("procorder")+"");
    fetchIP.setText(obj.getString("fetchipadr")+"");
    fetchPort.setText(obj.getInt("fetchport")+"");
    throttle.setText(obj.getInt("throttle")+"");
    RoleInstancePanel.setJComboBoxToID(role, obj.getInt("roleid"));
    discoveryType.setSelectedIndex(FUtil.enumStringToInt(discoveryMethods, obj.getString("discoverymethod")));
    RequestTypePanel.setJComboBoxToID(fetchRequestType, obj.getInt("fetchrequesttypeid"));
  }
  

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
    intPort = FUtil.chkInt(port, err, 0, 63999, true);
    valueProcOrder = FUtil.chkInt(procorder, err, 0, 1000000, true);
    valueFetchPort = FUtil.chkInt(fetchPort, err, 0, 65000, true);
    valueThrottle = FUtil.chkInt(throttle, err, 0, 1000000, true);
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
    UC.Look(bottomStuff);
    error.setText("");
    addUpdate.setEnabled(false);
    addUpdate.setText("Enter a FetchServer");
    deleteItem.setEnabled(false);
    changeTo.setText("");
    
    //USER: Clear all fields for this form, this sets "defaults" for new screen
    // description.setText("");
    ipadr.setText("");
    gaptype.setText("");
    port.setText("18000");
    chanRE.setText("[SEBH][NL][ZNE123]");
    fetchIP.setText("");
    fetchPort.setText("");
    throttle.setText("");
    role.setSelectedIndex(-1);
    discoveryType.setSelectedIndex(0);
    fetchRequestType.setSelectedIndex(-1);
    procorder.setText("");
  }


  /**********************  NO USER MODIFICATION BELOW HERE EXCEPT FOR ACTIONS **************/
  static ArrayList<FetchServer> v;             // ArrayList containing objects of this FetchServer Type
  DBObject obj;
  ErrorTrack err=new ErrorTrack();
  boolean initiating=false;

  
  private FetchServer newOne() {
      
    return new FetchServer(0, ((String) fetchServer.getSelectedItem()).toUpperCase() //, more
            ,"",18000,"","", "NONE","", 0, 0, 0
       );
  }
  
  private void oldOne(String p) throws SQLException {
    obj = new DBObject(DBConnectionThread.getThread(DBConnectionThread.getDBSchema()), 
            DBConnectionThread.getDBSchema(),       // Schema, if this is not right in URL load, it must be explicit here
            "fetchserver","fetchserver",p);                     // table name and field name are usually the same

    if(obj.isNew()) {
      //Util.prt("object is new"+p);
    }
    else {
      //USER: Here set all of the form fields to data from the DBObject
      //      fetchServer.setText(obj.getString("FetchServer"));
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
    fetchServer = getJComboBox();
    error = new javax.swing.JTextField();
    bottomStuff = new javax.swing.JPanel();
    addUpdate = new javax.swing.JButton();
    labChange = new javax.swing.JLabel();
    changeTo = new javax.swing.JTextField();
    deleteItem = new javax.swing.JButton();
    reload = new javax.swing.JButton();
    idLab = new javax.swing.JLabel();
    ID = new javax.swing.JTextField();
    labIP = new javax.swing.JLabel();
    ipadr = new javax.swing.JTextField();
    labPort = new javax.swing.JLabel();
    port = new javax.swing.JTextField();
    labGaptype = new javax.swing.JLabel();
    gaptype = new javax.swing.JTextField();
    labChanRE = new javax.swing.JLabel();
    chanRE = new javax.swing.JTextField();
    labProcess = new javax.swing.JLabel();
    procorder = new javax.swing.JTextField();
    role = RoleInstancePanel.getJComboBox()
    ;
    labRole = new javax.swing.JLabel();
    labReqType = new javax.swing.JLabel();
    discoveryType = FUtil.getEnumJComboBox(DBConnectionThread.getConnection("edge"), "fetchserver","discoverymethod");
    labFetchIP = new javax.swing.JLabel();
    fetchIP = new javax.swing.JTextField();
    labFetchPort = new javax.swing.JLabel();
    fetchPort = new javax.swing.JTextField();
    latThrottle = new javax.swing.JLabel();
    throttle = new javax.swing.JTextField();
    labFetchReqType = new javax.swing.JLabel();
    fetchRequestType = RequestTypePanel.getJComboBox();

    setLayout(new java.awt.GridBagLayout());

    labMain.setText("FetchServer:");
    labMain.setToolTipText("This button commits either a new item or the changes for an updating item.  Its label with change to reflect that.");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_END;
    add(labMain, gridBagConstraints);

    fetchServer.setEditable(true);
    fetchServer.setToolTipText("Select an item to edit in the JComboBox or type in the name of an item.   If the typed name does not match an existing item, an new item is assumed.");
    fetchServer.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        fetchServerActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(fetchServer, gridBagConstraints);

    error.setColumns(40);
    error.setEditable(false);
    error.setMinimumSize(new java.awt.Dimension(488, 22));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(error, gridBagConstraints);

    bottomStuff.setMinimumSize(new java.awt.Dimension(550, 60));

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
    gridBagConstraints.gridy = 17;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(bottomStuff, gridBagConstraints);

    labIP.setText("Discovery IP Adr:");
    labIP.addFocusListener(new java.awt.event.FocusAdapter() {
      public void focusLost(java.awt.event.FocusEvent evt) {
        labIPFocusLost(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 5;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    add(labIP, gridBagConstraints);

    ipadr.setToolTipText("<html>\nThis is the IP address of the SeedLink, FDSNWaveServer, TrinetWave server, etc which will be queried for the channels available.\n<p>\nThis is ofen not the address of where the actual timeseries wil be requested, but it can be. \n<p> For instance SeedLink is often used to learn what channels are available, but the actual requests are made to a data center using FDSNWaveServices.\n</html>");
    ipadr.setMinimumSize(new java.awt.Dimension(214, 28));
    ipadr.setPreferredSize(new java.awt.Dimension(214, 28));
    ipadr.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        ipadrActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 5;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(ipadr, gridBagConstraints);

    labPort.setText("Discovery Port :");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 6;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    add(labPort, gridBagConstraints);

    port.setToolTipText("This is the port of the SeedLinkServer, Trinet Wave Server of Earthworm Wave server to use find what channels are available.");
    port.setMinimumSize(new java.awt.Dimension(64, 28));
    port.setPreferredSize(new java.awt.Dimension(64, 28));
    port.addFocusListener(new java.awt.event.FocusAdapter() {
      public void focusLost(java.awt.event.FocusEvent evt) {
        portFocusLost(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 6;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(port, gridBagConstraints);

    labGaptype.setText("Discovery Gap Type:");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 7;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    add(labGaptype, gridBagConstraints);

    gaptype.setToolTipText("<html>\nThe two character gap type used to to specify which method will be used to gather the data.  \n<p>\nFor all discovered channels this gaptype will be set in the edge.channel table to indicate this.\n</html>");
    gaptype.setMinimumSize(new java.awt.Dimension(34, 28));
    gaptype.setPreferredSize(new java.awt.Dimension(34, 28));
    gaptype.addFocusListener(new java.awt.event.FocusAdapter() {
      public void focusLost(java.awt.event.FocusEvent evt) {
        gaptypeFocusLost(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 7;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(gaptype, gridBagConstraints);

    labChanRE.setText("Discovery Channel RE :");
    labChanRE.setToolTipText("");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 8;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    add(labChanRE, gridBagConstraints);

    chanRE.setToolTipText("<html>\nThis is a regular expression for the channel 3 character code which are to be discovered.  \n<p>\nFor instance when in SeedLink is probed for strong motion channels only this might be \"[SEBH][NL][ZNE123]\"\n</html>\n");
    chanRE.setMinimumSize(new java.awt.Dimension(414, 28));
    chanRE.setPreferredSize(new java.awt.Dimension(414, 28));
    chanRE.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        chanREActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 8;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(chanRE, gridBagConstraints);

    labProcess.setText("Discovery Order:");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 9;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    add(labProcess, gridBagConstraints);

    procorder.setColumns(4);
    procorder.setToolTipText("<html>\nWhen discovering channels, it is possible that a channel exists in multiple place - say a network's SeedLink server and the IRIS DMC Bud SeedLink server.\n<p>\nSet this such that when duplicates occur, the higher of these will be the source of the data used.  \n<p>\nExample: set the IRIS DMC to a large value so it is used if possible, but if not the next highest value is used.\n</html>");
    procorder.setMinimumSize(new java.awt.Dimension(62, 28));
    procorder.addFocusListener(new java.awt.event.FocusAdapter() {
      public void focusLost(java.awt.event.FocusEvent evt) {
        procorderFocusLost(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 9;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(procorder, gridBagConstraints);

    role.setToolTipText("The FetchServer on this role will get time series data from this FetchServer configuration.  ");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 2;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
    add(role, gridBagConstraints);

    labRole.setText("Role:");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 2;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_END;
    add(labRole, gridBagConstraints);

    labReqType.setText("DiscoveryType:");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 3;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_END;
    add(labReqType, gridBagConstraints);

    discoveryType.setToolTipText("<html>\nThis is the method to be used for gathering information about what channels exist - often it is a SeedLinkServer address.  \n<p>\nThat is this is NOT the method for the eventual request, but the method for gathering information about the channels.  These are often different.\n<br>\nA RequestType is configured in the RequestType panel and sets the output parameters and request method.  \n</html>");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 3;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
    add(discoveryType, gridBagConstraints);

    labFetchIP.setText("FetchIPAdr:");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 10;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_END;
    add(labFetchIP, gridBagConstraints);

    fetchIP.setToolTipText("<html>\nThe IP or DNS server name of the data center computer to be queried for the data (like the IRIS DMC).\n<p>\nThis server will be using the request method specified in the fetcher Request Type field (Like FDSNWebServices).\n</html>\n");
    fetchIP.setMinimumSize(new java.awt.Dimension(214, 26));
    fetchIP.setPreferredSize(new java.awt.Dimension(214, 26));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 10;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
    add(fetchIP, gridBagConstraints);

    labFetchPort.setText("FetchPort:");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 11;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_END;
    add(labFetchPort, gridBagConstraints);

    fetchPort.setToolTipText("<html>\nThis is the port to use for fetching time series data from the datacenter.\n</html>\n");
    fetchPort.setMinimumSize(new java.awt.Dimension(64, 26));
    fetchPort.setPreferredSize(new java.awt.Dimension(64, 26));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 11;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
    add(fetchPort, gridBagConstraints);

    latThrottle.setText("Throttle:");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 14;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_END;
    add(latThrottle, gridBagConstraints);

    throttle.setMinimumSize(new java.awt.Dimension(64, 26));
    throttle.setPreferredSize(new java.awt.Dimension(64, 26));
    throttle.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        throttleActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 14;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
    add(throttle, gridBagConstraints);

    labFetchReqType.setText("FetchRequestType:");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 13;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_END;
    add(labFetchReqType, gridBagConstraints);

    fetchRequestType.setToolTipText("<html>\nThis is the request type for fetching the time series data which describe method and were to insert the data. \n<br>\nIt would normally be some data center method in the RequestType table like FDSN WaveServer or CWBQueryServer. <br>\n</html>\n");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 13;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
    add(fetchRequestType, gridBagConstraints);
  }// </editor-fold>//GEN-END:initComponents

  private void addUpdateActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_addUpdateActionPerformed
    // Add your handling code here:
    
    // Do not change
    if(initiating) return;
    if(chkForm()) return;
    try {
      String p = fetchServer.getSelectedItem().toString();
      if(keyUpperCase) p = p.toUpperCase();                  // USER : drop this to allow Mixed case
      if(!obj.isNew()) obj.refreshRecord();        // Refresh the result set so the sets will not fail on stale one
      if(!changeTo.getText().equals("")) p = changeTo.getText();
      obj.setString("fetchServer",p);
      
      // USER : Set all of the fields in data base.  obj.setInt("thefield", ivalue);
      doAddUpdate();
      
      // Do not change
      obj.updateRecord();
      v=null;       // force reload of combo box
      getJComboBox(fetchServer);
      clearScreen();
      fetchServer.setSelectedIndex(-1);
    }  catch  (SQLException  E)
    { Util.SQLErrorPrint(E,"fetchServer: update failed partno="+fetchServer.getSelectedItem());
      error.setText("Could Not Update File!");
      error.setBackground(UC.red);
      addUpdate.setEnabled(true);
    }      
  }//GEN-LAST:event_addUpdateActionPerformed

  private void fetchServerActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_fetchServerActionPerformed
    // Add your handling code here:
   find();
 
 
  }//GEN-LAST:event_fetchServerActionPerformed

private void deleteItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_deleteItemActionPerformed
  try {
    obj.deleteRecord();
    fetchServer.removeItem(fetchServer.getSelectedItem());
    clearScreen();
  }
  catch(SQLException e) {
    Util.prta("Delete record failed SQL error="+e);
  }
}//GEN-LAST:event_deleteItemActionPerformed

private void reloadActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_reloadActionPerformed
  // put whatever jcombobox need reloading here
  //Class.getJComboBox(boxVariable);
  RoleInstancePanel.getJComboBox(role);
  RequestTypePanel.getJComboBox(fetchRequestType);
  
  clearScreen();
}//GEN-LAST:event_reloadActionPerformed

  private void chanREActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_chanREActionPerformed
    find();
    chkForm();
  }//GEN-LAST:event_chanREActionPerformed

  private void ipadrActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_ipadrActionPerformed
   chkForm();
  }//GEN-LAST:event_ipadrActionPerformed

  private void labIPFocusLost(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_labIPFocusLost
   chkForm();
  }//GEN-LAST:event_labIPFocusLost

  private void portFocusLost(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_portFocusLost
   chkForm();
  }//GEN-LAST:event_portFocusLost

  private void gaptypeFocusLost(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_gaptypeFocusLost
   chkForm();
  }//GEN-LAST:event_gaptypeFocusLost

  private void procorderFocusLost(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_procorderFocusLost
    chkForm();
  }//GEN-LAST:event_procorderFocusLost

  private void throttleActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_throttleActionPerformed
    // TODO add yo
  }//GEN-LAST:event_throttleActionPerformed
  
  
  // Variables declaration - do not modify//GEN-BEGIN:variables
  private javax.swing.JTextField ID;
  private javax.swing.JButton addUpdate;
  private javax.swing.JPanel bottomStuff;
  private javax.swing.JTextField chanRE;
  private javax.swing.JTextField changeTo;
  private javax.swing.JButton deleteItem;
  private javax.swing.JComboBox discoveryType;
  private javax.swing.JTextField error;
  private javax.swing.JTextField fetchIP;
  private javax.swing.JTextField fetchPort;
  private javax.swing.JComboBox fetchRequestType;
  private javax.swing.JComboBox fetchServer;
  private javax.swing.JTextField gaptype;
  private java.awt.GridBagLayout gridBagLayout1;
  private javax.swing.JLabel idLab;
  private javax.swing.JTextField ipadr;
  private javax.swing.JLabel labChanRE;
  private javax.swing.JLabel labChange;
  private javax.swing.JLabel labFetchIP;
  private javax.swing.JLabel labFetchPort;
  private javax.swing.JLabel labFetchReqType;
  private javax.swing.JLabel labGaptype;
  private javax.swing.JLabel labIP;
  private javax.swing.JLabel labMain;
  private javax.swing.JLabel labPort;
  private javax.swing.JLabel labProcess;
  private javax.swing.JLabel labReqType;
  private javax.swing.JLabel labRole;
  private javax.swing.JLabel latThrottle;
  private javax.swing.JTextField port;
  private javax.swing.JTextField procorder;
  private javax.swing.JButton reload;
  private javax.swing.JComboBox role;
  private javax.swing.JTextField throttle;
  // End of variables declaration//GEN-END:variables
  /** Creates new form FetchServerPanel */
  public FetchServerPanel() {
    initiating=true;
    initComponents();
    getJComboBox(fetchServer);                // set up the key JComboBox
    fetchServer.setSelectedIndex(-1);    // Set selected type
    initiating=false;
   
    // USER:This is a good place for UC.Look(components) to get rid of the ugly Grey
    doInit();
    clearScreen();                    // Start with empty screen
  }
  
  //////////////////////////////////////////////////////////////////////////////
  // No USER: changes needed below here!
  //////////////////////////////////////////////////////////////////////////////
  /** Create a JComboBox of all of the items in the table represented by this panel
   *@return A New JComboBox filled with all row keys from the table
   */
 public static JComboBox getJComboBox() { JComboBox b = new JComboBox(); getJComboBox(b); return b;}
  /** Udate a JComboBox to represent the rows represented by this panel
   *@param b The JComboBox to Update
   */
  public static void getJComboBox(JComboBox b) {
    b.removeAllItems();
    makeFetchServers();
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
    //Util.prt("FetchServerPanel.setJComboBoxToID id="+ID);
    for(int i=0; i<b.getItemCount(); i++) 
      if(((FetchServer) b.getItemAt(i)).getID() == ID) {
          b.setSelectedIndex(i);
          //Util.prt("FetchServerPanel.setJComboBoxToID id="+ID+" found at "+i);
      }
  }  
  
  /** Given a database ID find the index into a JComboBox consistent with this panel
   *@param ID The row ID from the database
   *@return The index in the JComboBox with this ID
   */
  public static int getJComboBoxIndex(int ID) {
    if(v == null) makeFetchServers();
    for(int i=0; i<v.size(); i++) if( ID == ((FetchServer) v.get(i)).getID()) return i;
    return -1;
  }
  /** Cause the main JComboBox to be reloaded*/
  public static void reload() {
    v = null;
    makeFetchServers();
  }
    /* return a ArrayList with all of the FetchServer
   * @return The ArrayList with the fetchServer
   */
  public static ArrayList<FetchServer> getFetchServerVector() {
    if(v == null) makeFetchServers();
    return v;
  }    /* return a ArrayList with all of the FetchServer
   * @return The ArrayList with the fetchServer
   */
  public static ArrayList<FetchServer> getFetchServerArrayList() {
    if(v == null) makeFetchServers();
    return v;
  }
  /** Get the item corresponding to database ID
   *@param ID the database Row ID
   *@return The FetchServer row with this ID
   */
  public static FetchServer getFetchServerWithID(int ID) {
    if(v == null) makeFetchServers();
    int i=getJComboBoxIndex(ID);
    if(i >= 0) return (FetchServer) v.get(i);
    else return null;
  }
  
  // This routine should only need tweeking if key field is not same as table name
  private static void makeFetchServers() {
    if (v != null) return;
    v=new ArrayList<FetchServer>(100);
    try {
      Statement stmt = DBConnectionThread.getConnection(DBConnectionThread.getDBSchema()).createStatement();   // used for query
      String s = "SELECT * FROM "+DBConnectionThread.getDBSchema()+".fetchserver ORDER BY fetchserver;";
      ResultSet rs = stmt.executeQuery(s);
      while (rs.next()) {
        FetchServer loc = new FetchServer(rs);
//        Util.prt("MakeFetchServer() i="+v.size()+" is "+loc.getFetchServer());
        v.add(loc);
      }
      rs.close();
      stmt.close();
    }
    catch (SQLException e) {
      Util.SQLErrorPrint(e,"makeFetchServers() on table SQL failed");
      e.printStackTrace();
    }    
  }
  
  // No changes needed
  private void find() {
    if(fetchServer == null) return;
    if(initiating) return;
    FetchServer l;
    if(fetchServer.getSelectedIndex() == -1) {
      if(fetchServer.getSelectedItem() == null) return;
      l = newOne();
    } 
    else {
      l = (FetchServer) fetchServer.getSelectedItem();
    }
      
    if(l == null) return;
    String p = l.getFetchServer();
    if(p == null) return;
    if(p.equals("")) {
      addUpdate.setEnabled(false);
      deleteItem.setEnabled(false);
      addUpdate.setText("Enter a FetchServer!");
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
    { Util.SQLErrorPrint(E,"FetchServer: SQL error getting FetchServer="+p);
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
      Show.inFrame(new FetchServerPanel(), UC.XSIZE, UC.YSIZE);
    }
    catch(InstantiationException e) {}


  }
}
