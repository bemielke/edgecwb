/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.tsplot;

import gov.usgs.anss.util.Util;
import java.util.GregorianCalendar;

/*
 * testMSGroup.java
 *
 * Created on July 17, 2006, 3:16 PM
 * By Jeremy Powell
 *
 */
public final class testMSGroup extends MSGroup {

  public testMSGroup() {

  }

  /**
   * Creates a new instance of testMSGroup
   *
   * @param startVal
   */
  public testMSGroup(int startVal) {
    // dont care about blk, make our own data
    data = new int[12000];
    for (int i = 0; i < 12000; ++i) {
      data[i] = (int) (20 * Math.sin(Math.toRadians(startVal + i)));
    }
    name = "test " + startVal;
    rate = 40;
    start = new GregorianCalendar();
    end = new GregorianCalendar();
    if (startVal == 0) {
      start.setTimeInMillis(Util.stringToDate2("2006,186-00:00").getTime());
      end.setTimeInMillis(Util.stringToDate2("2006,186-00:00").getTime() + 300000);
    } else {   // start second graph 1 minute later
      start.setTimeInMillis(Util.stringToDate2("2006,186-00:01").getTime());
      end.setTimeInMillis(Util.stringToDate2("2006,186-00:01").getTime() + 300000);
    }
    breakTimes = new long[0];
    breakValues = new int[0];
  }

}
