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
public class Filt10 extends FilterStage {
  private final int NCOEF=130;
  private final int NCOEF2=260;
  private final int NDIV=10;
  // Sum of these is 0.49835579467821
  private static final double [] cd2 = {
	 -2.21394475e-05,-3.84535815e-05,-4.37853414e-05,-7.33722990e-05,-9.14422270e-05
	,-1.23938036e-04,-1.49931453e-04,-1.82887350e-04,-2.09853868e-04,-2.37086758e-04
	,-2.56050523e-04,-2.69085954e-04,-2.70466349e-04,-2.60901754e-04,-2.37020460e-04
	,-1.99575967e-04,-1.47611397e-04,-8.30789066e-05,-7.54570009e-06, 7.52988052e-05
	, 1.61596254e-04, 2.46079027e-04, 3.23379936e-04, 3.87565407e-04, 4.33107110e-04
	, 4.54859517e-04, 4.48835693e-04, 4.12364374e-04, 3.44629923e-04, 2.46817391e-04
	, 1.22360550e-04,-2.31280355e-05,-1.81951138e-04,-3.44674597e-04,-5.00592286e-04
	,-6.38365628e-04,-7.46739503e-04,-8.15366219e-04,-8.35626851e-04,-8.01409710e-04
	,-7.09796207e-04,-5.61590364e-04,-3.61609301e-04,-1.18741082e-04, 1.54299850e-04
	, 4.41516895e-04, 7.24451324e-04, 9.83254634e-04, 1.19797245e-03, 1.34993438e-03
	, 1.42317742e-03, 1.40583271e-03, 1.29129179e-03, 1.07914617e-03, 7.75740248e-04
	, 3.94305388e-04,-4.53973334e-05,-5.17899868e-04,-9.93356573e-04,-1.43926206e-03
	,-1.82251318e-03,-2.11168220e-03,-2.27933633e-03,-2.30428576e-03,-2.17355182e-03
	,-1.88391749e-03,-1.44292321e-03,-8.69178445e-04,-1.91919025e-04, 5.50195341e-04
	, 1.31105061e-03, 2.03971169e-03, 2.68357107e-03, 3.19189206e-03, 3.51942912e-03
	, 3.62999248e-03, 3.49960895e-03, 3.11909197e-03, 2.49577942e-03, 1.65425078e-03
	, 6.35925447e-04,-5.02531242e-04,-1.69196655e-03,-2.85453256e-03,-3.90836782e-03
	,-4.77287639e-03,-5.37430168e-03,-5.65125141e-03,-5.55977458e-03,-5.07763680e-03
	,-4.20745555e-03,-2.97838403e-03,-1.44616992e-03, 3.08558868e-04, 2.18377239e-03
	, 4.06116107e-03, 5.81273483e-03, 7.30846961e-03, 8.42472258e-03, 9.05277486e-03
	, 9.10711288e-03, 8.53281188e-03, 7.31155462e-03, 5.46582648e-03, 3.06090363e-03
	, 2.04397496e-04,-2.95675639e-03,-6.24184543e-03,-9.44424327e-03,-1.23412758e-02
	,-1.47055937e-02,-1.63174812e-02,-1.69774238e-02,-1.65182277e-02,-1.48159964e-02
	,-1.17992693e-02,-7.45576526e-03,-1.83624704e-03, 4.94480971e-03, 1.27118798e-02
	, 2.12343372e-02, 3.02354842e-02, 3.94042134e-02, 4.84087914e-02, 5.69119937e-02
	, 6.45869150e-02, 7.11325034e-02, 7.62880817e-02, 7.98460468e-02, 8.16620364e-02};


  private final int [] history;     // The space for the history
  int point;
  public Filt10 () {
    history = new int[2*NCOEF];
    /*double sum=0.;
    for(int i=0; i<NCOEF; i++) sum += cd2[i];
    Util.prt("Sum10="+sum);*/
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

    //System.arraycopy(history, NDIV, history, 0, (NCOEF*2-NDIV));
    //memcpy(&history[0],&history[NDIV], (NCOEF*2-NDIV)*sizeof(long));/* save history*/
    return (int) sum;					/* return answer as a long int */
  }
}
