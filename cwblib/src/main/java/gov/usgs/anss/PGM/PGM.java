/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */

package gov.usgs.anss.PGM;

import java.util.GregorianCalendar;
import java.io.PrintStream;
import gov.usgs.anss.util.Util;

/** This class was largely adapted from C code from other PGM calculations.
 *
 * @author davidketchum
 */
public class PGM {

  public static final int MAXTRACELTH = 3200000;
  public static final double GRAVITY = 978.03;
  /* Gravity in cm/sec/sec */
  static final double[] d1 = new double[MAXTRACELTH];
  static final double[] d2 = new double[MAXTRACELTH];
  static final double[] d3 = new double[MAXTRACELTH];

  /**
   * ****************************************************************************
   * subroutine for estimation of ground motion * * input: * Data - data array * npts - number of
   * points in timeseries * itype - units for timeseries. 1=disp 2=vel 3=acc * dt - sample spacing
   * in sec * return: * SM_INFO object * null - if an * *
   * **************************************************************************** @param seed The 12
   * @param seed 12 character SEED name
   * @param QUID the Quake ID as a string
   * @param auth The quake author as a string
   * @param trig The trigger time, first motion time
   * @param start the start time of the time series
   * @param Data The data array in
   * @param npts The number of points in data array
   * @param itype 1=disp, 2=velocity, 3=acc
   * @param dt Sample spacing in seconds
   * @param out logging printstream
   * @return AN SM_INFO object containing the computed parameters
   */
  public static SM_INFO peak_ground(String seed, String QUID, String auth, GregorianCalendar trig,
          GregorianCalendar start, double[] Data, int npts, int itype, double dt, PrintStream out) {
    int ii, kk, kpts, icaus;
    int[] id = new int[4];
    int[][] npd = new int[4][2];
    double totint, a, tpi, omega, damp;

    double[] gd = new double[4];
    double[] sd = new double[4];
    out.println("Call SM_INFOR seed=" + seed + " QUID=" + QUID + " auth=" + auth + " trig=" + Util.ascdate(trig) + " " + Util.asctime(trig) + " itype=" + itype + " dt=" + dt + " npts=" + npts);
    SM_INFO sm = new SM_INFO(seed, QUID, auth, trig);

    /* Made these float arrays static because Solaris was Segfaulting on an
   * allocation this big from the stack.  These currently are 3.2 MB each
   * DK 20030108
   ************************************************************************/
    gd[0] = 0.05;
    gd[1] = 0.10;
    gd[2] = 0.20;
    gd[3] = 0.50;
    icaus = 1;

    tpi = 8.0 * Math.atan(1.0);

    /* Find the raw maximum and its period
*************************************/
    out.println("Ad demean");
    demean(Data, npts);
    AMP raw = new AMP();
    out.println("at amaxper");
    amaxper(npts, dt, Data, raw);
    out.println("If type=" + itype);
    switch (itype) {
      case 1:
        /* input data is displacement  */
        for (kk = 0; kk < npts; kk++) {
          d1[kk] = Data[kk];
        } locut(d1, npts, 0.17, dt, 2, icaus);
        for (kk = 1; kk < npts; kk++) {
          d2[kk] = (d1[kk] - d1[kk - 1]) / dt;
        } d2[0] = d2[1];
        demean(d2, npts);
        for (kk = 1; kk < npts; kk++) {
          d3[kk] = (d2[kk] - d2[kk - 1]) / dt;
        } d3[0] = d3[1];
        demean(d3, npts);
        break;
      case 2:
        /* input data is velocity      */
        for (kk = 0; kk < npts; kk++) {
          d2[kk] = Data[kk];
        } locut(d2, npts, 0.17, dt, 2, icaus);
        for (kk = 1; kk < npts; kk++) {
          d3[kk] = (d2[kk] - d2[kk - 1]) / dt;
        } d3[0] = d3[1];
        demean(d3, npts);
        totint = 0.0;
        for (kk = 0; kk < npts - 1; kk++) {
          totint = totint + (d2[kk] + d2[kk + 1]) * 0.5 * dt;
          d1[kk] = totint;
        } d1[npts - 1] = d1[npts - 2];
        demean(d1, npts);
        break;
      default:
        if (itype == 3) {
          /* input data is acceleration  */
          for (kk = 0; kk < npts; kk++) {
            d3[kk] = Data[kk];
          }
          locut(d3, npts, 0.17, dt, 2, icaus);
          
          totint = 0.0;
          for (kk = 0; kk < npts - 1; kk++) {
            totint = totint + (d3[kk] + d3[kk + 1]) * 0.5 * dt;
            d2[kk] = totint;
          }
          d2[npts - 1] = d2[npts - 2];
          demean(d2, npts);
          
          totint = 0.0;
          for (kk = 0; kk < npts - 1; kk++) {
            totint = totint + (d2[kk] + d2[kk + 1]) * 0.5 * dt;
            d1[kk] = totint;
          }
          d1[npts - 1] = d1[npts - 2];
          demean(d1, npts);
        } else {
          return null;
        } break;
    }

    /* Find the displacement(cm), velocity(cm/s), & acceleration(cm/s/s) maxima  and their periods
*********************************************************************************************/
    out.println("Do maxampper on all");
    AMP dsp = new AMP();
    amaxper(npts, dt, d1, dsp);
    AMP vel = new AMP();
    amaxper(npts, dt, d2, vel);
    AMP acc = new AMP();
    amaxper(npts, dt, d3, acc);

    /* Find the spectral response
****************************/
    out.println("Do spectra response");

    damp = 0.05;
    kk = 0;
    sm.pdrsa[kk] = 0.3;
    omega = tpi / sm.pdrsa[kk];
    RD rd = new RD();
    rdrvaa(d3, npts - 1, omega, damp, dt, rd);
    sm.rsa[kk] = rd.aa;
    kk += 1;

    sm.pdrsa[kk] = 1.0;
    omega = tpi / sm.pdrsa[kk];
    rdrvaa(d3, npts - 1, omega, damp, dt, rd);
    sm.rsa[kk] = rd.aa;
    kk += 1;

    sm.pdrsa[kk] = 3.0;
    omega = tpi / sm.pdrsa[kk];
    rdrvaa(d3, npts - 1, omega, damp, dt, rd);
    sm.rsa[kk] = rd.aa;
    kk += 1;

    sm.nrsa = kk;

    /* Since we are here, determine the duration of strong shaking
*************************************************************/
// DCK - this duration of shaking stored in sd[0-3] is not used here, perhaps they use it in a ML calc?
/*  for(kk=0;kk<4;kk++) {
      id[kk] = npd[kk][1] = npd[kk][2] = 0;
      for(ii=1;ii<=npts-1;ii++) {
          a = Math.abs(d3[ii]/GRAVITY);
          if (a >= gd[kk]) {
              id[kk] = id[kk] + 1;
              if (id[kk] == 1) npd[kk][1] = ii;
              npd[kk][2] = ii;
          }
      }
      if (id[kk] != 0) {
          kpts = npd[kk][2] - npd[kk][1] + 1;
          sd[kk] = kpts*dt;
      } else {
          sd[kk] = 0.0;
      }
  }
     */
    out.println("Set pg?");
    sm.pgd = Math.abs(dsp.aminmm) > Math.abs(dsp.amaxmm) ? Math.abs(dsp.aminmm) : Math.abs(dsp.amaxmm);
    sm.pgv = Math.abs(vel.aminmm) > Math.abs(vel.amaxmm) ? Math.abs(vel.aminmm) : Math.abs(vel.amaxmm);
    sm.pga = Math.abs(acc.aminmm) > Math.abs(acc.amaxmm) ? Math.abs(acc.aminmm) : Math.abs(acc.amaxmm);

    sm.tpgd.setTimeInMillis((long) (Math.abs(dsp.aminmm) > Math.abs(dsp.amaxmm) ? start.getTimeInMillis() + dt * dsp.imin * 1000 : start.getTimeInMillis() + dt * dsp.imax * 1000));
    sm.tpgv.setTimeInMillis((long) (Math.abs(vel.aminmm) > Math.abs(vel.amaxmm) ? start.getTimeInMillis() + dt * vel.imin * 1000 : start.getTimeInMillis() + dt * vel.imax * 1000));
    sm.tpga.setTimeInMillis((long) (Math.abs(acc.aminmm) > Math.abs(acc.amaxmm) ? start.getTimeInMillis() + dt * acc.imin * 1000 : start.getTimeInMillis() + dt * acc.imax * 1000));
    out.println("Return");
    return sm;
  }

  /**
   * ****************************************************************************
   * demean removes the mean from the n point series stored in array A. * *
 *****************************************************************************
   */
  static void demean(double[] A, int n) {
    int i;
    double xm;

    xm = 0.0;
    for (i = 0; i < n; i++) {
      xm = xm + A[i];
    }
    xm = xm / n;
    for (i = 0; i < n; i++) {
      A[i] = A[i] - xm;
    }
  }

  /**
   * ****************************************************************************
   * Butterworth locut filter order 2*nroll (nroll<=8) * (see Kanasewich, Time Sequence Analysis in
   * Geophysics, * Third Edition, University of Alberta Press, 1981) * written by W. B. Joyner
   * 01/07/97 * * s[j] input = the time series to be filtered * output = the filtered series *
   * dimension of s[j] must be at least as large as * nd+3.0*float(nroll)/(fcut*delt) * nd = the
   * number of points in the time series * fcut = the cutoff frequency * delt = the timestep * nroll
   * = filter order * causal if icaus.eq.1 - zero phase shift otherwise * * The response is given by
   * eq. 15.8-6 in Kanasewich: * Y = sqrt((f/fcut)**(2*n)/(1+(f/fcut)**(2*n))), * where n = 2*nroll
   * * * Dates: 01/07/97 - Written by Bill Joyner * 12/17/99 - D. Boore added check for fcut = 0.0,
   * in which case * no filter is applied. He also cleaned up the * appearance of the code (indented
   * statements in * loops, etc.) * 02/04/00 - Changed "n" to "nroll" to eliminate confusion with *
   * Kanesewich, who uses "n" as the order (=2*nroll) * 03/01/00 - Ported to C by Jim Luetgert * *
   * **************************************************************************** @param s The input
   * time series
   * @param nd Number of data points in series
   * @param fcut The cutoff frequence (HZ?)
   * @param delt The digitizing period in seconds
   * @param nroll The filter order
   * @param icaus if 1 zero phase sift, otherwise response is given above
   *
   */
  public static void locut(double[] s, int nd, double fcut, double delt, int nroll, int icaus) {
    double[] fact = new double[8];
    double[] b1 = new double[8];
    double[] b2 = new double[8];
    double pi, w0, w1, w2, w3, w4, w5, xp, yp, x1, x2, y1, y2;
    int j, k, np2, npad;

    if (fcut == 0.0) {
      return;       /* Added by DMB  */
    }

    pi = 4.0 * Math.atan(1.0);
    w0 = 2.0 * pi * fcut;
    w1 = 2.0 * Math.tan(w0 * delt / 2.0);
    w2 = (w1 / 2.0) * (w1 / 2.0);
    w3 = (w1 * w1) / 2.0 - 2.0;
    w4 = 0.25 * pi / nroll;

    for (k = 0; k < nroll; k++) {
      w5 = w4 * (2.0 * k + 1.0);
      fact[k] = 1.0 / (1.0 + Math.sin(w5) * w1 + w2);
      b1[k] = w3 * fact[k];
      b2[k] = (1.0 - Math.sin(w5) * w1 + w2) * fact[k];
    }

    np2 = nd;

    if (icaus != 1) {
      npad = (int) (3.0 * nroll / (fcut * delt));  // cast needed, loss of precision
      np2 = nd + npad;
      for (j = nd; j < np2; j++) {
        s[j] = 0.0;
      }
    }

    for (k = 0; k < nroll; k++) {
      x1 = x2 = y1 = y2 = 0.0;
      for (j = 0; j < np2; j++) {
        xp = s[j];
        yp = fact[k] * (xp - 2.0 * x1 + x2) - b1[k] * y1 - b2[k] * y2;
        s[j] = yp;
        y2 = y1;
        y1 = yp;
        x2 = x1;
        x1 = xp;
      }
    }

    if (icaus != 1) {
      for (k = 0; k < nroll; k++) {
        x1 = x2 = y1 = y2 = 0.0;
        for (j = 0; j < np2; j++) {
          xp = s[np2 - j - 1];
          yp = fact[k] * (xp - 2.0 * x1 + x2) - b1[k] * y1 - b2[k] * y2;
          s[np2 - j - 1] = yp;
          y2 = y1;
          y1 = yp;
          x2 = x1;
          x1 = xp;
        }
      }
    }
  }

  /**
   * ****************************************************************************
   * rdrvaa * * This is a modified version of "Quake.For", originally * written by J.M. Roesset in
   * 1971 and modified by * Stavros A. Anagnostopoulos, Oct. 1986. The formulation is * that of
   * Nigam and Jennings (BSSA, v. 59, 909-922, 1969). * Dates: 02/11/00 - Modified by David M.
   * Boore, based on RD_CALC * 03/01/00 - Ported to C by Jim Luetgert * * acc = acceleration time
   * series * na = length of time series * omega = 2*pi/per * damp = fractional damping (e.g., 0.05)
   * * dt = time spacing of input * rd = relative displacement of oscillator * rv = relative
   * velocity of oscillator * aa = absolute acceleration of oscillator *
   * **************************************************************************** @param acc The
   * accelleration time series
   * @param na The length of the time series
   * @param omega 2*PI/PER
   * @param damp Damping fraction (e.g.,0.05)
   * @param dt Time spacing of samples in seconds
   * @param rd This is a convenience obje to contain the rd, rv, and aa values
   */
  public static void rdrvaa(double[] acc, int na, double omega, double damp, double dt,
          RD rd) {
    double omt, d2a, bom, d3a, omd, om2, omdt, c1, c2, c3, c4, cc, ee;
    double s1, s2, s3, s4, s5, a11, a12, a21, a22, b11, b12, b21, b22;
    double y, ydot, y1, z, z1, z2, ra;
    int i;

    omt = omega * dt;
    d2a = Math.sqrt(1.0 - damp * damp);
    bom = damp * omega;
    d3a = 2.0 * bom;
    omd = omega * d2a;
    om2 = omega * omega;
    omdt = omd * dt;
    c1 = 1.0 / om2;
    c2 = 2.0 * damp / (om2 * omt);
    c3 = c1 + c2;
    c4 = 1.0 / (omega * omt);
    ee = Math.exp(-damp * omt);
    cc = Math.cos(omdt) * ee;
    s1 = Math.sin(omdt) * ee / omd;
    s2 = s1 * bom;
    s3 = s2 + cc;
    s4 = c4 * (1.0 - s3);
    s5 = s1 * c4 + c2;

    a11 = s3;
    a12 = s1;
    a21 = -om2 * s1;
    a22 = cc - s2;

    b11 = s3 * c3 - s5;
    b12 = -c2 * s3 + s5 - c1;
    b21 = -s1 + s4;
    b22 = -s4;

    y = ydot = rd.rd = rd.rv = rd.aa = 0.0;
    for (i = 0; i < na - 1; i++) {
      y1 = a11 * y + a12 * ydot + b11 * acc[i] + b12 * acc[i + 1];
      ydot = a21 * y + a22 * ydot + b21 * acc[i] + b22 * acc[i + 1];
      y = y1;
      /* y is the oscillator output at time corresponding to index i   */
      z = Math.abs(y);
      if (z > rd.rd) {
        rd.rd = z;
      }
      z1 = Math.abs(ydot);
      if (z1 > rd.rv) {
        rd.rv = z1;
      }
      ra = -d3a * ydot - om2 * y1;
      z2 = Math.abs(ra);
      if (z2 > rd.aa) {
        rd.aa = z2;
      }
    }
  }

  /**
   * ****************************************************************************
   * compute maximum amplitude and its associated period * * input: * npts - number of points in
   * timeseries * dt - sample spacing in sec * fc - input timeseries * output: * amaxmm - raw
   * maximum * pmax - period of maximum * imax - index of maxmimum point * *
 *****************************************************************************
   */
  static void amaxper(int npts, double dt, double[] fc, AMP amp) {
    double amin, amax, pp, pm, mean, frac;
    int i, j, jmin, jmax;

    amp.imax = jmax = amp.imin = jmin = 0;
    amax = amin = amp.amaxmm = amp.aminmm = fc[0];
    amp.aminmm = amp.pmax = mean = 0.0;
    for (i = 0; i < npts; i++) {
      mean = mean + fc[i] / npts;
      if (fc[i] > amax) {
        jmax = i;
        amax = fc[i];
      }
      if (fc[i] < amin) {
        jmin = i;
        amin = fc[i];
      }
    }

    /*     compute period of maximum    */
    pp = pm = 0.0;
    if (fc[jmax] > mean) {
      /* for this not to be true, fc[i] = mean everywhere */
      j = jmax + 1;
      while (j < npts && fc[j] > mean) {
        pp += dt;
        j += 1;
      }
      if (j < npts) {
        /* if j is past the end, do not do this meaningless calc */
        frac = dt * (mean - fc[j - 1]) / (fc[j] - fc[j - 1]);
        frac = 0.0;
        /* this seems useless to add fract to pp if it zero always */
        pp = pp + frac;
      }
      j = jmax - 1;
      if (j > 0) {
        /* DCK to not modify pm if jmax == 0 */
        while (j >= 0 && fc[j] > mean) {
          pm += dt;
          if (j <= 0) {
            break;
          }
          j -= 1;
        }
        if (j >= 0) {
          /* if j = -1, the period ran to the beginning, do not calc frac */
          frac = dt * (mean - fc[j + 1]) / (fc[j] - fc[j + 1]);
          frac = 0.0;
          /* this seems useless to add fract to pp if it zero always */
          pm = pm + frac;
        }
      }
    } else {
      /* seems unlike this else can be executed */
      j = jmax + 1;
      if (j < npts && fc[j] < mean) {
        pp += dt;
        j += 1;
      }
      frac = dt * (mean - fc[j - 1]) / (fc[j] - fc[j - 1]);
      frac = 0.0;
      pp = pp + frac;
      j = jmax - 1;
      if (j >= 0) {
        if (j >= 0 && fc[j] < mean) {
          pm += dt;
          j -= 1;
        }
        frac = dt * (mean - fc[j + 1]) / (fc[j] - fc[j + 1]);
        frac = 0.0;
        pm = pm + frac;
      }
    }

    amp.imin = jmin;
    amp.imax = jmax;
    amp.pmax = 2.0 * (pm + pp);
    amp.aminmm = amin;
    amp.amaxmm = amax;
  }

}
