/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */

package gov.usgs.edge.config; 

import gov.usgs.anss.db.DBConnectionThread;
import gov.usgs.anss.gui.*;
import gov.usgs.anss.metadatagui.*;
import gov.usgs.anss.gui.ReportPanel;
import gov.usgs.anss.gui.ReportsPanel;
import gov.usgs.anss.util.User;
import gov.usgs.anss.util.Util;
import gov.usgs.edge.alarm.AlarmTabs;
import gov.usgs.edge.snw.*;
import java.awt.BorderLayout;
import java.awt.Frame;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.ToolTipManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.SQLException;

/** 
 * This class implements the user interface for the tabs of the EdgeConfig application.
 * First the login panel is shown and then on successful login, it  builds the JPanel 
 * and adds all the tabs for the submenus and 
 * creates the specialized JPanel for each tab.  To add a tab :
 * create a JPanel implementing some user interface
 * 2) add it to a main tab.
 * @author  ketchum
 *  
 */
public final class EdgeConfig extends javax.swing.JApplet 
{
  private static final String version="EdgeCWB Configuration 0.36 2018-09-07";
  private final Login firstLogin;
  private User user;          // User information from logon
  private DBConnectionThread C;       // Connection to Database system JDBC
  private final String OS;
  static Frame aFrame;
  static EdgeConfig theEcon;
  static boolean includeHoldingsReports;
  private GuiComms guiComms;
  public EdgeConfig() {
		OS = System.getProperty("os.name");
    setName("Ketchum");
    Util.init();
    firstLogin = new Login(this);
    getContentPane().add (firstLogin);
    firstLogin.requestFocus();
  }
  public void buildMenus(User userin, DBConnectionThread Cin, boolean incHoldingsReports) {
    includeHoldingsReports = incHoldingsReports;
    theEcon = this;
    user = userin;              // Handle to user stuff
    C = Cin;
    if( hasTable("edge","channel")) 
      Util.prt("edge.channel exists");
    if( hasTable("dave","dave")) 
      Util.prt("dave exists!");
    if( hasTable("edge","dave")) 
      Util.prt("edge.dave exists");		Util.prta("In build menus C="+C);
    gov.usgs.edge.config.UC.setConnection(C.getConnection());  // This should not be used any more, but it might
    getContentPane().setVisible(false);
    initComponents ();

//		gov.usgs.edge.channels.UC.Look(this);
    getContentPane().remove(firstLogin);
//    getContentPane().setBackground(Color.green);
    getContentPane().setSize(gov.usgs.edge.config.UC.XSIZE, gov.usgs.edge.config.UC.YSIZE);

    getContentPane().setVisible(true);

    Util.prta("Build menus done!");
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
           private SNWStationPanel snwstation;
           private SNWRulePanel rule;
           private SNWGroupPanel group;
           private SNWStationRegexpPanel snwregexp;
           private SNWMultiStationPanel snwmult;

      private javax.swing.JTabbedPane metaTabs;
           private StationPanel station;
           private StationRegexpPanel stationRE;
           private ResponsePanel response;
           private ResponseRegexpPanel responseRE;
           private MetaChannelPanel metachannel;
           private MetaChannelRegexpPanel metachanRE;
      private javax.swing.JTabbedPane edgeTabs;
          private EdgemomPanel edgemom;
          private EdgemomsetupPanel edgemomsetup;
          private EdgeFilePanel edgefile;
          private EdgethreadPanel edgethread;
          private RolePanel role;
          private CpuPanel cpu;
          private ExportPanel export;
          private FKSetupPanel fkSetup;
      
      private javax.swing.JTabbedPane edgeTabsInstance;
          private EdgeMomInstancePanel edgemomInstance;
          private EdgeMomInstanceSetupPanel edgemomInstanceSetup;
          //private EdgemomSetupInstancePanel edgemomSetupInstance;
          private EdgeFilePanel edgefileInstance;
          private EdgethreadPanel edgethreadInstance;
          private RoleInstancePanel roleInstance;
          private CpuPanelInstance cpuInstance;
          private QueryMomInstancePanel querymomInstance;
          private ExportPanel exportInstance;
          private FKSetupPanel fkSetupInstance;


      private javax.swing.JTabbedPane metaMasterTabs;
           private gov.usgs.edge.config.GroupsPanel groups;
           private gov.usgs.anss.metadatagui.TypePanel types;
           private LoadURLPanel loadURL;
           private OwnerPanel owner;
           private StationFlagsPanel stationFlags;
           private ChannelFlagsPanel channelFlags;
           private ResponseFlagsPanel responseFlags;

      private javax.swing.JTabbedPane gsnTabs;
          private EdgeStationPanel edgeStation;
          private gov.usgs.anss.gui.UserCommandPanel userCommand;
          private gov.usgs.edge.config.UUSSChannelPanel uuss;
          private RequestStationPanel requestStationGSN;
          private javax.swing.JPanel remoteCommand;
          private gov.usgs.edge.config.FetchServerPanel fetchServer;
      private AlarmTabs alarmTabs;

      private javax.swing.JTabbedPane channels;
          private RegexpPanel regexp;
          private MultiChannelPanel channel;
          private CommGroupPanel commgroup;
          private SendtoPanel sendto;
          private HydraFlagsPanel hydraFlags;
          private OperatorPanel operator;
          private ProtocolPanel protocol;
          private gov.usgs.edge.config.FlagsPanel flags;
          private LinksPanel links;
          private RequestTypePanel requestType;
          private RequestStationPanel requestStation;
          private PickerPanel picker;
          private SubspaceAreaPanel subspace;
      private javax.swing.JTabbedPane reportsPane;
        private javax.swing.JPanel reportsPanel;
          private ReportsPanel reports;     // do reports
          private ReportPanel report;       // Define reports          
          private StatusLogRegexpPanel logPanel;
          private GapReportPanel gapreport;
          private NewMultiGapForm newmultigapreport;
          //private MultiGapReportPanel multigapreport;

  /**
   * Create all of the specialized panels that are needed and put them in the
   *appropriate tabs.  All the specializations are normally created, but the user's
   *privelges or Roles is used to decide if the tab is created and the JPanel attached
   *to it.
   */
  private void initComponents () {
    guiComms = new GuiComms(this);

    boolean includeMeta = Util.getProperty("MetaDBServer") != null;
    // Define the main panel and its tabbed paneTt
    //Util.prta("EdgeConfig - create Main Tab xsize="+gov.usgs.edge.channels.UC.XSIZE+" ysize="+gov.usgs.edge.channels.UC.YSIZE);
    mainPanel = new javax.swing.JPanel (); gov.usgs.edge.config.UC.Look(mainPanel);
    MainTabs = new javax.swing.JTabbedPane (); gov.usgs.edge.config.UC.Look(MainTabs);
    getContentPane ().setLayout (new java.awt.FlowLayout ());
    mainPanel.setPreferredSize (new java.awt.Dimension(gov.usgs.edge.config.UC.XSIZE, gov.usgs.edge.config.UC.YSIZE));
    MainTabs.setPreferredSize (new java.awt.Dimension(gov.usgs.edge.config.UC.XSIZE, gov.usgs.edge.config.UC.YSIZE));


    // This are the "Main tabs"
    channels = new javax.swing.JTabbedPane (); gov.usgs.edge.config.UC.Look(channels);
    channels.setPreferredSize (new java.awt.Dimension(gov.usgs.edge.config.UC.XSIZE, gov.usgs.edge.config.UC.YSIZE-30));
    snwTabs = new javax.swing.JTabbedPane (); gov.usgs.edge.config.UC.Look(snwTabs);
    snwTabs.setPreferredSize (new java.awt.Dimension(gov.usgs.edge.config.UC.XSIZE, gov.usgs.edge.config.UC.YSIZE-30));
    edgeTabs = new javax.swing.JTabbedPane (); gov.usgs.edge.config.UC.Look(edgeTabs);
    edgeTabs.setPreferredSize (new java.awt.Dimension(gov.usgs.edge.config.UC.XSIZE, gov.usgs.edge.config.UC.YSIZE-30));
    edgeTabsInstance = new javax.swing.JTabbedPane (); gov.usgs.edge.config.UC.Look(edgeTabsInstance);
    edgeTabsInstance.setPreferredSize (new java.awt.Dimension(gov.usgs.edge.config.UC.XSIZE, gov.usgs.edge.config.UC.YSIZE-30));
    gsnTabs = new javax.swing.JTabbedPane (); gov.usgs.edge.config.UC.Look(gsnTabs);
    gsnTabs.setPreferredSize (new java.awt.Dimension(gov.usgs.edge.config.UC.XSIZE, gov.usgs.edge.config.UC.YSIZE-30));
    alarmTabs = new AlarmTabs(); gov.usgs.edge.config.UC.Look(alarmTabs);
    reportsPanel = new javax.swing.JPanel(); gov.usgs.edge.config.UC.Look(reportsPanel);
    reportsPanel.setPreferredSize (new java.awt.Dimension(gov.usgs.edge.config.UC.XSIZE, gov.usgs.edge.config.UC.YSIZE-30));
    reportsPane = new javax.swing.JTabbedPane(); gov.usgs.edge.config.UC.Look(reportsPane);
    reportsPane.setPreferredSize(new java.awt.Dimension(gov.usgs.edge.config.UC.XSIZE, gov.usgs.edge.config.UC.YSIZE-30));

    // Tabs for the channels group
    Util.prta("Start config group");
    commgroup = new CommGroupPanel(); gov.usgs.edge.config.UC.Look(commgroup);
    operator = new OperatorPanel(); gov.usgs.edge.config.UC.Look(operator);
    channel = new MultiChannelPanel(); gov.usgs.edge.config.UC.Look(channel);
    sendto = new SendtoPanel(); gov.usgs.edge.config.UC.Look(sendto);
    hydraFlags = new HydraFlagsPanel(); gov.usgs.edge.config.UC.Look(hydraFlags);
    regexp = new RegexpPanel(); gov.usgs.edge.config.UC.Look(regexp);
    protocol = new ProtocolPanel(); gov.usgs.edge.config.UC.Look(protocol);
    flags = new gov.usgs.edge.config.FlagsPanel(); gov.usgs.edge.config.UC.Look(flags);
    links = new LinksPanel(); gov.usgs.edge.config.UC.Look(links);
    requestType = new RequestTypePanel(); gov.usgs.edge.config.UC.Look(requestType);
    requestStation = new RequestStationPanel(); gov.usgs.edge.config.UC.Look(requestStation);
    if( hasTable("edge","picker")) {
      picker = new PickerPanel(); gov.usgs.edge.config.UC.Look(picker);
    }
    if( hasTable("edge","subspacearea")) {
      subspace = new SubspaceAreaPanel(); UC.Look(subspace);
    }
    
    //Tabs for GSN tab
    //Util.prt("node="+Util.getSystemName()+" node="+Util.getNode());
    if( Util.getSystemName().contains("uucwb") || Util.getProperty("DBServer").indexOf("128.110") == 0
            ) {
      uuss = new UUSSChannelPanel();
    }
    
    if(hasTable("edge","gsnstation")) {
      edgeStation = new EdgeStationPanel(); gov.usgs.edge.config.UC.Look(edgeStation);  
      requestStationGSN = new RequestStationPanel(); gov.usgs.edge.config.UC.Look(requestStationGSN);
    }
    if( hasTable("edge","fetchserver")) {
      fetchServer = new FetchServerPanel(); gov.usgs.edge.config.UC.Look(fetchServer);
    }

    
    userCommand = new UserCommandPanel("anss");
    remoteCommand = new RemoteCommandPanel("anss");

    // Tabs for the snw group
    Util.prta("Start SNW group");
    snwstation = new SNWStationPanel(); gov.usgs.edge.config.UC.Look(snwstation);
    snwregexp = new SNWStationRegexpPanel(); gov.usgs.edge.config.UC.Look(snwregexp);
    snwmult = new SNWMultiStationPanel(); gov.usgs.edge.config.UC.Look(snwmult);
    group = new SNWGroupPanel(); gov.usgs.edge.config.UC.Look(group);
    rule = new SNWRulePanel(); gov.usgs.edge.config.UC.Look(rule);
    
    // Build tabs for the edge channels group
    Util.prta("Start EdgeConfig group");
    edgemom = new EdgemomPanel();gov.usgs.edge.config.UC.Look(edgemom);
    edgemomsetup = new EdgemomsetupPanel();gov.usgs.edge.config.UC.Look(edgemomsetup);
    edgefile = new EdgeFilePanel();gov.usgs.edge.config.UC.Look(edgefile);
    edgethread = new EdgethreadPanel();gov.usgs.edge.config.UC.Look(edgethread);
    role = new RolePanel();gov.usgs.edge.config.UC.Look(role);
    cpu = new CpuPanel();gov.usgs.edge.config.UC.Look(cpu);
    export = new ExportPanel();gov.usgs.edge.config.UC.Look(export);
    if( hasTable("edge","fk")) {
      fkSetup = new FKSetupPanel();gov.usgs.edge.config.UC.Look(fkSetup);
    }

    
    if( (Util.getProperty("instanceconfig") != null || Util.getProperty("AllowInstance") != null) && hasTable("edge","instancesetup")) {
      Util.prt("instanceconfig present - setup instance tab");
      edgemomInstance = new EdgeMomInstancePanel();gov.usgs.edge.config.UC.Look(edgemomInstance);
      edgemomInstance.setGuiComms(guiComms);
      edgemomInstanceSetup = new EdgeMomInstanceSetupPanel();gov.usgs.edge.config.UC.Look(edgemomInstanceSetup);
      guiComms.setEdgeMomInstanceSetup(edgemomInstanceSetup);
      edgefileInstance = new EdgeFilePanel();gov.usgs.edge.config.UC.Look(edgefileInstance);
      edgethreadInstance = new EdgethreadPanel();gov.usgs.edge.config.UC.Look(edgethreadInstance);
      //edgemomSetupInstance = new EdgemomSetupInstancePanel();gov.usgs.edge.config.UC.Look(edgemomSetupInstance);
      roleInstance = new RoleInstancePanel();gov.usgs.edge.config.UC.Look(roleInstance);
      cpuInstance = new CpuPanelInstance();gov.usgs.edge.config.UC.Look(cpuInstance);
      querymomInstance = new QueryMomInstancePanel();gov.usgs.edge.config.UC.Look(querymomInstance);
      if( hasTable("edge","fk")) {
        fkSetupInstance = new FKSetupPanel();gov.usgs.edge.config.UC.Look(fkSetupInstance);
      }
      exportInstance = new ExportPanel();gov.usgs.edge.config.UC.Look(exportInstance);
    }
  
    // tabs for meta data
    if(includeMeta) {
      Util.prta("Start Meta Group");
      metaTabs = new javax.swing.JTabbedPane (); gov.usgs.edge.config.UC.Look(metaTabs);
      metaTabs.setPreferredSize (new java.awt.Dimension(gov.usgs.edge.config.UC.XSIZE, gov.usgs.edge.config.UC.YSIZE-30));
      metaMasterTabs = new javax.swing.JTabbedPane (); gov.usgs.edge.config.UC.Look(metaMasterTabs);
      metaMasterTabs.setPreferredSize (new java.awt.Dimension(gov.usgs.edge.config.UC.XSIZE, gov.usgs.edge.config.UC.YSIZE-30));
      station = new StationPanel();gov.usgs.edge.config.UC.Look(station);
      stationRE = new StationRegexpPanel();gov.usgs.edge.config.UC.Look(stationRE);
      //response  = new ResponsePanel();gov.usgs.edge.config.UC.Look(response);
      responseRE = new ResponseRegexpPanel();gov.usgs.edge.config.UC.Look(responseRE);
      //metachannel = new MetaChannelPanel();gov.usgs.edge.config.UC.Look(metachannel);     
      //metachanRE = new MetaChannelRegexpPanel();gov.usgs.edge.config.UC.Look(metachanRE);
      groups = new gov.usgs.edge.config.GroupsPanel();gov.usgs.edge.config.UC.Look(groups);
      Util.prt("Start Meta Group2");
      types = new TypePanel();gov.usgs.edge.config.UC.Look(types);
      loadURL = new LoadURLPanel();gov.usgs.edge.config.UC.Look(loadURL);
      owner = new OwnerPanel();gov.usgs.edge.config.UC.Look(owner);
      Util.prt("Start Meta Group3");
      stationFlags = new StationFlagsPanel();gov.usgs.edge.config.UC.Look(stationFlags);
      channelFlags = new ChannelFlagsPanel();gov.usgs.edge.config.UC.Look(channelFlags);
      responseFlags = new ResponseFlagsPanel();gov.usgs.edge.config.UC.Look(responseFlags);
      requestType = new RequestTypePanel();gov.usgs.edge.config.UC.Look(requestType);
      
      // Add meta related panels to their respective tabs
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
      metaMasterTabs.addTab("Type", types);
      // Add meta tabs to main tabs
    }
    // Reports
    Util.prta("Start Reports");
    Util.prta("ReportPanel");
    report = new ReportPanel("edge");gov.usgs.edge.config.UC.Look(report);
    Util.prta("ReportsPanel");
    reports = new ReportsPanel("edge");
    logPanel = new StatusLogRegexpPanel();gov.usgs.edge.config.UC.Look(logPanel);
    if(includeHoldingsReports) {
      gapreport = new GapReportPanel(DBConnectionThread.getThread("gap"));gov.usgs.edge.config.UC.Look(gapreport);
      //multigapreport = new MultiGapReportPanel();
      newmultigapreport = new NewMultiGapForm(DBConnectionThread.getThread("gap"));gov.usgs.edge.config.UC.Look(newmultigapreport);
    }
    //if(Util.getProperty("AllowInstance") != null) {
      edgeTabsInstance.addTab("Thread Setup", edgemomInstanceSetup); 
      edgeTabsInstance.addTab("Instance", edgemomInstance); 
      edgeTabsInstance.addTab("Config File", edgefileInstance); 
      edgeTabsInstance.addTab("Thread Doc", edgethreadInstance); 
      edgeTabsInstance.addTab("Role", roleInstance); 
      edgeTabsInstance.addTab("Cpu", cpuInstance); 
      edgeTabsInstance.addTab("QueryMom",querymomInstance); 
      edgeTabsInstance.addTab("Export", exportInstance);
      if(fkSetupInstance != null) edgeTabsInstance.addTab("FK", fkSetupInstance); 
    //}
    // here the TabbedPanes are added to the Panel which encloses them.
    // The panels are added to other tabbedPanes
    Util.prta("Start building all tabs AllowInstance="+Util.getProperty("AllowInstance") 
            + " instanceconfig="+Util.getProperty("instanceconfig")+" includeMeta="+includeMeta
            + (picker == null?" no picker":" HasPicker")+ ( edgeStation == null ?" No EdgeStation":" Has EdgeStation")
            + (fetchServer == null?" No fetchserver":" Has FetchServer") + (fkSetup == null?" No FKSEetup":" Has FK"));
    logout = new Logout(C);
    MainTabs.addTab("Channels",channels);
    MainTabs.addTab("SNW",snwTabs);
    MainTabs.addTab("Maint"+(edgeStation != null ? "/Station":"") +(uuss != null && edgeStation != null?"/":"")+ (uuss != null? "UUSS":""), gsnTabs);
    if(Util.getProperty("instanceconfig") != null || Util.getProperty("AllowInstance") != null) {
      MainTabs.addTab("Instance", edgeTabsInstance) ;
    }
    else MainTabs.addTab("LegacyCfg",edgeTabs);

    MainTabs.addTab("Alarm", alarmTabs);
    if( includeMeta) {
      MainTabs.addTab("MetaData", metaTabs);
      MainTabs.addTab("MetaMaster", metaMasterTabs);
    }
    MainTabs.addTab("Reports",reportsPane);
    MainTabs.addTab("Logout", logout);
    
    channels.addTab("ChanConfig", channel);
    channels.addTab("Regexp Config", regexp);
    channels.addTab("Send To", sendto);
    //config.addTab("Operators", operator);
    //config.addTab("Comm Groups", commgroup);
    //config.addTab("Protocols", protocol);
    channels.addTab("Flags",flags);
    channels.addTab("Hydra", hydraFlags);
    channels.addTab("RqstStation",requestStation);
    channels.addTab("RqstType", requestType);
    if(picker != null) channels.addTab("Picker", picker);
    if(subspace != null) channels.addTab("SSD", subspace);
    //config.addTab("Links", links);
    
    // add the SNW tabs
    snwTabs.addTab("Stations",snwstation);
    snwTabs.addTab("Groups",group);
    snwTabs.addTab("Rules", rule);
    snwTabs.addTab("Regexp",snwregexp);
    snwTabs.addTab("Multi Station",snwmult);
    snwTabs.addTab("Operators", operator);
    //snwTabs.addTab("Comm Groups", commgroup);
    snwTabs.addTab("Protocols", protocol);
    
    // Build the edge channels tabs
    edgeTabs.addTab("EdgeMomSetup", edgemomsetup);
    edgeTabs.addTab("EdgeMom", edgemom);
    edgeTabs.addTab("SideFile", edgefile);
    edgeTabs.addTab("EdgeThread", edgethread);
    edgeTabs.addTab("Role", role);
    edgeTabs.addTab("Node", cpu);
    edgeTabs.addTab("Export", export);
    if(fkSetup != null) edgeTabs.addTab("FK", fkSetup);

    if(edgeStation != null) {gsnTabs.addTab("StationCnfg", edgeStation); }
    gsnTabs.addTab("EdgeMaint", userCommand);
    
    if(uuss != null) {gsnTabs.addTab("UUSS Chan", uuss); Util.prt("Adding UUSS panel");}
    gsnTabs.addTab("RequestStation", requestStationGSN);
    if(fetchServer != null) gsnTabs.addTab("FetchServer", fetchServer);
    gsnTabs.addTab("DefineUserCommand", remoteCommand);

    // add he Reports tabs
    reports.setPreferredSize(gov.usgs.edge.config.UC.XSIZE-35,gov.usgs.edge.config.UC.YSIZE-300);
    reportsPane.addTab("Reports",reports);
    if(User.isRole('M')) reportsPane.addTab("Define Reports",report);
    reportsPane.addTab("Log", logPanel);
    if(includeHoldingsReports) {
      reportsPane.addTab(GapReportPanel.TITLE,gapreport);

      //reportsPane.addTab(MultiGapReportPanel.TITLE,multigapreport);
      reportsPane.addTab(NewMultiGapForm.TITLE, newmultigapreport);
    }
    reportsPane.addTab("LegacyCfg",edgeTabs);  

    // Main pannel gets the main tabs
    mainPanel.add (MainTabs);
    getContentPane ().add (mainPanel);
    //Util.prt("Add menus to content1 isVisible=" + mainPanel.isVisible());
    Util.prta("EdgeConfig build done");
    int was = ToolTipManager.sharedInstance().getDismissDelay();
    ToolTipManager.sharedInstance().setDismissDelay(100000);
    int is =ToolTipManager.sharedInstance().getDismissDelay();
    Util.prta("ToolTipDismiss was="+was+" is now "+is);

  }
  
  public void setSelectedSubTab(String parentTabName, String subTabTitle) {
    if (parentTabName.equals("edgeTabsInstance")) {
      edgeTabsInstance.setSelectedIndex(selectTabIndexTitled(edgeTabsInstance,subTabTitle));
    }
  }
  
  private int selectTabIndexTitled(javax.swing.JTabbedPane parentTab,String tabTitle) {
    int index = -1;
    for (int i = parentTab.getTabCount()-1; i >=0; i-- ) {
      if (parentTab.getTitleAt(i).equals(tabTitle)) {
        index = i;
        break;
      }
    }
    return index;
  }
  public static boolean hasTable(String db, String table) {
    // Return true if this table is in the DB
    DBConnectionThread dbconn = DBConnectionThread.getThread(db);
    if(dbconn == null) return false;
    try {
      try (Statement stmt = dbconn.getNewStatement(false);ResultSet rs = stmt.executeQuery("DESCRIBE "+db+"."+table)) {
        if(rs.next()) {
          String s = rs.getString(1);
          //Util.prt(" s="+s);
        }
      }
      Util.prta("hasTable("+db+"."+table+") is True");
      return true;
    }
    catch(SQLException e) {
      Util.prta("hasTable("+db+"."+table+") is False");
      return false;
    }
  }
  private void saveProperties() {
    if(aFrame.getLocationOnScreen().getX() > -30000.) Util.setProperty("StartX",""+aFrame.getLocationOnScreen().getX());
    if(aFrame.getLocationOnScreen().getY() > -30000.) Util.setProperty("StartY",""+aFrame.getLocationOnScreen().getY());
    Util.saveProperties(); 
  }          
       
  /** This is called at closing to do cleanup like saving the properties file so corner a
   * nd size is preserved.
   */
  static class WL extends WindowAdapter {
    @Override
    public void windowClosing(WindowEvent e) {
      Util.prt(e.getWindow()+" aframe="+aFrame+" "+(e.getWindow() == aFrame));
      if(theEcon != null) theEcon.saveProperties();
      if(e.getWindow() == aFrame) System.exit(0);
    }
  }
  public static void cleanup() {}
  
  @Override
  public void start() {Util.prt("    **** start() called");}
  @Override
  public void init() {Util.prt("    **** init() called");}
  @Override
  public void stop() {Util.prt("    **** stop() called");}
  @Override
  public void destroy() {Util.prt("    **** destroys() called");}
  public void setTitle(String s) {aFrame.setTitle(s);}
  public String getTitle() {return version;}
  public static void  main(String [] args) {
    try {
      Util.setProcess("EdgeConfig");
      Util.init(gov.usgs.edge.config.UC.getPropertyFilename());
      gov.usgs.anss.util.UC.init();
      Util.prt("DBServer="+Util.getProperty("DBServer"));
      Util.setApplet(false);	// True= no disk output
      Util.addDefaultProperty("StartX","10");
      Util.addDefaultProperty("StartY","22");
      Util.addDefaultProperty("PrinterCommand", "print");
      Util.addDefaultProperty("PrinterFile","edgecon.lpt");
      Util.addDefaultProperty("SessionFile","SESSION.OUT");
      Util.addDefaultProperty("SSLOff", "true");
      Util.addDefaultProperty("DBServer", "localhost/3306:edge:mysql:edge");
      Util.addDefaultProperty("StatusDBServer", "localhost/3306:status:mysql:status");
      //Util.addDefaultProperty("MetaDBServer", "localhost/3306:metadata:mysql:metadata");
      //Util.loadProperties(gov.usgs.edge.channels.gov.usgs.edge.channels.UC.getPropertyFilename());
      EdgeConfig applet = new EdgeConfig();
      String OS = System.getProperty("os.name");
      Util.debug(true);		// false=session .out
      Util.prt("OS is "+OS);  
      Util.prt("starting up xsize="+gov.usgs.edge.config.UC.XSIZE+" ysize="+gov.usgs.edge.config.UC.YSIZE);
      if(OS.equals("SunOS")) Util.debug(true);
      aFrame = new Frame(version);
      aFrame.addWindowListener(new WL());
      aFrame.add(applet, BorderLayout.CENTER);
      aFrame.setSize(gov.usgs.edge.config.UC.XSIZE, gov.usgs.edge.config.UC.YSIZE+25);
      applet.init();
      Util.setModeGMT();
      aFrame.setLocation((int) Double.parseDouble(Util.getProperty("StartX")), (int) Double.parseDouble(Util.getProperty("StartY")));
      
      applet.start();
      Util.prt("After start");
      aFrame.setVisible(true);
    }
    catch(RuntimeException e) {
      System.out.println("Caught a Runtime exception"+e.getMessage());
      e.printStackTrace();
    }
    while(true) {Util.sleep(2000);}
  }
}


