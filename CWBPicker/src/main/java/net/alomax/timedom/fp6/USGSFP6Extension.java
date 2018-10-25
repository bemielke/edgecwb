/*
 *  This software is in the public domain because it contains materials 
 *  that originally came from the United States Geological Survey, 
 *  an agency of the United States Department of Interior. For more 
 *  information, see the official USGS copyright policy at 
 *  http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package net.alomax.timedom.fp6;
import gov.usgs.anss.picker.CWBFilterPicker6;
import net.alomax.timedom.TimeDomainException;
import gov.usgs.anss.util.Util;

/** this is a subclass of FilterPicker6 that tracks some implementation details
 needed at the USGS
 *
 * @author U.S. Geological Survey  <ketchum at usgs.gov>
 */
public final class USGSFP6Extension extends FilterPicker6  {
  private String channel;
  //private EdgeThread par;
  private CWBFilterPicker6 par;
  private String tag;
  private String title;
  private long totsamp;
  public CWBFilterPicker6 getCWBFilterPicker6() {return par;}
  public void prt(String s){ if(par == null) Util.prt(s); else par.prt(s);}
  public void prta(String s){ if(par == null) Util.prta(s); else par.prta(s);}
  public void prt(StringBuilder s){ if(par == null) Util.prt(s); else par.prt(s);}
  public void prta(StringBuilder s){ if(par == null) Util.prta(s); else par.prta(s);}
  /**
  * Constructs a new FilterPicker6 object setting filter bands automatically based on filterWindow and sample interval of data
  *
  * @param localeText locale for information, warning and error messages
   * @param ch The NSCL channel name 
   * @param parent The parent EdgeThread to use for logging
   * @param title The title of the configuration template
  * @param bandParam An array of BandParameter to set the bands
  * @param longTermWindow long term window factor
  * @param threshold1 threshold to trigger a pick event
  * @param threshold2 threshold to declare a pick
  * @param tUpEvent maximum time window after trigger for declaring a pick
  * @param tUpEventMin minimum time window after trigger for declaring a pick
  * @param direction The direction per the BasicPicker
   * @throws net.alomax.timedom.TimeDomainException
  */
  public USGSFP6Extension(String localeText, String ch, CWBFilterPicker6 parent, String title, 
          FilterPicker6_BandParameters[] bandParam, double longTermWindow, 
          double threshold1, double threshold2,
            double tUpEvent, double tUpEventMin, int direction) throws TimeDomainException {
    super(localeText, bandParam, longTermWindow, threshold1, threshold2, tUpEvent, tUpEventMin);

    try {
      par = parent;
      tag = ch;
      this.title=title;
      if(direction != this.getDirection()) setDirection(direction);
      isTriggered = new boolean[bandParam.length];
    }
    catch(RuntimeException e) {
      par.prt(tag+" Runtime="+e+" "+toString());
      e.printStackTrace(par.getPrintStream());
      throw e;
    }    
  }
  /** Given a pick, return the array for which bands were triggered and optionally a StringBuilder with
   * detail
   * @param pickData The pick returned from the FilterPicker6
   * @param sb If null, nothing, if not null, this is cleared and a line per pick is added.
   * @return Array of booleans, one for each frequency band, set true if the DF was big enough.
   */
  public boolean [] isTriggered(FilterPicker6PickData pickData, StringBuilder sb) {
    if(sb != null) Util.clear(sb).append("DEBUG: pick: P").append(triggerPickData.size() - 1).
            append(" nUp: ").append(pickData.nSamplesUpEventUsed).append(" (").
            append((float) (deltaTime * pickData.nSamplesUpEventUsed)).
            append("s) -------------------------------------------------------\n");
    for (int nband = 0; nband < numBands; nband++) {
      double cftrig = pickData.charFunctValueTrigger[nband] / threshold1;
      double cfmaxtrig = pickData.charFunctValueMaxInUpEventWindow[nband] / threshold1;
      isTriggered[nband] = cftrig >=1.;
      if(sb != null) {
        sb.append("DEBUG: ").append(nband).append("  T: ").append((float) pickData.bandParameters[nband].period).
                append("s/").append((float) (1.0 / pickData.bandParameters[nband].period)).append("Hz scale: ").
                append((float) (pickData.bandParameters[nband].thresholdScaleFactor)).
                append("  mean_sd: ").append((float) (fp6Memory.mean_stdDev_xRec[nband])).
                append("  CFmax: ").append((float) (pickData.charFunctValueMaxInUpEventWindow[nband])).
                append(" (").append((float) cfmaxtrig).append(")").append(cfmaxtrig >= 1.0 ? " +" : "  ").
                append("  CFtrig: ").append((float) pickData.charFunctValueTrigger[nband]).
                append(" (").append((float) cftrig).append(")").append(cftrig >= 1.0 ? " T" : "  ").
                append(nband == fp6Memory.bandTriggerPolarity ? " Pol" : " ").
                append(nband == fp6Memory.bandTriggerMax ? " MAX" : " ").append("\n");
      }
    }
    return isTriggered;
  }
   //  USGS code :
  @Override
  public String toString() {return tag+" #samp="+totsamp+" dt="+deltaTime+
          " fw="+filterWindow+" lt="+longTermWindowFactor+" nbands="+numBands+" dt="+deltaTime+
          " thr1="+threshold1+" thr2="+threshold2+" tUp="+tUpEvent+" tUpMin="+tUpEventMin+
          " fp6mem="+(fp6Memory == null?"null":fp6Memory.enableTriggering);}
  StringBuilder sb = new StringBuilder(1000);
  private int [] bandIndices = new int[2];
  public int [] getIndexBandTrigger(){return indexBandTrigger;}
  public double [] getCharFuncValueTrigger() {return charFunctValueTrigger;}
  public double [] getCharFuncValueMaxInUpEventWindow() {return charFunctValueMaxInUpEventWindow;}
  private boolean [] isTriggered;
  /** for a given band index, return the 1/2 power frequencies as an array of 2 with flow and fhigh in 0 and 1
   * 
   * @param nband The band index
   * @param band User array of 2 double to put answer in.
   * @return two element array with flow and fhigh at the 1/2 power points
   */
  public double [] getBandCorners(int nband, double [] band) {
    double freq = 1.0 / bandParameters[nband].period;
    double fmultiplier = Math.pow(bandWidthFactor, 1.25);    // gives best reconstruction of delta function
    band[1] = freq * fmultiplier;
    band[0] = freq / fmultiplier;
    return band;
  }
  /** given the list of triggered bands, compute the total widest bandwidth of the triggers
   * 
   * @param isTriggered An array of boolean with triggered bands, normally from pickData.isTriggered()
   * @return 
   */
  public int [] getBandWidthTriggered(boolean [] isTriggered) {
    //int ilow=0;
    int lowat=-1;
    int ncontig=0;
    //int max=-1;
    for(int i=0; i<numBands; i++) {
      if(isTriggered[i]) {
        int cnt=0;      // count consequtive bands triggered
        for(int j=i; j<numBands; j++) {
          if(isTriggered[j]) cnt++;
          else break;
        }
        if(cnt > ncontig) {
          ncontig=cnt;
          lowat=i;
        }
      }
    }

    // low at is the lowest band of contiguous trigger and ncontig is the number of bands, compute low and high frequencies.
    if(lowat == -1) {
      bandIndices[0] = -1; 
      bandIndices[1]=  -1; }
    else {
      bandIndices[0]= lowat;
      bandIndices[1] = lowat+ncontig-1;
      if(bandIndices[0] >= numBands || bandIndices[1] >= numBands) 
        Util.prt("Something is wrong bandIndices="+bandIndices[0]+" "+bandIndices[1]+" num="+numBands);
    }
    return bandIndices;
  }
  public StringBuilder getConfig() { return sb;}
  public StringBuilder setConfig() {
    Util.clear(sb);
    totsamp=0;
    sb.append(channel).append("numBands=").append(numBands).append(" dt=").append(deltaTime).
            append(" fw=").append(filterWindow).append(" lt=").append(longTermWindowFactor).
            append("thrs1=").append(threshold1).append(" thrs2=").append(threshold2).
            append(" tUp=").append(tUpEvent).append(" tUpMin=").append(tUpEventMin).append("\n");
    for(int i=0; i<numBands; i++) {
      sb.append(i).append(" per=").append(bandParameters[i].period).
              append(" factor=").append(bandParameters[i].thresholdScaleFactor).append("\n");
    }       
    return sb;
  }  
}
