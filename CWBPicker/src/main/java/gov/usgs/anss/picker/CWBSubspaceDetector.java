/*
 *  This software is in the public domain because it contains materials 
 *  that originally came from the United States Geological Survey, 
 *  an agency of the United States Department of Interior. For more 
 *  information, see the official USGS copyright policy at 
 *  http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */ 
package gov.usgs.anss.picker;

import com.oregondsp.io.SACInputStream;
import detection.usgs.LabeledDetectionStatistic;
//import gov.usgs.alarm.SendEvent;
//import gov.usgs.anss.edgeoutput.GlobalPickSender;
//import gov.usgs.anss.edgeoutput.JSONPickGenerator;
import gov.usgs.anss.edgethread.EdgeThread;
import gov.usgs.anss.util.Distaz;
import gov.usgs.anss.util.Util;
//import gov.usgs.edge.config.SubspaceArea;
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import subspacedetector.Config;
import subspacedetector.DetectionSummary;
import subspacedetector.FilterCollection;
import subspacedetector.PeakFinder;
import static subspacedetector.SubspaceDetector.filterData;

/**
 * This class interfaces to the SubspaceDectector project using the CWBPicker conventions and
 * infrastructure. The CWBPicker parameter "blocksize" must match the length of the SubspaceDetector
 * time so that calls to apply() are made with exactly the right amount of data.
 * <pre>
 * switch   Value         Description
 * -config  filename     Configuration file name for the SSD config file (from Preprocessor)
 * -ssddbg               If present, turn on more detailed debugging
 * 
 * 
 * The switchs to the super class CWBPicker are documented in this class.   Below is a copy:
 *  * switch   args    description
 * -1               If present this is a segment picker, not continuous
 * -nodb            If present, set DBServer and PickDBServer to "NoDB"
 *
 * For properties that come from a template file (separator for key#pairs is '#':
 * Property      switch args      description
 * TemplateFile -template file   ! this refers to the template manually create or from the picker table
 *
 * NSCL         -c NNSSSSSCCCLL  The channel to process, comes from the -c mostly but can be in preconfigured template
 * Blocksize    -blocksize       The processing block size in seconds
 * PickDBServer -db   dbURL      Set the DB where the status.globalpicks is located (used to generate ID numbers) 'NoDB' is legal
 * Author       -auth author     Set the author (def=FP6)
 * Agency       -agency agency   Set the agency
 * Title        -t     title     A descriptive title for this instance (example FP6-RP)
 * CWBIPAdrdess -h     cwb.ip    Set source of time series data (def cwbpub)
 * CWBPort      -p     port      Set the CWB port (def=2061)
 * MakeFiles    -mf              If present, this instance will make files in MiniSeed from parameters.
 * PickerTag    -f     pickerTag Some tag associated with the picker (def=NONE)
 * JSONArgs     -json  1;2&3;4   @see gov.usgs.picker.JsonDetectionSender for details
 *
 *
 * For properties kept in the state file :
 * StartTime    -b     begintime Starting time, not valid on a segment picker
 * EndTime      -e     endTIme   Ending Time
 * Rate         -rt    Hz        The digit rate, else one from the time series will be used
 * Example:
 * "-mf -json path;./JSON -c GSMT01_HHZ00,GSMT01_HH100,GSMT01_HH200 -h 137.227.224.97 "
 *            + " -b 2017/09/05-00:00 -e 2017/10/01-00:00 "
 *            + " -config Montana_2017/GSMT01_HH.00/GSMT01_.cfg"
 *            + " -auth SSD-TEST -agency US-NEIC -db localhost/3306:status:mysql:status >>USGSSSD";
 * </pre>
 * Note, the template file is loaded first, then the "#" tags from the template file is loaded,
 * then the SSD configuration file, and then the state file if it exists.  So to run this is
 * research mode delete the state file, and put the start/stop times in the configuration file.  If
 * the statefile exists, then those times would become the operational times.
 * @author U.S. Geological Survey  <ketchum at usgs.gov>
 *
 */
public final class CWBSubspaceDetector extends CWBPicker /*implements PickerInterface*/ {

  public static final String pickerType = "ssd";
  private static final long WARMUP_MS = 600000; // time before original starttime to start process to allow warmup
  private boolean dbg;
  private boolean makefiles;
  private EdgeThread par;

  // SSD related variables
  private Config cfg;
  private String configFile;
  private final GregorianCalendar pt = new GregorianCalendar();
  //private GregorianCalendar endt;
  private final FilterCollection filter;
  private final SimpleDateFormat folderformat = new SimpleDateFormat("yyyy_MM_dd_HH_mm_ss/");
  private ArrayList< LabeledDetectionStatistic> dstats;
  private DetectionSummary detsum = new DetectionSummary();
  private final float[] stack;   // Array containing the cross-correlation results
  private final int[] constant_picks;
  private StringBuilder sbssd = new StringBuilder(100);
  private final double distance;
  //private final double backazm;   // this is in the CWBPicker class
  // These are to fill in for things not yet know in the pick generation or are not yet implemented
  private final double period = -1;
  private final double err_wind = -1.;
  private final double lowpass = -1;
  private final double hipass = -1;
  private final double[] values = new double[2];
  private final double zScore = -1;
  private final float[][] refdata;
  

  // runtime related variables
  private GregorianCalendar picktime = new GregorianCalendar();
  private boolean first = true;
  private boolean ready;
  private StringBuilder tmpsb = new StringBuilder(1000);

  /*public void registerInstrumentation(SSDInstrumentation obj) {
    
  }*/
  @Override
  public boolean isReady() {
    return ready;
  }

  @Override
  public void terminate() {
    prta(Util.clear(statussb).append("Terminate() has been called - close and write state"));
    if (maker != null) {
      maker.close();
    }
    writestate();
    terminate = true;
    ready = false;
  }

  @Override
  public long getWarmupMS() {
    return WARMUP_MS;
  }

  @Override
  public StringBuilder getStatusString() {
    Util.clear(statussb).append("file=").append(cfg.getHPCorner()).append("-").append(cfg.getLPCorner()).
            append(" ").append(cfg.getEventType()).append(" ref=").append(cfg.getReferenceEventID()).
            append(" ").append(Util.ascdatetime2(cfg.getReferenceOriginTime())).append(" ").
            append(cfg.getReferenceMagnitude()).append(" ").append(cfg.getReferenceMagType()).
            append(" stat=").append(cfg.getStatInfo().getChannels()[0]).append(" ").append(cfg.getStatInfo().getRate());
    return statussb;
  }

  public CWBSubspaceDetector(String argline, String tag/*, FP6Instrumentation inst*/) throws FileNotFoundException, IOException {
    super(argline, tag, null);
    Util.setModeGMT();
    if(author == null) {
      prta("**** Author not set use default");
      author = "SSD-TEST";
    }
    if(author.equals("")) {
      prta("**** Author is empty, use default");
      author = "SSD-TEST";
    }
    //instrumentation = inst;
    String[] args = argline.split("\\s");
    while (getRate() <= 0.) {
      try {
        Thread.sleep(100);
      } catch (InterruptedException e) {
      }
    }
    rate = getRate();
    makefiles = getMakeFiles();
    maker = getMaker();
    //Util.clear(nsclsb).append(nscl);
    try {
      setArgs(getPickerArgs().toString());// Set any parameters from the template
    } catch (IOException e) {
      e.printStackTrace(getPrintStream());
    }

    for (int i = 0; i < args.length; i++) {
      if (args[i].length() == 0) {
        continue;
      }
      if (args[i].contains(">")) {
        break;
      } else if (args[i].equals("-empty")) { // this just abates a warning, no effect
      } else if (args[i].equals("-config")) {
        configFile = args[i + 1];
        i++;
      } else if (args[i].equals("-ssddbg")) {
        dbg = true;
      } else {
        switch (parentSwitch(args[i])) {      // Does parent handle this switch?
          case 1:
            i++;        // yes with an argument
            break;
          case 0:
            break;      // yes, without an argument
          default:      // No list it out.
            prta(i + "SSD " + getNSCL() + " ** Unknown argument to CWBSubspaceDetector=" + args[i]);
            break;
        }
      }
    }
    initialize(stateFile);        // the state file overrules all
    while (cfg == null) {
      cfg = new Config(this);
      //cfg.setStartEndTimes(Util.ascdatetime(getBeginTime()).toString(), Util.ascdatetime(getEndTime()).toString());
      boolean ok = cfg.readConfigFile(configFile);
      prta("Config: "+cfg.toString()+" "+cfg.getStartTime()+ " -"+ cfg.getStopTime());
      // If there is no state file, then the start-stop from the config file should be used
      if(createStateFile) {
        if(!cfg.getStartTime().equals("")) {
          setTime(Util.stringToDate2(cfg.getStartTime()).getTime());
          setBeginTime(getTime());   
        }
        if(!cfg.getStopTime().equals("")) {
          setEndTime(Util.stringToDate2(cfg.getStopTime()).getTime());
        }
      }
      if (!ok) {
        prta("***** Configuration " + configFile + " did not parse correctly!");
      }
    }
    blocksize = cfg.getDataLength() / rate;
    dstats = new ArrayList< LabeledDetectionStatistic>(cfg.getDetectors().getNumberOfTemplates());
    stack = new float[cfg.getDataLength() + cfg.getTemplateLength()];
    constant_picks = new int[stack.length];
    double[] ans = new double[4];

    // Compute the station reference distance and back azimuth
    Distaz.distaz(cfg.getReferenceLatitude(), cfg.getReferenceLongitude(), cfg.getStatInfo().getLatitude(), cfg.getStatInfo().getLongitude(), ans);
    distance = ans[1];          // Distance in degrees?
    backazm = (int) Math.round(ans[3]);

    refdata = readReferenceData(cfg);     // This will return null if the reference data is not found
    filter = new FilterCollection();
    filter.setup(cfg.getHPCorner(), cfg.getLPCorner(), cfg.getNpoles(), cfg.getStatInfo().getRate(),
            cfg.getFilterType(), cfg.getStatInfo().getNumberofChannels());
    if(blocksize != cfg.getDataLength()/rate) {
      prta("**** block size does not agree with cnf.dataLength "+blocksize+" != "+(cfg.getDataLength()/rate));
    }
    ready = true;
  }

  /**
   * write out the reference data for this station if there is any. If not, leave the reference data
   * behind. The reference data starts 60 seconds before reference arrival time and extends 60
   * seconds past. DCK added this in order to support Reference channels where the timeseries does
   * not have to still exist in the CWB. The unfiltered reference channel is written the last time
   * the data was available.
   *
   * @param cfg The configuration of this SSD
   * @return the two dimensional array of reference data
   */
  public float[][] readReferenceData(Config cfg) {
    int buffLengthSeconds = 60;
    int bufferLengthSamples = (int) (buffLengthSeconds * getRate()); // åbuffer around template
    //StringBuilder pt = Util.ascdatetime2(phasetime);
    GregorianCalendar t = new GregorianCalendar();
    t.setTimeInMillis(cfg.getReferenceArrivalTime().getTimeInMillis());
    t.add(Calendar.SECOND, -buffLengthSeconds);
    int samples = 2 * bufferLengthSamples + cfg.getTemplateLength();
    float[][] data = new float[cfg.getNumberofChannels()][samples];  // place to return the data
    // The time of the refdata is 60 seconds before its arrival time
    String cr = Util.ascdatetime2(t).toString();
    String ts = cr.substring(0, 4) + cr.substring(5, 7) + cr.substring(8, 10);
    ts = ts + "_" + cr.substring(11, 13) + cr.substring(14, 16) + cr.substring(17, 19);
    try {
      for (int i = 0; i < cfg.getNumberofChannels(); i++) {
        String filename = cfg.getOutputPath() + nscl.get(i).replaceAll(" ","_") + "_" + ts + "ref.sac";
        try (SACInputStream ss = new SACInputStream(filename)) {
          ss.readData(data[i]);
          filterData(data[i], cfg.getHPCorner(), cfg.getLPCorner(), cfg.getNpoles(), (int) rate, 
                  cfg.getFilterType());
        }
      }
    } catch (IOException e) {
      prta("reading reference data e=" + e);
      return null;
    }
    return data;
  }

  @Override
  public void writestate() {
    synchronized (statussb) {
      Util.clear(statussb);
      statussb.append("configfile#").append(configFile).append("\n");  // save the config file
      writeStateFile(statussb);
    }
  }

  @Override
  public final void setArgs(String s) throws FileNotFoundException, IOException {
    String start;
    String stop;
    try (BufferedReader in = new BufferedReader(new StringReader(s))) {
      String line;
      start = "";
      stop = "";
      while ((line = in.readLine()) != null) {
        if (line.contains("=")) {
          continue;      // Not a filter arg, but the CWBPicker arg
        }
        if (line.contains("!")) {
          line = line.substring(0, line.indexOf("!"));  // remove comments
          if(line.contains("#")) {
            String [] parts = line.split("#");
            if(parts[0].equals("configfile")) {
              configFile = parts[1];
              
            }
          }
        }
      }
      // adjust beginTime to the warmup interval
    }
  }

  @Override
  public void clearMemory() {
    //picker.clearMemory();
    filter.initialize();
    cfg.getDetectors().initialize();
    //cfg.getRDetectors().initialize();
    Arrays.fill(stack, 0.f);
    //Arrays.fill(formatted_z, 0.f);  // only if zScores being done
  }
  private float[][] segment;

  /**
   * The amount of data provided (npts) must agree with the cfg parameter datalength.
   *
   * @param current The time of the first point of the time series
   * @param rate Digitizing rate in Hz
   * @param idata Array with 3 components of data
   * @param npts Number of points in each time series.
   */
  @Override
  public void apply(GregorianCalendar current, double rate, int[][] idata, int npts) {
    if (npts != cfg.getDataLength()) {
      throw new RuntimeException("CWBSSD : wrong number of data points in apply() was " + npts + " need " + cfg.getDataLength());
    }
    if (segment == null) {
      segment = new float[cfg.getStatInfo().getNumberofChannels()][cfg.getDataLength()];
    }

    // convert the data to floats and filter it
    for (int i = 0; i < 3; i++) {
      for (int j = 0; j < idata[i].length; j++) {
        segment[i][j] = (float) idata[i][j];
      }
      filter.filterData(segment[i], i);
    }

    try {
      dstats = cfg.getDetectors().evaluateDetectionStatistics(segment);  //David Harris code, nothing to be rewritten

      //JN edit 2015: get rstats
      //if( cfg.getDetectionThresholdType().matches("all") || cfg.getDetectionThresholdType().matches("mad")) {
      //ArrayList< LabeledDetectionStatistic> rstats = cfg.getRDetectors().evaluateDetectionStatistics(segment);
      //}

      System.arraycopy(dstats.get(0).getDetectionStatistic(), 0,
              stack, cfg.getTemplateLength(), cfg.getDataLength());
      //float const_threshold = (float) 0.30;
      int cpick = PeakFinder.findPicks(stack, cfg.getTemplateLength(),
              cfg.getDataLength(), cfg.getAveragingLength(), cfg.getDetectionThreshold(), 
              constant_picks, sbssd);

      //JN edit: for constant thresh results
      for (int i = 0; i < cpick; i++) {

        StringBuilder ct = Util.ascdatetime(current);
        detsum.summary(cfg, current, constant_picks[i] - cfg.getTemplateLength());
        if (detsum.Complete() == true) {
          values[0] = -1;
          values[1] = -1;
          pickCorrelation(pickerType, detsum.PhaseTime(), stack[constant_picks[i]], period,
                  detsum.Phase(), err_wind, cfg.getReferenceLatitude(), cfg.getReferenceLongitude(), cfg.getReferenceDepth(),
                  detsum.OriginTime().getTimeInMillis(), values[1], values[0],
                  distance, backazm, cfg.getEventType(), zScore, cfg.getDetectionThreshold(), cfg.getDetectionThresholdType());
          /*System.out.println("Found Event, Calculating SNR");
              /*float[] values = getDetails(cfg,cfg.StatInfo(),detsum.PhaseTime(),refdata,false);

              if( sumf != null && v alues[0] > cfg.getSNRCutoff() ) 
              {
                  sumf_const.writeSummary(detsum,cfg,
                      nscl.substring(0,2).trim(),nscl.substring(2,7).trim(),
                      nscl.substring(7,10).trim(),nscl.substring(10,12),
                      stack[constant_picks[i]],cfg.DetectionThreshold(),values[0],values[1]);
                  detections = detections+1;
                  if(cfg.getJson()==true)
                  {
                      sequence = rn.nextInt(999999999);
                      jsonGenerator.writeCorrelation(cfg.Agency(), cfg.Author(), detsum.PhaseTime(), nscl, 
                      sequence, cfg.getReferenceLatitude(), cfg.getReferenceLongitude(), cfg.getReferenceDepth(), detsum.OriginTime(),
                      detsum.Phase(), cfg.EventType(), stack[constant_picks[i]], cfg.DetectionThreshold(), 
                      values[1], values[0], 0, cfg.getDetectionThresholdType(),
                      0,0,0,0);

                  }

              }*/
        }
      }
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  public static void main(String[] args) throws InterruptedException, IOException {
    Util.init("edge.prop");
    Util.setModeGMT();
    CWBPicker picker;
    GregorianCalendar newStart = new GregorianCalendar();
    //EdgeThread.setMainLogname("cwbfp6");

    // if no args, make the development one
    if (args.length == 0) {
      long start = (newStart.getTimeInMillis() - 86400000L * 2) / 86400000L * 86400000L;
      newStart.setTimeInMillis(start);
      Util.prt("start=" + Util.ascdatetime2(start));
      GregorianCalendar newEnd = new GregorianCalendar();
      newEnd.setTimeInMillis(start + 86400000L);     // process one day
      //QuerySpan.setDebugChan("USDUG  BHZ00"); 
      String cmd = "-path ./StJoeMO_2018/N4P38B_BH./ -ID 52 -auth SSD-TEST -agency US-NEIC -json path\\;./JSON -nodb -c N4P38B_BHE,N4P38B_BHN,N4P38B_BHZ -config ./StJoeMO_2018/N4P38B_BH./N4P38B_.cfg >>./StJoeMO_2018/N4P38B_BH./N4P38B_BH.";
     /* String cmd = "-mf -json path;./JSON -c GSMT01_HHZ00,GSMT01_HH100,GSMT01_HH200 -h 137.227.224.97 "
              //+ " -b 2017/09/05-00:00 -e 2017/10/01-00:00 "
              + " -config Montana_2017/GSMT01_HH.00/GSMT01_.cfg"
              + " -auth SSD-TEST -agency US-NEIC -db localhost/3306:status:mysql:status >>USGSSSD";*/
      picker = new CWBSubspaceDetector(cmd, "CWBSSD");

      while (picker.isAlive()) {
        Util.sleep(1000);
        if (picker.segmentDone()) {
          newStart.setTimeInMillis(newStart.getTimeInMillis() - 86400000L);
          picker.runSegment(newStart, 86400., 0, -1, -1., -1., -1.);
        }
      }
    }
    else if(args.length > 1) {
      String argline = "";
      for(int i=0; i<args.length; i++) {
        argline += args[i]+" ";
      }
      Util.prt("Starting : "+argline);
      picker = new CWBSubspaceDetector(argline, "CWBSSD");
      while(picker.isAlive()) {
        Util.sleep(2000);
      }
      Util.exit(0);
    } else {        // Build up the command line and run 
      
      String argline = "";
      String nscl = null;
      FP6Instrumentation instrument = null;
      for (int i = 0; i < args.length; i++) {
        argline += args[i].trim() + " ";
        if (args[i].equalsIgnoreCase("-c")) {
          nscl = args[i + 1];
        } else if (args[i].equalsIgnoreCase("-db")) {
          Util.setProperty("PickDBServer", args[i + 1]);
        }
        if (args[i].equalsIgnoreCase("-i")) {
          try {
            Class cl = Class.forName(args[i + 1]);
            Class[] em = new Class[0];
            Constructor ct = cl.getConstructor(em);
            instrument = (FP6Instrumentation) ct.newInstance();
          } catch (ClassNotFoundException | NoSuchMethodException | SecurityException
                  | InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
            e.printStackTrace();
            System.exit(0);
          }
        }

      }
      if (nscl == null) {
        Util.exit("-c is mandatory for manual starts");
      } else {
        argline = argline + ">> PL/" + nscl.replaceAll("-", "_").replaceAll(" ", "_").trim();
        picker = new CWBFilterPicker6(argline, "CWBFP", instrument);
        while (picker.isAlive()) {
          Util.sleep(1000);
          if (picker.segmentDone()) {
            break;
          }
        }
      }
    }
    System.exit(0);
  }
}
