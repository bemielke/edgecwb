/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */

package gov.usgs.anss.fk.obsolete;

/** This is the main class for running a FK derived beam from preset parameters.
 * In an QueryMom environment this thread would be started from the FKManager.  For
 * debugging it can be started by hand using its main class to pass all of the many
 * parameters.  This class uses the QuerySpan class to access time series either from 
 * the memory cache, if run with a memory caching QueryMom, or from disk if not.
 * <p>
 * The parameters are documented here, but they are really parsed and managed in the 
 * FKParams class.
 * 
 * <PRE>
 * 
 *  Description of command line arguments and definition of variables.  Example usage line
 * 
 * fk -s stationfile -fl hpc/lpc/npoles/npasses/hpass/lpass -fk kmax/nk -bm hpcorner/lpcorner/beamlen/overlap [-cwb][-dbg][-t yr/mn/dy hr:min:sec] 
 * Switch   Description of parameters
 * -cwb                write output into the CWB
 * -dbg                write into log files
 * -s  stationfile     name of the stations file
 * -fl hpc/lpc/npoles/npasses/hpass/lpass
 *      hpc: high-pass corner used to filter the beam trace after stacking
 *      lpc: low-pass corner used to filter the beam trace after stacking
 *      npoles: number of poles in the filter
 *      npasses: number of passes in the filter
 *      hpass: t = true
 *      lpass: t = true
 *  -fk kmax/nk 
 *      kmax: maximum slowness in the beamforming
 *      nk: number of slowness in the x and y direction (make it an odd number)
 *  -d  dt/dtoriginal
 *      dt: sample interval requested
 *      dtoriginal: sample interval of the original data"+
 *  -bm hpcorner/lpcorner/beamlen/overlap
 *      hpcorner: high-pass corner in the frequency-domain stacking (beamforming)
 *      lpcorner: low-pass corner in the frequency-domain stacking (beamforming)
 *      beamlen: number of samples used in the beamforming (power of 2)
 *      overlap: nubmer of samples in the overlap of beams (power of 2 and smaller than beamlen)
 *              if beamlen=512, overlap=256 mean 50% overlap
 *      ftlen: window length (in power of 2) for averaging the ftest results (typical 8 or 16)
 * -l secs    Number of seconds to use as the normal latency of this array from real time - used to minimize bashing for data when it is not yet in.
 * -beammethod n  Use the n method for making the beam (def=0 which is normal time domain stacking)
 * 
 * </PRE>
 * @author David Ketchum
 */ 
import gov.usgs.anss.edge.RawInputClient;
import gov.usgs.anss.util.Util;
import gov.usgs.anss.edgethread.EdgeThread;
import gov.usgs.cwbquery.QueryRing;
import gov.usgs.anss.edgethread.ManagerInterface;
import gov.usgs.anss.util.AnssPorts;
import gov.usgs.anss.fk.*;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.GregorianCalendar;

/**
 * Computes the FK results within an EdgeThread.  All of the parameters passed to this routine are
 * passed to the FKArray object created to do the calculations.  The only parameters which only apply to
 * this thread are ones which redirect the output to a different edge and cwb - not normally needed.
 * 
 * <PRE>
 * switch   Args    Description 
 * -cwbip  ip.add   The IP address of the CWB which would get data if older than 10 days (see RawInputClient for details)
 * -cwbport nnnn    Port to send CWB data that is older than 10 days (def=0 discard the data)
 * -edgeip ip.adr   The IP address to send data within the 10 day window (def = localhost)(see RawInputClient for details)
 * -edgeport nnn    THe port to send the data within 10 days (def= 7972)
 * -l           char    The location code second letter to use for output.
 * </PRE>
 */
public class FK extends EdgeThread implements ManagerInterface {

  //FKParams fkparms;
  //FKBeam beam;
  FKArray array;
  FKParams fkparms;
  private boolean dbg;

  private final String orgargline;

  private boolean check;
  private RawInputClient rawout;
  private String edgeServer="localhost";
  private int edgePort = AnssPorts.EDGEMOM_RAWINPUT_IMS_SERVER;
  private String cwbServer; 
  private int cwbPort;
  /**
   * convert from degrees to radians
   */
  private final StringBuilder tmpsb = new StringBuilder(100);
  @Override
  public String getArgs() {return orgargline;}
  @Override
  public boolean getCheck() {return check;}
  @Override
  public void setCheck(boolean t) {check=t;}  /** terminate thread (causes an interrupt to be sure) you may not need the interrupt! */
  @Override
  public void terminate() {terminate = true; prta(tag+" FK: interrupted called!");interrupt();}
  @Override
  public String toString() { return toStringBuilder(null).toString();}
  public final StringBuilder toStringBuilder(StringBuilder tmp) {
    StringBuilder sb = tmp;
    if(sb == null) sb = Util.clear(tmpsb);
    sb.append(tag).append(" ");
    return sb;
  }

  /** return the status string for this thread 
   *@return A string representing status for this thread */
  @Override
  public StringBuilder getStatusString() {
    Util.clear(statussb);
    return statussb;
  }
  /** return the monitor string for Nagios
   *@return A String representing the monitor key value pairs for this EdgeThread */
  @Override
  public StringBuilder getMonitorString() {
    Util.clear(monitorsb);
    return monitorsb;
  }

  /** return console output - this is fully integrated so it never returns anything
   *@return "" since this cannot get output outside of the prt() system */
  @Override
  public StringBuilder getConsoleOutput() {return consolesb;}
 
  /**
   * Construct a FK object
   * @param argline
   * @param tag
   */

  public FK(String argline, String tag)  {
    super(argline,tag);
    orgargline = argline;
    array = new FKArray(argline, tag, (EdgeThread) this);
    fkparms = array.getFKParams();
    String [] args = argline.split("\\s");
    for(int i=0; i<args.length; i++) {
      if(args[i].equals("-cwbip")) {cwbServer=args[i+1];i++;}
      if(args[i].equals("-edgeip")) {edgeServer=args[i+1];i++;}
      if(args[i].equals("-cwbport")) {cwbPort=Integer.parseInt(args[i+1]); i++;}
      if(args[i].equals("-edgeport")) {edgePort=Integer.parseInt(args[i+1]); i++;}
    }
    prta(" new FK "+toStringBuilder(null)+" args="+argline+" parms="+fkparms.toStringBuilder(null));
    start();
  }
  @Override
  public void run() {
    running=true;
    RandomAccessFile rftime = null;   //file containing the time (in millis) of the last processed block of data
    byte [] timebuf = new byte[16];
    String [] chans = new String[10]; // This contains the channel name for each method

    int npts=fkparms.getBeamlen();         //number of points in the time series (powers of 2)
    //int overlap = fkparms.getOverlap();    //number of points in the overlap (powers of 2; eg npts=256, overlap=128:  50% overlap)
    //while(true) try{sleep(1000);} catch(InterruptedException e) {break;}  // DEBUG: do nothing yet

    int [] idata = new int[10];
    //logname is the name of the logfile on the CWB side
    String logname = array.getRef().getArrayname().trim()+"beam.log";
    int nseis=array.getNActive();
    StringBuilder runsb = new StringBuilder(100);

    long current = System.currentTimeMillis() - 500000;
    GregorianCalendar gc = new GregorianCalendar();
    if(fkparms.getStartTime() == null) {
      try {
        rftime = new RandomAccessFile(fkparms.getStartTimeFile(),"rw");
        try {
          String tt = rftime.readLine();
          if(tt == null) current = System.currentTimeMillis() - 500000;
          else if(tt.trim().equals("")) current = System.currentTimeMillis() - 500000; // If no time given, start at the present
          else current = Long.parseLong(tt);
        } catch(IOException e) {
          prta(Util.clear(runsb).append(tag).append("FK.Main(): Cannot find starttime file ").append(fkparms.getStartTimeFile()).append(" ").append(e));
        }
      } catch(FileNotFoundException e) {
        prta(Util.clear(runsb).append(tag).append("FK.Main(): Cannot find starttime file ").append(fkparms.getStartTimeFile()).append(" ").append(e));
      }
    } else {
      current = fkparms.getStartTimeInMillis(); // User supplied start time
    }
    long endAt = fkparms.getEndTimeInMillis();

    prta(Util.clear(runsb).append(tag).append(Util.ascdate()).append(" is starting at time=").append(Util.ascdatetime2(current)));
    prta(Util.clear(runsb).append(tag).append("End at ").append(Util.ascdatetime2(endAt)));
    try {

      /*
       * Write to a timestamp file the current time of the block of data (first time point) in case
       * the computer crashes.  The program will read the file to get the time so it can start again
       * at the point it stopped.
       */
      if(rftime == null ) {   // This is a real time process, track the time so it can be picked up
        rftime = new RandomAccessFile(fkparms.getStartTimeFile(),"rw");
        rftime.seek(0L);     //position the pointer in the file
        Util.clear(runsb).append(current).append("\n");
        Util.stringBuilderToBuf(runsb, timebuf);
        rftime.write(timebuf, 0, runsb.length());
        rftime.setLength(runsb.length());
      }
      long lastTimeUpdate=current;
      //FKResult fkresults = array.getFKResult();   // make the structure for getting results.
      int advanceMillis =  (int) ((npts - fkparms.getOverlap())*fkparms.getDT()*1000.+0.5);  // force advance
      StringBuilder channel = new StringBuilder(12);
      channel.append(array.getRef().getChannel());
      Util.stringBuilderReplaceAll(channel, '_', ' ');
      rawout = new RawInputClient(channel.toString(), edgeServer, edgePort, cwbServer, cwbPort, this);  // note port 7972
      StringBuilder channelScratch = new StringBuilder(12).append(channel);
      channelScratch.setCharAt(8, 'X');
      

      int [] beam = new int[npts/2];
      double resultsRate = 1./fkparms.getBeamWindow()*2.;
      prta(Util.clear(runsb).append(tag).append("Starting FK for channel ").append(channel).append(" resultRate=").append(resultsRate));
      FKResult results = array.getFKResult();
      int ngood=nseis;
      while(!terminate) {
        if(current > endAt) {
          prta("End time has been reached end="+Util.ascdatetime2(endAt)+" Idle here forever");
          for(;;) try{sleep(1000); if(terminate) break;} catch(InterruptedException e) {}
          if(terminate) break;
        }
        // If behind real time, need to check on channels and advance by 30 seconds if none is found
        gc.setTimeInMillis(current);
        int nchan = array.getTimeseries(gc,npts, ngood);
        //boolean found=false;
        if( nchan <= 0) {     // Did not process this time
          if(current < System.currentTimeMillis() - fkparms.getLatencySec()*1000) {  // out of 5 minute window, process every section
            if(-nchan > nseis/2) {    // We have some channels but not all of them
              ngood = -nchan;         // set a smaller minimum size and try again
              continue;
            }      // accept what ever number of channels we can get
            current +=  advanceMillis;  // force advance - not enough channels
            prta(Util.clear(runsb).append(tag).append("Out of 5 minute realtime window.  Advance to ").append(Util.ascdatetime2(current)).
                    append("nch=").append(nchan).append(" ngood=").append(ngood).append(" nseis=").append(nseis));
          }
          else {      // within the 5 minute window, but no data, need to sleep for some more data
            prta(Util.clear(runsb).append(tag).append("In 5 minute realtime window. Wait ").append(advanceMillis/500.).
                    append(" current=").append(Util.ascdatetime2(current)).append(" nch=").append(nchan).
                    append(" ngood=").append(ngood).append(" nseis=").append(nseis));
            try{sleep(2*advanceMillis);} catch(InterruptedException e) {}
          }
        }
        else {        // it processed, advance the time
          if(nchan > ngood) ngood = nchan;    // If we have move channels this time, make that the new benchmark
          //prta(Util.clear(runsb).append(tag).append("Time processed : "+Util.ascdatetime2(current)+" method="+fkparms.getBeamMethod()));
            // output the data
          switch(fkparms.getBeamMethod()) {
          case 0:
            channel.setCharAt(11, '0');
            channelScratch.setCharAt(11, '0');
            if(chans[0] == null) chans[0] = channel.toString();
            double [] arraybeam = array.getBeam();
            for(int i=npts/4; i<npts*3/4; i++) beam[i-npts/4] = (int) Math.round(arraybeam[i]);
            gc.setTimeInMillis(current+advanceMillis/2);
            rawout.send(channel, npts/2, beam, gc, fkparms.getRate(), 0, 0, 0, 0);
            prta(Util.clear(runsb).append(tag).append("Add timeseries ").append(channel).append(" ").
                    append(Util.ascdatetime2(gc)).append(" npts=").append(npts/2).
                    append(" rt=").append(fkparms.getRate()).append(" nch=").append(nchan).
                    append("/").append(ngood).append("/").append(nseis));
            results.writeStatistics(this,chans[0]);
            //RawToMiniSeed.forceout(channel);
            break;
          default:
            prta(Util.clear(runsb).append(tag).append("Method not supported ").append(fkparms.getBeamMethod()));
          } 
          // Write out time series for all of the statistics channels
          // LOC-D Power[0]*10^6
          //channelScratch.setCharAt(11, 'D');
          //idata[0] = (int) Math.round(results.getPower()[0]*1000000.);
          //rawout.send(channelScratch, 1, idata, gc, resultsRate, 0, 0, 0, 0);
          // LOC-E power[1]*10^6
          //channelScratch.setCharAt(11, 'E');
          //idata[0] = (int) Math.round(results.getPower()[1]*1000000.);
          //rawout.send(channelScratch, 1, idata, gc, resultsRate, 0, 0, 0, 0);
          // LOC-F power[2]*10^6
          //channelScratch.setCharAt(11, 'F');
          //idata[0] = (int) Math.round(results.getPower()[2]*1000000.);
          //rawout.send(channelScratch, 1, idata, gc, resultsRate, 0, 0, 0, 0);
          // LOC-L Mean avg*10^6
          //channelScratch.setCharAt(11, 'L');
          //idata[0] = (int) Math.round(results.getMeanAvg()*1000000.);
          //rawout.send(channelScratch, 1, idata, gc, resultsRate, 0, 0, 0, 0);
          // LOC-M Mean*10^6
          //channelScratch.setCharAt(11, 'M');
          //idata[0] = (int) Math.round(results.getMean()*1000000.);
          //rawout.send(channelScratch, 1, idata, gc, resultsRate, 0, 0, 0, 0);
          // LOC-N power normalization/1000
          channelScratch.setCharAt(9, 'P');
          idata[0] = (int) Math.round(Math.sqrt(results.getPowerNorm()));
          rawout.send(channelScratch, 1, idata, gc, resultsRate, 0, 0, 0, 0);
          // LOC-R ratio of powerNorm/powerNormAvg*10
          channelScratch.setCharAt(9, 'R');
          idata[0] = (int) Math.round(results.getPowerRatio()*10);
          rawout.send(channelScratch, 1, idata, gc, resultsRate, 0, 0, 0, 0);
          // LOC-Q ratio of (power - meanavg)/stdavg*10.     
          //channelScratch.setCharAt(11, 'Q');
          //idata[0] = (int) Math.round((results.getPower()[0]-results.getMeanAvg())/results.getStdAvg()*10.);
          //rawout.send(channelScratch, 1, idata, gc, resultsRate, 0, 0, 0, 0);
          // LOC-O ratio to (power - mean)/std*10
          //channelScratch.setCharAt(11, 'O');
          //idata[0] = (int) Math.round((results.getPower()[0]-results.getMean())/results.getStdDev()*10.);
          //rawout.send(channelScratch, 1, idata, gc, resultsRate, 0, 0, 0, 0);
          // LOC-W ration to (sum powers - 3*mean)/std*10
          //channelScratch.setCharAt(11, 'W');
          //idata[0] = (int) Math.round((results.getPower()[0]+results.getPower()[1]+results.getPower()[2]-3.*results.getMean())/results.getStdDev()*10.);
          //rawout.send(channelScratch, 1, idata, gc, resultsRate, 0, 0, 0, 0);
          // LOC-T Total power (near 1)*1000
          //channelScratch.setCharAt(11, 'T');
          //idata[0] = (int) Math.round(results.getTotalPower()*1000.);
          //rawout.send(channelScratch, 1, idata, gc, resultsRate, 0, 0, 0, 0);
          // LOC-U StdDev avg*10^6
          //channelScratch.setCharAt(11, 'U');
          //idata[0] = (int) Math.round(results.getStdAvg()*1000000.);
          //rawout.send(channelScratch, 1, idata, gc, resultsRate, 0, 0, 0, 0);
          // LOC-V StdDEv*10^6
          //channelScratch.setCharAt(11, 'V');
          //idata[0] = (int) Math.round(results.getStdDev()*1000000.);
          //rawout.send(channelScratch, 1, idata, gc, resultsRate, 0, 0, 0, 0);
          // LOC-S Max slownes * 1000.
          channelScratch.setCharAt(9, 'S');
          idata[0] = (int) Math.round(results.getMaxSlw()*1000.);
          rawout.send(channelScratch, 1, idata, gc, resultsRate, 0, 0, 0, 0);
          // LOC-B instantaneous azm*10
          channelScratch.setCharAt(9, 'B');
          idata[0] = (int) Math.round(results.getAzi()*10.);
          rawout.send(channelScratch, 1, idata, gc, resultsRate, 0, 0, 0, 0);
          // LOC-A applied Azimuth *10
          channelScratch.setCharAt(9, 'A');
          idata[0] = (int) Math.round(results.getAziApplied()*10.);
          rawout.send(channelScratch, 1, idata, gc, resultsRate, 0, 0, 0, 0);
          // LOC-C max azimuth diff
          channelScratch.setCharAt(9, 'C');
          idata[0] = results.getMaxAziDiff();
          rawout.send(channelScratch, 1, idata, gc, resultsRate, 0, 0, 0, 0);

          
          if(current - lastTimeUpdate > 120000) {
            rftime.seek(0L);     //position the pointer in the file
            Util.clear(runsb).append(current).append("\n");
            Util.stringBuilderToBuf(runsb, timebuf);
            rftime.write(timebuf, 0, runsb.length());
            lastTimeUpdate = current;
          }
          current += advanceMillis; // advance to next time
          
        }
      }           // while(!terminate)
    } catch(IOException e) {
        e.printStackTrace(getPrintStream());
        prta(Util.clear(runsb).append(tag).append("FK.Main(): Can't open timelog file ").append(fkparms.getStartTimeFile()));
        System.exit(1);
    }
    catch(RuntimeException e) {
      prta(Util.clear(runsb).append(tag).append(" FK has runtime e=").append(e));
      e.printStackTrace(getPrintStream());
      e.printStackTrace();
    }
    prta(Util.clear(runsb).append(tag).append(" is exiting"));
    running=false;
  }

  
  /**
   * Main program
   * This is my first attempt at writing a Java program, so I am widely inconsistent in
   * my use of objects.  None-the-less, I d
   * @param args  Command line arguments
   */
  public static void main(String [] args) {
    Util.init("edge.prop");
    Util.setModeGMT();
    QueryRing.setDebugChan("USDUG  BHZ00"); 
      
    String argline = "";
    for (String arg : args) {
      argline += arg + " ";
    }
    FK fk = new FK(argline.trim(), "TEST");
    //fk.run(args);
    
 }

}