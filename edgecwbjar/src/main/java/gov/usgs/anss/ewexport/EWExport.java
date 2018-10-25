/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */

package gov.usgs.anss.ewexport;
import gov.usgs.alarm.SendEvent;
import gov.usgs.anss.edge.EdgeProperties;
import gov.usgs.anss.edge.IllegalSeednameException;
import gov.usgs.anss.edge.RawDisk;
import gov.usgs.anss.edge.IndexFile;
import gov.usgs.anss.edgemom.EdgeChannelServer;
import gov.usgs.anss.edgeoutput.RingFileInputer;
import gov.usgs.anss.edgeoutput.TraceBuf;
import gov.usgs.anss.edgethread.EdgeThread;
import gov.usgs.anss.edgethread.MonitorServer;
import gov.usgs.anss.seed.MiniSeed;
import gov.usgs.anss.seed.Steim1;
import gov.usgs.anss.seed.Steim2Object;
import gov.usgs.anss.seed.SteimException;
import gov.usgs.anss.util.AnssPorts;
import gov.usgs.anss.edgethread.MemoryChecker;
//import gov.usgs.anss.util.SeedUtil;
import gov.usgs.anss.util.Util;
import gov.usgs.edge.config.Channel;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.text.DecimalFormat;
import java.util.*;

/**
 * This class listens to a known port(s) for connections foreign EW IMPORTs.  This server is
 * unique in that multiple ports can be listened to (see -p port1:port2:port3).  Each port has
 * a separate thread listening for connections to it.  The configuration file describes each
 * connection from a foreign host.  If the same foreign host has to connect to this program
 * multiple times, it must do so to different ports.  Each connection line describes the line
 * including the source IP address and the local port where the connection is expected.
 * 
 * Example:  1.2.3.4 is going to connect to this host on ports 2004 and 16000.  in the
 * configuration file the lines would look like :
 * <p>
 * NAME1:1.2.3.4/2004:-server -p 2004 -inst 13 -hbin alive ...
 * <p>
 * NAME2:1.2.3.4/16000:-server -p 16000 -inst 12 -hbin iamalive ...
 * <p>
 * In the GUI the port number would be set on these server configurations so that the two
 * separate exports can be kept distinct.  Normally, server side connections do not have a
 * port associated with them, only the IP address of the source connection.  
 * 
 * <p>
 * For a normal server
 * connection there is no "-p port" on the configuration since the line is unique by IP of the source.
 * So for the same example, but only one connection the line would be :
 * <p>
 * NAME1:1.2.3.4/0:-server -inst 13 -hbin .....
 * 
 * <p>
 * In the rare case of a export_actv, the port is used to set the port number on the destination
 * since an export_actv is a client.
 *
 * Once the port is opened, an accept is kept
 * open at all times which will cause a EWExportSocket to be created to actually process
 * data on the connection.  This listener launches EWExportSockets on each connection
 * to actually move the data.  The parameters are passed to the EWExportSocket.
 *<p>
 *The termination is a little different as the holder may ask this thread to terminate
 * which causes a inner class ShutdownEWExportServer to be created.  This inner class
 * will start the termination on all of the connections and then wait for all of them
 * to be shutdown.
 *<p>
 * This thread starts :
 * 1) The EWExportManager to keep the configuration file up-to-date if MySQL is available and configured
 * <p>
 * 2) A CheckConfigFile thread to periodically check that the configuration has not change and do the changes
 * <p>
 * 3) A RingFileReader which reads each block from the ring file, converts it to one or more TraceBufs and
 * gives each EWExportSocket a chance to send it in real time
 * <p>
 * A state file is kept which tracts the last sequence for each channel.  This file is written on normal 
 * terminations and read on startup to insure packets are not resent causing reversal of time to crash
 * certain EarthWorm software (especially the picker!).  It is still recommended that data on the receiver go
 * through a order filter before being processed on the receiving Earthworm as this state feature can be
 * defeated by EWExport not being shutdown cleanly.

 *<br>
 *<PRE>
 * Command Line :
 * switch   arg      Description
 * -config  [filename|URLProp:filename|ipadr/port:db:vendor:schema:filename]" Use this configuration file to set up individual IMPORTs, 
 *                   if URLProp:filename or :filename is used, the given URLProperty is used to contact the database (def=DBServer)
 *                   if ipadr/port....:filename is used configure via GUI/Database (start a EWExportManager)
 *                   If just the filename, there must be a TAG.chan file for each configured export with the list of channels
 * -p   pt1[:pt2[:pt3] Port(s) which will listened to for connections from an IMPORT.  The default single port is 2004.  Multiple
 *                     Listeners are used so the same IP address can connect multiple times to here.
 * -bind    nn.nn:nn.nn   The IP address of DNS translatable server name to bind to this port (useful on multihomed systems)
 *                        There should be one ip address for each port served.
 * -dbg              If true, this module and all children IMPORTs have debug output turned on
 * -dbgdata ip.adr/port   If present, do detailed debugging of the IMPORT/EXPORT on the host, 
 *                           this must match the IP/port after the tag in tab below
 * -ring    filename  The ring file with all EXPORT eligible data, this parameter is mandatory, there is no default
 * -log     filename  Set the logfile name to this name (def=EWExport)
 * -instance n       If this is not the only instance on a node, set this to a positive instance number (1-10), or a port to use for monitoring
 *
 * For the config file for each EXPORT the lines look like :
 * TAG:ip.adr/port:[-client|-server nnnn]-config filename [options]
 *               If ip.adr/port has port 0, then all connections from this address use this configuration line
 *               if the port is not zero, multiple connections to different ports are expected
 *               from the same import host and the port of the connection determines which configuration line.
 *               In the GUI, this means the port is set for a Server EXPORT to give the proper served port.
 * switch  arg   Description
 * -nooutput     If present no data is sent to the database or Hydra (useful for testing)
 * -client port  This EXPORT is a client end and needs to make connection to host:port
 * -server port  This EXPORT is a server end and waits for connections from ip.adr to this port
 * -bw ddd.d     This is the maximum allowed bandwidth in kbits/sec.   The actual bandwith limit is computed from
 *               the average bandwidth used times 10 but limited to this value.  This allows a 10 times real time
 *               catchup rate, but this parameter can limit the bandwidth to less than the dynamic 10x real time.
 *               If zero, then no throttling is done - though calculations of bandwith used and recommended continue.
 * -hbout msg    Set the heart beat message to send to IMPORT (importalive)
 * -hbin msg     The message expected from the IMPORT end (exportalive)
 * -hbint int    Seconds between heartbeats to the IMPORT
 * -hbto int    seconds to allow for receipt of heartbeat from IMPORT before declaring a dead connection
 * -inst iiii    This ends heart beats will use this institution code
 * -mod  iiii    This ends heart beats will use this module code
 * -ack          This EXPORT uses acks for every packet 
 * -excludes str Exclude an channel which matches in the following string RE
 * -dbg          Allow debug output basically a line for every received packet
 * -dbgdata host Set debug output on EWExportOutputer to this host (causes lots of logging for this export)
 * -max  secs    Data older than this are discarded (def=18000 sec or 5 hours)
 * -scn         Set all input data to blank location code, this is usually a bad idea
 * -state file   Set the state filename.  Default would be TAG.state.
 * -q     nnnnn  Set the tracebuf queue size to nnnn.  This is how much data can be queued in tracebuf form to
 *               an export, before the thread has to go to "catch up" mode where it must read the file rather
 *               than get the latest data from the EWExport thread in near real time. Def=1000
 * -c    filename The filename for the channel list for this export.  Default is TAG.chan
 * -dbgch chan   Set a channel to generate debug output (not implemented).
 * -ring  filename The ring to use if catchup mode is needed (same as the ring to EWExport!!!!!)
 *
 * For the configuration file for the channels for each export :
 * channel1   12 character channel name for 1st channel
 * channel2     next
 * .
 * .
 * 
 * For a manual configuration the channel files must either be specified with -c filename, or be named TAG.chan.
 *</PRE>
 * @author davidketchum
 */
public class EWExport extends EdgeThread {


  public static String dbgDataExport="";
  private TreeMap<String, EWExportSocket> thr;
  //private static EWExportManager manager;
  private ServerSocket ss;                // the socket we are "listening" for new connections
  private final long lastStatus;
  private int [] port;
  private String [] hosts;              // The local bind host name
  private EWExportAcceptor [] acceptors;
  private String orgArgline;
  private String configFile;
  private boolean dbg;
  private final ShutdownEWExportServer shutdown;
  private long lastBytesIn;
  private long lastBytesOut;
  private boolean nohydra;
  private String ringfile;
  private int monOffset;        // If this is other than instance 0, set the offset or absolute port number
  private EWExportManager manager;
  private RawDisk ringIndex;
  private byte [] index;
  private ByteBuffer bbindex;
  private RingFileInputer ring;
  private RingFileReader ringReader;
  private int indexLastSeq;     // This is the last sequence from the ring file processed
  private String indexString;   // This is the copy of the string, without the index
  private final CheckConfigFile checkConfig;
  private MonitorServer monitor;
  public boolean getNoHydra() {return nohydra;}
  public String getRingFileStatus() {return ring.toString();}
  @Override
  public long getBytesIn() {
    Iterator<EWExportSocket> itr = thr.values().iterator();
    long sum=0;
    while(itr.hasNext()) sum += itr.next().getBytesIn();
    return sum;
  }
  @Override
 public long getBytesOut() {
    Iterator<EWExportSocket> itr = thr.values().iterator();
    long sum=0;
    while(itr.hasNext()) sum += itr.next().getBytesOut();
    return sum;
  }
  /** Creates a new instance of MSServer
   * @param argline The EdgeThread args
   * @param tg The EdgeThread tag
   */
  public EWExport(String argline,String tg)
  { super(argline,tg);
    thr = new TreeMap<>();
    String [] args = argline.split("\\s");
    orgArgline = argline;
    dbg=false;
    hosts= new String[1];
    hosts[0]="";
    running=false;
    port = new int[1];
    port[0]=2004;
    nohydra=false;
    configFile="EW"+Util.FS+"export.setup";
    int dummy=0;
    index = new byte[2048];
    bbindex = ByteBuffer.wrap(index);
    monOffset=0;
    for(int i=0; i<args.length; i++) {
      if(args[i].length() < 1) {prt("EWExport: arg i="+i+" is empty"); continue;}
      //prt(i+" arg="+args[i]);
      if(args[i].equals("-p")) {
        String [] parts = args[i+1].split(":");
        port = new int[parts.length];
        for(int j=0; j<parts.length; j++) {
          port[j] = Integer.parseInt(parts[j]);
        }
        i++;
      }
      else if(args[i].equals("-config")) {configFile=args[i+1]; i++;}
      else if(args[i].equals("-instance")) {
        if(args[i+1].equals("^")) monOffset=0; 
        else monOffset=IndexFile.getInstanceNumber(args[1+1]);
        i++;
      }
      else if(args[i].equals("-bind")) {
        hosts = args[i+1].split(":");
        i++;
      }
      else if(args[i].equals("-dbg")) dbg=true;
      else if(args[i].equals("-dbgdata")) {EWExportSocket.setDbgDataExport(args[i+1]); dbgDataExport=args[i+1];i++;}
      else if(args[i].equals("-empty") || args[i].equals("-instance") || args[i].equals("-i")) {} // do nothing but suppress NB warnings
      else if(args[i].equals("-ring")) {
        ringfile=args[i+1];
        i++;
        int startBlock;
        try {
          ring = new RingFileInputer(ringfile, -1);     // Open the ring
          ringIndex = new RawDisk(ringfile+".ex", "rw");
          if(ringIndex.length() == 0) {
            startBlock=-1;
          }
          else {
            readIndex();
            bbindex.position(0);
            startBlock = bbindex.getInt();
            prta("Aft read index startBlock="+startBlock+" indexLastSeq="+indexLastSeq);
            readIndex();
          }
          if(startBlock == -1) {
            prta("** Index file is empty start with next block in ring file="+ring.getLastSeq());
            ring.setNextout(ring.getLastSeq());
            indexLastSeq = ring.getLastSeq();
          }
          else ring.setNextout(indexLastSeq);

        }
        catch(IOException e) {
          prta("EWExport:Cannot open Index Ring="+e);
          e.printStackTrace(getPrintStream());
          System.exit(1);
        }
      }
      else if(args[i].substring(0,1).equals(">")) break;
      else prt("EWExport: unknown switch="+args[i]+" ln="+argline);
    }
    if(ringfile == null) {
      prta("**** EXExport: requires a -ring RINGFILENAME to run.  Please rerun with this parameter!");
      System.exit(1);
    }
    String dbserv;
    if(configFile.contains(":")) {
      String parts[] = configFile.split(":");
      if(configFile.lastIndexOf(":") == 0) dbserv="";
      else dbserv = configFile.substring(0, configFile.lastIndexOf(":"));
      prta("EWExport: dbserv="+dbserv+" last="+configFile.lastIndexOf(":")+" parts.len="+parts.length);
      if(parts.length == 2) {
        if(dbserv == null) dbserv="";
        if(dbserv.length() > 0) {
          prta("EWExport:Setting DB via URL="+dbserv);
          dbserv = Util.getProperty(dbserv);
        }
        if(dbserv.equals("")) {
          dbserv = Util.getProperty("DBServer"); 
          prta("EWExport: Setting DB via default to DBServer="+dbserv);
        }
      }
      
      configFile=parts[parts.length-1];
      parts = dbserv.split(":");
      if(parts.length< 4) {
        prta("DB access configured did not lead to valid URL - please revise url="+dbserv);
        System.exit(1);
      }
      String line="-db "+dbserv+" "+"-config "+configFile+(dbg?" -dbg":"")+" >>expman";
      prta("Start Manager line="+line);
      manager = new EWExportManager(line,"EXPMAN");

    }
    else prta(" ******* Running EWExport in manual mode !!! config="+configFile);
    shutdown= new ShutdownEWExportServer();
    checkConfig = new CheckConfigFile();
    Runtime.getRuntime().addShutdownHook(shutdown);
    String host="";
    if(hosts.length != 1 || !hosts[0].equals(""))
      if(hosts.length != port.length) {
        prta("EWExport: number of host binds does not equal number of ports! ****  #host="+hosts.length+" # ports="+port.length);
      }
    lastStatus =System.currentTimeMillis();   // Set initial time
    prta("EWExport: create server nohydra="+nohydra+" config="+configFile+" dbg="+dbg);
    start();
 
  }
  class EWExportAcceptor extends Thread {
    ServerSocket ss;
    int port;
    public void terminate() {
      if(ss != null) 
        if(!ss.isClosed()) 
           try {
             ss.close();
           } 
           catch(IOException e) {}
    }
    public EWExportAcceptor(String host, int port)  throws IOException {
      this.port=port;
      if(host == null) ss = new ServerSocket(port);
      else if(host.equals("")) ss = new ServerSocket(port);            // Create the listener
      else ss = new ServerSocket(port, 6, InetAddress.getByName(host));
      start();
    }
    @Override
    public void run() {
      prta(Util.asctime()+" EWExportAccept: start accept loop.  I am "+
        ss.getInetAddress()+"-"+ss.getLocalSocketAddress()+"|"+ss);
      while(!terminate) {
        try
        { prta(Util.asctime()+" EWExportAccept: call accept() "+tag+" "+getName()+" on "+ss.getInetAddress()+"-"+ss.getLocalSocketAddress());

          Socket s = accept();
          prta(Util.asctime()+" EWExportAccept: accept="+s.getInetAddress()+"/"+s.getPort()+
                        " to "+s.getLocalAddress()+"/"+s.getLocalPort());
          if(ss.isClosed()) break;
          if(terminate) break;
          processConfigFile(s);
        }
        catch(IOException e) {
          Util.prt("EWExportAccept: IOError on accept() "+e);
          e.printStackTrace(getPrintStream());
        }
        catch(RuntimeException e) {
          Util.prt("EWExportAccept: Got runtime e="+e);
          e.printStackTrace(getPrintStream());
          SendEvent.edgeSMEEvent("EWExpAccept", "There is a fatal error in accept loop ", this);
          System.exit(2);
        }
      }
      prta("EXExportAccept: terminated for port="+port+" terminate="+terminate);
    }
    private Socket accept() throws IOException { return ss.accept();}
  }
  private void readIndex() throws IOException {
    if(index.length < ringIndex.length()) {
      index = new byte[((int) ringIndex.length()) * 2];
      bbindex = ByteBuffer.wrap(index);
    }
    ringIndex.readBlock(index, 0, (int) ringIndex.length());
    bbindex.position(0);
    indexLastSeq = bbindex.getInt();
    for(int i= (int) (ringIndex.length()-1); i>=0; i--) {
      if(index[i] != 0) {
        indexString = new String(index,4, i);
        break;
      }
    }
  }
  private void writeIndex() throws IOException {
    bbindex.position(0);
    bbindex.putInt(indexLastSeq);
    Iterator<EWExportSocket> itr = thr.values().iterator();
    while(itr.hasNext()) {
      EWExportSocket q = (EWExportSocket) itr.next();
      bbindex.put((q.getTag()+"="+q.getLastSeq()+"\n").getBytes());
    }
    ringIndex.writeBlock(0, index, 0, bbindex.position());
    ringIndex.setLength(bbindex.position());
  }
  /** This Thread does accepts() on the port an creates a new EWExportSocket
   *class object to handle all i/o to the connection.
   */
  @Override
  public void run()
  { running=true;
    monitor = new MonitorServer(monOffset< 100? AnssPorts.MONITOR_EWEXPORT_PORT+monOffset:monOffset,this);
    if(manager != null) {
      int loop=0;
      while(manager.getLoop() < 1 && loop < 20) { // Wait 20 seconds for a config to be written, else start up with files.
        try{sleep(1000);
          prta("EXExport: Waiting for config to write="+manager.getLoop()+" loop="+loop);
          loop++;
        } catch(InterruptedException e) {}
      }
    }
    acceptors = new EWExportAcceptor[port.length];
    String host="";
    for(int i=0; i<port.length; i++) {
      try {
        host="";
        if(hosts.length >= i+1) host=hosts[i];
        prta("EWExport: create server on host="+host+" port="+port[i]);
        acceptors[i] = new EWExportAcceptor(host, port[i]);
      }
      catch(UnknownHostException e) {
        prta("EWExport:Got unknown host exception for host="+host+"/"+port[i]);
        
        SendEvent.edgeSMEEvent("EWExpBadCfg","Could not create listen on "+host+"/"+port[i],this);
        running=false;
      }
      catch(IOException e)
      {
        Util.IOErrorPrint(e,"EWExport: ***** Cannot set up socket server on port "+port[0]);
        //System.exit(10);
      }
    }
    processConfigFile(null);      // Open all of the clients
    long lastCheckConfig = 0;
    long now;
    
    ringReader = new RingFileReader(this);
    // Periodicall cause the config files to be reread
    int loop=0;
    while (true)
    { try{sleep(2000);} catch(InterruptedException e) {}
      now = System.currentTimeMillis();
      if(System.currentTimeMillis() - lastCheckConfig > 120000) {
        lastCheckConfig=now;
        Iterator<EWExportSocket> itr = thr.values().iterator();
        while(itr.hasNext()) {
          itr.next().readChanList();
        }
        if(loop++ % 10 == 2) prta("Status:"+getStatusString());
      }
      if(terminate) break;
    }
    prta("EWExportSever : start terminate on acceptors");
    for (EWExportAcceptor acceptor : acceptors) {
      acceptor.terminate();
    }
    Iterator itr= thr.values().iterator();
    prta("EWExportServer terminate - iterate on EWExportSockets");
    while(itr.hasNext()) ((EWExportSocket) itr.next()).terminate();
    prt("EWExportServer has been stopped");
    running=false;
    terminate=false;
  }
  /** call this whenever the config file needs to be checked for changes or
   * when a new socket connection is made with a server.  If s is not null,
   * then this is a new incoming connection for this export, and this new socket needs
   * to replace the existing socket.  IF it is null, then this invocation looks for changes
   * in configuration, and starts/stops threads as needed.
   *
   * @param s The new socket, if null, this is a client thread
   * @param startup If true, starting up, just add one EWExportSocket for each configured import.
   */
  ArrayList<String> tags = new ArrayList<>(10);
  public synchronized void processConfigFile(Socket s) {
    try {
      BufferedReader in = new BufferedReader(new FileReader(configFile));
      String line;
      String incomingIP;
      EWExportSocket q;
      tags.clear();
      boolean found=false;
      while( ( line = in.readLine()) != null) {
        if(line.length() < 3) continue;
        line = line.replaceAll("  "," ");
        line = line.replaceAll("  "," ");
        line = line.replaceAll("  "," ");
        String [] parts = line.split(":");
        // If the line is just disabled (a comment), try to drop it
        if(line.substring(0,1).equals("#") || line.substring(0,1).equals("!")) {
          if(line.contains("dbgDataExport=")) { 
            String s22 = line.substring(line.indexOf("=")+1).trim();
            if(!s22.equals(dbgDataExport)) {
              prta("EXExport: dbgDataExport Changed to "+s22);
              dbgDataExport=s22;
            }
          }          
          if(s == null) {
            if(line.contains("-client") || line.contains("-server")) {
              q = thr.get(parts[0].substring(1).trim());
              if(q != null) {
                q.terminate();
                prt("EWExport:Line for station has been commented.  Delete it "+line);
                thr.remove(parts[0].substring(1).trim());
              }
            }
          }
          continue;
        }
     
        String [] ipparts = parts[1].split("-");
        String ipadr = Util.cleanIP(ipparts[0].trim());
        int lport = Integer.parseInt(ipparts[1].trim());
        if(ipadr.indexOf(" ") >0) ipadr = ipadr.substring(0,ipadr.indexOf(" ")).trim();
        
        // For every line, find the EWExportSocket if its not present, create it, if present, check its configuration
        q = thr.get(parts[0]);      // This is the tag name
        tags.add(parts[0]);
        try {
          if(q == null) {
            if(line.contains("-"+ringfile)) {
              prt(parts[0]+"EWExport:Initial EWES : "+line);
              q = new EWExportSocket(line+(dbg?" -dbg":""), parts[0], this);
              thr.put(parts[0], q);
            }
            //else 
            //  prt(" * not config this ring="+ringfile+" line="+line);
          }
          else if( !q.getArgline().equals(line+(dbg?" -dbg":""))) {
            prt(parts[0]+"EWExport:EWES changed : "+line+" was \n               "+q.getArgline());
            q.terminate();
            q = new EWExportSocket(line+(dbg?" -dbg":""), parts[0], this);
            thr.put(parts[0], q);
          }
        }
        catch(RuntimeException e) {
          if(e.toString().contains("No port config")) {
            prt("EWExport:line of configuration does not contain port! Skip this import"+parts[0]+" "+line);
          }
          else {
            e.printStackTrace(getPrintStream());
          }
        }

        // If s == null, this is a client checkup for changes.
        if(s != null) {   // Client pass
          incomingIP = s.getInetAddress().toString().substring(1);
          int localPort = s.getLocalPort();
          if(ipadr.equals(incomingIP) && (lport == 0 || lport == localPort)) {
            q = thr.get(parts[0]);
            //q.setArgs(parts[0]+":"+parts[1]+":"+orgArgline);
            if(q != null) {
              q.setArgs(line+(dbg?" -dbg":""));
              prta(Util.asctime()+parts[0]+" EWES: reuse socket="+s.getInetAddress()+"/"+s.getPort()+
                          " to "+s.getLocalAddress()+"/"+s.getLocalPort()+" at client="+thr.size()+" new is "+q.getName());
              q.newSocket(s);
              found=true;
            }
            else prt("EWExport: got connection for unconfigured tag="+parts[0]+" close connection");
          }
        }
      }
      if(!found && s != null) {
        prta("EWES: did not find config for connection from "+s.getInetAddress());
        SendEvent.edgeSMEEvent("EWExpUnkwn", "Connection not configured "+s.getInetAddress(), this);
        s.close();
      }
      // Now see if any tags have dropped off
      Iterator<String> itr = thr.keySet().iterator();
      while(itr.hasNext()) {
        String key = itr.next();
        boolean fnd=false;
        for (String tag1 : tags) {
          if (key.equalsIgnoreCase(tag1)) {
            fnd=true; break;
          }
        }
        if(!fnd) {
          prta("EWES: found a tag now missing from the configuration="+key+" drop this thread");
          EWExportSocket es = thr.get(key);
          if(es != null) {
            es.terminate();
            thr.remove(key);
          }
        }
      }
    }
    catch (IOException e)
    { if(terminate && e.getMessage().equalsIgnoreCase("Socket closed"))
        prta("EWExport: accept socket closed during termination");
      else
        Util.IOErrorPrint(e,"EWExport: accept gave unusual IOException!");
    }
  }
  /** return the monitor string for Nagios
   *@return A String representing the monitor key value pairs for this EdgeThread */
  StringBuilder sbnoprog = new StringBuilder(1000);
  long lastMonBytesIn;
  long lastMonBytesOut;
  @Override
  public StringBuilder getMonitorString() {
    if(monitorsb.length() > 0)monitorsb.delete(0,monitorsb.length());
    long ni = getBytesIn();
    long no = getBytesOut();
    long nbi = ni- lastMonBytesIn;
    long nbo = no -lastMonBytesOut;
    lastMonBytesIn = ni;
    lastMonBytesOut = no;
    monitorsb.append("NThread=").append(thr.size()).
            append("\nBytesIn=").append(nbi).append("\n").
            append("BytesOut=").append(nbo).append("\n");
    Iterator<EWExportSocket> itr = thr.values().iterator();
    while(itr.hasNext()) statussb.append(itr.next().getMonitorString());
    return monitorsb;
  }
  /** Since this server might have many EWExportSocket threads, its getStatusString()
   * returns one line for each of the children
   *@return String with one line per child MSS
   */
  @Override
  public synchronized StringBuilder getStatusString() {
    if(statussb.length() > 0) statussb.delete(0,statussb.length());
    if(sbnoprog.length() > 0) sbnoprog.delete(0,sbnoprog.length());
    Iterator<EWExportSocket> itr = thr.values().iterator();
    long bytesin=getBytesIn();
    long bytesout=getBytesOut();
    String time=Util.asctime().substring(0,5);
    statussb.append("EWExportServer has ").append(thr.size()).append(" sockets open  port=").append(port[0]).
            append(port.length >1?","+port[1]:"").append(port.length>2?","+port[2]:"").
            append(" #bytesin=").append((bytesin - lastBytesIn) / 1000).append(" kB #out=").
            append((bytesout - lastBytesOut) / 1000).append(" kB\n");
    while(itr.hasNext()) {
      EWExportSocket sock = itr.next();
      statussb.append("  ").append(time).append(" ").append(sock.getStatusString()).append("\n");
      if(sock.getStatusBytesOut() == 0 && !sock.getTag().contains("TEST")) 
        sbnoprog.append(sock.getTag().replaceAll("\\[", "").replaceAll("\\]", "")).append(" ");
      sock.resetMaxused();
    }
    lastBytesOut=bytesout;
    lastBytesIn = bytesin;
    if(sbnoprog.length() > 0) SendEvent.edgeSMEEvent("ExportNoProg",sbnoprog+" not making progress", this);
    return statussb;
  }
  @Override
  public StringBuilder getConsoleOutput() {return consolesb;}
  /** we need to terminate all of the children threads before we can be exited
   */
  @Override
  public void terminate() {
    terminate=true;
    if(ss != null)
      try {
        ss.close();
      }
      catch(IOException e) {}
  }
  /** This class periodicall causes the configuration to be checked for changes.
   *
   */
  public class CheckConfigFile extends Thread {
    public CheckConfigFile() {
      start();
    }
    @Override
    public void run() {
      try{sleep(5000);} catch(InterruptedException e) {}  // give configurator a chance!
      processConfigFile(null);
      while(!terminate) {
        for(int i=0; i<12; i++) {
          try{sleep(10000);} catch(InterruptedException e) {}
          if(terminate) break;
        }
        processConfigFile(null);  // Check the config file for changes.
      }
      prt("EWExport:Check ConfigFile exiting...");
    }
  }
  public class ShutdownEWExportServer extends Thread {
    public ShutdownEWExportServer () {
      gov.usgs.anss.util.Util.prta("new ThreadShutdown "+getName()+" "+getClass().getSimpleName());
    }
    @Override
    public void run() {
      Iterator itr= thr.values().iterator();
      prta("Shutdown EWExportServer started - iterate on EWExportSockets");
      while(itr.hasNext()) ((EWExportSocket) itr.next()).terminate();
      boolean done=false;
      int loop=0;
      // Wait for done
      while(!done) {
        try {sleep(1000);} catch (InterruptedException e) {}
        itr =thr.values().iterator();
        done=true;

        // If any sub-thread is alive on is running, do  not finish shutdown yet
        while(itr.hasNext()) {
          EWExportSocket mss = (EWExportSocket) itr.next();
          if(mss.isRunning() || mss.isAlive()) {done=false; prta("ShutEWExportServer : sock="+mss.toString()+" is still up. "+loop);}
        }
        loop++;
        if(loop % 30 == 0) break;
      }
      terminate=true;

      // set terminate so main server thread is ready to terminate and close its socket
      // to force it to sense its termination
      try {
        if(ss != null) ss.close();
      }
      catch(IOException e) {
        prta("Shutdown EWExportServer socket close caused IOException");
        Util.IOErrorPrint(e,"Shutdown EWExportServer socket close caused IOException");
      }
      prta("Shutdown EWExportServer is complete.");
    }
  }
  public class RingFileReader extends Thread {
    EdgeThread par;
    public RingFileReader(EdgeThread parent) {
      par = parent;
      start();
    }
    @Override
    public void run() {
      byte [] buf = new byte[512];      // one block from file with one MS block
      MiniSeed ms = null;
      int nsamp;
      double rate;
      TraceBuf tb = new TraceBuf(TraceBuf.TRACE_MAX_NSAMPS);
      int seq=0;
      GregorianCalendar start = new GregorianCalendar();
      Steim2Object steim2 = new Steim2Object();
      int [] steim1 = new int[10000];
      int [] samples=null;
      byte [] frames = new byte[512-64];
      long lastStateUpdate = System.currentTimeMillis();
      boolean log;
      while(!terminate) {
        try {
          //boolean fake=true;
          //while(fake) try{sleep(1000);} catch(InterruptedException e) {}
          indexLastSeq = ring.getNextout();
          ring.getNextData(buf);    // This will hang if no more data is available
          try {
            // read in a block, decompress it, form it into a TraceBuf and let every EWExportSocket have a chance to send it
            if(ms == null) ms = new MiniSeed(buf);
            else ms.load(buf);
            Channel c = EdgeChannelServer.getChannel(ms.getSeedNameString());
            // Let each one see if it would like to process this block
            //int [] ymd = SeedUtil.ymd_from_doy(ms.getYear(), ms.getDoy());
            nsamp=ms.getNsamp();
            if(ms.getBlockSize()-ms.getDataOffset() > 512-64 || nsamp == 0)
                prt("EWExport:Bad MS sq="+(ring.getNextout()-1)+" block="+ms);
            if(nsamp == 0 || ms.getBlockSize()-ms.getDataOffset() > 512-64) continue;
            System.arraycopy(ms.getBuf(),ms.getDataOffset(), frames,0,ms.getBlockSize()-ms.getDataOffset());

            // Decode the block into samples
            try {
              if(ms.getEncoding() == 10) {
                Steim1.decode(buf, nsamp, steim1, ms.isSwapBytes(), 0);
                samples = steim1;
              }
              else if (ms.getEncoding() == 11) {
                steim2.decode(frames, nsamp, ms.isSwapBytes(), 0);
                samples = steim2.getSamples();
              }
            }
            catch(SteimException e) {
              prta("Steim problem e="+e+" "+ms);
              continue;
            }

            // Make this into one or more TraceBufs, allow each EWExportSocket to send it
            start.setTimeInMillis(ms.getTimeInMillis() );
            rate = ms.getRate();
            int offset = 0;
            long curr = System.currentTimeMillis();
            log=false;
            if(curr % 3600000 < 120000) log=true;
            while(offset < nsamp) {
              int ns = nsamp - offset;
              if(nsamp -offset > TraceBuf.TRACE_MAX_NSAMPS) ns = TraceBuf.TRACE_MAX_NSAMPS;
              tb.setData(ms.getSeedNameString(), start.getTimeInMillis(), ns, rate, samples, offset,
                  TraceBuf.INST_USNSN, TraceBuf.MODULE_EDGE, seq++, c.getID());
              offset += ns;
              //if(dbg) 
              //  prta("chk blk="+tb.toString().substring(0,80));
              start.add(Calendar.MILLISECOND, (int) (ns/rate*1000.+0.5));
              Iterator<EWExportSocket> itr = thr.values().iterator();
              while(itr.hasNext()) {
                EWExportSocket sck = itr.next();
                sck.checkBlock(tb, indexLastSeq);
                if(curr - lastStateUpdate > 120000)
                  sck.writeState(log);
              }
              if(curr - lastStateUpdate > 120000) 
                lastStateUpdate=curr;
            }

          }
          catch(IllegalSeednameException e) {
            par.prta("EWExport:Illegal seedname at next="+indexLastSeq+" e="+e);
          }
          catch(RuntimeException e) {
            par.prta("EWExport:Runtime reading file e="+e);
            e.printStackTrace(par.getPrintStream());
          }

        }
        catch(IOException e) {
          par.prt("EWExport:Ring read error="+e);
          e.printStackTrace(par.getPrintStream());
        }
      }
    }
  }
  static public void main(String [] args) {
    EdgeProperties.init();
    Util.setModeGMT();
    Util.setProcess("EWExport");
    EdgeThread.setUseConsole(false);
    boolean dbg=false;
    Util.setNoInteractive(true);
    Util.prta(Util.ascdate()+" Starting up");
    String logfile="EWExport";
    EdgeThread.setMainLogname(logfile);
    EdgeChannelServer echn = new EdgeChannelServer("-empty","ECHN");
    while(!EdgeChannelServer.isValid()) {try {Thread.sleep(100);} catch(InterruptedException e) {}}
    StringBuilder argline = new StringBuilder(100);
    //(int i=0; i<args.length; i++) 
    //  if(argline.length() >= 1) argline.delete(argline.length()-1,argline.length());
    
    for(int i=0; i<args.length; i++) {
      argline.append(args[i]).append(" ");
      if(args[i].equals("-dbg")) dbg=true;
      if(args[i].equals("-log")) logfile=args[i+1];
    }
    if(args.length == 0) {
      System.out.println("EWExport -config [filename|mysql:ipadr:filename] -ring filename -p port[:port..] [-bind nn.nn.nn] [-dbg][-log logfile]");
      System.out.println("-config  [filename|URLProp:filename|ipadr/port:db:vendor:schema:filename] Use this configuration file to set up individual IMPORTs");
      System.out.println("                  if URLProp:filename or :filename is used, the given URLProperty is used to contact the database (def=DBServer)");
      System.out.println("                  if ipadr/port....:filename is used configure via GUI/Database (start a EWExportManager)");
      System.out.println("                  If just the filename, there must be a TAG.chan file for each configured export with the list of channels");
      System.out.println("-p       port:port     Port(s) which will listened to for connections from an IMPORT (not normally used as IMPORTs are clients)");
      System.out.println("                       The default is a single port at 2004..");
      System.out.println("-bind    nn.nn:nn.nn   The IP address of DNS translatable server name to bind to this port (useful on multihomed systems)");
      System.out.println("                       There should be one ip address for each port served.");
      System.out.println("-dbg              If true, this module and all children EXPORTs have debug output turned on");
      System.out.println("-dbgdata ip.adr/port   If present, do detailed debugging of the IMPORT/EXPORT on the host, ");
      System.out.println("                          this must match the IP/port after the tag in tab below");
      System.out.println("-ring    filename  The ring file with all EXPORT eligible data, this parameter is mandatory, there is no default");
      System.out.println("-log     filename  Set the logfile name to this name (def=EWExport)");

      System.exit(0);
    }
    argline.append(" >>").append(logfile); 

    EWExport par = new EWExport(argline.toString(), "EWExport");
    
    // Memory monitoring loop
    DecimalFormat df1 = new DecimalFormat("0.0");
    int loop=1;
    boolean terminate=false;

    for(;;) {
      try {Thread.sleep(5000);} catch(InterruptedException e) {}
      if(terminate) {
          Util.prta("User must have changed terminate.  exitting");
          System.exit(1);
      }
      if(loop++ % 24 == 0) 
        if(MemoryChecker.checkMemory() ) {
          Util.prta("Memory check failed! exitting");
          System.exit(1);
        }
    }
  }

}
