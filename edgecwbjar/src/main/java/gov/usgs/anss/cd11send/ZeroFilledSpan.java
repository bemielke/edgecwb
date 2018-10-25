/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */


/*
 * ZeroFilledSpan.java
 *
 * Created on January 30, 2006, 3:32 PM
 *
 * To change this template, choose Tools | Options and locate the template under
 * the Source Creation and Management node. Right-click the template and choose
 * Open. You can then make changes to the template in the Source Editor.
 */

package gov.usgs.anss.cd11send;
//import com.sun.tools.javac.util.List;
import gov.usgs.anss.seed.MiniSeed;
import gov.usgs.anss.seed.Steim1;
import gov.usgs.anss.seed.Steim2;
import gov.usgs.anss.seed.SteimException;
import gov.usgs.anss.util.*;
//import java.util.ArrayList;
import java.util.GregorianCalendar;


/** This class represent a time series chunk which is zero filled if data is
 * missing.  The idea is to allow creation, population by a series of blocks
 * which may not be contiguous or even in order.  Constructors needs to deal
 * with different data types and construction methods.
 *
 *Initially, this class assumed it would be created from a list of mini-seed
 * blocks.  However, it is quite likely it will need to be extended to allow
 * a pre-allocation followed by many calls adding data to the timeseries.
 *
 * @author davidketchum
 */ 
public class ZeroFilledSpan {
  int nsamp;            // number of samples from beginning to end
  int fillValue;
  GregorianCalendar start;
  long [] times = new long[1000];
  //ArrayList<GregorianCalendar> times = new ArrayList<>(100);
  int [] data;                // The data array of nsamp samples
  byte [] frames = new byte[512];
  double rate=0.;
  boolean dbg=false;
  String missingSummary;
  String seedname;
  /** return the data rate for this channel 
   * @return the sample rate
   */
  public double getRate() {return rate;}
  /** return seedname for this channel 
   * @return the channel name in NNSSSSSCCCLL format
   */
  public String getSeedname() {return seedname;}
  /** string represting this time series
   *@return a String with nsamp, rate and start date/time*/
  @Override
  public String toString() {
    getNMissingData();
    return "Span: "+seedname+" ns="+nsamp+" rt="+rate+" "+
        Util.ascdatetime(start)+" to "+timeAt(nsamp)+" d.l="+data.length;
  }
  /** compute the time at the ith sample
   * 
   * @param i The index in the array of the sample 
   * @return The time of that sample extrapolated from the beginning time
   */
  public StringBuilder timeAt(int i) {
    GregorianCalendar e = new GregorianCalendar();
    e.setTimeInMillis( start.getTimeInMillis()+ ((long) (i/rate*1000.+0.5)));
    return Util.asctime2(e);
  }
  /** add the given time to the list of buffer times
   * 
   * @param g The time to add
   */
  private void addTime(GregorianCalendar g) {
    addTime(g.getTimeInMillis());
  }
  private void addTime(long millis) {
    for (int i=0; i<times.length; i++) {
      if (times[i] < 10000) {
        times[i]=millis;
        return;
      }
    }
    // Need to make times bigger
    long [] tmp = new long[times.length*2];
    System.arraycopy(times, 0, tmp, 0, times.length);
    times=tmp;
    Util.prt("Expanding times to "+times.length*2+" for "+seedname+" "+Util.ascdatetime2(start));
    /*GregorianCalendar gw = new GregorianCalendar();
    gw.setTimeInMillis(g.getTimeInMillis());
    times.add(gw);*/
  }
  /** purge any time we have that are before the given time
   * 
   * @param g The earliest time to remain on the list
   */
  private void purgeTimes(GregorianCalendar g) {
    long t = g.getTimeInMillis();
    for(int i=0; i<times.length; i++) {
      if(times[i] < t) {
        times[i] = 1000;
      }
    }
    /*for (GregorianCalendar time : times) {
      if (time.getTimeInMillis() < t) {
        time.setTimeInMillis(1000);
      }
    }*/
  }
  
  /** return the data starting at the given time
   * 
   * @param starting The starting time of the data returned
   * @param nsamp The number of samples to return
   * @param d The data array into which to put the data
   * @param outTime This is the exact time of the first sample in the buffer
   * @return The number of samples actually returned, may be less if the request is for more data than is in the buffer
   */
  public int  getData(GregorianCalendar starting, int nsamp, int [] d, GregorianCalendar outTime) {
    long msoff = starting.getTimeInMillis() - start.getTimeInMillis();
    int offset = (int) ((msoff + 1./rate*1000/2.-1.)/1000.*rate);
    if(offset < 0 ) return -1;
    outTime.setTimeInMillis(start.getTimeInMillis()+(long) (offset/rate*1000.));
    if(dbg) 
      Util.prt("getData starting ="+Util.asctime2(starting)+" buf start="+Util.asctime2(start)+" offset="+offset);
    if(nsamp+offset > data.length) nsamp = data.length - offset;
    System.arraycopy(data, offset, d, 0, nsamp);
    return nsamp;
    
  }
  /** get the time series as an arry of ints
   *@return The timeseries as an array of ints*/
  public int [] getData(){return data;}
  /** get the ith time series value
   *@param i THe index (starting with zero) of the data point to return.
   *@return The ith timeseries value*/
  public int getData(int i){return data[i];}
  /** get a chunk of the data into an array, 
   *@param d The array to put the data in
   *@param off The offset in the internal data buffer to start
   *@param len The maximum length of data to return (d must be dimensioned >len)
   *@return The number of samples actually returned <=len
   */
  public int getData(int [] d, int off, int len) {
    int n = len;
    if( (nsamp - off) < len) n = nsamp - off;
    System.arraycopy(data, off, d, 0, n);
    return n;
  }
  /** get number of data samples in timeseries (many might be zeros)
   *@return Number of samples in series */
  public int getNsamp(){ return nsamp;}
  /** return start time as a GregorianCalendar
   *@return The start time*/
  public GregorianCalendar getStart() {return start;}
  /** return the max value of the time serieis
   *@return Max value of the timeseries*/
  public int getMin() {
    int min = 2147000000;
    for(int i=0; i<nsamp; i++) if(data[i] < min) min=data[i];
    return min;
  }
  /** return the max value of the time serieis
   *@return Max value of the timeseries*/
  public int getMax() {
    int max = -2147000000;
    for(int i=0; i<nsamp; i++) if(data[i] > max) max=data[i];
    return max;
  }
  /** return true if any portion of the allocated space has a "no data" or fill value
   * @return true if there is at least on missing data value*/
  public boolean hasGapsBeforeEnd() {
    int ns = nsamp;
    if(data[0] == fillValue) return true;     // opening with fill
    int i;
    for(i=nsamp-1; i>=0; i--) if(data[i] != fillValue) {ns = i+1; break;}
    if(i <= 0 && ns == nsamp) return false;      // no fill Values found looking for last one!
    for(i=0; i<ns; i++) if(data[i] == fillValue) return true; 
    nsamp=ns;
    return false;
  }
  /** clear this span, set the time as empty and nsamp as zero, fill with value
   * 
   */
  public void clear() {
    nsamp=0;
    for(int i=0; i<data.length; i++) data[i] = fillValue;
    
  }
  /** check to see if the amount of time given has any filled data
   * @param g The time in the buffer to check
   * @param dur the amount of time in the buffer to check
   * @return true if some fill is found in the interval
   */
  public boolean hasFill(GregorianCalendar g, double dur) {
    long msoff = g.getTimeInMillis() - start.getTimeInMillis();
    int ns = (int)  (dur*rate);
    int offset = (int) ((msoff + 1./rate*1000/2.-1.)/1000.*rate);
    if(offset < 0 ) throw new IndexOutOfBoundsException(seedname+" Start is before first sample "+
            Util.ascdate(g)+" "+Util.asctime2(g)+" start="+Util.ascdate(start)+" "+Util.asctime2(start));
    if(dbg) Util.prt("getData at="+Util.asctime2(g)+" buf start="+Util.asctime2(start)+" offset="+offset);
    if(nsamp+offset > data.length) throw new IndexOutOfBoundsException(seedname+" Start + duration is out of buffer "+
            Util.ascdate(g)+" "+Util.asctime2(g)+" start="+Util.ascdate(start)+" "+Util.asctime2(start)+" d="+dur);
    for(int i=offset; i<offset+ns; i++) if(data[i] == fillValue) return true;
    return false;
  }

  /** return number of missing data points *
   * @return Number of missing data points
   */
  public int getNMissingData() {
    int noval=0;
    int first=-1;
    int last=nsamp+1;
    for(int i=0; i<nsamp; i++) 
      if(data[i] == fillValue) {noval++; last=i; if(noval == 1) first=i;}
    if(first >= 0) missingSummary = "First at "+first+" last at "+last+" # missing="+noval;
    return noval;
  }
  /** change the buffer by removing the first samples from it
   * @param dur the amount of time to remove at the beginning of the buffer
   * 
   */
  public void removeFirst(double dur) {
    if(dur < 0) return;
    int ns = (int) (dur*rate);
    start.setTimeInMillis( start.getTimeInMillis()+ ((long) (ns/rate*1000.+0.5)));
    int ind=-1;
    long min = 1000000000;
    long ms = start.getTimeInMillis();
    // find the time code just before this time
    for(int i=0; i<times.length; i++) {
      long diff = ms - times[i];
      if( diff > -10 && diff < min) {
        ind = i;
        min = diff;
      }
    }
    if(ind != -1) {
      int ns2  = (int) ((ms - times[ind])/1000.*rate+0.01);  // this should be a near integer
      start.setTimeInMillis(times[ind]+(long)(ns2/rate*1000.+0.5));
    }
    if(nsamp > ns) {
      System.arraycopy(data, ns, data, 0, nsamp - ns);      // copy down to remove ns samples
      for(int i=nsamp - ns; i<nsamp; i++) data[i] = fillValue;// the last ns samples must now be filled
      nsamp -=ns;  // set new number of valid samples.
    }
    else {
      for(int i=0; i<nsamp; i++) data[i]=fillValue;
      nsamp=0;
    }
    purgeTimes(start);          // throw out any older ones.
  }
  /**
   * compare to ZeroFilledSpans for "equivalence"
   * @return True if equivaleng
   * @param z Another ZeroFilledSpan to compare against.
   */
  public String differences(ZeroFilledSpan z) {
    StringBuilder sb = new StringBuilder(1000);
    StringBuilder details = new StringBuilder(1000);
    sb.append("Summary ").append(toString()).append("\n");
    sb.append("Summary ").append(z.toString()).append("\n");
    
    if(getNMissingData() != z.getNMissingData() ) 
      sb.append("*** # missing different ").append(getNMissingData()).append("!=").append(z.getNMissingData()).append("\n");
    if(getNsamp() != z.getNsamp()) 
      sb.append("*** Nsamp different ").append(nsamp).append(" != ").append(z.getNsamp()).
              append(" diff = ").append(nsamp - z.getNsamp()).append("\n");
    int gapStart=-1;
    int gapSize=0;
    for(int i=0; i<Math.min(nsamp,z.getNsamp()); i++) {
      if(data[i] != z.getData(i)) {
        if(gapStart == -1) {
          sb.append(" difference start at ").append(i).append(" ").append(timeAt(i));
          gapStart=i;
          gapSize++;
        }
        else {
          gapSize++;
        }
        details.append("*** ").append((i+"        ").substring(0,8)).append(Util.leftPad( (data[i] == fillValue ? "  nodata  ": ""+data[i]),8)).
                append(Util.leftPad(  (z.getData(i) == fillValue ? "  nodata  ":""+z.getData(i)) ,8));
        if( data[i] == fillValue || z.getData(i) == fillValue) details.append("\n");
        else details.append(Util.leftPad("df="+(data[i]-z.getData(i)),14)).append("\n");
      }
      else {
        if(gapStart != -1) {
          sb.append(" ends at ").append(i).append(" ").append(timeAt(i)).append(" # diff=").append(gapSize).append("\n");
          gapStart=-1;
          gapSize=0;
        }
      }
    }
    if(gapStart != -1) sb.append(" ends at ").append(nsamp).append(" ").append(timeAt(nsamp)).append(" # diff=").append(gapSize).append("\n");
    return sb.toString()+"\nDetails:\n"+details.toString();
  }
  
  /** Creates a new instance of ZeroFilledSpan - this represents zero filled
   * time series record
   *@param ms  A Mini-seed object to put in this series
   *@param trim  The start time - data before this time are discarded
   *@param duration Time in seconds that this series is to represent
   *@param fill a integer to use to pre-fill the array, (the not a data value)
   */
  public ZeroFilledSpan(MiniSeed ms, GregorianCalendar trim, double duration, int fill) {
    int j=0;
    if(ms == null) {
      return;
    }
    if(ms.getRate() <= 0.) throw new RuntimeException("The Rate is not positive ms="+ms);

    seedname = ms.getSeedNameString();
    rate = ms.getRate();
    data = new int[(int) (duration*ms.getRate()+0.01)];
    fillValue=fill;
    start=new GregorianCalendar();
    start.setTimeInMillis(trim.getTimeInMillis());
    nsamp=0;
    for(int i=0; i<data.length; i++) data[i] = fillValue;
    addMiniSeed(ms);
  }
 
  /** populate a zero filled span.  Called by the constructors
   * 
   * @param ms A mini-seed packet to add
   */
  public final void addMiniSeed(MiniSeed ms ) {
    if(nsamp == 0) start.setTimeInMillis(ms.getTimeInMillis()); // Buffer is empty, this must be the beginning

    //int begoffset = (int) ((start.getTimeInMillis() - ms.getGregorianCalendar().getTimeInMillis())*
    //    rate/1000.+0.01);
    if(dbg) Util.prt(Util.ascdatetime2(start).toString()+
        Util.ascdatetime2(ms.getTimeInMillis())+" ns="+ms.getNsamp());
    if(ms.getNsamp() <= 0) return;

    int msover2 = (int) (1./rate*1000./2.);         // 1/2 of a bin width in  millis
    addTime(ms.getTimeInMillis());             // put this on the list of times
    int offset = (int) ((ms.getTimeInMillis()-
        start.getTimeInMillis()+msover2)*rate/1000.);
    long mod = (long)((ms.getTimeInMillis()-
        start.getTimeInMillis()+msover2)*rate) % 1000L;
    if(dbg) Util.prt(Util.ascdate(start)+" "+Util.asctime(start)+" ms[0]="+
      Util.ascdatetime(ms.getTimeInMillis())
      +" offset="+offset+" ns="+ms.getNsamp());

    // get the compression frames
    if(frames.length < ms.getBlockSize()-ms.getDataOffset()) frames = new byte[ms.getBlockSize()-ms.getDataOffset()];  
    System.arraycopy(ms.getBuf(), ms.getDataOffset(), frames, 0, ms.getBlockSize()-ms.getDataOffset());
    if(ms.getEncoding() != 11 && ms.getEncoding() != 10) {
      boolean skip=false;
      for(int ith=0; ith<ms.getNBlockettes(); ith++) 
        if(ms.getBlocketteType(ith) == 201) skip=true;     // its a Murdock Hutt, skip it

      if(!skip) {
        Util.prt("ZeroFilledSpan: Cannot decode - not Steim I or II type="+ms.getEncoding());
        Util.prt(ms.toString());
      }
      return;
    }
    try {
      int reverse=0;
      int [] samples = null;
      if(ms.getEncoding() == 10) samples = Steim1.decode(frames, ms.getNsamp(), ms.isSwapBytes(), reverse);
      if(ms.getEncoding() == 11) samples = Steim2.decode(frames, ms.getNsamp(), ms.isSwapBytes(), reverse);
      // if the offset calculated is negative, shorten the transfer to beginning
      //Util.prt("offset="+offset+" ms.nsamp="+ms.getNsamp()+" bufsiz="+nsamp);
      if(offset < 0) {
        if(ms.getNsamp()+offset-1 > 0) {
          System.arraycopy(samples, -offset+1, data, 0, ms.getNsamp()+offset-1);
          if(ms.getNsamp()+offset-1 > nsamp) nsamp = ms.getNsamp()+offset -1;
        }
      }
      else  { // This data is above beginning
        if(offset+ms.getNsamp() < data.length) {  // will it fit with no losses off end
          System.arraycopy(samples, 0, data, offset, ms.getNsamp());
          nsamp = offset+ms.getNsamp();
        }
        else {    // Data is going to be lost off the end, do the best we can
          if(offset < data.length) {    // data laps over the end, keep what we can
            System.arraycopy(samples, 0, data, offset, data.length - offset);
            nsamp = data.length;
          }
          throw new ArrayIndexOutOfBoundsException(
                  "ZeroFilledSpan: Attempt to put data off end offset="+offset+" nsamp="+ms.getNsamp()+" max="+data.length);
        }
      }
    }
    catch (SteimException e) {
      Util.prt(" gave steim decode error. "+e.getMessage());
    }
  }

}
