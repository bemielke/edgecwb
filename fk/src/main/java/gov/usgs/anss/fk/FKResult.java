/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */

package gov.usgs.anss.fk;
//import gov.usgs.alarm.SendEvent;
//import gov.usgs.anss.db.DBAccess;
//import gov.usgs.anss.db.DBConnectionThread;
import gov.usgs.anss.db.DBMessageQueuedClient;
import gov.usgs.anss.util.Util;
import java.util.GregorianCalendar;
//import java.sql.*;
//import java.io.PrintStream;
import gov.usgs.anss.edgethread.EdgeThread;
/** This stores the FK  configuration parameters and the grid used in the analysis
 *
 * @author benz
 */
public class FKResult {
  public static final int MAX_POWER=3;
  private long begtime;
  private double tlength;
  private double totalPower;
  private double powerNorm;
  private double powerNormAvg;
  private double powerRatioLimit=5.2;
  private double powerRatio;
  private final double [] sxmax = new double[MAX_POWER];
  private final double [] symax = new double[MAX_POWER];
  private double maxslw;
  private double azi;
  private double aziApplied;
  private final double [] power = new double[MAX_POWER];
  private double lpcorner;
  private double hpcorner;
  private double [] kxy;
  private double mean;
  private double std;
  private double meanavg;
  private double stdavg;
  int navg;
  //private static final Integer mutex=Util.nextMutex();         // This is the mutex on dbconn to allow use of a single connection
  private static DBMessageQueuedClient dbMessageQueue;
//  private static DBConnectionThread dbconn;     // DB to the fkresults database
  //private static PreparedStatement insert;      // since all share this it must be used under the mutex
  //private static boolean reopening;
  //private static long ndiscard;
  //private final java.sql.Timestamp time = new Timestamp(0l);
  //private PrintStream prt;
  private final double [] azimuths = new double[3];
  private final double [] adiff = new double[3];
  private int maxAziDiff;
  private final StringBuilder msg = new StringBuilder(100);
  
  public boolean writeStatistics(EdgeThread par, String channel) {
    try {
      if(dbMessageQueue == null) {
        String dbMessageServer=Util.getProperty("StatusServer");
        dbMessageQueue = new DBMessageQueuedClient(dbMessageServer,7985, 50,500,par);
        par.prta("FKResult : message Server to "+dbMessageServer+"/"+7985);                 
      }
      synchronized(msg) {
        Util.clear(msg).append("fk^fkresult^time=").append(Util.ascdatetime(begtime)).
            append(";seedname=").append(channel).
            append(";totalpower=").append(Util.ef4(totalPower)).
            append(";power1=").append(Util.ef4(power[0])).
            append(";power2=").append(Util.ef4(power[1])).
            append(";power3=").append(Util.ef4(power[2])).
            append(";sxmax1=").append(Util.ef4(sxmax[0])).
            append(";sxmax2=").append(Util.ef4(sxmax[1])).
            append(";sxmax3=").append(Util.ef4(sxmax[2])).
            append(";symax1=").append(Util.ef4(symax[0])).
            append(";symax2=").append(Util.ef4(symax[1])).
            append(";symax3=").append(Util.ef4(symax[2])).
            append(";slowness=").append(Util.ef4(maxslw)).
            append(";mean=").append(Util.ef4(mean)).
            append(";std=").append(Util.ef4(std)).
            append(";norm=").append(Util.ef4(powerNorm)).
            append(";maxdiff=").append(Util.ef4(maxAziDiff)).
            append(";meanavg=").append(Util.ef4(meanavg)).
            append(";stdavg=").append(Util.ef4(stdavg)).
            append(";normavg=").append(Util.ef4(powerNormAvg)).
            append(";normratio=").append(Util.ef4(powerNorm/powerNormAvg)).
            append(";azm=").append(Util.ef4(azi)).
            append(";appliedazm=").append(Util.ef4(aziApplied)).
            append(";created=now()");
        //par.prta("FKResult len="+msg.length()+" msg="+msg);
        return dbMessageQueue.queueDBMsg(msg);
      }
    }
    catch(RuntimeException e) {
      e.printStackTrace(par.getPrintStream());
    }
    return false;
  }
  private double getAzimuth(double sxmax, double symax) {
      double azimuth = (Math.atan2(sxmax,symax)/Math.PI)*180.0;
      if( azimuth < 0.0 ) azimuth = azimuth + 360.0;
      if( azimuth == 360.0 ) azimuth = 0.0;
      return azimuth;
  }
  public boolean doStatistics() {
    // Computer the azimuth and max slowness
    azi = (Math.atan2(sxmax[0],symax[0])/Math.PI)*180.0;
    if( azi < 0.0 ) azi = azi + 360.0;
    if( azi == 360.0 ) azi = 0.0;
	
    maxslw = Math.sqrt(symax[0]*symax[0] + sxmax[0]*sxmax[0]);
    for(int j=0; j<3; j++) azimuths[j] = getAzimuth(sxmax[j], symax[j]);
    adiff[0] = azimuths[0] - azimuths[1];
    adiff[1] = azimuths[0] - azimuths[2];
    adiff[2] = azimuths[1] - azimuths[2];
    double maxdiff=-1;
    for(int j=0; j<3; j++) {
      if(adiff[j] < 0.) adiff[j] = -adiff[j];
      if(adiff[j] > 180.) adiff[j] = 360 -adiff[j];  // cannot be further than this
      maxdiff = Math.max(maxdiff, adiff[j]);
    }
    maxAziDiff = (int) Math.round(maxdiff);
     
    // Calculate mean and std from FK array of power
    mean=0.;
    double diff=0;
    for(int i=0; i<kxy.length; i++) mean += kxy[i];
    mean = mean/kxy.length;
    for(int i=0; i<kxy.length; i++) diff += (kxy[i] - mean)*(kxy[i] -mean);
    std = Math.sqrt(diff/(kxy.length -1));
    powerRatio = Math.sqrt(powerNorm / powerNormAvg);
    if(navg == 0) {
      stdavg=std;
      meanavg=mean;
      powerNormAvg=powerNorm;
      navg=1;
    }
    else {
      stdavg = (stdavg*navg+std)/(navg+1);
      meanavg = (meanavg*navg+mean)/(navg+1);
      if(powerRatio < powerRatioLimit)            // If this is a trigger, do not do power norm averaging
        powerNormAvg = (powerNormAvg*navg+powerNorm)/(navg+1);
      navg++;
      if(navg >= 100) navg=99;
    }
    return powerRatio > powerRatioLimit;
  }
  public double getMean() {return mean;}
  public double getStdDev() {return std;}
  public void clear() {
    for(int i=0; i<MAX_POWER; i++) {
      sxmax[i]=0; 
      symax[i]=0;
      power[i]=0;
    }
    totalPower=0.;
  }
  public void chkMax(double pow, double sx, double sy) {
    totalPower += pow;            // Add up the powers
    if(pow < power[2]) return;    // Too small, throw it back
    if(pow > power[0]) {
      power[2] = power[1];
      power[1] = power[0];
      power[0] = pow;
      sxmax[2] = sxmax[1];
      sxmax[1] = sxmax[0];
      sxmax[0] = sx;
      symax[2] = symax[1];
      symax[1] = symax[0];
      symax[0] = sy;
    }
    else if(pow > power[1]) {
      power[2] = power[1];
      power[1] = pow;
      sxmax[2] = sxmax[1];
      sxmax[1] = sx;
      symax[2] = symax[1];
      symax[1] = sy;
    }
    else if(pow > power[2]) {
      power[2] = pow;
      sxmax[2] = sx;
      symax[2] = sy;
    }
  }
  public double getPowerRatioLimit() {return powerRatioLimit;}
  public void setPowerRatioLimit(double limit) {powerRatioLimit=limit;}
  public int getMaxAziDiff() {return maxAziDiff;}
  public void setPowerNorm(double norm) {powerNorm=norm;}
  public double getPowerNorm() {return powerNorm;}
  public double getPowerNormAvg() {return powerNormAvg;}
  public double getPowerRatio() {return powerRatio;}
  public void setAziApplied(double a) {aziApplied=a;}
  public double getAziApplied() {return aziApplied;}
  public double getMeanAvg() {return meanavg;}  
  public double getStdAvg() {return stdavg;}
  public int getNAvg(){return navg;}
  /**
   * @return the total power in all of the cells
   */
  public double getTotalPower() {return totalPower;}
  /**
   * Length in time of the time series used to compute the FK grid
   * @return tlength
   */
  public double getTlength() {return tlength;}
  /**
   * Maximum slowness in the east-west direction
   * @return sxmax
   */
  public double [] getSXmax() {return sxmax;}
  /**
   * Maximum slowness in the north-south component
   * @return symax
   */
  public double [] getSYmax() {return symax;}
  /**
   * Slowness computed at the grid point maximum
   * @return maxslw
   */
  public double getMaxSlw() {return maxslw;}
  /**
   * Azimuth to the maximum
   * @return azimuth
   */
  public double getAzi() {return azi;}
  /**
   * Peak power
   * @return power
   */
  public double [] getPower() {return power;}
  /**
   * low pass corner frequency
   * @return lpcorner
   */
  public double getLpcorner() {return lpcorner;}
  /**
   * high pass corner frequency
   * @return hpcorner
   */
  public double getHpcorner() {return hpcorner;}
  /**
   * FK grid
   * @return kxy
   */
  public double [] getKxy() {return kxy;}
  /**
   * Start time of time series used in FK analysis
   * @return begtime
   */
  public long getBegTime() {return begtime;}
  /**
   * Start time of time series used in the FK analysis
   * @param t start time of time series
   */
  public void setBegTime(GregorianCalendar t) { begtime = t.getTimeInMillis();}
  public void setBegTime(long t) { begtime = t;}
  /**
   * Length of time series
   * @param t length of time series
   */
  public void setTlength(double t) {tlength = t;}
  /**
   * Maximum slowness in the east-west direction
   * @param d maximum slowness (east-west)
   */
  public void setSXmax(double d) {sxmax[0] = d;}
  /**
   * Maximum slowness in the east-west direction
   * @param d maximum slowness (north-south)
   */
  public void setSYmax(double d) {symax[0] = d;}
  /**
   * Optimal slowness
   * @param d optimal slowness
   */
  public void setMaxSlw(double d) {maxslw = d;}
  /**
   * Optimal azimuth
   * @param d optimal azimuth
   */
  public void setAzi(double d) {azi = d;}
  /**
   * Maximum power
   * @param d maximum power
   */
  public void setPower(double d) {power[0] = d;}
  /**
   * low pass corner frequency
   * @param d low pass corner frequency
   */
  public void setLpcorner(double d) {lpcorner = d;}
  /**
   * high pass corner frequency
   * @param d high pass corner frequency
   */
  public void setHpcorner(double d) {hpcorner = d;}
  /**
   * FK analysis grid
   * @param d FK grid
   */
  public void setKxy(double [] d) {
    kxy = d;
  }
  /** Creates a new instance of FKOutput
   * @param nk The number of columns and rows in the FK space
   */
  public FKResult(int nk) {
    kxy = new double[nk*nk];
/*    synchronized(mutex) {
      if(DBConnectionThread.getThread("fkresult") == null) {
        if(dbconn == null) {
          try {
            Util.prt("Opening fkresult connection to "+Util.getProperty("DBServer"));
            dbconn = new DBConnectionThread(Util.getProperty("DBServer"), "update", "edge", true, false,"fkresult", null);
            if(!dbconn.waitForConnection()) 
              if(!dbconn.waitForConnection()) 
                if(!dbconn.waitForConnection()) 
                  if(!dbconn.waitForConnection()) 
                    if(!dbconn.waitForConnection()) 
                      if(!dbconn.waitForConnection()) 
                        Util.prta( "Could not open database "+Util.getProperty("DBServer"));
          }
          catch(InstantiationException e) {
            Util.prt(" **** Impossible Instantiation exception e="+e);
          }  
        }
      }
    }*/
  }
  public static void main(String [] args) {
    Util.init("edge.prop");
    Util.setModeGMT();
    FKResult fkresult = new FKResult(101);
    fkresult.setBegTime(System.currentTimeMillis());
    fkresult.writeStatistics(null, "IMILAR SHZF0");
  }
  
}
