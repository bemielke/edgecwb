/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package subspacedetector;

import com.oregondsp.io.SACInputStream;
import detection.usgs.SubspaceDetectorCollection;
import gov.usgs.anss.edge.EdgeProperties;
import gov.usgs.anss.util.Util;
import gov.usgs.anss.edgethread.EdgeThread;
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.GregorianCalendar;

/**
 * This class contains the configuration as read from a SubspaceDetector configuration (output of
 * the SubspacePreprocessor. It uses the illname StationCollection (its not a collection) to hold
 * station information - It is not clear this usage is at all necessary. The FilterCollection is
 * also misnamed in that it is not a collection, but it it is a good class as it can be used to
 * filter segments of data keeping the history separate.
 * <p>
 * DCK - converted all of the getters to use the 'get' or 'is" rather than exposing methods with
 * capital letters. Converted the cascade of "if" on various key-value pairs to a case statement and
 * did the split of the line only once at the top and each key-value analysis to use that one.
 *
 * @author ketchum
 * @author benz
 * @author wyeck
 * @author jnealy
 */
public final class Config {

  private SubspaceDetectorCollection detectors;   //Contains the CC results for the set of templates
  private SubspaceDetectorCollection rdetectors;   //Contains the CC results for the set of templates

  private StationCollection stationinfo;

  private float referencelatitude;
  private float referencelongitude;
  private float referencedepth;
  private GregorianCalendar referenceorigintime;
  private GregorianCalendar referencearrivaltime;
  private String referencemagtype;
  //private String referencephase;
  //private String referencephasecomponent;
  private float referencemagnitude;
  private String referenceeventid;
  private final float[] maxComponentValues = new float[3];

  private float hpcorner = 1;
  private float lpcorner = 3;
  private int npoles = 3;
  private String filtertype = "bandpass";
  private float SNRcut = 1.5f;
  private float preEvent;

  private boolean ccresults = false;

  private boolean jsonOut = false;
  private String jsonPath = "";
  private String brokerConfig = "";
  private String agency = "NEIC";
  private String author = "";
  private String eventType = "earthquake";

  private int waittimeinMillis = 120000;         // The wait time (in milliseconds) to check for filled gaps in the CWB
  private int stepintervalinMillis = 10000;      // The interval in time (in milliseconds) to step thru a gap in the CWB
  private int realtimeintervalinMillis = 600000; // The interval of time (in milliseconds) preceeding real-time you are willing to wait to fill gaps

  private int templateLength;                    // Length in number of samples of ALL of the templates using in the processing
  public float templateduration = 10;           // Length (in sec) of the default template
  public float averagingduration = 5;            // Duration over which a peak is defined.  Takes highest peak in the duration
  public float dataLengthinsec = 600;               // Length (in sec) of block of data to process

  private float noisewindowlengthinsec = 1800;
  private float detectionthresholdscalefactor = 9;
  private String detectionthresholdtype = "constant";       // "constant": constant threshold, "mad": median absolute deviation, "ncc": noise cc, "all": do all for comparison

  private int dataLength;                     // Length in number of samples of data to be requested from the CWB
  private int averagingLength;                   // Length in number of samples of the window used to find peak amplitude and detection peak

  private int numberofDetectors;
  private int numberofChannels;

  //whether to append files;
  private boolean append = false;

  //save when data grabbed
  private boolean grabdates = true;

  //JN edit: (9-25-15) changed to use local host
  //private String inputcwbip = "137.227.224.97";
  private String inputcwbip = "localhost";
  private int inputcwbport = 2061;

  //JN edit (2015-10-09): added historic number as an input option
  private float historicnumber_hrs = 0.5f;

  private float detectionthreshold = 0.65f;       // Detecton threshold below which you do not look for a detection 
  private final float ztest = 4f; // for the z test
  private final float updateSeconds = 900; // when to update the sigma
  private final float statisticsWindow = 900; //how long of  a window to use

  private String starttime;                      // Start time of processing (format: 2012/02/14 12:00)
  private String stoptime;                       // End time of processing (format: 2014/01/01 00:00

  private String outputpath = "./";
  private boolean email = false;
  private String[] email_address;

  private boolean edgeMode;

  private final EdgeThread par;

  public String toString() {
    return stationinfo + " " + starttime + "-" + stoptime + " to " + outputpath;
  }

  public void setEdgeMode(boolean f) {
    edgeMode = f;
  }

  public float getNoiseWindowLengthinSec() {
    return noisewindowlengthinsec;
  }

  public float getDetectionThresholdScaleFactor() {
    return detectionthresholdscalefactor;
  }

  public String getDetectionThresholdType() {
    return detectionthresholdtype;
  }

  public float getPreEvent() {
    return preEvent;
  }

  public String getReferenceEventID() {
    return referenceeventid;
  }

  public float getReferenceLatitude() {
    return referencelatitude;
  }

  public float getReferenceLongitude() {
    return referencelongitude;
  }

  public float getReferenceDepth() {
    return referencedepth;
  }

  public float getReferenceMagnitude() {
    return referencemagnitude;
  }

  public GregorianCalendar getReferenceOriginTime() {
    return referenceorigintime;
  }

  public GregorianCalendar getReferenceArrivalTime() {
    return referencearrivaltime;
  }

  public String getReferenceMagType() {
    return referencemagtype;
  }
  //JN edit (2015-10-09):

  public float getHistoricNumberHours() {
    return historicnumber_hrs;
  }

  public int getNumberofDetectors() {
    return numberofDetectors;
  }

  public int getNumberofChannels() {
    return numberofChannels;
  }

  public int getTemplateLength() {
    return templateLength;
  }

  public int getAveragingLength() {
    return averagingLength;
  }

  public int getDataLength() {
    return dataLength;
  }

  public float[] getMaxes() {
    return maxComponentValues;
  }

  public void setMaxes(int i, float d) {
    maxComponentValues[i] = d;
  }

  public String getStartTime() {
    return starttime;
  }

  public String getStopTime() {
    return stoptime;
  }

  public String getOutputPath() {
    return outputpath;
  }

  public String getJsonPath() {
    return jsonPath;
  }

  public String getBrokerConfig() {
    return brokerConfig;
  }

  public boolean getJsonOutput() {
    return jsonOut;
  }

  public void setJsonPath(String jp) {
    jsonPath = jp;
  }

  public void setEventType(String s) {
    eventType = s;
    if (!eventType.equals("earthquake") && !eventType.equals("blast")) {
      prta(" *** broker switch  does not set Event type to earthquake or blast - set it to earthquake");
      eventType = "earthquake";
    }
  }

  public void setJsonOutput(boolean j) {
    jsonOut = j;
  }

  public String getAgency() {
    return agency;
  }

  public String getAuthor() {
    return author;
  }

  public String getEventType() {
    return eventType;
  }

  public float getDetectionThreshold() {
    return detectionthreshold;
  }

  public void setDetectionThreshold(float t) {
    detectionthreshold = t;
  }

  public String getCWBIP() {
    return inputcwbip;
  }

  public int getCWBport() {
    return inputcwbport;
  }

  public int getWaitTimeinMillis() {
    return waittimeinMillis;
  }

  public int getStepIntervalinMillis() {
    return stepintervalinMillis;
  }

  public int getRealTimeIntervalinMillis() {
    return realtimeintervalinMillis;
  }

  public float getHPCorner() {
    return hpcorner;
  }

  public float getLPCorner() {
    return lpcorner;
  }

  public int getNpoles() {
    return npoles;
  }

  public float getSNRCutoff() {
    return SNRcut;
  }

  public String getFilterType() {
    return filtertype;
  }

  public boolean getCCresults() {
    return ccresults;
  }

  public SubspaceDetectorCollection getDetectors() {
    return detectors;
  }

  public SubspaceDetectorCollection getRDetectors() {
    return rdetectors;
  }
  //public SubspaceTemplatesCollectionV2 Templates() { return templates; }

  public StationCollection getStatInfo() {
    return stationinfo;
  }

  public boolean append() {
    return append;
  }

  public boolean grabdates() {
    return grabdates;
  }

  public boolean isEmail() {
    return email;
  }

  public void setEmail(boolean e) {
    email = e;
  }

  public void setEmailAddresses(String[] emails) {
    email_address = emails;
  }

  public String[] getEmailAddresses() {
    return email_address;
  }

  public void setNoiseWindowLengthinSec(int s) {
    noisewindowlengthinsec = s;
  }

  public void setStartEndTimes(String start, String end) {
    starttime = start;
    stoptime = end;
  }

  public void prt(String s) {
    if (par == null) {
      Util.prt(s);
    } else {
      par.prt(s);
    }
  }

  public void prta(String s) {
    if (par == null) {
      Util.prta(s);
    } else {
      par.prta(s);
    }
  }

  /**
   * Construct a configuration object from a file - this seems to be the only constructor actually
   * used.
   *
   *
   * @param inputfile The config file to run
   * @param parent For logging.
   * @throws FileNotFoundException
   * @throws IOException
   * @throws InterruptedException
   */
  public Config(String inputfile, EdgeThread parent) throws FileNotFoundException, IOException, InterruptedException {
    par = parent;
    readConfigFile(inputfile);
  }

  /**
   * This constructor does not appear to be used, but if it was you would have to call
   * readConfigFile(filename) to use it. In which case using the Config(inputfile, parent)
   * constructor makes more sense
   *
   * @param parent A parent EdgeThread for logging, or null to log via the standard logger.
   */
  public Config(EdgeThread parent) {
    par = parent;
  }

  public Config(StationCollection sc, float inhpcorner, float inlpcorner, int inpoles, String ioutpath, String istart,
          String istop, int iwaittime, int istep, int ireal, String ip, int port, float detectionthresh, float noisewlength,
          float dthreshscale, String dthreshtype, float datalengthsec, float tempduration, float averagedur, float pretime,
          boolean ccresult, GregorianCalendar refot, GregorianCalendar refat, float reflat, float reflon, float refdepth,
          float refmag, String refmagtype, ArrayList<String> tnames, boolean append1, boolean gdates, EdgeThread parent)
          throws FileNotFoundException, IOException, InterruptedException {
    par = parent;
    filtertype = "bandpass";
    hpcorner = inhpcorner;
    lpcorner = inlpcorner;
    npoles = inpoles;
    stationinfo = sc;
    outputpath = ioutpath;
    starttime = istart;
    stoptime = istop;
    waittimeinMillis = 1000 * iwaittime;
    stepintervalinMillis = 1000 * istep;
    realtimeintervalinMillis = 1000 * ireal;
    inputcwbip = ip;
    inputcwbport = port;
    detectionthreshold = detectionthresh;
    noisewindowlengthinsec = noisewlength;
    if (noisewindowlengthinsec > 21600) {
      noisewindowlengthinsec = 21600;
    }
    detectionthresholdscalefactor = dthreshscale;
    detectionthresholdtype = dthreshtype;
    dataLengthinsec = datalengthsec;
    templateduration = tempduration;
    averagingduration = averagedur;
    preEvent = pretime;
    ccresults = ccresult;
    referenceorigintime = refot;
    referencearrivaltime = refat;
    referencelatitude = reflat;
    referencelongitude = reflon;
    referencedepth = refdepth;
    referencemagnitude = refmag;
    referencemagtype = refmagtype;
    append = append1;
    grabdates = gdates;
    ArrayList<String> templatenames = tnames;

    // Determine number of templates to use
    int npts = 0;
    boolean first = true;
    int nch = 0;
    int ntemplates = 0;
    for (int j = 0; j < templatenames.size(); j++) {
      String[] wf = templatenames.get(j).split("\\s+");
      int nn = 0;
      for (String wf1 : wf) {
        if (!"#".equals(wf1.substring(0, 1))) {
          SACInputStream ss = new SACInputStream(wf1.trim());
          if (first == true) {
            npts = ss.header.npts;
          }
          if (ss.header.npts < npts) {
            npts = ss.header.npts;
          }
          ss.close();
          nn++;
          //int sps = (int) (1.0/ss.header.delta);
          int sps = (int) Math.round(1.0 / ss.header.delta);
          stationinfo.setRate(sps);
        }
      }
      if (nn != 0) {
        nch = nn;
      }
      if (nn != 0) {
        ntemplates++;
      }
    }

    // segmentLength: block length in samples of the contiguous chunk of data to be correlated
    // templateLength: block length in samples of the templates used in the correlation
    // I haven't tested it, but I am pretty sure segmentLength needs to be bigger than templateLength
    dataLength = (int) (dataLengthinsec * stationinfo.getRate());
    templateLength = (int) (templateduration * stationinfo.getRate());
    averagingLength = (int) (averagingduration * stationinfo.getRate());

    templateLength = npts;
    numberofDetectors = ntemplates;
    numberofChannels = nch;
    // zzz contains the templates organized the way Dave Harris needs them
    float[][][] zzz = new float[numberofDetectors][numberofChannels][templateLength];
    float[][][] rrr = null;
    //JN edit (2015-10-08): changed empirical to mad, noisecc, and all
    //May need to change this setup slightly to accomidate mad not using all templates
    if (detectionthresholdtype.equals("mad") || detectionthresholdtype.equals("all")) {
      //rrr = new float[1][numberofChannels][templateLength];
      rrr = new float[numberofDetectors][numberofChannels][templateLength];
    }

    for (int idetector = 0; idetector < templatenames.size(); idetector++) {

      float[] tt = new float[templateLength * numberofChannels];
      String[] wf = templatenames.get(idetector).split("\\s+");
      int ich = 0;
      for (String wf1 : wf) {
        float[] t = new float[templateLength];
        if (!"#".equals(wf1.substring(0, 1))) {
          try (SACInputStream ss = new SACInputStream(wf1.trim())) {
            ss.readData(t);
            System.arraycopy(t, 0, tt, templateLength * ich, templateLength);
          }
          ich++;
        }
      }

      normalizeTemplates(tt);
      for (int iwaveform = 0; iwaveform < numberofChannels; iwaveform++) {
        System.arraycopy(tt, iwaveform * templateLength, zzz[idetector][iwaveform], 0, templateLength);
      }

      // You only need to use one template for the estimate of noise.  Assume the 1st one in the list
      // is the highest rank template
      //JN edit 2015: set up noise detectors using all idetectors and changed empirical to mad, noisecc, and all
      //May need to change this setup slightly to accomidate mad not using all templates
      //if( detectionthresholdtype.equals("empirical") && idetector == 0) {
      if (detectionthresholdtype.equals("mad") || detectionthresholdtype.equals("all")) {
        float[] rr = new float[templateLength * numberofChannels];
        for (int iwaveform = 0; iwaveform < numberofChannels; iwaveform++) {
          float[] r = new float[templateLength];
          System.arraycopy(tt, iwaveform * templateLength, r, 0, templateLength);
          EmpiricalNoiseTemplates.computeRandomTimeSeries(r,
                  hpcorner, lpcorner, npoles, filtertype, stationinfo.getRate());
          System.arraycopy(r, 0, rr, templateLength * iwaveform, templateLength);
        }
        normalizeTemplates(rr);
        for (int iwaveform = 0; iwaveform < numberofChannels; iwaveform++) {
          System.arraycopy(rr, iwaveform * templateLength, rrr[idetector][iwaveform], 0, templateLength);
        }
      }
    }

    if (detectors == null) {
      detectors = new SubspaceDetectorCollection(numberofChannels, dataLength);
    }
    detectors.addTemplate(zzz, "test");

    //JN edit (2015-10-08): changed empirical to mad and all
    if (detectionthresholdtype.equals("mad") || detectionthresholdtype.equals("all")) {
      if (rdetectors == null) {
        rdetectors = new SubspaceDetectorCollection(numberofChannels, dataLength);
      }
      rdetectors.addTemplate(rrr, "test");
    }

  }

  /**
   * This is over-the-top bad coding and too complicated. It tries to anticipate all the variations
   * on input and configurating. I will document as best I can.
   *
   * It reads the input file and sets all of the configuration properties.
   *
   * DCK - change to have it return false if the parse failed rather than exiting!
   *
   * @param inputfile The text file with a subspace detector configuration output from the
   * Preprocessor
   * @return True if the configuration file parsed with no errors!
   * @throws FileNotFoundException if the configuration file does not exist
   * @throws IOException under weird circumstance reading configuration file.
   */
  public boolean readConfigFile(String inputfile) throws FileNotFoundException, IOException {

    String line = null;
    StringBuilder sbscr = new StringBuilder(100);
    boolean waveformtemplatefiles = false;
    boolean filterparameters = false;
    boolean channelparameters = false;
    boolean locationparameters = false;
    boolean outputpathparameter = false;
    boolean startstopparameters = false;
    boolean acquisitionparameters = false;
    boolean cwbparameters = false;
    boolean templateparameters = false;
    boolean outputparameters = false;
    boolean detectionthresholdparameters = false;

    ArrayList<String> templatenames = new ArrayList<String>(1);
    //
    // Initialize station information and filter information
    // Set default filter parameters
    stationinfo = new StationCollection();
    String refphasedata;
    try {
      try ( // The 1st line cannot be commented out.  It is ALWAYS taken as the
              // reference event
              BufferedReader in = new BufferedReader(new FileReader(inputfile))) {
        refphasedata = in.readLine();

        while ((line = in.readLine()) != null) {
          line = line.trim();
          if (line.length() <= 0) {
            continue;                                  // empty line
          }
          if (line.charAt(0) == '#' || line.charAt(0) == '!') {
            continue;      // comment
          }
          String[] parts = line.split("\\s+");
          if (parts[0].contains(":")) {
            parts[0] = parts[0].replaceAll(":", "").trim();
          }
          // Set filter parameters if they exist in the configuration file
          switch (parts[0]) {
            case "bandpass":
            case "lowpass":
            case "highpass":
              if (parts.length == 4) {
                hpcorner = Float.parseFloat(parts[1]);
                lpcorner = Float.parseFloat(parts[2]);
                npoles = Integer.parseInt(parts[3]);
                if (parts[0].equals("bandpass")) {
                  filtertype = "bandpass";
                }
                if (parts[0].equals("lowpass")) {
                  filtertype = "lowpass";
                }
                if (parts[0].equals("highpass")) {
                  filtertype = "highpass";
                }
                filterparameters = true;
              } else {
                prta("filter parameters not correctly specified");
              }
              break;
            case "channels":
              String[] completelist = new String[parts.length - 1];
              int nch = 0;
              for (int i = 1; i < parts.length; i++) {
                completelist[i - 1] = parts[i];
                if (!"#".equals(parts[i].substring(0, 1))) {
                  nch++;
                }
              }
              String[] channels = new String[nch];
              nch = 0;
              for (int i = 1; i < parts.length; i++) {
                if (!"#".equals(parts[i].substring(0, 1))) {
                  channels[nch] = parts[i].trim();
                  nch++;
                }
              }
              stationinfo.setChannels(channels);
              stationinfo.setCompleteChannelList(completelist);
              channelparameters = true;
              break;
            case "historic_number":
              if (parts.length == 2) {
                historicnumber_hrs = Float.parseFloat(parts[1]);
              } else {
                prta("historic number hours parameter not correctly specified");
                return false;
              }
              break;
            case "SNRcutoff":
              if (parts.length == 2) {
                SNRcut = Float.parseFloat(parts[1]);
              } else {
                prta("SNR cutoff parameter not correctly specified");
                return false;
              }
              break;
            case "location":
            case "locationcode":
              String loc;
              if (parts.length == 1) {
                loc = "  ";
              } else {
                loc = parts[1].trim();
                loc = loc.trim().replaceAll("-", " ");
              }
              stationinfo.setLocation(loc);
              locationparameters = true;
              break;
            case "output_path":
              if (parts.length == 2) {
                outputpath = parts[1];
                outputpathparameter = true;
              } else {
                prta("output path not specified correctly");
              }
              break;
            case "json":
              if (parts.length == 5) {
                jsonPath = parts[1];
                agency = parts[2];
                author = parts[3];
                setEventType(parts[4]);
                jsonOut = true;
                outputpathparameter = true;
              } else {
                prta("json path not specified correctly");
              }
              break;
            case "broker":
              if (parts.length == 5) {
                brokerConfig = parts[1];
                agency = parts[2];
                author = parts[3];
                setEventType(parts[4]);
                eventType = parts[4];

                jsonOut = true;
                outputpathparameter = true;
              } else {
                prta("json path not specified correctly");
              }
              break;
            case "start_stop":
              if (parts.length == 3) {
                starttime = parts[1];
                stoptime = parts[2];
                startstopparameters = true;
              } else {
                prta("start_stop parameters not correctly specified");
              }
              break;
            case "acquisition":
            case "acquisition_parameters":
              if (parts.length == 4) {
                waittimeinMillis = 1000 * Integer.parseInt(parts[1]);
                stepintervalinMillis = 1000 * Integer.parseInt(parts[2]);
                realtimeintervalinMillis = 1000 * Integer.parseInt(parts[3]);
                acquisitionparameters = true;
              } else {
                prta("acquisition parameters not correctly specified");
              }
              break;
            case "inputcwb":
              if (parts.length == 3) {
                inputcwbip = parts[1];
                inputcwbport = Integer.parseInt(parts[2]);
                cwbparameters = true;
              } else {
                prta("input CWB parameters not correctly specified");
              }
              break;
            case "detectionthreshold_parameters":
              if (parts.length == 5) {
                //maximum duration of noise estimate is 6 hours (21600 sec)
                detectionthreshold = Float.parseFloat(parts[1]);
                noisewindowlengthinsec = Float.parseFloat(parts[2]);
                if (noisewindowlengthinsec > 21600) {
                  noisewindowlengthinsec = 21600;
                }
                detectionthresholdscalefactor = Float.parseFloat(parts[3]);
                detectionthresholdtype = parts[4];
                if (!"constant".equals(detectionthresholdtype)
                        && !"mad".equals(detectionthresholdtype)
                        && !"z_score".equals(detectionthresholdtype)
                        && !"all".equals(detectionthresholdtype)) {
                  detectionthresholdtype = "all";
                }
                detectionthresholdparameters = true;
              } else {
                prta("noise estimation parameters not correctly specified");
              }
              break;
            case "template_parameters":
              if (parts.length == 5) {
                dataLengthinsec = Float.parseFloat(parts[1]);
                templateduration = Float.parseFloat(parts[2]);
                averagingduration = Float.parseFloat(parts[3]);
                preEvent = Float.parseFloat(parts[4]);
                templateparameters = true;
              } else {
                prta("Template parameters not correctly specified");
              }
              break;
            case "waveform_templates":
              if (parts.length == 1 || parts.length > 4) {
                prta("Template parameters not correctly specified");
              } else {
                Util.clear(sbscr);
                for (int i = 1; i < parts.length; i++) {
                  sbscr.append(parts[i]).append(" ");
                }
                templatenames.add(sbscr.toString().trim());
                waveformtemplatefiles = true;
              }
              break;
            case "output_files":
              for (String oparam : parts) {
                if (oparam.contentEquals("ccresults")) {
                  ccresults = true;
                  break;
                }
                outputparameters = true;
              }
              break;
            // these are from the subspacepreprocessor that did not seem to be needed here
            case "sample":
            case "sample_rate":
              if (parts.length == 2) {
                stationinfo.setRate((int) Double.parseDouble(parts[1]));
                //filter.setRate((int) Double.parseDouble(parts[1]));
              } else {
                prta("*** sample rate not correctly specified (sample: rate) line=" + line);
                return false;
              }
              break;
            case "station":
            case "station_coordinates":
              if (parts.length == 4) {
                stationinfo.setLatitude(Float.parseFloat(parts[1]));
                stationinfo.setLongitude(Float.parseFloat(parts[2]));
                stationinfo.setElevation(Float.parseFloat(parts[3]));
              } else {
                prta(" *** Station parameters not correctly specified (station: lat long elev) line=" + line);
                return false;
              }
              break;
            case "centroid_location":             /// These parameters are apparently not used
            case "source_receiver_distance":
            case "radial_distance":
              break;
            default:
              if (parts.length < 15) {   // If is more its likely just a left over pick
                prta("** Unknown line in config file line=" + line);
              }
              break;
          }
        }
      }       // end of reading from config file       // end of reading from config file       // end of reading from config file       // end of reading from config file

      if (filterparameters == false) {
        prta("** Filter parameters not specified");
        return false;
      }
      if (channelparameters == false) {
        prta("** Channels parameters not specified");
        return false;
      }
      if (locationparameters == false) {
        prta("** Location code not specified");
        return false;
      }
      if (outputpathparameter == false) {
        prta("** Directory path to waveforms not specified");
        return false;
      }
      if (startstopparameters == false && starttime == null) {
        prta("** Time for starting and stopping processing not specified");
        return false;
      }
      if (acquisitionparameters == false) {
        prta("* Waveform acquisition parameters not specified");
        //return false;
      }
      if (cwbparameters == false) {
        prta("* CWB IP address and port number not specified");
        //return false;
      }
      if (templateparameters == false) {
        prta("** Template parameters not specified");
        return false;
      }
      if (waveformtemplatefiles == false) {
        prta("** Name and location of waveform templates not specified");
        return false;
      }
      if (outputparameters == false) {
        prta("** Output file parameters not specified");
        return false;
      }
      if (detectionthresholdparameters == false) {
        prta("** Detection threshold parameters not specified");
        return false;
      }

      String[] refsplit = refphasedata.split("\\s+");
      stationinfo.setNetwork(refsplit[8].trim());
      stationinfo.setStname(refsplit[9].trim());
      stationinfo.setPhaseLabel(refsplit[12].trim());
      stationinfo.setPhaseLabelComponent(refsplit[10].trim());
      referenceorigintime = new GregorianCalendar();
      referenceorigintime.setTimeInMillis(Util.stringToDate2(refsplit[1].trim() + " " + refsplit[2]).getTime());
      referencelatitude = Float.parseFloat(refsplit[3].trim());
      referencelongitude = Float.parseFloat(refsplit[4].trim());
      referencedepth = Float.parseFloat(refsplit[5].trim());
      referencemagnitude = Float.parseFloat(refsplit[6].trim());
      referencemagtype = refsplit[7].trim();
      referencearrivaltime = new GregorianCalendar();
      referencearrivaltime.setTimeInMillis(Util.stringToDate2(refsplit[13].trim() + " " + refsplit[14].trim()).getTime());

      // Determine number of templates to use
      int npts = 0;
      boolean first = true;
      int nch = 0;
      int ntemplates = 0;
      for (int j = 0; j < templatenames.size(); j++) {
        String[] wf = templatenames.get(j).split("\\s+");
        int nn = 0;
        for (String wf1 : wf) {
          if (!"#".equals(wf1.substring(0, 1))) {
            SACInputStream ss = new SACInputStream(wf1.trim());
            if (first == true) {
              npts = ss.header.npts;
            }
            if (ss.header.npts < npts) {
              npts = ss.header.npts;
            }
            ss.close();
            nn++;
            //int sps = (int) (1.0/ss.header.delta);
            int sps = (int) Math.round(1.0 / ss.header.delta);
            stationinfo.setRate(sps);
          }
        }
        if (nn != 0) {
          nch = nn;
        }
        if (nn != 0) {
          ntemplates++;
        }
      }

      // segmentLength: block length in samples of the contiguous chunk of data to be correlated
      // templateLength: block length in samples of the templates used in the correlation
      // I haven't tested it, but I am pretty sure segmentLength needs to be bigger than templateLength
      dataLength = (int) (dataLengthinsec * stationinfo.getRate());
      templateLength = (int) (templateduration * stationinfo.getRate());
      averagingLength = (int) (averagingduration * stationinfo.getRate());

      templateLength = npts;
      numberofDetectors = ntemplates;
      numberofChannels = nch;
      // zzz contains the templates organized the way Dave Harris needs them
      float[][][] zzz = new float[numberofDetectors][numberofChannels][templateLength];
      float[][][] rrr = null;
      //JN edit (2015-10-08): changed empirical to mad, noisecc, and all
      //May need to change this setup slightly to accomidate mad not using all templates
      if (detectionthresholdtype.equals("mad") || detectionthresholdtype.equals("all")) {
        //rrr = new float[1][numberofChannels][templateLength];
        rrr = new float[numberofDetectors][numberofChannels][templateLength];
      }

      for (int idetector = 0; idetector < templatenames.size(); idetector++) {

        float[] tt = new float[templateLength * numberofChannels];

        String[] wf = templatenames.get(idetector).split("\\s+");
        int ich = 0;
        for (String wf1 : wf) {
          float[] t = new float[templateLength];
          if (!"#".equals(wf1.substring(0, 1))) {
            try (final SACInputStream ss = new SACInputStream(wf1.trim())) {
              ss.readData(t);
              System.arraycopy(t, 0, tt, templateLength * ich, templateLength);
            }
            ich++;
          }
        }
        normalizeTemplates(tt);
        for (int iwaveform = 0; iwaveform < numberofChannels; iwaveform++) {
          System.arraycopy(tt, iwaveform * templateLength, zzz[idetector][iwaveform], 0, templateLength);
        }

        // You only need to use one template for the estimate of noise.  Assume the 1st one in the list
        // is the highest rank template
        //JN edit 2015: set up noise detectors using all idetectors and changed empirical to mad, noisecc, and all
        //May need to change this setup slightly to accomidate mad not using all templates
        //if( detectionthresholdtype.equals("empirical") && idetector == 0) {
        if (detectionthresholdtype.equals("mad") || detectionthresholdtype.equals("all")) {
          float[] rr = new float[templateLength * numberofChannels];
          for (int iwaveform = 0; iwaveform < numberofChannels; iwaveform++) {
            float[] r = new float[templateLength];
            System.arraycopy(tt, iwaveform * templateLength, r, 0, templateLength);
            EmpiricalNoiseTemplates.computeRandomTimeSeries(r,
                    hpcorner, lpcorner, npoles, filtertype, stationinfo.getRate());
            System.arraycopy(r, 0, rr, templateLength * iwaveform, templateLength);
          }
          normalizeTemplates(rr);
          for (int iwaveform = 0; iwaveform < numberofChannels; iwaveform++) {
            System.arraycopy(rr, iwaveform * templateLength, rrr[idetector][iwaveform], 0, templateLength);
          }
        }
      }

      if (detectors == null) {
        detectors = new SubspaceDetectorCollection(numberofChannels, dataLength);
      }
      detectors.addTemplate(zzz, "test");

      //JN edit (2015-10-08): changed empirical to mad and all
      if (detectionthresholdtype.equals("mad") || detectionthresholdtype.equals("all")) {
        if (rdetectors == null) {
          rdetectors = new SubspaceDetectorCollection(numberofChannels, dataLength);
        }
        rdetectors.addTemplate(rrr, "test");
      }
    } catch (FileNotFoundException e) {
      prta("**** File not found reading config for SSD file=" + inputfile);
      throw e;
    } catch (RuntimeException e) {
      prta("*** Runtime exception parsing parameters continue e=" + e + " line=" + line);
      e.printStackTrace(par.getPrintStream());
      return false;
    }
    return true;
  }

  public void normalizeTemplates(float[] data) {

    double energy = 0.0;
    for (int i = 0; i < data.length; i++) {
      energy += data[i] * data[i];
    }
    float T = (float) (1.0 / Math.sqrt(energy));
    for (int i = 0; i < data.length; i++) {
      data[i] *= T;
    }

  }

  public static void main(String[] args) throws FileNotFoundException, IOException, InterruptedException {
    Util.setModeGMT();
    EdgeProperties.init();
    Config ssd = new Config(args[0], null);  // No EdgeThread for logging
  }
}
