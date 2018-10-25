/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */

package gov.usgs.anss.fk;
import gov.usgs.anss.edge.RawInputClient;
import gov.usgs.anss.util.Util;
import gov.usgs.anss.edgethread.EdgeThread;
import gov.usgs.anss.edgethread.FakeThread;
import gov.usgs.cwbquery.QueryRing;
import gov.usgs.anss.edgethread.ManagerInterface;
import gov.usgs.anss.util.AnssPorts;
import gov.usgs.anss.picker.JsonDetectionSender;
import gov.usgs.anss.picker.PickerManager;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.GregorianCalendar;

/**
 * Computes the FK results within an EdgeThread.  All of the parameters passed to this routine are
 * passed to the FKArray object created to do the calculations.  The only parameters which only apply to
 * this thread are ones which redirect the output to a different edge and cwb - not normally needed.
 * <br>
 * In an QueryMom environment this thread would be started from the FKManager.  For
 * debugging it can be started by hand using its main class to pass all of the many
 * parameters.  This class uses the QuerySpan class to access time series either from 
 * the memory cache, if run with a memory caching QueryMom, or from disk if not.
 * <p>
 * The parameters are documented here, but they are really parsed and managed in the 
 * FKParams class.
 * <p>
 * The run() loop hear uses the FKArray.getTimeseries method to get the data and if the number
 * of channels exceed the number acceptable to run() (ngood variable), then the FKArray computers the beam. 
 * The realtime window depends on the latency seconds set by the -l parameter (def = 300).  
 * If data requested is within
 * this number of seconds of the current time, then the algorithm is in the real time window.
 * <br>
 * The ngood needed to perform a beam calculation is managed like this :
 * <pre>
 * 1)  It is set at the full number of channels configured for the array initially, this step is not repeated, 
 * 2a)  If in the real time window, then the number of returned channels must bt &gt=ngood, until it falls out of the real time window.
 * 2b)  If not in the real time window :
 *      If the number returned from getTimeseries is less than the current ngood (its returned as a negative), 
 *          then test to see if the number of channels retured  is greater than 1/2 of the configured channels - if so set the new ngood
 *          to the number returned, 
 * 3) If in or out of the real time window, if the number returned by getTimeseries() 
 * exceeds the current ngood, increase ngood to be the number returned.
 * </pre>
 * 
 * 
 * <PRE>
 * fk -s stationfile -fl hpc/lpc/npoles/npasses/hpass/lpass -fk kmax/nk -bm hpcorner/lpcorner/beamlen/overlap [-cwb][-dbg][-t yr/mn/dy hr:min:sec] 
 * switch   Args    Description 
 * -dbg                write into log files
 * 
 * Switch   Description of parameters (these are parsed in FKParams which is invoked in FKArray).
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
 * - ftlen: window length (in power of 2) for averaging the ftest results (typical 8 or 16)
 * -l secs    Number of seconds to use as the normal latency of this array from real time - used to minimize bashing for data when it is not yet in.
 * -beammethod n  Use the n method for making the beam (def=0 which is normal time domain stacking)
 * 
 * These switches only refer to running the FK in manuall mode using the main() in this class :
 *  * -cwb                write output into the CWB
 * -cwbip  ip.add   The IP address of the CWB which would get data if older than 10 days (see RawInputClient for details)
 * -cwbport nnnn    Port to send CWB data that is older than 10 days (def=0 discard the data)
 * -edgeip ip.adr   The IP address to send data within the 10 day window (def = localhost)(see RawInputClient for details)
 * -edgeport nnn    THe port to send the data within 10 days (def= 7972)
 * -longgap  nnnn   Set the number of seconds with no new data to modify behavior to only look every 5 minutes or so(def 600)
 *
 * </PRE>
 */
public final class FK extends EdgeThread implements ManagerInterface {

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
  private String cwbServer="localhost"; 
  private int cwbPort;
  private int state;
  private long longGapInterval = 600000;
  public int inState(){return state;}
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
    sb.append(tag).append(" ").append((rawout == null?"Null":rawout.toString()));
    return sb;
  }

  /** return the status string for this thread 
   *@return A string representing status for this thread */
  @Override
  public StringBuilder getStatusString() {
    Util.clear(statussb).append(tag).append(" st=").append(state);
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
    prta(tag+" fk cons array="+array+" fkparms="+fkparms);
    String [] args = argline.split("\\s");
    for(int i=0; i<args.length; i++) {
      if(args[i].equals("-cwbip")) {cwbServer=args[i+1];i++;}
      if(args[i].equals("-edgeip")) {edgeServer=args[i+1];i++;}
      if(args[i].equals("-cwbport")) {cwbPort=Integer.parseInt(args[i+1]); i++;}
      if(args[i].equals("-edgeport")) {edgePort=Integer.parseInt(args[i+1]); i++;}
      if(args[i].equals("-longgap")) {longGapInterval = Integer.parseInt(args[i+1]) * 1000; i++;}
    }
    prta(" new FK "+toStringBuilder(null)+" args="+argline+" parms="+fkparms.toStringBuilder(null)+" "+super.toString());
    Util.prta(" new FK2 "+toStringBuilder(null)+" args="+argline+" parms="+fkparms.toStringBuilder(null)+super.toString());
    start();
  }
  @Override
  public void run() {
    running=true;
    RandomAccessFile rftime = null;   //file containing the time (in millis) of the last processed block of data
    byte [] timebuf = new byte[50];   // for .time files with millis newline and date in human form
    String [] chans = new String[10]; // This contains the channel name for each method
    state=0;
    int npts=fkparms.getBeamlen();         //number of points in the time series (powers of 2)
    long lastDataReturn = System.currentTimeMillis() - longGapInterval + 60000; //int overlap = fkparms.getOverlap();    //number of points in the overlap (powers of 2; eg npts=256, overlap=128:  50% overlap)
    //while(true) try{sleep(1000);} catch(InterruptedException e) {break;}  // DEBUG: do nothing yet

    int [] idata = new int[10];
    //logname is the name of the logfile on the CWB side
    String logname = array.getRef().getArrayname().trim()+"beam.log";
    int nseis=array.getNActive();
    StringBuilder runsb = new StringBuilder(100);

    long current = System.currentTimeMillis() - 500000;
    GregorianCalendar gc = new GregorianCalendar();
    if(fkparms.getStartTime() == null) {
      state=20;
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
      state=21;
      current = fkparms.getStartTimeInMillis(); // User supplied start time
    }
    state=22;
    long endAt = fkparms.getEndTimeInMillis();
   

    prta(Util.clear(runsb).append(tag).append(Util.ascdate()).append(" is starting at time=").append(Util.ascdatetime2(current)));

    try {

      /*
       * Write to a timestamp file the current time of the block of data (first time point) in case
       * the computer crashes.  The program will read the file to get the time so it can start again
       * at the point it stopped.
       */
      if(rftime == null ) {   // This is a real time process, track the time so it can be picked up
        state=23;
        rftime = new RandomAccessFile(fkparms.getStartTimeFile(),"rw");
        rftime.seek(0L);     //position the pointer in the file
        Util.clear(runsb).append(current).append("\n").append(Util.ascdatetime2(current)).append("\n");
        Util.stringBuilderToBuf(runsb, timebuf);
        rftime.write(timebuf, 0, runsb.length());
        rftime.setLength(runsb.length());
      }
      long lastTimeUpdate=current;
      state=24;
      //FKResult fkresults = array.getFKResult();   // make the structure for getting results.
      int advanceMillis =  (int) ((npts - fkparms.getOverlap())*fkparms.getDT()*1000.+0.5);  // force advance
      StringBuilder channel = new StringBuilder(12);
      channel.append(array.getRef().getChannel());
      Util.stringBuilderReplaceAll(channel, '_', ' ');
      rawout = new RawInputClient(channel.toString(), edgeServer, edgePort, cwbServer, cwbPort, this);  // note port 7972
      rawout.send(channel, 0, idata, gc, 0., 0,0,0, advanceMillis);   // Set the tag
      StringBuilder channelScratch = new StringBuilder(12).append(channel);
      channelScratch.setCharAt(8, 'X');
      

      int [] beam = new int[npts/2];
      double resultsRate = 1./fkparms.getBeamWindow()*2.;
      prta(Util.clear(runsb).append(tag).append("Starting FK for channel ").append(channel).append(" resultRate=").append(resultsRate));
      FKResult results = array.getFKResult();
      int ngood=nseis;
      state=25;
      while(!terminate) {
        state=1;
        if(current > endAt) {
          prta("End time has been reached end="+Util.ascdatetime2(endAt)+" Idle here forever");
          for(;;) try{sleep(1000); if(terminate) break;} catch(InterruptedException e) {}
          if(terminate) break;
        }
        state=2;
        // If behind real time, need to check on channels and advance by 30 seconds if none is found
        gc.setTimeInMillis(current);
        int nchan = array.getTimeseries(gc,npts, ngood);
        state=3;
        //boolean found=false;
        long now = System.currentTimeMillis();
        if( nchan <= 0) {     // Did not process this time
          state=4;
          if(current < now - fkparms.getLatencySec()*1000 || now - lastDataReturn > longGapInterval) {  // out of 5 minute window, process every section
            if(-nchan > nseis/2) {    // We have some channels but not all of them
              ngood = -nchan;         // set a smaller minimum size and try again
              continue;
            }      // accept what ever number of channels we can get
            current +=  advanceMillis;  // force advance - not enough channels
            prta(Util.clear(runsb).append(tag).append("Out of 5 minute realtime window.  Advance to ").
                    append(Util.ascdatetime2(current)).append("nch=").append(nchan).
                    append(" ngood=").append(ngood).append(" nseis=").append(nseis));
            if(now - lastDataReturn > longGapInterval) {
              try {
                prta(Util.clear(runsb).append(tag).append("** Out of 5 minutes more than 10 minutes - sleep 300 ").append((now - lastDataReturn)/1000));
                sleep(300000);
                long inc = Math.min(now - current - 300000, Math.max((now - current) / 20, 600000));
                prta(Util.clear(runsb).append(tag).append(" inc=").append(inc).
                        append(" now -curr -330=").append(now - current -300000).
                        append(" now-curr/20=").append((now-current)/20));
                current = current + inc;
                
                // Update the current time as we progress
                rftime.seek(0L);     //position the pointer in the file
                Util.clear(runsb).append(current).append("\n ").append(Util.ascdatetime2(current)).append("\n");
                Util.stringBuilderToBuf(runsb, timebuf);
                rftime.write(timebuf, 0, runsb.length());
                rftime.setLength(runsb.length());
              }
              catch(InterruptedException expected) {
              }
            }
          }
          else {      // within the 5 minute window, but no data, need to sleep for some more data
            prta(Util.clear(runsb).append(tag).append("In 5 minute realtime window. Wait ").append(advanceMillis/500.).
                    append(" current=").append(Util.ascdatetime2(current)).append(" nch=").append(nchan).
                    append(" ngood=").append(ngood).append(" nseis=").append(nseis));
            state=5;
            try{sleep(2*advanceMillis);} catch(InterruptedException e) {}
            state=6;
          }
        }
        else {        // it processed, advance the time
          state=7;
          lastDataReturn = now;
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
              state=8;
              rawout.send(channel, npts/2, beam, gc, fkparms.getRate(), 0, 0, 0, 0);
              prta(Util.clear(runsb).append(tag).append("Add timeseries ").append(channel).append(" ").
                      append(Util.ascdatetime2(gc)).append(" npts=").append(npts/2).
                      append(" rt=").append(fkparms.getRate()).append(" nch=").append(nchan).
                      append("/").append(ngood).append("/").append(nseis));
              state=9;
              results.writeStatistics(this,chans[0]);
              state=10;
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
          state=11;
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

          state=13;
          if(current - lastTimeUpdate > 120000) {
            state=14;
            rftime.seek(0L);     //position the pointer in the file
            Util.clear(runsb).append(current).append("\n ").append(Util.ascdatetime2(current)).append("\n");
            Util.stringBuilderToBuf(runsb, timebuf);
            rftime.write(timebuf, 0, runsb.length());
            rftime.setLength(runsb.length());
            lastTimeUpdate = current;
          }
          state=15;
          current += advanceMillis; // advance to next time
          
        }
      }           // while(!terminate)
    } catch(IOException e) {
      e.printStackTrace(getPrintStream());
      prta(Util.clear(runsb).append(tag).append("FK.Main(): Can't open timelog file ").append(fkparms.getStartTimeFile()));
      Util.exit(1);
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
   * @param args  Command line arguments
   */
  public static void main(String [] args) {
    Util.init("edge.prop");
    Util.setModeGMT();
    QueryRing.setDebugChan("USDUG  BHZ00"); 
    if(args.length == 0) {
      GregorianCalendar newStart = new GregorianCalendar();
      long start = (newStart.getTimeInMillis() - 86400000L*2) / 86400000L * 86400000L;
      newStart.setTimeInMillis(start);
      Util.prt("start="+Util.ascdatetime2(start));
      GregorianCalendar newEnd = new GregorianCalendar();
      newEnd.setTimeInMillis(start+86400000L);     // process one day
      args = ("-json path;./JSON"+//&kafka;beam;igskcicgvmkafka.cr.usgs.gov:9092&kafka;pick;igskcicgvmkafka.cr.usgs.gov:9092"+
            " -t "+Util.ascdatetime(newStart)+" -e "+Util.ascdatetime(newEnd)+
            " -s config/NVAR.sta -fl 0.0/0.0/0/0/f/f -fk 1.0/101  -bm 1.0/3.0 "+
              "-ftlen 0 -l 1200 -beammethod 0 -snr 5.0 -cwbin cwbpub.cr.usgs.gov  >> nvar").split("\\s");
    }
    String argline = "";
    for( int i=0; i<args.length; i++) {
      if(args[i].equalsIgnoreCase("-json")) {
        FakeThread fake = new FakeThread("-empty >>fake","FAKE");
         JsonDetectionSender json = new JsonDetectionSender("-json "+args[i+1], fake);
         PickerManager.setJsonDetectionSender(json);
         i++;
      }
      else argline += args[i]+ " ";
    }
    FK fk = new FK(argline.trim(), "TEST");
    while(true) {
      Util.sleep(10000);
    }
  }
}
