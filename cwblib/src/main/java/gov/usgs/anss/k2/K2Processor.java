/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package gov.usgs.anss.k2;
import gov.usgs.anss.db.DBConnectionThread;
import gov.usgs.anss.edge.IllegalSeednameException;
import gov.usgs.anss.edge.MiniSeedOutputHandler;
import gov.usgs.anss.edge.RawDisk;
import gov.usgs.anss.edge.RawToMiniSeed;
import gov.usgs.anss.edgethread.EdgeThread;
import gov.usgs.anss.seed.MiniSeed;
import gov.usgs.anss.util.Util;
import java.io.File;
import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Iterator;
import java.util.TreeMap;
import kmi.altus.GenericEvent;
import kmi.smarts.lib.ParamsList;
/** This class processes K2 files as downloaded from the K2s.  It converts the data
 * to proper miniseed using input parameters for network and channel translation.
 * 
 * <br>
 * <PRE>
  * -db dbstring  A DBServer property type string for the DB to use
  * -mysql ip.adr The MySQLServer property to use for DB access (obsolete)
  * -source NN    A two digit source code to put with the triggers (def=UN)
  * -outdir PATH  The output directory to use for files
  * -dbg          turn debug output on
  * -n NN         The network code to assign to this data (def=GS)
  * -c CC-Z12:CC-Z12 The decode string for channels CC normally is "HN"
  * </PRE>
  *
 * @author davidketchum
 */
public class K2Processor extends EdgeThread implements MiniSeedOutputHandler {
  private static final TreeMap<String, RawDisk> files = new TreeMap<String, RawDisk>();
  public static final String [] unitTypes = {"K2","Etna","MW","Makalu",ParamsList.PRODUCTID};
  public static final DecimalFormat df2 = new DecimalFormat("00");
  public static final DecimalFormat df3 = new DecimalFormat("000");
  /**
   *
   * @return
   */
  //private RawDisk msout;
  //private int iblk;
  private boolean dbg;
  private String outdir;
  private long lastClosePass;
  @Override
  public StringBuilder getMonitorString() {return monitorsb;}
  @Override
  public StringBuilder getStatusString() {return statussb;}
  @Override
  public StringBuilder getConsoleOutput() {return consolesb;}
  @Override
  public void terminate() {}
  public K2Processor(String argline, String tg) {
    super(argline, tg);
     int istart =0;      // index of first file on the list
    int [] data = new int[50000];
    GregorianCalendar gc = new GregorianCalendar();
    GregorianCalendar trigtime = new GregorianCalendar();
    String assignNetwork="GS";
    String [] assignComponents={"1Z2"};
    String [] assignChannels={"HN"};
    lastClosePass = System.currentTimeMillis();
    outdir="";
    if(argline.contains(">")) argline = argline.substring(0,argline.indexOf(">") -1).trim();
    String [] args = argline.split("\\s");
    RawToMiniSeed.setStaticOutputHandler((K2Processor) this);
    String source="UN";
       
    for(int i=0; i<args.length; i++) {
      switch (args[i]) {
        case "-dbg":
          istart=i+1;
          dbg=true;
          break;
        case "-n":
          assignNetwork=args[i+1];
          istart=i+2;
          i++;
          break;
        case "-c":
          assignComponents=args[i+1].split(":");
          assignChannels = new String[assignComponents.length];
          for(int j=0; j<assignComponents.length; j++) {
            String [] parts = assignComponents[j].split("-");
            if(parts.length != 2) {
              System.err.println("-c should be format CC-Z12:CC-Z12");
              System.exit(0);
            }
            assignChannels[j]=parts[0];
            assignComponents[j]=parts[1];
            prt("Assign channel group "+j+"="+assignChannels[j]+" "+assignComponents[j]);
            if(assignChannels[j].charAt(0) != 'H' && assignChannels[j].charAt(0) != 'B' &&
                    assignChannels[j].charAt(0) != 'S' && assignChannels[j].charAt(0) != 'E') {
              System.err.println("Channel first character {band code} is not recognized="+
                      assignChannels[j].charAt(0)+" [HBSE legal]");
              System.exit(1);
            }
            if(assignChannels[j].charAt(1) != 'L' && assignChannels[j].charAt(1) != 'N' &&
                    assignChannels[j].charAt(1) != 'H' ) {
              System.err.println("Channel 2nd character {instrument code} is not recognized="+
                      assignChannels[j].charAt(0)+" [LNH legal]");
              System.exit(1);
            }
            for(int k=0; k<assignComponents[j].length(); k++)
              if(assignComponents[j].charAt(k) != 'Z' && assignComponents[j].charAt(k) != 'N' &&
                      assignComponents[j].charAt(k) != 'E' && assignComponents[j].charAt(k) != '1' &&
                      assignComponents[j].charAt(k) != '2') {
                System.err.println("Orientation character at position "+k+" is not recognized="+
                        assignComponents[j].charAt(k)+" [ZNE12 legal]");
                System.exit(1);
              }
          } istart=i+2;
          i++;
          break;
        case "-db":
          Util.setProperty("DBServer", args[i+1]);
          istart = i+2;
          i++;
          break;
        case "-mysql":
          Util.setProperty("MySQLServer", args[i+1]);
          istart = i+2;
          i++;
          break;
        case "-source":
          source=args[i+1];
          istart = i+2;
          i++;
          break;
        case "-outdir":
          outdir=args[i+1];
          istart = i+2;
          i++;
          break;
      }
    }

        
    DBConnectionThread dbconn =null;
    if(dbconn == null) {
      try {
        dbconn = new DBConnectionThread(Util.getProperty("DBServer"),"update", "portables",
            false,false,"Portables", getPrintStream());
        addLog(dbconn);
        if(!DBConnectionThread.waitForConnection("Portables"))
          if(!DBConnectionThread.waitForConnection("Portables")) prta("MDRun: Did not connect to DB promptly Portables");
      }
      catch(InstantiationException e) {
        Util.prta("InstantiationException opening edge database in MetaDataServer e="+e.getMessage());
        System.exit(1);
      }
    }

    for(int ifile=istart; ifile<args.length; ifile++) {
      GenericEvent evt = new GenericEvent();
      StringBuffer emsg = new StringBuffer();
      File file = new File(args[ifile]);
      prt("start processing ="+args[ifile]+" "+file.exists());
      try {
        if(evt.loadFromFile(args[ifile])) {
          if (evt.readTimeSeriesFromFile(args[ifile], emsg)) {
            // load the file into the object.
            gc.setTimeInMillis(evt.getStartTime()-(long)(evt.getGpsLocalOffset()*3600000.));
            trigtime.setTimeInMillis(evt.getTriggerTime()-(long)(evt.getGpsLocalOffset()*3600000.));
            // Print out the event stuff.
            prt(args[ifile]+" Site="+evt.getSiteID()+" stat="+evt.getStationID()+" s/n="+evt.getSerialNumber()+
                    " type="+unitTypes[evt.getUnitType()]+" "+(evt.getAntiAliasFilter() == 0 ? "Non-Causal": "Causal")+
                    //" start="+Util.ascdate(gc)+" "+Util.asctime2(gc)+" UTC "+
                    " trig="+Util.ascdate(trigtime)+" "+Util.asctime2(trigtime)+
                    " UTC locCorr="+evt.getGpsLocalOffset()+" hrs #smp="+evt.getScanCount()+
                    " #chaU/I/Mx="+evt.getUsedChannels()+"/"+evt.getInstalledChan()+"/"+evt.getMaxChannels()+
                    " dur="+evt.getDuration()+"/"+evt.getPreEvent()+" Batt="+evt.getBatteryVoltage()+
                    "V boot=B"+evt.getBootVersion()+"/A"+evt.getAppVersion());
            // ic is the raw channel count, i is the mapped channel or channel index
            int [] map = new int[evt.getUsedChannels()];

            // Analyze the altitude and azimuth of the channels to insure orientation string for this component group is right
            String alt = "Alt : ";
            String azm = "Azm : ";
            for(int ic=0; ic< evt.getUsedChannels(); ic++) {
              map[ic] = evt.channelIndex(ic)-1;
              alt += Util.leftPad(""+evt.getSensorAltitude(map[ic]),4)+" ";
              azm += Util.leftPad(""+evt.getSensorAzimuth(map[ic]),4)+" ";
            }
            if(evt.getUsedChannels() >= 3) {
              String expect = "";
              if(evt.getSensorAltitude(map[1]) == evt.getSensorAltitude(map[2]) &&        // This normally means they did not set it
                      evt.getSensorAltitude(map[0]) == evt.getSensorAltitude(map[1]))  {
                expect="N/A";
              }
              else if(evt.getSensorAltitude(map[1]) == evt.getSensorAltitude(map[2])) {
                expect = "Z12";
              }
              else if(evt.getSensorAltitude(map[0]) == evt.getSensorAltitude(map[2])) {
                expect = "1Z2";
              }
              else if(evt.getSensorAltitude(map[0]) == evt.getSensorAltitude(map[1])) {
                expect = "12Z";
                if(assignComponents[0].equals("21Z")) expect = "21Z";
              }
              prt(alt+" -> "+expect);
              prt(azm);
              if(expect.equals("N/A")) prt(" ** Warning : channel map does not make sense group=1!");
              else if(!expect.equals(assignComponents[0])) {
                prt(" **** Computed channel map="+expect+" does not match command line map="+Arrays.toString(assignComponents));
                System.err.println("\n\n **** Computed channel map="+expect+" does not match command line map="+
                        (assignComponents.length > 0?assignComponents[0]:"Null")+":"+
                        (assignComponents.length > 1?assignComponents[1]:"Null")
                        +" at file="+args[ifile]+"\n");
                System.exit(1);
              }
              if(evt.getUsedChannels() >= 6) {
                expect = "";
                if(evt.getSensorAltitude(map[4]) == evt.getSensorAltitude(map[5]) &&        // This normally means they did not set it
                        evt.getSensorAltitude(map[3]) == evt.getSensorAltitude(map[4]))  {
                  expect="N/A";
                }
                else if(evt.getSensorAltitude(map[4]) == evt.getSensorAltitude(map[5])) {
                  expect = "Z12";
                }
                else if(evt.getSensorAltitude(map[3]) == evt.getSensorAltitude(map[5])) {
                  expect = "1Z2";
                }
                else if(evt.getSensorAltitude(map[3]) == evt.getSensorAltitude(map[4])) {
                  expect = "12Z";
                }
                prt(alt+" -> "+expect);
                prt(azm);
                if(expect.equals("N/A")) prt(" ** Warning : channel map does not make sense group=2!");
                else if(!expect.equals(assignComponents[1])) {
                  prt(" **** Computed channel map="+expect+" does not match command line map="+Arrays.toString(assignComponents));
                  System.err.println("\n\n **** Computed channel map="+expect+" does not match command line map="+Arrays.toString(assignComponents)+" at file="+args[ifile]+"\n");
                  System.exit(1);
                }
              }
              else if(evt.getUsedChannels() == 4) {     // Group of 3 plus a vertical
                if(!assignComponents[1].substring(0,1).equals("Z")) {
                  prt(" **** Group 2 of 1 channel expect to be 'Z' does not match command line map="+assignComponents[1]);
                  System.err.println("\n\n **** Computed channel 'Z' does not match command line map="+assignComponents[1]+" at file="+args[ifile]+"\n");
                  System.exit(1);

                }
              }
            }

            // ic is the raw channel count, i is the mapped channel or channel index
            for(int ic=0; ic< evt.getUsedChannels(); ic++) {
              int i = evt.channelIndex(ic)-1;
              if(i < 0 ) {prt("** Bad index channel="+ic+"->"+i);System.err.println("** bad index channel="+ic+"->"+i);i = ic;} // What is going on, i should always be >1?
              //prt("Preamp gains must be "+(evt.getFullScale(i)/2.5));
              String topDir = args[ifile].substring(0,args[ifile].indexOf("/"));
              String station= (evt.getStationID()+"     ").substring(0,5);
              if(evt.getStationID().trim().equals("")) {
                prta(" **No station name in event file, use the top directory name "+args[ifile]);
                System.err.println(" **No station name in event file, use the top directory name "+args[ifile]);
                if(args[ifile].contains("/"))
                  station = args[ifile].substring(0,args[ifile].indexOf("/"));
                else {
                  prta(" *** Station not available use unit number of "+evt.getSerialNumber());
                  System.err.println("*** Station not available use unit number of "+evt.getSerialNumber()+" "+args[ifile]);
                  station = ""+evt.getSerialNumber();
                }
                station = (station+"     ").substring(0,5);
              }
              if(!topDir.equals(station.trim())) {
                prt(" ****** K2 station and the top level directory name do not agree, use directory name");
                System.err.println("****** K2 station and the top level directory name do not agree, use directory name"+args[ifile]+" stat="+station);
                station=(topDir+"    ").substring(0,5);
              }
              int chanGroup = ic/3;

              if(evt.getSampleRate() >= 80. && assignChannels[chanGroup].charAt(0) != 'H' && assignChannels[chanGroup].charAt(0) != 'E') {
                prt("Sample rate and band code do not agree. ch="+ic+" rate="+evt.getSampleRate()+" code="+assignChannels[chanGroup].charAt(0));
                System.err.println("Sample rate and band code do not agree. ch="+ic+" rate="+evt.getSampleRate()+" code="+assignChannels[chanGroup].charAt(0)+" "+args[ifile]);
                System.exit(1);
              }
              if(evt.getSampleRate() >=10.&& evt.getSampleRate() < 80. &&  assignChannels[chanGroup].charAt(0) != 'B' && assignChannels[chanGroup].charAt(0) != 'S') {
                prt("Sample rate and band code do not agree. ch="+ic+" rate="+evt.getSampleRate()+" code="+assignChannels[chanGroup].charAt(0));
                System.err.println("Sample rate and band code do not agree. ch="+ic+" rate="+evt.getSampleRate()+" code="+assignChannels[chanGroup].charAt(0)+" "+args[ifile]);
                System.exit(1);
              }
              String [] parts = args[ifile].split("/");
              String eventFile=parts[parts.length-1].trim();

              String orgEventDir = "";
              if(parts.length > 1) {
                orgEventDir="";
                for(int k=0; k<parts.length-1; k++) orgEventDir=orgEventDir + parts[k]+"/";
              }

              String location=(evt.getChannelLocationCode(i)+"  ").substring(0,2);
              String seedname = assignNetwork+station+assignChannels[chanGroup]+assignComponents[chanGroup].charAt(ic % 3)+location;
              StringBuilder seednameSB = new StringBuilder(12).append(seedname);
              prt(seedname+"K2Ch: "+evt.getSerialNumber()+" sta="+evt.getStationID()+" chID="+evt.getChannelID(i)+
                      " loc="+evt.getChannelLocationCode(i)+" net="+evt.getChannelNetworkCode(i)+
                      " rt="+evt.getSampleRate()+
                      " chnum="+ic+"->"+i+
                      " FS="+evt.getFullScale(i)+"/"+evt.getFullScaleADC(i)+
                      " Sensor:"+evt.getSensorType(i)+"->"+GenericEvent.intToSensorType(evt.getSensorType(i))+
                      (evt.getSensorType(i) == 32 ?" rangeCode="+evt.getSensorRangeCode(i)+" "+GenericEvent.intToGainCodeString(evt.getSensorRangeCode(i)):"")+
                      " gain="+evt.getSensitivity(i)+"V/g freq="+evt.getNaturalFrequency(i)+
                      " azm="+evt.getSensorAzimuth(i)+" alt="+evt.getSensorAltitude(i)+
                      " Gain="+evt.getSensorGain(i)+" S/N="+evt.getSensorSN(i)+
                      " Filt="+(evt.getAntiAliasFilter() == 0 ? "Non-Causal": "Causal")
                      );
              prt("GPS: alt="+evt.getGpsAltitude()+" lat="+evt.getGpsLatitude()+
                      " lng="+evt.getGpsLongitude()+" drift="+evt.getGPSDrift(0)+
                      " lastLock="+evt.getGpsLastLock(0)/1000+" s"+" localoffset="+evt.getGpsLocalOffset()+" hr");
              // Get mapped channel index
              // Create an array to store the data
              // Scaling factor (+/- 20v full scale)
              // getEvtData gives data in Millivolts, factor converts millivolts back to counts.
              double factor = 8388608.0 / (evt.getFullScale(i)*1000.);  // counts per millivolt
              /*evt.setEvtDataCounts(ic, 1, 20000);
              double val = evt.getEvtData(ic,1);
              double val2 = val*factor;*/

              if(data.length < evt.getScanCount()) data = new int[evt.getScanCount()*2];
                      for (int six=0; six<evt.getScanCount(); six++)
                        data[six] = (int) (evt.getEvtData(ic, six)*factor);
              if(dbg) {
                for(int in=2000; in<2100; in=in+20) {
                  String s = Util.leftPad(""+in,5)+":";
                  for(int j=in; j<in+20; j++) s += Util.leftPad(data[j]+"", 6);
                  prt(s);
                }
              }

              // Put this into the database as a trigger if its a vertical
              if(seedname.substring(9,10).equals("Z")) {
                try {
                 if(eventFile.length() > 12) eventFile=eventFile.substring(0,12);
                 ResultSet rs = dbconn.executeQuery("SELECT * FROM portables.k2trigger WHERE seedname='"+seedname.trim()+
                          "' AND evtfile='"+eventFile.replaceAll(".EVT","")+
                          "' AND ABS(DATEDIFF('"+Util.ascdate(trigtime)+"',trigtime)) < 2");
                  if(rs.next()) {
                    int ID=rs.getInt("id");
                    rs.close();
                    dbconn.executeUpdate("UPDATE portables.k2trigger SET trigtime='"+
                            Util.ascdate(trigtime)+" "+Util.asctime2(trigtime)+"',source='"+source+"',updated=now() WHERE id="+ID);
                  }
                  else {
                    rs.close();
                    dbconn.executeUpdate(
                            "INSERT INTO portables.k2trigger (seedname,trigtime,evtfile,source,updated,created_by, created) VALUES ('"+
                            seedname+"','"+Util.ascdate(trigtime)+" "+Util.asctime2(trigtime)+"','"+
                            eventFile.replaceAll(".EVT","")+"','"+source+"',now(),0, now())");
                  }
                }
                catch(SQLException e) {
                  e.printStackTrace(getPrintStream());
                  e.printStackTrace(System.err);
                  System.exit(2);
                }
              }
              int year = gc.get(Calendar.YEAR);
              int doy = gc.get(Calendar.DAY_OF_YEAR);
              int sec = (int) (gc.getTimeInMillis() % 86400000L);
              int micros = (sec % 1000) *1000;
              sec = sec / 1000;
              int activity=64;    // event in progress
              int quality=0;
              int timingQ= 100 - (int) ((gc.getTimeInMillis() - evt.getGpsLastLock(0))/600000);
              if(timingQ < 0) timingQ=0;
              int IOClock=(timingQ >= 97?32:0);
              if(outdir.equals("")) outdir = orgEventDir;
              else if(!outdir.endsWith("/")) outdir += "/";
              /*String filename=(outdir+year+df3.format(doy)+
                      df2.format(gc.get(Calendar.HOUR_OF_DAY))+df2.format(gc.get(Calendar.MINUTE))+df2.format(gc.get(Calendar.SECOND))+"_"+
                      seedname+"_"+eventFile.replaceAll(".EVT","")+".ms").replaceAll(" ","_");
              msout = new RawDisk(filename,"rw");
              iblk=0;*/
              RawToMiniSeed.addTimeseries(data, evt.getScanCount(), seednameSB,
                      year, doy, sec, micros, evt.getSampleRate(),
                      activity, IOClock,quality,timingQ, (K2Processor) this);
              RawToMiniSeed.forceout(seednameSB);
              RawToMiniSeed.forceStale(-1);
              /*msout.close();
              prt(iblk+" blocks written to "+filename);*/
            }
            prt("");
          }
          else {prt("load TimeseriesFromFile() returned false");}
        }
        else {prt("loadFromFile() returned false");}
      }
      catch(IOException e) {
        prt("Could not open the file or I/O error "+args[ifile]);
        System.err.println("Could not open the file or I/O error "+args[ifile]);
        e.printStackTrace(getPrintStream());
      }
      catch(RuntimeException e) {
        prt("Runtime emsg="+emsg);
        e.printStackTrace(getPrintStream());
        e.printStackTrace(System.err);
      }
      evt.cleanupBeforeExit();
    }

  }
  @Override
  public void close() {
    prt("  ********* Surprise - RawToMiniSeed called close()");
  }
  @Override
  public void putbuf(byte [] buf, int len) {
    try {
      MiniSeed ms = new MiniSeed(buf, 0, len);
      String file = ms.getTimeString().substring(0,7).replaceAll(" ","_")+"0_"+ms.getSeedNameSB().substring(0,12).replaceAll(" ","_");
      RawDisk msout = files.get(file);
      if(msout == null) {
        msout = new RawDisk((outdir+"/"+file+".ms").replaceAll("//","/"), "rw");
        prta("Open : "+(outdir+"/"+file+".ms").replaceAll("//","/"));
        msout.setLength(0L);      // always a new file if opened during run.
        files.put(file, msout);
        if(System.currentTimeMillis() - lastClosePass > 60000) {
          long now = System.currentTimeMillis();
          lastClosePass = now;
          Iterator<RawDisk> itr = files.values().iterator();
          while(itr.hasNext()) {
            RawDisk rw =  itr.next();
            if(now - rw.getLastUsed() > 60000) {
              prta("Close :  "+rw.getFilename());
              itr.remove();
            }
          }
        }
      }
      int iblk = (int) (msout.length()/512);
      msout.writeBlock(iblk, buf, 0, len);
    }
    catch(IOException e) {
            prt("cannot open output file e="+e);

    }
    catch(IllegalSeednameException e) {
      Util.prt("Unknown exception ");
      e.printStackTrace();
    }
  }
  public static void  main (String [] args) {
    Util.setModeGMT();
    Util.setNoInteractive(false);
    Util.setNoconsole(true);
    Util.init();
    Util.setProperty("logfilepath", "");
    String argline="";
    int istart=-1;
    String assignNetwork="GS";
    String assignComponents="1Z2";
    String logfile="k2seed";
    for(int i=0; i<args.length; i++) {
      switch (args[i]) {
        case "-dbg":
          argline += args[i]+" ";
          istart=i+1;
          break;
        case "-n":
          assignNetwork=args[i+1];
          istart=i+2;
          i++;
          break;
        case "-c":
          assignComponents=args[i+1];
          istart=i+2;
          i++;
          break;
        case "-logdir":
          Util.setProperty("logfilepath", args[i+1]+"/");
          istart=i+2;
          i++;
          break;
        case "-log":
          logfile=args[i+1];
          istart=i+2;
          i++;
          break;
        case "-outdir":
          argline += args[i]+" "+args[i+1]+" ";
          istart=i+2;
          i++;
          break;
        case "-mysql":
          argline += args[i]+" "+args[i+1]+" ";
          istart=i+2;
          i++;
          break;
        case "-source":
          argline += args[i]+" "+args[i+1]+" ";
          istart=i+2;
          i++;
          break;
        case "-db":
          argline += args[i]+" "+args[i+1]+" ";
          istart = i+2;
          i++;
          break;
      }
    }
    argline = argline + " -n "+assignNetwork+" -c "+assignComponents;
    for(int ifile=istart; ifile<args.length; ifile++) argline += " "+args[ifile];
    K2Processor doit = new K2Processor(argline.trim()+" >> "+logfile, "K2P");
    doit.terminate();
    DBConnectionThread.shutdown();
    Util.prt("End of Execution");
    System.exit(0);
  }
}
