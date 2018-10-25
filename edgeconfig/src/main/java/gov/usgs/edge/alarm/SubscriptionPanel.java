/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.edge.alarm;

import gov.usgs.anss.db.DBConnectionThread;
import gov.usgs.anss.db.DBObject;
import gov.usgs.anss.util.ErrorTrack;
import gov.usgs.anss.util.FUtil;
import gov.usgs.anss.util.Show;
import gov.usgs.anss.util.Util;
import gov.usgs.edge.config.UC;
import java.awt.Color;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import javax.swing.JComboBox;

/**
 *
 * requirements : The key for the file must be the name of the table and of the
 * JComboBox variable this variable is called "subscription" in the initial form
 * The table name and key name should match and start lower case (subscription).
 * The Class here will be that same name but with upper case at
 * beginning(Subscription).
 * <br> 1) Rename the location JComboBox to the "key" (subscription) value. This
 * should start Lower case.
 * <br> 2) The table name should match this key name and be all lower case.
 * <br> 3) Change all Subscription to ClassName of underlying data (case
 * sensitive!)
 * <br> 4) Change all subscription key value (the JComboBox above)
 * <br> 5) clearScreen should update swing components to new defaults
 * <br> 6) chkForm should check all field for validity. The errorTrack variable
 * is used to track multiple errors. In FUtil.chk* handle checks of the various
 * variable types. Date, double, and IP etc return the "canonical" form for the
 * variable. These should be stored in a class variable so the latest one from
 * "chkForm()" are available to the update writer.
 * <br> 7) newone() should be updated to create a new instance of the underlying
 * data class.
 * <br> 8) oldone() get data from database and update all swing elements to
 * correct values
 * <br> 9) addUpdateAction() add data base set*() operations updating all fields
 * in the DB
 * <br> 10) SubscriptionPanel() constructor - good place to change backgrounds
 * using UC.Look() any other weird startup stuff.
 *
 * <br> local variable error - Must be a JTextField for posting error
 * communications
 * <br> local variable updateAdd - Must be the JButton for user clicks to try to
 * post the data
 * <br> ID - JTextField which must be non-editable for posting Database IDs
 * <br>
 * <br>
 * ENUM notes : the ENUM are read from the database as type String. So the
 * constructor should us the strings to set the indexes. However, the writes to
 * the database are done as ints representing the value of the Enum. The
 * JComboBox with the Enum has the strings as Items in the list with indexes
 * equal to the database value of the int. Such JComboBoxes are usually
 * initialized with
 * <br>
 * <br> FUtil.getEnumJComboBox(DBConnectionThread.getConnection("alarm"),
 * "tcpstation","rtsport1");
 * <br> Put this in "CodeGeneration" "CustomCreateCode" of the form editor for
 * each Enum.
 * <br>
 * <br> In oldone() get Enum fields with something like :
 * <br> fieldJcomboBox.setSelectedItem(obj.getString("fieldName"));
 * <br> (This sets the JComboBox to the Item matching the string)
 * <br>
 * data class should have code like :
 * <br><br> import java.util.ArrayList; /// Import for Enum manipulation
 * <br> .
 * <br> static ArrayList fieldEnum; // This list will have Strings corresponding
 * to enum
 * <br> .
 * <br> .
 * <br> // To convert an enum we need to create the fieldEnum ArrayList. Do only
 * once(static)
 * <br> if(fieldEnum == null) fieldEnum =
 * FUtil.getEnum(DBConnectionThread.getConnection("alarm"),"table","fieldName");
 * <br>
 * <br> // Get the int corresponding to an Enum String for storage in the data
 * base and
 * <br> // the index into the JComboBox representing this Enum
 * <br> localVariable = FUtil.enumStringToInt(fieldEnum, enumString);
 * <br>
 *
 * @author D.C. Ketchum
 */
public final class SubscriptionPanel extends javax.swing.JPanel {

  // NOTE : here define all variables general.  "ArrayList subscriptions" is used for main Comboboz
  static ArrayList<Subscription> subscriptions;             // ArrayList containing objects of this Subscription Type
  private DBObject obj;
  private final ErrorTrack err = new ErrorTrack();
  private boolean initiating = false;

  //USER: Here are the local variables
  private Timestamp st, et;

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
    Util.prt("chkForm Subscription");

    if (source.getSelectedIndex() == -1) {
      err.set();
      err.appendText("No Source Selected");
    }
    st = FUtil.chkTimestampTimeOnly(startTime, err);
    et = FUtil.chkTimestampTimeOnly(endTime, err);
    if (action.getSelectedIndex() == -1) {
      err.set();
      err.appendText("No action selected");
    }
    if (action2.getSelectedIndex() == -1) {
      err.set();
      err.appendText("No other Action Selected");
    }

    // No CHANGES : If we found an error, color up error box
    if (err.isSet()) {
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
    delete.setEnabled(false);
    addUpdate.setText("Enter a Subscription");

    //USER: Clear all fields for this form, this sets "defaults" for new screen
    // description.setText("");
    source.setSelectedIndex(-1);
    eventID.setSelectedIndex(-1);
    targetID.setSelectedIndex(-1);
    //subscription.setSelectedIndex(-1);

    action.setSelectedIndex(-1);
    startTime.setText("");
    endTime.setText("");
    action2.setSelectedIndex(-1);
    nodeRegExp.setText("");
    processRegExp.setText("");

  }
//   public Subscription(int inID, String loc   //USER: add fields, double lon
//      , int eID, int tID, int dact, int act, Timestamp start, Timestamp end, Timestamp start2, Timestamp end2,int act2
//    )

  private Subscription newOne() {
    Timestamp t = new Timestamp(0l);
    return new Subscription(0, ((String) subscription.getSelectedItem()).toUpperCase(), //, more
            "", // source
            0, // eventID
            0, // targetID
            0, // action
            t, // StartTime
            t, // endTime
            0, // action2
            "", // nodeRegExp
            "" // processRegExp

    );
  }

  private void oldOne(String p) throws SQLException {
    obj = new DBObject(DBConnectionThread.getThread(DBConnectionThread.getDBSchema()),
            "alarm", "subscription", "subscription", p);

    if (obj.isNew()) {
      Util.prt("object is new" + p);
    } else {
      //USER: Here set all of the form fields to data from the DBObject
//      subscription.setJComboBoxToID(obj.getString("subscription"));
      // Example : description.setText(obj.getString("description"));
      SourcePanel.setJComboBoxToSource(source, obj.getString("source"));
      EventPanel.setJComboBoxToID(eventID, obj.getInt("eventID"));
      TargetPanel.setJComboBoxToID(targetID, obj.getInt("targetID"));
      ActionPanel.setJComboBoxToID(action, obj.getInt("action"));
      startTime.setText(obj.getTimestamp("startTime").toString().substring(11, 19));
      endTime.setText(obj.getTimestamp("endTime").toString().substring(11, 19));
      ActionPanel.setJComboBoxToID(action2, obj.getInt("action2"));
      nodeRegExp.setText(obj.getString("nodeRegExp"));
      processRegExp.setText(obj.getString("processRegExp"));

    }           // End else isNew() - processing to form
  }

  /**
   * This method is called from within the constructor to initialize the form.
   * WARNING: Do NOT modify this code. The content of this method is always
   * regenerated by the Form Editor.
   */
  // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
  private void initComponents() {
    java.awt.GridBagConstraints gridBagConstraints;

    gridBagLayout1 = new java.awt.GridBagLayout();
    lblSubscription = new javax.swing.JLabel();
    subscription = getJComboBox();
    addUpdate = new javax.swing.JButton();
    ID = new javax.swing.JTextField();
    jLabel9 = new javax.swing.JLabel();
    error = new javax.swing.JTextField();
    lblEvent = new javax.swing.JLabel();
    lblTarget = new javax.swing.JLabel();
    lblAction = new javax.swing.JLabel();
    lblStartTime = new javax.swing.JLabel();
    lblEndTime = new javax.swing.JLabel();
    lblAction2 = new javax.swing.JLabel();
    lblNodeRegExp = new javax.swing.JLabel();
    processRegExp = new javax.swing.JTextField();
    startTime = new javax.swing.JTextField();
    endTime = new javax.swing.JTextField();
    nodeRegExp = new javax.swing.JTextField();
    lblProcessRegExp = new javax.swing.JLabel();
    lblSource = new javax.swing.JLabel();
    source = SourcePanel.getJComboBox();
    eventID = EventPanel.getJComboBox();
    targetID = TargetPanel.getJComboBox();
    ;
    action = ActionPanel.getJComboBox();
    action2 = ActionPanel.getJComboBox();
    reload = new javax.swing.JButton();
    delete = new javax.swing.JButton();
    instructions = new javax.swing.JScrollPane();
    jTextArea1 = new javax.swing.JTextArea();
    labEventHelp = new javax.swing.JLabel();

    setLayout(new java.awt.GridBagLayout());

    lblSubscription.setText("Subscription : ");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 2;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    add(lblSubscription, gridBagConstraints);

    subscription.setEditable(true);
    subscription.setMaximumRowCount(40);
    subscription.setMinimumSize(new java.awt.Dimension(400, 22));
    subscription.setPreferredSize(new java.awt.Dimension(400, 22));
    subscription.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        subscriptionActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 2;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(subscription, gridBagConstraints);

    addUpdate.setText("Add/Update");
    addUpdate.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        addUpdateActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 14;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(addUpdate, gridBagConstraints);

    ID.setColumns(8);
    ID.setEditable(false);
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
    gridBagConstraints.gridy = 1;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(error, gridBagConstraints);

    lblEvent.setText("Event : ");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 4;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    add(lblEvent, gridBagConstraints);

    lblTarget.setText("Target : ");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 5;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    add(lblTarget, gridBagConstraints);

    lblAction.setText("In Hours action : ");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 6;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    add(lblAction, gridBagConstraints);

    lblStartTime.setText("StartTime(00:00:00) : ");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 7;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    add(lblStartTime, gridBagConstraints);

    lblEndTime.setText("EndTime(00:00:00) : ");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 8;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    add(lblEndTime, gridBagConstraints);

    lblAction2.setText("Out of Hours action : ");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 9;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    add(lblAction2, gridBagConstraints);

    lblNodeRegExp.setText("Node Regular Expression : ");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 10;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    add(lblNodeRegExp, gridBagConstraints);

    processRegExp.setText(" ");
    processRegExp.setPreferredSize(new java.awt.Dimension(150, 20));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 11;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(processRegExp, gridBagConstraints);

    startTime.setText(" ");
    startTime.setPreferredSize(new java.awt.Dimension(100, 20));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 7;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(startTime, gridBagConstraints);

    endTime.setText(" ");
    endTime.setPreferredSize(new java.awt.Dimension(100, 20));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 8;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(endTime, gridBagConstraints);

    nodeRegExp.setText(" ");
    nodeRegExp.setPreferredSize(new java.awt.Dimension(150, 20));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 10;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(nodeRegExp, gridBagConstraints);

    lblProcessRegExp.setText("Process Regular Expression : ");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 11;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    add(lblProcessRegExp, gridBagConstraints);

    lblSource.setText("Source : ");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 3;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    add(lblSource, gridBagConstraints);

    source.setEditable(true);
    source.addItemListener(new java.awt.event.ItemListener() {
      public void itemStateChanged(java.awt.event.ItemEvent evt) {
        sourceItemStateChanged(evt);
      }
    });
    source.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        sourceActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 3;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(source, gridBagConstraints);

    eventID.setEditable(true);
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 4;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(eventID, gridBagConstraints);

    targetID.setEditable(true);
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 5;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(targetID, gridBagConstraints);

    action.setEditable(true);
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 6;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(action, gridBagConstraints);

    action2.setEditable(true);
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 9;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(action2, gridBagConstraints);

    reload.setText("Reload");
    reload.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        reloadActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 15;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(reload, gridBagConstraints);

    delete.setText("Delete");
    delete.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        deleteActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 16;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(delete, gridBagConstraints);

    instructions.setPreferredSize(new java.awt.Dimension(370, 125));

    jTextArea1.setColumns(20);
    jTextArea1.setEditable(false);
    jTextArea1.setFont(new java.awt.Font("Arial", 1, 12)); // NOI18N
    jTextArea1.setRows(5);
    jTextArea1.setText(" Instructions:\n\n To create a new subscription just type anything into the \n 'subscription' field box and tab out, then fill in the fields.\n\n Note: To subscribe to an entire source, just leave event blank.");
    instructions.setViewportView(jTextArea1);

    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(instructions, gridBagConstraints);

    labEventHelp.setText("(Leave blank to subscribe to entire Source)                ");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 4;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    add(labEventHelp, gridBagConstraints);
  }// </editor-fold>//GEN-END:initComponents

  private void subscriptionActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_subscriptionActionPerformed
// TODO add your handling code here:
    find();
  }//GEN-LAST:event_subscriptionActionPerformed

  private void reloadActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_reloadActionPerformed
// TODO add your handling code here:
    TargetPanel.reload();
    TargetPanel.getJComboBox(targetID);
    ActionPanel.reload();
    ActionPanel.getJComboBox(action);
    ActionPanel.getJComboBox(action2);
    clearScreen();
  }//GEN-LAST:event_reloadActionPerformed

  private void deleteActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_deleteActionPerformed
// TODO add your handling code here:
    try {

      obj.deleteRecord();
      subscription.removeItem(subscription.getSelectedItem());
      clearScreen();
    } catch (SQLException e) {
      Util.SQLErrorPrint(e, "Exception trying to delete a subscription ");
    }

  }//GEN-LAST:event_deleteActionPerformed

  private void sourceItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_sourceItemStateChanged
// TODO add your handling code here: 
    if (source.getSelectedItem() == null) {
      EventPanel.getJComboBox(eventID);
      eventID.setSelectedIndex(-1);
    } else {
      EventPanel.getJComboBox(eventID, source.getSelectedItem().toString());
      eventID.setSelectedIndex(-1);
    }
  }//GEN-LAST:event_sourceItemStateChanged

  private void addUpdateActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_addUpdateActionPerformed
    // Add your handling code here:

    // Do not change
    if (initiating) {
      return;
    }
    if (chkForm()) {
      return;
    }
    try {
      //String p = subscription.getSelectedItem().toString();
      String p = ((eventID.getSelectedIndex() == -1) ? ((Source) source.getSelectedItem()).getSource()
              : ((Event) eventID.getSelectedItem()).toString())
              + "->" + TargetPanel.getItemWithID(((Target) targetID.getSelectedItem()).getID());
      if (!obj.isNew()) {
        obj.refreshRecord();        // Refresh the result set so the sets will not fail on stale one
      }
//      p = p.toUpperCase();                  // USER : drop this to allow Mixed case
      obj.setString("subscription", p);

      // USER : Set all of the fields in data base.  obj.setInt("thefield", ivalue);
      if (eventID.getSelectedIndex() != -1) {
        obj.setInt("eventID", ((Event) eventID.getSelectedItem()).getID());
        obj.setString("source", ((Source) source.getSelectedItem()).getSource());
      } else {
        obj.setInt("eventID", 0);
        obj.setString("source", ((Source) source.getSelectedItem()).getSource());
      }
      if (targetID.getSelectedIndex() != -1) {
        obj.setInt("targetID", ((Target) targetID.getSelectedItem()).getID());
      } else {
        obj.setInt("targetID", 0);
      }
      if (action.getSelectedIndex() != -1) {
        obj.setInt("action", ((Action) action.getSelectedItem()).getID());
      } else {
        obj.setInt("action", 0);
      }
      obj.setTimestamp("startTime", st);
      obj.setTimestamp("endTime", et);
      if (action2.getSelectedIndex() != -1) {
        obj.setInt("action2", ((Action) action2.getSelectedItem()).getID());
      } else {
        obj.setInt("action2", 0);
      }
      obj.setString("nodeRegExp", nodeRegExp.getText());
      obj.setString("processRegExp", processRegExp.getText());

      // Do not change
      obj.updateRecord();
      subscriptions = null;       // force reload of combo box
      getJComboBox(subscription);
      clearScreen();
      subscription.setSelectedIndex(-1);
    } catch (SQLException E) {
      Util.SQLErrorPrint(E, "subscription: update failed partno=" + subscription.getSelectedItem());
      error.setText("Could Not Update File!");
      error.setBackground(UC.red);
      addUpdate.setEnabled(true);
    }
  }//GEN-LAST:event_addUpdateActionPerformed

  private void sourceActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_sourceActionPerformed
    // TODO add your handling code here:
    if (initiating) {
      return;
    }
    FUtil.searchJComboBox(source, false);
  }//GEN-LAST:event_sourceActionPerformed


  // Variables declaration - do not modify//GEN-BEGIN:variables
  private javax.swing.JTextField ID;
  private javax.swing.JComboBox action;
  private javax.swing.JComboBox action2;
  private javax.swing.JButton addUpdate;
  private javax.swing.JButton delete;
  private javax.swing.JTextField endTime;
  private javax.swing.JTextField error;
  private javax.swing.JComboBox eventID;
  private java.awt.GridBagLayout gridBagLayout1;
  private javax.swing.JScrollPane instructions;
  private javax.swing.JLabel jLabel9;
  private javax.swing.JTextArea jTextArea1;
  private javax.swing.JLabel labEventHelp;
  private javax.swing.JLabel lblAction;
  private javax.swing.JLabel lblAction2;
  private javax.swing.JLabel lblEndTime;
  private javax.swing.JLabel lblEvent;
  private javax.swing.JLabel lblNodeRegExp;
  private javax.swing.JLabel lblProcessRegExp;
  private javax.swing.JLabel lblSource;
  private javax.swing.JLabel lblStartTime;
  private javax.swing.JLabel lblSubscription;
  private javax.swing.JLabel lblTarget;
  private javax.swing.JTextField nodeRegExp;
  private javax.swing.JTextField processRegExp;
  private javax.swing.JButton reload;
  private javax.swing.JComboBox source;
  private javax.swing.JTextField startTime;
  private javax.swing.JComboBox subscription;
  private javax.swing.JComboBox targetID;
  // End of variables declaration//GEN-END:variables
  /**
   * Creates new form SubscriptionPanel
   */
  public SubscriptionPanel() {
    initiating = true;
    initComponents();
    getJComboBox(subscription);                // set up the key JComboBox
    subscription.setSelectedIndex(-1);    // Set selected type
    Look();                    // Set color background
    initiating = false;

    // USER:This is a good place for UC.Look(components) to get rid of the ugly Grey
    clearScreen();                    // Start with empty screen
  }

  private void Look() {
    UC.Look(this);                    // Set color background
  }

  //////////////////////////////////////////////////////////////////////////////
  // No USER: changes needed below here!
  //////////////////////////////////////////////////////////////////////////////
  /**
   * Create a JComboBox of all of the items in the table represented by this
   * panel
   *
   * @return A New JComboBox filled with all row keys from the table
   */
  public static JComboBox getJComboBox() {
    JComboBox b = new JComboBox();
    getJComboBox(b);
    return b;
  }

  /**
   * Update a JComboBox to represent the rows represented by this panel
   *
   * @param b The JComboBox to Update
   */
  public static void getJComboBox(JComboBox b) {
    b.removeAllItems();
    makeSubscriptions();
    for (int i = 0; i < subscriptions.size(); i++) {
      b.addItem(subscriptions.get(i));
    }
    b.setMaximumRowCount(30);
  }

  /**
   * Given a JComboBox from getJComboBox, set selected item to match database ID
   *
   * @param b The JComboBox
   * @param ID the row ID from the database to select
   */
  public static void setJComboBoxToID(JComboBox b, int ID) {
    b.setSelectedIndex(-1);
    //Util.prt("SubscriptionPanel.setJComboBoxToID id="+ID);
    for (int i = 0; i < b.getItemCount(); i++) {
      if (((Subscription) b.getItemAt(i)).getID() == ID) {
        b.setSelectedIndex(i);
        //Util.prt("SubscriptionPanel.setJComboBoxToID id="+ID+" found at "+i);
      }
    }
  }

  /**
   * Given a database ID find the index into a JComboBox consistent with this
   * panel
   *
   * @param ID The row ID from the database
   * @return The index in the JComboBox with this ID
   */
  public static int getJComboBoxIndex(int ID) {
    if (subscriptions == null) {
      makeSubscriptions();
    }
    for (int i = 0; i < subscriptions.size(); i++) {
      if (ID == ((Subscription) subscriptions.get(i)).getID()) {
        return i;
      }
    }
    return -1;
  }

  /**
   * Cause the main JComboBox to be reloaded.
   */
  public static void reload() {
    subscriptions = null;
    makeSubscriptions();
  }

  /* return a ArrayList with all of the Subscription
   * @return The ArrayList with the subscription
   */
  public static ArrayList<Subscription> getSubscriptionArrayList() {
    if (subscriptions == null) {
      makeSubscriptions();
    }
    return subscriptions;
  }

  /**
   * Get the item corresponding to database ID
   *
   * @param ID the database Row ID
   * @return The Subscription row with this ID
   */
  public static Subscription getItemWithID(int ID) {
    if (subscriptions == null) {
      makeSubscriptions();
    }
    int i = getJComboBoxIndex(ID);
    if (i >= 0) {
      return (Subscription) subscriptions.get(i);
    } else {
      return null;
    }
  }

  // This routine should only need tweeking if key field is not same as table name
  private static void makeSubscriptions() {
    if (subscriptions != null) {
      return;
    }
    subscriptions = new ArrayList<Subscription>(100);
    try {
      Statement stmt = DBConnectionThread.getConnection(DBConnectionThread.getDBSchema()).createStatement();   // used for query
      String s = "SELECT * FROM alarm.subscription ORDER BY subscription;";
      ResultSet rs = stmt.executeQuery(s);
      while (rs.next()) {
        Subscription loc = new Subscription(rs);
        //Util.prt("MakeSubscription() i="+subscriptions.size()+" is "+loc.getSubscription());
        if (("" + loc).contains("null")) {
          Util.prt("Null=" + loc + " loc=" + loc.toString3());
        }
        subscriptions.add(loc);
      }
    } catch (SQLException e) {
      Util.SQLErrorPrint(e, "makeSubscriptions() on table SQL failed");
    }
  }

  // No changes needed
  private void find() {
    if (subscription == null) {
      return;
    }
    if (initiating) {
      return;
    }
    Subscription l;
    if (subscription.getSelectedIndex() == -1) {
      if (subscription.getSelectedItem() == null) {
        return;
      }
      l = newOne();
    } else {
      l = (Subscription) subscription.getSelectedItem();
    }

    if (l == null) {
      return;
    }
    String p = l.getSubscription();
    if (p == null) {
      return;
    }
    if (p.equals("")) {
      addUpdate.setEnabled(false);
      delete.setEnabled(false);
      addUpdate.setText("Enter a Subscription!");
    }
    //p = p.toUpperCase();
    error.setBackground(Color.lightGray);
    error.setText("");
    try {
      clearScreen();          // set screen to known state
      oldOne(p);

      // set add/Update button to indicate an update will happen
      if (obj.isNew()) {
        clearScreen();
        ID.setText("NEW!");

        // Set up for "NEW" - clear fields etc.
        error.setText("NOT found - assume NEW");
        error.setBackground(UC.yellow);
        addUpdate.setText("Add " + p);
        addUpdate.setEnabled(true);

        startTime.setText("00:00:00");
        endTime.setText("23:59:59");

      } else {
        addUpdate.setText("Update " + p);
        addUpdate.setEnabled(true);
        delete.setEnabled(true);
        ID.setText("" + obj.getInt("ID"));    // Info only, show ID
      }
    } // This is only thrown if something happens in oldone() new DBOject
    // Other than it being a new record.
    catch (SQLException E) // 
    {
      Util.SQLErrorPrint(E, "Subscription: SQL error getting Subscription=" + p);
    }

  }

  /**
   * This main displays the form Pane by itself
   *
   * @param args command line args ignored
   */
  public static void main(String args[]) {
    DBConnectionThread jcjbl;
    Util.init(UC.getPropertyFilename());
    UC.init();
    try {
      // Make test DBconnection for form
      jcjbl = new DBConnectionThread(DBConnectionThread.getDBServer(), DBConnectionThread.getDBCatalog(),
              UC.defaultUser(), UC.defaultPassword(), true, false, DBConnectionThread.getDBSchema(), DBConnectionThread.getDBVendor());
      if (!DBConnectionThread.waitForConnection(DBConnectionThread.getDBSchema())) {
        if (!DBConnectionThread.waitForConnection(DBConnectionThread.getDBSchema())) {
          if (!DBConnectionThread.waitForConnection(DBConnectionThread.getDBSchema())) {
            Util.prt("Could not connect to DB " + jcjbl);
            System.exit(1);
          }
        }
      }
      Show.inFrame(new SubscriptionPanel(), UC.XSIZE, UC.YSIZE);
    } catch (InstantiationException e) {
    }
  }
}
