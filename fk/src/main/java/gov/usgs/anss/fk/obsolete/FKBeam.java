/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */

package gov.usgs.anss.fk.obsolete;

/*
 * Beam.java testin
 *   
 *
 * Created on February 13, 2007, 10:47 AM
 *
 * This class does nothing but read in the beam parameters used in the FK analysis
 * This is mostly the passband for the generated beam
 *
 */

/**
 *
 * @author benz
 */
import gov.usgs.anss.fk.*;
import java.io.*;
import java.util.ArrayList;
import java.util.GregorianCalendar;
import java.util.Calendar;
import gov.usgs.anss.util.Util;
import gov.usgs.anss.edgethread.EdgeThread;
/**
 * This class reads in the FK beam parameters
 */
public class FKBeam {
  
  //FKChannel refchan;
  GregorianCalendar beamtime;      
  GregorianCalendar ftesttime;
  GregorianCalendar beamavgtime;
  private double [] beamavg = null;
  private double [] beam = null;
  private double [] ftest = null;
  private double [] ftestavg = null;
  private double bdt;
  private double ftdt;
  private double ftmax;

  // Computer space
  final double [] currentbeam;
  final double [] currentft;
  final double [] filteredbm;
   
  // Filtering
  HPLPFilter lp;
  HPLPFilter hp;
  PrintStream avgout = null;
  PrintStream ftavgout = null;
  PrintStream ftout = null;
  /**
   * Used in computation of phase info via sin and cos calculations
   */
  //public final static double pi=Math.PI;
  /**
   * Used in computation of phase info via sin and cos calculations
   */
  public final static double twopi = 2.0 * Math.PI;
  /**
   * Conversion from degrees to radians
   */
  public final static double torad = Math.PI / 180.0;
  final EdgeThread par;
  private final StringBuilder tmpsb = new StringBuilder(50);
  @Override
  public String toString() {return toStringBuilder(null).toString();}
  public StringBuilder toStringBuilder(StringBuilder tmp) {
    StringBuilder sb = tmp;
    if(sb == null) {
      sb=Util.clear(tmpsb);
    }
    synchronized(sb) {
      sb.append("FK Beam:").append(" lp=").append(lp).append(" hp=").append(hp).
              append(" beamtime=").append(Util.ascdatetime2(beamtime));
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
  /** sample interval of the ftest time series
   * @return ftdt
   */
  public double getFTdt() { return ftdt;}
  /** sample interval of the ftest time series
   * @param f
   */
  public void setFTdt(double f) { ftdt = f;}
  /** maximum amplitude of the ftest
   * @return ftmax
   */
  public double getFTmax() { return ftmax;}
  /** maximum amplitude of the ftest
   * @param f
   */
  public void setFTmax(double f) { ftmax = f;}
    /**
   * Beam average time series
   * @return beamavg
   */
  public double [] getBeamAvg() {return beamavg;}
  /**
   * Beam time series
   * @return beam
   */
  public double [] getBeam() {return beam;}
  /**
   * Ftest (f-statistics) time series
   * @return ftest
   */
  public double [] getFtest() { return ftest;}
    /**
   * Ftest (f-statistics) time series
   * @return ftest
   */
  public double [] getFtestAvg() { return ftestavg;}
    /**
   * Start time of beam average time series
   * @return beamtime
   */
  public GregorianCalendar getBeamAvgTime() { 
    GregorianCalendar t = new GregorianCalendar();
    t.setTimeInMillis(beamavgtime.getTimeInMillis());
    return t;
  }
  /**
   * Start time of beam time series
   * @return beamtime
   */
  public GregorianCalendar getBeamTime() { 
    GregorianCalendar t = new GregorianCalendar();
    t.setTimeInMillis(beamtime.getTimeInMillis());
    return t;
  }
  /**
   * Start time of ftest (f-statistics) time series
   * @return ftesttime
   */
  public GregorianCalendar getFtestTime() { 
    GregorianCalendar t = new GregorianCalendar();
    t.setTimeInMillis(ftesttime.getTimeInMillis());
    return t;
  }
  /**
   * Return the pointer to the object containing the high pass filter coefficients
   * @return hp
   */
  public HPLPFilter getHPFilter() { return hp; }
  /**
   * Return the pointer to the object containing the low pass filter coefficients
   * @return lp
   */
  public HPLPFilter getLPFilter() { return lp; }
  /**
   * Set the object containing the high pass filter coefficients
   * @param h object of high pass coefficients
   */
  public void setHPFilter(HPLPFilter h) { hp = h; }
  /**
   * Set the object containing the low pass filter coefficients
   * @param l object of low pass coefficients
   */
  public void setLPFilter(HPLPFilter l) { lp = l; }
   
  /**
   * Start time of beam average time series
   * @param t Time of first sample in Gregorian time
   */
  public void setBeamAvgTime(GregorianCalendar t) {beamavgtime = t;} 
  /**
   * Start time of beam time series
   * @param t Time of first sample in Gregorian time
   */
  public void setBeamTime(GregorianCalendar t) {beamtime = t;}
  /**
   * Start time of ftest time series
   * @param t Time of first sample in Gregorian time
   */
  public void setFtestTime(GregorianCalendar t) {ftesttime = t;}
   /**
   * Beam avg time series
   * @param d beam time series
   * @param n number of samples in the time series
   */
  public void setBeamAvg(double [] d, int n) { 
    if(beamavg == null) beamavg = new double [n];
    if(beamavg.length < n) beamavg = new double [n];
    System.arraycopy(d, 0, beamavg, 0, n);
}
     /**
   * Beam avg time series set it to null// DCK : is this a good idea
   */
  public void setBeamAvg() { beamavg = null;}
  /**
   * Beam time series
   * @param d beam time series
   * @param n number of samples in the time series
   */
  public void setBeam(double [] d, int n) { 
    if(beam == null) beam = new double [n]; 
    if(beam.length < n) beam = new double [n];
    System.arraycopy(d, 0, beam, 0, n);
  }
  /**
   * Ftest time series
   * @param d ftest time series
   * @param n number of samples in the time series
   */
  public void setFtest(double [] d, int n) {
    if(ftest == null) ftest = new double [n]; 
    if(ftest.length < n) ftest = new double [n];
    System.arraycopy(d, 0, ftest, 0, n);
  }
    /**
   * Ftest average time series
   * @param d ftest time series
   * @param n number of samples in the time series
   */
/*  public void setFtestAvg(double [] d, int n) {
    ftestavg = new double [n]; 
    for( int i = 0; i < n; i++) ftest[i]=d[i];    // DCK: this is clearly in ERROR
  }*/
    /**
   * Ftest time series
   */
  public void setFtest() {ftest = null;}        // DCK: is this a good idea
      /**
   * Ftest average time series
   */
  public void setFtestAvg() {ftestavg = null;}    // DCK: is this a good idea?
    /**
   * Sample interval of beam time serires
   * @return bdt sample interval (in sec)
   */
  public double getBDt() { return bdt;}
  /**
   * Sample interval of beam time serires
   * @param b sample interval (in sec)
   */
  public void setBDt(double b) { bdt = b;}

  // DCK: This code looks to be abandoned
  private void filterBeam(double [] b, double dt, double hpc, double lpc, int npoles, int npasses, boolean hpp, boolean lpp) {
    int n;
    n = b.length;
      if( hpp ) {
        hp = new HPLPFilter(dt,hpc,npoles,true);
        hp.Apply(b,n,npasses);
      }
      if( lpp ) {
        lp = new HPLPFilter(dt,lpc,npoles,false);
        lp.Apply(b,n,npasses);
      }
  }
  private void makeHPFilter(double dt, double hpc, int np) {
    hp = new HPLPFilter(dt, hpc, np, true);
  }
  private void makeLPFilter(double dt, double lpc, int np){
    lp = new HPLPFilter(dt,lpc, np, false);
  }
  private void makeFilter(double dt, double hpc, double lpc, int np) {
    hp = new HPLPFilter(dt, hpc, np, true);
    lp = new HPLPFilter(dt, lpc, np, false);
  }
    /**
   * Construct a Beam object
   * @param lp low pass corner
   * @param hp high pass corner
   * @param np number of poles
   * @param npass number of passes
   * @param fl filter type (=0, no filtering; =1, high pass; =2, low pass; =3 band pass
   * @param ft number of samples used in the the ftest averaging
   */
  /*public FKBeam() {
  }*/
  /**
   * This constructor uses Harley's testing files to create a beam
   * @param fkparms The object holding this FKs parameters.
   * @param array The FKArray configured for this FK 
   * @param parent The logging EdgeTHread to use
   */
  public FKBeam(FKParams fkparms, FKArray array, EdgeThread parent) {
    par = parent;
    double dt = 1./array.getRate();
    if( fkparms.getHpass() && hp == null ) makeHPFilter(dt,fkparms.getTDHpc(),fkparms.getNpoles());
    if( fkparms.getLpass() && lp == null ) makeLPFilter(dt,fkparms.getTDLpc(),fkparms.getNpoles());
    currentbeam = new double [fkparms.getBeamlen()];
    filteredbm = new double [fkparms.getBeamlen()];
    currentft = new double [fkparms.getBeamlen()];    
    
    try {
      if(fkparms.getDebug()) {
        avgout = new PrintStream("beamavg.out");
        ftavgout= new PrintStream("ftestavg.out");
        ftout = new PrintStream("ftest.out");
      }
    } 
    catch(FileNotFoundException e) {
        prta(" **FKBeam.FKBeam(): Cannot open logging file "+e);
        //System.exit(1);
    }
    
  }
  /**
   * Given an array of seismograms and FK results, compute the time domain beam
   * @param s ArrayList s: list of seismograms and spectra used in the FKAnalysis
   * @param fkparms FK results object
   * @param fkp FKResult FK parameters generated by the FK analysis, for this routine the parameters for the fk search are done.
   */
  public void beamCompute(ArrayList<FKSeismogram> s, FKParams fkparms, FKResult fkp)
  {

    int i, ii, j, k, nf, nt;
    int finish = 0;
    int n1, n2, nsftest;
    int nseis;
    int n_half;
    double sum;
    double tt, c1;
    double beam_ev, fdt, ftestmax;
    double dt, df;
    double xrel, yrel;

    double [] seis;
    double [] residual;
    
    nt     = fkparms.getBeamlen();      // number of samples per trace
    nf     = fkparms.getBeamlen();      // number of fourier coefficients (how can this not be fft len?)
    dt     = s.get(0).getDT();         // time domain sample interval
    //nseis  = s.size();
    for(nseis=0, i = 0; i < s.size(); i++ ) {
      if( s.get(i).getChannel().getUse() ) nseis++;
    }
    
    df = 1.0 / ( nf * dt);
    
    formbeam(currentbeam, true, s, fkp);        // currentbeam is the shifted composite beam
    
    // To compute the residuals we need the current beam filtered???? DCK
    //for( i = 0; i < nt; i++ ) filteredbm[i] = currentbeam[i]; 
    System.arraycopy(currentbeam, 0, filteredbm, 0, nt);
    if( fkparms.getHpass() || fkparms.getLpass() ) taper(filteredbm,nt,0.05);
    if( fkparms.getHpass() ) hp.Apply(filteredbm,nt,fkparms.getNpasses());
    if( fkparms.getLpass() ) lp.Apply(filteredbm,nt,fkparms.getNpasses());

    residual = new double [nseis*nt];
    seis = new double [nt];
    
    for(ii=0, i = 0; i < s.size(); i++) {
      if( s.get(i).getChannel().getUse()) {
        xrel = s.get(i).getChannel().getXrel();
        yrel = s.get(i).getChannel().getYrel();
        // shift the original trace
        shiftseismogram(seis,xrel,yrel,fkp.getSXmax()[0],fkp.getSYmax()[0],s.get(i).getData(),nf,df);
        //shiftseismogram(seis,xrel,yrel,fkp.getSXmax()[0],fkp.getSYmax()[0],s.get(i).getReal(),s.get(i).getImag(),nf,df);

        if( fkparms.getHpass() || fkparms.getLpass() ) taper(seis,nt,0.05);
        if( fkparms.getHpass() ) hp.Apply(seis,nt,fkparms.getNpasses());
        if( fkparms.getLpass() ) lp.Apply(seis,nt,fkparms.getNpasses());

        for(j = 0; j < nt; j++ ) {
          k = j + ii*nt;
          residual[k] = seis[j] - filteredbm[j];
          residual[k] = residual[k] * residual[k];
        }
        ii = ii + 1;
      }
    }

    for( j = 0; j < nt; j++ ) {
      for( sum = 0.0, i = 0; i < nseis; i++ ) {
        k = j + i*nt;
        sum = sum + residual[k];
      }
        residual[j] = sum;
    }
    
    c1 = (double)(nseis-1)/(double)nseis;
    n1 = 0;
    n2 = 5;  // DCK DEBUG : fkparms.getFTlen();      // This is what Hydra calls smooth
    n_half = n2 / 2;
    if( n_half == 0 ) n_half = 1;
    k = 0;
    while( n1 < nt && finish <= 1 ) {
      for( j=0, beam_ev=0.0, sum=0.0, i = n1;  i < n2; i++ ) {
        beam_ev = beam_ev + (filteredbm[i] * filteredbm[i]);
        sum = sum + residual[i];
        j++;
      }
      currentft[k] = c1 * beam_ev / sum;
      n1 = n1 + n_half;
      n2 = n2 + n_half;
      if( n2 > nt ) {
          n2 = nt;
          finish++;
      }
      k++;
    }

    nsftest = k;
    GregorianCalendar ftime = new GregorianCalendar();
    ftime.setTimeInMillis(s.get(0).getStarttime());
    fdt = dt;
    if( fkparms.getFTlen() != 1 ) {
     fdt = ( fkparms.getFTlen() + 1 ) * dt / 2.0;
     ftime.add(Calendar.MILLISECOND,(int)(fdt*1000));
    }
    ftestmax = currentft[0];
    for( i = 1; i < nsftest; i++ ) {
      if( currentft[i] > ftestmax ) ftestmax = currentft[i];
    }
    
    if(ftout != null) {
      for(tt=0.0, i = 0; i < nsftest; i++, tt=tt+fdt) {
        ftout.println(tt+" "+currentft[i]+" "+fdt);
      }
    }
    
    GregorianCalendar currenttime = new GregorianCalendar();
    GregorianCalendar currentftime = new GregorianCalendar();
    currenttime.setTimeInMillis(s.get(0).getStarttime());
    currentftime.setTimeInMillis(s.get(0).getStarttime());
    currentftime.add(Calendar.MILLISECOND,(int)(1000.*fdt));
    
    if( beam == null) {
        
      setBeam(currentbeam,nt);
      setFtest(currentft,nsftest);
      setBeamTime(currenttime);
      setFtestTime(ftime);
      setBDt(dt);
      setFTdt(fdt);
      setFTmax(ftestmax); 
        
    } else {
        
      //previousbeam is the previous beam
      //currentbeam is the current beam
      //make sure the two segments overlap 

      int overlap = fkparms.getOverlap();
      int npts = fkparms.getBeamlen();

      GregorianCalendar t0end = getBeamTime();
      t0end.add(Calendar.MILLISECOND,(int)(1000.0*dt*(npts-1)));

      //make sure that the current and previous beams overlap
      if( t0end.getTimeInMillis() > currenttime.getTimeInMillis() ) {

        int ftoverlap = 2 * overlap / fkparms.getFTlen();
        //double [] avg = new double [overlap];     // these now come from subroutines
        //double [] ftavg = new double [ftoverlap];
        //double [] previousbeam = new double [npts];
        //double [] previousftest = new double [nsftest];
        double [] previousbeam = getBeam();
        double [] previousftest = getFtest();
        double [] avg   = averagebeam(currentbeam,previousbeam,npts,overlap);
        double [] ftavg = averagebeam(currentft,previousftest,nsftest,ftoverlap);
        setBeamAvg(avg,overlap);
        setBeamAvgTime(currenttime);
        setBeam(currentbeam,npts);
        setBeamTime(currenttime);
        setFtest(currentft,nsftest);
        //setFtestAvg(ftavg,ftoverlap);
        setFtestTime(ftime);
        setFTmax(ftestmax);

        if(avgout != null) {
          int offset;
          GregorianCalendar t0 = new GregorianCalendar();
          t0.setTimeInMillis(getBeamTime().getTimeInMillis());
          GregorianCalendar t1 = new GregorianCalendar();
          t1.setTimeInMillis(getFtestTime().getTimeInMillis());
          for( i = 0; i < overlap; i++) {
            StringBuilder date = Util.ascdate(t0);
            StringBuilder time = Util.asctime(t0);
            avgout.println((double)(i*dt)+" "+avg[i]+" "+dt+" "+date+" "+time);
            offset = (int)Math.round(dt*1000.0);
            t0.add(Calendar.MILLISECOND,offset);
          }
          for( i = 0; i < ftoverlap; i++) {
            StringBuilder date = Util.ascdate(t1);
            StringBuilder time = Util.asctime(t1);
            ftavgout.println((double)(i*fdt)+" "+ftavg[i]+" "+getFTdt()+" "+date+" "+time);
            offset = (int)Math.round(fdt*1000.0);
            t1.add(Calendar.MILLISECOND,offset);
          }
        }
            
      } else {
        //this means that you have skip ahead, have a previous beam result, but no overlap
        //Consequently, copy the current beam and ftest results into the attributes and
        //null out the beamavg and ftestavg results
        setBeam(currentbeam,nt);
        setFtest(currentft,nsftest);
        setBeamTime(currenttime);
        setFtestTime(ftime);
        setBDt(dt);
        setFTdt(fdt);
        setFTmax(ftestmax);
        setBeamAvg();
        //setFtestAvg();
        }
        //averagetimeseries(bm,currenttime,nt,dt,"beam",fkparms.getDebug());
        //averagetimeseries(ft,ftime,nsftest,fdt,"ftest",fkparms.getDebug());
    }

}
  /** THIS LOOKS OBSOLETE or DEBUG DCK
   *@ param tseries array containing the beam or ftest time series
   *@ param bt start time of the beam or ftest time series
   *@ param nt number of samples in the beam or ftest time series
   *@ param dt sample interval of the beam or ftest time series
   *@ param type  =true, beam; =false, ftest
   */
  private void averagetimeseries(double [] tseries, GregorianCalendar bt, int nt, double dt, String type, boolean debug ) {
    
    int i, j, k, nn, b0n1, b0n2, b1n1, b1n2, offset;
    double arg, toffset, c1, c2, val;
    
    double [] tseries0;
    double [] tseriesaverage;
    
    GregorianCalendar t0 = new GregorianCalendar();

    tseriesaverage = new double [nt];
      
// 
// Get the indexing right of the two arrays
// toffset: difference between the start of the first time-series and the second time-series
//
    //String st2 = Util.asctime2(bt);
    //String st1 = Util.asctime2(getBeamTime());
    //String ft1 = Util.asctime2(getFtestTime());
    if(type.contentEquals("beam")) {
      toffset = (bt.getTimeInMillis() - getBeamTime().getTimeInMillis())/1000.0;
      tseries0 = getBeam();
    } else {
      toffset = (bt.getTimeInMillis() - getFtestTime().getTimeInMillis())/1000.0;
      tseries0 = getFtest();
    }

    nn = 0;
    b0n1 = (int) (toffset / dt);
    if( toffset < 0.0 ) {
      nn = -b0n1;
      b0n1 = 0;
    }
    b0n2 = tseries0.length;
    b1n1 = nn;
    b1n2 = b0n2-b0n1;
    if( b0n1 > b0n2 ) {
      // This means the time series dont overlap.  When this happens
      // output the time series (timeserie0) and then copy the new one (timeseries) into
      // the appropriate attribute and return
      t0.setTimeInMillis(bt.getTimeInMillis());
      if(type.contentEquals("beam")) {
        setBeamTime(t0);
        setBeam(tseries,nt);
        return;
      } else {
        setFtestTime(t0);
        setFtest(tseries,nt);
        return;
      }     
    }
      
    if(type.contentEquals("beam")) {
      for( i = 0; i < b0n1; i++ ) tseriesaverage[i] = this.beam[i];
    } else {
      for( i = 0; i < b0n1; i++ ) tseriesaverage[i] = this.ftest[i];
    }

    nn = b1n2;
    arg = Math.PI / (double) ( 2 * nn);
    for(k=0, j=b0n1, i = b1n1; i < b1n2; i++, j++, k++){
      val = (double)(i) * arg;
      c1 = 1.0 * Math.cos(val)*Math.cos(val);
      c2 = 1.0 - c1;
      tseriesaverage[b0n1+k] = c1 * tseries0[j] + c2 * tseries[i];
    }

    if(avgout != null || ftavgout != null) {
      t0.setTimeInMillis(getBeamTime().getTimeInMillis());
      nn = b0n1 + (b1n2-b1n1);
      for( i = 0; i < nn; i++) {
        StringBuilder tstr = Util.asctime2(t0);
        if(type.contentEquals("beam")) {
          avgout.println((double)(i*dt)+" "+tseriesaverage[i]+" "+dt+" "+tstr);
        } else {
          ftavgout.println((double)(i*dt)+" "+tseriesaverage[i]+" "+dt+" "+tstr);
        }
        offset = (int)Math.round(dt*1000.0);
        t0.add(Calendar.MILLISECOND,offset);
      }
    }
     
//After averaging, the remaining time series is shifted within the array and then becomes the
//beam for the next overlapping segment

    t0.setTimeInMillis(bt.getTimeInMillis());
    offset = (int) Math.round(1000.0*(dt*b1n2));
    t0.add(Calendar.MILLISECOND,offset);
    //String tstr = Util.asctime2(t0);
    nn = nt - b1n2;
    for(j = 0, i = b1n2; i < nt; i++, j++ ) tseries[j] = tseries[i]; 
    if(type.contentEquals("beam")) {
      setBeamTime(t0);
      setBeam(tseries,nn);
    } else {
      setFtestTime(t0);
      setFtest(tseries,nn);
    }     
  }

  /**
   *@param tseries array containing the current beam time series
   *@param tseries0 start time of the previous beam time series
   *@param npts number of samples in the beam time series
   *@param overlap number of samples in the overlap section
   * @return 
   */
  protected double [] averagebeam(double [] tseries, double [] tseries0, int npts, int overlap ) {
    int i;
    double arg, c1, c2, val;
    
    double [] tseriesaverage;

    tseriesaverage = new double [overlap];

    arg = Math.PI / (double) ( 2 * overlap);
    for(i = 0; i < overlap; i++){
      val = (double)(i) * arg;
      c1 = 1.0 * Math.cos(val)*Math.cos(val);
      c2 = 1.0 - c1;
      tseriesaverage[i] = c1 * tseries0[i+(npts-overlap)] + c2 * tseries[i];
    }

     
    return tseriesaverage;
//After averaging, the remaining time series is shifted within the array and then becomes the
//beam for the next overlapping segment

   
  }

  /** This forms the beam by taking all of the spectra in the seismograms and summing them up 
   * after phase shifting based on the largest power in FK space (the fk variable already contains
   * the FK results in terms of power and kx and ky values). 
   * 
   *@ param beam time series
   *@ s ArrayList of seismograms
   *@ fk FK output object
   */
  private void formbeam(double [] beam, boolean mean, ArrayList<FKSeismogram> s, FKResult fk) {
    
    int nt, nf;
    int i, j, nseis;
    double omega, ph, f;
    double dt, df;
    double tx, ty;
    double spx, spy;
    double [] x;
    double [] y;
    double [] wkb;
    
         
    nt     = s.get(0).getNsamp();      // number of samples per trace
    nf     = s.get(0).getNFsamp();     // number of samples per spectra
    dt     = s.get(0).getDT();         // time domain sample interval
    
    x   = new double [s.size()];
    y   = new double [s.size()];
    wkb = new double [2*nf];
    
    df = 1.0 / ( nf * dt);
   
    for(nseis=0, i = 0; i < s.size(); i++ ) {                 // get the relative x and y distances before computing the FK
      if(s.get(i).getChannel().getUse()) {
        x[i] = s.get(i).getChannel().getXrel();
        y[i] = s.get(i).getChannel().getYrel();
        nseis++;
      }
    }

    // For each frequency in the FK window, sum up the phase shifted fourier coefficents
    for( f = 0.0, i = 0; i < nf/2; i++, f+=df ) { // at each frequence in the FK window

      omega = twopi * f;
      tx = 0.0;
      ty = 0.0;

      for( j = 0; j < s.size(); j++) {  // for each time series channel

        if( s.get(j).getChannel().getUse()) {
          ph = -omega * (fk.getSXmax()[0]*x[j]+fk.getSYmax()[0]*y[j]);
          spx = Math.cos(ph);
          spy = Math.sin(ph);

          tx = tx+s.get(j).getRval(i)*spx-s.get(j).getIval(i)*spy;  // sum the real part
          ty = ty+s.get(j).getRval(i)*spy+s.get(j).getIval(i)*spx;  // sum the imaginary part
        }
        
      }
      wkb[2*i]   = tx;      // In the wkb we save the summed parts
      wkb[2*i+1] = ty;
    }
    
    for( i = 1; i < 2 * nf; i++ ) wkb[i] = wkb[i] / (double)nseis; // normalize for the number stacked
    fill_spectra(wkb,nf);   // This must be to setup the fourier transform array in wkb to be symmetric.
    double dtret = Fourier.four2(wkb,nf,+1,false,df);  // Inverse transform
    
    for( i = 0; i < nt; i++ ) beam[i] = wkb[2*i]; // take the time series from the real parts.
    if(mean) rmean(beam, nt);

  }
  private void rmean( double [] beam, int n) {
    double avg = 0.0;
    if( n > 0 ) {
      for( int i = 0; i < n; i++) {
          avg += beam[i];
      }
      avg = avg/n;
      for( int i = 0; i < n; i++ ) beam[i] -= avg;
    }
  }
  /*
   This simple program tapers the seismogram of npts.  
        n = number of points in the seismogram
        seis = seismogram
*/
  private static void taper(double [] seis, int n, double percent )
  {

    double f;
    //double correction = 0.0;
    //double tc;

    int nn = (int) (percent * n);

    for( int i = 0; i < nn; i++ ) {
      f = 0.5*(1.0-Math.cos(i * Math.PI / nn));
      //correction = correction + f;  // DCK this is not used?
      seis[i] *=  f;
      seis[n-i-1] *= f;
    }
    //DCK : no usedtc = n  / ( n - 2.0 *nn + 2.0 * correction);
   // setTaperCorrection(tc);
  }
  /**
   *@param seis  seismogram time series
   *@param xrel  relative distance (east-west direction)
   *@param yrel  relative distance (north-south direction)
   *@param sx    optimal slowness in the east-west direction
   *@param sy    optinal slowness in the north-south direction
   *@param fc    the fourier coefficents stored as real then imaginary parts in the double array
   *@param nf          number of samples in the frequency domain
   *@param df          sample interval in the frequency domain
   */
  public static void shiftseismogram(double [] seis, double xrel, double yrel, double sx, double sy, double [] fc, int nf, double df) {
    int i, j, nt;
    int nseis;
    double f, dt, xr;
    double ph, omega;
    double spx, spy;
    double [] wkb;
    
    wkb = new double [2*nf];
    nt = seis.length;
    
    for( i = 0; i < 2 * nf; i++ ) wkb[i] = 0.0;
    for( f = 0.0, i = 0; i < nf; i=i+2, f+=df ) {

      ph = -twopi * f * (sx*xrel + sy*yrel);
      spx = Math.cos(ph);
      spy = Math.sin(ph);
      
      wkb[i]   = wkb[i]  + fc[i]*spx-fc[i+1]*spy;
      wkb[i+1] = wkb[i+1] +fc[i]*spy+fc[i+1]*spx; 

    }    
    
    /*for( f = 0.0, i = 0; i < nf/2; i++, f+=df ) {

      ph = -twopi * f * (sx*xrel + sy*yrel);
      spx = Math.cos(ph);
      spy = Math.sin(ph);
      
      wkb[2*i]   = wkb[2*i]  + rspec[i]*spx-ispec[i]*spy;
      wkb[2*i+1] = wkb[2*i+1] +rspec[i]*spy+ispec[i]*spx; 

    }*/

/* 
Transform phase shifted seismograms into the time domain
*/
    fill_spectra(wkb,nf);
    dt = Fourier.four2(wkb,nf,+1,false,df);
    for( i = 0; i < nt; i++ ) seis[i] = wkb[2*i]; 

  }

  private static  void fill_spectra(double [] d, int n)
  {
      int i, j;
      for( i = 1; i < n/2;  i++ ) {
      j = 2*i;
      d[n+j]   =  d[n-j];
      d[n+j+1] = -d[n-j+1];
    }
  }

  private int npow2(int n)
  {
    int k, nn;
    k = 1; nn = 2;
    while(nn < n){
      nn *=2;
    }
    return(nn);
  }

}
