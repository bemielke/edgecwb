/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */

package gov.usgs.anss.portables;

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
import java.sql.Timestamp;
import java.util.ArrayList;
import javax.swing.JComboBox;
/**
 *
 * requirements : 
 * The key for the file must be the name of the table and of the
 * JComboBox variable this variable is called "reftek" in the initial form
 * The table name and key name should match and start lower case (reftek).
 * The Class here will be that same name but with upper case at beginning(Reftek).
 * <br> 1)  Rename the location JComboBox to the "key" (reftek) value.  This should start Lower
 *      case.
 *<br>  2)  The table name should match this key name and be all lower case.
 *<br>  3)  Change all Reftek to ClassName of underlying data (case sensitive!)
 *<br>  4)  Change all reftek key value (the JComboBox above)
 *<br>  5)  clearScreen should update swing components to new defaults
 *<br>  6)  chkForm should check all field for validity.  The errorTrack variable is
 *      used to track multiple errors.  In FUtil.chk* handle checks of the various
 *      variable types.  Date, double, and IP etc return the "canonical" form for 
 *      the variable.  These should be stored in a class variable so the latest one
 *      from "chkForm()" are available to the update writer.
 *<br>  7) newone() should be updated to create a new instance of the underlying data class.
 *<br>  8) oldone() get data from database and update all swing elements to correct values
 *<br>  9) addUpdateAction() add data base set*() operations updating all fields in the DB
 *<br> 10) ReftekPanel() constructor - good place to change backgrounds using UC.Look() any
 *    other weird startup stuff.
 *
 *<br>  local variable error - Must be a JTextField for posting error communications
 * <br> local variable updateAdd - Must be the JButton for user clicks to try to post the data
 * <br> ID - JTextField which must be non-editable for posting Database IDs
 * <br>
 *<br>
 *ENUM notes :  the ENUM are read from the database as type String.  So the constructor
 * should us the strings to set the indexes.  However, the writes to the database are
 * done as ints representing the value of the Enum.  The JComboBox with the Enum has 
 * the strings as Items in the list with indexes equal to the database value of the int.
 * Such JComboBoxes are usually initalized with 
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
public final class ReftekPortablePanel extends javax.swing.JPanel {
  
  // NOTE : here define all variables general.  "ArrayList refteckPortables" is used for main Comboboz
  static ArrayList<ReftekPortable> refteckPortables;             // ArrayList containing objects of this Reftek Type
  private DBObject obj;
  private final ErrorTrack err=new ErrorTrack();
  private boolean initiating=false;
  
  //USER: Here are the local variables
  private double drate1, drate2, drate3, drate4, drate5,drate6;
  private int istream1, istream2, istream3,istream4, istream5,istream6;
  private Timestamp endTimestamp;
  

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
   Util.prt("chkForm Reftek");
   drate1 = FUtil.chkDouble(rate1, err, .0, 300., true);
   drate2 = FUtil.chkDouble(rate2, err, .0, 300., true);
   drate3 = FUtil.chkDouble(rate3, err, .0, 300., true);
   drate4 = FUtil.chkDouble(rate4, err, .0, 300., true);
   drate5 = FUtil.chkDouble(rate5, err, .0, 300., true);
   drate6 = FUtil.chkDouble(rate6, err, .0, 300., true);
   istream1 = FUtil.chkInt(stream1, err, -1,10, true);
   istream2 = FUtil.chkInt(stream2, err, -1,10, true);
   istream3 = FUtil.chkInt(stream3, err, -1,10, true);
   istream4 = FUtil.chkInt(stream4, err, -1,10, true);
   istream5 = FUtil.chkInt(stream5, err, -1,10, true);
   istream6 = FUtil.chkInt(stream6, err, -1,10, true);
   if(istream1 > 0) { 
     if(!band1.getText().equals("SH") && !band1.getText().equals("BH") &&
              !band1.getText().equals("HH") && !band1.getText().equals("HN") &&
              !band1.getText().equals("BL") && !band1.getText().equals("HL") &&
              !band1.getText().equals("BN") && !band1.getText().equals("SN") &&
              !band1.getText().equals("EH") && !band1.getText().equals("EL") &&
              !band1.getText().equals("LH") && !band1.getText().equals("LN") &&
              !band1.getText().equals("VM")) {
       err.set(true);
       err.appendText("Band code1 "+band1.getText()+" invalid");
     }
     String band = band1.getText().substring(0,1);
     if( (band.equals("S") || band.equals("B")) && (drate1 <=1. || drate1 >=80.) ){
       err.set(true);
       err.appendText("Band code1 is S or B but data rate not >= 10 and < 80.");
     }
     if( (band.equals("H") || band.equals("E")) && drate1 < 80. ){
       err.set(true);
       err.appendText("Band code1 is H or E but data rate not>= 80.");
     }
     if( (band.equals("L")) && drate1 >1.){
       err.set(true);
       err.appendText("Band code1 is L but data rate not <=1.");
     }
     if(location1.getText().length() != 2 || !(location1.getText().equals("00") || location1.getText().equals("10") ||
         location1.getText().equals("20") || location1.getText().equals("01") || location1.getText().equals("11") ||
         location1.getText().equals("21") )) {
       location1.setBackground(Color.YELLOW);
       err.appendText("Loc1 is not standard-");
     }
   }
   if(istream2 > 0) {
     if(!band2.getText().equals("SH") && !band2.getText().equals("BH") &&
              !band2.getText().equals("HH") && !band2.getText().equals("HN") &&
              !band2.getText().equals("BN") && !band2.getText().equals("SN") &&
              !band2.getText().equals("EH") && !band2.getText().equals("EL") &&
              !band2.getText().equals("LH") && !band2.getText().equals("LN") &&
              !band2.getText().equals("VM")) {
       err.set(true);
       err.appendText("Band code2 "+band2.getText()+" invalid");
     }
     String band = band2.getText().substring(0,1);
     if( (band.equals("S") || band.equals("B")) && (drate2 <=1. || drate2 >=80.) ){
       err.set(true);
       err.appendText("Band code2 is B or S but data rate not >=10 and <80.");
     }
     if( (band.equals("H") || band.equals("E")) && drate2 < 80. ){
       err.set(true);
       err.appendText("Band code2 is H or E but data rate not>= 80.");
     }
     if( (band.equals("L")) && drate2 >1.){
       err.set(true);
       err.appendText("Band code2 is L but data rate not <= 1.");
     }
     if(location2.getText().length() != 2 || !(location2.getText().equals("00") || location2.getText().equals("10") ||
              location2.getText().equals("20") || location2.getText().equals("01") || location2.getText().equals("11") ||
               location2.getText().equals("21") )) {
       location2.setBackground(Color.YELLOW);
      err.appendText("Loc2 is not standard-");
     }
   }
   if(istream3 > 0) {
     if(!band3.getText().equals("SH") && !band3.getText().equals("BH") &&
              !band3.getText().equals("HH") && !band3.getText().equals("HN") &&
              !band3.getText().equals("BN") && !band3.getText().equals("SN") &&
              !band3.getText().equals("EH") && !band3.getText().equals("EL") &&
              !band3.getText().equals("LH") && !band3.getText().equals("LN") &&
              !band3.getText().equals("VM")) {
       err.set(true);
       err.appendText("Band code3 "+band3.getText()+" invalid");
     }
     String band = band3.getText().substring(0,1);
     if( (band.equals("S") || band.equals("B")) && (drate3 <=10. || drate3 >=80.) ){
       err.set(true);
       err.appendText("Band code3 is S or B but data rate not >=10 and <80");
     }
     if( (band.equals("H") || band.equals("E")) && drate3 < 80. ){
       err.set(true);
       err.appendText("Band code3 is H or E but data rate not>= 80.");
     }
     if( (band.equals("L")) && drate3 >1.){
       err.set(true);
       err.appendText("Band code3 is L but data rate not <= 1.");
     }
     if(location3.getText().length() != 2 || !(location3.getText().equals("00") || location3.getText().equals("10") ||
              location3.getText().equals("20") || location3.getText().equals("01") || location1.getText().equals("11") ||
               location3.getText().equals("21") )) location3.setBackground(Color.YELLOW);
     err.appendText("Loc1 is not standard-");
   }

   if(istream4 > 0) {
     if(!band4.getText().equals("SH") && !band4.getText().equals("BH") &&
              !band4.getText().equals("HH") && !band4.getText().equals("HN") &&
              !band4.getText().equals("BN") && !band4.getText().equals("SN") &&
              !band4.getText().equals("EH") && !band4.getText().equals("EL") &&
              !band4.getText().equals("LH") && !band4.getText().equals("LN") &&
              !band4.getText().equals("VM")) {
       err.set(true);
       err.appendText("Band code4 "+band4.getText()+" invalid");
     }
     String band = band4.getText().substring(0,1);
     if( (band.equals("S") || band.equals("B")) && (drate4 <=10. || drate4 >=80.) ){
       err.set(true);
       err.appendText("Band code4 is S or B but data rate not >=10 and <80");
     }
     if( (band.equals("H") || band.equals("E")) && drate4 < 80. ){
       err.set(true);
       err.appendText("Band code4 is H or E but data rate not>= 80.");
     }
     if( (band.equals("L")) && drate4 >1.){
       err.set(true);
       err.appendText("Band code4 is L but data rate not <= 1.");
     }
     if(location4.getText().length() != 2 || !(location4.getText().equals("00") || location4.getText().equals("10") ||
              location4.getText().equals("20") || location4.getText().equals("01") || location4.getText().equals("11") ||
               location4.getText().equals("21") )) location4.setBackground(Color.YELLOW);
     err.appendText("Loc4 is not standard-");
   }

   if(istream5 > 0) {
     if(!band5.getText().equals("SH") && !band5.getText().equals("BH") &&
              !band5.getText().equals("HH") && !band5.getText().equals("HN") &&
              !band5.getText().equals("BN") && !band5.getText().equals("SN") &&
              !band5.getText().equals("EH") && !band5.getText().equals("EL") &&
              !band5.getText().equals("LH") && !band5.getText().equals("LN") &&
              !band5.getText().equals("VM")) {
       err.set(true);
       err.appendText("Band code5 "+band5.getText()+" invalid");
     }
     String band = band5.getText().substring(0,1);
     if( (band.equals("S") || band.equals("B")) && (drate5 <=10. || drate5 >=80.) ){
       err.set(true);
       err.appendText("Band code5 is S or B but data rate not >=10 and <80");
     }
     if( (band.equals("H") || band.equals("E")) && drate5 < 80. ){
       err.set(true);
       err.appendText("Band code5 is H or E but data rate not>= 80.");
     }
     if( (band.equals("L")) && drate5 >1.){
       err.set(true);
       err.appendText("Band code5 is L but data rate not <= 1.");
     }
     if(location5.getText().length() != 2 || !(location5.getText().equals("00") || location5.getText().equals("10") ||
              location5.getText().equals("20") || location5.getText().equals("01") || location5.getText().equals("11") ||
               location5.getText().equals("21") )) location5.setBackground(Color.YELLOW);
     err.appendText("Loc5 is not standard-");
   }

      if(istream6 > 0) {
     if(!band6.getText().equals("SH") && !band6.getText().equals("BH") &&
              !band6.getText().equals("HH") && !band6.getText().equals("HN") &&
              !band6.getText().equals("BN") && !band6.getText().equals("SN") &&
              !band6.getText().equals("EH") && !band6.getText().equals("EL") &&
              !band6.getText().equals("LH") && !band6.getText().equals("LN") &&
              !band6.getText().equals("VM")) {
       err.set(true);
       err.appendText("Band code6 "+band6.getText()+" invalid");
     }
     String band = band6.getText().substring(0,1);
     if( (band.equals("S") || band.equals("B")) && (drate6 <=10. || drate6 >=80.) ){
       err.set(true);
       err.appendText("Band code6 is S or B but data rate not >=10 and <80");
     }
     if( (band.equals("H") || band.equals("E")) && drate6 < 80. ){
       err.set(true);
       err.appendText("Band code6 is H or E but data rate not>= 80.");
     }
     if( (band.equals("L")) && drate6 >1.){
       err.set(true);
       err.appendText("Band code6 is L but data rate not <= 1.");
     }
     if(location6.getText().length() != 2 || !(location6.getText().equals("00") || location6.getText().equals("10") ||
              location6.getText().equals("20") || location6.getText().equals("01") || location6.getText().equals("11") ||
               location6.getText().equals("21") )) location6.setBackground(Color.YELLOW);
     err.appendText("Loc6 is not standard-");
   }
   endTimestamp = FUtil.chkTimestamp(enddate, err);
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
    addUpdate.setText("Enter a Reftek");
    
    //USER: Clear all fields for this form, this sets "defaults" for new screen
    // description.setText("");
    network.setText("");
    ipadr.setText(""); ipadr.setVisible(false); labIP.setVisible(false);
    netmask.setText("");netmask.setVisible(false); labNetmask.setVisible(false);
    gateway.setText("");gateway.setVisible(false); labGateway.setVisible(false);
    comment.setText("");
    serial.setText("");
    stream1.setText("");
    rate1.setText("");
    chans1.setText("");
    comps1.setText("");
    location1.setText("");
    band1.setText("");
    stream2.setText("");
    rate2.setText("");
    chans2.setText("");
    comps2.setText("");
    location2.setText("");
    band2.setText("");
    stream3.setText("");
    rate3.setText("");
    chans3.setText("");
    comps3.setText("");
    location3.setText("");
    band3.setText("");

    stream4.setText("");
    rate4.setText("");
    chans4.setText("");
    comps4.setText("");
    location4.setText("");
    band4.setText("");

    stream5.setText("");
    rate5.setText("");
    chans5.setText("");
    comps5.setText("");
    location5.setText("");
    band5.setText("");

    stream6.setText("");
    rate6.setText("");
    chans6.setText("");
    comps6.setText("");
    location6.setText("");
    band6.setText("");

    enddate.setText("2037-12-31");
    changeDeployment.setSelectedIndex(-1);
    newStation.setText("");
  }
 
  
  private ReftekPortable newOne() {
      
    return new ReftekPortable(0, ((String) reftek.getSelectedItem()).toUpperCase() //, more
            ,"","","","","", 0, 0., "","","","",0, 0., "","","","",0, 0., "","","","","", 0
       );

  }
  
  private void oldOne(String p) throws SQLException {
    obj = new DBObject(DBConnectionThread.getThread(DBConnectionThread.getDBSchema()), 
            "anss", "portablereftek","station",p);

    if(obj.isNew()) {
      Util.prt("object is new"+p);
    }
    else {
      //USER: Here set all of the form fields to data from the DBObject
      //      reftek.setText(obj.getString("Reftek"));
      // Example : description.setText(obj.getString("description"));
      network.setText(obj.getString("network"));
      serial.setText(obj.getString("serial"));
      ipadr.setText(obj.getString("ipadr"));
      netmask.setText(obj.getString("netmask"));
      gateway.setText(obj.getString("gateway"));
      comment.setText(obj.getString("comment"));

      stream1.setText(obj.getInt("stream1")+"");
      rate1.setText(obj.getDouble("rate1")+"");
      chans1.setText(obj.getString("chans1"));
      comps1.setText(obj.getString("components1"));
      location1.setText(obj.getString("location1"));
      band1.setText(obj.getString("band1"));
      stream2.setText(obj.getInt("stream2")+"");
      rate2.setText(obj.getDouble("rate2")+"");
      chans2.setText(obj.getString("chans2"));
      comps2.setText(obj.getString("components2"));
      location2.setText(obj.getString("location2"));
      band2.setText(obj.getString("band2"));
      stream3.setText(obj.getInt("stream3")+"");
      rate3.setText(obj.getDouble("rate3")+"");
      chans3.setText(obj.getString("chans3"));
      comps3.setText(obj.getString("components3"));
      location3.setText(obj.getString("location3"));
      band3.setText(obj.getString("band3"));

      stream4.setText(obj.getInt("stream4")+"");
      rate4.setText(obj.getDouble("rate4")+"");
      chans4.setText(obj.getString("chans4"));
      comps4.setText(obj.getString("components4"));
      location4.setText(obj.getString("location4"));
      band4.setText(obj.getString("band4"));

      stream5.setText(obj.getInt("stream5")+"");
      rate5.setText(obj.getDouble("rate5")+"");
      chans5.setText(obj.getString("chans5"));
      comps5.setText(obj.getString("components5"));
      location5.setText(obj.getString("location5"));
      band5.setText(obj.getString("band5"));

      stream6.setText(obj.getInt("stream6")+"");
      rate6.setText(obj.getDouble("rate6")+"");
      chans6.setText(obj.getString("chans6"));
      comps6.setText(obj.getString("components6"));
      location6.setText(obj.getString("location6"));
      band6.setText(obj.getString("band6"));

      enddate.setText(obj.getTimestamp("enddate").toString().substring(0,19));

        
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

        addUpdate = new javax.swing.JButton();
        error = new javax.swing.JTextField();
        panel1 = new javax.swing.JPanel();
        labStr1 = new javax.swing.JLabel();
        stream1 = new javax.swing.JTextField();
        labRate1 = new javax.swing.JLabel();
        rate1 = new javax.swing.JTextField();
        labChans1 = new javax.swing.JLabel();
        chans1 = new javax.swing.JTextField();
        labComps1 = new javax.swing.JLabel();
        comps1 = new javax.swing.JTextField();
        labLoc1 = new javax.swing.JLabel();
        location1 = new javax.swing.JTextField();
        labBand1 = new javax.swing.JLabel();
        band1 = new javax.swing.JTextField();
        panel2 = new javax.swing.JPanel();
        labStr2 = new javax.swing.JLabel();
        stream2 = new javax.swing.JTextField();
        labRate2 = new javax.swing.JLabel();
        rate2 = new javax.swing.JTextField();
        labChans2 = new javax.swing.JLabel();
        chans2 = new javax.swing.JTextField();
        labComps2 = new javax.swing.JLabel();
        comps2 = new javax.swing.JTextField();
        labLoc2 = new javax.swing.JLabel();
        location2 = new javax.swing.JTextField();
        labBand2 = new javax.swing.JLabel();
        band2 = new javax.swing.JTextField();
        panel3 = new javax.swing.JPanel();
        labStr3 = new javax.swing.JLabel();
        stream3 = new javax.swing.JTextField();
        labRate3 = new javax.swing.JLabel();
        rate3 = new javax.swing.JTextField();
        labChans3 = new javax.swing.JLabel();
        chans3 = new javax.swing.JTextField();
        labComps3 = new javax.swing.JLabel();
        comps3 = new javax.swing.JTextField();
        labLoc3 = new javax.swing.JLabel();
        location3 = new javax.swing.JTextField();
        labBand3 = new javax.swing.JLabel();
        band3 = new javax.swing.JTextField();
        helpScroll = new javax.swing.JScrollPane();
        help = new javax.swing.JTextArea();
        deployment = DeploymentPortablePanel.getJComboBox();
        labDeployment = new javax.swing.JLabel();
        topPanel = new javax.swing.JPanel();
        labStation = new javax.swing.JLabel();
        reftek = getJComboBox();
        labNet = new javax.swing.JLabel();
        network = new javax.swing.JTextField();
        labSerial = new javax.swing.JLabel();
        serial = new javax.swing.JTextField();
        labEnddate = new javax.swing.JLabel();
        enddate = new javax.swing.JTextField();
        labIP = new javax.swing.JLabel();
        ipadr = new javax.swing.JTextField();
        labNetmask = new javax.swing.JLabel();
        netmask = new javax.swing.JTextField();
        setDefault = new javax.swing.JButton();
        labGateway = new javax.swing.JLabel();
        gateway = new javax.swing.JTextField();
        labComment = new javax.swing.JLabel();
        comment = new javax.swing.JTextField();
        panel4 = new javax.swing.JPanel();
        labStr4 = new javax.swing.JLabel();
        stream4 = new javax.swing.JTextField();
        labRate4 = new javax.swing.JLabel();
        rate4 = new javax.swing.JTextField();
        labChans4 = new javax.swing.JLabel();
        chans4 = new javax.swing.JTextField();
        labComps4 = new javax.swing.JLabel();
        comps4 = new javax.swing.JTextField();
        labLoc4 = new javax.swing.JLabel();
        location4 = new javax.swing.JTextField();
        labBand4 = new javax.swing.JLabel();
        band4 = new javax.swing.JTextField();
        panel5 = new javax.swing.JPanel();
        labStr5 = new javax.swing.JLabel();
        stream5 = new javax.swing.JTextField();
        labRate5 = new javax.swing.JLabel();
        rate5 = new javax.swing.JTextField();
        labChans5 = new javax.swing.JLabel();
        chans5 = new javax.swing.JTextField();
        labComps5 = new javax.swing.JLabel();
        comps5 = new javax.swing.JTextField();
        labLoc5 = new javax.swing.JLabel();
        location5 = new javax.swing.JTextField();
        labBand5 = new javax.swing.JLabel();
        band5 = new javax.swing.JTextField();
        panel6 = new javax.swing.JPanel();
        labStr6 = new javax.swing.JLabel();
        stream6 = new javax.swing.JTextField();
        labRate6 = new javax.swing.JLabel();
        rate6 = new javax.swing.JTextField();
        labChans6 = new javax.swing.JLabel();
        chans6 = new javax.swing.JTextField();
        labComps6 = new javax.swing.JLabel();
        comps6 = new javax.swing.JTextField();
        labLoc6 = new javax.swing.JLabel();
        location6 = new javax.swing.JTextField();
        labBand6 = new javax.swing.JLabel();
        band6 = new javax.swing.JTextField();
        bottomPanel = new javax.swing.JPanel();
        changeDepLbl = new javax.swing.JLabel();
        changeDeployment = DeploymentPortablePanel.getJComboBox();
        jLabel1 = new javax.swing.JLabel();
        newStation = new javax.swing.JTextField();
        reload = new javax.swing.JButton();
        jLabel9 = new javax.swing.JLabel();
        ID = new javax.swing.JTextField();

        setLayout(new java.awt.GridBagLayout());

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

        error.setColumns(40);
        error.setEditable(false);
        error.setMinimumSize(new java.awt.Dimension(488, 22));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        add(error, gridBagConstraints);

        panel1.setMinimumSize(new java.awt.Dimension(600, 30));
        panel1.setPreferredSize(new java.awt.Dimension(600, 30));

        labStr1.setText("Stream:");
        panel1.add(labStr1);

        stream1.setColumns(2);
        stream1.setMinimumSize(new java.awt.Dimension(15, 28));
        stream1.setPreferredSize(new java.awt.Dimension(15, 28));
        panel1.add(stream1);

        labRate1.setText("Rate:");
        panel1.add(labRate1);

        rate1.setColumns(5);
        rate1.setMinimumSize(new java.awt.Dimension(30, 28));
        rate1.setPreferredSize(new java.awt.Dimension(30, 28));
        rate1.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                rate1ActionPerformed(evt);
            }
        });
        panel1.add(rate1);

        labChans1.setText("Chans:");
        panel1.add(labChans1);

        chans1.setColumns(4);
        chans1.setMinimumSize(new java.awt.Dimension(20, 28));
        panel1.add(chans1);

        labComps1.setText("->");
        panel1.add(labComps1);

        comps1.setColumns(4);
        comps1.setMinimumSize(new java.awt.Dimension(20, 28));
        comps1.setPreferredSize(new java.awt.Dimension(20, 28));
        panel1.add(comps1);

        labLoc1.setText("Loc:");
        panel1.add(labLoc1);

        location1.setColumns(3);
        location1.setMinimumSize(new java.awt.Dimension(20, 28));
        location1.setPreferredSize(new java.awt.Dimension(20, 28));
        panel1.add(location1);

        labBand1.setText("Band:");
        panel1.add(labBand1);

        band1.setColumns(2);
        band1.setMinimumSize(new java.awt.Dimension(15, 28));
        band1.setPreferredSize(new java.awt.Dimension(15, 28));
        panel1.add(band1);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 7;
        add(panel1, gridBagConstraints);

        panel2.setMinimumSize(new java.awt.Dimension(600, 30));
        panel2.setPreferredSize(new java.awt.Dimension(600, 30));

        labStr2.setText("Stream:");
        panel2.add(labStr2);

        stream2.setColumns(2);
        stream2.setMinimumSize(new java.awt.Dimension(15, 28));
        stream2.setPreferredSize(new java.awt.Dimension(15, 28));
        panel2.add(stream2);

        labRate2.setText("Rate:");
        panel2.add(labRate2);

        rate2.setColumns(5);
        rate2.setMinimumSize(new java.awt.Dimension(30, 28));
        rate2.setPreferredSize(new java.awt.Dimension(30, 28));
        rate2.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                rate2ActionPerformed(evt);
            }
        });
        panel2.add(rate2);

        labChans2.setText("Chans:");
        panel2.add(labChans2);

        chans2.setColumns(4);
        chans2.setMinimumSize(new java.awt.Dimension(20, 28));
        panel2.add(chans2);

        labComps2.setText("->");
        panel2.add(labComps2);

        comps2.setColumns(4);
        comps2.setMinimumSize(new java.awt.Dimension(20, 28));
        comps2.setPreferredSize(new java.awt.Dimension(20, 28));
        panel2.add(comps2);

        labLoc2.setText("Loc:");
        panel2.add(labLoc2);

        location2.setColumns(3);
        location2.setMinimumSize(new java.awt.Dimension(20, 28));
        location2.setPreferredSize(new java.awt.Dimension(20, 28));
        panel2.add(location2);

        labBand2.setText("Band:");
        panel2.add(labBand2);

        band2.setColumns(2);
        band2.setMinimumSize(new java.awt.Dimension(15, 28));
        band2.setPreferredSize(new java.awt.Dimension(15, 28));
        panel2.add(band2);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 8;
        add(panel2, gridBagConstraints);

        panel3.setMinimumSize(new java.awt.Dimension(600, 30));
        panel3.setPreferredSize(new java.awt.Dimension(600, 30));

        labStr3.setText("Stream:");
        panel3.add(labStr3);

        stream3.setColumns(2);
        stream3.setMinimumSize(new java.awt.Dimension(15, 28));
        stream3.setPreferredSize(new java.awt.Dimension(15, 28));
        panel3.add(stream3);

        labRate3.setText("Rate:");
        panel3.add(labRate3);

        rate3.setColumns(5);
        rate3.setMinimumSize(new java.awt.Dimension(30, 28));
        rate3.setPreferredSize(new java.awt.Dimension(30, 28));
        rate3.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                rate3ActionPerformed(evt);
            }
        });
        panel3.add(rate3);

        labChans3.setText("Chans:");
        panel3.add(labChans3);

        chans3.setColumns(4);
        chans3.setMinimumSize(new java.awt.Dimension(20, 28));
        panel3.add(chans3);

        labComps3.setText("->");
        panel3.add(labComps3);

        comps3.setColumns(4);
        comps3.setMinimumSize(new java.awt.Dimension(20, 28));
        comps3.setPreferredSize(new java.awt.Dimension(20, 28));
        panel3.add(comps3);

        labLoc3.setText("Loc:");
        panel3.add(labLoc3);

        location3.setColumns(3);
        location3.setMinimumSize(new java.awt.Dimension(20, 28));
        location3.setPreferredSize(new java.awt.Dimension(20, 28));
        panel3.add(location3);

        labBand3.setText("Band:");
        panel3.add(labBand3);

        band3.setColumns(2);
        band3.setMinimumSize(new java.awt.Dimension(15, 28));
        band3.setPreferredSize(new java.awt.Dimension(15, 28));
        panel3.add(band3);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 9;
        add(panel3, gridBagConstraints);

        helpScroll.setMinimumSize(new java.awt.Dimension(575, 90));
        helpScroll.setPreferredSize(new java.awt.Dimension(575, 90));

        help.setColumns(20);
        help.setFont(new java.awt.Font("Monospaced", 0, 12)); // NOI18N
        help.setRows(4);
        help.setText("\"Stream\"\tis the Reftek 130 data stream number (normally 1)\n\"Rate\"   is the data rate in Hz.\n\"Chans\"  is the list of channel number used from this stream (normally \"123\")\n\"Comps\"  is the orientation of the channels so \"ZNE\" means channel 1 is Z, etc.\n\"Loc\"    is the two digit SEED location code, 1st BB=00, 2nd BB=10, SM=20\n\"Band\"   is the SEED band code so a Broad band at 40 Hz should be \"BH\", \n        an accelerometer (Strong Motion) at 100 Hz is \"HN\", for example.\n");
        help.setMinimumSize(new java.awt.Dimension(470, 80));
        help.setPreferredSize(new java.awt.Dimension(470, 80));
        helpScroll.setViewportView(help);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 14;
        add(helpScroll, gridBagConstraints);

        deployment.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                deploymentActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        add(deployment, gridBagConstraints);

        labDeployment.setText("Deployment:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
        add(labDeployment, gridBagConstraints);

        topPanel.setMinimumSize(new java.awt.Dimension(550, 84));
        topPanel.setPreferredSize(new java.awt.Dimension(550, 84));
        topPanel.setLayout(new java.awt.GridBagLayout());

        labStation.setText("Station");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
        topPanel.add(labStation, gridBagConstraints);

        reftek.setEditable(true);
        reftek.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                reftekActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        topPanel.add(reftek, gridBagConstraints);

        labNet.setText("Network:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
        topPanel.add(labNet, gridBagConstraints);

        network.setColumns(4);
        network.setMinimumSize(new java.awt.Dimension(60, 28));
        network.setPreferredSize(new java.awt.Dimension(30, 28));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        topPanel.add(network, gridBagConstraints);

        labSerial.setText("Serial (Hex):");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
        topPanel.add(labSerial, gridBagConstraints);

        serial.setColumns(5);
        serial.setMinimumSize(new java.awt.Dimension(60, 28));
        serial.setPreferredSize(new java.awt.Dimension(60, 28));
        serial.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                serialActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        topPanel.add(serial, gridBagConstraints);

        labEnddate.setText("EndingDate");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
        topPanel.add(labEnddate, gridBagConstraints);

        enddate.setColumns(20);
        enddate.setMinimumSize(new java.awt.Dimension(150, 28));
        enddate.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                enddateActionPerformed(evt);
            }
        });
        enddate.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusLost(java.awt.event.FocusEvent evt) {
                enddateFocusLost(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 3;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        topPanel.add(enddate, gridBagConstraints);

        labIP.setText("IP Adr:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 4;
        gridBagConstraints.gridy = 2;
        topPanel.add(labIP, gridBagConstraints);

        ipadr.setColumns(12);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 4;
        gridBagConstraints.gridy = 3;
        topPanel.add(ipadr, gridBagConstraints);

        labNetmask.setText("NetMask");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 4;
        gridBagConstraints.gridy = 1;
        topPanel.add(labNetmask, gridBagConstraints);

        netmask.setColumns(12);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 4;
        gridBagConstraints.gridy = 0;
        topPanel.add(netmask, gridBagConstraints);

        setDefault.setText("Set All Data Streams to Default");
        setDefault.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                setDefaultActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 3;
        gridBagConstraints.gridy = 2;
        topPanel.add(setDefault, gridBagConstraints);

        labGateway.setText("Gateway:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 3;
        topPanel.add(labGateway, gridBagConstraints);

        gateway.setColumns(16);
        gateway.setMinimumSize(new java.awt.Dimension(200, 28));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 3;
        gridBagConstraints.gridy = 3;
        topPanel.add(gateway, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        add(topPanel, gridBagConstraints);

        labComment.setText("Comment:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 5;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
        add(labComment, gridBagConstraints);

        comment.setColumns(50);
        comment.setMinimumSize(new java.awt.Dimension(409, 28));
        comment.setPreferredSize(new java.awt.Dimension(400, 28));
        comment.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                commentActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 5;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        add(comment, gridBagConstraints);

        panel4.setMinimumSize(new java.awt.Dimension(600, 30));
        panel4.setPreferredSize(new java.awt.Dimension(600, 30));

        labStr4.setText("Stream:");
        panel4.add(labStr4);

        stream4.setColumns(2);
        stream4.setMinimumSize(new java.awt.Dimension(15, 28));
        stream4.setPreferredSize(new java.awt.Dimension(15, 28));
        panel4.add(stream4);

        labRate4.setText("Rate:");
        panel4.add(labRate4);

        rate4.setColumns(5);
        rate4.setMinimumSize(new java.awt.Dimension(30, 28));
        rate4.setPreferredSize(new java.awt.Dimension(30, 28));
        rate4.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                rate4ActionPerformed(evt);
            }
        });
        panel4.add(rate4);

        labChans4.setText("Chans:");
        panel4.add(labChans4);

        chans4.setColumns(4);
        chans4.setMinimumSize(new java.awt.Dimension(20, 28));
        panel4.add(chans4);

        labComps4.setText("->");
        panel4.add(labComps4);

        comps4.setColumns(4);
        comps4.setMinimumSize(new java.awt.Dimension(20, 28));
        comps4.setPreferredSize(new java.awt.Dimension(20, 28));
        panel4.add(comps4);

        labLoc4.setText("Loc:");
        panel4.add(labLoc4);

        location4.setColumns(3);
        location4.setMinimumSize(new java.awt.Dimension(20, 28));
        location4.setPreferredSize(new java.awt.Dimension(20, 28));
        panel4.add(location4);

        labBand4.setText("Band:");
        panel4.add(labBand4);

        band4.setColumns(2);
        band4.setMinimumSize(new java.awt.Dimension(15, 28));
        band4.setPreferredSize(new java.awt.Dimension(15, 28));
        panel4.add(band4);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 10;
        add(panel4, gridBagConstraints);

        panel5.setMinimumSize(new java.awt.Dimension(600, 30));
        panel5.setPreferredSize(new java.awt.Dimension(600, 30));

        labStr5.setText("Stream:");
        panel5.add(labStr5);

        stream5.setColumns(2);
        stream5.setMinimumSize(new java.awt.Dimension(15, 28));
        stream5.setPreferredSize(new java.awt.Dimension(15, 28));
        panel5.add(stream5);

        labRate5.setText("Rate:");
        panel5.add(labRate5);

        rate5.setColumns(5);
        rate5.setMinimumSize(new java.awt.Dimension(30, 28));
        rate5.setPreferredSize(new java.awt.Dimension(30, 28));
        rate5.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                rate5ActionPerformed(evt);
            }
        });
        panel5.add(rate5);

        labChans5.setText("Chans:");
        panel5.add(labChans5);

        chans5.setColumns(4);
        chans5.setMinimumSize(new java.awt.Dimension(20, 28));
        panel5.add(chans5);

        labComps5.setText("->");
        panel5.add(labComps5);

        comps5.setColumns(4);
        comps5.setMinimumSize(new java.awt.Dimension(20, 28));
        comps5.setPreferredSize(new java.awt.Dimension(20, 28));
        panel5.add(comps5);

        labLoc5.setText("Loc:");
        panel5.add(labLoc5);

        location5.setColumns(3);
        location5.setMinimumSize(new java.awt.Dimension(20, 28));
        location5.setPreferredSize(new java.awt.Dimension(20, 28));
        panel5.add(location5);

        labBand5.setText("Band:");
        panel5.add(labBand5);

        band5.setColumns(2);
        band5.setMinimumSize(new java.awt.Dimension(15, 28));
        band5.setPreferredSize(new java.awt.Dimension(15, 28));
        panel5.add(band5);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 11;
        add(panel5, gridBagConstraints);

        panel6.setMinimumSize(new java.awt.Dimension(600, 30));
        panel6.setPreferredSize(new java.awt.Dimension(600, 30));

        labStr6.setText("Stream:");
        panel6.add(labStr6);

        stream6.setColumns(2);
        stream6.setMinimumSize(new java.awt.Dimension(15, 28));
        stream6.setPreferredSize(new java.awt.Dimension(15, 28));
        panel6.add(stream6);

        labRate6.setText("Rate:");
        panel6.add(labRate6);

        rate6.setColumns(5);
        rate6.setMinimumSize(new java.awt.Dimension(30, 28));
        rate6.setPreferredSize(new java.awt.Dimension(30, 28));
        rate6.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                rate6ActionPerformed(evt);
            }
        });
        panel6.add(rate6);

        labChans6.setText("Chans:");
        panel6.add(labChans6);

        chans6.setColumns(4);
        chans6.setMinimumSize(new java.awt.Dimension(20, 28));
        panel6.add(chans6);

        labComps6.setText("->");
        panel6.add(labComps6);

        comps6.setColumns(4);
        comps6.setMinimumSize(new java.awt.Dimension(20, 28));
        comps6.setPreferredSize(new java.awt.Dimension(20, 28));
        panel6.add(comps6);

        labLoc6.setText("Loc:");
        panel6.add(labLoc6);

        location6.setColumns(3);
        location6.setMinimumSize(new java.awt.Dimension(20, 28));
        location6.setPreferredSize(new java.awt.Dimension(20, 28));
        panel6.add(location6);

        labBand6.setText("Band:");
        panel6.add(labBand6);

        band6.setColumns(2);
        band6.setMinimumSize(new java.awt.Dimension(15, 28));
        band6.setPreferredSize(new java.awt.Dimension(15, 28));
        panel6.add(band6);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 12;
        add(panel6, gridBagConstraints);

        bottomPanel.setMinimumSize(new java.awt.Dimension(550, 40));
        bottomPanel.setPreferredSize(new java.awt.Dimension(550, 40));

        changeDepLbl.setFont(new java.awt.Font("Monospaced", 0, 12)); // NOI18N
        changeDepLbl.setText("Set Deployment to:");
        bottomPanel.add(changeDepLbl);

        bottomPanel.add(changeDeployment);

        jLabel1.setFont(new java.awt.Font("Monospaced", 0, 12)); // NOI18N
        jLabel1.setText("Change Station:");
        bottomPanel.add(jLabel1);

        newStation.setFont(new java.awt.Font("Monospaced", 0, 12)); // NOI18N
        newStation.setMinimumSize(new java.awt.Dimension(40, 14));
        newStation.setPreferredSize(new java.awt.Dimension(40, 14));
        bottomPanel.add(newStation);

        reload.setText("Reload");
        reload.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                reloadActionPerformed(evt);
            }
        });
        bottomPanel.add(reload);

        jLabel9.setFont(new java.awt.Font("Monospaced", 0, 12)); // NOI18N
        jLabel9.setText("ID :");
        bottomPanel.add(jLabel9);

        ID.setBackground(new java.awt.Color(204, 204, 204));
        ID.setColumns(3);
        ID.setEditable(false);
        ID.setFont(new java.awt.Font("Monospaced", 0, 12)); // NOI18N
        bottomPanel.add(ID);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 18;
        add(bottomPanel, gridBagConstraints);
    }// </editor-fold>//GEN-END:initComponents

  private void addUpdateActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_addUpdateActionPerformed
    // Add your handling code here:
    
    // Do not change
    if(initiating) return;
    if(chkForm()) return;
    try {
      String p = reftek.getSelectedItem().toString();
      p = p.toUpperCase();                  // USER : drop this to allow Mixed case
       if(!obj.isNew()) obj.refreshRecord();        // Refresh the result set so the sets will not fail on stale one
      obj.setString("station",p);
      
      // USER : Set all of the fields in data base.  obj.setInt("thefield", ivalue);
      obj.setString("network", network.getText());
      obj.setString("serial", serial.getText());
      obj.setString("ipadr", ipadr.getText());
      obj.setString("netmask", netmask.getText());
      obj.setString("gateway", gateway.getText());
      obj.setString("comment", comment.getText());
      obj.setInt("stream1", istream1);
      obj.setDouble("rate1", drate1);
      obj.setString("chans1", chans1.getText());
      obj.setString("components1", comps1.getText());
      obj.setString("location1", location1.getText());
      obj.setString("band1", band1.getText());
      obj.setInt("stream2", istream2);
      obj.setDouble("rate2", drate2);
      obj.setString("chans2", chans2.getText());
      obj.setString("components2", comps2.getText());
      obj.setString("location2", location2.getText());
      obj.setString("band2", band2.getText());
      obj.setInt("stream3", istream3);
      obj.setDouble("rate3", drate3);
      obj.setString("chans3", chans3.getText());
      obj.setString("components3", comps3.getText());
      obj.setString("location3", location3.getText());
      obj.setString("band3", band3.getText());

      obj.setInt("stream4", istream4);
      obj.setDouble("rate4", drate4);
      obj.setString("chans4", chans4.getText());
      obj.setString("components4", comps4.getText());
      obj.setString("location4", location4.getText());
      obj.setString("band4", band4.getText());

      obj.setInt("stream5", istream5);
      obj.setDouble("rate5", drate5);
      obj.setString("chans5", chans5.getText());
      obj.setString("components5", comps5.getText());
      obj.setString("location5", location5.getText());
      obj.setString("band5", band5.getText());

      obj.setInt("stream6", istream6);
      obj.setDouble("rate6", drate6);
      obj.setString("chans6", chans6.getText());
      obj.setString("components6", comps6.getText());
      obj.setString("location6", location6.getText());
      obj.setString("band6", band6.getText());

      if(changeDeployment.getSelectedIndex() >=0) obj.setInt("deploymentid", ((DeploymentPortable) changeDeployment.getSelectedItem()).getID());
      else obj.setInt("deploymentid", ((DeploymentPortable) deployment.getSelectedItem()).getID());
      if(!newStation.getText().equals("")) obj.setString("station",newStation.getText());
      obj.setTimestamp("enddate", endTimestamp);
      
      // Do not change
      obj.updateRecord();
      refteckPortables=null;       // force reload of combo box
      getJComboBox(reftek);
      clearScreen();
      reftek.setSelectedIndex(-1);
      setDeployment();
    }  catch  (SQLException  E)
    { Util.SQLErrorPrint(E,"reftek: update failed partno="+reftek.getSelectedItem());
      error.setText("Could Not Update File!");
      error.setBackground(UC.red);
      addUpdate.setEnabled(true);
    }      
  }//GEN-LAST:event_addUpdateActionPerformed

  private void reftekActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_reftekActionPerformed
    // Add your handling code here:
   find();
 
 
}//GEN-LAST:event_reftekActionPerformed

  private void rate1ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_rate1ActionPerformed
    // TODO add your handling code here:
  }//GEN-LAST:event_rate1ActionPerformed

  private void serialActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_serialActionPerformed
    // TODO add your handling code here:
  }//GEN-LAST:event_serialActionPerformed

  private void rate2ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_rate2ActionPerformed
    // TODO add your handling code here:
  }//GEN-LAST:event_rate2ActionPerformed

  private void rate3ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_rate3ActionPerformed
    // TODO add your handling code here:
  }//GEN-LAST:event_rate3ActionPerformed

private void enddateActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_enddateActionPerformed
  doEndDate();
}//GEN-LAST:event_enddateActionPerformed

private void commentActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_commentActionPerformed
// TODO add your handling code here:
}//GEN-LAST:event_commentActionPerformed

private void deploymentActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_deploymentActionPerformed
  setDeployment();
  if(deployment.getSelectedIndex() != -1) {
    int BOB = ((DeploymentPortable) deployment.getSelectedItem()).getID();
    getJComboBox(reftek);
    for(int i=reftek.getItemCount()-1; i>=0; i--) {
      if(((ReftekPortable) reftek.getItemAt(i)).getDeploymentID() != BOB) reftek.removeItemAt(i);
    }
  }
}//GEN-LAST:event_deploymentActionPerformed
private void setDeployment() {
    if(deployment.getSelectedIndex() != -1) {
    int BOB = ((DeploymentPortable) deployment.getSelectedItem()).getID();
    getJComboBox(reftek);
    for(int i=reftek.getItemCount()-1; i>=0; i--) {
      if(((ReftekPortable) reftek.getItemAt(i)).getDeploymentID() != BOB) reftek.removeItemAt(i);
    }
  }
}
private void enddateFocusLost(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_enddateFocusLost
  doEndDate();
}//GEN-LAST:event_enddateFocusLost

private void reloadActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_reloadActionPerformed
  DeploymentPortablePanel.getJComboBox(deployment);
  DeploymentPortablePanel.getJComboBox(changeDeployment);
  changeDeployment.setSelectedIndex(-1);
}//GEN-LAST:event_reloadActionPerformed

private void rate4ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_rate4ActionPerformed
// TODO add your handling code here:
}//GEN-LAST:event_rate4ActionPerformed

private void rate5ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_rate5ActionPerformed
// TODO add your handling code here:
}//GEN-LAST:event_rate5ActionPerformed

private void rate6ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_rate6ActionPerformed
// TODO add your handling code here:
}//GEN-LAST:event_rate6ActionPerformed

private void setDefaultActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_setDefaultActionPerformed
// TODO add your handling code here:
  if(initiating) return;
  stream1.setText("1");
  rate1.setText("40");
  chans1.setText("123");
  comps1.setText("ZNE");
  location1.setText("00");
  band1.setText("BH");

  stream2.setText("2");
  rate2.setText("100");
  chans2.setText("456");
  comps2.setText("ZNE");
  location2.setText("20");
  band2.setText("HN");

  stream3.setText("3");
  rate3.setText("100");
  chans3.setText("123");
  comps3.setText("ZNE");
  location3.setText("00");
  band3.setText("HH");

  stream4.setText("4");
  rate4.setText("100");
  chans4.setText("456");
  comps4.setText("ZNE");
  location4.setText("10");
  band4.setText("HH");

  stream5.setText("5");
  rate5.setText("200");
  chans5.setText("123");
  comps5.setText("ZNE");
  location5.setText("01");
  band5.setText("HH");

  stream6.setText("6");
  rate6.setText("500");
  chans6.setText("456");
  comps6.setText("ZNE");
  location6.setText("21");
  band6.setText("HN");



}//GEN-LAST:event_setDefaultActionPerformed
String lastEndDateText="";
private void doEndDate() {
  if(enddate.getText().equalsIgnoreCase("s")) enddate.setText(lastEndDateText);
  else lastEndDateText =  enddate.getText();
  chkForm();
}
  
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JTextField ID;
    private javax.swing.JButton addUpdate;
    private javax.swing.JTextField band1;
    private javax.swing.JTextField band2;
    private javax.swing.JTextField band3;
    private javax.swing.JTextField band4;
    private javax.swing.JTextField band5;
    private javax.swing.JTextField band6;
    private javax.swing.JPanel bottomPanel;
    private javax.swing.JLabel changeDepLbl;
    private javax.swing.JComboBox changeDeployment;
    private javax.swing.JTextField chans1;
    private javax.swing.JTextField chans2;
    private javax.swing.JTextField chans3;
    private javax.swing.JTextField chans4;
    private javax.swing.JTextField chans5;
    private javax.swing.JTextField chans6;
    private javax.swing.JTextField comment;
    private javax.swing.JTextField comps1;
    private javax.swing.JTextField comps2;
    private javax.swing.JTextField comps3;
    private javax.swing.JTextField comps4;
    private javax.swing.JTextField comps5;
    private javax.swing.JTextField comps6;
    private javax.swing.JComboBox deployment;
    private javax.swing.JTextField enddate;
    private javax.swing.JTextField error;
    private javax.swing.JTextField gateway;
    private javax.swing.JTextArea help;
    private javax.swing.JScrollPane helpScroll;
    private javax.swing.JTextField ipadr;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel9;
    private javax.swing.JLabel labBand1;
    private javax.swing.JLabel labBand2;
    private javax.swing.JLabel labBand3;
    private javax.swing.JLabel labBand4;
    private javax.swing.JLabel labBand5;
    private javax.swing.JLabel labBand6;
    private javax.swing.JLabel labChans1;
    private javax.swing.JLabel labChans2;
    private javax.swing.JLabel labChans3;
    private javax.swing.JLabel labChans4;
    private javax.swing.JLabel labChans5;
    private javax.swing.JLabel labChans6;
    private javax.swing.JLabel labComment;
    private javax.swing.JLabel labComps1;
    private javax.swing.JLabel labComps2;
    private javax.swing.JLabel labComps3;
    private javax.swing.JLabel labComps4;
    private javax.swing.JLabel labComps5;
    private javax.swing.JLabel labComps6;
    private javax.swing.JLabel labDeployment;
    private javax.swing.JLabel labEnddate;
    private javax.swing.JLabel labGateway;
    private javax.swing.JLabel labIP;
    private javax.swing.JLabel labLoc1;
    private javax.swing.JLabel labLoc2;
    private javax.swing.JLabel labLoc3;
    private javax.swing.JLabel labLoc4;
    private javax.swing.JLabel labLoc5;
    private javax.swing.JLabel labLoc6;
    private javax.swing.JLabel labNet;
    private javax.swing.JLabel labNetmask;
    private javax.swing.JLabel labRate1;
    private javax.swing.JLabel labRate2;
    private javax.swing.JLabel labRate3;
    private javax.swing.JLabel labRate4;
    private javax.swing.JLabel labRate5;
    private javax.swing.JLabel labRate6;
    private javax.swing.JLabel labSerial;
    private javax.swing.JLabel labStation;
    private javax.swing.JLabel labStr1;
    private javax.swing.JLabel labStr2;
    private javax.swing.JLabel labStr3;
    private javax.swing.JLabel labStr4;
    private javax.swing.JLabel labStr5;
    private javax.swing.JLabel labStr6;
    private javax.swing.JTextField location1;
    private javax.swing.JTextField location2;
    private javax.swing.JTextField location3;
    private javax.swing.JTextField location4;
    private javax.swing.JTextField location5;
    private javax.swing.JTextField location6;
    private javax.swing.JTextField netmask;
    private javax.swing.JTextField network;
    private javax.swing.JTextField newStation;
    private javax.swing.JPanel panel1;
    private javax.swing.JPanel panel2;
    private javax.swing.JPanel panel3;
    private javax.swing.JPanel panel4;
    private javax.swing.JPanel panel5;
    private javax.swing.JPanel panel6;
    private javax.swing.JTextField rate1;
    private javax.swing.JTextField rate2;
    private javax.swing.JTextField rate3;
    private javax.swing.JTextField rate4;
    private javax.swing.JTextField rate5;
    private javax.swing.JTextField rate6;
    private javax.swing.JComboBox reftek;
    private javax.swing.JButton reload;
    private javax.swing.JTextField serial;
    private javax.swing.JButton setDefault;
    private javax.swing.JTextField stream1;
    private javax.swing.JTextField stream2;
    private javax.swing.JTextField stream3;
    private javax.swing.JTextField stream4;
    private javax.swing.JTextField stream5;
    private javax.swing.JTextField stream6;
    private javax.swing.JPanel topPanel;
    // End of variables declaration//GEN-END:variables
  /** Creates new form ReftekPanel */
  public ReftekPortablePanel() {
    initiating=true;
    initComponents();
    getJComboBox(reftek);                // set up the key JComboBox
    reftek.setSelectedIndex(-1);    // Set selected type
    Look();                    // Set color background
    initiating=false;
    UC.Look(panel1);
    UC.Look(panel2);
    UC.Look(panel3);
    UC.Look(panel4);
    UC.Look(panel5);
    UC.Look(panel6);
    UC.Look(topPanel);
    UC.Look(bottomPanel);
   
    // USER:This is a good place for UC.Look(components) to get rid of the ugly Grey
    
    clearScreen();                    // Start with empty screen
    deployment.setSelectedIndex(-1);
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
    makeRefteks();
    for (int i=0; i< refteckPortables.size(); i++) {
      b.addItem(refteckPortables.get(i));
    }
    b.setMaximumRowCount(30);
   
  }
  
  /** Given a JComboBox from getJComboBox, set selected item to match database ID
   *@param b The JComboBox
   *@param ID the row ID from the database to select
   */
  public static void setJComboBoxToID(JComboBox b, int ID) {
    b.setSelectedIndex(-1);
    //Util.prt("ReftekPanel.setJComboBoxToID id="+ID);
    for(int i=0; i<b.getItemCount(); i++) 
      if(((ReftekPortable) b.getItemAt(i)).getID() == ID) {
          b.setSelectedIndex(i);
          //Util.prt("ReftekPanel.setJComboBoxToID id="+ID+" found at "+i);
      }
    
  }  
  
  /** Given a database ID find the index into a JComboBox consistent with this panel
   *@param ID The row ID from the database
   *@return The index in the JComboBox with this ID
   */
  public static int getJComboBoxIndex(int ID) {
    if(refteckPortables == null) makeRefteks();
    for(int i=0; i<refteckPortables.size(); i++) if( ID == ((ReftekPortable) refteckPortables.get(i)).getID()) return i;
    return -1;
  }
  /** Cause the main JComboBox to be reloaded*/
  public static void reload() {
    refteckPortables = null;
    makeRefteks();
  }
  /* return a ArrayList with all of the Reftek
   * @return The ArrayList with the reftek
   */
  public static ArrayList getReftekVector() {
    if(refteckPortables == null) makeRefteks();
    return refteckPortables;
  }  /* return a ArrayList with all of the Reftek
   * @return The ArrayList with the reftek
   */
  public static ArrayList getReftekArrayList() {
    if(refteckPortables == null) makeRefteks();
    return refteckPortables;
  }
  /** Get the item corresponding to database ID
   *@param ID the database Row ID
   *@return The Reftek row with this ID
   */
  public static ReftekPortable getReftekWithID(int ID) {
    if(refteckPortables == null) makeRefteks();
    int i=getJComboBoxIndex(ID);
    if(i >= 0) return (ReftekPortable) refteckPortables.get(i);
    else return null;
  }
  
  // This routine should only need tweeking if key field is not same as table name
  private static void makeRefteks() {
    if (refteckPortables != null) return;
    refteckPortables=new ArrayList<ReftekPortable>(100);
    try {
      Statement stmt = DBConnectionThread.getConnection(DBConnectionThread.getDBSchema()).createStatement();   // used for query
      String s = "SELECT * FROM anss.portablereftek ORDER BY station;";
      ResultSet rs = stmt.executeQuery(s);
      while (rs.next()) {
        ReftekPortable loc = new ReftekPortable(rs);
//        Util.prt("MakeReftek() i="+refteckPortables.size()+" is "+loc.getReftek());
        refteckPortables.add(loc);
      }
      rs.close();
      stmt.close();
    }
    catch (SQLException e) {
      Util.SQLErrorPrint(e,"makeRefteks() on table SQL failed");
    }    
  }
  
  // No changes needed
  private void find() {
    if(reftek == null) return;
    if(initiating) return;
    ReftekPortable l;
    if(reftek.getSelectedIndex() == -1) {
      if(reftek.getSelectedItem() == null) return;
      l = newOne();
    } 
    else {
      l = (ReftekPortable) reftek.getSelectedItem();
    }
      
    if(l == null) return;
    String p = l.getReftek();
    if(p == null) return;
    if(p.equals("")) {
      addUpdate.setEnabled(false);
      addUpdate.setText("Enter a Reftek!");
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
          stream1.setText("1");
          rate1.setText("40.");
          chans1.setText("123");
          comps1.setText("ZNE");
          band1.setText("BH");

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
    { Util.SQLErrorPrint(E,"Reftek: SQL error getting Reftek="+p);
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
      Show.inFrame(new ReftekPortablePanel(), UC.XSIZE, UC.YSIZE);
    }
    catch(InstantiationException e) {}
  }
}
