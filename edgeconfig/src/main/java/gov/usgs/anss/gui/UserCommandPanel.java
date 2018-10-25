/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */

package gov.usgs.anss.gui;
import gov.usgs.anss.db.DBConnectionThread;
import gov.usgs.anss.util.SeedUtil;
import gov.usgs.anss.util.Show;
import gov.usgs.anss.util.TextStatusClient;
import gov.usgs.anss.util.Util;
import gov.usgs.edge.config.RoleInstancePanel;
import gov.usgs.edge.config.RoleInstance;
import gov.usgs.edge.config.CpuPanelInstance;
import java.awt.Color;
import java.awt.Point;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;
import javax.swing.JOptionPane;
/**
 * UserCommandPanel - take user roles and command and execute them on a remote node
 * through the CommandServer.  
 * 
 * <pre>
 * To add a role/node to the list of roles :
 * In Anss.jar :
 * 
 * 1)  Add the computer to the Master->"role" table. (cwbpub IP=137.227.224.97 Linux) - 
 *     exit GUI as there is no "reload" for the next step
 * 2) Master->Role-Link IPs add new role to public link - put some gargage in CommLinkKey 
 *    to force a new one, select the role (cwbpub), and commlink (Public), enter the IP add add it
 * 3) In MasterFiles->Remote Commands add a command for this role (RemoteCmd=cwbpub, 
 *    Roles=cwbpub, Command=Execute bash scripts/doBash.bash scripts/cwbpub.bash 
 * 
 * You need to insure that on the node that  scripts/cwbpub.bash is present.  Normally you can copy another one like gacq1.bach to cwbpub.bash.
 *</PRE>
 * Created on December 30, 2003, 10:45 AM
 */

public final class UserCommandPanel extends javax.swing.JPanel {
  private String db="anss";
  private final TreeMap<String, ArrayList<RemoteCommand>> commands = new TreeMap<String, ArrayList<RemoteCommand>>();
  private String filename;
  private boolean starting;
  private TextStatusClient client=null;
  private String lastDialog="";
  private final UpdateText updater;
  private int defaultNodeItem =0;
  public UserCommandPanel(String dbin)
  { starting = true;
    if(db != null)
      if(!db.equals("")) db = dbin;
    initComponents();
    buildNodeJComboBox();
    
    GetKeys keys = new GetKeys();
    query.addKeyListener(keys);
    this.addKeyListener(keys);
    updater = new UpdateText();     // This paint the text area if a client is running

    saveFile.setVisible(false);
    saveFile.setEnabled(false);
    if(node.getItemCount() == 1) node.setSelectedIndex(0);
    else if(node.getItemCount() -1 < defaultNodeItem) node.setSelectedIndex(-1);
    else node.setSelectedIndex(defaultNodeItem);
    starting=false;

  }
  private void buildNodeJComboBox() {
    UC.Look(this);
    commands.clear();
    try {
      Statement stmt = DBConnectionThread.getConnection(DBConnectionThread.getDBSchema()).createStatement(); 
      ResultSet rs = stmt.executeQuery("SELECT * FROM "+db+".remotecommand ORDER BY remotecommand");
      while(rs.next()) {
        RemoteCommand cmd = new RemoteCommand(rs);
        String [] nodes = cmd.getNodes().split("\\s");
        for (String node1 : nodes) {
          ArrayList<RemoteCommand> cmds = commands.get(node1.trim());
          if (cmds == null) {
            cmds = new ArrayList<RemoteCommand>(10);
            commands.put(node1.trim(), cmds);
          }
          // Add this command to the list for this node
          cmds.add(cmd);
        }
      }
      rs.close();
      // Now build a JComboBox with the CPU names and IP address
       node.removeAllItems();
      if(db.equals("anss")) {
        Iterator<String> itr = commands.keySet().iterator();
        while(itr.hasNext()) {
          String key = itr.next();
          if(key.equals("*")) continue;
          rs = stmt.executeQuery("SELECT * FROM anss.cpulinkip WHERE cpulinkip='"+key.toLowerCase().trim()+"-Public'");
          if(rs.next()) {
            key = key+" "+rs.getString("ipadr").replaceAll("\\.0",".");
          }
          else {
            rs.close();
            String s = "SELECT * FROM edge.role where role='"+key.toLowerCase().trim()+"'";
            Util.prta(s);
            rs=stmt.executeQuery(s);
            if(rs.next()) {
              key = key + " "+ rs.getString("ipadr").replaceAll("\\.0",".").replaceAll("^0", "");
            }
            else {
              Util.prta("Did not find key="+key+" in role");
              rs.close();
              continue;
            }
          }
          rs.close();
          node.addItem(key);
          if(key.contains("gacq1") ) defaultNodeItem = node.getItemCount() -1;
        }
        stmt.close();
      }
      else {    // This is not a anss/edge node, make the node list from roles
        String s = "SELECT * FROM edge.role";
        Util.prta(s);
        String key;
        rs=stmt.executeQuery(s);
        while(rs.next()) {
          key = rs.getString("role")+ " "+ rs.getString("ipadr").replaceAll("\\.0",".").replaceAll("^0", "");
          node.addItem(key);
        }
        rs.close();
      }
    }
    catch(SQLException e) {
      Util.SQLErrorPrint(e,"Getting remote commands");
      System.exit(1);
    }
  }
  /** This method is called from within the constructor to
   * initialize the form.
   * WARNING: Do NOT modify this code. The content of this method is
   * always regenerated by the Form Editor.
   */

  // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
  private void initComponents() {

    nodeLabel = new javax.swing.JLabel();
    node = new javax.swing.JComboBox();
    jSeparator1 = new javax.swing.JSeparator();
    labIP = new javax.swing.JLabel();
    regexp = new javax.swing.JTextField();
    jSeparator2 = new javax.swing.JSeparator();
    jLabel1 = new javax.swing.JLabel();
    command = new javax.swing.JComboBox();
    saveFile = new javax.swing.JButton();
    error = new javax.swing.JTextField();
    queryScroll = new javax.swing.JScrollPane();
    query = new javax.swing.JTextArea();
    reload = new javax.swing.JButton();

    nodeLabel.setText("Role:");
    add(nodeLabel);

    node.setMaximumRowCount(20);
    node.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        nodeActionPerformed(evt);
      }
    });
    add(node);

    jSeparator1.setMinimumSize(new java.awt.Dimension(750, 2));
    jSeparator1.setPreferredSize(new java.awt.Dimension(750, 10));
    jSeparator1.setSize(new java.awt.Dimension(750, 10));
    add(jSeparator1);

    labIP.setText("RegExp:");
    add(labIP);

    regexp.setColumns(39);
    regexp.setMinimumSize(new java.awt.Dimension(600, 28));
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

    jSeparator2.setMinimumSize(new java.awt.Dimension(750, 2));
    jSeparator2.setPreferredSize(new java.awt.Dimension(750, 2));
    jSeparator2.setSize(new java.awt.Dimension(750, 2));
    add(jSeparator2);

    jLabel1.setText("Command:");
    add(jLabel1);

    command.setMaximumRowCount(40);
    command.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        commandActionPerformed(evt);
      }
    });
    add(command);

    saveFile.setText("Save file");
    saveFile.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        saveFileActionPerformed(evt);
      }
    });
    add(saveFile);

    error.setEditable(false);
    error.setColumns(45);
    add(error);

    queryScroll.setPreferredSize(new java.awt.Dimension(715, 425));

    query.setBackground(new java.awt.Color(204, 204, 204));
    query.setColumns(80);
    query.setFont(new java.awt.Font("Monospaced", 0, 12)); // NOI18N
    query.setRows(50);
    query.setBorder(javax.swing.BorderFactory.createEtchedBorder());
    queryScroll.setViewportView(query);

    add(queryScroll);

    reload.setText("Reload");
    reload.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        reloadActionPerformed(evt);
      }
    });
    add(reload);
  }// </editor-fold>//GEN-END:initComponents

  private void saveFileActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_saveFileActionPerformed
// TODO add your handling code here:
    String text = query.getText();
    if(text.substring(text.length()-1).equals("\n"))
      text = text.substring(0,text.length()-1);
    String nd = (String) node.getSelectedItem();
    if(nd.contains(" ")) nd = nd.substring(0,nd.indexOf(" "));
    client = new TextStatusClient(nd,7984, 
        "putfile "+filename.trim(), text.replaceAll("Command output :\n",""));
    query.setEditable(false);
    query.setBackground(Color.LIGHT_GRAY);
    query.setText(client.getText());
    saveFile.setEnabled(false);
    saveFile.setVisible(false);
  }//GEN-LAST:event_saveFileActionPerformed

  private void commandActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_commandActionPerformed
// TODO add your handling code here:
    String [] vars ;
    String [] defaults;
    if(starting) return;
    query.setText("Command started="+command.getSelectedIndex());
    if(starting) return;
    saveFile.setEnabled(false);
    saveFile.setVisible(false);
    query.setBackground(Color.LIGHT_GRAY);
    query.setEditable(false);
    if(client != null) client.terminate();
    if(command.getSelectedIndex() == -1) return;
    RemoteCommand cmd = (RemoteCommand) command.getSelectedItem();
    boolean edit=false;
    query.setText("Command started = "+cmd);
    GregorianCalendar now = new GregorianCalendar();
    int [] ymd = SeedUtil.fromJulian(SeedUtil.toJulian(now));
    char vers= (char) ('0'+(SeedUtil.doy_from_ymd(ymd) % 10));
    String nd = ((String) node.getSelectedItem()).trim();
    String roleName="";
    if(nd.contains(" ")) {roleName=nd.substring(0, nd.indexOf(" ")).trim();nd = nd.substring(nd.indexOf(" ")+1); }
    else {nd = nd.trim();}
    String cpuNumber="0";
    ArrayList<RoleInstance> roles = RoleInstancePanel.getRoleVector();
    if(roles != null) {
      int cpuID=-1;
      for(RoleInstance role: roles) if(role.getRole().equalsIgnoreCase(roleName)) {cpuID=role.getCpuID(); break;}
      if(cpuID > 0) {
        gov.usgs.edge.config.Cpu cpu =  CpuPanelInstance.getCpuWithID(cpuID);
        if(cpu != null) cpuNumber = ""+cpu.getNodeNumber();
      }
    }

    String cmd2
        = cmd.getCommand();
    Util.prta("command:"+cmd2);

    // See if there is a dialog box to process
    // remote commands can have optional arguments expressed like [$1:tagname=def].  We need to process these so we know
    // what to do with them.
    if(!cmd.getDialog().equals("")) {
      String [] parts = cmd.getDialog().split("\\[");
      String defline="";
      vars = new String[parts.length];
      defaults = new String[parts.length];
      for(int i=0; i<parts.length; i++) {vars[i]=""; defaults[i] = "";}
      String outline=parts[0];
      for(int i=1; i<parts.length; i++) { 
        String [] options = parts[i].split("[:=\\]]");
        vars[i-1] = options[0];
        if(vars[i-1].substring(0,1).equals("$")) vars[i-1] = "\\"+vars[i-1];
        if(options.length >= 3) {
          defaults[i-1] = options[2];
          defline +=options[2]+" ";
        }
        outline += " ["+options[1]+"]";
        //outline += " "+options[options.length-1];
      }
      defline = defline.replaceAll("%j",""+vers);
      defline = defline.replaceAll("%Y",""+now.get(Calendar.YEAR));
      defline = defline.replaceAll("%J",""+now.get(Calendar.DAY_OF_YEAR));
      defline = defline.replaceAll("%M",""+now.get(Calendar.MONTH)+1);
      defline = defline.replaceAll("%D",""+now.get(Calendar.DAY_OF_MONTH));
      defline = defline.replaceAll("%N",cpuNumber);
      defline = defline.replaceAll("%L",""+lastDialog);

      String ans = JOptionPane.showInputDialog(null, outline,defline);
      if(ans == null) {Util.prta("user cancelled"); return;}      // User canceled out
      if(ans.equalsIgnoreCase("s")) ans = cmd.getUserString();
      else cmd.setUserString(ans);
      lastDialog=ans;
      String [] values = ans.split("\\s");
      for(int i=0; i<parts.length; i++) {
        if(i < values.length) { 
          if(!vars[i].equals("")) cmd2 = cmd2.replaceAll(vars[i], values[i] );
        }
        else if(!vars[i].equals("")) cmd2 = cmd2.replaceAll(vars[i], defaults[i]);
      }
      if(cmd2.contains("$@")) { 
        String allString = "";
        for (String value : values) {
          allString += value + " ";
        }
        allString = allString.trim();
        cmd2 = cmd2.replaceAll("\\$@",allString);
      }
    }
    // replace know variables
    cmd2 = cmd2.replaceAll("%j", ""+vers);
    Util.prta("command is now :"+cmd2);
    if(cmd2.contains("getfile")) {
      filename = cmd2.substring(8);
      edit=true;
    }
    client = new TextStatusClient((String) nd, 7984, cmd2);
    
    if(edit) {      // If its a edit command, we need to make query editable and white, eable save button
      query.setBackground(Color.WHITE);
      query.setEditable(true);
      saveFile.setVisible(true);
      saveFile.setEnabled(true);
    }
 
    //command.addItem("");
    //command.addItem("");

    if(regexp.getText().equals("") && client != null) query.setText(client.getText());
    else if(client != null) query.setText(client.getText(regexp.getText()));
    queryScroll.getViewport().setViewPosition(new Point(1,1));
   
    
  }//GEN-LAST:event_commandActionPerformed

  private void regexpActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_regexpActionPerformed
    // Add your handling code here:
    if(regexp.getText().equals("") && client != null) query.setText(client.getText());
    else if(client != null) query.setText(client.getText(regexp.getText()));
    queryScroll.getViewport().setViewPosition(new Point(1,1));

  }//GEN-LAST:event_regexpActionPerformed

  private void regexpFocusLost(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_regexpFocusLost
    // Add your handling code here:
    Util.prta("regexp="+regexp+"\nclient="+client+"\nquery="+query);
    if(regexp.getText().equals("") && client != null) query.setText(client.getText());
    else if(client != null) query.setText(client.getText(regexp.getText()));
    queryScroll.getViewport().setViewPosition(new Point(1,1));
  

  }//GEN-LAST:event_regexpFocusLost

  private void nodeActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_nodeActionPerformed
    // TODO add your handling code here:
    // The user has selected some CPU, change the command JComboBox to have commands for that node
    if(node.getSelectedIndex() == -1 || starting) return;
    String nd = (String) node.getSelectedItem();
    String [] parts = nd.split("\\s");
    ArrayList<RemoteCommand> cmds = commands.get(parts[0].trim().toLowerCase());
    if(cmds == null) {
      Util.prta("Selected node does not exist node="+parts[0]);
      return;
    }
    // now build up the commands in the JComboBox
    command.removeAllItems();
    for (RemoteCommand cmd : cmds) {
      command.addItem(cmd);
    }
    if(nd.contains("gldqc") || nd.contains("gldcds")) return;
    cmds = commands.get("*");
    if(cmds != null) 
      for (RemoteCommand cmd : cmds) {
        command.addItem(cmd);
    }
    command.setSelectedIndex(0);
  }//GEN-LAST:event_nodeActionPerformed

  private void reloadActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_reloadActionPerformed
    // TODO add your handling code here:
    this.buildNodeJComboBox();
  }//GEN-LAST:event_reloadActionPerformed
  /*private void wait(int l) {
    long start = System.currentTimeMillis();
    while ( (System.currentTimeMillis() - start) < l) {}
  }*/

  public final class UpdateText extends Thread {
    public UpdateText() {
      start();
    }
    @Override
    public void run() {
      for(;;) {
        if(client != null) {
          if(regexp.getText().equals("")) query.setText(client.getText());
          else query.setText(client.getText(regexp.getText()));
          query.setCaretPosition(query.getText().length());
          if(!client.isAlive()) {
            Util.prta("Client has died ="+client);
            try{sleep(1000);} catch(InterruptedException e) {}
            client = null;
          }
        }
        try{sleep(1000);} catch(InterruptedException e) {}
      }
    }

  }
  public final class GetKeys implements KeyListener {
    @Override
    public void keyPressed(KeyEvent event) {
      //System.out.println("press"+event.toString());
    }
    @Override
    public void keyReleased(KeyEvent event) {
      int key = event.getKeyCode();
      //System.out.print("released="+key+" code="+event);

    }
    @Override
    public void keyTyped(KeyEvent event) {
      byte b = (byte) event.getKeyChar();
      //Util.prta("sess="+session+" char="+b);
      if(b == 3) {
        if(client != null) client.terminate();
        client=null;
        query.append("\nTerminated by user.\n");
      }
    }
  }
  // Variables declaration - do not modify//GEN-BEGIN:variables
  private javax.swing.JComboBox command;
  private javax.swing.JTextField error;
  private javax.swing.JLabel jLabel1;
  private javax.swing.JSeparator jSeparator1;
  private javax.swing.JSeparator jSeparator2;
  private javax.swing.JLabel labIP;
  private javax.swing.JComboBox node;
  private javax.swing.JLabel nodeLabel;
  private javax.swing.JTextArea query;
  private javax.swing.JScrollPane queryScroll;
  private javax.swing.JTextField regexp;
  private javax.swing.JButton reload;
  private javax.swing.JButton saveFile;
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
          {Util.prta("Could not connect to DB "+jcjbl); System.exit(1);}
      Show.inFrame(new CommLinkPanel(), UC.XSIZE, UC.YSIZE);
    }
    catch(InstantiationException e) {} 
  }
}
