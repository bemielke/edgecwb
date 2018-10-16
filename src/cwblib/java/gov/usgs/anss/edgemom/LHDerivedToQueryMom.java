/*
 *  This software is in the public domain because it contains materials 
 *  that originally came from the United States Geological Survey, 
 *  an agency of the United States Department of Interior. For more 
 *  information, see the official USGS copyright policy at 
 *  http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.edgemom;

import java.util.ArrayList;
import gov.usgs.anss.edgeoutput.RingServerSeedLink;
import gov.usgs.anss.edgethread.EdgeThread;
import gov.usgs.anss.filter.LHDerivedProcess;
import gov.usgs.anss.util.Util;
import gov.usgs.anss.util.SeedUtil;
import gov.usgs.anss.filter.LHOutputer;
import gov.usgs.edge.config.Channel;
//import gov.usgs.anss.edge.TimeSeriesBlock;
import gov.usgs.anss.seed.MiniSeed;
import gov.usgs.anss.edge.IllegalSeednameException;
import gnu.trove.map.hash.TLongObjectHashMap;
import gov.usgs.alarm.SendEvent;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

/**  This class send derived 1 sps data to be sent to the memory buffers of one or more QueryMom instances.
 * It reads a configuration file with the parameters
 * described below.  It creates a RingServerSeedLink object for every line in the configuration file.  
 * <p>
 * 1) This class allows a Hydra configured to create derivedLH to forward this data to a QueryMom
 * DataLink for storage in the memory buffers. 
 * It sends data to RSSLs when the sendDataQM() function is called by the HydraOutputer.
 * <p>
 * 2) Alternatively the OutputInfrastructure process can present each MiniSEED block to this class though
 * the static processToLHFromIO() method which will do the calculation and submit the results
 * itself.  This alternative makes it so LH data can be derived even when no Hydra thread is needed.
 * To use this mode the -oi flag must be set.  It creates and manages one LHDerviedProcess for each channel.  
 * It registers itself as the LHOutputer for each channel and when the sendOutput() method is called 
 * it sends the data to all of the configured RSSLs.
 * <p>
 * The target QueryMoms must have a corresponding DataLinkToQueryServer configured to receive the data.
 * 
 * <p>
 * Example Config line in EdgeMom:
 * <br>
 * TAGTHR:[-config PATHTOCONFIG][-oi]
 *
 * <PRE>
 * switch  args               Description
 * -config configfile     Configure the RingServerSeedLink threads with the contents of this file
 * -dbg                   More output
 * -oi                    If present, data from OutputInfrastructures is processes and Hydra data is ignored
 * -nolittle              If present, the channel names are not changed to bHZ and hHZ, but to LHZ.
 * -hydra                 If present, send the derived LH data to the Hydra thread to be put on a edge wire.
 *
 *  The config file is made up of RingServerSeedLink (see that class for the latest information).
 * Here are the switchs and documentation for convenience.
 * switch     args            Description
 * -h         ipadr       The address of the ringserver datalink  (def=localhost)
 * -p         port        The datalink port number on the ringserver (def=18002)
 * -allowlog              If set LOG records will go out (normally not a good idea)
 * -qsize     nnnn        The number of miniseed blocks to buffer through the in memory queue (512 bytes each, def=1000)
 * -file      filenam     The filename of the file to use for backup buffering (def=ringserver.queued)
 * -maxdisk   nnn         amount of disk space to use in mB (def=1000 mB).
 * -allowrestricted true  If argument is 'true', then allow restricted channels
 * -allowraw              This must be set if this RSSL is being used to send raw data to a QueryMom DLTQS.
 * -xinst                 A regular expression of instances that are blocked output blocked
 * -dbg                   Set more voluminous log output
 * -dbgchan               Channel tag to match with contains
 *
 * Here is an example config file contents sending the derived LH data to two different QueryMoms:
 * CWB1:-h 192.168.0.1 -p 16099 -file /data2/derived_cwb1.ringserver
 * CWB2:-h 192.169.0.100 -p 16099 -file /data2/derived_cwb2.ringserver
 * </PRE>
 *
 * @author U.S. Geological Survey  <ketchum at usgs.gov>
 */
public final class LHDerivedToQueryMom extends EdgeThread implements LHOutputer {

  private static final ArrayList<RingServerSeedLink> rssl = new ArrayList<RingServerSeedLink>(4);
  private static boolean hydraRunning = true;
  private static final TLongObjectHashMap< LHDerivedProcess> lhp = new TLongObjectHashMap<>(1000);
  private static boolean active;
  private static MiniSeed mswork;
  private String configFile = "config" + Util.fs + "derived2querymom.setup";
  private static LHDerivedToQueryMom thisThread;
  private static boolean dbg;
  private long lastModified = 0;
  private static long nsent;
  private static boolean nolittle;
  private boolean hydra;

  public static boolean isActive() {
    return active;
  }

  private void readConfig() {
    try {
      // check to see if the file has been modified
      long lastMod = Util.isModifyDateNewer(configFile, lastModified);
      if (lastMod == 0) {
        return;
      } else if (lastMod > 0) {
        lastModified = lastMod;
      } else {
        prta("LDQM: *** config file does not exist! " + configFile);
        return;
      }
      try ( // Read in the config file
              BufferedReader in = new BufferedReader(new FileReader(configFile))) {
        String line;
        String host;
        int port;

        while ((line = in.readLine()) != null) {
          if (line.length() < 3) {
            continue;
          }
          line = line.replaceAll("  ", " ");
          line = line.replaceAll("  ", " ");
          line = line.replaceAll("  ", " ");
          String[] parts = line.split(":");
          if (parts.length != 2) {
            continue;
          }
          String tagline = parts[0];
          String[] args = parts[1].split("\\s");
          host = "";
          port = 16099;
          RingServerSeedLink thr = null;
          int index = -1;
          for (int i = 0; i < args.length; i++) {
            if (args[i].equals("-h")) {
              host = args[i + 1];
              i++;
            } else if (args[i].equals("-p")) {
              port = Integer.parseInt(args[i + 1]);
              i++;
            }
          }
          synchronized (rssl) {
            for (int i = 0; i < rssl.size(); i++) {
              Util.prt(i + " chk host=" + rssl.get(i).getHost() + "|" + host + " port=" + rssl.get(i).getPort() + "|" + port);
              if (rssl.get(i).getHost().equals(host) && rssl.get(i).getPort() == port) {
                if (tagline.charAt(0) == '#' || tagline.charAt(0) == '!') {    // need to close this its now commented
                  prta("LHDQM:Found now commented line.  Drop the matching sender " + line);
                  rssl.get(i).close();
                  rssl.remove(i);
                  continue;
                }
                thr = rssl.get(i);
                index = i;
                break;
              }
            }
            if (thr != null) {     // Got the same one, check the argument list
              if (!thr.getArgs().equals(parts[1])) {
                // Need to shut down this one  and create a new one
                prta("LHDQM:Found the line has changed.  Stop and rebuild it" + line);
                thr.close();
                rssl.set(index, new RingServerSeedLink(parts[1], tagline, this));
              }
            } else {
              if (tagline.charAt(0) == '#' || tagline.charAt(0) == '!') {
                continue;      // its a commented line
              }
              rssl.add(new RingServerSeedLink(parts[1], tagline, this));
              prta("LHDQM: Found a new line.  Create a sender " + parts[1] + "| sz=" + rssl.size());
            }

          }
        }
      }
    } catch (IOException e) {
      if (terminate && e.getMessage().equalsIgnoreCase("Socket closed")) {
        prta("LHDQM: accept socket closed during termination");
      } else {
        Util.IOErrorPrint(e, "LHDQM: accept gave unusual IOException!");
      }
    }
  }

  /**
   * terminate thread (causes an interrupt to be sure) you may not need the interrupt!
   */
  @Override
  public void terminate() {
    terminate = true;
    prta("LHDQM: interrupted called!");
    interrupt();
  }

  @Override
  public String toString() {
    return configFile + " #thr=" + rssl.size() + " #sent=" + nsent;
  }

  /**
   * return the status string for this thread
   *
   * @return A string representing status for this thread
   */
  @Override
  public StringBuilder getStatusString() {
    Util.clear(statussb);
    statussb.append("LHDQM: #client=").append(rssl.size()).append(" config=").append(configFile).append("\n");
    for (RingServerSeedLink rssl1 : rssl) {
      statussb.append("   ").append(rssl1.toStringBuilder(null)).append("\n");
    }
    return statussb;
  }
  private static StringBuilder seed = new StringBuilder(12);
  /**
   * The OI process sometimes sends straight buffers. The OI is single threaded so no
   * synchronization should be needed here.
   *
   * @param buf buffer with MiniSEED
   */
  public static void processToLHFromOI(byte[] buf) {
    //Util.prta("processToLHFromOI buf.len="+buf.length+" hydraRunning="+hydraRunning+" thisThread="+thisThread);
    if (hydraRunning || thisThread == null) {
      return;
    }
    try {
      if (mswork == null) {
        mswork = new MiniSeed(buf);
      } else {
        if (mswork.getBuf().length < buf.length) {
          mswork = new MiniSeed(buf);
        } else {
          mswork.load(buf);
        }
      }
    } catch (IllegalSeednameException e) {
      if (thisThread != null) {
        thisThread.prta("IllegalSeedname in processToLHFromOI(buf) e=" + e);
      }
    }
    //Util.prta("processToLHFromOI ms="+mswork);
    if ((mswork.getSeedNameSB().charAt(7) == 'L' && mswork.getSeedNameSB().charAt(8) == 'H')) {
      Util.clear(seed).append(mswork.getSeedNameSB());
      seed.deleteCharAt(7);
      seed.insert(7, 'B');
      LHDerivedProcess lh = lhp.get(Util.getHashFromSeedname(seed));
      if( lh != null) {
        //Util.prta("processToLHFromOI set phase "+seed+" to "+ (mswork.getTimeInMillisTruncated() % 1000)+" "+mswork);
        lh.setPhaseMS((int) (mswork.getTimeInMillis() % 1000));
        return;
      }
      else {
        seed.deleteCharAt(7);
        seed.insert(7,'H');
        lh = lhp.get(Util.getHashFromSeedname(seed));
        if(lh != null) {
          //Util.prt("processToLHFromOI set phase "+seed+" to "+ (mswork.getTimeInMillisTruncated() % 1000)+" "+mswork);
          lh.setPhaseMS((int) (mswork.getTimeInMillis() % 1000));
          return;
        }
      }
    }
    if ((mswork.getSeedNameSB().charAt(7) == 'B' || mswork.getSeedNameSB().charAt(7) == 'H') && 
            mswork.getSeedNameSB().charAt(8) == 'H'  && mswork.getRate() <= 101.) {

      // Find the processor for this channel and create it if not found
      LHDerivedProcess lh = lhp.get(Util.getHashFromSeedname(mswork.getSeedNameSB()));
      if (lh == null) {
        lh = new LHDerivedProcess((int) Math.round(mswork.getRate()), mswork.getSeedNameString(), thisThread);
        Util.clear(seed).append(mswork.getSeedNameSB());
        seed.deleteCharAt(7);
        seed.insert(7, 'L');
        Channel chn = EdgeChannelServer.getChannel(seed);
        if(chn == null) {
          thisThread.prta(seed+" LH is set no ouput "+mswork);
          lh.setNoOutput(true);
        }
        lh.setOutputer(thisThread);
        lhp.put(Util.getHashFromSeedname(mswork.getSeedNameSB()), lh);
      }
      //Util.prta("processToLHFromOI ms=" + mswork);
      lh.processToLH(mswork);         // Process the data

    }   // End if on its a BH or HH
  }

  /**
   * return the monitor string for ICINGA
   *
   * @return A String representing the monitor key value pairs for this EdgeThread
   */
  @Override
  public StringBuilder getMonitorString() {
    Util.clear(monitorsb);
    monitorsb.append("RSSLthread=").append(rssl.size());
    monitorsb.append("\n");
    long totalIn = 0;
    for (RingServerSeedLink rssl1 : rssl) {
      totalIn += rssl1.getBytesOut();
    }
    monitorsb.append("LHDQMbytesOut=").append(totalIn).append("\n");
    return monitorsb;
  }

  /**
   * Messages from LHDerivedProcess would come here if this is the registered outputer, that is if
   * OutputInfrastructure is the source of the data
   *
   * @param time Time in millis
   * @param seedname NSCL
   * @param data data buffer with nout data points
   * @param nsamp Number of samples
   */
  @Override
  public void sendOutput(long time, StringBuilder seedname, int[] data, int nsamp) {
    if (hydraRunning) {
      return;        // Data is coming from Hydra, turn this off
    }
    Util.clear(littleSeed);
    for (int i = 0; i < 7; i++) {
      littleSeed.append(seedname.charAt(i));
    }
    if(nolittle) {
      littleSeed.append("L");       // Send LH not bH or hH
    } else {
      littleSeed.append(Character.toLowerCase(seedname.charAt(7))); // Send hH or bH
    }
    for (int i = 8; i < 12; i++) {
      littleSeed.append(seedname.charAt(i));
    }

    if (thisThread != null && dbg) {
      thisThread.prta("active=" + active + " sz=" + rssl.size() + " sendOutput " + littleSeed + " " + Util.ascdatetime2(time, null) + " ns=" + nsamp
              + " sz=" + rssl.size() + " dt=" + data[0] + " " + data[1] + " " + data[2]);
    }
    if (!active) {
      return;
    }
    nsent++;

    synchronized (rssl) {
      for (RingServerSeedLink rssl1 : rssl) {
        rssl1.processRawBlockQM(littleSeed, time, 1., nsamp, data); // Write the raw data to the QueryMom
      }
    }
    if(hydra) {
      int julian = SeedUtil.toJulian(time);
      SeedUtil.fromJulian(julian, ymd);
      int doy = SeedUtil.doy_from_ymd(ymd);
      int secs = (int) (time % 86400000L / 1000);
      int usecs = (int) (time % 1000)*1000;
      prta(littleSeed + " "+ymd[0]+","+doy+" s="+secs+" usec="+usecs+" ns="+nsamp+" "+Util.ascdatetime2(time));
      Hydra.send(littleSeed, ymd[0], doy, secs, usecs, nsamp, data, 1.);
    }
  }
  private StringBuilder littleSeed = new StringBuilder(12);
  private int [] ymd = new int[3];
  /**
   * Hydra.HydraOutputers use this method to send data to the QM, if it is active then any
   * contributions from OutputInfrastructure should be ignored.
   *
   * @param seedname NSCL
   * @param time in millis
   * @param rate in Hz
   * @param nsamp number of samples ins data
   * @param data array of integer samples.
   */
  public static void sendDataQM(StringBuilder seedname, long time, double rate, int nsamp, int[] data) {
    if (!hydraRunning) {
      SendEvent.edgeSMEEvent("LHDeriveBoth", "A hydra thread is sending LHdervived when its a OI thread!", "LHDerivedToQueryMom");
      return;
    }
    if (thisThread != null && dbg) {
      thisThread.prta("acive=" + active + " sz=" + rssl.size() + " SendDataQM " + seedname + " " + Util.ascdatetime2(time, null) + " rt=" + rate + " ns=" + nsamp
              + " sz=" + rssl.size() + " dt=" + data[0] + " " + data[1] + " " + data[2]);
    }
    if (!active) {
      return;
    }
    nsent++;
    synchronized (rssl) {
      for (RingServerSeedLink rssl1 : rssl) {
        rssl1.processRawBlockQM(seedname, time, rate, nsamp, data); // Write the raw data to the QueryMom
      }
    }
  }

  /**
   * return console output - this is fully integrated so it never returns anything
   *
   * @return "" since this cannot get output outside of the prt() system
   */
  @Override
  public StringBuilder getConsoleOutput() {
    return consolesb;
  }

  public LHDerivedToQueryMom(String argline, String tg) {
    super(argline, tg);
//initThread(argline,tg);
    String[] args = argline.split("\\s");
    nolittle = false;
    hydraRunning = true;
    dbg = false;
    for (int i = 0; i < args.length; i++) {
      //prt(i+" arg="+args[i]);
      if (args[i].equals("-dbg")) {
        dbg = true;
      } else if (args[i].equals("-config")) {
        configFile = args[i + 1];
        i++;
      } // else if(args[i].equals("-h")) { host=args[i+1]; i++;}
      else if (args[i].substring(0, 1).equals(">")) {
        break;
      } else if (args[i].equals("-oi")) {
        hydraRunning = false;
      } else if (args[i].equals("-nolittle")) {
        nolittle = true;
      } else if (args[i].equals("-hydra")) {
        hydra = true;      
      } else {
        prt("LHDQM: unknown switch=" + args[i] + " ln=" + argline);
      }
    }
    prta("LHDQM: startup confg="+configFile+" nolittle="+nolittle+" hydraRunning="+hydraRunning);
    readConfig();
    setDaemon(true);
    start();
  }

  @Override
  public void run() {
    active = true;
    if(thisThread != null) {
      prta("LHDQM: **** multiple start up ");
    }
    thisThread = this;
    running = true;
    while (!terminate) {
      prt("LHDQM: " + toString());
      readConfig();
      for (int i = 0; i < 120; i++) {
        if (terminate) {
          break;
        }
        try {
          sleep(1000);
        } catch (InterruptedException expected) {
        }
      }
    }

    // Loop exited, terminate
    for (RingServerSeedLink rss1 : rssl) {
      rss1.close();
    }
    rssl.clear();
    running = false;
    active = false;
    thisThread = null;
    
  }

}
