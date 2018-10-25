/*
 *  This software is in the public domain because it contains materials 
 *  that originally came from the United States Geological Survey, 
 *  an agency of the United States Department of Interior. For more 
 *  information, see the official USGS copyright policy at 
 *  http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.picker;

import gov.usgs.alarm.SendEvent;
//import gov.usgs.anss.edgeoutput.GlobalPickSender;
//import gov.usgs.anss.edgeoutput.JSONPickGenerator;
import gov.usgs.anss.edgethread.EdgeThread;
import gov.usgs.anss.util.Util;
import gov.usgs.anss.edgethread.MemoryChecker;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.TreeMap;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import net.alomax.timedom.BasicPicker;
import net.alomax.timedom.PickData;
import net.alomax.timedom.TimeDomainException;
import net.alomax.timedom.fp6.FilterPicker6PickData;
import net.alomax.timedom.fp6.FilterPicker6_BandParameters;
import net.alomax.timedom.fp6.USGSFP6Extension;

/**
 * This is the USGS implementation of the FilterPicker6 using the CWBPicker parent class.
 *
 * <p>
 * Running manually single - normally the user would us the command line argument -template to setup
 * the base template configuration. These parameters would not change as the command is run. The
 * user must also specify "-c NNSSSSSCCCLL -b yyyy/mm/dd-hh:mm -e yyyy/mm/dd-hh:mm -path SOME PATH".
 * The log files will be in log/PL.
 * <p>
 * Running manually a list of segments - Create a template with the parameters for the run and
 * create a file with lines like :
 * <PRE>
 * -1 [-template template.file] -c NNSSSSSCCCLL -h cwbrs -b yyyy/mm/dd-hh:mm:ss.ddd -e yyyy/mm/dd-hh:mm:ss.ddd [-i instrument.class]
 * </PRE> Invoke the process with :
 * <PRE>
 * java -cp PATH_TO/CWBPicker.jar gov.usgs.anss.CWBFilterPicker6 -file filename [-template template.file][-db URL.to.pickdb][-i instrument.class]
 * </PRE> The -template argument must either be on the command line so share it across all segments
 * in the file or it must be on each line in the file to be run. Both will work and the later will
 * take precedent. The -db must be declared or PickDBServer must be in the template file. The -i can
 * be either on the invocation line or on each line of the segment list file. In most cases its
 * makes more sense to put -template, -db and -i on the invocation line.
 *
 * <br>
 * Alternatively - the program an be started with all command line parameters in which case all
 * parameters for the CWBPicker and the CWBFilterPicker6 class should be used to set the
 * configuration. This can be a pretty long command line, but it is totally equivalent to running
 * from an template.
 * <p>
 * Manually running with instrumentation - The FPInstrumentation give the interface for making a
 * class that takes call backs from this class whenever apply() or some picks are declared. There is
 * an example FP6InstrumentationDCK which does this and picks apart the picks in detail and creates
 * MinISEED files with some of the parameter and data. To run this from this class add "-i
 * fully.qualified.pathname" of your FP6Instrumentation implementation. The main() will instantiate
 * your class and pass it to the constructor for this class. This instrumentation makes use of the
 * USGSFP6Extension to make internal attributes of the FilterPicker6 class available to the
 * instrumentation.
 * <p>
 * For automatic use this class is instantiated from the PickerManager class which uses the database
 * system to determine the primary picker and the template to use for each channel which has a pick
 * method assigned. See PickerManager for details.
 * <p>
 * CWBFilterPicker6 - specific switches and uses. Each of these also has a Key value associated with
 * it so that the configuration of the FP6 can be done in a template file. Here is an example of a
 * FP6 template files :
 * <br>
 * <PRE>
 * switch           description
 * -1               If present this is a Segment picker and not a continuous one
 * -nodb            If present set both DBServer and PickeDBServer properties to "NoDB"
 * -template  file  This template controls the filter picker parameters and optionally ouputs.
 *
 * !Key=value                   ! for CWB Picker
 * TemplateFile=RayPick.template   ! this refers to the template stored in the picker DB table
 * Blocksize=10                ! The processing block size in seconds
 * PickDBServer=localhost/3306:status:mysql:status ! Set the DB where the status.globalpicks and edge.picker configuration are (NoDB is possible)
 * Author=FP6-RP               ! Set the author (def=FP6)
 * Agency=US-NEIC              ! Set the agency
 * Title=FP6-RP                ! A descriptive title for this instance
 * Rate=100.00                 ! The digit rate, else one from the time series will be used
 * CWBIPAddress=localhost      ! Set source of time series data
 * CWBPort=2061                ! 2061 always we hope
 * Makefiles=true              ! If true, this instance will make files in MiniSeed from parameters.
 * PickerTag=FP6-RP             ! Some tag associated with the picker
 * !JSONArgs=                   ! see gov.usgs.picker.JsonDetectionSender for details
 * PickTopic=                  ! set the Kafka pick topic (def=pick-edge)
 * !key#value For FP6 specific ! for FP6 Prog Def  Comment
 * bands#2.6063,0.5:2.085,1.0:1.668,1.0:1.334,1.0:1.0675,1.:0.8540,.5  ! fr,fact:fr,fact... where fr is center freq in Hz and fact is the scaling factor for thresholds
 * filterWindow#0.8            ! 0.8
 * longTermWindow#30.          ! 6.0
 * Threshold1#8.00             ! 9.36
 * Threshold2#1.00             ! 1.0
 * TupEvent#20.                ! 20.
 * TupEventMin#0.25            ! 1.
 * </PRE>
 * <PRE>
 * switch    args           Description
 * -c        NNSSSSSCCCLL The Standard nscl within the code - must match only one channel
 * -tfilter  period      Longest period for a set of filtered signals from the differential signal of the raw broadband data Recommended:  300*(sample interval)
 * -tlong    period      Time interval used to accumulate time-average statistics of the input data 500*(sample interval)
 * -thold1   snr         Threshold - a trigger is declared when the summary Characteristic Function exceeds this. (Def=10);
 * -thold2               A pick is declared if and when, within a window of predefined time width, tup after the trigger time, the integral of the summary Characteristic Function exceeds the value threshold2*tup
 * -tup      secs        Time window used for pick validation Recommended 20*(sample interval)
 * -tupmin      secs     Time window used for pick validation length of shortest pick interval
 * -blocksize secs       The size (in sec) of block of data that will be requested from the QuerySpan and processed by FilterPicker each cycle
 * -t         Title            Title for this describing type of FP being run (NE network, or global, etc)
 * -b        yyyy/mm/dd-hh:mm  Time to start (normally not used), default is current time.
 * -e        yyyy/mm/dd-hh:mm  Time to end (normally not used, default is 2099/12/31
 * -pri      val         The thread priority of this thread - default=Thread.MIN_PRIOITY+1
 * -bands    r,fact:fr,fact... where fr is center freq in Hz and fact is the scaling factor for thresholds
 *                       Default : 0.5,1.:1.,1.:2.,1.:4.,0.5:8.,0.3:16.,0.3:32.,.3
 *
 * Key-Value pairs:
 * filterWindow#0.8
 * longTermWindow#300.   6.0
 * Threshold1#6.00       9.36
 * Threshold2#5.00       9.21
 * TupEvent#4.           0.388
 * TupEventMin#0.25      1.
 * BandsParameter#"0.5,1.:1.,1.:2.,1.:4.,0.5:8.,0.3:16.,0.3:32.,.3"
 * </PRE>
 * <br> For the USGS the -bands command line option or the BandsParmeter key value pair is most
 * often used rather than filterWindow. With either, the bands are specified as a series of
 * frequency and scale factor pairs separated by commas with the band/scale pairs separated by colon
 * or hyphen. The band frequencies must be equal spaced in bandwidth or the FilterPicker6 setup will
 * fail.
 *
 * <br>The filter window (filterWindow) in seconds determines how far back in time the previous
 * samples are examined. The filter window will be adjusted upwards to be an integer N power of 2
 * times the sample interval (deltaTime). Then numRecursive = N + 1 "filter bands" are created. For
 * each filter band n = 0,N the data samples are processed through a simple recursive filter
 * backwards from the current sample, and picking statistics and characteristic function are
 * generated. Picks are generated based on the maximum of the characteristic function values over
 * all filter bands relative to the threshold values threshold1 and threshold2. Note: the
 * filterWindow is not used if the bands are specified with the -bands command line argument or the
 * template file "BandsParameter key-value pair.
 *
 * <br> the long term window (longTermWindow) determines: a) a stabilization delay time after the
 * beginning of data; before this delay time picks will not be generated. b) the decay constant of a
 * simple recursive filter to accumulate/smooth all picking statistics and characteristic functions
 * for all filter bands.
 *
 * <br> threshold1 sets the threshold to trigger a pick event (potential pick). This threshold is
 * reached when the (clipped) characteristic function for any filter band exceeds threshold1.
 * <br>threshold2 sets the threshold to declare a pick (pick will be accepted when tUpEvent
 * reached). This threshold is reached when the integral of the (clipped) characteristic function
 * for any filter band over the window tUpEvent getPciexceeds threshold2 * tUpEvent (i.e. the average
 * (clipped) characteristic function over tUpEvent is greater than threshold2
 *
 * <br>tUpEvent determines the maximum time the integral of the (clipped) characteristic function is
 * accumulated after threshold1 is reached (pick event triggered) to check for this integral
 * exceeding threshold2 * tUpEvent (pick declared).
 *
 * @author U.S. Geological Survey <ketchum at usgs.gov>
 */
public class CWBFilterPicker6 extends CWBPicker /*implements PickerInterface*/ {

  public static final String pickerType = "filterpicker";
  private static final long WARMUP_MS = 600000; // time before original starttime to start process to allow warmup
  //private int outputport = 2061;
  /**
   * the filter window (filterWindow) in seconds determines how far back in time the previous
   * samples are examined. The filter window will be adjusted upwards to be an integer N power of 2
   * times the sample interval (deltaTime). Then numRecursive = N + 1 "filter bands" are created.
   * For each filter band n = 0,N the data samples are processed through a simple recursive filter
   * backwards from the current sample, and picking statistics and characteristic function are
   * generated. Picks are generated based on the maximum of the characteristic function values over
   * all filter bands relative to the threshold values threshold1 and threshold2.
   */
  private double filterWindow = 0.8;                   // Filter window (in sec) (2.4)
  /**
   * the long term window (longTermWindow) determines: a) a stabilization delay time after the
   * beginning of data; before this delay time picks will not be generated. b) the decay constant of
   * a simple recursive filter to accumulate/smooth all picking statistics and characteristic
   * functions for all filter bands.
   */
  private double longTermWindow = 6.0;               // Long-term filter window (in sec)
  /**
   * threshold1 sets the threshold to trigger a pick event (potential pick). This threshold is
   * reached when the (clipped) characteristic function for any filter band exceeds threshold1.
   */
  private double threshold1 = 9.36;             // (20.)
  /**
   * threshold2 sets the threshold to declare a pick (pick will be accepted when tUpEvent reached).
   * This threshold is reached when the integral of the (clipped) characteristic function for any
   * filter band over the window tUpEvent exceeds threshold2 * tUpEvent (i.e. the average (clipped)
   * characteristic function over tUpEvent is greater than threshold2).
   */
  private double threshold2 = 9.21;            // (8.0) 
  /**
   * tUpEvent determines the maximum time the integral of the (clipped) characteristic function is
   * accumulated after threshold1 is reached (pick event triggered) to check for this integral
   * exceeding threshold2 * tUpEvent (pick declared).
   */
  private double tupEvent = 0.388;             // (0.2
  /**
   * tUpEventMin sets a minimum time window after threshold1 is reached (pick event triggered) in
   * which an integral of the (clipped) characteristic function is accumulated to confirm a pick. A
   * pick is confirmed if this integral exceeds threshold1 * tUpEventMin.
   */
  private double tupEventMin = 1.;
  private String bandParms;   // This is of the form fr,fact:fr,fact:fr,fact where fr is in Hz and fact is the scaling factor for thresholds
  private boolean dbg;
  private boolean makefiles;
  private FP6Instrumentation instrumentation;   // If not null, call back during processing for analysis
  private EdgeThread par;
  private USGSFP6Extension picker;
  private FilterPicker6_BandParameters[] bandParameters;
  private final double[] bandTemp1 = new double[2];
  private final double[] bandTemp2 = new double[2];
  private int nbands = 0;
  // Pick related variables
  private float pickoffset;
  private float pickerror;
  private GregorianCalendar picktime = new GregorianCalendar();
  private boolean first = true;
  private boolean ready;
  private StringBuilder tmpsb = new StringBuilder(1000);

  public void registerInstrumentation(FP6Instrumentation inst) {
    instrumentation = inst;
  }

  public USGSFP6Extension getFilterPicker6WithExtensions() {
    return picker;
  }

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
    Util.clear(statussb).append("fw=").append(filterWindow).append(" ltw=").append(longTermWindow).
            append(" thres1=").append(threshold1).append(" thres2=").append(threshold2).append(" tup=").append(tupEvent);
    return statussb;
  }

  public CWBFilterPicker6(String argline, String tag, FP6Instrumentation inst) {
    super(argline, tag, null);
    Util.setModeGMT();
    instrumentation = inst;
    if(author == null) {
      prta("**** Author not set use default");
      author = "FP6-TEST";
    }
    if(author.equals("")) {
      prta("**** Author is empty, use default");
      author = "FP6-TEST";
    }
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
    //beginTime.setTimeInMillis(getBeginTime().getTimeInMillis());
    //Util.clear(nsclsb).append(nscl);
    try {
      setArgs(getPickerArgs().toString());
    } catch (IOException e) {
      e.printStackTrace(getPrintStream());
    }

    for (int i = 0; i < args.length; i++) {
      if (args[i].length() == 0) {
        continue;
      }
      if (args[i].contains(">")) {
        break;
      } else if (args[i].equals("-tfilter")) {
        filterWindow = Double.parseDouble(args[i + 1]);
        i++;
      } else if (args[i].equals("-tlong")) {
        longTermWindow = Double.parseDouble(args[i + 1]);
        i++;
      } else if (args[i].equals("-thold1")) {
        threshold1 = Double.parseDouble(args[i + 1]);
        i++;
      } else if (args[i].equals("-thold2")) {
        threshold2 = Double.parseDouble(args[i + 1]);
        i++;
      } else if (args[i].equals("-tup")) {
        tupEvent = Double.parseDouble(args[i + 1]);
        i++;
      } else if (args[i].equals("-tupmin")) {
        tupEventMin = Double.parseDouble(args[i + 1]);
        i++;
      } else if (args[i].equals("-bands")) {
        bandParms = args[i + 1];
        i++;
      } else if (args[i].equals("-empty")) {
      } // this just abates a warning, no effect
      else if (args[i].equals("-fp6dbg")) {
        dbg = true;
      } else {
        switch (parentSwitch(args[i])) {      // Does parent handle this switch?
          case 1:
            i++;        // yes with an argument
            break;
          case 0:
            break;      // yes, without an argument
          default:      // No list it out.
            prta(i + "FP " + getNSCL() + " ** Unknown argument to FilterPicker=" + args[i]);
            SendEvent.edgeSMEEvent("FP6BadArg","FP6 unknown arg="+args[i], (CWBFilterPicker6) this);
            break;
        }
      }
    }
    double [] freqs;
    double [] facts;
    while(true) {
      if (bandParms == null) {
        bandParms = "0.5,1.:1.,1.:2.,1.:4.,0.5:8.,0.3:16.,0.3:32.,.3";
        prta(" ****** Band parameters not setup using default!");
        SendEvent.edgeSMEEvent("FP6BandsNull","Bands using defaults ", (CWBFilterPicker6) this);
      }
      String[] parts = bandParms.split("[-:]");      // either colons or hyphens can be used to separate the pairs
      freqs = new double[parts.length];
      facts = new double[parts.length];
      try {
        for (String part : parts) {
          String[] bp = part.split(",");
          if (bp.length == 2) {
            double freq = Double.parseDouble(bp[0]);
            double factor = Double.parseDouble(bp[1]);
            if (freq > 0.49999 && freq < rate / 2 * 0.75) {
              freqs[nbands] = freq;
              facts[nbands] = factor;
              nbands++;
            }
          }
        }
        break;
      } catch (RuntimeException e) {
        prta("***** Bands codes could not be parsed.  use defaults bands="+bandParms+ " e="+e);
        bandParms = "0.5,1.:1.,1.:2.,1.:4.,0.5:8.,0.3:16.,0.3:32.,.3";
        SendEvent.edgeSMEEvent("FP6BadBands", "Bands parse err="+e, (CWBFilterPicker6) this);
      }
    }
    bandParameters = new FilterPicker6_BandParameters[nbands];
    for (int i = 0; i < nbands; i++) {
      bandParameters[i] = new FilterPicker6_BandParameters(1. / freqs[i], facts[i], FilterPicker6_BandParameters.DisplayMode.DISPLAY_MODE_FREQ);
    }
    // Bubble sort to increasing period
    for (int i = 0; i < nbands - 1; i++) {
      for (int j = i + 1; j < nbands; j++) {
        if (bandParameters[i].period > bandParameters[j].period) {
          FilterPicker6_BandParameters tmp = bandParameters[i];
          bandParameters[i] = bandParameters[j];
          bandParameters[j] = tmp;
        }
      }
    }
    for (int i = 0; i < nbands; i++) {
      prta(bandParameters[i].toString());
    }
    //  Vassallo, M, Satriano, C. and A. Lomax (2012).   Automatic
    //  PickerInterface Developments and Optimization: A Strategy for Improving
    //  the Performances of Automatic Phase Pickers, Seismological
    //  Research Letters, 83 (3), 541-554
    // FilterPicker6 Paper 09-03-2015 by A. Lomax with revisions
    try {
      picker = new net.alomax.timedom.fp6.USGSFP6Extension("en_US", nscl.get(0), this, getTitle(),
              bandParameters, longTermWindow,
              threshold1, threshold2, tupEvent, tupEventMin, 1);
      picker.setResultsType(BasicPicker.RESULT_PICKS);    // Tell it we want picks and not RESULT_TRIGGER or RESULT_CHAR
      picker.setUseMemory(true);                // Tell it to keep memory between calls
      prta(Util.clear(tmpsb).append("bands=").append(bandParms));
      for (int i = 0; i < nbands; i++) {
        picker.getBandCorners(i, bandTemp1);
        prt(Util.clear(tmpsb).append(bandParameters[i]).append(" rate=").append(1. / bandParameters[i].period).
                append(" fl=").append(Util.df23(bandTemp1[0])).append(" fh=").append(Util.df23(bandTemp1[1])));
      }
      if (getMakeFiles()) {
        picker.setSaveBandCharFunc(true);
      }
    } catch (TimeDomainException e) {
      prta("Failed to setup picker " + nscl.get(0) + " e=" + e);
      SendEvent.edgeSMEEvent("FPSetupFail", "FilterPicker setup failed " + nscl.get(0) + " e=" + e, (CWBFilterPicker6) this);
    }
    ready = true;
  }

  @Override
  public void writestate() {
    synchronized (statussb) {
      Util.clear(statussb);
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
        }
        String[] parts = line.split("#");
        if (parts.length == 1) {
          continue;      // nothing after the #
        }
        parts[1] = parts[1].trim();
        if (parts[0].equalsIgnoreCase("filterWindow")) {
          filterWindow = Double.parseDouble(parts[1]);
        } else if (parts[0].equalsIgnoreCase("longtermwindow")) {
          longTermWindow = Double.parseDouble(parts[1]);
        } else if (parts[0].equalsIgnoreCase("threshold1")) {
          threshold1 = Double.parseDouble(parts[1]);
        } else if (parts[0].equalsIgnoreCase("threshold2") || parts[0].equals("Threshond2")) {
          threshold2 = Double.parseDouble(parts[1]);
        } else if (parts[0].equalsIgnoreCase("tupevent")) {
          tupEvent = Double.parseDouble(parts[1]);
        } else if (parts[0].equalsIgnoreCase("tupeventmin")) {
          tupEventMin = Double.parseDouble(parts[1]);
        } else if (parts[0].equalsIgnoreCase("bands")) {
          bandParms = parts[1];
        } else {
          prt("****** Unknown tag in state file s=" + line);
        }
      }
    }
    prta("CWBFP: state file read " +/*callback.*/ getNSCL() + " " + start + " to " + stop + " fW=" + filterWindow + " lt=" + longTermWindow
            + " th1=" + threshold1 + " th2=" + threshold2 + " tup=" + tupEvent);
    // adjust beginTime to the warmup interval
  }

  @Override
  public void clearMemory() {
    picker.clearMemory();
  }
  private float rawdata[];

  @Override
  public void apply(GregorianCalendar time, double rate, int[][] data, int npts) {
    try {
      if (rawdata == null) {
        rawdata = new float[npts * 2];
      }
      if (rawdata.length < npts) {
        rawdata = new float[npts * 2];
      }
      for (int i = 0; i < npts; i++) {
        rawdata[i] = data[0][i];
      }
      picker.apply(1. / rate, rawdata, npts);
      if (first) {
        prta("Filter setup :" + picker.toString());
        first = false;
      }  // Was dump of mem structures
      if (instrumentation != null) {
        instrumentation.doApply(picker, nscl.get(0), time, npts, data[0], rate, makefiles);
      }
    } catch (TimeDomainException e) {
      prta("TimeDomainException e=" + e);
      e.printStackTrace(par.getPrintStream());
    }

    // Handle any picks that occurred.
    if (picker.getPickData().size() > 0 && getTime() >= getBeginTime()) {
      ArrayList<PickData> picks = picker.getPickData(); // User needs to clear the picks array when done or it accumulates
      if (instrumentation != null) {
        instrumentation.doPicks(picker, nscl.get(0), picks, time);
      }

      // for each pick in the arraylist, get all of the parameters, compute the time, so it can be
      // submitted as a new pick.  TODO : perhaps multiple picks should be combined in some way?
      for (PickData pick : picks) {
        FilterPicker6PickData pk = (FilterPicker6PickData) pick;
        boolean[] isTriggered = picker.isTriggered(pk, tmpsb);         // Array of booleans with triggered bands
        int[] bandIndices = picker.getBandWidthTriggered(isTriggered); // Get the indices of the triggered bands
        picker.getBandCorners(bandIndices[0], bandTemp1);               // get the corner frequencies of the triggere bands
        picker.getBandCorners(bandIndices[1], bandTemp2);
        double lowFreq = Math.min(bandTemp1[0], bandTemp2[0]);
        double highFreq = Math.max(bandTemp1[1], bandTemp2[1]);
        pickoffset = (float) ((pk.indices[0] + pk.indices[1]) / 2);
        pickerror = (float) ((pk.indices[1] - pk.indices[0]) / rate);
        picktime.setTimeInMillis(time.getTimeInMillis());
        picktime.add(Calendar.MILLISECOND, (int) (1000 * pickoffset / rate));
        pick(pickerType, picktime, 0., pk.period, "P", pickerror, // amplitude is zero as FP6 does not generate one
                (pk.polarity == PickData.POLARITY_POS ? "up" : (pk.polarity == PickData.POLARITY_NEG ? "down" : null)),
                null, lowFreq, highFreq, pk.amplitude);
      }
      picks.clear();
    }

  }

  public static void main(String[] args) throws InterruptedException, IOException {
    Util.init("edge.prop");
    Util.setModeGMT();
    CWBPicker picker;
    GregorianCalendar newStart = new GregorianCalendar();
    GregorianCalendar newEnd = new GregorianCalendar();
    EdgeThread.setMainLogname("cwbfp6");
    //args = "-sort -file sitechan_badstationRemoved.sort ".split("\\s");

    // if no args, make the development one
    if (args.length == 0) {
      long start = (newStart.getTimeInMillis() - 86400000L * 2) / 86400000L * 86400000L;
      newStart.setTimeInMillis(start);
      Util.prt("start=" + Util.ascdatetime2(start));
      newEnd.setTimeInMillis(start + 86400000L);     // process one day
      //QuerySpan.setDebugChan("USDUG  BHZ00"); 
      /*CWBPicker cwbpicker = 
              new CWBPicker("-mf -json path;./JSON -c USDUG--BHZ00 -h 137.227.224.97 -b 2015-09-26-00:00 -e 2015-09-27-00:00 "+
                      "-auth FP6-TEST -agency US-NEIC -db localhost/3306:status:mysql:status -blocksize 10. >>USGSFP","CWBP", null);*/
      //String cmd =  "-mf -1 -json path;./JSON -c USDUG--BHZ00 -h 137.227.224.97 -b "+Util.ascdatetime(newStart)+
      //        " -e "+Util.ascdatetime(newEnd)+
      //        " -auth FP6-TEST -agency US-NEIC -db localhost/3306:status:mysql:status -blocksize 10. "+
      //        " -bands 0.5,1.:1.,1.:2.,1.:4.,1.:8.,1.:16.,1.:32.,1. -tlong 300.0 -thold1 6."+
      //        " -thold2 5.0 -tup 4. -tupmin .25 >>USGSFP";
      String cmd = "-mf -1 -json path;./JSON -c GSKAN17HHZ01,GSKAN17HHN01,GSKAN17HHE01 -h 137.227.224.97 -b 2016/09/08-00:00"
              + " -e 2017/12/24-00:00"
              + " -auth FP6-TEST -agency US-NEIC -db localhost/3306:status:mysql:status -blocksize 10. "
              + " -bands 0.5,1.:1.,1.:2.,1.:4.,1.:8.,1.:16.,1.:32.,1. -tlong 300.0 -thold1 6."
              + " -thold2 5.0 -tup 4. -tupmin .25 >>USGSFP";
      picker = new CWBFilterPicker6(cmd, "CWBFP", null);
      //cwbpicker.setPicker(picker);
      //              new USGSFP6("-c USDUG--BHZ00 -h 137.227.224.97 -b 2014/03/10-00:00 -e 2014-03-11 >>USDUGFP2",
      //              "FP:USDUG", null);
      //             new USGSFP6("-c GSOK029HHZ00 -h 137.227.224.97 -b 2014/02/19-00:00 -e 2014-02-20 >>OK029FP2",
      //              "FP:GSOK029", null);
      //              new USGSFP6("-c GSOK029HHZ00 -h 137.227.224.97 -b 2014/02/19-00:00 -e 2014-02-20 >>OK029FP2",
      //              "FP:GSOK029", null);
      while (picker.isAlive()) {
        Util.sleep(1000);
        if (picker.segmentDone()) {
          newStart.setTimeInMillis(newStart.getTimeInMillis() - 86400000L);
          picker.runSegment(newStart, 86400., 0, -1, -1., -1., -1.);
        }
      }
    } else {        // Build up the command line and run 
      String argline = "";
      String nscl = null;
      String listFile = null;
      String template = null;
      boolean sorted = false;
      FP6Instrumentation instrument = null;
      for (int i = 0; i < args.length; i++) {
        argline += args[i].trim() + " ";
        if (args[i].equalsIgnoreCase("-c")) {
          nscl = args[i + 1];
          argline += args[i + 1] + " ";
        } else if (args[i].equalsIgnoreCase("-db")) {
          Util.setProperty("PickDBServer", args[i + 1]);
        } else if (args[i].equalsIgnoreCase("-i")) {
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
        } else if (args[i].equals("-file")) {
          String[] parts = args[i + 1].split(":");
          listFile = parts[0];
          argline += args[i + 1] + " ";
          i++;
        } else if (args[i].equals("-template")) {
          template = args[i + 1];
          argline += args[i + 1] + " ";
          i++;
        } else if (args[i].equals("-sort")) {
          sorted = true;
        }

      }
      if (sorted) {
        try {
          int nline = 0;
          BufferedReader in = new BufferedReader(new FileReader(listFile));
          String line;
          double duration;
          long oldStart = -1;
          long oldEnd = -1;
          String oldLine = "";

          while ((line = in.readLine()) != null) {
            line = line.trim();
            nline++;
            if (line.length() < 10) {
              continue;
            }
            if (line.charAt(0) == '#' || line.charAt(0) == '!') {
              continue;
            }
            String[] parts = line.split("\\s");
            String chan = null;
            for (int i = 0; i < parts.length; i++) {
              if (parts[i].equals("-c")) {
                chan = parts[i + 1];
                i++;
              }
              if (parts[i].equals("-template")) {
                template = parts[i + 1];
                i++;
              }
              if (parts[i].equals("-b")) {
                newStart.setTimeInMillis(Util.stringToDate2(parts[i + 1]).getTime());
              }
              if (parts[i].equals("-e")) {
                newEnd.setTimeInMillis(Util.stringToDate2(parts[i + 1]).getTime());
              }
            }
            if (!(newStart.getTimeInMillis() >= oldEnd || newEnd.getTimeInMillis() < oldStart)) {
              double overlap = (oldEnd - newStart.getTimeInMillis()) / 1000.;
              if (overlap > 600.) {
                Util.prt("Overlapping :"
                        + (newStart.getTimeInMillis() - oldStart < 300 ? "*** starts are close! " + (newStart.getTimeInMillis() - oldStart) / 1000. : "")
                        + " overlap=" + overlap
                        + "\n" + oldLine + "\n" + line);
              }
            }
            if (newEnd.getTimeInMillis() - newStart.getTimeInMillis() < 300000) {
              Util.prt("Interval is very short **** : "
                      + (newEnd.getTimeInMillis() - newStart.getTimeInMillis()) / 1000. + "s " + line);
            }
            oldStart = newStart.getTimeInMillis();
            oldEnd = newEnd.getTimeInMillis();
            oldLine = line;
          }
          System.exit(0);
        } catch (IOException e) {
          e.printStackTrace();
        }
        catch(RuntimeException e) {
          Util.prta("Runtime caught e="+e);
          e.printStackTrace();
          System.exit(1);
        }      }

      // Check to see if there is a list file, if so, use it to process many picks at once.
      if (listFile != null) {
        //ArrayList<CWBFilterPicker6> pickers = new ArrayList<>(1000);
        TreeMap<String, CWBFilterPicker6> pickers = new TreeMap<>();
        MemoryChecker memchk = new MemoryChecker(60, null);
        try {
          int nline = 0;
          BufferedReader in = new BufferedReader(new FileReader(listFile));
          String line;
          long oldStart = -1;
          long oldEnd = -1;
          String oldLine = "";
          String oldChan = "";
          while ((line = in.readLine()) != null) {
            if(nline % 10000 == 9999) Util.prta(Util.getThreadsString());
            line = line.trim();
            nline++;
            if (line.length() < 10) {
              continue;
            }
            if (line.charAt(0) == '#' || line.charAt(0) == '!') {
              continue;
            }
            String[] parts = line.split("\\s");
            String chan = null;
            for (int i = 0; i < parts.length; i++) {
              if (parts[i].equals("-c")) {
                chan = parts[i + 1];
                i++;
              }
              if (parts[i].equals("-template")) {
                template = parts[i + 1];
                i++;
              }
              if (parts[i].equals("-b")) {
                newStart.setTimeInMillis(Util.stringToDate2(parts[i + 1]).getTime());
              }
              if (parts[i].equals("-e")) {
                newEnd.setTimeInMillis(Util.stringToDate2(parts[i + 1]).getTime());
              }
            }
            if (chan == null || template == null) {
              Util.prt("Skipping line=" + argline + " No template or channel");
              continue;
            }
            if (newEnd.getTimeInMillis() - newStart.getTimeInMillis() < 300000) {
              Util.prt("Skip line - interval too short=" + line + " " + (newEnd.getTimeInMillis() - newStart.getTimeInMillis()) / 1000.);
              continue;
            }

            oldStart = newStart.getTimeInMillis();
            oldEnd = newEnd.getTimeInMillis();
            oldChan = chan;
            oldLine = line;

            String arg = argline + line + " >> PL/" + (chan.replaceAll(" ", "_") + "-" + template).trim().replaceAll(" ", "");
            CWBFilterPicker6 fp6 = pickers.get(chan);
            if (fp6 == null) {
              fp6 = new CWBFilterPicker6(arg, "CWBFP", instrument);
              pickers.put(chan, fp6);
              Util.prta("Start new FP6 size=" + pickers.size() + " nline=" + nline + " " + chan + " "
                      + Util.ascdatetime2(newStart) + "_" + Util.ascdatetime2(newEnd) + " "
                      + (newEnd.getTimeInMillis() - newStart.getTimeInMillis()) / 1000.);
            } else if (!fp6.isAlive()) {
              Util.prta("** FP6 found not alive  " + fp6);
              fp6 = new CWBFilterPicker6(arg, "CWBFP", instrument);
              pickers.put(chan, fp6);
              Util.prta("* Replace FP6 size=" + pickers.size() + " nline=" + nline + " " + arg);
            } else {
              int loop = 0;
              while (!fp6.segmentDone()) {
                if (loop == 0) {
                  Util.prta("wait for " + chan + " to be done ");
                }
                Util.sleep(100);
                if (loop++ % 100 == 99) {
                  Util.prta("  **** Still waiting for " + fp6);
                }
                if (loop > 2400) {
                  Util.prt("  ******* wait over 240 seconds.  Break out *******");
                  break;
                }
              }
              Util.prta("Start new segment for " + chan + " nline=" + nline + " " + Util.ascdatetime2(newStart)
                      + "-" + Util.ascdatetime2(newEnd) + " " + (newEnd.getTimeInMillis() - newStart.getTimeInMillis()) / 1000.);
              fp6.runSegment(newStart.getTimeInMillis(), newEnd.getTimeInMillis());
            }

          }
          Util.prta("Wait for all segment pickers to exit");
          int loop = 0;
          for (;;) {
            Object[] keys = pickers.keySet().toArray();
            boolean allDone = true;
            for (Object key : keys) {
              CWBFilterPicker6 fp6 = pickers.get(key.toString());
              if (!fp6.isAlive()) {
              } else if (fp6.segmentDone()) {
                fp6.terminate();
              } else {
                if (loop % 30 == 29) {
                  Util.prta("Still running" + fp6.toString());
                }
                allDone = false;
              }
            }
            if (allDone) {
              break;
            }
            Util.sleep(1000);

          }
          Util.prta("End of execution");
          System.exit(0);
          /*// If the maximum number of threads are running, check for some to have exited, before starting a new one
            if(pickers.size() >= maxThreads) {
              Util.prta("Wait for some pickers to exit to start new ones size="+pickers.size()+" max="+maxThreads+" nline="+nline);
              while(pickers.size() >= maxThreads) {
                Util.sleep(5000);
                for(int i=pickers.size() -1; i>=0; i--) {
                  CWBFilterPicker6 pick = pickers.get(i);
                  if(!pick.isAlive() || pick.segmentDone()) {
                    pick.terminate();
                    Util.prta(i+" terminate "+pick.toString());
                    pickers.remove(i);
                  }
                }
              }
              Util.prta("Number of pickers after purge="+pickers.size());
            }
          }
          
          // All threads started, wait for them all to finish
          while(pickers.size() > 0) {
            Util.prta("Wait for all pickers to end size="+pickers.size());
            Util.sleep(5000);
            for(int i=pickers.size() -1; i>=0; i--) {
              CWBFilterPicker6 pick = pickers.get(i);
              if(pickers.size() <= 1) {
                Util.prt(pick.toString());
              }
              if(!pick.isAlive() || pick.segmentDone()) {
                pick.terminate();
                Util.prta(i+" terminate "+pick.toString());
                pickers.remove(i);
              }
            }
          }
          Util.prta("All pickers are now down size="+pickers.size());*/
        } catch (IOException e) {
          Util.prta("Exception reading list file");
          e.printStackTrace();
        }
        catch(RuntimeException e) {
          Util.prta("Runtime caught e="+e);
          e.printStackTrace();
          System.exit(1);
        }
      } else if (nscl == null) {
        Util.exit("-c is mandatory for manual starts");
      } else {
        argline = argline + " >> PL/" + nscl.replaceAll("-", "_").replaceAll(" ", "_").trim();
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
