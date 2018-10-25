/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */

package gov.usgs.anss.fk;

import gov.usgs.anss.util.Util;
import gov.usgs.anss.edgethread.EdgeThread;
import java.io.RandomAccessFile;
import java.io.IOException;

/*
 *
 * @author benz
 */
public final class FKParams {
    private final static String COMMANDLINEHELP = 
    "Version 1.0.0\n"+
    "Description of command line arguments and definition of variables\n"+
    "fk -t \"yr/mn/dy hr:min:sec\" (not required) -s stationfile " +
    " -fl hpc/lpc/npoles/npasses/hpass/lpass -fk kmax/nk\n"+
    " -bm hpcorner/lpcorner/beamlen/overlap\n"+
    " -cwb write output into the CWB\n"+
    " -dbg write into log files\n"+
    " -l sec set the latency to wait at the realtime boundary for gap to fill before going on\n"+
    " -snr limit Set the ratio of power to average power to use for recording a detection\n"+
    " -s  stationfile: name of the stations file\n"+
    " -fl hpc/lpc/npoles/npasses/hpass/lpass   (Time domain filtering)\n"+
    "     hpc: high-pass corner used to filter the beam trace after stacking\n"+
    "     lpc: low-pass corner used to filter the beam trace after stacking\n"+
    "     npoles: number of poles in the filter\n"+
    "     npasses: number of passes in the filter\n"+
    "     hpass: t = true\n"+
    "     lpass: t = true\n\n"+
    " -fk kmax/nk \n"+
    "     kmax: maximum slowness in the beamforming\n"+
    "     nk: number of slowness in the x and y direction (make it an odd number)\n\n"+
    " -d  dt/dtoriginal (This is obsolete! rate comes from stations file) \n"+
    "     dt: sample interval requested\n"+
    "     dtoriginal: sample interval of the original data"+
    " -bm hpcorner/lpcorner[/beamlen/overlap]\n"+
    "     hpcorner: high-pass corner in the FK frequency-domain stacking (beamforming)\n"+
    "     lpcorner: low-pass corner in the FK frequency-domain stacking (beamforming)\n"+
    "     beamlen: number of samples used in the beamforming (power of 2) normall computed from beamwindow\n"+
    "     overlap: nubmer of samples in the overlap of beams (power of 2 normally 1/2 of beamlen)\n"+
    "             if beamlen=512, overlap=256 mean 50% overlap\n\n"+
    " -beamwindow secs  The minimum length of the window in seconds will be rounded up to a power of two samples\n"+
    " -ftlen   window length (in power of 2??) for averaging the ftest results (typical 8 or 16)\n"+
    " -rate hz  The digitizing rate in Hz - normally this comes from the station file so is not required";
    
  private String configFile;
  private double fkhpc;         // high pass corner for FK formation
  private double fklpc;         // low pass corner for FK formation
  private double tdhpc;         // optional IIR highpass filter corner to be applied to formed beams
  private double tdlpc;         // ditto for low pass order
  private int npoles;           // Number of poles of tdhpc and tdlpc
  private int npasses;          // number of passes of tdhpc and tdlpc
  private boolean hpass = false;// This might not be usefull any more, if true apply time domain high pass
  private boolean lpass = false;//  ditto
  private boolean debug = false;
  private boolean cwb = false;  // if true, write the data directly to a CWB

  private int beamMethod;       // 0=normal time domains stack, other might be for nth root beams
  
  //private double dt;
  //private double dtoriginal;
  private double beamWindow =10.;   // Beams are to be something this long in seconds (will be roundup up to power of two)
  private int beamlen;   // the power of two number of points which cover the beamlen, not normally set by user
  private int overlap;              // overlap - normally not set by user
  private double kmax;              // bound of the search in k
  //private double delta;
  private double rate;         // Data rate of data, not a configuration parameter but discovered from metadata
  private int nk;              // number of data point over which to search k (must be odd so k=0 is considered
  private int ftlen;           // Probably obsolete : this was used for smoothing for the ftest 
  private String stationfile;  // FIlename with station data created by the FKManager
  private String starttime;    // optional : for real time the start time comes from the state file
  private long startTimeMS = -1;
  private long endTimeMS = Long.MAX_VALUE;
  private String endtime;      // optional : only used for batch recomputing of an array.
  private String starttimefile;// This time of the last data for a real time run (only used if FK class is used
  private int latencySec=300;  // This the interval that FK will wait to get more real time data - if this is exceeded a gap is created.
  private double snrLimit=5.1;  // This is the limit in normPower/normPowerAverage to be a "trigger"
  private String cwbin="localhost";// The CWB with the data, normally we get it locally
  private int cwbinPort=2061;
  
  private final StringBuilder tmpsb = new StringBuilder(100);
  private final EdgeThread par;// The creator gives a EdgeThread to this class for logging
  @Override
  public String toString() {return toStringBuilder(null).toString();}
  public StringBuilder toStringBuilder(StringBuilder tmp) {
    StringBuilder sb = tmp;
    if(sb == null) {
      sb=Util.clear(tmpsb);
    }
    synchronized(sb) {
      sb.append("FK Parms : ").append("-s ").append(configFile).
              append(" -fl ").append(tdhpc).append("/").append(tdlpc).append("/").append(npoles).append("/").
              append(npasses).append("/").append(hpass).append("/").append(lpass).
              append(" -fk ").append(kmax).append("/").append(nk).
              append(" -bm ").append(fkhpc).append(".").
              append(fklpc).append("/").append(beamlen).append("/").
              append(overlap).append(" -ftlen ").append(ftlen).append(" -rate ").
              append(rate).append(" -beamwindow ").append(beamWindow).append(" -l ").append(latencySec).
              append(" -snr ").append(Util.df21(snrLimit));
    }
    return sb;
  }  
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
  public double getSNRLimit() {return snrLimit;}
  public void setSNRLimit(double limit) {snrLimit=limit;}
    public int getBeamMethod() {return beamMethod;}
    /** Sampling interval (sps) requested
     * @return dt
     */
    public double getDT() {return 1/rate;}
    /** Sampling interval (sps) of the original data
     * @return dtoriginal
     */
    //public double getDTOriginal() {return dtoriginal;}
    /** Sampling interval (sps) requested
     * @param d
     */
    public void setDT(double d) { rate = 1/d;}
    /** Sampling interval (sps) of the original data
     * @param d
     */
    //public void setDTOriginal(double d) { dtoriginal = d;}
    /** debug program by doing lots of prints
     * @return debug
     */
    public boolean getDebug() {return debug;}
    /** debug program by doing lost of prints
     * @param d
     */
    public void setDebug(boolean d) { debug = d; }
    /** get which CWB to get data from
     * @return cwb The timeseries source cwb
     */
    public String getTimeSeriesCWB() {return cwbin;}
    /** Return the port of the timeseries source
     * 
     * @return Seldom changed, normally 2061
     */
    public int getTimeSeriesCWBPort() {return cwbinPort;}
    /** write beam results to the CWB
     * @return cwb
     */
    public boolean getCWB() {return cwb;}
    /** write beam results to the CWB
     * @param d set CWB boolean
     */
    public void setCWB(boolean d) { cwb = d; }
    /** Get the length in seconds of the minimum FK computation interval (def=10 seconds)
     * @return The beam minimum window length
     */
    public double getBeamWindow() {return beamWindow;}
    /** sample interval of the observed data
     * @param d
     */
    public void setBeamWindow(double d) {beamWindow=d;}    /** Get the length in seconds of the minimum FK computation interval (def=10 seconds)
    /**Set the sample rate in Hz
    * @return The digitizing rate of the data
     */
    public double getRate() {return rate;}
    /** sample interval of the observed data
     * @param d
     */
    public void setRate(double d) {
      rate=d;
    }
    /** return the power of 2 number of points which covers the beam length
     * 
     * @return The power of two which is at least as long as the beam length variable
     */
    public int getNpts() {return beamlen;}
    /** sample interval of the observed data
     * @return delta
     */
    //public double getDelta() {return delta;}
    /** sample interval of the observed data
     * @param d
     */
    //public void setDelta(double d) {delta=d;}
    /** starttime of beamforming
    * @return starttime
    */
    public String getStartTime() {return starttime;}
    /** starttime of beamforming
    * @return starttime if < 0, no start time has been set
    */
    public long getStartTimeInMillis() {return startTimeMS;}
    /** starttime of beamforming in millis
    * @return starttime
    */
    public String getEndTime() {return endtime;}
    /** endTime of beamforming in millis
    * @return starttime if = Long.MAX_VALUE, no end time has been set
    */
    public long getEndTimeInMillis() {return endTimeMS;}
    /** end time of beamforming as a string
    * @param s
    */
    public void setStartTime(String s) { starttime = s;}
    /** file name of start time log file
    * @param s 
    */
    public void setStartTimeFile(String s) { starttimefile = s;}
    /** file name of start time log file
    * @return startimefile
    */
    public String getStartTimeFile() { return starttimefile;}
    /** name of station file
    * @return stationfile
    */
    public String getStationFile() {return stationfile;}
    /** name of station file
    * @param f
    */
    public void setStationFile(String f) { stationfile = f;}
    /** number of samples in the overlap segment, must be less than npts
    * recommend 1/2 of beamlen
    * @return overlap
    */
    public int getOverlap() {return overlap;}
    /** number of samples in the overlap segment, must be less than npts
    * recommend 1/2 of beamlen
    * @param o The overlap
    */
    public void setOverlap(int o) { overlap = o;}
    /** number of sample points in the beam (power of 2)
    * @return beamlen
    */
    public int getBeamlen() {return beamlen;}
    /** number of sample points in the beam (power of 2)
    * @param b The beam length
    */
    public void setBeamlen(int b) {beamlen = b;}
    /** high-pass corner frequency used in the FK stacking
    * @return hpc  
    */
    public double getFKHpc() {return fkhpc;}
    /** high-pass corner frequency used in the FK stacking
    * @param h
    */
    public void setFKHpc(double h) {fkhpc = h;}
    /** low-pass corner frequency used in the FK stacking
    * @return lpc  
    */
    public double getFKLpc() {return fklpc;}
    /** low-pass corner frequency used in the FK stackign
    * @param l
    */
    public void setFKLpc(double l) {fklpc = l;}
    /**number of poles in the time-domain filtering of the beam
    * @return npoles  
    */
    public int getNpoles() {return npoles;}
    /** number of poles in the time-domain filtering of the beam
    * @param n 
    */
    public void setNpoles( int n) { npoles = n;}
    /**number of passes in the time-domain filtering of the beam
    * @return npasses  
    */
    public int getNpasses() {return npasses;}
    /** the number of passes in the time-domain filtering of the beam
    * @param n
    */
    public void setNpasses( int n) { npasses = n;}
    /** get the boolean expression on whether to high pass filter
    * @return hpass  
    */
    public boolean getHpass() {return hpass;}
    /** booelan expression on whether to high pass filter the beam in the time-domain
     * @param h
     */
    public void setHpass(boolean h) {hpass = h;}
    /** get the boolean expression on whether to low pass filter the beam in the time-domain
    * @return lpass  
    */
    public boolean getLpass() {return lpass;}
    /** boolean expression on whether to low pass filter the beam in the time-domain
     * @param l
     */
    public void setLpass(boolean l) {lpass = l;}
    /** get the nk value, the number of points in the x and y directions.
    *@return the NK value */
    public int getNK() {return nk;}
    /** the number of FK points points in the x and y diretions
     * @param n
     */
    public void setNK(int n) { nk = n; }
    /** length (in samples) of the ftest averaging 
    *@return ftlen
    */
    public int getFTlen() {return ftlen;}
    /** length (in samples) of the ftest averaging
     * @param f
     */
    public void setFTlen(int f) { ftlen = f;}
    /**
    * low pass corner (in Hz)
    * @return lpcorner
    */
    public double getTDLpc() {return tdlpc;}
    /** low pass corner (in Hz) to filter the beam
     * @param l
     */
    public void setTDLpc(double l) { tdlpc = l;}
    /**
    * high pass corner (in Hz)
    * @return hpcorner
    */
    public double getTDHpc() {return tdhpc;}
    /** high pass corner to filter the beam
     * @param h 
     */
    public void setTDHpc(double h) {tdhpc = h;}
    /**
    * The maximum slowness used in the FK analysis
    * @return kmax
    */
    public double getKmax() {return kmax;}
    /** 
     * Get the time behind realtime to wait for the next data.
     * @return the time to wait behind real time
     */
    public int getLatencySec() {return latencySec;}
    public void setLatencySec(int sec) {latencySec=sec;}
    /** Maximum slowness used in the FK analysis
   * @param k Set maximum slowness
     */
    public void setKmax(double k) { kmax = k;}

    public FKParams(String [] commandline, EdgeThread parent) {
        par = parent;
        parseArgs(commandline);
    }
    public FKParams(String configFile, EdgeThread parent) {
      par = parent;
      readConfigFile();
      
    }
    private long configLastMod;
    private byte [] configbuf;
    private void readConfigFile() {
      if(configFile == null) return ;   // not using a config file
      long lastMod = Util.isModifyDateNewer(configFile, configLastMod);
      if(lastMod == 0) return;      // Modify date the same
      if(lastMod > 0) {
        configLastMod = lastMod;
      }
      else {prt("Config file does not exist "+configFile);}
      // Read the configuration file
      try {
        try (RandomAccessFile rw = new RandomAccessFile(configFile, "r")) {
          if(configbuf == null) configbuf = new byte[(int) rw.length()*2];
          if(configbuf.length < rw.length()) configbuf = new byte[(int) rw.length()*2];
          rw.seek(0);
          rw.read(configbuf, 0, (int) rw.length());
          String [] args = new String(configbuf, 0, (int) rw.length()).split("\\s");
          parseArgs(args);
        }
      }
      catch(IOException e) {
        prt("Config file read error "+configFile);
      }
    }
    public void parseArgs(String [] args){   
    
        int nargs;
        nargs = args.length;  
        String list [];

        if( nargs == 0 ) {
          prt(COMMANDLINEHELP);
          System.exit(1);
        }

        for( int i = 0; i < nargs; i++ ) {
          if( args[i].equalsIgnoreCase("-cwbin")) {cwbin = args[i+1]; i++;}
          if( args[i].equalsIgnoreCase("-cwbinport")) {cwbinPort = Integer.parseInt(args[i+1]); i++;}
          else if( args[i].equalsIgnoreCase("-cwb")) cwb = true;
          else if( args[i].equalsIgnoreCase("-dbg")) debug = true;
          else if( args[i].equalsIgnoreCase("-beamwindow")) {beamWindow = Double.parseDouble(args[i+1]); i++;}
          else if( args[i].equalsIgnoreCase("-rate")) {rate = Double.parseDouble(args[i+1]); i++;}
          else if( args[i].equalsIgnoreCase("-ftlen")) {ftlen = Integer.parseInt(args[i+1]); i++;} // ftest smoothing length
          else if( args[i].equalsIgnoreCase("-l")) {latencySec = Integer.parseInt(args[i+1]); i++;} // ftest smoothing length
          else if( args[i].equalsIgnoreCase("-beammethod")) {beamMethod = Integer.parseInt(args[i+1]); i++;}
          else if( args[i].equalsIgnoreCase("-snr")) {snrLimit = Double.parseDouble(args[i+1]); i++; prta("Set SNRlimit="+snrLimit);}
          else if( args[i].equalsIgnoreCase("-t")) {
              if(args.length > i+1) {
                  starttime = args[i+1];
              }
              if(starttime.length() < 10) {
                  prt(" ***** Not enough arguments in start time e.g. -t \"2008/10/08 14:34:13\"");
                  //System.exit(1);
              }
              
              else startTimeMS = Util.stringToDate2(starttime).getTime();
              i++;
          }
          else if( args[i].equalsIgnoreCase("-e")) {
              if(args.length > i+1) {
                  endtime = args[i+1];
              }
              if(endtime.length() < 10) {
                  prt(" ***** Not enough arguments in start time e.g. -t \"2008/10/08 14:34:13\"");
                  //System.exit(1);
              }
              else endTimeMS = Util.stringToDate2(endtime).getTime();
              i++;
          }
          else if( args[i].equalsIgnoreCase("-s")) {
                  stationfile = args[i+1];
                  i++;
          }
          /*if( args[i].equalsIgnoreCase("-e") || args[i].equalsIgnoreCase("-d")) {    // document says -d but code originally said -e
              if(args.length > i+1) {
                  list = args[i+1].split("\\/");
                  if(list.length < 2) {
                      throw new RuntimeException("-e dt/dtoriginal");
                  }
                  dt = Double.parseDouble(list[0]);
                  dtoriginal = Integer.parseInt(list[1]);
              }
          }*/
          else if( args[i].equalsIgnoreCase("-fk")) {    //FK parameters
            list = args[i+1].split("\\/");
            i++;
            if(list.length < 2) {
              throw new RuntimeException("-fk kmax/nk");
            }
            kmax = Double.parseDouble(list[0]);
            nk = Integer.parseInt(list[1]);
          }
          else if( args[i].equalsIgnoreCase("-fl")) {    //FK parameters
            list = args[i+1].split("\\/");
            if(list.length < 6) {
                throw new RuntimeException("-fl hpc/lpc/npoles/npasses/hpass/lpass");
            }
            tdhpc = Double.parseDouble(list[0]);
            tdlpc = Double.parseDouble(list[1]);
            npoles = Integer.parseInt(list[2]);
            npasses = Integer.parseInt(list[3]);
            if(list[4].contains("t")) hpass = true;
            if(list[5].contains("t")) lpass = true;
            if(tdhpc >= tdlpc && tdhpc != 0.) {
              throw new RuntimeException(" high and low pass corners not specificed properly");
            }
            i++;
          }
          else if( args[i].equalsIgnoreCase("-bm")) {    //FK parameters
            list = args[i+1].split("\\/");
            if(list.length < 2) {
              throw new RuntimeException("-bm hpcorner/lpcorner[/beamlen/overlap/ftestwlen]");
            }
            fkhpc = Double.parseDouble(list[0]);
            fklpc = Double.parseDouble(list[1]);
            if(list.length >= 3) beamlen = Integer.parseInt(list[2]); // normally computed from rate
            if(list.length >= 4) overlap = Integer.parseInt(list[3]); // ""
            if(list.length >= 5) ftlen = Integer.parseInt(list[4]);   // ""
            if(overlap >= beamlen && beamlen > 0) {
              throw new RuntimeException(" length of overlap >= length of beam trace");
            }
            if(ftlen != 0 && (ftlen <= 0 || ftlen > overlap)) {
                throw new RuntimeException(" ftlen not specified properly");
            }
            if(fkhpc >= fklpc) {
                throw new RuntimeException(" high and low pass corners not specificed properlay");
            }
            i++;
          }
        }
        starttimefile = stationfile+".time";
        if(rate > 0) setRate(rate);
    }
}
