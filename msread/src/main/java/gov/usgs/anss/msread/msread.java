/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.msread;

//import gov.usgs.anss.gui.DasConfig;
//import gov.usgs.anss.seed.Steim1;
//import gov.usgs.anss.seed.Steim2;
//import gov.usgs.anss.seed.SteimException;
import gov.usgs.anss.edge.MasterBlock;
import gov.usgs.anss.edge.IndexFileReplicator;
import gov.usgs.anss.edge.IndexFile;
import gov.usgs.anss.edge.IndexBlock;
//import gov.usgs.anss.edge.EdgeProperties;
import gov.usgs.alarm.SendEvent;
import gov.usgs.anss.db.DBConnectionThread;
import gov.usgs.anss.db.DBSetup;
import gov.usgs.anss.edge.*;
import gov.usgs.anss.edgemom.HoldingSender;
import gov.usgs.anss.net.HoldingArray;
import gov.usgs.anss.net.Wget;
import gov.usgs.anss.seed.MiniSeedPool;
import gov.usgs.anss.seed.*;
import gov.usgs.anss.edgethread.MemoryChecker;
import gov.usgs.anss.util.SeedUtil;
import gov.usgs.anss.util.SimpleSMTPThread;
import gov.usgs.anss.util.UserPropertiesPanel;
import gov.usgs.anss.util.Util;
import java.awt.BorderLayout;
import java.awt.Frame;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.*;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.text.DecimalFormat;
import java.util.*;

/**
 * This is a utility class that does many functions and probably should be refactored into other
 * commands. It can :
 * <br>
 * 1) Dump miniSEED files using the -r -rd
 * <br>
 * 2) Analyze the contents of IndexFiles and dump them like directories (-id for instance)
 * <br>
 * 3) Scan data files,update holdings, and optionally create fetch list entries for channels with
 * non-blank fetch types (bases for updateHoldings Script)
 * <br>
 * 4) Inserted miniSEED files into a CWB/Edge using the -edge options with other switches required
 * <br>
 * 5) Trim the size of .ms files to save space at the end (-trimsize) - especially Edge files moved
 * by replication to a CWB
 * <br>
 * 6) Convert IDA 10.4 data to miniSEED with the -idaload command
 * <br>
 * 7) Convert SAC files from DST digitizers into miniSEED using HVOScan developed for same
 * (-hvoscan)
 * <br>
 * There are likely other non-documented functions that have been dumped into this program.
 * <PRE>
 * msread [-b nnn] [-dbg] [-err] [-d][-v][-ii yyyy doy][-s pattern] filenames  #Version 1.8 Feb 24, 2017");
 * General options :
 *    -editprop Bring up a GUI to edit my msread.prop file
 *    -b nnnnn Set block size
 *    -dbg     Set voluminous output for miniseed dumps(IndexFiles, setup, etc.)
 *    -err     Set errors only must appear before -ii (if present)
 *    -decode  Do the decompression and list the time seriies
 * Index related options (uses and dumps index headers for channels minimum):
 *    -trimsize Trim the size of the data file
 *    -id Index dump only on this file [-err][-v][-d]
 *    -s pattern is regular expression to use when matching channels (12 char '.' for match any)
 *    -d n     Read data when doing a -ii must appear before -ii Bit mask of :
 *             n=0 do not read data unless holdings mode is on, 1= read but list errors only,
 *             n=2 read display all of the data blocks
 *             n=4 read and display all of the data including discontinuities
 *             n=8 decompress all packets and check for decompression errors
 *    -v       Verbose index output (extents detail)
 *    -ii yyyy doy  Dump the index on this node for this year and doy
 *    -c  file1 file2  Read both files and compare spans and data(single channel only)
 *    -if  Run with holdings update, fetchlist build, etc.  Maintenance mode only - not a user command!
 * RawMode options - used if index is to be ignored
 *    -r       Raw mode : read file with out index as mini-SEED
 *    -rd      Raw mode : decompress and show a little data at beginning and end
 *              Activity Flags:  S=Swap, C=CAL ON, *=Time Corrected, T=Event Start, t=Event End, -=Neg Leap, +=Pos Leap, E=Event ON
 *              IOClock Flags: P=Parity Error, L=Long Rec, s=Short Rec, O=Start Series, F=End Series, U=Clock Locked
 *              Data Quality : A=Amp Saturated, c=Clipped, G=Spikes, g=Glitches, M=Missing data, e=Telemetry Error, d=Charging, q=Questionable Time
 *             B2000 : encoding rec#/[RS]record  or stream [PN] package or not frg=[A1CE] All, 1st, continue,or end block of many
 *    -blk  blknum Start scanning at this block. Usefull to jump in the middle of large files
 *    -setB1000:[012]:NNNN if no Blockette 1000, try after setting steim 1 or 2 or 0 try to discover encoding and using block length NNNN  *
 * Holding mode :
 *    -hold[:nnn.nnn.nnn.nnn:pppp:TY] where the holding mode server, port and type are given
 *           If this mode is set, -d can be zero.
 *    -ignoreholdings  Do not actually send the holdings, but operate like it would
 * Send to Edge/CWB mode
 * -ris[:nnn.nnn.nnn.nnn:pppp:cut]   Send data to RawToMiniSeed server on GCWB
 * -edge[:nnn.nnn.nnn.nnn:pppp:cut] where you set the edge server name, port, and cut # days
 *          If data is after the cut number of days it is sent to the CWB directly
 *    -first yyyy,ddd-hh:mm:ss Do not put in any blocks before this time
 *    -last  yyyy,ddd-hh:mm:ss Do not put in any blocks after this time
 *    -fs SSSSS Force the station name to be SSSSS
 *    -fn NN Force the network name to be NN
 *    -fc CCC Force the component name to be CCC
 *    -fl LL Force the location to be LL
 *    -allowrecent Allow data newer that cutoff into the edge node (default only CWB can be loaded)
 *    -ignoreholdings  Do not vet the insertion with holdings database
 *    -cwbIP nnn.nnn.nnn.nnn Send data to this IP address when data is not in cut days
 *    -ignoreBogus  Do not allow blocks with rate=&lt. or nsamp&lt=0 into database (will block some admin channels)
 *    -cwbport nnnn Send the data to this CWB port rather than the default (2062)
 *    -sleepms mmm Send data with no throttle  (Def=10, 0=none - do not use this to EDGE or CWB nodes!)
 *    -setB1000:[012]:NNNN if no blockette 1000 found, load after setting steim 1 or 2 or 0=try to discover encoding and using block length NNNN
 *    -nooutput do a trial run of the load, but do not put anything in the edge or CWB (good for checking quality of MiniSEED)
 *    -x SeedRE Do not include blocks matching the regular expression
 *    -sanity Do not insert networks not on the sanity check list (def(NEIC)=IU-GT-IW-US-CU-IC-NE-GS-II-LB-NQ-NP-CN-");
 *    -sanityNets NT-N1-N2 Do not insert networks not on this list of hypen separated networks.  This switch implies -sanity ");
 * -db servurl  Use this server for database instead of the one in msread.prop  *
 * -mysql serv  Use this server for MySQL instead of the one in msread.prop [obsolete)
 * -idaload NNSSSS convert the files on the remainder of the line from ISI 10.4 format, to MiniSEED
 * -hvoscan     Convert SAC files for HVO doing special processing to calculate rates and time (see HVOScan javadoc)
 * </PRE>
 *
 * @author davidketchum
 */
public class msread extends Thread {

  private int readData;
  private boolean brief;
  private boolean decode;
  private String seedMask = "";
  private boolean imode;
  private boolean rawMode;    // if true, read it as a Mini-seed file (no index) 
  private boolean errorsOnly;
  private int julian;
  private int startblock;
  private boolean holdingMode;
  private String holdingIP;
  private int holdingPort;
  private String holdingType;

  // Edge send parameters
  private boolean edgeMode;
  private String edgeIP;
  private int edgePort;
  private int edgeCutoff;
  boolean risMode;

  private int blocklen;
  private GregorianCalendar last;
  private byte[] buf;
  private int start;
  private DBConnectionThread dbconn;
  private double fscale;      // scaling factor for converting floats to ints
  private String sanityNets = "IU-GT-IW-US-CU-IC-NE-GS-II-LB-NQ-NP-CN-IM-HV-AV-C0-";

  /**
   * Creates a new instance of msread
   *
   * @param args The command line arguments per the documentation
   */
  public msread(String[] args) {
    Util.setNoInteractive(true);
    long JAN_01_2000 = new GregorianCalendar(2000, 0, 1).getTimeInMillis();
    Util.prt("Secs 2000 = " + JAN_01_2000 / 1000);

    if (args.length == 0) {
      Util.prt("msread [-b nnn] [-dbg] [-err] [-d][-v][-i yyyy doy][-s pattern] filenames  #Version 1.8 Feb 24, 2017");
      Util.prt("General options :");
      Util.prt("   -editprop Bring up a GUI to edit my msread.prop file");
      Util.prt("   -b nnnnn Set block size");
      Util.prt("   -dbg     Set voluminous output for miniseed dumps(IndexFiles, setup, etc.)");
      Util.prt("   -err     Set errors only must appear before -i (if present) ");
      Util.prt("   -decode  Do the decompression and list the time seriies");
      Util.prt("Index related options (uses and dumps index headers for channels minimum):");
      Util.prt("   -trimsize Trim the size of the data file");
      Util.prt("   -id Index dump only on this file [-err][-v][-d]");
      Util.prt("   -s pattern is regular expression to use when matching channels (12 char '.' for match any)");
      Util.prt("   -d n     Read data when doing a -i must appear before -i Bit mask of :");
      Util.prt("            n=0 do not read data unless holdings mode is on, 1= read but list errors only, ");
      Util.prt("            n=2 read display all of the data blocks");
      Util.prt("            n=4 read and display all of the data including discontinuities");
      Util.prt("            n=8 decompress all packets and check for decompression errors");
      Util.prt("   -v       Verbose index output (extents detail)");
      Util.prt("   -i yyyy doy  Dump the index on this node for this year and doy");
      Util.prt("   -c  file1 file2  Read both files and compare spans and data(single channel only)");
      Util.prt("   -if  Run with holdings update, fetchlist build, etc.  Maintenance mode only - not a user command!");
      Util.prt("RawMode options - used if index is to be ignored");
      Util.prt("   -r       Raw mode : read file with out index as mini-SEED");
      Util.prt("   -rd      Raw mode : decompress and show a little data at beginning and end");
      Util.prt("             Activity Flags:  S=Swap, C=CAL ON, *=Time Corrected, T=Event Start, t=Event End, -=Neg Leap, +=Pos Leap, E=Event ON");
      Util.prt("             IOClock Flags: P=Parity Error, L=Long Rec, s=Short Rec, O=Start Series, F=End Series, U=Clock Locked");
      Util.prt("             Data Quality : A=Amp Saturated, c=Clipped, G=Spikes, g=Glitches, M=Missing data, e=Telemetry Error, d=Charging, q=Questionable Time");
      Util.prt("            B2000 : encoding rec#/[RS]record  or stream [PN] package or not frg=[A1CE] All, 1st, continue,or end block of many");
      Util.prt("   -blk  BLK Start scanning at this block.  Usefull to jump in the middle of large files");
      Util.prt("   -setB1000:[012]:NNNN if no Blockette 1000, try after setting steim 1 or 2 or 0 try to discover encoding and using block length NNNN");

      Util.prt("Holding mode :");
      Util.prt("   -hold[:nnn.nnn.nnn.nnn:pppp:TY] where the holding mode server, port and type are given");
      Util.prt("           If this mode is set, -d can be zero.");
      Util.prt("   -ignoreholdings  Do not actually send the holdings, but operate like it would");
      Util.prt("Send to Edge/CWB mode");
      Util.prt("-edit ");
      Util.prt("-ris[:nnn.nnn.nnn.nnn:pppp:cut]   Send data to RawToMiniSeed server on GCWB");
      Util.prt("-edge[:nnn.nnn.nnn.nnn:pppp:cut] where you set the edge server name, port, and cut # days");
      Util.prt("         If data is after the cut number of days it is sent to the CWB directly");
      Util.prt("   -first yyyy,ddd-hh:mm:ss Do not put in any blocks before this time");
      Util.prt("   -last  yyyy,ddd-hh:mm:ss Do not put in any blocks after this time");
      Util.prt("   -fs SSSSS Force the station name to be SSSSS");
      Util.prt("   -fn NN Force the network name to be NN");
      Util.prt("   -fc CCC Force the component name to be CCC");
      Util.prt("   -fl LL Force the location to be LL");
      Util.prt("   -allowrecent Allow data newer that cutoff into the edge node (default only CWB can be loaded)");
      Util.prt("   -ignoreholdings  Do not vet the insertion with holdings database");
      Util.prt("   -cwbIP nnn.nnn.nnn.nnn Send data to this IP address when data is not in cut days");
      Util.prt("   -ignoreBogus  Do not allow blocks with rate=<0. or nsamp<=0 into database (will block some admin channels)");
      Util.prt("   -cwbport nnnn Send the data to this CWB port rather than the default (2062)");
      Util.prt("   -sleepms mmm Send data with no throttle  (Def=10, 0=none - do not use this to EDGE or CWB nodes!)");
      Util.prt("   -setB1000:[012]:NNNN if no blockette 1000 found, load after setting steim 1 or 2 or 0=try to discover encoding and using block length NNNN");
      Util.prt("   -nooutput do a trial run of the load, but do not put anything in the edge or CWB (good for checking quality of MiniSEED)");
      Util.prt("   -x SeedRE Do not include blocks matching the regular expression");
      Util.prt("   -sanity Do not insert networks not on the sanity check list (def=" + sanityNets + ")");
      Util.prt("   -sanityNets NT-N1-N2 Do not insert networks not on this list of hypen separated networks.  This switch implies -sanity ");
      Util.prt("   -db servurl  Use this server for database instead of the one in msread.prop");

      Util.prt("-mysql serv  Use this server for MySQL instead of the one in msread.prop [obsolete)");
      Util.prt("-idaload NNSSSS convert the files on the remainder of the line from ISI 10.4 format, to MiniSEED");
      Util.prt("-owsload IPADDR:PORT:NNSSSSSSCCCLL:YYYY-MM-DD-HH:MM:SS:YYYY-MM-DD-HH:MM:SS:RAWIP:RAWPORT where space are dashes");
      Util.prt("Property file pairs with defaults :");
      Util.prt("   edgeip=" + Util.getProperty("edgeip"));
      Util.prt("   edgeport=" + Util.getProperty("edgeport"));
      Util.prt("   edgecutoff=" + Util.getProperty("edgecutoff"));
      Util.prt("   holdingip=" + Util.getProperty("holdingip"));
      Util.prt("   holdingport=" + Util.getProperty("holdingport"));
      Util.prt("   holdingtype=" + Util.getProperty("holdingtype"));
      //Util.prt("holdingmode","false");
      Util.prt("   DBServer=" + Util.getProperty("DBServer"));
      Util.prt("   MySQLServer=" + Util.getProperty("MySQLServer"));
      Util.prt("   StatusDBServer=" + Util.getProperty("StatusDBServer"));
      Util.prt("   cwbip=" + Util.getProperty("cwbip"));
      Util.prt("   cwbport=" + Util.getProperty("cwbport"));
      Util.prt("   risport=" + Util.getProperty("risport"));
      Util.prt("   SMTPServer=" + Util.getProperty("SMTPServer"));
      System.exit(0);
    }
    startblock = 0;
    holdingMode = false;
    boolean fetchList = false;
    //holdingMode = Util.getProperty("holdingmode").equalsIgnoreCase("true");
    holdingIP = Util.getProperty("holdingip");
    holdingIP = Util.getProperty("StatusServer");
    holdingPort = 7996;
    holdingPort = Integer.parseInt(Util.getProperty("holdingport"));
    holdingType = "CW";
    holdingType = Util.getProperty("holdingtype");
    StringBuilder seednameSB = new StringBuilder(12);
    boolean noHoldingsSend = false;
    blocklen = 512;
    Util.setNoInteractive(true);
    boolean trimsize = false;
    boolean zap = false;
    boolean dumpMode = false;
    StringBuilder zapname = new StringBuilder(12).append("ZZZZZZZZZ");
    boolean fixIndexLinks = false;
    fscale = 0;
    boolean dbg = false;
    for (int i = 0; i < args.length; i++) {
      //Util.prt(" arg="+args[ii]+" ii="+ii);
      if (args[i].equals("-b")) {
        blocklen = Integer.parseInt(args[i + 1]);
        start = i + 2;
      } else if (args[i].equalsIgnoreCase("-poke")) {
        String [] args2 = new String[args.length -1];
        for(int ii=1; ii<args.length; ii++) {
          args2[ii-1] = args[ii];
        }
        Poke.main(args2);
        System.exit(0);
      } else if (args[i].equalsIgnoreCase("-dbg")) {//MiniSeed.setDebug(true); 
        IndexFile.setDebug(true);
        IndexFileReplicator.setDebug(true);
        IndexBlock.setDebug(true);
        Util.debug(true);
        dbg = true;
        start = i + 1;
      } else if (args[i].equalsIgnoreCase("-err")) {
        errorsOnly = true;
        start = i + 1;
      } else if (args[i].equalsIgnoreCase("-blk")) {
        startblock = Integer.parseInt(args[i + 1]);
      } else if (args[i].equalsIgnoreCase("-decode")) {
        decode = true;
        start = i + 1;
      } else if (args[i].equalsIgnoreCase("-trimsize")) {
        trimsize = true;
        start = i + 1;
      } else if (args[i].equalsIgnoreCase("-zap")) {
        zap = true;
        Util.clear(zapname).append(args[i + 1]);
        Util.rightPad(zapname, 12);
        start = i + 2;
      } else if (args[i].equalsIgnoreCase("-ignoreholdings")) {
        noHoldingsSend = true;
        start = i + 1;
      } else if (args[i].equalsIgnoreCase("-allowrecent")) {
        start = i + 1;
      } else if (args[i].equalsIgnoreCase("-cwbip")) {
        start = i + 2;
      } else if (args[i].equalsIgnoreCase("-sleepms")) {
        start = i + 2;
      } else if (args[i].equalsIgnoreCase("-cwbport")) {
        start = i + 2;
      } else if (args[i].equalsIgnoreCase("-ignoreBogus")) {
        start = i + 1;
      } else if (args[i].equalsIgnoreCase("-nooutpout")) {
        start = i + 1;
      } else if (args[i].equalsIgnoreCase("-fs")) {
        start = i + 2;
      } else if (args[i].equalsIgnoreCase("-fl")) {
        start = i + 2;
      } else if (args[i].equalsIgnoreCase("-fn")) {
        start = i + 2;
      } else if (args[i].equalsIgnoreCase("-fc")) {
        start = i + 2;
      } else if (args[i].equalsIgnoreCase("-x")) {
        start = i + 2;
      } else if (args[i].equalsIgnoreCase("-first")) {
        start = i + 2;
      } else if (args[i].equalsIgnoreCase("-last")) {
        start = i + 2;
      } else if (args[i].equalsIgnoreCase("-sanity")) {
        start = i + 1;
      } else if (args[i].equalsIgnoreCase("-sanitynets")) {
        start = i + 2;
      } else if (args[i].equalsIgnoreCase("-fscale")) {
        fscale = Double.parseDouble(args[i + 1]);
        i++;
      } else if (args[i].equalsIgnoreCase("-fixlinks")) {
        fixIndexLinks = true;
      } else if (args[i].equalsIgnoreCase("-editprop")) {
        editProperties();
        System.exit(0);
      } else if (args[i].equalsIgnoreCase("-nooutput")) {
        start = i + 1;
      } else if (args[i].equalsIgnoreCase("-fetch")) {
        doFetchList(args);
        System.exit(0);
      } else if (args[i].equalsIgnoreCase("-fetchlist") || args[i].equalsIgnoreCase("-mf")) {
        fetchList = true;
      } else if (args[i].equalsIgnoreCase("-gsn")) {
        GSNRequest.main(args);
        System.exit(0);
      } else if (args[i].equalsIgnoreCase("-mysql")) {
        Util.setProperty("MySQLServer", args[i + 1]);
        i++;
      } else if (args[i].equalsIgnoreCase("-db")) {
        Util.setProperty("DBServer", args[i + 1]);
        i++;
      } else if (args[i].equalsIgnoreCase("-dbstatus")) {
        Util.setProperty("StatusDBServer", args[i + 1]);
        i++;
      } else if (args[i].indexOf("-setB1000") == 0) {
        start = i + 1;
      } else if (args[i].equalsIgnoreCase("-loadlog")) {
        if (dbconn == null) {
          try {
            dbconn = new DBConnectionThread(Util.getProperty("StatusDBServer"), "update", "status", false, false, "msreadStatus", Util.getOutput());
            while (!DBConnectionThread.waitForConnection("msreadStatus")) {
              Util.prta("Waiting for msreadStatus connection");
            }
          } catch (InstantiationException e) {
            Util.prt("Cannot connect to database Instantiation exception e=" + e.getMessage());
            System.exit(0);
          }
        }
        String s;
        String station;
        Util.setNoInteractive(true);
        StringBuffer sb = new StringBuffer(100000);
        StringBuilder email = new StringBuilder(2000);

        for (int j = i + 1; j < args.length; j++) {
          if (sb.length() > 0) {
            sb.delete(0, sb.length());
          }
          try {
            BufferedReader in = new BufferedReader(new FileReader(args[j]));

            while ((s = in.readLine()) != null) {
              s = s.replaceAll("\\a", "<BEL>");
              s = s.replaceAll("'", "\"");    // we use single quotes so string cannot
              sb.append(s.trim()).append("\n");
            }
            int l = sb.length();

            summarize(sb);
            if (sb.length() > 60000) {
              email.append(args[j]).append(" is >60k len=").append(sb.length()).append("\n");
            }
            if (sb.indexOf("Channel not included in data") > 0) {
              email.append(args[j]).append(" has 'Channel not included in data'\n");
            }
            Util.prta("Loading =" + Util.rightPad(args[j], 22) + " len=" + Util.leftPad("" + sb.length(), 7) + " bef=" + Util.leftPad("" + l, 7)
                    + " saved=" + Util.leftPad("" + (sb.length() - l), 7) + Util.leftPad("" + ((sb.length() - l) * 100L / Math.max(1, l)), 4) + " %");

            int pos = args[j].lastIndexOf("_");
            station = args[j].substring(pos + 1);
            pos = station.indexOf(".");
            station = station.substring(0, pos);
            pos = args[j].lastIndexOf("/");
            String date = args[j].substring(pos + 1, pos + 9);
            ResultSet rs = dbconn.executeQuery("SELECT UNCOMPRESS(bin) FROM status.log where station='"
                    + station + "' AND date='" + date + "'");
            if (rs.next()) {
              int ret = dbconn.executeUpdate("UPDATE status.log set bin=compress('"
                      + rs.getString(1) + " **** appended ****\n" + sb.toString() + "') where station='"
                      + station + "' AND date='" + date + "'");
              Util.prt("Doing update " + ret);
            } else {
              int ret = dbconn.executeUpdate("INSERT into status.log (station,date,bin) values ('"
                      + station + "','" + date + "',compress('" + sb.toString() + "'))");
            }
          } catch (FileNotFoundException e) {
            Util.prt("file not found=" + args[j]);
          } catch (IOException | SQLException e) {
            Util.prt("Exception found =" + e);
            e.printStackTrace();
          }
        }
        if (email.length() > 10) {
          SimpleSMTPThread.email(Util.getProperty("emailTo"), "Log file processing errors", sb.toString());
        }
        System.exit(0);
      } else if (args[i].indexOf("-hold") == 0) {
        holdingMode = true;
        rawMode = true;
        String[] a = args[i].split(":");
        if (a.length == 4) {
          holdingIP = a[1];
          holdingPort = Integer.parseInt(a[2]);
          holdingType = a[3];
        } else {
          if (a.length != 1) {
            Util.prt("***** -hold argument format error.  Right format is -hold:nn.nn.nn.nn:pppp:TY is " + args[i]);
            System.exit(1);
          }
        }
        start = i + 1;
      } else if (args[i].indexOf("-edge") == 0 || args[i].indexOf("-ris") == 0) {
        edgeIP = "gacq1";
        edgeIP = Util.getProperty("edgeip");
        edgePort = 7205;
        edgePort = Integer.parseInt(Util.getProperty("edgeport"));
        edgeCutoff = 10;
        edgeCutoff = Integer.parseInt(Util.getProperty("edgecutoff"));
        if (args[i].indexOf("-ris") == 0) {
          risMode = true;
        }
        edgeMode = true;
        rawMode = true;
        String[] a = args[i].split(":");
        switch (a.length) {
          case 4:
            edgeIP = a[1];
            edgePort = Integer.parseInt(a[2]);
            edgeCutoff = Integer.parseInt(a[3]);
            if (edgeCutoff <= 0) {
              edgeCutoff = 10;
            } break;
          case 1:
            break;
          default:
            Util.prt("-edge mode must have all 3 arguments -edge:nnn.nnn.nnn.nnn:port:cutoff");
            System.exit(0);
        }
        start = i + 1;
      } else if (args[i].equalsIgnoreCase("-s")) {
        seedMask = args[i + 1];
        if (seedMask.length() > 0 && seedMask.length() < 12) {
          seedMask = (seedMask + "...........").substring(0, 12);
        }
        seedMask = seedMask.replaceAll(" ", ".");
        start = i + 2;
      } else if (args[i].equalsIgnoreCase("-edit")) {
        CWBEditor.main(args);
        System.exit(0);
      } else if (args[i].equalsIgnoreCase("-isaload") || args[i].equalsIgnoreCase("-isiload") || args[i].equalsIgnoreCase("-idaload")) {
        String path = Util.getProperty("logfilepath");
        if (path == null) {
          Util.setProperty("logfilepath", "./");
        }
        String station = args[i + 1];
        if (dbg) {
          ISALoader.setDebug(dbg);
        }
        ISALoader tmp = new ISALoader("-empty >>isaload", station);
        for (int j = i + 2; j < args.length; j++) {
          tmp.doFile(args[j]);
        }
        tmp.close();
        System.exit(0);
      } else if (args[i].equalsIgnoreCase("-owsload")) {
        OWSLoader.main(args);
        System.exit(0);
      } else if (args[i].equalsIgnoreCase("-d")) {
        readData = Integer.parseInt(args[i + 1]);
        start = i + 2;
      } else if (args[i].equalsIgnoreCase("-v")) {
        brief = true;
        start = i + 1;
      } else if (args[i].equalsIgnoreCase("-r")) {
        rawMode = true;
        start = i + 1;
      } else if (args[i].equalsIgnoreCase("-rd")) {
        rawMode = true;
        decode = true;
        start = i + 1;
      } else if (args[i].equalsIgnoreCase("-rp")) {
        rawMode = true;
        start = i + 1;
        MiniSeed.setStrict(false);
      } else if (args[i].equalsIgnoreCase("-rdp")) {
        rawMode = true;
        decode = true;
        start = i + 1;
        MiniSeed.setStrict(false);
      } else if (args[i].equalsIgnoreCase("-strictric")) {
        Steim2.setStrictRIC(true);
      } else if (args[i].equalsIgnoreCase("-i")) {
        imode = true;
        MiniSeed.setDebug(false);
        julian = gov.usgs.anss.util.SeedUtil.toJulian(Integer.parseInt(args[i + 1]), Integer.parseInt(args[i + 2]));
        Util.setProperty("Node", args[i + 3]);
        i += 3;
        if (julian < 2450000) {
          Util.prt("-i option year and doy translate to bad julian yr=" + args[i + 1] + " doy=" + args[i + 2]);
          System.exit(0);
        }
      } else if (args[i].equalsIgnoreCase("-c")) {
        if (args.length < i + 2) {
          Util.prt("-c requires two file name arguments");
          System.exit(0);
        }
        compareTwoFiles(args[i + 1], args[i + 2]);
        System.exit(0);
      } else if (args[i].equalsIgnoreCase("-if")) {
        imode = true;
        start = i + 1;
        julian = 0;         // indicate file specified mode
      } else if (args[i].equalsIgnoreCase("-id")) {
        imode = true;
        start = i + 1;
        dumpMode = true;
        julian = 0;
      } else if (args[i].equalsIgnoreCase("-cpustress")) {
        int nthread = Integer.parseInt(args[i + 1]);
        CPUStressThread[] thr = new CPUStressThread[nthread];
        for (int e = 0; e < nthread; e++) {
          thr[e] = new CPUStressThread();
        }
        for (int iii = 0; iii < 300; iii++) {
          Util.sleep(1000);
        }
        for (int e = 0; e < nthread; e++) {
          thr[e].terminate();
        }
      } else if (args[i].equalsIgnoreCase("-seedratio")) {
        double f = Double.parseDouble(args[i + 1]);
        findSEEDRatioForRate(f);
        System.exit(0);
      }
    }
    if (dumpMode) {
      for (int i = start; i < args.length; i++) {
        Util.prt(IndexFile.analyze(args[i], brief, errorsOnly, readData, seedMask, fixIndexLinks).toString());
        Util.prta("DumpMode done fixlinks=" + fixIndexLinks);
      }
      IndexFile.closeAll();
      System.exit(0);
    }
    if (zap) {
      for (int i = start; i < args.length; i++) {
        try {
          Util.prt("Start file=" + args[i] + " zapname=" + zapname);
          IndexFileReplicator r = null;
          try {
            r = new IndexFileReplicator(args[i], "", 0, false, false);
          } catch (EdgeFileDuplicateCreationException e) {
            Util.prta("Duplicate creation of IFR!");
            System.exit(0);
          }
          try {
            MasterBlock[] mb = r.getMasterBlocks();
            RawDisk rw = r.getRawDisk();
            byte[] sname = new byte[12];
            int ind = 0;
            int nbad = 0;
            for (int imb = 0; imb < mb.length; imb++) {
              if (mb[imb] == null) {
                break;
              }
              Util.prt(imb + " chk Master block for |" + zapname + "| blk=" + mb[imb].getBlockNumber());
              if (Util.stringBuilderEqual(zapname, "ILLEGAL")) {
                for (int ich = 0; ich < MasterBlock.MAX_CHANNELS; ich++) {
                  StringBuilder s = mb[imb].getSeedName(ich);
                  if (Util.stringBuilderEqual(s, "            ")) {
                    continue;
                  }
                  if (!Util.isValidSeedName(s)) {
                    Util.prt("Master block jam " + ("__ILLEGAL" + nbad + "      ").substring(0, 12) + " for "
                            + Util.toAllPrintable(s) + " in blk=" + mb[imb].getBlockNumber() + " index=" + ind);
                    Util.clear(seednameSB).append("  ILLEGAL").append(nbad);
                    Util.rightPad(seednameSB, 12);
                    rw.writeBlock(mb[imb].getBlockNumber(),
                            mb[imb].jamSeedname(s, seednameSB), 0, 512);
                    ind = mb[imb].getFirstIndexBlock(ich);
                    nbad++;
                    while (ind > 0) {
                      byte[] buf2 = rw.readBlock(ind, 512);
                      ByteBuffer bb = ByteBuffer.wrap(buf2);
                      bb.position(0);
                      bb.get(sname);
                      String was = new String(sname);
                      bb.position(12);
                      int next_ind = bb.getInt();
                      Util.prt("Zapping index block at " + ind + " " + ("__ILLEGAL" + nbad + "      ").substring(0, 12) + " next ind=" + next_ind);
                      bb.position(0);
                      bb.put((("__ILLEGAL" + nbad + "      ").substring(0, 12)).getBytes());
                      rw.writeBlock(ind, buf2, 0, 512);
                      ind = next_ind;
                    }
                  }
                }
              } else {      // single seedname zapper
                if ((ind = mb[imb].getFirstIndexBlock(zapname)) >= 0) {
                  Util.clear(seednameSB).append("ZZ").append(zapname.substring(2));
                  Util.prt("Master block jam ZZ" + zapname.substring(2) + " in blk=" + mb[imb].getBlockNumber() + " index=" + ind);
                  rw.writeBlock(mb[imb].getBlockNumber(), mb[imb].jamSeedname(zapname, seednameSB), 0, 512);
                  while (ind > 0) {
                    byte[] buf2 = rw.readBlock(ind, 512);
                    ByteBuffer bb = ByteBuffer.wrap(buf2);
                    bb.position(0);
                    bb.get(sname);
                    String was = new String(sname);
                    bb.position(12);
                    int next_ind = bb.getInt();
                    Util.prt("Zapping index block at " + ind + " " + zapname + " next ind=" + next_ind);
                    bb.position(0);
                    bb.put(("ZZ" + zapname.substring(2)).getBytes());
                    rw.writeBlock(ind, buf2, 0, 512);
                    ind = next_ind;
                  }
                  break;        // no not need to check the other master blocks
                }
              }
            }
          } catch (IllegalSeednameException e) {
            Util.prta("Illegal seedname e=" + e.getMessage());
          } catch (IOException e) {
            Util.prt("IOEXception trying to zap a seedname=" + e.getMessage());
            Util.IOErrorPrint(e, "IOError trying to zap " + zapname);
          }
        } catch (IOException e) {
          Util.IOErrorPrint(e, "Error opening file=" + args[i]);
        } catch (EdgeFileReadOnlyException e) {
        }
      }
      System.exit(0);
    }
    if (trimsize) {
      int savings = 0;
      for (int i = start; i < args.length; i++) {
        savings += trimsize(args[i]);
      }
      Util.prta("Total savings is " + savings + " blocks " + (savings / 2000) + " mB");
      System.exit(0);
    }
    // Do an index file analyze
    if (imode || !rawMode) {

      //EdgeProperties.init();
      for(int i=0; i<args.length; i++) {
        if(args[i].equals("-prop")) {
          Util.prt("HOldings load prop ="+args[i+1]);
          Util.loadProperties(args[i+1]);
          //Util.prtProperties();
        }
      }
      Util.setSuppressPadsbWarning(true);
      //Util.prtProperties();
      Util.prta("analyze indexDetails=" + brief + " errorsOnly=" + errorsOnly
              + " readData=" + readData + " seedmask=" + seedMask);
      if (julian > 0) {
        Util.prta(IndexFile.analyze(julian, brief, errorsOnly, readData, seedMask, fixIndexLinks).toString());
        System.exit(0);
      } else {
        try {
          //SUtil.setTestStream(args[start]+".dmp");    // FORCE CREATION OF DEBUG FILE
          HoldingSender hs = null;
          for (int i = start; i < args.length; i++) {
            if (args[i].contains("*")) {
              Util.prt("Skip file : " + args[i]);
              continue;
            }
            if (args[i].trim().equals("")) {
              continue;
            }
            Util.prt("Open file : " + args[i] + " fetchlist=" + fetchList);
            if (holdingMode && hs == null) {             // only the first file makes a holding Sender
              try {
                Util.prt("Open HoldingSender: " + "-h " + holdingIP + " -p " + holdingPort + " -t " + holdingType + " -q 10000 -tcp -quiet -noeto");
                hs = new HoldingSender("-h " + holdingIP + " -p " + holdingPort + " -t " + holdingType + " -q 10000 -tcp -quiet -noeto", "");
                Util.prt("HS:" + hs.toString());
              } catch (UnknownHostException e) {
                Util.prt("Unknown host exception host=" + holdingIP);
                System.exit(1);
              }
            }

            AnalyzeIndexFile aif = new AnalyzeIndexFile(args[i], hs, noHoldingsSend);
            Util.prta(aif.analyze(brief, readData, errorsOnly, seedMask, fixIndexLinks).toString());
            Util.prt(aif.errorSummary());
            if (hs != null) {
              hs.forceOutHoldings();
            }
            IndexFileReplicator.closeAll();
            Util.sleep(5000);
          }
          // All of the files are analyzed, if the user has asked to create fetchlist entries do that
          StringBuilder gapsEmail = new StringBuilder(1000);
          if (fetchList) {
            int nnew = makeFetchList(gapsEmail);
            int jul = AnalyzeIndexFile.getJulian();
            int[] ymd = SeedUtil.fromJulian(jul);
            int doy = SeedUtil.doy_from_ymd(ymd);
            /*String [] fargs = new String[2];
            fargs[0]="-fetch";
            fargs[1]=""+ymd[0]+","+doy+",1";*/
            Util.prta(nnew + " new gaps to database");
            AnalyzeIndexFile.getMail().append(Util.asctime()).append(" ").append(nnew).append(" new gaps to database\n");

            Util.prt(/*AnalyzeIndexFile.getMail().toString() +*/ " Exit Normal");
            Util.prt("\n\n=============== Gap Details =========================\n" + gapsEmail.toString());
            Util.prta("Run Completed - send e-mail to " + Util.getProperty("emailTo") + " SMTPFrom=" + Util.getProperty("SMTPFrom")
                    + " size=" + (AnalyzeIndexFile.getMail().toString().length() + gapsEmail.toString().length()));
            Util.sleep(2000);
            //SimpleSMTPThread.setDebug(true);
            SimpleSMTPThread smtp = SimpleSMTPThread.email(Util.getProperty("emailTo"),
                    "UpdateHoldings run " + args[start] + " " + Util.getNode() + "/" + Util.getSystemName() + "/" + Util.getLocalHostIP(),
                    AnalyzeIndexFile.getMail().toString() + " Exit Normal"
                    + "\n\n=============== Gap Details =========================\n" + gapsEmail.toString());

            Util.sleep(20000);
            Util.prt("Success=" + smtp.wasSuccessful() + " err=" + smtp.getSendMailError());
            Util.prt(Util.getThreadsString());
          }
          System.exit(0);
        } catch (RuntimeException e) {
          Util.prta("RuntimeException in msread doing a AnalyzeIndexFile e=" + e);
          SimpleSMTPThread.email(Util.getProperty("emailTo"), "UpdateHoldings run RuntimeExcep "
                  + Util.getNode() + "/" + Util.getSystemName() + "/" + Util.getLocalHostIP(), AnalyzeIndexFile.getMail().toString()
                  + "\n\n**********  Ended with RuntimeException!\n");
          e.printStackTrace();
          Util.sleep(10000);
          System.exit(1);
        }
      }
    }

    // in holding mode
    if (holdingMode) {
      doHoldingMode(start, args);
      return;
    }
    if (edgeMode) {
      doEdgeMode(start, args);
      return;
    }
    if (rawMode) {
      doRawMode(start, args);
    }
    //Util.prt("block len="+blocklen+" start="+start+" end="+(args.length-1));

  } /// ed of in holding mode  

  private void summarize(StringBuffer sb) {
    int pos = 0;
    int nlines = 0;
    for (int i = 0; i < 10000000; i++) {
      pos = sb.indexOf("\n", pos + 1);
      if (pos < 0 || pos >= sb.length()) {
        nlines = i;
        break;
      }
    }
    String[] lines = new String[nlines + 10];
    String[] deletes = new String[nlines + 10];
    int[] ncount = new int[nlines + 10];
    for (int i = 0; i < nlines + 10; i++) {
      lines[i] = "__";
    }
    pos = 0;
    int oldpos = 0;
    nlines = 0;
    while ((pos = sb.indexOf("\n", pos + 1)) >= 0) {
      lines[nlines] = sb.substring(oldpos, pos);
      if (lines[nlines].length() != 12 || !Character.isDigit(lines[nlines].charAt(0))
              || !Character.isDigit(lines[nlines].charAt(1)) || !Character.isDigit(lines[nlines].charAt(2))
              || lines[nlines].charAt(3) != ':' || !Character.isDigit(lines[nlines].charAt(4))) {
        nlines++;
      }
      oldpos = pos + 1;
    }
    Arrays.sort(lines);
    int ndelete = 0;
    for (int i = 0; i < nlines - 1; i++) {
      if (lines[i].equals(lines[i + 1])) {
        boolean found = false;
        for (int j = 0; j < ndelete; j++) {
          if (lines[i].equals(deletes[j])) {
            found = true;
            ncount[j]++;
            break;
          }
        }
        if (!found) {
          deletes[ndelete++] = lines[i];
        }
        lines[i] = null;
      }
    }
    nlines = 0;
    for (int i = 0; i < ndelete; i++) {
      //Util.prt(ncount[ii]+" "+deletes[ii]);
      if (ncount[i] > 1000) {
        deletes[nlines] = deletes[i];
        ncount[nlines++] = ncount[i];
      }
    }
    ndelete = nlines;
    //for(int ii=0; ii<ndelete; ii++)  Util.prt("final "+ncount[ii]+" "+deletes[ii]);

    try {
      BufferedReader in = new BufferedReader(new StringReader(sb.toString()));
      String s;
      String time = "";
      sb.delete(0, sb.length());
      for (int i = 0; i < ndelete; i++) {
        sb.append(ncount[i]).append(" occurances of \"").append(deletes[i]).append("\" were deleted.\n");
      }
      while ((s = in.readLine()) != null) {
        if (s.length() == 12 && Character.isDigit(s.charAt(0))
                && Character.isDigit(s.charAt(1)) && Character.isDigit(s.charAt(2))
                && s.charAt(3) == ':' && Character.isDigit(s.charAt(4))) {
          time = s;
          continue;
        }
        boolean found = false;
        for (int i = 0; i < ndelete; i++) {
          if (s.equals(deletes[i])) {
            time = "";
            found = true;
            break;
          }
        }
        if (!found) {
          if (!time.equals("")) {
            sb.append(time).append("\n");
          }
          sb.append(s).append("\n");
        }
      }
    } catch (IOException e) {
      Util.prt("IOException building new log string=" + e.getMessage());
    }
  }

  private void doHoldingMode(int start, String[] args) {
    // To get here we are in "rawMode
    int iblk;
    int nsamp = 0;
    int totgap = 0;
    int totrev = 0;
    double rate = 0.;
    int nsteim = 0;
    int totoor = 0;
    int ngap = 0;
    boolean bad512 = false;
    boolean blockout = false;
    for (String arg : args) {
      if (arg.equalsIgnoreCase("-blockout")) {
        blockout = true;
      }
      if (arg.equalsIgnoreCase("-bad512")) {
        bad512 = true;
      }
    }

    byte[] littlebuf = new byte[8192];
    RawDisk rw;
    byte[] buf2 = new byte[2048 * 512];
    if (buf == null) {
      buf = new byte[4096];
    }

    // Open up a holding sender
    HoldingSender hs;
    try {
      // Remember that to change the size of the q has implications elsewhere in this code
      Util.prt("Open HoldingSender " + "-h " + holdingIP + " -p " + holdingPort + " -t " + holdingType + " -q 10000 -tcp -quiet -noeto");
      hs = new HoldingSender("-h " + holdingIP + " -p " + holdingPort + " -t " + holdingType + " -q 10000 -tcp -quiet -noeto", "");
    } catch (UnknownHostException e) {
      Util.prt("Unknown host exception host=" + holdingIP);
      hs = null;
      System.exit(1);
    }
    for (int i = start; i < args.length; i++) {
      last = null;
      long length;
      try {

        rw = new RawDisk(args[i], "r");
        length = rw.length();
      } catch (FileNotFoundException e) {
        Util.prt("File is not found = " + args[i]);
        continue;
      } catch (IOException e) {
        Util.prt("Cannot get length of file " + args[i]);
        continue;
      }
      iblk = startblock;
      Util.prta(" Open file : " + args[i] + " start block=" + iblk + " end=" + (length / 512L) + "\n");

      long lastTime = System.currentTimeMillis() - 1000;
      int zeroInARow = 0;
      int next = 0;
      int len;
      int nsleep = 0;
      int off = 0;
      int nsend = 0;
      MiniSeed ms = null;
      //MiniSeed msbig = null;
      int iblk90 = 0;
      for (;;) {
        try {
          if (iblk90 == 0) {
            iblk90 = (int) (rw.length() / 512 / 10 * 9);
          }
          try {
            sleep(50);
          } catch (InterruptedException e) {
          }
          // read up to 2048*512 bytes or whater is left in the file - always read at least 512 to make sure EOF is reached.
          len = rw.readBlock(buf2, iblk, (iblk * 512L + 2048 * 512 <= length ? 2048 * 512 : Math.max(512, (int) (length - iblk * 512))));
          if (iblk >= next) {
            next += 200000;
            System.out.println(
                    Util.asctime().substring(0, 8) + " " + iblk + " " + (200000000 / Math.max(System.currentTimeMillis() - lastTime, 1))
                    + " b/s siz=" + rw.length() / 512 + " sleep=" + nsleep + " hsend=" + nsend + " " + args[i] + " blockout=" + blockout);
            lastTime = System.currentTimeMillis();
            nsleep = 0;
          }

          while (off < len) {
            if (iblk == 0 && off == 0) {
              if (MiniSeed.isQ680Header(buf)) {
                Util.prt("Q680 hdr " + new String(buf, 21, 30) + " skip");
                off = 512;
              }
            }
            if (buf2[off] == 0 && buf2[off + 1] == 0 && buf2[off + 2] == 0) {
              zeroInARow++;

              if (zeroInARow > 600 && iblk > iblk90) {
                System.out.println("\n" + Util.asctime() + " Zeros found for EOF " + (iblk + off / 512));
                break;
              }
              off += 512;
              continue;
            }
            zeroInARow = 0;

            //MiniSeed ms = new MiniSeed(buf);
            while (hs.getNleft() < 3000) {
              //Util.prta("Slept 1 left="+hs.getNleft()+" "+hs.getStatusString());
              while (hs.getNleft() < 5000) {
                nsleep++;
                try {
                  sleep(100);
                } catch (InterruptedException e) {
                }
              }
            }
            System.arraycopy(buf2, off, littlebuf, 0, 512);
            try {
              //if(iblk == 0 && off == 0) msbig = new MiniSeed(littlebuf);     // first buffer
              String seedname = MiniSeed.crackSeedname(littlebuf);
              if (!Util.isValidSeedName(seedname)
                      || seedname.substring(7, 10).equals("ACE") || seedname.substring(7, 10).equals("OCF")
                      || MiniSeed.crackNsamp(littlebuf) == 0) {
                off += 512;
                continue;
              }
              if (ms == null) {
                ms = new MiniSeed(littlebuf);
              } else {
                ms.load(littlebuf);
              }
              seedname = ms.getSeedNameString();
              if (bad512) {
                if (seedname.substring(0, 2).equals("US") && ms.getSequence() > 900000 && ms.getSequence() < 901000
                        && ms.getB1001USec() != 0) {
                  Util.prt(ms.toString());
                }
              }
              if (ms.getNsamp() > 0 && ms.getNsamp() < 8000
                      && ms.getJulian() > SeedUtil.toJulian(1970, 1)
                      && ms.getJulian() < SeedUtil.toJulian(2100, 1)
                      && (ms.getBlockSize() == 512 || ms.getBlockSize() == 4096)
                      && ms.getRate() > 0.0099 && ms.getRate() <= 250.) {
                nsend++;
                if (!blockout) {
                  boolean sent = hs.send(ms.getBuf());
                  while (!sent) {
                    Util.sleep(1000);
                    Util.prta("Got a not sent! iblk=" + iblk + " " + ms);
                    sent = hs.send(ms.getBuf());    // send it again
                  }
                }
                if (ms.getBlockSize() == 512) {
                  off += 512;
                } else if (65536 - (off % 65536) >= ms.getBlockSize()) {
                  off += ms.getBlockSize();
                } else {
                  off += 65536 - (off % 65536);
                }
              } else {
                try {
                  Util.prta(iblk + "/" + off + " something is not right=" + Util.toAllPrintable(seedname) + " ns=" + ms.getNsamp()
                          + " bs=" + ms.getBlockSize() + " rt=" + ms.getRate() + " jul=" + ms.getJulian());
                } catch (RuntimeException e) {
                  Util.prt("Runtime at iblk=" + iblk + " off=" + off + " is " + (e.getMessage() == null ? "null" : e.getMessage()));
                  if (e.getMessage() != null) {
                    if (!e.getMessage().contains("impossible yr")) {
                      e.printStackTrace();
                    }
                  }
                }
                off += 512;
              }
            } catch (IllegalArgumentException e) {
              off += 512;
              Util.prta("Illegal agument exception caught " + (e == null ? "null" : e.getMessage()));
              if (e != null) {
                e.printStackTrace();
              }
            } catch (IllegalSeednameException e) {
              Util.prt("Illegal seedname caught iblk=" + iblk + " off=" + off + " e=" + e.getMessage());
              off += 512;
            } catch (RuntimeException e) {
              Util.prt("Runtime2 at iblk=" + iblk + " off=" + off + " is " + (e.getMessage() == null ? "null" : e.getMessage()));
              if (e.getMessage() != null) {
                if (!e.getMessage().contains("impossible yr")) {
                  e.printStackTrace();
                }
              }
              off += 512;
            }
            //Util.prt("off="+off+" "+new MiniSeed(littlebuf).toString());

          }
          if (zeroInARow > 600 && iblk > iblk90) {
            break;
          }
          off = off - len;
          iblk = iblk + len / 512;
        } /* catch(IllegalSeednameException e ) {
          Util.prt("Illegal seedname found iblk="+iblk+" "+e.getMessage());
          iblk++;
        }*/ catch (EOFException e) {
          Util.prt("EOF on file iblk=" + iblk);
          break;
        } catch (IOException e) {
          Util.prt("IOException = " + e.getMessage());
          System.exit(1);
        }
      }     // infinite loop on a file
      try {
        while (hs.getNleft() < 9999) {
          Util.prta("" + hs);
          try {
            sleep(1000);
          } catch (InterruptedException e) {
          }
        }
        rw.close();
      } catch (IOException e) {
        Util.prt("ERrror closing file e=" + e.getMessage());
      }
    }       // loops on files
    hs.close();
    System.exit(1);   // We have done the holdings
  }

  private void doRawMode(int start, String[] args) {
    // To get here we are in "rawMode
    int iblk = 0;
    int nsamp = 0;
    int totgap = 0;
    int totrev = 0;
    double rate = 0.;
    int nsteim = 0;
    int totoor = 0;
    int ngap = 0;
    Steim2Object steim2 = new Steim2Object();
    RawDisk rw = null;
    long lastlength = 0;
    String lastString = "";
    long diff;
    GregorianCalendar time;
    MiniSeed ms;
    DecimalFormat df8 = null;
    byte[] frames;
    boolean dumpBlock = false;
    byte[] b = new byte[512];
    ByteBuffer bb = ByteBuffer.wrap(b);
    byte[] str = new byte[128];
    int overrideBlockSize = 512;
    int overrideEncoding = -1;
    byte[] frame512 = new byte[512];
    byte[] frame4096 = new byte[4096];
    byte[] buf512 = new byte[512];
    byte[] buf4096 = new byte[4096];
    String forceComponent = null;
    buf = buf4096;
    boolean createB1000 = false;
    int blockSize = 512;
    int nzero = 0;
    for (int i = 0; i < args.length; i++) {
      if (args[i].equalsIgnoreCase("-strictric")) {
        steim2.setStrictRIC(true);
      }
      if (args[i].indexOf("-setB1000") == 0) {
        String[] parts = args[i].split(":");
        createB1000 = true;
        overrideBlockSize = Integer.parseInt(parts[2]);
        overrideEncoding = Integer.parseInt(parts[1]);
      }
      if (args[i].equalsIgnoreCase("-fc")) {
        forceComponent = args[i + 1];
        i++;
      }
      if (args[i].equalsIgnoreCase("-b")) {
        blockSize = Integer.parseInt(args[i + 1]);
      }
    }
    for (int i = start; i < args.length; i++) {
      last = null;
      try {
        rw = new RawDisk(args[i], "r");
        long length = rw.length();
        iblk = startblock;
        Util.prt(i + "/" + (args.length - start) + " File : " + args[i] + " start blk=" + iblk + " end=" + (length / 512L) + "(n=nsamp dt=data offset off=blockette offset #f=frames used nb=#blockettes e=encoding)");
        nsamp = 0;
        totgap = 0;
        ngap = 0;
        totrev = 0;
        rate = 0.;
        nsteim = 0;
        totoor = 0;
        String lastComp = "";
        for (;;) {
          buf = buf512;
          int len = rw.readBlock(buf, iblk, 512);
          //for(int ii=0; ii<512; ii++) Util.prt("ii="+ii+" "+buf[ii]+" "+((char) buf[ii]));

          if (iblk == 0) {
            if (MiniSeed.isQ680Header(buf)) {
              Util.prt("Q680 hdr " + new String(buf, 21, 30));
              iblk = 1;
              buf = rw.readBlock(iblk, 512);
            }
          }
          //Util.prt("read bloc len="+buf.length+" iblk="+iblk);

          // Bust the first 512 bytes to get all the header and data blockettes - data
          // may not be complete!  If the block length is longer, read it in complete and
          // bust it appart again.
          try {
            if (forceComponent != null) {
              for (int iii = 0; iii < 3; iii++) {
                buf[15 + iii] = (byte) forceComponent.charAt(iii);
              }
            }

            MiniSeed.simpleFixes(buf);
            ms = new MiniSeed(buf);
            if (ms.hasBlk1000()) {

              if (ms.getBlockSize() > 512) {
                buf = buf4096;
                len = rw.readBlock(buf, iblk, ms.getBlockSize());
                if (forceComponent != null) {
                  for (int iii = 0; iii < 3; iii++) {
                    buf[15 + iii] = (byte) forceComponent.charAt(iii);
                  }
                }
                MiniSeed.simpleFixes(buf);
                ms = new MiniSeed(buf);
                blockSize = ms.getBlockSize();
              }
            } else {
              if (!errorsOnly) {
                Util.prt("  ** No blockette 1000, add it");
              }
              if (createB1000) {
                blockSize = overrideBlockSize;
                buf = rw.readBlock(iblk, overrideBlockSize);
                if (forceComponent != null) {
                  for (int iii = 0; iii < 3; iii++) {
                    buf[15 + iii] = (byte) forceComponent.charAt(iii);                //iblk += overrideBlockSize/512-1;
                  }
                }
                int lastBlocksize = overrideBlockSize;
                MiniSeed.simpleFixes(buf);
                ms = new MiniSeed(buf);
                int encoding = 0;
                if (overrideEncoding == 0) {
                  try {
                    if (overrideBlockSize == 512) {
                      frames = frame512;
                    } else {
                      frames = frame4096;
                    }
                    System.arraycopy(buf, ms.getDataOffset(), frames, 0, overrideBlockSize - ms.getDataOffset());
                    int[] samples = null;
                    // try steim 2
                    if (steim2.decode(frames, ms.getNsamp(), ms.isSwapBytes())) {
                      samples = steim2.getSamples();
                      encoding = 11;
                      //samples = Steim2.decode(frames,ms.getNsamp(),ms.isSwapBytes());
                    } else {
                      if (steim2.hadReverseError() || steim2.getSampleCountError().length() > 2) {// Reverse integration error, skip the block
                        samples = Steim1.decode(frames, ms.getNsamp(), ms.isSwapBytes());
                        encoding = 10;
                      } else {
                        encoding = 11;
                      }
                    }
                    Util.prt(" * Set BLk1000 deduces encoding=" + encoding);
                  } catch (SteimException e) {    //Steim error skip the block
                    Util.prt("   *** steim2 err=" + e.getMessage() + " ms=" + ms.toString());
                  } catch (RuntimeException e) {
                    Util.prt("    **** steim2 throws error. assume steim 1.  runtime=" + e);
                    encoding = 10;
                  }
                } else {
                  encoding = (overrideEncoding == 1 ? 10 : 11);
                }
                if (ms.getNBlockettes() == 0) {
                  buf = ms.getBuf();
                  boolean swap = MiniSeed.swapNeeded(buf);
                  for (int ii = 48; ii < 64; ii++) {
                    buf[ii] = 0;
                  }
                  ByteBuffer bbuf = ByteBuffer.wrap(buf);
                  if (swap) {
                    bbuf.order(ByteOrder.LITTLE_ENDIAN);
                  }
                  int blkbit;
                  int ii = overrideBlockSize;
                  for (blkbit = 0; blkbit < 32; blkbit++) {
                    if (ii <= 0) {
                      break;
                    }
                    ii /= 2;
                  }
                  blkbit--;
                  bbuf.position(39);
                  bbuf.put((byte) 1);
                  bbuf.position(46);
                  bbuf.putShort((short) 48);
                  bbuf.position(48);
                  bbuf.putShort((short) 1000);
                  bbuf.putShort((short) 0);    // no more blockettes
                  bbuf.put((byte) encoding);
                  bbuf.put((byte) (swap ? 0 : 1));
                  bbuf.put((byte) blkbit);
                  ms = new MiniSeed(buf);
                  Util.prt("AFt=" + ms);
                } else {
                  Util.prt("   **** need to add a blockette 1000 but a blockette already exists ");
                }
                iblk += overrideBlockSize / 512;
                continue;
              } else {
                Util.prt("  *** This input file does not have Blk1000 and creation of them was not specified.");
                iblk += overrideBlockSize / 512;
                continue;
              }
            }
            if (dumpBlock) {
              Util.prt("Blk=" + iblk + " ms=" + ms + "\n" + dumpBlock(buf, ms.getBlockSize()));
            }
            // List a line about the record unless we are in errors only mode
            if (!errorsOnly) {
              Util.prt(ms.toString() + " iblk=" + iblk + "/" + ms.getBlockSize());
              if (!MiniSeed.isSupportedEncoding(ms.getEncoding(), fscale)) {
                Util.prt("  ** unsupported encoding fscale=" + fscale + " encoding=" + ms.getEncoding() + " ms=" + ms);
              }
              if (ms.getRate() > 0. && ms.getNsamp() > 0 && ms.getNsamp() != MiniSeed.getNsampFromBuf(buf, ms.getEncoding())
                      && (ms.getEncoding() == 10 || ms.getEncoding() == 11 || ms.getEncoding() == 30
                      || ms.getEncoding() == 32 || ms.getEncoding() == 16)) {
                Util.prt("   ** nsamps do not agree " + ms.getNsamp() + " " + MiniSeed.getNsampFromBuf(buf, ms.getEncoding()) + " " + ms);
              }
              byte[] b2 = ms.getBlockette500();
              if (b2 != null) {
                System.arraycopy(b2, 0, b, 0, b2.length);
                bb.position(0);
                Util.prt("  ** Blk500 " + bb.getShort() + " off=" + bb.getShort() + " vco=" + bb.getFloat() + " time=" + bb.getShort() + " "
                        + bb.getShort() + ":" + bb.get() + ":" + bb.get() + ":" + bb.get() + (bb.get() == 0 ? "" : "") + "." + bb.getShort()
                        + " usec=" + bb.get() + " rqual=" + bb.get() + " excount=" + bb.getInt());
                bb.get(str, 0, 16);
                Util.prt("  ** Except  type=" + new String(str, 0, 16));
                bb.get(str, 0, 32);
                Util.prt("  ** Clock Model =" + new String(str, 0, 32));
                bb.get(str, 0, 128);
                Util.prt("  ** Clock status=" + new String(str, 0, 128));
              }
              // blockett 200
              b2 = ms.getBlockette200();
              if (b2 != null) {
                System.arraycopy(b2, 0, b, 0, b2.length);
                bb.position(0);
                Util.prt("  ** Blk200 " + bb.getShort() + " off=" + bb.getShort() + " sig amp=" + bb.getFloat()
                        + " sig per=" + bb.getFloat() + " Back=" + bb.getFloat()
                        + " event det flgs=" + Util.toHex(bb.get()) + " rsvd=" + bb.get() + " onset=" + bb.getShort() + " "
                        + bb.getShort() + ":" + bb.get() + ":" + bb.get() + ":" + bb.get() + (bb.get() == 0 ? "" : "") + "." + bb.getShort());
                bb.get(str, 0, 24);
                Util.prt("  ** Detector name=" + new String(str, 0, 24));

              }
              // Blockette 201
              b2 = ms.getBlockette201();
              if (b2 != null) {
                System.arraycopy(b2, 0, b, 0, b2.length);
                bb.position(0);
                Util.prt("  ** Blk201 " + bb.getShort() + " off=" + bb.getShort()
                        + " sig amp=" + bb.getFloat()
                        + " sig per=" + bb.getFloat() + " Bkgrnd=" + bb.getFloat()
                        + " event det flags=" + Util.toHex(bb.get()) + " rsvd=" + bb.get() + " onset=" + bb.getShort() + " "
                        + bb.getShort() + ":" + bb.get() + ":" + bb.get() + ":" + bb.get() + (bb.get() == 0 ? "" : "") + "." + bb.getShort());
                Util.prt(" SNR=" + bb.get() + " " + bb.get() + " " + bb.get() + " " + bb.get() + " " + bb.get() + " " + bb.get() + " "
                        + " lookback=" + bb.get() + " Pick algorithm=" + bb.get());

                bb.get(str, 0, 24);
                Util.prt("  ** Detector name=" + new String(str, 0, 24));

              }
              // blockett 300
              b2 = ms.getBlockette300();
              if (b2 != null) {
                System.arraycopy(b2, 0, b, 0, b2.length);
                bb.position(0);
                Util.prt("  ** Blk300 " + bb.getShort() + " off=" + bb.getShort()
                        + " beg time=" + bb.getShort() + " "
                        + bb.getShort() + ":" + bb.get() + ":" + bb.get() + ":" + bb.get() + (bb.get() == 0 ? "" : "") + "." + bb.getShort()
                        + " #steps=" + bb.get() + " cal flags=" + Util.toHex(bb.get()) + " duration=" + bb.getInt()
                        + " int duration=" + bb.getInt() + " cal amp=" + bb.getFloat());

                bb.get(str, 0, 3);
                Util.prt("  ** Chan=" + new String(str, 0, 3) + " reserv=" + bb.get() + " ref amp=" + bb.getInt());
                bb.get(str, 0, 12);
                Util.prt("  ** coupling=" + new String(str, 0, 12));
                bb.get(str, 0, 12);
                Util.prt("  ** roll off=" + new String(str, 0, 12));

              }
              // blockett 310
              b2 = ms.getBlockette310();
              if (b2 != null) {
                System.arraycopy(b2, 0, b, 0, b2.length);
                bb.position(0);
                Util.prt("  ** Blk310 " + bb.getShort() + " off=" + bb.getShort()
                        + " beg time=" + bb.getShort() + " "
                        + bb.getShort() + ":" + bb.get() + ":" + bb.get() + ":" + bb.get() + (bb.get() == 0 ? "" : "") + "." + bb.getShort()
                        + " reserve=" + bb.get() + " cal flags=" + Util.toHex(bb.get()) + " duration=" + bb.getInt()
                        + " period=" + bb.getFloat() + " cal amp=" + bb.getFloat());

                bb.get(str, 0, 3);
                Util.prt("  ** Chan=" + new String(str, 0, 3) + " reserv=" + bb.get() + " ref amp=" + bb.getInt());
                bb.get(str, 0, 12);
                Util.prt("  ** coupling=" + new String(str, 0, 12));
                bb.get(str, 0, 12);
                Util.prt("  ** roll off=" + new String(str, 0, 12));

              }
              // blockett 320
              b2 = ms.getBlockette320();
              if (b2 != null) {
                System.arraycopy(b2, 0, b, 0, b2.length);
                bb.position(0);
                Util.prt("vBlk320 " + bb.getShort() + " off=" + bb.getShort()
                        + " beg time=" + bb.getShort() + " "
                        + bb.getShort() + ":" + bb.get() + ":" + bb.get() + ":" + bb.get() + (bb.get() == 0 ? "" : "") + "." + bb.getShort()
                        + " reserved=" + bb.get() + " cal flags=" + Util.toHex(bb.get()) + " duration=" + bb.getInt()
                        + " PP amp=" + bb.getFloat());
                bb.get(str, 0, 3);
                Util.prt("  ** Chan=" + new String(str, 0, 3) + " reserv=" + bb.get() + " ref amp=" + bb.getInt());
                bb.get(str, 0, 12);
                Util.prt("  ** coupling=" + new String(str, 0, 12));
                bb.get(str, 0, 12);
                Util.prt("  ** roll off=" + new String(str, 0, 12));
                bb.get(str, 0, 8);
                Util.prt("  ** noise type=" + new String(str, 0, 8));

              }
              // blockett 320
              b2 = ms.getBlockette395();
              if (b2 != null) {
                System.arraycopy(b2, 0, b, 0, b2.length);
                bb.position(0);
                Util.prt("  ** Blk395 " + bb.getShort() + " off=" + bb.getShort()
                        + " end time=" + bb.getShort() + " "
                        + bb.getShort() + ":" + bb.get() + ":" + bb.get() + ":" + bb.get() + (bb.get() == 0 ? "" : "") + "." + bb.getShort());
              }
            }
            char band = ms.getSeedNameString().charAt(8);
            char rateC = ms.getSeedNameString().charAt(7);
            boolean timeseries = true;
            if (band != 'H' && band != 'N' && band != 'H' && band != 'L') {
              timeseries = false;
            }
            if (rateC != 'V' && rateC != 'B' && rateC != 'H' && rateC != 'L' && rateC != 'S'
                    && rateC != 'E' && rateC != 'C' && rateC != 'D') {
              timeseries = false;
            }
            if (ms.getEncoding() == 11 || ms.getEncoding() == 10 || ms.getEncoding() == 30
                    || ms.getEncoding() == 3 || ms.getEncoding() == 4 || ms.getEncoding() == 5) {
              try {
                if (ms.getBlockSize() == 512) {
                  frames = frame512;
                } else {
                  frames = frame4096;
                }
                if (frames == null || frames.length < buf.length - 64) {
                  frames = new byte[buf.length - 64];
                }
                if (df8 == null) {
                  df8 = new DecimalFormat("00000000;");
                }
                if (ms.getDataOffset() != 0) {
                  System.arraycopy(buf, ms.getDataOffset(), frames, 0, buf.length - ms.getDataOffset());
                  int[] samples = null;
                  int encoding = ms.getEncoding();
                  if (encoding >= 1 && encoding <= 5) {
                    samples = new int[ms.getNsamp()];
                    bb.position(ms.getDataOffset());
                    System.arraycopy(ms.getBuf(), 0, b, 0, ms.getBlockSize());
                    for (int ii = 0; ii < ms.getNsamp(); ii++) {
                      switch (encoding) {
                        case 5:
                          double d = bb.getDouble();
                          Util.prt(ii + " d=" + d);
                          samples[ii] = (int) (d * fscale + 0.5);
                          break;
                        case 1:
                          samples[ii] = bb.getShort();
                          break;
                        case 2:
                          int i2 = bb.getInt();
                          bb.position(bb.position() - 1);
                          i2 = i2 & 0xffffff;
                          if ((i2 & 0x800000) != 0) {
                            i2 = i2 | 0xff000000;
                          } samples[ii] = i2;
                          break;
                        case 3:
                          samples[ii] = bb.getInt();
                          break;
                        case 4:
                          samples[ii] = (int) (bb.getFloat() * fscale + 0.5);
                          break;
                        default:
                          break;
                      }
                    }
                  } else if (ms.getEncoding() == 30) {
                    samples = new int[ms.getNsamp()];
                    ByteBuffer bbuf = ByteBuffer.wrap(buf);
                    bb.position(ms.getDataOffset());
                    System.arraycopy(ms.getBuf(), 0, buf, 0, ms.getBlockSize());
                    for (int ii = 0; ii < ms.getNsamp(); ii++) {
                      int word = ((int) bbuf.getShort());
                      int mantissa = word & 0xfff;
                      if ((mantissa & 0x800) == 0x800) {
                        mantissa = mantissa | 0xFFFFF000;  // Do sign
                      }
                      int exp = (word >> 12) & 0xf;
                      //Util.prt(ii+" m="+mantissa+" exp="+exp+" exp2="+(-exp + 10)+" val="+(mantissa << (-exp +10)));
                      exp = -exp + 10;                   // The sign is apparently biased by 10
                      if (exp > 0) {
                        samples[ii] = mantissa << exp;
                      } else if (exp < 0) {
                        Util.prt("Illegal exponent in SRO exp=" + exp);
                        samples[ii] = mantissa >> exp;
                      } else {
                        samples[ii] = mantissa;
                      }
                    }
                  } else if (ms.getEncoding() == 11) {
                    if (steim2.decode(frames, ms.getNsamp(), ms.isSwapBytes())) {
                      samples = steim2.getSamples();
                    } else {
                      samples = steim2.getSamples();
                      if (steim2.hadReverseError()) {
                        Util.prt("    ** " + steim2.getReverseError() + " " + ms);
                      }
                      if (steim2.hadSampleCountError()) {
                        Util.prt("    ** " + steim2.getSampleCountError() + " " + ms);
                      }
                    }
                  } else if (ms.getEncoding() == 10) {
                    samples = Steim1.decode(frames, ms.getNsamp(), ms.isSwapBytes());
                    if (Steim1.getRIC() != samples[ms.getNsamp() - 1]) {
                      Util.prt("    ** Steim1 RIC error RIC=" + Steim1.getRIC() + "!=" + samples[ms.getNsamp() - 1]);
                    }
                  }
                  int nrange = 0;
                  int min = 2147000000;
                  int max = -2147000000;

                  for (int j = 0; j < ms.getNsamp(); j++) {
                    if (Math.abs(samples[j]) > 4000000) {
                      nrange++;
                    }
                    if (samples[j] < min) {
                      min = samples[j];
                    }
                    if (samples[j] > max) {
                      max = samples[j];
                    }
                  }
                  if (nrange > 0 && timeseries) {
                    totoor += nrange;
                    Util.prt(ms.toString() + "\n   *** # Sample out of range=" + nrange + " " + ms.getSeedNameString());
                  }
                  if (!errorsOnly && decode) {
                    StringBuilder sb = new StringBuilder(10000);
                    if (samples != null) {
                      for (int j = 0; j < Math.min(samples.length, 4); j++) {
                        sb.append((samples[j] + "       ").substring(0, 8)).append(" ");
                      }
                      sb.append(" ... ");
                      for (int j = Math.max(0, ms.getNsamp() - 4); j < ms.getNsamp(); j++) {
                        sb.append((samples[j] + "       ").substring(0, 8)).append(" ");
                      }
                    }
                    sb.append(ms.getEncoding() == 11 ? " RIC=" + steim2.getRIC() + " " : ms.getEncoding() == 10 ? "RIC=" + Steim1.getRIC() + " " : "").
                            append("min=").append(min).append(" max=").append(max).append(" diff=").append(max - min);
                    if (max - min > 1000000) {
                      sb.append(" %%%% Large  ").append(ms.getSeedNameString());
                    }
                    /*for(int j=-; j<ms.getNsamp(); j++) {
                      sb.append((samples[j]+"       ").substring(0,8)+" ");
                      if(j % 8 == 7) sb.append("\n");
                    }*/
                    Util.prt(sb.toString());
                  }
                }
              } catch (SteimException e) {
                Util.prt("   *** steim2 err=" + e.getMessage() + " iblk=" + iblk + " ms=" + ms.toString());
                nsteim++;
              }
            }
            time = ms.getGregorianCalendar();

            // if this is a trigger or other record it will have no samples (but a time!) so
            // do not use it for discontinuity decisions
            if (ms.getNsamp() > 0) {
              nsamp += ms.getNsamp();
            }
            if (ms.getNsamp() > 0 && lastComp.equals(ms.getSeedNameString())) {
              rate = ms.getRate();

              if (last != null) {
                diff = time.getTimeInMillis() - last.getTimeInMillis()
                        - lastlength;
                if (Math.abs(diff) > 1) {
                  ngap++;
                  if (errorsOnly) {
                    Util.prt(lastString);
                    Util.prt(ms.toString() + " iblk=" + iblk + "/" + ms.getBlockSize());
                  }
                  Util.prt("   * discontinuity = " + diff + " ms ends at " + ms.getTimeString()
                          + " " + ms.getSeedNameString());
                }
                if (diff <= -1) {
                  totrev += diff;
                } else {
                  totgap += diff;
                }
              }
              lastString = ms.toString() + " iblk=" + iblk + "/" + ms.getBlockSize();
            }
            last = time;
            lastComp = ms.getSeedNameString();
            double r = ms.getRate();
            if (ms.getB100Rate() > 0.) {
              r = ms.getB100Rate();
            }

            lastlength = (long) (ms.getNsamp() * 1000. / r + 0.5);

            // Print out the console long if any is received.
            if (ms.getSeedNameString().substring(7, 10).equals("LOG") && ms.getNsamp() > 0 && !errorsOnly) {
              Util.prt("data=" + new String(ms.getData(ms.getNsamp())));
            }

            // if reported Blck 1000 length is zero, use the last known block len
            if (ms.getBlockSize() <= 0) {
              iblk += blocklen / 512;
            } else {
              iblk += Math.max(1, ms.getBlockSize() / 512);
              blocklen = ms.getBlockSize();
            }
          } catch (IllegalSeednameException e) {
            if (buf[0] == 0 && buf[1] == 0 && buf[6] == 0 && (buf[8] == 0 || buf[8] == 32)) {
              if (nzero % 100 == 0) {
                Util.prt(" *** Zero block.  Skip. end iblk=" + iblk);
              }
              nzero++;
              iblk += Math.max(1, blockSize / 512);
            } else {
              Util.prt(" buf[0]=" + buf[0] + " " + buf[1] + " " + buf[6] + " " + buf[8]);
              Util.prt(" *** Illegal seedname found iblk=" + iblk + " " + e.getMessage());
              e.printStackTrace();
              iblk += Math.max(1, blockSize / 512);
            }
          } catch (RuntimeException e) {
            Util.prt(" *** Runtime Exception iblk=" + iblk + " e=" + e);
            e.printStackTrace();
            iblk += Math.max(1, blockSize / 512);
          }
        }       // end of read new header for(;;)
      } catch (IOException e) {
        if (e.getMessage() != null) {
          Util.prt(" *** IOException " + e.getMessage());
        } else {
          String pct = "" + (totgap / (nsamp / rate * 1000 + totgap) * 100.);
          Util.prt("  " + args[i] + " #blks=" + iblk + " #samp=" + nsamp + " #gap=" + ngap + (ngap > 10 ? "! " : " ")
                  + "totgap=" + (totgap / 1000.) + (totgap > 1000000 ? "! " : " ")
                  + "totrev=" + (totrev / 1000.) + (totrev < -1000000 ? "! " : " ")
                  + "#steim=" + nsteim + (nsteim > 0 ? "! " : " ")
                  + "#OOR=" + totoor + (totoor > 0 ? "! " : " ")
                  + "tot time=" + (nsamp / rate * 1000 + totgap) / 1000. + " "
                  + pct.substring(0, Math.min(5, pct.length())) + "%");
        }
        try {
          if (rw != null) {
            rw.close();
          }
        } catch (IOException e2) {
        }

      }
    }
  }

  private String dumpBlock(byte[] b, int l) {
    StringBuilder sb = new StringBuilder(l * 5);
    for (int i = 0; i < l; i = i + 32) {
      sb.append(Util.leftPad("" + i, 5)).append(":");
      for (int j = i; j < i + 32; j++) {
        sb.append(Util.leftPad("" + b[j], 4));
      }
      sb.append("\n");
    }
    return sb.toString();
  }

  /**
   * This will look at fetch list records specified
   */
  private int doFetchList(String[] args) {
    int nfill = 0;
    try {
      Util.setProperty("cwbip", "gcwb");    // force setting of cwbip
      Util.prt("cwbip from prop=" + Util.getProperty("cwbip"));
      edgeIP = "gacq1";
      edgeIP = Util.getProperty("edgeip");
      edgePort = 7205;
      edgePort = Integer.parseInt(Util.getProperty("edgeport"));
      edgeCutoff = 10;
      String fetchArgs = "";
      edgeCutoff = Integer.parseInt(Util.getProperty("edgecutoff"));
      for (int i = 0; i < args.length; i++) {
        if (args[i].equalsIgnoreCase("-fetch")) {
          fetchArgs = args[i + 1];  // yyyy,doy,dur
        }
        if (args[i].length() >= 5) {
          if (args[i].substring(0, 5).equalsIgnoreCase("-edge")) {
            String[] a = args[i].split(":");
            switch (a.length) {
              case 4:
                edgeIP = a[1];
                edgePort = Integer.parseInt(a[2]);
                edgeCutoff = Integer.parseInt(a[3]);
                if (edgeCutoff <= 0) {
                  edgeCutoff = 10;
                } break;
              case 1:
                break;
              default:
                Util.prt("-edge mode must have all 3 arguments -edge:nnn.nnn.nnn.nnn:port:cutoff");
                System.exit(0);
            }
          }
        }
      }
      dbconn = DBConnectionThread.getThread("msreadEdge");
      if (dbconn == null) {
        try {
          dbconn = new DBConnectionThread(Util.getProperty("DBServer"), "update", "edge", false, false, "msreadEdge", Util.getOutput());
          while (!DBConnectionThread.waitForConnection("msreadEdge")) {
            Util.prta("Waiting for msreadEdge connection");
          }
        } catch (InstantiationException e) {
          Util.prt("Cannot connect to database Instantiation exception e=" + e.getMessage());
          System.exit(0);
        }
      }
      String[] parts = fetchArgs.split(",");
      if (parts.length != 3) {
        Util.prt("Attempt to run fetch without adequate args.  Args must be YYYY,DOY,DUR");
        System.exit(0);
      }
      int year = Integer.parseInt(parts[0]);
      int doy = Integer.parseInt(parts[1]);
      int dur = Integer.parseInt(parts[2]);
      Util.prt("doFetchlist for " + year + "," + doy + " dur=" + dur + " edge=" + edgeIP + "/" + edgePort + ":" + edgeCutoff + " cwb=" + Util.getProperty("cwbip"));

      int[] ymd = SeedUtil.ymd_from_doy(year, doy);
      GregorianCalendar g = new GregorianCalendar(ymd[0], ymd[1] - 1, ymd[2]);
      Timestamp start2 = new Timestamp(g.getTimeInMillis());
      String st = start2.toString().substring(0, 10);
      start2.setTime(start2.getTime() + dur * 86400000L);
      String en = start2.toString().substring(0, 10);
      ResultSet rs = dbconn.executeQuery("SELECT * FROM fetcher.fetchlist WHERE start>='" + st + "' AND start<='" + en
              + "' AND status!='completed' ORDER BY seedname,start");
      Statement update = dbconn.getConnection().createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE);
      while (rs.next()) {
        if ((rs.getString("type") + "X").substring(0, 1).equals("X")) {
          continue;
        }
        if ((rs.getString("type") + "  ").substring(1, 2).equals("L")) {
          continue;
        }
        String s = "-hp -s " + (rs.getString("seedname") + "     ").substring(0, 12).replaceAll(" ", "-").replaceAll("\\?", ".") + " -d " + rs.getDouble("duration")
                + " -b " + rs.getTimestamp("start").toString().substring(0, 19).replaceAll("-", "/").replaceAll(" ", "-") + "."
                + Util.df3(rs.getInt("start_ms")) + " -t null";
        Util.prt(s);
        long startMillis = rs.getTimestamp("start").getTime() + rs.getInt("start_ms");
        ArrayList<Run> runs = new ArrayList<Run>();
        ArrayList<ArrayList<MiniSeed>> mss = null;
        //DEBUG mss = EdgeQueryClient.query(s);
        if (mss == null) {
          Util.prta("Null returned");
          continue;
        } // Nothing returned
        else if (mss.isEmpty()) {
          Util.prta("No blocks returned");
          continue;
        } else if (mss.get(0).isEmpty()) {
          Util.prta("No blocks returned");
          continue;
        }
        try (RawDisk rw = new RawDisk("tmp.ms", "rw")) {
          int iblk = 0;
          double rate = 0;
          for (int j = 0; j < mss.size(); j++) {
            for (int i = 0; i < mss.get(j).size(); i++) {
              MiniSeed ms = mss.get(j).get(i);
              int nb = ms.getBlockSize();
              rw.writeBlock(iblk, ms.getBuf(), 0, nb);
              iblk += nb / 512;
              Util.prt(j + " " + i + " " + ms.toString());
              if (rate == 0.) {
                rate = ms.getRate();
              }
              boolean done = false;
              for (gov.usgs.anss.msread.Run run : runs) {
                if (run.add(ms)) {
                  done = true;
                  break;
                }
              }
              if (!done) {
                Run r = new Run(ms);
                runs.add(r);
              }
            }
          }
          for (int i = 0; i < runs.size(); i++) {
            Util.prt(i + " " + runs.get(i));
            if (runs.get(i).getStart().getTimeInMillis() <= startMillis + (long) (1. / rate * 1000.)
                    && runs.get(i).getEnd().getTimeInMillis() >= startMillis + (long) (rs.getDouble("duration") * 1000.)) {
              Util.prt("  ** " + runs.get(i) + " spans the gap " + st + " " + rs.getDouble("duration"));
              String[] eargs = new String[5];
              eargs[0] = "-allowrecent";
              eargs[1] = "-ignoreHoldings";
              eargs[2] = "-cwbip";
              eargs[3] = Util.getProperty("cwbip");
              eargs[4] = "tmp.ms";
              doEdgeMode(4, eargs);
              update.executeUpdate("UPDATE fetcher.fetchlist set status='completed',updated=now() WHERE ID=" + rs.getInt("ID"));
              nfill++;
            }
          }
        }
      }
    } catch (FileNotFoundException e) {
      Util.prta("Could not open output file ");
      System.exit(0);
    } catch (SQLException e) {
      Util.SQLErrorPrint(e, "SQL trying to get fetchlist");
    } catch (IOException e) {
      Util.prta("Got IOException writing temp file");
      e.printStackTrace();
      System.exit(0);
    } catch (RuntimeException e) {
      Util.prta("Got runtime exception during doFetchList() " + e);
      e.printStackTrace();
      System.exit(0);
    }
    return nfill;
  }

  private void doEdgeMode(int start, String[] args) {
    // To get here we are in "rawMode
    int iblk;
    int nblks;
    int ncwb;
    byte[] header = new byte[40];
    ByteBuffer hdr = ByteBuffer.wrap(header);
    RawDisk rw;
    MiniSeed ms = null;
    String forceStation = null;
    String forceLocation = null;
    boolean allowrecent = false;
    String forceNetwork = null;
    String forceComponent = null;
    boolean ignoreHoldings = false;
    boolean ignoreBogus = false;
    boolean sanityCheck = false;
    long totalElapse = System.currentTimeMillis();
    //Util.prtProperties();
    String cwbIP = Util.getProperty("cwbip");
    int sleepMS = 50;
    int cwbport = Integer.parseInt(Util.getProperty("cwbport"));
    int encoding = 11;
    int blklen = 512;
    boolean createB1000 = false;
    String excludeRE = null;
    byte[] defaultLocation = null;
    boolean noCheck = false;
    long earliestTime = 0;
    long latestTime = Long.MAX_VALUE;
    boolean add1024 = false;
    long timeOffsetMS = Long.MIN_VALUE;
    GregorianCalendar gcalc = new GregorianCalendar();
    boolean nooutput = false;
    int dummy = 0;
    String email = null;
    for (int i = 0; i < args.length; i++) {
      if (args[i].equalsIgnoreCase("-fs")) {
        forceStation = (args[i + 1].toUpperCase() + "     ").substring(0, 5).replaceAll("-", " ");
        i++;
      } else if (args[i].equalsIgnoreCase("-fl")) {
        forceLocation = (args[i + 1].toUpperCase() + "     ").substring(0, 2).replaceAll("-", " ");
        i++;
      } else if (args[i].equalsIgnoreCase("-fn")) {
        forceNetwork = (args[i + 1].toUpperCase() + "     ").substring(0, 2).replaceAll("-", " ");
        i++;
      } else if (args[i].equalsIgnoreCase("-fc")) {
        forceComponent = (args[i + 1].toUpperCase() + "     ").substring(0, 3).replaceAll("-", " ");
        i++;
      } else if (args[i].equalsIgnoreCase("-dlc")) {
        defaultLocation = (args[i + 1].toUpperCase() + "  ").substring(0, 2).replaceAll("-", " ").getBytes();
        i++;
      } else if (args[i].equalsIgnoreCase("-err")) {
        dummy = 1;
      } else if (args[i].equalsIgnoreCase("-allowrecent")) {
        allowrecent = true;
      } else if (args[i].equalsIgnoreCase("-ignoreholdings")) {
        ignoreHoldings = true;
      } else if (args[i].equalsIgnoreCase("-cwbip")) {
        cwbIP = args[i + 1];
        i++;
      } else if (args[i].equalsIgnoreCase("-cwbport")) {
        cwbport = Integer.parseInt(args[i + 1]);
        i++;
      } else if (args[i].equalsIgnoreCase("-sleepms")) {
        sleepMS = Integer.parseInt(args[i + 1]);
        i++;
      } else if (args[i].equalsIgnoreCase("-ignoreBogus")) {
        ignoreBogus = true;
      } else if (args[i].equalsIgnoreCase("-sanity")) {
        sanityCheck = true;
      } else if (args[i].equalsIgnoreCase("-sanitynets")) {
        sanityCheck = true;
        sanityNets = args[i + 1];
        i++;
      } else if (args[i].equalsIgnoreCase("-mysql")) {
        Util.setProperty("MySQLServer", args[i + 1]);
        i++;
      } else if (args[i].equalsIgnoreCase("-db")) {
        Util.setProperty("DBServer", args[i + 1]);
        Util.setProperty("StatusDBServer", args[i + 1]);
        i++;
      } else if (args[i].equalsIgnoreCase("-x")) {
        excludeRE = args[i + 1];
      } else if (args[i].equalsIgnoreCase("-email")) {
        email = args[i + 1];
        i++;
      } else if (args[i].equalsIgnoreCase("-nochk")) {
        noCheck = true;
      } else if (args[i].equalsIgnoreCase("-add1024")) {
        add1024 = true;
      } else if (args[i].equalsIgnoreCase("-timeoffsetms")) {
        timeOffsetMS = Long.parseLong(args[i + 1]);
      } else if (args[i].equalsIgnoreCase("-nooutput")) {
        nooutput = true;
      } else if (args[i].equals("-first")) {
        java.util.Date t = Util.stringToDate2(args[i + 1]);
        earliestTime = t.getTime();
        i++;
      } else if (args[i].equalsIgnoreCase("-last")) {
        java.util.Date t = Util.stringToDate2(args[i + 1]);
        latestTime = t.getTime();
        i++;
      } else if (args[i].equalsIgnoreCase("-prop")) { 
        Util.prt("Loading prop file2 : "+args[i+1]);
        Util.loadProperties(args[i+1]);
        i++;
      } else if (args[i].indexOf("-setB1000") == 0) {
        createB1000 = true;
        String[] bits = args[i].split(":");
        if (bits[1].equals("1") || bits[i].equals("2") || bits[i].equals("")) {
          encoding = 0;
          if (bits[1].equals("1")) {
            encoding = 10;
          }
          if (bits[1].equals("2")) {
            encoding = 11;
          }
        }
        blklen = Integer.parseInt(bits[2]);
      } else if (args[i].substring(0, Math.min(5, args[i].length())).equalsIgnoreCase("-edge")) {

      } else if (args[i].contains(".") && i >= start) {
        dummy = 2;
      } else {
        Util.prt("*** Unknown option (edge) : " + args[i] + " at " + i + " start=" + start);
      }
    }
    //Util.prtProperties();
    Util.prta(" recent=" + allowrecent + " ignoreHoldings=" + ignoreHoldings + " "
            + " sleep=" + sleepMS + " "
            + (forceStation != null ? " fstat=" + forceStation : "")
            + (forceNetwork != null ? " fchn=" + forceComponent : "")
            + (forceNetwork != null ? " floc=" + forceLocation : "")
            + (excludeRE != null ? " exclRE=" + excludeRE : ""));
    if (cwbIP.equals("cwbrs")) {
      Util.setProperty("DBServer", cwbIP + "/3306:edge:mysql:edge");
      Util.setProperty("StatusDBServer", cwbIP + "/3306:edge:mysql:edge");
      Util.setProperty("MySQLServer", cwbIP);
    }
    if (cwbIP.equals("gldpwb")) {
      Util.setProperty("DBServer", cwbIP + "/3306:edge:mysql:edge");
      Util.setProperty("StatusDBServer", cwbIP + "/3306:edge:mysql:edge");
      Util.setProperty("MySQLServer", cwbIP);
    }
    dbconn = DBConnectionThread.getThread("msreadHoldings");
    while (dbconn == null && !ignoreHoldings) {
      try {
        dbconn = new DBConnectionThread(Util.getProperty("StatusDBServer"), "readonly", "status", false, false, "msreadHoldings", Util.getOutput());
        while (!DBConnectionThread.waitForConnection("msreadHoldings")) {
          Util.prta("Waiting for msreadHoldings connectionB");
        }
      } catch (InstantiationException e) {
        Util.prt("");
        Util.prt("");
        Util.prt("  ***** Need to setup readonly access to holdings on " + Util.getProperty("StatusDBServer") + "!");
        Util.prt("Enter username : ");
        try {
          BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
          String u = in.readLine();
          Util.prt("Enter password :");
          String p = in.readLine();
          DBSetup.quickMake(Util.getProperty("StatusDBServer"), "user:" + u + "=" + p);
        } catch (IOException e2) {
          //try{
          Util.prt("IOError trying to open database e=" + e);
          e.printStackTrace();
          //dbconn = new DBConnectionThread(Util.getProperty("StatusDBServer"),"readonly","status", false,false,"msreadHoldings", Util.getOutput());
          //while(!DBConnectionThread.waitForConnection("msreadHoldings")) Util.prta("Waiting for msreadHoldings connectionB");
          //}
          //catch(InstantiationException e3) {
          // Util.prt("****** Cannot connect to database Instantiation exception e="+e3.getMessage());
          // System.exit(0);
          //}
        }
      }
    }
    try {
      Socket s = null;
      Socket scwb = null;
      int nfails = 0;
      OutputStream out = null;
      String systemName = Util.getSystemName().toLowerCase();
      while (scwb == null) {
        try {
          if (allowrecent) {
            Util.prt("Open edge at " + edgeIP + "/" + edgePort);
            s = new Socket(edgeIP, edgePort);
            out = s.getOutputStream();
          }
          if (risMode) {
            cwbport = Integer.parseInt(Util.getProperty("risport"));
          }
          Util.prt("Open cwb at " + cwbIP + "/" + cwbport + " sleepms=" + sleepMS);
          scwb = new Socket(cwbIP, cwbport); // DEBUG: should be 2062
        } catch (IOException e) {
          nfails++;
          Util.prt("* cannot open a socket to send data! nfail=" + nfails + " e=" + e.getMessage());
          if (nfails == 20) {
            Util.prt("  ***** cannot open a socket to cwb or edge ip");
            System.exit(2);
          }
          try {
            sleep(30000);
          } catch (InterruptedException e2) {
          }
          if (scwb != null) {
            try {
              scwb.close();
            } catch (IOException e2) {
            }
          }
          if (s != null) {
            try {
              s.close();
            } catch (IOException e2) {
            }
          }

        }
      }
      OutputStream outCWB = scwb.getOutputStream();
      int lastBlocksize = 4096;
      //ArrayList<MiniSeed> mslist = new ArrayList<MiniSeed>(9);
      byte[] frame512 = new byte[512];
      byte[] frame4096 = new byte[4096];
      byte[] frames;
      int[] samples = new int[10000];
      byte[] databuf = null;
      ByteBuffer data = null;
      if (risMode) {
        databuf = new byte[40000];
        data = ByteBuffer.wrap(databuf);
      }
      int today = SeedUtil.toJulian(new GregorianCalendar());
      boolean zero = false;
      HoldingArray ha = null;           // place to put holdings
      int nredundant;
      int inrow = 0;
      long length;
      ArrayList<MiniSeed> blocks = new ArrayList<MiniSeed>(100000);
      GregorianCalendar channelStart = new GregorianCalendar();
      GregorianCalendar channelEnd = new GregorianCalendar();
      String lastSeedname = "";

      // variables for tracking entworks not in the Sanity checks
      int nfailSanity = 0;
      int[] nfailedBlock = new int[1000];
      StringBuilder[] failedStation = new StringBuilder[1000];
      long[] earliest = new long[1000];
      long[] latest = new long[1000];
      StringBuilder failStation = new StringBuilder(7);   // place to put network of blocks
      int nfailedIndex = 0;
      int ncwbchan = 0;
      int nredundantchan = 0;
      int nlivechan = 0;
      StringBuilder mail = new StringBuilder(10000);
      StringBuilder mailhdr = new StringBuilder(1000);
      //mail.append("This is a testin message to force output always!\n");
      int expectedBlockSize;
      MiniSeedPool msp = new MiniSeedPool();
      Timestamp st = new Timestamp(100000L);
      Timestamp en = new Timestamp(100000L);
      buf = new byte[4096];
      for (int i = start; i < args.length; i++) {
        lastSeedname = "";
        last = null;
        boolean inOrder = true;   // tract whether all blocks for this channel are in order
        long lastTime = 0;        // time of last block from this channel
        String lastChan = "";
        try {
          rw = new RawDisk(args[i], "r");
        } catch (FileNotFoundException e) {
          Util.prt("IOError opening file=" + args[i] + " skip...");
          continue;
        }
        length = rw.length();
        iblk = startblock;
        nblks = 0;
        ncwb = 0;
        nredundant = 0;
        boolean eof = false;
        Util.prta(i + " Open file : " + args[i] + " start block=" + iblk
                + " end=" + (length / 512L) + " allow recent=" + allowrecent + " sleepms=" + sleepMS + " nooutput=" + nooutput);
        if (length == 0) {
          continue;     // no data
        }        // This loops until the file is completed, as the file is processed every 100,000 blocks.
        while (!eof) {
          try {
            blocks.clear();
            if (msp.getUsedList().size() > 0) {
              Util.prt("Not all MSP blocks were freed!!  msp=" + msp);
              for (int ii = 0; ii < msp.getUsedList().size(); ii++) {
                Util.prt(ii + " *** " + msp.getUsedList().get(ii));
              }
            }
            msp.freeAll();

            for (;;) {
              rw.readBlock(buf, iblk, 512);
              expectedBlockSize = 1;
              if (iblk == 0) {  // See if this is a SEED volume and get block length if it is
                //Util.prt("6="+buf[6]+" 7="+buf[7]+" 17="+buf[17]);
                if (buf[6] == 86 && buf[7] == ' ' && buf[17] == '.') { // this is a seed volume, set iblk to begin of data
                  int power = (buf[19] - '0') * 10 + (buf[20] - '0');
                  int fixBlockSize = 1 << power;
                  int nblk = fixBlockSize / 512;
                  buf = new byte[fixBlockSize];             // if there are no blockette 1000 then this size governs!
                  while (buf[6] != 'D' && buf[6] != 'Q' && buf[6] != 'R' && buf[6] != 'M') {
                    iblk = iblk + nblk;
                    rw.readBlock(buf, iblk, buf.length);
                  }
                  Util.prt("This is a seed volume data starts at iblk=" + iblk);
                }
                if (iblk == 0) {
                  if (MiniSeed.isQ680Header(buf)) {
                    Util.prt("Q680 hdr " + new String(buf, 21, 30) + " skip");
                    iblk = 1;
                    rw.readBlock(buf, iblk, 512);
                  }
                }
              }
              if (buf[0] == 0 && buf[1] == 0 && buf[2] == 0) {
                Util.prta("  **** all zero block at iblk=" + iblk + " skip....");
                iblk++;
                continue;
              }
              try {
                MiniSeed.simpleFixes(buf);
              } catch (IllegalSeednameException e) {
                Util.prta(" IllegalSeedname/form in simpleFixes.  Skip iblk=" + iblk + " e=" + e);
                iblk++;
                continue;
              }
              //if(iblk % 100000 == 1) Util.prta((iblk-1)+" done");
              zero = true;
              //mslist.clear();
              // The request data from the Q680 is really odd, it contains no network code in Log
              // records (both are zero).  We need to detect this before trying to decode it!
              if (buf[15] == 'L' && buf[16] == 'O' && buf[17] == 'G') {
                iblk += lastBlocksize / 512;
                continue;
              }
              if (buf[0] == 0 && buf[1] == 0 && buf[2] == 0) {
                inrow++;
                if (inrow > 2000) {
                  Util.prt("    ***** All zero for 2000+ blocks.  EOF.  move on iblk=" + iblk);
                  break;
                }
                iblk++;
                continue;
              } else {
                inrow = 0;
              }
              //Util.prt("read bloc len="+buf.length+" iblk="+iblk);

              // Bust the first 512 bytes to get all the header and data blockettes - data
              // may not be complete!  If the block length is longer, read it in complete and
              // bust it appart again.
              try {
                if (ms == null) {
                  ms = new MiniSeed(buf);
                } else {
                  ms.load(buf);
                }
                if (ms.hasBlk1000()) {
                  lastBlocksize = ms.getBlockSize();
                  if (ms.getBlockSize() > 512) {
                    int l = rw.readBlock(buf, iblk, ms.getBlockSize());
                    MiniSeed.simpleFixes(buf);
                    if (ms.getBuf().length < buf.length) {
                      ms = new MiniSeed(buf); // make a bigger ms record
                    }
                    iblk += ms.getBlockSize() / 512 - 1;
                    expectedBlockSize = ms.getBlockSize() / 512;
                  }
                } else {
                  if (createB1000) {

                    buf = rw.readBlock(iblk, blklen);
                    iblk += blklen / 512 - 1;
                    lastBlocksize = blklen;
                    MiniSeed.simpleFixes(buf);
                    ms.load(buf);
                    int detectEncoding = 0;
                    if (encoding == 0) {
                      try {
                        if (blklen == 512) {
                          frames = frame512;
                        } else {
                          frames = frame4096;
                        }
                        System.arraycopy(buf, ms.getDataOffset(), frames, 0, blklen - ms.getDataOffset());

                        // try steim 2
                        samples = Steim2.decode(frames, ms.getNsamp(), samples, ms.isSwapBytes(), 0);
                        if (Steim2.hadReverseError() || Steim2.getSampleCountError().length() > 2) {// Reverse integration error, skip the block
                          Steim1.decode(frames, ms.getNsamp(), samples, ms.isSwapBytes(), 0);
                          detectEncoding = 10;
                        } else {
                          detectEncoding = 11;
                        }
                        Util.prt(" * Set BLk1000 deduces encoding=" + encoding);
                      } catch (SteimException e) {    //Steim error skip the block
                        Util.prt("   * steim2 err=" + e.getMessage() + " assume steim1. ms=" + ms.toString());
                        detectEncoding = 10;
                      } catch (RuntimeException e) {
                        detectEncoding = 10;
                        Util.prt("   **** steim 2 threw error. assume steim1 e=" + e);
                      }
                    }
                    buf = ms.getBuf();
                    boolean swap = MiniSeed.swapNeeded(buf);
                    for (int ii = 48; ii < 64; ii++) {
                      buf[ii] = 0;
                    }
                    ByteBuffer b = ByteBuffer.wrap(buf);
                    if (swap) {
                      b.order(ByteOrder.LITTLE_ENDIAN);
                    }
                    int blkbit;
                    int ii = blklen;
                    for (blkbit = 0; blkbit < 32; blkbit++) {
                      if (ii <= 0) {
                        break;
                      }
                      ii /= 2;
                    }
                    blkbit--;
                    b.position(39);
                    b.put((byte) 1);
                    b.position(46);
                    b.putShort((short) 48);
                    b.position(48);
                    b.putShort((short) 1000);
                    b.putShort((short) 0);    // no more blockettes
                    b.put((byte) (encoding == 0 ? detectEncoding : encoding));
                    b.put((byte) (swap ? 0 : 1));
                    b.put((byte) blkbit);
                    ms.load(buf);
                    //ms = new MiniSeed(buf);
                    Util.prt("AFt=" + ms);

                  } else {
                    Util.prt("  **** This input file does not have Blk1000 and creation of them was not specified.");
                    iblk += blklen / 512;
                    continue;
                  }
                }

                // If the user had a time correction in Millis, apply it to the block
                if (timeOffsetMS > Long.MIN_VALUE) {
                  gcalc.setTimeInMillis(ms.getTimeInMillis() + timeOffsetMS);
                  int hunds = ms.getUseconds() % 1000 / 100;
                  Util.prta("Reset time to " + Util.ascdatetime(gcalc) + "." + Util.df4(hunds) + " " + ms.toString().substring(0, 60));
                  ms.setTime(gcalc, hunds);
                  buf = ms.getBuf();
                }
                // The time correction for a 1024 week needs to be applied
                if (add1024) {
                  if (ms.getTimeInMillis() < (System.currentTimeMillis() - 1023 * 7 * 86400000L)) {
                    gcalc.setTimeInMillis((ms.getTimeInMillis() + 1024 * 7 * 86400000L) / 1000 * 1000);
                    int hunds = ms.getUseconds() % 1000 / 100;
                    Util.prta("Reset time to " + Util.ascdate(gcalc) + " " + Util.asctime(gcalc) + "." + hunds + " " + ms.toString().substring(0, 60));
                    ms.setTime(gcalc, hunds);
                    buf = ms.getBuf();
                  } else {
                    iblk += blklen / 512;
                    continue;
                  }
                }

                // If we need to change the network, station or location code do so here
                if (forceStation != null || forceLocation != null
                        || forceNetwork != null || defaultLocation != null || forceComponent != null) {
                  ByteBuffer bb = ByteBuffer.wrap(buf);
                  if (forceStation != null) {
                    bb.position(8);
                    bb.put(forceStation.getBytes());
                  }
                  if (forceLocation != null) {
                    bb.position(13);
                    bb.put(forceLocation.getBytes());
                  }
                  if (forceNetwork != null) {
                    bb.position(18);
                    bb.put(forceNetwork.getBytes());
                  }
                  if (forceComponent != null) {
                    bb.position(15);
                    bb.put(forceComponent.getBytes());
                  }
                  if (defaultLocation != null) {
                    bb.position(13);
                    if (bb.get() < '0' && bb.get() < '0') {
                      bb.position(13);
                      bb.put(defaultLocation);
                    }
                  }
                  ms.load(buf);
                  //ms = new MiniSeed(buf);     // replace it with the new one!
                }

                // check to see this is a supported format for insertions
                // if there is a default location and the location code is blank, set it
                MiniSeed ms2 = msp.get(buf, 0, ms.getBlockSize());
                boolean added = false;

                iblk++;
                if (blocks.size() > 0) {
                  if (ms2.isDuplicate(blocks.get(blocks.size() - 1))) {
                    Util.prt(" * Found duplicate block - discard " + ms2.toString().substring(0, 61));
                    msp.free(ms2);        // free it
                    continue;
                  }
                }
                // If its not legal encoding, do not send it
                if (!MiniSeed.isSupportedEncoding(ms.getEncoding(), fscale)) {
                  Util.prt("  ** unsupported encoding fscale=" + fscale + " encoding=" + ms.getEncoding() + " ms=" + ms);
                  msp.free(ms2);    // Free block
                  continue;
                }
                if (excludeRE != null) {
                  if (!ms2.getSeedNameString().matches(excludeRE)) {
                    added = true;
                    blocks.add(ms2);
                  } else {
                    Util.prt("Exclude " + ms2.toString());
                    msp.free(ms2);
                  }
                } else {
                  added = true;
                  blocks.add(ms2);
                }
                // check to see if blocks are in order
                if (added) {
                  if (lastChan.equals("")) {
                    lastChan = ms2.getSeedNameString();
                  }
                  if (!lastChan.equals(ms2.getSeedNameString())) {
                    inOrder = false;
                  }
                  if (ms2.getTimeInMillis() <= lastTime) {
                    //Util.prt("OOR : diff="+(ms2.getTimeInMillis()-lastTime)+" "+ms2);
                    inOrder = false;

                  }
                  lastTime = ms2.getTimeInMillis();
                }

                if (iblk % 200 / (blklen / 512) == 0 && sleepMS > 0) {
                  try {
                    Thread.sleep(sleepMS);
                  } catch (InterruptedException e) {
                  }
                }
                if (ignoreBogus && (ms.getNsamp() <= 0 || ms.getRate() <= 0.)) {
                  Util.prt("     **** IgnoreBogus : " + ms);
                  continue;
                }

              } catch (IllegalSeednameException e) {
                Util.prt("  ***Illegal seed name at blk=" + iblk + " blockSize=" + expectedBlockSize + " is " + e.getMessage());
                e.printStackTrace();
                iblk += Math.max(expectedBlockSize, 1);
              } catch (RuntimeException e) {
                Util.prt("   **** got runtime exception cracking miniseed - skip block");
                e.printStackTrace();
                iblk += Math.max(1, expectedBlockSize);
              }
              if (blocks.size() > 100000) {
                break;   // Process every 100000 blocks, then got back to while(!eof)
              }
            }
          } catch (EOFException e) { // If its an eof, then we can process and exit
            eof = true;             // Leave the !eof while loop when this is processed
            Util.prt("EOF found nblks=" + nblks + " blocks.size() =" + blocks.size());
            try {
              rw.close();
            } catch (IOException e2) {
            }
          } catch (IOException e) {
            Util.prt("*** IOError skip block=" + iblk + " e=" + e);
            e.printStackTrace();
            /*try {
              if(rw != null) rw.close();
            }
            catch(IOException e2) {}*/
            iblk++;
          }
          Util.prta("Read " + blocks.size() + " Miniseed blocks.  Check sort them....");
          if (!inOrder && !ignoreHoldings && !noCheck) {
            Collections.sort(blocks);
            Util.prta("Sort completed start load sleepms=" + sleepMS);
          } else {
            Util.prta("No sort In order=" + inOrder + " noCheck=" + noCheck + " ignoreHold=" + ignoreHoldings);
          }
          for (int ii = 0; ii < blocks.size(); ii++) {
            if (ii % 200 / (blklen / 512) == 0 && sleepMS > 0) {
              try {
                Thread.sleep(sleepMS);
              } catch (InterruptedException e) {
              }
            }
            MiniSeed ms3 = blocks.get(ii);
            //Util.prt(ii+" "+ms3.toString());
            if (sanityCheck) {
              String net = ms3.getSeedNameString().substring(0, 2);
              /*if( (System.currentTimeMillis() - ms3.getTimeInMillis()) > 86400000L*365 ||
                      !(net.equals("IU") || net.equals("GT") ||  net.equals("IW") || 
                      net.equals("US") || net.equals("CU") || net.equals("IC") ||  net.equals("NE") ||
                      (!systemName.contains("aslcwb") && net.equals("LB")) || (!systemName.contains("aslcwb") && net.equals("NQ")) || 
                      (!systemName.contains("aslcwb") && net.equals("GS")) || (!systemName.contains("aslcwb") && net.equals("II")))) {*/
              if ((System.currentTimeMillis() - ms3.getTimeInMillis()) > 86400000L * 365 || !sanityNets.contains(net)) {
                //Util.prt(" *** Fails Sanity check ms="+ms3);
                nfailSanity++;
                Util.clear(failStation);
                for (int jj = 0; jj < 7; jj++) {
                  failStation.append(ms3.getSeedNameSB().charAt(jj));
                }
                boolean found = false;
                for (int jj = 0; jj < nfailedIndex; jj++) {
                  if (Util.stringBuilderEqual(failedStation[jj], failStation)) {
                    nfailedBlock[jj]++;
                    if (earliest[jj] > ms3.getTimeInMillis()) {
                      earliest[jj] = ms3.getTimeInMillis();
                    }
                    if (latest[jj] < ms3.getNextExpectedTimeInMillis()) {
                      latest[jj] = ms3.getNextExpectedTimeInMillis();
                    }
                    found = true;
                    break;
                  }
                }
                if (!found) {
                  nfailedBlock[nfailedIndex] = 0;
                  failedStation[nfailedIndex] = new StringBuilder(7).append(failStation);
                  Util.prt("Add sanity failed station=" + failStation + " " + nfailedIndex);
                  earliest[nfailedIndex] = ms3.getTimeInMillis();
                  latest[nfailedIndex] = ms3.getNextExpectedTimeInMillis();
                  nfailedIndex++;
                  if (nfailedIndex >= 1000) {
                    nfailedIndex = 999;
                  }
                }
                msp.free(ms3);
                continue;
              }
            }
            char band = ms3.getSeedNameString().charAt(8);
            char rate = ms3.getSeedNameString().charAt(7);
            boolean timeseries = true;
            if (band != 'H' && band != 'N' && band != 'H' && band != 'L') {
              timeseries = false;
            }
            if (rate != 'V' && rate != 'B' && rate != 'H' && rate != 'L' && rate != 'S'
                    && rate != 'E' && rate != 'C' && rate != 'D') {
              timeseries = false;
            }
            if (timeseries && ms3.getNsamp() > 0 && ms3.getRate() > 0.00001 && !noCheck) {
              try {
                if (ms3.getBlockSize() == 512) {
                  frames = frame512;
                } else {
                  frames = frame4096;
                }

                System.arraycopy(ms3.getBuf(), ms3.getDataOffset(), frames, 0, ms3.getBlockSize() - ms3.getDataOffset());

                if (ms3.getEncoding() == 11) {
                  samples = Steim2.decode(frames, ms3.getNsamp(), samples, ms3.isSwapBytes(), 0);
                  if (Steim2.hadReverseError()) {// Reverse integration error, skip the block
                    Util.prt("    *** " + Steim2.getReverseError() + " ignore data. " + ms3.toString());
                    msp.free(ms3);
                    continue;
                  }
                  if (Steim2.getSampleCountError().length() > 2) {
                    Util.prt("    **** Sample count error=" + Steim2.getSampleCountError());
                    msp.free(ms3);
                    continue;
                  }
                } else if (ms3.getEncoding() == 10) {
                  Steim1.decode(frames, ms3.getNsamp(), samples, ms3.isSwapBytes(), 0);
                } else if (MiniSeed.isSupportedEncoding(ms3.getEncoding(), 0.)) {
                  samples = ms3.decomp(samples, fscale);
                } else {
                  Util.prt(" *** Unknown encoding =" + ms3.getEncoding());
                }
                //if(samples.length != ms3.getNsamp()) Util.prt(" ** Steim1 did not return correct number of samples");
                int min = 2147000000;
                int max = -2147000000;
                for (int j = 0; j < ms3.getNsamp(); j++) {
                  if (min > samples[j]) {
                    min = samples[j];
                  }
                  if (max < samples[j]) {
                    max = samples[j];
                  }
                }
                if (min < -8000000 || max > 8000000 || (max - min) > 2000000) {
                  Util.prt("   * Odd range on packet min=" + min + " max=" + max + " ms=" + ms3 + " isswap=" + ms3.isSwapBytes());
                }
                if (risMode) {
                  data.position(0);
                  for (int k = 0; k < ms3.getNsamp(); k++) {
                    data.putInt(samples[k]);
                  }
                }

              } catch (SteimException e) {    //Steim error skip the block
                Util.prt("   *** steim2 err=" + e.getMessage() + " ignore this block. ms=" + ms3.toString());
                e.printStackTrace();
                msp.free(ms3);
                continue;
              } // check the block against holdings
              catch (RuntimeException e) {
                Util.prt("  *** Steim2 throws runtime. ignore this block ms=" + ms3);
                msp.free(ms3);
                continue;
              }

              if (ha == null && !ignoreHoldings) {
                ha = new HoldingArray();
              }
              if (!ignoreHoldings && length > 20480 && System.currentTimeMillis() - ms3.getTimeInMillis() > 2 * 86400000L) {
                if (!ms3.getSeedNameString().equals(lastSeedname)) {
                  try {
                    if (!lastSeedname.equals("")) {
                      Util.prt(lastSeedname + " " + Util.ascdate(channelStart) + " " + Util.asctime2(channelStart) + " - "
                              + Util.ascdate(channelEnd) + " " + Util.asctime2(channelEnd)
                              + " #cwb=" + ncwbchan + " #redund=" + nredundantchan + " #live=" + nlivechan);
                      if (sanityCheck) {
                        mail.append(lastSeedname).append(" ").append(Util.ascdate(channelStart)).
                                append(" ").append(Util.asctime2(channelStart)).append(" - ").
                                append(Util.ascdate(channelEnd)).append(" ").append(Util.asctime2(channelEnd)).
                                append(" #cwb=").append(ncwbchan).append(" #redund=").append(nredundantchan).
                                append(" #live=").append(nlivechan).append("\n");
                      }
                    }
                    nredundantchan = 0;
                    nlivechan = 0;
                    ncwbchan = 0;
                    st.setTime(ms3.getTimeInMillis() - 86400000);
                    channelStart.setTimeInMillis(ms3.getTimeInMillis());
                    en.setTime(ms3.getTimeInMillis() + 86400000 * 11);
                    ha.clearNoWrite();
                    String type = "CW";
                    if (cwbIP.equals("136.177.30.235")) {
                      type = "CR";
                    }
                    if (cwbIP.equals("136.177.30.110") || cwbIP.contains("gldpwb")) {
                      type = "C.";
                    }
                    if (cwbIP.equals("localhost")) {
                      type = "C.";
                    }
                    String sql = "SELECT * FROM status.holdingshist2 WHERE seedname='" + ms3.getSeedNameString() + "' AND type regexp '" + type + "' "
                            + "AND not (ended <'" + st.toString().substring(0, 10) + "' OR start >'" + en.toString().substring(0, 10) + "') ORDER BY start";
                    Util.prt(sql);
                    try {
                      try (ResultSet rs = dbconn.executeQuery(sql)) {
                        while (rs.next()) {
                          ha.addEnd(rs);
                        }
                      }
                    } catch (SQLException e) {
                    }    // Ignore problems getting holdings from hist2
                    sql = sql.replaceAll("hist2", "hist");
                    ResultSet rs = dbconn.executeQuery(sql);
                    while (rs.next()) {
                      ha.addEnd(rs);
                    }
                    rs.close();
                    sql = sql.replaceAll("hist", "");
                    rs = dbconn.executeQuery(sql);
                    while (rs.next()) {
                      ha.addEnd(rs);
                    }
                    rs.close();
                    Util.prta("Got holdings for " + ms3.getSeedNameString() + " size=" + ha.getSize() + " #cwb=" + ncwb + " #redundant=" + nredundant + " #live=" + nblks);
                  } catch (SQLException e) {
                    Util.SQLErrorPrint(e, "SQL error getting holdings for seedname=" + ms3.getSeedNameString());
                    System.exit(0);
                  }
                  lastSeedname = ms3.getSeedNameString();
                }
              }
            }
            channelEnd.setTimeInMillis(ms3.getTimeInMillis());
            // Make sure its ok to put it in the database
            if (ms3.getTimeInMillis() < earliestTime || ms3.getTimeInMillis() > latestTime) {
              nredundant++;
              nredundantchan++;
              msp.free(ms3);
              continue;
            }
            // Note: These are all of the encodings supported for input, 1-3 ints, 10 steim1, 11 steim2, 30=SRO
            // This has to match the list of supported encodings in MiniSeedSocket.
            if (!MiniSeed.isSupportedEncoding(ms3.getEncoding(), fscale)) {
              Util.prt("** Encoding not supported encoding=" + ms3.getEncoding() + " ms=" + ms);
              msp.free(ms3);
              continue;
            }

            if (ha != null && !ignoreHoldings) {
              if (timeseries && (ha.containsFully(ms3.getTimeInMillis(),
                      (int) (ms3.getNextExpectedTimeInMillis() - ms3.getTimeInMillis())) && !ignoreHoldings)) {
                nredundant++;
                nredundantchan++;
                msp.free(ms3);
                continue;
              }
            }
            if (risMode) {
              int seq = 1;
              // write it out to a RIS server
              try {
                hdr.position(0);
                hdr.putShort((short) 0xa1b2);
                hdr.putShort((short) ms3.getNsamp());
                hdr.put(ms3.getSeedNameString().getBytes());
                hdr.position(16);
                GregorianCalendar time = ms3.getGregorianCalendar();
                hdr.putShort((short) time.get(Calendar.YEAR));
                hdr.putShort((short) time.get(Calendar.DAY_OF_YEAR));
                int mantissa;
                int divisor;
                if (ms3.getRate() >= 0.99999) {
                  mantissa = (int) (ms3.getRate() * 100. + 0.01);
                  divisor = -100;
                } else {
                  mantissa = (int) (ms3.getRate() * 10000. + 0.01);
                  divisor = -10000;
                }
                hdr.putShort((short) mantissa);
                hdr.putShort((short) divisor);
                hdr.put(ms3.getActivityFlags());
                hdr.put(ms3.getIOClockFlags());
                hdr.put(ms3.getDataQualityFlags());
                hdr.put((byte) ms3.getTimingQuality());
                int secs = (int) ((time.getTimeInMillis() % 86400000) / 1000L);
                hdr.putInt(secs);
                hdr.putInt(ms3.getUseconds());
                hdr.putInt(seq++);
                if (!nooutput) {
                  outCWB.write(header, 0, 40);
                }
                if (!nooutput) {
                  outCWB.write(databuf, 0, ms3.getNsamp() * 4);
                }
                //Util.prt("CWB write "+mslist.get(j)+" gbs="+mslist.get(j).getBlockSize());
              } catch (IOException e) {
                try {
                  Util.prt("Error writing to CWB  try to reopen... e=" + (e == null ? "null" : e.getMessage()));
                  scwb = new Socket(cwbIP, cwbport); // DEBUG: should be 7969
                  outCWB = scwb.getOutputStream();
                  if (!nooutput) {
                    outCWB.write(ms3.getBuf(), 0, ms3.getBlockSize());
                  }
                } catch (IOException e2) {
                  Util.prt(" *** Could not reopen and write to cwb. Abort!");
                  Util.prta("e=" + (e == null ? "null" : e.getMessage()));
                  Util.prta("e2=" + (e2 == null ? "null" : e2.getMessage()));
                  System.exit(1);
                }
              }
            } else {      // No RIS mode, its miniseed mode
              boolean ok = false;
              while (!ok) {
                try {     // This is MiniSeeed mode
                  if (ms3.getJulian() > today - edgeCutoff) {   // put in current edge
                    if (allowrecent && !risMode) {
                      if (!nooutput) {
                        out.write(ms3.getBuf(), 0, ms3.getBlockSize());
                      }
                      nblks++;
                      nlivechan++;
                      ok = true;
                    } else {
                      Util.prta(" ** Recent block found but all recent not on.  Block not sent\n" + ms3);
                      ok = true;
                    }
                  } else {                      // put it CWB
                    //Util.prt("CWB write "+mslist.get(j)+" gbs="+mslist.get(j).getBlockSize());
                    if (!nooutput) {
                      outCWB.write(ms3.getBuf(), 0, ms3.getBlockSize());
                    }
                    ncwb++;
                    ncwbchan++;
                    ok = true;
                  }
                } catch (IOException e) {
                  boolean reopenOK = false;
                  Util.sleep(300000);
                  while (!reopenOK) {
                    try {
                      if (allowrecent && !risMode) {
                        Util.prta("Open edge at " + edgeIP + "/" + edgePort);
                        s = new Socket(edgeIP, edgePort);
                        out = s.getOutputStream();
                        reopenOK = true;
                      } else {
                        Util.prta(" * Error writing to CWB  try to reopen... e=" + (e == null ? "null" : e.getMessage()) + " " + ms3);
                        scwb = new Socket(cwbIP, cwbport);
                        outCWB = scwb.getOutputStream();
                        reopenOK = true;
                      }
                    } catch (IOException e2) {
                      reopenOK = false;
                      Util.prta("** Retry open failed.  Try again");
                    }
                  }
                }
              }
            }
            msp.free(ms3);        // Free the just sent block
          }       // end of read  for each block to send

          Util.prta(Util.leftPad("" + nblks, 7) + " blks to " + edgeIP + "/" + edgePort + " " + Util.leftPad("" + ncwb, 6)
                  + " blks to CWB/" + cwbport + " " + Util.leftPad("" + nredundant, 6) + " blks redundant " + args[i] + " eof=" + eof + " msp=" + msp);
        } // end of while(!eof)
        rw.close();
        if (MemoryChecker.checkMemory()) {
          Util.prta("**** Memory problem " + msp.toString());
        }
      }
      Util.prt(lastSeedname + " " + Util.ascdate(channelStart) + " " + Util.asctime2(channelStart) + " - "
              + Util.ascdate(channelEnd) + " " + Util.asctime2(channelEnd) + " elapse=" + (System.currentTimeMillis() - totalElapse) / 1000.
              + " s sanity=" + sanityCheck + " failed=" + nfailSanity + " email=" + email + " mail.length=" + mail.length());
      if (sanityCheck) {
        if (!lastSeedname.equals("")) {
          mail.append(lastSeedname).append(" ").append(Util.ascdate(channelStart)).
                  append(" ").append(Util.asctime2(channelStart)).append(" - ").
                  append(Util.ascdate(channelEnd)).append(" ").append(Util.asctime2(channelEnd)).
                  append(" #cwb=").append(ncwbchan).append(" #redund=").append(nredundantchan).
                  append(" #live=").append(nlivechan).append("\n");
        }
        if (mail.length() > 20 || nfailSanity > 100) {
          mailhdr.append(nfailSanity).append(" blks failed sanity checks and were not sent to the CWB\n");
          for (int i = 0; i < nfailedIndex; i++) {
            mailhdr.append("Failed sanity ").append(failedStation[i]).append(" ").
                    append(Util.ascdatetime2(earliest[i])).append("-").append(Util.ascdatetime2(latest[i])).append(" ").
                    append(Util.df5(nfailedBlock[i])).append(" blks failed ").append("\n");
          }
          for (String arg : args) {
            mailhdr.append(arg).append(" ");
          }
          mailhdr.append("\n");
          SimpleSMTPThread e = SimpleSMTPThread.email(Util.getProperty("emailTo"),
                  "DBG:Late data processing report " + Util.getNode() + "/" + Util.getSystemName(),
                  mailhdr.toString()+mail.toString() + "\n");
          Util.prt(mailhdr.toString()+mail.toString());
          Util.sleep(4000);
          e.waitForDone();
          Util.prta("Email to " + Util.getProperty("emailTo") + " Successful=" + e.wasSuccessful());
          // This is weird, the first email would timeout, but later e-mails worked.
          if (!e.wasSuccessful()) {

            Util.prta("Try to send " + Util.getProperty("emailTo") + " again");
            e = SimpleSMTPThread.email(Util.getProperty("emailTo"),
                    "DBG:Late data processing report2 " + Util.getNode() + "/" + Util.getSystemName(), mail.toString() + "\n");
            e.waitForDone();
            Util.prta("2nd Email to " + Util.getProperty("emailTo") + " success=" + e.wasSuccessful());
          }
          if (email != null) {
            //Util.prt("Email to lind success="+e.wasSuccessful());
            String[] names = email.split(",");
            for (String name : names) {
              e = SimpleSMTPThread.email(name, "DBG:Late data processing report3 "
                      + Util.getNode() + "/" + Util.getSystemName(), mailhdr.toString()+mail.toString() + "\n");
              e.waitForDone();
              Util.sleep(4000);
              Util.prt(name + " success=" + e.wasSuccessful());
            }
          }
        }
      }
      if (s != null) {
        try {
          s.close();
        } catch (IOException e) {
        }

      }
      try {
        scwb.close();
      } catch (IOException e) {
      }
    } catch (IOException e) {
      Util.prt("cannot open a socket to send data! e=" + e.getMessage());
      e.printStackTrace();
      System.exit(0);
    }
  }

  private int makeFetchList(StringBuilder sb) {
    AnalyzeIndexFile.doFetchList(sb);         // convert runs into fetches across all files
    ArrayList<FetchList> gaplist = AnalyzeIndexFile.getFetchList();
    Util.prta("Start makeFetchList #gaps=" + gaplist.size());
    sb.append("Start makeFetchList #gaps=").append(gaplist.size()).append("\n");
    //DecimalFormat df33 = new DecimalFormat("0.000");
    FetchList[] gaps = new FetchList[gaplist.size()];
    int ngaps = 0;
    // Put the gaps in a n array and eliminate any of the mysterious nulls
    for (int i = 0; i < gaplist.size(); i++) {
      if (gaplist.get(i) != null) {
        gaps[ngaps++] = gaplist.get(i);
      } else {
        Util.prta("gap is null at " + i + " of " + gaps.length + " gaplist.size=" + gaplist.size() + " ngaps=" + ngaps);
      }
    }
    if (ngaps != gaps.length) {
      FetchList[] tmp = new FetchList[ngaps];
      System.arraycopy(gaps, 0, tmp, 0, ngaps);
      gaps = tmp;
    }
    for (int i = 0; i < gaps.length; i++) {
      if (gaps[i] == null) {
        Util.prta("gap is null at " + i + " of " + gaps.length + " gaplist.size=" + gaplist.size());
      }
    }
    Arrays.sort(gaps);
    int[] ymd = SeedUtil.fromJulian(AnalyzeIndexFile.getJulian());
    GregorianCalendar g = new GregorianCalendar(ymd[0], ymd[1] - 1, ymd[2]);
    Timestamp today = new Timestamp(g.getTimeInMillis());
    Timestamp tommorrow = new Timestamp(g.getTimeInMillis() + 86400000L);
    TreeMap<String, String> expected = new TreeMap<String, String>();
    Util.prt("MakeFetchList() gaps.lengh=" + gaps.length + " ngaps=" + ngaps + " DBServer=" + Util.getProperty("DBServer"));
    if (gaps.length <= 0) {
      Util.prta("No gaps returned.  Skip make fetch.");
      return 0;
    }     // Nothing to do
    //String typedbg="XX";
    DBConnectionThread anss = null;
    int countNew = 0;
    if (anss == null) {
      try {
        anss = new DBConnectionThread(Util.getProperty("DBServer"), "update", "anss", false, false, "msreadANSS", Util.getOutput());
        while (!anss.waitForConnection()) {
          Util.prta("Waiting for msreadAnss connection");
        }
      } catch (InstantiationException e) {
        Util.prt("Cannot connect to database Instantiation exception e=" + e.getMessage());
        System.exit(0);
      }
    }
    DBConnectionThread dbconn2 = null;
    if (dbconn2 == null) {
      try {
        dbconn2 = new DBConnectionThread(Util.getProperty("DBServer"), "update", "edge", true, false, "msreadEdge", Util.getOutput());
        while (!DBConnectionThread.waitForConnection("msreadEdge")) {
          Util.prta("Waiting for msreadEdge connection");
        }
      } catch (InstantiationException e) {
        Util.prt("Cannot connect to database Instantiation exception e=" + e.getMessage());
        System.exit(0);
      }
    }

    try {
      long mask;
      try (ResultSet rs2 = dbconn2.executeQuery("SELECT id FROM edge.flags WHERE flags='SMGetter Fetch' OR flags='Not Continuous'")) {
        mask = 0;
        if (rs2.next()) {
          mask = 1 << (rs2.getInt(1) - 1);
        }
      }
      Util.prt("Not continupus/SMGetter fetch is mask=" + Util.toHex(mask));
      try ( // Add the list of channels expecting gaps from channelList
              ResultSet rs = dbconn2.executeQuery(
                      "SELECT channel,gaptype FROM edge.channel WHERE gaptype!='' AND gaptype!='--' AND (flags & "
                      + mask + ")=0 ORDER BY channel")) {
        while (rs.next()) {
          expected.put((rs.getString("channel") + "      ").substring(0, 12), rs.getString("gaptype"));
        }
      }
      Util.prt("Number of channels found=" + expected.size());
    } catch (SQLException e) {
      Util.SQLErrorPrint(e, "Building list of stations for gaps");
    }

    int ndups = 0;
    int nnew = 0;
    int nongap = 0;
    String lastSeed = "";
    ArrayList<FetchList> known = new ArrayList<FetchList>(100);
    String lastType = "";
    for (int i = 0; i < gaps.length; i++) {
      try {
        FetchList gap = gaps[i];
        if (gap == null) {
          continue;
        }
        String type;
        if (gap.getSeedname().substring(0, 2).equals("XX")) {
          continue;
        }
        if (gap.getSeedname() == null) {
          Util.prta("found a null gap i=" + i + " lastSeed=" + lastSeed + " size=" + gaps.length);
          continue;
        }
        if (gap.getSeedname().equals(lastSeed)) {
          type = lastType;
        } else {
          // This is a new component - mark it handled and read in the fetchlist for comparison
          type = expected.get((gap.getSeedname() + "   ").substring(0, 12));
          if (type != null) { // mark off the found expected channels
            //type="K"+type.substring(0,1);   // override types for now
            expected.put((gap.getSeedname() + "   ").substring(0, 12), null);
            lastSeed = gap.getSeedname();
            lastType = type;
            if (known.size() > 0) {
              known.clear();
            }
            try {
              ResultSet rs = dbconn2.executeQuery("SELECT * FROM fetcher.fetchlist WHERE seedname='" + gap.getSeedname()
                      + "' AND start >='" + today.toString().substring(0, 19) + "' AND start<='" + tommorrow.toString().substring(0, 19)
                      + //"' AND type ='"+type+
                      "'");
              while (rs.next()) {
                known.add(new FetchList(rs));
              }
            } catch (SQLException e) {
              Util.SQLErrorPrint(e, "Trying to get fetchList items");
            }
          }
        }
        if (type != null) {
          // Process this gap, compare to known gaps
          Timestamp end = new Timestamp(gap.getStart().getTime() + 2000);
          try {
            boolean found = false;
            gap.setType(type);
            for (FetchList known1 : known) {
              if (gap.isSame(known1)) {
                Util.prt(gap.toString() + " is a duplicate with ID=" + known1.getID());
                sb.append("Duplicate : ").append(gap.toString()).append(" ID=").append(known1.getID()).append("\n");
                ndups++;
                found = true;
                break;
              }
            }
            if (!found) {
              countNew++;
              Util.prt(countNew + " New Gap : " + gap);
              sb.append(countNew).append(" New Gap : ").append(gap).append("\n");
              Timestamp now = new Timestamp(gap.getStart().getTime());
              double duration = gap.getDuration();
              while (duration > 0) {
                double dur = duration;
                if (dur > 10800.) {
                  dur = 10800.;
                }
                String s = "INSERT INTO fetcher.fetchlist (seedname,start,start_ms,duration, "
                        + "type,status,updated,created_by, created) VALUES "
                        + "('" + gap.getSeedname() + "','" + now.toString().substring(0, 19) + "',"
                        + gap.getStartMS() + "," + Util.df23(dur) + ",'" + type + "','open',now(),0, now())";
                dbconn2.executeUpdate(s);
                duration -= dur;
                now.setTime(now.getTime() + (long) (dur * 1000.));
              }
              nnew++;
            }
          } catch (SQLException e) {
            Util.SQLErrorPrint(e, "Reading from fetchlist or inserting into fetchlist");
          }
        } else {
          nongap++;
        }
      } catch (RuntimeException e) {  // we do not want runtimes to cause loss of gap writes, so catch, log and go on.
        Util.prta("Got a Runtime making the fetchlist at i=" + i + " lastSeed=" + lastSeed + " e=" + e);
        e.printStackTrace();
      }
    }
    // Now we need to see about the expected channels that did not have gaps and create full day gaps
    Object[] keys = expected.keySet().toArray();
    TreeMap<String, HoldingSummary> holdings = AnalyzeIndexFile.getHoldings();
    Iterator<HoldingSummary> itr = holdings.values().iterator();
    Util.prt("Holdings size=" + holdings.size());
    while (itr.hasNext()) {
      Util.prt(itr.next().toString());
    }
    for (Object key1 : keys) {
      String key = (String) key1;
      String type = expected.get(key);
      Util.prt("Look day gap for " + key + " type=" + type + " hold=" + holdings.get(key));
      if (key.substring(0, 2).equals("XX")) {
        continue;
      }
      if (type != null) {
        //type = "K"+type.substring(0,1);// Debug override tyep
        try {
          HoldingSummary h = holdings.get(key);
          if (h == null) {
            countNew++;
            if (known.size() > 0) {
              known.clear();
            }
            try {
              ResultSet rs = dbconn2.executeQuery("SELECT * FROM fetcher.fetchlist WHERE seedname='" + key
                      + "' AND start >='" + today.toString().substring(0, 19) + "' AND start<='" + tommorrow.toString().substring(0, 19)
                      + //"' AND type ='"+type+
                      "'");
              while (rs.next()) {
                known.add(new FetchList(rs));
              }
            } catch (SQLException e) {
              Util.SQLErrorPrint(e, "Trying to get fetchList items");
            }

            // There are no holdings for this station on this day.  Must need to create fetchlist
            if (key.substring(7, 8).equals("L") || key.substring(7, 8).compareTo("U") >= 0) {
              FetchList gap = new FetchList(key, today, 0, 86400., type, "XX");
              boolean found = false;
              for (FetchList known1 : known) {
                if (known1.isSame(gap)) {
                  Util.prt(gap.toString() + " duplicates with ID=" + known1.getID());
                  sb.append("Duplicate : ").append(gap.toString()).append(" with ID=").append(known1.getID()).append("\n");
                  found = true;
                  break;
                }
              }
              if (!found) {
                Util.prt("Day gap : " + key + " " + today.toString() + " dur=86400." + " type=" + type);
                sb.append("Day gap : ").append(key).append(" ").append(today.toString()).
                        append(" dur=86400." + "type=").append(type).append("\n");
                String s = "INSERT INTO fetcher.fetchlist (seedname,start,start_ms,duration, "
                        + "type,status,updated,created_by, created) VALUES "
                        + "('" + key + "','" + today.toString().substring(0, 19) + "',"
                        + "0,86400.,'" + type + "','open',now(),0, now())";
                dbconn2.executeUpdate(s);
              }

            } else {
              countNew++;
              Timestamp now = new Timestamp(today.getTime());
              Util.prt("Day gap : " + key + " " + now.toString() + " dur=86400. type=" + type);

              sb.append("Day gap : ").append(key).append(" ").append(now.toString()).
                      append(" dur=86400. type=").append(type).append("\n");
              for (int j = 0; j < 8; j++) {      //  create one fetch each 3 hours for baler sanity
                FetchList gap = new FetchList(key, now, 0, 10800., type, "XX");
                boolean found = false;
                for (int k = 0; k < known.size(); k++) {
                  if (known.get(k).isSame(gap)) {
                    Util.prt(gap.toString() + " duplicates with ID=" + known.get(k).getID());
                    sb.append("Duplicate : ").append(gap.toString()).append(" with ID=").
                            append(known.get(j).getID()).append("\n");
                    found = true;
                    break;
                  }
                }
                if (!found) {
                  String s = "INSERT INTO fetcher.fetchlist (seedname,start,start_ms,duration, "
                          + "type,status,updated,created_by, created) VALUES "
                          + "('" + key + "','" + now.toString().substring(0, 19) + "',"
                          + "0,10800.,'" + type + "','open',now(),0, now())";
                  dbconn2.executeUpdate(s);
                }
                now.setTime(now.getTime() + 10800000L);
              }
            }
          }
        } catch (SQLException e) {
          Util.SQLErrorPrint(e, "Inserting day long gaps");
        }
      } // if type is not null
    } // for each key (expected holdings)
    return countNew;
  }

  /**
   * this inner class is used to analyze holdings
   */
  public static class Span {

    RawDisk rw;
    int nsamp;
    long startTime;
    int[] data;
    int iblk;
    boolean dbg;
    double rate;
    int blocklen;

    public String listDifferences(Span sp) {
      StringBuilder sb = new StringBuilder(10000);
      if (sp.getNSamp() != nsamp) {
        sb.append("Number of samples are not same nsamp=").append(nsamp).append(" ").append(sp.getNSamp()).append("\n");
      }
      if (sp.getStartTime() != startTime) {
        GregorianCalendar g1 = new GregorianCalendar();
        GregorianCalendar g2 = new GregorianCalendar();
        g1.setTimeInMillis(startTime);
        g2.setTimeInMillis(sp.getStartTime());

        sb.append("Start times not the same : ").append(Util.ascdate(g1)).append(" ").
                append(Util.asctime(g1)).append(" != ").append(Util.ascdate(g2)).
                append(" ").append(Util.asctime(g2)).append("\n");
      }
      if (rate != sp.getRate()) {
        sb.append("Rates not the same ").append(rate).append("!=").append(sp.getRate());
      }
      if (nsamp == sp.getNSamp()) {
        int[] data2 = sp.getSamples();
        for (int i = 0; i < nsamp; i++) {
          if (data[i] == data2[i]) {
            sb.append(i).append(" ").append(data[i]).append("!=").append(data2[i]).append("\n");
          }
        }
      }
      return sb.toString();
    }

    public Span(RawDisk a) {
      rw = a;
      data = new int[3460000];
      iblk = 0;
      dbg = false;
      rate = -1.;
      blocklen = 512;
    }

    public void setDebug(boolean t) {
      dbg = t;
    }

    @Override
    public String toString() {
      GregorianCalendar g = new GregorianCalendar();
      g.setTimeInMillis(startTime);
      GregorianCalendar end = new GregorianCalendar();
      end.setTimeInMillis(startTime + ((long) (nsamp / rate * 1000.)));
      return "Span: " + Util.ascdate(g) + " " + Util.asctime(g) + " ns=" + nsamp + " rate=" + rate
              + " end=" + Util.asctime(end);
    }

    public int getNSamp() {
      return nsamp;
    }

    public int[] getSamples() {
      return data;
    }

    public long getStartTime() {
      return startTime;
    }

    public double getRate() {
      return rate;
    }

    public void getNext() {
      boolean contiguous = true;
      nsamp = 0;
      byte[] buf;
      MiniSeed ms;
      startTime = -1;
      GregorianCalendar time;
      GregorianCalendar last = null;
      long diff;
      long lastlength = 0;
      String lastString = "";
      while (contiguous) {
        try {
          buf = rw.readBlock(iblk, 512);

          //Util.prt("read bloc len="+buf.length+" iblk="+iblk);
          // Bust the first 512 bytes to get all the header and data blockettes - data
          // may not be complete!  If the block length is longer, read it in complete and
          // bust it appart again.
          ms = new MiniSeed(buf);
          if (ms.getBlockSize() > 512) {
            buf = rw.readBlock(iblk, ms.getBlockSize());
            ms = new MiniSeed(buf);
            blocklen = ms.getBlockSize();
          }

          // Print out the console long if any is received.
          if (ms.getSeedNameString().substring(7, 10).equals("LOG") && ms.getNsamp() > 0 && dbg) {
            Util.prt("data=" + new String(ms.getData(ms.getNsamp())));
            continue;
          }
          // List a line about the record unless we are in errors only mode
          if (dbg) {
            Util.prt(ms.toString() + " iblk=" + iblk + "/" + ms.getBlockSize());
          }
          time = ms.getGregorianCalendar();
          if (startTime == -1) {
            startTime = time.getTimeInMillis();
          }

          if (last != null) {
            diff = time.getTimeInMillis() - last.getTimeInMillis()
                    - lastlength;
            if (diff > 1) {
              if (dbg) {
                Util.prt(lastString);
                Util.prt(ms.toString() + " iblk=" + iblk + "/" + ms.getBlockSize());
              }
              Util.prta("Span: ***** discontinuity = " + diff + " ms. ends at " + ms.getTimeString()
                      + " blk=" + iblk);
              //contiguous=false;
              break;
            }
          }
          if (rate < 0) {
            rate = ms.getRate();
          }
          // if this is a trigger or other record it will have no samples (but a time!) so
          // do not use it for discontinuity decisions
          if (ms.getNsamp() > 0) {
            byte[] frames = new byte[ms.getBlockSize() - 64];
            System.arraycopy(buf, ms.getDataOffset(), frames, 0, ms.getBlockSize() - ms.getDataOffset());
            if (ms.getEncoding() != 11) {
              rw.close();
              Util.prt("Span: Cannot decode - not Steim II");
              System.exit(0);
            }
            int activity = ms.getActivityFlags();
            int IOClock = ms.getIOClockFlags();
            int dataQuality = ms.getDataQualityFlags();
            try {
              int reverse = 0;
              if (nsamp > 0) {
                reverse = data[nsamp - 1];
              }
              int[] samples = Steim2.decode(frames, ms.getNsamp(), ms.isSwapBytes(), reverse);
              if (reverse != Steim2.getXminus1() && nsamp != 0) {
                Util.prt("Span: xminus1 error ns=" + nsamp + " reverse=" + reverse + " xminus1=" + Steim2.getXminus1() + " diff=" + (reverse - Steim2.getXminus1()));
              }
              System.arraycopy(samples, 0, data, nsamp, ms.getNsamp());
            } catch (SteimException e) {
              Util.prt("Span: Steim Exception caught on ms=" + ms.toString());
            }
            nsamp += ms.getNsamp();
          }
          lastlength = (long) (ms.getNsamp() * 1000 / ms.getRate());
          last = time;
          lastString = ms.toString() + " iblk=" + iblk + "/" + ms.getBlockSize();

          // if reported Blck 1000 length is zero, use the last known block len
          if (ms.getBlockSize() <= 0) {
            iblk += blocklen / 512;
          } else {
            iblk += ms.getBlockSize() / 512;
            blocklen = ms.getBlockSize();
          }
        } catch (IllegalSeednameException e) {
          Util.prt("Span: Illegal seedname found " + e.getMessage());
        } catch (IOException e) {
          if (e.getMessage() != null) {
            Util.prta("Span: IOException " + e.getMessage());
          }
          //else Util.prta("Span: EOF Nblocks="+iblk);
          contiguous = false;
        }
      } // while contiguous
    }
  }

  public static final int trimsize(String filename) {
    byte[] buf2 = new byte[512 * 2000];
    ByteBuffer bb = ByteBuffer.wrap(buf2);
    int savings = 0;
    // Index block stuff
    int[] starting = new int[IndexBlock.MAX_EXTENTS];
    // Master block list from the control block
    short[] mBlocks = new short[IndexFile.MAX_MASTER_BLOCKS];
    String seedNameMB;
    short firstIndex;
    short lastIndex;
    byte[] mbbuf = new byte[512];
    ByteBuffer mbb = ByteBuffer.wrap(mbbuf);

    int maxStarting = 0;        // assume a small maximum
    try {
      String idxFile = filename.replaceAll(".ms", ".idx");
      RandomAccessFile data;
      try (RandomAccessFile idx = new RandomAccessFile(idxFile, "rw")) {
        data = new RandomAccessFile(filename, "rw");
        long fileLength = data.length() / 512L;
        // read in the index control block and decode the parameters
        idx.read(buf2, 0, 512);
        bb.clear();
        int len = bb.getInt();
        int next_extent = bb.getInt();
        short next_index = bb.getShort();
        int nmb = 0;
        for (int ii = 0; ii < IndexFile.MAX_MASTER_BLOCKS; ii++) {
          mBlocks[ii] = bb.getShort();
          if (mBlocks[ii] > 0) {
            nmb = ii;
          }
        } //Util.prt("len="+len+" next="+next_extent+" nextIndex="+next_index+" idx.size="+(idx.length()/512L)+" mbs are "+mBlocks[0]+" "+
        //    mBlocks[1]+" "+mBlocks[2]+" "+mBlocks[3]+" "+mBlocks[4]+" ");
        // This big section reads the master blocks and the last index block for each channel
        // in search of the largest extent assigned.  Sometimes this is bigger the next_ext in control
        // block because the control block was not written out correctly when the file was closed on the
        // edge (prior to Feb 2008).  We then use the larger of the extent assigned or next_exent.
        for (int ii = 0; ii < IndexFile.MAX_MASTER_BLOCKS; ii++) {
          int mblk;
          if (mBlocks[ii] != 0) {
            try {
              mblk = mBlocks[ii] & 0xFFFF;
              idx.seek(mblk * 512L);
            } catch (IOException e) {
              Util.prt("Seek error for master block ii=" + ii + " mblock[ii]=" + mBlocks[ii] + " seek bytes = " + (mBlocks[ii] * 512L));
              continue;
            }
            int lenmb = idx.read(mbbuf, 0, 512);      // get the master block
            if (lenmb != 512) {
              Util.prt("*** could not read master block at " + mBlocks[ii] + "/" + mblk + " rtn=" + lenmb + " file=" + filename + " mb=" + ii + " #mb=" + nmb);
              continue;
            }
            mbb.position(0);
            byte[] namebuf = new byte[12];
            // for each channel in the master block trace the index blocks and track the maximum extent.
            for (int j = 0; j < MasterBlock.MAX_CHANNELS; j++) {
              mbb.get(namebuf);
              seedNameMB = new String(namebuf);
              firstIndex = mbb.getShort();
              lastIndex = mbb.getShort();
              if (firstIndex <= 0) {
                break;      //  no more channels in this master block
              }
              int nextIndex = lastIndex;     // first index for this component
              // Until we are out of index blocks for this channel, decode, tranc maxStarting, and track next one
              while (nextIndex > 0) {
                idx.seek(nextIndex * 512);
                idx.read(buf2, 0, 512);
                bb.position(0);
                bb.get(namebuf);
                String seedName = new String(namebuf);
                if (!seedName.equals(seedNameMB)) {
                  Util.prta("Seedname mismatch on link " + seedNameMB + "|" + seedName);
                  break;
                }
                nextIndex = bb.getInt();
                //Util.prta(seedName+" "+nextIndex+" last="+lastIndex);
                int updateTime = bb.getInt() * 1000;
                int currentExtentIndex = -1;
                for (int i = 0; i < IndexBlock.MAX_EXTENTS; i++) {
                  starting[i] = bb.getInt();
                  if (starting[i] >= 0) {
                    currentExtentIndex = ii;
                  }
                  if (starting[i] > maxStarting) {
                    maxStarting = starting[i];
                  }
                  bb.getLong();   // bitmap[i]
                  bb.getShort();  //earliestTime[i]=
                  bb.getShort();  //latestTime[i]=
                }
              }
            }
          }
        } //Util.prt("trimSize rw="+filename+" nxtext="+next_extent+" maxStart="+maxStarting+
        //  " datalen bks="+fileLength+"/idx="+len+" trim nxtext+64");
        if (maxStarting > next_extent) {
          Util.prt("**** max starting > next_extent  st=" + maxStarting + " next=" + next_extent + " diff=" + (maxStarting - next_extent));
          next_extent = maxStarting;
        }
        if (fileLength > next_extent + 64) {
          // check to be sure there is no dat just past the end of file
          boolean allZero = true;
          data.seek((next_extent + 64) * 512L);
          int l = data.read(buf2, 0, buf2.length - 131072);
          if (l != buf2.length - 131072) {
            Util.prt("**** got l < 991232 = " + l);
          }
          int nzero = 0;
          int firstOff = -1;
          for (int j = 0; j < buf2.length - 131072; j++) {
            if (buf2[j] != 0) {
              nzero++;
              allZero = false;
              if (firstOff < 0) {
                firstOff = j;
              }
            }
          }
          if (nzero > 0) {
            Util.prt("    ******* " + filename + " trimFileSize() found non-zero at=" + firstOff
                    + " #=" + nzero + " of " + l + " " + (nzero * 100 / l) + "% next_extent=" + next_extent);
            for (int i = firstOff; i < l; i = i + 512) {
              if (mbbuf[0] == 0) {
                continue;
              }
              try {
                System.arraycopy(buf2, i, mbbuf, 0, 512);
                MiniSeed ms = new MiniSeed(mbbuf);
                Util.prt(i + " Bad zero contains " + ms.toString().substring(0, 100));
              } catch (IllegalSeednameException e) {
                Util.prt("Bad zero block does not translate");
              }
            }
          }
          if (allZero) {
            savings += (fileLength - next_extent - 64);
            Util.prta(filename + " trimFileSize() to " + ((next_extent + 64L) * 512L) + " b " + (next_extent + 64)
                    + " blks saving " + (fileLength - next_extent - 64) + " blks" + (fileLength - next_extent - 64 > 500000 ? " ****" : ""));
            //if(false) {
            data.setLength((long) (next_extent + 64) * 512L);
            // Update the index control block to reflect the new realitiy.
            idx.seek(0L);
            idx.read(buf2, 0, 512);
            bb.position(0);
            bb.putInt(next_extent + 64);
            bb.putInt(next_extent);
            idx.seek(0L);
            idx.write(buf2, 0, 512);
            //}
          } else {
            Util.prta(filename + " is not all zeros after next_extent=" + next_extent);
          }

        }
      }
      data.close();
    } catch (IOException e) {
      Util.IOErrorPrint(e, "Error opening file=" + filename);
    }
    return savings;
  }

  /**
   * the unit test main
   *
   * @param args COmmand line arguments
   */
  public static void main(String[] args) {
    Util.setProcess("msread");
    int blocklen = 512;
    Util.debug(false);
    Util.setNoconsole(false);
    Util.setNoInteractive(true);
    Util.setModeGMT();
    Util.addDefaultProperty("edgeip", "localhost");
    Util.addDefaultProperty("edgeport", "7205");
    Util.addDefaultProperty("edgecutoff", "10");
    Util.addDefaultProperty("holdingip", "localhost");
    Util.addDefaultProperty("holdingport", "7996");
    Util.addDefaultProperty("holdingtype", "CW");
    //Util.addDefaultProperty("holdingmode","false");
    Util.addDefaultProperty("MySQLServer", "localhost");
    Util.addDefaultProperty("DBServer", "localhost/3306:edge:mysql:edge");
    Util.addDefaultProperty("StatusDBServer", "localhost/3306:edge:mysql:edge");
    Util.addDefaultProperty("MetaDBServer", "localhost/3306:edge:mysql:edge");
    Util.addDefaultProperty("cwbip", "localhost");
    Util.addDefaultProperty("cwbport", "2062");
    Util.addDefaultProperty("risport", "7976");
    Util.loadProperties("edge.prop");
    Util.loadProperties("msread.prop");
    Util.saveProperties();

    for (int i=0; i<args.length; i++) {
      String arg = args[i];
      if (arg.equalsIgnoreCase("-makeTA")) {
        MakeTASeedLinkLists();
        System.exit(0);
      }
      if (arg.equals("-reorg")) {
        ReorganizeFile.main(args);
        System.exit(0);
      }
      /*if (arg.equals("-hout")) {
        ClientClientCopier.main(args); System.exit(0);
      }*/
      if (arg.equalsIgnoreCase("-editprop")) {
        editProperties();
      }
      if (arg.equalsIgnoreCase("-prop")) { 
        Util.prt("Loading prop file : "+args[i+1]);
        Util.loadProperties(args[i+1]);
        i++;
      }
    }
    IndexFile.init();
    //Util.prtProperties();
    msread msr = new msread(args);
    System.exit(0);

  }

  public final void compareTwoFiles(String file1, String file2) {
    RawDisk one = null;
    RawDisk two = null;
    try {
      one = new RawDisk(file1, "r");
      two = new RawDisk(file2, "r");
    } catch (IOException e) {
      Util.prt("file not found exception found." + e.getMessage());
      Util.IOErrorPrint(e, "Opening raw disk files");
      System.exit(0);
    }

    boolean contiguous = true;
    byte[] buf2;
    MiniSeed ms = null;
    Span span1 = new Span(one);
    Span span2 = new Span(two);
    while (true) {
      span1.getNext();
      span2.getNext();
      if (span1.getNSamp() == 0 && span2.getNSamp() == 0) {
        break;
      }
      boolean err = false;
      if (span1.getStartTime() != span2.getStartTime()) {
        Util.prt("Span do not have same start time.");
        err = true;
      }
      if (span1.getStartTime() + (long) (span1.getNSamp() / span1.getRate())
              != span2.getStartTime() + (long) (span2.getNSamp() / span2.getRate())) {
        Util.prt("Span do not have same end time");
        err = true;
      }

      if (span1.getNSamp() != span2.getNSamp()) {
        Util.prt("Spans do not have same number of samples");
        err = true;
      }
      if (err) {
        Util.prt("Span1: " + span1.toString());
        Util.prt("Span2: " + span2.toString());
      } else {
        int[] samples1 = span1.getSamples();
        int[] samples2 = span2.getSamples();
        int ndiff = 0;
        for (int i = 0; i < span1.getNSamp(); i++) {
          if (samples1[i] != samples2[i]) {
            Util.prt((i + "    ").substring(0, 6) + " diff " + samples1[i] + "!=" + samples2[i]
                    + " diff=" + (samples1[i] - samples2[i]));
            ndiff++;
          }
        }
        if (ndiff > 0) {
          Util.prt(ndiff + " samples are different");
        }
      }
    }
  }

  /**
   * Look at the All-StationsList.txt, and US-TA-StationList.txt and build up a list of stations
   * that are in the REF or EARN lists, but eliminate any station in the TA found in ta_sla.setup
   * and ta_slb.setup, or bud_sl.setup. In additional accumulate a list of all of the stations in
   * the TA which are NOT REF, EARN or on the ta_sla.setup, ta_slb.setup or bud_sl.setup - this is
   * the list of TA stations to be acquired outside of OPS directly to cwbrs.
   * <br>
   *
   * NOTE: The cascadia list used to be added, but all stations on the cascadia list are now closed
   * and are part of the UW network.
   * <br>
   * 1) create a list of TA stations which are ended (taEnded)
   * <br>
   * 2) Create a list of TA stations which are on the EARN list (taEarn)
   * <br>
   * 3) Remove an station on taEarn from taEnded (the station is ended in the TA but are operating
   * as part of EARN)
   * <br>
   * 4) Use bud_sl.setup to create an override list of stations, if any stations is on the ended
   * list add it to the email as closed.  This file is obsoleted.
   * <br>
   * 5) Use ta_sla.setup to add to the override list of station, if any stations are on the ended
   * list add it the email as closed
   * <br>
   * 6) Use ta_slb.setup to add to the override list of station, if any stations are on the ended
   * list add it the email as closed
   * <br>
   * 7) for each station on the combined all-stations and EARN list,
   * <br>
   * if the station is either REF or EARN and Operating and network TA add it to the taRef list
   * <br>
   * else If the station is US-TA and is operating and is network TA and is not on the override
   * list, add it to taNotRef list.
   * <br>
   * 8) Send the "ended" email accumulated in step 4-6 - closed stations in sl_sla.setup,
   * sl_slb.setup or bud_sl.setup (obsolete)
   * <br>
   * 9) For each line on the taNotRef list, insure it is not on the taRef list, and add it to the
   * ta_notref.setup file
   * <br>
   * IRIS has a disturbing tradition of putting stations on both list - eliminate REF from non-ref
   * <br>
   * 10) Compare the current ta_ref_sl.setup and ta_notref_sl.setup against what has been
   * accumulated here, and if they are different set the return value to success if there is no
   * change in the files.
   * <br>
   * 11) write out the ta_ref_sl.setup and ta_notref_sl.setup
   * <br>
   *
   */

  public static void MakeTASeedLinkLists() {
    //http://ds.iris.edu/files/earthscope/usarray/_US-TA-ADOPTED-StationList.txt  // another one, not needed yet
    // http://ds.iris.edu/files/earthscope/usarray/_CASCADIA-TA-StationList.txt   // another one, not needed yet
    String cmdall = "http://ds.iris.edu/files/earthscope/usarray/ALL-StationList.txt";
    String cmdearn =  "http://ds.iris.edu/files/earthscope/usarray/_US-EARN-StationList.txt";
    String cmdcascadia = "http://ds.iris.edu/files/earthscope/usarray/_CASCADIA-TA-StationList.txt";
    ArrayList<String> taRef = new ArrayList<String>(100);
    ArrayList<String> taEarn = new ArrayList<>(100);
    ArrayList<String> taNotRef = new ArrayList<String>(100);
    ArrayList<String> taEnded = new ArrayList<String>(100);
    StringBuilder ended = new StringBuilder(10000);
    StringBuilder all = new StringBuilder(100000);
    String s = "";
    String cmd = cmdall;
    try {
      Wget w2 = new Wget(cmdall);
      Util.prta(Util.ascdate() + " " + cmdall + " getLength=" + w2.getLength());
      for (int i = 0; i < w2.getLength(); i++) {
        all.append((char) w2.getBuf()[i]);
      }
      if (all.length() < 10000) {
        SendEvent.edgeSMEEvent("MakeRefBad", "ALL len="+all.length() +" " + cmdall, "msread");
        return;
      }
      cmd = cmdearn;
      Wget wearn = new Wget(cmdearn);
      Util.prta(Util.ascdate() + " " + cmdearn + " getLength=" + wearn.getLength());
      for (int i = 0; i < wearn.getLength(); i++) {
        all.append((char) wearn.getBuf()[i]);
      }
      s = all.toString();
      if (wearn.getLength() < 1000) {
        SendEvent.edgeSMEEvent("MakeRefBad", "EARN len="+wearn.getLength() +" " + cmdearn, "msread");
        return;
      }
      cmd = cmdcascadia;
      Wget wcascadia = new Wget(cmdcascadia);
      Util.prta(Util.ascdate() + " " + cmdcascadia + " getLength=" + wcascadia.getLength());
      for (int i = 0; i < wcascadia.getLength(); i++) {
        all.append((char) wcascadia.getBuf()[i]);
      }
      if (wcascadia.getLength() < 1000) {
        SendEvent.edgeSMEEvent("MakeRefBad", "CASCADE len="+wcascadia.getLength() +" " + cmdcascadia, "msread");
        return;
      }
    } catch (IOException e) {
      SendEvent.edgeSMEEvent("MakeRefBad", "WGet err="+e + " " + cmd, "msread");
      e.printStackTrace();
      return;
    }
    StringBuilder taRefLines = new StringBuilder(10000);
    taRefLines.append("#<Begin>\n");

    StringBuilder taNotRefLines = new StringBuilder(200000);
    taNotRefLines.append("#<Begin>\n");

    String line;
    String overrideList = "";
    try {
      BufferedReader in = new BufferedReader(new StringReader(s));
      // Find the header line and leave
      while ((line = in.readLine()) != null) {
        if (line.contains("VNET")) {
          break;
        }
      }

      // For each line create a list called taEnded and taEarn consist=ing of ended station and operating earn stations
      while ((line = in.readLine()) != null) {
        String[] parts = line.split("\t");
        if (parts[0].equals("_US-TA") && parts[9].equalsIgnoreCase("Ended")) {
          taEnded.add(parts[1] + " " + parts[2]);
        }
        if (parts[0].equals("_US-EARN") && parts[9].equalsIgnoreCase("Operating")) {
          taEarn.add(parts[1] + " " + parts[2]);
        }
      }

      in.close();
      // take the EARN sites off of the ended list because the are ended for the TA, but are still operating a EARN
      Util.prt("Number of earn sites=" + taEarn.size() + " taEnded size=" + taEnded.size());
      for (String taEarn1 : taEarn) {
        for (int j = taEnded.size() - 1; j >= 0; j--) {
          if (taEnded.get(j).equals(taEarn1)) {
            Util.prt(taEnded.get(j) + " is on US-EARN list and is open");
            taEnded.remove(j);
          }
        }
      }

      // Use bud_sl to create an override list of stations, if any stations is on the ended list add it to the email to be unconfigured
      /*Util.prta("Read bud_sl.setup");
      in = new BufferedReader(new FileReader("EDGEMOM/SEEDLINK/bud_sl.setup"));
      while ((line = in.readLine()) != null) {
        if (line.length() >= 2) {
          if (line.substring(0, 2).equals("TA")) {
            overrideList += line.substring(0, 7) + " ";
            for (String taEnded1 : taEnded) {
              if (line.substring(0, 7).trim().equals(taEnded1.trim())) {
                ended.append("** closed station ").append(line.substring(0, 7)).append(" in bud_sl.setup\n");
              }
            }
          }
        }
      }*/
      in.close();
      Util.prta("Read EDGEMOM/SEEDNAME/ta_sla.setup");
      // Use ta_sla.setup  to create an override list of stations, if any stations is on the ended list add it to the email to be unconfigured
      in = new BufferedReader(new FileReader("EDGEMOM/SEEDLINK/ta_sla.setup"));
      while ((line = in.readLine()) != null) {
        if (line.length() >= 2) {
          if (line.substring(0, 2).equals("TA")) {
            overrideList += line.substring(0, 7) + " ";
            for (String taEnded1 : taEnded) {
              if (line.substring(0, 7).trim().equals(taEnded1.trim())) {
                ended.append("** closed station ").append(line.substring(0, 7)).append(" in ta_sla.setup\n");
              }
            }
          }
        }
      }
      in.close();
      Util.prta("Read EDGEMOM/SEEDNAME/ta_slb.setup");
      // Use ta_sla.setup  to create an override list of stations, if any stations is on the ended list add it to the email to be unconfigured
      in = new BufferedReader(new FileReader("EDGEMOM/SEEDLINK/ta_slb.setup"));
      while ((line = in.readLine()) != null) {
        if (line.length() >= 2) {
          if (line.substring(0, 2).equals("TA")) {
            overrideList += line.substring(0, 7) + " ";
            for (String taEnded1 : taEnded) {
              if (line.substring(0, 7).trim().equals(taEnded1.trim())) {
                ended.append("** closed station ").append(line.substring(0, 7)).append(" in ta_slb.setup\n");
              }
            }
          }
        }
      }
      in.close();
      Util.prt("TA station forced into Edge =" + overrideList);
    } catch (IOException e) {
      Util.prt("Could not read bud_sl.setup e=" + e);
      e.printStackTrace();
      SendEvent.edgeSMEEvent("MakeTAErr", "Make TA Err e=" + e, "msread");
    }

    //Util.prt(s);
    BufferedReader in = new BufferedReader(new StringReader(s));
    // for each station on the combined allstations and EARN list, if the station is either REF or EARN
    // and Operating and network TA add it to the taRef list
    // If the station is US-TA and is operating and is network TA and is not on the override list, add it to taNotRef list
    try {
      while ((line = in.readLine()) != null) {
        if (line.contains("VNET")) {
          break;
        }
      }
      while ((line = in.readLine()) != null) {
        String[] parts = line.split("\t");
        //if(parts[2].equals("N23A")) 
        //  Util.prt("N23A");
        //Util.prt("VNET="+parts[0]+" net="+parts[1]+" stat="+parts[2]+" start="+parts[7]+" end="+parts[8]+" status="+parts[9]);
        if (parts[2].equals("Q16A")) {
          parts[0] = "_US-REF";     // crandall mine collapse station is special
        }
        if (parts[0].equals("_US-REF") || parts[0].equals("_US-EARN")) {
          if (parts[9].equals("Operating") && parts[1].equals("TA")) {
            if (!overrideList.contains(parts[1] + " " + parts[2])) {
              taRef.add(parts[1] + " " + parts[2]);
              taRefLines.append(parts[1]).append(" ").append(parts[2]).append("  #").append(parts[0]).append("\n");
            } else {
              Util.prt(parts[1] + " " + parts[2] + " is on override list.  Do not add to ref.");
            }
          }
        } else if (parts[0].equals("_US-TA")) {
          if (parts[9].equals("Operating") && parts[1].equals("TA")) {
            if (!overrideList.contains(parts[1] + " " + parts[2])) {
              taNotRef.add(parts[1] + " " + parts[2]);
              taNotRefLines.append(parts[1]).append(" ").append(parts[2]).append("  #").append(parts[0]).append("\n");
            } else {
              Util.prt(parts[1] + " " + parts[2] + " is on override list.  Do not add to non-ref.");
            }
          }
        }
      }
      // They have a disturbing tradition of putting station on both list - eliminate REF from non-ref
      in.close();
      Util.prt("Ended email message length=" + ended.length() + "\n" + ended);
      if (ended.length() > 10 && System.currentTimeMillis() % (10 * 86400000L) < 180000) {
        SimpleSMTPThread.email("ketchum", "TA stations closed list",
                Util.ascdate() + " " + Util.asctime() + " the following stations have been closed on the TA (are not on EARN or CASCADIA) and are configured :\n"
                + ended);
        SimpleSMTPThread.email("dmason1", "TA stations closed list",
                Util.ascdate() + " " + Util.asctime() + " the following stations have been closed on the TA (are not on EARN or CASCADIA) and are configured :\n"
                + ended);
      }
      taNotRefLines.delete(0, taNotRefLines.length());
      taNotRefLines.append("#<Begin>\n");

      // For each line on the taNotRef list, insure it is not on the taRef list, and add it to the ta_notref.setup file
      // They have a disturbing tradition of putting station on both list - eliminate REF from non-ref
      for (String taNotRef1 : taNotRef) {
        boolean found = false;
        for (int j = 0; j < taRef.size(); j++) {
          if (taNotRef1.equals(taRef.get(j))) {
            found = true;
            Util.prt("Found in both " + taNotRef1);
            break;
          }
        }
        if (!found) {
          taNotRefLines.append(taNotRef1).append("\n");
        }
      }
    } catch (IOException e) {
      Util.IOErrorPrint(e, "Reading the All-station.txt file");
      e.printStackTrace();
      SendEvent.edgeSMEEvent("MakeRefBad", "Web file IOerr=" + e, "msread");
      return;
    }
    boolean diff = false;
    taRefLines.append("#<End>\n");
    taNotRefLines.append("#<End>\n");

    // write out ta_ref_sl.setup and ta_notref.setup
    try {
      Util.writeFileFromSB("SEEDLINK/ALL-StationList.txt", all);
      if (taRefLines.length() < 5) {
        SendEvent.edgeSMEEvent("MakeRefBad", "ALL ref lines<20 len=" + taRefLines.length() + " "+ cmdall, "msread");
        return;
      }
      RandomAccessFile rw = new RandomAccessFile("SEEDLINK/ta_ref_sl.setup", "rw");
      byte[] b = new byte[(int) rw.length()];
      rw.read(b);
      String oldRef = new String(b);
      if (!oldRef.contains(taRefLines.toString())) {
        SimpleSMTPThread.email("ketchum", "Reference list change on gacq2",
                Util.ascdate() + " " + Util.asctime() + " the reference list has changed was :\n"
                + oldRef + "\nNow:\n" + taRefLines.toString());
        diff = true;
        taRefLines.insert(0, "#\n#  List of TA reference stations made " + Util.ascdate() + " " + Util.asctime() + "\n#\n");
        rw.seek(0L);
        rw.write(taRefLines.toString().getBytes());
        rw.setLength(taRefLines.toString().length());
        Util.prta("ta_ref_sl.setup are unchanged");
      } else {
        Util.prt("old ta_ref_sl.setup name as new one " + oldRef.length() + " new="
                + taRefLines.length() + " at " + oldRef.indexOf(taRefLines.toString()));
      }
      rw.close();
      rw = new RandomAccessFile("SEEDLINK/ta_notref_sl.setup", "rw");
      b = new byte[(int) rw.length()];
      rw.read(b);
      String oldNotRef = new String(b);
      if (!oldNotRef.contains(taNotRefLines.toString())) {
        diff = true;
        taNotRefLines.insert(0, "#\n#  List of TA stations excluding reference stations made "
                + Util.ascdate() + " " + Util.asctime() + "\n#\n");
        rw.seek(0L);
        rw.write(taNotRefLines.toString().getBytes());
        rw.setLength(taNotRefLines.toString().length());
        Util.prta("ta_notref_sl.setup is unchanged");
      } else {
        Util.prt("old ta_notref_sl.setup name as new one " + oldNotRef.length()
                + " new=" + taNotRefLines.length() + " at " + oldNotRef.indexOf(taNotRefLines.toString()));
      }
      rw.close();
      if (diff) {
        Util.prta(Util.ascdate() + " Files have changed.");
        System.exit(1);
      } else {
        Util.prta(Util.ascdate() + " Files are same.");
        System.exit(0);
      }
    } catch (IOException e) {
      Util.IOErrorPrint(e, "Getting station list");
    }
  }

  public static void editProperties() {
    Util.prta("UserPropertiesPanel");
    Frame aFrame = new Frame("msread Properties");
    aFrame.addWindowListener(new WL());
    aFrame.add(new UserPropertiesPanel(), BorderLayout.CENTER);
    aFrame.setSize(700, 500);
    Util.prt("size=" + gov.usgs.anss.gui.UC.XSIZE + " " + gov.usgs.anss.gui.UC.YSIZE);
    aFrame.setVisible(true);
    while (true) {
      Util.sleep(1000);
    }
  }

  public static void findSEEDRatioForRate(double rate) {
    double diff = Double.MAX_VALUE;
    int besti = 0;
    int bestj = 0;
    //if(rate > 1.) {
    for (int i = 1; i < 32768; i++) {
      if (i % 1000 == 0) {
        Util.prta("i=" + i + " besti=" + besti + " bestj=" + bestj + " diff=" + diff);
      }
      for (int j = 1; j < 32767; j++) {
        double f = (double) i / (double) j;
        if (Math.abs(f - rate) < diff) {
          diff = Math.abs(f - rate);
          if (f > 1.) {
            besti = i;
          } else {
            besti = -i;
          }
          besti = i;
          bestj = -j;
        }
        f = (double) j * (double) i;
        if (Math.abs(f - rate) < diff) {
          diff = Math.abs(f - rate);
          if (f > 1.) {
            besti = i;
          } else {
            besti = -i;
          }
          bestj = j;
        }
        if (diff == 0.) {
          break;
        }
      }
    }
    Util.prt("Best is numerator=" + besti + " denominator=" + bestj + " diff=" + diff + " f=" + ((double) besti / (double) bestj) + " vs " + rate);
  }

  /**
   * The use of this window adapter to handle a "close" or exit is common. But the call to
   * InvTree.exitCleanup() allows that routine to check for unrecorded changes and prompt the user
   * for advice on whether to save.
   */
  static class WL extends WindowAdapter {

    @Override
    public void windowClosing(WindowEvent e) {
      Util.saveProperties();
      System.exit(0);
    }
  }
}
