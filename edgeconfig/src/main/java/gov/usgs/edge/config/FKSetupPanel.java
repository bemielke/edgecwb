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
 * JComboBox variable this variable is called "fksetup" in the initial form
 * The table name and key name should match and start lower case (fksetup).
 * The Class here will be that same name but with upper case at beginning(FKSetup).
 * <br> 1)  Rename the location JComboBox to the "key" (fksetup) value.  This should start Lower
 *      case.
 *<br>  2)  The table name should match this key name and be all lower case.
 *<br>  3)  Change all FKSetup to ClassName of underlying data (case sensitive!)
 *<br>  4)  Change all fksetup key value (the JComboBox above)
 *<br>  5)  clearScreen should update swing components to new defaults
 *<br>  6)  chkForm should check all field for validity.  The errorTrack variable is
 *      used to track multiple errors.  In FUtil.chk* handle checks of the various
 *      variable types.  Date, double, and IP etc return the "canonical" form for 
 *      the variable.  These should be stored in a class variable so the latest one
 *      from "chkForm()" are available to the update writer.
 *<br>  7) newone() should be updated to create a new instance of the underlying data class.
 *<br>  8) oldone() get data from database and update all swing elements to correct values
 *<br>  9) addUpdateAction() add data base set*() operations updating all fields in the DB
 *<br> 10) FKSetupPanel() constructor - good place to change backgrounds using UC.Look() any
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
public class FKSetupPanel extends javax.swing.JPanel {
  //USER: set this for the upper case conversion of keys.
  private final boolean keyUpperCase=true;    // set this true if all keys must be upper case
  //USER: Here are the local variables
  // NOTE : here define all variables general.  "ArrayList v" is used for main Comboboz
  double fkhpcd, fklpcd,beamwind,highpass, lowpass,kmaxd, snri;
  int  ftleni,npolesi,npassi,nki, latency;
  private void doInit() {
  // Good place to add UC.Looks()
    deleteItem.setVisible(true);
    reload.setVisible(false);
    idLab.setVisible(true);
    ID.setVisible(true);
    changeTo.setVisible(true);
    labChange.setVisible(true);
    UC.Look(this);                    // Set color background
    UC.Look(filterPanel);
    UC.Look(fkPanel);
    UC.Look(chanPanel);
  }
  private void doAddUpdate() throws SQLException {
    // USER : Set all of the fields in data base.  obj.setInt("thefield", ivalue);
    obj.setInt("roleid", (role.getSelectedIndex() >= 0?((Role) role.getSelectedItem()).getID():0));
    obj.setDouble("fkhpc", fkhpcd);
    obj.setDouble("fklpc", fklpcd);
    obj.setDouble("beamwindow", beamwind);
    obj.setInt("beammethod", beammethod.getSelectedIndex());
    //obj.setInt("ftlen", Integer.parseInt(ftlen.getText()));
    obj.setInt("latencysecs", latency);
    obj.setDouble("snrlimit", snri);
    obj.setInt("ftlen", ftleni);
    obj.setDouble("highpassfreq", highpass);
    obj.setDouble("lowpassfreq", lowpass);
    obj.setInt("npoles", npolesi);
    obj.setInt("npass", npassi);
    obj.setDouble("kmax", kmaxd);
    obj.setInt("nk", nki);
    obj.setString("args", args.getText());
    obj.setString("refchan", refchan.getText());
    obj.setString("channels", Util.chkTrailingNewLine(channels.getText()));
    obj.setString("comment", Util.chkTrailingNewLine(channelComment.getText()));
    
  }
  private void doOldOne() throws SQLException {
      //USER: Here set all of the form fields to data from the DBObject
      //      fksetup.setText(obj.getString("FKSetup"));
      // Example : description.setText(obj.getString("description"));
    RolePanel.setJComboBoxToID(role, obj.getInt("roleid"));
    fkhpc.setText(obj.getDouble("fkhpc")+"");
    fklpc.setText(obj.getDouble("fklpc")+"");
    beamwindow.setText(obj.getDouble("beamwindow")+"");
    beammethod.setSelectedIndex(obj.getInt("beammethod"));
    latencySec.setText(obj.getInt("latencysecs")+"");
    snrLimit.setText(obj.getDouble("snrlimit")+"");
    highpassfreq.setText(obj.getDouble("highpassfreq")+"");
    lowpassfreq.setText(obj.getDouble("lowpassfreq")+"");
    npoles.setText(obj.getInt("npoles")+"");
    npass.setText(obj.getInt("npass")+"");
    kmax.setText(obj.getDouble("kmax")+"");
    nk.setText(obj.getInt("nk")+"");
    args.setText(obj.getString("args"));
    refchan.setText(obj.getString("refchan"));
    channels.setText(obj.getString("channels"));
    channelComment.setText(obj.getString("comment"));
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
   fkhpcd = FUtil.chkDouble(fkhpc, err, 0., 100.);
   fklpcd = FUtil.chkDouble(fklpc, err, 0., 100.);
   beamwind = FUtil.chkDouble(beamwindow, err, 0., 100.);
   highpass = FUtil.chkDouble(highpassfreq, err, 0., 100.);
   lowpass = FUtil.chkDouble(lowpassfreq, err, 0., 100.);
   kmaxd = FUtil.chkDouble(kmax, err, 0., 10.);
   ftleni = 0;
   nki = FUtil.chkInt(nk, err, 0, 201);
   npolesi = FUtil.chkInt(npoles, err, 0, 10);
   npassi = FUtil.chkInt(npass, err, 0, 10);
   latency = FUtil.chkInt(latencySec, err, 10, 3600);
   snri = FUtil.chkDouble(snrLimit, err, 3., 200.);
   
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
    addUpdate.setText("Enter a FKSetup");
    deleteItem.setEnabled(false);
    changeTo.setText("");
    
    //USER: Clear all fields for this form, this sets "defaults" for new screen
    // description.setText("");
    role.setSelectedIndex(-1);
    fkhpc.setText("");
    fklpc.setText("");
    beamwindow.setText("");
    beammethod.setSelectedIndex(0);
    latencySec.setText("");
    snrLimit.setText("");
    highpassfreq.setText("");
    lowpassfreq.setText("");
    npoles.setText("");
    npass.setText("");
    kmax.setText("");
    nk.setText("");
    args.setText("");
    refchan.setText("");
    channels.setText("");
    channelComment.setText("");
  }


  /**********************  NO USER MODIFICATION BELOW HERE EXCEPT FOR ACTIONS **************/
  static ArrayList<FKSetup> v;             // ArrayList containing objects of this FKSetup Type
  DBObject obj;
  ErrorTrack err=new ErrorTrack();
  boolean initiating=false;

  private void setDefaults() {
    role.setSelectedIndex(-1);
    fkhpc.setText("1.");
    fklpc.setText("3.");
    beamwindow.setText("10.");
    beammethod.setSelectedIndex(0);
    latencySec.setText("300");
    snrLimit.setText("25"); 
    highpassfreq.setText("0.");
    lowpassfreq.setText("0.");
    npoles.setText("0");
    npass.setText("0");
    kmax.setText("1.");
    nk.setText("81");
    args.setText("");
    refchan.setText("");
    channels.setText("");    // makeFKSetup(inID, loc, role, fkhpc, fklpc, beamwindow, beammethod, ftlen, latency,
    channelComment.setText("");
  }
  private FKSetup newOne() {
    //    highpassfreq,lowpassfreq, npoles, npass, kmax, nk, args, refchan, channels//, lon
     
    return new FKSetup(0, ((String) fksetup.getSelectedItem()).toUpperCase(), //, more
      0, 0., 0., 10., 0, 0, 0, 0., 0., 0, 0, 0., 0, "", "", "", 0., "" );
  }
  
  private void oldOne(String p) throws SQLException {
    obj = new DBObject(DBConnectionThread.getThread(DBConnectionThread.getDBSchema()), 
            "edge",       // Schema, if this is not right in URL load, it must be explicit here
            "fk","fk",p);                     // table name and field name are usually the same

    if(obj.isNew()) {
      //Util.prt("object is new"+p);
    }
    else {
      //USER: Here set all of the form fields to data from the DBObject
      //      fksetup.setText(obj.getString("FKSetup"));
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
    fksetup = getJComboBox();
    error = new javax.swing.JTextField();
    bottomStuff = new javax.swing.JPanel();
    addUpdate = new javax.swing.JButton();
    labChange = new javax.swing.JLabel();
    changeTo = new javax.swing.JTextField();
    deleteItem = new javax.swing.JButton();
    reload = new javax.swing.JButton();
    idLab = new javax.swing.JLabel();
    ID = new javax.swing.JTextField();
    rolelab = new javax.swing.JLabel();
    role = RolePanel.getJComboBox();
    fkPanel = new javax.swing.JPanel();
    windowLab = new javax.swing.JLabel();
    beamwindow = new javax.swing.JTextField();
    kmaxLab = new javax.swing.JLabel();
    kmax = new javax.swing.JTextField();
    nkLab = new javax.swing.JLabel();
    nk = new javax.swing.JTextField();
    fkhpcLab = new javax.swing.JLabel();
    fkhpc = new javax.swing.JTextField();
    fklpcLab = new javax.swing.JLabel();
    fklpc = new javax.swing.JTextField();
    latencyLab = new javax.swing.JLabel();
    snrLab = new javax.swing.JLabel();
    latencySec = new javax.swing.JTextField();
    snrLimit = new javax.swing.JTextField();
    methodLab = new javax.swing.JLabel();
    beammethod = new javax.swing.JComboBox();
    refchanLab = new javax.swing.JLabel();
    refchan = new javax.swing.JTextField();
    chanLab = new javax.swing.JLabel();
    argslab = new javax.swing.JLabel();
    args = new javax.swing.JTextField();
    filterPanel = new javax.swing.JPanel();
    hipassLab = new javax.swing.JLabel();
    highpassfreq = new javax.swing.JTextField();
    lowpassLab = new javax.swing.JLabel();
    lowpassfreq = new javax.swing.JTextField();
    npoleLab = new javax.swing.JLabel();
    npoles = new javax.swing.JTextField();
    npassLab = new javax.swing.JLabel();
    npass = new javax.swing.JTextField();
    chanPanel = new javax.swing.JPanel();
    jScrollPane2 = new javax.swing.JScrollPane();
    channels = new javax.swing.JTextArea();
    commentLab = new javax.swing.JLabel();
    jScrollPane1 = new javax.swing.JScrollPane();
    channelComment = new javax.swing.JTextArea();

    setLayout(new java.awt.GridBagLayout());

    labMain.setText("Array:");
    labMain.setToolTipText("This button commits either a new item or the changes for an updating item.  Its label with change to reflect that.");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_END;
    add(labMain, gridBagConstraints);

    fksetup.setEditable(true);
    fksetup.setToolTipText("<html>\nSelect an item to edit in the JComboBox or type in the name of an item.   \n<br>If the typed name does not match an existing item, an new item is assumed.  \n<br>To disable an array change its name to start with \"#\" using the \"Change To\" option at the bottom of the screen.\n</html>");
    fksetup.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        fksetupActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(fksetup, gridBagConstraints);

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
    gridBagConstraints.gridy = 29;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(bottomStuff, gridBagConstraints);

    rolelab.setText("Role:");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 2;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_END;
    add(rolelab, gridBagConstraints);

    role.setMinimumSize(new java.awt.Dimension(100, 22));
    role.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        roleActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 2;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
    add(role, gridBagConstraints);

    fkPanel.setMinimumSize(new java.awt.Dimension(625, 65));

    windowLab.setText("Window(sec):");
    fkPanel.add(windowLab);

    beamwindow.setColumns(4);
    beamwindow.setToolTipText("Set the time widow for calcuations.  The FK is computed in overlapping windows at 1/2 this interval.");
    beamwindow.setMinimumSize(new java.awt.Dimension(60, 28));
    beamwindow.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        beamwindowActionPerformed(evt);
      }
    });
    fkPanel.add(beamwindow);

    kmaxLab.setText("Kmax:");
    fkPanel.add(kmaxLab);

    kmax.setColumns(3);
    kmax.setToolTipText("Maximum value of K over which to search.  The area of +/-k is searched.");
    kmax.setMinimumSize(new java.awt.Dimension(50, 28));
    fkPanel.add(kmax);

    nkLab.setText("Nk:");
    fkPanel.add(nkLab);

    nk.setColumns(3);
    nk.setToolTipText("Number of intervals of k to be searched (the grid in FK is Nk x Nk).");
    nk.setMinimumSize(new java.awt.Dimension(50, 28));
    fkPanel.add(nk);

    fkhpcLab.setText("FK HP (Hz):");
    fkPanel.add(fkhpcLab);

    fkhpc.setColumns(3);
    fkhpc.setToolTipText("FK calculations need a highpass filter frequence to reject longer periods that are not of interest.");
    fkhpc.setMinimumSize(new java.awt.Dimension(50, 28));
    fkPanel.add(fkhpc);

    fklpcLab.setText("FK LP (Hz):");
    fkPanel.add(fklpcLab);

    fklpc.setColumns(3);
    fklpc.setToolTipText("FK low pass frequency.  This and the HP frequency set the frequency interval for the FK search.");
    fklpc.setMinimumSize(new java.awt.Dimension(50, 28));
    fkPanel.add(fklpc);

    latencyLab.setText("Latency:");
    fkPanel.add(latencyLab);

    snrLab.setText("SNR:");
    fkPanel.add(snrLab);

    latencySec.setColumns(5);
    latencySec.setToolTipText("<html>\nIf the calculation cannot find all of the channels configured for the FK calculation \n<br>and it is currently this number of seconds behind real time and the number\n<br>of channels returned is bigger than 1/2 of the number configured, then \n<br>the calculation can proceed with the current number of channels returned\n<br>If the number ofchannels available is bigger than the current minimum number, \n<br>the current number of available channels becomes the limit.\n<html>");
    latencySec.setMinimumSize(new java.awt.Dimension(100, 28));
    fkPanel.add(latencySec);

    snrLimit.setColumns(4);
    snrLimit.setToolTipText("The SNR limit in amplitude which cause the beam to declare there is signal and to return back azimuth etc.");
    snrLimit.setMinimumSize(new java.awt.Dimension(30, 28));
    fkPanel.add(snrLimit);

    methodLab.setText("Beam Method:");
    fkPanel.add(methodLab);

    beammethod.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "Time Domain stack", "1st Root", "2nd Root", "3rd Root", "4th Root" }));
    fkPanel.add(beammethod);

    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 3;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
    add(fkPanel, gridBagConstraints);

    refchanLab.setText("RefChan(NNSSSSSCCC):");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 5;
    add(refchanLab, gridBagConstraints);

    refchan.setColumns(30);
    refchan.setToolTipText("<html>\nThis channel is the \"reference\" point at which the beam will be calculated.  \n<br>\nBy default it is NOT one of the elements for the computation.  To make it a used element add '+' at the beginning of the name.\n</html>\n");
    refchan.setMinimumSize(new java.awt.Dimension(150, 28));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 5;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
    add(refchan, gridBagConstraints);

    chanLab.setText("Channels");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 6;
    add(chanLab, gridBagConstraints);

    argslab.setText("Args:");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 10;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_END;
    add(argslab, gridBagConstraints);

    args.setColumns(10);
    args.setToolTipText("<html>\nAddtional argument to pass to the FK object (see for details), but only the \n<br>\n-dbg    Turn on more logging\n<br> \n-cwb   Send all of FK generation statistics to the CWB - this is seldom done outside of debugging mode.\n-cwbip    IP of the CWB\n-cwbport Port to send data to on CWB.\n</html>");
    args.setMinimumSize(new java.awt.Dimension(100, 28));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 10;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
    add(args, gridBagConstraints);

    filterPanel.setMinimumSize(new java.awt.Dimension(650, 30));

    hipassLab.setText("TD Hi Pass:");
    filterPanel.add(hipassLab);

    highpassfreq.setColumns(8);
    highpassfreq.setMinimumSize(new java.awt.Dimension(60, 28));
    highpassfreq.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        highpassfreqActionPerformed(evt);
      }
    });
    filterPanel.add(highpassfreq);

    lowpassLab.setText("TD Low Pass:");
    filterPanel.add(lowpassLab);

    lowpassfreq.setColumns(8);
    lowpassfreq.setMinimumSize(new java.awt.Dimension(60, 28));
    filterPanel.add(lowpassfreq);

    npoleLab.setText("Npoles:");
    filterPanel.add(npoleLab);

    npoles.setColumns(3);
    npoles.setMinimumSize(new java.awt.Dimension(25, 28));
    filterPanel.add(npoles);

    npassLab.setText("Npass:");
    filterPanel.add(npassLab);

    npass.setColumns(3);
    npass.setMinimumSize(new java.awt.Dimension(25, 28));
    filterPanel.add(npass);

    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 25;
    add(filterPanel, gridBagConstraints);

    chanPanel.setMinimumSize(new java.awt.Dimension(625, 250));
    chanPanel.setPreferredSize(new java.awt.Dimension(625, 250));

    jScrollPane2.setMinimumSize(new java.awt.Dimension(150, 200));
    jScrollPane2.setPreferredSize(new java.awt.Dimension(150, 200));

    channels.setColumns(20);
    channels.setRows(20);
    channels.setToolTipText("<html>\nEnter a list of NNSSSSSCCC fixed field.  To disable the use of the channel start it with '-', '#', or '!'.\n<br>\nIf a channel is \"disabled\", it is not counted in the number of stations that must be present for the FK calculation to proceed.\n</html>");
    jScrollPane2.setViewportView(channels);

    chanPanel.add(jScrollPane2);

    commentLab.setText("Comment:");
    chanPanel.add(commentLab);

    jScrollPane1.setMinimumSize(new java.awt.Dimension(325, 200));
    jScrollPane1.setPreferredSize(new java.awt.Dimension(325, 200));

    channelComment.setColumns(50);
    channelComment.setRows(20);
    jScrollPane1.setViewportView(channelComment);

    chanPanel.add(jScrollPane1);

    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 6;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
    add(chanPanel, gridBagConstraints);
  }// </editor-fold>//GEN-END:initComponents

  private void addUpdateActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_addUpdateActionPerformed
    // Add your handling code here:
    
    // Do not change
    if(initiating) return;
    if(chkForm()) return;
    try {
      String p = fksetup.getSelectedItem().toString();
      if(keyUpperCase) p = p.toUpperCase();                  // USER : drop this to allow Mixed case
      if(!obj.isNew()) obj.refreshRecord();        // Refresh the result set so the sets will not fail on stale one
      if(!changeTo.getText().equals("")) p = changeTo.getText();
      obj.setString("fk",p);
      
      // USER : Set all of the fields in data base.  obj.setInt("thefield", ivalue);
      doAddUpdate();
      
      // Do not change
      obj.updateRecord();
      v=null;       // force reload of combo box
      getJComboBox(fksetup);
      clearScreen();
      fksetup.setSelectedIndex(-1);
    }  catch  (SQLException  E)
    { Util.SQLErrorPrint(E,"fksetup: update failed partno="+fksetup.getSelectedItem());
      error.setText("Could Not Update File!");
      error.setBackground(UC.red);
      addUpdate.setEnabled(true);
    }      
  }//GEN-LAST:event_addUpdateActionPerformed

  private void fksetupActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_fksetupActionPerformed
    // Add your handling code here:
   find();
 
 
  }//GEN-LAST:event_fksetupActionPerformed

private void deleteItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_deleteItemActionPerformed
  try {
    obj.deleteRecord();
    fksetup.removeItem(fksetup.getSelectedItem());
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
    // TODO add your handling code here:
  }//GEN-LAST:event_roleActionPerformed

  private void highpassfreqActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_highpassfreqActionPerformed
    // TODO add your handling code here:
  }//GEN-LAST:event_highpassfreqActionPerformed

  private void beamwindowActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_beamwindowActionPerformed
    // TODO add your handling code here:
  }//GEN-LAST:event_beamwindowActionPerformed
  
  
  // Variables declaration - do not modify//GEN-BEGIN:variables
  private javax.swing.JTextField ID;
  private javax.swing.JButton addUpdate;
  private javax.swing.JTextField args;
  private javax.swing.JLabel argslab;
  private javax.swing.JComboBox beammethod;
  private javax.swing.JTextField beamwindow;
  private javax.swing.JPanel bottomStuff;
  private javax.swing.JLabel chanLab;
  private javax.swing.JPanel chanPanel;
  private javax.swing.JTextField changeTo;
  private javax.swing.JTextArea channelComment;
  private javax.swing.JTextArea channels;
  private javax.swing.JLabel commentLab;
  private javax.swing.JButton deleteItem;
  private javax.swing.JTextField error;
  private javax.swing.JPanel filterPanel;
  private javax.swing.JPanel fkPanel;
  private javax.swing.JTextField fkhpc;
  private javax.swing.JLabel fkhpcLab;
  private javax.swing.JTextField fklpc;
  private javax.swing.JLabel fklpcLab;
  private javax.swing.JComboBox fksetup;
  private java.awt.GridBagLayout gridBagLayout1;
  private javax.swing.JTextField highpassfreq;
  private javax.swing.JLabel hipassLab;
  private javax.swing.JLabel idLab;
  private javax.swing.JScrollPane jScrollPane1;
  private javax.swing.JScrollPane jScrollPane2;
  private javax.swing.JTextField kmax;
  private javax.swing.JLabel kmaxLab;
  private javax.swing.JLabel labChange;
  private javax.swing.JLabel labMain;
  private javax.swing.JLabel latencyLab;
  private javax.swing.JTextField latencySec;
  private javax.swing.JLabel lowpassLab;
  private javax.swing.JTextField lowpassfreq;
  private javax.swing.JLabel methodLab;
  private javax.swing.JTextField nk;
  private javax.swing.JLabel nkLab;
  private javax.swing.JTextField npass;
  private javax.swing.JLabel npassLab;
  private javax.swing.JLabel npoleLab;
  private javax.swing.JTextField npoles;
  private javax.swing.JTextField refchan;
  private javax.swing.JLabel refchanLab;
  private javax.swing.JButton reload;
  private javax.swing.JComboBox role;
  private javax.swing.JLabel rolelab;
  private javax.swing.JLabel snrLab;
  private javax.swing.JTextField snrLimit;
  private javax.swing.JLabel windowLab;
  // End of variables declaration//GEN-END:variables
  /** Creates new form FKSetupPanel. */
  public FKSetupPanel() {
    initiating=true;
    initComponents();
    getJComboBox(fksetup);                // set up the key JComboBox
    fksetup.setSelectedIndex(-1);    // Set selected type
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
  /** Update a JComboBox to represent the rows represented by this panel
   *@param b The JComboBox to Update
   */
  public static void getJComboBox(JComboBox b) {
    b.removeAllItems();
    makeFKSetups();
    for (FKSetup v1 : v) {
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
    //Util.prt("FKSetupPanel.setJComboBoxToID id="+ID);
    for(int i=0; i<b.getItemCount(); i++) 
      if(((FKSetup) b.getItemAt(i)).getID() == ID) {
          b.setSelectedIndex(i);
          //Util.prt("FKSetupPanel.setJComboBoxToID id="+ID+" found at "+i);
      }
  }  
  
  /** Given a database ID find the index into a JComboBox consistent with this panel
   *@param ID The row ID from the database
   *@return The index in the JComboBox with this ID
   */
  public static int getJComboBoxIndex(int ID) {
    if(v == null) makeFKSetups();
    for(int i=0; i<v.size(); i++) if( ID == ((FKSetup) v.get(i)).getID()) return i;
    return -1;
  }
  /** Cause the main JComboBox to be reloaded. */
  public static void reload() {
    v = null;
    makeFKSetups();
  }
  /** return a ArrayList with all of the FKSetup
   * @return The ArrayList with the fksetup
   */
  public static ArrayList<FKSetup> getFKSetupVector() {
    if(v == null) makeFKSetups();
    return v;
  }    
	/** return a ArrayList with all of the FKSetup
   * @return The ArrayList with the fksetup
   */
  public static ArrayList<FKSetup> getFKSetupArrayList() {
    if(v == null) makeFKSetups();
    return v;
  }
  /** Get the item corresponding to database ID
   *@param ID the database Row ID
   *@return The FKSetup row with this ID
   */
  public static FKSetup getFKSetupWithID(int ID) {
    if(v == null) makeFKSetups();
    int i=getJComboBoxIndex(ID);
    if(i >= 0) return (FKSetup) v.get(i);
    else return null;
  }
  
  // This routine should only need tweeking if key field is not same as table name
  private static void makeFKSetups() {
    if (v != null) return;
    v=new ArrayList<FKSetup>(100);
    try {
      try (Statement stmt = DBConnectionThread.getConnection(DBConnectionThread.getDBSchema()).createStatement() // used for query
      ) {
        String s = "SELECT * FROM "+"edge.fk ORDER BY fk;";
        try (ResultSet rs = stmt.executeQuery(s)) {
          while (rs.next()) {
            FKSetup loc = new FKSetup(rs);
            //        Util.prt("MakeFKSetup() i="+v.size()+" is "+loc.getFKSetup());
            v.add(loc);
          }
        }
      }
    }
    catch (SQLException e) {
      Util.SQLErrorPrint(e,"makeFKSetups() on table SQL failed");
    }    
  }
  
  // No changes needed
  private void find() {
    if(fksetup == null) return;
    if(initiating) return;
    FKSetup l;
    if(fksetup.getSelectedIndex() == -1) {
      if(fksetup.getSelectedItem() == null) return;
      l = newOne();
    } 
    else {
      l = (FKSetup) fksetup.getSelectedItem();
    }
      
    if(l == null) return;
    String p = l.getFKSetup();
    if(p == null) return;
    if(p.equals("")) {
      addUpdate.setEnabled(false);
      deleteItem.setEnabled(false);
      addUpdate.setText("Enter a FKSetup!");
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
          setDefaults();
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
    { Util.SQLErrorPrint(E,"FKSetup: SQL error getting FKSetup="+p);
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
      jcjbl = new DBConnectionThread(Util.getProperty("DBServer"),"user", 
              DBConnectionThread.getDBCatalog(),
               true, false, DBConnectionThread.getDBSchema(), null);
      if(!DBConnectionThread.waitForConnection(DBConnectionThread.getDBSchema()))
        if(!DBConnectionThread.waitForConnection(DBConnectionThread.getDBSchema()))
          if(!DBConnectionThread.waitForConnection(DBConnectionThread.getDBSchema()))
          {Util.prt("COuld not connect to DB "+jcjbl); System.exit(1);}
      Show.inFrame(new FKSetupPanel(), UC.XSIZE, UC.YSIZE);
    }
    catch(InstantiationException e) {
      e.printStackTrace();
    }


  }
}
