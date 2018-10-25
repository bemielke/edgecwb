
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

package gov.usgs.anss.ewexport;
import gov.usgs.alarm.SendEvent;
import gov.usgs.anss.edge.ChannelSender;
import gov.usgs.anss.edge.IllegalSeednameException;
import gov.usgs.anss.edge.RawDisk;
import gov.usgs.anss.edgemom.EdgeChannelServer;
import gov.usgs.anss.edgeoutput.Chan;
import gov.usgs.anss.edgeoutput.InorderChannel;
import gov.usgs.anss.edgeoutput.RingFileInputer;
import gov.usgs.anss.edgeoutput.TraceBuf;
import gov.usgs.anss.seed.MiniSeed;
import gov.usgs.anss.seed.Steim1;
import gov.usgs.anss.seed.Steim2Object;
import gov.usgs.anss.seed.SteimException;
import gov.usgs.anss.util.Util;
import gov.usgs.edge.config.Channel;
import java.io.IOException;
import java.net.Socket;
import java.io.FileNotFoundException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.*;


/**This class controls a single sockets to an IMPORT.  It maintains a queue of TraceBufs
 * that are ready to send, a separate EWExportOutputer which empties this queue onto the link
 * and uses the queue for any roll backs.
 *
 * It has two modes >
 * <p>
 * 1)  If the link is caught up, new data arrives via the checkBlock() method
 * and if the channel in the TraceBuf is on this link, it is queued.  It was implemented this way
 * so that if we are caught up the decompression and making of TraceBufs only is done once in the
 * EWExport.
 * <p>
 * 2) In this mode the link is behind in sequences and the current sequences are too far
 * in the future.  A separate CatchUp thread reads in from the last sequence successfully processed
 * until the head of the ring file is reached.  For each block, if the block is on the export list,
 * it is processed into TraceBufs and added to the queue.  This process is throttled to the output list
 * by suspending if the queue is full.
 *
 *
 * <PRE>
 * the line format is :
 * TAG:IPADDRES:args
 *
 * where TAG is used to identify threads in the log and IPADDRESS is the address of
 * the ip address of the EXPORT (-client or -server defines direction of connection)
 *
 * args          Description
 * -client port This IMPORT is a client end and needs to make connection to host:port
 * -server port This IMPORT is a server end and waits for connections from host:port
 * -ack          This IMPORT uses acks for every packet
 * -hbout msg    Set the heart beat message to send to EXPORT (importalive)
 * -hbin msg     The message expected from the EXPORT end (exportalive)
 * -hbint int    Seconds between heartbeats to the EXPORT
 * -hbto int    seconds to allow for receipt of heartbeat from EXPORT before declaring a dead connection
 * -inst iiii    This ends heart beats will use this institution code
 * -mod  iiii    This ends heart beats will use thiis module code
 * -nooutput     If present, no data will be send to be compressed and stored.
 * -param:brd.ip.addr:portoff[:inst:module] This is a params import, export to edgewire
 * -max  nnnn    Maximum latency on data to be sent is this number of seconds (def=18000 5 hours)
 * </PRE>
 *
 * @author davidketchum
 */
public class EWExportSocket extends Thread  {
  private static String dbgdataExport="";
  private static final int ACK_BUF_SIZE=20;
  //private static MySQLMessageQueuedClient mysql;
  private static int threadCount;
  private final int threadNumber;
  TreeMap<String, Chan> chans = new TreeMap<>();
  private int instate;
  private int state;
  private long lastBytesOut;
  private long bytesIn;
  private long bytesOut;
  private int npackets;
  private int ntooOld;
  private boolean noStateFile;
  private long statusBytesOut;          // Save bytes out from last status call  private boolean noStateFile;
  //private int nsyncError;
  private int ndisconnect;
  private long lastStatusOut;
  private final EWExport par;
  //private GapList gaps;
  private String export;
  private boolean nooutput;

  private final byte [] buf;
  private final byte [] ackbuf;

  private String tag;
  private TraceBuf  tb;         // trace buf for hydra sender
  private boolean running;
  private boolean terminate;
  private boolean dbg;
  private boolean dbgdata;
  private String dbgchan;
  private boolean allowRestricted;
  private Socket s;
  // EXPORT/IMPORT behavior flags
  private String orgArgline;
  private boolean doAcks;       // If true, perform per packet acking
  private boolean clientMode;   // IF true, this is a client and needs to maintain a connection
  private String exportHost;   // only needed in client mode, the target host
  private int exportPort;       // only needed in client mode, the tagret port
  private int hbInstitution;
  private int hbModule;
  private int hbInterval;       // the interval in seconds between heartbeats
  private int hbIntervalTimeout; // seconds to allow for non-receipt of heartbeat from EXPORT before breaking connection.
  private String hbMsgOut;
  private String hbMsgIn;
  private int maxLatency;       // Packet older than this number of milliseconds are discarded.
  private String chanFile;      // File with our list of channels
  private String stateFile;
  private final TreeMap<String, InorderChannel> inorderChannels = new TreeMap<>();;
  private boolean scn;  // if try override location code.
  private ChannelSender csend;

  // Data needed to maintain the ringfile and progress to ring file for each export
  private String ringfile;
  private RingFileInputer ring;
  private RawDisk ringIndex;
  private Catchup catchup;
  private TraceBufQueue queue;
  private EWExportOutputer exportOutputer;
  // Memory queue related items
  private int lastSeq;        // This is the last sequence processed by this module
  private int lastCheckSeq;   // the sequence number of the last sequence check to this module from realtime reader
  public void resetMaxused() {queue.resetMaxused();}
  public int getLastSeq() {return lastSeq;}
  public String getTag() {return tag.replaceAll("\\[\\]", ""); }
  public String getArgline() {return orgArgline;}
  public static void setDbgDataExport(String s) {dbgdataExport=s.trim();}
  public boolean isRunning() {return running;}
  public boolean isCatchingUp() {return (catchup == null? false: catchup.isCatchingUp());}
  public long getStatusBytesOut() {return statusBytesOut;}
  /** runt a block through the Hydra Inorder system (generally called by ChannelHolder as a final check)
   *
   * @param tb  The TimeSeries block we want to send
   * @return true, if ok.  Not ok, means its earlier, or too far in the future
   */
  private final GregorianCalendar st = new GregorianCalendar();
  private final GregorianCalendar exp = new GregorianCalendar();
  /** This checks to see if a packet would pass the current inorder test.  Note : the
   * lastTime stored in the inorder class is set by actually sending data to the IMPORT.  So
   * the TraceBufs currently bufferred to go out are not included.  There are two ways to use
   * this, if update is true, then he caller needs to have just sent the data to the IMPORT.
   * If update is false, callers can test a tracebuf to see if it should be rejected because it
   * is already to old to use.
   *
   * @param tb The trace buf to test to see if it can be sent.
   * @param seq The sequence number of that tracebuf from the input queue
   * @param update If true, this test will update the next time available and sequency number in the InorderChannel object
   * @return 1=if packet should go, 0 if packet is no on channel list, -1 if packet is out of order
   */
  public int checkInorder(TraceBuf tb, int seq, boolean update) {
    

    // Find or create the channel
    InorderChannel c;
    if(chanList == null) return 0;      // We are not configured, do not send anything
    synchronized(inorderChannels) {
      c = inorderChannels.get(tb.getSeedNameString());
      if(c == null) {
        // This is a new channel, make sure its on our output list before we allow it to be created
        if(chanList.contains(tb.getSeedNameString().trim())) {
          c = new InorderChannel(tb.getSeedNameString(), tb.getTimeInMillis());// use packet start time so it passes below on new ones
          inorderChannels.put(tb.getSeedNameString(), c);
          if(dbg) par.prta(tag+"New InorderChannel2="+tb.getSeedNameString()+"|");
        }
        else {
          //if(dbg) 
          //  par.prt("reject chan="+tb.getSeedNameString()+" clist="+chanList);
          return 0;
        }    // not a configured channel
      }
    }

    // Is it too early, print message and return false.
    if(tb.getTimeInMillis() - c.getLastTimeInMillis() < -1000./tb.getRate()) {
      synchronized(st) {
        st.setTimeInMillis(tb.getTimeInMillis());
        exp.setTimeInMillis(c.getLastTimeInMillis());
        par.prta(tag+" "+tb.getSeedNameSB()+" checkInorder discard packet from past seq="+seq+" "+Util.ascdate(st)+" "+Util.asctime2(st)+
               " ns="+tb.getNsamp()+ " exp="+Util.ascdate(exp)+" "+Util.asctime2(exp)+" diff="+
            (tb.getTimeInMillis() - c.getLastTimeInMillis()));
        //new RuntimeException(tag+" "+tb.getSeedname()+" Discard").printStackTrace(par.getPrintStream());
      }
      return -1;
    }

    // Try to update the time, if it is too far in the future, it will return false and we will too after a message
    if(!update) return 1;
    if(c.setLastTimeInMillis(tb.getNextExpectedTimeInMillis(), seq)) return 1;
    else {
      par.prta(tag+" "+tb.getSeedNameSB()+" checkInorder discard future packet "+
              " off="+(System.currentTimeMillis() - tb.getNextExpectedTimeInMillis()));
      throw new RuntimeException("Packet from future");
    }
    //return false;
  }
  long lastOutputBytesOut;
  public String getStatusString() {
    double elapsed = Math.max(System.currentTimeMillis() - lastStatusOut,1)/1000.;
    lastStatusOut=System.currentTimeMillis();
    statusBytesOut = exportOutputer.getBytesOut() - lastOutputBytesOut;
    lastOutputBytesOut= exportOutputer.getBytesOut();
    lastBytesOut = bytesOut;
    return tag+" #pkt="+npackets+" #bytesout="+(statusBytesOut/1000)+"kB "+((int)(statusBytesOut*8/elapsed))+
            " b/s Err: discon="+ndisconnect+" #old="+ntooOld+" s="+
            (s == null? "null": (s.isClosed()?"closed":
            s.getRemoteSocketAddress().toString().substring(1)+"/"+s.getLocalPort()))+
            " state="+instate+"/"+state+" "+exportOutputer.getStatus()+" q="+queue.used()+"/"+queue.getSize()+"/"+queue.getMaxUsed();
  }
  
  public long getBytesIn() {return bytesIn;}
  public long getBytesOut() {return bytesOut;}
  private long lastMonitorOut = System.currentTimeMillis();
  private long lastMonitorBytesOut;
  public String getMonitorString() {
    double elapsed = (System.currentTimeMillis() - lastMonitorOut)/1000.;
    //par.prta("getMonitorString() bytesOut="+bytesOut+" monBytesOut="+lastMonitorBytesOut+" elapase="+elapsed);
    lastMonitorOut=System.currentTimeMillis();
    long nb = bytesOut - lastMonitorBytesOut;
    lastMonitorBytesOut = bytesOut;
    String tg = tag.replaceAll("\\[", "").replaceAll("\\]", "").trim()+"-Q";
    return tg+"BytesOut="+nb+"\n"+tg+"KBPSOut="+((int) (nb/elapsed))+
            "\n"+exportOutputer.getMonitorString();
  }
  public String getConsoleOutput() {return "";}
  public void terminate() {
    terminate=true;
    //if(hbHandler != null) hbHandler.terminate();
    if(par != null) par.prt(tag+" EWExport terminate called");
    else Util.prt(tag+" EWExport terminate called.");
    if(s != null) try{s.close();} catch(IOException e){}
    exportOutputer.terminate();
    writeState(true);
    if(par != null) par.prt(tag+" EWExport write state completed.  Terminate complete");
  }
  @Override
  public String toString() {return tag+" s="+(s == null? "null" :s.getInetAddress().toString()+"/"+s.getPort()+" "+s.isClosed());}
  public void setDebug(boolean t) {dbg=t; par.prt(tag+" set debug to "+t);}
  /** check on one block for processing
   *  Return true if this block was processed or discarded.
   * @param ms The trace buf
   * @param seq Sequence
   * @return If it was processed
   */
   public boolean checkBlock(TraceBuf ms, int seq) {
    // Is this one of our channels, are we caught up, etc.  Queue it.
    if(lastSeq == lastCheckSeq || noStateFile) {

      // we are caught up with real time
      lastCheckSeq=seq;
      if( !queue.hasRoom()) {// we cannot put this in the queue, the queue is full
        //if(dbg) 
          par.prta(tag+"Cannot check in sq="+seq+" "+queue.toString()+" tb="+ms.toString().substring(0,100));
        return false;                // return, the disk catchup will have to run.
      }
      int oldLastSeq=lastSeq;
      lastSeq = seq;                  // one way or another, we are going to process this seq
      try {
        if(checkInorder(ms, seq, false) != 1) return true; // It is not in order, we processed it, but did not send it
      }
      catch(RuntimeException e) {
        par.prt(tag+": order error e="+e);
        e.printStackTrace(par.getPrintStream());
      }
      if(dbg) par.prta(tag+"Check sq="+seq+" "+queue.toString()+" tb="+ms.toString().substring(0,100));
      Channel c = EdgeChannelServer.getChannel(ms.getSeedNameSB());
      if(c != null) {
        if((c.getFlags() & 16 ) !=0 && !allowRestricted) {
            par.prt(" *** Get restricted channel allowRestricted="+allowRestricted+" ms2="+ms);
            SendEvent.edgeSMEEvent("ExportChnRstr","Channel "+ms.getSeedNameSB()+"is restricted! export="+tag,this);
        }
        else {
          if(!queue.add(ms, seq)) {par.prta(tag+" *** Queue full realtime packet skipped"); lastSeq = oldLastSeq;}
          else {npackets++; bytesOut += 512;}
        }
      }
      else {par.prta("**** channel is null for seedname="+ms.getSeedNameSB());}
    }
    else {
      // We are behind real time by seq - lastSeq, check that reader is running
      if(catchup == null || !catchup.isAlive() ) catchup = new Catchup();
      if(dbg) par.prta(tag+" catching up sq="+seq+" "+catchup);
      lastCheckSeq=seq;
    }

    return true;

  }
  /** set a new socket to receive from.  This is only called when IMPORT is in server (passive) mode
   *
   * @param ss The socket to use going forward
   */
  public void newSocket(Socket ss) {
    exportOutputer.newSocket(ss, dbgdataExport);
    s = ss;
    ndisconnect++;
  }
  /** This routine parses a command line and does the defaults for command line parameters
   *
   * @param argline
   */
  public final void setArgs(String argline) {
    String [] parts = argline.split(":");
    exportHost=parts[1];
    tag="["+parts[0]+"]";
    String [] args = parts[2].split("\\s");
    orgArgline=argline;
    doAcks=false;
    clientMode=false;
    hbMsgIn="importalive";
    hbMsgOut="exportalive";
    exportPort=-1;
    hbInterval=60;
    hbIntervalTimeout=120;
    hbModule=28;
    hbInstitution=13;
    nooutput=false;
    dbgchan="ZZZZZZ";
    allowRestricted=false;
    maxLatency = 18000000;
    int throttleMS = 800;
    int queueSize=1000;
    String mhost="";
    int dummy=0;
    for(int i=0; i<args.length; i++) {
      if(args[i].trim().equals(""))  continue;
      if(args[i].equals("-ack")) doAcks=true;
      else if(args[i].equals("-inst")) {hbInstitution=Integer.parseInt(args[i+1]);i++;}
      else if(args[i].equals("-mod")) {hbModule=Integer.parseInt(args[i+1]);i++;}
      else if(args[i].equals("-hbint")) {hbInterval= Integer.parseInt(args[i+1]);i++;}
      else if(args[i].equals("-hbto")) {hbIntervalTimeout= Integer.parseInt(args[i+1]);i++;}
      else if(args[i].equals("-hbout")) {hbMsgOut = args[i+1].replaceAll("\\|"," "); i++;}
      else if(args[i].equals("-hbin")) {hbMsgIn = args[i+1].replaceAll("\\|"," "); i++;}
      else if(args[i].equals("-q")) {queueSize = Integer.parseInt(args[i+1]); i++;}
      else if(args[i].equals("-dbgch")) {dbgchan=args[i+1].replaceAll("_"," "); i++;}
      else if(args[i].equals("-lat")) {maxLatency = Integer.parseInt(args[i+1])*1000; i++;}
      else if(args[i].equals("-dbg")) dbg=true;
      else if(args[i].equals("-c")) {chanFile = args[i+1]; i++;}
      else if(args[i].equals("-state")) {stateFile = args[i+1]; i++;}
      else if(args[i].equals("-nooutput")) nooutput=true;
      else if(args[i].equals("-scn")) scn=true;
      else if(args[i].equals("-allowrestricted")) allowRestricted=true;
      else if(args[i].equalsIgnoreCase("-bw")) {
        double kb=Double.parseDouble(args[i+1]); 
        par.prta(tag+"Set bandwidth="+kb+" Kb/s");
        if(kb > 80000.) par.prt("Override bandwidth to 80000");
        throttleMS=(int)(400000/(Math.min(80000., kb))); 
        i++;
      }
      else if(args[i].equals("-ring")) {ringfile=args[i+1];i++;}
      else if(args[i].equals("-client") ) {
        exportPort=Integer.parseInt(args[i+1]);
        if(args[i].equals("-client")) clientMode=true;
        i++;
      }
      else if(args[i].equals("-server")) clientMode=false;
      // These are arguments to EWExport that get passed along, but have no meaning here
      else if(args[i].equals("-p") || args[i].equals("-config") || args[i].equals("-bind") || args[i].equals("-dbgdata")) {i++;}
      else if(args[i].equals("-empty")) dummy=1;    // do nothing
      else par.prt(tag+"Unknown argument="+args[i]+" at "+i+" line="+argline);

    }
    if(queue == null) {
      queue = new TraceBufQueue(queueSize,  tag+"TBQ:", par);
      queue.setDebug(dbg);
    }
    if(stateFile == null) stateFile = getTag()+".state";
    if(chanFile == null) chanFile = getTag()+".chan";

    //if(exportPort == -1) throw new RuntimeException("No port configured");
    //tb = new TraceBuf(new byte[TraceBuf.TRACE_LENGTH]);        // Trace buf for sending data to HydraOutputer
    export = (tag+":"+exportHost+"/"+exportPort);
    //TraceBufQueue q, int inst, int mod, String msgout, String msgin, int hbint, int hbto,
    //      String host, int port, String tg, EdgeThread parent
    if(exportOutputer == null) {
      exportOutputer = new EWExportOutputer(queue,
            hbInstitution, hbModule, hbMsgOut, hbMsgIn, hbInterval, hbIntervalTimeout,
            exportHost, exportPort, doAcks,getTag(), throttleMS, scn, par, this);
      if(dbg) exportOutputer.setDebug(dbg);
    }
    par.prta(tag+"CFG: ack="+doAcks+" no out="+nooutput+
        " HB(i/m="+hbInstitution+"/"+hbModule+" "+hbMsgOut+">"+hbMsgIn+"< "+
        hbInterval+"s TO="+hbIntervalTimeout+"s') client="+clientMode+
        " Export:"+exportHost+":"+exportPort+" maxLatency="+maxLatency+
            " allowrestricted="+allowRestricted+" nooutput="+nooutput+" dbg="+dbg+"/"+dbgchan);


  }
  /** Create a EW socket - whether it is a server or client (normal) style is determined by the command line
   *
   * @param argline The argline as documented above
   * @param tg The tag to use on output
   * @param parent The parent EdgeThread to use for logging.
   */
  public EWExportSocket(String argline, String tg, EWExport parent){
    //super(argline, tg);
    tag="["+tg+"]";
    par=parent;
    buf = new byte[8192];               // Message is assembled here
    //b= new byte[buf.length];            // raw data goes here
    ackbuf = new byte[ACK_BUF_SIZE];
    //ackbb = ByteBuffer.wrap(ackbuf);
    threadNumber=threadCount++;
    setArgs(argline);     // Set arguments per the input command line
    readState();
    lastStatusOut=System.currentTimeMillis();
    if(clientMode) start();
  }
  /** This thread only runs in client mode.  It makes sure that there is an open socket to the
   * client and if it finds it down, it remakes it.
   * 
   */
  @Override
  public void run() {
    while(!terminate) {
      if(clientMode) {
        if(s == null ) {
          openSocket();
          exportOutputer.newSocket(s, dbgdataExport);
          ndisconnect++;
          instate=3;
          par.prta(tag+"Socket is open to "+s);
        }
        else if(s.isClosed()) {
          openSocket();
          exportOutputer.newSocket(s, dbgdataExport);
          ndisconnect++;
        }
      }
      try{sleep(5000);} catch(InterruptedException e) {}
    }
    
  }
  private void openSocket() {
    int loop=0;
    int isleep=2000;
    //int sleepMax=exportHost.contains("136.177.")?10000:120000;
    int sleepMax=Util.isNEICHost(exportHost)?10000:120000;
    instate=1;
    while(true && !terminate) {
      try
      { // Make sure anything we have open can be let go
        if(s != null) {
          try {
            if(!s.isClosed()) s.close();
          }
          catch(IOException e) {}
        }
        par.prta(tag+" Open Port="+exportHost+"/"+exportPort+" isleep="+(isleep/1000));
        s = new Socket(exportHost, exportPort);
        par.prta(tag+" Open Port="+exportHost+"/"+exportPort+" successful!");
        break;
      }
      catch (UnknownHostException e)
      {
        par.prt(tag+"* Host is unknown="+exportHost+"/"+exportPort+" loop="+loop);
        if(loop % 30 == 1) {
          SendEvent.edgeEvent("HostUnknown","EXPORT client host unknown="+exportHost+" "+tag,this);
        }
        loop++;
        try {sleep(120000L);} catch(InterruptedException e2) {}
      }
      catch (IOException e)
      {
        if(e.getMessage().equalsIgnoreCase("Connection refused")) {
          isleep = isleep * 2;
          if(isleep > sleepMax) isleep=sleepMax;
          par.prta(tag+"* Connection refused.  wait "+(isleep/1000)+" secs ....");
          if(isleep >= sleepMax) {
            SendEvent.edgeSMEEvent("EXPORTBadCon", "Conn refused "+tag+" "+exportHost+"/"+exportPort, this);
          }
          try {sleep(isleep);} catch(InterruptedException e2) {}
        }
        else if(e.getMessage().equalsIgnoreCase("Connection timed out") ||
                e.getMessage().equalsIgnoreCase("Operation timed out")) {
          isleep = isleep * 2;
          if(isleep > sleepMax) isleep=sleepMax;
          par.prta(tag+"* Connection timed out.  wait "+isleep/1000+" secs ....");
          SendEvent.edgeSMEEvent("EXPORTBadCon", "Conn timeout "+tag+" "+exportHost+"/"+exportPort, this);
          try {sleep(isleep);} catch(InterruptedException e2) {}
        }
        else  {
          //Util.IOErrorPrint(e,tag+"** IO error opening socket="+exportHost+"/"+exportPort);
          par.prta(tag+"** IOError opening socket to "+exportHost+"/"+exportPort+" e="+e);
          try {sleep(120000L);} catch(InterruptedException e2) {}
        }

      }

      //try {sleep(10000L);} catch (InterruptedException e) {}
    }   // While True on opening the socket
    instate=2;
  }
 
    /** read in the channel config file.  This file looks like a list of channels
     * on separate lines
   *
   * </PRE>
   * @return If no chan config file given, cannot read it!
   */
  private String chanList;
  private byte [] lastChanList;
  private final ArrayList<String> deletes = new ArrayList<>(10);
  public final boolean readChanList() {
    if(chanFile == null) return false;
    //par.prta(tag+"EWES: read chanFile file="+chanFile);
    try {
      byte[] bbs;
      try ( //Read in the entire channel file list as one raw read
              RawDisk in = new RawDisk(chanFile,"r")) {
        bbs = new byte[(int) in.length()];
        in.readBlock(bbs, 0, (int) in.length());
      }
      // if the first time or the old lastChanList is not the right length or has different contents
      // update lastChanList (raw bytes) and chanList (String) to whats in the file now
      boolean doit = false;
      if(lastChanList == null) doit = true;
      else if(bbs.length != lastChanList.length) doit = true;
      if(!doit) {
        for(int i=0; i<bbs.length; i++) {
          if(bbs[i] != lastChanList[i]) {
            doit=true; 
            break;
          }
        }
      }
      // If we need to update the list, we also need to look for dropped channels.
      if(doit) {
        par.prta(tag+"EWES: read changed chan file="+chanFile+" #ch="+(bbs.length/13));
        lastChanList = bbs;
        chanList = new String(bbs)/*.replaceAll("  \n","--\n").replaceAll(" \n","\n")*/;
        synchronized(inorderChannels) {
          // Need to look for channels removed from list
          Iterator<String>  itr = inorderChannels.keySet().iterator();
          if(deletes.size() > 0) deletes.clear();
          while(itr.hasNext()) {
            String key = itr.next();
            if(!chanList.contains(key.trim())) deletes.add(key);
          }
          for (String delete : deletes) {
            par.prta(tag+"*Dropping channel key=" + delete + " no longer on list=" + chanList);
            inorderChannels.remove(delete);
          }
        }
      }
    }
    catch(IOException e) {
      par.prta(tag+"EWES: Error reading chan file="+chanFile+" e="+e);
    }
    return true;
  }
  /** read in the state file.  This file looks like :
   * <PRE>
   * lastSequenec   The last sequence processed by this thread
   * <repeat each 24 bytes for number of channels>
   * Channel (12 char)
   * ms (time in ms since epoch as a long)
   * lastseq (Last sequence number sent for this channel int)
   *
   * </PRE>
   * @return
   */
 
  public final boolean readState() {
    if(stateFile == null) {noStateFile=true;return false;}
    par.prta(tag+"EWES: read state file="+stateFile);
    try {
      byte[] bbs;
      ByteBuffer bb;
      try (RawDisk in = new RawDisk(stateFile,"r")) {
        if(in.length() == 0) {par.prta(tag+"EWES: read state on empty file="+stateFile); return false;}
        noStateFile=false;
        bbs = new byte[(int) in.length()];
        bb = ByteBuffer.wrap(bbs);
        in.readBlock(bbs, 0, (int) in.length());
      }
      byte [] bs = new byte[12];
      bb.position(0);
      lastSeq = bb.getInt();
      long ms;
      int clastseq;
      String seedname;
      synchronized(inorderChannels) {
        for(int i=0; i<bbs.length/24; i++) {
          bb.get(bs);
          seedname = new String(bs);
          ms = bb.getLong();
          clastseq = bb.getInt();
          inorderChannels.put(seedname,new InorderChannel(seedname, ms, clastseq));
          par.prt(tag+" RS:"+inorderChannels.get(seedname)+"");
        }
      }
      par.prta(tag+"EWES: read state file="+stateFile+" "+(bbs.length/20)+" chans start seq="+lastSeq);
      readChanList();
    }
    catch(FileNotFoundException e) {
      try {
        par.prta(tag+"EWES: no state file found, create an empty one.");
        RawDisk in = new RawDisk(stateFile,"rw");
        in.close();
      }
      catch(IOException e2) {
        par.prta(tag+"EWES: error creating an empty state file e="+e);
        e.printStackTrace(par.getPrintStream());
      }
    }
    catch(IOException e) {
      par.prta(tag+"EWES: Error reading state file="+stateFile+" e="+e);
      noStateFile=true;
      lastSeq=0;
      return false;
    }
    return true;
  }
  /** write out the channel state file.  For format of file see readState() above.
   * The next startup lastSeq is the maximum of all of the sequences last sent for each channel,
   * that is it is the biggest sequence actually sent to the other end as all sequences are
   * always queued in order
   *
   * @param log if true, log this
   * @return true, if the file was written.
   */
  public boolean writeState(boolean log) {
    if(stateFile == null || inorderChannels.size() == 0) return false;
    ByteBuffer bb;
    byte [] bbs;
    int max=0;
    int ncurrent=0;
    long recent=System.currentTimeMillis()-600000;
    synchronized(inorderChannels) {
      bbs = new byte[inorderChannels.size()*24+4];
      bb = ByteBuffer.wrap(bbs);
      bb.position(0);
      bb.putInt(lastSeq);
      Object [] objs = inorderChannels.values().toArray();
      for (Object obj : objs) {
        InorderChannel c = (InorderChannel) obj;
        for(int i=0; i<12; i++) bb.put((byte) c.getSeednameSB().charAt(i));
        //bb.put((c.getSeedname()+"         ").substring(0,12).getBytes());
        bb.putLong(c.getLastTimeInMillis());
        bb.putInt(c.getLastSeq());
        if(c.getLastTimeInMillis() > recent) ncurrent++;
        if(c.getLastSeq() > max) max = c.getLastSeq();
        if(log) par.prt(tag+" max="+max+" "+c);
      }
    }
    bb.position(0);
    bb.putInt(max);
    try {
      try (RawDisk out = new RawDisk(stateFile, "rw")) {
        out.writeBlock(0, bbs, 0, bbs.length);
        out.setLength(bbs.length);
      }
      if(log) par.prta(tag+"EWES: write state file="+stateFile+" #currChans="+ncurrent+" of "+(bbs.length/24)+" chans max Seq="+max+
              " curr lastseq="+lastSeq+" log="+log+par.getRingFileStatus());
    }
    catch(IOException e) {
      par.prta(tag+"EWES: Error writing state file="+stateFile+" e="+e);
    }
    return true;
  }
  public void logStatus(int npack) {
    Object [] objs;
    par.prta(tag+"LogStatus (from EWExportOutputer #pack="+npack);
    synchronized(inorderChannels) {
      objs = inorderChannels.values().toArray();
    }
    for (Object obj : objs) {
      par.prt(tag + ((InorderChannel) obj).toString());
    }
  }
  /** This class reads data from the disk between lastSeq and lastCheckSeq and puts the resulting data in the queue
   *
   */
  class Catchup extends Thread {
    boolean catchupMode;
    public boolean isCatchingUp() {return catchupMode;}
    @Override
    public String toString() {return "catchingUp="+catchupMode+" lastSeq="+lastSeq+" lastCheckSeq="+lastCheckSeq+" diff="+(lastCheckSeq-lastSeq);}
    public Catchup() {
      int startBlock=-1;
      try {
        ring = new RingFileInputer(ringfile, -1);     // Open the ring

      }
      catch(IOException e) {
        par.prta(tag+"Cannot open Index Ring="+e);
        e.printStackTrace(par.getPrintStream());
        System.exit(1);
      }
      start();
    }
    @Override
    public void run() {
      byte [] buf = new byte[512];
      GregorianCalendar start = new GregorianCalendar();
      Steim2Object steim2 = new Steim2Object();
        int [] steim1 = new int[10000];
      int [] samples=null;
      byte [] frames = new byte[512-64];
      MiniSeed ms = null;
      int nsamp;
      double rate;
      int seq=0;
      int next=0;
      catchupMode=false;
      TraceBuf tb = new TraceBuf(TraceBuf.TRACE_MAX_NSAMPS);
      boolean run;
      while(!terminate) {
        run = false;
        synchronized(inorderChannels) {
          if(lastSeq != lastCheckSeq) {
            par.prta(tag+" Catchup: * start running again lastseq="+lastSeq+" lastCheckSeq="+lastCheckSeq);
            if(lastCheckSeq - lastSeq > ring.getSize()*9/10 || lastSeq < 10) {
              par.prta(tag+" Catchup: * Catchup is full buffer - start at current data");
              lastCheckSeq = ring.getLastSeq();
              lastSeq = lastCheckSeq -1;
            }
            run =true;
          }
        }
        if(run) {
          if(dbg) par.prta(tag+"Catchup: behind "+lastSeq+"-"+lastCheckSeq+" fire up disk reader");
          lastSeq++;              // We want to get the sequence after lastSeq
          try {
            ring.setNextout(lastSeq);
          }
          catch(IOException e) {
            par.prta("Weird go exception doing a ringfile nextout() e="+e);
            e.printStackTrace(par.getPrintStream());
            System.exit(1);
          }
          catchupMode=true;
          long startTime = System.currentTimeMillis();
          while(lastSeq != lastCheckSeq) {    // Ring.nextout is to next data which might not yet be in, lastCheckSeq is last seq into checker
            try {
              ring.getNextData(buf);
              try {
                if(ms == null) ms = new MiniSeed(buf);
                else ms.load(buf);
                Channel c = EdgeChannelServer.getChannel(ms.getSeedNameSB());

                // Let each one see if it would like to process this block
                //int [] ymd = SeedUtil.ymd_from_doy(ms.getYear(), ms.getDoy());
                nsamp=ms.getNsamp();
                if(ms.getBlockSize()-ms.getDataOffset() > 512-64 || nsamp == 0)
                    par.prt(tag+"*** Bad MS sq="+(ring.getNextout()-1)+" block="+ms); 
                if(startTime - ms.getTimeInMillis() < maxLatency) {
                  ntooOld++;
                }
                if((c.getFlags() & 16 ) !=0 && !allowRestricted) {
                   par.prt(" *** Get restricted channel allowRestricted="+allowRestricted+" ms="+ms);
                   SendEvent.edgeSMEEvent("ExportChnRstr","Channel "+ms.getSeedNameSB()+"is restricted! export="+tag,this);
                }
                if(nsamp != 0 && ms.getBlockSize()-ms.getDataOffset() <= 512-64 && 
                        startTime - ms.getTimeInMillis() < maxLatency && ms.getRate() > 0. &&// if data is older than 5 hours, skip it
                        ((c.getFlags() & 16) == 0 || allowRestricted))    // data is not restricted or allowRestricted is set
                {    
                  System.arraycopy(ms.getBuf(),ms.getDataOffset(), frames,0,ms.getBlockSize()-ms.getDataOffset());
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
                    par.prta(tag+"Steim problem e="+e+" "+ms);
                    continue;
                  }
                  start.setTimeInMillis(ms.getTimeInMillis() );
                  rate = ms.getRate();
                  int offset = 0;
                  while(offset < nsamp) {
                    int ns = nsamp - offset;
                    if(nsamp -offset > TraceBuf.TRACE_MAX_NSAMPS) ns = TraceBuf.TRACE_MAX_NSAMPS;
                    tb.setData(ms.getSeedNameSB(),start.getTimeInMillis(), ns, rate, samples, offset,
                        TraceBuf.INST_USNSN, TraceBuf.MODULE_EDGE, seq++, c.getID());
                    offset += ns;
                    start.add(Calendar.MILLISECOND, (int) (ns/rate*1000.+0.5));
                    // If the data is in order, queue it or wait for it to be queuable.  This just insures its
                    // not older than the last data sent out, not that it is totally o.k.
                    switch(checkInorder(tb, ring.getNextout()-1, false)) {
                      case 1: 
                        while(!queue.hasRoom()) try{sleep(100);} catch(InterruptedException e) {}  // wait for some space
                        queue.add(tb, ring.getNextout()-1);
                        npackets++;
                        bytesOut += 512;
                        if(dbg)
                          par.prta(tag+"catchup: send sq="+(ring.getNextout()-1)+" lastchk="+lastCheckSeq+
                                  " used="+queue.used()+" behind="+(lastCheckSeq - (ring.getNextout()-1))+
                                  " tb="+tb.toString().substring(0,100));
                        break;
                      case 0:
                        if(dbg) par.prta(tag+"catchup: seq not on channel list "+tb.toString().substring(0,100));
                        break;
                      case -1:
                      //if(dbg)
                        par.prta(tag+"Catchup: send seq="+(ring.getNextout() -1)+" was OOR tb="+tb.toString().substring(0,100));
                        break;
                      default:
                        par.prta(tag+" Invalid return from checkInOrder in catchup");
                    }
                  }
                }
              }
              catch(IllegalSeednameException e) {
                par.prta(tag+"Illegal seedname at next="+ring.getNextout()+" e="+e);
              }
              catch(RuntimeException e) {
                Util.prt(tag+" Catchup: Runtime2 error="+e);
                e.printStackTrace(par.getPrintStream());                
              }
              lastSeq = ring.getNextout()-1;
              if(lastSeq <0) lastSeq = 0;
            }
            catch(IOException e) {
              Util.prt(tag+"Catchup: error reading ring file! e="+e);
              e.printStackTrace(par.getPrintStream());
             }
            catch(RuntimeException e) {
              Util.prt(tag+" Catchup: Runtim error="+e);
              e.printStackTrace(par.getPrintStream());
              lastSeq = ring.getNextout()-1;
              if(lastSeq <0) lastSeq = 0;
            }
          }   // while not yet caught up
          catchupMode=false;
          //if(dbg) 
            par.prta(tag+"Catchup:  * just caught up at seq="+lastCheckSeq+" last="+lastSeq);
        }
        try{sleep(10000);} catch(InterruptedException e) {}
      }
      par.prta(tag+"Catchup:thread is exiting term="+terminate);
    }
  }

}
