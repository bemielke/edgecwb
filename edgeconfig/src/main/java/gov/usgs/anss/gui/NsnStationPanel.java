/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */

package gov.usgs.anss.gui;

/** This class is likely obsolted by the retirement of Q680 and Q730 from the ANSS backbone.
 *
 * @author  ketchum
 * requirements : 
 * The key for the file must be the name of the table and of the
 * JComboBox variable this variable is called "location" in the initial form
 * The table name and key name should match and start lower case (nsnstation).
 * The Class here will be that same name but with upper case at beginning(NsnStation).
 * 1)  Rename the location JComboBox to the "key" (nsnstation) value.  This should start Lower
 *      case.
 * 2)  The table name should match this key name and be all lower case.
 * 3)  Change all NsnStation to ClassName of underlying data (case sensitive!)
 * 4)  Change all nsnstation key value (the JComboBox above)
 * 5)  clearScreen should update swing components to new defaults
 * 6)  chkForm should check all field for validity.  The errorTrack variable is
 *      used to track multiple errors.  In FUtil.chk* handle checks of the various
 *      variable types.  Date, double, and IP etc return the "canonical" form for 
 *      the variable.  These should be stored in a class variable so the latest one
 *      from "chkForm()" are available to the update writer.
 * 7) newone() should be updated to create a new instance of the underlying data class.
 * 8) oldone() get data from database and update all swing elements to correct values
 * 9) addUpdateAction() add data base set*() operations updating all fields in the DB
 *10) NsnStationPanel() constructor - good place to change backgrounds using UC.Look() any
 *    other weird startup stuff.
 *
 * local variable error - Must be a JTextField for posting error communications
 * local variable updateAdd - Must be the JButton for user clicks to try to post the data
 * ID - JTextField which must be non-editable for posting Database IDs
 * 
 *ENUM notes :  the ENUM are read from the database as type String.  So the constructor
 * should us the strings to set the indexes.  However, the writes to the database are
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

import gov.usgs.anss.guidb.TCPStation;
import gov.usgs.anss.guidb.NsnStation;
import gov.usgs.anss.guidb.DasConfig;
import gov.usgs.anss.db.DBConnectionThread;
import gov.usgs.anss.db.DBObject;
import gov.usgs.anss.util.*;
import gov.usgs.edge.snw.SNWStationPanel;
import java.awt.Color;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import javax.swing.JComboBox;
import javax.swing.JOptionPane;

public final  class NsnStationPanel extends javax.swing.JPanel {
  
  // NOTE : here define all variables general.  "ArrayList v" is used for main Comboboz
  static ArrayList<NsnStation> v;             // ArrayList containing objects of this NsnStation Type
  DBObject obj;
  ErrorTrack err=new ErrorTrack();
  boolean initiating;
  
  // Here are the local variables
   int netidVal, nodeidVal, tstartVal, dac480Val, attn480Val, expbiasVal;
   static ArrayList quanterraTypeEnum,stationTypeEnum,trueFalseEnum, seismometerEnum, calEnableEnum;
  
   
   // In order to track whether the configuration for Inv.jar has changed, save starting
   String startingQuanterraType,startingHighGain,startingLowGain,startingCont40,
           startingCont20, startingCalEnable;
   int startingDasConfig, startingAUXAD, startingAUXCH2Battery;
   String startingSeedNetwork;
   String template;
   boolean startingLowPower;
   DisplayPopup popup = new DisplayPopup();


  // This routine must validate all fields on the form.  The err (ErrorTrack) variable
  // is used by the FUtil to verify fields.  Use FUTil or custom code here for verifications
  private boolean chkForm() {
    // Do not change
    if(initiating) return false;
   err.reset();
   UC.Look(error);
   error.setText("");
   
   // Your error checking code goes here setting the local variables to valid settings
   // note : Use FUtil.chk* to check dates, timestamps, IP addresses, etc and put them in
   // canonical form.  store in a class variable.  For instance :
   //   sdate = FUtil.chkDate(dateTextField, err);
   // Any errors found should err.set(true);  and err.append("error message");
   //Util.prt("chkForm NsnStation");
   String [] unit=new String[1];
   if(nsnstation.getSelectedIndex() != -1) unit = nsnstation.getSelectedItem().toString().split(":");
   
   nodeidVal = FUtil.chkInt(nodeid, err, 0, 255);
   netidVal = FUtil.chkInt(netid, err, 0, 255);
   tstartVal = FUtil.chkInt(startTbase, err, 1000, 1900);
   dac480Val = FUtil.chkInt(dac480, err, 0,4);
   attn480Val = FUtil.chkInt(attn480, err, -1000000, 1000000);
   expbiasVal = FUtil.chkInt(expbias, err, 1, 32);
   FUtil.chkJComboBox(stationType, "Station Type", err);
   FUtil.chkJComboBox(quanterraType, "Quanterra Type", err);
   if(quanterraType.getSelectedItem().toString().indexOf("Q330") >= 0) {
   // FUtil.chkJComboBox(dasconfig,"DAS Config", err);
    if(stationType.getSelectedItem().toString().indexOf("Q330") < 0) {
      err.set(); err.appendText("Q330 stations and quanterra type not both Q330");
    }
   }
   if(stationType.getSelectedItem().toString().indexOf("Q330") >=0 &&
       quanterraType.getSelectedItem().toString().indexOf("Q330") < 0) {
      err.set(); err.appendText("Q330 stations and quanterra type not both Q330");     
   }
   else {   // station type is not Q330, if its nsn, make sure a Q680 or Q730 is in quanter type
     if(stationType.getSelectedItem().toString().equals("NSN") &&
         !quanterraType.getSelectedItem().toString().equals("Q680") &&
         !quanterraType.getSelectedItem().toString().equals("Q730") ) {
       err.set(); err.appendText("Station type NSN must be Q680 or Q730");
     }
     
   }
   FUtil.chkJComboBox(seismometer, "Seismometer Type", err);
   FUtil.chkJComboBox(highgain, "Highgain Present", err);
   FUtil.chkJComboBox(lowgain, "Lowgain Present", err);
   FUtil.chkJComboBox(cont40, "Continuous 40 Hz", err);
   FUtil.chkJComboBox(cont20, "Continuous 20 Hz", err);
   FUtil.chkJComboBox(calEnable, "Cal Enable", err);
   template="";
   if(quanterraType.getSelectedItem().equals("Q330TA")) template+="TA_";
   else if(quanterraType.getSelectedItem().equals("Q330SR")) template+="SR_";
   else if(quanterraType.getSelectedItem().equals("Q330HR")) template+="HR_";
   if(!template.equals("")) {
     if(unit.length == 2) template+=unit[1]+"_";
     //if(lowPower.isSelected()) template+="LP_";
     if(cont40.getSelectedItem().equals("True") && highgain.getSelectedItem().equals("True")) template+="40HZ_";
     if(cont20.getSelectedItem().equals("True") && highgain.getSelectedItem().equals("True")) template+="20HZ_";
     //if(highgain.getSelectedItem().equals("True")) template+="HG_";
     if(!lowgain.getSelectedItem().equals("True")&& template.substring(0,2).equals("SR")) template+="NOSM_";
     if(!highgain.getSelectedItem().equals("True")  ) template+="NOHG_";
   }
   if(template.length() >3) template = template.substring(0,template.length()-1);
   configTag.setText(template);
    String q330config;
    if(quanterraType.getSelectedItem().toString().indexOf("Q330") == 0)  {
      q330config= configTag.getText();
      dasconfig.setSelectedIndex(-1);
      for(int i=0; i<dasconfig.getItemCount(); i++) {
        if( q330config.equals(dasconfig.getItemAt(i).toString()))
          dasconfig.setSelectedIndex(i);

      }
      if(dasconfig.getSelectedIndex() < 0) {
        Util.prt(" ********* Could not find a config named ="+q330config);
        err.appendText("No config "+q330config);
        err.set();
      }
    }
    // No CHANGES : If we found an error, color up error box
    if(err.isSet()) {
      error.setText(err.getText());
      error.setBackground(UC.red);
      addUpdate.setEnabled(false);
    } 
    else {
     addUpdate.setEnabled(true);
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
    addUpdate.setText("Enter a NsnStation");

    
    
    // Clear all fields for this form, this sets "defaults" for new screen
    netid.setText("");
    nodeid.setText("");
    stationType.setSelectedIndex(1);
    quanterraType.setSelectedIndex(1);
    dasconfig.setSelectedIndex(-1);
    seismometer.setSelectedIndex(-1);
    highgain.setSelectedIndex(2);
    lowgain.setSelectedIndex(2);
    cont40.setSelectedIndex(2);
    cont20.setSelectedIndex(1);
    calEnable.setSelectedIndex(1);
    auxAD.setSelected(false);
    auxADCH2Battery.setSelected(false);
    dac480.setText("0");
    attn480.setText("0");
    expbias.setText("16");
    startTbase.setText("1500");    
    startingQuanterraType="";startingHighGain="";startingLowGain="";
    startingDasConfig = 0;
    startingCont40="";
    startingCont20="";
    startingCalEnable="";
    startingSeedNetwork="";
    startingLowPower=false;
    startingAUXAD=0;
    startingAUXCH2Battery=0;
    dasconfig.setEnabled(false);
    dasconfig.setVisible(false);
    labDasconfig.setVisible(false);
    dasText.setVisible(false);
    labDasconfig.setVisible(false);
    labCont20.setVisible(false);
    labCont40.setVisible(false);
    cont40.setVisible(false);
    cont20.setVisible(false);
    labcalEnable.setVisible(false);
    calEnable.setVisible(false);
    auxAD.setVisible(false);
    auxADCH2Battery.setVisible(false);
    lab480.setVisible(false);
    dac480.setVisible(false);
    labAttn480.setVisible(false);
    attn480.setVisible(false);
    labStartTbase.setVisible(false);
    startTbase.setVisible(false);
    lowPower.setVisible(false);
      
  }
 
  
  private NsnStation newOne() {
    startingQuanterraType="";
    startingDasConfig=0;
    startingSeedNetwork="";
    return new NsnStation(0, ((String) nsnstation.getSelectedItem()).toUpperCase(),
         0,  0,  1500,  1,  0,  16,
         "Nsn",  1,  1, 10000, 0,  1,  "Unknown", "Q680",  0, 0,
         0,"","On","12","","Seismometer", 0, 0);
  }
  
  private void oldOne(String p) throws SQLException {
    obj = new DBObject(DBConnectionThread.getThread(DBConnectionThread.getDBSchema()), DBConnectionThread.getDBSchema(), "nsnstation","nsnstation",p);

    if(obj.isNew()) {
      Util.prt("object is new"+p);
    }
    else {
      // Here set all of the form fields to data from the DBObject
      //      nsnstation.setText(obj.getString("NsnStation"));
      // Example : description.setText(obj.getString("description"));
      netid.setText(obj.getInt("netid")+"");
      nodeid.setText(obj.getInt("nodeid")+"");
      stationType.setSelectedItem(obj.getString("stationtype"));
      quanterraType.setSelectedItem(obj.getString("quanterraType"));
      DasConfigPanel.setJComboBoxToID(dasconfig,obj.getInt("dasconfigid"));
      seismometer.setSelectedItem(obj.getString("seismometer"));
      highgain.setSelectedItem(obj.getString("highgain"));
      lowgain.setSelectedItem(obj.getString("lowgain"));
      cont40.setSelectedItem(obj.getString("cont40"));
      cont20.setSelectedItem(obj.getString("cont20"));
      calEnable.setSelectedItem(obj.getString("calenable"));
      if(obj.getInt("auxad") == 0) {auxAD.setSelected(false);startingAUXAD=0;}
      else {auxAD.setSelected(true);startingAUXAD=1;}
      if(obj.getInt("aux2isbattery") == 0) {auxADCH2Battery.setSelected(false);startingAUXCH2Battery=0;}
      else {auxADCH2Battery.setSelected(true);startingAUXCH2Battery=1;}
      if(auxAD.isSelected()) auxADCH2Battery.setVisible(true);
      dac480.setText(""+obj.getInt("dac480"));
      attn480.setText(""+obj.getInt("attn480"));
      expbias.setText(""+obj.getInt("expbias"));
      startTbase.setText(""+obj.getInt("startTbase"));
      startingQuanterraType=obj.getString("QuanterraType");
      startingDasConfig = obj.getInt("dasconfigid");
      startingSeedNetwork=obj.getString("seednetwork");
      seedNet.setText(startingSeedNetwork);
      overrideConfig.setText(obj.getString("overridetemplate"));
      lowPower.setSelected(obj.getString("gpsconfig").equals("Cycled"));
      startingLowPower=obj.getString("gpsconfig").equals("Cycled");
      startingHighGain=obj.getString("highgain");
      startingLowGain=obj.getString("lowgain");
      startingCont40=obj.getString("cont40");
      startingCont20=obj.getString("cont20");
      startingCalEnable=obj.getString("calenable");
      chkForm();
        
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
        nsnstation = getJComboBox();
        addUpdate = new javax.swing.JButton();
        ID = new javax.swing.JTextField();
        jLabel9 = new javax.swing.JLabel();
        error = new javax.swing.JTextField();
        labnet = new javax.swing.JLabel();
        netid = new javax.swing.JTextField();
        labNode = new javax.swing.JLabel();
        nodeid = new javax.swing.JTextField();
        labStationTp = new javax.swing.JLabel();
        lab480 = new javax.swing.JLabel();
        dac480 = new javax.swing.JTextField();
        labAttn480 = new javax.swing.JLabel();
        attn480 = new javax.swing.JTextField();
        labExpbias = new javax.swing.JLabel();
        expbias = new javax.swing.JTextField();
        labStartTbase = new javax.swing.JLabel();
        startTbase = new javax.swing.JTextField();
        labSeis = new javax.swing.JLabel();
        seismometer = FUtil.getEnumJComboBox(DBConnectionThread.getConnection("anss"), "nsnstation","seismometer");
        labQType = new javax.swing.JLabel();
        quanterraType = FUtil.getEnumJComboBox(DBConnectionThread.getConnection("anss"), "nsnstation","quanterratype");
        stationType = FUtil.getEnumJComboBox(DBConnectionThread.getConnection("anss"), "nsnstation","stationtype");
        dasText = new javax.swing.JTextArea();
        configPanel = new javax.swing.JPanel();
        labLowgain = new javax.swing.JLabel();
        lowgain = FUtil.getEnumJComboBox(DBConnectionThread.getConnection("anss"), "nsnstation","lowgain");
        labHighgain = new javax.swing.JLabel();
        highgain = FUtil.getEnumJComboBox(DBConnectionThread.getConnection("anss"), "nsnstation","highgain");
        labcalEnable = new javax.swing.JLabel();
        calEnable = FUtil.getEnumJComboBox(DBConnectionThread.getConnection("anss"), "nsnstation","calenable");
        auxAD = new javax.swing.JRadioButton();
        forceDownload = new javax.swing.JRadioButton();
        auxADCH2Battery = new javax.swing.JRadioButton();
        lowPower = new javax.swing.JRadioButton();
        labCont40 = new javax.swing.JLabel();
        cont40 = FUtil.getEnumJComboBox(DBConnectionThread.getConnection("anss"), "nsnstation","cont40");
        labCont20 = new javax.swing.JLabel();
        cont20 = FUtil.getEnumJComboBox(DBConnectionThread.getConnection("anss"), "nsnstation","cont20");
        labDasconfig = new javax.swing.JLabel();
        dasconfig = DasConfigPanel.getJComboBox();
        labSeedNet = new javax.swing.JLabel();
        seedNet = new javax.swing.JTextField();
        labConfig = new javax.swing.JLabel();
        configTag = new javax.swing.JTextField();
        labOverride = new javax.swing.JLabel();
        overrideConfig = new javax.swing.JTextField();

        setLayout(new java.awt.GridBagLayout());

        jLabel1.setText("Station :");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
        add(jLabel1, gridBagConstraints);

        nsnstation.setEditable(true);
        nsnstation.setMinimumSize(new java.awt.Dimension(70, 28));
        nsnstation.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                nsnstationActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        add(nsnstation, gridBagConstraints);

        addUpdate.setText("Add/Update");
        addUpdate.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                addUpdateActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 19;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        add(addUpdate, gridBagConstraints);

        ID.setColumns(8);
        ID.setEditable(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 20;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        add(ID, gridBagConstraints);

        jLabel9.setText("ID :");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 20;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
        add(jLabel9, gridBagConstraints);

        error.setColumns(40);
        error.setEditable(false);
        error.setMinimumSize(new java.awt.Dimension(488, 22));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        add(error, gridBagConstraints);

        labnet.setText("Net ID :");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
        add(labnet, gridBagConstraints);

        netid.setColumns(5);
        netid.setMinimumSize(new java.awt.Dimension(70, 22));
        netid.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusLost(java.awt.event.FocusEvent evt) {
                netidFocusLost(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        add(netid, gridBagConstraints);

        labNode.setText("Node ID :");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
        add(labNode, gridBagConstraints);

        nodeid.setColumns(5);
        nodeid.setMinimumSize(new java.awt.Dimension(70, 22));
        nodeid.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusLost(java.awt.event.FocusEvent evt) {
                nodeidFocusLost(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        add(nodeid, gridBagConstraints);

        labStationTp.setText("Station Type :");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 4;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
        add(labStationTp, gridBagConstraints);

        lab480.setText("Dac480 :");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 7;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
        add(lab480, gridBagConstraints);

        dac480.setColumns(5);
        dac480.setMinimumSize(new java.awt.Dimension(40, 22));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 7;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        add(dac480, gridBagConstraints);

        labAttn480.setText("Attn 480 (+\\mult, -=divide)");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 8;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
        add(labAttn480, gridBagConstraints);

        attn480.setColumns(8);
        attn480.setMinimumSize(new java.awt.Dimension(40, 22));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 8;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        add(attn480, gridBagConstraints);

        labExpbias.setText("Exp BIas (16) :");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 9;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
        add(labExpbias, gridBagConstraints);

        expbias.setColumns(5);
        expbias.setMinimumSize(new java.awt.Dimension(60, 22));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 9;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        add(expbias, gridBagConstraints);

        labStartTbase.setText("Start TBase :(1000-1900):");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 10;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
        add(labStartTbase, gridBagConstraints);

        startTbase.setColumns(8);
        startTbase.setMinimumSize(new java.awt.Dimension(60, 22));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 10;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        add(startTbase, gridBagConstraints);

        labSeis.setText("Seismometer : ");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 6;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
        add(labSeis, gridBagConstraints);

        seismometer.setMinimumSize(new java.awt.Dimension(100, 27));
        seismometer.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                seismometerActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 6;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        add(seismometer, gridBagConstraints);

        labQType.setText("Quanterra Type :");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 5;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
        add(labQType, gridBagConstraints);

        quanterraType.setMinimumSize(new java.awt.Dimension(100, 27));
        quanterraType.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                quanterraTypeActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 5;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        add(quanterraType, gridBagConstraints);

        stationType.setMinimumSize(new java.awt.Dimension(100, 27));
        stationType.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                stationTypeActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 4;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        add(stationType, gridBagConstraints);

        dasText.setBackground(new java.awt.Color(192, 192, 192));
        dasText.setColumns(80);
        dasText.setEditable(false);
        dasText.setFont(new java.awt.Font("Courier New", 0, 12)); // NOI18N
        dasText.setLineWrap(true);
        dasText.setRows(5);
        dasText.setMinimumSize(new java.awt.Dimension(490, 40));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 13;
        add(dasText, gridBagConstraints);

        configPanel.setBorder(javax.swing.BorderFactory.createEtchedBorder());
        configPanel.setMinimumSize(new java.awt.Dimension(500, 250));
        configPanel.setPreferredSize(new java.awt.Dimension(575, 250));
        configPanel.setLayout(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT));

        labLowgain.setText("LowGain :");
        configPanel.add(labLowgain);

        lowgain.setMinimumSize(new java.awt.Dimension(100, 27));
        lowgain.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                lowgainActionPerformed(evt);
            }
        });
        configPanel.add(lowgain);

        labHighgain.setText("HiGain :");
        configPanel.add(labHighgain);

        highgain.setMinimumSize(new java.awt.Dimension(100, 27));
        highgain.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                highgainActionPerformed(evt);
            }
        });
        configPanel.add(highgain);

        labcalEnable.setText("CalEnable:");
        configPanel.add(labcalEnable);

        configPanel.add(calEnable);

        auxAD.setText("AuxAD present and hooked up");
        auxAD.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                auxADActionPerformed(evt);
            }
        });
        configPanel.add(auxAD);

        forceDownload.setText("Force Download/Resp Update");
        configPanel.add(forceDownload);

        auxADCH2Battery.setText("AUXAD 2 is Battery not HG Temp");
        configPanel.add(auxADCH2Battery);

        lowPower.setText("Set Low Power ( Cycle GPS and Baler power - Solar Powered sites )                          ");
        lowPower.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                lowPowerActionPerformed(evt);
            }
        });
        configPanel.add(lowPower);

        labCont40.setText("Continuous 40 Hz :");
        configPanel.add(labCont40);

        cont40.setMinimumSize(new java.awt.Dimension(100, 27));
        cont40.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                cont40ActionPerformed(evt);
            }
        });
        configPanel.add(cont40);

        labCont20.setText("Continuous 20 Hz :");
        configPanel.add(labCont20);

        cont20.setMinimumSize(new java.awt.Dimension(100, 27));
        cont20.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                cont20ActionPerformed(evt);
            }
        });
        configPanel.add(cont20);

        labDasconfig.setText("DAS Configuration:");
        configPanel.add(labDasconfig);

        dasconfig.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                dasconfigActionPerformed(evt);
            }
        });
        configPanel.add(dasconfig);

        labSeedNet.setText("SeedNetwork:");
        configPanel.add(labSeedNet);

        seedNet.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                seedNetActionPerformed(evt);
            }
        });
        configPanel.add(seedNet);

        labConfig.setText("AutoConfig:");
        configPanel.add(labConfig);

        configTag.setBackground(new java.awt.Color(204, 204, 204));
        configTag.setColumns(30);
        configTag.setEditable(false);
        configTag.setMinimumSize(new java.awt.Dimension(384, 28));
        configPanel.add(configTag);

        labOverride.setText("OverrideConfiguration:");
        configPanel.add(labOverride);

        overrideConfig.setColumns(30);
        overrideConfig.setMinimumSize(new java.awt.Dimension(384, 28));
        configPanel.add(overrideConfig);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 14;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        add(configPanel, gridBagConstraints);
    }// </editor-fold>//GEN-END:initComponents

  private void seedNetActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_seedNetActionPerformed
// TODO add your handling code here:
  }//GEN-LAST:event_seedNetActionPerformed

  private void nodeidFocusLost(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_nodeidFocusLost
// TODO add your handling code here:
    chkForm();
  }//GEN-LAST:event_nodeidFocusLost

  private void netidFocusLost(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_netidFocusLost
// TODO add your handling code here:
    chkForm();
  }//GEN-LAST:event_netidFocusLost

  private void seismometerActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_seismometerActionPerformed
// TODO add your handling code here:
    chkForm();
  }//GEN-LAST:event_seismometerActionPerformed

  private void stationTypeActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_stationTypeActionPerformed

    chkForm();
  }//GEN-LAST:event_stationTypeActionPerformed

  private void dasconfigActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_dasconfigActionPerformed
// TODO add your handling code here:

    //chkForm();
    if(dasconfig.getSelectedIndex() >=0 ) 
      dasText.setText( ((DasConfig) dasconfig.getSelectedItem()).getText());
    //dasScroll.getViewport().setViewPosition(new Point(0,0));
  }//GEN-LAST:event_dasconfigActionPerformed

  private void quanterraTypeActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_quanterraTypeActionPerformed
// TODO add your handling code here:
    if(initiating) return;
    if(quanterraType.getSelectedItem().toString().indexOf("Q330") >=0) {
      dasconfig.setEnabled(false);
      dasconfig.setVisible(true);
      labDasconfig.setVisible(true);
      labSeedNet.setVisible(true);
      labExpbias.setVisible(false);
      labnet.setVisible(false);
      netid.setVisible(false);
      labNode.setVisible(false);
      nodeid.setVisible(false);
      expbias.setVisible(false);
      seedNet.setVisible(true);
      dasText.setVisible(true);
      lowPower.setVisible(true);
      labCont20.setVisible(true);
      labCont40.setVisible(true);
      labcalEnable.setVisible(true);
      calEnable.setVisible(true);
      auxAD.setVisible(true);
      if(auxAD.isSelected()) auxADCH2Battery.setVisible(true);
      cont40.setVisible(true);
      cont20.setVisible(true);
      configTag.setVisible(true);
      labConfig.setVisible(true);
      labOverride.setVisible(true);
      overrideConfig.setVisible(true);
      lab480.setVisible(false);
      dac480.setVisible(false);
      labAttn480.setVisible(false);
      attn480.setVisible(false);
      labStartTbase.setVisible(false);
      startTbase.setVisible(false);
      
    }
    else {
      dasconfig.setEnabled(false);
      dasconfig.setVisible(false);
      //labSeedNet.setVisible(false);
      //seedNet.setVisible(false);
      labExpbias.setVisible(true);
      labnet.setVisible(true);
      netid.setVisible(true);
      labNode.setVisible(true);
      nodeid.setVisible(true);
      expbias.setVisible(true);
      labDasconfig.setVisible(false);
      dasText.setVisible(false);
      labCont20.setVisible(true);
      labCont40.setVisible(true);
      cont40.setVisible(true);
      cont20.setVisible(true);
      labcalEnable.setVisible(false);
      calEnable.setVisible(true);
      auxAD.setVisible(false);
      auxADCH2Battery.setVisible(false);
      lab480.setVisible(true);
      dac480.setVisible(true);
      labAttn480.setVisible(true);
      attn480.setVisible(true);
      labStartTbase.setVisible(true);
      startTbase.setVisible(true);
      lowPower.setVisible(false);
      configTag.setVisible(false);
      labConfig.setVisible(false);
      labOverride.setVisible(false);
      overrideConfig.setVisible(false);

    }
    chkForm();
  }//GEN-LAST:event_quanterraTypeActionPerformed

  private void addUpdateActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_addUpdateActionPerformed
    // Add your handling code here:
    if(initiating) return;
    // Do not change
    if(chkForm()) return;
    popup.setText("Starting...\n");


    try {
      String p = nsnstation.getSelectedItem().toString();
      p = p.toUpperCase();                  // USER : drop this to allow Mixed case
      if(!obj.isNew()) obj.refreshRecord();        // Refresh the result set so the sets will not fail on stale one
      obj.setString("nsnstation",p);

      
      // USER : Set all of the fields in data base.  obj.setInt("thefield", ivalue);
      obj.setInt("nodeid", nodeidVal);
      obj.setInt("netid", netidVal);
      obj.setInt("stationtype", stationType.getSelectedIndex());
      obj.setInt("quanterratype", quanterraType.getSelectedIndex());

      if(dasconfig.getSelectedIndex() < 0) obj.setInt("dasconfigid",0); 
      else  obj.setInt("dasconfigid", ((DasConfig) dasconfig.getSelectedItem()).getID());
      obj.setString("seednetwork", seedNet.getText().substring(0,Math.min(2,seedNet.getText().length())));
      obj.setString("overridetemplate", overrideConfig.getText());
      Util.prt("Set gpsconfig to "+(lowPower.isSelected()?2:1));
      obj.setInt("gpsconfig", (lowPower.isSelected()?2:1));
      obj.setInt("seismometer", seismometer.getSelectedIndex());
      obj.setInt("highgain", highgain.getSelectedIndex());
      obj.setInt("lowgain", lowgain.getSelectedIndex());
      obj.setInt("cont40", cont40.getSelectedIndex());
      obj.setInt("cont20", cont20.getSelectedIndex());
      obj.setInt("calenable", calEnable.getSelectedIndex());
      obj.setInt("auxad", (auxAD.isSelected() ? 1:0));
      obj.setInt("aux2isbattery", (auxADCH2Battery.isSelected() ? 1:0));
      obj.setInt("dac480", dac480Val);
      obj.setInt("attn480", attn480Val);
      obj.setInt("expbias", expbiasVal);
      obj.setInt("startTbase", tstartVal);
      obj.setInt("seismometer2", 1);
      obj.setInt("horizontals",1);
      Util.prt("qtype="+quanterraType.getSelectedIndex()+
          " stype="+stationType.getSelectedIndex()+" dasconfig="+dasconfig.getSelectedIndex());
      if( ((String) quanterraTypeEnum.get(quanterraType.getSelectedIndex())).indexOf("Q330") >= 0)
        obj.setString("HasConsole","False");
      else obj.setString("hasConsole","True");
      
       // Do not change
      obj.updateRecord();
      // Special case - we updated the DB so now update the FOrm stuff on VMS
      Statement stmt = DBConnectionThread.getConnection(DBConnectionThread.getDBSchema()).createStatement();   // used for query
      String psave=p;
      if(p.indexOf(":") >= 0) {
        psave=p;
        psave = psave.substring(0,p.indexOf(":"))+"%";
      }
      
      String s = "SELECT * FROM anss.nsnstation where nsnstation = "
              + Util.sqlEscape(p);
      ResultSet rs = stmt.executeQuery(s);
      rs.next();
      NsnStation loc = new NsnStation(rs);    // read back what we just updated
      String station=p;
      if(station.indexOf(":") > 0) station=p.substring(0,p.indexOf(":"));
      Util.prt("Look up tcpstation="+station);
      s = "SELECT * FROM anss.tcpstation where tcpstation = "+Util.sqlEscape(station);
      rs = stmt.executeQuery(s);
      rs.next();
      TCPStation tcp = new TCPStation(rs);    // read back what we just updated
      //loc.sendForm(psave);                         // SEnd it to VMS
      
      SNWStationPanel.setSNWGroupsFromANSS(p);

      
      // Update the Inv.jar DAS configuration if something has changed!
     boolean configChanged=false;
     boolean responseChanged=false;
      if(!startingQuanterraType.equalsIgnoreCase((String) quanterraType.getSelectedItem())) 
      {configChanged=true; responseChanged=true;}
     /*if(quanterraType.getSelectedItem().toString().indexOf("Q330") < 0) {*/
        if(!startingHighGain.equalsIgnoreCase((String) highgain.getSelectedItem())) 
        {configChanged=true;responseChanged=true;}
        if(!startingLowGain.equalsIgnoreCase((String) lowgain.getSelectedItem())) 
        {configChanged=true;responseChanged=true;}
        if(!startingCont40.equalsIgnoreCase((String) cont40.getSelectedItem())) 
        {configChanged=true;responseChanged=true;}
        if(!startingCont20.equalsIgnoreCase((String) cont20.getSelectedItem())) 
        {configChanged=true;responseChanged=true;}
        if(!startingCalEnable.equalsIgnoreCase((String) calEnable.getSelectedItem()))
        {configChanged=true;responseChanged=false;}
        if(startingAUXAD != (auxAD.isSelected() ? 1 : 0)) {configChanged=true;responseChanged=true;}
        if(startingAUXCH2Battery != (auxADCH2Battery.isSelected() ? 1 : 0)) {configChanged=true;responseChanged=true;}
        if(startingLowPower != lowPower.isSelected()) {configChanged=true;responseChanged=false;}
     if(forceDownload.isSelected()) {configChanged=true; responseChanged=true;}

     /*}
     else {   // its not a Q330, only check on config
       int dasid = 0;
       if(dasconfig.getSelectedIndex() != -1) dasid = ((DasConfig) dasconfig.getSelectedItem()).getID();
       if(startingDasConfig != dasid)
         configChanged=true;
       if(!startingSeedNetwork.equalsIgnoreCase(seedNet.getText().trim())) {
         configChanged=true;
       }
     }*/
      
      if(configChanged) {
        String tag=((String) quanterraType.getSelectedItem())+":";
        if(quanterraType.getSelectedItem().toString().indexOf("Q330") == 0) {
          tag = quanterraType.getSelectedItem().toString()+":"+configTag.getText();
        }
        else {  // Q730 or Q680
          if( tag.indexOf("Q680") >= 0) tag = "Q680N:";
          if(tag.indexOf("Q730") >= 0) tag = "Q730N:";
          if( ((String) lowgain.getSelectedItem()).equals("True")) tag +="SM-";
          if( ((String) highgain.getSelectedItem()).equals("True")) tag +="HG";
          if( ((String) cont40.getSelectedItem()).equals("False") &&
              ((String) cont20.getSelectedItem()).equals("True")) tag +="-20HZ";
        }
        station=nsnstation.getSelectedItem().toString();
        String digitizer="";
        int ibeg;
        if( (ibeg=station.indexOf(":")) >0) {
          digitizer=station.substring(ibeg+1);
          station=station.substring(0,ibeg);
          Util.prt("NSNStationPanel: update location config="+station+":"+digitizer);
        }
        if(configChanged) {
          int ans=-99;
          if(responseChanged) {
            ans =JOptionPane.showConfirmDialog(null,
                "A new configuration of "+tag+" has been created in Inv.jar for "+station+
                "\n\nYou do not need to send it unless you 1) change the DAS type or \n"+
                "2) changed the 'High gain' or 'Low gain' fields, or \n" +
                "3) changed the continuous 40 hz or 20 hz fields.\n\n"+
                " Should I send it?",
                "User Alert",JOptionPane.YES_NO_OPTION);
          }
          Timestamp time = new Timestamp(System.currentTimeMillis());
          if(quanterraType.getSelectedItem().toString().indexOf("Q330") >=0 ) {
            String q330type = quanterraType.getSelectedItem().toString().substring(4,6).toLowerCase();
            String [] parts = p.split(":");
            int unit=1;
            String config = parts[0];
            if(parts.length > 1) {
              unit = parts[1].charAt(0) - 'A' +2;
            }
            TextStatusClient client = new TextStatusClient("gacq1", 7984,
                  "Execute bash scripts/configQ330 "+
                    parts[0]+" "+tcp.getQ330Stations()[unit-1]+" "+unit+" "+q330type+(parts.length>=2?"_"+parts[1]:""));
            popup.setClient(client);
            popup.setVisible(true);
            for(int i=0; i<1000; i++) {
              Util.sleep(100);
              String str = client.getText("Secs=");
              if(str.indexOf("Secs") == 0) {
                int secs = Integer.parseInt(str.substring(5, str.indexOf("\n")));
                time.setTime((secs*1000L)/60000L*60000L+120000L);
                break;
              }
            }
          }
          else {
            tag = dasconfig.getSelectedItem().toString();
          }

          if(responseChanged && ans == JOptionPane.YES_OPTION) {
            String str="";
            try {

              s = "INSERT INTO inv.locationconfig (location,digitizer,text,"
                + "effective,created_by,created) VALUES ("
                + Util.sqlEscape(station) + "," + Util.sqlEscape(digitizer) + ","
                + Util.sqlEscape(tag) + "," + Util.sqlEscape(time) + ","
                + Util.sqlEscape(User.getUserID()) + ","
                + Util.sqlEscape(Util.now()) + ")";
              Util.prta(" Do update="+s);
              Util.prta("NSNStationPanel s="+s);
              Statement stmt2=DBConnectionThread.getConnection(DBConnectionThread.getDBSchema()).createStatement();   // used for query
              stmt2.executeUpdate(s);
            }
            catch (SQLException e) {
              Util.SQLErrorPrint(e,"Could not update inv.locationconfig");
            }
          }
        }
        SNWStationPanel.setSNWGroupsFromANSS(station);
      }
      else popup.setText("Configuration is unchanged.  Nothing to do....\n");
    }
    catch  (SQLException  E)
    { Util.SQLErrorPrint(E,"nsnstation: update failed panel partno="+nsnstation.getSelectedItem());
      error.setText("Could Not Update File!");
      error.setBackground(UC.red);
      addUpdate.setEnabled(true);
      E.printStackTrace();
    } 
    getJComboBox(nsnstation);
    clearScreen();
    nsnstation.setSelectedIndex(-1);

  }//GEN-LAST:event_addUpdateActionPerformed

private void nsnstationActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_nsnstationActionPerformed
    // Add your handling code here:
   find();
 
 
}//GEN-LAST:event_nsnstationActionPerformed

private void highgainActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_highgainActionPerformed
  chkForm();
}//GEN-LAST:event_highgainActionPerformed

private void lowgainActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_lowgainActionPerformed
  chkForm();
}//GEN-LAST:event_lowgainActionPerformed

private void cont40ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_cont40ActionPerformed
// TODO add your handling code here:
   if(cont40.getSelectedItem().toString().equals("True")) cont20.setSelectedItem("False");
   else if(cont40.getSelectedItem().toString().equals("False")) cont20.setSelectedItem("True");
   chkForm();

}//GEN-LAST:event_cont40ActionPerformed

private void cont20ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_cont20ActionPerformed
   if(cont20.getSelectedItem().toString().equals("True")) cont40.setSelectedItem("False");
   else if(cont20.getSelectedItem().toString().equals("False")) cont40.setSelectedItem("True");
   chkForm();
}//GEN-LAST:event_cont20ActionPerformed

private void lowPowerActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_lowPowerActionPerformed
// TODO add your handling code here:
  chkForm();
}//GEN-LAST:event_lowPowerActionPerformed

private void auxADActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_auxADActionPerformed
  if(auxAD.isSelected()) auxADCH2Battery.setVisible(true);
  else auxADCH2Battery.setVisible(false);
}//GEN-LAST:event_auxADActionPerformed
  
  
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JTextField ID;
    private javax.swing.JButton addUpdate;
    private javax.swing.JTextField attn480;
    private javax.swing.JRadioButton auxAD;
    private javax.swing.JRadioButton auxADCH2Battery;
    private javax.swing.JComboBox calEnable;
    private javax.swing.JPanel configPanel;
    private javax.swing.JTextField configTag;
    private javax.swing.JComboBox cont20;
    private javax.swing.JComboBox cont40;
    private javax.swing.JTextField dac480;
    private javax.swing.JTextArea dasText;
    private javax.swing.JComboBox dasconfig;
    private javax.swing.JTextField error;
    private javax.swing.JTextField expbias;
    private javax.swing.JRadioButton forceDownload;
    private java.awt.GridBagLayout gridBagLayout1;
    private javax.swing.JComboBox highgain;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel9;
    private javax.swing.JLabel lab480;
    private javax.swing.JLabel labAttn480;
    private javax.swing.JLabel labConfig;
    private javax.swing.JLabel labCont20;
    private javax.swing.JLabel labCont40;
    private javax.swing.JLabel labDasconfig;
    private javax.swing.JLabel labExpbias;
    private javax.swing.JLabel labHighgain;
    private javax.swing.JLabel labLowgain;
    private javax.swing.JLabel labNode;
    private javax.swing.JLabel labOverride;
    private javax.swing.JLabel labQType;
    private javax.swing.JLabel labSeedNet;
    private javax.swing.JLabel labSeis;
    private javax.swing.JLabel labStartTbase;
    private javax.swing.JLabel labStationTp;
    private javax.swing.JLabel labcalEnable;
    private javax.swing.JLabel labnet;
    private javax.swing.JRadioButton lowPower;
    private javax.swing.JComboBox lowgain;
    private javax.swing.JTextField netid;
    private javax.swing.JTextField nodeid;
    private javax.swing.JComboBox nsnstation;
    private javax.swing.JTextField overrideConfig;
    private javax.swing.JComboBox quanterraType;
    private javax.swing.JTextField seedNet;
    private javax.swing.JComboBox seismometer;
    private javax.swing.JTextField startTbase;
    private javax.swing.JComboBox stationType;
    // End of variables declaration//GEN-END:variables
  /** Creates new form NsnStationPanel */
  public NsnStationPanel() {
    initiating=true;
    initComponents();
    getJComboBox(nsnstation);                // set up the key JComboBox
    nsnstation.setSelectedIndex(-1);    // Set selected type
    Look();                    // Set color background
    UC.Look(configPanel);
    if(quanterraTypeEnum == null) 
      quanterraTypeEnum = FUtil.getEnum(DBConnectionThread.getConnection("anss"),"nsnstation","quanterraType");
    if(stationTypeEnum == null) 
      stationTypeEnum = FUtil.getEnum(DBConnectionThread.getConnection("anss"),"nsnstation","stationType");
    if(trueFalseEnum == null) 
      trueFalseEnum = FUtil.getEnum(DBConnectionThread.getConnection("anss"),"nsnstation","cont40");
    if(seismometerEnum == null) 
      seismometerEnum = FUtil.getEnum(DBConnectionThread.getConnection("anss"),"nsnstation","seismometer");
    if(calEnableEnum == null)
      calEnableEnum = FUtil.getEnum(DBConnectionThread.getConnection("anss"), "nsnstation", "calenable");
    
   
    // USER:This is a good place for UC.Look(components) to get rid of the ugly Grey
    initiating=false;
    
    clearScreen();                    // Start with empty screen
  }
  private void Look() {
    UC.Look(this);                    // Set color background 
  }
  //////////////////////////////////////////////////////////////////////////////
  // No USER: changes needed below here!
  //////////////////////////////////////////////////////////////////////////////
 public static JComboBox getJComboBox() { JComboBox b = new JComboBox(); getJComboBox(b); return b;}
  
  public static void getJComboBox(JComboBox b) {
    b.removeAllItems();
    makeNsnStations();
    for (int i=0; i< v.size(); i++) {
      b.addItem( v.get(i));
    }
    b.setMaximumRowCount(30);
  }
  
  // Given a JComboBox from getJComboBox, set selected item to match database ID
  public static void setJComboBoxToID(JComboBox b, int ID) {
    b.setSelectedIndex(-1);
    //Util.prt("NsnStationPanel.setJComboBoxToID id="+ID);
    for(int i=0; i<b.getItemCount(); i++) 
      if(((TCPStation) b.getItemAt(i)).getID() == ID) {
          b.setSelectedIndex(i);
          //Util.prt("TCPStationPanel.setJComboBoxToID id="+ID+" found at "+i);
      }
  }  
  
  // IF you have the maintcodeID and need to know where it is in the combo box, call this!
  public static int getJComboBoxIndex(int ID) {
    for(int i=0; i<v.size(); i++) if( ID == ((NsnStation) v.get(i)).getID()) return i;
    return -1;
  }
  public static ArrayList<NsnStation> getVector() {
    makeNsnStations();
    return v;
  }
  public static void reload() {
    if(v != null) v.clear();
    v = null;
    makeNsnStations();
  }
  
  // This routine should only need tweeking if key field is not same as table name
  private static void makeNsnStations() {
    if (v != null) return;
    v=new ArrayList<NsnStation>(100);
    try {
      Statement stmt = DBConnectionThread.getConnection(DBConnectionThread.getDBSchema()).createStatement();   // used for query
      String s = "SELECT * FROM anss.nsnstation ORDER BY nsnstation";
      ResultSet rs = stmt.executeQuery(s);
      while (rs.next()) {
        NsnStation loc = new NsnStation(rs);
//        Util.prt("MakeNsnStation() i="+v.size()+" is "+loc.getNsnStation());
        v.add(loc);
      }
      rs.close();
      stmt.close();
    }
    catch (SQLException e) {
      Util.SQLErrorPrint(e,"makeNsnStations() on table SQL failed");
    }    
  }
  
  // No changes needed
  private void find() {
    if(nsnstation == null) return;
    NsnStation l;
    if(nsnstation.getSelectedIndex() == -1) {
      if(nsnstation.getSelectedItem() == null) return;
      l = newOne();
    } 
    else {
      l = (NsnStation) nsnstation.getSelectedItem();
    }
      
    if(l == null) return;
    String p = l.getNsnStation();
    if(p == null) return;
    if(p.equals("")) {
      addUpdate.setEnabled(false);
      addUpdate.setText("Enter a NsnStation!");
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
    { Util.SQLErrorPrint(E,"NsnStation: SQL error getting NsnStation="+p);
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
      Show.inFrame(new NsnStationPanel(), UC.XSIZE, UC.YSIZE);
    }
    catch(InstantiationException e) {}
  }
}
