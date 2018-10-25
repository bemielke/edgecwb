/*
 *  This software is in the public domain because it contains materials 
 *  that originally came from the United States Geological Survey, 
 *  an agency of the United States Department of Interior. For more 
 *  information, see the official USGS copyright policy at 
 *  http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.edgemom;

import gov.usgs.alarm.SendEvent;
import gov.usgs.anss.edgethread.EdgeThread;
import gov.usgs.anss.util.Util;
import gnu.trove.map.hash.TLongObjectHashMap;
import gnu.trove.iterator.TLongObjectIterator;
import gov.usgs.anss.db.DBMessageQueuedClient;
import java.io.IOException;
import java.io.BufferedReader;
import java.io.FileReader;

/**
 * This class implements the finding of gaps in data which arrives in order. The user needs to call
 * the static process function with the information about each data packets similar to sending data
 * via ChannelSender to UdpChannel. The class maintains a list of channels, and processes the
 * information through the GapsFromInorderChannel object created for each new one.
 * <br>
 *
 * <PRE>
 * switch    value        description
 * -config  keyfile   Set the key-value file name to this instead of the default "config/edgemom.config"
 * -prop    propfile  If another property file is needed to complete the conversion (def=edge.prop)
 * -once              If present only run the conversion once and then exit
 * -wait    secs      The number of seconds to wait between passes (def=120)
 * -nodate            If present, the key and property files check for modified dates and the files are always read.
 * </PRE>
 *
 * @author U.S. Geological Survey <ketchum at usgs.gov>
 */
public final class GapsFromInorderMaker extends EdgeThread {

  private static final TLongObjectHashMap<GapsFromInorderChannel> chans = new TLongObjectHashMap<GapsFromInorderChannel>();
  private static GapsFromInorderMaker theThread;
  public static boolean noDB;
  private final DBMessageQueuedClient messageServer;
  private static final String db = "fetcher";
  private static String table = "fetchlistrt";      // the table the gaps go into
  private final StringBuilder sb = new StringBuilder(10000);
  private String dbmsgserver;
  private static String statusFile;
  private int ngap;
  private boolean dbg;

  /**
   * return the monitor string for Nagios
   *
   * @return A String representing the monitor key value pairs for this EdgeThread
   */
  @Override
  public StringBuilder getMonitorString() {
    Util.clear(monitorsb);
    return monitorsb.append(tag).append(" #gap=").append(ngap).append(" #chan=").append(chans.size());
  }

  @Override
  public StringBuilder getStatusString() {
    Util.clear(statussb);
    return statussb.append(tag).append(" #gap=").append(ngap).append(" #chan=").append(chans.size());
  }

  @Override
  public void terminate() {
    prta(tag + " terminate called");
    terminate = true;
    interrupt();
  }

  @Override
  public StringBuilder getConsoleOutput() {
    if (consolesb.length() > 0) {
      consolesb.delete(0, consolesb.length());
    }
    return consolesb;
  }

  public GapsFromInorderMaker(String argline, String tg) {
    super(argline, tg);
    if (theThread != null) {
      prta("Attempt to create a 2nd GapsFromInorderMaker - this should not happen");
      throw new RuntimeException("Attempt to create a 2nd GapsFromInorderMaker!");
    }
    theThread = (GapsFromInorderMaker) this;
    String[] args = argline.split("\\s");
    dbmsgserver = Util.getProperty("StatusServer");
    for (int i = 0; i < args.length; i++) {
      if (args[i].trim().equals("")) {
        continue;
      }
      if (args[i].equalsIgnoreCase("-dbmsg")) {
        dbmsgserver = args[i + 1];
      } else if (args[i].equals("-table")) {
        table = args[i + 1];
        i++;
      } else if (args[i].equalsIgnoreCase("-empty")) ; else if (args[i].equalsIgnoreCase("-dbg")) {
        dbg = true;
      } else if (args[i].startsWith(">")) {
        break;
      } else {
        Util.prta("GFIM: ** Unknown switch =" + args[i]);
      }
    }
    if (dbmsgserver.contains("/")) {
      dbmsgserver = dbmsgserver.substring(0, dbmsgserver.indexOf("/"));
    }
    messageServer = new DBMessageQueuedClient(dbmsgserver, 7985, 500, 300, this);  // 100 message queue, 300 in length
    statusFile = "config/" + tg + "_" + EdgeMom.getInstance() + ".state";
    prt("GFIM: message Server to " + dbmsgserver + "/" + 7985 + " created " + statusFile + " table=" + table);
    Runtime.getRuntime().addShutdownHook(new ShutdownGFIM());
    restoreState();
    running = true;
    start();
  }
  private static final StringBuilder status = new StringBuilder(2000);

  private void restoreState() {
    try {
      try (BufferedReader in = new BufferedReader(new FileReader(statusFile))) {
        String line;
        while ((line = in.readLine()) != null) {
          String[] parts = line.split(",");
          Util.clear(sb).append(parts[0]);
          //                 desired               seed nsamp rate,                   gaptype
          processChannel(Long.parseLong(parts[2]), sb, 0, Double.parseDouble(parts[3]), parts[1]);
        }
      }
      prta("Restore files #chan=" + chans.size());
    } catch (IOException e) {
      e.printStackTrace(getPrintStream());
    }
  }

  public static void saveState() throws IOException {
    Util.clear(status);
    TLongObjectIterator<GapsFromInorderChannel> itr = chans.iterator();
    while (itr.hasNext()) {
      itr.advance();
      GapsFromInorderChannel ch = itr.value();
      if (ch.getGapType() != null) {
        status.append(ch.getSeedname()).append(",").append(ch.getGapType()).append(",").
                append(ch.getDesired()).append(",").append(ch.getRate()).append("\n");
      }
    }
    Util.writeFileFromSB(statusFile, status);
  }
  byte[] buf = new byte[200];

  /**
   * Add a gap to the database.gap table through the dbase messaging service
   *
   * @param seed A SB with 12 character NSCL
   * @param time Time in millis
   * @param duration Duration in seconds
   * @param gapType Two character gap type
   */
  public void addGap(StringBuilder seed, long time, double duration, String gapType) {
    synchronized (messageServer) {
      Util.clear(sb).append(db).append("^").append(table).append("^seedname=").append(seed).
              append(";type=").append(gapType).append(";start=").append(Util.ascdatetime(time / 1000 * 1000)).
              append(";start_ms=").append(time % 1000).append(";duration=").append(duration).
              append(";status=open;created_by=0;created=current_timestamp();");
      Util.stringBuilderToBuf(sb, buf);
      messageServer.queueDBMsg(buf, 0, sb.length());
      prta("Send Gap : " + sb);
    }
    ngap++;
  }

  @Override
  public void run() {
    long loop = 0;
    StringBuilder runsb = new StringBuilder(100);
    while (!terminate) {
      try {
        sleep(1000);
      } catch (InterruptedException expected) {
      }
      loop++;
      if (terminate) {
        break;
      }
      if (loop % 120 == 0) {
        prta(getStatusString());
        try {
          saveState();
          prta("Save state len=" + status.length() + " #ch=" + chans.size());
        } catch (IOException e) {
          e.printStackTrace(getPrintStream());
          SendEvent.edgeSMEEvent("GFIMStateFile", "Could not write statefile " + statusFile, this);
        }
      }
    }
    try {
      saveState();
    } catch (IOException e) {
      e.printStackTrace(getPrintStream());
    }
    prta("GFIM: exiting terminate=" + terminate + " #ch=" + chans.size() + " state=" + status.length());
    running = false;
  }

  /**
   * Process a packet
   *
   * @param time In millis
   * @param seedname 12 character NSCL
   * @param nsamp Number of samples
   * @param rate Data rate in Hz
   * @param gaptype GapType to create if this channel has a gap
   */
  static public void processChannel(long time, StringBuilder seedname, int nsamp, double rate, String gaptype) {
    if (seedname == null || rate == 0.) {
      return;
    }
    if (theThread == null) {
      theThread = new GapsFromInorderMaker("-empty >> gfim", "GFIM");
    }
    // The GapFromInorderChannel process is looking for gaps and gathering statistics
    GapsFromInorderChannel ch;
    synchronized (chans) {
      ch = chans.get(Util.getHashFromSeedname(seedname));
      if (ch == null) {
        ch = new GapsFromInorderChannel(seedname, time, nsamp, rate, gaptype, theThread);
        chans.put(Util.getHashFromSeedname(seedname), ch);
        return;
      }
      if (ch.getGapType() == null && gaptype == null) ; else if (gaptype == null) ; else if (ch.getGapType() == null) {
        theThread.prta("** weird gap type for " + seedname + " to " + gaptype + " ch=" + ch.getGapType());
      } else if (!ch.getGapType().equals(gaptype)) {
        theThread.prta("** change gap type for " + seedname + " to " + gaptype + " from " + ch.getGapType());
        ch.setGapType(gaptype);
      }
    }
    ch.process(seedname, time, nsamp, rate);    // Use the channel object to track the gaps/overlaps.
  }

  private class ShutdownGFIM extends Thread {

    public ShutdownGFIM() {

    }

    /**
     * this is called by the Runtime.shutdown during the shutdown sequence cause all cleanup actions
     * to occur
     */
    @Override
    public void run() {
      terminate();
      Util.prta("GFIM:Shutdown Done. CLient c");

    }
  }

  public static void main(String[] args) {
    Util.setModeGMT();
    Util.setNoconsole(false);
    Util.setNoInteractive(true);
    Util.init("edge.prop");
    String argline = "";
    StringBuilder seed = new StringBuilder(12);
    seed.append("USISCO BHZ99");
    for (String arg : args) {
      argline += arg + " ";
    }
    if (argline.equals("")) {
      argline = "-dbmsg localhost >>gfim";
    } else {
      argline += " >>gfim";
    }

    GapsFromInorderMaker config = new GapsFromInorderMaker(argline, "GFIMC");
    long time = System.currentTimeMillis() - 300000;
    GapsFromInorderMaker.processChannel(time, seed, 100, 40., "ZP");
    time = time + (long) (100 / 40. * 1000. + 0.5);
    GapsFromInorderMaker.processChannel(time, seed, 100, 40., "ZP");
    time = time + 300000;
    GapsFromInorderMaker.processChannel(time, seed, 100, 40., "ZP");

    while (config.isRunning()) {
      Util.sleep(200);
    }
    System.exit(0);

  }
}
