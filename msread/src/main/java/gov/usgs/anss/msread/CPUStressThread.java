/*
 *  This software is in the public domain because it contains materials 
 *  that originally came from the United States Geological Survey, 
 *  an agency of the United States Department of Interior. For more 
 *  information, see the official USGS copyright policy at 
 *  http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */

package gov.usgs.anss.msread;
import gov.usgs.anss.util.Util;
/**
 *
 * @author U.S. Geological Survey <ketchum at usgs.gov>
 */
public class CPUStressThread extends Thread{
  boolean terminate;
  public void terminate() {terminate=true;}
  public CPUStressThread() {
    setDaemon(true);
    start();
  }
  @Override
  public void run() {
    double ans = 0.;
    double w = 0.;
    int loop=0;
    while(!terminate) {
      for(int l=0; l<10000; l++) {
        for(int i=0; i< 1000; i++) {
          ans += Math.sin(Math.PI/1000.*i);
        }
      }
      Util.prta(this.getName()+" l="+loop+" "+ans);
      ans=0;
      loop++;
    }
  }
}
