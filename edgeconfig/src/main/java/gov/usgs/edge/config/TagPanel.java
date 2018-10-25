/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */

/*
 * TagPanel.java
 *
 * Created on April 11, 2008, 3:56 PM
 */

package gov.usgs.edge.config;

import gov.usgs.anss.db.DBConnectionThread;
import gov.usgs.anss.db.DBObject;
import gov.usgs.anss.util.ErrorTrack;
import gov.usgs.anss.util.FUtil;
import gov.usgs.anss.util.Util;
import java.sql.SQLException;
import java.util.ArrayList;
import javax.swing.JComponent;

/** TagPanel
 *   This panel is a subpanel for the EdgemomPanel, each "tag" is generated from
 *   an edgemomsetup located in the edgemomsetup table. Each tag represents one
 *   line in an edgemomsetup configuration file that is generated based upon
 *   roles designated for each edge cpu. The EdgemomPanel allows the user to
 *   choose a role and then populate the Panel with all the TagPanels that
 *   relate to the role. You can add/delete/edit each tag, verify that they
 *   meet certain criteria (Check) then post them as a complete unit to
 *   the database. Additionally you can generate the edgemomsetup configuration
 *   file and display it in a PopupForm.
 *
 * @author  rjackson
 */
public class TagPanel  extends javax.swing.JPanel {
  // NOTE : here define all variables general.  "Vector v" is used for main Comboboz
  static ArrayList<Role> v;             // Vector containing objects of this Role Type
  DBObject obj;
  ErrorTrack err=new ErrorTrack();
  boolean initiating=false;  
  EdgemomPanel mom;
  JComponent parent;
  PopupForm popup;
  int intpriority;
  String strlogfile="";
  
  /** Creates new form TagPanel
   * @param mommy the EdgeMomPane connected to this tag panel
   * @param par the parent component (JPanel?)
   * @param momID The ID of the Edgemomsetup being worked on
   * @param role The role index to use
   * @param pop The PopupForm for displaying feed back
   */
  public TagPanel (EdgemomPanel mommy, JComponent par,int momID, int role, PopupForm pop) {
    initComponents();
    ID.setVisible(true);
    Edgemomsetup loc=null;
    mom = mommy;
    parent = par;
    popup = (PopupForm) pop;
    try {
    obj = new DBObject(DBConnectionThread.getThread(DBConnectionThread.getDBSchema()), DBConnectionThread.getDBSchema(), "edgemomsetup","ID",""+momID);

    if(obj.isNew()) {
      Util.prt("object is new");
      roleID.setSelectedIndex(role);
      account.setText(mom.getAccount());
    }  
    else {

        tag.setText(obj.getString("tag"));
        ID.setText(""+obj.getInt("ID"));
        int r = obj.getInt("roleID");
        if (r==0) roleID.setSelectedIndex(-1);
        else RolePanel.setJComboBoxToID(roleID, r);
        account.setText(obj.getString("account"));
        priority.setText(""+obj.getInt("priority"));
        int e = obj.getInt("edgethreadID");
        if (e==0) edgethreadID.setSelectedIndex(-1);
        else EdgethreadPanel.setJComboBoxToID(edgethreadID, e);
        args.setText(obj.getString("args"));
        disable.setSelected( (obj.getInt("disabled") != 0));
        logfile.setText(obj.getString("logfile"));
        comment.setText(obj.getString("comment"));
        sideFile.setText(obj.getString("filename"));
    
      }
    }
    catch (SQLException e) {
      Util.SQLErrorPrint(e,"makeEdgemomsetups() on table SQL failed");
    }
        
  }
  // This routine must validate all fields on the form.  The err (ErrorTrack) variable
  // is used by the FUtil to verify fields.  Use FUTil or custom code here for verifications
  public boolean chkForm(ErrorTrack err) {
    // Do not change
//   err.reset();
   
   
   //USER:  Your error checking code goes here setting the local variables to valid settings
   // note : Use FUtil.chk* to check dates, timestamps, IP addresses, etc and put them in
   // canonical form.  store in a class variable.  For instance :
   //   sdate = FUtil.chkDate(dateTextField, err);
   // Any errors found should err.set(true);  and err.append("error message");
   Util.prt("chkForm Edgemomsetup");

   intpriority = FUtil.chkInt(priority, err, 0, 9, true);
   strlogfile = FUtil.chkLogfile(logfile, err);

    // No CHANGES : If we found an error, color up error box

    return err.isSet();
  }
  
  // This routine must set the form to initial state.  It does not update the JCombobox
  private void clearScreen() {
    

    
    //USER: Clear all fields for this form, this sets "defaults" for new screen
    // description.setText("");
    ID.setText("");
    tag.setText("");
    roleID.setSelectedIndex(-1);
    account.setText(mom.getAccount());
    priority.setText("");
    edgethreadID.setSelectedIndex(-1);
    args.setText("");
    disable.setSelected(false);
    logfile.setText("");
    comment.setText("");
    sideFile.setText("");
  }  
//   private TagPanel newOne() {
//      
////    return new Tag(0, "newtag", //, more
////            "", "", 0, 0
////       );
//  }
  
  private void oldOne(String p) throws SQLException {
    
    obj = new DBObject(DBConnectionThread.getThread(DBConnectionThread.getDBSchema()), DBConnectionThread.getDBSchema(), "edgemomsetup","ID",p);

    if(obj.isNew()) {
      Util.prt("object is new"+p);
    }
    else {
      //USER: Here set all of the form fields to data from the DBObject
      //      role.setText(obj.getString("Role"));
      // Example : description.setText(obj.getString("description"));
      tag.setText(obj.getString("tag"));
      ID.setText(""+obj.getInt("ID"));
      int r = obj.getInt("roleID");
      if (r==0) roleID.setSelectedIndex(-1);
        else RolePanel.setJComboBoxToID(roleID, r);
      account.setText(obj.getString("account"));
      priority.setText(""+obj.getInt("priority"));
      int e = obj.getInt("edgethreadID");
      if (e==0) edgethreadID.setSelectedIndex(-1);
      else EdgethreadPanel.setJComboBoxToID(edgethreadID, e);
      args.setText(obj.getString("args"));
      disable.setSelected( (obj.getInt("disabled") != 0));
      logfile.setText(obj.getString("logfile"));
      comment.setText(obj.getString("comment"));
      sideFile.setText(obj.getString("filename"));
  
        
    }           // End else isNew() - processing to form
  } 
  public void addUpdate(ErrorTrack err) {                                          
    // Add your handling code here:
    
    // Do not change
//    if(initiating) return;
    if(chkForm(err)) return;
    try {

      if(!obj.isNew()) obj.refreshRecord();        // Refresh the result set so the sets will not fail on stale one
      obj.setString("tag", tag.getText());
      obj.setString("args", args.getText());
      obj.setInt("disabled", ( disable.isSelected() ? 1 : 0));
      obj.setString("logfile", strlogfile);
      obj.setString("comment", comment.getText());
      obj.setInt("priority",  intpriority);
      if(roleID.getSelectedIndex() != -1) obj.setInt("roleID", ((Role) roleID.getSelectedItem()).getID());
      else obj.setInt("roleID",0);
      obj.setString("account", account.getText());
      if(edgethreadID.getSelectedIndex() != -1) obj.setInt("edgethreadID", ((Edgethread) edgethreadID.getSelectedItem()).getID());
      else obj.setInt("edgethreadID",0);
      obj.setString("filename", sideFile.getText());
    

      
      // Do not change
      obj.updateRecord();
      String p = ID.getText();
      clearScreen();
      oldOne(p);
    }
    catch (SQLException e) {
      Util.SQLErrorPrint(e,"makeEdgemomsetups() on table SQL failed");
    }     
  }    
  /** ,
   */
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        ID = new javax.swing.JTextField();
        lblTag = new javax.swing.JLabel();
        tag = new javax.swing.JTextField();
        lblEdgethread = new javax.swing.JLabel();
        edgethreadID = gov.usgs.edge.config.EdgethreadPanel.getJComboBox();
        lblPriority = new javax.swing.JLabel();
        priority = new javax.swing.JTextField();
        delete = new javax.swing.JButton();
        lblArgs = new javax.swing.JLabel();
        jScrollPane1 = new javax.swing.JScrollPane();
        args = new javax.swing.JTextArea();
        lblLogfile = new javax.swing.JLabel();
        logfile = new javax.swing.JTextField();
        lblComment = new javax.swing.JLabel();
        comment = new javax.swing.JTextField();
        lblSide = new javax.swing.JLabel();
        sideFile = new javax.swing.JTextField();
        lblRole = new javax.swing.JLabel();
        roleID = RolePanel.getJComboBox();
        account = new javax.swing.JTextField();
        disable = new javax.swing.JRadioButton();

        setBorder(javax.swing.BorderFactory.createBevelBorder(javax.swing.border.BevelBorder.RAISED));
        setMaximumSize(new java.awt.Dimension(665, 120));
        setMinimumSize(new java.awt.Dimension(665, 120));
        setPreferredSize(new java.awt.Dimension(665, 120));
        setLayout(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 0, 0));

        ID.setBackground(new java.awt.Color(204, 204, 204));
        ID.setEditable(false);
        ID.setFocusable(false);
        ID.setMinimumSize(new java.awt.Dimension(50, 28));
        ID.setPreferredSize(new java.awt.Dimension(50, 28));
        add(ID);

        lblTag.setText("Tag : ");
        add(lblTag);

        tag.setMinimumSize(new java.awt.Dimension(100, 28));
        tag.setPreferredSize(new java.awt.Dimension(100, 28));
        add(tag);

        lblEdgethread.setText("Edgethread : ");
        add(lblEdgethread);

        edgethreadID.setMinimumSize(new java.awt.Dimension(175, 27));
        edgethreadID.setPreferredSize(new java.awt.Dimension(175, 27));
        edgethreadID.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                edgethreadIDActionPerformed(evt);
            }
        });
        add(edgethreadID);

        lblPriority.setText("Priority : ");
        add(lblPriority);

        priority.setColumns(2);
        priority.setMinimumSize(new java.awt.Dimension(45, 27));
        priority.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                priorityActionPerformed(evt);
            }
        });
        priority.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusLost(java.awt.event.FocusEvent evt) {
                priorityFocusLost(evt);
            }
        });
        add(priority);

        delete.setText("Delete Tag");
        delete.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                deleteActionPerformed(evt);
            }
        });
        add(delete);

        lblArgs.setText("Args : ");
        lblArgs.setPreferredSize(new java.awt.Dimension(42, 16));
        add(lblArgs);

        jScrollPane1.setMinimumSize(new java.awt.Dimension(364, 23));
        jScrollPane1.setPreferredSize(new java.awt.Dimension(600, 28));

        args.setColumns(60);
        args.setFont(new java.awt.Font("Monospaced", 0, 10));
        args.setLineWrap(true);
        args.setRows(5);
        args.setPreferredSize(new java.awt.Dimension(360, 11));
        jScrollPane1.setViewportView(args);

        add(jScrollPane1);

        lblLogfile.setText("Log File : ");
        add(lblLogfile);

        logfile.setMinimumSize(new java.awt.Dimension(250, 27));
        logfile.setPreferredSize(new java.awt.Dimension(150, 27));
        add(logfile);

        lblComment.setText("Comment : ");
        add(lblComment);

        comment.setMinimumSize(new java.awt.Dimension(250, 27));
        comment.setPreferredSize(new java.awt.Dimension(375, 27));
        add(comment);

        lblSide.setText("Side File:");
        add(lblSide);

        sideFile.setColumns(10);
        add(sideFile);

        lblRole.setText("Change Role/Account : ");
        lblRole.setMaximumSize(new java.awt.Dimension(150, 16));
        lblRole.setMinimumSize(new java.awt.Dimension(150, 16));
        lblRole.setPreferredSize(new java.awt.Dimension(150, 16));
        add(lblRole);

        roleID.setMinimumSize(new java.awt.Dimension(125, 27));
        roleID.setPreferredSize(new java.awt.Dimension(125, 27));
        add(roleID);

        account.setColumns(8);
        account.setText("vdl");
        account.setMinimumSize(new java.awt.Dimension(110, 28));
        add(account);

        disable.setText("Disable");
        add(disable);
    }// </editor-fold>//GEN-END:initComponents

  private void priorityActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_priorityActionPerformed
    // TODO add your handling code here:
  }//GEN-LAST:event_priorityActionPerformed

  private void priorityFocusLost(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_priorityFocusLost
    // TODO add your handling code here:
    intpriority = FUtil.chkInt(priority, err, 1, 9);
  }//GEN-LAST:event_priorityFocusLost

  private void deleteActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_deleteActionPerformed
    // TODO add your handling code here:
    try{
      obj.deleteRecord();
//      for (int k=0;k<parent.getComponentCount();k++) 
//        Util.prta(k+" comp="+parent.getComponent(k)+" this="+this);
//      int j = parent.getComponentCount();
      parent.remove(this); 
//      int i = parent.getComponentCount();
      parent.validate();
      parent.repaint();
    }
    catch (SQLException e) {
      Util.SQLErrorPrint(e,"makeEdgemomsetups() on table SQL failed");
    }      
  }//GEN-LAST:event_deleteActionPerformed

  private void edgethreadIDActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_edgethreadIDActionPerformed
    // TODO add your handling code here:
    Edgethread x = (Edgethread) edgethreadID.getSelectedItem();
    if (x == null) return;
    popup.setText(x.getHelp());
    if (popup.isVisible()) mom.acceptpopup();
    
    priority.requestFocusInWindow();
//    priority.setRequestFocusEnabled(true);
  }//GEN-LAST:event_edgethreadIDActionPerformed
  
  
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JTextField ID;
    private javax.swing.JTextField account;
    private javax.swing.JTextArea args;
    private javax.swing.JTextField comment;
    private javax.swing.JButton delete;
    private javax.swing.JRadioButton disable;
    private javax.swing.JComboBox edgethreadID;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JLabel lblArgs;
    private javax.swing.JLabel lblComment;
    private javax.swing.JLabel lblEdgethread;
    private javax.swing.JLabel lblLogfile;
    private javax.swing.JLabel lblPriority;
    private javax.swing.JLabel lblRole;
    private javax.swing.JLabel lblSide;
    private javax.swing.JLabel lblTag;
    private javax.swing.JTextField logfile;
    private javax.swing.JTextField priority;
    private javax.swing.JComboBox roleID;
    private javax.swing.JTextField sideFile;
    private javax.swing.JTextField tag;
    // End of variables declaration//GEN-END:variables
 
  

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
      //Show.inFrame(new TagPanel(), UC.XSIZE, UC.YSIZE);
    }
    catch(InstantiationException e) {}
  }
  
}
