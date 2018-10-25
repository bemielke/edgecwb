/*
 *  This software is in the public domain because it contains materials 
 *  that originally came from the United States Geological Survey, 
 *  an agency of the United States Department of Interior. For more 
 *  information, see the official USGS copyright policy at 
 *  http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.subspaceprocess;

import com.oregondsp.io.SACFileWriter;
import gov.usgs.adslserverdb.MDSChannel;
import gov.usgs.anss.edgethread.EdgeThread;
import gov.usgs.anss.edge.EdgeProperties;
import gov.usgs.anss.util.StaSrv;
import gov.usgs.anss.util.Distaz;
import gov.usgs.anss.util.Util;
import java.io.IOException;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.io.File;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;

/**
 * This class parses the configuration files to setup a SSD. It is created empty and then normally
 * the user calls setConfiguration(filename) to load the parameters, and then buildTemplates() to
 * actually acquire waveforms, possibly do the SVD if the configuration file calls for it.
 * <p>
 * The FilterCollection is only used in this routine to store and retrieve parameters and not to
 * actually filter anything. They could just as well be local variables in this object.
 * <p>
 *
 * <PRE>
 * SubspacePreprocess ssp = new SubspacePreprocess(null, false);
 * boolean isConfigOk = ssp.setConfiguration("myconfigfile.cfg");
 * ssp.cleanupFiles();            // need to read configuration first so that output_path is known
 * ssp.buildTemplates();
 * </PRE>
 *
 * @author benz additional work by wyeck and dketchum
 */
public class SubspacePreprocess {

  protected SubspaceTemplatesCollection templates;

  protected StationCollection stationinfo;

  protected FilterCollection filter;

  protected ArrayList<String> templatenames;

  protected ArrayList<String> wfinfo;
  protected ArrayList<String> phasedata = new ArrayList<String>(1);
  //protected ArrayList<String> eqlist;                     

  boolean ccresults = false;

  protected int waittimeinMillis = 120000;         // The wait time (in milliseconds) to check for filled gaps in the CWB
  protected int stepintervalinMillis = 10000;      // The interval in time (in milliseconds) to step thru a gap in the CWB
  protected int realtimeintervalinMillis = 600000; // The interval of time (in milliseconds) preceeding real-time you are willing to wait to fill gaps

  protected int templateLength;                    // Length in number of samples of ALL of the templates using in the processing
  protected float templateduration = 10;           // Length (in sec) of the default template
  protected float averagingduration = 5;            // Duration over which a peak is defined.  Takes highest peak in the duration
  protected float blockLength = 600;               // Length (in sec) of block of data to process
  protected int segmentLength;                     // Length in number of samples of data to be requested from the CWB (DCK - computed by not used)
  protected int averagingLength;                   // Length in number of samples of the window used to find peak amplitude and detection peak

  protected float noisewindowlengthinsec = 1800;
  protected float detectionthresholdscalefactor = 9;
  protected String detectionthresholdtype = "constant";   // "constant", constant threshold, "mad", median absolute deviation

  protected boolean align = false; // Whether templates should be aligned... 
  protected float alignBuffer = 1f; // Seconds to buffer templates on each side before alignment
  protected float corrRange = 1.5f; // Seconds to perform cross correlation across
  protected float pickwindow = 0.75f; // the plus/minus pick window, maskes out everything surrounding when doing correlation ... 0 implies use full trace

  protected float completeness = 0.90f;     // this was final until HB said to make it a parameter

  protected String inputcwbip = "137.227.224.97";
  protected int inputcwbport = 2061;

  protected float detectionthreshold = 0.65f;       // Detecton threshold below which you do not look for a detection
  protected String starttime;                      // Start time of processing (format: 2012/02/14 12:00)
  protected String stoptime;                       // End time of processing (format: 2014/01/01 00:00)
  protected float maxdist = 10;                    // Maximum distance (in kilometers) between reference event getLocation and any new event that is added      

  protected float centroidlat = Float.MAX_VALUE;                // centroid coordinates.
  protected float centroidlon = Float.MAX_VALUE;
  protected float centroiddep = Float.MAX_VALUE;
  protected float srcrcvdist;

  private String outputpath = "./";
  private String inputfile;
  private final boolean operationalOutput;        // If set, then only generate files needed for ops - no plots
  // parameters passed from setConfiguration() to buildTemplates
  private boolean templateparametersfound;        // still needed to coordinate between setConfiguration() and buildTemplates()
  private final double[] distaz = new double[4]; // array for distaz results.
  private StaSrv srv;                             // If infor is needed from the MDS, this is used

  private float preEvent = -0.1f;

  private final GregorianCalendar current = new GregorianCalendar();
  //private final GregorianCalendar endtimeofprocessing = new GregorianCalendar();
  //private final GregorianCalendar lastdetection = new GregorianCalendar();
  private EdgeThread par;                     // if set, logging goes to this thread

  private void prt(String s) {
    if (par == null) {
      Util.prt(s);
    } else {
      par.prt(s);
    }
  }

  private void prta(String s) {
    if (par == null) {
      Util.prta(s);
    } else {
      par.prta(s);
    }
  }

  private void prt(StringBuilder s) {
    if (par == null) {
      Util.prt(s);
    } else {
      par.prt(s);
    }
  }

  private void prta(StringBuilder s) {
    if (par == null) {
      Util.prta(s);
    } else {
      par.prta(s);
    }
  }

  public void setLoggering(EdgeThread parent) {
    par = parent;
  }

  /**
   *
   * @param inputfile optional input file if known, it becomes the default for setConfiguration()
   * @param operational If true, output files are the minimum need for a operational pick - no plot
   * files
   */
  public SubspacePreprocess(String inputfile, boolean operational) {
    Util.setModeGMT();
    this.operationalOutput = operational;
    this.inputfile = inputfile;
    EdgeProperties.init();
  }

  /**
   * This deletes all files from the output directory. Normally called before build files to keep a
   * cleaner directory. Files with .sac or .dat are deleted.
   *
   */
  public void cleanUpFiles() {
    File dir = new File(outputpath);
    File[] files = dir.listFiles();
    for (File file : files) {
      String name = file.getAbsolutePath();
      if (name.endsWith(".dat") || name.endsWith(".sac")) {
        file.delete();
      }
    }
  }

  /**
   * This is over-the-top bad coding and too complicated. It tries to anticipate all the variations
   * on input and configurating. I will document as best I can
   *
   * @param inputfile This is a configuration file for the Preprocessor - someone ill defined! If
   * not present one from constructor used
   * @return If true, the file parse fine
   * @throws java.io.FileNotFoundException
   * @thorws FileNotFoundException If one occurs, generally on the input file
   */
  public boolean setConfiguration(String inputfile) throws FileNotFoundException, IOException {
    if (inputfile != null) {
      this.inputfile = inputfile;
    }
    centroidlat = Float.MAX_VALUE;                // centroid coordinates.
    centroidlon = Float.MAX_VALUE;
    centroiddep = Float.MAX_VALUE;

    //
    // Initialize getStation information and filter information
    // Set default filter parameters
    stationinfo = new StationCollection();
    filter = new FilterCollection();
    filter.setHPcorner((float) 1.0);
    filter.setLPcorner((float) 3.0);
    filter.setNpoles(3);
    filter.setFilterType("bandpass");
    String line = "First";
    int iline = -1;
    try ( // Get ready to read the file
            BufferedReader in = new BufferedReader(new FileReader(this.inputfile))) {
      // ArrayList contains all of the EQ getLocation and phase data that will be processed to
      // compute templates
      // wfinfo: contains all getLocation and phase info even for events that are commented out so
      // that they can be put into the state file.
      // phasedata:  contains locations and phase info for only the events that will be processed
      wfinfo = new ArrayList<String>(1);
      while ((line = in.readLine()) != null) {
        iline++;
        if (line.trim().length() == 0) {   // Blank lines mean new section
          break;
        }
        if (!"#".equals(line.substring(0, 1))) {
          phasedata.add(line.trim());
        }
        wfinfo.add(line);
      }

      while ((line = in.readLine()) != null) {
        iline++;
        line = line.trim();
        if (line.length() <= 0) {
          continue;                               // empty line
        }
        if (line.charAt(0) == '#' || line.charAt(0) == '!') {
          continue;   // commented out
        }
        String[] parts = line.split("\\s+");
        parts[0] = parts[0].toLowerCase();
        if (parts[0].contains(":")) {
          parts[0] = parts[0].replaceAll(":", "").trim();
        }

        // Set filter parameters if they exist in the configuration file
        switch (parts[0]) {
          case "bandpass":
          case "lowpass":
          case "highpass":
            if (parts.length == 4) {
              filter.setHPcorner(Float.parseFloat(parts[1]));
              filter.setLPcorner(Float.parseFloat(parts[2]));
              filter.setNpoles(Integer.parseInt(parts[3]));

              if (parts[0].equals("bandpass")) {
                filter.setFilterType("bandpass");
              } else if (parts[0].equals("lowpass")) {
                filter.setFilterType("lowpass");
              } else if (parts[0].equals("highpass")) {
                filter.setFilterType("highpass");
              } else {
                prta("*** type of filter is illegal line=" + line);
                return false;
              }
            } else {
              prta("*** filter parameters not correctly specified (type hpc lpc npolls) line=" + line);
              return false;
            }
            break;
          // Set channel information if it exists in the configuration file
          case "channels":
            String[] completelist = new String[parts.length - 1];
            int nch = 0;
            for (int i = 1; i < parts.length; i++) {
              completelist[i - 1] = "" + parts[i];
              if (!"#".equals(parts[i].substring(0, 1))) {
                nch++;
              }
            }
            String[] channels = new String[nch];
            nch = 0;
            for (int i = 1; i < parts.length; i++) {
              if (!"#".equals(parts[i].substring(0, 1))) {
                channels[nch] = "" + parts[i].trim();
                nch++;
              }
            }
            stationinfo.setChannels(channels);
            stationinfo.setCompleteChannelList(completelist);
            break;
          // Set getLocation information if it exists in the configuration file
          case "locationcode":
          case "location":
            String loc;
            if (parts.length == 1) {
              loc = "  ";
            } else {
              loc = parts[1].trim();
              loc = loc.trim().replaceAll("-", " ");
            }
            stationinfo.setLocation(loc);
            break;
          // Set sample rate if it exists in the configuration file
          case "sample":
            if (parts.length == 2) {
              stationinfo.setRate((int) Double.parseDouble(parts[1]));
              filter.setRate((int) Double.parseDouble(parts[1]));
            } else {
              prta("*** sample rate not correctly specified (sample: rate) line=" + line);
              return false;
            }
            break;
          // Set the outputpath if it exists in the configuration file
          case "output_path":
            if (parts.length == 2) {
              outputpath = "" + parts[1];
            } else {
              prta("*** output path not specified correctly (output_path: path) line=" + line);
              return false;
            }
            break;
          // Set the start_stop date.  This must exist for code to excute
          case "start_stop":
            if (parts.length == 3) {
              starttime = parts[1];
              stoptime = parts[2];
            } else {
              prta("*** start_stop parameters not correctly specified (start_stop: start stop) line=" + line);
              return false;
            }
            break;
          // Set the acquisition parameters if they exists in the configuration file
          case "acquisition":
            if (parts.length == 4) {
              waittimeinMillis = 1000 * Integer.parseInt(parts[1]);
              stepintervalinMillis = 1000 * Integer.parseInt(parts[2]);
              realtimeintervalinMillis = 1000 * Integer.parseInt(parts[3]);
            } else {
              prta("*** acquisition parameters not correctly specified (acquisition: waittime stepint realtimeint) line=" + line);
              return false;
            }
            break;
          // Set the detection parameters if they exists in the configuration file
          case "detectionthreshold_parameters":
            if (parts.length == 5) {
              detectionthreshold = Float.parseFloat(parts[1]);
              noisewindowlengthinsec = Float.parseFloat(parts[2]);
              // Longest allowable noise window is 6 hours
              if (noisewindowlengthinsec > 21600) {
                noisewindowlengthinsec = 21600;
              }
              detectionthresholdscalefactor = Float.parseFloat(parts[3]);
              detectionthresholdtype = parts[4];
              if (!"constant".equals(detectionthresholdtype)
                      && !"empirical".equals(detectionthresholdtype)) {
                detectionthresholdtype = "constant";
              }
            } else {
              prta("*** noise detection threshold parameters not specified correctly (detectionthreshold_parameters: detthrhld noisewind scale) line=" + line);
              return false;
            }
            break;
          case "completeness":
            if (parts.length == 2) {
              completeness = Float.parseFloat(parts[1]);
              prta("Set completeness = "+completeness);
            }
            else {
              prta("**** input CWB parameter completeness wrong number of arguments");
              return false;
            }
            break;
          // Set the cwb ip address and port number if they exists in the configuration file
          case "inputcwb":
            if (parts.length == 3) {
              inputcwbip = parts[1];
              inputcwbport = Integer.parseInt(parts[2]);
            } else {
              prta("*** input CWB parameters not correctly specified (inputcwb: cwbip cwbport) line=" + line);
              return false;
            }
            break;
          //The average latitude and longitude (centriod) of all the earthquakes
          case "centroid":
            if (parts.length == 4) {
              centroidlat = Float.parseFloat(parts[1]);
              centroidlon = Float.parseFloat(parts[2]);
              centroiddep = Float.parseFloat(parts[3]);
            } else {
              prta("*** centroid parameters not correctly specified centroid: lat lon depth) line=" + line);
              return false;
            }
            break;
          // Set the src-rec distance if it exists in the configuration file
          case "source_receiver":
            if (parts.length == 2) {
              srcrcvdist = Float.parseFloat(parts[1]);
            } else {
              prta("*** source_receiver distance not specificed correctly (source_receiver: srcrcvdist) line=" + line);
              return false;
            }
            break;
          // Set the radial search distance for including phase data if it exists
          case "radial_distance":
            if (parts.length == 2) {
              maxdist = Float.parseFloat(parts[1]);
            } else {
              prta("*** radial distance not correctly specified (radial_distance: dist) line=" + line);
              return false;
            }
            break;
          // Set the template parameters if they exists in the configuration file
          case "template_parameters":
            if (parts.length == 5) {
              blockLength = Float.parseFloat(parts[1]);
              templateduration = Float.parseFloat(parts[2]);
              averagingduration = Float.parseFloat(parts[3]);
              preEvent = Float.parseFloat(parts[4]);
              templateparametersfound = true;
            } else {
              prta("*** Template parameters not correctly specified (template_parameters: blocklen tempdur avgdur preevent) line=" + line);
              return false;
            }
            break;
          // Set the alignment parameters if they exists in the configuration file
          case "align":
            if (parts.length == 5) {
              if (parts[1].contains("true")) {
                align = true; //whether to aling or not
              }
              if (parts[1].contains("false")) {
                align = false;
              }
              alignBuffer = Float.parseFloat(parts[2]); //the buffer to add to around picks pre alignment, should be londer than length of correlation
              corrRange = Float.parseFloat(parts[3]); // the lenght of the correlation window, +-, so a value of 1s will look at +1s -1s from pick
              pickwindow = Float.parseFloat(parts[4]);
            } else {
              prta("*** Alignment Parameters Not Set Correctly (align: [true|false] alignBuf corrRange pickwindow) line=" + line);
              return false;
            }
            break;
          // Set the getStation coordinate parameters if they exists in the configuration file
          case "station":
            if (parts.length == 4) {
              stationinfo.setLatitude(Float.parseFloat(parts[1]));
              stationinfo.setLongitude(Float.parseFloat(parts[2]));
              stationinfo.setElevation(Float.parseFloat(parts[3]));
            } else {
              prta(" *** Station parameters not correctly specified (station: lat long elev) line=" + line);
              return false;
            }
            break;
          // Set the name of the waveform templates to use if they exists in the configuration file
          case "waveform_templates":
            if (parts.length == 1 || parts.length > 4) {
              prta("*** Template parameters not correctly specified (waveform_templates: temp1 [temp2] [temp3]) line=" + line);
              return false;
            } else {
              if (templatenames == null) {
                templatenames = new ArrayList<String>(1);
              }
              String tname = "";
              for (int i = 1; i < parts.length; i++) {
                tname = tname + parts[i] + " ";
              }
              templatenames.add(tname.trim());
            }
            //waveformtemplatefilesfound = true;
            break;
          case "output_files":
            for (String oparam : parts) {
              if (oparam.contentEquals("ccresults")) {
                ccresults = true;
                break;
              }
            }
            break;
          default:
            prta("**** setParameters did not decode line " + iline + "=" + line);
            return false;
        }
      }
    } catch (RuntimeException e) {
      prt("*** Runtime exception parsing parameters continue e=" + e + " line=" + line);
      if (par == null) {
        e.printStackTrace();
      } else {
        e.printStackTrace(par.getPrintStream());
      }
      return false;
    }
    return true;
  }

  /**
   * The parameters are read and tested in setConfiguration(), but the actual work of making
   * templates is done here from those parameters
   *
   * @return true if all templates built normally
   * @throws IOException
   */
  public boolean buildTemplates() throws IOException {
    try {
      if (starttime == null) {
        current.setTimeInMillis(System.currentTimeMillis());
        starttime = Util.ascdatetime2(current).toString();
        starttime = starttime.replaceAll(" ", "-");
        current.add(Calendar.MILLISECOND, 1000 * (86400));
        stoptime = Util.ascdatetime2(current).toString();
        stoptime = stoptime.replaceAll(" ", "-");
      }
      if(phasedata.isEmpty()) {
        prta("buildTemplates() There is no phase data - skip this");
        return false;
      }

      //if( templatenames != null ) waveformtemplatefilesfound = true;
      // Query getStation metadata server to get information about the getStation
      // that includes NSCL, coordinates and sample rate
      StringBuilder sb = new StringBuilder(1000);
      ArrayList<MDSChannel> list = new ArrayList<MDSChannel>(10);
      String[] tparams = phasedata.get(0).split("\\s+");
      String network = tparams[8];
      for (int j = network.length(); j < 2; j++) {
        network = network + " ";
      }
      String station = tparams[9];
      for (int j = station.length(); j < 5; j++) {
        station = station + " ";
      }

      // If we need information on coordinates, rates, location codes or matching channels, call the MDS
      while (!stationinfo.coordinatesOK() || stationinfo.getRate() <= 0
              || stationinfo.getLocation() == null || stationinfo.getChannels() == null) {
        if (srv == null) {
          srv = new StaSrv("cwbpub.cr.usgs.gov", 2052, 500);
        }
        String nscl = network + station + tparams[10].substring(0, tparams[10].length() - 1) + "." + tparams[11].trim();
        String ot = tparams[1] + "-00:00:00";
        int len = srv.getSACResponse(nscl, ot, "nm", sb);
        // Ask for a wildcarded channel on a given date
        //int len = srv.getSACResponse("USDUG  ...","2013,318-00:00:00", "nm", sb);
        // This parses what was returned (mdget output) into objects
        MDSChannel.loadMDSResponse(sb, list);
        if (list.isEmpty()) {
          prta("Did not get response for " + sb + " wait 10 seconds and try again");
          Util.sleep(100000);
        } else {
          break;
        }
      }
      stationinfo.setNetwork(network);
      stationinfo.setStname(station);

      if (!stationinfo.coordinatesOK()) {
        if (list.size() > 0) {
          stationinfo.setLatitude(list.get(0).getLatitude());
          stationinfo.setLongitude(list.get(0).getLongitude());
          stationinfo.setElevation(list.get(0).getElevation());
        } else {
          prta(network + " " + station + " *** does not have coordinates in the .cfg or int the list!");
          return false;
        }
      }

      if (stationinfo.getRate() <= 0) {
        stationinfo.setRate((int) list.get(0).getRate());
        filter.setRate((int) list.get(0).getRate());
      }

      // This gets the getLocation code and sample rate based on the component
      // for which the pick is derived
      if (stationinfo.getLocation() == null) {
        stationinfo.setLocation(list.get(0).getLocationCode());
      }

      // Now find out how many components have the same getLocation code
      // and 1st part (2 characters) of the channels code.
      // This is needed because some stations have the same channel code,
      // but different getLocation code      
      //USDUG  BH100 40.195 -112.8133 1477.0 40.0 Hz 0.0 0.0 0.0 inst=STS2-I=80442=Gen=Q330SR=0746
      //USDUG  BH200 40.195 -112.8133 1477.0 40.0 Hz 90.0 0.0 0.0 inst=STS2-I=80442=Gen=Q330SR=0746
      //USDUG  BHZ00 40.195 -112.8133 1477.0 40.0 Hz 0.0 -90.0 0.0 inst=STS2-I=80442=Gen=Q330SR=0746
      if (stationinfo.getChannels() == null) {
        int nchannels = 0;
        for (int j = 0; j < list.size(); j++) {
          String c = list.get(j).toString();
          if (c.substring(10, 12).equals(stationinfo.getLocation())
                  && c.substring(7, 9).equals(tparams[10].substring(0, 2))) {
            nchannels++;
          }
        }
        String[] channels = new String[nchannels];
        //String [] channellist = new String [nchannels];     // DCK: this is apparently redundant to channels[]
        int n = 0;
        for (int j = 0; j < list.size(); j++) {
          String c = list.get(j).toString();
          if (c.substring(10, 12).equals(stationinfo.getLocation())
                  && c.substring(7, 9).equals(tparams[10].substring(0, 2))) {
            channels[n] = c.substring(7, 10);
            //channellist[n] = c.substring(7,10);     // DCK : see above
            n++;
          }
        }
        stationinfo.setChannels(channels);
        stationinfo.setCompleteChannelList(channels);
      }

      // segmentLength: block length in samples of the contiguous chunk of data to be correlated
      // templateLength: block length in samples of the templates used in the correlation
      // I haven't tested it, but I am pretty sure segmentLength needs to be bigger than templateLength
      segmentLength = (int) (blockLength * stationinfo.getRate());
      templateLength = (int) (templateduration * stationinfo.getRate());
      averagingLength = (int) (averagingduration * stationinfo.getRate());

      if (templateparametersfound == false) {
        // This chunk of code needs to be rewritten. If the distance is too small
        //  using the java-version of ttimes doesn't compute a travel-time, so I
        //  switch to a simpler measure of travel-time.
        float maxtl = 0;
        //Distazold dist = new Distazold();
        for (int nt = 0; nt < phasedata.size(); nt++) {
          float tt = 0;
          String[] eq = phasedata.get(nt).split("\\s+");
          float evlat = Float.parseFloat(eq[3]);
          float evlon = Float.parseFloat(eq[4]);
          float evdep = Float.parseFloat(eq[5]);
          //dist.computeDistaz(evlat, evlon, stationinfo.getLatitude(), stationinfo.getLongitude());
          Distaz.distaz(evlat, evlon, stationinfo.getLatitude(), stationinfo.getLongitude(), distaz);
          //This needs work
          //if( dist.getDeg() > 0.5 ) tt = computeTT((float)dist.getDeg(),evdep);
          if (tt <= 0) {
            //float distance = (float) Math.sqrt(dist.getDist() * dist.getDist() + evdep * evdep);
            float distance = (float) Math.sqrt(distaz[0] * distaz[0] + evdep * evdep);
            tt = surfacewaveTT(distance);
          }

          GregorianCalendar ot = new GregorianCalendar();
          GregorianCalendar at = new GregorianCalendar();
          ot.setTimeInMillis(Util.stringToDate2(eq[1] + " " + eq[2]).getTime());
          String dat1 = Util.ascdatetime2(ot).toString();
          at.setTimeInMillis(Util.stringToDate2(eq[13].trim() + " " + eq[14].trim()).getTime());
          String dat2 = Util.ascdatetime2(at).toString();
          float artt = (float) (at.getTimeInMillis() - ot.getTimeInMillis()) / 1000;

          if ((tt - artt) > maxtl) {    // figureing the maximum arr - origin time?
            maxtl = tt - artt;
          }
        }

        if (maxtl < 2.5) {
          maxtl = (float) 2.5;
        }
        if (maxtl > 300) {
          maxtl = (float) 300;
        }
        templateLength = (int) (maxtl * stationinfo.getRate());
        // Set the averagingLength based on the template length
        averagingLength = templateLength;
        if (averagingLength / stationinfo.getRate() > 30.0) {
          averagingduration = 30;
          averagingLength = (int) (averagingduration * stationinfo.getRate());
        }
      }

      //
      // Initialize the template object with the getStation, filter  and
      // template specific information.  Templates just stores the filter parameters but does not
      // actually use the collection.
      //
      templates = new SubspaceTemplatesCollection(par);
      templates.initialize(stationinfo, filter, preEvent, templateLength);
      templates.setOutputpath(outputpath);

      // The 1st line that specifies getLocation and phase information is ALWAYS the reference event
      // and it tells what type of processing will occur.
      String[] stmp = phasedata.get(0).split("\\s+");
      String collectiontype = "independent";
      if (stmp[stmp.length - 1].contentEquals("svd") || stmp[stmp.length - 1].contentEquals("SVD")) {
        collectiontype = "svd";
      }
      if (stmp[stmp.length - 1].contentEquals("indep") || stmp[stmp.length - 1].contentEquals("INDEP")) {
        collectiontype = "independent";
      }
      if (stmp[stmp.length - 1].contentEquals("empirical") || stmp[stmp.length - 1].contentEquals("EMPIRICAL")) {
        collectiontype = "empirical";
      }
      float[] shifts = new float[phasedata.size()];
      System.out.println(align);
      if (align == true) {
        shifts = templates.align(alignBuffer, corrRange, pickwindow, phasedata, inputcwbip, inputcwbport);
      }

      boolean refeq = true;
      for (int i = 0; i < phasedata.size(); i++) {
        String phd = "";
        String[] ss = phasedata.get(i).split("\\s+");
        for (int ii = 1; ii < ss.length; ii++) {
          phd = phd + ss[ii] + " ";
        }
        if (align == true) {
          templates.getTemplates(outputpath, phd, refeq, maxdist, inputcwbip, inputcwbport, false, shifts[i]);
        } else {
          templates.getTemplates(outputpath, phd, refeq, maxdist, inputcwbip, inputcwbport, false, 0);
        }
        // If we just loaded the reference data as part of loading the templates, save the Reference
        if (refeq && templates.getReferenceData() != null) {
          writeReferenceData();
        }
        refeq = false;
      }
      if (templates.TemporaryDetectorTemplates().isEmpty()) {
        prt("***** no templates for the events were loaded!");
        return false;
      } else {
        prt("#temporarytemplates=" + templates.TemporaryDetectorTemplates().size());
      }

      if (collectiontype.contains("svd")) {
        templates.computeEigenvectors(completeness, false);
      } else if (collectiontype.contains("independent")) {
        templates.updateDetectors();
      } else {
        prta("Must set SVD or indep");
        return false;
      }

      /**
       * else if ( collectiontype.contains("empirical") ) { // This is the case were the templates
       * are read in, filtered and cut to the correct length // They are then stacked and the first
       * order derivative is taken to create the template boolean refeq = true; for( int i = 0; i
       * &lt // * phasedata.size(); i++ ) { String phd c= ""; String [] ss =
       * phasedata.get(i).split("\\s+"); for( int ii = 1; ii &lt ss.length; ii++ ) phd=phd+ss[ii]+"
       * "; templates.getTemplates(phd, refeq, maxdist, inputcwbip, inputcwbport, false); refeq =
       * false; } if(templates.stackAndDifferentiate() == false) templates.updateDetectors(); } *
       */
      if (centroidlat == Float.MAX_VALUE) {
        computeEQCentroidLocation(templates);
      }

      stationinfo.setPhaseLabel(templates.getReferencePhaseLabel());
      stationinfo.setPhaseLabelComponent(templates.getReferencePhaseLabelComp());

      // This chunk of code initializes the detection methods
      // ttt is a template array used for lots of things
      float[][][] ttt = new float[1][templates.getNumberOfChannels()][templates.getTemplateLength()];

      templatenames = new ArrayList<String>(1);
      for (int i = 0; i < templates.getNumberOfTemplates(); i++) {

        String tnames = "";
        // This is bit unfortunate code because Template1 output 2-D arrays,
        // while the SubspaceDetectorCollection class requires 3-D arrays

        for (int ich = 0; ich < templates.getNumberOfChannels(); ich++) {

          System.arraycopy(templates.DetectorTemplates().get(i), ich * templateLength, ttt[0][ich], 0, templateLength);

          tnames = tnames + " " + outputpath + "template" + i + "_" + ich + ".sac";

        }

        templatenames.add(tnames.trim());

        writeTemplates(templates.DetectorTemplates().get(i), stationinfo.getRate(),
                stationinfo.getChannels().length, i);

      }

      // This computes the distance between the centroid getLocation and stations
      // The results are output into the updated configuration file, but not used
      //Distazold dd = new Distazold();
      //dd.computeDistaz(centroidlat, centroidlon, stationinfo.getLatitude(), stationinfo.getLongitude());
      Distaz.distaz(centroidlat, centroidlon, stationinfo.getLatitude(), stationinfo.getLongitude(), distaz);

      //srcrcvdist = (float) dd.getDist();
      srcrcvdist = (float) distaz[0];     // distance in km

      writeConfigurationState(current);
    } catch (RuntimeException e) {
      prta("Got runtime exception processing templates continue e=" + e);
      if (par == null) {
        e.printStackTrace();
      } else {
        e.printStackTrace(par.getPrintStream());
      }
      return false;
    }
    return true;
  }

  /**
   * write out the reference data for this station if there is any. If not, leave the reference data
   * behind.
   *
   * @throws IOException
   */
  public void writeReferenceData() throws IOException {
    // The time of the refdata is 60 seconds before its arrival time
    GregorianCalendar t = new GregorianCalendar();
    t.setTimeInMillis(templates.getReferenceArrivalTime().getTimeInMillis() - 60 * 1000);
    String cr = Util.ascdatetime2(t).toString();
    String ts = cr.substring(0, 4) + cr.substring(5, 7) + cr.substring(8, 10);
    ts = ts + "_" + cr.substring(11, 13) + cr.substring(14, 16) + cr.substring(17, 19);

    float delta = (float) (1.0 / templates.getRate());
    for (int i = 0; i < templates.getReferenceData().length; i++) {
      String nscl = Util.makeSeedname(templates.getNetwork(), templates.getStation(),
              templates.getChannels()[i], templates.getLocation());
      String filename = outputpath + nscl.replaceAll(" ", "_") + "_" + ts + "ref.sac";
      File f = new File(filename);
      if (f.exists() && f.length() > 20000) {
        continue;    // Nothing to do
      }
      SACFileWriter writernn = new SACFileWriter(filename);
      writernn.getHeader().nzyear = t.get(GregorianCalendar.YEAR);
      writernn.getHeader().nzjday = t.get(GregorianCalendar.DAY_OF_YEAR);
      writernn.getHeader().nzhour = t.get(GregorianCalendar.HOUR_OF_DAY);
      writernn.getHeader().nzmin = t.get(GregorianCalendar.MINUTE);
      writernn.getHeader().nzsec = t.get(GregorianCalendar.SECOND);
      writernn.getHeader().nzmsec = t.get(GregorianCalendar.MILLISECOND);
      writernn.getHeader().delta = delta;
      writernn.getHeader().b = 0;
      writernn.getHeader().knetwk = templates.getNetwork();
      writernn.getHeader().kstnm = templates.getStation();
      writernn.getHeader().khole = templates.getLocation();
      writernn.getHeader().kstnm = templates.getChannels()[i];
      writernn.writeFloatArray(templates.getReferenceData()[i]);
      writernn.close();
    }
  }

  /**
   * DCK - This does not seem to be used
   *
   * @param data
   */
  /*public void filterData(float[] data) {

    float delta = (float) (1.0 / filter.getRate());
    Butterworth F = null;
    //
    // The Butterworth has to be initialized for each seismogram
    //  to get the coefficients correct
    //
    if (filter.getFilterType().contains("highpass")) {
      F = new Butterworth(filter.getNpoles(), PassbandType.HIGHPASS,
              filter.getHPcorner(), filter.getLPcorner(), delta);
    }
    if (filter.getFilterType().contains("lowpass")) {
      F = new Butterworth(filter.getNpoles(), PassbandType.LOWPASS,
              filter.getHPcorner(), filter.getLPcorner(), delta);
    }
    if (filter.getFilterType().contains("bandpass")) {
      F = new Butterworth(filter.getNpoles(), PassbandType.BANDPASS,
              filter.getHPcorner(), filter.getLPcorner(), delta);
    }
    if (F != null) {
      F.filter(data);
    } else {
      prta(" ***** something is wrong with the filtering setup type="
              + filter.getFilterType() + " " + filter.getNpoles() + " hp=" + filter.getHPcorner() + " lp=" + filter.getLPcorner());
    }
  }*/

  /**
   * This method writes to a file the input parameters and updates the start time of the processing.
   *
   * @param statetime The time for this state
   * @throws java.io.FileNotFoundException If the printstream cannot be opened
   */
  public void writeConfigurationState(GregorianCalendar statetime) throws FileNotFoundException {

    String filename = outputpath + stationinfo.getNetwork().replaceAll(" ", "_") + stationinfo.getStname().replaceAll(" ", "_") + ".cfg";
    try (PrintStream state = new PrintStream(filename)) {
      for (int i = 0; i < wfinfo.size(); i++) {
        state.println(wfinfo.get(i));
      }
      state.println("");
      if (filter.getFilterType().contains("bandpass")) {
        state.println("bandpass: "
                + filter.getHPcorner() + " " + filter.getLPcorner() + " " + filter.getNpoles());
      }
      if (filter.getFilterType().contains("highpass")) {
        state.println("highpass: "
                + filter.getHPcorner() + " " + filter.getLPcorner() + " " + filter.getNpoles());
      }
      if (filter.getFilterType().contains("lowpass")) {
        state.println("lowpass: "
                + filter.getHPcorner() + " " + filter.getLPcorner() + " " + filter.getNpoles());
      }

      state.println("sample_rate: " + stationinfo.getRate());
      state.println("start_stop: " + starttime + " " + stoptime);
      state.println("acquisition_parameters: " + waittimeinMillis / 1000 + " "
              + stepintervalinMillis / 1000 + " " + realtimeintervalinMillis / 1000);

      state.println("inputcwb: " + inputcwbip + " " + inputcwbport);

      state.println("detectionthreshold_parameters: " + detectionthreshold + " "
              + noisewindowlengthinsec + " " + detectionthresholdscalefactor + " "
              + detectionthresholdtype);

      state.println("template_parameters: " + blockLength + " "
              + (float) templateLength / (float) stationinfo.getRate()
              + " " + (float) averagingLength / (float) stationinfo.getRate()
              + " " + templates.getPreEvent());
      state.println("output_path: " + outputpath);

      state.println("station_coordinates: " + Util.df25(stationinfo.getLatitude())
              + " " + Util.df25(stationinfo.getLongitude()) + " " + Util.df21(stationinfo.getElevation()));
      state.println("centroid_location: " + Util.df25(centroidlat) + " " + Util.df25(centroidlon) + " " + Util.df21(centroiddep));
      state.println("source_receiver_distance: " + Util.df25(srcrcvdist));
      state.println("radial_distance: " + maxdist);

      String chan = "";
      for (int i = 0; i < stationinfo.getCompleteChannelList().length; i++) {
        String[] channels = stationinfo.getCompleteChannelList();
        chan = chan + channels[i] + " ";
      }
      state.println("channels: " + chan);
      //String loc = "..";
      if (stationinfo.getLocation() != null) {
        String loc = "" + stationinfo.getLocation();
        if (loc.trim().length() == 0) {
          loc = "--";
        }
        state.println("locationcode: " + loc);
      }

      for (int i = 0; i < templatenames.size(); i++) {
        state.print("waveform_templates: " + templatenames.get(i) + "\n");
      }

      String out = "output_files:";
      if (ccresults == true) {
        out = out + " ccresults";
      }
      if (ccresults != true) {
        out = out + " noccresults";
      }
      state.println(out);
    }
  }

  public void computeEQCentroidLocation(SubspaceTemplatesCollection t) {

    float mlat = 0;
    float mlon = 0;
    float mdep = 0;
    int ntemplates = t.EQLoc().size();

    for (int i = 0; i < ntemplates; i++) {
      String loc = t.EQLoc().get(i);
      String[] tmp = loc.split("\\s+");
      mlat = mlat + Float.parseFloat(tmp[2]);
      mlon = mlon + Float.parseFloat(tmp[3]);
      mdep = mdep + Float.parseFloat(tmp[4]);
    }
    centroidlat = mlat / ntemplates;
    centroidlon = mlon / ntemplates;
    centroiddep = mdep / ntemplates;
  }

  public void computeEQCentroidLocation(float clat, float clon, float cdep) {
    centroidlat = clat;
    centroidlon = clon;
    centroiddep = cdep;
  }

  public float surfacewaveTT(float distance) {
    float velocity = 2.2f;
    if (distance >= 30 && distance < 50) {
      velocity = (float) 2.4;
    }
    if (distance >= 50 && distance < 80) {
      velocity = (float) 2.6;
    }
    if (distance >= 80) {
      velocity = (float) 3.0;
    }
    float t = (float) (distance / velocity);
    return t;
  }

  public void writeTemplates(float[] data, int sps, int nch, int ndet) throws FileNotFoundException, IOException {

    int nsamples = data.length / nch;
    float[] temp = new float[nsamples];
    float delta = (float) (1.0 / sps);

    for (int ich = 0; ich < nch; ich++) {

      System.arraycopy(data, ich * nsamples, temp, 0, nsamples);
      SACFileWriter writernn = new SACFileWriter(outputpath + "template" + ndet + "_" + ich + ".sac");
      writernn.getHeader().nzyear = 1970;
      writernn.getHeader().nzjday = 1;
      writernn.getHeader().nzhour = 1;
      writernn.getHeader().nzmin = 1;
      writernn.getHeader().nzsec = 0;
      writernn.getHeader().nzmsec = 0;
      writernn.getHeader().delta = (float) (1.0 / (float) sps);
      writernn.getHeader().b = 0;
      writernn.getHeader().knetwk = "TT";
      writernn.getHeader().kstnm = "TEMP";
      writernn.getHeader().khole = "00";
      if (ich == 0) {
        writernn.getHeader().kcmpnm = "HH0";
      }
      if (ich == 1) {
        writernn.getHeader().kcmpnm = "HH1";
      }
      if (ich == 2) {
        writernn.getHeader().kcmpnm = "HH2";
      }
      writernn.writeFloatArray(temp);
      writernn.close();

      if (!operationalOutput) {
        try (PrintStream out4 = new PrintStream(outputpath + "template" + ndet + "_" + ich + ".dat")) {
          out4.print("TEXT X=15, Y=90, F=TIMES-BOLD, H=3.5, C=BLACK T=\""
                  + stationinfo.getNetwork() + stationinfo.getStname() + " ("
                  + filter.getHPcorner() + "-" + filter.getLPcorner() + " Hz) "
                  + Util.ascdatetime2(current).substring(0, 16) + "\"\n");
          out4.print("VARIABLES=Time(sec),Amp\n");
          for (int i = 0; i < nsamples; i++) {
            out4.print(i * delta + " " + temp[i] + "\n");
          }
        }
      }
    }

    if (!operationalOutput) {
      try (PrintStream out4 = new PrintStream(outputpath + "ctemplate" + ndet + ".dat")) {
        out4.print("TEXT X=15, Y=90, F=TIMES-BOLD, H=3.5, C=BLACK T=\""
                + stationinfo.getNetwork() + stationinfo.getStname() + " ("
                + filter.getHPcorner() + "-" + filter.getLPcorner() + " Hz) "
                + Util.ascdatetime2(current).substring(0, 16) + "\"\n");
        out4.print("VARIABLES=Time(sec),Amp\n");
        temp = new float[nch * nsamples];
        System.arraycopy(data, 0, temp, 0, nch * nsamples);
        float max = Math.abs(temp[0]);
        for (int i = 1; i < temp.length; i++) {
          if (max < Math.abs(temp[i])) {
            max = Math.abs(temp[i]);
          }
        }
        int j = 0;
        for (int i = 0; i < temp.length; i++) {
          temp[i] = temp[i] / max;
        }
        for (int ich = 0; ich < nch; ich++) {
          out4.print("Zone T=\"" + stationinfo.getChannels()[ich] + "\", I=" + nsamples + "\n");
          for (int i = 0; i < nsamples; i++) {
            out4.print(j * delta + " " + temp[ich * nsamples + i] + "\n");
            j++;
          }
          j = j + (int) (0.10 * 1 / delta);
        }
      }
    }
  }

  public static void main(String[] args) throws FileNotFoundException, IOException, InterruptedException {
    if (args.length == 0) {
      Util.prt("Usage : SubspacePreprocess configFile ");
      System.exit(0);
    }
    SubspacePreprocess pre = new SubspacePreprocess(args[0], false);  // Create a Subspace Preprocessor
    pre.setConfiguration(args[0]);        // read the configuration files
    pre.cleanUpFiles();                    // delete .sac and .dat files 
    pre.buildTemplates();                 // create the template per the user config file, create new config
  }
}
