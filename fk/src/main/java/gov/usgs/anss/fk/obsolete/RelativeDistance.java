/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */

package gov.usgs.anss.fk.obsolete;

/**
 * RelativeDistance.java  - This is exactly Distaz.java from the Util package.  This
 * function moved to obsolete code and replace with Distax.
 * DCK - this might be better as a static function passing 
 * back an array with the 4 results.  
 *
 * Created on February 8, 2007, 11:30 AM
 *
 * @author benz
 */
public class RelativeDistance {
    
    public final static double PI=Math.PI;
    public final static double RADIUS=6378.160;   // semi-major axis
    public double lat1,lat2,long1,long2;
    public double az;
    public double baz;
    public double dist;
    public double deg;
    
    /** Creates a new instance of RelativeDistance
   * @param lt1 latitude1
   * @param lg1 longitude1
   * @param lt2 latitude2
   * @param lg2 longitude2
   */
    public RelativeDistance(double lt1, double lg1, double lt2, double lg2) {
      lat1=lt1; long1=lg1; lat2=lt2; long2=lg2;

      double torad, todeg, ec2, onemec2, temp;
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

      double c00=1.0, c01=0.25, c02=-0.046875, c03=0.01953125;
      double c21=-0.125,c22=0.03125,c23=-0.0146484375;
      double c42=-0.00390625,c43=0.0029296875;

      double fl=0.00335293;
      double twopideg=360.0; 
      double degtokm=111.3199;

/* - Initialize.             */

      torad = PI / 180.0;
      todeg = 1.0 / torad;
      ec2=2.*fl-fl*fl;
      onemec2=1.-ec2;

/*
* - Convert event location to radians.
*   (Equations are unstable for latidudes of exactly 0 degrees.)
*/

      temp=lat1;
      if(temp == 0.) temp=1.0e-08;
      lat1rad=torad*temp;
      long1rad=torad*long1;

/*
* - Must convert from geographic to geocentric coordinates in order
*   to use lat1 slong1rical trig equations.  This requires a latitude
*   correction given by: 1-EC2=1-2*FL+FL*FL
*/

      thg=Math.atan(onemec2*Math.tan(lat1rad));
      d=Math.sin(long1rad);
      e=-Math.cos(long1rad);
      f=-Math.cos(thg);
      c=Math.sin(thg);
      a= f*e;
      b=-f*d;
      g=-c*e;
      h=c*d;

        temp=lat2;
        if(temp == 0.)temp=1.0e-08;
        lat2rad=torad*temp;
        long2rad=torad*long2;

/* -- Calculate some trig constants. */
        thg=Math.atan(onemec2*Math.tan(lat2rad));
        d1=Math.sin(long2rad);
        e1=-Math.cos(long2rad);
        f1=-Math.cos(thg);
        c1=Math.sin(thg);
        a1=f1*e1;
        b1=-f1*d1;
        g1=-c1*e1;
        h1=c1*d1;
        sc=a*a1+b*b1+c*c1;

/* - Slong1rical trig relationships used to compute angles.  */

        arg=((a-a1)*(a-a1)+(b-b1)*(b-b1)+(c-c1)*(c-c1))*
            ((a+a1)*(a+a1)+(b+b1)*(b+b1)+(c+c1)*(c+c1));
        sd=0.5*Math.sqrt(arg);
        xxdeg=Math.atan2(sd,sc)*todeg;
        if(xxdeg < 0.)xxdeg=xxdeg+twopideg;
        deg = xxdeg;

        ss = ((a1-d)*(a1-d)+(b1-e)*(b1-e)+(c1*c1)-2.);
        sc = ((a1-g)*(a1-g)+(b1-h)*(b1-h)+(c1-f)*(c1-f)-2.);
        xaz=Math.atan2(ss,sc)*todeg;
        if(xaz < 0.)xaz=xaz+twopideg;
        az = xaz;

        ss=((a-d1)*(a-d1)+(b-e1)*(b-e1)+(c*c)-2.);
        sc=((a-g1)*(a-g1)+(b-h1)*(b-h1)+(c-f1)*(c-f1)-2.);
        xbaz=Math.atan2(ss,sc)*todeg;
        if(xbaz < 0.)xbaz=xbaz+twopideg;
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
          
        if(lat2rad > 0.) { 
            t1=lat2rad; 
            p1=long2rad; 
            t2=lat1rad;
            p2=long1rad;
          } else {
            t1=lat1rad;
            p1=long1rad;
            t2=lat2rad;
            p2=long2rad;
          }
          el=ec2/onemec2;
          e1=1.+el;
          costhi=Math.cos(t1);
          costhk=Math.cos(t2);
          sinthi=Math.sin(t1);
          sinthk=Math.sin(t2);
          tanthi=sinthi/costhi;
          tanthk=sinthk/costhk;
          al=tanthi/(e1*tanthk)+
             ec2*Math.sqrt((e1+(tanthi*tanthi))/(e1+(tanthk*tanthk)));
          dl=p1-p2;
          a12top=Math.sin(dl);
          a12bot=(al-Math.cos(dl))*sinthk;
          a12=Math.atan2(a12top,a12bot);
          cosa12=Math.cos(a12);
          sina12=Math.sin(a12);
          e1=el*(((costhk*cosa12)*(costhk*cosa12))+(sinthk*sinthk));
          e2=e1*e1;
          e3=e1*e2;
          c0=c00+c01*e1+c02*e2+c03*e3;
          c2=c21*e1+c22*e2+c23*e3;
          c4=c42*e2+c43*e3;
          v1=RADIUS/Math.sqrt(1.-ec2*(sinthk*sinthk));
          v2=RADIUS/Math.sqrt(1.-ec2*(sinthi*sinthi));
          z1=v1*(1.-ec2)*sinthk;
          z2=v2*(1.-ec2)*sinthi;
          x2=v2*costhi*Math.cos(dl);
          y2=v2*costhi*Math.sin(dl);
          e1p1=e1+1.;
          sqrte1p1=Math.sqrt(e1p1);
          u1bot=sqrte1p1*cosa12;
          u1=Math.atan2(tanthk,u1bot);
          u2top=v1*sinthk+e1p1*(z2-z1);
          u2bot=sqrte1p1*(x2*cosa12-y2*sinthk*sina12);
          u2=Math.atan2(u2top,u2bot);
          b0=v1*Math.sqrt(1.+el*((costhk*cosa12)*(costhk*cosa12)))/e1p1;
          du=u2 -u1;
          pdist=b0*(c2*(Math.sin(2.*u2)-Math.sin(2.*u1))+
             c4*(Math.sin(4.*u2)-Math.sin(4.*u1)));
          xdist=Math.abs(b0*c0*du+pdist);
          if( Math.abs(xdist-degtokm*xxdeg) > 100.0) {
              throw new RuntimeException("ERROR in computing distance and azimuth\n");
          }
          dist=xdist;        
    }
    public double getAzimuth() {return az;}
    public double getDeg() {return deg;}
    public double getBackAzimuth() {return baz;}
    public double getDistKM() {return dist;}
    @Override
    public String toString() {return "az="+az+" baz="+baz+" dist="+dist+" deg="+deg+" from "+lat1+" "+long1+" "+lat2+" "+long2;}
    static public void main(String [] args) {
      RelativeDistance a = new RelativeDistance(0.,10., 10., 30.);
      System.out.println(a.toString()); // main()
      System.out.println(" az="+a.az+" baz="+a.baz+" az.azimuth()="+a.getAzimuth());//main()
    }
}    
