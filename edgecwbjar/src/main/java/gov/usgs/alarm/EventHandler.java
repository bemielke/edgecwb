/*
 *  This software is in the public domain because it contains materials 
 *  that originally came from the United States Geological Survey, 
 *  an agency of the United States Department of Interior. For more 
 *  information, see the official USGS copyright policy at 
 *  http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.alarm;

import gov.usgs.edge.alarm.Target;
import gov.usgs.edge.alarm.Subscription;
import gov.usgs.edge.alarm.Source;
import gov.usgs.edge.alarm.Event;
import gov.usgs.anss.util.SimpleSMTPThread;
import gov.usgs.anss.util.Util;
import gov.usgs.anss.db.DBAccess;
import gov.usgs.anss.edgemom.EdgeChannelServer;
import java.sql.Timestamp;
import java.util.*;

/**
 * EventHandler - accepts static declaration of events, creates event handlers
 * for each instance of an event (this includes node and process). Each instance
 * is a thread waiting for a doit() to fire off the processing of the event.
 * When it fires, the full event processing is done dispatching e-mail, pages,
 * etc per the other tables (subscription, schedule, alias).
 *
 * @author davidketchum
 */
public final class EventHandler extends Thread {

  private static SimpleTimeZone denverTimeZone;
  private static final TreeMap<String, EventHandler> handlers
          = new TreeMap<>();
  private final String key;
  private Event event;
  private Event eventLast;
  private long lastEventReceived;
  //private static long lastSourceReload;
  private final String tag;
  private final GregorianCalendar now = new GregorianCalendar();
  private static boolean noAction;
  private static Alarm alarm;
  private static DBAccess access;

  public static void setAlarm(Alarm a) {
    alarm = a;
  }

  public static void setNoAction(boolean b) {
    noAction = b;
    Util.prt("EventHandler no action set=" + b);
  }

  @Override
  public String toString() {
    return tag + " evt=" + key + " evt=" + eventLast.toString3();
  }

  /**
   * return an array list of all of the event handlers
   *
   * @return the Array List
   */
  public static TreeMap<String, EventHandler> getHandlers() {
    return handlers;
  }

  /**
   * return specially formatted monitor text
   *
   * @return The text
   */
  public String getMonitorText() {
    return tag + event.getMonitorText() + "lastEventRecSec=" + (System.currentTimeMillis() - lastEventReceived) / 1000;
  }

  /**
   * declareEvent either creates an event hander for this event, or it updates a
   * reoccurance of this event to the handler via doit(). It is only through
   * this static method that EventHandlers are created.
   *
   * @param evt The event to start an handler for
   */
  public synchronized static void declareEvent(Event evt) {

    // get handler, if it has never been created, create it, add it to static handlers.
    EventHandler e = handlers.get(evt.eventKey());  // keys are source/code/node/process
    if (e == null) {
      synchronized (handlers) {
        e = new EventHandler(evt);
        handlers.put(evt.eventKey(), e);
      }
      Util.prta("EH: Create new event handler for " + evt.eventKey());
    }
    Util.prta("EH: Event handler selected=" + e.toString());
    e.doIt(evt);
  }

  /**
   * Creates a new instance of EventHandler
   *
   * @param evt An event to create this handler for
   */
  public EventHandler(Event evt) {
    event = evt;
    eventLast = event;
    key = evt.eventKey();
    tag = "EH:[" + getName().substring(getName().indexOf("-") + 1) + "-" + key + "]:";
    if (access == null) {
      while (!EdgeChannelServer.isValid()) {
        try {
          sleep(100);
        } catch (InterruptedException expected) {
        }
      }
      access = EdgeChannelServer.getDBAccess();
    }
    start();
  }

  /**
   * cause the eventHandler to execute one cycle for this event.
   */
  private void doIt(Event evt) {
    event = evt;
    eventLast = event;
    Util.prta(tag + "EH: Doit called for event=" + evt.toString4());
  }

  /**
   * process the event, find subscribers, send stuff to notification objects
   * <br> 1) Find all of the subscribers for an event (by source or event code)
   * <br> 2) For each subscriber :
   * <br> Check that the node and process regexp pass muster if present
   * <br> Do the event
   * <br> 3) If no subscribers are found, process event using the source
   * defaults (panic or emergency defaults)
   */
  @Override
  public void run() {
    for (;;) {
      try {
        // Check for suppression
        if (event != null) {
          Util.prta(tag + "Event Handler has started");
          lastEventReceived = System.currentTimeMillis();   // get current time
          Source source = null;
          for (int i = 0; i < access.getSourceSize(); i++) {
            if (access.getSource(i).getSource().equalsIgnoreCase(event.getSource())) {
              source = access.getSource(i);
              if (source.suppressAll()) {
                Util.prta("***** event=" + event + " is suppressed by entire source being suppressed until "
                        + source.suppressAll());
                event = null;
                continue;
              }
              break;
            }
          }
          if (source == null) {
            SimpleSMTPThread.email(Util.getProperty("emailTo"), "_Alarm source unknown to source table=" + event.getSource(),
                    "This message comes from Alarm EventHandler when the default lookup for a source for action is undefined\n"
                    + Util.getIDText() + "\n");
            SendEvent.edgeSMEEvent("AlmUnknSrc", event.getSource() + " is an unknown source to Alarm " + Util.getNode() + "/" + Util.getAccount(), this);
            event = null;
            continue;
          }

          // We need to get suppression parameters from the overall event which is kept up-to-date by EventInputer
          Event chkEvent = EventInputer.findEvent(event.getSource(), event.getCode(), "");
          if (chkEvent == null) {
            Util.prta(tag + "check suppression chkEvent=null Skip suppression");
          } else {
            Util.prta(tag + "check suppression perm=" + chkEvent.getSuppressNodeRegExpPerm() + " RE=" + chkEvent.getSuppressNodeRegExp()
                    + "| evt=" + event + " evt node=" + event.getNode() + "| matchperm=" + event.getNode().matches(chkEvent.getSuppressNodeRegExpPerm()));
            if (chkEvent.getSuppressNodeRegExpPerm().length() > 0) {      // Suppression on specific nodes (usually test nodes)
              if (event.getNode().trim().matches(chkEvent.getSuppressNodeRegExpPerm().trim())) {
                Util.prta("***** event=" + event + " is suppress by CPU for this node PERM re=" + chkEvent.getSuppressNodeRegExpPerm());
                event = null;
                continue;
              }
            }
            if (chkEvent.getSuppressNodeRegExp().length() > 0) {      // Suppression on specific nodes (usually test nodes)
              Util.prta(tag + "Event regexp found node=" + event.getNode() + "|RE=" + chkEvent.getSuppressNodeRegExp() + "| match="
                      + event.getNode().trim().matches(chkEvent.getSuppressNodeRegExp().trim()));
              if (event.getNode().trim().matches(chkEvent.getSuppressNodeRegExp().trim())) {
                if (chkEvent.getSuppressUntil().getTime() > System.currentTimeMillis()) {
                  Util.prta(tag + "***** event=" + event + " is suppress by CPU for this node re=" + chkEvent.getSuppressNodeRegExp()
                          + " until " + chkEvent.getSuppressUntil());
                  event = null;
                  continue;
                } else {
                  Util.prta(tag + "***** event=" + event + "+ is not suppressed for CPU on node re=" + chkEvent.getSuppressNodeRegExp());
                }
              }
            } else if (chkEvent.getSuppressUntil().getTime() > System.currentTimeMillis()) { // suppression til dates specific!
              Util.prta(tag + "***** event=" + event + " is suppressed until " + chkEvent.getSuppressUntil().toString());
              event = null;
              continue;
            }
          }

          ArrayList<Subscription> subscriptions = new ArrayList<>(100);
          int nsub = 0;
          int nsubscribers = 0;
          synchronized (handlers) {  // surrogate for dbconn which cannot be final
            Util.prt(tag + "  Find subscribers eventid=" + event.getID() + " " + event.toString2());
            for (int i = 0; i < access.getSubscriptionSize(); i++) {
              Subscription sub = access.getSubscription(i);
              if (sub.getEventID() == event.getID()
                      || (sub.getSource().equals(event.getSource()) && sub.getEventID() == 0)) {
                subscriptions.add(nsub++, sub);
              }

            }

            Util.prta("Number of subscriptins found=" + nsub);
            for (int i = 0; i < nsub; i++) {
              Subscription subscription = subscriptions.get(i);
              Util.prt(tag + "  Check subscription=" + subscription.toString3());
              // If the user has a node regular expression, test it
              if (subscription.getNodeRegexp().trim().length() > 0) {
                if (event.getNode() != null) {
                  if (!event.getNode().matches(subscription.getNodeRegexp())) {
                    Util.prt("    Fail node regexp");
                    continue;
                  }
                }
              }
              // If the user has a process regular expression, test it
              if (subscription.getProcessRegexp().trim().length() > 0) {
                if (event.getProcess() != null) {
                  if (!event.getProcess().matches(subscription.getProcessRegexp())) {
                    Util.prt("    Fail process regexp");
                    continue;
                  }
                }
              }
              nsubscribers++;     // count things that happen to eliminate default mode
              doSubscription(subscription, event);    // Send this event to subscriber

            }

            // If no-one got anything for this event, then go back to the source defaults
            if (nsubscribers == 0) {
              Util.prt(tag + "  NO SUBSCRIBERS - take action based on source " + event.getSource());
              Subscription tmp = new Subscription(0, "TEMP", source.getSource(),
                      0, source.getDefaultTargetID(), source.getAction(),
                      Timestamp.valueOf("2000-01-01 00:00:00"), Timestamp.valueOf("2000-01-01 23:59:59"),
                      source.getAction(), "", "");
              doSubscription(tmp, event);
              Util.prt(tag + "  ****** Notify extreme default:" + source + " target=" + Target.getItemWithID(source.getDefaultTargetID())
                      + " event=" + event);
            }
          }

          event = null;
        } // event != null
        else {
          try {
            sleep(200);
          } catch (InterruptedException expected) {
          }       // Nothing to do, just wait and spin
        }
      } catch (RuntimeException e) {
        if (e != null) {
          if (e.getMessage() != null) {
            Util.prta(tag + "RuntimeException in EventHandler e=" + e.getMessage());
          } else {
            Util.prta(tag + "RuntimeException in Event handler has null message e=" + e);
          }
          e.printStackTrace();
        } else {
          Util.prta(tag + "RuntimeException in Eventhander is null");
        }
        event = null;
        try {
          sleep(200);
        } catch (InterruptedException expected) {
        }       // Nothing to do, just wait and spin
      }
    }     // end of forever loop
    //Util.prt(tag+"Loop exiting!");
  }

  /**
   * Process the subscription and send out resulting e-mail or pages. This is
   * the main work for the event handler.
   * <br> 1) Get the "target" for the subscription
   * <br> 2) If it is an alias, then it might be "scheduled", check scheduling
   * system (this might result in new "on duty" target).
   * <br> 3) If the target is still an alias, then call alias to get a list of
   * physical targets to hit.
   * <br> 4) Check the time of day against window in subscription and select the
   * appropriate action
   * <br> 5) Page, e=mail, ignore as the actions says.
   *
   * @param subscription The individual subscription record to act upon
   * @param event The event causing this subscription to go off.
   */
  private void doSubscription(Subscription subscription, Event event) {
// Get the target ID, add to list of used targets so far
    int targetID = subscription.getTargetID();

    // We need to calculate the timeInMillis since midnight in the local time zone.
    now.setTimeInMillis(System.currentTimeMillis());
    Util.prt(tag + "    Subscription=" + subscription.toString3() + " event=" + event.toString());
    if (denverTimeZone == null) {
      denverTimeZone = new SimpleTimeZone(
              -7 * 3600000, // Normally 7 hours earlier than GMT
              "America/Denver",
              Calendar.MARCH, 8, -Calendar.SUNDAY, 7200000, // 2nd Sunday in March at 2:00 am
              Calendar.NOVEMBER, 1, -Calendar.SUNDAY, 7200000, // first sunday in November
              3600000);   // Jump one hour ahead.
    }
    now.setTimeZone(denverTimeZone);
    long timeInMillis = now.get(Calendar.HOUR_OF_DAY) * 3600000L + now.get(Calendar.MINUTE) * 60000L + now.get(Calendar.SECOND) * 1000L;
    Util.prt(tag + "    to local time offset=" + denverTimeZone.getOffset(System.currentTimeMillis()) + " DST offset=" + denverTimeZone.getDSTSavings()
            + " now=" + System.currentTimeMillis() + " timeComputed=" + timeInMillis + " " + format(timeInMillis));
    while (true) {

      Target target = null;
      for (int i = 0; i < access.getTargetSize(); i++) {
        if (access.getTarget(i).getID() == targetID) {
          target = access.getTarget(i);
          break;
        }
      }
      if (target == null) {
        Util.prt("Cannot find target matching ID=" + targetID + " in subscription " + subscription);
        return;
      }

      int[] targetIDs;
      targetIDs = new int[1];
      targetIDs[0] = targetID;
      // If this is still an alias we need to process all of its incarations and do the pages.
      // TODO: there is no support for target aliases since rework to put MySQL into Alarm

      // We need to decide on the action to take based on the time of day, this is start time less any time difference to time zone now
      long start = (subscription.getStartTime().getTime()/*+denverTimeZone.getOffset(now.getTimeInMillis())*/) % 86400000L;
      long end = (subscription.getEndTime().getTime()/*+denverTimeZone.getOffset(now.getTimeInMillis())*/) % 86400000L + 999L; // only reports second so round up fraction
      int action;
      String actionText = "Unknown";
      if (timeInMillis >= start && timeInMillis < end) {
        action = subscription.getAction();
        Util.prt(tag + "    Time is IN window " + Util.asctime() + " UTC " + timeInMillis + "->LOCAL " + format(timeInMillis) + " "
                + format(start) + " to " + format(end));
      } else {
        action = subscription.getOtherwiseAction();
        Util.prt(tag + "    Time is OUT OF window " + Util.asctime() + " UTC " + timeInMillis + "->LOCAL " + format(timeInMillis) + " "
                + format(start) + " to " + format(end));
      }
      if ((event.getFlags() & Event.EMAIL_ONLY) != 0) {
        action = 1;
        Util.prt("Event is overriden to EMAIL_ONLY");
      }
      for (int i = 0; i < access.getActionSize(); i++) {
        if (access.getAction(i).getID() == action) {
          actionText = access.getAction(i).getAction();
        }
      }

      Util.prt(tag + "    time calc:start=" + start + " now=" + timeInMillis + " end=" + end
              + " action=" + action + " " + actionText + " in hours=" + subscription.getAction()
              + " otherwise=" + subscription.getOtherwiseAction());

      // TargetIDs contains a list of real targets to page - do the paging
      for (int i = 0; i < targetIDs.length; i++) {
        if (targetIDs[i] > 0) {

          // TODO : should be replace with getting Target from the Panel so they cando damping
          for (int j = 0; j < access.getTargetSize(); j++) {
            if (access.getTarget(j).getID() == targetIDs[i]) {
              target = access.getTarget(j);
              break;
            }
          }

          // Are we in the range for the subscription
          Util.prt(tag + "     *** Notify : "
                  + " mthd=" + actionText + "  " + subscription.getStartTime().toString().substring(11) + " to "
                  + subscription.getEndTime().toString().substring(11) + " "
                  + target.toString2() + " target " + target.getEmailAddress() + " ev=" + event + " pg="
                  + target.getPagerText() + " by " + target.getProcess()
                  + " node=" + subscription.getNodeRegexp() + " prc=" + subscription.getProcessRegexp());
          String subject = event.toString();
          if (event.getSource().contains("Sitescope")) {
            subject += " " + event.getNode() + "/" + event.getProcess();
          }
          if (action == 1 || action == 4) {   // Email
            // this should be replaced by a target.email(event, subscription)
            if (!Util.getNode().contains("ketchum")) {
              if (!noAction) {
                SimpleSMTPThread.email(target.getEmailAddress().trim(), "_" + subject + " " + event.getPhrase().substring(0, Math.min(30, event.getPhrase().length())),
                        Util.ascdate() + " " + Util.asctime() + " node=" + event.getNode().trim() + " process=" + event.getProcess().trim()
                        + "\nevent=" + event + " " + event.getPhrase().trim() + "\n ");
              }
              Util.prta(tag + (noAction ? "t" : "f") + "  *** EMAIL away " + target.getTarget().trim() + " via " + Util.getProperty("SMTPServer") + "/" + Util.getProperty("SMTPFrom")
                      + " email=" + target.getEmailAddress().trim() +/*" "+
                      event.toString()+*/ " ph='" + event.getPhrase().substring(0, Math.min(30, event.getPhrase().length())) + "'");
            }
          }
          if (action == 2 || action == 4) {    // page
            if (target.getProcess().equals("") || target.getProcess().equalsIgnoreCase("Pager")) {
              if (!Util.getNode().contains("ketchum")) {
                String body = " " + event.getNode().trim() + "/" + event.getProcess().trim()
                        + " " + event.getPhrase().trim() + " at " + Util.ascdate().substring(5) + " " + Util.asctime().substring(0, 5) + " UTC";
                if (body.length() > 200) {
                  body = event + " " + " on " + event.getNode().trim() + "/" + event.getProcess().trim()
                          + " " + event.getPhrase().trim().substring(0, event.getPhrase().length() - (body.length() - 200))
                          + " at " + Util.ascdate().substring(5) + " " + Util.asctime().substring(0, 5) + " UTC";
                }
                body = body.replaceAll("\\n", "^");
                Util.prta(tag + (noAction ? "t" : "f") + "  *** PAGE away " + target.getTarget().trim() + " via "
                        + Util.getProperty("SMTPServer") + "/" + Util.getProperty("SMTPFrom")
                        +//" "+event.toString()+
                        " pagerText=" + target.getPagerText().trim() + " body=" + body);
                if (!noAction) {
                  SimpleSMTPThread page = SimpleSMTPThread.emailAlt(target.getPagerText().trim(), event.toString(), body);
                  if (!page.getSendMailError().trim().equals("")) {
                    Util.prt(tag + "Page err " + page.getSendMailError());
                  }
                }
              }
            } else {
              Util.prt(tag + "    *** PAGE Not yet implement " + target.getTarget() + "process=" + target.getProcess()
                      + " pagerText=" + target.getPagerText() + " evnt=" + event);
            }
          } else if (action == 3) {
            Util.prt(tag + "    **** IGNORE action on " + event + " " + event.getPhrase().trim() + " "
                    + event.getNode().trim() + "/" + event.getProcess().trim() + " to " + target);
          }
        }
      }
      break;
    }
  }

  private String format(long s) {
    long hr = s / 3600000L;
    s = s % 3600000L;
    long min = s / 60000L;
    s = s % 60000L;
    s = s / 1000l;
    return Util.df2(hr) + ":" + Util.df2(min) + ":" + Util.df2(s);
  }

  /**
   * main test
   *
   * @param args the args
   */
  public static void main(String[] args) {
    Util.setModeGMT();
    Util.init("edge.prop");

    Alarm alarm2 = new Alarm("-nocfg -noudpchan -nodbmsg -noaction", "alarm");
    EventHandler.setAlarm(alarm2);
    while (!EdgeChannelServer.isValid()) {
      Util.sleep(100);
    }
    try {
      //Event evt = EventPanel.getItemWithID(48);
      DBAccess access2 = EdgeChannelServer.getDBAccess();
      Event evt = access2.getEvent(48);
      evt = (Event) evt.clone();
      //Event evt = new Event(0,"SNW-Test","SNW","Test", "This is the test phrase","", 5, 0, 0, 0, 0);
      evt.setNode("NodeName");
      evt.setProcess("ProcName");
      declareEvent(evt);
    } catch (CloneNotSupportedException e) {
      e.printStackTrace();
    }
  }
}
