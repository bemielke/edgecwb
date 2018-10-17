/*
 *  This software is in the public domain because it contains materials 
 *  that originally came from the United States Geological Survey, 
 *  an agency of the United States Department of Interior. For more 
 *  information, see the official USGS copyright policy at 
 *  http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.cwbquery;
import gov.usgs.anss.seed.MiniSeed;
import gov.usgs.anss.edge.TimeSeriesBlock; 
import gov.usgs.anss.util.Util;
import gov.usgs.anss.edge.RawDisk;
import gov.usgs.anss.edgethread.EdgeThread;
import gov.usgs.anss.edge.IllegalSeednameException;
import java.io.IOException;

/** Load miniseed blocks this check for continuity and returns a calculated "best" rate
 * for the channel.  Used by QuerySpan for channels whose rates might not be that accurate
 *
 * @author U.S. Geological Survey <ketchum at usgs.gov>
 */
public final class RateSpanCalculator {
  private long start; // in 100s usec since epoch
  private int nsamp;
  private long end;         // in 100s usec since epoch
  private int durNsamp;
  private double duration;
  private long lastUpdate;
  private double bestRate;
  private double nominalRate;
  private long minTime;
  private long bestTime;
  private int bestNsamp;
  private int msover2;
  private String seedname;
  private final EdgeThread par;
  public double getNominalRate() { return nominalRate;}
  public double getBestRate() {
    if(Math.abs(bestRate/nominalRate -1.) < 0.00000001) return nominalRate;
    return bestRate;}
  public int getBestNsamp() {return bestNsamp;}
  public long getBestTime() {return bestTime;}
  public long getStart() {return start;}
  public long getEnd() {return end;}
  public int getNsamp() {return nsamp;}
  private void prt(String s){ if(par == null) Util.prt(s); else par.prt(s);}
  private void prta(String s){ if(par == null) Util.prta(s); else par.prta(s);}
  public final void clear(String seedname, double rate, double duration) {
    durNsamp = (int) (duration*rate);
    if(durNsamp < 1000) durNsamp=1000;
    bestRate = rate;
    nominalRate=rate;
    msover2 = (int) (1000./rate/2.+0.5);
    minTime = (long) (duration/4.*1000.);
    this.duration = duration;
    this.seedname = seedname;    
    start=0;
    end=0;
    nsamp=0;
  }
  public RateSpanCalculator(String seedname, double rate, double duration, EdgeThread parent) {
    par = parent;
    clear(seedname,rate,duration);
  }
  public boolean addMS(MiniSeed ms) {
    if(ms.getNsamp() <=0 || ms.getRate() <= 0.00001) return false;
    if(start == 0) {
      setStart(ms);
      setEnd(ms);
      nsamp=ms.getNsamp();
      return false;
    }
    if(Math.abs(ms.getTimeInMillis() - end/10) < msover2) {      // is it a add to the end
      setEnd(ms);
      nsamp += ms.getNsamp();
    }
    else if(Math.abs(ms.getNextExpectedTimeInMillis() - start/10) <= msover2) { // is it an add at the beginning?
      setStart(ms);
      nsamp += ms.getNsamp();
    }
    // is it entire in this span?
    else if(ms.getTimeInMillis() >= start/10-msover2 && ms.getNextExpectedTimeInMillis() < end/10 + msover2) {
      return false;
    }
    else {      // not continuous start a new one
      boolean ret=false;
      if(nsamp > 0) {
        //Util.prta("Discon nsamp="+nsamp+" "+toString());
        if(bestTime == 0 || nsamp > bestNsamp) {return doBestRate();}
      }
      nsamp = ms.getNsamp();
      setStart(ms);
      setEnd(ms);
      return ret;
    }
    if(nsamp > durNsamp*4) {      // Its very continuous, need to cut it off
      boolean ok = doBestRate();
      nsamp = ms.getNsamp();
      setStart(ms);
      setEnd(ms);
      return ok;      
    }
    long timediff = System.currentTimeMillis() - bestTime;
    if(nsamp < bestNsamp*1.5 && timediff < minTime) return false;
    if(nsamp < durNsamp/4) return false;
    return doBestRate();
  }
    public boolean addTSB(TimeSeriesBlock ms) {
    if(ms.getNsamp() <=0 || ms.getRate() <= 0.00001) return false;
    if(start == 0) {
      setStart(ms);
      setEnd(ms);
      nsamp=ms.getNsamp();
      return false;
    }
    if(Math.abs(ms.getTimeInMillis() - end/10) < msover2) {      // is it a add to the end
      setEnd(ms);
      nsamp += ms.getNsamp();
    }
    else if(Math.abs(ms.getNextExpectedTimeInMillis() - start/10) <= msover2) { // is it an add at the beginning?
      setStart(ms);
      nsamp += ms.getNsamp();
    }
    // is it entire in this span?
    else if(ms.getTimeInMillis() >= start/10-msover2 && ms.getNextExpectedTimeInMillis() < end/10 + msover2) {
      return false;
    }
    else {      // not continuous start a new one
      boolean ret=false;
      if(nsamp > 0) {
        //Util.prta("Discon nsamp="+nsamp+" "+toString());
        if(bestTime == 0 || nsamp > bestNsamp) {return doBestRate();}
      }
      nsamp = ms.getNsamp();
      setStart(ms);
      setEnd(ms);
      return ret;
    }
    if(nsamp > durNsamp*4) {      // Its very continuous, need to cut it off
      boolean ok = doBestRate();
      nsamp = ms.getNsamp();
      setStart(ms);
      setEnd(ms);
      return ok;      
    }
    long timediff = System.currentTimeMillis() - bestTime;
    if(nsamp < bestNsamp*1.5 && timediff < minTime) return false;
    if(nsamp < durNsamp/4) return false;
    return doBestRate();
  }
  @Override
  public String toString() { return seedname+" rt="+bestRate+" 1 per "+
          Util.roundToSig(nominalRate/(Math.max(Math.abs(bestRate-nominalRate),0.0000000001*nominalRate)),2)+
          " ns="+bestNsamp+" age="+(System.currentTimeMillis()-bestTime)/1000.+"s";}
  private final StringBuilder tmpsb = new StringBuilder(100);
  public StringBuilder toStringBuilder(StringBuilder sb) {
    StringBuilder tmp = sb;
    if(tmp == null) {
      tmp = Util.clear(tmpsb);
    }
    tmp.append(seedname).append(" rt=").append(bestRate).append(" 1 per ").
            append(Util.roundToSig(nominalRate/(Math.max(Math.abs(bestRate-nominalRate),0.0000000001*nominalRate)),2)).
            append(" ns=").append(bestNsamp).append(" age=").append((System.currentTimeMillis()-bestTime)/1000.).append("s");
    return tmp;
  }
  public boolean doBestRate() {
    if(end - start < 60000) return false;
    bestRate = nsamp / ((end - start)/10000.);
    bestRate = Util.roundToSig(bestRate, 10);
    bestTime = System.currentTimeMillis();
    bestNsamp = nsamp;
    //prta(seedname+" do Best rate ns="+nsamp+" "+Util.ascdatetime2(start/10)+(start%10)+"-"+Util.ascdatetime2(end/10)+(end%10)+" "+toString());
    return true;
  }
  private void setStart(MiniSeed ms) {
    start = ms.getTimeInMillisTruncated()/1000L*10000 + ms.getUseconds() / 100;
  }
  private void setEnd(MiniSeed ms) {
    end = ms.getTimeInMillisTruncated()/1000L*10000 + ms.getUseconds() / 100 + (long) (ms.getNsamp()/ms.getRate()*10000.+0.1);
    
  }
  private void setStart(TimeSeriesBlock ms) {
    start = ms.getTimeInMillis()*10;
  }
  private void setEnd(TimeSeriesBlock ms) {
    end = ms.getTimeInMillis()*10 + (long) (ms.getNsamp()/ms.getRate()*10000.+0.1);
    
  }
  public static void main(String [] args) {
    byte [] buf = new byte[512];
    MiniSeed ms = null;
    RateSpanCalculator rsc = null;
    for(int i=1; i<args.length;i++) {
      try {
        try (RawDisk rw = new RawDisk(args[i],"rw")) {
          int nblks = (int) (rw.length()/512L);
          for(int iblk=0; iblk<nblks; iblk++) {
            //for(int iblk=nblks-1; iblk>=0; iblk--) {
            rw.readBlock(buf, iblk, 512);
            if(ms == null) ms = new MiniSeed(buf);
            else ms.load(buf);
            if(rsc == null) rsc = new RateSpanCalculator(ms.getSeedNameString(), ms.getRate(), 3600., null);
            rsc.addMS(ms);
          }
        }
        if(rsc != null) {
          if(rsc.getBestNsamp() <= 0) rsc.doBestRate();
          Util.prta(rsc.toString());
        }
        rsc=null;
      }
      catch(IOException | IllegalSeednameException e) {
        e.printStackTrace();
      }
    }
  }
}
