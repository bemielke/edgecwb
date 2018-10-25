/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */

package gov.usgs.anss.edgemom;
import gov.usgs.alarm.SendEvent;
import gov.usgs.anss.edge.IndexFile;
import gov.usgs.anss.edge.RawToMiniSeed;
import gov.usgs.anss.edgethread.EdgeThread;
import gov.usgs.anss.util.Util;
import java.io.BufferedReader;
import java.io.FileReader; 
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Iterator;
import java.util.TreeMap;
 

/**
 * This class listens to a known port for connections of Earthworm style import connections 
 * and creates any import clients based on the external configuration file.  The individual 
 * import connection configuration lines contain
 * a client (normal) or server (actv only) indicating the nature of the connections.  This program
 * creates client connections when parsing the configuration file and periodically checks the configuration
 * file for changes.  It also restarts dead threads.
 * The server would only really be used with an export_actv.  It is setup in all EWImport within an
 * EdgeMom regardless of whether it would be used.  While the 
 * actv mode was tested briefly, it was never used to our knowledge in a production.
 * <p>
 * Once the port is opened, an accept is kept
 * open at all times which will cause a EWImportSocket to be created to actually process
 * data on the connection.  This listener launches EWImportSockets on each connection
 * to actually move the data.  The parameters are passed to the EWImportSocket.
 *<p>
 * The termination is a little different as the holder may ask this thread to terminate
 * which causes a inner class ShutdownEWImportServer to be created.  This inner class
 * will start the termination on all of the connections and then wait for all of them
 * to be shutdown.
 *<p>
 *<PRE>
 * Command Line :
 * switch			arg      Description
 * -config		filename Use this configuration file to set up individual IMPORTs.
 * -p					port     Port which will listened to for connections from an IMPORT (not normally used as IMPORTs are clients).
 * -bind			nn.nn.nn The IP address of DNS translatable server name to bind to this port (useful on multi-homed systems).
 * -dbg								 If true, this module and all children IMPORTs have debug output turned on.
 * -dbgdata						 If present, do detailed debugging of the IMPORT/EXPORT protocol.
 * -rtmsdbg						 If present, do detailed debugging of the compression of data to MiniSeed.
 * -noudpchan					 Do not send summary of data to UdpChannel for ChannelDisplay.
 * -nohydra						 Disable output to Hydra from all IMPORTS(normally used on test systems to avoid duplications).
 *
 * For the config file the lines look like :
 * TAG:ip.adr:[-client|-server nnnn]-config filename [options]
 * 
 * switch				arg						Description
 * -nooutput									If present, no data is sent to the database or Hydra (useful for testing).
 * -client			port					This IMPORT is a client end and needs to make connection to host:port.
 * -server			port					This IMPORT is a server end and waits for connections from host:port.
 * -hbout				msg						Set the heart beat message to send to EXPORT (importalive).
 * -hbin				msg						The message expected from the EXPORT end (exportalive).
 * -hbint				int						Seconds between heartbeats to the EXPORT.
 * -hbto				int						Seconds to allow for receipt of heartbeat from EXPORT before declaring a dead connection.
 * -inst				iiii					This ends heart beats by using this institution code.
 * -mod					iiii					This ends heart beats by using this module code.
 * -ack												This IMPORT uses acks for every packet.
 * -obsrio										ObsRio zip payload; do not expect escaping of special characters - process them all.
 * -excludes		str						Exclude an channel which matches in the following string RE.
 * -tran				filename			Use the filename as a list of NSCL translations (NNSSSSSCCCLL:NNSSSSSCCLLL from:to).
 * -dbgch				NNSSSSSCCCLL	Do detailed output on this channel.  Use _ for spaces. Does a String.contains() match, not a RE.
 * -dbg												Allow debug output basically a line for every received packet.
 * -bind				dot.adr				Bind to this interface.
 * -scn												Set all input data to blank location code (this is usually a bad idea).
 * -rsend				maxRate				Send Raw data to OutputInfrastructure if rate is less than the given rate - normally used to QueryMom RAM population. 
 *</PRE>
 * @author davidketchum  <ketchum at usgs.gov>
 */
public final class EWImportServer extends EdgeThread {
  private final TreeMap<String, EWImportSocket> thr = new TreeMap<>();
  //private static EWImportManager manager;
  private ServerSocket ss;                // The socket we are "listening" to for new connections
  private long lastStatus;
  private int port;
  private String host;              // The local bind host name
  private String orgArgline;
  private String configFile;
  private boolean dbg;
  //private ChannelSender csend;
  private boolean nocsend;
  private ShutdownEWImportServer shutdown;
  private long lastBytesIn;
  private long lastBytesOut;
  private boolean nohydra;
  private CheckConfigFile checkConfig;
  private final StringBuilder tmpsb = new StringBuilder(100);
  public boolean getNoHydra() {return nohydra;}
  public boolean getNoChannelSend() {return nocsend;}
  @Override
  public long getBytesIn() {
    Iterator<EWImportSocket> itr = thr.values().iterator();
    long sum=0;
    while(itr.hasNext()) sum += itr.next().getBytesIn();
    return sum;
  }
  @Override
 public long getBytesOut() {
    Iterator<EWImportSocket> itr = thr.values().iterator();
    long sum=0;
    while(itr.hasNext()) sum += itr.next().getBytesOut();
    return sum;
  }
  //public ChannelSender getChannelSender() {return csend;}
  /** Creates a new instance of MSServer. 
   * @param argline The EdgeThread args.
   * @param tg The EdgeThread tag. 
   */
  public EWImportServer(String argline,String tg)
  { super(argline,tg);
    String [] args = argline.split("\\s");
    orgArgline = argline;
    dbg=false;
    host="";
    running=false;
    port=2004;
    nocsend=false;
    nohydra=false;
    configFile="import.config";
    int dummy=0;
    for(int i=0; i<args.length; i++) {
      //prt(i+" arg="+args[i]);
      if(args[i].equals("-p")) {port = Integer.parseInt(args[i+1]);i++;}
      else if(args[i].equals("-config")) {configFile=args[i+1]; i++;}
      else if(args[i].equals("-bind")) {host=args[i+1];i++;}
      else if(args[i].equals("-dbg")) dbg=true;
      else if(args[i].equals("-rtmsdbg")) {RawToMiniSeed.setDebugChannel(args[i+1]); i++;}
      else if(args[i].equals("-noudpchan")) nocsend=true;
      else if(args[i].equals("-nohydra")) nohydra=true;
      else if(args[i].equals("-dbgdata")) {EWImportSocket.setDbgDataExport(args[i+1]); i++;}
      else if(args[i].equals("-empty")) dummy=1 ;
      else if(args[i].equals("-tran")) dummy=2;
      else if(args[i].equalsIgnoreCase("-nodb")) dummy=2;
      else if(args[i].substring(0,1).equals(">")) break;
      else prta(Util.clear(tmpsb).append("EWImport: unknown switch=").append(args[i]).append(" ln=").append(argline));
    }
    //if(manager == null) manager = new EWImportManager("-empty","EWImportM");
    shutdown= new ShutdownEWImportServer();
    checkConfig = new CheckConfigFile();
    Runtime.getRuntime().addShutdownHook(shutdown);
    try
    { prta(Util.clear(tmpsb).append("EWImport: create server on host=").append(host).
            append(" port=").append(port).append(" nohydra=").append(nohydra).
            append(" noudpchan=").append(nocsend).append(" config=").append(configFile).
            append(" dbg=").append(dbg));
      if(host.equals("")) ss = new ServerSocket(port);            // Create the listener
      else ss = new ServerSocket(port, 6, InetAddress.getByName(host));
      lastStatus =System.currentTimeMillis();   // Set initial time
      for(int i=0; i<400; i++) {
        if(EdgeChannelServer.isValid()) break;
        try{sleep(100);} catch(InterruptedException e) {}
      }
      start();
    }
    catch(UnknownHostException e) {
      prta("Got unknown host exception for host="+host+" args="+argline);
      running=false;
    }
    catch(IOException e)
    {
      Util.IOErrorPrint(e,"EWImport: ***** Cannot set up socket server on port "+port);
      //Util.exit(10);
    }

  }
  private Socket accept() throws IOException { return ss.accept();}
  /** This Thread does accepts() on the port and creates a new EWImportSocket
   *class object to handle all i/o to the connection.
   */
  @Override
  public void run()
  { running=true;
    try{sleep(2000);} catch(InterruptedException expected) {}
    prta(Util.clear(tmpsb).append(Util.ascdate()).append(" EWImport: start accept loop.  I am ").
            append(ss.getInetAddress()).append("-").append(ss.getLocalSocketAddress()).
            append("|").append(ss));
    processConfigFile(null);      // Open all of the clients
    while (!terminate)
    {
      prta(Util.clear(tmpsb).append(" EWImport: call accept() ").append(tag).append(" ").append(getName()));
      try
      {
        Socket s = accept();
        prta(Util.clear(tmpsb).append(" EWImport: accept=").append(s.getInetAddress()).
                append("/").append(s.getPort()).append(" to ").append(s.getLocalAddress()).
                append("/").append(s.getLocalPort()));
        if(terminate) break;
        processConfigFile(s);
      }
      catch(IOException expected) {}
      if(terminate) break;
    }
    Iterator itr= thr.values().iterator();
    prta("EWImportServer terminate - iterate on EWImportSockets");
    while(itr.hasNext()) ((EWImportSocket) itr.next()).terminate();
    prt("EWImportServer has been stopped");
    running=false;
    terminate=false;
  }
  public  void processConfigFile(Socket s) {
    try {
      try (BufferedReader in = new BufferedReader(new FileReader(configFile))) {
        String line;
        String incomingIP;
        EWImportSocket q;
        while( ( line = in.readLine()) != null) {
          if(line.length() < 3) continue;
          line = line.replaceAll("  "," ");
          line = line.replaceAll("  "," ");
          line = line.replaceAll("  "," ");
          String [] parts = line.split(":");
          if(line.substring(0,1).equals("#") || line.substring(0,1).equals("!")) {
            if(s == null) {
              if(line.contains("-client")) {
                q = thr.get(parts[0].substring(1).trim());
                if(q != null) {
                  q.terminate();
                  prt(Util.clear(tmpsb).append("Line for station has been commented.  Delete the process for tag  ").append(line));
                  thr.remove(parts[0].substring(1).trim());
                }
              }
            }
            continue;
          }
          if(terminate) break;
          String ipadr = parts[1].trim();
          if(ipadr.indexOf(" ") >0) ipadr = ipadr.substring(0,ipadr.indexOf(" ")).trim();
          if(s == null) {   // Client pass
            // If this line is a client, then build it if it is new. If it has changed, terminate and restart it.
            if(line.contains("-client")) {
              q = thr.get(parts[0]);
              try {
                if(q == null) {
                  prta(Util.clear(tmpsb).append("Initial client : ").append(line));
                  q = new EWImportSocket(line, parts[0], this);
                  thr.put(parts[0], q);
                }
                else if( !q.getArgline().equals(line) || !q.isAlive()) {
                  if(!q.isAlive()) prta("Client has died. restart it "+line);
                  prta(Util.clear(tmpsb).append("Client changed : ").append(line).append(" was \n").append(q.getArgline()));
                  q.terminate();
                  q = new EWImportSocket(line, parts[0], this);
                  thr.put(parts[0], q);
                }
              }
              catch(RuntimeException e) {
                if(e.toString().contains("No port config")) {
                  prt("line of configuration does not contain port! Skip this import");
                }
                else {
                  e.printStackTrace(getPrintStream());
                }
              }
            }
          }
          else {
            incomingIP = s.getInetAddress().toString().substring(1);
            if(ipadr.equals(incomingIP)) {
              q = thr.get(parts[0]);
              try {
                if(q == null) {
                  q = new EWImportSocket(line, parts[0], this);
                  thr.put(parts[0], q);
                  q.setDebug(dbg);
                  prta(Util.clear(tmpsb).append(" EWImport: new socket=").append(s.getInetAddress()).
                          append("/").append(s.getPort()).append(" to ").append(s.getLocalAddress()).
                          append("/").append(s.getLocalPort()).append(" at client=").append(thr.size()).
                          append(" new is ").append(q.getName()));
                }
                else {
                  q.setArgs(parts[1]+" "+orgArgline);
                  prta(Util.clear(tmpsb).append(" EWImport: reuse socket=").append(s.getInetAddress()).
                          append("/").append(s.getPort()).append(" to ").append(s.getLocalAddress()).
                          append("/").append(s.getLocalPort()).append(" at client=").append(thr.size()).
                          append(" new is ").append(q.getName()));
                }
                q.newSocket(s);
              }
              catch(RuntimeException e) {
                if(e.toString().contains("No port config")) {
                  prt("line of configuration does not contain port! Skip this import");
                }
                else {
                  e.printStackTrace(getPrintStream());
                }
              }
              if(q == null) {
                prta(Util.clear(tmpsb).append("EWImport: Socket connection from unknown IP to EWImport ip=").append(incomingIP));
                SendEvent.debugSMEEvent("EWImportUknown", "EWImportConnection from an unknown IP="+incomingIP, this);
                s.close();
              }
            }
          }
        }
      }
    }
    catch (IOException e)
    { if(terminate && e.getMessage().equalsIgnoreCase("Socket closed"))
        prta("EWImport: accept socket closed during termination");
      else
        Util.IOErrorPrint(e,"EWImport: accept gave unusual IOException!");
    }
  }
  /** Return the monitor string for Icinga.
   *@return A String representing the monitor key value pairs for this EdgeThread. */
  long lastMonBytes;
  long lastMonpublic;
  @Override
  public StringBuilder getMonitorString() {
    if(monitorsb.length() > 0) monitorsb.delete(0, monitorsb.length());
    long bi = getBytesIn();
    long nb = bi - lastMonBytes;
    lastMonBytes = bi;
    Iterator<EWImportSocket> itr = thr.values().iterator();
    while(itr.hasNext()) {
      monitorsb.append(itr.next().getMonitorString());
    }
    monitorsb.insert(0, "EWITotalBytesIn="+nb+"\nNThreads="+thr.size()+"\n");
    return monitorsb;
  }
  /** Since this server might have many EWImportSocket threads, its getStatusString()
   * returns one line for each of the children.
   *@return String with one line per child MSS
   */
  @Override
  public StringBuilder getStatusString() {
    Util.clear(statussb);
    Iterator itr = thr.values().iterator();
    long bytesin=getBytesIn();
    long bytesout=getBytesOut();
    statussb.append("EWImportServer has ").append(thr.size()).append(" sockets open  port=").
            append(port).append(" #bytesin=").append((bytesin-lastBytesIn)/1000).
            append(" kB #out=").append((bytesout-lastBytesOut)/1000).append(" kB\n");
    while(itr.hasNext()) statussb.append("      ").append(((EWImportSocket) itr.next()).getStatusString()).append("\n");
    lastBytesOut=bytesout;
    lastBytesIn = bytesin;
    return statussb;

  }
  @Override
  public StringBuilder getConsoleOutput() {return consolesb;}
  /** We need to terminate all of the children threads before we can be exited.
   */
  @Override
  public void terminate() {
    terminate=true; 
    
    if(ss != null)
      try {
        ss.close();
      }
      catch(IOException expected) {}
  }
  private final class CheckConfigFile extends Thread {
    public CheckConfigFile() {
      setDaemon(true);
      start();
    }
    @Override
    public void run() {
      while(!terminate) {
        for(int i=0; i<12; i++) {
          try{sleep(5000);} catch(InterruptedException expected) {}
          if(terminate) break;
        }
        processConfigFile(null);
      }
      prt("Check COnfigFile exiting...");
    }
  }
  public class ShutdownEWImportServer extends Thread {
    public ShutdownEWImportServer () {
      gov.usgs.anss.util.Util.prta("new ThreadShutdown "+getName()+" "+getClass().getSimpleName());
    }
    @Override
    public void run() {
      Iterator itr= thr.values().iterator();
      terminate=true;
      prta("Shutdown EWImportServer started - iterate on EWImportSockets");
      while(itr.hasNext()) ((EWImportSocket) itr.next()).terminate();
      boolean done=false;
      int loop=0;
      // Wait for done
      while(!done) {
        try {sleep(1000);} catch (InterruptedException expected) {}
        itr =thr.values().iterator();
        done=true;
        
        // If any sub-thread is alive or is running, do not finish shutdown yet
        while(itr.hasNext()) {
          EWImportSocket mss = (EWImportSocket) itr.next();
          if(mss.isRunning() || mss.isAlive()) {done=false; prta("ShutEWImportServer : sock="+mss.toString()+" is still up. "+loop);}
        }
        loop++;
        if(loop % 30 == 0) break;
      }
      terminate=true;
     
      // Set terminate so main server thread is ready to terminate and close its socket
      // to force it to sense its termination
      try {
        ss.close();
      }
      catch(IOException e) {
        prta("Shutdown EWImportServer socket close caused IOException");
        Util.IOErrorPrint(e,"Shutdown EWImportServer socket close caused IOException");
      }
      prta("Shutdown EWImportServer is complete.");
    }
  }
  static public void main(String [] args) {
    IndexFile.init();
    gov.usgs.anss.edgemom.EdgeMom.prt("EWImportServer startup");
    gov.usgs.anss.edgemom.EdgeMom.setNoConsole(false);
    EWImportServer mss = new EWImportServer("-dbg -noudpchan -nohydra", "EWImport");
  }
}
