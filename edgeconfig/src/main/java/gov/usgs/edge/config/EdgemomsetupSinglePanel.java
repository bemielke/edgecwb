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
import gov.usgs.anss.util.Show;
import gov.usgs.anss.util.Util;
import java.awt.Color;
import java.awt.Dimension;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import javax.swing.JComboBox;
/**
 *
 * requirements : 
 * The key for the file must be the name of the table and of the
 * JComboBox variable this variable is called "edgemomsetup" in the initial form
 * The table name and key name should match and start lower case (edgemomsetup).
 * The Class here will be that same name but with upper case at beginning(Edgemomsetup).
 <br> 1)  Rename the location JComboBox to the "key" (edgemomsetup) value.  This should start Lower
 *      case.
 *<br>  2)  The table name should match this key name and be all lower case.
 *<br>  3)  Change all Edgemomsetup to ClassName of underlying data (case sensitive!)
<br>  4)  Change all edgemomsetup key value (the JComboBox above)
 *<br>  5)  clearScreen should update swing components to new defaults
 *<br>  6)  chkForm should check all field for validity.  The errorTrack variable is
 *      used to track multiple errors.  In FUtil.chk* handle checks of the various
 *      variable types.  Date, double, and IP etc return the "canonical" form for 
 *      the variable.  These should be stored in a class variable so the latest one
 *      from "chkForm()" are available to the update writer.
 *<br>  7) newone() should be updated to create a new instance of the underlying data class.
 *<br>  8) oldone() get data from database and update all swing elements to correct values
 *<br>  9) addUpdateAction() add data base set*() operations updating all fields in the DB
 *<br> 10) EdgemomsetupPanel() constructor - good place to change backgrounds using UC.Look() any
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
//DCK: this was JPanel, changed to EdgeMomPanel to make it compile
public class EdgemomsetupSinglePanel extends EdgemomPanel {
  
  // NOTE : here define all variables general.  "Vector v" is used for main Comboboz
  //static Vector<Edgemomsetup> v;             // Vector containing objects of this Edgemomsetup Type
  //DBObject obj;
  //ErrorTrack err=new ErrorTrack();
  //boolean initiating=false;
  //TagPanel tag=null;
//  TagPanel tag2=null;
  
  //USER: Here are the local variables
  //int intpriority;
  //PopupForm popup = new PopupForm(this);

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
   Object[] t = sub.getComponents();
//   Iterator i = t.
   int i = 0;
   while (t.length>i) {
     TagPanel x;
     x = (TagPanel) t[i];
        x.chkForm(err);
     i++;
   }
   
   Util.prt("chkForm Edgemomsetup");


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
    roleID.setSelectedIndex(-1);
    UC.Look(sub);
    UC.Look(error);
    error.setText("");
    post.setEnabled(false);
//    post.setText("Enter a Edgemomsetup");
    
    //USER: Clear all fields for this form, this sets "defaults" for new screen
    // description.setText("");
  }
 
  
  private Edgemomsetup newOne() {
      
    return new Edgemomsetup(0, "0", "", //, more
            0, "",0, 0, "", "", "", "", 0
            );
  }
  
  private void oldOne(String p) throws SQLException {
    obj = new DBObject(DBConnectionThread.getThread(DBConnectionThread.getDBSchema()), DBConnectionThread.getDBSchema(), "edgemomsetup","tag",p);

    if(obj.isNew()) {
      Util.prt("object is new"+p);
    }
    else {
      //USER: Here set all of the form fields to data from the DBObject
      //      edgemomsetup.setText(obj.getString("Edgemomsetup"));
      // Example : description.setText(obj.getString("description"));
      roleID.setSelectedIndex(obj.getInt("roleID"));
//      priority.setText(""+obj.getInt("priority"));
//      edgethreadID.setSelectedIndex(obj.getInt("edgethreadID"));
//      args.setText(obj.getString("args"));
//      logfile.setText(obj.getString("logfile"));
//      comment.setText(obj.getString("comment"));
//      if(obj.getInt("hasdata")==1 ) hasdata.setSelected(true);
//      else hasdata.setSelected(false);          
      

        
        
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
    scrollpane = new javax.swing.JScrollPane();
    sub = new javax.swing.JPanel();
    jPanel1 = new javax.swing.JPanel();
    post = new javax.swing.JButton();
    error = new javax.swing.JTextField();
    lblRoleID = new javax.swing.JLabel();
    roleID = RolePanel.getJComboBox();
    check = new javax.swing.JButton();
    add = new javax.swing.JButton();
    ID = new javax.swing.JTextField();
    lblID = new javax.swing.JLabel();

    setLayout(new java.awt.GridBagLayout());

    scrollpane.setBorder(null);
    scrollpane.setHorizontalScrollBarPolicy(javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_ALWAYS);
    scrollpane.setVerticalScrollBarPolicy(javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
    scrollpane.setMinimumSize(new java.awt.Dimension(700, 300));
    scrollpane.setPreferredSize(new java.awt.Dimension(700, 300));

    sub.setMaximumSize(new java.awt.Dimension(32767, 700));
    sub.setPreferredSize(new java.awt.Dimension(100, 700));
    scrollpane.setViewportView(sub);

    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 10;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(scrollpane, gridBagConstraints);

    jPanel1.setLayout(new java.awt.GridBagLayout());

    post.setText("Post Changes");
    post.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        postActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 9;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    jPanel1.add(post, gridBagConstraints);

    error.setColumns(40);
    error.setEditable(false);
    error.setMinimumSize(new java.awt.Dimension(488, 22));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    jPanel1.add(error, gridBagConstraints);

    lblRoleID.setText("RoleID : ");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 2;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    jPanel1.add(lblRoleID, gridBagConstraints);

    roleID.setMinimumSize(new java.awt.Dimension(150, 27));
    roleID.setPreferredSize(new java.awt.Dimension(150, 27));
    roleID.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        roleIDActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 2;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    jPanel1.add(roleID, gridBagConstraints);

    check.setText("Check");
    check.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        checkActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 2;
    gridBagConstraints.gridy = 2;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    jPanel1.add(check, gridBagConstraints);

    add.setText("Add New Tag");
    add.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        addActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 2;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    jPanel1.add(add, gridBagConstraints);

    ID.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        IDActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 9;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    jPanel1.add(ID, gridBagConstraints);

    lblID.setText("ID : ");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 9;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    jPanel1.add(lblID, gridBagConstraints);

    add(jPanel1, new java.awt.GridBagConstraints());
  }// </editor-fold>//GEN-END:initComponents

  private void postActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_postActionPerformed
    // Add your handling code here:
    
    // Do not change
    if(initiating) return;
   err.reset();
   UC.Look(error);
   
   //USER:  Your error checking code goes here setting the local variables to valid settings
   // note : Use FUtil.chk* to check dates, timestamps, IP addresses, etc and put them in
   // canonical form.  store in a class variable.  For instance :
   //   sdate = FUtil.chkDate(dateTextField, err);
   // Any errors found should err.set(true);  and err.append("error message");
   Object[] t = sub.getComponents();
//   Iterator i = t.
   int i = 0;
   while (t.length>i) {
     TagPanel x;
     x = (TagPanel) t[i];
        x.addUpdate(err);
     i++;
   }
   
   Util.prt("postActionPerformed Edgemomsetup");


    // No CHANGES : If we found an error, color up error box
    if(err.isSet()) {
      error.setText(err.getText());
      error.setBackground(UC.red);
    }
        
}//GEN-LAST:event_postActionPerformed

  private void roleIDActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_roleIDActionPerformed
    // TODO add your handling code here:
    if(initiating) return;
    if(roleID.getSelectedIndex() == -1) return;
    if(roleID.getSelectedItem() == null) return;
    sub.removeAll();
    int i=0,height=30;
    Dimension ps;
//    int role = roleID.getSelectedID();
    Role r = (Role)roleID.getSelectedItem();
    int roletmp = r.getID();

    Edgemomsetup loc;
    try {
      Statement stmt = DBConnectionThread.getConnection("edge").createStatement();   // used for query
      String s = "SELECT * FROM edge.edgemomsetup WHERE roleID="+roletmp+" ORDER BY tag;";
      ResultSet rs = stmt.executeQuery(s);
      while (rs.next()) {
        loc = new Edgemomsetup(rs);
        //DCK: this is not right
        tag = new TagPanel(this, this,loc.getID(), 0 , popup);
//        height+=tag.HEIGHT;
        height+=tag.getPreferredSize().getHeight();
        UC.Look(tag);
        sub.add(tag);
        i++;
//        Util.prt("MakeEdgemomsetup() i="+v.size()+" is "+loc.getEdgemomsetup());
//        v.add(loc);
      }
      ps = sub.getPreferredSize();
      ps.height=height;
      sub.setPreferredSize(ps);
      sub.setMaximumSize(ps);
      sub.setMinimumSize(ps);
    }
    catch (SQLException e) {
      Util.SQLErrorPrint(e,"makeEdgemomsetups() on table SQL failed");
    }    
//    Edgemomsetup mom = new Edgemomsetup(rs);
//    TagPanel tag = new TagPanel(1);
//    UC.Look(tag);
//    sub.add(tag);
//    TagPanel tag2 = new TagPanel(2);
//    UC.Look(tag2);
//    sub.add(tag2);
//    sub.
    
  }//GEN-LAST:event_roleIDActionPerformed

  private void IDActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_IDActionPerformed
    // TODO add your handling code here:
}//GEN-LAST:event_IDActionPerformed

  private void checkActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_checkActionPerformed
    // TODO add your handling code here:
    if(chkForm()) return;
    post.setEnabled(true);
  }//GEN-LAST:event_checkActionPerformed

  private void addActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_addActionPerformed
    // TODO add your handling code here:
    int i,height;
    Dimension ps;    
    
    //DCK: this is not right
    tag = new TagPanel(this, this,0,0,popup);
    height= (int) tag.getPreferredSize().getHeight();
    UC.Look(tag);
//    ps = sub.getPreferredSize();
    ps = sub.getSize();
    ps.height=((int) (height+sub.getPreferredSize().getHeight()));
    sub.add(tag);
    sub.setPreferredSize(ps); 
    sub.setMaximumSize(ps); 
    sub.setPreferredSize(ps);    
  }//GEN-LAST:event_addActionPerformed
  
  
  // Variables declaration - do not modify//GEN-BEGIN:variables
  private javax.swing.JTextField ID;
  private javax.swing.JButton add;
  private javax.swing.JButton check;
  private javax.swing.JTextField error;
  private java.awt.GridBagLayout gridBagLayout1;
  private javax.swing.JPanel jPanel1;
  private javax.swing.JLabel lblID;
  private javax.swing.JLabel lblRoleID;
  private javax.swing.JButton post;
  private javax.swing.JComboBox roleID;
  private javax.swing.JScrollPane scrollpane;
  private javax.swing.JPanel sub;
  // End of variables declaration//GEN-END:variables
  /** Creates new form EdgemomsetupPanel */
  public EdgemomsetupSinglePanel() {
    initiating=true;
    initComponents();
    roleID.setSelectedIndex(-1);    // Set selected type
    Look();                    // Set color background
    initiating=false;
   
    // USER:This is a good place for UC.Look(components) to get rid of the ugly Grey
    UC.Look(jPanel1);                    // Set color background
    UC.Look(scrollpane);                    // Set color background
    
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
    makeEdgemomsetups();
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
    //Util.prt("EdgemomsetupPanel.setJComboBoxToID id="+ID);
    for(int i=0; i<b.getItemCount(); i++) 
      if(((Edgemomsetup) b.getItemAt(i)).getID() == ID) {
          b.setSelectedIndex(i);
          //Util.prt("EdgemomsetupPanel.setJComboBoxToID id="+ID+" found at "+i);
      }
  }  
  
  /** Given a database ID find the index into a JComboBox consistent with this panel
   *@param ID The row ID from the database
   *@return The index in the JComboBox with this ID
   */
  public static int getJComboBoxIndex(int ID) {
    if(v == null) makeEdgemomsetups();
    for(int i=0; i<v.size(); i++) if( ID == ((Edgemomsetup) v.get(i)).getID()) return i;
    return -1;
  }
  /** Cause the main JComboBox to be reloaded. */
  public static void reload() {
    v = null;
    makeEdgemomsetups();
  }
  /** return a vector with all of the Edgemomsetup
   * @return The vector with the edgemomsetup
   */
  public static ArrayList getEdgemomsetupVector() {
    if(v == null) makeEdgemomsetups();
    return v;
  }
  /** Get the item corresponding to database ID
   *@param ID the database Row ID
   *@return The Edgemomsetup row with this ID
   */
  public static Edgemomsetup getEdgemomsetupWithID(int ID) {
    if(v == null) makeEdgemomsetups();
    int i=getJComboBoxIndex(ID);
    if(i >= 0) return (Edgemomsetup) v.get(i);
    else return null;
  }
  
  // This routine should only need tweeking if key field is not same as table name
  private static void makeEdgemomsetups() {
    if (v != null) return;
    v=new ArrayList<Edgemomsetup>(100);
    try {
      Statement stmt = DBConnectionThread.getConnection(DBConnectionThread.getDBSchema()).createStatement();   // used for query
      String s = "SELECT * FROM edge.edgemomsetup ORDER BY tag;";
      ResultSet rs = stmt.executeQuery(s);
      while (rs.next()) {
        Edgemomsetup loc = new Edgemomsetup(rs);
//        Util.prt("MakeEdgemomsetup() i="+v.size()+" is "+loc.getEdgemomsetup());
        v.add(loc);
      }
      rs.close();
      stmt.close();
    }
    catch (SQLException e) {
      Util.SQLErrorPrint(e,"makeEdgemomsetups() on table SQL failed");
    }    
  }
  
  // No changes needed
  private void find() {
    if(roleID == null) return;
    if(initiating) return;
    Edgemomsetup l;
    if(roleID.getSelectedIndex() == -1) {
      if(roleID.getSelectedItem() == null) return;
      l = newOne();
    } 
    else {
      l = (Edgemomsetup) roleID.getSelectedItem();
    }
      
    if(l == null) return;
    String p = l.getEdgemomsetup();
    if(p == null) return;
    if(p.equals("")) {
      post.setEnabled(false);
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
          post.setText("Add "+p);
          post.setEnabled(true);
      }
      else {
        post.setText("Update "+p);
        post.setEnabled(true);
        ID.setText(""+obj.getInt("ID"));    // Info only, show ID
      }
    }  
    // This is only thrown if something happens in oldone() new DBOject
    // Other than it being a new record.
    catch  (SQLException  E)                                 // 
    { Util.SQLErrorPrint(E,"Edgemomsetup: SQL error getting Edgemomsetup="+p);
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
      Show.inFrame(new EdgemomsetupSinglePanel(), UC.XSIZE, UC.YSIZE);
    }
    catch(InstantiationException e) {}
  }
}
