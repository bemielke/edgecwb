/*
 *  This software is in the public domain because it contains materials 
 *  that originally came from the United States Geological Survey, 
 *  an agency of the United States Department of Interior. For more 
 *  information, see the official USGS copyright policy at 
 *  http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.edge;

import gov.usgs.anss.edgeoutput.DeleteBlockInfo;
import gov.usgs.anss.cwbqueryclient.EdgeQueryClient;
import gov.usgs.anss.cwbqueryclient.ZeroFilledSpan;
import gov.usgs.anss.seed.*;
import gov.usgs.anss.util.Util;
import java.io.*;
import java.net.*;
import java.nio.*;
import java.util.*;

/**
 * This class implements the delete and replace functions in a CWB. If the user call this correctly,
 * they can reuse space in the CWB by using delete() to the data, and then reusing the object to
 * replace() data in the blocks found by the delete().
 * <p>
 * This class encapsulates connections to a QueryServer for deleting the blocks and a connection to
 * a MiniSeedServer for inserting blocks. These have to be two different connections because
 * QueryServer and EdgeMom (which would run the MiniSeedServer thread), are separate processes.
 * <p>
 * Usage:<br>
 * <b>Deleting a time range of data</b> - with the delete() method.
 *
 * <p>
 * <b>Insert new data into the CWB</b> - use replace() without calling delete() first. These blocks
 * will be added to the CWB, but if they duplicate the time span, both the original and the newly
 * inserted data will be in the CWB. This is a mess, so be sure that the channel does not exist
 * before doing this.
 * <p>
 * <b>Delete data and replace it with other data</b> - use delete() and then replace() on the same
 * time range of data. Method replace() can be called many times after a delete() for the same
 * channel and time series in blocks which can be replaces will be. For instance if the data is
 * available in separate time spans with gaps between them, call delete() for the entire interval
 * and then call replace() with each time span.
 * <p>
 * Details :<br>
 * Only two methods are normally called by the user - delete(), and replace(). When the delete is
 * called the blocks are deleted during a session with the QueryServer which returns information
 * about the blocks deleted (basically how to find these blocks in the index of the file). If
 * replace() is called after a delete on the same object, the blocks will be checked for consistency
 * with any and all delete blocks created by prior deletes. If any of the blocks match the channel
 * "seedname" and time etc of a deleted block, the block is actually written over the deleted block.
 * If no deleted blocks are found for a replace() generated block, the block is added to the CWB
 * datafile in the usual way. The list of blocks for deletion contain all of the deletes for all
 * channels done through the delete() method on this object, so it is possible to delete many
 * channels, and then replace those channels in any order as long as the same object is used. If the
 * context of deleted blocks needs to be cleared, call either clearDeletedBlocks() on th NSCL or
 * clearAllDeletedBlocks() to clear the list of deleted blocks for all channels.
 *
 *
 * @author U.S. Geological Survey <ketchum at usgs.gov>
 */
public class CWBEditor implements MiniSeedOutputHandler {

  /**
   * The value of the Y2K epochs in milliseconds back to 1970
   */
  public static long year2000MS;          // On initialization this is set to the year2000 epoch in Millis
  /**
   * The value of the y2kepoch in double seconds since 1970
   */
  public static double year2000sec;       // On initializaiton this is set to the year 2000 epoch in decimal seconds
  /**
   * The value accepted for data which is missing (i.e. part of a gap()
   */
  public static final int NO_DATA = Integer.MIN_VALUE;// The value representing no data.
  private static boolean init;            // Indicates init() has been executed
  private final TreeMap<String, ArrayList<DeleteBlockInfo>> channels = new TreeMap<String, ArrayList<DeleteBlockInfo>>();
  private final String cwbhost;           // The host where the CWB QueryServer and MiniSeedServer thread are running
  private int cwbport;              // Port for the QueryServer (normally 2061)
  private final int insertport;           // Port for the MiniSeedServer
  private Socket cwbsock;           // Socket connection to the QueryServer
  private Socket insertSock;        // Socket connection to the MiniSeedServer
  private final int[] d = new int[1000]; // Some scratch space for replace() to use for building miniSeed from int []
  private final byte[] b = new byte[DeleteBlockInfo.DELETE_BLOCK_SIZE];// Scratch buffer for decoding delete info from QueryServer
  private final ByteBuffer bb;            // ByteBuffer on b[] to makde decoding easier
  private final ArrayList<MiniSeed> msblks = new ArrayList<MiniSeed>(1000);// ArrayList of miniseed blocks built from raw timeseries in replace()
  private final DeleteBlockInfo dbi;      // Scratch example for a DeleteBlockInfo
  private boolean dbg;

  public void setDebug(boolean b) {
    dbg = b;
  }

  /**
   * Create an instance of a CWBEditor which can delete and replace data in a CWB given the ip or
   * host name translatable by DNS, the query port (def=2061), and the port of a MiniSeedServer.
   *
   * @param host The single host which is running a QueryServer and an MiniSeedServer thread for use
   * by this utility
   * @param queryport The port of the QueryServer (default=2061 if set to <=0)
   * @param insertPort The port of the MiniSeedServer - there is no default port, make sure a
   * MiniSeedServer is running on this port
   * @throws IOException Usually if the connections to the QueryServer or MiniSeedServer cannot be
   * made.
   */
  public CWBEditor(String host, int queryport, int insertPort) throws IOException {
    init();
    cwbhost = host;
    cwbport = queryport;
    insertport = insertPort;
    if (cwbport <= 0) {
      cwbport = 2061;                // Set default for queryserver port
    }
    cwbsock = new Socket(cwbhost, cwbport);       // Make a connection to the QueryServer
    //insertSock = new Socket(cwbhost, insertport); // Make connection to a MiniSeedServer port
    bb = ByteBuffer.wrap(b);
    bb.position(0);
    dbi = new DeleteBlockInfo(bb);
  }

  /**
   * Clear all information about all channels with deleted blocks for this object. After this call,
   * no data will be able to reuse these blocks.
   */
  public void clearAllDeletedBlocks() {
    Iterator<ArrayList<DeleteBlockInfo>> itr = channels.values().iterator();
    while (itr.hasNext()) {
      itr.next().clear();
      itr.remove();
    }
  }

  /**
   * Clear the list of deleted blocks associated with this single channel
   *
   * @param net Two character network code
   * @param stat 3-5 character station code
   * @param chan 3 character SEED channel code
   * @param loc 0-2 character location code
   * @return
   */
  public boolean clearDeletedBlocks(String net, String stat, String chan, String loc) {
    String seedname = makeSeedName(net, stat, chan, loc);
    seedname = seedname.replaceAll("-", " ");
    ArrayList<DeleteBlockInfo> dels = channels.get(seedname);
    if (dels != null) {
      dels.clear();
      return true;
    }
    return false;
  }

  /**
   * This is executed once when the first object of this type is created. Skipped by later
   * instantiations.
   */
  private static void init() {
    if (init) {
      return;
    }
    Util.setModeGMT();                            // We work in UTC time always
    GregorianCalendar g = new GregorianCalendar(2000, 0, 1);  // Get a calendar for 2000/01/01
    g.setTimeInMillis(g.getTimeInMillis() / 86400000L * 86400000/*L+86400000L/2*/);// what is the wierd noon for?
    year2000MS = g.getTimeInMillis();
    year2000sec = year2000MS / 1000.;

  }

  /**
   * List all of the blocks on the delete list
   *
   * @return A string builder with the list of deleted blocks.
   */
  public StringBuilder listDeletedBlocks() {
    StringBuilder sb = new StringBuilder(1000);
    Iterator<ArrayList<DeleteBlockInfo>> itr = channels.values().iterator();
    while (itr.hasNext()) {
      ArrayList<DeleteBlockInfo> list = itr.next();
      for (int i = 0; i < list.size(); i++) {
        sb.append(list.get(i).toString()).append(" ").append(list.get(i).getIndexString()).append("\n");
      }
    }
    return sb;
  }

  /**
   * Delete data for a channel for the time frame given. The CWB QueryServer gets this request and
   * marks all of the blocks as deleted on disk and returns the structures needed for these blocks
   * to potentially be reused. A list of all such blocks deleted but not yet reused is maintained
   * for each SEED channel.
   * <p>
   * There must be at least two MiniSeed blocks on the server that are to be deleted/modified. A
   * replace within a single block is not supported as this software cannot break the block into two
   * pieces as it cannot add anew block to the file.
   * <br>
   * HINT : it is best to make the time codes be a little before the first sample and a little after
   * the last sample to delete. Typical usage would be to take the time of the first known sample
   * and correct the time by 1/2 of digitizing period and set the end time to this beginning time
   * plus the number of samples times the digitizing period. Do so avoids round off problem in the
   * milliseconds field from deciding to include a sample or exclude it mysteriously!
   *
   * @param net 2 character network code
   * @param station up to 5 character station code
   * @param chan 3 character channel code
   * @param loc two character location code (will default to blanks)
   * @param y2kstart The start time in seconds since 2000/01/01
   * @param y2kend The ending time in seconds since 2000/01/01
   * @return number of blocks deleted, if less than 0, then this was within a single block which
   * cannot be delete!
   * @throws IOException
   */
  public int delete(String net, String station, String chan, String loc, double y2kstart, double y2kend) throws IOException {
    String seedname = makeSeedName(net, station, chan, loc);
    String s = "'-eqdbg' '-s' '" + seedname.replaceAll(" ", "-") + "' '-b' '" + y2kToString(y2kstart) + "' '-d' '" + (y2kend - y2kstart) + "' '-delete'\t";
    int ndelete = 0;
    ArrayList<DeleteBlockInfo> list = null;
    try {
      if (cwbsock == null) {
        cwbsock = new Socket(cwbhost, cwbport);
      }
      if (cwbsock.isClosed()) {
        cwbsock = new Socket(cwbhost, cwbport);
      }
      cwbsock.getOutputStream().write(s.getBytes()); // send the delete to the QueryServer

      // Until EOF read in the blocks deleted and now free
      boolean eof = false;
      while (!eof) {
        eof = read(cwbsock.getInputStream(), b, 0, DeleteBlockInfo.DELETE_BLOCK_SIZE);// read a block in binary
        if (dbg) {
          Util.prt("Read() returned eof=" + eof);
        }
        if (eof) {
          break;  // DONE
        }
        if (b[0] == '<' && b[1] == 'E' && b[2] == 'O' && b[3] == 'R' && b[4] == '>') {
          break;
        }
        if (b[0] == '<' && b[1] == 'E' && b[2] == 'R' && b[3] == 'R' && b[4] == '>') {
          Util.prt(" **  Error occurred deleting range - No space freed");
          ndelete = -1000;
          continue;
        }

        bb.position(0);       // creat a new DeleteBlockInfo
        DeleteBlockInfo dblk = new DeleteBlockInfo(bb);
        list = channels.get(dblk.getSeedname()); // Does this seename exist
        if (list == null) {  // No create it and add it to the list
          list = new ArrayList<DeleteBlockInfo>(100);
          channels.put(dblk.getSeedname(), list);
          if (dbg) {
            Util.prt("Create new channel " + dblk.getSeedname() + "|");
          }
        }
        boolean ok = insert(dblk, list);
        if (ok) {
          ndelete++;
        }
      }
      if (eof) {
        cwbsock.close();
        cwbsock = null;
      } else {
        if (dbg) {
          Util.prt("EOR available=" + cwbsock.getInputStream().available());
        }
      }
    } catch (IOException e) {
      Util.prt("IOException e=" + e);
      e.printStackTrace();
      throw e;
    }
    if (dbg) {
      Util.prt("delete list size=" + ndelete + " list.size()=" + (list == null ? "null" : list.size()));
    }
    return ndelete;
  }

  /**
   * Internally insert a block into a the list for a given channel. The blocks are inserted in time
   * order. Duplicates are discarded.
   */
  private boolean insert(DeleteBlockInfo dbi, ArrayList<DeleteBlockInfo> list) {
    for (int i = 0; i < list.size(); i++) {
      int j = dbi.compareTo(list.get(i));
      if (j == 0) {
        return false;    // Its a duplicate, ignore it
      }
      if (j < 0) {
        list.add(i, dbi);
        return true;
      }
    }
    list.add(dbi);
    return true;
  }

  /**
   * Read from input until the desired number of bytes appear or and true EOF or and "<EOR>" is
   * received
   *
   * @param in The input stream
   * @param b Array for the data
   * @param off initial offset in b to put data
   * @param l Number of bytes needed
   * @return true if EOF, if data is obtained or <EOR> return false
   * @throws IOException
   */
  private boolean read(InputStream in, byte[] b, int off, int l)
          throws IOException {
    int len;
    while ((len = Util.socketRead(in, b, off, l)) > 0) {
      //if(dbg) Util.prt(" read len="+len+" off="+off);
      if (b[0] == '<' && b[1] == 'E' && b[2] == 'R' && b[3] == 'R' && b[4] == '>') {
        return false;
      }
      if (b[0] == '<' && b[1] == 'E' && b[2] == 'O' && b[3] == 'R' && b[4] == '>') {
        return false;
      }
      off += len;
      l -= len;
      if (l == 0) {
        return false;
      }
    }
    return true;
  }

  /**
   * this is the call back from the RawToMiniSeed class when a block of MiniSEED has been created.
   * The replace() method registers with RawToMiniSeed to get this call back
   *
   * @param buf The array of bytes with one miniSeed block
   * @param size THe size of the miniSeed block (normally 512)
   */
  @Override
  public void putbuf(byte[] buf, int size) {
    try {
      MiniSeed ms = new MiniSeed(buf, 0, size);
      msblks.add(ms);
    } catch (IllegalSeednameException e) {
      Util.prt("IllegalSeedname - how does this happen!" + e);
    }
  }

  /**
   * The interface for RawToMiniSeed callbacks requires a close. This does nothing.
   *
   */
  @Override
  public void close() {

  }

  /**
   * Replace the given channel with the given data. There must be at least two MiniSeed blocks on
   * the server that are to be deleted. A replace within a single block is not supported.
   *
   * @param net 2 character network code
   * @param station up to 5 character station code
   * @param chan 3 character channel code
   * @param loc two character location code (will default to blanks)
   * @param y2kstart The start time of the time series in data (that is the epochal year 2k time for
   * data[0]
   * @param data The data as integers equally spaced in time by rate. The NO_DATA value can be in
   * any data position to represent gaps
   * @param rate The data digitizing rate in Hertz
   * @param nsamp the number of samples in data[] containing data to be inserted.
   * @return The number of data blocks replace with this new data (requires delete() to populate the
   * deleted block list)
   * @throws IOException If a socket connection problem occurs.
   */
  public int replace(String net, String station, String chan, String loc, double y2kstart,
          int[] data, double rate, int nsamp) throws IOException {
    if (msblks.size() > 0) {
      msblks.clear();
    }
    String seedname = makeSeedName(net, station, chan, loc);
    GregorianCalendar start = new GregorianCalendar();            // Make a start time as a more usable GC
    start.setTimeInMillis((long) (y2kstart * 1000. + year2000MS));    // time of first sample in data
    if (insertSock == null) {
      insertSock = new Socket(cwbhost, insertport); // Make connection to a MiniSeedServer port
    }
    RawToMiniSeed.setStaticOutputHandler(this);       // register with RawToMiniSeed to get block callbacks
    long startms = start.getTimeInMillis();           // save the start time of the new sample
    int i = 0;         // This will move through data[]
    int j = 0;          // this will count good samples moved to d[] from data[]
    seedname = seedname.replaceAll("-", " ");
    StringBuilder seednameSB = new StringBuilder(12); // Hack: not ready to support seedname as StringBuilder
    seednameSB.append(seedname);
    Util.rightPad(seednameSB, 12);
    while (i < nsamp) {  // For all of the data
      if (data[i] != NO_DATA) {
        d[j++] = data[i];  // This data is o.k., move it
      }      // If d[] is full, or this sample is a NO_DATA, or this is the very last sample
      if (j >= d.length || data[i] == NO_DATA || i == nsamp - 1) {
        if (j > 0) {   // If there is anything in d[] compress it to miniSeed
          start.setTimeInMillis(startms + (long) ((i - j + (data[i] != NO_DATA ? 1 : 0)) / rate * 1000. + 0.5));    // i has not been incremented yet
          RawToMiniSeed.addTimeseries(d, j, seednameSB,
                  start.get(Calendar.YEAR), start.get(Calendar.DAY_OF_YEAR),
                  ((int) (start.getTimeInMillis() % 86400000) / 1000), ((int) (start.getTimeInMillis() % 1000) * 1000), rate,
                  0, 0, 0, 0, null);
          j = 0;        // no more samples in d
          if (j < d.length || data[i] == NO_DATA) {
            RawToMiniSeed.forceout(seednameSB);// This is a gap, force out short miniseed block
          }
        }
      }
      i++;      // to next sample in data[]
    }
    RawToMiniSeed.forceout(seednameSB);     // add any remaining blocks to the end
    if (dbg) {
      for (int ii = 0; ii < msblks.size(); ii++) {
        Util.prt("ms[" + ii + "] " + msblks.get(ii));// List out the blocks
      }
    }
    // The list of miniseed blocks is in msblks in sorted order, now process them into the CWB using the insert port
    ArrayList<DeleteBlockInfo> dels = channels.get(seedname.replaceAll("-", " "));
    if (dbg) {
      Util.prt("in replace() get list for " + seedname.replaceAll("-", " ") + "| returned " + (dels == null ? "null" : dels.size()));
    }
    if (dels != null && dbg) {
      for (i = 0; i < dels.size(); i++) {
        Util.prt("del[" + i + "] " + dels.get(i));
      }
    }
    int nreplace = 0;                       // counter for replaced blocks
    // for each block, looks for a confomrable block on the delete list and set dblk if one is found
    for (i = 0; i < msblks.size(); i++) {
      DeleteBlockInfo dblk = null;
      if (dels != null) {
        short time = (short) ((msblks.get(i).getTimeInMillis() % 86400000L) / 3000);
        short end = (short) ((msblks.get(i).getNextExpectedTimeInMillis() % 86400000L) / 3000 + 1);
        for (j = 0; j < dels.size(); j++) {
          if (dels.get(j).getEarliest() <= time && dels.get(j).getLatest() >= end) {// this is a perfect block, use it
            dblk = dels.get(j);
            Util.prt("Choose del[" + j + "] " + dblk + " form ms=" + msblks.get(i));
            dels.remove(j);
            nreplace++;
            break;
          }
        }
      }
      // If we have a dblk (deleted block to use to replace) write out the special command for to MiniSeedServer
      if (dblk != null) {      // Send block to deleted block
        insertSock.getOutputStream().write("CMD REPLACE\n".getBytes());
        insertSock.getOutputStream().write(dblk.getBytes());
        insertSock.getOutputStream().write(msblks.get(i).getBuf(), 0, 512);
      } else {                  // Send miniseed block to MiniSeed server usual way, no replace block available
        insertSock.getOutputStream().write(msblks.get(i).getBuf(), 0, 512);
      }
    }

    return nreplace;

  }

  /**
   * From the parts of a Seedname make the 12 character NNSSSSSCCCLL used by the Edge/CWB
   *
   * @param n
   * @param s
   * @param c
   * @param l
   * @return
   */
  private String makeSeedName(String n, String s, String c, String l) {
    return (n + "  ").substring(0, 2) + (s + "     ").substring(0, 5) + (c + "   ").substring(0, 3) + (l + "  ").substring(0, 2);
  }

  /**
   * Calcualte a Gregorian/1970 epoch in millis from a Y2K double
   *
   * @param t The Y2k double
   * @return The millis since 1970
   */
  public static long y2kToEpoch(double t) {
    return (long) (t * 1000. + year2000MS);
  }

  /**
   * return a y2k epoch as a string
   *
   * @param t the y2k epoch double
   * @return A time string
   */
  public static String y2kToString(double t) {
    return Util.ascdatetime2(y2kToEpoch(t)).toString();
  }

  /**
   * Testing routine for this class.
   *
   * @param args
   */
  public static void main(String[] args) {
    GregorianCalendar start = new GregorianCalendar();
    GregorianCalendar end = new GregorianCalendar();
    boolean delete = false;
    boolean replace = false;
    double duration = 0;
    String cwbip = "localhost";
    int cwbport = 2061;
    int msServerPort = 7974;
    String type = "";
    String seedname = "";
    for (int i = 0; i < args.length; i++) {
      switch (args[i]) {
        case "-delete":
          delete = true;
          break;
        case "-b":
          start.setTimeInMillis(Util.stringToDate2(args[i + 1]).getTime());
          i++;
          break;
        case "-d":
          double mult = 1;
          if (args[i + 1].indexOf("d") > 0 || args[i + 1].indexOf("D") > 0) {
            mult = 86400.;
            args[i + 1] = args[i + 1].replaceAll("d", "").replaceAll("D", "");
          }
          duration = Double.parseDouble(args[i + 1]) * mult;
          i++;
          break;
        case "-s":
          seedname = args[i + 1];
          i++;
          break;
        case "-replace":
          replace = true;
          type = args[i + 1];
          i++;
          break;
        case "-cwbport":
          cwbport = Integer.parseInt(args[i + 1]);
          i++;
          break;
        case "-cwbip":
          cwbip = args[i + 1];
          i++;
          break;
        case "-msport":
          msServerPort = Integer.parseInt(args[i + 1]);
          i++;
          break;
        case "-help":
          Util.prt("For deleting/forgetting an interval :"
                  + "-delete \n"
                  + "-b yyyy-mm/dd-hh:mm:ss\n"
                  + "-d duration in seconds or days with an appended d\n"
                  + "-s NNSSSSSCCCLL \n"
                  + "-cwbport port  (def=2061 which is the normal query server port)\n"
                  + "-cwbip   ip.adr (def = localhost)\n"
                  + "-msport  port (For an MiniSEED server to use for replacing data),  Needed but not used for deletes.");
          break;
        default:
          break;
      }
    }
    try {
      Util.prt("seed=" + seedname + " cwbip=" + cwbip + "/" + cwbport + "/" + msServerPort + " del=" + delete + " rep=" + replace + " start=" + Util.ascdatetime(start) + " dur=" + duration);
      CWBEditor cwb = new CWBEditor(cwbip, cwbport, msServerPort);
      cwb.setDebug(true);
      double y2kstart = (start.getTimeInMillis() - year2000MS) / 1000.;
      double y2kend = (start.getTimeInMillis() + duration * 1000 - year2000MS) / 1000.;
      //y2kstart=4.266432e8;
      //y2kend=  4.267296e8;
      start.setTimeInMillis((long) (y2kstart * 1000. + year2000MS));
      Util.prt("Start=" + Util.ascdate(start.getTimeInMillis()) + " " + Util.asctime2(start.getTimeInMillis()));
      //int ndel1 = cwb.delete("NT","BOU","MVE","R9", y2kstart,y2kend);
      //Util.prt("ndel="+ndel1);
      //int [] d = new int[1440];
      //for(int i=0; i<1440; i++) d[i]=i;
      //int nrep1 = cwb.replace("NT", "BOU", "MVE", "R0", y2kstart, d, 1./60., 1440);
      //Util.prta("replace="+nrep1);
      seedname = (seedname + "            ").substring(0, 12).replaceAll(" ", "-");
      String net = seedname.substring(0, 2).replaceAll("-", " ");
      String station = seedname.substring(2, 7).replaceAll("-", " ");
      String chan = seedname.substring(7, 10).replaceAll("-", " ");
      String loc = seedname.substring(10, 12).replaceAll("-", " ");
      if (delete) {
        int ndel = cwb.delete(net, station, chan, loc, y2kstart, y2kend);
        if (ndel < 0) {
          Util.prt("***** delete inside of a single block. Nothing is deleted!");
        }
        Util.prt("#del=" + ndel);
        cwb.listDeletedBlocks();
      } else if (replace) {
        String line = "-s " + seedname + " -b " + Util.stringBuilderReplaceAll(Util.ascdatetime(start), " ", "-") + " -d " + duration + " -t null -h localhost";
        Util.prt("Line=" + line);
        ArrayList<ArrayList<MiniSeed>> blks = EdgeQueryClient.query(line);
        ZeroFilledSpan span = new ZeroFilledSpan(blks.get(0), start, duration, NO_DATA);
        for (int i = 0; i < span.getNsamp(); i++) {
          if (span.getData()[i] != NO_DATA) {
            span.getData()[i] = span.getData()[i] + 1000;
          }
        }
        for (int i = 1200; i < 1300; i++) {
          span.getData()[i] = NO_DATA; // clear some data in the middle
        }
        for (int i = 0; i < 30; i++) {
          span.getData()[i] = NO_DATA;      // Clear some data at the beginning
        }
        if (span.getNsamp() > 3600) {
          for (int i = 3570; i < 3600; i++) {
            span.getData()[i] = NO_DATA; // Clear some data at the end
          }
        }
        int ndel = cwb.delete(net, station, chan, loc, y2kstart, y2kend);
        if (ndel < 0) {
          Util.prt(" *** Delete was within a single block - it cannot be deleted");
        }
        Util.prt("Deleted " + ndel + " blocks");
        int nrep = cwb.replace(net, station, chan, loc, y2kstart, span.getData(), span.getRate(), span.getNsamp());
        Util.prt("#replace=" + nrep);
      }
    } catch (IOException e) {
      Util.prt("IOError=" + e);
      e.printStackTrace();
    }
    System.exit(0);
  }
}
