/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */

package gov.usgs.anss.fk;

/*
 * FKSeismogram.java
 *
 * Created on February 13, 2007, 1:38 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

/**
 *
 * @author benz
 */
import gov.usgs.anss.util.Util;
/**
 * Class for organizing seismograms and computed the Fourier spectra
 */
public class FKSeismogram {
  /**
   * used in sin and cos calculations
   */
  public final static double pi=Math.PI;
  /**
   * Percent of taper used in analysis (5%)
   */
  public final static double percent=0.05;
  private final FKChannel chan;
  private long starttime;
  private final double dt;
  private final int nsamp;
  private int nfsamp;
  private final double [] data;   // this is the buffer area for doing the fft
  //private double [] real;
  //private double [] imag;
  private final StringBuilder tmpsb = new StringBuilder(50);
  @Override
  public String toString() {return toStringBuilder(null).toString();}
  public StringBuilder toStringBuilder(StringBuilder tmp) {
    StringBuilder sb = tmp;
    if(sb == null) {
      sb=Util.clear(tmpsb);
    }
    synchronized(sb) {
      sb.append("FK Seis:").append(chan.getChannel()).append(" ").append(Util.ascdatetime2(starttime)).
              append(" dt=").append(dt).append(" ns=").append(nsamp).append(" nf=").append(nfsamp);
    }
    return sb;
  }  
  /**
   * Number of samples in the time series
   * @return nsamp
   */
  public int getNsamp() {return nsamp;}
  /**
   * Seismogram
   * @return data
   */
  public  double [] getData() {return data;}
  /**
   * Sample interval of seismogram
   * @return dt
   */
  public double getDT() {return dt;}
  /**
   * Number of samples in the Fourier spectra
   * @return nfsamp
   */
  public int getNFsamp() {return nfsamp;}
  /**
   * Real compoment of the spectra
   * @return real
   */
  //public double [] getReal() { return real; }
  /**
   * Get real component value of spectra via index
   * @param i index
   * @return real value
   */
  public double getRval(int i) { return data[i*2];}
  /**
   * Imaginary component of spectra
   * @return imag
   */
  //public double [] getImag() { return imag; }
  /**
   * Get imaginary component value of spectra via index
   * @param i index
   * @return imag[i]
   */
  public double getIval(int i) { return data[i*2+1];}
  /**
   * Number of sample in the fourier spectra
   * @param n nubmer of samples
   */
  public void setNFsamp(int n) { nfsamp = n; }
  /**
   * Channel description
   * @return channel object
   */
  public FKChannel getChannel() {return chan;}
  /**
   * Start time of time series
   * @return t
   */
  public long getStarttime() { 
    return starttime;
  }


    /**
   * Construct a FKSeismogram object
   * @param st start time (Gregorian time)
   * @param ch Channel information for a station
   * @param dtime sample interval for a channel
   * @param np number of samples in the time series
   * @param d time series
   */
/*  public FKSeismogram(FKChannel ch, GregorianCalendar st, double dtime, int np, double [] d)  {
    chan=ch; dt = dtime; nsamp=np; starttime=st.getTimeInMillis(); 
    data = new double[nsamp*2];
    System.arraycopy(d, 0, data, 0, nsamp);
  }*/
  /**
   * Construct a FKSeismogram object which will ber reloaded by load()
   * @param ch Channel information for a station
   * @param dtime sample interval for a channel
   * @param np number of samples in the time series
   */  
  public FKSeismogram(FKChannel ch, double dtime, int np) {
    chan = ch; dt = dtime; nsamp = np; nfsamp = np;
    data = new double[nsamp*2];
  }
 /* public FKSeismogram(FKChannel ch, long st, double dtime, int np, double [] d)  {
    chan=ch; dt = dtime; nsamp=np; starttime=st; 
    data = new double[nsamp];
    System.arraycopy(d, 0, data, 0, nsamp);
  }*/
  public void load(long start, int npts, int [] d) {
    starttime=start;
    if(npts != nsamp) {
      throw new RuntimeException("Attempt to change length of FKSeismogram!");
    }
    // perform mean and taper on integer seismogram
    rmean(d);
    staper(d);
    // FFT needs the data in only the real parts of data
    for(int i = 0; i < npts; i++ ) data[2*i] = d[i];
    
    //double df = Fourier.four(data,npts,-1,true,dt);
    double df = FFT.doFFT(data, npts, -1, true, dt);

    //setSpectra( data );
  }
  /**
   * Set the real and imaginary components of spectra
   * @param sp spectra computed by the Fourier routine, real and imag parts stored in alternate locations
   */
  /*public void setSpectra(double [] sp) {
 
  //The fourier transform routine computes the spectra by loading the real and imaginary part in alternating
  //positions in a one-dimensional array.  Consequently, this code separates the real and imaginary parts
  //into their own arrays.  See method ComputeSpectra and Fourier.fou
    int i , j;
    if(real == null) real = new double [sp.length/2];
    if(real.length < sp.length/2) real = new double [sp.length/2];
    if(imag == null) imag = new double [sp.length/2];
    if(imag.length < sp.length/2) imag = new double [sp.length/2];
    for( j = 0, i =0; i < sp.length; i=i+2, j++ ) {
      real[j] = sp[i];
      imag[j] = sp[i+1];
    }

    nfsamp = j;
  }*/
    /*
   This simple program removes the mean of the data 
        n = number of points in the seismogram
        seis = seismogram
*/
  private void rmean(int [] seis) {
     double mean = 0.0;
     for( int i = 0; i < nsamp; i++ ) mean = mean + seis[i];
     mean = mean / (double)nsamp;
     int imean = (int) Math.round(mean);
     for( int i = 0; i < nsamp; i++ ) seis[i] = seis[i] - imean;
  }
 private int ntaper;
 private double [] taperCosines;
/*
   This simple program tapers the seismogram of npts.  
        n = number of points in the seismogram
        seis = seismogram
*/
  private void staper(int [] seis ) {
    
    double f;
    double val;
    // If the first tapler, computer the cosine needed
    if(ntaper == 0 ) {
      ntaper = (int) (percent * nsamp);
      taperCosines = new double[ntaper];
      for(int i = 0; i< ntaper; i++) {
        val = (double)i * pi / (double)ntaper;
        taperCosines[i] = 0.5*(1.0-Math.cos(val));   
      }
    }
    // Apply the cosines
    for( int i = 0; i < ntaper; i++ ) {
      seis[i] = (int) Math.round(seis[i] * taperCosines[i]);
      seis[nsamp-i-1] = (int) Math.round(seis[nsamp-i-1] * taperCosines[i]);
    }
  }
   
}
