/*
 *  This software is in the public domain because it contains materials 
 *  that originally came from the United States Geological Survey, 
 *  an agency of the United States Department of Interior. For more 
 *  information, see the official USGS copyright policy at 
 *  http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.fetcher;

import gov.usgs.anss.edge.FetchList;
import gov.usgs.anss.edge.RawDisk;
import gov.usgs.anss.seed.MiniSeed; 
import gov.usgs.anss.seed.MiniSeedPool;
import gov.usgs.anss.util.Util;
import java.io.IOException;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.trinet.jasi.DataSource;
import org.trinet.jasi.JasiDatabasePropertyList;
import org.trinet.jdbc.datasources.DbaseConnectionDescription;
import org.trinet.util.LeapSeconds;
import org.trinet.waveserver.rt.WaveClientNew;
/**
 *
 * @author davidketchum
 */
public class TrinetRequest extends Fetcher {
  int blocksize;
  //Subprocess sp;
  String station;
  private static WaveClientNew wclient;
  private static final Integer wcmutex=Util.nextMutex();
  private static final MiniSeedPool msp = new MiniSeedPool();
  private final ArrayList<MiniSeed> mss = new ArrayList<>(1);

  @Override
  public void closeCleanup() {
    prta(station+" closeCleanup - terminate mysql and any subprocesses");
    //if(mysqlanss != null) mysqlanss.terminate();
    wclient.close();
  }
  public TrinetRequest(String [] args) throws SQLException {
    super(args);
    if(getSeedname().length() >= 7) {
      station = getSeedname().substring(2,7).trim()+getSeedname().substring(10).trim();
    }
    else {
      station = getType();
    }

    // Since this is a thread mysqanss might be in use by other threads.  Always
    // create
    tag="CWB"+getName().substring(getName().indexOf("-"))+"-"+station.trim()+":";
    prt(tag+" start "+orgSeedname+" "+host+"/"+port);
    openDataSource("/home/vdl/datasource.prop");
  }
  /** This creates a DB connection so the LeapSeconds can be read from the DB into LeapSeconds.
   * 
   * @param propFileName A property file with enough stuff to make a valid connection to
   * and oracle data source.
   * @return true if the data source appears to have worked
   */
  public final boolean openDataSource(String propFileName ) {
        JasiDatabasePropertyList newProps = null;
        
        newProps = new JasiDatabasePropertyList(propFileName, null); 
        Util.prta("Reading properties from file: " + propFileName+" #props="+newProps.size());
        // setup the db connection info
        DbaseConnectionDescription dbDescription = newProps.getDbaseDescription();
        boolean ret=true;
        try {
          if (! dbDescription.isValid()) {
              prta("Invalid dbase spec: "+dbDescription.toString());
              ret=false;                   
          }

          // Connect to the dbase 
          DataSource.createDefaultDataSource();
          if (!DataSource.set(dbDescription)) {
              prta("Invalid dbase: "+dbDescription.toString());
             ret=false;                        
          }        
        }
        catch(Exception e) {
          prta("Exception thrown setting up data source.");
          e.printStackTrace();
        }
       LeapSeconds.getLeapSecsAtNominal(System.currentTimeMillis()/1000.);    // this should set the leap seconds from the DB, warning if no
       return ret;
  }
  @Override
  public void startup() {
    if(localBaler) return;    // local baler, do not try Baler stuff.
    checkNewDay();
  }


  /** retrieve the data in the fetch entry
   *
   * @param fetch A fetch list object containing the fetch details
   * @return The MiniSeed data resulting from the fetch
   */
  @Override
  public synchronized ArrayList<MiniSeed> getData(FetchList fetch) {
    if(!mss.isEmpty()) 
      for(int i=mss.size()-1; i>=0; i--) msp.free(mss.get(i));
    mss.clear();
    if(terminate) return mss;
    synchronized(wcmutex) {
      if(wclient == null) {    // we are the first, create it
        wclient = new WaveClientNew();
      
        for(;;) {
          wclient.addServer(host, port);
          if(wclient.listServers().length == 0) {
            prt(tag+" wclient servers not connected.  Try again servers.len="+wclient.listServers().length);
            Util.sleep(1000);
          }
          else break;
        }
        wclient.setTruncateAtTimeGap(false);
        wclient.setMaxRetries(10);
        prta(tag+" wclient="+wclient+" list Servers="+wclient.listServers()[0]);
        wclient.setMaxTimeoutMilliSecs(180000); 
      }
    }
    Timestamp end = new Timestamp(fetch.getStart().getTime()+(long) (fetch.getDuration()*1000.+fetch.getStartMS()));
    String sname=fetch.getSeedname();
    boolean hrunit=false;

    java.util.Date trinetstart = new java.util.Date();
    int npacket=0;
    long begin = System.currentTimeMillis();
    long start = fetch.getStart().getTime();
    trinetstart.setTime(start);
    synchronized(wcmutex) {
      prta(tag+" try Trinet Fetch of "+sname+" "+Util.ascdatetime2(start)+" "+fetch.getDuration()+" from "+host+"/"+port+" "+wclient);
      List packets;
      if(sname.substring(10).trim().equals("")) {
        packets= wclient.getPacketData(sname.substring(0,2).trim(),
              sname.substring(2,7).trim(),sname.substring(7,10), trinetstart, (int) (fetch.getDuration()+1.)); 
      }
      else {
        packets= wclient.getPacketData(sname.substring(0,2).trim(),
              sname.substring(2,7).trim(),sname.substring(7,10), sname.substring(10).trim(), 
              trinetstart, (int) (fetch.getDuration()+1.)); 
      }
      if(packets != null) {
        prta(tag+" return="+packets.size()+" tim="+trinetstart);
        npacket+= packets.size();
        for (Object packet : packets) {
          MiniSeed ms = msp.get((byte[])packet, 0, 512);
          mss.add(ms);
        }
      }
      else {
        prta(tag+" *** Trinet returned null! "+sname+" "+Util.ascdatetime2(start)+" "+fetch.getDuration());
        if(fetch.getStart().getTime()+fetch.getDuration()*1000 < System.currentTimeMillis() - 4*86400000L) {
          prta(tag+" ** Trinet null is  4 days old - set nodata");
          
        }
      }
    }

    prt(tag+" return size mss="+mss.size());
    Collections.sort(mss);
    if(!mss.isEmpty()) {
      if(fetch.getStart().getTime() + fetch.getStartMS() > mss.get(mss.size()-1).getTimeInMillis()) {
        prta(tag+" ** Trinet fetch start is after return end.  Set nodata.");
        for(int i=mss.size()-1; i>=0; i--) msp.free(mss.get(i));
        mss.clear();
        return null;
      }
      if(fetch.getStart().getTime()+fetch.getDuration()*1000.+fetch.getStartMS() < mss.get(0).getTimeInMillis()) {
        prta(tag+" ** Trinet fetch end is before return begging.  Set nodata.");
        for(int i=mss.size()-1; i>=0; i--) msp.free(mss.get(i));
        mss.clear();
        return null;
      }
    }
    if(mss.isEmpty()) return null; 
    return mss;
  }
  
 /**
  * @param args the command line arguments
  */
  public static void main(String[] args) {

    Util.setModeGMT();
    Util.init("edge.prop");
    boolean single=false;
    if(args.length == 0) args = "-s CIISA--BHZ -b 2015/08/10 12:00 -d 300 -1 -t T0 -h 131.215.68.152 -p 6509".split("\\s");
    // set the single mode flag
    for (String arg : args) {
      if (arg.equals("-1")) {
        single = true;
      }
    }
    
    // Try to start up a new requestor
    try {
      TrinetRequest trinetrequest = new TrinetRequest(args);
      if(single) {
        ArrayList<MiniSeed> mss = trinetrequest.getData(trinetrequest.getFetchList().get(0));
        if(mss == null) Util.prt("NO DATA was returned for fetch="+trinetrequest.getFetchList().get(0));
        else if(mss.isEmpty()) Util.prt("Empty data return - normally leave fetch open "+trinetrequest.getFetchList().get(0));
        else {
          try (RawDisk rw = new RawDisk("tmp.ms", "rw")) {
            for(int i=0; i<mss.size(); i++) {
              rw.writeBlock(i, mss.get(i).getBuf(), 0, 512);
              Util.prt(mss.get(i).toString());
            }
            rw.setLength(mss.size()*512L);
          }
        }
        System.exit(0);
      }
      else {      // Do fetch from table mode 
        trinetrequest.startit();
        while(trinetrequest.isAlive()) Util.sleep(1000);
      }
    }
    catch(IOException e) {
      Util.prt("IOError="+e);
      e.printStackTrace();
    }
    catch(SQLException e) {
      Util.SQLErrorPrint(e,"Impossible in test mode");
    }


  }
}
