/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */

package gov.usgs.anss.fk.obsolete;

import gov.usgs.anss.util.Util;

/* This code should have replace by FFT everywhere in the FK code.  It is obsolete as it is here.

 * Fourier.java
 *
 * Created on February 8, 2007, 12:08 PM
 *
 *
 * @author benz
 */

public class Fourier {
    
  private double [] wstpr = null;
  private double [] wstpi = null;
  
  public double [] getWstpr() { return wstpr; }
  public double [] getWstpi() { return wstpi; }
  
  /** Creates a new instance of Fourier */
  public Fourier() {       
  }
  public Fourier(int nn, int isign) {
      computecoeff(nn,isign);
  }
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
      theta = 6.283185307/(double)(isign*mmax);
      //theta = 2.0*Math.PI/(double)(isign*mmax);
      sinth=Math.sin(theta/2.);
      wstpr[i] =-2.*sinth*sinth;
      wstpi[i]=Math.sin(theta);
      mmax = istep;
      i++;
    }
  }
  
  private double fourhb(double data[], int nn, int isign, boolean isDt, double delta ) {
/*
	subroutine four(data,nn,isign,dt,df)
c-----
 *  if isDT is true, delta is dt, if not, its df
c     the cooley-tookey fast fourier transform in usasi basic fortran
c     transform(j) = sum(data(i)*w**((i-1)(j-1)), where i and j run
c     from 1 to nn and w = exp(isign*2*pi*sqrt(-1)/nn).  data is a one-
c     dimensional complex array (i.e., the real and imaginary parts of
c     data are located immediately adjacent in storage, such as fortran
c     places them) whose length nn is a power of two.  isign
c     is +1 or -1, giving the sign of the transform.  transform values
c     are returned in array data, replacing the input data.  the time is
c     proportional to n*log2(n), rather than the usual n**2
c     rms resolution error being bounded by 6*sqrt(i)*log2(nn)*2**(-b),
c     b is the number of bits in the floating point fraction.
c
c     the program computes df from dt, dt from df and checks to see
c     if they are consistent. In addition, the transforms are multiplied
c     by dt or df to make the results dimensionally correct
c-----
      real*4 data(*)
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

    if(wstpr == null) computecoeff(nn,isign);

    n = 2 * nn;
    if((dtt) == 0.0) dtt = 1./(nn*(dff)) ;
    if((dff) == 0.0) dff = 1./(nn*(dtt)) ;
    if((dtt) != (nn*(dff))) dff = 1./(nn*(dtt)) ;
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
      ii=ii+1;
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

  public static double four2(double data[], int nn, int isign, boolean isDt, double delta) {
/*
	subroutine four(data,nn,isign,dt,df)
c-----
 *  if isDT is true, delta is dt, if not, its df
c     the cooley-tookey fast fourier transform in usasi basic fortran
c     transform(j) = sum(data(i)*w**((i-1)(j-1)), where i and j run
c     from 1 to nn and w = exp(isign*2*pi*sqrt(-1)/nn).  data is a one-
c     dimensional complex array (i.e., the real and imaginary parts of
c     data are located immediately adjacent in storage, such as fortran
c     places them) whose length nn is a power of two.  isign
c     is +1 or -1, giving the sign of the transform.  transform values
c     are returned in array data, replacing the input data.  the time is
c     proportional to n*log2(n), rather than the usual n**2
c     rms resolution error being bounded by 6*sqrt(i)*log2(nn)*2**(-b),
c     b is the number of bits in the floating point fraction.
c
c     the program computes df from dt, dt from df and checks to see
c     if they are consistent. In addition, the transforms are multiplied
c     by dt or df to make the results dimensionally correct
c-----
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
    int kk = 0;
    while(mmax < n ){
      istep= 2 * mmax;
      theta = 6.283185307/(double)(isign*mmax);
      sinth=Math.sin(theta/2.);
      wstpr=-2.*sinth*sinth;
      wstpi=Math.sin(theta);
      wr=1.0;
      wi=0.0;
      kk++;
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
  public static double amp2(double real, double imag) {
    return Math.sqrt(real*real+imag*imag);
  }
  public static double phase2(double real, double imag) {
    return Math.atan(imag/real)*180/Math.PI;
  }
  public static void main(String [] args) { 
    double [] d = new double[64];
    double [] c = new double[64];
    int nn = 32;
    int isign = -1;
    double dt = 0.5;
    for(int i=0; i<(nn+nn); i=i+2) {
      d[i] = c[i] = 0.0;
      d[i+1] = c[i] = 0.0;
    }
    
    d[2]=c[2]=1.0;
    Fourier tst = new Fourier();
    double dff = tst.fourhb(d, nn, isign, true, dt);
    System.out.println("DT="+dt+" DF="+dff);  //main()
    for(int i=0; i<(nn+nn); i=i+2) {
      System.out.println(i+" ("+d[i]+", "+d[i+1]+")"+" "+amp2(d[i],d[i+1])+" "+phase2(d[i],d[i+1]));  //main()
    }
    // Dirac delta
    for(int i=0; i<(nn+nn); i=i+2) {
      d[i] = c[i] = 0.0;
      d[i+1] = c[i] = 0.0;
    }
    d[2]=c[2]=1.0;
    double df = Fourier.four2(d, nn, -1, true, dt);
    System.out.println("DT="+dt+" DF="+df);  //main()
    for(int i=0; i<(nn+nn); i=i+2) {
      System.out.println(i+" ("+d[i]+", "+d[i+1]+")"+" "+amp2(d[i],d[i+1])+" "+phase2(d[i],d[i+1]));  //main()
    }
    double dt1 = Fourier.four2(d, nn, +1, false, df);
    System.out.println("DT="+dt1+" DF="+df);  //main()
    for(int i=0; i<(nn+nn); i=i+2) {
      System.out.println(i+" ("+d[i]+", "+d[i+1]+")  ("+c[i]+", "+c[i+1]+")");  //main()
    }
    
    // Sine wave 4 cycles in FFT
    for(int i=0; i<(nn+nn); i=i+2) {
      d[i] = c[i] = 100.*Math.sin(i*Math.PI/nn*4.);
      d[i+1] = c[i+1] = 0.;
    }
    double df2 = Fourier.four2(d, nn, -1, true, dt);
    System.out.println("DT="+dt+" DF="+df2);  //main()
    for(int i=0; i<(nn+nn); i=i+2) {
      System.out.println(i+" "+Util.df25((i/2)*df2)+" "+Util.df25(c[i])+
      " ("+Util.ef4(d[i])+", "+Util.ef4(d[i+1])+")  "+
              Util.ef4(amp2(d[i],d[i+1]))+" "+Util.df23(phase2(d[i],d[i+1])) );  //main()
    }
  }
}
