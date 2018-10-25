/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */

package gov.usgs.anss.metadatagui;
//package gov.usgs.edge.template;
//import gov.usgs.anss.metadata.*;
import gov.usgs.anss.db.DBConnectionThread;
import gov.usgs.anss.db.DBObject;
import gov.usgs.anss.gui.FlagPanel2;
import gov.usgs.anss.util.ErrorTrack;
import gov.usgs.anss.util.Show;
import gov.usgs.anss.util.Util;
import gov.usgs.edge.config.UC;
import java.awt.Color;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.text.DecimalFormat;
import java.util.ArrayList;
import javax.swing.JComboBox;
/**
 *
 * requirements : 
 * The key for the file must be the name of the table and of the
 * JComboBox variable this variable is called "station" in the initial form
 * The table name and key name should match and start lower case (station).
 * The Class here will be that same name but with upper case at beginning(Station).
 * <br> 1)  Rename the location JComboBox to the "key" (station) value.  This should start Lower
 *      case.
 *<br>  2)  The table name should match this key name and be all lower case.
 *<br>  3)  Change all Station to ClassName of underlying data (case sensitive!)
 *<br>  4)  Change all station key value (the JComboBox above)
 *<br>  5)  clearScreen should update swing components to new defaults
 *<br>  6)  chkForm should check all field for validity.  The errorTrack variable is
 *      used to track multiple errors.  In FUtil.chk* handle checks of the various
 *      variable types.  Date, double, and IP etc return the "canonical" form for 
 *      the variable.  These should be stored in a class variable so the latest one
 *      from "chkForm()" are available to the update writer.
 *<br>  7) newone() should be updated to create a new instance of the underlying data class.
 *<br>  8) oldone() get data from database and update all swing elements to correct values
 *<br>  9) addUpdateAction() add data base set*() operations updating all fields in the DB
 *<br> 10) StationPanel() constructor - good place to change backgrounds using UC.Look() any
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
 *<br> FUtil.getEnumJComboBox(DBConnectionThread.getConnection("metadata"), "tcpstation","rtsport1");
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
 * <br>    if(fieldEnum == null) fieldEnum = FUtil.getEnum(DBConnectionThread.getConnection("metadata"),"table","fieldName");
 * <br>    
 * <br>   // Get the int corresponding to an Enum String for storage in the data base and 
 * <br>   // the index into the JComboBox representing this Enum 
 * <br>   localVariable = FUtil.enumStringToInt(fieldEnum, enumString);
  * <br>
 * @author  D.C. Ketchum
 */
public class StationPanel extends javax.swing.JPanel {
  
  // NOTE : here define all variables general.  "ArrayList v" is used for main Comboboz
  static ArrayList<Station> v;             // ArrayList containing objects of this Station Type
  DBObject obj;
  ErrorTrack err=new ErrorTrack();
  boolean initiating=false;
  static DecimalFormat e6 = new DecimalFormat("0.0000E00");
  static DecimalFormat deg = new DecimalFormat("0.00000");  
  //USER: Here are the local variables

  long intFlags, intGroupMask, intTypeMask;


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
   Util.prt("chkForm Station");

   intFlags = fpFlags.getCurrentMask();
   intGroupMask = fpGroupMask.getCurrentMask();
   intTypeMask = fpTypeMask.getCurrentMask();

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
    UC.Look(fpGroupMask);
    UC.Look(fpTypeMask);
    UC.Look(fpFlags);
    UC.Look(typePanel);
    UC.Look(groupmaskPanel);
    UC.Look(flagPanel);
    error.setText("");
    addUpdate.setEnabled(false);
    addUpdate.setText("Enter a Station");
    
    //USER: Clear all fields for this form, this sets "defaults" for new screen
    // description.setText("");
//    station.setSelectedIndex(-1);
    jTextArea1.setText("");
    fpGroupMask.setAllClear();     
    fpTypeMask.setAllClear();     
    fpFlags.setAllClear();
    irname.setText("");
    irowner.setText("");
  }
 
  
  private Station newOne() {
    Timestamp t = new Timestamp(10000l);
    return new Station(0, ((String) station.getSelectedItem()).toUpperCase(), //, more
            "","","",0,"","",0,0,t,t,"","",0d,0d,0d,0d,0d,0d,"","",0
       );
  }
  
  private void oldOne(String p) throws SQLException {
    obj = new DBObject(DBConnectionThread.getThread("metadata"), 
        "metadata", "station","station",p);

    if(obj.isNew()) {
      Util.prt("object is new"+p);
    }
    else {
      //USER: Here set all of the form fields to data from the DBObject
//      station.setText(obj.getString("Station"));
      // Example : description.setText(obj.getString("description"));
      fpFlags.setMask(obj.getLong("flags"));
      fpGroupMask.setMask(obj.getLong("groupmask"));
      fpTypeMask.setMask(obj.getLong("typemask"));
      irname.setText(obj.getString("irname"));
      irowner.setText(obj.getString("irowner"));
      
      StringBuilder sbx = new StringBuilder();
      sbx.append("Region : ").append(obj.getString("region")).append("\n");
      sbx.append("Seed Site Name : ").append(obj.getString("seedSiteName")).append("\n");
      sbx.append("IR Name : ").append(obj.getString("irname")).append("\n");
      sbx.append("Seed Owner : ").append(obj.getString("seedOwner")).append("\n");
      sbx.append("IR Owner : ").append(obj.getString("irowner")).append("\n");
      sbx.append("Opened : ").append(obj.getTimestamp("opened").toString()).append("\n");
      sbx.append("Closed : ").append(obj.getTimestamp("closed").toString()).append("\n");
      sbx.append("XML URL : ").append(obj.getString("xmlurl")).append("\n");
      sbx.append("Seed URL : ").append(obj.getString("seedurl")).append("\n");
      sbx.append("latitude : ").append(deg.format(obj.getDouble("latitude"))).append("  ");
      sbx.append("longitude : ").append(deg.format(obj.getDouble("longitude"))).append("  ");
      sbx.append("elevation : ").append(deg.format(obj.getDouble("elevation"))).append("\n");
      sbx.append("seedlatitude : ").append(deg.format(obj.getDouble("seedlatitude"))).append("  ");
      sbx.append("seedlongitude : ").append(deg.format(obj.getDouble("seedlongitude"))).append("\n");
      jTextArea1.setText(sbx.toString());  
        
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
        lblStation = new javax.swing.JLabel();
        station = getJComboBox();
        addUpdate = new javax.swing.JButton();
        ID = new javax.swing.JTextField();
        jLabel9 = new javax.swing.JLabel();
        error = new javax.swing.JTextField();
        typePanel = new javax.swing.JPanel();
        lblTypemask = new javax.swing.JLabel();
        lblGroupmask = new javax.swing.JLabel();
        lblFlags = new javax.swing.JLabel();
        groupmaskPanel = new javax.swing.JPanel();
        flagPanel = new javax.swing.JPanel();
        jScrollPane1 = new javax.swing.JScrollPane();
        jTextArea1 = new javax.swing.JTextArea();
        labIRName = new javax.swing.JLabel();
        irname = new javax.swing.JTextField();
        labIROwner = new javax.swing.JLabel();
        irowner = new javax.swing.JTextField();

        setLayout(new java.awt.GridBagLayout());

        lblStation.setText("Station : ");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        add(lblStation, gridBagConstraints);

        station.setEditable(true);
        station.setFont(new java.awt.Font("Courier", 0, 13));
        station.setMinimumSize(new java.awt.Dimension(180, 20));
        station.setPreferredSize(new java.awt.Dimension(180, 20));
        station.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                stationActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        add(station, gridBagConstraints);

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
        gridBagConstraints.gridy = 0;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        add(error, gridBagConstraints);

        typePanel.setBackground(new java.awt.Color(204, 255, 204));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 8;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        add(typePanel, gridBagConstraints);

        lblTypemask.setText("TypeMask : ");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 8;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
        add(lblTypemask, gridBagConstraints);

        lblGroupmask.setText("GroupMask : ");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 10;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
        add(lblGroupmask, gridBagConstraints);

        lblFlags.setText("Flags : ");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 6;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
        add(lblFlags, gridBagConstraints);

        groupmaskPanel.setBackground(new java.awt.Color(204, 255, 204));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 10;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        add(groupmaskPanel, gridBagConstraints);

        flagPanel.setBackground(new java.awt.Color(204, 255, 204));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 6;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        add(flagPanel, gridBagConstraints);

        jScrollPane1.setPreferredSize(new java.awt.Dimension(480, 200));

        jTextArea1.setColumns(20);
        jTextArea1.setRows(5);
        jScrollPane1.setViewportView(jTextArea1);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 14;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        add(jScrollPane1, gridBagConstraints);

        labIRName.setText("IR name:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 11;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
        add(labIRName, gridBagConstraints);

        irname.setColumns(50);
        irname.setMinimumSize(new java.awt.Dimension(500, 28));
        irname.setPreferredSize(new java.awt.Dimension(500, 28));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 11;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        add(irname, gridBagConstraints);

        labIROwner.setText("IR Owner:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 12;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
        add(labIROwner, gridBagConstraints);

        irowner.setColumns(50);
        irowner.setMinimumSize(new java.awt.Dimension(500, 28));
        irowner.setPreferredSize(new java.awt.Dimension(500, 28));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 12;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        add(irowner, gridBagConstraints);
    }// </editor-fold>//GEN-END:initComponents

  private void addUpdateActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_addUpdateActionPerformed
    // Add your handling code here:
    
    // Do not change
    if(initiating) return;
    if(chkForm()) return;
    try {
      String p = station.getSelectedItem().toString();
      p = p.toUpperCase();                  // USER : drop this to allow Mixed case
      if(p.indexOf("-") > 0) p = p.substring(0,p.indexOf("-")).trim();
      if(!obj.isNew()) obj.refreshRecord();        // Refresh the result set so the sets will not fail on stale one
      obj.setString("station",p);
      
      // USER : Set all of the fields in data base.  obj.setInt("thefield", ivalue);
      obj.setLong("flags",intFlags);
      obj.setLong("groupmask",intGroupMask);
      obj.setLong("typemask",intTypeMask);
      obj.setString("irname", irname.getText());
      obj.setString("irowner", irowner.getText());

      // Do not change
      obj.updateRecord();
      v=null;       // force reload of combo box
      getJComboBox(station);
      clearScreen();
      station.setSelectedIndex(-1);
    }  catch  (SQLException  E)
    { Util.SQLErrorPrint(E,"station: update failed partno="+station.getSelectedItem());
      error.setText("Could Not Update File!");
      error.setBackground(UC.red);
      addUpdate.setEnabled(true);
    }      
  }//GEN-LAST:event_addUpdateActionPerformed

  private void stationActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_stationActionPerformed
    // Add your handling code here:
   find();
 
 
  }//GEN-LAST:event_stationActionPerformed
  
  
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JTextField ID;
    private javax.swing.JButton addUpdate;
    private javax.swing.JTextField error;
    private javax.swing.JPanel flagPanel;
    private java.awt.GridBagLayout gridBagLayout1;
    private javax.swing.JPanel groupmaskPanel;
    private javax.swing.JTextField irname;
    private javax.swing.JTextField irowner;
    private javax.swing.JLabel jLabel9;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JTextArea jTextArea1;
    private javax.swing.JLabel labIRName;
    private javax.swing.JLabel labIROwner;
    private javax.swing.JLabel lblFlags;
    private javax.swing.JLabel lblGroupmask;
    private javax.swing.JLabel lblStation;
    private javax.swing.JLabel lblTypemask;
    private javax.swing.JComboBox station;
    private javax.swing.JPanel typePanel;
    // End of variables declaration//GEN-END:variables
  private FlagPanel2 fpGroupMask;
  private FlagPanel2 fpFlags;
  private FlagPanel2 fpTypeMask;

    
    /** Creates new form StationPanel */
  public StationPanel() {
    initiating=true;
    initComponents();
    getJComboBox(station);                // set up the key JComboBox
    station.setSelectedIndex(-1);    // Set selected type
    Look();                    // Set color background
    initiating=false;
    fpFlags = new FlagPanel2(StationFlagsPanel.getJComboBox(), 600,2);
    UC.Look(fpFlags);
    UC.Look(fpFlags);
    flagPanel.add(fpFlags);   
    fpGroupMask = new FlagPanel2(GroupsPanel.getJComboBox(), 600,3);
    UC.Look(fpGroupMask);
    UC.Look(fpGroupMask);
    groupmaskPanel.add(fpGroupMask);   
    fpTypeMask = new FlagPanel2(TypePanel.getJComboBox(), 600,3);
    UC.Look(fpTypeMask);
    UC.Look(fpTypeMask);
    typePanel.add(fpTypeMask);   
   
    // USER:This is a good place for UC.Look(components) to get rid of the ugly Grey
    
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
  /** Udate a JComboBox to represent the rows represented by this panel
   *@param b The JComboBox to Update
   */
  public static void getJComboBox(JComboBox b) {
    b.removeAllItems();
    makeStations();
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
    //Util.prt("StationPanel.setJComboBoxToID id="+ID);
    for(int i=0; i<b.getItemCount(); i++) 
      if(((Station) b.getItemAt(i)).getID() == ID) {
          b.setSelectedIndex(i);
          //Util.prt("StationPanel.setJComboBoxToID id="+ID+" found at "+i);
      }
  }  
  
  /** Given a database ID find the index into a JComboBox consistent with this panel
   *@param ID The row ID from the database
   *@return The index in the JComboBox with this ID
   */
  public static int getJComboBoxIndex(int ID) {
    if(v == null) makeStations();
    for(int i=0; i<v.size(); i++) if( ID == ((Station) v.get(i)).getID()) return i;
    return -1;
  }
  /** Cause the main JComboBox to be reloaded*/
  public static void reload() {
    v = null;
    makeStations();
  }
    /* return a ArrayList with all of the Station
   * @return The ArrayList with the station
   */
  public static ArrayList getStationArrayList() {
    if(v == null) makeStations();
    return v;
  }
  /** Get the item corresponding to database ID
   *@param ID the database Row ID
   *@return The Station row with this ID
   */
  public static Station getStationWithID(int ID) {
    if(v == null) makeStations();
    int i=getJComboBoxIndex(ID);
    if(i >= 0) return (Station) v.get(i);
    else return null;
  }
  
  // This routine should only need tweeking if key field is not same as table name
  private static void makeStations() {
    if (v != null) return;
    v=new ArrayList<Station>(100);
    try {
      Statement stmt = DBConnectionThread.getConnection("metadata").createStatement();   // used for query
      String s = "SELECT * FROM metadata.station ORDER BY station;";
      ResultSet rs = stmt.executeQuery(s);
      while (rs.next()) {
        Station loc = new Station(rs);
//        Util.prt("MakeStation() i="+v.size()+" is "+loc.getStation());
        v.add(loc);
      }
    }
    catch (SQLException e) {
      Util.SQLErrorPrint(e,"makeStations() on table SQL failed");
    }    
  }
  
  // No changes needed
  private void find() {
    if(station == null) return;
    if(initiating) return;
    Station l;
    if(station.getSelectedIndex() == -1) {
      if(station.getSelectedItem() == null) return;
      l = newOne();
    } 
    else {
      l =  (Station) station.getSelectedItem();
    }
      
    if(l == null) return;
    String p = l.getStation();
    if(p == null) return;
    if(p.equals("")) {
      addUpdate.setEnabled(false);
      addUpdate.setText("Enter a Station!");
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
    { Util.SQLErrorPrint(E,"Station: SQL error getting Station="+p);
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
      Show.inFrame(new StationPanel(), UC.XSIZE, UC.YSIZE);
    }
    catch(InstantiationException e) {}
  }
}
