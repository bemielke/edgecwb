/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */

/*
 * MultiChannelPanel.java
 *
 * Created on July 11, 2006, 11:42 AM
 */

package gov.usgs.anss.metadatagui;
//import gov.usgs.edge.config.*;
import gov.usgs.anss.db.DBConnectionThread;
import gov.usgs.anss.gui.AllFlagsOnOffPanel;
import gov.usgs.anss.gui.FlagOnOffPanel2;
import gov.usgs.anss.util.ErrorTrack;
import gov.usgs.anss.util.Show;
import gov.usgs.anss.util.Util;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.DecimalFormat;
import java.util.ArrayList;

/**
 *
 * @author  davidketchum
 */
public class ResponseRegexpPanel extends javax.swing.JPanel {
  static DecimalFormat e6 = new DecimalFormat("0.0000E00");
  static DecimalFormat deg = new DecimalFormat("0.00000");
  static DecimalFormat d1 = new DecimalFormat("0.0");
//  FlagOnOffPanel2 sendtoPanel;
  FlagOnOffPanel2 flagsPanel;
//  FlagOnOffPanel2 linksPanel;
  Statement stmt;
  ErrorTrack err;
  int delayTime;
  
//  String tmp;
  

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
   Util.prt("chkForm Channel");
  
    // No CHANGES : If we found an error, color up error box
    if(err.isSet()) {
      error.setText(err.getText());
      error.setBackground(UC.red);
    }
    return err.isSet();
      
  }
  /** set intial state of screen */
  private void clearScreen() {


    for(int i=0; i<flagsPanel.getList().size(); i++) {
      flagsPanel.setOnRadioSelected(i, false);
      flagsPanel.setOffRadioSelected(i,false);
    }

    channels.setText("");
    regexp.setText("");

  }
  

  
  /** Creates new form MultiChannelPanel */
  public ResponseRegexpPanel() {
    err = new ErrorTrack();
    initComponents();
    ArrayList<FlagOnOffPanel2> subpanels = new ArrayList<FlagOnOffPanel2>(1);
//    sendtoPanel = new FlagOnOffPanel2(SendtoPanel.getJComboBox()); subpanels.add(sendtoPanel);
    flagsPanel = new FlagOnOffPanel2(ResponseFlagsPanel.getJComboBox(),1); subpanels.add(flagsPanel);
//    linksPanel = new FlagOnOffPanel2(LinksPanel.getJComboBox()); subpanels.add(linksPanel);
    javax.swing.JPanel allflags = new AllFlagsOnOffPanel(subpanels);
    sendtoScroll.add(allflags);
    sendtoScroll.setViewportView(allflags);
    
    Look();
    UC.Look(fixed);
    clearScreen();
  }
  private void Look() {
    UC.Look(this);                    // Set color background
  }

  /** This method is called from within the constructor to
   * initialize the form.
   * WARNING: Do NOT modify this code. The content of this method is
   * always regenerated by the Form Editor.
   */
  // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
  private void initComponents() {

    labRegexp = new javax.swing.JLabel();
    regexp = new javax.swing.JTextField();
    error = new javax.swing.JTextField();
    channelsPane = new javax.swing.JScrollPane();
    channels = new javax.swing.JTextArea();
    fixed = new javax.swing.JPanel();
    sendtoScroll = new javax.swing.JScrollPane();
    addUpdate = new javax.swing.JButton();
    reload = new javax.swing.JButton();

    setMaximumSize(new java.awt.Dimension(800, 800));
    setMinimumSize(new java.awt.Dimension(750, 600));
    setPreferredSize(new java.awt.Dimension(800, 800));
    setRequestFocusEnabled(false);

    labRegexp.setText("RegExp (or WHERE clause):");
    add(labRegexp);

    regexp.setColumns(40);
    regexp.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        regexpActionPerformed(evt);
      }
    });
    regexp.addFocusListener(new java.awt.event.FocusAdapter() {
      public void focusLost(java.awt.event.FocusEvent evt) {
        regexpFocusLost(evt);
      }
    });
    add(regexp);

    error.setBackground(new java.awt.Color(192, 192, 192));
    error.setColumns(50);
    error.setEditable(false);
    add(error);

    channelsPane.setPreferredSize(new java.awt.Dimension(700, 300));

    channels.setColumns(80);
    channels.setFont(new java.awt.Font("Monospaced", 0, 13));
    channels.setMaximumSize(new java.awt.Dimension(640, 500));
    channels.setMinimumSize(new java.awt.Dimension(640, 500));
    channelsPane.setViewportView(channels);

    add(channelsPane);

    fixed.setPreferredSize(new java.awt.Dimension(700, 20));
    fixed.setLayout(new java.awt.GridBagLayout());
    add(fixed);

    sendtoScroll.setFont(new java.awt.Font("Lucida Grande", 0, 12));
    sendtoScroll.setMaximumSize(new java.awt.Dimension(740, 270));
    sendtoScroll.setMinimumSize(new java.awt.Dimension(740, 270));
    sendtoScroll.setPreferredSize(new java.awt.Dimension(740, 270));
    add(sendtoScroll);

    addUpdate.setText("Update Changes");
    addUpdate.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        addUpdateActionPerformed(evt);
      }
    });
    add(addUpdate);

    reload.setText("Reload");
    reload.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        reloadActionPerformed(evt);
      }
    });
    add(reload);
  }// </editor-fold>//GEN-END:initComponents

  private void reloadActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_reloadActionPerformed
// TODO add your handling code here:
    
    ResponseFlagsPanel.reload();
    flagsPanel.buildFlagPanel(ResponseFlagsPanel.getJComboBox(), 1);

    
  }//GEN-LAST:event_reloadActionPerformed

  private void addUpdateActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_addUpdateActionPerformed
// TODO add your handling code here:
    if(chkForm()) return;
    int left =90;
    int right =96;
    int chklength = 97;
    int length;
    long mask;
    String id;
    Statement stmtout=null;
    try {
      String [] stats = channels.getText().split("\n");
      if(stmtout == null) {
        stmtout = DBConnectionThread.getConnection("metadata").createStatement(
            ResultSet.TYPE_FORWARD_ONLY,ResultSet.CONCUR_UPDATABLE);
      }
      ResultSet rs;
      for (String stat : stats) {
        // Get ID
        length = stat.length();
//        right = stats[j].indexOf("]",left);
        // check for valid
        if (length == chklength) {
          id = stat.substring(left, right).trim();
          // Get original flags mask
          rs = stmtout.executeQuery("SELECT * FROM response WHERE ID="+id+";");
          while(rs.next()){
            mask = rs.getLong("flags");
            long newmask = flagsPanel.getCurrent(mask);
            if (newmask!=mask) {            
              Util.prt("for "+rs.getString("channel"));
              Util.prt("   changed flags after="+Util.toHex(mask));
              rs.updateLong("flags", newmask);
              rs.updateRow();
            }
          }
          rs.close();
        }
      }
      stmtout.close();
    }
    catch(SQLException e) {
      Util.SQLErrorPrint(e,"Trying to update from ResponseRegexpPanel");
    }
    clearScreen();
  }//GEN-LAST:event_addUpdateActionPerformed

  private void regexpFocusLost(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_regexpFocusLost
// TODO add your handling code here:
    doRegexp();
  }//GEN-LAST:event_regexpFocusLost

  private void regexpActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_regexpActionPerformed
// TODO add your handling code here:
    // Need to run list of matching channels, populate the channels text area
    doRegexp();
    
  }//GEN-LAST:event_regexpActionPerformed
  private void doRegexp() {

    if(regexp.getText().equals("")) return;
    try {
      Connection C = DBConnectionThread.getConnection("metadata");
      if(stmt == null) stmt = C.createStatement();
      ResultSet rs ;
      if(regexp.getText().length() >= 5 && regexp.getText().substring(0,5).equalsIgnoreCase("WHERE")) {
        rs = stmt.executeQuery("SELECT * FROM metadata.response "+regexp.getText()+" ORDER BY channel, effective DESC");
      }
      else {
        rs = stmt.executeQuery("SELECT * FROM metadata.response WHERE channel REGEXP '"+
          regexp.getText()+"' ORDER BY channel, effective DESC");
      }
      String lastStation="";
      StringBuilder sbx = new StringBuilder(1000);
      StringBuilder sbchan = new StringBuilder(1000);

      flagsPanel.setAllClear();
      
      ArrayList flag = flagsPanel.getList();
      long [] flags = new long[flag.size()];
      for(int i=0; i<flag.size(); i++) flags[i]=-1;

      int nchan=0;
      // for each member of the selected set, see if all of the parameters are set the 
      // same by calling chkParm for each value.  the parameter check must be initialized
      // to -1 to start.
      while (rs.next()) {
        long flagsMask = rs.getLong("flags");
        String flagsString="";
        for(int i=0; i<64; i++) {
          if( (flagsMask & 1L<<i) != 0) {
            ResponseFlags r = ResponseFlagsPanel.getItemWithID(i+1);
            if(r != null) flagsString += r.getResponseflags()+"|";
          }
        }      
        if(flagsString.endsWith("|")) flagsString=flagsString.substring(0,flagsString.length()-1);
        sbchan.append(Util.rightPad(rs.getString("channel"),12)).append(" - ");
        sbchan.append(" From ").append(rs.getString("effective"));
        sbchan.append(" To ").append(rs.getString("endingDate"));
        sbchan.append("                      [").append(Util.leftPad(rs.getString("ID"),6)).append("]\n");
        // Load x with station info in case only one chosen by QUERY
        sbx.append(Util.rightPad(rs.getString("channel"),12)).append(" - ");
        sbx.append(" From ").append(rs.getString("effective"));
        sbx.append(" To ").append(rs.getString("endingDate"));
        sbx.append("                      [").append(Util.leftPad(rs.getString("ID"),6)).append("]\n");
        sbx.append("Dip             : ").append(d1.format(rs.getDouble("dip"))).append("  ");
        sbx.append("Azimuth         : ").append(d1.format(rs.getDouble("azimuth"))).append("  ");
        sbx.append("Rate            : ").append(d1.format(rs.getDouble("rate"))).append("  ");
        sbx.append("Units           : ").append(rs.getString("units")).append("\n");
        sbx.append("InstrumentGain  : ").append(d1.format(rs.getDouble("instrumentGain"))).append("  ");
        sbx.append("InstrumentType : ").append(rs.getString("instrumentType")).append("\n");
        sbx.append("a0              : ").append(e6.format(rs.getDouble("a0"))).append("  ");
        sbx.append("a0calc : ").append(e6.format(rs.getDouble("a0calc"))).append("  ");
        sbx.append("a0freq : ").append(deg.format(rs.getDouble("a0freq"))).append("  ");
        sbx.append("SeedFlags : ").append(rs.getString("seedflags")).append("\n");
        sbx.append("Flags           : ").append(Util.toHex(flagsMask)).append(":").append(flagsString).append("\n");
        sbx.append("Sensitivity     : ").append(e6.format(rs.getDouble("sensitivity"))).append("  ");
        sbx.append("SensitivityCalc : ").append(e6.format(rs.getDouble("sensitivityCalc"))).append("\n");
        sbx.append("latitude        : ").append(deg.format(rs.getDouble("latitude"))).append("  ");
        sbx.append("longitude : ").append(deg.format(rs.getDouble("longitude"))).append("  ");
        sbx.append("elevation : ").append(d1.format(rs.getDouble("elevation"))).append("  ");
        sbx.append("Depth : ").append(d1.format(rs.getDouble("depth"))).append("\n");
        sbx.append("CookedResponse  : ").append(rs.getString("cookedresponse"));
        sbx.append("\n--------------------------------------------------------------------------------\n");

        nchan++;

        long mask = rs.getLong("flags");
        for(int i=0; i<flag.size(); i++) 
          flags[i] = chkParm(flags[i], ((ResponseFlags) flag.get(i)).getMask() & mask);
      }
      sbx.append(nchan).append(" channel-epochs found\n");
//      sb.append("\n"+nchan+" channels\n");
      if (nchan<120) channels.setText(sbx.toString());
      else channels.setText(sbchan.toString());
      channels.setCaretPosition(0);
      addUpdate.setText("Update "+nchan+" channels");

      // Set on off or neither as determined by the chkParms
      for(int i=0; i<flag.size(); i++) {
        if(flags[i] != -2) {
          if(flags[i] == 0) flagsPanel.setOff(i); 
          else flagsPanel.setOn(i);
        }
      }
    }
    catch (SQLException e) {
      channels.setText("SQL error getting channels\n"+e.getMessage()+"\n"+e.getErrorCode()+"\n"+e.getSQLState());
    }    
  }
  /** The chkParms work this way.  User sets commID to -1 at beginning.  It then
   * calls chkParm(commID, i) where i is each suceeding value for a member of the 
   * selected set.  chkParms return -2 when parameters are found not to be set all the same
   * and the value when they are all set the same */
  private int chkParm(int commID, int i) {
    if(commID != -2) {
      if(commID == -1) commID=i;
      if(commID != i)  commID = -2;
    }
    return commID;
  }
  private String chkParm(String commID, String i) {
    if(!commID.equals("CHG") ) {
      if(commID.equals("")) commID=i;
      if(!commID.equals(i))  commID = "CHG";
    }
    return commID;
  }
  private long chkParm(long commID, long i) {
    if(commID != -2) {
      if(commID == -1) commID=i;
      if(commID != i)  commID = -2;
    }
    return commID;
  }
      
  
  // Variables declaration - do not modify//GEN-BEGIN:variables
  private javax.swing.JButton addUpdate;
  private javax.swing.JTextArea channels;
  private javax.swing.JScrollPane channelsPane;
  private javax.swing.JTextField error;
  private javax.swing.JPanel fixed;
  private javax.swing.JLabel labRegexp;
  private javax.swing.JTextField regexp;
  private javax.swing.JButton reload;
  private javax.swing.JScrollPane sendtoScroll;
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
      Show.inFrame(new ResponseRegexpPanel(), UC.XSIZE, UC.YSIZE);
    }
    catch(InstantiationException e) {}
  }
  
}
