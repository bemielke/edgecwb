/*
 *  This software is in the public domain because it contains materials 
 *  that originally came from the United States Geological Survey, 
 *  an agency of the United States Department of Interior. For more 
 *  information, see the official USGS copyright policy at 
 *  http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.subspaceprocess;

import Jama.Matrix;
import Jama.SingularValueDecomposition;
import com.oregondsp.io.SACFileWriter;
//import com.oregondsp.io.SACInputStream;
import com.oregondsp.signalProcessing.filter.iir.Butterworth;
import com.oregondsp.signalProcessing.filter.iir.PassbandType;
import gov.usgs.anss.util.Util;
import gov.usgs.anss.edgethread.EdgeThread;
import gov.usgs.cwbquery.QueryRing;
import gov.usgs.anss.util.Distaz;
//import java.io.BufferedReader;
//import java.io.FileNotFoundException;
//import java.io.FileReader;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.TreeMap;

/**
 *
 * @author hbenz This java class is intended to contain all of the waveforms associated with a
 * detection. It aggregates together the waveforms form which we can compute the eigenvectors and
 * eigenvalues that define the subspace of waveforms.  It is used only the the SubspacePreprocess
 * class to hold stuff.  To make it reusable, a clear() method was added.
 * <br>
 * Aug 2017 DCK - pass to put into better coding standards form for variable names, dangling open
 * braces, 2 space tabs
 * Sep 2017 DCK - Eliminate use of special Distaz and use the standard one.
 * Nov 2017 DCK - make many objects final, allow resuse via initialize, comment all apparently dead code.
 */
public final class SubspaceTemplatesCollection {
  // Temporary templates and one for each trigger and is nchan*templateLength long
  private final ArrayList<float[]> temporarytemplates = new ArrayList<float[]>(1);
  private final ArrayList<float[]> detectortemplates = new ArrayList<float[]>(1);
  private final ArrayList<String> eqloc = new ArrayList<>(1);
  private final static TreeMap<String, QueryRing> rings = new TreeMap();  // One QueryRing for each channel
  
  // parameters set in initialize()
  private String station;
  private String network;
  private String location;
  private String [] channels;
  private float hpc;
  private float lpc;
  private int npoles;
  private String filtertype;
  private int templateLength;
  private float preEvent;
  private int rate;  
  //
  private int refindex;
  private float completeness;       // set to 0.95 in initialize()
  private int optimalnumber;

  //DCK:private float[] referencetemplates;     // This does not look to be used anymore
  //DCK:private float[] referenceamplitudes;    // This does not look to be used anymore
  private float [][] refdata;             // reference data 60 second before etc for mag calc
  //DCKprivate float referencedotproduct;
  
  // These come from the reference phase (first phase in list)
  private float referencemagnitude;
  private String referencemagtype;          // Set to 'm' in initialize
  private float referencelatitude;
  private float referencelongitude;
  private float referencedepth;
  private String referencephaselabel;
  private String referencephaselabelcomp;
  private String referencelocation;
  static private String outputpath = "./";
  private final GregorianCalendar referenceot = new GregorianCalendar();
  private final GregorianCalendar referencearrivaltime = new GregorianCalendar();
  
  // Other variables
  private boolean initialtime = true;
  
  private EdgeThread par;
  public void prt(String s) {if(par == null) Util.prt(s); else par.prt(s);}
  public void prta(String s) {if(par == null) Util.prta(s); else par.prta(s);}

  @Override
  public String toString() {
    return network+station+" "+channels[0]+location+" rt="+rate+" tlen="+templateLength+" refmag="+referencemagnitude+referencemagtype+
            " #dettmp="+detectortemplates.size();
  }
  public ArrayList<String> EQLoc() {
    return eqloc;
  }

  public ArrayList<float[]> TemporaryDetectorTemplates() {
    return temporarytemplates;
  }

  public ArrayList<float[]> DetectorTemplates() {
    return detectortemplates;
  }
  /** add an output path for putting all files 
   * 
   * @param s The path with trailing slash
   */
  public void setOutputpath(String s) {
    outputpath = s;
  }
  public boolean getInitialTime() {
    return initialtime;
  }

  public int getOptimalNumber() {
    return optimalnumber;
  }

  public float getCompleteness() {
    return completeness;
  }

  public String getStation() {
    return station;
  }

  public String getNetwork() {
    return network;
  }

  public String getLocation() {
    return location;
  }

  public float getPreEvent() {
    return preEvent;
  }

  public String getFilterType() {
    return filtertype;
  }

  public float getHPCorner() {
    return hpc;
  }

  public float getLPCorner() {
    return lpc;
  }

  public int getNpoles() {
    return npoles;
  }

  public int getRate() {
    return rate;
  }

  public int getReferenceIndex() {
    return refindex;
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

  /*DCKpublic float getReferenceDotProduct() {
    return referencedotproduct;
  }*/

  /*DCKpublic float[] getReferenceAmplitudes() {
    return referenceamplitudes;
  }*/

  public String getReferenceMagType() {
    return referencemagtype;
  }

  public String getReferencePhaseLabel() {
    return referencephaselabel;
  }

  public String getReferencePhaseLabelComp() {
    return referencephaselabelcomp;
  }

  public String getReferenceLocation() {
    return referencelocation;
  }

  public GregorianCalendar getReferenceOT() {
    return referenceot;
  }

  public GregorianCalendar getReferenceArrivalTime() {
    return referencearrivaltime;
  }

  /*DCK:public float[] getReferenceTemplates() {
    return referencetemplates;
  }*/
  
  public float[][] getReferenceData() {
    return refdata;
  }
  

  public int getNumberOfTemplates() {
    return detectortemplates.size();
  }

  public int getNumberOfChannels() {
    return channels.length;
  }

  public int getTemplateLength() {
    return templateLength;
  }

  public String[] getChannels() {
    return channels;
  }

  public SubspaceTemplatesCollection(EdgeThread parent) {
    par = parent;
  }
  /** Initialize the variables for this set of templates
   * 
   * @param stat Station collection has all of the information about the station
   * @param filter The FilterCollection has informatino about the filtering (hpc, lpc, npoles, type)
   * @param pre The pre-event memory to fetch in seconds
   * @param tlen The length of the templates in samples
   */
  public void initialize(StationCollection stat, FilterCollection filter,
          float pre, int tlen) {

    station = "" + stat.getStname();
    network = "" + stat.getNetwork();
    location = "" + stat.getLocation();
    channels = new String[stat.getNumberofChannels()];
    channels = stat.getChannels();
    rate = stat.getRate();
    preEvent = pre;
    templateLength = tlen;

    hpc = filter.getHPcorner();
    lpc = filter.getLPcorner();
    npoles = filter.getNpoles();
    filtertype = filter.getFilterType();
    initialize();
  }

  public void initialize(String stat, String net, String loc, String[] chan,
          float hp, float lp, int np, String ft, int sps,
          float pre, int tlen) {
    station = "" + stat;
    network = "" + net;
    location = "" + loc;
    channels = new String[chan.length];
    for (int i = 0; i < chan.length; i++) {
      channels[i] = "" + chan[i];
    }
    hpc = hp;
    lpc = lp;
    npoles = np;
    templateLength = tlen;
    filtertype = "" + ft;
    rate = sps;
    preEvent = pre;
    initialize();
  }
  private void initialize() {
    detectortemplates.clear();
    temporarytemplates.clear();
    eqloc.clear();  
    completeness = (float) 0.95;    
    optimalnumber = 0;
    referencemagtype = "m";
    referencemagnitude = (float) 0.;
    referencelatitude = (float) 0.;
    referencelongitude = (float) 0.;
    referencedepth = (float) 0.;
    referencephaselabel = null;
    referencephaselabelcomp = null;
    referencelocation = null;
    refdata = null;
  }
  /** DCK this does not appear to be used
   */
  /*public void readTemplatesConfig(String configfilename) throws FileNotFoundException, IOException {

    try (BufferedReader in = new BufferedReader(new FileReader(configfilename))) {
      String line;
      float[] t = null;

      while ((line = in.readLine()) != null) {
        if (line.trim().length() == 0) {
          break;
        }
        if (!"#".equals(line.substring(0, 1))) {
          String[] sacfiles = line.split("\\s+");
          int nchannels = sacfiles.length;
          for (int i = 0; i < nchannels; i++) {
            SACInputStream sac = new SACInputStream(sacfiles[i]);
            int npts = sac.header.npts;
            if (i == 0) {
              t = new float[nchannels * npts];
            }
            float[] data = new float[sac.header.npts];
            sac.readData(data);
            System.arraycopy(data, 0, t, i * npts, npts);
          }
          detectortemplates.add(t);
        }
      }

      while ((line = in.readLine()) != null) {
        if (line.contains("stationparams")) {
          String[] sp = line.split("\\s+");
          network = sp[1];
          station = sp[2];
          location = sp[3];
        }
        if (line.contains("channels")) {
          String[] sp = line.split("\\s+");
          channels = new String[sp.length - 1];
          for (int i = 0; i < channels.length; i++) {
            channels[i] = sp[i + 1];
          }
        }
        if (line.contains("filterparams")) {
          String[] sp = line.split("\\s+");
          filtertype = sp[1];
          hpc = Float.parseFloat(sp[2]);
          lpc = Float.parseFloat(sp[3]);
          npoles = Integer.parseInt(sp[4]);
        }
        if (line.contains("templateparams")) {
          String[] sp = line.split("\\s+");
          templateLength = Integer.parseInt(sp[1]);
          rate = Integer.parseInt(sp[2]);
          preEvent = Float.parseFloat(sp[3]);
        }
        if (line.contains("eigenparams")) {
          String[] sp = line.split("\\s+");
          completeness = Float.parseFloat(sp[1]);
          optimalnumber = Integer.parseInt(sp[2]);
        }
        if (line.contains("referencelocation")) {
          String[] sp = line.split("\\s+");
          referencelocation = line;
          String yrmnday = sp[1];
          String hrminsec = sp[2];
          referenceot = new GregorianCalendar();
          referenceot.setTimeInMillis(Util.stringToDate2(yrmnday + " " + hrminsec).getTime());
          referencelatitude = Float.parseFloat(sp[3]);
          referencelongitude = Float.parseFloat(sp[4]);
          referencedepth = Float.parseFloat(sp[5]);
          referencemagnitude = Float.parseFloat(sp[6]);
        }
        if (line.contains("referencetime")) {
          String[] sp = line.split("\\s+");
          referencelocation = line;
          String yrmnday = sp[3];
          String hrminsec = sp[4];
          referencephaselabel = sp[2];
          referencearrivaltime = new GregorianCalendar();
          referencearrivaltime.setTimeInMillis(Util.stringToDate2(yrmnday + " " + hrminsec).getTime());
        }
        if (line.contains("referenceamplitudes")) {
          String[] sp = line.split("\\s+");
          int n = 0;
          for (int i = 1; i <= sp.length - 1; i++) {
            if (!"#".equals(sp[i].substring(0, 1))) {
              n++;
            }
          }
          referenceamplitudes = new float[n];
          for (int i = 1; i <= sp.length - 1; i++) {
            if (!"#".equals(sp[i].substring(0, 1))) {
              referenceamplitudes[i - 1] = Float.parseFloat(sp[i]);
            }
          }
        }
      }
    }
  }*/

  /*public void writeTemplatesConfig() throws FileNotFoundException, IOException {

    try (PrintStream out = new PrintStream("templates.cfg")) {
      GregorianCalendar bt = new GregorianCalendar();
      bt.setTimeInMillis(referencearrivaltime.getTimeInMillis());
      bt.add(Calendar.MILLISECOND, (int) (-1000. * preEvent));
      String nt = network.trim();
      for (int i = network.length(); i < 2; i++) {
        nt = nt + " ";
      }
      String stat = "" + station.trim();
      for (int i = station.trim().length(); i < 5; i++) {
        stat = stat + " ";
      }
      String loc = "" + location.trim();
      for (int i = location.trim().length(); i < 2; i++) {
        loc = loc + " ";
      }
      for (int i = 0; i < detectortemplates.size(); i++) {
        float[] t = new float[detectortemplates.get(0).length];
        System.arraycopy(detectortemplates.get(i), 0, t, 0, detectortemplates.get(i).length);
        int npts = detectortemplates.get(i).length / channels.length;
        for (int j = 0; j < channels.length; j++) {
          String filename = outputpath + nt + stat + channels[j] + loc + "template" + i + ".sac";
          filename = filename.replaceAll(" ", "_");
          out.print(filename + " ");
          SACFileWriter writernn = new SACFileWriter(filename);
          writernn.getHeader().nzyear = bt.get(GregorianCalendar.YEAR);
          writernn.getHeader().nzjday = bt.get(GregorianCalendar.DAY_OF_YEAR);
          writernn.getHeader().nzhour = bt.get(GregorianCalendar.HOUR_OF_DAY);
          writernn.getHeader().nzmin = bt.get(GregorianCalendar.MINUTE);
          writernn.getHeader().nzsec = bt.get(GregorianCalendar.SECOND);
          writernn.getHeader().nzmsec = bt.get(GregorianCalendar.MILLISECOND);
          writernn.getHeader().delta = (float) (1.0 / (float) rate);
          writernn.getHeader().b = 0;
          writernn.getHeader().knetwk = network.trim();
          writernn.getHeader().kstnm = station.trim();
          writernn.getHeader().khole = location.trim();
          writernn.getHeader().kcmpnm = channels[j];
          float[] d = new float[npts];
          System.arraycopy(t, j * npts, d, 0, npts);
          writernn.writeFloatArray(d);
          writernn.close();
        }
        out.print("\n");
      }
      out.print("\n");
      for (int i = location.trim().length(); i < 2; i++) {
        location = location + ".";
      }
      out.println("stationparams: " + network + " " + station + " " + location);
      out.print("channels: ");
      for (String channel : channels) {
        out.print(channel + " ");
      }
      out.print("\n");
      out.println("filterparams: " + filtertype + " " + hpc + " " + lpc + " " + npoles);
      out.println("templateparams: " + templateLength + " " + rate + " " + preEvent);
      out.println("eigenparams: " + completeness + " " + optimalnumber);
      String refot = Util.ascdatetime2(referenceot).toString();
      out.println("referencelocation: " + refot + " " + referencelatitude + " " + referencelongitude
              + " " + referencedepth + " " + referencemagnitude);
      String phasetime = Util.ascdatetime2(referencearrivaltime).toString();
      String chan = "" + referencephaselabelcomp.trim();
      String nscl = nt + stat + chan + loc;
      out.println("referencetime: " + nscl + " " + referencephaselabel + " " + phasetime);
      out.print("referenceamplitudes: ");
      for (int i = 0; i < referenceamplitudes.length; i++) {
        out.print(referenceamplitudes[i] + " ");
      }
      out.print("\n");
      //ArrayList<float []> temporarytemplates;
      //ArrayList<float []> detectortemplates;
    }

  }*/
  /**
   * 
   * @param phasetime Time of the phase for the reference
   * @param cwbip Where to get the data
   * @param cwbport and its port
   * @param debug flag for more output?
   * @return
   * @throws IOException 
   */
  private float[][] getReferenceWF( GregorianCalendar phasetime,String cwbip, int cwbport, boolean debug) 
          throws IOException {
    int buffLengthSeconds = 120;
    int bufferLengthSamples = (int) (buffLengthSeconds * getRate()); // åbuffer around template
    //StringBuilder pt = Util.ascdatetime2(phasetime);
    GregorianCalendar pt = new GregorianCalendar();
    pt.setTimeInMillis(phasetime.getTimeInMillis());
    pt.add(Calendar.SECOND, -buffLengthSeconds);
    int samples = 2 * bufferLengthSamples + templateLength;
    /*DCK -this does not appear to be used?
    float[] time = new float[samples];
    for (int i = 0; i < samples; i++) {
      time[i] = i * (1.f / getRate());
    }*/

    float[][] eventdata = new float[channels.length][samples];

    for (int i = 0; i < channels.length; i++) {
      String nscl = "" + getNetwork() + getStation() + channels[i] + getLocation().replaceAll(" ", "-");
      float[] timeseries = queryData(nscl, getRate(), pt, samples, cwbip, cwbport);
      if(timeseries != null) {
        //filterData(timeseries,cfg.HPCorner(),cfg.LPCorner(),cfg.Npoles(),station.getRate(),cfg.FilterType());   
        float[] event = Arrays.copyOfRange(timeseries, (int) 60 * getRate(), 60 * getRate() + templateLength);
        eventdata[i] = event;
      }
      else {
        prta("** Timeseries null in SubspaceProcess getReferenceWF() "+nscl+" "+Util.ascdatetime2(pt)+" "+samples+" "+cwbip+"/"+cwbport);
      }
    }
    return eventdata;
  }
  /** Why open a new query ring and then leave it dangling?  Should be a query ring per channels?
   * perhaps they are never reused in the same time interval?
   * 
   * @param nscl
   * @param sps
   * @param starttime
   * @param nlength
   * @param cwbip
   * @param cwbport
   * @return
   * @throws IOException 
   */
  private float[] queryData(String nscl, int sps, GregorianCalendar starttime,
          int nlength, String cwbip, int cwbport) throws IOException {
    float bufferlength = (nlength + 1) / sps;
    String date = Util.ascdatetime2(starttime).toString();
    GregorianCalendar starttmp = new GregorianCalendar();
    starttmp.setTimeInMillis(starttime.getTimeInMillis() - 1000 / sps); // start time less one sample
    QueryRing ring = rings.get(nscl);

    if(ring == null) {
      ring = new QueryRing(cwbip, cwbport, nscl, starttmp,
            3 * bufferlength, bufferlength, sps, par);
      rings.put(nscl, ring);
    }
      ring.getQuerySpan().resetSpan(starttime.getTimeInMillis() - 10000);   
    //QueryRing ring = new QueryRing(cwbip,cwbport,nscl,starttime,
    //                2*bufferlength,bufferlength);
    //ring.setRate(sps);
    int npts = nlength;
    float[] data = new float[npts];
    int nret;
    synchronized(ring) {
      nret = ring.getDataAt(starttime, npts, data, false);
    }
    if (nret == 0) {
      return null;
    } else {
      return data;
    }
  }
  /** DCK This does not appear to be used 
   * 
   * @param refphasedata
   * @param refdata
   * @param nchan
   * @throws IOException 
   */
  /*public void initializeReferenceEQ(String refphasedata, float[] refdata, int nchan) throws IOException {
    String[] eq = refphasedata.trim().split("\\s+");
    GregorianCalendar ot = new GregorianCalendar();
    ot.setTimeInMillis(Util.stringToDate2(eq[0] + " " + eq[1]).getTime());

    GregorianCalendar phasetime = new GregorianCalendar();
    phasetime.setTimeInMillis(Util.stringToDate2(eq[12] + " " + eq[13]).getTime());

    referenceot = new GregorianCalendar();
    referenceot.setTimeInMillis(ot.getTimeInMillis());
    referencemagnitude = Float.parseFloat(eq[5]);
    referencemagtype = eq[6];
    refindex = 0;
    referencelatitude = Float.parseFloat(eq[2]);
    referencelongitude = Float.parseFloat(eq[3]);
    referencedepth = Float.parseFloat(eq[4]);
    referencephaselabel = "" + eq[11];
    referencephaselabelcomp = "" + eq[9];
    referencelocation = "" + refphasedata.trim();
    if (referencearrivaltime == null) {
      referencearrivaltime = new GregorianCalendar();
    }
    referencearrivaltime.setTimeInMillis(phasetime.getTimeInMillis());

    referencetemplates = new float[refdata.length];
    computeReferenceAmplitudes(refdata, nchan);
    referencedotproduct = computeDotProduct(refdata);
    System.arraycopy(refdata, 0, referencetemplates, 0, refdata.length);
  }*/

  /** Used by SubspacePreprocess
   * 
  * Get waveform templates from Query.
  * This is done at start-up or when a new event is located and potentially
  *  added to the detection
   * @param eqphasedata A phase line from the configuratin
   * @param ref Is this the reference event?
   * @param maxdistance Skip any where this distance from the reference location is found
   * @param cwbip Where to get the data
   * @param cwbport and its port
   * @param outputwaveforms If true, write out SAC files with the data
   * @param shift The amount to shift this waveform in seconds ( that is start the waveform this much before usual start)
   * @throws java.io.IOException 
  */
  public void getTemplates(String outputpath, String eqphasedata,  boolean ref, float maxdistance,
          String cwbip, int cwbport, boolean outputwaveforms, float shift) throws IOException {

    String[] eq = eqphasedata.trim().split("\\s+");
    GregorianCalendar ot = new GregorianCalendar();
    ot.setTimeInMillis(Util.stringToDate2(eq[0] + " " + eq[1]).getTime());

    GregorianCalendar phasetime = new GregorianCalendar();
    phasetime.setTimeInMillis(Util.stringToDate2(eq[12] + " " + eq[13]).getTime());

    if (ref == true) {
      //DCKreferenceot = new GregorianCalendar();
      referenceot.setTimeInMillis(ot.getTimeInMillis());
      referencemagnitude = Float.parseFloat(eq[5]);
      referencemagtype = eq[6];
      refindex = 0;
      referencelatitude = Float.parseFloat(eq[2]);
      referencelongitude = Float.parseFloat(eq[3]);
      referencedepth = Float.parseFloat(eq[4]);
      referencephaselabel = "" + eq[11];
      referencephaselabelcomp = "" + eq[9];
      referencelocation = "" + eqphasedata.trim();
      /*DCKif (referencearrivaltime == null) {
        referencearrivaltime = new GregorianCalendar();
      }*/
      referencearrivaltime.setTimeInMillis(phasetime.getTimeInMillis());
      refdata = getReferenceWF(referencearrivaltime, cwbip, cwbport, false);
    }

    float elat = Float.parseFloat(eq[2]);
    float elon = Float.parseFloat(eq[3]);
    //Distaz dd = new Distaz();
    //dd.computeDistaz(referencelatitude, referencelongitude, elat, elon);
    double [] answer = new double[4];
    Distaz.distaz(referencelatitude, referencelongitude, elat, elon, answer);

    //if (dd.getDist() <= maxdistance) {
    if (answer[0] <= maxdistance) {
      GregorianCalendar start = new GregorianCalendar();
      start.setTimeInMillis(phasetime.getTimeInMillis());
      start.add(Calendar.MILLISECOND, -(int) (1000 * 1.5 * templateLength / rate));
      String ph = Util.ascdatetime2(phasetime).toString();
      String st = Util.ascdatetime2(start).toString();
      float tt = (float) ((phasetime.getTimeInMillis() - start.getTimeInMillis()) / 1000.0);
      tt = (tt - Math.abs(preEvent)) * rate;
      int firstsample = Math.round(tt);

      GregorianCalendar bt = new GregorianCalendar();
      bt.setTimeInMillis(phasetime.getTimeInMillis());
      bt.add(Calendar.MILLISECOND, (int) (preEvent * 1000));

      float[] t = new float[channels.length * templateLength];

      int numberofchannels = 0;

      //Add shift
      if(shift != 0) {
        start.add(Calendar.MILLISECOND, -(int) (1000 * shift));
      }

      for (int i = 0; i < channels.length; i++) {
        String nscl = "" + network + station + channels[i] + location.replaceAll(" ", "-");

        // Query more data than the required template length because of the need
        // to filter data and then cut out the required portion, which is 
        float[] ss = queryData(nscl, rate, start, 4 * templateLength, cwbip, cwbport);

        if (ss != null) {
          filterData(ss, hpc, lpc, npoles, rate, filtertype);
          
          
          // Harley wants the filtered data from the time of the starting sample
          String cr = Util.ascdatetime2(bt).toString();
          String ts = cr.substring(0, 4) + cr.substring(5, 7) + cr.substring(8, 10);
          ts = ts + "_" + cr.substring(11, 13) + cr.substring(14, 16) + cr.substring(17, 19);

          float delta = (float) (1.0 / rate);

          SACFileWriter writernn = new SACFileWriter(outputpath + nscl.replaceAll(" ","_") + "_" + ts + ".sac");
          writernn.getHeader().nzyear = bt.get(GregorianCalendar.YEAR);
          writernn.getHeader().nzjday = bt.get(GregorianCalendar.DAY_OF_YEAR);
          writernn.getHeader().nzhour = bt.get(GregorianCalendar.HOUR_OF_DAY);
          writernn.getHeader().nzmin = bt.get(GregorianCalendar.MINUTE);
          writernn.getHeader().nzsec = bt.get(GregorianCalendar.SECOND);
          writernn.getHeader().nzmsec = bt.get(GregorianCalendar.MILLISECOND);
          writernn.getHeader().delta = delta;
          writernn.getHeader().b = 0;
          writernn.getHeader().knetwk = "TS";
          writernn.getHeader().khole = "00";
          writernn.getHeader().kstnm = "" + station.trim();
          writernn.getHeader().kcmpnm = channels[i].trim();
          float [] d = new float[templateLength];
          System.arraycopy(ss, firstsample, d, 0, templateLength);
          writernn.writeFloatArray(d);
          writernn.close();
          if (outputwaveforms == true) {            
            writeTemplates2(ss, station, channels[i], rate, bt);       //Debug stuff, but harley wanted these for comparison so always output them
          }
          // save data into t starting at start of waveform (skipping preevent for filtering)
          System.arraycopy(ss, firstsample, t, i * templateLength, templateLength);
          numberofchannels++;
        }
      }

      if (numberofchannels == channels.length) {
        if (outputwaveforms == true) {
          writeTemplates(t, station, channels, rate, bt);    //Debug stuff
        }
        normalizeTemplates(t);          // divide by sqrt(energy) all components
        temporarytemplates.add(t);
        /*DCKif (eqloc == null) {
          eqloc = new ArrayList<String>(1);
        }*/
        eqloc.add(eqphasedata);
        prta("getTemplates(): Got template starting at " + Util.ascdatetime2(start) + " "+ eqphasedata);
      } else {
        prta("getTemplates(): Could not acquire data, skipped template starting at " + Util.ascdatetime2(start) + eqphasedata);
      }
    }
  }

  /**
   * Same as get template, but instead of adding to template collection, just
  * returns the multichannel array
  * Each component of an event is strung into a single array for convenience
   * @param eqphasedata An event record from the configuration file
   * @param ref Is this the reference event?
   * @param cwbip CWB with the data
   * @param cwbport and its port
   * @param outputwaveforms If true, write out SAC files with the data templates
   * @param alignbuffer If non-zero, allow this many seconds of extra data into the buffers
   * @return The single dimensioned stacked array of data
   * @throws java.io.IOException 
  */
  private float[] getTemplateData(String eqphasedata, boolean ref,
          String cwbip, int cwbport, boolean outputwaveforms, float alignbuffer) throws IOException {

    String[] eq = eqphasedata.trim().split("\\s+");
    GregorianCalendar ot = new GregorianCalendar();
    ot.setTimeInMillis(Util.stringToDate2(eq[0] + " " + eq[1]).getTime());

    GregorianCalendar phasetime = new GregorianCalendar();
    phasetime.setTimeInMillis(Util.stringToDate2(eq[12] + " " + eq[13]).getTime());

    if (ref == true) {
      //DCKreferenceot = new GregorianCalendar();
      referenceot.setTimeInMillis(ot.getTimeInMillis());
      referencemagnitude = Float.parseFloat(eq[5]);
      referencemagtype = eq[6];
      refindex = 0;
      referencelatitude = Float.parseFloat(eq[2]);
      referencelongitude = Float.parseFloat(eq[3]);
      referencedepth = Float.parseFloat(eq[4]);
      referencephaselabel = "" + eq[11];
      referencephaselabelcomp = "" + eq[9];
      referencelocation = "" + eqphasedata.trim();
      /**DCKif (referencearrivaltime == null) {
        referencearrivaltime = new GregorianCalendar();
      }*/
      referencearrivaltime.setTimeInMillis(phasetime.getTimeInMillis());
    }

    // DCK: this distance calculation is not used??
    //float elat = Float.parseFloat(eq[2]);
    //float elon = Float.parseFloat(eq[3]);
    //Distaz dd = new Distaz();
    //dd.computeDistaz(referencelatitude, referencelongitude, elat, elon);

    GregorianCalendar start = new GregorianCalendar();
    start.setTimeInMillis(phasetime.getTimeInMillis());
    start.add(Calendar.MILLISECOND, -(int) (1000 * 1.5 * templateLength / rate));
    String ph = Util.ascdatetime2(phasetime).toString();
    String st = Util.ascdatetime2(start).toString();
    float tt = (float) ((phasetime.getTimeInMillis() - start.getTimeInMillis()) / 1000.0);
    tt = (tt - Math.abs(preEvent)) * rate;
    int firstsample = Math.round(tt);

    /*DCKif (temporarytemplates == null) {
      temporarytemplates = new ArrayList<float[]>(1);
    }*/
    int buffersamples = (int) alignbuffer * rate;
    GregorianCalendar bt = new GregorianCalendar();
    bt.setTimeInMillis(phasetime.getTimeInMillis());

    float[] t = new float[channels.length * templateLength + buffersamples * channels.length * 2];

    int numberofchannels = 0;

    for (int i = 0; i < channels.length; i++) {
      String nscl = "" + network + station + channels[i] + location.replaceAll(" ", "-");
      System.out.println(cwbip + " " + cwbport);
      // Query more data than the required template length because of the need
      // to filter data and then cut out the required portion, which is 
      float[] ss = queryData(nscl, rate, start, 4 * templateLength, cwbip, cwbport);


      if (ss != null) {
        filterData(ss, hpc, lpc, npoles, rate, filtertype);
        System.arraycopy(ss, firstsample - buffersamples, t, i * templateLength + (i * 2 * buffersamples), templateLength + 2 * buffersamples);
        numberofchannels++;
      }
    }

    if (numberofchannels == channels.length) {
      normalizeTemplates(t);
    } else {
      System.out.println("Could not acquire data, skipped template starting at " + Util.ascdatetime2(start) + "\n");
    }
    return t;
  }
  
  /** this appears to not be used except in routine that does not appear to be used
   * 
   * @param ss
   * @return 
   */
  /*public float computeDotProduct(float[] ss) {
    float dot = 0;
    for (int i = 0; i < ss.length; i++) {
      dot = dot + ss[i] * ss[i];
    }
    return dot;
  }*/
  /** DCK - this is onlyused in a routine that does not appear to be used anymore.
   * 
   * @param ss
   * @param nch 
   */
  /*public void computeReferenceAmplitudes(float[] ss, int nch) {

    referenceamplitudes = new float[nch];
    int tlen = ss.length / nch;

    for (int i = 0; i < channels.length; i++) {
      int s = i * tlen;
      float maxamp = Math.abs(ss[s]);
      for (int j = 1; j < tlen; j++) {
        if (Math.abs(ss[s + j]) > maxamp) {
          maxamp = Math.abs(ss[s + j]);
        }
      }
      referenceamplitudes[i] = maxamp;
    }
  }*/
  /** Filter the given data with a new filter constructed from the other arguments
   * 
   * @param data Array of data to filter
   * @param hp high pass corner
   * @param lp Low pass corner
   * @param npoles Number of poles
   * @param sps Data rate in hertz
   * @param filtertype filter type "lowpass", "highpass", "bandpass"
   */
  public static void filterData(float[] data, float hp, float lp, int npoles, int sps, String filtertype) {

    float delta = (float) (1.0 / sps);
    Butterworth filter = null;
    //
    // The Butterworth has to be initialized for each seismogram
    //  to get the coefficients correct
    //
    if (filtertype.contains("highpass")) {
      filter = new Butterworth(npoles, PassbandType.HIGHPASS,
              hp, lp, delta);
    } else if (filtertype.contains("lowpass")) {
      filter = new Butterworth(npoles, PassbandType.LOWPASS,
              hp, lp, delta);
    } else if (filtertype.contains("bandpass")) {
      filter = new Butterworth(npoles, PassbandType.BANDPASS,
              hp, lp, delta);
    }
    if (filter == null) {
      System.out.println("**** Filter not proper set up type=" + filtertype);
    } else {
      filter.filter(data);
    }
  }

  private void normalizeTemplates(float[] data) {

    double energy = 0.0;
    double dc = 0.;
    for (int i = 0; i < data.length; i++) {
      energy += data[i] * data[i];
      dc += data[i];
    }
    dc = dc / data.length;
    float T = (float) (1.0 / Math.sqrt(energy));
    for (int i = 0; i < data.length; i++) {
      data[i] *= T;
    }
    if(Math.abs(dc) > 10.) System.out.println("***** normalized templates are not near DC? dc="+dc);
  }

  /*DCKprivate void cleartemporarytemplates() {
    temporarytemplates.clear();
  }*/

  /** DCK this does not appear to be used 
   * 
  * This methods uses the index of the peak, time of the 1st sample in the waveform buffer,
  * and knowledge about the length of the template (and lag) to find the waveform 
  * associated with the detections
  * wfbuffer: contains the waveform data taken from the QueryRing and filtered
  *
  */
  /*public void templatesfrombuffer(float[][] wfbuffer, int offset,
          GregorianCalendar bt) throws IOException {
    if (offset >= 0 && (offset + channels.length * templateLength) < wfbuffer[0].length) {
      float[] t = new float[templateLength * channels.length];
      for (int i = 0; i < channels.length; i++) {
        System.arraycopy(wfbuffer[i], offset, t, i * templateLength, templateLength);
      }
      normalizeTemplates(t);

      writeTemplates(t, channels.length, rate, bt);  //Debug
      temporarytemplates.add(t);
    }
  }*/

  //
  // This methods uses the index of the peak, time of the 1st sample in the waveform buffer,
  // and knowledge about the length of the template (and lag) to find the waveform 
  // associated with the detections
  // wfbuffer: contains the waveform data taken from the QueryRing and filtered
  // t: reuseable array that will contain the extracted templates for the detection
  //    t is packed end-to-end
  // t: time of the 1st sample of the waveform buffere
  // tlen:  template length
  //
  //
  /*DCKprivate void initializeDetectorTemplates() {
    detectortemplates.clear();
  }*/

  private void addDetectorTemplates(float[] wfbuffer, int nchannels,
          boolean normalize) throws IOException {

    /*DCKif (detectortemplates == null) {
      detectortemplates = new ArrayList<float[]>(1);
    }*/
    if (normalize) {
      normalizeTemplates(wfbuffer);
    }
    detectortemplates.add(wfbuffer);

  }

  /**
   * This methods uses the index of the peak, time of the 1st sample in the waveform buffer, and
   * knowledge about the length of the template (and lag) to find the waveform associated with the
   * detections.
   *
   * <br>
   * DCK - this does not appear to be used
   *
   * @param wfbuffer: contains the waveform data taken from the QueryRing and filtered
   * @param bt time of the 1st sample of the waveform buffer
   * @param tlen template length
   * @throws java.io.IOException
   *
   */
  /*public void extractAndAddTemplates(float[][] wfbuffer, int offset,
          int tlen, GregorianCalendar bt) throws IOException {

    int nchannels = wfbuffer.length;
    if (temporarytemplates == null) {
      temporarytemplates = new ArrayList<float[]>(1);
      templateLength = tlen;
    }

    float[] t = new float[nchannels * tlen];

    for (int i = 0; i < nchannels; i++) {
      System.arraycopy(wfbuffer[i], offset, t, i * templateLength, templateLength); // DCK: this looks very suspicious!
    }
    normalizeTemplates(t);
    temporarytemplates.add(t);

    GregorianCalendar b = new GregorianCalendar();
    b.setTimeInMillis(bt.getTimeInMillis());

    //Debug 
    writeTemplates(t, channels.length, rate, b);

  }*/

  // Debug code
  private static void writeTemplates2(float[] d, String stat, String channel, int rate, GregorianCalendar t) throws IOException {

    String cr = Util.ascdatetime2(t).toString();
    String ts = cr.substring(0, 4) + cr.substring(5, 7) + cr.substring(8, 10);
    ts = ts + "_" + cr.substring(11, 13) + cr.substring(14, 16) + cr.substring(17, 19);

    float delta = (float) (1.0 / rate);

    String name = "" + channel;
    SACFileWriter writernn = new SACFileWriter(outputpath + stat.trim() + "_" + name + "_" + ts + "long.sac");
    writernn.getHeader().nzyear = t.get(GregorianCalendar.YEAR);
    writernn.getHeader().nzjday = t.get(GregorianCalendar.DAY_OF_YEAR);
    writernn.getHeader().nzhour = t.get(GregorianCalendar.HOUR_OF_DAY);
    writernn.getHeader().nzmin = t.get(GregorianCalendar.MINUTE);
    writernn.getHeader().nzsec = t.get(GregorianCalendar.SECOND);
    writernn.getHeader().nzmsec = t.get(GregorianCalendar.MILLISECOND);
    writernn.getHeader().delta = delta;
    writernn.getHeader().b = 0;
    writernn.getHeader().knetwk = "TS";
    writernn.getHeader().khole = "00";
    writernn.getHeader().kstnm = "" + stat.trim();
    writernn.getHeader().kcmpnm = channel.trim();
    writernn.writeFloatArray(d);
    writernn.close();
  }

  // Debug code
  /*DCKprivate static void writeRTemplates(float[] d, String stat, String[] channels, int rate, GregorianCalendar t) throws IOException {

    int nchannels = channels.length;
    int length = d.length / nchannels;
    float[] data = new float[length];

    float delta = (float) (1.0 / rate);

    String cr = Util.ascdatetime2(t).toString();
    String ts = cr.substring(0, 4) + cr.substring(5, 7) + cr.substring(8, 10);
    ts = ts + "_" + cr.substring(11, 13) + cr.substring(14, 16) + cr.substring(17, 19);

    for (int i = 0; i < nchannels; i++) {
      SACFileWriter writernn = new SACFileWriter(outputpath + stat.trim() + "_" + channels[i] + "_" + ts + "r.sac");
      writernn.getHeader().nzyear = t.get(GregorianCalendar.YEAR);
      writernn.getHeader().nzjday = t.get(GregorianCalendar.DAY_OF_YEAR);
      writernn.getHeader().nzhour = t.get(GregorianCalendar.HOUR_OF_DAY);
      writernn.getHeader().nzmin = t.get(GregorianCalendar.MINUTE);
      writernn.getHeader().nzsec = t.get(GregorianCalendar.SECOND);
      writernn.getHeader().nzmsec = t.get(GregorianCalendar.MILLISECOND);
      writernn.getHeader().delta = delta;
      writernn.getHeader().b = 0;
      writernn.getHeader().knetwk = "TS";
      writernn.getHeader().khole = "00";
      writernn.getHeader().kstnm = "" + stat.trim();
      System.arraycopy(d, i * length, data, 0, length);
      writernn.writeFloatArray(data);
      writernn.close();
    }
  }*/

  // Debug code
  private static void writeTemplates(float[] d, String stat, String[] channels, int rate, GregorianCalendar t) throws IOException {

    int nchannels = channels.length;
    int length = d.length / nchannels;
    float[] data = new float[length];
    float delta = (float) (1.0 / rate);

    String cr = Util.ascdatetime2(t).toString();
    String ts = cr.substring(0, 4) + cr.substring(5, 7) + cr.substring(8, 10);
    ts = ts + "_" + cr.substring(11, 13) + cr.substring(14, 16) + cr.substring(17, 19);

    for (int i = 0; i < nchannels; i++) {
      SACFileWriter writernn = new SACFileWriter(outputpath + stat.trim() + "_" + channels[i] + "_" + ts + ".sac");
      writernn.getHeader().nzyear = t.get(GregorianCalendar.YEAR);
      writernn.getHeader().nzjday = t.get(GregorianCalendar.DAY_OF_YEAR);
      writernn.getHeader().nzhour = t.get(GregorianCalendar.HOUR_OF_DAY);
      writernn.getHeader().nzmin = t.get(GregorianCalendar.MINUTE);
      writernn.getHeader().nzsec = t.get(GregorianCalendar.SECOND);
      writernn.getHeader().nzmsec = t.get(GregorianCalendar.MILLISECOND);
      writernn.getHeader().delta = delta;
      writernn.getHeader().b = 0;
      writernn.getHeader().knetwk = "TS";
      writernn.getHeader().khole = "00";
      writernn.getHeader().kstnm = "" + stat.trim();
      writernn.getHeader().kcmpnm = channels[i];
      System.arraycopy(d, i * length, data, 0, length);
      writernn.writeFloatArray(data);
      writernn.close();
    }
  }

  // Debug code
  /*DCKprivate static void writeNoiseTemplates(float[] d, String stat, String[] channels, int rate, GregorianCalendar t) throws IOException {

    int nchannels = channels.length;
    int length = d.length / nchannels;
    float[] data = new float[length];
    float delta = (float) (1.0 / rate);

    String cr = Util.ascdatetime2(t).toString();
    String ts = cr.substring(0, 4) + cr.substring(5, 7) + cr.substring(8, 10);
    ts = ts + "_" + cr.substring(11, 13) + cr.substring(14, 16) + cr.substring(17, 19);

    for (int i = 0; i < nchannels; i++) {

      String name = "" + channels[i];
      SACFileWriter writernn = new SACFileWriter(outputpath + stat.trim() + "_" + name + "_" + ts + "noise.sac");
      writernn.getHeader().nzyear = t.get(GregorianCalendar.YEAR);
      writernn.getHeader().nzjday = t.get(GregorianCalendar.DAY_OF_YEAR);
      writernn.getHeader().nzhour = t.get(GregorianCalendar.HOUR_OF_DAY);
      writernn.getHeader().nzmin = t.get(GregorianCalendar.MINUTE);
      writernn.getHeader().nzsec = t.get(GregorianCalendar.SECOND);
      writernn.getHeader().nzmsec = t.get(GregorianCalendar.MILLISECOND);
      writernn.getHeader().delta = delta;
      writernn.getHeader().b = 0;
      writernn.getHeader().knetwk = "TS";
      writernn.getHeader().khole = "00";
      writernn.getHeader().kstnm = "" + stat.trim();
      System.arraycopy(d, i * length, data, 0, length);
      writernn.writeFloatArray(data);
      writernn.close();
    }
  }*/
  // Debug code

  /*DCKprivate static void writeTemplates(float[] d, int nchannels, int rate, GregorianCalendar t) throws IOException {
    int length = d.length / nchannels;
    float[] data = new float[length];
    String name = "";

    float delta = (float) (1.0 / rate);
    for (int i = 0; i < nchannels; i++) {

      String cr = Util.ascdatetime2(t).toString();
      String ts = cr.substring(0, 4) + cr.substring(5, 7) + cr.substring(8, 10);
      ts = ts + "_" + cr.substring(11, 13) + cr.substring(14, 16) + cr.substring(17, 19);
      if (i == 0) {
        name = "tX";
      }
      if (i == 1) {
        name = "tY";
      }
      if (i == 2) {
        name = "tZ";
      }
      SACFileWriter writernn = new SACFileWriter(outputpath + name + "_" + ts + ".sac");
      writernn.getHeader().nzyear = t.get(GregorianCalendar.YEAR);
      writernn.getHeader().nzjday = t.get(GregorianCalendar.DAY_OF_YEAR);
      writernn.getHeader().nzhour = t.get(GregorianCalendar.HOUR_OF_DAY);
      writernn.getHeader().nzmin = t.get(GregorianCalendar.MINUTE);
      writernn.getHeader().nzsec = t.get(GregorianCalendar.SECOND);
      writernn.getHeader().nzmsec = t.get(GregorianCalendar.MILLISECOND);
      writernn.getHeader().delta = delta;
      writernn.getHeader().b = 0;
      writernn.getHeader().knetwk = "TS";
      writernn.getHeader().kstnm = "TEST";
      writernn.getHeader().khole = "00";
      writernn.getHeader().kstnm = "HH";
      System.arraycopy(d, i * length, data, 0, length);
      writernn.writeFloatArray(data);
      writernn.close();
    }
  }*/
  


  //Do Harris 2006 dendro alignment
  public float[] align(float alignbuffer, float corrRange, float pickwindow, ArrayList<String> phasedata,
          String inputcwbip, int inputcwbport) throws IOException {

    int buffersamples = (int) (alignbuffer * rate);
    int corrFullLength = channels.length * templateLength + buffersamples * channels.length * 2;
    float[][] datavects = new float[phasedata.size()][corrFullLength];
    int pickWindowSamples = (int) (pickwindow * rate);
    PrintStream aout = new PrintStream("./Alignment.out");

    for (int i = 0; i < phasedata.size(); i++) {
      String phd = "";
      String[] ss = phasedata.get(i).split("\\s+");
      for (int ii = 1; ii < ss.length; ii++) {
        phd = phd + ss[ii] + " ";
      }
      datavects[i] = getTemplateData(phd, false, inputcwbip, inputcwbport, false, alignbuffer);
    }

    float[][] datavects_premask = new float[phasedata.size()][corrFullLength];

    for (int i = 0; i < phasedata.size(); i++) {
      System.arraycopy(datavects[i], 0, datavects_premask[i], 0, corrFullLength);
    }
    System.out.println("Aligning Templates");
    int precounts = (int) Math.abs(preEvent) * rate;
    if (pickwindow != 0.0f) {
      for (int i = 0; i < phasedata.size(); i++) {
        for (int j = 0; j < corrFullLength; j++) {
          switch (channels.length) {
            case 1:
              // DCK: this looks awful, does it do something useful?
              if ((j >= (buffersamples + (2 * 0 * buffersamples) + (0 * templateLength) + precounts - pickWindowSamples))
                      && (j <= (buffersamples + (2 * 0 * buffersamples) + (0 * templateLength) + precounts + pickWindowSamples))) 
                ; else {
                datavects[i][j] = 0;
              }
              break;
            case 2:
              // DCK: this looks awful, does it do something useful?
              if (j >= ((buffersamples + (2 * 0 * buffersamples) + (0 * templateLength) + precounts - pickWindowSamples))
                      && (j <= (buffersamples + (2 * 0 * buffersamples) + (0 * templateLength) + precounts + pickWindowSamples)))
                ; else if ((j >= (buffersamples + (2 * 1 * buffersamples) + (1 * templateLength) + precounts - pickWindowSamples))
                      && (j <= (buffersamples + (2 * 1 * buffersamples) + (1 * templateLength) + precounts + pickWindowSamples)))
                ; else {
                datavects[i][j] = 0;
              }
              break;
            case 3:
              // DCK: this looks awful, does it do something useful?
              if ((j >= (buffersamples + (2 * 0 * buffersamples) + (0 * templateLength) + precounts - pickWindowSamples))
                      && (j <= (buffersamples + (2 * 0 * buffersamples) + (0 * templateLength) + precounts + pickWindowSamples)))
                ; else if ((j >= (buffersamples + (2 * 1 * buffersamples) + (1 * templateLength) + precounts - pickWindowSamples))
                      && (j <= (buffersamples + (2 * 1 * buffersamples) + (1 * templateLength) + precounts + pickWindowSamples)))
                ; else if ((j >= (buffersamples + (2 * 2 * buffersamples) + (2 * templateLength) + precounts - pickWindowSamples))
                      && (j <= (buffersamples + (2 * 2 * buffersamples) + (2 * templateLength) + precounts + pickWindowSamples)))
                ; else {
                datavects[i][j] = 0;
              }
              break;
            default:
              break;
          }
        }
      }
    }

    try (PrintWriter writer = new PrintWriter("corr_templates.txt", "UTF-8")) {
      for (int i = 0; i < phasedata.size(); i++) {
        for (int j = 0; j < corrFullLength; j++) {
          writer.print(datavects_premask[i][j]);
          writer.print(" ");
          writer.println(datavects[i][j]);
        }
      }
    }
    //Array of shifts and maxcor coefficient, 1st dim 0 is shifts, 1 is coef
    float[][][] corinfo = new float[2][phasedata.size()][phasedata.size()];
    for (int i = 0; i < phasedata.size(); i++) {
      for (int j = i + 1; j < phasedata.size(); j++) {
        float[] corresults = xcor(datavects[i], datavects[j], (int) (corrRange * rate * 2));
        corinfo[0][i][j] = corresults[0];
        corinfo[1][i][j] = corresults[1];

        //System.out.println(corresults[0]);
      }
    }

    int[] events = new int[phasedata.size()];
    int[] events_renamed = new int[phasedata.size()];

    for (int i = 0; i < events.length; i++) {
      events[i] = i;
      events_renamed[i] = i;
    }

    //Debug Printing
    System.out.println("Correlation Matrix:");
    System.out.print("         ");
    aout.println("Correlation Matrix:");
    aout.println("         ");
    for (int i = 1; i < events.length; i++) {
      System.out.printf("%8d ", events[i]);
      aout.println(events[i]);
    }
    System.out.println("");
    aout.println("");
    for (int i = 0; i < phasedata.size(); i++) {
      if (i + 1 < events.length) {
        System.out.printf("%8d ", events[i]);
        aout.println(events[i]);
      }
      for (int j = 0; j < i; j++) {
        System.out.print("         ");
        aout.println("         ");
      }
      for (int j = i + 1; j < phasedata.size(); j++) {
        System.out.printf("%8.2f", corinfo[1][i][j]);
        System.out.print(" ");
        aout.println(corinfo[1][i][j]);
        aout.println(" ");
        //System.out.println(corresults[0]);
      }

      System.out.println("");
      aout.println("");
    }
    System.out.println("");
    aout.println("");

    //Dendrogram it.... Harris 2006
    System.out.println("Clustering:");
    aout.println("Clustering:");

    //float[][][] corfinal = new float[2][phasedata.size()][phasedata.size()];
    float[][][] corworking = new float[2][phasedata.size()][phasedata.size()];
    for (int i = 0; i < events.length; i++) {
      for (int j = i + 1; j < events.length; j++) {
        corworking[0][i][j] = corinfo[0][i][j];
        corworking[1][i][j] = corinfo[1][i][j];

      }
    }

    int pairs = 0;
    int[][] pairlist = new int[2][events.length - 1];
    float[] shifts = new float[events.length - 1];
    while (pairs < events.length - 1) {

      //findmax
      float maxcor = -1;
      int paira = -1;
      int pairb = -1;
      float shift = -999f;

      for (int i = 0; i < corworking[0][0].length; i++) {
        for (int j = i + 1; j < corworking[0][0].length; j++) {
          if ((corworking[1][i][j] > maxcor)) {

            paira = i;
            pairb = j;
            maxcor = corworking[1][paira][pairb];
            shift = corworking[0][paira][pairb];
          }
        }
      }

      System.out.printf("Found max pair: %3d %3d %4.2f %6.2f\n", events_renamed[paira], events_renamed[pairb], maxcor, shift);
      aout.println("Found max pair:" + events_renamed[paira] + " " + events_renamed[pairb] + " " + maxcor + " " + shift + "\n");
      pairlist[0][pairs] = events_renamed[paira];
      pairlist[1][pairs] = events_renamed[pairb];
      shifts[pairs] = shift;
      System.out.println("");
      aout.println("");
      //Make new matrix 
      float[][][] cortemp = new float[2][phasedata.size() - pairs - 1][phasedata.size() - pairs - 1];
      float[] tempcor = new float[((corworking[0][0].length - 1) * (corworking[0][0].length - 2)) / 2];
      float[] tempshift = new float[((corworking[0][0].length - 1) * (corworking[0][0].length - 2)) / 2];
      int k = 0;
      for (int i = 0; i < corworking[0][0].length; i++) {
        for (int j = i + 1; j < corworking[0][0].length; j++) {
          if (i != pairb && j != pairb) {

            if ((i == paira) && (j > pairb) && (corworking[1][i][j] < corworking[1][pairb][j])) {
              tempcor[k] = corworking[1][pairb][j];
              tempshift[k] = corworking[0][pairb][j] + shift;
            } else if ((i == paira) && (j < pairb) && (corworking[1][i][j] < corworking[1][j][pairb])) {
              tempcor[k] = corworking[1][j][pairb];
              tempshift[k] = shift - corworking[0][j][pairb];
            } else if ((j == paira) && (i > pairb) && (corworking[1][i][j] < corworking[1][pairb][i])) {
              tempcor[k] = corworking[1][pairb][i];
              tempshift[k] = corworking[0][pairb][i] + shift;
            } else if ((j == paira) && (i < pairb) && (corworking[1][i][j] < corworking[1][i][pairb])) {
              tempcor[k] = corworking[1][i][pairb];
              tempshift[k] = corworking[0][i][pairb] - shift;
            } else {
              tempcor[k] = corworking[1][i][j];
              tempshift[k] = corworking[0][i][j];
            }
            k += 1;
          }
        }
      }

      k = 0;
      for (int i = 0; i < cortemp[0][0].length; i++) {
        for (int j = i + 1; j < cortemp[0][0].length; j++) {
          cortemp[0][i][j] = tempshift[k];
          cortemp[1][i][j] = tempcor[k];
          k += 1;
        }
      }

      corworking = cortemp;

      int[] tmp = new int[cortemp[0][0].length];
      for (int i = 0; i < tmp.length; i++) {
        tmp[i] = events_renamed[i];
        if (i >= pairb) {
          tmp[i] = events_renamed[i + 1];
        }
      }
      events_renamed = tmp;
      //print matrix
      System.out.print("         ");
      aout.println("         ");
      for (int i = 1; i < corworking[0][0].length; i++) {
        System.out.printf("%8d ", events_renamed[i]);
        aout.println(events_renamed[i]);
      }
      System.out.println("");
      aout.println("");
      for (int i = 0; i < corworking[0][0].length; i++) {
        if (i + 1 < corworking[0][0].length) {
          System.out.printf("%8d ", events_renamed[i]);
          aout.println(events_renamed[i]);
        }
        for (int j = 0; j < i; j++) {
          System.out.print("         ");
          aout.println("         ");
        }
        for (int j = i + 1; j < corworking[0][0].length; j++) {
          System.out.printf("%8.2f", corworking[1][i][j]);
          System.out.print(" ");
          aout.println(corworking[1][i][j]);
          aout.println(" ");
          //System.out.println(corresults[0]);
        }

        System.out.println("");
        aout.println("");
      }
      System.out.println("");
      aout.println("");
      pairs += 1;

    }   // while pairs

    System.out.println("Shift List:");
    System.out.println("Iteration Event1 Event2 Shift:");
    aout.println("Shift List:");
    aout.println("Iteration Event1 Event2 Shift:");

    for (int j = 0; j < pairlist[0].length; j++) {

      System.out.printf("%2d %2d %2d %4.2f\n", j, pairlist[0][j], pairlist[1][j], shifts[j]);
      aout.println(j + " " + pairlist[0][j] + " " + pairlist[1][j] + " " + shifts[j]);

    }
    System.out.println("End");
    aout.println("End");

    float[] shiftsums = new float[events.length];
    for (int i = 0; i < events.length; i++) {
      int eventtemp = i;
      for (int j = 0; j < pairlist[0].length; j++) {
        if (pairlist[1][j] == eventtemp) {
          eventtemp = pairlist[0][j];
          shiftsums[i] += shifts[j];
        }
      }
    }

    System.out.println("\nRelative Shifts:\nEvent Shift");
    aout.println("\nRelative Shifts:\nEvent Shift");

    for (int i = 0; i < events.length; i++) {
      System.out.printf("%5d %6.3f\n", events[i], shiftsums[i]);
      aout.println(events[i] + " " + shiftsums[i]);
    }
    System.out.println("");
    aout.println("");
    return shiftsums;
  }

  private float[] xcor(float[] array1, float[] array2, int samples) {
    float max1 = 0;
    float max2 = 0;
    float mean1 = 0;
    float mean2 = 0;
    float std1 = 0;
    float std2 = 0;

    //assummes arrays are the same length
    for (int i = 0; i < array1.length; i++) {
      if (array1[i] > max1) {
        max1 = array1[i];
      }
      if (array2[i] > max2) {
        max2 = array2[i];
      }
      mean1 = array1[i];
      mean2 = array2[i];

    }

    mean1 = mean1 / array1.length;
    mean2 = mean2 / array2.length;

    for (int i = 0; i < array1.length; i++) {
      array1[i] = array1[i] / max1;
      array2[i] = array2[i] / max2;

      std1 += (array1[i] - mean1) * (array1[i] - mean1);
      std2 += (array2[i] - mean2) * (array2[i] - mean2);
    }

    float denometer = (float) Math.sqrt(std1 * std2);

    float[] corrvalues = new float[2 * samples - 1];

    for (int lag = array2.length - 1, idx = samples - array2.length + 1; lag > -array1.length; lag--, idx++) {
      if (idx < 0) {
        continue;
      }

      if (idx >= corrvalues.length) {
        break;
      }
      int start = 0;
      if (lag < 0) {
        start = -lag;
      }

      int end = array1.length - 1;
      if (end > array2.length - lag - 1) {
        end = array2.length - lag - 1;
      }

      for (int n = start; n <= end; n++) {
        corrvalues[idx] += (array1[n] - mean1) * (array2[lag + n] - mean2);
      }
    }

    //to hold shift and max cor value
    float[] corresults = new float[2];
    int index = 0;
    float maxcor = -999;
    for (int i = 0; i < corrvalues.length; i++) {
      if (corrvalues[i] / denometer > maxcor) {
        maxcor = corrvalues[i] / denometer;
        index = i;
      }
    }

    float timeshift = -1.0f * (float) samples / rate + (float) index / rate;
    corresults[0] = timeshift;
    corresults[1] = maxcor;
    if (corresults[1] < -1.1) {
      corresults[0] = 0;
      corresults[1] = 0;
    }
    return corresults;
  }

  //
  // Moves templates in the temporary template array into the permanent templates
  //
  public void updateDetectors() throws IOException {
    /*DCKif (detectortemplates == null) {
      detectortemplates = new ArrayList<float[]>(1);
    }*/
    int nseis;
    //DCKif (temporarytemplates != null) {
      nseis = temporarytemplates.size();
    //DCK}
    int nch = channels.length;
    int npts = templateLength * nch;

    for (int i = 0; i < nseis; i++) {
      float[] data = new float[nch * templateLength];
      System.arraycopy(temporarytemplates.get(i), 0, data, 0, npts);
      detectortemplates.add(data);
    }
    //DCKif(temporarytemplates != null) {
      temporarytemplates.clear();
    //DCK}
    initialtime = false;
  }

  private boolean stackAndDifferentiate() throws IOException {

    // Find the getStation with the fewest number of sample points in the seismogram 
    // nseis: number of seismograms in the ensemble used to compute the templates
    // Also get the SCNL for the getStation in order to add the SCNL information
    // into the header and file name of the template eigenvectors
    boolean sucess = false;
    int nchannels = channels.length;
    int ntemplates = 0;
    int nseis = temporarytemplates.size();
    int npts = temporarytemplates.get(0).length / nchannels;

    //DCKif (detectortemplates != null) {
      ntemplates = detectortemplates.size();
    //DCK}
    if (nseis < 2) {
      System.out.println("Not enough templates to stack, running in Independent Mode\n");
      return sucess;
    }
    float stacks[] = new float[nchannels * npts];
    float diffStacks[] = new float[nchannels * npts];

    for (int i = 0; i < nseis; i++) {
      for (int j = 0; j < nchannels * npts; j++) {
        stacks[j] = stacks[j] + temporarytemplates.get(i)[j];
      }
    }
    //Average
    for (int j = 0; j < nchannels * npts; j++) {
      stacks[j] = stacks[j] / nseis;
    }

    //Differentiate
    for (int j = 1; j < nchannels * npts; j++) {
      diffStacks[j - 1] = diffStacks[j - 1] = stacks[j] - stacks[j - 1];
    }

    //DCKdetectortemplates = new ArrayList<float[]>(1);
    detectortemplates.clear();      // DCK prevent all these creations of arrays lists
    detectortemplates.add(stacks);
    detectortemplates.add(diffStacks);

    temporarytemplates.clear();
    sucess = true;
    return sucess;

  }
  /**
   * 
   * @param c The completeness seems to always be 0.9
   * @param outputresults If true, write out the results
   * @throws IOException 
   */
  public void computeEigenvectors(float c, boolean outputresults) throws IOException {

    // Find the getStation with the fewest number of sample points in the seismogram 
    // nseis: number of seismograms in the ensemble used to compute the templates
    // Also get the SCNL for the getStation in order to add the SCNL information
    // into the header and file name of the template eigenvectors
    completeness = c;
    int nchannels = channels.length;
    int ntemplates = 0;
    int nseis = temporarytemplates.size();
    int npts = temporarytemplates.get(0).length / nchannels;

    if (detectortemplates != null) {
      ntemplates = detectortemplates.size();
    }

    //Matrix of data basis vectors is U (these are eigenvectors of G*G')
    //Corresponding singular values S are square roots of the eigenvalues, lambda, of G*G'u=lambda u
    //See Aster, 2012 for details on the SVD
    // Load a matrix with the seismograms that are used to compute the template
    // The seismograms are loaded a column vectors in the 2-D array matrix
    // Normalize each time-series (vector) by its Euclidean norm.  There is a call to
    // ObsData to do this.
    double matrix[][] = new double[nchannels * npts][nseis + ntemplates];

    if (detectortemplates != null) {
      for (int i = 0; i < ntemplates; i++) {
        for (int j = 0; j < nchannels * npts; j++) {
          matrix[j][i] = detectortemplates.get(i)[j];
        }
      }
    }
    for (int i = 0; i < nseis; i++) {
      for (int j = 0; j < nchannels * npts; j++) {
        matrix[j][i + ntemplates] = temporarytemplates.get(i)[j];
      }
    }

    Matrix M = new Matrix(matrix);
    SingularValueDecomposition SVD = new SingularValueDecomposition(M);
    Matrix U = SVD.getU();
    Matrix UT = U.transpose();

    // From Rick Aster example
    //%display the decomposition of each trace as a martix
    //for i=1:b
    //for j=1:b
    //smat(i,j)=(U(:,j)'*d(:,i))^2;
    //end
    //end
    Matrix SS = UT.times(M);
    double[] Uvec = U.getColumnPackedCopy();
    double[][] smat = SS.getArrayCopy();
    for (int i = 0; i < nseis; i++) {
      for (int j = 0; j < nseis; j++) {
        smat[i][j] = smat[i][j] * smat[i][j];
      }
    }

    int neigenvectors = U.getColumnDimension();
    int nsmpts = U.getRowDimension();

    //matrix[j][i] = temporarytemplates.get(k)[j];
    float[][] alphas = new float[neigenvectors][neigenvectors];
    float alpha = 0;
    for (int i = 0; i < neigenvectors; i++) {
      //float [] ev = temporarytemplates.get(i);
      for (int j = 0; j < neigenvectors; j++) {
        int startingindex = j * nsmpts;
        for (int k = 0; k < nsmpts; k++) {
          alpha = (float) (alpha + matrix[k][i] * (float) Uvec[k + startingindex]);
        }
        alphas[i][j] = alpha * alpha;
        alpha = 0;
      }
    }

    float[] rank = new float[neigenvectors];
    alpha = 0;
    for (int i = 0; i < neigenvectors; i++) {
      for (int j = 0; j < neigenvectors; j++) {
        alpha = alpha + alphas[j][i];
      }
      rank[i] = alpha;
    }
    for (int i = 0; i < neigenvectors; i++) {
      if (rank[i] >= completeness * neigenvectors) {
        optimalnumber = i + 1;
        break;
      }
    }

    if (outputresults == true) {
      try (PrintStream alphaout = new PrintStream("alphas.txt")) {
        alphaout.printf("Completeness: " + completeness + " Optimal Number of Eigenvectors: " + optimalnumber + "\n");
        alphaout.printf("Coeffients per seismogram \n");
        for (int i = 0; i < neigenvectors; i++) {
          float sum = (float) 0.0;
          for (int j = 0; j < neigenvectors; j++) {
            alphaout.printf(alphas[i][j] + " ");
            sum = sum + alphas[i][j];
          }
          alphaout.println("  Sum Total: " + sum + "\n");
        }
      }

      GregorianCalendar tt = new GregorianCalendar();
      tt.setTimeInMillis(System.currentTimeMillis());
      String cr = Util.ascdatetime2(tt).toString();
      String ts = cr.substring(0, 4) + cr.substring(5, 7) + cr.substring(8, 10);
      ts = ts + "_" + cr.substring(11, 13) + cr.substring(14, 16) + cr.substring(17, 19);

      // For testing
      for (int i = 0; i < neigenvectors; i++) {
        System.out.println(i + 1 + "," + rank[i] / neigenvectors + "\n");
      }
      for (int i = 0; i < neigenvectors; i++) {
        System.out.println("EV" + i + "\n");
        alpha = 0;
        for (int j = 0; j < neigenvectors; j++) {
          alpha = alpha + alphas[i][j];
          System.out.println(j + 1 + "," + alpha + "\n");
        }
      }

      float[] temp = new float[neigenvectors];
      for (int i = 0; i < neigenvectors; i++) {
        alpha = 0;
        for (int j = 0; j < neigenvectors; j++) {
          alpha = alpha + alphas[i][j];
          temp[j] = alpha;
        }

        String evnumber = Integer.toString(i);
        evnumber = Util.leftPad(evnumber, 2).toString().replaceAll(" ", "0");
        String name = "alpha" + evnumber + "_" + ts + ".dat";
        try (PrintStream alphafile = new PrintStream(name)) {
          alphafile.print("VARIABLES=EigenNumber,Alpha\n");

          for (int j = 0; j < neigenvectors; j++) {
            alphafile.printf(j + "," + temp[j] + "\n");
          }
        }

        //evnumber = Util.leftPad(evnumber,2).replaceAll(" ","0");
        String sacfile = outputpath + "alpha" + evnumber + "_" + ts + ".sac";
        SACFileWriter writerv = new SACFileWriter(sacfile);
        writerv.getHeader().delta = 1;
        writerv.getHeader().b = 0.0f;
        writerv.getHeader().az = i + 1;
        writerv.getHeader().knetwk = "EV";
        writerv.getHeader().kstnm = "TEST";
        writerv.getHeader().kcmpnm = "HH";
        writerv.getHeader().khole = "00";
        writerv.writeFloatArray(temp);
        writerv.close();
      }
    }

    /*DCKif (detectortemplates == null) {
      detectortemplates = new ArrayList<float[]>(1);
    }*/
    if (optimalnumber > detectortemplates.size()) {
      for (int i = 0; i < optimalnumber; i++) {
        float[] eigenvector = new float[nsmpts];
        int si = i * nsmpts;
        for (int j = 0; j < nsmpts; j++) {
          eigenvector[j] = (float) Uvec[si + j];
        }
        if (initialtime == true) {
          detectortemplates.add(eigenvector);
        } else if (i > detectortemplates.size() - 1) {
          detectortemplates.add(eigenvector);
        }
      }
    }
    temporarytemplates.clear();
    initialtime = false;

    //
    //This gets thrown away--Debug   
    if (outputresults == true) {

      GregorianCalendar tt = new GregorianCalendar();
      tt.setTimeInMillis(System.currentTimeMillis());
      String cr = Util.ascdatetime2(tt).toString();
      String ts = cr.substring(0, 4) + cr.substring(5, 7) + cr.substring(8, 10);
      ts = ts + "_" + cr.substring(11, 13) + cr.substring(14, 16) + cr.substring(17, 19);

      for (int i = 0; i < neigenvectors; i++) {
        float[] eigenvector = new float[nsmpts];
        //
        // The vertical and horizontal components are stacked end-to-end
        // in the array. 

        String evnumber = Integer.toString(i);
        evnumber = Util.leftPad(evnumber, 2).toString().replaceAll(" ", "0");

        int si = i * nsmpts;
        for (int j = 0; j < nsmpts; j++) {
          eigenvector[j] = (float) Uvec[si + j];
        }

        String name = "";
        for (int ich = 0; ich < nchannels; ich++) {

          if (ich == 0) {
            name = "tX_ev";
          }
          if (ich == 1) {
            name = "tY_ev";
          }
          if (ich == 2) {
            name = "tZ_ev";
          }

          String sacfile = outputpath + name + evnumber + "_" + ts + ".sac";
          SACFileWriter writerv = new SACFileWriter(sacfile);
          writerv.getHeader().delta = 1;
          writerv.getHeader().b = 0.0f;
          writerv.getHeader().az = i + 1;
          writerv.getHeader().knetwk = "EV";
          writerv.getHeader().kstnm = "TEST";
          writerv.getHeader().kcmpnm = "" + ich;
          writerv.getHeader().khole = "00";

          float[] v = new float[npts];
          System.arraycopy(eigenvector, ich * npts, v, 0, npts);
          writerv.writeFloatArray(v);
          writerv.close();
        }
      }
    }
    for (int i = 0; i < neigenvectors; i++) {
      rank[i] = rank[i] / neigenvectors;
    }
  }

  public void computeEigenvectors(boolean outputresults) throws IOException {

    // Find the getStation with the fewest number of sample points in the seismogram 
    // nseis: number of seismograms in the ensemble used to compute the templates
    // Also get the SCNL for the getStation in order to add the SCNL information
    // into the header and file name of the template eigenvectors
    int nchannels = channels.length;
    int ntemplates = 0;
    int nseis = temporarytemplates.size();
    int npts = temporarytemplates.get(0).length / nchannels;

    if (detectortemplates != null) {
      ntemplates = detectortemplates.size();
    }

    //Matrix of data basis vectors is U (these are eigenvectors of G*G')
    //Corresponding singular values S are square roots of the eigenvalues, lambda, of G*G'u=lambda u
    //See Aster, 2012 for details on the SVD
    // Load a matrix with the seismograms that are used to compute the template
    // The seismograms are loaded a column vectors in the 2-D array matrix
    // Normalize each time-series (vector) by its Euclidean norm.  There is a call to
    // ObsData to do this.
    double matrix[][] = new double[nchannels * npts][nseis + ntemplates];

    if (detectortemplates != null) {
      for (int i = 0; i < ntemplates; i++) {
        for (int j = 0; j < nchannels * npts; j++) {
          matrix[j][i] = detectortemplates.get(i)[j];
        }
      }
    }
    for (int i = 0; i < nseis; i++) {
      for (int j = 0; j < nchannels * npts; j++) {
        matrix[j][i + ntemplates] = temporarytemplates.get(i)[j];
      }
    }

    Matrix M = new Matrix(matrix);
    SingularValueDecomposition SVD = new SingularValueDecomposition(M);
    Matrix U = SVD.getU();
    Matrix UT = U.transpose();

    // From Rick Aster example
    //%display the decomposition of each trace as a martix
    //for i=1:b
    //for j=1:b
    //smat(i,j)=(U(:,j)'*d(:,i))^2;
    //end
    //end
    Matrix SS = UT.times(M);
    double[] Uvec = U.getColumnPackedCopy();
    double[][] smat = SS.getArrayCopy();
    for (int i = 0; i < nseis; i++) {
      for (int j = 0; j < nseis; j++) {
        smat[i][j] = smat[i][j] * smat[i][j];
      }
    }

    int neigenvectors = U.getColumnDimension();
    int nsmpts = U.getRowDimension();

    //matrix[j][i] = temporarytemplates.get(k)[j];
    float[][] alphas = new float[neigenvectors][neigenvectors];
    float alpha = 0;
    for (int i = 0; i < neigenvectors; i++) {
      //float [] ev = temporarytemplates.get(i);
      for (int j = 0; j < neigenvectors; j++) {
        int startingindex = j * nsmpts;
        for (int k = 0; k < nsmpts; k++) {
          alpha = (float) (alpha + matrix[k][i] * (float) Uvec[k + startingindex]);
        }
        alphas[i][j] = alpha * alpha;
        alpha = 0;
      }
    }

    float[] rank = new float[neigenvectors];
    alpha = 0;
    for (int i = 0; i < neigenvectors; i++) {
      for (int j = 0; j < neigenvectors; j++) {
        alpha = alpha + alphas[j][i];
      }
      rank[i] = alpha;
    }
    for (int i = 0; i < neigenvectors; i++) {
      if (rank[i] >= completeness * neigenvectors) {
        optimalnumber = i + 1;
        break;
      }
    }

    // All gets tossed in an operational system
    if (outputresults == true) {
      try (PrintStream alphaout = new PrintStream("alphas.txt")) {
        alphaout.printf("Completeness: " + completeness + " Optimal Number of Eigenvectors: " + optimalnumber + "\n");
        alphaout.printf("Coeffients per seismogram \n");
        for (int i = 0; i < neigenvectors; i++) {
          float sum = (float) 0.0;
          for (int j = 0; j < neigenvectors; j++) {
            alphaout.printf(alphas[i][j] + " ");
            sum = sum + alphas[i][j];
          }
          alphaout.println("  Sum Total: " + sum + "\n");
        }
      }

      GregorianCalendar tt = new GregorianCalendar();
      tt.setTimeInMillis(System.currentTimeMillis());
      String cr = Util.ascdatetime2(tt).toString();
      String ts = cr.substring(0, 4) + cr.substring(5, 7) + cr.substring(8, 10);
      ts = ts + "_" + cr.substring(11, 13) + cr.substring(14, 16) + cr.substring(17, 19);

      // For testing
      for (int i = 0; i < neigenvectors; i++) {
        System.out.println(i + 1 + "," + rank[i] / neigenvectors + "\n");
      }
      for (int i = 0; i < neigenvectors; i++) {
        System.out.println("EV" + i + "\n");
        alpha = 0;
        for (int j = 0; j < neigenvectors; j++) {
          alpha = alpha + alphas[i][j];
          System.out.println(j + 1 + "," + alpha + "\n");
        }
      }
      float[] temp = new float[neigenvectors];
      for (int i = 0; i < neigenvectors; i++) {
        alpha = 0;
        for (int j = 0; j < neigenvectors; j++) {
          alpha = alpha + alphas[i][j];
          temp[j] = alpha;
        }

        String evnumber = Integer.toString(i);
        evnumber = Util.leftPad(evnumber, 2).toString().replaceAll(" ", "0");
        String name = "alpha" + evnumber + "_" + ts + ".dat";
        try (PrintStream alphafile = new PrintStream(name)) {
          alphafile.print("VARIABLES=EigenNumber,Alpha\n");

          for (int j = 0; j < neigenvectors; j++) {
            alphafile.printf(j + "," + temp[j] + "\n");
          }
        }

        //evnumber = Util.leftPad(evnumber,2).replaceAll(" ","0");
        String sacfile = outputpath + "alpha" + evnumber + "_" + ts + ".sac";
        SACFileWriter writerv = new SACFileWriter(sacfile);
        writerv.getHeader().delta = 1;
        writerv.getHeader().b = 0.0f;
        writerv.getHeader().az = i + 1;
        writerv.getHeader().knetwk = "EV";
        writerv.getHeader().kstnm = "TEST";
        writerv.getHeader().kcmpnm = "HH";
        writerv.getHeader().khole = "00";
        writerv.writeFloatArray(temp);
        writerv.close();
      }
    }

    /*DCKif (detectortemplates == null) {
      detectortemplates = new ArrayList<float[]>(1);
    }*/
    if (optimalnumber > detectortemplates.size()) {
      for (int i = 0; i < optimalnumber; i++) {
        float[] eigenvector = new float[nsmpts];
        int si = i * nsmpts;
        for (int j = 0; j < nsmpts; j++) {
          eigenvector[j] = (float) Uvec[si + j];
        }
        if (initialtime == true) {
          detectortemplates.add(eigenvector);
        } else if (i > detectortemplates.size() - 1) {
          detectortemplates.add(eigenvector);
        }
      }
    }
    temporarytemplates.clear();
    initialtime = false;

    if (outputresults == true) {
      GregorianCalendar tt = new GregorianCalendar();
      tt.setTimeInMillis(System.currentTimeMillis());
      String cr = Util.ascdatetime2(tt).toString();
      String ts = cr.substring(0, 4) + cr.substring(5, 7) + cr.substring(8, 10);
      ts = ts + "_" + cr.substring(11, 13) + cr.substring(14, 16) + cr.substring(17, 19);

      for (int i = 0; i < neigenvectors; i++) {
        float[] eigenvector = new float[nsmpts];
        //
        // The vertical and horizontal components are stacked end-to-end
        // in the array. 

        String evnumber = Integer.toString(i);
        evnumber = Util.leftPad(evnumber, 2).toString().replaceAll(" ", "0");

        int si = i * nsmpts;
        for (int j = 0; j < nsmpts; j++) {
          eigenvector[j] = (float) Uvec[si + j];
        }

        String name = "";
        for (int ich = 0; ich < nchannels; ich++) {

          if (ich == 0) {
            name = "tX_ev";
          }
          if (ich == 1) {
            name = "tY_ev";
          }
          if (ich == 2) {
            name = "tZ_ev";
          }

          String sacfile = outputpath + name + evnumber + "_" + ts + ".sac";
          SACFileWriter writerv = new SACFileWriter(sacfile);
          writerv.getHeader().delta = 1;
          writerv.getHeader().b = 0.0f;
          writerv.getHeader().az = i + 1;
          writerv.getHeader().knetwk = "EV";
          writerv.getHeader().kstnm = "TEST";
          writerv.getHeader().kcmpnm = "" + ich;
          writerv.getHeader().khole = "00";

          float[] v = new float[npts];
          System.arraycopy(eigenvector, ich * npts, v, 0, npts);
          writerv.writeFloatArray(v);
          writerv.close();
        }
      }
    }
    for (int i = 0; i < neigenvectors; i++) {
      rank[i] = rank[i] / neigenvectors;
    }
  }
}
