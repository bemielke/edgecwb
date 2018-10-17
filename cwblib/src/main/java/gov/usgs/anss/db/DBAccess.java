/*
 *  This software is in the public domain because it contains materials 
 *  that originally came from the United States Geological Survey, 
 *  an agency of the United States Department of Interior. For more 
 *  information, see the official USGS copyright policy at 
 *  http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.db;

import gov.usgs.anss.dbtable.DBTable;
import gov.usgs.anss.edgethread.CommandServer;
import gov.usgs.anss.edgethread.CommandServerInterface;
import gov.usgs.anss.util.Util;
import gov.usgs.edge.alarm.Action;
import gov.usgs.edge.alarm.Alias;
import gov.usgs.edge.alarm.Event;
import gov.usgs.edge.alarm.Schedule;
import gov.usgs.edge.alarm.Source;
import gov.usgs.edge.alarm.Subscription;
import gov.usgs.edge.alarm.Target;
import gov.usgs.edge.config.Channel;
import gov.usgs.edge.config.Sendto;
import gov.usgs.edge.config.Flags;
import gov.usgs.edge.config.HydraFlags;
import gov.usgs.edge.snw.SNWStation;
import gov.usgs.edge.snw.SNWGroup;
import gov.usgs.anss.edgethread.EdgeThread;
//import gov.usgs.anss.util.SimpleSMTPThread;
import java.io.IOException;
import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Collections;

/**
 * This class is the main class provides access to all of the database of configuration parameters
 * by reading the latest settings of the configuration tables from the DB directory. Files in the DB
 * directory are of the for db_table.txt (edge_channel.txt). This thread can additionally be run in
 * DB mode. In DB mode this thread writes out the files based on the values in the database
 * periodically. Only one such thread is required on any EdgeCWB installation and the updating one
 * is normally run in Alarm. It can be run as part of an EdgeMom if the Alarm module is not used
 * (hard to imagine this case).
 * <p>
 * To add a new table to this class the tableNames, dbFilenames, classes arrays must have the table
 * name, filename in DB directory and class of the objects created by rows in this table. A new
 * ArrayList of newObjectClass needs to be creates like the events variable. This new ArrayList must
 * be added to the arraylist variable. See tags "MORETABLES" below.
 *
 * <PRE>
 * Switch        Description
 * -master         This process is responsible for writing out the data tables, normally the -db instance, but if no DB at all one must be set this way
 * -dbmsg  ip.adr  Set DBMessagesServer ip address (def=property("StatusServer"))
 * -dbmsgqsize nnn Set the dbmessage queue size (def=500)
 * -dbmsglen   nnn Set the maximum DBMessage message size (def=300)
 * Debug Switches :
 * -dbg            If present, log more output
 * -dbgmail        Turn on debugging output in SimpleSMTPThread (not know that this is very usefuly)
 *
 * </PRE>
 *
 * @author U.S. Geological Survey  <ketchum at usgs.gov>
 */
public final class DBAccess extends EdgeThread implements CommandServerInterface {

  private static boolean noDB;
  private static DBAccess thisThread;     // grant access to this thread statically.
  private static DBMessageQueuedClient dbMessageQueue;

  public static DBAccess getAccess() {
    return thisThread;
  }
  private static DBConnectionThread dbconnedge;
  private static final Integer dbconnedgeMutex = Util.nextMutex();
  private static DBConnectionThread dbanssDefault;    // This is open to benefit GUI classes that assume DBCT "anss" exists
  private static DBConnectionThread dbedgeDefault;    // This is open to benefit GUI classes that assume DBCT "edge" exists
  // MORETABLES add an element to the next 3 definitions to the end of eache
  public static final String[] tableNames = {
    "alarm.event", "alarm.alias", "alarm.action", "alarm.schedule",
    "alarm.source", "alarm.subscription", "alarm.target",
    "edge.channel", "edge.sendto", "edge.flags", "edge.hydraflags",
    "edge.snwstation", "edge.snwgroup"};
  private static final String[] dbFilenames = {
    "DB/alarm_event.txt", "DB/alarm_aliases.txt", "DB/alarm_action.txt", "DB/alarm_schedule.txt",
    "DB/alarm_source.txt", "DB/alarm_subscription", "DB/alarm_target",
    "DB/edge_channel.txt", "DB/edge_sendto.txt", "DB/edge_flags.txt", "DB/edge_hydraflags.txt",
    "DB/edge_snwstation.txt", "DB/edge_swngroup"};
  private static final Class[] classes = {
    Event.class, Alias.class, Action.class, Schedule.class,
    Source.class, Subscription.class, Target.class,
    Channel.class, Sendto.class, Flags.class, HydraFlags.class,
    SNWStation.class, SNWGroup.class};

  private boolean dbg;
  // MORETABLES: These are the arraylists of objects for all of the tables.  They are used in the getters
  // Note these are synchronized List to protect simultaneous updates
  private final List<Event> events = Collections.synchronizedList(new ArrayList<Event>(10));
  private final List<Alias> aliases = Collections.synchronizedList(new ArrayList<Alias>(10));
  private final List<Action> actions = Collections.synchronizedList(new ArrayList<Action>(10));
  private final List<Schedule> schedules = Collections.synchronizedList(new ArrayList<Schedule>(10));
  private final List<Source> sources = Collections.synchronizedList(new ArrayList<Source>(10));
  private final List<Subscription> subscriptions = Collections.synchronizedList(new ArrayList<Subscription>(10));
  private final List<Target> targets = Collections.synchronizedList(new ArrayList<Target>(10));
  private final List<Channel> channels = Collections.synchronizedList(new ArrayList<Channel>(10));
  private final List<Sendto> sendtos = Collections.synchronizedList(new ArrayList<Sendto>(10));
  private final List<Flags> flags = Collections.synchronizedList(new ArrayList<Flags>(10));
  private final List<HydraFlags> hydraflags = Collections.synchronizedList(new ArrayList<HydraFlags>(10));
  private final List<SNWStation> snwstations = Collections.synchronizedList(new ArrayList<SNWStation>(10));
  private final List<SNWGroup> snwgroups = Collections.synchronizedList(new ArrayList<SNWGroup>(10));

  //MORETABLES: This must be in the same order as the arrays above
  private final List[] arraylist = {events, aliases, actions, schedules, sources, subscriptions, targets,
    channels, sendtos, flags, hydraflags, snwstations, snwgroups};

  private final Constructor[] rsConstructor;
  private final Constructor[] dbConstructor;
  private final Method[] rsReload;
  private final Method[] dbReload;
  private final DBTable[] dbTable;
  private boolean dbMaster;
  private boolean ready;

  // these should be final, but any failure to initialize them should cause an exit
  //private final DBTable dbevent, dbalias,dbaction,dbschedule,dbsource,dbsubscription, dbtarget;
  // This is the queue of unperformed SQL statements waiting for a valid DBConnection
  //private final ArrayList<String> sqlqueue = new ArrayList<>(10);
  // MORETABLES: Add a getters and a getSize() for new tables here.
  //Note since these array lists are in ID order and seldom is anything deleted, 
  //they should always return the same object
  private void chkDB(int i) {
    try {
      if (rsConstructor[i] == null) {
        setUse(tableNames[i]);
      }
    } catch (RuntimeException e) {
      prta("DBA: chkDB runtime for i=" + i + " " + tableNames[i] + " e=" + e);
    }
  }

  public boolean isValid() {
    return ready;
  }

  public long getChannelsLastRead() {
    chkDB(7);
    return dbTable[7].getLastLoad();
  }

  public Event getEvent(int i) {
    chkDB(0);
    return events.get(i);
  }

  public Alias getAlias(int i) {
    chkDB(1);
    return aliases.get(i);
  }

  public Action getAction(int i) {
    chkDB(2);
    return actions.get(i);
  }

  public Schedule getSchedule(int i) {
    chkDB(3);
    return schedules.get(i);
  }

  public Source getSource(int i) {
    chkDB(4);
    return sources.get(i);
  }

  public Subscription getSubscription(int i) {
    chkDB(5);
    return subscriptions.get(i);
  }

  public Target getTarget(int i) {
    chkDB(6);
    return targets.get(i);
  }

  public Channel getChannel(int i) {
    chkDB(7);
    return channels.get(i);
  }

  public Sendto getSendto(int i) {
    chkDB(8);
    return sendtos.get(i);
  }

  public Flags getFlag(int i) {
    chkDB(9);
    return flags.get(i);
  }

  public HydraFlags getHydraFlag(int i) {
    chkDB(10);
    return hydraflags.get(i);
  }

  public SNWStation getSNWStation(int i) {
    chkDB(11);
    return snwstations.get(i);
  }

  public SNWGroup getSNWGroup(int i) {
    chkDB(12);
    return snwgroups.get(i);
  }

  public int getEventSize() {
    chkDB(0);
    return events.size();
  }

  public int getAliasSize() {
    chkDB(1);
    return aliases.size();
  }

  public int getActionSize() {
    chkDB(2);
    return actions.size();
  }

  public int getScheduleSize() {
    chkDB(3);
    return schedules.size();
  }

  public int getSourceSize() {
    chkDB(4);
    return sources.size();
  }

  public int getSubscriptionSize() {
    chkDB(5);
    return subscriptions.size();
  }

  public int getTargetSize() {
    chkDB(6);
    return targets.size();
  }

  public int getChannelSize() {
    chkDB(7);
    return channels.size();
  }

  public int getSendtoSize() {
    chkDB(8);
    return sendtos.size();
  }

  public int getFlagSize() {
    chkDB(9);
    return flags.size();
  }

  public int getHydraFlagSize() {
    chkDB(10);
    return hydraflags.size();
  }

  public int getSNWStationSize() {
    chkDB(11);
    return snwstations.size();
  }

  public int getSNWGroupSize() {
    chkDB(12);
    return snwgroups.size();
  }

  // MORETABLES: Allow callers access to DBTables so they can update fields
  public DBTable getEventDBTable() {
    chkDB(0);
    return dbTable[0];
  }

  public DBTable getAliasDBTable() {
    chkDB(1);
    return dbTable[1];
  }

  public DBTable getActionDBTable() {
    chkDB(2);
    return dbTable[2];
  }

  public DBTable getScheduleDBTable() {
    chkDB(3);
    return dbTable[3];
  }

  public DBTable getSourceDBTable() {
    chkDB(4);
    return dbTable[4];
  }

  public DBTable getSubscriptionDBTable() {
    chkDB(5);
    return dbTable[5];
  }

  public DBTable getTargetDBTable() {
    chkDB(6);
    return dbTable[6];
  }

  public DBTable getChannelDBTable() {
    chkDB(7);
    return dbTable[7];
  }

  public DBTable getSendtoDBTable() {
    chkDB(8);
    return dbTable[8];
  }

  public DBTable getFlagsDBTable() {
    chkDB(9);
    return dbTable[9];
  }

  public DBTable getHydraFlagDBTable() {
    chkDB(10);
    return dbTable[10];
  }

  public DBTable getSNWStationDBTable() {
    chkDB(11);
    return dbTable[11];
  }

  public DBTable getSNWGroupDBTable() {
    chkDB(12);
    return dbTable[12];
  }
  private final CommandServer commandServer;
  private int commandPort = 7705;
  private final String help = CommandServerInterface.helpBase + "GET,CHANNEL - return this channel\n";
  private final StringBuilder cmdsb = new StringBuilder(10);

  @Override
  public String getHelp() {
    return help;
  }

  @Override
  public StringBuilder doCommand(String command) {
    String[] parts = command.split(",");
    if (parts[0].trim().equalsIgnoreCase("GET")) {
      int j = getChannel(parts[1].trim());
      Channel c = null;
      if (j >= 0) {
        c = getChannel(j);
      }
      if (c == null) {
        return Util.clear(cmdsb).append(parts[1]).append(" is not a known channel!\n");
      }
      DBTable dbt = getChannelDBTable();
      Util.clear(cmdsb).append(tag).append(" ").append(c).append("DBA: rt=").append(c.getRate()).append(" sendto=").append(Util.toHex(c.getSendtoMask())).
              append(" fl=").append(Util.toHex(c.getFlags())).
              append(" hy=").append(Util.toHex(c.getHydraFlags())).append("/").append(c.getHydraValue()).
              append(" id=").append(c.getID()).append(" gap=").append(c.getGapType()).
              append(" pickID=").append(c.getPickerID()).
              append(" last=").append(c.getLastData()).
              append(" created=").append(c.getCreated()).append("\n");
      if (dbt != null) {
        int row = dbt.findRowWithID(c.getID());
        if (row >= 0) {
          cmdsb.append(" dbt: row of ID=").append(row);
        }
        for (int i = 0; i < dbt.getNCols(); i++) {
          cmdsb.append(dbt.getField(i)).append("=").append(dbt.getValue(row, i)).append(" ");
        }
        cmdsb.append("\n");
      }
      return cmdsb;
    } else if (parts[0].trim().equalsIgnoreCase("STATUS")) {
      return getMonitorString();
    } else {
      return Util.clear(cmdsb).append("Unknown command ").append(parts[0]);
    }
  }

  // These get things by their normal keys.  There does not have to be on of these
  public int getChannel(String chan) {
    String c = chan.trim();
    for (int i = 0; i < channels.size(); i++) {
      if (channels.get(i).getChannel().equals(c)) {
        return i;
      }
    }
    return -1;
  }

  public int getSNWStation(String station, boolean dbg) {
    String net = station.substring(0, 2).trim();
    String st = station.substring(2).trim();
    for (int i = 0; i < snwstations.size(); i++) {
      //if(dbg && net.equalsIgnoreCase(snwstations.get(i).getNetwork()));
      if(dbg) {
        prta(i+" "+snwstations.get(i).getID()+" getSNWStation "+net+"|"+snwstations.get(i).getNetwork()+"|"+st+"|"+snwstations.get(i).getSNWStation()+"|"+
                (snwstations.get(i).getNetwork().equalsIgnoreCase(net) && snwstations.get(i).getSNWStation().equalsIgnoreCase(st)));
      }
      if (snwstations.get(i).getNetwork().equalsIgnoreCase(net) && snwstations.get(i).getSNWStation().equalsIgnoreCase(st)) {
        return i;
      }
    }
    return -1;
  }

  @Override
  public StringBuilder getStatusString() {
    Util.clear(statussb);
    return statussb;
  }

  @Override
  public StringBuilder getConsoleOutput() {
    return consolesb;
  }

  @Override
  public void terminate() {
    terminate = true;
    interrupt();
  }

  @Override
  public StringBuilder getMonitorString() {
    Util.clear(monitorsb);
    for (int i = 0; i < tableNames.length; i++) {
      monitorsb.append(tableNames[i]).append("=").append(Util.ascdatetime(lastChanged[i])).append("\n");
    }
    return monitorsb;
  }

  public DBAccess(String argline, String tag) {
    super(argline, tag);
    String[] args = argline.split("\\s");
    noDB = false;
    String dbMessageServer = Util.getProperty("StatusServer");
    int dbMsgLength = 300;
    int dbMsgQueueSize = 500;
    for (int i = 0; i < args.length; i++) {
      prt(i + "DBA: arg=" + args[i]);
      if (args[i].equals("-dbg")) {
        dbg = true;
      } //else if(args[i].equalsIgnoreCase("-db")) {noDB=false;dbMaster=true;}
      //else if(args[i].equalsIgnoreCase("-nodb")) noDB=true;
      else if (args[i].equals("-dbmsg")) {
        dbMessageServer = args[i + 1];
        i++;
      } else if (args[i].equals("-dbmsgqsize")) {
        dbMsgQueueSize = Integer.parseInt(args[i + 1]);
        i++;
      } else if (args[i].equals("-dbmsglen")) {
        dbMsgLength = Integer.parseInt(args[i + 1]);
        i++;
      } else if (args[i].equalsIgnoreCase("-master")) {
        dbMaster = true;
        //} else if (args[i].equalsIgnoreCase("-dbgmail")) {
        //  SimpleSMTPThread.setDebug(true);
      } else if (args[i].substring(0, 1).equals(">")) {
        break;
      } else {
        prta("DBA: unknown arg i=" + i + "=" + args[i]);
      }
    }

    if (Util.getProperty("DBServer").equals("")) {
      noDB = true;
    }
    if (Util.getProperty("DBServer").equalsIgnoreCase("NoDB")) {
      noDB = true;
    }
    prta("DBA: master=" + dbMaster + " dbserver=" + Util.getProperty("DBServer") + " dbmsg=" + dbMessageServer + " dbg=" + dbg + " noDB=" + noDB);

    // noDB has been set based on the database properties.  To allow only one DBAcess to be using the DB, change this setting if not master
    if (!dbMaster) {
      noDB = true;              // this one is only going to user files!
    }
    if (!noDB) {
      try {
        dbconnedge = new DBConnectionThread(Util.getProperty("DBServer"), "update",
                "edge", false, false, "edgeAccess", getPrintStream());
        this.addLog(dbconnedge);
        if (!dbconnedge.waitForConnection()) {
          if (!dbconnedge.waitForConnection()) {
            if (!dbconnedge.waitForConnection()) {
              if (!dbconnedge.waitForConnection()) {
                prta("DBA: ***** could not connect edge database");
              }
            }
          }
        }
        prta("DBA: connected to " + Util.getProperty("DBServer") + dbconnedge);
      } catch (InstantiationException e) {
        prta("DBA: Impossible Instantiation problem");
        e.printStackTrace(getPrintStream());
      }
      dbanssDefault = DBConnectionThread.getThread("anss");
      if (dbanssDefault == null) {
        try {
          prta("DBA: connect to " + Util.getProperty("DBServer"));
          dbanssDefault = new DBConnectionThread(Util.getProperty("DBServer"), "update", "anss",
                  false, false, "anss", EdgeThread.getStaticPrintStream());
          addLog(dbanssDefault);
          if (!DBConnectionThread.waitForConnection("anss")) {
            if (!DBConnectionThread.waitForConnection("anss")) {
              if (!DBConnectionThread.waitForConnection("anss")) {
                prt("DBA: Did not promptly connect to anss default");
              }
            }
          }
          prt("DBA: connected to " + Util.getProperty("DBServer") + dbanssDefault);
        } catch (InstantiationException e) {
          prta("DBA: Instantiation getting (impossible) anssro e=" + e.getMessage());
          dbanssDefault = DBConnectionThread.getThread("anss");
        }
      }
      dbedgeDefault = DBConnectionThread.getThread("edge");
      if (dbedgeDefault == null) {
        try {
          prta("DBA: connect to " + Util.getProperty("DBServer"));
          dbedgeDefault = new DBConnectionThread(Util.getProperty("DBServer"), "update", "edge",
                  false, false, "edge", EdgeThread.getStaticPrintStream());
          addLog(dbedgeDefault);
          if (!DBConnectionThread.waitForConnection("edge")) {
            if (!DBConnectionThread.waitForConnection("edge")) {
              if (!DBConnectionThread.waitForConnection("edge")) {
                prt("DBA: Did not promptly connect to edge default");
              }
            }
          }
          prta("DBA: connected to " + Util.getProperty("DBServer") + dbedgeDefault);
        } catch (InstantiationException e) {
          prta("Instantiation getting (impossible) edge ro e=" + e.getMessage());
          dbedgeDefault = DBConnectionThread.getThread("edge");
        }
      }
      if (!noDB) {
        dbMessageQueue = new DBMessageQueuedClient(dbMessageServer, 7985, dbMsgQueueSize, dbMsgLength, this);
        prta("DBA: message Server to " + dbMessageServer + "/" + 7985 + " created len=" + dbMsgLength + " queue=" + dbMsgQueueSize
                + " proc=" + Util.getProcess() + " pid=" + Util.getPID());
      }  // 100 message queue, 300 in length
    }

    rsConstructor = new Constructor[tableNames.length];
    dbConstructor = new Constructor[tableNames.length];
    rsReload = new Method[tableNames.length];
    dbReload = new Method[tableNames.length];
    lastChanged = new long[tableNames.length];
    dbTable = new DBTable[tableNames.length];
    commandServer = new CommandServer(tag, commandPort, (EdgeThread) this, (CommandServerInterface) this);
    setDaemon(true);
    thisThread = (DBAccess) this;
    running = true;
    start();
  }

  public int setUse(String table) {
    while (!running) {
      try {
        sleep(1000);
      } catch (InterruptedException expected) {
      }
      prta("DBA: setUse() before DBAccess is stable! table=" + table);
    }
    for (int i = 0; i < tableNames.length; i++) {
      if (tableNames[i].contains(table)) {
        if (rsConstructor[i] == null) {
          doTable(i, arraylist[i]);
        }
        return i;
      }
    }
    return -1;
  }

  /**
   * send a DBMessageQueue message
   *
   * @param msg A string builder in db^table^file=value;field=value; form
   * @return
   */
  public boolean dbMessageQueue(StringBuilder msg) {
    if (getAccess() == null) {
      return false;
    }
    if (dbMessageQueue == null) {
      return false;
    }
    return dbMessageQueue.queueDBMsg(msg);
  }

  private DBTable doTable(int i, List array) {
    DBTable table = new DBTable(dbFilenames[i], this);
    table.setDebug(dbg);
    dbTable[i] = table;

    // Constructor for ResultSets and DBTables
    try {
      Class[] em = new Class[1];
      em[0] = ResultSet.class;
      rsConstructor[i] = classes[i].getConstructor(em);
      rsReload[i] = classes[i].getMethod("reload", em);
      em[0] = DBTable.class;
      dbConstructor[i] = classes[i].getConstructor(em);
      dbReload[i] = classes[i].getMethod("reload", em);

    } catch (NoSuchMethodException e) {
      prta(i + " Got  no such constructor method");

    }
    if (!noDB) {
      if (!dbconnedge.isOK()) {
        dbconnedge.reopen();
      }
    }

    for (int itry = 0; itry < 3; itry++) {
      synchronized (dbconnedgeMutex) {
        try {
          if (!noDB && dbconnedge.isOK()) {    // we are using the DB and we have a db thread
            prta("DBA: startup creating table from DB " + tableNames[i]);
            ResultSet rs = dbconnedge.executeQuery("SELECT * FROM " + tableNames[i] + " ORDER BY id");
            table.makeDBTableFromDB(rs);
          }
          loadFromTable(i, array);
        } catch (SQLException | RuntimeException e) {
          prta("DBA: Error making table try=" + itry + " for " + tableNames[i] + " db=" + dbconnedge);
          e.printStackTrace(getPrintStream());
          dbconnedge.reopen();
          if (itry >= 2) {
            prta("DBA:  ****** fatal error making table !" + tableNames[i]);
          }
        } catch (IOException e) {
          e.printStackTrace(getPrintStream());
          Util.exit("Something is wrong try=" + itry + " trying to create a table " + table + " e=" + e);
          dbconnedge.reopen();
          if (itry >= 2) {
            prta("DBA:  ****** fatal error making table !" + tableNames[i]);
          }
        }
      }
    }
    return table;
  }

  private void loadFromTable(int i, List values) throws IOException {
    // Load the alias table
    int nrep = 0;
    int cnt = 0;
    boolean changed;
    synchronized (dbTable[i]) {
      changed = dbTable[i].load();
      if (changed) {
        int size = values.size();
        prta("DBA: " + dbTable[i].toString().substring(0, Math.min(dbTable[i].toString().length(), 40)) + " has changed size=" + size + " hasnext=" + dbTable[i].hasNext());    // DEBUG:
        while (dbTable[i].hasNext()) {
          dbTable[i].next();
          if (cnt < size) {
            try {
              dbReload[i].invoke(values.get(cnt), dbTable[i]);
              if (dbTable[i].getFilename().contains("edge_channel")) {
                Channel c = (Channel) values.get(cnt);
                //if(c.getChannel().startsWith("TAP33M")) prta("Setting "+c+" rt="+c.getRate());
              }
              nrep++;
            } catch (IllegalAccessException | InvocationTargetException e) {
              e.printStackTrace(getPrintStream());
            }
            cnt++;
            //values.get(cnt++).reload(dbTable[i]);
          } else {
            try {
              Object ev = dbConstructor[i].newInstance(dbTable[i]);
              values.add(ev);
              if (dbg) {
                prt("DBA: " + i + " " + tableNames[i] + " " + ev.toString());
              }
              cnt++;
            } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
              e.printStackTrace(getPrintStream());
            }
          }
        }
      }
    }
    prta("DBA: loadFromTable() #" + tableNames[i] + " changed=" + changed + " loaded=" + values.size() + " cnt=" + cnt + " replaced=" + nrep);
  }
  long lastVersionTime = 0;

  /**
   * THis may be a bad idea, but if the updated field in the version table is newer than last time,
   * return true.
   *
   * @return If version.udpated is newer than last time.
   * @throws SQLException
   */
  private boolean isVersionUpdated() throws SQLException {
    boolean ret = false;
    synchronized (dbconnedgeMutex) {
      if (dbconnedge.isOK()) {
        try (ResultSet rs = dbconnedge.executeQuery("SELECT updated FROM edge.version")) {
          if (rs.next()) {
            long version = rs.getTimestamp(1).getTime();
            if (version > lastVersionTime) {
              ret = true;
              lastVersionTime = version + 1000;
            }
          }
        } catch (SQLException e) {
          e.printStackTrace(getPrintStream());
          throw e;
        }
      } else {
        dbconnedge.reopen();
      }
    }
    return ret;
  }
  private final long[] lastChanged;

  /**
   * If this object is a master, this routine looks to update the disk file from the DB if the
   * MAX(updated) is bigger than the last time the file was written. If so, the full DB is queried
   * and a new file written and the functions returns true.
   *
   * If this is not the master, this checks to see if the file modify time has been changed
   * (indicating the master has written it), and returns true if this is so.
   *
   * @param i This is the index into the dbtable array of the table to check
   * @return true if the disk files was modified
   * @throws SQLException If thrown
   * @throws IOException
   */
  private boolean getDBTableFromDB(int i) throws SQLException, IOException {
    boolean ret = false;
    if (dbTable[i] == null) {
      return false;
    }
    if (dbMaster && !noDB) {
      int loop = 0;
      synchronized (dbconnedgeMutex) {
        while (!dbconnedge.isOK()) {
          loop++;
          if (loop % 20 == 0) {
            prta("DBA: *** getDBTableFromDB() waiting for dbconnedge to be o.k. " + loop);
          }
          try {
            sleep(500);
          } catch (InterruptedException expected) {
          }
          if (loop % 60 == 0) {
            dbconnedge.reopen();
          }
        }
        try (ResultSet rs = dbconnedge.executeQuery("SELECT MAX(updated) FROM " + tableNames[i])) {
          if (rs.next()) {
            if (rs.getTimestamp(1) != null) {      // I do not know what this means, but it happens
              if (rs.getTimestamp(1).getTime() > lastChanged[i]) {
                lastChanged[i] = rs.getTimestamp(1).getTime();
                ret = true;
                prta("DBA: getDBTableFromDB() DB update " + tableNames[i]);
                try (ResultSet rs2 = dbconnedge.executeQuery("SELECT * FROM " + tableNames[i] + " ORDER BY id")) {
                  dbTable[i].makeDBTableFromDB(rs2);
                }
              }
            } else {
              prta("DBA: *** getDBTableFromDB() " + tableNames[i] + " SELECT returned a null!  Table probably not populated.");
            }
          }
        } catch (SQLException e) {
          e.printStackTrace(getPrintStream());
          throw e;
        }
      }
    } else {
      File f = new File(dbTable[i].getFilename());
      if (f.lastModified() > lastChanged[i]) {
        prta("DBA: getDBTableFromDB() files is modified " + tableNames[i] + " "
                + (lastChanged[i] == 0 ? 0 : f.lastModified() - lastChanged[i]));
        ret = true;
        lastChanged[i] = f.lastModified();
      }
    }
    return ret;
  }
  public void forceUpdatesNow() {
    interrupt();
  }

  @Override
  public void run() {
    long waittime;
    boolean doChanged;
    int lastj = 0;
    int nsqlerr = 0;
    boolean first = true;
    long now = System.currentTimeMillis();

    // If this thread is not reading from the database and is not responsible for writing the channel table
    prta("DBA: starting noDB=" + noDB + " master=" + dbMaster + " dbconn=" + (dbconnedge == null ? "null" : dbconnedge.isOK()));
    if (noDB && !dbMaster) {
      for (int i = 0; i < 450; i++) {
        File f = new File("DB/edge_channel.txt");
        if (f.exists() && (now - f.lastModified() < 600000)) {
          prta("DBA: noDB waited for DB/edge_channel.txt for " + (System.currentTimeMillis() - now) + " ms file age=" + (now - f.lastModified()));
          break;
        }
        try {
          sleep(100);
        } catch (InterruptedException expected) {
        }
        if (i % 100 == 99) {
          prta("DBA: is waiting for DB/edge_channel.txt to be more recent! " + i);
        }
      }
      waittime = 1000;
      //doChanged = true;
    } else {
      waittime = 15000;
    }
    while (!terminate) {
      try {
        for (int j = 0; j < waittime / 1000; j++) {

          lastj = j;
          doChanged = false;
          try {
            sleep(1000);
          } catch (InterruptedException expected) {
          }
          for (int i = 0; i < tableNames.length; i++) {
            if (dbTable[i] != null) {
              if (dbTable[i].hasInsertOccurred() ) {      // interupt out for inserts, but updates can wait
                prta("DBA: ** change occurred in dbTables " + dbTable[i].getFilename()
                        + " ins=" + dbTable[i].hasInsertOccurred() + " upd=" + dbTable[i].hasUpdateOccurred());
                try {
                  sleep(1000);
                } catch (InterruptedException expected) {
                }  // Give 1 second for DBMessages and MySQL to be updated
                doChanged = true;
                break;
              }
            }
          }
          if (doChanged) {
            break;
          }
        }
        doChanged = true;   // force and update
        if (terminate) {
          break;
        }
        if (!noDB) {
          if (!dbconnedge.isOK()) {
            dbconnedge.reopen();
          }
        }

        // if doChanged is false, periodically load the data from the files, othersize just do the changed files
        try {
          for (int i = 0; i < tableNames.length; i++) {
            if (dbTable[i] != null) {
              if (!dbMaster) {
                prta("DBA: * reload table only from file not master i=" + i + " routine=" + dbTable[i].getFilename() + " #rows=" + dbTable[i].getNrows());
                loadFromTable(i, arraylist[i]); // Just read the table
              } else {    // We are the master, write something out
                if (!noDB && dbconnedge.isOK()) {    // If the DB says its o.k., then try to load from the DB
                  if (doChanged || dbTable[i].hasInsertOccurred() || dbTable[i].hasUpdateOccurred()) { // if periodic or something changed
                    prta("DBA: reload table from DB chk i=" + i + " doChanged=" + doChanged + " update=" + dbTable[i].hasUpdateOccurred() + " ins=" + dbTable[i].hasInsertOccurred()
                            + " " + dbTable[i].getFilename() + " #rows=" + dbTable[i].getNRows() + " lastj=" + lastj + " waittime=" + waittime);
                    if (getDBTableFromDB(i)) {    // Is the table changed, if so read it in
                      loadFromTable(i, arraylist[i]);
                      prta("DBA: reload table from DB complete update=" + dbTable[i].hasUpdateOccurred() + " ins=" + dbTable[i].hasInsertOccurred() + " " + dbTable[i].getFilename());
                    }
                  }
                } else {      // We are in noDB mode or the DB says its not ok, then just make and reload from the memory->disk->load
                  if (doChanged || dbTable[i].hasInsertOccurred() || dbTable[i].hasUpdateOccurred()) {// if periodic or something changed
                    try {
                      if (!first) {
                        prta("DBA: * i=" + i + " Write data file w/o DB noDB mode (inserted or updated) "
                                + dbTable[i].getFilename() + " #rows=" + dbTable[i].getNRows());
                        dbTable[i].writeTable();
                      }        // update the table from the memory 
                    } catch (IOException e) {
                      e.printStackTrace(getPrintStream());
                    }
                    prta("DBA: * reload table only from file i=" + i + " routine=" + dbTable[i].getFilename() + " #rows=" + dbTable[i].getNRows());
                    loadFromTable(i, arraylist[i]);
                  }
                }
              }
            }
          }
        } catch (SQLException | IOException e) {
          prta("DBA: SQL problem - assume database is off line! e=" + e);
          e.printStackTrace(getPrintStream());
          nsqlerr++;
          dbconnedge.reopen();
        }
      } catch (RuntimeException e) {
        prta("DBA: Runtime exception - continue.");
        e.printStackTrace(getPrintStream());
      }
      first = false;
      if (noDB) {
        waittime = 1200000;
      } else {
        waittime = 120000;
      }
      ready = true;
    } // while(!terminate)
    prta("DBA: DBAccess is exiting");
    running = false;
  }

  /**
   * @param args the command line arguments
   */
  public static void main(String[] args) {
    Util.prt(Util.asctime());
    Util.setModeGMT();
    Util.init("edge.prop");
    Util.setNoInteractive(true);
    Util.prt(Util.asctime());
    DBAccess access = new DBAccess("-db >>dbaccess", "DBTH");
    access.setUse("edge.snwgroup");
    for (int i = 0; i < tableNames.length; i++) {
      if (i % 3 == 0) {
        access.setUse(tableNames[i]);
      }
    }
    Util.prt("Event=" + access.getEvent(10).toString3());
    Util.prt("Alias=" + access.getAlias(2).toString2());
    Util.prt("subscription=" + access.getSubscription(10).toString3());
    Util.prt("Sendto=" + access.getSendto(10).toString2());
    Util.prt("Channel=" + access.getChannel(10).toString2());
    Util.prt("Flag=" + access.getFlag(2).toString2());
    Util.prt("HydraFlag=" + access.getHydraFlag(2).toString2());

    //ServerThread server = new ServerThread(AnssPorts.PROCESS_SERVER_PORT, false);
    String argline = "";
    int loop = 0;
    for (;;) {
      Util.sleep(10000);
      if (loop % 30 == 0) {
        Util.prta(DBConnectionThread.getStatus());
      }
    }

  }
}
