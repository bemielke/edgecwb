/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */

/*
 * LISSServer.java
 *
 * Created on May 30, 2007, 5:04 PM
 *
 * To change this template, choose Tools | Template Manager
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
import gov.usgs.anss.edgeoutput.RingFileToChannelInfrastructure;
import gov.usgs.anss.edgeoutput.RingFileToLISS;
import gov.usgs.anss.edgeoutput.LISSStationServer;
import gov.usgs.anss.edgeoutput.ChannelHolder;
import gov.usgs.anss.edge.EdgeProperties;
import gov.usgs.alarm.SendEvent;
import gov.usgs.anss.edge.*;
import gov.usgs.anss.edgemom.EdgeChannelServer;
import gov.usgs.anss.edgeoutput.*;
import gov.usgs.anss.edgethread.EdgeThread;
import gov.usgs.anss.util.*;
import gov.usgs.edge.config.Channel;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.DecimalFormat;
//import java.util.Iterator;
import gnu.trove.iterator.TLongObjectIterator;


/** This class acts as the main() for the LISSServer process.  It creates a RingFileToChannelInfrastructure
 * for the ring and creates the LISSStationServers (one per station), which will take data from the 
 * RingFileToChannelInfrastructure.  Most of the command line arguments are actually intended for the
 * RintFileToChannelInfrastructure, so examine that class for command line arguments.
 * 
 * After starting the classes this main periodically 1)  Checks for new LISS station assignments and starts 
 * the LISSStationServer, 2)  Monitors memory and other SOH on this process, 3)  periodically calls the
 * method that detects and disposes of dead connections to the LISSStationServer created sockets.
 *
 * <PRE>
 * args     Description
 * -dbg     Set more log output
 * -nomonitor Do not send pages if not much data is going out.
 * </PRE>
 * @author davidketchum
 */
public class LISSServer {
  
  /** Creates a new instance of LISSServer */
  public LISSServer() {
  }
/**
   * @param args the command line arguments
   */
  public static void main(String[] args) {
    // TODO code application logic here
    EdgeProperties.init();
    boolean makeCheck=true;
    Util.setModeGMT();
    Util.setProcess("LISSServer");
    EdgeThread.setMainLogname("liss");
    //EdgeMom.setNoConsole(false);
    //EdgeMom.prt("no console");
    EdgeThread.setUseConsole(false);
    boolean dbg=false;
    Util.setNoInteractive(true);
    Util.prta(Util.ascdate()+" Starting up");
    EdgeChannelServer echn = new EdgeChannelServer("-empty >>lissechn","ECHN");
    while(!EdgeChannelServer.isValid()) {try {Thread.sleep(100);} catch(InterruptedException e) {}}
    // figure out if this is > user vdl (add vdl # to file name)
    boolean done=false;
    int ninfra = 0;
    EdgeThread [] infras = new EdgeThread[20];
    boolean nomonitor=false;
    // set up the RLISS LISSStationservers by going through the channels
    Util.prta("Check for LISS files");
    for(int j=10; j>=0; j--) {
      String nodeUser = ""+j;
      if(j == 0) nodeUser="";
      Util.prt("Check for LISS"+nodeUser);
      try{
        RawDisk rw = new RawDisk("/data2/LISS"+nodeUser+".ring","r");
      }
      catch(FileNotFoundException e) {
        continue;
      }
      String argline ="-ring /data2/LISS"+nodeUser+".ring -secdepth 300 -defclass LISSOutputer -minrecs 4 -bitid 1 ";
      for (String arg : args) {
        if (arg.equals("-nomonitor")) { 
          nomonitor=true;
          continue;
        }
        argline += arg + " ";
        if (arg.equals("-dbg")) {
          dbg=true;
        }
      }
      argline += " >>liss"+nodeUser;
      Util.prta("Start RFTCI on /data2/LISS"+nodeUser+".ring");
      infras[ninfra++] = new RingFileToChannelInfrastructure( argline,"RF2CItest");
    }
    // For each of the possible ring users, set up a RLISS Ring server (add vdl # to file name)
    byte [] buf = new byte[512];
    ByteBuffer bb = ByteBuffer.wrap(buf);
    for(int j=20; j>=0; j--) {
      String nodeUser = ""+j;
      if(j == 0) nodeUser="";
      try{
        Util.prt("try RLISS"+nodeUser);
        RawDisk rw = new RawDisk("/data2/RLISS"+nodeUser+".ring","r");
        rw.readBlock(buf, 0,512);
        bb.position(0);
        int next=bb.getInt();
        int size=bb.getInt();
        Util.prt("RLISS"+nodeUser+" found next="+next+" size="+size);
      }
      catch(FileNotFoundException e) {
        continue;
      }
      catch(IOException e) {
        Util.prt("IOExcp reading ring!"+e);
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
      //if(j == 10) LISSStationServer.setParent(infras[ninfra-1]);
    }    
    Util.prt("Start LISSStationsServer for LISS");
    // This sets up the login to the LISS file, and if none is present to the RLISS file
    LISSStationServer.setParent(infras[0]);     // Use the first LISS or RLISS thread
    EdgeThread par = infras[0];
    if(par == null) {
      Util.prta("There are no LISS or RLISS files! exit()");
      System.exit(0);
    }

    // Now start a LISSStationServer by looking for LISS or RLISS output on each channel
    // if getServer() does not find one, it starts one
    TLongObjectIterator<Channel> itr = EdgeChannelServer.getIterator();
    while(itr.hasNext()) {
      itr.advance();
      Channel c = itr.value();
      if( (c.getSendtoMask() & 3) != 0) {           // Start one for each LISS or RLISS
        LISSStationServer.getServer(c.getChannel());
      }
    }
    par.prt("Look at RLISS");
    // Memory monitoring loop
    long freeMemory;
    long maxMemory;
    long totalMemory;
    long lastPage = System.currentTimeMillis();
    long lastPage2 = System.currentTimeMillis();
    DecimalFormat df1 = new DecimalFormat("0.0");
    int loop=0;
    long lastTotal=lastPage;
    for(;;) {
      // every 5 minutes check the configuration and start any missing LISSStationServers for both RLISS and LISS
      if(loop % 5 == 0) {
        // Start up a LISSStationServer for each configured LISS station, do this every 20 minutes or so to pick up new stations
        itr = EdgeChannelServer.getIterator();
        while(itr.hasNext()) {
          itr.advance();
          Channel c = itr.value();
          if( (c.getSendtoMask() & 3) != 0) LISSStationServer.getServer(c.getChannel()); // 3 is LISS and RLISS mask
        }
      }
      try {Thread.sleep(60000);} catch(InterruptedException e) {}
      freeMemory = Runtime.getRuntime().freeMemory();
      maxMemory = Runtime.getRuntime().maxMemory(); 
      totalMemory = Runtime.getRuntime().totalMemory();
      if(loop % 5 == 0) par.prta("freeMemory="+df1.format(freeMemory/1000000.)+" maxMem="+df1.format(maxMemory/1000000.)+
          " totMem="+df1.format(totalMemory/1000000.)+
              " net="+df1.format((maxMemory - totalMemory+freeMemory)/1000000.));
      if( (maxMemory - totalMemory) + freeMemory < 5000000 && maxMemory == totalMemory) {
        if(System.currentTimeMillis() - lastPage > 900000) {
          SendEvent.sendEvent("Edge","MemoryLow",
              "LISSServer free memory critical ="+df1.format((maxMemory - totalMemory+freeMemory)/1000000.)+" "+Util.getNode(),
              Util.getNode(),"LISSServer");
          SimpleSMTPThread.email(Util.getProperty("emailTo"),"LISSServer free memory critical ="+
              df1.format((maxMemory - totalMemory+freeMemory)/1000000.)+" "+Util.getNode(),
               Util.ascdate()+" "+Util.asctime()+Util.getNode()+
              "\nfreeMemory="+df1.format(freeMemory/1000000.)+" maxMem="+df1.format(maxMemory/1000000.)+
              " totMem="+df1.format(totalMemory/1000000.)+
              " net="+df1.format((maxMemory - totalMemory+freeMemory)/1000000.)+"\n");
          lastPage = System.currentTimeMillis();
        }
      }
      if(loop % 30 == 0) {
        for(int i=0; i<ninfra; i++) par.prt("infras["+i+"]="+infras[i].toString());
        par.prt(ChannelHolder.getSummary());
        par.prt(LISSStationServer.getStatus());
        LISSStationServer.clearDeadConnections(3600000);
      }
      if(loop % 5 == 0) {
        long total = LISSStationServer.getTotalPackets();
        par.prta("Total packets = "+(total - lastTotal));
        if(Math.abs(total - lastTotal) < 25 && !nomonitor) {
          SendEvent.edgeSMEEvent("LISSUnderMin","LISS has only sent out "+(total-lastTotal)+
              " packets on "+Util.getNode()+" in last 10 minutes","LISSServer");
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
