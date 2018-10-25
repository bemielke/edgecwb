/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */

package gov.usgs.edge.snw;
import gov.usgs.anss.db.DBConnectionThread;
import gov.usgs.anss.db.DBObject;
import gov.usgs.anss.gui.FlagPanel;
import gov.usgs.anss.util.ErrorTrack;
import gov.usgs.anss.util.FUtil;
import gov.usgs.anss.util.Util;
import gov.usgs.edge.config.UC;
import java.awt.Dimension;
import java.sql.Date;
import java.sql.SQLException;
import java.text.DateFormat;
import java.util.ArrayList;
/**
 *
 * @author  davidketchum
 */
public final  class SNWStationSubPanel extends javax.swing.JPanel {
  private DBObject obj;
  private final ErrorTrack err = new ErrorTrack();
  private Date disDate;
  private boolean initiating;
  private final FlagPanel groups;
  private DateFormat dateformat;
  
  // This routine must validate all fields on the form.  The err (ErrorTrack) variable
  // is used by the FUtil to verify fields.  Use FUTil or custom code here for verifications
  public boolean chkForm(ErrorTrack err) {
    // Do not change
   err.reset();
   
   //USER:  Your error checking code goes here setting the local variables to valid settings
   // note : Use FUtil.chk* to check dates, timestamps, IP addresses, etc and put them in
   // canonical form.  store in a class variable.  For instance :
   //   sdate = FUtil.chkDate(dateTextField, err);
   // Any errors found should err.set(true);  and err.append("error message");
   disDate = FUtil.chkDate(disableDate,err);

   if(err.isSet()) disableDate.setBackground(UC.red);
   else {disableDate.setBackground(UC.white); disableDate.setText(disDate.toString());}
   return err.isSet();
  }
  
  // This routine must set the form to initial state.  It does not update the JCombobox
  private void clearScreen() {
    
    // Do not change
    snwstation.setText("");
    
    //USER: Clear all fields for this form, this sets "defaults" for new screen
    // description.setText("");
    snwrule.setSelectedIndex(-1);
    disableDate.setText("");
    disable.setSelected(false);
    disableComment.setText("");
    network.setText("");
  }
   
  public String getStation() {return snwstation.getText();}
  /** Creates new form SNWStationSubPanel
   * @param stat The station to update.
   * @throws SQLException 
   */
  public SNWStationSubPanel(String stat) throws SQLException {
    initiating=true;
    initComponents();
    groups = new FlagPanel(SNWGroupPanel.getJComboBox(),700);
    Dimension fxed = fixedPanel.getPreferredSize();
    Dimension grp = groups.getPreferredSize();
    fixedPanel.setBorder(new javax.swing.border.EtchedBorder());
    setPreferredSize(new Dimension(700 , (int) (fxed.getHeight()+grp.getHeight()+10)));
    setMinimumSize(new Dimension(700, (int) (fxed.getHeight()+grp.getHeight()+10)));
    setBorder(new javax.swing.border.EtchedBorder());
    
    Util.prt("groups.hght-"+groups.getPreferredSize()+
        " fixed="+fixedPanel.getPreferredSize());
    Look();
    populate(stat);
    UC.Look(fixedPanel);
    initiating=false;
  }
 private void Look() {
    UC.Look(this);                    // Set color background
  }
  public final void populate(String p) throws SQLException {
    Util.prt("Build a SNWStationSubPanel for "+p);
    obj = new DBObject(DBConnectionThread.getThread("edge"),"edge", "snwstation","snwstation",p);

    if(obj.isNew()) {
      Util.prt("object is new"+p);
    }
    else {
      //USER: Here set all of the form fields to data from the DBObject
      //      channel.setText(obj.getString("Channel"));
      // Example : description.setText(obj.getString("description"));
      snwstation.setText(obj.getString("snwstation"));
      network.setText(obj.getString("network"));
      SNWRulePanel.setJComboBoxToID(snwrule,obj.getInt("snwruleid"));
      if(obj.getInt("disable") != 0) {
        disable.setSelected(true);
        disableDate.setText("disableDate");
        disableComment.setText("disableComment");
      }
      else {
        disable.setSelected(false);
        disableDate.setText("");
        disableComment.setText("");
        
      }
      
      add(groups);
    }           // End else isNew() - processing to form
  }
  
  public void updateRecord(ErrorTrack err) {
    // Do not change
    if(initiating) return;
    if(chkForm(err)) return;
    try {
      // USER : Set all of the fields in data base.  obj.setInt("thefield", ivalue);
      if(!obj.isNew()) obj.refreshRecord();
      obj.setInt("snwruleid",((SNWRule) snwrule.getSelectedItem()).getID());
      obj.setString("network",network.getText());
      if(disable.isSelected()) {
        obj.setInt("disable",1);
        obj.setString("disableDate",disDate.toString());
        obj.setString("disableComment",disableComment.getText());
      }
      else {
        obj.setInt("disable",0);
        obj.setString("disableDate","");
        obj.setString("disableComment","");
        
      }
      // Set the  flags
      ArrayList list = groups.getList();
      long mask = 0;
      for(int i=0; i<list.size(); i++) {
        if(groups.isradioSelected(i)) mask |= ((SNWGroup) list.get(i)).getMask();
      }
      obj.setLong("groupmask", mask);
      
      
      // Do not change
      obj.updateRecord();
      clearScreen();

    }  catch  (SQLException  E)
    { err.set(); err.appendText(snwstation.getText()+" SQLError Updating SWNStation");
    }      
  }
    
  /** This method is called from within the constructor to
   * initialize the form.
   * WARNING: Do NOT modify this code. The content of this method is
   * always regenerated by the Form Editor.
   */
  // <editor-fold defaultstate="collapsed" desc=" Generated Code ">//GEN-BEGIN:initComponents
  private void initComponents() {
    java.awt.GridBagConstraints gridBagConstraints;

    fixedPanel = new javax.swing.JPanel();
    labSNWStation = new javax.swing.JLabel();
    snwrule = SNWRulePanel.getJComboBox();
    snwstation = new javax.swing.JTextField();
    labRule = new javax.swing.JLabel();
    labNetwork = new javax.swing.JLabel();
    network = new javax.swing.JTextField();
    disable = new javax.swing.JRadioButton();
    labDisableDate = new javax.swing.JLabel();
    disableDate = new javax.swing.JTextField();
    labDisableComment = new javax.swing.JLabel();
    disableComment = new javax.swing.JTextField();

    fixedPanel.setLayout(new java.awt.GridBagLayout());

    fixedPanel.setPreferredSize(new java.awt.Dimension(700, 100));
    labSNWStation.setText("Station:");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    fixedPanel.add(labSNWStation, gridBagConstraints);

    snwrule.setMinimumSize(new java.awt.Dimension(180, 27));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 2;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    fixedPanel.add(snwrule, gridBagConstraints);

    snwstation.setColumns(8);
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    fixedPanel.add(snwstation, gridBagConstraints);

    labRule.setText("Rule:");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 2;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    fixedPanel.add(labRule, gridBagConstraints);

    labNetwork.setText("Network:");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    fixedPanel.add(labNetwork, gridBagConstraints);

    network.setColumns(3);
    network.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        networkActionPerformed(evt);
      }
    });

    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    fixedPanel.add(network, gridBagConstraints);

    disable.setText("Disable?");
    disable.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 0, 0, 0));
    disable.setMargin(new java.awt.Insets(0, 0, 0, 0));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 2;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    fixedPanel.add(disable, gridBagConstraints);

    labDisableDate.setText("Disable Til:");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 2;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    fixedPanel.add(labDisableDate, gridBagConstraints);

    disableDate.setColumns(12);
    disableDate.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        disableDateActionPerformed(evt);
      }
    });

    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 3;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    fixedPanel.add(disableDate, gridBagConstraints);

    labDisableComment.setText("DisableDesc:");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 2;
    gridBagConstraints.gridy = 2;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    fixedPanel.add(labDisableComment, gridBagConstraints);

    disableComment.setColumns(30);
    disableComment.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        disableCommentActionPerformed(evt);
      }
    });

    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 3;
    gridBagConstraints.gridy = 2;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    fixedPanel.add(disableComment, gridBagConstraints);

    add(fixedPanel);

  }// </editor-fold>//GEN-END:initComponents

  private void disableDateActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_disableDateActionPerformed
// TODO add your handling code here:
   
  }//GEN-LAST:event_disableDateActionPerformed

  private void disableCommentActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_disableCommentActionPerformed
// TODO add your handling code here:
  }//GEN-LAST:event_disableCommentActionPerformed

  private void networkActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_networkActionPerformed
// TODO add your handling code here:
  }//GEN-LAST:event_networkActionPerformed
  
  
  // Variables declaration - do not modify//GEN-BEGIN:variables
  private javax.swing.JRadioButton disable;
  private javax.swing.JTextField disableComment;
  private javax.swing.JTextField disableDate;
  private javax.swing.JPanel fixedPanel;
  private javax.swing.JLabel labDisableComment;
  private javax.swing.JLabel labDisableDate;
  private javax.swing.JLabel labNetwork;
  private javax.swing.JLabel labRule;
  private javax.swing.JLabel labSNWStation;
  private javax.swing.JTextField network;
  private javax.swing.JComboBox snwrule;
  private javax.swing.JTextField snwstation;
  // End of variables declaration//GEN-END:variables
  
}
