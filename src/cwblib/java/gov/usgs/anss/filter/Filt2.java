/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */

package gov.usgs.anss.filter;
//import gov.usgs.anss.util.Util;
/**
 *
 * @author davidketchum
 */
public class Filt2 extends FilterStage {
  private final int NCOEF=32;
  private final int NCOEF2=64;
  private final int NDIV=2;
  // Sum of these is 0.505563441905
  private static final double [] cd2 = {
	  2.88049545e-04, 1.55313976e-03, 2.98230513e-03, 2.51714466e-03,-5.02926821e-04
	,-2.81205843e-03,-8.08708369e-04, 3.21542984e-03, 2.71266000e-03,-2.91550322e-03
	,-5.09429071e-03, 1.33933034e-03, 7.40034366e-03, 1.82796526e-03,-8.81958286e-03
	,-6.56719319e-03, 8.38608573e-03, 1.24268681e-02,-5.12978853e-03,-1.84868593e-02
	,-1.79236766e-03, 2.33604181e-02, 1.30477296e-02,-2.51709446e-02,-2.93134767e-02
	, 2.12669298e-02, 5.21898977e-02,-6.61517353e-03,-8.83535221e-02,-3.66062373e-02
	, 1.86273292e-01, 4.03764486e-01};


  private final int [] history;     // The space for the history
  int point;
  public Filt2 () {
    history = new int[NCOEF2];
/*    double sum=0.;
    for(int i=0; i<NCOEF; i++) sum += cd2[i];
    Util.prt("Sum2="+sum);*/
    point=0;    // this will track the first data
  }
  @Override
  public int filt(int [] data, int off) {
    System.arraycopy(data, off, history, point, NDIV);  // Move new data to point
    point += NDIV;
    if(point >= NCOEF2) point = point - NCOEF2; // point now points to oldest data
    double sum=0.;
    int k=point;          // first (oldest) point in buffer
    int j=point-1;        // last (newest) point in the data buffer
    if(j < 0) j += NCOEF2;
    for (int i=0; i<NCOEF; i++) {
      sum+=cd2[i]*history[k++];			/* Convolve 1st half of series */
      sum+=cd2[i]*history[j--];			/* Convolve 2nd half of series */
      if(k >= NCOEF2) k -= NCOEF2;
      if(j < 0) j += NCOEF2;
    }

    //System.arraycopy(history, NDIV, history, 0, (NCOEF*2-NDIV));
    //memcpy(&history[0],&history[NDIV], (NCOEF*2-NDIV)*sizeof(long));/* save history*/
    return (int) sum;					/* return answer as a long int */
  }
}
