/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.filter;

/**
 *
 * @author davidketchum
 */
public class FiltHeli extends FilterStage {
  private  static final int NSEC=2;
  private static final int MXCOEF=5;
  private static final int NALLCO=10;
	int [] nc = {5,4};
	int [] mc = {4,3};

	double [][] xc = new double[NSEC][MXCOEF];
	double [][] yc = new double[NSEC][MXCOEF];
	static double [][] bc =
		{{4.3284664e-1,-1.7313866e+0, 2.5970799e+0,-1.7313866e+0,4.3284664e-1},
    { 9.8531161e-2, 2.9559348e-1, 2.9559348e-1,9.8531161e-2, 0.}};
	static double [][] ac={
		{2.3695130e+0,-2.3139884e+0, 1.0546654e+0,-1.8737949e-1,0.},
    { 5.7724052e-1,-4.2178705e-1, 5.6297236e-2,0., 0.}};
  @Override
  public int filt(int [] data, int off) {
    //System.arraycopy(history, 0, xc, 0, NALLCO);
    //memcpy(xc,history,sizeof(double)*NALLCO);
    //System.arraycopy(history,NALLCO, yc , 0 , NALLCO);
    //memcpy(yc,history+NALLCO,sizeof(double)*NALLCO);
    double yn=data[off];
  /*	fprintf(logout,"a=%d %x %d\n",a,history,sizeof(double));
    for(n=0;n<NSEC;n++) for(j=0;j<MXCOEF;j++) fprintf(logout,"%10.3e ",xc[n][j]);
    fprintf(logout," xc\n");
    for(n=0;n<NSEC;n++) for(j=0;j<MXCOEF;j++) fprintf(logout,"%10.3e ",yc[n][j]);
    fprintf(logout," yc\n");*/
    for(int n=0; n<NSEC; n++)
    {	xc[n][0]=yn;
        yn=0.;

      for(int j=0; j<nc[n]; j++) yn=yn+bc[n][j]*xc[n][j];

      for(int j=nc[n]-1; j>0; j--) xc[n][j]=xc[n][j-1];

      for(int j=0; j<mc[n]; j++) yn=yn+ac[n][j]*yc[n][j];

      for(int j=mc[n]-1; j>0; j--) yc[n][j]=yc[n][j-1];

      yc[n][0]=yn;
    }
  /*	for(n=0;n<NSEC;n++) for(j=0;j<MXCOEF;j++) fprintf(logout,"%10.3e ",xc[n][j]);
    fprintf(logout," xc\n");
    for(n=0;n<NSEC;n++) for(j=0;j<MXCOEF;j++) fprintf(logout,"%10.3e ",yc[n][j]);
    fprintf(logout," yc\n");*/
    //System.arraycopy(xc, 0, history, 0, NALLCO);
    //memcpy(history,xc,sizeof(double)*NALLCO);
    //System.arraycopy(yc, 0, history, NALLCO, NALLCO);
    //memcpy(history+NALLCO,yc,sizeof(double)*NALLCO);
    return (int) yn;

  }
    public int filt(int [] data, int off, double gain) {
    //System.arraycopy(history, 0, xc, 0, NALLCO);
    //memcpy(xc,history,sizeof(double)*NALLCO);
    //System.arraycopy(history,NALLCO, yc , 0 , NALLCO);
    //memcpy(yc,history+NALLCO,sizeof(double)*NALLCO);
    double yn=data[off]*gain;
  /*	fprintf(logout,"a=%d %x %d\n",a,history,sizeof(double));
    for(n=0;n<NSEC;n++) for(j=0;j<MXCOEF;j++) fprintf(logout,"%10.3e ",xc[n][j]);
    fprintf(logout," xc\n");
    for(n=0;n<NSEC;n++) for(j=0;j<MXCOEF;j++) fprintf(logout,"%10.3e ",yc[n][j]);
    fprintf(logout," yc\n");*/
    for(int n=0; n<NSEC; n++)
    {	xc[n][0]=yn;
        yn=0.;

      for(int j=0; j<nc[n]; j++) yn=yn+bc[n][j]*xc[n][j];

      for(int j=nc[n]-1; j>0; j--) xc[n][j]=xc[n][j-1];

      for(int j=0; j<mc[n]; j++) yn=yn+ac[n][j]*yc[n][j];

      for(int j=mc[n]-1; j>0; j--) yc[n][j]=yc[n][j-1];

      yc[n][0]=yn;
    }
  /*	for(n=0;n<NSEC;n++) for(j=0;j<MXCOEF;j++) fprintf(logout,"%10.3e ",xc[n][j]);
    fprintf(logout," xc\n");
    for(n=0;n<NSEC;n++) for(j=0;j<MXCOEF;j++) fprintf(logout,"%10.3e ",yc[n][j]);
    fprintf(logout," yc\n");*/
    //System.arraycopy(xc, 0, history, 0, NALLCO);
    //memcpy(history,xc,sizeof(double)*NALLCO);
    //System.arraycopy(yc, 0, history, NALLCO, NALLCO);
    //memcpy(history+NALLCO,yc,sizeof(double)*NALLCO);
    return (int) yn;

  }
  public static void main(String [] args) {
    double pi2=2*3.1415926;
    int [] sine = new int[1000];
    int [] out = new int[1000];
    FiltHeli filt = new FiltHeli();
    int min=Integer.MAX_VALUE,max=Integer.MIN_VALUE;
    double hertz=0.5;
    for (int i=0; i<sine.length; i++) sine[i]=(int) (1000000.*Math.sin(pi2/(10./hertz)*i)); // 10 hz sampleing of hertz data
    for(int i=0; i<sine.length; i++) out[i] = filt.filt(sine, i, 10000.);
    for(int i=500; i<sine.length; i++) {
      System.out.println(i+" "+sine[i]+" out="+out[i]);
      if(out[i] < min) min = out[i];
      if(out[i] > max) max = out[i];
    }
    System.out.println("min="+min+" max="+max);
  }
}
