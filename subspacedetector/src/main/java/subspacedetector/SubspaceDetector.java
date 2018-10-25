/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package subspacedetector;

import com.oregondsp.signalProcessing.filter.iir.Butterworth;
import com.oregondsp.signalProcessing.filter.iir.PassbandType;
import detection.usgs.LabeledDetectionStatistic;
import gov.usgs.anss.edge.EdgeProperties;
import gov.usgs.anss.util.Util;
//import gov.usgs.cwbquery.QueryRing;
import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.GregorianCalendar;
import gov.usgs.cwbquery.QueryRing;
import java.text.SimpleDateFormat;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 *
 * @author benz
 * @author wyeck
 * @author jnealy
 */
public final class SubspaceDetector {

  private GregorianCalendar pt;
  private GregorianCalendar endt;
  private FilterCollection filter;

  private QueryRing ring0 = null;                       // 1st of three rings, if single channel only one ring is initialized
  private QueryRing ring1 = null;                       // 2st of three rings, initialized if 3-channel processing
  private QueryRing ring2 = null;                       // 2st of three rings, initialized if 3-channel processing

  private final GregorianCalendar current = new GregorianCalendar();
  private final GregorianCalendar endtimeofprocessing = new GregorianCalendar();
  private final SimpleDateFormat folderformat = new SimpleDateFormat("yyyy_MM_dd_HH_mm_ss/");
  private final GregorianCalendar starttmp = new GregorianCalendar(); // temp used in queryData

  public void prt(String s) {
    Util.prt(s);
  }

  public void prta(String s) {
    Util.prta(s);
  }

  public SubspaceDetector() throws FileNotFoundException, IOException, InterruptedException {
  }
  // This needs better error checking

  private void zeroArray(float[] array, int index1, int index2) {
    if (index2 > array.length) {
      prta("index2 out of range");
    } else {
      for (int i = index1; i < index2; i++) {
        array[i] = 0;
      }
    }
  }

  public float[] queryData(String nscl, int sps, GregorianCalendar starttime,
          int nlength, String cwbip, int cwbport) throws IOException {

    float bufferlength = (nlength + 1) / sps;
    String date = Util.ascdatetime2(starttime).toString();
    int onesmapleinMillis = 1000 / sps;
    starttmp.setTimeInMillis(starttime.getTimeInMillis() - onesmapleinMillis);

    QueryRing ring = new QueryRing(cwbip, cwbport, nscl, starttmp,
            2 * bufferlength, bufferlength, sps, null);
    int npts = nlength;
    float[] data = new float[npts];
    int nret = ring.getDataAt(starttime, npts, data, false);

    if (nret == 0) {
      return null;
    } else {
      return data;
    }
  }

  public float[] getDetails(Config cfg, StationCollection station, GregorianCalendar phasetime,
          float[][] refdata, boolean debug) throws IOException, InterruptedException {

    String foldername = folderformat.format(phasetime.getTime());
    float SNR = 0;
    float orig_SNR = 0;
    String[] channels = station.getChannels();
    int buffLengthSeconds = 60; //pre
    int buffLengthEndSeconds = 10; //post
    int bufferLenghthSamples = (int) (buffLengthSeconds * station.getRate()); // buffer around template
    int bufferLenghthEndSamples = (int) (buffLengthEndSeconds * station.getRate()); // buffer around template
    int nret = 0;
    //Try setting a new value equal to phasetime for use below
    //StringBuilder pt = Util.ascdatetime2(phasetime);
    pt = new GregorianCalendar();
    pt.setTimeInMillis(phasetime.getTimeInMillis());
    pt.add(Calendar.SECOND, -1 * buffLengthSeconds);
    int samples = bufferLenghthSamples + cfg.getTemplateLength() + bufferLenghthEndSamples;
    endt = new GregorianCalendar();
    endt.setTimeInMillis(phasetime.getTimeInMillis() + buffLengthEndSeconds * 1000);

    if ((System.currentTimeMillis()) < endt.getTimeInMillis() + 2000) {
      TimeUnit.SECONDS.sleep(buffLengthEndSeconds);
    }

    float[] time = new float[samples];
    for (int i = 0; i < samples; i++) {
      time[i] = i * (1.f / station.getRate());
    }
    float[] maxes = new float[channels.length];
    float energynum = 0;
    float energydenom = 0;

    for (int i = 0; i < channels.length; i++) {
      String nscl = "" + station.getNetwork() + station.getStname() + channels[i] + station.getLocation().replaceAll(" ", "-");

      //JN edit: new method to get timeseries
      float[] timeseries = new float[samples];
      int seglen = timeseries.length;
      if (i == 0) {
        nret = ring0.getDataAt(pt, seglen, timeseries, false);
      } else if (i == 1) {
        nret = ring1.getDataAt(pt, seglen, timeseries, false);
      } else if (i == 2) {
        nret = ring2.getDataAt(pt, seglen, timeseries, false);
      }

      if (nret != seglen || timeseries == null) {
        System.out.println("Event was in/too close to gap! nscl: " + nscl + " phasetime: " + pt + " samples: "
                + samples + "\n");
        float[] returns = new float[2];
        returns[0] = -99f;
        returns[1] = -99f;
        return returns;
      }

      filterData(timeseries, cfg.getHPCorner(), cfg.getLPCorner(), cfg.getNpoles(), station.getRate(), cfg.getFilterType());
      float[] noise = Arrays.copyOfRange(timeseries, (int) 25 * station.getRate(), (int) 55 * station.getRate());
      float[] noiseTime = Arrays.copyOfRange(time, (int) 25 * station.getRate(), (int) 55 * station.getRate());
      float[] event = Arrays.copyOfRange(timeseries, (int) 60 * station.getRate(), 60 * station.getRate() + cfg.getTemplateLength());
      float[] eventTime = Arrays.copyOfRange(time, (int) 60 * station.getRate(), 60 * station.getRate() + cfg.getTemplateLength());
      maxes[i] = findMaxValue(event);
      SNR = SNR + getSNR(noise, event);
      //Done JN edit

      for (int j = 0; j < event.length; j++) {
        energynum += Math.abs(event[j]) * Math.abs(refdata[i][j]);
        energydenom += refdata[i][j] * refdata[i][j];
      }

      if (debug) // print out waveforms
      {
        new File(cfg.getOutputPath() + foldername).mkdirs();

        String filenameAll = "all_" + channels[i] + ".dat";
        PrintStream all = new PrintStream(cfg.getOutputPath() + foldername + filenameAll);
        for (int j = 0; j < timeseries.length; j++) {
          all.print(time[j]);
          all.print(" ");
          all.println(timeseries[j]);
        }
        all.close();

        String filenameevent = "event_" + channels[i] + ".dat";
        PrintStream eventf = new PrintStream(cfg.getOutputPath() + foldername + filenameevent);
        for (int j = 0; j < event.length; j++) {
          eventf.print(eventTime[j]);
          eventf.print(" ");
          eventf.println(event[j]);
        }
        eventf.close();

        String filenamenoise = "noise_" + channels[i] + ".dat";
        PrintStream noisef = new PrintStream(cfg.getOutputPath() + foldername + filenamenoise);
        for (int j = 0; j < noise.length; j++) {
          noisef.print(noiseTime[j]);
          noisef.print(" ");
          noisef.println(noise[j]);
        }

        noisef.close();

      }
    }

    SNR = SNR / channels.length;
    double magref = (double) cfg.getReferenceMagnitude() + Math.log10(energynum / energydenom);

    if (debug == true) {
      PrintStream dets = new PrintStream(cfg.getOutputPath() + foldername + "Details.dat");
      dets.print(SNR);
      for (int i = 0; i < channels.length; i++) {
        dets.print(" ");
        dets.print(maxes[i]);
      }
      dets.close();
    }
    float[] returns = new float[2];
    returns[0] = SNR;
    returns[1] = (float) magref;
    return returns;

  }

  public float[][] getReference(Config cfg, StationCollection station, GregorianCalendar phasetime,
          boolean debug) throws IOException {

    String[] channels = station.getChannels();
    int buffLengthSeconds = 60;
    int bufferLenghthSamples = (int) (buffLengthSeconds * station.getRate()); // åbuffer around template
    //StringBuilder pt = Util.ascdatetime2(phasetime);
    pt = new GregorianCalendar();
    pt.setTimeInMillis(phasetime.getTimeInMillis());
    pt.add(Calendar.SECOND, -1 * buffLengthSeconds);
    int samples = 2 * bufferLenghthSamples + cfg.getTemplateLength();
    float[] time = new float[samples];
    for (int i = 0; i < samples; i++) {
      time[i] = i * (1.f / station.getRate());
    }

    float[][] eventdata = new float[channels.length][cfg.getTemplateLength()];

    for (int i = 0; i < channels.length; i++) {
      String nscl = "" + station.getNetwork() + station.getStname() + channels[i] + station.getLocation().replaceAll(" ", "-");
      float[] timeseries = queryData(nscl, station.getRate(), pt, samples, cfg.getCWBIP(), cfg.getCWBport());
      filterData(timeseries, cfg.getHPCorner(), cfg.getLPCorner(), cfg.getNpoles(), station.getRate(), cfg.getFilterType());
      float[] event = Arrays.copyOfRange(timeseries, (int) 60 * station.getRate(), 60 * station.getRate() + cfg.getTemplateLength());
      eventdata[i] = event;
    }
    return eventdata;
  }

  public float getSNR(float[] noise, float[] data) {
    float n = 0;
    for (int i = 0; i < noise.length; i++) {
      n = n + (noise[i] * noise[i]);
    }
    n = n / noise.length;
    n = (float) Math.sqrt(n);

    float d = 0;
    for (int i = 0; i < data.length; i++) {
      d = d + (data[i] * data[i]);
    }
    d = d / data.length;
    d = (float) Math.sqrt(d);

    return d / n;
  }

  public static void filterData(float[] data, float hp, float lp, int npoles, int sps, String filtertype) {

    float delta = (float) (1.0 / sps);
    Butterworth F = null;
    //
    // The Butterworth has to be initialized for each seismogram
    //  to get the coefficients correct
    //
    if (filtertype.contains("highpass")) {
      F = new Butterworth(npoles, PassbandType.HIGHPASS,
              hp, lp, delta);
    }
    if (filtertype.contains("lowpass")) {
      F = new Butterworth(npoles, PassbandType.LOWPASS,
              hp, lp, delta);
    }
    if (filtertype.contains("bandpass")) {
      F = new Butterworth(npoles, PassbandType.BANDPASS,
              hp, lp, delta);
    }
    if(F != null) F.filter(data);
  }

  public boolean getnextDataSegmentandFilter(float[][] data) throws IOException {

    int nret;

    boolean gotALLdata0 = true;
    boolean gotALLdata1 = true;
    boolean gotALLdata2 = true;

    int nchan = data.length;
    int seglen = data[0].length;

    if (nchan >= 1) {

      nret = ring0.getDataAt(current, seglen, data[0], false);
      if (nret == seglen) {
        filter.f0.filter(data[0]);
      } else {
        gotALLdata0 = false;
      }
    }
    if (nchan >= 2) {
      nret = ring1.getDataAt(current, seglen, data[1], false);
      if (nret == seglen) {
        filter.f1.filter(data[1]);
      } else {
        gotALLdata1 = false;
      }
    }
    if (nchan == 3) {
      nret = ring2.getDataAt(current, seglen, data[2], false);
      if (nret == seglen) {
        filter.f2.filter(data[2]);
      } else {
        gotALLdata2 = false;
      }
    }
    if (gotALLdata0 == false || gotALLdata1 == false || gotALLdata2 == false) {
      return false;
    } else {
      return true;
    }
  }

  public float computeCPF(float[] data) {
    Arrays.sort(data);
    float p = (float) (1.0 / (float) data.length);
    float mean = 0;
    float[] cpf = new float[data.length];
    cpf[0] = p;
    mean = p;
    for (int i = 1; i < data.length; i++) {
      cpf[i] = p + cpf[i - 1];
      mean = mean + cpf[i];
    }
    mean = mean / data.length;
    return mean;
  }

  public float computeMedianAbsoluteDeviation(float[] data) {
    Arrays.sort(data);
    float median = 0;
    if (data.length % 2 == 0) {
      float a = data[(data.length - 1) / 2];
      float b = data[data.length / 2];
      median = (a + b) / 2;
    } else {//if the array is odd
      median = data[data.length / 2];
    }
    for (int i = 0; i < data.length; i++) {
      data[i] = Math.abs(data[i] - median);
    }
    Arrays.sort(data);
    if (data.length % 2 == 0) {//if the array is even
      float a = data[(data.length - 1) / 2];
      float b = data[data.length / 2];
      median = (a + b) / 2;
    } else {//if the array is odd
      median = data[data.length / 2];
    }
    return median;
  }

  public float computeAverage(float[] array) {
    float sum = 0;
    for (int i = 0; i < array.length; i++) {
      sum = sum + array[i];
    }

    float average = sum / array.length;
    return average;
  }

  //JN edit (2015-10-14): compute stdv
  public float computeStdv(float[] array, float mean) {
    float sq_total = 0;

    for (int i = 0; i < array.length; i++) {
      sq_total = (float) (sq_total + java.lang.Math.pow((mean - array[i]), 2));
    }

    float stdv = (float) java.lang.Math.pow((sq_total / array.length), 0.5);
    return stdv;
  }

  public float[] computeMedianInfo(float[] maxVs) {
    Arrays.sort(maxVs);
    float[] returnvals = new float[2];
    float median = 0;
    int middle = ((maxVs.length) / 2);

    if (maxVs.length % 2 == 0) {
      float meda = maxVs[middle];
      float medb = maxVs[middle - 1];
      median = (meda + medb) / 2;
    } else {//if the array is odd
      median = maxVs[middle];
    }

    returnvals[0] = median;

    for (int i = 0; i < maxVs.length; i++) {
      maxVs[i] = Math.abs(maxVs[i] - median);
    }

    Arrays.sort(maxVs);
    if (maxVs.length % 2 == 0) {//if the array is even
      float meda = maxVs[middle];
      float medb = maxVs[middle - 1];
      median = (meda + medb) / 2;
    } else {//if the array is odd
      median = maxVs[middle];
    }

    returnvals[1] = median;

    return returnvals;
  }

  //JN edit (2015-10-14): Fisher z-transformation and z-score calculation
  public float[] FisherzScore(float[] array, Outputter z_out, int stat_info, Outputter fz_out) {
    //array contains an array of cross-correlation (cc) values
    float[] z_scores = new float[array.length]; //this will store the z-scores
    float[] FisherZ = new float[array.length]; //this will store the Fisher transformed cc values

    //Do Fisher z-transform
    for (int i = 0; i < array.length; i++) {
      //System.out.println("***Fisher z-transform - CC value: " + array[i] + "\n");
      FisherZ[i] = (float) (0.5 * java.lang.Math.log((1 + array[i]) / (1 - array[i])));
      //System.out.println("***Transformed CC value: " + FisherZ[i] + "\n");
    }

    //Print out Fisher z-transform values
    if (fz_out != null) {
      fz_out.writeDetectionStatistics(FisherZ, 0, current, stat_info);
    }
    //Find mean and stdv
    float mean = computeAverage(FisherZ);
    float stdv = computeStdv(FisherZ, mean);

    //Calculate z-score
    for (int i = 0; i < array.length; i++) {
      z_scores[i] = java.lang.Math.abs((FisherZ[i] - mean) / stdv);
    }

    //Print out Z-score values 
    if (z_out != null) {
      z_out.writeDetectionStatistics(z_scores, 0, current, stat_info);
    }
    return z_scores;
  }

  /**
   * This method run the cross-correlation by initiating the QueryRing class for accessing data,
   * initiating the filtering of the time-series, checking for data gaps, and waiting for new data
   * to arrive when appropriate
   *
   * @param cfg
   * @return Number of detections
   * @throws java.io.FileNotFoundException
   * @throws InterruptedException
   */
  public int run(Config cfg) throws FileNotFoundException, InterruptedException {

    // Initialize the beginning time of processing
    // Initialize the ending time of processing
    GregorianCalendar nexttime = new GregorianCalendar();
    Random rn = new Random();
    current.setTimeInMillis(Util.stringToDate2(cfg.getStartTime()).getTime());
    endtimeofprocessing.setTimeInMillis(Util.stringToDate2(cfg.getStopTime()).getTime());

    int detections = 0;

    float[][] refdata = new float[cfg.getNumberofChannels()][cfg.getTemplateLength()];

    try {
      refdata = getReference(cfg, cfg.getStatInfo(), cfg.getReferenceArrivalTime(), false);
    } catch (IOException e) {
      prta("IOException in Subspace e=" + e);
      e.printStackTrace();
    }

    GregorianCalendar detectionThresholdStartTime = new GregorianCalendar();
    detectionThresholdStartTime.setTimeInMillis(Util.stringToDate2(cfg.getStartTime()).getTime());
    detectionThresholdStartTime.add(Calendar.MILLISECOND, (int) (-1000.));

    PrintStream pf = null;
    if (cfg.grabdates()) {
      pf = new PrintStream(new FileOutputStream(cfg.getOutputPath() + "DatesGrabed.txt", true));

    }
    // Setup the rings needs for each channel that will be processed
    StringBuilder nsclsb = new StringBuilder(12);
    String nscl;
    for (int ich = 0; ich < cfg.getNumberofChannels(); ich++) {
      if (cfg.getStatInfo().getLocation().trim().length() == 0) {
        nscl = cfg.getStatInfo().getNetwork() + cfg.getStatInfo().getStname()
                + cfg.getStatInfo().getChannels()[ich];
      } else {
        nscl = cfg.getStatInfo().getNetwork() + cfg.getStatInfo().getStname()
                + cfg.getStatInfo().getChannels()[ich] + cfg.getStatInfo().getLocation();
      }
      if (ich == 0) {
        ring0 = new QueryRing(cfg.getCWBIP(), cfg.getCWBport(), nscl, current,
                10 * cfg.getDataLength() / (float) cfg.getStatInfo().getRate(),
                cfg.getDataLength() / (float) cfg.getStatInfo().getRate(),
                cfg.getStatInfo().getRate(), null);
        ring0.setDebug(false);
      } else if (ich == 1) {
        ring1 = new QueryRing(cfg.getCWBIP(), cfg.getCWBport(), nscl, current,
                10 * cfg.getDataLength() / (float) cfg.getStatInfo().getRate(),
                cfg.getDataLength() / (float) cfg.getStatInfo().getRate(),
                cfg.getStatInfo().getRate(), null);
        ring1.setDebug(false);
      } else if (ich == 2) {
        ring2 = new QueryRing(cfg.getCWBIP(), cfg.getCWBport(), nscl, current,
                10 * cfg.getDataLength() / (float) cfg.getStatInfo().getRate(),
                cfg.getDataLength() / (float) cfg.getStatInfo().getRate(),
                cfg.getStatInfo().getRate(), null);
        ring2.setDebug(false);
      }
    }

    //int nchan = cfg.getStatInfo().getNumberofChannels();
    filter = new FilterCollection();
    filter.setup(cfg.getHPCorner(), cfg.getLPCorner(), cfg.getNpoles(), cfg.getStatInfo().getRate(),
            cfg.getFilterType(), cfg.getStatInfo().getNumberofChannels());

    DetectionSummary detsum = new DetectionSummary();

    try {

      int segmentDurationinMillis = (int) ((cfg.getDataLength() / (float) cfg.getStatInfo().getRate()) * 1000);
      float[][] segment = new float[cfg.getStatInfo().getNumberofChannels()][cfg.getDataLength()];      // This array contains the data from the Query request   
      float[] stack = new float[cfg.getDataLength() + cfg.getTemplateLength()];   // Array containing the cross-correlation results
      float[] formatted_z = new float[cfg.getDataLength() + cfg.getTemplateLength()];

      ArrayList< LabeledDetectionStatistic> dstats = new ArrayList< LabeledDetectionStatistic>(cfg.getDetectors().getNumberOfTemplates());

      ArrayList< LabeledDetectionStatistic> rstats = null;
      if (cfg.getDetectionThresholdType().matches("all") || cfg.getDetectionThresholdType().matches("mad")) {
        rstats = new ArrayList< LabeledDetectionStatistic>(cfg.getRDetectors().getNumberOfTemplates()); // Test random phase tempalte
      }

      // Used in the pickFinder method
      StringBuilder sb = new StringBuilder(1000);
      int[] mad_picks = new int[stack.length];
      int[] constant_picks = new int[stack.length];
      int[] z_picks = new int[stack.length];

      Outputter sumf = null;             //Debug
      Outputter sumf_vary = null;
      Outputter sumf_const = null;
      Outputter sumf_z = null;

      Outputter rccout = null;           //Debug
      Outputter ccout = null;
      Outputter fz_out = null;
      Outputter z_out = null;

      PrintStream rout = new PrintStream(cfg.getOutputPath() + "mad_threshold.out");            //Debug 

      int julianday = 0;
      int jday = 0;

      nscl = formNSCLstring(cfg.getStatInfo().getNetwork(), cfg.getStatInfo().getStname(),
              cfg.getStatInfo().getPhaseLabelComponent(), cfg.getStatInfo().getLocation());

      boolean gotALLdata = true;
      //JN Note: this historic number should be in the config file as an option rather then hardcoded as 4hrs
      //int historicNumber = (int) Math.round(4.*60.*60./cfg.getNoiseWindowLengthinSec()); //number of values to save (segements per day)
      int historicNumber = (int) Math.round(cfg.getHistoricNumberHours() * 60. * 60. / cfg.getNoiseWindowLengthinSec());
      float[] maxes = new float[historicNumber]; // circular buffer storing maxes
      float[] maxessorted = new float[historicNumber]; // with circular buffer code, need backup arrays for sorting data

      int dsegmentLength = (int) (cfg.getNoiseWindowLengthinSec() * cfg.getStatInfo().getRate());
      int nrblocks = dsegmentLength / cfg.getDataLength();
      if (nrblocks == 0) {
        nrblocks = 1;
      }
      int nb = 0;
      int nmaxes = 0; // To see if array is filled up
      float[] mad = new float[2];
      int maxesring = historicNumber - 1;
      float max = 0;
      float tempmax = 0;
      float ncc_max = 0;
      float curr_mad = 0;

      JSONCorrelationGenerator jsonGenerator = null;
      int sequence = 0;
      if (cfg.getJsonOutput() == true) {
        jsonGenerator = new JSONCorrelationGenerator(cfg.getJsonPath(), cfg.getBrokerConfig(), null);
      }

      for (;;) {

        if (current.getTimeInMillis() > endtimeofprocessing.getTimeInMillis()) {
          break;    // DONE! 
        }
        int doy = current.get(GregorianCalendar.DAY_OF_YEAR);
        int year = current.get(GregorianCalendar.YEAR);
        int month = current.get(GregorianCalendar.MONTH) + 1;
        int dayofmonth = current.get(GregorianCalendar.DAY_OF_MONTH);

        if (doy > julianday) {
          julianday = doy;
        }

        // Everything in the debug bracket gets tossed in an operational system
        if (cfg.getCCresults() == true) {

          if (doy != jday) {
            String stat = cfg.getStatInfo().getStname();

            String syear = Integer.toString(year);
            String sdoy = Util.leftPad(Integer.toString(doy), 3).toString().replaceAll(" ", "0");
            String styrjday = stat.trim() + "_" + syear + "_" + sdoy;

            ccout = new Outputter();
            ccout.openFile(cfg.getOutputPath() + styrjday + "ccout.dat");
            ccout.writeStringtoFile("VARIABLES=Time(sec),Variance");
            if (cfg.getDetectionThresholdType().matches("all") || cfg.getDetectionThresholdType().matches("mad")) {
              rccout = new Outputter();
              rccout.openFile(cfg.getOutputPath() + styrjday + "rccout.dat");
              rccout.writeStringtoFile("VARIABLES=Time(sec),Variance");
            }
            if (cfg.getDetectionThresholdType().matches("all") || cfg.getDetectionThresholdType().matches("z_score")) {
              z_out = new Outputter();
              z_out.openFile(cfg.getOutputPath() + styrjday + "z_out.dat");
              z_out.writeStringtoFile("VARIABLES=Time(sec),Z-score values");

              fz_out = new Outputter();
              fz_out.openFile(cfg.getOutputPath() + styrjday + "_fz_out.dat");
              fz_out.writeStringtoFile("VARIABLES=Time(sec),Fisher z-transform value");
            }
          }
        }
        if (doy != jday) {
          String stat = cfg.getStatInfo().getStname();

          String syear = Integer.toString(year);
          String sdoy = Util.leftPad(Integer.toString(doy), 3).toString().replaceAll(" ", "0");
          String styrjday = stat.trim() + "_" + syear + "_" + sdoy;

          sumf = new Outputter();
          sumf_const = new Outputter();
          sumf_z = new Outputter();

          if (cfg.getDetectionThresholdType().matches("all") || cfg.getDetectionThresholdType().matches("mad")) {
            if (cfg.append()) {
              sumf.openFileAppend(cfg.getOutputPath() + styrjday + "mad_summary.dat");
            } else {
              sumf.openFile(cfg.getOutputPath() + styrjday + "mad_summary.dat");
            }
          }

          if (cfg.getDetectionThresholdType().matches("all") || cfg.getDetectionThresholdType().matches("constant")) {
            if (cfg.append()) {
              sumf_const.openFileAppend(cfg.getOutputPath() + styrjday + "const_thresh_summary.dat");
            } else {
              sumf_const.openFile(cfg.getOutputPath() + styrjday + "const_thresh_summary.dat");
            }
          }

          if (cfg.getDetectionThresholdType().matches("all") || cfg.getDetectionThresholdType().matches("z_score")) {
            if (cfg.append()) {
              sumf_z.openFileAppend(cfg.getOutputPath() + styrjday + "z_score_summary.dat");
            } else {
              sumf_z.openFile(cfg.getOutputPath() + styrjday + "z_score_summary.dat");
            }
          }

        }

        if (doy != jday) {
          jday = current.get(GregorianCalendar.DAY_OF_YEAR);
        }

        try {

          for (;;) {

            gotALLdata = getnextDataSegmentandFilter(segment);

            if (gotALLdata == false) {
              break;
            }

            if (cfg.grabdates()) {
              nexttime.setTimeInMillis((long) (current.getTimeInMillis() + (1. / cfg.getStatInfo().getRate()) * 1000 * cfg.getDataLength()));
              pf.print(Util.ascdatetime2(current) + " " + Util.ascdatetime2(nexttime) + "\n");

            }

            dstats = cfg.getDetectors().evaluateDetectionStatistics(segment);  //David Harris code, nothing to be rewritten
            //JN edit 2015: get rstats
            if (cfg.getDetectionThresholdType().matches("all") || cfg.getDetectionThresholdType().matches("mad")) {
              rstats = cfg.getRDetectors().evaluateDetectionStatistics(segment);
            }

            System.arraycopy(dstats.get(0).getDetectionStatistic(), 0,
                    stack, cfg.getTemplateLength(), cfg.getDataLength());

            //JN edit (2015-10-15): Do Fisher z-transform
            float[] post_z = new float[dstats.get(0).getDetectionStatistic().length];
            if (cfg.getDetectionThresholdType().matches("z_score") || cfg.getDetectionThresholdType().matches("all")) {
              post_z = FisherzScore(dstats.get(0).getDetectionStatistic(), z_out, cfg.getStatInfo().getRate(), fz_out);
              System.arraycopy(post_z, 0, formatted_z, cfg.getTemplateLength(), cfg.getDataLength());
            }

            if (cfg.getCCresults() == true) {
              ccout.writeDetectionStatistics(dstats.get(0).getDetectionStatistic(),
                      0, current, cfg.getStatInfo().getRate());
              if (cfg.getDetectionThresholdType().matches("all") || cfg.getDetectionThresholdType().matches("mad")) {
                rccout.writeDetectionStatistics(rstats.get(0).getDetectionStatistic(),
                        0, current, cfg.getStatInfo().getRate());
              }
            }

            if (cfg.getDetectionThresholdType().matches("mad") || cfg.getDetectionThresholdType().matches("all")) {

              tempmax = findMaxValueNoAbs(rstats.get(0).getDetectionStatistic());
              if (tempmax > max) {
                max = tempmax;
              }

              if (nb == nrblocks) {
                nb = 0;
                if (nmaxes == historicNumber) {
                  maxes[maxesring] = max;
                  maxesring++;
                  if (maxesring == historicNumber) {
                    maxesring = 0;
                  }
                  maxessorted = maxes.clone();
                  mad = computeMedianInfo(maxessorted);
                  curr_mad = mad[0] * cfg.getDetectionThresholdScaleFactor();
                  int cyear = current.get(GregorianCalendar.YEAR);
                  int cmonth = current.get(GregorianCalendar.MONTH) + 1;
                  int cdayofmonth = current.get(GregorianCalendar.DAY_OF_MONTH);
                  GregorianCalendar bday = new GregorianCalendar();
                  String bb = cyear + "/" + cmonth + "/" + cdayofmonth + " 00:00";
                  bday.setTimeInMillis(Util.stringToDate2(bb).getTime());
                  float firstpointintime = (float) ((float) (current.getTimeInMillis() - bday.getTimeInMillis()) / 1000.0);
                  rout.println(firstpointintime + " " + Float.toString(curr_mad));
                } else if (nmaxes < historicNumber) {
                  maxes[nmaxes] = max;
                  nmaxes++;
                }
                max = 0;
              }
              nb++;

            }

            float z_thresh = 6; //currently hardcoded - eventually will be in config

            if (cfg.getDetectionThresholdType().matches("z_score") || cfg.getDetectionThresholdType().matches("all")) {
              int zpick = PeakFinder.findPicks(formatted_z, cfg.getTemplateLength(),
                      cfg.getDataLength(), cfg.getAveragingLength(), z_thresh, z_picks, sb);

              System.arraycopy(formatted_z, formatted_z.length - cfg.getTemplateLength(),
                      post_z, 0, cfg.getTemplateLength());
              for (int i = 0; i < zpick; i++) {

                StringBuilder ct = Util.ascdatetime(current);
                detsum.summary(cfg, current, z_picks[i] - cfg.getTemplateLength());
                if (detsum.Complete() == true) {
                  System.out.println("Found Event");
                  float[] values = getDetails(cfg, cfg.getStatInfo(), detsum.PhaseTime(), refdata, false);

                  if (sumf_z != null && values[0] > cfg.getSNRCutoff()) {
                    sumf_z.writeSummary(detsum, cfg,
                            nscl.substring(0, 2).trim(), nscl.substring(2, 7).trim(),
                            nscl.substring(7, 10).trim(), nscl.substring(10, 12),
                            formatted_z[z_picks[i]], z_thresh, values[0], values[1]);
                    detections = detections + 1;
                    if (cfg.getJsonOutput() == true) {
                      sequence = sequence + 1;
                      jsonGenerator.writeCorrelation(cfg.getAgency(), cfg.getAuthor(), detsum.PhaseTime(), nscl,
                              sequence, cfg.getReferenceLatitude(), cfg.getReferenceLongitude(), cfg.getReferenceDepth(), detsum.OriginTime(),
                              detsum.Phase(), cfg.getEventType(), stack[z_picks[i]], z_thresh, values[1], values[0], 0, cfg.getDetectionThresholdType(),
                              0, 0, 0, 0);

                    }
                  }
                }
              }
            }

            if (cfg.getDetectionThresholdType().matches("mad") || cfg.getDetectionThresholdType().matches("all")) {
              int npick = PeakFinder.findPicks(stack, cfg.getTemplateLength(),
                      cfg.getDataLength(), cfg.getAveragingLength(), curr_mad, mad_picks, sb);
              for (int i = 0; i < npick; i++) {

                StringBuilder ct = Util.ascdatetime(current);
                detsum.summary(cfg, current, mad_picks[i] - cfg.getTemplateLength());
                if (detsum.Complete() == true) {
                  System.out.println("Found Event, Calculating SNR");
                  float[] values = getDetails(cfg, cfg.getStatInfo(), detsum.PhaseTime(), refdata, false);

                  if (sumf != null && values[0] > cfg.getSNRCutoff()) {
                    sumf.writeSummary(detsum, cfg,
                            nscl.substring(0, 2).trim(), nscl.substring(2, 7).trim(),
                            nscl.substring(7, 10).trim(), nscl.substring(10, 12),
                            stack[mad_picks[i]], curr_mad, values[0], values[1]);
                    detections = detections + 1;
                    if (cfg.getJsonOutput() == true) {
                      sequence = sequence + 1;
                      jsonGenerator.writeCorrelation(cfg.getAgency(), cfg.getAuthor(), detsum.PhaseTime(), nscl,
                              sequence, cfg.getReferenceLatitude(), cfg.getReferenceLongitude(), cfg.getReferenceDepth(), detsum.OriginTime(),
                              detsum.Phase(), cfg.getEventType(), stack[mad_picks[i]], curr_mad, values[1], values[0], 0, cfg.getDetectionThresholdType(),
                              0, 0, 0, 0);

                    }
                  }
                }
              }
            }

            if (cfg.getDetectionThresholdType().matches("constant")) {
              //JN edit: for constant threshold
              //float const_threshold = (float) 0.30;
              int cpick = PeakFinder.findPicks(stack, cfg.getTemplateLength(),
                      cfg.getDataLength(), cfg.getAveragingLength(), cfg.getDetectionThreshold(), 
                      constant_picks, sb);

              //JN edit: for constant thresh results
              for (int i = 0; i < cpick; i++) {

                StringBuilder ct = Util.ascdatetime(current);
                detsum.summary(cfg, current, constant_picks[i] - cfg.getTemplateLength());
                if (detsum.Complete() == true) {
                  System.out.println("Found Event, Calculating SNR");
                  float[] values = getDetails(cfg, cfg.getStatInfo(), detsum.PhaseTime(), refdata, false);

                  if (sumf != null && values[0] > cfg.getSNRCutoff()) {
                    sumf_const.writeSummary(detsum, cfg,
                            nscl.substring(0, 2).trim(), nscl.substring(2, 7).trim(),
                            nscl.substring(7, 10).trim(), nscl.substring(10, 12),
                            stack[constant_picks[i]], cfg.getDetectionThreshold(), values[0], values[1]);
                    detections = detections + 1;
                    if (cfg.getJsonOutput() == true) {
                      sequence = rn.nextInt(999999999);
                      jsonGenerator.writeCorrelation(cfg.getAgency(), cfg.getAuthor(), detsum.PhaseTime(), nscl,
                              sequence, cfg.getReferenceLatitude(), cfg.getReferenceLongitude(), cfg.getReferenceDepth(), detsum.OriginTime(),
                              detsum.Phase(), cfg.getEventType(), stack[constant_picks[i]], cfg.getDetectionThreshold(), values[1], values[0], 0, cfg.getDetectionThresholdType(),
                              0, 0, 0, 0);

                    }

                  }
                }
              }
            }

            System.arraycopy(stack, stack.length - cfg.getTemplateLength(), stack, 0, cfg.getTemplateLength());

            break;
          }

        } catch (IOException e) {
          Util.prta("IOEXception with getDataAT(): " + e + " " + Util.ascdatetime2(current));
          Util.sleep(5000);
        }

        if (gotALLdata == false) {

          if ((System.currentTimeMillis() - current.getTimeInMillis()) < cfg.getRealTimeIntervalinMillis()) {
            try {
              Thread.sleep(cfg.getWaitTimeinMillis());
            } catch (InterruptedException e) {
            }

            prta("Waiting for Data:" + Util.ascdate(current) + " " + Util.asctime2(current) + " "
                    + +cfg.getDataLength() + " no. of channels: " + cfg.getStatInfo().getChannels().length);

            continue;
          } else {

            if (cfg.getCCresults() == true) {

              GregorianCalendar bday = new GregorianCalendar();
              String bb = year + "/" + month + "/" + dayofmonth + " 00:00";
              bday.setTimeInMillis(Util.stringToDate2(bb).getTime());
              float firstpointintime = (current.getTimeInMillis() - bday.getTimeInMillis()) / 1000;

              int npts = (int) ((cfg.getStepIntervalinMillis() / 1000) * cfg.getStatInfo().getRate());

              for (int i = 0; i < npts; i++) {
                ccout.getPrintStream().printf("%13.4f %1.8f\n", firstpointintime + i / (float) cfg.getStatInfo().getRate(), 0f);
              }
            }

            current.add(Calendar.MILLISECOND, cfg.getStepIntervalinMillis());
            prta("Advancing Time:" + Util.ascdate(current) + " " + Util.asctime2(current));

            if (current.getTimeInMillis() > endtimeofprocessing.getTimeInMillis()) {
              break;    // DONE!
            }
            //
            // There is a gap, so time is advanced to find more data.
            // Consequently, must reinitialize the filter and overlap-and-add seciton
            // of the cross-correlation
            // 
            filter.initialize();
            cfg.getDetectors().initialize();

            //JN edit (2015-10-08): changed empirical to noisecc, mad, and all
            if (cfg.getDetectionThresholdType().matches("mad") || cfg.getDetectionThresholdType().matches("all")) {
              cfg.getRDetectors().initialize();
            }

            if (cfg.getDetectionThresholdType().matches("mad") || cfg.getDetectionThresholdType().matches("all")) {
              zeroArray(stack, 0, cfg.getTemplateLength());  //Erase memory for PeakFinderV3  
            }
            if (cfg.getDetectionThresholdType().matches("z_score") || cfg.getDetectionThresholdType().matches("all")) {
              zeroArray(formatted_z, 0, cfg.getTemplateLength());  //Erase memory for PeakFinderV3  
            }

            continue;
          }
        }

        // Increment time
        current.add(Calendar.MILLISECOND, segmentDurationinMillis);
        if (current.getTimeInMillis() > endtimeofprocessing.getTimeInMillis()) {
          break;    // DONE!        
        }
      }

    } catch (IOException e) {
      prta("IOException in Subspace e=" + e);
      e.printStackTrace();
    }

    return detections;
  }

  public String formNSCLstring(String net, String sta, String comp, String loc) {

    String nscl;
    for (int j = net.length(); j < 2; j++) {
      net = net + " ";
    }
    for (int j = sta.length(); j < 5; j++) {
      sta = sta + " ";
    }
    for (int j = comp.length(); j < 3; j++) {
      comp = comp + " ";
    }
    for (int j = loc.length(); j < 2; j++) {
      loc = loc + " ";
    }
    loc = loc.replaceAll(" ", ".");
    nscl = net + sta + comp + loc;
    return nscl;

  }
  // Sort data from smallest to largest

  public static void bubble_srt(float array[]) {
    int n = array.length;
    int k;
    for (int m = n; m >= 0; m--) {
      for (int i = 0; i < n - 1; i++) {
        k = i + 1;
        if (array[i] > array[k]) {
          swapNumbers(i, k, array);
        }
      }
    }
  }

  private static void swapNumbers(int i, int j, float[] array) {
    float temp;
    temp = array[i];
    array[i] = array[j];
    array[j] = temp;
  }

  public float findMaxValue(float[] d) {
    float max = Math.abs(d[0]);
    for (int i = 1; i < d.length; i++) {
      if (Math.abs(d[i]) > max) {
        max = Math.abs(d[i]);
      }
    }
    return max;
  }

  public float findMaxValueNoAbs(float[] d) {
    float max = d[0];
    for (int i = 1; i < d.length; i++) {
      if (d[i] > max) {
        max = d[i];
      }
    }
    return max;
  }

  public static void main(String[] args) throws FileNotFoundException, IOException, InterruptedException {
    Util.setModeGMT();
    EdgeProperties.init();
    Util.setNoconsole(false);
    Util.setNoInteractive(true);
    Config cfg = new Config(args[0], null);
    SubspaceDetector ssd = new SubspaceDetector();
    ssd.run(cfg);
  }
}
