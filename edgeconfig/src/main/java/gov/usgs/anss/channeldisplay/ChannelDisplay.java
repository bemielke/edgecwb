/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.channeldisplay;

import gov.usgs.anss.channelstatus.SourceStationComponentComparator;
import gov.usgs.anss.channelstatus.NodeProcessComparator;
import gov.usgs.anss.channelstatus.LastDataComparator;
import gov.usgs.anss.channelstatus.LatencyComparator;
import gov.usgs.anss.channelstatus.ChannelStatus;
import gov.usgs.anss.util.UserPropertiesPanel;
import gov.usgs.alarm.SNWGroupEventProcess;
import gov.usgs.anss.db.DBConnectionThread;
import gov.usgs.anss.gui.LearningJComboBox;
import gov.usgs.anss.gui.SimpleAudioPlayer;
import gov.usgs.anss.net.StatusSocketReader;
import gov.usgs.anss.util.*;
import gov.usgs.edge.snw.SNWGroup;
import gov.usgs.edge.snw.SNWGroupPanel;
import gov.usgs.anss.db.DBSetup;
import java.awt.Color;
import java.net.URL;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.TreeSet;
import javax.swing.JOptionPane;
import javax.swing.WindowConstants;
import java.awt.event.ComponentEvent;

/**
 * ChannelDisplay.java
 *
 * Created on December 22, 2003, 4:48 PM
 *
 * @author ketchum
 */
public final class ChannelDisplay extends javax.swing.JFrame {

  private static final String VERSION = "2.22 20180104";
  private static SNWGroupEventProcess snw;
  private static boolean shuttingDown;
  private static StatusSocketReader channels;
  public static ChannelDisplay theChannelDisplay;
  private static boolean forceBad;
  private final TimerLoop tl;
  private NetStationLocationChannelComparator netStationLocationComparator;
  private StationComponentComparator stationComponentComparator;
  private SourceStationComponentComparator sourceStationComparator;
  private LatencyComparator latencyComparator;
  private LastDataComparator lastDataComparator;
  private NodeProcessComparator nodeProcessComparator;
  //StationComponentComparator sourceStationComparator;
  private LearningJComboBox sourceLearn, networkLearn, cpuLearn;
  private final Display display;

  // DIsplay related variables to be reused
  private final DecimalFormat df, dfi5, dfi6;
  private StringBuffer tags[];
  private ChannelStatus cs[];
  private Color colors[];
  private int updateNumber;
  private int beeps;
  private int beepInterval;
  private long totalbytes = -1;
  private String lastHour = "";

  private final StringBuffer sb;
  private boolean ready;        // when false, display.doUpdate cannot be run.  We found the many possible
  // reasons to update (timer expiration, changes in JComboBoxes, selection
  // in JComboBoxes, would cause the routine to reenter.  I thought
  // synchronizing the display.doUpdate would do this, but apparently not
  private boolean immediateUpdate;
  private boolean terminate;
  private boolean isClosed;
  private static boolean noDB;
  private boolean dbg = false;
  private final TreeSet stations;
  private final ChannelDisplayShutdown shutdown;

  public static boolean isNoDB() {
    return noDB;
  }

  public static void toggleForceBad() {
    forceBad = !forceBad;
  }

  public javax.swing.JPanel getThePanel() {
    return jPanel1;
  }

  public void doImmediateUpdate() {
    immediateUpdate = true;
  }

  public void terminate() {
    terminate = true;
    DBConnectionThread.shutdown();
    jPanel1.removeAll();
    //Runtime.getRuntime().removeShutdownHook(shutdown);
    display.close();
    jPanel1.setVisible(false);
    this.dispose();
    isClosed = true;
  }

  public boolean isClosed() {
    return isClosed;
  }

  /**
   * Creates new form ChannelDisplay
   */
  public ChannelDisplay() {
    ready = false;
    beepInterval = 6;
    Util.addDefaultProperty("StartX", "10");
    Util.addDefaultProperty("StartY", "22");
    Util.addDefaultProperty("AlertMode", "false");
    Util.addDefaultProperty("Sort", "0");
    Util.addDefaultProperty("Refresh", "1");
    Util.addDefaultProperty("SNWGroupID", "14");
    Util.addDefaultProperty("SoundOff", "false");
    Util.addDefaultProperty("popupX", "10");
    Util.addDefaultProperty("popupY", "50");
    Util.addDefaultProperty("sound", "sinister1");
    if (Util.getProperty("MySQLServer") != null && Util.getProperty("DBServer") == null) {
      Util.setProperty("DBServer", Util.getProperty("MySQLServer") + "/3306:edge:mysql:edge");
      Util.setProperty("StatusDBServer", Util.getProperty("DBServer"));
      Util.setProperty("StatusServer", Util.getProperty("MySQLServer"));
      Util.getProperties().remove("MySQLServer");
    }
    Util.addDefaultProperty("DBServer", "localhost/3306:edge:mysql:edge");
    Util.addDefaultProperty("StatusDBServer", "localhost/3306:status:mysql:status");
    Util.addDefaultProperty("StatusServer", "localhost");
    if (DBSetup.checkEmpty()) {
      UserPropertiesPanel p = new UserPropertiesPanel();
      p.updateButtonActionPerformed(null);
      Util.prt(p.toString());
      Util.saveProperties();
    }
    /*if (USGSPropertyFixer.checkProperties()) {
      UserPropertiesPanel p = new UserPropertiesPanel();
      p.updateButtonActionPerformed(null);
      Util.prt(p.toString());
      Util.saveProperties();
    }*/
    shutdown = new ChannelDisplayShutdown();
    if (Util.getProperty("DBServer").contains("NoDB")) {
      Util.prt("Starting up in NoDB mode");
      noDB = true;
    } else {
      DBConnectionThread.init(Util.getProperty("DBServer"));

      Runtime.getRuntime().addShutdownHook(shutdown);
      setLocation((int) Double.parseDouble(Util.getProperty("StartX")),
              (int) Double.parseDouble(Util.getProperty("StartY")));
      try {
        DBConnectionThread dbconnedge = new DBConnectionThread(Util.getProperty("DBServer"), "user", "edge",
                false, false, "edge", Util.getOutput());
        if (!dbconnedge.waitForConnection()) {
          if (!dbconnedge.waitForConnection()) {
            if (!dbconnedge.waitForConnection()) {
              if (!dbconnedge.waitForConnection()) {
                Util.prta(" ****** Could not connect to edge database at " + Util.getProperty("DBServer"));
                //System.exit(1);
              }
            }
          }
        }
        DBConnectionThread dbconnanss = new DBConnectionThread(Util.getProperty("DBServer"), "user", "anss",
                false, false, "anss", Util.getOutput());
        if (!dbconnanss.waitForConnection()) {
          if (!dbconnanss.waitForConnection()) {
            if (!dbconnanss.waitForConnection()) {
              if (!dbconnanss.waitForConnection()) {
                Util.prta(" ****** Could not connect to anss database at " + Util.getProperty("DBServer"));
                //System.exit(1);
              }
            }
          }
        }
      } catch (InstantiationException e) {
        Util.prta(e + "SQLException making ro connection to db " + Util.getProperty("DBServer"));
        e.printStackTrace();
        //System.exit(1);
      }
    }
    initComponents();
    this.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
    setup();
    //table.setFont(new Font("Monospaced",Font.PLAIN, 12));
    display = new Display();
    stations = new TreeSet();
    if (snw == null && !noDB) {
      snw = new SNWGroupEventProcess(false, null);
      channels = snw.getChannelSSR();
    }
    if (channels == null) {
      channels = new StatusSocketReader(ChannelStatus.class, Util.DBURL2IP(Util.getProperty("StatusServer")),
              AnssPorts.CHANNEL_SERVER_PORT);
    }
    ((ChannelJTable) table).getPopup().setLocation((int) Double.parseDouble(Util.getProperty("popupX")),
            (int) Double.parseDouble(Util.getProperty("popupY")));
    setTitle("ChannelDisplay " + VERSION + " - " + Util.getProperty("StatusServer"));
    df = new DecimalFormat("##0.0");  //df.setMinimumIntegerDigits(3); df.setMinimumFractionDigits(1);
    dfi5 = new DecimalFormat("####0"); //dfi5.setMinimumIntegerDigits(5);
    dfi6 = new DecimalFormat("#####0");//dfi6.setMinimumIntegerDigits(6);
    tags = new StringBuffer[2000];
    cs = new ChannelStatus[2000];
    colors = new Color[tags.length];
    for (int i = 0; i < 2000; i++) {
      tags[i] = new StringBuffer(40);
    }
    sb = new StringBuffer(20000);
    tl = new TimerLoop();
    ready = true;
    theChannelDisplay = (ChannelDisplay) this;
    soundOff.setVisible(false);
    // This code senses resizes and sets a update to occur ASAP
    this.addComponentListener(new java.awt.event.ComponentAdapter() {
      @Override
      public void componentResized(ComponentEvent e) {
        doImmediateUpdate();
        //Util.prta("JFrame was resized ");
      }
    });
  }

  private void setup() {
    stationComponentComparator = new StationComponentComparator();
    sourceStationComparator = new SourceStationComponentComparator();
    latencyComparator = new LatencyComparator();
    lastDataComparator = new LastDataComparator();
    nodeProcessComparator = new NodeProcessComparator();
    netStationLocationComparator = new NetStationLocationChannelComparator();
    sort.addItem("Net-Stat-Comp");
    sort.addItem("Stat-Comp");
    sort.addItem("Src-Stat-Comp");
    sort.addItem("Node-Proc-Comp");
    sort.addItem("Latency");
    sort.addItem("LastData");

    select.addItem("All");
    select.addItem("[BEHSCD]HZ Only");
    select.addItem("BHZ Only");
    select.addItem("HHZ Only");
    select.addItem("Z Only");
    select.addItem("L1Z");
    select.addItem("Infra|Hydro");
    String scomp = Util.getProperty("Comp");
    select.setSelectedIndex(1);
    select.setSelectedIndex(1);
    if (scomp != null) {
      for (int i = 0; i < select.getItemCount(); i++) {
        if (((String) select.getItemAt(i)).equals(scomp)) {
          select.setSelectedIndex(i);
          break;
        }
      }
    }
    String snd = Util.getProperty("sound");
    if (snd == null) {
      snd = "NONE";
    }
    for (int i = 0; i < sound.getItemCount(); i++) {
      if (((String) sound.getItemAt(i)).equals(snd)) {
        sound.setSelectedIndex(i);
        break;
      }
    }
    refresh.addItem("3 secs");
    refresh.addItem("10 secs");
    refresh.addItem("30 secs");
    refresh.addItem("60 secs");
    refresh.addItem("Never");
    refresh.setSelectedIndex(1);

    sourceLearn = new LearningJComboBox(source);
    networkLearn = new LearningJComboBox(network);
    cpuLearn = new LearningJComboBox(cpu);

    try {
      SNWGroupPanel.setJComboBoxToID(snwGroup, Integer.parseInt(Util.getProperty("SNWGroupID")));
      sort.setSelectedIndex(Integer.parseInt(Util.getProperty("Sort")));
      refresh.setSelectedIndex(Integer.parseInt(Util.getProperty("Refresh")));
      if (Util.getProperty("AlertMode").equals("true")) {
        alertMode.setSelected(true);
      }
      //if(Util.getProperty("SoundOff").equals("true")) soundOff.setSelected(true);
    } catch (NumberFormatException e) {
      Util.prt("Got runtime exception setting from properies file");
      e.printStackTrace();
    }
  }

  private void playSelectedSound() {
    playSound(sound.getSelectedItem().toString());
  }

  private void playSound(String soundName) {
    if (soundName.startsWith("NONE")) {
      return;
    }
    SimpleAudioPlayer tmp = new SimpleAudioPlayer(getClass().getResource("/tones/mono/" + soundName + ".wav"));

  }

  public void setDebug(boolean b) {
    dbg = b;
  }

  /**
   * This method is called from within the constructor to initialize the form. WARNING: Do NOT
   * modify this code. The content of this method is always regenerated by the Form Editor.
   */

  // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
  private void initComponents() {

    jPanel1 = new javax.swing.JPanel();
    alertMode = new javax.swing.JRadioButton();
    sortLab = new javax.swing.JLabel();
    sort = new javax.swing.JComboBox();
    compLab = new javax.swing.JLabel();
    select = new javax.swing.JComboBox();
    labSNWGroup = new javax.swing.JLabel();
    snwGroup = SNWGroupPanel.getJComboBox();
    snwGroup.insertItemAt(new SNWGroup(0," None", "None","None", 0, 0),0);
    snwGroup.setMaximumRowCount(40);
    snwGroup.setSelectedIndex(SNWGroupPanel.getJComboBoxIndex(14));
    Util.prt("Set selected to index="+SNWGroupPanel.getJComboBoxIndex(14));
    networkLab = new javax.swing.JLabel();
    network = new javax.swing.JComboBox();
    sourceLab = new javax.swing.JLabel();
    source = new javax.swing.JComboBox();
    cpuLab = new javax.swing.JLabel();
    cpu = new javax.swing.JComboBox();
    refreshLab = new javax.swing.JLabel();
    refresh = new javax.swing.JComboBox();
    jLabel1 = new javax.swing.JLabel();
    sound = new javax.swing.JComboBox();
    server = new javax.swing.JTextField();
    server.setText(Util.getProperty("StatusServer")+","+Util.getProperty("DBServer")+","+Util.getProperty("StatusDBServer"));
    table = new ChannelJTable("StatnCmpNtLc k/M 2nd Lrcv-m Ltny-s",4,250);
    soundOff = new javax.swing.JRadioButton();

    addWindowListener(new java.awt.event.WindowAdapter() {
      public void windowClosing(java.awt.event.WindowEvent evt) {
        exitForm(evt);
      }
    });

    jPanel1.setPreferredSize(new java.awt.Dimension(1046, 810));

    alertMode.setText("Alert Mode");
    alertMode.setMargin(new java.awt.Insets(0, 0, 0, 0));
    alertMode.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        alertModeActionPerformed(evt);
      }
    });
    jPanel1.add(alertMode);

    sortLab.setText("Sort :");
    jPanel1.add(sortLab);

    sort.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        sortActionPerformed(evt);
      }
    });
    jPanel1.add(sort);

    compLab.setText("Comp:");
    jPanel1.add(compLab);

    select.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        selectActionPerformed(evt);
      }
    });
    jPanel1.add(select);

    labSNWGroup.setText("SNWGroup");
    jPanel1.add(labSNWGroup);

    snwGroup.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        snwGroupActionPerformed(evt);
      }
    });
    jPanel1.add(snwGroup);

    networkLab.setText("Net:");
    jPanel1.add(networkLab);

    network.setMaximumRowCount(25);
    network.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        networkActionPerformed(evt);
      }
    });
    jPanel1.add(network);

    sourceLab.setText("Source:");
    jPanel1.add(sourceLab);

    source.setMaximumRowCount(25);
    source.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        sourceActionPerformed(evt);
      }
    });
    jPanel1.add(source);

    cpuLab.setText("CPU:");
    jPanel1.add(cpuLab);

    cpu.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        cpuActionPerformed(evt);
      }
    });
    jPanel1.add(cpu);

    refreshLab.setText("Refresh:");
    jPanel1.add(refreshLab);

    refresh.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        refreshActionPerformed(evt);
      }
    });
    jPanel1.add(refresh);

    jLabel1.setText("Sound:");
    jPanel1.add(jLabel1);

    sound.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "NONE", "chord1", "chord2", "chord3", "doubletone1", "doubletone2", "doubletone3", "sinister1", "sinister2", "sinister3", "tone1", "tone2", "tone3" }));
    sound.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        soundActionPerformed(evt);
      }
    });
    jPanel1.add(sound);

    server.setColumns(50);
    server.setFont(new java.awt.Font("Courier New", 0, 10)); // NOI18N
    server.setToolTipText("<html>\nThis is the 'StatusServer','DBServer','StatusDBServer' properties in that order separated by commas.  To change edit this field to something like:\n<br>\nlocalhost,localhost/3306:edge:mysql:edge,localhost:status:mysql:status");
    server.setMinimumSize(new java.awt.Dimension(110, 24));
    server.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        serverActionPerformed(evt);
      }
    });
    jPanel1.add(server);
    jPanel1.add(table);

    soundOff.setText("Sound Off");
    soundOff.setMargin(new java.awt.Insets(0, 0, 0, 0));
    soundOff.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        soundOffActionPerformed(evt);
      }
    });
    jPanel1.add(soundOff);

    getContentPane().add(jPanel1, java.awt.BorderLayout.NORTH);

    pack();
  }// </editor-fold>//GEN-END:initComponents

  private void alertModeActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_alertModeActionPerformed
    doAlertMode();
    if (alertMode.isSelected()) {
      display.doUpdate();
    }

  }//GEN-LAST:event_alertModeActionPerformed

  public void doAlertMode() {
    boolean flg = false;
    if (alertMode.isSelected()) {
      labSNWGroup.setVisible(flg);
      networkLab.setVisible(flg);
      networkLearn.setVisible(flg);
      snwGroup.setVisible(flg);
      sort.setVisible(flg);
      sortLab.setVisible(flg);
      sourceLab.setVisible(flg);
      compLab.setVisible(flg);
      select.setVisible(flg);
      cpu.setVisible(flg);
      cpuLab.setVisible(flg);
      cpuLearn.setVisible(flg);
      sourceLearn.setVisible(flg);
      this.setSize(300, 250);
      //jPanel1.setSize(new java.awt.Dimension(300,250));
      ((ChannelJTable) table).setSmall();
    } else {
      saveProperties();     // Save change of alertMode because it may not be right
      terminate();          // kill this display window, main will restart it as needed
    }
  }
  private void snwGroupActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_snwGroupActionPerformed
    if (ready) {
      doImmediateUpdate();
    }

  }//GEN-LAST:event_snwGroupActionPerformed

  private void sortActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_sortActionPerformed
  {//GEN-HEADEREND:event_sortActionPerformed
    // Add your handling code here:
    if (ready) {
      doImmediateUpdate();
    }
  }//GEN-LAST:event_sortActionPerformed

  private void refreshActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_refreshActionPerformed
  {//GEN-HEADEREND:event_refreshActionPerformed
    // Add your handling code here:
    if (ready) {
      doImmediateUpdate();
    }
  }//GEN-LAST:event_refreshActionPerformed

  private void sourceActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_sourceActionPerformed
  {//GEN-HEADEREND:event_sourceActionPerformed
    // Add your handling code here:
    if (ready) {
      doImmediateUpdate();
    }
  }//GEN-LAST:event_sourceActionPerformed

  private void cpuActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_cpuActionPerformed
  {//GEN-HEADEREND:event_cpuActionPerformed
    // Add your handling code here:
    if (ready) {
      doImmediateUpdate();
    }
  }//GEN-LAST:event_cpuActionPerformed

  private void networkActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_networkActionPerformed
  {//GEN-HEADEREND:event_networkActionPerformed
    // Add your handling code here:
    if (ready) {
      doImmediateUpdate();
    }
  }//GEN-LAST:event_networkActionPerformed

  private void selectActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_selectActionPerformed
  {//GEN-HEADEREND:event_selectActionPerformed
    // Add your handling code here:
    if (ready) {
      doImmediateUpdate();
    }
  }//GEN-LAST:event_selectActionPerformed

  /**
   * Exit the Application
   */
  private void exitForm(java.awt.event.WindowEvent evt)
  {//GEN-FIRST:event_exitForm
    saveProperties();
    terminate();
    System.exit(0);
  }//GEN-LAST:event_exitForm

private void serverActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_serverActionPerformed
  // The server field should be the StatusServer(for UdpChannel), the DBServer (for edge access) and StatusDBServer (for status DB)
  String[] servers = server.getText().split(",");
  if (servers.length >= 2) {
    if (!servers[1].contains(":")) {
      String addon = "/3306:edge:mysql:edge";
      if (Util.getProperty("DBServer") != null) {
        if (Util.getProperty("DBServer").indexOf("/") > 0) {
          addon = Util.getProperty("DBServer").substring(Util.getProperty("DBServer").indexOf("/"));
        } else if (Util.getProperty("DBServer").indexOf(":") > 0) {
          addon = Util.getProperty("DBServer").substring(Util.getProperty("DBServer").indexOf(":"));
        }
      }
      if (!servers[1].contains("/") && !servers[1].contains(":")) {
        servers[1] = servers[1].trim() + addon;
      }
    }
  }
  if (servers.length >= 3) {
    if (!servers[2].contains(":")) {
      String addon = "/3306:status:mysql:status";
      if (Util.getProperty("StatusDBServer") != null) {
        if (Util.getProperty("StatusDBServer").indexOf("/") > 0) {
          addon = Util.getProperty("StatusDBServer").substring(Util.getProperty("StatusDBServer").indexOf("/"));
        } else if (Util.getProperty("StatusDBServer").indexOf(":") > 0) {
          addon = Util.getProperty("StatusDBServer").substring(Util.getProperty("StatusDBServer").indexOf(":"));
        }
      }
      if (!servers[2].contains("/") && !servers[1].contains(":")) {
        servers[2] = servers[2].trim() + addon;
      }
    }
  }

  Util.prt("Changing server to " + server.getText());
  Util.setProperty("StatusServer", servers[0]);
  if (servers.length >= 2) {
    Util.setProperty("DBServer", servers[1]);
  }
  if (servers.length >= 3) {
    Util.setProperty("StatusDBServer", servers[2]);
  }
  server.setText(Util.getProperty("StatusServer") + "," + Util.getProperty("DBServer") + "," + Util.getProperty("StatusDBServer"));
  JOptionPane.showMessageDialog(this, "You have changed servers.  ChannelDisplay will now exit and save this new configuration.  Please restart.",
          "Restart Warning", JOptionPane.PLAIN_MESSAGE);
  // All of this logic does not work because channels in msgs do not have right groups
  // need to figure out- until then, restart.
  if (snw != null) {
    snw.terminate();
  }
  SNWGroupEventProcess.clearMySQL();
  exitForm(null); // This is a terminate.
  snw = new SNWGroupEventProcess(false, null);
  channels = snw.getChannelSSR();
  setTitle("Channels - " + Util.getProperty("StatusServer"));
  SNWGroupPanel.reload();
  SNWGroupPanel.getJComboBox(snwGroup);
  snwGroup.insertItemAt(new SNWGroup(0, " None", "None", "None", 0, 0), 0);
  snwGroup.setMaximumRowCount(40);
  if (snwGroup.getItemCount() < 15) {
    snwGroup.setSelectedIndex(1);
  } else {
    snwGroup.setSelectedIndex(SNWGroupPanel.getJComboBoxIndex(14));
  }
  SNWGroupPanel.setJComboBoxToID(snwGroup, Integer.parseInt(Util.getProperty("SNWGroupID")));
  ((ChannelJTable) table).close();
  jPanel1.remove(table);
  table = new ChannelJTable("StatnCmpNtLc k/M 2nd Lrcv-m Ltny-s", 4, 250);
  jPanel1.add(table);
  saveProperties();
  System.exit(1);
}//GEN-LAST:event_serverActionPerformed

  private void soundOffActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_soundOffActionPerformed
    if (!soundOff.isSelected()) {
      URL url = getClass().getResource("/tones/mono/chord3.wav");
      SimpleAudioPlayer tmp = new SimpleAudioPlayer(url);
    }
  }//GEN-LAST:event_soundOffActionPerformed

  private void soundActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_soundActionPerformed
    if (ready) {
      playSelectedSound();
    }
  }//GEN-LAST:event_soundActionPerformed
  public final class TimerLoop extends Thread {

    long interval;

    public long getInterval() {
      return interval;
    }

    public TimerLoop() {

      start();
    }

    @Override
    @SuppressWarnings("empty-statement")
    public void run() {
      int loop = 0;
      long keepFresh = System.currentTimeMillis();
      try {
        sleep((long) 2000);
      } catch (InterruptedException e) {
      };
      while (true) {
        if (terminate) {
          break;
        }
        //text.setText(""+loop); 
        if (dbg) {
          Util.prta("Loop=" + loop);
        }
        loop++;
        try {
          if (dbg) {
            Util.prta("CD: timer loop cause update");
          }
          if (ready) {
            display.doUpdate();
          }

          // rebuild the dynamic (LearningJComboBox) and disable any triggered updates
          // Use the refresh JCOmboBox to decide the interval and sleep that long
          interval = 9000;
          if (refresh.getSelectedItem().equals("10 secs")) {
            interval = 10000;
          } else if (refresh.getSelectedItem().equals("3 secs")) {
            interval = 3000;
          } else if (refresh.getSelectedItem().equals("30 secs")) {
            interval = 30000;
          } else if (refresh.getSelectedItem().equals("60 secs")) {
            interval = 60000;
          } else if (refresh.getSelectedItem().equals("Never")) {
            interval = 3000000;
          }
          if (dbg) {
            Util.prta("CD: Time loop sleep=" + interval);
          }
          for (int i = 0; i < interval / 200; i++) {
            sleep(200);
            if (immediateUpdate) {
              immediateUpdate = false;
              break;
            }
            ready = false;
            networkLearn.buildJComboBox();
            sourceLearn.buildJComboBox();
            cpuLearn.buildJComboBox();
            ready = true;
            if (terminate) {
              break;
            }
            if (System.currentTimeMillis() - keepFresh > 600000) {
              keepFresh = System.currentTimeMillis();
            }
          }
        } catch (InterruptedException e) {
          Util.prta("CD: TimerLoop interrupted");
        } catch (RuntimeException e) {
          if (e.toString().contains("violates its general contract")) {
            Util.prta("CD: comparison runtime on contract - ignore");
          } else {
            Util.prta("CD: timer loop runtime e=" + e);
            e.printStackTrace();
          }
          try {
            sleep(1000);
          } catch (InterruptedException e2) {
          }
        }
      }
      Util.prt("TimerLoop has exited");
    }

  }

  class Display {

    long msLastMinute;
    long lastDBrefresh;

    public void close() {
    }
    Object msgs[] = new Object[2000];

    public Display() {
      msLastMinute = Util.getTimeInMillis();
      lastDBrefresh = msLastMinute;
    }

    private synchronized void doUpdate() {
      long now = System.currentTimeMillis();
      if (now - lastDBrefresh > 3600000) {
        lastDBrefresh = now;
        Util.prt(channels.toString());
      }
      if (updateNumber == 0 && alertMode.isSelected()) {
        doAlertMode();
      }
      updateNumber++;
      if (!ready) {
        Util.prta("******* Attempt to recurse in display.doUpdate!");
        return;
      }
      ready = false;                // Disable uses of me until I am done (not reentrant)
      ChannelStatus ps, ps2, ps3;

      int nmsgs;
      if (dbg) {
        Util.prta("CDupd: Update start " + channels);
      }
      if (channels != null) {
        nmsgs = channels.length();
        if (nmsgs > msgs.length - 10) {
          msgs = new Object[nmsgs * 2];
        }
        channels.getObjects(msgs);
      } else {
        return;
      }
      if (dbg) {
        Util.prta("CDupd: nmsg=" + nmsgs);
      }
      if (nmsgs > 0) {
        now = Util.getTimeInMillis();
        if (Math.abs(now - msLastMinute) > 59900) {
          totalbytes = 0;
          if (dbg) {
            Util.prta("Minute update " + now + " " + (now - msLastMinute));
          }
          for (int i = 0; i < nmsgs; i++) {
            ps = (ChannelStatus) msgs[i];
            //Util.prt(i+" "+ps.getKey()+" nb="+ps.getNbytes()+" last="+ps.getLastNbytes());
            ps.updateLastNbytes();
            totalbytes += ps.getNbytesChange();
          }
          msLastMinute = now;
        }
        if (tags == null || tags.length < msgs.length + 30) {
          //Util.prt("CDupd: Expand tags array="+tags.length);
          while (tags.length < msgs.length + 30) {
            int l = 0;
            if (tags != null) {
              l = tags.length;
            }
            tags = new StringBuffer[l + 1000];
            cs = new ChannelStatus[l + 1000];
            colors = new Color[l + 1000];
            for (int i = 0; i < l + 1000; i++) {
              tags[i] = new StringBuffer(40);
            }
          }
        }
        for (StringBuffer tag2 : tags) {
          if (tag2.length() > 0) {
            tag2.delete(0, tag2.length());
          }
        }

        sourceChanges = false;
        nodeChanges = false;
        lastSource = " ";
        lastCpu = " ";
        lastProcess = " ";
        if (dbg) {
          Util.prta("CDupd: Start Sort " + sort.getSelectedItem());
        }
        if (sort.getSelectedItem().equals("Net-Stat-Comp")) {
          Arrays.sort(msgs, 0, nmsgs, netStationLocationComparator);
        } else if (sort.getSelectedItem().equals("Stat-Comp")) {
          Arrays.sort(msgs, 0, nmsgs, stationComponentComparator);
        } else if (sort.getSelectedItem().equals("Latency")) {
          Arrays.sort(msgs, 0, nmsgs, latencyComparator);
        } else if (sort.getSelectedItem().equals("LastData")) {
          Arrays.sort(msgs, 0, nmsgs, lastDataComparator);  // Throws violates contract - I think the msgs changed underneath 
        } else if (sort.getSelectedItem().equals("Src-Stat-Comp")) {
          Arrays.sort(msgs, 0, nmsgs, sourceStationComparator);
          sourceChanges = true;
        } else if (sort.getSelectedItem().equals("Node-Proc-Comp")) {
          Arrays.sort(msgs, 0, nmsgs, nodeProcessComparator);
          nodeChanges = true;
        } else {
          Util.prta("CDupd: Unknown sort order!=" + sort.getSelectedItem());
        }

        // See if any of the selections are set
        boolean bhz = false;
        boolean bshz = false;
        boolean hhz = false;
        boolean infra = false;
        boolean l1z = false;
        if (select.getSelectedItem().equals("[BEHSCD]HZ Only")) {
          bshz = true;
        }
        if (select.getSelectedItem().equals("BHZ Only")) {
          bhz = true;
        }
        if (select.getSelectedItem().equals("HHZ Only")) {
          hhz = true;
        }
        if (select.getSelectedItem().equals("L1Z")) {
          l1z = true;
        }
        if (select.getSelectedItem().equals("Infra|Hydro")) {
          infra = true;
        }
        boolean zonly;
        zonly = select.getSelectedItem().equals("Z Only");

        // Run each network and source through the learning box to see if anything new
        if (dbg) {
          Util.prta("CDupd: Update boxes n=" + nmsgs);
        }
        for (int i = 0; i < nmsgs; i++) {
          networkLearn.learn(((ChannelStatus) msgs[i]).getKey().substring(0, 2));
          sourceLearn.learn(((ChannelStatus) msgs[i]).getSource());
          cpuLearn.learn(((ChannelStatus) msgs[i]).getCpu());
        }

        // FOr "selects" set the flag and the selected value
        boolean sourceFlag = true;
        String sourceMatch;
        synchronized (source) {
          sourceMatch = (String) source.getSelectedItem();
          if (source.getSelectedItem().equals(" None")) {
            sourceFlag = false;
          }
        }

        boolean networkFlag = true;
        String networkMatch;
        synchronized (network) {
          networkMatch = (String) network.getSelectedItem();
          if (network.getSelectedItem().equals(" None")) {
            networkFlag = false;
          }
        }

        String cpuMatch;
        boolean cpuFlag = true;
        synchronized (cpu) {
          cpuMatch = (String) cpu.getSelectedItem();
          if (cpu.getSelectedItem().equals(" None")) {
            cpuFlag = false;
          }
        }
        boolean snwGroupFlag = true;
        String snwGroupMatch = "";
        long snwmask = 0;
        synchronized (snwGroup) {
          if (snwGroup.getSelectedIndex() != -1) {
            snwGroupMatch = (String) snwGroup.getSelectedItem().toString();
            if (snwGroup.getSelectedItem().toString().equals(" None")) {
              snwGroupFlag = false;
            } else {
              snwmask = ((SNWGroup) snwGroup.getSelectedItem()).getMask();
            }
          } else {
            snwGroupFlag = false;
          }
        }

        if (dbg) {
          Util.prta("CDupd: src=" + sourceFlag + " " + sourceMatch
                  + " net=" + networkFlag + " " + networkMatch + " cpu=" + cpuFlag + " " + cpuMatch
                  + " snwgrp=" + snwGroupFlag + " " + snwGroupMatch + " msk=" + Util.toHex(snwmask));
        }

        // The tags are formated lines which are going to the screen under current conditions
        if (dbg) {
          Util.prta("CDupd: Format tags");
        }
        tag = 0;                // Count the display tags;
        heads = 0;              // Number of headers displayed
        nevers = 0;             // Number of "purples" displayed
        ndisabled = 0;
        nyellows = 0;           // Number of yellowed displayed
        nreds = 0;
        int nchan = 0;
        nbyteTotal = 0;
        lastKey = "            ";
        Color bk = UC.white;
        lastColor = UC.white;
        nsecond = 0;
        ChannelStatus lastStation = null;
        for (int i = 0; i < nmsgs; i++) {
          if (msgs[i] != null && !alertMode.isSelected()) {
            nchan++;
            ps = (ChannelStatus) msgs[i];
            if (ps.getNbytes() == 0 && ps.getNsamp() == -1) ; 
            else if (!stations.contains(ps.getKey().substring(0, 7))) {
              stations.add(ps.getKey().substring(0, 7));
              
            }
            
            // Search HNZ only stations
            if(ps.getKey().startsWith("GSKS22")) 
              Util.prt("Got KS22 ps="+ps);
            if(lastStation != null) {

              if(!lastStation.getKey().substring(0,7).equals(ps.getKey().substring(0,7))) {               
                if(lastStation.getKey().substring(7,10).equals("HNZ")) {
                  if(bshz && (!sourceFlag || lastStation.getSource().equals(sourceMatch))
                          && (!cpuFlag || lastStation.getCpu().equals(cpuMatch))
                          && (!snwGroupFlag || (ps.snwGroupMask() & snwmask) != 0)
                          && (!networkFlag || lastStation.getKey().substring(0,2).equals(networkMatch))) {
                    add(lastStation);
                  }
                }
                lastStation = null;
              } 
              else if(lastStation.getKey().substring(7,9).equals("HN") && 
                      ps.getKey().substring(7,9).equals("HN")) {    // If its still HN, update to get h
                lastStation = ps;
              }
            }
            else {
              lastStation = ps;
            }
 
            if (ps.getKey().compareTo("ZZZ") < 0
                    && ps.getKey().indexOf('%') < 0
                    && (!bhz || ps.getKey().substring(7, 10).equals("BHZ"))
                    && (!l1z || ps.getKey().substring(7, 10).equals("L1Z"))
                    && (!hhz || ps.getKey().substring(7, 10).equals("HHZ"))
                    && (!infra || ps.getKey().substring(7, 10).equals("EDH") || ps.getKey().substring(7, 9).equals("BD")
                    || ps.getKey().substring(7, 10).equals("HDF"))
                    && (!bshz || ps.getKey().substring(7, 10).equals("BHZ") || ps.getKey().substring(7, 10).equals("GPZ")
                    || ps.getKey().substring(7, 10).equals("SHZ") || ps.getKey().substring(7, 10).equals("EHZ")
                    || ps.getKey().substring(7, 10).equals("HHZ") || ps.getKey().substring(7, 10).equals("CHZ")
                    || ps.getKey().substring(7, 10).equals("DHZ"))
                    && (!zonly || ps.getKey().substring(9, 10).equals("Z"))
                    && (!sourceFlag || ps.getSource().equals(sourceMatch))
                    && (!cpuFlag || ps.getCpu().equals(cpuMatch))
                    && (!snwGroupFlag || (ps.snwGroupMask() & snwmask) != 0)
                    && (!networkFlag || ps.getKey().substring(0, 2).equals(networkMatch))) {
              add(ps);
            }

          }
        }
        int ndisplay = tag;
        ps = (ChannelStatus) msgs[0];
        if (!ChannelDisplay.isNoDB()) {
          ArrayList<SNWGroup> groups = snw.getGroups();

          if (groups != null) {
            int[] bad = snw.getNBadStations();
            int[] yellow = snw.getNWarnStations();
            int[] totstations = snw.getTotalStations();
            boolean[] isWarn = snw.getIsWarn();
            boolean[] isBad = snw.getIsBad();
            if (forceBad) {
              isBad[1] = true;      // force something to be bad for DEBUG
            }
            tags[tag].append("   SNWGroup            %bad%wrn #ch");
            colors[tag] = UC.white;
            cs[tag] = ps;
            tag++;

            // Data gathering done, Decide about groups.
            for (SNWGroup group : groups) {
              if (group.getSNWGroup().contains("_Availa") || group.getSNWGroup().contains("_unused")) {
                continue;
              }
              if (group.isEventable()) {
                int id = group.getID() - 1;
                if (!alertMode.isSelected() || isBad[id] || isWarn[id]) {
                  if (forceBad && group.getSNWGroup().equals("Q330")) {
                    tags[tag].append((group.getSNWGroup() + " DEBUG MODE!!!          ").substring(0, 23));
                  } else {
                    tags[tag].append((group.getSNWGroupAnalyst() + "                     ").substring(0, 23));
                  }
                  int pct = (int) ((double) bad[id] / ((double) totstations[id]) * 100. + 0.5);
                  tags[tag].append(Util.leftPad((isBad[id] ? "*" : " ") + pct, 4));
                  pct = (int) (((double) bad[id] + yellow[id]) / ((double) totstations[id]) * 100. + 0.5);
                  tags[tag].append(Util.leftPad((isWarn[id] ? "*" : " ") + pct, 4)).
                          append(Util.leftPad("" + totstations[id], 4));
                  colors[tag] = UC.white;
                  if (isWarn[id]) {
                    colors[tag] = UC.yellow;
                    if (bk == UC.white) {
                      bk = UC.yellow;
                    }
                  }
                  if (isBad[id]) {
                    colors[tag] = UC.red;
                    if (bk == UC.white || bk == UC.yellow) {
                      bk = UC.red;
                    }
                  }
                  cs[tag] = ps;
                  //Util.prt("out group="+groups.get(i)+" warn="+isWarn[id]+" bad="+isBad[id]+" tag2="+tags[tag2]+"| i="+i+" id="+id+" tag2="+tag2);
                  if (!alertMode.isSelected() || group.isAnalystGroup()) {
                    tag++;
                  }
                }
              }
            }
          }
        }
        if (!alertMode.isSelected()) {
          tags[tag].append("k/M is KBytes per minute");
          colors[tag] = UC.white;
          cs[tag] = ps;
          tag++;
          tags[tag].append("2nd is age of catchup stream in hr");
          cs[tag] = ps;
          colors[tag] = UC.white;
          tag++;
          tags[tag].append("Lrcv-m is last packet recvd in min");
          cs[tag] = ps;
          colors[tag] = UC.white;
          tag++;
          tags[tag].append("Ltny-s is latency of last in secs");
          cs[tag] = ps;
          colors[tag] = UC.white;
          tag++;
          tags[tag].append(nyellows).append(" Lrcv >  3 m or Ltny >  180 s");
          cs[tag] = ps;
          colors[tag] = UC.yellow;
          tag++;
          tags[tag].append(nreds).append(" Lrcv > 60 m or Ltny > 1000 s");
          cs[tag] = ps;
          colors[tag] = UC.red;
          tag++;
          tags[tag].append("IC & BUD have different rules");
          cs[tag] = ps;
          colors[tag] = UC.look;
          tag++;
          tags[tag].append(ndisplay - heads).append(" Channels displayed 2nd=").append(nsecond);

          cs[tag] = ps;
          colors[tag] = UC.white;
          tag++;
          tags[tag].append(nevers).append(" No data ever rcvd");
          cs[tag] = ps;
          colors[tag] = UC.purple;
          tag++;
          tags[tag].append(ndisabled).append(" Station Monitor disabled");
          cs[tag] = ps;
          colors[tag] = Color.gray;
          tag++;
          tags[tag].append(nchan - nevers).append(" Total Channels received.");
          cs[tag] = ps;
          colors[tag] = UC.white;
          tag++;
          tags[tag].append(stations.size()).append(" Distinct Stations ").append(network.getItemCount() - 2).append(" nets.");
          cs[tag] = ps;
          colors[tag] = UC.white;
          tag++;
          tags[tag].append(dfi6.format(nbyteTotal / 60.)).append(" bytes/s for displayed curr");
          cs[tag] = ps;
          colors[tag] = UC.white;
          tag++;
        }

        tags[tag].append(Util.ascdate().substring(5)).append(" ").append(Util.asctime().substring(0, 8)).
                append(" UTC ").append(beepInterval).append(" ").append(beeps);
        // Play the sound every hour - why is this a good idea?
        /*if(!Util.asctime().substring(0,2).equals(lastHour) && !soundOff.isSelected()) {
          lastHour = Util.asctime().substring(0,2);
          playSelectedSound();
        }*/
        cs[tag] = ps;
        colors[tag] = UC.white;
        tag++;
        tags[tag].append("   ");
        cs[tag] = ps;
        colors[tag] = UC.white;
        tag++;

        /*Util.prta("nyel="+nyellows+" red="+nreds+" purple="+nevers+" nmsgs="+nmsgs+" nch="+nchan+
          " ndisp="+ndisplay+" heads="+heads);*/
        if (updateNumber % 2 == 0) {
          if (bk != UC.white) {
            beeps++;
            URL url = null;
            if (beeps % beepInterval == 1 && !soundOff.isSelected()) {
              //if(bk == UC.yellow) url = getClass().getResource("/tones/mono/tone2.wav");
              //if(bk == UC.red) url = getClass().getResource("/tones/mono/sinister3.wav");
              //SimpleAudioPlayer tmp = new SimpleAudioPlayer(url);
              if (bk == UC.yellow) {
                playSelectedSound();
              }
              if (bk == UC.red) {
                playSelectedSound();
                Util.sleep(1000);
                playSelectedSound();
              }
              beepInterval *= 2;
              if (beepInterval * tl.getInterval() > 1800000) {
                beepInterval = (int) (1800000L / tl.getInterval());
              }
            }
          } else {
            beeps = 1;
            beepInterval = 6;
          }
          jPanel1.setBackground(bk);
        } else {
          jPanel1.setBackground(UC.white);
        }
        ((ChannelJTable) table).refreshTable(tags, colors, (StatusInfo[]) cs, tag);

      }

      ready = true;
  }
  private boolean sourceChanges, nodeChanges;
  private String lastSource, lastCpu,lastProcess, lastKey;
  private Color lastColor;
  private int nbyteTotal, nsecond,nevers,ndisabled,nyellows,nreds;
  private int heads;
  private int tag;
    
  private void add(ChannelStatus ps) {
      // Here we do tags of changes in various fields
      if (sourceChanges && !ps.getSource().equals(lastSource)) {
        colors[tag] = UC.white;
        tags[tag++].append("     ").append(ps.getSource());
        lastSource = ps.getSource();
        heads++;
      }
      if (nodeChanges && (!ps.getCpu().equals(lastCpu) || !ps.getProcess().equals(lastProcess))) {
        colors[tag] = UC.white;
        tags[tag++].append("  ").append(ps.getCpu()).append("-").append(ps.getProcess());
        lastCpu = ps.getCpu();
        lastProcess = ps.getProcess();
        heads++;
      }
      // Format up a line
      String key = ps.getKey();
      if (lastKey.substring(0, 7).equals(key.substring(0, 7))) {
        if (lastKey.substring(7, 9).equals("BH") && lastColor == UC.white
                && lastKey.substring(10).equals(key.substring(10))
                && (key.substring(7, 9).equals("HH") || key.substring(7, 9).equals("EH"))) {
          //continue;
          return;
        }
      }
      nbyteTotal += ps.getNbytesChange();
      //if(key.substring(0,10).equals("IUHKT  VHZ"))
      //  Util.prt("GRFO VHS latency="+ps.getLatency());
      colors[tag] = UC.white;
      tags[tag].append(key.substring(2, 10));
      tags[tag].append(" ");
      tags[tag].append(key.substring(0, 2));
      tags[tag].append(key.substring(10, 12));
      if (ps.getNbytesChange() >= 100000) {
        tags[tag].append(Util.leftPad(dfi6.format(ps.getNbytesChange() / 1000.), 4));
      } else {
        tags[tag].append(Util.leftPad(df.format(ps.getNbytesChange() / 1000.), 4));
      }
      if (ps.getOtherStreamLatency() >= 1444000) {
        tags[tag].append("***");
      } else if (ps.getOtherStreamLatency() >= 1440) {
        tags[tag].append(Util.leftPad("" + ((int) ps.getOtherStreamLatency() / 1440), 2)).append("d");
      } else if (ps.getOtherStreamLatency() > 600) {
        tags[tag].append(Util.leftPad("" + ps.getOtherStreamLatency() / 60, 3));
      } else if (ps.getOtherStreamLatency() > 60) {
        tags[tag].append(Util.leftPad(df.format(ps.getOtherStreamLatency() / 60.), 3));
      } else {
        tags[tag].append(" ").append(df.format(ps.getOtherStreamLatency() / 60.).substring(1, 3));
      }
      if (ps.getOtherStreamLatency() > 10) {
        nsecond++;
      }
      if (ps.getAge() > 9999.9) {
        tags[tag].append(Util.leftPad(df.format(ps.getAge() / 1440.), 6)).append("d");
      } else {
        tags[tag].append(Util.leftPad(df.format(ps.getAge()), 7));
      }
      if (ps.getLatency() > 86400.) {
        tags[tag].append(Util.leftPad(df.format(ps.getLatency() / 86400.), 6)).append("d");
      } else if (ps.getLatency() > 9999.9) {
        tags[tag].append(Util.leftPad(dfi6.format(ps.getLatency()), 7));
      } else {
        tags[tag].append(Util.leftPad(df.format(ps.getLatency()), 7));
      }
      //Util.prt("ps.getKey="+ps.getKey()+" network="+ps.getNetwork()+" age="+ps.getAge()+" lat="+ps.getLatency());

      if (ps.getNetwork().equals("IC")) {
        if (ps.getLatency() > 7200) {
          colors[tag] = UC.yellow;
        }
        if (ps.getAge() > 3.) {
          colors[tag] = UC.yellow;
        }
        if (ps.getLatency() > 8200) {
          colors[tag] = UC.red;
        }
        if (ps.getAge() > 60.) {
          colors[tag] = UC.red;
        }
      } else if ((ps.snwGroupMask() & (1L << 33)) != 0) {   // BUD Seedlink 
        if (ps.getLatency() > 1080) {
          colors[tag] = UC.yellow;
        }
        if (ps.getAge() > 20.) {
          colors[tag] = UC.yellow;
        }
        if (ps.getLatency() > 1500) {
          colors[tag] = UC.red;
        }
        if (ps.getAge() > 60.) {
          colors[tag] = UC.red;
        }
      } else {
        if (ps.getLatency() > 180) {
          colors[tag] = UC.yellow;
        }
        if (ps.getAge() > 3.) {
          colors[tag] = UC.yellow;
        }
        if (ps.getLatency() > 1000) {
          colors[tag] = UC.red;
        }
        if (ps.getAge() > 60.) {
          colors[tag] = UC.red;
        }
      }
      if (ps.getNbytes() == 0 && ps.getNsamp() == -1) {
        colors[tag] = UC.purple;
        nevers++;
      }
      if (!ChannelDisplay.isNoDB()) {
        if ((ps.snwGroupMask() & snw.getDisabledMask()) != 0) {
          colors[tag] = Color.gray;
          ndisabled++;
        }
      }
      cs[tag] = ps;
      if (colors[tag] == UC.yellow) {
        nyellows++;
      }
      if (colors[tag] == UC.red) {
        nreds++;
      }
      lastColor = colors[tag];
      lastKey = key;
      tag++;
    }
  }
  class ChannelDisplayShutdown extends Thread {

    public ChannelDisplayShutdown() {

    }

    @Override
    public void run() {
      Util.prta("ChannelDisplayShutdown");
      shuttingDown = true;
      Util.sleep(400);
      Util.prt(Util.getThreadsString());
    }
  }

  private void saveProperties() {
    if (getLocationOnScreen().getX() > -30000.) {
      Util.setProperty("StartX", "" + getLocationOnScreen().getX());
    }
    if (getLocationOnScreen().getY() > -30000.) {
      Util.setProperty("StartY", "" + getLocationOnScreen().getY());
    }
    Util.setProperty("Sort", "" + sort.getSelectedIndex());
    Util.setProperty("Refresh", "" + refresh.getSelectedIndex());
    Util.setProperty("Comp", select.getSelectedItem().toString());
    Util.setProperty("AlertMode", "" + alertMode.isSelected());
    Util.setProperty("SNWGroupID", "" + (snwGroup.getSelectedIndex() == -1 ? 0 : ((SNWGroup) snwGroup.getSelectedItem()).getID()));
    Util.setProperty("SoundOff", "" + soundOff.isSelected());
    Util.setProperty("popupX", "" + ((ChannelJTable) table).getPopup().getX());
    Util.setProperty("popupY", "" + ((ChannelJTable) table).getPopup().getY());
    Util.setProperty("sound", sound.getSelectedItem() + "");
    String parts[] = server.getText().split(",");
    if (parts.length == 3) {
      Util.setProperty("StatusServer", parts[0]);
      Util.setProperty("DBServer", parts[1]);
      Util.setProperty("StatusDBServer", parts[2]);
    }

    Util.prt("Save properties - DB=" + Util.getProperty("DBServer") + " status="
            + Util.getProperty("StatusServer") + " to " + Util.propfilename);
    Util.saveProperties();
  }

  /*public void play(String ur) {
    SimpleAudioPlayer player;
    URL url = getClass().getResource("/tones/mono/"+ur);
     Util.prt("Try /tones/mono/"+ur);
     if(url != null)
       player = new SimpleAudioPlayer(url);
  }*/
  /**
   * main test class
   *
   * @param args the command line arguments
   */
  public static void main(String args[]) {
    String[] tones = {"chord1", "chord2", "chord3", "doubletone1", "doubletone3", "sinister1",
      "sinister2", "sinister3", "tone1", "tone2", "tone3"};
    Util.init("chandisp.prop");
    if (Util.getProperty("NoDB") != null) {
      Util.setProperty("DBServer", "NoDB");
    }
    /*if (USGSPropertyFixer.checkProperties()) {
      Util.saveProperties();
    }*/
    Util.setModeGMT();
    Util.setProcess("ChannelDisplay");
    Util.prt("starting argc=" + args.length + " DBServer=" + Util.getProperty("DBServer"));
    Util.setNoInteractive(true);
    for (int i = 0; i < args.length; i++) {
      Util.prt("arg " + i + " is " + args[i]);
      if (args[i].equals("-p")) {
        Util.prt("loading properties " + args[i + 1]);
        Util.init(args[i + 1]);
        Util.prtProperties();
      }
    }
    ChannelDisplay cd = new ChannelDisplay();
    cd.setVisible(true);
    while (!shuttingDown) {
      if (args.length >= 1) {
        if (args[0].equals("-dbg")) {
          cd.setDebug(true);
        }
      }
      Util.sleep(300);
      if (shuttingDown) {
        break;
      }
      if (cd.isClosed() && !shuttingDown) {
        Util.prta("ChannelDisplay closed/terminated - likely transition from alert mode - restarting");
        Util.sleep(2000);       // let everthing shutdown and then reset
        DBConnectionThread.resetShuttingDown();
        cd = new ChannelDisplay();
        cd.setVisible(true);
      }
      if (cd.isClosed()) {
        break;
      }
      if (args.length >= 1) {
        if (args[0].equals("-play")) {
          for (;;) {
            for (String tone : tones) {
              cd.playSound(tone);
              Util.sleep(5000);
            }
          }
        }
      }

    }
    Util.prta("Main exiting.");
    Util.sleep(2000);
    Util.prta("Threads=" + Util.getThreadsString());
    System.exit(0);
  }


  // Variables declaration - do not modify//GEN-BEGIN:variables
  private javax.swing.JRadioButton alertMode;
  private javax.swing.JLabel compLab;
  private javax.swing.JComboBox cpu;
  private javax.swing.JLabel cpuLab;
  private javax.swing.JLabel jLabel1;
  private javax.swing.JPanel jPanel1;
  private javax.swing.JLabel labSNWGroup;
  private javax.swing.JComboBox network;
  private javax.swing.JLabel networkLab;
  private javax.swing.JComboBox refresh;
  private javax.swing.JLabel refreshLab;
  private javax.swing.JComboBox select;
  private javax.swing.JTextField server;
  private javax.swing.JComboBox snwGroup;
  private javax.swing.JComboBox sort;
  private javax.swing.JLabel sortLab;
  private javax.swing.JComboBox sound;
  private javax.swing.JRadioButton soundOff;
  private javax.swing.JComboBox source;
  private javax.swing.JLabel sourceLab;
  private javax.swing.JScrollPane table;
  // End of variables declaration//GEN-END:variables

}
