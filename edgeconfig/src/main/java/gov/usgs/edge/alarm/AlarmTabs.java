/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.edge.alarm;

import gov.usgs.anss.util.UC;
import javax.swing.JPanel;

/**
 * AlarmTabs.java - Provides the JFrame on which various alarm related JPanels are hung.
 *
 * NOTE: The presumption is that all Timestamps are in UTC time. So the user caller of this program
 * must have done a Util.setModeGMT(). If this is not done times will appear on the screen will
 * actually be adjusted by the UTC offset (6 or 7 hours in Golden), and the time calculations in the
 * Alarm program that rely on the UTC will not work as expected!
 *
 * Created on July 23, 2007, 5:04 PM
 *
 * @author davidketchum
 */
public final class AlarmTabs extends JPanel {

  private final javax.swing.JTabbedPane alarmTabs;
  private final ActionPanel action;
  private final AliasPanel alias;
  private final EventPanel event;
  private final SourcePanel source;
  private final TargetPanel target;
  private final SubscriptionPanel subscription;
  private final SchedulePanel schedule;

  /**
   * Creates a new instance of AlarmTabs - only one of these should be created!
   */
  public AlarmTabs() {
    alarmTabs = new javax.swing.JTabbedPane();
    UC.Look(alarmTabs);
    alarmTabs.setPreferredSize(new java.awt.Dimension(UC.XSIZE, UC.YSIZE - 30));
    // Build up the Panels
    action = new ActionPanel();
    alias = new AliasPanel();
    event = new EventPanel();
    source = new SourcePanel();
    target = new TargetPanel();
    subscription = new SubscriptionPanel();
    schedule = new SchedulePanel();
    Look();

    // add the SNW tabs
    alarmTabs.addTab("Subscriptions", subscription);
    alarmTabs.add("Schedule", schedule);
    alarmTabs.addTab("Events", event);
    alarmTabs.addTab("Targets", target);
    alarmTabs.addTab("Aliases", alias);
    alarmTabs.addTab("Source", source);
    alarmTabs.addTab("Action", action);
    this.add(alarmTabs);

  }

  private void Look() {
    UC.Look(this);                    // Set color background

  }
}
