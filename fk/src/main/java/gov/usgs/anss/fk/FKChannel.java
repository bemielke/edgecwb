/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */

package gov.usgs.anss.fk;
import gov.usgs.anss.edgethread.EdgeThread;
import gov.usgs.anss.util.Distaz;
import gov.usgs.anss.util.Util;
/*
 * FKStation.java
 *
 * Created on February 13, 2007, 11:33 AM

 * Class for setting the channel information per array
 * @author benz
 */
public  class FKChannel {
  /**
   * convert degrees to radians
   */
  public final static double torad = Math.PI/180.0;
  private final String arrayname;
  private final String channel;
  private final double lat;
  private final double lon;
  private final double ele;
  private final double cf;
  private double xrel;
  private double yrel;
  private boolean use;    // This is a working variable for time series reading
  private boolean ref;
  private final EdgeThread par;
  //RelativeDistance rel;
  private final StringBuilder tmpsb = new StringBuilder(50);
  @Override
  public String toString() {return toStringBuilder(null).toString();}
  public StringBuilder toStringBuilder(StringBuilder tmp) {
    StringBuilder sb = tmp;
    if(sb == null) {
      sb=Util.clear(tmpsb);
    }
    synchronized(sb) {
      sb.append(arrayname).append(":").append(channel).
              append(" coord=").append(lat).append(" ").append(lon).append(" ").append(ele).
              append(" cf=").append(Util.df23(cf)).append(" xrel=").append(Util.df23(xrel)).append(" yrel=").append(Util.df23(yrel)).
              append(" use=").append(use?"T":"F").append(" ref=").append(ref?"T":"F");
    }
    return sb;
  }  
  /**
   * Array name
   * @return Array name
   */
  public String  getArrayname(){return arrayname;}
  /**
   * Channel information (NSCL)
   * @return channel
   */
  public String  getChannel() {return channel;}
  /**
   * Latitude
   * @return lat
   */
  public double  getLat()     {return lat;}
  /**
   * Longitude
   * @return lon
   */
  public double  getLon()     {return lon;}
  /**
   * Elevation
   * @return ele
   */
  public double  getEle()     {return ele;}
  /**
   * east-west distance in degrees relative to reference station
   * @return xrel
   */
  public double  getXrel()    {return xrel;}
  /**
   * north-south distance in degrees relative to reference station
   * @return yrel
   */
  public double  getYrel()    {return yrel;}
  /**
   * Channel correction factor (not used)
   * @return cf
   */
  public double  getCF()      {return cf;}
  /**
   * Tag for identifying use
   * @return use
   */
  public boolean getUse()     {return use;}
  public void setUse(boolean t) {use=t;}
  /**
   * reference
   * @return ref
   */
  public boolean getRef()     {return ref;}
  /**
   * label for whether channel is reference
   * @param r reference
   */
  public void setRef(boolean r) { ref = r; }
  
    /**
   * Construct a Channel object
   * @param name network name
   * @param chan string containing the NSCL
   * @param lt channel latitude (negative, south)
   * @param lg channel longitude (negative, west)
   * @param el channel elevation (not used in FK analysis)
   * @param cr channel corrections factor (not used in FK analysis)
   * @param us channel usage: =0, do not use
   * @param parent The EdgeThread for any logging
   */
  public FKChannel(String name, String chan, double lt, double lg, double el, double cr, boolean us, EdgeThread parent) {
    par = parent;
    arrayname=name; channel=chan; lat=lt; lon=lg; ele=el; cf=cr; use=us;
  }
  /**
   * calculate relative distance between reference station and channel
   * @param ref reference channel object
   */
  public void calcRelativeDistance(FKChannel ref) {
   
    double rlat = ref.getLat();
    double rlon = ref.getLon();
        
// 
// Sign Convention:
//     X positive --->  East
//     Y positive --->  North
//        
    double [] results = Distaz.distaz(rlat, rlon, lat, lon);
    xrel = Math.sin(results[2]*torad)*results[0];
    yrel = Math.cos(results[2]*torad)*results[0];
    //RelativeDistance rel = new RelativeDistance(rlat,rlon,lat,lon);
    //double xrelold = Math.sin(rel.az*torad)*rel.dist;
    //double yrelold = Math.cos(rel.az*torad)*rel.dist;
    //if(xrel != xrelold || yrel != yrelold)
    //  par.prta(" "+channel+", "+xrel+", "+yrel+" old "+xrelold+", "+yrelold);    
  }
}
