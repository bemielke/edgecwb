/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.util;

/**
 * Attached are the coefficients for the AB95 SCR relation (that's Atkinson & Boore, 1995). ab95PGA
 * is peak ground acceleration for stable shield, a conservative relation (you won't miss anything
 * this way). For PGA, you only need the top line of the table (starts with 0.0000).
 *
 * Implementation to calculate PGA in %g as seen below.
 *
 * M = moment magnitude R = hypocentral distance
 *
 * ab95File = 'GMPE\attn_atkboore_momag.txt'; [T,c1,c2,c3,c4] = textread(ab95File,'%s %f %f %f
 * %f','headerlines',4);
 *
 * ab95PGA = exp(c1(1) + c2(1) .* (M - 6.0) + c3(1) .* (M - 6.0).^2 - ln(R) + c4(1) .* R) * 100;
 *
 * % Atkinson and Boore (1997) Attenuation Coefficients using Moment Magnitude % created by David
 * Robinson 19 November 2002 % T c1 c2 c3 c4 % 0.0000 1.8410 0.6860 -0.1230 -0.00311 0.0500 2.7620
 * 0.7550 -0.1100 -0.00520 0.0770 2.4630 0.7970 -0.1130 -0.00352 0.1000 2.3010 0.8290 -0.1210
 * -0.00279 0.1300 2.1400 0.8640 -0.1290 -0.00207 0.2000 1.7490 0.9630 -0.1480 -0.00105 0.3100
 * 1.2650 1.0940 -0.1650 -0.00024 0.5000 0.6200 1.2670 -0.1470 0 0.7700 -0.0940 1.3910 -0.1180
 * 01.0000 -0.5080 1.4280 -0.0940 0 1.2500 -0.9000 1.4620 -0.0710 0 2.0000 -1.6600 1.4600 -0.0390 0
 * PGV 4.6970 0.972 -0.0859 0
 *
 *
 *
 * @author davidketchum
 */
import java.text.DecimalFormat;

public class AB95 {

  /**
   * for a given magnitude and distance, return the %g expected
   *
   * @param m moment magnitude
   * @param r distance in km
   * @return
   */
  public static double getAccel(double m, double r) {
    double c1 = 1.8410;
    double c2 = 0.6860;
    double c3 = -0.1230;
    double c4 = -0.00311;
    double a = Math.exp(c1 + c2 * (m - 6.0) + c3 * (m - 6.0) * (m - 6.0) - Math.log(r) + c4 * r) * 100.;
    return a;
  }
  /** search for the distance at which the prediction amplitude is a for a given magnitude.
   * 
   * @param m Magnitude
   * @param a Desired % of g
   * @return The distance where this occurs.
   */
  public static double findDistance(double m, double a) {
    double low = .1;
    double high = 20000;
    double alow = getAccel(m, low);
    double ahigh = getAccel(m, high);
    double change = 5000;
    double d = 0.;
    while (Math.abs(alow - a) > 0.0001 * a) {
      d = (low + high) / 2.;
      double n = getAccel(m, d);
      //System.out.println("low="+low+" high = "+high+" accel low="+alow+" hi="+ahigh+" d="+d+" n = "+n);
      if (n < a) {
        high = d;
        change = n - ahigh;
        ahigh = n;

      } else {
        low = d;
        change = n - alow;
        alow = n;
      }
    }
    return d;
  }

  /**
   *
   * subroutine pred_sgm_jb82(dist,xmag,p,s,ivid,x,sigx,iere) c c subroutine for estimation of peak
   * horizontal acceleration c and velocity, based on Joyner & Boore, USGS OFR, 1982. c c PGA valid
   * for 5.0 <= M <= 7.7 c PGV valid for 5.0 <= M <= 7.7 c c input: c dist - distance in km c xmag -
   * event magnitude - assumed to be moment-mag c p - 0 for 50% probability of exceedence; c - 1 for
   * 84% probability of exceedence c s - 0 for rock site c - 1 for soil site c ivid - 1 for PGA c -
   * 2 for PGV c output: c x - acceleration (in g) or velocity (in cm/s) c sigx - standard error of
   * PGA or PGV prediction c iere - error return code c real*4 dist, xmag, p, s real*4 alx, vlx, x,
   * sigx integer*4 iere c c initialize error return c iere = 0 c if (ivid .eq. 1) then c c peak
   * horizontal acceleration c rdist = sqrt(dist**2 + 8.0**2) alx = -0.95 + 0.230*xmag -
   * alog10(rdist) - 0.00270*rdist . + 0.28*p x = 10.**alx sigx = 0.28 else c c peak horizontal
   * velocity c rdist = sqrt(dist**2 + 4.0**2) vlx = -0.85 + 0.49*xmag - alog10(rdist) -
   * 0.00260*rdist . + 0.22*p + 0.17*s x = 10.**vlx sigx = 0.33 end if c c all done c return end
   * This is for the case p=0 s=0 and ivid=1 (PGA)
   *
   * @param xmag Moment magnitude
   * @param dist Distance in km
   * @return predicted sgm
   */
  public static double pred_sgm_jb82(double xmag, double dist) {
    double rdist = Math.sqrt(dist * dist + 64.);
    double alx = -0.95 + 0.230 * xmag - Math.log10(rdist) - 0.00270 * rdist;
    double x = Math.pow(10., alx);
    return x;

  }

  public static double findDistance2(double m, double a) {
    double low = .1;
    double high = 20000;
    double alow = pred_sgm_jb82(m, low);
    double ahigh = pred_sgm_jb82(m, high);
    double change = 5000;
    double d = 0.;
    while (Math.abs(alow - a) > 0.0001 * a) {
      d = (low + high) / 2.;
      double n = pred_sgm_jb82(m, d);
      //System.out.println("low="+low+" high = "+high+" accel low="+alow+" hi="+ahigh+" d="+d+" n = "+n);
      if (n < a) {
        high = d;
        change = n - ahigh;
        ahigh = n;

      } else {
        low = d;
        change = n - alow;
        alow = n;
      }
    }
    return d;
  }

  public static void main(String[] args) {
    if (args.length >= 1) {
      if (args[0].equals("-h") || args[0].equals("-?")) {
        Util.prt("Usage : AB95 mag, dist or AB95 mag, lat1, long1,lat2,long2");
        System.exit(0);
      } else if (args[0].equals("-test")) {    // quick test over man 2.5 to 7 of dist formula and PGA at that distance
        for (int i = 25; i < 70; i++) {
          Util.prt((i / 10.) + " " + Util.df21(-415. + i / 10. * 190.)
                  + " km accel=" + Util.df24(AB95.getAccel(i / 10., (-415 + i / 10. * 190))) + " %g");
        }
        System.exit(0);
      }
      double mag = Double.parseDouble(args[0]);
      double km = 0.;
      switch (args.length) {
        case 2:
          km = Double.parseDouble(args[1]);
          break;
        case 5:
          double lt = Double.parseDouble(args[1]);
          double lg = Double.parseDouble(args[2]);
          double lt2 = Double.parseDouble(args[3]);
          double lg2 = Double.parseDouble(args[4]);
          double[] ans = Distaz.distaz(lt, lg, lt2, lg2);
          km = ans[0];
          break;
        default:
          Util.prt("Usage : AB95 mag, dist or AB95 mag, lat1, long1,lat2,long2");
          break;
      }

      Util.prt("AB95.getAccel(" + mag + "," + Util.df24(km) + ")=" + Util.df24(AB95.getAccel(mag, km))
              + " %g min dist(SMGetter)=" + (-415. + mag * 190) + " km");
      System.exit(0);
    }
    double[] r = {10., 20., 50., 100., 200., 500., 1000., 2000., 5000., 10000.};
    Util.prt("accel(3.2,193)=" + getAccel(3.2, 193.));
    DecimalFormat df = new DecimalFormat("0.0000");
    DecimalFormat df0 = new DecimalFormat("0.0");
    StringBuilder sb = new StringBuilder(20000);
    sb.append("       ");
    for (int i = 0; i < 14; i++) {
      sb.append("  ").append(df0.format(3. + i * 0.5)).append("  ");
    }
    sb.append("\n");
    for (int ir = 0; ir < r.length; ir++) {
      sb.append((r[ir] + "        ").substring(0, 6)).append(" ");
      for (int i = 0; i < 14; i++) {
        double m = 3.0 + i * 0.5;
        sb.append((df.format(getAccel(m, r[ir]))).substring(0, 6)).append(" ");
      }
      sb.append("\n");
    }
    System.out.print(sb.toString());
    for (int i = 0; i < 90; i++) {
      double d = findDistance(1. + i * 0.1, .1);
      double d2 = findDistance2(1. + i * 0.1, 0.001);
      System.out.println(df0.format(1.0 + i * 0.1) + "\t" + df0.format(d) + "\t" + df0.format(1.0 + i * 0.1)
              + "\t" + df.format(d / 110.0) + "\t" + df.format(getAccel(1.0 + i * 0.1, d))
              + "\t" + df0.format(d2) + "\t" + df.format(pred_sgm_jb82(1.0 + 0.1 * i, d2)));
    }
    double mag = 4.0;
    while(mag < 9.) {
      double high = 10000.;
      
      while(true) {
        double hians = AB95.getAccel(mag, high);
        if(hians < 0.01) high = high - 1.;
        else {
          System.out.println(mag+" "+high+" "+hians+" "+AB95.findDistance(mag, 0.01));
          break;
        }
             
      }
      mag += 0.5;
    }
  }
}
