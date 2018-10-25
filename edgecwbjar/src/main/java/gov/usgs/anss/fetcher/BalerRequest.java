/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */


package gov.usgs.anss.fetcher;
import gov.usgs.alarm.SendEvent;
import gov.usgs.anss.db.DBConnectionThread;
import gov.usgs.anss.edge.FetchList;
import gov.usgs.anss.edge.IllegalSeednameException;
import gov.usgs.anss.edge.RawDisk;
import gov.usgs.anss.edgethread.EdgeThread;
import gov.usgs.anss.guidb.DasConfig;
import gov.usgs.anss.guidb.TCPStation;
import gov.usgs.anss.seed.MiniSeed;
import gov.usgs.anss.seed.Steim2Object;
import gov.usgs.anss.seed.SteimException;
import gov.usgs.anss.util.Subprocess;
import gov.usgs.anss.util.Util;
import gov.usgs.anss.guidb.Cpu;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
/**
 *
 * @author davidketchum
 */
public class BalerRequest extends Fetcher {
  Wget wget;
  TCPStation tcpstation;
  String balername="";
  int balernumber=0;
  int blocksize;
  Subprocess sp;
  String station;
  public int getLength() {return wget.getLength();}
  public byte[] getBuf() {return wget.getBuf();}
  @Override
  public void closeCleanup() {
    prta(station+" closeCleanup - terminate database and any subprocesses");
    if(sp != null) sp.terminate();
    if(wget != null) wget.close();
  }
  public BalerRequest(String [] args) throws SQLException {
    super(args);
    station = getSeedname().substring(2,7).trim()+getSeedname().substring(10).trim();
    // If we are running at NEIC, we need to do the turn on baler

    // Since this is a thread mysqanss might be in use by other threads.  Always
    // create
    tag="Baler"+getName().substring(getName().indexOf("-"))+"-"+getSeedname().substring(2,7).trim()+":";
    prt(tag+" start "+orgSeedname+" "+host+"/"+port);

  }
  @Override
  public void startup() {
    if(localBaler) return;    // local baler, do not try Baler stuff.
    checkNewDay();
    String fulltext = "";
    boolean done=false;
    try {
      while(!done) {
        if(terminate) return;
        prta(tag+" startup begins.");
        synchronized(edgedbMutex) {
          if(mutexdbg) Util.prta(tag+"Got MutexB1");
          try {
            ResultSet rs = dbconnedge.executeQuery("SELECT * FROM anss.tcpstation WHERE tcpstation='"+getSeedname().substring(2,7).trim()+"'");
            balername = seedname.substring(0,7).trim();
            tcpstation = null;
            if(rs.next()) {
              tcpstation = new TCPStation(rs);
            }
            else {
              prt(tag+" ** Could not find IP address for tcpstation="+getSeedname());
              break;
            }
            rs.close();
          }
          catch(SQLException e) {
            prta(tag+" ** Exception trying to get tcpstation in startup.  Try again in 30 "+e);
            if(log == null) e.printStackTrace();
            else e.printStackTrace(log);
            Util.sleep(30000);
          }
          if(mutexdbg) Util.prta(tag+"Rel MutexB1");
        }
        if(tcpstation != null && seedname.length() >= 12)
          if(seedname.substring(10,12).equals("HR") || seedname.substring(10,12).equals("10")) {
            balername =seedname.substring(0,2)+tcpstation.getQ330Stations()[1].trim();  // Let primary station wake this one up
            prta(tag+" HR or 10 - wait on baler turnon");
            balernumber=1;
            Util.sleep(40000);
            done=true;
          }  // Let primary station wake this one up
          else {
            if(tcpstation.getQ330Stations() != null) turnOnBaler(tcpstation.getQ330Stations()[0]);
            done=true;
          }  //// Note this turns on all Balers at the site
        if(!done) continue;     // Did not get the station or baler is not on try again

        try{sleep(20000);} catch(InterruptedException e) {}
        String command = "http://"+host+":"+port+"/baler.htm";
        prta("Issue status command : "+command);
        if(terminate) return;
        wget = new Wget(command, this);
        prta(tag+seedname.substring(0,7)+" baler.htm returns getLength="+wget.getLength());
        if(wget.getLength() > 0) {
          String s = new String(wget.getBuf(), 0, wget.getLength());
          String version="";
          String hex="";
          int size=0;
          int nfiles=0;
          int usage=0;
          double voltage=0.;
          int temp=0;
          String model="";
          boolean ok=false;
          String [] parts;
          if(s.contains("Baler Model")) {
            ok=true;
            int pos = s.indexOf("<LI");
            if(pos > 0) s = s.substring(pos);
            s = s.replaceAll("\r","");
            fulltext = s.replaceAll("</LI>", "\n").replaceAll("<LI>", "").replaceAll("\n\n", "\n");
            pos=fulltext.indexOf("</UL>");
            if(pos > 0) fulltext = fulltext.substring(0,pos);
            s= s.replaceAll("</LI>", "");
            String [] tags = s.split("<LI>");
            for (String tag1 : tags) {
              parts = tag1.split(":");
              if(parts.length < 2) {
                if(parts[0].contains("Serial Number") ) hex=parts[0].substring(parts[0].indexOf("Serial Number=")+14).trim();
                else if(parts[0].contains("Voltage")) voltage =  Double.parseDouble(parts[0].substring(parts[0].indexOf("=")+1).trim());
                else if(parts[0].contains("Temperature")) {
                  temp = Integer.parseInt(parts[0].substring(parts[0].indexOf("=")+1).trim().replaceAll("C", ""));

                }
                continue;
              }
              if(parts[0].contains("Software Version")) version=parts[1].trim();
              else if(parts[0].contains("Baler Model"))  model = parts[1].trim();
              else if(parts[0].contains("Size")) size = (int) (Long.parseLong(parts[1].trim())/1000000L);
              else if(parts[0].contains("Percent"))  {
                usage = (int) (Double.parseDouble(parts[1].trim())+0.5);
                nfiles = parts[0].indexOf(" of ")+3;
                nfiles= Integer.parseInt(parts[0].substring(nfiles, parts[0].indexOf(" ", nfiles+1)).trim());
              }
            }
          }


          // Update or create a baler as needed
          synchronized(edgedbMutex) {
            if(mutexdbg) Util.prta(tag+"Got MutexB2");
            try {
              ResultSet rs = dbconnedge.executeQuery("SELECT ID FROM edge.baler WHERE baler='"+balername+"'");
              if(rs.next()) {
                rs.close();
                if(ok) dbconnedge.executeUpdate("UPDATE edge.baler set hexserial='"+hex+"',version='"+version+"',model='"+model+
                        "',size="+size+",nfiles="+nfiles+",used="+usage+",voltage="+voltage+",temp="+temp+",msg='"+fulltext+
                        "',updated=now() WHERE baler='"+balername+"'");
              }
              else {
                rs.close();
                if(ok) dbconnedge.executeUpdate(
                "INSERT INTO edge.baler (baler,hexserial,version,model,size,nfiles,used,voltage,temp,msg,updated,created_by,created) VALUES "+
                "('"+balername+"','"+hex+"','"+version+"','"+model+"',"+size+","+nfiles+","+usage+","+voltage+","+temp+",'"+fulltext+"',now(),0,now())");
                else dbconnedge.executeUpdate("INSERT INTO edge.baler (baler,updated,created_by,created) VALUES('"+balername+"',now(),0,now())");
              }
            }
            catch(SQLException e) {
              Util.SQLErrorPrint(e,"Setting the baler table");
            }
            if(mutexdbg) Util.prta(tag+"Rel MutexB2");
          }
          prta(balername+" Baler Version : "+version+" #files="+nfiles+
                  " %full="+usage+" Volt="+voltage+" temp="+temp+"C hex="+hex+
                  " model="+model+" fulltext.len="+fulltext.length());
          done=true;
        }
        else {
          prta(balername+" baler did not return a status message - probably down. retry terminate="+terminate);
          for(int i=0; i<60; i++) {
            try{sleep(10000);} catch(InterruptedException e) {}
            if(terminate) break;
          }
        }
      }
    }
    catch(RuntimeException e) {
      prta(balername+" threw runtime e="+e);
      prta(balername+" fulltext="+fulltext);
      if(log == null) e.printStackTrace();
      else e.printStackTrace(log);
    }

  }
  
  private void turnOnBaler(String name) { 
    try{
      prta(tag+"Start turnonBaler for "+name);
      sp = new Subprocess("bash "+System.getProperty("user.home")+Util.FS+"scripts"+Util.FS+"turnonBaler "+name);
      int count=0;
      while(sp.exitValue() == -99 && !terminate) {
        try{sleep(1000);} catch(InterruptedException e) {}
        count++;
        if(count > 180) {prta(" *** Baler turnon taking longer than 3 minutes.  bail out"); break;}
      }
      prta(tag+" turnOnBaler out="+sp.getOutput()+" err="+sp.getErrorOutput()+" terminate="+terminate);
      sp = null;

    }
    catch(IOException e) {
      prta(tag+" ** IOerror on turning on baler="+e);
    }
  }
  /** retrieve the data in the fetch entry
   *
   * @param fetch A fetch list object containing the fetch details
   * @return The MiniSeed data resulting from the fetch
   */
  @Override
  public ArrayList<MiniSeed> getData(FetchList fetch) {
    ArrayList<MiniSeed> mss = new ArrayList<MiniSeed>(10000);
    try {
      String seed=fetch.getSeedname();
      String starting = fetch.getStart().toString().substring(0,19).replaceAll("-", "/");
      Timestamp end = new Timestamp(fetch.getStart().getTime()+(long) (fetch.getDuration()*1000.+fetch.getStartMS()+1000));  // next second always
      String sname=fetch.getSeedname();
      boolean hrunit=false;
      Steim2Object steim2 = new Steim2Object();
      byte [] frames = new byte[4096];
      if(sname.length() < 12) {
        sname=sname.substring(7,10);
      }
      else {
        sname=sname.substring(10,12)+"-"+sname.substring(7,10);
        hrunit=true;
      }

      String command = "http://"+host+":"+port+
          "/RETRIEVE.HTM?SEED="+
          URLEncoder.encode(sname,"UTF-8")+"&MAX=&BEG="+
          URLEncoder.encode(starting,"UTF-8")+"&END="+
          URLEncoder.encode(end.toString().substring(0,19).replaceAll("-","/"),"UTF-8")+
          "&STN=&REQ=Download+Data&FILE=tmp.ms&DONE=YES";
      prt(tag+" "+sname+" "+host+":"+port+" "+starting+" to "+end.toString().substring(0,19)+" "+((end.getTime()-fetch.getStart().getTime())/1000.));
      //prt(tag+command);
      //Util.prt("http://67.47.200.99:5354/RETRIEVE.HTM?SEED=USISCO+BHZ&MAX=&BEG=2009%2F1%2F1+10%3A00&END=2009%2F1%2F1+11%3A00&STN=&REQ=Download+Data&FILE=tmp.ms&DONE=YES");
      wget = new Wget(command, this);
      prt(tag+"getLength="+wget.getLength());
      if(wget.getLength() <= 0 && tcpstation != null && !localBaler) {
        prta(" *** WGet failed 1st time - try turning the baler on and retry : cmd="+command);
        if(tcpstation.getQ330Stations() != null) turnOnBaler(tcpstation.getQ330Stations()[0]);
        try{sleep(15000);} catch(InterruptedException e) {}
        wget = new Wget(command, this);
        prt(tag+"getLength2="+wget.getLength());
      }
      if(wget.getLength() < 511) {
        String s = new String(wget.getBuf(), 0, wget.getLength());
        prt(tag+"Message? -> "+s);
        if(s.contains("No Entries in time range")) {
          nsuccess++;       // no data was available, but the query was as success
          return null;
        }
        // This means the WGet failed entirely, try turning on the baler
        else if(wget.getLength() <= 0 && tcpstation != null && !localBaler) {
          prta(" *** WGet failed 2nd time- try turning the baler on - exit with no data : cmd="+command);
          turnOnBaler(tcpstation.getQ330Stations()[0]);
          try{sleep(15000);} catch(InterruptedException e) {}
        }
        // anything else, just return empty mss to indicate no information
      }
      else {
        byte [] in = wget.getBuf();
        int len = 0;
        byte [] buf = new byte[4096];
        while(len < wget.getLength()) {
          if(wget.getLength() - len < 512) {
            prta("partial buffer at end skipped len="+len+" bufLen="+wget.getLength());
            IOErrorDuringFetch=true;
            break;
          }   // Do not do partials at end
          System.arraycopy(in, len, buf, 0, 512);   // get 512 bytes of miniseed
          try {
            int plen = MiniSeed.crackBlockSize(buf);
            if(plen < 512 || plen > 4096)  {
              prta("Bad length returned from MiniSeedBlock.  abort... plen="+plen);
              IOErrorDuringFetch=true;
              break;
            }
            if(plen > 512) {
              if(wget.getLength() -len < plen) {
                prta("partial buffer at end skipped long plen="+plen+" len="+len+" bufLen="+wget.getLength());
                IOErrorDuringFetch=true;
                break;
              }
              System.arraycopy(in, len, buf, 0, plen );
            }

            try {
              MiniSeed ms = new MiniSeed(buf,0, plen);
              if(blocksize == 0) {
                blocksize = ms.getBlockSize();
                if(mutexdbg) Util.prt(tag+" try to get MutexB4");
                synchronized(edgedbMutex) {
                  if(mutexdbg) Util.prta(tag+"Got MutexB4");
                  try {
                    String s= "UPDATE edge.baler set blocksize="+blocksize+" WHERE baler='"+balername+"'";
                    prta(s);
                     dbconnedge.executeUpdate(s);
                   }
                   catch(SQLException e) {
                     prta("Could not update blocksize in baler table for "+balername);
                   }
                    if(mutexdbg) Util.prta(tag+"Rel MutexB4");
                }
              }

             // On HR units make sure the station name is not like JFW1 instead of JFWS
             if( (hrunit ||  ms.getSeedNameString().substring(7,10).equals("LDO")) &&
                  !ms.getSeedNameString().substring(0,7).equals(fetch.getSeedname().substring(0,7))    // the names disagree
                  ) {
                prt("name change "+ms);
                buf[11] = (byte) fetch.getSeedname().charAt(5);
                ms = new MiniSeed(buf, 0, plen);
                prt("name to     "+ms);
              }
              // Some versions of the baler code would return one out of request block, suppress these!
              if( !(ms.getTimeInMillis() > fetch.getStart().getTime()+fetch.getStartMS() +fetch.getDuration()*1000. || // it after end
                    ms.getGregorianCalendarEndTime().getTimeInMillis() < fetch.getStart().getTime()) ) {// is end of block before beginning?
                
                  // This is the gauntlet to insure the block is correct, no RICs no number of sample errors
                  System.arraycopy(buf, ms.getDataOffset(),frames,0,  plen-ms.getDataOffset());
                  try {
                    int [] samples=null;
                    if(ms.getEncoding() == 11) {
                      if(steim2.decode(frames,ms.getNsamp(),ms.isSwapBytes())) {
                        samples =steim2.getSamples();
                      }
                      else {
                        samples = steim2.getSamples();
                        if(steim2.hadReverseError()) prt("    ** "+steim2.getReverseError()+" "+ms);
                        if(steim2.hadSampleCountError()) prt("    ** "+steim2.getSampleCountError()+" "+ms);
                      }
                    }
                  }
                  catch (SteimException e) {    //Steim error skip the block
                    Util.prt("   *** steim2 err="+e.getMessage()+" ms="+ms.toString());
                  }
 
                  if(ms.getNsamp() == MiniSeed.getNsampFromBuf(buf,ms.getEncoding()) &&
                     !steim2.hadReverseError() && !steim2.hadSampleCountError()) mss.add(ms);
                  else {
                    prt(" Block rejected nsamp do not agree or RIC error "+ms.getNsamp()+"!="+
                          MiniSeed.getNsampFromBuf(buf,ms.getEncoding())+" ms="+ms);
                    SendEvent.debugEvent("BalerRICErr", "RIC or Nsamp error for "+ms, this);
                  }
              }
              else prta("Discard out of time block="+ms);
            }
            catch(IllegalSeednameException e) {
              prt(tag+" ** Got IllegalSeedname e="+e+" for "+fetch);
            }
            len += plen;
          }
          catch(IllegalSeednameException e ) {
            len += 512;
            Util.prt("Got non miniseed ="+MiniSeed.toStringRaw(buf));
            e.printStackTrace();
          } 
        }
        
        // Drop any blocks entirely before the requested time.
        for(int i=mss.size()-1; i>=0; i--) {
          if(mss.get(i).getNextExpectedTimeInMillis() < fetch.getStart().getTime() + fetch.getStartMS()) {
            prta("MS block before begin time - remove it. "+mss.get(i).toStringBuilder(null)+
                    " bef="+Util.ascdatetime2(fetch.getStart().getTime()));
            mss.remove(i);
          }
          else if(mss.get(i).getTimeInMillis() > end.getTime()) {
            prta("MS block after end of request time - remove it. "+mss.get(i).toStringBuilder(null)+" aft "+
                    Util.ascdatetime2(end.getTime(),null));
            mss.remove(i);
          }
        }        
        if(wget.getLength() > 0 && mss.isEmpty()) {   // the response was non-responsive, same as nodata
          return null;
        }
        if(mss.size() > 0) {
          synchronized(edgedbMutex) {
            if(mutexdbg) Util.prta(tag+"Got MutexB8");
            try {
              String s= "UPDATE edge.baler set lastfetch='"+fetch.getStart().toString().substring(0,19)+
                      "' WHERE baler='"+balername+"' AND lastfetch<'"+fetch.getStart().toString().substring(0,19)+"'";
              prta("s="+s);
              prta(dbconnedge.executeUpdate(s)+" for "+s);

            }
            catch(SQLException e) {
              prta("Could not update lastfetch in baler table for "+balername+" e="+e);
            }
            if(mutexdbg) Util.prta(tag+"Rel MutexB8");
          }
          nsuccess++;
        }      // we got some blocks, that is success!
        return mss;
      }
    }
    catch(UnsupportedEncodingException e) {
      prt(tag+" ** impossible unreported encoding e="+e);
      e.printStackTrace();
    }
    return mss;
  }


/**
 * @version $Revision: 1.20 $
 */
public class Wget {
  String commandName ;
  int count;
  boolean dbg;
  URLConnection url;
  byte [] out = new byte[1000000];
  int nbyte;
  BalerRequest parent;
  WgetTimeout timeout;
  int state;
  InputStream in ;
  public int getLength() {return nbyte;}
  public byte[] getBuf() {return out;}
  public int getState() {return state;}
  public Wget(String command, BalerRequest par) {
    parent=par;
    commandName=command;
    try {
      timeout = new WgetTimeout(this, parent);
      state=1;
      url = (new URL(command)).openConnection();
      state=2;
      if(terminate) return;
      state=4;
      if(dbg) printHeader(url);
      if(url instanceof HttpURLConnection) {
        state=5;
        readHttpURL( (HttpURLConnection) url);
      }
      else {
        state=6;
        readURL(url.getInputStream());
      }
      state=7;
    }
    catch(java.net.MalformedURLException e) {
      prta(tag+" ** Malformed URL = command");
    }
    catch(java.io.IOException e) {
      prta(tag+" ** IOError thrown e="+e.getMessage());
      if(e.getMessage().contains("onnection reset") || e.getMessage().contains("onnection timed out") ) {
        connectFails++;
      }
    }
    state=8;
    timeout.terminate();
  }


  public final void readURL(InputStream input) throws IOException {
    in = input;
    try {
      while (!terminate) {
        int nb = socketRead(in, out, nbyte, Math.min(512, out.length - nbyte));
        state=58;
        //int nb = in.read(out, nbyte, Math.min(20000, out.length - nbyte));
        if(nb <= 0) {
          //if(dbg)
          prta(tag+commandName +
              ": EOF on Read " + nbyte +
              " bytes from " + url.getURL());
          break;
        }
        state=59;
        doDynamicThrottle(nb);
        nbyte += nb;
        if(nbyte >= out.length) {
          byte [] temp = new byte[out.length*2];
          System.arraycopy(out, 0, temp, 0, nbyte);
          out=temp;
        }
      }
    } catch (EOFException e) {
      IOErrorDuringFetch=true;
      if(dbg) prta(tag+commandName +
              ": Read " + nbyte +
              " bytes from " + url.getURL());
    } catch (IOException e) {
      IOErrorDuringFetch=true;
      prta(tag+"IOError:"+ e + ": " + e.getMessage());
      if(dbg) prt(tag+commandName +
              ": Read " + count +
              " bytes from " + url.getURL());
    }
    timeout.terminate();
  }

  public final void readHttpURL(HttpURLConnection url)
    throws IOException {

    long before, after;

    //url.setAllowUserInteraction (true);
    if(dbg) prt(tag+commandName + ": Contacting the URL ...");
    url.setConnectTimeout(20000);
    url.setReadTimeout(30000);
    state=51;
    url.connect();
    if(dbg) prt(tag+commandName + ": Connect. Waiting for reply ...");
    before = System.currentTimeMillis();
    in = url.getInputStream();
    after = System.currentTimeMillis();
    if(dbg) prt(tag+commandName + ": The reply takes " +
            ((int) (after - before) / 1000) + " seconds");

    before = System.currentTimeMillis();
    state=52;

    try {
      if (url.getResponseCode() != HttpURLConnection.HTTP_OK) {
        prt(tag+commandName + ":: " + url.getResponseMessage());
      } else {
        if(dbg) printHeader(url);
        state=53;
        readURL(in);
        state=54;
      }
    } catch (EOFException e) {
      state=55;
      after = System.currentTimeMillis();
      int milliSeconds = (int) (after-before);
      if(dbg) prt(tag+commandName +
              ": Read " + count +
              " bytes from " + url.getURL());
      if(dbg) prt(tag+commandName + ": HTTP/1.0 " + url.getResponseCode() +
              " " + url.getResponseMessage());
      url.disconnect();

      prt(tag+commandName + ": It takes " + (milliSeconds/1000) +
              " seconds" + " (at " + round(count/(float) milliSeconds) +
              " K/sec).");
      if (url.usingProxy()) {
        if(dbg) prt(tag+commandName + ": This URL uses a proxy");
      }
    } catch (IOException e) {
      state=56;
      prt( e + ": " + e.getMessage());
      if(dbg) Util.prt(tag+commandName +
              ": I/O Error : Read " + count +
              " bytes from " + url.getURL());
      prt(tag+commandName + ": I/O Error " + url.getResponseMessage());
      IOErrorDuringFetch=true;
    }
    state=57;
  }


  public float round(float f) {
    return Math.round(f * 100) / (float) 100;
  }


  public final void printHeader(URLConnection url) {
    prt(tag+": Content-Length   : " +
            url.getContentLength() );
    prt(tag+ ": Content-Type     : " +
            url.getContentType() );
    if (url.getContentEncoding() != null)
      prt(tag+": Content-Encoding : " +
              url.getContentEncoding() );
  }
  public void close() {
    prta(tag+" ** WGet close() started state="+state+" in="+in);
    if(timeout != null) timeout.terminate();
    try {
      if(in != null) in.close();
    }
    catch(IOException e) {
      if(log == null) e.printStackTrace();
      else e.printStackTrace(log);
    }
    if(url == null) {
      prta(" *** the URL is hung opening the connection.  abort thread!");
      parent.terminate();

    }
    else {

      prta(tag+" *** WGet close() close via url.getInputStream()");
      try{url.getInputStream().close();}
      catch(IOException e) {
        if(log == null) e.printStackTrace();
        else e.printStackTrace(log);
      }
    }

    prta(tag+" *** WGet close() completed url="+url);
  }
}
public class WgetTimeout extends Thread {
  int lastnbyte;
  Wget thr;
  boolean terminate;
  BalerRequest parent;
  public void terminate() {terminate=true; interrupt();}
  public WgetTimeout(Wget thread, BalerRequest par) {
    thr=thread;
    parent=par;
    start();
  }
  @Override
  public void run() {
    lastnbyte = thr.getLength();
    int i = 1;
    boolean timedOut=false;
    while(!terminate) {
      // Wait 2 seconds to check for terminates, wait 60 seconds to check on progress
      // and close socket, if two sucessive timeouts occur SendEvent to warn it may be hung
      try{sleep(2000);} catch(InterruptedException e) {}
      if(terminate) break;
      if(i++ % 30 == 0) {
        if(lastnbyte == thr.getLength()) {
          prta(" *** "+tag+" WgetTimeout: has timed out.  Close socket state="+thr.getState());
          thr.close();
          parent.interrupt();
          if(timedOut) SendEvent.debugSMEEvent("BalerWgetTO", tag+" has WGet timed out state="+thr.getState(), "BalerRequest)");
          timedOut=true;
        }
        else timedOut=false;
        lastnbyte = thr.getLength();
      }
    }
    prta(tag+" WgetTimeout has  terminated");
  }
}

  /** read up to the number of bytes, or throw exception.  Suitable for sockets since the read method
   * uses a lot of CPU if you just call it.  This checks to make sure there is data before attemping the read.
   *@param in The InputStream to read from
   *@param buf The byte buffer to receive the data
   *@param off The offset into the buffer to start the read
   *@param len Then desired # of bytes
   * @return The length of the read in bytes, zero if EOF is reached
   * @throws IOException if one is thrown by the InputStream
   */
  public int socketRead(InputStream in, byte [] buf, int off, int len) throws IOException {
    int nchar;
    nchar= in.read(buf, off, len);// get nchar
    if(nchar <= 0) {
      prta(len+" SR read nchar="+nchar+" len="+len+" in.avail="+in.available());
      return 0;
    }
    return nchar;
  }
  /**
   * @param args the command line arguments
   */
  public static void main(String[] args) {

    Util.setModeGMT();
    Util.init("edge.prop");


    try {
      BalerRequest baler =
            new BalerRequest(args);
      baler.startit();
      while(baler.isAlive()) Util.sleep(1000);
      int len = baler.getLength();
      RawDisk rw = new RawDisk("tmp.ms", "rw");
      rw.writeBlock(0, baler.getBuf(), 0, len);
      rw.setLength(len);
      rw.close();
    }
    catch(SQLException e) {
      Util.SQLErrorPrint(e,"Impossible in test mode");
    }
    catch(IOException e) {
      Util.prt("IOError="+e);
      e.printStackTrace();
    }
  }

}
