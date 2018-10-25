/*
 *  This software is in the public domain because it contains materials 
 *  that originally came from the United States Geological Survey, 
 *  an agency of the United States Department of Interior. For more 
 *  information, see the official USGS copyright policy at 
 *  http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.alarm;

import gov.usgs.anss.util.Util;
import gov.usgs.edge.alarm.Event;
import gov.usgs.anss.db.DBAccess;
import gov.usgs.anss.edgemom.EdgeChannelServer;
import gov.usgs.edge.alarm.Source;
import java.sql.Timestamp;

/**
 * This class is the super class to any inputer of events. Originally on the UDP
 * based UDPEventProcess extended this class but others are expected. It uses
 * the DBAccess to get information about known events. Through the findEvent()
 * static function it gives access to all of the events. Via handleEvent() it
 * gives a method to build and event and track it.
 *
 * @author davidketchum
 */
public class EventInputer extends Thread {

  // This list of events is set up to do the damping as redeclares come in
  protected boolean terminate;
  protected boolean noDB;
  protected static DBAccess access;
  private final Timestamp now = new Timestamp(3993939L);
  private final StringBuilder sql = new StringBuilder(100);

  //public static ArrayList<Event> getEvents() {return events;}
  /**
   * Creates a new instance of EventInputer - there should only be one!
   *
   * @param noDB If true, no databases are used
   */
  public EventInputer(boolean noDB) {
    this.noDB = noDB;
    access = DBAccess.getAccess();
  }

  public static String deq(String s) {
    // remove all backslashes and backslash any single quotes.
    return s.replaceAll("\\\\", "").replaceAll("\\'", "\\\\'").replaceAll("\\^", "~");
  }

  /**
   * given the raw strings that make up an event, handle its input to alarm.
   * This involves insuring the event is in the database, creating it if
   * necessary, and creating a clone of the generally event and updating same
   * with this particular instance data. The data are damped and events acted
   * upon via EventHandler.declareEvent()
   *
   * @param source The source string
   * @param code the code within the source that makes this event unique
   * @param phrase General text issued by the event
   * @param process The process associated with this instance of the event
   * @param node The computer node or other grouping (Line SNWGroup) associated
   * with this instance of the event
   * @return a clone of a general event record with the instance data set (node,
   * process and phrase, event time).
   */
  public Event handleEvent(String source, String code, String phrase, String process, String node) {
    // User has supplied the code, how unusual
    if (noDB) {
      Util.prt("**** HandleEvent in NoDB mode!!!!!!");
      return null;
    }
    Event evt = findEvent(source, code, phrase);

    // This is a new event.  Add it to the event table if it has a source
    String s = null;
    if (evt == null) {
      if (source.length() > 0) {
        Source src = null;
        for (int i = 0; i < access.getSourceSize(); i++) {
          if (source.trim().equalsIgnoreCase(access.getSource(i).getSource())) {
            src = access.getSource(i);
          }
        }
        if (src == null) {
          Util.prt(" **** At event creation could not find source=" + source);
        }
        if (Util.getNode().contains("ketchum")) {
          return evt;    // do not create on test system
        }
        phrase = ((source.contains("Sitescope") ? node.trim() + "|" + process.trim() + "|" : "")
                + phrase.trim()).substring(0, Math.min(80, phrase.trim().length()));
        Util.clear(sql).append("alarm^event^event=").
                append(source.substring(0, Math.min(12, source.trim().length()))).append("-").append(code.substring(0, Math.min(12, code.trim().length()))).
                append(";source=").append(source.trim()).
                append(";code=").append(code.trim()).
                append(";phrase=").append(deq(phrase.trim())).
                append(";damping=").append(src == null ? 2 : src.getDefaultDamping()).
                append(";damping2=").append(src == null ? 0 : src.getDefaultDamping2()).
                append(";updated=now();created=now();\n");
        if (!noDB) {
          Util.prta(" create event : " + sql);
          if (EdgeChannelServer.dbMessageQueue(sql)) {
            Util.prta("EI: dmsg update did not queue sql=" + sql);
          }
        }
      }
    } else {
      Util.prt("  EI: Found event=" + evt + " from src=" + source.trim() + "-" + code.trim()
              + " nd/prc=" + node.trim() + "/" + process.trim() + " pl=" + phrase.trim());
    }
    if (evt == null) {
      return null;
    }

    Event event = evt;
    if (System.currentTimeMillis() - event.getLastUpdated() > 600000) {
      evt.setLastUpdated(System.currentTimeMillis());
      now.setTime(System.currentTimeMillis());
      int row = access.getEventDBTable().findRowWithID(evt.getID());
      if (row >= 0) {
        access.getEventDBTable().updateString(row, "phrase", phrase.substring(0, Math.min(80, phrase.length())));
        access.getEventDBTable().updateTimestamp(row, "updated", now);
      }
      if (!noDB) {
        Util.clear(sql).append("alarm^event^id=").append(evt.getID()).append(";phrase=").append(phrase).append(";updated=now();\n");
        if (EdgeChannelServer.dbMessageQueue(sql)) {
          Util.prta("EI: dmsg update did not queue sql=" + sql);
        }
      }
      evt.setPhrase(phrase);
      Util.prta("EI: update event " + event + " to " + phrase.trim());
    }

    // check to see if damping will prevent an update here
    if (event.isDamped(node)) {
      Util.prt("  EI: Damped discard " + event.toString3());
    } else {
      try {
        Util.prta("  EI: evt went off " + event.toString3());
        Event clone = (Event) event.clone();    // make a clone for passing on 
        clone.setNode(node);
        clone.setProcess(process);
        clone.setPhrase(phrase);
        clone.setEventTime(System.currentTimeMillis());
        EventHandler.declareEvent(clone);
      } catch (Exception expected) {
      }
    }
    return event;
  }

  /**
   * Find an event given the source, code, and payload
   * <br> 1) If source and code are set, use them to look up the event
   * <br> 2) If source or code are blank, use regular expression
   *
   * @param src The source code to match
   * @param cd The code to match
   * @param payload The payload of the message
   * @return null if no match is found, else a Event object matching these
   * parameters.
   */
  public static Event findEvent(String src, String cd, String payload) {
    if (src.length() > 0 && cd.length() > 0) {
      for (int i = 0; i < access.getEventSize(); i++) {
        Event event = access.getEvent(i);
        if (event != null) {
          if (event.getSource().equalsIgnoreCase(src.trim()) && event.getCode().equalsIgnoreCase(cd.trim())) {
            return event;
          }
        }
      }
      return null;
    } else {
      for (int i = 0; i < access.getEventSize(); i++) {
        Event event = access.getEvent(i);
        if (event != null) {
          String re = event.getRegexp();
          if (re.length() > 0) {
            if (payload.matches(re)) {
              return event;
            }
          }
        }
      }
    }
    return null;
  }
}
