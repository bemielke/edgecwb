/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */

package gov.usgs.anss.fk;


import java.io.*;
import java.util.ArrayList;
import java.util.GregorianCalendar;
import java.util.Calendar;

import gov.usgs.cwbquery.QueryRing;
import gov.usgs.alarm.SendEvent;
import gov.usgs.anss.edge.RawToMiniSeed;
import gov.usgs.anss.edge.MiniSeedOutputFile;
import gov.usgs.anss.util.Util;
import gov.usgs.anss.edgethread.EdgeThread;
import gov.usgs.anss.edgemom.Logger;
import gov.usgs.anss.util.Distaz;
import gov.usgs.anss.picker.CWBFilterPicker6;

/**  FKArray is the main computational routine for the FK calculations.  It is called from  a FK
 * thread which is started by the FKManager mainly by invoking the getTimeseries() method which returns
 * the number of channels successfully obtained and does the beam calculation if the number obtained is
 * as large as the "ngood" from the caller.  See the method documentation for more details.
 * <br>
 * This class creates the FKParams object where the configuration data is kept, creates the FKChannel object
 * used in the calculations, and creates and populates the FKResults object used to pass
 * around the calculation outputs.
 * <p>
 * It can be configurated to run a FilterPicker when the beam shows a detection in order to build a
 * JSON pick including the beam back azimuth and other beam parameters.
 * <p>
 * This object is normally logged through the FK object that created it.
 * 
 *
 * @author benz 
 * @author ketchum
 */
public class FKArray {
  /**
   * used in the sin and cos calculations
   */
  public final static double pi=Math.PI;
  public final static double torad = Math.PI / 180.0;
/**
   * used in the sin and cos calculations
   */
  public final static double twopi = 2.0 * pi;
  public final long earlyLimit;
  static final double [] dzero = new double[101*101]; // used to zero arrays
  private FKChannel reference;
  private double rate;
  private final ArrayList<FKChannel> channels = new ArrayList<FKChannel>(2);
  private final ArrayList<FKSeismogram> ts = new ArrayList<FKSeismogram>(2);
  private final int nseis;
  private ArrayList<QueryRing> rings;
  private final String tag;
  private EdgeThread par;
  private FKParams fkparms;
  private FKResult fkresults;
  private double maxDistance;
  private double maxOverall;
  private PrintStream fksum = null;
  private PrintStream fkgrid = null;
  private PrintStream bmout = null;
  private boolean dbg;
  private final int [] d;               // Data read in as integers goes here before conversion to double
  private final double [] scratch;
  private final double [] beam;
  private final StringBuilder tmpsb = new StringBuilder(50);
  private double lastAzimuth;
  private long lastAzimuthTime;
  private CWBFilterPicker6 filterPicker;
  private String cmd;
  @Override
  public String toString() {return toStringBuilder(null).toString();}
  public final StringBuilder toStringBuilder(StringBuilder tmp) {
    StringBuilder sb = tmp;
    if(sb == null) {
      sb=Util.clear(tmpsb);
    }
    synchronized(sb) {
      sb.append("FK Array:").append(" ref=").append(reference.getChannel()).append(" nchan(used)=").append(nseis).
              append("/").append(channels.size()).append(" rt=").append(rate).
              append(" maxdist=").append(Util.df22(maxDistance)).append(" overdist=").append(Util.df22(maxOverall));
    }
    return sb;
  } 
  /** Get the array list of FK channels
   * 
   * @return the list of FKChannels
   */
  public ArrayList<FKChannel> getChannelList() {return channels;}
  /**
   * 
   * @param s A string to print to the log
   */
  protected void prt(String s){ if(par == null) Util.prt(s); else par.prt(s);}
  /**
   * 
   * @param s A String to print in the log preceded by the current time
   */
  protected final void prta(String s){ if(par == null) Util.prta(s); else par.prta(s);}
  /**
   * 
   * @param s A string to print to the log
   */
  protected void prt(StringBuilder s){ if(par == null) Util.prt(s); else par.prt(s);}
  /**
   * 
   * @param s A String to print in the log preceded by the current time
   */
  protected final void prta(StringBuilder s){ if(par == null) Util.prta(s); else par.prta(s);}
 
  /** This routine zeros out a double array of the given length
   * 
   * @param d User array to zero
   * @param len Length of d to zero
   */
  public static void dzero(double [] d, int len) {
    for(int i=0; i<len; i=i+dzero.length) {
      System.arraycopy(dzero, 0, d, i, Math.min(len -i, dzero.length));
    }
  }  
  /** return an FKChannel which is for the reference station/location
   * 
   * @return The reference FKChannel
   */
  public FKChannel getRef(){ return reference;}
  public double getRate() {return rate;}
  /** Return number of active channels in the beam - the active ones configed
   * 
   * @return Number of active channels in configuration
   */
  public int getNActive() { return nseis;}
  /** get double array with the beam computed from the method in the configuration
   * 
   * @return the beam array
   */
  public double [] getBeam() {return beam;}  // Get the beam of type beam method
  /** Creates a new instance of FKArray */
  //public FKArray() {
  //}
  /** Set the reference channel which is NOT in the channels for computing
   * 
   * @param s A FKChannel with the reference channel
   */
  private void addReference(FKChannel s) {
    reference = s;        // Allow adding a reference which is NOT in the calculation
  }
  /**
   * 
   * @param s An FKChannel to add to this configuration
   * @param ref If true, this channel is the referecne channel
   */
  private void addchan(FKChannel s, boolean ref) {
    if(ref) {
      reference = s;
    }
    channels.add(s);
  }
  /** Return the FKParams object which contains most of the configuration info
   * 
   * @return The FKParams
   */
  public FKParams getFKParams() {return fkparms;}
  /** Return the FKResult object from the last call to getTimeSeries().  This contains the
   * power grid, azimuth.  From the statisitics it contains the 3 greatest powers and their sxmax, symax values,
   * the totalpower in the grid, the mean and standard deviation over all power cells.
   * 
   * @return the FKResults object
   */
  public FKResult getFKResult() {return fkresults;}
  /** This constructor creates the FKParams object and populates it, 
   * creates the FKResult object for storing results :<br>
   * 1) Reads in the configured stations file and using parseSta, and addChan() creates the channels-FKChannel list
   * 2) Calls calcRelativeDistance() on each channel to set the relative distance parameters in X, Y,<br>
   * 3) Finds the maximum relative distance,<br>
   * 4) in FKParams sets the rate, beamlen (fft len), beamwindow(secs), overlap<br>
   * 5) Builds the list of FKSeismograms (one per channel)<br>
   * 6) Calculates the number of enabled channels.<br>
   * <br>
   * After creation the user calls getTimeSeries(time, npts) advancing time as needed.  The one configured
   * beam method can be obtained with getBeam() and the time etc is available from 
   * getFKParams(), and other beam results from getFKResults().
   * 
   * @param argline Argline with parameters
   * @param tag Tag to use in logging
   * @param parent EdgeThread through which logging is done
   */
  public FKArray (String argline, String tag, EdgeThread parent){
    par = parent;
    String line;
    this.tag = tag+"[FKA]";
    String [] args = argline.split("\\s");
    earlyLimit = new GregorianCalendar(2015, 1, 1, 0, 0, 0).getTimeInMillis();

    // parse the parameters
    fkparms = new FKParams(args, parent);
    if(fkparms.getDebug()) {
      try {
        dbg=true;
        fksum = new PrintStream(tag+"_fksum.out");    // These files used for diagnostic output
        fkgrid = new PrintStream(tag+"_fkgrid.out");
      } 
      catch(FileNotFoundException e) {
        prta("FK.FK(): Cannot open fksum.out or fkgrid.out file "+e);
        System.exit(1);
      }        
    }
    //beam = new FKBeam(fkparms, this, (FK) this);
    prta("new FKA: "+fkparms.toStringBuilder(null)+" args="+argline);
    //prta(beam.toStringBuilder(null));
    try {
      BufferedReader in = new BufferedReader(new FileReader(fkparms.getStationFile()));
      int i = 0;
      boolean first=true;
      while( (line = in.readLine()) != null) {
        int firstSpace = line.indexOf(" ");
        
        if(line.charAt(0) == '#' || line.charAt(0) == '!' ) 
          continue;
        if(line.charAt(firstSpace+1) == '#' || line.charAt(firstSpace+1) == '!') 
          continue;
        parseSta(line, first);
        first = false;
        i++;
      }
      // Calculate the relative distance from the referencs for all channels
      FKChannel r = reference;       // First in ArrayList is always the reference channel
      maxDistance=0.;
      maxOverall=0.;
      double [] results = new double[4];
      for(FKChannel c:channels) {
        c.calcRelativeDistance(r);
        double dist = Math.sqrt(c.getXrel() * c.getXrel() + c.getYrel() * c.getYrel());
        if(dist > maxDistance) maxDistance = dist;
        for(FKChannel c2: channels) {
          Distaz.distaz(c.getLat(), c.getLon(), c2.getLat(), c2.getLon(), results);
          if(results[0] > maxOverall) maxOverall=results[0];
        }
      }      
    }
    catch(IOException e) {
      prta("GOT an IOException opening file e="+e.getMessage());
      SendEvent.edgeSMEEvent("FKBadStnFile", "Station file read failed e="+e, (FKArray) this);
      e.printStackTrace(par.getPrintStream());
    } 
    
    // Set the rate in the parameters - calculate  the right FFT length based on beam window in seconds
    fkparms.setRate(rate);     // the rate comes from the station file
    if(fkparms.getBeamlen() <= 0) {
      int len = 2;
      while( len / rate < fkparms.getBeamWindow()) {
        len = len *2;
      }
      fkparms.setBeamlen(len);
      fkparms.setBeamWindow(len/rate);
      fkparms.setOverlap(len/2);
      prta(tag+" rate="+rate+" beamlen="+len+" beamwindow="+len/rate);
    }
    else {
      prta(tag+" **** unusual manual setting of beam fft length and overlap!!");
    }
    // compute the number of used seismograms and add a new FKSeismogram for each channel
    int ns=0;
    for (FKChannel channel : channels) {
      ts.add(new FKSeismogram(channel, 1./rate, fkparms.getBeamlen()));
      if (channel.getUse()) ns++;
    }
    nseis=ns;       // Set nchan to the number of channels in use
    
    // Allocate memory for results, beam, and scratch space for time series
    fkresults = new FKResult(fkparms.getNK());   // make the structure for getting results.
    fkresults.setPowerRatioLimit(fkparms.getSNRLimit());
    scratch = new double[fkparms.getBeamlen()];
    beam = new double[fkparms.getBeamlen()];
    d = new int[fkparms.getBeamlen()];
    cmd =  "-1 -mf -1 -c "+reference.getChannel()+" -b 2001-01-02 "+
            " -e 2001-01-01"+
            " -auth FP6-FK2 -agency US -db localhost/3306:status:mysql:status -blocksize 10. "+
            " -bands 0.5,1.:1.,1.:2.,1.:4.,1.:8.,1.:16.,1.:32.,1. -tlong 300.0 -thold1 6."+
            " -thold2 5.0 -tup 4. -tupmin .25 -h localhost >>USGSFK_"+reference.getChannel().replaceAll(" ", "_");

    
    // If debug is on, open up the print streams for the output
    if(fkparms.getDebug()) {
      try {
        bmout = new PrintStream("beam.out");
      } catch(FileNotFoundException e) {
        prta("FK.Main(): Cannot open log file beam.out "+e);
        System.exit(1);
      }
    }
    prta(toStringBuilder(null));      
  }
  /** Parse a line from the station file into its components, create an FKChannel record
   * for the line, 
   * 
   * @param str Line of the form ARRAY NN SSSSS CCC LL  latitude longitude elevation correction use
   * @param first If true, this station is the reference station
   */
  private void parseSta(String str, boolean first) {
    String [] line;
    line = str.split("\\s");
    String netn = line[0];             //array name
    String netw = line[1];             //network code
    String stat = line[2];             //station name
    String comp = line[3];             //component
    String loca = line[4];             //location code
    double lat  = Double.parseDouble(line[5]);
    double lon  = Double.parseDouble(line[6]);
    double ele  = Double.parseDouble(line[7]);
    double cr   = Double.parseDouble(line[8]);
    boolean use  = Integer.parseInt(line[9]) != 0;
    if(line.length >= 11) {
      prta("rate for "+netn+" is "+line[10]);
      rate = Double.parseDouble(line[10]);
    }
    FKChannel c = new FKChannel( netn, (netw+"  ").substring(0,2)+(stat+"     ").substring(0,5)+
        (comp+"   ").substring(0,3)+(loca+"  ").substring(0,2), lat, lon, ele, cr, use, par);
    if(use) 
      addchan(c, first);
    else if(first)  {
      addReference(c);
    }
  }
  /** The first call to getTimeSeries() needs to create all of the QueryRings used to access
   * the data.  It does so by calling this method
   * 
   * @param t Start time
   */
  private void initRings(GregorianCalendar t) {
    rings = new ArrayList<QueryRing>(channels.size());
    prta("Create Rings for "+Util.ascdatetime2(t)+" ch.size="+channels.size()+" "+fkparms.getTimeSeriesCWB());
    t.setTimeInMillis(t.getTimeInMillis()-10000);   // Start 10 seconds before first requested time
    for(int i=0; i<channels.size(); i++) {
      if(channels.get(i).getUse()) {        // Use flag must be on for us to use
        String seedname = channels.get(i).getChannel();
        seedname = seedname.replaceAll("_"," ");
        QueryRing q = new QueryRing(fkparms.getTimeSeriesCWB(),fkparms.getTimeSeriesCWBPort(), 
                seedname.trim(), t, 600., 50., 0., par);   // DCK: Rate will be looked up by MDS - is this a good idea?
        rings.add(i,q);   // note rings are index the same as the channels.
      }
    }
    t.setTimeInMillis(t.getTimeInMillis() + 10000);
    if(dbg) rings.get(rings.size()-1).setDebug(true);    
  }
  /** Reads in the time series, and if more the 70% of the channels are available for
   * the entire interval from time gtmp, the FFTs are done and the FK matrix formed according
   * to the configuration.  The user gets the number of channels used to form the 
   * FK which will never be below 70% of the total channels configured.  If the returned
   * number of channels is zero, then nothing was done because the time series is either 
   * not present or it has gaps in it.
   * <p>  
   * Since there are many ways to form a beam from these results, the user can use the
   * FKResult structure to form a beam.  The common ways we form beams at the USGS
   * are available from the various formBeam????() methods in this routine.  The configured
   * beam method is formed automatically and can be accessed via getBeam() after calling
   * this routine.
   * <p>
   * When the channels are loaded (and enough channels contain the whole time series), then
   * the FFTs are computed on all of the FKSeismograms.  At this point the FKTimeSeries
   * only contains the transforms and the original data is lost.
   * 
   * @param t The Gregorian time of the first sample desired
   * @param npts The number of points desired.
   * @param nchanOK Minimum number of channels complete to actually compute the FK
   * @return Number of channel successful computed, if zero, data not yet present, if less than 0 number of channels is too small
   * @throws IOException if opening a QueryRing does
   */
  public int getTimeseries(GregorianCalendar t, int npts, int nchanOK) throws IOException {
     // need to initialize, must be the first call
    if(rings == null) 
      initRings(t);
    
    // Reserve the ArrayList for return and get the data for each channel
    // THe data must be converted to double and put in the ring buffer
    // NOTE : As an optimization we may want to get a set of seismograms pre allocated
    // and return them over and over if they are discarded quickly.
    prta("Load data for "+Util.ascdatetime2(t)+" npts="+npts+" ch.size="+channels.size());
    //ArrayList<FKSeismogram> ts = new ArrayList<>(channels.size());
    int ngood=0;
    long stack;
    long fkcomp;
    long fetch=System.currentTimeMillis();
    for(int i=0; i<channels.size(); i++) {
      if(d.length < npts) throw new ArrayIndexOutOfBoundsException("Change of length from d.length to npts not supported");  // if previous scratch space was too small, expand it
      
      // nret is the number of points returned, if it less the npts, there is not enough data
      // We do not want channels which are not complete, contain gaps, or are not used.
      channels.get(i).setUse(true);
      int nret = rings.get(i).getDataAt(t, npts, d, false);
      // look for FILL_VALUE in  return and declare a gap if one is found, look from end where it is most likely
      boolean hasGap = false;
      for(int j=npts-1; j>=0; j--) {
        if(d[j] == QueryRing.FILL_VALUE) {
          // If we are near real time any gap means wait and hope it comes in - return no data
          if(System.currentTimeMillis() - t.getTimeInMillis() < fkparms.getLatencySec()*1000) return 0; // data is close to realtime - wait needed
          channels.get(i).setUse(false);
          hasGap=true;          // not near real time, mark not to use this channel
          break;
        }
      }
      // If we got the right amount of data and its gap free, create the FKSeismogram for it
      if(nret == npts && !hasGap) {
        // Note this loads the FKSeismogram and computes the FFT
        ts.get(i).load(t.getTimeInMillis(), npts, d);
        ngood++;
      }
      else channels.get(i).setUse(false);
    }
    fkcomp = System.currentTimeMillis();
    fetch = fkcomp - fetch;    
    // We have all of the data per the policy, if there are enough channels compute the FK
    if(ngood >= nchanOK) {
      fkCompute(ts,fkparms, fkresults);
      stack = System.currentTimeMillis();
      fkcomp = stack - fkcomp;
      // Here we form the beams based on the selected beam method
      switch(fkparms.getBeamMethod()) {
        case 0:
          formBeamTimeDomain(beam);
          break;
        default:
           prta("Unknown beam method = "+fkparms.getBeamMethod()+" use normal type");
           formBeamTimeDomain(beam);
      }
      int bnpts = fkparms.getBeamlen();
      stack = System.currentTimeMillis() - stack;

      if(bmout != null) {
        GregorianCalendar tt  = new GregorianCalendar();
        tt.setTimeInMillis(t.getTimeInMillis());
        for(int i = 0; i < bnpts; i++ ){
          StringBuilder date = Util.ascdate(tt);
          StringBuilder time = Util.asctime(tt);
          bmout.println(i*fkparms.getDT()+" "+beam[i]+" "+fkparms.getDT()+" "+date+" "+time);
          tt.add(Calendar.MILLISECOND, (int)(1000.0*fkparms.getDT()));
        }
      }
    }
    else {

      
      prta("getTS: #chan short  < ngood="+ngood+" of "+channels.size());
      return -ngood;
    }    // There are not enough good ones.
    prta("getTS: all OK fetch="+fetch+" fkcomp="+fkcomp+" stack="+stack+" ngood="+ngood+
            " of "+channels.size());
    if(fetch+fkcomp+stack > 30000) 
      prta("***** cycle time dangerously long! ");
    return ngood;
  }
  
  // variables related to beam formation and statistics.
  int [] offset;
  long [] shiftedTime;
  private GregorianCalendar gtmp;
  private boolean beamOn;
  private long beamOnTime;
  private long beamEndTime;
  private double navg;
  private double sumazm;
  private double sumslow;
  private double maxazm;
  private double minazm;
  private double minslow;
  private double maxslow;
  private double maxsnr;
  private double slowAtMax;
  private double azmAtMax;
  private void addBeamJson(boolean triggered, FKResult fkresult) {
    if(triggered && fkresult.getNAvg() > 10) {// beam just turned on
      if(!beamOn) {
        beamOn = true;
        beamOnTime=fkresults.getBegTime();
        navg=0;
        maxazm=0;
        minazm=360;
        minslow=Double.MAX_VALUE;
        maxslow=0;
        maxsnr=0;
        slowAtMax=0.;
        azmAtMax=0.;
        sumslow=0.;
        sumazm=0.;
      }
      // Is this a similar beam
      navg++;
      sumslow += fkresult.getMaxSlw();
      sumazm += fkresult.getAzi();
      if(fkresult.getPowerRatio() > maxsnr) {
        maxsnr = fkresult.getPowerRatio();
        slowAtMax = fkresult.getMaxSlw();
        azmAtMax = fkresult.getAzi();
      }
      minazm = Math.min(fkresult.getAzi(), minazm);
      maxazm = Math.max(fkresult.getAzi(), maxazm);
      minslow = Math.min(fkresult.getMaxSlw(), minslow);
      maxslow = Math.max(fkresult.getMaxSlw(), maxslow);
    }
    else {
      if(beamOn) {      // This is the turn off of good beam
        if(filterPicker == null) 
              filterPicker = new CWBFilterPicker6(cmd, "CWBFK-"+reference.getChannel().replaceAll(" ", "_"), null);
        beamOn = false;
        beamEndTime= fkresults.getBegTime();
        /*long id = PickerManager.beamToDB(filterPicker.getAgency(), filterPicker.getAuthor(), 
                beamOnTime, beamEndTime,  reference.getChannel(), 0,(int) Math.round(azmAtMax), 
                (int) Math.round((maxazm - minazm)/2.), slowAtMax, (maxslow-minslow)/2., maxsnr, par);*/
        prta(reference.getChannel()+" Beam found "+Util.ascdatetime(beamOnTime)+" "+((beamEndTime-beamOnTime)/1000.)+
                " azm="+Math.round(azmAtMax)+" slow="+Util.df22(slowAtMax));
        /*JsonDetectionSender sender = PickerManager.getJSONSender();
        if(sender != null) {    // Beams are now part of the pick message
          if(beamOnTime < earlyLimit || beamEndTime < earlyLimit) {
            prta("**** Beam found but times are not set!  skip...");
          }
          else {
            sender.sendBeam(Util.getSystemNumber()+"_"+id, 
                filterPicker.getAgency(), filterPicker.getAuthor(), 
                beamOnTime, beamEndTime,  reference.getChannel().replaceAll("_", " "), 0, Math.round(azmAtMax), 
                (int) Math.round((maxazm - minazm)/2.), slowAtMax, (maxslow-minslow)/2.);
          }
        }*/
        prta("picker state before="+filterPicker);
        if(!filterPicker.isAlive()) {
            filterPicker = new CWBFilterPicker6(cmd, "CWBFK-"+reference.getChannel().replaceAll(" ", "_"),null);
            prta("**** picker is not alive.  Recreate it!");
        }
        double aszerr = (maxazm-minazm);      // Assume they are in thr right order (assume they are not spread over more than 180 degrees!)
        if(aszerr > 180.) aszerr -= 360.;     // if not its bigger than 180 degrees so reduce to correct value
        
        filterPicker.runSegment(beamOnTime, 60., 0,   // run from beam on time (less warmup) to 60 seconds after beam on time
              (int) Math.round(azmAtMax),  slowAtMax, aszerr/2., maxsnr); // azmuth err est, powerRatio
        
        prta("picker state ** beam after ="+filterPicker+" "+reference.getChannel()+" bazm="+azmAtMax+" slow="+slowAtMax+" pwr="+maxsnr);
      }
    }
  }
   /**
   * Given an array of seismograms and FK results, compute the time domain beam.  This is the code
   * to form the beam in the time domain rather than from the frequency domains.
   * 
   * @param beam Array to put resulting beam
   */
  public void formBeamTimeDomain(double [] beam) {
    if(offset == null) {
      offset = new int [ts.size()];
      shiftedTime  = new long[ts.size()];
      gtmp = new GregorianCalendar();
    }
    
    int nt     = fkparms.getBeamlen();      // number of samples per trace
    int nf     = fkparms.getBeamlen();      // number of fourier coefficients (how can this not be fft len?)
    //nseis  = ts.size();
    double phi = -1.;
    int tsindex = -1;
    for(int i=0; i<ts.size(); i++) if(ts.get(i).getStarttime() > 86400000L) {tsindex=i; break;}
    if(tsindex != 0) prta("** formBeamTimeDomain tsindex="+tsindex);
    double dt = ts.get(0).getDT();         // time domain sample interval
    if(fkresults.doStatistics()) {
      prta(" Trigger: beamtime="+Util.ascdatetime(ts.get(tsindex).getStarttime())+" azm="+Util.df21(fkresults.getAzi())+
            " sxmax[0]="+Util.df23(fkresults.getSXmax()[0])+
            " symax[0]="+Util.df23(fkresults.getSYmax()[0])+
            " minsnr="+fkparms.getSNRLimit()+" rat="+
            Util.df22(fkresults.getPowerRatio()));
      lastAzimuth= fkresults.getAzi();
      lastAzimuthTime=ts.get(tsindex).getStarttime();
      phi = fkresults.getAzi()*torad;
      addBeamJson(true, fkresults);
  
    }    // Something looks to be there
    else {
      if(ts.get(tsindex).getStarttime() - lastAzimuthTime < 240000) phi = lastAzimuth*torad;// use the last good one for up to 4 minutes
      addBeamJson(false, fkresults);

    }
    /*	public Beam(String newID, String newSiteID, String newStation,
			String newChannel, String newNetwork, String newLocation,
			String newAgencyID, String newAuthor, Date newStartTime,
			Date newEndTime, Double newBackAzimuth, Double newBackAzimutherror,
			Double newSlowness, Double newSlownessError) {*/
    fkresults.setAziApplied(phi);
    FKArray.dzero(beam, nt);
    int nchan=0;
    for( int i = 0; i < ts.size(); i++ ) {
      if( ts.get(i).getChannel().getUse() ) {
        nchan++;
        if(phi >= 0.) {
          double xrel = this.getChannelList().get(i).getXrel();
          double yrel = this.getChannelList().get(i).getYrel();
          double timeDelay = 0.;
          if(phi >= 0.) timeDelay = (-xrel * sin(phi) -yrel * cos(phi))*fkresults.getMaxSlw(); // time delays only if significant energy
          offset[i] = (int) ((timeDelay / ts.get(i).getDT()) + Math.signum(timeDelay)*0.5);
          shiftedTime[i] = ts.get(i).getStarttime() + (long) (offset[i]*ts.get(i).getDT() * 1000. + Math.signum(timeDelay)*0.5);
          gtmp.setTimeInMillis(shiftedTime[i]);
          if(dbg) 
            prta(i+" Form Beam for "+ts.get(i).getChannel()+" azm="+Util.df21(phi/torad)+" slw="+Util.df23(fkresults.getMaxSlw())+
                  " offset="+offset[i]+" delay="+Util.df23(timeDelay)+
                " start="+Util.ascdatetime2(gtmp)+" "+Util.ascdatetime2(ts.get(i).getStarttime()));
        }
        else {
          gtmp.setTimeInMillis(ts.get(tsindex).getStarttime());
        }
        try {
          if(this.getChannelTimeSeries(i, gtmp, nt, d) ) {
            for(int j=0; j<nt; j++) {
              beam[j] += d[j];
            } 
          }
        }
        catch(IOException e) {
          e.printStackTrace(par.getPrintStream());
        }
      }
    }        
    for(int i=0; i<nt; i++) beam[i] = beam[i]/nchan;
  }
    /** Get a single section of time series from the ith channels 
   * 
   * @param ich The channel index for the channels desired
   * @param g The time as a gregorian Calendar
   * @param npts The number of points to return
   * @param d An array of ints to receive the answer
   * @return True if the data has no gaps and is complete
   * @throws IOException 
   */
  public boolean getChannelTimeSeries(int ich, GregorianCalendar g, int npts, int [] d) throws IOException  {
     int nret = rings.get(ich).getDataAt(g, npts, d, false);
      // look for FILL_VALUE in  return and declare a gap if one is found
     boolean hasGap = false;
     for(int j=0; j<npts; j++) if(d[j] == QueryRing.FILL_VALUE) {hasGap=true; break;}
     return !hasGap && nret == npts;
   
  }
  double [] x;
  double [] y;
  /**
   * Compute the FK grid
   * @param s ArrayList of seismograms
   * @param fkparms The configuration object
   * @param fkresults An FKResult object to return results in
   * @return results are returned to the object FKResult
   */
  public FKResult fkCompute(ArrayList<FKSeismogram> s, FKParams fkparms, FKResult fkresults) {
    int    i, ii, j, k, l, nn;
    int    n1, n2;
    int    slow_limit;
    double fl, fh, nyquist;
    double delta_slow;
    double norm, omega;
    double deltaT, deltaF;
    double ysy, xsx, spx, spy, tx, ty;
    double f, sum, ph;

    int nk = fkparms.getNK();
    double kmax = fkparms.getKmax();
    slow_limit = ( nk - 1 ) / 2;
    delta_slow = -kmax / (double) slow_limit;     // negative since the index is also negative
  //double [] rr  ;
    //double [] im ;
    // Only the first time do we need to setup xrel and yrel
    boolean isNew=false;
    if(x == null) {
      x   = new double [s.size()];
      y   = new double [s.size()];
      isNew=true;
    }
    if(x.length < s.size() ) {
      x   = new double [s.size()];
      y   = new double [s.size()];    
      isNew=true;
    }
    // get the relative xrel and yrel distances before computing the FK
    if(isNew) {
      for( i = 0; i < s.size(); i++ ) {                 
        x[i] = s.get(i).getChannel().getXrel();
        y[i] = s.get(i).getChannel().getYrel();
      }
    }
    double [] kxy = fkresults.getKxy();
    if(kxy.length < nk*nk) {
      prta("FKOutput does not  have enought space.!! nk="+nk+" nk*nk="+nk*nk+" size="+kxy.length);
      kxy = new double[nk*nk];    // note this will replace the kyx in fkresults when they are posted below
      fkresults.setKxy(kxy);
    }
    
    //for( i = 0; i < nk*nk; i++) kxy[i] = 0.0
    dzero(kxy, nk*nk);
     
    nn     = s.get(0).getNFsamp();     // number of samples per spectra
    deltaT = s.get(0).getDT();         // time domain sample interval
    
    deltaF = 1.0 / ( nn * deltaT);
    nyquist = 1.0/(2.0*deltaT);
    //n1 = 0;
    //n2 = nn/2;
    
//  Find the indices closest to the specified passband 
    
    fl = fkparms.getFKLpc();
    fh = fkparms.getFKHpc();
    if(fl > nyquist || fl < 0.0) fl = nyquist;
    if(fh > nyquist || fh < 0.0) fh = 0.0;
    if( fh > fl) fh = 0.0;
    if( fl < fh) fl = nyquist;
    n1 = (int) ( fh  / deltaF );       // Compute the indice of the low frequency corner
    n2 = (int) ( fl  / deltaF ) + 1;   // Compute the indice of the high frequency corner
        
// Recompute the passband (fl,fh) passed on the indices
        
    fh  = deltaF * (double) n1;
    fl  = deltaF * (double) n2;
    
    // norm is sum of the squares of all of the coefficients DCK: this can be done without the Real and imag separately!
    norm = 0.0;
    int nnorm=0;
    for( i = 0; i < s.size(); i++ ) {
      if( s.get(i).getChannel().getUse()) {
        nnorm++;
        //rr = s.get(i).getReal();
        //im = s.get(i).getImag();
        //for(j=n1; j<n2; j++) norm=norm+(rr[j]*rr[j]+im[j]*im[j
        double [] fc = s.get(i).getData();
        for(j = n1*2; j<n2*2; j++) norm += fc[j]*fc[j];
      }
    }
    fkresults.setPowerNorm(norm/nnorm);   // Average power for one seismogram.
    
    norm = (double) nk*nk * norm;     // This makes this sum kxy come out to 1.

    ii = 0;
    
    fkresults.clear();        // Its a new cycle, clear out the results.
    for( i = -slow_limit; i <= slow_limit; i++ ) {
      ysy = (double) i * delta_slow;

       //Loop over slowness in the xrel (east-west) direction

      for( j = -slow_limit; j <= slow_limit; j++ ) {
        xsx = (double) j * delta_slow;
        //Store coordinate phase contribution in tables
        sum = 0.0;
        for( f = fh, k = n1; k < n2; k++, f+=deltaF ) {

          omega = twopi * f;
          tx = 0.0;
          ty = 0.0;

          for( l = 0; l < s.size(); l++) {

            if( s.get(l).getChannel().getUse()) {
              ph = -omega * (xsx*x[l]+ysy*y[l]); // w* r . s or w*time shift which is radians of phase shift
              //spx = Math.cos(ph);     // basicall this phase shift as complex(spx,spy)
              //spy = Math.sin(ph);
              spx = cos(ph);     // basicall this phase shift as complex(spx,spy)
              spy = sin(ph);    //  Note this is using the approximation sin and cosine

              tx = tx+s.get(l).getRval(k)*spx-s.get(l).getIval(k)*spy;
              ty = ty+s.get(l).getRval(k)*spy+s.get(l).getIval(k)*spx;
            }
          }
          sum = sum + (tx*tx + ty*ty);
        }
        kxy[ii] = sum / norm;       // So the powers have been normalized by sum of all power so a big power is near 1.
        fkresults.chkMax(kxy[ii],xsx, ysy);
        ii++;
      }
    }

    
    fkresults.setLpcorner(fl);
    fkresults.setHpcorner(fh);
    //fkresults.setKxy(kxy,nk);    Note we are using the fkresults kxy directly - no need to changes this
    //
    // Define the time window over which the FK was computed.  The first seismogram in the ArrayList
    // is the reference channel
    //
    fkresults.setBegTime(s.get(0).getStarttime());
    fkresults.setTlength(s.get(0).getDT()*(s.get(0).getNsamp()-1));
    
    
    // Compute the maximum slowness and power information 
    //slfkms(kxy,nk,kmax,fkresults);  // This is now done with fkresults.chkMax()

    if(fkparms.getDebug()) { 
      gtmp.setTimeInMillis(s.get(0).getStarttime());
      StringBuilder date0 = Util.ascdate(gtmp);
      StringBuilder time0 = Util.asctime(gtmp);
      gtmp.add(Calendar.MILLISECOND,(int)(1000.0*fkresults.getTlength()));
      StringBuilder date1 = Util.ascdate(gtmp);
      StringBuilder time1 = Util.asctime(gtmp);
      fksum.println(fkresults.getPower()[0]+" "+fkresults.getAzi()+" "+fkresults.getSXmax()[0]+" "+fkresults.getSYmax()[0]+" "+
              fkresults.getMaxSlw()+" "+date0+" "+time0+" "+date1+" "+time1+" (power,azi,sxmax,symax,maxslw,time0,time1)");
      fkgrid.println("VARIABLES = \"Sx(s/deg)\", \"Sy(s/deg)\", \"Power\"");
      fkgrid.println("ZONE I="+nk+", J="+nk+", F=POINT");

      ii = 0;
      slow_limit = ( nk - 1 ) / 2;
      delta_slow = -kmax / (double) slow_limit;
      for( i = -slow_limit; i <= slow_limit; i++ ) {
        ysy = (double) i * delta_slow;
        for( j = -slow_limit; j <= slow_limit; j++ ) {
          xsx = (double) j * delta_slow;
          fkgrid.println(xsx+" "+ysy+" "+kxy[ii]);
          ii++;
        } 
      }   
    }
    return fkresults;

    }
  // This code was adapted for the fortran code (slfkms.f by Kvaerma)

    private void slfkms(double [] wrkbuf, int nk, double kmax, FKResult a)
    {
    int i, ii, j, jj, k;
    int slow_limit;
    double delta_slow, value, ysy, xsx;
    double power, azi, sxmax, symax, maxslw;

    k = 0;
    power = 0.0;
    sxmax = symax = 0.0;

    slow_limit = ( nk - 1 ) / 2;
    delta_slow = -kmax / (double) slow_limit;
    
    for( ii= 0, i = -slow_limit;  i <= slow_limit; i++, ii++ ) {
      ysy = (double) i * delta_slow;
      for( jj = 0, j = -slow_limit;  j <= slow_limit; j++, jj++ ) {
        xsx = (double) j * delta_slow;
        value = wrkbuf[k];

        if( value >= power ) {	
          symax = ysy;
          sxmax = xsx;
          power = value;
        }
        k = k + 1;
      }
    } 

    azi = (Math.atan2(sxmax,symax)/pi)*180.0;
    if( azi < 0.0 ) azi = azi + 360.0;
    if( azi == 360.0 ) azi = 0.0;
	
    maxslw = Math.sqrt(symax*symax + sxmax*sxmax);
    
    a.setAzi(azi);
    a.setMaxSlw(maxslw);
    if(Math.abs(power - a.getPower()[0]) > 0.0000001) 
      Util.prta(" *** not equal! power="+Util.df24(power)+" power[0]="+Util.df24(a.getPower()[0])+
            " sxmax="+sxmax+" sxmax[0]="+a.getSXmax()[0]+
            " symax="+symax+" symax[0]="+a.getSYmax()[0]+" "+(power-a.getPower()[0])+" "+(sxmax - a.getSXmax()[0])+" "+(symax-a.getSYmax()[0])+
            " "+Util.df24(a.getPower()[0] - a.getPower()[1])+" "+Util.df24(a.getPower()[0]-a.getPower()[2]));

    a.setPower(power);
    a.setSXmax(sxmax);
    a.setSYmax(symax);

  }
  // Create a table of sins to make it faster to look one up than to compute them each time.
  static final int modulus = 36000;
  static final double[] sin = new double[modulus]; // lookup table
  static { 
      // a static initializer fills the table
      // in this implementation, units are in degrees
      for (int i = 0; i<sin.length; i++) {
          sin[i]=Math.sin(twopi*i/modulus);
      }
  }
  // Private function for table lookup
  private static double sinLookup(int a) {
      return a>=0 ? sin[a%(modulus)] : -sin[-a%(modulus)];
  }

  // These are your working functions:
  public static double sin(double a) {
      return sinLookup((int)(a / twopi * modulus + 0.5f));
  }
  public static double cos(double a) {
    return sinLookup((int)((a + Math.PI/2) / twopi * modulus + 0.5f));
  }
  
  /** For manual runs you should include a -gtmp yyyy/mm/dd-hh:mm:ss and -s station.file.
     * 
     * @param args The arguments
     * <PRE>
     * switch   args              Description 
     * -gtmp   yyyy/mm/dd-hh:mm:ss Start at this time - default = real time
     * -log     filename          Use this as the log file name -default fkarray or the TAG if present
     * -config  filename          Use file file name to find the array configuration
     * -a       TAG               Use this TAG from the config file. This must be last on line.
 * </PRE>
     */
  public static void main(String [] args) {
    Util.init("edge.prop");
    Util.setModeGMT();
    if(Util.getProperty("logfilepath") == null) Util.setProperty("logfilepath", "./");
            
    QueryRing.setDebugChan("USDUG  BHZ00"); 
    String argline="";
    String logfile="fkarray";
    String configFile="fk.setup";
    String tag = "FKTEST";
    int [] methods= new int[1];
    methods[0] =0;
    GregorianCalendar gc = new GregorianCalendar();
    long current = System.currentTimeMillis();
    MiniSeedOutputFile [] files = new MiniSeedOutputFile[1];

    for(int i=0; i<args.length; i++) {
      if(args[i].equalsIgnoreCase("-log")) {logfile=args[i+1]; Util.setProperty("logfilepath", "./"); i++;}
      else if(args[i].equalsIgnoreCase("-t")) {
        current = Util.stringToDate2(args[i+1]).getTime();argline += "-t "+args[i+1]+" "; i++;
      }
      else if (args[i].equalsIgnoreCase("-config")) {
        configFile=args[i+1];argline+= "-config "+args[i+1]+" ";i++;
      }
      else if(args[i].equalsIgnoreCase("-method")) {
        String [] m = args[i+1].split(","); 
        methods=new int[m.length]; 
        files = new MiniSeedOutputFile[m.length];
        for(int j=0; j<m.length; j++) methods[j] = Integer.parseInt(m[j]);
        i++;
      }
      else if(args[i].equalsIgnoreCase("-a")) {
        tag = args[i+1];
        try {
          try (BufferedReader in = new BufferedReader(new FileReader(configFile))) {
            String line;
            while( (line = in.readLine()) != null) {
              if(line.charAt(0) == '#' || line.charAt(0) == '!') continue;
              String [] parts = line.split(":");
              if(parts[0].equalsIgnoreCase(args[i+1])) {
                tag = parts[0];
                if(logfile.equals("fkarray")) logfile=tag;
                argline+=parts[1];
                break;
              }
            }
          }
          break;
        }
        catch(IOException e) {
          e.printStackTrace();
        }
      }
      else argline = args[i]+" ";
    }
    
    Logger logger = new Logger("-empty >>"+logfile, "LOGGER");
    FKArray array = new FKArray(argline, tag, logger);
    FKParams fkparms = array.getFKParams();
    int npts = fkparms.getBeamlen();
    int [] beam = new int[npts/2];        // The new time series is this long
    StringBuilder channel = new StringBuilder(12);
    Util.clear(channel).append("IM").append((tag+"     ").substring(0,5)).
            append(array.getChannelList().get(0).getChannel().substring(7,10)).append("F").append(" ");
    
    if(fkparms.getStartTime() != null) {  
      current = Util.stringToDate2(fkparms.getStartTime()).getTime();
      Util.prta("Start time "+Util.ascdatetime2(current));
    }
    int advanceMillis =  (int) ((npts - fkparms.getOverlap())*fkparms.getDT()*1000.+0.5);  // force advance
    Util.prta("advanceMillis ="+advanceMillis);
    int nseis = array.getNActive();
    int ngood = nseis;
    for(;;) {
      try {
        // If behind real time, need to check on channels and advance by 30 seconds if none is found
        gc.setTimeInMillis(current);
        int nchan = array.getTimeseries(gc,npts, ngood);
        if( nchan <= 0) {     // Did not process this time
          if(current < System.currentTimeMillis() - fkparms.getLatencySec()*1000) {  // out of real time window, process every section
            if(-nchan >= nseis/2) {
              ngood = -nchan;
              Util.sleep(advanceMillis);
              continue;         // Go get the same time 
            }
            current +=  advanceMillis;  // force advance
            Util.prta("Advance through gap "+Util.ascdatetime2(current));
          }
          else {      // within the 5 minute window, but no data, need to sleep for some more data
            Util.sleep(Math.max(10000,advanceMillis));
          }
        }
        else {        // it processed, advance the time
          if(nchan > ngood) ngood = nchan;
          Util.prta("Time processed : "+Util.ascdatetime2(current)+" nseis="+nseis+" ngood="+ngood+" nchan="+nchan);
            // output the data
          for(int m=0; m<methods.length;m++) {
            int method = methods[m];
            channel.setCharAt(11, (char) ('0'+method));
            for(int i=npts/4; i<npts*3/4; i++) beam[i-npts/4] = (int) Math.round(array.beam[i]);
            gc.setTimeInMillis(current+advanceMillis/2);
            int micros = (int)  (gc.getTimeInMillis()%1000*1000);
            if(files != null)
              if(files[m] == null) files[m] = new MiniSeedOutputFile(channel.toString()+".ms");
            RawToMiniSeed.setStaticOutputHandler(files[m]);
            Util.prt("Add timeseries "+Util.ascdatetime2(gc)+" npts="+npts+" rt="+fkparms.getRate());
            RawToMiniSeed.addTimeseries(beam, npts/2, channel, 
                    gc.get(Calendar.YEAR), gc.get(Calendar.DAY_OF_YEAR),
                    gc.get(Calendar.HOUR_OF_DAY)*3600+gc.get(Calendar.MINUTE)*60+gc.get(Calendar.SECOND),
                    micros, fkparms.getRate(), 0, 0, 0, 0, logger);
            //RawToMiniSeed.forceout(channel);
          }        
          current += advanceMillis; // advance to next time
        }
      }
      catch(IOException e) {
        e.printStackTrace(logger.getPrintStream());
        e.printStackTrace();
      }
    }
  }
}
