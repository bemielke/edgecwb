/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */

package gov.usgs.edge.snw;
import gov.usgs.edge.config.UpdateFromMetadata;
import gov.usgs.edge.config.Protocol;
import gov.usgs.edge.config.Operator;
import gov.usgs.anss.db.DBConnectionThread;
import gov.usgs.anss.db.DBObject;
import gov.usgs.anss.gui.FlagPanel;
import gov.usgs.anss.util.Util;
import gov.usgs.anss.util.FUtil;
import gov.usgs.anss.util.StaSrv;
import gov.usgs.anss.util.Show;
import gov.usgs.anss.util.ErrorTrack;
import gov.usgs.edge.config.UC;
import gov.usgs.edge.config.ProtocolPanel;
import gov.usgs.edge.config.OperatorPanel;
import java.awt.Color;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import javax.swing.JComboBox;
import javax.swing.JRadioButton;


/**
 * 
 * requirements : 
 * The key for the file must be the name of the table and of the
 * JComboBox variable this variable is called "snwstation" in the initial form
 * The table name and key name should match and start lower case (snwstation).
 * The Class here will be that same name but with upper case at beginning(SNWStation).
 * <br> 1)  Rename the location JComboBox to the "key" (snwstation) value.  This should start Lower
 *      case.
 * <br>  2)  The table name should match this key name and be all lower case.
 * <br>  3)  Change all SNWStation to ClassName of underlying data (case sensitive!)
 * <br>  4)  Change all snwstation key value (the JComboBox above)
 * <br>  5)  clearScreen should update swing components to new defaults
 * <br>  6)  chkForm should check all field for validity.  The errorTrack variable is
 *      used to track multiple errors.  In FUtil.chk* handle checks of the various
 *      variable types.  Date, double, and IP etc return the "canonical" form for 
 *      the variable.  These should be stored in a class variable so the latest one
 *      from "chkForm()" are available to the update writer.
 * <br>  7) newone() should be updated to create a new instance of the underlying data class.
 * <br>  8) oldone() get data from database and update all swing elements to correct values
 * <br>  9) addUpdateAction() add data base set*() operations updating all fields in the DB
 * <br> 10) SNWStationPanel() constructor - good place to change backgrounds using UC.Look() any
 *    other weird startup stuff.
 * 
 * <br>  local variable error - Must be a JTextField for posting error communications
 * <br> local variable updateAdd - Must be the JButton for user clicks to try to post the data
 * <br> ID - JTextField which must be non-editable for posting Database IDs
 * <br>
 * <br>
 * ENUM notes :  the ENUM are read from the database as type String.  So the constructor
 * should us the strings to set the indexes.  However, the writes to the database are
 * done as ints representing the value of the Enum.  The JComboBox with the Enum has 
 * the strings as Items in the list with indexes equal to the database value of the int.
 * Such JComboBoxes are usually initialized with 
 * <br>
 * <br> FUtil.getEnumJComboBox(DBConnectionThread.getConnection("edge"), "tcpstation","rtsport1");
 * <br> Put this in "CodeGeneration"  "CustomCreateCode" of the form editor for each Enum.
 * <br>
 * <br> In  oldone() get Enum fields with something like :
 * <br>  fieldJcomboBox.setSelectedItem(obj.getString("fieldName"));
 * <br>  (This sets the JComboBox to the Item matching the string)
 * <br>
 * data class should have code like :
 * <br><br>  import java.util.ArrayList;          /// Import for Enum manipulation
 * <br>    .
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
 * 
 * @author D.C. Ketchum
 */
public final class SNWStationPanel extends javax.swing.JPanel {
  
  // NOTE : here define all variables general.  "Vector snwStations" is used for main Comboboz
  static ArrayList<SNWStation> snwStations;             // Vector containing objects of this SNWStation Type
  private DBObject obj;
  private final ErrorTrack err=new ErrorTrack();
  private boolean initiating=false;
  
  //USER: Here are the local variables
  private boolean metaUpdated;
  private int disableVal;
  private Date disableExpiresVal;
  private int latencySaveInt;       // transcribed value from latencySave
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
   if(disable.isSelected()) disableVal=1;
   else disableVal= 0;
   if(disableVal>0) {
     disableExpiresVal = FUtil.chkDate(disableExpires, err);
     disableComment.setText(disableComment.getText().replaceAll(";", " -"));
//     disableComment.setText(disableComment.getText(this));
   }
   else {
       disableComment.setText("");
       disableExpires.setText("");
   }
   latencySaveInt = FUtil.chkInt(latencySave,err,  0,7200);
   

   // Any errors found should err.set(true);  and err.append("error message");
   Util.prt("chkForm SNWStation");


    // No CHANGES : If we found an error, color up error box
    if(err.isSet()) {
      error.setText(err.getText());
      error.setBackground(UC.red);
      addUpdate.setEnabled(false);
    }
    else addUpdate.setEnabled(true);
    return err.isSet();
  }
  
  // This routine must set the form to initial state.  It does not update the JCombobox
  private void clearScreen() {
    
    // Do not change
    ID.setText("");
    UC.Look(error);
    error.setText("");
    addUpdate.setEnabled(false);
    addUpdate.setText("Enter a SNWStation");
    delete.setEnabled(false);
    
    //USER: Clear all fields for this form, this sets "defaults" for new screen
    //SNWStationPanel.setJComboBoxToID(snwstation, -1);
    //snwstation.setSelectedIndex(-1);
    metaUpdated=false;
    network.setText("");
    latencySave.setText("");
    description.setText("");
    latitude.setText("");
    longitude.setText("");
    elevation.setText("");
    helpstring.setText("");
    
    sort.setText("");
    process.setText("Enabled");
    cpu.setText("");
    disable.setText("");
    disableExpires.setText("");
    disableComment.setText("");
    snwrule.setSelectedIndex(-1);
    operator.setSelectedIndex(-1);
    protocol.setSelectedIndex(-1);
 }
 
  
  private SNWStation newOne() {
    return new SNWStation(0, ((String) snwstation.getSelectedItem()).toUpperCase()
            , ""  //network
            , ""  //description
            , 0.0 //latitude
            , 0.0 //longitude
            , 0.0 //elevation
            , 0L   //groupmask
            , 0   //snwruleid
            , 0   // operatorID
            ,0    // protocolID
            , ""  //helpstring
            , ""  //sort
            , ""  //process
            , ""  //cpu
            , 0   //disable
            , ""  //disableExpires
            , ""  //disableComment 
            , ""  //remote IP
            , ""  // remoteprocess
            , 120
       );
       
 
  }
  //makeSNWStation(inID, loc, "","",0.0,0.0,0.0,0,0,"","","","",0,"","" 
  private void oldOne(int id) throws SQLException {
    obj = new DBObject(DBConnectionThread.getThread(DBConnectionThread.getDBSchema()), DBConnectionThread.getDBSchema(), "snwstation",id);

    if(obj.isNew()) {
      Util.prt("object is new "+id);
      
    }
    else {
      //USER: Here set all of the form fields to data from the DBObject
      //      snwstation.setText(obj.getString("SNWStation"));
      // Example : description.setText(obj.getString("description"));
      network.setText(obj.getString("network"));
      latencySave.setText(obj.getInt("latencySaveInterval")+"");
      description.setText(obj.getString("description"));
      latitude.setText(""+obj.getDouble("latitude"));
      longitude.setText(""+obj.getDouble("longitude"));
      elevation.setText(""+obj.getDouble("elevation"));

      // Load group flags
      long groupmask=obj.getLong("groupmask");
      fpGroupMask.setAllClear();
      ArrayList fpList=fpGroupMask.getList();
      ArrayList radioList = fpGroupMask.getRadio();
      
      for(int i=0; i<radioList.size(); i++) {
         JRadioButton rad = (JRadioButton) radioList.get(i);
         SNWGroup grp = (SNWGroup) fpList.get(i);
         rad.setSelected( ((groupmask & (1L << (grp.getID()-1) )) != 0) );
         //if(rad.isSelected() ) groupmask |= 1L << (grp.getID()-1);
      }
      
      SNWRulePanel.setJComboBoxToID(snwrule,obj.getInt("snwruleID"));
      ProtocolPanel.setJComboBoxToID(protocol, obj.getInt("protocolID"));
      OperatorPanel.setJComboBoxToID(operator, obj.getInt("operatorID"));
      helpstring.setText(obj.getString("helpstring"));
      sort.setText(obj.getString("sort"));
      process.setText(obj.getString("process"));
      cpu.setText(obj.getString("cpu"));
      
      if(obj.getInt("disable")>0) disable.setSelected(true);
      else disable.setSelected(false);
      
      disableExpires.setText(obj.getString("disableExpires"));
      disableComment.setText(obj.getString("disableComment"));

      latencySummary.setText((obj.getTimestamp("latencytime").toString().substring(0,16))+"/"+obj.getShort("lastrecv")+"/"+obj.getShort("Latency"));
        
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
    labSNWStation = new javax.swing.JLabel();
    snwstation = getJComboBox();
    addUpdate = new javax.swing.JButton();
    ID = new javax.swing.JTextField();
    labID = new javax.swing.JLabel();
    error = new javax.swing.JTextField();
    labMask = new javax.swing.JLabel();
    labHelp = new javax.swing.JLabel();
    helpstring = new javax.swing.JTextField();
    gPanel = new javax.swing.JPanel();
    labNetwork = new javax.swing.JLabel();
    network = new javax.swing.JTextField();
    reload = new javax.swing.JButton();
    delete = new javax.swing.JButton();
    fixedPanel = new javax.swing.JPanel();
    labRuleID = new javax.swing.JLabel();
    labLatencySave = new javax.swing.JLabel();
    latencySave = new javax.swing.JTextField();
    labOperator = new javax.swing.JLabel();
    labProtocol = new javax.swing.JLabel();
    operator = OperatorPanel.getJComboBox();
    protocol = ProtocolPanel.getJComboBox();
    snwrule = SNWRulePanel.getJComboBox();
    belowPanel = new javax.swing.JPanel();
    labDesc = new javax.swing.JLabel();
    updateMeta = new javax.swing.JButton();
    description = new javax.swing.JTextField();
    labLat = new javax.swing.JLabel();
    latitude = new javax.swing.JTextField();
    labLong = new javax.swing.JLabel();
    longitude = new javax.swing.JTextField();
    labElev = new javax.swing.JLabel();
    elevation = new javax.swing.JTextField();
    labSort1 = new javax.swing.JLabel();
    sort = new javax.swing.JTextField();
    labProcess1 = new javax.swing.JLabel();
    process = new javax.swing.JTextField();
    labCPU1 = new javax.swing.JLabel();
    cpu = new javax.swing.JTextField();
    latencySummary = new javax.swing.JTextField();
    disablePanel = new javax.swing.JPanel();
    labDisable = new javax.swing.JLabel();
    disable = new javax.swing.JRadioButton();
    labDisableExpires = new javax.swing.JLabel();
    disableExpires = new javax.swing.JTextField();
    labDisableComment = new javax.swing.JLabel();
    disableComment = new javax.swing.JTextField();

    setLayout(new java.awt.GridBagLayout());

    labSNWStation.setText("Station:");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    add(labSNWStation, gridBagConstraints);

    snwstation.setEditable(true);
    snwstation.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        snwstationActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(snwstation, gridBagConstraints);

    addUpdate.setText("Add/Update");
    addUpdate.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        addUpdateActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 10;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(addUpdate, gridBagConstraints);

    ID.setColumns(8);
    ID.setEditable(false);
    ID.setMinimumSize(new java.awt.Dimension(84, 22));
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

    error.setColumns(40);
    error.setEditable(false);
    error.setMinimumSize(new java.awt.Dimension(488, 22));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(error, gridBagConstraints);

    labMask.setText("GroupMask:");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 4;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    add(labMask, gridBagConstraints);

    labHelp.setText("HelpString:");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 2;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    add(labHelp, gridBagConstraints);

    helpstring.setColumns(60);
    helpstring.setMinimumSize(new java.awt.Dimension(604, 22));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 2;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(helpstring, gridBagConstraints);

    gPanel.setBackground(new java.awt.Color(204, 255, 204));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 4;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(gPanel, gridBagConstraints);

    labNetwork.setText("Network:");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 3;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    add(labNetwork, gridBagConstraints);

    network.setColumns(2);
    network.setMinimumSize(new java.awt.Dimension(35, 22));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 3;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(network, gridBagConstraints);

    reload.setText("Reload Form");
    reload.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        reloadActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 22;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(reload, gridBagConstraints);

    delete.setText("Delete");
    delete.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        deleteActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 22;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(delete, gridBagConstraints);

    fixedPanel.setPreferredSize(new java.awt.Dimension(500, 60));
    fixedPanel.setLayout(new java.awt.GridBagLayout());

    labRuleID.setText("RuleID:");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    fixedPanel.add(labRuleID, gridBagConstraints);

    labLatencySave.setText("LatencySave(min):");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 2;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    fixedPanel.add(labLatencySave, gridBagConstraints);

    latencySave.setColumns(6);
    latencySave.setMinimumSize(new java.awt.Dimension(50, 22));
    latencySave.addFocusListener(new java.awt.event.FocusAdapter() {
      public void focusLost(java.awt.event.FocusEvent evt) {
        latencySaveFocusLost(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 3;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    fixedPanel.add(latencySave, gridBagConstraints);

    labOperator.setText("Operator:");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    fixedPanel.add(labOperator, gridBagConstraints);

    labProtocol.setText("Protocol:");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 2;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    fixedPanel.add(labProtocol, gridBagConstraints);

    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    fixedPanel.add(operator, gridBagConstraints);

    protocol.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        protocolActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 3;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    fixedPanel.add(protocol, gridBagConstraints);

    snwrule.setBackground(new java.awt.Color(255, 255, 255));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    fixedPanel.add(snwrule, gridBagConstraints);

    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 5;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(fixedPanel, gridBagConstraints);

    belowPanel.setMinimumSize(new java.awt.Dimension(600, 150));
    belowPanel.setPreferredSize(new java.awt.Dimension(600, 150));

    labDesc.setText("Description:                                                      ");
    belowPanel.add(labDesc);

    updateMeta.setText("ReFetch Coords/Names");
    updateMeta.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        updateMetaActionPerformed(evt);
      }
    });
    belowPanel.add(updateMeta);

    description.setColumns(50);
    description.setEditable(false);
    description.setMinimumSize(new java.awt.Dimension(504, 22));
    description.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        descriptionActionPerformed(evt);
      }
    });
    belowPanel.add(description);

    labLat.setText("Latitude:");
    belowPanel.add(labLat);

    latitude.setColumns(9);
    latitude.setEditable(false);
    latitude.setMinimumSize(new java.awt.Dimension(94, 22));
    latitude.addFocusListener(new java.awt.event.FocusAdapter() {
      public void focusLost(java.awt.event.FocusEvent evt) {
        latitudeFocusLost(evt);
      }
    });
    belowPanel.add(latitude);

    labLong.setText("Longitude");
    belowPanel.add(labLong);

    longitude.setColumns(9);
    longitude.setEditable(false);
    longitude.setMinimumSize(new java.awt.Dimension(94, 22));
    belowPanel.add(longitude);

    labElev.setText("Elevation:");
    belowPanel.add(labElev);

    elevation.setColumns(10);
    elevation.setEditable(false);
    elevation.setMinimumSize(new java.awt.Dimension(204, 22));
    belowPanel.add(elevation);

    labSort1.setText("Sort:");
    belowPanel.add(labSort1);

    sort.setBackground(new java.awt.Color(204, 204, 204));
    sort.setColumns(5);
    sort.setEditable(false);
    sort.setFont(new java.awt.Font("Monospaced", 0, 10)); // NOI18N
    sort.setMinimumSize(new java.awt.Dimension(20, 22));
    belowPanel.add(sort);

    labProcess1.setText("Proc:");
    belowPanel.add(labProcess1);

    process.setBackground(new java.awt.Color(204, 204, 204));
    process.setColumns(12);
    process.setEditable(false);
    process.setFont(new java.awt.Font("Monospaced", 0, 10)); // NOI18N
    process.setMinimumSize(new java.awt.Dimension(50, 22));
    belowPanel.add(process);

    labCPU1.setText("Node:");
    belowPanel.add(labCPU1);

    cpu.setBackground(new java.awt.Color(204, 204, 204));
    cpu.setColumns(6);
    cpu.setEditable(false);
    cpu.setFont(new java.awt.Font("Monospaced", 0, 10)); // NOI18N
    cpu.setMinimumSize(new java.awt.Dimension(30, 22));
    belowPanel.add(cpu);

    latencySummary.setBackground(new java.awt.Color(204, 204, 204));
    latencySummary.setColumns(40);
    latencySummary.setEditable(false);
    latencySummary.setFont(new java.awt.Font("Monospaced", 0, 10)); // NOI18N
    latencySummary.setMinimumSize(new java.awt.Dimension(114, 28));
    belowPanel.add(latencySummary);

    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 14;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(belowPanel, gridBagConstraints);

    disablePanel.setMinimumSize(new java.awt.Dimension(600, 60));
    disablePanel.setPreferredSize(new java.awt.Dimension(600, 60));

    labDisable.setText("Disable Monitor?");
    disablePanel.add(labDisable);

    disable.setBackground(new java.awt.Color(255, 255, 255));
    disable.setToolTipText("If set, then this station is disabled until the date given (if >2050/1/1) omit station for SNW");
    disable.setMargin(new java.awt.Insets(0, 0, 0, 0));
    disablePanel.add(disable);

    labDisableExpires.setText("Expire Date(=2050-01-01 omit station):");
    disablePanel.add(labDisableExpires);

    disableExpires.setColumns(12);
    disableExpires.setToolTipText("If the Disable is set, this sets the date the station will come off of disable. If >2050, then omit permanently from SNW.");
    disableExpires.setMinimumSize(new java.awt.Dimension(158, 22));
    disablePanel.add(disableExpires);

    labDisableComment.setText("Disable Comment:");
    disablePanel.add(labDisableComment);

    disableComment.setColumns(37);
    disableComment.setMinimumSize(new java.awt.Dimension(400, 22));
    disableComment.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        disableCommentActionPerformed(evt);
      }
    });
    disablePanel.add(disableComment);

    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 6;
    add(disablePanel, gridBagConstraints);
  }// </editor-fold>//GEN-END:initComponents

  private void latencySaveFocusLost(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_latencySaveFocusLost
// TODO add your handling code here:
    chkForm();
  }//GEN-LAST:event_latencySaveFocusLost

    private void deleteActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_deleteActionPerformed
// TODO add your handling code here:
      try {
        obj.deleteRecord();
        SNWStationPanel.reload();
        SNWStationPanel.getJComboBox(snwstation);
        snwstation.setSelectedIndex(-1);
        clearScreen();
      }
      catch(SQLException e) {Util.SQLErrorPrint(e,"Error deleting record");}
    }//GEN-LAST:event_deleteActionPerformed

    private void reloadActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_reloadActionPerformed
// TODO add your handling code here:
        SNWRulePanel.reload();
        SNWRulePanel.getJComboBox(snwrule);
        ProtocolPanel.reload();
        ProtocolPanel.getJComboBox(protocol);
        OperatorPanel.reload();
        OperatorPanel.getJComboBox(operator);
        SNWGroupPanel.reload();
        fpGroupMask = new FlagPanel(SNWGroupPanel.getJComboBox(), 600); 
        gPanel.removeAll();
        gPanel.add(fpGroupMask);
        UC.Look(gPanel);
        reload();
        getJComboBox(snwstation);
        snwstation.setSelectedIndex(-1);
        clearScreen();
    }//GEN-LAST:event_reloadActionPerformed

    private void disableCommentActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_disableCommentActionPerformed
// TODO add your handling code here:
    }//GEN-LAST:event_disableCommentActionPerformed

    private void descriptionActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_descriptionActionPerformed
// TODO add your handling code here:
    }//GEN-LAST:event_descriptionActionPerformed

    private void latitudeFocusLost(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_latitudeFocusLost
// TODO add your handling code here:
        chkForm();
    }//GEN-LAST:event_latitudeFocusLost

  private void addUpdateActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_addUpdateActionPerformed
    // Add your handling code here:
    
    // Do not change
    if(initiating) return;
    if(chkForm()) return;
    try {
      String p = snwstation.getSelectedItem().toString();
      p = p.toUpperCase();                  // USER : drop this to allow Mixed case
      if(p.indexOf("-") > 0) p = p.substring(0,p.indexOf("-"));
      if(!obj.isNew()) obj.refreshRecord();        // Refresh the result set so the sets will not fail on stale one
      obj.setString("snwstation",p);
      
      // USER : Set all of the fields in data base.  obj.setInt("thefield", ivalue);
      
      if(snwrule.getSelectedIndex() != -1) obj.setInt("snwruleid", ((SNWRule) snwrule.getSelectedItem()).getID());
      else obj.setInt("snwruleid",0);
      if(protocol.getSelectedIndex() != -1) obj.setInt("protocolid", ((Protocol) protocol.getSelectedItem()).getID());
      else obj.setInt("protocolid",0);
      if(operator.getSelectedIndex() != -1) obj.setInt("operatorid", ((Operator) operator.getSelectedItem()).getID());
      else obj.setInt("operatorid",0);
      obj.setString("helpstring",helpstring.getText());
      // GroupMask
      long groupmask = 0L;
      ArrayList fpList = fpGroupMask.getList();
      ArrayList radioList = fpGroupMask.getRadio();
      long disableMask=0;
      for(int i=0; i<radioList.size(); i++) {
         JRadioButton rad = (JRadioButton) radioList.get(i);
         SNWGroup grp = (SNWGroup) fpList.get(i);
         // rad.setSelected( ((groupmask & (1L << (grp.getID()-1) )) != 0) );
         if(rad.isSelected() ) groupmask |= 1L << (grp.getID()-1);
         if( ((SNWGroup) fpList.get(i)).getSNWGroup().equals("_Disable Monitor"))
           disableMask = 1L << (((SNWGroup) fpList.get(i)).getID()-1);
      }
      try ( // check to see if this is a ANSS.jar configured station and set certain groups per that
              Statement stmt = DBConnectionThread.getConnection("edge").createStatement(); 
              ResultSet rs = stmt.executeQuery("SELECT commlinkid,commlinkoverrideid,q330tunnel,usebh FROM anss.tcpstation WHERE tcpstation='"+p+"'")) {
        if(rs.next()) {
          int lastMile = rs.getInt("commlinkid");
          int backHaul = rs.getInt("commlinkoverrideid");
          boolean q330tunnel = rs.getString("q330tunnel").equals("True");
          int useBH = rs.getInt("usebh");
          ResultSet rs2 = stmt.executeQuery("SELECT quanterratype FROM anss.nsnstation where nsnstation='"+p+"'");
          String dasType="";
          if(rs2.next()) dasType = rs2.getString("quanterratype");
          long newGroupMask = SNWStation.setSNWGroupsForLinks(lastMile, backHaul, useBH, q330tunnel, dasType, groupmask);
          if(groupmask != newGroupMask) {
            Util.prt(p+" lastMile="+lastMile+" BH="+backHaul+" das="+dasType+" bef="+Util.toHex(groupmask)+" aft="+Util.toHex(newGroupMask));
            //JOptionPane.showMessageDialog(null,"ANss.jar mask is "+Util.toHex(newGroupMask)+" vs manual "+Util.toHex(groupmask));
          }
          groupmask = newGroupMask;
        }
      }
      if(disableVal != 0 &&
         disableExpiresVal.compareTo(new Date(System.currentTimeMillis())) > 0)
              groupmask |= disableMask;
      else groupmask &= ~disableMask;
      obj.setLong("groupmask",groupmask);
      
      if(disable.isSelected()) obj.setInt("disable", 1);
      else obj.setInt("disable", 0);
      
      obj.setString("network",network.getText());
      obj.setInt("latencySaveInterval",latencySaveInt);
      obj.setString("description",description.getText());
   //   SNWRulePanel.setJComboBoxToID(snwrule,obj.getInt("snwruleID");
      obj.setString("helpstring",helpstring.getText());
      obj.setInt("disable",disableVal);
      obj.setString("disableExpires",disableExpires.getText());
      obj.setString("disableComment",disableComment.getText());
      if(obj.isNew() || metaUpdated ) {
        obj.setDouble("latitude",Double.parseDouble(latitude.getText()));
        obj.setDouble("longitude",Double.parseDouble(longitude.getText()));
        obj.setDouble("elevation",Double.parseDouble(elevation.getText()));
      }
      
      // Do not change
      obj.updateRecord();
      snwStations=null;       // force reload of combo box
      getJComboBox(snwstation);
      clearScreen();
      snwstation.setSelectedIndex(-1);
    }  catch  (SQLException  E)
    { Util.SQLErrorPrint(E,"snwstation: update failed partno="+snwstation.getSelectedItem());
      error.setText("Could Not Update File!");
      error.setBackground(UC.red);
      addUpdate.setEnabled(true);
    }      
  }//GEN-LAST:event_addUpdateActionPerformed

  private void snwstationActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_snwstationActionPerformed
    // Add your handling code here:
   find();
   
 
  }//GEN-LAST:event_snwstationActionPerformed

  private void protocolActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_protocolActionPerformed
    // TODO add your handling code here:
  }//GEN-LAST:event_protocolActionPerformed

  private void updateMetaActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_updateMetaActionPerformed
    StaSrv stasrv = new StaSrv(null, 2052);

    double [] coord;
    String [] names;
    if(snwstation.getSelectedIndex() == -1) {
      UpdateFromMetadata.setMetaFromMDS(false, true, false);
      return;
    }
    else if(snwstation.getSelectedItem().toString().contains("-")) {
      String [] parts = snwstation.getSelectedItem().toString().split("-");
      network.setText(parts[1]);
      coord = stasrv.getMetaCoord((parts[1]+"  ").substring(0,2).toUpperCase()+parts[0].trim().toUpperCase());
      names = stasrv.getMetaComment((parts[1]+"  ").substring(0,2).toUpperCase()+parts[0].trim().toUpperCase());
      if(coord[0] == 0 || coord[1] == 0. || names[0].contains("unknown station")) {
        stasrv = new StaSrv("137.227.224.97", 2052);
        coord = stasrv.getCoord((parts[1]+"  ").substring(0,2).toUpperCase(), parts[0].trim().toUpperCase(),"  ");
        names = stasrv.getComment((parts[1]+"  ").substring(0,2).toUpperCase(), parts[0].trim().toUpperCase(), "  ");
      }
    }
    else {
      coord = stasrv.getMetaCoord(".."+snwstation.getSelectedItem().toString().toUpperCase());
      names = stasrv.getMetaComment(".."+snwstation.getSelectedItem().toString().toUpperCase());
   }
    names[0] = names[0].replaceAll("'","^");
    description.setText(names[0]);
    latitude.setText(""+coord[0]);
    longitude.setText(""+coord[1]);
    elevation.setText(""+coord[2]);
    metaUpdated=true;
  }//GEN-LAST:event_updateMetaActionPerformed
  
  
  // Variables declaration - do not modify//GEN-BEGIN:variables
  private javax.swing.JTextField ID;
  private javax.swing.JButton addUpdate;
  private javax.swing.JPanel belowPanel;
  private javax.swing.JTextField cpu;
  private javax.swing.JButton delete;
  private javax.swing.JTextField description;
  private javax.swing.JRadioButton disable;
  private javax.swing.JTextField disableComment;
  private javax.swing.JTextField disableExpires;
  private javax.swing.JPanel disablePanel;
  private javax.swing.JTextField elevation;
  private javax.swing.JTextField error;
  private javax.swing.JPanel fixedPanel;
  private javax.swing.JPanel gPanel;
  private java.awt.GridBagLayout gridBagLayout1;
  private javax.swing.JTextField helpstring;
  private javax.swing.JLabel labCPU1;
  private javax.swing.JLabel labDesc;
  private javax.swing.JLabel labDisable;
  private javax.swing.JLabel labDisableComment;
  private javax.swing.JLabel labDisableExpires;
  private javax.swing.JLabel labElev;
  private javax.swing.JLabel labHelp;
  private javax.swing.JLabel labID;
  private javax.swing.JLabel labLat;
  private javax.swing.JLabel labLatencySave;
  private javax.swing.JLabel labLong;
  private javax.swing.JLabel labMask;
  private javax.swing.JLabel labNetwork;
  private javax.swing.JLabel labOperator;
  private javax.swing.JLabel labProcess1;
  private javax.swing.JLabel labProtocol;
  private javax.swing.JLabel labRuleID;
  private javax.swing.JLabel labSNWStation;
  private javax.swing.JLabel labSort1;
  private javax.swing.JTextField latencySave;
  private javax.swing.JTextField latencySummary;
  private javax.swing.JTextField latitude;
  private javax.swing.JTextField longitude;
  private javax.swing.JTextField network;
  private javax.swing.JComboBox operator;
  private javax.swing.JTextField process;
  private javax.swing.JComboBox protocol;
  private javax.swing.JButton reload;
  private javax.swing.JComboBox snwrule;
  private javax.swing.JComboBox snwstation;
  private javax.swing.JTextField sort;
  private javax.swing.JButton updateMeta;
  // End of variables declaration//GEN-END:variables
  private FlagPanel fpGroupMask;
    /**
     * Creates new form SNWStationPanel.
     */
  public  SNWStationPanel() {
    initiating=true;
    initComponents();
    fpGroupMask = new FlagPanel(SNWGroupPanel.getJComboBox(), 600);
    UC.Look(fpGroupMask);
    gPanel.add(fpGroupMask);
    getJComboBox(snwstation);                // set up the key JComboBox
    snwstation.setSelectedIndex(-1);    // Set selected type
    Look();                    // Set color background
    initiating=false;
    UC.Look(fixedPanel);
    UC.Look(belowPanel);
    UC.Look(disablePanel);
    // USER:This is a good place for UC.Look(components) to get rid of the ugly Grey
    UC.Look(gPanel);
    
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
    makeSNWStations();
    for (int i=0; i< snwStations.size(); i++) {
      b.addItem(snwStations.get(i));
    }
    b.setMaximumRowCount(30);
  }
  
  /** Given a JComboBox from getJComboBox, set selected item to match database ID
   *@param b The JComboBox
   *@param ID the row ID from the database to select
   */
  public static void setJComboBoxToID(JComboBox b, int ID) {
    b.setSelectedIndex(-1);
    //Util.prt("SNWStationPanel.setJComboBoxToID id="+ID);
    for(int i=0; i<b.getItemCount(); i++) 
      if(((SNWStation) b.getItemAt(i)).getID() == ID) {
          b.setSelectedIndex(i);
          //Util.prt("SNWStationPanel.setJComboBoxToID id="+ID+" found at "+i);
      }
  }  
  
  /** Given a database ID find the index into a JComboBox consistent with this panel
   *@param ID The row ID from the database
   *@return The index in the JComboBox with this ID
   */
  public static int getJComboBoxIndex(int ID) {
    for(int i=0; i<snwStations.size(); i++) if( ID == ((SNWStation) snwStations.get(i)).getID()) return i;
    return -1;
  }
  /** Cause the main JComboBox to be reloaded. */
  public static void reload() {
    snwStations = null;
    makeSNWStations();
  }
  
  /**@return vector of SNWStations */
  public static  ArrayList getSNWStationVector() {
    if(snwStations == null) makeSNWStations();
    return snwStations;
  }
  /**@return vector of SNWStations */
  public ArrayList getVector() {
    if(snwStations == null) makeSNWStations();
    return snwStations;
  }
  
  /** Get the item corresponding to database ID
   *@param ID the database Row ID
   *@return The SNWStation row with this ID
   */
  public static SNWStation getItemWithID(int ID) {
    if (snwStations == null) makeSNWStations();
    int i=getJComboBoxIndex(ID);
    if(i >= 0) return (SNWStation) snwStations.get(i);
    else return null;
  }  
  public static SNWStation getItemAt(int index) {
    if (snwStations == null) makeSNWStations();
    int i = index;
    if(i >= 0) return (SNWStation) snwStations.get(i);
    else return null;
  }
  public static int getItemCount() {
    if (snwStations == null) makeSNWStations();
    return snwStations.size();
  }

  // This routine should only need tweeking if key field is not same as table name
  private static void makeSNWStations() {
    if (snwStations != null) return;
    snwStations=new ArrayList<SNWStation>(100);
    try {
      try (Statement stmt = DBConnectionThread.getConnection(DBConnectionThread.getDBSchema()).createStatement() // used for query
      ) {
        String s = "SELECT * FROM edge.snwstation ORDER BY snwstation;";
        try (ResultSet rs = stmt.executeQuery(s)) {
          while (rs.next()) {
            SNWStation loc = new SNWStation(rs);
            //        Util.prt("MakeSNWStation() i="+snwStations.size()+" is "+loc.getSNWStation());
            snwStations.add(loc);
          }
          rs.close();
        }
      }
    }
    catch (SQLException e) {
      Util.SQLErrorPrint(e,"makeSNWStations() on table SQL failed");
    }    
  }
  
  // No changes needed
  private void find() {
    if(snwstation == null) return;
    if(initiating) return;
    SNWStation l;
    if(snwstation.getSelectedIndex() == -1) {
      if(snwstation.getSelectedItem() == null) return;
      String match= (String) snwstation.getSelectedItem();
      match=match.toUpperCase();
      l = null;
      for(int i=0; i<snwstation.getItemCount(); i++) {
        if(snwstation.getItemAt(i).toString().contains(match)) {
          l=(SNWStation) snwstation.getItemAt(i);
          snwstation.setSelectedIndex(i);
          break;
        }
      }
      if(l == null) l = newOne();
    } 
    else {
      l = (SNWStation) snwstation.getSelectedItem();
    }
      
    if(l == null) return;
    String p = l.getSNWStation();
    if(p.indexOf(" ")>0){
        p=null; // snwstation name cannot have spaces
        error.setText("Spaces not allowed");
        error.setBackground(UC.yellow);
    }
    if(p == null) return;
    if(p.equals("")) {
      addUpdate.setEnabled(false);
      delete.setEnabled(false);
      addUpdate.setText("Enter a SNWStation!");
    }
    p = p.toUpperCase();
    error.setBackground(Color.lightGray);
    error.setText("");
    try {
      clearScreen();          // set screen to known state
      oldOne(l.getID());
      
      // set add/Update button to indicate an update will happen
      if(obj.isNew()) {
        clearScreen();
        ID.setText("NEW!");

        // Set up for "NEW" - clear fields etc.
        error.setText("NOT found - assume NEW");
        error.setBackground(UC.yellow);
        addUpdate.setText("Add "+p);
        addUpdate.setEnabled(true);
        StaSrv stasrv = new StaSrv("137.227.224.97", 2052);
        double [] coord;
        String [] names;
        if(((String) snwstation.getSelectedItem()).contains("-")) {
          String [] parts = ((String) snwstation.getSelectedItem()).split("-");
          network.setText(parts[1]);
          coord = stasrv.getMetaCoord((parts[1]+"..").substring(0,2).toUpperCase()+parts[0].trim().toUpperCase());
          names = stasrv.getMetaComment((parts[1]+"..").substring(0,2).toUpperCase()+((String) snwstation.getSelectedItem()).toUpperCase());
        }
        else {
          coord = stasrv.getMetaCoord(".."+((String) snwstation.getSelectedItem()).toUpperCase());
          names = stasrv.getMetaComment(".."+((String) snwstation.getSelectedItem()).toUpperCase());
       }
        names[0] = names[0].replaceAll("'","^");
        description.setText(names[0]);
        latitude.setText(""+coord[0]);
        longitude.setText(""+coord[1]);
        elevation.setText(""+coord[2]);
      }
      else {
        addUpdate.setText("Update "+p);
        delete.setEnabled(true);
        addUpdate.setEnabled(true);
        ID.setText(""+obj.getInt("ID"));    // Info only, show ID
      }
    }  
    // This is only thrown if something happens in oldone() new DBOject
    // Other than it being a new record.
    catch  (SQLException  E)                                 // 
    { Util.SQLErrorPrint(E,"SNWStation: SQL error getting SNWStation="+p);
    }
    
  }
  /** Allow setAnnsSNWGroupsFrom ANSS to be run from the command line. 
   * <PRE>
   * java -cp ~PATH/cwblib.jar gov.usgs.edge.snw.setAllSNWGroupsFromANSS [stations]
   * </PRE>
   * with no stations all station are done.
   * 
   * @param stations 
   */
  public static void setAllSNWGroupsFromANSS(String [] stations) {
    if(stations.length == 0) setSNWGroupsFromANSS("");
    else {
      for (String station : stations) {
        setSNWGroupsFromANSS(station);
      }
    }
    System.exit(0);
  }
  static public void setSNWGroupsFromANSS(String station) {
    int nc=0;
    try {
      DBConnectionThread dbconn = DBConnectionThread.getThread("anss");
      if(dbconn == null) dbconn = DBConnectionThread.getThread("edge");
      if(dbconn == null) {Util.prt("setSNWGroups from ANSS does not have a DB connection!"); System.exit(1);}
      
      Statement stmt2 = dbconn.getConnection().createStatement();
      Statement stmt3 = dbconn.getConnection().createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE);
      ResultSet rss = stmt2.executeQuery("SELECT tcpstation from anss.tcpstation "+
          (station.equals("")?"":" WHERE tcpstation='"+station.trim()+"'"));
      while(rss.next()) {
        
        String p = rss.getString("tcpstation");
        try (ResultSet rss2 = stmt3.executeQuery("SELECT ID,groupmask,protocolid,operatorid FROM edge.snwstation where snwstation='"+p+"'")) {
          if(rss2.next()) {
            long groupmask = rss2.getLong("groupmask");
            int protocolID =rss2.getInt("protocolid");
            int operatorID = rss2.getInt("operatorid");
            int ID = rss2.getInt("ID");
            Statement stmt = dbconn.getConnection().createStatement();
            try (ResultSet rs = stmt.executeQuery("SELECT commlinkid,commlinkoverrideid,q330tunnel,usebh FROM anss.tcpstation WHERE tcpstation='"+p+"'")) {
              if(rs.next()) {
                int lastMile = rs.getInt("commlinkid");
                int backHaul = rs.getInt("commlinkoverrideid");
                boolean q330tunnel=rs.getString("q330tunnel").equals("True");
                int useBH = rs.getInt("usebh");
                ResultSet rs2 = stmt.executeQuery("SELECT quanterratype FROM anss.nsnstation where nsnstation='"+p+"'");
                String dasType="";
                if(rs2.next()) dasType = rs2.getString("quanterratype");
                long newGroupMask = SNWStation.setSNWGroupsForLinks(lastMile, backHaul, useBH, q330tunnel, dasType, groupmask);
                int newProtocol = 0;
                if(dasType.equals("Q680")) newProtocol=16;
                if(dasType.contains("Q330")) newProtocol=11;
                if(dasType.indexOf("Q730") >0) newProtocol=12;
                
                if(groupmask != newGroupMask || (newProtocol != protocolID && newProtocol !=0) || operatorID != 19) {
                  Util.prt(p+" lastMile="+lastMile+" BH="+backHaul+" das="+dasType+
                          " bef="+Util.toHex(groupmask)+" aft="+Util.toHex(newGroupMask)+
                          " prot="+protocolID+" now "+newProtocol+" operator=19 was "+operatorID);
                  nc++;
                  stmt3.executeUpdate("UPDATE edge.snwstation set groupmask="+newGroupMask+",operatorid=19"+
                          (newProtocol == 0? "":",protocolid="+newProtocol)+" WHERE ID="+ID);
                  stmt3.executeUpdate(
                          "UPDATE edge.channel,edge.snwstation set channel.operatorid=snwstation.operatorid,"+
                                  "channel.protocolid=snwstation.protocolid "+
                                  "WHERE snwstation.id="+ID+" AND substring(channel,1,2)=snwstation.network "+
                                          "AND trim(substring(channel,3,5))=snwstation.snwstation");           }
              }
            }
          }
        }
      }
      if(station.equals("")) {
        // transfer operator, and protocol ids to the channel table from the snwstation table for convenience
        int nupd = stmt3.executeUpdate(
              "UPDATE edge.channel,edge.snwstation set channel.operatorid=snwstation.operatorid,"+
              "channel.protocolid=snwstation.protocolid "+
              "WHERE snwstation.network=substring(channel,1,2) AND snwstation.snwstation=trim(substring(channel,3,5))");
        
        // For all configured refteks, set the snwgroup to include Refteck and set the SNWRuleID to the Reftek rule
        int nupd2 = stmt3.executeUpdate("UPDATE edge.snwstation SET groupmask = groupmask | 1<<21, snwruleid=5 " +
              "WHERE (snwruleid!=5 OR groupmask & 1<<21 = 0)  AND TRIM(CONCAT(network,snwstation)) IN " +
              "(SELECT TRIM(CONCAT(network,station)) as rstat FROM anss.reftek " +
              "WHERE enddate > now() AND NOT network IN ('XX','ZZ') ORDER BY network,station)");
        Util.prt("#updated protocols/operators="+nupd+" #Reftek updates="+nupd2);
      }
    }
    catch(SQLException e) {
      Util.SQLErrorPrint(e, "looking for differences SNWStationPanel");
      e.printStackTrace();
    }
    Util.prt("#changed="+nc);
  }
/** This main displays the form Pane by itself
   *@param args command line args ignored*/
  public static void main(String args[]) {
    DBConnectionThread jcjbl;
    Util.init(UC.getPropertyFilename());
    UC.init();
    DBConnectionThread.init(Util.getProperty("DBServer"));
    Util.prt(Util.getProperty("DBServer")+" DBC.getDBServer="+DBConnectionThread.getDBServer()+" UC.getProp="+UC.getPropertyFilename());
    try {
        // Make test DBconnection for form
      jcjbl = new DBConnectionThread(DBConnectionThread.getDBServer(),DBConnectionThread.getDBCatalog(),
              UC.defaultUser(),UC.defaultPassword(), true, false, DBConnectionThread.getDBSchema(), DBConnectionThread.getDBVendor());
      if(!DBConnectionThread.waitForConnection(DBConnectionThread.getDBSchema()))
        if(!DBConnectionThread.waitForConnection(DBConnectionThread.getDBSchema()))
          if(!DBConnectionThread.waitForConnection(DBConnectionThread.getDBSchema()))
          {Util.prt("Could not connect to DB "+jcjbl); System.exit(1);}
      for (String arg : args) {
        if (arg.equals("-setsnwgroups")) {
          String [] tmp = new String[1];
          tmp[0]="";
          SNWStationPanel.setAllSNWGroupsFromANSS(tmp);
          System.exit(0);
        }
      }      
      Show.inFrame(new SNWStationPanel(), UC.XSIZE, UC.YSIZE);
    }
    catch(InstantiationException e) {}
  }
}
