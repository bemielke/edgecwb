/*
 *  This software is in the public domain because it contains materials 
 *  that originally came from the United States Geological Survey, 
 *  an agency of the United States Department of Interior. For more 
 *  information, see the official USGS copyright policy at 
 *  http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.picker;
import gov.usgs.alarm.SendEvent;
//import gov.usgs.anss.edgeoutput.GlobalPickSender;
//import gov.usgs.anss.edgeoutput.JSONPickGenerator;
import gov.usgs.anss.edgethread.EdgeThread;
import gov.usgs.anss.util.Util;
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
/**
 *
 * @author U.S. Geological Survey  <ketchum at usgs.gov>
 */
public class CWBCorrelationPicker extends CWBPicker {
  public static final String pickerType="correlationpicker";
  public static final long WARMUP_MS=0;
  private boolean ready;
  private boolean dbg;
  private boolean first;
  private final  StringBuilder tmpsb = new StringBuilder(100);
  private final  StringBuilder runsb = new StringBuilder(100);
  private final  StringBuilder line = new StringBuilder(100);
  @Override
  public boolean isReady() {return ready;}
  @Override
  public void terminate() { if(maker != null) maker.close(); writestate(); terminate=true;ready=false;}
  @Override
  public long getWarmupMS() {return WARMUP_MS;}
 @Override
  public StringBuilder getStatusString() {
    Util.clear(statussb);
    return statussb;
  }
  public CWBCorrelationPicker(String argline, String tag, FP6Instrumentation inst) {
    super(argline, tag, null);
    Util.setModeGMT();
    first=true;
  }
  @Override
  public void writestate() {
    synchronized(statussb) {
      Util.clear(statussb);
      writeStateFile(statussb);
    }
  }

  private StringBuilder key = new StringBuilder(20);
  private StringBuilder value = new StringBuilder(20);
  @Override
  public final void setArgs(String s) throws FileNotFoundException, IOException {   
    try {
      BufferedReader in = new BufferedReader(new StringReader(s));
      for(;;) {
        int len = Util.stringBuilderReadline(in, line);
        if(len < 0) break;
        if(line.indexOf("=") >= 0) continue;     // Its from the CWBPicker part of the template
        if(line.indexOf("!") >= 0) line.delete(line.indexOf("!"), line.length());
        if(line.length() == 0) continue;
        if(line.indexOf("#") > 0) {
          Util.clear(key).append(line.substring(0, line.indexOf("#")));
          Util.clear(value).append(line.substring(line.indexOf("#")+1));
          if(Util.stringBuilderEqualIgnoreCase(key,"-dbg")) dbg=true;
          else prt("**** Unknown tag in state file s="+line);
        }
      }
    }
    catch(IOException e) {
      
    }
  }
  @Override
  public void clearMemory() {
    //TODO : 
  }  
  @Override
  public void apply(GregorianCalendar time, double rate, int [][] data, int npts) {

      if(first) {
        prta("Filter setup :"); 
        first=false;
      }  // Was dump of mem structures
    
    // Handle any picks that occurred.

      /*pick(pickerType, picktime, 0., pk.period, "P", pickerror,     // amplitude is zero as FP6 does not generate one
              (pk.polarity== PickData.POLARITY_POS?"up":(pk.polarity==PickData.POLARITY_NEG?"down":null)),
              null, lowFreq, highFreq,  pk.amplitude)
      picks.clear();*/
  }
  public static void main(String [] args) throws InterruptedException, IOException {
    Util.init("edge.prop");
    Util.setModeGMT();
    CWBPicker picker;
    GregorianCalendar newStart = new GregorianCalendar();
    EdgeThread.setMainLogname("cwbcorr");
    
    // if no args, make the development one
    if(args.length == 0) {
      long start = (newStart.getTimeInMillis() - 86400000L*2) / 86400000L * 86400000L;
      newStart.setTimeInMillis(start);
      Util.prt("start="+Util.ascdatetime2(start));
      GregorianCalendar newEnd = new GregorianCalendar();
      newEnd.setTimeInMillis(start+86400000L);     // process one day
      //QuerySpan.setDebugChan("USDUG  BHZ00"); 
      /*CWBPicker cwbpicker = 
              new CWBPicker("-mf -json path;./JSON -c USDUG--BHZ00 -h 137.227.224.97 -b 2015-09-26-00:00 -e 2015-09-27-00:00 "+
                      "-auth FP6-TEST -agency US-NEIC -db localhost/3306:status:mysql:status -blocksize 10. >>USGSFP","CWBP", null);*/
      String cmd =  "-mf -1 -json path;./JSON -c USDUG--BHZ00 -h 137.227.224.97 -b "+
              Util.ascdatetime(newStart) + " -e "+Util.ascdatetime(newEnd)+
              " -auth SSD-TEST -agency US-NEIC -db localhost/3306:status:mysql:status -blocksize 10. "+
              " >>USGSCORR";
      picker = new CWBCorrelationPicker(cmd, "CWBCORR", null);

      while(picker.isAlive()) {
        Util.sleep(1000);
        if(picker.segmentDone()) {
          newStart.setTimeInMillis(newStart.getTimeInMillis()-86400000L);
          picker.runSegment(newStart, 86400., 0, -1, -1., -1., -1.);
        }    
      }
    }
    else {        // Build up the command line and run 
      String argline="";
      String nscl=null;
      FP6Instrumentation instrument=null;
      for(int i=0; i<args.length; i++) {
        argline += args[i].trim()+" ";
        if(args[i].equalsIgnoreCase("-c")) nscl=args[i+1];
        else if(args[i].equalsIgnoreCase("-db")) Util.setProperty("PickDBServer",args[i+1]);
        /*if(args[i].equalsIgnoreCase("-i")) {
          try {
            Class cl = Class.forName(args[i+1]);
            Class [] em = new Class[0];
            Constructor ct = cl.getConstructor(em);
            instrument = (FP6Instrumentation) ct.newInstance();
          }
          catch(ClassNotFoundException | NoSuchMethodException | SecurityException | 
                  InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
            e.printStackTrace();
            System.exit(0);
          }
        }*/
        
      }
      if(nscl == null) Util.exit("-c is mandatory for manual starts");
      else {
        argline = argline+">> CORR/"+nscl.replaceAll("-","_").replaceAll(" ","_").trim();
        picker = new CWBCorrelationPicker(argline, "CWBCORR", instrument);
        while(picker.isAlive()) {
          Util.sleep(1000);
          if(picker.segmentDone()) {
            break;
          }
        }
      }
    }
    System.exit(0);
  }  
}
