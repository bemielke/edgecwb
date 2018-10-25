/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */

package gov.usgs.anss.fk;

import gov.usgs.anss.fk.obsolete.Fourier;   // This is only used to test this routine against its ancestor.
import java.util.ArrayList;
import gov.usgs.anss.util.Util;

/** This is a object oriented version for the Fourier.java routine which was more of 
 * a static FFT.  Each object is characterized by its data length and sign.  A list of
 * FFTs is created for this signature and when the FFT.doFFT() static function is 
 * called an existing one is used for the computation, or a new one with these parameters
 * is created and added to the collections.  The primary benefit is that the sines and cosines
 * are only computed once and stored in an array for use by the object.
 * <p>
 * 
 * The static FFT.four() is the original code for comparison and can be used, but all of the
 * sines and cosines will be recomputed on each call to four().
 *  based on the now obsolete Fourier.java
 *
 * Created on February 8, 2007, 12:08 PM
 * 
 * Reworked into this class from Fourier.java by benz by Ketchum in Apr 2015
 *
 * @author benz
 */

public class FFT {
  private static final ArrayList<FFT> ffts = new ArrayList<FFT>(10);
  private static final double twopi = 2. * Math.PI;
  private double [] wstpr = null;
  private double [] wstpi = null;
  private final int nn;
  private final int isign;
  
  public double [] getWstpr() { return wstpr; }
  public double [] getWstpi() { return wstpi; }
  public int getNN() {return nn;}
  public int getSign() {return isign;}
  /** This static function performs an FFT based on the parameter and creates an object
   * of type FFT for the given number of points and signs if one is not already available
   * on a list of FFTs previously created.
   * <p>
   * The documentation of the original FFT was as follows: 
   * <br>
   * if isDT is true, delta is dt, if not, its df
   * the cooley-tookey fast fourier transform in usasi basic fortran
   * transform(j) = sum(data(i)*w**((i-1)(j-1)), where i and j run
   * from 1 to nn and w = exp(isign*2*pi*sqrt(-1)/nn).  data is a one-
   * dimensional complex array (i.e., the real and imaginary parts of
   * data are located immediately adjacent in storage, such as fortran
   * places them) whose length nn is a power of two.  isign
   * is +1 or -1, giving the sign of the transform.  transform values
   * are returned in array data, replacing the input data.  the time is
   * proportional to n*log2(n), rather than the usual n**2
   * rms resolution error being bounded by 6*sqrt(i)*log2(nn)*2**(-b),
   * b is the number of bits in the floating point fraction.
   * <br>
   * the program computes df from dt, dt from df and checks to see
   * if they are consistent. In addition, the transforms are multiplied
   * by dt or df to make the results dimensionally correct
   * 
   * @param data Double data to FFT
   * @param nn The number of data points in data[]
   * @param isign The sign of the desired transform
   * @param isDt If true, delta is a sample interval, if false, delta is a delta frequence 
   * @param delta The sample Interval in seconds or if isDT is false, the delta frequence
   * @return This is df if isDT is true, and dt if isDT is false - that is the delta in the new domain
   */
  public static double doFFT(double data[], int nn, int isign, boolean isDt, double delta) {
    FFT fft = null;
    synchronized(ffts) {
      for (FFT fft2 : ffts) {
        if (fft2.getNN() == nn && fft2.getSign() == isign) {
          fft = fft2;
          break;
        }
      }
      if(fft == null) {
        fft = new FFT(nn, isign);
        ffts.add(fft);
      }
    }
    return fft.fft(data, nn, isign, isDt, delta);
  }
  public FFT(int nn, int isign) {
    this.nn=nn;
    this.isign=isign;
    computecoeff(nn,isign);
  }
  /** compute the sine/consine table for an fft of length nn and sign as given
   * 
   * @param nn Length of the data array 
   * @param isign  The sign of the desired transform
   */
  private void computecoeff(int nn, int isign) {

    int mmax = 2 ;
    int n = 2 * nn;
    int istep;
    double theta;
    double sinth;

    int k = 0;
    while(mmax < n ){     // figure the power of 2 for nn*2
      istep= 2 *mmax;
      mmax = istep;
      k++;
    }
    wstpr = new double [k];
    wstpi = new double [k];

    mmax = 2;
    int i = 0; 
    while(mmax < n ){
      istep= 2 *mmax;
      theta = twopi/(double)(isign*mmax);
      //theta = 2.0*Math.PI/(double)(isign*mmax);
      sinth=Math.sin(theta/2.);
      wstpr[i] =-2.*sinth*sinth;
      wstpi[i]=Math.sin(theta);
      mmax = istep;
      i++;
    }
  }
  
  private double fft(double data[], int nn, int isign, boolean isDt, double delta ) {
/*
	subroutine four(data,nn,isign,dt,df)
c-----
 *  if isDT is true, delta is dt, if not, its df
   * the cooley-tookey fast fourier transform in usasi basic fortran
   * transform(j) = sum(data(i)*w**((i-1)(j-1)), where i and j run
   * from 1 to nn and w = exp(isign*2*pi*sqrt(-1)/nn).  data is a one-
   * dimensional complex array (i.e., the real and imaginary parts of
   * data are located immediately adjacent in storage, such as fortran
   * places them) whose length nn is a power of two.  isign
   * is +1 or -1, giving the sign of the transform.  transform values
   * are returned in array data, replacing the input data.  the time is
   * proportional to n*log2(n), rather than the usual n**2
   * rms resolution error being bounded by 6*sqrt(i)*log2(nn)*2**(-b),
   * b is the number of bits in the floating point fraction.
   * <br>
   * the program computes df from dt, dt from df and checks to see
   * if they are consistent. In addition, the transforms are multiplied
   * by dt or df to make the results dimensionally correct
*/
    int n;
    int i, j, m, mmax, iiii, istep;
    double tempr, tempi;
    double wr, wi;
    //double wstpr, wstpi;
    //double sinth, theta;
    double dtt=0, dff=0;
    double retValue;
    if(isDt)  dtt = delta;
    else dff = delta;

    //if(wstpr == null) computecoeff(nn,isign);

    n = 2 * nn;
    if(dtt == 0.0) dtt = 1./(nn*dff) ;
    if(dff == 0.0) dff = 1./(nn*dtt) ;
    if(dtt != (nn*dff)) dff = 1./(nn*dtt) ;
    if(isDt) retValue =dff;
    else retValue = dtt;
    j = 1;
    for (i=1;i<=n; i+=2) {
      if(i < j){
        tempr = data[j-1];
        tempi = data[j  ];
        data[j-1] = data[i-1];
        data[j  ]=data[i  ];
        data[i-1] = tempr;
        data[i  ] = tempi;
      }
      m = n/2;
      do {
        if(j<= m) break;
        j = j - m;
        m = m/2; 
      } while(n >= 2);
      j = j+m;
    }
    mmax = 2 ;
    int ii = 0;
    while(mmax < n ){
      istep= 2 *mmax;
      //theta = 6.283185307/(double)(isign*mmax);
      //sinth=Math.sin(theta/2.);
      //wstpr=-2.*sinth*sinth;
      //wstpi=Math.sin(theta);
      wr=1.0;
      wi=0.0;
      for (m=1; m <= mmax ; m +=2){
        for(i = m ; i <= n ; i+=istep){
          j=i+mmax;
          tempr=wr*data[j-1]-wi*data[j  ];
          tempi=wr*data[j  ]+wi*data[j-1];
          data[j-1]=data[i-1]-tempr;
          data[j  ]=data[i  ]-tempi;
          data[i-1]=data[i-1]+tempr;
          data[i  ]=data[i  ]+tempi;
        }
        tempr = wr;
        wr = wr*wstpr[ii]-wi*wstpi[ii] + wr;
        wi = wi*wstpr[ii]+tempr*wstpi[ii] + wi;
      }
      mmax = istep;
      ii++;
    }
   /*
    * 	get the correct dimensions for a Fourier Transform 
    * 	from the Discrete Fourier Transform
  */ 
    if(isign > 0){
      /*
      frequency to time domain
      */
      for (iiii= 0 ; iiii < n ; iiii++){
        data[iiii] = data[iiii] * dff;
      }
    } else {
      /*
      time to frequency domain
      */
      for (iiii= 0 ; iiii < n ; iiii++){
        data[iiii] = data[iiii] * dtt;
      }
    }
    return retValue;
  }

  public static double four(double data[], int nn, int isign, boolean isDt, double delta) {
/*
	 * subroutine four(data,nn,isign,dt,df)
   *
   *  if isDT is true, delta is dt, if not, its df
   * the cooley-tookey fast fourier transform in usasi basic fortran
   * transform(j) = sum(data(i)*w**((i-1)(j-1)), where i and j run
   * from 1 to nn and w = exp(isign*2*pi*sqrt(-1)/nn).  data is a one-
   * dimensional complex array (i.e., the real and imaginary parts of
   * data are located immediately adjacent in storage, such as fortran
   * places them) whose length nn is a power of two.  isign
   * is +1 or -1, giving the sign of the transform.  transform values
   * are returned in array data, replacing the input data.  the time is
   * proportional to n*log2(n), rather than the usual n**2
   * rms resolution error being bounded by 6*sqrt(i)*log2(nn)*2**(-b),
   * b is the number of bits in the floating point fraction.
   *<br>
   * the program computes df from dt, dt from df and checks to see
   * if they are consistent. In addition, the transforms are multiplied
   * by dt or df to make the results dimensionally correct
      real*4 data(*)
*/
    int n;
    int i, j, m, mmax, iiii, istep;
    double tempr, tempi;
    double wr, wi;
    double wstpr, wstpi;
    double sinth, theta;
    double dtt=0, dff=0;
    double retValue;
    if(isDt)  dtt = delta;
    else dff = delta;

    n = 2 * nn;
    if((dtt) == 0.0) dtt = 1./(nn*(dff)) ;
    if((dff) == 0.0) dff = 1./(nn*(dtt)) ;
    if((dtt) != (nn*(dff))) dff = 1./(nn*(dtt)) ;
    if(isDt) retValue =dff;
    else retValue = dtt;
    j = 1;
    for (i=1;i<=n; i+=2){
      if(i < j){
        tempr = data[j-1];
        tempi = data[j  ];
        data[j-1] = data[i-1];
        data[j  ]=data[i  ];
        data[i-1] = tempr;
        data[i  ] = tempi;
      }
      m = n/2;
      do {
        if(j<= m) break;
        j = j - m;
        m = m/2; 
      } while(n >= 2);
      j = j+m;
    }
    mmax = 2 ;
    while(mmax < n ){
      istep= 2 * mmax;
      theta = twopi/(double)(isign*mmax);
      sinth=Math.sin(theta/2.);
      wstpr=-2.*sinth*sinth;
      wstpi=Math.sin(theta);
      wr=1.0;
      wi=0.0;
      for (m=1; m <= mmax ; m +=2){
        for(i = m ; i <= n ; i+=istep){
          j=i+mmax;
          tempr=wr*data[j-1]-wi*data[j  ];
          tempi=wr*data[j  ]+wi*data[j-1];
          data[j-1]=data[i-1]-tempr;
          data[j  ]=data[i  ]-tempi;
          data[i-1]=data[i-1]+tempr;
          data[i  ]=data[i  ]+tempi;
        }
        tempr = wr;
        wr = wr*wstpr-wi*wstpi + wr;
        wi = wi*wstpr+tempr*wstpi + wi;
      }
      mmax = istep;
    }
   /*
    * 	get the correct dimensions for a Fourier Transform 
    * 	from the Discrete Fourier Transform
  */ 
    if(isign > 0){
      /*
      frequency to time domain
      */
      for (iiii= 0 ; iiii < n ; iiii++){
        data[iiii] = data[iiii] * dff;
      }
    } else {
      /*
      time to frequency domain
      */
      for (iiii= 0 ; iiii < n ; iiii++){
        data[iiii] = data[iiii] * dtt;
      }
    }
    return retValue;
  }
  public static double amp(double real, double imag) {
    return Math.sqrt(real*real+imag*imag);
  }
  public static double phase(double real, double imag) {
    return Math.atan(imag/real)*180/Math.PI;
  }
  public static void main(String [] args) { 
    double [] d = new double[64];
    double [] c = new double[64];
    StringBuilder sb1 = new StringBuilder(2000);
    StringBuilder sb2 = new StringBuilder(2000);
    StringBuilder sb3 = new StringBuilder(2000);
    int nn = 32;
    int isign = -1;
    double dt = 0.5;
    for(int i=0; i<(nn+nn); i=i+2) {
      d[i] = c[i] = 0.0;
      d[i+1] = c[i] = 0.0;
    }
    
    d[2]=c[2]=1.0;
    //Fourier tst = new Fourier();
    //double dff = tst.fourhb(d, nn, isign, true, dt);
    double dff = FFT.doFFT(d, nn, -1, true, dt);
    
    sb1.append("DT=").append(dt).append(" DF=").append(dff).append("\n");  //main()
    for(int i=0; i<(nn+nn); i=i+2) {
      sb1.append(i).append(" (").append(Util.ef6(d[i])).append(", ").append(Util.ef6(d[i+1])).append(")" + " ").
              append(Util.ef6(amp(d[i],d[i+1]))).append(" ").append(Util.ef6(phase(d[i],d[i+1]))).append("\n");  //main()
    }
    // Dirac delta
    for(int i=0; i<(nn+nn); i=i+2) {
      d[i] = c[i] = 0.0;
      d[i+1] = c[i] = 0.0;
    }
    d[2]=c[2]=1.0;
    double df = Fourier.four2(d, nn, -1, true, dt);
    sb2.append("DT=").append(dt).append(" DF=").append(dff).append("\n");  //main()
    for(int i=0; i<(nn+nn); i=i+2) {
      sb2.append(i).append(" (").append(Util.ef6(d[i])).append(", ").append(Util.ef6(d[i+1])).append(") ").
              append(Util.ef6(amp(d[i],d[i+1]))).append(" ").append(Util.ef6(phase(d[i],d[i+1]))).append("\n");  //main()
    }    
    // Invers it back
    double dt1 = Fourier.four2(d, nn, +1, false, df);
    sb3.append("DT=").append(dt1).append(" DF=").append(df).append("\n");  //main()
    for(int i=0; i<(nn+nn); i=i+2) {
      sb3.append(i).append(" (").append(Util.ef6(d[i])).append(", ").append(Util.ef6(d[i+1])).append(")  (").
              append(Util.ef6(c[i])).append(", ").append(Util.ef6(c[i+1])).append(")"+"\n");  //main()
    }
    if(Util.stringBuilderEqual(sb1, sb2)) System.out.println("Dirac FFTs identical");
    else {System.out.println("****** Dirc=ac FFT different!\n"+sb1.toString()+"\n"+sb2.toString());}
    
    Util.clear(sb1);
    Util.clear(sb2);
    // Sine wave 4 cycles in FFT
    for(int i=0; i<(nn+nn); i=i+2) {
      d[i] = c[i] = 100.*Math.sin(i*Math.PI/nn*4.);
      d[i+1] = c[i+1] = 0.;
    }
    double df2 = FFT.doFFT(d, nn, -1, true, dt);
    sb1.append("DT=").append(dt).append(" DF=").append(df2).append("\n");  //main()
    for(int i=0; i<(nn+nn); i=i+2) {
      sb1.append(i).append(" ").append(Util.df25((i/2)*df2)).append(" ").append(Util.df25(c[i])).
              append(" (").append(Util.ef6(d[i])).append(", ").append(Util.ef6(d[i+1])).append(")  ").
              append(Util.ef6(amp(d[i],d[i+1]))).append(" ").append(Util.df23(phase(d[i],d[i+1]))).append("\n");  //main()
    }
    // Sine wave 4 cycles in FFT
    for(int i=0; i<(nn+nn); i=i+2) {
      d[i] = c[i] = 100.*Math.sin(i*Math.PI/nn*4.);
      d[i+1] = c[i+1] = 0.;
    }
    df2 = Fourier.four2(d, nn, -1, true, dt);
    sb2.append("DT=").append(dt).append(" DF=").append(df2).append("\n");  //main()
    for(int i=0; i<(nn+nn); i=i+2) {
      sb2.append(i).append(" ").append(Util.df25((i/2)*df2)).append(" ").append(Util.df25(c[i])).
              append(" (").append(Util.ef6(d[i])).append(", ").append(Util.ef6(d[i+1])).append(")  ").
              append(Util.ef6(amp(d[i],d[i+1]))).append(" ").append(Util.df23(phase(d[i],d[i+1]))).append("\n");  //main()
    }
    if(Util.stringBuilderEqual(sb1, sb2)) System.out.println("Sine waves the same!"+"\n"+sb1.toString());
    else {System.out.println("****** Sine waves different!\n"+sb1.toString()+"\n"+sb2.toString());}
  }
}
