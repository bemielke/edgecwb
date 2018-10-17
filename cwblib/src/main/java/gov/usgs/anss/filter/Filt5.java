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
public class Filt5 extends FilterStage {
  private final int NCOEF=100;
  private final int NCOEF2=200;
  private final int NDIV=5;
  private static final double [] cd2 = {
  3.26563000e-07,  6.33260000e-07,  5.80818000e-07, -1.01565000e-06, -6.33460000e-06,
 -1.87720000e-05, -4.28524000e-05, -8.36933000e-05, -1.45932000e-04, -2.32161000e-04,
 -3.41113000e-04, -4.66018000e-04, -5.93696000e-04, -7.04979000e-04, -7.76850000e-04,
 -7.86372000e-04, -7.15968000e-04, -5.59059000e-04, -3.24694000e-04, -3.96210000e-05,
  2.53443000e-04,  5.01929000e-04,  6.53407000e-04,  6.67806000e-04,  5.29163000e-04,
  2.53751000e-04, -1.08307000e-04, -4.79672000e-04, -7.70524000e-04, -8.99036000e-04,
 -8.13215000e-04, -5.08537000e-04, -3.59907000e-05,  5.03228000e-04,  9.77089000e-04,
  1.25319000e-03,  1.23375000e-03,  8.86415000e-04,  2.61813000e-04, -5.08843000e-04,
 -1.23828000e-03, -1.72626000e-03, -1.81207000e-03, -1.42318000e-03, -6.06267000e-04,
  4.70300000e-04,  1.54702000e-03,  2.33268000e-03,  2.57997000e-03,  2.15850000e-03,
  1.10463000e-03, -3.68453000e-04, -1.90808000e-03, -3.10241000e-03, -3.58647000e-03,
 -3.14790000e-03, -1.80284000e-03,  1.82236000e-04,  2.33534000e-03,  4.08545000e-03,
  4.90838000e-03,  4.47630000e-03,  2.76981000e-03,  1.17590000e-04, -2.85532000e-03,
 -5.36668000e-03, -6.67475000e-03, -6.28675000e-03, -4.12191000e-03, -5.79407000e-04,
  3.51786000e-03,  7.10179000e-03,  9.12450000e-03,  8.84788000e-03,  6.08102000e-03,
  1.29550000e-03, -4.42629000e-03, -9.61428000e-03, -1.27645000e-02, -1.27437000e-02,
 -9.14235000e-03, -2.47694000e-03,  5.82861000e-03,  1.37038000e-02,  1.88947000e-02,
  1.95345000e-02,  1.46899000e-02,  4.74422000e-03, -8.49351000e-03, -2.20100000e-02,
 -3.21194000e-02, -3.52107000e-02, -2.85584000e-02, -1.10178000e-02,  1.65598000e-02,
  5.13019000e-02,  8.87094000e-02,  1.23410000e-01,  1.50120000e-01,  1.64640000e-01
	};


  private final int [] history;     // The space for the history
  int point;
  public Filt5 () {
    history = new int[2*NCOEF];
    /*double sum=0.;
    for(int i=0; i<NCOEF; i++) sum += cd2[i];
    Util.prt("Sum5="+sum);*/
    point=0;
  }
  @Override
  public int filt(int [] data, int off) {
    System.arraycopy(data, off, history, point, NDIV);
    point += NDIV;
    if(point >= NCOEF2) point = point - NCOEF2;
    double sum=0.;
  /*	sum2=0;							/* zero the summation variable */
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
  public int filt1(int  data) {
    history[point++] = data;
    if(point >= NCOEF2) point = point - NCOEF2;
    double sum=0.;
  /*	sum2=0;							/* zero the summation variable */
    int k=point;          // first (oldest) point in buffer
    int j=point-1;        // last (newest) point in the data buffer
    if(j < 0) j += NCOEF2;
    for (int i=0; i<NCOEF; i++) {
      sum+=cd2[i]*history[k++];			/* Convolve 1st half of series */
      sum+=cd2[i]*history[j--];			/* Convolve 2nd half of series */
      if(k >= NCOEF2) k -= NCOEF2;
      if(j < 0) j += NCOEF2;
    }
    return (int) sum;
  }
}
