/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */

package gov.usgs.anss.fk;

/*
 * HPLPFilter.java
 *
 *
 * @author benz
 */
import gov.usgs.anss.util.Util;
import java.io.*;
    
public class HPLPFilter {
    
  //int     order;
  int     nsects;
  double  zeros_real[];
  double  zeros_imag[];
  double  poles_real[];
  double  poles_imag[];
  double  sos_num[];
  double  sos_denom[];
  double  dcvalue[];
  double corner;
  int     stype[];  
  boolean highpass;
  private final static int MAXTORDR=10;     // DCK: what are these for?
  private final static int FILTORDR=4;
  private final static int CPZ=0;
  private final static int CP=1;
  private final static int SP=2;
  
  public final static double pi=Math.PI;
  private final StringBuilder tmpsb = new StringBuilder(50);
  @Override
  public String toString() {return toStringBuilder(null).toString();}
  public StringBuilder toStringBuilder(StringBuilder tmp) {
    StringBuilder sb = tmp;
    if(sb == null) {
      sb=Util.clear(tmpsb);
    }
    synchronized(sb) {
      sb.append("Filt:").append(" hp=").append(highpass).append(" ").append(corner).append(" npole=").append(nsects*2);
    }
    return sb;
  }      
  /** Creates a new instance of HPLPFilter
   * @param dt sample interval of seismic data
   * @param corner corner frequency of filter (in Hz)
   * @param npoles number of poles in filter
   * @param highPass true (will high pass filter), false (will low pass filter)
   */
  public HPLPFilter ( double dt, double corner, int npoles, boolean highPass) {
    this.highpass=highPass;
    this.corner=corner;
    zeros_real = new double[npoles/2];
    zeros_imag = new double[npoles/2];
    poles_real = new double[npoles/2];
    poles_imag = new double[npoles/2];
    sos_num = new double[30];
    sos_denom = new double[30];
    dcvalue = new double[npoles/2];
    stype = new int[npoles/2];
   
    if(highPass) {
     double fhw;

     BUroots( npoles );
     fhw = warp( corner*dt/2., 2. );
     LPtoHP( );
     CutOffs(  fhw );
     BiLin( );

     //Apply(  seis, n, npasses );
      
    }
    else {
     double flw;

     BUroots(  npoles );
     flw = warp(  corner*dt/2., 2. );
     LP(  );
     CutOffs(  flw );
     BiLin(  );

     //Apply( seis, n, npasses );      
    }
  }

private void BUroots( int order)
{
     int half, k;
     double  angle;

     half = order / 2 ;

     nsects = half;

     for ( k = 0 ; k < nsects ; k++ )
     {
         angle = pi * ( 0.5 +  ((2.0*(k+1)-1)/(2.0*order)) ) ;
         poles_real[k] = Math.cos( angle ) ;
         poles_imag[k] = Math.sin( angle ) ;
         stype[k] = CP ;
         dcvalue[k] = 1.0 ;
     }
}
/*
//  
======================================================================== 
===
//
//warp analog cutoff freq based on sample rate in prep for bilinear transform
//
//  
======================================================================== 
===
*/
private double warp(double f, double ts)
{
     double angle;
     double warp;


     angle = 2. * pi * f * ts / 2. ;
     warp = 2. * Math.tan( angle ) / ts ;
     warp = warp / 2. / pi;
     return( warp );

}
/*
//  
======================================================================== 
===
//
//		 Compute the analog low pass Butterworth Second Order Sections for  
iorder butterworth
//
//  
======================================================================== 
===
*/
private void LP()
{
     int iptr, i;
     double scale;

     iptr = 0;
     for ( i = 0 ; i < nsects ; i++ )
     {
         scale = poles_real[i] * poles_real[i]
               + poles_imag[i] * poles_imag[i] ;

         sos_num[iptr] = scale ;
         sos_num[iptr+1] = 0. ;
         sos_num[iptr+2] = 0. ;

         sos_denom[iptr] = scale ;
         sos_denom[iptr+1] = -2. * poles_real[i] ;
         sos_denom[iptr+2] = 1. ;

         iptr += 3 ;
     }

     sos_num[0] *= dcvalue[0] ;
     sos_num[1] *= dcvalue[0] ;
     sos_num[2] *= dcvalue[0] ;

}
/*
//  
======================================================================== 
===
//
//		 Compute the analog high pass Butterworth Second Order Sections for  
iorder butterworth
//
//  
======================================================================== 
===
*/
private void LPtoHP()
{
     int iptr, i;
     double scale;

     iptr = 0;
     for ( i = 0 ; i < nsects ; i++ )
     {
         scale = poles_real[i] * poles_real[i]
               + poles_imag[i] * poles_imag[i] ;

         sos_num[iptr] = 0. ;
         sos_num[iptr+1] = 0. ;
         sos_num[iptr+2] = scale ;

         sos_denom[iptr] = 1. ;
         sos_denom[iptr+1] = -2. * poles_real[i] ;
         sos_denom[iptr+2] = scale ;

         iptr += 3 ;
     }

     sos_num[0] *= dcvalue[0] ;
     sos_num[1] *= dcvalue[0] ;
     sos_num[2] *= dcvalue[0] ;

}
/*
//  
======================================================================== 
===
//
//		 Compute the analog Butterworth Second Order Sections for iorder  
butterworth
//
//  
======================================================================== 
===
*/
private void CutOffs(double f)
{

     int iptr, i;
     double scale;


     scale = 2. * pi * f ;


     iptr = 0;
     for ( i = 0 ; i < nsects ; i++ )
     {

         sos_num[iptr+1] /= scale ;
         sos_num[iptr+2] /= (scale*scale) ;

         sos_denom[iptr+1] /= scale ;
         sos_denom[iptr+2] /= (scale*scale) ;

         iptr += 3 ;
     }

}
/*
//  
======================================================================== 
===
//
//		 BiLinear transform the analog Butterworth to digital filter
//
//  
======================================================================== 
===
*/
private void BiLin()
{
     int iptr, i;
     double scale, a0, a1, a2;

     iptr = 0;
     for ( i = 0 ; i < nsects ; i++ )
     {
         a0 = sos_denom[iptr];
         a1 = sos_denom[iptr+1];
         a2 = sos_denom[iptr+2];

         scale = a2 + a1 + a0 ;

         sos_denom[iptr] = 1. ;
         sos_denom[iptr+1] = ( 2. * ( a0 - a2 ) ) / scale ;
         sos_denom[iptr+2] = ( a2 - a1 + a0 ) / scale ;

         a0 = sos_num[iptr];
         a1 = sos_num[iptr+1];
         a2 = sos_num[iptr+2];

         sos_num[iptr] = ( a2 + a1 + a0 ) / scale ;
         sos_num[iptr+1] = ( 2. * ( a0 - a2 ) ) / scale ;
         sos_num[iptr+2] = ( a2 - a1 + a0 ) / scale ;

         iptr += 3 ;
     }

}
/*
//  
======================================================================== 
===
//
// Apply the digital Butterworth Second Order Sections for iorder  
butterworth
//
//  
======================================================================== 
===
*/
public void Apply( double [] data, int nsamps, int zp)
{

     int jptr, j, i;
     double x1, x2, y1, y2, b0, b1, b2, a1, a2, output;

     jptr = 0;
     for ( j = 0 ; j < nsects ; j++ )
     {
         x1 = 0. ;
         x2 = 0. ;
         y1 = 0. ;
         y2 = 0. ;

         b0 = sos_num[jptr] ;
         b1 = sos_num[jptr+1] ;
         b2 = sos_num[jptr+2] ;

         a1 = sos_denom[jptr+1] ;
         a2 = sos_denom[jptr+2] ;

         for ( i = 0 ; i < nsamps ; i++ )
         {
             output = (b0 * data[i]) + (b1 * x1) + (b2 * x2) ;
             output = output - ( a1 * y1 + a2 * y2 ) ;
             y2 = y1 ;
             y1 = output ;
             x2 = x1 ;
             x1 = data[i] ;
             data[i] = output ;
         }

         jptr += 3 ;
     }

     if ( zp == 2 )
     {
             jptr = 0;
     for ( j = 0 ; j < nsects ; j++ )
     {
         x1 = 0. ;
         x2 = 0. ;
         y1 = 0. ;
         y2 = 0. ;

         b0 = sos_num[jptr] ;
         b1 = sos_num[jptr+1] ;
         b2 = sos_num[jptr+2] ;

         a1 = sos_denom[jptr+1] ;
         a2 = sos_denom[jptr+2] ;

         for ( i = (nsamps - 1) ; i >= 0 ; i-- )
         {
             output = (b0 * data[i]) + (b1 * x1) + (b2 * x2) ;
             output = output - ( a1 * y1 + a2 * y2 ) ;
             y2 = y1 ;
             y1 = output ;
             x2 = x1 ;
             x1 = data[i] ;
             data[i] = output ;
         }

         jptr += 3 ;
     }

     }

}
  public static void main(String [] args) {
    
    double dt = 0.05;
    double hpcorner = 0.5;
    double lpcorner = 0.1;
    double t0 = 0.0;
    int npoles = 4;
    int npasses = 2;
    int n = 256;
    
    try {
      PrintStream out = new PrintStream("log");
   

      HPLPFilter hp = new HPLPFilter(dt, hpcorner, npoles, true);
      HPLPFilter lp = new HPLPFilter(dt, lpcorner, npoles, false);
      double [] data = new double[n];
      for(int i=0; i<n; i++) data[i]=0.;
      data[n/2]=1.0;
      hp.Apply(data, n, npasses);
      out.println("HP "+dt);
      for(int i=0; i<n; i++) {
        out.println(t0+" "+data[i]+" "+dt);
        System.out.println("hp["+i+"]="+data[i]);  //main()
        t0 = t0 + dt;
      }
      for(int i=0; i<n; i++) data[i]=0.;
      data[n/2]=1.0;
      lp.Apply(data, n, npasses);
      t0 = 0.0;
      out.println("LP "+dt);
      for(int i=0; i<n; i++) {
        out.println(t0+" "+data[i]+" "+dt);
        System.out.println("lp["+i+"]="+data[i]);  //main()
        t0 = t0 + dt;
      }
    
    }
    catch (FileNotFoundException e) {
      System.out.println("Could not open log "+e.getMessage());  //main()
    }
    
  }

}
