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
import gov.usgs.anss.gui.FlagPanel;
import gov.usgs.anss.util.ErrorTrack;
import gov.usgs.anss.util.FUtil;
import gov.usgs.anss.util.Show;
import gov.usgs.anss.util.Util;
import java.awt.Dimension;
import java.sql.SQLException;
import java.util.ArrayList;

/**
 *
 * requirements : 
 * The key for the file must be the name of the table and of the
 * JComboBox variable this variable is called "channel" in the initial form
 * The table name and key name should match and start lower case (channel).
 * The Class here will be that same name but with upper case at beginning(Channel).
 * <br> 1)  Rename the location JComboBox to the "key" (channel) value.  This should start Lower
 *      case.
 *<br>  2)  The table name should match this key name and be all lower case.
 *<br>  3)  Change all Channel to ClassName of underlying data (case sensitive!)
 *<br>  4)  Change all channel key value (the JComboBox above)
 *<br>  5)  clearScreen should update swing components to new defaults
 *<br>  6)  chkForm should check all field for validity.  The errorTrack variable is
 *      used to track multiple errors.  In FUtil.chk* handle checks of the various
 *      variable types.  Date, double, and IP etc return the "canonical" form for 
 *      the variable.  These should be stored in a class variable so the latest one
 *      from "chkForm()" are available to the update writer.
 *<br>  7) newone() should be updated to create a new instance of the underlying data class.
 *<br>  8) oldone() get data from database and update all swing elements to correct values
 *<br>  9) addUpdateAction() add data base set*() operations updating all fields in the DB
 *<br> 10) ChannelPanel() constructor - good place to change backgrounds using UC.Look() any
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
public class ChannelPanel extends javax.swing.JPanel {
  
  // NOTE : here define all variables general.  "Vector v" is used for main Comboboz
  DBObject obj;
  boolean initiating=false;
  
  //USER: Here are the local variables
  int delayTime;
  double rateOut;
  double hydraReal;
  FlagPanel sendtoPanel;
  //FlagPanel linksPanel;   // This was all moved to SNW groups
  FlagPanel flagsPanel;
  FlagPanel hydraPanel;
  
  public String getChannel() {return channel.getText();}

  // This routine must validate all fields on the form.  The err (ErrorTrack) variable
  // is used by the FUtil to verify fields.  Use FUTil or custom code here for verifications
  public boolean chkForm(ErrorTrack err) {
    // Do not change
   err.reset();
   UC.Look(error);
   error.setText("");
   
   //USER:  Your error checking code goes here setting the local variables to valid settings
   // note : Use FUtil.chk* to check dates, timestamps, IP addresses, etc and put them in
   // canonical form.  store in a class variable.  For instance :
   //   sdate = FUtil.chkDate(dateTextField, err);
   // Any errors found should err.set(true);  and err.append("error message");
   Util.prt("chkForm Channel");
   delayTime = FUtil.chkInt(delay, err);
   hydraReal = FUtil.chkDouble(hydraValue,err, 0., 1.01);
   rateOut = FUtil.chkDouble(rate,err, 0., 1000.);
   if(rateOut < 20. && picker.getSelectedIndex() >= 0) {
     picker.setSelectedIndex(-1);
     error.setText("Pickers only work above 20 Hz!");
   }
   if(err.isSet()) {
     error.setBackground(UC.red);
     error.setText(err.getText());
   }
   return err.isSet();
  }
  
  // This routine must set the form to initial state.  It does not update the JCombobox
  private void clearScreen() {
    
    // Do not change
    ID.setText("");
    channel.setText("");
    
    //USER: Clear all fields for this form, this sets "defaults" for new screen
    // description.setText("");

    sendtoPanel.setAllClear();
    flagsPanel.setAllClear();
    hydraPanel.setAllClear();
    delay.setText("");
    picker.setSelectedIndex(-1);
    hydraValue.setText("0.00");
    metadataStatus.setText("");
    lastData.setText("");
    updated.setText("");
    created.setText("");
  }
 

  
  public final void populate(String p) throws SQLException {
    obj = new DBObject(DBConnectionThread.getThread(DBConnectionThread.getDBSchema()), DBConnectionThread.getDBSchema(), "channel","channel",p);

    if(obj.isNew()) {
      Util.prt("object is new"+p);
    }
    else {
      //USER: Here set all of the form fields to data from the DBObject
      //      channel.setText(obj.getString("Channel"));
      // Example : description.setText(obj.getString("description"));
      channel.setText(obj.getString("channel"));
      delay.setText(""+obj.getInt("delay"));
      PickerPanel.setJComboBoxToID(picker, obj.getInt("nsnnet"));
      rate.setText(obj.getDouble("rate")+"");
      hydraValue.setText(Util.df22(obj.getDouble("hydravalue")));
      expected.setSelected( (obj.getInt("expected") != 0 ? true : false));
      gapType.setText(obj.getString("gaptype"));
      ID.setText(""+obj.getInt("ID"));
      lastData.setText(obj.getTimestamp("lastdata").toString().substring(0,16));
      updated.setText(obj.getTimestamp("updated").toString().substring(0,16));
      created.setText(obj.getTimestamp("created").toString().substring(0,16));
      long temp = obj.getLong("sendto");
      
      for(int i=0; i<sendtoPanel.getList().size(); i++) {
          sendtoPanel.setradioSelected(i, 
              (((Sendto) sendtoPanel.getList().get(i)).getMask() & temp) != 0);
      }
      temp = obj.getLong("flags");
      for(int i=0; i<flagsPanel.getList().size(); i++) {
          flagsPanel.setradioSelected(i,
              (((Flags) flagsPanel.getList().get(i)).getMask() & temp) != 0);
      }
      temp = obj.getLong("hydraflags");
      for(int i=0; i<hydraPanel.getList().size(); i++) {
          hydraPanel.setradioSelected(i,
              (((HydraFlags) hydraPanel.getList().get(i)).getMask() & temp) != 0);
      }
      long flags = obj.getLong("mdsflags");
      String s = "";
      if( (flags & Channel.FLAG_RESPONSE_IN_MDS) == 0) s+="MDS NO RESPONSE-";
      if( (flags & Channel.FLAG_RESPONSE_BAD_PDF) != 0) s+="PDF BAD-";
      if( (flags & Channel.RESP_FLAG_SENSITIVITY_BAD) != 0) s+="BAD SENSITIVITY-";
      if( (flags & Channel.RESP_FLAG_SENSITIVITY_WARN) != 0) s+="BAD A0-";
      if( (flags & Channel.RESP_FLAG_BAD) != 0) s+="BAD-";
      if( (flags & Channel.RESP_FLAG_DO_NOT_USE) != 0) s+="DO NOT USE-";
      if( (flags & Channel.RESP_FLAG_MIXED_POLES) != 0) s+="MIXED POLES-";
      if( (flags & Channel.RESP_FLAG_A0_BAD) != 0) s+="A0 BAD-";
      if( (flags & Channel.RESP_FLAG_A0_WARN) != 0) s+="A0 WARN-";
      if( (flags & Channel.RESP_FLAG_ELEVATION_INCONSISTENT) != 0) s+="ELEVATION INCONSISTENT-";
      metadataStatus.setText(s);
      /*temp = obj.getLong("links");
      for(int i=0; i<linksPanel.getList().size(); i++) {
          linksPanel.setradioSelected(i, 
              (((Links) linksPanel.getList().get(i)).getMask() & temp) != 0);
      }*/  
    }          // End else isNew() - processing to form
  }
  
  public void updateRecord(ErrorTrack err) {
    // Do not change
    if(initiating) return;
    if(chkForm(err)) return;
    try {
      // USER : Set all of the fields in data base.  obj.setInt("thefield", ivalue);
      //obj.setString("sort1", sort1.getText());
      //obj.setString("sort2", sort2.getText());
      obj.setInt("delay", delayTime);
      Picker p = (Picker) picker.getSelectedItem();
      obj.setInt("nsnnet", (p == null?0:p.getID()));
      /*if(commgroup.getSelectedIndex() >= 0) obj.setInt("commgroupid",
          ((CommGroup)commgroup.getSelectedItem()).getID());
      else obj.setInt("commgroupid", 0);
      if(operator.getSelectedIndex() >= 0) obj.setInt("operatorid", 
          ((Operator)operator.getSelectedItem()).getID());
      else obj.setInt("operatorid", 0);
      if(protocol.getSelectedIndex() >= 0) obj.setInt("protocolid", 
          ((Protocol)protocol.getSelectedItem()).getID());
      else obj.setInt("protocolid", 0);*/
      obj.setDouble("rate", rateOut);
      obj.setInt("expected",(expected.isSelected()? 1 : 0));
      obj.setString("gaptype",gapType.getText());
      obj.setDouble("hydravalue", hydraReal);
     
      // Set the sendto flags
      ArrayList list = sendtoPanel.getList();
      long mask = 0;
      for(int i=0; i<list.size(); i++) {
        if(sendtoPanel.isradioSelected(i)) mask |= ((Sendto) list.get(i)).getMask();
      }
      obj.setLong("sendto", mask);
      
      // Set the links flags
      /*list = linksPanel.getList();
      mask = 0;
      for(int i=0; i<list.size(); i++) {
        if(linksPanel.isradioSelected(i)) mask |= ((Links) list.get(i)).getMask();
      }
      obj.setLong("links", mask);
       **/
      
      // Set the  flags
      list = flagsPanel.getList();
      mask = 0;
      for(int i=0; i<list.size(); i++) {
        if(flagsPanel.isradioSelected(i)) mask |= ((Flags) list.get(i)).getMask();
      }
      obj.setLong("flags", mask);
      // do the hydra flags.
      list = hydraPanel.getList();
      mask = 0;
      for(int i=0; i<list.size(); i++) {
        if(hydraPanel.isradioSelected(i)) mask |= ((HydraFlags) list.get(i)).getMask();
      }
      obj.setLong("hydraflags", mask);
      
      
      // Do not change
      obj.updateRecord();
      clearScreen();

    }  catch  (SQLException  E)
    { err.set(); err.appendText(channel.getText()+" SQLError Updating channel");
    }      
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
    error = new javax.swing.JTextField();
    fixed = new javax.swing.JPanel();
    chanDesc = new javax.swing.JLabel();
    labGapType = new javax.swing.JLabel();
    gapType = new javax.swing.JTextField();
    labDelay = new javax.swing.JLabel();
    delay = new javax.swing.JTextField();
    reload = new javax.swing.JButton();
    ID = new javax.swing.JTextField();
    channel = new javax.swing.JTextField();
    labID = new javax.swing.JLabel();
    rate = new javax.swing.JTextField();
    expected = new javax.swing.JRadioButton();
    labLast = new javax.swing.JLabel();
    lastData = new javax.swing.JTextField();
    labUpdated = new javax.swing.JLabel();
    updated = new javax.swing.JTextField();
    created = new javax.swing.JTextField();
    labCreated = new javax.swing.JLabel();
    hydraValueLab = new javax.swing.JLabel();
    hydraValue = new javax.swing.JTextField();
    picker = PickerPanel.getJComboBox();
    labPick = new javax.swing.JLabel();
    metadataStatus = new javax.swing.JTextField();

    setMinimumSize(new java.awt.Dimension(723, 145));
    setPreferredSize(new java.awt.Dimension(208, 150));

    error.setBackground(new java.awt.Color(204, 204, 204));
    error.setColumns(80);
    error.setEditable(false);
    error.setFont(new java.awt.Font("Monospaced", 0, 10)); // NOI18N
    add(error);

    fixed.setMaximumSize(new java.awt.Dimension(675, 500));
    fixed.setMinimumSize(new java.awt.Dimension(675, 100));
    fixed.setPreferredSize(new java.awt.Dimension(675, 78));
    fixed.setLayout(new java.awt.GridBagLayout());

    chanDesc.setText("Chan(NSCL):");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    fixed.add(chanDesc, gridBagConstraints);

    labGapType.setText("GapType :");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 4;
    gridBagConstraints.gridy = 2;
    fixed.add(labGapType, gridBagConstraints);

    gapType.setColumns(3);
    gapType.setMinimumSize(new java.awt.Dimension(50, 28));
    gapType.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        gapTypeActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 5;
    gridBagConstraints.gridy = 2;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    fixed.add(gapType, gridBagConstraints);

    labDelay.setText("Delay(sec):");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 4;
    gridBagConstraints.gridy = 3;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    fixed.add(labDelay, gridBagConstraints);

    delay.setColumns(6);
    delay.setMinimumSize(new java.awt.Dimension(40, 28));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 5;
    gridBagConstraints.gridy = 3;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    fixed.add(delay, gridBagConstraints);

    reload.setText("Reload");
    reload.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        reloadActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 5;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    fixed.add(reload, gridBagConstraints);

    ID.setBackground(new java.awt.Color(192, 192, 192));
    ID.setColumns(8);
    ID.setEditable(false);
    ID.setMinimumSize(new java.awt.Dimension(110, 28));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 3;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    fixed.add(ID, gridBagConstraints);

    channel.setBackground(new java.awt.Color(204, 204, 204));
    channel.setColumns(12);
    channel.setEditable(false);
    channel.setMinimumSize(new java.awt.Dimension(120, 22));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 1;
    fixed.add(channel, gridBagConstraints);

    labID.setText("ID:");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 2;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    fixed.add(labID, gridBagConstraints);

    rate.setBackground(new java.awt.Color(192, 192, 192));
    rate.setColumns(6);
    rate.setEditable(false);
    rate.setMinimumSize(new java.awt.Dimension(86, 28));
    rate.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        rateActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 4;
    gridBagConstraints.gridy = 1;
    fixed.add(rate, gridBagConstraints);

    expected.setText("Expected");
    expected.setMargin(new java.awt.Insets(0, 0, 0, 0));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 6;
    gridBagConstraints.gridy = 1;
    fixed.add(expected, gridBagConstraints);

    labLast.setText("Last:");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 2;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    fixed.add(labLast, gridBagConstraints);

    lastData.setEditable(false);
    lastData.setBackground(new java.awt.Color(204, 204, 204));
    lastData.setColumns(14);
    lastData.setFont(new java.awt.Font("Monospaced", 0, 10)); // NOI18N
    lastData.setMinimumSize(new java.awt.Dimension(115, 28));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 2;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    fixed.add(lastData, gridBagConstraints);

    labUpdated.setText("Upd:");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 2;
    gridBagConstraints.gridy = 2;
    fixed.add(labUpdated, gridBagConstraints);

    updated.setEditable(false);
    updated.setBackground(new java.awt.Color(204, 204, 204));
    updated.setColumns(20);
    updated.setFont(new java.awt.Font("Monospaced", 0, 10)); // NOI18N
    updated.setMinimumSize(new java.awt.Dimension(115, 28));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 3;
    gridBagConstraints.gridy = 2;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    fixed.add(updated, gridBagConstraints);

    created.setBackground(new java.awt.Color(204, 204, 204));
    created.setColumns(20);
    created.setEditable(false);
    created.setFont(new java.awt.Font("Monospaced", 0, 10)); // NOI18N
    created.setMinimumSize(new java.awt.Dimension(115, 23));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 3;
    gridBagConstraints.gridy = 3;
    fixed.add(created, gridBagConstraints);

    labCreated.setText("Crd:");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 2;
    gridBagConstraints.gridy = 3;
    fixed.add(labCreated, gridBagConstraints);

    hydraValueLab.setText("HydraValue:");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 6;
    gridBagConstraints.gridy = 2;
    fixed.add(hydraValueLab, gridBagConstraints);

    hydraValue.setColumns(5);
    hydraValue.setMinimumSize(new java.awt.Dimension(64, 28));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 7;
    gridBagConstraints.gridy = 2;
    fixed.add(hydraValue, gridBagConstraints);

    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 3;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
    fixed.add(picker, gridBagConstraints);

    labPick.setText("Picker:");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 3;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_END;
    fixed.add(labPick, gridBagConstraints);

    add(fixed);

    metadataStatus.setBackground(new java.awt.Color(204, 204, 204));
    metadataStatus.setColumns(50);
    metadataStatus.setEditable(false);
    metadataStatus.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        metadataStatusActionPerformed(evt);
      }
    });
    add(metadataStatus);
  }// </editor-fold>//GEN-END:initComponents

  private void gapTypeActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_gapTypeActionPerformed
// TODO add your handling code here:
  }//GEN-LAST:event_gapTypeActionPerformed

  private void rateActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_rateActionPerformed
// TODO add your handling code here:
  }//GEN-LAST:event_rateActionPerformed

  private void reloadActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_reloadActionPerformed
// TODO add your handling code here:
    //buildSentoPanel();
    /*CommGroupPanel.getJComboBox(commgroup);
    OperatorPanel.getJComboBox(operator);
    ProtocolPanel.getJComboBox(protocol);*/
  }//GEN-LAST:event_reloadActionPerformed

  private void metadataStatusActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_metadataStatusActionPerformed
    // TODO add your handling code here:
}//GEN-LAST:event_metadataStatusActionPerformed
  
  
  // Variables declaration - do not modify//GEN-BEGIN:variables
  private javax.swing.JTextField ID;
  private javax.swing.JLabel chanDesc;
  private javax.swing.JTextField channel;
  private javax.swing.JTextField created;
  private javax.swing.JTextField delay;
  private javax.swing.JTextField error;
  private javax.swing.JRadioButton expected;
  private javax.swing.JPanel fixed;
  private javax.swing.JTextField gapType;
  private java.awt.GridBagLayout gridBagLayout1;
  private javax.swing.JTextField hydraValue;
  private javax.swing.JLabel hydraValueLab;
  private javax.swing.JLabel labCreated;
  private javax.swing.JLabel labDelay;
  private javax.swing.JLabel labGapType;
  private javax.swing.JLabel labID;
  private javax.swing.JLabel labLast;
  private javax.swing.JLabel labPick;
  private javax.swing.JLabel labUpdated;
  private javax.swing.JTextField lastData;
  private javax.swing.JTextField metadataStatus;
  private javax.swing.JComboBox picker;
  private javax.swing.JTextField rate;
  private javax.swing.JButton reload;
  private javax.swing.JTextField updated;
  // End of variables declaration//GEN-END:variables
  /** Creates new form ChannelPanel
   *@param seed The seed channel to display
   * @throws SQLException 
   */
  public ChannelPanel(String seed) throws SQLException {
    initiating=true;
    initComponents();
    Look();                   // Set color background
    initiating=false;
    // USER:This is a good place for UC.Look(components) to get rid of the ugly Grey
    //CommGroupPanel.getJComboBox(commgroup);
    //OperatorPanel.getJComboBox(operator);
    UC.Look(fixed);
    ArrayList<FlagPanel> subpanels = new ArrayList<FlagPanel>(3);
    int wid=700;
    sendtoPanel = new FlagPanel(SendtoPanel.getJComboBox(),wid); subpanels.add(sendtoPanel);
    flagsPanel = new FlagPanel(FlagsPanel.getJComboBox(),wid); subpanels.add(flagsPanel);
    hydraPanel = new FlagPanel(HydraFlagsPanel.getJComboBox(),wid); subpanels.add(hydraPanel);
    UC.Look(sendtoPanel);
    UC.Look(flagsPanel);
    UC.Look(hydraPanel);
    
    //linksPanel = new FlagPanel(LinksPanel.getJComboBox(),wid); subpanels.add(linksPanel);
    javax.swing.JPanel allflagsPanel = new AllFlagsPanel(subpanels);
    add(allflagsPanel);
    Dimension fixedSize = fixed.getPreferredSize();
    Dimension allflagsSize = allflagsPanel.getPreferredSize();
    int width = (int) Math.max(fixedSize.getWidth(), allflagsSize.getWidth());
    int height = (int) (fixedSize.getHeight()+allflagsSize.getHeight());
    setPreferredSize(new Dimension(width , height+60));
    setMinimumSize(new Dimension(width , height+60));
    setBorder(new javax.swing.border.EtchedBorder());
    
    //allflagsScroll.add(allflags);
    //allflagsScroll.setViewportView(allflags);
    clearScreen();                    // Start with empty screen
    populate(seed);
  }
  private void Look() {
    UC.Look(this);                    // Set color background
    
  }
  /** This main displays the form Pane by itself
   *@param args command line args ignored*/
  public static void main(String args[]) throws SQLException {
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
      Show.inFrame(new ChannelPanel("USDUG  BHZ"), UC.XSIZE, UC.YSIZE);
    }
    catch(InstantiationException e) {}
  }
}
