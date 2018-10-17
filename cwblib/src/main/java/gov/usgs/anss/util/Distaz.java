/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.util;

import java.text.DecimalFormat;

/**
 * The author of theses routines is largely unknown, though I think H. Benz did the original. D.
 * Ketchum changed them to allow user supplied arrays to be used rather than creating them and made
 * all of these routines static as I could see no good reason to create an array each time something
 * was called.
 *
 * @author adapted by davidketchum
 */
public final class Distaz {

  private final static double TORAD = Math.PI / 180.0;
  private final static double TODEG = 1.0 / TORAD;
  private final static double EARTHRADIUS = 6378.160;   // semi-major axis
  //private final static double pi=Math.PI;
  private final static double vpvsratio = Math.sqrt(3.0);

  /**
   * Calculate the distance az and back az from two lats and longs. Differs from distaz() as it
   * System.exit() if error occurs rather than reporting it and then returning a bad answer. It
   * always creates the results array rather than using a user supplied one, so distaz() is
   * preferred.
   *
   * @param lt1 First latitude
   * @param lg1 First Longitude
   * @param lt2 2nd latitude
   * @param lg2 2nd longitude
   * @return array of 4 with dist(km), dist(deg), az, and baz
   */
  public static double[] distaz2(double lt1, double lg1, double lt2, double lg2) {

    double lat1 = lt1;
    double long1 = lg1;
    double lat2 = lt2;
    double long2 = lg2;

    double dist, deg, az, baz;

    double[] results;
    results = new double[4];

    double ec2, onemec2, eps, temp;
    double lat1rad, long1rad, lat2rad, long2rad, thg;
    double a, b, c, d, e, f, g, h;
    double a1, b1, c1, d1, e1, f1, g1, h1;
    double sc, sd, ss;
    double v1, v2, y2, z1, z2;
    double c0, c2, c4, e3;
    double cosa12, sina12, a12;
    double sinthi, sinthk, tanthi, tanthk, costhi, costhk;
    double t1, p1, t2, p2;
    double u2, u2bot, u2top, u1, u1bot;
    double du, pdist, b0, e1p1, sqrte1p1, arg;
    double al, dl, el, a12top, a12bot, e2, x2;
    double xdist, xaz, xbaz, xxdeg;

    /*
Calculations are based upon lat1 reference slong1roid of 1968 and
are defined by lat1 major radius (RAD) and lat1 flattening (FL).
     */
    double c00 = 1.0, c01 = 0.25, c02 = -0.046875, c03 = 0.01953125;
    double c21 = -0.125, c22 = 0.03125, c23 = -0.0146484375;
    double c42 = -0.00390625, c43 = 0.0029296875;

    double fl = 0.00335293;
    double twopideg = 360.0;
    double degtokm = 111.3199;

    /* - Initialize.             */
    ec2 = 2. * fl - fl * fl;
    onemec2 = 1. - ec2;
    eps = 1. + ec2 / onemec2;

    /*
* - Convert event location to radians.
*   (Equations are unstable for latidudes of exactly 0 degrees.)
     */
    temp = lat1;
    if (temp == 0.) {
      temp = 1.0e-08;
    }
    lat1rad = TORAD * temp;
    long1rad = TORAD * long1;

    /*
* - Must convert from geographic to geocentric coordinates in order
*   to use lat1 slong1rical trig equations.  This requires a latitude
*   correction given by: 1-EC2=1-2*FL+FL*FL
     */
    thg = Math.atan(onemec2 * Math.tan(lat1rad));
    d = Math.sin(long1rad);
    e = -Math.cos(long1rad);
    f = -Math.cos(thg);
    c = Math.sin(thg);
    a = f * e;
    b = -f * d;
    g = -c * e;
    h = c * d;

    temp = lat2;
    if (temp == 0.) {
      temp = 1.0e-08;
    }
    lat2rad = TORAD * temp;
    long2rad = TORAD * long2;

    /* -- Calculate some trig constants. */
    thg = Math.atan(onemec2 * Math.tan(lat2rad));
    d1 = Math.sin(long2rad);
    e1 = -Math.cos(long2rad);
    f1 = -Math.cos(thg);
    c1 = Math.sin(thg);
    a1 = f1 * e1;
    b1 = -f1 * d1;
    g1 = -c1 * e1;
    h1 = c1 * d1;
    sc = a * a1 + b * b1 + c * c1;

    /* - Slong1rical trig relationships used to compute angles.  */
    arg = ((a - a1) * (a - a1) + (b - b1) * (b - b1) + (c - c1) * (c - c1))
            * ((a + a1) * (a + a1) + (b + b1) * (b + b1) + (c + c1) * (c + c1));
    sd = 0.5 * Math.sqrt(arg);
    xxdeg = Math.atan2(sd, sc) * TODEG;
    if (xxdeg < 0.) {
      xxdeg = xxdeg + twopideg;
    }
    deg = xxdeg;

    ss = ((a1 - d) * (a1 - d) + (b1 - e) * (b1 - e) + (c1 * c1) - 2.);
    sc = ((a1 - g) * (a1 - g) + (b1 - h) * (b1 - h) + (c1 - f) * (c1 - f) - 2.);
    xaz = Math.atan2(ss, sc) * TODEG;
    if (xaz < 0.) {
      xaz = xaz + twopideg;
    }
    az = xaz;

    ss = ((a - d1) * (a - d1) + (b - e1) * (b - e1) + (c * c) - 2.);
    sc = ((a - g1) * (a - g1) + (b - h1) * (b - h1) + (c - f1) * (c - f1) - 2.);
    xbaz = Math.atan2(ss, sc) * TODEG;
    if (xbaz < 0.) {
      xbaz = xbaz + twopideg;
    }
    baz = xbaz;

    /*
* - Now compute lat1 distance between lat1 two points using Rudoe's
*   formula given in GEODESY, section 2.15(b).
*   (lat1re is some numerical problem with lat1 following formulae.
*   If lat1 station is in lat1 soulat1rn hemislong1re and lat1 event in
*   in lat1 norlat1rn, lat1se equations give lat1 longer, not lat1
*   shorter distance between lat1 two locations.  Since lat1 equations
*   are fairly messy, lat1 simplist solution is to reverse lat1
*   meanings of lat1 two locations for this case.)
     */
    if (lat2rad > 0.) {
      t1 = lat2rad;
      p1 = long2rad;
      t2 = lat1rad;
      p2 = long1rad;
    } else {
      t1 = lat1rad;
      p1 = long1rad;
      t2 = lat2rad;
      p2 = long2rad;
    }
    el = ec2 / onemec2;
    e1 = 1. + el;
    costhi = Math.cos(t1);
    costhk = Math.cos(t2);
    sinthi = Math.sin(t1);
    sinthk = Math.sin(t2);
    tanthi = sinthi / costhi;
    tanthk = sinthk / costhk;
    al = tanthi / (e1 * tanthk)
            + ec2 * Math.sqrt((e1 + (tanthi * tanthi)) / (e1 + (tanthk * tanthk)));
    dl = p1 - p2;
    a12top = Math.sin(dl);
    a12bot = (al - Math.cos(dl)) * sinthk;
    a12 = Math.atan2(a12top, a12bot);
    cosa12 = Math.cos(a12);
    sina12 = Math.sin(a12);
    e1 = el * (((costhk * cosa12) * (costhk * cosa12)) + (sinthk * sinthk));
    e2 = e1 * e1;
    e3 = e1 * e2;
    c0 = c00 + c01 * e1 + c02 * e2 + c03 * e3;
    c2 = c21 * e1 + c22 * e2 + c23 * e3;
    c4 = c42 * e2 + c43 * e3;
    v1 = EARTHRADIUS / Math.sqrt(1. - ec2 * (sinthk * sinthk));
    v2 = EARTHRADIUS / Math.sqrt(1. - ec2 * (sinthi * sinthi));
    z1 = v1 * (1. - ec2) * sinthk;
    z2 = v2 * (1. - ec2) * sinthi;
    x2 = v2 * costhi * Math.cos(dl);
    y2 = v2 * costhi * Math.sin(dl);
    e1p1 = e1 + 1.;
    sqrte1p1 = Math.sqrt(e1p1);
    u1bot = sqrte1p1 * cosa12;
    u1 = Math.atan2(tanthk, u1bot);
    u2top = v1 * sinthk + e1p1 * (z2 - z1);
    u2bot = sqrte1p1 * (x2 * cosa12 - y2 * sinthk * sina12);
    u2 = Math.atan2(u2top, u2bot);
    b0 = v1 * Math.sqrt(1. + el * ((costhk * cosa12) * (costhk * cosa12))) / e1p1;
    du = u2 - u1;
    pdist = b0 * (c2 * (Math.sin(2. * u2) - Math.sin(2. * u1))
            + c4 * (Math.sin(4. * u2) - Math.sin(4. * u1)));
    xdist = Math.abs(b0 * c0 * du + pdist);
    if (Math.abs(xdist - degtokm * xxdeg) > 100.0) {
      System.out.print("ERROR in computing distance and azimuth 1=" + lt1 + "," + lg1 + " 2=" + lt2 + "," + lg2 + "\n");
    }
    dist = xdist;

    results[0] = dist;
    results[1] = deg;
    results[2] = az;
    results[3] = baz;

    return results;

  }

  /**
   * Calculate the distance az and back az from two lats and longs. Differs from distaz2(). This
   * reports an error and returns bad distances if something odd happens
   *
   * @param lt1 First latitude
   * @param lg1 First Longitude
   * @param lt2 2nd latitude
   * @param lg2 2nd longitude
   * @return array of 4 with dist(km), dist(deg), az, and baz
   */
  public static double[] distaz(double lt1, double lg1, double lt2, double lg2) {
    double[] results = new double[4];
    distaz(lt1, lg1, lt2, lg2, results);
    return results;
  }

  /**
   * Calculate the distance az and back az from two lats and longs. Differs from distaz2(). This
   * reports an error and returns bad distances if something odd happens
   *
   * @param lt1 First latitude
   * @param lg1 First Longitude
   * @param lt2 2nd latitude
   * @param lg2 2nd longitude
   * @param results Must be at least 4 long returned with dist(km), dist(deg), az, and baz
   * @return the users array with 4 values that is results
   */
  public static double[] distaz(double lt1, double lg1, double lt2, double lg2, double[] results) {

    double lat1 = lt1;
    double long1 = lg1;
    double lat2 = lt2;
    double long2 = lg2;

    double dist, deg, az, baz;

    double ec2, onemec2, eps, temp;
    double lat1rad, long1rad, lat2rad, long2rad, thg;
    double a, b, c, d, e, f, g, h;
    double a1, b1, c1, d1, e1, f1, g1, h1;
    double sc, sd, ss;
    double v1, v2, y2, z1, z2;
    double c0, c2, c4, e3;
    double cosa12, sina12, a12;
    double sinthi, sinthk, tanthi, tanthk, costhi, costhk;
    double t1, p1, t2, p2;
    double u2, u2bot, u2top, u1, u1bot;
    double du, pdist, b0, e1p1, sqrte1p1, arg;
    double al, dl, el, a12top, a12bot, e2, x2;
    double xdist, xaz, xbaz, xxdeg;

    /*
Calculations are based upon lat1 reference slong1roid of 1968 and
are defined by lat1 major radius (RAD) and lat1 flattening (FL).
     */
    double c00 = 1.0, c01 = 0.25, c02 = -0.046875, c03 = 0.01953125;
    double c21 = -0.125, c22 = 0.03125, c23 = -0.0146484375;
    double c42 = -0.00390625, c43 = 0.0029296875;

    double fl = 0.00335293;
    double twopideg = 360.0;
    double degtokm = 111.3199;

    /* - Initialize.             */
    ec2 = 2. * fl - fl * fl;
    onemec2 = 1. - ec2;
    eps = 1. + ec2 / onemec2;

    /*
* - Convert event location to radians.
*   (Equations are unstable for latidudes of exactly 0 degrees.)
     */
    temp = lat1;
    if (temp == 0.) {
      temp = 1.0e-08;
    }
    lat1rad = TORAD * temp;
    long1rad = TORAD * long1;

    /*
* - Must convert from geographic to geocentric coordinates in order
*   to use lat1 slong1rical trig equations.  This requires a latitude
*   correction given by: 1-EC2=1-2*FL+FL*FL
     */
    thg = Math.atan(onemec2 * Math.tan(lat1rad));
    d = Math.sin(long1rad);
    e = -Math.cos(long1rad);
    f = -Math.cos(thg);
    c = Math.sin(thg);
    a = f * e;
    b = -f * d;
    g = -c * e;
    h = c * d;

    temp = lat2;
    if (temp == 0.) {
      temp = 1.0e-08;
    }
    lat2rad = TORAD * temp;
    long2rad = TORAD * long2;

    /* -- Calculate some trig constants. */
    thg = Math.atan(onemec2 * Math.tan(lat2rad));
    d1 = Math.sin(long2rad);
    e1 = -Math.cos(long2rad);
    f1 = -Math.cos(thg);
    c1 = Math.sin(thg);
    a1 = f1 * e1;
    b1 = -f1 * d1;
    g1 = -c1 * e1;
    h1 = c1 * d1;
    sc = a * a1 + b * b1 + c * c1;

    /* - Slong1rical trig relationships used to compute angles.  */
    arg = ((a - a1) * (a - a1) + (b - b1) * (b - b1) + (c - c1) * (c - c1))
            * ((a + a1) * (a + a1) + (b + b1) * (b + b1) + (c + c1) * (c + c1));
    sd = 0.5 * Math.sqrt(arg);
    xxdeg = Math.atan2(sd, sc) * TODEG;
    if (xxdeg < 0.) {
      xxdeg = xxdeg + twopideg;
    }
    deg = xxdeg;

    ss = ((a1 - d) * (a1 - d) + (b1 - e) * (b1 - e) + (c1 * c1) - 2.);
    sc = ((a1 - g) * (a1 - g) + (b1 - h) * (b1 - h) + (c1 - f) * (c1 - f) - 2.);
    xaz = Math.atan2(ss, sc) * TODEG;
    if (xaz < 0.) {
      xaz = xaz + twopideg;
    }
    az = xaz;

    ss = ((a - d1) * (a - d1) + (b - e1) * (b - e1) + (c * c) - 2.);
    sc = ((a - g1) * (a - g1) + (b - h1) * (b - h1) + (c - f1) * (c - f1) - 2.);
    xbaz = Math.atan2(ss, sc) * TODEG;
    if (xbaz < 0.) {
      xbaz = xbaz + twopideg;
    }
    baz = xbaz;

    /*
* - Now compute lat1 distance between lat1 two points using Rudoe's
*   formula given in GEODESY, section 2.15(b).
*   (lat1re is some numerical problem with lat1 following formulae.
*   If lat1 station is in lat1 soulat1rn hemislong1re and lat1 event in
*   in lat1 norlat1rn, lat1se equations give lat1 longer, not lat1
*   shorter distance between lat1 two locations.  Since lat1 equations
*   are fairly messy, lat1 simplist solution is to reverse lat1
*   meanings of lat1 two locations for this case.)
     */
    if (lat2rad > 0.) {
      t1 = lat2rad;
      p1 = long2rad;
      t2 = lat1rad;
      p2 = long1rad;
    } else {
      t1 = lat1rad;
      p1 = long1rad;
      t2 = lat2rad;
      p2 = long2rad;
    }
    el = ec2 / onemec2;
    e1 = 1. + el;
    costhi = Math.cos(t1);
    costhk = Math.cos(t2);
    sinthi = Math.sin(t1);
    sinthk = Math.sin(t2);
    tanthi = sinthi / costhi;
    tanthk = sinthk / costhk;
    al = tanthi / (e1 * tanthk)
            + ec2 * Math.sqrt((e1 + (tanthi * tanthi)) / (e1 + (tanthk * tanthk)));
    dl = p1 - p2;
    a12top = Math.sin(dl);
    a12bot = (al - Math.cos(dl)) * sinthk;
    a12 = Math.atan2(a12top, a12bot);
    cosa12 = Math.cos(a12);
    sina12 = Math.sin(a12);
    e1 = el * (((costhk * cosa12) * (costhk * cosa12)) + (sinthk * sinthk));
    e2 = e1 * e1;
    e3 = e1 * e2;
    c0 = c00 + c01 * e1 + c02 * e2 + c03 * e3;
    c2 = c21 * e1 + c22 * e2 + c23 * e3;
    c4 = c42 * e2 + c43 * e3;
    v1 = EARTHRADIUS / Math.sqrt(1. - ec2 * (sinthk * sinthk));
    v2 = EARTHRADIUS / Math.sqrt(1. - ec2 * (sinthi * sinthi));
    z1 = v1 * (1. - ec2) * sinthk;
    z2 = v2 * (1. - ec2) * sinthi;
    x2 = v2 * costhi * Math.cos(dl);
    y2 = v2 * costhi * Math.sin(dl);
    e1p1 = e1 + 1.;
    sqrte1p1 = Math.sqrt(e1p1);
    u1bot = sqrte1p1 * cosa12;
    u1 = Math.atan2(tanthk, u1bot);
    u2top = v1 * sinthk + e1p1 * (z2 - z1);
    u2bot = sqrte1p1 * (x2 * cosa12 - y2 * sinthk * sina12);
    u2 = Math.atan2(u2top, u2bot);
    b0 = v1 * Math.sqrt(1. + el * ((costhk * cosa12) * (costhk * cosa12))) / e1p1;
    du = u2 - u1;
    pdist = b0 * (c2 * (Math.sin(2. * u2) - Math.sin(2. * u1))
            + c4 * (Math.sin(4. * u2) - Math.sin(4. * u1)));
    xdist = Math.abs(b0 * c0 * du + pdist);
    if (Math.abs(xdist - degtokm * xxdeg) > 100.0) {
      //System.out.print("ERROR in computing distance and azimuth 1="+lt1+","+lg1+" 2="+lt2+","+lg2+
      //    " xdeg="+xxdeg+" xdist="+xdist+" deg="+deg+"\n");
      dist = xxdeg * degtokm;
      //Util.exit(-1);
    } else {
      dist = xdist;
    }

    results[0] = dist;
    results[1] = deg;
    results[2] = az;
    results[3] = baz;

    return results;

  }

  /**
   * DCK - I moved this from a similar named Java file of Harley's and modified it to be static and
   * return answers in an array
   *
   * @param olat latitude to project from
   * @param olon Longitude to project from
   * @param dist Distance in km
   * @param azimuth Azimuth from latitude and longitude measured clockwise from North in degrees
   * @param answer latitude in index 0 and longitude in index 1, user must create a double array of
   * at least dimension 2
   */
  public static void computeLatLon(double olat, double olon, double dist, double azimuth, double[] answer) {

    /*
    Calculations are based upon the reference spheroid of 1967.
    See GEODESY by Bomford for definitions and reference values.
    Reference spheriod is found in GEODESY by Bomford (page 426).  Definitions
    for flattening, eccentricity and second eccentricity are found in
    Appendix A (page 646).
     */
    double a = 6378.16, b = 6356.775;
    double a1 = 0.25, b1 = -0.125, c1 = -0.0078125;
    double g00 = 1.0, g01 = -0.25, g02 = 0.109375, g03 = -0.058139535;
    double g21 = 0.125, g22 = -0.06250, g23 = 0.034667969;
    double g42 = 0.01953125, g43 = -0.01953125;
    double g63 = 0.004720052;

    /* - Initialize.             */
    double fl = (a - b) / a;
    /* earth flattening                 */
    double ec2 = 2.0 * fl - fl * fl;
    /* square of eccentricity           */
    double ec = Math.sqrt(ec2);
    /* eccentricity                     */
    double eps = ec2 / (1.0 - ec2);
    /* second eccentricity e'*e' = eps  */
    //double TORAD = Math.PI / 180.0;
    //double TODEG = 1.0 / TORAD;

    /* - Convert location to radians.                                    */

    double temp = olat;
    if (temp == 0.0) {
      temp = 1.0e-08;
    }
    double latrad = TORAD * temp;
    double lonrad = TORAD * olon;
    double azmrad = azimuth * TORAD;

    /*  Compute some of the easier terms               */
    double coslat = Math.cos(latrad);
    double sinlat = Math.sin(latrad);
    double cosazm = Math.cos(azmrad);
    double sinazm = Math.sin(azmrad);
    double tanazm = Math.tan(azmrad);
    double tanlat = Math.tan(latrad);

    double C_sqr = coslat * coslat * cosazm * cosazm + sinlat * sinlat;
    double C = Math.sqrt(C_sqr);
    double eps0 = C_sqr * eps;
    double v1 = a / Math.sqrt(1.0 - ec2 * sinlat * sinlat);
    /* Radii of curvature    */
    double b0 = v1 * Math.sqrt(1.0 + eps * coslat * coslat * cosazm * cosazm) / (1.0 + eps0);

    double g0 = g00 + g01 * eps0 + g02 * eps0 * eps0 + g03 * eps0 * eps0 * eps0;
    double g2 = g21 * eps0 + g22 * eps0 * eps0 + g23 * eps0 * eps0 * eps0;
    double g4 = g42 * eps0 * eps0 + g43 * eps0 * eps0 * eps0;
    double g6 = g63 * eps0 * eps0 * eps0;

    double tanu1p = tanlat / (cosazm * Math.sqrt(1.0 + eps0));
    double u1pbot = cosazm * Math.sqrt(1.0 + eps0);
    double u1p = Math.atan2(tanlat, u1pbot);
    double sig1 = 0.5 * (2.0 * u1p - (a1 * eps0 + b1 * eps0 * eps0) * Math.sin(2.0 * u1p)
            + c1 * eps0 * eps0 * Math.sin(4.0 * u1p));

    /*
      aa = ( sig2 - sig1 ) page 117, GEODESY
      bb = ( sig1 + sig2 ) page 117, GEODESY
     */
    double aa = (dist * g0) / b0;
    double bb = 2.0 * sig1 + aa;
    double u2p = u1p + aa + 2.0 * g2 * Math.sin(aa) * Math.cos(bb) + 2.0 * g4 * Math.sin(2. * aa) * Math.cos(2. * bb)
            + 2. * g6 * Math.sin(3. * aa) * Math.cos(3. * bb);
    double sinu1 = tanlat / Math.sqrt(1.0 + eps + tanlat * tanlat);
    double sinu2 = ((b0 * C) / b) * Math.sin(u2p) - ((eps - eps0) / (1 + eps0)) * sinu1;
    double u2 = Math.asin(sinu2);

    double a0 = b0 * Math.sqrt(1.0 + eps0);
    double tanmu = sinlat * tanazm;
    double mu = Math.atan(tanmu);

    /*
   This calculation of latitude and longitude is an alternative to Rudoe's
   formulation.  See GEODESY by Bomford (page 118).
     */
    double sd = (ec2 * v1 * sinlat * coslat * sinazm) / (1. - ec2 * coslat * coslat * sinazm * sinazm);
    double x2 = a0 * Math.cos(u2p) * Math.cos(mu) + b0 * Math.sin(u2p) * Math.sin(mu) * coslat * sinazm
            + sd * sinlat * sinazm;
    double y2 = -a0 * Math.cos(u2p) * Math.sin(mu) + b0 * Math.sin(u2p) * Math.cos(mu) * coslat * sinazm
            + sd * cosazm;
    double z2 = b0 * C * Math.sin(u2p) - (sd * coslat * sinazm) / (1.0 + eps);
    double ztop = (1.0 + eps) * z2;
    double zbot = Math.sqrt(x2 * x2 + y2 * y2);

    answer[0] = Math.atan2(ztop, zbot) * TODEG;
    answer[1] = (Math.atan(y2 / x2) + lonrad) * TODEG;
    if ((Math.atan(y2 / x2) + lonrad) < 0.0) {
      answer[1] = ((Math.atan(y2 / x2) + lonrad) * TODEG);
    }

  }

  public static void main(String[] args) {

    DecimalFormat df5 = new DecimalFormat("0.00000");
    if (args.length == 0) {
      System.out.println("Usage : distaz lat1 long1 lat2 long2");
      System.out.println("Usage : distaz -p lat long distkm azimuth");
      System.exit(1);
    }
    if (args[0].equals("-p")) {
      double lat1 = Double.parseDouble(args[1]);
      double long1 = Double.parseDouble(args[2]);
      double lat2 = Double.parseDouble(args[3]);
      double long2 = Double.parseDouble(args[4]);
      double[] ans = new double[4];
      Distaz.computeLatLon(lat1, long1, lat2, long2, ans);
      System.out.println("lat=" + Util.df24.format(ans[0]) + " long=" + Util.df24.format(ans[1])
              + " lat/long1=" + lat1 + " " + long1 + " dist(deg)=" + lat2 + " azimuth=" + long2);
      System.exit(0);

    }

    double lat1 = Double.parseDouble(args[0]);
    double long1 = Double.parseDouble(args[1]);
    double lat2 = Double.parseDouble(args[2]);
    double long2 = Double.parseDouble(args[3]);
    double[] ans = new double[4];
    Distaz.distaz(lat1, long1, lat2, long2, ans);
    System.out.println("diff = " + df5.format(ans[0]) + " km "
            + df5.format(ans[1]) + " km azimuth=" + Util.df21.format(ans[2]) + " deg bazm=" + Util.df21.format(ans[3]) + " deg lat/long1=" + lat1 + " " + long1 + " lat/long2=" + lat2 + " " + long2);
  }
}
