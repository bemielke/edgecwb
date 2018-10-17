/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.edgemom;

import gov.usgs.alarm.SendEvent;
import gov.usgs.anss.edgethread.EdgeThread;
import gov.usgs.anss.edgethread.CommandServer;
import gov.usgs.anss.edgethread.CommandServerInterface;
import gov.usgs.anss.db.DBMessageQueuedClient;
import gov.usgs.anss.util.Util;
import gov.usgs.edge.config.Channel;
import gov.usgs.edge.config.Flags;
import gov.usgs.edge.config.Sendto;
import gov.usgs.edge.config.HydraFlags;
import gov.usgs.anss.dbtable.DBTable;
import gnu.trove.map.hash.TLongObjectHashMap;
import gnu.trove.iterator.TLongObjectIterator;
import gov.usgs.anss.db.DBAccess;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Random;
import java.io.IOException;

/**
 * This class maintains a static TLongObjectHashMap of all of the channels using
 * the 12 character seedname to long mapping for all channels which have records
 * in the edge.channel table. It maintains a connection to the database and
 * updates any changes every 120000 ms unless changed by command line. The
 * expectation is that a process would start one of these and use static Channel
 * getChannel(seedname) to obtain configuration data for the channel. Only one
 * of these should be started per EdgeMom or other process. Note that
 * performance is better if the calls to the getters use StringBuilders to
 * specify the seedname rather than strings.
 * <p>
 * For better efficiencies this class support getting channels with
 * StringBuffers with the channel names. If the using class can use
 * StringBuilders this further reduces the creation of String objects with
 * seednames in them.
 * <p>
 * This class descended from the original EdgeChannelServer wich used a TreeMap
 * index by channel string to Channel objects. This class is roughly twice as
 * fast and creates fewer intermeediate objects (strings and other TreeMap index
 * related objects).
 *
 * <br>
 * <PRE>
 *Switch  arg        Description
 *-dbg               Debug output on
 * -master           If present, this EdgeChannelServer will write out the database.  This only makes sense on no-Alarm systems
 * -update  ms        Milliseconds before updates of the list from database
 * -dbmsg  ip.adr    The POCMysqlServer is expected at property DBServer, this overrides this.
 * -dbmsgqsize nnnn  Set the queue size to nnnn def=500
 * -dbmsglength nnn  Set the max message length to nnn (def=300)
 * </PRE>
 *
 * @author davidketchum
 */
public final class EdgeChannelServer extends EdgeThread implements CommandServerInterface {

  private static DBMessageQueuedClient dbMessageQueue;    // object to send out DBMessageServer updates
  private static String dbMessageServer;
  // Structures with various tables.
  private static final TLongObjectHashMap<Channel> chans = new TLongObjectHashMap<>();    // Static list of all of the channels this server is keeping
  private static final ArrayList<Sendto> sendtos = new ArrayList<>(64);
  ;
  private static final ArrayList<Flags> flags = new ArrayList<>(64);
  private static final ArrayList<HydraFlags> hydraflags = new ArrayList<>(64);
  private boolean dbMaster;    // if true, then this dbaccess writes out the files from this instance
  private static DBAccess access;
  private int updateMS;                 // Number of ms between update passes.
  private static boolean started;              // Set true when first population is completed
  private static int ngets;                    // count calls to getter
  private static int ngetsString;
  private static EdgeChannelServer aServer;
  private static boolean noDB;
  // These masks are for the "standard" flags that most installations might use.
  static public long MASK_NOT_PUBLIC;
  static public long MASK_ARRAY_PORT;
  static public long MASK_NO_EDGEWIRE;
  static public long MASK_SMGETTER_FETCH;
  static public long MASK_HAS_METADATA;

  static public long MASK_NO_NUCLEATION;
  static public long MASK_NO_PICKS;
  static public long MASK_USE_MWC;
  static public long MASK_USE_MWB;
  static public long MASK_USE_MWW;
  static public long MASK_MANUAL_NO_PICKS;
  static public long MASK_MANUAL_NO_AMPLITUDES;
  static public long MASK_USE_LOCAL;
  static public long MASK_USE_REGIONAL;
  static public long MASK_USE_TELESEISM;
  private static final StringBuilder dbmsg = new StringBuilder(100);
  private static int dbmsgQsize = 500;
  private static int dbmsgLength = 300;
  private final ShutdownEdgeChannelServer shutdown;
  private boolean dbg;
  // Command server stuff
  private final CommandServer commandServer;
  private int commandPort = 7700;
  private final String help = CommandServerInterface.helpBase + "GET,CHANNEL - return this channel\nADD,CHANNEL - add this channel\nSTATUS - report status\n";
  private final StringBuilder cmdsb = new StringBuilder(10);
  private final StringBuilder runsb = new StringBuilder(100);      // use inside of run method

  @Override
  public String getHelp() {
    return help;
  }

  @Override
  public StringBuilder doCommand(String command) {
    String[] parts = command.split(",");
    if (parts[0].trim().equalsIgnoreCase("GET")) {
      Channel c = getChannel(parts[1].trim());
      if (c == null) {
        return Util.clear(cmdsb).append(parts[1]).append(" is not a known channel!\n");
      }
      return Util.clear(cmdsb).append(tag).append(" ").append(c).append(" ECS: rt=").append(c.getRate()).
              append(" sendto=").append(Util.toHex(c.getSendtoMask())).append("/").append(c.getSendtoMask()).
              append(" fl=").append(Util.toHex(c.getFlags())).
              append(" hy=").append(Util.toHex(c.getHydraFlags())).append("/").append(c.getHydraValue()).
              append(" id=").append(c.getID()).append(" gap=").append(c.getGapType()).
              append(" pickID=").append(c.getPickerID()).
              append(" last=").append(c.getLastData()).
              append(" created=").append(c.getCreated()).append("\n");
    } else if (parts[0].trim().equalsIgnoreCase("STATUS")) {
      return getMonitorString();
    } else if (parts[0].trim().equalsIgnoreCase("ADD")) {
      Util.clear(cmdsb).append(parts[1]);
      createNewChannel(cmdsb, 40., "", this);
      return cmdsb.append("Was created\n");
    } else {
      return Util.clear(cmdsb).append("Unknown command ").append(parts[0]);
    }
  }

  public static String getDBMessageServer() {
    return dbMessageServer;
  }   // used by SNWChannelClient 

  public static DBAccess getDBAccess() {
    return access;
  }

  public static boolean exists() {
    return aServer != null;
  }

  public static boolean isNoDB() {
    return noDB;
  }

  public static boolean dbMessageQueue(StringBuilder msg) {
    if (dbMessageQueue == null && !noDB) {
      if (dbMessageServer == null) {
        Util.prt("**** attempt to us EdgeChannelServer.dbMessageQueue() before ECS instantiated!  continue");
        return false;
      }
      dbMessageQueue = new DBMessageQueuedClient(dbMessageServer, 7985, dbmsgQsize, dbmsgLength, aServer);  // 500 message queue, 300 in length normally
      if (aServer != null) {
        aServer.prta("ECS: DBMessagQueuedClient2 to " + dbMessageServer + "/" + 7985 + " created");
      } else {
        Util.prta("ECS: DBMessageQueuedClient2 to " + dbMessageServer + "/" + 7985 + " created " + dbMessageQueue);
      }
    }
    if (noDB) {
      Util.prta("ECS: dbMessageQueue called when in noDB mode " + msg);
    } else {
      dbMessageQueue.queueDBMsg(msg);
    }
    return false;
  }

  @Override
  public String toString() {
    return "ECS: started=" + started + " #ch=" + chans.size();
  }

  public static boolean isChannelArrayPort(Channel c) {
    return (c.getFlags() & MASK_ARRAY_PORT) != 0;
  }

  public static boolean isChannelArrayPort(String chan) {
    synchronized (statsb) {
      Util.clear(statsb).append(chan);
      ngetsString++;
      return isChannelArrayPort(statsb);
    }
  }

  public static boolean isChannelArrayPort(StringBuilder chan) {
    Channel c;
    synchronized (chans) {
      c = chans.get(Util.getHashFromSeedname(chan));
    }
    if (c == null) {
      return false;
    }
    return (c.getFlags() & MASK_ARRAY_PORT) != 0;
  }

  public static boolean isChannelNotPublic(Channel c) {
    return (c.getFlags() & MASK_NOT_PUBLIC) != 0;
  }

  public static boolean isChannelNoEdgeWire(Channel c) {
    return (c.getFlags() & MASK_NO_EDGEWIRE) != 0;
  }

  public static boolean isChannelNotContinous(Channel c) {
    return (c.getFlags() & MASK_SMGETTER_FETCH) != 0;
  }

  public static boolean hasChannelMetadata(Channel c) {
    return (c.getFlags() & MASK_HAS_METADATA) != 0;
  }

  public static void setLastData(String chan, long ms) {
    Channel c;
    synchronized (chans) {
      c = chans.get(Util.getHashFromSeedname(chan.toUpperCase()));
    }
    if (c != null) {
      c.setLastData(ms);
    }
  }

  public static void setLastData(StringBuilder chan, long ms) {
    if (Character.isLowerCase(chan.charAt(7))) {
      return;
    }
    Channel c;
    synchronized (chans) {
      c = chans.get(Util.getHashFromSeedname(chan));
    }
    if (c != null) {
      c.setLastData(ms);
    }
  }

  public static boolean isChannelNotPublic(String seedname) {
    synchronized (statsb) {
      Util.clear(statsb).append(seedname);
      return isChannelNotPublic(statsb);
    }
  }

  public static boolean isChannelNotPublic(StringBuilder seedname) {
    Channel c = getChannel(seedname);
    if (c == null) {
      return false;
    }
    return (c.getFlags() & MASK_NOT_PUBLIC) != 0;
  }

  public static boolean isChannelNoEdgeWire(StringBuilder seedname) {
    Channel c = getChannel(seedname);
    if (c == null) {
      return false;
    }
    return (c.getFlags() & MASK_NO_EDGEWIRE) != 0;
  }

  public static boolean isChannelNotContinuous(StringBuilder seedname) {
    Channel c = getChannel(seedname);
    if (c == null) {
      return false;
    }
    return (c.getFlags() & MASK_SMGETTER_FETCH) != 0;
  }

  public static boolean hasChannelMetadata(StringBuilder seedname) {
    Channel c = getChannel(seedname);
    if (c == null) {
      return false;
    }
    return (c.getFlags() & MASK_HAS_METADATA) != 0;
  }

  public static Object[] getChannels() {
    return chans.values();
  }

  /**
   * is valid returns true if the channel database has been read
   *
   * @return true if database has been read, else still starting up
   */
  public static boolean isValid() {
    return started;
  }

  /**
   * return a ArrayList with all of the Sendto objects defined. There are a
   * maximum of 64 where the index is the bit number of the mask, is the ID-1th
   * bit
   *
   * @return An array list with up to 74 sendtos or nulls!
   */
  public static ArrayList<Sendto> getSendtos() {
    return sendtos;
  }

  /**
   * return a ArrayList with all of the Flags objects defined. There are a
   * maximum of 64 where the index is the bit number of the mask, is the ID-1th
   * bit
   *
   * @return An array list with up to 64 flags or nulls!
   */
  public static ArrayList<Flags> getFlags() {
    return flags;
  }

  /**
   * check if a particular flag is on this configuration
   *
   * @param flagtag The string of the flag
   * @return The long mask for the flag
   */
  public static long getFlagMask(String flagtag) {
    while (!started) {
      Util.sleep(100);
    }
    for (Flags f : flags) {
      if (f == null) {
        continue;
      }
      if (flagtag.equalsIgnoreCase(f.getFlags())) {
        return f.getMask();
      }
    }
    return 0L;
  }

  /**
   * set debug state
   *
   * @param t The new debug state
   */
  public void setDebug(boolean t) {
    dbg = t;
  }

  /**
   * terminate thread (causes an interrupt to be sure) you may not need the
   * interrupt!
   */
  @Override
  public void terminate() {
    terminate = true;
    interrupt();
    prta("ECS: terminate called");
    try {
      sleep(500);
    } catch (InterruptedException expected) {
    }
  }

  /**
   * return some channel give a station
   *
   * @param netStat The network and station portion
   * @return some channel from this station
   */
  public static Channel getAnyChannel(String netStat) {
    String ns = (netStat + "       ").substring(0, 7);
    Object[] arr = chans.values();
    int end = arr.length - 1;
    int beg = 0;
    boolean found = false;
    int ind;
    while (!found) {
      ind = (end + beg) / 2;
      Channel c = (Channel) arr[ind];
      String ch = c.getChannel();
      if (ch.substring(0, 7).equals(netStat)) {
        return c;
      }
      if (ch.substring(0, 7).compareTo(ns) < 0) {
        beg = ind;
      } else {
        end = ind;
      }
      if ((end + beg) / 2 == ind) {
        break;
      }
    }
    if (aServer != null) {
      aServer.prt("Did not find beg=" + beg + " end=" + end);
    }
    for (int i = beg; i <= end; i++) {
      //Util.prt("Try "+arr[i]);
      if (((Channel) arr[i]).getChannel().substring(0, 7).equals(ns)) {
        return (Channel) arr[i];
      }
    }
    return null;
  }

  /**
   * return the channel data for a seedname
   *
   * @param seedname The seedname (full 12 character) to return a channel for
   * @return the matching channel object or null if not found
   */
  static private final ArrayList<CharSequence> missing = new ArrayList<>(100);
  ;
  static private final StringBuilder statsb = new StringBuilder(12);

  public static Channel getChannel(String seedname) {
    synchronized (statsb) {
      ngetsString++;
      //if(ngetsString % 1000 == 999) new RuntimeException(" getChannel(String) ngets="+ngetsString+" SB="+ngets);
      Util.clear(statsb).append(seedname);
      return getChannel(statsb, true);
    }
  }

  public static Channel getChannel(String seedname, boolean eventAllowed) {
    synchronized (statsb) {
      Util.clear(statsb).append(seedname);
      return getChannel(statsb, eventAllowed);
    }
  }

  public static Channel getChannel(CharSequence seedname) {
    return getChannel(seedname, true);
  }

  public static Channel getChannel(CharSequence seedname, boolean eventAllowed) {
    if (chans == null) {
      return null;
    }
    if (Util.charSequenceIndexOf(seedname, "DUMMY") >= 0) {
      return null;
    }
    if (seedname.charAt(0) == 0 || seedname.charAt(1) == 0 || seedname.charAt(2) == 0) {

    }
    //aServer.prta("getchannel ="+seedname); 
    Channel c;

    synchronized (chans) {
      c = chans.get(Util.getHashFromSeedname(seedname));
    }
    if (c == null) {
      synchronized (missing) {
        for (CharSequence missing1 : missing) {
          if (Util.stringBuilderEqual(missing1, seedname)) {
            return null;
          }
        }
      }
      if (aServer != null) {
        aServer.prta("did not find " + seedname + "! #chn=" + chans.size());
      } else {
        Util.prta("did not find " + seedname + "! #chn=" + chans.size());
      }
      if (!started) {
        return null;
      }
      if (eventAllowed) {
        String ch = ""+seedname.charAt(7)+seedname.charAt(8)+seedname.charAt(9);
        if (!(ch.equals("AFP") || ch.equals("ACO") || ch.equals("ACE") || ch.substring(0, 1).equals("O")
                || ch.substring(1, 2).equals("Y"))) {
          SendEvent.edgeSMEEvent("ChanNotFnd", "Channel not found1=" + Util.toAllPrintable(seedname) + " new?",
                  "EdgeChannelServer/" + Util.getProcess());
          StackTraceElement[] elements;
          elements = new RuntimeException("EdgeChannelServer : channel not found1 " + Util.toAllPrintable(seedname)).getStackTrace();
          for (StackTraceElement element : elements) {
            aServer.prta("" + element + " " + element.getFileName() + ":" + element.getMethodName() + "/" + element.getLineNumber());
          }
        }
      }

      if (aServer.getPrintStream() == null) {
        new RuntimeException("EdgeChannelServer : channel not found1 " + seedname).
                printStackTrace();
      } else {
        new RuntimeException("EdgeChannelServer : channel not found1 " + seedname).
                printStackTrace(aServer.getPrintStream());
      }
      synchronized (missing) {
        missing.add(seedname);
      }
      return null;
    }
    //else Util.prta("Got channel "+seedname+" "+c.toString());
    ngets++;
    if (ngets % 100000 == 0) {
      aServer.prta("ECS: gets #=" + ngets + " #string=" + ngetsString);
    }
    return c;
  }

  /*public static Channel getChannelNoTraceback(String seedname) {
    return getChannelNoTraceback(seedname);
    synchronized (statsb) {
      Util.clear(statsb).append(seedname);
      return getChannelNoTraceback(statsb);
    }
  }*/

  public static Channel getChannelNoTraceback(CharSequence seedname) {
    if (chans == null) {
      return null;
    }
    //aServer.prta("getchannel ="+seedname);
    Channel c;
    synchronized (chans) {
      //c = chans.get(Util.getHashFromSeedname(seedname));
      c = chans.get(Util.getHashFromSeedname(seedname));
    }
    if (c == null) {
      synchronized (missing) {
        for (CharSequence missing1 : missing) {
          if (Util.stringBuilderEqual(statsb, seedname)) {
            return null;
          }
        }
        missing.add(seedname);
      }
      return null;
    }
    //else Util.prta("Go:qt channel "+seedname+" "+c.toString());
    ngets++;
    if (ngets % 100000 == 0) {
      aServer.prta("ECS: gets #=" + ngets);
    }
    return c;
  }

  /**
   * return an Iterator to the channels. Note : this is a
   * Collections.synchronizedMap() wrapping so fast fail
   * ConncurentModificationsExceptions are possible if the user tries to modify
   * the map Which IT SHOULD NOT DO!
   *
   * @return the iterator
   */
  public static TLongObjectIterator<Channel> getIterator() {
    if (chans == null) {
      return null;
    }
    return chans.iterator();
  }

  /**
   * return the status string for this thread
   *
   * @return A string representing status for this thread
   */
  @Override
  public StringBuilder getStatusString() {
    if (statussb.length() > 0) {
      statussb.delete(0, statussb.length());
    }
    return statussb.append(" #gets=").append(ngets).append(" #chan=").append(chans.size()).append("\n");
  }

  /**
   * return the monitor string for ICINGA
   *
   * @return A String representing the monitor key value pairs for this
   * EdgeThread
   */
  @Override
  public StringBuilder getMonitorString() {
    return Util.clear(monitorsb).append("NChan=").append(chans.size()).append("\n");
  }

  /**
   * return console output - this is fully integrated so it never returns
   * anything
   *
   * @return "" since this cannot get output outside of the prt() system
   */
  @Override
  public StringBuilder getConsoleOutput() {
    return consolesb;
  }

  /**
   * creates an new instance of LISSCLient - which will try to stay connected to
   * the host/port source of data. This one gets its arguments from a command
   * line
   *
   * @param argline The startup command line for this instance
   * @param tg The tag to id this thread
   */
  public EdgeChannelServer(String argline, String tg) {
    super(argline, tg);
//initThread(argline,tg);

    updateMS = 120000;
    started = false;
    String[] args = argline.split("\\s");
    if (dbMessageServer != null) {
      prta("Attempt to open a 2nd EdgeChannelServer????? term="
              + (aServer != null ? aServer.terminate + " alive=" + aServer.isAlive() : "null"));
    }

    for (int i = 0; i < 64; i++) {
      sendtos.add(i, null); // Initialize to all nulls
    }
    for (int i = 0; i < 64; i++) {
      flags.add(i, null);
    }
    for (int i = 0; i < 64; i++) {
      hydraflags.add(i, null);
    }
    noDB = false;

    dbg = false;
    dbMessageServer = Util.getProperty("StatusServer");
    for (int i = 0; i < args.length; i++) {
      //prt(i+" arg="+args[i]);
      if (args[i].length() == 0) {
        continue;
      }
      if (args[i].equals("-dbg")) {
        dbg = true;
      } else if (args[i].equals("-empty")) {
        started = false;  // does nothing but suppress warning
      } else if (args[i].equals("-update")) {
        updateMS = Integer.parseInt(args[i + 1]) * 1000;
      } else if (args[i].equals("-dbmsg")) {
        dbMessageServer = args[i + 1];
        i++;
      } else if (args[i].equals("-dbmsgqsize")) {
        dbmsgQsize = Integer.parseInt(args[i + 1]);
        i++;
      } else if (args[i].equals("-dbmsglen")) {
        dbmsgLength = Integer.parseInt(args[i + 1]);
        i++;
      } else if (args[i].equalsIgnoreCase("-master")) {
        dbMaster = true;
      } // else if(args[i].equals("-h")) { host=args[i+1]; i++;}
      else if (args[i].substring(0, 1).equals(">")) {
        break;
      } else {
        prt("EdgeChannelServer: unknown switch=" + args[i] + " ln=" + argline);
      }
    }
    commandServer = new CommandServer("ECHNCS", 7700, (EdgeThread) this, (CommandServerInterface) this);
    // Open DB connection
    // this will keep a connection up to anss
    if (Util.getProperty("DBServer") == null) {
      noDB = true;
    } else if (Util.getProperty("DBServer").equalsIgnoreCase("NoDB")) {
      noDB = true;
    } else if (Util.getProperty("DBServer").equals("")) {
      noDB = true;
    }

    prta("ECS: created args=" + argline + " tag=" + tag + " nodb=" + noDB + " updatems=" + updateMS + " dbSrv=" + dbMessageServer);
    shutdown = new ShutdownEdgeChannelServer();
    Runtime.getRuntime().addShutdownHook(shutdown);
    start();
  }

  /**
   * This Thread keeps a socket open to the host and port, reads in any
   * information on that socket, keeps a list of unique StatusInfo, and updates
   * that list when new data for the unique key comes in.
   */
  @Override
  public void run() {
    int loop = 0;
    aServer = this;
    running = true;
    while ((access = DBAccess.getAccess()) == null) {
      prta(Util.clear(runsb).append("ECS: ****** Access not available! Starting one assuming MySQL! ").
              append(Util.getProperty("DBServer")).append(" nodb=").append(noDB));
      access = new DBAccess((dbMaster ? "-master " : "-empty ") + ">>dbaccess_" + Util.getProcess().toLowerCase(), "DBTH");
    }
    access = DBAccess.getAccess();
    runNoDB();
    running = false;            // Let all know we are not running
    terminate = false;          // sign that a terminate is no longer in progress
  }

  private void runNoDB() {

    boolean first = true;
    boolean done = false;
    long nowtime = System.currentTimeMillis();
    for (int i = 0; i < 600; i++) {
      if (access.isValid()) {
        break;
      }
      try {
        sleep(100);
      } catch (InterruptedException expected) {
      }
      if (i % 100 == 99) {
        prta(Util.clear(runsb).append("ECS: waiting for DBAccess ready ").
                append(System.currentTimeMillis() - nowtime) + " ms");
      }
    }
    prta("ECS: waited for DB/edge_channel.txt for " + (System.currentTimeMillis() - nowtime) + " ms");
    //try {sleep(5000);} catch(InterruptedException expected) {} // let the configuration routine run
    while (!terminate) {
      try {
        for (int i = 0; i < access.getChannelSize(); i++) {
          Channel chan = access.getChannel(i);
          long index = Util.getHashFromSeedname(chan.getChannel().toUpperCase());
          synchronized (chans) {
            chans.put(index, chan);
          }
        }
        prta(Util.clear(runsb).append("ECS: updated ").append(access.getChannelSize()).append(" channels"));
        for (int i = 0; i < access.getSendtoSize(); i++) {
          int ID = access.getSendto(i).getID();
          sendtos.set(ID, access.getSendto(i));
        }
        for (int i = 0; i < access.getFlagSize(); i++) {
          int ID = access.getFlag(i).getID();
          flags.set(ID, access.getFlag(i));
          if (MASK_NOT_PUBLIC == 0 && flags.get(ID).getFlags().contains("No Public Distrib")) {
            MASK_NOT_PUBLIC = 1L << (ID - 1);
            prta(Util.clear(runsb).append("ECS: No public distribution is ID=").append(ID).append(" mask=").append(Util.toHex(MASK_NOT_PUBLIC)));
          } else if (MASK_ARRAY_PORT == 0 && flags.get(ID).getFlags().contains("HydraArrayPort")) {
            MASK_ARRAY_PORT = 1L << (ID - 1);
            prta(Util.clear(runsb).append("ECS: HydraArrayPort is ID=").append(ID).append(" mask=").append(Util.toHex(MASK_ARRAY_PORT)));
          } else if (MASK_NO_EDGEWIRE == 0 && flags.get(ID).getFlags().contains("EdgeWire Disable")) {
            MASK_NO_EDGEWIRE = 1L << (ID - 1);
            prta(Util.clear(runsb).append("ECS: NoEdgeWire is ID=").append(ID).append(" mask=").append(Util.toHex(MASK_NO_EDGEWIRE)));
          } else if (MASK_SMGETTER_FETCH == 0 && (flags.get(ID).getFlags().contains("SMGetter Fetch") || flags.get(ID).getFlags().contains("Not Continuous"))) {
            MASK_SMGETTER_FETCH = 1L << (ID - 1);
            prta(Util.clear(runsb).append("ECS: SeedLink SM is ID=").append(ID).append(" mask=").append(Util.toHex(MASK_SMGETTER_FETCH)));
          } else if (MASK_HAS_METADATA == 0 && flags.get(ID).getFlags().contains("Has MetaData")) {
            MASK_HAS_METADATA = 1L << (ID - 1);
            prta(Util.clear(runsb).append("ECS: Has Metadata is ID=").append(ID).append(" mask=").append(Util.toHex(MASK_HAS_METADATA)));
          }
        }
        for (int i = 0; i < access.getHydraFlagSize(); i++) {
          int ID = access.getHydraFlag(i).getID();
          hydraflags.set(ID, access.getHydraFlag(i));
          if (MASK_NO_NUCLEATION == 0 && hydraflags.get(ID).getHydraFlags().contains("DoNotUseForNucleation")) {
            MASK_NO_NUCLEATION = 1L << (ID - 1);
            prta(Util.clear(runsb).append("ECS: No nucleation is ID=").append(ID).append(" mask=").append(Util.toHex(MASK_NO_NUCLEATION)));
          } else if (MASK_NO_PICKS == 0 && hydraflags.get(ID).getHydraFlags().contains("DoNotUseForPicker")) {
            MASK_NO_PICKS = 1L << (ID - 1);
            prta(Util.clear(runsb).append("ECS: No picker is ID=").append(ID).append(" mask=").append(Util.toHex(MASK_NO_PICKS)));
          } else if (MASK_USE_MWC == 0 && hydraflags.get(ID).getHydraFlags().contains("UseForMwc")) {
            MASK_USE_MWC = 1L << (ID - 1);
            prta(Util.clear(runsb).append("ECS: Use Mwc is ID=").append(ID).append(" mask=").append(Util.toHex(MASK_USE_MWC)));
          } else if (MASK_USE_MWB == 0 && hydraflags.get(ID).getHydraFlags().contains("UseForMwb")) {
            MASK_USE_MWB = 1L << (ID - 1);
            prta(Util.clear(runsb).append("ECS: Use Mwb is ID=").append(ID).append(" mask=").append(Util.toHex(MASK_USE_MWB)));
          } else if (MASK_USE_MWW == 0 && hydraflags.get(ID).getHydraFlags().contains("UseForMww")) {
            MASK_USE_MWW = 1L << (ID - 1);
            prta(Util.clear(runsb).append("ECS: Use Mww is ID=").append(ID).append(" mask=").append(Util.toHex(MASK_USE_MWW)));
          } else if (MASK_MANUAL_NO_PICKS == 0 && hydraflags.get(ID).getHydraFlags().contains("ManualNoPick")) {
            MASK_MANUAL_NO_PICKS = 1L << (ID - 1);
            prta(Util.clear(runsb).append("ECS: Manual No pick is ID=").append(ID).append(" mask=").append(Util.toHex(MASK_MANUAL_NO_PICKS)));
          } else if (MASK_MANUAL_NO_AMPLITUDES == 0 && hydraflags.get(ID).getHydraFlags().contains("ManualNoAmplitudes")) {
            MASK_MANUAL_NO_AMPLITUDES = 1L << (ID - 1);
            prta(Util.clear(runsb).append("ECS: Manual no amplitudes is ID=").append(ID).append(" mask=").append(Util.toHex(MASK_MANUAL_NO_AMPLITUDES)));
          } else if (MASK_USE_LOCAL == 0 && hydraflags.get(ID).getHydraFlags().contains("UseForLocal")) {
            MASK_USE_LOCAL = 1L << (ID - 1);
            prta(Util.clear(runsb).append("ECS: Use for local is ID=").append(ID).append(" mask=").append(Util.toHex(MASK_USE_LOCAL)));
          } else if (MASK_USE_REGIONAL == 0 && hydraflags.get(ID).getHydraFlags().contains("UseForRegional")) {
            MASK_USE_REGIONAL = 1L << (ID - 1);
            prta(Util.clear(runsb).append("ECS: Use for regional is ID=").append(ID).append(" mask=").append(Util.toHex(MASK_USE_REGIONAL)));
          } else if (MASK_USE_TELESEISM == 0 && hydraflags.get(ID).getHydraFlags().contains("UseForTeleseism")) {
            MASK_USE_TELESEISM = 1L << (ID - 1);
            prta(Util.clear(runsb).append("ECS: Use for Teleseism ID=").append(ID).append(" mask=").append(Util.toHex(MASK_USE_TELESEISM)));
          }
        }
        started = true;
        first = false;
        for (int i = 0; i < updateMS / 200; i++) {
          try {
            sleep(200);
          } catch (InterruptedException expected) {
          }
          if (terminate) {
            break;
          }
        }

      } catch (RuntimeException e) {
        prta("Runtime error in EdgeChannelServer.run() continue e=" + e);
        e.printStackTrace(getPrintStream());
      }
    }
  }

  /**
   * Create a new channel if it is unknown with this seedname and rate
   *
   * @param seedname The seedname
   * @param rate The digit rate
   * @param t The object causing this creation
   */
  public static void createNewChannel(CharSequence seedname, double rate, Object t) {
    Channel c = EdgeChannelServer.getChannel(seedname);
    if (c == null) {
      Util.prta("ECHN: ***** newChannel found=" + seedname);
      if (Util.charSequenceIndexOf(seedname, "DUMMY") >= 0) {
        return;
      }
      SendEvent.edgeSMEEvent("ChanNotFnd", "Channel not found2=" + seedname + " new?", t);
      synchronized (dbmsg) {
        Util.clear(dbmsg).append("edge^channel^channel=").append(seedname).
                append(";commgroupid=0;operatorid=0;protocolid=0;sendto=0;flags=0;links=0;delay=0;sort1=new;sort2=;rate=").
                append(rate).append(";nsnnet=0;nsnnode=0;nsnchan=0;created_by=0;lastdata=current_timestamp();created=current_timestamp();");
        Util.prta("ECHN: *** newChannel3 dbmsg=" + dbmsg);
        EdgeChannelServer.createNewChannel(dbmsg);
      }
    }
  }

  public static void createNewChannel(String seedname, double rate, String gaptype, Object t) {
    synchronized (statsb) {
      Util.clear(statsb).append(seedname);
      createNewChannel(statsb, rate, gaptype, t);
    }
  }

  /**
   * Create a new channel if it is unknown with this seedname and rate. These
   * normally come from building lists of channels from SeedLInk servers
   *
   * @param seedname The seedname
   * @param rate The digit rate
   * @param gaptype Gap type - This is only used to create SeedLink SM channels!
   * @param t The object causing this creation
   */
  public static void createNewChannel(CharSequence seedname, double rate, String gaptype, Object t) {
    Channel c = EdgeChannelServer.getChannel(seedname);
    if (c == null) {
      Util.prta("ECHN: *** newChannel found=" + seedname + " gaptype=" + gaptype + " rt=" + rate + " flags=" + Util.toHex(MASK_NO_EDGEWIRE | MASK_NOT_PUBLIC | MASK_SMGETTER_FETCH));
      SendEvent.edgeSMEEvent("ChanNotFnd", "Channel not found3=" + seedname + " new?", t);
      if (Util.charSequenceIndexOf(seedname, "DUMMY") >= 0) {
        return;
      }
      //SendEvent.edgeSMEEvent("ChanNotFnd","TraceBuf Channel not found2="+seedname+" new?", t
      synchronized (dbmsg) {
        Util.clear(dbmsg).append("edge^channel^channel=").append(seedname).
                append(";commgroupid=0;operatorid=0;protocolid=0;sendto=0;flags=").
                append(MASK_NO_EDGEWIRE | MASK_NOT_PUBLIC | MASK_SMGETTER_FETCH).
                append(";links=0;delay=0;" + "sort1=new;sort2=;rate=").append(rate).
                append(";gaptype=").append(gaptype == null ? "" : gaptype).
                append(";nsnnet=0;nsnnode=0;nsnchan=0;created_by=0;created=current_timestamp();");
        EdgeChannelServer.createNewChannel(dbmsg);
      }
    }
  }
  /**
   * this accepts a line in the format for DBServer and sends it to the database
   * and also puts it in the local copy so that it shows up locally after
   * validating its a good seedname for channel
   *
   * @param msg The message to use to create the new channel, its in
   * MySQLQueuedClientServer format
   */
  private static final StringBuilder keytmp = new StringBuilder(20);
  private static final StringBuilder valuetmp = new StringBuilder(100);
  public static final Timestamp now = new Timestamp(100000L);

  public static void createNewChannel(StringBuilder msg) {
    if (aServer != null) {
      aServer.prta("ECS: *** attempt newChannel creation to " + Util.getProperty("StatusServer") + " from " + msg);
      new RuntimeException("ECS: new channel creation with message msg=" + msg + "|").printStackTrace(aServer.getPrintStream());
    } else {
      Util.prta("ECS: *** attempt newChannel2 creation to " + Util.getProperty("StatusServer") + " from " + msg);
      new RuntimeException("ECS: new channel creation with message msg=" + msg + "|").printStackTrace();
    }
    int ipnt = msg.indexOf("channel=");
    if (ipnt >= 0) {
      String tmp = msg.substring(ipnt);
      ipnt = tmp.indexOf(";");
      if (ipnt >= 0) {
        tmp = tmp.substring(0, ipnt);
        ipnt = tmp.indexOf("=");
        tmp = tmp.substring(ipnt + 1);
        String tmp2 = "0.5";
        ipnt = msg.indexOf("rate=");
        if (ipnt > 0) {
          tmp2 = msg.substring(ipnt + 5);
          ipnt = tmp2.indexOf(";");
          if (ipnt > 0) {
            tmp2 = tmp2.substring(0, ipnt);
          }
        }
        if (aServer != null) {
          aServer.prta("ECS: * create channel=" + tmp + " rate=" + tmp2);
        } else {
          Util.prta("ECS: create channel=" + tmp + " rate=" + tmp2);
        }
        if (!Util.isValidSeedName((tmp + "         ").substring(0, 12))) {
          if (aServer != null) {
            aServer.prta("ECS: *** attempt to create illegal seedname=" + msg);
          } else {
            Util.prta("ECS: *** attempt to create illegal seedname=" + msg);
          }
          return;
        }
        // Create the connection to a MySQLServer if it does not exist

        double rate = .5;
        try {
          rate = Double.parseDouble(tmp2);
        } catch (NumberFormatException e) {
          if (aServer != null) {
            aServer.prta("ECS: could not parse rate tmp2=" + tmp2);
          }
        }
        long mask = sendtoMask(tmp);
        // Note the id is zero for this temporary channel
        Channel c = new Channel(0, tmp, 0, 0, 0, mask, 0L, 0L, 0, "", "", rate, 0, 0, 0, 0, "", new Timestamp(System.currentTimeMillis()), 0L, 0L, 0., new Timestamp(System.currentTimeMillis()));
        synchronized (chans) {
          if (aServer != null) {
            aServer.prta("ECS:  * create channel to list " + c.getChannel() + " rt=" + c.getRate() + " sendto=" + c.getSendtoMask());
          } else {
            Util.prta("ECS:  * create channel to list " + c.getChannel() + " rt=" + c.getRate() + " sendto=" + c.getSendtoMask());
          }
          chans.put(Util.getHashFromSeedname(tmp.toUpperCase()), c);
          ///c = chans.get(Util.getHashFromSeedname(tmp.toUpperCase()));
        }
        // check to make sure c was created
        //if(c == null)
        //  if(aServer != null) aServer.prta("ECS: *** Impossible I just created "+tmp+" but it is not there!");
        //  else Util.prta("ECS: *** Impossible I just created "+tmp+" but it is not there!");

        // In the noDB case we need to update the channel table
        try {
          DBTable channel = access.getChannelDBTable();
          if (noDB) {
            if (aServer != null) {
              aServer.prta("ECS:  ***create channel to DBTable " + c.getChannel() + " rt=" + c.getRate() + " sendto=" + c.getSendtoMask());
            } else {
              Util.prta("ECS:  * create channel to DBTable" + c.getChannel() + " rt=" + c.getRate() + " sendto=" + c.getSendtoMask());
            }

            synchronized (channel) {
              channel.newRow();
              //String [] parts = msg.toString().split(";");
              //for(int i=1; i<parts.length; i++) {
              int lastcaret = msg.lastIndexOf("^");
              int state = 0;      // getting a key
              Util.clear(keytmp);
              Util.clear(valuetmp);
              for (int i = lastcaret + 1; i < msg.length(); i++) {
                if (state == 0) {
                  if (msg.charAt(i) == '=' || msg.charAt(i) == ';' || msg.charAt(i) == '\n') {
                    state = 1;
                  } else {
                    keytmp.append(msg.charAt(i));    // build up the key
                  }
                }
                if (state == 1) {       // value state
                  if (msg.charAt(i) == ';' || msg.charAt(i) == '\n') {
                    state = 2;
                  } else {
                    valuetmp.append(msg.charAt(i));    // add to the value
                  }
                }
                if (state == 2) {
                  Util.prt(keytmp + " " + valuetmp);
                  if (!Util.stringBuilderEqual(keytmp, "rate") && !Util.stringBuilderEqual(keytmp, "sendto")
                          && !Util.stringBuilderEqual(keytmp, "channel")) {
                    if (Util.stringBuilderEqual(valuetmp, "current_timestamp()") || Util.stringBuilderEqual(valuetmp, "now()")) {
                      Util.clear(valuetmp).append(Util.ascdatetime(System.currentTimeMillis()).substring(0, 19));
                    }
                    channel.updateString(keytmp, valuetmp);
                  }
                }
              }
              channel.updateString("channel", tmp.trim());
              channel.updateString("rate", Util.df24(rate));
              now.setTime(System.currentTimeMillis());
              channel.updateInt("commgroupid", 0);
              channel.updateInt("operatorid", 0);
              channel.updateInt("protocolid", 0);
              channel.updateInt("flags", 0);
              channel.updateInt("links", 0);
              channel.updateInt("delay", 0);

              channel.updateInt("nsnnet", 0);
              channel.updateInt("nsnnode", 0);
              channel.updateInt("nsnchan", 0);
              channel.updateInt("expected", 0);
              channel.updateInt("mdsflags", 0);
              channel.updateInt("hydraflags", 0);
              channel.updateInt("hydravalue", 0);
              channel.updateInt("created_by", 0);

              channel.updateTimestamp("lastdata", now);
              channel.updateTimestamp("updated", now);
              channel.updateTimestamp("created", now);
              channel.updateLong("sendto", mask);
              if (aServer != null) {
                aServer.prta("ECS:  *** create channel updateRow " + c.getChannel() + " rt=" + c.getRate() + " sendto=" + c.getSendtoMask() + channel.getFilename());
              } else {
                Util.prta("ECS:  * create channel updateRow " + c.getChannel() + " rt=" + c.getRate() + " sendto=" + c.getSendtoMask());
              }
              channel.updateRow();
              if (aServer != null) {
                aServer.prta("ECS:  *** create channel writeTable " + c.getChannel() + " rt=" + c.getRate() + " sendto=" + c.getSendtoMask());
              } else {
                Util.prta("ECS:  * create channel writeTable " + c.getChannel() + " rt=" + c.getRate() + " sendto=" + c.getSendtoMask());
              }
              channel.writeTable();
              if (aServer != null) {
                aServer.prta("ECS:  *** create channel writeTable done " + c.getChannel() + " rt=" + c.getRate() + " sendto=" + c.getSendtoMask());
              } else {
                Util.prta("ECS:  * create channel writeTable done " + c.getChannel() + " rt=" + c.getRate() + " sendto=" + c.getSendtoMask());
              }
            }
          } else {    // We are in DB mode, need to send this out.
            int ind = msg.indexOf("sendto=0");
            if (ind >= 0) {
              //msg = msg.replaceAll("sendto=0","sendto="+mask);  // add RLISS and IRISDMC
              if (mask != 0) {
                Util.stringBuilderReplaceAll(msg, "sendto=0", "sendto=" + mask);
                SendEvent.edgeSMEEvent("SendtoOvr", "Sendto IRIS/DMC on " + tmp + " " + Util.toHex(mask), "EdgeChannelServer");
              }
            }
            if (dbMessageQueue == null) {
              dbMessageQueue = new DBMessageQueuedClient(dbMessageServer, 7985, dbmsgQsize, dbmsgLength, aServer);  // 100 message queue, 300 in length
              if (aServer != null) {
                aServer.prta("ECS: message Server to " + dbMessageServer + "/" + 7985 + " created");
              } else {
                Util.prta("ECS: DBMessageQueuedClient to " + dbMessageServer + "/" + 7985 + " created " + dbMessageQueue);
              }

            }
            // If DB updates are on,send this update out.
            if (dbMessageQueue != null) {
              dbMessageQueue.queueDBMsg(msg); // Send to message server  to create new empty channel
              if (aServer != null) {
                aServer.prta("ECS: * create channel messaged queued dbmsg=" + dbMessageQueue);
              } else {
                Util.prta("ECS: * create channel messaged queued dbmsg=" + dbMessageQueue);
              }
              channel.setInsertOccurred(true);
            }
          }     // else - it is a DB mode
          if (aServer != null) {
            aServer.prta("ECS: * create channel queue " + dbMessageQueue + " =" + msg);
          } else {
            Util.prta("ECS: * create channel queue " + dbMessageQueue + " =" + msg);
          }
        } catch (IOException e) {
          if (aServer != null) {
            e.printStackTrace(aServer.getPrintStream());
          } else {
            e.printStackTrace();
          }
          SendEvent.edgeSMEEvent("ECSNoDBErrWr", "EdgeChannelServer in NoDB mode could not write to channel file", "EdgeChannelServer");
        } catch (RuntimeException e) {
          if (aServer != null) {
            e.printStackTrace(aServer.getPrintStream());
          } else {
            e.printStackTrace();
          }
          SendEvent.edgeSMEEvent("ECSNoDBErrWr", "EdgeChannel server in NoDB mode had runtime e=" + e, "EdgeChannelServer");
        }
      } else {
        if (aServer != null) {
          aServer.prta("ECS: * create channel message is illegal. Does not contain channel tag" + msg);
        } else {
          Util.prta("ECS: * create channel message is illegal. Does not contain channel tag" + msg);
        }
      }

    }

    // Pick the channel name out and create a channel for our TreeMap of channels.
  }

  public static long sendtoMask(String tmp) {
    long mask = 0;
    for (int i = 0; i < 64; i++) {
      try {
        if (sendtos.get(i) != null) {
          //Util.prt("ECS: mask sendto for "+tmp.trim()+"|"+Util.toHex(sendtos.get(i).getAutoSetMask(tmp.trim()))+
          //        " "+sendtos.get(i).getAutoSet()+" match="+tmp.trim().matches(sendtos.get(i).getAutoSet()));
          mask |= sendtos.get(i).getAutoSetMask(tmp.trim());
        }
      } catch (RuntimeException e) {
        Util.prta("Runtime doing sento masks e=" + e);
        SendEvent.edgeSMEEvent("SendtoAutoRT", "Runtime doing auto sendto " + e, "EdgeChannelServer");
      }
    }
    return mask;
  }

  /**
   * this internal class gets registered with the Runtime shutdown system and is
   * invoked when the system is shutting down. This must cause the thread to
   * exit
   */
  class ShutdownEdgeChannelServer extends Thread {

    /**
     * default constructor does nothing the shutdown hook starts the run()
     * thread
     */
    public ShutdownEdgeChannelServer() {
      gov.usgs.anss.util.Util.prta("new ThreadShutdown " + getName() + " " + getClass().getSimpleName());
    }

    // This is called by the RunTime.shutdown at any termination 
    @Override
    public void run() {
      System.err.println(Util.asctime() + " ECS: EdgeChannelServer Shutdown() started...");
      if (aServer != null) {
        prta("ECS: EdgeChannelServer Shutdown() started...");
      }
      terminate();          // send terminate to main thread and cause interrupt
      int loop = 0;
      if (dbMessageQueue != null) {
        dbMessageQueue.terminate();
      }
      while (running) {
        // Do stuff to aid the shutdown
        try {
          sleep(1000);
        } catch (InterruptedException e) {
        }
        loop++;
      }
      System.err.println(Util.asctime() + " ECS: Shutdown() of EdgeChannelServer is complete.");
      if (aServer != null) {
        prta("ECS: Shutdown() of EdgeChannelServer is complete.");
      }
    }
  }

  static public void main(String[] args) {
    EdgeThread.setUseConsole(true);
    String chan = "USDUG  BHZ";
    String regexp = "US.....[BHVL][HN][ZNE12].*|IW.....[BHVL][HN][ZNE12].*";
    Util.prt(chan + "|" + regexp + "|" + chan.matches(regexp));
    EdgeChannelServer ecs = new EdgeChannelServer("-update 20 -empty", "ECS");
    while (!EdgeChannelServer.isValid()) {
      Util.sleep(100);
    }
    ecs.setDebug(false);
    boolean ok = true;
    while (ok) {
      Util.sleep(1000);
    }
    StringBuilder sb = new StringBuilder(200);
    sb.append("edge^channel^channel=" + "USDUG  HH301"
            + ";commgroupid=0;operatorid=0;protocolid=0;sendto=0;flags=0;links=0;delay=0;"
            + "sort1=new;sort2=;rate=" + "200.001"
            + ";nsnnet=0;nsnnode=0;nsnchan=0;created_by=0;"
            + "created=current_timestamp();");
    EdgeChannelServer.createNewChannel(sb);
    Random ran = new Random();
    ArrayList<String> list = new ArrayList<String>(5500);
    for (;;) {
      try {
        Thread.sleep(3000);
      } catch (InterruptedException expected) {
      }
      Channel c = EdgeChannelServer.getChannel("USISCO BHZ  ");
      if (c != null) {
        Util.prta(c.getChannel() + " " + Util.toHex(c.getSendtoMask()) + " " + c.getNsnNetwork() + "-"
                + c.getNsnNode() + "-" + c.getNsnChan() + " " + c.getRate() + " " + c.getID());
      } else {
        Util.prta("Not expected to be NULL!");
      }
      c = EdgeChannelServer.getChannel("ZZZZZZZZZZZZ");
      if (c != null) {
        Util.prta("All Z returned ca channel!!!!" + c.getChannel());
      }
      TLongObjectIterator<Channel> itr = EdgeChannelServer.getIterator();
      while (itr.hasNext()) {
        itr.advance();
        list.add(itr.value().getChannel());
      }
      for (int i = 0; i < list.size(); i++) {
        if (list.get(i).contains("null")) {
          Util.prt(i + "=" + list.get(i));
        }
      }

      Util.prta("Start 50000 lookups");

      for (int i = 0; i < 50000; i++) {
        String n = list.get((int) (ran.nextFloat() * list.size()));
        c = EdgeChannelServer.getChannel(n);
        if (c == null) {
          Util.prta("did not find " + n);
        }
        if (i > 50000) {
          Util.prt(n + " c=" + c);
        }
      }
      Util.prta("End 50000 lookups");
    }
  }
}
