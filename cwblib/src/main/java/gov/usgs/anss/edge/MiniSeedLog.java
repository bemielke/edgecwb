/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.edge;

import gov.usgs.anss.seed.MiniSeed;
import gov.usgs.alarm.SendEvent;
import gov.usgs.anss.util.*;
import gov.usgs.anss.net.*;
import gov.usgs.anss.edgeoutput.RingFile;
import gov.usgs.anss.seed.Steim1;
import gov.usgs.anss.seed.Steim2Object;
import gov.usgs.anss.seed.SteimException;
import java.util.TreeMap;
import java.util.Map;
import java.util.Collections;
import java.util.Iterator;
import java.util.ArrayList;
import java.io.*;
import java.net.*;

/**
 * Examine any MiniSeed and 1) create log files from LOG channels, 2) send last sample from any
 * non-administrative status channels to SeisNetWatch (mass position, voltages...), 3) do nothing on
 * real data from transducers. This is called by the IndexBlock write methods mostly.
 * <p>
 * If a channel is 'LOG', it is also written to the RLISS via the OutputInfrastructures. They like
 * their log channels on LISS!
 *
 * @author davidketchum
 */
public final class MiniSeedLog {

  private static final Map<String, TimedPrintStream> files
          = Collections.synchronizedMap(new TreeMap<String, TimedPrintStream>());
  private static long lastClose;
  private static SNWSender sender;    // used to send stuff to SNW via the SocketClient
  private static final ArrayList<RingFile> rliss = new ArrayList<>(10);
  private static final StringBuilder snwmsg = new StringBuilder(20);
  private static final byte[] frames = new byte[4096 - 64];
  private static final Steim2Object steim2 = new Steim2Object();
  private static final boolean saveLogInCWB = (Util.getProperty("LogToCWB") != null);
  private static char [][] excludes;      // 3 by n excludes
  public static void setRLISSOutputer(RingFile rf) {
    rliss.add(rf);
  }
  public static void setExcludeChannels(String [] exc) {
    excludes = new char[exc.length][3];
    for (int i=0; i<exc.length; i++) {
      excludes[i][0] = exc[i].charAt(0);
      excludes[i][1] = exc[i].charAt(1);
      excludes[i][2] = exc[i].charAt(2);
    }
  }
  /**
   * Creates a new instance of MiniSeedLog
   */
  public MiniSeedLog() {
  }

  /**
   * see if this MiniSeed is a LOG record. If so write it to the log areas
   *
   * @param ms The miniseed record to write
   * @return True if this was a miniseed log record, false if not
   */
  public static boolean write(MiniSeed ms) {
    // If this is a "LOG" component, write it out into console logging area
    //if(ms.getSeedNameSB().substring(7,10).equals("LOG") && ms.getNsamp() > 0) {
    char char7 = ms.getSeedNameSB().charAt(7);
    char char8 = ms.getSeedNameSB().charAt(8);
    char char9 = ms.getSeedNameSB().charAt(9);
    boolean found=false;
    if (excludes != null) {
      for (char[] exclude : excludes) {
        if (char7 == exclude[0] && char8 == exclude[1] && char9 == exclude[2]) {
          found=true;
          break;
        }
      }
      if (found) {
        return false;
      }
    }
    
    if (char7 == 'L' && char8 == 'O' && char9 == 'G'
            && ms.getNsamp() > 0) {
      String filename = SeedUtil.fileStub(ms.getYear(), ms.getDay()) + "_" + ms.getSeedNameString().substring(0, 7);
      filename = filename.trim();
      TimedPrintStream out = files.get(filename);
      if (out == null) {
        // Its not open, so open it for append and add it to files (TreeMap)
        try {
          out = new TimedPrintStream(
                  Util.getProperty("logfilepath") + "MSLOG/" + filename + ".log", true);
          Util.prta("open file " + Util.getProperty("logfilepath") + "MSLOG/" + filename + ".log" + " size=" + files.size());
          synchronized (files) {
            files.put(filename, out);
          }
        } catch (FileNotFoundException e) {
          if (e.getMessage().contains("Too many open")) {
            Util.prta("LOG File did not open - too many open files" + filename + " try closing some");
            synchronized (files) {
              Iterator itr = files.values().iterator();
              while (itr.hasNext()) {
                TimedPrintStream o = (TimedPrintStream) itr.next();
                if (System.currentTimeMillis() - o.getLastMS() > 300000L) {
                  Util.prta("Try to make space by closing logs - Close log file " + o.toString());
                  o.close();
                  itr.remove();
                }
              }
            }
          }
          Util.prta("Could not open log file=" + Util.getProperty("logfilepath") + "MSLOG/" + filename + ".log");
          Util.IOErrorPrint(e, "Log file name open error");
          return !saveLogInCWB;
        }
      }
      // Send any LOG blocks to the RLISS processors
      for (RingFile rlis : rliss) {
        //Util.prta("LOG record added to rliss="+rliss.get(i)+" ms="+ms);
        rlis.processBlock(ms.getBuf(), null);
      }

      // File is in out, put the messages into it
      out.print(ms.getTimeString().substring(5, 17) + "\n" + (new String(ms.getData(ms.getNsamp()))));

      // See if its time to try to close some of these
      if (System.currentTimeMillis() - lastClose > 3600000L) {
        lastClose = System.currentTimeMillis();
        synchronized (files) {
          Iterator itr = files.values().iterator();
          while (itr.hasNext()) {
            TimedPrintStream o = (TimedPrintStream) itr.next();
            if (System.currentTimeMillis() - o.getLastMS() > 86400000L) {
              Util.prta("Close log file " + o.toString());
              o.close();
              itr.remove();
            }
          }
        }
      }
      return !saveLogInCWB;
    } else if( (char7 == 'B'|| char7 == 'H' || char7 == 'E' || char7 == 'S' || 
            char7 == 'C' || char7 == 'D' || char7 == 'F' || char7 == 'G') && char8 == 'C') {
      return false;         // These are calibration signals
    
    } else if ( (char7 == 'B' || char7 == 'H' || char7 == 'E' || char7 == 'S' || // data channels
            char7 == 'C' || char7 == 'D' || char7 == 'L' || char7 == 'V' || char7 == 'U' || char7 == 'M' ||
            char7 == 'G' || char7 == 'F') &&
            (char8 == 'H'|| // Seismometers highgain
            char8 == 'A' || // GSN odd data
            char8 == 'B' || // Creep Meter
            char8 == 'D' || // GSN odd data
            char8 == 'F' || // Magnetometer
            char8 == 'G' || // Gravimeter
            char8 == 'J' || // Seismometers high gain rotational
            char8 == 'L' || // Seismometers low gain
            char8 == 'N' || // Accelerometer 
            //char8 == 'C' || // Calibration input, handled above
            char8 == 'O' || // Water current
            char8 == 'P' || // Geophone
            char8 == 'Q' || // Electric potential
            char8 == 'R' || // Rainfall
            char8 == 'S' || // Strain
            char8 == 'T' || // Tide
            char8 == 'U' || // Bolometer
            char8 == 'V' || // Volumetric Strain
            char8 == 'W' || // Wind
            char8 == 'X' || // GSN odd data
            char8 == 'Y' || // GSN odd data
            char8 == 'Z' || // Synthesized Beams
            char8 == '1' || // CalTech latency data
            char8 == '2' ||  // CalTech Latency data
            char8 == '3' ||  // CalTech odd data
            char8 == '4'     // CalTech odd data
        )) {          
      return false;// Magnetometer?
    // exclude problematic channels with no rates or samples.
    } else if ( char7 == 'O' ||     // Opaque packets
            (char7 == 'A' && char8 == 'C' && char9 == 'E' ||char9 == 'O') ||
            (char7 == 'N' && char8 == 'O' && char9 == 'N') ||// NON at HVO on NP stations
            char7 == 'Q') { // channels starting with Q from AVO
      //Util.prta("Ace fouind ms="); 
      return false;
    } 
    // These are GSN style status data, decompress and make last sample available to SNW
    else if ((char7 == 'A' || // Any administrative channel 
            char8 == 'E' || // Test points
            char8 == 'K' || // temperature
            char8 == 'I' || // digital on/off?
            char8 == 'C' || // Clock related
            char8 == 'W' || // Wind related
            char8 == 'M') // Mass positions
            && ms.getRate() > 0. && ms.getNsamp() > 0 // SNW bound have to have rates/samples
            ) {    // Mass positions
      // This must be a "status channel", send it to SeisNetWatch 
      if (ms.getNsamp() <= 0) {
        Util.prta("NS<=0 on " + ms.getSeedNameSB());
        SendEvent.edgeSMEEvent("ChanNotHndld", "Channel has no samples for SNW "+ms.getSeedNameSB(), "MiniSeedLog");
        return false;
      }
      if (ms.getRate() <= 0.) {
        SendEvent.edgeSMEEvent("ChanNotHndld", "Channel bad rate for SNW "+ms.getSeedNameSB()+" rt="+ms.getRate(), "MiniSeedLog");
        return false;
      }

      if (sender == null) {
        try {
          Util.prta("MiniSeedLog: open SNWSender ");
          sender = new SNWSender(100, 100);

        } catch (UnknownHostException e) {
          Util.prta("MiniSeedLog: SNWSender got a host not found going to gsnw");
          sender = null;
        }
      }

      // This is some status channel, send its last sample to seisnetwatch
      try {
        int[] data = null;
        synchronized (snwmsg) {
          System.arraycopy(ms.getBuf(), ms.getDataOffset(), frames, 0, ms.getBlockSize() - ms.getDataOffset());
          if (ms.getEncoding() == 11) {
            try {
              steim2.decode(frames, ms.getNsamp(), ms.isSwapBytes());
              data = steim2.getSamples();
              if (steim2.hadReverseError()) {
                Util.prta("MSL:" + ms.getSeedNameString() + " " + steim2.getReverseError());
              }
              if (steim2.hadSampleCountError()) {
                Util.prta("MSL:" + ms.getSeedNameString() + " " + steim2.getSampleCountError());
              }
            } catch (RuntimeException e) {
              Util.prta("RuntimeException : on Steim2 decode=" + ms.getSeedNameString() + " " + e.getMessage());
              return false;
            }
          } else if (ms.getEncoding() == 10) {
            data = Steim1.decode(frames, 1000, ms.isSwapBytes());
          }
          Util.clear(snwmsg).append(ms.getSeedNameSB().charAt(0));
          if (ms.getSeedNameSB().charAt(1) != ' ') {
            snwmsg.append(ms.getSeedNameSB().charAt(1));
          }
          snwmsg.append('-');
          for (int i = 2; i < 7; i++) {
            if (ms.getSeedNameSB().charAt(i) != ' ') {
              snwmsg.append(ms.getSeedNameSB().charAt(i));
            }
          }
          snwmsg.append(":1:");
          if( (char7 == 'L' && char8 == 'C' && char9 == 'Q') ||
              (char7 == 'A' && char8 == 'C' && char9 == 'Q')) {
            snwmsg.append("Clock Quality");
            if(ms.getSeedNameSB().charAt(10) != ' ') snwmsg.append("-").append(ms.getSeedNameSB().charAt(10));
            if(ms.getSeedNameSB().charAt(11) != ' ') snwmsg.append(ms.getSeedNameSB().charAt(11));
          }
          else {
            for (int i = 7; i < ms.getSeedNameSB().length(); i++) {
              if(ms.getSeedNameSB().charAt(i) != ' ') snwmsg.append(ms.getSeedNameSB().charAt(i));
            }
          }
          snwmsg.append("=").append(data[Math.min(999, ms.getNsamp() - 1)]).append("\n");

          //String s = ms.getSeedNameString().substring(0,2)+"-"+ms.getSeedNameString().substring(2,7).trim()+":1:"+
          //      ms.getSeedNameString().substring(7)+"="+data[Math.min(999,ms.getNsamp()-1)];
          //Util.prta("MSL:"+snwmsg+" last data="+data[Math.min(9999,ms.getNsamp()-1)]+" msss="+ms.toString().substring(0,70));
          if (sender != null) {
            //if(snwmsg.indexOf("GS-MT04") >= 0 || snwmsg.indexOf("GS-ADOK") >= 0){
            //  Util.prta("snw message : "+snwmsg);
            //}
            sender.queue(snwmsg);        // Send to seisnetwatch
          }
        }
      } catch (SteimException e) {
        Util.prt("**** steim exception decompressing end of day packet." + ms.toStringBuilder(null));
      }
      return false;
    } else {
      SendEvent.edgeSMEEvent("ChanNotHndld", "Channel not handled"+ms.getSeedNameSB(), "MiniSeedLog");
      Util.prta(" * Unhandled msss=" + ms.toStringBuilder(null, 70));
    }
    return false;
  }

}
