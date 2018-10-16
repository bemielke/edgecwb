/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.edge.alarm;

import gov.usgs.anss.db.DBConnectionThread;
import gov.usgs.anss.util.Util;
import gov.usgs.anss.dbtable.DBTable;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.TreeMap;

/**
 * Event.java If the name of the class does not match the name of the field in the class you may
 * need to modify the rs.getSTring in the constructor.
 *
 * This Event templace for creating a MySQL database object. It is not really needed by the
 * EventPanel which uses the DBObject system for changing a record in the database file. However,
 * you have to have this shell to create objects containing a MySQL record for passing around and
 * manipulating. There are two constructors normally given:
 * <br>
 * 1) passed a result set positioned to the MySQL record to decode
 * <br> 2) Passed all of the data arguments needed to build the same set of data.
 * <br>
 * The Event should be replaced with capitalized version (class name) for the file. event should be
 * replaced with the lower case version (for variables).
 *
 * Both the constructors use the "makeEvent(list of data args) to set the local variables to the
 * value. The result set just uses the rs.get???("fieldname") to get the data from the database
 * where the other passes the arguments from the caller.
 *
 * <br> Notes on Enums :
 * <br> * data class should have code like :
 * <br> import java.util.ArrayList; /// Import for Enum manipulation
 * <br> .
 * <br> static ArrayList fieldEnum; // This list will have Strings corresponding to enum
 * <br> .
 * <br> .
 * <br> // To convert an enum we need to create the fieldEnum ArrayList. Do only once(static)
 * <br> if(fieldEnum == null) fieldEnum = FUtil.getEnum(UC.getConnection(),"table","fieldName");
 * <br>
 * <br> // Get the int corresponding to an Enum String for storage in the data base and
 * <br> // the index into the JComboBox representing this Enum
 * <br> localVariable = FUtil.enumStringToInt(fieldEnum, enumString);
 * <br> Created on July 8, 2002, 1:23 PM
 *
 * @author D.C. Ketchum
 */
public final class Event implements Cloneable //implements Comparator
{   // static ArrayList enumName;   // this need to be populated in makeEvent()

  public static ArrayList<Event> events;             // Vector containing objects of this Event Type
  public static final int EMAIL_ONLY = 1;      // When set, this event can only cause E-mails
  /**
   * Creates a new instance of Event
   */
  private int ID;                   // This is the MySQL ID (should alwas be named "ID"
  private String event;             // This is the main key which should have same name
  // as the table which it is a key to.  It is a concatenation
  // of the source and error code separated by dash.
  private String source;          // Where this came from (main name space like Hydra or edge)
  private String code;            // An error code with source it must be unique
  private String phrase;          // Some text to use about this error
  private String regexp;          // The regular expression match if any
  private int damping;            // first damping variable (minimum time between actions in minutes)
  private int damping2;           // 2nd damping variable (persistent time(arm on first, trigger on first after damping2 and before damping2*2)
  private int escalationID;       // primary escalation ID
  private int escalationID1;      // secodary escalation method
  private int escalationID2;      // tertiary escalation method

  // Additional fields that might be stored here if this represent a particular event rather than just the DB entry
  private String node;            // Computer sending the event
  private String process;         // A process sending the event
  private long eventTime;         // Set the event time and date
  private int flags;              // Flags 
  private long lastUpdated;
  private Timestamp suppressUntil;
  private String suppressReason;
  private String suppressNodeRegExp;
  private String suppressNodeRegExpPerm;

  private int total;
  private int nfires;
  private TreeMap<String, Damping> damps;

  // USER: All fields of file go here
  //  double longitude
  // Put in correct detail constructor here.  Use makeEvent() by filling in all
  // the rs.get???("fieldName") for all of the arguments
  /**
   * Create a instance of this table row from a positioned result set
   *
   * @param rs The ResultSet already SELECTED
   * @exception SQLException SQLException If the ResultSet is Improper, network is down, or database
   * is down
   */
  public Event(ResultSet rs) throws SQLException {  //USER: add all data field here
    makeEvent(rs.getInt("ID"), rs.getString("event"),
            rs.getString("source"), rs.getInt("flags"), rs.getString("code"), rs.getString("phrase"), rs.getString("regularexpression"),
            rs.getInt("damping"), rs.getInt("damping2"), rs.getInt("escalationMethodID"),
            rs.getInt("escalationMethodID1"), rs.getInt("escalationMethodID2"), // ,rs.getDouble(longitude)
            rs.getTimestamp("suppressUntil"), rs.getString("suppressReason"), rs.getString("suppressNodeRegExp"),
            rs.getString("suppressNodeRegExpPerm")// ,rs.getDouble(longitude)
    );

  }

  /**
   * Update an instance of this table row from a positioned result set
   *
   * @param rs The ResultSet already SELECTED
   * @exception SQLException SQLException If the ResultSet is Improper, network is down, or database
   * is down
   */
  public void updateEvent(ResultSet rs) throws SQLException {  //USER: add all data field here
    makeEvent(rs.getInt("ID"), rs.getString("event"),
            rs.getString("source"), rs.getInt("flags"), rs.getString("code"), rs.getString("phrase"), rs.getString("regularexpression"),
            rs.getInt("damping"), rs.getInt("damping2"), rs.getInt("escalationMethodID"),
            rs.getInt("escalationMethodID1"), rs.getInt("escalationMethodID2"), // ,rs.getDouble(longitude)
            rs.getTimestamp("suppressUntil"), rs.getString("suppressReason"), rs.getString("suppressnoderegexp"),
            rs.getString("suppressNodeRegExpPerm")// ,rs.getDouble(longitude)
    );

  }

  /**
   * Reload an instance of this table row from a positioned result set
   *
   * @param rs The ResultSet already SELECTED
   * @exception SQLException SQLException If the ResultSet is Improper, network is down, or database
   * is down
   */
  public void reload(ResultSet rs) throws SQLException {
    updateEvent(rs);
  }

  /**
   * Update an instance of this table row from a positioned result set
   *
   * @param rs The ResultSet alreay SELECTED
   * @exception SQLException SQLException If the ResultSet is Improper, network is down, or database
   * is down
   */
  public final void reload(DBTable rs) throws SQLException {  //USER: add all data field here
    makeEvent(rs.getInt("ID"), rs.getString("event"),
            rs.getString("source"), rs.getInt("flags"), rs.getString("code"), rs.getString("phrase"), rs.getString("regularexpression"),
            rs.getInt("damping"), rs.getInt("damping2"), rs.getInt("escalationMethodID"),
            rs.getInt("escalationMethodID1"), rs.getInt("escalationMethodID2"), // ,rs.getDouble(longitude)
            rs.getTimestamp("suppressUntil"), rs.getString("suppressReason"), rs.getString("suppressnoderegexp"),
            rs.getString("suppressNodeRegExpPerm")// ,rs.getDouble(longitude)
    );

  }

  /**
   * get millisof last time this event was updated/created
   *
   * @return The millis since last update or creation of this event
   */
  public long getLastUpdated() {
    return lastUpdated;
  }

  public void setLastUpdated(long t) {
    lastUpdated = t;
  }

  // Detail Constructor, match set up with makeEvent(), this argument list should be
  // the same as for the result set builder as both use makeEvent()
  /**
   * create a row record from individual variables
   *
   * @param inID The ID in the database, normally zero for this type of construction
   * @param loc The key value for the database (same a name of database table)
   * @param src The source value
   * @param flg The flags (email only, test, etc.)
   * @param cd The 12 letter event code
   * @param phr The phase for this event
   * @param re The regular expression match against the phrase (unusual!)
   * @param damp The damping in minutes (min time between events)
   * @param damp2 The damping persistence time in minutes
   * @param esID Escalation ID 1
   * @param esID1 Escalation ID 2
   * @param esID2 Escalation ID 3
   * @param su Supression until date (end date for a suppression)
   * @param snre The regular expression for matching nodes in s suppression
   * @param sr The reason for the suppression
   *
   */
  public Event(int inID, String loc, //USER: add fields, double lon
          String src, int flg, String cd, String phr, String re, int damp, int damp2, int esID, int esID1, int esID2,
          Timestamp su, String sr, String snre//USER: add fields, double lon
  ) {
    makeEvent(inID, loc, //, lon
            src, flg, cd, phr, re, damp, damp2, esID, esID1, esID2, su, sr, snre, ""
    );
  }

  /**
   *
   * @param rs Create an event from a DB table
   * @throws java.sql.SQLException
   */
  public Event(DBTable rs) throws SQLException {
    reload(rs);

  }

  // This routine transfers to the local variables of this object all of the data variables
  // associated with this file.
  /**
   * internally set all of the field in our data to the passed data
   *
   * @param inID The row ID in the database
   * @param loc The key (same as table name)
   *
   */
  private void makeEvent(int inID, String loc,
          String src, int flg, String cd, String phr, String re, int damp, int damp2, int esID, int esID1, int esID2,
          Timestamp su, String sr, String snre, String snperm//USER: add fields, double lon
  ) {
    ID = inID;
    event = loc;     // longitude = lon
    //USER:  Put asssignments to all fields from arguments here
    source = src;
    code = cd;
    phrase = phr;
    regexp = re;
    damping = damp;
    damping2 = damp2;
    escalationID = esID;
    escalationID1 = esID1;
    escalationID2 = esID2;
    //lastUpdated = System.currentTimeMillis();
    suppressUntil = su;
    suppressReason = sr;
    suppressNodeRegExp = snre;
    suppressNodeRegExpPerm = snperm;
    flags = flg;
    // ENUM example:
    //if(qtypeEnum == null) qtypeEnum=FUtil.getEnum(UC.getConnection(), "nsnstation","quanterraType");
    //qtype = FUtil.enumStringToInt(qtypeEnum, st); 
    // Boolean example where ENUM "True", "False
    // cont40 = FUtil.isTrue(c40);          // c40 is a string with "true" or false

  }

  /**
   * return the key name as the string
   *
   * @return the key name
   */
  public String toString2() {
    return event;
  }

  /**
   * return the key name as the string
   *
   * @return the key name
   */
  @Override
  public String toString() {
    return source.trim() + "-" + code.trim();
  }

  /**
   * get full description of the state of this event
   *
   * @return the full description
   */
  public String toString3() {
    long curr = System.currentTimeMillis();
    String s = toString2() + " damp=" + (damping * 60) + "s damp2=" + (damping2 * 60) + "s #rcv=" + total + " #fire=" + nfires;
    if (damps != null) {
      Iterator<Damping> itr = damps.values().iterator();
      while (itr.hasNext()) {
        Damping d = itr.next();
        s += "\n     Damping: " + toString2() + " " + d.toString();
      }
    }
    return s;
  }

  public String toString4() {
    return toString() + " " + node.trim() + "/" + process.trim() + " " + phrase.trim();
  }

  public String getMonitorText() {
    StringBuilder sb = new StringBuilder(100);
    if (damps != null) {
      Iterator<Damping> itr = damps.values().iterator();
      while (itr.hasNext()) {
        Damping d = itr.next();
        if (d.isActive()) {
          sb.append("eventID=").append(ID).append("|node=").append(d.getNode().trim()).append("|source=").append(source.trim()).append("|code=").append(code.trim()).append("|").append(d.toString()).append("\n");
        }
      }
    }
    return sb.toString();
  }
// getter

  // standard getters
  /**
   * get the database ID for the row
   *
   * @return The database ID for the row
   */
  public int getID() {
    return ID;
  }

  /**
   * get the key name for the row
   *
   * @return The key name string for the row
   */
  public String getEvent() {
    return event;
  }

  //USER:  All field getters here
  //  public double getLongitude() { return longitude;}
  public String getSource() {
    return source;
  }

  public String getCode() {
    return code;
  }

  public String getPhrase() {
    return phrase;
  }

  public String getRegexp() {
    return regexp;
  }

  public int getDamping() {
    return damping;
  }

  public int getDamping2() {
    return damping2;
  }

  public int getEscalationID() {
    return escalationID;
  }

  public int getEscalationID1() {
    return escalationID1;
  }

  public int getEscalationID2() {
    return escalationID2;
  }

  public Timestamp getSuppressUntil() {
    return suppressUntil;
  }

  public String getSuppressReason() {
    return suppressReason;
  }

  public String getSuppressNodeRegExp() {
    return suppressNodeRegExp;
  }

  public String getSuppressNodeRegExpPerm() {
    return suppressNodeRegExpPerm;
  }

  // get/set other data (from a event instance rather than db
  public String getNode() {
    return node;
  }

  public String getProcess() {
    return process;
  }

  public void setNode(String p) {
    node = p;
  }

  public void setProcess(String p) {
    process = p;
  }

  public void setPhrase(String p) {
    phrase = p;
  }

  public void setEventTime(long t) {
    eventTime = t;
  }

  public long getEventTime() {
    return eventTime;
  }

  public int getFlags() {
    return flags;
  }

  public String eventKey() {
    return source.trim() + "-" + code.trim() + "-" + node.trim() + "/" + process.trim();
  }

  // This is needed if you need to implement Comparator
  /*  public class EquipCompare implements Comparator {
    public int compare(Object a, Object b) {
      return ((Event) a).getEvent().compareTo(((Event) b).getEvent());
    }
    public boolean equals(Object a, Object b) {
      if( ((Event)a).getEvent().equals( ((Event) b).getEvent())) return true;
      return false;
    }
//  }*/
  @Override
  public Object clone() throws CloneNotSupportedException {
    try {
      Object o = super.clone();
      return o;
    } catch (CloneNotSupportedException e) {
      Util.prt("Clone not supported to event=" + e);
      e.printStackTrace();
    }
    return null;
  }

  /**
   * Determine if the event has damping going on this node. A TreeMap of nodes with this event is
   * kept to keep the damping data separated.
   *
   * @param nd Check to see if there is damping for this event on the given node.
   * @return true If this event should be Damped
   */
  public boolean isDamped(String nd) {
    // Need to add suppress all flag from source here!

    if (damps == null) {
      damps = new TreeMap<String, Damping>();
    }
    Damping d = damps.get(nd);
    if (d == null) {
      Util.prt("       Create new Damping for " + nd + " " + toString());
      d = new Damping(nd);
      damps.put(nd, d);
    }
    return d.isDamped();
  }

  /**
   * this inner class implements basic event damping for a single node. It It applies damping2 as a
   * minutes persistence before first page and damping as a minimum interval to repeat an event.
   *
   * @author d.c. ketchum
   */
  public final class Damping {

    private long firstEvent;        // Time this event was first posted, zero if not in the wait interval
    private long dampUntil;         // Time tis event can next go off
    private long lastEvent;         // Time last went off
    private int ndamp;
    private final String forNode;

    public String getNode() {
      return forNode;
    }

    public Damping(String nd) {
      forNode = nd;
      //long curr = System.currentTimeMillis();
      // Set the initial times.
      firstEvent = 0;
      lastEvent = 0;
    }

    @Override
    public String toString() {
      long curr = System.currentTimeMillis();
      return "for Nd=" + forNode.trim() + " 1st=" + Math.min((curr - firstEvent) / 1000, 9999) + " last=" + Math.min((curr - lastEvent) / 1000, 9999)
              + " damp until=" + Math.max(Math.min((dampUntil - curr) / 1000, 9999), -9999)
              + " nd/prc=" + (node == null ? "nl" : node.trim()) + "/" + (process == null ? "nl" : process.trim()) + " "
              + " ndamp=" + ndamp + " fires=" + nfires
              + (phrase == null ? "nl" : phrase.trim());
    }

    public String toString2() {
      return source.trim() + "-" + code.trim() + " nd/prc=" + (node == null ? "null" : node.trim()) + "/"
              + (process == null ? "null" : process.trim()) + " "
              + (phrase == null ? "null" : phrase.trim());

    }

    /**
     * Is there an event in progress. Or
     *
     * @return true if this event is damped
     */
    public boolean isActive() {
      long curr = System.currentTimeMillis();
      if (curr < dampUntil) {
        return true;     // We are in the damping interval
      }
      if (firstEvent == 0) {
        return false;    // If this has never been called.
      }
      if (curr - firstEvent < damping2 * 60000) { // Are we still in the wait interval
        return true;
      }
      return curr - lastEvent < damping * 120000;
    }

    /**
     * this is called each time a event is declared and does the basic damping. } It arms if this is
     * the first event after 2*damping2. It goes off after damping minutes. It will go off again
     * each damping2 minutes thereafter.
     *
     * @return true if this event is damped
     */
    public boolean isDamped() {
      Util.prt("      " + toString());
      total++;
      long curr = System.currentTimeMillis();
      if (curr - dampUntil < 0) {
        ndamp++;
        return true;
      }
      // lastEvent is the last time a event was triggered (not damped)
      if (curr - lastEvent > damping * 60000) { // Has the damping period expired (minimum time between pages)
        // Does thies event have a damping2 (persistence time)
        if (damping2 != 0) {   // does this event have a persistence damping (yes)
          // firstEvent is the time this event was armed, an event is declared if it persists
          // occurs again in the inverval from damping2 to damping2*2
          if (curr - firstEvent <= damping2 * 120000) {// we are past the triggering interval, arm
            // We are in damping2*2 interval, if damping2 has expired, declare event
            if (curr - firstEvent >= damping2 * 60000) { // Damping 2 has expired
              lastEvent = curr;
              nfires++;
              Util.prta("Damping : **** persistent event went off " + toString());
              dampUntil = curr + damping * 60000;
              return false;
            } else {
              ndamp++;
              return true;
            }   // The damping interval is not yet over (should never happen see dampuntil)
          } // We are past the trigger interval, this just sets up for a new trigger
          else {
            ndamp = 0;
            firstEvent = curr;
            dampUntil = curr + damping2 * 60000;
            return true;
          }
        } // damping2 is zero, we need to just go off!
        else {
          ndamp = 0;
          nfires++;
          lastEvent = curr;
          Util.prta("Damping : **** non-persistent event went off " + toString());
          dampUntil = curr + damping * 60000;
          return false;
        }
      } else {      // In the damping interval after an event
        ndamp++;
        return true;
      }
    }
    /*      if(curr - lastEvent > damping2*60000) { // Has the damping 2 period expired
        if(curr - firstEvent > damping*60000) { // Has minimum time since first event
          if(firstEvent != 0 || damping == 0) {      // first event is armed, go off, or no damping on interval
            ndamp=0;
            nfires++;
            lastEvent = curr;
            if(damping2 > 0) dampUntil= curr+damping2*60000;
            else dampUntil = curr+damping*60000;
            firstEvent=0;
            return false;
          }
          else {    // Need to arm the first event
            firstEvent = curr;
            dampUntil = curr + damping*60000;
            if(damping2 == 0) return false;     // no persistence damp, go off now
            return true;
          }
        }
        else return true;   // The damping interval is not yet over
      }
      else return true;     // We are in the damping 2 inteval
    }  */
 /*public boolean isDamped() {

      total++;
      long curr = System.currentTimeMillis();
      if(curr - dampUntil < 0) {
        ndamp++;
        return true;
      }
      if(firstEvent == 0) firstEvent=curr;    // If this has never been called.
      if(curr - firstEvent < damping2*60000) { // Are we still in the wait interval
        ndamp=0;
        return true;
      }
      // The first interval just expired if ndamp = 0
      if(curr - lastEvent > damping*120000) { // are we past the rearm interval
        firstEvent = curr;
        if(damping2 > 0) return true;         // start the first wait again, so no event on first one
        dampUntil = curr+damping*60000;
        ndamp=0;
        nfires++;
        lastEvent=curr;
        return false;
      }
      else {      // The situation has never cleared, reset  damping further ahead
        dampUntil = curr+damping*60000;
        ndamp++;
        nfires++;
        lastEvent=curr;
        return false;
      }
    } */
  }

  /**
   * Get the item corresponding to database ID
   *
   * @param ID the database Row ID
   * @return The Event row with this ID
   */
  public static Event getItemWithID(int ID) {
    if (events == null) {
      makeEvents();
    }
    for (Event ev : events) {
      if (ev.getID() == ID) {
        return ev;
      }
    }
    return null;
  }

  // This routine should only need tweeking if key field is not same as table name
  public static void makeEvents() {
    if (events != null) {
      return;
    }
    events = new ArrayList<Event>(100);
    try {
      Statement stmt = DBConnectionThread.getConnection(DBConnectionThread.getDBSchema()).createStatement();   // used for query
      String s = "SELECT * FROM alarm.event ORDER BY event;";
      ResultSet rs = stmt.executeQuery(s);
      while (rs.next()) {
        Event loc = new Event(rs);
//        Util.prt("MakeEvent() i="+v.size()+" is "+loc.getEvent());
        events.add(loc);
      }
    } catch (SQLException e) {
      Util.SQLErrorPrint(e, "makeEvents() on table SQL failed");
    }
  }
}
