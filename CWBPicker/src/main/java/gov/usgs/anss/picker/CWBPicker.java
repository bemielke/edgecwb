/*
 *  This software is in the public domain because it contains materials 
 *  that originally came from the United States Geological Survey, 
 *  an agency of the United States Department of Interior. For more 
 *  information, see the official USGS copyright policy at 
 *  http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.picker;
//import gov.usgs.anss.query.QueryRing;

import gov.usgs.anss.util.Util;
import gov.usgs.anss.edgethread.EdgeThread;
import gov.usgs.alarm.SendEvent;
import gov.usgs.anss.edgethread.ManagerInterface;
//import gov.usgs.anss.edgeoutput.GlobalPickSender;
//import gov.usgs.anss.edgeoutput.JSONPickGenerator;
import gov.usgs.cwbquery.QueryRing;
import gov.usgs.anss.utility.MakeRawToMiniseed;
import gov.usgs.anss.seed.MiniSeed;
import gov.usgs.detectionformats.*;

import gnu.trove.iterator.TLongObjectIterator;
import java.io.*;
//import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.ArrayList;

/**
 * This class provides the common data access and pick reporting from the NEIC CWB program QueryMom.
 * A USGS generated picker, or an interface to an external picker is provided by extending this
 * class and providing the implementation of the abstract methods. All picker threads run in real
 * time use the PickerManager class to start up the pickers which implement this class. The
 * PickerManager class also provides unique sequences for all pickers by providing a access to write
 * picks into a database and the database ID is the unique sequence.
 *
 * To picker implementors, please see the abstract methods that your class need to implement when
 * extending this class. An example is the CWBFilterPicker which implements this and causes the
 * standard interface to all the FilterPicker library from Anthony Lomax as it was written. This
 * allows the picker implementation to remained undisturbed by its use by the NEIC/Edge/CWB software
 * as much as possible.
 * <p>
 * These command line options are shared by all pickers and control the realtime, DB, time segments,
 * and JSON reporting. The actual extending class will implement its own options for things specific
 * to that picker class. All of these options end up written into the state file which is used to
 * pick up the real time processing. As such these parameters might be overridden by the state file
 * if this is a continuous realtime process thread.
 * <p>
 * The JSONDectionSender can be obtained many ways :<br>
 * 1) It can be set on the creation by including the sender in the constructor arguments<br>
 * 2) It can be specified on the command line here in which case this overrides and is individual to
 * each picker<br>
 * 3) It can be defined in the PickerManager either at instantiation or by static setJSONSender().
 * This become the sender if none of the above has set on.<br>
 * 4) It can be null in which case no picks JSON messages are created.<br>
 * <br> 
 * <PRE>
 * switch   args    description
 * -1               If present this is a segment picker, not continuous
 * -nodb            If present, set DBServer and PickDBServer to "NoDB"
 *
 * For properties that come from a template file (separator for key#pairs is '#':
 * Property      switch args      description
 * TemplateFile -template file   ! this refers to the template manually create or from the picker table
 *
 * NSCL         -c NNSSSSSCCCLL  The channel to process, comes from the -c mostly but can be in preconfigured template
 * Blocksize    -blocksize       The processing block size in seconds
 * PickDBServer -db   dbURL      Set the DB where the status.globalpicks us located (used to generate ID numbers) 'NoDB' is legal
 * Author       -auth author     Set the author (def=FP6)
 * Agency       -agency agency   Set the agency
 * Title        -t     title     A descriptive title for this instance (example FP6-RP)
 * CWBIPAdrdess -h     cwb.ip    Set source of time series data (def=cwbpub)
 * CWBPort      -p     port      Set the CWB port (def=2061)
 * MakeFiles    -mf              If present, this instance will make files in MiniSeed from parameters.
 * PickerTag    -f     pickerTag Some tag associated with the picker (def=NONE)
 * OverrideTopic -topic Kafka topic The kafka topic to use when sending picks from this picker (def=null)
 * JSONArgs     -json  1;2&3;4   @see gov.usgs.picker.JsonDetectionSender for details
 *
 *
 * For properties kept in the state file :
 * StartTime    -b     begintime Starting time, not valid on a segment picker
 * EndTime      -e     endTIme   Ending Time
 * Rate         -rt    Hz        The digit rate, else one from the time series will be used
 * </PRE>
 *
 * @author U.S. Geological Survey  <ketchum at usgs.gov>
 */
public abstract class CWBPicker extends EdgeThread implements ManagerInterface {

  // State file variables are static and shared between all threads, must be synchronized on usage
  protected static final StringBuilder statesb = new StringBuilder(400);
  private static byte[] statebuf = new byte[400];
  public static final GregorianCalendar date2099 = new GregorianCalendar(2099, 11, 31, 23, 59, 59);
  protected PickerManager pickerManager;
  // attributes for PickerInterface
  private GregorianCalendar gtime = new GregorianCalendar(); // Time to process next
  private long time;
  private GregorianCalendar gbeginTime = new GregorianCalendar();  // Time to start process, adjust for "warmup interval"
  private long beginTime;
  private long endtime;
  private GregorianCalendar gendtime = new GregorianCalendar();
  protected ArrayList<String> nscl = new ArrayList<>(3);
  protected ArrayList<StringBuilder> nsclsb = new ArrayList<>(3);
  protected String cwbipaddress = "137.227.224.97";   //DEBUG -
  protected int cwbport = 2061;
  protected double blocksize = 10.;
  protected long numberofpicks = 0; 
  protected long numberofgaps = 0;
  protected String stateFile;
  //protected GlobalPickSender globalPickSender;
  protected String jsonArgs;
  protected JsonDetectionSender jsonPickSender;
  protected String overrideTopic;
  protected Pick jsonPick;
  private boolean check;
  private boolean noApply;        // if true, do not actually feed data to picker
  private String argsin;
  private boolean dbg;
  protected boolean makeFiles;  // when true MiniSeed files are written for the data and Char function and trigger
  protected MakeRawToMiniseed maker;
  protected long inbytes;
  protected long outbytes;
  protected double rate;
  private int priority = Thread.MIN_PRIORITY + 1;
  protected String agency = "US-NEIC";
  protected String author;
  protected String title = "";
  protected String pickerTag = "NONE";
  protected String threadID;
  protected String templateFile;    // always comes from the -template command line argument
  //protected String templateState;   // Template file from reading the state file, should match template file
  protected boolean idle;       // if true, this thread is waiting for something to reset start and end time.
  protected int backazm = -1;          // can be set by caller to runSegment()
  protected double slowness;      // can be set by caller to runSetment();
  protected double backazmErr;    // can be set by caller to runSetment();
  protected double powerRatio;    // can be set by caller to runSetment();
  private int state;
  protected StringBuilder pickerArgs = new StringBuilder(100);
  private final ArrayList<QueryRing> waveformrings = new ArrayList<>(3);

  // segment picker variable
  protected boolean segmentPicker;
  protected long pickID;        // if there is a prior pickID it is set here and no new pick ID is created.
  protected int pickerID;       // this ID is passed into all cwbpick tables

// Unsure
  protected String path = "./Picker/";
  protected boolean createStateFile = false;

  //protected PickerInterface picker;
  public String getAuthor() {
    return author;
  }

  public String getAgency() {
    return agency;
  }

  public final String getTitle() {
    return title;
  }

  public String getTemplateFile() {
    return templateFile;
  }

  public int getBackAzm() {
    return backazm;
  }

  public double getSlowness() {
    return slowness;
  }

  /**
   * This method builds a StringBuilder of the state file parameters specific to the extending
   * picker for key-value pairs that change over time (not things in the template) and then calls
   * the parent writeState(StringBUilder) to actually combine and write the state file. For many
   * pickers this will do nothing other might record a background noise level to help the picker
   * warmup quicker, etc.
   *
   * @throws FileNotFoundException
   * @throws IOException
   */
  abstract void writestate() throws FileNotFoundException, IOException;

  /**
   * this will be called when a gap in the data is detected to allow the picker to reset its history
   * or other parameters.
   */
  abstract void clearMemory();

  /**
   *
   * @param time
   * @param rate Data rate in hertz
   * @param rawdata Raw data as integers, this array can be modified in the implementation
   * @param npts The number of points in rawdata that are valid
   */
  abstract void apply(GregorianCalendar time, double rate, int[][] rawdata, int npts);

  /**
   * The CWBPicker handles many arguments from the state file itself. These are : Title, Starttime,
   * Endtime, NSCL, CWBipaddress,CWBport Blocksize, Agency, Author and PickDBServer Each picker can
   * have addition data specific to that picker. The extending class needs to define these are read
   * the in setArgs() and provide them as a StringBuilder in the writestate() method when it calls
   * writeStateFile of the parent class. The user can use the static statesb variable to make the
   * state, but needs to synchronize its usage. Example :
   * <PRE>
   *   @Override
   *  protected final void writestate() throws FileNotFoundException, IOException {
   *    synchronized(statesb) {
   *      Util.clear(statesb);
   *       statesb.append("filterWindow# ").append(Util.df24(filterWindow)).append("\n");
   *      .  //write out all of the filter specific key#value pairs.
   *      writeStateFile(statesb);
   *   }
   * }
   * </PRE>
   *
   * @param s String with all of the state file parameters not handled by CWBPicker - i.e. those for
   * the specific picker
   * @throws FileNotFoundException
   * @throws IOException
   */
  abstract void setArgs(String s) throws FileNotFoundException, IOException;

  /**
   * Get number of millis the method need in order to warm up on a segmented call.
   *
   * @return Number of millis of warmup needed.
   */
  abstract long getWarmupMS();

  /**
   * indicate the picker is ready to pick
   *
   * @return Whether all setup is completed and picker is ready.
   */
  abstract public boolean isReady();

  /**
   * The extending class needs to set the terminate variable to true in its concrete method.
   */
  @Override
  abstract public void terminate();

  //@Override
  public final boolean getMakeFiles() {
    return makeFiles;
  }

  ///@Override
  public final double getRate() {
    return rate;
  }

  public final int getNChan() {
    return nsclsb.size();
  }

  //@Override
  public final ArrayList<StringBuilder> getNSCL() {
    return nsclsb;
  }

  // @Override
  public final MakeRawToMiniseed getMaker() {
    return maker;
  }

  //@Override
  public final Pick getJSONPick() {
    return jsonPick;
  }

  //@Override
  //public GlobalPickSender getGlobalPickSender() {return globalPickSender;}
  // @Override
  public final EdgeThread getEdgeThread() {
    return this;
  }

  public final StringBuilder getPickerArgs() {
    return pickerArgs;
  }

  /**
   *
   * @return The time that is current pointer is at right now.
   */
  public long getTime() {
    return time;
  }

  /**
   *
   * @return The begin time for the run
   */
  public final long getBeginTime() {
    return beginTime;
  }

  /**
   *
   * @return End time for the run
   */
  public final long getEndTime() {
    return endtime;
  }

  /**
   * @param s Set the JSONSender to this value, if null, the JSON messages are turned off
   */
  public void setJSONSender(JsonDetectionSender s) {
    jsonPickSender = s;
  }

  /**
   * Get the JSONDetectionSender being used by this picker
   *
   * @return The JsonDetectionSender or null if none has been set
   */
  public JsonDetectionSender getJSONSender() {
    return jsonPickSender;
  }

  public String getSeedname(int i) {
    return nscl.get(i);
  }

  @Override
  public void setCheck(boolean t) {
    check = t;
  }

  public void setTime(long t) {
    time = t;
    gtime.setTimeInMillis(time);
  }

  public void setTime(GregorianCalendar t) {
    time = t.getTimeInMillis();
    gtime.setTimeInMillis(time);
  }

  public void setBeginTime(long t) {
    beginTime = t;
    gbeginTime.setTimeInMillis(beginTime);
  }

  public void setBeginTime(GregorianCalendar t) {
    beginTime = t.getTimeInMillis();
    gbeginTime.setTimeInMillis(beginTime);
  }

  public void setEndTime(long t) {
    endtime = t;
    gendtime.setTimeInMillis(endtime);
  }

  public void setEndTime(GregorianCalendar t) {
    endtime = t.getTimeInMillis();
    gendtime.setTimeInMillis(endtime);
  }

  @Override
  public boolean getCheck() {
    return check;
  }

  @Override
  public String getArgs() {
    return argsin;
  }

  public void setDebug(boolean t) {
    dbg = t;
  }

  @Override
  public long getBytesIn() {
    long t = inbytes;
    inbytes = 0;
    return t;
  }

  @Override
  public long getBytesOut() {
    return outbytes;
  }

  @Override
  public String toString() {
    return threadID + ":" + title + "/" + priority + (segmentPicker ? "*" : "") + nscl + " #picks=" + numberofpicks
            + " " + Util.ascdatetime2(time)
            + " beg=" + Util.ascdatetime2(beginTime)
            + " end=" + Util.ascdatetime2(endtime) + " st=" + state + " idle=" + idle
            + " alive=" + isAlive() + (state == 3 ? " QR=" + waveformrings.get(0) : "");
  }

  @Override
  public StringBuilder getStatusString() {
    Util.clear(statussb);
    statussb.append(title).append("/").append(nscl).append(" #picks=").append(numberofpicks).append(" #gaps=").
            append(numberofgaps);
    return statussb;
  }

  @Override
  public StringBuilder getMonitorString() {
    return monitorsb;
  }

  @Override
  public StringBuilder getConsoleOutput() {
    return consolesb;
  }

  /**
   * This instantiates a FilterPicker in the normal EdgeThread manner
   *
   *
   * @param argline The command line for this filter p
   * @param tg A short tag for logging
   * @param pickSender A JSON sender object or null if one is not used
   */
  public CWBPicker(String argline, String tg, JsonDetectionSender pickSender) {
    super(argline, tg);
    argsin = argline;
    threadID = getName();
    threadID = threadID.substring(threadID.indexOf("-") + 1);
    //picker=null;
    //globalPickSender=pickSender;
    jsonPickSender = pickSender;
    if (argsin.contains(">")) {
      argsin = argsin.substring(0, argsin.indexOf(">")).trim();
    }
    time = System.currentTimeMillis() - 600000;    // If no state file, 10 minutes ago should be start
    gendtime.set(2099, 11, 31);
    endtime = gendtime.getTimeInMillis();

    String[] args = argline.split("\\s");
    for (int i = 0; i < args.length; i++) {
      if (args[i].contains(">")) {
        break;
      } else if (args[i].equals("-c")) {
        String[] parts = args[i + 1].replaceAll("-", " ").replaceAll("_", " ").split("[;:,]");
        for (String p : parts) {
          nscl.add(p);
          nsclsb.add(new StringBuilder(12).append(p));
        }
        i++;
      } else if (args[i].equals("-template")) {
        templateFile = args[i + 1];
        i++;
      } else if (args[i].equals("-1")) {
        segmentPicker = true;
      } else if (args[i].equalsIgnoreCase("-path")) {
        path = args[i + 1];
        i++;
      } else if (args[i].equalsIgnoreCase("-id")) {
        pickerID = Integer.parseInt(args[i + 1]);
        i++;
      } else if (args[i].equalsIgnoreCase("-noapply")) {
        noApply = true;
      }
      //else prta(i+"FP "+nscl+" ** Unknown argument to CWBPicker="+args[i]);
    }

    if (!segmentPicker) {    // if a continous picker, try to read state file
      // Try to read in the state file or create one
      try {

        if (nscl.size() == 3) {    // State files for 3 channels should have . in component
          stateFile = path + nscl.get(0).replaceAll(" ", "_").substring(0, 9) + "."
                  + nscl.get(0).substring(10).replaceAll(" ", "_") + "-" + Integer.toString(pickerID) + ".state";
        } else {
          stateFile = path + nscl.get(0).replaceAll(" ", "_").trim() +  "-" + Integer.toString(pickerID) + ".state";
        }
        Util.chkFilePath(stateFile);
        File state2 = new File(stateFile);
        if (state2.exists()) {
          initialize(stateFile);    // load int the state
        } else {
          try {
            time = System.currentTimeMillis() - getWarmupMS(); // this variable is static in child
            gtime.setTimeInMillis(time);
            Util.clear(statesb).
                    append("StartTime=").append(Util.ascdatetime2(time).toString().replaceAll(" ", "-")).append("\n");
            if(templateFile != null) {
              statesb.append("templatefile=").append(templateFile).append("\n");
            }
            Util.writeFileFromSB(stateFile, statesb);
          } catch (IOException e) {
            e.printStackTrace(getPrintStream());
          }
          createStateFile = true;
        }
      } catch (FileNotFoundException e) {
        prta(stateFile + " not found.  Initialize it.");
        createStateFile = true;
      } catch (IOException e) {
        prta(stateFile + " IOException e=" + e + " initialize it");
        createStateFile = true;
      }
    }
    // Load in base template.
    try {
      if (templateFile != null) {
        initialize(templateFile);         // set the template parameters
      }
    } catch (IOException e) {
      prta(tag + " IOerror reading template file e=" + e);
    }

    // allow command line to override the template
    for (int i = 0; i < args.length; i++) {
      switch (args[i]) {
        case "-blocksize":
          blocksize = Double.parseDouble(args[i + 1]);
          i++;
          break;
        case "-db":
          Util.setProperty("PickDBServer", args[i + 1]);
          i++;
          break;
        case "-auth":
          author = args[i + 1];
          i++;
          break;
        case "-agency":
          agency = args[i + 1];
          i++;
          break;
        case "-1":
          segmentPicker = true;
          break;
        case "-b":
          beginTime = Util.stringToDate2(args[i + 1]).getTime();
          gbeginTime.setTimeInMillis(beginTime);
          time = beginTime;
          i++;
          break;
        case "-e":
          endtime = Util.stringToDate2(args[i + 1]).getTime();
          gendtime.setTimeInMillis(endtime);
          i++;
          break;
        case "-t":
          title = args[i + 1];
          i++;
          break;
        case "-rt":
          rate = Double.parseDouble(args[i + 1]);
          i++;
          break;
        case "-h":
          cwbipaddress = args[i + 1];
          i++;
          break;
        case "-p":
          cwbport = Integer.parseInt(args[i + 1]);
          break;
        // this just abates a warning, no effect
        case "-empty":
          break;
        case "-dbg":
          dbg = true;
          break;
        case "-mf":
          makeFiles = true;
          maker = new MakeRawToMiniseed(this);
          break;
        case "-f":
          pickerTag = args[i + 1];
          i++;
          break;
        case "-nodb":
          Util.setProperty("DBServer", "NoDB");
          Util.setProperty("PickDBServer", "NoDB");
          break;
        case "-json":     // A individual thread kafka sender is very unusual
          jsonArgs = args[i + 1];
          if (jsonPickSender == null) {
            jsonPickSender = new JsonDetectionSender("-json " + args[i + 1], this);
          } else {
            prta("*** duplicate setup of JSONDetctionSender args=" + args[i + 1] + " json=" + jsonPickSender);
          }
          i++;
          break;
        case "-topic":
          overrideTopic = args[i+1].trim();
          break;
        default:
          break;
      }
    }
    //Util.clear(nsclsb).append(Util.rightPad(nscl,12));
    //
    // Initiate the QuerySpan for the NSCL and CWB.  Request a memory buffer
    // containing 600 sec (10 minutes) of raw data and shift the memory by
    // 580 sec when reaching the end of the memory ring.   This is a 
    // hardwired initiation
    //
    for (String chan : nscl) {
      prta("QueryRing new " + chan);
      QueryRing waveformring = new QueryRing(cwbipaddress, cwbport, chan, gtime,
              3000., 650., rate, this);  // remember 2 spaces to pad station name
      waveformring.setDebug(dbg);
      rate = waveformring.getRate();
      waveformrings.add(waveformring);
    }
    pickerManager = PickerManager.getPickerManager();     // If null, then no picker manager object is running
    if (jsonPickSender == null) {
      jsonPickSender = PickerManager.getJSONSender();
    }
    if (pickerManager != null) {
      if( pickerManager.getJsonTopicAddon() != null) {
        if(overrideTopic == null) {
          overrideTopic = pickerManager.getJsonTopicAddon();
        }
        else {
          overrideTopic += pickerManager.getJsonTopicAddon();
        }
      }
    }
    else {
      prta("CWBPick:  How can the pickerManager be null? ");
    }
    prta("CWBPick: create " + Util.ascdatetime2(time) + " beg=" + Util.ascdatetime2(beginTime)
            + " end=" + Util.ascdatetime2(endtime) + " auth/agt="+author+"/"+agency+ " temp="+templateFile 
            + " seg="+segmentPicker  + " "+ waveformrings.get(0) + " json=" + jsonPickSender
            + " pickerID=" + pickerID + " PM=" + pickerManager);
    running = true;
    start();
  }

  /**
   * this is to help the extender class to skip over parameters handled by this class. The return is
   * the value to add to the index interating over the args (normally variable i). If -1, then this
   * parameter should have been handled by the extender class.
   *
   * @param arg Normally args[i]
   * @return 0 if single switch (-dbg) , 1 if switch + arg, or -1 if this is not a switch to this
   * class.
   */
  public final int parentSwitch(String arg) {
    switch (arg.toLowerCase()) {
      // These are handled in the parent class
      // These are handled in the parent class
      case "-blocksize":
      case "-db":
      case "-auth":
      case "-agency":
      case "-b":
      case "-e":
      case "-t":
      case "-rt":
      case "-h":
      case "-f":
      case "-json":
      case "-c":
      case "-template":
      case "-path":
      case "-id":
      case "-topic":
        return 1;
      case "-1":
      case "-mf":
      case "-empty":
      case "-dbg":
      case "-nodb":
      case "-noapply":
        return 0;
      default:
        return -1;
    }
  }

  /**
   * This method writes to a file the input parameters and updates the start time of the processing.
   * This is used to re-initiate the processing in the case of an unexpected exit.
   *
   * @param statesb A String builder with picker dependent parameters in the form Key#value.
   */
  //@Override
  public final void writeStateFile(StringBuilder statesb) {
    if (segmentPicker) {
      return;      // No state files when run this way
    }
    long elapse = System.currentTimeMillis();
    //statesb.append("Title=").append(title.trim()).append("\n");
    statesb.append("NSCL=");
    for (String chan : nscl) {
      statesb.append(chan.replaceAll(" ", "_")).append(",");
    }
    statesb.append("\n");
    if (templateFile != null) {
      statesb.append("TemplateFile=").append(templateFile).append("\n");
    }
    statesb.append("Starttime=").append(Util.ascdatetime2(time).toString().replaceAll(" ", "-")).append("\n");
    statesb.append("Endtime=").append(Util.ascdatetime2(endtime).toString().replaceAll(" ", "-")).append("\n");

    try {
      Util.writeFileFromSB(stateFile, statesb);
    } catch (IOException e) {
      SendEvent.edgeSMEEvent("CWBPckFileEr", "Cannot write state=" + stateFile + " e=" + e, this);
    }
    prta("State file update " + stateFile + " len=" + statesb.length() + " time=" + Util.ascdatetime2(time)
            + " elapsed=" + (System.currentTimeMillis() - elapse) + " ms");
  }

  /**
   * This method reads a file that contains the input parameters for a CWBPicker
   *
   * @param filename File name with parms.
   * @throws java.io.FileNotFoundException
   */
  protected final void initialize(String filename) throws FileNotFoundException, IOException {
    //if(segmentPicker) return;     // cannot be inititalize
    if (filename == null) {
      return;
    }
    RandomAccessFile in2 = new RandomAccessFile(filename, "r");
    synchronized (statesb) {
      if (statebuf.length < in2.length()) {
        statebuf = new byte[(int) in2.length() * 2];
      }
      in2.read(statebuf, 0, (int) in2.length());
      Util.clear(statesb);
      for (int i = 0; i < in2.length(); i++) {
        statesb.append((char) statebuf[i]);
      }
      in2.close();
    }
    String line;
    String start = "";
    String stop = "";
    StringBuilder sb;
    try (BufferedReader in = new BufferedReader(new StringReader(statesb.toString()))) {
      Util.clear(pickerArgs);
      while ((line = in.readLine()) != null) {
        if (line.startsWith("!")) {
          continue;             // comment line
        }
        if (line.contains("#")) {
          pickerArgs.append(line).append("\n");    // key#value are for the specific picker
        }
        if (line.contains("!")) {
          line = line.substring(0, line.indexOf("!")).trim();  // remove comments
        }
        if (line.trim().length() == 0) {
          continue;        // nothing left!
        }
        if (line.contains("=")) {
          String[] parts = line.split("=");
          if (parts.length == 1) {
            continue;   // NOthing past the =
          }
          parts[1] = parts[1].trim();
          if (parts[0].equalsIgnoreCase("Title")) {
            title = parts[1];
          } else if (parts[0].equalsIgnoreCase("Starttime")) {
            start = parts[1];  // statefile
          } else if (parts[0].equalsIgnoreCase("Endtime")) {
            stop = parts[1];     // statefile
//          } else if (parts[0].equalsIgnoreCase("templatefile")) {
//            templateState = parts[1];//statefile
          } else if (parts[0].equalsIgnoreCase("NSCL")) {
            String[] chans = parts[1].split("[,:;]");
            nscl.clear();
            nsclsb.clear();
            for (String chan : chans) {
              chan = (chan.replaceAll("_", " ").replaceAll("-", " ") + "   ").substring(0, 12);
              nscl.add(chan);
              nsclsb.add(new StringBuilder(12).append(chan));
            }
          } else if (parts[0].equalsIgnoreCase("CWBipaddress")) {
            cwbipaddress = parts[1];
          } else if (parts[0].equalsIgnoreCase("CWBport")) {
            cwbport = Integer.parseInt(parts[1]);
          } else if (parts[0].equalsIgnoreCase("blocksize")) {
            blocksize = Double.parseDouble(parts[1]);
          } else if (parts[0].equalsIgnoreCase("agency")) {
            agency = parts[1];
          } else if (parts[0].equalsIgnoreCase("author")) {
            author = parts[1];
          } else if (parts[0].equalsIgnoreCase("rate")) {
            rate = Double.parseDouble(parts[1]);
          } else if (parts[0].equalsIgnoreCase("pickdbserver")) {
            Util.setProperty("PickDBServer", parts[1]);
          } else if (parts[0].equalsIgnoreCase("JSONArgs")) {
            if (jsonPickSender != null) {
              prta("*** Reading template has jsonPickSender, but one is already set up jsonArgs=" + parts[1] + " json=" + jsonPickSender);
            } else {
              jsonPickSender = new JsonDetectionSender("-json " + parts[1], this);
            }
          } else if (parts[0].equalsIgnoreCase("overridetopic")) {
            if(!parts[1].trim().equals("")) {
              overrideTopic = parts[1].trim();
            }
          } else if (parts[0].equalsIgnoreCase("makefiles")) {
            if (parts[1].equalsIgnoreCase("t")) {
              makeFiles = true;
              if (maker == null) {
                maker = new MakeRawToMiniseed(this);
              }
            }
          } else if (parts[0].equalsIgnoreCase("pickertag")) {
            pickerTag = parts[1];
          } else {
            prta("CWBPick: ***** unknown key value pair=" + line);
          }
        }
      }
    }
    if (!start.equals("")) {
      time = Util.stringToDate2(start).getTime();
      gtime.setTimeInMillis(time);

      if (stop.equals("")) {
        gendtime.setTimeInMillis(date2099.getTimeInMillis());
        endtime = gendtime.getTimeInMillis();
      } else {
        endtime = Util.stringToDate2(stop.replaceAll("_", "-")).getTime();
        gendtime.setTimeInMillis(endtime);
      }
      gbeginTime.setTimeInMillis(time);
      beginTime = time;
    }
    // See if there is a overriding topic extension in the PickerManager
    prta("initialize() : file read " + title + " " + nscl + " " + start + " to " + stop + " blk=" + blocksize
            +" topic=" + overrideTopic + " st=" + Util.ascdatetime2(time) + " end=" + Util.ascdatetime2(endtime));
  }

  /**
   * return true if this is a segmented picker and the last segment has been processed
   *
   * @return True if this segmented picker is currently idling.
   */
  public boolean segmentDone() {
    return segmentPicker && idle;
  }

  /**
   * give an new segment to be picked, assume the old span is no longer useful
   *
   * @param start start of new segment to pick
   * @param end end of new segment to pick
   */
  public void runSegment(long start, long end) {
    time = start;
    gbeginTime.setTimeInMillis(start);
    beginTime = start;
    gendtime.setTimeInMillis(end);
    endtime = end;
    clearMemory();
    //try {
    for (QueryRing q : waveformrings) {
      // If the current span does not overlap the start of this one, then clear out the span
      //if(q.getQuerySpan().getTimeInMillisAt(0) >= end ||
      //q.getQuerySpan().getTimeInMillisAt(q.getQuerySpan().getNsamp()) <= start) {
      q.getQuerySpan().resetSpan(time);
//          q.getDataAt(time, 1, temp, false);
      //}
    }
    /*} catch (IOException e) {
      prta(tag + " runSegment: IO error on ring e=" + e);

    }*/

    prta("  **** runSegment pick=" + toString() + " " + waveformrings.get(0).getQuerySpan());
    idle = false;   // kick off the segment
  }

  /**
   * Give a segment picker a new time span to run
   *
   * @param pickTime The time of the pick causing this segment from getWarmupMS() before this time
   * is run
   * @param duration The time in seconds to run after pick time
   * @param pickID Existing pick id, if this is a rerun, less than or equal to zero to create a new
   * pick ID
   * @param backazm If no back azimuth set to -1, in degrees 0-355
   * @param slowness if no slowness set to -1.
   * @param backazmErr Estimate of the back azimuth error in degrees
   * @param powerRatio THe power ratio
   */
  public void runSegment(GregorianCalendar pickTime, double duration, long pickID, int backazm,
          double slowness, double backazmErr, double powerRatio) {
    runSegment(pickTime.getTimeInMillis(), duration, pickID, backazm, slowness, backazmErr, powerRatio);
  }

  /**
   * Give a segment picker a new time span to run
   *
   * @param pickTime The time of the pick causing this segment from getWarmupMS() before this time
   * is run
   * @param duration The time in seconds to run after pick time
   * @param pickID Existing pick id, if this is a rerun, less than or equal to zero to create a new
   * pick ID
   * @param backazm If no back azimuth, set to -1 in degrees 0-355
   * @param slowness if no slowness set to -1.
   * @param backazmErr
   * @param powerRatio
   */
  public void runSegment(long pickTime, double duration, long pickID, int backazm, double slowness,
          double backazmErr, double powerRatio) {
    if (!segmentPicker) {
      prta(tag + " runSegment called on non-segment picker????");
      return;
    }

    // See if this can just extend a pick
    if (pickTime <= endtime) {
      if (pickTime + duration * 1000 >= endtime) { // yes it could just extend
        endtime = pickTime + (long) (duration * 1000. + 0.5);
        gendtime.setTimeInMillis(endtime);
        idle = false;
        return;
      }
    }
    // Set the times of the segment to run and clear the memory.
    clearMemory();
    this.pickID = pickID;
    this.backazm = backazm;
    this.slowness = slowness;
    this.backazmErr = backazmErr;
    this.powerRatio = powerRatio;
    if (backazm >= 0.) {
      prta(tag + " runSegment has ** beam " + getSeedname(0) + " bazm=" + backazm + " slow=" + slowness + " pwr=" + powerRatio);
    }
    time = pickTime - getWarmupMS();
    gtime.setTimeInMillis(time);
    time = gtime.getTimeInMillis();
    gbeginTime.setTimeInMillis(time);
    beginTime = time;
    //try {
    for (QueryRing q : waveformrings) {
      if(q.getQuerySpan() != null) {
        q.getQuerySpan().resetSpan(time);
      }
    }
    /*} catch (IOException e) {
      prta(tag + " runSegment: IO error on ring e=" + e);

    }*/
    endtime = pickTime + (long) (duration * 1000. + 0.5);
    gendtime.setTimeInMillis(endtime);

    prta(tag + " runSegment: " + Util.ascdatetime2(time) + "-" + Util.ascdatetime2(endtime) + " " + waveformrings.get(0));
    idle = false;   // This causes the picker to leave the idle loop.
  }

  /**
   * This method run the picker by initiating the QuerySpan class for accessing data, initiating the
   * PickerInterface class for picking the time-series, checking for data gaps, and waiting for new
   * data to arrive when appropriate
   */
  @Override
  public void run() {
    setPriority(priority);
    //StringBuilder e = new StringBuilder(1000);
    long lastStateWrite = System.currentTimeMillis();
    StringBuilder runsb = new StringBuilder(100);   // THis is used for logging in the run() 

    for (QueryRing waveformring : waveformrings) {
      prta("QueryRing created : " + waveformring);
    }
    while (!isReady()) {
      try {
        sleep(100);
      } catch (InterruptedException e) {
      }   // wait for extending object to be ready for data
    }
    //while(picker == null) try{sleep(250);} catch(InterruptedException e) {}
    // Wait to make sure the picker is made
    if (createStateFile) {
      try {
        writestate();
        //picker.writestate();
      } catch (IOException e) {
        prta("CWBPick: Cannot initialize " + stateFile + " e=" + e);
        SendEvent.edgeSMEEvent("CWBPckFileEr", "Cannot init statefile=" + stateFile + " e=" + e, this);
      }
    }
    if (!terminate) {      // abnormal terminate

      // Get the sample rate of the requested data stream
      // Computed the number of samples for the requested data stream
      // Initialize the data array to hold the requested data stream
      int npts = (int) (blocksize * waveformrings.get(0).getRate());
      int[][] rawdata = new int[waveformrings.size()][npts];
      int[] nret = new int[waveformrings.size()];

      // Compute the block size in milliseconds.  Used to advance time the
      // appropriate amount
      int blocksizeinMillis = (int) (npts / rate * 1000);
      int waittime = 5000;          // how long to wait for new data (in milliseconds)
      int realtimewindow = 300000;  // time window defined as being real-time (in milliseconds)
      int incrementtime = 5000;     // how much to advance time searching for new data
      long lastDbg = 0;
      // once outside of the realtime window
      int ngap = 0;
      boolean inGap = false;
      long lastRunStat = time - 3600000;
      if (time >= endtime) {
        prta(Util.clear(runsb).append(Util.ascdatetime2(time)).append("-").append(Util.ascdatetime2(endtime)).
                append("Starting Idle...."));
        // This means we are not a continuous picker.  we need to idle until a new time segment is sent to us
        idle = true;
        while (idle) {
          try {
            sleep(1000);
          } catch (InterruptedException e) {
          }
        }
        prta(Util.clear(runsb).append(tag).append(" Idle completed.  Start up run").append(Util.ascdatetime2(time)).append("-").
                append(Util.ascdatetime2(endtime)));
      }
      int tries = 0;
      while (!terminate) {
        state = 1;
        long now = System.currentTimeMillis();
        try {
          if (now - lastStateWrite > 300000 && !segmentPicker) {
            state = 2;
            writestate();               // force extender to write state
            lastStateWrite = now;
          }
          if (Util.isShuttingDown()) {
            terminate();

            break;
          }  // detect shudown of QueryMom
          boolean allOK = true;
          state = 3;
          for (int i = 0; i < waveformrings.size(); i++) {
            nret[i] = waveformrings.get(i).getDataAt(gtime, npts, rawdata[i], false);// try to get the required data
            state = 4;
            if (nret[i] != npts) {
              allOK = false;
              break;
            }
          }
          tries++;
          //if (Util.stringBuilderEqual(nscl.get(0), "TXALPN HH100")) {
          if (tries % 60 == 59 || now - lastDbg > 60000 || time - lastRunStat > 3600000) {
            state = 5;
            prta(Util.clear(runsb).append(tag).append(" allOK=").append(allOK).
                    append(" try=").append(tries).append(" ").
                    append(nret[0]).append(" npts=").append(npts).
                    append(" time=").append(Util.ascdatetime2(time)).
                    append(" ingap=").append(inGap).append(" #ch=").append(waveformrings.size()));
            lastDbg = now;
            lastRunStat = time;
          }
          //}

          // If a complete block of data is not returned, one has to make one
          // of 2 decisions.  1) Wait for more data and re-request, 2) advance
          // time looking for complete data.   It maybe the case that a gap
          // is not going to be filled in any reasonable amount of time.
          //
          // The strategy that we have adopted is that if we are within 
          // 5 minutes of real-time (300 sec or 300000 millisecs).  Sleep for
          // 5 seconds (5000 millisecs) and re-request the data.
          // If you fall outside of 5 minutes (300 s or 300000 millisec),
          // advance time by 5 sec (5000 millisecs) trying to skip over a gap
          // of data.
          if (!allOK) {
            state = 6;
            // Check to see if the data request is within 5 minutes (300000 millisec)
            // of real-time.  If so, see for 5 secs before re-requesting data
            if ((System.currentTimeMillis() - time) < realtimewindow) {
              try {
                state = 7;
                sleep(waittime);
                state = 8;
              } catch (InterruptedException e) {
              }
            } else {
              state = 9;
              if (!inGap) {
                numberofgaps++;
                inGap = true;
              }
              ngap++;
              // If outside of 5 minutes of real-time, advance time by
              // 5 sec and re-request data trying to span a gap of data
              // The picker memory needs to be cleared because new statistics
              // have to be computed
              long before = time;
              time = time + incrementtime;
              gtime.setTimeInMillis(time);
              if (ngap % 60 == 1) {
                prta(Util.clear(runsb).append(tag).append("Advancing Time thru gap:").
                        append(nscl.get(0)).append(" ").
                        append(Util.ascdatetime2(time)).append(" ").append(incrementtime / 1000).
                        append(" sec diff=").append(time - before).
                        append(" ngap=").append(ngap).
                        append(" end=").append(Util.ascdatetime2(endtime)));
              }
              clearMemory();
              state = 10;
              // Since we are not in real time, we can try to find the next time there is data in each buffer
              // by asking for the next time there might be data (or the end if the buffer)
              if (System.currentTimeMillis() - time > 86400000) {  // only do this until the last day is up
                time = time - incrementtime + (long) (npts / rate * 1000);
                gtime.setTimeInMillis(time);
                long nextDataTime = time;
                for (int i = 0; i < waveformrings.size(); i++) {
                  nextDataTime = Math.max(nextDataTime, waveformrings.get(i).getTimeOfNextDataAfter(gtime, gendtime));
                  time = nextDataTime;
                  gtime.setTimeInMillis(nextDataTime);
                }
                prta(Util.clear(runsb).append("Advancing through gap accellerated time=").
                        append(Util.ascdatetime2(time)).append(" ").append(Util.ascdatetime2(nextDataTime)).
                        append(" diff=").append(nextDataTime - time).
                        append(" end=").append(Util.ascdatetime2(endtime)));
                time = nextDataTime;
                gtime.setTimeInMillis(nextDataTime);
              }
            }
            state = 11;
          } else {
            state = 12;
            if (ngap > 0) {
              prta(Util.clear(runsb).append("Gap ending :").append(nscl).
                      append(" ").append(Util.asctime2(time)).append(" ngap=").append(ngap));
            }
            ngap = 0;
            inGap = false;

            // If the right amount of data is found, process it
            if (dbg) {
              state = 13;
              for (int i = 0; i < waveformrings.size(); i++) {
                int max = Integer.MIN_VALUE;
                int min = Integer.MAX_VALUE;
                double avg = (double) 0.;
                for (int jj = 0; jj < npts; jj++) {
                  avg += rawdata[i][jj];
                  if (rawdata[i][jj] > max) {
                    max = rawdata[i][jj];
                  }
                  if (rawdata[i][jj] < min) {
                    min = rawdata[i][jj];
                  }
                }
                avg = avg / npts;
                prta(Util.clear(runsb).append("Processing ").append(nscl.get(i)).append(" ").
                        append(Util.ascdatetime2(time)).append(" ").append(Util.df21(min)).
                        append(" ").append(Util.df21(max)).append(" avg=").append(Util.df21(avg)).
                        append(time < beginTime ? " *" : ""));
                if (min < -20000000 || max > 20000000) {
                  prta("Suspicous " + Util.df21(min) + " " + Util.df21(max));
                }
              }
            }

            inbytes += npts * 4;
            //picker.apply(time, rate, rawdata, npts);
            state = 14;
            if(!noApply) {
              apply(gtime, rate, rawdata, npts);
            }
            state = 15;
            // Compute time of the beginning of the next block of data
            time = time + blocksizeinMillis;
            gtime.setTimeInMillis(time);
            tries = 0;
          }
          state = 16;

          // have we reached the end of the time for picking
          if (time + blocksizeinMillis >= endtime) {
            state = 17;
            if (!segmentPicker) {
              prta(Util.clear(runsb).append(Util.ascdatetime2(time)).append("-").append(Util.ascdatetime2(endtime)).
                      append(" totpnts=").append(inbytes).append(" completed.  Go Idle...."));
              break;     // time to exit this thread
            }
            prta(Util.clear(runsb).append(Util.ascdatetime2(time)).append("-").append(Util.ascdatetime2(endtime)).
                    append(" totpnts=").append(inbytes).append(" completed.  Go Idle...."));
            // This means we are not a continuous picker.  we need to idle until a new time segment is sent to us
            idle = true;
            state = 18;
            while (idle) {
              try {
                sleep(1000);
              } catch (InterruptedException e) {
              }
            }
            state = 19;
            prta(Util.clear(runsb).append(tag).append(" Idle completed.  Loop back for new run ").
                    append(Util.ascdatetime2(time)).append("-").append(Util.ascdatetime2(endtime)));
          } //DONE, endtime reached
          state = 20;
        } catch (IOException e) {
          prta("CWBPick: IOError e=" + e);
          e.printStackTrace(getPrintStream());
        } catch (RuntimeException e) {
          prta("CWBPick: Runtime " + nscl + " e=" + e + " picker=" + toString());
          e.printStackTrace(getPrintStream());
          terminate();
          break;
        }
      }
    }     // While !Terminate
    state = 21;
    running = false;
    String filename = "";
    try {
      //picker.writestate();
      prta("CWBPick: * exiting main loop!");
      writestate();
      if (makeFiles) {
        maker.flush();
        TLongObjectIterator<ArrayList<MiniSeed>> itr = maker.getIterator();
        while (itr.hasNext()) {
          itr.advance();
          ArrayList<MiniSeed> chan = itr.value();
          if (chan.size() > 0) {
            filename = "DATA" + Util.FS + chan.get(0).getSeedNameString() + "_"
                    + Util.ascdatetime(chan.get(0).getTimeInMillis()).toString().replaceAll(":", "").replaceAll("/", "") + ".ms";
            filename = filename.replaceAll(" ", "_");
            maker.writeToDisk(chan.get(0).getSeedNameString(), filename);
          }
        }
      }
      //picker.terminate();     // give the picker a chance to close
      terminate();
      state = 22;
    } catch (FileNotFoundException e) {
      Util.chkFilePath("DATA" + Util.FS);
      prta("CWBPick: ** writestate() try chkFilePath on " + filename);
    } catch (IOException e) {
      prta("CWBPick: ** writestate() exiting IOError=" + e);
    }
    prta("CWBPick: * exiting terminate=" + terminate + " isShuttingDown()=" + Util.isShuttingDown());
  }

  /**
   * Create a new pick, get an ID and put int the DB with PickManager, and send info via JSON if a
   * sender is present. This call is when there is no beam and associated parameters.
   *
   * @param pickerType Something to identify this picker type like "FP" for FilterPicker
   * @param picktime The time of the pick in millis
   * @param amplitude The amplitude of the pick
   * @param period The period of the pick
   * @param phase The phase of the pick
   * @param error_window The error_window of the pick in seconds
   * @param polarity The polarity ("up" or "down" or null)
   * @param onset "impulsive","emergent","questionable" or null
   * @param hipass in Hz
   * @param lowpass in Hz
   * @param snr Estimate for this pick
   */
  public final void pick(String pickerType, GregorianCalendar picktime,
          double amplitude, double period,
          String phase, double error_window,
          String polarity, String onset,
          double hipass, double lowpass, double snr
  ) {
    pick(pickerType, picktime, amplitude, period, phase, error_window, polarity,
            onset, hipass, lowpass, -1, 0, 0., 0., snr, // No beam
            0., 0., 0., 0.);  // no associated
  }

  /**
   * Create a new pick, get an ID and put in the DB with PickManager, and send info via JSON if a
   * sender is present. This call is when there is a beam but now associated parameters.
   *
   * @param pickerType Something to identify this picker type like "FP" for FilterPicker
   * @param picktime The time of the pick in millis
   * @param amplitude The amplitude of the pick
   * @param period The period of the pick
   * @param phase The phase of the pick
   * @param error_window The error_window of the pick in seconds
   * @param polarity The polarity ("up" or "down" or null)
   * @param onset "impulsive","emergent","questionable" or null
   * @param hipass in Hz
   * @param lowpass in Hz
   * @param backasm In degrees 0-355 or -1 if no back azimuth
   * @param backazmerr in degrees
   * @param slow The slowness or if none negative value
   * @param powerRatio If beam, the power ratio
   * @param snr Estimate for this pick
   */
  public final void pick(String pickerType, GregorianCalendar picktime,
          double amplitude, double period,
          String phase, double error_window,
          String polarity, String onset,
          double hipass, double lowpass, int backasm, int backazmerr, double slow, double powerRatio, double snr
  ) {
    pick(pickerType, picktime, amplitude, period, phase, error_window, polarity,
            onset, hipass, lowpass, backasm, backazmerr, slow, powerRatio, snr,
            -1., 0., 0., 0.);     // No associated
  }

  /**
   * Create a new pick, get an ID and put int the DB with PickManager, and send info via JSON if a
   * sender is present.
   *
   * @param pickerType Something to identify this picker type like "FP" for FilterPicker
   * @param picktime The time of the pick in millis
   * @param amplitude The amplitude of the pick
   * @param period The period of the pick
   * @param phase The phase of the pick
   * @param error_window The error_window of the pick in seconds
   * @param polarity The polarity ("up" or "down" or null)
   * @param onset "impulsive","emergent","questionable" or null
   * @param hipass in Hz
   * @param lowpass in Hz
   * @param backasm In degrees 0-355 negative -1 if no back azimuth
   * @param backazmerr in degrees
   * @param slow The slowness or if none negative value
   * @param powerRatio If beam, the power ratio
   * @param snr Estimate for this picke
   * @param distance associated distance in degrees
   * @param azimuth associated azimuth in degrees
   * @param residual associated residual in secs
   * @param sigma associated error estimate
   */
  public final void pick(String pickerType, GregorianCalendar picktime,
          double amplitude, double period,
          String phase, double error_window,
          String polarity, String onset,
          double hipass, double lowpass, int backasm, int backazmerr, double slow, double powerRatio, double snr,
          double distance, double azimuth, double residual, double sigma) {
    long id = pickID;
    if (backasm < 0 && backazm >= 0) {
      backasm = backazm;    // If this set somewhere else, pass it along
    }
    if (slow <= 0. && slowness >= 0.) {
      slow = slowness;         // ditto
    }
    if (powerRatio <= 0.) {
      powerRatio = this.powerRatio;
    }
    if (backazmerr <= 0.) {
      backazmerr = (int) this.backazmErr;
    }
    if (pickID <= 0) {
      id = PickerManager.pickToDB(agency, author, picktime.getTimeInMillis(),
              nscl.get(0), amplitude, period, phase,
              0, 2, error_window,
              polarity,
              onset, hipass, lowpass, backasm, backazmerr, slow, snr, powerRatio, pickerID,
              distance, azimuth, residual, sigma, this);
    }
    if (id <= 0) {
      id = PickerManager.getNextSeq();
    }
    if (jsonPickSender != null) {
      java.util.Date pickDate = new java.util.Date();
      pickDate.setTime(picktime.getTimeInMillis());
      prta("CWBPicker pick with " + nscl.get(0) + " " + Util.ascdatetime2(picktime) 
              + " id=" + id
              + (backazm >= 0 ? " ** beam  bazm=" + backasm + " berr=" + backazmerr
                      + " slow=" + Util.df25(slow) + " power=" + Util.df21(powerRatio) : "")
              + " per=" + Util.df23(period)  +" snr="+Util.df22(snr) );
      jsonPickSender.sendPick(Util.getSystemNumber() + "_" + id, overrideTopic, 
              agency, author, picktime, nscl.get(0),
              amplitude, period, phase, error_window, polarity, onset, pickerType, hipass, lowpass, snr,
              backasm, backazmerr, slow, 0., powerRatio, 0., // power ratio error
              -1., -1., -1., -1.);    // Association parameters dist, azimuth, residula, sigma

    }
    if (pickerManager != null) {
      pickerManager.addPick(id, agency, author, nscl.get(0),
              pickerType, picktime, amplitude, period, phase, error_window, polarity, onset, hipass,
              lowpass, backasm, slow, snr);
    }
  }

  /**
   * Create a new pick, get an ID and put int the DB with PickManager, and send info via JSON if a
   * sender is present.
   *
   * @param pickerType Something to identify this picker type like "FP" for FilterPicker
   * @param picktime The time of the pick in millis
   * @param amplitude The amplitude of the pick - this case the correlation value
   * @param period The period of the pick
   * @param phase The phase of the pick
   * @param error_window The error_window of the pick in seconds
   * @param latitude The latitude of the reference event
   * @param longitude The longitude of the reference event
   * @param depth The depth of the reference event;
   * @param originTime The origin time of this event using the offset from the reference one
   * @param magnitude The magnitude estimate from this pick
   * @param zScore The zScore if one is computed -1 if not
   * @param threshold The threshold of the picker in correlation
   * @param thresholdType Normally "constant" since other methods are not use
   * @param snr Estimate for this picker
   * @param distance associated distance in degrees
   * @param azimuth associated azimuth in degrees
   *
   * @param eventType either "blast", or "earthquake" or null
   */
  public final void pickCorrelation(String pickerType, GregorianCalendar picktime,
          double amplitude, double period,
          String phase, double error_window,
          double latitude, double longitude, double depth, long originTime,
          double magnitude,
          double snr, double distance, double azimuth, String eventType,
          double zScore, double threshold, String thresholdType
  ) {
    long id = pickID;
    if (pickID <= 0) {      // If so, use the Pickermanager to get a pick ID from the DB
      id = PickerManager.pickToDB(agency, author, picktime.getTimeInMillis(),
              nscl.get(0), amplitude, period, phase,
              0, 2, error_window,
              null, // polarity
              null, // onset
              0., 0., // hipass/lowpass
              -1, -1, // backazm and backazm error
              -1., // slowness
              snr, -1., // snr and power ration
              pickerID,
              distance, azimuth, // 
              -999., -1., // residual and error estimate of residual
              this);
    }
    prta("CWBPicker pick with ** correlation " + nscl.get(0) + " " + Util.getNode() + " " + Util.getSystemNumber()
            + " " + agency + " " + author + " " + Util.ascdatetime2(picktime) + " amp=" + amplitude + " "
            + phase + " coord=" + latitude + " " + longitude + " " + depth);

    if (id <= 0) {
      id = PickerManager.getNextSeq();
    }
    if (jsonPickSender != null) {
      java.util.Date pickDate = new java.util.Date();
      pickDate.setTime(picktime.getTimeInMillis());
      //if(backasm >= 0.) 
      prta("CWBPicker pick with ** correlation " + nscl.get(0) + " " + Util.getNode() + " " + Util.getSystemNumber());
      jsonPickSender.sendCorrelation(Util.getSystemNumber() + "_" + id, overrideTopic, 
              agency, author, picktime.getTimeInMillis(),
              -1., // time error
              nscl.get(0),
              1, // version
              phase, // phase
              amplitude, // correlation level 0-1.
              latitude, longitude, originTime, depth,
              -1., -1., -1.,// lat err, longitude error, depth error
              eventType, // String event type (blast, earthquake or null)
              magnitude, // magnitude, if not computed set to null
              snr, // snr if present
              zScore, // zScore
              threshold, // threshhold
              thresholdType);
    }
    if (pickerManager != null) {
      pickerManager.addPick(id, agency, author, nscl.get(0),
              pickerType, picktime, amplitude, period, phase, error_window,
              null, null, -1., -1., -1, -1., snr);
    }
  }

}
