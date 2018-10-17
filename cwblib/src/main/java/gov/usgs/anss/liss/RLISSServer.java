/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package gov.usgs.anss.liss;
/*
 * LISSServer.java
 *
 * Created on April 2, 2007, 12:22 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */
//import gov.usgs.anss.edgemom.*;
import gov.usgs.anss.edgeoutput.RingFileToLISS;
import gov.usgs.anss.edgeoutput.LISSStationServer;
import gov.usgs.anss.edgeoutput.ChannelHolder;
import gov.usgs.anss.edge.EdgeProperties;
import java.io.FileNotFoundException;
//import java.util.Iterator;
import java.text.DecimalFormat;
import gov.usgs.anss.edgemom.EdgeChannelServer;
import gov.usgs.anss.edgeoutput.*;
import gov.usgs.edge.config.Channel;
import gov.usgs.anss.edgethread.EdgeThread;
import gov.usgs.anss.util.*;
import gov.usgs.anss.edge.*;
import gov.usgs.alarm.*;
import gnu.trove.iterator.TLongObjectIterator;
/** This class acts as the main() for the LISSServer process.  It creates a RingFileToLISS
 * for the ring and creates the LISSStationServers (one per station), which will take data from the 
 * RingFileToLISS.  Most of the command line arguments are actually intended for the
 * RintFileToChannelInfrastructure, so examine that class for command line arguments.
 * 
 * After starting the classes this main periodically 1)  Checks for new LISS station assignments and starts 
 * the LISSStationServer, 2)  Monitors memory and other SOH on this process, 3)  periodically calls the
 * method that detects and disposes of dead connections to the LISSStationServer created sockets.
 *
 * @author davidketchum
 */
public class RLISSServer {
  
  /** Creates a new instance of LISSServer */
  public RLISSServer() {
  }
/**
   * @param args the command line arguments
   */
  public static void main(String[] args) {
    EdgeProperties.init();
    boolean makeCheck=true;
    Util.setModeGMT();
    Util.setProcess("RLISSServer");
    //EdgeMom.setNoConsole(false);
    //EdgeMom.prt("no console");
    EdgeThread.setUseConsole(false);
    boolean dbg=false;
    Util.setNoInteractive(true);
    Util.prta(Util.ascdate()+" Starting up");
    EdgeThread.setMainLogname("rliss");
    EdgeChannelServer echn = new EdgeChannelServer("-empty","ECHN");
    LISSStationServer.setParent(echn);
    while(!EdgeChannelServer.isValid()) {try {Thread.sleep(100);} catch(InterruptedException e) {}}
    TLongObjectIterator<Channel> itr = EdgeChannelServer.getIterator();
    while(itr.hasNext()) {
      itr.advance();
      Channel c = itr.value();
      if( (c.getSendtoMask() & 2) != 0) {
        LISSStationServer.getServer(c.getChannel());
      }
    }
    // figure out if this is > user vdl (add vdl # to file name)
    boolean done=false;
    int ninfra = 0;
    RingFileToLISS [] infras = new RingFileToLISS[10];
    for(int j=20; j>=0; j--) {
      String nodeUser = ""+j;
      if(j == 0) nodeUser="";
      try{
        RawDisk rw = new RawDisk("/data2/RLISS"+nodeUser+".ring","r");
      }
      catch(FileNotFoundException e) {
        continue;
      }
      String argline ="-ring /data2/RLISS"+nodeUser+".ring -bitid 2 ";
      for (String arg : args) {
        argline += arg + " ";
        if (arg.equals("-dbg")) {
          dbg=true;
        }
      }
      argline += " >>rliss"+nodeUser;
      Util.prta("Start RF2RLISS on /data2/RLISS"+nodeUser+".ring");
      infras[ninfra++] = new RingFileToLISS( argline,"RF2LISStest");
      LISSStationServer.setParent(infras[ninfra-1]);
    }
    
    // Memory monitoring loop
    long freeMemory;
    long maxMemory;
    long totalMemory;
    long lastPage = System.currentTimeMillis();
    long lastPage2 = System.currentTimeMillis();
    DecimalFormat df1 = new DecimalFormat("0.0");
    EdgeThread par = infras[0];
    int loop=1;
    long lastTotal=lastPage;
    for(;;) {
      if(loop % 5 == 0) {
        // Start up a LISSStationServer for each configured LISS station, do this every 5 minutes or so to pick up new stations
        itr = EdgeChannelServer.getIterator();
        while(itr.hasNext()) {
          itr.advance();
          Channel c = itr.value();
          if( (c.getSendtoMask() & 2) != 0) LISSStationServer.getServer(c.getChannel());// RLISS and LISS is mask 6
        }
      }
      try {Thread.sleep(60000);} catch(InterruptedException e) {}
      freeMemory = Runtime.getRuntime().freeMemory();
      maxMemory = Runtime.getRuntime().maxMemory(); 
      totalMemory = Runtime.getRuntime().totalMemory();
      if(loop % 5 == 0) par.prta("freeMemory="+df1.format(freeMemory/1000000.)+" maxMem="+df1.format(maxMemory/1000000.)+
          " totMem="+df1.format(totalMemory/1000000.)+
              " net="+df1.format((maxMemory - totalMemory+freeMemory)/1000000.));
      if( (maxMemory - totalMemory) + freeMemory < 10000000) {
        if(System.currentTimeMillis() - lastPage > 900000) {
          SendEvent.sendEvent("Edge","MemoryLow",
              "RLISSServer free memory critical ="+df1.format((maxMemory - totalMemory+freeMemory)/1000000.)+" "+Util.getNode(),
              Util.getNode(),"RLISSServer");
          SimpleSMTPThread.email(Util.getProperty("emailTo"),"RLISSServer free memory critical ="+
              df1.format((maxMemory - totalMemory+freeMemory)/1000000.)+" "+Util.getNode(),
               Util.ascdate()+" "+Util.asctime()+
              "\nfreeMemory="+df1.format(freeMemory/1000000.)+" maxMem="+df1.format(maxMemory/1000000.)+
              " totMem="+df1.format(totalMemory/1000000.)+
              " net="+df1.format((maxMemory - totalMemory+freeMemory)/1000000.)+"\n");
          lastPage = System.currentTimeMillis();
        }
      }
      if(loop % 2 == 0) {
        for(int i=0; i<ninfra; i++) par.prt(infras[i].toString());
        par.prt(ChannelHolder.getSummary());
        par.prt(LISSStationServer.getStatus());
        LISSStationServer.clearDeadConnections(3600000);
      }
      if(loop % 2 == 0) {
        long total = LISSStationServer.getTotalPackets();
        par.prta("Total packets = "+(total - lastTotal));
        if(Math.abs(total - lastTotal) < 25) {
          SendEvent.edgeSMEEvent("RLISSUnderMin","RLISS has only sent out "+(total-lastTotal)+
              " packets on "+Util.getNode()+" in last 10 minutes","RLISSServer");
            lastPage2 = System.currentTimeMillis();
        }
        if(loop % 60 == 0) {
          par.prta("Threads \n"+Util.getThreadsString());
        }
        lastTotal=total;
      }
      loop++;
    }

  }  
}

