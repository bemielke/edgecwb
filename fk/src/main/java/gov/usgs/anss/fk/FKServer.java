/*
 *  This software is in the public domain because it contains materials 
 *  that originally came from the United States Geological Survey, 
 *  an agency of the United States Department of Interior. For more 
 *  information, see the official USGS copyright policy at 
 *  http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */
package gov.usgs.anss.fk;

import gov.usgs.alarm.SendEvent;
import gov.usgs.anss.edge.EdgeProperties;
import gov.usgs.anss.edge.IllegalSeednameException;
import gov.usgs.anss.edge.MiniSeedOutputHandler;
import gov.usgs.anss.edge.RawInputClient;
import gov.usgs.anss.edge.RawToMiniSeed;
import gov.usgs.anss.util.Util;
import gov.usgs.anss.edgethread.EdgeThread;
import gov.usgs.anss.seed.MiniSeed;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import static java.lang.Thread.sleep;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.GregorianCalendar;
import java.util.Calendar;
import java.util.concurrent.Semaphore;

/** This server accepts connections and inputs in FKServer format and returns
 * responses.  This server maintains a pool of handlers and the starting number of
 * handler threads and the maximum number are set on the command line (defaults are 20 and 1000).
 * <p>
 * FKServer accomplishes this by using the configuration line received to 
 * <p>
 * <PRE>
 * switch   arg     Description
 * -p      Port         The server port (def=2064)
 * -allowrestricted     If present, restricted channels can be served by this server
 * -maxthreads  NN      Maximum number of threads to permit simultaneously (def 1000)
 * -minthreads  NN      The number of TrinetHandler threads to start immediately (def 20)
 * -quiet               If present, reduce the verbosity of log output
 * -dbg                 Increase logging for debugging purposes
 * 
 * For the thread input to compute the FK the arguments are all of the ones possible for creating an FKArray plus :
 *  switch   Args    Description 
 * -cwbip  ip.add   The IP address of the CWB which would get data if older than 10 days (see RawInputClient for details)
 * -cwbport nnnn    Port to send CWB data that is older than 10 days (def=0 discard the data)
 * -edgeip ip.adr   The IP address to send data within the 10 day window (def = localhost)(see RawInputClient for details)
 * -edgeport nnn    THe port to send the data within 10 days (def= 7972)
 * -l        char    The location code second letter to use for output.
 * 
 * </PRE>
 *
 * @author U.S. Geological Survey  <ketchum at usgs.gov>
 */
public class FKServer extends EdgeThread {
  static FKServer fk;
  static int thrcount;
  private int port;         // Port to listen for connections
  private boolean dbg;
  private int maxThreads,minThreads;// Min/max thread count
  private String host;
  private boolean quiet;
  private boolean allowrestricted;
  private int usedThreads;
  private ArrayList<FKServerHandler>  handlers = new ArrayList<FKServerHandler>(maxThreads);

  private String dbgSeedname;
  private ServerSocket d;
  private final ShutdownFKServer shutdown;
    private StringBuilder runsb = new StringBuilder(100);   // use this in the run loop for logging
  @Override
  public void terminate() {terminate=true; try{d.close();} catch(IOException e) {} interrupt();}   // cause the termination to begin
  /** return the monitor string for Nagios
   *@return A String representing the monitor key value pairs for this EdgeThread */
  @Override
  public StringBuilder getMonitorString() { 
    Util.clear(monitorsb).append("port=").append(port).append("\n");

    return monitorsb;
  }
  @Override
  public StringBuilder getStatusString() {
    if(statussb.length() > 0) statussb.delete(0, statussb.length());
    statussb.append("FKS : port=").append(port);
    return statussb;
  }
  @Override
  public StringBuilder getConsoleOutput() {return consolesb;}

  public FKServer(String argline, String tg) {
    super(argline, tg);
    Util.setModeGMT();
    dbg=false;
    port = 2064;
    maxThreads=1000;
    host = null;
    minThreads=20;
    prta(Util.clear(runsb).append("FKS: Argline=").append(argline));
    int dummy=0;
   
    String [] args = argline.split("\\s");
    for(int i=0; i<args.length; i++) {
      if(args[i].equals("-dbg")) dbg=true;
      //else if(args[i].equals("-dbgmsp")) EdgeQueryClient.setDebugMiniSeedPool(true);
      else if(args[i].equals("-empty")) dummy=1 ; // Do nothing, supress NB warnings
      else if(args[i].equals("-quiet")) quiet=true;
      else if(args[i].equals("-p")) {port = Integer.parseInt(args[i+1]); i++;}   // Port for the server
      else if(args[i].equals("-maxthreads")) {maxThreads = Integer.parseInt(args[i+1]);i++;}
      else if(args[i].equals("-minthreads")) {minThreads = Integer.parseInt(args[i+1]);i++;}
      else if(args[i].equals("-allowrestricted")) { allowrestricted=true;}
      else if(args[i].length() > 0)
        if(args[i].substring(0,1).equals(">")) break;
      else prt(tag+"FKServer: unknown switch="+i+" "+args[i]+" ln="+argline);
    }

    prta(Util.clear(runsb).append(tag).append(Util.ascdate()).append(" FKS: created2 args=").
            append(argline).append(" tag=").append(tag).append("host=").append(host).
            append(" port=").append(port).
            append(" quiet=").append(quiet).append(" allowrestrict=").append(allowrestricted).
            append(" dbgch=").append(dbgSeedname).append(" thr=").append(this.getId()));
    for(int i=0; i<minThreads; i++) handlers.add(new FKServerHandler(null,this));
    this.setDaemon(true);
    shutdown = new ShutdownFKServer();
    Runtime.getRuntime().addShutdownHook(shutdown);
    running=true;
    start();    
  }
  @Override
  public void run()
  { if(fk != null) {
      prt("FKS: **** Duplicate creation of CWBWaverServer! Panic!");
      System.exit(1);
    }
    try {sleep(3000);} catch(InterruptedException e) {} // give CWBWaveServer a chance to come up.
    fk=this;
 
    long lastStatus = System.currentTimeMillis();
    //setPriority(getPriority()+2);
    if(dbg) prta(Util.getThreadsString(300));

    // OPen up a port to listen for new connections.
    while(!terminate) {
      try {
        //server = s;
        prta(Util.clear(runsb).append(tag).append(" FKS: Open Port=").append(port));
        d = new ServerSocket(port);
        break;
      }
      catch (SocketException e)
      { if(e.getMessage().equals(tag+" FKS: Address already in use"))
        {
            try {
              prt(Util.clear(runsb).append(tag).append(" FKS: Address in use - try again."));
              Thread.sleep(2000);
            }
            catch (InterruptedException E) {

          }
        }
        else {
          prt(Util.clear(runsb).append(tag).append(" FKS:Error opening TCP listen port =").
                  append(port).append("-").append(e.getMessage()));
          try{
            Thread.sleep(2000);
          }
          catch (InterruptedException E) {

          }
        }
      }
      catch(IOException e) {
        prt(Util.clear(runsb).append(tag).append(" FKS:Error opening socket server=").append(e.getMessage()));
        try {
          Thread.sleep(2000);
        }
        catch (InterruptedException E) {

        }
      }
    }

    String ip="";
    Socket s=null;
    while(!terminate)
    { 
      try {
        s = accept();
        //prta(tag+" FKS: accept from "+s);
        ip = s.getInetAddress().toString();


        // find a thread to assign this to
        boolean assigned=false;
        while(!assigned) {
          //prta(tag+" try to assign");
          // look through pool of connections and assign the first null one found
          for(int i=0; i<handlers.size(); i++) {
            if(!handlers.get(i).isAlive()) {    // If the thread has failed, make a new one
              prta(Util.clear(runsb).append(tag).append("[").append(i).
                      append("]FKS: is not alive ***** , replace it! ").
                      append(handlers.get(i).toStringBuilder(null)));
              SendEvent.edgeSMEEvent("CWBFKSThrDown",tag+"["+i+"] is not alive ***** , replace it! ",this);
              handlers.set(i,new FKServerHandler(null, this));
            }
            // If the handler is not assigned a socket, use it
            if(handlers.get(i).getSocket() == null ) {
              if(dbg)
                prta(Util.clear(runsb).append(tag).append("[").append(i).append("]FKS: Assign socket to ").
                        append(i).append("/").append(handlers.size()).append("/").append(maxThreads).
                        append(" ").append(handlers.get(i).toStringBuilder(null)));
              handlers.get(i).assignSocket(s);
              usedThreads++;
              assigned=true;
              break;
            }
            else if(handlers.get(i).getSocket().isClosed()) { // The socket is assigned, make sure its not closed
              prta(Util.clear(runsb).append(tag).append(" FKS: found [").append(i).append("] is closed, free it"));
              handlers.get(i).closeConnection();
              handlers.get(i).assignSocket(s);
              usedThreads++;
              assigned=true;
            }
          }

          // If we did not assign a connection, time out the list, create a new one, and try again
          if(!assigned) {
            long nw = System.currentTimeMillis();
            int nfreed=0;
            int maxi=-1;
            long maxaction=0;
            for(int i=0; i<handlers.size(); i++) {
              if(dbg)
                prta(Util.clear(runsb).append(tag).append(" FKS: check ").append(i).append(" ").append(handlers.get(i)));
              // only dead sockets and long left analysts could be more than an hour old
              if(nw - handlers.get(i).getLastActionMillis() > 3600000 && handlers.get(i).getSocket() != null) {
                prta(Util.clear(runsb).append(tag).append(" FKS: Free connection ").append(i).append(" ").append(handlers.get(i).toStringBuilder(null)));
                handlers.get(i).closeConnection();
                nfreed++;
              }
              else {
                if(maxaction < (nw - handlers.get(i).getLastActionMillis())) {
                  maxaction = nw - handlers.get(i).getLastActionMillis();
                  maxi=i;
                }
              }
            }
            if(nfreed > 0) continue;        // go use one of the freed ones
            // If we are under the max limit, create a new one to handle this connection, else, have to wait!
            if(handlers.size() < maxThreads) {
              prta(Util.clear(runsb).append(tag).append("FKS: create new FKSH ").append(handlers.size()).append(" s=").append(s));
              handlers.add(new FKServerHandler(s, this)); 
              usedThreads++;
              assigned=true;
            }
            else {
              if(maxi >= 0) {
                prta(Util.clear(runsb).append(tag).append(" FKS: ** No free connections and maxthreads reached.  Dropped oldest action=").
                        append(maxaction).append(" ").append(handlers.get(maxi).toStringBuilder(null)));
                SendEvent.debugEvent("FKSThrFull", "FKS thread list is full - deleting oldest", this);
                handlers.get(maxi).closeConnection();
                continue;
              }
              
              prta(Util.clear(runsb).append(tag).append(" FKS: **** There is no room for more threads. Size=").append(handlers.size()).append(" s=").append(s));
              SendEvent.edgeSMEEvent("FKSMaxThread", "There is not more room for threads!", this);
              try {sleep(500);} catch(InterruptedException e) {}
            }
          }
          if(terminate) break;
        } // Until something is assigned
      }
      catch (IOException e) {
        if(e.toString().contains("Too many open files")) {
          SendEvent.edgeEvent("TooManyOpen","Panic, too many open files in CWBWaveServer", this);
          System.exit(1);
        } 
        Util.SocketIOErrorPrint(e,"in FKS setup - aborting", getPrintStream());
      }
      catch(RuntimeException e) {
        SendEvent.edgeSMEEvent("RuntimeExcp","FKS: got Runtime="+e, this);
        prta(Util.clear(runsb).append("FKS: Runtime in CWBWS: ip=").append(ip).append(" s=").append(s));
        e.printStackTrace(getPrintStream());
      }
      long now = System.currentTimeMillis();
    }       // end of infinite loop (while(true))
    for (FKServerHandler handler : handlers) {
      if (handler != null) {
        handler.terminate();
      }
    } 
    prta(Util.clear(runsb).append(tag).append(" FKS:read loop terminated"));
    running=false;
  }
  private Socket accept() throws IOException {  return d.accept();}
  
  // Start of class FKServerHander
  class FKServerHandler extends Thread implements MiniSeedOutputHandler {
    Semaphore semaphore = new Semaphore(1);     // a one size semaphone
    Socket s;
    long lastAction;
    String ttag;
    StringBuilder tmpsb = new StringBuilder(100);
    StringBuilder runsb = new StringBuilder(100);
    int ithr;
    FKArray array;
    String orgargline;
    FKParams fkparms;
    EdgeThread par;
    RawToMiniSeed rtms;
    ArrayList<MiniSeed> blks = new ArrayList<MiniSeed>(100);
    @Override
    public void close() {}      // this is the close for the MiniSeedOutputHandler - not for this socket!
    @Override
    public void putbuf(byte [] buf, int len) {
      try {
        MiniSeed ms = new MiniSeed(buf, 0, len);
        blks.add(ms);
        //DEBUG: we should write the result to the user here!
      }
      catch(IllegalSeednameException e) {
        Util.prt("Illegal seedname="+e);
      }      
    }
    @Override
    public String toString() {return ttag+" lastaction="+(System.currentTimeMillis() - lastAction)/1000+" s="+s;}
    public StringBuilder toStringBuilder(StringBuilder tmp) {
      StringBuilder sb = tmp;
      if(sb == null) sb = Util.clear(tmpsb);
      synchronized(sb) {
        sb.append(ttag).append(" lastaction=").append((System.currentTimeMillis() - lastAction)/1000).append(" s=").append(s);
      }
      return sb;
    }
    public Socket getSocket() {return s;}
    public void terminate() {closeConnection();}
    /** the main server thread starts up a handler by assigning it a socket.  This is where this is done and
     * the thread will startup right after!
     * 
     * @param ss The socket to use 
     */
    public final void assignSocket(Socket ss) {
      s = ss; 
      if(s != null) {
            ttag = ttag.substring(0,ttag.indexOf("{"))+
              "{"+s.getInetAddress().toString().substring(1)+":"+s.getPort()+"}";    
        semaphore.release();
      }
      lastAction=System.currentTimeMillis(); 
    }
    /** close the connection if it is open */
    public void closeConnection() {
      if(dbg) prta(ttag+" CloseConnection "+s);
      if(s == null) return;
      if(!s.isClosed()) {
        try {
          s.close();
        }
        catch(IOException e) {}
      }
      usedThreads--;
      s=null;
    }

    public long getLastActionMillis() {return lastAction;}
    public FKServerHandler(Socket s, EdgeThread parent) {
      ithr=thrcount++;      
      par = parent;
      ttag = tag+"["+ithr+"]-"+this.getId()+"{}";
      try{semaphore.acquire(1);} catch(InterruptedException e) {}   // we have the block
      assignSocket(s);
      start();
    }

    @Override
    public void run() {
      //boolean ok;
      prta(Util.clear(runsb).append(ttag).append("Starting FKSH: "));
      String line;
      long startMS;
      long current=0;
      GregorianCalendar gc = new GregorianCalendar();
      RawInputClient rawout=null;
      int [] idata = new int[2];
      while(!terminate) {   // This loop gets a socket
        try{
          // Wait for a socket to be assigned
          while(s == null) {
            try {semaphore.acquire(1);} catch(InterruptedException e) {}
            if(dbg) prta(Util.clear(runsb).append(ttag).append("Acquired semaphore s=").append(s).
                    append(" semaphore=").append(semaphore));
          }  
          BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream()));
          // body of Handler goes here
          while( (line = in.readLine()) != null) {
            startMS = System.currentTimeMillis();
            lastAction = System.currentTimeMillis();
            line = line.replaceAll("  ", " "); 
            line = line.replaceAll("  ", " ");
            line = line.replaceAll("  ", " ");
            if(!quiet)
              prta(Util.clear(runsb).append(ttag).append(" FKSH : line=").append(line));

            orgargline = line;
            String [] args = line.split("\\s");
            String cwbServer=null;
            String edgeServer=null;
            int cwbPort=0;
            int edgePort=0;
            char overrideLoc='Z';
            for(int i=0; i<args.length; i++) {
              if(args[i].equals("-cwbip")) {cwbServer=args[i+1];i++;}
              if(args[i].equals("-edgeip")) {edgeServer=args[i+1];i++;}
              if(args[i].equals("-cwbport")) {cwbPort=Integer.parseInt(args[i+1]); i++;}
              if(args[i].equals("-edgeport")) {edgePort=Integer.parseInt(args[i+1]); i++;}
              if(args[i].equals("-l")) {overrideLoc=args[i+1].charAt(0);i++;}
            }            
            array = new FKArray(line, tag, (EdgeThread) par);
            fkparms = array.getFKParams();
            if(fkparms.getStartTime() == null) {
              current = fkparms.getStartTimeInMillis(); // User supplied start time
            }
            else {    // THis is an error I think
            }
            long endAt = fkparms.getEndTimeInMillis();            
            int npts = fkparms.getBeamlen();
            int nseis=array.getNActive();
            int [] beam = new int[npts/2];
            double resultsRate = 1./fkparms.getBeamWindow()*2.;
            FKResult results = array.getFKResult();
            int advanceMillis =  (int) ((npts - fkparms.getOverlap())*fkparms.getDT()*1000.+0.5);  // force advance
            StringBuilder channel = new StringBuilder(12);
            channel.append(array.getRef().getChannel());
            Util.stringBuilderReplaceAll(channel, '_', ' ');
            prta(Util.clear(runsb).append(tag).append("Starting FK for channel ").append(channel).append(" resultRate=").append(resultsRate));
            if(edgeServer != null && cwbServer != null) {
              rawout = new RawInputClient(channel.toString(), edgeServer, edgePort, cwbServer, cwbPort, par);
            } 

            int ngood=nseis;
            while(!terminate) {
              if(current > endAt) {
                prta("End time has been reached end="+Util.ascdatetime2(endAt)+" Idle here forever");
                for(;;) try{sleep(1000); if(terminate) break;} catch(InterruptedException e) {}
                if(terminate) break;
              }
              // If behind real time, need to check on channels and advance by 30 seconds if none is found
              gc.setTimeInMillis(current);
              int nchan = array.getTimeseries(gc,npts, ngood);
              //boolean found=false;
              if( nchan <= 0) {     // Did not process this time
                if(current < System.currentTimeMillis() - fkparms.getLatencySec()*1000) {  // out of 5 minute window, process every section
                  if(-nchan > nseis/2) {    // We have some channels but not all of them
                    ngood = -nchan;         // set a smaller minimum size and try again
                    continue;
                  }      // accept what ever number of channels we can get
                  current +=  advanceMillis;  // force advance - not enough channels
                  prta(Util.clear(runsb).append(tag).append("Out of 5 minute realtime window.  Advance to ").append(Util.ascdatetime2(current)).
                          append("nch=").append(nchan).append(" ngood=").append(ngood).append(" nseis=").append(nseis));
                }
                else {      // within the 5 minute window, but no data, need to sleep for some more data
                  prta(Util.clear(runsb).append(tag).append("In 5 minute realtime window. Wait ").append(advanceMillis/500.).
                          append(" current=").append(Util.ascdatetime2(current)).append("nch=").append(nchan).
                          append(" ngood=").append(ngood).append(" nseis=").append(nseis));
                  try{sleep(2*advanceMillis);} catch(InterruptedException e) {}
                }
              }
              else {        // it processed, advance the time
                if(nchan > ngood) ngood = nchan;    // If we have move channels this time, make that the new benchmark
                //prta(Util.clear(runsb).append(tag).append("Time processed : "+Util.ascdatetime2(current)+" method="+fkparms.getBeamMethod()));
                  // output the data
                switch(fkparms.getBeamMethod()) {
                case 0:
                  channel.setCharAt(11, overrideLoc);
                  double [] arraybeam = array.getBeam();
                  for(int i=npts/4; i<npts*3/4; i++) beam[i-npts/4] = (int) Math.round(arraybeam[i]);
                  gc.setTimeInMillis(current+advanceMillis/2);
                  if(rawout != null) {
                    rawout.send(channel, npts/2, beam, gc, fkparms.getRate(), 0, 0, 0, 0);
                  }
                  else {      // WE need to make MiniSeed out of this
                    if(rtms == null) {
                      rtms = new RawToMiniSeed(channel, fkparms.getRate(), 7, 
                            gc.get(Calendar.YEAR), gc.get(Calendar.DAY_OF_YEAR), 
                            (int) ((gc.getTimeInMillis() % 86400000L)/1000L), (int) ((gc.getTimeInMillis() % 1000) * 1000),
                          1, par);
                      rtms.setOutputHandler(this);      // Set this RTMS to user this objects output handler
                    }
                    rtms.process(beam, npts/2,gc.get(Calendar.YEAR), gc.get(Calendar.DAY_OF_YEAR), 
                            (int) ((gc.getTimeInMillis() % 86400000L)/1000L), (int) ((gc.getTimeInMillis() % 1000) * 1000), 
                            (int) fkparms.getRate(), 0, 0, 0, 0);
                  }
                  prta(Util.clear(runsb).append(tag).append("Add timeseries ").append(channel).append(" ").
                          append(Util.ascdatetime2(gc)).append(" npts=").append(npts).
                          append(" rt=").append(fkparms.getRate()).append(" nch=").append(nchan).
                          append("/").append(ngood).append("/").append(nseis));
                  //results.writeStatistics(par,channel);
                  //RawToMiniSeed.forceout(channel);
                  break;
                default:
                  prta(Util.clear(runsb).append(tag).append("Method not supported ").append(fkparms.getBeamMethod()));
                } 
                /*channel.setCharAt(11, 'N');
                idata[0] = (int) Math.round(results.getPowerNorm()/1000.);
                rawout.send(channel, 1, idata, gc, resultsRate, 0, 0, 0, 0);
                // LOC-R ratio of powerNorm/powerNormAvg*10
                channel.setCharAt(11, 'R');
                idata[0] = (int) Math.round(results.getPowerNorm()/results.getPowerNormAvg()*10);
                rawout.send(channel, 1, idata, gc, resultsRate, 0, 0, 0, 0);
                // LOC-Q ratio of (power - meanavg)/stdavg*10.     
                channel.setCharAt(11, 'Q');
                idata[0] = (int) Math.round((results.getPower()[0]-results.getMeanAvg())/results.getStdAvg()*10.);
                rawout.send(channel, 1, idata, gc, resultsRate, 0, 0, 0, 0);
                channel.setCharAt(11, 'S');
                idata[0] = (int) Math.round(results.getMaxSlw()*1000.);
                rawout.send(channel, 1, idata, gc, resultsRate, 0, 0, 0, 0);
                // LOC-B instantaneous azm*10
                channel.setCharAt(11, 'B');
                idata[0] = (int) Math.round(results.getAzi()*10.);
                rawout.send(channel, 1, idata, gc, resultsRate, 0, 0, 0, 0);
                // LOC-A applied Azimuth *10
                channel.setCharAt(11, 'A');
                idata[0] = (int) Math.round(results.getAziApplied()*10.);
                rawout.send(channel, 1, idata, gc, resultsRate, 0, 0, 0, 0);
                // LOC-C max azimuth diff
                channel.setCharAt(11, 'C');
                idata[0] = results.getMaxAziDiff();
                rawout.send(channel, 1, idata, gc, resultsRate, 0, 0, 0, 0);
                current += advanceMillis; // advance to next time*/

              }
            }           // while(!terminate)            
          }     // Read another line
        }
        catch(IOException e) {
          prta(ttag+"IOError on input socket e="+e);
          e.printStackTrace(par.getPrintStream());
        }
        catch(RuntimeException e) {
          if(e.toString().indexOf("OutOfMemory") > 0) {
            prta(Util.clear(runsb).append(ttag).append("FKSH: got out of memory - try to terminate!"));
            prt(Util.getThreadsString());
            System.exit(101);
          }
          prt(Util.clear(runsb).append(ttag).append(" FKSH: Handler RuntimeException caught e=").append(e).
                  append(" msg=").append(e.getMessage() == null?"null":e.getMessage()).append(s));
          e.printStackTrace(getPrintStream());
          closeConnection();                 
        } 
        // The socket for reading a line has errored out, close it
        if(s != null) {
          try {s.close();} catch(IOException e) {}
          s=null;
        }
      } // while not terminate - get a socket
      //prt("Exiting CWBWaveServers run()!! should never happen!****\n");
      prta(Util.clear(runsb).append(ttag).append(" FKSH: terminated"));
    }

  }   // End of FKServerHandler class
  private class ShutdownFKServer extends Thread {
    public ShutdownFKServer() {

    }

    /** this is called by the Runtime.shutdown during the shutdown sequence
     *cause all cleanup actions to occur
     */

    @Override
    public void run() {
      terminate=true;
      interrupt();
      prta(Util.clear(runsb).append(tag).append(" CWS: Shutdown started"));
      int nloop=0;
      if(d != null) if(!d.isClosed()) try {d.close();} catch(IOException e) {}
      prta(Util.clear(runsb).append(tag).append(" CWS:Shutdown Done."));
      try{sleep(10000);} catch(InterruptedException e) {}
      //if(!quiet) 
        prta(Util.getThreadsString());
      prta(Util.clear(runsb).append(tag).append(" CWS:Shutdown Done exit"));
    
    }
  }
  public static void main(String [] args) {
    Util.setProcess("FKServer");
    EdgeProperties.init();
    Util.setNoconsole(false);
    Util.setNoInteractive(true);
    Util.setModeGMT();
    EdgeThread.setMainLogname("fk");
    Util.prt("Starting FKServer"); 
    FKServer fkMain = new FKServer("-p 6000 >fk", "FKS");
    for(;;) {
      Util.sleep(1000);
    }
            
  }
}
