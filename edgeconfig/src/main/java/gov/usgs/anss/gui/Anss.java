/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.gui;

import gov.usgs.anss.db.DBConnectionThread;
import gov.usgs.anss.db.JDBConnection;
import gov.usgs.anss.portables.DeploymentPortablePanel;
import gov.usgs.anss.portables.ReftekPortablePanel;
import gov.usgs.anss.tsplot.TSPlotPanel;
import gov.usgs.anss.util.UC;
import gov.usgs.anss.util.User;
import gov.usgs.anss.util.UserPropertiesPanel;
import gov.usgs.anss.util.Util;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.ToolTipManager;

/**
 * This class implements the user interface for the tabs of the Inv application. The constructor
 * builds the JPanel and adds all the tabs for the submenus and creates the specialized JPanel for
 * each tab. To add a tab : create a JPanel implementing some user interface 2)
 *
 * @author ketchum
 * @version 1.0
 */
public final class Anss extends javax.swing.JApplet {

  private final Login firstLogin;
  private User user;          // User information from logon
  private static DBConnectionThread C;       // Connection to Database system JDBC
  private final String OS;
  static Frame aFrame;
  private static Anss theAnss;
  private java.awt.Container theContentPane;
  static boolean includeHoldingsReports;

  public Anss() {
    OS = System.getProperty("os.name");
    Util.init();
    setName("Ketchum");
    firstLogin = new Login(this);
    getContentPane().add(firstLogin);
//		UC.Look(getContentP);
    firstLogin.requestFocus();

//    initComponents ();
  }

  //public static Connection getConnection() {return C;}
  public void buildMenus(User userin, DBConnectionThread Cin, boolean includeHoldingsReports) {
    Anss.includeHoldingsReports = includeHoldingsReports;
    theAnss = this;
    user = userin;              // Handle to user stuff
    C = Cin;
    Util.prta("In build menus C=" + C);
    getContentPane().setVisible(false);
    initComponents();
//		UC.Look(this);
    getContentPane().remove(firstLogin);
//    getContentPane().setBackground(Color.green);
    getContentPane().setSize(gov.usgs.anss.gui.UC.XSIZE, gov.usgs.anss.gui.UC.YSIZE);
    aFrame.setSize(gov.usgs.anss.gui.UC.XSIZE, gov.usgs.anss.gui.UC.YSIZE);

    getContentPane().setVisible(true);

    Util.prta("Build menus done!");
    //new KeepMySQLFresh();     // Thread to keep alive all MySQL connections

  }
  // declare all of the JPanels which implenent a pane in the interface.  All of these
  // should be given instance by the initComponents() routine.
  private javax.swing.JPanel login;
  private javax.swing.JPanel mainPanel;
  private javax.swing.JTabbedPane MainTabs;
  private javax.swing.JPanel files;
  private javax.swing.JTabbedPane fileMaintTabs;
  private RTSStationPanel rtsstation;
  private ReftekPanel reftek;
  private TCPStationPanel tcpstation;
  private NsnStationPanel nsnstation;
  private Q330Panel q330;
  private VsatPanel vsat;
  private UserPropertiesPanel userProperties;
  private javax.swing.JPanel edge;
  private javax.swing.JTabbedPane edgeMaintTabs;
  //private TextClientPanel textClient;
  private UserCommandPanel userCommand;
  private javax.swing.JPanel utilities;
  private javax.swing.JTabbedPane utilitiesTabs;
  private UserSessionPanel console;
  private RTSUtilityPanel rtsUtility;
  private TSPlotPanel tsplot;
  private javax.swing.JPanel portable;
  private javax.swing.JTabbedPane portableTabs;
  private javax.swing.JPanel portableDeployment;
  private javax.swing.JPanel portableReftek;
  private javax.swing.JPanel master;
  private javax.swing.JTabbedPane masterTabs;
  private javax.swing.JPanel cpu;
  private javax.swing.JPanel commlink;
  private javax.swing.JPanel cpulinkip;
  private javax.swing.JPanel dasConfig;
  private javax.swing.JPanel remoteCommand;
  private javax.swing.JTabbedPane reportsPane;
  private javax.swing.JPanel reportsPanel;
  private ReportsPanel reports;     // do reports
  private ReportPanel report;       // Define reports
  private GapReportPanel gapreport;
  private NewMultiGapForm newmgapreport;
  //private NewMultiGapForm newmultigapreport;
  //private MultiGapReportPanel multigapreport;
  private StatusLogRegexpPanel logPanel;
  private Logout logout;

  /**
   * Create all of the specialized panels that are needed and put them in the appropriate tabs. All
   * the specializations are normally created, but the user's privelges or Roles is used to decide
   * if the tab is created and the JPanel attached to it.
   */
  private void initComponents() {
    // Create each of the possible Panels we can display and may need
    //gov.usgs.anss.gui.UC.setConnection(JDBConnection.getConnection("anss"));
    Util.prta("Logout");
    logout = new Logout(C);
    Util.prta("TCPStationPanel");
    tcpstation = new TCPStationPanel();
    Util.prta("ReftekPanel");
    reftek = new ReftekPanel();
    Util.prta("RTSStationPanel");
    rtsstation = new RTSStationPanel();
    Util.prta("NsnStationPanel");
    dasConfig = new DasConfigPanel();
    remoteCommand = new RemoteCommandPanel("anss");
    nsnstation = new NsnStationPanel();
    Util.prta("VsatPanel");
    vsat = new VsatPanel();
    q330 = new Q330Panel();
    Util.prta("UserPropertiesPanel");
    userProperties = new UserPropertiesPanel();
    Util.prta("RTSUtilityPanel");
    if (Util.getProperty("RTSServer") != null) {
      if (!Util.getProperty("RTSServer").equals("") && !Util.getProperty("RTSServer").equalsIgnoreCase("none")) {
        rtsUtility = new RTSUtilityPanel();
      }
    }
    tsplot = new TSPlotPanel(new Dimension(this.getWidth() - 10, this.getHeight() - 70));
    //textClient = new TextClientPanel();
    userCommand = new UserCommandPanel("anss");
    console = new UserSessionPanel();
    Util.prta("RolePanel");
    cpu = new CpuPanel();
    Util.prta("CommLinkPanel");
    commlink = new CommLinkPanel();
    Util.prta("RoleLinkIPPanel");
    cpulinkip = new CpuLinkIPPanel();
    Util.prta("ReportPanel");
    report = new ReportPanel("anss");
    Util.prta("ReportsPanel");
    reports = new ReportsPanel("anss");
    /*Util.prta("MultiGapReportPanel"); 
    multigapreport = new MultiGapReportPanel();*/
    Util.prta("New M Gap");
    if (includeHoldingsReports) {
      //newmgapreport = new NewMultiGapForm(DBConnectionThread.getThread("anss"));
      newmgapreport = new NewMultiGapForm(DBConnectionThread.getThread("gap"));
      Util.prta("GapReportPanel");
      gapreport = new GapReportPanel(DBConnectionThread.getThread("gap"));
    }
    Util.prta("Define panel");
    logPanel = new StatusLogRegexpPanel();

    portableDeployment = new DeploymentPortablePanel();
    portableReftek = new ReftekPortablePanel();

    // Define the main panel and its tabbed paneTt
    mainPanel = new javax.swing.JPanel();
    UC.Look(mainPanel);
    MainTabs = new javax.swing.JTabbedPane();
    UC.Look(MainTabs);
    getContentPane().setLayout(new java.awt.FlowLayout());
    mainPanel.setPreferredSize(new java.awt.Dimension(gov.usgs.anss.gui.UC.XSIZE, gov.usgs.anss.gui.UC.YSIZE));
    MainTabs.setPreferredSize(new java.awt.Dimension(gov.usgs.anss.gui.UC.XSIZE, gov.usgs.anss.gui.UC.YSIZE));

    // This are the "Main tabs"
    files = new javax.swing.JPanel();
    UC.Look(files);
    edge = new javax.swing.JPanel();
    UC.Look(edge);
    utilities = new javax.swing.JPanel();
    UC.Look(utilities);
    master = new javax.swing.JPanel();
    UC.Look(master);
    portable = new javax.swing.JPanel();
    UC.Look(portable);
    reportsPanel = new javax.swing.JPanel();
    UC.Look(reportsPanel);

    // Here set up the main tabs by creating a JTabbed Pane and setting its size
    fileMaintTabs = new javax.swing.JTabbedPane();
    UC.Look(fileMaintTabs);
    fileMaintTabs.setPreferredSize(new java.awt.Dimension(gov.usgs.anss.gui.UC.XSIZE, gov.usgs.anss.gui.UC.YSIZE - 30));
    edgeMaintTabs = new javax.swing.JTabbedPane();
    UC.Look(edgeMaintTabs);
    edgeMaintTabs.setPreferredSize(new java.awt.Dimension(gov.usgs.anss.gui.UC.XSIZE, gov.usgs.anss.gui.UC.YSIZE - 30));
    utilitiesTabs = new javax.swing.JTabbedPane();
    UC.Look(utilitiesTabs);
    utilitiesTabs.setPreferredSize(new java.awt.Dimension(gov.usgs.anss.gui.UC.XSIZE, gov.usgs.anss.gui.UC.YSIZE - 30));
    masterTabs = new javax.swing.JTabbedPane();
    UC.Look(masterTabs);
    masterTabs.setPreferredSize(new java.awt.Dimension(gov.usgs.anss.gui.UC.XSIZE, gov.usgs.anss.gui.UC.YSIZE - 30));
    portableTabs = new javax.swing.JTabbedPane();
    UC.Look(portableTabs);
    portableTabs.setPreferredSize(new java.awt.Dimension(gov.usgs.anss.gui.UC.XSIZE, gov.usgs.anss.gui.UC.YSIZE - 30));

    // Add working file maintenance panels to the fileMaintTabs as new tabs
    fileMaintTabs.addTab("RTS Stations", rtsstation);
    fileMaintTabs.addTab("DAS parms", nsnstation);
    fileMaintTabs.addTab("Reftek", reftek);
    fileMaintTabs.addTab("TCPStations", tcpstation);
    fileMaintTabs.addTab("Q330 Serial", q330);
    fileMaintTabs.addTab("VSATs", vsat);
    fileMaintTabs.addTab("User Properties", userProperties);

    // Add working edge maintainancetabs to the edgeMainTabs as new tabs
    edgeMaintTabs.addTab("Command/Status", userCommand);
    edgeMaintTabs.addTab("RTS Console", console);

    // Add working Utility panels to the utilitiesTab as new tabs
    if (rtsUtility != null) {
      utilitiesTabs.addTab("RTS Utility", rtsUtility);
    }
    utilitiesTabs.addTab("CWBPlot", tsplot);

    // Add working master panels to masterTabs
    masterTabs.addTab("Roles", cpu);
    masterTabs.addTab("Comm Links", commlink);
    masterTabs.addTab("Role-Link IPs", cpulinkip);
    masterTabs.addTab("DAS Config", dasConfig);
    masterTabs.addTab("Remote Commands", remoteCommand);

    // Add to the portables tab
    portableTabs.addTab("Reftek", portableReftek);
    portableTabs.addTab("Deployment", portableDeployment);

    // reports Tab
    reportsPane = new javax.swing.JTabbedPane();
    UC.Look(reportsPane);
    reportsPane.setPreferredSize(new java.awt.Dimension(gov.usgs.anss.gui.UC.XSIZE, gov.usgs.anss.gui.UC.YSIZE - 30));

    // here the TabbedPanes are added to the Panel which encloses them.
    // The panels are added to other tabbedPanes
    files.add(fileMaintTabs);
    edge.add(edgeMaintTabs);
    utilities.add(utilitiesTabs);
    master.add(masterTabs);

    // Here add the panels with the sub-tabs to the main tabs
    MainTabs.addTab("File Maint", files);
    MainTabs.addTab("RTS/Edge Maint", edge);
    MainTabs.addTab("Utilities", utilitiesTabs);
    if (User.isRole('M')) {
      MainTabs.addTab("Master Files", masterTabs);
    }
    MainTabs.addTab("Reports", reportsPane);
    if (User.getUser().equalsIgnoreCase("mem") || User.isRole("M")) {
      MainTabs.addTab("Portables", portableTabs);
    }

    reports.setPreferredSize(gov.usgs.anss.gui.UC.XSIZE - 35, gov.usgs.anss.gui.UC.YSIZE - 300);
    reportsPane.addTab("Reports", reports);
    if (User.isRole('M')) {
      reportsPane.addTab("Define Reports", report);
    }
    reportsPane.addTab("Logs", logPanel);
    if (includeHoldingsReports) {
      reportsPane.addTab(GapReportPanel.TITLE, gapreport);
      /* reportsPane.addTab(MultiGapReportPanel.TITLE,multigapreport);*/
      reportsPane.addTab(MultiGapReportPanel.TITLE, newmgapreport);
    }

    // Add Logout tab to all menus
    Util.prt("logout added !!!!!!!!!!!!!!!!!!!!!!!");
    MainTabs.addTab("Logout", logout);

    // Main pannel gets the main tabs
    mainPanel.add(MainTabs);
    getContentPane().add(mainPanel);
    theContentPane = getContentPane();
    getContentPane().addComponentListener(new ComponentAdapter() {
      @Override
      public void componentResized(ComponentEvent e) {
        // This is only called when the user releases the mouse button.
        Util.prt("" + theContentPane.getSize());
        MainTabs.setSize(theContentPane.getSize());
        ((javax.swing.JPanel) theContentPane).validate();
        MainTabs.setPreferredSize(theContentPane.getSize());
        mainPanel.setSize(theContentPane.getSize());
        mainPanel.setPreferredSize(theContentPane.getSize());
        theContentPane.validate();

      }
    });
    //Util.prt("Add menus to content1 isVisible=" + mainPanel.isVisible());
    UC.setConnection(JDBConnection.getConnection("anss"));
    int was = ToolTipManager.sharedInstance().getDismissDelay();
    ToolTipManager.sharedInstance().setDismissDelay(100000);
    int is = ToolTipManager.sharedInstance().getDismissDelay();

  }

  /**
   * GIven a main tab "name" and an item in the sub-tabs, select that tab. This is used to select a
   * tab based on a popup menu item selection in the InvTree Panel. Often we need to flip between
   * "Tree View" and one of the functional Panes.
   *
   * @param m The main tab name as a String
   * @param s The sub - tab name as a String
   * @return If it worked.
   */
  public boolean setSelectedTab(String m, String s) {
    for (int i = 0; i < MainTabs.getTabCount(); i++) {
      if (MainTabs.getTitleAt(i).equalsIgnoreCase(m)) {
        Object jp = MainTabs.getComponentAt(i);
        if (jp instanceof JPanel) {
          Object sub = ((JPanel) jp).getComponent(0);

          if (sub instanceof JTabbedPane) {
            for (int j = 0; j < ((JTabbedPane) sub).getTabCount(); j++) {
              if (((JTabbedPane) sub).getTitleAt(j).equalsIgnoreCase(s)) {
                MainTabs.setSelectedIndex(i);
                ((JTabbedPane) sub).setSelectedIndex(j);
                //Util.prt("SetSelectedTabs "+m+"="+i+" sub="+s+"="+j);
                return true;
              }
            }
          } else {
            //Util.prt("SetSelectedTabs no sub set at "+i+" sub class="+sub.getClass().getName());
            MainTabs.setSelectedIndex(i);

            return true;
          }
        }
      }
    }

    return false;
  }

  private void saveProperties() {
    if (aFrame.getLocationOnScreen().getX() > -30000.) {
      Util.setProperty("StartX", "" + aFrame.getLocationOnScreen().getX());
    }
    if (aFrame.getLocationOnScreen().getY() > -30000.) {
      Util.setProperty("StartY", "" + aFrame.getLocationOnScreen().getY());
    }
    Util.saveProperties();
  }

  /**
   * The use of this window adapter to handle a "close" or exit is common. But the call to
   * InvTree.exitCleanup() allows that routine to check for unrecorded changes and prompt the user
   * for advice on whether to save.
   */
  static class WL extends WindowAdapter {

    @Override
    public void windowClosing(WindowEvent e) {
      if (theAnss != null) {
        theAnss.saveProperties();
      }
      if (e.getWindow() == aFrame) {
        System.exit(0);
      }
    }
  }

  public static void cleanup() {
  }

  public String getTitle() {
    return aFrame.getTitle();
  }

  public void setTitle(String s) {
    aFrame.setTitle(s);
  }

  public static void main(String[] args) {
    Util.setProcess("Anss");
    Util.init("anss.prop");
    UC.init();
    Util.setModeGMT();
    Util.addDefaultProperty("StartX", "10");
    Util.addDefaultProperty("StartY", "22");
    Util.addDefaultProperty("RTSServer", UC.RTS_SERVER_NODE);
    Util.addDefaultProperty("StatusServer", "localhost");
    Util.addDefaultProperty("CWBIP", "localhost ");
    Util.addDefaultProperty("PrinterFile", "edgecon.lpt");
    Util.addDefaultProperty("SessionFile", "SESSION.OUT");
    //Util.addDefaultProperty("MySQLServer","localhost");
    Util.addDefaultProperty("DBServer", "localhost/3306:anss:mysql:anss");
    if (Util.getProperty("DBServer") != null) {
      if (Util.getProperty("DBServer").contains("edge")) {
        Util.setProperty("DBServer", Util.getProperty("DBServer").replaceAll("edge", "anss"));
        Util.saveProperties();
        DBConnectionThread.init(Util.getProperty("DBServer"));
      }
    }
    Anss applet = new Anss();
    Util.setApplet(false);	// True= no disk output
    String OS = System.getProperty("os.name");
    Util.debug(true);		// false=session .out
    Util.prt("OS is " + OS);
    Util.prt("starting up");
    if (OS.equals("SunOS")) {
      Util.debug(true);
    }
    Util.prt(Util.now().toString());

    Util.loadProperties(UC.getPropertyFilename());
    aFrame = new Frame("Anss DB system");
    aFrame.addWindowListener(new WL());
    aFrame.add(applet, BorderLayout.CENTER);
    aFrame.setSize(gov.usgs.anss.gui.UC.XSIZE, gov.usgs.anss.gui.UC.YSIZE);
    aFrame.setLocation((int) Math.min(Math.max(0., Double.parseDouble(Util.getProperty("StartX"))), 2048.),
            (int) Math.min(Math.max(0., Double.parseDouble(Util.getProperty("StartY"))), 1024.));
    Util.prt("size=" + gov.usgs.anss.gui.UC.XSIZE + " " + gov.usgs.anss.gui.UC.YSIZE);
    applet.init();
    applet.start();
    aFrame.setVisible(true);
    while(true) {Util.sleep(2000);}
  }
}
