/*
 *  This software is in the public domain because it contains materials 
 *  that originally came from the United States Geological Survey, 
 *  an agency of the United States Department of Interior. For more 
 *  information, see the official USGS copyright policy at 
 *  http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package subspacedetector;

import gov.usgs.anss.util.Util;
import java.io.*;
import java.util.Random;

/**
 *
 * @author benz
 */
public final class EmpiricalNoiseTemplates {

  public void prt(String s) {
    Util.prt(s);
  }

  public void prta(String s) {
    Util.prta(s);
  }

  public EmpiricalNoiseTemplates() throws FileNotFoundException, IOException, InterruptedException {
  }
  // The method takes a time-series (data), preserve the spectral amplitude and randomizes the phase
  // The resulting empirical noise time-series can be used to estimate noise in the cross-correlation 
  // process

  public static void computeRandomTimeSeries(float[] data, float hpcorner, float lpcorner,
          int npoles, String filtertype, int rate) throws FileNotFoundException, IOException {

    int dataLength = data.length;
    float[] randomtimeseries = new float[dataLength];              // Case of generating a template with random phase
    float[] gaussiannoise = new float[dataLength];     // Time-series of guassian random noise;

    Random rts = new Random();
    for (int j = 0; j < dataLength; j++) {
      gaussiannoise[j] = (float) (rts.nextGaussian() * Math.PI);
    }

    System.arraycopy(data, 0, randomtimeseries, 0, dataLength);
    taper(gaussiannoise, 0.05);

    FilterCollection f = new FilterCollection(hpcorner, lpcorner,
            npoles, rate, filtertype, 1);

    f.f0.filter(gaussiannoise);

    computeNoiseTemplate(randomtimeseries, gaussiannoise, rate);  // Case of generating a template with random phase to estimate level of noise  

    System.arraycopy(randomtimeseries, 0, data, 0, dataLength);

  }

  public static void computeNoiseTemplate(float[] seismogram, float[] noise, int rate) throws IOException {

    int i, j, jr, ji, kr, ki;

    int npts = seismogram.length;

    int nft = NPow2(npts);
    double dt = 1.0 / (double) rate;
    double[] dst = new double[2 * nft];
    double[] nst = new double[2 * nft];

    for (i = 0; i < npts; i++) {
      dst[2 * i] = seismogram[i];
    }
    for (i = 0; i < npts; i++) {
      nst[2 * i] = noise[i];
    }

    double df = computeFFT(dst, nft, -1, true, dt);
    double ndf = computeFFT(nst, nft, -1, true, dt);

    //
    // nextGaussian is zero mean and normally distributed.  Value are typically between
    // -1 and 1.   We need a random series of numbers that are normally-distributed and
    // between 0 and 2pi
    //
    int nft22 = nft / 2;

    double[] xx = new double[2 * nft];
    double amp;
    for (i = 0, j = 0; i < nft22; i++) {
      jr = j;
      ji = j + 1;
      amp = Math.sqrt(dst[jr] * dst[jr] + dst[ji] * dst[ji]);
      double phase = Math.atan2(nst[ji], nst[jr]);
      xx[jr] = amp * Math.cos(phase);
      xx[ji] = amp * Math.sin(phase);
      j = j + 2;
    }
    for (i = 0; i < nft22 - 1; i++) {
      jr = nft - 2 * i - 2;
      ji = jr + 1;
      kr = nft + 2 * i + 2;
      ki = kr + 1;
      xx[kr] = xx[jr];
      xx[ki] = -xx[ji];
    }
    xx[nft] = dst[nft];
    xx[nft + 1] = 0.0;
    xx[1] = 0.0;

    dt = computeFFT(xx, nft, 1, false, df);
    for (i = 0; i < npts; i++) {
      seismogram[i] = (float) xx[2 * i];
    }
  }

  /**
   * Tapers the ends of time series
   */
  public static void taper(float[] s, double percent) {
    float f = 0.0f;
    float val;

    if (percent > 0.5) {
      percent = 0.5;
    }
    if (percent < 0.0) {
      percent = 0.1;
    }
    int n = s.length;
    int nl = (int) (percent * n);

    for (int i = 0; i < nl; i++) {
      val = (float) ((float) i * Math.PI / (float) nl);
      f = (float) (0.5 * (1.0 - Math.cos(val)));
      s[i] = s[i] * f;
      s[n - i - 1] = s[n - i - 1] * f;
    }
  }

  /**
   * Fourier transform data[], real and imaginary parts alternate in data[] results are returned in
   * a vector
   */
  public static double computeFFT(double data[], int nn, int isign, boolean isDt,
          double delta) {
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
    double dtt = 0, dff = 0;
    double retValue;
    if (isDt) {
      dtt = delta;
    } else {
      dff = delta;
    }

    n = 2 * nn;
    if ((dtt) == 0.0) {
      dtt = 1. / (nn * (dff));
    }
    if ((dff) == 0.0) {
      dff = 1. / (nn * (dtt));
    }
    if ((dtt) != (nn * (dff))) {
      dff = 1. / (nn * (dtt));
    }
    if (isDt) {
      retValue = dff;
    } else {
      retValue = dtt;
    }
    j = 1;

    for (i = 1; i <= n; i += 2) {
      if (i < j) {
        tempr = data[j - 1];
        tempi = data[j];
        data[j - 1] = data[i - 1];
        data[j] = data[i];
        data[i - 1] = tempr;
        data[i] = tempi;
      }
      m = n / 2;
      do {
        if (j <= m) {
          break;
        }
        j = j - m;
        m = m / 2;
      } while (n >= 2);
      j = j + m;
    }

    mmax = 2;
    while (mmax < n) {
      istep = 2 * mmax;
      theta = 6.283185307 / (double) (isign * mmax);
      sinth = Math.sin(theta / 2.);
      wstpr = -2. * sinth * sinth;
      wstpi = Math.sin(theta);
      wr = 1.0;
      wi = 0.0;
      for (m = 1; m <= mmax; m += 2) {
        for (i = m; i <= n; i += istep) {
          j = i + mmax;
          tempr = wr * data[j - 1] - wi * data[j];
          tempi = wr * data[j] + wi * data[j - 1];
          data[j - 1] = data[i - 1] - tempr;
          data[j] = data[i] - tempi;
          data[i - 1] = data[i - 1] + tempr;
          data[i] = data[i] + tempi;
        }
        tempr = wr;
        wr = wr * wstpr - wi * wstpi + wr;
        wi = wi * wstpr + tempr * wstpi + wi;
      }
      mmax = istep;
    }
    /*
        * 	get the correct dimensions for a Fourier Transform 
        * 	from the Discrete Fourier Transform
     */
    if (isign > 0) {
      /*
            frequency to time domain
       */
      for (iiii = 0; iiii < n; iiii++) {
        data[iiii] = data[iiii] * dff;
      }
    } else {
      /*
            time to frequency domain
       */
      for (iiii = 0; iiii < n; iiii++) {
        data[iiii] = data[iiii] * dtt;
      }
    }
    return retValue;
  }

  public static int NPow2(int n) {
    int nn;
    nn = 2;
    while (nn < n) {
      nn *= 2;
    }
    return nn;
  }

  public static void main(String[] args) throws FileNotFoundException, IOException, InterruptedException {
  }
}
