/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */

/*
 * GSNRequest.java
 *
 * Created on February 28, 2008, 12:54 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */
 
package gov.usgs.anss.msread;
import java.net.*;
import java.nio.*; 
import java.io.*;
import java.sql.*;
import java.util.GregorianCalendar;
import java.util.ArrayList;
import java.util.TreeMap;
import gov.usgs.anss.util.Util;  
import gov.usgs.anss.util.FUtil;
import gov.usgs.anss.mysql.MySQLConnectionThreadOld;
import gov.usgs.anss.seed.*;
import gov.usgs.anss.edge.IllegalSeednameException;
import gov.usgs.anss.edge.FetchList;
import gov.usgs.anss.edge.RawDisk;

/**
 *
 * @author davidketchum
 */
public class GSNRequest extends Thread {
  byte [] b;
  ByteBuffer buf;
  private final  ArrayList<MiniSeed> mss = new ArrayList<MiniSeed>(1000);
  private final String host;
  private final int port;
  private final boolean dbg;
  private String line;
  private final Timestamp start;
  private final double duration;
  private final String seedname;
  private String status;
  private boolean running;
  private int fetchID;
  private FetchList fetch;
  
  private Socket s;
  private boolean terminate;
  @Override
  public String toString() {return "GNSReq"+host+"/"+port+" "+seedname+" start="+start+" dur="+duration+" run="+running+" "+status;}
  public String getStatusString() {return "LOG: "+status.replaceAll("\r","\nLOG:")+"\n";}
  public int getFetchID() {return fetchID;}
  public void setFetchID(int i) {fetchID=i;}
  public Timestamp getStart() {return start;}
  public double getDuration() {return duration;}
  /** return the array list of MiniSeed blocks returned as a result
   * 
   * 
   * @return  The array list of miniseed.  Some of the indices may contain null where duplicates were eliminated)
   */
  public ArrayList<MiniSeed> getResults() {return mss;}
  /** shutdown this connection to the server */
  public void terminate() {terminate=true; interrupt();if(!s.isClosed()) try{s.close();} catch(IOException e) {}}
  public FetchList getFetchList() {return fetch;}
  public ArrayList<MiniSeed> waitFor() {
    while(running) {
      Util.sleep(100);
    }
    Util.prt("returned mss ="+mss);
    return mss;
  }
  /** Creates a new instance of SynSeisClient 
   *@param h The host to connect to
   *@param p The port to connect to 
   * @param seed the seedname of this request
   * @param st The start time of the request
   * @param dur The duration in seconds of the request
   * @param debug If true, turn on debug output
   * @param ID the ID of the fetchlist entry to be l
   *@throws IOException if one occurs setting up the socket
   *@throws UnknownHostException if the host name is bad or does not translate in DNS
   */
  public GSNRequest(String h, int p, String seed, Timestamp st, double dur, boolean debug, int ID) throws UnknownHostException, IOException  {
    seedname=seed;
    host = h;
    fetchID=ID;
    port=p;
    start = st;
    duration = dur;
    b = new byte[512];
    status="";
    dbg=debug;
    //if(dbg) 
      Util.prta("Open socket to "+host+"/"+port+" for "+seedname+" start="+start+" dur="+duration+" dbg="+dbg);
    s = new Socket(host,port);
    s.setReceiveBufferSize(200000);
    s.setSoLinger(false, 0);
    running=true;
    start();
  }
  /** Creates a new instance of SynSeisClient 
   *@param h The host to connect to
   *@param p The port to connect to
   * @param fetchList The fetchlist entry to go get
   * @param debug If true, turn on debug output. 
   *@throws IOException if one occurs setting up the socket
   *@throws UnknownHostException if the host name is bad or does not translate in DNS
   */
  public GSNRequest(String h, int p, FetchList fetchList, boolean debug) throws UnknownHostException, IOException  {
    fetch=fetchList;
    seedname=fetch.getSeedname();
    host = h;
    fetchID=fetch.getID();
    port=p;
    start = fetch.getStart();
    start.setTime(start.getTime()+fetch.getStartMS());
    duration = fetch.getDuration();
    dbg=debug;
    b = new byte[512];
    status="";
    s = new Socket(host,port);
    //if(dbg) 
    Util.prta("Open socket to "+host+"/"+port+" for "+seedname+" start="+start+" dur="+duration+" rcv="+s.getReceiveBufferSize());
    s.setReceiveBufferSize(200000);
    s.setSoLinger(false, 0);
    running=true;
    start();
  }
  public boolean isRunning() {return running;}

  @Override
  public void run() {
    GregorianCalendar g = new GregorianCalendar();
    g.setTimeInMillis(start.getTime());
    //MiniSeed ms = null;
    double dur = duration;
    while(dur > 0.02) {
      if(s.isClosed()) {
        try {
          s = new Socket(host,port);
          //if(dbg) 
          Util.prta("Open socket2 to "+host+"/"+port+" for "+seedname+" start="+start+" dur="+duration+" rcv="+s.getReceiveBufferSize());
          s.setReceiveBufferSize(200000);
          s.setSoLinger(false, 0);
        }
        catch(UnknownHostException e) {
          Util.prta("Got unknown host on query to "+host+"/"+port);
          terminate=true;
          break;
        }
        catch(IOException e) {
           Util.prta("Got IOError opening socket  on query to "+host+"/"+port);
          terminate=true;
          break;   
        }
      }
      line = "DATREQ "+seedname.substring(2,7).trim()+"."+seedname.substring(10,12)+"-"+seedname.substring(7,10)+" "+
        Util.ascdate(g)+" "+Util.asctime(g).substring(0,8)+" "+((int) (Math.min(10801., dur+0.99)))+"\n";
      dur -= Math.min(10800., dur);
      g.setTimeInMillis(g.getTimeInMillis()+(long) (Math.min(10800., dur)*1000.));
      try {
        Util.prt("line="+line);
        s.getOutputStream().write((line+"\n").getBytes());
      }
      catch(IOException e) {
        Util.prta("Got IOEError writing command or reading socket e="+e.toString());
      }         
      terminate=false;
      while(!terminate) {
        try {
          int l = readFully(s, b, 0, 512);
          if(l <= 0) {
            Util.prta("ReadFully returned EOF="+l);
            terminate=true;
            s.close();
            break;     // EOF found
          }
          if(b[15] == 'L' && b[16] == 'O' && b[17] == 'G' && b[13] == 'R' && b[14] == 'Q') {
            status = new String(b, 64, 200).trim();
            if(dbg)
              Util.prt("\n"+status.replaceAll("\r","\nLOG:")+"\n"+" size="+mss.size()+"<EOL>");
            terminate=true;
            s.close();
            break;
          }
          int size=MiniSeed.crackBlockSize(b);
          if(size > 512) {
            if(b.length < size) {
              byte [] tmp = b;
              b = new byte[size];
              System.arraycopy(tmp, 0, b, 0, tmp.length);
            }
            readFully(s, b, 512, size - 512);
          }
          // Build a new SAC time series and fill it with data
          mss.add(new MiniSeed(b));
          if(dbg)
            Util.prta(mss.get(mss.size()-1).toString());

        }
        catch(IllegalSeednameException e) {
          Util.prta("IllegalSeedname at blk="+mss.size()+" e="+e);
        }
        catch(SocketException e) {
          if(e.getMessage().contains("Connection reset")) {
            Util.prta("GSN request : connection reset"); terminate=true;}
          else if(e.getMessage().contains("Broken pipe")) {
            Util.prta("GSN request : broken pipe - exit"); terminate=true;}
          else {Util.prta("Unknown socket problem e="+e); terminate=true;}
        }
        catch(IOException e) {
          Util.prt("IOError trying to read from socket e="+e);
        }
      }   // reads are not terminated by being completed
      // See if we are done
      if(dbg) Util.prta("Bottom of loop. mss.size="+mss.size()+" dbg="+dbg);
        
    }   // end of forever loop
    
    // THe request might have been broken into several pieces, if it was some blocks my be duplicated, dropp them
    for(int i=1; i<mss.size(); i++) if(mss.get(i).isDuplicate(mss.get(i-1))) mss.set(i-1, null);    // convert it to a null block
    if(!s.isClosed() ) try{s.close();} catch(IOException e) {}
    Util.prta("GSN request exiting - "+status+" mss.size="+mss.size());
    running=false;
  }
  /** read fully the number of bytes, or throw exception 
   *@param s The socket to read from
   *@param b The byte buffer to receive the data
   *@param off The offset into the buffer to start the read
   *@param len The length of the read in bytes
   */
  private int readFully(Socket s, byte [] buf, int off, int len) throws IOException {
    int nchar;
    InputStream in = s.getInputStream();
    int l=off;
    while(len > 0) {            // 
      nchar= in.read(buf, l, len);// get nchar
      if(nchar <= 0) {
        Util.prta(len+" read nchar="+nchar+" len="+len+" in.avail="+in.available());
        return 0;
      }     // EOF - close up
      l += nchar;               // update the offset
      len -= nchar;             // reduce the number left to read
    }
    return l;
  }

  public static void main(String [] args) {
    String host = "140.247.18.162";
    TreeMap <String, ArrayList<FetchList>> thrs= new TreeMap<String, ArrayList<FetchList>>();
    boolean dbg=false;
    Util.setModeGMT();
    Util.prt("Start");
    Util.setNoInteractive(true);
    //String host = "192.168.1.102";
    int port=4003;
    Timestamp start = FUtil.stringToTimestamp("2008-10-01 12:00:00");
    Timestamp endtime=null;
    double duration=3600.;
    String seedname="";
    String fetchServer="gacq4";
    String cwbip="gcwb";
    String type="";
    if(args.length <=1) {
      Util.prt("Usage: GSNRequest -s NNSSSSSCCCLL -b yyyy/mm/dd hh:mm:ss -d dddd [-h gsn.stat.ip.adr][-p port (def=4003)]");
      Util.prt("  OR   GSNRequest -db fetch.list.db -t XX [-cwbip ip.adr] [-s seedRE][-b yyyy/mm/dd hh:mm}][-e yyyy/mm/dd hh:mm]"); // Get all the fetchlist list type XX on fetchlist server");
      Util.prt("In the NNSSSSSCCCLL use - for all spaces.  The name must be 12 characters long.");
      Util.prt("For fetchlist mode: ");
      Util.prt("  the name is a regular expression and can be any length.");
      Util.prt("  -e and -s restrict the start time of the gap to between these two times.");
      Util.prt("  The IP address and port of the request is obtained from the GSNStation table so -h and -p are ignored");
      Util.prt("For non-fetchlist mode :");
      Util.prt("    A filename based on the NNSSSSSCCCLL (spaces become _) with .ms extension will be created");
      Util.prt("The default host is "+host+" port="+port+" seedname="+seedname+" duration="+duration);
    }
    for(int i=0; i<args.length; i++) {
      if(args[i].equals("-h")) {host = args[i+1]; i++;}
      else if(args[i].equals("-dbg")) dbg=true;
      else if(args[i].equals("-p")) {port = Integer.parseInt(args[i+1]); i++;}
      else if(args[i].equals("-cwbip")) {cwbip=args[i+1]; i++;}
      else if(args[i].equals("-b")) {
        String s = args[i+1].replaceAll("/","-")+" "+args[i+2];
        start = FUtil.stringToTimestamp(s);
        i=i+2;
      }
      else if(args[i].equals("-e")) {
        String s = args[i+1].replaceAll("/","-")+" "+args[i+2];
        endtime = FUtil.stringToTimestamp(s);
        i=i+2;
      }      else if(args[i].equals("-d")) {duration = Double.parseDouble(args[i+1]); i++;}
      else if(args[i].equals("-s")) {
        seedname= (args[i+1]+"       ").substring(0,12).replaceAll("-"," ");i++;}
      else if(args[i].equals("-db")) {fetchServer=args[i+1];i++;}
      else if(args[i].equals("-t")) {type = args[i+1]; i++;}
      else if(args[i].equals("-gsn")) ;
    }
    if(type.equals("")) {
    // Make a client connection to the server
      try {
        GSNRequest gsn = new GSNRequest(host, port, seedname, start, duration, true, 0);
        ArrayList<MiniSeed> mss = gsn.waitFor();
        RawDisk rw = new RawDisk(seedname.trim().replaceAll(" ","_")+".ms","rw");
        rw.position(0);
        rw.setLength(0L);
        int iblk=0;
        for (MiniSeed ms : mss) {
          if (ms != null) {
            Util.prt(ms.toString());
            rw.writeBlock(iblk++, ms.getBuf(), 0, 512);
          }
        }
        rw.close();
        Util.prta("\n\nDone. got "+mss.size()+" blocks status="+gsn.getStatusString());
      }
      catch(UnknownHostException e) {
        Util.prta("host not found="+host);
      }
      catch(IOException e) {
        Util.SocketIOErrorPrint(e,"Reading data");
      }
    }
    else {      // This is type driven, read the database
      int nfill=0;
      Util.prt("Start GSNRequest db="+fetchServer+" cwbid="+cwbip+" type="+type);
      try {
        // Build up the thrs list one per station (with many requests per list)
        MySQLConnectionThreadOld mysql = new MySQLConnectionThreadOld(fetchServer, "edge","vdl","nop240",true, true, "edge");
        MySQLConnectionThreadOld.waitForConnection("edge");
        //DEBUG: limit to HRV
        String s = "SELECT * FROM fetcher.fetchlist where type='"+type+
                 "' AND status='open'"+
               " AND start>='"+start.toString().substring(0,16)+"'";
        if(endtime != null) s += " AND start<'"+endtime.toString().substring(0,16)+"' ";
        if(!seedname.equals("")) s += " AND seedname regexp '"+seedname.trim()+"' ";
        s += " ORDER BY start desc,seedname";
        Util.prt("Select : "+s);
        ResultSet rs = mysql.executeQuery(s);
        while(rs.next()) {
          Timestamp st = rs.getTimestamp("start");
          st.setTime(st.getTime()+rs.getShort("start_ms"));
          FetchList req =  new FetchList(rs);
          ArrayList<FetchList> array = thrs.get(req.getSeedname().substring(0,7));
          if(array == null) {
            array = new ArrayList<FetchList>(10);
            thrs.put(req.getSeedname().substring(0,7), array);
          }
          array.add(req);
        }
        
        // Now start one thread for each
        String [] keys = new String[thrs.size()];
        keys = thrs.keySet().toArray(keys);
        GSNRequest [] threads = new GSNRequest[keys.length];
        boolean completed=false;
        while(!completed) {
          for(int i=0; i<keys.length; i++) {
            completed=true;
            if(keys[i] != null) {
              completed=false;
              if(threads[i] == null) {      // need to start a thread for this station
                ArrayList<FetchList> fetches = thrs.get(keys[i]);
                if(fetches.isEmpty()) {
                  Util.prt("No fetches found for "+keys[i]+" skip."); 
                  keys[i]=null; 
                  continue;
                }
                rs = mysql.executeQuery("SELECT * FROM gsnstation WHERE gsnstation='"+keys[i]+"'");
                if(rs.next()) {
                  try {
                    threads[i] = new GSNRequest(rs.getString("requestIP"),rs.getInt("requestport"),fetches.get(0), dbg);
                    fetches.remove(0);          // THread is started, do not need the fetch any more
                    if(fetches.size() <= 0) {Util.prt("All fetches for "+keys[i]+" have been processed"); keys[i] = null;}       // no more fetchs for this station
                  }
                  catch(UnknownHostException e) {
                    Util.prta("Could not start a GSNRequest.  Unknown host="+rs.getString("requestip"));
                  }
                  catch(IOException e) {
                    Util.prta("Could not start a GSNRequest due to IOException.  host="+
                            rs.getString("requestip")+"/"+rs.getInt("requestport")+" fetch="+fetches.get(0)+" e="+e);
                  }
                }
                else {
                  Util.prt("No gsnstation record for "+keys[i]+" stop all fetches");
                  keys[i]=null;
                  threads[i] = null;
                }
              }
              else {          // There is a thread for this station, see if it is done
                if(!threads[i].isRunning()) {   // It is not done
                  ArrayList<MiniSeed> mss = threads[i].getResults();
                  ArrayList<Run> runs = new ArrayList<Run>(1000);
                  Util.prt(keys[i]+" done processing status="+threads[i].getStatusString()+" return size="+mss.size()+" fetchid="+threads[i].getFetchID());
                  if(threads[i].getStatusString().contains("No data was available")) {
                    if(threads[i].getFetchID() > 0) {
                      int rows =mysql.executeUpdate("UPDATE fetcher.fetchlist set status='nodata' where ID="+threads[i].getFetchID());
                      Util.prt(keys[i]+" no data - set id="+threads[i].getFetchID()+" to nodata="+rows);
                    } else Util.prt(keys[i]+" no data but no fetchID to update.");
                    threads[i]=null;
                  }
                  else {
                    double rate=0;
                    if(mss.size() > 0) {          // was anything returned
                      try {
                        RawDisk rw = new RawDisk("tmp.ms","rw");
                        rw.setLength(0L);
                        int iblk=0;
                        for(int j=0; j<mss.size(); j++) {
                            MiniSeed ms = mss.get(j);
                            if(ms == null) continue;
                            int nb = ms.getBlockSize();
                            rw.writeBlock(iblk, ms.getBuf(), 0, nb);
                            iblk += nb/512;
                            Util.prt(j+" "+ms.toString());
                            if(rate == 0.) rate=ms.getRate();
                            boolean done=false;
                          for (Run run : runs) {
                            if (run.add(ms)) {
                              done=true;break;
                            }
                          }
                            if(!done) {
                              Run r = new Run(ms);
                              runs.add(r);
                            }
                        }
                        rw.close();
                      }
                      catch(FileNotFoundException e) {
                        Util.prta("File not found writing tmp file");
                        System.exit(0);
                      }
                      catch(IOException e) {
                        Util.IOErrorPrint(e, "Writting ms temp file");
                        System.exit(0);
                      }
                      
                      // Put the data into the cwb or the edge using the -edge mode of msread
                      String [] eargs = new String[6];
                      eargs[0]="-allowrecent";
                      //eargs[1]="-ignoreHoldings";
                      eargs[1]="-err";
                      eargs[2]="-cwbip";
                      eargs[3]=cwbip;
                      eargs[4]="-edge:gacq5:7965:10";
                      eargs[5]="tmp.ms";
                      new msread( eargs);
                      nfill++;
                     
                      // Now we need to fixe up the fetch list to show this as done, 
                      // or make multiple fetchlist entries to show what was done and what is left 
                      long startMillis = threads[i].getStart().getTime();
                      boolean complete=false;
                      for(int j=0; j<runs.size(); j++) {
                        Util.prt(i+" "+runs.get(i));
                        if(runs.get(j).getStart().getTimeInMillis() <= startMillis+(long) (1./rate*1000.) &&
                           runs.get(j).getEnd().getTimeInMillis() >= startMillis+ (long) (threads[i].getDuration()*1000.)) {
                           Util.prt("  ** "+runs.get(j)+" spans the gap "+threads[i].getStart()+" "+threads[i].getDuration());
                           complete=true;
                        }
                      }
                      if(complete) {
                        mysql.executeUpdate("UPDATE fetchlist set status='completed',updated=now() WHERE ID="+threads[i].getFetchID());
                      }
                      else {      // This did not cover everything, make up new list
                        GregorianCalendar g = new GregorianCalendar();
                        // convert the original fetch list entry to just cover the portion of the first run
                        if(runs.get(0).getStart().getTimeInMillis() <= threads[i].getFetchList().getStart().getTime()) {
                          // The gap just filled runs from the start of the fetchlist to the end of the run
                          double dur = (runs.get(0).getEnd().getTimeInMillis() - threads[i].getFetchList().getStart().getTime())/1000.;
                          mysql.executeUpdate("UPDATE fetcher.fetchlist set status='completed',"+
                                "duration="+dur+",updated=now() WHERE ID="+threads[i].getFetchID()); 
                        }
                        else {    // There is a gap before the beginning of this
                          // the gap is from the fetchlist start until the beginning of the runs
                          double dur = (runs.get(0).getStart().getTimeInMillis() - threads[i].getFetchList().getStart().getTime())/1000.;
                          g.setTimeInMillis(threads[i].getStart().getTime()/1000*1000);
                          
                          s = "INSERT INTO fetcher.fetchlist (seedname,type,start, start_ms,duration,status,updated,created) VALUES ('"+
                                  runs.get(0).getSeedname().trim()+"','"+threads[i].getFetchList().getType().trim()+"','"+
                                  Util.ascdate(g)+" "+Util.asctime(g).substring(0,8)+"',"+
                                  (threads[i].getFetchList().getStartMS())+","+
                                  dur+",'open',now(),now())";
                          mysql.executeUpdate(s);
                          
                          // The original gap needs to be shortened to the run time and run length and marked complete
                          s = "UPDATE fetcher.fetchlist set status='completed',start='"+
                                 Util.ascdate(runs.get(0).getStart())+" "+Util.asctime(runs.get(0).getStart()).substring(0,8)+"',"+
                                  "start_ms="+(runs.get(0).getStart().getTimeInMillis() %1000)+","+                                
                                "duration="+runs.get(0).getLength()+",updated=now() WHERE ID="+threads[i].getFetchID();
                          mysql.executeUpdate(s);   
                        }
                        // each run after the first describes a remaining gap and a new completed fetch
                        for(int j=1; j<runs.size(); j++) {
                          s= "INSERT INTO fetcher.fetchlist (seedname,type,start, start_ms,duration,status,updated,created) VALUES ('"+
                                  runs.get(j).getSeedname().trim()+"','"+threads[i].getFetchList().getType().trim()+"','"+
                                  Util.ascdate(runs.get(j).getStart())+" "+Util.asctime(runs.get(j).getStart()).substring(0,8)+"',"+
                                  (runs.get(j).getStart().getTimeInMillis() %1000)+","+
                                  runs.get(j).getLength()+",'completed',now(),now())";
                          mysql.executeUpdate(s);
                          //Now the gap between this run and the prior one
                          double dur = (runs.get(j).getStart().getTimeInMillis() - runs.get(j-1).getEnd().getTimeInMillis())/1000.;
                          s = "INSERT INTO fetcher.fetchlist (seedname,type,start, start_ms,duration,status,updated,created) VALUES ('"+
                                  runs.get(j).getSeedname().trim()+"','"+threads[i].getFetchList().getType().trim()+"','"+
                                  Util.ascdate(runs.get(j-1).getEnd())+" "+Util.asctime(runs.get(j-1).getEnd()).substring(0,8)+"',"+
                                  (runs.get(j-1).getEnd().getTimeInMillis() %1000)+","+
                                  dur+",'open',now(),now())";
                          mysql.executeUpdate(s);
                        }
                        // Now is there a gap after this last run and the end of the original gap?
                        long end = (long) (threads[i].getFetchList().getStart().getTime() + threads[i].getFetchList().getDuration()*1000.);
                        if(end > runs.get(runs.size()-1).getEnd().getTimeInMillis()) {
                         
                          s = "INSERT INTO fetcher.fetchlist (seedname,type,start, start_ms,duration,status,updated,created) VALUES ('"+
                                  runs.get(0).getSeedname().trim()+"','"+threads[i].getFetchList().getType().trim()+"','"+
                                  Util.ascdate(runs.get(runs.size()-1).getEnd())+" "+Util.asctime(runs.get(runs.size()-1).getEnd()).substring(0,8)+"',"+
                                  (runs.get(runs.size()-1).getEnd().getTimeInMillis() %1000)+","+
                                  ((end - runs.get(runs.size()-1).getEnd().getTimeInMillis())/1000.)+",'open',now(),now())";
                          mysql.executeUpdate(s);
                        }
                      }
                    }   // if mss contains data
                    threads[i] = null;        // it is done
                  }
                }     // else there was no data
              } // the thread is done running, if not wait for it to finish
            }   // if the keys is not null
          }     // for each key
          try{Thread.sleep(5000);} catch(InterruptedException e) {}
        }     // While not completed
      }
      catch(InstantiationException e) {
        Util.prt("Could not instantiate a MySQLConnection!");
        e.printStackTrace();
      }
      catch(SQLException e) {
        Util.SQLErrorPrint(e, "SQLerror getting fetchlist for type "+type+" from "+fetchServer);
        e.printStackTrace();
      }

    }
    //System.exit(0);
  }
}
