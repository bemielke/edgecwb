/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */


/*
 * RTRequest.java - This seems to have been moved to RTPDArchiveRequest.  
 *
 * Created on February 28, 2008, 12:54 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package gov.usgs.anss.fetcher;
//import java.net.*;
//import java.nio.*;
import java.io.*;
import java.sql.*;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.ArrayList;
import java.net.*;
import java.nio.*;
import java.text.DecimalFormat;
import gov.usgs.anss.util.Util;
import gov.usgs.anss.db.DBConnectionThread;
import gov.usgs.anss.seed.*;
import gov.usgs.anss.edge.RawDisk;
import gov.usgs.anss.edge.RawToMiniSeed;
import gov.usgs.anss.edge.IllegalSeednameException;
import gov.usgs.anss.edge.FetchList;
import gov.usgs.anss.guidb.Reftek;
import gov.usgs.anss.edgethread.EdgeThread;
import gov.usgs.anss.util.Subprocess;
import gov.usgs.anss.edge.MiniSeedOutputHandler;

/**
 *
 * @author davidketchum
 */
public class ReftekRequest extends Fetcher implements MiniSeedOutputHandler {
  byte [] b;
  ByteBuffer bb;
  private final ArrayList<MiniSeed> mss = new ArrayList<>(1000);
  RTReaderTimeout timeout;
  private boolean dbg;
  private String status;
  private boolean running;
  private int totalBytes;
  private boolean inReader;
  private Reftek reftek;
  private final DecimalFormat df2 = new DecimalFormat("00");
  private final DecimalFormat df3 = new DecimalFormat("000");


  private Socket s;
  // These implement the MiniSeedOutputHander interface
  @Override
  public void close() {}
  @Override
  public void closeCleanup() {}
  @Override
  public void putbuf(byte [] b, int size) {
    try {
      MiniSeed ms = new MiniSeed(b, 0, size);
      mss.add(ms);
    }
    catch(IllegalSeednameException e) {
      prta("RTReq: "+e);
    }
  }
  //private boolean terminate;
  public String toStringDetail() {return "RTReq"+host+"/"+port+"  run="+running+" "+status;}
  public String getStatusString() {return "LOG: "+status.replaceAll("\r","\nLOG:")+"\n";}
  /** return the array list of MiniSeed blocks returned as a result
   *
   *
   * @return  The array list of miniseed.  Some of the indices may contain null where duplicates were eliminated)
   */
  public ArrayList<MiniSeed> getResults() {return mss;}
  /** shutdown this connection to the server */
  @Override
  public void terminate() {prta("Reftek terminate called."); terminate=true; interrupt();if(s != null) if(!s.isClosed()) try{s.close();} catch(IOException e) {}}
  public ArrayList<MiniSeed> waitFor() {
    while(running) {
      Util.sleep(100);
    }
    prt("returned mss ="+mss);
    return mss;
  }
  public ReftekRequest(String [] args) throws UnknownHostException, IOException, SQLException  {
    super(args);
    for (String arg : args) {
      if (arg.equals("-dbg")) {
        dbg=true;
      }
    }
    b = new byte[1024];
    bb = ByteBuffer.wrap(b);
    //if(dbg)
    //if(!singleRequest) {
      ResultSet rs = dbconnedge.executeQuery("SELECT * FROM anss.reftek WHERE station regexp '"+
              (getSeedname()+"       ").substring(2,7).trim()+"'");
      if(rs.next()) {
        reftek = new Reftek(rs);
      }
      else throw new UnknownHostException("ReftekRequest could not find station for "+getSeedname());
      prta("Open socket to "+host+"/"+port+" dbg="+dbg);
      //timeout = new RTReaderTimeout(this);
    //}
  }

  public boolean isRunning() {return running;}

  @Override
  public ArrayList<MiniSeed> getData(FetchList fetch) {
    b = new byte[4096];
    running=true;
    mss.clear();
    GregorianCalendar g = new GregorianCalendar();
    g.setTimeInMillis(fetch.getStart().getTime()+fetch.getStartMS());
    boolean nodata=true;
    //MiniSeed ms = null;
    char dt1,dt2;
    byte experiment;
    int year;
    short unit;
    byte[] timebuf;
    short byteCount;
    short sequence;
    String stime;
    int iday;
    int sec;
    int micros;
    int activity;
    int ioClock;
    int quality;
    int tQual;
    String file="";
    double dur = fetch.getDuration();
    int inbytes=0;
    long startms = System.currentTimeMillis();
    boolean eof=false;
    seedname = fetch.getSeedname();
    StringBuilder seednameSB = new StringBuilder(12);
    seednameSB.append(seedname);
    Util.rightPad(seednameSB,12);
    int stream = -1;
    String chan;
    String chans="   ";
    String comps="   ";
    double rate=0.;
    String time = g.get(Calendar.YEAR)+":"+df3.format(g.get(Calendar.DAY_OF_YEAR))+":"+
            df2.format(g.get(Calendar.HOUR_OF_DAY))+":"+
            df2.format(g.get(Calendar.MINUTE))+":"+df2.format(g.get(Calendar.SECOND))+"."+df3.format(fetch.getStartMS());
    String band = seedname.substring(7,9);
    if(band.equals(reftek.getBand1().substring(0,2))) {
      stream = reftek.getStream();
      chans = reftek.getChans1();
      comps = reftek.getComps1();
      rate = reftek.getRate1();
    }
    else if(band.equals(reftek.getBand2().substring(0,2))) {
      stream = reftek.getStream2();
      chans = reftek.getChans1();
      comps = reftek.getComps2();
      rate = reftek.getRate3();
    }
    else if(band.equals(reftek.getBand3().substring(0,2))) {
      stream = reftek.getStream3();
      chans = reftek.getChans3();
      comps = reftek.getComps3();
      rate = reftek.getRate3();
    }


    int pos = comps.indexOf(seedname.substring(9,10));
    if(pos < 0) {
      prta("RTReq: **** the direction code for "+seedname+" is not in the configuration.  Skipping.");
      return null;
    }
    chan = chans.substring(pos, pos+1);

    String str = "arcfetch "+System.getProperty("user.home")+Util.FS+"archive "+reftek.getSerial().toUpperCase()+","+stream+","+chan+","+
            time+",+"+dur;
    inReader=true;
    try {
      Subprocess sp = new Subprocess(str);
      sp.waitFor();
      String output = sp.getOutput();
      String error = sp.getErrorOutput();
      String [] lines = output.split("\\n");
      for (String line : lines) {
        if (line.contains("FILE FETCHED")) {
          file = line.substring(line.indexOf("FILE FETCHED") + 13).trim();
        }
      }
      if(file.equals("")) {
        prta("RTReq: Failed to get filename from output="+output);
        return null;
      }
    }
    catch(IOException e) {
      prta("IOError trying to do the arcfetch. e="+e);
      e.printStackTrace();
    }
    catch(InterruptedException e) {
      prta("RTReq: *** go interrupted exception doing arcfetch");
      return null;
    }
    try {
      RawToMiniSeed rtms = null;
      RawDisk rw = new RawDisk(file, "r");
      int [] data = new int[4096];
      int [] decode = new int[4096];
      byte [] decomp = new byte[960];
      timebuf = new byte[6];
      for(int iblk=0; iblk<rw.length()/512; iblk = iblk+2) {
        totalBytes += 1024;
        bb.position(0);
        dt1 = (char) bb.get();
        dt2 = (char) bb.get();
        int len = rw.readBlock(b, iblk, 1024);
        experiment = bb.get();
        year = bb.get();
        year = ((year & 0xf0) >> 4)*10 + (year & 0xf);
        year += 2000;
        unit = bb.getShort();
        bb.get(timebuf);
        byteCount = Reftek.BCDtoInt(bb.getShort());    // BCD byte count
        sequence = Reftek.BCDtoInt(bb.getShort());      // BCD packet Sequence
        stime = Reftek.timeToString(timebuf);
        iday = Integer.parseInt(stime.substring(0,3));
        sec = Integer.parseInt(stime.substring(3,5))*3600+Integer.parseInt(stime.substring(5,7))*60+Integer.parseInt(stime.substring(7,9));
        micros = Integer.parseInt(stime.substring(9,12))*1000;
        if(dbg) prta(dt1+dt2+" exp="+experiment+" yr="+year+":"+iday+
                " unit="+Util.toHex(unit)+" time="+stime+" "+sec+" "+micros+" sq="+sequence);
        if(rtms == null) {
          rtms = new RawToMiniSeed(seednameSB, rate, 7, year, iday, sec, micros, 800000, null);
          rtms.setOutputHandler(this);
        }
        if(dt1 == 'D' && dt2 == 'T') {
          short evt = Reftek.BCDtoInt(bb.getShort());
          stream = bb.get();
          stream = Reftek.BCDtoInt(stream);
          int ichan = bb.get();
          ichan = Reftek.BCDtoInt(ichan);
          int nsamp = Reftek.BCDtoInt(bb.getShort());
          byte flags = bb.get();
          int format = bb.get();
          format &= 0xff;
          if(dbg) prta("DT process stream="+stream+" ichan="+ichan+" chan="+chan+" "+seedname+" nsamp="+nsamp+" flags="+Util.toHex(flags)+" form="+Util.toHex(format));
          if(format == 0x16) {  // 16 bit not compressed
            for (int i=0; i<nsamp; i++) {
              decode[i] = bb.getShort();
            }
            data = decode;
          }
          else if(format == 0x32) { // 32 bit not compressed
            for(int i=0; i<nsamp; i++) {
              decode[i] = bb.getInt();
            }
            data = decode;
          }
          else if(format == 0x33) { // 32 bit not compressed with overscale
            for(int i=0; i<nsamp; i++) {
              decode[i] = bb.getInt();
            }
            data = decode;
          }
          else if(format == 0xC0) { // Binary compressed data STEIMI
            try {
              System.arraycopy(b, 64, decomp, 0, 960);
              data = Steim1.decode(decomp, nsamp, false);
              if(data.length != nsamp) prta("Steim1 decompressed to different length! data.len="+data.length+" nsamp="+nsamp);
            }
            catch(SteimException e) {
              prta("RTReq: **** Steim error e="+e);
              continue;
            }
          }
          else if(format == 0xC1) { // Binary compressed data with overscale flag Steim1
            prta(" *** Got Compressed overscale - not handled");
          }
          else if(format == 0xC2) {  // Binary highly compressed data Steim2
            prta(" *** Got Steim 2 compressed - not handled");
          }
          else if(format == 0xC3) { // Binaty Highly Compressed data with overscale flag Steim2
            prta(" *** Got Steim 2 compressed with overscale - not handled");
          }
          else {
            prta(tag+" ** DT packet is encoded in undefined manner="+Util.toHex(format));
          }
          activity=0;
          ioClock=0;
          quality=0;
          tQual = 0;
          if((flags & 0x80) != 0) activity |= 1;
          if((flags & 0x40) != 0) quality |= 2;
          if((flags & 1) != 0) activity |= 4;
          if((flags & 2) != 0) activity |= 8;
          if(dbg) {
            int min=2147000000;
            int max = -2147000000;
            for(int i=0; i<nsamp; i++) {
              if(min > data[i]) min = data[i];
              if(max < data[i]) max = data [i];
            }
            prta("  "+ seedname+" "+Util.toHex( unit).substring(2)+" "+
                    stream+"-"+chan+" SteimI min="+min+" max="+max+" act="+
                    Util.toHex(activity)+" quality="+Util.toHex(quality)+" ioC="+Util.toHex(ioClock)+" tQ="+tQual);
          }

          if(seedname != null && rate > 0.9) {
            RawToMiniSeed.addTimeseries(data, nsamp, seednameSB, year, iday, sec, micros,
                    rate, activity, ioClock, quality, tQual, null);
          }
        }
        else {
          prta("RTReq: Got a non DT packet ="+dt1+dt2);
        }
      }   // loop through all data blocks
    }
    catch(IOException e) {
      prta("IOError trying process arcfetch output file e="+e);
      e.printStackTrace();
    }
    RawToMiniSeed.forceoutAll(seednameSB);    // Close out all Miniseed
    inReader = false;
    if(mss.isEmpty() && nodata) {
      prta("RT request nodata exiting - "+status+" inbytes="+inbytes);
      running=false;
      return null;
    }
    else {
      // THe request might have been broken into several pieces, if it was some blocks my be duplicated, drop them
      for(int i=1; i<mss.size(); i++)
        if(mss.get(i-1) != null)
          if(mss.get(i).isDuplicate(mss.get(i-1))) mss.set(i, null);    // convert it to a null block
      
      // Discard an MiniSeed not in the interval
      int nremove=0;
      int ndup=0;
      long end = (long) (fetch.getStart().getTime()+ fetch.getStartMS() + fetch.getDuration()*1000.);
      for(int i=mss.size()-1; i>=0; i--) {
        if(mss.get(i) == null) {mss.remove(i); ndup++; continue;}
        if(mss.get(i).getNextExpectedTimeInMillis() < fetch.getStart().getTime()+ fetch.getStartMS()) {
          prta("** MS block before begin time - remove it. "+mss.get(i).toStringBuilder(null));
          mss.remove(i);
          nremove++;
        }
        else if(mss.get(i).getTimeInMillis() >= end) {
          prta("** MS block after end of request time - remove it. "+mss.get(i).toStringBuilder(null));
          mss.remove(i);
          nremove++;
        }
      }
      prta("RT request exiting - "+status+" mss.size="+mss.size()+" inbytes="+inbytes+
              " eof="+eof+" term="+terminate+" ndup="+ndup+" nremove="+nremove);
  }
    running=false;
    return mss;
  }

  /** this is for doing things at the start of the run which might take too long in the constructor.
   * For Refteks I cannot think of a thing!
   */
  @Override
  public void startup() {}

  class RTReaderTimeout extends Thread {
    ReftekRequest thr;
    int lastBytes;
    RTReaderTimeout(ReftekRequest a) {
      thr=a;
      start();
    }
    @Override
    public void run() {
      while(thr.isAlive()) {
        // We have to be in the reader to activate this code.
        if(inReader) {
          lastBytes = totalBytes;     // Save current total bytes
          int loop=0;                 // counter of seconds
          while(lastBytes == totalBytes && inReader) {    // If no progress is being made and have not left reader
            try{sleep(1000);} catch(InterruptedException e) {}
            loop++;                   // count up the seconds
            if(loop > 1800) {
              prta(" *** timeout on readFully.  Force socket closed "+seedname);
              try{
                if(s != null)
                  if(!s.isClosed())s.close();
              }
              catch(IOException e) {
                prta("  *** timeout "+seedname+" got IO exception trying to close socket e="+e);
              }
              break;
            }    // if over second limit
          }
        }
        try{sleep(10000);} catch(InterruptedException e) {}
      }
      prta("RTReaderTimeout is exiting "+seedname);
    }
  }

  /** test main routine *.
   * @param args The command line arguments
   */
  public static void main(String [] args) {
    Util.init("edge.prop");
    //DBConnectionThread dbconnedge;
    DBConnectionThread anss;
    Statement stmt = null;
    Statement stmt2 = null;
    String fetchServer=null;
    Util.setModeGMT();
    if(fetchServer == null) fetchServer = Util.getProperty("DBServer");
    if(fetchServer == null) fetchServer = Util.getProperty("MySQLServer");
    if(fetchServer.equals("")) fetchServer = Util.getProperty("MySQLServer");
    EdgeThread.staticprt("Test ReftekRequest mysql="+fetchServer);
    try {
      anss = DBConnectionThread.getThread("anss");
      if(anss == null) {
        anss = new DBConnectionThread(fetchServer, "readonly","anss", false, false, "anss", EdgeThread.getStaticPrintStream());
        EdgeThread.addStaticLog(anss);
      }
      anss.waitForConnection();
      dbconnedge = DBConnectionThread.getThread("edge");
      if(dbconnedge == null) {
        dbconnedge = new DBConnectionThread(fetchServer, "update", "edge",  true, (Util.getProperty("SSLEnabled") != null),"edge", EdgeThread.getStaticPrintStream());
        EdgeThread.addStaticLog(dbconnedge);
        if(!dbconnedge.waitForConnection())
          if(!dbconnedge.waitForConnection())
            if(!dbconnedge.waitForConnection()) {
              EdgeThread.staticprt("Could not connect to database "+fetchServer);
              Util.exit(1);
            }
      }
      stmt = dbconnedge.getConnection().createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE);
      stmt2 = dbconnedge.getConnection().createStatement();
    }
    catch(SQLException e) {
      EdgeThread.staticprt("Could not create updateable statement.  must not be Golden!");
      return;
    }
    catch(InstantiationException e) {
      EdgeThread.staticprt("Impossible Instantiation exception e="+e);
      Util.exit(0);
    }
    String status="open";
    Timestamp start = new Timestamp(20000);
    GregorianCalendar g = new GregorianCalendar();
    g.set(2009, 9,3,12, 0, 0);
    start.setTime(g.getTimeInMillis());
    FetchList fetch = new FetchList("XXTEST1BHZ  ", start, 0, 300,"RT", status);
    String [] arg = new String[10];
    arg[0]="-1";
    arg[1]="-s";
    arg[2]="XXTEST1BHZ  ";
    arg[3]="-e";
    arg[4]="2009-10-03 12:00:00";
    arg[5]="-d";
    arg[6]="300";
    arg[7]="-c";
    arg[8]="ReftekRequest";
    arg[9]="-dbg";
    try {
      ReftekRequest ref = new ReftekRequest(arg);
    }
    catch(UnknownHostException e) {
      EdgeThread.staticprta("Unknown host?="+e);
    }
    catch(IOException e) {
      EdgeThread.staticprta("IOError?="+e);
    }
    catch(SQLException e) {
      EdgeThread.staticprta("SQLException e="+e);
    }
  }
}

