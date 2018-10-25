/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */


package gov.usgs.anss.fetcher;
//import java.net.*;
//import java.nio.*;
//import gov.usgs.anss.db.DBConnectionThread;
import gov.usgs.anss.edge.FetchList;
import gov.usgs.anss.edge.IllegalSeednameException;
import gov.usgs.anss.edge.MiniSeedOutputHandler;
import gov.usgs.anss.edge.RawToMiniSeed;
import gov.usgs.anss.edgemom.ReftekClient;
import gov.usgs.anss.edgemom.Subprocess;
//import gov.usgs.anss.edgethread.EdgeThread;
import gov.usgs.anss.seed.MiniSeed;
import gov.usgs.anss.util.Util;
import java.io.File;
import java.io.IOException;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.sql.ResultSet;
import java.sql.SQLException;
//import java.sql.Statement;
//import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
/** This class provides fetcher functionality against a Reftek RTPD maintained archive.
 * It works by converting the Fetch requests to arcfetch command formation, running the
 * arcfetch as a subprocess to create a file in Reftek format, and then using the ReftekClient
 * class to convert that file to MiniSeed.  The miniSEED is returned to the Fetcher class
 * for disposal like any other fetch.
 *
 * <PRE>
 * switch     args       description
 * -archive path1:path2  Look for data in all of the paths given
 * -dbg                  More verbose output
 * </pre>
 * @author davidketchum
 */
public class RTPDArchiveRequest extends Fetcher implements MiniSeedOutputHandler {
  byte [] b;
  ByteBuffer buf;
  private final ArrayList<MiniSeed> mss = new ArrayList<>(1000);
  private String [] archives;
  private boolean dbg;
  private String line;
  private String status;
  private boolean running;
  //private FetchList fetch;
  private int totalBytes;
  private boolean inReader;
  private boolean inThrottle;
  private boolean inAvail;
  private static ReftekClient reftekClient;

  private Socket s;
  //private boolean terminate;
  @Override
  public void closeCleanup() {}
  public String toStringDetail() {return "GNSReq"+host+"/"+port+"  run="+running+" "+status;}
  public String getStatusString() {return "LOG: "+status.replaceAll("\r","\nLOG:")+"\n";}
  /** return the array list of MiniSeed blocks returned as a result
   *
   *
   * @return  The array list of miniseed.  Some of the indices may contain null where duplicates were eliminated)
   */
  public ArrayList<MiniSeed> getResults() {return mss;}
  /** shutdown this connection to the server */
  @Override
  public void terminate() {prta("RTPDArc terminate called."); terminate=true; interrupt();if(s != null) if(!s.isClosed()) try{s.close();} catch(IOException e) {}}
  public ArrayList<MiniSeed> waitFor() {
    while(running) {
      Util.sleep(100);
    }
    prt("returned mss ="+mss);
    return mss;
  }
  public RTPDArchiveRequest(String [] args) throws UnknownHostException, IOException, SQLException  {
    super(args);
    String argline="";
    for (int i=0; i<args.length; i++) {
      argline += args[i];
      switch (args[i]) {
        case "-dbg":
          dbg=true;
          break;
        case "-archive":
          archives = args[i+1].split(":");
          break;
      }
    }
    if(archives == null) {
      archives = new String[1];
      archives[0] = "archive";
    }
    //if(dbg)
    if(!singleRequest) {
      if(reftekClient == null) {
        reftekClient = new ReftekClient(dbconnedge.toString(), argline+" -refdbg", tag);
      }
    }
    prta("Archives: "+archives[0]+(archives.length > 1?" 1="+archives[1]:""));
  }

  public boolean isRunning() {return running;}

  @Override
  public ArrayList<MiniSeed> getData(FetchList fetch) throws UnknownHostException {
    try {sleep(10000);} catch(InterruptedException e) {}
    b = new byte[4096];
    running=true;
    mss.clear();
    GregorianCalendar g = new GregorianCalendar();
    g.setTimeInMillis(fetch.getStart().getTime()+fetch.getStartMS());
    int stream=-1;
    int chan=-1;
    String serial="";
    String comps;
    String chans;
    File dir = new File(".");
    File [] files = dir.listFiles();
    for(File file : files) {
      if(file.isDirectory() && file.getAbsolutePath().contains("archive")) {
        
      }
    }
    
    String stub = "bash --login -c 'arcfetch -C+ -L1 -P+ ";
    String outputFile;
    double rate=0.;
    synchronized(edgedbMutex) {   // note: we do everything under the mutex because the RTMS is used statically so only one at a time
      try {
        ResultSet rs = dbconnedge.executeQuery("SELECT * FROM anss.reftek WHERE network='"+fetch.getSeedname().substring(0,2)+
                "' AND station regexp '"+fetch.getSeedname().substring(2,7).replaceAll(":","").trim()+"'");
        int isel=-1;
        int igroup=-1;
        while(rs.next()) {
          serial = rs.getString("serial");
          String bandCode = fetch.getSeedname().substring(7,9);
          String locationCode = fetch.getSeedname().substring(10).trim();
          String band = "";
          String location;
          for(int i=1; i<7; i++) {
            band = rs.getString("band"+i)+"    ";
            location =rs.getString("location"+i)+"     ";
            if( band.substring(0,2).equals(bandCode) &&  location.substring(0,2).trim().equals(locationCode)) { 
              isel=i; 
              igroup=0;
              prta("Match found band="+bandCode+" to "+band+" isel="+isel); 
              break;
            }                  
            if(band.substring(2,4).equals(bandCode) && location.substring(2,4).trim().equals(locationCode)) {
              isel=i; 
              igroup=2;
              prta("Match found band="+bandCode+" to "+band+" isel="+isel); 
              break;
            }
          }
          if(isel >=0) {
            stream = rs.getInt("stream"+isel);
            comps = rs.getString("components"+isel);
            chans = rs.getString("chans"+isel);
            rate = rs.getDouble("rate"+isel);
            prta("Found band match isel="+isel+" str="+stream+" comps="+comps+" chans="+chans+
                    " rt="+rate+" band="+band.substring(0,2)+" bandcode="+bandCode);
            for(int i=(band.substring(0,2).equals(bandCode)?0:3); i<comps.length(); i++)
              if(comps.charAt(i) == fetch.getSeedname().charAt(9)) {
                chan = chans.charAt(i) - '0'; 
                prta("Found full match chan="+chan+" process the request");
                break;
              }  
          }
          if(isel >= 0 && chan >= 0) break;     // found the match
        }
        if(isel == -1 || rate==0. || stream == -1) 
          throw new UnknownHostException("RTPDArchiveRequest could not find station in anss.reftek for "+
                  getSeedname()+" fetching "+fetch.getSeedname()+" isel="+isel+" igroup="+igroup+
                  " bcode="+fetch.getSeedname().substring(7,9)+"|");
        
      }
      catch(SQLException e) {
        prta("SQLException looking for reftek for "+getSeedname());
        throw new UnknownHostException("RTPDArchiveRequest SQL could not find host for "+getSeedname()+" fetching "+fetch.getSeedname());
      }

      try {
        //prta("start line="+line2);
        for(int i=0; i<2; i++) {
          for(String archive : archives ) {
            String line2=stub;
            outputFile = fetch.getSeedname().replaceAll(" ","_")+"_"+g.get(Calendar.YEAR)+"_"+Util.df3(g.get(Calendar.DAY_OF_YEAR))+
                    "_"+Util.df2(g.get(Calendar.HOUR_OF_DAY))+Util.df2(g.get(Calendar.MINUTE))+Util.df2(g.get(Calendar.SECOND))+".rt";
            line2 += archive+" "+serial+","+(stream+(i*4))+","+chan+","+g.get(Calendar.YEAR)+":"+Util.df3(g.get(Calendar.DAY_OF_YEAR))+
                    ":"+Util.asctime2(g)+",+"+Util.df23(fetch.getDuration()+1./rate)+" "+outputFile+"'";
            prta("Start line="+line2);
            Subprocess sp = new Subprocess(line2, "ARCF");
            int stat = sp.waitFor();
            File f = new File(outputFile);
            String output = sp.getConsoleOutput().toString();
            // If it does not contain anything, go on.
            prta("exist="+f.exists()+" size="+f.length()+" status="+stat+" Output="+output);
            if(output.contains("Can't find data in range specified") || f.length() == 0 || !f.exists()) {
              prta("Data not in "+archive);
              if(f.exists()) f.delete();
              continue;
            }
            reftekClient.setPrint(log);
            reftekClient.readConfigDB(nowday, "0x"+serial,"reftek");
            RawToMiniSeed.setStaticOutputHandler(this);
            reftekClient.processFile(outputFile);
            StringBuilder tmp = new StringBuilder(12);
            tmp.append((fetch.getSeedname()+"    ").substring(0,12));   //Hack: not ready to support all StringBUilders here
            RawToMiniSeed.forceout(tmp);
            RawToMiniSeed.forceStale(-100000);
            if(f.exists()) f.delete();
            prta("Processed mss.size()="+mss.size());
          }
        }
        if(mss.size() == 1) {
          if(mss.get(0).getNsamp() <= 1) {
            prta("** only returned one sample, mark as no data.");
            mss.remove(0);      // Make it the same a no data
          }
          if(mss.isEmpty()) {
            prta(" No data found in file.  Set nodata");
            //return null;
          }// We processed it, but must have just been EH records.
        }
        for(int i=mss.size()-1; i>=0; i--) {
          if(mss.get(i).getNextExpectedTimeInMillis() < fetch.getStartMS()) {
            prta("MS block before begin time - remove it. "+mss.get(i).toStringBuilder(null));
            mss.remove(i);
          }
          else if(mss.get(i).getTimeInMillis() > fetch.getStartMS() + fetch.getDuration()*1000) {
            prta("MS block after end of request time - remove it. "+mss.get(i).toStringBuilder(null));
            mss.remove(i);
          }
        }

      }
      catch(InterruptedException e) {
        prta("Interrupted! error="+e);
      }
      catch(IOException e ) {
        prta("Got IOException getting arcfetch file!"+e);
        return mss;
      }
    }

    running=false;
    prta("Final mss.size()="+mss.size());
    return mss.isEmpty()? null: mss;
  }
  @Override
  public void putbuf(byte [] buf, int len) {
    try {
      MiniSeed ms = new MiniSeed(buf, 0, len);
      mss.add(ms);
    }
    catch(IllegalSeednameException e) {
      prta("Illegal seedname exceptions should not happen! e="+e);
    }

  }
  /** required by MiniSeedOutputHandler, but does not do anything here.
   *
   */
  @Override
  public void close() {

  }
  /** this is for doing things at the start of the run which might take too long in the constructor.
   * For Reftek archfetch requests,  I cannot think of a thing!
   */
  @Override
  public void startup() {}

}

