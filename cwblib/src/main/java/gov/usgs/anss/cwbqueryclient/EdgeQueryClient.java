/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.cwbqueryclient;

import gov.usgs.anss.seed.MiniSeedPool;
import gov.usgs.alarm.SendEvent;
import gov.usgs.anss.edge.IllegalSeednameException;
import gov.usgs.anss.edge.IndexBlock;
import gov.usgs.anss.edge.IndexFile;
import gov.usgs.anss.edge.RawDisk;
import gov.usgs.anss.seed.MiniSeed;
import gov.usgs.anss.seed.Steim2Object;
import gov.usgs.anss.seed.SteimException;
import gov.usgs.anss.edgethread.MemoryChecker;
import gov.usgs.anss.util.SeedUtil;
import gov.usgs.anss.util.Util;
import gov.usgs.anss.utility.FloatsToMS;
import gov.usgs.anss.utility.HVOScan;
import gov.usgs.anss.utility.SacToMS;
import gov.usgs.anss.utility.QuakeCatcherSacDump;
import java.io.*;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.*;

/**
 * This class is the main class for CWBQuery which allows the user to make queries against all files
 * on a CWB or Edge computer. The program has two modes : command line, and command file.
 *
 * In command line mode the user can specify all of the options and get data from a single seedname
 * mask.
 *
 * In command file mode some of the command line args come from the command line while the seedname
 * mask, start time and duration come from the command file
 *
 * The command line arguments are (many more arguments are visible running this without any
 * arguments) :
 * <pre>
 * -s seedmask  Set the seedmask as a regular expression (.=anychar, [] set of matches).
 *          The 12 character seedname is NNSSSSSCCCLL N=network, S=station code,
 *          C= channel code L=location code.
 *          The regular expression can be useful in that IMPD03.[BS]H... would return
 *          all components with network IM, the first 4 chars of station code are PD03,
 *          for all BH or SH components and any location code.
 * -b yyyy/mm/dd hh:mm:ss The time to start the query.
 * -d secs  The number of seconds of data (the duration).
 *
 * -t type Legal types are "sac", "ms", "msz", dcc512, and "dcc" where
 *         sac = sac binary format. Data will be zero filled and start at next sample
 *              at or after the -b time with exactly the duration
 *         ms = mini-seed raw format.  The mini-seed blocks are returned in sorted order
 *              but they might overlap or be duplicated.
 *         msz = mini-seed but zero-filled and recompressed.  No blk 1000, 1001 are
 *              preserved.  Data will start at sample follow -b time and there will be
 *              a full duration of the data.  If -msb is specified, use that block length.
 *              Subswitches allow generation of fetchlist on gap detection.
 *        dcc = blocks processed to 4096 to best effort Mini-seed eliminating overlaps, etc.
 *        dcc512 = blocks processed to 512 best effort Mini-seed eliminating overlaps, etc.
 *        null  Return the blocks - this is used if a java program wants to use this class
 * -f filename Use the file command mode.  The list of -s -b -d are in the file one per line
 * -h host    The host of the server computer as a name or dotted IP address
 * -p port    The port on which the service is running
 * -msb  blocksize Set the blocksize for msz output
 * -dbg Turn on the debug flag
 * -mb   nnnnn Set maxBlocks to process (def = 500000)
 * -perf      Output information on milliseconds in various stages
 * -perfdbg   Output debug statements at various stages.
 * </pre> Note : when using "-t null" the blocks are returned to the sender in an ArrayList of
 * ArrayList MiniSeed where there is one ArrayList of the outer level containing an ArrayList for
 * MiniSeed for each channel returned. The user can call Collections.sort(ArrayList of MiniSEED); to
 * put the blocks in order. The data has had duplicates removed. If the "-uf" switch is used, the
 * user must free the blocks from the MiniSeedPool by calling EdgeQueryClient.freeQueryBlocks().
 * <p>
 * Command lines should be of the form :
 * <p>
 * -h HOST -b yyyy/mm/yy-hh:mm:ss -d secs -s NNSSSSSCCCLL -uf -t null
 * <br>
 *
 * Here is a snippet of example code :
 * <PRE>
 *   ArrayList\<ArrayList\<MiniSeed\>\> mss =
 *           EdgeQueryClient.query("-h 137.227.224.97 -b 2015/01/26-12:01:23 -d 300 -s CIISA--BH. -uf -t null");
 *   for(int i=0; i;mss.size(); i++) {		// process each channel
 *     Collections.sort(mss.get(i));		  // Put the data in order
 *     for(int j=0; j;mss.get(i).size(); j++) {
 *       System.out.println("i="+i+" j="+j+" "+mss.get(i).get(j).toString());
 *       byte [] msbuf = mss.get(i).get(j).getBuf();
 *       // Write the 512 bytes of data from msbuf to somewhere
 *     }
 *   }
 *   EdgeQueryClient.freeQueryBlocks(mss, "SomeTag", null);		// Free the blocks from the internal MiniSeedPool
 * </PRE>
 *
 * @author davidketchum
 */
public class EdgeQueryClient {

  static MiniSeedPool miniSeedPool = new MiniSeedPool();
  public static boolean dbgMiniSeedPool;

  public static void setDebugMiniSeedPool(boolean t) {
    dbgMiniSeedPool = true;
  }

  public static MiniSeedPool getMiniSeedPool() {
    return miniSeedPool;
  }

  public static String makeFilename(String mask, String seed, MiniSeed ms, boolean deriveLH) {
    StringBuilder sb = new StringBuilder(100);
    String seedname = seed;
    seedname = seedname.replaceAll(" ", "_");
    int[] ymd = SeedUtil.fromJulian(ms.getJulian());
    String name = mask;
    if (deriveLH) {
      seedname = seedname.substring(0, 7) + seedname.substring(7, 9).replaceAll("HH", "IH").replaceAll("BH", "CH") + seedname.substring(9);
    }
    name = name.replaceAll("%N", seedname.substring(0, 12));
    name = name.replaceAll("%n", seedname.substring(0, 2));
    name = name.replaceAll("%s", seedname.substring(2, 7));
    name = name.replaceAll("%_s", seedname.substring(2, 7).replaceAll("_", "").trim());
    name = name.replaceAll("%c", seedname.substring(7, 10));
    name = name.replaceAll("%l", seedname.substring(10, 12));
    name = name.replaceAll("%y", "" + ms.getYear());
    name = name.replaceAll("%Y", ("" + ms.getYear()).substring(2, 4));
    name = name.replaceAll("%j", Util.df4(ms.getDay()).substring(1, 4));
    name = name.replaceAll("%J", "" + ms.getJulian());
    name = name.replaceAll("%d", Util.df2(ymd[2]));
    name = name.replaceAll("%D", Util.df2(ymd[2]));
    name = name.replaceAll("%M", Util.df2(ymd[1]));
    name = name.replaceAll("%h", Util.df2(ms.getHour()));
    name = name.replaceAll("%m", Util.df2(ms.getMinute()));
    name = name.replaceAll("%S", Util.df2(ms.getSeconds()));
    name = name.replaceAll("%h", Util.df2(ms.getHour()));
    if (name.contains("%z")) {
      name = name.replaceAll("%z", "");
      name = name.replaceAll("_", "");
    }
    if (name.contains("%_/")) {
      name = name.replaceAll("%_/", "");
      name = name.replaceAll("_/", Util.fs);
      name = name.replaceAll("_/", Util.fs);
      name = name.replaceAll("_/", Util.fs);
      name = name.replaceAll("_/", Util.fs);
      name = name.replaceAll("/_", Util.fs);
      name = name.replaceAll("/_", Util.fs);
      name = name.replaceAll("/_", Util.fs);
      name = name.replaceAll("/_", Util.fs);

    }
    if (name.contains("%_") || name.contains("%&")) {
      name = name.replaceAll("_", "&").replaceAll("%_", "").replaceAll("%&", "").replaceAll("%&", "");
    }
    if (name.contains("%x")) {
      name = name.replaceAll("%x", "");
    }
    if (name.contains("%a")) {
      name = name.replaceAll("%a", "").toLowerCase();
    }

    return name;

  }

  public static String makeFilename(String mask, String seed, java.util.Date beg, boolean deriveLH) {
    GregorianCalendar g = new GregorianCalendar();
    g.setTimeInMillis(beg.getTime());

    StringBuilder sb = new StringBuilder(100);
    String seedname = seed;
    seedname = seedname.replaceAll(" ", "_");
    String name = mask;
    if (deriveLH) {
      seedname = seedname.substring(0, 7) + seedname.substring(7, 9).replaceAll("HH", "IH").replaceAll("BH", "CH") + seedname.substring(9);
    }
    name = name.replaceAll("%N", seedname.substring(0, 12));
    name = name.replaceAll("%n", seedname.substring(0, 2));
    name = name.replaceAll("%s", seedname.substring(2, 7));
    name = name.replaceAll("%c", seedname.substring(7, 10));
    name = name.replaceAll("%l", seedname.substring(10, 12));
    name = name.replaceAll("%y", "" + g.get(Calendar.YEAR));
    name = name.replaceAll("%Y", ("" + g.get(Calendar.YEAR)).substring(2, 4));
    name = name.replaceAll("%j", Util.df4(g.get(Calendar.DAY_OF_YEAR)).substring(1, 4));

    name = name.replaceAll("%J", "" + SeedUtil.toJulian(g));
    name = name.replaceAll("%d", Util.df2(g.get(Calendar.DAY_OF_MONTH)));
    name = name.replaceAll("%D", Util.df2(g.get(Calendar.DAY_OF_MONTH)));
    name = name.replaceAll("%M", Util.df2(g.get(Calendar.MONTH) + 1));
    name = name.replaceAll("%h", Util.df2(g.get(Calendar.HOUR_OF_DAY)));
    name = name.replaceAll("%m", Util.df2(g.get(Calendar.MINUTE)));
    name = name.replaceAll("%S", Util.df2(g.get(Calendar.SECOND)));
    name = name.replaceAll("%h", Util.df2(g.get(Calendar.HOUR_OF_DAY)));
    if (name.contains("%z")) {
      name = name.replaceAll("%z", "");
      name = name.replaceAll("_", "");
    }
    if (name.contains("%x")) {
      name = name.replaceAll("%x", "");
    }
    if (name.contains("%a")) {
      name = name.replaceAll("%a", "").toLowerCase();
    }
    return name;

  }

  /**
   * Creates a new instance of EdgeQueryClient
   */
  public EdgeQueryClient() {

  }

  static public StringBuilder getVersion(String host, int port) throws IOException {
    StringBuilder ans = new StringBuilder(20);
    Socket s = new Socket(host, port);
    s.getOutputStream().write("VERSION: GS\n".getBytes());
    for (;;) {

      int b = s.getInputStream().read();
      if (b == -1) {
        break;
      }
      if (b == '\n') {
        break;
      }
      ans.append((char) b);
    }
    return ans;
  }

  /**
   * do a query from a command string, break it into a command args list and call query
   *
   * @param line The command line string
   * @return ArrayList of ArrayList of MiniSeed with each channels data on each array list
   */
  static public ArrayList<ArrayList<MiniSeed>> query(String line) {
    String[] arg = line.split(" ");
    //Util.prt("line="+line);
    for (int i = 0; i < arg.length; i++) {
      arg[i] = "";
    }
    int narg = 0;
    boolean inQuote = false;
    int beg = 0;
    int end = 0;
    while (end < line.length()) {
      if (inQuote) {
        if (line.charAt(end) == '"' || line.charAt(end) == '\'') {
          arg[narg++] = line.substring(beg, end).trim();
          if (arg[narg - 1].equals("")) {
            narg--;
          }
          inQuote = false;
          beg = end + 1;
        }
      } else {
        if (line.charAt(end) == '"' || line.charAt(end) == '\'') {
          inQuote = true;
          beg = end + 1;
        } else if (line.charAt(end) == ' ') {
          arg[narg++] = line.substring(beg, end).trim();
          if (arg[narg - 1].equals("")) {
            narg--;
          }
          beg = end + 1;
        }
      }
      end++;
    }
    if (inQuote) {
      Util.prt("Query argument list has open quotes!");
      return new ArrayList<>(1);
    }
    arg[narg++] = line.substring(beg, end).trim();
    int n = 0;
    for (int i = 0; i < narg; i++) {
      if (!arg[i].equals("")) {
        n++;
      }
    }
    String[] args = new String[n];
    n = 0;
    for (int i = 0; i < narg; i++) {
      if (!arg[i].equals("")) {
        if (line.contains("dbg")) {
          Util.prt(n + "=" + arg[i] + "|");
        }
        args[n++] = arg[i];
      }
    }
    return query(args);
  }

  /**
   * do a query. The command line arguments are passed in as they are for the query tool a files is
   * created unless -t null is specified. In that case the return is an ArrayList containing
   * ArrayList of MiniSeed for each channel returned
   *
   * @param args The String array with args per the documentation
   * @return The ArrayList with ArrayLists of miniseed one for each channel returned.
   */
  static public ArrayList<ArrayList<MiniSeed>> query(String[] args) {
    String line = "";
    String host = "137.227.224.97";
    Util.loadProperties("query.prop");
    Util.addDefaultProperty("cwbip", "137.227.224.97");
    Util.addDefaultProperty("queryport", "2061");
    Util.addDefaultProperty("metadataserver", "137.227.224.97");
    MiniSeed.setStrict(false);
    byte[] ipaddr;
    InetAddress addr;
    InetAddress addrc = null;
    InetAddress[] addrall = null;
    String cwb = "Public (cwb-pub)";
    long msSetup = 0;
    long msConnect = 0;
    long msTransfer = 0;
    long msOutput = 0;
    long msCommand = 0;
    long msSortDups = 0;
    long startTime = System.currentTimeMillis();
    long startPhase = startTime;
    try {
      addr = InetAddress.getLocalHost();
      if (addr != null) {
        addrc = InetAddress.getByName(addr.getCanonicalHostName());
      } else {
        Util.prt("WARNING : getLocalHost() returned null -- This should never happen!  You must use a query.prop!");
      }
      if (addr != null) {
        addrall = InetAddress.getAllByName(addr.getCanonicalHostName());
      }
    } catch (UnknownHostException e) {
    }
    if (Util.getProperty("cwbip") == null || Util.getProperty("cwbip").length() == 0) {
      Util.setProperty("cwbip", "localhost");
    } else {
      host = Util.getProperty("cwbip");
    }
    //Util.prt("Using host "+host);
    byte[] b = new byte[4096];
    Outputer out = null;
    int maxBlocks = 500000;

    GregorianCalendar jan_01_2007 = new GregorianCalendar(2007, 0, 1);
    int port = 2061;
    if (Util.getProperty("queryport").length() > 1) {
      port = Integer.parseInt(Util.getProperty("queryport"));
    }
    if (args.length == 0) {
      Util.prt("Version 1.30  11/24/2015");
      Util.prt("CWBQuery [-f filename][ -b date_time -s NSCL -d duration] -t [ms | msz | sac(def)| sacseg] [-o filemask]");
      Util.prt("   e.g.  java -jar /home/jsmith/bin/CWBQuery.jar -s \"IIAAK  LHZ00\" -b \"2007/05/06 21:11:52\" -d 3600 -t sac -o %N_%y_%j");
      Util.prt("   OR    java -jar /home/jsmith/bin/CWBQuery.jar -f my_batch_file");
      Util.prt("Directory/Holdings:");
      Util.prt("   -ls  List the data files available for queries (not generally useful to users)");
      Util.prt("   -lsc List every channel its days of availability from begin time through duration (default last 15 days)");
      Util.prt("        Use the -b and -d options to set limits on time interval of the channel list ");
      Util.prt("        This option can be cpu intensive if you ask for a long interval, so use only as needed.");
      Util.prt("   -list Add this to a normal query (-s, -b, -d) to get a listing of the runs and gaps in that interval");
      Util.prt("Query Modes of operation :");
      Util.prt("  Line mode (command line mode) do not use -f and include -s NSCL -b yyyy/mm/dd hh:mm:ss -d secs on line");
      Util.prt("  File mode or batch mode:");
      Util.prt("    Create a file with one line per query with lines like [QUERY SWITCHES][OUTPUT SWITCHES]:");
      Util.prt("      example line :   '-s NSCL -b yyyy/mm/dd hh:mm:ss -d duration -t sac -o %N_%y_%j'");
      Util.prt("    Then run EdgeQuery with  '-f filename' filename with list of SNCL start times and durations");
      Util.prt("Server selection (please use9 query.prop for this and not these switches) :");
      Util.prt("   -h host IP or name of server default=" + host + " " + cwb + " which is set differently inside and outside the NEIC subnets.");
      Util.prt("   #-hp    The public server is cwbpub.cr.usgs.gov ");
      Util.prt("   #-hi    NEIC use only - internal is gcwb ");
      Util.prt("   #-hr    NEIC user only - The research CWB ");
      Util.prt("   #-hl    Use localhost for server");
      Util.prt("   #-h file Means treat -s argument as a filename and convert it to the desired output type - no query of CWB is done");
      Util.prt("   -p port  on server where server is running default=" + port);
      Util.prt("            This computer reports is IP address as " + Util.getProperty("HostIP"));
      Util.prt("Query Switches (station and time interval limits) :");
      Util.prt("   -s NSCL or REGEXP  (note: on many shells its best to put this argument in double quotes)");
      Util.prt("      NNSSSSSCCCLL to specify an 12 character seed channel name. If < 12 characters, match any seednames starting");
      Util.prt("          with those characters.  Example : '-s IUA' would return all IU stations starting with 'A' (ADK,AFI,ANMO,ANTO) ");
      Util.prt("      OR ");
      Util.prt("      REGEXP :'.' matches any character, [ABC] means one character which is A, or B or C The regexp is right padded with '.'");
      Util.prt("          Example: '-s IUANMO.LH[ZN]..' returns the vertical and north component for LH channels at station(s) starting with 'ANMO'");
      Util.prt("          Notice that '-s IUANMO.LH[ZN]' matches nothing because it does not represent all 12 characters!");
      Util.prt("          '.*' matchs zero or more following characters (include in quotes as * has many meanings!");
      Util.prt("          'AA|BB' means matches pattern AA or BB e.g.  'US.*|IU.*' matches the US and IU networks");
      Util.prt("   -b begin     time yyyy/mm/dd hh:mm:ss (normally enclose in quotes) or yyyy,doy-hh:mm:ss or yyyy/mm/dd-hh:mm:ss");
      Util.prt("   -d nnnn[d]   seconds of duration(default is 300 seconds) end with 'd' to indicate nnnn is in days");
      Util.prt("   -nodups      Eliminate any duplications on input rather than after sort.  Only use this if the duplications are massive! It will be much slower.");
      Util.prt("   -allowdups    Allow duplicate packets into output.  This is not recommended.  Dups are eliminated by default");
      Util.prt("   -dbgdup      Print out all duplicate packets as they are eliminated");
      Util.prt("   -allowdeleted Allow blocks in query which were deleted (DEBUG USE)");
      Util.prt("   -allowric    Include blocks that fail the Reverse Integration Constant test - the blocks are likely bad.");
      Util.prt("   -mb or -maxblocks nnnnn set the maximum number of blocks to process in one section (def=" + maxBlocks);
      Util.prt("   -sacpz nm|um Request sac style response files in either nanometers (nm) or micrometers(um)");
      Util.prt("   -nice        Set this option if speed of query is not important - query will run slightly slower");
      Util.prt("   -derive Hz   Convert the series from its input rate the 'Hz' via lowpass filter and decimate (if same band code at 5 to location code)");
      Util.prt("   -deriveLH    Derive LH (1 sps) from the BH or HH input channel (if possible).  Output files and channels are change BH->CH and HH->IH");
      Util.prt("   -delazc [mindeg:]maxdeg:lat:long [-b dateString] - limit returns to channels within 'deg'ress of 'lat'itude and 'long'itude");
      Util.prt("                This works with data requests and with -lsc requests for channel lists");
      Util.prt("Output Controls : ");
      Util.prt("   -q           Run in quiet mode (No progress or file status reporting)");
      Util.prt("   -t [ms | msz | sac | sacseg | dcc|dcc512|wf|wf1|wfms[:name]|wfms1[:name] output type.  ");
      Util.prt("                ms is raw blocks with gaps/overlaps (ext='.ms')");
      Util.prt("                msz = is data output as continuous mini-seed with filling use -fill to set other fill values (ext='.msz')");
      Util.prt("                  can also be output as gappy miniseed with -msgaps NOTE: msz rounds times to nearest millsecond");
      Util.prt("                sac = is Seismic Analysis Code format (see -fill for info on nodata code) (ext='.sac')");
      Util.prt("                sacseg = is SAC format with one file per segment found in interval YYYYMMDD_HHMMSS will be appended to -o mask [-cosmos]");
      Util.prt("                dcc = best effort reconciliation to 4096 byte mini-seed form.  Overlaps are eliminated. (ext='.msd'");
      Util.prt("                dcc512 = best effort reconciliation to 512 byte mini-seed form.  Overlaps are eliminated. (ext='.msd'");
      Util.prt("                null = do not create data file, return blocks to caller (for use from a user program)");
      Util.prt("                wf or wf1 creates wfdisc and .w files with s4 data - always use -o with -t wf1!");
      Util.prt("                wf1 creates only one .wfdisc and one concatenated file of s4s");
      Util.prt("                wfms:name creates wfdisc and separate miniSEED (sd) data files (based on -o) and using name.wfdisc");
      Util.prt("                wfms1:name creates wfdisc and a single miniSEED (sd) data files using name.wfdisc and name.wfms");
      Util.prt("   -o mask      Put the output in the given filename described by the mask/tokens (Default : %N");
      Util.prt("          Tokens: (Any non-tokens will be literally in the file name)");
      Util.prt("                %N the whole seedname NNSSSSSCCCLL");
      Util.prt("                %n the two letter SEED network          %s the 5 character SEED station code");
      Util.prt("                %c the 3 character SEED channel         %l the two character location");
      Util.prt("                %y Year as 4 digits                     %Y 2 character Year");
      Util.prt("                %j Day of year (1-366)                  %J Julian day (since 1572)");
      Util.prt("                %M 2 digit month                        %D 2 digit day of month");
      Util.prt("                %h 2 digit hour                         %m 2 digit minute");
      Util.prt("                %S 2 digit second                       %z zap all underscores from name");
      Util.prt("                %_s the trimmed Station code            %_/ remove all '/_' and '_/' from the name");
      Util.prt("                %a Convert full file name to lower case %& Convert all '&' to '_' useful to force some _");
      Util.prt("                %x for -t sacseg do not add time/date to filename - User is responsible to make filenames unique!");
      Util.prt("  WF options :");
      Util.prt("    -fap        output a frequency, amplitude, and phase file if metadata has an PNZ response");
      Util.prt("  DCC[512] debug options:");
      Util.prt("    -chk        Do a check of the input and output buffers(DEBUGGING)");
      Util.prt("    -dccdbg     Turn on debugging output(DEBUGGING)");
      Util.prt("  SAC  and SACSEG options :");
      Util.prt("    -fill nnnnn use nnnnnn as the fill value instead of -12345");
      Util.prt("    -nogaps     if present, any missing data in the interval except at the end results in no output file. A -sactrim is also done");
      Util.prt("    -sactrim T  Trim length of returned time series so that no 'nodata' points are at the end of the buffer");
      Util.prt("    -sacpz nm|um Request sac response files in either nanometers (nm) or micrometers(um)");
      Util.prt("    -nometa     Do not try to look up meta-data for orientation or coordinates or response");
      Util.prt("    -cosmos     Used only with -t sacseg create a COSMOS file from the segmented output");
      Util.prt("    -ascii      Dump an comma delimited set of data values into a flat text file (with fill values)");
      Util.prt("  MSZ options :  NOTE : msz data has its times rounded to the nearest millisecond");
      Util.prt("    -fill nnnnnn use nnnnnn as the fill value instead of -12345");
      Util.prt("    -gaps       if present, only displays a list of any gaps in the data - no output file is created");
      Util.prt("    -gapfile    if presentwith -gaps,  gaps will be written to files like NN.SSSS.LL.CHN.gap form yyyy/mm/dd hh:mm:ss.mmm|duration");
      Util.prt("    -makefetch TY  if present, make fetch list entries of type TY from this run (if TY is CW, list CWBQuery lines instead)\n");
      Util.prt("    -msb nnnn   Set mini-seed block size to nnnn (512 and 4096 only)");
      Util.prt("    -msgaps     Process the data and have gaps in the output miniseed rather than filled values\n");
      Util.prt("Miscellaneous:");
      Util.prt("    -e          Exclude the non-public stations from a query (not valid outside of NEIC subnets)");
      Util.prt("    -dbg        Turn on debugging output to stdout");
      Util.prt("    -perf -perfdbg Output a line analizing time in each phase.  -perfdbg add lots more output");
      Util.prt("    -hold[:nnn.nnn.nnn.nnn:pppp:type] send holdings from request to indicated server (NEIC use only)");
      Util.prt("    -sac2ms     Convert SAC files to MiniSeed - use just -sac2ms to get help on this feature");
      Util.prt("    -qc2ms      Convert SAC files to MiniSeed which came from a QuakeCatcher - use just -qc2ms to get help on this feature");
      Util.prt("    -float2ms     Convert MiniSeed with floats/doubles to MiniSeed - use just -float2ms to get help on this feature");
      Util.prt("    -fscale nnn  Set the scale factor to multiply the floats/doubles by in converting them to ints with -float2ms");
      Util.prt("    -mdsip  ip.adr  Set the IP address for MetaDataServer requests for this run (def=137.227.224.97)");
      Util.prt("    -strict      MiniSeed class is set to strict so channel names are check for correctness");
      Util.prt("Properties with defaults (Current setting in parenthesis possibly set by query.prop file) :");
      Util.prt("   cwbip=137.227.224.97          ( " + Util.getProperty("cwbip") + " host=" + host + ")");
      Util.prt("   queryport=2061                (" + Util.getProperty("queryport") + ")");
      Util.prt("   metadataserver=137.227.224.97 (" + Util.getProperty("metadataserver") + ")");
      Util.prt("** latest version is at ftp://hazards.cr.usgs.gov/CWBQuery/CWBQuery_unix.tar.gz or CWBQuery.zip");
      return null;
    }
    ArrayList<ArrayList<MiniSeed>> blksAll = null;
    double duration = 300.;
    String seedname = "";
    String begin = "";
    String type = "sac";
    boolean dbg = false;
    boolean lsoption = false;
    boolean lschannels = false;
    java.util.Date beg;
    int julian = 0;
    String filenamein = " ";
    String filename;
    int blocksize = 512;        // only used for msz type
    BufferedReader infile;
    String filemask = "%N";
    boolean reset = false;
    boolean quiet = false;
    boolean gapsonly = false;
    boolean dbgdup = false;
    // Make a pass for the command line args for either mode!
    String exclude = "";
    boolean nosort = false;
    String durationString = "";
    boolean holdingMode = false;
    String holdingIP = "";
    int holdingPort = 0;
    String holdingType = "";
    boolean showIllegals = false;
    boolean chkDups = false;
    boolean sacpz = false;
    SacPZ stasrv = null;
    boolean allowDups = false;
    boolean dropRIC = true;
    String pzunit;
    String stahost = Util.getProperty("metadataserver");
    int timesec = 120;
    boolean deriveLH;
    boolean derive;
    int deriveRate = 40;
    boolean perf = false;
    boolean perfdbg = false;
    boolean userFreeBlocks = false;
    String delazString = "";
    boolean eqdbg = false;
    double overrideRate = -1.;

    int dummy = 0;
    // This loop must validate all arguments on the command line, parsing is really done below
    for (int i = 0; i < args.length; i++) {
      args[i] = args[i].trim();
      if (args[i].equals("-f")) {
        filenamein = args[i + 1];
        i++;
      } else if (args[i].equalsIgnoreCase("-h")) {
        host = args[i + 1];
        i++;
      } else if (args[i].equalsIgnoreCase("-hp")) {
        host = "137.227.224.97";
      } else if (args[i].equalsIgnoreCase("-hr")) {
        host = "cwbrs";
      } else if (args[i].equalsIgnoreCase("-hi")) {
        host = "cwbrs";
      } else if (args[i].equalsIgnoreCase("-hl")) {
        host = "localhost";
      } else if (args[i].equalsIgnoreCase("-mdsip")) {
        Util.setProperty("metadataserver", args[i + 1]);
        stahost = args[i + 1];
        i++;
      } else if (args[i].equalsIgnoreCase("-p")) {
        port = Integer.parseInt(args[i + 1]);
        i++;
      } else if (args[i].equalsIgnoreCase("-dbg")) {
        dbg = true;
        /*MiniSeed.setDebug(true);*/
        IndexFile.setDebug(true);
        IndexBlock.setDebug(true);
        Util.debug(true);
        Util.prta("Debug is on!");
      } else if (args[i].equalsIgnoreCase("-eqdbg")) {
        eqdbg = true;
      } else if (args[i].equalsIgnoreCase("-uf")) {
        userFreeBlocks = true;
      } else if (args[i].equalsIgnoreCase("-perf")) {
        perf = true;
      } else if (args[i].equalsIgnoreCase("-perfdbg")) {
        perfdbg=true;
      } else if (args[i].equalsIgnoreCase("-t")) {
        type = args[i + 1];
        i++;
      } else if (args[i].equalsIgnoreCase("-msb")) {
        blocksize = Integer.parseInt(args[i + 1]);
        i++;
      } else if (args[i].equalsIgnoreCase("-o")) {
        filemask = args[i + 1];
        i++;
      } else if (args[i].equalsIgnoreCase("-e")) {
        exclude = "exclude.txt";
      } else if (args[i].equalsIgnoreCase("-el")) {
        exclude = args[i + 1];
        i++;
      } else if (args[i].equalsIgnoreCase("-ls")) {
        lsoption = true;
      } else if (args[i].equalsIgnoreCase("-lsc")) {
        lschannels = true;
        lsoption = true;
      } else if (args[i].equalsIgnoreCase("-reset")) {
        reset = true;
      } else if (args[i].equals("-mb") || args[i].equals("-maxblocks")) {
        maxBlocks = Integer.parseInt(args[i + 1]);
        i++;
      } else if (args[i].equalsIgnoreCase("-overriderate")) {
        overrideRate = Double.parseDouble(args[i + 1]);
        i++;
      } else if (args[i].equalsIgnoreCase("-b")) {
        begin = args[i + 1];
        i++;
        if ((i + 1) < args.length) {
          if (args[i + 1].indexOf(":") == 2) {
            begin += "-" + args[i + 1];
            i++;
          }
        }
      } else if (args[i].equalsIgnoreCase("-s")) {
        seedname = args[i + 1];
        i++;
      } else if (args[i].equalsIgnoreCase("-d")) {
        durationString = args[i + 1];
        i++;
      } else if (args[i].equalsIgnoreCase("-q")) {
        quiet = true;
      } else if (args[i].equalsIgnoreCase("-to")) {
        timesec = Integer.parseInt(args[i + 1]);
        i++;
      } else if (args[i].equalsIgnoreCase("-derivelh")) {
        dummy = 1;
      } else if (args[i].equalsIgnoreCase("-derive")) {
        i++;
      } else if (args[i].equalsIgnoreCase("-makefetch")) {
        i++;                   // legal for wf and wf1 types
      } else if (args[i].equalsIgnoreCase("-fap")) {
        dummy = 2;                   // legal for wf and wf1 types
      } else if (args[i].equalsIgnoreCase("-allowdeleted")) {
        dummy = 1;                   // legal for wf and wf1 types
      } else if (args[i].equalsIgnoreCase("-nosort")) {
        nosort = true;
      } else if (args[i].equalsIgnoreCase("-nogaps")) {
        dummy = 1;                // legal for sac and zero MS
      } else if (args[i].equalsIgnoreCase("-nodups")) {
        chkDups = true;
      } else if (args[i].equalsIgnoreCase("-allowdups")) {
        allowDups = true;
      } else if (args[i].equalsIgnoreCase("-allowric")) {
        dropRIC = false;
      } else if (args[i].equalsIgnoreCase("-sactrim")) {
        dummy = 1;               // legal for sac and zero MS
      } else if (args[i].equalsIgnoreCase("-ascii")) {
        dummy = 1;
      } else if (args[i].equalsIgnoreCase("-gaps")) {
        gapsonly = true;
        type = "msz";
      } // legal for zero MS
      else if (args[i].equalsIgnoreCase("-list")) {
        type = "msz";
        gapsonly = true;
        durationString = "1d";
        quiet = true;
      } else if (args[i].equalsIgnoreCase("-msgaps")) {
        dummy = 1;                // legal for zero ms
      } else if (args[i].equalsIgnoreCase("-nice")) {
        dummy = 1;                // legal for zero ms
      } else if (args[i].equalsIgnoreCase("-nonice")) {
        dummy = 1;                // legal for zero ms
      } else if (args[i].equalsIgnoreCase("-fn")) {
        i++;                // legal for zero ms
      } else if (args[i].equalsIgnoreCase("-delazc")) {
        delazString = args[i + 1];
        i++;
      } else if (args[i].equalsIgnoreCase("-udphold")) {
        gapsonly = true;  // legal for zero MS
      } else if (args[i].equalsIgnoreCase("-chk")) {
        dummy = 1;                   // valid only for -t dcc
      } else if (args[i].equalsIgnoreCase("-dccdbg")) {
        dummy = 1;                // valid only for -t dcc & -t fdcc512
      } else if (args[i].equalsIgnoreCase("-little")) {
        dummy = 1;
      } else if (args[i].equalsIgnoreCase("-dbgdup")) {
        dbgdup = true;
      } else if (args[i].equalsIgnoreCase("-nometa")) {
        dummy = 1;
      } else if (args[i].equalsIgnoreCase("-fill")) {
        i++;
      } else if (args[i].equalsIgnoreCase("-sacpz")) {
        sacpz = true;
        if (i + 1 > args.length) {
          Util.prt(" ***** -sacpz units must be either um or nm and is required!");
          System.exit(1);
        }

        pzunit = args[i + 1];
        if (stahost == null || stahost.equals("")) {
          stahost = "137.227.224.97";
        }
        if (!args[i + 1].equalsIgnoreCase("nm") && !args[i + 1].equalsIgnoreCase("um")) {
          Util.prt("   ****** -sacpz units must be either um or nm switch values is " + args[i + 1]);
          System.exit(1);
        }
        stasrv = new SacPZ(stahost, pzunit);
        i++;
      } else if (args[i].equalsIgnoreCase("-si")) {
        showIllegals = true;
      } else if (args[i].indexOf("-hold") == 0) {
        holdingMode = true;
        gapsonly = true;
        type = "HOLD";
        Util.prt("Holdings server=" + holdingIP + "/" + holdingPort + " type=" + holdingType);
      } else if (args[i].equalsIgnoreCase("-cosmos") || args[i].equalsIgnoreCase("-gapfile")) {
      } else {
        Util.prt("Unknown CWB Query argument=" + args[i]);
      }

    }

    if (reset) {
      doReset(host, port);
      return null;
    }
    // The ls option does not require any args checking
    if (lsoption) {
      doLSC(host, port, seedname, begin, durationString, delazString, exclude,
              eqdbg, lschannels, showIllegals, null);
      return null;
    }

    // if not -f mode, read in more command line parameters for the run
    if (filenamein.equals(" ")) {
      for (String arg : args) {
        line += arg.replaceAll(" ", "@") + " ";
      }
      infile = new BufferedReader(new StringReader(line));
    } else {
      try {
        infile = new BufferedReader(new FileReader(filenamein));
      } catch (FileNotFoundException e) {
        Util.prt("did not find the input file=" + filenamein);
        return null;
      }
    }

    // the "in" BufferedReader will give us the command lines we need for the other end
    try {
      // for each line of input, read it, reformat it with single quotes, send to server
      int nline = 0;
      int totblks = 0;
      Socket ds = null;
      ReadTimeout timeout = null;
      OutputStream outtcp = null;
      InputStream in;

      while ((line = infile.readLine()) != null) {
        nline++;
        //Util.prt("Start line="+line);
        if (line.length() < 2) {
          continue;
        }
        char[] linechar = line.toCharArray();
        boolean on = false;
        for (int i = 0; i < line.length(); i++) {
          if (linechar[i] == '"') {
            on = !on;
          } else if (linechar[i] == ' ') {
            if (on) {
              linechar[i] = '@';
            }
          }
        }
        if (dbg) {
          Util.prta("EQC: line=" + line);
        }

        line = new String(linechar);
        line = line.replaceAll("\"", " ");
        line = line.replaceAll("  ", " ");
        line = line.replaceAll("  ", " ");
        line = line.replaceAll("  ", " ");

        args = line.split(" ");
        deriveLH = false;
        derive = false;
        for (int i = 0; i < args.length; i++) {
          if (args[i].equals("-b")) {
            begin = args[i + 1].replaceAll("@", " ");
            if (begin.equalsIgnoreCase("recent")) {
              begin = Util.ascdatetime(System.currentTimeMillis() - 1200000).toString().replaceAll(" ", "-");
              args[i + 1] = begin;
              duration = 3600;
              Util.prt("recent=" + begin);
            }
            if ((i + 2) < args.length) {
              if (args[i + 2].indexOf(":") == 2) {
                begin += "-" + args[i + 2];
              }
            }
          } else if (args[i].equals("-s")) {
            seedname = args[i + 1].replaceAll("@", " ");
          } else if (args[i].equals("-d")) {
            if (args[i + 1].endsWith("d") || args[i + 1].endsWith("D")) {
              duration = Double.parseDouble(args[i + 1].substring(0, args[i + 1].length() - 1)) * 86400.;
            } else {
              duration = Double.parseDouble(args[i + 1]);
            }
            if (duration > 366 * 86400 + 1.) {
              Util.prt("The duration cannot exceed 366 days.  Try again duration=" + duration + " sec or " + Util.df22(duration / 86400) + " days!");
              return null;
            }
          } else if (args[i].equals("-t")) {
            type = args[i + 1];
            i++;
          } else if (args[i].equals("-msb")) {
            blocksize = Integer.parseInt(args[i + 1]);
            i++;
          } else if (args[i].equals("-o")) {
            filemask = args[i + 1].replaceAll("@", " ");
            i++;
          } //else if(args[i].equals("-e")) exclude="exclude.txt";
          //else if(args[i].equals("-el")) {exclude = args[i+1].replaceAll("@"," ");i++;}
          else if (args[i].equals("-delazc")) {
            i++;
          } else if (args[i].equals("-derivelh")) {
            deriveLH = true;
          } else if (args[i].equals("-derive")) {
            derive = true;
            deriveRate = Integer.parseInt(args[i + 1]);
            i++;
          } else if (args[i].equals("-q")) {
            quiet = true;
          } else if (args[i].equals("-nosort")) {
            nosort = true;
          } else if (args[i].indexOf("-hold") == 0) {
            args[i] = "-gaps";     // change to tel other end gaps mode
          }
        }
        if (blocksize != 512 && blocksize != 4096) {
          Util.prt("-msb must be 512 or 4096 and is only meaningful for msz type");
          return null;
        }
        if (begin.equals("")) {
          if (!host.equals("file")) {
            Util.prt("You must enter a beginning time @line " + nline);
            return null;
          }
          beg = null;
        } else {
          beg = Util.stringToDate2(begin);
          if (beg.before(Util.stringToDate2("1970/12/31/ 23:59"))) {
            Util.prt("the -b field date did not parse correctly. @line" + nline);
            return null;
          }
        }
        if (seedname.equals("") && !host.equals("file")) {
          Util.prt("-s SCNL is not optional.  Specify a seedname @line" + nline);
          return null;
        }
        if (type.equals("ms") || type.equals("msz") || type.equals("sac")
                || type.equals("dcc") || type.equals("dcc512") || type.equals("HOLD")
                || type.equals("wf") || type.equals("wf1") || type.equals("sacseg") || type.indexOf("wfms") == 0) {
          if (seedname.length() < 12) {
            seedname = (seedname + ".............").substring(0, 12);
          }
          if (type.equals("ms")) {
            out = new MSOutputer(nosort);
          }
          if (type.equals("sac")) {
            out = new SacOutputer();
          }
          if (type.equals("sacseg")) {
            out = new SacSegmentOutputer();
          }
          if (type.equals("msz")) {
            out = new MSZOutputer(blocksize);
          }
          if (type.equals("wf") || type.equals("wf1")) {
            out = new WFDiscOutputer();
          }
          if (type.indexOf("wfms") == 0) {
            String name = "wfms";
            if (type.indexOf(":") > 0) {
              name = type.substring(type.indexOf(":") + 1);
            }
            if (filenamein.equals(" ")) {
              out = new WFDiscMSOutputer(name, type.contains("wfms1"));
            } else if (out == null) {
              out = new WFDiscMSOutputer(name, type.contains("wfms1"));
            }
          }
          if (type.equals("dcc")) {
            out = new DCCOutputer();
          }
          if (type.equals("dcc512")) {
            out = new DCC512Outputer();
          }
          if (type.equals("HOLD")) {
            out = new HoldingOutputer();
          }
        } else if (type.equals("null")) {
          out = null;
          blksAll = new ArrayList<>(20);
        } else {
          Util.prt("Output format not supported.  Choose dcc, dcc512, ms, msz, or sac");
          return null;
        }
        // The length at which our compare for changes depends on the output file mask
        int compareLength = 12;
        if (filemask.contains("%n")) {
          compareLength = 2;
        }
        if (filemask.contains("%s")) {
          compareLength = 7;
        }
        if (filemask.contains("%c")) {
          compareLength = 10;
        }
        if (filemask.contains("%l")) {
          compareLength = 12;
        }
        if (filemask.contains("%N")) {
          compareLength = 12;
        }

        // If doing wfdisc in one file, the user must use -o
        if (type.equals("wf1") && filemask.equals("%N")) {
          Util.prt("     ****** -t wf1 requires a -o with a filemask.  Overriding to wf1.%y%j%h%m%S");
          filemask = "wf1.%y%j%h%m%S";
        }
        msSetup += (System.currentTimeMillis() - startPhase);
        startPhase = System.currentTimeMillis();
        boolean lineDone = false;
        for (int itime = 0; itime < 10; itime++) {
          if (lineDone) {
            break;
          }
          if (itime == 9) {
            Util.prt("This query failed 9 times, something must be seriously wrong!");
            StringBuilder sb = new StringBuilder(1000);
            for (String arg : args) {
              sb.append(arg).append(" ");
            }
            sb.append("\n");
            sb.append("Line of failure=").append(line).append("\n");
            sb.append(Util.ascdate()).append(" ").append(Util.asctime()).append(" node=").
                    append(Util.getNode()).append(" ").append(Util.getAccount()).append("\n");
            Util.prt(sb.toString());
            System.exit(1);
          }
          if (itime > 0) {
            Util.prta("**** request failed.  Try again up to 9 times. itime=" + itime);
          }
          // particularly for the DCC we want this program to not error out if we cannot connect to the server
          // So make sure we can connect and print messages
          if (host.equals("file")) {
            //RawDisk file = new RawDisk(seedname,"rw");
            in = new FileInputStream(seedname);
          } else {
            while (ds == null) {
              try {
                if (dbg) {
                  Util.prta("EQC: open socket to " + host + ":" + port);
                }
                ds = new Socket(host, port);
                if (timeout == null) {
                  timeout = new ReadTimeout(ds, timesec);
                } else {
                  timeout.setSocket(ds);
                }
                if (dbg) {
                  Util.prta("EQC: Socket is now open timeout=" + timesec);
                }
              } catch (IOException e) {
                ds = null;
                if (e != null) {
                  if (e.getMessage() != null) {
                    if (e.getMessage().contains("Connection refused")) {
                      Util.prta("Got a connection refused. " + host + "/" + port + "  Is the server up?  Wait 20 and try again");
                    }
                    else {
                      Util.prta("Got IOerror opening socket to server " + host + "/" + port+" e=" + e);
                    }
                  } else {
                    Util.prta("Got IOError opening socket to server " + host + "/" + port+" e=" + e);
                  }
                } else {
                  Util.prta("Got IOError opening socket to server " + host + "/" + port+" e=" + e);
                }
                Util.sleep(20000);
              }
            }
            if (timeout == null) {
              Util.prt("Timeout did not get setup=null timesec=" + timesec);
              timeout = new ReadTimeout(ds, timesec);
            }
            timeout.reset();
            in = ds.getInputStream();        // Get input and output streams
            outtcp = ds.getOutputStream();
            line = "";
            for (String arg : args) {
              if (!arg.equals("")) {
                line += "'" + arg.replaceAll("@", " ") + "' ";
              }
            }
            line = line.trim().replaceAll("-list", "-gaps") + "\t";
            if(dbg) {
              Util.prta("Write line : "+line);
            }
            outtcp.write(line.getBytes());
          }

          // put command line in single quotes.
          line = ""; 
          long maxTime = 0;
          int ndups = 0;
          int nmassive = 0;
          ArrayList<MiniSeed> blks = new ArrayList<>(100);
          try {

            boolean perfStart = true;
            int iblk = 0;
            String lastComp = "            ";
            boolean eof = false;
            boolean got500k = false;
            MiniSeed ms;
            if (type.equals("sac")) {
              if (compareLength < 10) {
                Util.prt("\n    ***** Sac files must have names including the channel! *****");
                if (timeout != null) {
                  timeout.terminate();
                }
                return null;
              }

            }
            if (type.equals("msz") && compareLength < 10) {
              Util.prt("\n    ***** msz files must have names including the channel! *****");
              if (timeout != null) {
                timeout.terminate();
              }
              return null;
            }
            int npur = 0;
            if (perfdbg) {
              Util.prta("Start reads..");
            }
            msConnect += (System.currentTimeMillis() - startPhase);
            startPhase = System.currentTimeMillis();
            boolean broken = false;
            while (!eof) {
              // Try to read a mini-seed, if it failes mark eof
              if (host.equals("file")) {  // read data from a file
                try {
                  int len = in.read(b, 0, 512);
                  if (len != 512) {
                    eof = true;
                    ms = null;
                  } else {
                    ms = miniSeedPool.get(b, 0, 512);
                    iblk++;
                    totblks++;
                  }
                } catch (EOFException e) {
                  eof = true;
                  ms = null;
                }
              } else {      // read data from a QueryServer
                if (read(in, b, 0, (gapsonly ? 64 : 512))) {
                  if (timeout != null) {
                    timeout.reset();
                  }
                  if (b[0] == '<' && b[1] == 'E' && b[2] == 'O' && b[3] == 'R' && b[4] == '>') {
                    eof = true;
                    ms = null;
                    if (dbg) {
                      Util.prt("EOR found iblk=" + iblk);
                    }
                    lineDone = true;
                  } else {
                    ms = miniSeedPool.get(b, 0, 512);
                    //ms = new MiniSeed(b, 0, 512);
                    //SUtil.prt(""+ms);
                    if (ms != null) {
                      if (!gapsonly && ms.getBlockSize() > 512) { // the blocks is >512 and not a header only read
                        if (read(in, b, 512, ms.getBlockSize() - 512)) {
                          miniSeedPool.free(ms);      // do not need the 512 byte ms any more
                          ms = miniSeedPool.get(b, 0, ms.getBlockSize()); // Make the bigger one
                        } else {
                          eof = true;
                          Util.prta("    **** Unexpected EOF Found2");
                          broken = true;
                          continue;
                        }
                      }
                    }
                    iblk++;
                    totblks++;
                  }
                } else {
                  eof = true;         // still need to process this last channel THIS SHOULD NEVER  HAPPEN unless socket is lost
                  Util.prt("   *** Unexpected EOF Found");
                  broken = true;
                  //if(out != null) 
                  continue;      // error out with no file
                }
              }
              if (perfStart) {
                msCommand += (System.currentTimeMillis() - startPhase);
                startPhase = System.currentTimeMillis();
                perfStart = false;

              }
              //Util.prt(iblk+" "+ms);
              if (!quiet && iblk % 1000 == 0 && iblk > 0) {
                System.out.print("\r            \r" + iblk + "...");
              }

              if (eof || (lastComp.trim().length() > 0
                      && // is it time to process this channel/blks
                      (ms == null ? true : !lastComp.substring(0, compareLength).equals(
                                      ms.getSeedNameString().substring(0, compareLength)))) || blks.size() >= maxBlocks) {

                // reset the got 500 k if there is a new channel.  If marked got500k we are still on the same channel so do not process
                if (got500k) {    // This is after the first 500k were processed, skip the rest.
                  Util.prta(" ****** " + lastComp + " received over " + maxBlocks + " additional blocks : " + blks.size() + " blks skipped.... ");
                  for (int i = blks.size() - 1; i >= 0; i--) {
                    miniSeedPool.free(blks.get(i));
                  }
                  blks.clear();
                }
                if (ms != null) {
                  if (!lastComp.substring(0, compareLength).equals(ms.getSeedNameString().substring(0, compareLength))) {
                    got500k = false;
                  }
                }
                if (blks.size() >= maxBlocks) {  // First time 500k blocks received
                  Util.prta(" ****** " + lastComp + " received over " + maxBlocks + " blocks, do partial process to save myself! This channel maybe incomplete.");

                  got500k = true;
                }
                if (perfdbg) {
                  Util.prta("\nStart analysis of " + lastComp);
                }
                if (timeout != null) {
                  timeout.reset();
                }
                msTransfer += (System.currentTimeMillis() - startPhase);
                startPhase = System.currentTimeMillis();
                int nsgot = 0;

                // Check for RIC errors
                if (dropRIC && !gapsonly) {
                  doDropRics(blks, perfdbg, quiet);
                }

                // Sort the collection of blocks and eliminate any duplicates, override rates if on, 
                // derive LH if on, 
                if (blks.size() > 0) {
                  if (perfdbg) {
                    Util.prta("Start sort of blocks size=" + blks.size());
                  }
                  if (timeout != null) {
                    timeout.reset();
                  }
                  if(!nosort) {
                    Collections.sort(blks);
                  }
                  if (timeout != null) {
                    timeout.reset();
                  }
                  if (perfdbg) {
                    Util.prta("Start duplicates elimination " + lastComp + " ndups=" + ndups + " blks=" + blks.size());
                  }
                  if (!allowDups && !nosort) {
                    for (int i = blks.size() - 1; i > 0; i--) {
                      if (blks.get(i).isDuplicate(blks.get(i - 1))) {
                        ndups++;
                        miniSeedPool.free(blks.get(i));
                        blks.remove(i);
                      }
                    }
                  }

                  //Util.prt(blks.size() +" "+iblk);
                  for (MiniSeed blk : blks) {
                    if (overrideRate > 0) {
                      blk.setRate(overrideRate);
                    }
                    nsgot += (blk).getNsamp();
                  }
                  //Util.prt(""+(MiniSeed) blks.get(blks.size()-1));
                  if (!quiet && out != null) {
                    Util.prt("\r" + Util.asctime() + " Query on " + lastComp.substring(0, compareLength) + " "
                            + Util.df6(blks.size()) + " mini-seed blks "
                            + (blks.get(0) == null ? "Null" : ((MiniSeed) blks.get(0)).getTimeString()) + " "
                            + (blks.get((blks.size() - 1)) == null
                            ? "Null" : (blks.get(blks.size() - 1)).getEndTimeString()) + " "
                            + " ns=" + nsgot + " #dups=" + ndups);
                  }

                  // If we need to derive LP data, do it now
                  ArrayList<MiniSeed> blksLH = null;
                  if (deriveLH && (lastComp.substring(7, 9).equals("BH") || lastComp.substring(7, 9).equals("HH"))) {
                    blksLH = doDeriveLH(blks);
                  }

                  // If we are doing an arbitrary derive, do it
                  if (derive) {
                    DeriveAny any = new DeriveAny();
                    blksLH = any.deriveFromMS(blks, deriveRate);
                  }

                  // NOTE: Due to a foul up in data in Nov, Dec 2006 it is possible the Q330s got the
                  // same baler block twice, but the last 7 512's of the block zeroed and the other
                  // correct.  Find these and purge the bad ones.
                  if (!gapsonly) {// This is a special check
                    for (int i = blks.size() - 1; i >= 0; i--) {
                      if (blks.get(i).getBlockSize() == 4096
                              && // Has to be a big block or it does not happen
                              blks.get(i).getGregorianCalendar().compareTo(jan_01_2007) < 0
                              && blks.get(i).getUsedFrameCount() < blks.get(i).getB1001FrameCount()
                              && blks.get(i).getUsedFrameCount() <= 7 && blks.get(i).getB1001FrameCount() > 7) {
                        miniSeedPool.free(blks.get(i));
                        blks.remove(i);
                        npur++;
                      }
                    }
                  }
                  msSortDups += (System.currentTimeMillis() - startPhase);
                  startPhase = System.currentTimeMillis();                  
                  // if the output type is null, return the miniseed blocks to theuser in blksAll.
                  MiniSeed ms2 = blks.get(0);
                  if (out == null) {     // Get the array list output
                    ArrayList<MiniSeed> newBlks = new ArrayList<>(blks.size());
                    for (int i = 0; i < blks.size(); i++) {
                      newBlks.add(i, blks.get(i));
                    }
                    blksAll.add(newBlks); // NOte blksAll cannot be null
                  } // If there is a output file, create it and dispose of the blocks
                  else {      // create the output file
                    if (perfdbg) {
                      Util.prta("Start output pass " + lastComp + " ndups=" + ndups + " blks=" + blks.size());
                    }
                    if (timeout != null) {
                      timeout.reset();
                    }
                    if (beg == null) {
                      beg = new java.util.Date(blks.get(0).getTimeInMillis());
                    }
                    if (type.equals("ms") || type.equals("dcc") || type.equals("dcc512") || type.equals("msz")) {
                      filename = EdgeQueryClient.makeFilename(filemask, lastComp, ms2, false);
                    } else {
                      filename = EdgeQueryClient.makeFilename(filemask, lastComp, beg, false);
                    }

                    //filename = lastComp;
                    filename = filename.replaceAll(" ", "_");
                    if (dbg) {
                      Util.prt(((MiniSeed) blks.get(0)).getTimeString() + " to "
                              + ((MiniSeed) blks.get(blks.size() - 1)).getTimeString()
                              + " " + (((MiniSeed) blks.get(0)).getGregorianCalendar().getTimeInMillis()
                              - ((MiniSeed) blks.get(blks.size() - 1)).getGregorianCalendar().getTimeInMillis()) / 1000L);
                    }

                    //Util.prt("Found "+npur+" recs with on first block of 4096 valid");
                    blks.trimToSize();
                    //for(int i=0; i<blks.size(); i++) Util.prt(((MiniSeed) blks.get(i)).toString());
                    if (sacpz && !out.getClass().getSimpleName().contains("SacOutputer")
                            && !out.getClass().getSimpleName().contains("SacSegmentOutputer")) {   // if asked for write out the sac response file
                      //String time = blks.get(0).getTimeString();
                      //time = time.substring(0,4)+","+time.substring(5,8)+"-"+time.substring(9,17);
                      if (stasrv != null) {
                        stasrv.getSACResponse(lastComp, begin, filename);
                      }
                    }
                    // Write out the files.
                    out.makeFile(lastComp, filename, filemask, blks, beg, duration, args);
                    if (derive && blksLH != null) {
                      if (type.equals("ms") || type.equals("dcc") || type.equals("dcc512") || type.equals("msz")) {
                        filename = EdgeQueryClient.makeFilename(filemask, blksLH.get(0).getSeedNameString(), ms2, false);
                      } else {
                        filename = EdgeQueryClient.makeFilename(filemask, blksLH.get(0).getSeedNameString(), beg, false);
                      }
                      if (perfdbg) {
                        Util.prt("Calling makefile blks=" + blks.size());
                      }
                      out.makeFile(blksLH.get(0).getSeedNameString(), filename, filemask, blksLH, beg, duration, args);
                    }
                    if (deriveLH && blksLH != null) {
                      if (type.equals("ms") || type.equals("dcc") || type.equals("dcc512") || type.equals("msz")) {
                        filename = EdgeQueryClient.makeFilename(filemask, lastComp, ms2, true);
                      } else {
                        filename = EdgeQueryClient.makeFilename(filemask, lastComp, beg, true);
                      }
                      if (perfdbg) {
                        Util.prt("Calling makefile blks=" + blks.size());
                      }
                      out.makeFile(lastComp, filename, filemask, blksLH, beg, duration, args);
                    }
                  }
                } else if (!quiet && null != null) {
                  Util.prta("\rQuery on " + seedname + " returned 0 blocks!");
                }
                if (timeout != null) {
                  timeout.reset();
                }
                if (perfdbg) {
                  Util.prta("Start free miniSeedPool=" + lastComp + " miniSeedPool=" + miniSeedPool + " blks=" + blks.size());
                }
                maxTime = 0;
                if (blks.size() > 0) {
                  //miniSeedPool.freeAll();
                  if (!userFreeBlocks) {
                    for (int iii = blks.size() - 1; iii >= 0; iii--) {
                      if (!miniSeedPool.free(blks.get(iii))) {
                        Util.prta("Free failed iii=" + iii);
                      }
                    }
                  }
                  if (perfdbg) {
                    Util.prta("miniSeedPools=" + miniSeedPool);
                    MemoryChecker.checkMemory();
                  }
                  blks.clear();
                  //System.gc();        // Lots of memory just abandoned.  Try garbage collector
                }
                msOutput += (System.currentTimeMillis() - startPhase);
                startPhase = System.currentTimeMillis();
                if (perfdbg) {
                  Util.prta("End of loop " + lastComp);
                }
                if (timeout != null) {
                  timeout.reset();
                }
              }     // end of its time to process this group of blocks

              // If this block is the first in a new component, clear the blks array
              //if(!lastComp.substring(0,compareLength).equals(
              //    ms.getSeedNameString().substring(0,compareLength))) blks.clear();
              /* in late 2007 there was some files which were massively duplicated by block.
               * to prevent this from blowing memory when there are so may we eliminate and duplicate
               * blocks here.  If it is massively out of order , all of these block checks will slow things
               * down.
               **/
              boolean isDuplicate = false;
              // chkDUps is rare and only used if massively duplicate data means there is to much to store, sort and then eliminate dups
              if (ms != null) {
                int limit = (int) (duration / 1000. * ms.getRate() / 100.);   // expected nsamples divided by a worst case 100 samples per block
                if (chkDups || blks.size() > limit) {
                  if (ms.getTimeInMillis() <= maxTime) {    // No need to check duplicates if this is newest seen
                    if (!gapsonly) {
                      if (blks.size() >= 1) {
                        for (int i = blks.size() - 1; i >= (chkDups ? 0 : Math.max(blks.size() - 2 * limit, 0)); i--) {
                          if (ms.isDuplicate(blks.get(i))) {
                            isDuplicate = true;
                            nmassive++;
                            break;
                          }
                        }
                      }
                    }
                    if (dbgdup && isDuplicate) {
                      Util.prt("Dup:" + blks.size() + " " + nmassive + " " + ms);
                    }
                    if (!isDuplicate && ms.getIndicator().compareTo("D ") >= 0) {
                      blks.add(ms);
                    } else {
                      miniSeedPool.free(ms);
                      ndups++;
                    }
                  } else {
                    if (ms.getIndicator().compareTo("D ") >= 0) {
                      blks.add(ms); // If its not D or better, its been zapped!
                    } else {
                      miniSeedPool.free(ms);
                    }
                    maxTime = ms.getTimeInMillis();
                  }
                } // Add the block since its not a duplicate
                else {
                  blks.add(ms);
                }
              }
              if (ms != null) {
                lastComp = ms.getSeedNameString();
              }
            }   // while(!eof)
            if (broken) {
              Util.prta("Broken request - try again j=" + itime + " ds=" + (ds == null ? "null" : ds.isClosed() + " " + ds));
              if (ds != null) {
                if (!ds.isClosed()) {
                  try {
                    ds.close();
                  } catch (IOException expected) {
                  }
                }
              }
              for (MiniSeed blk : blks) {
                miniSeedPool.free(blk);
              }
              ds = null;
              continue;
            }        // if unexpected EOF or other bad read, just try it again
            if (!quiet && iblk > 0 && out != null) {
              Util.prt(iblk + " Total blocks transferred in "
                      + (System.currentTimeMillis() - startTime) + " ms "
                      + (iblk * 1000L / Math.max(System.currentTimeMillis() - startTime, 1)) + " b/s " + (nmassive > 0 ? "nmassive=" + nmassive : "") + npur + " #dups=" + ndups);
              if (perf) {
                long msEnd = System.currentTimeMillis() - startPhase;
                Util.prta("Perf setup=" + msSetup + " connect=" + msConnect + " Cmd=" + msCommand + " xfr=" + msTransfer + 
                        " msSortDups=" + msSortDups + " out=" + msOutput + " last=" + msEnd 
                        + " tot=" + (msSetup + msConnect + msCommand +  msTransfer + msSortDups + msOutput + msEnd) 
                        + " #blks=" + totblks + " #lines=" + nline);
              }            
            }
            if (out == null) {
              if (timeout != null) {
                timeout.terminate();
              }
              if (ds != null) {
                if (!ds.isClosed()) {
                  try {
                    ds.close();
                  } catch (IOException expected) {
                  }
                }
              }
              return blksAll;
            }     // If called in no file output mode, return the blocks
            for (MiniSeed blk : blks) {
              miniSeedPool.free(blk);
            }
            blks.clear();
          } catch (UnknownHostException e) {
            Util.prt("EQC main: Host is unknown=" + host + "/" + port);
            if (out != null) {
              System.exit(1);
            }
            if (timeout != null) {
              timeout.terminate();
            }
            return null;
          } catch (IOException e) {
            if (e.getMessage().equalsIgnoreCase("Connection refused")) {
              Util.prta("The connection was refused.  Server is likely down or is blocked. This should never happen.");
              if (timeout != null) {
                timeout.terminate();
              }
              return null;
            } else if (e.getMessage().contains("close")) {
              Util.prta("*** The connection closed in mid transfer.  Try again. blks.size()="
                      + (blks == null ? "null" : "" + blks.size()) + " blksAll.size()=" 
                      + (blksAll == null ? "Null" : "" + blksAll.size())
                      + " line="+line);
              //timeout.terminate();
              //if(out == null) return null;
            } else if (e.getMessage().contains("eset")) {
              Util.prta("*** The connection reset in mid transfer.  Try again.");
            } else if (e.getMessage().contains("Broken ")) {
              Util.prt("*** got a broken pipe.  Try again.");
            } else {
              Util.prta("Unknown IO Error ");
              e.printStackTrace();
              Util.IOErrorPrint(e, "EQC main: IO error opening/reading socket=" + host + "/" + port);
              //if(out != null) System.exit(1);
            }
            if (ds != null) {
              if (!ds.isClosed()) {
                try {
                  ds.close();
                } catch (IOException expected) {
                }
              }
            }
            ds = null;
            if (timeout != null) {
              timeout.terminate();
            }
          } catch (RuntimeException e) {
            Util.prt("Runtime err e=" + e);
            e.printStackTrace();
            if (timeout != null) {
              timeout.terminate();
            }
            System.exit(1);
          }
          if (host.equals("file")) {
            return null;
          }
          if (out == null) {
            return null;
          }
        }     // for(itime=0; itime<9;itime++) on each line to make sure full query happens
      }       // End of readline
      if (outtcp != null) {
        outtcp.write("\n".getBytes());      // Send end of request marker to query
      }
      if (ds != null) {
        if (!ds.isClosed()) {
          try {
            ds.close();
          } catch (IOException expected) {
          }
        }
      }

      if (timeout != null) {
        timeout.terminate();
      }
      return null;
    } catch (IOException e) {
      Util.SocketIOErrorPrint(e, "IOError reading input lines.");
    }
    return null;
  }

  /**
   * Convert BH or HH blocks to LH
   *
   * @param blks The HH or BH blocks
   * @return the derived LH blocks
   */
  private static ArrayList<MiniSeed> doDeriveLH(ArrayList<MiniSeed> blks) {
    ArrayList<MiniSeed> blksLH;
    DeriveLH deriveit;
    // Try using each block to build a deriveLH, some will not work
    try {
      deriveit = new DeriveLH();
      blksLH = deriveit.deriveLH(blks);
      return blksLH;
    } catch (RuntimeException e) {
      Util.prt("DeriveLH through runtime e=" + e);
    }
    // Got a DeriveLH object for this channel, process the blicks
    return null;
  }

  /**
   * Drop and RIC (reverse integration constants) blocks
   *
   * @param blks the data blocks
   * @param perfdbg Flag controlling some output
   * @param quiet Flag controlling more output
   */
  private static void doDropRics(ArrayList<MiniSeed> blks, boolean perfdbg, boolean quiet) {
    Steim2Object steim2 = new Steim2Object();
    byte[] frames = null;
    for (int i = blks.size() - 1; i >= 0; i--) {
      try {
        if (blks.get(i).getEncoding() == 11) {
          if (frames == null) {
            frames = new byte[blks.get(i).getBlockSize() - blks.get(i).getDataOffset()];
          }
          if (frames.length != blks.get(i).getBlockSize() - blks.get(i).getDataOffset()) {
            frames = new byte[blks.get(i).getBlockSize() - blks.get(i).getDataOffset()];
          }
          System.arraycopy(blks.get(i).getBuf(), blks.get(i).getDataOffset(), frames, 0, blks.get(i).getBlockSize() - blks.get(i).getDataOffset());
          int[] samples;
          // try steim 2

          if (steim2.decode(frames, blks.get(i).getNsamp(), blks.get(i).isSwapBytes())) {
            samples = steim2.getSamples();
          } else {
            if (steim2.hadReverseError() || steim2.getSampleCountError().length() > 2
                    || blks.get(i).getNsamp() != MiniSeed.getNsampFromBuf(blks.get(i).getBuf(), blks.get(i).getEncoding())) {// Reverse integration error, skip the block
              if (!quiet) {
                Util.prt("Bad block i=" + i + " " + blks.get(i).getNsamp() + "!=" + MiniSeed.getNsampFromBuf(blks.get(i).getBuf(), blks.get(i).getEncoding())
                        + " Blk has errors RIC=" + steim2.getReverseError() + " count=" + steim2.getSampleCountError());
              }
              if (!quiet) {
                Util.prt("** RIC Dropping " + blks.get(i).toString());
              }
              miniSeedPool.free(blks.get(i));
              blks.remove(i);
            }
          }
        }   // If this is steim 2
      } catch (IllegalSeednameException e) {
        Util.prt("** Illegal seedname exception.  Dropping " + blks.get(i).toString());
        miniSeedPool.free(blks.get(i));
        blks.remove(i);
      } catch (SteimException e) {
        Util.prt("Steim exception e=" + e);
      } catch (RuntimeException e) {
        Util.prta("*** RunTime in doDropRics blks.size=" + blks.size() + " i=" + i);
        if (blks.get(i) != null) {
          Util.prta(blks.get(i).toString());
        }
      }

    }
    if (perfdbg) {
      Util.prta("Done with decomp checks ");
    }
  }

  /**
   * Do the -lsc command,
   *
   * @param host Host IP
   * @param port Host port
   * @param seedname Seedname
   * @param begin Beginning time
   * @param durationString Duration string
   * @param delazString Delaz
   * @param exclude
   * @param eqdbg
   * @param lschannels
   * @param showIllegals
   * @param sb
   * @return
   */
  public static StringBuilder doLSC(String host, int port, String seedname,
          String begin, String durationString, String delazString, String exclude, boolean eqdbg,
          boolean lschannels, boolean showIllegals, StringBuilder sb) {
    try {
      Socket ds = new Socket(host, port);
      ds.setReceiveBufferSize(512000);
      //ds.setTcpNoDelay(true);
      InputStream in = ds.getInputStream();        // Get input and output streams
      OutputStream outtcp = ds.getOutputStream();
      String line;
      if (!exclude.equals("")) {
        line = "'-el' '" + exclude + "' ";
      } else {
        line = "";
      }
      if (!begin.equals("")) {
        line += "'-b' '" + begin.trim() + "' ";
      }
      if (!durationString.equals("")) {
        line += "'-d' '" + durationString + "' ";
      }
      if (!delazString.equals("")) {
        line += "'-delazc' '" + delazString + "' ";
      }
      if (eqdbg) {
        line += "'-eqdbg' ";
      }
      if (!seedname.equals("")) {
        line += "'-s' '" + seedname + "' ";
      }
      if (lschannels) {
        if (showIllegals) {
          line += "'-si' ";
        }
        line += "'-lsc'\n";
      } else {
        line += "'-ls'\n";
      }
      Util.prta(host + "/" + port + " line=" + line.substring(0, line.length() - 1) + ":");
      outtcp.write(line.getBytes());
      if (sb == null) {
        sb = new StringBuilder(100000);
      }
      int len;
      byte[] b = new byte[512];
      while ((len = Util.socketRead(in, b, 0, 512)) > 0) {
        //while( (len=in.read(b, 0, 512)) > 0 ) {
        sb.append(new String(b, 0, len));
      }
      Util.prt(sb.toString());
      ds.close();
      return null;
    } catch (IOException e) {
      Util.IOErrorPrint(e, "Getting a directory");
      return null;
    }
  }

  /**
   * force the QueryServer to reset - that is the QueryMom at the other ends will exit.
   *
   * @param host Host IP
   * @param port port number (2061!);
   */
  private static void doReset(String host, int port) {
    try {
      try (Socket ds = new Socket(host, port)) {
        InputStream in = ds.getInputStream();        // Get input and output streams
        OutputStream outtcp = ds.getOutputStream();
        outtcp.write("'-reset'\n".getBytes());
      } // Get input and output streams
    } catch (IOException e) {
      Util.IOErrorPrint(e, "Getting a directory");
    }
  }

  /**
   *
   * @param in
   * @param b The buffer for the data
   * @param off Offset in b to put the bytes
   * @param l The number of bytes to read
   * @return false if EOF is found, else return the bytes
   * @throws IOException
   */
  public static boolean read(InputStream in, byte[] b, int off, int l)
          throws IOException {
    try {
      int len;
      while ((len = in.read(b, off, l)) >= 0) {
        //while( (len = in.read(b, off, l)) > 0) {
        off += len;
        l -= len;
        if (l == 0) {
          return true;
        }
      }
      // if len < 0, then its EOF
      Util.prta("read() saw an EOF len=" + len);
      return false;
    } catch (IOException e) {
      Util.prta("IOException in read().  off=" + off + " l=" + l + " avail=" + in.available());
      throw e;
    }
  }

  /**
   * If a java program using EdgeQueryClient it needs to set the blocks free after processing them
   *
   * @param mss The block structure returned by a call to EdgeQueryClient.query() with -t null
   * @param ttag A tag to use in logging
   * @param par A place to log output
   */
  public static void freeQueryBlocks(ArrayList<ArrayList<MiniSeed>> mss, String ttag, PrintStream par) {
    miniSeedPool = EdgeQueryClient.getMiniSeedPool();
    int nfree = 0;
    if (mss != null) {
      if (par != null && dbgMiniSeedPool) {
        par.println(ttag + "MiniSeedPoolE before=" + miniSeedPool.toString() + " mss.size=" + (mss.size() > 0 ? mss.get(0).size() : "0"));
      }
      boolean ok = true;
      for (ArrayList<MiniSeed> ms : mss) {
        for (int iii = ms.size() - 1; iii >= 0; iii--) {
          if (!miniSeedPool.free(ms.get(iii))) {
            if (par != null) {
              par.println(ttag + "MiniSeed block did not free iii=" + iii);
            }
            ok = false;
          } else {
            nfree++;
          }
        }
        ms.clear();
      }
      mss.clear();
      if (par != null && dbgMiniSeedPool) {
        par.println(ttag + "MiniSeedPoolE aft=" + miniSeedPool.toString() + " freed=" + nfree);
      }
      if (miniSeedPool.getUsedList().size() > 200000) {
        SendEvent.edgeSMEEvent("CWBUsedBig", "Used blocks big " + miniSeedPool.getUsedList().size(), ttag);
      }
      if (!miniSeedPool.getUsedList().isEmpty() && !ok) {
        for (int i = 0; i < miniSeedPool.getUsedList().size(); i++) {
          if (i < 10 || i % 100 == 0) {
            if (par != null) {
              par.println(i + " Not freed : " + miniSeedPool.getUsedList().get(i).toString());
            }
          }
        }
      }
    }
  }

  /**
   * Free blocks from the miniSeedPool that are in a ArrayList (i.e. not returned by EdgeQueryClient
   * directly)
   *
   * @param mss The ArrayList of MiniSEED blocks
   * @param ttag A logging tag
   * @param par A Printstream for logging
   */
  public static void freeBlocks(ArrayList<MiniSeed> mss, String ttag, PrintStream par) {
    miniSeedPool = EdgeQueryClient.getMiniSeedPool();
    int nfree = 0;
    if (mss != null) {
      if (par != null && dbgMiniSeedPool) {
        par.println(ttag + "MiniSeedPoolE before=" + miniSeedPool.toString() + " mss.size=" + mss.size());
      }
      boolean ok = true;
      for (int iii = mss.size() - 1; iii >= 0; iii--) {
        if (!miniSeedPool.free(mss.get(iii))) {
          if (par != null) {
            par.println(ttag + "MiniSeed block did not free iii=" + iii);
          }
          ok = false;
        } else {
          nfree++;
        }
      }
      mss.clear();
      if (par != null && dbgMiniSeedPool) {
        par.println(ttag + "MiniSeedPoolE aft=" + miniSeedPool.toString() + " freed=" + nfree);
      }
      if (miniSeedPool.getUsedList().size() > 200000) {
        SendEvent.edgeSMEEvent("CWBUsedBig", "Used blocks big " + miniSeedPool.getUsedList().size(), ttag);
      }
      if (!miniSeedPool.getUsedList().isEmpty() && !ok) {
        for (int i = 0; i < miniSeedPool.getUsedList().size(); i++) {
          if (i < 10 || i % 100 == 0) {
            if (par != null) {
              par.println(i + " Not freed : " + miniSeedPool.getUsedList().get(i).toString());
            }
          }
        }
      }
    }
  }

  public static void main(String[] args) {
    Util.init("query.prop");
    Util.debug(false);
    Util.setModeGMT();
    Util.setProcess("CWBQuery");
    /*query("-s USISCO LHZ  -b 2007,1-00:00 -d 1d -t ms");
    query("-s \"USISCO LHZ\"  -b 2007,1-00:00 -d 1d -t ms");
    query("-s \"USISCO LHZ  -b 2007,1-00:00 -d 1d -t ms");
    query("-s \"USISCO LHZ\"  -b '2007/1/1 00:00' -d  1d -t  ms");
     **/

    //ArrayList<ArrayList<MiniSeed>> msszz =EdgeQueryClient.query("-s USDUG..BHZ -b 2012,262 -d 300 -t null");
    //Util.prt("blks="+msszz.get(0).size());
    VMSServer vms = null;
    String host = "137.227.224.97";
    Util.setNoconsole(false);
    Util.setNoInteractive(true);

    int port = 7808;
    for (int i = 0; i < args.length; i++) {
      if (args[i].equals("-version")) {
        try {
          StringBuilder sb = getVersion(args[i + 1], Integer.parseInt(args[i + 2]));
          Util.prt("len=" + sb.length() + " " + sb + "|");
        } catch (IOException e) {
          e.printStackTrace();
          System.exit(1);
        }
        System.exit(0);
      }
      if (args[i].equals("-vms")) {
        Util.prt("In VMS server mode");
        vms = new VMSServer(port);
      }
      //if(args[i].equalsIgnoreCase("-h")) {
      //  Util.prta("-h "+args[i+1]);
      //}
      if (args[i].equalsIgnoreCase("-sac2ms")) {
        SacToMS.main(args);
      }
      if (args[i].equalsIgnoreCase("-qc2ms")) {
        QuakeCatcherSacDump.main(args);
        System.exit(0);
      }
      if (args[i].equalsIgnoreCase("-float2ms")) {
        FloatsToMS.main(args);
      }
      if (args[i].equalsIgnoreCase("-hvoscan")) {
        HVOScan.main(args);
        System.exit(0);
      }
      if (args[i].equals("-ms2sac")) {
        TreeMap<String, RawDisk> outfiles = new TreeMap<>();
        RawDisk file;
        try {
          file = new RawDisk(args[i + 1], "rw");
          byte[] buf = new byte[4096];
          for (int iblk = 0; iblk < file.length() / 512; iblk++) {
            int blockLen = 512;
            file.readBlock(buf, iblk, blockLen);
            blockLen = MiniSeed.crackBlockSize(buf);
            if (blockLen > 512) {
              file.readBlock(buf, iblk, blockLen);
              iblk += blockLen / 512 - 1;
            }
            try {
              String seedname = MiniSeed.crackSeedname(buf).replaceAll(" ", "_");
              RawDisk put = outfiles.get(seedname);
              if (put == null) {
                MiniSeed ms = new MiniSeed(buf, 0, blockLen);
                GregorianCalendar g = ms.getGregorianCalendar();
                put = new RawDisk(seedname + "_" + Util.df4(g.get(Calendar.YEAR)) + "_" + Util.df3(g.get(Calendar.DAY_OF_YEAR))
                        + "_" + Util.df2(g.get(Calendar.HOUR_OF_DAY)) + Util.df2(g.get(Calendar.MINUTE)) + ".ms", "rw");
                put.position(0);
                put.setLength(0L);
                outfiles.put(seedname, put);

              }
              put.write(buf, 0, blockLen);
              MiniSeed ms = new MiniSeed(buf, 0, blockLen);
              Util.prt(ms.toString());
            } catch (IOException e) {
              Util.prta("IOError e=" + e);
              e.printStackTrace();
            } catch (IllegalSeednameException e) {
              Util.prta("Illegal seedname=" + e);
            }
          }
          Iterator<RawDisk> itr = outfiles.values().iterator();
          while (itr.hasNext()) {
            RawDisk rd = itr.next();
            String filename = rd.getFilename();
            String[] arg = new String[8];
            arg[0] = "-h";
            arg[1] = "file";
            arg[2] = "-s";
            arg[3] = filename;
            arg[4] = "-t";
            arg[5] = "sac";
            arg[6] = "-sacpz";
            arg[7] = "um";
            ArrayList<ArrayList<MiniSeed>> mss2 = EdgeQueryClient.query(arg);
          }

        } catch (IOException e) {
          Util.prta("File not found or other exception opening input file e=" + e);
          System.exit(1);
        } catch (IllegalSeednameException e) {
          Util.prta("Illegal seedname=" + e);
        }
        System.exit(0);
      }
    }
    if (vms == null) {
      ArrayList<ArrayList<MiniSeed>> mss2 = EdgeQueryClient.query(args);
      if (mss2 == null) {
        System.exit(1);
      }
      System.exit(0);
    }

  }

}
