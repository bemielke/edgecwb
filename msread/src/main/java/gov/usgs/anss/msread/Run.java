/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */

package gov.usgs.anss.msread;
/*
 * Run.java
 *
 * Created on January 26, 2008, 2:33 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

/*
 * Run.java
 *
 * Created on April 6, 2007, 12:55 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

import java.util.GregorianCalendar;
import gov.usgs.anss.seed.*;
import gov.usgs.anss.util.*;
/**
 *
 * @author davidketchum
 */
/** This class creates a list of contiguous blocks.  A block can be added to it
 *and will be rejected if it is not contiguous at the end.  The user just attempts
 *to add the next data block in time to each of the known runs, and creates a new run
 *with the block when none of the existing ones accepts it.
 */
public class Run implements Comparable<Run> {
  GregorianCalendar start;      // start time of this run
  GregorianCalendar end;        // current ending time of this run (expected time of next block)
  String seedname;
  double rate;
  int nwierd;
  /** return the seedname for the run
   *@return The seedname*/
  public String getSeedname() {return seedname;}
  /** return the data rate for this run
   *@return the rate in hz
   */
  public double getRate() {return rate;}
  /** return the start time of the run
   *@return the start time as GregorianCalendar*/
  public GregorianCalendar getStart() {return start;}
  /** return the end time of the run (Actually the time of the next expected sample)
   *@return the end time as GregorianCalendar*/
  public GregorianCalendar getEnd() {return end;}
  /** return duration of run in seconds
   *@return The duration of run in seconds*/
  public double getLength() { return (end.getTimeInMillis()-start.getTimeInMillis())/1000.;}
  /** string representation
   *@return a String representation of this run */
  @Override
  public String toString() {return 
      "Run from "+Util.toDOYString(start)+" to "+
      Util.toDOYString(end)+" "+getLength()+" nwierd="+nwierd;
  }
  public void clear() {}
  /** implement Comparable
   *@param r the Run to compare this to
   *@return -1 if <, 0 if =, 1 if >than */
  public int compareTo(Run r) {
    if(r == null) return -1;
    if(!seedname.equals(r.getSeedname())) return seedname.compareTo(r.getSeedname());
    long l = start.getTimeInMillis() - r.getStart().getTimeInMillis();
    if(l > 0 ) return 1;
    if(l == 0) return 0;
    return -1;
  }
  /** create a new run with the given miniseed as initial block
   *@param ms The miniseed block to first include */
  public Run(MiniSeed ms) {
    //if(ms.getSeedNameString().indexOf("IMMJA0 HHZ")>=0)
    //  Util.prt("ms="+ms);
    start = ms.getGregorianCalendarTruncated();
    end = ms.getGregorianCalendarTruncated();
    end.setTimeInMillis(end.getTimeInMillis()+((long) (ms.getNsamp()/ms.getRate()*1000+0.49)));
    seedname=ms.getSeedNameString();
    rate = ms.getRate();
  }
  /** see if this miniseed block will add contiguously to the end of this run
   *@param ms the miniseed block to consider for contiguousness, add it if is is
   *@return true, if block was contiguous and was added to this run, false otherwise*/
  public boolean add(MiniSeed ms) {
    if(!ms.getSeedNameString().equals(seedname)) return false;
    if(ms.getRate() == 0.) return true;// probably a trigger packet!
    //if(ms.getSeedNameString().indexOf("IMMJA0 HHZ") >=0 )
    //  Util.prt("ms2="+ms);
    if(rate > 0. && ms.getRate() > 0.)
      if(Math.abs(rate - ms.getRate())/rate > 0.01 && nwierd % 1000 == 1) 
        Util.prt("Run: Weird diff rate! "+seedname+" ms="+ms.getSeedNameString()+
        " exp="+rate+"!="+ms.getRate()+" nwierd="+(nwierd++));
    // Is the beginning of this one near the end of the last one!
    if( Math.abs(ms.getGregorianCalendarTruncated().getTimeInMillis() - end.getTimeInMillis()) <
        500./ms.getRate()) {
      // add this block to the list
      end = ms.getGregorianCalendarTruncated();
      end.setTimeInMillis(end.getTimeInMillis()+
          ((long) (ms.getNsamp()/rate*1000+0.49)));
      return true;
    }
    else if(Math.abs(ms.getNextExpectedTimeInMillis() - start.getTimeInMillis()) < 
        500./ms.getRate()) {
      start = ms.getGregorianCalendarTruncated();
      return true;
    }
    else return false;
  }

}    
  

