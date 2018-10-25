/*
 * Copyright 2010, United States Geological Survey or
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
/*
 * MetaGUI.java
 *
 * Created on July 7, 2006, 4:13 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package gov.usgs.anss.metadatagui;

import gov.usgs.anss.gui.ReportsPanel;
import gov.usgs.anss.gui.ReportPanel;
import java.awt.event.*;
import java.awt.*;
//import java.sql.*;
import gov.usgs.anss.util.*;
import gov.usgs.anss.db.DBConnectionThread;
import javax.swing.ToolTipManager;
//import gov.usgs.edge.snw.*;


/** 
 * This class implements the user interface for the tabs of the Inv application.
 * The constructor builds the JPanel and adds all the tabs for the submenus and 
 * creates the specialized JPanel for each tab.  To add a tab :
 * create a JPanel implementing some user interface
 * 2) 
 * @author  ketchum
 * @version 
 */
public final class MetaGUI extends javax.swing.JApplet 
{

  private final Login firstLogin;
  private User user;          // User information from logon
  private DBConnectionThread C;       // Connection to Database system JDBC
  private final String OS;
  static Frame aFrame;
  static MetaGUI theGUI;
  public MetaGUI() {
		OS = System.getProperty("os.name");
    theGUI=this;
    setName("Ketchum");
    Util.init();
    firstLogin = new Login(this);
    getContentPane().add (firstLogin);
    firstLogin.requestFocus();
//		UC.Look(getContentP);
    

//    initComponents ();
  }
  public void buildMenus(User userin, DBConnectionThread Cin) {
    user = userin;              // Handle to user stuff
    C = Cin;
		Util.prt("In build menus C="+C);
    getContentPane().setVisible(false);
    initComponents ();
//		UC.Look(this);
    getContentPane().remove(firstLogin);
//    getContentPane().setBackground(Color.green);
    getContentPane().setSize(UC.XSIZE, UC.YSIZE);

    getContentPane().setVisible(true);

    Util.prt("Build menus done!");

  }
  public void buildMenus() {
    getContentPane().removeAll();
    getContentPane().setVisible(false);
    initComponents();
    getContentPane().setVisible(true);
    
  }
  // declare all of the JPanels which implenent a pane in the interface.  All of these
  // should be given instance by the initComponents() routine.
  private javax.swing.JPanel login;
  private javax.swing.JPanel logout;
  private javax.swing.JPanel mainPanel;
    private javax.swing.JTabbedPane MainTabs;
     private javax.swing.JTabbedPane snwTabs;
//           private SNWStationPanel snwstation;
//           private SNWRulePanel rule;
//           private SNWGroupPanel group;
//           private SNWStationRegexpPanel snwregexp;
//           private SNWMultiStationPanel snwmult;

      private javax.swing.JTabbedPane metaTabs;
           private StationPanel station;
           private StationRegexpPanel stationRE;
           private ResponsePanel response;
           private ResponseRegexpPanel responseRE;
           private MetaChannelPanel metachannel;
           private MetaChannelRegexpPanel metachanRE;
      private javax.swing.JTabbedPane metaMasterTabs;
           private GroupsPanel groups;
           private LoadURLPanel loadURL;
           private OwnerPanel owner;
           private StationFlagsPanel stationFlags;
           private ChannelFlagsPanel channelFlags;
           private ResponseFlagsPanel responseFlags;
           

      private javax.swing.JTabbedPane config;
//          private RegexpPanel regexp;
//          private MultiChannelPanel channel;
//          private CommGroupPanel commgroup;
//          private SendtoPanel sendto;
//          private OperatorPanel operator;
//          private ProtocolPanel protocol;
          private FlagsPanel flags;
//          private LinksPanel links;
      private javax.swing.JTabbedPane reportsPane;
        private javax.swing.JPanel reportsPanel;
          private ReportsPanel reports;     // do reports
          private ReportPanel report;       // Define reports          

  /**
   * Create all of the specialized pannels that are needed and put them in the
   *appropriate tabs.  All the specializations are normally created, but the user's
   *privelges or Roles is used to decide if the tab is created and the JPanel attached
   *to it.
   */
  private void initComponents () {

    
    // Define the main panel and its tabbed paneTt
    Util.prta("EdgeConfig - create Main Tab");
    mainPanel = new javax.swing.JPanel (); UC.Look(mainPanel);
    MainTabs = new javax.swing.JTabbedPane (); UC.Look(MainTabs);
    getContentPane ().setLayout (new java.awt.FlowLayout ());
    mainPanel.setPreferredSize (new java.awt.Dimension(UC.XSIZE, UC.YSIZE));
    MainTabs.setPreferredSize (new java.awt.Dimension(UC.XSIZE, UC.YSIZE));


    // This are the "Main tabs"
    config = new javax.swing.JTabbedPane (); UC.Look(config);
    config.setPreferredSize (new java.awt.Dimension(UC.XSIZE, UC.YSIZE-30));
    snwTabs = new javax.swing.JTabbedPane (); UC.Look(snwTabs);
    snwTabs.setPreferredSize (new java.awt.Dimension(UC.XSIZE, UC.YSIZE-30));
    metaTabs = new javax.swing.JTabbedPane (); UC.Look(snwTabs);
    metaTabs.setPreferredSize (new java.awt.Dimension(UC.XSIZE, UC.YSIZE-30));
    metaMasterTabs = new javax.swing.JTabbedPane (); UC.Look(snwTabs);
    metaMasterTabs.setPreferredSize (new java.awt.Dimension(UC.XSIZE, UC.YSIZE-30));
    reportsPanel = new javax.swing.JPanel(); UC.Look(reportsPanel);
    reportsPanel.setPreferredSize (new java.awt.Dimension(UC.XSIZE, UC.YSIZE-30));
    reportsPane = new javax.swing.JTabbedPane(); UC.Look(reportsPane);
    reportsPane.setPreferredSize(new java.awt.Dimension(UC.XSIZE, UC.YSIZE-30));

    // Tabs for the config group
//    commgroup = new CommGroupPanel(); UC.Look(commgroup);
//    operator = new OperatorPanel(); UC.Look(operator);
//    channel = new MultiChannelPanel(); UC.Look(channel);
//    sendto = new SendtoPanel(); UC.Look(sendto);
//    regexp = new RegexpPanel(); UC.Look(regexp);
//    protocol = new ProtocolPanel(); UC.Look(protocol);
    flags = new FlagsPanel(); UC.Look(flags);
//    links = new LinksPanel(); UC.Look(links);
    
    // Tabs for the snw gropo
//    snwstation = new SNWStationPanel(); UC.Look(snwstation);
//    snwregexp = new SNWStationRegexpPanel(); UC.Look(snwregexp);
//    snwmult = new SNWMultiStationPanel(); UC.Look(snwmult);
//    group = new SNWGroupPanel(); UC.Look(group);
//    rule = new SNWRulePanel(); UC.Look(rule);
   
    // tabs for meta data
     station = new StationPanel();
     stationRE = new StationRegexpPanel();
     //response  = new ResponsePanel();
     responseRE = new ResponseRegexpPanel();
     //metachannel = new MetaChannelPanel();
     metachanRE = new MetaChannelRegexpPanel();
     groups = new GroupsPanel();
     loadURL = new LoadURLPanel();
     owner = new OwnerPanel();
     stationFlags = new StationFlagsPanel();
     channelFlags = new ChannelFlagsPanel();
     responseFlags = new ResponseFlagsPanel();
    // Reports
    Util.prta("ReportPanel");
    report = new ReportPanel("edge");
    Util.prta("ReportsPanel");
    reports = new ReportsPanel("edge");

    // here the TabbedPanes are added to the Panel which encloses them.
    // The panels are added to other tabbedPanes
    logout = new Logoutx(UC.getConnection());
//    MainTabs.addTab("ChanConfig",config);
//    MainTabs.addTab("SNW Config",snwTabs);
    MainTabs.addTab("MetaData", metaTabs);
    MainTabs.addTab("MetaMaster", metaMasterTabs);
    MainTabs.addTab("Reports",reportsPane);
    MainTabs.addTab("Logout", logout);
    
//    config.addTab("ChanConfig", channel);
//    config.addTab("Regexp Config", regexp);
//    config.addTab("Send To", sendto);
    //config.addTab("Operators", operator);
    //config.addTab("Comm Groups", commgroup);
    //config.addTab("Protocols", protocol);
    config.addTab("Flags",flags);
    //config.addTab("Links", links);
    
    // add the SNW tabs
//    snwTabs.addTab("Stations",snwstation);
//    snwTabs.addTab("Groups",group);
//    snwTabs.addTab("Rules", rule);
//    snwTabs.addTab("Regexp",snwregexp);
//    snwTabs.addTab("Multi Station",snwmult);
//    snwTabs.addTab("Operators", operator);
    //snwTabs.addTab("Comm Groups", commgroup);
//    snwTabs.addTab("Protocols", protocol);
    
    // add the MetaData tabs
    metaTabs.addTab("Station",station);
    metaTabs.addTab("StatRegExp",stationRE);
    //metaTabs.addTab("Channel",metachannel);
    metaTabs.addTab("ChanRegExp", metachanRE);
    //metaTabs.addTab("Response", response);
    metaTabs.addTab("ResponseRegExp", responseRE);
    metaMasterTabs.addTab("loadURL",loadURL);
    metaMasterTabs.addTab("StationFlags", stationFlags);
    metaMasterTabs.addTab("ChannelFlags", channelFlags);
    metaMasterTabs.addTab("RespFlags", responseFlags);
    metaMasterTabs.addTab("Group",groups);

    // add he Reports tabs
    reports.setPreferredSize(UC.XSIZE-35,UC.YSIZE-300);
    reportsPane.addTab("Reports",reports);
    reportsPane.addTab("Define Reports",report);
       
    // Main pannel gets the main tabs
    mainPanel.add (MainTabs);
    getContentPane ().add (mainPanel);
    //Util.prt("Add menus to content1 isVisible=" + mainPanel.isVisible());
    //Util.prt("Add menus to content1 isVisible=" + mainPanel.isVisible());
    int was = ToolTipManager.sharedInstance().getDismissDelay();
    ToolTipManager.sharedInstance().setDismissDelay(100000);
    int is =ToolTipManager.sharedInstance().getDismissDelay();
    Util.prta("ToolTipDismiss was="+was+" is now "+is);

  }
  
       
  /**
   *  The use of this window adapter to handle a "close" or exit is common.  But
   * the call to InvTree.exitCleanup() allows that routine to check for unrecorded
   *changes and prompt the user for advice on whether to save.
   */
  static class WL extends WindowAdapter {
    @Override
    public void windowClosing(WindowEvent e) {
      theGUI.saveProperties();
      
      System.exit(0);
    }
  }
  public static void cleanup() {

  }
  
  @Override
  public void start() {Util.prt("    **** start() called");}
  @Override
  public void init() {Util.prt("    **** init() called");}
  @Override
  public void stop() {Util.prt("    **** stop() called");}
  @Override
  public void destroy() {Util.prt("    **** destroys() called");}
  public void setTitle(String s) {aFrame.setTitle(s);}
  public String getTitle() {return aFrame.getTitle();}
  private void saveProperties() {
    Util.setProperty("StartX",""+aFrame.getLocationOnScreen().getX());
    Util.setProperty("StartY",""+aFrame.getLocationOnScreen().getY());
    Util.saveProperties(); 
  }          
  
  public static void  main(String [] args) {
    try {
      Util.init(gov.usgs.anss.metadatagui.UC.getPropertyFilename());
      DBConnectionThread.init(Util.getProperty("MetaDBServer"));
      Util.setApplet(false);	// True= no disk output
      Util.addDefaultProperty("StartX","10");
      Util.addDefaultProperty("StartY","22");
      Util.addDefaultProperty("PrinterCommand", "print");
      Util.addDefaultProperty("PrinterFile","metagui.lpt");
      Util.addDefaultProperty("SessionFile","SESSION.OUT");
     //Util.loadProperties(gov.usgs.anss.metadatagui.UC.getPropertyFilename());
      MetaGUI applet = new MetaGUI();
      String OS = System.getProperty("os.name");
      Util.debug(true);		// false=session .out
      Util.prt("OS is "+OS);  Util.prt("starting up");
      if(OS.equals("SunOS")) Util.debug(true);
      aFrame = new Frame("MetaData GUI");
      aFrame.addWindowListener(new WL());
      aFrame.add(applet, BorderLayout.CENTER);
      aFrame.setSize(UC.XSIZE, UC.YSIZE);
      aFrame.setLocation((int) Double.parseDouble(Util.getProperty("StartX")), (int) Double.parseDouble(Util.getProperty("StartY")));
      applet.init();
      Util.setModeGMT();
     
      applet.start();
      Util.prt("After start");
      aFrame.setVisible(true);
      while(true) {Util.sleep(1000);}
    }
    catch(RuntimeException e) {
      Util.prt("Caught a Runtime exception"+e.getMessage());
      e.printStackTrace();
    }
  }
}


