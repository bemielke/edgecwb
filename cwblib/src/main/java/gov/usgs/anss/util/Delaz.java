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
 * Another implementation of Delaz!
 *
 * @author davidketchum
 */
public final class Delaz {

  public static final double radius = 6371.;
  public static final double rad = Math.PI / 180.;
  public static final double deg = 180. / Math.PI;

  public static double delaz(double lat1, double long1, double lat2, double long2) {
    double elats = Math.sin((90. - lat1) * rad);
    double elatc = Math.cos((90. - lat1) * rad);
    double elons = Math.sin(long1 * rad);
    double elonc = Math.cos(long1 * rad);
    double slats = Math.sin((90. - lat2) * rad);
    double slatc = Math.cos((90. - lat2) * rad);
    double slons = Math.sin(long2 * rad);
    double slonc = Math.cos(long2 * rad);
    double delta;
    double azim;
    if (Math.abs(slats) < 1.e-4) {   // south pole
      delta = (Math.PI - Math.acos(elatc)) * deg;
      azim = Math.PI;

    } else if (Math.abs(elats) < 1.e-4) {  // epicenter is at s. pole
      delta = (Math.PI - Math.acos(slatc)) * deg;
      azim = Math.PI;
    } else {    // The non - wierd cases
      double cosdel = elats * slats * (slonc * elonc + slons * elons) + elatc * slatc;
      double tm1 = slats * (slons * elonc - slonc * elons);
      double tm2 = elats * slatc - elatc * slats * (slonc * elonc + slons * elons);
      double sindel = Math.sqrt(tm1 * tm1 + tm2 * tm2);
      if (sindel == 0. && cosdel == 0.) {
        delta = 0.;
      } else {
        delta = Math.atan2(sindel, cosdel) * deg;
      }
      if (tm1 == 0. && tm2 == 0.) {
        azim = 0.;
      } else {
        azim = Math.atan2(tm1, tm2);
      }
      if (azim < 0.) {
        azim = 2. * Math.PI + azim;
      }
    }
    /*double [] d = Distaz.distaz(lat1, long1, lat2, long2);
    if( Math.abs(d[1] - delta) > 0.4) 
      Util.prt("delta="+delta+" "+d[1]+" az="+(deg*azim)+" "+d[2]+" "+lat1+" "+long1+" "+lat2+" "+long2);*/
    return delta;
  }

  public static void main(String[] args) {
    /*for(int i=-90; i<=90; i++) {
      for(int j=-180; j<=180; j++) {
        double lat1=i;
        double long1=j;
        double lat2=-i;
        double long2=-j;
        double delta = Delaz.delaz(lat1,long1,lat2,long2);
     }
    }*/
    DecimalFormat df5 = new DecimalFormat("0.00000");
    if (args.length != 4) {
      System.out.println("Usage : delaz lat1 long1 lat2 long2");
      System.exit(1);
    }
    double lat1 = Double.parseDouble(args[0]);
    double long1 = Double.parseDouble(args[1]);
    double lat2 = Double.parseDouble(args[2]);
    double long2 = Double.parseDouble(args[3]);
    System.out.println("diff = " + df5.format(Delaz.delaz(lat1, long1, lat2, long2)) + " dg "
            + df5.format(Delaz.delaz(lat1, long1, lat2, long2) * 110.57)
            + " km lat/long1=" + lat1 + " " + long1 + " lat/long2=" + lat2 + " " + long2);
  }
}
