/*
 *  This software is in the public domain because it contains materials 
 *  that originally came from the United States Geological Survey, 
 *  an agency of the United States Department of Interior. For more 
 *  information, see the official USGS copyright policy at 
 *  http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */

package gov.usgs.anss.waveserver;
import gov.usgs.alarm.SendEvent;
import gov.usgs.anss.edgeoutput.TraceBuf;
import gov.usgs.anss.edgethread.EdgeThread;
import gov.usgs.anss.util.Util;
import gov.usgs.anss.util.Subprocess;
import java.io.IOException;
import java.net.Socket;
import java.net.ServerSocket;
import java.util.GregorianCalendar;
import java.util.ArrayList;



/** This class monitors services from a QueryMom - QueryServer, CWBWaveServer - It needs to know the IP of the
 * Server, the ports for the two services, and a list of channels to use in testing.  
 * 
 * NOTE: this code does not appear to be active.  Similar code in gov.usgs.anss.alarm is being used.  This has the ability to kill a target.
 * 
 * If the cwbwsport is zero,
 * no testing of this function is done.  The query interval is normally from 65 seconds in the past to 60 seconds
 * in the past which works well for BH and HH data.  The delay secs for each port can be lengthened for lower
 * sample rate data so that only one or two packets of response are returned on each query (for 1 second data
 * the delay seconds should be about 600 seconds).
 * <br>
 * ipadr:cwbwsport[/delaysec]:queryport[/delaysec]:SEEDNAMEdashes:seedname2:seedname3:....
 * <br>
 * example: localhost:2060:2061/600:USDUG--BHZ00:ATPMR--BHZ--:GTLPAZ-BHZ00
 * <br>
 * This would allow the last 600 seconds in the query (useful for slow channels).  
 * Note the seednames must be 12 characters long following the NNSSSSSCCCLL convention with dashes for spaces.
 * <PRE>
 * field    value     Description
 * ipadr   ip.adr    Address of DNS resolvable name.  An exclamation point in the address turns on debugging.
 * cwbsport nnnn     The port of the CWBWaveServer (default delay=65 seconds)
 * queryport nnnn    The port of the MiniSeed QueryServer (default delay=65 seconds)
 * SEEDNAME NNSSSSSCCCLL  The 12 character seedname with hyphens for spaces
 * </PRE>
 * @author U.S. Geological Survey <ketchum at usgs.gov>
 */
public final class QueryMomMonitor extends Thread {
  private final int cwbwsport, queryport;
  private final String host;
  private final String [] channels;
  private WaveServerClient cwbws;
  private long wsdelay=65000;
  private long cwbdelay=65000;
  private final EdgeThread par;
  private boolean dbg;
  private final String tag;
  private final String target;
  
  /**
   * 
   * @param s A string to print to the log
   */
  protected final void prt(String s){ if(par == null) Util.prt(s); else par.prt(s);}
  /**
   * 
   * @param s A String to print in the log preceded by the current time
   */
  protected final void prta(String s){ if(par == null) Util.prta(s); else par.prta(s);}
  public void setDebug(boolean t) {dbg=t;}
  /**
   * 
   * @param argline a ipadr:cwbwsport:queryport:NNSSSS-CCCLL:seedname2:seedname3:.... command line
   * @param target A string to use killgen on should something wrong happen!
   * @param parent The logging process, if null, logging will go to the default log
   */
  public QueryMomMonitor(String argline, String target, EdgeThread parent)  {
    String [] args = argline.split(":");
    this.target = target;
    par=parent;
    if(args[0].contains("!")) {dbg=true; host=args[0].replaceAll("!","");}  // if !, then turn on debugging
    else host = args[0];
    prta("QMM: startup argline="+argline+" args.length="+args.length);
    if(args[1].contains("/")) {
      String [] parts = args[1].split("/");
      cwbwsport = Integer.parseInt(parts[0]);
      wsdelay = Integer.parseInt(parts[1])*1000;
      prta("QMM: got slash in 2nd arg "+args[1]+" parts.length="+parts.length+" wsdelay="+wsdelay+(parts.length > 1?parts[1]:""));
    }
    else cwbwsport = Integer.parseInt(args[1]);
    if(args[2].contains("/")) {
      String [] parts = args[2].split("/");
      queryport = Integer.parseInt(parts[0]);
      cwbdelay = Integer.parseInt(parts[1])*1000;
      prta("QMM: got slash in 3rd arg "+args[2]+" parts.length="+parts.length+" wsdelay="+cwbdelay+(parts.length > 1?parts[1]:""));
    }
    else queryport = Integer.parseInt(args[2]);
    channels = new String[args.length-3];
    for(int i=3; i<args.length; i++) {
      channels[i-3] = args[i].replaceAll("-"," ");
    }
    if(cwbwsport > 0) {
      cwbws = new WaveServerClient(host,cwbwsport);
      cwbws.setTimeoutInterval(5000);
    } // this does not actually open the port until a command is issued.
    else cwbws=null;
    setDaemon(true);
    tag = "QMM:["+host+"/"+cwbwsport+"/"+queryport+"]";
    start();
  }
  @Override
  public void run() {
    GregorianCalendar start=new GregorianCalendar();
    GregorianCalendar end = new GregorianCalendar();
    long now;
    try{sleep(Math.abs(tag.hashCode() % 300));} catch(InterruptedException expected) {}     // randomize start time
    ArrayList<TraceBuf> tbs = new ArrayList<>(10);
    byte [] version = "VERSION: QMM\n".getBytes();
    byte [] buf = new byte[512];
    //boolean okscnl= true;
    //boolean okmenu= true;
    prta(tag+" starting cwbws="+cwbws+" cwb="+host+"/"+queryport+"/"+cwbdelay+" ws="+cwbwsport+"/"+wsdelay);
    int ntimeout;
    StringBuilder versionback = new StringBuilder(100);
    for(;;) {
      try {
        now = System.currentTimeMillis();

        if(now % 86400000L < 300000) {
          prta(tag+"Need to wait for new day to get settled");
          try{sleep(300000 - now % 86400000L);} catch(InterruptedException expected) {}
          now = System.currentTimeMillis();
        }
        if(cwbws != null && cwbwsport > 0) {
          try {
            try(Socket s = new Socket(host, cwbwsport)) {
              s.setSoTimeout(10000);
              s.getOutputStream().write(version);
              int l=0;
              Util.clear(versionback);
              ntimeout=0;
              while(l == 0) {
                try{sleep(1000);} catch(InterruptedException expected) {}
                l = s.getInputStream().read(buf, 0, Math.min(512,s.getInputStream().available()));
                for(int i=0; i<l; i++) versionback.append((char) buf[i]);
                if(dbg) 
                  prta(tag+" VERSION command read = "+new String(buf, 0, l));
                ntimeout++;
                if(ntimeout >= 10) {
                  prta(tag+" **** VERSION Timed out");
                  panic();
                  try {sleep(90000);} catch(InterruptedException expected) {}
                  break;
                }
                if(Util.stringBuilderEqual(versionback,"PROTOCOL_VERSION: 3\n")) {
                  prta(tag+" protocol string match!");
                  break;
                }
              }
              s.close();
            }          
          }
          catch(IOException e) {
            handleIOError(e);
            try{sleep(90000);} catch(InterruptedException expected) {}    // wait for it to restart!
          }

          ntimeout=0;
          for(String channel : channels) {
            boolean okscnl=false;
            try {
              start.setTimeInMillis(now - wsdelay);
              end.setTimeInMillis(now - 60000);              
              cwbws.getSCNLRAW("QMM2", channel, start,end, true, tbs);
              if(dbg) 
                prta(tag+" GetSCNLRAW for "+channel+" "+Util.ascdatetime2(start)+
                      " returned "+tbs.size()+" "+(tbs.size() > 0?tbs.get(0).toString():":"));
              if(tbs.size() > 0) okscnl=true;
              else prta(tag+" *** GETSCNLRAW for "+channel+" "+Util.ascdatetime2(start)+" returned "+tbs.size());
              cwbws.freeMemory(tbs);
              tbs.clear();
              if(okscnl) break;
            }
            catch(IOException e) {
              if(cwbws.timedOut()) {
                ntimeout++;
                prta(tag+" data timed out! n="+ntimeout);
              }
              else handleIOError(e);
              cwbws.closeSocket();
              cwbws = new WaveServerClient(host, cwbwsport);
              cwbws.setTimeoutInterval(5000);
              if(e.toString().contains("ocket close")) {
                prta(tag+" the socket closed - timeout! ignore");
              }
              else {
                if(par == null) e.printStackTrace();
                else e.printStackTrace(par.getPrintStream());
              }
            }
          }
          if(ntimeout > 2) {
            prta(tag+" ***** data timeout panic!");
            panic();
          }
        }    
        try{sleep(30000);} catch(InterruptedException expected) {}
      }
      catch(RuntimeException e) {
        prta(tag+" *** got runtime error.  continue e="+e);
        e.printStackTrace(par.getPrintStream());
        SendEvent.edgeSMEEvent("RuntimeExcp", "QMM e="+e, this);
      }
    }   // end of infinite loop
  }
  private void panic() {
    try {
      SendEvent.edgeEvent("CWBWSTimeOut", tag+" data timeouts! restart "+target, this);
      Subprocess sp = new Subprocess("bash "+Util.fs+"home"+Util.fs+"vdl"+Util.fs+"vdl"+Util.fs+"SCRIPTS"+Util.fs+"killgen "+target);
      sp.waitFor(60000);
      prta(sp.getOutput());
      prta(sp.getErrorOutput());
    }
    catch(IOException | InterruptedException | RuntimeException e) {
      if(par == null) e.printStackTrace();
      else e.printStackTrace(par.getPrintStream());
    }
    try{sleep(600000);} catch(InterruptedException expected) {}
  }
  private void handleIOError (IOException e) {
    String err = e.toString();
    boolean timedOut = cwbws.timedOut();
    if(err.contains("Connection refused") ||
       err.contains("Connection timed out") ||
       err.contains("o route to host")) {
      if(err.contains("Connection refused")) err="refused";
      else if(err.contains("timed out")) err="Conn timed out";
      else if(err.contains("o route to host")) err="no route";
      else if(timedOut) {
        err = "data timed out";
      }
      prta(tag+"  *** did not connect err="+err);
    }
    else {
      prta(tag+"  *** e="+e);
    }
    
  }
    public static void  main(String [] args) {
      Util.init("edge.prop");
      ServerSocket server = null;
      if(args.length == 0) {
        try {
          server = new ServerSocket(2060, 10);
        }
        catch(IOException e) {
          e.printStackTrace();
        }
        args=new String[2];
        args[0]="localhost:2060:2061:USDUG--BHZ00:USISCO-BHZ00:IUANMO-BHZ00:USCBSK-BHZ00";
        args[1]="13Q0";
      }
      QueryMomMonitor mon = new QueryMomMonitor(args[0], args[1], null);
      mon.setDebug(true);
      for(;;) Util.sleep(1000);
    }
}
