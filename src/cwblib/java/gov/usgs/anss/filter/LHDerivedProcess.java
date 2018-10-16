/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.filter;

import gov.usgs.anss.edge.TimeSeriesBlock;
import gov.usgs.anss.seed.*;
import gov.usgs.anss.edgeoutput.TraceBuf;
import gov.usgs.anss.util.Util;
import gov.usgs.anss.edgethread.EdgeThread;

/** This class computers a derived LH from some BH or HH channel.  Each instance is a single 
 * channel.  It attempts to phase sync the derived to the actual by adjusting its computation to
 * the set of samples which will produce a 1 sps sample as close to the one set via setPhase() from
 * the LH channel.  The data are forwarded when ever 10 or so of them have been computed.
 * <p>
 * There is a warmup phase that covers 2 times the expected delay and no data is generated during
 * the warmup.  If a phase shift in the LH to derived LH occurs, the warmup is restarted to allow
 * it to get back in phase.  So gaps in the derived LH are likely caused by this algorithm.
 * 
 * <p>
 * Only HydraOutputer and LHDerivedToQueryMom are known to use this class.  In the long run, the 
 * HydraOutputer should no longer be allowed to derived LH as this feature is not longer needed to put
 * such channels on the edge wire.  
 *
 * @author davidketchum
 */
public class LHDerivedProcess {

  private long expectedTime;  // Expected time of nex call to processLH
  private int buffer[];       // storage for left over data, and working buffer for filtering
  private long lhtime;        // this is the time of the first sample in output
  private int output[];       // Output data samples
  private int nout;           // Number of samples in output
  private int inbuf;          // number of samples in buffer
  private final int rate;           // decimation factor
  private final StringBuilder channel = new StringBuilder(12);     // expected channel
  private int warmup;         // if above zero, the number of samples remaining to be needed for warmup
  private final int warmupCount;    // time it takes in samples for this filter to be warmed up
  private final int delayMS;        // delay of fd as configured
  private final FilterDecimate fd;   // The FilerDecimates being used to filter down the data
  private LHOutputer lhoutputer;// The currently registered handler of data from this object
  private int phaseMS = -1;
  private boolean noOutput;
  private boolean dbg = false;
  private final EdgeThread par;
  /** Set the milliseconds of offset in the actual LH data so it can be used to sync up with the 
   * derived LH better
   * 
   * @param ms Milliseconds from a LH? record
   */
  public void setPhaseMS(int ms) {
    if(ms != phaseMS) {
      prta("LDH: Set phase "+channel+" to "+ms+ " was "+phaseMS);
      phaseMS = ms;
    }
  }
  
  public void setNoOutput(boolean t) {
    noOutput = t;
    prta("LDH: * "+channel+" is set noOutput="+t);
  }
  public void setDebug(boolean t) {
    dbg = t;
  }

  /** register an process which implements the LHOutputer interface which will decide what to do with
   * the output
   * 
   * @param obj THe object to handle output (normally HydraOutputer of LHDerivedToQueryMom
   */
  public void setOutputer(LHOutputer obj) {
    lhoutputer = obj;
  }

  @Override
  public String toString() {
    return channel + " rate=" + rate + " delay=" + delayMS;
  }

  public void prt(String s) {
    if (par == null) {
      Util.prt(s);
    } else {
      par.prt(s);
    }
  }

  public final void prta(String s) {
    if (par == null) {
      Util.prta(s);
    } else {
      par.prta(s);
    }
  }
  /**  Create an object to process data for a single channel of BH or HH to an LH channe
   * 
   * @param inputRate input rate of the data
   * @param chan NSCL 12 characters
   * @param parent Someplace to log output (Normally LHDerivedToQueryMom or Hydra)
   */
  public LHDerivedProcess(int inputRate, String chan, EdgeThread parent) {
    rate = inputRate;
    par = parent;
    Util.clear(channel).append(chan);
    buffer = new int[inputRate];
    output = new int[30];
    try {
      fd = new FilterDecimate(inputRate, 1, false);
    }
    catch(RuntimeException e) {
      prta("LDH: Runtime setting up filter inputrate="+inputRate+" c"+chan);
      throw e;
    }
    delayMS = fd.getDelayMS();
    warmupCount = delayMS * rate / 1000 * 2 + 1;  // Two warmump 
    warmup = warmupCount;
    if (dbg) {
      prta("LHD:" + channel + " new channel=" + channel + " in rate=" + rate 
              + " delayMS=" + delayMS + " warmup=" + warmupCount);
    }
  }

  /**  Process a TimeSeries block into 1sps data
   * 
   * @param tb A miniSeed or TraceBuf or othe TimeSeries block
   */
  public void processToLH(TimeSeriesBlock tb) {
    if (!Util.stringBuilderEqual(tb.getSeedNameSB(), channel) || noOutput) {
      return;
    }
    if (expectedTime == 0) {
      expectedTime = tb.getTimeInMillis();
    }
    if (tb.getTimeInMillis() < expectedTime - 500 / rate) {
      if (dbg) {
        //g2.setTimeInMillis(tb.getTimeInMillis());
        prt("LHD:" + channel + " *** reject early packet " + Util.ascdatetime2(tb.getTimeInMillis()) 
                + " " + (tb.getTimeInMillis() - expectedTime));
      }
      return;
    }  // this is prior data
    int nsamp;
    int[] data;
    // Is this data the next expected
    if (Math.abs(tb.getTimeInMillis() - expectedTime) > 500 / rate) {
      warmup = warmupCount;
      inbuf = 0;
      if (dbg) {
        prt("LHD:" + channel + " *** packet has gap of " + (tb.getTimeInMillis() - expectedTime)
                + " ms.  set expected to " + Util.ascdate(tb.getTimeInMillis()) 
                + " " + Util.asctime2(tb.getTimeInMillis()));
      }
      sendOutput();
      expectedTime = tb.getTimeInMillis();
    }

    // This case statement on Timeseries buffer type must put the data points in data and set nsamp
    if (tb.getClass().getName().contains("MiniSeed")) {
      try {
        MiniSeed ms = (MiniSeed) tb;
        data = ms.decomp();
        if (data != null) {
          nsamp = ms.getNsamp();
          if (nsamp != data.length) {
            prt("LHD:" + channel + " *** Decompression of Miniseed != nsamp");
            if (data.length < nsamp) {
              nsamp = data.length;
            }
          }

        } else {
          return;
        }
      } catch (SteimException e) {
        return;
      }
    } else if (tb.getClass().getName().contains("TraceBuf")) {    // It must be a tracebuf
      TraceBuf trace = (TraceBuf) tb;
      data = trace.getData();
      nsamp = tb.getNsamp();
    } else {
      prt("LHD:" + channel + " ***** Unknown type of Timeseries buf!=" + tb.getClass());
      return;
    }

    // check to see that our buffer is big enough, if not expand it
    if (inbuf + nsamp > buffer.length) {
      int[] tmp = new int[(inbuf + nsamp) * 3 / 2];
      if (inbuf > 0) {
        System.arraycopy(buffer, 0, tmp, 0, inbuf);
      }
      buffer = tmp;
    }
    // move the new data into the buffer at inbuf
    System.arraycopy(data, 0, buffer, inbuf, nsamp);
    int nold = inbuf;   // save number of old samples being held
    inbuf += nsamp;

    // When we get here buffer contains inbuf worth of data ready to go
    for (int i = 0; i < inbuf; i = i + rate) {
      if (i + rate >= inbuf) {   // no more samples, set inbuf and setup buffer
        if (dbg) {
          prt("LHD:" + channel + " save end of buffer inbuf=" + inbuf + " i=" + i 
                  + " remain=" + (inbuf - i));
        }
        inbuf = inbuf - i;
        if (inbuf > 0) {
          System.arraycopy(buffer, i, buffer, 0, inbuf);
        }
        continue;
      }
      if (nout == 0 && warmup <= 0) {

        lhtime = expectedTime + (i - nold + rate) * 1000 / rate - delayMS;
        if (dbg) {
          prt("LHD:" + channel + " 1st LP samp i=" + i + " nold=" + nold 
                    + " time=" + Util.ascdatetime2(lhtime) + " diff=" + (lhtime - expectedTime));
        }
        
        // Is the output data out of phase with the LH data, if so warmup again
        if(phaseMS != -1 && 
                !( Math.abs(lhtime % 1000 - phaseMS) <=  500/rate ||          // small diff
                Math.abs(Math.abs(lhtime % 1000 - phaseMS) - 1000) <= 500/rate) ) { // near 1 sec diff
          warmup = warmupCount;
          prta("LHD : **** "+channel+" out of phase ms="+phaseMS + " is "+(lhtime % 1000)
                  +"  Warmup again....");
        }
        /*else {    // DEBUG : show phase oi
          if(phaseMS != -1) {
            prta("LHD: "+channel+ " phase o.k. "+phaseMS+" "+ (lhtime % 1000)+" "+rate);
          }
        }*/
      }
      if (nout + 1 >= output.length) {
        if (dbg) {
          prt("LHD:" + channel + " increase output buffer size to " + (output.length * 2));
        }
        int[] tmp = new int[output.length * 2];
        System.arraycopy(output, 0, tmp, 0, nout);
        output = tmp;
      }
      // See if the phasing is off
      if(phaseMS != -1) {
        long phaseOff =  (long) ((expectedTime + (i - nold + rate) * 1000 / rate - delayMS)) % 1000;  
        if(phaseOff < 0) phaseOff += 1000;
        // The phase difference will either be near zero or near one second, insure in either case it is close
        if( phaseMS != -1 && 
            !( Math.abs(phaseOff - phaseMS) <= 500/rate ||                  // Small diff
            Math.abs(Math.abs(phaseOff - phaseMS) - 1000) <= 500/rate) ) {  // near 1 sec diff
          int adjust = (int) Math.round((phaseOff - phaseMS)/1000. * rate); // nsamps change
          if(i - adjust >= 0) {
            prta("LHD: **** warmup phase is off "+channel+" phaseMS="+phaseMS+ " is "
                  + phaseOff+ " adjust i="+i+" by "+adjust+ " samp "+rate);
          }
          i = Math.max(i - adjust, 0);
        }
      }
      output[nout++] = fd.decimate(buffer, i);
      if (warmup > 0) {    // if we are warming up, do not send any output
        if (dbg) {
          prt("LHD:" + channel + " warmup =" + warmup);
        }
        warmup -= rate;
        nout = 0;
      }
    }
    expectedTime = expectedTime + nsamp * 1000 / rate;
    if (nout >= 10) {
      sendOutput();
    }

  }

  public void forceout() {
    sendOutput();
  }    // force any leftover data out

  private void sendOutput() {
    if (lhoutputer != null) {
      if (dbg) {
        prt("LHD:" + channel + " sendoutput nsamp=" + nout + " " + Util.ascdatetime2(lhtime) 
                + " phasems="+phaseMS);
      }
      //for(int i=0; i<nout; i++) output[i] += 1000000; // DEBUG: make clear its this data
      lhoutputer.sendOutput(lhtime, channel, output, nout);

    } else {
      prt("LHD:" + channel + " Got sendOutput() with no outputer yet set!!!!!");
    }
    nout = 0;
  }
}
