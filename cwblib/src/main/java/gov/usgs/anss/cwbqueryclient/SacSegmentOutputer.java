/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */

package gov.usgs.anss.cwbqueryclient;

import edu.sc.seis.TauP.SacTimeSeries;
import gov.usgs.adslserverdb.InstrumentType;
import gov.usgs.anss.PGM.PGM;
import gov.usgs.anss.PGM.SM_INFO;
import gov.usgs.anss.seed.MiniSeed;
import gov.usgs.anss.util.PNZ;
import gov.usgs.anss.util.Util;
import java.io.*;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import kmi.seismic.COSMOSDataHolder;
import kmi.tools.FileBasedTempDataArray;


/** Take the CWBQuery list of MiniSEED blocks and create a SAC file(s) from each segment with some options.
 * <PRE>
 * switch  args   Description
 * -fill   value  Change value from the SAC default of -12345
 * -sacpz         If present, write out the SAC style Poles and Zeros file for the instrument stages
 * -nogaps        If any gap is present, return nothing
 * -sactrim       If present, do not fill out and end-of-interval gap with nodata values, just shorten the nsamps
 * -nometa        If present, do not populate the SAC header with coordinates, etc from the MetaDataServer
 * -pgm   level   Set the minimum level of acceleration needed to write out the file in % g.
 * -q             Quiet - less output
 * -derivelh      If present, change the input sample rate to 1 hz after lowpass and decimage
 * -cosmos        Create a COSMOS file instead of a SAC file
 * </pre>
 * @author davidketchum
 */
public class SacSegmentOutputer extends Outputer {
   static final String styleString = ".textheader\n"+
         "12345678901234567890123456789012345678901234567890123456789012345678901234567890\n"+
         "                          (Format v01.20 with    text lines)\n"+
         "\n"+
         "HypoCenter:                     H=   km\n"+
         "Origin:                        UTC\n"+
         "Statn No:   -       Code:  -\n"+
         "Coords:                    Site geology:\n"+
         "Recorder:        s/n      (   Chns of     at Sta) Sensor:         s/n\n"+
         "Rcrd start time:                             (Q= ) RcrdId:\n"+
         "Sta Chan   :         (Rcrdr Chan   ) Location:\n"+
         "Raw record length =         sec, Uncor max =          , at          sec.\n"+
         "Processed:                               Max =                    at         sec\n"+
         "Record filtered below       Hz (periods over       secs), and above      Hz\n"+
         "Values used when parameter or data value is unknown/unspecified:       ,\n"+
         ".integerheader\n"+
         "12345678901234567890123456789012345678901234567890123456789012345678901234567890\n"+
         "     Integer-header values follow on     lines, Format=\n"+
         ".realheader\n"+
         "12345678901234567890123456789012345678901234567890123456789012345678901234567890\n"+
         "     Real-header values follow on     lines, Format=\n"+
         ".commentheader\n"+
         "12345678901234567890123456789012345678901234567890123456789012345678901234567890\n"+
         "     Comment line(s) follow, each starting with a \"|\":\n"+
         "|SEED Chan: CCC    L-code: LL\n"+
         ".dataheader\n"+
         "12345678901234567890123456789012345678901234567890123456789012345678901234567890\n"+
         "                         , approx      secs, units=       (  ),Format=\n"+
         ".trailer\n"+
         "12345678901234567890123456789012345678901234567890123456789012345678901234567890\n"+
         "End-of-data for chan\n"+
         ".set SOURCEAGENCY \n"+
         ".set NETWORKNUMBER 1\n"+
         ".set NETWORKCODE CE\n"+
         ".set NETWORKABBREV CDMG\n";
  static final DecimalFormat df2 = new DecimalFormat("00");
  static final DecimalFormat df3 = new DecimalFormat("000");
  static final DecimalFormat df4 = new DecimalFormat("0000");
  boolean dbg;
  private static SacPZ stasrv;
  private static String stasrvHost;
  private static int stasrvPort;
  private SacTimeSeries sac;

  /** Creates a new instance of SacOutputer */
  public SacSegmentOutputer() {
  }
  public SacTimeSeries getSacTimeSeries() {return sac;}
   @Override
  public void makeFile(String lastComp, String filename, String filemask, ArrayList<MiniSeed> blks,
      java.util.Date beg, double duration, String [] args) throws IOException {

    // Process the args for things that affect us
    if(blks.isEmpty()) return;    // no data to save
    boolean nogaps=false;       // if true, do not generate a file if it has any gaps!
    int fill=2147000000;
    boolean sacpz=false;
    boolean noStaSrv=false;
    boolean quiet=false;
    boolean pgm=false;
    double pgmLevel=0.;
    boolean sactrim=false;      // return full length padded with no data value
    String pzunit="nm";
    boolean deriveLH=false;
    String newComp = lastComp;
    double rate=0;
    boolean cosmos=false;
    for(int i=0; i<blks.size(); i++)
      if(blks.get(i).getNsamp() > 0 && blks.get(i).getRate() > 0.001)
      {newComp=blks.get(i).getSeedNameString(); rate = blks.get(i).getRate(); break;}

    String orgFilename=filename;
    String stahost = Util.getProperty("metadataserver");
    int staport=2052;
    for(int i=0; i<args.length; i++) {
      if(args[i].equals("-fill")) fill = Integer.parseInt(args[i+1]);
      if(args[i].equals("-nogaps")) {fill = 2147000000;nogaps=true;}
      if(args[i].equals("-nometa")) noStaSrv=true;
      if(args[i].equals("-sactrim")) sactrim=true;
      if(args[i].equals("-pgm")) {pgm=true; pgmLevel = Double.parseDouble(args[i+1]);}
      if(args[i].equals("-q")) quiet=true;
      if(args[i].equalsIgnoreCase("-derivelh")) deriveLH=true;
      if(args[i].equals("-cosmos")) cosmos=true;
      if(args[i].equals("-sacpz")) {
        sacpz=true;
        pzunit=args[i+1];
        if(stahost == null || stahost.equals("")) stahost="137.227.230.1";
      }
    }
    if(stahost.equals("")) noStaSrv=true;
    if( !noStaSrv && (stasrv == null || !stahost.equals(stasrvHost) || stasrvPort != staport)) {
      stasrv = new SacPZ(stahost, pzunit);
      stasrvHost=stahost;
      stasrvPort = staport;
    }
    // Use the span to populate a sac file
    GregorianCalendar start = new GregorianCalendar();
    start.setTimeInMillis(beg.getTime());

    // build the zero filled area (either with exact limits or with all blocks)
    if(deriveLH && (newComp.charAt(7) == 'C' || newComp.charAt(8) == 'I')) start.setTimeInMillis(beg.getTime()+ 27000);
    DCC512Outputer dcc512 = new DCC512Outputer();
    String [] args2= new String[1];
    args2[0]="-nooutput";
    dcc512.makeFile(lastComp, filename, filemask, blks, beg, duration, args2);
    ArrayList<MiniSeed> outblks = dcc512.getOutblocks();
    blks = outblks;
    int iblk=0;
    ArrayList<MiniSeed> segblks = new ArrayList<MiniSeed>(1000);
    long maxTime=0;
    GregorianCalendar trigTime = new GregorianCalendar();
    for(;;) {
      if(iblk >= blks.size() || (segblks.size() > 0 &&
                (blks.get(iblk).getTimeInMillis() - maxTime ) >= 1000/rate)) {  // As long as its the next sampe or before it

        ZeroFilledSpan span = new ZeroFilledSpan(segblks, segblks.get(0).getGregorianCalendar(),
                (segblks.get(segblks.size()-1).getNextExpectedTimeInMillis() - segblks.get(0).getTimeInMillis())/1000., fill);
        if(span.getRate() <= 0.00) return ;         // There is no real data to put in SAC
        if(dbg) Util.prt("ZeroSpan="+span.toString());
        int noval= span.getNMissingData();

        if(nogaps && span.hasGapsBeforeEnd()) {Util.prt("  ** "+lastComp+" has gaps - discarded # missing ="+noval);return;}
        if(filemask.contains("%x")) {
          filename = EdgeQueryClient.makeFilename(filemask, segblks.get(0).getSeedNameString(), segblks.get(0) , deriveLH);
        }
        else {
          filename = orgFilename.replaceAll("[__]", "_");
          GregorianCalendar st = segblks.get(0).getGregorianCalendar();
          filename += "_"+st.get(Calendar.YEAR)+df2.format(st.get(Calendar.MONTH)+1)+df2.format(st.get(Calendar.DAY_OF_MONTH))+
                  "_"+df2.format(st.get(Calendar.HOUR_OF_DAY))+df2.format(st.get(Calendar.MINUTE))+df2.format(st.get(Calendar.SECOND));
        }

        if(filemask.equals("%N")) filename += ".sac";

        //ZeroFilledSpan span = new ZeroFilledSpan(blks);
        String sacpzString="";
        double [] coord = new double[3];
        double [] orient = new double[3];
        String instrumentUnit="";
        String instrumentType="";
        String comment="";
        double instrumentGain=0.;
        double sensSeed=0.;
        double sensCalc=0.;
        double a0Seed=0.;
        double a0Calc=0.;
        double constant=0.;
        String longName="";
        for(int i=0; i<3; i++) {
          orient[i]=  SacTimeSeries.DOUBLE_UNDEF;
          coord[i] =  SacTimeSeries.DOUBLE_UNDEF;
        }
        PNZ pnz = null;
        String pz=null;
        if(!noStaSrv) {
          //coord = stasrv.getCoord(lastComp.substring(0,2),
          //    lastComp.substring(2,7),lastComp.substring(10,12));
          // orientation [0] = azimuth clwse from N, [1]=dip down from horizontl, [2]=burial depth
          //orient = stasrv.getOrientation(lastComp.substring(0,2),
          //    lastComp.substring(2,7),lastComp.substring(10,12),lastComp.substring(7,10));
          String time = blks.get(0).getTimeString();
          time = time.substring(0,4)+","+time.substring(5,8)+"-"+time.substring(9,17);

          String s;
          if(sacpz) s =stasrv.getSACResponse(lastComp, time, filename.replaceAll(".sac","")+".sac");  // write out the file too
          else s = stasrv.getSACResponse(lastComp, time);
          sacpzString = s;
          pz=s;
          int loop=0;
          while (s.contains("MetaDataServer not up")) {
            if(loop++ % 15 == 1) Util.prta("MetaDataServer is not up - waiting for connection");
            try{Thread.sleep(2000);} catch(InterruptedException e) {}
            s = stasrv.getSACResponse(lastComp, time, filename);
            pz=s;
          }
          try {
            BufferedReader in = new BufferedReader(new StringReader(s));
            String line;
            while( (line = in.readLine()) != null ) {
              if(line.indexOf("LAT-SEED") > 0) coord[0] = Double.parseDouble(line.substring(15));
              else if(line.indexOf("LONG-SEED") > 0) coord[1] = Double.parseDouble(line.substring(15));
              else if(line.indexOf("ELEV-SEED") > 0) coord[2] = Double.parseDouble(line.substring(15));
              else if(line.indexOf("AZIMUTH") > 0) orient[0] = Double.parseDouble(line.substring(15));
              else if(line.indexOf("DIP") > 0) orient[1] = Double.parseDouble(line.substring(15));
              else if(line.indexOf("CONSTANT") == 0) constant = Double.parseDouble(line.substring(15).trim());
              else if(line.indexOf("SENS-SEED") > 0) sensSeed = Double.parseDouble(line.substring(15));
              else if(line.indexOf("SENS-CALC") > 0) sensCalc = Double.parseDouble(line.substring(15));
              else if(line.indexOf("A0-SEED") > 0) a0Seed = Double.parseDouble(line.substring(15));
              else if(line.indexOf("A0-CALC") > 0) a0Calc = Double.parseDouble(line.substring(15));
              else if(line.indexOf("INSTRMNTUNIT") > 0) instrumentUnit = line.substring(15).trim();
              else if(line.indexOf("INSTRMNTGAIN") > 0) instrumentGain = Double.parseDouble(line.substring(15).trim());
              else if(line.indexOf("INSTRMNTCMNT") > 0) comment = line.substring(15).trim();
              else if(line.indexOf("INSTRMNTTYPE") > 0) instrumentType = line.substring(15).trim();
              else if(line.indexOf("DESCRIPTION") > 0) longName = line.substring(15).trim();
            }
            //Util.prt("coord="+coord[0]+" "+coord[1]+" "+coord[2]+" orient="+orient[0]+" "+orient[1]+" "+orient[2]);
          }
          catch(IOException e) {
              Util.prta("OUtput error writing sac response file "+lastComp+".resp e="+e.getMessage());
          }
        }

        if(cosmos) {
          MiniSeed msbeg = null;
          for(int i=0; i<blks.size(); i++) if(blks.get(i).getNsamp() > 0 && blks.get(i).getRate() > 0) {msbeg=blks.get(i); break;}
          COSMOSDataHolder cData = new COSMOSDataHolder(span.getNsamp(), (int) (span.getRate()+.0001));
          /*
          Read the style in.
          */
          if (!cData.loadStyle(styleString))
          {
             Util.prt("*** " + cData.error());
          }
          int ttype=1;
          cData.setHeaderInteger("TRIGGERTYPE", ttype);                     // Seismic trigger?
          InstrumentType.decodeInstrument(lastComp, instrumentType, comment);
          String seismometer=InstrumentType.getSeismometerString();
          String das = InstrumentType.getDASString();
          int itype =  cData.getHeaderInteger("UNKNOWNI");
          if(null == das) {
            if(lastComp.substring(0,2).equals("IU")) {
              if(beg.toString().compareTo("2010") < 0) {das="Q680";itype=604;}
              else {das="Q330";itype=605;}
            }
            else das="UNKNOWN";
          }
          else switch (das) {
            case "Q330":
              itype=605;       // This is not officially in cosmos tables
              break;
            case "Q680":
              itype=604;
              break;
            case "Q4120":
              itype=600;
              break;
            case "Q730":
              itype=602;
              break;
            case "RT72":
              itype=700;
              break;
            case "RT130":
              itype=701;
              break;
            case "NetQuakes":
              itype=800;
              break;
            case "K2":
              itype=108;
              break;
            case "Etna":
              itype=109;
              break;
            case "MtWhitney":
              itype=110;
              break;
            case "Makalu":
              itype=112;
              break;
            case "Granite":
              itype=130;
              break;
            case "SSA1":
              itype=104;
              break;
            case "SSA2":
              itype=105;
              break;
            default:
              break;
          }

          if(itype ==  cData.getHeaderInteger("UNKNOWNI")) 
            Util.prt(" ** Unknown DAS type="+das+" ty="+instrumentType+":"+comment);
          cData.setHeaderInteger("RECORDERTYPE", itype);  // Unit type - See digital recorder tables
          cData.setHeaderString("RECORDERNAME", das);                     // Recorder name
          try {
            cData.setHeaderInteger("RECORDERSN", 
                    (InstrumentType.getDASSerial().equalsIgnoreCase("Unknown")? 0 :
                    Integer.parseInt(InstrumentType.getDASSerial())));      // Recorder SN
          }
          catch(NumberFormatException e) {
            cData.setHeaderInteger("RECORDERSN", 0);
          }
          cData.setHeaderInteger("RECORDERCHANS", 3);   // Recorder channels
          cData.setHeaderInteger("STATIONCHANS", 6);    // # of channels at station, we do not really know this
          cData.setHeaderReal("RECORDERLSB",20./8388608.0*1000000.0);                   // LSB in uV
          cData.setHeaderReal("RECORDERFULLSCALE", 20.); // Full scale in volts
          cData.setHeaderInteger("RECORDERBITS", 24);    // Number of bits as recorded
          cData.setHeaderInteger("EFFECTIVEBITS", 24);
          cData.setHeaderReal("PREEVENTSECS", 30.);           // Pre-event
          cData.setHeaderReal("POSTEVENTSECS", span.getNsamp()/span.getRate() -30.);         // Post-event

          GregorianCalendar st = span.getStart();
          String sttime = df2.format(st.get(Calendar.MONTH)+1)
                + "/" + df2.format(st.get(Calendar.DAY_OF_MONTH))
                + "/" + df4.format(st.get(Calendar.YEAR))
                + " " + df2.format(st.get(Calendar.HOUR_OF_DAY))
                + ":" + df2.format(st.get(Calendar.MINUTE))
                + ":" + df2.format(st.get(Calendar.SECOND))
                + "." + df3.format(st.get(Calendar.MILLISECOND))
                + " UTC";
          cData.setHeaderString("RECORDSTART", sttime);                     // Record start time
          cData.setHeaderInteger("STARTYEAR", st.get(Calendar.YEAR));    // Start time - Year
          cData.setHeaderInteger("STARTJULIANDAY",
             st.get(Calendar.DAY_OF_YEAR));                              // Julian day
          cData.setHeaderInteger("STARTMONTH", st.get(Calendar.MONTH)+1);// Month
          cData.setHeaderInteger("STARTDAYOFMONTH",
             st.get(Calendar.DAY_OF_MONTH));                             // Day of month
          cData.setHeaderInteger("STARTHOUR",
             st.get(Calendar.HOUR_OF_DAY));                              // Hour
          cData.setHeaderInteger("STARTMINUTE",
             st.get(Calendar.MINUTE));                                   // Minute
          double dsec = ((double)(st.getTimeInMillis()  % 60000))/1000.0;
          cData.setHeaderReal("STARTSECOND", dsec);                         // Second
          // Determine clock quality
          cData.setHeaderInteger("TIMEQUALITY", (msbeg == null?20:msbeg.getTimingQuality()/20));// Time quality 0-5 (degrade 1 every 10 points)
          cData.setHeaderInteger("TIMESOURCE", 5);
          switch (das) {
            case "NetQuakes":
              cData.setHeaderInteger("TIMESOURCE",6);
              break;
            default:
              cData.setHeaderInteger("TIMESOURCE", 5);                    // Time source assume 5=GPS for all modern instruments
              break;
          }
          cData.setHeaderReal("STALATITUDE", coord[0]);         // Latitude
          cData.setHeaderReal("STALONGITUDE", coord[1]);       // Longitude
          cData.setHeaderReal("STAELEVATION", coord[2]);       // Elevation

          cData.setHeaderReal("SAMPLEINTERVAL",1.0/(double) span.getRate());                              // Sample interval
          int ich=0;
          int azm=-1;
          switch (lastComp.charAt(9)) {
            case 'Z':
              ich=1;
              if(orient[1] == -90) azm =400; else if(orient[1] == 90.) azm=401;
              break;
            case 'N':
            case '1':
              ich=2;
              azm=(int) orient[0];
              break;
            case 'E':
            case '2':
              ich=3;
              azm =(int) orient[0];
              break;
            default:
              break;
          }
          if(ich == 0) 
            Util.prt("***** unknown channel component for cosmos "+lastComp);

          cData.setHeaderInteger("RECORDERCHNUM", ich);                // Recorder channel number
          cData.setHeaderInteger("STATIONCHNUM", ich);                //station channel number
          //cData.setHeaderString("RECORDID", lastComp.substring(7,10));
          //cData.setHeaderString("SENSORLOCATION",lastComp.substring(10));
          // Determine sensor type
          itype = cData.getHeaderInteger("UNKNOWNI");


          switch (seismometer) {
            case "FBA3":
              itype=3;
              break;
            case "FBA11":
              itype=4;
              break;
            case "FBA13":
              itype=5;
              break;
            case "FBA23":
              itype=7;
              break;
            case "EST":
              itype=20;
              break;
            case "Episensor200":
              itype=20;   // NSMP uses this name
              break;
            case "NetQuakes":
              itype=550;
              break;
            case "ESDH":
              itype=22;
              break;
            case "ESU":
              itype=21;
              break;
            case "SSA120":
              itype=100;
              break;
            case "SSA220":
              itype=101;
              break;
            case "SSA320":
              itype=102;
              break;
            case "W731":
              itype=150;
              break;
            case "CMG5":
              itype=200;
              break;
            case "RT131":
              itype=251;   // 250, 252-256 are all version of this
              break;
            case "LIS344":
              itype=350;
              break;
            case "AC6x":
              itype=400;
              break;
            case "Titan":
              itype=450;
              break;
            case "SS1":
              itype=1001;
              break;
            case "Trillium240":
              itype=1100;  // The trillium 120 and 40 should have own codes
              break;
            case "Trillium120":
              itype=1100;
              break;
            case "Trillium40":
              itype=1100;
              break;
            case "CMG1":
              itype=1201;
              break;
            case "CMG3":
              itype=1202;
              break;
            case "CMG3ESP":
              itype=1203;
              break;
            case "CMG40T":
              itype=1204;
              break;
            case "STS1":
              itype=1250;
              break;
            case "STS2":
              itype=1251;
              break;
            case "L4":
              itype=1300;
              break;
            case "L22":
              itype=1301;
              break;
            default:
              break;
          }

          
          if(itype == cData.getHeaderInteger("UNKNOWNI")) 
            Util.prt("  *** no seismometer found for "+seismometer+" type="+instrumentType+":"+comment);

          cData.setHeaderString("SENSORTYPE", seismometer);                       // Sensor type string
          cData.setHeaderInteger("SENSORTYPEI", itype);                     // Sensor type code translated from evt.getSensorType()
          try {
           //cData.setHeaderString("SENSORSN", InstrumentType.getLastSerial()); // set alpha serials
           cData.setHeaderInteger("SENSORSN", Integer.parseInt(InstrumentType.getLastSerial()));        // Sensor serial number
          }
          catch(NumberFormatException expected) {}
          double nf = cData.getHeaderReal("UNKNOWNR");
          cData.setHeaderReal("SENSORNATFREQ", nf);                         // Sensor natural frequancy
          double dmp = cData.getHeaderReal("UNKNOWNR");
          double digitGain = sensCalc/instrumentGain;     // digitizer gain in counts per volt (assume FIRs have gain of 1)
          double fsinCounts = 20*digitGain;               // full scale if this is 20 Volts FS
          double fsinVolts = 8388608/digitGain;           // Full scale voltage based on 2**23 counts is full scale
          if(fsinVolts > 19) fsinVolts=20;
          else if(fsinVolts > 9.8) fsinVolts=10.;
          else if(fsinVolts > 4.8) fsinVolts=5.;
          

          cData.setHeaderReal("SENSORDAMPING", dmp);                        // Sensor damping
          cData.setHeaderReal("SENSORSENS", instrumentGain*9.80665);        // Sensor sensitivity (v/g) (v/m/s**2 * 9.8 V/M/s**2 per g)
          cData.setHeaderReal("SENSORFSINV", fsinVolts);                // Sensor full scale output (assumed 2**23 counts=full scale)
          cData.setHeaderReal("SENSORFSING",fsinVolts/(instrumentGain*9.80665)); // Full scale in g = 20 Volts/sensor Senitvity in V/g
          cData.setHeaderReal("SENSORGAIN", 1.);       // Gain from header
          cData.setHeaderInteger("SENSORAZIMUTH",azm);                                   // Azimuth of sensor
          cData.setHeaderReal("SENSOROFFSETN",0.);                                   // Sensor offset north
          cData.setHeaderReal("SENSOROFFSETE",0.);                                   // Sensor offset east
          cData.setHeaderReal("SENSOROFFSETV",0.);                                  // Sensor offset vertical
          String network=lastComp.substring(0,2);
          int networkNumber=-1;
          switch (network) {
            case "C ":
              networkNumber=1;
              break;
            case "NP":
              networkNumber=2;
              break;
            case "RE":
              networkNumber=3;
              break;
            case "CE":
              networkNumber=5;
              break;
            case "CI":
              networkNumber=6;
              break;
            case "BK":
              networkNumber=7;
              break;
            case "NC":
              networkNumber=8;
              break;
            case "SB":
              networkNumber=9;
              break;
            case "AZ":
              networkNumber=10;
              break;
            case "NN":
              networkNumber=14;
              break;
            case "UW":
              networkNumber=15;
              break;
            case "TO":
              networkNumber=16;
              break;
            case "AA":
              networkNumber=17;
              break;
            case "WR":
              networkNumber=20;
              break;
            case "PG":
              networkNumber=21;
              break;
            case "US":
              networkNumber=30;
              break;
            case "NE":
              networkNumber=35; // USGS other
              break;
            case "GS":
              networkNumber=31;
              break;
            case "IU":
              networkNumber=32;
              break;
            case "CU":
              networkNumber=33;
              break;
            case "NQ":
              networkNumber=34;
              break;
            case "TW":
              networkNumber=100;
              break;
            case "GB":
              networkNumber=107;
              break;
            default:
              break;
          }
          if(networkNumber == -1) {networkNumber=199; 
          Util.prt(" ** Unknown network number for "+lastComp);}
          cData.setHeaderInteger("NETWORKNUMBER", networkNumber);
          cData.setHeaderString("NETWORKABBREV", network);
          cData.setHeaderString("NETWORKCODE", network);
          cData.setHeaderString("STATIONCODE", lastComp.substring(2,7).trim());
          cData.setHeaderString("STATIONNAME", longName);

          cData.setComment(1, cData.getComment(1).replaceAll("CCC",lastComp.substring(7,10)).replaceAll("LL",lastComp.substring(10,12)));
          BufferedReader in = new BufferedReader(new StringReader(sacpzString));
          String line;
          int i=2;
          while( (line = in.readLine()) != null) {
            cData.addComment(i++, "|<PZ> "+line);
          }
          cData.setHeaderInteger("COMMENTLENGTH", i-1);
          

          // Set the data values
          FileBasedTempDataArray sampleData = new FileBasedTempDataArray("samples", 2000000, span.getNsamp());
          // Read in the samples
          for (int six=0; six<span.getNsamp(); six++)
          {
                sampleData.set(span.getData(six), six);
          }
          cData.setData(sampleData);
          try ( // Create the output file
                  BufferedWriter ofile = new BufferedWriter(new FileWriter(filename.replaceAll(".sac","")+".v0"))) {
            cData.write(ofile);
            ofile.flush();
            /*ofile = new BufferedWriter(new FileWriter(filename.replaceAll(".sac","")+".v0.pz"));
            ofile.write(pz);
            ofile.flush();
            ofile.close();*/
          }

          // Clean up working arrays
          sampleData.cleanup();
          cData.cleanup();

        }

        // Set the byteOrder based on native architecture and sac statics
        sac = new SacTimeSeries();
        sac.npts=span.getNsamp();
        sac.nvhdr=6;                // Only format supported
        sac.b= 0.;           // beginning time offsed
        sac.e= (span.getNsamp()/span.getRate());
        sac.iftype=SacTimeSeries.ITIME;
        sac.leven=SacTimeSeries.TRUE;
        sac.delta=(1./span.getRate());
        sac.depmin= span.getMin();
        sac.depmax= span.getMax();
        sac.nzyear = span.getStart().get(Calendar.YEAR);
        sac.nzjday = span.getStart().get(Calendar.DAY_OF_YEAR);
        sac.nzhour = span.getStart().get(Calendar.HOUR_OF_DAY);
        sac.nzmin = span.getStart().get(Calendar.MINUTE);
        sac.nzsec = span.getStart().get(Calendar.SECOND);
        sac.nzmsec = span.getStart().get(Calendar.MILLISECOND);
        sac.iztype = SacTimeSeries.IB;
        sac.knetwk = lastComp.substring(0,2);
        sac.kstnm = lastComp.substring(2,7);
        sac.kcmpnm = newComp.substring(7,10);
        sac.khole="  ";
        if(!lastComp.substring(10,12).equals("  ")) sac.khole=lastComp.substring(10,12);
        if(coord[0] != SacTimeSeries.DOUBLE_UNDEF) sac.stla =  coord[0];
        if(coord[1] != SacTimeSeries.DOUBLE_UNDEF) sac.stlo =  coord[1];
        if(coord[2] != SacTimeSeries.DOUBLE_UNDEF) sac.stel =  coord[2];
        if(coord[0] == SacTimeSeries.DOUBLE_UNDEF && coord[1] == SacTimeSeries.DOUBLE_UNDEF && coord[2] == SacTimeSeries.DOUBLE_UNDEF)
          if(!noStaSrv) Util.prt("   **** "+lastComp+" did not get lat/long.  Is server down?");
        if(orient != null) {
          if(orient[2] != SacTimeSeries.DOUBLE_UNDEF) sac.stdp =  orient[2];
          if(orient[0] != SacTimeSeries.DOUBLE_UNDEF) sac.cmpaz =  orient[0];
          if(orient[1] != SacTimeSeries.DOUBLE_UNDEF) sac.cmpinc =  (orient[1]+90.);   // seed is down from horiz, sac is down from vertical
        }
        else {
          if(orient[0] == SacTimeSeries.DOUBLE_UNDEF && orient[1] == SacTimeSeries.DOUBLE_UNDEF)
            if(!noStaSrv) Util.prt("      ***** "+lastComp+" Did not get orientation.  Is server down?");
        }
        //Util.prt("Sac stla="+sac.stla+" stlo="+sac.stlo+" stel="+sac.stel+" cmpaz="+sac.cmpaz+" cmpinc="+sac.cmpinc+" stdp="+sac.stdp);
        sac.y = new double[span.getNsamp()];   // allocate space for data
        int nodata=0;
        for(int i=0; i<span.getNsamp(); i++) {
          sac.y[i] =  span.getData(i);
          if(sac.y[i] == fill) {
            nodata++;
          }
        }
        if(nodata > 0 && !quiet) Util.prt(" ******* #No data points = "+nodata+" fill="+fill+" npts="+sac.npts);
        int trimmed = sac.trimNodataEnd(fill);
        if(trimmed > 0) Util.prt(trimmed+" data points trimmed from end containing no data");

        // If PGM calculations are needed do them here
        // Check for strong motion pgm information
        SM_INFO sm = null;
        if(pgm) {
          double [] data = new double[span.getNsamp()];
          for(int i=0; i<span.getNsamp(); i++) {
            data[i] = sac.y[i]/(sensCalc*100.);    // 100 to convert M to cm
          }
          int itype=0;
          switch (instrumentUnit) {
            case "V":
              itype=2;
              break;
            case "D":
              itype=1;
              break;
            case "A":
              itype=3;
              break;
            default:
              break;
          }
          trigTime.setTimeInMillis(span.getStart().getTimeInMillis()+60000);

          sm = PGM.peak_ground(lastComp, "QUID", "AUTH", trigTime, span.getStart(),data, span.getNsamp(), itype, 1/rate, Util.getOutput());
          sm.latitude=coord[0];
          sm.longitude=coord[1];
          sm.longName=longName;
          Util.prt("PGM dump : "+sm.toString());
        }

        // write it out if it passes muster
        try {
          if(!pgm || (pgm && sm.pga > pgmLevel) ) {
            if(cosmos) sac.write(filename.replaceAll(".sac","")+".sac");
            else sac.write(filename);
          }
        }
        catch(FileNotFoundException e) {
          Util.IOErrorPrint(e,"File Not found writing SAC");
        }
        catch(IOException e) {
          Util.IOErrorPrint(e,"IO exception writing SAC");
          throw e;
        }
       // }           //  IF this is not cosmos and thus must be SAC

        maxTime=0;
        segblks.clear();
        if(iblk >= blks.size()) break;

      }
      else if(iblk < blks.size()) {
        if(blks.get(iblk).getRate() > 0 && blks.get(iblk).getNsamp() > 0) segblks.add(blks.get(iblk));
        if(maxTime < blks.get(iblk).getNextExpectedTimeInMillis())
           maxTime = blks.get(iblk).getNextExpectedTimeInMillis();
        iblk++;
      }
    }
  }

}
